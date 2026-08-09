#!/usr/bin/env python3
"""Validate the Bangle wrist-slot, crafting, compatibility, and future-family contract."""

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
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"

BASE_DEFS = SERVER / "conf/server/defs/ItemDefs.json"
CUSTOM_DEFS = SERVER / "conf/server/defs/ItemDefsCustom.json"
MYWORLD_DEFS = SERVER / "conf/server/defs/ItemDefsMyWorld.json"
CRAFTING_DEFS = SERVER / "conf/server/defs/extras/ItemCraftingDef.xml"
RETRO_CRAFTING_DEFS = SERVER / "conf/server/defs/extras/retro/ItemCraftingDef.xml"
EQUIPMENT = SERVER / "src/com/openrsc/server/model/container/Equipment.java"
BANK_PRESET = SERVER / "src/com/openrsc/server/model/container/BankPreset.java"
BANK = SERVER / "src/com/openrsc/server/model/container/Bank.java"
PLAYER = SERVER / "src/com/openrsc/server/model/entity/player/Player.java"
PLAYER_SERVICE = SERVER / "src/com/openrsc/server/service/PlayerService.java"
GAME_DATABASE = SERVER / "src/com/openrsc/server/database/GameDatabase.java"
EFFECTS = SERVER / "src/com/openrsc/server/content/EnchantingItemEffects.java"
LEGACY_COMPATIBILITY = (
    SERVER / "src/com/openrsc/server/content/LegacyAmuletCompatibility.java"
)
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
SPELL_HANDLER = (
    SERVER / "src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"
)
ITEM_EQUIP = SERVER / "src/com/openrsc/server/net/rsc/handlers/ItemEquip.java"
ITEM_UNEQUIP = SERVER / "src/com/openrsc/server/net/rsc/handlers/ItemUnequip.java"
ACTION_SENDER = SERVER / "src/com/openrsc/server/net/rsc/ActionSender.java"
CRAFTING_SHOPS = (
    SERVER
    / "plugins/com/openrsc/server/plugins/authentic/npcs/CraftingEquipmentShops.java"
)
ADMINS = (
    SERVER / "plugins/com/openrsc/server/plugins/authentic/commands/Admins.java"
)
SUPERCHISEL = (
    SERVER / "plugins/com/openrsc/server/plugins/custom/misc/Superchisel.java"
)
SKILL_GUIDE = CLIENT / "com/openrsc/interfaces/misc/SkillGuideInterface.java"

CONFIG = CLIENT / "orsc/Config.java"
SLOT_MAPPING = CLIENT / "orsc/EquipmentSlotMapping.java"
PACKET_HANDLER = CLIENT / "orsc/PacketHandler.java"
MUDCLIENT = CLIENT / "orsc/mudclient.java"
INVENTORY_EQUIP_POLICY = CLIENT / "orsc/InventoryEquipMenuPolicy.java"
CLIENT_ENTITIES = (
    CLIENT / "com/openrsc/client/entityhandling/EntityHandler.java"
)
CLIENT_BANK = (
    CLIENT / "com/openrsc/interfaces/misc/CustomBankInterface.java"
)
CLIENT_BANK_TAGS = (
    CLIENT / "com/openrsc/interfaces/misc/BankItemTag.java"
)
DO_SKILL = CLIENT / "com/openrsc/interfaces/misc/DoSkillInterface.java"

ASSETS = {
    "bangle": (
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/bangle.png",
        (19, 15),
    ),
    "mould": (
        ROOT
        / "dev/myworld/assets/sprites/items/inventory-ground/tools/bangle-mould.png",
        (27, 26),
    ),
    "slot": (
        ROOT / "dev/myworld/assets/sprites/UI/equipment/bangle-slot.png",
        (49, 34),
    ),
    "medallion": (
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/medallion.png",
        (14, 13),
    ),
}

TIERS = ("Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone")
BASE_BANGLES = (3282, 3283, 3284, 3285, 3286)
GOLD_BANGLE = 3292
MEDALLION_IDS = (3287, 3288, 3289, 3290, 3291)
ENCHANTED_BANGLES = (
    tuple(range(1593, 1613))
    + tuple(range(1709, 1714))
    + tuple(range(1719, 1759))
    + tuple(range(3106, 3111))
)
ENCHANTED_NECKLACES = (
    tuple(range(1613, 1673))
    + tuple(range(1759, 1764))
    + tuple(range(3101, 3106))
)
ENCHANTED_RINGS = (
    tuple(range(1673, 1709))
    + tuple(range(1714, 1719))
    + tuple(range(3076, 3101))
)
STANDARD_ENCHANTED_BANGLES = {
    314: "Sapphire Bangle of Magic",
    315: "Emerald Bangle of Protection",
    316: "Ruby Bangle of Strength",
    317: "Diamond Bangle of Power",
    597: "Charged Dragonstone Bangle",
}

