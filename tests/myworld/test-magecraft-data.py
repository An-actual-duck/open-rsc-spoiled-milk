#!/usr/bin/env python3
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ITEMS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefs.json"
CUSTOM_ITEMS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsCustom.json"
MYWORLD_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsMyWorld.json"
EFFECTS_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "content" / "EnchantingItemEffects.java"
SPELL_HANDLER_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "handlers" / "SpellHandler.java"
MAGE_ARENA_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "minigames" / "mage_arena" / "MageArena.java"
MOB_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "Mob.java"
BURN_EVENT_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "BurnEvent.java"
WATER_SLOW_EVENT_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "WaterSlowEvent.java"
ELEMENTAL_DEBUFF_EVENT_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "ElementalDebuffEvent.java"
COMBAT_EVENT_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "combat" / "CombatEvent.java"
PVM_MELEE_EVENT_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "combat" / "PvmMeleeEvent.java"
PROJECTILE_EVENT_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "ProjectileEvent.java"
RANGE_UTILS_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "RangeUtils.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_item_map(path: Path) -> dict[int, dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(data, dict):
        if "items" in data:
            entries = data["items"]
        elif "item" in data:
            entries = data["item"]
        else:
            fail(f"Unsupported item wrapper in {path}")
    else:
        entries = data
    return {entry["id"]: entry for entry in entries}


def load_all_items() -> dict[int, dict]:
    items = load_item_map(ITEMS_PATH)
    items.update(load_item_map(CUSTOM_ITEMS_PATH))
    return items


def load_myworld_overrides() -> dict[int, dict]:
    data = json.loads(MYWORLD_PATH.read_text(encoding="utf-8"))
    return {entry["id"]: entry for entry in data["items"]}


def expect_name(items: dict[int, dict], item_id: int, expected_name: str) -> None:
    entry = items.get(item_id)
    if entry is None:
        fail(f"Missing item id {item_id}")
    if entry["name"] != expected_name:
        fail(f"Item {item_id} expected {expected_name!r} but found {entry['name']!r}")


def ensure_staff_line(items: dict[int, dict]) -> None:
    expected_names = {
        100: "staff",
        2131: "Pine Staff",
        1764: "Oak Staff",
        1769: "Willow Staff",
        2136: "Palm Staff",
        1774: "Maple Staff",
        1779: "Yew Staff",
        2141: "Ebony Staff",
        1784: "Magic Staff",
        2146: "Blood Staff",
        101: "Air Staff",
        102: "Water Staff",
        103: "Earth Staff",
        197: "Fire Staff",
        2132: "Air Pine Staff",
        2133: "Water Pine Staff",
        2134: "Earth Pine Staff",
        2135: "Fire Pine Staff",
        1765: "Air Oak Staff",
        1766: "Water Oak Staff",
        1767: "Earth Oak Staff",
        1768: "Fire Oak Staff",
        1770: "Air Willow Staff",
        1771: "Water Willow Staff",
        1772: "Earth Willow Staff",
        1773: "Fire Willow Staff",
        2137: "Air Palm Staff",
        2138: "Water Palm Staff",
        2139: "Earth Palm Staff",
        2140: "Fire Palm Staff",
        1775: "Air Maple Staff",
        1776: "Water Maple Staff",
        1777: "Earth Maple Staff",
        1778: "Fire Maple Staff",
        1780: "Air Yew Staff",
        1781: "Water Yew Staff",
        1782: "Earth Yew Staff",
        1783: "Fire Yew Staff",
        2142: "Air Ebony Staff",
        2143: "Water Ebony Staff",
        2144: "Earth Ebony Staff",
        2145: "Fire Ebony Staff",
        1785: "Air Magic Staff",
        1786: "Water Magic Staff",
        1787: "Earth Magic Staff",
        1788: "Fire Magic Staff",
        2147: "Air Blood Staff",
        2148: "Water Blood Staff",
        2149: "Earth Blood Staff",
        2150: "Fire Blood Staff",
    }
    tier_labels = ["", "Pine", "Oak", "Willow", "Palm", "Maple", "Yew", "Ebony", "Magic", "Blood"]
    line_starts = {
        "Mind": 2238,
        "Body": 2248,
        "Cosmic": 2258,
        "Chaos": 2268,
        "Nature": 2278,
        "Law": 2288,
        "Death": 2298,
        "Blood Rune": 2308,
        "Soul": 2318,
    }
    for rune_name, start_id in line_starts.items():
        for index, material in enumerate(tier_labels):
            label = f"{rune_name} Staff" if not material else f"{rune_name} {material} Staff"
            expected_names[start_id + index] = label
    for item_id, name in expected_names.items():
        expect_name(items, item_id, name)


