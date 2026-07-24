#!/usr/bin/env python3
"""Validate the Bangel wrist-slot, crafting, compatibility, and future-family contract."""

import json
import re
import struct
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any, NoReturn


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
CLIENT = ROOT / "Client_Base/src"

CUSTOM_DEFS = SERVER / "conf/server/defs/ItemDefsCustom.json"
MYWORLD_DEFS = SERVER / "conf/server/defs/ItemDefsMyWorld.json"
CRAFTING_DEFS = SERVER / "conf/server/defs/extras/ItemCraftingDef.xml"
EQUIPMENT = SERVER / "src/com/openrsc/server/model/container/Equipment.java"
BANK_PRESET = SERVER / "src/com/openrsc/server/model/container/BankPreset.java"
PLAYER = SERVER / "src/com/openrsc/server/model/entity/player/Player.java"
EFFECTS = SERVER / "src/com/openrsc/server/content/EnchantingItemEffects.java"
MEDALLIONS = SERVER / "src/com/openrsc/server/content/FutureMedallionCatalog.java"
MYWORLD_IDS = (
    SERVER / "src/com/openrsc/server/constants/custom/MyWorldItemId.java"
)
CRAFTING = (
    SERVER
    / "plugins/com/openrsc/server/plugins/authentic/skills/crafting/Crafting.java"
)
SMELTING = (
    SERVER
    / "plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smelting.java"
)
ENCHANTING = (
    SERVER
    / "plugins/com/openrsc/server/plugins/custom/myworld/skills/enchanting/Enchanting.java"
)

CONFIG = CLIENT / "orsc/Config.java"
SLOT_MAPPING = CLIENT / "orsc/EquipmentSlotMapping.java"
PACKET_HANDLER = CLIENT / "orsc/PacketHandler.java"
MUDCLIENT = CLIENT / "orsc/mudclient.java"
CLIENT_ENTITIES = (
    CLIENT / "com/openrsc/client/entityhandling/EntityHandler.java"
)
CLIENT_BANK = (
    CLIENT / "com/openrsc/interfaces/misc/CustomBankInterface.java"
)
CLIENT_BANK_TAGS = (
    CLIENT / "com/openrsc/interfaces/misc/BankItemTag.java"
)

ASSETS = {
    "bangel": (
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/bangel.png",
        (19, 15),
    ),
    "mould": (
        ROOT
        / "dev/myworld/assets/sprites/items/inventory-ground/tools/bangel-mould.png",
        (27, 26),
    ),
    "slot": (
        ROOT / "dev/myworld/assets/sprites/ui/equipment/bangel-slot.png",
        (49, 34),
    ),
    "medallion": (
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/medallion.png",
        (14, 13),
    ),
}

TIERS = ("Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone")
BASE_BANGELS = (3282, 3283, 3284, 3285, 3286)
MEDALLION_IDS = (3287, 3288, 3289, 3290, 3291)
ENCHANTED_BANGELS = (
    tuple(range(1593, 1613))
    + tuple(range(1709, 1714))
    + tuple(range(1719, 1759))
    + tuple(range(3106, 3111))
)

EXPECTED_BANGEL_RECIPES = {
    3282: (13, 260, 164, 1800),
    3283: (26, 280, 163, 3000),
    3284: (44, 340, 162, 6000),
    3285: (60, 400, 161, 12000),
    3286: (70, 600, 523, 35000),
}


