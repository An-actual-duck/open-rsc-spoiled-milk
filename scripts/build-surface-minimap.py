#!/usr/bin/env python3
"""Bake the installed world into level-specific minimap atlases."""

import hashlib
import json
import struct
import sys
import xml.etree.ElementTree as ET
import zlib
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "dev/myworld/assets/runtime/ui/minimap"
AUTHORING = ROOT / "dev/myworld/assets/authoring/ui/minimap"
LANDMARKS = AUTHORING / "landmarks.json"
CATALOG = AUTHORING / "landmark-catalog.json"
SECTOR = 48
PIXELS = SECTOR * 2
TRANSPARENT = 12345678  # Scene.TRANSPARENT
MARKER_SPACING = 4  # tiles at the minimap's fixed zoom

# Index zero is uncharted terrain. The last color is drawn over tile boundaries.
PALETTE = (
    0x17252C, 0x536F45, 0x61794A, 0x6F8052, 0x7A8559,
    0x387289, 0x488799, 0xAE9780, 0x958269, 0x826F60,
    0x978D72, 0x384144,
)
WALL = len(PALETTE) - 1


def read_verified(package, record):
    data = (package / record["path"]).read_bytes()
    if hashlib.sha256(data).hexdigest() != record["sha256"]:
        raise ValueError("Package checksum mismatch for " + record["path"])
    return data


