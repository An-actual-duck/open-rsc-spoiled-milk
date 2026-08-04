#!/usr/bin/env python3
"""Focused integration tests for neutral AI worktree preservation."""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


SOURCE_ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_FILES = [
    Path("scripts/ai-workspace.sh"),
    Path("scripts/ai-manager.sh"),
    Path("scripts/create-ai-workspace.sh"),
    Path("scripts/lib/ai-workspace-common.sh"),
]


class WorkflowFixture:
    def __init__(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="ai-workspace-test-")
        self.base = Path(self.temp.name)
        self.root = self.base / "Core-Framework"
        self.remote = self.base / "remote.git"
        self.root.mkdir()

        self.git("init", "--initial-branch=main")
        self.git("config", "user.name", "AI Workflow Test")
        self.git("config", "user.email", "ai-workflow@example.test")
        for relative in WORKFLOW_FILES:
            destination = self.root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(SOURCE_ROOT / relative, destination)
        package_stub = self.root / "scripts" / "package-player-release.sh"
        package_stub.write_text(
            "#!/usr/bin/env bash\n"
            "set -euo pipefail\n"
            "printf '%s\\n' \"$@\" > \"$AI_RELEASE_CAPTURE\"\n",
            encoding="utf-8",
        )
        package_stub.chmod(0o755)
        layered_package_stub = self.root / "scripts" / "package-layered-world-release.sh"
        layered_package_stub.write_text(
            "#!/usr/bin/env bash\n"
            "set -euo pipefail\n"
            "printf '%s\\n' \"$@\" > \"$AI_LAYERED_RELEASE_CAPTURE\"\n",
            encoding="utf-8",
        )
        layered_package_stub.chmod(0o755)
        (self.root / ".gitignore").write_text("AI_WORKSPACE.md\n", encoding="utf-8")
        (self.root / "README.md").write_text("fixture\n", encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "-m", "Initial fixture")

        subprocess.run(["git", "init", "--bare", str(self.remote)], check=True, capture_output=True, text=True)
        self.git("remote", "add", "spoiled-milk", str(self.remote))
        self.git("push", "--set-upstream", "spoiled-milk", "main")

        self.env = {
            **os.environ,
            "ROOT_DIR": str(self.root),
            "AI_REMOTE": "spoiled-milk",
            "AI_WORKSPACE_PARENT": str(self.base),
            "MYWORLD_LIVE_ROOT": str(self.base / "live-does-not-exist"),
            "AI_RELEASE_CAPTURE": str(self.base / "release-args.txt"),
            "AI_LAYERED_RELEASE_CAPTURE": str(self.base / "layered-release-args.txt"),
        }

    def close(self) -> None:
        self.temp.cleanup()

    def git(self, *args: str, cwd: Path | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["git", *args],
            cwd=cwd or self.root,
            check=check,
            capture_output=True,
            text=True,
        )

    def run(
        self,
        script: str,
        *args: str,
        cwd: Path | None = None,
        check: bool = True,
    ) -> subprocess.CompletedProcess[str]:
        run_cwd = cwd or self.root
        result = subprocess.run(
            ["bash", str(self.root / "scripts" / script), *args],
            cwd=run_cwd,
            env={**self.env, "ROOT_DIR": str(run_cwd)},
            capture_output=True,
            text=True,
        )
        if check and result.returncode != 0:
            self.fail_result(script, args, result)
        return result

    @staticmethod
    def fail_result(script: str, args: tuple[str, ...], result: subprocess.CompletedProcess[str]) -> None:
        raise AssertionError(
            f"{script} {' '.join(args)} failed ({result.returncode}):\n"
            f"STDOUT:\n{result.stdout}\nSTDERR:\n{result.stderr}"
        )

    def slot(self, number: int) -> Path:
        return self.base / f"Core-Framework-ai-{number}"

    def state(self, number: int) -> dict[str, str]:
        path = self.root / ".git" / "ai-workspaces" / f"ai-{number}.state"
        return dict(line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines())


