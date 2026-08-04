#!/usr/bin/env python3
"""Ensure unified platebody items use female top visuals on female bodies."""

import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
APPEARANCES = ROOT / "server/src/com/openrsc/server/constants/AppearanceId.java"
EQUIPMENT = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
ITEM_DEFS = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start == -1:
        fail(f"Missing method signature: {signature}")
    brace = source.find("{", start)
    if brace == -1:
        fail(f"Missing method body for: {signature}")
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    fail(f"Unclosed method body for: {signature}")


def require(source: str, snippet: str, message: str) -> None:
    if snippet not in source:
        fail(message)


def require_mapping(body: str, male_appearance: str, female_appearance: str, label: str) -> None:
    require(body, f"case {male_appearance}:", f"{label} body appearance should be remapped for female bodies")
    require(
        body,
        f"return AppearanceId.{female_appearance}.id();",
        f"{label} should resolve to the matching female top appearance",
    )


def item_by_id(items: list[dict], item_id: int) -> dict:
    for item in items:
        if item.get("id") == item_id:
            return item
    fail(f"Missing item definition {item_id}")


def require_item_appearance(items: list[dict], item_id: int, appearance_id: int, label: str) -> None:
    item = item_by_id(items, item_id)
    actual = item.get("appearanceID")
    if actual != appearance_id:
        fail(f"{label} should use appearance {appearance_id}, found {actual}")


