#!/usr/bin/env python3
"""Published-tag exception must verify remote identity without changing refs."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[2]
for case, tag, accepted in (
    ('annotated', 'v0.2.88', True),
    ('lightweight', 'v0.2.88', True),
    ('drift', 'v0.2.88', False),
    ('missing', 'v0.2.88', False),
    ('annotated', 'main', False),
    ('annotated', 'v0.2.88;echo unsafe', False),
):
    shell = r'''
source "$1/scripts/lib/myworld-common.sh"
git() {
  shift 2
  case "$1" in
    rev-parse) echo aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ;;
    ls-remote)
      case "$TEST_CASE" in
        missing) return 2 ;;
        drift) printf 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\trefs/tags/v0.2.88\n' ;;
        annotated) printf 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\trefs/tags/v0.2.88\naaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\trefs/tags/v0.2.88^{}\n' ;;
        lightweight) printf 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\trefs/tags/v0.2.88\n' ;;
      esac ;;
    *) return 99 ;;
  esac
}
TEST_CASE="$2"
myworld_git_verified_release_commit /fixture "$3"
'''
    result = subprocess.run(['bash', '-c', shell, 'test', str(root), case, tag], capture_output=True, text=True)
    assert (result.returncode == 0) == accepted, (case, tag, result.stdout, result.stderr)
print('PASS: published release annotated/lightweight identity and drift/missing/invalid rejection')
