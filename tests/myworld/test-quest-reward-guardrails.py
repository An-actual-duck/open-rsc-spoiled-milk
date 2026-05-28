#!/usr/bin/env python3

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
QUEST_REWARD_REGISTRAR = ROOT / "server/plugins/com/openrsc/server/plugins/shared/QuestRewardRegistrar.java"
AUTHENTIC_QUESTS = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/quests"


RETIRED_REWARD_SKILLS = ("ATTACK", "STRENGTH", "DEFENSE", "FLETCHING")


def fail(message: str) -> None:
    raise AssertionError(message)


def find_forbidden_reward_skills(path: Path, pattern: str) -> list[str]:
    text = path.read_text(encoding="utf-8")
    failures: list[str] = []
    for skill in RETIRED_REWARD_SKILLS:
        if re.search(pattern.format(skill=skill), text):
            failures.append(f"{path.relative_to(ROOT)} still rewards Skill.{skill}")
    return failures


def main() -> None:
    failures: list[str] = []
    failures.extend(find_forbidden_reward_skills(
        QUEST_REWARD_REGISTRAR,
        r"new\s+XPReward\s*\(\s*Skill\.{skill}\b",
    ))

    for path in sorted(AUTHENTIC_QUESTS.rglob("*.java")):
        failures.extend(find_forbidden_reward_skills(
            path,
            r"(?:incExp|incStat)\s*\(\s*Skill\.{skill}\.id\(\)",
        ))

    shortcut_text = (ROOT / "server/plugins/com/openrsc/server/plugins/custom/quests/MyWorldQuestShortcuts.java").read_text(encoding="utf-8")
    for source, target in {
        "ATTACK": "MELEE",
        "STRENGTH": "MELEE",
        "DEFENSE": "MELEE",
        "FLETCHING": "CRAFTING",
    }.items():
        expected = f"Skill.{source}, Skill.{target}"
        if expected not in shortcut_text:
            failures.append(f"MyWorldQuestShortcuts.java is missing explicit {source} -> {target} reward remap")

    if failures:
        fail("\n".join(failures))

    print("PASS: quest reward guardrails validated")


if __name__ == "__main__":
    main()
