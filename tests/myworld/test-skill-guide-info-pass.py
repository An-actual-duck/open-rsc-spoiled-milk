#!/usr/bin/env python3
"""Validate the second MyWorld skill-guide info pass."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
WORK_ITEMS = ROOT / "docs/myworld/in-progress-work-plans/work-items.md"


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        fail(f"{label} missing expected snippet: {snippet}")


def main() -> None:
    guide = GUIDE.read_text(encoding="utf-8")
    client = CLIENT.read_text(encoding="utf-8")
    work_items = WORK_ITEMS.read_text(encoding="utf-8")

    for snippet in (
        'skillGuideChosenTabs.add("Info");',
        '"Combat summons stay until death or dismissal"',
        '"Support upkeep rises every 3 minutes active"',
        '"Upkeep recovers 1 step per minute inactive"',
        '"Utility uses are spent only on successful tasks"',
        '"Hoarder - 1 base use; certs matching selected items"',
        '"Beast of Burden - 1 base use; banks one stack"',
        '"Utility Bangels add +1, +2, +3, +5, or +8 uses"',
        '"Summoning has a 5 second interruptible charge"',
        "isSummoningInfoTab()",
        "isExpositoryTab() && !isSummoningInfoTab() ? x + 10 : detailX + 10",
        "int headerX = isSummoningInfoTab() ? x + 85 : x + 5",
    ):
        require(client + guide, snippet, "Summoning guide info")

    for snippet in (
        '"Each cloth piece preserves 10% matching rune"',
        '"Matching enchanted staff preserves 50%"',
        '"Matching cloth and staff can preserve 100%"',
        '"Jewelry requires both gem and altar levels"',
        '"Examine jewelry for details on their effects"',
    ):
        require(guide, snippet, "Enchanting guide info")
    for retired_snippet in (
        '"Rings and necklaces can stack matching effects"',
        '"Law jewelry spends charges to bank items"',
        '"Soul jewelry protects death losses"',
    ):
        if retired_snippet in guide:
            fail(f"Enchanting guide info should not list partial jewelry effects: {retired_snippet}")

    for snippet in (
        '"Leather armor has set effects if all five pieces are worn"',
        '"Leather armor mimics the defenses of its creature"',
        '"Hides and carapaces must be tanned at tanning racks"',
        '"Examine armor pieces to read their trait"',
        '"Set traits can grant stats, procs, or spirits"',
        '"Arrowheads moved to Crafting and use molds"',
        '"Bolts moved to Crafting and use molds"',
        '"Dart tips moved to Crafting and use molds"',
        '"Throwing knives moved to Crafting and use molds"',
        '"Crafting Guild can be entered at level 40 with a brown apron"',
    ):
        require(guide, snippet, "Crafting guide info")

    for snippet in (
        '"Seed focus: No seeds, A few, More, or Even more"',
        '"Seeds can roll up to hatchet tier plus 2"',
        '"Seeds can roll up to shears tier plus 2"',
        '"Above-tier seeds roll at reduced weight"',
        '"Knowledge and Money seeds scale to your level"',
        '"Geode focus: Just the ore, A few, Plenty, Lots"',
        '"Higher geode focus increases rare geode rolls"',
        '"Side loot can roll up to rod tier plus 2"',
        '"Above-tier side loot rolls at reduced weight"',
        '"Fishing Guild can be entered at level 68"',
        '"Mining Guild can be entered at level 60"',
        '"Woodcutting Guild can be entered at level 55 after paying 1000 coins"',
    ):
        require(guide, snippet, "Gathering guide info")

    for snippet in (
        '"Magic Guild can be entered at level 66"',
        '"Cooking Guild can be entered at level 32 with a chef\'s hat"',
    ):
        require(guide, snippet, "Skill guild guide info")

    require(
        work_items,
        "The second in-game skill-guide Info pass is in place",
        "Work items implemented state",
    )
    print("PASS: skill-guide info pass validated")


if __name__ == "__main__":
    main()
