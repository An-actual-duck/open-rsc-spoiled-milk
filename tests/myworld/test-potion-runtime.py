#!/usr/bin/env python3
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
PLAYER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "player" / "Player.java"
CACHE = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "Cache.java"
DRINKABLES = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "itemactions" / "Drinkables.java"
FUNCTIONS = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "plugins" / "Functions.java"
SKILLS = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "Skills.java"
RUNESCRIPT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "plugins" / "RuneScript.java"

PROTECTED_DRAIN_SOURCES = (
    "server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/DragonFireBreath.java",
    "server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/ElvargPrayerDrain.java",
    "server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/KingBlackDragonPrayerDrain.java",
    "server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/MonkZamorak.java",
    "server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/SalarinTheTwistedDrain.java",
    "server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/ShadowSpiderPrayerDrain.java",
    "server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/SkeletonMage.java",
    "server/src/com/openrsc/server/event/rsc/impl/projectile/RangeUtils.java",
    "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java",
    "server/plugins/com/openrsc/server/plugins/authentic/misc/SalarinTheTwistedMageAI.java",
    "server/plugins/com/openrsc/server/plugins/authentic/misc/StrangeBarrels.java",
    "server/plugins/com/openrsc/server/plugins/authentic/misc/Zamorak.java",
    "server/plugins/com/openrsc/server/plugins/authentic/npcs/brimhaven/BrimHavenBartender.java",
    "server/plugins/com/openrsc/server/plugins/authentic/npcs/falador/Barmaid.java",
    "server/plugins/com/openrsc/server/plugins/authentic/npcs/seers/SeersBartender.java",
    "server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/Bartender.java",
    "server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/JollyBoarInnBartender.java",
    "server/plugins/com/openrsc/server/plugins/authentic/quests/members/WitchesHouse.java",
    "server/plugins/com/openrsc/server/plugins/authentic/quests/members/legendsquest/mechanism/LegendsQuestInvAction.java",
    "server/plugins/com/openrsc/server/plugins/authentic/quests/members/legendsquest/npcs/LegendsQuestEchnedZekin.java",
    "server/plugins/com/openrsc/server/plugins/authentic/quests/members/legendsquest/npcs/LegendsQuestGujuo.java",
    "server/plugins/com/openrsc/server/plugins/authentic/quests/members/legendsquest/npcs/LegendsQuestNezikchened.java",
    "server/plugins/com/openrsc/server/plugins/authentic/quests/members/legendsquest/npcs/LegendsQuestUngadulu.java",
    "server/plugins/com/openrsc/server/plugins/authentic/quests/members/legendsquest/obstacles/LegendsQuestGameObjects.java",
)


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, message: str) -> None:
    if snippet not in text:
        fail(message)


