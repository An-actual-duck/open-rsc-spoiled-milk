#!/usr/bin/env python3
"""Characterize the runtime facts used by the Devotion balance audit."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PRAYER_PLUGINS = (
    ROOT
    / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/prayer"
)
AUDIT = (
    ROOT
    / "docs/myworld/in-progress-work-plans/devotion-destruction-blessing-audit.md"
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def simulate_offerings(target: int, mode: str) -> tuple[int, int, int]:
    """Return interactions, offering units, and current flat bonus XP."""
    offerings = 0
    interactions = 0
    bonus_xp = 0
    symbol_toggle = False
    unicorn_toggle = False

    while offerings // 10 < target:
        devotion = offerings // 10
        bonus_xp += devotion
        gain = 1

        if mode in {"symbol", "both"} and devotion >= 25:
            if not symbol_toggle:
                gain += 1
            symbol_toggle = not symbol_toggle

        if mode in {"unicorn", "both"}:
            if not unicorn_toggle:
                gain += 1
            unicorn_toggle = not unicorn_toggle

        offerings = min(10_000, offerings + gain)
        interactions += 1

    return interactions, offerings, bonus_xp


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

    for marker in (
        "OFFERINGS_PER_DEVOTION_LEVEL = OFFERINGS_PER_BONUS_XP",
        "MAX_DEVOTION_LEVEL = 1000",
        "MIN_DEVOTION_LEVEL = -1000",
        "return bonusXp * 4;",
        "100.0D + devotionLevel",
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

    remove_index = bones.index("RuneScript.remove(bones.getCatalogId(), 1);")
    reward_index = bones.index("giveBonesExperience(player, bones, true);")
    require(
        remove_index < reward_index
        and "if (RuneScript.remove" not in bones,
        "Bonecrusher removal semantics changed; refresh the audit",
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

    for omitted_id in (432, 2161, 3123, 3229, 3230, 3231, 3232, 3233, 3234):
        require(
            f"case {omitted_id}:" not in destroy,
            f"destruction coverage changed for documented gap {omitted_id}",
        )

    expected_progression = {
        (25, "plain"): (250, 250, 3_000),
        (500, "plain"): (5_000, 5_000, 1_247_500),
        (1000, "plain"): (10_000, 10_000, 4_995_000),
        (500, "symbol"): (3_417, 5_001, 832_833),
        (1000, "symbol"): (6_750, 10_000, 3_331_000),
        (500, "both"): (2_542, 5_001, 624_258),
        (1000, "both"): (5_042, 10_000, 2_498_008),
    }
    for key, expected in expected_progression.items():
        require(
            simulate_offerings(*key) == expected,
            f"offering progression changed for target/mode {key}",
        )

    for statement in (
        "No gameplay values or runtime behavior were changed by this audit.",
        "10 successful blessings / one fixed one-hour window",
        "The matrix contains 96 ordinary blessing results",
        "God square shields, spears, and scythes",
        "Bonecrusher does not check removal",
    ):
        require(statement in audit, f"audit is missing required finding: {statement}")

    print("PASS: Devotion/blessing/destruction audit facts validated")


if __name__ == "__main__":
    main()
