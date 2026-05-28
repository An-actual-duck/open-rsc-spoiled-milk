#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FORMULAE = ROOT / "server/src/com/openrsc/server/util/rsc/Formulae.java"
SPELL_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"
MAGIC_COMBAT_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/MagicCombatEvent.java"


def strip_comments(source: str) -> str:
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
    return re.sub(r"//.*", "", source)


def main() -> int:
    failures: list[str] = []
    searched = [SPELL_HANDLER, MAGIC_COMBAT_EVENT]

    for path in searched:
        source = strip_comments(path.read_text(encoding="utf-8"))
        if "Formulae.castSpell(" in source:
            failures.append(f"{path.relative_to(ROOT)} calls Formulae.castSpell, reintroducing random spell failure")
        if re.search(r"\bspellfail\b|spellfail\.wav|You fail to cast", source, re.I):
            failures.append(f"{path.relative_to(ROOT)} references spell-failure presentation")

    formulae_source = FORMULAE.read_text(encoding="utf-8")
    declarations = len(re.findall(r"\bboolean\s+castSpell\s*\(", formulae_source))
    call_sites = 0
    for path in ROOT.rglob("*.java"):
        if path == FORMULAE:
            continue
        source = strip_comments(path.read_text(encoding="utf-8", errors="ignore"))
        call_sites += len(re.findall(r"\bFormulae\.castSpell\s*\(", source))

    print(f"Formulae.castSpell declarations: {declarations}")
    print(f"Live Formulae.castSpell call sites outside Formulae.java: {call_sites}")

    if declarations == 0:
        print("Note: Formulae.castSpell has already been removed entirely.")

    if call_sites:
        failures.append("Random spell-failure helper has live call sites")

    if failures:
        print("\nFAIL:")
        for failure in failures:
            print(failure)
        return 1

    print("\nPASS: live magic casting paths do not use random spell failure")
    return 0


if __name__ == "__main__":
    sys.exit(main())
