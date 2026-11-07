#!/usr/bin/env python3
"""Check guide destinations against the configured installed world, not legacy locs."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import struct
import tempfile
import xml.etree.ElementTree as ET

from installed_world_package import ROOT, manifest, package_root


GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def check_locations():
    config = (ROOT / "server/myworld.conf").read_text()
    selected = re.search(r"^layered_native_terrain_package_path:\s*(\S+)", config, re.M)
    require(selected is not None, "myworld must select an installed layered package")
    server_root = package_root("server")
    require((ROOT / "server" / selected[1]).resolve() == server_root,
            "primary configuration must match myworld's effective package")
    expected_sha = re.search(r"^layered_native_terrain_manifest_sha256:\s*(\S+)", config, re.M)
    manifest_bytes = (server_root / "manifest.json").read_bytes()
    require(expected_sha and hashlib.sha256(manifest_bytes).hexdigest() == expected_sha[1],
            "selected server manifest must match myworld's pinned digest")
    require(manifest_bytes == (package_root("client") / "manifest.json").read_bytes(),
            "guide client and server must use the same installed world")

    definitions = {}
    for entry in ET.parse(ROOT / "server/conf/server/defs/extras/ObjectRunecraft.xml").getroot():
        definition = entry.find("ObjectRunecraftDef")
        definitions[int(entry.findtext("int"))] = {
            "item": int(definition.findtext("runeId")),
            "level": int(definition.findtext("requiredLvl")),
            "name": definition.findtext("runeName").title() + " Rune",
        }
    plugin = (ROOT / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/runecraft/Runecraft.java").read_text()
    aliases = {name: int(number) for name, number in
               re.findall(r"final int (\w+_ALTAR) = (\d+);", plugin)}
    fallback = plugin.split("private ObjectRunecraftDef getAltarDef", 1)[1]
    alias_ids = {aliases[name] for name in re.findall(r"case (\w+_ALTAR):", fallback)}
    require("getObjectRunecraftDef(obj.getID())" in fallback
            and "getObjectRunecraftDef(obj.getID() + 1)" in fallback,
            "review interaction identity resolution if the altar plugin changes")

    placements = []
    world_scenery = []
    world_boundaries = []
    for placement_set in manifest("server")["placementSets"]:
        relative = placement_set["path"]
        payload = (server_root / relative).read_bytes()
        require(hashlib.sha256(payload).hexdigest() == placement_set["sha256"],
                "placement digest differs: " + relative)
        require(payload == (package_root("client") / relative).read_bytes(),
                "client/server placements differ: " + relative)
        document = json.loads(payload)
        require(document["level"] == placement_set["level"], "placement level mismatch")
        world_boundaries.extend(dict(obj, level=document["level"]) for obj in document["boundaries"])
        for obj in document["scenery"]:
            world_scenery.append(dict(obj, level=document["level"]))
            identity = obj["sceneryId"]
            definition = definitions.get(identity)
            if definition is None and identity in alias_ids:
                definition = definitions.get(identity + 1)
            if definition:
                placements.append(dict(definition, x=obj["position"]["x"],
                    y=obj["position"]["y"], worldLevel=document["level"],
                    objectId=identity, placementId=obj["placementId"], source=relative))

    calls = re.findall(r'addAltarGuide\((\d+),\s*(\d+),\s*"([^"]+)",\s*'
                       r'(-?\d+),\s*(-?\d+),\s*(-?\d+)\);', GUIDE.read_text())
    expected_items = {d["item"] for d in definitions.values()}
    require(len(calls) == len(expected_items), "guide must list each craftable rune exactly once")
    require({int(c[0]) for c in calls} == expected_items, "guide rune coverage differs from definitions")
    evidence = []
    for item, level, name, x, y, world_level in calls:
        matches = [p for p in placements if
                   (p["item"], p["level"], p["name"], p["x"], p["y"], p["worldLevel"])
                   == (int(item), int(level), name, int(x), int(y), int(world_level))]
        require(len(matches) == 1,
                f"{name} guide location {x},{y},L{world_level} has {len(matches)} matching usable altars")
        evidence.append(matches[0])
    listed_ids = {p["placementId"] for p in evidence}
    teleports = check_teleports(evidence, world_scenery, world_boundaries, server_root)
    return {"package": str(server_root.relative_to(ROOT)),
            "manifestSha256": expected_sha[1], "listedAltars": evidence,
            "lawBangleLandings": teleports,
            "otherAltarPlacements": [p for p in placements if p["placementId"] not in listed_ids]}


def check_teleports(altars, scenery, boundaries, server_root):
    """Require a clear adjacent tile at the same level as each advertised altar."""
    source = (ROOT / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/enchanting/LawJewelry.java").read_text()
    enum = source.split("private enum RuneAltarDestination", 1)[1].split("private final", 1)[0]
    destinations = re.findall(r'\w+\(ItemId\.(\w+)\.id\(\),\s*'
                              r'(-?\d+),\s*(-?\d+),\s*(-?\d+)\)', enum)
    object_defs = ET.parse(ROOT / "server/conf/server/defs/GameObjectDef.xml").getroot()
    tile_defs = ET.parse(ROOT / "server/conf/server/defs/TileDef.xml").getroot()
    require(len(destinations) == len(altars), "law Bangle must cover all advertised rune altars")
    evidence = []
    sectors = manifest("server")["terrainSectors"]
    for rune, x, y, level in destinations:
        x, y, level = int(x), int(y), int(level)
        name = rune.replace("_", " ").title()
        altar = next(p for p in altars if p["name"] == name)
        require(level == altar["worldLevel"], f"{name} teleport is on the wrong level")
        definition = object_defs[altar["objectId"]]
        width, height = int(definition.findtext("width")), int(definition.findtext("height"))
        # Current altars have square footprints, so rotation does not affect this check.
        require(width == height, "review adjacency for non-square altar footprints")
        dx = max(altar["x"] - x, 0, x - (altar["x"] + width - 1))
        dy = max(altar["y"] - y, 0, y - (altar["y"] + height - 1))
        require(dx + dy == 1, f"{name} teleport must land beside its altar, not inside or elsewhere")
        sector = next(s for s in sectors if s["worldSpace"] == "global"
                      and (s["level"], s["sectorX"], s["sectorY"]) == (level, x // 48, y // 48))
        formats = {"raw-layered-sector-v1": ">BBBBBBI", "raw-layered-sector-v2-u16": ">HBBBBBI"}
        require(sector["encoding"] in formats, "review landing decoder for new encoding")
        payload = (server_root / sector["path"]).read_bytes()
        require(hashlib.sha256(payload).hexdigest() == sector["sha256"], "landing sector digest mismatch")
        require(payload == (package_root("client") / sector["path"]).read_bytes(),
                "client/server landing terrain differs")
        fmt = formats[sector["encoding"]]
        tile = struct.unpack_from(fmt, payload, ((x % 48) * 48 + y % 48) * struct.calcsize(fmt))
        overlay = tile[2]
        require(not overlay or tile_defs[overlay - 1].findtext("objectType") == "0",
                f"{name} teleport lands on blocking terrain")
        require(tile[4:] == (0, 0, 0), f"{name} teleport lands on a terrain wall")
        for obj in scenery:
            if obj["level"] != level:
                continue
            ox, oy = obj["position"]["x"], obj["position"]["y"]
            definition = object_defs[obj["sceneryId"]] if obj["sceneryId"] < len(object_defs) else None
            if definition is None or definition.findtext("type") != "1":
                continue
            ow, oh = int(definition.findtext("width")), int(definition.findtext("height"))
            if obj.get("direction", 0) not in (0, 4):
                ow, oh = oh, ow
            require(not (ox <= x < ox + ow and oy <= y < oy + oh),
                    f"{name} teleport overlaps blocking scenery {obj['sceneryId']}")
        if rune in ("COSMIC_RUNE", "DEATH_RUNE"):
            require(not [obj for obj in boundaries if obj["level"] == level
                         and abs(obj["position"]["x"] - x) <= 1
                         and abs(obj["position"]["y"] - y) <= 1],
                    f"{name} revised landing has a nearby boundary; review collision")
            require(not [obj for obj in scenery if obj["level"] == level
                         and abs(obj["position"]["x"] - x) <= 1
                         and abs(obj["position"]["y"] - y) <= 1
                         and obj["sceneryId"] < len(object_defs)
                         and object_defs[obj["sceneryId"]].findtext("type") == "2"],
                    f"{name} revised landing has a nearby wall object; review collision")
        evidence.append({"rune": name, "x": x, "y": y, "level": level,
                         "terrain": list(tile), "altarPlacementId": altar["placementId"]})
    return evidence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, help="Write installed-placement evidence JSON")
    parser.add_argument("--render", type=Path, help="Check native guide bounds/scroll and save PNGs")
    args = parser.parse_args()
    evidence = check_locations()
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(evidence, indent=2) + "\n")
    if args.render:
        jar = ROOT / "Client_Base/Open_RSC_Client.jar"
        require(jar.is_file(), "build the actual client first")
        java = Path(os.environ.get("JAVA_HOME", "/home/justin/jdk8u482-b08")) / "bin"
        with tempfile.TemporaryDirectory(prefix="rune-altar-guide-") as directory:
            subprocess.run([str(java / "javac"), "-J-Xmx128m", "-cp", str(jar), "-d", directory,
                str(ROOT / "tests/myworld/RuneAltarGuideClientFixture.java")], check=True)
            subprocess.run([str(java / "java"), "-Xmx256m", "-XX:+UseSerialGC",
                "-Djava.awt.headless=true", "-Dspoiled_milk.opengl_sprite_overlay=true",
                "-Dspoiled_milk.opengl_sprite_overlay_mode=phased", "-Dspoiledmilk.openglWorldUiReplay=true",
                "-cp", os.pathsep.join([directory, str(jar)]),
                "orsc.RuneAltarGuideClientFixture", str(ROOT), str(args.render.resolve())],
                check=True, cwd=ROOT)
    print(f"PASS: {len(evidence['listedAltars'])} rune guide entries match the selected installed "
          f"world and interaction definitions; {len(evidence['otherAltarPlacements'])} additional "
          "altar placements preserved")


if __name__ == "__main__":
    main()