def ensure_staff_stats(items: dict[int, dict], overrides: dict[int, dict]) -> None:
    base_ids = [100, 2131, 1764, 1769, 2136, 1774, 1779, 2141, 1784, 2146]
    attuned_ids_by_base = {
        100: [101, 2238, 102, 103, 197, 2248, 2258, 2268, 2278, 2288, 2298, 2308, 2318],
        2131: [2132, 2239, 2133, 2134, 2135, 2249, 2259, 2269, 2279, 2289, 2299, 2309, 2319],
        1764: [1765, 2240, 1766, 1767, 1768, 2250, 2260, 2270, 2280, 2290, 2300, 2310, 2320],
        1769: [1770, 2241, 1771, 1772, 1773, 2251, 2261, 2271, 2281, 2291, 2301, 2311, 2321],
        2136: [2137, 2242, 2138, 2139, 2140, 2252, 2262, 2272, 2282, 2292, 2302, 2312, 2322],
        1774: [1775, 2243, 1776, 1777, 1778, 2253, 2263, 2273, 2283, 2293, 2303, 2313, 2323],
        1779: [1780, 2244, 1781, 1782, 1783, 2254, 2264, 2274, 2284, 2294, 2304, 2314, 2324],
        2141: [2142, 2245, 2143, 2144, 2145, 2255, 2265, 2275, 2285, 2295, 2305, 2315, 2325],
        1784: [1785, 2246, 1786, 1787, 1788, 2256, 2266, 2276, 2286, 2296, 2306, 2316, 2326],
        2146: [2147, 2247, 2148, 2149, 2150, 2257, 2267, 2277, 2287, 2297, 2307, 2317, 2327],
    }
    expected_offense = [8, 12, 16, 24, 28, 32, 40, 44, 48, 56]
    expected_levels = [1, 8, 15, 22, 30, 38, 46, 54, 62, 70]

    for index, base_id in enumerate(base_ids):
        override = overrides.get(base_id)
        if override is None:
            fail(f"Missing MyWorld override for staff {base_id}")
        if override.get("magicOffense") != expected_offense[index]:
            fail(f"Staff {base_id} has wrong magicOffense")
        if override.get("requiredSkillID") != 6 or override.get("requiredLevel") != expected_levels[index]:
            fail(f"Staff {base_id} has wrong wield requirement override")
        base_item = items[base_id]
        if base_item["wearSlot"] != 4:
            fail(f"Staff {base_id} should use mainhand slot")
        for attuned_id in attuned_ids_by_base.get(base_id, []):
            attuned_override = overrides.get(attuned_id)
            if attuned_override is None:
                fail(f"Missing MyWorld override for attuned staff {attuned_id}")
            if attuned_override.get("magicOffense") != expected_offense[index]:
                fail(f"Attuned staff {attuned_id} should match base offense for tier {index + 1}")
            if attuned_override.get("requiredSkillID") != 6 or attuned_override.get("requiredLevel") != expected_levels[index]:
                fail(f"Attuned staff {attuned_id} has wrong wield requirement override")


def ensure_retired_battlestaff_holdovers(items: dict[int, dict], overrides: dict[int, dict]) -> None:
    regular_ids = [614, 615, 616, 617, 618]
    enchanted_ids = [682, 683, 684, 685]

    for item_id in regular_ids:
        if overrides.get(item_id, {}).get("magicOffense") != 4 + (1 if item_id != 614 else 0):
            fail(f"Retired battlestaff holdover {item_id} has drifted from its low legacy magicOffense lane")
    for item_id in enchanted_ids:
        if overrides.get(item_id, {}).get("magicOffense") != 6:
            fail(f"Retired enchanted battlestaff holdover {item_id} should stay capped at legacy magicOffense 6")
    for item_id in regular_ids + enchanted_ids:
        if items[item_id]["name"] != "Retired item":
            fail(f"Retired battlestaff holdover {item_id} should use inert item naming")


