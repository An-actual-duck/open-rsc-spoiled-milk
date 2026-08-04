package com.openrsc.server.content.cleric.effect;

/** Typed immutable magnitude records shared by later mechanics and presentation. */
public final class ClericEffectMagnitudes {
	private ClericEffectMagnitudes() {
	}

	public static final class HealingPulse implements ClericEffectMagnitude {
		private final int hitsPerPulse;
		private final int firstDelayedPulseTicks;
		private final int secondDelayedPulseTicks;

		public HealingPulse(int hitsPerPulse, int firstDelayedPulseTicks,
				int secondDelayedPulseTicks) {
			if (hitsPerPulse <= 0 || firstDelayedPulseTicks <= 0
					|| secondDelayedPulseTicks <= firstDelayedPulseTicks) {
				throw new IllegalArgumentException("Invalid Cleric healing-pulse magnitude");
			}
			this.hitsPerPulse = hitsPerPulse;
			this.firstDelayedPulseTicks = firstDelayedPulseTicks;
			this.secondDelayedPulseTicks = secondDelayedPulseTicks;
		}

		public int getHitsPerPulse() {
			return hitsPerPulse;
		}

		public int getFirstDelayedPulseTicks() {
			return firstDelayedPulseTicks;
		}

		public int getSecondDelayedPulseTicks() {
			return secondDelayedPulseTicks;
		}
	}

	public static final class Accuracy implements ClericEffectMagnitude {
		private final int upwardRollChancePercent;
		private final int rollIncrease;

		public Accuracy(int upwardRollChancePercent, int rollIncrease) {
			checkPercent(upwardRollChancePercent, "accuracy chance");
			if (rollIncrease <= 0) {
				throw new IllegalArgumentException("Cleric accuracy roll increase must be positive");
			}
			this.upwardRollChancePercent = upwardRollChancePercent;
			this.rollIncrease = rollIncrease;
		}

		public int getUpwardRollChancePercent() {
			return upwardRollChancePercent;
		}

		public int getRollIncrease() {
			return rollIncrease;
		}
	}

	public static final class Protection implements ClericEffectMagnitude {
		private final int reductionPercent;

		public Protection(int reductionPercent) {
			if (reductionPercent <= 0 || reductionPercent >= 100) {
				throw new IllegalArgumentException("Cleric protection must be between 1 and 99 percent");
			}
			this.reductionPercent = reductionPercent;
		}

		public int getReductionPercent() {
			return reductionPercent;
		}
	}

	public static final class Damage implements ClericEffectMagnitude {
		private final int bonusPercent;

		public Damage(int bonusPercent) {
			checkPercent(bonusPercent, "damage bonus");
			this.bonusPercent = bonusPercent;
		}

		public int getBonusPercent() {
			return bonusPercent;
		}
	}

	public static final class Reflection implements ClericEffectMagnitude {
		private final int reflectedPercent;

		public Reflection(int reflectedPercent) {
			checkPercent(reflectedPercent, "reflection");
			this.reflectedPercent = reflectedPercent;
		}

		public int getReflectedPercent() {
			return reflectedPercent;
		}
	}

	public static final class Lifesteal implements ClericEffectMagnitude {
		private final int lifestealPercent;
		private final int endingHitsPercent;

		public Lifesteal(int lifestealPercent, int endingHitsPercent) {
			checkPercent(lifestealPercent, "lifesteal");
			checkPercent(endingHitsPercent, "lifesteal ending Hits");
			this.lifestealPercent = lifestealPercent;
			this.endingHitsPercent = endingHitsPercent;
		}

		public int getLifestealPercent() {
			return lifestealPercent;
		}

		public int getEndingHitsPercent() {
			return endingHitsPercent;
		}
	}

	public static final class Regeneration implements ClericEffectMagnitude {
		private final int speedIncreasePercent;

		public Regeneration(int speedIncreasePercent) {
			checkPercent(speedIncreasePercent, "regeneration speed");
			this.speedIncreasePercent = speedIncreasePercent;
		}

		public int getSpeedIncreasePercent() {
			return speedIncreasePercent;
		}
	}

	private static void checkPercent(int percent, String label) {
		if (percent <= 0 || percent > 100) {
			throw new IllegalArgumentException("Cleric " + label + " must be between 1 and 100 percent");
		}
	}
}
