#!/usr/bin/env python3
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BASE_ITEMS = ROOT / "server/conf/server/defs/ItemDefs.json"
CUSTOM_ITEMS = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
MYWORLD_ITEMS = ROOT / "server/conf/server/defs/ItemDefsMyWorld.json"
EQUIPMENT = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
STAT_CALCULATOR = ROOT / "server/src/com/openrsc/server/model/container/EquipmentStatCalculator.java"
DESTRUCTION = ROOT / (
    "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/prayer/"
    "DestroyOpposingBlessedObject.java"
)
POISONING = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/itemactions/"
    "InvItemPoisoning.java"
)
PLAN = ROOT / "docs/myworld/in-progress-work-plans/prayer-devotion-equipment-plan.md"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def load_items(path: Path, key: str) -> dict[int, dict]:
    entries = json.loads(path.read_text(encoding="utf-8"))[key]
    return {entry["id"]: entry for entry in entries}


def main() -> None:
    effective_items = load_items(BASE_ITEMS, "item")
    effective_items.update(load_items(CUSTOM_ITEMS, "items"))
    for item_id, override in load_items(MYWORLD_ITEMS, "items").items():
        effective_items[item_id] = {**effective_items.get(item_id, {}), **override}

    equipment = EQUIPMENT.read_text(encoding="utf-8")
    stat_calculator = STAT_CALCULATOR.read_text(encoding="utf-8")
    destruction = DESTRUCTION.read_text(encoding="utf-8")
    poisoning = POISONING.read_text(encoding="utf-8")
    plan = PLAN.read_text(encoding="utf-8")

    require(effective_items[1]["name"] == "Iron Short Sword"
            and effective_items[1]["prayerBonus"] == 0,
            "Iron Short Sword must explicitly clear its inherited Prayer bonus")
    require(effective_items[565]["name"] == "Poisoned Black dagger"
            and effective_items[565]["prayerBonus"] == effective_items[423]["prayerBonus"] == 1,
            "Poisoned Black Dagger should retain Black Dagger Prayer parity")

    require("case 565: // POISONED_BLACK_DAGGER" in equipment,
            "Poisoned Black Dagger must require Zamorak worship")
    require(stat_calculator.count("case 565:") == 4,
            "Poisoned Black Dagger must share resource, aim, power, and offense scaling")
    require(destruction.count("case 565: // POISONED_BLACK_DAGGER") == 2,
            "Poisoned Black Dagger must have Zamorak identity and tier-one destruction value")

    require('String poisonedVersion = "Poisoned " + name;' in poisoning,
            "Poisoned weapon acquisition should still use exact product names")
    missing_variants = (
        "Poisoned White Dagger",
        "Poisoned Grey Dagger",
        "Poisoned Black Spear",
        "Poisoned White Spear",
        "Poisoned Grey Spear",
    )
    item_names = {item["name"].lower() for item in effective_items.values()}
    for variant in missing_variants:
        require(variant.lower() not in item_names,
                f"{variant} unexpectedly exists without completing the planned feature wave")
        require(variant in plan, f"Deferred poisoned god variant is undocumented: {variant}")

    print("PASS: neutral Iron Short Sword and Zamorak-aligned Poisoned Black Dagger validated")


if __name__ == "__main__":
    main()
