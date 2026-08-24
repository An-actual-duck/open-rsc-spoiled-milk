#!/usr/bin/env python3
"""Export Core's final client item-to-visual mapping for neutral consumers."""

from __future__ import annotations

import argparse
import base64
import gzip
import hashlib
import json
import struct
import subprocess
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
TOOL_DIR = Path(__file__).resolve().parent
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
CUSTOM_ARCHIVE = ROOT / "Client_Base/Cache/video/Custom_Sprites.osar"
AUTHENTIC_ARCHIVE = ROOT / "Client_Base/Cache/video/Authentic_Sprites.orsc"
DEFAULT_FULL_OUTPUT = TOOL_DIR / "generated/item-visuals-full-v1.json"
DEFAULT_COMPAT_OUTPUT = TOOL_DIR / "generated/item-visuals-3309-3317-v1.json"
COMPATIBILITY_IDS = tuple(range(3309, 3318))
SUPPORTED_OSAR_TYPES_WITH_LAYER = {1, 2, 3}
EXTERNAL_PREFIX = "external-png:"
EXTERNAL_ASSET_DIRS = (
    "dev/myworld/assets/sprites/items/inventory-ground/agility-pouches",
    "dev/myworld/assets/sprites/items/inventory-ground/tools",
    "dev/myworld/assets/sprites/items/inventory-ground/weapons",
    "dev/myworld/assets/sprites/items/inventory-ground/resources/sigils",
    "dev/myworld/assets/sprites/items/inventory-ground/resources",
    "dev/myworld/assets/sprites/items/inventory-ground",
    "output/pngs",
)
PROVIDER_INPUTS = (
    "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java",
    "Client_Base/src/com/openrsc/client/entityhandling/MyWorldItemOverrides.java",
    "Client_Base/src/com/openrsc/client/entityhandling/defs/ItemDef.java",
    "Client_Base/src/orsc/Config.java",
)


class ExportError(RuntimeError):
    """An actionable provider-contract validation failure."""


@dataclass(frozen=True)
class FinalItem:
    item_id: int
    diagnostic_name: str | None
    sprite_location: str | None
    sprite_id: int
    picture_mask: int
    blue_mask: int


@dataclass(frozen=True)
class ExternalSpec:
    specification: str
    asset_name: str
    file_name: str
    target_width: int
    target_height: int


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def canonical_hash(value: object) -> str:
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return sha256_bytes(encoded.encode("utf-8"))


def repository_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT.resolve()).as_posix()


def require_file(path: Path, purpose: str) -> None:
    if not path.is_file():
        raise ExportError(f"Missing {purpose}: {path}")


def decode_probe_text(value: str) -> str | None:
    if value == "~":
        return None
    try:
        return base64.b64decode(value, validate=True).decode("utf-8")
    except (ValueError, UnicodeDecodeError) as failure:
        raise ExportError(f"Final-item probe emitted malformed text {value!r}: {failure}") from failure


def build_client() -> None:
    subprocess.run([str(ROOT / "scripts/build-client.sh")], cwd=ROOT, check=True)


