#!/usr/bin/env python3
import json
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base"
CLIENT_JAR = CLIENT / "Open_RSC_Client.jar"
SOURCE = CLIENT / "src/orsc/MovementTimingDiagnostics.java"
MUDCLIENT = CLIENT / "src/orsc/mudclient.java"
PACKET_HANDLER = CLIENT / "src/orsc/PacketHandler.java"
APPLET = ROOT / "PC_Client/src/orsc/ORSCApplet.java"
LAUNCHER = ROOT / "scripts/run-client.sh"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def run(command: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, cwd=ROOT, text=True, capture_output=True)


def ensure_client_jar() -> None:
    if CLIENT_JAR.exists() and SOURCE.stat().st_mtime <= CLIENT_JAR.stat().st_mtime:
        return
    result = run([str(ROOT / "scripts/build-client.sh")])
    if result.returncode != 0:
        fail("client build failed:\n" + result.stdout + result.stderr)


FIXTURE = r"""
package orsc;

public final class MovementTimingDiagnosticsFixture {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        MovementTimingDiagnostics.FixedHistogram histogram =
            new MovementTimingDiagnostics.FixedHistogram(
                new long[] { 10L, 20L, Long.MAX_VALUE });
        histogram.record(1L);
        histogram.record(10L);
        histogram.record(11L);
        histogram.record(20L);
        histogram.record(21L);
        require(histogram.count() == 5L, "histogram count");
        require(histogram.percentileUpperBound(0.50D) == 20L, "histogram p50");
        require(histogram.percentileUpperBound(0.95D) == 21L, "histogram overflow p95");

        MovementTimingDiagnostics.TimingAccumulator timing =
            new MovementTimingDiagnostics.TimingAccumulator(100L, 50L, 4, 2);
        require(!timing.recordPacket(10L, (byte)1, -1, -1, 2), "first movement arrival");
        require(timing.recordPacket(70L, (byte)1, -1, -1, 3), "movement arrival outlier");
        require(!timing.recordPacket(20L, (byte)2, 100, 10, 4), "first snapshot arrival");
        require(timing.recordPacket(80L, (byte)2, 101, 12, 5), "snapshot arrival outlier");
        require(!timing.recordPacket(140L, (byte)1, -1, -1, 6), "bounded outlier event output");
        require(timing.movementPacketCount() == 3L, "movement packet accounting");
        require(timing.snapshotPacketCount() == 2L, "snapshot packet accounting");
        require(timing.packetRecordCount() == 20L, "record accounting");
        require(timing.arrivalOutlierCount() == 3L, "arrival outlier accounting");
        require(timing.suppressedOutlierEventCount() == 1L, "outlier suppression accounting");
        require(timing.recentPacketLines(10).length == 4, "recent packet ring bound");

        timing.observeLocal(150L, 0);
        timing.recordWaypointAppend(190L, true, true, 1);
        require(timing.idleTransitionCount() == 1L, "idle transition accounting");
        require(timing.latestCompletedIdleDuration() == 40L, "idle duration accounting");
        require(timing.currentDepth() == 1, "waypoint depth accounting");
        require(timing.summaryDue(200L), "summary interval accounting");
        timing.startNextSummaryWindow(200L);
        require(timing.movementPacketCount() == 0L, "summary window reset");
        require(timing.recentPacketLines(10).length == 4, "recent ring retained across summaries");

        MovementTimingDiagnostics.recordMovementPacket(1, 2);
        MovementTimingDiagnostics.recordMovementSnapshot(7, 9, 3, 4);
        MovementTimingDiagnostics.markStutterObserved(null);
        RendererDiagnosticSession.close();
        System.out.println("PASS: deterministic client movement timing diagnostics");
    }
}
"""


def read_jsonl(path: Path) -> list[dict]:
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


