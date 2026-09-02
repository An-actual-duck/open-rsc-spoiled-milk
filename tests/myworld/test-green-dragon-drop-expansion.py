#!/usr/bin/env python3
"""Validate the farmable ID-862 table without weakening Elvarg isolation."""

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DROPS = ROOT / "server/src/com/openrsc/server/constants/NpcDrops.java"
DEFS = ROOT / "server/conf/server/defs"


def require(condition: bool, message: str) -> None:
    if not condition:
        print(f"FAIL: {message}")
        sys.exit(1)


def between(source: str, start: str, end: str) -> str:
    match = re.search(re.escape(start) + r"(.*?)" + re.escape(end), source, re.DOTALL)
    require(match is not None, f"missing drop block beginning {start}")
    return match.group(1)


def weights(source: str) -> list[int]:
    return [
        int(weight)
        for weight in re.findall(
            r"currentNpcDrops\.add(?:Item|Table)Drop\([^;]*?,\s*(\d+)\s*\);",
            source,
        )
    ]


def main() -> None:
    drops = DROPS.read_text(encoding="utf-8")
    elvarg = between(drops, 'new DropTable("Elvarg (196)");', 'new DropTable("Green Dragon (862)");')
    green = between(drops, 'new DropTable("Green Dragon (862)");', 'new DropTable("Dark Warrior (199)");')

    require("addItemDrop" not in elvarg and "addTableDrop" not in elvarg,
            "Elvarg must retain an empty normal table")
    require("addEmptyDrop(128 - currentNpcDrops.getTotalWeight())" in elvarg,
            "Elvarg must retain its 128-weight empty table")

    conditional = re.search(
        r"if\(config\.WANT_OPENPK_POINTS\) \{(.*?)\} else \{(.*?)\}(.*)",
        green,
        re.DOTALL,
    )
    require(conditional is not None, "ordinary Green Dragon config branches are malformed")
    openpk, normal, common = conditional.groups()
    common = common.split("currentNpcDrops.addEmptyDrop", 1)[0]
    require(sum(weights(openpk)) + sum(weights(common)) == 126,
            "OpenPK ordinary Green Dragon rewards must leave exactly weight 2 empty")
    require(sum(weights(normal)) + sum(weights(common)) == 126,
            "normal ordinary Green Dragon rewards must leave exactly weight 2 empty")

    intended = {
        "ItemId.COINS.id(), 250, 17": "OpenPK 250-coin roll",
        "ItemId.COINS.id(), 250, 27": "normal 250-coin roll",
        "ItemId.COINS.id(), 500, 18": "500-coin roll",
        "ItemId.COINS.id(), 1000, 8": "1,000-coin roll",
        "ItemId.COINS.id(), 2500, 1": "2,500-coin roll",
        "ItemId.EARTH_RUNE.id(), 250, 10": "250 earth runes",
        "ItemId.EARTH_RUNE.id(), 500, 4": "500 earth runes",
        "ItemId.CHAOS_RUNE.id(), 40, 9": "40 chaos runes",
        "ItemId.CHAOS_RUNE.id(), 100, 3": "100 chaos runes",
        "ItemId.DEATH_RUNE.id(), 15, 6": "15 death runes",
        "ItemId.DEATH_RUNE.id(), 40, 2": "40 death runes",
        "ItemId.BLOOD_RUNE.id(), 10, 4": "10 blood runes",
        "ItemId.RUNE_PLATE_MAIL_LEGS.id(), 1, 1": "Rune plate legs",
        "ItemId.LARGE_RUNE_HELMET.id(), 1, 1": "large Rune helmet",
        "ItemId.PINE_STAFF_OF_EARTH.id(), 1, 2": "Earth Pine Staff",
        "ItemId.MAPLE_STAFF_OF_EARTH.id(), 1, 1": "Earth Maple Staff",
    }
    for snippet, label in intended.items():
        require(snippet in green, f"ordinary Green Dragon table missing {label}: {snippet}")
    blue = between(drops, 'new DropTable("Blue Dragon (202)");', 'new DropTable("Zombie (Entrana) (214)");')
    for retired in ("EARTH_TALISMAN", "WATER_TALISMAN", "EARTH_ORB", "BATTLESTAFF_OF_EARTH"):
        require(retired not in green, f"ordinary table must not use retired item {retired}")
    require("WATER_TALISMAN" not in blue, "adult Blue Dragon must not be documented or treated as dropping Water talismans")

    items: dict[int, dict] = {}
    for filename in ("ItemDefs.json", "ItemDefsCustom.json"):
        payload = json.loads((DEFS / filename).read_text(encoding="utf-8"))
        entries = payload.get("item", payload.get("items"))
        items.update({int(entry["id"]): entry for entry in entries})
    active_rewards = {
        34: ("Earth-Rune", 4),
        38: ("Death-Rune", 20),
        41: ("Chaos-Rune", 10),
        619: ("Blood-Rune", 25),
        112: ("Large Rune Helmet", 35200),
        402: ("Rune Plate Mail Legs", 64000),
        1777: ("Earth Maple Staff", 2500),
        2134: ("Earth Pine Staff", 875),
    }
    for item_id, (name, price) in active_rewards.items():
        entry = items.get(item_id)
        require(entry is not None and entry.get("name") == name,
                f"drop reward {item_id} must resolve to active item {name}")
        require(entry.get("basePrice") == price and entry.get("isUntradable") == 0,
                f"drop reward {name} must retain its reviewed active/tradable economy definition")

    hidden = between(drops, "private void createHiddenUniqueDrops()", "private void addHiddenUniqueDrop(final int npcId")
    require("addHiddenUniqueDrop(NpcId.GREEN_DRAGON.id(), ItemId.EARTH_SWORD.id(), 1, HiddenUniqueRarity.ULTRA_RARE_UNIQUE);" in hidden,
            "ordinary Green Dragon Earth Sword rate must remain 1/4096")
    require("addHiddenUniqueDrop(NpcId.GREEN_DRAGON.id(), ItemId.RAW_DRAGON_METAL.id(), 1, 1, 1024);" in hidden,
            "ordinary Green Dragon raw dragon metal rate must remain 1/1024")
    require("addHiddenUniqueDrop(NpcId.DRAGON.id()," not in hidden,
            "Elvarg must not gain hidden drops")
    require("this.dragonNpcs.add(NpcId.DRAGON.id());" in drops
            and "this.dragonNpcs.add(NpcId.GREEN_DRAGON.id());" in drops,
            "both variants must retain dragon bones")
    require("addGuaranteedDrop(NpcId.DRAGON.id(), ItemId.DRAGON_HIDE.id()" in drops
            and "addGuaranteedDrop(NpcId.GREEN_DRAGON.id(), ItemId.DRAGON_HIDE.id()" in drops,
            "both variants must retain guaranteed green dragon hide")

    print("PASS: ordinary Green Dragon expanded table totals 128 and leaves Elvarg isolated")


if __name__ == "__main__":
    main()
