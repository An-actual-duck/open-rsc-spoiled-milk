#!/usr/bin/env python3
import json
import re
import sys
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
CLIENT_NPC_DEFS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
WORLD_NPC_EDIT_FILES = SERVER / "src/com/openrsc/server/util/WorldNpcEditFiles.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def wilderness_level(x: int, y: int) -> int:
    height = y // 944
    wild = 2203 - (y + (1776 - (944 * height)))
    if x + 2304 >= 2640:
        return 0
    return 1 + wild // 6 if wild > 0 else 0


def is_rangers_guild_overlay(loc: dict) -> bool:
    x = int(loc["start"]["X"])
    y = int(loc["start"]["Y"])
    return (
        484 <= x <= 515 and 432 <= y <= 480
        or 484 <= x <= 515 and 1376 <= y <= 1424
        or 484 <= x <= 515 and 3281 <= y <= 3310
    )


def is_karamja_volcano_fire_warrior_overlay(loc: dict) -> bool:
    x = int(loc["start"]["X"])
    y = int(loc["start"]["Y"])
    return int(loc["id"]) == 843 and 417 <= x <= 424 and 3544 <= y <= 3550


def is_frumscone_training_cage_overlay(loc: dict) -> bool:
    x = int(loc["start"]["X"])
    y = int(loc["start"]["Y"])
    return int(loc["id"]) in (203, 516) and 600 <= x <= 624 and 3578 <= y <= 3591


def is_elite_mining_guild_dragon_overlay(loc: dict) -> bool:
    x = int(loc["start"]["X"])
    y = int(loc["start"]["Y"])
    return int(loc["id"]) in (196, 844) and 248 <= x <= 280 and 3416 <= y <= 3444


def is_lumbridge_bank_overlay(loc: dict) -> bool:
    return (
        int(loc["id"]) == 95
        and int(loc["start"]["X"]) == 111
        and int(loc["start"]["Y"]) == 652
    )


def require_valid_drop_budget(drops: str, table_name: str) -> None:
    start = drops.find(f'new DropTable("{table_name}")')
    require(start >= 0, f"Missing drop table: {table_name}")
    end = drops.find("addEmptyDrop", start)
    require(end >= 0, f"Missing empty-drop budget terminator: {table_name}")
    section = drops[start:end]
    weights = re.findall(r"add(?:Item|Table)Drop\([^;]*,\s*(\d+)\);", section)
    total = sum(int(weight) for weight in weights)
    require(total <= 128, f"{table_name} exceeds the 128 drop-weight budget: {total}")


def drop_table_section(drops: str, table_name: str) -> str:
    start = drops.find(f'new DropTable("{table_name}")')
    require(start >= 0, f"Missing drop table: {table_name}")
    end = drops.find("addEmptyDrop", start)
    require(end >= 0, f"Missing empty-drop budget terminator: {table_name}")
    return drops[start:end]


