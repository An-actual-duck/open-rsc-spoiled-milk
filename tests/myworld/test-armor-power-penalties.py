#!/usr/bin/env python3
"""Validate MyWorld armor combat-style power penalties."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
EQUIPMENT = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
COMBAT_SPEC = ROOT / "docs/myworld/combat-equipment-spec.md"
WORK_ITEMS = ROOT / "docs/myworld/work-items.md"


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        fail(f"{label} missing expected snippet: {snippet}")


def main() -> None:
    equipment = EQUIPMENT.read_text(encoding="utf-8")
    combat_spec = COMBAT_SPEC.read_text(encoding="utf-8")
    work_items = WORK_ITEMS.read_text(encoding="utf-8")

    for snippet in (
        "private static final int ARMOR_POWER_PENALTY_PER_MAJOR_SLOT = 8;",
        "return getModifiedOffense(PrayerCatalog.CombatStyle.MELEE);",
        "return getModifiedOffense(PrayerCatalog.CombatStyle.RANGED);",
        "return getModifiedOffense(PrayerCatalog.CombatStyle.MAGIC);",
        "private int getModifiedOffense(final PrayerCatalog.CombatStyle combatStyle)",
        "return Math.max(0, getDisplayedModifiedOffense(combatStyle));",
        "private int getDisplayedModifiedOffense(final PrayerCatalog.CombatStyle combatStyle)",
        "return applyOffenseBonus(combatStyle, getRawOffense(combatStyle)) - getArmorPowerPenalty(combatStyle);",
        "private int getArmorPowerPenalty(final PrayerCatalog.CombatStyle combatStyle)",
        "return pieces * ARMOR_POWER_PENALTY_PER_MAJOR_SLOT;",
        "case MELEE:\n\t\t\t\treturn isClothArmorPenaltyItem(item);",
        "case RANGED:\n\t\t\t\treturn isMetalArmorPenaltyItem(item);",
        "case MAGIC:\n\t\t\t\treturn isLeatherArmorPenaltyItem(item);",
        "private boolean isMajorArmorPenaltySlot(Item item)",
        "private boolean isLeatherArmorPenaltyItem(Item item)",
        "private boolean isClothArmorPenaltyItem(Item item)",
        "private boolean isMetalArmorPenaltyItem(Item item)",
    ):
        require(equipment, snippet, "Equipment armor power penalty")

    major_slot_block = equipment.split("private boolean isMajorArmorPenaltySlot(Item item)", 1)[1].split(
        "private boolean isLeatherArmorPenaltyItem(Item item)", 1
    )[0]
    for slot in (
        "SLOT_LARGE_HELMET",
        "SLOT_MEDIUM_HELMET",
        "SLOT_CHAIN_BODY",
        "SLOT_PLATE_BODY",
        "SLOT_PLATE_LEGS",
        "SLOT_SKIRT",
    ):
        require(major_slot_block, slot, "Major armor penalty slots")
    for ignored_slot in ("SLOT_GLOVES", "SLOT_BOOTS", "SLOT_CAPE"):
        if ignored_slot in major_slot_block:
            fail(f"Major armor penalty slots should not include {ignored_slot}")

    for snippet in (
        "## Armor Weapon-Power Penalties",
        "`Metal` armor lowers `Ranged Power`.",
        "`Leather/carapace` armor lowers `Magic Power`.",
        "`Cloth/robe` armor lowers `Melee Power`.",
        "combat offense calculation clamps equipment-side style power at `0`",
    ):
        require(combat_spec, snippet, "Combat equipment spec")

    for snippet in (
        "Armor weapon-power penalties are live:",
        "`Metal` head/chest/legs apply `-8 Ranged Power` per piece",
        "`Leather/carapace` head/chest/legs apply `-8 Magic Power` per piece",
        "`Cloth/robe` head/chest/legs apply `-8 Melee Power` per piece",
        "gloves, boots, and back-slot items do not apply these penalties",
    ):
        require(work_items, snippet, "Work items armor penalty state")

    print("PASS: armor combat-style power penalties validated")


if __name__ == "__main__":
    main()
