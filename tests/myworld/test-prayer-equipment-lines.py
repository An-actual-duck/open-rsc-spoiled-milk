#!/usr/bin/env python3
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def load_items(path: str, key: str):
    return json.loads((ROOT / path).read_text())[key]


def by_id(items, item_id: int):
    for item in items:
        if item["id"] == item_id:
            return item
    raise AssertionError(f"missing item {item_id}")


def main():
    base_items = load_items("server/conf/server/defs/ItemDefs.json", "item")
    custom_items = load_items("server/conf/server/defs/ItemDefsCustom.json", "items")

    expected_prayer = {
        1213: 5,  # Zamorak Cape
        1214: 5,  # Saradomin Cape
        1215: 5,  # Guthix Cape
        59: 1,    # Phoenix Crossbow
        60: 1,    # Crossbow
        0: 4,     # Iron Mace
        94: 3,    # Bronze Mace
        95: 5,    # Steel Mace
        96: 6,    # Mithril Mace
        97: 8,    # Adamantite Mace
        98: 10,   # Rune Mace
        1453: 10, # Dragon crossbow
        2003: 1,  # Tin Mace
        2014: 2,  # Copper Mace
        2025: 7,  # Titan steel Mace
        2036: 9,  # Orichalcum Mace
        430: 7,   # Black Mace
        2157: 6,  # White Mace
        3119: 6,  # Grey Mace
        2169: 2,  # Oak Crossbow
        2170: 3,  # Willow Crossbow
        2171: 4,  # Palm Crossbow
        2172: 5,  # Maple Crossbow
        2173: 6,  # Yew Crossbow
        2174: 7,  # Ebony Crossbow
        2175: 8,  # Magic Crossbow
        2176: 9,  # Blood Crossbow
        2: 4,     # Iron Paladin Shield
        128: 3,   # Bronze Paladin Shield
        129: 5,   # Steel Paladin Shield
        130: 6,   # Mithril Paladin Shield
        131: 8,   # Adamantite Paladin Shield
        404: 10,  # Rune Paladin Shield
        433: 7,   # Black Paladin Shield
        1426: 11, # Dragon Paladin Shield
        1962: 1,  # Tin Paladin Shield
        1968: 2,  # Copper Paladin Shield
        1974: 7,  # Titan steel Paladin Shield
        1980: 9,  # Orichalcum Paladin Shield
        2161: 1,  # White Square Shield
        2162: 6,  # White Paladin Shield
        432: 1,   # Black Square Shield
        3123: 1,  # Grey Square Shield
        3124: 6,  # Grey Paladin Shield
        196: 1,   # Black Plate Mail Body
        230: 1,   # Large Black Helmet
        248: 1,   # Black Plate Mail Legs
        313: 1,   # Black Plate Mail top
        2158: 1,  # Large White Helmet
        2163: 1,  # White Plate Mail Body
        2164: 1,  # White Plate Mail Legs
        2165: 1,  # White Plate Mail top
        2166: 1,  # White Plated skirt
        3120: 1,  # Large Grey Helmet
        3125: 1,  # Grey Plate Mail Body
        3126: 1,  # Grey Plate Mail Legs
        3127: 1,  # Grey Plate Mail top
        3128: 1,  # Grey Plated skirt
        385: 8,   # Holy Symbol of Saradomin
        1029: 8,  # Unholy Symbol of Zamorak
        1523: 8,  # Prayer cape
        522: 0,   # Dragonstone Amulet legacy holdover should not grant prayer
        597: 0,   # Charged Dragonstone Amulet legacy holdover should not grant prayer
    }

    all_items = {item["id"]: item for item in base_items}
    all_items.update({item["id"]: item for item in custom_items})
    for item_id, prayer in expected_prayer.items():
        assert all_items[item_id]["prayerBonus"] == prayer, (
            f"{all_items[item_id]['name']} should give {prayer} prayer bonus"
        )

    expected_staffs = {
        2131: (7, 3, 6),
        2132: (7, 3, 20),
        2133: (7, 3, 20),
        2134: (7, 3, 20),
        2135: (7, 3, 20),
        2136: (11, 5, 6),
        2137: (11, 5, 20),
        2138: (11, 5, 20),
        2139: (11, 5, 20),
        2140: (11, 5, 20),
        2141: (15, 7, 6),
        2142: (15, 7, 20),
        2143: (15, 7, 20),
        2144: (15, 7, 20),
        2145: (15, 7, 20),
        2146: (18, 9, 6),
        2147: (18, 9, 20),
        2148: (18, 9, 20),
        2149: (18, 9, 20),
        2150: (18, 9, 20),
    }
    for item_id, (aim, power, magic) in expected_staffs.items():
        item = by_id(custom_items, item_id)
        assert item["weaponAimBonus"] == aim, f"{item['name']} aim mismatch"
        assert item["weaponPowerBonus"] == power, f"{item['name']} power mismatch"
        assert item["magicBonus"] == magic, f"{item['name']} magic mismatch"

    expected_custom_maces = {
        2003: (3, 2),
        2014: (4, 3),
        2025: (21, 15),
        2036: (32, 24),
    }
    for item_id, (aim, power) in expected_custom_maces.items():
        item = by_id(custom_items, item_id)
        assert item["weaponAimBonus"] == aim, f"{item['name']} aim mismatch"
        assert item["weaponPowerBonus"] == power, f"{item['name']} power mismatch"

    expected_square_names = {
        2224: "Tin Square Shield",
        2225: "Copper Square Shield",
        2226: "Titan Steel Square Shield",
        2227: "Orichalcum Square Shield",
    }
    for item_id, name in expected_square_names.items():
        item = by_id(custom_items, item_id)
        assert item["name"] == name
        assert item["prayerBonus"] == 0

    expected_blessed_staffs = {
        2228: ("Blessed Staff", 6, 2, 6, 1),
        2229: ("Blessed Pine Staff", 7, 3, 6, 2),
        2230: ("Blessed Oak Staff", 8, 4, 6, 3),
        2231: ("Blessed Willow Staff", 10, 5, 6, 4),
        2232: ("Blessed Palm Staff", 11, 5, 6, 5),
        2233: ("Blessed Maple Staff", 12, 6, 6, 6),
        2234: ("Blessed Yew Staff", 14, 7, 6, 7),
        2235: ("Blessed Ebony Staff", 15, 7, 6, 8),
        2236: ("Blessed Magic Staff", 16, 8, 6, 9),
        2237: ("Blessed Blood Staff", 18, 9, 6, 10),
    }
    for item_id, (name, aim, power, magic, prayer) in expected_blessed_staffs.items():
        item = by_id(custom_items, item_id)
        assert item["name"] == name
        assert item["weaponAimBonus"] == aim
        assert item["weaponPowerBonus"] == power
        assert item["magicBonus"] == magic
        assert item["prayerBonus"] == prayer

    expected_god_staffs = {
        1216: ("Staff of Zamorak", 20, 10, 0, 11),
        1217: ("Staff of Guthix", 20, 10, 0, 11),
        1218: ("Staff of Saradomin", 20, 10, 0, 11),
    }
    for item_id, (name, aim, power, magic, prayer) in expected_god_staffs.items():
        item = by_id(base_items, item_id)
        assert item["name"] == name
        assert item["weaponAimBonus"] == aim
        assert item["weaponPowerBonus"] == power
        assert item["magicBonus"] == magic
        assert item["prayerBonus"] == prayer

    expected_god_capes = {
        1213: ("Zamorak Cape", 0, 5),
        1214: ("Saradomin Cape", 0, 5),
        1215: ("Guthix Cape", 0, 5),
    }
    for item_id, (name, magic, prayer) in expected_god_capes.items():
        item = by_id(base_items, item_id)
        assert item["name"] == name
        assert item["magicBonus"] == magic
        assert item["prayerBonus"] == prayer

    expected_god_robes = {
        607: ("Robe of Guthix", 6),
        608: ("Robe of Guthix", 5),
        702: ("Robe of Zamorak", 6),
        703: ("Robe of Zamorak", 5),
        807: ("Robe of Saradomin", 6),
        808: ("Robe of Saradomin", 5),
    }
    for item_id, (name, prayer) in expected_god_robes.items():
        item = by_id(base_items, item_id)
        assert item["name"] == name
        assert item["magicBonus"] == 0
        assert item["prayerBonus"] == prayer


if __name__ == "__main__":
    main()
