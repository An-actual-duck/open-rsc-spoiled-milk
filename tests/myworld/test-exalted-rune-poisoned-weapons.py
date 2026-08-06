#!/usr/bin/env python3
"""Guard complete Exalted Rune weapon-poison parity and runtime classification."""

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CUSTOM_ITEMS = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
OVERRIDES = ROOT / "server/conf/server/defs/ItemDefsMyWorld.json"
SOURCE_OVERRIDES = ROOT / "tools/generators/item-overrides/50-exalted-rune.json"
MYWORLD_ITEM_ID = ROOT / "server/src/com/openrsc/server/constants/custom/MyWorldItemId.java"
ITEM_ID = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
POISONING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/itemactions/InvItemPoisoning.java"
POISON_POWER = ROOT / "server/src/com/openrsc/server/content/PoisonPower.java"
PVM_MELEE = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java"
CLIENT_ITEMS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
DOCUMENTATION = ROOT / "docs/myworld/info/exalted-rune-poisoned-weapons.md"

WEAPON_PAIRS = {
    3262: (3309, "DAGGER"),
    3263: (3310, "SHORT_SWORD"),
    3264: (3311, "LONG_SWORD"),
    3265: (3312, "SCIMITAR"),
    3266: (3313, "2_HANDED_SWORD"),
    3269: (3314, "BATTLE_AXE"),
    3270: (3315, "SCYTHE"),
    3271: (3316, "MACE"),
    3273: (3317, "SPEAR"),
}

SERVER_PARITY_FIELDS = (
    "command",
    "isFemaleOnly",
    "isMembersOnly",
    "isStackable",
    "isUntradable",
    "isWearable",
    "appearanceID",
    "wearableID",
    "wearSlot",
    "requiredLevel",
    "requiredSkillID",
    "armourBonus",
    "weaponAimBonus",
    "weaponPowerBonus",
    "magicBonus",
    "prayerBonus",
    "basePrice",
    "isNoteable",
    "meleeOffense",
    "weaponSpeed",
)


def load_items(path: Path) -> dict[int, dict]:
    return {
        int(entry["id"]): entry
        for entry in json.loads(path.read_text(encoding="utf-8"))["items"]
    }


def effective_items() -> dict[int, dict]:
    items = load_items(CUSTOM_ITEMS)
    for item_id, override in load_items(OVERRIDES).items():
        items[item_id] = {**items.get(item_id, {}), **override}
    return items


def client_definitions() -> dict[int, tuple]:
    source = CLIENT_ITEMS.read_text(encoding="utf-8")
    pattern = re.compile(
        r'setCustomItemDefinition\((\d+),\s*new ItemDef\('
        r'"([^"]+)",\s*"[^"]*",\s*"[^"]*",\s*'
        r'(\d+),\s*(-?\d+),\s*"([^"]+)",\s*'
        r'(true|false),\s*(true|false),\s*(\d+),\s*([^,]+),\s*'
        r'(true|false),\s*(true|false),\s*(true|false),\s*(\d+)\)\);',
        re.DOTALL,
    )
    definitions: dict[int, tuple] = {}
    for match in pattern.finditer(source):
        item_id = int(match.group(1))
        definitions[item_id] = (
            match.group(2),
            int(match.group(3)),
            int(match.group(4)),
            match.group(5),
            match.group(6),
            match.group(7),
            int(match.group(8)),
            match.group(9).strip(),
            match.group(10),
            match.group(11),
            match.group(12),
            int(match.group(13)),
        )
    return definitions


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    items = effective_items()
    source_items = load_items(SOURCE_OVERRIDES)
    client = client_definitions()
    constants = MYWORLD_ITEM_ID.read_text(encoding="utf-8")
    item_id_source = ITEM_ID.read_text(encoding="utf-8")
    poisoning = POISONING.read_text(encoding="utf-8")
    poison_power = POISON_POWER.read_text(encoding="utf-8")
    pvm_melee = PVM_MELEE.read_text(encoding="utf-8")

    for plain_id, (poisoned_id, constant_suffix) in WEAPON_PAIRS.items():
        plain = items.get(plain_id)
        poisoned = items.get(poisoned_id)
        if plain is None or poisoned is None:
            fail(f"missing Exalted Rune weapon pair {plain_id}/{poisoned_id}")
        if poisoned.get("name") != "Poisoned " + plain.get("name", ""):
            fail(f"weapon poison name lookup cannot resolve pair {plain_id}/{poisoned_id}")
        for field in SERVER_PARITY_FIELDS:
            if plain.get(field) != poisoned.get(field):
                fail(
                    f"{poisoned['name']} field {field}={poisoned.get(field)!r} "
                    f"does not preserve {plain['name']} value {plain.get(field)!r}"
                )
        if poisoned_id not in source_items:
            fail(f"poisoned override {poisoned_id} is absent from authoritative generator input")

        constant = f"POISONED_EXALTED_RUNE_{constant_suffix}"
        if f"{constant} = {poisoned_id};" not in constants:
            fail(f"missing stable constant {constant}={poisoned_id}")
        if f"case MyWorldItemId.{constant}:" not in poison_power:
            fail(f"{constant} is not classified by PoisonPower")

        plain_client = client.get(plain_id)
        poisoned_client = client.get(poisoned_id)
        if plain_client is None or poisoned_client is None:
            fail(f"missing client weapon pair {plain_id}/{poisoned_id}")
        if poisoned_client[0] != "Poisoned " + plain_client[0]:
            fail(f"client poison name parity failed for {plain_id}/{poisoned_id}")
        if poisoned_client[1:-1] != plain_client[1:-1]:
            fail(f"client appearance/equipment parity failed for {plain_id}/{poisoned_id}")
        if poisoned_client[-1] != poisoned_id:
            fail(f"client definition identity drift for {poisoned_id}")

    if 'String poisonedVersion = "Poisoned " + name;' not in poisoning:
        fail("weapon poison no longer uses the established exact-name conversion")
    if "return 12;" not in poison_power:
        fail("poisoned Exalted Rune weapons do not receive tier-12 poison power")
    if "MyWorldItemId.POISONED_EXALTED_RUNE_SCYTHE" not in pvm_melee:
        fail("poisoned Exalted Rune Scythe lost sweeping cleave recognition")
    if "public static final int maxCustom = 3318;" not in item_id_source:
        fail("exclusive custom item count does not cover IDs through 3317")

    excluded_names = {
        "Poisoned Exalted Rune Hatchet",
        "Poisoned Exalted Rune Pickaxe",
        "Poisoned Exalted Rune shears",
    }
    actual_names = {str(item.get("name", "")) for item in items.values()}
    unexpected = sorted(excluded_names & actual_names)
    if unexpected:
        fail(f"gathering tools unexpectedly became poisonable: {unexpected}")

    if not DOCUMENTATION.is_file():
        fail("Exalted Rune poison family documentation is missing")

    print("PASS: all nine Exalted Rune combat weapons preserve full parity and tier-12 poison behavior")


if __name__ == "__main__":
    main()
