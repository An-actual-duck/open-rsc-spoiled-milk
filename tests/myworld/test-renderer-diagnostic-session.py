#!/usr/bin/env python3
import json
import os
import signal
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
SESSION_SOURCE = ROOT / "Client_Base/src/orsc/RendererDiagnosticSession.java"
PERFORMANCE_DIAGNOSTICS_SOURCE = (
    ROOT / "Client_Base/src/orsc/RendererPerformanceDiagnostics.java"
)
OPENRSC_SOURCE = ROOT / "PC_Client/src/orsc/OpenRSC.java"
RUNTIME_LOGGER_SOURCE = ROOT / "Client_Base/src/orsc/ClientRuntimeLogger.java"
LAUNCHER = ROOT / "scripts/run-client.sh"
BOUNDED_TEE = ROOT / "scripts/bounded-log-tee.py"


JAVA_SOURCE = r"""
package orsc;

import java.io.File;

public final class RendererDiagnosticSessionFixture {
	public static void main(String[] args) {
		RendererDiagnosticSession.start();
		RendererDiagnosticSession.Record telemetry =
			RendererDiagnosticSession.newTelemetryRecord("fixture", 42L);
		if (telemetry != null) {
			telemetry.number("frame.totalNanos", 1234567L);
			telemetry.number("frame.windowAverageNanos", 765432.5);
			telemetry.numbers("frame.samplesNanos", new long[] {100L, 200L, 300L});
			RendererDiagnosticSession.writeTelemetry(telemetry);
		}
		RendererDiagnosticSession.recordEvent("fixture.event", "safe detail");
		RendererDiagnosticSession.recordThrowable(
			"fixture failure",
			new IllegalStateException("expected test exception"));
		if (RendererDiagnosticSession.isEnabled()) {
			File captureDirectory = new File(
				System.getProperty("spoiledmilk.rendererDiagnosticSessionDir"),
				"captures/capture-fixture");
			captureDirectory.mkdirs();
			RendererDiagnosticSession.recordCaptureFrame(
				"started", 7L, 0, 3, 42L, captureDirectory, 1000L, 1000L, 0L, 0L,
				false, null, new String[] {"metadata.txt"});
			RendererDiagnosticSession.recordCaptureFrame(
				"completed", 7L, 0, 3, 43L, captureDirectory, 9000L, 1000L, 4000L, 2000L,
				false, null, new String[] {"00-legacy-source.png", "metadata.txt", "summary.txt"});
		}
		RenderTelemetry.recordSceneRender(500000L);
		RenderTelemetry.recordWorldSectionLoad(600000L);
		RenderTelemetry.recordOpenGLWorldChunkUpload(
			2, 1, 1, 0, 0, "fixture-upload", 250000L, 1000000L);
		RenderTelemetry.recordOpenGLWorldChunkUpload(
			2, 0, 2, 0, 0, "steady", 100000L, 1000000L);
		RenderTelemetry.recordOpenGLWorldChunkUpload(
			2, 1, 1, 0, 0, "fixture-upload", 250000L, 1000000L);
		RenderTelemetry.recordFrame(
			2000000L,
			100000L,
			200000L,
			300000L,
			400000L,
			512,
			346,
			1.0f,
			"fixture-scaling",
			"fixture-path");
		RenderTelemetry.recordDiagnosticBoundary("capture-burst-before");
		RenderTelemetry.recordDiagnosticBoundary("capture-burst-after");
		RendererDiagnosticSession.close();
	}
}
"""


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def run(command: list[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(command, cwd=ROOT, capture_output=True, **kwargs)


def ensure_client_jar() -> None:
    if CLIENT_JAR.exists() and SESSION_SOURCE.stat().st_mtime <= CLIENT_JAR.stat().st_mtime:
        return
    result = run([str(ROOT / "scripts/build-client.sh")], text=True)
    if result.returncode != 0:
        fail("client build failed:\n" + result.stdout + result.stderr)


def read_jsonl(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


def validate_runtime_session(tmp: Path) -> None:
    ensure_client_jar()
    source_dir = tmp / "source" / "orsc"
    classes_dir = tmp / "classes"
    source_dir.mkdir(parents=True)
    classes_dir.mkdir()
    source_file = source_dir / "RendererDiagnosticSessionFixture.java"
    source_file.write_text(JAVA_SOURCE, encoding="utf-8")

    compile_result = run(
        [
            "javac",
            "-source",
            "1.8",
            "-target",
            "1.8",
            "-cp",
            str(CLIENT_JAR),
            "-d",
            str(classes_dir),
            str(source_file),
        ],
        text=True,
    )
    if compile_result.returncode != 0:
        fail("fixture compile failed:\n" + compile_result.stdout + compile_result.stderr)

    disabled_dir = tmp / "disabled"
    disabled_result = run(
        [
            "java",
            f"-Dspoiledmilk.rendererDiagnosticSessionDir={disabled_dir}",
            "-cp",
            f"{classes_dir}:{CLIENT_JAR}",
            "orsc.RendererDiagnosticSessionFixture",
        ],
        text=True,
    )
    if disabled_result.returncode != 0:
        fail("disabled fixture failed:\n" + disabled_result.stdout + disabled_result.stderr)
    if disabled_dir.exists():
        fail("disabled diagnostics created a session directory")

    session_dir = tmp / "session-fixture"
    environment = os.environ.copy()
    environment.update(
        {
            "SPOILED_MILK_CLIENT_BRANCH": "test-branch",
            "SPOILED_MILK_CLIENT_COMMIT": "0123456789abcdef",
            "SPOILED_MILK_CLIENT_TARGET_MODE": "test",
        }
    )
    enabled_result = run(
        [
            "java",
            "-Dspoiledmilk.rendererDiagnostics=true",
            "-Dspoiledmilk.rendererTelemetry=true",
            "-Dspoiledmilk.rendererTelemetryInterval=1",
            f"-Dspoiledmilk.rendererDiagnosticSessionDir={session_dir}",
            "-Dspoiledmilk.rendererExampleSetting=enabled",
            "-Dspoiledmilk.testToken=must-not-appear",
            "-cp",
            f"{classes_dir}:{CLIENT_JAR}",
            "orsc.RendererDiagnosticSessionFixture",
        ],
        text=True,
        env=environment,
    )
    if enabled_result.returncode != 0:
        fail("enabled fixture failed:\n" + enabled_result.stdout + enabled_result.stderr)

    manifest = json.loads((session_dir / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("schema") != "renderer-diagnostics" or manifest.get("schemaVersion") != 1:
        fail(f"unexpected manifest schema: {manifest}")
    if manifest.get("state") != "closed":
        fail(f"session did not close cleanly: {manifest.get('state')}")
    if manifest.get("branch") != "test-branch" or manifest.get("commit") != "0123456789abcdef":
        fail("launcher revision metadata was not retained")
    if manifest.get("settings.property.spoiledmilk.rendererExampleSetting") != "enabled":
        fail("safe renderer property missing from manifest")
    if manifest.get("openGL.renderer") != "unknown":
        fail("manifest should retain an explicit OpenGL device placeholder")
    if "must-not-appear" in json.dumps(manifest):
        fail("sensitive token-like property leaked into manifest")

    telemetry = read_jsonl(session_dir / "telemetry.jsonl")
    if len(telemetry) != 4:
        fail(f"expected fixture, periodic, and boundary telemetry records, got {len(telemetry)}")
    fixture_record = next((record for record in telemetry if record.get("trigger") == "fixture"), None)
    periodic_record = next((record for record in telemetry if record.get("trigger") == "periodic"), None)
    if fixture_record is None or periodic_record is None:
        fail(f"missing expected telemetry triggers: {telemetry}")
    boundary_triggers = [
        record.get("trigger") for record in telemetry if str(record.get("trigger", "")).startswith("capture-")
    ]
    if boundary_triggers != ["capture-burst-before", "capture-burst-after"]:
        fail(f"capture boundary telemetry missing: {boundary_triggers}")
    if fixture_record.get("rendererFrameSequence") != 42:
        fail("telemetry frame sequence missing")
    if fixture_record.get("frame.totalNanos") != 1234567:
        fail("raw numeric telemetry missing")
    if fixture_record.get("frame.samplesNanos") != [100, 200, 300]:
        fail("raw numeric telemetry arrays are missing")
    expected_periodic = {
        "frame.sourceWidth": 512,
        "frame.sourceHeight": 346,
        "frame.path": "fixture-path",
        "stage.frame.lifetime.averageNanos": 2000000,
        "stage.frame.window.p50Nanos": 2000000,
        "stage.frame.window.p95Nanos": 2000000,
        "stage.frame.window.p99Nanos": 2000000,
        "stage.frame.window.samplesNanos": [2000000],
        "stage.sceneRender.lifetime.averageNanos": 500000,
        "runtime.heap.usedBytes": int,
        "runtime.nonHeap.usedBytes": int,
        "runtime.gc.collectionCountDelta": int,
        "runtime.cpu.processTimeDeltaNanos": int,
        "runtime.thread.cpuSupported": bool,
        "runtime.thread.allocationSupported": bool,
        "runtime.allocation.javaThreadBytesDelta": int,
        "counter.openGLWorldMaterialUnclassified.window.average": 0.0,
        "counter.renderer2DSpriteCommandDropped.window.average": 0.0,
        "config.renderer2D.rotatedSpriteCommandLimit": 256,
    }
    for key, expected in expected_periodic.items():
        value = periodic_record.get(key)
        if expected is int:
            if not isinstance(value, int) or value < 0:
                fail(f"periodic telemetry field {key} is not a nonnegative integer: {value!r}")
        elif expected is bool:
            if not isinstance(value, bool):
                fail(f"periodic telemetry field {key} is not boolean: {value!r}")
        elif value != expected:
            fail(f"periodic telemetry field {key} expected {expected!r}, got {value!r}")
    if not any(
        key.startswith("runtime.memoryPool.") and key.endswith(".collection.usedBytes")
        for key in periodic_record
    ):
        fail("post-collection memory-pool telemetry is missing")
    if not any(
        key.startswith("runtime.gcCollector.") and key.endswith(".collectionTimeMillisDelta")
        for key in periodic_record
    ):
        fail("per-collector GC telemetry is missing")
    if not any(
        key.startswith("runtime.bufferPool.") and key.endswith(".memoryUsedBytes")
        for key in periodic_record
    ):
        fail("native buffer-pool telemetry is missing")

    events = read_jsonl(session_dir / "events.jsonl")
    event_types = [event.get("eventType") for event in events]
    expected_types = [
        "session.start",
        "fixture.event",
        "client.exception",
        "capture.frame.started",
        "capture.frame.completed",
        "renderer.world-section-load",
        "renderer.chunk-upload-reason-change",
        "renderer.chunk-upload-reason-change",
        "session.stop",
    ]
    if event_types != expected_types:
        fail(f"unexpected event sequence: {event_types}")
    reason_events = [
        event
        for event in events
        if event.get("eventType") == "renderer.chunk-upload-reason-change"
    ]
    if reason_events[-1].get("suppressedTransitions") != 2:
        fail(f"reason transition aggregation missing: {reason_events}")

    capture_index = read_jsonl(session_dir / "captures" / "capture-index.jsonl")
    if [record.get("status") for record in capture_index] != ["started", "completed"]:
        fail(f"unexpected capture index: {capture_index}")
    completed_capture = capture_index[-1]
    if completed_capture.get("path") != "captures/capture-fixture/":
        fail(f"capture path is not session-relative: {completed_capture.get('path')!r}")
    if completed_capture.get("layerCaptureNanos") != 4000:
        fail("capture layer timing missing")
    if completed_capture.get("captureWorkNanos") != 7000:
        fail("capture work timing is not separated from capture span")
    if completed_capture.get("artifacts") != [
        "00-legacy-source.png",
        "metadata.txt",
        "summary.txt",
    ]:
        fail(f"capture artifacts missing: {completed_capture.get('artifacts')}")


def validate_bounded_tee(tmp: Path) -> None:
    log_file = tmp / "bounded.log"
    input_text = "first retained line\n" + "x" * 160 + "\nlast visible line\n"
    result = run(
        ["python3", str(BOUNDED_TEE), str(log_file), "120"],
        input=input_text.encode("utf-8"),
    )
    if result.returncode != 0:
        fail("bounded tee failed:\n" + result.stderr.decode("utf-8", errors="replace"))
    if result.stdout.decode("utf-8") != input_text:
        fail("bounded tee did not preserve visible stdout")
    retained = log_file.read_bytes()
    if len(retained) > 120:
        fail(f"bounded tee exceeded byte budget: {len(retained)}")
    if b"byte budget reached" not in retained:
        fail("bounded tee did not mark truncation")
    if b"last visible line" in retained:
        fail("bounded tee retained content after truncation")

    interrupted_log = tmp / "interrupted.log"
    process = subprocess.Popen(
        ["python3", str(BOUNDED_TEE), str(interrupted_log), "1024"],
        cwd=ROOT,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    assert process.stdin is not None
    assert process.stdout is not None
    process.stdin.write(b"before interrupt\n")
    process.stdin.flush()
    first_line = process.stdout.readline()
    if first_line != b"before interrupt\n":
        process.kill()
        fail(f"bounded tee did not become ready for interrupt test: {first_line!r}")
    process.send_signal(signal.SIGINT)
    stdout, stderr = process.communicate(timeout=5)
    if process.returncode != 0 or b"KeyboardInterrupt" in stderr:
        fail(
            "bounded tee did not stop cleanly after interrupt:\n"
            + (first_line + stdout).decode("utf-8", errors="replace")
            + stderr.decode("utf-8", errors="replace")
        )


def validate_source_contract() -> None:
    session = SESSION_SOURCE.read_text(encoding="utf-8")
    performance_diagnostics = PERFORMANCE_DIAGNOSTICS_SOURCE.read_text(encoding="utf-8")
    openrsc = OPENRSC_SOURCE.read_text(encoding="utf-8")
    runtime_logger = RUNTIME_LOGGER_SOURCE.read_text(encoding="utf-8")
    launcher = LAUNCHER.read_text(encoding="utf-8")
    presenter = (ROOT / "PC_Client/src/orsc/OpenGLFramePresenter.java").read_text(encoding="utf-8")
    graphics = (ROOT / "Client_Base/src/orsc/graphics/two/GraphicsController.java").read_text(encoding="utf-8")
    opengl_log = (ROOT / "PC_Client/src/orsc/OpenGLRendererLog.java").read_text(encoding="utf-8")
    mudclient = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text(encoding="utf-8")

    required = {
        "disabled-by-default session gate": "spoiledmilk.rendererDiagnostics",
        "versioned JSONL telemetry": 'openWriter(new File(sessionDirectory, "telemetry.jsonl"))',
        "versioned JSONL events": 'openWriter(new File(sessionDirectory, "events.jsonl"))',
        "manifest publication": 'writeManifest("open")',
        "privacy filter": 'normalized.contains("credential")',
        "structured byte budget": "MAX_LOG_BYTES",
    }
    for description, needle in required.items():
        if needle not in session:
            fail(f"missing {description}: {needle}")
    if "RendererDiagnosticSession.start();" not in openrsc:
        fail("desktop client does not start diagnostic sessions")
    if "RendererDiagnosticSession.recordThrowable(context, throwable);" not in runtime_logger:
        fail("uncaught failures are not correlated with the diagnostic session")
    if 'RenderTelemetry.recordDiagnosticBoundary("capture-burst-before")' not in presenter:
        fail("capture request does not snapshot pre-burst telemetry")
    if 'RenderTelemetry.recordDiagnosticBoundary("capture-burst-after")' not in presenter:
        fail("capture completion does not snapshot post-burst telemetry")
    if "RendererDiagnosticSession.recordCaptureFrame(" not in presenter:
        fail("capture frames are not indexed")
    if "RenderTelemetry.recordRenderer2DOverflowEvent(stream, limit);" not in graphics:
        fail("2D overflow is not a structured event")
    if 'RendererDiagnosticSession.recordEvent("renderer.opengl.log", message);' not in opengl_log:
        fail("OpenGL renderer logs are not retained as session events")
    if 'RendererDiagnosticSession.recordEvent("client.login", null);' not in mudclient:
        fail("diagnostic sessions do not mark account-free login epochs")
    if 'RendererDiagnosticSession.recordEvent("client.logout", null);' not in mudclient:
        fail("diagnostic sessions do not mark account-free logout epochs")
    for needle in (
        '"renderer.performance-phase"',
        'RenderTelemetry.recordPerformancePhaseBoundary("start")',
        'RenderTelemetry.recordPerformancePhaseBoundary("stop")',
        "Performance markers require a renderer-diagnostics launch.",
    ):
        if needle not in performance_diagnostics:
            fail(f"performance phase diagnostics missing {needle}")
    if "RendererPerformanceDiagnostics.isCommand(var11)" not in mudclient:
        fail("client-local performance marker commands are not intercepted")
    for needle in (
        "--renderer-diagnostics",
        "SPOILED_MILK_RENDERER_TELEMETRY=true",
        "FRAME_CAPTURE_ENABLED=true",
        'SPOILED_MILK_OPENGL_FRAME_CAPTURE="$FRAME_CAPTURE_ENABLED"',
        "--jfr",
        "SPOILED_MILK_CLIENT_JFR_PATH",
        "bounded-log-tee.py",
    ):
        if needle not in launcher:
            fail(f"diagnostic launcher missing {needle}")


def main() -> None:
    validate_source_contract()
    with tempfile.TemporaryDirectory(prefix="renderer-diagnostic-session-") as tmp_name:
        tmp = Path(tmp_name)
        validate_runtime_session(tmp)
        validate_bounded_tee(tmp)
    print("PASS: renderer diagnostic session foundation is bounded and machine-readable")


if __name__ == "__main__":
    main()
