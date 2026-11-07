#!/usr/bin/env python3
"""Negative process/data guards for the optional native benchmark tooling."""
import contextlib
import io
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import socket
import subprocess
import time
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
                for command in (benchmark.start, benchmark.stop, benchmark.capture, benchmark.preview):
                    with self.assertRaises(ValueError): command(args)
                spawn.assert_not_called(); kill.assert_not_called()

    def test_relog_requires_completed_first_session(self):
        with tempfile.TemporaryDirectory() as name:
            run=Path(name)
            with self.assertRaisesRegex(ValueError,"completed first login"): benchmark.capture_history(run,"relog")
            first=run/"visual-first"; first.mkdir(); (first/"command.json").write_text("{}")
            with self.assertRaisesRegex(ValueError,"completed first login"): benchmark.capture_history(run,"relog")
            with self.assertRaisesRegex(ValueError,"fresh server"): benchmark.capture_history(run,"visual")
            (first/"CLIENT-DONE").touch()
            self.assertEqual(benchmark.capture_history(run,"relog"),first)

    def test_relog_does_not_allow_unbounded_reuse(self):
        with tempfile.TemporaryDirectory() as name:
            run=Path(name)
            for subject in ("first","second"):
                capture=run/subject; capture.mkdir(); (capture/"command.json").write_text("{}"); (capture/"CLIENT-DONE").touch()
            with self.assertRaisesRegex(ValueError,"exactly one"): benchmark.capture_history(run,"relog")

    def test_stale_pid_never_signals_unrelated_process(self):
        with tempfile.TemporaryDirectory() as name:
            run = Path(name); (run / benchmark.MARKER).write_text("{}")
            (run / "processes.json").write_text(json.dumps(dict(client=os.getpid(), server=os.getpid())))
            with patch.object(benchmark.os, "kill") as kill, patch.object(benchmark.signal,"pidfd_send_signal") as send:
                with self.assertRaisesRegex(ValueError, "Client PID ownership mismatch"):
                    benchmark.stop(SimpleNamespace(run=run))
                kill.assert_not_called(); send.assert_not_called()

    def test_exited_zombie_client_is_not_signaled(self):
        child=subprocess.Popen(["python3","-c","pass"])
        try:
            deadline=time.monotonic()+2
            while time.monotonic()<deadline:
                status=(Path("/proc")/str(child.pid)/"stat").read_text().rsplit(") ",1)[1].split()[0]
                if status=="Z": break
                time.sleep(.01)
            with patch.object(benchmark.signal,"pidfd_send_signal") as send:
                benchmark.signal_owned_client(child.pid,Path("/not-owned"))
                send.assert_not_called()
        finally: child.wait(timeout=2)

    def test_port_probe_refuses_live_listener(self):
        with socket.socket() as listener:
            listener.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
            listener.bind(("127.0.0.1",0)); listener.listen(1)
            with patch.object(benchmark,"PORTS",(listener.getsockname()[1],)):
                with self.assertRaises(OSError): benchmark.free_ports()

    def test_port_probe_allows_own_closed_connection_time_wait(self):
        with socket.socket() as listener:
            listener.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
            listener.bind(("127.0.0.1",0)); listener.listen(1); port=listener.getsockname()[1]
            with socket.create_connection(("127.0.0.1",port)) as client:
                connection,_=listener.accept()
                connection.shutdown(socket.SHUT_WR); client.recv(1); connection.close()
        with patch.object(benchmark,"PORTS",(port,)): benchmark.free_ports()

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

    def test_partial_server_override_refused_before_staging(self):
        args=SimpleNamespace(name="unused",server_core=Path("missing.jar"),server_overlay=None,server_source=None)
        with self.assertRaisesRegex(ValueError,"matched core"):
            benchmark.prepare(args)

    def test_fixture_runtime_exception_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            run=Path(name); (run/"server.log").write_text("java.lang.IllegalStateException: No authored corridor\n")
            with self.assertRaisesRegex(ValueError,"Unexpected server runtime"):
                benchmark.validate_runtime(run,[],"busy-walk")

    def test_walk_requires_actual_four_tile_movement(self):
        with tempfile.TemporaryDirectory() as name:
            run=Path(name)
            with self.assertRaisesRegex(ValueError,"actual viewer"):
                benchmark.validate_runtime(run,[dict(serverState=dict(x=502,y=453))],"busy-walk")
            result=benchmark.validate_runtime(run,[dict(serverState=dict(x=x,y=453)) for x in (502,506)],"busy-walk")
            self.assertEqual(result["maxAxisDistanceTiles"],4)

    def test_report_requires_complete_client(self):
        with tempfile.TemporaryDirectory() as name:
            run = Path(name); (run / benchmark.MARKER).write_text("{}")
            capture = run / "capture"; capture.mkdir()
            with self.assertRaisesRegex(ValueError, "client completion"):
                benchmark.report(SimpleNamespace(capture=capture))

    def test_report_gpu_edges_duplicates_and_presented_intervals(self):
        with tempfile.TemporaryDirectory() as name:
            run=Path(name); (run/benchmark.MARKER).write_text("{}")
            capture=run/"capture"; capture.mkdir(); (capture/"CLIENT-DONE").touch()
            (capture/"manifest.json").write_text("{}")
            (capture/"scenario.json").write_text('{"visiblePreview":true}')
            events=[dict(eventType="renderer.performance-phase",phaseId=1,action=action,sessionElapsedNanos=when,label="test") for action,when in [("start",0),("stop",10_000_000_000)]]
            (capture/"events.jsonl").write_text("".join(json.dumps(r)+"\n" for r in events))
            telemetry=dict(trigger="periodic",sessionElapsedNanos=5_000_000_000,**{"stage.openGLFrameInterval.window.samplesNanos":[16_000_000,18_000_000],"counter.openGLWorldChunkTriangle.recent.latest":123})
            (capture/"telemetry.jsonl").write_text(json.dumps(telemetry)+"\n")
            (capture/"client-samples.jsonl").write_text('{"contentViewportWidth":1849,"contentViewportHeight":1040,"surface":{"framebufferWidth":1920,"framebufferHeight":1040,"windowWidth":1920,"windowHeight":1040},"windowState":1}\n')
            (capture/"gpu-samples.csv").write_text("pass,sequence,sessionElapsedNanos,gpuNanos\nframe-render,1,100000000,1000000\nframe-render,2,2000000000,2000000\nframe-render,2,2000000000,2000000\nframe-render,3,9500000000,3000000\n")
            with contextlib.redirect_stdout(io.StringIO()): benchmark.report(SimpleNamespace(capture=capture))
            summary=json.loads((capture/"benchmark-summary.json").read_text())
            self.assertEqual(summary["gpuDuplicateSamples"],1)
            self.assertEqual(summary["gpuCoverage"]["passes"]["frame-render"],dict(allPhaseUniqueSamples=3,retainedSamples=1,phaseEdgeExcludedSamples=2))
            self.assertEqual(summary["gpu"]["frame-render"]["meanms"],2)
            self.assertEqual(summary["stages"]["openGLFrameInterval"]["over16_667ms"],1)
            self.assertTrue(summary["presentationEvidence"]["allSamplesVisibleUniconified"])
            self.assertFalse(summary["presentationEvidence"]["contentMatches1080Target"])
            self.assertEqual(summary["surfaceContext"]["framebufferWidth"],[1920])
            self.assertTrue(summary["visiblePreview"])
            self.assertFalse(summary["fullClientPresentation60FpsAccepted"])
            self.assertEqual(summary["sceneInventory"]["openGLWorldChunkTriangle"]["min"],123)


if __name__ == "__main__": unittest.main()
