package com.openrsc.server.content.cleric.effect;

/** Typed wire/display shape derived from the authoritative magnitude record. */
public enum ClericEffectPresentationKind {
	HEALING_PULSE(1),
	ACCURACY(2),
	PROTECTION(3),
	DAMAGE(4),
	REFLECTION(5),
	LIFESTEAL(6),
	REGENERATION(7);

	private final int code;

	ClericEffectPresentationKind(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static ClericEffectPresentationKind forMagnitude(ClericEffectMagnitude magnitude) {
		if (magnitude instanceof ClericEffectMagnitudes.HealingPulse) {
			return HEALING_PULSE;
		}
		if (magnitude instanceof ClericEffectMagnitudes.Accuracy) {
			return ACCURACY;
		}
		if (magnitude instanceof ClericEffectMagnitudes.Protection) {
			return PROTECTION;
		}
		if (magnitude instanceof ClericEffectMagnitudes.Damage) {
			return DAMAGE;
		}
		if (magnitude instanceof ClericEffectMagnitudes.Reflection) {
			return REFLECTION;
		}
		if (magnitude instanceof ClericEffectMagnitudes.Lifesteal) {
			return LIFESTEAL;
		}
		if (magnitude instanceof ClericEffectMagnitudes.Regeneration) {
			return REGENERATION;
		}
		throw new IllegalArgumentException("Unknown Cleric effect magnitude type");
	}

	public int getPrimaryMagnitude(ClericEffectMagnitude magnitude) {
		requireMatching(magnitude);
		switch (this) {
			case HEALING_PULSE:
				return ((ClericEffectMagnitudes.HealingPulse) magnitude).getHitsPerPulse();
			case ACCURACY:
				return ((ClericEffectMagnitudes.Accuracy) magnitude).getUpwardRollChancePercent();
			case PROTECTION:
				return ((ClericEffectMagnitudes.Protection) magnitude).getReductionPercent();
			case DAMAGE:
				return ((ClericEffectMagnitudes.Damage) magnitude).getBonusPercent();
			case REFLECTION:
				return ((ClericEffectMagnitudes.Reflection) magnitude).getReflectedPercent();
			case LIFESTEAL:
				return ((ClericEffectMagnitudes.Lifesteal) magnitude).getLifestealPercent();
			case REGENERATION:
			default:
				return ((ClericEffectMagnitudes.Regeneration) magnitude).getSpeedIncreasePercent();
		}
	}

	public int getSecondaryMagnitude(ClericEffectMagnitude magnitude) {
		requireMatching(magnitude);
		if (this == ACCURACY) {
			return ((ClericEffectMagnitudes.Accuracy) magnitude).getRollIncrease();
		}
		if (this == LIFESTEAL) {
			return ((ClericEffectMagnitudes.Lifesteal) magnitude).getEndingHitsPercent();
		}
		return 0;
	}

	private void requireMatching(ClericEffectMagnitude magnitude) {
		if (forMagnitude(magnitude) != this) {
			throw new IllegalArgumentException("Mismatched Cleric magnitude presentation kind");
		}
	}
}
