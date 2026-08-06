#!/usr/bin/env python3

import re
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
EQUIPMENT = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
STAT_RESTORATION = ROOT / "server/src/com/openrsc/server/event/rsc/impl/StatRestorationEvent.java"
COMBAT_FORMULA = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java"
SPELL_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"
SPELL_CLASSIFICATION = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellClassification.java"
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
PROJECTILE_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java"
PVM_MELEE_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java"
COMBAT_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java"
RUNECRAFT = ROOT / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/runecraft/Runecraft.java"


def fail(message: str) -> NoReturn:
	print(f"FAIL: {message}")
	sys.exit(1)


def require(text: str, snippet: str, label: str) -> None:
	if snippet not in text:
		fail(f"{label} missing expected snippet: {snippet}")


def require_regex(text: str, pattern: str, label: str) -> None:
	if not re.search(pattern, text, re.DOTALL):
		fail(f"{label} missing expected pattern: {pattern}")


def java_method_body(text: str, signature: str) -> str:
	start = text.find(signature)
	if start == -1:
		fail(f"Missing Java method signature: {signature}")
	opening_brace = text.find("{", start)
	if opening_brace == -1:
		fail(f"Missing Java method body: {signature}")
	depth = 0
	for index in range(opening_brace, len(text)):
		if text[index] == "{":
			depth += 1
		elif text[index] == "}":
			depth -= 1
			if depth == 0:
				return text[opening_brace + 1:index]
	fail(f"Unterminated Java method body: {signature}")


