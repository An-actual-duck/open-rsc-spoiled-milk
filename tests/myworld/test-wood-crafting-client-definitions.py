#!/usr/bin/env python3
"""Validate client definitions for knife-on-log crafting outputs."""

import re
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
FLETCHING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/fletching/Fletching.java"
ITEM_LOG_CUT_DEF = ROOT / "server/conf/server/defs/extras/ItemLogCutDef.xml"
ITEM_BOW_STRING_DEF = ROOT / "server/conf/server/defs/extras/ItemBowStringDef.xml"


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, message: str) -> None:
    if snippet not in text:
        fail(message)


def require_helper_uses_explicit_ids(client_text: str, helper_name: str, expected_calls: tuple[str, ...]) -> None:
    match = re.search(
        rf"private static void {helper_name}\(.*?\n\t\}}",
        client_text,
        re.DOTALL,
    )
    if match is None:
        fail(f"Missing client helper {helper_name}")
    helper = match.group(0)
    if "items.add(new ItemDef" in helper:
        fail(f"{helper_name} must not append sparse item IDs with items.add")
    for call in expected_calls:
        require(helper, call, f"{helper_name} missing explicit item-slot write: {call}")


def main() -> None:
    client_text = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    fletching_text = FLETCHING.read_text(encoding="utf-8")
    log_cut_text = ITEM_LOG_CUT_DEF.read_text(encoding="utf-8")
    bow_string_text = ITEM_BOW_STRING_DEF.read_text(encoding="utf-8")

    require_helper_uses_explicit_ids(
        client_text,
        "addCustomWoodBowDefinitions",
        (
            "setCustomItemDefinition(unstrungLongId,",
            "setCustomItemDefinition(unstrungShortId,",
            "setCustomItemDefinition(longbowId,",
            "setCustomItemDefinition(shortbowId,",
        ),
    )
    require_helper_uses_explicit_ids(
        client_text,
        "addWoodCrossbowDefinition",
        ("setCustomItemDefinition(id, item);",),
    )
    require_helper_uses_explicit_ids(
        client_text,
        "addCustomWoodStaffDefinitions",
        (
            "setCustomItemDefinition(baseStaffId,",
            "setCustomItemDefinition(airStaffId,",
            "setCustomItemDefinition(waterStaffId,",
            "setCustomItemDefinition(earthStaffId,",
            "setCustomItemDefinition(fireStaffId,",
        ),
    )

    for item_name in (
        "PINE_STAFF",
        "PALM_STAFF",
        "EBONY_STAFF",
        "BLOOD_STAFF",
        "PINE_FISHING_ROD",
        "BLOOD_FISHING_ROD",
        "UNSTRUNG_PINE_SHORTBOW",
        "UNSTRUNG_BLOOD_LONGBOW",
        "OAK_CROSSBOW",
        "BLOOD_CROSSBOW",
    ):
        require(
            fletching_text,
            f"ItemId.{item_name}.id()",
            f"Knife-on-log crafting should still reference {item_name}",
        )

    for item_name in (
        "UNSTRUNG_YEW_SHORTBOW",
        "UNSTRUNG_YEW_LONGBOW",
        "UNSTRUNG_MAGIC_SHORTBOW",
        "UNSTRUNG_MAGIC_LONGBOW",
    ):
        require(
            fletching_text,
            f"ItemId.{item_name}.id()",
            f"Fletching should keep {item_name} registered as a stringable bow",
        )

    for snippet, message in (
        ("addStandardWoodBowDefinitions();", "Standard bow client definitions should be explicitly slotted"),
        ('addCustomWoodBowDefinitions("Oak", 658, 659, 648, 649,', "Oak bow client definitions should cover unstrung guide icons"),
        ('addCustomWoodBowDefinitions("Willow", 660, 661, 650, 651,', "Willow bow client definitions should cover unstrung guide icons"),
        ('addCustomWoodBowDefinitions("Maple", 662, 663, 652, 653,', "Maple bow client definitions should cover unstrung guide icons"),
        ('addCustomWoodBowDefinitions("Yew", 664, 665, 654, 655,', "Yew bow client definitions should cover unstrung guide icons"),
        ('addCustomWoodBowDefinitions("Ebony", 2123, 2124, 2125, 2126,', "Ebony bow client definitions should cover unstrung guide icons"),
        ('addCustomWoodBowDefinitions("Magic", 666, 667, 656, 657,', "Magic bow client definitions should cover unstrung guide icons"),
        ('addCustomWoodBowDefinitions("Blood", 2127, 2128, 2129, 2130,', "Blood bow client definitions should cover unstrung guide icons"),
    ):
        require(client_text, snippet, message)

    log_cut_bow_ids = {
        int(value)
        for value in re.findall(r"<(?:shortbowID|longbowID)>(\d+)</(?:shortbowID|longbowID)>", log_cut_text)
    }
    stringed_bow_ids = {
        int(value)
        for value in re.findall(r"<bowID>(\d+)</bowID>", bow_string_text)
    }
    helper_bow_ids = set()
    for call in re.findall(r"addCustomWoodBowDefinitions\((.*?)\);", client_text, re.DOTALL):
        helper_bow_ids.update(int(value) for value in re.findall(r"\b\d{3,4}\b", call)[:4])
    helper_bow_ids.update(
        int(value)
        for value in re.findall(r"setCustomItemDefinition\((\d+),\s*new ItemDef\(\"(?:unstrung|Longbow|Shortbow)", client_text)
    )

    for item_id in sorted((log_cut_bow_ids | stringed_bow_ids) - helper_bow_ids):
        fail(f"Bow output {item_id} should have an explicit client definition to avoid unobtainium guide icons")

    for snippet, message in (
        ("<shortbowID>665</shortbowID>", "Yew logs should cut into unstrung yew shortbows"),
        ("<longbowID>664</longbowID>", "Yew logs should cut into unstrung yew longbows"),
        ("<shortbowID>667</shortbowID>", "Magic logs should cut into unstrung magic shortbows"),
        ("<longbowID>666</longbowID>", "Magic logs should cut into unstrung magic longbows"),
    ):
        require(log_cut_text, snippet, message)

    for snippet, message in (
        ("<int>665</int>", "Unstrung yew shortbows should have a bow-stringing recipe"),
        ("<int>664</int>", "Unstrung yew longbows should have a bow-stringing recipe"),
        ("<int>667</int>", "Unstrung magic shortbows should have a bow-stringing recipe"),
        ("<int>666</int>", "Unstrung magic longbows should have a bow-stringing recipe"),
    ):
        require(bow_string_text, snippet, message)

    client_staff_ids = {
        int(value)
        for value in re.findall(
            r"MYWORLD_STAFF_BASE_IDS\s*=\s*\{(.*?)\};",
            client_text,
            re.DOTALL,
        )[0].replace("\n", " ").split(",")
        if value.strip()
    }
    rune_staff_block = re.findall(
        r"MYWORLD_RUNE_STAFF_IDS\s*=\s*\{(.*?)\};",
        client_text,
        re.DOTALL,
    )[0]
    client_staff_ids.update(int(value) for value in re.findall(r"\d+", rune_staff_block))

    legacy_staff_ids = {100, 101, 102, 103, 197}
    helper_staff_ids = set()
    for call in re.findall(r"addCustomWoodStaffDefinitions\((.*?)\);", client_text, re.DOTALL):
        helper_staff_ids.update(int(value) for value in re.findall(r"\b\d{3,4}\b", call))
    for first_id in re.findall(r"addBlessedStaffLine\((\d+),", client_text):
        helper_staff_ids.update(range(int(first_id), int(first_id) + 10))

    for item_id in sorted(client_staff_ids - legacy_staff_ids - helper_staff_ids):
        require(
            client_text,
            f"setCustomItemDefinition({item_id},",
            f"Craftable, enchanted, or blessed staff {item_id} should have an explicit client definition",
        )

    print("PASS: wood crafting client definitions validated")


if __name__ == "__main__":
    main()