def main() -> None:
    player_text = PLAYER.read_text(encoding="utf-8")
    cache_text = CACHE.read_text(encoding="utf-8")
    drinkables_text = DRINKABLES.read_text(encoding="utf-8")
    functions_text = FUNCTIONS.read_text(encoding="utf-8")
    skills_text = SKILLS.read_text(encoding="utf-8")
    runescript_text = RUNESCRIPT.read_text(encoding="utf-8")

    for snippet in (
        "POTION_BRAWN_SKILLS",
        "POTION_DEFTNESS_SKILLS",
        "POTION_INSIGHT_SKILLS",
        "activatePotionOfBrawn",
        "activatePotionOfDeftness",
        "activatePotionOfInsightSkills",
        "activateSkillerBrew",
        "activateWarriorBrew",
        "getPotionXpBonusPercent",
        "setStatReductionProtection",
        "hasStatReductionProtection",
        "syncHerblawSkillPotionBonuses",
        "getHerblawSkillPotionBonus",
        "getPersistedSkillLevel",
        "isCombatXpSkill",
    ):
        require(player_text, snippet, f"Player missing new Herblaw runtime hook: {snippet}")

    for snippet in (
        "final int potionBonus = getHerblawSkillPotionBonus(skill);",
        "return getSkills().getMaxStat(skill) + potionBonus;",
        "return Math.max(0, getSkills().getLevel(skill) - potionBonus);",
        "final int potionXpBonusPercent = getPotionXpBonusPercent(skill);",
        "skill == Skill.MELEE.id()",
        "skill == Skill.SUMMONING.id()",
    ):
        require(player_text, snippet, f"Player missing skill/xp potion behavior: {snippet}")

    for snippet in (
        "case FULL_ATTACK_POTION: return skillPotion(\"brawn\", 5, 5",
        "case FULL_SUPER_ATTACK_POTION: return skillPotion(\"brawn\", 17, 17",
        "case FULL_POTION_OF_BRAWN_V6: return skillPotion(\"brawn\", 20, 20",
        "case FULL_FISHING_POTION: return skillPotion(\"deftness\", 5, 5",
        "case FULL_CURE_POISON_POTION: return skillPotion(\"deftness\", 17, 17",
        "case FULL_POTION_OF_DEFTNESS_V6: return skillPotion(\"deftness\", 20, 20",
        "case FULL_POISON_ANTIDOTE: return skillPotion(\"insight\", 5, 5",
        "case FULL_MAGIC_POTION: return skillPotion(\"insight\", 17, 17",
        "case FULL_POTION_OF_INSIGHT_V6: return skillPotion(\"insight\", 20, 20",
        "case FULL_POTION_OF_SARADOMIN: return specialPotion(\"restore\"",
        "case FULL_SUPER_RANGING_POTION: return specialPotion(\"antidote\"",
        "case FULL_SUPER_MAGIC_POTION: return xpBrew(\"skiller\", 20, 30",
        "case FULL_STRENGTH_POTION: return xpBrew(\"skiller\", 40, 60",
        "case FULL_WARRIORS_BREW: return xpBrew(\"warrior\", 20, 30",
        "case FULL_STRONG_WARRIORS_BREW: return xpBrew(\"warrior\", 40, 60",
        "player.setStatReductionProtection(TimeUnit.MINUTES.toMillis(10), itemId);",
        "player.setPoisonProtection(TimeUnit.MINUTES.toMillis(5), itemId);",
    ):
        require(drinkables_text, snippet, f"Drinkables missing new Herblaw behavior: {snippet}")

    for snippet in (
        "setLevelFromStatReduction",
        "subtractLevelFromStatReduction",
        "((Player) mob).hasStatReductionProtection()",
        "skill != Skill.HITS.id()",
    ):
        require(skills_text, snippet, f"Skills missing protected reduction boundary: {snippet}")

    require(functions_text, "setLevelFromStatReduction",
            "Shared substat helper should use protected reduction boundary")
    require(runescript_text, "setLevelFromStatReduction",
            "RuneScript subplaystat should use protected reduction boundary")

    for relative_path in PROTECTED_DRAIN_SOURCES:
        source = (ROOT / relative_path).read_text(encoding="utf-8")
        require(source, "LevelFromStatReduction",
                f"Temporary drain source bypasses stat protection: {relative_path}")

    # These are deliberately not temporary debuffs: active-prayer upkeep,
    # restoration/boost decay, and player-authorized resource costs must remain
    # able to lower a stat while protection is active.
    for relative_path in (
        "server/src/com/openrsc/server/event/rsc/impl/PrayerDrainEvent.java",
        "server/src/com/openrsc/server/event/rsc/impl/StatRestorationEvent.java",
        "server/plugins/com/openrsc/server/plugins/custom/myworld/misc/GrapeEmpowerment.java",
        "server/plugins/com/openrsc/server/plugins/authentic/quests/members/shilovillage/ShiloVillageUtils.java",
    ):
        source = (ROOT / relative_path).read_text(encoding="utf-8")
        if "LevelFromStatReduction" in source:
            fail(f"Non-debuff stat cost was accidentally protected: {relative_path}")

    require(
        cache_text,
        "public void store(String key, int i)",
        "Integer potion bonuses should not widen into Long cache values",
    )
    require(
        cache_text,
        "longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE",
        "Persisted Long potion bonuses should be safely range-checked",
    )

    print("PASS: potion runtime wiring validated")


if __name__ == "__main__":
    main()
