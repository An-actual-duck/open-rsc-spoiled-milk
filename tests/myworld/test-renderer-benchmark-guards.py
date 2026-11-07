#!/usr/bin/env python3
"""Negative process/data guards for the optional native benchmark tooling."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("renderer_benchmark", ROOT / "scripts/renderer-benchmark.py")
benchmark = importlib.util.module_from_spec(spec); spec.loader.exec_module(benchmark)


class GuardTests(unittest.TestCase):
    def test_unmarked_run_refused_before_start_or_signal(self):
        with tempfile.TemporaryDirectory() as name:
            args = SimpleNamespace(run=Path(name))
            with patch.object(benchmark.subprocess, "Popen") as spawn, patch.object(benchmark.os, "kill") as kill:
                for command in (benchmark.start, benchmark.stop):
                    with self.assertRaises(ValueError): command(args)
                spawn.assert_not_called(); kill.assert_not_called()

    def test_stale_pid_never_signals_unrelated_process(self):
        with tempfile.TemporaryDirectory() as name:
            run = Path(name); (run / benchmark.MARKER).write_text("{}")
            (run / "processes.json").write_text(json.dumps(dict(client=os.getpid(), server=os.getpid())))
            with patch.object(benchmark.os, "kill") as kill:
                with self.assertRaisesRegex(ValueError, "Client PID ownership mismatch"):
                    benchmark.stop(SimpleNamespace(run=run))
                kill.assert_not_called()

    def test_changed_immutable_server_jar_refused(self):
        with tempfile.TemporaryDirectory() as name:
            run = Path(name); (run / "server/inc/sqlite").mkdir(parents=True)
            (run / "server/inc/sqlite/renderer_benchmark_dev.db").write_bytes(b"disposable")
            jar = run / "server/core.jar"; jar.write_bytes(b"original")
            marker = dict(schema=1, private=True, tcp=43639, websocket=43539, artifacts=[dict(path="core.jar", sha256=benchmark.sha(jar))])
            (run / benchmark.MARKER).write_text(json.dumps(marker)); jar.write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "Immutable server artifact mismatch"):
                benchmark.validate_artifacts(run, include_client=False)

    def test_symlink_database_refused(self):
        with tempfile.TemporaryDirectory() as name:
            run = Path(name); (run / "server/inc/sqlite").mkdir(parents=True)
            data = run / "preserved.db"; data.write_bytes(b"must remain untouched")
            (run / "server/inc/sqlite/renderer_benchmark_dev.db").symlink_to(data)
            (run / benchmark.MARKER).write_text(json.dumps(dict(schema=1,private=True,tcp=43639,websocket=43539,artifacts=[])))
            with self.assertRaisesRegex(ValueError, "regular database"):
                benchmark.validate_artifacts(run, include_client=False)
            self.assertEqual(data.read_bytes(), b"must remain untouched")


if __name__ == "__main__": unittest.main()
