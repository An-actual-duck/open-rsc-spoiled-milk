#!/usr/bin/env python3
"""Bake the installed surface map into individually compressed minimap sectors."""

import hashlib
import json
import struct
import sys
import xml.etree.ElementTree as ET
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "dev/myworld/assets/runtime/ui/minimap/surface.bin"
SECTOR = 48
PIXELS = SECTOR * 2
TRANSPARENT = 12345678  # Scene.TRANSPARENT
LANDMARKS = ROOT / "dev/myworld/assets/authoring/ui/minimap/landmarks.json"
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


def floor_color(elevation, texture, overlay, roof, overlay_types):
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
        return 5
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


def bake_sector(raw, encoding, overlay_types, visible_walls):
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
        color = floor_color(elevation, texture, overlay, roof, overlay_types)
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


def bake_landmarks(placements):
    anchors = set()
    for source, collection, catalog, position in (
        ("npc", "npcs", "npcId", "start"),
        ("scenery", "scenery", "sceneryId", "position"),
    ):
        anchors.update((source, item[catalog], item[position]["x"], item[position]["y"])
                       for item in placements[collection])

    offsets = [(0, 0)]
    for distance in range(1, MARKER_SPACING + 2):
        offsets.extend(((0, -distance), (0, distance), (distance, 0), (-distance, 0)))
    occupied = []
    blocks = []
    for town, landmarks in json.loads(LANDMARKS.read_text()).items():
        for point in landmarks:
            x, y = point["x"], point["y"]
            if (point["source"], point["id"], x, y) not in anchors:
                raise ValueError(f"Missing landmark anchor in {town}: {point['label']}")
            for dx, dy in offsets:
                if all(max(abs(x + dx - other_x), abs(y + dy - other_y)) >= MARKER_SPACING
                       for other_x, other_y in occupied):
                    break
            else:
                raise ValueError(f"Landmarks too close in {town}: {point['label']}")
            occupied.append((x + dx, y + dy))
            icon = point["icon"].encode("ascii")
            label = point["label"].encode("utf-8")
            blocks.append(struct.pack(">hhbbBB", x, y, dx, dy, len(icon), len(label)) + icon + label)
    return blocks

def bake(package):
    manifest = (package / "manifest.json").read_bytes()
    world = json.loads(manifest)
    if world["storage"]["sectorSize"] != SECTOR:
        raise ValueError("Unexpected map sector size")
    surface = lambda item: item["worldSpace"] == "global" and item["level"] == 0
    placements = next(record for record in world["placementSets"] if surface(record))
    markers = bake_landmarks(json.loads(read_verified(package, placements)))
    defs = ET.parse(ROOT / "server/conf/server/defs/TileDef.xml").getroot()
    overlay_types = {i: int(tile.findtext("unknown")) for i, tile in enumerate(defs, 1)}
    boundaries = ET.parse(ROOT / "server/conf/server/defs/DoorDef.xml").getroot()
    visible_walls = visible_wall_ids(boundaries)
    blocks = []
    for record in sorted(filter(surface, world["terrainSectors"]),
                         key=lambda item: (item["sectorX"], item["sectorY"])):
        encoded = bake_sector(read_verified(package, record), record["encoding"], overlay_types, visible_walls)
        if encoded:
            blocks.append(struct.pack(">hhH", record["sectorX"], record["sectorY"], len(encoded)) + encoded)
    header = b"RMAP\x02" + hashlib.sha256(manifest).digest()
    header += struct.pack(">BHH", len(PALETTE), len(blocks), len(markers))
    palette = b"".join(struct.pack(">I", color) for color in PALETTE)
    return header + palette + b"".join(markers) + b"".join(blocks)


if __name__ == "__main__":
    primary = json.loads((ROOT / "server/world-builder-configs/primary.json").read_text())
    package = ROOT / primary["serverMapRelativePath"]
    data = bake(package)
    if sys.argv[1:] == ["--check"]:
        if OUTPUT.read_bytes() != data:
            raise SystemExit("Minimap atlas differs from the installed map")
    elif not sys.argv[1:]:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_bytes(data)
    else:
        raise SystemExit("Usage: build-surface-minimap.py [--check]")
    print(f"Surface minimap atlas: {len(data):,} bytes")