def extract_final_items() -> tuple[list[FinalItem], int]:
    require_file(CLIENT_JAR, "compiled client jar; run scripts/build-client.sh")
    probe_source = TOOL_DIR / "FinalItemDefinitionsProbe.java"
    require_file(probe_source, "final item definition probe")
    with tempfile.TemporaryDirectory(prefix="core-item-visual-provider-") as temporary:
        subprocess.run(
            ["javac", "-cp", str(CLIENT_JAR), "-d", temporary, str(probe_source)],
            cwd=ROOT,
            check=True,
        )
        result = subprocess.run(
            ["java", "-cp", f"{temporary}:{CLIENT_JAR}", "FinalItemDefinitionsProbe"],
            cwd=ROOT / "Client_Base",
            capture_output=True,
            text=True,
        )
    if result.returncode != 0:
        raise ExportError(
            "Final client item-definition probe failed. "
            f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}"
        )

    expected_count: int | None = None
    authentic_item_base: int | None = None
    items: list[FinalItem] = []
    for line in result.stdout.splitlines():
        fields = line.split("\t")
        if fields[0] == "AUTHENTIC_ITEM_BASE" and len(fields) == 2:
            authentic_item_base = int(fields[1])
        elif fields[0] == "CATALOG" and len(fields) == 2:
            expected_count = int(fields[1])
        elif fields[0] == "ITEM" and len(fields) == 8:
            index, item_id = int(fields[1]), int(fields[2])
            if index != len(items) or item_id != index:
                raise ExportError(
                    f"Final client item catalog lost stable ID ordering at index {index}: item.id={item_id}"
                )
            items.append(
                FinalItem(
                    item_id=item_id,
                    diagnostic_name=decode_probe_text(fields[3]),
                    sprite_location=decode_probe_text(fields[4]),
                    sprite_id=int(fields[5]),
                    picture_mask=int(fields[6]),
                    blue_mask=int(fields[7]),
                )
            )
    if expected_count is None:
        raise ExportError("Final client item-definition probe did not report a catalog size")
    if authentic_item_base is None or authentic_item_base < 0:
        raise ExportError("Final client item-definition probe did not report a valid authentic item base")
    if len(items) != expected_count:
        raise ExportError(f"Final client item-definition probe reported {expected_count} items but emitted {len(items)}")
    return items, authentic_item_base


def read_null_string(data: bytes, position: int, context: str) -> tuple[str, int]:
    end = data.find(b"\0", position)
    if end < 0:
        raise ExportError(f"Unterminated string while parsing {context} at byte {position}")
    return data[position:end].decode("latin1"), end + 1


def require_bytes(data: bytes, position: int, count: int, context: str) -> None:
    if position < 0 or count < 0 or position + count > len(data):
        raise ExportError(
            f"Truncated {context}: needs bytes {position}..{position + count}, archive size is {len(data)}"
        )


def load_custom_archive_entries(path: Path) -> dict[tuple[str, str], str]:
    require_file(path, "custom sprite archive")
    try:
        data = gzip.decompress(path.read_bytes())
    except (OSError, EOFError) as failure:
        raise ExportError(f"Cannot decompress custom sprite archive {path}: {failure}") from failure
    require_bytes(data, 0, 1, "custom sprite archive header")
    position = 0
    subspace_count = data[position]
    position += 1
    entries: dict[tuple[str, str], str] = {}
    for subspace_index in range(subspace_count):
        subspace, position = read_null_string(data, position, f"custom sprite subspace {subspace_index}")
        require_bytes(data, position, 2, f"entry count for custom sprite subspace {subspace!r}")
        entry_count = struct.unpack_from(">H", data, position)[0]
        position += 2
        for entry_index in range(entry_count):
            entry_start = position
            entry, position = read_null_string(
                data, position, f"custom sprite entry {entry_index} in {subspace!r}"
            )
            require_bytes(data, position, 1, f"type for custom sprite {subspace}:{entry}")
            entry_type = data[position]
            position += 1
            if entry_type in SUPPORTED_OSAR_TYPES_WITH_LAYER:
                require_bytes(data, position, 1, f"layer for custom sprite {subspace}:{entry}")
                position += 1
            require_bytes(data, position, 2, f"frame and color counts for custom sprite {subspace}:{entry}")
            frame_count = data[position]
            position += 1
            color_table_size = data[position] + 1
            position += 1
            require_bytes(data, position, color_table_size * 3, f"color table for custom sprite {subspace}:{entry}")
            position += color_table_size * 3
            for frame_index in range(frame_count):
                require_bytes(data, position, 4, f"frame {frame_index} dimensions for {subspace}:{entry}")
                width, height = struct.unpack_from(">HH", data, position)
                position += 4
                require_bytes(data, position, 9, f"frame {frame_index} metadata for {subspace}:{entry}")
                position += 9
                require_bytes(data, position, width * height, f"frame {frame_index} pixels for {subspace}:{entry}")
                position += width * height
            key = (subspace, entry)
            if key in entries:
                raise ExportError(f"Duplicate custom sprite archive entry {subspace}:{entry}")
            entries[key] = sha256_bytes(data[entry_start:position])
    if position != len(data):
        raise ExportError(
            f"Custom sprite archive parse ended at byte {position}, but decompressed size is {len(data)}"
        )
    return entries


