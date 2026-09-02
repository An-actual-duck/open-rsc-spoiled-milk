#!/usr/bin/env python3
"""Guard the ordinary Green Dragon / quest-only Elvarg split."""

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PACKAGE_ID = "d037a81117d359bd1e92147ced077f566e2ce6fdaa424e949f8bf6f83e6c3b2b"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def load(path: str):
    return json.loads(read(path))


def npc_by_id(entries: list[dict], npc_id: int) -> dict:
    matches = [entry for entry in entries if entry.get("id") == npc_id]
    require(len(matches) == 1, f"expected one NPC definition for {npc_id}, found {len(matches)}")
    return matches[0]


def block(source: str, start: str, end: str) -> str:
    match = re.search(re.escape(start) + r"(.*?)" + re.escape(end), source, re.DOTALL)
    require(match is not None, f"could not find source block beginning {start}")
    return match.group(1)


def main() -> None:
    base_defs = load("server/conf/server/defs/NpcDefs.json")["npcs"]
    ordinary_defs = load("server/conf/server/defs/MyWorldNpcDefs.json")["npcs"]
    overrides = load("server/conf/server/defs/NpcDefsMyWorld.json")["npcs"]
    elvarg = npc_by_id(base_defs, 196)
    ordinary = npc_by_id(ordinary_defs, 862)

    require(elvarg["name"] == "Dragon" and elvarg["description"] == "A powerful and ancient dragon",
            "ID 196 must retain the vanilla Dragon/Elvarg identity")
    require([elvarg[key] for key in ("attack", "strength", "hits", "defense")] == [110] * 4,
            "ID 196 must retain vanilla 110 combat stats")
    require(not any(entry.get("id") == 196 for entry in overrides),
            "MyWorld overrides must not turn Elvarg into the enhanced ordinary dragon")
    require(ordinary["name"] == "Green Dragon" and ordinary["strength"] == 98,
            "ID 862 must own the enhanced ordinary Green Dragon definition")
    require(ordinary["sprites1"] == elvarg["sprites1"] and ordinary["camera1"] == elvarg["camera1"],
            "ordinary Green Dragon must preserve the established green-dragon appearance")
    ordinary_override = npc_by_id(overrides, 862)
    require(ordinary_override.get("rangedDefenseMultiplier") == 0.75,
            "ordinary Green Dragon must inherit the enhanced ranged-defense profile")

    constants = read("server/src/com/openrsc/server/constants/NpcId.java")
    loader = read("server/src/com/openrsc/server/external/EntityHandler.java")
    client = read("Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java")
    require("GREEN_DRAGON(862)" in constants, "NpcId must reserve ordinary Green Dragon ID 862")
    require('loadNpcs(getServer().getConfig().CONFIG_DIR + "/defs/MyWorldNpcDefs.json")' in loader,
            "server must append the ordinary definition in MyWorld mode")
    require("setCustomNpcDefinition(862, new NPCDef(" in client,
            "client must define ordinary Green Dragon ID 862")

    drops = read("server/src/com/openrsc/server/constants/NpcDrops.java")
    hidden = block(drops, "private void createHiddenUniqueDrops()", "private void addHiddenUniqueDrop(final int npcId")
    require("addHiddenUniqueDrop(NpcId.GREEN_DRAGON.id(), ItemId.EARTH_SWORD.id(), 1, HiddenUniqueRarity.ULTRA_RARE_UNIQUE);" in hidden,
            "ordinary Green Dragon must own the Earth Sword unique")
    require("addHiddenUniqueDrop(NpcId.GREEN_DRAGON.id(), ItemId.RAW_DRAGON_METAL.id(), 1, 1, 1024);" in hidden,
            "ordinary Green Dragon must own the 1/1024 raw dragon metal roll")
    require("addHiddenUniqueDrop(NpcId.DRAGON.id()," not in hidden,
            "Elvarg must have no hidden unique rolls")
    elvarg_drops = block(drops, 'new DropTable("Elvarg (196)");', 'new DropTable("Green Dragon (862)");')
    require("addItemDrop" not in elvarg_drops and "addTableDrop" not in elvarg_drops,
            "Elvarg's weighted drop table must remain empty")
    green_drops = block(drops, 'new DropTable("Green Dragon (862)");', 'new DropTable("Dark Warrior (199)");')
    for expected in (
        "addTableDrop(herbDropTable, 10)", "addTableDrop(rareDropTable, 5)",
        "ItemId.COINS.id(), 500, 18", "ItemId.EARTH_RUNE.id(), 250, 10",
        "ItemId.RUNE_PLATE_MAIL_LEGS.id(), 1, 1",
        "addEmptyDrop(128 - currentNpcDrops.getTotalWeight())",
    ):
        require(expected in green_drops, f"ordinary drop table missing {expected}")
    require("this.dragonNpcs.add(NpcId.DRAGON.id());" in drops
            and "this.dragonNpcs.add(NpcId.GREEN_DRAGON.id());" in drops,
            "both variants must drop dragon bones")
    require("addGuaranteedDrop(NpcId.DRAGON.id(), ItemId.DRAGON_HIDE.id()" in drops
            and "addGuaranteedDrop(NpcId.GREEN_DRAGON.id(), ItemId.DRAGON_HIDE.id()" in drops,
            "both variants must guarantee ordinary green dragon hide")

    quest = read("server/plugins/com/openrsc/server/plugins/authentic/quests/free/DragonSlayer.java")
    prayer = read("server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/ElvargPrayerDrain.java")
    require("NpcId.DRAGON" in quest and "GREEN_DRAGON" not in quest,
            "Dragon Slayer hooks must remain exclusive to Elvarg")
    require("NpcId.DRAGON.id()" in prayer and "GREEN_DRAGON" not in prayer,
            "Elvarg prayer drain must not transfer to the ordinary Green Dragon")

    profile = read("server/src/com/openrsc/server/model/entity/npc/NpcAttackStyleProfile.java")
    formula = read("server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java")
    element = block(profile, "private static NpcMagicElement getDragonMagicElement", "private static NpcMagicElement randomElement")
    vulnerability = block(formula, "private static boolean isIceSwordVulnerable", "private static int applyEarthSwordElementalBonus")
    require("case 862:" in element and "case 196:" not in element,
            "Earth magic must belong to ordinary Green Dragons, not Elvarg")
    require("NpcId.GREEN_DRAGON.id()" in vulnerability and "NpcId.DRAGON.id()" not in vulnerability,
            "Earth vulnerability must belong to ordinary Green Dragons, not Elvarg")

    points = read("server/src/com/openrsc/server/content/RangersGuildPoints.java")
    families = load("server/conf/server/defs/extras/MonsterSlayer.json")["families"]
    require("npcId == NpcId.GREEN_DRAGON.id()" in points and "NpcId.DRAGON" not in points,
            "Rangers Guild points must recognize only ordinary Green Dragons")
    green_family = next(entry for entry in families if entry.get("key") == "green_dragon")
    require(green_family["npcIds"] == [862], "Monster Slayer green_dragon family must use only ID 862")

    source_spawns = load("server/conf/server/defs/locs/MyWorldNpcLocs.json")["npclocs"]
    fallback_spawns = load("server/world-builder-fallback/placements/MyWorldNpcLocs.json")["npclocs"]
    expected_mining = {(259, 3431), (269, 3428), (254, 3427), (274, 3422)}
    for entries, label in ((source_spawns, "source"), (fallback_spawns, "fallback")):
        green = [entry for entry in entries if entry.get("id") == 862]
        require({(entry["start"]["X"], entry["start"]["Y"]) for entry in green} == expected_mining,
                f"{label} placements must contain the four migrated Mining Guild dragons")
        require(not any(entry.get("id") == 196 for entry in entries),
                f"{label} MyWorld placements must contain no quest Elvarg")

    package_path = f"world-builder/packages/{PACKAGE_ID}/package/placements/global/lm1.json"
    server_package = load("server/" + package_path)["npcs"]
    client_package = load("Client_Base/" + package_path)["npcs"]
    require(server_package == client_package, "server/client active layered NPC placements must be identical")
    green = [entry for entry in server_package if entry.get("npcId") == 862]
    elvargs = [entry for entry in server_package if entry.get("npcId") == 196]
    mining = [entry for entry in green if entry["placementId"].startswith("spoiled-milk.npc.myworldnpclocs-json.")]
    rangers = [entry for entry in green if entry["placementId"].startswith("world-builder.authored.npc.lm1.")]
    require(len(green) == 6 and len(mining) == 4 and len(rangers) == 2,
            "active package must migrate exactly four Mining Guild and two Rangers Guild dragons")
    require(len(elvargs) == 1 and elvargs[0]["placementId"] == "spoiled-milk.npc.npclocs-json.003125"
            and elvargs[0]["start"] == {"x": 418, "y": 647},
            "active package must preserve only the authentic Crandor/Elvarg placement")

    print("PASS: ordinary Green Dragon is isolated from vanilla quest Elvarg")


if __name__ == "__main__":
    main()
