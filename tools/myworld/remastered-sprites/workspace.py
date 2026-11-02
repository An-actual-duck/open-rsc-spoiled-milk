#!/usr/bin/env python3
"""Authoring tools for the curated remastered sprite workspace."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import re
import struct
import sys
from collections import Counter
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[3]
ASSETS = Path(os.environ.get(
    "REMASTERED_SPRITE_ASSETS_ROOT",
    ROOT / "dev" / "myworld" / "assets" / "authoring" / "remastered-sprites",
)).resolve()
ARCHIVE = ROOT / "Client_Base" / "Cache" / "video" / "Custom_Sprites.osar"
MANIFEST = ASSETS / "manifest.json"
RUNTIME = ROOT / "dev/myworld/assets/runtime/remastered-sprites"
JAVA_CATALOG = ROOT / "Client_Base/src/orsc/remastered/RemasteredSpriteCatalogData.java"
OUTPUT = ROOT / "output" / "art/remastered-sprites"
ALLOWED_CATEGORIES = {"npcs", "players", "equipment", "items", "textures", "ui", "static"}
ALLOWED_STATUSES = {"work", "ready", "retired"}
SLUG = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
ID_FILE = re.compile(r"^id-(\d+)\.png$", re.IGNORECASE)
BOBO_ENTITY_TARGETS = {
    (27, 44): ("sprite/player/body1", "confirmed"),
    (54, 71): ("sprite/player/legs1", "confirmed"),
    (108, 125): ("sprite/player/fbody1", "confirmed"),
    (864, 881): ("sprite/npc/demon", "confirmed"),
    (1674, 1691): ("sprite/equipment/hatchet", "provisional"),
}


class WorkspaceError(Exception):
    pass


def display_path(path: Path) -> str:
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


def read_null_string(data: bytes, pos: int) -> tuple[str, int]:
    end = data.find(b"\0", pos)
    if end < 0:
        raise WorkspaceError("unterminated string in Custom_Sprites.osar")
    return data[pos:end].decode("latin1"), end + 1


def load_archive() -> dict[tuple[str, str], list[dict[str, Any]]]:
    data = gzip.decompress(ARCHIVE.read_bytes())
    pos = 0
    subspace_count = data[pos]
    pos += 1
    entries: dict[tuple[str, str], list[dict[str, Any]]] = {}

    for _ in range(subspace_count):
        subspace, pos = read_null_string(data, pos)
        entry_count = struct.unpack_from(">H", data, pos)[0]
        pos += 2
        for _ in range(entry_count):
            entry, pos = read_null_string(data, pos)
            entry_type = data[pos]
            pos += 1
            if entry_type in {1, 2, 3}:
                pos += 1
            frame_count = data[pos]
            pos += 1
            palette_size = data[pos] + 1
            pos += 1 + palette_size * 3
            frames = []
            for frame_index in range(frame_count):
                width, height = struct.unpack_from(">HH", data, pos)
                pos += 4
                requires_shift = data[pos] == 1
                pos += 1
                shift_x, shift_y = struct.unpack_from(">hh", data, pos)
                pos += 4
                bound_width, bound_height = struct.unpack_from(">HH", data, pos)
                pos += 4
                pos += width * height
                frames.append({
                    "frame": frame_index,
                    "pixelWidth": width,
                    "pixelHeight": height,
                    "requiresShift": requires_shift,
                    "shiftX": shift_x,
                    "shiftY": shift_y,
                    "boundWidth": bound_width,
                    "boundHeight": bound_height,
                })
            entries[(subspace.lower(), entry.lower())] = frames

    if pos != len(data):
        raise WorkspaceError(f"archive parse ended at {pos}, expected {len(data)}")
    return entries


def png_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()[:24]
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise WorkspaceError(f"not a valid PNG header: {path.relative_to(ROOT)}")
    return struct.unpack(">II", data[16:24])


def descriptor_paths() -> list[Path]:
    return sorted(
        path for path in ASSETS.rglob("set.json")
        if "_templates" not in path.parts and "incoming" not in path.parts
    )


def load_descriptor(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise WorkspaceError(f"{path.relative_to(ROOT)}: {exc}") from exc
    if not isinstance(value, dict):
        raise WorkspaceError(f"{path.relative_to(ROOT)}: descriptor must be an object")
    return value


def canonical_frames(key_prefix: str, archive: dict[tuple[str, str], list[dict[str, Any]]]) -> list[dict[str, Any]]:
    parts = key_prefix.split("/")
    if len(parts) == 3 and parts[0] == "sprite":
        lookup = (parts[1].lower(), parts[2].lower())
    elif len(parts) == 2 and parts[0] == "texture":
        lookup = ("textures", parts[1].lower())
    else:
        raise WorkspaceError(f"invalid keyPrefix {key_prefix!r}")
    if lookup not in archive:
        raise WorkspaceError(f"unknown canonical target {key_prefix!r}")
    return archive[lookup]


def expand_target(target: dict[str, Any], set_dir: Path) -> list[tuple[int, str, Path]]:
    frames = target.get("frames")
    if not isinstance(frames, dict):
        raise WorkspaceError("target.frames must be an object")
    pattern = frames.get("pattern")
    first = frames.get("first")
    count = frames.get("count")
    if not isinstance(pattern, str) or not re.fullmatch(r"(?:frames|work)/[^/]*%02d[^/]*\.png", pattern):
        raise WorkspaceError("frames.pattern must be a local frames/ or work/ %02d PNG pattern")
    if not isinstance(first, int) or first < 0 or not isinstance(count, int) or not 1 <= count <= 255:
        raise WorkspaceError("frames.first/count must describe 1..255 non-negative frames")
    expanded = []
    for frame in range(first, first + count):
        try:
            relative = pattern % frame
        except (TypeError, ValueError) as exc:
            raise WorkspaceError(f"invalid frames.pattern {pattern!r}") from exc
        asset = set_dir / relative
        if asset.resolve().parent.parent != set_dir.resolve():
            raise WorkspaceError(f"frame escapes its set directory: {relative}")
        expanded.append((frame, relative, asset))
    return expanded


def validate_workspace() -> tuple[list[dict[str, Any]], list[str]]:
    archive = load_archive()
    errors: list[str] = []
    warnings: list[str] = []
    manifest_entries: list[dict[str, Any]] = []
    seen_set_ids: dict[str, Path] = {}
    seen_keys: dict[str, Path] = {}
    declared_ready_files: set[Path] = set()

    for path in descriptor_paths():
        relative = path.relative_to(ROOT)
        set_dir = path.parent
        try:
            descriptor = load_descriptor(path)
            category = set_dir.relative_to(ASSETS).parts[0]
            if category not in ALLOWED_CATEGORIES:
                raise WorkspaceError(f"set must be beneath a known category, found {category!r}")
            set_id = descriptor.get("setId")
            if not isinstance(set_id, str) or not SLUG.fullmatch(set_id):
                raise WorkspaceError("setId must be lowercase kebab-case")
            if set_id in seen_set_ids:
                raise WorkspaceError(f"duplicate setId also used by {seen_set_ids[set_id].relative_to(ROOT)}")
            seen_set_ids[set_id] = path
            if descriptor.get("schemaVersion") != 1:
                raise WorkspaceError("schemaVersion must be 1")
            if not isinstance(descriptor.get("displayName"), str) or not descriptor["displayName"].strip():
                raise WorkspaceError("displayName is required")
            status = descriptor.get("status")
            if status not in ALLOWED_STATUSES:
                raise WorkspaceError(f"status must be one of {sorted(ALLOWED_STATUSES)}")
            contributor = descriptor.get("contributor")
            if not isinstance(contributor, dict):
                raise WorkspaceError("contributor object is required")
            for field in ("id", "displayName", "provenance"):
                if not isinstance(contributor.get(field), str) or not contributor[field].strip():
                    raise WorkspaceError(f"contributor.{field} is required")
            if not SLUG.fullmatch(contributor["id"]):
                raise WorkspaceError("contributor.id must be lowercase kebab-case")
            targets = descriptor.get("targets")
            if not isinstance(targets, list) or not targets:
                raise WorkspaceError("at least one target is required")

            for target in targets:
                if not isinstance(target, dict):
                    raise WorkspaceError("each target must be an object")
                key_prefix = target.get("keyPrefix")
                if not isinstance(key_prefix, str) or key_prefix != key_prefix.lower():
                    raise WorkspaceError("keyPrefix must be a lowercase string")
                canonical = canonical_frames(key_prefix, archive)
                if target.get("sizePolicy") != "exact-canonical":
                    raise WorkspaceError("only sizePolicy exact-canonical is supported")
                if target.get("recolorPolicy") not in {"inherit", "none"}:
                    raise WorkspaceError("recolorPolicy must be inherit or none")
                threshold = target.get("alphaThreshold")
                if not isinstance(threshold, int) or not 1 <= threshold <= 255:
                    raise WorkspaceError("alphaThreshold must be 1..255")
                confidence = target.get("mappingConfidence")
                if confidence not in {"confirmed", "provisional"}:
                    raise WorkspaceError("mappingConfidence must be confirmed or provisional")
                if status == "ready" and confidence != "confirmed":
                    raise WorkspaceError("a ready set cannot have a provisional mapping")

                for frame, asset_relative, asset in expand_target(target, set_dir):
                    if frame >= len(canonical):
                        raise WorkspaceError(
                            f"frame {frame} exceeds {key_prefix} canonical count {len(canonical)}"
                        )
                    if status == "ready" and not asset_relative.startswith("frames/"):
                        raise WorkspaceError("ready targets must use frames/")
                    if status == "work" and not asset_relative.startswith("work/"):
                        raise WorkspaceError("work targets must use work/")
                    if status != "retired" and not asset.is_file():
                        raise WorkspaceError(f"declared PNG is missing: {asset.relative_to(ROOT)}")
                    if status == "retired":
                        continue
                    actual_size = png_size(asset)
                    expected = canonical[frame]
                    expected_size = (expected["pixelWidth"], expected["pixelHeight"])
                    if actual_size != expected_size:
                        message = (
                            f"{asset.relative_to(ROOT)} is {actual_size[0]}x{actual_size[1]}, "
                            f"canonical {key_prefix}/{frame} is {expected_size[0]}x{expected_size[1]}"
                        )
                        if status == "ready":
                            raise WorkspaceError(message)
                        warnings.append(message)
                    key = key_prefix if key_prefix.startswith("texture/") else f"{key_prefix}/{frame}"
                    if key in seen_keys:
                        raise WorkspaceError(f"duplicate target {key} also used by {seen_keys[key].relative_to(ROOT)}")
                    seen_keys[key] = path
                    if status == "ready":
                        declared_ready_files.add(asset.resolve())
                        manifest_entries.append({
                            "key": key,
                            "png": asset.relative_to(ASSETS).as_posix(),
                            "expectedCanonical": expected,
                            "sizePolicy": target["sizePolicy"],
                            "recolorPolicy": target["recolorPolicy"],
                            "alphaThreshold": threshold,
                            "setId": set_id,
                            "contributorId": contributor["id"],
                        })
        except WorkspaceError as exc:
            errors.append(f"{relative}: {exc}")

    for frame_file in sorted(ASSETS.glob("**/frames/*.png")):
        if frame_file.resolve() not in declared_ready_files:
            errors.append(f"{frame_file.relative_to(ROOT)}: orphan ready PNG")

    if errors:
        raise WorkspaceError("\n".join(errors))
    return sorted(manifest_entries, key=lambda entry: entry["key"]), sorted(set(warnings))


def manifest_document(entries: list[dict[str, Any]]) -> dict[str, Any]:
    signature_source = json.dumps(entries, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return {
        "schemaVersion": 1,
        "catalogRevision": hashlib.sha256(signature_source).hexdigest(),
        "entryCount": len(entries),
        "entries": entries,
    }


def rendered_manifest(entries: list[dict[str, Any]]) -> str:
    return json.dumps(manifest_document(entries), indent=2, sort_keys=True) + "\n"


def java_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=True)


def rendered_java_catalog(entries: list[dict[str, Any]]) -> str:
    document = manifest_document(entries)
    lines = [
        "package orsc.remastered;",
        "",
        "// Generated by tools/myworld/remastered-sprites/workspace.py. Do not edit by hand.",
        "final class RemasteredSpriteCatalogData {",
        f"\tstatic final String REVISION = {java_string(document['catalogRevision'])};",
        "\tstatic final Record[] ENTRIES = {",
    ]
    for entry in entries:
        expected = entry["expectedCanonical"]
        lines.append(
            "\t\tnew Record("
            + ", ".join([
                java_string(entry["key"]),
                java_string(entry["png"]),
                str(expected["pixelWidth"]),
                str(expected["pixelHeight"]),
                str(expected["requiresShift"]).lower(),
                str(expected["shiftX"]),
                str(expected["shiftY"]),
                str(expected["boundWidth"]),
                str(expected["boundHeight"]),
                java_string(entry["sizePolicy"]),
                java_string(entry["recolorPolicy"]),
                str(entry["alphaThreshold"]),
                java_string(entry["setId"]),
                java_string(entry["contributorId"]),
            ])
            + "),"
        )
    lines.extend([
        "\t};",
        "",
        "\tprivate RemasteredSpriteCatalogData() {",
        "\t}",
        "",
        "\tstatic final class Record {",
        "\t\tfinal String key;",
        "\t\tfinal String png;",
        "\t\tfinal int pixelWidth;",
        "\t\tfinal int pixelHeight;",
        "\t\tfinal boolean requiresShift;",
        "\t\tfinal int shiftX;",
        "\t\tfinal int shiftY;",
        "\t\tfinal int boundWidth;",
        "\t\tfinal int boundHeight;",
        "\t\tfinal String sizePolicy;",
        "\t\tfinal String recolorPolicy;",
        "\t\tfinal int alphaThreshold;",
        "\t\tfinal String setId;",
        "\t\tfinal String contributorId;",
        "",
        "\t\tRecord(String key, String png, int pixelWidth, int pixelHeight, boolean requiresShift,",
        "\t\t\t\tint shiftX, int shiftY, int boundWidth, int boundHeight, String sizePolicy,",
        "\t\t\t\tString recolorPolicy, int alphaThreshold, String setId, String contributorId) {",
        "\t\t\tthis.key = key;",
        "\t\t\tthis.png = png;",
        "\t\t\tthis.pixelWidth = pixelWidth;",
        "\t\t\tthis.pixelHeight = pixelHeight;",
        "\t\t\tthis.requiresShift = requiresShift;",
        "\t\t\tthis.shiftX = shiftX;",
        "\t\t\tthis.shiftY = shiftY;",
        "\t\t\tthis.boundWidth = boundWidth;",
        "\t\t\tthis.boundHeight = boundHeight;",
        "\t\t\tthis.sizePolicy = sizePolicy;",
        "\t\t\tthis.recolorPolicy = recolorPolicy;",
        "\t\t\tthis.alphaThreshold = alphaThreshold;",
        "\t\t\tthis.setId = setId;",
        "\t\t\tthis.contributorId = contributorId;",
        "\t\t}",
        "\t}",
        "}",
        "",
    ])
    return "\n".join(lines)


def command_validate(_: argparse.Namespace) -> int:
    entries, warnings = validate_workspace()
    for warning in warnings:
        print(f"WARN: {warning}")
    print(f"PASS: {len(descriptor_paths())} sets validated; {len(entries)} ready catalog entries")
    return 0


def command_generate(args: argparse.Namespace) -> int:
    entries, warnings = validate_workspace()
    expected_manifest = rendered_manifest(entries)
    expected_java = rendered_java_catalog(entries)
    for warning in warnings:
        print(f"WARN: {warning}")
    runtime_files = {"manifest.json": expected_manifest.encode("utf-8")}
    for entry in entries:
        runtime_files[entry["png"]] = (ASSETS / entry["png"]).read_bytes()
    # Authoring descriptors, incoming deliveries, work frames and templates never
    # enter the client bundle. Publish only the explicit ready catalog payload.
    canonical_authoring = ROOT / "dev/myworld/assets/authoring/remastered-sprites"
    publish_runtime = ASSETS == canonical_authoring
    if args.check:
        actual_manifest = MANIFEST.read_text(encoding="utf-8") if MANIFEST.exists() else ""
        actual_java = JAVA_CATALOG.read_text(encoding="utf-8") if JAVA_CATALOG.exists() else ""
        stale = []
        if actual_manifest != expected_manifest:
            stale.append(str(MANIFEST.relative_to(ROOT)))
        if actual_java != expected_java:
            stale.append(str(JAVA_CATALOG.relative_to(ROOT)))
        if publish_runtime:
            for relative, payload in runtime_files.items():
                path = RUNTIME / relative
                if not path.is_file() or path.read_bytes() != payload:
                    stale.append(str(path.relative_to(ROOT)))
            actual_pngs = {p.relative_to(RUNTIME).as_posix() for p in RUNTIME.rglob("*.png")}
            if actual_pngs != set(runtime_files) - {"manifest.json"}:
                stale.append("runtime remastered PNG inventory")
        if stale:
            raise WorkspaceError("generated outputs are stale: " + ", ".join(stale) + "; run generate")
        print(f"PASS: generated manifest and Java catalog are current ({len(entries)} entries)")
        return 0
    MANIFEST.write_text(expected_manifest, encoding="utf-8")
    JAVA_CATALOG.parent.mkdir(parents=True, exist_ok=True)
    JAVA_CATALOG.write_text(expected_java, encoding="utf-8")
    if publish_runtime:
        unexpected = {p.relative_to(RUNTIME).as_posix() for p in RUNTIME.rglob("*.png")} - set(runtime_files)
        if unexpected:
            raise WorkspaceError("retire unexpected runtime frames explicitly before generation: " + ", ".join(sorted(unexpected)))
        for relative, payload in runtime_files.items():
            path = RUNTIME / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(payload)
    print(f"WROTE: {MANIFEST.relative_to(ROOT)} and {JAVA_CATALOG.relative_to(ROOT)} ({len(entries)} entries)")
    return 0


def command_inventory(_: argparse.Namespace) -> int:
    counts = Counter()
    png_counts = Counter()
    for path in descriptor_paths():
        descriptor = load_descriptor(path)
        counts[descriptor.get("status", "invalid")] += 1
        png_counts[path.parent.relative_to(ASSETS).parts[0]] += len(list(path.parent.glob("frames/*.png")))
        png_counts[path.parent.relative_to(ASSETS).parts[0]] += len(list(path.parent.glob("work/*.png")))
    incoming = len(list((ASSETS / "incoming").glob("**/*.png")))
    legacy = ROOT / "dev" / "myworld" / "assets" / "runtime" / "bobo-resprites"
    legacy_count = len(list(legacy.glob("**/*.png"))) if legacy.exists() else 0
    print(f"sets={sum(counts.values())} ready={counts['ready']} work={counts['work']} retired={counts['retired']}")
    print("category_pngs=" + ", ".join(f"{name}:{png_counts[name]}" for name in sorted(png_counts)))
    print(f"incoming_pngs={incoming} legacy_bobo_pngs={legacy_count}")
    return 0


def consecutive_ranges(values: list[int]) -> list[tuple[int, int]]:
    if not values:
        return []
    ranges = []
    start = previous = values[0]
    for value in values[1:]:
        if value != previous + 1:
            ranges.append((start, previous))
            start = value
        previous = value
    ranges.append((start, previous))
    return ranges


def command_classify(args: argparse.Namespace) -> int:
    source = Path(args.path).resolve()
    try:
        source.relative_to(ROOT)
    except ValueError as exc:
        raise WorkspaceError("classification path must be inside the repository") from exc
    if not source.is_dir():
        raise WorkspaceError(f"not a directory: {source}")
    files = sorted(source.rglob("*.png"))
    identified: list[tuple[int, Path]] = []
    for path in files:
        match = ID_FILE.fullmatch(path.name)
        if match:
            identified.append((int(match.group(1)), path))
        else:
            print(f"UNCLASSIFIED {path.relative_to(ROOT)}: filename is not id-NNNNN.png")
    values = sorted(value for value, _ in identified)
    print(f"files={len(files)} numeric_ids={len(values)}")
    item_aliases = load_item_aliases()
    texture_defs = load_texture_definitions()
    for start, end in consecutive_ranges(values):
        known = BOBO_ENTITY_TARGETS.get((start, end))
        if known:
            print(f"ENTITY {start}-{end} -> {known[0]} ({known[1]}; review before moving)")
            continue
        for value in range(start, end + 1):
            if 2150 <= value < 2801:
                entry = value - 2150
                aliases = item_aliases.get(entry, [])
                alias_text = ", ".join(aliases) if aliases else "no current ItemDef aliases"
                print(
                    f"ITEM {value} -> sprite/items/{entry}/0 "
                    f"(logical mapping; choose family name; aliases: {alias_text})"
                )
            elif 3225 <= value < 3292:
                entry = value - 3225
                if entry < len(texture_defs):
                    data_name, animation_name = texture_defs[entry]
                    identity = data_name + (f" / {animation_name}" if animation_name else "")
                    state = f"active TextureDef: {identity}"
                else:
                    state = "archive-only/dormant; no active TextureDef"
                print(f"TEXTURE {value} -> texture/{entry} ({state}; stage until texture adapter)")
            else:
                print(f"AMBIGUOUS {value}: no safe offset adapter")
    return 0


def load_item_aliases() -> dict[int, list[str]]:
    source = (ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java").read_text(
        encoding="utf-8"
    )
    aliases: dict[int, set[str]] = {}
    pattern = re.compile(r'new ItemDef\("([^"]*)".*?,\s*\d+,\s*"items:(\d+)"')
    for line in source.splitlines():
        match = pattern.search(line)
        if match:
            aliases.setdefault(int(match.group(2)), set()).add(match.group(1))
    return {entry: sorted(names, key=str.casefold) for entry, names in aliases.items()}


def load_texture_definitions() -> list[tuple[str, str]]:
    source = (ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java").read_text(
        encoding="utf-8"
    )
    block_match = re.search(
        r"private static void loadTextureDefinitions\(\) \{(.*?)\n\t\}", source, re.DOTALL
    )
    if not block_match:
        raise WorkspaceError("could not locate loadTextureDefinitions")
    return re.findall(r'new TextureDef\("([^"]*)",\s*"([^"]*)"\)', block_match.group(1))


def command_scaffold(args: argparse.Namespace) -> int:
    if args.category not in ALLOWED_CATEGORIES:
        raise WorkspaceError(f"category must be one of {sorted(ALLOWED_CATEGORIES)}")
    parts = args.name.split("/")
    if any(not SLUG.fullmatch(part) for part in parts):
        raise WorkspaceError("set name must contain lowercase kebab-case path segments")
    destination = ASSETS / args.category
    for part in parts:
        destination /= part
    if destination.exists():
        raise WorkspaceError(f"refusing to overwrite {destination.relative_to(ROOT)}")
    destination.mkdir(parents=True)
    (destination / "frames").mkdir()
    (destination / "work").mkdir()
    (destination / "source").mkdir()
    template = json.loads((ASSETS / "_templates" / "set.json").read_text(encoding="utf-8"))
    set_id = "-".join([args.category.rstrip("s"), *parts])
    template["setId"] = set_id
    template["displayName"] = parts[-1].replace("-", " ").title()
    (destination / "set.json").write_text(json.dumps(template, indent=2) + "\n", encoding="utf-8")
    print(f"CREATED: {display_path(destination)}")
    return 0


def inventory_payload() -> dict[str, Any]:
    entries, warnings = validate_workspace()
    sets = []
    for path in descriptor_paths():
        descriptor = load_descriptor(path)
        sets.append({
            "path": path.parent.relative_to(ASSETS).as_posix(),
            "setId": descriptor["setId"],
            "status": descriptor["status"],
            "pngCount": len(list(path.parent.glob("frames/*.png"))) + len(list(path.parent.glob("work/*.png"))),
        })
    return {"sets": sets, "readyEntries": len(entries), "warnings": warnings}


def command_report(_: argparse.Namespace) -> int:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    path = OUTPUT / "workspace-report.json"
    path.write_text(json.dumps(inventory_payload(), indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"WROTE: {path.relative_to(ROOT)}")
    return 0


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    commands = result.add_subparsers(dest="command", required=True)
    commands.add_parser("inventory").set_defaults(function=command_inventory)
    classify = commands.add_parser("classify")
    classify.add_argument("path")
    classify.set_defaults(function=command_classify)
    scaffold = commands.add_parser("scaffold")
    scaffold.add_argument("category")
    scaffold.add_argument("name")
    scaffold.set_defaults(function=command_scaffold)
    commands.add_parser("validate").set_defaults(function=command_validate)
    generate = commands.add_parser("generate")
    generate.add_argument("--check", action="store_true")
    generate.set_defaults(function=command_generate)
    commands.add_parser("report").set_defaults(function=command_report)
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        return args.function(args)
    except WorkspaceError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
