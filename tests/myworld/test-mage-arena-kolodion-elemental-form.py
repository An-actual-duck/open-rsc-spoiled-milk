#!/usr/bin/env python3
"""Validate Mage Arena Kolodion elemental forms."""

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
NPC_PROFILE = ROOT / "server/src/com/openrsc/server/model/entity/npc/NpcAttackStyleProfile.java"
NPC_BEHAVIOR = ROOT / "server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java"
PROJECTILE_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java"
MAGE_ARENA = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/minigames/mage_arena/MageArena.java"
PVM_MELEE = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java"
BASE_NPC_DEFS = ROOT / "server/conf/server/defs/NpcDefs.json"
CUSTOM_NPC_DEFS = ROOT / "server/conf/server/defs/NpcDefsCustom.json"
MYWORLD_NPC_DEFS = ROOT / "server/conf/server/defs/NpcDefsMyWorld.json"


def fail(message: str) -> None:
	print(f"FAIL: {message}")
	sys.exit(1)


def require(condition: bool, message: str) -> None:
	if not condition:
		fail(message)


def require_contains(text: str, snippet: str, label: str) -> None:
	require(snippet in text, f"{label} missing expected snippet: {snippet}")


def require_regex(text: str, pattern: str, label: str) -> None:
	require(re.search(pattern, text, re.DOTALL) is not None, f"{label} missing expected pattern: {pattern}")


def load_json_array(path: Path, key: str) -> list[dict]:
	data = json.loads(path.read_text(encoding="utf-8"))
	entries = data.get(key)
	require(isinstance(entries, list), f"{path.name} must contain a top-level '{key}' array")
	return entries


def load_merged_npcs() -> dict[int, dict]:
	merged: dict[int, dict] = {}
	for entry in load_json_array(BASE_NPC_DEFS, "npcs"):
		merged[int(entry["id"])] = dict(entry)
	for entry in load_json_array(CUSTOM_NPC_DEFS, "npcs"):
		merged[int(entry["id"])] = dict(entry)
	for entry in load_json_array(MYWORLD_NPC_DEFS, "npcs"):
		npc_id = int(entry["id"])
		require(npc_id in merged, f"NpcDefsMyWorld.json overrides unknown npc id {npc_id}")
		merged[npc_id].update(entry)
	return merged


