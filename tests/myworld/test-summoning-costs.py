#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
PLAN = ROOT / "docs/myworld/summoning-plan.md"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def main() -> int:
    summoning = SUMMONING.read_text(encoding="utf-8")
    guide = GUIDE.read_text(encoding="utf-8")
    client = CLIENT.read_text(encoding="utf-8")
    plan = PLAN.read_text(encoding="utf-8")

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

    plan_start = plan.index("### Ironhide Bear")
    plan_end = plan.index("### Sacred Unicorn")
    bear_plan = plan[plan_start:plan_end]
    if "`2 Body runes`" not in bear_plan:
        fail("Summoning plan should document 2 body runes for Ironhide Bear")
    if "`1 Nature rune`" in bear_plan:
        fail("Summoning plan should not document a nature rune for Ironhide Bear")

    print("PASS: summoning costs validated")
    return 0


if __name__ == "__main__":
    sys.exit(main())
