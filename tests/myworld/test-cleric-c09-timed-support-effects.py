#!/usr/bin/env python3
"""Compile and validate C09 Mend-family and Respite mechanics."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server/src/com/openrsc/server"
CLERIC = SERVER / "content/cleric"
EFFECT = CLERIC / "effect"
RUNTIME = CLERIC / "runtime/ClericSupportCasting.java"
TIMED_RUNTIME = CLERIC / "runtime/ClericTimedEffectRuntime.java"
PULSE_EVENT = CLERIC / "runtime/ClericHealingPulseEvent.java"
RESTORATION = SERVER / "event/rsc/impl/StatRestorationEvent.java"
NATURAL_REGEN = SERVER / "event/rsc/impl/NaturalHitsRegeneration.java"
PLAYER = SERVER / "model/entity/player/Player.java"
PLAYER_EFFECT = SERVER / "model/entity/player"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


FIXTURE = r"""
package com.openrsc.server.content.cleric.effect;

import com.openrsc.server.content.cleric.ClericCastTransaction;
import com.openrsc.server.content.cleric.ClericHealingPulseEffect;
import com.openrsc.server.content.cleric.ClericSpellCatalog;
import com.openrsc.server.content.cleric.ClericSpellDefinition;
import com.openrsc.server.content.cleric.ClericSpellId;
import com.openrsc.server.event.rsc.impl.NaturalHitsRegeneration;
import com.openrsc.server.model.entity.player.TransientEffectMembershipToken;
import com.openrsc.server.model.entity.player.TransientEffectSessionToken;

import java.util.Collections;

public final class ClericC09TimedSupportFixture {
	private static final class FakeClock implements ClericEffectClock {
		private long now;
		public long nanoTime() { return now; }
		public long getGameTickMilliseconds() { return 640L; }
		void advanceTicks(long ticks) { now += ticks * 640_000_000L; }
	}