def fail(message: str) -> NoReturn:
    raise SystemExit(f"FAIL: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_items(path: Path) -> dict[int, dict[str, Any]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    entries = data["items"] if isinstance(data, dict) else data
    return {int(entry["id"]): dict(entry) for entry in entries}


def load_active_custom_items() -> dict[int, dict[str, Any]]:
    items = load_items(CUSTOM_DEFS)
    for item_id, override in load_items(MYWORLD_DEFS).items():
        if item_id in items:
            items[item_id].update(override)
    return items


def method_body(source: str, method_name: str) -> str:
    match = re.search(
        rf"^\s*(?:public|protected|private)\s+[^\n]+\b{re.escape(method_name)}\(",
        source,
        re.MULTILINE,
    )
    require(match is not None, f"missing method {method_name}")
    start = match.start()
    brace = source.find("{", start)
    require(brace >= 0, f"missing body for method {method_name}")
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[brace : index + 1]
    fail(f"unterminated method {method_name}")


def png_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    require(data[:8] == b"\x89PNG\r\n\x1a\n", f"{path} is not a PNG")
    return struct.unpack(">II", data[16:24])


def ensure_slot_contract() -> None:
    equipment = EQUIPMENT.read_text(encoding="utf-8")
    config = CONFIG.read_text(encoding="utf-8")
    mapping = SLOT_MAPPING.read_text(encoding="utf-8")
    packets = PACKET_HANDLER.read_text(encoding="utf-8")
    client = MUDCLIENT.read_text(encoding="utf-8")
    bank = CLIENT_BANK.read_text(encoding="utf-8")

    require("public static final int SLOT_COUNT = 15;" in equipment,
            "server equipment slot count should be 15")
    require("SLOT_RING(13),\n\t\tSLOT_WRIST(14);" in equipment,
            "wrist must be appended immediately after ring")
    correction = method_body(equipment, "correctIndex")
    require(
        "request.equipmentSlot.getIndex() > 4" in correction
        and "request.equipmentSlot.getIndex() + 3" in correction,
        "client logical wrist slot should translate back to server slot 14",
    )
    require("public static int S_PLAYER_SLOT_COUNT = 12;" in config,
            "client logical equipment slot count should be 12")
    require("if (serverSlot > 7)" in mapping and "return serverSlot - 3;" in mapping,
            "server wrist slot should translate to client slot 11")
    require(packets.count("EquipmentSlotMapping.serverToClient(") == 3,
            "all equipment and preset packet paths should share slot translation")

    require(
        "new int[]{98, 98, 98, 153, 43, 43, 98, 98, 43, 153, 153, 43}"
        in client,
        "equipment UI should position all 12 logical slots",
    )
    require(
        "new int[]{5, 85, 125, 85, 85, 165, 165, 45, 45, 45, 165, 125}"
        in client,
        "wrist slot should have a distinct non-overlapping UI position",
    )
    for snippet in (
        "for (int i = 0; i < S_PLAYER_SLOT_COUNT; i++)",
        "if (hoveredEquipmentSlot >= 0 && equippedItems[hoveredEquipmentSlot] != null)",
        "MenuItemAction.ITEM_UNEQUIP_FROM_EQUIPMENT",
        "bufferBits.putByte(j);",
        "getEquipmentSlotSprite(i)",
        '"bangel-slot.png"',
    ):
        require(snippet in client, f"equipment UI missing wrist-safe behavior: {snippet}")

    require(
        "private int[] equipmentViewOrder = new int[]{0, 1, 2, 7, 4, 3, 8, 9, 5, 6, 10, 11};"
        in bank,
        "bank equipment view should include the appended wrist slot",
    )
    require("getEquipmentSlotSprite(this.equipmentViewOrder[i])" in bank,
            "bank equipment view should draw the wrist placeholder")
    require("getEquipmentSlotSprite(i)" in bank,
            "bank preset view should draw the wrist placeholder")

    fixture = """
package orsc;
public final class BangelSlotMappingFixture {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        require(EquipmentSlotMapping.serverToClient(0) == 0, "helmet");
        require(EquipmentSlotMapping.serverToClient(4) == 4, "mainhand");
        require(EquipmentSlotMapping.serverToClient(5) == 0, "alternate helmet");
        require(EquipmentSlotMapping.serverToClient(6) == 1, "alternate body");
        require(EquipmentSlotMapping.serverToClient(7) == 2, "alternate legs");
        require(EquipmentSlotMapping.serverToClient(8) == 5, "gloves");
        require(EquipmentSlotMapping.serverToClient(13) == 10, "ring");
        require(EquipmentSlotMapping.serverToClient(14) == 11, "wrist");
        System.out.println("PASS: deterministic server/client equipment slot translation");
    }
}
"""
    with tempfile.TemporaryDirectory(prefix="bangel-slot-mapping-") as raw_tmp:
        tmp = Path(raw_tmp)
        fixture_path = tmp / "orsc/BangelSlotMappingFixture.java"
        fixture_path.parent.mkdir(parents=True)
        fixture_path.write_text(fixture, encoding="utf-8")
        classes = tmp / "classes"
        classes.mkdir()
        compile_result = subprocess.run(
            ["javac", "-d", str(classes), str(SLOT_MAPPING), str(fixture_path)],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        require(compile_result.returncode == 0,
                "slot mapping fixture did not compile:\n" + compile_result.stderr)
        run_result = subprocess.run(
            ["java", "-cp", str(classes), "orsc.BangelSlotMappingFixture"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        require(run_result.returncode == 0,
                "slot mapping fixture failed:\n" + run_result.stderr)
        print(run_result.stdout.strip())


def ensure_persistence_and_lifecycle_contract() -> None:
    equipment = EQUIPMENT.read_text(encoding="utf-8")
    preset = BANK_PRESET.read_text(encoding="utf-8")
    player = PLAYER.read_text(encoding="utf-8")

    require("new Item[Equipment.SLOT_COUNT]" in preset,
            "bank presets should allocate the current server slot count")
    require("for (int i = 0; i < Equipment.SLOT_COUNT; i++)" in preset,
            "bank preset decoding should include wrist")
    require("if (!blobData.hasRemaining())" in preset,
            "legacy 14-slot preset data should remain readable")
    require("int currentSlot = item.getWieldPosition();" in preset,
            "old preset holdings should migrate by current item definition")
    require("equipment[currentSlot]" in preset,
            "migrated preset items should populate their current slot")
    require("for (int slot = 0; slot < Equipment.SLOT_COUNT; slot++)" in player,
            "player equipment validation should include wrist")

    require("public Item getEquippedWristItem()" in equipment,
            "equipment should expose the active wrist item")
    require(
        "getWieldPosition() == EquipmentSlot.SLOT_WRIST.getIndex()" in equipment,
        "legacy inventory-as-equipment worlds should resolve wrist items",
    )


def ensure_item_identity_and_visuals(items: dict[int, dict[str, Any]]) -> None:
    require(len(ENCHANTED_BANGELS) == 70, "expected 70 migrated enchanted IDs")
    for item_id in ENCHANTED_BANGELS:
        item = items.get(item_id)
        require(item is not None, f"missing preserved enchanted item ID {item_id}")
        require("Bangel" in item["name"] and "Amulet" not in item["name"],
                f"item {item_id} should be player-facing Bangel, found {item['name']!r}")
        require(item.get("wearSlot") == 14,
                f"migrated Bangel {item_id} should use wrist slot")
        require(item.get("appearanceID") == 0 and item.get("wearableID") == 0,
                f"migrated Bangel {item_id} should not require a worn model")

    client = CLIENT_ENTITIES.read_text(encoding="utf-8")
    for snippet in (
        "addBangelJewelryDefinitions();",
        "applyBangelVisuals();",
        '"external-png:bangel"',
        '"external-png:bangel-mould"',
        '"external-png:medallion"',
    ):
        require(snippet in client, f"client definition coverage missing: {snippet}")

    for name, (path, expected_size) in ASSETS.items():
        require(path.is_file(), f"missing tracked {name} asset")
        require(png_size(path) == expected_size,
                f"{name} asset dimensions changed from supplied source")


def ensure_bangel_crafting(items: dict[int, dict[str, Any]]) -> None:
    root = ET.parse(CRAFTING_DEFS).getroot()
    definitions: dict[int, tuple[int, int, int]] = {}
    for entry in root.findall("entry"):
        definition = entry.find("ItemCraftingDef")
        if definition is None:
            continue
        item_id = int(definition.findtext("itemID", "-1"))
        definitions[item_id] = (
            int(definition.findtext("requiredLvl", "-1")),
            int(definition.findtext("exp", "-1")),
            int(definition.findtext("gemID", "-1")),
        )

    for tier, item_id in zip(TIERS, BASE_BANGELS):
        expected_level, expected_xp, expected_gem, expected_price = (
            EXPECTED_BANGEL_RECIPES[item_id]
        )
        item = items.get(item_id)
        require(item is not None and item["name"] == f"{tier} Bangel",
                f"missing base {tier} Bangel")
        require(item.get("wearSlot") == 14 and item.get("isWearable") == 1,
                f"base {tier} Bangel should equip on wrist")
        require(item.get("basePrice") == expected_price,
                f"base {tier} Bangel should inherit the active Amulet price")
        require(
            definitions.get(item_id) == (expected_level, expected_xp, expected_gem),
            f"base {tier} Bangel crafting metadata drifted",
        )

    crafting = CRAFTING.read_text(encoding="utf-8")
    smelting = SMELTING.read_text(encoding="utf-8")
    effects = EFFECTS.read_text(encoding="utf-8")
    enchanting = ENCHANTING.read_text(encoding="utf-8")
    ids = MYWORLD_IDS.read_text(encoding="utf-8")
    bank_tags = CLIENT_BANK_TAGS.read_text(encoding="utf-8")

    for snippet in (
        "BANGEL_MOULD = 3281",
        "SAPPHIRE_BANGEL = 3282",
        "DRAGONSTONE_BANGEL = 3286",
    ):
        require(snippet in ids, f"missing custom ID constant: {snippet}")
    require("MyWorldItemId.BANGEL_MOULD" in crafting,
            "Bangel production should require the Bangel mould")
    require("JewelryCategory.BANGELS" in crafting,
            "modern furnace UI should expose the Bangel family")
    require("MyWorldItemId.SAPPHIRE_BANGEL" in smelting,
            "furnace categories should include Bangels")
    require("ItemId.BALL_OF_WOOL" not in method_body(crafting, "getRequiredGoldMouldId"),
            "Bangel mould selection must not introduce wool")
    require("isBangelBase(item.getCatalogId())" in enchanting,
            "altars should accept base Bangels")
    require("isAmuletBase(item.getCatalogId())" not in enchanting,
            "ordinary base Amulets should not remain altar inputs")
    require("MyWorldItemId.SAPPHIRE_BANGEL" in effects,
            "base Bangels should own the active altar tier ladder")
    require(
        'equalsAny(name, "sapphire amulet", "emerald amulet", "ruby amulet", "diamond amulet",'
        in bank_tags
        and '"unenchanted dragonstone amulet", "dragonstone amulet")' in bank_tags,
        "ordinary Amulets should be excluded from the Enchanting bank filter",
    )
    require('"dragonstone necklace", "sapphire bangel", "emerald bangel"' in bank_tags,
            "base Bangels should replace ordinary Amulets in the Enchanting bank filter")
    for ordinary_amulet in (
        "ItemId.SAPPHIRE_AMULET.id()",
        "ItemId.EMERALD_AMULET.id()",
        "ItemId.RUBY_AMULET.id()",
        "ItemId.DIAMOND_AMULET.id()",
        "ItemId.UNENCHANTED_DRAGONSTONE_AMULET.id()",
    ):
        require(ordinary_amulet not in method_body(effects, "isBangelBase"),
                f"ordinary Amulet leaked into active altar inputs: {ordinary_amulet}")


def ensure_effect_slot_contract(items: dict[int, dict[str, Any]]) -> None:
    equipment = EQUIPMENT.read_text(encoding="utf-8")
    player = PLAYER.read_text(encoding="utf-8")

    wrist_effect_getters = (
        "getChaosAmuletYieldBonusPercent",
        "getChaosAmuletBonusRuneWeights",
        "getDeathAmuletDamagePerKillBonus",
        "getBloodAmuletLifestealChance",
        "getDeathAmuletBurstRadius",
        "getDeathAmuletBurstMinDamage",
        "getDeathAmuletBurstMaxDamage",
        "getLifeAmuletSummonMaxDamageBonus",
        "getMindAmuletPotionDurationBonus",
        "getBodyAmuletRegenSpeedBonus",
        "getMindCombatAmuletXpBonus",
        "getBodyDisciplineAmuletXpBonus",
        "getNatureCleansingPoisonDecayBonus",
        "getGatheringAmuletYieldBonusPercent",
        "getCosmicAmuletExtraResourceChance",
        "getCosmicAmuletRareGatheringDoubleChance",
        "getCosmicAmuletGemChanceMultiplier",
        "getCosmicAmuletHerbQualityChance",
        "getSoulAmuletBurstRadius",
        "getSoulAmuletBurstMinHeal",
        "getSoulAmuletBurstMaxHeal",
        "getEquippedElementalDefenseBonus",
    )
    for getter in wrist_effect_getters:
        body = method_body(equipment, getter)
        require("getEquippedWristItem()" in body,
                f"{getter} should read the wrist slot")
        require("getEquippedNeckItem()" not in body,
                f"{getter} still reads the neck slot")

    for combined in ("getMindJewelryXpBonus", "getBodyJewelryXpBonus"):
        body = method_body(equipment, combined)
        for accessor in (
            "getEquippedWristItem()",
            "getEquippedNeckItem()",
            "getEquippedRingItem()",
        ):
            require(accessor in body,
                    f"{combined} should combine Bangel, Necklace, and Ring")

    require("getEquippedWristItem();" in method_body(player, "applyDeathAmuletBurst"),
            "death Bangel burst should resolve from wrist")
    require("getEquippedWristItem();" in method_body(player, "applySoulAmuletBurst"),
            "soul Bangel burst should resolve from wrist")

    # Representative same-tier items prove the three families occupy independent slots.
    require(items[3076]["wearSlot"] == 13, "Ring should remain in ring slot")
    require(items[1618]["wearSlot"] == 10, "Necklace should remain in neck slot")
    require(items[1739]["wearSlot"] == 14, "Bangel should occupy wrist slot")


def ensure_hidden_medallions(items: dict[int, dict[str, Any]]) -> None:
    medallions = MEDALLIONS.read_text(encoding="utf-8")
    crafting = CRAFTING.read_text(encoding="utf-8")
    smelting = SMELTING.read_text(encoding="utf-8")
    enchanting = ENCHANTING.read_text(encoding="utf-8")

    require("PRODUCTION_ENABLED = false" in medallions,
            "future Medallion production gate must remain closed")
    require("ALTAR_ENCHANTING_ENABLED = false" in medallions,
            "future Medallion altar gate must remain closed")
    require("return ItemId.SILVER_BAR.id();" in medallions,
            "future Medallions should use silver")

    for tier, item_id in zip(TIERS, MEDALLION_IDS):
        item = items.get(item_id)
        require(item is not None and item["name"] == f"{tier} Medallion",
                f"missing hidden {tier} Medallion definition")
        require(item.get("isWearable") == 0 and item.get("wearSlot") == -1,
                f"hidden {tier} Medallion should not be active equipment")
        require(item_id not in {
            int(definition.findtext("itemID", "-1"))
            for definition in ET.parse(CRAFTING_DEFS).getroot().iter("ItemCraftingDef")
        }, f"hidden {tier} Medallion leaked into production definitions")

    for source_name, source in (
        ("Crafting", crafting),
        ("Smelting", smelting),
        ("Enchanting", enchanting),
    ):
        require("MEDALLION" not in source,
                f"hidden Medallions leaked into {source_name} runtime")


def main() -> None:
    items = load_active_custom_items()
    ensure_slot_contract()
    ensure_persistence_and_lifecycle_contract()
    ensure_item_identity_and_visuals(items)
    ensure_bangel_crafting(items)
    ensure_effect_slot_contract(items)
    ensure_hidden_medallions(items)
    print("PASS: Bangel wrist jewelry, compatibility, crafting, and hidden Medallion gates validated")


if __name__ == "__main__":
    main()
