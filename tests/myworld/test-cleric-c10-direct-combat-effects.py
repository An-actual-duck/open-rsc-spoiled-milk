#!/usr/bin/env python3
"""Compile and validate C10 direct-combat Cleric effect contracts."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server/src/com/openrsc/server"
CLERIC = SERVER / "content/cleric"
EFFECT = CLERIC / "effect"
PLAYER_EFFECT = SERVER / "model/entity/player"
RUNTIME = CLERIC / "runtime/ClericDirectCombatRuntime.java"
CASTING = CLERIC / "runtime/ClericSupportCasting.java"
TIMED = CLERIC / "runtime/ClericTimedEffectRuntime.java"
FORMULA = SERVER / "event/rsc/impl/combat/CombatFormula.java"
OSRS_FORMULA = SERVER / "event/rsc/impl/combat/OSRSCombatFormula.java"
COMBAT = SERVER / "event/rsc/impl/combat/CombatEvent.java"
PVM_COMBAT = SERVER / "event/rsc/impl/combat/PvmMeleeEvent.java"
PROJECTILE = SERVER / "event/rsc/impl/projectile/ProjectileEvent.java"
SPELL_HANDLER = SERVER / "net/rsc/handlers/SpellHandler.java"
SKILLS = SERVER / "model/Skills.java"
PLAYER = PLAYER_EFFECT / "Player.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


FIXTURE = r"""
package com.openrsc.server.content.cleric.effect;

import com.openrsc.server.content.cleric.ClericDirectCombatEffects;
import com.openrsc.server.content.cleric.ClericSpellId;
import com.openrsc.server.model.entity.player.TransientEffectMembershipToken;
import com.openrsc.server.model.entity.player.TransientEffectSessionToken;

public final class ClericC10DirectCombatFixture {
	private interface Action { void run(); }

	private static final class FakeClock implements ClericEffectClock {
		private long now;
		public long nanoTime() { return now; }
		public long getGameTickMilliseconds() { return 640L; }
		void advanceSeconds(long seconds) { now += seconds * 1_000_000_000L; }
	}

	private static final class LiveOrigin implements ClericEffectOriginValidator {
		private final ClericEffectOrigin origin;
		LiveOrigin(ClericEffectOrigin origin) { this.origin = origin; }
		public boolean isCurrent(ClericEffectOrigin candidate) { return candidate == origin; }
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static void reject(Action action, String message) {
		try {
			action.run();
			throw new AssertionError("Expected rejection: " + message);
		} catch (IllegalArgumentException expected) {
			// Expected validation failure.
		}
	}

	private static ClericEffectOrigin origin() {
		return new ClericEffectOrigin(TransientEffectSessionToken.issue(),
			TransientEffectMembershipToken.issue(),
			TransientEffectMembershipToken.issue());
	}

	private static void pureArithmeticChecks() {
		check(Math.abs(ClericDirectCombatEffects.combineUpwardRollChance(0.10D, 20)
			- 0.30D) < 0.000_001D,
			"Fervor must add to the existing upward-roll probability");
		check(ClericDirectCombatEffects.combineUpwardRollChance(0.95D, 20) == 1.0D,
			"combined Fervor probability must be bounded");
		check(ClericDirectCombatEffects.applyProtection(1, 25) == 1,
			"Ward must not prevent or consume on a one-point hit");
		check(ClericDirectCombatEffects.applyProtection(2, 50) == 1,
			"Aegis two-point rounding drift");
		check(ClericDirectCombatEffects.applyProtection(3, 25) == 3,
			"Ward favorable ceiling rounding drift");
		check(ClericDirectCombatEffects.applyProtection(4, 25) == 3,
			"Ward exact reduction drift");
		check(ClericDirectCombatEffects.applyProtection(99, 50) == 50,
			"Aegis ceiling rounding drift");
		check(ClericDirectCombatEffects.applyProtection(Integer.MAX_VALUE, 50)
			== 1_073_741_824, "protection arithmetic overflowed");
		check(ClericDirectCombatEffects.addBounded(Integer.MAX_VALUE, 1)
			== Integer.MAX_VALUE, "Zeal damage overflow must saturate");

		check(ClericDirectCombatEffects.stochasticPercentage(1, 5, 4) == 1,
			"Thorns fractional success boundary drift");
		check(ClericDirectCombatEffects.stochasticPercentage(1, 5, 5) == 0,
			"Thorns fractional miss boundary drift");
		check(ClericDirectCombatEffects.stochasticPercentage(20, 5, 99) == 1,
			"whole Thorns damage must not depend on the random roll");
		check(ClericDirectCombatEffects.stochasticPercentage(0, 15, 0) == 0,
			"zero damage must never gain a Thorns minimum");

		check(ClericDirectCombatEffects.isBelowPercent(49, 100, 50),
			"Rally application must accept strictly below half Hits");
		check(!ClericDirectCombatEffects.isBelowPercent(50, 100, 50),
			"Rally application must reject exactly half Hits");
		check(ClericDirectCombatEffects.isBelowPercent(54, 99, 55),
			"Rally odd-ceiling threshold drift");
		check(!ClericDirectCombatEffects.isBelowPercent(55, 99, 55),
			"Rally odd-ceiling ending boundary drift");

		reject(() -> ClericDirectCombatEffects.applyProtection(1, 100),
			"complete protection");
		reject(() -> ClericDirectCombatEffects.stochasticPercentage(1, 5, 100),
			"out-of-range stochastic roll");
	}

