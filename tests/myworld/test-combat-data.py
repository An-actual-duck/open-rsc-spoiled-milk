#!/usr/bin/env python3
import json
import sys
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BASE_ITEMS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefs.json"
CUSTOM_ITEMS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsCustom.json"
ITEMS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsMyWorld.json"
NPCS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "NpcDefsMyWorld.json"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_json_array(path: Path, key: str):
    if not path.exists():
        fail(f"Missing file: {path}")
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if key not in data or not isinstance(data[key], list):
        fail(f"{path.name} must contain a top-level '{key}' array")
    return data[key]


def load_item_catalog() -> dict[int, dict]:
    base_items = load_json_array(BASE_ITEMS_PATH, "item")
    custom_items = load_json_array(CUSTOM_ITEMS_PATH, "items")
    items_by_id = {entry["id"]: entry for entry in base_items}
    items_by_id.update({entry["id"]: entry for entry in custom_items})
    return items_by_id


def ensure_unique_ids(entries, label: str) -> None:
    counts = Counter(entry["id"] for entry in entries)
    duplicates = [str(entry_id) for entry_id, count in counts.items() if count > 1]
    if duplicates:
        fail(f"{label} contains duplicate ids: {', '.join(sorted(duplicates))}")


def ensure_item_fields(entries, label: str, numeric_fields, string_fields) -> None:
    for entry in entries:
        if "id" not in entry or not isinstance(entry["id"], int):
            fail(f"{label} entry missing integer id: {entry}")
        allowed_fields = set(numeric_fields) | set(string_fields)
        unknown = sorted(set(entry.keys()) - {"id"} - allowed_fields)
        if unknown:
            fail(f"{label} entry {entry['id']} has unknown fields: {', '.join(unknown)}")
        for field in numeric_fields:
            if field in entry and not isinstance(entry[field], (int, float)):
                fail(f"{label} entry {entry['id']} field {field} must be numeric")
        for field in string_fields:
            if field in entry and not isinstance(entry[field], str):
                fail(f"{label} entry {entry['id']} field {field} must be a string")


def ensure_numeric_fields(entries, label: str, allowed_fields) -> None:
    for entry in entries:
        if "id" not in entry or not isinstance(entry["id"], int):
            fail(f"{label} entry missing integer id: {entry}")
        unknown = sorted(set(entry.keys()) - {"id"} - allowed_fields)
        if unknown:
            fail(f"{label} entry {entry['id']} has unknown fields: {', '.join(unknown)}")
        for field in allowed_fields:
            if field in entry and not isinstance(entry[field], (int, float)):
                fail(f"{label} entry {entry['id']} field {field} must be numeric")


def check_monotonic(entries_by_id, sequence, field, label) -> None:
    values = []
    for entry_id in sequence:
        entry = entries_by_id.get(entry_id)
        if entry is None or field not in entry:
            fail(f"{label} missing {field} for id {entry_id}")
        values.append(entry[field])
    for previous, current in zip(values, values[1:]):
        if current < previous:
            fail(f"{label} is not monotonic for {field}: {values}")


def require_exact(entries_by_id, entry_id, field, expected, label) -> None:
    entry = entries_by_id.get(entry_id)
    if entry is None or field not in entry:
        fail(f"{label} missing {field} for id {entry_id}")
    if entry[field] != expected:
        fail(f"{label} expected {field}={expected} for id {entry_id} but found {entry[field]}")


def require_field_absent(entries_by_id, entry_id, field, label) -> None:
    entry = entries_by_id.get(entry_id)
    if entry is None:
        fail(f"{label} missing override for id {entry_id}")
    if field in entry:
        fail(f"{label} should not override {field} for id {entry_id}")


def require_absent(entries_by_id, entry_id, label) -> None:
    if entry_id in entries_by_id:
        fail(f"{label} should not be overridden in ItemDefsMyWorld.json")


def require_uniform(entries_by_id, entry_ids, field, expected, label) -> None:
    for entry_id in entry_ids:
        require_exact(entries_by_id, entry_id, field, expected, label)


def require_no_equip_requirement(entries_by_id, entry_ids, label) -> None:
    for entry_id in entry_ids:
        require_exact(entries_by_id, entry_id, "requiredLevel", 0, label)
        require_exact(entries_by_id, entry_id, "requiredSkillID", -1, label)


def require_tier_requirements(entries_by_id, level_by_id, required_skill_id, label) -> None:
    for entry_id, required_level in level_by_id.items():
        require_exact(entries_by_id, entry_id, "requiredLevel", required_level, label)
        require_exact(entries_by_id, entry_id, "requiredSkillID", required_skill_id, label)


def ensure_npc_multiplier_bounds(entries) -> None:
    for entry in entries:
        multiplier_fields = [
            field for field in ("meleeDefenseMultiplier", "rangedDefenseMultiplier", "magicDefenseMultiplier")
            if field in entry
        ]
        if not multiplier_fields:
            continue
        for field in multiplier_fields:
            if entry[field] > 1.0:
                fail(f"NpcDefsMyWorld.json entry {entry['id']} has {field}>{1.0}")
        if not any(entry[field] == 1.0 for field in multiplier_fields):
            fail(f"NpcDefsMyWorld.json entry {entry['id']} must keep at least one defense multiplier at 1.0")


