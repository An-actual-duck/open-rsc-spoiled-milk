package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.runtime.GameRandom;

/** Shared primary Infernal Fire proc policy for central on-hit owners. */
public final class InfernalFireProc {
	private InfernalFireProc() {
	}

	@FunctionalInterface
	public interface AuxiliaryMagicDamage {
		int apply(int rolledDamage);
	}

	@FunctionalInterface
	public interface PostDamageFollowup {
		void apply(int damageDealt);
	}

	/**
	 * Applies Infernal Fire's common chance, payload, presentation and debuff.
	 *
	 * <p>The central event owner retains phase eligibility, event-specific magic
	 * damage settlement, Hell's Inferno area policy, debug source label, and
	 * death handling through the callbacks.</p>
	 */
	public static Result tryApply(final Player source, final Mob target,
			final GameRandom random, final AuxiliaryMagicDamage auxiliaryMagicDamage,
			final PostDamageFollowup postDamageFollowup) {
		final int maxHit = source.getInfernalFireProcMaxHit();
		if (maxHit <= 0) {
			return Result.notConfigured(maxHit);
		}
		final double chance = source.getInfernalFireProcChance();
		final double roll = random.nextDouble();
		if (roll >= chance) {
			return Result.failed(maxHit, chance, roll);
		}
		target.getUpdateFlags().setCombatEffect(new CombatEffect(target,
			CombatEffect.infernalEffectForMaxHit(maxHit)));
		final int rolledDamage = random.nextIntInclusive(0, maxHit);
		final int damageDealt = auxiliaryMagicDamage.apply(rolledDamage);
		target.applyInfernalFireDefenseDebuff(
			source.getInfernalFireDefenseDebuffPercent());
		postDamageFollowup.apply(damageDealt);
		return Result.succeeded(maxHit, chance, roll, rolledDamage, damageDealt);
	}

	/** Immutable owner-debug result; it does not own any gameplay authority. */
	public static final class Result {
		private final int maxHit;
		private final double chance;
		private final double roll;
		private final boolean triggered;
		private final int rolledDamage;
		private final int damageDealt;

		private Result(final int maxHit, final double chance, final double roll,
				final boolean triggered, final int rolledDamage,
				final int damageDealt) {
			this.maxHit = maxHit;
			this.chance = chance;
			this.roll = roll;
			this.triggered = triggered;
			this.rolledDamage = rolledDamage;
			this.damageDealt = damageDealt;
		}

		private static Result notConfigured(final int maxHit) {
			return new Result(maxHit, 0.0D, -1.0D, false, 0, 0);
		}

		private static Result failed(final int maxHit, final double chance,
				final double roll) {
			return new Result(maxHit, chance, roll, false, 0, 0);
		}

		private static Result succeeded(final int maxHit, final double chance,
				final double roll, final int rolledDamage, final int damageDealt) {
			return new Result(maxHit, chance, roll, true, rolledDamage,
				damageDealt);
		}

		public boolean isConfigured() { return maxHit > 0; }
		public int getMaxHit() { return maxHit; }
		public double getChance() { return chance; }
		public double getRoll() { return roll; }
		public boolean isTriggered() { return triggered; }
		public int getRolledDamage() { return rolledDamage; }
		public int getDamageDealt() { return damageDealt; }
	}
}