	private static void fractionalCarryChecks() {
		FakeClock clock = new FakeClock();
		ClericEffectOrigin origin = origin();
		LiveOrigin live = new LiveOrigin(origin);
		ClericEffectRegistry registry = new ClericEffectRegistry(clock);
		ClericEffectRankDefinition<?> zeal = ClericEffectCatalog.get(ClericSpellId.ZEAL, 1);
		registry.apply(zeal, origin, live);
		for (int hit = 1; hit < 20; hit++) {
			check(registry.accumulateFractionalPercent(ClericEffectFamily.DAMAGE,
				ClericSpellId.ZEAL, zeal, 1, 5, live) == 0,
				"Zeal rounded a small hit upward at hit " + hit);
		}
		check(registry.accumulateFractionalPercent(ClericEffectFamily.DAMAGE,
			ClericSpellId.ZEAL, zeal, 1, 5, live) == 1,
			"Zeal did not release one whole accumulated point");

		for (int hit = 0; hit < 19; hit++) {
			registry.accumulateFractionalPercent(ClericEffectFamily.DAMAGE,
				ClericSpellId.ZEAL, zeal, 1, 5, live);
		}
		registry.apply(zeal, origin, live);
		check(registry.accumulateFractionalPercent(ClericEffectFamily.DAMAGE,
			ClericSpellId.ZEAL, zeal, 1, 5, live) == 0,
			"equal-rank refresh retained old Zeal fraction");

		ClericEffectRankDefinition<?> zealTwo = ClericEffectCatalog.get(ClericSpellId.ZEAL, 2);
		registry.apply(zealTwo, origin, live);
		check(registry.accumulateFractionalPercent(ClericEffectFamily.DAMAGE,
			ClericSpellId.ZEAL, zeal, 1, 5, live) == 0,
			"stale definition consumed a replacement's Zeal fraction");
		check(registry.accumulateFractionalPercent(ClericEffectFamily.DAMAGE,
			ClericSpellId.ZEAL, zealTwo, Integer.MAX_VALUE, 100, live)
			== Integer.MAX_VALUE, "maximum Zeal fractional input overflowed");

		ClericEffectRegistry rallyRegistry = new ClericEffectRegistry(clock);
		ClericEffectRankDefinition<?> rally = ClericEffectCatalog.get(ClericSpellId.RALLY, 1);
		rallyRegistry.apply(rally, origin, live);
		for (int hit = 0; hit < 4; hit++) {
			check(rallyRegistry.accumulateFractionalPercent(ClericEffectFamily.LIFESTEAL,
				ClericSpellId.RALLY, rally, 1, 20, live) == 0,
				"Rally invented a minimum heal");
		}
		check(rallyRegistry.accumulateFractionalPercent(ClericEffectFamily.LIFESTEAL,
			ClericSpellId.RALLY, rally, 1, 20, live) == 1,
			"Rally did not release one whole accumulated heal");
		check(rallyRegistry.onHitsLevelIncreased(54, 100) == 0,
			"Rally ended before its authored threshold");
		check(rallyRegistry.onHitsLevelIncreased(55, 100) == 1,
			"Rally did not end when ordinary healing reached its threshold");
		check(rallyRegistry.onHitsLevelIncreased(56, 100) == 0,
			"Rally threshold cleanup must be idempotent");

		rallyRegistry.apply(rally, origin, live);
		rallyRegistry.accumulateFractionalPercent(ClericEffectFamily.LIFESTEAL,
			ClericSpellId.RALLY, rally, 4, 20, live);
		clock.advanceSeconds(31);
		check(rallyRegistry.size(live) == 0, "expired Rally state remained active");
		rallyRegistry.apply(rally, origin, live);
		check(rallyRegistry.accumulateFractionalPercent(ClericEffectFamily.LIFESTEAL,
			ClericSpellId.RALLY, rally, 1, 20, live) == 0,
			"expired Rally fraction leaked into a new effect");
	}