def main() -> None:
    item_entries = load_json_array(ITEMS_PATH, "items")
    npc_entries = load_json_array(NPCS_PATH, "npcs")
    catalog_items = load_item_catalog()

    item_fields = {
        "meleeOffense",
        "rangedOffense",
        "magicOffense",
        "weaponSpeed",
        "meleeDefense",
        "rangedDefense",
        "magicDefense",
        "requiredLevel",
        "requiredSkillID",
        "prayerBonus",
        "weaponAimBonus",
        "weaponPowerBonus",
        "armourBonus",
        "magicBonus",
    }
    item_string_fields = {"name", "description"}
    npc_fields = {
        "attack",
        "strength",
        "hits",
        "defense",
        "meleeDefense",
        "rangedDefense",
        "magicDefense",
        "meleeDefenseMultiplier",
        "rangedDefenseMultiplier",
        "magicDefenseMultiplier",
        "meleeDefenseDivisor",
        "rangedDefenseDivisor",
        "magicDefenseDivisor",
        "combatlvl",
        "hairColour",
        "topColour",
        "bottomColour",
        "skinColour",
    }

    ensure_unique_ids(item_entries, "ItemDefsMyWorld.json")
    ensure_unique_ids(npc_entries, "NpcDefsMyWorld.json")
    ensure_item_fields(item_entries, "ItemDefsMyWorld.json", item_fields, item_string_fields)
    ensure_numeric_fields(npc_entries, "NpcDefsMyWorld.json", npc_fields)
    ensure_npc_multiplier_bounds(npc_entries)

    items_by_id = {entry["id"]: entry for entry in item_entries}
    npcs_by_id = {entry["id"]: entry for entry in npc_entries}

    for entry in item_entries:
        if any(field in entry for field in ("meleeDefense", "rangedDefense", "magicDefense")):
            if entry.get("requiredLevel") != 0 or entry.get("requiredSkillID") != -1:
                fail(f"Armor/defensive item override {entry['id']} must not have an equip requirement")

    missing_explicit_overrides = []
    explicit_fields = ("meleeOffense", "rangedOffense", "magicOffense", "meleeDefense", "rangedDefense", "magicDefense")
    legacy_fields = ("armourBonus", "weaponAimBonus", "weaponPowerBonus", "magicBonus")
    for item_id, item in sorted(catalog_items.items()):
        if not item.get("isWieldable"):
            continue
        if item_id in items_by_id and any(field in items_by_id[item_id] for field in explicit_fields):
            continue
        if any(item.get(field, 0) for field in legacy_fields):
            missing_explicit_overrides.append(f"{item_id}:{item.get('name', 'unknown')}")
    if missing_explicit_overrides:
        fail(
            "Wieldable items still relying on legacy combat fields without explicit MyWorld overrides: "
            + ", ".join(missing_explicit_overrides[:20])
        )

    check_monotonic(items_by_id, [82, 83, 84, 85, 86, 398], "meleeOffense", "Scimitar family")
    check_monotonic(items_by_id, [76, 77, 78, 79, 80, 81], "meleeOffense", "2h sword family")
    check_monotonic(items_by_id, [70, 71, 72, 425, 73, 74, 75], "meleeOffense", "Long sword family")
    check_monotonic(items_by_id, [66, 1, 67, 424, 68, 69, 397], "meleeOffense", "Short sword family")
    check_monotonic(items_by_id, [205, 89, 90, 429, 91, 92, 93], "meleeOffense", "Battleaxe family")
    check_monotonic(items_by_id, [2003, 2014, 94, 0, 95, 430, 96, 2025, 97, 2036, 98], "meleeOffense", "Mace family")
    check_monotonic(items_by_id, [2207, 2208, 827, 1088, 1089, 1090, 2209, 1091, 2210, 1092], "meleeOffense", "Spear family")
    check_monotonic(items_by_id, [188, 2117, 648, 650, 2121, 652, 654, 2125, 656, 2129, 1454], "rangedOffense", "Longbow family")
    check_monotonic(items_by_id, [189, 2118, 649, 651, 2122, 653, 655, 2126, 657, 2130], "rangedOffense", "Shortbow family")
    check_monotonic(items_by_id, [60, 59, 2169, 2170, 2171, 2172, 2173, 2174, 2175, 2176, 1453], "rangedOffense", "Crossbow family")
    check_monotonic(items_by_id, [2039, 2040, 11, 638, 640, 642, 2041, 644, 2042, 646, 1449], "rangedOffense", "Arrow family")
    check_monotonic(items_by_id, [190, 2178, 2180, 2182, 2184, 2186, 2188, 2190, 2192, 2194, 1451], "rangedOffense", "Bolt family")
    check_monotonic(items_by_id, [2043, 2044, 1013, 1015, 1024, 1068, 2045, 1069, 2046, 1070], "rangedOffense", "Throwing dart family")
    check_monotonic(items_by_id, [1996, 2007, 1076, 1075, 1077, 1078, 2018, 1079, 2029, 1080], "rangedOffense", "Throwing knife family")
    check_monotonic(items_by_id, [104, 5, 105, 106, 107, 399], "meleeDefense", "Medium helmet family")
    check_monotonic(items_by_id, [108, 6, 109, 110, 111, 112], "meleeDefense", "Large helmet family")
    check_monotonic(items_by_id, [124, 3, 125, 126, 127, 403], "meleeDefense", "Square shield family")
    check_monotonic(items_by_id, [128, 2, 129, 130, 131, 404], "meleeDefense", "Kite shield family")
    check_monotonic(items_by_id, [16, 1372, 1370, 1498, 15, 1371], "meleeDefense", "Leather armor melee-defense split")
    check_monotonic(items_by_id, [16, 1372, 1370, 1498, 15, 1371], "rangedDefense", "Leather armor ranged-defense split")
    require_exact(items_by_id, 1835, "meleeDefense", 1, "Cow-hide coif explicit melee defense")
    require_exact(items_by_id, 1838, "meleeDefense", 3, "Cow-hide chaps explicit melee defense")
    require_exact(items_by_id, 1840, "meleeDefense", 1, "Goblin-hide coif explicit melee defense")
    require_exact(items_by_id, 1845, "magicDefense", 1, "Unicorn-hide coif explicit magic defense")
    require_exact(items_by_id, 1848, "magicDefense", 4, "Unicorn-hide chaps explicit magic defense")
    require_exact(items_by_id, 1850, "meleeDefense", 2, "Bear-hide coif explicit melee defense")
    require_exact(items_by_id, 1855, "magicDefense", 1, "Black unicorn-hide coif explicit magic defense")

    require_exact(items_by_id, 656, "rangedOffense", 44, "Magic longbow")
    require_exact(items_by_id, 646, "rangedOffense", 24, "Rune arrows")
    require_exact(items_by_id, 1454, "rangedOffense", 56, "Dragon longbow")
    require_exact(items_by_id, 1449, "rangedOffense", 28, "Dragon arrows")
    require_uniform(items_by_id, [2228, 2229, 2230, 2231, 2232, 2233, 2234, 2235, 2236, 2237], "magicOffense", 2, "Blessed staff explicit magic offense")
    require_uniform(items_by_id, [62, 28, 63, 423, 64, 65, 396], "weaponSpeed", 5, "Dagger family speed")
    require_uniform(items_by_id, [560, 559, 561, 565, 562, 564, 563], "weaponSpeed", 5, "Poisoned dagger family speed")
    require_uniform(items_by_id, [66, 1, 67, 424, 68, 69, 397], "weaponSpeed", 4, "Short sword family speed")
    require_uniform(items_by_id, [82, 83, 84, 427, 85, 86, 398], "weaponSpeed", 4, "Scimitar family speed")
    require_uniform(items_by_id, [70, 71, 72, 425, 73, 74, 75], "weaponSpeed", 3, "Long sword family speed")
    require_uniform(items_by_id, [205, 89, 90, 429, 91, 92, 93], "weaponSpeed", 3, "Battleaxe family speed")
    require_uniform(items_by_id, [2003, 2014, 94, 0, 95, 430, 96, 2025, 97, 2036, 98], "weaponSpeed", 3, "Mace family speed")
    require_uniform(items_by_id, [2207, 2208, 827, 1088, 1089, 1090, 2209, 1091, 2210, 1092], "weaponSpeed", 3, "Spear family speed")
    require_uniform(items_by_id, [2211, 2212, 1135, 1136, 1137, 1138, 2213, 1139, 2214, 1140], "weaponSpeed", 3, "Poisoned spear family speed")
    require_uniform(items_by_id, [76, 77, 78, 426, 79, 80, 81], "weaponSpeed", 2, "2h sword family speed")
    require_uniform(items_by_id, [2001, 2012, 87, 12, 88, 428, 203, 2023, 204, 2034, 405, 594, 1480], "meleeOffense", 0, "Hatchet family offense removal")
    require_uniform(items_by_id, [2001, 2012, 87, 12, 88, 428, 203, 2023, 204, 2034, 405, 594, 1480], "weaponAimBonus", 0, "Hatchet family accuracy removal")
    require_uniform(items_by_id, [2001, 2012, 87, 12, 88, 428, 203, 2023, 204, 2034, 405, 594, 1480], "weaponPowerBonus", 0, "Hatchet family power removal")
    require_uniform(items_by_id, [1987, 2047, 156, 1258, 1259, 1260, 2048, 1261, 2049, 1262], "meleeOffense", 0, "Pickaxe family offense removal")
    require_uniform(items_by_id, [1987, 2047, 156, 1258, 1259, 1260, 2048, 1261, 2049, 1262], "weaponAimBonus", 0, "Pickaxe family accuracy removal")
    require_uniform(items_by_id, [1987, 2047, 156, 1258, 1259, 1260, 2048, 1261, 2049, 1262], "weaponPowerBonus", 0, "Pickaxe family power removal")
    require_exact(items_by_id, 2001, "name", "Tin Hatchet", "Tin axe rename")
    require_exact(items_by_id, 2012, "name", "Copper Hatchet", "Copper axe rename")
    require_exact(items_by_id, 87, "name", "Bronze Hatchet", "Bronze axe rename")
    require_exact(items_by_id, 12, "name", "Iron Hatchet", "Iron axe rename")
    require_exact(items_by_id, 88, "name", "Steel Hatchet", "Steel axe rename")
    require_exact(items_by_id, 428, "name", "Black Hatchet", "Black axe rename")
    require_exact(items_by_id, 203, "name", "Mithril Hatchet", "Mithril axe rename")
    require_exact(items_by_id, 2023, "name", "Titan Steel Hatchet", "Titan Steel axe rename")
    require_exact(items_by_id, 204, "name", "Adamantite Hatchet", "Adamantite axe rename")
    require_exact(items_by_id, 2034, "name", "Orichalcum Hatchet", "Orichalcum axe rename")
    require_exact(items_by_id, 405, "name", "Rune Hatchet", "Rune axe rename")
    require_exact(items_by_id, 594, "name", "Dragon Hatchet", "Dragon axe rename")
    require_exact(items_by_id, 1987, "name", "Tin Pickaxe", "Tin pickaxe tool profile")
    require_exact(items_by_id, 2047, "name", "Copper Pickaxe", "Copper pickaxe tool profile")
    require_exact(items_by_id, 2048, "name", "Titan Steel Pickaxe", "Titan Steel pickaxe tool profile")
    require_exact(items_by_id, 2049, "name", "Orichalcum Pickaxe", "Orichalcum pickaxe tool profile")
    require_exact(items_by_id, 52, "meleeOffense", 24, "Silverlight longsword profile")
    require_exact(items_by_id, 52, "weaponSpeed", 3, "Silverlight longsword profile")
    require_exact(items_by_id, 265, "meleeOffense", 24, "Faladian Knight's sword longsword profile")
    require_exact(items_by_id, 265, "weaponSpeed", 3, "Faladian Knight's sword longsword profile")
    require_exact(items_by_id, 606, "meleeOffense", 48, "Excalibur longsword profile")
    require_exact(items_by_id, 606, "weaponSpeed", 3, "Excalibur longsword profile")
    require_exact(items_by_id, 307, "meleeOffense", 17, "Mace of Zamorak mace profile")
    require_exact(items_by_id, 307, "weaponSpeed", 3, "Mace of Zamorak mace profile")
    require_exact(items_by_id, 754, "meleeOffense", 25, "Bloody axe of Zamorak battleaxe profile")
    require_exact(items_by_id, 754, "weaponSpeed", 3, "Bloody axe of Zamorak battleaxe profile")
    require_exact(items_by_id, 1172, "meleeOffense", 6, "Machette light blade profile")
    require_exact(items_by_id, 1172, "weaponSpeed", 4, "Machette light blade profile")
    require_exact(items_by_id, 1205, "meleeOffense", 3, "Silver dagger profile")
    require_exact(items_by_id, 1205, "weaponSpeed", 5, "Silver dagger profile")
    require_exact(items_by_id, 1230, "meleeOffense", 3, "Silver dagger duplicate profile")
    require_exact(items_by_id, 1230, "weaponSpeed", 5, "Silver dagger duplicate profile")
    require_exact(items_by_id, 1236, "meleeOffense", 6, "Plain dagger profile")
    require_exact(items_by_id, 1236, "weaponSpeed", 5, "Plain dagger profile")
    require_exact(items_by_id, 1255, "meleeOffense", 9, "Dark dagger profile")
    require_exact(items_by_id, 1255, "weaponSpeed", 5, "Dark dagger profile")
    require_exact(items_by_id, 1256, "meleeOffense", 12, "Glowing dark dagger profile")
    require_exact(items_by_id, 1256, "weaponSpeed", 5, "Glowing dark dagger profile")
    require_exact(items_by_id, 1447, "meleeOffense", 21, "Dragon dagger tier-11 dagger profile")
    require_exact(items_by_id, 1447, "weaponSpeed", 5, "Dragon dagger tier-11 dagger profile")
    require_exact(items_by_id, 1448, "meleeOffense", items_by_id[1447]["meleeOffense"], "Poisoned dragon dagger parity")
    require_exact(items_by_id, 1448, "weaponSpeed", items_by_id[1447]["weaponSpeed"], "Poisoned dragon dagger speed parity")
    require_exact(items_by_id, 593, "meleeOffense", 84, "Dragon sword tier-11 longsword profile")
    require_exact(items_by_id, 593, "weaponSpeed", 3, "Dragon sword tier-11 longsword profile")
    require_exact(items_by_id, 1289, "meleeOffense", 12, "Scythe baseline profile")
    require_exact(items_by_id, 1289, "weaponSpeed", 3, "Scythe baseline profile")
    require_exact(items_by_id, 1346, "meleeOffense", 174, "Dragon 2h tier-11 two-handed profile")
    require_exact(items_by_id, 1346, "weaponSpeed", 2, "Dragon 2h tier-11 two-handed profile")
    require_exact(items_by_id, 1496, "meleeOffense", 3, "Yoyo novelty weapon profile")
    require_exact(items_by_id, 1496, "weaponSpeed", 5, "Yoyo novelty weapon profile")
    require_exact(items_by_id, 1592, "meleeOffense", 10, "Boomstick novelty weapon profile")
    require_exact(items_by_id, 1592, "weaponSpeed", 3, "Boomstick novelty weapon profile")
    require_exact(items_by_id, 217, "meleeOffense", 1, "Stake explicit quest weapon profile")
    require_exact(items_by_id, 314, "magicOffense", 2, "Sapphire Amulet of magic explicit legacy profile")
    require_exact(items_by_id, 315, "meleeDefense", 1, "Emerald Amulet of protection explicit legacy profile")
    require_exact(items_by_id, 316, "meleeOffense", 2, "Ruby Amulet of strength explicit legacy profile")
    require_exact(items_by_id, 317, "meleeOffense", 1, "Diamond Amulet of power explicit legacy profile")
    require_exact(items_by_id, 317, "magicOffense", 2, "Diamond Amulet of power explicit legacy profile")
    require_exact(items_by_id, 317, "meleeDefense", 1, "Diamond Amulet of power explicit legacy profile")
    require_uniform(items_by_id, [698, 699, 700, 701, 1006], "meleeOffense", 0, "Gauntlet one-offs offense removal")
    require_exact(items_by_id, 725, "magicOffense", 4, "Staff of Armadyl explicit magic profile")
    require_field_absent(items_by_id, 509, "meleeOffense", "Dramen Staff magic-only profile")
    require_field_absent(items_by_id, 725, "meleeOffense", "Staff of Armadyl magic-only profile")
    require_exact(items_by_id, 734, "meleeDefense", 1, "Khazard chainmail explicit defense")
    require_exact(items_by_id, 744, "meleeDefense", 1, "Gnome Emerald Amulet of protection explicit legacy profile")
    require_exact(items_by_id, 761, "meleeDefense", 1, "Protective trousers explicit defense")
    require_exact(items_by_id, 852, "meleeOffense", 1, "Beads of the dead explicit legacy profile")
    require_exact(items_by_id, 852, "magicOffense", 1, "Beads of the dead explicit legacy profile")
    require_exact(items_by_id, 852, "meleeDefense", 1, "Beads of the dead explicit legacy profile")
    require_exact(items_by_id, 1029, "meleeOffense", 1, "Unholy Symbol explicit legacy profile")
    require_exact(items_by_id, 1029, "meleeDefense", 1, "Unholy Symbol explicit legacy profile")
    require_exact(items_by_id, 1497, "meleeDefense", 1, "Ogre Ears explicit cosmetic defense")
    require_exact(items_by_id, 560, "meleeOffense", items_by_id[62]["meleeOffense"], "Poisoned bronze dagger parity")
    require_exact(items_by_id, 559, "meleeOffense", items_by_id[28]["meleeOffense"], "Poisoned iron dagger parity")
    require_exact(items_by_id, 561, "meleeOffense", items_by_id[63]["meleeOffense"], "Poisoned steel dagger parity")
    require_exact(items_by_id, 565, "meleeOffense", items_by_id[423]["meleeOffense"], "Poisoned black dagger parity")
    require_exact(items_by_id, 562, "meleeOffense", items_by_id[64]["meleeOffense"], "Poisoned mithril dagger parity")
    require_exact(items_by_id, 564, "meleeOffense", items_by_id[65]["meleeOffense"], "Poisoned adamantite dagger parity")
    require_exact(items_by_id, 563, "meleeOffense", items_by_id[396]["meleeOffense"], "Poisoned rune dagger parity")
    require_exact(items_by_id, 2211, "meleeOffense", items_by_id[2207]["meleeOffense"], "Poisoned tin spear parity")
    require_exact(items_by_id, 2212, "meleeOffense", items_by_id[2208]["meleeOffense"], "Poisoned copper spear parity")
    require_exact(items_by_id, 1135, "meleeOffense", items_by_id[827]["meleeOffense"], "Poisoned bronze spear parity")
    require_exact(items_by_id, 1136, "meleeOffense", items_by_id[1088]["meleeOffense"], "Poisoned iron spear parity")
    require_exact(items_by_id, 1137, "meleeOffense", items_by_id[1089]["meleeOffense"], "Poisoned steel spear parity")
    require_exact(items_by_id, 1138, "meleeOffense", items_by_id[1090]["meleeOffense"], "Poisoned mithril spear parity")
    require_exact(items_by_id, 2213, "meleeOffense", items_by_id[2209]["meleeOffense"], "Poisoned titan steel spear parity")
    require_exact(items_by_id, 1139, "meleeOffense", items_by_id[1091]["meleeOffense"], "Poisoned adamantite spear parity")
    require_exact(items_by_id, 2214, "meleeOffense", items_by_id[2210]["meleeOffense"], "Poisoned orichalcum spear parity")
    require_exact(items_by_id, 1140, "meleeOffense", items_by_id[1092]["meleeOffense"], "Poisoned rune spear parity")
    require_tier_requirements(items_by_id, {
        2207: 1, 2211: 1,
        2208: 8, 2212: 8,
        827: 15, 1135: 15,
        1088: 22, 1136: 22,
        1089: 30, 1137: 30,
        1090: 38, 1138: 38,
        2209: 46, 2213: 46,
        1091: 54, 1139: 54,
        2210: 62, 2214: 62,
        1092: 70, 1140: 70,
    }, 0, "Spear melee tier requirements")
    require_uniform(items_by_id, [188, 2117, 648, 650, 2121, 652, 654, 2125, 656, 2129, 1454], "weaponSpeed", 3, "Longbow family speed")
    require_uniform(items_by_id, [189, 2118, 649, 651, 2122, 653, 655, 2126, 657, 2130], "weaponSpeed", 4, "Shortbow family speed")
    require_uniform(items_by_id, [60, 59, 2169, 2170, 2171, 2172, 2173, 2174, 2175, 2176, 1453], "weaponSpeed", 4, "Crossbow family speed")
    require_tier_requirements(items_by_id, {
        188: 1, 2117: 8, 648: 15, 650: 22, 2121: 30, 652: 38, 654: 46, 2125: 54, 656: 62, 2129: 70, 1454: 80,
        189: 1, 2118: 8, 649: 15, 651: 22, 2122: 30, 653: 38, 655: 46, 2126: 54, 657: 62, 2130: 70,
        60: 1, 59: 8, 2169: 15, 2170: 22, 2171: 30, 2172: 38, 2173: 46, 2174: 54, 2175: 62, 2176: 70, 1453: 80,
    }, 4, "Ranged wood weapon tier requirements")
    require_exact(items_by_id, 59, "name", "Pine Crossbow", "Phoenix crossbow replacement")
    require_exact(items_by_id, 190, "name", "Tin bolts", "Crossbow bolts tier rename")
    require_exact(items_by_id, 592, "name", "Poison Tin bolts", "Poison crossbow bolts tier rename")
    require_exact(items_by_id, 1453, "weaponSpeed", 4, "Dragon crossbow")
    require_exact(items_by_id, 2177, "name", "Bolt mould", "Bolt mould item")
    require_exact(items_by_id, 592, "rangedOffense", items_by_id[190]["rangedOffense"], "Poison tin bolts parity")
    require_exact(items_by_id, 2179, "rangedOffense", items_by_id[2178]["rangedOffense"], "Poison copper bolts parity")
    require_exact(items_by_id, 2181, "rangedOffense", items_by_id[2180]["rangedOffense"], "Poison bronze bolts parity")
    require_exact(items_by_id, 2183, "rangedOffense", items_by_id[2182]["rangedOffense"], "Poison iron bolts parity")
    require_exact(items_by_id, 2185, "rangedOffense", items_by_id[2184]["rangedOffense"], "Poison steel bolts parity")
    require_exact(items_by_id, 786, "rangedOffense", items_by_id[2182]["rangedOffense"], "Oyster pearl bolts tier-4 parity")
    require_exact(items_by_id, 2187, "rangedOffense", items_by_id[2186]["rangedOffense"], "Poison mithril bolts parity")
    require_exact(items_by_id, 2189, "rangedOffense", items_by_id[2188]["rangedOffense"], "Poison titan steel bolts parity")
    require_exact(items_by_id, 2191, "rangedOffense", items_by_id[2190]["rangedOffense"], "Poison adamantite bolts parity")
    require_exact(items_by_id, 2193, "rangedOffense", items_by_id[2192]["rangedOffense"], "Poison orichalcum bolts parity")
    require_exact(items_by_id, 2195, "rangedOffense", items_by_id[2194]["rangedOffense"], "Poison rune bolts parity")
    require_exact(items_by_id, 2196, "rangedOffense", items_by_id[2043]["rangedOffense"], "Poison tin dart parity")
    require_exact(items_by_id, 2196, "weaponSpeed", items_by_id[2043]["weaponSpeed"], "Poison tin dart speed parity")
    require_exact(items_by_id, 2197, "rangedOffense", items_by_id[2044]["rangedOffense"], "Poison copper dart parity")
    require_exact(items_by_id, 2197, "weaponSpeed", items_by_id[2044]["weaponSpeed"], "Poison copper dart speed parity")
    require_exact(items_by_id, 1122, "rangedOffense", items_by_id[1013]["rangedOffense"], "Poison bronze dart parity")
    require_exact(items_by_id, 1123, "rangedOffense", items_by_id[1015]["rangedOffense"], "Poison iron dart parity")
    require_exact(items_by_id, 1124, "rangedOffense", items_by_id[1024]["rangedOffense"], "Poison steel dart parity")
    require_exact(items_by_id, 1125, "rangedOffense", items_by_id[1068]["rangedOffense"], "Poison mithril dart parity")
    require_exact(items_by_id, 2198, "rangedOffense", items_by_id[2045]["rangedOffense"], "Poison titan steel dart parity")
    require_exact(items_by_id, 2198, "weaponSpeed", items_by_id[2045]["weaponSpeed"], "Poison titan steel dart speed parity")
    require_exact(items_by_id, 1126, "rangedOffense", items_by_id[1069]["rangedOffense"], "Poison adamantite dart parity")
    require_exact(items_by_id, 2199, "rangedOffense", items_by_id[2046]["rangedOffense"], "Poison orichalcum dart parity")
    require_exact(items_by_id, 2199, "weaponSpeed", items_by_id[2046]["weaponSpeed"], "Poison orichalcum dart speed parity")
    require_exact(items_by_id, 1127, "rangedOffense", items_by_id[1070]["rangedOffense"], "Poison rune dart parity")
    require_exact(items_by_id, 2200, "rangedOffense", items_by_id[1996]["rangedOffense"], "Poison tin knife parity")
    require_exact(items_by_id, 2200, "weaponSpeed", items_by_id[1996]["weaponSpeed"], "Poison tin knife speed parity")
    require_exact(items_by_id, 2201, "rangedOffense", items_by_id[2007]["rangedOffense"], "Poison copper knife parity")
    require_exact(items_by_id, 2201, "weaponSpeed", items_by_id[2007]["weaponSpeed"], "Poison copper knife speed parity")
    require_exact(items_by_id, 1128, "rangedOffense", items_by_id[1076]["rangedOffense"], "Poison bronze knife parity")
    require_exact(items_by_id, 1129, "rangedOffense", items_by_id[1075]["rangedOffense"], "Poison iron knife parity")
    require_exact(items_by_id, 1130, "rangedOffense", items_by_id[1077]["rangedOffense"], "Poison steel knife parity")
    require_exact(items_by_id, 1131, "rangedOffense", items_by_id[1078]["rangedOffense"], "Poison mithril knife parity")
    require_exact(items_by_id, 1132, "rangedOffense", items_by_id[1081]["rangedOffense"], "Poison black knife parity")
    require_exact(items_by_id, 2202, "rangedOffense", items_by_id[2018]["rangedOffense"], "Poison titan steel knife parity")
    require_exact(items_by_id, 2202, "weaponSpeed", items_by_id[2018]["weaponSpeed"], "Poison titan steel knife speed parity")
    require_exact(items_by_id, 1133, "rangedOffense", items_by_id[1079]["rangedOffense"], "Poison adamantite knife parity")
    require_exact(items_by_id, 2203, "rangedOffense", items_by_id[2029]["rangedOffense"], "Poison orichalcum knife parity")
    require_exact(items_by_id, 2203, "weaponSpeed", items_by_id[2029]["weaponSpeed"], "Poison orichalcum knife speed parity")
    require_exact(items_by_id, 1134, "rangedOffense", items_by_id[1080]["rangedOffense"], "Poison rune knife parity")
    require_uniform(items_by_id, [2043, 2044, 1013, 1014, 1015, 1024, 1068, 2045, 1069, 2046, 1070, 2196, 2197, 1122, 1123, 1124, 1125, 2198, 1126, 2199, 1127], "weaponSpeed", 5, "Throwing dart family speed")
    require_uniform(items_by_id, [1996, 2007, 1076, 1075, 1077, 1081, 1078, 2018, 1079, 2029, 1080, 2200, 2201, 1128, 1129, 1130, 1131, 1132, 2202, 1133, 2203, 1134], "weaponSpeed", 3, "Throwing knife family speed")
    require_tier_requirements(items_by_id, {
        2043: 1, 2196: 1, 1996: 1, 2200: 1,
        2044: 8, 2197: 8, 2007: 8, 2201: 8,
        1013: 15, 1122: 15, 1076: 15, 1128: 15, 1014: 15,
        1015: 22, 1123: 22, 1075: 22, 1129: 22,
        1024: 30, 1124: 30, 1077: 30, 1130: 30, 1081: 30, 1132: 30,
        1068: 38, 1125: 38, 1078: 38, 1131: 38,
        2045: 46, 2198: 46, 2018: 46, 2202: 46,
        1069: 54, 1126: 54, 1079: 54, 1133: 54,
        2046: 62, 2199: 62, 2029: 62, 2203: 62,
        1070: 70, 1127: 70, 1080: 70, 1134: 70,
    }, 4, "Ranged metal thrown weapon tier requirements")
    require_exact(items_by_id, 2050, "magicDefense", 1, "Wool wizard hat")
    require_exact(items_by_id, 2051, "magicDefense", 4, "Wool robe top")
    require_exact(items_by_id, 2052, "magicDefense", 3, "Wool robe skirt")
    require_uniform(items_by_id, [2054, 2055, 2056, 2057, 2058, 2059], "magicDefense", 1, "Dyed wool wizard hats")
    require_uniform(items_by_id, [2060, 2061, 2062, 2063, 2064, 2065], "magicDefense", 4, "Dyed wool robe tops")
    require_uniform(items_by_id, [2066, 2067, 2068, 2069, 2070, 2071], "magicDefense", 3, "Dyed wool robe skirts")
    require_uniform(items_by_id, [2072, 2073, 2074, 2075, 2076, 2077, 2078, 2079, 2080, 2081, 2082, 2083, 2084], "magicDefense", 1, "Enchanted wool wizard hats")
    require_uniform(items_by_id, [2085, 2086, 2087, 2088, 2089, 2090, 2091, 2092, 2093, 2094, 2095, 2096, 2097], "magicDefense", 4, "Enchanted wool robe tops")
    require_uniform(items_by_id, [2098, 2099, 2100, 2101, 2102, 2103, 2104, 2105, 2106, 2107, 2108, 2109, 2110], "magicDefense", 3, "Enchanted wool robe skirts")
    require_uniform(items_by_id, [841, 842, 843, 844, 845], "magicDefense", 1, "Gnome hats wool-tier magic defense")
    require_uniform(items_by_id, [846, 847, 848, 849, 850], "magicDefense", 4, "Gnome tops wool-tier magic defense")
    require_uniform(items_by_id, [836, 837, 838, 839, 840], "magicDefense", 3, "Gnome robe bottoms wool-tier magic defense")
    require_uniform(items_by_id, [836, 837, 838, 839, 840, 841, 842, 843, 844, 845, 846, 847, 848, 849, 850], "armourBonus", 0, "Gnome cloth clears legacy armour bonus")
    require_uniform(items_by_id, [836, 837, 838, 839, 840, 841, 842, 843, 844, 845, 846, 847, 848, 849, 850], "magicBonus", 0, "Gnome cloth clears legacy magic bonus")
    require_absent(items_by_id, 2053, "Wool cape combat stats")
    require_exact(items_by_id, 184, "magicBonus", 0, "Legacy wizard robe magic offense removal")
    require_exact(items_by_id, 185, "magicBonus", 0, "Legacy blue wizard hat magic offense removal")
    require_absent(items_by_id, 187, "Legacy blue skirt combat stats")
    require_exact(items_by_id, 199, "magicBonus", 0, "Legacy black wizard hat magic offense removal")
    require_exact(items_by_id, 216, "magicBonus", 0, "Legacy black robe magic offense removal")
    require_exact(items_by_id, 423, "meleeOffense", 9, "Black dagger steel-tier profile")
    require_exact(items_by_id, 423, "prayerBonus", 1, "Black dagger prayer bonus")
    require_exact(items_by_id, 565, "meleeOffense", items_by_id[423]["meleeOffense"], "Poisoned black dagger parity")
    require_exact(items_by_id, 565, "prayerBonus", items_by_id[423]["prayerBonus"], "Poisoned black dagger prayer parity")
    require_exact(items_by_id, 424, "meleeOffense", 20, "Black shortsword steel-tier profile")
    require_exact(items_by_id, 427, "meleeOffense", 19, "Black scimitar steel-tier profile")
    require_exact(items_by_id, 425, "meleeOffense", 36, "Black longsword steel-tier profile")
    require_exact(items_by_id, 429, "meleeOffense", 38, "Black battleaxe steel-tier profile")
    require_exact(items_by_id, 430, "meleeOffense", 25, "Black mace steel-tier profile")
    require_exact(items_by_id, 426, "meleeOffense", 86, "Black 2h steel-tier profile")
    require_exact(items_by_id, 2151, "meleeOffense", items_by_id[423]["meleeOffense"], "White dagger mirrors black dagger")
    require_exact(items_by_id, 2152, "meleeOffense", items_by_id[424]["meleeOffense"], "White shortsword mirrors black shortsword")
    require_exact(items_by_id, 2155, "meleeOffense", items_by_id[427]["meleeOffense"], "White scimitar mirrors black scimitar")
    require_exact(items_by_id, 2153, "meleeOffense", items_by_id[425]["meleeOffense"], "White longsword mirrors black longsword")
    require_exact(items_by_id, 2156, "meleeOffense", items_by_id[429]["meleeOffense"], "White battleaxe mirrors black battleaxe")
    require_exact(items_by_id, 2157, "meleeOffense", items_by_id[430]["meleeOffense"], "White mace mirrors black mace")
    require_exact(items_by_id, 2154, "meleeOffense", items_by_id[426]["meleeOffense"], "White 2h mirrors black 2h")
    require_uniform(items_by_id, [423, 565, 2151], "weaponSpeed", 5, "Black/white dagger prayer family speed")
    require_uniform(items_by_id, [424, 2152], "weaponSpeed", 4, "Black/white shortsword prayer family speed")
    require_uniform(items_by_id, [427, 2155], "weaponSpeed", 4, "Black/white scimitar prayer family speed")
    require_uniform(items_by_id, [425, 2153, 429, 2156, 430, 2157], "requiredLevel", 5, "Black/white steel-tier melee requirement")
    require_uniform(items_by_id, [426, 2154], "prayerBonus", 4, "Black/white 2h prayer bonus")
    require_uniform(items_by_id, [430, 2157], "prayerBonus", 2, "Black/white mace prayer bonus")
    require_exact(items_by_id, 470, "prayerBonus", 1, "Black medium helm prayer bonus")
    require_exact(items_by_id, 230, "prayerBonus", 2, "Black large helm prayer bonus")
    require_exact(items_by_id, 432, "prayerBonus", 2, "Black square shield prayer bonus")
    require_exact(items_by_id, 433, "prayerBonus", 3, "Black kite shield prayer bonus")
    require_exact(items_by_id, 196, "prayerBonus", 4, "Black plate body prayer bonus")
    require_exact(items_by_id, 313, "prayerBonus", 4, "Black plate top prayer bonus")
    require_exact(items_by_id, 248, "prayerBonus", 3, "Black plate legs prayer bonus")
    require_exact(items_by_id, 434, "prayerBonus", 3, "Black plated skirt prayer bonus")
    require_exact(items_by_id, 431, "prayerBonus", 3, "Black chain body prayer bonus")
    require_exact(items_by_id, 1533, "prayerBonus", 3, "Black chain top prayer bonus")
    require_exact(items_by_id, 1424, "prayerBonus", 2, "Black chain legs prayer bonus")
    require_exact(items_by_id, 196, "meleeDefense", 19, "Black plate body tier-5 metal defense")
    require_exact(items_by_id, 196, "rangedDefense", 6, "Black plate body tier-5 metal defense")
    require_exact(items_by_id, 214, "meleeDefense", 7, "Bronze plate legs standard metal defense")
    require_exact(items_by_id, 214, "rangedDefense", 2, "Bronze plate legs standard metal defense")
    require_exact(items_by_id, 215, "meleeDefense", 9, "Iron plate legs standard metal defense")
    require_exact(items_by_id, 215, "rangedDefense", 3, "Iron plate legs standard metal defense")
    require_exact(items_by_id, 225, "meleeDefense", 12, "Steel plate legs standard metal defense")
    require_exact(items_by_id, 225, "rangedDefense", 3, "Steel plate legs standard metal defense")
    require_exact(items_by_id, 226, "meleeDefense", 14, "Mithril plate legs standard metal defense")
    require_exact(items_by_id, 226, "rangedDefense", 4, "Mithril plate legs standard metal defense")
    require_exact(items_by_id, 227, "meleeDefense", 18, "Adamantite plate legs standard metal defense")
    require_exact(items_by_id, 227, "rangedDefense", 6, "Adamantite plate legs standard metal defense")
    require_exact(items_by_id, 406, "meleeDefense", 23, "Rune plate legs standard metal defense")
    require_exact(items_by_id, 406, "rangedDefense", 7, "Rune plate legs standard metal defense")
    require_uniform(items_by_id, [1290, 1293, 1296], "meleeDefense", 1, "Ironman helms explicit cosmetic defense")
    require_uniform(items_by_id, [1291, 1294, 1297, 1554, 1555, 1556], "meleeDefense", 2, "Ironman platebodies explicit cosmetic defense")
    require_uniform(items_by_id, [1292, 1295, 1298, 1557, 1558, 1559], "meleeDefense", 1, "Ironman platelegs explicit cosmetic defense")
    require_uniform(items_by_id, [183, 209, 229, 511, 512, 513, 514, 1288, 1373, 1374, 1375, 1376, 1377, 1380, 1381, 1382, 1383, 1384, 1516, 1518, 1519, 1520, 1521, 1522, 1523, 1524, 1525, 1526, 1527, 1528, 1529], "meleeDefense", 1, "Cape explicit cosmetic defense")
    require_exact(items_by_id, 248, "meleeDefense", 12, "Black plate legs tier-5 metal defense")
    require_exact(items_by_id, 248, "rangedDefense", 3, "Black plate legs tier-5 metal defense")
    require_exact(items_by_id, 431, "meleeDefense", 12, "Black chain body tier-5 metal defense")
    require_exact(items_by_id, 432, "meleeDefense", 8, "Black square shield tier-5 metal defense")
    require_exact(items_by_id, 432, "rangedDefense", 2, "Black square shield tier-5 metal defense")
    require_exact(items_by_id, 433, "meleeDefense", 12, "Black paladin shield tier-5 prayer defense")
    require_field_absent(items_by_id, 433, "rangedDefense", "Black paladin shield tier-5 prayer defense")
    require_exact(items_by_id, 433, "magicDefense", 3, "Black paladin shield tier-5 prayer defense")
    require_exact(items_by_id, 2158, "prayerBonus", 2, "White large helm prayer bonus")
    require_exact(items_by_id, 2159, "prayerBonus", 1, "White medium helm prayer bonus")
    require_exact(items_by_id, 2160, "prayerBonus", 3, "White chain body prayer bonus")
    require_exact(items_by_id, 2168, "prayerBonus", 3, "White chain top prayer bonus")
    require_exact(items_by_id, 2167, "prayerBonus", 2, "White chain legs prayer bonus")
    require_exact(items_by_id, 2161, "prayerBonus", 2, "White square shield prayer bonus")
    require_exact(items_by_id, 2162, "prayerBonus", 3, "White kite shield prayer bonus")
    require_exact(items_by_id, 2162, "meleeDefense", items_by_id[433]["meleeDefense"], "White paladin shield mirrors black paladin shield")
    require_field_absent(items_by_id, 2162, "rangedDefense", "White paladin shield mirrors black paladin shield")
    require_exact(items_by_id, 2162, "magicDefense", items_by_id[433]["magicDefense"], "White paladin shield mirrors black paladin shield")
    require_exact(items_by_id, 2163, "prayerBonus", 4, "White plate body prayer bonus")
    require_exact(items_by_id, 2165, "prayerBonus", 4, "White plate top prayer bonus")
    require_exact(items_by_id, 2164, "prayerBonus", 3, "White plate legs prayer bonus")
    require_exact(items_by_id, 2166, "prayerBonus", 3, "White plated skirt prayer bonus")
    require_exact(items_by_id, 2163, "meleeDefense", items_by_id[196]["meleeDefense"], "White plate body mirrors black plate body")
    require_exact(items_by_id, 2163, "rangedDefense", items_by_id[196]["rangedDefense"], "White plate body mirrors black plate body")
    require_exact(items_by_id, 2164, "meleeDefense", items_by_id[248]["meleeDefense"], "White plate legs mirrors black plate legs")
    require_exact(items_by_id, 2164, "rangedDefense", items_by_id[248]["rangedDefense"], "White plate legs mirrors black plate legs")
    require_exact(items_by_id, 2160, "meleeDefense", items_by_id[431]["meleeDefense"], "White chain body mirrors black chain body")
    require_no_equip_requirement(items_by_id, [470, 230, 431, 1533, 1424, 432, 433, 196, 313, 248, 434, 2158, 2159, 2160, 2168, 2167, 2161, 2162, 2163, 2165, 2164, 2166], "Black/white prayer armour requirement removal")
    require_exact(items_by_id, 795, "meleeDefense", 10, "Dragon medium helmet tier-10 hybrid line")
    require_exact(items_by_id, 795, "rangedDefense", 8, "Dragon medium helmet tier-10 hybrid line")
    require_exact(items_by_id, 795, "magicDefense", 5, "Dragon medium helmet tier-10 hybrid line")
    require_exact(items_by_id, 1425, "meleeDefense", 22, "Large dragon helmet tier-11 hybrid line")
    require_exact(items_by_id, 1425, "rangedDefense", 17, "Large dragon helmet tier-11 hybrid line")
    require_exact(items_by_id, 1425, "magicDefense", 11, "Large dragon helmet tier-11 hybrid line")
    require_exact(items_by_id, 1278, "meleeDefense", 22, "Dragon square shield tier-11 hybrid line")
    require_exact(items_by_id, 1278, "rangedDefense", 17, "Dragon square shield tier-11 hybrid line")
    require_exact(items_by_id, 1278, "magicDefense", 11, "Dragon square shield tier-11 hybrid line")
    require_exact(items_by_id, 1426, "meleeDefense", 30, "Dragon paladin shield prayer defense")
    require_field_absent(items_by_id, 1426, "rangedDefense", "Dragon paladin shield prayer defense")
    require_exact(items_by_id, 1426, "magicDefense", 23, "Dragon paladin shield prayer defense")
    require_exact(items_by_id, 1368, "meleeDefense", 30, "Dragon scale mail body tier-10 hybrid line")
    require_exact(items_by_id, 1368, "rangedDefense", 23, "Dragon scale mail body tier-10 hybrid line")
    require_exact(items_by_id, 1368, "magicDefense", 15, "Dragon scale mail body tier-10 hybrid line")
    require_exact(items_by_id, 1537, "meleeDefense", 30, "Dragon scale mail top tier-10 hybrid line")
    require_exact(items_by_id, 1537, "rangedDefense", 23, "Dragon scale mail top tier-10 hybrid line")
    require_exact(items_by_id, 1537, "magicDefense", 15, "Dragon scale mail top tier-10 hybrid line")
    require_exact(items_by_id, 1427, "meleeDefense", 44, "Dragon plate body tier-11 hybrid line")
    require_exact(items_by_id, 1427, "rangedDefense", 33, "Dragon plate body tier-11 hybrid line")
    require_exact(items_by_id, 1427, "magicDefense", 22, "Dragon plate body tier-11 hybrid line")
    require_exact(items_by_id, 1428, "meleeDefense", 44, "Dragon plate top normalized tier-11 hybrid line")
    require_exact(items_by_id, 1428, "rangedDefense", 33, "Dragon plate top normalized tier-11 hybrid line")
    require_exact(items_by_id, 1428, "magicDefense", 22, "Dragon plate top normalized tier-11 hybrid line")
    require_exact(items_by_id, 1429, "meleeDefense", 33, "Dragon plate legs tier-11 hybrid line")
    require_exact(items_by_id, 1429, "rangedDefense", 25, "Dragon plate legs tier-11 hybrid line")
    require_exact(items_by_id, 1429, "magicDefense", 17, "Dragon plate legs tier-11 hybrid line")
    require_exact(items_by_id, 1430, "meleeDefense", 33, "Dragon plate skirt normalized tier-11 hybrid line")
    require_exact(items_by_id, 1430, "rangedDefense", 25, "Dragon plate skirt normalized tier-11 hybrid line")
    require_exact(items_by_id, 1430, "magicDefense", 17, "Dragon plate skirt normalized tier-11 hybrid line")
    require_no_equip_requirement(items_by_id, [795, 1278, 1368, 1537, 1425, 1426, 1427, 1428, 1429, 1430], "Dragon armor requirement removal")

    important_npcs = [3, 6, 15, 40, 41, 22, 184, 196, 201, 202, 291, 477, 81, 789]
    for npc_id in important_npcs:
        if npc_id not in npcs_by_id:
            fail(f"NpcDefsMyWorld.json missing expected npc override id {npc_id}")

    require_exact(npcs_by_id, 3, "meleeDefenseMultiplier", 1.0, "Chicken low-defense multipliers")
    require_exact(npcs_by_id, 3, "rangedDefenseMultiplier", 0.1, "Chicken low-defense multipliers")
    require_exact(npcs_by_id, 3, "magicDefenseMultiplier", 0.1, "Chicken low-defense multipliers")
    require_exact(npcs_by_id, 6, "meleeDefenseMultiplier", 1.0, "Cow low-defense multipliers")
    require_exact(npcs_by_id, 6, "rangedDefenseMultiplier", 0.1, "Cow low-defense multipliers")
    require_exact(npcs_by_id, 6, "magicDefenseMultiplier", 0.1, "Cow low-defense multipliers")

    require_exact(npcs_by_id, 22, "meleeDefenseMultiplier", 0.75, "Lesser demon style-identity multipliers")
    require_exact(npcs_by_id, 22, "rangedDefenseMultiplier", 0.5, "Lesser demon style-identity multipliers")
    require_exact(npcs_by_id, 22, "magicDefenseMultiplier", 1.0, "Lesser demon style-identity multipliers")
    require_exact(npcs_by_id, 15, "meleeDefenseMultiplier", 0.1, "Ghost style-identity multipliers")
    require_exact(npcs_by_id, 15, "rangedDefenseMultiplier", 0.1, "Ghost style-identity multipliers")
    require_exact(npcs_by_id, 15, "magicDefenseMultiplier", 1.0, "Ghost style-identity multipliers")
    require_exact(npcs_by_id, 40, "meleeDefenseMultiplier", 0.25, "Skeleton style-identity multipliers")
    require_exact(npcs_by_id, 40, "rangedDefenseMultiplier", 1.0, "Skeleton style-identity multipliers")
    require_exact(npcs_by_id, 40, "magicDefenseMultiplier", 0.5, "Skeleton style-identity multipliers")
    require_exact(npcs_by_id, 41, "meleeDefenseMultiplier", 1.0, "Zombie style-identity multipliers")
    require_exact(npcs_by_id, 41, "rangedDefenseMultiplier", 0.5, "Zombie style-identity multipliers")
    require_exact(npcs_by_id, 41, "magicDefenseMultiplier", 0.5, "Zombie style-identity multipliers")
    require_exact(npcs_by_id, 65, "meleeDefenseMultiplier", 1.0, "Guard style-identity multipliers")
    require_exact(npcs_by_id, 65, "rangedDefenseMultiplier", 0.1, "Guard style-identity multipliers")
    require_exact(npcs_by_id, 65, "magicDefenseMultiplier", 0.1, "Guard style-identity multipliers")
    require_exact(npcs_by_id, 102, "meleeDefenseMultiplier", 1.0, "White knight style-identity multipliers")
    require_exact(npcs_by_id, 102, "rangedDefenseMultiplier", 0.1, "White knight style-identity multipliers")
    require_exact(npcs_by_id, 102, "magicDefenseMultiplier", 0.1, "White knight style-identity multipliers")
    require_exact(npcs_by_id, 102, "topColour", 15196886, "White knight armour recolor")
    require_exact(npcs_by_id, 102, "bottomColour", 15196886, "White knight armour recolor")
    require_exact(npcs_by_id, 531, "meleeDefenseMultiplier", 1.0, "Ogre style-identity multipliers")
    require_exact(npcs_by_id, 531, "rangedDefenseMultiplier", 0.75, "Ogre style-identity multipliers")
    require_exact(npcs_by_id, 531, "magicDefenseMultiplier", 0.25, "Ogre style-identity multipliers")
    require_exact(npcs_by_id, 263, "meleeDefenseMultiplier", 0.25, "Ice spider style-identity multipliers")
    require_exact(npcs_by_id, 263, "rangedDefenseMultiplier", 1.0, "Ice spider style-identity multipliers")
    require_exact(npcs_by_id, 263, "magicDefenseMultiplier", 0.5, "Ice spider style-identity multipliers")
    require_exact(npcs_by_id, 202, "meleeDefenseMultiplier", 1.0, "Dragon style-identity multipliers")
    require_exact(npcs_by_id, 202, "rangedDefenseMultiplier", 0.75, "Dragon style-identity multipliers")
    require_exact(npcs_by_id, 202, "magicDefenseMultiplier", 1.0, "Dragon style-identity multipliers")
    require_exact(npcs_by_id, 789, "magicDefenseMultiplier", 1.0, "Battle mage style-identity multipliers")

    print("PASS: combat override data validated")
    print(f"Items overridden: {len(item_entries)}")
    print(f"NPCs overridden: {len(npc_entries)}")


if __name__ == "__main__":
    main()
