package com.openrsc.server.event.rsc.impl.projectile;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.content.PoisonProcChance;
import com.openrsc.server.content.PoisonPower;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.SingleTickEvent;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.Damage;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.states.CombatState;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.CombatEffectUtil;
import com.openrsc.server.util.rsc.DataConversions;

public class ProjectileEvent extends SingleTickEvent {

	Mob caster, opponent;
	protected int damage;
	protected int windAccuracyDebuffPercent;
	protected int waterMaxHitDebuffPercent;
	protected int earthAttackSpeedDebuffPercent;
	protected int fireDefenseDebuffPercent;
	protected int startleProcChancePercent;
	protected int acidPoisonPower;
	protected int frostbiteProcChancePercent;
	protected int splinterProcChancePercent;
	protected int poisonWeaponId;
	protected int type;
	protected int projectileType;
	protected int impactEffectType;
	protected boolean showProjectile;
	boolean canceled;
	boolean shouldChase;

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type) {
		this(world, caster, opponent, damage, type, true, -1);
	}

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type, boolean setChasing) {
		this(world, caster, opponent, damage, type, setChasing, -1, 0, 0, 0, 0, DuplicationStrategy.ONE_PER_MOB, type, 0, true);
	}

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type, boolean setChasing, int poisonWeaponId) {
		this(world, caster, opponent, damage, type, setChasing, poisonWeaponId, 0, 0, 0, 0, DuplicationStrategy.ONE_PER_MOB, type, 0, true);
	}

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type, boolean setChasing, int windAccuracyDebuffPercent, int waterMaxHitDebuffPercent, int earthAttackSpeedDebuffPercent, int fireDefenseDebuffPercent) {
		this(world, caster, opponent, damage, type, setChasing, -1, windAccuracyDebuffPercent, waterMaxHitDebuffPercent, earthAttackSpeedDebuffPercent, fireDefenseDebuffPercent, DuplicationStrategy.ONE_PER_MOB, type, 0, true);
	}

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type, boolean setChasing,
						   int windAccuracyDebuffPercent, int waterMaxHitDebuffPercent, int earthAttackSpeedDebuffPercent,
						   int fireDefenseDebuffPercent, int projectileType, int impactEffectType, boolean showProjectile) {
		this(world, caster, opponent, damage, type, setChasing, -1, windAccuracyDebuffPercent, waterMaxHitDebuffPercent,
			earthAttackSpeedDebuffPercent, fireDefenseDebuffPercent, DuplicationStrategy.ONE_PER_MOB,
			projectileType, impactEffectType, showProjectile);
	}

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type, boolean setChasing,
						   int windAccuracyDebuffPercent, int waterMaxHitDebuffPercent, int earthAttackSpeedDebuffPercent,
						   int fireDefenseDebuffPercent, int projectileType, int impactEffectType, boolean showProjectile,
						   int startleProcChancePercent, int acidPoisonPower, int frostbiteProcChancePercent,
						   int splinterProcChancePercent) {
		this(world, caster, opponent, damage, type, setChasing, windAccuracyDebuffPercent, waterMaxHitDebuffPercent,
			earthAttackSpeedDebuffPercent, fireDefenseDebuffPercent, projectileType, impactEffectType, showProjectile);
		this.startleProcChancePercent = startleProcChancePercent;
		this.acidPoisonPower = acidPoisonPower;
		this.frostbiteProcChancePercent = frostbiteProcChancePercent;
		this.splinterProcChancePercent = splinterProcChancePercent;
	}

	public ProjectileEvent(final World world, final Mob caster, final Mob opponent, final int damage, final int type,
						   final boolean setChasing, final DuplicationStrategy duplicationStrategy)
	{
		this(world, caster, opponent, damage, type, setChasing, -1, 0, 0, 0, 0, duplicationStrategy, type, 0, true);
	}

	public ProjectileEvent(final World world, final Mob caster, final Mob opponent, final int damage, final int type,
						   final boolean setChasing, final int poisonWeaponId, final int windAccuracyDebuffPercent, final int waterMaxHitDebuffPercent,
						   final int earthAttackSpeedDebuffPercent, final int fireDefenseDebuffPercent, final DuplicationStrategy duplicationStrategy,
						   final int projectileType, final int impactEffectType, final boolean showProjectile)
	{
		super(world, caster, 1, "Projectile Event", duplicationStrategy);
		this.caster = caster;
		this.opponent = opponent;
		this.damage = damage;
		this.poisonWeaponId = poisonWeaponId;
		this.windAccuracyDebuffPercent = windAccuracyDebuffPercent;
		this.waterMaxHitDebuffPercent = waterMaxHitDebuffPercent;
		this.earthAttackSpeedDebuffPercent = earthAttackSpeedDebuffPercent;
		this.fireDefenseDebuffPercent = fireDefenseDebuffPercent;
		this.type = type;
		this.projectileType = projectileType;
		this.impactEffectType = impactEffectType;
		this.showProjectile = showProjectile;
		this.shouldChase = setChasing;

		if (this.showProjectile) {
			sendProjectile(caster, opponent);
		}
		if (caster.isPlayer() && opponent.isPlayer()) {
			Player oppPlayer = (Player) opponent;
			Player casterPlayer = (Player) caster;
			if (!casterPlayer.getDuel().isDuelActive())
				casterPlayer.setSkulledOn(oppPlayer);
			String casterName = casterPlayer.getUsername();

			oppPlayer.message("Warning! " + casterName + " is shooting at you!");
		}
	}

	public ProjectileEvent(final World world, final Mob caster, final Mob opponent, final int damage, final int type,
						   final boolean setChasing, final int windAccuracyDebuffPercent, final int waterMaxHitDebuffPercent,
						   final int earthAttackSpeedDebuffPercent, final int fireDefenseDebuffPercent, final DuplicationStrategy duplicationStrategy)
	{
		this(world, caster, opponent, damage, type, setChasing, -1, windAccuracyDebuffPercent, waterMaxHitDebuffPercent,
			earthAttackSpeedDebuffPercent, fireDefenseDebuffPercent, duplicationStrategy, type, 0, true);
	}

	private void sendProjectile(Mob caster, Mob opponent) {
		Projectile projectile = new Projectile(caster, opponent, projectileType);
		caster.getUpdateFlags().setProjectile(projectile);
	}

	@Override
	public void action() {
		if (!canceled && caster.withinRange(opponent, 15)) {// maybe this will
			// cancel the damage
			// out on death
			projectileDamage();
			applyChaosAmuletSecondHit();
			if (opponent.isPlayer()) {
				final Player opponentPlayer = (Player) opponent;
				if (opponentPlayer.getCarriedItems().getEquipment().getChaosRecoilChance() > 0.0D) {
					recoilDamage(opponentPlayer, caster, damage);
				} else if (opponent.getSkills().getLevel(Skill.HITS.id()) > 0) {
					if (opponentPlayer.checkRingOfLife(caster))
						return;
				}
			}
			caster.consumeAttackBasedDebuffs();
			if (caster.isPlayer()) {
				((Player) caster).consumeLeatherSetAttackBuffs();
			}
		}
	}

	private void applyChaosAmuletSecondHit() {
		if (!caster.isPlayer() || damage <= 0 || opponent.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final Player casterPlayer = (Player) caster;
		final double procChance = casterPlayer.getCarriedItems().getEquipment().getChaosAmuletSecondHitChance();
		if (procChance <= 0.0D) {
			return;
		}
		final double roll = DataConversions.getRandom().nextDouble();
		if (roll >= procChance) {
			return;
		}

		final Mob splashTarget = selectChaosAmuletSplashTarget(casterPlayer, opponent);
		if (splashTarget == null) {
			return;
		}
		int extraDamage = Math.max(1, (int) Math.ceil(damage / 2.0D));
		if (splashTarget.isPlayer()) {
			Player opponentPlayer = (Player) splashTarget;
			if (type == 1 || type == 4) {
				extraDamage = opponentPlayer.applyPotionMagicDamageReduction(extraDamage);
			} else if (type == 2 || type == 5) {
				extraDamage = opponentPlayer.applyPotionRangedDamageReduction(extraDamage);
			}
		}
		int lastHits = splashTarget.getLevel(Skill.HITS.id());
		splashTarget.getSkills().subtractLevel(Skill.HITS.id(), extraDamage, false);
		splashTarget.getUpdateFlags().setDamage(new Damage(splashTarget, extraDamage));
		splashTarget.getUpdateFlags().addHitSplat(new HitSplat(splashTarget, HitSplat.TYPE_ARMOR_PROC, extraDamage));
		if (splashTarget.isNpc()) {
			Npc npc = (Npc) splashTarget;
			final int dealtDamage = Math.min(extraDamage, lastHits);
			if (type == 1 || type == 4) {
				npc.addMageDamage(casterPlayer, dealtDamage);
			} else if (type == 2 || type == 5) {
				npc.addRangeDamage(casterPlayer, dealtDamage);
			}
			casterPlayer.applyBloodAmuletLifesteal(dealtDamage);
		}
		if (splashTarget.isPlayer()) {
			ActionSender.sendStat((Player) splashTarget, Skill.HITS.id());
		}
		if (splashTarget == opponent && splashTarget.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			handleDeath();
		} else if (splashTarget.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			splashTarget.killedBy(caster);
		}
	}

	private Mob selectChaosAmuletSplashTarget(final Player player, final Mob primaryTarget) {
		if (!primaryTarget.isNpc()) {
			return primaryTarget;
		}
		final java.util.ArrayList<Npc> candidates = new java.util.ArrayList<Npc>();
		for (Npc npc : player.getViewArea().getNpcsInView()) {
			if (npc != null && !npc.isRemoved() && npc.getSkills().getLevel(Skill.HITS.id()) > 0
				&& npc.withinRange(primaryTarget.getLocation(), 4)) {
				candidates.add(npc);
			}
		}
		if (candidates.isEmpty()) {
			return null;
		}
		return candidates.get(DataConversions.random(0, candidates.size() - 1));
	}

	private void recoilDamage(Player opponent, Mob caster, int damage) {
		final double recoilChance = opponent.getCarriedItems().getEquipment().getChaosRecoilChance();
		if (recoilChance <= 0.0D) {
			return;
		}
		final double recoilRoll = DataConversions.getRandom().nextDouble();
		final int divisor = opponent.getCarriedItems().getEquipment().getChaosRecoilDamageDivisor();
		int reflectedDamage = damage / divisor + ((damage > 0) ? 1 : 0);
		final boolean proc = recoilRoll < recoilChance;
		if (!proc || reflectedDamage == 0)
			return;

		caster.getSkills().subtractLevel(Skill.HITS.id(), reflectedDamage, false);
		caster.getUpdateFlags().setDamage(new Damage(caster, reflectedDamage));
		caster.getUpdateFlags().addHitSplat(new HitSplat(caster, HitSplat.TYPE_ARMOR_PROC, reflectedDamage));

		if (caster.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			if (type == 2 || type == 5) {
				opponent.resetRange();
			}
			caster.killedBy(opponent);
		} else {
			if (caster.isPlayer()) {
				((Player) caster).checkRingOfLife(opponent);
			}
		}
	}

	private void projectileDamage() {
		damage = Summoning.applySummonOutgoingDamage(caster, damage);
		if (caster.isPlayer()
				&& opponent.isRemoved()
				&& type == 2
		) {
				caster.resetRange();
		}
		if (caster.isPlayer()) {
			damage = applyPlayerProjectileDamageBuff((Player) caster, damage);
		}
		if (caster.consumeOgreStaggerDebuff() || caster.consumeStartleDebuff()) {
			damage = 0;
		}

		if (opponent.isPlayer()) {
			Player opponentPlayer = (Player) opponent;
			damage = opponentPlayer.applyRobeDamageMitigation(damage);
			if (type == 1 || type == 4) {
				damage = opponentPlayer.applyPotionMagicDamageReduction(damage);
			} else if (type == 2 || type == 5) {
				damage = opponentPlayer.applyPotionRangedDamageReduction(damage);
			}
			if (caster.isNpc()) {
				damage = Summoning.applySummonDamageAbsorption(opponentPlayer, caster, damage);
			}
		}
		damage = applyFrostbiteReflection(caster, opponent, damage);
		int lastHits = opponent.getLevel(Skill.HITS.id());
		opponent.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
		opponent.getUpdateFlags().setDamage(new Damage(opponent, damage));
		opponent.getUpdateFlags().addHitSplat(new HitSplat(opponent, HitSplat.TYPE_STANDARD, damage));
		if (impactEffectType > 0) {
			opponent.getUpdateFlags().setCombatEffect(new CombatEffect(opponent, impactEffectType));
		}


		if (caster.isPlayer()) {
			Player casterPlayer = (Player) caster;
			if (opponent.isNpc()) {
				Npc npc = (Npc) opponent;
				if (type == 1 || type == 4) {
					damage = Math.min(damage, lastHits);
					npc.addMageDamage(casterPlayer, damage);
				}
				else if (type == 2 || type == 5) {
					damage = Math.min(damage, lastHits);
					npc.addRangeDamage(casterPlayer, damage);
				}
			}
		} else if (Summoning.isSummon(caster) && opponent.isNpc()) {
			Summoning.creditSummonProjectileDamage(caster, opponent, Math.min(damage, lastHits), type);
		}

		// Update party menu with new HITS stat.
		if (opponent.isPlayer()) {
			Player affectedPlayer = (Player) opponent;
			ActionSender.sendStat(affectedPlayer, Skill.HITS.id());
			applyChaosRobeReflect(affectedPlayer, caster, damage);
			if (affectedPlayer.getConfig().WANT_PARTIES) {
				if (affectedPlayer.getParty() != null) {
					affectedPlayer.getParty().sendParty();
				}
			}
		}

		if (damage > 0 && caster.isPlayer() && (type == 1 || type == 4)) {
			applyBloodRobeLifesteal((Player) caster, damage);
			((Player) caster).applyBloodAmuletLifesteal(damage);
		}

		applySplinterOnHitEffect();
		if (opponent.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			handleDeath();
		} else {
			if (Summoning.applySummonOnHitEffects(caster, opponent, damage)) {
				handleDeath();
				return;
			}
			applyWeaponPoison();
			applyLeatherSetOnHitEffects();
			if (damage > 0 && windAccuracyDebuffPercent > 0) {
				opponent.applyWindDebuff(windAccuracyDebuffPercent);
			}
			if (damage > 0 && waterMaxHitDebuffPercent > 0) {
				opponent.applyWaterMaxHitDebuff(waterMaxHitDebuffPercent);
			}
			if (damage > 0 && earthAttackSpeedDebuffPercent > 0) {
				opponent.applyEarthAttackSpeedDebuff(earthAttackSpeedDebuffPercent);
			}
			if (damage > 0 && fireDefenseDebuffPercent > 0) {
				opponent.applyFireDefenseDebuff(fireDefenseDebuffPercent);
			}
			applyDualElementOnHitEffects();
			if (opponent.isNpc() && caster.isPlayer()) {
				Npc npc = (Npc) opponent;
				Player player = (Player) caster;
				if (!npc.isChasing() && !npc.inCombat() && npc.getCombatState() != CombatState.RUNNING && this.shouldChase) {
					Player preferredThreatTarget = npc.getPreferredThreatTarget();
					npc.setChasing(preferredThreatTarget != null ? preferredThreatTarget : player);
				}
			}
		}
	}

	private void applyDualElementOnHitEffects() {
		if (damage <= 0 || opponent.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		if (startleProcChancePercent > 0 && rollsPercent(startleProcChancePercent)) {
			opponent.applyStartleDebuff(caster);
		}
		if (acidPoisonPower > 0 && rollsPercent(getDualElementProcChancePercent())) {
			opponent.applyPoison(acidPoisonPower, acidPoisonPower);
			if (caster.isPlayer() && opponent.isNpc()) {
				((Player) caster).message("@gr2@Corrode poisons the " + ((Npc) opponent).getDef().name + ".");
			}
		}
		if (frostbiteProcChancePercent > 0 && rollsPercent(frostbiteProcChancePercent)) {
			opponent.applyFrostbiteDebuff(caster);
		}
	}

	private int getDualElementProcChancePercent() {
		if (startleProcChancePercent > 0) {
			return startleProcChancePercent;
		}
		if (frostbiteProcChancePercent > 0) {
			return frostbiteProcChancePercent;
		}
		if (splinterProcChancePercent > 0) {
			return splinterProcChancePercent;
		}
		if (acidPoisonPower >= 40) {
			return 25;
		}
		if (acidPoisonPower >= 30) {
			return 15;
		}
		return acidPoisonPower > 0 ? 7 : 0;
	}

	private boolean rollsPercent(int chancePercent) {
		return chancePercent > 0 && DataConversions.getRandom().nextDouble() < chancePercent / 100.0D;
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

	private void inflictFrostbiteReflectedDamage(final Mob source, final Mob target, final int reflectedDamage) {
		if (reflectedDamage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final Mob creditedSource = source != null ? source : target;
		final int lastHits = target.getLevel(Skill.HITS.id());
		target.getSkills().subtractLevel(Skill.HITS.id(), reflectedDamage, false);
		target.getUpdateFlags().setDamage(new Damage(target, reflectedDamage));
		target.getUpdateFlags().addHitSplat(new HitSplat(target, HitSplat.TYPE_ARMOR_PROC, reflectedDamage));
		if (target.isNpc() && creditedSource.isPlayer()) {
			((Npc) target).addMageDamage((Player) creditedSource, Math.min(reflectedDamage, lastHits));
		}
		if (target.isPlayer()) {
			ActionSender.sendStat((Player) target, Skill.HITS.id());
		}
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			target.killedBy(creditedSource);
		}
	}

	private void applySplinter() {
		if (!caster.isPlayer() || !opponent.isNpc()) {
			return;
		}
		final Player casterPlayer = (Player) caster;
		final Npc splinterTarget = selectSplinterTarget(casterPlayer, opponent);
		if (splinterTarget == null) {
			return;
		}
		final int splinterDamage = Math.max(1, (int) Math.ceil(damage / 2.0D));
		final int lastHits = splinterTarget.getLevel(Skill.HITS.id());
		splinterTarget.getSkills().subtractLevel(Skill.HITS.id(), splinterDamage, false);
		splinterTarget.getUpdateFlags().setDamage(new Damage(splinterTarget, splinterDamage));
		splinterTarget.getUpdateFlags().addHitSplat(new HitSplat(splinterTarget, HitSplat.TYPE_ARMOR_PROC, splinterDamage));
		splinterTarget.addMageDamage(casterPlayer, Math.min(splinterDamage, lastHits));
		if (!splinterTarget.isChasing() && !splinterTarget.inCombat()
			&& splinterTarget.getCombatState() != CombatState.RUNNING && this.shouldChase) {
			splinterTarget.setChasing(casterPlayer);
		}
		if (splinterTarget.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			splinterTarget.killedBy(casterPlayer);
		}
	}

	private void applySplinterOnHitEffect() {
		if (damage > 0 && splinterProcChancePercent > 0 && rollsPercent(splinterProcChancePercent)) {
			applySplinter();
		}
	}

	private Npc selectSplinterTarget(final Player casterPlayer, final Mob primaryTarget) {
		final java.util.ArrayList<Npc> candidates = new java.util.ArrayList<Npc>();
		for (Npc npc : casterPlayer.getViewArea().getNpcsInView()) {
			if (npc != null && npc != primaryTarget && !npc.isRemoved()
				&& npc.getDef().isAttackable() && npc.getSkills().getLevel(Skill.HITS.id()) > 0
				&& npc.getLocation().withinRange(primaryTarget.getLocation(), 2)) {
				candidates.add(npc);
			}
		}
		if (candidates.isEmpty()) {
			return null;
		}
		return candidates.get(DataConversions.random(0, candidates.size() - 1));
	}

	private void applyWeaponPoison() {
		if (!caster.isPlayer() || damage <= 0) {
			return;
		}
		final Player casterPlayer = (Player) caster;
		casterPlayer.removeAttribute("dragon_breath_armor_proc");
		final int weaponMaxPower = PoisonPower.getWeaponMaxPoisonPower(poisonWeaponId);
		final boolean isMagicAttack = type == 1 || type == 4;
		final boolean isRangedAttack = type == 2 || type == 5;
		final int styleArmorMaxPower = isMagicAttack
			? casterPlayer.getMagicPoisonArmorMaxPower()
			: (isRangedAttack ? casterPlayer.getRangedPoisonArmorMaxPower() : 0);
		final int breathArmorMaxPower = casterPlayer.hasFullBlackDragonSet() ? 30 : (casterPlayer.hasFullKingBlackDragonSet() ? 40 : 0);
		final int armorMaxPower = styleArmorMaxPower + breathArmorMaxPower;
		final int totalMaxPower = weaponMaxPower + armorMaxPower;
		if (totalMaxPower <= 0) {
			return;
		}

		int appliedPoisonPower = 0;
		if (weaponMaxPower > 0 && PoisonProcChance.rollWeapon(casterPlayer, opponent, poisonWeaponId)) {
			appliedPoisonPower = Math.max(appliedPoisonPower, PoisonPower.getWeaponAppliedPoisonPower(poisonWeaponId));
		}
		if (isMagicAttack && styleArmorMaxPower > 0 && PoisonProcChance.rollArmor(casterPlayer, opponent, "magic")) {
			appliedPoisonPower = Math.max(appliedPoisonPower, casterPlayer.getMagicPoisonArmorAppliedPower());
		}
		if (isRangedAttack && styleArmorMaxPower > 0 && PoisonProcChance.rollArmor(casterPlayer, opponent, "ranged")) {
			appliedPoisonPower = Math.max(appliedPoisonPower, casterPlayer.getRangedPoisonArmorAppliedPower());
		}
		if (casterPlayer.hasFullBlackDragonSet() && DataConversions.getRandom().nextDouble() < 0.20D) {
			appliedPoisonPower = Math.max(appliedPoisonPower, 15);
			casterPlayer.setAttribute("dragon_breath_armor_proc", "black");
		} else if (casterPlayer.hasFullKingBlackDragonSet() && DataConversions.getRandom().nextDouble() < 0.60D) {
			appliedPoisonPower = Math.max(appliedPoisonPower, 20);
			casterPlayer.setAttribute("dragon_breath_armor_proc", "kbd");
		}
		if (appliedPoisonPower <= 0) {
			return;
		}

		opponent.applyPoison(appliedPoisonPower, totalMaxPower);
		if (opponent.isNpc()) {
			casterPlayer.message("@gr3@You @gr2@have @gr1@poisioned @gr2@the " + ((Npc) opponent).getDef().name + "!");
		}
	}

	private void applyLeatherSetOnHitEffects() {
		if (!caster.isPlayer() || opponent.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final Player casterPlayer = (Player) caster;
		final int intimidatePercent = casterPlayer.getBearHideIntimidatePercent();
		if (intimidatePercent > 0 && DataConversions.getRandom().nextDouble() < casterPlayer.getBearHideIntimidateProcChance()) {
			opponent.applyBearIntimidateDebuff(intimidatePercent);
		}
		if (casterPlayer.getGoblinEnragedProcChance() > 0.0D && DataConversions.getRandom().nextDouble() < casterPlayer.getGoblinEnragedProcChance()) {
			casterPlayer.activateGoblinEnraged();
		}
		if (casterPlayer.getGiantBruteForceProcChance() > 0.0D && DataConversions.getRandom().nextDouble() < casterPlayer.getGiantBruteForceProcChance()) {
			casterPlayer.activateGiantBruteForce();
		}
		if (casterPlayer.getMossGiantBruteForceProcChance() > 0.0D && DataConversions.getRandom().nextDouble() < casterPlayer.getMossGiantBruteForceProcChance()) {
			casterPlayer.activateMossGiantBruteForce();
		}
		if (casterPlayer.getIceGiantBruteForceProcChance() > 0.0D && DataConversions.getRandom().nextDouble() < casterPlayer.getIceGiantBruteForceProcChance()) {
			casterPlayer.activateIceGiantBruteForce();
		}
		if (casterPlayer.getFireGiantBruteForceProcChance() > 0.0D && DataConversions.getRandom().nextDouble() < casterPlayer.getFireGiantBruteForceProcChance()) {
			casterPlayer.activateFireGiantBruteForce();
		}
		if (casterPlayer.hasFullOgreSet() && casterPlayer.isOgreStaggeringBlowReady()
			&& DataConversions.getRandom().nextDouble() < casterPlayer.getOgreStaggeringBlowProcChance()) {
			opponent.applyOgreStaggerDebuff();
			casterPlayer.activateOgreStaggeringBlowCooldown();
		}
		final int smokePercent = casterPlayer.getBabyDragonSmokeAccuracyDebuffPercent();
		if (smokePercent > 0 && DataConversions.getRandom().nextDouble() < casterPlayer.getBabyDragonSmokeProcChance()) {
			caster.getUpdateFlags().setProjectile(new Projectile(caster, opponent, Projectile.BLOW_SMOKE));
			opponent.applySmokeAccuracyDebuff(smokePercent);
		}
		final int infernalMaxHit = casterPlayer.getInfernalFireProcMaxHit();
		final int infernalPieces = casterPlayer.getInfernalArmorPieceCount();
		if (infernalMaxHit > 0) {
			final double infernalChance = casterPlayer.getInfernalFireProcChance();
			final double infernalRoll = DataConversions.getRandom().nextDouble();
			final boolean infernalProc = infernalRoll < infernalChance;
			int procDamage = 0;
			int procDamageDealt = 0;
			if (infernalProc) {
				opponent.getUpdateFlags().setCombatEffect(new CombatEffect(opponent, CombatEffect.infernalEffectForMaxHit(infernalMaxHit)));
				procDamage = DataConversions.random(0, infernalMaxHit);
				procDamageDealt = inflictAuxiliaryMagicDamage(caster, opponent, procDamage);
				opponent.applyInfernalFireDefenseDebuff(casterPlayer.getInfernalFireDefenseDebuffPercent());
			}
			CombatEffectUtil.sendInfernalProcDebug(casterPlayer, "projectile", opponent, damage, infernalPieces,
				infernalMaxHit, infernalRoll, infernalChance, infernalProc, procDamage, procDamageDealt);
		} else if (infernalPieces > 0) {
			CombatEffectUtil.sendInfernalProcDebug(casterPlayer, "projectile", opponent, damage, infernalPieces,
				infernalMaxHit, -1.0D, 0.0D, false, 0, 0);
		}
		if (casterPlayer.hasFullBlueDragonSet() && DataConversions.getRandom().nextDouble() < 0.20D) {
			final int procDamage = DataConversions.random(0, 10);
			if (procDamage > 0) {
				inflictAuxiliaryTrueDamage(caster, opponent, procDamage);
			}
			opponent.applyDragonWaterMaxHitDebuff(10);
		}
		if (casterPlayer.hasFullEarthDragonSet() && DataConversions.getRandom().nextDouble() < 0.20D) {
			final int procDamage = DataConversions.random(0, 10);
			if (procDamage > 0) {
				inflictAuxiliaryTrueDamage(caster, opponent, procDamage);
			}
			opponent.applyDragonEarthAttackSpeedDebuff(6);
		}
		if (casterPlayer.hasFullRedDragonSet() && DataConversions.getRandom().nextDouble() < 0.20D) {
			final int procDamage = DataConversions.random(0, 10);
			if (procDamage > 0) {
				inflictAuxiliaryTrueDamage(caster, opponent, procDamage);
			}
			opponent.applyDragonFireDefenseDebuff(6);
		}
		if ("black".equals(casterPlayer.getAttribute("dragon_breath_armor_proc", ""))
			|| "kbd".equals(casterPlayer.getAttribute("dragon_breath_armor_proc", ""))) {
			casterPlayer.getUpdateFlags().setCombatEffect(new CombatEffect(casterPlayer, CombatEffect.DRAGON_BREATH));
		}
		if (casterPlayer.hasFullBlackDragonSet() && "black".equals(casterPlayer.getAttribute("dragon_breath_armor_proc", ""))) {
			final int procDamage = DataConversions.random(0, 10);
			if (procDamage > 0) {
				inflictAuxiliaryTrueDamage(caster, opponent, procDamage);
			}
		}
		if (casterPlayer.hasFullKingBlackDragonSet() && "kbd".equals(casterPlayer.getAttribute("dragon_breath_armor_proc", ""))) {
			final int procDamage = DataConversions.random(0, 10);
			if (procDamage > 0) {
				inflictAuxiliaryTrueDamage(caster, opponent, procDamage);
			}
			switch (DataConversions.random(0, 2)) {
				case 0:
					opponent.applyDragonWaterMaxHitDebuff(10);
					break;
				case 1:
					opponent.applyDragonEarthAttackSpeedDebuff(6);
					break;
				default:
					opponent.applyDragonFireDefenseDebuff(6);
					break;
			}
		}
	}

	private int applyPlayerProjectileDamageBuff(final Player player, final int damage) {
		if (damage <= 0) {
			return damage;
		}
		if (type == 1 || type == 4) {
			if (earthAttackSpeedDebuffPercent > 0) {
				return Math.max(0, (int) Math.floor(damage * player.getEarthMagicDamageMultiplier()));
			}
			if (waterMaxHitDebuffPercent > 0) {
				return Math.max(0, (int) Math.floor(damage * player.getWaterMagicDamageMultiplier()));
			}
			if (fireDefenseDebuffPercent > 0) {
				return Math.max(0, (int) Math.floor(damage * player.getFireMagicDamageMultiplier()));
			}
		}
		return damage;
	}

	private void applyChaosRobeReflect(final Player defender, final Mob attacker, final int damage) {
		if (damage <= 0 || attacker.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final double reflectPercent = defender.getChaosRobeReflectPercent();
		if (reflectPercent <= 0.0D) {
			return;
		}
		final int reflectedDamage = Math.max(1, (int) Math.ceil(damage * reflectPercent));
		attacker.getSkills().subtractLevel(Skill.HITS.id(), reflectedDamage, false);
		attacker.getUpdateFlags().setDamage(new Damage(attacker, reflectedDamage));
		attacker.getUpdateFlags().addHitSplat(new HitSplat(attacker, HitSplat.TYPE_ARMOR_PROC, reflectedDamage));
		if (attacker.isPlayer()) {
			ActionSender.sendStat((Player) attacker, Skill.HITS.id());
		}
	}

	private void applyBloodRobeLifesteal(final Player casterPlayer, final int damageDealt) {
		final int robePieces = casterPlayer.getBloodRobePieces();
		if (robePieces <= 0 || damageDealt <= 0) {
			return;
		}
		final int maxHits = casterPlayer.getSkills().getMaxStat(Skill.HITS.id());
		final int currentHits = casterPlayer.getSkills().getLevel(Skill.HITS.id());
		if (currentHits >= maxHits) {
			return;
		}
		final int healing = Math.max(1, (int) Math.floor(damageDealt * (robePieces * 0.05D)));
		casterPlayer.getSkills().setLevel(Skill.HITS.id(), Math.min(maxHits, currentHits + healing));
		ActionSender.sendStat(casterPlayer, Skill.HITS.id());
	}

	private int inflictAuxiliaryMagicDamage(final Mob hitter, final Mob target, int bonusDamage) {
		if (bonusDamage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return 0;
		}
		if (target.isPlayer()) {
			Player targetPlayer = (Player) target;
			bonusDamage = targetPlayer.applyRobeDamageMitigation(bonusDamage);
			bonusDamage = targetPlayer.applyPotionMagicDamageReduction(bonusDamage);
		}
		if (bonusDamage <= 0) {
			return 0;
		}

		final int lastHits = target.getLevel(Skill.HITS.id());
		target.getSkills().subtractLevel(Skill.HITS.id(), bonusDamage, false);
		final int damageDealt = Math.min(bonusDamage, lastHits);
		target.getUpdateFlags().setDamage(new Damage(target, bonusDamage));
		target.getUpdateFlags().addHitSplat(new HitSplat(target, HitSplat.TYPE_ARMOR_PROC, bonusDamage));
		if (target.isNpc() && hitter.isPlayer()) {
			((Npc) target).addMageDamage((Player) hitter, damageDealt);
		}
		if (target.isPlayer()) {
			ActionSender.sendStat((Player) target, Skill.HITS.id());
			applyChaosRobeReflect((Player) target, hitter, bonusDamage);
		}
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			handleDeath();
		}
		return damageDealt;
	}

	private void inflictAuxiliaryTrueDamage(final Mob hitter, final Mob target, int bonusDamage) {
		if (bonusDamage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		if (target.isPlayer()) {
			bonusDamage = ((Player) target).applyRobeDamageMitigation(bonusDamage);
		}
		if (bonusDamage <= 0) {
			return;
		}

		final int lastHits = target.getLevel(Skill.HITS.id());
		target.getSkills().subtractLevel(Skill.HITS.id(), bonusDamage, false);
		target.getUpdateFlags().setDamage(new Damage(target, bonusDamage));
		target.getUpdateFlags().addHitSplat(new HitSplat(target, HitSplat.TYPE_ARMOR_PROC, bonusDamage));
		if (target.isNpc() && hitter.isPlayer()) {
			((Npc) target).addCombatDamage((Player) hitter, Math.min(bonusDamage, lastHits));
		}
		if (target.isPlayer()) {
			ActionSender.sendStat((Player) target, Skill.HITS.id());
			applyChaosRobeReflect((Player) target, hitter, bonusDamage);
		}
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			handleDeath();
		}
	}

	private void handleDeath() {
		if (caster.isPlayer()) {
			Player player = (Player) caster;
			player.applyDeathAmuletBurst(opponent);
			if (type == 2 || type == 5) {
				player.resetRange();
			}
		}
		if (opponent.isNpc() && caster.isPlayer()) {
			final Player playerCaster = (Player) caster;
			final Npc npcOpponent = (Npc) opponent;
			npcOpponent.killedBy(playerCaster);
		} else if (opponent.isPlayer() && caster.isPlayer()) {
			final Player playerCaster = (Player) caster;
			final Player playerOpponent = (Player) opponent;
			playerOpponent.killedBy(playerCaster);
		} else {
			opponent.killedBy(caster);
		}
	}

	public void setCanceled(boolean b) {
		canceled = b;
	}

}
