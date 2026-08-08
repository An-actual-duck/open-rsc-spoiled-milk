package com.openrsc.server.model.combat.dot;

/**
 * Immutable next-state calculation for one target's generic poison. It has no
 * scheduler or entity references, so a caller can calculate and validate the
 * complete update before exposing it to the target/event registry.
 */
public final class PoisonTargetState {
	private final int currentPower;
	private final int maximumPower;
	private final PeriodicEffectProvenance provenance;

	private PoisonTargetState(final int currentPower, final int maximumPower,
			final PeriodicEffectProvenance provenance) {
		if (currentPower < 0 || maximumPower < 0
			|| currentPower > maximumPower) {
			throw new IllegalArgumentException("invalid poison target state");
		}
		this.currentPower = currentPower;
		this.maximumPower = maximumPower;
		this.provenance = provenance;
	}

	public static PoisonTargetState empty() {
		return new PoisonTargetState(0, 0, null);
	}

	public static PoisonTargetState of(final int currentPower,
			final int maximumPower, final PeriodicEffectProvenance provenance) {
		return new PoisonTargetState(currentPower, maximumPower, provenance);
	}

	public PoisonTargetState apply(final int appliedPower,
			final int incomingMaximum,
			final PeriodicEffectProvenance incomingProvenance) {
		if (appliedPower <= 0 || incomingMaximum <= 0) {
			throw new IllegalArgumentException("poison application must be positive");
		}
		final int nextMaximum = Math.max(maximumPower, incomingMaximum);
		final long uncappedPower = (long) currentPower + (long) appliedPower;
		final int nextCurrent = (int) Math.min((long) nextMaximum,
			uncappedPower);
		final PeriodicEffectProvenance nextProvenance =
			PoisonTargetPolicy.transfersProvenance(currentPower, nextCurrent)
				? incomingProvenance : provenance;
		return new PoisonTargetState(nextCurrent, nextMaximum, nextProvenance);
	}

	public int getCurrentPower() { return currentPower; }
	public int getMaximumPower() { return maximumPower; }
	public PeriodicEffectProvenance getProvenance() { return provenance; }
}
