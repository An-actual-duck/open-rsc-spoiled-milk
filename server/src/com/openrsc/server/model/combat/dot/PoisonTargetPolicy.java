package com.openrsc.server.model.combat.dot;

import java.util.UUID;

/**
 * The approved A08 target-policy decisions that are independent of damage
 * mutation. Runtime migration consumes this policy rather than inferring an
 * owner from a target's mutable combat opponent.
 */
public final class PoisonTargetPolicy {
	public enum GenericBurnDisposition {
		RETIRE_AND_MIGRATE
	}

	private PoisonTargetPolicy() {
	}

	/**
	 * A capped application that adds no power must not take ownership of later
	 * poison pulses, Leach, contribution, or kill settlement.
	 */
	public static boolean transfersProvenance(final int currentPower,
			final int nextPower) {
		if (currentPower < 0 || nextPower < 0) {
			throw new IllegalArgumentException("poison power must be non-negative");
		}
		return nextPower > currentPower;
	}

	/**
	 * Only an online player source can receive player contribution/rewards. A
	 * missing/offline source never falls back to an unrelated current opponent.
	 */
	public static UUID eligiblePlayerAttribution(
			final PeriodicEffectProvenance provenance,
			final boolean sourcePlayerOnline) {
		if (provenance == null || !provenance.isPlayer()
			|| !sourcePlayerOnline) {
			return null;
		}
		return provenance.getSourceId();
	}

	public static GenericBurnDisposition genericBurnDisposition() {
		return GenericBurnDisposition.RETIRE_AND_MIGRATE;
	}
}
