package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.runtime.CombatTraceObserver;
import com.openrsc.server.runtime.CombatTraceProfile;
import com.openrsc.server.runtime.CombatTraceRecord;

import java.util.List;

/** Executable contract for A11's opt-in, bounded combat diagnostics. */
final class CurrentCombatObservabilityCharacterization {
	private CurrentCombatObservabilityCharacterization() {
	}

	static void profilesRedactionAndBoundedRetention(
			final CurrentCombatHarness harness) {
		assertEquals(CombatTraceProfile.FULL,
			CombatTraceProfile.fromExternalValue(" full "),
			"validated full trace profile");
		assertRejectedProfile("damage-and-names");
		assertRejectedCapacity(0);
		assertRejectedCapacity(CombatTraceObserver.MAX_CAPACITY + 1);

		final CombatTraceObserver observer = new CombatTraceObserver(
			CombatTraceProfile.FULL, 2);
		final Player source = harness.player("trace source private", 880, 880);
		final Npc target = harness.npc(3, 881, 880);
		final DamageRequest safeRequest = DamageRequest.resolvedLegacy(source,
			target, DamageRequest.SourceCategory.ACTOR, "safe-effect", 2)
			.style(CombatStyle.MELEE).build();
		observer.onDamageObserved(DamageResult.observedCurrentPath(safeRequest,
			10, 8));
		final DamageRequest unsafeRequest = DamageRequest.resolvedLegacy(source,
			target, DamageRequest.SourceCategory.SCRIPT,
			"player trace source private", 10).build();
		observer.onDamageObserved(DamageResult.observedCurrentPath(unsafeRequest,
			8, 0));

		final List<CombatTraceRecord> records = observer.snapshot();
		assertEquals(2, records.size(), "bounded trace retains newest records");
		assertEquals(CombatTraceRecord.Reason.DAMAGE_SETTLED,
			records.get(0).getReason(), "terminal damage trace remains factual");
		assertEquals("redacted", records.get(0).getEffectKey(),
			"unsafe effect value is redacted");
		assertEquals(CombatTraceRecord.Reason.TARGET_HITS_DEPLETED,
			records.get(1).getReason(), "terminal lifecycle trace reason");
		assertEquals("redacted", records.get(1).getEffectKey(),
			"lifecycle record remains redacted");
		assertFalse(records.toString().contains("trace source private"),
			"trace records retain no player name");
		boolean immutable = false;
		try {
			records.clear();
		} catch (final UnsupportedOperationException expected) {
			immutable = true;
		}
		assertTrue(immutable, "trace snapshots cannot mutate recorder storage");
	}

	static void lifecycleProfileAndInstalledObserverRemainReadOnly(
			final CurrentCombatHarness harness)
			throws Exception {
		final CombatTraceObserver observer = new CombatTraceObserver(
			CombatTraceProfile.LIFECYCLE, 4);
		CurrentCombatCharacterizationTest.setDiagnosticObserver(harness, observer);
		try {
			final Player source = harness.player("trace installed source", 890, 880);
			final Npc target = harness.npc(3, 891, 880);
			target.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 5, 5,
				false);
			final DamageRequest request = DamageRequest.resolvedLegacy(source,
				target, DamageRequest.SourceCategory.ACTOR, "trace-installed", 7)
				.style(CombatStyle.MELEE).build();
			harness.server().getResolvedDamageTransaction().apply(request);

			assertEquals(0, target.getLevel(Skill.HITS.id()),
				"installed trace cannot alter authoritative Hits settlement");
			assertEquals(1, target.getUpdateFlags().getHitSplats().size(),
				"installed trace cannot alter presentation");
			final List<CombatTraceRecord> records = observer.snapshot();
			assertEquals(1, records.size(),
				"lifecycle-only profile omits ordinary damage trace");
			assertEquals(CombatTraceRecord.Reason.TARGET_HITS_DEPLETED,
				records.get(0).getReason(), "terminal transition is recorded");
			assertEquals(0, records.get(0).getHitsAfter(),
				"lifecycle trace records the immediate terminal Hits state");
		} finally {
			CurrentCombatCharacterizationTest.setDiagnosticObserver(harness, null);
		}
	}

	private static void assertRejectedProfile(final String value) {
		boolean rejected = false;
		try {
			CombatTraceProfile.fromExternalValue(value);
		} catch (final IllegalArgumentException expected) {
			rejected = true;
		}
		assertTrue(rejected, "unknown trace profile is rejected");
	}

	private static void assertRejectedCapacity(final int capacity) {
		boolean rejected = false;
		try {
			new CombatTraceObserver(CombatTraceProfile.DAMAGE, capacity);
		} catch (final IllegalArgumentException expected) {
			rejected = true;
		}
		assertTrue(rejected, "invalid trace capacity is rejected");
	}

	private static void assertEquals(final Object expected, final Object actual,
			final String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected
				+ " but was " + actual);
		}
	}

	private static void assertTrue(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(final boolean condition, final String message) {
		assertTrue(!condition, message);
	}
}
