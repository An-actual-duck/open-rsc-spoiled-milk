#!/usr/bin/env python3
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
GUIDE = ROOT / "Client_Base" / "src" / "com" / "openrsc" / "interfaces" / "misc" / "SkillGuideInterface.java"
SPELL_HANDLER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "handlers" / "SpellHandler.java"
SPELL_CLASSIFICATION = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "handlers" / "SpellClassification.java"
SPELL_DEF = ROOT / "server" / "conf" / "server" / "defs" / "SpellDef.xml"

MIND_RUNE_ID = "35"

MIND_COMBAT_SPELLS = {
    "Wind Arrow": "WIND_STRIKE",
    "Water Ball": "WATER_STRIKE",
    "Rock Throw": "EARTH_STRIKE",
    "Fireball": "FIRE_STRIKE",
    "Thunder Ball": "THUNDER_BALL",
    "Icicle Shot": "ICICLE_SHOT",
    "Acid Drop": "ACID_DROP",
    "Spore": "BRANCH_SPORE",
}


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        fail(f"{label} missing expected snippet: {snippet}")


def require_regex(text: str, pattern: str, label: str) -> None:
    if not re.search(pattern, text, re.DOTALL):
        fail(f"{label} missing expected pattern: {pattern}")


def get_regular_mind_combat_spell_names() -> set[str]:
    root = ET.parse(SPELL_DEF).getroot()
    names: set[str] = set()
    for spell in root.findall("SpellDef"):
        if spell.findtext("type") != "2":
            continue
        if int(spell.findtext("reqLevel", "0")) > 10:
            continue
        rune_ids = {
            entry.findtext("int")
            for entry in spell.findall("./requiredRunes/entry")
        }
        if MIND_RUNE_ID in rune_ids:
            names.add(spell.findtext("name", ""))
    return names


def main() -> None:
    guide = GUIDE.read_text(encoding="utf-8")
    handler = SPELL_HANDLER.read_text(encoding="utf-8")
    classification = SPELL_CLASSIFICATION.read_text(encoding="utf-8")

    require(guide, "Chaos gauntlets raise mind-rune combat spells to the chaos-rune damage cap", "Magic guide chaos gauntlet line")
    require(guide, "Thunder can Startle: staggers for 1 attack; then 5 unstaggered attacks before reapplication", "Magic guide Thunder dual-effect line")
    require(guide, "Acid spells can Corrode and apply poison", "Magic guide Acid dual-effect line")
    require(guide, "Ice spells can Frostbite and reflect damage", "Magic guide Ice dual-effect line")
    require(guide, "Wood spells can Splinter to hit another eligible enemy", "Magic guide Wood dual-effect line")

    mind_combat_spells = get_regular_mind_combat_spell_names()
    if mind_combat_spells != set(MIND_COMBAT_SPELLS):
        fail(f"Unexpected mind-rune combat spell set: {sorted(mind_combat_spells)}")

    is_mind_spell = classification[
        classification.index("static boolean isMindSpell") :
        classification.index("static boolean isThunderSpell")
    ]
    for spell_name, enum_name in MIND_COMBAT_SPELLS.items():
        require(is_mind_spell, f"Spells.{enum_name}", f"Chaos gauntlet mind-rune coverage for {spell_name}")

    require_regex(
        handler,
        r"chaosGauntletBonus && SpellClassification\.isMindSpell\(spellEnum\)\s*"
        r"\?\s*0\.60D\s*:\s*SpellClassification\.getSpellDamageCapPercent\(spellEnum\)",
        "Chaos gauntlets should promote mind-rune spells to chaos-rune damage cap",
    )
    require_regex(
        classification,
        r"case WIND_STRIKE:.*?case WATER_STRIKE:.*?case EARTH_STRIKE:.*?case FIRE_STRIKE:.*?"
        r"case THUNDER_BALL:.*?case ICICLE_SHOT:.*?case ACID_DROP:.*?case BRANCH_SPORE:.*?return 0\.40D;",
        "All mind-rune combat spells should share the tier-1 damage cap before chaos gauntlets",
    )
    require_regex(
        classification,
        r"case WIND_BOLT:.*?case WATER_BOLT:.*?case EARTH_BOLT:.*?case FIRE_BOLT:.*?"
        r"case THUNDER_SPLASH:.*?case ICE_BURST:.*?case ACID_FROG:.*?case WOOD_DRILL:.*?return 0\.60D;",
        "All chaos-rune combat spells should share the tier-2 damage cap",
    )

    print("PASS: magic guide and chaos gauntlet spell coverage validated")


if __name__ == "__main__":
    main()
