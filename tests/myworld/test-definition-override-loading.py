#!/usr/bin/env python3

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEFS = ROOT / "server" / "conf" / "server" / "defs"
ENTITY_HANDLER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "external" / "EntityHandler.java"

NPC_FIELDS = {
    "id", "name", "description", "attack", "strength", "hits", "defense", "ranged",
    "projectileRange",
    "meleeOffense", "rangedOffense", "magicOffense",
    "meleeDefense", "rangedDefense", "magicDefense", "meleeDefenseMultiplier",
    "rangedDefenseMultiplier", "magicDefenseMultiplier", "meleeDefenseDivisor",
    "rangedDefenseDivisor", "magicDefenseDivisor", "combatlvl", "hairColour",
    "topColour", "bottomColour", "skinColour",
}
ITEM_FIELDS = {
    "id", "name", "description", "meleeOffense", "rangedOffense", "magicOffense",
    "weaponSpeed", "meleeDefense", "rangedDefense", "magicDefense", "requiredLevel",
    "requiredSkillID", "isWearable", "appearanceID", "wearableID", "wearSlot",
    "weaponAimBonus", "weaponPowerBonus", "armourBonus", "magicBonus", "prayerBonus",
    "basePrice",
}


def load_entries(filename: str) -> list[dict]:
    data = json.loads((DEFS / filename).read_text(encoding="utf-8"))
    return data[next(iter(data))]


def definition_ids(*filenames: str) -> set[int]:
    result: set[int] = set()
    for filename in filenames:
        result.update(int(entry["id"]) for entry in load_entries(filename))
    return result


def validate_overrides(
    entries: list[dict], known_ids: set[int], allowed_fields: set[str], label: str
) -> None:
    seen: set[int] = set()
    for index, entry in enumerate(entries):
        entry_id = int(entry["id"])
        if entry_id in seen:
            raise AssertionError(f"{label} has duplicate id {entry_id}")
        seen.add(entry_id)
        if entry_id not in known_ids:
            raise AssertionError(f"{label} index {index} references unknown id {entry_id}")
        unexpected = set(entry) - allowed_fields
        if unexpected:
            raise AssertionError(
                f"{label} index {index} has unsupported fields: {sorted(unexpected)}"
            )


def require(source: str, text: str, description: str) -> None:
    if text not in source:
        raise AssertionError(f"Missing {description}: {text}")


def main() -> None:
    validate_overrides(
        load_entries("NpcDefsMyWorld.json"),
        definition_ids("NpcDefs.json", "NpcDefsCustom.json"),
        NPC_FIELDS,
        "NpcDefsMyWorld.json",
    )
    validate_overrides(
        load_entries("ItemDefsMyWorld.json"),
        definition_ids("ItemDefs.json", "ItemDefsCustom.json"),
        ITEM_FIELDS,
        "ItemDefsMyWorld.json",
    )

    source = ENTITY_HANDLER.read_text(encoding="utf-8")
    require(source, "ArrayList<NPCDef> stagedNpcs = new ArrayList<>(npcs);", "staged NPC catalog")
    require(source, "npcs = stagedNpcs;", "atomic NPC catalog swap")
    require(source, '"meleeOffense", "rangedOffense", "magicOffense"', "NPC power override whitelist")
    require(source, '"projectileRange"', "NPC projectile range override whitelist")
    require(source, 'if (npc.has("projectileRange"))', "NPC projectile range override")
    require(source, 'if (npc.has("meleeOffense")) staged.meleeOffense', "NPC melee power override")
    require(source, 'if (npc.has("rangedOffense")) staged.rangedOffense', "NPC ranged power override")
    require(source, 'if (npc.has("magicOffense")) staged.magicOffense', "NPC magic power override")
    require(source, "ArrayList<ItemDefinition> stagedItems = new ArrayList<>(items);", "staged item catalog")
    require(source, "items = stagedItems;", "atomic item catalog swap")
    require(source, 'throw new IllegalArgumentException("Duplicate npc override id "', "duplicate NPC rejection")
    require(source, 'throw new IllegalArgumentException("Duplicate item override id "', "duplicate item rejection")
    require(source, 'throw new IllegalStateException("Failed to apply npc overrides from "', "NPC startup failure")
    require(source, 'throw new IllegalStateException("Failed to apply item overrides from "', "item startup failure")

    print("PASS: MyWorld definition overrides are validated and applied transactionally")


if __name__ == "__main__":
    main()
