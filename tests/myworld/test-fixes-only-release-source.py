#!/usr/bin/env python3
"""Fail-closed tests of the exceptional release-source guard, without network."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMMIT = "a" * 40
BRANCH = "fix/fixes-only-test"


class SourceGuard(unittest.TestCase):
    def check_guard(self, state="valid", branch=BRANCH, commit=COMMIT, operation=None):
        with tempfile.TemporaryDirectory() as directory:
            if operation:
                (Path(directory) / operation).touch()
            env = dict(os.environ, CASE=state, MOCK_GIT_DIR=directory,
                       EXPECTED=COMMIT, BRANCH=BRANCH)
            shell = r'''
set -euo pipefail
fail() { echo "FAIL: $*" >&2; exit 1; }
git() {
  shift 2
  case "$1" in
    symbolic-ref) [[ "$CASE" == wrong-branch ]] && echo main || echo "$BRANCH" ;;
    rev-parse) [[ "$2" == --absolute-git-dir ]] && echo "$MOCK_GIT_DIR" || echo "$EXPECTED" ;;
    status) [[ "$CASE" != dirty ]] || echo ' M tracked-file'; return 0 ;;
    ls-remote)
      [[ "$CASE" != missing-remote ]] || return 2
      [[ "$CASE" == remote-drift ]] && echo bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb || echo "$EXPECTED" ;;
    merge-base) [[ "$CASE" != wrong-baseline ]] ;;
    *) return 99 ;;
  esac
}
source "$1"
fixes_only_require_source /fixture "$2" "$3"
'''
            return subprocess.run(
                ["bash", "-c", shell, "guard", str(ROOT / "scripts/lib/fixes-only-release-source.sh"), branch, commit],
                env=env, capture_output=True, text=True)

    def test_exact_clean_pushed_source(self):
        result = self.check_guard()
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_rejects_unverified_sources(self):
        for state in ("dirty", "wrong-branch", "remote-drift", "missing-remote", "wrong-baseline"):
            with self.subTest(state=state):
                self.assertNotEqual(self.check_guard(state).returncode, 0)

    def test_requires_both_explicit_flags_and_exact_commit(self):
        for branch, commit in (("", COMMIT), (BRANCH, ""), ("main", COMMIT), (BRANCH, "a" * 39), (BRANCH, "b" * 40)):
            with self.subTest(branch=branch, commit=commit):
                self.assertNotEqual(self.check_guard(branch=branch, commit=commit).returncode, 0)

    def test_rejects_pending_operations(self):
        for operation in ("MERGE_HEAD", "CHERRY_PICK_HEAD", "REVERT_HEAD", "REBASE_HEAD", "rebase-apply", "rebase-merge", "sequencer", "BISECT_LOG"):
            with self.subTest(operation=operation):
                self.assertNotEqual(self.check_guard(operation=operation).returncode, 0)


if __name__ == "__main__":
    unittest.main()