def floor_color(elevation, texture, overlay, roof, overlay_types, surface):
    if overlay == 8:
        return 0
    kind = overlay_types.get(overlay, 0)
    if kind in (3, 4):
        return 6 if kind == 4 else 5
    if roof:
        return 9
    if kind == 2:
        return 7
    if kind == 1:
        return 8
    if overlay:
        return 10
    if texture == 0 and elevation == 0:
        return 5 if surface else 0
    return 1 + min(3, elevation // 45)


def visible_wall_ids(definitions):
    visible = set()
    for raw_id, wall in enumerate(definitions, 1):
        # Match visibility for static walls in the terrain renderer.
        if int(wall.findtext("unknown")) != 0:
            continue
        materials = [int(wall.findtext("modelVar2")), int(wall.findtext("modelVar3"))]
        if int(wall.findtext("wallLowerHeight", "0")) > 0:
            materials.extend((int(wall.findtext("wallUpperFront")), int(wall.findtext("wallUpperBack"))))
        if any(material != TRANSPARENT for material in materials):
            visible.add(raw_id)
    return visible


def bake_sector(raw, encoding, overlay_types, visible_walls, surface):
    if encoding == "raw-layered-sector-v1":
        tile = struct.Struct(">6BI")
    elif encoding == "raw-layered-sector-v2-u16":
        tile = struct.Struct(">H5BI")
    else:
        raise ValueError("Unsupported terrain encoding " + encoding)
    if len(raw) != SECTOR * SECTOR * tile.size:
        raise ValueError("Invalid terrain sector size")
    pixels = bytearray(PIXELS * PIXELS)
    for offset, values in enumerate(tile.iter_unpack(raw)):
        elevation, texture, overlay, roof, vertical, horizontal, diagonal = values
        x, y = divmod(offset, SECTOR)
        top = (y * 2) * PIXELS + (SECTOR - 1 - x) * 2
        color = floor_color(elevation, texture, overlay, roof, overlay_types, surface)
        pixels[top] = color
        pixels[top + 1] = color
        pixels[top + PIXELS] = color
        pixels[top + PIXELS + 1] = color
        # Match the terrain model's north edge (x + 1) and east edge (y + 1).
        if vertical in visible_walls:
            pixels[top] = pixels[top + 1] = WALL
        if horizontal in visible_walls:
            pixels[top + 1] = pixels[top + PIXELS + 1] = WALL
        if 0 < diagonal < 12000 and diagonal in visible_walls:
            pixels[top] = pixels[top + PIXELS + 1] = WALL
        elif 12000 < diagonal < 24000 and diagonal - 12000 in visible_walls:
            pixels[top + 1] = pixels[top + PIXELS] = WALL
    return zlib.compress(pixels, 9) if any(pixels) else b""


def collect_landmarks(placements, featured, catalog):
    anchors = defaultdict(list)
    for source, collection, catalog_id, position in (
        ("npc", "npcs", "npcId", "start"),
        ("scenery", "scenery", "sceneryId", "position"),
    ):
        for item in placements[collection]:
            point = item[position]
            anchors[source, item[catalog_id]].append((point["x"], point["y"]))

    for point in featured:
        if (point["x"], point["y"]) not in anchors[point["source"], point["id"]]:
            raise ValueError(f"Missing landmark anchor: {point['label']}")

    landmarks = list(featured)
    for source, definitions in (("npc", catalog["shops"]), ("scenery", catalog["workstations"])):
        for raw_id, (icon, label) in definitions.items():
            for x, y in sorted(anchors[source, int(raw_id)]):
                # Keep distinct vendors and group repeat stations at the same facility.
                distance = 0 if source == "npc" else (7 if icon == "WATER" else 5)
                if any(point["icon"] == icon and max(abs(x - point["x"]), abs(y - point["y"])) <= distance
                       for point in landmarks):
                    continue
                landmarks.append({"icon": icon, "label": label, "x": x, "y": y})
    return landmarks


def bake_landmarks(landmarks):
    offsets = sorted(
        ((dx, dy) for dx in range(-6, 7) for dy in range(-6, 7) if dx * dx + dy * dy <= 36),
        key=lambda shift: (shift[0] ** 2 + shift[1] ** 2, shift[0] != 0 and shift[1] != 0, shift),
    )
    occupied = []
    blocks = []
    for point in landmarks:
        x, y = point["x"], point["y"]
        for dx, dy in offsets:
            if all(max(abs(x + dx - other_x), abs(y + dy - other_y)) >= MARKER_SPACING
                   for other_x, other_y in occupied):
                break
        else:
            raise ValueError(f"Landmarks too close: {point['label']}")
        occupied.append((x + dx, y + dy))
        icon = point["icon"].encode("ascii")
        label = point["label"].encode("utf-8")
        blocks.append(struct.pack(">hhbbBB", x, y, dx, dy, len(icon), len(label)) + icon + label)
    return blocks


def atlas_name(level):
    return "surface.bin" if level == 0 else f"level-{level}.bin"


def bake(package):
    manifest = (package / "manifest.json").read_bytes()
    world = json.loads(manifest)
    if world["storage"]["sectorSize"] != SECTOR:
        raise ValueError("Unexpected map sector size")
    definitions = ET.parse(ROOT / "server/conf/server/defs/TileDef.xml").getroot()
    overlay_types = {i: int(tile.findtext("unknown")) for i, tile in enumerate(definitions, 1)}
    boundaries = ET.parse(ROOT / "server/conf/server/defs/DoorDef.xml").getroot()
    visible_walls = visible_wall_ids(boundaries)

    sectors_by_level = defaultdict(list)
    for record in world["terrainSectors"]:
        if record["worldSpace"] == "global":
            sectors_by_level[record["level"]].append(record)
    placements_by_level = {
        record["level"]: json.loads(read_verified(package, record))
        for record in world["placementSets"] if record["worldSpace"] == "global"
    }

    catalog = json.loads(CATALOG.read_text())
    featured = defaultdict(list)
    for town in json.loads(LANDMARKS.read_text()).values():
        featured[0].extend(town)
    for point in catalog["placements"]:
        featured[point["level"]].append(point)
    if not featured.keys() <= sectors_by_level.keys():
        raise ValueError("Landmark level does not exist in the installed terrain")

    palette = b"".join(struct.pack(">I", color) for color in PALETTE)
    manifest_header = b"RMAP\x03" + hashlib.sha256(manifest).digest()
    for level, records in sorted(sectors_by_level.items()):
        placements = placements_by_level.get(level, {"npcs": [], "scenery": []})
        markers = bake_landmarks(collect_landmarks(placements, featured[level], catalog))
        blocks = []
        for record in sorted(records, key=lambda item: (item["sectorX"], item["sectorY"])):
            encoded = bake_sector(read_verified(package, record), record["encoding"], overlay_types,
                                  visible_walls, level == 0)
            if encoded:
                blocks.append(struct.pack(">hhH", record["sectorX"], record["sectorY"], len(encoded)) + encoded)
        header = manifest_header + struct.pack(">hBHH", level, len(PALETTE), len(blocks), len(markers))
        yield atlas_name(level), header + palette + b"".join(markers) + b"".join(blocks), len(markers)


if __name__ == "__main__":
    if sys.argv[1:] not in ([], ["--check"]):
        raise SystemExit("Usage: build-surface-minimap.py [--check]")
    primary = json.loads((ROOT / "server/world-builder-configs/primary.json").read_text())
    for name, data, count in bake(ROOT / primary["serverMapRelativePath"]):
        target = OUTPUT / name
        if sys.argv[1:]:
            if not target.exists() or target.read_bytes() != data:
                raise SystemExit(f"Minimap atlas differs from the installed map: {name}")
        else:
            OUTPUT.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        print(f"{name}: {len(data):,} bytes, {count} landmarks")