	private static void protectionCounterChecks() {
		FakeClock clock = new FakeClock();
		ClericEffectOrigin origin = origin();
		LiveOrigin live = new LiveOrigin(origin);
		ClericEffectRegistry registry = new ClericEffectRegistry(clock);
		ClericEffectRankDefinition<?> ward = ClericEffectCatalog.get(ClericSpellId.WARD, 1);
		ClericEffectRankDefinition<?> aegis = ClericEffectCatalog.get(ClericSpellId.AEGIS, 1);
		registry.apply(ward, origin, live);
		check(registry.consumeCounter(ClericEffectFamily.PROTECTION,
			ClericEffectCounterKind.CHARGES, aegis, live)
			== ClericEffectRegistry.CounterResult.MISSING_OR_INVALID,
			"stale protection identity consumed a Ward charge");
		check(registry.get(ClericEffectFamily.PROTECTION, live).get()
			.getRemainingCounter() == 2, "failed protection precondition changed charges");
		check(registry.consumeCounter(ClericEffectFamily.PROTECTION,
			ClericEffectCounterKind.CHARGES, ward, live)
			== ClericEffectRegistry.CounterResult.CONSUMED,
			"qualifying Ward hit did not consume one charge");
		registry.apply(aegis, origin, live);
		check(registry.get(ClericEffectFamily.PROTECTION, live).get()
			.getDefinition() == aegis, "Aegis did not replace Ward");
		check(registry.consumeCounter(ClericEffectFamily.PROTECTION,
			ClericEffectCounterKind.CHARGES, aegis, live)
			== ClericEffectRegistry.CounterResult.EXHAUSTED,
			"Aegis I must exhaust after one qualifying hit");
	}

