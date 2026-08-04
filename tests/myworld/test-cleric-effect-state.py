#!/usr/bin/env python3
"""Compile and validate the inert C08A Cleric effect-state foundation."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLERIC_ROOT = ROOT / "server/src/com/openrsc/server/content/cleric"
EFFECT_ROOT = CLERIC_ROOT / "effect"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
PARTY = ROOT / "server/src/com/openrsc/server/content/party/Party.java"
PARTY_MANAGER = ROOT / "server/src/com/openrsc/server/content/party/PartyManager.java"
CASTING = CLERIC_ROOT / "runtime/ClericSupportCasting.java"
HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/InterfaceOptionHandler.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/cleric-spellbook-implementation-plan.md"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


FIXTURE = r"""
package com.openrsc.server.content.cleric.effect;

import com.openrsc.server.content.cleric.ClericSpellId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class ClericEffectStateFixture {
	private interface Action {
		void run();
	}

	private static final class FakeClock implements ClericEffectClock {
		private long nowNanos;
		private final long gameTickMilliseconds;

		FakeClock(long nowNanos, long gameTickMilliseconds) {
			this.nowNanos = nowNanos;
			this.gameTickMilliseconds = gameTickMilliseconds;
		}

		public long nanoTime() {
			return nowNanos;
		}

		public long getGameTickMilliseconds() {
			return gameTickMilliseconds;
		}

		void advanceMilliseconds(long milliseconds) {
			nowNanos = Math.addExact(nowNanos, Math.multiplyExact(milliseconds, 1_000_000L));
		}

		void setNanos(long nowNanos) {
			this.nowNanos = nowNanos;
		}
	}

	private static final class LiveOrigins implements ClericEffectOriginValidator {
		private final Set<ClericSessionToken> sessions = identitySet();
		private final Set<ClericPartyMembershipToken> memberships = identitySet();

		void add(ClericEffectOrigin origin) {
			sessions.add(origin.getCasterSession());
			memberships.add(origin.getCasterMembership());
			memberships.add(origin.getRecipientMembership());
		}

		void removeSession(ClericSessionToken session) {
			sessions.remove(session);
		}

		void addSession(ClericSessionToken session) {
			sessions.add(session);
		}

		void removeMembership(ClericPartyMembershipToken membership) {
			memberships.remove(membership);
		}

		void addMembership(ClericPartyMembershipToken membership) {
			memberships.add(membership);
		}

		public boolean isCurrent(ClericEffectOrigin origin) {
			return sessions.contains(origin.getCasterSession())
				&& memberships.contains(origin.getCasterMembership())
				&& memberships.contains(origin.getRecipientMembership());
		}
	}

	private static <T> Set<T> identitySet() {
		return Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void reject(Action action, String message) {
		try {
			action.run();
			throw new AssertionError("Expected rejection: " + message);
		} catch (IllegalArgumentException | IllegalStateException | ArithmeticException expected) {
			// Expected validation failure.
		}
	}

	private static ClericEffectOrigin origin(ClericPartyMembershipToken recipient) {
		return new ClericEffectOrigin(ClericSessionToken.issue(),
			ClericPartyMembershipToken.issue(), recipient);
	}

	private static ClericEffectRankDefinition<?> effect(ClericSpellId spell, int rank) {
		return ClericEffectCatalog.get(spell, rank);
	}

	private static void catalogChecks() {
		check(ClericEffectFamily.values().length == 7, "effect-family count drift");
		for (int code = 0; code < ClericEffectFamily.values().length; code++) {
			ClericEffectFamily family = ClericEffectFamily.fromCode(code);
			check(ClericEffectFamily.fromKey(family.getKey()) == family,
				"effect-family stable lookup drift");
		}
		check(ClericEffectCounterKind.fromCode(0) == ClericEffectCounterKind.NONE,
			"NONE counter code drift");
		check(ClericEffectCounterKind.fromCode(1) == ClericEffectCounterKind.CHARGES,
			"CHARGES counter code drift");
		check(ClericEffectCounterKind.fromCode(2) == ClericEffectCounterKind.PULSES,
			"PULSES counter code drift");

		check(ClericEffectCatalog.getAll().size() == 35, "timed rank total drift");
		for (ClericSpellId instant : Arrays.asList(
				ClericSpellId.UNIFY, ClericSpellId.PURIFY, ClericSpellId.RESTORE)) {
			check(!ClericEffectCatalog.hasTimedEffect(instant),
				"instant/movement spell gained timed state: " + instant);
			check(ClericEffectCatalog.getRanks(instant).isEmpty(),
				"instant/movement spell exposes timed ranks: " + instant);
		}

		int[] mend = {1, 2, 3};
		for (int rank = 1; rank <= mend.length; rank++) {
			ClericEffectRankDefinition<?> definition = effect(ClericSpellId.MEND, rank);
			ClericEffectMagnitudes.HealingPulse magnitude =
				(ClericEffectMagnitudes.HealingPulse) definition.getMagnitude();
			check(definition.getFamily() == ClericEffectFamily.HEALING_PULSES,
				"Mend family drift");
			check(definition.getCounterKind() == ClericEffectCounterKind.PULSES
				&& definition.getInitialCounter() == 3, "Mend pulse counter drift");
			check(magnitude.getHitsPerPulse() == mend[rank - 1]
				&& magnitude.getFirstDelayedPulseTicks() == 8
				&& magnitude.getSecondDelayedPulseTicks() == 16,
				"Mend magnitude/cadence drift");
			check(definition.getDuration().toNanos(640L) == 10_240_000_000L,
				"Mend tick-relative duration drift");
		}

		int[] greaterMend = {2, 3, 4, 5};
		int[] tacticalSeconds = {30, 45, 60, 90};
		int[] wardCharges = {2, 4, 6, 8};
		int[] aegisCharges = {1, 2, 3, 4};
		int[] fervor = {5, 10, 15, 20};
		int[] damage = {5, 8, 11, 15};
		int[] rallyThreshold = {55, 60, 65, 70};
		int[] respiteSpeed = {10, 15, 20, 25};
		int[] respiteMinutes = {5, 10, 15, 20};
		for (int rank = 1; rank <= 4; rank++) {
			ClericEffectRankDefinition<?> greater = effect(ClericSpellId.GREATER_MEND, rank);
			check(((ClericEffectMagnitudes.HealingPulse) greater.getMagnitude())
				.getHitsPerPulse() == greaterMend[rank - 1], "Greater Mend magnitude drift");
			check(greater.getFamilyPrecedence() == 2 && greater.getInitialCounter() == 3,
				"Greater Mend precedence/counter drift");

			ClericEffectRankDefinition<?> accuracy = effect(ClericSpellId.FERVOR, rank);
			ClericEffectMagnitudes.Accuracy accuracyMagnitude =
				(ClericEffectMagnitudes.Accuracy) accuracy.getMagnitude();
			check(accuracyMagnitude.getUpwardRollChancePercent() == fervor[rank - 1]
				&& accuracyMagnitude.getRollIncrease() == 1, "Fervor magnitude drift");

			ClericEffectRankDefinition<?> ward = effect(ClericSpellId.WARD, rank);
			check(((ClericEffectMagnitudes.Protection) ward.getMagnitude())
				.getReductionPercent() == 25 && ward.getInitialCounter() == wardCharges[rank - 1],
				"Ward magnitude/counter drift");

			ClericEffectRankDefinition<?> aegis = effect(ClericSpellId.AEGIS, rank);
			check(((ClericEffectMagnitudes.Protection) aegis.getMagnitude())
				.getReductionPercent() == 50 && aegis.getInitialCounter() == aegisCharges[rank - 1],
				"Aegis magnitude/counter drift");

			check(((ClericEffectMagnitudes.Damage) effect(ClericSpellId.ZEAL, rank)
				.getMagnitude()).getBonusPercent() == damage[rank - 1], "Zeal magnitude drift");
			check(((ClericEffectMagnitudes.Reflection) effect(ClericSpellId.THORNS, rank)
				.getMagnitude()).getReflectedPercent() == damage[rank - 1], "Thorns magnitude drift");

			ClericEffectMagnitudes.Lifesteal lifesteal =
				(ClericEffectMagnitudes.Lifesteal) effect(ClericSpellId.RALLY, rank).getMagnitude();
			check(lifesteal.getLifestealPercent() == 20
				&& lifesteal.getEndingHitsPercent() == rallyThreshold[rank - 1],
				"Rally magnitude drift");

			ClericEffectRankDefinition<?> respite = effect(ClericSpellId.RESPITE, rank);
			check(((ClericEffectMagnitudes.Regeneration) respite.getMagnitude())
				.getSpeedIncreasePercent() == respiteSpeed[rank - 1], "Respite magnitude drift");
			check(respite.getDuration().toNanos(640L)
				== respiteMinutes[rank - 1] * 60_000_000_000L, "Respite duration drift");

			for (ClericSpellId tactical : Arrays.asList(ClericSpellId.FERVOR,
					ClericSpellId.WARD, ClericSpellId.ZEAL, ClericSpellId.THORNS,
					ClericSpellId.AEGIS, ClericSpellId.RALLY)) {
				check(effect(tactical, rank).getDuration().toNanos(640L)
					== tacticalSeconds[rank - 1] * 1_000_000_000L,
					"tactical duration drift: " + tactical);
			}
		}

		try {
			ClericEffectCatalog.getAll().clear();
			throw new AssertionError("effect catalog must be immutable");
		} catch (UnsupportedOperationException expected) {
			// Expected immutable view.
		}
		reject(() -> ClericEffectCatalog.get(ClericSpellId.MEND, 4), "unknown Mend rank");
		reject(() -> ClericEffectCatalog.get(null, 1), "null spell identity");
		reject(() -> ClericEffectDuration.gameTicks(0), "zero duration");
		reject(() -> ClericEffectDuration.seconds(Long.MAX_VALUE), "duration overflow");
		reject(() -> ClericEffectClock.system(0), "zero game tick");
		reject(() -> ClericEffectFamily.fromCode(7), "unknown family code");
		reject(() -> ClericEffectCounterKind.fromCode(3), "unknown counter code");
		reject(() -> new ClericEffectMagnitudes.Accuracy(0, 1), "zero accuracy");
		reject(() -> new ClericEffectMagnitudes.Protection(100), "complete protection");
		reject(() -> new ClericEffectMagnitudes.HealingPulse(1, 8, 8), "duplicate pulse ticks");
	}

	private static void replacementAndCounterChecks() {
		FakeClock clock = new FakeClock(1_000_000L, 640L);
		ClericEffectRegistry registry = new ClericEffectRegistry(clock);
		ClericPartyMembershipToken recipient = ClericPartyMembershipToken.issue();
		ClericEffectOrigin firstCaster = origin(recipient);
		ClericEffectOrigin secondCaster = origin(recipient);
		LiveOrigins live = new LiveOrigins();
		live.add(firstCaster);
		live.add(secondCaster);

		check(registry.apply(effect(ClericSpellId.WARD, 4), firstCaster, live)
			== ClericEffectRegistry.ApplyResult.APPLIED, "initial Ward must apply");
		check(registry.consumeCounter(ClericEffectFamily.PROTECTION,
			ClericEffectCounterKind.CHARGES, live) == ClericEffectRegistry.CounterResult.CONSUMED,
			"Ward charge must consume");
		check(registry.get(ClericEffectFamily.PROTECTION, live).get().getRemainingCounter() == 7,
			"Ward charge decrement drift");

		clock.advanceMilliseconds(1_000L);
		check(registry.apply(effect(ClericSpellId.AEGIS, 4), secondCaster, live)
			== ClericEffectRegistry.ApplyResult.REPLACED,
			"Aegis must replace Ward regardless of Ward rank");
		ClericEffectEntry aegis = registry.get(ClericEffectFamily.PROTECTION, live).get();
		check(aegis.getDefinition().getSpellId() == ClericSpellId.AEGIS
			&& aegis.getRemainingCounter() == 4 && aegis.getOrigin() == secondCaster,
			"Aegis replacement must install its complete snapshot and origin");
		long firstAegisAppliedAt = aegis.getAppliedAtNanos();

		check(registry.apply(effect(ClericSpellId.WARD, 4), firstCaster, live)
			== ClericEffectRegistry.ApplyResult.REJECTED_WEAKER,
			"Ward must not overwrite Aegis");
		check(registry.get(ClericEffectFamily.PROTECTION, live).get().getOrigin() == secondCaster,
			"rejected weaker cast must not transfer origin");
		check(registry.consumeCounter(ClericEffectFamily.PROTECTION,
			ClericEffectCounterKind.CHARGES, live) == ClericEffectRegistry.CounterResult.CONSUMED,
			"Aegis charge must consume");

		clock.advanceMilliseconds(1_000L);
		check(registry.apply(effect(ClericSpellId.AEGIS, 4), firstCaster, live)
			== ClericEffectRegistry.ApplyResult.REFRESHED, "equal Aegis must refresh");
		ClericEffectEntry refreshed = registry.get(ClericEffectFamily.PROTECTION, live).get();
		check(refreshed.getRemainingCounter() == 4 && refreshed.getOrigin() == firstCaster
			&& refreshed.getAppliedAtNanos() > firstAegisAppliedAt,
			"refresh must reset, retime, and transfer origin without accumulation");
		check(registry.apply(effect(ClericSpellId.AEGIS, 3), secondCaster, live)
			== ClericEffectRegistry.ApplyResult.REJECTED_WEAKER,
			"lower Aegis rank must not overwrite stronger rank");

		ClericEffectRegistry healing = new ClericEffectRegistry(clock);
		check(healing.apply(effect(ClericSpellId.MEND, 3), firstCaster, live).isUseful(),
			"Mend must apply");
		check(healing.apply(effect(ClericSpellId.GREATER_MEND, 1), secondCaster, live)
			== ClericEffectRegistry.ApplyResult.REPLACED,
			"Greater Mend I must replace Mend III");
		check(healing.apply(effect(ClericSpellId.MEND, 3), firstCaster, live)
			== ClericEffectRegistry.ApplyResult.REJECTED_WEAKER,
			"Mend must not replace Greater Mend");

		ClericEffectRegistry allFamilies = new ClericEffectRegistry(clock);
		for (ClericSpellId spell : Arrays.asList(ClericSpellId.MEND, ClericSpellId.FERVOR,
				ClericSpellId.WARD, ClericSpellId.ZEAL, ClericSpellId.THORNS,
				ClericSpellId.RALLY, ClericSpellId.RESPITE)) {
			check(allFamilies.apply(effect(spell, 1), firstCaster, live).isUseful(),
				"different family must coexist: " + spell);
		}
		check(allFamilies.size(live) == 7, "registry must remain bounded to seven families");
		try {
			allFamilies.snapshot(live).clear();
			throw new AssertionError("registry snapshot must be immutable");
		} catch (UnsupportedOperationException expected) {
			// Expected immutable snapshot.
		}

		ClericEffectRegistry charges = new ClericEffectRegistry(clock);
		charges.apply(effect(ClericSpellId.WARD, 1), firstCaster, live);
		check(charges.consumeCounter(ClericEffectFamily.PROTECTION,
			ClericEffectCounterKind.PULSES, live) == ClericEffectRegistry.CounterResult.WRONG_KIND,
			"counter kind mismatch must fail closed");
		check(charges.consumeCounter(ClericEffectFamily.PROTECTION,
			ClericEffectCounterKind.CHARGES, live) == ClericEffectRegistry.CounterResult.CONSUMED,
			"first Ward charge drift");
		check(charges.consumeCounter(ClericEffectFamily.PROTECTION,
			ClericEffectCounterKind.CHARGES, live) == ClericEffectRegistry.CounterResult.EXHAUSTED,
			"final Ward charge must remove effect");
		check(charges.consumeCounter(ClericEffectFamily.PROTECTION,
			ClericEffectCounterKind.CHARGES, live)
			== ClericEffectRegistry.CounterResult.MISSING_OR_INVALID,
			"counter must not underflow after exhaustion");

		ClericEffectRegistry pulses = new ClericEffectRegistry(clock);
		pulses.apply(effect(ClericSpellId.MEND, 1), firstCaster, live);
		check(pulses.consumeCounter(ClericEffectFamily.HEALING_PULSES,
			ClericEffectCounterKind.PULSES, live) == ClericEffectRegistry.CounterResult.CONSUMED,
			"first Mend pulse drift");
		check(pulses.consumeCounter(ClericEffectFamily.HEALING_PULSES,
			ClericEffectCounterKind.PULSES, live) == ClericEffectRegistry.CounterResult.CONSUMED,
			"second Mend pulse drift");
		check(pulses.consumeCounter(ClericEffectFamily.HEALING_PULSES,
			ClericEffectCounterKind.PULSES, live) == ClericEffectRegistry.CounterResult.EXHAUSTED,
			"final Mend pulse must remove effect");
	}

	private static void expiryAndOriginChecks() {
		FakeClock clock = new FakeClock(0L, 640L);
		ClericEffectRegistry registry = new ClericEffectRegistry(clock);
		ClericPartyMembershipToken recipient = ClericPartyMembershipToken.issue();
		ClericEffectOrigin origin = origin(recipient);
		LiveOrigins live = new LiveOrigins();
		live.add(origin);

		registry.apply(effect(ClericSpellId.FERVOR, 1), origin, live);
		clock.advanceMilliseconds(29_999L);
		check(registry.size(live) == 1, "effect expired before exact deadline");
		clock.advanceMilliseconds(1L);
		check(registry.size(live) == 0, "effect must expire at exact deadline");
		clock.setNanos(0L);
		check(registry.size(live) == 0, "removed effect must never revive");

		registry.apply(effect(ClericSpellId.FERVOR, 1), origin, live);
		live.removeSession(origin.getCasterSession());
		check(registry.size(live) == 0, "caster relog/logout token invalidation drift");
		live.addSession(origin.getCasterSession());

		ClericEffectOrigin staleMembership = origin(ClericPartyMembershipToken.issue());
		live.add(staleMembership);
		registry.apply(effect(ClericSpellId.ZEAL, 1), staleMembership, live);
		live.removeMembership(staleMembership.getRecipientMembership());
		live.addMembership(ClericPartyMembershipToken.issue());
		check(registry.size(live) == 0, "same-party leave/rejoin must invalidate old effect");

		ClericEffectOrigin invalid = origin(ClericPartyMembershipToken.issue());
		check(registry.apply(effect(ClericSpellId.FERVOR, 1), invalid, live)
			== ClericEffectRegistry.ApplyResult.REJECTED_INVALID_ORIGIN,
			"invalid candidate origin must fail closed");
		check(registry.size(live) == 0, "invalid candidate must not mutate registry");

		FakeClock overflowClock = new FakeClock(Long.MAX_VALUE - 1L, 640L);
		ClericEffectRegistry overflow = new ClericEffectRegistry(overflowClock);
		reject(() -> overflow.apply(effect(ClericSpellId.FERVOR, 1), origin, live),
			"deadline overflow");
	}

	private static void lifecycleChecks() {
		FakeClock clock = new FakeClock(0L, 640L);
		ClericEffectOrigin departingOrigin = origin(ClericPartyMembershipToken.issue());
		ClericEffectOrigin otherOrigin = origin(ClericPartyMembershipToken.issue());
		LiveOrigins live = new LiveOrigins();
		live.add(departingOrigin);
		live.add(otherOrigin);

		ClericEffectRegistry departing = new ClericEffectRegistry(clock);
		ClericEffectRegistry firstRecipient = new ClericEffectRegistry(clock);
		ClericEffectRegistry secondRecipient = new ClericEffectRegistry(clock);
		departing.apply(effect(ClericSpellId.FERVOR, 1), otherOrigin, live);
		firstRecipient.apply(effect(ClericSpellId.FERVOR, 1), departingOrigin, live);
		firstRecipient.apply(effect(ClericSpellId.ZEAL, 1), otherOrigin, live);
		secondRecipient.apply(effect(ClericSpellId.RESPITE, 1), departingOrigin, live);

		int removed = ClericEffectLifecycle.endMembership(departing,
			departingOrigin.getCasterSession(), departingOrigin.getCasterMembership(),
			Arrays.asList(firstRecipient, firstRecipient, secondRecipient, departing));
		check(removed == 3, "membership cleanup must clear local and matching origin exactly once");
		check(departing.size(live) == 0, "departing recipient retained received effects");
		check(firstRecipient.size(live) == 1
			&& firstRecipient.get(ClericEffectFamily.DAMAGE, live).isPresent(),
			"membership cleanup removed another caster's effect");
		check(secondRecipient.size(live) == 0, "departing caster effect survived cleanup");
		check(ClericEffectLifecycle.endMembership(departing,
			departingOrigin.getCasterSession(), departingOrigin.getCasterMembership(),
			Arrays.asList(firstRecipient, secondRecipient)) == 0,
			"repeated membership cleanup must be idempotent");

		ClericEffectRegistry casterReceived = new ClericEffectRegistry(clock);
		ClericEffectRegistry livingRecipient = new ClericEffectRegistry(clock);
		casterReceived.apply(effect(ClericSpellId.FERVOR, 1), otherOrigin, live);
		livingRecipient.apply(effect(ClericSpellId.ZEAL, 1), departingOrigin, live);
		check(ClericEffectLifecycle.clearRecipient(casterReceived) == 1,
			"recipient death must clear local registry");
		check(livingRecipient.size(live) == 1,
			"caster death alone must not clear effects on living recipients");
		check(ClericEffectLifecycle.clearRecipient(casterReceived) == 0,
			"repeated recipient cleanup must be idempotent");
	}

	private static void concurrencyChecks() throws Exception {
		FakeClock clock = new FakeClock(0L, 640L);
		final ClericEffectRegistry registry = new ClericEffectRegistry(clock);
		final ClericEffectOrigin origin = origin(ClericPartyMembershipToken.issue());
		final LiveOrigins live = new LiveOrigins();
		live.add(origin);
		final CountDownLatch start = new CountDownLatch(1);
		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		List<Thread> threads = new ArrayList<Thread>();
		for (int threadIndex = 0; threadIndex < 4; threadIndex++) {
			Thread thread = new Thread(new Runnable() {
				public void run() {
					try {
						start.await();
						for (int iteration = 0; iteration < 500; iteration++) {
							registry.apply(effect(ClericSpellId.FERVOR, 4), origin, live);
							registry.snapshot(live);
						}
					} catch (Throwable throwable) {
						failure.compareAndSet(null, throwable);
					}
				}
			}, "cleric-effect-fixture-" + threadIndex);
			threads.add(thread);
			thread.start();
		}
		start.countDown();
		for (Thread thread : threads) {
			thread.join();
		}
		if (failure.get() != null) {
			throw new AssertionError("concurrent registry access failed", failure.get());
		}
		check(registry.size(live) == 1, "concurrent refresh must retain one family entry");
	}

	private static void validationChecks() {
		ClericEffectMagnitude magnitude = new ClericEffectMagnitudes.Damage(5);
		reject(() -> new ClericEffectRankDefinition<ClericEffectMagnitude>(
			ClericSpellId.ZEAL, ClericEffectFamily.DAMAGE, 0, 1,
			ClericEffectDuration.seconds(30), ClericEffectCounterKind.NONE, 0, magnitude),
			"zero rank");
		reject(() -> new ClericEffectRankDefinition<ClericEffectMagnitude>(
			ClericSpellId.ZEAL, ClericEffectFamily.DAMAGE, 1, 1,
			ClericEffectDuration.seconds(30), ClericEffectCounterKind.NONE, 1, magnitude),
			"counter on NONE");
		reject(() -> new ClericEffectRankDefinition<ClericEffectMagnitude>(
			ClericSpellId.WARD, ClericEffectFamily.PROTECTION, 1, 1,
			ClericEffectDuration.seconds(30), ClericEffectCounterKind.CHARGES, 0, magnitude),
			"missing charge count");
		reject(() -> new ClericEffectRankDefinition<ClericEffectMagnitude>(
			ClericSpellId.WARD, ClericEffectFamily.PROTECTION, 1, 1,
			ClericEffectDuration.seconds(30), ClericEffectCounterKind.CHARGES, 1, magnitude),
			"wrong typed magnitude");
		reject(() -> new ClericEffectRankDefinition<ClericEffectMagnitude>(
			ClericSpellId.PURIFY, ClericEffectFamily.DAMAGE, 1, 1,
			ClericEffectDuration.seconds(30), ClericEffectCounterKind.NONE, 0, magnitude),
			"instant spell timed state");
		reject(() -> new ClericEffectOrigin(null, ClericPartyMembershipToken.issue(),
			ClericPartyMembershipToken.issue()), "partial origin");
		reject(() -> new ClericEffectRegistry(null), "missing clock");
	}

	public static void main(String[] args) throws Exception {
		catalogChecks();
		replacementAndCounterChecks();
		expiryAndOriginChecks();
		lifecycleChecks();
		concurrencyChecks();
		validationChecks();
	}
}
"""


def validate_source_boundaries() -> None:
    effect_sources = sorted(EFFECT_ROOT.glob("*.java"))
    require(len(effect_sources) >= 12, "C08A typed effect package is incomplete")
    combined = "\n".join(path.read_text(encoding="utf-8") for path in effect_sources)
    lowered = combined.lower()
    for forbidden in (
        "getcache(", "playercache", "databaseconnection", "actionsender",
        "sendactivepotioneffects", "clericsupportcasting",
    ):
        require(forbidden not in lowered, f"effect foundation crossed inert boundary: {forbidden}")

    player = PLAYER.read_text(encoding="utf-8")
    require("private final ClericSessionToken clericSessionToken" in player,
            "Player lacks process-local Cleric session identity")
    require("private final ClericEffectRegistry clericEffectRegistry" in player,
            "Player lacks recipient-owned Cleric registry")
    require("ClericPartyMembershipToken.issue()" in player,
            "party membership does not issue a new opaque generation")
    require("ClericEffectLifecycle.endMembership(clericEffectRegistry" in player,
            "party transition does not use centralized Cleric cleanup")

    death = player.split("public void killedBy(final Mob mob)", 1)[1].split(
        "private int getEquippedWeaponID", 1
    )[0]
    tutorial_return = death.index("skipTutorial();")
    death_clear = death.index("ClericEffectLifecycle.clearRecipient(clericEffectRegistry);")
    death_record = death.index('getCache().store("last_death"')
    require(tutorial_return < death_clear < death_record,
            "real-death cleanup must follow tutorial pseudo-death and precede death processing")

    logout = player.split("public void logout()", 1)[1].split(
        "public void sendMemberErrorMessage", 1
    )[0]
    require(logout.index("ClericEffectLifecycle.clearRecipient(clericEffectRegistry);")
            < logout.index("getParty().removePlayer"),
            "logout must clear received effects before party departure")

    set_party = player.split("public void setParty(final Party party)", 1)[1].split(
        "public PartyInvite getActivePartyInvite", 1
    )[0]
    require(set_party.index("ClericEffectLifecycle.endMembership")
            < set_party.index("this.party = party"),
            "membership cleanup must precede party transition")
    require("caster.isLoggedIn() && !caster.isUnregistering()" in set_party,
            "defensive origin validation does not reject stale caster sessions")
    require("caster.getParty() == party" in set_party,
            "defensive origin validation does not require current shared party")

    set_party_calls = []
    for source_root in (ROOT / "server/src", ROOT / "server/plugins"):
        for path in source_root.rglob("*.java"):
            text = path.read_text(encoding="utf-8")
            if ".setParty(" in text or "void setParty(" in text:
                set_party_calls.append(path.relative_to(ROOT).as_posix())
    require(sorted(set(set_party_calls)) == sorted({
        "server/src/com/openrsc/server/content/party/Party.java",
        "server/src/com/openrsc/server/content/party/PartyManager.java",
        "server/src/com/openrsc/server/model/entity/player/Player.java",
    }), f"unexpected party-assignment boundary bypass: {set_party_calls}")

    party = PARTY.read_text(encoding="utf-8")
    manager = PARTY_MANAGER.read_text(encoding="utf-8")
    remove_player = party.split("public void removePlayer(String username)", 1)[1].split(
        "public void updateRankPlayer", 1
    )[0]
    require("Player player = member.getPlayerReference();" in remove_player
            and "if (player != null)" in remove_player
            and "player.setParty(null);" in remove_player,
            "leave/kick/logout path no longer converges on Player.setParty")
    require(remove_player.index("player.setParty(null);")
            < remove_player.index("getPlayers().remove(member)"),
            "membership cleanup must precede party-list removal")
    require("player.setParty(p)" in manager,
            "login party reattachment no longer converges on Player.setParty")

    casting = CASTING.read_text(encoding="utf-8")
    handler = HANDLER.read_text(encoding="utf-8")
    require("definition.getId() != ClericSpellId.UNIFY" in casting,
            "C08A exposed a non-Unify Cleric effect")
    require("ClericEffectCatalog" not in casting and "ClericEffectRegistry" not in casting,
            "C08A casting path can populate the new registry")
    require("ClericEffectCatalog" not in handler and "ClericEffectRegistry" not in handler,
            "C08A request handler can populate the new registry")

    production_catalog_references = []
    for path in (ROOT / "server").rglob("*.java"):
        if EFFECT_ROOT in path.parents:
            continue
        if "ClericEffectCatalog" in path.read_text(encoding="utf-8"):
            production_catalog_references.append(path.relative_to(ROOT).as_posix())
    require(not production_catalog_references,
            f"effect catalog escaped inert C08A package: {production_catalog_references}")

    plan = PLAN.read_text(encoding="utf-8")
    require("C08A — `feat/cleric-effect-state-foundation`" in plan,
            "C08A branch boundary missing from implementation plan")
    require("The production registry must remain empty" in plan,
            "C08A non-exposure stop condition is undocumented")


def run_compiled_fixture() -> None:
    sources = sorted(str(path) for path in CLERIC_ROOT.glob("*.java"))
    sources.extend(sorted(str(path) for path in EFFECT_ROOT.glob("*.java")))
    with tempfile.TemporaryDirectory(prefix="cleric-c08a-") as temporary:
        temp = Path(temporary)
        fixture = temp / "com/openrsc/server/content/cleric/effect/ClericEffectStateFixture.java"
        fixture.parent.mkdir(parents=True)
        fixture.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        classes = temp / "classes"
        classes.mkdir()
        subprocess.run(["javac", "-d", str(classes), *sources, str(fixture)],
                       cwd=ROOT, check=True)
        subprocess.run([
            "java", "-cp", str(classes),
            "com.openrsc.server.content.cleric.effect.ClericEffectStateFixture",
        ], cwd=ROOT, check=True)


def main() -> None:
    validate_source_boundaries()
    run_compiled_fixture()
    print("Cleric C08A typed effect state and lifecycle checks passed")


if __name__ == "__main__":
    main()
