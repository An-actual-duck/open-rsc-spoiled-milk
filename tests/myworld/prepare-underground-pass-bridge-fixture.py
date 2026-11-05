#!/usr/bin/env python3
"""Disposable bridge map: real definitions, overlapping floors, no accounts."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys

from installed_world_package import manifest as installed_manifest, package_root

ROOT = Path(__file__).resolve().parents[2]
installed = package_root("server")
authored = []
for placement in installed_manifest("server")["placementSets"]:
    if placement["worldSpace"] == "global" and placement["level"] == -1:
        authored.extend(json.loads((installed / placement["path"]).read_text())["scenery"])
for object_id, x, y in ((726, 704, 585), (733, 711, 586)):
    matches = [entry for entry in authored if entry["sceneryId"] == object_id
               and entry["position"] == {"x": x, "y": y}]
    if len(matches) != 1:
        raise SystemExit(f"Installed bridge/lever placement changed: {object_id} at {x},{y},-1")
target = Path(sys.argv[1]).resolve()
if target.exists():
    raise SystemExit("Use a fresh bridge fixture directory")
target.mkdir(parents=True)
for name in ("conf", "database"):
    shutil.copytree(ROOT / "server" / name, target / name)
for name in ("badwords.txt", "goodwords.txt", "globalrules.txt", "alertwords.txt"):
    shutil.copy2(ROOT / "server" / name, target / name)
(target / "connections.conf").write_text(
    "db_type: sqlite\n"  # Server context construction never initializes a database.
)
package = target / "package"
package.mkdir()


def write(name, value):
    data = json.dumps(value, sort_keys=True).encode()
    (package / name).write_bytes(data)
    return hashlib.sha256(data).hexdigest()


manifest = {
    "schemaVersion": 1, "packageType": "layered-world",
    "coordinateModel": "signed-layered-v1", "packageId": "underground-bridge-test",
    "packageVersion": "1.0.0", "storage": {"sectorSize": 48, "presentationChunkSize": 24},
    "worldSpaces": [{"id": "global", "kind": "static"}],
    "levels": [], "terrainSectors": [], "placementSets": [],
}
for level in (-1, 0):
    manifest["levels"].append({"worldSpace": "global", "level": level,
                               "name": f"Bridge test {level}", "role": "test"})
    name = f"terrain-{level}.json"
    digest = write(name, {"schemaVersion": 1, "encoding": "uniform-layered-sector-v1",
                          "size": 48, "tile": {"elevation": 0, "texture": 93,
                          "overlay": 3, "roof": 0, "verticalWall": 0,
                          "horizontalWall": 0, "diagonalWall": 0}})
    manifest["terrainSectors"].append({"worldSpace": "global", "level": level,
        "sectorX": 14, "sectorY": 12, "encoding": "uniform-layered-sector-v1",
        "path": name, "sha256": digest})
    name = f"placements-{level}.json"
    digest = write(name, {"schemaVersion": 4, "encoding": "layered-world-placements-v4",
        "worldSpace": "global", "level": level, "npcs": [], "groundItems": [],
        "scenery": [
            {"placementId": f"bridge-{level}", "sceneryId": 726,
             "position": {"x": 704, "y": 585}, "direction": 2},
            {"placementId": f"lever-{level}", "sceneryId": 733,
             "position": {"x": 711, "y": 586}, "direction": 0}],
        "boundaries": []})
    manifest["placementSets"].append({"id": f"floor-{level}".replace("--", "-minus-"),
        "worldSpace": "global", "level": level, "encoding": "layered-world-placements-v4",
        "path": name, "sha256": digest})
manifest_hash = write("manifest.json", manifest)
configuration = (ROOT / "server/myworld.conf").read_text()
for key, value in {
    "layered_native_terrain_package_path": str(package),
    "layered_native_terrain_manifest_sha256": manifest_hash,
    "layered_native_world_runtime_profile": "native-replacement",
    "want_discord_bot": "false",
}.items():
    configuration, count = re.subn(r"(?m)^([ \t]*)" + key + r":.*$",
        lambda match: f"{match[1]}{key}: {value}", configuration)
    if not count:
        configuration += f"\n{key}: {value}\n"
(target / "myworld.conf").write_text(configuration)
print(target)