	public static void main(String[] args) {
		pureArithmeticChecks();
		fractionalCarryChecks();
		protectionCounterChecks();
	}
}
"""


def run_compiled_fixture() -> None:
    sources = sorted(str(path) for path in CLERIC.glob("*.java"))
    sources.append(str(SERVER / "content/PoisonPowerReduction.java"))
    sources.extend(sorted(
        str(path) for path in EFFECT.glob("*.java")
        if path.name != "ClericEffectOrigins.java"
    ))
    sources.extend(str(PLAYER_EFFECT / name) for name in (
        "TransientEffectMembershipToken.java",
        "TransientEffectSessionToken.java",
        "TransientEffectState.java",
    ))
    with tempfile.TemporaryDirectory(prefix="cleric-c10-direct-") as temporary:
        temp = Path(temporary)
        fixture = temp / (
            "com/openrsc/server/content/cleric/effect/"
            "ClericC10DirectCombatFixture.java"
        )
        fixture.parent.mkdir(parents=True)
        fixture.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        classes = temp / "classes"
        classes.mkdir()
        subprocess.run(
            ["javac", "-d", str(classes), *sources, str(fixture)],
            cwd=ROOT,
            check=True,
        )
        subprocess.run(
            ["java", "-cp", str(classes),
             "com.openrsc.server.content.cleric.effect.ClericC10DirectCombatFixture"],
            cwd=ROOT,
            check=True,
        )


def validate_runtime_wiring() -> None:
    runtime = RUNTIME.read_text(encoding="utf-8")
    casting = CASTING.read_text(encoding="utf-8")
    timed = TIMED.read_text(encoding="utf-8")
    formula = FORMULA.read_text(encoding="utf-8")
    osrs_formula = OSRS_FORMULA.read_text(encoding="utf-8")
    combat = COMBAT.read_text(encoding="utf-8")
    pvm = PVM_COMBAT.read_text(encoding="utf-8")
    projectile = PROJECTILE.read_text(encoding="utf-8")
    spell_handler = SPELL_HANDLER.read_text(encoding="utf-8")
    skills = SKILLS.read_text(encoding="utf-8")
    player = PLAYER.read_text(encoding="utf-8")

    for spell in ("FERVOR", "WARD", "ZEAL", "THORNS", "AEGIS", "RALLY"):
        require(f"spellId == ClericSpellId.{spell}" in casting,
                f"C10 cast whitelist omits {spell}")
        require(f"spellId == ClericSpellId.{spell}" in timed,
                f"C10 timed-state whitelist omits {spell}")

    for snippet in (
        "ClericEffectFamily.ACCURACY",
        "ClericEffectFamily.PROTECTION",
        "ClericEffectFamily.DAMAGE",
        "ClericEffectFamily.REFLECTION",
        "ClericEffectFamily.LIFESTEAL",
        "consumeCounter(",
        "accumulateFractionalPercent(",
        "ActionSender.sendActivePotionEffects(defender)",
        "Summoning.isSummon(attacker)",
        "attacker instanceof Player && defender instanceof Player",
        "!ClericSupportCasting.isPvpContext(player)",
    ):
        require(snippet in runtime, f"shared direct-effect runtime missing: {snippet}")

    require("rollPlayerCrit(source, attackMax) ? attackMax" in formula,
            "Fervor must not alter the normal critical-hit branch")
    require("combineFervorRollChance(source, existingChance)" in formula,
            "MyWorld damage rolls do not combine Fervor with equipment bias")
    require("doSecondaryMeleeDamage" in formula and "directAttack" in formula,
            "secondary melee lacks an explicit Fervor-free roll boundary")
    require("criticalHit" in osrs_formula and "!criticalHit" in osrs_formula,
            "OSRS compatibility formula does not exclude critical hits")
    require("doSecondaryMeleeDamage(player, npc)" in pvm,
            "scythe cleave is not using the secondary-damage boundary")

    for event_source, label in ((combat, "legacy melee"), (pvm, "PvM melee")):
        require("beforeDirectDamage(hitter, target, damage)" in event_source,
                f"{label} omits shared pre-damage effects")
        require("afterExistingLifesteal(hitter, target, damageDealt)" in event_source,
                f"{label} omits shared post-lifesteal effects")
        require("getPreventedDamage()" in event_source,
                f"{label} omits Cleric blocked-damage telemetry")
        require("inflictJewelryEffectDamage(target, hitter" in event_source,
                f"{label} bypasses the attributed terminal reflection path")

    require("isClericEligibleProjectileType()" in projectile,
            "projectile direct/secondary boundary is missing")
    cleric_types = projectile.split(
        "private boolean isClericEligibleProjectileType()", 1
    )[1].split("}", 1)[0]
    require("type == 1" in cleric_types and "type == 2" in cleric_types
            and "type == 4" in cleric_types and "type == 5" not in cleric_types,
            "Magic/ranged/Iban must qualify while cannon damage remains secondary")
    require("afterExistingLifesteal(" in projectile
            and "!deferClericRally" in projectile,
            "projectile post-hit effects or god-spell deferral are missing")
    require("secondaryEffectDamage = damage;" in projectile
            and "Math.ceil(secondaryEffectDamage / 2.0D)" in projectile,
            "Zeal-adjusted direct damage leaked into projectile secondary effects")
    require("inflictClericThornsDamage" in projectile
            and "caster.killedBy(opponent)" in projectile,
            "projectile Thorns lacks normal defender attribution")

    require("calculateSecondaryMagicDamage(caster, npc, secondaryMax)" in spell_handler,
            "god-spell area damage can inherit Fervor")
    require("calculateSecondaryMagicDamage(" in spell_handler
            and "SpellClassification.getSpellDamageCapPercent(Spells.IBAN_BLAST)"
            in spell_handler,
            "Iban area damage can inherit Fervor")
    deferred = spell_handler.split("godSpellEvent.deferClericRally();", 1)[1]
    require(deferred.index("applyGodSpellAreaEffects(")
            < deferred.index("godSpellEvent.resolveDeferredClericRally();"),
            "Rally must resolve after established god-spell lifesteal")

    require("levels[skill] > previousLevel" in skills
            and "((Player) mob).onHitsLevelIncreased();" in skills,
            "ordinary healing paths do not notify transient threshold state")
    require("getTransientEffectState().onHitsLevelIncreased(" in player
            and "ActionSender.sendActivePotionEffects(this);" in player,
            "Rally threshold cleanup does not refresh the authoritative HUD")
    require("getCache()" not in runtime and "addExperience" not in runtime,
            "C10 effects must remain transient and award no Worship XP")


def main() -> None:
    validate_runtime_wiring()
    run_compiled_fixture()
    print("Cleric C10 direct-combat effect checks passed")


if __name__ == "__main__":
    main()
