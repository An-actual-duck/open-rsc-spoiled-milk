#!/usr/bin/env python3
"""Validate the second MyWorld skill-guide info pass."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
WORK_ITEMS = ROOT / "docs/myworld/work-items.md"


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
        '"Support summons consume life runes for upkeep"',
        '"Utility summons complete one task, then leave"',
        '"Summoning has a 5 second interruptible charge"',
    ):
        require(client + guide, snippet, "Summoning guide info")

    for snippet in (
        '"Each cloth piece preserves 10% matching rune"',
        '"Matching enchanted staff preserves 50%"',
        '"Matching cloth and staff can preserve 100%"',
        '"Jewelry requires both gem and altar levels"',
        '"Rings and necklaces can stack matching effects"',
        '"Law jewelry spends charges to bank items"',
        '"Soul jewelry protects death losses"',
    ):
        require(guide, snippet, "Enchanting guide info")

    for snippet in (
        '"Leather armor has set effects if all five pieces are worn"',
        '"Examine armor pieces to read their trait"',
        '"Set traits can grant stats, procs, or spirits"',
    ):
        require(guide, snippet, "Crafting guide info")

    for snippet in (
        '"Seed focus: No seeds, A few, More, or Even more"',
        '"Seeds can roll up to hatchet tier plus 2"',
        '"Seeds can roll up to shears tier plus 2"',
        '"Above-tier seeds roll at reduced weight"',
        '"Knowledge and Money seeds scale to your level"',
        '"Gem focus: Just the ore, A few, Plenty, Lots"',
        '"Higher gem focus increases rare gem rolls"',
        '"Side loot can roll up to rod tier plus 2"',
        '"Above-tier side loot rolls at reduced weight"',
    ):
        require(guide, snippet, "Gathering guide info")

    require(
        work_items,
        "The second in-game skill-guide Info pass is in place",
        "Work items implemented state",
    )
    print("PASS: skill-guide info pass validated")


if __name__ == "__main__":
    main()
