#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
ITEM_ID = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
MYWORLD_ITEM_ID = ROOT / "server/src/com/openrsc/server/constants/custom/MyWorldItemId.java"
CUSTOM_ITEMS = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
MYWORLD_ITEMS = ROOT / "server/conf/server/defs/ItemDefsMyWorld.json"
SMITHING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smithing.java"
SKILL_GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
PVM_MELEE = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java"

SCYTHES = {
    3181: "Tin Scythe",
    3182: "Copper Scythe",
    3183: "Bronze Scythe",
    3184: "Iron Scythe",
    3185: "Steel Scythe",
    3186: "Mithril Scythe",
    3187: "Titan Steel Scythe",
    3188: "Adamantite Scythe",
    3189: "Orichalcum Scythe",
    3190: "Rune Scythe",
}

BLESSED_SCYTHES = {
    3232: "Black Scythe",
    3233: "White Scythe",
    3234: "Grey Scythe",
}

EXPECTED_SCYTHE_OFFENSE = {
    3181: 15,
    3182: 24,
    3183: 32,
    3184: 49,
    3185: 65,
    3186: 82,
    3187: 91,
    3188: 99,
    3189: 108,
    3190: 116,
}

EXPECTED_SCYTHE_REQUIREMENTS = {
    3181: 1,
    3182: 8,
    3183: 15,
    3184: 22,
    3185: 30,
    3186: 38,
    3187: 46,
    3188: 54,
    3189: 62,
    3190: 70,
}

MATCHING_TWO_HANDER_OFFENSE = {
    3181: 20,
    3182: 31,
    3183: 42,
    3184: 64,
    3185: 86,
    3186: 108,
    3187: 119,
    3188: 130,
    3189: 141,
    3190: 152,
}


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_items(path: Path, key: str) -> dict[int, dict]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    entries = payload.get(key)
    if not isinstance(entries, list):
        fail(f"{path.name} missing {key} array")
    return {int(entry["id"]): entry for entry in entries}


