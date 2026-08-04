#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MUDCLIENT_PATH = ROOT / "Client_Base" / "src" / "orsc" / "mudclient.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def main() -> None:
    client = MUDCLIENT_PATH.read_text(encoding="utf-8")
    required = (
        "sortDisplayedSkillsByName(displayedSkills);",
        "private void sortDisplayedSkillsByName(int[] displayedSkills)",
        "compareToIgnoreCase(skillName) > 0",
        "return displayedSkills.length / 2;",
        'addSkill("Enchanting", "Enchant");',
        'addSkill("Blessing");',
        'this.getSurface().drawString("Quest Points:@yel@" + this.questPoints',
    )
    for snippet in required:
        if snippet not in client:
            fail(f"stats menu layout missing expected snippet: {snippet}")
    if '"Crafting".equalsIgnoreCase(skillNameLong[displayedSkills[displayIndex]])' in client:
        fail("stats menu still splits columns around Crafting instead of alphabetic halves")
    print("PASS: stats menu skill layout is alphabetized with quest points separate")


if __name__ == "__main__":
    main()
