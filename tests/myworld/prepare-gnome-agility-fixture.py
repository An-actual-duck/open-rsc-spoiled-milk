#!/usr/bin/env python3
"""Isolated course fixture from installed terrain/placements; no accounts or listeners."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys

from installed_world_package import ROOT, manifest, package_root


target = Path(sys.argv[1]).resolve()
if target.exists():
    raise SystemExit("Use a fresh fixture directory; previous evidence is preserved")
source = package_root("server")
installed = manifest("server")
target.mkdir(parents=True)
for directory in ("conf", "database"):
    shutil.copytree(ROOT / "server" / directory, target / directory)
for filename in ("badwords.txt", "goodwords.txt", "globalrules.txt", "alertwords.txt"):
    shutil.copy2(ROOT / "server" / filename, target / filename)
(target / "connections.conf").write_text("db_type: sqlite\n")
package = target / "package"
package.mkdir()


def write(name, value):
    data = json.dumps(value, sort_keys=True).encode()
    (package / name).write_bytes(data)
    return hashlib.sha256(data).hexdigest()


course = {key: installed[key] for key in (
    "schemaVersion", "packageType", "coordinateModel", "storage", "worldSpaces")}
course.update(packageId="gnome-agility-regression", packageVersion="1.0.0",
              levels=[], terrainSectors=[], placementSets=[])
expected = {0: {(655, 692, 495), (647, 692, 503), (653, 683, 502), (654, 683, 497)},
            1: {(648, 693, 508)}, 2: {(650, 689, 507), (649, 683, 508)}}
for level in (0, 1, 2):
    course["levels"].append(next(entry for entry in installed["levels"]
                                 if entry["worldSpace"] == "global" and entry["level"] == level))
    sector = next(entry for entry in installed["terrainSectors"] if entry["worldSpace"] == "global"
                  and (entry["level"], entry["sectorX"], entry["sectorY"]) == (level, 14, 10))
    payload = (source / sector["path"]).read_bytes()
    assert hashlib.sha256(payload).hexdigest() == sector["sha256"]
    name = f"terrain-{level}.bin"
    (package / name).write_bytes(payload)
    course["terrainSectors"].append(dict(sector, path=name))
    scenery, boundaries = [], []
    for entry in installed["placementSets"]:
        if entry["worldSpace"] != "global" or entry["level"] != level:
            continue
        payload = (source / entry["path"]).read_bytes()
        assert hashlib.sha256(payload).hexdigest() == entry["sha256"]
        data = json.loads(payload)
        for kind, destination in (("scenery", scenery), ("boundaries", boundaries)):
            destination.extend(obj for obj in data[kind]
                               if 680 <= obj["position"]["x"] <= 697
                               and 490 <= obj["position"]["y"] <= 515)
    actual = {(obj["sceneryId"], obj["position"]["x"], obj["position"]["y"]) for obj in scenery}
    assert expected[level] <= actual, f"Installed course obstacle placements changed on level {level}"
    name = f"placements-{level}.json"
    digest = write(name, dict(schemaVersion=4, encoding="layered-world-placements-v4",
                             worldSpace="global", level=level, npcs=[], groundItems=[],
                             scenery=scenery, boundaries=boundaries))
    course["placementSets"].append(dict(id=f"course-{level}", worldSpace="global", level=level,
                                       encoding="layered-world-placements-v4", path=name, sha256=digest))
digest = write("manifest.json", course)
configuration = (ROOT / "server/myworld.conf").read_text()
for key, value in {
    "layered_native_terrain_package_path": str(package),
    "layered_native_terrain_manifest_sha256": digest,
    "layered_native_world_runtime_profile": "native-replacement",
    "want_discord_bot": "false",
}.items():
    configuration, count = re.subn(r"(?m)^([ \t]*)" + key + r":.*$",
                                  lambda match: f"{match[1]}{key}: {value}", configuration)
    if not count:
        configuration += f"\n{key}: {value}\n"
(target / "myworld.conf").write_text(configuration)
print(target)