def main() -> None:
	equipment = EQUIPMENT.read_text(encoding="utf-8")
	player = PLAYER.read_text(encoding="utf-8")
	stat_restoration = STAT_RESTORATION.read_text(encoding="utf-8")
	combat_formula = COMBAT_FORMULA.read_text(encoding="utf-8")
	spell_handler = SPELL_HANDLER.read_text(encoding="utf-8")
	spell_classification = SPELL_CLASSIFICATION.read_text(encoding="utf-8")
	summoning = SUMMONING.read_text(encoding="utf-8")
	projectile_event = PROJECTILE_EVENT.read_text(encoding="utf-8")
	pvm_melee_event = PVM_MELEE_EVENT.read_text(encoding="utf-8")
	combat_event = COMBAT_EVENT.read_text(encoding="utf-8")
	runecraft = RUNECRAFT.read_text(encoding="utf-8")

	for robe in ("Air", "Water", "Earth", "Fire", "Mind", "Body", "Nature", "Cosmic", "Chaos", "Law", "Blood", "Death", "Soul", "Life"):
		require(equipment, f"get{robe}RobeTierTotal()", f"{robe} robe tier total accessor")
		require(equipment, f"{robe.lower()}TierTotal += tier;", f"{robe} robe tier accumulation")

	require(player, "getMindRobeSpellCapBonus()", "Mind robe spell cap bonus method")
	require(player, "getNatureRobePotionBonusPercent()", "Nature robe potion bonus method")
	require(player, "getSoulRobeHealthRegenerationBonus()", "Soul robe health regeneration method")
	require(player, "applyPotionPowerBonus(value)", "Nature robe should strengthen potion values")
	require(player, "+ (getNatureRobePotionBonusPercent() / 100.0D)", "Nature robe should extend potion duration")
	require(player, "rollCosmicRobeCrit()", "Cosmic robe crit roll method")
	require(player, "getChaosRobeSurroundedDamageMultiplier()", "Chaos robe surrounded damage method")
	require(player, "getBloodRobeSpellSplashPercent()", "Blood robe spell splash method")
	require(player, "getDeathRobeOverkillSplashPercent()", "Death robe overkill splash method")
	require(player, "getLifeRobeSummonBonusPercent()", "Life robe summon bonus method")
	require(player, "applyElementalRobeResistance(incomingDamage, magicElement)", "Elemental robe resistance should be part of damage mitigation")
	require(player, "getAirRobeTierTotal()", "Air robe resistance should read air robe tier total")
	require(player, "getWaterRobeTierTotal()", "Water robe resistance should read water robe tier total")
	require(player, "getEarthRobeTierTotal()", "Earth robe resistance should read earth robe tier total")
	require(player, "getFireRobeTierTotal()", "Fire robe resistance should read fire robe tier total")
	require(player, "Math.min(1.0D, tierTotal * 0.02D)", "Elemental robe resistance should scale at two percent per tier")
	require(player, "return 0;", "Full elemental robe resistance should be able to negate matching damage")
	require(player, "chargeBodyRobeWeaponPower(remainingDamage);", "Body robe charge on damage taken")
	require(player, "+ getBodyRobeWeaponPowerBonus()", "Body robe weapon power should affect offense")
	require(player, "BODY_ROBE_POWER_DECAY_TICKS = 10", "Body robe power should decay on a combat-scale timer")

	require(stat_restoration, "tickBodyRobeWeaponPowerDecay();", "Body robe temporary power should decay")
	require(stat_restoration, "getSoulRobeHealthRegenerationBonus()", "Soul robe should speed health regeneration")

	require(combat_formula, "rollPlayerCrit(source, attackMax)", "Cosmic robe crit should affect melee/ranged/magic rolls")
	require(combat_formula, "getChaosRobeSurroundedDamageMultiplier()", "Chaos robe adjacency bonus should enter damage multiplier")
	for retired in (
		"getLawRobeExtraDamageRolls",
		"getMindRobeDebuffMultiplier",
		"getNatureRobePoisonMitigation",
		"getCosmicRobeDefenseRerollChance",
		"getChaosRobeReflectPercent",
		"getSoulRobeShield",
		"applyChaosRobeReflect",
	):
		if retired in player or retired in combat_formula or retired in projectile_event or retired in pvm_melee_event or retired in combat_event:
			fail(f"Retired robe effect hook should be removed: {retired}")

	require(runecraft, "getLawRobeRunecraftBonusPercent(player)", "Law robe runecraft bonus should be applied during runecrafting")
	require(runecraft, "getLawRobeTierTotal() * 2", "Law robe runecraft bonus should scale at 2% per tier")
	require(runecraft, "LAW_ROBE_RUNEPRODUCTION_CACHE_PREFIX + runeId", "Law robe fractional carryover should be stored per rune")

	require(spell_handler, "damageCapPercent += getPlayer().getMindRobeSpellCapBonus();", "Mind robe cap should stack after chaos gauntlets")
	require(spell_classification, "static boolean isBloodSpell(final SpellDef spell)", "Blood spell detection should use spell definitions")
	require(spell_classification, "rune.getKey() == ItemId.BLOOD_RUNE.id()", "Blood spells should be detected by blood rune requirement")
	require(spell_handler, "SpellClassification.isBloodSpell(spell)", "Blood spell flag should be passed to projectile events")

	require(summoning, "getLifeRobeSummonBonusPercent()", "Life robe should affect summon duration/health")

	require(projectile_event, "protected boolean bloodSpell;", "Projectile event should track blood spells")
	require(projectile_event, "protected NpcMagicElement magicElement = NpcMagicElement.NONE;", "Projectile event should track NPC magic element")
	require(projectile_event, "applyRobeDamageMitigation(damage, magicElement)", "Typed projectile damage should use elemental robe resistance")
	require(projectile_event, "applyBloodRobeSplash((Player) caster, damageDealt);", "Blood robe should splash from actual blood spell projectile damage")
	require(projectile_event, "applyDeathRobeOverkillSplash((Player) caster, (Npc) opponent, damage - lastHits);", "Death robe should splash projectile overkill")
	blood_splash = java_method_body(
		projectile_event,
		"private void applyBloodRobeSplash(final Player casterPlayer, final int damageDealt)",
	)
	guard_match = re.search(r"if\s*\((.*?)\)\s*\{\s*return\s*;", blood_splash, re.DOTALL)
	if guard_match is None:
		fail("Blood splash should begin with a rejecting guard clause")
	blood_splash_guard = guard_match.group(1)
	for pattern, label in (
		(r"Summoning\s*\.\s*isPlayerAreaEffectSuppressed\s*\(\s*casterPlayer\s*\)", "Guard Dog AoE suppression"),
		(r"!\s*bloodSpell\b", "blood-spell requirement"),
		(r"opponent\s*==\s*null", "missing-opponent rejection"),
		(r"!\s*opponent\s*\.\s*isNpc\s*\(\s*\)", "non-NPC rejection"),
	):
		require_regex(blood_splash_guard, pattern, f"Blood splash {label}")

	require(pvm_melee_event, "applyDeathRobeOverkillSplash((Player) hitter, (Npc) target, rawDamage - lastHits);", "Death robe should splash PvM melee overkill")
	require(pvm_melee_event, "applyDeathRobeOverkillSplash(player, npc, damage - lastHits);", "Death robe should splash scythe cleave overkill")
	require(combat_event, "applyDeathRobeOverkillSplash((Player) hitter, (Npc) target, rawDamage - lastHits);", "Death robe should splash standard combat overkill")

	print("PASS: enchanted robe runtime effect hooks validated")


if __name__ == "__main__":
	main()
