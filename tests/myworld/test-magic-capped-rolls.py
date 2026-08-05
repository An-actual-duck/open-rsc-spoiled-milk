#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COMBAT_FORMULA = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java"
SPELL_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"
SPELL_CLASSIFICATION = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellClassification.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def main() -> None:
    formula = COMBAT_FORMULA.read_text(encoding="utf-8")
    if "applyMitigationRoll(source, victim, cappedAttackMax, defenseMax, directAttack)" not in formula:
        fail("capped magic spells should roll directly against cappedAttackMax")
    if re.search(r"Math\.min\s*\(\s*offenseRoll\s*,\s*damageCap\s*\)", formula):
        fail("capped magic spells should not roll high and clamp high rolls into the cap")
    if re.search(r"applyMitigationRoll\s*\([^)]*damageCap", formula):
        fail("damageCap overload should not be used for magic roll capping")

    handler = SPELL_HANDLER.read_text(encoding="utf-8")
    classification = SPELL_CLASSIFICATION.read_text(encoding="utf-8")
    classification_required = (
        "case WIND_STRIKE:",
        "case WATER_STRIKE:",
        "case EARTH_STRIKE:",
        "case FIRE_STRIKE:",
        "case IBAN_BLAST:",
        "return 0.40D;",
    )
    handler_required = (
        "final double ibanDamageCapPercent = SpellClassification.getSpellDamageCapPercent(spellEnum);",
        "CombatFormula.calculateMagicDamage(getPlayer(), affectedMob, 15, ibanDamageCapPercent)",
        "SpellClassification.getSpellDamageCapPercent(Spells.IBAN_BLAST)",
    )
    for needle in classification_required:
        if needle not in classification:
            fail(f"mind-tier spell cap missing `{needle}`")
    for needle in handler_required:
        if needle not in handler:
            fail(f"mind-tier spell cap missing `{needle}`")

    print("PASS: capped magic spells roll inside the cap instead of clamping high rolls")


if __name__ == "__main__":
    main()
