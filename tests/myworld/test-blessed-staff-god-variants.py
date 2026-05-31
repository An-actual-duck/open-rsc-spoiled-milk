#!/usr/bin/env python3
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ITEM_ID = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "constants" / "ItemId.java"
ITEMS = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsCustom.json"
PLUGIN = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "custom" / "myworld" / "skills" / "prayer" / "BlessedStaffs.java"
EQUIPMENT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "container" / "Equipment.java"
PLAYER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "player" / "Player.java"
CLIENT_HANDLER = ROOT / "Client_Base" / "src" / "com" / "openrsc" / "client" / "entityhandling" / "EntityHandler.java"
CLIENT = ROOT / "Client_Base" / "src" / "orsc" / "mudclient.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def main() -> None:
    item_id = ITEM_ID.read_text()
    plugin = PLUGIN.read_text()
    equipment = EQUIPMENT.read_text()
    player = PLAYER.read_text()
    client_handler = CLIENT_HANDLER.read_text()
    client = CLIENT.read_text()
    items = {item["id"]: item for item in json.loads(ITEMS.read_text())["items"]}

    max_custom = re.search(r"public static final int maxCustom = (\d+);", item_id)
    require(max_custom is not None and int(max_custom.group(1)) >= 3172,
            "ItemId.maxCustom must include all blessed staff god variants")

    expected = {
        3152: "Staff blessed by Saradomin",
        3161: "Blood staff blessed by Saradomin",
        3162: "Staff blessed by Guthix",
        3171: "Blood staff blessed by Guthix",
    }
    for item_id_value, name in expected.items():
        item = items.get(item_id_value)
        require(item is not None, f"missing item {item_id_value}")
        require(item["name"] == name, f"{item_id_value} name mismatch")
        require(item["magicBonus"] == 6, f"{name} magic bonus mismatch")
        require(item["prayerBonus"] == ((item_id_value - (3152 if item_id_value < 3162 else 3162)) + 1),
                f"{name} prayer tier mismatch")

    require("ItemId.SARADOMIN_BLESSED_STAFF.id() + tierIndex" in plugin,
            "Saradomin blessed staff products should be altar-specific")
    require("ItemId.GUTHIX_BLESSED_STAFF.id() + tierIndex" in plugin,
            "Guthix blessed staff products should be altar-specific")
    require("godLine == PrayerCatalog.GodLine.ZAMORAK" in plugin
            and "godLine == PrayerCatalog.GodLine.SARADOMIN" in plugin
            and "godLine == PrayerCatalog.GodLine.GUTHIX" in plugin,
            "Blessed staff plugin should branch by god line")
    require("isSaradominBlessedStaff(itemId)" in equipment and "isGuthixBlessedStaff(itemId)" in equipment,
            "Equipment should assign blessed staff variants to their god")
    require("ItemId.SARADOMIN_BLESSED_STAFF.id()" in player and "ItemId.GUTHIX_BLESSED_STAFF.id()" in player,
            "Player equipment checks should recognize all blessed staff variants")
    require("addAdditionalBlessedStaffDefinitions();" in client_handler,
            "Client handler should define Saradomin/Guthix blessed staffs")
    require("(item.id >= 3152 && item.id <= 3171)" in client,
            "Client magic offense should include Saradomin/Guthix blessed staffs")

    print("PASS: blessed staff god variants validated")


if __name__ == "__main__":
    main()
