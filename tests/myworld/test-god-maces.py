#!/usr/bin/env python3
"""Validate god mace item, sprite, and equip requirement wiring."""

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def load_items(path: Path) -> dict[int, dict]:
    return {int(entry["id"]): entry for entry in json.loads(path.read_text(encoding="utf-8"))["items"]}


def main() -> None:
    item_id = (ROOT / "server/src/com/openrsc/server/constants/ItemId.java").read_text(encoding="utf-8")
    appearance_id = (ROOT / "server/src/com/openrsc/server/constants/AppearanceId.java").read_text(encoding="utf-8")
    client_defs = (ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java").read_text(encoding="utf-8")
    server_handler = (ROOT / "server/src/com/openrsc/server/external/EntityHandler.java").read_text(encoding="utf-8")
    equipment = (ROOT / "server/src/com/openrsc/server/model/container/Equipment.java").read_text(encoding="utf-8")
    custom_items = load_items(ROOT / "server/conf/server/defs/ItemDefsCustom.json")
    myworld_items = load_items(ROOT / "server/conf/server/defs/ItemDefsMyWorld.json")

    expected = {
        3252: ("SARADOMIN_MACE", "Saradomin mace", 1044, 1043, "SARADOMIN", "saradomin-mace-icon", "0xFFD84A"),
        3253: ("ZAMORAK_MACE", "Zamorak mace", 1045, 1044, "ZAMORAK", "zamorak-mace-icon", "0x8A2BE2"),
        3254: ("GUTHIX_MACE", "Guthix mace", 1046, 1045, "GUTHIX", "guthix-mace-icon", "0xC2A678"),
    }

    require("public static final int maxCustom = 3311;" in item_id, "ItemId.maxCustom should include current custom items")
    require("GOD_MACE_PRAYER_REQUIREMENT = 80" in equipment, "God maces should require 80 Worship")
    require("GOD_MACE_DEVOTION_REQUIREMENT" not in equipment, "God maces should not require current devotion to wield")
    require("Devotion.getDevotionLevel(player, godLine)" not in equipment, "God mace devotion is an acquisition gate, not a wield gate")

    for item_id_value, (constant, name, appearance, animation_index, god_line, icon, animation_color) in expected.items():
        require(f"{constant}({item_id_value})" in item_id, f"ItemId missing {constant}")
        require(f"{constant}({appearance}, WEAPON)" in appearance_id, f"AppearanceId missing {constant}")
        require(f"ItemId.{constant}.id()" in server_handler, f"Server handler should map {name} custom appearance")
        require(f"itemId == ItemId.{constant}.id()" in equipment, f"Equipment should identify {name}")
        require(f"PrayerCatalog.GodLine.{god_line}" in equipment, f"{name} should be aligned to {god_line}")
        require(f'setCustomItemDefinition({item_id_value}, new ItemDef("{name}"' in client_defs, f"Client missing {name}")
        require(f'"external-png:{icon}"' in client_defs, f"Client should use external icon for {name}")
        require(f'new AnimationDef("mace", "equipment", {animation_color}, 0, true, false, 0)); // {animation_index} - {name}' in client_defs, f"Client should use recolored mace animation for {name}")
        require((ROOT / f"dev/myworld/assets/sprites/items/inventory-ground/weapons/{icon}.png").is_file(), f"Missing icon asset for {name}")

        for source_name, items in (("ItemDefsCustom", custom_items), ("ItemDefsMyWorld", myworld_items)):
            item = items.get(item_id_value)
            require(item is not None, f"{source_name} missing {name}")
            require(item["name"] == name, f"{source_name} wrong name for {name}")
            require(item["meleeOffense"] == 72, f"{source_name} should use tier-11 one-handed melee offense for {name}")
            require(item["weaponSpeed"] == 3, f"{source_name} should keep mace speed for {name}")
            require(item["requiredSkillID"] == 0 and item["requiredLevel"] == 80, f"{source_name} should require 80 Attack for {name}")
            require(item["isWearable"] == 1 and item["wearableID"] == 16 and item["wearSlot"] == 4, f"{source_name} should make {name} a one-handed mainhand weapon")
            require(item["appearanceID"] == appearance, f"{source_name} should use custom appearance for {name}")
            require(item["weaponAimBonus"] == 60 and item["weaponPowerBonus"] == 60, f"{source_name} should use dragon-tier aim/power for {name}")
            require(item["prayerBonus"] == 11, f"{source_name} should use tier-11 mace prayer bonus for {name}")
            require(item["basePrice"] == 120000, f"{source_name} should use dragon-tier base price for {name}")

        effective_item = dict(custom_items[item_id_value])
        effective_item.update(myworld_items[item_id_value])
        require(effective_item["isUntradable"] == 1, f"{name} should not be tradeable")
        require(
            f'setCustomItemDefinition({item_id_value}, new ItemDef("{name}",'
            in client_defs
            and f'"external-png:{icon}", false, true, 16, 0, true, true, true, {item_id_value}))'
            in client_defs,
            f"Client should mark {name} untradeable",
        )

    print("PASS: god mace wiring validated")


if __name__ == "__main__":
    main()
