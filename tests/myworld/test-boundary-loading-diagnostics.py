#!/usr/bin/env python3
import json
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base"
CLIENT_JAR = CLIENT / "Open_RSC_Client.jar"
SOURCE = CLIENT / "src/orsc/BoundaryLoadingDiagnostics.java"
TELEMETRY = CLIENT / "src/orsc/RenderTelemetry.java"
PACKETS = CLIENT / "src/orsc/PacketHandler.java"
WORLD = CLIENT / "src/orsc/graphics/three/World.java"
CLIENT_MAIN = CLIENT / "src/orsc/mudclient.java"
DIAGNOSTIC_SESSION = CLIENT / "src/orsc/RendererDiagnosticSession.java"
RUNTIME_LOGGER = CLIENT / "src/orsc/ClientRuntimeLogger.java"
LAUNCHER = ROOT / "scripts/run-client.sh"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def run(command: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, cwd=ROOT, text=True, capture_output=True)


def ensure_client_jar() -> None:
    inputs = (
        SOURCE,
        TELEMETRY,
        PACKETS,
        WORLD,
        CLIENT_MAIN,
        DIAGNOSTIC_SESSION,
        RUNTIME_LOGGER,
    )
    if CLIENT_JAR.exists() and all(
        source.stat().st_mtime <= CLIENT_JAR.stat().st_mtime
        for source in inputs
    ):
        return
    result = run([str(ROOT / "scripts/build-client.sh")])
    if result.returncode != 0:
        fail("client build failed:\n" + result.stdout + result.stderr)


FIXTURE = r"""
package orsc;

public final class BoundaryLoadingDiagnosticsFixture {
    private static void runSettledTrace(int contextSequence, int centerX) {
        long before = System.nanoTime();
        BoundaryLoadingDiagnostics.recordOpenGLPhases(
            1_000_000L, 2_000_000L, 100_000L, 200_000L, 50_000L, 300_000L);
        BoundaryLoadingDiagnostics.recordOpenGLWorldFrame(
            10, 1000, 64L, 1, 1, 0, 0,
            500_000L, 250_000L, 350_000L, true, false);
        BoundaryLoadingDiagnostics.recordOpenGLFrame(
            contextSequence * 10L, 100_000L, 7_000_000L, 16_000_000L);
        BoundaryLoadingDiagnostics.recordPrediction(
            0, centerX, 13, 5, 300_000L, 4_000_000L,
            8_000_000L, true, 900, 12, 4);
        BoundaryLoadingDiagnostics.beginContextTransition(
            8, contextSequence, 100 + contextSequence,
            centerX * 48, 13 * 48, 0, before);
        BoundaryLoadingDiagnostics.updateDestination(
            0, centerX, 13, false, true);
        for (int index = 0; index < 20; index++) {
            BoundaryLoadingDiagnostics.recordPhase(
                "fixture", "phase-" + index, before, index + 1L);
        }
        BoundaryLoadingDiagnostics.recordPacket(
            157, 256, before, 2_000_000L);
        BoundaryLoadingDiagnostics.recordRegionTransition(
            false, false, 48, 0, 1100, 40, 2, 12);
        BoundaryLoadingDiagnostics.recordStaticPresentationBuild(
            100, 80, 20, 1_000_000L, 2_000_000L,
            3_000_000L, true, 3);
        BoundaryLoadingDiagnostics.recordDiskRead(
            "tile-archive", 4096L, before, 900_000L);
        BoundaryLoadingDiagnostics.recordLockWait(
            "prepared-cache", before, 100_000L);
        BoundaryLoadingDiagnostics.recordAtomicActivationProgress(
            true, true, true, 12L);
        BoundaryLoadingDiagnostics.recordOpenGLPhases(
            2_000_000L, 4_000_000L, 200_000L, 300_000L, 100_000L, 400_000L);
        BoundaryLoadingDiagnostics.recordOpenGLWorldFrame(
            120, 20000, 8192L, 20, 16, 4, 0,
            5_000_000L, 1_000_000L, 2_000_000L, false, true);
        BoundaryLoadingDiagnostics.recordOpenGLShadow(
            3_000_000L, 1_000_000L, false);
        BoundaryLoadingDiagnostics.recordOpenGLFrame(
            contextSequence * 10L + 1L,
            2_000_000L, 10_000_000L, 20_000_000L);
        BoundaryLoadingDiagnostics.recordPresentationFrame(
            contextSequence, 4_000_000L, 1_000_000L, 2_000_000L);
        BoundaryLoadingDiagnostics.recordPresentationRelease(
            2, true, 120, 20000);
        BoundaryLoadingDiagnostics.recordOpenGLPhases(
            3_000_000L, 5_000_000L, 300_000L, 400_000L, 100_000L, 500_000L);
        BoundaryLoadingDiagnostics.recordOpenGLWorldFrame(
            120, 20000, 0L, 20, 0, 20, 0,
            0L, 1_000_000L, 2_000_000L, true, false);
        BoundaryLoadingDiagnostics.recordOpenGLFrame(
            contextSequence * 10L + 2L,
            0L, 30_000_000L, 40_000_000L);
    }

    public static void main(String[] args) {
        RendererDiagnosticSession.start();
        runSettledTrace(1, 2);
        long now = System.nanoTime();
        BoundaryLoadingDiagnostics.beginContextTransition(
            8, 2, 102, 144, 624, 0, now);
        BoundaryLoadingDiagnostics.updateDestination(0, 3, 13, false, false);
        BoundaryLoadingDiagnostics.beginContextTransition(
            8, 3, 103, 192, 624, 0, now);
        RendererDiagnosticSession.close();
        System.out.println("PASS: deterministic bounded boundary diagnostics");
    }
}
"""


