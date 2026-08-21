#!/usr/bin/env python3
"""Characterize the runtime facts used by the Devotion balance audit."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PRAYER_PLUGINS = (
    ROOT
    / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/prayer"
)
TRANSACTION = PRAYER_PLUGINS / "PrayerBlessingTransaction.java"
AUDIT = (
    ROOT
    / "docs/myworld/completed-work-plans/devotion-destruction-blessing-audit.md"
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def simulate_offerings(target: int, mode: str) -> tuple[int, int, int]:
    """Return interactions, exact half-offering units, and flat bonus XP."""
    half_offering_units = 0
    interactions = 0
    bonus_xp = 0

    while half_offering_units // 20 < target:
        devotion = half_offering_units // 20
        bonus_xp += devotion
        gain = 2

        if mode in {"symbol", "symbol_summon", "all"} and devotion >= 50:
            gain += 2

        if mode in {"unicorn", "symbol_summon", "summon_set", "all"}:
            gain += 1
        if mode in {"set", "summon_set", "all"}:
            gain += 1

        half_offering_units = min(20_000, half_offering_units + gain)
        interactions += 1

    return interactions, half_offering_units, bonus_xp


def main() -> None:
    devotion = (
        ROOT / "server/src/com/openrsc/server/content/Devotion.java"
    ).read_text(encoding="utf-8")
    bones = (
        ROOT
        / "server/plugins/com/openrsc/server/plugins/authentic/misc/Bones.java"
    ).read_text(encoding="utf-8")
    limiter = (PRAYER_PLUGINS / "PrayerBlessingLimit.java").read_text(
        encoding="utf-8"
    )
    destroy = (PRAYER_PLUGINS / "DestroyOpposingBlessedObject.java").read_text(
        encoding="utf-8"
    )
    artifacts = (
        ROOT / "server/src/com/openrsc/server/content/GodArtifacts.java"
    ).read_text(encoding="utf-8")
    audit = AUDIT.read_text(encoding="utf-8")
    transaction = TRANSACTION.read_text(encoding="utf-8")

    for marker in (
        "OFFERINGS_PER_DEVOTION_LEVEL = OFFERINGS_PER_BONUS_XP",
        "MAX_DEVOTION_LEVEL = DevotionHalfOfferingBalance.MAX_DEVOTION_LEVEL",
        "MIN_DEVOTION_LEVEL = DevotionHalfOfferingBalance.MIN_DEVOTION_LEVEL",
        "DEVOTION_REQUIREMENT_PER_RESOURCE = 100",
        "BLESSING_OFFERING_COST_PER_RESOURCE = OFFERINGS_PER_DEVOTION_LEVEL / 2",
        "OfferingExperience.scaleDisplayedExperience(bonusXp, offeringItemId)",
        "* OfferingExperience.INTERNAL_XP_UNITS_PER_XP;",
        "100.0D + devotionLevel",
        "DevotionOfferingGain.getHalfOfferingUnits(",
    ):
        require(marker in devotion, f"Devotion runtime changed: missing {marker}")

    require(
        "BLESSINGS_PER_HOUR = 10" in limiter
        and "60L * 60L * 1000L" in limiter,
        "blessing limiter is no longer ten per fixed hour",
    )
    require(
        "PrayerBlessingLimit" not in destroy,
        "destruction unexpectedly began consuming blessing slots",
    )
    require(
        "REQUIRED_DEVOTION = 800" in artifacts
        and "DEVOTION_COST = 400" in artifacts,
        "artifact Devotion requirement/cost changed",
    )

    require("if (bones.getNoted())" in bones, "Bonecrusher no longer rejects notes")
    require(
        "RuneScript.remove(bones.getCatalogId(), 1);" not in bones,
        "Bonecrusher regressed to catalog-only removal",
    )
    remove_index = bones.index(
        "player.getCarriedItems().getInventory().remove(bones, true)"
    )
    reward_index = bones.index("giveBonesExperience(player, bones, true);")
    require(
        remove_index < reward_index,
        "Bonecrusher reward precedes exact successful removal",
    )
    require(
        "player.getPrayerBook() != godLine" in transaction,
        "ordinary blessing no longer requires altar alignment",
    )

    # The blessing matrix has 96 distinct results.
    result_ids = {
        385,
        1029,
        3175,
        *range(2228, 2238),
        *range(3137, 3172),
        423,
        424,
        425,
        426,
        427,
        429,
        430,
        230,
        432,
        433,
        196,
        248,
        2151,
        2152,
        2153,
        2154,
        2155,
        2156,
        2157,
        2158,
        2161,
        2162,
        2163,
        2164,
        3113,
        3114,
        3115,
        3116,
        3117,
        3118,
        3119,
        3120,
        3123,
        3124,
        3125,
        3126,
        3131,
        3132,
        3133,
        3134,
        3135,
        3136,
        3229,
        3230,
        3231,
        3232,
        3233,
        3234,
    }
    require(len(result_ids) == 96, "expected 96 distinct normal blessing results")
    for item_id in result_ids:
        require(f"({item_id})" in audit, f"audit matrix omits result item {item_id}")

    for completed_id in (432, 2161, 3123, 3229, 3230, 3231, 3232, 3233, 3234):
        require(
            f"case {completed_id}:" in destroy,
            f"destruction coverage is missing completed item {completed_id}",
        )

    expected_progression = {
        (25, "plain"): (250, 500, 3_000),
        (500, "plain"): (5_000, 10_000, 1_247_500),
        (1000, "plain"): (10_000, 20_000, 4_995_000),
        (500, "symbol"): (2_750, 10_000, 629_875),
        (1000, "symbol"): (5_250, 20_000, 2_503_625),
        (500, "unicorn"): (3_334, 10_002, 831_833),
        (500, "set"): (3_334, 10_002, 831_833),
        (500, "symbol_summon"): (2_134, 10_002, 502_283),
        (500, "summon_set"): (2_500, 10_000, 623_750),
        (500, "all"): (1_750, 10_000, 417_725),
        (1000, "all"): (3_417, 20_000, 1_667_058),
    }
    for key, expected in expected_progression.items():
        require(
            simulate_offerings(*key) == expected,
            f"offering progression changed for target/mode {key}",
        )

    for statement in (
        "## Implementation Status",
        "10 successful blessings / one fixed one-hour window",
        "The matrix contains 96 ordinary blessing results",
        "God square shields, spears, and scythes",
        "Bonecrusher correctness fix",
    ):
        require(statement in audit, f"audit is missing required finding: {statement}")

    print("PASS: Devotion/blessing/destruction audit facts validated")


if __name__ == "__main__":
    main()
