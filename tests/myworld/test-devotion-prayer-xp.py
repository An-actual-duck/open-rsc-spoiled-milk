#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEVOTION = ROOT / "server/src/com/openrsc/server/content/Devotion.java"
BONES = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/misc/Bones.java"
GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


def main() -> None:
    devotion = DEVOTION.read_text(encoding="utf-8")
    bones = BONES.read_text(encoding="utf-8")
    guide = GUIDE.read_text(encoding="utf-8")

    require('CACHE_PREFIX = "devotion_"' in devotion, "devotion cache prefix should be stable")
    require('CACHE_SUFFIX = "_offerings"' in devotion, "devotion cache suffix should be stable")
    require("OFFERINGS_PER_BONUS_XP = 10" in devotion, "devotion should award +1 XP per 10 offerings")
    require("previousOfferings / OFFERINGS_PER_BONUS_XP" in devotion,
            "devotion bonus should be based on completed prior offering tiers")
    require("player.getPrayerBook()" in devotion, "devotion should track against the active worshipped god")
    require("safeGodLine.name().toLowerCase()" in devotion, "devotion cache keys should be per god")
    require("recordOfferingAndGetPrayerXpBonus" in bones, "bones and ashes should record devotion offerings")
    require("xpToGive += devotionBonusXp;" in bones, "devotion bonus should be added to Prayer XP")
    require("Devotion: every 10 offerings to a god adds +1 Prayer XP" in guide,
            "Prayer skill guide should explain devotion")

    print("PASS: devotion Prayer XP scaling is wired to bone and ash offerings")


if __name__ == "__main__":
    main()