def main() -> None:
    npc_ids = (SERVER / "src/com/openrsc/server/constants/NpcId.java").read_text(encoding="utf-8")
    drops = (SERVER / "src/com/openrsc/server/constants/NpcDrops.java").read_text(encoding="utf-8")
    cracker = (SERVER / "plugins/com/openrsc/server/plugins/authentic/misc/HalloweenCracker.java").read_text(encoding="utf-8")
    populator = (SERVER / "src/com/openrsc/server/database/WorldPopulator.java").read_text(encoding="utf-8")
    world_npc_edit_files = WORLD_NPC_EDIT_FILES.read_text(encoding="utf-8")
    audit = (ROOT / "tools/myworld/audit-npc-clusters.py").read_text(encoding="utf-8")

    require("GREY_KNIGHT(836)" in npc_ids, "Grey Knight NPC constant is missing")
    client_defs = CLIENT_NPC_DEFS.read_text(encoding="utf-8")
    require("applyMyWorldNpcDefinitionOverrides();" in client_defs,
            "Client should apply stable MyWorld NPC ids after loading base definitions")
    require('setCustomNpcDefinition(834, new NPCDef(\n\t\t\t"McGrubor", "Grumpy old McGruber"' in client_defs,
            "Client McGrubor definition should use its stable NPC id")
    require('setCustomNpcDefinition(835, new NPCDef(\n\t\t\t"Ash", "Groovy"' in client_defs,
            "Client Ash definition should use its stable NPC id")
    require('setCustomNpcDefinition(836, new NPCDef(\n\t\t\t"Grey Knight", "An armoured follower of Guthix"' in client_defs,
            "Client Grey Knight definition is missing from stable NPC overrides")
    require("GREY_KNIGHT_FALLBACK" not in client_defs,
            "Client Grey Knight should be a stable NPC override, not a fallback")
    for snippet in (
        'new DropTable("Black Knight (66, 189) Jailer (265) Lord Darquarius (266) Renegade Knight (277)")',
        "ItemId.BLACK_2_HANDED_SWORD.id()",
        "ItemId.BLACK_SCIMITAR.id()",
        "ItemId.BLACK_BATTLE_AXE.id()",
        "ItemId.BLACK_GAUNTLETS.id()",
        "ItemId.BLACK_GREAVES.id()",
        "ItemId.WHITE_2_HANDED_SWORD.id()",
        "ItemId.WHITE_SCIMITAR.id()",
        "ItemId.WHITE_BATTLE_AXE.id()",
        "ItemId.WHITE_GAUNTLETS.id()",
        "ItemId.WHITE_GREAVES.id()",
        'new DropTable("Grey Knight (836)")',
        "ItemId.GREY_LONG_SWORD.id()",
        "ItemId.GREY_KITE_SHIELD.id()",
        "ItemId.GREY_PLATE_MAIL_BODY.id()",
        "ItemId.GREY_PLATE_MAIL_LEGS.id()",
        "ItemId.GREY_GAUNTLETS.id()",
        "ItemId.GREY_GREAVES.id()",
        "NpcId.GREY_KNIGHT.id()",
    ):
        require(snippet in drops, f"Knight drops missing: {snippet}")
    for table_name, forbidden in (
        ("Black Knight (66, 189) Jailer (265) Lord Darquarius (266) Renegade Knight (277)", ("BLACK_CHAIN_MAIL", "MEDIUM_BLACK_HELMET", "BLACK_PLATE_MAIL_TOP", "BLACK_PLATED_SKIRT", "BLACK_AXE", "BLACK_THROWING_KNIFE")),
        ("White Knight (102)", ("WHITE_CHAIN_MAIL", "MEDIUM_WHITE_HELMET", "WHITE_PLATE_MAIL_TOP", "WHITE_PLATED_SKIRT")),
        ("Grey Knight (836)", ("GREY_CHAIN_MAIL", "MEDIUM_GREY_HELMET", "GREY_PLATE_MAIL_TOP", "GREY_PLATED_SKIRT")),
    ):
        section = drop_table_section(drops, table_name)
        for item_name in forbidden:
            require(item_name not in section, f"{table_name} should not drop retired/hidden equipment: {item_name}")
    require("ItemId.BLACK_AXE.id()" not in drop_table_section(drops, "Dark Warrior (199)"),
            "Dark Warriors should not drop the hidden black hatchet")
    for hidden in ("BLACK_PLATE_MAIL_TOP", "BLACK_AXE", "BLACK_THROWING_KNIFE"):
        require(hidden not in cracker, f"Halloween cracker should not award hidden black equipment: {hidden}")
    require("ItemId.BLACK_GAUNTLETS.id()" in cracker and "ItemId.BLACK_GREAVES.id()" in cracker,
            "Halloween cracker black prize set should use the active hand/foot armour")
    require_valid_drop_budget(drops, "Black Knight (66, 189) Jailer (265) Lord Darquarius (266) Renegade Knight (277)")
    require_valid_drop_budget(drops, "White Knight (102)")
    require_valid_drop_budget(drops, "Grey Knight (836)")

    custom_defs = json.loads((SERVER / "conf/server/defs/NpcDefsCustom.json").read_text(encoding="utf-8"))
    grey = next((npc for npc in custom_defs["npcs"] if npc["id"] == 836), None)
    require(grey is not None, "Grey Knight definition is missing")
    require(grey["name"] == "Grey Knight" and grey["combatlvl"] == 56, "Grey Knight identity is incorrect")
    require(grey["attackable"] == 1 and grey["aggressive"] == 0, "Grey Knight combat disposition is incorrect")
    require('"Grey Knight", "An armoured follower of Guthix", "", 55, 58, 52, 60, true' in client_defs,
            "Client Grey Knight identity/stats/disposition are incorrect")
    require("new int[]{19, 34, 43, -1, 49" in client_defs,
            "Client Grey Knight should use knight armour sprites")
    require("8421504, 8421504" in client_defs,
            "Client Grey Knight should use grey armour colours")
    myworld_defs = json.loads((SERVER / "conf/server/defs/NpcDefsMyWorld.json").read_text(encoding="utf-8"))
    grey_override = next((npc for npc in myworld_defs["npcs"] if npc["id"] == 836), None)
    require(grey_override is not None, "Grey Knight MyWorld combat override is missing")
    require(
        grey_override["meleeDefenseMultiplier"] == 1.0
        and grey_override["rangedDefenseMultiplier"] == 0.1
        and grey_override["magicDefenseMultiplier"] == 0.1,
        "Grey Knight should follow the armored knight defense profile",
    )

    overlay_path = SERVER / "conf/server/defs/locs/MyWorldNpcLocs.json"
    overlay = json.loads(overlay_path.read_text(encoding="utf-8"))["npclocs"]
    karamja_fire_warriors = [loc for loc in overlay if is_karamja_volcano_fire_warrior_overlay(loc)]
    require(len(karamja_fire_warriors) == 4, "Karamja volcano should have exactly 4 Fire warrior spawns")
    fire_warrior_starts = {
        (int(loc["start"]["X"]), int(loc["start"]["Y"]))
        for loc in karamja_fire_warriors
    }
    require(
        fire_warrior_starts == {(418, 3546), (420, 3547), (422, 3548), (424, 3546)},
        "Karamja Fire warrior starts should be clustered around 420,3547",
    )
    training_cage_spawns = [loc for loc in overlay if is_frumscone_training_cage_overlay(loc)]
    training_cage_counts = Counter(loc["id"] for loc in training_cage_spawns)
    require(
        training_cage_counts == {516: 2, 203: 4},
        f"Frumscone training cage overlay should have 2 target-practice zombies and 4 baby blue dragons: {dict(training_cage_counts)}",
    )
    mining_guild_dragon_spawns = [loc for loc in overlay if is_elite_mining_guild_dragon_overlay(loc)]
    mining_guild_dragon_counts = Counter(loc["id"] for loc in mining_guild_dragon_spawns)
    require(
        mining_guild_dragon_counts == {844: 1, 196: 4},
        f"Elite Mining Guild overlay should have 1 Elder Green Dragon and 4 Green Dragons: {dict(mining_guild_dragon_counts)}",
    )
    mining_guild_dragon_starts = {
        (int(loc["id"]), int(loc["start"]["X"]), int(loc["start"]["Y"]))
        for loc in mining_guild_dragon_spawns
    }
    require(
        mining_guild_dragon_starts == {
            (844, 263, 3430),
            (196, 259, 3431),
            (196, 269, 3428),
            (196, 254, 3427),
            (196, 274, 3422),
        },
        "Elite Mining Guild dragon starts should guard the dragon sulfur field",
    )
    population_overlay = [
        loc for loc in overlay
        if not is_rangers_guild_overlay(loc)
        and not is_karamja_volcano_fire_warrior_overlay(loc)
        and not is_frumscone_training_cage_overlay(loc)
        and not is_elite_mining_guild_dragon_overlay(loc)
        and not is_lumbridge_bank_overlay(loc)
    ]
    counts = Counter(loc["id"] for loc in population_overlay)
    expected_counts = {
        836: 4,
        199: 4,
        184: 6,
        57: 4,
        251: 4,
        188: 4,
        53: 4,
        232: 4,
        296: 4,
        189: 4,
        190: 5,
        201: 4,
        22: 4,
        294: 4,
        243: 4,
        342: 4,
        293: 4,
        555: 3,
        358: 1,
        311: 3,
        787: 3,
        104: 3,
    }
    require(counts == expected_counts, f"Unexpected MyWorld Wilderness/God Knight spawn overlay counts: {dict(counts)}")
    heroes_guild_spawns = [
        loc for loc in population_overlay
        if 365 <= loc["start"]["X"] <= 377 and 3264 <= loc["start"]["Y"] <= 3276
    ]
    require(len(heroes_guild_spawns) == 6, "Heroes' Guild overlay should add exactly 6 hostiles")
    for loc in (loc for loc in population_overlay if loc["id"] in (199, 184) and loc not in heroes_guild_spawns):
        require(wilderness_level(loc["start"]["X"], loc["start"]["Y"]) > 0,
                f"Intended Wilderness addition is outside the Wilderness: {loc}")
    wilderness_additions = [loc for loc in population_overlay if loc["id"] != 836 and loc not in heroes_guild_spawns]
    require(len(wilderness_additions) == 74, "Wilderness overlay should add exactly 74 hostiles")
    for loc in wilderness_additions:
        require(wilderness_level(loc["start"]["X"], loc["start"]["Y"]) > 0,
                f"MyWorld Wilderness addition is outside the Wilderness: {loc}")

    require("WorldNpcEditFiles.npcLocsPath" in populator and "WANT_MYWORLD" in populator
            and "MyWorldNpcLocs.json" in world_npc_edit_files,
            "Runtime location loader does not include the MyWorld NPC overlay")
    require("MyWorldNpcLocs.json" in audit and "--wilderness-only" in audit
            and "Population By Wilderness Level" in audit,
            "Population audit does not cover the MyWorld Wilderness overlay")

    print("PASS: god knight drops and Wilderness population overlay validated")


if __name__ == "__main__":
    main()