def load_authentic_archive_entries(path: Path) -> dict[int, str]:
    require_file(path, "authentic sprite archive")
    entries: dict[int, str] = {}
    try:
        with zipfile.ZipFile(path) as archive:
            corrupt = archive.testzip()
            if corrupt is not None:
                raise ExportError(f"Authentic sprite archive contains a corrupt entry: {corrupt}")
            for name in archive.namelist():
                if name.isdigit():
                    entries[int(name)] = sha256_bytes(archive.read(name))
    except zipfile.BadZipFile as failure:
        raise ExportError(f"Cannot read authentic sprite archive {path}: {failure}") from failure
    return entries


def parse_external_spec(sprite_location: str) -> ExternalSpec | None:
    if not sprite_location.startswith(EXTERNAL_PREFIX):
        return None
    specification = sprite_location[len(EXTERNAL_PREFIX):]
    if not specification or "/" in specification or "\\" in specification:
        return None
    asset_name = specification
    target_width, target_height = 46, 30
    size_index = specification.find("@")
    if size_index > 0:
        asset_name = specification[:size_index]
        dimensions = specification[size_index + 1 :].split("x")
        if len(dimensions) == 2:
            try:
                target_width = max(1, min(46, int(dimensions[0])))
                target_height = max(1, min(30, int(dimensions[1])))
            except ValueError:
                target_width, target_height = 46, 30
    if not asset_name:
        return None
    file_name = asset_name if asset_name.endswith(".png") else asset_name + ".png"
    return ExternalSpec(specification, asset_name, file_name, target_width, target_height)


def resolve_external_asset(spec: ExternalSpec, packaged_entries: dict[str, str]) -> dict[str, object]:
    source: Path | None = None
    for relative_directory in EXTERNAL_ASSET_DIRS:
        candidate = ROOT / relative_directory / spec.file_name
        if candidate.is_file():
            source = candidate
            break
    if source is None:
        searched = ", ".join(str(ROOT / directory / spec.file_name) for directory in EXTERNAL_ASSET_DIRS)
        raise ExportError(
            f"External PNG specification {spec.specification!r} has no source asset. Searched: {searched}"
        )
    try:
        asset_relative = source.relative_to(ROOT / "dev/myworld/assets")
    except ValueError as failure:
        raise ExportError(
            f"External PNG {spec.specification!r} resolves to {source}, which is not in the client-packaged "
            "dev/myworld/assets tree"
        ) from failure
    packaged_resource = "myworld-assets/" + asset_relative.as_posix()
    source_hash = sha256_file(source)
    packaged_hash = packaged_entries.get(packaged_resource)
    if packaged_hash is None:
        raise ExportError(
            f"External PNG {spec.specification!r} resolves to {repository_path(source)}, but compiled client "
            f"{repository_path(CLIENT_JAR)} does not contain {packaged_resource}; rebuild or correct packaging"
        )
    if packaged_hash != source_hash:
        raise ExportError(
            f"Packaged external PNG {packaged_resource} does not match {repository_path(source)} "
            f"(source sha256={source_hash}, packaged sha256={packaged_hash})"
        )
    return {
        "specification": spec.specification,
        "assetName": spec.asset_name,
        "targetWidth": spec.target_width,
        "targetHeight": spec.target_height,
        "sourcePath": repository_path(source),
        "packagedResource": packaged_resource,
        "sha256": source_hash,
    }


