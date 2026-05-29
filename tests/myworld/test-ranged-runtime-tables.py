#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FORMULAE_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "util" / "rsc" / "Formulae.java"
SERVER_ENTITY_HANDLER_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "external" / "EntityHandler.java"
CLIENT_ENTITY_HANDLER_PATH = ROOT / "Client_Base" / "src" / "com" / "openrsc" / "client" / "entityhandling" / "EntityHandler.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def extract_array_constants(text: str, name: str) -> list[str]:
    pattern = re.compile(rf"public static final int\[\] {name} = \{{(?P<body>.*?)\}};", re.DOTALL)
    match = pattern.search(text)
    if not match:
        fail(f"Could not find Formulae.{name}")
    return re.findall(r"ItemId\.([A-Z0-9_]+)\.id\(\)", match.group("body"))


def require_members(actual: list[str], expected: list[str], label: str) -> None:
    missing = [entry for entry in expected if entry not in actual]
    if missing:
        fail(f"{label} missing expected entries: {', '.join(missing)}")


def require_text(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        fail(f"{label} missing expected snippet: {snippet}")


def main() -> None:
    formulae_text = FORMULAE_PATH.read_text(encoding="utf-8")
    server_entity_handler_text = SERVER_ENTITY_HANDLER_PATH.read_text(encoding="utf-8")
    client_entity_handler_text = CLIENT_ENTITY_HANDLER_PATH.read_text(encoding="utf-8")

    arrow_ids = extract_array_constants(formulae_text, "arrowIDs")
    bolt_ids = extract_array_constants(formulae_text, "boltIDs")
    throwing_ids = extract_array_constants(formulae_text, "throwingIDs")

    require_members(
        arrow_ids,
        [
            "TIN_ARROWS",
            "COPPER_ARROWS",
            "TITAN_STEEL_ARROWS",
            "ORICHALCUM_ARROWS",
            "DRAGON_ARROWS",
            "POISON_DRAGON_ARROWS",
        ],
        "Formulae.arrowIDs",
    )
    require_members(
        bolt_ids,
        [
            "CROSSBOW_BOLTS",
            "COPPER_BOLTS",
            "BRONZE_BOLTS",
            "IRON_BOLTS",
            "STEEL_BOLTS",
            "MITHRIL_BOLTS",
            "TITAN_STEEL_BOLTS",
            "ADAMANTITE_BOLTS",
            "ORICHALCUM_BOLTS",
            "RUNE_BOLTS",
            "DRAGON_BOLTS",
            "POISON_DRAGON_BOLTS",
        ],
        "Formulae.boltIDs",
    )
    require_members(
        throwing_ids,
        [
            "TIN_THROWING_DART",
            "COPPER_THROWING_DART",
            "TITAN_STEEL_THROWING_DART",
            "ORICHALCUM_THROWING_DART",
            "POISONED_TIN_THROWING_DART",
            "POISONED_COPPER_THROWING_DART",
            "POISONED_TITAN_STEEL_THROWING_DART",
            "POISONED_ORICHALCUM_THROWING_DART",
            "TIN_THROWING_KNIFE",
            "COPPER_THROWING_KNIFE",
            "TITAN_STEEL_THROWING_KNIFE",
            "ORICHALCUM_THROWING_KNIFE",
            "POISONED_TIN_THROWING_KNIFE",
            "POISONED_COPPER_THROWING_KNIFE",
            "POISONED_TITAN_STEEL_THROWING_KNIFE",
            "POISONED_ORICHALCUM_THROWING_KNIFE",
        ],
        "Formulae.throwingIDs",
    )
    spear_throwing_ids = [entry for entry in throwing_ids if entry.endswith("_SPEAR")]
    if spear_throwing_ids:
        fail(f"Formulae.throwingIDs should not include melee spears: {', '.join(spear_throwing_ids)}")

    for arrow in [
        "TIN_ARROWS",
        "COPPER_ARROWS",
        "TITAN_STEEL_ARROWS",
        "ORICHALCUM_ARROWS",
    ]:
        require_text(
            server_entity_handler_text,
            f"ItemId.{arrow}.id()",
            f"Server ammo-slot registration for {arrow}",
        )

    for item_id, name in [
        (2039, "Tin Arrows"),
        (2040, "Copper Arrows"),
        (2041, "Titan Steel Arrows"),
        (2042, "Orichalcum Arrows"),
    ]:
        pattern = re.compile(
            rf'new ItemDef\("{re.escape(name)}", "Arrows with newly forged heads", "",'
            rf".*?Config\.S_WANT_EQUIPMENT_TAB, Config\.S_WANT_EQUIPMENT_TAB \? 1000 : 0,"
            rf".*?, {item_id}\)"
        )
        if not pattern.search(client_entity_handler_text):
            fail(f"Client definition for {name} must equip in the arrow ammo slot")

    for bolt in [
        "CROSSBOW_BOLTS",
        "POISON_CROSSBOW_BOLTS",
        "COPPER_BOLTS",
        "POISON_COPPER_BOLTS",
        "BRONZE_BOLTS",
        "POISON_BRONZE_BOLTS",
        "IRON_BOLTS",
        "POISON_IRON_BOLTS",
        "STEEL_BOLTS",
        "POISON_STEEL_BOLTS",
        "MITHRIL_BOLTS",
        "POISON_MITHRIL_BOLTS",
        "TITAN_STEEL_BOLTS",
        "POISON_TITAN_STEEL_BOLTS",
        "ADAMANTITE_BOLTS",
        "POISON_ADAMANTITE_BOLTS",
        "ORICHALCUM_BOLTS",
        "POISON_ORICHALCUM_BOLTS",
        "RUNE_BOLTS",
        "POISON_RUNE_BOLTS",
        "DRAGON_BOLTS",
        "POISON_DRAGON_BOLTS",
    ]:
        require_text(
            server_entity_handler_text,
            f"ItemId.{bolt}.id()",
            f"Server ammo-slot registration for {bolt}",
        )

    for item_id, name in [
        (190, "Tin bolts"),
        (592, "Poison Tin bolts"),
        (2178, "Copper bolts"),
        (2179, "Poison Copper bolts"),
        (2180, "Bronze bolts"),
        (2181, "Poison Bronze bolts"),
        (2182, "Iron bolts"),
        (2183, "Poison Iron bolts"),
        (2184, "Steel bolts"),
        (2185, "Poison Steel bolts"),
        (2186, "Mithril bolts"),
        (2187, "Poison Mithril bolts"),
        (2188, "Titan Steel bolts"),
        (2189, "Poison Titan Steel bolts"),
        (2190, "Adamantite bolts"),
        (2191, "Poison Adamantite bolts"),
        (2192, "Orichalcum bolts"),
        (2193, "Poison Orichalcum bolts"),
        (2194, "Rune bolts"),
        (2195, "Poison Rune bolts"),
    ]:
        require_text(
            client_entity_handler_text,
            f'addMetalBoltDefinition("{name}", {item_id},',
            f"Client metal bolt definition for {name}",
        )
    require_text(
        client_entity_handler_text,
        "Config.S_WANT_EQUIPMENT_TAB ? 1001 : 0",
        "Client bolt ammo-slot wearable id",
    )

    print("PASS: ranged runtime tables validated")
    print(f"arrowIDs entries: {len(arrow_ids)}")
    print(f"boltIDs entries: {len(bolt_ids)}")
    print(f"throwingIDs entries: {len(throwing_ids)}")


if __name__ == "__main__":
    main()