class AiWorkspaceWorkflowTest(unittest.TestCase):
    def setUp(self) -> None:
        self.fixture = WorkflowFixture()

    def tearDown(self) -> None:
        self.fixture.close()

    def test_checkpoint_handoff_merge_and_safe_recycle(self) -> None:
        f = self.fixture
        f.run("ai-workspace.sh", "init", "2")
        slot = f.slot(1)
        self.assertEqual(f.git("branch", "--show-current", cwd=slot).stdout.strip(), "")
        self.assertEqual(f.state(1)["phase"], "IDLE")

        f.run("ai-workspace.sh", "start", "ai-1", "fix/fixture-task")
        self.assertEqual(f.git("branch", "--show-current", cwd=slot).stdout.strip(), "fix/fixture-task")
        (slot / "untracked-preserved.txt").write_text("checkpoint me\n", encoding="utf-8")

        f.run("ai-workspace.sh", "checkpoint", "-m", "Checkpoint fixture", cwd=slot)
        self.assertEqual(f.state(1)["phase"], "ACTIVE")
        local_head = f.git("rev-parse", "HEAD", cwd=slot).stdout.strip()
        remote_head = f.git("rev-parse", "refs/remotes/spoiled-milk/fix/fixture-task", cwd=slot).stdout.strip()
        self.assertEqual(local_head, remote_head)
        self.assertTrue((slot / "untracked-preserved.txt").is_file())

        f.run("ai-workspace.sh", "handoff", "-m", "Hand off fixture", cwd=slot)
        self.assertEqual(f.state(1)["phase"], "READY")
        self.assertEqual(f.state(1)["head"], local_head)

        f.run("ai-manager.sh", "merge", "fix/fixture-task")
        recycle_before_push = f.run("ai-workspace.sh", "recycle", "ai-1", check=False)
        self.assertNotEqual(recycle_before_push.returncode, 0)
        self.assertIn("published", recycle_before_push.stderr)

        f.git("push", "spoiled-milk", "main")
        f.run("ai-workspace.sh", "recycle", "ai-1")
        self.assertEqual(f.git("branch", "--show-current", cwd=slot).stdout.strip(), "")
        self.assertEqual(f.state(1)["phase"], "IDLE")
        self.assertNotIn("fix/fixture-task", f.git("branch", "--format=%(refname:short)").stdout.splitlines())
        # Published main now preserves the commit, so recycle removes the
        # temporary remote branch by default instead of leaving remote clutter.
        remote_lookup = f.git(
            "rev-parse",
            "--verify",
            "refs/remotes/spoiled-milk/fix/fixture-task",
            check=False,
        )
        self.assertNotEqual(remote_lookup.returncode, 0)

    def test_dirty_or_unhanded_work_cannot_be_merged_or_reused(self) -> None:
        f = self.fixture
        f.run("ai-workspace.sh", "create", "ai-1")
        slot = f.slot(1)
        f.run("ai-workspace.sh", "start", "ai-1", "feat/unfinished")
        (slot / "unfinished.txt").write_text("not handed off\n", encoding="utf-8")

        merge = f.run("ai-manager.sh", "merge", "feat/unfinished", check=False)
        self.assertNotEqual(merge.returncode, 0)
        self.assertIn("dirty", merge.stderr)
        start_again = f.run("ai-workspace.sh", "start", "ai-1", "fix/other", check=False)
        self.assertNotEqual(start_again.returncode, 0)
        self.assertIn("dirty", start_again.stderr)

        f.run("ai-manager.sh", "rescue", "ai-1", "-m", "Rescue unfinished fixture")
        self.assertEqual(f.state(1)["phase"], "READY")
        self.assertEqual(f.git("status", "--porcelain", cwd=slot).stdout, "")
        rescued_branch = f.git("branch", "--show-current", cwd=slot).stdout.strip()
        self.assertEqual(rescued_branch, "feat/unfinished")
        self.assertEqual(
            f.git("rev-parse", "HEAD", cwd=slot).stdout.strip(),
            f.git("rev-parse", f"refs/remotes/spoiled-milk/{rescued_branch}", cwd=slot).stdout.strip(),
        )

    def test_handoff_marker_detects_later_commit_and_release_gate_detects_stash(self) -> None:
        f = self.fixture
        f.run("ai-workspace.sh", "create", "ai-1")
        slot = f.slot(1)
        f.run("ai-workspace.sh", "start", "ai-1", "docs/handoff-race")
        (slot / "handoff.txt").write_text("first\n", encoding="utf-8")
        f.run("ai-workspace.sh", "handoff", "-m", "Ready first tip", cwd=slot)

        (slot / "handoff.txt").write_text("second\n", encoding="utf-8")
        f.git("add", "handoff.txt", cwd=slot)
        f.git("commit", "-m", "Advance after handoff", cwd=slot)
        merge = f.run("ai-manager.sh", "merge", "docs/handoff-race", check=False)
        self.assertNotEqual(merge.returncode, 0)
        self.assertIn("changed after handoff", merge.stderr)

        f.run("ai-workspace.sh", "handoff", "-m", "Ready second tip", cwd=slot)
        f.run("ai-manager.sh", "release-check")
        (slot / "temporary.txt").write_text("stash sentinel\n", encoding="utf-8")
        f.git("add", "temporary.txt", cwd=slot)
        f.git("stash", "push", "-m", "fixture stash", cwd=slot)
        release = f.run("ai-manager.sh", "release-check", check=False)
        self.assertNotEqual(release.returncode, 0)
        self.assertIn("stash", release.stderr.lower())

    def test_rescue_gives_detached_files_a_named_remote_branch(self) -> None:
        f = self.fixture
        f.run("ai-workspace.sh", "create", "ai-1")
        slot = f.slot(1)
        (slot / "detached-rescue.txt").write_text("preserve detached work\n", encoding="utf-8")
        f.run("ai-manager.sh", "rescue", "ai-1", "-m", "Rescue detached work")
        branch = f.git("branch", "--show-current", cwd=slot).stdout.strip()
        self.assertTrue(branch.startswith("rescue/ai-1/"))
        self.assertEqual(f.state(1)["phase"], "READY")
        self.assertEqual(
            f.git("rev-parse", "HEAD", cwd=slot).stdout.strip(),
            f.git("rev-parse", f"refs/remotes/spoiled-milk/{branch}", cwd=slot).stdout.strip(),
        )

    def test_rescue_still_preserves_worker_when_manager_is_dirty(self) -> None:
        f = self.fixture
        f.run("ai-workspace.sh", "create", "ai-1")
        slot = f.slot(1)
        (slot / "detached-rescue.txt").write_text("worker must survive\n", encoding="utf-8")
        (f.root / "manager-notes.txt").write_text("manager is messy too\n", encoding="utf-8")

        f.run("ai-manager.sh", "rescue", "ai-1", "-m", "Rescue despite dirty manager")
        branch = f.git("branch", "--show-current", cwd=slot).stdout.strip()
        self.assertTrue(branch.startswith("rescue/ai-1/"))
        self.assertEqual(f.state(1)["phase"], "READY")

    def test_release_gate_rejects_unmanaged_orphan_branch(self) -> None:
        f = self.fixture
        f.git("branch", "fix/orphaned-task")
        result = f.run("ai-manager.sh", "release-check", check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Unmanaged local branch", result.stderr)

    def test_status_identifies_manager_slots_and_backup_state(self) -> None:
        f = self.fixture
        f.run("ai-workspace.sh", "create", "ai-1")
        f.run("ai-workspace.sh", "start", "ai-1", "fix/status")
        status = f.run("ai-workspace.sh", "status").stdout
        self.assertIn("[MANAGER]", status)
        self.assertIn("[AI-SLOT]", status)
        self.assertIn("branch=fix/status", status)
        self.assertIn("remote=MISSING", status)

    def test_cross_project_invocation_is_rejected(self) -> None:
        f = self.fixture
        outside = f.base / "unrelated-project"
        outside.mkdir()
        for script in ("ai-workspace.sh", "ai-manager.sh"):
            result = subprocess.run(
                ["bash", str(f.root / "scripts" / script), "status"],
                cwd=outside,
                env={**f.env, "ROOT_DIR": str(f.root)},
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Refusing cross-project invocation", result.stderr)

    def test_clean_detached_unique_commit_is_blocked_then_rescued(self) -> None:
        f = self.fixture
        f.run("ai-workspace.sh", "create", "ai-1")
        slot = f.slot(1)
        (slot / "detached-commit.txt").write_text("must survive\n", encoding="utf-8")
        f.git("add", "detached-commit.txt", cwd=slot)
        f.git("commit", "-m", "Detached work", cwd=slot)

        start = f.run("ai-workspace.sh", "start", "ai-1", "fix/reuse", check=False)
        self.assertNotEqual(start.returncode, 0)
        self.assertIn("detached commits", start.stderr)
        recycle = f.run("ai-workspace.sh", "recycle", "ai-1", check=False)
        self.assertNotEqual(recycle.returncode, 0)
        self.assertIn("detached commits", recycle.stderr)
        release = f.run("ai-manager.sh", "release-check", check=False)
        self.assertNotEqual(release.returncode, 0)
        self.assertIn("ambiguous registration", release.stderr)

        f.run("ai-manager.sh", "rescue", "ai-1", "-m", "Rescue clean detached commit")
        branch = f.git("branch", "--show-current", cwd=slot).stdout.strip()
        self.assertTrue(branch.startswith("rescue/ai-1/"))
        self.assertEqual(f.state(1)["phase"], "READY")

    def test_checkpoint_quarantines_sensitive_and_large_files(self) -> None:
        f = self.fixture
        f.run("ai-workspace.sh", "create", "ai-1")
        slot = f.slot(1)
        f.run("ai-workspace.sh", "start", "ai-1", "test/quarantine")

        (slot / ".env").write_text("TOKEN=not-a-real-secret\n", encoding="utf-8")
        blocked = f.run("ai-workspace.sh", "checkpoint", "-m", "Risky", cwd=slot, check=False)
        self.assertNotEqual(blocked.returncode, 0)
        self.assertIn("quarantined", blocked.stderr.lower())
        self.assertEqual(f.state(1)["phase"], "QUARANTINED")
        self.assertIn(".env", f.git("diff", "--cached", "--name-only", cwd=slot).stdout)
        f.run(
            "ai-workspace.sh",
            "checkpoint",
            "-m",
            "Reviewed fixture secret",
            "--allow-sensitive",
            cwd=slot,
        )

        remote_before = f.git("rev-parse", "refs/remotes/spoiled-milk/test/quarantine", cwd=slot).stdout.strip()
        (slot / "notes.txt").write_text(
            "ghp_" + ("A" * 32) + "\n" + ("ordinary notes\n" * 40000),
            encoding="utf-8",
        )
        blocked = f.run("ai-workspace.sh", "checkpoint", "-m", "Content secret", cwd=slot, check=False)
        self.assertNotEqual(blocked.returncode, 0)
        self.assertIn("credential signature", blocked.stderr)
        self.assertEqual(
            f.git("rev-parse", "refs/remotes/spoiled-milk/test/quarantine", cwd=slot).stdout.strip(),
            remote_before,
        )
        (slot / "notes.txt").unlink()

        f.env["AI_MAX_CHECKPOINT_BLOB_BYTES"] = "8"
        (slot / "large.bin").write_bytes(b"0123456789")
        blocked = f.run("ai-workspace.sh", "checkpoint", "-m", "Too large", cwd=slot, check=False)
        self.assertNotEqual(blocked.returncode, 0)
        self.assertIn("--allow-large", blocked.stderr)
        f.run(
            "ai-workspace.sh",
            "checkpoint",
            "-m",
            "Reviewed fixture blob",
            "--allow-large",
            cwd=slot,
        )

    def test_recycle_recovers_after_interruption_post_branch_delete(self) -> None:
        f = self.fixture
        f.run("ai-workspace.sh", "create", "ai-1")
        slot = f.slot(1)
        branch = "fix/interrupted-recycle"
        f.run("ai-workspace.sh", "start", "ai-1", branch)
        (slot / "finished.txt").write_text("published work\n", encoding="utf-8")
        f.run("ai-workspace.sh", "handoff", "-m", "Ready for interrupted recycle", cwd=slot)
        f.run("ai-manager.sh", "merge", branch)
        f.git("push", "spoiled-milk", "main")

        state_path = f.root / ".git" / "ai-workspaces" / "ai-1.state"
        state_text = state_path.read_text(encoding="utf-8")
        state_text = state_text.replace("phase=READY", "phase=RECYCLING")
        state_text = state_text.replace("remote_policy=-", "remote_policy=DELETE")
        state_path.write_text(state_text, encoding="utf-8")
        f.git("push", "spoiled-milk", "--delete", branch)
        f.git("switch", "--detach", "spoiled-milk/main", cwd=slot)
        f.git("branch", "-d", branch)

        f.run("ai-workspace.sh", "recycle", "ai-1")
        self.assertEqual(f.state(1)["phase"], "IDLE")
        self.assertEqual(f.git("branch", "--show-current", cwd=slot).stdout.strip(), "")
        f.run("ai-workspace.sh", "start", "ai-1", "fix/after-recovery")

    def test_manager_release_forwards_args_and_rejects_skip_build(self) -> None:
        f = self.fixture
        capture = f.base / "release-args.txt"
        layered_capture = f.base / "layered-release-args.txt"
        f.run("ai-manager.sh", "release", "--version", "v1.2.3", "--assets-cleared")
        self.assertEqual(capture.read_text(encoding="utf-8").splitlines(), ["--version", "v1.2.3", "--assets-cleared"])
        self.assertEqual(
            layered_capture.read_text(encoding="utf-8").splitlines(),
            ["--version", "v1.2.3"],
        )
        capture.unlink()
        layered_capture.unlink()
        blocked = f.run("ai-manager.sh", "release", "--skip-build", "--assets-cleared", check=False)
        self.assertNotEqual(blocked.returncode, 0)
        self.assertIn("cannot use --skip-build", blocked.stderr)
        self.assertFalse(capture.exists())
        self.assertFalse(layered_capture.exists())

    def test_manager_collects_exact_external_handoff_without_merging(self) -> None:
        f = self.fixture
        f.run("ai-workspace.sh", "create", "ai-1")
        contributor = f.base / "external-contributor"
        subprocess.run(
            ["git", "clone", "--branch", "main", str(f.remote), str(contributor)],
            check=True,
            capture_output=True,
            text=True,
        )
        f.git("config", "user.name", "Goutan", cwd=contributor)
        f.git("config", "user.email", "goutan@example.test", cwd=contributor)
        branch = "goutan/fix/external-fixture"
        f.git("switch", "-c", branch, cwd=contributor)
        (contributor / "external.txt").write_text("review me\n", encoding="utf-8")
        f.git("add", "external.txt", cwd=contributor)
        f.git("commit", "-m", "External fixture", cwd=contributor)
        expected_head = f.git("rev-parse", "HEAD", cwd=contributor).stdout.strip()
        f.git("push", "origin", branch, cwd=contributor)

        f.run("ai-manager.sh", "collect-contributor", "ai-1", branch, expected_head)
        self.assertEqual(f.state(1)["phase"], "READY")
        self.assertEqual(f.state(1)["head"], expected_head)
        self.assertEqual(f.git("branch", "--show-current", cwd=f.slot(1)).stdout.strip(), branch)
        self.assertEqual(f.git("rev-parse", "main").stdout.strip(), f.git("rev-parse", "spoiled-milk/main").stdout.strip())

        moved = expected_head[:-1] + ("0" if expected_head[-1] != "0" else "1")
        refused = f.run(
            "ai-manager.sh",
            "collect-contributor",
            "ai-2",
            branch,
            moved,
            check=False,
        )
        self.assertNotEqual(refused.returncode, 0)
        self.assertIn("moved", refused.stderr)

    def test_start_rejects_non_idle_state_and_status_survives_missing_slot(self) -> None:
        f = self.fixture
        f.run("ai-workspace.sh", "create", "ai-1")
        state_path = f.root / ".git" / "ai-workspaces" / "ai-1.state"
        state_path.write_text(state_path.read_text(encoding="utf-8").replace("phase=IDLE", "phase=READY"), encoding="utf-8")
        blocked = f.run("ai-workspace.sh", "start", "ai-1", "fix/wrong-state", check=False)
        self.assertNotEqual(blocked.returncode, 0)
        self.assertIn("not IDLE", blocked.stderr)

        shutil.rmtree(f.slot(1))
        status = f.run("ai-workspace.sh", "status")
        self.assertIn("STALE/PRUNABLE", status.stdout)

    def test_git_status_failure_is_never_classified_as_clean(self) -> None:
        f = self.fixture
        common = f.root / "scripts" / "lib" / "ai-workspace-common.sh"
        script = (
            f"source {common}; "
            "ai_dirty_status() { return 1; }; "
            "ai_is_clean \"$ROOT_DIR\""
        )
        result = subprocess.run(
            ["bash", "-c", script],
            cwd=f.root,
            env=f.env,
            capture_output=True,
            text=True,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("refusing to classify", result.stderr)


if __name__ == "__main__":
    unittest.main()
