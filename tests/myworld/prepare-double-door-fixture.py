#!/usr/bin/env python3
"""Disposable map/config fixture. No account reads, game launch or source-target changes."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
target = Path(sys.argv[1]).resolve()
if target.exists():
    raise SystemExit("Use a fresh fixture directory; refusing to overwrite evidence")
target.mkdir(parents=True)
shutil.copytree(root / "server/conf", target / "conf")
shutil.copytree(root / "server/database", target / "database")
for name in ("badwords.txt", "goodwords.txt", "globalrules.txt", "alertwords.txt"):
    shutil.copy2(root / "server" / name, target / name)
(target / "connections.conf").write_text("database:\n\tdb_type: MYSQL\n\tdb_host: 127.0.0.1\n\tdb_user: isolated_test\n\tdb_pass: unused\n")
definitions = ET.parse(root / "server/conf/server/defs/GameObjectDef.xml").getroot().findall("GameObjectDef")
closed = [i for i, d in enumerate(definitions) if d.findtext("name") == "Pass-through double doors" and d.findtext("command1") == "Open"]
assert len(closed) == 1
marker = next(i for i, d in enumerate(definitions) if d.findtext("command1", "").lower() == "agility-jump")
package = target / "package"
package.mkdir()
def write(name, value):
    data = json.dumps(value, sort_keys=True).encode()
    (package / name).write_bytes(data)
    return hashlib.sha256(data).hexdigest()
manifest = {"schemaVersion": 1, "packageType": "layered-world", "coordinateModel": "signed-layered-v1", "packageId": "double-door-test", "packageVersion": "1.0.0", "storage": {"sectorSize": 48, "presentationChunkSize": 24}, "worldSpaces": [{"id": "global", "kind": "static"}], "levels": [], "terrainSectors": [], "placementSets": []}
for level in (0, 1, 2, 3, -3):
    manifest["levels"].append({"worldSpace": "global", "level": level, "name": f"Test {level}", "role": "test"})
    for sx in (0, 1):
        overlay = 3 if sx == 0 else (0 if level in (1, 2) else 255)
        terrain = {"schemaVersion": 1, "encoding": "uniform-layered-sector-v1", "size": 48, "tile": {"elevation": 0, "texture": 93, "overlay": overlay, "roof": 0, "verticalWall": 0, "horizontalWall": 0, "diagonalWall": 0}}
        name = f"terrain-{level}-{sx}.json"
        encoding = "uniform-layered-sector-v1"
        if sx == 0:
            # Upper two-tile corridor: real ordinary floor surrounded by transparent void.
            # A second isolated floor corner has an empty half after vanilla edge cleanup.
            data = bytearray()
            for x in range(48):
                for y in range(48):
                    raw = overlay
                    if 8 <= x <= 15 and 25 <= y <= 32:
                        raw = 1 if y in (28, 29) else 8
                    if 20 <= x <= 25 and 25 <= y <= 32:
                        raw = 1 if (x >= 22 and y >= 28) else 0
                    data += struct.pack(">6Bi", 0, 93, raw, 0, 0, 0, 0)
            name = f"terrain-{level}-{sx}.bin"
            (package / name).write_bytes(data)
            digest = hashlib.sha256(data).hexdigest()
            encoding = "raw-layered-sector-v1"
        else:
            digest = write(name, terrain)
        manifest["terrainSectors"].append({"worldSpace": "global", "level": level, "sectorX": sx, "sectorY": 0, "encoding": encoding, "path": name, "sha256": digest})
    scenery = [{"placementId": f"door-{level}-{direction}", "sceneryId": closed[0], "position": {"x": 12 + index * 8, "y": 12}, "direction": direction} for index, direction in enumerate((0, 2, 4, 6))]
    scenery += [{"placementId": f"void-door-{level}", "sceneryId": closed[0], "position": {"x": 60, "y": 30}, "direction": 0}, {"placementId": f"edge-door-{level}", "sceneryId": closed[0], "position": {"x": 30, "y": 46}, "direction": 2}]
    scenery += [{"placementId": f"hall-door-{level}", "sceneryId": closed[0], "position": {"x": 12, "y": 28}, "direction": 0}, {"placementId": f"half-floor-door-{level}", "sceneryId": closed[0], "position": {"x": 22, "y": 28}, "direction": 4}]
    boundaries = []
    if level == 0:
        scenery += [{"placementId": "block-test-door", "sceneryId": closed[0], "position": {"x": 12, "y": 22}, "direction": 0}, {"placementId": "block-test-marker", "sceneryId": marker, "position": {"x": 11, "y": 22}, "direction": 0}]
        boundaries = [{"placementId": "sidewall-marker", "boundaryId": 1, "position": {"x": 11, "y": 22}, "direction": 0}, {"placementId": "edgewall-marker", "boundaryId": 1, "position": {"x": 12, "y": 22}, "direction": 1}]
    payload = {"schemaVersion": 4, "encoding": "layered-world-placements-v4", "worldSpace": "global", "level": level, "npcs": [], "groundItems": [], "scenery": scenery, "boundaries": boundaries}
    name = f"placements-{level}.json"
    digest = write(name, payload)
    manifest["placementSets"].append({"id": f"placements-{level}".replace("--", "-minus-"), "worldSpace": "global", "level": level, "encoding": "layered-world-placements-v4", "path": name, "sha256": digest})
manifest_hash = write("manifest.json", manifest)
configuration = (root / "server/myworld.conf").read_text()
for key, value in {"layered_native_terrain_package_path": str(package), "layered_native_terrain_manifest_sha256": manifest_hash, "layered_native_world_runtime_profile": "native-replacement", "want_discord_bot": "false"}.items():
    configuration, count = re.subn(r"(?m)^([ \t]*)" + key + r":.*$", lambda match: f"{match[1]}{key}: {value}", configuration)
    if not count:
        configuration += f"\n{key}: {value}\n"
(target / "myworld.conf").write_text(configuration)
print(target)
