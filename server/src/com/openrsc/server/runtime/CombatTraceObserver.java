package com.openrsc.server.runtime;

import com.openrsc.server.model.combat.DamageResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bounded, read-only combat diagnostic recorder.
 *
 * <p>The recorder is inert unless an owning integration explicitly constructs
 * it with a non-{@link CombatTraceProfile#OFF} profile. It never writes player
 * state or participates in combat decisions. R2 configuration integration is
 * intentionally outside this class and must validate both profile and capacity
 * before injecting it through the existing {@link CombatDamageObserver} seam.</p>
 */
public final class CombatTraceObserver implements CombatDamageObserver {
	public static final int MIN_CAPACITY = 1;
	public static final int MAX_CAPACITY = 1_024;

	private final CombatTraceProfile profile;
	private final int capacity;
	private final ArrayDeque<CombatTraceRecord> records =
		new ArrayDeque<CombatTraceRecord>();

	public CombatTraceObserver(final CombatTraceProfile profile,
			final int capacity) {
		this.profile = Objects.requireNonNull(profile, "profile");
		if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
			throw new IllegalArgumentException("combat trace capacity must be "
				+ MIN_CAPACITY + "-" + MAX_CAPACITY);
		}
		this.capacity = capacity;
	}

	public CombatTraceProfile getProfile() {
		return profile;
	}

	public int getCapacity() {
		return capacity;
	}

	@Override
	public boolean isEnabled() {
		return profile != CombatTraceProfile.OFF;
	}

	@Override
	public synchronized void onDamageObserved(final DamageResult result) {
		if (result == null || !isEnabled()) {
			return;
		}
		if (profile.recordsDamage()) {
			append(CombatTraceRecord.settled(result));
		}
		if (profile.recordsLifecycle() && result.isTargetTerminal()) {
			append(CombatTraceRecord.targetHitsDepleted(result));
		}
	}

	/** Returns a stable snapshot without exposing mutable recorder storage. */
	public synchronized List<CombatTraceRecord> snapshot() {
		return Collections.unmodifiableList(new ArrayList<CombatTraceRecord>(
			records));
	}

	public synchronized void clear() {
		records.clear();
	}

	private void append(final CombatTraceRecord record) {
		if (records.size() == capacity) {
			records.removeFirst();
		}
		records.addLast(record);
	}
}