RETIRED_AMULET_IDS = (
    294, 296, 297, 298, 299, 300, 301, 302, 303, 304, 305, 522, 524, 610
)
RETIRED_AMULET_TOKENS = (
    "ItemId.AMULET_MOULD.id()",
    "ItemId.UNSTRUNG_GOLD_AMULET.id()",
    "ItemId.UNSTRUNG_SAPPHIRE_AMULET.id()",
    "ItemId.UNSTRUNG_EMERALD_AMULET.id()",
    "ItemId.UNSTRUNG_RUBY_AMULET.id()",
    "ItemId.UNSTRUNG_DIAMOND_AMULET.id()",
    "ItemId.GOLD_AMULET.id()",
    "ItemId.SAPPHIRE_AMULET.id()",
    "ItemId.EMERALD_AMULET.id()",
    "ItemId.RUBY_AMULET.id()",
    "ItemId.DIAMOND_AMULET.id()",
    "ItemId.DRAGONSTONE_AMULET.id()",
    "ItemId.UNSTRUNG_DRAGONSTONE_AMULET.id()",
    "ItemId.UNENCHANTED_DRAGONSTONE_AMULET.id()",
)

EXPECTED_BANGLE_RECIPES = {
    3292: (5, 120, -1, 900),
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
    entries = (
        data.get("items", data.get("item", []))
        if isinstance(data, dict)
        else data
    )
    return {int(entry["id"]): dict(entry) for entry in entries}


def load_active_custom_items() -> dict[int, dict[str, Any]]:
    items = load_items(BASE_DEFS)
    for source in (CUSTOM_DEFS, MYWORLD_DEFS):
        for item_id, override in load_items(source).items():
            if item_id in items:
                items[item_id].update(override)
            else:
                items[item_id] = override
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
        '"bangle-slot.png"',
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
public final class BangleSlotMappingFixture {
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
    with tempfile.TemporaryDirectory(prefix="bangle-slot-mapping-") as raw_tmp:
        tmp = Path(raw_tmp)
        fixture_path = tmp / "orsc/BangleSlotMappingFixture.java"
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
            ["java", "-cp", str(classes), "orsc.BangleSlotMappingFixture"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        require(run_result.returncode == 0,
                "slot mapping fixture failed:\n" + run_result.stderr)
        print(run_result.stdout.strip())

    equip_fixture = """
package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;

public final class ZeroVisualBangleEquipFixture {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertBangle(int itemId) {
        ItemDef item = EntityHandler.getItemDef(itemId);
        require(item != null, "missing client Bangle " + itemId);
        require(item.getName().contains("Bangle"), "wrong client name for " + itemId);
        require(item.isWieldable(), "client Bangle is not wieldable: " + itemId);
        require(item.wearableID == 0, "client Bangle has character-model wearable ID: " + itemId);
        require(InventoryEquipMenuPolicy.canOfferEquip(item, false), "client Bangle lacks Wear action: " + itemId);
        require("Wear".equals(InventoryEquipMenuPolicy.actionLabel(item)), "client Bangle action is not Wear: " + itemId);
    }

    private static void assertRange(int first, int last) {
        for (int itemId = first; itemId <= last; itemId++) {
            assertBangle(itemId);
        }
    }

    public static void main(String[] args) {
        ItemDef zeroVisual = new ItemDef(
            "Zero visual equipment", "", "", 1, -1, "", false, true, 0, 0,
            false, false, true, 9999);
        require(InventoryEquipMenuPolicy.canOfferEquip(zeroVisual, false),
            "wieldable zero-visual-ID item should receive an equip action");
        require("Wear".equals(InventoryEquipMenuPolicy.actionLabel(zeroVisual)),
            "zero-visual-ID equipment should use Wear");
        require(!InventoryEquipMenuPolicy.canOfferEquip(zeroVisual, true),
            "noted equipment should not receive an equip action");

        ItemDef visualButNotWieldable = new ItemDef(
            "Visual-only item", "", "", 1, -1, "", false, false, 16, 0,
            false, false, true, 10000);
        require(!InventoryEquipMenuPolicy.canOfferEquip(visualButNotWieldable, false),
            "visual wearable ID must not imply equipability");

        ItemDef weapon = new ItemDef(
            "Weapon", "", "", 1, -1, "", false, true, 16, 0,
            false, false, true, 10001);
        require("Wield".equals(InventoryEquipMenuPolicy.actionLabel(weapon)),
            "weapon visual type should retain Wield wording");

        EntityHandler.load(true);
        assertBangle(3292);
        assertRange(3282, 3286);
        assertRange(314, 317);
        assertBangle(597);
        assertRange(1593, 1612);
        assertRange(1709, 1713);
        assertRange(1719, 1758);
        assertRange(3106, 3110);
        System.out.println("PASS: all 81 zero-visual-ID Bangles receive Wear actions");
    }
}
"""
    with tempfile.TemporaryDirectory(prefix="bangle-zero-visual-equip-") as raw_tmp:
        tmp = Path(raw_tmp)
        fixture_path = tmp / "orsc/ZeroVisualBangleEquipFixture.java"
        fixture_path.parent.mkdir(parents=True)
        fixture_path.write_text(equip_fixture, encoding="utf-8")
        classes = tmp / "classes"
        classes.mkdir()
        compile_result = subprocess.run(
            [
                "javac",
                "-cp",
                str(CLIENT_JAR),
                "-d",
                str(classes),
                str(INVENTORY_EQUIP_POLICY),
                str(fixture_path),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        require(
            compile_result.returncode == 0,
            "zero-visual-ID equip fixture did not compile:\n"
            + compile_result.stderr,
        )
        run_result = subprocess.run(
            [
                "java",
                "-cp",
                f"{classes}:{CLIENT_JAR}",
                "orsc.ZeroVisualBangleEquipFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        require(
            run_result.returncode == 0,
            "zero-visual-ID equip fixture failed:\n"
            + run_result.stdout
            + run_result.stderr,
        )
        print(run_result.stdout.strip())


def ensure_persistence_and_lifecycle_contract() -> None:
    equipment = EQUIPMENT.read_text(encoding="utf-8")
    preset = BANK_PRESET.read_text(encoding="utf-8")
    bank = BANK.read_text(encoding="utf-8")
    player = PLAYER.read_text(encoding="utf-8")
    player_service = PLAYER_SERVICE.read_text(encoding="utf-8")
    database = GAME_DATABASE.read_text(encoding="utf-8")
    compatibility = LEGACY_COMPATIBILITY.read_text(encoding="utf-8")

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
    require("LegacyAmuletCompatibility.canonicalize(invItem.item);" in player_service,
            "inventory holdings should canonicalize on login")
    require("LegacyAmuletCompatibility.canonicalize(equippedItem.itemStatus)" in player_service,
            "equipped holdings should canonicalize on login")
    require("LegacyAmuletCompatibility.canonicalize(bankItems[i].itemStatus);" in player_service,
            "bank holdings should canonicalize on login")
    require("convertedItemIds.contains(equippedItem.itemId)" in player_service
            and "overflow.add(item);" in player_service,
            "wrist collisions should preserve converted legacy equipment in the bank")
    require(preset.count("LegacyAmuletCompatibility.canonicalCatalogId(") == 2,
            "bank presets should canonicalize retired Amulet IDs")
    require("LegacyAmuletCompatibility.canonicalCatalogId(catalogId)" in bank,
            "bank pin metadata should canonicalize retired Amulet IDs")
    require(database.count("LegacyAmuletCompatibility.canonicalCatalogId(") >= 5,
            "auction listings and collectible property should canonicalize retired Amulets")
    require("for (int slot = 0; slot < Equipment.SLOT_COUNT; slot++)" in player,
            "player equipment validation should include wrist")

    expected_mapping = {
        "AMULET_MOULD": "BANGLE_MOULD",
        "GOLD_AMULET": "GOLD_BANGLE",
        "SAPPHIRE_AMULET": "SAPPHIRE_BANGLE",
        "EMERALD_AMULET": "EMERALD_BANGLE",
        "RUBY_AMULET": "RUBY_BANGLE",
        "DIAMOND_AMULET": "DIAMOND_BANGLE",
        "DRAGONSTONE_AMULET": "DRAGONSTONE_BANGLE",
    }
    for legacy_name, bangle_name in expected_mapping.items():
        require(legacy_name in compatibility and bangle_name in compatibility,
                f"missing compatibility conversion from {legacy_name} to {bangle_name}")
    require("status.setCatalogId(canonicalId);" in compatibility,
            "conversion must preserve the existing ItemStatus ownership token")

    require("public Item getEquippedWristItem()" in equipment,
            "equipment should expose the active wrist item")
    require(
        "getWieldPosition() == EquipmentSlot.SLOT_WRIST.getIndex()" in equipment,
        "legacy inventory-as-equipment worlds should resolve wrist items",
    )

    fixture = """
import com.openrsc.server.content.LegacyAmuletCompatibility;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.ItemStatus;

public final class BanglePropertyCompatibilityFixture {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static ItemStatus status(int catalogId, int seed) {
        ItemStatus status = new ItemStatus();
        status.setCatalogId(catalogId);
        status.setAmount(37 + seed);
        status.setNoted((seed & 1) == 0);
        status.setWielded((seed & 1) != 0);
        status.setDurability(100 + seed);
        return status;
    }

    public static void main(String[] args) {
        int[] legacy = {294, 296, 301, 297, 302, 298, 303, 299, 304, 300, 305, 522, 524, 610};
        int[] expected = {3281, 3292, 3292, 3282, 3282, 3283, 3283, 3284, 3284, 3285, 3285, 3286, 3286, 3286};
        for (int i = 0; i < legacy.length; i++) {
            ItemStatus value = status(legacy[i], i);
            int amount = value.getAmount();
            boolean noted = value.getNoted();
            boolean wielded = value.isWielded();
            int durability = value.getDurability();
            require(LegacyAmuletCompatibility.canonicalize(value), "legacy ID should convert: " + legacy[i]);
            require(value.getCatalogId() == expected[i], "wrong target for " + legacy[i]);
            require(value.getAmount() == amount, "amount changed for " + legacy[i]);
            require(value.getNoted() == noted, "note state changed for " + legacy[i]);
            require(value.isWielded() == wielded, "wield state changed for " + legacy[i]);
            require(value.getDurability() == durability, "durability changed for " + legacy[i]);
            require(!LegacyAmuletCompatibility.canonicalize(value), "conversion should be idempotent");
        }

        int[] questExceptions = {24, 235, 744, 782, 826, 1009, 1010, 1011, 1591};
        for (int id : questExceptions) {
            ItemStatus value = status(id, id);
            require(!LegacyAmuletCompatibility.canonicalize(value), "quest Amulet converted: " + id);
            require(value.getCatalogId() == id, "quest Amulet ID changed: " + id);
        }

        int[] retainedBangleIds = {314, 315, 316, 317, 597, 1593, 1612, 1709, 1758, 3106, 3110};
        for (int id : retainedBangleIds) {
            ItemStatus value = status(id, id);
            require(!LegacyAmuletCompatibility.canonicalize(value), "retained Bangle ID remapped: " + id);
            require(value.getCatalogId() == id, "retained Bangle ID changed: " + id);
        }

        ItemStatus legacyHolding = status(297, 1);
        ItemStatus currentHolding = status(3282, 2);
        int totalBefore = legacyHolding.getAmount() + currentHolding.getAmount();
        require(LegacyAmuletCompatibility.canonicalize(legacyHolding), "coexisting legacy holding did not convert");
        require(legacyHolding != currentHolding, "distinct holdings collapsed");
        require(legacyHolding.getCatalogId() == currentHolding.getCatalogId(), "equivalent holdings disagree");
        require(legacyHolding.getAmount() + currentHolding.getAmount() == totalBefore, "coexisting quantity changed");

        ItemStatus ownedStatus = status(300, 4);
        Item ownedItem = new Item(987654321L, ownedStatus);
        require(LegacyAmuletCompatibility.canonicalize(ownedItem), "owned item did not convert");
        require(ownedItem.getItemId() == 987654321L, "ownership token changed");
        require(ownedItem.getItemStatus() == ownedStatus, "ItemStatus instance was replaced");
        require(ownedItem.getCatalogId() == 3285, "owned item converted to wrong tier");

        System.out.println("PASS: deterministic Bangle property conversion and identity preservation");
    }
}
"""
    with tempfile.TemporaryDirectory(prefix="bangle-property-compatibility-") as raw_tmp:
        tmp = Path(raw_tmp)
        fixture_path = tmp / "BanglePropertyCompatibilityFixture.java"
        fixture_path.write_text(fixture, encoding="utf-8")
        classes = tmp / "classes"
        classes.mkdir()
        compile_result = subprocess.run(
            [
                "javac",
                "-cp",
                str(SERVER / "core.jar"),
                "-d",
                str(classes),
                str(LEGACY_COMPATIBILITY),
                str(fixture_path),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        require(
            compile_result.returncode == 0,
            "property compatibility fixture did not compile:\n"
            + compile_result.stderr,
        )
        run_result = subprocess.run(
            [
                "java",
                "-cp",
                f"{classes}:{SERVER / 'core.jar'}",
                "BanglePropertyCompatibilityFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        require(
            run_result.returncode == 0,
            "property compatibility fixture failed:\n" + run_result.stderr,
        )
        print(run_result.stdout.strip())


def ensure_item_identity_and_visuals(items: dict[int, dict[str, Any]]) -> None:
    require(len(ENCHANTED_BANGLES) == 70, "expected 70 migrated enchanted IDs")
    for item_id in ENCHANTED_BANGLES:
        item = items.get(item_id)
        require(item is not None, f"missing preserved enchanted item ID {item_id}")
        require("Bangle" in item["name"] and "Amulet" not in item["name"],
                f"item {item_id} should be player-facing Bangle, found {item['name']!r}")
        require(item.get("isWearable") == 1 and item.get("wearSlot") == 14,
                f"migrated Bangle {item_id} should be wieldable in wrist slot")
        require(item.get("appearanceID") == 0 and item.get("wearableID") == 0,
                f"migrated Bangle {item_id} should not require a worn model")

    for item_id, expected_name in STANDARD_ENCHANTED_BANGLES.items():
        item = items.get(item_id)
        require(item is not None and item.get("name") == expected_name,
                f"classic enchanted ID {item_id} should now be {expected_name}")
        require(item.get("isWearable") == 1 and item.get("wearSlot") == 14,
                f"classic enchanted Bangle {item_id} should equip on wrist")
        require(item.get("appearanceID") == 0 and item.get("wearableID") == 0,
                f"classic enchanted Bangle {item_id} should not use a worn model")

    client = CLIENT_ENTITIES.read_text(encoding="utf-8")
    for snippet in (
        "addBangleJewelryDefinitions();",
        "applyBangleVisuals();",
        '"external-png:bangle@19x15"',
        '"external-png:bangle-mould"',
        '"external-png:medallion"',
    ):
        require(snippet in client, f"client definition coverage missing: {snippet}")

    for name, (path, expected_size) in ASSETS.items():
        require(path.is_file(), f"missing tracked {name} asset")
        require(png_size(path) == expected_size,
                f"{name} asset dimensions changed from supplied source")


def ensure_bangle_crafting(items: dict[int, dict[str, Any]]) -> None:
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

    craftable_bangles = (("Gold", GOLD_BANGLE),) + tuple(zip(TIERS, BASE_BANGLES))
    for tier, item_id in craftable_bangles:
        expected_level, expected_xp, expected_gem, expected_price = (
            EXPECTED_BANGLE_RECIPES[item_id]
        )
        item = items.get(item_id)
        require(item is not None and item["name"] == f"{tier} Bangle",
                f"missing base {tier} Bangle")
        require(item.get("wearSlot") == 14 and item.get("isWearable") == 1,
                f"base {tier} Bangle should equip on wrist")
        require(item.get("basePrice") == expected_price,
                f"base {tier} Bangle should inherit the active Amulet price")
        require(item.get("appearanceID") == 0 and item.get("wearableID") == 0,
                f"base {tier} Bangle should not use a character-model visual")
        require(
            definitions.get(item_id) == (expected_level, expected_xp, expected_gem),
            f"base {tier} Bangle crafting metadata drifted",
        )

    crafting = CRAFTING.read_text(encoding="utf-8")
    smelting = SMELTING.read_text(encoding="utf-8")
    effects = EFFECTS.read_text(encoding="utf-8")
    enchanting = ENCHANTING.read_text(encoding="utf-8")
    ids = MYWORLD_IDS.read_text(encoding="utf-8")
    bank_tags = CLIENT_BANK_TAGS.read_text(encoding="utf-8")
    do_skill = DO_SKILL.read_text(encoding="utf-8")

    for snippet in (
        "BANGLE_MOULD = 3281",
        "GOLD_BANGLE = 3292",
        "SAPPHIRE_BANGLE = 3282",
        "DRAGONSTONE_BANGLE = 3286",
    ):
        require(snippet in ids, f"missing custom ID constant: {snippet}")
    require("MyWorldItemId.BANGLE_MOULD" in crafting,
            "Bangle production should require the Bangle mould")
    require("JewelryCategory.BANGLES" in crafting,
            "modern furnace UI should expose the Bangle family")
    require("MyWorldItemId.GOLD_BANGLE" in smelting,
            "furnace categories should include Bangles")
    require("ItemId.BALL_OF_WOOL" not in method_body(crafting, "getRequiredGoldMouldId"),
            "Bangle mould selection must not introduce wool")
    require("isBangleBase(item.getCatalogId())" in enchanting,
            "altars should accept base Bangles")
    require("isAmuletBase(item.getCatalogId())" not in enchanting,
            "ordinary base Amulets should not remain altar inputs")
    require("MyWorldItemId.SAPPHIRE_BANGLE" in effects,
            "base Bangles should own the active altar tier ladder")
    require('"dragonstone necklace", "sapphire bangle", "emerald bangle"' in bank_tags,
            "base Bangles should replace ordinary Amulets in the Enchanting bank filter")
    require('"amulet mould"' not in method_body(bank_tags, "isJewelryMould"),
            "retired Amulet mould should not remain a bank crafting classification")
    furnace_names = method_body(do_skill, "furnaceCategoryName")
    require("case 3292:" in furnace_names and 'return "Bangles";' in furnace_names,
            "client furnace category should recognize the Gold Bangle sentinel")
    require("case 296:" not in furnace_names and 'return "Amulets";' not in furnace_names,
            "retired Amulet furnace category should not remain visible")

    output_ids = method_body(crafting, "getGoldJewelryProductionOutputIds")
    automatic_menu = method_body(crafting, "getDesiredGoldCraftingAutoDetection")
    authentic_menu = method_body(crafting, "getDesiredGoldCraftingAuthentic")
    for item_name in (
        "GOLD_BANGLE",
        "SAPPHIRE_BANGLE",
        "EMERALD_BANGLE",
        "RUBY_BANGLE",
        "DIAMOND_BANGLE",
        "DRAGONSTONE_BANGLE",
    ):
        require(
            f"MyWorldItemId.{item_name}" in output_ids,
            f"modern production list omits {item_name}",
        )
        require(
            f"MyWorldItemId.{item_name}" in automatic_menu,
            f"automatic recipe detection omits {item_name}",
        )
    require(
        "final boolean directTierSelection = player.getConfig().WANT_EQUIPMENT_TAB;"
        in authentic_menu
        and "boolean gemUsed = directTierSelection;" in authentic_menu,
        "legacy direct-tier menu should expose gemmed Bangles instead of silently choosing Gold",
    )
    require(
        "new String[]{Gold, Sapphire, Emerald, Ruby, Diamond, Dragonstone}"
        in authentic_menu
        and "new String[]{Gold, Sapphire, Emerald, Ruby, Diamond}" in authentic_menu,
        "legacy menu should expose Gold plus every available Bangle tier",
    )

    recipe = method_body(crafting, "goldJewelryProductionRecipe")
    require(
        "addProductionIngredient(ingredientIds, fallbackIds, amounts, mouldId, 1);"
        in recipe,
        "modern Bangle recipes should display the mould requirement",
    )
    begin_production = method_body(crafting, "beginProductionFromInterface")
    require(
        "getRequiredGoldMouldId(itemId)" in begin_production
        and "canStartGoldJewelryRecipe(player, def, mouldId)" in begin_production
        and "batchGoldJewelry(player, goldBar, def)" in begin_production,
        "modern batch production should validate the mould and use the shared batch path",
    )
    batch = method_body(crafting, "batchGoldJewelry")
    require(
        "ItemId.BALL_OF_WOOL" not in batch
        and "MyWorldItemId.BANGLE_MOULD" not in batch,
        "batch production must consume neither wool nor the reusable Bangle mould",
    )
    wool_stringing = method_body(crafting, "useWool")
    require(
        "BANGLE" not in wool_stringing and "AMULET" not in wool_stringing,
        "wool stringing must not produce retired Amulets or Bangles",
    )

    legacy_gold_menu = method_body(do_skill, "populateSkillItems")
    for retired_id in (296, 297, 298, 299, 300, 524):
        require(f"new DoSkillItem({retired_id}," not in legacy_gold_menu,
                f"legacy Gold menu still exposes retired Amulet ID {retired_id}")
    for bangle_id in (GOLD_BANGLE,) + BASE_BANGLES:
        require(
            f"new DoSkillItem({bangle_id}," in legacy_gold_menu,
            f"legacy/retro Gold menu omits Bangle ID {bangle_id}",
        )
    for ordinary_amulet in (
        "ItemId.SAPPHIRE_AMULET.id()",
        "ItemId.EMERALD_AMULET.id()",
        "ItemId.RUBY_AMULET.id()",
        "ItemId.DIAMOND_AMULET.id()",
        "ItemId.UNENCHANTED_DRAGONSTONE_AMULET.id()",
    ):
        require(ordinary_amulet not in method_body(effects, "isBangleBase"),
                f"ordinary Amulet leaked into active altar inputs: {ordinary_amulet}")


def ensure_zero_visual_equip_lifecycle_contract() -> None:
    client = MUDCLIENT.read_text(encoding="utf-8")
    policy = INVENTORY_EQUIP_POLICY.read_text(encoding="utf-8")
    bank = CLIENT_BANK.read_text(encoding="utf-8")
    item_equip = ITEM_EQUIP.read_text(encoding="utf-8")
    item_unequip = ITEM_UNEQUIP.read_text(encoding="utf-8")
    equipment = EQUIPMENT.read_text(encoding="utf-8")
    player = PLAYER.read_text(encoding="utf-8")

    require(
        "InventoryEquipMenuPolicy.canOfferEquip(def, item.getNoted())" in client,
        "inventory menu should use the real wieldable flag",
    )
    require(
        "EntityHandler.getItemDef(id).wearableID != 0" not in client,
        "inventory menu still treats a visual wearable ID as equipability",
    )
    require(
        "item != null && item.isWieldable() && !noted" in policy,
        "equip policy should accept wieldable zero-visual-ID items and reject notes",
    )
    require(
        "(item.wearableID & 24) != 0 ? \"Wield\" : \"Wear\"" in policy,
        "visual type should affect only the Wear/Wield label",
    )
    require(
        "case ITEM_EQUIP_FROM_INVENTORY:" in client
        and "newPacket(Opcodes.Out.ITEM_EQUIP_FROM_INVENTORY.getOpcode())" in client,
        "inventory Wear action should send the equip packet",
    )

    require(
        bank.count("getItemDef().isWieldable()") >= 6
        and "newPacket(172)" in bank,
        "bank equipment paths should admit zero-visual-ID wieldable items",
    )
    require(
        "opcode == OpcodeIn.ITEM_EQUIP_FROM_INVENTORY" in item_equip
        and "opcode == OpcodeIn.ITEM_EQUIP_FROM_BANK" in item_equip
        and "!request.item.getDef(player.getWorld()).isWieldable()" in item_equip,
        "server equip handler should accept wieldable items from inventory and bank",
    )
    require(
        "equipItemFromInventory(request, updateClient)" in equipment
        and "equipItemFromBank(request, updateClient)" in equipment,
        "equipment container should complete both inventory and bank equip paths",
    )
    require(
        "player.updateWornItems(itemDef.getWieldPosition(), itemDef.getAppearanceId(), itemDef.getWearableId(), true);"
        in equipment,
        "equip completion should use independent slot and visual fields",
    )

    require(
        "MenuItemAction.ITEM_UNEQUIP_FROM_EQUIPMENT" in client
        and "newPacket(Opcodes.Out.ITEM_UNEQUIP_FROM_EQUIPMENT.getOpcode())" in client,
        "equipment interface should expose and send the unequip action",
    )
    require(
        "opcode == OpcodeIn.ITEM_UNEQUIP_FROM_EQUIPMENT" in item_unequip
        and "Equipment.correctIndex(request);" in item_unequip
        and "!request.item.getDef(player.getWorld()).isWieldable()" in item_unequip,
        "server unequip handler should map the client slot and accept zero-visual equipment",
    )
    correct_index = method_body(equipment, "correctIndex")
    require(
        "request.equipmentSlot.getIndex() + 3" in correct_index,
        "client logical wrist slot 11 should map back to server wrist slot 14",
    )

    require(
        "getEquippedItemIdInServerSlot(14)" in client
        and "EquipmentSlotMapping.serverToClient(serverSlot)" in client,
        "client equipped lookups should use slot mapping instead of wearableID",
    )
    require(
        "item.wearableID == slot" not in client,
        "client equipped lookup still treats visual wearable ID as a slot",
    )
    require(
        "if (indexPosition <= 11)" in player
        and "indexPosition == AppearanceId.SLOT_MORPHING_RING" in player,
        "wrist slot 14 should remain outside character appearance layers",
    )


def ensure_acquisition_and_test_utility_contract() -> None:
    shops = CRAFTING_SHOPS.read_text(encoding="utf-8")
    starter = ACTION_SENDER.read_text(encoding="utf-8")
    admins = ADMINS.read_text(encoding="utf-8")
    superchisel = SUPERCHISEL.read_text(encoding="utf-8")

    require(
        "new Item(MyWorldItemId.BANGLE_MOULD, 2)" in shops
        and "ItemId.AMULET_MOULD" not in shops,
        "crafting-equipment shops should replace the retired mould with the Bangle mould",
    )
    require(
        "new Item(MyWorldItemId.BANGLE_MOULD)" in starter
        and "ItemId.AMULET_MOULD" not in starter,
        "My World starter crafting tools should supply only the Bangle mould",
    )
    require(
        admins.count("LegacyAmuletCompatibility.canonicalCatalogId(") >= 4
        and "LegacyAmuletCompatibility.isRetiredCatalogId(i)" in admins,
        "admin item utilities should canonicalize direct IDs and omit retired Amulet supplies",
    )

    utility = method_body(superchisel, "onUseInv")
    require(
        "notSuperchisel.getCatalogId() == MyWorldItemId.BANGLE_MOULD" in utility,
        "Superchisel Bangle testing should be opened with the Bangle mould",
    )
    require(
        'multi(player, "Gold", "Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone")'
        in utility,
        "Superchisel should offer all six unenchanted Bangles",
    )
    for token in (
        "MyWorldItemId.GOLD_BANGLE",
        "MyWorldItemId.SAPPHIRE_BANGLE",
        "MyWorldItemId.EMERALD_BANGLE",
        "MyWorldItemId.RUBY_BANGLE",
        "MyWorldItemId.DIAMOND_BANGLE",
        "MyWorldItemId.DRAGONSTONE_BANGLE",
        "ItemId.UNCUT_DRAGONSTONE",
        "ItemId.DRAGONSTONE",
    ):
        require(token in utility, f"Superchisel testing support omits {token}")
    require(
        "ItemId.AMULET_MOULD" not in utility,
        "Superchisel must not reacquire the retired Amulet mould",
    )
    blocker = method_body(superchisel, "blockUseInv")
    require(
        "MyWorldItemId.BANGLE_MOULD" in blocker
        and "ItemId.UNCUT_DRAGONSTONE.id()" in blocker
        and "ItemId.BALL_OF_WOOL.id(), ItemId.SUPERCHISEL.id()" not in blocker,
        "Superchisel routing should use the Bangle mould, not wool, and cover Dragonstone",
    )


def ensure_standard_spell_and_retirement_contract(
    items: dict[int, dict[str, Any]],
) -> None:
    spells = SPELL_HANDLER.read_text(encoding="utf-8")
    spell_pairs = (
        ("SAPPHIRE_BANGLE", "SAPPHIRE_AMULET_OF_MAGIC"),
        ("EMERALD_BANGLE", "EMERALD_AMULET_OF_PROTECTION"),
        ("RUBY_BANGLE", "RUBY_AMULET_OF_STRENGTH"),
        ("DIAMOND_BANGLE", "DIAMOND_AMULET_OF_POWER"),
        ("DRAGONSTONE_BANGLE", "CHARGED_DRAGONSTONE_AMULET"),
    )
    for input_name, compatibility_output_name in spell_pairs:
        require(
            f"MyWorldItemId.{input_name}" in spells
            and f"ItemId.{compatibility_output_name}.id()" in spells,
            f"standard enchantment spell missing {input_name} Bangle conversion",
        )
    require("private void enchantBangle(" in spells,
            "standard enchantment spells should share the successful Bangle path")

    for crafting_path in (CRAFTING_DEFS, RETRO_CRAFTING_DEFS):
        crafting_source = crafting_path.read_text(encoding="utf-8")
        require("<!-- Amulet -->" not in crafting_source,
                f"retired Amulet recipe family remains scaffolded in {crafting_path.name}")
        crafting_ids = {
            int(definition.findtext("itemID", "-1"))
            for definition in ET.parse(crafting_path).getroot().iter("ItemCraftingDef")
        }
        for retired_id in RETIRED_AMULET_IDS:
            require(retired_id not in crafting_ids,
                    f"retired Amulet ID {retired_id} remains craftable in {crafting_path.name}")
        for bangle_id in (GOLD_BANGLE,) + BASE_BANGLES:
            require(bangle_id in crafting_ids,
                    f"Bangle ID {bangle_id} missing from {crafting_path.name}")

    allowed_legacy_sources = {
        Path("src/com/openrsc/server/constants/ItemId.java"),
        Path("src/com/openrsc/server/content/LegacyAmuletCompatibility.java"),
        Path("src/com/openrsc/server/external/EntityHandler.java"),
    }
    for path in SERVER.rglob("*.java"):
        relative = path.relative_to(SERVER)
        if relative in allowed_legacy_sources:
            continue
        source = path.read_text(encoding="utf-8")
        for token in RETIRED_AMULET_TOKENS:
            require(token not in source,
                    f"retired acquisition token {token} remains in {relative}")

    for path in (SERVER / "conf/server/defs/locs").glob("GroundItems*.json"):
        source = path.read_text(encoding="utf-8")
        for retired_id in RETIRED_AMULET_IDS:
            require(
                re.search(rf'"id"\s*:\s*{retired_id}(?:\s*[,}}])', source) is None,
                f"retired Amulet ID {retired_id} remains a static spawn in {path.name}",
            )

    guide = SKILL_GUIDE.read_text(encoding="utf-8")
    for retired_id in (296, 297, 298, 299, 300, 524):
        require(f"new SkillMenuItem({retired_id}," not in guide,
                f"retired Amulet ID {retired_id} remains in the Crafting guide")
    expected_guide_entries = (
        (3292, 5, "Gold"),
        (3282, 13, "Sapphire"),
        (3283, 26, "Emerald"),
        (3284, 44, "Ruby"),
        (3285, 60, "Diamond"),
        (3286, 70, "Dragonstone"),
    )
    for item_id, level, tier in expected_guide_entries:
        require(
            f'new SkillMenuItem({item_id}, "{level}", "{tier} Bangle")' in guide,
            f"Crafting guide should advertise the level-{level} {tier} Bangle",
        )

    item_ids = (
        SERVER / "src/com/openrsc/server/constants/ItemId.java"
    ).read_text(encoding="utf-8")
    for quest_exception in (
        "AMULET_OF_GHOSTSPEAK(24)",
        "AMULET_OF_ACCURACY(235)",
        "GNOME_EMERALD_AMULET_OF_PROTECTION(744)",
        "GLARIALS_AMULET(782)",
        "KING_LATHAS_AMULET(826)",
        "AMULET_OF_OTHAINIAN(1009)",
        "AMULET_OF_DOOMION(1010)",
        "AMULET_OF_HOLTHION(1011)",
    ):
        require(quest_exception in item_ids,
                f"quest-specific Amulet exception was removed: {quest_exception}")

    for retired_id in RETIRED_AMULET_IDS:
        require(retired_id not in STANDARD_ENCHANTED_BANGLES,
                f"retired base ID {retired_id} must not masquerade as a real item")
    require(items[GOLD_BANGLE]["name"] == "Gold Bangle",
            "plain Gold Bangle definition is missing")


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
        "getGatheringAmuletYieldBonusPercent",
		"getAnglerBangleBestCatchChanceBonusPercent",
        "getCosmicAmuletExtraResourceChance",
        "getCosmicAmuletRareGatheringDoubleChance",
        "getCosmicAmuletGemChanceMultiplier",
        "getCosmicAmuletHerbQualityChance",
        "getSoulAmuletBurstRadius",
        "getSoulAmuletBurstMinHeal",
        "getSoulAmuletBurstMaxHeal",
    )
    for getter in wrist_effect_getters:
        body = method_body(equipment, getter)
        require("getEquippedWristItem()" in body,
                f"{getter} should read the wrist slot")
        require("getEquippedNeckItem()" not in body,
                f"{getter} still reads the neck slot")

    neck_effect_getters = (
        "getChaosNecklaceChainLightningChance",
        "getBloodNecklaceLeachPercent",
        "rollDeathNecklaceGuaranteedDropBonus",
        "getLifeNecklaceSummonHealthPercent",
        "getNatureCleansingPoisonDecayBonus",
        "getCosmicNecklaceStandardDropChance",
        "tryBankMonsterLootWithLawNecklace",
        "getSoulNecklaceExtraKeptItems",
        "getEquippedElementalDefenseBonus",
    )
    for getter in neck_effect_getters:
        body = method_body(equipment, getter)
        require("getEquippedNeckItem()" in body,
                f"{getter} should read the neck slot")
        require("getEquippedWristItem()" not in body,
                f"{getter} incorrectly reads the wrist slot")

    ring_effect_getters = (
        "getChaosRecoilChance",
        "getLifeRingSupportDurationPercent",
        "getBloodRingHitsBonus",
        "getNatureFoodHealingBonus",
        "bankSkillingDropWithLawRing",
        "getEquippedElementalPowerBonus",
    )
    for getter in ring_effect_getters:
        body = method_body(equipment, getter)
        require("getEquippedRingItem()" in body,
                f"{getter} should read the ring slot")
        require("getEquippedNeckItem()" not in body,
                f"{getter} incorrectly reads the neck slot")
        require("getEquippedWristItem()" not in body,
                f"{getter} incorrectly reads the wrist slot")

    for combined in ("getMindJewelryXpBonus", "getBodyJewelryXpBonus"):
        body = method_body(equipment, combined)
        for accessor in (
            "getEquippedWristItem()",
            "getEquippedNeckItem()",
            "getEquippedRingItem()",
        ):
            require(accessor in body,
                    f"{combined} should combine Bangle, Necklace, and Ring")

    require("getEquippedWristItem();" in method_body(player, "applyDeathAmuletBurst"),
            "death Bangle burst should resolve from wrist")
    require("getEquippedWristItem();" in method_body(player, "applySoulAmuletBurst"),
            "soul Bangle burst should resolve from wrist")

    for item_id in (*ENCHANTED_BANGLES, *STANDARD_ENCHANTED_BANGLES):
        require(items[item_id]["wearSlot"] == 14,
                f"Bangle {item_id} should occupy the wrist slot")
    for item_id in ENCHANTED_NECKLACES:
        require(items[item_id]["wearSlot"] == 10,
                f"Necklace {item_id} should remain in the neck slot")
    for item_id in ENCHANTED_RINGS:
        require(items[item_id]["wearSlot"] == 13,
                f"Ring {item_id} should remain in the ring slot")


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
    ensure_bangle_crafting(items)
    ensure_zero_visual_equip_lifecycle_contract()
    ensure_acquisition_and_test_utility_contract()
    ensure_standard_spell_and_retirement_contract(items)
    ensure_effect_slot_contract(items)
    ensure_hidden_medallions(items)
    print("PASS: Bangle wrist jewelry, compatibility, crafting, and hidden Medallion gates validated")


if __name__ == "__main__":
    main()
