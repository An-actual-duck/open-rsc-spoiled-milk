package com.openrsc.server.event.rsc.impl.combat;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.CorrosiveAura;
import com.openrsc.server.content.DivineGrace;
import com.openrsc.server.content.DivineRetribution;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.content.PoisonProcChance;
import com.openrsc.server.content.PoisonPower;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.content.TrueDefense;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.projectile.MagicCombatEvent;
import com.openrsc.server.model.Path;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.Equipment.EquipmentSlot;
import com.openrsc.server.model.entity.KillType;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.Damage;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.states.CombatState;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.CombatEffectUtil;
import com.openrsc.server.util.rsc.DataConversions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

public class PvmMeleeEvent extends GameTickEvent {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final int CHAOS_CHAIN_LIGHTNING_MAX_HOPS = 3;
	private static final int CHAOS_CHAIN_LIGHTNING_RADIUS = 4;
	private static final double KOLODION_DEMON_FIRE_CLAW_PROC_CHANCE = 0.10D;
	private static final int FIRE_CLAW_FIRE_DEFENSE_DEBUFF_PERCENT = 6;
	private static final int[] SCYTHE_IDS = {
		ItemId.TIN_SCYTHE.id(), ItemId.COPPER_SCYTHE.id(), ItemId.BRONZE_SCYTHE.id(),
		ItemId.IRON_SCYTHE.id(), ItemId.STEEL_SCYTHE.id(), ItemId.MITHRIL_SCYTHE.id(),
		ItemId.TITAN_STEEL_SCYTHE.id(), ItemId.ADAMANTITE_SCYTHE.id(),
		ItemId.ORICHALCUM_SCYTHE.id(), ItemId.RUNE_SCYTHE.id(),
		ItemId.BLACK_SCYTHE.id(), ItemId.WHITE_SCYTHE.id(), ItemId.GREY_SCYTHE.id()
	};
	private final Mob attackerMob;
	private final Mob targetMob;

	public PvmMeleeEvent(final World world, final Mob attacker, final Mob target) {
		super(world, attacker, 1, "PvM Melee Event", DuplicationStrategy.ONE_PER_MOB);
		this.attackerMob = attacker;
		this.targetMob = target;
		attackerMob.resetRanAwayTimer();
		targetMob.resetRanAwayTimer();
		attacker.getWorld().getServer().getCombatScriptLoader().checkAndExecuteOnStartCombatScript(attacker, target);
		if (attacker.isNpc()) {
			((Npc) attacker).setExecutedAggroScript(false);
		} else if (target.isNpc()) {
			((Npc) target).setExecutedAggroScript(false);
		}
	}

	private boolean targetCanFreelyRetreat() {
		if (!targetMob.isPlayer()) {
			return false;
		}
		Player targetPlayer = (Player) targetMob;
		if (targetPlayer.getAutoRetaliate()) {
			return false;
		}
		if (targetMob.getCombatState() != CombatState.RUNNING) {
			return false;
		}
		int retreatTicks = 5;
		long retreatWindowMs = TimeUnit.MILLISECONDS.convert(retreatTicks * attackerMob.getConfig().GAME_TICK, TimeUnit.MILLISECONDS);
		long timeSinceRetreat = System.currentTimeMillis() - targetMob.getRanAwayTimer();
		return timeSinceRetreat < retreatWindowMs;
	}

	public Mob getAttacker() {
		return attackerMob;
	}

	public Mob getTarget() {
		return targetMob;
	}

	private boolean isNpcMeleeDisabled() {
		return attackerMob.isNpc() && ((Npc) attackerMob).getID() == NpcId.KOLODION_HUMAN.id();
	}

