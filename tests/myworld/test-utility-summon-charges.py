#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/summoning-plan.md"
GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        fail(f"Missing {label}: {snippet}")


def between(text: str, start: str, end: str) -> str:
    try:
        return text[text.index(start):text.index(end, text.index(start) + len(start))]
    except ValueError:
        fail(f"Could not isolate source block from {start} to {end}")
        return ""


def main() -> None:
    summoning = SUMMONING.read_text(encoding="utf-8")
    plan = PLAN.read_text(encoding="utf-8")
    guide = GUIDE.read_text(encoding="utf-8")

    require(summoning, "PACK_RAT_UTILITY_USES = 1", "Pack Rat base service count")
    require(summoning, "DELIVERY_CAMEL_UTILITY_USES = 1", "Delivery Camel base service count")
    require(
        summoning,
        'SUMMON_UTILITY_USES_REMAINING_KEY = "myworld_summon_utility_uses_remaining"',
        "summon-lifecycle service counter",
    )
    require(
        summoning,
        '"Pack Rat", 33, 145, UTILITY_RAT_NPC_ID, KIND_RAT, PACK_RAT_UTILITY_USES,',
        "Pack Rat profile service count",
    )
    require(
        summoning,
        '"Delivery Camel", 58, 335, NpcId.CAMEL.id(), KIND_CAMEL, DELIVERY_CAMEL_UTILITY_USES,',
        "Delivery Camel profile service count",
    )

    utility_profile = between(summoning, "private static SummonProfile utilityProfile", "private static SummonProfile armorCombatProfile")
    require(utility_profile, "NO_DURATION_LIMIT, utilityUses", "charge-governed utility lifetime")
    spawn = between(summoning, "private static void spawnManualSummon", "private static int getSummonArrivalEffect")
    require(
        spawn,
        "profile.utilityUses + owner.getCarriedItems().getEquipment().getLifeBangleUtilityChargeBonus()",
        "Bangle-adjusted service initialization on the active summon",
    )

    rat_menu = between(summoning, "public static boolean handleRatMenuReply", "public static boolean handleRatItemSelection")
    require(rat_menu, "if (convertedAmount > 0)", "Pack Rat menu success guard")
    require(rat_menu, "completeUtilityService(player, rat);", "Pack Rat menu service consumption")
    if "dismissManualSummon" in rat_menu:
        fail("Pack Rat menu failures must not dismiss the summon")

    rat_item = between(summoning, "public static boolean handleRatItemSelection", "public static boolean handleCamelItemSelection")
    require(rat_item, "if (convertedAmount > 0)", "Pack Rat item success guard")
    require(rat_item, "completeUtilityService(player, rat);", "Pack Rat item service consumption")
    if "dismissManualSummon" in rat_item:
        fail("Pack Rat item failures must not dismiss the summon")

    camel_item = between(summoning, "public static boolean handleCamelItemSelection", "private static void spawnManualSummon")
    require(camel_item, "if (depositInventorySlotToBank(player, item))", "Delivery Camel success guard")
    require(camel_item, "completeUtilityService(player, camel);", "Delivery Camel service consumption")
    if "dismissManualSummon" in camel_item:
        fail("Delivery Camel failures must not dismiss the summon")

    completion = between(summoning, "private static void completeUtilityService", "private static void awardPackRatUtilityExperience")
    require(completion, "if (currentUses <= 0)", "duplicate-consumption guard")
    require(completion, "final int remainingUses = currentUses - 1;", "one-service decrement")
    require(completion, "remainingUses == 0 ? \" and disappears.\"", "final-service message")
    require(completion, "if (remainingUses == 0)", "final-service dismissal guard")
    require(completion, "finishManualSummon(owner, summon, true);", "identity-safe final dismissal")

    if "into certs and disappears" in summoning:
        fail("Pack Rat action message must not claim immediate dismissal")
    if '"The camel deposits your " + def.getName() + " and disappears."' in summoning:
        fail("Successful utility action messages must not claim immediate dismissal")

    cleanup = between(summoning, "private static void clearManualSummonState", "public static void dismissAll")
    for key in ("RAT_AWAITING_ITEM_KEY", "RAT_NPC_KEY", "CAMEL_AWAITING_ITEM_KEY", "CAMEL_NPC_KEY"):
        require(cleanup, f"removeAttribute({key})", f"stale selection cleanup for {key}")

    require(plan, "maximum with the Dragonstone Utility Command Bangle: `1,350` displayed", "Pack Rat maximum service XP")
    require(plan, "or `1,495` including the `145` cast XP", "Pack Rat total summon XP")
    require(plan, "maximum with the Dragonstone Utility Command Bangle: `2,025` displayed", "Delivery Camel maximum service XP")
    require(plan, "or `2,360` including the `335` cast XP", "Delivery Camel total summon XP")
    require(plan, "maximum XP remains a balance concern to\nfield-test", "utility XP balance warning")
    require(guide, "Hoarder - 1 base use; certs matching selected items", "Pack Rat base service count")
    require(guide, "Beast of Burden - 1 base use; banks one stack", "Delivery Camel base service count")
    require(guide, "Utility uses are spent only on successful tasks", "utility success-only guide rule")
    require(guide, "Utility Bangels add +1, +2, +3, +5, or +8 uses", "utility Bangle guide rule")
    if "Utility summons time out after 1 minute" in guide:
        fail("Utility guide must not advertise the retired timeout")

    print("PASS: utility summon charges consume only successful services and dismiss on the final use")


if __name__ == "__main__":
    main()
