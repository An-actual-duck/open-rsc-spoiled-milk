#!/usr/bin/env python3
"""Validate and summarize a renderer diagnostic session for AI-assisted review."""

import argparse
import json
import math
import subprocess
import sys
from collections import Counter
from pathlib import Path
from typing import Any


SCHEMA_NAME = "renderer-diagnostics"
SUPPORTED_SCHEMA_VERSION = 1
CAPTURE_ANALYZER = Path(__file__).with_name("analyze-renderer-v2-capture.py")


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("session_dir", type=Path)
    parser.add_argument(
        "--analyze-captures",
        action="store_true",
        help="run the existing frame analyzer for every completed indexed capture",
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="fail for missing indexed artifacts or failed capture frames",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="include record paths and detailed event counts",
    )
    parser.add_argument(
        "--no-write",
        action="store_true",
        help="print the summary without writing ai-summary.md",
    )
    return parser.parse_args()


def read_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing required file: {path}")
    except json.JSONDecodeError as error:
        fail(f"invalid JSON in {path}: {error}")
    if not isinstance(value, dict):
        fail(f"expected JSON object in {path}")
    return value


def read_jsonl(path: Path) -> tuple[list[dict[str, Any]], bool]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except FileNotFoundError:
        fail(f"missing required file: {path}")

    records: list[dict[str, Any]] = []
    partial_last_line = False
    nonempty_indices = [index for index, line in enumerate(lines) if line.strip()]
    last_nonempty = nonempty_indices[-1] if nonempty_indices else -1
    for index, line in enumerate(lines):
        if not line.strip():
            continue
        try:
            value = json.loads(line)
        except json.JSONDecodeError as error:
            if index == last_nonempty:
                partial_last_line = True
                break
            fail(f"invalid JSONL record {index + 1} in {path}: {error}")
        if not isinstance(value, dict):
            fail(f"expected object at record {index + 1} in {path}")
        records.append(value)
    return records, partial_last_line


def validate_schema(record: dict[str, Any], source: str) -> None:
    if record.get("schema") != SCHEMA_NAME:
        fail(f"{source} has unsupported schema {record.get('schema')!r}")
    version = record.get("schemaVersion")
    if version != SUPPORTED_SCHEMA_VERSION:
        fail(f"{source} has unsupported schema version {version!r}")


def numeric(record: dict[str, Any], key: str, default: float = 0.0) -> float:
    value = record.get(key, default)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return default
    return float(value)


def numeric_array(record: dict[str, Any], key: str) -> list[float]:
    value = record.get(key)
    if not isinstance(value, list):
        return []
    return [
        float(item)
        for item in value
        if not isinstance(item, bool) and isinstance(item, (int, float)) and item >= 0
    ]


def stage_samples(records: list[dict[str, Any]], stage: str) -> list[float]:
    key = f"stage.{stage}.window.samplesNanos"
    samples: list[float] = []
    for record in records:
        samples.extend(numeric_array(record, key))
    return samples


def percentile(values: list[float], percent: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(percent * len(ordered)) - 1))
    return ordered[index]


def milliseconds(nanos: float) -> str:
    return f"{nanos / 1_000_000.0:.3f}ms"


def select_old_generation_key(records: list[dict[str, Any]]) -> tuple[str | None, str]:
    suffix = ".collection.usedBytes"
    candidates: dict[str, list[float]] = {}
    names: dict[str, str] = {}
    for record in records:
        for key, value in record.items():
            if not key.startswith("runtime.memoryPool.") or not key.endswith(suffix):
                continue
            if isinstance(value, bool) or not isinstance(value, (int, float)) or value < 0:
                continue
            base = key[: -len(suffix)]
            candidates.setdefault(key, []).append(float(value))
            name = record.get(base + ".name")
            if isinstance(name, str):
                names[key] = name
    if not candidates:
        return None, "unavailable"

    def rank(key: str) -> tuple[int, int, float]:
        label = names.get(key, key).lower()
        preferred = int("old" in label or "tenured" in label)
        values = candidates[key]
        return preferred, len(values), max(values)

    selected = max(candidates, key=rank)
    return selected, names.get(selected, selected)


def direct_buffer_key(records: list[dict[str, Any]]) -> tuple[str | None, str]:
    suffix = ".memoryUsedBytes"
    candidates: dict[str, int] = {}
    names: dict[str, str] = {}
    for record in records:
        for key, value in record.items():
            if not key.startswith("runtime.bufferPool.") or not key.endswith(suffix):
                continue
            if isinstance(value, bool) or not isinstance(value, (int, float)) or value < 0:
                continue
            candidates[key] = candidates.get(key, 0) + 1
            base = key[: -len(suffix)]
            name = record.get(base + ".name")
            if isinstance(name, str):
                names[key] = name
    if not candidates:
        return None, "unavailable"
    direct = [key for key in candidates if names.get(key, key).lower() == "direct"]
    selected = max(direct or list(candidates), key=lambda key: candidates[key])
    return selected, names.get(selected, selected)


def metric_values(records: list[dict[str, Any]], key: str | None) -> list[float]:
    if key is None:
        return []
    values = []
    for record in records:
        value = record.get(key)
        if isinstance(value, bool) or not isinstance(value, (int, float)) or value < 0:
            continue
        values.append(float(value))
    return values


def integer_span(records: list[dict[str, Any]], key: str) -> str:
    values = [
        float(record[key])
        for record in records
        if key in record
        and not isinstance(record[key], bool)
        and isinstance(record[key], (int, float))
    ]
    if not values:
        return "unavailable"
    low = int(min(values))
    high = int(max(values))
    return str(low) if low == high else f"{low}..{high}"


