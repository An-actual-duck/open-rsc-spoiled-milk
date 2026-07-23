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
import com.openrsc.server.model.entity.KillType;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcMagicElement;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.Damage;
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
	private static final String BURN_ACTIVE_KEY = "elder_green_dragon_burn_active";
	private static final String BURN_END_AT_KEY = "elder_green_dragon_burn_end_at";
	private static final String BURN_SOURCE_KEY = "elder_green_dragon_burn_source";

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
		player.setAttribute(BURN_END_AT_KEY, System.currentTimeMillis() + BURN_DURATION_MILLIS);
		player.setAttribute(BURN_SOURCE_KEY, dragon);
		if (player.getAttribute(BURN_ACTIVE_KEY, false)) {
			return;
		}
		player.setAttribute(BURN_ACTIVE_KEY, true);
		world.getServer().getGameEventHandler().add(new ElderGreenDragonBurnEvent(world, player));
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
				dragon.getWorld(), dragon.getLocation(), player.getLocation());
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

		final int lastHits = player.getLevel(Skill.HITS.id());
		player.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
		final int damageDealt = Math.min(damage, lastHits);
		player.getUpdateFlags().setDamage(new Damage(player, damage));
		player.getUpdateFlags().addHitSplat(new HitSplat(player, hitSplatType, damage));
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
			final long burnEndAt = player.getAttribute(BURN_END_AT_KEY, 0L);
			if (System.currentTimeMillis() >= burnEndAt) {
				clearBurn(player);
				return;
			}
			final Npc dragon = player.getAttribute(BURN_SOURCE_KEY, null);
			if (dragon == null || dragon.isRemoved()) {
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
			if (player != null) {
				player.removeAttribute(BURN_ACTIVE_KEY);
				player.removeAttribute(BURN_END_AT_KEY);
				player.removeAttribute(BURN_SOURCE_KEY);
			}
			stop();
		}
	}
}
