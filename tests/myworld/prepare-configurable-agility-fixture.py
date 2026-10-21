#!/usr/bin/env python3
"""Create disposable authored terrain/config only; never reads or copies accounts or credentials."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
target = Path(sys.argv[1]).resolve()
if target.exists():
    raise SystemExit("Use a fresh fixture directory; refusing to overwrite existing evidence")
target.mkdir(parents=True)
shutil.copytree(root / "server/conf", target / "conf")
shutil.copytree(root / "server/database", target / "database")
for filename in ("badwords.txt", "goodwords.txt", "globalrules.txt", "alertwords.txt"):
    shutil.copy2(root / "server" / filename, target / filename)
(target / "connections.conf").write_text("database:\n\tdb_type: MYSQL\n\tdb_host: 127.0.0.1\n\tdb_user: isolated_test\n\tdb_pass: unused\n")

definitions = ET.parse(root / "server/conf/server/defs/GameObjectDef.xml").getroot().findall("GameObjectDef")
generic = [i for i, d in enumerate(definitions) if d.findtext("command1", "").lower() == "agility-jump"]
assert len(generic) == 1
package = target / "package"
package.mkdir()
def write(name, value):
    data = json.dumps(value, sort_keys=True).encode()
    (package / name).write_bytes(data)
    return hashlib.sha256(data).hexdigest()

terrain = {"schemaVersion": 1, "encoding": "uniform-layered-sector-v1", "size": 48,
           "tile": {"elevation": 0, "texture": 93, "overlay": 3, "roof": 0,
                    "verticalWall": 0, "horizontalWall": 0, "diagonalWall": 0}}
terrain_hash = write("terrain.json", terrain)
manifest = {"schemaVersion": 1, "packageType": "layered-world", "coordinateModel": "signed-layered-v1",
            "packageId": "agility-test", "packageVersion": "1.0.0", "storage": {"sectorSize": 48, "presentationChunkSize": 24},
            "worldSpaces": [{"id": "global", "kind": "static"}], "levels": [], "terrainSectors": [], "placementSets": []}
def endpoint(x, y, level):
    return {"worldSpace": "global", "x": x, "y": y, "level": level}
for level in (0, 2, -3):
    manifest["levels"].append({"worldSpace": "global", "level": level, "name": f"Test level {level}", "role": "test"})
    manifest["terrainSectors"].append({"worldSpace": "global", "level": level, "sectorX": 0, "sectorY": 0,
                                      "encoding": "uniform-layered-sector-v1", "path": "terrain.json", "sha256": terrain_hash})
    scenery = []
    if level == 2:
        scenery.append({"placementId": "landing-marker", "sceneryId": generic[0], "position": {"x": 30, "y": 30}, "direction": 0})
    if level == 0:
        for name, x, chance in (("success-jump", 20, 100), ("failure-jump", 24, 0)):
            scenery.append({"placementId": name, "sceneryId": generic[0], "position": {"x": x, "y": 20}, "direction": 0,
                            "agilityShortcut": {"schemaVersion": 1, "template": "jump-v1", "action": "agility-jump",
                                                "requiredLevel": 30, "startChancePercent": chance, "maxChancePercent": chance,
                                                "maxChanceLevel": 30, "successDestination": endpoint(30, 30, 2),
                                                "failureDestination": endpoint(32, 30, -3)}})
    payload = {"schemaVersion": 5, "encoding": "layered-world-placements-v5", "worldSpace": "global", "level": level,
               "npcs": [], "groundItems": [], "scenery": scenery, "boundaries": []}
    name = f"placements-{level}.json"
    digest = write(name, payload)
    manifest["placementSets"].append({"id": f"placements-{level}".replace("--", "-minus-"), "worldSpace": "global", "level": level,
                                      "encoding": "layered-world-placements-v5", "path": name, "sha256": digest})
# Each payload path must have one owner, even if bytes are identical.
for sector in manifest["terrainSectors"]:
    name = f"terrain-{sector['level']}.json"
    sector["path"] = name
    write(name, terrain)
manifest_hash = write("manifest.json", manifest)
configuration = (root / "server/myworld.conf").read_text()
replacements = {"layered_native_terrain_package_path": str(package), "layered_native_terrain_manifest_sha256": manifest_hash,
                "layered_native_world_runtime_profile": "native-replacement", "want_discord_bot": "false"}
for key, value in replacements.items():
    configuration, count = re.subn(r"(?m)^([ \t]*)" + key + r":.*$", lambda match: f"{match[1]}{key}: {value}", configuration)
    if not count:
        configuration += f"\n{key}: {value}\n"
(target / "myworld.conf").write_text(configuration)
print(target)