def read_jsonl(path: Path) -> list[dict]:
    if not path.exists():
        return []
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line
    ]


def main() -> None:
    ensure_client_jar()
    source_text = SOURCE.read_text(encoding="utf-8")
    telemetry_text = TELEMETRY.read_text(encoding="utf-8")
    packet_text = PACKETS.read_text(encoding="utf-8")
    world_text = WORLD.read_text(encoding="utf-8")
    client_text = CLIENT_MAIN.read_text(encoding="utf-8")
    diagnostic_session_text = DIAGNOSTIC_SESSION.read_text(encoding="utf-8")
    runtime_logger_text = RUNTIME_LOGGER.read_text(encoding="utf-8")
    launcher_text = LAUNCHER.read_text(encoding="utf-8")

    for haystack, snippet, label in (
        (source_text, "private static final boolean ENABLED", "disabled gate"),
        (source_text, "createRecentFrames()", "bounded recent-frame allocation"),
        (source_text, "if (!ENABLED) {\n\t\t\treturn null;", "disabled storage allocation"),
        (source_text, "DEFAULT_MAX_TRANSITIONS = 256", "transition bound"),
        (source_text, "DEFAULT_MAX_SPANS = 192", "span bound"),
        (source_text, "frame.opengl.renderP99Nanos", "frame percentile output"),
        (source_text, "runtime.gcTimeMillisDelta", "GC accounting"),
        (source_text, "runtime.thread.opengl", "thread allocation accounting"),
        (telemetry_text, "recordOpenGLWorldFrame", "GPU residency hook"),
        (telemetry_text, "recordOpenGLShadow", "shadow hook"),
        (telemetry_text, "recordPresentationFrame", "presentation hook"),
        (packet_text, "beginContextTransition", "packet context hook"),
        (packet_text, "recordPrediction", "prediction hook"),
        (packet_text, "recordAtomicActivationProgress", "atomic activation hook"),
        (world_text, '"minimap",\n\t\t\t\t\t\t"publish"', "minimap hook"),
        (world_text, "recordBoundaryDiskRead", "disk hook"),
        (world_text, "recordBoundaryLockWait", "lock hook"),
        (client_text, "recordStaticPresentationBuild", "scenery mesh hook"),
        (client_text, "recordPresentationRelease", "atomic presentation hook"),
        (diagnostic_session_text, '"diagnostic-event-log"', "diagnostic-output timing hook"),
        (runtime_logger_text, '"client-runtime-log"', "runtime-log timing hook"),
        (launcher_text, "--boundary-diagnostics", "launcher option"),
        (launcher_text, 'FRAME_CAPTURE_ENABLED=false', "capture isolation"),
    ):
        if snippet not in haystack:
            fail(f"missing {label}: {snippet}")

    with tempfile.TemporaryDirectory(prefix="boundary-diagnostics-") as raw_tmp:
        tmp = Path(raw_tmp)
        source_dir = tmp / "source/orsc"
        classes_dir = tmp / "classes"
        source_dir.mkdir(parents=True)
        classes_dir.mkdir()
        fixture = source_dir / "BoundaryLoadingDiagnosticsFixture.java"
        fixture.write_text(FIXTURE, encoding="utf-8")
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
                str(fixture),
            ]
        )
        if compile_result.returncode != 0:
            fail(
                "fixture compile failed:\n"
                + compile_result.stdout
                + compile_result.stderr
            )

        disabled_dir = tmp / "disabled"
        disabled = run(
            [
                "java",
                f"-Dspoiledmilk.rendererDiagnosticSessionDir={disabled_dir}",
                "-cp",
                f"{classes_dir}:{CLIENT_JAR}",
                "orsc.BoundaryLoadingDiagnosticsFixture",
            ]
        )
        if disabled.returncode != 0:
            fail("disabled fixture failed:\n" + disabled.stdout + disabled.stderr)
        if disabled_dir.exists():
            fail("disabled boundary diagnostics created output")

        enabled_dir = tmp / "enabled"
        enabled = run(
            [
                "java",
                "-Dspoiledmilk.rendererDiagnostics=true",
                "-Dspoiledmilk.boundaryDiagnostics=true",
                "-Dspoiledmilk.boundaryDiagnostics.maxTransitions=2",
                "-Dspoiledmilk.boundaryDiagnostics.maxSpans=8",
                "-Dspoiledmilk.boundaryDiagnostics.postFrames=1",
                f"-Dspoiledmilk.rendererDiagnosticSessionDir={enabled_dir}",
                "-Dspoiledmilk.testCredential=must-not-appear",
                "-cp",
                f"{classes_dir}:{CLIENT_JAR}",
                "orsc.BoundaryLoadingDiagnosticsFixture",
            ]
        )
        if enabled.returncode != 0:
            fail("enabled fixture failed:\n" + enabled.stdout + enabled.stderr)
        events = read_jsonl(enabled_dir / "events.jsonl")
        traces = [
            event
            for event in events
            if event.get("eventType") == "boundary.transition-summary"
        ]
        if len(traces) != 2:
            fail(f"expected exactly two bounded trace summaries: {traces}")
        settled, superseded = traces
        if settled.get("completion") != "settled":
            fail(f"settled completion missing: {settled}")
        if superseded.get("completion") != "superseded":
            fail(f"superseded completion missing: {superseded}")
        if superseded.get("diagnostics.suppressedTransitions") != 1:
            fail(f"transition suppression accounting missing: {superseded}")
        if settled.get("span.dropped", 0) <= 0:
            fail(f"span bound was not exercised: {settled}")
        if len(settled.get("span.names", [])) > 8:
            fail(f"span storage exceeded configured bound: {settled}")
        if len(settled.get("phase.names", [])) > 64:
            fail(f"phase storage exceeded fixed bound: {settled}")
        if len(settled.get("frame.opengl.renderNanos", [])) > 96:
            fail(f"frame storage exceeded fixed bound: {settled}")
        if settled.get("frame.opengl.renderP50Nanos") != 10_000_000:
            fail(f"frame p50 accounting incorrect: {settled}")
        if settled.get("frame.opengl.renderP95Nanos") != 30_000_000:
            fail(f"frame p95 accounting incorrect: {settled}")
        if settled.get("frame.opengl.renderP99Nanos") != 30_000_000:
            fail(f"frame p99 accounting incorrect: {settled}")
        if settled.get("frame.opengl.renderMaxNanos") != 30_000_000:
            fail(f"frame max accounting incorrect: {settled}")
        if settled.get("disk.reads") != 1 or settled.get("lock.waitCount") != 1:
            fail(f"disk/lock accounting incorrect: {settled}")
        if settled.get("prediction.matched") is not True:
            fail(f"prediction correlation missing: {settled}")
        serialized = json.dumps(events).lower()
        for forbidden in (
            "must-not-appear",
            "credential",
            "password",
            "username",
            "networkaddress",
            "chat",
        ):
            if forbidden in serialized:
                fail(f"privacy-sensitive value leaked into events: {forbidden}")

    print("PASS: boundary diagnostics are disabled-clean, bounded, and correlated")


if __name__ == "__main__":
    main()
