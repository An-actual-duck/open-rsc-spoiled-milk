#!/usr/bin/env python3
import json
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ANALYZER = ROOT / "scripts/analyze-renderer-session.py"


def record(record_type: str, **values) -> dict:
    result = {
        "schema": "renderer-diagnostics",
        "schemaVersion": 1,
        "recordType": record_type,
        "sessionId": "session-fixture",
        "timestamp": "2026-07-10T12:00:00Z",
        "sessionElapsedNanos": 1,
    }
    result.update(values)
    return result


def write_jsonl(path: Path, records: list[dict], partial: str = "") -> None:
    text = "".join(json.dumps(item, separators=(",", ":")) + "\n" for item in records)
    path.write_text(text + partial, encoding="utf-8")


def make_session(session_dir: Path) -> None:
    captures = session_dir / "captures"
    capture_dir = captures / "capture-fixture"
    capture_dir.mkdir(parents=True, exist_ok=True)
    for name in ("metadata.txt", "summary.txt"):
        (capture_dir / name).write_text("fixture\n", encoding="utf-8")

    manifest = {
        "schema": "renderer-diagnostics",
        "schemaVersion": 1,
        "sessionId": "session-fixture",
        "state": "closed",
        "branch": "feat/renderer-v2-refinement",
        "commit": "abcdef123456",
        "targetMode": "live",
    }
    (session_dir / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    write_jsonl(
        session_dir / "telemetry.jsonl",
        [
            record(
                "telemetry",
                trigger="periodic",
                rendererFrameSequence=300,
                **{
                    "sessionElapsedNanos": 1_000_000_000,
                    "frame.lastNanos": 16_000_000,
                    "stage.clientLoop.window.averageNanos": 16_700_000,
                    "stage.sceneRender.window.averageNanos": 3_000_000,
                    "stage.openGLRender.window.averageNanos": 7_000_000,
                    "stage.openGLRender.window.samplesNanos": [6_000_000, 8_000_000],
                    "stage.openGLWorld.window.averageNanos": 5_000_000,
                    "stage.openGLWorld.window.samplesNanos": [4_000_000, 6_000_000],
                    "runtime.heap.usedBytes": 100_000_000,
                    "runtime.memoryPool.G1_Old_Gen.name": "G1 Old Gen",
                    "runtime.memoryPool.G1_Old_Gen.collection.usedBytes": 80_000_000,
                    "runtime.bufferPool.direct.name": "direct",
                    "runtime.bufferPool.direct.memoryUsedBytes": 4_000_000,
                    "runtime.gc.collectionCountDelta": 0,
                    "runtime.gc.collectionTimeMillisDelta": 0,
                    "runtime.cpu.sampleWallNanos": 1_000_000_000,
                    "runtime.cpu.processTimeDeltaNanos": 500_000_000,
                    "runtime.cpu.javaThreadTimeDeltaNanos": 450_000_000,
                    "runtime.cpu.unattributedTimeDeltaNanos": 50_000_000,
                    "runtime.allocation.javaThreadBytesDelta": 104_857_600,
                    "runtime.thread.named.clientLoop.present": True,
                    "runtime.thread.named.clientLoop.cpuTimeDeltaNanos": 100_000_000,
                    "runtime.thread.named.clientLoop.allocatedBytesDelta": 20_971_520,
                    "runtime.thread.named.openGLPresenter.present": True,
                    "runtime.thread.named.openGLPresenter.cpuTimeDeltaNanos": 300_000_000,
                    "runtime.thread.named.openGLPresenter.allocatedBytesDelta": 73_400_320,
                    "camera.stateKnown": True,
                    "camera.zoomSetting": 900,
                    "camera.zoomSettingMin": -100,
                    "camera.zoomSettingMax": 900,
                    "camera.baseZoom": 2400,
                    "camera.effectiveZoom": 2400,
                    "camera.pitch": 912,
                    "camera.rotation": 0,
                    "camera.firstPerson": False,
                    "camera.fogMode": "on",
                    "camera.drawDistanceWorldUnits": 9920,
                    "camera.fogStartDistanceWorldUnits": 8384,
                    "camera.drawDistanceTiles": 40,
                    "camera.fogStartDistanceTiles": 28,
                    "openGL.droppedFrames.lifetime": 0,
                    "openGL.frames.lifetime": 300,
                    "openGL.droppedFrames.window": 0,
                    "counter.openGLWorldChunkUpload.window.total": 0,
                    "counter.renderer2DRotatedSpriteCommandAccepted.recent.latest": 12,
                    "counter.renderer2DRotatedSpriteCommandAccepted.lifetime.max": 18,
                    "counter.renderer2DRotatedSpriteCommandDropped.lifetime.total": 0,
                    "config.renderer2D.rotatedSpriteCommandLimit": 256,
                },
            ),
            record(
                "telemetry",
                trigger="capture-burst-before",
                rendererFrameSequence=301,
                **{
                    "sessionElapsedNanos": 2_000_000_000,
                    "openGL.frames.lifetime": 300,
                    "openGL.droppedFrames.lifetime": 0,
                },
            ),
            record(
                "telemetry",
                trigger="periodic",
                rendererFrameSequence=450,
                **{
                    "sessionElapsedNanos": 3_000_000_000,
                    "stage.openGLRender.window.averageNanos": 30_000_000,
                    "openGL.frames.lifetime": 301,
                    "openGL.droppedFrames.lifetime": 100,
                    "runtime.gc.collectionCountDelta": 4,
                    "runtime.gc.collectionTimeMillisDelta": 20,
                },
            ),
            record(
                "telemetry",
                trigger="capture-burst-after",
                rendererFrameSequence=451,
                **{
                    "sessionElapsedNanos": 4_000_000_000,
                    "openGL.frames.lifetime": 301,
                    "openGL.droppedFrames.lifetime": 100,
                },
            ),
            record(
                "telemetry",
                trigger="slow-frame",
                rendererFrameSequence=600,
                **{
                    "sessionElapsedNanos": 5_000_000_000,
                    "frame.lastNanos": 48_000_000,
                    "stage.clientLoop.window.averageNanos": 16_800_000,
                    "stage.sceneRender.window.averageNanos": 3_500_000,
                    "stage.openGLRender.window.averageNanos": 10_000_000,
                    "stage.openGLRender.window.samplesNanos": [10_000_000, 20_000_000],
                    "stage.openGLWorld.window.averageNanos": 8_000_000,
                    "stage.openGLWorld.window.samplesNanos": [7_000_000, 12_000_000],
                    "runtime.heap.usedBytes": 140_000_000,
                    "runtime.memoryPool.G1_Old_Gen.name": "G1 Old Gen",
                    "runtime.memoryPool.G1_Old_Gen.collection.usedBytes": 92_000_000,
                    "runtime.bufferPool.direct.name": "direct",
                    "runtime.bufferPool.direct.memoryUsedBytes": 5_000_000,
                    "runtime.gc.collectionCountDelta": 1,
                    "runtime.gc.collectionTimeMillisDelta": 7,
                    "runtime.cpu.sampleWallNanos": 1_000_000_000,
                    "runtime.cpu.processTimeDeltaNanos": 600_000_000,
                    "runtime.cpu.javaThreadTimeDeltaNanos": 500_000_000,
                    "runtime.cpu.unattributedTimeDeltaNanos": 100_000_000,
                    "runtime.allocation.javaThreadBytesDelta": 125_829_120,
                    "runtime.thread.named.clientLoop.present": True,
                    "runtime.thread.named.clientLoop.cpuTimeDeltaNanos": 120_000_000,
                    "runtime.thread.named.clientLoop.allocatedBytesDelta": 31_457_280,
                    "runtime.thread.named.openGLPresenter.present": True,
                    "runtime.thread.named.openGLPresenter.cpuTimeDeltaNanos": 320_000_000,
                    "runtime.thread.named.openGLPresenter.allocatedBytesDelta": 83_886_080,
                    "camera.stateKnown": True,
                    "camera.zoomSetting": 900,
                    "camera.zoomSettingMin": -100,
                    "camera.zoomSettingMax": 900,
                    "camera.baseZoom": 2400,
                    "camera.effectiveZoom": 2400,
                    "camera.pitch": 912,
                    "camera.rotation": 512,
                    "camera.firstPerson": False,
                    "camera.fogMode": "on",
                    "camera.drawDistanceWorldUnits": 9920,
                    "camera.fogStartDistanceWorldUnits": 8384,
                    "camera.drawDistanceTiles": 40,
                    "camera.fogStartDistanceTiles": 28,
                    "openGL.droppedFrames.lifetime": 102,
                    "openGL.frames.lifetime": 600,
                    "openGL.droppedFrames.window": 2,
                    "counter.openGLWorldChunkUpload.window.total": 4,
                    "counter.openGLWorldMaterialUnclassified.recent.latest": 0,
                    "counter.openGLWorldMaterialTerrain.recent.latest": 800,
                    "counter.openGLWorldMaterialWater.recent.latest": 24,
                    "counter.openGLWorldMaterialWall.recent.latest": 160,
                    "counter.openGLWorldMaterialRoof.recent.latest": 96,
                    "counter.openGLWorldMaterialScenery.recent.latest": 75,
                    "counter.openGLWorldMaterialFoliage.recent.latest": 18,
                    "counter.openGLWorldMaterialOre.recent.latest": 7,
                    "counter.openGLWorldMaterialEmissive.recent.latest": 4,
                    "counter.openGLWorldMaterialEffect.recent.latest": 2,
                    "counter.renderer2DRotatedSpriteCommandAccepted.recent.latest": 256,
                    "counter.renderer2DRotatedSpriteCommandAccepted.lifetime.max": 256,
                    "counter.renderer2DRotatedSpriteCommandDropped.lifetime.total": 3,
                    "counter.renderer2DRotatedSpriteCommandDropped.window.total": 3,
                    "config.renderer2D.rotatedSpriteCommandLimit": 256,
                },
            ),
        ],
    )
    write_jsonl(
        session_dir / "events.jsonl",
        [
            record("event", eventType="session.start"),
            record(
                "event",
                eventType="renderer.performance-phase",
                action="start",
                label="dense",
                phaseId=1,
                sessionElapsedNanos=900_000_000,
            ),
            record("event", eventType="client.login", sessionElapsedNanos=500_000_000),
            record("event", eventType="renderer.slow-frame"),
            record(
                "event",
                eventType="movement.timing-summary",
                **{
                    "movement.movementPackets": 11,
                    "movement.snapshotPackets": 10,
                    "movement.packetRecords": 44,
                    "movement.latestServerTick": 123,
                    "movement.latestSequence": 456,
                    "movement.snapshotArrival.p50UpperBound": 430_000_000,
                    "movement.snapshotArrival.p95UpperBound": 500_000_000,
                    "movement.snapshotArrival.p99UpperBound": 650_000_000,
                    "movement.snapshotArrival.max": 520_000_000,
                    "movement.currentWaypointDepth": 0,
                    "movement.maxWaypointDepth": 2,
                    "movement.idleTransitions": 3,
                    "movement.idleDuration.p95UpperBound": 25_000_000,
                    "movement.endpointSnaps": 10,
                    "movement.correctionSnaps": 0,
                },
            ),
            record(
                "event",
                eventType="movement.stutter-observed",
                sessionElapsedNanos=4_500_000_000,
                **{
                    "movement.latestArrivalIntervalNanos": 520_000_000,
                    "movement.currentWaypointDepth": 0,
                    "movement.currentIdleNanos": 30_000_000,
                    "frame.clientLoopLatestNanos": 18_000_000,
                    "frame.clientLoopSampleAgeNanos": 1_000_000,
                },
            ),
            record("event", eventType="renderer.2d-command-overflow", stream="rotated-sprite"),
            record(
                "event",
                eventType="renderer.world-section-load",
                sessionElapsedNanos=2_500_000_000,
            ),
            record(
                "event",
                eventType="renderer.world-section-transition",
                sessionElapsedNanos=2_500_000_000,
                totalNanos=12_000_000,
            ),
            record(
                "event",
                eventType="boundary.transition-summary",
                completion="settled",
                traceId=1,
                centerX=2,
                centerY=13,
                sessionElapsedNanos=2_500_000_000,
                **{
                    "crossing.kind": "cardinal",
                    "visit.return": False,
                    "prediction.matched": True,
                    "scopeChanged": False,
                    "phase.names": ["world-section.total", "minimap.publish"],
                    "phase.totalNanos": [12_000_000, 2_000_000],
                    "frame.opengl.renderNanos": [8_000_000, 10_000_000],
                    "frame.opengl.intervalNanos": [16_000_000, 20_000_000],
                    "frame.opengl.offsetNanos": [-2_000_000, 10_000_000],
                    "frame.opengl.chunkUploadNanos": [2_000_000, 5_000_000],
                    "frame.opengl.uploadedBytes": [100, 200],
                    "frame.opengl.shadowBuildNanos": [1_000_000, 0],
                    "frame.opengl.shadowUploadNanos": [500_000, 0],
                    "frame.presentation.totalNanos": [3_000_000],
                    "frame.presentation.offsetNanos": [12_000_000],
                    "frame.client.loopNanos": [16_000_000, 18_000_000],
                    "frame.client.offsetNanos": [-1_000_000, 8_000_000],
                    "atomic.playerReceiptOffsetNanos": 2_000_000,
                    "atomic.staticBaselineOffsetNanos": 6_000_000,
                    "presentation.productsReadyOffsetNanos": 8_000_000,
                    "presentation.releaseOffsetNanos": 10_000_000,
                    "presentation.retainedAttempts": 1,
                    "opengl.presenterWait.totalNanos": 8_000_000,
                    "opengl.presenterWait.maxNanos": 4_000_000,
                    "opengl.queue.totalNanos": 1_000_000,
                    "opengl.queue.maxNanos": 1_000_000,
                    "runtime.gcCountDelta": 0,
                    "runtime.gcTimeMillisDelta": 0,
                    "lock.waitCount": 1,
                    "lock.waitNanos": 100_000,
                    "disk.reads": 0,
                    "disk.totalNanos": 0,
                },
            ),
            record(
                "event",
                eventType="boundary.transition-summary",
                completion="settled",
                traceId=2,
                centerX=3,
                centerY=13,
                sessionElapsedNanos=3_500_000_000,
                **{
                    "crossing.kind": "diagonal",
                    "visit.return": True,
                    "prediction.matched": False,
                    "scopeChanged": False,
                    "phase.names": ["world-section.total", "minimap.publish"],
                    "phase.totalNanos": [20_000_000, 4_000_000],
                    "frame.opengl.renderNanos": [9_000_000, 25_000_000],
                    "frame.opengl.intervalNanos": [17_000_000, 30_000_000],
                    "frame.opengl.offsetNanos": [-2_000_000, 10_000_000],
                    "frame.opengl.chunkUploadNanos": [1_000_000, 12_000_000],
                    "frame.opengl.uploadedBytes": [50, 4096],
                    "frame.opengl.shadowBuildNanos": [0, 3_000_000],
                    "frame.opengl.shadowUploadNanos": [0, 1_000_000],
                    "frame.presentation.totalNanos": [4_000_000],
                    "frame.presentation.offsetNanos": [12_000_000],
                    "frame.client.loopNanos": [17_000_000, 22_000_000],
                    "frame.client.offsetNanos": [-1_000_000, 8_000_000],
                    "atomic.playerReceiptOffsetNanos": 3_000_000,
                    "atomic.staticBaselineOffsetNanos": 7_000_000,
                    "presentation.productsReadyOffsetNanos": 9_000_000,
                    "presentation.releaseOffsetNanos": 11_000_000,
                    "presentation.retainedAttempts": 2,
                    "opengl.presenterWait.totalNanos": 9_000_000,
                    "opengl.presenterWait.maxNanos": 5_000_000,
                    "opengl.queue.totalNanos": 2_000_000,
                    "opengl.queue.maxNanos": 2_000_000,
                    "runtime.gcCountDelta": 1,
                    "runtime.gcTimeMillisDelta": 4,
                    "lock.waitCount": 2,
                    "lock.waitNanos": 200_000,
                    "disk.reads": 1,
                    "disk.totalNanos": 900_000,
                    "packet.sceneBaseline.serverTick": [120, 120, 121],
                    "packet.sceneBaseline.pageCategory": [3, 4, 5],
                    "packet.sceneBaseline.offsetNanos": [1_000_000, 2_000_000, 640_000_000],
                },
            ),
            record(
                "event",
                eventType="renderer.performance-phase",
                action="stop",
                label="dense",
                phaseId=1,
                durationNanos=4_200_000_000,
                sessionElapsedNanos=5_100_000_000,
            ),
            record("event", eventType="client.logout", sessionElapsedNanos=5_500_000_000),
            record("event", eventType="session.stop"),
        ],
    )
    write_jsonl(
        captures / "capture-index.jsonl",
        [
            record(
                "capture",
                status="completed",
                burstId=1,
                burstFrameIndex=0,
                captureSequence=1,
                rendererFrameSequence=600,
                path="captures/capture-fixture/",
                captureSpanNanos=20_000_000,
                inputCaptureNanos=2_000_000,
                layerCaptureNanos=8_000_000,
                finishCaptureNanos=2_000_000,
                captureWorkNanos=12_000_000,
                failed=False,
                artifacts=["metadata.txt", "summary.txt"],
            )
        ],
    )


def run_analyzer(session_dir: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["python3", str(ANALYZER), str(session_dir), *args],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="renderer-session-analyzer-") as tmp_name:
        session_dir = Path(tmp_name)
        make_session(session_dir)
        result = run_analyzer(session_dir, "--strict", "--verbose")
        if result.returncode != 0:
            raise AssertionError(result.stderr)
        for snippet in (
            "# Renderer Diagnostic Session Summary",
            "OpenGL render window p50/p95/p99: 7.000ms / 10.000ms / 10.000ms",
            "OpenGL render p50/p95/p99/max: 8.000ms / 20.000ms / 20.000ms / 20.000ms (4 samples).",
            "Process/Java-thread/unattributed CPU: 0.550 / 0.475 / 0.075 average cores across 2.0s.",
            "Java-thread allocation rate: 110.00 MiB/s.",
            "Named CPU/allocation ownership: client loop=0.110 cores/25.00 MiB/s, OpenGL presenter=0.310 cores/75.00 MiB/s.",
            "`dense` (#1): 4.2s; GL render p95/p99 20.000ms/20.000ms, world p95/p99 12.000ms/12.000ms, 4 frames; 0.550 CPU cores, 110.00 MiB/s allocated.",
            "Camera: zoom setting 900 (allowed -100..900), base/effective 2400/2400, pitch 912, rotation 0..512, fog on, draw 40 tiles/9920 world units, fog start 28 tiles/8384 world units, first-person off.",
            "GC delta across report windows: 1 collections / 7ms (7.00ms average",
            "Post-GC old-generation pool `G1 Old Gen` range: 80000000..92000000 bytes",
            "Native buffer pool `direct` range: 4000000..5000000 bytes; early/late floor 4000000 / 5000000 (delta +1000000).",
            "Login epoch 1: 5.0s, 2 telemetry records, 1 GC collections, 1 section loads; old-gen post-GC 80000000->92000000 bytes; direct range 4000000..5000000 bytes, first/last 4000000->5000000.",
            "OpenGL normal presented/dropped frames: 599 / 2 (0.33% dropped, excluding indexed capture bursts)",
            "Frame 600: render 10.000ms, world 8.000ms, client loop 16.800ms; correlated with GC activity, chunk uploads, OpenGL frame drops, RotatedSprite command overflow.",
            "world section: p50/p95/max 12.000ms / 12.000ms / 12.000ms (1 transitions).",
            "Timing summaries/markers/arrival outliers/correction records: 1 / 1 / 0 / 0.",
            "Latest packet window: movement=11, snapshot=10, records=44, server tick/sequence=123/456.",
            "Snapshot arrival upper-bound p50/p95/p99/max: 430.000ms / 500.000ms / 650.000ms / 520.000ms.",
            "Waypoint/idle window: current depth=0, max depth=2, idle transitions=3, idle p95 upper bound=25.000ms, endpoint snaps=10, correction snaps=0.",
            "Marker 1 at 4.500s: arrival=520.000ms, depth=0, current idle=30.000ms, nearby client loop=18.000ms (sample age 1.000ms).",
            "## Boundary Loading Diagnostics",
            "Bounded traces: 2 (settled=2); settled OpenGL/client-loop/presentation samples: 2 / 2 / 2. Pre-transition ring samples are excluded.",
            "OpenGL interval p50/p95/p99/max: 20.000ms / 30.000ms / 30.000ms / 30.000ms.",
            "`test=dense`: 2 traces; interval p95/p99/max 30.000ms / 30.000ms / 30.000ms",
            "`world-section.total` p50/p95/p99/max: 12.000ms / 20.000ms / 20.000ms / 20.000ms (2 traces).",
            "Trace 2 center 3,13 (diagonal, return): interval/render max 30.000ms / 25.000ms, upload 12.000ms / 4096 bytes",
            "Packets: scene baseline 3 packets over 2 server ticks (120..121), fence=1, presentation-scenery=1, presentation-walls=1, final packet 640.000ms.",
            "Latest resident triangle total: 1186; unclassified 0 (0.00%).",
            "Family counts: unclassified=0, terrain=800, water=24, wall=160, roof=96, scenery=75, foliage=18, ore=7, emissive=4, effect=2.",
            "rotated-sprite: latest accepted 256, lifetime max 256, latest dropped 0, total dropped 3 across 1 report windows, limit 256",
            "Burst 1 frame 0: `completed` at `captures/capture-fixture/`",
            "Presented/dropped during indexed bursts: 1 / 100",
            "Indexed capture artifacts are present.",
        ):
            if snippet not in result.stdout:
                raise AssertionError(f"missing {snippet!r} in:\n{result.stdout}")
        summary = (session_dir / "ai-summary.md").read_text(encoding="utf-8")
        if summary != result.stdout:
            raise AssertionError("written AI summary differs from stdout")

        make_session(session_dir)
        with (session_dir / "events.jsonl").open("a", encoding="utf-8") as writer:
            writer.write('{"schema":"renderer-diagnostics"')
        result = run_analyzer(session_dir, "--no-write")
        if result.returncode != 0:
            raise AssertionError("partial final JSONL record should be tolerated:\n" + result.stderr)
        if "Ignored an incomplete final JSONL record in: events.jsonl" not in result.stdout:
            raise AssertionError(result.stdout)

        make_session(session_dir)
        manifest = json.loads((session_dir / "manifest.json").read_text(encoding="utf-8"))
        manifest["schemaVersion"] = 99
        (session_dir / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        result = run_analyzer(session_dir)
        if result.returncode == 0 or "unsupported schema version 99" not in result.stderr:
            raise AssertionError(result.stderr)

        make_session(session_dir)
        manifest = json.loads((session_dir / "manifest.json").read_text(encoding="utf-8"))
        manifest["bulkLogTruncated"] = True
        manifest["eventLogTruncated"] = True
        (session_dir / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        result = run_analyzer(session_dir, "--no-write")
        if result.returncode != 0:
            raise AssertionError(result.stderr)
        if "structured bulk-telemetry budget was exhausted" not in result.stdout:
            raise AssertionError(result.stdout)
        if "structured event-log budget was exhausted" not in result.stdout:
            raise AssertionError(result.stdout)

        make_session(session_dir)
        lines = (session_dir / "telemetry.jsonl").read_text(encoding="utf-8").splitlines()
        (session_dir / "telemetry.jsonl").write_text(
            lines[0] + "\n{" + "\n" + lines[1] + "\n",
            encoding="utf-8",
        )
        result = run_analyzer(session_dir)
        if result.returncode == 0 or "invalid JSONL record 2" not in result.stderr:
            raise AssertionError(result.stderr)

        make_session(session_dir)
        (session_dir / "captures/capture-fixture/summary.txt").unlink()
        result = run_analyzer(session_dir, "--strict")
        if result.returncode == 0 or "missing indexed artifact summary.txt" not in result.stderr:
            raise AssertionError(result.stderr)

        make_session(session_dir)
        capture_index_path = session_dir / "captures/capture-index.jsonl"
        capture_record = json.loads(capture_index_path.read_text(encoding="utf-8"))
        capture_record["status"] = "failed"
        capture_record["failed"] = True
        capture_record["failure"] = "fixture failure"
        write_jsonl(capture_index_path, [capture_record])
        result = run_analyzer(session_dir, "--strict")
        if result.returncode == 0 or "reported failure" not in result.stderr:
            raise AssertionError(result.stderr)

        standalone_capture = session_dir / "standalone-capture"
        standalone_capture.mkdir()
        (standalone_capture / "metadata.txt").write_text("sequence=1\n", encoding="utf-8")
        result = run_analyzer(standalone_capture)
        if result.returncode == 0 or "capture missing required files" not in result.stderr:
            raise AssertionError(
                "standalone capture was not delegated to the frame analyzer:\n" + result.stderr
            )

    print("PASS: renderer diagnostic session analyzer validates and correlates AI-readable logs")


if __name__ == "__main__":
    main()
