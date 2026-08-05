#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/summoning-plan.md"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def parse_int_matrix(source: str, declaration: str) -> list[list[int]]:
    start = source.index(declaration)
    block_start = source.index("{", start)
    depth = 0
    block_end = -1
    for index in range(block_start, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                block_end = index
                break
    if block_end == -1:
        fail(f"Could not parse {declaration}")
    block = source[block_start + 1:block_end]
    rows = []
    for row in re.findall(r"\{([^{}]*)\}", block):
        rows.append([int(value.strip()) for value in row.split(",") if value.strip()])
    return rows


def main() -> int:
    summoning = SUMMONING.read_text(encoding="utf-8")
    guide = GUIDE.read_text(encoding="utf-8")
    client = CLIENT.read_text(encoding="utf-8")
    plan = PLAN.read_text(encoding="utf-8")
    client_cost_item_ids = parse_int_matrix(client, "private static final int[][] SUMMONING_COST_ITEM_IDS")
    client_cost_amounts = parse_int_matrix(client, "private static final int[][] SUMMONING_COST_AMOUNTS")

    match = re.search(
        r"private static final SummonProfile BEAR_PROFILE = combatProfile\((?P<body>.*?)\n\t\);",
        summoning,
        re.S,
    )
    if not match:
        fail("Ironhide Bear summon profile not found")

    bear_profile = match.group("body")
    if "cost(ItemId.BODY_RUNE.id(), 2)" not in bear_profile:
        fail("Ironhide Bear should cost 2 body runes")
    if "cost(ItemId.NATURE_RUNE.id()" in bear_profile:
        fail("Ironhide Bear should not cost a nature rune")
    if "Ironhide Bear - Combat; 1 life, 2 body, bones" not in guide:
        fail("Summoning skill guide should show the updated Ironhide Bear cost")
    if "{37, 36, 20}" not in client or "{1, 2, 1}" not in client:
        fail("Summoning tooltip should show Ironhide Bear as 1 life rune, 2 body runes, and bones")

    loot_match = re.search(
        r"private static final SummonProfile LOOT_GOBLIN_PROFILE = supportProfile\((?P<body>.*?)\n\t\);",
        summoning,
        re.S,
    )
    if not loot_match:
        fail("Loot Goblin summon profile not found")
    loot_profile = loot_match.group("body")
    for expected in (
        '"Loot Goblin", 12',
        "NpcId.LOOT_GOBLIN.id()",
        "cost(ItemId.BONES.id(), 1)",
        "cost(ItemId.LIFE_RUNE.id(), 1)",
        "cost(ItemId.BODY_RUNE.id(), 1)",
        "cost(ItemId.MIND_RUNE.id(), 1)",
    ):
        if expected not in loot_profile:
            fail(f"Loot Goblin profile is missing {expected}")
    if "Loot Goblin - Support; bones, life, body, mind" not in guide:
        fail("Summoning skill guide should show Loot Goblin cost")
    if client_cost_item_ids[2] != [20, 37, 36, 35] or client_cost_amounts[2] != [1, 1, 1, 1]:
        fail("Loot Goblin dropdown cost icons should be bones, life, body, mind")

    rat_match = re.search(
        r"private static final SummonProfile RAT_PROFILE = utilityProfile\((?P<body>.*?)\n\t\);",
        summoning,
        re.S,
    )
    if not rat_match:
        fail("Pack Rat summon profile not found")
    rat_profile = rat_match.group("body")
    if "cost(ItemId.BONES.id()" in rat_profile:
        fail("Pack Rat should not cost bones")
    if "Pack Rat - Utility; 1 life, 2 law, body, nature" not in guide:
        fail("Summoning skill guide should show Pack Rat without bones")
    if "Pack Rat - Utility; 1 life, 2 law, body, nature, bones" in guide:
        fail("Summoning skill guide should not show Pack Rat bones")
    if "{37, 42, 36, 40}" not in client or "{1, 2, 1, 1}" not in client:
        fail("Summoning tooltip should show Pack Rat without bones")
    if client_cost_item_ids[6] != [37, 42, 36, 40] or client_cost_amounts[6] != [1, 2, 1, 1]:
        fail("Pack Rat dropdown cost icons should be life, law, body, nature only")

    camel_match = re.search(
        r"private static final SummonProfile CAMEL_PROFILE = utilityProfile\((?P<body>.*?)\n\t\);",
        summoning,
        re.S,
    )
    if not camel_match:
        fail("Delivery Camel summon profile not found")
    camel_profile = camel_match.group("body")
    if "cost(ItemId.BONES.id()" in camel_profile:
        fail("Delivery Camel should not cost bones")
    if "Delivery Camel - Utility; 1 life, 2 body, 2 law, 2 nature" not in guide:
        fail("Summoning skill guide should show Delivery Camel without bones")
    if "Delivery Camel - Utility; 1 life, 2 body, 2 law, 2 nature, bones" in guide:
        fail("Summoning skill guide should not show Delivery Camel bones")
    if "{37, 36, 42, 40}" not in client or "{1, 2, 2, 2}" not in client:
        fail("Summoning tooltip should show Delivery Camel without bones")
    if client_cost_item_ids[11] != [37, 36, 42, 40] or client_cost_amounts[11] != [1, 2, 2, 2]:
        fail("Delivery Camel dropdown cost icons should be life, body, law, nature only")

    foundry_match = re.search(
        r"private static final SummonProfile FOUNDRY_DRAGON_PROFILE = supportProfile\((?P<body>.*?)\n\t\);",
        summoning,
        re.S,
    )
    if not foundry_match:
        fail("Foundry Dragon summon profile not found")
    foundry_profile = foundry_match.group("body")
    for expected in (
        '"Foundry Dragon", 61, 365',
        "cost(ItemId.LIFE_RUNE.id(), 2)",
        "cost(ItemId.FIRE_RUNE.id(), 5)",
        "cost(ItemId.NATURE_RUNE.id(), 1)",
        "cost(ItemId.DRAGON_BONES.id(), 1)",
    ):
        if expected not in foundry_profile:
            fail(f"Foundry Dragon profile is missing {expected}")
    if client_cost_item_ids[12] != [37, 31, 40, 814] or client_cost_amounts[12] != [2, 5, 1, 1]:
        fail("Foundry Dragon dropdown cost icons should be life, fire, nature, dragon bones")

    upkeep_checks = (
        "SUPPORT_UPKEEP_BASE_COST = 1",
        "SUPPORT_UPKEEP_INCREASE_MS = 3 * SUPPORT_UPKEEP_MS",
        "SUPPORT_UPKEEP_RECOVERY_MS = SUPPORT_UPKEEP_MS",
        "updateSupportUpkeepEscalation(owner)",
        "consumeLifeRunes(owner, upkeepCost)",
        "startSupportUpkeepRecovery(owner)",
        "SUPPORT_UPKEEP_RECOVERY_STARTED_KEY",
        "SUPPORT_UPKEEP_NEXT_INCREASE_KEY",
        "applySupportUpkeepRecovery(owner)",
        "Your support summon upkeep increases to ",
        "Your support summon upkeep decreases to ",
    )
    for check in upkeep_checks:
        if check not in summoning:
            fail(f"Support summon upkeep escalation is missing: {check}")
    if "getPotentialSummonCostAmount" in summoning:
        fail("Summoning availability checks should not waive rune requirements before consumption")
    has_costs_start = summoning.index("private static boolean hasSummonCosts")
    has_costs_end = summoning.index("private static void awardSummoningExperience")
    has_costs_body = summoning[has_costs_start:has_costs_end]
    if "final int requiredAmount = entry.getValue();" not in has_costs_body:
        fail("Summoning should require the full recipe cost before rune preservation is rolled")
    upkeep_start = summoning.index("private static boolean consumeLifeRunes")
    upkeep_end = summoning.index("private static boolean hasBlackUnicorn")
    upkeep_body = summoning[upkeep_start:upkeep_end]
    if "countId(ItemId.LIFE_RUNE.id(), Optional.of(false)) < amount" not in upkeep_body:
        fail("Support upkeep should require life runes before preservation is rolled")
    if upkeep_body.index("countId(ItemId.LIFE_RUNE.id(), Optional.of(false)) < amount") > upkeep_body.index("shouldPreserveRuneCost(owner, ItemId.LIFE_RUNE.id())"):
        fail("Support upkeep must check life rune availability before preservation")
    if "Support upkeep rises every 3 minutes active" not in guide:
        fail("Summoning skill guide should explain support upkeep escalation")
    if "Upkeep recovers 1 step per minute inactive" not in guide:
        fail("Summoning skill guide should explain support upkeep recovery")
    if "upkeep rises by `1 Life rune` every `3 minutes` active" not in plan:
        fail("Summoning plan should document support upkeep escalation")
    if "recovers by `1 Life rune` for each `1 minute`" not in plan:
        fail("Summoning plan should document support upkeep recovery")

    plan_start = plan.index("### Ironhide Bear")
    plan_end = plan.index("### Sacred Unicorn")
    bear_plan = plan[plan_start:plan_end]
    if "`2 Body runes`" not in bear_plan:
        fail("Summoning plan should document 2 body runes for Ironhide Bear")
    if "`1 Nature rune`" in bear_plan:
        fail("Summoning plan should not document a nature rune for Ironhide Bear")

    rat_plan = plan[plan.index("### Pack Rat"):plan.index("### Bound Battleaxe")]
    if "`1 bones`" in rat_plan:
        fail("Summoning plan should not document bones for Pack Rat")

    camel_plan = plan[plan.index("### Delivery Camel"):plan.index("### Foundry Dragon")]
    if "`1 bones`" in camel_plan:
        fail("Summoning plan should not document bones for Delivery Camel")

    foundry_plan = plan[plan.index("### Foundry Dragon"):plan.index("### Astral Wraith")]
    for expected in ("`2 Life runes`", "`5 Fire runes`", "`1 Nature rune`", "`1 Dragon bones`"):
        if expected not in foundry_plan:
            fail(f"Summoning plan should document Foundry Dragon cost {expected}")

    print("PASS: summoning costs validated")
    return 0


if __name__ == "__main__":
    sys.exit(main())
