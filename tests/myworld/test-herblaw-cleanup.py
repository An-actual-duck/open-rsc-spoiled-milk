#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
DRINKABLES = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "itemactions" / "Drinkables.java"
HERBLAW = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "skills" / "herblaw" / "Herblaw.java"
APOTHECARY = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "varrock" / "Apothecary.java"
COMBINE_POTIONS = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "custom" / "itemactions" / "CombinePotions.java"
RUNECRAFT_POTION = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "custom" / "myworld" / "itemactions" / "RunecraftPotion.java"
ITEM_DEFS = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsCustom.json"

RETIRED_POTION_IDS = {1411, 1412, 1413, 1414, 1415, 1416}


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require_absent(text: str, snippet: str, message: str) -> None:
    if snippet in text:
        fail(message)


def main() -> None:
    drinkables_text = DRINKABLES.read_text(encoding="utf-8")
    herblaw_text = HERBLAW.read_text(encoding="utf-8")
    apothecary_text = APOTHECARY.read_text(encoding="utf-8")
    combine_potions_text = COMBINE_POTIONS.read_text(encoding="utf-8")
    runecraft_potion_text = RUNECRAFT_POTION.read_text(encoding="utf-8")
    defs = json.loads(ITEM_DEFS.read_text(encoding="utf-8"))

    for snippet in (
        "ItemId.FULL_RUNECRAFT_POTION.id()",
        "ItemId.TWO_RUNECRAFT_POTION.id()",
        "ItemId.ONE_RUNECRAFT_POTION.id()",
        "ItemId.FULL_SUPER_RUNECRAFT_POTION.id()",
        "ItemId.TWO_SUPER_RUNECRAFT_POTION.id()",
        "ItemId.ONE_SUPER_RUNECRAFT_POTION.id()",
    ):
        require_absent(
            drinkables_text,
            snippet,
            "Drinkables should not delegate retired runecraft/enchanting potions",
        )
        require_absent(
            apothecary_text,
            snippet,
            "Apothecary should not advertise retired runecraft/enchanting potions",
        )
        require_absent(
            runecraft_potion_text,
            snippet,
            "RunecraftPotion should remain an inert compatibility shell",
        )

    for snippet in (
        "ItemId.FULL_RANGING_POTION.id()",
        "ItemId.TWO_RANGING_POTION.id()",
        "ItemId.ONE_RANGING_POTION.id()",
    ):
        require_absent(
            apothecary_text,
            snippet,
            "Apothecary should not treat retired ranging potions as active vial utility input",
        )
        require_absent(
            combine_potions_text,
            snippet,
            "CombinePotions should not decant retired ranging potions",
        )

    require_absent(
        herblaw_text,
        "ItemId.FULL_RUNECRAFT_POTION.id()",
        "Herblaw should not craft retired runecraft/enchanting potions",
    )
    require_absent(
        herblaw_text,
        "ItemId.FULL_SUPER_RUNECRAFT_POTION.id()",
        "Herblaw should not craft retired super runecraft/enchanting potions",
    )

    item_defs = {item["id"]: item for item in defs["items"] if item["id"] in RETIRED_POTION_IDS}
    if set(item_defs) != RETIRED_POTION_IDS:
        fail("Expected retired enchanting potion item definitions to remain present for compatibility")

    for item_id, item in item_defs.items():
        if item.get("command") != "":
            fail(f"Retired enchanting potion item {item_id} should not be drinkable")

    print("PASS: retired herblaw potion cleanup validated")


if __name__ == "__main__":
    main()