	private static final class LiveOrigin implements ClericEffectOriginValidator {
		private final ClericEffectOrigin origin;
		private boolean current = true;
		LiveOrigin(ClericEffectOrigin origin) { this.origin = origin; }
		public boolean isCurrent(ClericEffectOrigin candidate) {
			return current && candidate == origin;
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static ClericEffectOrigin origin() {
		return new ClericEffectOrigin(TransientEffectSessionToken.issue(),
			TransientEffectMembershipToken.issue(),
			TransientEffectMembershipToken.issue());
	}

	private static ClericEffectRankDefinition<?> effect(ClericSpellId spell, int rank) {
		return ClericEffectCatalog.get(spell, rank);
	}

	private static void healingClampChecks() {
		check(ClericHealingPulseEffect.healedHits(90, 99, 5) == 5,
			"ordinary Mend healing drift");
		check(ClericHealingPulseEffect.healedHits(98, 99, 5) == 1,
			"Mend must clamp at the healing ceiling");
		check(ClericHealingPulseEffect.healedHits(99, 109, 5) == 5,
			"temporary maximum Hits must remain a valid healing ceiling");
		check(ClericHealingPulseEffect.healedHits(109, 109, 5) == 0,
			"full-health pulses must be harmless but consumable");
		check(ClericHealingPulseEffect.healedHits(Integer.MAX_VALUE - 1,
			Integer.MAX_VALUE, 5) == 1, "Mend healing arithmetic overflowed");
	}

	private static void mendRegistryChecks() {
		FakeClock clock = new FakeClock();
		ClericEffectRegistry registry = new ClericEffectRegistry(clock);
		ClericEffectOrigin origin = origin();
		LiveOrigin live = new LiveOrigin(origin);

		ClericEffectRankDefinition<?> mendTwo = effect(ClericSpellId.MEND, 2);
		check(registry.preview(mendTwo, origin, live)
			== ClericEffectRegistry.ApplyResult.APPLIED,
			"new Mend must preflight as useful");
		check(registry.size(live) == 0, "Mend preflight must be side-effect free");
		check(registry.apply(mendTwo, origin, live)
			== ClericEffectRegistry.ApplyResult.APPLIED, "Mend must apply");
		check(registry.consumeCounter(ClericEffectFamily.HEALING_PULSES,
			ClericEffectCounterKind.PULSES, live)
			== ClericEffectRegistry.CounterResult.CONSUMED,
			"immediate Mend pulse must leave two delayed pulses");
		check(registry.get(ClericEffectFamily.HEALING_PULSES, live)
			.get().getRemainingCounter() == 2, "immediate pulse counter drift");

		clock.advanceTicks(8);
		check(registry.consumeCounter(ClericEffectFamily.HEALING_PULSES,
			ClericEffectCounterKind.PULSES, live)
			== ClericEffectRegistry.CounterResult.CONSUMED,
			"tick-eight Mend pulse must continue");
		clock.advanceTicks(8);
		check(registry.get(ClericEffectFamily.HEALING_PULSES, live).isPresent(),
			"tick-sixteen Mend pulse expired at its own endpoint");
		check(registry.consumeCounter(ClericEffectFamily.HEALING_PULSES,
			ClericEffectCounterKind.PULSES, live)
			== ClericEffectRegistry.CounterResult.EXHAUSTED,
			"tick-sixteen Mend pulse must exhaust the sequence");
		check(!registry.get(ClericEffectFamily.HEALING_PULSES, live).isPresent(),
			"completed Mend sequence must leave no status");

		check(registry.apply(effect(ClericSpellId.MEND, 3), origin, live)
			== ClericEffectRegistry.ApplyResult.APPLIED, "Mend III must apply");
		registry.consumeCounter(ClericEffectFamily.HEALING_PULSES,
			ClericEffectCounterKind.PULSES, live);
		check(registry.apply(effect(ClericSpellId.GREATER_MEND, 1), origin, live)
			== ClericEffectRegistry.ApplyResult.REPLACED,
			"Greater Mend must replace Mend regardless of numerical rank");
		check(registry.get(ClericEffectFamily.HEALING_PULSES, live)
			.get().getRemainingCounter() == 3,
			"Greater Mend replacement must restart all three pulses");
		check(registry.preview(effect(ClericSpellId.MEND, 3), origin, live)
			== ClericEffectRegistry.ApplyResult.REJECTED_WEAKER,
			"Mend must not replace Greater Mend");
		check(registry.apply(effect(ClericSpellId.GREATER_MEND, 1), origin, live)
			== ClericEffectRegistry.ApplyResult.REFRESHED,
			"equal Greater Mend must refresh");
		check(registry.get(ClericEffectFamily.HEALING_PULSES, live)
			.get().getRemainingCounter() == 3,
			"refresh must restart rather than add pulse counters");
	}

	private static void expiredPreviewChecks() {
		FakeClock clock = new FakeClock();
		ClericEffectRegistry registry = new ClericEffectRegistry(clock);
		ClericEffectOrigin origin = origin();
		LiveOrigin live = new LiveOrigin(origin);
		registry.apply(effect(ClericSpellId.MEND, 1), origin, live);
		clock.advanceTicks(18);
		check(registry.preview(effect(ClericSpellId.MEND, 1), origin, live)
			== ClericEffectRegistry.ApplyResult.APPLIED,
			"expired Mend must preflight as a new useful effect");
		check(registry.clearOriginatingFrom(origin.getCasterSession(),
			origin.getCasterMembership()) == 1,
			"side-effect-free preview must not purge the old entry");
	}

	private static void respiteChecks() {
		int[] speeds = {10, 15, 20, 25};
		int[] minutes = {5, 10, 15, 20};
		for (int rank = 1; rank <= 4; rank++) {
			ClericEffectRankDefinition<?> definition = effect(ClericSpellId.RESPITE, rank);
			ClericEffectMagnitudes.Regeneration magnitude =
				(ClericEffectMagnitudes.Regeneration) definition.getMagnitude();
			check(magnitude.getSpeedIncreasePercent() == speeds[rank - 1],
				"Respite speed rank drift");
			check(definition.getDuration().toNanos(640L)
				== minutes[rank - 1] * 60_000_000_000L,
				"Respite duration rank drift");
		}
		check(NaturalHitsRegeneration.applySpeedBonus(64_000L, 640L, 0.25D)
			== 51_200L, "rank-IV Respite interval drift");
		long composed = NaturalHitsRegeneration.applySpeedBonus(
			NaturalHitsRegeneration.applySpeedBonus(64_000L, 640L, 1.0D),
			640L, 0.25D);
		check(composed == 25_600L,
			"Respite and potion factors must compose multiplicatively");

		FakeClock clock = new FakeClock();
		ClericEffectRegistry registry = new ClericEffectRegistry(clock);
		ClericEffectOrigin origin = origin();
		LiveOrigin live = new LiveOrigin(origin);
		check(registry.apply(effect(ClericSpellId.RESPITE, 1), origin, live)
			== ClericEffectRegistry.ApplyResult.APPLIED,
			"Respite must apply even without missing Hits");
		check(registry.apply(effect(ClericSpellId.MEND, 1), origin, live)
			== ClericEffectRegistry.ApplyResult.APPLIED && registry.size(live) == 2,
			"Respite and Mend must coexist in independent families");
	}

	private static void thresholdAndTransactionChecks() {
		int[] mendRanks = {1, 1, 2, 2, 3};
		int[] mendPower = {0, 11, 12, 27, 28};
		ClericSpellDefinition mend = ClericSpellCatalog.get(ClericSpellId.MEND);
		for (int index = 0; index < mendPower.length; index++) {
			check(mend.resolveEffectRank(mendPower[index]) == mendRanks[index],
				"Mend Holy Power threshold drift");
		}
		ClericSpellDefinition greater = ClericSpellCatalog.get(ClericSpellId.GREATER_MEND);
		ClericSpellDefinition respite = ClericSpellCatalog.get(ClericSpellId.RESPITE);
		for (ClericSpellDefinition spell : new ClericSpellDefinition[] {greater, respite}) {
			check(spell.resolveEffectRank(0) == 1 && spell.resolveEffectRank(24) == 2
				&& spell.resolveEffectRank(44) == 3 && spell.resolveEffectRank(64) == 4,
				"tier-two Holy Power threshold drift: " + spell.getId());
		}

		final int[] commits = {0};
		ClericCastTransaction.PreparedApplication useful =
			new ClericCastTransaction.PreparedApplication() {
				public boolean isUseful() { return true; }
				public void commit() { commits[0]++; }
			};
		ClericCastTransaction.Result missing = ClericCastTransaction.execute(
			Collections.singletonList(useful), commit -> false);
		check(missing.getOutcome() == ClericCastTransaction.Outcome.INSUFFICIENT_RESOURCES
			&& commits[0] == 0, "missing sigils must install no timed effect or pulse");
	}

	public static void main(String[] args) {
		healingClampChecks();
		mendRegistryChecks();
		expiredPreviewChecks();
		respiteChecks();
		thresholdAndTransactionChecks();
	}
}
"""


def validate_runtime_wiring() -> None:
    casting = RUNTIME.read_text(encoding="utf-8")
    runtime = TIMED_RUNTIME.read_text(encoding="utf-8")
    pulse_event = PULSE_EVENT.read_text(encoding="utf-8")
    restoration = RESTORATION.read_text(encoding="utf-8")
    player = PLAYER.read_text(encoding="utf-8")

    for spell in ("MEND", "GREATER_MEND", "RESPITE"):
        require(f"spellId == ClericSpellId.{spell}" in casting,
                f"C09 runtime whitelist omits {spell}")
    for snippet in (
        "ClericTimedEffectRuntime.prepare(",
        "caster, target, definition, effectRank",
        "ClericCastTransaction.execute(",
        "ClericSupportTargeting.resolve(",
    ):
        require(snippet in casting, f"timed C09 cast wiring missing: {snippet}")

    for snippet in (
        "ClericEffectCatalog.get(spell.getId(), effectRank)",
        "ClericEffectOrigins.current(caster, recipient)",
        "attachment.registry.preview(",
        "recipient.installTransientEffectState(attachment.registry)",
        "attachment.registry.apply(",
        "pulseHealing(recipient)",
        "ClericHealingPulseEvent.restart(recipient)",
        "ActionSender.sendActivePotionEffects(recipient)",
        "recipient.getHealingMaximumHits()",
    ):
        require(snippet in runtime, f"timed C09 registry wiring missing: {snippet}")
    require("getCache()" not in runtime and "addExperience" not in runtime,
            "timed Cleric effects must remain transient and award no Worship XP")
    require("PULSE_INTERVAL_TICKS = 8" in pulse_event
            and "DuplicationStrategy.ONE_PER_MOB" in pulse_event
            and "event.resetCountdown()" in pulse_event,
            "Mend pulse event must be unique and restart its eight-tick cadence")
    require("ClericTimedEffectRuntime.pulseHealing(recipient)" in pulse_event,
            "delayed Mend pulses bypass the shared pulse authority")

    require("ClericTimedEffectRuntime.getRespiteSpeedBonus(player)" in restoration,
            "natural healing does not consume the validated Respite rank")
    interval = restoration.split("long getNaturalHitsInterval", 1)[1].split(
        "private void normalizeLevel", 1
    )[0]
    require(interval.rfind("respiteSpeedBonus") > interval.find(
        "getBodyAmuletRegenSpeedBonus"),
        "Respite must remain an independently composed regeneration factor")
    require("lastHitRestoration" not in runtime and "normalizeLevel" not in runtime,
            "Respite application must not reset or directly invoke natural healing")

    require("clearTransientEffectsAndRefreshStatus();" in player
            and "ActionSender.sendActivePotionEffects(recipient);" in player,
            "death/logout/party cleanup must refresh affected maintained HUDs")


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
    sources.append(str(NATURAL_REGEN))
    with tempfile.TemporaryDirectory(prefix="cleric-c09-timed-") as temporary:
        temp = Path(temporary)
        fixture = temp / (
            "com/openrsc/server/content/cleric/effect/ClericC09TimedSupportFixture.java"
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
             "com.openrsc.server.content.cleric.effect.ClericC09TimedSupportFixture"],
            cwd=ROOT,
            check=True,
        )


def main() -> None:
    validate_runtime_wiring()
    run_compiled_fixture()
    print("Cleric C09 Mend, Greater Mend, and Respite checks passed")


if __name__ == "__main__":
    main()
