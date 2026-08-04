package com.openrsc.server.content.cleric.effect;

import com.openrsc.server.content.cleric.ClericSpellId;

/** Immutable authoritative magnitude and lifetime for one Cleric effect rank. */
public final class ClericEffectRankDefinition<M extends ClericEffectMagnitude> {
	private final ClericSpellId spellId;
	private final ClericEffectFamily family;
	private final int rank;
	private final int familyPrecedence;
	private final ClericEffectDuration duration;
	private final ClericEffectCounterKind counterKind;
	private final int initialCounter;
	private final M magnitude;

	ClericEffectRankDefinition(ClericSpellId spellId, ClericEffectFamily family,
			int rank, int familyPrecedence, ClericEffectDuration duration,
			ClericEffectCounterKind counterKind, int initialCounter, M magnitude) {
		if (spellId == null || family == null || duration == null
				|| counterKind == null || magnitude == null) {
			throw new IllegalArgumentException("Complete Cleric effect-rank metadata is required");
		}
		if (rank <= 0 || rank > 255 || familyPrecedence <= 0) {
			throw new IllegalArgumentException("Cleric rank and family precedence must be positive");
		}
		if (counterKind == ClericEffectCounterKind.NONE && initialCounter != 0) {
			throw new IllegalArgumentException("Counter-free Cleric effects cannot carry a counter");
		}
		if (counterKind != ClericEffectCounterKind.NONE
				&& (initialCounter <= 0 || initialCounter > 65_535)) {
			throw new IllegalArgumentException("Counted Cleric effects require a bounded counter");
		}
		validateIdentityShape(spellId, family, counterKind, magnitude);
		this.spellId = spellId;
		this.family = family;
		this.rank = rank;
		this.familyPrecedence = familyPrecedence;
		this.duration = duration;
		this.counterKind = counterKind;
		this.initialCounter = initialCounter;
		this.magnitude = magnitude;
	}

	public ClericSpellId getSpellId() {
		return spellId;
	}

	public ClericEffectFamily getFamily() {
		return family;
	}

	public int getRank() {
		return rank;
	}

	public int getFamilyPrecedence() {
		return familyPrecedence;
	}

	public ClericEffectDuration getDuration() {
		return duration;
	}

	public ClericEffectCounterKind getCounterKind() {
		return counterKind;
	}

	public int getInitialCounter() {
		return initialCounter;
	}

	public M getMagnitude() {
		return magnitude;
	}

	private static void validateIdentityShape(ClericSpellId spellId,
			ClericEffectFamily family, ClericEffectCounterKind counterKind,
			ClericEffectMagnitude magnitude) {
		switch (spellId) {
			case MEND:
			case GREATER_MEND:
				requireShape(family == ClericEffectFamily.HEALING_PULSES
					&& counterKind == ClericEffectCounterKind.PULSES
					&& magnitude instanceof ClericEffectMagnitudes.HealingPulse, spellId);
				return;
			case FERVOR:
				requireShape(family == ClericEffectFamily.ACCURACY
					&& counterKind == ClericEffectCounterKind.NONE
					&& magnitude instanceof ClericEffectMagnitudes.Accuracy, spellId);
				return;
			case WARD:
			case AEGIS:
				requireShape(family == ClericEffectFamily.PROTECTION
					&& counterKind == ClericEffectCounterKind.CHARGES
					&& magnitude instanceof ClericEffectMagnitudes.Protection, spellId);
				return;
			case ZEAL:
				requireShape(family == ClericEffectFamily.DAMAGE
					&& counterKind == ClericEffectCounterKind.NONE
					&& magnitude instanceof ClericEffectMagnitudes.Damage, spellId);
				return;
			case THORNS:
				requireShape(family == ClericEffectFamily.REFLECTION
					&& counterKind == ClericEffectCounterKind.NONE
					&& magnitude instanceof ClericEffectMagnitudes.Reflection, spellId);
				return;
			case RALLY:
				requireShape(family == ClericEffectFamily.LIFESTEAL
					&& counterKind == ClericEffectCounterKind.NONE
					&& magnitude instanceof ClericEffectMagnitudes.Lifesteal, spellId);
				return;
			case RESPITE:
				requireShape(family == ClericEffectFamily.PASSIVE_REGENERATION
					&& counterKind == ClericEffectCounterKind.NONE
					&& magnitude instanceof ClericEffectMagnitudes.Regeneration, spellId);
				return;
			default:
				throw new IllegalArgumentException(
					"Cleric spell does not define transient effect state: " + spellId);
		}
	}

	private static void requireShape(boolean valid, ClericSpellId spellId) {
		if (!valid) {
			throw new IllegalArgumentException(
				"Invalid Cleric effect family, counter, or magnitude for " + spellId);
		}
	}
}
