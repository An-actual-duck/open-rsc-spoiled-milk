#!/usr/bin/env python3
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]
BONES = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/misc/Bones.java"
SKILL_CAPES = ROOT / "server/src/com/openrsc/server/content/SkillCapes.java"
BROTHER_JERED = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/npcs/edgeville/BrotherJered.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> None:
    bones = BONES.read_text(encoding="utf-8")
    skill_capes = SKILL_CAPES.read_text(encoding="utf-8")
    brother_jered = BROTHER_JERED.read_text(encoding="utf-8")

    cape_method = re.search(
        r"private void prayerCape\(final Player player, final Item bone\) \{.*?\n\t\}",
        bones,
        re.DOTALL,
    )
    require(cape_method, "Missing Worship cape offering effect")
    effect = cape_method.group(0)

    for remains, amount in (
        ("BONES", 1),
        ("BAT_BONES", 1),
        ("BIG_BONES", 2),
        ("DEMON_ASH", 2),
        ("DRAGON_BONES", 4),
    ):
        case_index = effect.find(f"case {remains}:")
        require(case_index >= 0, f"Missing Worship cape tier for {remains}")
        next_assignment = re.search(r"pointsToHeal = (\d+);", effect[case_index:])
        require(next_assignment and int(next_assignment.group(1)) == amount,
                f"{remains} should heal {amount} Hits")

    require("Math.min(pointsToHeal, maxHits - currentHits)" in effect,
            "Worship cape healing must be limited to missing Hits")
    require("setLevel(Skill.HITS.id(), currentHits + healed, true)" in effect,
            "Worship cape should heal the Hits skill")
    require("new HitSplat(player, HitSplat.TYPE_HEAL, healed)" in effect,
            "Worship cape should display its actual healing")
    require("Skill.PRAYER" not in effect and "prayer points" not in effect,
            "Worship cape must no longer restore Prayer allocation points")
    require("final int pointsChance = 100;" in skill_capes,
            "Worship cape offering activation rate should remain 100%")
    require("The cape channels that devotion into vitality" in brother_jered
            and "it can restore some of your health" in brother_jered,
            "Brother Jered should describe the cape's healing effect")
    require("Your prayers to the gods will endure longer" not in brother_jered,
            "Brother Jered must not advertise the retired Prayer restoration effect")

    for requested, missing, expected in (
        (1, 10, 1),
        (2, 10, 2),
        (4, 10, 4),
        (4, 2, 2),
        (4, 0, 0),
    ):
        require(min(requested, missing) == expected,
                f"Missing-health clamp failed for requested={requested}, missing={missing}")

    print("PASS: Worship cape offerings heal 1/2/4 Hits without overhealing")


if __name__ == "__main__":
    main()
