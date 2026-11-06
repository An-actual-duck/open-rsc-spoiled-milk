#!/usr/bin/env python3
"""Extend the disposable bridge fixture with real rope/puzzle placements."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

from installed_world_package import manifest as installed_manifest, package_root

ROOT = Path(__file__).resolve().parents[2]
target = Path(sys.argv[1]).resolve()
installed = package_root("server")
authored = []
for entry in installed_manifest("server")["placementSets"]:
    if entry["worldSpace"] == "global" and entry["level"] == -1:
        authored.extend(json.loads((installed / entry["path"]).read_text())["scenery"])
anchors = ((798, 681, 603), (799, 679, 603), (771, 697, 603),
           (801, 689, 612), (801, 689, 621), (802, 690, 617),
           (825, 728, 608), (829, 728, 604), (728, 672, 588))
for object_id, x, y in anchors:
    matches = [obj for obj in authored if obj["sceneryId"] == object_id
               and obj["position"] == {"x": x, "y": y}]
    if len(matches) != 1:
        raise SystemExit(f"Installed Underground Pass placement changed: {object_id} at {x},{y},-1")

subprocess.run([sys.executable, str(ROOT / "tests/myworld/prepare-underground-pass-bridge-fixture.py"),
                str(target)], check=True)
package = target / "package"
manifest = json.loads((package / "manifest.json").read_text())
ids = {771, 798, 799, 792, 793, 777, 785, 786, 787, 788, 789, 790, 791, 782, 801, 802}
selected = [obj for obj in authored if obj["sceneryId"] in ids
            and 677 <= obj["position"]["x"] <= 698 and 602 <= obj["position"]["y"] <= 621]
selected.extend(obj for obj in authored if obj["sceneryId"] in (825, 828, 829)
                and obj["position"]["x"] == 728)
surface = []
for entry in installed_manifest("server")["placementSets"]:
    if entry["worldSpace"] == "global" and entry["level"] == 0:
        surface.extend(json.loads((installed / entry["path"]).read_text())["scenery"])
tombs = [obj for obj in surface if obj["sceneryId"] == 878
         and obj["position"] == {"x": 724, "y": 654}]
if len(tombs) != 1:
    raise SystemExit("Installed Iban tomb placement changed: 878 at 724,654,0")
selected.extend(tombs)
selected.extend(obj for obj in authored if obj["sceneryId"] == 728
                and obj["position"] == {"x": 672, "y": 588})
entrances = [obj for obj in surface if obj["sceneryId"] == 725
             and obj["position"] == {"x": 715, "y": 580}]
if len(entrances) != 1:
    raise SystemExit("Installed cave entrance changed: 725 at 715,580,0")
selected.extend(entrances)
for entry in manifest["placementSets"]:
    path = package / entry["path"]
    placements = json.loads(path.read_text())
    for obj in selected:
        clone = dict(obj, placementId=f"audit-{entry['level']}-{obj['placementId']}")
        placements["scenery"].append(clone)
    data = json.dumps(placements, sort_keys=True).encode()
    path.write_bytes(data)
    entry["sha256"] = hashlib.sha256(data).hexdigest()

# Include the grill pit, northern plank passage, and the actual surface tomb.
for level in (-1, 0):
    original = next(entry for entry in manifest["terrainSectors"] if entry["level"] == level)
    for sector_x, sector_y in ((14, 13), (15, 12), (15, 13)):
        name = f"terrain-{level}-{sector_x}-{sector_y}.json"
        (package / name).write_bytes((package / original["path"]).read_bytes())
        manifest["terrainSectors"].append(dict(original, sectorX=sector_x, sectorY=sector_y, path=name))
data = json.dumps(manifest, sort_keys=True).encode()
(package / "manifest.json").write_bytes(data)
configuration = (target / "myworld.conf").read_text()
configuration = re.sub(r"(?m)^(\s*layered_native_terrain_manifest_sha256:) .*$",
                       lambda match: match[1] + " " + hashlib.sha256(data).hexdigest(), configuration)
if len(sys.argv) == 3 and sys.argv[2] == "--installed-map":
    configuration = re.sub(r"(?m)^(\s*layered_native_terrain_package_path:) .*$",
                           lambda match: match[1] + " " + str(installed), configuration)
    configuration = re.sub(r"(?m)^(\s*layered_native_terrain_manifest_sha256:) .*$",
                           lambda match: match[1] + " " + hashlib.sha256(
                               (installed / "manifest.json").read_bytes()).hexdigest(), configuration)
(target / "myworld.conf").write_text(configuration)
