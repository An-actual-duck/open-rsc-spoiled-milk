package com.openrsc.server.content.cleric.runtime;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.content.cleric.ClericDirectCombatEffects;
import com.openrsc.server.content.cleric.ClericSpellId;
import com.openrsc.server.content.cleric.effect.ClericEffectCounterKind;
import com.openrsc.server.content.cleric.effect.ClericEffectEntry;
import com.openrsc.server.content.cleric.effect.ClericEffectFamily;
import com.openrsc.server.content.cleric.effect.ClericEffectMagnitudes;
import com.openrsc.server.content.cleric.effect.ClericEffectOriginValidator;
import com.openrsc.server.content.cleric.effect.ClericEffectOrigins;
import com.openrsc.server.content.cleric.effect.ClericEffectRegistry;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.DataConversions;

import java.util.Optional;

/**
 * Content-owned bridge between direct melee/projectile hits and recipient-owned
 * Cleric state. Callers must invoke this only for a primary direct hit; the
 * runtime independently rejects PvP and summon paths.
 */
public final class ClericDirectCombatRuntime {
	private ClericDirectCombatRuntime() {
	}

	public static double combineFervorRollChance(final Mob attacker,
			final double existingChance) {
		if (!(attacker instanceof Player)) {
			return Math.min(1.0D, Math.max(0.0D, existingChance));
		}
		final Player player = (Player) attacker;
		final int fervorPercent = isEffectUsable(player)
			? accuracyPercent(player) : 0;
		return ClericDirectCombatEffects.combineUpwardRollChance(
			existingChance, fervorPercent);
	}

	public static BeforeDamage beforeDirectDamage(final Mob attacker,
			final Mob defender, final int damage) {
		if (damage < 0) {
			throw new IllegalArgumentException("Direct damage cannot be negative");
		}
		if (damage == 0 || !isEligibleDirectCombat(attacker, defender)) {
			return new BeforeDamage(damage, 0);
		}

		int adjustedDamage = damage;
		int preventedDamage = 0;
		if (defender instanceof Player) {
			final ProtectionResult protection = applyProtection(
				(Player) defender, adjustedDamage);
			adjustedDamage = protection.damage;
			preventedDamage = protection.prevented;
		}
		if (attacker instanceof Player && adjustedDamage > 0) {
			adjustedDamage = applyZeal((Player) attacker, adjustedDamage);
		}
		return new BeforeDamage(adjustedDamage, preventedDamage);
	}

	/**
	 * Resolves Rally after established direct-hit healing and returns terminal
	 * Thorns damage for the event to apply through its normal attributed-death
	 * path.
	 */
	public static AfterDamage afterExistingLifesteal(final Mob attacker,
			final Mob defender, final int actualDamage) {
		return afterExistingLifesteal(attacker, defender, actualDamage, true);
	}

	public static AfterDamage afterExistingLifesteal(final Mob attacker,
			final Mob defender, final int actualDamage, final boolean applyRally) {
		if (actualDamage <= 0 || !isEligibleDirectCombat(attacker, defender)) {
			return AfterDamage.NONE;
		}
		int rallyHealing = applyRally && attacker instanceof Player
			? applyRally((Player) attacker, actualDamage) : 0;
		int thornsDamage = defender instanceof Player
			? rollThorns((Player) defender, actualDamage) : 0;
		return new AfterDamage(rallyHealing, thornsDamage);
	}

	/** Used by god spells after their established delayed lifesteal resolves. */
	public static int applyDeferredRally(final Mob attacker, final Mob defender,
			final int actualDamage) {
		if (actualDamage <= 0 || !(attacker instanceof Player)
				|| !isEligibleDirectCombat(attacker, defender)) {
			return 0;
		}
		return applyRally((Player) attacker, actualDamage);
	}