	@Override
	public void run() {
		if (!combatCanContinue()) {
			attackerMob.setLastCombatState(CombatState.ERROR);
			targetMob.setLastCombatState(CombatState.ERROR);
			resetCombat();
			return;
		}

		if (isNpcMeleeDisabled()) {
			attackerMob.faceCombat(targetMob);
			setDelayTicks(3);
			return;
		}

		if (attackerMob.isNpc() && ((Npc) attackerMob).getBehavior().shouldRetreat(((Npc) attackerMob)) && targetMob.getHitsMade() >= 3) {
			((Npc) attackerMob).getBehavior().retreat();
			return;
		}

		if (attackerMob.isPlayer() && isManualDisengage()) {
			attackerMob.setLastOpponent(targetMob);
			attackerMob.setCombatTimer();
			resetCombat();
			return;
		}

		boolean sameTile = attackerMob.getX() == targetMob.getX()
			&& attackerMob.getY() == targetMob.getY();
		boolean adjacent = !sameTile && PathValidation.checkAdjacentDistance(attackerMob.getWorld(),
			attackerMob.getX(), attackerMob.getY(), targetMob.getX(), targetMob.getY(), true, false);
		boolean crowdedSummonAssistReach = Summoning.canSummonUseCrowdedAssistReach(attackerMob, targetMob);
		if ((!attackerMob.withinRange(targetMob, 1) || !adjacent) && !crowdedSummonAssistReach) {
			// Don't force player to walk back if they're hostile and trying to run away
			if (attackerMob.isPlayer() && attackerMob.isHostile() && !attackerMob.finishedPath()) {
				setDelayTicks(1);
				return;
			}
			if (attackerMob.isNpc() && !Summoning.isSummon(attackerMob) && targetOutsideNpcLeash((Npc) attackerMob, targetMob)) {
				Npc attackerNpc = (Npc) attackerMob;
				if (attackerMob.getConfig().WANT_IMPROVED_PATHFINDING) {
					Point origin = new Point(attackerNpc.getLoc().startX(), attackerNpc.getLoc().startY());
					attackerMob.walkToEntityAStar(origin.getX(), origin.getY());
				}
				attackerMob.getSkills().normalize();
				attackerMob.cure();
				attackerNpc.getBehavior().setRoaming();
				resetCombat(false);
				return;
			}
			attackerMob.walkAdjacentToEntity(targetMob);
			setDelayTicks(1);
			return;
		}

		attackerMob.resetPath();
		attackerMob.faceCombat(targetMob);

		boolean attackSuppressed = attackerMob.consumeOgreStaggerDebuff() || attackerMob.consumeStartleDebuff();
		if (ElderGreenDragonSpecialAttacks.shouldUseMeleeSweep(attackerMob, targetMob, attackSuppressed)) {
			ElderGreenDragonSpecialAttacks.applyMeleeSweep(getWorld(), (Npc) attackerMob, targetMob, false);
			if (attackerMob.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				return;
			}
			attackerMob.consumeAttackBasedDebuffs();
			setDelayTicks(getAdjustedMeleeDelayTicks(attackerMob, 2));
			return;
		}

		int damage;
		if (attackSuppressed) {
			damage = 0;
		} else if (getWorld().getServer().getConfig().OSRS_COMBAT_MELEE) {
			damage = OSRSCombatFormula.Melee.doMeleeDamage(attackerMob, targetMob);
		} else {
			damage = CombatFormula.doMeleeDamage(attackerMob, targetMob);
		}
		if (attackerMob.isPlayer()) {
			damage = applyPlayerMeleeDamageBuff((Player) attackerMob, damage);
		}
		inflictDamage(attackerMob, targetMob, damage);
		applyBearMaulSecondHit(attackerMob, targetMob, damage);
		if (!attackSuppressed && !getWorld().getServer().getConfig().OSRS_COMBAT_MELEE) {
			applyDragonWeaponBreathDamage(attackerMob, targetMob);
			applyElementalSwordProc(attackerMob, targetMob);
			applyDemonPitchforkHellBlazeProc(attackerMob, targetMob, damage);
			applyNpcMeleeSpecialProc(attackerMob, targetMob, damage);
		}
		if (attackerMob.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		applyWeaponPoison(attackerMob, targetMob, damage);
		applyChaosAmuletChainLightning(attackerMob, targetMob, damage);
		int scytheTargetsHit = 1;
		if (!attackSuppressed && attackerMob.isPlayer() && targetMob.isNpc()) {
			scytheTargetsHit += applyScytheNpcCleave((Player) attackerMob, (Npc) targetMob);
		}
		if (targetMob.isPlayer()) {
			final Player targetPlayer = (Player) targetMob;
			final double recoilChance = targetPlayer.getCarriedItems().getEquipment().getChaosRecoilChance();
			if (recoilChance > 0.0D) {
				final double recoilRoll = DataConversions.getRandom().nextDouble();
				final int divisor = targetPlayer.getCarriedItems().getEquipment().getChaosRecoilDamageDivisor();
				final int reflectedDamage = damage <= 0 ? 0 : Math.max(1, damage / divisor);
				final boolean proc = recoilRoll < recoilChance;
				if (proc && reflectedDamage > 0) {
					inflictJewelryEffectDamage(targetMob, attackerMob, reflectedDamage);
				}
			}
		}
		attackerMob.consumeAttackBasedDebuffs();
		if (attackerMob.isPlayer()) {
			((Player) attackerMob).consumeLeatherSetAttackBuffs();
		}

		int delayTicks = getAdjustedMeleeDelayTicks(attackerMob, attackerMob.isNpc() && targetMob.isPlayer() ? 2 : 3);
		if (scytheTargetsHit > 1) {
			delayTicks += scytheTargetsHit - 1;
		}
		setDelayTicks(delayTicks);
	}

	private boolean isManualDisengage() {
		if (!attackerMob.isPlayer() || attackerMob.getWalkingQueue().path == null || attackerMob.finishedPath()) {
			return false;
		}
		return attackerMob.getWalkingQueue().path.getPathType() == Path.PathType.WALK_TO_POINT;
	}

	private boolean combatCanContinue() {
		boolean attackerLoggedIn = !attackerMob.isPlayer() || ((Player) attackerMob).loggedIn();
		boolean targetLoggedIn = !targetMob.isPlayer() || ((Player) targetMob).loggedIn();
		boolean attackerRespawning = attackerMob.isNpc() && ((Npc) attackerMob).isRespawning();
		boolean targetRespawning = targetMob.isNpc() && ((Npc) targetMob).isRespawning();
		boolean removed = attackerMob.isRemoved() || targetMob.isRemoved();
		boolean living = attackerMob.getSkills().getLevel(Skill.HITS.id()) > 0 && targetMob.getSkills().getLevel(Skill.HITS.id()) > 0;
		return running && Summoning.canSummonAttack(attackerMob, targetMob)
			&& attackerLoggedIn && targetLoggedIn && !attackerRespawning && !targetRespawning && !removed && living;
	}

	private boolean targetOutsideNpcLeash(final Npc attackerNpc, final Mob target) {
		return target.getX() < (attackerNpc.getLoc().minX() - 4)
			|| target.getX() > (attackerNpc.getLoc().maxX() + 4)
			|| target.getY() < (attackerNpc.getLoc().minY() - 4)
			|| target.getY() > (attackerNpc.getLoc().maxY() + 4);
	}

	private void inflictDamage(final Mob hitter, final Mob target, int damage) {
		if (!Summoning.canSummonAttack(hitter, target)) {
			return;
		}
		hitter.incHitsMade();
		damage = Summoning.applySummonOutgoingDamage(hitter, damage);

		if (target.isPlayer()) {
			Player targetPlayer = (Player) target;
			hitter.getWorld().getServer().getCombatScriptLoader().checkAndExecuteCombatSideEffectScript(hitter, target);

			if (hitter.isNpc()) {
				if (!targetPlayer.getConfig().WANT_MYWORLD && targetPlayer.getPrayers().isPrayerActivated(Prayers.PARALYZE_MONSTER)) {
					setDelayTicks(2);
					return;
				} else {
					hitter.getWorld().getServer().getCombatScriptLoader().checkAndExecuteCombatScript(hitter, target);
				}
			}
		}

		int lastHits = target.getLevel(Skill.HITS.id());
		final int rawDamage = damage;
		if (target.isPlayer()) {
			Player targetPlayer = (Player) target;
			damage = targetPlayer.applyRobeDamageMitigation(damage);
			damage = targetPlayer.applyPotionMeleeDamageReduction(damage);
			if (hitter.isNpc()) {
				damage = Summoning.applySummonDamageAbsorption(targetPlayer, hitter, damage);
			}
		}
		damage = applyFrostbiteReflection(hitter, target, damage);
		if (target.isPlayer()) {
			damage = TrueDefense.apply((Player) target, damage);
		}
		target.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
		final int damageDealt = Math.min(damage, lastHits);
		target.getUpdateFlags().setDamage(new Damage(target, damage));
		target.getUpdateFlags().addHitSplat(new HitSplat(target, Summoning.getSummonDamageHitSplatType(hitter), damage));
		Summoning.applySummonLifesteal(hitter, target, damageDealt);
		if (target.isNpc() && hitter.isPlayer()) {
			Npc n = (Npc) target;
			Player player = ((Player) hitter);
			damage = Math.min(damage, lastHits);
			n.addCombatDamage(player, damage);
			Summoning.recordOwnerCombatSummonDamage(player, n, damage);
			DivineGrace.apply(player, damage);
			if (target.getSkills().getLevel(Skill.HITS.id()) > 0 && player.applyDeathRingChargeHit(n)) {
				onDeath(target, hitter);
				return;
			}
		}
		if (hitter.isPlayer()) {
			((Player) hitter).applyBloodAmuletLifesteal(damageDealt);
		}
		if (target.isPlayer() && hitter.isPlayer()) {
			DivineGrace.apply((Player) hitter, damageDealt);
		}

		if (target.isPlayer()) {
			sendSound((Player) target, hitter, damage > 0);
			ActionSender.sendStat((Player) target, Skill.HITS.id());
			updateParty((Player) target);
			CorrosiveAura.apply((Player) target, hitter, damageDealt);
			DivineRetribution.Result result = DivineRetribution.apply((Player) target, hitter, damageDealt);
			if (result.killedAttacker()) {
				onDeath(hitter, target);
			}
		}
		if (hitter.isPlayer()) {
			sendSound((Player) hitter, target, damage > 0);
			updateParty((Player) hitter);
		}

		if (target.getSkills().getLevel(Skill.HITS.id()) > 0) {
			boolean ringOfLifeScript = false;
			if (target.isPlayer()) {
				Player player = (Player) target;
				ringOfLifeScript = !player.getDuel().isDuelActive() && player.checkRingOfLife(hitter);
			}
			if (target.isNpc() || ringOfLifeScript) {
				target.getWorld().getServer().getCombatScriptLoader().checkAndExecuteCombatScript(hitter, target);
			}

			if (damage > 0) {
				target.setLastOpponent(hitter);
				target.setCombatTimer();
			}
			if (Summoning.applySummonOnHitEffects(hitter, target, damage)) {
				onDeath(target, hitter);
				return;
			}
			applyLeatherSetOnHitEffects(hitter, target, damage);
			tryAutoRetaliateAfterIncomingAttack(hitter, target);
		} else {
			if (target.isNpc() && hitter.isPlayer()) {
				applyDeathRobeOverkillSplash((Player) hitter, (Npc) target, rawDamage - lastHits);
			}
			onDeath(target, hitter);
		}
	}

	private void applyDeathRobeOverkillSplash(final Player player, final Npc primaryTarget, final int overkillDamage) {
		final double splashPercent = player.getDeathRobeOverkillSplashPercent();
		if (overkillDamage <= 0 || splashPercent <= 0.0D) {
			return;
		}
		final int splashDamage = Math.max(1, (int) Math.floor(overkillDamage * splashPercent));
		for (Npc npc : player.getViewArea().getNpcsInView()) {
			if (npc == null || npc == primaryTarget || npc.isRemoved() || npc.isRespawning()
				|| Summoning.isSummon(npc) || !npc.getDef().isAttackable()) {
				continue;
			}
			if (npc.getSkills().getLevel(Skill.HITS.id()) <= 0 || !npc.withinRange(primaryTarget.getLocation(), 2)) {
				continue;
			}
			final int lastHits = npc.getLevel(Skill.HITS.id());
			npc.getSkills().subtractLevel(Skill.HITS.id(), splashDamage, false);
			final int damageDealt = Math.min(splashDamage, lastHits);
			npc.getUpdateFlags().setDamage(new Damage(npc, splashDamage));
			npc.getUpdateFlags().addHitSplat(new HitSplat(npc, HitSplat.TYPE_ARMOR_PROC, splashDamage));
			npc.addCombatDamage(player, damageDealt);
			Summoning.recordOwnerCombatSummonDamage(player, npc, damageDealt);
			if (npc.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				npc.setLastCombatState(CombatState.LOST);
				player.setKillType(KillType.COMBAT);
				npc.killedBy(player);
			}
		}
	}

	private void tryAutoRetaliateAfterIncomingAttack(final Mob hitter, final Mob target) {
		if (!hitter.isNpc() || !target.isPlayer()) {
			return;
		}
		Player targetPlayer = (Player) target;
		if (!targetPlayer.getAutoRetaliate()
			|| targetPlayer.getSkills().getLevel(Skill.HITS.id()) <= 0
			|| hitter.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		PvmMeleeEvent outgoingAttack = targetPlayer.getPvmMeleeEvent();
		if (outgoingAttack != null && outgoingAttack.isRunning() && outgoingAttack.getTarget() == hitter) {
			return;
		}
		if (!targetPlayer.checkAttack(hitter, false)) {
			return;
		}
		if (targetPlayer.getAutoCastSpell() != null && MagicCombatEvent.start(targetPlayer, hitter)) {
			return;
		}
		targetPlayer.startPvmCounterCombat(hitter);
	}

	private int applyFrostbiteReflection(final Mob hitter, final Mob target, int incomingDamage) {
		if (incomingDamage <= 0) {
			return incomingDamage;
		}
		final Mob source = hitter.consumeFrostbiteSource();
		if (source == null) {
			return incomingDamage;
		}
		final int reflectedDamage = Math.max(1, (int) Math.ceil(incomingDamage / 2.0D));
		inflictFrostbiteReflectedDamage(source, hitter, reflectedDamage);
		return Math.max(0, incomingDamage - reflectedDamage);
	}

	private void inflictFrostbiteReflectedDamage(final Mob source, final Mob target, final int damage) {
		if (damage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final Mob creditedSource = source != null ? source : target;
		final int lastHits = target.getLevel(Skill.HITS.id());
		target.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
		target.getUpdateFlags().setDamage(new Damage(target, damage));
		target.getUpdateFlags().addHitSplat(new HitSplat(target, HitSplat.TYPE_ARMOR_PROC, damage));
		if (target.isNpc() && creditedSource.isPlayer()) {
			((Npc) target).addMageDamage((Player) creditedSource, Math.min(damage, lastHits));
		}
		if (target.isPlayer()) {
			ActionSender.sendStat((Player) target, Skill.HITS.id());
		}
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			onDeath(target, creditedSource);
		}
	}

	private void applyLeatherSetOnHitEffects(final Mob hitter, final Mob target, final int damage) {
		if (!hitter.isPlayer() || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final Player player = (Player) hitter;
		if (damage > 0) {
			player.applyElementalGiantMightDebuff(target);
		}
		if (player.hasFullOgreSet() && DataConversions.getRandom().nextDouble() < player.getOgreStaggeringBlowProcChance()) {
			target.applyOgreStaggerDebuff();
		}
		final int smokePercent = player.getBabyDragonSmokeAccuracyDebuffPercent();
		if (smokePercent > 0 && DataConversions.getRandom().nextDouble() < player.getBabyDragonSmokeProcChance()) {
			target.getUpdateFlags().setProjectile(new Projectile(hitter, target, Projectile.BLOW_SMOKE));
			target.applySmokeAccuracyDebuff(smokePercent);
		}
		final int infernalMaxHit = player.getInfernalFireProcMaxHit();
		final int infernalPieces = player.getInfernalArmorPieceCount();
		if (infernalMaxHit > 0) {
			final double infernalChance = player.getInfernalFireProcChance();
			final double infernalRoll = DataConversions.getRandom().nextDouble();
			final boolean infernalProc = infernalRoll < infernalChance;
			int procDamage = 0;
			int procDamageDealt = 0;
			if (infernalProc) {
				target.getUpdateFlags().setCombatEffect(new CombatEffect(target, CombatEffect.infernalEffectForMaxHit(infernalMaxHit)));
				procDamage = DataConversions.random(0, infernalMaxHit);
				procDamageDealt = inflictAuxiliaryMagicDamage(hitter, target, procDamage);
				target.applyInfernalFireDefenseDebuff(player.getInfernalFireDefenseDebuffPercent());
			}
			CombatEffectUtil.sendInfernalProcDebug(player, "pvm_melee", target, damage, infernalPieces,
				infernalMaxHit, infernalRoll, infernalChance, infernalProc, procDamage, procDamageDealt);
		} else if (infernalPieces > 0) {
			CombatEffectUtil.sendInfernalProcDebug(player, "pvm_melee", target, damage, infernalPieces,
				infernalMaxHit, -1.0D, 0.0D, false, 0, 0);
		}
		if (player.hasFullBlueDragonSet() && DataConversions.getRandom().nextDouble() < 0.20D) {
			final int procDamage = DataConversions.random(0, 10);
			if (procDamage > 0) {
				inflictAuxiliaryTrueDamage(hitter, target, procDamage);
			}
			target.applyDragonWaterMaxHitDebuff(10);
		}
		if (player.hasFullEarthDragonSet() && DataConversions.getRandom().nextDouble() < 0.20D) {
			final int procDamage = DataConversions.random(0, 10);
			if (procDamage > 0) {
				inflictAuxiliaryTrueDamage(hitter, target, procDamage);
			}
			target.applyDragonEarthAttackSpeedDebuff(6);
		}
		if (player.hasFullRedDragonSet() && DataConversions.getRandom().nextDouble() < 0.20D) {
			final int procDamage = DataConversions.random(0, 10);
			if (procDamage > 0) {
				inflictAuxiliaryTrueDamage(hitter, target, procDamage);
			}
			target.applyDragonFireDefenseDebuff(6);
		}
		if ("black".equals(player.getAttribute("dragon_breath_armor_proc", ""))
			|| "elder_green".equals(player.getAttribute("dragon_breath_armor_proc", ""))) {
			player.getUpdateFlags().setCombatEffect(new CombatEffect(player, CombatEffect.DRAGON_BREATH));
		}
		if (player.hasFullBlackDragonSet() && "black".equals(player.getAttribute("dragon_breath_armor_proc", ""))) {
			final int procDamage = DataConversions.random(0, 10);
			if (procDamage > 0) {
				inflictAuxiliaryTrueDamage(hitter, target, procDamage);
			}
		}
		if (player.hasFullElderGreenDragonSet() && "elder_green".equals(player.getAttribute("dragon_breath_armor_proc", ""))) {
			final int procDamage = DataConversions.random(0, 10);
			if (procDamage > 0) {
				inflictAuxiliaryTrueDamage(hitter, target, procDamage);
			}
			switch (DataConversions.random(0, 2)) {
				case 0:
					target.applyDragonWaterMaxHitDebuff(10);
					break;
				case 1:
					target.applyDragonEarthAttackSpeedDebuff(6);
					break;
				default:
					target.applyDragonFireDefenseDebuff(6);
					break;
			}
		}
	}

	private int applyPlayerMeleeDamageBuff(final Player player, final int damage) {
		if (damage <= 0) {
			return damage;
		}
		final int buffedDamage = Math.max(0, (int) Math.floor(damage * player.getLeatherSetMeleeDamageMultiplier()));
		return player.applyBearMaulDamage(buffedDamage);
	}

	private void applyBearMaulSecondHit(final Mob hitter, final Mob target, final int damage) {
		if (!hitter.isPlayer() || !((Player) hitter).hasFullBearHideSet() || damage <= 0
			|| target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		inflictAuxiliaryTrueDamage(hitter, target, damage);
	}

	private void applyDragonWeaponBreathDamage(final Mob hitter, final Mob target) {
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final int breathDamage = CombatFormula.rollDragonMeleeBreathDamage(hitter);
		if (breathDamage <= 0) {
			return;
		}
		final int slashEffect = DataConversions.random(0, 1) == 0
			? CombatEffect.DRAGON_WEAPON_BREATH
			: CombatEffect.DRAGON_WEAPON_SLASH_2;
		target.getUpdateFlags().setCombatEffect(new CombatEffect(target, slashEffect));
		inflictAuxiliaryTrueDamage(hitter, target, breathDamage);
	}

	private void applyElementalSwordProc(final Mob hitter, final Mob target) {
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final int effectType = CombatFormula.getElementalSwordProcEffect(hitter);
		if (effectType == CombatEffect.NONE || !CombatFormula.rollElementalSwordProcChance()) {
			return;
		}
		if (effectType == CombatEffect.ICE_SWORD) {
			target.getUpdateFlags().setProjectile(new Projectile(hitter, target, Projectile.ICE_SWORD_STAB));
		} else {
			target.getUpdateFlags().setCombatEffect(new CombatEffect(target, effectType));
		}
		CombatFormula.applyElementalSwordProcDebuff(target, effectType);
		final int procDamage = CombatFormula.rollElementalSwordProcDamage(hitter);
		if (procDamage > 0) {
			inflictAuxiliaryTrueDamage(hitter, target, procDamage);
		}
	}

	private void applyDemonPitchforkHellBlazeProc(final Mob hitter, final Mob target, final int damage) {
		if (damage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		if (!CombatFormula.rollDemonPitchforkHellBlazeProc(hitter)) {
			return;
		}
		target.getUpdateFlags().setCombatEffect(new CombatEffect(target, CombatEffect.HELLS_BLAZE));
		final int procDamage = CombatFormula.rollDemonPitchforkHellBlazeDamage();
		if (procDamage > 0) {
			inflictAuxiliaryMagicDamage(hitter, target, procDamage);
		}
	}

	private void applyNpcMeleeSpecialProc(final Mob hitter, final Mob target, final int damage) {
		if (!hitter.isNpc() || damage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final Npc npc = (Npc) hitter;
		if (npc.getID() != NpcId.KOLODION_DEMON.id()
			|| DataConversions.getRandom().nextDouble() >= KOLODION_DEMON_FIRE_CLAW_PROC_CHANCE) {
			return;
		}
		target.getUpdateFlags().setCombatEffect(new CombatEffect(target, CombatEffect.FIRE_CLAW));
		target.applyFireDefenseDebuff(FIRE_CLAW_FIRE_DEFENSE_DEBUFF_PERCENT);
	}

	private int applyScytheNpcCleave(final Player player, final Npc primaryTarget) {
		if (!isScytheEquipped(player)) {
			return 0;
		}

		int extraTargetsHit = 0;
		for (Npc npc : player.getViewArea().getNpcsInView()) {
			if (!isValidScytheCleaveTarget(player, primaryTarget, npc)) {
				continue;
			}
			int damage;
			if (getWorld().getServer().getConfig().OSRS_COMBAT_MELEE) {
				damage = OSRSCombatFormula.Melee.doMeleeDamage(player, npc);
			} else {
				damage = CombatFormula.doMeleeDamage(player, npc);
			}
			damage = applyPlayerMeleeDamageBuff(player, damage);
			inflictScytheCleaveDamage(player, npc, damage);
			applyBearMaulSecondHit(player, npc, damage);
			if (!getWorld().getServer().getConfig().OSRS_COMBAT_MELEE) {
				applyDragonWeaponBreathDamage(player, npc);
				applyElementalSwordProc(player, npc);
			}
			if (player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				return extraTargetsHit;
			}
			applyWeaponPoison(player, npc, damage);
			extraTargetsHit++;
		}
		return extraTargetsHit;
	}

	private boolean isScytheEquipped(final Player player) {
		Item weapon = player.getCarriedItems().getEquipment().get(EquipmentSlot.SLOT_MAINHAND.getIndex());
		if (weapon == null) {
			return false;
		}
		for (int scytheId : SCYTHE_IDS) {
			if (weapon.getCatalogId() == scytheId) {
				return true;
			}
		}
		return false;
	}

	private boolean isValidScytheCleaveTarget(final Player player, final Npc primaryTarget, final Npc npc) {
		if (npc == null || npc == primaryTarget || npc.isRemoved() || npc.isRespawning()) {
			return false;
		}
		if (npc.getSkills().getLevel(Skill.HITS.id()) <= 0 || Summoning.isSummon(npc)
			|| !npc.getDef().isAttackable()) {
			return false;
		}
		int xDiff = Math.abs(player.getX() - npc.getX());
		int yDiff = Math.abs(player.getY() - npc.getY());
		return xDiff <= 1 && yDiff <= 1 && (xDiff != 0 || yDiff != 0);
	}

	private void inflictScytheCleaveDamage(final Player player, final Npc npc, int damage) {
		if (Summoning.isSummon(npc)) {
			return;
		}
		if (damage <= 0 || npc.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			if (damage == 0 && npc.getSkills().getLevel(Skill.HITS.id()) > 0) {
				npc.getUpdateFlags().setDamage(new Damage(npc, 0));
				npc.getUpdateFlags().addHitSplat(new HitSplat(npc, HitSplat.TYPE_STANDARD, 0));
				triggerScytheCleaveAggro(player, npc);
			}
			return;
		}
		damage = Summoning.applySummonOutgoingDamage(player, damage);
		final int lastHits = npc.getLevel(Skill.HITS.id());
		npc.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
		final int damageDealt = Math.min(damage, lastHits);
		npc.getUpdateFlags().setDamage(new Damage(npc, damage));
		npc.getUpdateFlags().addHitSplat(new HitSplat(npc, HitSplat.TYPE_STANDARD, damage));
		npc.addCombatDamage(player, damageDealt);
		Summoning.recordOwnerCombatSummonDamage(player, npc, damageDealt);
		DivineGrace.apply(player, damageDealt);
		player.applyBloodAmuletLifesteal(damageDealt);
		Summoning.applySummonLifesteal(player, npc, damageDealt);
		if (npc.getSkills().getLevel(Skill.HITS.id()) > 0 && player.applyDeathRingChargeHit(npc)) {
			npc.setLastCombatState(CombatState.LOST);
			player.setKillType(KillType.COMBAT);
			npc.killedBy(player);
			updateParty(player);
			return;
		}
		if (npc.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			npc.setLastCombatState(CombatState.LOST);
			player.setKillType(KillType.COMBAT);
			applyDeathRobeOverkillSplash(player, npc, damage - lastHits);
			npc.killedBy(player);
			updateParty(player);
		} else if (damage > 0) {
			npc.setLastOpponent(player);
			npc.setCombatTimer();
			triggerScytheCleaveAggro(player, npc);
		}
	}

	private void triggerScytheCleaveAggro(final Player player, final Npc npc) {
		if (npc.getSkills().getLevel(Skill.HITS.id()) <= 0 || Summoning.isSummon(npc)) {
			return;
		}
		PvmMeleeEvent existingEvent = npc.getPvmMeleeEvent();
		if (existingEvent != null && existingEvent.isRunning()) {
			return;
		}
		npc.startPvmCounterCombat(player);
	}

	private int inflictAuxiliaryMagicDamage(final Mob hitter, final Mob target, int damage) {
		if (damage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return 0;
		}
		if (target.isPlayer()) {
			Player targetPlayer = (Player) target;
			damage = targetPlayer.applyRobeDamageMitigation(damage);
			damage = targetPlayer.applyPotionMagicDamageReduction(damage);
		}
		if (damage <= 0) {
			return 0;
		}

		final int lastHits = target.getLevel(Skill.HITS.id());
		target.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
		final int damageDealt = Math.min(damage, lastHits);
		target.getUpdateFlags().setDamage(new Damage(target, damage));
		target.getUpdateFlags().addHitSplat(new HitSplat(target, HitSplat.TYPE_ARMOR_PROC, damage));
		if (target.isNpc() && hitter.isPlayer()) {
			((Npc) target).addMageDamage((Player) hitter, damageDealt);
		}
		if (target.isPlayer()) {
			ActionSender.sendStat((Player) target, Skill.HITS.id());
		}
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			onDeath(target, hitter);
		}
		return damageDealt;
	}

	private void inflictAuxiliaryTrueDamage(final Mob hitter, final Mob target, int damage) {
		if (damage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		if (target.isPlayer()) {
			damage = ((Player) target).applyRobeDamageMitigation(damage);
		}
		if (damage <= 0) {
			return;
		}

		final int lastHits = target.getLevel(Skill.HITS.id());
		target.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
		target.getUpdateFlags().setDamage(new Damage(target, damage));
		target.getUpdateFlags().addHitSplat(new HitSplat(target, HitSplat.TYPE_ARMOR_PROC, damage));
		if (target.isNpc() && hitter.isPlayer()) {
			((Npc) target).addCombatDamage((Player) hitter, Math.min(damage, lastHits));
		}
		if (target.isPlayer()) {
			ActionSender.sendStat((Player) target, Skill.HITS.id());
		}
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			onDeath(target, hitter);
		}
	}

	private void onDeath(final Mob killed, final Mob killer) {
		killed.setLastCombatState(CombatState.LOST);
		killer.setLastCombatState(CombatState.WON);
		killer.setKillType(KillType.COMBAT);
		killed.killedBy(killer);
		if (killer.isPlayer() && killer != attackerMob) {
			updateParty((Player) killer);
		}
		resetCombat();
	}

	private void applyChaosAmuletChainLightning(final Mob hitter, final Mob target, final int baseDamage) {
		if (!hitter.isPlayer() || baseDamage <= 0 || !target.isNpc()) {
			return;
		}
		final Player player = (Player) hitter;
		final double chainChance = player.getCarriedItems().getEquipment().getChaosNecklaceChainLightningChance();
		if (chainChance <= 0.0D) {
			return;
		}
		Mob anchor = target;
		int chainDamage = Math.max(1, (int) Math.ceil(baseDamage / 2.0D));
		for (int hop = 0; hop < CHAOS_CHAIN_LIGHTNING_MAX_HOPS; hop++) {
			if (DataConversions.getRandom().nextDouble() >= chainChance) {
				break;
			}
			final Mob chainTarget = selectChaosChainLightningTarget(player, anchor);
			if (chainTarget == null) {
				break;
			}
			chainTarget.getUpdateFlags().setProjectile(new Projectile(anchor, chainTarget, getChaosChainLightningProjectile(hop)));
			inflictJewelryEffectDamage(hitter, chainTarget, chainDamage);
			anchor = chainTarget;
			chainDamage = Math.max(1, (int) Math.ceil(chainDamage / 2.0D));
		}
	}

	private int getChaosChainLightningProjectile(final int hop) {
		switch (hop % 3) {
			case 0:
				return Projectile.CHAIN_LIGHTNING_A;
			case 1:
				return Projectile.CHAIN_LIGHTNING_B;
			default:
				return Projectile.CHAIN_LIGHTNING_C;
		}
	}

	private void inflictJewelryEffectDamage(final Mob hitter, final Mob target, final int damage) {
		if (damage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final int lastHits = target.getLevel(Skill.HITS.id());
		target.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
		target.getUpdateFlags().setDamage(new Damage(target, damage));
		target.getUpdateFlags().addHitSplat(new HitSplat(target, HitSplat.TYPE_ARMOR_PROC, damage));
		if (target.isNpc() && hitter.isPlayer()) {
			((Npc) target).addCombatDamage((Player) hitter, Math.min(damage, lastHits));
		}
		if (target.isPlayer()) {
			ActionSender.sendStat((Player) target, Skill.HITS.id());
		}
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			onDeath(target, hitter);
		}
	}

	private Mob selectChaosChainLightningTarget(final Player player, final Mob primaryTarget) {
		if (primaryTarget == null) {
			return null;
		}
		if (!primaryTarget.isNpc()) {
			return null;
		}
		final java.util.ArrayList<Npc> candidates = new java.util.ArrayList<Npc>();
		for (Npc npc : player.getViewArea().getNpcsInView()) {
			if (npc != null && npc != primaryTarget && !npc.isRemoved() && npc.getSkills().getLevel(Skill.HITS.id()) > 0
				&& npc.getDef().isAttackable()
				&& !Summoning.isSummon(npc)
				&& npc.withinRange(primaryTarget.getLocation(), CHAOS_CHAIN_LIGHTNING_RADIUS)) {
				candidates.add(npc);
			}
		}
		if (candidates.isEmpty()) {
			return null;
		}
		return candidates.get(DataConversions.random(0, candidates.size() - 1));
	}

	private void applyWeaponPoison(final Mob hitter, final Mob target, final int damage) {
		if (!hitter.isPlayer() || damage <= 0) {
			return;
		}
		final Player player = (Player) hitter;
		player.removeAttribute("dragon_breath_armor_proc");
		final Item weapon = player.getCarriedItems().getEquipment().get(EquipmentSlot.SLOT_MAINHAND.getIndex());
		final int poisonWeaponId = weapon == null ? -1 : weapon.getCatalogId();
		final int weaponMaxPower = PoisonPower.getWeaponMaxPoisonPower(poisonWeaponId);
		final int styleArmorMaxPower = player.getMeleePoisonArmorMaxPower();
		final int breathArmorMaxPower = player.hasFullBlackDragonSet() ? 30 : (player.hasFullElderGreenDragonSet() ? 40 : 0);
		final int armorMaxPower = styleArmorMaxPower + breathArmorMaxPower;
		final int totalMaxPower = weaponMaxPower + armorMaxPower;
		if (totalMaxPower <= 0) {
			return;
		}

		int appliedPoisonPower = 0;
		if (weaponMaxPower > 0 && PoisonProcChance.rollWeapon(player, target, poisonWeaponId)) {
			appliedPoisonPower = Math.max(appliedPoisonPower, PoisonPower.getWeaponAppliedPoisonPower(poisonWeaponId));
		}
		final boolean armorPoisonProc = styleArmorMaxPower > 0
			&& PoisonProcChance.rollArmor(player, target, "melee");
		if (armorPoisonProc) {
			appliedPoisonPower = Math.max(appliedPoisonPower, player.getMeleePoisonArmorAppliedPower());
			target.getUpdateFlags().setProjectile(new Projectile(player, target, Projectile.ACID_ARMOR_PROC));
		}
		if (player.hasFullBlackDragonSet() && DataConversions.getRandom().nextDouble() < 0.20D) {
			appliedPoisonPower = Math.max(appliedPoisonPower, 15);
			player.setAttribute("dragon_breath_armor_proc", "black");
		} else if (player.hasFullElderGreenDragonSet() && DataConversions.getRandom().nextDouble() < 0.60D) {
			appliedPoisonPower = Math.max(appliedPoisonPower, 20);
			player.setAttribute("dragon_breath_armor_proc", "elder_green");
		}
		if (appliedPoisonPower <= 0) {
			return;
		}

			target.applyPoison(appliedPoisonPower, totalMaxPower, player);
		if (target.isNpc()) {
			player.message("@gr3@You @gr2@have @gr1@poisioned @gr2@the " + ((Npc) target).getDef().name + "!");
		}
	}

	private int getAdjustedMeleeDelayTicks(final Mob hitter, final int baseDelayTicks) {
		double multiplier = getCombatSpeedMultiplier(hitter);
		double effectiveDelay = baseDelayTicks / multiplier;
		return Math.max(1, (int) Math.floor(effectiveDelay));
	}

	private double getCombatSpeedMultiplier(final Mob hitter) {
		double multiplier = 1.0D;
		if (hitter.isPlayer()) {
			Player player = (Player) hitter;
			multiplier = getWeaponSpeedMultiplier(player) * player.getPotionAttackSpeedMultiplier() * player.getLeatherSetAttackSpeedMultiplier();
		}
		if (hitter.getEarthAttackSpeedDebuffPercent() > 0) {
			multiplier *= Math.max(0.10D, 1.0D - (hitter.getEarthAttackSpeedDebuffPercent() / 100.0D));
		}
		return multiplier;
	}

	private double getWeaponSpeedMultiplier(final Player player) {
		int weaponSpeed = player.getCarriedItems().getEquipment().getWeaponSpeed();
		double weaponMultiplier;
		switch (weaponSpeed) {
			case 1:
				weaponMultiplier = 0.8D;
				break;
			case 2:
				weaponMultiplier = 0.9D;
				break;
			case 4:
				weaponMultiplier = 1.1D;
				break;
			case 5:
				weaponMultiplier = 1.2D;
				break;
			case 3:
			default:
				weaponMultiplier = 1.0D;
				break;
		}
		return weaponMultiplier * player.getArmorSpeedMultiplier();
	}

	private void sendSound(final Player player, final Mob mob, final boolean damaged) {
		final String combatSound;
		if (mob.isPlayer() || DataConversions.inArray(Constants.ARMOR_NPCS, mob.getID())) {
			combatSound = damaged ? "combat2b" : "combat2a";
		} else if (mob.isNpc() && DataConversions.inArray(Constants.UNDEAD_NPCS, mob.getID())) {
			combatSound = damaged ? "combat3b" : "combat3a";
		} else {
			combatSound = damaged ? "combat1b" : "combat1a";
		}
		ActionSender.sendSound(player, combatSound);
	}

	private void updateParty(final Player player) {
		if (getWorld().getServer().getConfig().WANT_PARTIES && player.getParty() != null) {
			player.getParty().sendParty();
		}
	}

	public void resetCombat() {
		resetCombat(true);
	}

	public void resetCombat(final boolean resetAttackerState) {
		if (attackerMob != null) {
			if (resetAttackerState && attackerMob.isPlayer()) {
				((Player) attackerMob).resetAll();
			}
			if (attackerMob.getOpponent() == targetMob) {
				attackerMob.setOpponent(null);
			}
			if (attackerMob.isHostileToward(targetMob)) {
				attackerMob.clearHostility();
			}
			attackerMob.setPvmMeleeEvent(null);
			attackerMob.setHitsMade(0);
			if (attackerMob.getSprite() > 7) {
				attackerMob.setSprite(4);
				attackerMob.face(attackerMob.getX(), attackerMob.getY() - 1);
			}
			attackerMob.setCombatTimer();
		}
		if (targetMob != null && targetMob.getOpponent() == attackerMob && !targetStillHasOutgoingCombat()) {
			targetMob.setOpponent(null);
			if (targetMob.isHostileToward(attackerMob)) {
				targetMob.clearHostility();
			}
			targetMob.setHitsMade(0);
			if (targetMob.getCombatEvent() == null && targetMob.getPvmMeleeEvent() == null && targetMob.getSprite() > 7) {
				targetMob.setSprite(4);
				targetMob.face(targetMob.getX(), targetMob.getY() - 1);
			}
		}
		stop();
	}

	private boolean targetStillHasOutgoingCombat() {
		if (targetMob.getCombatEvent() != null && targetMob.getCombatEvent().isRunning()) {
			return true;
		}
		PvmMeleeEvent targetEvent = targetMob.getPvmMeleeEvent();
		return targetEvent != null && targetEvent.isRunning() && targetEvent.getTarget() == attackerMob;
	}
}