def load_packaged_assets(path: Path) -> dict[str, str]:
    require_file(path, "compiled client jar for packaged external-asset validation")
    try:
        with zipfile.ZipFile(path) as archive:
            return {
                name: sha256_bytes(archive.read(name))
                for name in archive.namelist()
                if name.startswith("myworld-assets/") and name.endswith(".png")
            }
    except zipfile.BadZipFile as failure:
        raise ExportError(f"Cannot read compiled client jar {path}: {failure}") from failure


def provider_inputs() -> list[dict[str, str]]:
    values: list[dict[str, str]] = []
    for relative in PROVIDER_INPUTS:
        path = ROOT / relative
        require_file(path, "provider input")
        values.append({"path": relative, "sha256": sha256_file(path)})
    return values


def definition_catalog_identity(items: Iterable[FinalItem]) -> str:
    return canonical_hash(
        [
            {
                "itemId": item.item_id,
                "diagnosticName": item.diagnostic_name,
                "spriteLocation": item.sprite_location,
                "spriteId": item.sprite_id,
                "pictureMask": item.picture_mask,
                "blueMask": item.blue_mask,
            }
            for item in items
        ]
    )


def map_item(
    item: FinalItem,
    custom_entries: dict[tuple[str, str], str],
    authentic_entries: dict[int, str],
    packaged_entries: dict[str, str],
    authentic_item_base: int,
) -> dict[str, object]:
    authentic_id = authentic_item_base + item.sprite_id
    authentic = None
    if item.sprite_id >= 0 and authentic_id in authentic_entries:
        authentic = {
            "archiveId": authentic_id,
            "entrySha256": authentic_entries[authentic_id],
        }

    custom = None
    external = None
    role: str | None = None
    location = item.sprite_location or ""
    external_spec = parse_external_spec(location)
    if location.startswith(EXTERNAL_PREFIX) and external_spec is None:
        raise ExportError(
            f"Item {item.item_id} ({item.diagnostic_name!r}) has malformed external PNG "
            f"spriteLocation {location!r}; expected external-png:<asset> or external-png:<asset>@<width>x<height>"
        )
    if external_spec is not None:
        external = resolve_external_asset(external_spec, packaged_entries)
        role = "external-png"
    elif not location.startswith(EXTERNAL_PREFIX):
        parts = location.split(":")
        if len(parts) >= 2:
            key = (parts[0], parts[1])
            entry_hash = custom_entries.get(key)
            if entry_hash is not None:
                custom = {
                    "subspace": key[0],
                    "entry": key[1],
                    "baseArchiveEntrySha256": entry_hash,
                    "spritepackOverrideKey": f"{key[0]}:{key[1]}",
                }
                role = "custom-sprite-archive"

    if role is None and authentic is not None:
        role = "authentic-archive-fallback"
    if role is None:
        details = []
        if location.startswith(EXTERNAL_PREFIX):
            details.append(f"invalid external PNG specification {location!r}")
        elif location:
            details.append(f"missing custom archive entry for {location!r}")
        else:
            details.append("empty spriteLocation")
        details.append(f"no authentic entry for spriteId {item.sprite_id} (archive ID {authentic_id})")
        raise ExportError(
            f"Item {item.item_id} ({item.diagnostic_name!r}) has no resolvable base visual: " + "; ".join(details)
        )

    return {
        "itemId": item.item_id,
        "diagnosticName": item.diagnostic_name,
        "spriteLocation": item.sprite_location,
        "spriteId": item.sprite_id,
        "resolvedBaseSourceRole": role,
        "authenticArchive": authentic,
        "customOrSpritepack": custom,
        "externalPng": external,
        "pictureMask": item.picture_mask,
        "blueMask": item.blue_mask,
    }