	private static int accuracyPercent(final Player player) {
		final ClericEffectRegistry registry = installedRegistry(player);
		if (registry == null) {
			return 0;
		}
		final Optional<ClericEffectEntry> entry = registry.get(
			ClericEffectFamily.ACCURACY, ClericEffectOrigins.validatorFor(player));
		if (!entry.isPresent()
				|| entry.get().getDefinition().getSpellId() != ClericSpellId.FERVOR) {
			return 0;
		}
		return ((ClericEffectMagnitudes.Accuracy) entry.get().getDefinition()
			.getMagnitude()).getUpwardRollChancePercent();
	}

	private static ProtectionResult applyProtection(final Player defender,
			final int damage) {
		final ClericEffectRegistry registry = installedRegistry(defender);
		if (registry == null || !isEffectUsable(defender)) {
			return new ProtectionResult(damage, 0);
		}
		final ClericEffectOriginValidator validator =
			ClericEffectOrigins.validatorFor(defender);
		final Optional<ClericEffectEntry> current = registry.get(
			ClericEffectFamily.PROTECTION, validator);
		if (!current.isPresent()) {
			return new ProtectionResult(damage, 0);
		}
		final ClericEffectEntry entry = current.get();
		final ClericSpellId spell = entry.getDefinition().getSpellId();
		if (spell != ClericSpellId.WARD && spell != ClericSpellId.AEGIS) {
			return new ProtectionResult(damage, 0);
		}
		final ClericEffectMagnitudes.Protection magnitude =
			(ClericEffectMagnitudes.Protection) entry.getDefinition().getMagnitude();
		final int protectedDamage = ClericDirectCombatEffects.applyProtection(
			damage, magnitude.getReductionPercent());
		final int prevented = damage - protectedDamage;
		if (prevented <= 0) {
			return new ProtectionResult(damage, 0);
		}
		final ClericEffectRegistry.CounterResult consumed = registry.consumeCounter(
			ClericEffectFamily.PROTECTION, ClericEffectCounterKind.CHARGES,
			entry.getDefinition(), validator);
		if (consumed != ClericEffectRegistry.CounterResult.CONSUMED
				&& consumed != ClericEffectRegistry.CounterResult.EXHAUSTED) {
			return new ProtectionResult(damage, 0);
		}
		ActionSender.sendActivePotionEffects(defender);
		return new ProtectionResult(protectedDamage, prevented);
	}

	private static int applyZeal(final Player attacker, final int damage) {
		final ClericEffectRegistry registry = installedRegistry(attacker);
		if (registry == null || !isEffectUsable(attacker)) {
			return damage;
		}
		final ClericEffectOriginValidator validator =
			ClericEffectOrigins.validatorFor(attacker);
		final Optional<ClericEffectEntry> current = registry.get(
			ClericEffectFamily.DAMAGE, validator);
		if (!current.isPresent()
				|| current.get().getDefinition().getSpellId() != ClericSpellId.ZEAL) {
			return damage;
		}
		final ClericEffectEntry entry = current.get();
		final int bonus = registry.accumulateFractionalPercent(
			ClericEffectFamily.DAMAGE, ClericSpellId.ZEAL,
			entry.getDefinition(), damage, validator);
		return ClericDirectCombatEffects.addBounded(damage, bonus);
	}