def main() -> int:
	profile = NPC_PROFILE.read_text(encoding="utf-8")
	behavior = NPC_BEHAVIOR.read_text(encoding="utf-8")
	projectile_event = PROJECTILE_EVENT.read_text(encoding="utf-8")
	arena = MAGE_ARENA.read_text(encoding="utf-8")
	pvm_melee = PVM_MELEE.read_text(encoding="utf-8")

	require_contains(profile, "private static boolean isKolodionIntroForm(final Npc npc)", "Kolodion intro profile helper")
	require_contains(profile, "private static boolean isKolodionOgreForm(final Npc npc)", "Kolodion ogre profile helper")
	require_contains(profile, "private static boolean isKolodionSpiderForm(final Npc npc)", "Kolodion spider profile helper")
	require_contains(profile, "private static boolean isKolodionSoulessForm(final Npc npc)", "Kolodion souless profile helper")
	require_contains(profile, "private static boolean isKolodionDemonForm(final Npc npc)", "Kolodion demon profile helper")
	require_contains(profile, "return npc != null && npc.getID() == NpcId.KOLODION_HUMAN.id();", "Kolodion intro id check")
	require_contains(profile, "return npc != null && npc.getID() == NpcId.KOLODION_OGRE.id();", "Kolodion ogre id check")
	require_contains(profile, "return npc != null && npc.getID() == NpcId.KOLODION_SPIDER.id();", "Kolodion spider id check")
	require_contains(profile, "return npc != null && npc.getID() == NpcId.KOLODION_SOULESS.id();", "Kolodion souless id check")
	require_contains(profile, "return npc != null && npc.getID() == NpcId.KOLODION_DEMON.id();", "Kolodion demon id check")
	require_contains(profile, "if (isKolodionIntroForm(npc)) {\n\t\t\treturn PURE_MAGIC;\n\t\t}", "Kolodion intro profile")
	require_contains(profile, "if (isKolodionOgreForm(npc)) {\n\t\t\treturn MELEE_FREQUENT_MAGIC;\n\t\t}", "Kolodion ogre profile")
	require_contains(profile, "if (isKolodionSpiderForm(npc)) {\n\t\t\treturn MELEE_FREQUENT_MAGIC;\n\t\t}", "Kolodion spider profile")
	require_contains(profile, "if (isKolodionSoulessForm(npc)) {\n\t\t\treturn MELEE_FREQUENT_MAGIC;\n\t\t}", "Kolodion souless profile")
	require_contains(profile, "if (isKolodionDemonForm(npc)) {\n\t\t\treturn MELEE_RARE_MAGIC;\n\t\t}", "Kolodion demon profile")
	require_contains(profile, "getCombatRandom().nextInt(100) < 85;", "Kolodion ogre frequent magic preference")
	require_contains(profile, "getCombatRandom().nextInt(100) < 10;", "Kolodion demon rare magic preference")
	require_contains(profile, "return randomElement(npc, NpcMagicElement.AIR, NpcMagicElement.WATER, NpcMagicElement.EARTH, NpcMagicElement.FIRE);", "Kolodion intro elemental split")
	require_contains(profile, "return randomElement(npc, NpcMagicElement.AIR, NpcMagicElement.EARTH);", "Kolodion ogre elemental split")
	require_contains(profile, "if (isKolodionSpiderForm(npc)) {\n\t\t\treturn NpcMagicElement.NONE;\n\t\t}", "Kolodion spider acid element")
	require_contains(profile, "return randomElement(npc, NpcMagicElement.AIR, NpcMagicElement.THUNDER, NpcMagicElement.WOOD);", "Kolodion souless spell split")
	require_contains(profile, "if (isKolodionDemonForm(npc)) {\n\t\t\treturn NpcMagicElement.FIRE;\n\t\t}", "Kolodion demon fire spell")
	require_contains(profile, "if (isKolodionSpiderForm(npc)) {\n\t\t\treturn Projectile.ACID_DROP;\n\t\t}", "Kolodion spider acid projectile")
	for projectile in ("Projectile.WIND_ARROW", "Projectile.WATER_BALL", "Projectile.ROCK_THROW", "Projectile.FIREBALL"):
		require_contains(profile, f"return {projectile};", f"Kolodion elemental projectile fallback {projectile}")
	require_contains(profile, "if (isKolodionIntroForm(npc)) {\n\t\t\treturn CombatEffect.NONE;\n\t\t}", "Kolodion intro impact")
	require_contains(profile, "private static int getKolodionOgreImpactEffect(final NpcMagicElement element)", "Kolodion ogre impact helper")
	require_contains(profile, "if (isKolodionOgreForm(npc)) {\n\t\t\treturn getKolodionOgreImpactEffect(element);\n\t\t}", "Kolodion ogre impact selection")
	require_contains(profile, "return CombatEffect.WIND_BEAM;", "Kolodion ogre wind beam effect")
	require_contains(profile, "return CombatEffect.EARTH_BURST;", "Kolodion ogre earth burst effect")
	require_contains(profile, "if (isKolodionSpiderForm(npc)) {\n\t\t\treturn CombatEffect.ACID_GUSH;\n\t\t}", "Kolodion spider acid gush effect")
	require_contains(profile, "public int getMagicAcidPoisonPower(final Npc npc, final NpcMagicElement element)", "Kolodion spider acid poison accessor")
	require_contains(profile, "if (isKolodionSpiderForm(npc)) {\n\t\t\treturn 40;\n\t\t}", "Kolodion spider acid poison power")
	require_contains(profile, "private static int getKolodionSoulessImpactEffect(final NpcMagicElement element)", "Kolodion souless impact helper")
	require_contains(profile, "return CombatEffect.TORNADO;", "Kolodion souless tornado effect")
	require_contains(profile, "return CombatEffect.THUNDER_STRIKE;", "Kolodion souless thunder strike effect")
	require_contains(profile, "return CombatEffect.BATTERING_RAM;", "Kolodion souless battering ram effect")
	require_contains(profile, "public int getMagicStartleProcChancePercent(final Npc npc, final NpcMagicElement element)", "Kolodion souless startle accessor")
	require_contains(profile, "if (isKolodionSoulessForm(npc) && element == NpcMagicElement.THUNDER) {\n\t\t\treturn 25;\n\t\t}", "Kolodion souless thunder startle chance")
	require_contains(profile, "public int getMagicSplinterProcChancePercent(final Npc npc, final NpcMagicElement element)", "Kolodion souless splinter accessor")
	require_contains(profile, "if (isKolodionSoulessForm(npc) && element == NpcMagicElement.WOOD) {\n\t\t\treturn 25;\n\t\t}", "Kolodion souless wood splinter chance")
	require_contains(profile, "if (isKolodionDemonForm(npc)) {\n\t\t\treturn CombatEffect.FIRE_PILLAR;\n\t\t}", "Kolodion demon fire pillar effect")
	require_contains(profile, "public int getMagicFireDefenseDebuffPercent(final Npc npc, final NpcMagicElement element)", "Kolodion demon fire debuff accessor")
	require_contains(profile, "private static final int FIRE_PILLAR_FIRE_DEFENSE_DEBUFF_PERCENT = 12;", "Kolodion demon fire pillar debuff amount")
	require_contains(profile, "if (isKolodionDemonForm(npc) && element == NpcMagicElement.FIRE) {\n\t\t\treturn FIRE_PILLAR_FIRE_DEFENSE_DEBUFF_PERCENT;\n\t\t}", "Kolodion demon fire pillar scorch")

	require_contains(behavior, "int startleProcChancePercent = magicAttack.getStartleProcChancePercent();", "NPC behavior startle lookup")
	require_contains(behavior, "int acidPoisonPower = magicAttack.getAcidPoisonPower();", "NPC behavior acid poison lookup")
	require_contains(behavior, "int fireDefenseDebuffPercent = magicAttack.getFireDefenseDebuffPercent();", "NPC behavior fire debuff lookup")
	require_contains(behavior, "int splinterProcChancePercent = magicAttack.getSplinterProcChancePercent();", "NPC behavior splinter lookup")
	require_contains(behavior, "magicAttack.getProjectileVisual(),", "NPC behavior fire debuff projectile payload")
	require_contains(behavior, ".dualElementProcs(startleProcChancePercent,\n\t\t\t\t\t\tacidPoisonPower, 0, splinterProcChancePercent)", "NPC behavior status projectile payload")
	require_contains(projectile_event, "NpcMagicElement magicElement, int startleProcChancePercent, int acidPoisonPower,", "Projectile event combined magic/status constructor")
	require_contains(projectile_event, "if (acidPoisonPower >= 40) {\n\t\t\treturn 25;", "Acid Gush poison proc chance")
	require_contains(projectile_event, "opponent.applyPoison(acidPoisonPower, acidPoisonPower, caster);", "Acid Gush poison application")

	require_contains(arena, "private boolean isIntroductoryKolodionForm(final Npc npc)", "Mage Arena intro form helper")
	require_contains(arena, "private boolean isKolodionOgreForm(final Npc npc)", "Mage Arena ogre form helper")
	require_contains(arena, "private boolean isKolodionSpiderForm(final Npc npc)", "Mage Arena spider form helper")
	require_contains(arena, "private boolean isKolodionSoulessForm(final Npc npc)", "Mage Arena souless form helper")
	require_contains(arena, "private boolean isKolodionDemonForm(final Npc npc)", "Mage Arena demon form helper")
	require_contains(arena, "private void startKolodionEvent(Player player)", "Mage Arena Kolodion cleanup event")
	require_contains(arena, "clampMageArenaCombatStats(getOwner());", "Mage Arena should keep arena stat clamp active")
	for retired_retaliation_snippet in (
		"usesProfileDrivenKolodionMagic",
		"maged_kolodion",
		"godSpellObject(",
		"Mage Arena Learn Spell Event",
		"Spells.CLAWS_OF_GUTHIX",
		"Spells.SARADOMIN_STRIKE",
		"Spells.FLAMES_OF_ZAMORAK",
	):
		require(retired_retaliation_snippet not in arena, f"Mage Arena should not keep old special retaliation snippet: {retired_retaliation_snippet}")
	require_regex(
		arena,
		r"if \(isIntroductoryKolodionForm\(kolodion\)\) \{.*?kolodion raises his staff and begins casting elemental magic.*?\} else if \(isKolodionOgreForm\(kolodion\)\) \{.*?kolodion grows larger and channels earth and air magic.*?\} else if \(isKolodionSpiderForm\(kolodion\)\) \{.*?kolodion skitters forward as acid gathers around its fangs.*?\} else if \(isKolodionSoulessForm\(kolodion\)\) \{.*?kolodion's form twists into a forest spirit of violent magic.*?\} else if \(isKolodionDemonForm\(kolodion\)\) \{.*?kolodion erupts into a demon and lashes out with burning claws.*?\} else \{.*?player\.damage\(random\(7, 15\)\);.*?\}",
		"Mage Arena early-form opening blast bypass",
	)
	require_contains(arena, "kolodion erupts into a demon and lashes out with burning claws", "Mage Arena demon opening blast bypass message")

	require_contains(pvm_melee, "import com.openrsc.server.constants.NpcId;", "PvM melee Kolodion id import")
	require_contains(pvm_melee, "private boolean isNpcMeleeDisabled()", "PvM melee suppression helper")
	require_contains(pvm_melee, "attackerMob.isNpc() && ((Npc) attackerMob).getID() == NpcId.KOLODION_HUMAN.id()", "PvM melee Kolodion suppression id")
	require_contains(pvm_melee, "private static final double KOLODION_DEMON_FIRE_CLAW_PROC_CHANCE = 0.10D;", "Kolodion demon fire claw proc chance")
	require_contains(pvm_melee, "private static final int FIRE_CLAW_FIRE_DEFENSE_DEBUFF_PERCENT = 6;", "Kolodion demon fire claw debuff amount")
	require_contains(pvm_melee, "applyNpcMeleeSpecialProc(attackerMob, targetMob, damage);", "NPC melee special proc hook")
	require_contains(pvm_melee, "KolodionFireClawProc.tryApply(hitter, target, damage,", "Kolodion demon fire claw proc gate")
	require_contains(pvm_melee, "target.getUpdateFlags().setCombatEffect(new CombatEffect(target, CombatEffect.FIRE_CLAW));", "Kolodion demon fire claw visual")
	require_contains(pvm_melee, "target.applyFireDefenseDebuff(FIRE_CLAW_FIRE_DEFENSE_DEBUFF_PERCENT);", "Kolodion demon fire claw scorch")
	require_regex(
		pvm_melee,
		r"if \(isNpcMeleeDisabled\(\)\) \{.*?attackerMob\.faceCombat\(targetMob\);.*?setDelayTicks\(3\);.*?return;.*?\}",
		"PvM melee suppression branch",
	)

	require_contains(profile, "return Math.max(1, Math.max(npc.getDef().getAtt(), npc.getDef().getStr()));", "NPC magic offense source")
	require_contains(profile, "return Math.max(1.0D, npc.getMagicOffense() / 12.0D);", "NPC magic spell power source")
	require_regex(profile, r"case \"lesser demon\":(?:(?!return [A-Z_]+;).)*return MELEE_MAGIC;", "Lesser demon attack style")
	require_regex(profile, r"case \"lesser demon\":(?:(?!return NpcMagicElement\.[A-Z_]+;).)*return NpcMagicElement\.FIRE;", "Lesser demon element")

	npcs = load_merged_npcs()
	intro_kolodion = npcs[713]
	require(str(intro_kolodion.get("name", "")).lower() == "kolodion", "NPC 713 should still be Kolodion")
	require(int(intro_kolodion.get("hits", 0)) == 30, "Introductory Kolodion should have 30 hits")
	ogre_kolodion = npcs[757]
	require(str(ogre_kolodion.get("name", "")).lower() == "kolodion", "NPC 757 should still be Kolodion")
	require(int(ogre_kolodion.get("hits", 0)) == 65, "Ogre Kolodion should keep 65 hits")
	require(max(int(ogre_kolodion.get("attack", 0)), int(ogre_kolodion.get("strength", 0))) > 0, "Ogre Kolodion needs attack/strength for magic offense")
	spider_kolodion = npcs[758]
	require(str(spider_kolodion.get("name", "")).lower() == "kolodion", "NPC 758 should still be Kolodion")
	require(int(spider_kolodion.get("hits", 0)) == 78, "Spider Kolodion should keep 78 hits")
	require(max(int(spider_kolodion.get("attack", 0)), int(spider_kolodion.get("strength", 0))) > 0, "Spider Kolodion needs attack/strength for magic offense")
	souless_kolodion = npcs[759]
	require(str(souless_kolodion.get("name", "")).lower() == "kolodion", "NPC 759 should still be Kolodion")
	require(int(souless_kolodion.get("attack", 0)) == 38, "Souless Kolodion should have 38 attack")
	require(int(souless_kolodion.get("defense", 0)) == 88, "Souless Kolodion should have 88 defense")
	require(int(souless_kolodion.get("hits", 0)) == 78, "Souless Kolodion should keep 78 hits")
	require(max(int(souless_kolodion.get("attack", 0)), int(souless_kolodion.get("strength", 0))) > 0, "Souless Kolodion needs attack/strength for magic offense")
	demon_kolodion = npcs[760]
	require(str(demon_kolodion.get("name", "")).lower() == "kolodion", "NPC 760 should still be Kolodion")
	require(int(demon_kolodion.get("hits", 0)) == 107, "Demon Kolodion should keep 107 hits")
	require(int(demon_kolodion.get("attack", 0)) == 105, "Demon Kolodion should keep 105 attack for melee pressure")
	require(max(int(demon_kolodion.get("attack", 0)), int(demon_kolodion.get("strength", 0))) > 0, "Demon Kolodion needs attack/strength for magic offense")

	for npc_id in (713, 757, 758, 759, 760):
		require(float(npcs[npc_id].get("magicDefenseMultiplier", 0)) == 1.0, f"Kolodion form {npc_id} should have magic defense multiplier 1.0")

	for npc_id in (22, 181):
		npc = npcs[npc_id]
		require(str(npc.get("name", "")).lower() == "lesser demon", f"NPC {npc_id} should still be a lesser demon")
		require(int(npc.get("attackable", 0)) == 1, f"NPC {npc_id} lesser demon should remain attackable")
		require(max(int(npc.get("attack", 0)), int(npc.get("strength", 0))) > 0, f"NPC {npc_id} lesser demon needs attack/strength for magic offense")

	print("PASS: Mage Arena Kolodion elemental forms validated")
	return 0


if __name__ == "__main__":
	sys.exit(main())