def main() -> None:
    ensure_client_jar()
    packet_text = PACKET_HANDLER.read_text(encoding="utf-8")
    mudclient_text = MUDCLIENT.read_text(encoding="utf-8")
    applet_text = APPLET.read_text(encoding="utf-8")
    source_text = SOURCE.read_text(encoding="utf-8")
    launcher_text = LAUNCHER.read_text(encoding="utf-8")
    for haystack, snippet, label in (
        (packet_text, "MovementTimingDiagnostics.recordMovementPacket", "movement packet arrival hook"),
        (packet_text, "MovementTimingDiagnostics.recordMovementSnapshot", "snapshot arrival hook"),
        (mudclient_text, "MovementTimingDiagnostics.observeLocalPlayer", "local idle/depth hook"),
        (mudclient_text, "MovementTimingDiagnostics.recordCorrectionSnap", "correction hook"),
        (mudclient_text, "MovementTimingDiagnostics.recordEndpointSnap", "endpoint snap hook"),
        (mudclient_text, '"Movement stutter marker recorded."', "visible marker acknowledgement"),
        (applet_text, "mudclient.markMovementStutterObserved()", "Ctrl+F8 marker hotkey"),
    ):
        if snippet not in haystack:
            fail(f"missing {label}: {snippet}")
    if "if (!ENABLED)" not in source_text:
        fail("disabled diagnostics gate missing")
    if "ACCUMULATOR = ENABLED" not in source_text:
        fail("disabled diagnostics still allocate accumulator storage")
    if "--no-frame-capture" not in launcher_text or 'SPOILED_MILK_OPENGL_FRAME_CAPTURE="$FRAME_CAPTURE_ENABLED"' not in launcher_text:
        fail("renderer diagnostics cannot explicitly disable frame capture")
    diagnostic_session = (CLIENT / "src/orsc/RendererDiagnosticSession.java").read_text(
        encoding="utf-8"
    )
    if "EVENT_RESERVED_BYTES" not in diagnostic_session:
        fail("movement events have no reserved structured-log budget")

    with tempfile.TemporaryDirectory(prefix="client-movement-timing-") as raw_tmp:
        tmp = Path(raw_tmp)
        source_dir = tmp / "source/orsc"
        classes_dir = tmp / "classes"
        source_dir.mkdir(parents=True)
        classes_dir.mkdir()
        fixture = source_dir / "MovementTimingDiagnosticsFixture.java"
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
            fail("fixture compile failed:\n" + compile_result.stdout + compile_result.stderr)

        disabled_dir = tmp / "disabled"
        disabled = run(
            [
                "java",
                f"-Dspoiledmilk.rendererDiagnosticSessionDir={disabled_dir}",
                "-cp",
                f"{classes_dir}:{CLIENT_JAR}",
                "orsc.MovementTimingDiagnosticsFixture",
            ]
        )
        if disabled.returncode != 0:
            fail("disabled fixture failed:\n" + disabled.stdout + disabled.stderr)
        if disabled_dir.exists():
            fail("disabled movement diagnostics created output")

        enabled_dir = tmp / "enabled"
        enabled = run(
            [
                "java",
                "-Dspoiledmilk.rendererDiagnostics=true",
                f"-Dspoiledmilk.rendererDiagnosticSessionDir={enabled_dir}",
                "-Dspoiledmilk.testPassword=must-not-appear",
                "-cp",
                f"{classes_dir}:{CLIENT_JAR}",
                "orsc.MovementTimingDiagnosticsFixture",
            ]
        )
        if enabled.returncode != 0:
            fail("enabled fixture failed:\n" + enabled.stdout + enabled.stderr)
        events = read_jsonl(enabled_dir / "events.jsonl")
        marker = next(
            (event for event in events if event.get("eventType") == "movement.stutter-observed"),
            None,
        )
        if marker is None:
            fail(f"manual marker event missing: {events}")
        if not isinstance(marker.get("movement.recentPackets"), list):
            fail("manual marker recent packet ring missing")
        serialized = json.dumps(events)
        for forbidden in ("must-not-appear", "password", "username", "address", "chat"):
            if forbidden.lower() in serialized.lower():
                fail(f"privacy-sensitive value leaked into movement events: {forbidden}")

    print("PASS: client movement timing diagnostics and privacy guardrails")


if __name__ == "__main__":
    main()
