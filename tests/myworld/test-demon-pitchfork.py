from pathlib import Path
import json
import re


ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    raise AssertionError(message)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def require_hidden_unique(drops: str, npc_name: str, item_name: str, rarity: str, label: str) -> None:
    require(
        f"addHiddenUniqueDrop(NpcId.{npc_name}.id(), ItemId.{item_name}.id(), 1, HiddenUniqueRarity.{rarity});" in drops,
        label,
    )


def load_items(path: Path) -> dict[int, dict]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    return {int(entry["id"]): entry for entry in payload["items"]}


def java_int_array(source: str, name: str) -> list[int]:
    match = re.search(
        rf"private static final int\[\] {name} = new int\[\] \{{([^}}]+)\}};",
        source,
    )
    require(match is not None, f"Client should define {name}")
    return [int(value.strip()) for value in match.group(1).split(",")]


def main() -> None:
    item_id = (ROOT / "server/src/com/openrsc/server/constants/ItemId.java").read_text(encoding="utf-8")
    appearance_id = (ROOT / "server/src/com/openrsc/server/constants/AppearanceId.java").read_text(encoding="utf-8")
    client_defs = (ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java").read_text(encoding="utf-8")
    mudclient = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text(encoding="utf-8")
    formula = (ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java").read_text(encoding="utf-8")
    pvm = (ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java").read_text(encoding="utf-8")
    pvp = (ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java").read_text(encoding="utf-8")
    drops = (ROOT / "server/src/com/openrsc/server/constants/NpcDrops.java").read_text(encoding="utf-8")
    custom_items = load_items(ROOT / "server/conf/server/defs/ItemDefsCustom.json")
    myworld_items = load_items(ROOT / "server/conf/server/defs/ItemDefsMyWorld.json")

    require("DEMON_PITCHFORK(3239)" in item_id, "ItemId should reserve Demon pitchfork")
    require("public static final int maxCustom = 3281;" in item_id, "maxCustom should include Demon pitchfork")
    require("SHEARS(1041, WEAPON)" in appearance_id, "AppearanceId should reserve universal shears")
    require("DEMON_PITCHFORK(1042, WEAPON)" in appearance_id, "AppearanceId should reserve Demon pitchfork")

    for source_name, items in (("ItemDefsCustom", custom_items), ("ItemDefsMyWorld", myworld_items)):
        pitchfork = items.get(3239)
        require(pitchfork is not None, f"{source_name} should define Demon pitchfork")
        require(pitchfork["name"] == "Demon pitchfork", f"{source_name} should keep the expected item name")
        require(pitchfork["weaponAimBonus"] == 31, f"{source_name} should use tier-9 long sword aim bonus")
        require(pitchfork["weaponPowerBonus"] == 31, f"{source_name} should use tier-9 long sword power bonus")
        require(pitchfork["appearanceID"] == 1042, f"{source_name} should use the custom pitchfork appearance")
    require(myworld_items[3239]["meleeOffense"] == 60, "ItemDefsMyWorld should use tier-9 long sword melee offense")
    require(myworld_items[3239]["weaponSpeed"] == 3, "ItemDefsMyWorld should use tier-9 long sword weapon speed")

    require('setCustomItemDefinition(3239, new ItemDef("Demon pitchfork"' in client_defs,
        "Client should define Demon pitchfork")
    require('new AnimationDef("demonpitchfork", "equipment", 0, 0, true, false, 0)); // 1042 - Demon pitchfork' in client_defs,
        "Client should expose the demon pitchfork equipment animation")
    require(
        'loadExternalCombatMainHandEquipmentSprite("demonpitchfork", getExternalEquipmentNumberedFolder("demon-pitchfork"),\n'
        '\t\t\tDEMON_PITCHFORK_EQUIPMENT_OFFSET_X, DEMON_PITCHFORK_EQUIPMENT_OFFSET_Y)' in mudclient,
        "Client should load the external demon pitchfork frames with dedicated anchors",
    )
    require(
        'loadExternalCombatMainHandEquipmentSprite("firesword", getExternalEquipmentNumberedFolder("fire-sword"),\n'
        '\t\t\tSWORD_EQUIPMENT_OFFSET_X, SWORD_EQUIPMENT_OFFSET_Y)' in mudclient,
        "Fire sword should retain the established sword anchors",
    )
    require(
        'loadExternalCombatMainHandEquipmentSprite("icesword", getExternalEquipmentNumberedFolder("ice-sword"),\n'
        '\t\t\tSWORD_EQUIPMENT_OFFSET_X, SWORD_EQUIPMENT_OFFSET_Y)' in mudclient,
        "Ice sword should retain the established sword anchors",
    )

    expected_pitchfork_x = [13, 12, 11, 8, 10, 22, 17, 25, 35, 32, 37, 46, 41, 41, 41, 3, 22, 2]
    expected_pitchfork_y = [16, 15, 13, 15, 15, 14, 16, 16, 14, 15, 13, 13, 15, 14, 11, -7, -4, 31]
    require(
        java_int_array(mudclient, "DEMON_PITCHFORK_EQUIPMENT_OFFSET_X") == expected_pitchfork_x,
        "Demon pitchfork should preserve the canonical spear hand anchor in every X frame",
    )
    require(
        java_int_array(mudclient, "DEMON_PITCHFORK_EQUIPMENT_OFFSET_Y") == expected_pitchfork_y,
        "Demon pitchfork should preserve the canonical spear hand anchor in every Y frame",
    )
    require(
        len(java_int_array(mudclient, "COMBAT_MAIN_HAND_EQUIPMENT_BOUND_WIDTH")) == 18,
        "Combat equipment should retain one render bound per frame",
    )

    pitchfork_frames = sorted((ROOT / "dev/myworld/assets/sprites/equipment/demon-pitchfork/numbered").glob("*.png"))
    require(len(pitchfork_frames) == 18, "Demon pitchfork should have 18 numbered equipment frames")
    require((ROOT / "dev/myworld/assets/sprites/items/inventory-ground/weapons/demon-pitchfork-icon.png").is_file(),
        "Demon pitchfork should have an external inventory/ground icon")
    shears_frames = sorted((ROOT / "dev/myworld/assets/sprites/equipment/shears/numbered").glob("*.png"))
    require(len(shears_frames) == 15, "Universal shears should have 15 no-combat equipment frames")
    require('loadExternalMainHandEquipmentSprite("shears", getExternalEquipmentNumberedFolder("shears"))' in mudclient,
        "Client should load the external universal shears frames")

    require("DEMON_PITCHFORK_HELL_BLAZE_PROC_CHANCE_PERCENT = 10" in formula,
        "Demon pitchfork should have a 10% Hell's Blaze proc chance")
    require("DEMON_PITCHFORK_HELL_BLAZE_MAX_HIT = 12" in formula,
        "Demon pitchfork should use the Hell's Blaze max-hit tier")
    require("hasEquipped(DEMON_PITCHFORK.id())" in formula,
        "Demon pitchfork proc should be gated by the equipped item")
    require("applyDemonPitchforkHellBlazeProc(attackerMob, targetMob, damage)" in pvm,
        "PvM melee should apply the Demon pitchfork proc")
    require("applyDemonPitchforkHellBlazeProc(hitter, target, damage)" in pvp,
        "PvP melee should apply the Demon pitchfork proc")
    require_hidden_unique(drops, "BALROG", "DEMON_PITCHFORK", "HIDDEN",
        "Balrog should drop Demon pitchfork at 1/512")
    require_hidden_unique(drops, "BLACK_DEMON", "DEMON_PITCHFORK", "RARE_UNIQUE",
        "Black Demon should drop Demon pitchfork at 1/1024")
    require_hidden_unique(drops, "GREATER_DEMON", "DEMON_PITCHFORK", "VERY_RARE_UNIQUE",
        "Greater Demon should drop Demon pitchfork at 1/2048")
    require_hidden_unique(drops, "LESSER_DEMON", "DEMON_PITCHFORK", "ULTRA_RARE_UNIQUE",
        "Lesser Demon should drop Demon pitchfork at 1/4096")
    require("addItemDrop(ItemId.DEMON_PITCHFORK.id()" not in drops,
        "Demon pitchfork should stay out of normal weighted NPC drop tables")

    print("PASS: Demon pitchfork and universal shears wiring validated")


if __name__ == "__main__":
    main()