def ensure_special_staff_notes(items: dict[int, dict], overrides: dict[int, dict]) -> None:
    if overrides.get(509, {}).get("magicOffense") != 16:
        fail("Dramen Staff should currently mirror tier-3 utility magicOffense")
    if overrides.get(198, {}).get("magicOffense") != 28:
        fail("Legacy quest Magic Staff should currently mirror tier-5 utility magicOffense")
    if overrides.get(198, {}).get("name") != "Wizard staff":
        fail("Legacy utility Magic Staff should now be renamed to Wizard staff in MyWorld")
    if items[1784]["name"] != "Magic Staff":
        fail("Tier-9 wood-line Magic Staff naming unexpectedly changed")


def ensure_source_support(items: dict[int, dict]) -> None:
    effects_text = EFFECTS_PATH.read_text(encoding="utf-8")
    spell_text = SPELL_HANDLER_PATH.read_text(encoding="utf-8")
    mage_arena_text = MAGE_ARENA_PATH.read_text(encoding="utf-8")
    mob_text = MOB_PATH.read_text(encoding="utf-8")
    burn_event_text = BURN_EVENT_PATH.read_text(encoding="utf-8")
    water_slow_event_text = WATER_SLOW_EVENT_PATH.read_text(encoding="utf-8")
    elemental_debuff_event_text = ELEMENTAL_DEBUFF_EVENT_PATH.read_text(encoding="utf-8")
    combat_event_text = COMBAT_EVENT_PATH.read_text(encoding="utf-8")
    pvm_melee_event_text = PVM_MELEE_EVENT_PATH.read_text(encoding="utf-8")
    projectile_event_text = PROJECTILE_EVENT_PATH.read_text(encoding="utf-8")
    range_utils_text = RANGE_UTILS_PATH.read_text(encoding="utf-8")
    for snippet in (
        "private static final int[] BASE_STAFFS = {",
        "private static final int[][] ELEMENTAL_STAFFS = {",
        "private static final int[] MIND_STAFFS = {",
        "private static final TieredLine[] STANDARD_STAFF_LINES = {",
        "public static int getStaffProduct",
        "public static int getStaffRuneCost(final int tier)",
        "public static boolean isStaffForRune",
        "public static double getStaffRunePreservationChance",
    ):
        if snippet not in effects_text:
            fail(f"EnchantingItemEffects.java missing expected snippet: {snippet}")
    for snippet in (
        "getRuneNegationChance",
        "EnchantingItemEffects.getStaffRunePreservationChance",
        "Orb charging has been retired. Use a staff on the matching altar through Enchanting instead.",
        "getWindAccuracyDebuffPercent",
        "getWaterMaxHitDebuffPercent",
        "getEarthAttackSpeedDebuffPercent",
        "getFireDefenseDebuffPercent",
        "getSplinterProcChancePercent",
        "new ProjectileEvent(getPlayer().getWorld(), getPlayer(), affectedMob, damage, 1, setChasing,\n\t\t\t\t\t\t\twindAccuracyDebuffPercent, waterMaxHitDebuffPercent, earthAttackSpeedDebuffPercent, fireDefenseDebuffPercent,\n\t\t\t\t\t\t\tprojectileVisual, impactEffect, impactEffect <= 0,\n\t\t\t\t\t\t\tstartleProcChancePercent, acidPoisonPower, frostbiteProcChancePercent, splinterProcChancePercent)",
    ):
        if snippet not in spell_text:
            fail(f"SpellHandler.java missing expected snippet: {snippet}")
    for snippet in (
        "BATTLESTAFF_OF_FIRE",
        "BATTLESTAFF_OF_WATER",
        "BATTLESTAFF_OF_AIR",
        "BATTLESTAFF_OF_EARTH",
        "ENCHANTED_BATTLESTAFF_OF_FIRE",
        "ENCHANTED_BATTLESTAFF_OF_WATER",
        "ENCHANTED_BATTLESTAFF_OF_AIR",
        "ENCHANTED_BATTLESTAFF_OF_EARTH",
        "You succesfully charge the orb",
    ):
        if snippet in spell_text:
            fail(f"SpellHandler.java still references retired legacy path: {snippet}")
    for old_name in ("Staff of Air", "Staff of water", "Staff of earth", "Staff of fire"):
        if old_name in (items[item_id]["name"] for item_id in (101, 102, 103, 197)):
            fail(f"Standard staff enchantment should use rune-first naming, not {old_name!r}")
    for snippet in (
        "ItemId.STAFF_OF_AIR.id()",
        "ItemId.STAFF_OF_WATER.id()",
        "ItemId.STAFF_OF_EARTH.id()",
        "ItemId.STAFF_OF_FIRE.id()",
    ):
        if snippet in mage_arena_text:
            fail(f"MageArena.java should not explicitly allow standard elemental staff: {snippet}")
    if "EnchantingItemEffects.isEnchantedStaff(item.getCatalogId())" not in mage_arena_text:
        fail("MageArena.java should allow the current altar-attuned staff line")
    for snippet in (
        "public void curePoison()",
        "public void extinguish()",
        "public void applyBurn(int burnDamage, int burnPulses)",
        "public void startBurnEvent()",
        "public void applyWindDebuff(int percent)",
        "public void applyWaterMaxHitDebuff(int percent)",
        "public void applyEarthAttackSpeedDebuff(int percent)",
        "public void applyFireDefenseDebuff(int percent)",
        "public double getWindLowRollBiasChance()",
        "public double getWaterMaxHitMultiplier()",
        "public int getEarthAttackSpeedDebuffPercent()",
        "public int getFireDefenseDebuffPercent()",
        "public void clearWaterSlow()",
        "public void applyWaterSlow(int percent)",
        "public int getWaterSlowPercent()",
    ):
        if snippet not in mob_text:
            fail(f"Mob.java missing expected snippet: {snippet}")
    for snippet in (
        "class ElementalDebuffEvent",
        "enum DebuffType",
        "EARTH",
        "Mob.ELEMENTAL_DEBUFF_DURATION_TICKS",
        "mob.clearWindDebuff()",
        "mob.clearWaterMaxHitDebuff()",
        "mob.clearEarthAttackSpeedDebuff()",
        "mob.clearFireDefenseDebuff()",
        "Slow fades.",
    ):
        if snippet not in elemental_debuff_event_text:
            fail(f"ElementalDebuffEvent.java missing expected snippet: {snippet}")
    for snippet in (
        "class BurnEvent",
        "super(world, owner, 8, \"Burn Event\"",
        "You are burning! You lose",
        "player.getCache().set(\"burn_damage\", burnDamage);",
        "player.getCache().set(\"burn_pulses\", pulsesRemaining);",
    ):
        if snippet not in burn_event_text:
            fail(f"BurnEvent.java missing expected snippet: {snippet}")
    for snippet in (
        "class WaterSlowEvent",
        "super(world, owner, 24, \"Water Slow Event\"",
        "The slowing water magic wears off.",
    ):
        if snippet not in water_slow_event_text:
            fail(f"WaterSlowEvent.java missing expected snippet: {snippet}")
    for snippet in (
        "getCombatSpeedMultiplier",
        "hitter.getEarthAttackSpeedDebuffPercent()",
    ):
        if snippet not in combat_event_text:
            fail(f"CombatEvent.java missing expected snippet: {snippet}")
    for snippet in (
        "splinterProcChancePercent",
        "applySplinter()",
        "applySplinterOnHitEffect();",
        "selectSplinterTarget",
        "npc != primaryTarget",
        "npc.getDef().isAttackable()",
        "primaryTarget.getLocation(), 2",
        "Math.ceil(damage / 2.0D)",
        "splinterTarget.addMageDamage(casterPlayer",
    ):
        if snippet not in projectile_event_text:
            fail(f"ProjectileEvent.java missing expected Wood Splinter snippet: {snippet}")
    retired_bind_text = "\n".join((spell_text, mob_text, combat_event_text, pvm_melee_event_text, projectile_event_text))
    for snippet in ("getBindProcChancePercent", "applyBindDebuff", "consumeBindDebuff", "applyBindReduction"):
        if snippet in retired_bind_text:
            fail(f"Wood Bind implementation should be retired in favor of Splinter: {snippet}")
    for snippet in (
        "getAdjustedRangeDelayTicks(final Mob attacker",
        "attacker.getEarthAttackSpeedDebuffPercent()",
    ):
        if snippet not in range_utils_text:
            fail(f"RangeUtils.java missing expected snippet: {snippet}")


def main() -> None:
    items = load_all_items()
    overrides = load_myworld_overrides()
    ensure_staff_line(items)
    ensure_staff_stats(items, overrides)
    ensure_retired_battlestaff_holdovers(items, overrides)
    ensure_special_staff_notes(items, overrides)
    ensure_source_support(items)
    print("PASS: Enchanting staff data validated")
    print("Standard staff tiers validated: 10")


if __name__ == "__main__":
    main()
