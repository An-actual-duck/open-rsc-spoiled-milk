package com.openrsc.server.event.rsc.impl.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.CorrosiveAura;
import com.openrsc.server.content.DivineRetribution;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.content.TrueDefense;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.SingleTickEvent;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.combat.SecondaryEffectPolicy;
import com.openrsc.server.model.combat.dot.ElderGreenDragonBurnState;
import com.openrsc.server.model.combat.dot.PeriodicEffectProvenance;
import com.openrsc.server.model.entity.KillType;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcMagicElement;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.DataConversions;

public final class ElderGreenDragonSpecialAttacks {
	public static final int AOE_RADIUS = 6;
	public static final int MELEE_SWEEP_RADIUS = 2;
	public static final int BURN_DURATION_MILLIS = 5000;
	public static final int BURN_DAMAGE_MIN = 1;
	public static final int BURN_DAMAGE_MAX = 3;

	private static final int MELEE_SWEEP_PROC_PERCENT = 25;
	private static final int FIRESHOT_PROC_PERCENT = 22;
	private static final int BURN_PROC_PERCENT = 14;
	private static final String BURN_EVENT_KEY = "elder_green_dragon_burn_event";
	private static final String BURN_STATE_KEY = "elder_green_dragon_burn_state";

	private ElderGreenDragonSpecialAttacks() {
	}

	public static boolean isElderGreenDragon(final Mob mob) {
		return mob != null && mob.isNpc() && ((Npc) mob).getID() == NpcId.ELDER_GREEN_DRAGON.id();
	}

	public static boolean shouldUseMeleeSweep(final Mob attacker, final Mob primaryTarget, final boolean attackSuppressed) {
		return !attackSuppressed
			&& isElderGreenDragon(attacker)
			&& primaryTarget != null
			&& primaryTarget.isPlayer()
			&& DataConversions.getRandom().nextInt(100) < MELEE_SWEEP_PROC_PERCENT;
	}

