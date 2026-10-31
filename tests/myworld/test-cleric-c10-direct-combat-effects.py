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

	private static void catalogChecks() {
		int[] seconds = {30, 45, 60, 90};
		int[] fervor = {5, 10, 15, 20};
		int[] percentage = {5, 8, 11, 15};
		int[] wardCharges = {2, 4, 6, 8};
		int[] aegisCharges = {1, 2, 3, 4};
		int[] rallyThresholds = {55, 60, 65, 70};
		for (int rank = 1; rank <= 4; rank++) {
			ClericEffectRankDefinition<?> accuracy =
				LegacyClericEffectFixtureCatalog.get(ClericSpellId.FERVOR, rank);
			check(((ClericEffectMagnitudes.Accuracy) accuracy.getMagnitude())
				.getUpwardRollChancePercent() == fervor[rank - 1],
				"Fervor rank drift");
			ClericEffectRankDefinition<?> ward =
				LegacyClericEffectFixtureCatalog.get(ClericSpellId.WARD, rank);
			check(((ClericEffectMagnitudes.Protection) ward.getMagnitude())
				.getReductionPercent() == 25
				&& ward.getInitialCounter() == wardCharges[rank - 1],
				"Ward rank drift");
			ClericEffectRankDefinition<?> zeal =
				LegacyClericEffectFixtureCatalog.get(ClericSpellId.ZEAL, rank);
			check(((ClericEffectMagnitudes.Damage) zeal.getMagnitude())
				.getBonusPercent() == percentage[rank - 1], "Zeal rank drift");
			ClericEffectRankDefinition<?> thorns =
				LegacyClericEffectFixtureCatalog.get(ClericSpellId.THORNS, rank);
			check(((ClericEffectMagnitudes.Reflection) thorns.getMagnitude())
				.getReflectedPercent() == percentage[rank - 1], "Thorns rank drift");
			ClericEffectRankDefinition<?> aegis =
				LegacyClericEffectFixtureCatalog.get(ClericSpellId.AEGIS, rank);
			check(((ClericEffectMagnitudes.Protection) aegis.getMagnitude())
				.getReductionPercent() == 50
				&& aegis.getInitialCounter() == aegisCharges[rank - 1],
				"Aegis rank drift");
			ClericEffectRankDefinition<?> rally =
				LegacyClericEffectFixtureCatalog.get(ClericSpellId.RALLY, rank);
			ClericEffectMagnitudes.Lifesteal rallyMagnitude =
				(ClericEffectMagnitudes.Lifesteal) rally.getMagnitude();
			check(rallyMagnitude.getLifestealPercent() == 20
				&& rallyMagnitude.getEndingHitsPercent() == rallyThresholds[rank - 1],
				"Rally rank drift");
			for (ClericEffectRankDefinition<?> definition : new ClericEffectRankDefinition<?>[] {
				accuracy, ward, zeal, thorns, aegis, rally
			}) {
				check(definition.getDuration().toNanos(640L)
					== seconds[rank - 1] * 1_000_000_000L,
					"C10 tactical duration drift: " + definition.getSpellId());
			}
		}
	}

	private static void fractionalCarryChecks() {
		FakeClock clock = new FakeClock();
		ClericEffectOrigin origin = origin();
		LiveOrigin live = new LiveOrigin(origin);
		ClericEffectRegistry registry = new ClericEffectRegistry(clock);
		ClericEffectRankDefinition<?> zeal = LegacyClericEffectFixtureCatalog.get(ClericSpellId.ZEAL, 1);
		registry.apply(zeal, origin, live);
		for (int hit = 1; hit < 20; hit++) {
			check(registry.accumulateFractionalPercent(ClericEffectFamily.DAMAGE,
				ClericSpellId.ZEAL, zeal, 1, live) == 0,
				"Zeal rounded a small hit upward at hit " + hit);
		}
		check(registry.accumulateFractionalPercent(ClericEffectFamily.DAMAGE,
			ClericSpellId.ZEAL, zeal, 1, live) == 1,
			"Zeal did not release one whole accumulated point");

		for (int hit = 0; hit < 19; hit++) {
			registry.accumulateFractionalPercent(ClericEffectFamily.DAMAGE,
				ClericSpellId.ZEAL, zeal, 1, live);
		}
		registry.apply(zeal, origin, live);
		check(registry.accumulateFractionalPercent(ClericEffectFamily.DAMAGE,
			ClericSpellId.ZEAL, zeal, 1, live) == 0,
			"equal-rank refresh retained old Zeal fraction");

		ClericEffectRankDefinition<?> zealTwo = LegacyClericEffectFixtureCatalog.get(ClericSpellId.ZEAL, 2);
		registry.apply(zealTwo, origin, live);
		check(registry.accumulateFractionalPercent(ClericEffectFamily.DAMAGE,
			ClericSpellId.ZEAL, zeal, 1, live) == 0,
			"stale definition consumed a replacement's Zeal fraction");
		check(registry.accumulateFractionalPercent(ClericEffectFamily.DAMAGE,
			ClericSpellId.ZEAL, zealTwo, Integer.MAX_VALUE, live)
			== (int) ((long) Integer.MAX_VALUE * 8L / 100L),
			"maximum Zeal fractional input overflowed");

		ClericEffectRegistry rallyRegistry = new ClericEffectRegistry(clock);
		ClericEffectRankDefinition<?> rally = LegacyClericEffectFixtureCatalog.get(ClericSpellId.RALLY, 1);
		rallyRegistry.apply(rally, origin, live);
		for (int hit = 0; hit < 4; hit++) {
			check(rallyRegistry.accumulateFractionalPercent(ClericEffectFamily.LIFESTEAL,
				ClericSpellId.RALLY, rally, 1, live) == 0,
				"Rally invented a minimum heal");
		}
		check(rallyRegistry.accumulateFractionalPercent(ClericEffectFamily.LIFESTEAL,
			ClericSpellId.RALLY, rally, 1, live) == 1,
			"Rally did not release one whole accumulated heal");
		check(rallyRegistry.onHitsLevelIncreased(54, 100) == 0,
			"Rally ended before its authored threshold");
		check(rallyRegistry.onHitsLevelIncreased(55, 100) == 1,
			"Rally did not end when ordinary healing reached its threshold");
		check(rallyRegistry.onHitsLevelIncreased(56, 100) == 0,
			"Rally threshold cleanup must be idempotent");

		rallyRegistry.apply(rally, origin, live);
		rallyRegistry.accumulateFractionalPercent(ClericEffectFamily.LIFESTEAL,
			ClericSpellId.RALLY, rally, 4, live);
		clock.advanceSeconds(31);
		check(rallyRegistry.size(live) == 0, "expired Rally state remained active");
		rallyRegistry.apply(rally, origin, live);
		check(rallyRegistry.accumulateFractionalPercent(ClericEffectFamily.LIFESTEAL,
			ClericSpellId.RALLY, rally, 1, live) == 0,
			"expired Rally fraction leaked into a new effect");
		clock.advanceSeconds(31);
		check(rallyRegistry.onHitsLevelIncreased(1, 100) == 1,
			"ordinary healing did not retire an already expired Rally");
	}

	private static void protectionCounterChecks() {
		FakeClock clock = new FakeClock();
		ClericEffectOrigin origin = origin();
		LiveOrigin live = new LiveOrigin(origin);
		ClericEffectRegistry registry = new ClericEffectRegistry(clock);
		ClericEffectRankDefinition<?> ward = LegacyClericEffectFixtureCatalog.get(ClericSpellId.WARD, 1);
		ClericEffectRankDefinition<?> aegis = LegacyClericEffectFixtureCatalog.get(ClericSpellId.AEGIS, 1);
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
		catalogChecks();
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
    sources.extend(str(SERVER / "runtime" / name) for name in (
        "GameClock.java",
        "SystemGameClock.java",
    ))
    sources.extend(str(path) for path in (ROOT / "tests/myworld/fixtures").rglob("LegacyCleric*FixtureCatalog.java"))
    classpath = str(ROOT / "server/core.jar") + ":" + str(ROOT / "server/lib/*")
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
            ["javac", "-cp", classpath, "-d", str(classes), *sources, str(fixture)],
            cwd=ROOT,
            check=True,
        )
        subprocess.run(
            ["java", "-cp", str(classes) + ":" + classpath,
             "com.openrsc.server.content.cleric.effect.ClericC10DirectCombatFixture"],
            cwd=ROOT,
            check=True,
        )


def validate_runtime_wiring() -> None:
    """Legacy fixture data below characterizes generic math/counters only; active acceptance is composite."""
    runtime = (ROOT / "server/src/com/openrsc/server/content/cleric/runtime/ClericTimedEffectRuntime.java").read_text()
    registry = (ROOT / "server/src/com/openrsc/server/content/cleric/effect/ClericEffectRegistry.java").read_text()
    assert "spell.getId().isBlessing()" in runtime
    assert "nextPeriodic" in registry and "accumulateComponentPercent" in registry
    assert "event.resetCountdown();" not in (ROOT / "server/src/com/openrsc/server/content/cleric/runtime/ClericHealingPulseEvent.java").read_text()


def main() -> None:
    validate_runtime_wiring()
    run_compiled_fixture()
    subprocess.run(["python3", str(ROOT / "tests/myworld/test-cleric-blessings.py")], check=True)
    print("Cleric historical arithmetic and active composite direct-combat checks passed")


if __name__ == "__main__":
    main()
