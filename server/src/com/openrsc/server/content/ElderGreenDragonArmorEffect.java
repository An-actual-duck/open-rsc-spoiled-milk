package com.openrsc.server.content;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.combat.SecondaryEffectPolicy;
import com.openrsc.server.model.combat.dot.ElderArmorBurnState;
import com.openrsc.server.model.combat.dot.PeriodicEffectProvenance;
import com.openrsc.server.model.entity.KillType;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.states.CombatState;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.CombatEffectUtil;

/** Player-owned damage and refreshable burn for the Elder Green Dragon armor set. */
public final class ElderGreenDragonArmorEffect {
	public static final double PROC_CHANCE = 0.60D;
	public static final int MAX_TRUE_DAMAGE = 10;
	public static final int SPLASH_RADIUS = 2;
	public static final int BURN_DAMAGE = 1;
	public static final int BURN_PULSES = 5;

	private static final String BURN_EVENT_KEY =
		"elder_green_dragon_armor_burn_event";
	private static final String BURN_STATE_KEY =
		"elder_green_dragon_armor_burn_state";

	private ElderGreenDragonArmorEffect() {
	}

	public static int splashDamage(final int primaryDamageDealt) {
		return primaryDamageDealt <= 0
			? 0 : (primaryDamageDealt / 2) + (primaryDamageDealt % 2);
	}

	/** Applies one already-rolled Elder Breath proc to its primary and eligible splash targets. */
	public static void applyProc(final Player source, final Mob primaryTarget,
			final int primaryDamageRoll) {
		if (!isValidSource(source) || !isValidTarget(primaryTarget)) {
			return;
		}
		source.getUpdateFlags().setCombatEffect(
			new CombatEffect(source, CombatEffect.DRAGON_BREATH));
		final int primaryDamageDealt = inflictTrueDamage(
			source, primaryTarget, primaryDamageRoll);
		if (primaryDamageDealt <= 0) {
			return;
		}
		if (isValidTarget(primaryTarget)) {
			applyBurn(source, primaryTarget);
		}
		final int secondaryDamage = splashDamage(primaryDamageDealt);
		if (secondaryDamage <= 0) {
			return;
		}
		if (primaryTarget.isNpc()) {
			for (Npc target : CombatEffectUtil.findPlayerOwnedNpcSplashTargets(
				source, (Npc) primaryTarget, SPLASH_RADIUS)) {
				applySecondary(source, target, secondaryDamage);
			}
		} else if (primaryTarget.isPlayer()) {
			for (Player target : CombatEffectUtil.findPlayerOwnedPvpSplashTargets(
				source, (Player) primaryTarget, SPLASH_RADIUS)) {
				applySecondary(source, target, secondaryDamage);
			}
		}
	}

	public static int inflictTrueDamage(final Player source, final Mob target,
			final int damage) {
		return inflictOwnedDamage(source, target, damage,
			SecondaryEffectPolicy.ELDER_GREEN_DRAGON_ARMOR_TRUE_DAMAGE);
	}

	public static void applyBurn(final Player source, final Mob target) {
		if (!isValidSource(source) || !isValidTarget(target)) {
			return;
		}
		synchronized (target) {
			final PeriodicEffectProvenance provenance =
				PeriodicEffectProvenance.player(source.getUUID());
			final Object eventAttribute = target.getAttribute(BURN_EVENT_KEY, null);
			ElderArmorBurnEvent existing = eventAttribute instanceof ElderArmorBurnEvent
				? (ElderArmorBurnEvent) eventAttribute : null;
			if (eventAttribute != null && existing == null) {
				target.removeAttribute(BURN_EVENT_KEY);
				existing = findRunningBurnEvent(target);
				if (existing != null) {
					target.setAttribute(BURN_EVENT_KEY, existing);
				}
			}
			if (existing != null && existing.isRunning()) {
				target.setAttribute(BURN_STATE_KEY, ElderArmorBurnState.of(
					provenance, BURN_PULSES));
				existing.refresh();
			} else {
				target.removeAttribute(BURN_EVENT_KEY);
				final ElderArmorBurnEvent burn = new ElderArmorBurnEvent(target);
				if (!target.getWorld().getServer().getGameEventHandler().add(burn)) {
					return;
				}
				target.setAttribute(BURN_STATE_KEY, ElderArmorBurnState.of(
					provenance, BURN_PULSES));
				target.setAttribute(BURN_EVENT_KEY, burn);
			}
		}
		target.getUpdateFlags().setCombatEffect(
			new CombatEffect(target, CombatEffect.ELDER_DRAGON_BURN));
	}

