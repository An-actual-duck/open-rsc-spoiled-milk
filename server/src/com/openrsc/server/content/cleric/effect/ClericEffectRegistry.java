package com.openrsc.server.content.cleric.effect;

import com.openrsc.server.model.entity.player.TransientEffectMembershipToken;
import com.openrsc.server.model.entity.player.TransientEffectSessionToken;
import com.openrsc.server.model.entity.player.TransientEffectState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Thread-safe, bounded, recipient-owned authority for transient Cleric effects. */
public final class ClericEffectRegistry implements TransientEffectState {
	public enum ApplyResult {
		APPLIED(true),
		REPLACED(true),
		REFRESHED(true),
		REJECTED_WEAKER(false),
		REJECTED_INVALID_ORIGIN(false);

		private final boolean useful;

		ApplyResult(boolean useful) {
			this.useful = useful;
		}

		public boolean isUseful() {
			return useful;
		}
	}

	public enum CounterResult {
		CONSUMED,
		EXHAUSTED,
		MISSING_OR_INVALID,
		WRONG_KIND
	}

	private final ClericEffectClock clock;
	private final EnumMap<ClericEffectFamily, ClericEffectEntry> entries =
		new EnumMap<ClericEffectFamily, ClericEffectEntry>(ClericEffectFamily.class);

	public ClericEffectRegistry(ClericEffectClock clock) {
		if (clock == null) {
			throw new IllegalArgumentException("Cleric effect clock is required");
		}
		this.clock = clock;
	}

	public synchronized ApplyResult apply(
			ClericEffectRankDefinition<? extends ClericEffectMagnitude> definition,
			ClericEffectOrigin origin, ClericEffectOriginValidator validator) {
		requireDefinitionOriginAndValidator(definition, origin, validator);
		long nowNanos = clock.nanoTime();
		purgeInvalidLocked(nowNanos, validator);
		if (!validator.isCurrent(origin)) {
			return ApplyResult.REJECTED_INVALID_ORIGIN;
		}

		ClericEffectEntry existing = entries.get(definition.getFamily());
		ApplyResult result = classify(definition, existing);
		if (!result.isUseful()) {
			return result;
		}

		long durationNanos = definition.getDuration().toNanos(clock.getGameTickMilliseconds());
		final long expiresAtNanos;
		try {
			expiresAtNanos = Math.addExact(nowNanos, durationNanos);
		} catch (ArithmeticException ex) {
			throw new IllegalStateException("Cleric effect deadline overflow", ex);
		}
		entries.put(definition.getFamily(), new ClericEffectEntry(definition, origin,
			nowNanos, expiresAtNanos, definition.getInitialCounter()));
		return result;
	}

	/** Side-effect-free replacement preflight for the cast transaction. */
	public synchronized ApplyResult preview(
			ClericEffectRankDefinition<? extends ClericEffectMagnitude> definition,
			ClericEffectOrigin origin, ClericEffectOriginValidator validator) {
		requireDefinitionOriginAndValidator(definition, origin, validator);
		if (!validator.isCurrent(origin)) {
			return ApplyResult.REJECTED_INVALID_ORIGIN;
		}
		ClericEffectEntry existing = entries.get(definition.getFamily());
		if (existing == null || existing.isExpired(clock.nanoTime())
				|| !validator.isCurrent(existing.getOrigin())) {
			return ApplyResult.APPLIED;
		}
		return classify(definition, existing);
	}

	public synchronized CounterResult consumeCounter(ClericEffectFamily family,
			ClericEffectCounterKind expectedKind, ClericEffectOriginValidator validator) {
		if (family == null || expectedKind == null || validator == null) {
			throw new IllegalArgumentException("Cleric counter lookup requires family, kind, and validator");
		}
		long nowNanos = clock.nanoTime();
		purgeInvalidLocked(nowNanos, validator);
		ClericEffectEntry existing = entries.get(family);
		if (existing == null) {
			return CounterResult.MISSING_OR_INVALID;
		}
		if (expectedKind == ClericEffectCounterKind.NONE
				|| existing.getDefinition().getCounterKind() != expectedKind) {
			return CounterResult.WRONG_KIND;
		}

		int remaining = existing.getRemainingCounter() - 1;
		if (remaining == 0) {
			entries.remove(family);
			return CounterResult.EXHAUSTED;
		}
		entries.put(family, existing.withRemainingCounter(remaining));
		return CounterResult.CONSUMED;
	}

	public synchronized Optional<ClericEffectEntry> get(ClericEffectFamily family,
			ClericEffectOriginValidator validator) {
		if (family == null || validator == null) {
			throw new IllegalArgumentException("Cleric effect lookup requires family and validator");
		}
		purgeInvalidLocked(clock.nanoTime(), validator);
		return Optional.ofNullable(entries.get(family));
	}