	public static void applyMeleeSweep(final World world, final Npc dragon, final Mob primaryTarget, final boolean attackSuppressed) {
		if (world == null || attackSuppressed || !isElderGreenDragon(dragon) || primaryTarget == null || !primaryTarget.isPlayer()
			|| dragon.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		Player primaryPlayer = (Player) primaryTarget;
		if (isValidPlayerTarget(dragon, primaryPlayer, MELEE_SWEEP_RADIUS)) {
			inflictMeleeSweepDamage(world, dragon, primaryPlayer);
		}
		for (Player player : dragon.getViewArea().getPlayersInView()) {
			if (!isValidPlayerTarget(dragon, player, MELEE_SWEEP_RADIUS) || player == primaryTarget) {
				continue;
			}
			inflictMeleeSweepDamage(world, dragon, player);
			if (dragon.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				return;
			}
		}
	}

	private static void inflictMeleeSweepDamage(final World world, final Npc dragon, final Player player) {
		int damage;
		boolean damageAlreadyTracked;
		if (world.getServer().getConfig().OSRS_COMBAT_MELEE) {
			damage = OSRSCombatFormula.Melee.doMeleeDamage(dragon, player);
			damageAlreadyTracked = false;
		} else {
			damage = CombatFormula.doMeleeDamage(dragon, player);
			damageAlreadyTracked = true;
		}
		dragon.setKillType(KillType.COMBAT);
		inflictPlayerDamage(dragon, player, damage, DamageStyle.MELEE, HitSplat.TYPE_STANDARD, damageAlreadyTracked);
	}

	public static void maybeApplyProjectileAoe(final World world, final Mob caster, final Mob primaryTarget, final boolean attackSuppressed) {
		if (world == null || attackSuppressed || !isElderGreenDragon(caster) || primaryTarget == null || !primaryTarget.isPlayer()
			|| caster.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final Npc dragon = (Npc) caster;
		final int roll = DataConversions.getRandom().nextInt(100);
		if (roll < BURN_PROC_PERCENT) {
			launchBurnAoe(world, dragon);
		} else if (roll < BURN_PROC_PERCENT + FIRESHOT_PROC_PERCENT) {
			launchFireshotAoe(world, dragon);
		}
	}

	private static void launchFireshotAoe(final World world, final Npc dragon) {
		for (Player player : dragon.getViewArea().getPlayersInView()) {
			if (!isValidProjectilePlayerTarget(dragon, player, AOE_RADIUS)) {
				continue;
			}
			player.getUpdateFlags().setProjectile(new Projectile(dragon, player, Projectile.FIREBALL));
			player.getUpdateFlags().setCombatEffect(new CombatEffect(player, CombatEffect.ELDER_DRAGON_FIRESHOT));
			world.getServer().getGameEventHandler().add(new SingleTickEvent(world, dragon, 1,
				"Elder Green Dragon Fireshot", DuplicationStrategy.ALLOW_MULTIPLE) {
				@Override
				public void action() {
					if (!isValidPlayerTarget(dragon, player, AOE_RADIUS)
						|| dragon.getSkills().getLevel(Skill.HITS.id()) <= 0) {
						return;
					}
					dragon.setKillType(KillType.RANGED);
					int damage = CombatFormula.doRangedDamage(dragon, ItemId.LONGBOW.id(), ItemId.BRONZE_ARROWS.id(), player, false);
					inflictPlayerDamage(dragon, player, damage, DamageStyle.RANGED, HitSplat.TYPE_ARMOR_PROC);
				}
			});
		}
	}

	private static void launchBurnAoe(final World world, final Npc dragon) {
		for (Player player : dragon.getViewArea().getPlayersInView()) {
			if (!isValidProjectilePlayerTarget(dragon, player, AOE_RADIUS)) {
				continue;
			}
			player.getUpdateFlags().setProjectile(new Projectile(dragon, player, Projectile.FIREBALL));
			player.getUpdateFlags().setCombatEffect(new CombatEffect(player, CombatEffect.ELDER_DRAGON_BURN));
			applyBurn(world, dragon, player);
		}
	}

	private static void applyBurn(final World world, final Npc dragon, final Player player) {
		synchronized (player) {
			final ElderGreenDragonBurnState state = ElderGreenDragonBurnState.of(
				PeriodicEffectProvenance.npc(dragon.getUUID(),
					Math.max(1L, dragon.getCombatLifecycle())),
				System.currentTimeMillis() + BURN_DURATION_MILLIS);
			final Object eventAttribute = player.getAttribute(BURN_EVENT_KEY, null);
			ElderGreenDragonBurnEvent existing = eventAttribute instanceof ElderGreenDragonBurnEvent
				? (ElderGreenDragonBurnEvent) eventAttribute : null;
			if (eventAttribute != null && existing == null) {
				player.removeAttribute(BURN_EVENT_KEY);
				existing = findRunningBurnEvent(player);
				if (existing != null) {
					player.setAttribute(BURN_EVENT_KEY, existing);
				}
			}
			player.setAttribute(BURN_STATE_KEY, state);
			if (existing != null && existing.isRunning()) {
				return;
			}
			player.removeAttribute(BURN_EVENT_KEY);
			final ElderGreenDragonBurnEvent burn = new ElderGreenDragonBurnEvent(world, player);
			if (!world.getServer().getGameEventHandler().add(burn)) {
				player.removeAttribute(BURN_STATE_KEY);
				return;
			}
			player.setAttribute(BURN_EVENT_KEY, burn);
		}
	}

	private static ElderGreenDragonBurnEvent findRunningBurnEvent(final Player player) {
		for (GameTickEvent event : player.getWorld().getServer()
				.getGameEventHandler().getEvents()) {
			if (event instanceof ElderGreenDragonBurnEvent && event.isRunning()
					&& event.getOwner() == player) {
				return (ElderGreenDragonBurnEvent) event;
			}
		}
		return null;
	}

	/** Clears only the boss-owned Elder burn on this target. */
	public static void clearBurn(final Player player) {
		if (player == null) return;
		synchronized (player) {
			final Object attribute = player.getAttribute(BURN_EVENT_KEY, null);
			if (attribute instanceof ElderGreenDragonBurnEvent) {
				((ElderGreenDragonBurnEvent) attribute).stop();
			}
			player.removeAttribute(BURN_EVENT_KEY);
			player.removeAttribute(BURN_STATE_KEY);
		}
	}

	private static boolean isValidPlayerTarget(final Npc dragon, final Player player, final int radius) {
		return dragon != null
			&& player != null
			&& player.loggedIn()
			&& !player.isRemoved()
			&& player.getSkills().getLevel(Skill.HITS.id()) > 0
			&& player.withinRange(dragon, radius);
	}

	private static boolean isValidProjectilePlayerTarget(final Npc dragon, final Player player, final int radius) {
			return isValidPlayerTarget(dragon, player, radius)
				&& PathValidation.checkHostileProjectilePath(
					dragon.getWorld(),
					dragon.getWorldLocation(),
					player.getWorldLocation());
	}

	private static int inflictPlayerDamage(final Npc dragon, final Player player, int damage, final DamageStyle style, final int hitSplatType) {
		return inflictPlayerDamage(dragon, player, damage, style, hitSplatType, false);
	}

	private static int inflictPlayerDamage(final Npc dragon, final Player player, int damage, final DamageStyle style,
										   final int hitSplatType, final boolean damageAlreadyTracked) {
		if (!isDamageablePlayer(dragon, player) || !Summoning.canSummonAttack(dragon, player)) {
			return 0;
		}
		damage = Summoning.applySummonOutgoingDamage(dragon, damage);
		damage = applyPlayerMitigation(dragon, player, damage, style);
		damage = Math.max(0, damage);
		if (isPrimaryDamageStyle(style)) {
			damage = TrueDefense.apply(player, damage);
		}

		final DamageRequest damageRequest = DamageRequest.resolvedLegacy(
			dragon, player, DamageRequest.SourceCategory.OWNED_EFFECT,
			damagePolicy(style).getStableKey(), damage)
			.style(combatStyle(style))
			.hitSplatType(hitSplatType)
			.build();
		final DamageResult damageResult = player.getWorld().getServer()
			.getResolvedDamageTransaction().apply(damageRequest);
		final int damageDealt = damageResult.getLegacyDamageDealt();
		if (!damageAlreadyTracked) {
			player.updateDamageAndBlockedDamageTracking(dragon, damageDealt, 0);
		}
		ActionSender.sendStat(player, Skill.HITS.id());
		if (player.getConfig().WANT_PARTIES && player.getParty() != null) {
			player.getParty().sendParty();
		}
		CorrosiveAura.apply(player, dragon, damageDealt);
		DivineRetribution.Result result = DivineRetribution.apply(player, dragon, damageDealt);
		if (result.killedAttacker()) {
			dragon.killedBy(player);
			return damageDealt;
		}
		if (player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			player.killedBy(dragon);
		} else {
			if (damage > 0) {
				player.setLastOpponent(dragon);
				player.setCombatTimer();
			}
			player.checkRingOfLife(dragon);
		}
		return damageDealt;
	}

	private static boolean isDamageablePlayer(final Npc dragon, final Player player) {
		return dragon != null
			&& player != null
			&& player.loggedIn()
			&& !player.isRemoved()
			&& player.getSkills().getLevel(Skill.HITS.id()) > 0;
	}

	private static int applyPlayerMitigation(final Npc dragon, final Player player, int damage, final DamageStyle style) {
		if (style == DamageStyle.MAGIC || style == DamageStyle.BURN) {
			damage = player.applyRobeDamageMitigation(damage, NpcMagicElement.FIRE);
			damage = player.applyPotionMagicDamageReduction(damage);
		} else {
			damage = player.applyRobeDamageMitigation(damage);
			if (style == DamageStyle.RANGED) {
				damage = player.applyPotionRangedDamageReduction(damage);
			} else {
				damage = player.applyPotionMeleeDamageReduction(damage);
			}
		}
		return Summoning.applySummonDamageAbsorption(player, dragon, damage);
	}

	private static boolean isPrimaryDamageStyle(final DamageStyle style) {
		return style == DamageStyle.MELEE || style == DamageStyle.RANGED || style == DamageStyle.MAGIC;
	}

	private static SecondaryEffectPolicy damagePolicy(final DamageStyle style) {
		switch (style) {
			case MELEE:
				return SecondaryEffectPolicy.ELDER_GREEN_DRAGON_MELEE_SWEEP;
			case RANGED:
				return SecondaryEffectPolicy.ELDER_GREEN_DRAGON_RANGED_FIRESHOT;
			case MAGIC:
				return SecondaryEffectPolicy.ELDER_GREEN_DRAGON_MAGIC_SECONDARY;
			case BURN:
				return SecondaryEffectPolicy.ELDER_GREEN_DRAGON_BURN_PULSE;
			default:
				throw new IllegalArgumentException("Unsupported damage style: " + style);
		}
	}

	private static CombatStyle combatStyle(final DamageStyle style) {
		switch (style) {
			case MELEE:
				return CombatStyle.MELEE;
			case RANGED:
				return CombatStyle.RANGED;
			case MAGIC:
				return CombatStyle.MAGIC;
			case BURN:
				return null;
			default:
				throw new IllegalArgumentException("Unsupported damage style: " + style);
		}
	}

	private enum DamageStyle {
		MELEE,
		RANGED,
		MAGIC,
		BURN
	}

	private static final class ElderGreenDragonBurnEvent extends GameTickEvent {
		private ElderGreenDragonBurnEvent(final World world, final Player player) {
			super(world, player, 1, "Elder Green Dragon Burn", DuplicationStrategy.ONE_PER_MOB);
		}

		@Override
		public void run() {
			setDelayTicks(1);
			final Player player = getPlayerOwner();
			if (player == null || !player.loggedIn() || player.isRemoved()
				|| player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				clearBurn(player);
				return;
			}
			final ElderGreenDragonBurnState state = player.getAttribute(BURN_STATE_KEY, null);
			if (state == null || System.currentTimeMillis() >= state.getEndAtMillis()) {
				clearBurn(player);
				return;
			}
			final Npc dragon = getWorld().getNpcByUUID(state.getProvenance().getSourceId());
			if (!isElderGreenDragon(dragon) || dragon.isRemoved()
					|| dragon.getCombatLifecycle() != state.getProvenance().getSourceLifecycle()
					|| dragon.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				clearBurn(player);
				return;
			}
			dragon.setKillType(KillType.MAGIC);
			final int damage = DataConversions.random(BURN_DAMAGE_MIN, BURN_DAMAGE_MAX);
			inflictPlayerDamage(dragon, player, damage, DamageStyle.BURN, HitSplat.TYPE_ARMOR_PROC);
			if (player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				clearBurn(player);
			}
		}

		private void clearBurn(final Player player) {
			ElderGreenDragonSpecialAttacks.clearBurn(player);
			stop();
		}
	}
}