	private static ElderArmorBurnEvent findRunningBurnEvent(final Mob target) {
		for (GameTickEvent event : target.getWorld().getServer()
				.getGameEventHandler().getEvents()) {
			if (event instanceof ElderArmorBurnEvent && event.isRunning()
					&& event.getOwner() == target) {
				return (ElderArmorBurnEvent) event;
			}
		}
		return null;
	}

	/** Clears only this armor-owned burn; generic burn remains retired. */
	public static void clearBurn(final Mob target) {
		if (target == null) return;
		synchronized (target) {
			final Object attribute = target.getAttribute(BURN_EVENT_KEY, null);
			if (attribute instanceof ElderArmorBurnEvent) {
				((ElderArmorBurnEvent) attribute).stop();
			}
			target.removeAttribute(BURN_EVENT_KEY);
			target.removeAttribute(BURN_STATE_KEY);
		}
	}

	private static void applySecondary(final Player source, final Mob target,
			final int damage) {
		final int damageDealt = inflictTrueDamage(source, target, damage);
		if (damageDealt > 0 && isValidTarget(target)) {
			applyBurn(source, target);
		}
	}

	private static int inflictOwnedDamage(final Player source, final Mob target,
			final int damage, final SecondaryEffectPolicy effectPolicy) {
		if (damage <= 0 || !isValidSource(source) || !isValidTarget(target)) {
			return 0;
		}
		final DamageRequest request = DamageRequest.resolvedLegacy(
			source, target, DamageRequest.SourceCategory.OWNED_EFFECT,
			effectPolicy.getStableKey(), damage)
			.hitSplatType(HitSplat.TYPE_ARMOR_PROC)
			.build();
		final DamageResult result = target.getWorld().getServer()
			.getResolvedDamageTransaction().apply(request);
		final int damageDealt = result.getLegacyDamageDealt();
		if (target.isNpc()) {
			((Npc) target).addCombatDamage(source, damageDealt);
		} else if (target.isPlayer()) {
			ActionSender.sendStat((Player) target, Skill.HITS.id());
		}
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			target.setLastCombatState(CombatState.LOST);
			source.setLastCombatState(CombatState.WON);
			source.setKillType(KillType.COMBAT);
			target.killedBy(source);
		}
		return damageDealt;
	}

	private static boolean isValidSource(final Player source) {
		return source != null && source.loggedIn() && !source.isRemoved()
			&& source.getSkills().getLevel(Skill.HITS.id()) > 0;
	}

	private static boolean isValidTarget(final Mob target) {
		return target != null && !target.isRemoved()
			&& target.getSkills().getLevel(Skill.HITS.id()) > 0;
	}

	private static final class ElderArmorBurnEvent extends GameTickEvent {
		private final Mob target;

		private ElderArmorBurnEvent(final Mob target) {
			super(target.getWorld(), target, 1, "Elder Green Dragon Armor Burn",
				DuplicationStrategy.ONE_PER_MOB);
			this.target = target;
		}

		private void refresh() {
			resetCountdown();
		}

		@Override
		public void run() {
			final ElderArmorBurnState state = target.getAttribute(BURN_STATE_KEY, null);
			final Player currentSource = state == null ? null
				: target.getWorld().getPlayerByUUID(state.getProvenance().getSourceId());
			if (!isValidSource(currentSource) || !isValidTarget(target)) {
				clear();
				return;
			}
			inflictOwnedDamage(currentSource, target, BURN_DAMAGE,
				SecondaryEffectPolicy.ELDER_GREEN_DRAGON_ARMOR_BURN);
			final ElderArmorBurnState next = state.afterPulse();
			if (next == null || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				clear();
			} else {
				target.setAttribute(BURN_STATE_KEY, next);
			}
		}

		private void clear() {
			if (target.getAttribute(BURN_EVENT_KEY, null) == this) {
				target.removeAttribute(BURN_EVENT_KEY);
			}
			target.removeAttribute(BURN_STATE_KEY);
			stop();
		}
	}
}