def early_late_floor(values: list[float]) -> tuple[float, float]:
    quarter = max(1, len(values) // 4)
    return min(values[:quarter]), min(values[-quarter:])


def login_epochs(
    events: list[dict[str, Any]], session_end_nanos: float
) -> list[tuple[float, float]]:
    epochs: list[tuple[float, float]] = []
    start: float | None = None
    for event in sorted(events, key=lambda item: numeric(item, "sessionElapsedNanos")):
        event_type = event.get("eventType")
        elapsed = numeric(event, "sessionElapsedNanos")
        if event_type == "client.login":
            if start is not None:
                epochs.append((start, elapsed))
            start = elapsed
        elif event_type == "client.logout" and start is not None:
            epochs.append((start, elapsed))
            start = None
    if start is not None:
        epochs.append((start, session_end_nanos))
    return epochs


def performance_phases(
    events: list[dict[str, Any]],
) -> list[tuple[dict[str, Any], dict[str, Any]]]:
    active: dict[int, dict[str, Any]] = {}
    completed: list[tuple[dict[str, Any], dict[str, Any]]] = []
    for event in sorted(events, key=lambda item: numeric(item, "sessionElapsedNanos")):
        if event.get("eventType") != "renderer.performance-phase":
            continue
        phase_id = int(numeric(event, "phaseId", -1))
        action = event.get("action")
        if action == "start":
            active[phase_id] = event
        elif action == "stop" and phase_id in active:
            completed.append((active.pop(phase_id), event))
    return completed


def cpu_totals(records: list[dict[str, Any]]) -> dict[str, float]:
    result = {
        "wallNanos": 0.0,
        "processNanos": 0.0,
        "javaThreadNanos": 0.0,
        "unattributedNanos": 0.0,
        "allocatedBytes": 0.0,
    }
    for record in records:
        wall = numeric(record, "runtime.cpu.sampleWallNanos", -1)
        if wall <= 0:
            continue
        result["wallNanos"] += wall
        result["processNanos"] += max(
            0.0, numeric(record, "runtime.cpu.processTimeDeltaNanos")
        )
        result["javaThreadNanos"] += max(
            0.0, numeric(record, "runtime.cpu.javaThreadTimeDeltaNanos")
        )
        result["unattributedNanos"] += max(
            0.0, numeric(record, "runtime.cpu.unattributedTimeDeltaNanos")
        )
        result["allocatedBytes"] += max(
            0.0, numeric(record, "runtime.allocation.javaThreadBytesDelta")
        )
    return result


def named_thread_totals(
    records: list[dict[str, Any]], key_name: str
) -> tuple[float, float]:
    prefix = f"runtime.thread.named.{key_name}"
    cpu_nanos = 0.0
    allocated_bytes = 0.0
    for record in records:
        if not record.get(prefix + ".present"):
            continue
        cpu_nanos += max(0.0, numeric(record, prefix + ".cpuTimeDeltaNanos"))
        allocated_bytes += max(0.0, numeric(record, prefix + ".allocatedBytesDelta"))
    return cpu_nanos, allocated_bytes


def cores(nanos: float, wall_nanos: float) -> float:
    return nanos / wall_nanos if wall_nanos > 0 else 0.0


def mebibytes_per_second(byte_count: float, wall_nanos: float) -> float:
    if wall_nanos <= 0:
        return 0.0
    return byte_count * 1_000_000_000.0 / wall_nanos / (1024.0 * 1024.0)


def final_capture_records(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    final_by_key: dict[tuple[Any, Any], dict[str, Any]] = {}
    for record in records:
        status = record.get("status")
        if status not in {"completed", "failed"}:
            continue
        key = (record.get("burstId"), record.get("burstFrameIndex"))
        final_by_key[key] = record
    return list(final_by_key.values())


def capture_telemetry_ranges(
    telemetry: list[dict[str, Any]],
) -> list[tuple[float, float, dict[str, Any], dict[str, Any]]]:
    ranges: list[tuple[float, float, dict[str, Any], dict[str, Any]]] = []
    pending: dict[str, Any] | None = None
    for record in telemetry:
        trigger = record.get("trigger")
        if trigger == "capture-burst-before":
            pending = record
        elif trigger == "capture-burst-after" and pending is not None:
            start = numeric(pending, "sessionElapsedNanos")
            end = numeric(record, "sessionElapsedNanos")
            ranges.append((start, end, pending, record))
            pending = None
    return ranges


def within_capture_range(
    record: dict[str, Any],
    ranges: list[tuple[float, float, dict[str, Any], dict[str, Any]]],
) -> bool:
    elapsed = numeric(record, "sessionElapsedNanos")
    return any(start <= elapsed <= end for start, end, _, _ in ranges)


def capture_artifact_issues(
    session_dir: Path, capture_records: list[dict[str, Any]]
) -> list[str]:
    issues: list[str] = []
    for record in capture_records:
        relative = record.get("path")
        if not isinstance(relative, str) or not relative:
            issues.append(
                f"burst {record.get('burstId')} frame {record.get('burstFrameIndex')} has no path"
            )
            continue
        capture_dir = session_dir / relative
        if not capture_dir.is_dir():
            issues.append(f"missing capture directory {relative}")
            continue
        artifacts = record.get("artifacts", [])
        if not isinstance(artifacts, list):
            issues.append(f"capture {relative} has invalid artifact inventory")
            continue
        for artifact in artifacts:
            if isinstance(artifact, str) and not (capture_dir / artifact).is_file():
                issues.append(f"capture {relative} is missing indexed artifact {artifact}")
    return issues


def correlation_flags(record: dict[str, Any]) -> list[str]:
    flags: list[str] = []
    if numeric(record, "runtime.gc.collectionTimeMillisDelta") > 0:
        flags.append("GC activity")
    if numeric(record, "counter.openGLWorldChunkUpload.window.total") > 0:
        flags.append("chunk uploads")
    if numeric(record, "counter.openGLWorldChunkEvict.window.total") > 0:
        flags.append("chunk evictions")
    if numeric(record, "openGL.droppedFrames.window") > 0:
        flags.append("OpenGL frame drops")
    for stream in ("Sprite", "Text", "Primitive", "RotatedSprite", "Circle"):
        key = f"counter.renderer2D{stream}CommandDropped.window.total"
        if numeric(record, key) > 0:
            flags.append(f"{stream} command overflow")
    return flags


def analyze_completed_capture(capture_dir: Path) -> tuple[bool, str]:
    result = subprocess.run(
        [sys.executable, str(CAPTURE_ANALYZER), str(capture_dir), "--strict"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return False, result.stderr.strip() or result.stdout.strip()
    headline = next((line for line in result.stdout.splitlines() if line.strip()), "capture valid")
    return True, headline


def delegate_standalone_capture(path: Path) -> None:
    result = subprocess.run([sys.executable, str(CAPTURE_ANALYZER), str(path)])
    raise SystemExit(result.returncode)


def build_summary(
    session_dir: Path,
    manifest: dict[str, Any],
    telemetry: list[dict[str, Any]],
    events: list[dict[str, Any]],
    capture_index: list[dict[str, Any]],
    partial_files: list[str],
    artifact_issues: list[str],
    capture_analysis: list[tuple[str, bool, str]],
    verbose: bool,
) -> str:
    report_records = [
        record for record in telemetry if record.get("trigger") in {"periodic", "slow-frame"}
    ]
    capture_ranges = capture_telemetry_ranges(telemetry)
    normal_report_records = [
        record for record in report_records if not within_capture_range(record, capture_ranges)
    ]
    normal_telemetry = [
        record for record in telemetry if not within_capture_range(record, capture_ranges)
    ]
    timing_records = normal_report_records or report_records or telemetry
    distribution_triggers = {
        "periodic",
        "slow-frame",
        "performance-phase-start",
        "performance-phase-stop",
    }
    distribution_records = [
        record
        for record in normal_telemetry
        if record.get("trigger") in distribution_triggers
    ]
    client_loop_nanos = [
        numeric(record, "stage.clientLoop.window.averageNanos") for record in timing_records
    ]
    scene_nanos = [
        numeric(record, "stage.sceneRender.window.averageNanos") for record in timing_records
    ]
    open_gl_render_nanos = [
        numeric(record, "stage.openGLRender.window.averageNanos") for record in timing_records
    ]
    open_gl_world_nanos = [
        numeric(record, "stage.openGLWorld.window.averageNanos") for record in timing_records
    ]
    true_client_loop_nanos = stage_samples(distribution_records, "clientLoop")
    true_scene_nanos = stage_samples(distribution_records, "sceneRender")
    true_open_gl_render_nanos = stage_samples(distribution_records, "openGLRender")
    true_open_gl_world_nanos = stage_samples(distribution_records, "openGLWorld")
    runtime_totals = cpu_totals(normal_telemetry)
    completed_performance_phases = performance_phases(events)
    worst_records = sorted(
        timing_records,
        key=lambda record: numeric(record, "stage.openGLRender.window.averageNanos"),
        reverse=True,
    )[:5]
    event_counts = Counter(str(event.get("eventType", "unknown")) for event in events)
    completed_captures = final_capture_records(capture_index)
    failed_captures = [record for record in completed_captures if record.get("failed")]
    total_gc_count = sum(
        numeric(record, "runtime.gc.collectionCountDelta") for record in normal_report_records
    )
    total_gc_time = sum(
        numeric(record, "runtime.gc.collectionTimeMillisDelta") for record in normal_report_records
    )
    heap_values = [numeric(record, "runtime.heap.usedBytes") for record in timing_records]
    dropped_frames = max(
        (numeric(record, "openGL.droppedFrames.lifetime") for record in telemetry),
        default=0.0,
    )
    presented_frames = max(
        (numeric(record, "openGL.frames.lifetime") for record in telemetry),
        default=0.0,
    )
    capture_dropped_frames = sum(
        max(
            0.0,
            numeric(after, "openGL.droppedFrames.lifetime")
            - numeric(before, "openGL.droppedFrames.lifetime"),
        )
        for _, _, before, after in capture_ranges
    )
    capture_presented_frames = sum(
        max(
            0.0,
            numeric(after, "openGL.frames.lifetime")
            - numeric(before, "openGL.frames.lifetime"),
        )
        for _, _, before, after in capture_ranges
    )
    normal_dropped_frames = max(0.0, dropped_frames - capture_dropped_frames)
    normal_presented_frames = max(0.0, presented_frames - capture_presented_frames)
    dropped_percent = (
        normal_dropped_frames * 100.0 / (normal_presented_frames + normal_dropped_frames)
        if normal_presented_frames + normal_dropped_frames > 0
        else 0.0
    )
    elapsed_nanos = max(
        (numeric(record, "sessionElapsedNanos") for record in telemetry),
        default=0.0,
    )
    gc_time_percent = (
        total_gc_time * 1_000_000.0 * 100.0 / elapsed_nanos if elapsed_nanos > 0 else 0.0
    )
    gc_average_millis = total_gc_time / total_gc_count if total_gc_count > 0 else 0.0
    old_generation_key, old_generation_name = select_old_generation_key(normal_telemetry)
    old_generation_values = metric_values(normal_telemetry, old_generation_key)
    direct_key, direct_name = direct_buffer_key(normal_telemetry)
    direct_values = metric_values(normal_telemetry, direct_key)
    epochs = login_epochs(events, elapsed_nanos)
    movement_summaries = [
        event for event in events if event.get("eventType") == "movement.timing-summary"
    ]
    movement_markers = [
        event for event in events if event.get("eventType") == "movement.stutter-observed"
    ]
    movement_arrival_outliers = [
        event
        for event in events
        if event.get("eventType") == "movement.packet-arrival-outlier"
    ]
    movement_corrections = [
        event for event in events if event.get("eventType") == "movement.correction-snap"
    ]

    lines = [
        "# Renderer Diagnostic Session Summary",
        "",
        f"- Session: `{manifest.get('sessionId', session_dir.name)}`",
        f"- State: `{manifest.get('state', 'unknown')}`",
        f"- Revision: `{manifest.get('branch', 'unknown')}` / `{manifest.get('commit', 'unknown')}`",
        f"- Target mode: `{manifest.get('targetMode', 'unknown')}`",
        f"- Telemetry records: {len(telemetry)} ({len(report_records)} report windows)",
        f"- Event records: {len(events)}",
        f"- Indexed capture frames: {len(completed_captures)} ({len(failed_captures)} failed)",
        f"- Sampled duration: {elapsed_nanos / 1_000_000_000.0:.1f}s",
        "",
        "## Performance",
        "",
        f"- Client-loop window p50/p95/p99: {milliseconds(percentile(client_loop_nanos, 0.50))} / "
        f"{milliseconds(percentile(client_loop_nanos, 0.95))} / {milliseconds(percentile(client_loop_nanos, 0.99))}",
        f"- Scene window p50/p95/p99: {milliseconds(percentile(scene_nanos, 0.50))} / "
        f"{milliseconds(percentile(scene_nanos, 0.95))} / {milliseconds(percentile(scene_nanos, 0.99))}",
        f"- OpenGL render window p50/p95/p99: {milliseconds(percentile(open_gl_render_nanos, 0.50))} / "
        f"{milliseconds(percentile(open_gl_render_nanos, 0.95))} / {milliseconds(percentile(open_gl_render_nanos, 0.99))}",
        f"- OpenGL world window p50/p95/p99: {milliseconds(percentile(open_gl_world_nanos, 0.50))} / "
        f"{milliseconds(percentile(open_gl_world_nanos, 0.95))} / {milliseconds(percentile(open_gl_world_nanos, 0.99))}",
        f"- OpenGL normal presented/dropped frames: {int(normal_presented_frames)} / "
        f"{int(normal_dropped_frames)} ({dropped_percent:.2f}% dropped, excluding indexed capture bursts)",
        f"- GC delta across report windows: {int(total_gc_count)} collections / {int(total_gc_time)}ms "
        f"({gc_average_millis:.2f}ms average, {gc_time_percent:.2f}% of sampled duration)",
    ]
    true_distributions = (
        ("Client loop", true_client_loop_nanos),
        ("Scene", true_scene_nanos),
        ("OpenGL render", true_open_gl_render_nanos),
        ("OpenGL world", true_open_gl_world_nanos),
    )
    if any(values for _, values in true_distributions):
        lines.extend(["", "### True per-frame distributions", ""])
        for label, values in true_distributions:
            if not values:
                lines.append(f"- {label}: raw frame samples unavailable.")
                continue
            lines.append(
                f"- {label} p50/p95/p99/max: "
                f"{milliseconds(percentile(values, 0.50))} / "
                f"{milliseconds(percentile(values, 0.95))} / "
                f"{milliseconds(percentile(values, 0.99))} / "
                f"{milliseconds(max(values))} ({len(values)} samples)."
            )
    if heap_values:
        lines.append(
            f"- Heap used range: {int(min(heap_values))}..{int(max(heap_values))} bytes"
        )
        quarter = max(1, len(heap_values) // 4)
        early_floor = min(heap_values[:quarter])
        late_floor = min(heap_values[-quarter:])
        lines.append(
            f"- Early/late sampled heap floor: {int(early_floor)} / {int(late_floor)} bytes "
            f"(delta {int(late_floor - early_floor):+d}); this is a retention signal, not leak proof."
        )

    lines.extend(["", "## CPU And Allocation", ""])
    runtime_wall = runtime_totals["wallNanos"]
    if runtime_wall > 0:
        lines.append(
            "- Process/Java-thread/unattributed CPU: "
            f"{cores(runtime_totals['processNanos'], runtime_wall):.3f} / "
            f"{cores(runtime_totals['javaThreadNanos'], runtime_wall):.3f} / "
            f"{cores(runtime_totals['unattributedNanos'], runtime_wall):.3f} "
            f"average cores across {runtime_wall / 1_000_000_000.0:.1f}s."
        )
        lines.append(
            "- Java-thread allocation rate: "
            f"{mebibytes_per_second(runtime_totals['allocatedBytes'], runtime_wall):.2f} MiB/s."
        )
        named_threads = (
            ("client loop", "clientLoop"),
            ("OpenGL presenter", "openGLPresenter"),
            ("world preload", "worldPreload"),
            ("AWT event queue", "awtEventQueue"),
        )
        thread_parts = []
        for label, key_name in named_threads:
            thread_cpu, thread_allocated = named_thread_totals(normal_telemetry, key_name)
            if thread_cpu <= 0 and thread_allocated <= 0:
                continue
            thread_parts.append(
                f"{label}={cores(thread_cpu, runtime_wall):.3f} cores/"
                f"{mebibytes_per_second(thread_allocated, runtime_wall):.2f} MiB/s"
            )
        if thread_parts:
            lines.append("- Named CPU/allocation ownership: " + ", ".join(thread_parts) + ".")
    else:
        lines.append("- Process/thread CPU and allocation telemetry is unavailable in this session.")

    latest_thread_record = next(
        (
            record
            for record in reversed(normal_telemetry)
            if numeric(record, "runtime.thread.topCpu.count") > 0
        ),
        None,
    )
    if latest_thread_record is not None:
        top_parts = []
        top_count = int(numeric(latest_thread_record, "runtime.thread.topCpu.count"))
        wall = numeric(latest_thread_record, "runtime.cpu.sampleWallNanos")
        for index in range(min(5, top_count)):
            prefix = f"runtime.thread.topCpu.{index}"
            name = latest_thread_record.get(prefix + ".name")
            if not isinstance(name, str):
                continue
            top_parts.append(
                f"{name}={cores(numeric(latest_thread_record, prefix + '.cpuTimeDeltaNanos'), wall):.3f}"
            )
        if top_parts:
            lines.append("- Latest top Java threads by average cores: " + ", ".join(top_parts) + ".")
    jfr_relative = manifest.get("files.jfr")
    if isinstance(jfr_relative, str) and jfr_relative:
        jfr_path = session_dir / jfr_relative
        if jfr_path.is_file():
            lines.append(
                f"- JFR profile: `{jfr_relative}` ({jfr_path.stat().st_size} bytes); "
                "use `jfr summary` and `jfr print` for stack-level attribution."
            )
        else:
            lines.append(f"- JFR profile is indexed but not present: `{jfr_relative}`.")

    lines.extend(["", "## Named Performance Phases", ""])
    if not completed_performance_phases:
        lines.append("- No completed `::pf` performance phases were recorded.")
    for start_event, stop_event in completed_performance_phases:
        start_nanos = numeric(start_event, "sessionElapsedNanos")
        stop_nanos = numeric(stop_event, "sessionElapsedNanos")
        phase_records = [
            record
            for record in normal_telemetry
            if start_nanos <= numeric(record, "sessionElapsedNanos") <= stop_nanos
        ]
        phase_distribution_records = [
            record
            for record in phase_records
            if record.get("trigger") in {"periodic", "slow-frame", "performance-phase-stop"}
        ]
        phase_render = stage_samples(phase_distribution_records, "openGLRender")
        phase_world = stage_samples(phase_distribution_records, "openGLWorld")
        phase_runtime = cpu_totals(phase_records)
        phase_wall = phase_runtime["wallNanos"]
        label = str(start_event.get("label", "unnamed"))
        duration = max(0.0, stop_nanos - start_nanos)
        if phase_render:
            frame_text = (
                f"GL render p95/p99 {milliseconds(percentile(phase_render, 0.95))}/"
                f"{milliseconds(percentile(phase_render, 0.99))}, "
                f"world p95/p99 {milliseconds(percentile(phase_world, 0.95))}/"
                f"{milliseconds(percentile(phase_world, 0.99))}, "
                f"{len(phase_render)} frames"
            )
        else:
            frame_text = "raw frame samples unavailable"
        cpu_text = (
            f"{cores(phase_runtime['processNanos'], phase_wall):.3f} CPU cores, "
            f"{mebibytes_per_second(phase_runtime['allocatedBytes'], phase_wall):.2f} MiB/s allocated"
            if phase_wall > 0
            else "CPU/allocation unavailable"
        )
        lines.append(
            f"- `{label}` (#{int(numeric(start_event, 'phaseId'))}): "
            f"{duration / 1_000_000_000.0:.1f}s; {frame_text}; {cpu_text}."
        )
        camera_records = [
            record for record in phase_records if record.get("camera.stateKnown") is True
        ]
        if camera_records:
            fog_modes = sorted(
                {
                    str(record.get("camera.fogMode"))
                    for record in camera_records
                    if isinstance(record.get("camera.fogMode"), str)
                }
            )
            first_person_modes = sorted(
                {
                    "on" if record.get("camera.firstPerson") is True else "off"
                    for record in camera_records
                }
            )
            lines.append(
                "  Camera: zoom setting "
                f"{integer_span(camera_records, 'camera.zoomSetting')} "
                f"(allowed {integer_span(camera_records, 'camera.zoomSettingMin')}"
                f"..{integer_span(camera_records, 'camera.zoomSettingMax')}), "
                f"base/effective {integer_span(camera_records, 'camera.baseZoom')}/"
                f"{integer_span(camera_records, 'camera.effectiveZoom')}, "
                f"pitch {integer_span(camera_records, 'camera.pitch')}, "
                f"rotation {integer_span(camera_records, 'camera.rotation')}, "
                f"fog {','.join(fog_modes) or 'unknown'}, "
                f"draw {integer_span(camera_records, 'camera.drawDistanceTiles')} tiles/"
                f"{integer_span(camera_records, 'camera.drawDistanceWorldUnits')} world units, "
                f"fog start {integer_span(camera_records, 'camera.fogStartDistanceTiles')} tiles/"
                f"{integer_span(camera_records, 'camera.fogStartDistanceWorldUnits')} world units, "
                f"first-person {','.join(first_person_modes)}."
            )

    lines.extend(["", "## Memory Retention", ""])
    if old_generation_values:
        early_old_floor, late_old_floor = early_late_floor(old_generation_values)
        lines.append(
            f"- Post-GC old-generation pool `{old_generation_name}` range: "
            f"{int(min(old_generation_values))}..{int(max(old_generation_values))} bytes; "
            f"early/late floor {int(early_old_floor)} / {int(late_old_floor)} "
            f"(delta {int(late_old_floor - early_old_floor):+d})."
        )
    else:
        lines.append("- Post-GC old-generation occupancy is unavailable in this session.")
    if direct_values:
        early_direct_floor, late_direct_floor = early_late_floor(direct_values)
        lines.append(
            f"- Native buffer pool `{direct_name}` range: "
            f"{int(min(direct_values))}..{int(max(direct_values))} bytes; "
            f"early/late floor {int(early_direct_floor)} / {int(late_direct_floor)} "
            f"(delta {int(late_direct_floor - early_direct_floor):+d})."
        )
    else:
        lines.append("- Native/direct buffer-pool occupancy is unavailable in this session.")
    if not epochs:
        lines.append("- No client login epochs were recorded.")
    for index, (start, end) in enumerate(epochs, start=1):
        epoch_records = [
            record
            for record in normal_telemetry
            if start <= numeric(record, "sessionElapsedNanos") <= end
        ]
        epoch_old = metric_values(epoch_records, old_generation_key)
        epoch_direct = metric_values(epoch_records, direct_key)
        epoch_gc = sum(numeric(record, "runtime.gc.collectionCountDelta") for record in epoch_records)
        section_loads = sum(
            1
            for event in events
            if event.get("eventType") == "renderer.world-section-load"
            and start <= numeric(event, "sessionElapsedNanos") <= end
        )
        old_text = (
            f"old-gen post-GC {int(epoch_old[0])}->{int(epoch_old[-1])} bytes"
            if epoch_old
            else "old-gen post-GC unavailable"
        )
        direct_text = (
            f"direct range {int(min(epoch_direct))}..{int(max(epoch_direct))} bytes, "
            f"first/last {int(epoch_direct[0])}->{int(epoch_direct[-1])}"
            if epoch_direct
            else "direct unavailable"
        )
        lines.append(
            f"- Login epoch {index}: {(end - start) / 1_000_000_000.0:.1f}s, "
            f"{len(epoch_records)} telemetry records, {int(epoch_gc)} GC collections, "
            f"{section_loads} section loads; {old_text}; {direct_text}."
        )

    lines.extend(["", "### Worst sampled OpenGL render windows", ""])
    if not worst_records:
        lines.append("- No telemetry samples.")
    for record in worst_records:
        flags = correlation_flags(record)
        correlation = ", ".join(flags) if flags else "no recorded pressure signal"
        lines.append(
            f"- Frame {int(numeric(record, 'rendererFrameSequence'))}: "
            f"render {milliseconds(numeric(record, 'stage.openGLRender.window.averageNanos'))}, "
            f"world {milliseconds(numeric(record, 'stage.openGLWorld.window.averageNanos'))}, "
            f"client loop {milliseconds(numeric(record, 'stage.clientLoop.window.averageNanos'))}; "
            f"correlated with {correlation}."
        )

    lines.extend(["", "## Movement Timing", ""])
    lines.append(
        f"- Timing summaries/markers/arrival outliers/correction records: "
        f"{len(movement_summaries)} / {len(movement_markers)} / "
        f"{len(movement_arrival_outliers)} / {len(movement_corrections)}."
    )
    if movement_summaries:
        latest_movement = movement_summaries[-1]
        lines.append(
            "- Latest packet window: "
            f"movement={int(numeric(latest_movement, 'movement.movementPackets'))}, "
            f"snapshot={int(numeric(latest_movement, 'movement.snapshotPackets'))}, "
            f"records={int(numeric(latest_movement, 'movement.packetRecords'))}, "
            f"server tick/sequence="
            f"{int(numeric(latest_movement, 'movement.latestServerTick', -1))}/"
            f"{int(numeric(latest_movement, 'movement.latestSequence', -1))}."
        )
        lines.append(
            "- Snapshot arrival upper-bound p50/p95/p99/max: "
            f"{milliseconds(numeric(latest_movement, 'movement.snapshotArrival.p50UpperBound'))} / "
            f"{milliseconds(numeric(latest_movement, 'movement.snapshotArrival.p95UpperBound'))} / "
            f"{milliseconds(numeric(latest_movement, 'movement.snapshotArrival.p99UpperBound'))} / "
            f"{milliseconds(numeric(latest_movement, 'movement.snapshotArrival.max'))}."
        )
        lines.append(
            "- Waypoint/idle window: "
            f"current depth={int(numeric(latest_movement, 'movement.currentWaypointDepth'))}, "
            f"max depth={int(numeric(latest_movement, 'movement.maxWaypointDepth'))}, "
            f"idle transitions={int(numeric(latest_movement, 'movement.idleTransitions'))}, "
            f"idle p95 upper bound="
            f"{milliseconds(numeric(latest_movement, 'movement.idleDuration.p95UpperBound'))}, "
            f"endpoint snaps={int(numeric(latest_movement, 'movement.endpointSnaps'))}, "
            f"correction snaps={int(numeric(latest_movement, 'movement.correctionSnaps'))}."
        )
    else:
        lines.append("- No movement timing summaries were recorded.")
    for index, marker in enumerate(movement_markers, start=1):
        lines.append(
            f"- Marker {index} at {numeric(marker, 'sessionElapsedNanos') / 1_000_000_000.0:.3f}s: "
            f"arrival={milliseconds(numeric(marker, 'movement.latestArrivalIntervalNanos'))}, "
            f"depth={int(numeric(marker, 'movement.currentWaypointDepth'))}, "
            f"current idle={milliseconds(numeric(marker, 'movement.currentIdleNanos'))}, "
            f"nearby client loop={milliseconds(numeric(marker, 'frame.clientLoopLatestNanos'))} "
            f"(sample age {milliseconds(numeric(marker, 'frame.clientLoopSampleAgeNanos'))})."
        )

    lines.extend(["", "## Renderer Signals", ""])
    signal_types = (
        "renderer.slow-frame",
        "renderer.2d-command-overflow",
        "renderer.world-section-load",
        "renderer.chunk-upload-reason-change",
        "renderer.resident-chunk-reason-change",
        "renderer.shadow-mask-reason-change",
        "client.exception",
    )
    for event_type in signal_types:
        matching_events = [event for event in events if event.get("eventType") == event_type]
        transition_count = sum(
            1 + int(numeric(event, "suppressedTransitions")) for event in matching_events
        )
        if any("suppressedTransitions" in event for event in matching_events):
            lines.append(
                f"- `{event_type}`: {len(matching_events)} records / "
                f"{transition_count} aggregated transitions"
            )
        else:
            lines.append(f"- `{event_type}`: {event_counts.get(event_type, 0)}")

    lines.extend(["", "### Transition timing distributions", ""])
    transition_types = (
        ("world model", "renderer.world-model-transition"),
        ("world section", "renderer.world-section-transition"),
        ("client region", "renderer.client-region-transition"),
    )
    transition_found = False
    for label, event_type in transition_types:
        values = [
            numeric(event, "totalNanos")
            for event in events
            if event.get("eventType") == event_type and numeric(event, "totalNanos", -1) >= 0
        ]
        if not values:
            continue
        transition_found = True
        lines.append(
            f"- {label}: p50/p95/max "
            f"{milliseconds(percentile(values, 0.50))} / "
            f"{milliseconds(percentile(values, 0.95))} / "
            f"{milliseconds(max(values))} ({len(values)} transitions)."
        )
    if not transition_found:
        lines.append("- No structured transition timing events were recorded.")

    latest = telemetry[-1] if telemetry else {}
    lines.extend(["", "## Resident Material Families", ""])
    material_fields = {
        "unclassified": "Unclassified",
        "terrain": "Terrain",
        "water": "Water",
        "wall": "Wall",
        "roof": "Roof",
        "scenery": "Scenery",
        "foliage": "Foliage",
        "ore": "Ore",
        "emissive": "Emissive",
        "effect": "Effect",
    }
    material_values = {
        label: numeric(latest, f"counter.openGLWorldMaterial{name}.recent.latest")
        for label, name in material_fields.items()
    }
    material_keys_present = any(
        f"counter.openGLWorldMaterial{name}.recent.latest" in latest
        for name in material_fields.values()
    )
    if material_keys_present:
        material_total = sum(material_values.values())
        unclassified = material_values["unclassified"]
        unclassified_percent = (
            unclassified * 100.0 / material_total if material_total > 0 else 0.0
        )
        lines.append(
            f"- Latest resident triangle total: {int(material_total)}; "
            f"unclassified {int(unclassified)} ({unclassified_percent:.2f}%)."
        )
        lines.append(
            "- Family counts: "
            + ", ".join(
                f"{label}={int(material_values[label])}" for label in material_fields
            )
            + "."
        )
    else:
        lines.append("- Material-family telemetry is unavailable in this session.")

    lines.extend(["", "## Renderer 2D Capacity", ""])
    stream_fields = {
        "sprite": ("Sprite", "spriteCommandLimit"),
        "text": ("Text", "textCommandLimit"),
        "primitive": ("Primitive", "primitiveCommandLimit"),
        "rotated-sprite": ("RotatedSprite", "rotatedSpriteCommandLimit"),
        "circle": ("Circle", "circleCommandLimit"),
    }
    for label, (metric_name, limit_name) in stream_fields.items():
        accepted = numeric(
            latest, f"counter.renderer2D{metric_name}CommandAccepted.recent.latest"
        )
        maximum = numeric(
            latest, f"counter.renderer2D{metric_name}CommandAccepted.lifetime.max"
        )
        dropped = numeric(
            latest, f"counter.renderer2D{metric_name}CommandDropped.lifetime.total"
        )
        latest_dropped = numeric(
            latest, f"counter.renderer2D{metric_name}CommandDropped.recent.latest"
        )
        drop_windows = sum(
            1
            for record in report_records
            if numeric(record, f"counter.renderer2D{metric_name}CommandDropped.window.total") > 0
        )
        limit = numeric(latest, f"config.renderer2D.{limit_name}")
        lines.append(
            f"- {label}: latest accepted {accepted:g}, lifetime max {maximum:g}, "
            f"latest dropped {latest_dropped:g}, total dropped {dropped:g} across "
            f"{drop_windows} report windows, limit {limit:g}"
        )

    lines.extend(["", "## Captures", ""])
    if not completed_captures:
        lines.append("- No completed capture frames were indexed.")
    for record in completed_captures:
        lines.append(
            f"- Burst {record.get('burstId')} frame {record.get('burstFrameIndex')}: "
            f"`{record.get('status')}` at `{record.get('path', 'missing')}`; "
            f"capture work {milliseconds(numeric(record, 'captureWorkNanos'))}, "
            f"frame span {milliseconds(numeric(record, 'captureSpanNanos'))}."
        )
    for relative, valid, detail in capture_analysis:
        lines.append(f"- Analyzer `{relative}`: {'PASS' if valid else 'FAIL'} — {detail}")
    if capture_ranges:
        capture_duration_nanos = sum(max(0.0, end - start) for start, end, _, _ in capture_ranges)
        capture_work_nanos = sum(numeric(record, "captureWorkNanos") for record in completed_captures)
        average_capture_work = (
            capture_work_nanos / len(completed_captures) if completed_captures else 0.0
        )
        lines.extend(
            [
                "",
                "### Capture impact",
                "",
                f"- Burst duration: {capture_duration_nanos / 1_000_000_000.0:.3f}s",
                f"- Capture work total/average: {milliseconds(capture_work_nanos)} / "
                f"{milliseconds(average_capture_work)} per frame",
                f"- Presented/dropped during indexed bursts: {int(capture_presented_frames)} / "
                f"{int(capture_dropped_frames)}",
                "- Capture impact is reported separately and excluded from normal performance rankings.",
            ]
        )

    lines.extend(["", "## Data Quality", ""])
    if manifest.get("bulkLogTruncated"):
        lines.append(
            "- WARNING: the structured bulk-telemetry budget was exhausted; "
            "later report windows may be missing."
        )
    if manifest.get("eventLogTruncated"):
        lines.append(
            "- WARNING: the structured event-log budget was exhausted; later events may be missing."
        )
    latest_telemetry_elapsed = max(
        (numeric(record, "sessionElapsedNanos") for record in telemetry),
        default=0.0,
    )
    latest_event_elapsed = max(
        (numeric(record, "sessionElapsedNanos") for record in events),
        default=0.0,
    )
    telemetry_event_gap = latest_event_elapsed - latest_telemetry_elapsed
    if latest_telemetry_elapsed > 0.0 and telemetry_event_gap > 15_000_000_000.0:
        lines.append(
            "- WARNING: periodic telemetry ends "
            f"{telemetry_event_gap / 1_000_000_000.0:.1f}s before the latest event; "
            "later named phases may lack frame, CPU, and allocation samples."
        )
    if partial_files:
        lines.append(
            "- Ignored an incomplete final JSONL record in: " + ", ".join(partial_files)
        )
    else:
        lines.append("- All JSONL records were complete.")
    if artifact_issues:
        lines.extend(f"- {issue}" for issue in artifact_issues)
    else:
        lines.append("- Indexed capture artifacts are present.")
    lines.append(
        "- Correlations above identify simultaneous signals; they do not by themselves prove causation."
    )

    if verbose:
        lines.extend(
            [
                "",
                "## Record Inventory",
                "",
                "- `manifest.json`",
                "- `telemetry.jsonl`",
                "- `events.jsonl`",
                "- `captures/capture-index.jsonl`",
                "",
                "### All event counts",
                "",
            ]
        )
        lines.extend(f"- `{name}`: {count}" for name, count in sorted(event_counts.items()))

    return "\n".join(lines) + "\n"


def main() -> None:
    args = parse_args()
    session_dir = args.session_dir.resolve()
    manifest_path = session_dir / "manifest.json"
    if not manifest_path.is_file() and (session_dir / "metadata.txt").is_file():
        delegate_standalone_capture(session_dir)
    manifest = read_json(manifest_path)
    validate_schema(manifest, "manifest")

    telemetry, telemetry_partial = read_jsonl(session_dir / "telemetry.jsonl")
    events, events_partial = read_jsonl(session_dir / "events.jsonl")
    capture_index, capture_partial = read_jsonl(
        session_dir / "captures" / "capture-index.jsonl"
    )
    for index, record in enumerate(telemetry):
        validate_schema(record, f"telemetry record {index + 1}")
    for index, record in enumerate(events):
        validate_schema(record, f"event record {index + 1}")
    for index, record in enumerate(capture_index):
        validate_schema(record, f"capture record {index + 1}")

    completed_captures = final_capture_records(capture_index)
    artifact_issues = capture_artifact_issues(session_dir, completed_captures)
    failed_captures = [record for record in completed_captures if record.get("failed")]
    if args.strict and (artifact_issues or failed_captures):
        problems = artifact_issues + [
            f"capture {record.get('path', 'unknown')} reported failure"
            for record in failed_captures
        ]
        fail("; ".join(problems))

    capture_analysis: list[tuple[str, bool, str]] = []
    if args.analyze_captures:
        for record in completed_captures:
            relative = record.get("path")
            if record.get("failed") or not isinstance(relative, str):
                continue
            valid, detail = analyze_completed_capture(session_dir / relative)
            capture_analysis.append((relative, valid, detail))
            if args.strict and not valid:
                fail(f"capture analyzer rejected {relative}: {detail}")

    partial_files = [
        name
        for name, partial in (
            ("telemetry.jsonl", telemetry_partial),
            ("events.jsonl", events_partial),
            ("capture-index.jsonl", capture_partial),
        )
        if partial
    ]
    summary = build_summary(
        session_dir,
        manifest,
        telemetry,
        events,
        capture_index,
        partial_files,
        artifact_issues,
        capture_analysis,
        args.verbose,
    )
    if not args.no_write:
        (session_dir / "ai-summary.md").write_text(summary, encoding="utf-8")
    print(summary, end="")


if __name__ == "__main__":
    main()