def main() -> None:
    item_id_text = ITEM_ID.read_text(encoding="utf-8")
    myworld_item_id_text = MYWORLD_ITEM_ID.read_text(encoding="utf-8")
    custom_items = load_items(CUSTOM_ITEMS, "items")
    myworld_items = load_items(MYWORLD_ITEMS, "items")
    smithing = SMITHING.read_text(encoding="utf-8")
    guide = SKILL_GUIDE.read_text(encoding="utf-8")
    client_defs = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    pvm_melee = PVM_MELEE.read_text(encoding="utf-8")

    require("public static final int maxCustom = 3309;" in item_id_text, "ItemId.maxCustom should include Demon pitchfork")
    for item_id, name in SCYTHES.items():
        enum_name = name.upper().replace(" ", "_")
        require(f"{enum_name}({item_id})" in item_id_text, f"Missing ItemId enum for {name}")
        custom = custom_items.get(item_id)
        require(custom is not None, f"ItemDefsCustom missing {name}")
        require(custom["name"] == name, f"ItemDefsCustom wrong name for {item_id}")
        require(custom["isWearable"] == 1, f"{name} should be wearable")
        require(custom["wearSlot"] == 4, f"{name} should use mainhand weapon slot")
        require(custom["wearableID"] & 8 == 8, f"{name} should conflict with shields as a two-handed weapon")
        require(custom["appearanceID"] == 1033, f"{name} should use custom white combat scythe appearance")
        expected_offense = EXPECTED_SCYTHE_OFFENSE[item_id]
        require(custom["weaponAimBonus"] == expected_offense, f"{name} should use tuned two-handed aim")
        require(custom["weaponPowerBonus"] == expected_offense, f"{name} should use tuned two-handed power")
        override = myworld_items.get(item_id)
        require(override is not None, f"ItemDefsMyWorld missing combat override for {name}")
        require(override["weaponSpeed"] == 3, f"{name} should use normal weapon speed")
        require(override["meleeOffense"] == expected_offense, f"{name} should use tuned MyWorld melee offense")
        require(override["requiredSkillID"] == 0, f"{name} should require Melee like other metal melee weapons")
        require(override["requiredLevel"] == EXPECTED_SCYTHE_REQUIREMENTS[item_id],
                f"{name} should use the matching metal melee tier requirement")
        require(override["meleeOffense"] < MATCHING_TWO_HANDER_OFFENSE[item_id],
                f"{name} should remain just below the matching two-handed sword")

    require('"Hatchet", "Pickaxe", "Shears", "Battle Axe (3 bars)", "Scythe (3 bars)"' in smithing,
            "Legacy Smithing axe menu should expose 3-bar scythes")
    require("case 22:" in smithing and "def.bars = 3;" in smithing and "getModernScytheId(barId)" in smithing,
            "Modern Smithing should define 3-bar scythe recipes")
    require("addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), 22);" in smithing,
            "Production Smithing should expose scythes")
    for item_id, name in SCYTHES.items():
        require(str(item_id) in guide and 'name + " scythe - 3 bars"' in guide,
                f"Smithing guide missing {name}")
        require(f'"{name}"' in client_defs, f"Client custom definitions missing {name}")

    for item_id, name in BLESSED_SCYTHES.items():
        enum_name = name.upper().replace(" ", "_")
        require(f"{enum_name}({item_id})" in item_id_text, f"Missing ItemId enum for {name}")
        custom = custom_items.get(item_id)
        require(custom is not None, f"ItemDefsCustom missing {name}")
        require(custom["name"] == name, f"ItemDefsCustom wrong name for {item_id}")
        require(custom["isWearable"] == 1, f"{name} should be wearable")
        require(custom["wearSlot"] == 4, f"{name} should use mainhand weapon slot")
        require(custom["wearableID"] & 8 == 8, f"{name} should conflict with shields as a two-handed weapon")
        require(custom["appearanceID"] == 1033, f"{name} should use custom white combat scythe appearance")
        override = myworld_items.get(item_id)
        require(override is not None, f"ItemDefsMyWorld missing combat override for {name}")
        require(override["weaponSpeed"] == 3, f"{name} should use normal weapon speed")
        require(override["meleeOffense"] == EXPECTED_SCYTHE_OFFENSE[3185], f"{name} should mirror steel scythe offense")
        require(override["requiredSkillID"] == 0, f"{name} should require Melee like other metal melee weapons")
        require(override["requiredLevel"] == EXPECTED_SCYTHE_REQUIREMENTS[3185],
                f"{name} should use the steel scythe melee requirement")
        require(f'"{name}"' in client_defs, f"Client custom definitions missing {name}")

    require('new AnimationDef("scythe", "equipment", 0xF0F0F0, 0, true, false, 0)' in client_defs,
            "Client should define a white held combat scythe variant")
    require("0x7585A8, 0x5BC878, 0x5A3F7D, 0x86D7FF" in client_defs,
            "Orichalcum scythe icon mask should use the established deep purple orichalcum palette")
    require("private static final int[] SCYTHE_IDS" in pvm_melee, "PvM melee should identify scythe weapons")
    require("EXALTED_RUNE_SCYTHE = 3270" in myworld_item_id_text,
            "Exalted Rune scythe custom identity should remain stable")
    require("MyWorldItemId.EXALTED_RUNE_SCYTHE" in pvm_melee,
            "PvM melee should recognize the Exalted Rune scythe for AoE cleave")
    for enum_name in ("BLACK_SCYTHE", "WHITE_SCYTHE", "GREY_SCYTHE"):
        require(f"ItemId.{enum_name}.id()" in pvm_melee, f"PvM melee should recognize {enum_name} as a scythe")
    require("applyScytheNpcCleave((Player) attackerMob, (Npc) targetMob)" in pvm_melee,
            "PvM melee should run scythe cleave from player-vs-NPC combat")
    require("Summoning.isSummon(npc)" in pvm_melee, "Scythe cleave must exclude summons")
    require("!npc.getDef().isAttackable()" in pvm_melee, "Scythe cleave must exclude non-attackable NPCs")
    require("targetMob.isNpc()" in pvm_melee, "Scythe cleave should stay NPC-only in the first pass")
    require("delayTicks += scytheTargetsHit - 1;" in pvm_melee,
            "Scythe cleave should add follow-up delay per extra target")
    require("xDiff <= 1 && yDiff <= 1 && (xDiff != 0 || yDiff != 0)" in pvm_melee,
            "Scythe cleave should use adjacent tiles around the player")
    require("inflictScytheCleaveDamage(player, npc, damage)" in pvm_melee,
            "Scythe secondary cleave hits should not reset the primary combat event")
    require("triggerScytheCleaveAggro(player, npc)" in pvm_melee,
            "Scythe secondary cleave hits should wake idle targets")
    require("PvmMeleeEvent existingEvent = npc.getPvmMeleeEvent();" in pvm_melee
            and "existingEvent != null && existingEvent.isRunning()" in pvm_melee
            and "npc.startPvmCounterCombat(player);" in pvm_melee,
            "Scythe cleave aggro should avoid hijacking NPCs already fighting in PvM")

    print("PASS: AoE scythe line validated")


if __name__ == "__main__":
    main()
