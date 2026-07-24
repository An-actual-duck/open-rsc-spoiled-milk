#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PRAYER_PLUGIN_DIR = ROOT / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/prayer"
LIMITER = PRAYER_PLUGIN_DIR / "PrayerBlessingLimit.java"
TRANSACTION = PRAYER_PLUGIN_DIR / "PrayerBlessingTransaction.java"
BLESSING_PLUGINS = [
    PRAYER_PLUGIN_DIR / "BlessedStaffs.java",
    PRAYER_PLUGIN_DIR / "BlessedWoolArmor.java",
    PRAYER_PLUGIN_DIR / "BlessedSymbols.java",
    PRAYER_PLUGIN_DIR / "GodKnightEquipment.java",
]
DESTROY_PLUGIN = PRAYER_PLUGIN_DIR / "DestroyOpposingBlessedObject.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def main() -> None:
    limiter = LIMITER.read_text(encoding="utf-8")
    transaction = TRANSACTION.read_text(encoding="utf-8")
    require("BLESSINGS_PER_HOUR = 10" in limiter, "prayer blessing cap should be 10 per hour")
    require("60L * 60L * 1000L" in limiter, "prayer blessing window should be one hour")
    require('"You hear a low rumbling voice..."' in limiter, "limit should send the rumbling voice intro")
    require('"You must learn Patience"' in limiter, "Saradomin limit message missing")
    require('"That is quite enough for now"' in limiter, "Guthix limit message missing")
    require('"Leave. Me. ALONE!"' in limiter, "Zamorak limit message missing")
    require("myworld_prayer_blessing_window_start" in limiter, "limit should persist the window start in player cache")
    require("myworld_prayer_blessing_window_count" in limiter, "limit should persist the blessing count in player cache")
    require("count >= BLESSINGS_PER_HOUR" in limiter, "limit should reject counts at the cap")
    require("Math.min(BLESSINGS_PER_HOUR, count + 1)" in limiter, "recording should not exceed the cap")
    require("completeSuccessfulBlessing" in limiter, "limit should expose one atomic successful-conversion operation")
    require("synchronized (player)" in limiter, "limit check and record should share the player monitor")
    require("BooleanSupplier conversion" in limiter, "limit should execute the conversion within its critical section")
    conversion_index = limiter.index("conversion.getAsBoolean()")
    counter_index = limiter.index("Math.min(BLESSINGS_PER_HOUR, count + 1)")
    require(conversion_index < counter_index, "failed conversions must not increment the hourly count")

    require("synchronized (player)" in transaction, "ordinary blessing transaction should be serialized per player")
    require("player.getPrayerBook() != godLine" in transaction, "blessing should require altar/worship alignment")
    require("replaceExact(source, new Item(productId), true)" in transaction,
            "blessing should replace only the exact selected inventory instance")
    require("Devotion.adjustDevotionOfferings(player, godLine, -devotionOfferingCost)" in transaction,
            "successful equipment blessings should spend fractional offering units")
    require("PrayerBlessingLimit.completeSuccessfulBlessing(" in transaction,
            "conversion and hourly accounting should use one atomic limiter operation")

    for plugin_path in BLESSING_PLUGINS:
        plugin = plugin_path.read_text(encoding="utf-8")
        require(
            "PrayerBlessingTransaction.bless(" in plugin,
            f"{plugin_path.name} should use the shared atomic blessing path",
        )
        require("PrayerBlessingLimit.canBless" not in plugin,
                f"{plugin_path.name} should not split the hourly availability check")
        require("PrayerBlessingLimit.recordBlessing" not in plugin,
                f"{plugin_path.name} should not record outside the transaction")

    destroy = DESTROY_PLUGIN.read_text(encoding="utf-8")
    require("PrayerBlessingLimit" not in destroy, "destroying opposing blessed objects should not spend blessing slots")

    print("PASS: prayer blessing hourly limit validated")


if __name__ == "__main__":
    main()