def main() -> int:
    player = PLAYER.read_text(encoding="utf-8")
    appearances = APPEARANCES.read_text(encoding="utf-8")
    equipment = EQUIPMENT.read_text(encoding="utf-8")
    entity_handler = ENTITY_HANDLER.read_text(encoding="utf-8")
    items = json.loads(ITEM_DEFS.read_text(encoding="utf-8"))["items"]

    resolver = method_body(player, "private int resolveBodyAppearance")
    body_slot_check = method_body(player, "private boolean isBodyAppearanceSlot")
    require(resolver, "isBodyAppearanceSlot(indexPosition)", "Female plate remapping should only affect body appearance slots")
    require(resolver, "usesFemaleBodySprite()", "Female plate remapping should only run for female body sprites")
    require(body_slot_check, "AppearanceId.SLOT_SHIRT", "Platebodies equip through the shirt/base-body equipment slot")
    require(body_slot_check, "AppearanceId.SLOT_BODY", "Body overlay slot should also support female plate remapping")

    legacy_plate_mappings = [
        ("BRONZE_PLATE_MAIL_BODY", "FEMALE_BRONZE_PLATE_MAIL_TOP", "bronze plate"),
        ("IRON_PLATE_MAIL_BODY", "FEMALE_IRON_PLATE_MAIL_TOP", "iron plate"),
        ("STEEL_PLATE_MAIL_BODY", "FEMALE_STEEL_PLATE_MAIL_TOP", "steel plate"),
        ("MITHRIL_PLATE_MAIL_BODY", "FEMALE_MITHRIL_PLATE_MAIL_TOP", "mithril plate"),
        ("ADAMANTITE_PLATE_MAIL_BODY", "FEMALE_ADAMANTITE_PLATE_MAIL_TOP", "adamantite plate"),
        ("BLACK_PLATE_MAIL_BODY", "FEMALE_BLACK_PLATE_MAIL_TOP", "black plate"),
        ("RUNE_PLATE_MAIL_BODY", "FEMALE_RUNE_PLATE_MAIL_TOP", "rune plate"),
        ("WHITE_PLATE_MAIL_BODY", "FEMALE_WHITE_PLATE_MAIL_TOP", "white plate"),
    ]
    for male_appearance, female_appearance, label in legacy_plate_mappings:
        require_mapping(resolver, male_appearance, female_appearance, label)

    modern_plate_mappings = [
        ("DRAGON_PLATE_MAIL_BODY", "FEMALE_DRAGON_PLATE_MAIL_TOP", "dragon plate"),
        ("IRONMAN_PLATEBODY", "FEMALE_IRONMAN_PLATE_TOP", "ironman plate"),
        ("ULTIMATE_IRONMAN_PLATEBODY", "FEMALE_ULTIMATE_IRONMAN_PLATE_TOP", "ultimate ironman plate"),
        ("HARDCORE_IRONMAN_PLATEBODY", "FEMALE_HARDCORE_IRONMAN_PLATE_TOP", "hardcore ironman plate"),
    ]
    for male_appearance, female_appearance, label in modern_plate_mappings:
        require_mapping(resolver, male_appearance, female_appearance, label)

    custom_plate_mappings = [
        ("TIN_PLATE_MAIL_BODY", "FEMALE_TIN_PLATE_MAIL_TOP", "tin plate"),
        ("COPPER_PLATE_MAIL_BODY", "FEMALE_COPPER_PLATE_MAIL_TOP", "copper plate"),
        ("TITAN_STEEL_PLATE_MAIL_BODY", "FEMALE_TITAN_STEEL_PLATE_MAIL_TOP", "titan steel plate"),
        ("ORICHALCUM_PLATE_MAIL_BODY", "FEMALE_ORICHALCUM_PLATE_MAIL_TOP", "orichalcum plate"),
        ("EXALTED_RUNE_PLATE_MAIL_BODY", "FEMALE_EXALTED_RUNE_PLATE_MAIL_TOP", "exalted rune plate"),
    ]
    for male_appearance, female_appearance, label in custom_plate_mappings:
        require_mapping(resolver, male_appearance, female_appearance, label)

    require(appearances, "FEMALE_WHITE_PLATE_MAIL_TOP(158, BODY)", "White/grey female plate top should use the client fplatemailtop animation")
    require(appearances, "FEMALE_DRAGON_PLATE_MAIL_TOP(299, BODY)", "Dragon female plate top appearance should be named")
    require(appearances, "FEMALE_IRONMAN_PLATE_TOP(535, BODY)", "Ironman female plate top appearance should be named")
    require(appearances, "TIN_PLATE_MAIL_BODY(690, BODY)", "Tin platebody appearance should be named")
    require(appearances, "COPPER_PLATE_MAIL_BODY(691, BODY)", "Copper platebody appearance should be named")
    require(appearances, "TITAN_STEEL_PLATE_MAIL_BODY(692, BODY)", "Titan steel platebody appearance should be named")
    require(appearances, "ORICHALCUM_PLATE_MAIL_BODY(693, BODY)", "Orichalcum platebody appearance should be named")
    require(appearances, "FEMALE_TIN_PLATE_MAIL_TOP(1038, BODY)", "Tin female plate top appearance should be named")
    require(appearances, "FEMALE_COPPER_PLATE_MAIL_TOP(1039, BODY)", "Copper female plate top appearance should be named")
    require(appearances, "FEMALE_TITAN_STEEL_PLATE_MAIL_TOP(1040, BODY)", "Titan steel female plate top appearance should be named")
    require(appearances, "FEMALE_ORICHALCUM_PLATE_MAIL_TOP(1041, BODY)", "Orichalcum female plate top appearance should be named")
    require(appearances, "EXALTED_RUNE_PLATE_MAIL_BODY(1060, BODY)", "Exalted Rune platebody appearance should be named")
    require(appearances, "FEMALE_EXALTED_RUNE_PLATE_MAIL_TOP(1061, BODY)", "Exalted Rune female plate top appearance should be named")
    require(
        entity_handler,
        'new AnimationDef("fplatemailtop", "equipment", 0xB7C9D9, 0, true, false, 0)); // 1037 - Tin female plate top',
        "Tin female plate top should have a client fplatemailtop animation",
    )
    require(
        entity_handler,
        'new AnimationDef("fplatemailtop", "equipment", 0xC86A2B, 0, true, false, 0)); // 1038 - Copper female plate top',
        "Copper female plate top should have a client fplatemailtop animation",
    )
    require(
        entity_handler,
        'new AnimationDef("fplatemailtop", "equipment", 0x8EA6BB, 0, true, false, 0)); // 1039 - Titan Steel female plate top',
        "Titan steel female plate top should have a client fplatemailtop animation",
    )
    require(
        entity_handler,
        'new AnimationDef("fplatemailtop", "equipment", 0x5A3F7D, 0, true, false, 0)); // 1040 - Orichalcum female plate top',
        "Orichalcum female plate top should have a client fplatemailtop animation",
    )
    require(
        entity_handler,
        'new AnimationDef("fplatemailtop", "equipment", EXALTED_RUNE_COLOR, 0, true, false, 0)); // 1060 - Exalted Rune female plate top',
        "Exalted Rune female plate top should have a client fplatemailtop animation",
    )

    require(equipment, "ItemId.DRAGON_PLATE_MAIL_BODY.id()", "Dragon platebody should stay in the body normalization bucket")
    require(equipment, "ItemId.DRAGON_PLATE_MAIL_TOP.id()", "Dragon plate top should normalize into the body item")
    require(equipment, "replaceRequestedItem(request, plateBodyIds[i])", "Plate tops should be replaced by their unified body item")

    require_item_appearance(items, 1427, 296, "Dragon plate mail body")
    require_item_appearance(items, 1428, 299, "Dragon plate mail top compatibility item")
    require_item_appearance(items, 1291, 319, "Ironman platebody")
    require_item_appearance(items, 1294, 322, "Ultimate ironman platebody")
    require_item_appearance(items, 1297, 325, "Hardcore ironman platebody")
    require_item_appearance(items, 1554, 535, "Ironman plate top compatibility item")
    require_item_appearance(items, 1555, 536, "Ultimate ironman plate top compatibility item")
    require_item_appearance(items, 1556, 537, "Hardcore ironman plate top compatibility item")
    require_item_appearance(items, 1964, 690, "Tin plate mail body")
    require_item_appearance(items, 1970, 691, "Copper plate mail body")
    require_item_appearance(items, 1976, 692, "Titan steel plate mail body")
    require_item_appearance(items, 1982, 693, "Orichalcum plate mail body")
    require_item_appearance(items, 3280, 1060, "Exalted Rune plate mail body")

    print("PASS: unified platebodies resolve to female top visuals for female bodies")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
