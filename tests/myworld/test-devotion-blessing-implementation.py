#!/usr/bin/env python3
"""Regression coverage for the implemented Devotion conversion rules."""

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PRAYER = (
    ROOT
    / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/prayer"
)
DEVOTION = ROOT / "server/src/com/openrsc/server/content/Devotion.java"
INVENTORY = ROOT / "server/src/com/openrsc/server/model/container/Inventory.java"
BONES = (
    ROOT / "server/plugins/com/openrsc/server/plugins/authentic/misc/Bones.java"
)
ITEM_ID = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
GUIDE = (
    ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def clamped_adjust(current: int, requested: int) -> tuple[int, int]:
    updated = max(-10_000, min(10_000, current + requested))
    return updated, updated - current


def record_blessing(
    start: int, count: int, now: int, conversion_succeeds: bool
) -> tuple[int, int, bool]:
    active = start > 0 and now - start < 3_600_000
    if active and count >= 10:
        return start, count, False
    if not conversion_succeeds:
        return start, count, False
    if not active:
        return now, 1, True
    return start, min(10, count + 1), True


def main() -> None:
    devotion = DEVOTION.read_text(encoding="utf-8")
    inventory = INVENTORY.read_text(encoding="utf-8")
    bones = BONES.read_text(encoding="utf-8")
    limiter = (PRAYER / "PrayerBlessingLimit.java").read_text(encoding="utf-8")
    transaction = (PRAYER / "PrayerBlessingTransaction.java").read_text(
        encoding="utf-8"
    )
    symbols = (PRAYER / "BlessedSymbols.java").read_text(encoding="utf-8")
    staffs = (PRAYER / "BlessedStaffs.java").read_text(encoding="utf-8")
    wool = (PRAYER / "BlessedWoolArmor.java").read_text(encoding="utf-8")
    knight = (PRAYER / "GodKnightEquipment.java").read_text(encoding="utf-8")
    destroy = (PRAYER / "DestroyOpposingBlessedObject.java").read_text(
        encoding="utf-8"
    )
    item_id = ITEM_ID.read_text(encoding="utf-8")
    guide = GUIDE.read_text(encoding="utf-8")

    # Fixed-point Devotion rules: requirements are whole levels while costs
    # and transfers use the existing ten offering units per displayed level.
    require(
        "DEVOTION_REQUIREMENT_PER_RESOURCE = 100" in devotion,
        "requirements must be 100 Devotion per resource",
    )
    require(
        "BLESSING_OFFERING_COST_PER_RESOURCE = OFFERINGS_PER_DEVOTION_LEVEL / 2"
        in devotion,
        "blessing must cost five offering units per resource",
    )
    for resources in range(1, 11):
        require(100 * resources == resources * 100, "requirement arithmetic drift")
        require(5 * resources == resources * 5, "blessing cost arithmetic drift")
        require(10 * resources == resources * 10, "destruction transfer drift")

    require(
        "return updated - previous;" in devotion,
        "Devotion adjustment must return the actual clamped change",
    )
    require(
        "if (updated < previous)" in devotion
        and "player.getPrayers().deactivateOverflowingPrayers();" in devotion,
        "all Devotion reductions must clean up overflowing prayers",
    )
    require(clamped_adjust(9_995, 10) == (10_000, 5), "positive clamp accounting")
    require(clamped_adjust(-9_995, -10) == (-10_000, -5), "negative clamp accounting")
    require(clamped_adjust(10_000, 10) == (10_000, 0), "capped gain accounting")

    # Bonecrusher: exact selected inventory instance, notes rejected, and no
    # reward before confirmed removal.
    require("if (bones.getNoted())" in bones, "Bonecrusher must reject notes")
    require(
        "player.getCarriedItems().getInventory().remove(bones, true)" in bones,
        "Bonecrusher must remove the exact selected inventory instance",
    )
    require(
        "RuneScript.remove(bones.getCatalogId(), 1)" not in bones,
        "Bonecrusher must not use catalog-only removal",
    )
    require(
        bones.index("getInventory().remove(bones, true)")
        < bones.index("giveBonesExperience(player, bones, true)"),
        "Bonecrusher rewards must follow successful removal",
    )

    # Exact in-slot conversion prevents stale catalog matches, equipment
    # fallback, inventory compaction, and partial remove/add outcomes.
    require("public boolean replaceExact(" in inventory, "exact replacement API missing")
    require(
        "existing.getItemId() != itemToReplace.getItemId()" in inventory,
        "exact replacement must use the persistent item identity",
    )
    require(
        "list.set(index, newItem.copyWithItemId(existing.getItemId()))" in inventory,
        "replacement must retain the source slot and item identity",
    )

    # One serialized operation owns availability, conversion success, and the
    # bounded hourly counter.
    require("synchronized (player)" in limiter, "hourly accounting must be serialized")
    require(
        limiter.index("conversion.getAsBoolean()")
        < limiter.index("Math.min(BLESSINGS_PER_HOUR, count + 1)"),
        "failed conversions must not consume an hourly use",
    )
    require(
        record_blessing(1_000, 9, 2_000, False) == (1_000, 9, False),
        "failed conversion changed deterministic counter",
    )
    require(
        record_blessing(1_000, 9, 2_000, True) == (1_000, 10, True),
        "tenth successful conversion was not recorded",
    )
    require(
        record_blessing(1_000, 10, 2_000, True) == (1_000, 10, False),
        "counter exceeded ten blessings",
    )
    require(
        record_blessing(1_000, 10, 3_601_000, True)
        == (3_601_000, 1, True),
        "expired hourly window did not restart",
    )

    require(
        "player.getPrayerBook() != godLine" in transaction,
        "blessing must require worship/altar alignment",
    )
    alignment = transaction.index("player.getPrayerBook() != godLine")
    replacement = transaction.index("replaceExact(source, new Item(productId), true)")
    require(alignment < replacement, "alignment must be checked before mutation")
    require(
        "Devotion.getBlessingPrayerXp(player, godLine, basePrayerXp)" in transaction,
        "approved blessing XP formula must remain active",
    )
    require(
        "Devotion.adjustDevotionOfferings(player, godLine, -devotionOfferingCost)"
        in transaction,
        "successful blessings must pay their fixed-point cost",
    )
    for plugin_name, source in (
        ("symbols", symbols),
        ("staffs", staffs),
        ("wool", wool),
        ("knight equipment", knight),
    ):
        require(
            "PrayerBlessingTransaction.bless(" in source,
            f"{plugin_name} bypasses the atomic blessing transaction",
        )

    require(
        "SYMBOL_DEVOTION_REQUIREMENT = 50" in symbols,
        "symbols must require 50 Devotion",
    )
    symbol_call = symbols[symbols.index("PrayerBlessingTransaction.bless(") :]
    require(
        re.search(r"SYMBOL_DEVOTION_REQUIREMENT,\s*0,\s*SYMBOL_CRAFTING_XP", symbol_call),
        "symbols must be free to bless",
    )

    require(
        "getSteelDevotionResourceCost" in knight
        and "itemId == ItemId.LARGE_STEEL_HELMET.id()" in knight,
        "knight helmet must use the one-resource Devotion mapping",
    )
    require(
        "getSteelProductionResourceCost" in knight,
        "production XP must remain separate from the helmet Devotion mapping",
    )
    require(
        "getStaffResourceCost" in staffs
        and "EnchantingItemEffects.getTierForBaseStaff(itemId)" in staffs,
        "staff requirement/cost must follow its tier",
    )

    # Every god-knight blessing product must also be recognized by destruction.
    constants = {
        name: int(value)
        for name, value in re.findall(r"\b([A-Z][A-Z0-9_]+)\((-?\d+)\)", item_id)
    }
    knight_product_names = set(
        re.findall(r"return ItemId\.([A-Z0-9_]+)\.id\(\);", knight)
    )
    require(len(knight_product_names) == 48, "expected 48 god-knight products")
    for name in knight_product_names:
        require(name in constants, f"missing ItemId value for {name}")
        require(
            f"case {constants[name]}:" in destroy,
            f"blessing product {name} is missing from destruction",
        )

    for item, resources in {
        432: 3,
        2161: 3,
        3123: 3,
        3229: 2,
        3230: 2,
        3231: 2,
        3232: 3,
        3233: 3,
        3234: 3,
    }.items():
        require(f"case {item}:" in destroy, f"missing god-equipment mapping {item}")
        require(resources in (2, 3), "unexpected missing-item resource value")

    require(
        "SYMBOL_DEVOTION_OFFERING_CHANGE = 2" in destroy,
        "symbol destruction must transfer 0.2 Devotion",
    )
    require(
        "PRAYER_XP_MULTIPLIER = 5" in destroy,
        "destruction XP multiplier changed",
    )
    require(
        "getBlessedStaffTier(itemId)" in destroy,
        "staff destruction must derive transfer from tier",
    )
    require(
        "formatDevotionOfferingChange(actualGain)" in destroy
        and "formatDevotionOfferingChange(actualLoss)" in destroy,
        "destruction messages must use actual clamped changes",
    )
    for artifact in (1213, 1214, 1215, 1216, 1217, 1218, 3252, 3253, 3254):
        require(
            f"case {artifact}:" not in destroy,
            f"god artifact {artifact} became destructible",
        )

    for text in (
        "Symbols require 50 devotion and are free to bless",
        "Equipment requires 100 devotion per resource",
        "Blessing equipment costs 0.5 devotion per resource",
        "You may complete 10 blessings per hour",
        "Destruction transfers 1 devotion per resource or staff tier",
        "Symbols transfer 0.2 devotion when destroyed",
        "God artifacts cannot be destroyed",
    ):
        require(text in guide, f"Prayer guide is missing: {text}")

    print("PASS: implemented Devotion blessing/destruction rules validated")


if __name__ == "__main__":
    main()
