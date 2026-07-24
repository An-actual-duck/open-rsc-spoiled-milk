#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def block_between(text: str, start: str, end: str) -> str:
    start_index = text.index(start)
    end_index = text.index(end, start_index + len(start))
    return text[start_index:end_index]


def load_items(path: Path) -> dict[int, dict]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    entries = payload.get("items", payload.get("item", []))
    return {item["id"]: item for item in entries}


def main() -> None:
    constants = (SERVER / "src/com/openrsc/server/constants/ItemId.java").read_text(encoding="utf-8")
    client = (ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java").read_text(encoding="utf-8")
    mudclient = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text(encoding="utf-8")
    do_skill = (ROOT / "Client_Base/src/com/openrsc/interfaces/misc/DoSkillInterface.java").read_text(encoding="utf-8")
    crafting = (SERVER / "plugins/com/openrsc/server/plugins/authentic/skills/crafting/Crafting.java").read_text(encoding="utf-8")
    crafting_shop = (SERVER / "plugins/com/openrsc/server/plugins/authentic/npcs/CraftingEquipmentShops.java").read_text(encoding="utf-8")
    smelting = (SERVER / "plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smelting.java").read_text(encoding="utf-8")
    blessed = (SERVER / "plugins/com/openrsc/server/plugins/custom/myworld/skills/prayer/BlessedSymbols.java").read_text(encoding="utf-8")
    destroy = (SERVER / "plugins/com/openrsc/server/plugins/custom/myworld/skills/prayer/DestroyOpposingBlessedObject.java").read_text(encoding="utf-8")
    devotion = (SERVER / "src/com/openrsc/server/content/Devotion.java").read_text(encoding="utf-8")
    equipment = (SERVER / "src/com/openrsc/server/model/container/Equipment.java").read_text(encoding="utf-8")
    npc_drops = (SERVER / "src/com/openrsc/server/constants/NpcDrops.java").read_text(encoding="utf-8")
    brother_jered = (SERVER / "plugins/com/openrsc/server/plugins/authentic/npcs/edgeville/BrotherJered.java").read_text(encoding="utf-8")
    scorpius = (SERVER / "plugins/com/openrsc/server/plugins/authentic/npcs/ardougne/west/SpiritOfScorpius.java").read_text(encoding="utf-8")
    base_items = load_items(SERVER / "conf/server/defs/ItemDefs.json")
    custom_items = load_items(SERVER / "conf/server/defs/ItemDefsCustom.json")

    for name, item_id in {
        "GUTHIX_SYMBOL_MOULD": 3172,
        "UNSTRUNG_GUTHIX_SYMBOL": 3173,
        "UNBLESSED_GUTHIX_SYMBOL": 3174,
        "GUTHIX_SYMBOL": 3175,
    }.items():
        require(f"{name}({item_id})" in constants, f"missing ItemId constant {name}")
        require(item_id in custom_items, f"missing custom item definition {item_id}")
        require(f"setCustomItemDefinition({item_id}," in client, f"missing client definition {item_id}")

    for item_id in (385, 1029):
        require(base_items[item_id]["prayerBonus"] == 0, f"{base_items[item_id]['name']} should no longer grant prayer points")
    require(custom_items[3175]["prayerBonus"] == 0, "Guthix Symbol should not grant prayer points")
    require(base_items[44]["name"] == "Unstrung symbol of Saradomin", "Saradomin unstrung symbol name mismatch")
    require(base_items[45]["name"] == "Unblessed symbol of Saradomin", "Saradomin unblessed symbol name mismatch")
    require(base_items[385]["name"] == "Symbol of Saradomin", "Saradomin blessed symbol name mismatch")
    require(base_items[386]["name"] == "Saradomin symbol mould", "Saradomin symbol mould name mismatch")
    require(base_items[1026]["name"] == "Zamorak symbol mould", "Zamorak symbol mould name mismatch")
    require(base_items[1027]["name"] == "Unstrung symbol of Zamorak", "Zamorak unstrung symbol name mismatch")
    require(base_items[1028]["name"] == "Unblessed symbol of Zamorak", "Zamorak unblessed symbol name mismatch")
    require(base_items[1029]["name"] == "Symbol of Zamorak", "Zamorak blessed symbol name mismatch")
    require(custom_items[3173]["name"] == "Unstrung symbol of Guthix", "Guthix unstrung symbol name mismatch")
    require(custom_items[3174]["name"] == "Unblessed symbol of Guthix", "Guthix unblessed symbol name mismatch")
    require(custom_items[3175]["name"] == "Symbol of Guthix", "Guthix blessed symbol name mismatch")
    require(custom_items[3174]["appearanceID"] == 995 and custom_items[3175]["appearanceID"] == 995,
            "Guthix worn symbols should use the external Guthix symbol appearance")
    require('new ItemDef("Saradomin symbol mould"' in client, "Client Saradomin mould name mismatch")
    require('new ItemDef("Zamorak symbol mould"' in client, "Client Zamorak mould name mismatch")
    require("external-png:guthix-symbol-mould" in client, "Guthix mould should use the new inventory sprite")
    require("external-png:unstrung-symbol-of-guthix" in client, "Guthix unstrung symbol should use the new inventory sprite")
    require("external-png:unblessed-symbol-of-guthix" in client, "Guthix unblessed symbol should use the new inventory sprite")
    require("external-png:symbol-of-guthix" in client, "Guthix blessed symbol should use the new inventory sprite")
    require('new AnimationDef("guthsymbol", "equipment"' in client, "Guthix worn symbol animation missing")
    require('loadExternalNeckEquipmentSprite("guthsymbol"' in mudclient, "Guthix worn symbol external loader missing")
    for path in [
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/guthix-symbol-mould.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/unstrung-symbol-of-guthix.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/unblessed-symbol-of-guthix.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/symbol-of-guthix.png",
        ROOT / "dev/myworld/assets/sprites/equipment/guthix-symbol/numbered/17.png",
    ]:
        require(path.is_file(), f"missing Guthix symbol asset {path.relative_to(ROOT)}")

    require("class BlessedSymbols implements UseLocTrigger" in blessed, "blessed symbol altar plugin missing")
    require("SYMBOL_DEVOTION_REQUIREMENT = 50" in blessed, "symbol blessing should require 50 devotion")
    require("ItemId.UNBLESSED_HOLY_SYMBOL.id()" in blessed and "ItemId.HOLY_SYMBOL_OF_SARADOMIN.id()" in blessed,
            "Saradomin holy symbol altar blessing missing")
    require("ItemId.UNBLESSED_UNHOLY_SYMBOL_OF_ZAMORAK.id()" in blessed and "ItemId.UNHOLY_SYMBOL_OF_ZAMORAK.id()" in blessed,
            "Zamorak unholy symbol altar blessing missing")
    require("ItemId.UNBLESSED_GUTHIX_SYMBOL.id()" in blessed and "ItemId.GUTHIX_SYMBOL.id()" in blessed,
            "Guthix symbol altar blessing missing")
    require("PrayerBlessingTransaction.bless(" in blessed
            and "SYMBOL_DEVOTION_REQUIREMENT," in blessed
            and "SYMBOL_CRAFTING_XP," in blessed,
            "symbol blessing should use the atomic scaled-XP transaction")

    require("ItemId.GUTHIX_SYMBOL_MOULD.id()" in crafting, "Guthix symbol mould should be a silver mould")
    require("ItemId.HOLY_SYMBOL_MOULD.id()" in crafting_shop, "Saradomin symbol mould should be sold by crafting equipment shops")
    require("ItemId.UNHOLY_SYMBOL_MOULD.id()" in crafting_shop, "Zamorak symbol mould should be sold by crafting equipment shops")
    require("ItemId.GUTHIX_SYMBOL_MOULD.id()" in crafting_shop, "Guthix symbol mould should be sold by crafting equipment shops")
    require("ItemId.UNSTRUNG_GUTHIX_SYMBOL.id()" in crafting, "Guthix unstrung symbol should be craftable")
    require("ItemId.UNBLESSED_GUTHIX_SYMBOL.id()" in crafting, "Guthix symbol should be stringable")
    require("FURNACE_CATEGORY_GUTHIX_SYMBOLS" in smelting, "Guthix symbols should be available from furnace categories")
    require("case 3173:" in do_skill and 'return "Guthix symbols";' in do_skill,
            "client furnace category should label Guthix symbols")

    require("isZamorakBlessedSymbol" in destroy and "isSaradominBlessedSymbol" in destroy and "isGuthixBlessedSymbol" in destroy,
            "symbol destruction should recognize all god lines")
    require("SYMBOL_DEVOTION_OFFERING_CHANGE = 2" in destroy
            and "return 200;" in destroy
            and "Devotion.adjustDevotionOfferings(player, worshippedGod, devotionOfferingChange)" in destroy
            and "Devotion.adjustDevotionOfferings(player, itemGod, -devotionOfferingChange)" in destroy,
            "symbol destruction should transfer 0.2 devotion and preserve symbol production XP")
    require('player.message("You attempt to put it on...")' in equipment
            and 'player.message("It scalds the flesh! Metaphorically, of course.")' in equipment,
            "opposing god equipment should use the scalding equip rejection message")
    require("itemId == ItemId.HOLY_SYMBOL_OF_SARADOMIN.id()" in equipment,
            "Saradomin symbol should be gated by Saradomin worship")
    require("itemId == ItemId.UNHOLY_SYMBOL_OF_ZAMORAK.id()" in equipment,
            "Zamorak symbol should be gated by Zamorak worship")
    require("itemId == ItemId.GUTHIX_SYMBOL.id()" in equipment,
            "Guthix symbol should be gated by Guthix worship")

    require('currentNpcDrops = new DropTable("Monk (93)");' in npc_drops
            and "ItemId.HOLY_SYMBOL_OF_SARADOMIN.id(), 1, 2" in npc_drops
            and "this.npcDrops.put(NpcId.MONK.id(), currentNpcDrops);" in npc_drops,
            "Saradomin monks should have an uncommon blessed symbol drop")
    require('currentNpcDrops = new DropTable("Monk of Zamorak Level 29 (139)");' in npc_drops
            and "ItemId.UNHOLY_SYMBOL_OF_ZAMORAK.id(), 1, 2" in npc_drops
            and "this.npcDrops.put(NpcId.MONK_OF_ZAMORAK.id(), currentNpcDrops);" in npc_drops,
            "Zamorak monks should have an uncommon blessed symbol drop")
    require('currentNpcDrops = new DropTable("Druid (200)");' in npc_drops
            and "ItemId.GUTHIX_SYMBOL.id(), 1, 2" in npc_drops
            and "this.npcDrops.put(NpcId.DRUID.id(), currentNpcDrops);" in npc_drops,
            "Guthix druids should have an uncommon blessed symbol drop")
    black_knight_drops = block_between(
            npc_drops,
            'currentNpcDrops = new DropTable("Black Knight (66, 189) Jailer (265) Lord Darquarius (266) Renegade Knight (277)");',
            'currentNpcDrops = new DropTable("Hobgoblin Level 32 (67) Hobgoblin Level 48 (311)");')
    require("SYMBOL" not in black_knight_drops,
            "blessed symbols should not be added to knight drop tables")

    require("SYMBOL_BONUS_SUFFIX" in devotion, "devotion should track every-other symbol bonus")
    require("hasBlessedSymbolEquipped(player, godLine)" in devotion, "offerings should check matching blessed symbol")
    require("return 1 + getEveryOtherOfferingBonus(player, godLine, SYMBOL_BONUS_SUFFIX);" in devotion,
            "blessed symbols should average 1.5 devotion per offering")
    require("return bonusThisOffering ? 1 : 0;" in devotion,
            "every-other offering bonuses should preserve fractional devotion without rounding")

    require("Take that symbol to an altar of Saradomin" in brother_jered,
            "Brother Jered should redirect MyWorld holy symbol blessing to altars")
    require("Take the unholy symbol to an altar of Zamorak" in scorpius,
            "Spirit of Scorpius should redirect MyWorld unholy symbol blessing to altars")

    print("PASS: blessed symbol altar flow and devotion offering effect validated")


if __name__ == "__main__":
    main()
