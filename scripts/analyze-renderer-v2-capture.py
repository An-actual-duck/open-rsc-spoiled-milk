#!/usr/bin/env python3
"""Summarize a renderer-v2 Ctrl+F9 capture directory.

This is the first offline replay-harness slice: it validates that a capture has
the structured world/sprite data needed for replay work and reports likely
sprite occlusion pressure without requiring a live client.
"""

from __future__ import annotations

import argparse
import csv
import sys
from collections import Counter
from pathlib import Path
from typing import Iterable


REQUIRED_FILES = [
    "metadata.txt",
    "summary.txt",
    "00-legacy-source.png",
    "00-depth-kind.png",
    "00-entity-occluder-mask.png",
    "07-final.png",
    "world-faces.tsv",
    "sprite-commands.tsv",
    "sprite-anchors.tsv",
    "sprite-submissions.tsv",
    "character-sprites.tsv",
    "entity-restore-stats.tsv",
]

SPRITE_OCCLUDER_KINDS = {
    "TERRAIN",
    "WALL",
    "ROOF",
    "GAME_OBJECT",
    "WALL_OBJECT",
}

MATERIAL_FAMILY_SHADER_IDS = {
    "UNCLASSIFIED": 0,
    "TERRAIN": 1,
    "WATER": 2,
    "WALL": 3,
    "ROOF": 4,
    "SCENERY": 5,
    "FOLIAGE": 6,
    "ORE": 7,
    "EMISSIVE": 8,
    "EFFECT": 9,
}


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture_dir", type=Path, help="Ctrl+F9 renderer-v2 capture directory")
    parser.add_argument(
        "--top",
        type=int,
        default=12,
        help="number of top occlusion-pressure rows to print",
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="exit nonzero for empty world/sprite captures",
    )
    parser.add_argument(
        "--disagreement-threshold",
        type=float,
        default=95.0,
        help="occlusion percent at or above which missing/low captured depth stats are reported",
    )
    parser.add_argument(
        "--fail-on-suspicious-visibility",
        action="store_true",
        help="exit nonzero when projected entity sprites have neither visible restore output nor expected full occlusion",
    )
    return parser.parse_args()


def read_tsv(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        fail(f"missing {path}")
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle, delimiter="\t"))


