#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COMBAT_FORMULA = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java"
SPELL_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def main() -> None:
    formula = COMBAT_FORMULA.read_text(encoding="utf-8")
    if "int damage = applyMitigationRoll(source, victim, cappedAttackMax, defenseMax);" not in formula:
        fail("capped magic spells should roll directly against cappedAttackMax")
    if re.search(r"Math\.min\s*\(\s*offenseRoll\s*,\s*damageCap\s*\)", formula):
        fail("capped magic spells should not roll high and clamp high rolls into the cap")
    if re.search(r"applyMitigationRoll\s*\([^)]*damageCap", formula):
        fail("damageCap overload should not be used for magic roll capping")

    handler = SPELL_HANDLER.read_text(encoding="utf-8")
    required = (
        "case WIND_STRIKE:",
        "case WATER_STRIKE:",
        "case EARTH_STRIKE:",
        "case FIRE_STRIKE:",
        "return 0.40D;",
    )
    for needle in required:
        if needle not in handler:
            fail(f"mind-tier spell cap missing `{needle}`")

    print("PASS: capped magic spells roll inside the cap instead of clamping high rolls")


if __name__ == "__main__":
    main()