def build_manifest(
    all_items: list[FinalItem],
    selected_ids: Iterable[int] | None,
    custom_entries: dict[tuple[str, str], str],
    authentic_entries: dict[int, str],
    packaged_entries: dict[str, str],
    inputs: list[dict[str, str]],
    authentic_item_base: int,
) -> dict[str, object]:
    catalog_by_id = {item.item_id: item for item in all_items}
    if selected_ids is None:
        selected = list(all_items)
        selection_kind = "complete-final-catalog"
    else:
        requested = sorted(set(selected_ids))
        missing = [item_id for item_id in requested if item_id not in catalog_by_id]
        if missing:
            raise ExportError(f"Requested item IDs are absent from the final client catalog: {missing}")
        selected = [catalog_by_id[item_id] for item_id in requested]
        selection_kind = "filtered-compatibility"
    mapped = [
        map_item(item, custom_entries, authentic_entries, packaged_entries, authentic_item_base)
        for item in selected
    ]
    selection: dict[str, object] = {
        "kind": selection_kind,
        "itemCount": len(mapped),
        "itemIdsSha256": canonical_hash([entry["itemId"] for entry in mapped]),
        "mappingSha256": canonical_hash(mapped),
    }
    if mapped:
        selection["minimumItemId"] = mapped[0]["itemId"]
        selection["maximumItemId"] = mapped[-1]["itemId"]
    return {
        "schemaVersion": 1,
        "manifestType": "world-builder-item-visual-mapping",
        "provider": {
            "identity": "spoiled-milk-core-final-client-item-definitions",
            "definitionMode": "members-enabled-post-override",
            "catalogItemCount": len(all_items),
            "catalogSha256": definition_catalog_identity(all_items),
            "inputs": inputs,
        },
        "assetProviders": {
            "customSpriteArchive": {
                "path": repository_path(CUSTOM_ARCHIVE),
                "sha256": sha256_file(CUSTOM_ARCHIVE),
                "entryCount": len(custom_entries),
            },
            "authenticSpriteArchive": {
                "path": repository_path(AUTHENTIC_ARCHIVE),
                "sha256": sha256_file(AUTHENTIC_ARCHIVE),
                "numericEntryCount": len(authentic_entries),
                "itemArchiveBaseId": authentic_item_base,
            },
            "externalPngPackaging": {
                "clientJarPath": repository_path(CLIENT_JAR),
                "packagedPngCount": len(packaged_entries),
                "resourceRoot": "myworld-assets/",
            },
        },
        "selection": selection,
        "itemVisuals": mapped,
    }


def write_manifest(path: Path, manifest: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    path.write_text(payload, encoding="utf-8")


def export(full_output: Path, compatibility_output: Path, skip_client_build: bool) -> tuple[int, int]:
    if not skip_client_build:
        build_client()
    all_items, authentic_item_base = extract_final_items()
    custom_entries = load_custom_archive_entries(CUSTOM_ARCHIVE)
    authentic_entries = load_authentic_archive_entries(AUTHENTIC_ARCHIVE)
    packaged_entries = load_packaged_assets(CLIENT_JAR)
    inputs = provider_inputs()
    full = build_manifest(
        all_items, None, custom_entries, authentic_entries, packaged_entries, inputs,
        authentic_item_base,
    )
    compatibility = build_manifest(
        all_items, COMPATIBILITY_IDS, custom_entries, authentic_entries, packaged_entries, inputs,
        authentic_item_base,
    )
    write_manifest(full_output, full)
    write_manifest(compatibility_output, compatibility)
    return len(full["itemVisuals"]), len(compatibility["itemVisuals"])


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--full-output", type=Path, default=DEFAULT_FULL_OUTPUT)
    parser.add_argument("--compatibility-output", type=Path, default=DEFAULT_COMPAT_OUTPUT)
    parser.add_argument(
        "--skip-client-build",
        action="store_true",
        help="Use the existing compiled client jar (asset packaging is still validated).",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        full_count, compatibility_count = export(
            args.full_output.resolve(),
            args.compatibility_output.resolve(),
            args.skip_client_build,
        )
    except (ExportError, subprocess.CalledProcessError) as failure:
        print(f"FAIL: {failure}", file=sys.stderr)
        return 1
    print(f"Wrote {full_count} final item visuals to {args.full_output}")
    print(f"Wrote {compatibility_count} compatibility item visuals to {args.compatibility_output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