def read_optional_tsv(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        return []
    return read_tsv(path)


def parse_int(value: str | None, default: int = 0) -> int:
    if value is None or value == "":
        return default
    try:
        return int(value)
    except ValueError:
        return default


def parse_bool(value: str | None) -> bool:
    return str(value).lower() == "true"


def parse_int_list(value: str | None) -> list[int]:
    if value is None or value == "":
        return []
    result: list[int] = []
    for part in value.split(","):
        try:
            result.append(int(part))
        except ValueError:
            continue
    return result


def bbox_from_points(xs: Iterable[int], ys: Iterable[int]) -> tuple[int, int, int, int] | None:
    xs = list(xs)
    ys = list(ys)
    if not xs or not ys or len(xs) != len(ys):
        return None
    return min(xs), min(ys), max(xs), max(ys)


def bbox_from_row(row: dict[str, str]) -> tuple[int, int, int, int]:
    x = parse_int(row.get("drawX"))
    y = parse_int(row.get("drawY"))
    width = parse_int(row.get("drawWidth"))
    height = parse_int(row.get("drawHeight"))
    return x, y, x + width, y + height


def overlap_area(left: tuple[int, int, int, int], right: tuple[int, int, int, int]) -> int:
    min_x = max(left[0], right[0])
    min_y = max(left[1], right[1])
    max_x = min(left[2], right[2])
    max_y = min(left[3], right[3])
    if max_x <= min_x or max_y <= min_y:
        return 0
    return (max_x - min_x) * (max_y - min_y)


def point_in_polygon(x: float, y: float, points: list[tuple[int, int]]) -> bool:
    inside = False
    count = len(points)
    if count < 3:
        return False
    previous_x, previous_y = points[-1]
    for current_x, current_y in points:
        if (current_y > y) != (previous_y > y):
            intersection_x = (previous_x - current_x) * (y - current_y) / (previous_y - current_y) + current_x
            if x < intersection_x:
                inside = not inside
        previous_x, previous_y = current_x, current_y
    return inside


def load_metadata(path: Path) -> dict[str, str]:
    metadata: dict[str, str] = {}
    if not path.exists():
        return metadata
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        metadata[key] = value
    return metadata


def validate_capture(capture_dir: Path) -> None:
    if not capture_dir.exists():
        fail(f"capture directory does not exist: {capture_dir}")
    if not capture_dir.is_dir():
        fail(f"capture path is not a directory: {capture_dir}")
    missing = [name for name in REQUIRED_FILES if not (capture_dir / name).exists()]
    if missing:
        fail("capture missing required files: " + ", ".join(missing))


def summarize_occlusion_pressure(
    world_faces: list[dict[str, str]],
    sprite_anchors: list[dict[str, str]],
    limit: int,
) -> list[dict[str, object]]:
    face_records = []
    for face in world_faces:
        kind = face.get("modelKind", "")
        if kind not in SPRITE_OCCLUDER_KINDS:
            continue
        bbox = bbox_from_points(parse_int_list(face.get("screenX")), parse_int_list(face.get("screenY")))
        if bbox is None:
            continue
        face_records.append(
            {
                "kind": kind,
                "legacyDrawOrder": parse_int(face.get("legacyDrawOrder"), -1),
                "averageDepth": parse_int(face.get("averageDepth"), -1),
                "bbox": bbox,
            }
        )

    rows = []
    for anchor in sprite_anchors:
        if not parse_bool(anchor.get("legacyEntity")):
            continue
        sprite_order = parse_int(anchor.get("legacyDrawOrder"), -1)
        if sprite_order < 0:
            continue
        sprite_bbox = bbox_from_row(anchor)
        overlaps_by_kind: Counter[str] = Counter()
        max_overlap = 0
        later_faces = 0
        for face in face_records:
            face_order = int(face["legacyDrawOrder"])
            if face_order <= sprite_order:
                continue
            area = overlap_area(sprite_bbox, face["bbox"])  # type: ignore[arg-type]
            if area <= 0:
                continue
            later_faces += 1
            overlaps_by_kind[str(face["kind"])] += 1
            max_overlap = max(max_overlap, area)
        if later_faces == 0:
            continue
        rows.append(
            {
                "spriteId": anchor.get("spriteId", ""),
                "faceId": anchor.get("faceId", ""),
                "legacyDrawOrder": sprite_order,
                "laterOccluderFaces": later_faces,
                "maxOverlapPixels": max_overlap,
                "kinds": ",".join(f"{kind}:{count}" for kind, count in sorted(overlaps_by_kind.items())),
                "bbox": f"{sprite_bbox[0]},{sprite_bbox[1]}-{sprite_bbox[2]},{sprite_bbox[3]}",
            }
        )

    rows.sort(
        key=lambda row: (
            int(row["laterOccluderFaces"]),
            int(row["maxOverlapPixels"]),
            -int(row["legacyDrawOrder"]),
        ),
        reverse=True,
    )
    return rows[: max(0, limit)]


def load_restore_stats(rows: list[dict[str, str]]) -> dict[str, dict[str, str]]:
    return {row.get("legacySpriteId", ""): row for row in rows if row.get("legacySpriteId")}


def load_depth_evaluations(rows: list[dict[str, str]]) -> dict[tuple[str, int, int, int, int, int], dict[str, str]]:
    evaluations: dict[tuple[str, int, int, int, int, int], dict[str, str]] = {}
    for row in rows:
        sprite_id = row.get("legacySpriteId", "")
        if not sprite_id:
            continue
        key = (
            sprite_id,
            parse_int(row.get("sequence"), -1),
            parse_int(row.get("x")),
            parse_int(row.get("y")),
            parse_int(row.get("width")),
            parse_int(row.get("height")),
        )
        evaluations[key] = row
    return evaluations


def summarize_depth_evaluations(rows: list[dict[str, str]], limit: int) -> tuple[dict[str, int], list[dict[str, str]]]:
    summary = {
        "total": len(rows),
        "fullyOccluded": 0,
        "sourcePixels": 0,
        "visiblePixels": 0,
        "occludedPixels": 0,
        "clippedPixels": 0,
        "outOfBoundsPixels": 0,
        "terrainOccludedPixels": 0,
        "wallOccludedPixels": 0,
        "roofOccludedPixels": 0,
        "gameObjectOccludedPixels": 0,
        "wallObjectOccludedPixels": 0,
    }
    fully_occluded: list[dict[str, str]] = []
    for row in rows:
        if parse_bool(row.get("fullyOccluded")):
            summary["fullyOccluded"] += 1
            fully_occluded.append(row)
        summary["sourcePixels"] += parse_int(row.get("sourcePixels"))
        summary["visiblePixels"] += parse_int(row.get("visiblePixels"))
        summary["occludedPixels"] += parse_int(row.get("occludedPixels"))
        summary["clippedPixels"] += parse_int(row.get("clippedPixels"))
        summary["outOfBoundsPixels"] += parse_int(row.get("outOfBoundsPixels"))
        summary["terrainOccludedPixels"] += parse_int(row.get("terrainOccludedPixels"))
        summary["wallOccludedPixels"] += parse_int(row.get("wallOccludedPixels"))
        summary["roofOccludedPixels"] += parse_int(row.get("roofOccludedPixels"))
        summary["gameObjectOccludedPixels"] += parse_int(row.get("gameObjectOccludedPixels"))
        summary["wallObjectOccludedPixels"] += parse_int(row.get("wallObjectOccludedPixels"))
    fully_occluded.sort(
        key=lambda row: (
            parse_int(row.get("sourcePixels")),
            parse_int(row.get("anchorLegacyDrawOrder")),
        ),
        reverse=True,
    )
    return summary, fully_occluded[: max(0, limit)]


def live_occluder_kinds(row: dict[str, str]) -> str:
    parts = []
    for label, column in [
        ("TERRAIN", "terrainOccludedPixels"),
        ("WALL", "wallOccludedPixels"),
        ("ROOF", "roofOccludedPixels"),
        ("GAME_OBJECT", "gameObjectOccludedPixels"),
        ("WALL_OBJECT", "wallObjectOccludedPixels"),
    ]:
        value = parse_int(row.get(column))
        if value > 0:
            parts.append(f"{label}:{value}")
    return ",".join(parts)


def summarize_anchor_match_modes(rows: list[dict[str, str]]) -> Counter[str]:
    modes: Counter[str] = Counter()
    for row in rows:
        mode = row.get("anchorMatchMode", "")
        if not mode:
            mode = "unknown"
        modes[mode] += 1
    return modes


def summarize_world_sprite_commands(rows: list[dict[str, str]]) -> tuple[dict[str, int], Counter[str]]:
    summary = {
        "total": len(rows),
        "anchored": 0,
        "missingAnchor": 0,
        "depthOwned": 0,
        "rendererSnapshotOwned": 0,
        "sourceCropped": 0,
        "mirrorX": 0,
        "skewed": 0,
    }
    modes: Counter[str] = Counter()
    for row in rows:
        mode = row.get("anchorMatchMode", "")
        modes[mode or "unknown"] += 1
        if row.get("anchorLegacyDrawOrder"):
            summary["anchored"] += 1
        else:
            summary["missingAnchor"] += 1
        if parse_bool(row.get("depthOwned")):
            summary["depthOwned"] += 1
        if parse_bool(row.get("rendererSnapshotOwned")):
            summary["rendererSnapshotOwned"] += 1
        if parse_bool(row.get("sourceCropped")):
            summary["sourceCropped"] += 1
        if parse_bool(row.get("mirrorX")):
            summary["mirrorX"] += 1
        if parse_bool(row.get("skewed")):
            summary["skewed"] += 1
    return summary, modes


def summarize_world_sprite_snapshots(rows: list[dict[str, str]]) -> dict[str, int]:
    world_rows = [
        row
        for row in rows
        if (
            5000 <= parse_int(row.get("legacySpriteId"), -1) < 50000
            or parse_bool(row.get("groundItemSource"))
        )
    ]
    groups = {row.get("snapshotIndex", "") for row in world_rows}
    groups.discard("")
    layer_rows = [row for row in world_rows if row.get("layerIndex", "") != ""]
    character_groups = {
        row.get("snapshotIndex", "")
        for row in world_rows
        if row.get("characterKind", "") != ""
    }
    character_groups.discard("")
    ground_item_groups = {
        row.get("snapshotIndex", "")
        for row in world_rows
        if (
            40000 <= parse_int(row.get("legacySpriteId"), -1) < 50000
            or parse_bool(row.get("groundItemSource"))
        )
    }
    ground_item_groups.discard("")
    direct_source_groups = {
        row.get("snapshotIndex", "")
        for row in world_rows
        if parse_bool(row.get("groundItemSource"))
    }
    direct_source_groups.discard("")
    direct_parity_checked_groups = {
        row.get("snapshotIndex", "")
        for row in world_rows
        if parse_bool(row.get("directParityChecked"))
    }
    direct_parity_checked_groups.discard("")
    direct_parity_exact_groups = {
        row.get("snapshotIndex", "")
        for row in world_rows
        if parse_bool(row.get("directParityExact"))
    }
    direct_parity_exact_groups.discard("")
    direct_presentation_groups = {
        row.get("snapshotIndex", "")
        for row in world_rows
        if parse_bool(row.get("directPresentation"))
    }
    direct_presentation_groups.discard("")
    direct_mismatch_groups = {
        row.get("snapshotIndex", "")
        for row in world_rows
        if row.get("directMismatchReason", "") != ""
    }
    direct_mismatch_groups.discard("")
    invalid_anchor_rows = sum(
        1
        for row in world_rows
        if parse_int(row.get("snapshotIndex"), -1)
        != parse_int(row.get("anchorIndex"), -2)
    )
    sequence_order_violations = 0
    previous_sequence_by_group: dict[str, int] = {}
    for row in layer_rows:
        group = row.get("snapshotIndex", "")
        sequence = parse_int(row.get("sequence"), -1)
        previous = previous_sequence_by_group.get(group)
        if previous is not None and sequence <= previous:
            sequence_order_violations += 1
        previous_sequence_by_group[group] = sequence
    return {
        "rows": len(world_rows),
        "groups": len(groups),
        "layers": len(layer_rows),
        "characterGroups": len(character_groups),
        "groundItemGroups": len(ground_item_groups),
        "directSourceGroups": len(direct_source_groups),
        "directParityCheckedGroups": len(direct_parity_checked_groups),
        "directParityExactGroups": len(direct_parity_exact_groups),
        "directPresentationGroups": len(direct_presentation_groups),
        "directMismatchGroups": len(direct_mismatch_groups),
        "invalidAnchorRows": invalid_anchor_rows,
        "sequenceOrderViolations": sequence_order_violations,
    }


def summarize_depth_owned_entity_world_sprite_commands(rows: list[dict[str, str]]) -> Counter[str]:
    counts: Counter[str] = Counter()
    for row in rows:
        if row.get("worldSpriteKind") != "entity":
            continue
        if not parse_bool(row.get("depthOwned")):
            continue
        sprite_id = row.get("legacySpriteId", "")
        if sprite_id:
            counts[sprite_id] += 1
    return counts


def summarize_scene_commands(rows: list[dict[str, str]]) -> tuple[dict[str, int], Counter[str]]:
    summary = {
        "total": len(rows),
        "worldSprite": 0,
        "frontOccluderRange": 0,
        "frontOccluderFaces": 0,
        "staticWorldRange": 0,
        "sourceCropped": 0,
        "mirrorX": 0,
        "skewed": 0,
    }
    modes: Counter[str] = Counter()
    for row in rows:
        kind = row.get("kind", "")
        if kind == "WORLD_SPRITE":
            summary["worldSprite"] += 1
        elif kind == "FRONT_OCCLUDER_RANGE":
            summary["frontOccluderRange"] += 1
            summary["frontOccluderFaces"] += parse_int(row.get("frontOccluderFaces"))
        elif kind == "STATIC_WORLD_RANGE":
            summary["staticWorldRange"] += 1
        mode = row.get("anchorMatchMode", "")
        if mode:
            modes[mode] += 1
        if parse_bool(row.get("sourceCropped")):
            summary["sourceCropped"] += 1
        if parse_bool(row.get("mirrorX")):
            summary["mirrorX"] += 1
        if parse_bool(row.get("skewed")):
            summary["skewed"] += 1
    return summary, modes


def summarize_static_world_commands(
    commands: list[dict[str, str]],
    ownership_rows: list[dict[str, str]],
    world_faces: list[dict[str, str]],
    front_occluder_candidates: list[dict[str, str]],
) -> tuple[dict[str, int], Counter[str], list[str]]:
    summary = {
        "total": len(commands),
        "baseWorld": 0,
        "faceCount": 0,
        "triangleCount": 0,
        "ownedFaces": len(ownership_rows),
        "uniqueFaces": 0,
        "duplicateFaces": 0,
        "unownedWorldFaces": 0,
        "orphanOwnedFaces": 0,
        "unownedFrontOccluders": 0,
    }
    kinds: Counter[str] = Counter()
    errors: list[str] = []
    commands_by_index = {row.get("index", ""): row for row in commands}
    ownership_counts: Counter[str] = Counter()
    owned_face_keys: set[tuple[int, int]] = set()
    for command in commands:
        if command.get("kind") == "BASE_WORLD":
            summary["baseWorld"] += 1
        summary["faceCount"] += parse_int(command.get("faceCount"))
        summary["triangleCount"] += parse_int(command.get("triangleCount"))
        kinds[command.get("modelKind", "") or "UNKNOWN"] += 1
    for row in ownership_rows:
        command_index = row.get("commandIndex", "")
        ownership_counts[command_index] += 1
        command = commands_by_index.get(command_index)
        if command is None:
            errors.append(f"ownership row references missing command {command_index}")
        elif command.get("kind", "") != row.get("kind", ""):
            errors.append(f"ownership row kind differs from command {command_index}")
        elif command.get("modelKind", "") != row.get("modelKind", ""):
            errors.append(f"ownership row model kind differs from command {command_index}")
        face_key = (parse_int(row.get("modelIndex")), parse_int(row.get("faceId")))
        if face_key in owned_face_keys:
            summary["duplicateFaces"] += 1
        owned_face_keys.add(face_key)
    summary["uniqueFaces"] = len(owned_face_keys)
    world_face_keys = {
        (parse_int(row.get("modelIndex")), parse_int(row.get("faceId")))
        for row in world_faces
    }
    summary["unownedWorldFaces"] = len(world_face_keys - owned_face_keys)
    summary["orphanOwnedFaces"] = len(owned_face_keys - world_face_keys)
    for command_index, command in commands_by_index.items():
        expected_faces = parse_int(command.get("faceCount"))
        actual_faces = ownership_counts[command_index]
        if expected_faces != actual_faces:
            errors.append(
                f"command {command_index} face count differs from ownership rows: "
                f"{expected_faces} != {actual_faces}"
            )
    for candidate in front_occluder_candidates:
        face_key = (parse_int(candidate.get("modelIndex")), parse_int(candidate.get("faceId")))
        if face_key not in owned_face_keys:
            summary["unownedFrontOccluders"] += 1
    return summary, kinds, errors


def summarize_static_world_material_triangles(
    rows: list[dict[str, str]], expected_triangle_count: int
) -> tuple[dict[str, int], Counter[str], Counter[str], list[str]]:
    triangle_indices = [parse_int(row.get("triangleIndex"), -1) for row in rows]
    index_counts = Counter(triangle_indices)
    passes = Counter((row.get("materialPass", "") or "UNKNOWN") for row in rows)
    kinds = Counter((row.get("modelKind", "") or "UNKNOWN") for row in rows)
    summary = {
        "total": len(rows),
        "expected": expected_triangle_count,
        "uniqueTriangles": len(index_counts),
        "duplicateTriangles": sum(count - 1 for count in index_counts.values() if count > 1),
        "missingTriangles": len(set(range(expected_triangle_count)) - set(triangle_indices)),
        "outOfRangeTriangles": sum(
            count for index, count in index_counts.items() if index < 0 or index >= expected_triangle_count
        ),
        "unresolved": passes["UNRESOLVED"],
        "translucent": passes["TRANSLUCENT"],
    }
    errors: list[str] = []
    valid_passes = {"OPAQUE", "CUTOUT", "TRANSLUCENT", "DISCARDED", "UNRESOLVED"}
    for row in rows:
        material_pass = row.get("materialPass", "")
        has_transparency = parse_bool(row.get("textureHasTransparency"))
        if material_pass not in valid_passes:
            errors.append(f"unknown static world material pass {material_pass or '<empty>'}")
        elif material_pass == "CUTOUT" and not has_transparency:
            errors.append(f"cutout triangle {row.get('triangleIndex', '')} has no transparent texture pixels")
        elif material_pass == "OPAQUE" and has_transparency:
            errors.append(f"opaque triangle {row.get('triangleIndex', '')} has transparent texture pixels")
    return summary, passes, kinds, errors


def summarize_static_range_candidates(rows: list[dict[str, str]]) -> dict[str, int]:
    summary = {
        "total": len(rows),
        "finalRanges": 0,
        "worldSpriteCommandsAtOrders": 0,
        "staticFaces": 0,
        "overlapFaces": 0,
        "overlapWorldSpriteCommands": 0,
        "overlapTerrainFaces": 0,
        "overlapWallFaces": 0,
        "overlapRoofFaces": 0,
        "overlapGameObjectFaces": 0,
        "overlapWallObjectFaces": 0,
    }
    for row in rows:
        if parse_bool(row.get("finalRange")):
            summary["finalRanges"] += 1
        summary["worldSpriteCommandsAtOrders"] += parse_int(row.get("worldSpriteCommandsAtOrder"))
        summary["staticFaces"] += parse_int(row.get("staticFaces"))
        summary["overlapFaces"] += parse_int(row.get("overlapFaces"))
        summary["overlapWorldSpriteCommands"] += parse_int(row.get("overlapWorldSpriteCommands"))
        summary["overlapTerrainFaces"] += parse_int(row.get("overlapTerrainFaces"))
        summary["overlapWallFaces"] += parse_int(row.get("overlapWallFaces"))
        summary["overlapRoofFaces"] += parse_int(row.get("overlapRoofFaces"))
        summary["overlapGameObjectFaces"] += parse_int(row.get("overlapGameObjectFaces"))
        summary["overlapWallObjectFaces"] += parse_int(row.get("overlapWallObjectFaces"))
    return summary


def top_static_range_candidates(rows: list[dict[str, str]], limit: int) -> list[dict[str, str]]:
    ranked = list(rows)
    ranked.sort(
        key=lambda row: (
            parse_int(row.get("overlapWorldSpriteCommands")),
            parse_int(row.get("overlapFaces")),
            parse_int(row.get("staticFaces")),
            parse_int(row.get("minExclusiveOrder")),
        ),
        reverse=True,
    )
    return ranked[: max(0, limit)]


def summarize_front_occluder_candidates(rows: list[dict[str, str]]) -> dict[str, int]:
    summary = {
        "total": len(rows),
        "wall": 0,
        "gameObject": 0,
        "wallObject": 0,
        "overlapWorldSpriteCommands": 0,
    }
    for row in rows:
        kind = row.get("modelKind", "")
        if kind == "WALL":
            summary["wall"] += 1
        elif kind == "GAME_OBJECT":
            summary["gameObject"] += 1
        elif kind == "WALL_OBJECT":
            summary["wallObject"] += 1
        summary["overlapWorldSpriteCommands"] += parse_int(row.get("overlapWorldSpriteCommands"))
    return summary


def top_front_occluder_candidates(rows: list[dict[str, str]], limit: int) -> list[dict[str, str]]:
    ranked = list(rows)
    ranked.sort(
        key=lambda row: (
            parse_int(row.get("overlapWorldSpriteCommands")),
            parse_int(row.get("legacyDrawOrder")),
            parse_int(row.get("rangeIndex")),
        ),
        reverse=True,
    )
    return ranked[: max(0, limit)]


def summarize_anchor_geometry(rows: list[dict[str, str]]) -> dict[str, int]:
    summary = {
        "count": 0,
        "exactRect": 0,
        "maxAbsDeltaX": 0,
        "maxAbsDeltaY": 0,
        "maxAbsDeltaWidth": 0,
        "maxAbsDeltaHeight": 0,
    }
    for row in rows:
        if not row.get("anchorDeltaX"):
            continue
        delta_x = parse_int(row.get("anchorDeltaX"))
        delta_y = parse_int(row.get("anchorDeltaY"))
        delta_width = parse_int(row.get("anchorDeltaWidth"))
        delta_height = parse_int(row.get("anchorDeltaHeight"))
        summary["count"] += 1
        if delta_x == 0 and delta_y == 0 and delta_width == 0 and delta_height == 0:
            summary["exactRect"] += 1
        summary["maxAbsDeltaX"] = max(summary["maxAbsDeltaX"], abs(delta_x))
        summary["maxAbsDeltaY"] = max(summary["maxAbsDeltaY"], abs(delta_y))
        summary["maxAbsDeltaWidth"] = max(summary["maxAbsDeltaWidth"], abs(delta_width))
        summary["maxAbsDeltaHeight"] = max(summary["maxAbsDeltaHeight"], abs(delta_height))
    return summary


def load_sprite_commands_by_depth_key(
    rows: list[dict[str, str]],
) -> dict[tuple[str, int, int, int, int, int], dict[str, str]]:
    commands: dict[tuple[str, int, int, int, int, int], dict[str, str]] = {}
    for row in rows:
        sprite_id = row.get("legacySpriteId", "")
        if not sprite_id:
            continue
        key = (
            sprite_id,
            parse_int(row.get("sequence"), -1),
            parse_int(row.get("x")),
            parse_int(row.get("y")),
            parse_int(row.get("width")),
            parse_int(row.get("height")),
        )
        commands.setdefault(key, row)
    return commands


def load_depth_owned_world_sprite_keys(
    rows: list[dict[str, str]],
) -> set[tuple[str, int, int, int, int, int]]:
    keys: set[tuple[str, int, int, int, int, int]] = set()
    for row in rows:
        sprite_id = row.get("legacySpriteId", "")
        if not sprite_id or row.get("worldSpriteKind") != "entity":
            continue
        if not parse_bool(row.get("depthOwned")):
            continue
        keys.add(
            (
                sprite_id,
                parse_int(row.get("sequence"), -1),
                parse_int(row.get("x")),
                parse_int(row.get("y")),
                parse_int(row.get("width")),
                parse_int(row.get("height")),
            )
        )
    return keys


def summarize_anchor_geometry_outliers(
    depth_rows: list[dict[str, str]],
    sprite_commands_by_depth_key: dict[tuple[str, int, int, int, int, int], dict[str, str]],
    character_info_by_sprite: dict[str, dict[str, str]],
    limit: int,
) -> list[dict[str, object]]:
    outliers: list[dict[str, object]] = []
    for row in depth_rows:
        sprite_id = row.get("legacySpriteId", "")
        if not sprite_id or not row.get("anchorDeltaX"):
            continue
        delta_x = parse_int(row.get("anchorDeltaX"))
        delta_y = parse_int(row.get("anchorDeltaY"))
        delta_width = parse_int(row.get("anchorDeltaWidth"))
        delta_height = parse_int(row.get("anchorDeltaHeight"))
        if delta_x == 0 and delta_y == 0 and delta_width == 0 and delta_height == 0:
            continue
        key = (
            sprite_id,
            parse_int(row.get("sequence"), -1),
            parse_int(row.get("x")),
            parse_int(row.get("y")),
            parse_int(row.get("width")),
            parse_int(row.get("height")),
        )
        command = sprite_commands_by_depth_key.get(key, {})
        source_x = parse_int(command.get("sourceX"))
        source_y = parse_int(command.get("sourceY"))
        source_width = parse_int(command.get("sourceWidth"))
        source_height = parse_int(command.get("sourceHeight"))
        sprite_width = parse_int(command.get("spriteWidth"))
        sprite_height = parse_int(command.get("spriteHeight"))
        source_crop = (
            source_x != 0
            or source_y != 0
            or (sprite_width > 0 and source_width > 0 and source_width != sprite_width)
            or (sprite_height > 0 and source_height > 0 and source_height != sprite_height)
        )
        delta_score = abs(delta_x) + abs(delta_y) + abs(delta_width) + abs(delta_height)
        character = character_info_by_sprite.get(sprite_id, {})
        outliers.append(
            {
                "deltaScore": delta_score,
                "kind": character.get("kind", ""),
                "displayName": character.get("displayName", ""),
                "serverIndex": character.get("serverIndex", ""),
                "legacySpriteId": sprite_id,
                "sequence": row.get("sequence", ""),
                "anchorMatchMode": row.get("anchorMatchMode", ""),
                "anchorMatchScore": row.get("anchorMatchScore", ""),
                "commandRect": (
                    f"{row.get('x', '')},{row.get('y', '')},"
                    f"{row.get('width', '')}x{row.get('height', '')}"
                ),
                "anchorRect": (
                    f"{row.get('anchorDrawX', '')},{row.get('anchorDrawY', '')},"
                    f"{row.get('anchorDrawWidth', '')}x{row.get('anchorDrawHeight', '')}"
                ),
                "deltaRect": f"{delta_x},{delta_y},{delta_width}x{delta_height}",
                "sourceRect": f"{source_x},{source_y},{source_width}x{source_height}",
                "spriteSize": f"{sprite_width}x{sprite_height}",
                "sourceCrop": source_crop,
                "mirrorX": command.get("mirrorX", ""),
                "topX16": row.get("topX16", ""),
                "bottomX16": row.get("bottomX16", ""),
            }
        )
    outliers.sort(
        key=lambda outlier: (
            int(outlier["deltaScore"]),
            str(outlier["legacySpriteId"]),
            int(str(outlier["sequence"]) or 0),
        ),
        reverse=True,
    )
    return outliers[: max(0, limit)]


def summarize_anchor_geometry_context(
    depth_rows: list[dict[str, str]],
    sprite_commands_by_depth_key: dict[tuple[str, int, int, int, int, int], dict[str, str]],
) -> dict[str, int]:
    summary = {
        "nonExact": 0,
        "sourceCrop": 0,
        "uncropped": 0,
        "mirrorX": 0,
        "skewed": 0,
        "missingCommand": 0,
    }
    for row in depth_rows:
        if not row.get("anchorDeltaX"):
            continue
        delta_x = parse_int(row.get("anchorDeltaX"))
        delta_y = parse_int(row.get("anchorDeltaY"))
        delta_width = parse_int(row.get("anchorDeltaWidth"))
        delta_height = parse_int(row.get("anchorDeltaHeight"))
        if delta_x == 0 and delta_y == 0 and delta_width == 0 and delta_height == 0:
            continue
        summary["nonExact"] += 1
        key = (
            row.get("legacySpriteId", ""),
            parse_int(row.get("sequence"), -1),
            parse_int(row.get("x")),
            parse_int(row.get("y")),
            parse_int(row.get("width")),
            parse_int(row.get("height")),
        )
        command = sprite_commands_by_depth_key.get(key)
        if command is None:
            summary["missingCommand"] += 1
            continue
        source_x = parse_int(command.get("sourceX"))
        source_y = parse_int(command.get("sourceY"))
        source_width = parse_int(command.get("sourceWidth"))
        source_height = parse_int(command.get("sourceHeight"))
        sprite_width = parse_int(command.get("spriteWidth"))
        sprite_height = parse_int(command.get("spriteHeight"))
        source_crop = (
            source_x != 0
            or source_y != 0
            or (sprite_width > 0 and source_width > 0 and source_width != sprite_width)
            or (sprite_height > 0 and source_height > 0 and source_height != sprite_height)
        )
        if source_crop:
            summary["sourceCrop"] += 1
        else:
            summary["uncropped"] += 1
        if parse_bool(command.get("mirrorX")):
            summary["mirrorX"] += 1
        if parse_int(row.get("topX16")) != parse_int(row.get("bottomX16")):
            summary["skewed"] += 1
    return summary


def summarize_fully_occluded_entities(
    rows: list[dict[str, str]],
    character_info_by_sprite: dict[str, dict[str, str]],
    limit: int,
) -> list[dict[str, object]]:
    grouped: dict[str, dict[str, object]] = {}
    for row in rows:
        sprite_id = row.get("legacySpriteId", "")
        if not sprite_id:
            continue
        entity = grouped.setdefault(
            sprite_id,
            {
                "spriteId": sprite_id,
                "commands": 0,
                "sourcePixels": 0,
                "occludedPixels": 0,
                "kinds": Counter(),
                "dominantFaces": Counter(),
                "topFace": "",
            },
        )
        entity["commands"] = int(entity["commands"]) + 1
        entity["sourcePixels"] = int(entity["sourcePixels"]) + parse_int(row.get("sourcePixels"))
        entity["occludedPixels"] = int(entity["occludedPixels"]) + parse_int(row.get("occludedPixels"))
        for part in live_occluder_kinds(row).split(","):
            if not part or ":" not in part:
                continue
            kind, count = part.split(":", 1)
            entity["kinds"][kind] += parse_int(count)  # type: ignore[index]
        dominant = (
            f"{row.get('dominantOccluderKind', '')}:"
            f"face{row.get('dominantOccluderFaceId', '')}:"
            f"model{row.get('dominantOccluderModelIndex', '')}"
        )
        pixels = parse_int(row.get("dominantOccluderPixels"))
        if pixels > 0:
            entity["dominantFaces"][dominant] += pixels  # type: ignore[index]

    result: list[dict[str, object]] = []
    for sprite_id, entity in grouped.items():
        character = character_info_by_sprite.get(sprite_id, {})
        dominant_faces = entity["dominantFaces"]  # type: ignore[assignment]
        top_face = ""
        if dominant_faces:
            top_face = dominant_faces.most_common(1)[0][0]
        kinds = entity["kinds"]  # type: ignore[assignment]
        result.append(
            {
                "spriteId": sprite_id,
                "kind": character.get("kind", ""),
                "displayName": character.get("displayName", ""),
                "serverIndex": character.get("serverIndex", ""),
                "commands": entity["commands"],
                "sourcePixels": entity["sourcePixels"],
                "occludedPixels": entity["occludedPixels"],
                "liveOccluderKinds": ",".join(
                    f"{kind}:{count}" for kind, count in sorted(kinds.items())
                ),
                "topDominantFace": top_face,
            }
        )
    result.sort(
        key=lambda row: (
            int(row["occludedPixels"]),
            int(row["sourcePixels"]),
            int(row["commands"]),
        ),
        reverse=True,
    )
    return result[: max(0, limit)]


EXPECTED_ENTITY_VISIBILITY_DIAGNOSES = {
    "restore-depth-visible",
    "restore-visible",
    "restore-depth-fully-occluded",
    "restore-depth-out-of-bounds",
    "restore-depth-clipped",
}


def summarize_entity_visibility(
    characters: list[dict[str, str]],
    depth_owned_entity_world_sprite_counts: Counter[str],
    limit: int,
) -> tuple[dict[str, int], list[dict[str, str]]]:
    summary = {
        "projectedWithBody": 0,
        "visible": 0,
        "expectedFullyOccluded": 0,
        "expectedOutOfFrame": 0,
        "notProjected": 0,
        "suspicious": 0,
    }
    suspicious: list[dict[str, str]] = []
    for row in characters:
        diagnosis = row.get("diagnosis", "")
        if diagnosis.startswith("not-projected:"):
            summary["notProjected"] += 1
            continue
        if not parse_bool(row.get("projected")) and diagnosis.startswith("not-projected:"):
            summary["notProjected"] += 1
            continue
        body_commands = parse_int(row.get("bodyCommands"), 1 if diagnosis else 0)
        body_source_pixels = parse_int(row.get("bodySourceVisiblePixels"), 1 if diagnosis else 0)
        if body_commands <= 0 or body_source_pixels <= 0:
            continue
        summary["projectedWithBody"] += 1
        restore_depth_evaluations = parse_int(row.get("restoreDepthEvaluations"))
        restore_depth_source_pixels = parse_int(row.get("restoreDepthSourcePixels"))
        restore_depth_visible_pixels = parse_int(row.get("restoreDepthVisiblePixels"))
        restore_depth_occluded_pixels = parse_int(row.get("restoreDepthOccludedPixels"))
        restore_depth_clipped_pixels = parse_int(row.get("restoreDepthClippedPixels"))
        restore_depth_out_of_bounds_pixels = parse_int(row.get("restoreDepthOutOfBoundsPixels"))
        depth_owned_world_sprite_commands = depth_owned_entity_world_sprite_counts.get(row.get("spriteId", ""), 0)
        if diagnosis in {"restore-depth-visible", "restore-visible"}:
            summary["visible"] += 1
        elif (
            diagnosis == "body-command-before-restore-stats"
            and depth_owned_world_sprite_commands >= body_commands
        ):
            summary["visible"] += 1
        elif diagnosis == "restore-depth-fully-occluded":
            summary["expectedFullyOccluded"] += 1
        elif (
            diagnosis in {"restore-depth-out-of-bounds", "restore-depth-clipped"}
            or (
                restore_depth_evaluations > 0
                and restore_depth_source_pixels > 0
                and restore_depth_visible_pixels == 0
                and (
                    restore_depth_out_of_bounds_pixels > 0
                    or restore_depth_clipped_pixels > 0
                )
                and (
                    restore_depth_out_of_bounds_pixels
                    + restore_depth_clipped_pixels
                    + restore_depth_occluded_pixels
                    >= restore_depth_source_pixels
                )
            )
        ):
            summary["expectedOutOfFrame"] += 1
        elif diagnosis not in EXPECTED_ENTITY_VISIBILITY_DIAGNOSES:
            summary["suspicious"] += 1
            suspicious.append(row)
    suspicious.sort(
        key=lambda row: (
            parse_int(row.get("bodySourceVisiblePixels")),
            parse_int(row.get("bodyCommands")),
        ),
        reverse=True,
    )
    return summary, suspicious[: max(0, limit)]


def load_character_diagnoses(rows: list[dict[str, str]]) -> dict[str, str]:
    diagnoses: dict[str, str] = {}
    for row in rows:
        sprite_id = row.get("spriteId", "")
        if sprite_id and sprite_id not in diagnoses:
            diagnoses[sprite_id] = row.get("diagnosis", "")
    return diagnoses


def load_character_info(rows: list[dict[str, str]]) -> dict[str, dict[str, str]]:
    info: dict[str, dict[str, str]] = {}
    for row in rows:
        sprite_id = row.get("spriteId", "")
        if sprite_id and sprite_id not in info:
            info[sprite_id] = row
    return info


def build_occluder_faces(world_faces: list[dict[str, str]]) -> list[dict[str, object]]:
    records: list[dict[str, object]] = []
    for face in world_faces:
        kind = face.get("modelKind", "")
        if kind not in SPRITE_OCCLUDER_KINDS:
            continue
        screen_x = parse_int_list(face.get("screenX"))
        screen_y = parse_int_list(face.get("screenY"))
        if len(screen_x) < 3 or len(screen_x) != len(screen_y):
            continue
        bbox = bbox_from_points(screen_x, screen_y)
        if bbox is None:
            continue
        records.append(
            {
                "kind": kind,
                "legacyDrawOrder": parse_int(face.get("legacyDrawOrder"), -1),
                "averageDepth": parse_int(face.get("averageDepth"), -1),
                "points": list(zip(screen_x, screen_y)),
                "bbox": bbox,
            }
        )
    return records


def build_entity_sprite_subjects(
    sprite_commands: list[dict[str, str]],
    sprite_anchors: list[dict[str, str]],
) -> list[dict[str, object]]:
    anchors_by_sprite: dict[str, dict[str, str]] = {}
    for anchor in sprite_anchors:
        if parse_bool(anchor.get("legacyEntity")):
            anchors_by_sprite.setdefault(anchor.get("spriteId", ""), anchor)

    subjects: list[dict[str, object]] = []
    seen_commands: set[tuple[str, int, int, int, int, int]] = set()
    for command in sprite_commands:
        if command.get("phase") != "SCENE" or not parse_bool(command.get("legacyEntity")):
            continue
        sprite_id = command.get("legacySpriteId", "")
        anchor = anchors_by_sprite.get(sprite_id)
        if anchor is None:
            continue
        command_key = (
            sprite_id,
            parse_int(command.get("sequence"), -1),
            parse_int(command.get("x")),
            parse_int(command.get("y")),
            parse_int(command.get("width")),
            parse_int(command.get("height")),
        )
        if command_key in seen_commands:
            continue
        seen_commands.add(command_key)
        subjects.append(
            {
                "spriteId": sprite_id,
                "sequence": parse_int(command.get("sequence"), -1),
                "faceId": anchor.get("faceId", ""),
                "legacyDrawOrder": parse_int(anchor.get("legacyDrawOrder"), -1),
                "bbox": bbox_from_row(
                    {
                        "drawX": command.get("x", "0"),
                        "drawY": command.get("y", "0"),
                        "drawWidth": command.get("width", "0"),
                        "drawHeight": command.get("height", "0"),
                    }
                ),
                "width": parse_int(command.get("width")),
                "height": parse_int(command.get("height")),
            }
        )

    if subjects:
        return subjects

    for anchor in sprite_anchors:
        if not parse_bool(anchor.get("legacyEntity")):
            continue
        subjects.append(
            {
                "spriteId": anchor.get("spriteId", ""),
                "sequence": -1,
                "faceId": anchor.get("faceId", ""),
                "legacyDrawOrder": parse_int(anchor.get("legacyDrawOrder"), -1),
                "bbox": bbox_from_row(anchor),
                "width": parse_int(anchor.get("drawWidth")),
                "height": parse_int(anchor.get("drawHeight")),
            }
        )
    return subjects


def rasterize_face_into_sprite(
    sprite_bbox: tuple[int, int, int, int],
    sprite_width: int,
    sprite_height: int,
    face: dict[str, object],
) -> set[tuple[int, int]]:
    face_bbox = face["bbox"]  # type: ignore[assignment]
    clip_min_x = max(sprite_bbox[0], face_bbox[0])  # type: ignore[index]
    clip_min_y = max(sprite_bbox[1], face_bbox[1])  # type: ignore[index]
    clip_max_x = min(sprite_bbox[2], face_bbox[2])  # type: ignore[index]
    clip_max_y = min(sprite_bbox[3], face_bbox[3])  # type: ignore[index]
    if clip_max_x <= clip_min_x or clip_max_y <= clip_min_y:
        return set()
    points = face["points"]  # type: ignore[assignment]
    pixels: set[tuple[int, int]] = set()
    for screen_y in range(clip_min_y, clip_max_y):
        local_y = screen_y - sprite_bbox[1]
        if local_y < 0 or local_y >= sprite_height:
            continue
        for screen_x in range(clip_min_x, clip_max_x):
            local_x = screen_x - sprite_bbox[0]
            if local_x < 0 or local_x >= sprite_width:
                continue
            if point_in_polygon(screen_x + 0.5, screen_y + 0.5, points):  # type: ignore[arg-type]
                pixels.add((local_x, local_y))
    return pixels


def replay_entity_occlusion(
    world_faces: list[dict[str, str]],
    sprite_commands: list[dict[str, str]],
    sprite_anchors: list[dict[str, str]],
    restore_stats: dict[str, dict[str, str]],
    depth_evaluations: dict[tuple[str, int, int, int, int, int], dict[str, str]],
    depth_owned_world_sprite_keys: set[tuple[str, int, int, int, int, int]],
    limit: int | None,
) -> list[dict[str, object]]:
    occluder_faces = build_occluder_faces(world_faces)
    subjects = build_entity_sprite_subjects(sprite_commands, sprite_anchors)
    rows: list[dict[str, object]] = []
    for subject in subjects:
        sprite_order = int(subject["legacyDrawOrder"])
        sprite_width = int(subject["width"])
        sprite_height = int(subject["height"])
        if sprite_order < 0 or sprite_width <= 0 or sprite_height <= 0:
            continue
        sprite_bbox = subject["bbox"]  # type: ignore[assignment]
        sprite_pixels = max(1, sprite_width * sprite_height)
        covered_pixels: set[tuple[int, int]] = set()
        pixels_by_kind: Counter[str] = Counter()
        candidate_faces = 0
        raster_faces = 0
        for face in occluder_faces:
            if int(face["legacyDrawOrder"]) <= sprite_order:
                continue
            if overlap_area(sprite_bbox, face["bbox"]):  # type: ignore[arg-type]
                candidate_faces += 1
            face_pixels = rasterize_face_into_sprite(sprite_bbox, sprite_width, sprite_height, face)
            if not face_pixels:
                continue
            raster_faces += 1
            newly_covered = face_pixels - covered_pixels
            if newly_covered:
                pixels_by_kind[str(face["kind"])] += len(newly_covered)
            covered_pixels.update(face_pixels)
        if not covered_pixels:
            continue
        sprite_id = str(subject["spriteId"])
        depth_key = (
            sprite_id,
            int(subject["sequence"]),
            sprite_bbox[0],
            sprite_bbox[1],
            sprite_width,
            sprite_height,
        )
        depth_stats = depth_evaluations.get(depth_key)
        depth_owned_world_sprite = depth_key in depth_owned_world_sprite_keys
        stats = depth_stats if depth_stats is not None else restore_stats.get(sprite_id, {})
        depth_stats_source = "command" if depth_stats is not None else "sprite"
        if depth_stats is None and depth_owned_world_sprite:
            depth_stats_source = "world-sprite-depth-owned"
        rows.append(
            {
                "spriteId": sprite_id,
                "sequence": subject["sequence"],
                "faceId": subject["faceId"],
                "legacyDrawOrder": sprite_order,
                "candidateFaces": candidate_faces,
                "rasterFaces": raster_faces,
                "occludedPixels": len(covered_pixels),
                "spritePixels": sprite_pixels,
                "occludedPct": len(covered_pixels) * 100.0 / sprite_pixels,
                "kinds": ",".join(f"{kind}:{count}" for kind, count in sorted(pixels_by_kind.items())),
                "depthStatsSource": depth_stats_source,
                "depthMisses": parse_int(stats.get("depthMisses")) if depth_stats is None else int(parse_bool(stats.get("fullyOccluded"))),
                "depthEvaluations": parse_int(stats.get("depthEvaluations")) if depth_stats is None else 1,
                "depthVisiblePixels": parse_int(stats.get("depthVisiblePixels") or stats.get("visiblePixels")),
                "depthSourcePixels": parse_int(stats.get("depthSourcePixels") or stats.get("sourcePixels")),
                "depthOccludedPixels": parse_int(stats.get("depthOccludedPixels") or stats.get("occludedPixels")),
                "bbox": f"{sprite_bbox[0]},{sprite_bbox[1]}-{sprite_bbox[2]},{sprite_bbox[3]}",
            }
        )
    rows.sort(
        key=lambda row: (
            float(row["occludedPct"]),
            int(row["occludedPixels"]),
            int(row["candidateFaces"]),
        ),
        reverse=True,
    )
    if limit is None:
        return rows
    return rows[: max(0, limit)]


def find_occlusion_disagreements(
    replay_rows: list[dict[str, object]],
    character_diagnoses: dict[str, str],
    threshold: float,
    limit: int,
) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    threshold = max(0.0, min(100.0, threshold))
    for row in replay_rows:
        occluded_pct = float(row["occludedPct"])
        if occluded_pct < threshold:
            continue
        if row.get("depthStatsSource") == "world-sprite-depth-owned":
            continue
        depth_misses = int(row["depthMisses"])
        depth_evaluations = int(row["depthEvaluations"])
        depth_source_pixels = int(row["depthSourcePixels"])
        depth_occluded_pixels = int(row["depthOccludedPixels"])
        if depth_evaluations <= 0 and depth_misses <= 0:
            reason = "no-depth-mask-stats"
        elif depth_source_pixels <= 0:
            reason = "depth-mask-no-source-pixels"
        else:
            captured_pct = depth_occluded_pixels * 100.0 / depth_source_pixels
            if captured_pct >= threshold:
                continue
            reason = "depth-mask-covered-less-than-replay"
        rows.append(
            {
                "spriteId": row["spriteId"],
                "faceId": row["faceId"],
                "occludedPct": occluded_pct,
                "occludedPixels": row["occludedPixels"],
                "spritePixels": row["spritePixels"],
                "depthMisses": depth_misses,
                "depthEvaluations": depth_evaluations,
                "depthVisiblePixels": row["depthVisiblePixels"],
                "depthSourcePixels": depth_source_pixels,
                "depthOccludedPixels": depth_occluded_pixels,
                "kinds": row["kinds"],
                "diagnosis": character_diagnoses.get(str(row["spriteId"]), ""),
                "reason": reason,
            }
        )
    rows.sort(
        key=lambda row: (
            str(row["reason"]),
            float(row["occludedPct"]),
            int(row["occludedPixels"]),
        ),
        reverse=True,
    )
    return rows[: max(0, limit)]


def print_counter(title: str, counter: Counter[str]) -> None:
    print(title)
    if not counter:
        print("  none")
        return
    for key, count in sorted(counter.items()):
        print(f"  {key}: {count}")


def summarize_resident_material_families(
    rows: list[dict[str, str]], expected: int
) -> tuple[dict[str, int], Counter[str], Counter[str], list[str]]:
    families: Counter[str] = Counter()
    model_kinds: Counter[str] = Counter()
    seen: set[tuple[str, ...]] = set()
    duplicate_rows = 0
    invalid_rows = 0
    water_non_terrain = 0
    semantic_non_object = 0
    emissive_without_glow = 0
    errors: list[str] = []
    total_triangles = 0
    for row in rows:
        key = tuple(
            row.get(field, "")
            for field in (
                "chunkIndex",
                "modelKind",
                "materialFamily",
                "shaderId",
                "textureId",
                "fallbackColor",
                "glowEmitterCount",
            )
        )
        if key in seen:
            duplicate_rows += 1
        seen.add(key)
        triangle_count = max(0, parse_int(row.get("triangleCount")))
        total_triangles += triangle_count
        family = row.get("materialFamily", "UNCLASSIFIED")
        model_kind = row.get("modelKind", "UNCLASSIFIED")
        families[family] += triangle_count
        model_kinds[model_kind] += triangle_count
        expected_shader_id = MATERIAL_FAMILY_SHADER_IDS.get(family)
        shader_id = parse_int(row.get("shaderId"), -1)
        if expected_shader_id is None or shader_id != expected_shader_id:
            invalid_rows += 1
            errors.append(
                f"resident material family {family!r} has shader id {shader_id}, "
                f"expected {expected_shader_id!r}"
            )
        if family == "WATER" and model_kind != "TERRAIN":
            water_non_terrain += 1
        if family in {"FOLIAGE", "ORE"} and model_kind not in {"GAME_OBJECT", "WALL_OBJECT"}:
            semantic_non_object += 1
        if (
            family == "EMISSIVE"
            and "glowEmitterCount" in row
            and parse_int(row.get("glowEmitterCount")) <= 0
        ):
            emissive_without_glow += 1
    contradictions = water_non_terrain + semantic_non_object + emissive_without_glow
    summary = {
        "rows": len(rows),
        "total": total_triangles,
        "expected": expected,
        "uniqueRows": len(seen),
        "duplicateRows": duplicate_rows,
        "missingTriangles": max(0, expected - total_triangles),
        "invalidRows": invalid_rows,
        "contradictions": contradictions,
        "waterNonTerrain": water_non_terrain,
        "semanticNonObject": semantic_non_object,
        "emissiveWithoutGlow": emissive_without_glow,
        "unclassified": families.get("UNCLASSIFIED", 0),
    }
    return summary, families, model_kinds, errors


def main() -> None:
    args = parse_args()
    capture_dir = args.capture_dir.resolve()
    validate_capture(capture_dir)

    metadata = load_metadata(capture_dir / "metadata.txt")
    world_faces = read_tsv(capture_dir / "world-faces.tsv")
    sprite_commands = read_tsv(capture_dir / "sprite-commands.tsv")
    world_sprite_snapshots = read_optional_tsv(capture_dir / "world-sprite-snapshots.tsv")
    world_sprite_commands = read_optional_tsv(capture_dir / "world-sprite-commands.tsv")
    world_sprite_batch_stats = read_optional_tsv(capture_dir / "world-sprite-batch-stats.tsv")
    scene_commands = read_optional_tsv(capture_dir / "scene-commands.tsv")
    static_world_commands = read_optional_tsv(capture_dir / "static-world-commands.tsv")
    static_world_face_ownership = read_optional_tsv(capture_dir / "static-world-face-ownership.tsv")
    static_world_material_path = capture_dir / "static-world-material-triangles.tsv"
    static_world_material_triangles = read_optional_tsv(static_world_material_path)
    resident_material_path = capture_dir / "resident-material-families.tsv"
    resident_material_families = read_optional_tsv(resident_material_path)
    static_range_candidates = read_optional_tsv(capture_dir / "static-range-candidates.tsv")
    front_occluder_candidates = read_optional_tsv(capture_dir / "front-occluder-candidates.tsv")
    renderer_2d_command_limits = read_optional_tsv(capture_dir / "renderer-2d-command-limits.tsv")
    sprite_anchors = read_tsv(capture_dir / "sprite-anchors.tsv")
    sprite_submissions = read_tsv(capture_dir / "sprite-submissions.tsv")
    characters = read_tsv(capture_dir / "character-sprites.tsv")
    restore_stats = load_restore_stats(read_tsv(capture_dir / "entity-restore-stats.tsv"))
    depth_evaluation_rows = read_optional_tsv(capture_dir / "entity-depth-evaluations.tsv")
    depth_evaluations = load_depth_evaluations(depth_evaluation_rows)
    sprite_commands_by_depth_key = load_sprite_commands_by_depth_key(sprite_commands)
    depth_owned_world_sprite_keys = load_depth_owned_world_sprite_keys(world_sprite_commands)
    character_diagnoses_by_sprite = load_character_diagnoses(characters)
    character_info_by_sprite = load_character_info(characters)

    world_kinds = Counter(row.get("modelKind", "UNKNOWN") for row in world_faces)
    sprite_phases = Counter(row.get("phase", "UNKNOWN") for row in sprite_commands)
    character_kinds = Counter(row.get("kind", "UNKNOWN") for row in characters)
    character_diagnoses = Counter(row.get("diagnosis", "UNKNOWN") for row in characters)
    entity_command_count = sum(1 for row in sprite_commands if parse_bool(row.get("legacyEntity")))
    entity_anchor_count = sum(1 for row in sprite_anchors if parse_bool(row.get("legacyEntity")))

    if args.strict:
        if not world_faces:
            fail("world-faces.tsv has no static world rows")
        if not sprite_commands:
            fail("sprite-commands.tsv has no sprite rows")
        if not sprite_anchors:
            fail("sprite-anchors.tsv has no anchor rows")

    print(f"capture={capture_dir}")
    print(f"source={metadata.get('source', 'unknown')}")
    print(f"target={metadata.get('target', 'unknown')}")
    print(f"worldReplacementComposite={metadata.get('worldReplacementComposite', 'unknown')}")
    print(
        "counts="
        f"worldFaces:{len(world_faces)} "
        f"spriteCommands:{len(sprite_commands)} "
        f"worldSpriteSnapshotRows:{len(world_sprite_snapshots)} "
        f"worldSpriteCommands:{len(world_sprite_commands)} "
        f"sceneCommands:{len(scene_commands)} "
        f"staticWorldCommands:{len(static_world_commands)} "
        f"staticWorldOwnedFaces:{len(static_world_face_ownership)} "
        f"staticWorldMaterialTriangles:{len(static_world_material_triangles)} "
        f"residentMaterialRows:{len(resident_material_families)} "
        f"staticRangeCandidates:{len(static_range_candidates)} "
        f"frontOccluderCandidates:{len(front_occluder_candidates)} "
        f"spriteAnchors:{len(sprite_anchors)} "
        f"spriteSubmissions:{len(sprite_submissions)} "
        f"characters:{len(characters)} "
        f"entityDepthEvaluations:{len(depth_evaluation_rows)} "
        f"entitySpriteCommands:{entity_command_count} "
        f"entitySpriteAnchors:{entity_anchor_count}"
    )
    print_counter("worldKinds:", world_kinds)
    print_counter("spritePhases:", sprite_phases)
    print("renderer2DCommandLimits:")
    if not renderer_2d_command_limits:
        print("  none")
    else:
        for row in renderer_2d_command_limits:
            stream = row.get("stream", "unknown")
            limit = parse_int(row.get("limit"))
            attempted = parse_int(row.get("attempted"))
            accepted = parse_int(row.get("accepted"))
            dropped = parse_int(row.get("dropped"))
            if accepted > limit:
                fail(f"renderer 2D {stream} accepted count exceeds limit: {accepted} > {limit}")
            if attempted != accepted + dropped:
                fail(
                    f"renderer 2D {stream} attempted count does not match accepted+dropped: "
                    f"{attempted} != {accepted}+{dropped}"
                )
            print(
                f"  {stream}: limit={limit} attempted={attempted} "
                f"accepted={accepted} dropped={dropped}"
            )
    world_sprite_summary, world_sprite_match_modes = summarize_world_sprite_commands(world_sprite_commands)
    depth_owned_entity_world_sprite_counts = summarize_depth_owned_entity_world_sprite_commands(
        world_sprite_commands
    )
    if (
        args.strict
        and world_sprite_commands
        and "depthOwned" in world_sprite_commands[0]
        and world_sprite_summary["depthOwned"] != world_sprite_summary["anchored"]
    ):
        fail(
            "anchored world sprites are missing GPU depth ownership: "
            f"{world_sprite_summary['anchored'] - world_sprite_summary['depthOwned']}"
        )
    print("worldSpriteCommands:")
    if not world_sprite_commands:
        print("  none")
    else:
        print(
            "  "
            + " ".join(
                f"{key}:{value}"
                for key, value in [
                    ("total", world_sprite_summary["total"]),
                    ("anchored", world_sprite_summary["anchored"]),
                    ("missingAnchor", world_sprite_summary["missingAnchor"]),
                    ("depthOwned", world_sprite_summary["depthOwned"]),
                    (
                        "rendererSnapshotOwned",
                        world_sprite_summary["rendererSnapshotOwned"],
                    ),
                    ("sourceCropped", world_sprite_summary["sourceCropped"]),
                    ("mirrorX", world_sprite_summary["mirrorX"]),
                    ("skewed", world_sprite_summary["skewed"]),
                ]
            )
        )
        print_counter("worldSpriteAnchorMatchModes:", world_sprite_match_modes)
    world_sprite_snapshot_summary = summarize_world_sprite_snapshots(
        world_sprite_snapshots
    )
    if args.strict and world_sprite_snapshots:
        if (
            world_sprite_snapshot_summary["layers"]
            != world_sprite_summary["rendererSnapshotOwned"]
        ):
            fail(
                "renderer snapshot layer total differs from snapshot-owned world commands: "
                f"{world_sprite_snapshot_summary['layers']} != "
                f"{world_sprite_summary['rendererSnapshotOwned']}"
            )
        if world_sprite_snapshot_summary["invalidAnchorRows"] != 0:
            fail(
                "renderer snapshot rows contain invalid anchor ownership: "
                f"{world_sprite_snapshot_summary['invalidAnchorRows']}"
            )
        if world_sprite_snapshot_summary["sequenceOrderViolations"] != 0:
            fail(
                "renderer snapshot layer order is not stable: "
                f"{world_sprite_snapshot_summary['sequenceOrderViolations']}"
            )
        if "groundItemSource" in world_sprite_snapshots[0]:
            if (
                world_sprite_snapshot_summary["directSourceGroups"]
                != world_sprite_snapshot_summary["groundItemGroups"]
            ):
                fail(
                    "ground-item snapshots are missing direct source inputs: "
                    f"{world_sprite_snapshot_summary['directSourceGroups']} != "
                    f"{world_sprite_snapshot_summary['groundItemGroups']}"
                )
            if (
                world_sprite_snapshot_summary["directParityCheckedGroups"]
                != world_sprite_snapshot_summary["groundItemGroups"]
                or world_sprite_snapshot_summary["directParityExactGroups"]
                != world_sprite_snapshot_summary["groundItemGroups"]
                or world_sprite_snapshot_summary["directPresentationGroups"]
                != world_sprite_snapshot_summary["groundItemGroups"]
                or world_sprite_snapshot_summary["directMismatchGroups"] != 0
            ):
                fail(
                    "ground-item direct inputs did not reach exact active parity: "
                    f"items={world_sprite_snapshot_summary['groundItemGroups']} "
                    f"checked={world_sprite_snapshot_summary['directParityCheckedGroups']} "
                    f"exact={world_sprite_snapshot_summary['directParityExactGroups']} "
                    f"active={world_sprite_snapshot_summary['directPresentationGroups']} "
                    f"mismatch={world_sprite_snapshot_summary['directMismatchGroups']}"
                )
    print("worldSpriteSnapshots:")
    if not world_sprite_snapshots:
        print("  none")
    else:
        print(
            "  "
            + " ".join(
                f"{key}:{value}"
                for key, value in world_sprite_snapshot_summary.items()
            )
        )
    print("worldSpriteBatches:")
    if not world_sprite_batch_stats:
        print("  none")
    else:
        batch_commands = parse_int(world_sprite_batch_stats[0].get("commands"))
        texture_batches = parse_int(world_sprite_batch_stats[0].get("textureBatches"))
        if args.strict and batch_commands != world_sprite_summary["depthOwned"]:
            fail(
                "world sprite batch command total differs from depth-owned commands: "
                f"{batch_commands} != {world_sprite_summary['depthOwned']}"
            )
        if args.strict and batch_commands > 0 and texture_batches <= 0:
            fail("world sprite batch capture has commands but no texture batches")
        print(f"  commands:{batch_commands} textureBatches:{texture_batches}")
    scene_command_summary, scene_command_match_modes = summarize_scene_commands(scene_commands)
    print("sceneCommandQueue:")
    if not scene_commands:
        print("  none")
    else:
        print(
            "  "
            + " ".join(
                f"{key}:{value}"
                for key, value in [
                    ("total", scene_command_summary["total"]),
                    ("worldSprite", scene_command_summary["worldSprite"]),
                    ("frontOccluderRange", scene_command_summary["frontOccluderRange"]),
                    ("frontOccluderFaces", scene_command_summary["frontOccluderFaces"]),
                    ("staticWorldRange", scene_command_summary["staticWorldRange"]),
                    ("sourceCropped", scene_command_summary["sourceCropped"]),
                    ("mirrorX", scene_command_summary["mirrorX"]),
                    ("skewed", scene_command_summary["skewed"]),
                ]
            )
        )
        print_counter("sceneCommandAnchorMatchModes:", scene_command_match_modes)
    static_world_summary, static_world_kinds, static_world_errors = summarize_static_world_commands(
        static_world_commands,
        static_world_face_ownership,
        world_faces,
        front_occluder_candidates,
    )
    if args.strict and (static_world_commands or static_world_face_ownership):
        if not static_world_commands or not static_world_face_ownership:
            fail("static world command capture requires both command and face ownership rows")
        if static_world_summary["faceCount"] != static_world_summary["ownedFaces"]:
            fail(
                "static world command face total does not match ownership rows: "
                f"{static_world_summary['faceCount']} != {static_world_summary['ownedFaces']}"
            )
        if static_world_summary["duplicateFaces"] != 0:
            fail(f"static world face ownership contains duplicates: {static_world_summary['duplicateFaces']}")
        if static_world_summary["unownedWorldFaces"] != 0:
            fail(f"captured world faces are missing base-world ownership: {static_world_summary['unownedWorldFaces']}")
        if static_world_summary["orphanOwnedFaces"] != 0:
            fail(f"base-world ownership references uncaptured world faces: {static_world_summary['orphanOwnedFaces']}")
        if static_world_summary["unownedFrontOccluders"] != 0:
            fail(
                "front occluder candidates are missing base-world ownership: "
                f"{static_world_summary['unownedFrontOccluders']}"
            )
        if static_world_errors:
            fail(static_world_errors[0])
    print("staticWorldCommands:")
    if not static_world_commands and not static_world_face_ownership:
        print("  none")
    else:
        print(
            "  "
            + " ".join(
                f"{key}:{value}"
                for key, value in [
                    ("total", static_world_summary["total"]),
                    ("baseWorld", static_world_summary["baseWorld"]),
                    ("faceCount", static_world_summary["faceCount"]),
                    ("triangleCount", static_world_summary["triangleCount"]),
                    ("ownedFaces", static_world_summary["ownedFaces"]),
                    ("uniqueFaces", static_world_summary["uniqueFaces"]),
                    ("duplicateFaces", static_world_summary["duplicateFaces"]),
                    ("unownedWorldFaces", static_world_summary["unownedWorldFaces"]),
                    ("orphanOwnedFaces", static_world_summary["orphanOwnedFaces"]),
                    ("unownedFrontOccluders", static_world_summary["unownedFrontOccluders"]),
                ]
            )
        )
        print_counter("staticWorldCommandKinds:", static_world_kinds)
    material_summary, material_passes, material_kinds, material_errors = (
        summarize_static_world_material_triangles(
            static_world_material_triangles,
            static_world_summary["triangleCount"],
        )
    )
    if args.strict and static_world_material_path.exists():
        if material_summary["total"] != material_summary["expected"]:
            fail(
                "static world material triangle total differs from owned mesh: "
                f"{material_summary['total']} != {material_summary['expected']}"
            )
        if material_summary["duplicateTriangles"] != 0:
            fail(f"static world material capture contains duplicate triangles: {material_summary['duplicateTriangles']}")
        if material_summary["missingTriangles"] != 0:
            fail(f"static world material capture is missing triangles: {material_summary['missingTriangles']}")
        if material_summary["outOfRangeTriangles"] != 0:
            fail(f"static world material capture has out-of-range triangles: {material_summary['outOfRangeTriangles']}")
        if material_summary["unresolved"] != 0:
            fail(f"static world material capture has unresolved textures: {material_summary['unresolved']}")
        if material_errors:
            fail(material_errors[0])
    print("staticWorldMaterialPasses:")
    if not static_world_material_triangles:
        print("  none")
    else:
        print(
            "  "
            + " ".join(
                f"{key}:{value}"
                for key, value in [
                    ("total", material_summary["total"]),
                    ("expected", material_summary["expected"]),
                    ("uniqueTriangles", material_summary["uniqueTriangles"]),
                    ("duplicateTriangles", material_summary["duplicateTriangles"]),
                    ("missingTriangles", material_summary["missingTriangles"]),
                    ("outOfRangeTriangles", material_summary["outOfRangeTriangles"]),
                    ("unresolved", material_summary["unresolved"]),
                    ("translucent", material_summary["translucent"]),
                ]
            )
        )
        print_counter("staticWorldMaterialPassCounts:", material_passes)
        print_counter("staticWorldMaterialModelKinds:", material_kinds)
    resident_expected = parse_int(metadata.get("residentTriangles"), len(resident_material_families))
    resident_summary, resident_families, resident_kinds, resident_errors = (
        summarize_resident_material_families(resident_material_families, resident_expected)
    )
    if args.strict and resident_material_path.exists():
        if resident_summary["total"] != resident_summary["expected"]:
            fail(
                "resident material family total differs from resident triangles: "
                f"{resident_summary['total']} != {resident_summary['expected']}"
            )
        if resident_summary["duplicateRows"] != 0:
            fail(
                "resident material family capture contains duplicate rows: "
                f"{resident_summary['duplicateRows']}"
            )
        if resident_summary["missingTriangles"] != 0:
            fail(
                "resident material family capture is missing triangles: "
                f"{resident_summary['missingTriangles']}"
            )
        if resident_summary["invalidRows"] != 0 and resident_errors:
            fail(resident_errors[0])
    print("residentMaterialFamilies:")
    if not resident_material_families:
        print("  none")
    else:
        print(
            "  "
            + " ".join(
                f"{key}:{value}"
                for key, value in [
                    ("total", resident_summary["total"]),
                    ("expected", resident_summary["expected"]),
                    ("rows", resident_summary["rows"]),
                    ("uniqueRows", resident_summary["uniqueRows"]),
                    ("duplicateRows", resident_summary["duplicateRows"]),
                    ("missingTriangles", resident_summary["missingTriangles"]),
                    ("invalidRows", resident_summary["invalidRows"]),
                    ("contradictions", resident_summary["contradictions"]),
                    ("waterNonTerrain", resident_summary["waterNonTerrain"]),
                    ("semanticNonObject", resident_summary["semanticNonObject"]),
                    ("emissiveWithoutGlow", resident_summary["emissiveWithoutGlow"]),
                    ("unclassified", resident_summary["unclassified"]),
                ]
            )
        )
        print_counter("residentMaterialFamilyCounts:", resident_families)
        print_counter("residentMaterialModelKinds:", resident_kinds)
    static_range_summary = summarize_static_range_candidates(static_range_candidates)
    static_range_outliers = top_static_range_candidates(static_range_candidates, args.top)
    print("staticRangeCandidates:")
    if not static_range_candidates:
        print("  none")
    else:
        print(
            "  "
            + " ".join(
                f"{key}:{value}"
                for key, value in [
                    ("total", static_range_summary["total"]),
                    ("finalRanges", static_range_summary["finalRanges"]),
                    (
                        "worldSpriteCommandsAtOrders",
                        static_range_summary["worldSpriteCommandsAtOrders"],
                    ),
                    ("staticFaces", static_range_summary["staticFaces"]),
                    ("overlapFaces", static_range_summary["overlapFaces"]),
                    (
                        "overlapWorldSpriteCommands",
                        static_range_summary["overlapWorldSpriteCommands"],
                    ),
                    ("overlapTerrain", static_range_summary["overlapTerrainFaces"]),
                    ("overlapWall", static_range_summary["overlapWallFaces"]),
                    ("overlapRoof", static_range_summary["overlapRoofFaces"]),
                    ("overlapGameObject", static_range_summary["overlapGameObjectFaces"]),
                    ("overlapWallObject", static_range_summary["overlapWallObjectFaces"]),
                ]
            )
        )
    print("staticRangeOutliers:")
    if not static_range_outliers:
        print("  none")
    else:
        print(
            "  index\tminExclusiveOrder\tmaxExclusiveOrder\tworldSpriteCommandsAtOrder\tfinalRange"
            "\tstaticFaces\toverlapFaces\toverlapWorldSpriteCommands"
            "\toverlapTerrain\toverlapWall\toverlapRoof\toverlapGameObject\toverlapWallObject"
        )
        for row in static_range_outliers:
            print(
                "  "
                + "\t".join(
                    [
                        row.get("index", ""),
                        row.get("minExclusiveOrder", ""),
                        row.get("maxExclusiveOrder", ""),
                        row.get("worldSpriteCommandsAtOrder", ""),
                        row.get("finalRange", ""),
                        row.get("staticFaces", ""),
                        row.get("overlapFaces", ""),
                        row.get("overlapWorldSpriteCommands", ""),
                        row.get("overlapTerrainFaces", ""),
                        row.get("overlapWallFaces", ""),
                        row.get("overlapRoofFaces", ""),
                        row.get("overlapGameObjectFaces", ""),
                        row.get("overlapWallObjectFaces", ""),
                    ]
                )
            )
    front_occluder_summary = summarize_front_occluder_candidates(front_occluder_candidates)
    front_occluder_outliers = top_front_occluder_candidates(front_occluder_candidates, args.top)
    expected_front_occluders = (
        static_range_summary["overlapWallFaces"]
        + static_range_summary["overlapGameObjectFaces"]
        + static_range_summary["overlapWallObjectFaces"]
    )
    if args.strict:
        if front_occluder_summary["total"] != expected_front_occluders:
            fail(
                "front-occluder candidate total does not match front static overlap faces: "
                f"{front_occluder_summary['total']} != {expected_front_occluders}"
            )
        if (
            scene_command_summary["frontOccluderRange"] > 0
            and scene_command_summary["frontOccluderFaces"] != front_occluder_summary["total"]
        ):
            fail(
                "front-occluder scene command face ownership does not match candidate rows: "
                f"{scene_command_summary['frontOccluderFaces']} != {front_occluder_summary['total']}"
            )
    print("frontOccluderCandidates:")
    if not front_occluder_candidates:
        print("  none")
    else:
        print(
            "  "
            + " ".join(
                f"{key}:{value}"
                for key, value in [
                    ("total", front_occluder_summary["total"]),
                    ("expectedFromStaticOverlap", expected_front_occluders),
                    ("wall", front_occluder_summary["wall"]),
                    ("gameObject", front_occluder_summary["gameObject"]),
                    ("wallObject", front_occluder_summary["wallObject"]),
                    (
                        "overlapWorldSpriteCommands",
                        front_occluder_summary["overlapWorldSpriteCommands"],
                    ),
                ]
            )
        )
    print("frontOccluderOutliers:")
    if not front_occluder_outliers:
        print("  none")
    else:
        print(
            "  index\trangeIndex\tminExclusiveOrder\tmaxExclusiveOrder\tmodelKind"
            "\tmodelIndex\tfaceId\tlegacyDrawOrder\taverageDepth"
            "\toverlapWorldSpriteCommands\tbounds"
        )
        for row in front_occluder_outliers:
            print(
                "  "
                + "\t".join(
                    [
                        row.get("index", ""),
                        row.get("rangeIndex", ""),
                        row.get("minExclusiveOrder", ""),
                        row.get("maxExclusiveOrder", ""),
                        row.get("modelKind", ""),
                        row.get("modelIndex", ""),
                        row.get("faceId", ""),
                        row.get("legacyDrawOrder", ""),
                        row.get("averageDepth", ""),
                        row.get("overlapWorldSpriteCommands", ""),
                        row.get("bounds", ""),
                    ]
                )
            )
    print_counter("characterKinds:", character_kinds)
    print_counter("characterDiagnoses:", character_diagnoses)

    visibility_summary, suspicious_visibility_rows = summarize_entity_visibility(
        characters,
        depth_owned_entity_world_sprite_counts,
        args.top,
    )
    print("entityVisibilityHealth:")
    print(
        "  "
        + " ".join(
            f"{key}:{value}"
            for key, value in [
                ("projectedWithBody", visibility_summary["projectedWithBody"]),
                ("visible", visibility_summary["visible"]),
                ("expectedFullyOccluded", visibility_summary["expectedFullyOccluded"]),
                ("expectedOutOfFrame", visibility_summary["expectedOutOfFrame"]),
                ("notProjected", visibility_summary["notProjected"]),
                ("suspicious", visibility_summary["suspicious"]),
            ]
        )
    )
    print("suspiciousVisibility:")
    if not suspicious_visibility_rows:
        print("  none")
    else:
        print(
            "  kind\tdisplayName\tserverIndex\tspriteId\tdiagnosis"
            "\tbodyCommands\tbodySourceVisiblePixels\trestoreDepthEvaluations"
            "\trestoreDepthVisiblePixels\trestoreDepthOccludedPixels"
        )
        for row in suspicious_visibility_rows:
            print(
                "  "
                + "\t".join(
                    [
                        row.get("kind", ""),
                        row.get("displayName", ""),
                        row.get("serverIndex", ""),
                        row.get("spriteId", ""),
                        row.get("diagnosis", ""),
                        row.get("bodyCommands", ""),
                        row.get("bodySourceVisiblePixels", ""),
                        row.get("restoreDepthEvaluations", ""),
                        row.get("restoreDepthVisiblePixels", ""),
                        row.get("restoreDepthOccludedPixels", ""),
                    ]
                )
            )

    depth_summary, fully_occluded_rows = summarize_depth_evaluations(depth_evaluation_rows, args.top)
    anchor_match_modes = summarize_anchor_match_modes(depth_evaluation_rows)
    print("liveDepthEvaluations:")
    if not depth_evaluation_rows:
        print("  none")
    else:
        print(
            "  "
            + " ".join(
                f"{key}:{value}"
                for key, value in [
                    ("total", depth_summary["total"]),
                    ("fullyOccluded", depth_summary["fullyOccluded"]),
                    ("sourcePixels", depth_summary["sourcePixels"]),
                    ("visiblePixels", depth_summary["visiblePixels"]),
                    ("occludedPixels", depth_summary["occludedPixels"]),
                    ("clippedPixels", depth_summary["clippedPixels"]),
                    ("outOfBoundsPixels", depth_summary["outOfBoundsPixels"]),
                    ("terrain", depth_summary["terrainOccludedPixels"]),
                    ("wall", depth_summary["wallOccludedPixels"]),
                    ("roof", depth_summary["roofOccludedPixels"]),
                    ("gameObject", depth_summary["gameObjectOccludedPixels"]),
                    ("wallObject", depth_summary["wallObjectOccludedPixels"]),
                ]
            )
        )
    print_counter("anchorMatchModes:", anchor_match_modes)
    anchor_geometry = summarize_anchor_geometry(depth_evaluation_rows)
    anchor_geometry_context = summarize_anchor_geometry_context(
        depth_evaluation_rows,
        sprite_commands_by_depth_key,
    )
    anchor_geometry_outliers = summarize_anchor_geometry_outliers(
        depth_evaluation_rows,
        sprite_commands_by_depth_key,
        character_info_by_sprite,
        args.top,
    )
    print("anchorGeometryHealth:")
    if anchor_geometry["count"] <= 0:
        print("  none")
    else:
        print(
            "  "
            + " ".join(
                f"{key}:{value}"
                for key, value in [
                    ("count", anchor_geometry["count"]),
                    ("exactRect", anchor_geometry["exactRect"]),
                    ("maxAbsDeltaX", anchor_geometry["maxAbsDeltaX"]),
                    ("maxAbsDeltaY", anchor_geometry["maxAbsDeltaY"]),
                    ("maxAbsDeltaWidth", anchor_geometry["maxAbsDeltaWidth"]),
                    ("maxAbsDeltaHeight", anchor_geometry["maxAbsDeltaHeight"]),
                ]
            )
        )
        print(
            "  "
            + " ".join(
                f"{key}:{value}"
                for key, value in [
                    ("nonExact", anchor_geometry_context["nonExact"]),
                    ("sourceCrop", anchor_geometry_context["sourceCrop"]),
                    ("uncropped", anchor_geometry_context["uncropped"]),
                    ("mirrorX", anchor_geometry_context["mirrorX"]),
                    ("skewed", anchor_geometry_context["skewed"]),
                    ("missingCommand", anchor_geometry_context["missingCommand"]),
                ]
            )
        )
    print("anchorGeometryOutliers:")
    if not anchor_geometry_outliers:
        print("  none")
    else:
        print(
            "  deltaScore\tkind\tdisplayName\tserverIndex\tlegacySpriteId\tsequence"
            "\tanchorMatchMode\tanchorMatchScore\tcommandRect\tanchorRect\tdeltaRect"
            "\tsourceRect\tspriteSize\tsourceCrop\tmirrorX\ttopX16\tbottomX16"
        )
        for row in anchor_geometry_outliers:
            print(
                "  "
                + "\t".join(
                    [
                        str(row["deltaScore"]),
                        str(row["kind"]),
                        str(row["displayName"]),
                        str(row["serverIndex"]),
                        str(row["legacySpriteId"]),
                        str(row["sequence"]),
                        str(row["anchorMatchMode"]),
                        str(row["anchorMatchScore"]),
                        str(row["commandRect"]),
                        str(row["anchorRect"]),
                        str(row["deltaRect"]),
                        str(row["sourceRect"]),
                        str(row["spriteSize"]),
                        str(row["sourceCrop"]).lower(),
                        str(row["mirrorX"]),
                        str(row["topX16"]),
                        str(row["bottomX16"]),
                    ]
                )
            )
    print("fullyOccludedCommands:")
    if not fully_occluded_rows:
        print("  none")
    else:
        print(
            "  index\tsequence\tkind\tdisplayName\tserverIndex\tlegacySpriteId\tx\ty\twidth\theight"
            "\tanchorLegacyDrawOrder\tsourcePixels\toccludedPixels\tliveOccluderKinds"
            "\toccluderOrderRange\toccluderDepthRange\tdominantOccluder"
            "\tdominantOccluderPixels\tbounds"
        )
        for row in fully_occluded_rows:
            character = character_info_by_sprite.get(row.get("legacySpriteId", ""), {})
            print(
                "  "
                + "\t".join(
                    [
                        row.get("index", ""),
                        row.get("sequence", ""),
                        character.get("kind", ""),
                        character.get("displayName", ""),
                        character.get("serverIndex", ""),
                        row.get("legacySpriteId", ""),
                        row.get("x", ""),
                        row.get("y", ""),
                        row.get("width", ""),
                        row.get("height", ""),
                        row.get("anchorLegacyDrawOrder", ""),
                        row.get("sourcePixels", ""),
                        row.get("occludedPixels", ""),
                        live_occluder_kinds(row),
                        f"{row.get('minOccluderLegacyDrawOrder', '')}-{row.get('maxOccluderLegacyDrawOrder', '')}",
                        f"{row.get('minOccluderDepth', '')}-{row.get('maxOccluderDepth', '')}",
                        (
                            f"{row.get('dominantOccluderKind', '')}:"
                            f"face{row.get('dominantOccluderFaceId', '')}:"
                            f"model{row.get('dominantOccluderModelIndex', '')}:"
                            f"order{row.get('dominantOccluderLegacyDrawOrder', '')}:"
                            f"depth{row.get('dominantOccluderDepth', '')}"
                        ),
                        row.get("dominantOccluderPixels", ""),
                        row.get("bounds", ""),
                    ]
                )
            )

    fully_occluded_entities = summarize_fully_occluded_entities(
        fully_occluded_rows,
        character_info_by_sprite,
        args.top,
    )
    print("fullyOccludedEntities:")
    if not fully_occluded_entities:
        print("  none")
    else:
        print(
            "  kind\tdisplayName\tserverIndex\tlegacySpriteId\tcommands"
            "\tsourcePixels\toccludedPixels\tliveOccluderKinds\ttopDominantFace"
        )
        for row in fully_occluded_entities:
            print(
                "  "
                + "\t".join(
                    [
                        str(row["kind"]),
                        str(row["displayName"]),
                        str(row["serverIndex"]),
                        str(row["spriteId"]),
                        str(row["commands"]),
                        str(row["sourcePixels"]),
                        str(row["occludedPixels"]),
                        str(row["liveOccluderKinds"]),
                        str(row["topDominantFace"]),
                    ]
                )
            )

    pressure_rows = summarize_occlusion_pressure(world_faces, sprite_anchors, args.top)
    print("occlusionPressure:")
    if not pressure_rows:
        print("  none")
    else:
        print("  spriteId\tfaceId\tlegacyDrawOrder\tlaterOccluderFaces\tmaxOverlapPixels\tkinds\tbbox")
        for row in pressure_rows:
            print(
                "  "
                + "\t".join(
                    [
                        str(row["spriteId"]),
                        str(row["faceId"]),
                        str(row["legacyDrawOrder"]),
                        str(row["laterOccluderFaces"]),
                        str(row["maxOverlapPixels"]),
                        str(row["kinds"]),
                        str(row["bbox"]),
                    ]
                )
            )

    all_replay_rows = replay_entity_occlusion(
        world_faces,
        sprite_commands,
        sprite_anchors,
        restore_stats,
        depth_evaluations,
        depth_owned_world_sprite_keys,
        None,
    )
    replay_rows = all_replay_rows[: max(0, args.top)]
    print("occlusionReplay:")
    if not replay_rows:
        print("  none")
    else:
        print(
            "  spriteId\tsequence\tfaceId\tlegacyDrawOrder\tcandidateFaces\trasterFaces"
            "\toccludedPixels\tspritePixels\toccludedPct\tkinds"
            "\tdepthStatsSource\tdepthEvaluations\tdepthVisiblePixels\tdepthMisses"
            "\tdepthSourcePixels\tdepthOccludedPixels\tbbox"
        )
        for row in replay_rows:
            print(
                "  "
                + "\t".join(
                    [
                        str(row["spriteId"]),
                        str(row["sequence"]),
                        str(row["faceId"]),
                        str(row["legacyDrawOrder"]),
                        str(row["candidateFaces"]),
                        str(row["rasterFaces"]),
                        str(row["occludedPixels"]),
                        str(row["spritePixels"]),
                        f"{float(row['occludedPct']):.1f}",
                        str(row["kinds"]),
                        str(row["depthStatsSource"]),
                        str(row["depthEvaluations"]),
                        str(row["depthVisiblePixels"]),
                        str(row["depthMisses"]),
                        str(row["depthSourcePixels"]),
                        str(row["depthOccludedPixels"]),
                        str(row["bbox"]),
                    ]
                )
            )

    disagreement_rows = find_occlusion_disagreements(
        all_replay_rows,
        character_diagnoses_by_sprite,
        args.disagreement_threshold,
        args.top,
    )
    print("occlusionDisagreements:")
    if not disagreement_rows:
        print("  none")
    else:
        print(
            "  spriteId\tfaceId\toccludedPct\toccludedPixels\tspritePixels"
            "\tdepthEvaluations\tdepthVisiblePixels\tdepthMisses"
            "\tdepthSourcePixels\tdepthOccludedPixels\tkinds\tdiagnosis\treason"
        )
        for row in disagreement_rows:
            print(
                "  "
                + "\t".join(
                    [
                        str(row["spriteId"]),
                        str(row["faceId"]),
                        f"{float(row['occludedPct']):.1f}",
                        str(row["occludedPixels"]),
                        str(row["spritePixels"]),
                        str(row["depthEvaluations"]),
                        str(row["depthVisiblePixels"]),
                        str(row["depthMisses"]),
                        str(row["depthSourcePixels"]),
                        str(row["depthOccludedPixels"]),
                        str(row["kinds"]),
                        str(row["diagnosis"]),
                        str(row["reason"]),
                    ]
                )
            )

    if args.fail_on_suspicious_visibility and visibility_summary["suspicious"] > 0:
        fail(f"suspicious projected entity visibility rows: {visibility_summary['suspicious']}")


if __name__ == "__main__":
    main()
