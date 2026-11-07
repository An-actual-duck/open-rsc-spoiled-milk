#!/usr/bin/env python3
"""Build isolated native terrain/route fixtures from the selected installed world."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
import xml.etree.ElementTree as ET

from installed_world_package import ROOT, manifest, package_root


target = Path(sys.argv[1]).resolve()
if target.exists():
    raise SystemExit("Use a fresh fixture directory; prior evidence is preserved")
source = package_root("server")
installed = manifest("server")
placements = []
for entry in installed["placementSets"]:
    payload = (source / entry["path"]).read_bytes()
    assert hashlib.sha256(payload).hexdigest() == entry["sha256"]
    data = json.loads(payload)
    for kind in ("scenery", "boundaries"):
        placements.extend(dict(obj, kind=kind, level=data["level"], source=entry["path"])
                          for obj in data[kind])


def objects(identity, kind="scenery"):
    key = "sceneryId" if kind == "scenery" else "boundaryId"
    return [obj for obj in placements if obj["kind"] == kind and obj[key] == identity]


def location(obj):
    return [obj["position"]["x"], obj["position"]["y"], obj["level"]]


object_defs = ET.parse(ROOT / "server/conf/server/defs/GameObjectDef.xml").getroot()
scenarios = []


def route(plugin, obj, start, destination, label, *, edge="low", agility=70,
          trace=None, command=None, setup=None, damage=None):
    scenarios.append(dict(key=label + ":" + obj["placementId"], plugin=plugin,
                          kind=obj["kind"], objectId=obj.get("sceneryId", obj.get("boundaryId")),
                          objectLocation=location(obj), placementId=obj["placementId"],
                          start=start, destination=destination, edge=edge, agility=agility,
                          trace=trace, command=command, setup=setup or [], damage=damage))


# Classic course cross-floor controls and both outcomes of the elevated ledge.
barbarian = "authentic.skills.agility.BarbarianAgilityCourse"
for obj in objects(675):
    for edge, dest, damage in (("low", [486, 559, 0], 0), ("high", [486, 557, -1], 15)):
        route(barbarian, obj, [486, 555, 0], dest, "Barbarian swing " + edge,
              edge=edge, agility=35, damage=damage, trace=[dest])
for obj in objects(677):
    route(barbarian, obj, [496, 564, 0], [496, 563, 1], "Barbarian net", trace=[[496, 563, 1]])
for obj in objects(678):
    if obj["position"]["x"] != 498:
        route(barbarian, obj, [501, 562, 1], [501, 562, 1], "Barbarian inactive ledge segment", trace=[])
        continue
    for edge, dest, damage in (("low", [501, 562, 1], 0), ("high", [499, 563, 0], 15)):
        route(barbarian, obj, [497, 562, 1], dest, "Barbarian ledge " + edge,
              edge=edge, agility=35, damage=damage, trace=[dest])
for obj in objects(679):
    route(barbarian, obj, [496, 555, 0], [497, 555, 0], "Barbarian surface handhold control")

wilderness = "authentic.skills.agility.WildernessAgilityCourse"
for identity, start, success, failure, amount in (
    (706, [292, 111, 0], [292, 108, 0], [293, 110, -1], 15),
    (708, [295, 112, 0], [301, 111, 0], [298, 113, -1], 25),
):
    for obj in objects(identity):
        for edge, dest, damage in (("low", success, 0), ("high", failure, amount)):
            route(wilderness, obj, start, dest, "Wilderness obstacle " + str(identity) + " " + edge,
                  edge=edge, agility=52, damage=damage)

# Shortcut destinations are the existing authored endpoints, expressed in signed levels.
shortcuts = "authentic.skills.agility.AgilityShortcuts"
for identity, start, success, failure, level in (
    (684, [208, 392, -1], [207, 389, -1], None, 15),
    (685, [205, 390, -1], [206, 393, -1], None, 15),
    (628, [596, 753, -1], [596, 749, -1], [596, 702, -1], 57),
    (627, [598, 749, -1], [598, 753, -1], [598, 704, -1], 57),
    (614, [601, 725, -1], [601, 731, -1], [603, 688, -1], 40),
    (615, [601, 731, -1], [601, 725, -1], [603, 688, -1], 40),
    (636, [582, 741, -1], [580, 693, -1], None, 67),
    (633, [580, 693, -1], [582, 741, -1], None, 67),
    (656, [605, 736, -1], [608, 736, -1], None, 49),
    (657, [608, 736, -1], [605, 736, -1], None, 49),
    (658, [637, 734, 0], [637, 736, 1], None, 18),
    (1237, [375, 520, -1], [372, 520, -1], None, 70),
    (1291, [281, 183, -1], [272, 180, -1], [281, 184, -1], 67),
    (1292, [272, 180, -1], [281, 183, -1], [271, 180, -1], 67),
):
    for obj in objects(identity):
        route(shortcuts, obj, start, success, "Shortcut " + str(identity) + " success", agility=level)
        if failure:
            route(shortcuts, obj, start, failure, "Shortcut " + str(identity) + " failure",
                  agility=level, edge="high", damage=19 if identity in (614, 615) else None)

stairs = "authentic.quests.members.legendsquest.obstacles.LegendsQuestCaveAgility"
for identity, sides, middle, fall in (
    (1125, ([419, 875, -1], [423, 875, -1]), [421, 875, -1], [423, 875, -1]),
    (1124, ([419, 870, -1], [419, 874, -1]), [419, 872, -1], [419, 874, -1]),
    (1123, ([426, 870, -1], [422, 870, -1]), [424, 870, -1], [422, 870, -1]),
    (1114, ([426, 874, -1], [426, 870, -1]), [426, 872, -1], [426, 870, -1]),
):
    for obj in objects(identity):
        for side, start in enumerate(sides):
            dest = sides[1 - side]
            route(stairs, obj, start, dest, "Legends stair " + str(identity) + " side " + str(side),
                  agility=50, trace=[middle, dest], damage=0)
            route(stairs, obj, start, fall, "Legends stair " + str(identity) + " failure side " + str(side),
                  agility=50, edge="high", trace=[middle, fall], damage=3)
for identity in (558, 559, 560, 561):
    for obj in objects(identity):
        dest = location(obj); start = [obj["position"]["x"], 871, -1]
        route(stairs, obj, start, dest, "Legends walkway success", agility=50, trace=[dest], damage=0)
        route(stairs, obj, start, [421, 867, -1], "Legends walkway failure", agility=50,
              edge="high", trace=[[421, 867, -1]], damage=31)

wall = "authentic.quests.members.legendsquest.obstacles.LegendsQuestWallObjects"
for obj in objects(211, "boundaries"):
    for start, dest in (([455, 897, -1], [457, 895, -1]), ([457, 895, -1], [455, 897, -1])):
        for edge, damage in (("low", 0), ("high", 6)):
            route(wall, obj, start, dest, "Legends ruined wall " + edge + str(start),
                  agility=50, edge=edge, damage=damage, trace=[dest])
for obj in objects(1151):
    for edge, dest, damage, agility in (("low", [461, 868, -1], 0, 70),
                                       ("high", [451, 874, 0], 5, 50)):
        route("authentic.quests.members.legendsquest.obstacles.LegendsQuestGameObjects", obj,
              [451, 874, 0], dest, "Legends crevice " + edge, edge="low",
              agility=agility, setup=["legends"], damage=damage)

shilo = "authentic.quests.members.shilovillage.ShiloVillageObjects"
for obj in objects(719):
    dest = [348, 784 if obj["position"]["y"] == 787 else 788, -1]
    route(shilo, obj, [348, 788, -1], dest, "Shilo cave rock success", agility=32,
          setup=["beads"], trace=[[349, 786, -1], dest], damage=0)
    route(shilo, obj, [348, 788, -1], [348, 788, -1], "Shilo cave rock failure", agility=32,
          setup=["beads"], edge="high", trace=[[349, 786, -1], [348, 788, -1]], damage=3)
for obj in objects(690):
    route(shilo, obj, [467, 844, -1], [471, 836, 0], "Shilo tomb handholds success",
          agility=32, damage=0, trace=[[471, 836, 0]])
    route(shilo, obj, [467, 844, -1], [467, 842, -1], "Shilo tomb handholds failure",
          agility=32, edge="high", damage=18, trace=[[380, 860, -1], [467, 842, -1]])
for obj in objects(696):
    for edge, dest, damage in (("low", [339, 808, 0], 23), ("high", [344, 808, 0], 0)):
        route(shilo, obj, [342, 852, -1], dest, "Shilo waterfall " + edge, agility=32,
              edge=edge, damage=damage, trace=[[342, 852, -1], dest])
for obj in objects(670):
    dest = [356, 835, -1] if obj["position"] == {"x": 348, "y": 876} else [347, 877, -1]
    route(shilo, obj, [obj["position"]["x"] + 1, obj["position"]["y"], -1], dest,
          "Shilo rubble passage", agility=32, trace=[dest], damage=0)
for obj in objects(651):
    for rope in (False, True):
        route(shilo, obj, [353, 815, 0], [352, 818, -1], "Shilo fissure " + str(rope),
              command="Search", setup=["shilo-fissure", "rope"] if rope else ["shilo-fissure"],
              trace=[[352, 818, -1]] if rope else [[380, 860, -1], [352, 818, -1]],
              damage=0 if rope else 31)
for obj in objects(688):
    route(shilo, obj, [473, 826, 0], [471, 826, -1], "Shilo stacked rocks success",
          agility=32, command="Search", setup=["shilo-tomb"], trace=[[471, 826, -1]], damage=0)
    route(shilo, obj, [473, 826, 0], [473, 826, 0], "Shilo stacked rocks failure",
          agility=32, command="Search", edge="high", setup=["shilo-tomb"], trace=[], damage=3)

for identity, start, dest in ((635, [712, 476, 1], [710, 476, 2]),
                             (646, [710, 476, 2], [712, 476, 1])):
    for obj in objects(identity):
        route("authentic.quests.members.grandtree.GrandTree", obj, start, dest,
              "Grand Tree tower " + str(identity), agility=25, trace=[dest])

digsite = "authentic.quests.members.digsite."
for identity, dest, other in ((1095, [26, 514, -1], None), (1053, [19, 505, -1], [19, 553, -1])):
    for obj in objects(identity):
        start = [obj["position"]["x"], obj["position"]["y"] + 1, 0]
        route(digsite + "DigsiteWinch", obj, start, dest, "Digsite winch " + str(identity),
              agility=10, setup=["digsite"], trace=[dest])
        if other:
            route(digsite + "DigsiteWinch", obj, start, other, "Digsite late winch",
                  agility=10, setup=["digsite-late"], trace=[other])
for identity, dest in ((1097, [14, 506, 0]), (1098, [25, 515, 0])):
    for obj in objects(identity):
        route(digsite + "DigsiteObjects", obj, location(obj), dest,
              "Digsite rope ascent", trace=[dest])

# The Gnome drop is a regression control from the preceding accepted patch.
for identity, start, dest in ((647, [692, 504, 0], [692, 504, 1]),
                             (648, [693, 507, 1], [693, 506, 2]),
                             (650, [689, 507, 2], [685, 508, 2]),
                             (649, [684, 506, 2], [683, 506, 0])):
    for obj in objects(identity):
        route("authentic.skills.agility.GnomeAgilityCourse", obj, start, dest,
              "Gnome control " + str(identity), trace=[dest])

# Existing catwalk success/fall branches: installed placements, both ends where applicable.
up = "authentic.quests.members.undergroundpass.obstacles.UndergroundPassAgilityObstacles"
for identity in (889, 921):
    for obj in objects(identity):
        dest = [766, 585, 0] if identity == 889 else [739, 667, 0]
        route(up, obj, location(obj), dest, "Underground Pass stairs " + str(identity),
              setup=["underground-pass"], trace=[dest])
falls = {889: (738, 584), 891: (738, 584), 898: (756, 591), 893: (753, 608),
         892: (734, 596), 896: (734, 610), 910: (734, 662), 907: (733, 646),
         906: (731, 639), 905: (742, 630), 904: (742, 630), 908: (760, 638),
         909: (745, 656), 902: (759, 664), 903: (761, 613), 901: (727, 617),
         895: (727, 618), 899: (734, 619), 900: (734, 666), 894: (763, 613), 897: (753, 585)}
for identity in sorted(set(falls) - {889}):
    for obj in objects(identity):
        x, y, level = location(obj)
        # Derive the long axis from the native definition and installed rotation.
        definition = object_defs[identity]
        width, height = int(definition.findtext("width")), int(definition.findtext("height"))
        if obj["direction"] not in (0, 4):
            width, height = height, width
        assert max(width, height) == 3 and min(width, height) == 1
        horizontal = width > height
        if identity == 891:
            horizontal = True
        starts = [[x - 1, y, level], [x + 3, y, level]] if horizontal else [[x, y - 1, level], [x, y + 3, level]]
        destinations = [[x + 3, y, level], [x - 1, y, level]] if horizontal else [[x, y + 3, level], [x, y - 1, level]]
        if identity == 891:
            starts, destinations = [[773, y, level], [776, y, level]], [[776, y, level], [773, y, level]]
        for side, (start, dest) in enumerate(zip(starts, destinations)):
            route(up, obj, start, dest, "Underground Pass bridge success " + str(side),
                  setup=["underground-pass"], trace=[dest], damage=0)
            fall = [*falls[identity], 0]
            route(up, obj, start, fall, "Underground Pass bridge failure " + str(side),
                  setup=["underground-pass"], edge="high", trace=[fall], damage=24)
for identity, xs in ((862, [730]), (864, [734, 751]), (863, [763]),
                     (872, [748, 765]), (865, [728]), (866, [738, 755])):
    y = {862: 662, 864: 664, 863: 610, 872: 665, 865: 667, 866: 669}[identity]
    for obj in objects(identity):
        for index, x in enumerate(xs):
            setup = ["up-old-stage"] if len(xs) > 1 and index == 0 else ["underground-pass"]
            route(up, obj, location(obj), [x, y, -1], "Underground Pass ledge success " + str(index),
                  setup=setup, trace=[[x, y, -1]], damage=0)
        route(up, obj, location(obj), location(obj), "Underground Pass ledge failure",
              setup=["underground-pass"], edge="high", trace=[], damage=2)

# The fixed Map1 drop and swamp-recovery chain are same-layer controls.
up1 = "authentic.quests.members.undergroundpass.obstacles.UndergroundPassObstaclesMap1"
for obj in objects(812):
    route(up1, obj, [706, 609, -1], [706, 607, -1], "UP Map1 ledge drop", trace=[[706, 607, -1]])
for obj in objects(754):
    route(up1, obj, location(obj), [690, 629, -1], "UP Map1 swamp fall", damage=6,
          trace=[[674, 630, -1], [677, 630, -1], [680, 633, -1], [682, 630, -1],
                 [683, 633, -1], [685, 632, -1], [687, 630, -1], [690, 629, -1]])
for obj in objects(767):
    route(up1, obj, location(obj), [685, 588, -1], "UP Map1 mud recovery", trace=[[685, 588, -1]])

# Shared Map2 helpers retain the cave level, including their forced slip branches.
up2 = "authentic.quests.members.undergroundpass.obstacles.UndergroundPassObstaclesMap2"
for obj in objects(837):
    route(up2, obj, [764, 633, -1], [764, 631, -1], "UP Map2 ledge climb success",
          command="climb up", edge="high", damage=0, trace=[[764, 631, -1]])
    route(up2, obj, [764, 633, -1], [764, 633, -1], "UP Map2 ledge climb slip",
          command="climb up", damage=3, trace=[])
    route(up2, obj, [764, 631, -1], [764, 635, -1], "UP Map2 doomed ledge jump",
          command="jump", damage=24, trace=[[764, 629, -1], [764, 635, -1]])
for obj in objects(856):
    for side, (start, dest) in enumerate((([734, 648, -1], [735, 647, -1]),
                                         ([735, 647, -1], [734, 648, -1]))):
        route(up2, obj, start, dest, "UP Map2 hijack rock success " + str(side), damage=0, trace=[dest])
        route(up2, obj, start, [734, 651, -1], "UP Map2 hijack rock slip " + str(side),
              edge="high", damage=24, trace=[[734, 651, -1]])

# Actual configured crawl placements retain their signed destinations and safety proof.
for obj in placements:
    if "agilityShortcut" not in obj:
        continue
    shortcut = obj["agilityShortcut"]
    if obj["level"] == 0 and all(shortcut[key]["level"] == 0 for key in ("successDestination", "failureDestination")):
        continue
    failure = shortcut["failureDestination"]
    success = shortcut["successDestination"]
    endpoint = lambda value: [value["x"], value["y"], value["level"]]
    route("configured", obj, endpoint(failure), endpoint(success), "Configured crawl success",
          agility=99, command=shortcut["action"], trace=[endpoint(success)])

for scenario in scenarios:
    if scenario["plugin"] == shortcuts and scenario["objectId"] in (1291, 1292):
        facing = ([280, 183, -1], 6) if scenario["objectId"] == 1291 else ([272, 181, -1], 4)
        scenario["facings"] = [dict(location=facing[0], sprite=facing[1])]
        if scenario["edge"] == "low":
            scenario["facings"].append(dict(location=[272, 183, -1], sprite=0 if scenario["objectId"] == 1291 else 2))

assert scenarios
target.mkdir(parents=True)
for directory in ("conf", "database"):
    shutil.copytree(ROOT / "server" / directory, target / directory)
for filename in ("badwords.txt", "goodwords.txt", "globalrules.txt", "alertwords.txt"):
    shutil.copy2(ROOT / "server" / filename, target / filename)
(target / "connections.conf").write_text("db_type: sqlite\n")
(target / "source-world.json").write_text(json.dumps(dict(package=str(source), manifestSha256=hashlib.sha256((source / "manifest.json").read_bytes()).hexdigest(), placementCount=len(placements)), indent=2) + "\n")
(target / "scenarios.json").write_text(json.dumps(scenarios, indent=2) + "\n")
package = target / "package"
package.mkdir()


def write(name, value):
    payload = json.dumps(value, sort_keys=True).encode()
    (package / name).write_bytes(payload)
    return hashlib.sha256(payload).hexdigest()


needed = set()
for scenario in scenarios:
    for x, y, level in [scenario["objectLocation"], scenario["start"], scenario["destination"]] + (scenario["trace"] or []):
        needed.update((level, x // 48 + dx, y // 48 + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1))
subset = {key: installed[key] for key in ("schemaVersion", "packageType", "coordinateModel", "storage", "worldSpaces")}
subset.update(packageId="nonzero-agility-regression", packageVersion="1.0.0", levels=[], terrainSectors=[], placementSets=[])
for sector in installed["terrainSectors"]:
    key = (sector["level"], sector["sectorX"], sector["sectorY"])
    if sector["worldSpace"] != "global":
        continue
    payload = (source / sector["path"]).read_bytes()
    assert hashlib.sha256(payload).hexdigest() == sector["sha256"]
    name = "sector-%s-%s-%s.bin" % key
    (package / name).write_bytes(payload)
    subset["terrainSectors"].append(dict(sector, path=name))
included = {(sector["level"], sector["sectorX"], sector["sectorY"]) for sector in subset["terrainSectors"]}
levels = {key[0] for key in included}
subset["levels"] = [entry for entry in installed["levels"] if entry["worldSpace"] == "global" and entry["level"] in levels]
for level in sorted(levels):
    data = dict(schemaVersion=5, encoding="layered-world-placements-v5", worldSpace="global", level=level,
                npcs=[], groundItems=[], scenery=[], boundaries=[])
    for obj in placements:
        if obj["level"] == level and (level, obj["position"]["x"] // 48, obj["position"]["y"] // 48) in needed:
            data[obj["kind"]].append({key: value for key, value in obj.items() if key not in ("kind", "level", "source")})
    name = f"placements-{level}.json"
    digest = write(name, data)
    subset["placementSets"].append(dict(id=f"course-{level}".replace("--", "-minus-"), worldSpace="global", level=level,
                                       encoding="layered-world-placements-v5", path=name, sha256=digest))
digest = write("manifest.json", subset)
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
print(f"Prepared {len(scenarios)} branch scenarios using {len(subset['terrainSectors'])} real installed sectors")