	private static int applyRally(final Player attacker, final int actualDamage) {
		final ClericEffectRegistry registry = installedRegistry(attacker);
		if (registry == null || !isEffectUsable(attacker)
				|| attacker.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return 0;
		}
		final ClericEffectOriginValidator validator =
			ClericEffectOrigins.validatorFor(attacker);
		final Optional<ClericEffectEntry> current = registry.get(
			ClericEffectFamily.LIFESTEAL, validator);
		if (!current.isPresent()
				|| current.get().getDefinition().getSpellId() != ClericSpellId.RALLY) {
			return 0;
		}
		final ClericEffectEntry entry = current.get();
		final ClericEffectMagnitudes.Lifesteal magnitude =
			(ClericEffectMagnitudes.Lifesteal) entry.getDefinition().getMagnitude();
		final int currentHits = attacker.getSkills().getLevel(Skill.HITS.id());
		final int healingCeiling = attacker.getHealingMaximumHits();
		if (!ClericDirectCombatEffects.isBelowPercent(currentHits,
				healingCeiling, magnitude.getEndingHitsPercent())) {
			if (registry.remove(ClericEffectFamily.LIFESTEAL, entry.getDefinition())) {
				ActionSender.sendActivePotionEffects(attacker);
			}
			return 0;
		}
		final int earned = registry.accumulateFractionalPercent(
			ClericEffectFamily.LIFESTEAL, ClericSpellId.RALLY,
			entry.getDefinition(), actualDamage, validator);
		final int healed = Math.min(earned, Math.max(0, healingCeiling - currentHits));
		if (healed <= 0) {
			return 0;
		}
		attacker.getSkills().setLevel(Skill.HITS.id(), currentHits + healed);
		attacker.getUpdateFlags().addHitSplat(
			new HitSplat(attacker, HitSplat.TYPE_HEAL, healed));
		ActionSender.sendStat(attacker, Skill.HITS.id());
		if (attacker.getConfig().WANT_PARTIES && attacker.getParty() != null) {
			attacker.getParty().sendParty();
		}
		return healed;
	}

	private static int rollThorns(final Player defender, final int actualDamage) {
		final ClericEffectRegistry registry = installedRegistry(defender);
		if (registry == null || !isEffectUsable(defender)) {
			return 0;
		}
		final Optional<ClericEffectEntry> current = registry.get(
			ClericEffectFamily.REFLECTION, ClericEffectOrigins.validatorFor(defender));
		if (!current.isPresent()
				|| current.get().getDefinition().getSpellId() != ClericSpellId.THORNS) {
			return 0;
		}
		final int percent = ((ClericEffectMagnitudes.Reflection) current.get()
			.getDefinition().getMagnitude()).getReflectedPercent();
		return ClericDirectCombatEffects.stochasticPercentage(actualDamage,
			percent, DataConversions.getRandom().nextInt(100));
	}

	private static boolean isEligibleDirectCombat(final Mob attacker,
			final Mob defender) {
		if (attacker == null || defender == null
				|| Summoning.isSummon(attacker) || Summoning.isSummon(defender)) {
			return false;
		}
		if (attacker instanceof Player && defender instanceof Player) {
			return false;
		}
		return (!(attacker instanceof Player) || isEffectUsable((Player) attacker))
			&& (!(defender instanceof Player) || isEffectUsable((Player) defender));
	}

	private static boolean isEffectUsable(final Player player) {
		return player != null && player.isLoggedIn() && !player.isUnregistering()
			&& !ClericSupportCasting.isPvpContext(player);
	}

	private static ClericEffectRegistry installedRegistry(final Player player) {
		return player != null
			&& player.getTransientEffectState() instanceof ClericEffectRegistry
			? (ClericEffectRegistry) player.getTransientEffectState() : null;
	}

	public static final class BeforeDamage {
		private final int damage;
		private final int preventedDamage;

		private BeforeDamage(int damage, int preventedDamage) {
			this.damage = damage;
			this.preventedDamage = preventedDamage;
		}

		public int getDamage() {
			return damage;
		}

		public int getPreventedDamage() {
			return preventedDamage;
		}
	}

	public static final class AfterDamage {
		private static final AfterDamage NONE = new AfterDamage(0, 0);

		private final int rallyHealing;
		private final int thornsDamage;

		private AfterDamage(int rallyHealing, int thornsDamage) {
			this.rallyHealing = rallyHealing;
			this.thornsDamage = thornsDamage;
		}

		public int getRallyHealing() {
			return rallyHealing;
		}

		public int getThornsDamage() {
			return thornsDamage;
		}
	}

	private static final class ProtectionResult {
		private final int damage;
		private final int prevented;

		private ProtectionResult(int damage, int prevented) {
			this.damage = damage;
			this.prevented = prevented;
		}
	}
}
