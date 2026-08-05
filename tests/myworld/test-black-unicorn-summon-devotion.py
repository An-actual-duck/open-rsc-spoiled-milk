#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
DEVOTION = ROOT / "server/src/com/openrsc/server/content/Devotion.java"
GAIN = ROOT / "server/src/com/openrsc/server/content/DevotionOfferingGain.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
NPC = ROOT / "server/src/com/openrsc/server/model/entity/npc/Npc.java"


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        raise SystemExit(f"FAIL: {label} missing expected snippet: {snippet}")


def main() -> None:
    summoning = SUMMONING.read_text(encoding="utf-8")
    devotion = DEVOTION.read_text(encoding="utf-8")
    gain = GAIN.read_text(encoding="utf-8")
    player = PLAYER.read_text(encoding="utf-8")
    npc = NPC.read_text(encoding="utf-8")

    require(
        summoning,
        '"Mourning Unicorn", 45, 230, NpcId.BLACK_UNICORN.id(), KIND_BLACK_UNICORN, 0',
        "Black unicorn support profile should not grant a Prayer bonus",
    )
    require(
        player,
        "getCarriedItems().getEquipment().getPrayer() + Summoning.getPrayerBonus(this)",
        "Player prayer calculations should include summon Prayer bonus",
    )
    require(
        summoning,
        "final int devotionBonusXp = recordAutoBuryDevotionBonus(owner, amount);",
        "Black unicorn auto-offerings should calculate devotion bonus XP",
    )
    require(
        summoning,
        "bonusXp += Devotion.recordBlackUnicornOfferingAndGetPrayerXpBonus(owner);",
        "Black unicorn auto-offerings should advance devotion with the unicorn bonus",
    )
    require(
        devotion,
        "DevotionOfferingGain.getHalfOfferingUnits(",
        "Black unicorn auto-offerings should use exact additive Devotion gains",
    )
    require(
        gain,
        "BLACK_UNICORN_BONUS_HALF_UNITS = 1",
        "Black unicorn should add exactly half an offering per offering",
    )
    require(
        summoning,
        "final int xp = getPrayerDropExperience(itemId) * amount * 2;",
        "Black unicorn auto-offerings should keep normal Worship XP separate",
    )
    require(
        summoning,
        "Devotion.awardOfferingPrayerXpBonus(owner, Skill.PRAYER.id(), devotionBonusXp);",
        "Black unicorn auto-offerings should award devotion bonus as flat Worship XP",
    )
    require(
        summoning,
        "for (int i = 0; i < amount; i++)",
        "Black unicorn auto-offerings should apply devotion credit per item",
    )
    require(
        summoning,
        "recordAutoBuryDevotionBonus(owner, amount)",
        "Black unicorn auto-offerings should record devotion for the whole stack",
    )
    require(
        summoning,
        "BlackUnicornOfferingHealing.apply(owner, itemId, amount);",
        "Black unicorn auto-offerings should apply shared stacked set healing",
    )
    death_necklace_bonus = (
        "final Item boneDrop = applyDeathNecklaceGuaranteedDropBonus(owner, new Item(bones, 1));"
    )
    bone_priority = "if (!Summoning.tryAutoBuryDrop(owner, bones, boneAmount))"
    law_banking = (
        "owner.getCarriedItems().getEquipment()"
        ".tryBankMonsterLootWithLawNecklace(new Item(bones, boneAmount))"
    )
    require(
        npc,
        death_necklace_bonus,
        "Guaranteed bone-quantity bonuses should be resolved before summon handling",
    )
    require(
        npc,
        bone_priority,
        "Black unicorn should get first chance at NPC bone drops",
    )
    require(
        npc,
        law_banking,
        "Law necklace fallback should still handle non-offered bone drops",
    )
    if not (
        npc.index(death_necklace_bonus)
        < npc.index(bone_priority)
        < npc.index(law_banking)
    ):
        raise SystemExit("FAIL: Black unicorn auto-offering should run before law necklace monster-loot banking")

    print("PASS: black unicorn summon records boosted devotion offerings without granting Prayer")


if __name__ == "__main__":
    main()