	public synchronized List<ClericEffectEntry> snapshot(ClericEffectOriginValidator validator) {
		if (validator == null) {
			throw new IllegalArgumentException("Cleric effect snapshot validator is required");
		}
		purgeInvalidLocked(clock.nanoTime(), validator);
		return Collections.unmodifiableList(new ArrayList<ClericEffectEntry>(entries.values()));
	}

	/**
	 * Returns one server-authoritative timer/counter snapshot using the same
	 * monotonic instant for validation and countdown rounding.
	 */
	public synchronized List<ClericEffectStatusSnapshot> statusSnapshot(
			ClericEffectOriginValidator validator) {
		if (validator == null) {
			throw new IllegalArgumentException("Cleric effect snapshot validator is required");
		}
		long nowNanos = clock.nanoTime();
		purgeInvalidLocked(nowNanos, validator);
		ArrayList<ClericEffectStatusSnapshot> snapshots =
			new ArrayList<ClericEffectStatusSnapshot>(entries.size());
		for (ClericEffectEntry entry : entries.values()) {
			long remainingNanos = entry.getRemainingNanos(nowNanos);
			long remainingSeconds = remainingNanos / 1_000_000_000L
				+ (remainingNanos % 1_000_000_000L == 0L ? 0L : 1L);
			snapshots.add(new ClericEffectStatusSnapshot(entry.getDefinition(),
				(int) Math.min(Integer.MAX_VALUE, remainingSeconds),
				entry.getRemainingCounter()));
		}
		return Collections.unmodifiableList(snapshots);
	}

	public synchronized int size(ClericEffectOriginValidator validator) {
		if (validator == null) {
			throw new IllegalArgumentException("Cleric effect size validator is required");
		}
		purgeInvalidLocked(clock.nanoTime(), validator);
		return entries.size();
	}

	@Override
	public synchronized int clearAll() {
		int removed = entries.size();
		entries.clear();
		return removed;
	}

	@Override
	public synchronized int clearOriginatingFrom(TransientEffectSessionToken casterSession,
			TransientEffectMembershipToken casterMembership) {
		if (casterSession == null || casterMembership == null) {
			throw new IllegalArgumentException("Complete Cleric caster origin is required");
		}
		int removed = 0;
		Iterator<Map.Entry<ClericEffectFamily, ClericEffectEntry>> iterator =
			entries.entrySet().iterator();
		while (iterator.hasNext()) {
			ClericEffectEntry entry = iterator.next().getValue();
			if (entry.getOrigin().originatedFrom(casterSession, casterMembership)) {
				iterator.remove();
				removed++;
			}
		}
		return removed;
	}

	private ApplyResult classify(
			ClericEffectRankDefinition<? extends ClericEffectMagnitude> candidate,
			ClericEffectEntry existingEntry) {
		if (existingEntry == null) {
			return ApplyResult.APPLIED;
		}
		ClericEffectRankDefinition<? extends ClericEffectMagnitude> existing =
			existingEntry.getDefinition();
		if (candidate.getFamilyPrecedence() > existing.getFamilyPrecedence()) {
			return ApplyResult.REPLACED;
		}
		if (candidate.getFamilyPrecedence() < existing.getFamilyPrecedence()) {
			return ApplyResult.REJECTED_WEAKER;
		}
		if (candidate.getSpellId() != existing.getSpellId()) {
			throw new IllegalStateException("Ambiguous same-precedence Cleric family identities");
		}
		if (candidate.getRank() > existing.getRank()) {
			return ApplyResult.REPLACED;
		}
		if (candidate.getRank() < existing.getRank()) {
			return ApplyResult.REJECTED_WEAKER;
		}
		return ApplyResult.REFRESHED;
	}

	private void purgeInvalidLocked(long nowNanos, ClericEffectOriginValidator validator) {
		Iterator<Map.Entry<ClericEffectFamily, ClericEffectEntry>> iterator =
			entries.entrySet().iterator();
		while (iterator.hasNext()) {
			ClericEffectEntry entry = iterator.next().getValue();
			if (entry.isExpired(nowNanos) || !validator.isCurrent(entry.getOrigin())) {
				iterator.remove();
			}
		}
	}

	private void requireDefinitionOriginAndValidator(
			ClericEffectRankDefinition<? extends ClericEffectMagnitude> definition,
			ClericEffectOrigin origin, ClericEffectOriginValidator validator) {
		if (definition == null || origin == null || validator == null) {
			throw new IllegalArgumentException("Cleric effect application requires definition, origin, and validator");
		}
	}
}
