package com.openrsc.server.event.rsc.impl.projectile;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.CorrosiveAura;
import com.openrsc.server.content.DivineGrace;
import com.openrsc.server.content.DivineRetribution;
import com.openrsc.server.content.ElderGreenDragonArmorEffect;
import com.openrsc.server.content.PoisonProcChance;
import com.openrsc.server.content.PoisonPower;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.content.TrueDefense;
import com.openrsc.server.content.cleric.runtime.ClericDirectCombatRuntime;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.SingleTickEvent;
import com.openrsc.server.event.rsc.impl.combat.ElderGreenDragonSpecialAttacks;
import com.openrsc.server.model.combat.BabyDragonSmokeProc;
import com.openrsc.server.model.combat.BlackDragonBreathFollowup;
import com.openrsc.server.model.combat.BlueDragonWaterProc;
import com.openrsc.server.model.combat.CombatEngagement;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.ChainLightningTraversalPolicy;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.combat.EarthDragonSlowProc;
import com.openrsc.server.model.combat.ElderGreenDragonArmorProc;
import com.openrsc.server.model.combat.InfernalFireProc;
import com.openrsc.server.model.combat.HellsInfernoNpcSplash;
import com.openrsc.server.model.combat.DeathRobeOverkillSplash;
import com.openrsc.server.model.combat.PlayerProjectileDamageBuff;
import com.openrsc.server.model.combat.KingBlackDragonBreathFollowup;
import com.openrsc.server.model.combat.OgreStaggeringBlowProc;
import com.openrsc.server.model.combat.ProjectileImpactDecision;
import com.openrsc.server.model.combat.ProjectileImpactLedger;
import com.openrsc.server.model.combat.ProjectileImpactValidator;
import com.openrsc.server.model.combat.ProjectileLaunchSnapshot;
import com.openrsc.server.model.combat.ProjectileLaunchSpecification;
import com.openrsc.server.model.combat.ProjectileResourceLedger;
import com.openrsc.server.model.combat.PlayerOwnedNpcRadiusSelection;
import com.openrsc.server.model.combat.RedDragonFireProc;
import com.openrsc.server.model.combat.SecondaryEffectPolicy;
import com.openrsc.server.model.combat.SplinterTargetSelectionPolicy;
import com.openrsc.server.model.entity.KillType;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcMagicElement;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.Damage;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.states.CombatState;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.runtime.ProductionGameRandom;
import com.openrsc.server.util.rsc.CombatEffectUtil;
import com.openrsc.server.util.rsc.DataConversions;

public class ProjectileEvent extends SingleTickEvent {

	private static final SecondaryEffectPolicy AUXILIARY_MAGIC_DAMAGE_POLICY =
		SecondaryEffectPolicy.PROJECTILE_AUXILIARY_MAGIC;
	private static final SecondaryEffectPolicy AUXILIARY_TRUE_DAMAGE_POLICY =
		SecondaryEffectPolicy.PROJECTILE_AUXILIARY_TRUE;
	private static final SecondaryEffectPolicy FROSTBITE_REFLECTION_POLICY =
		SecondaryEffectPolicy.PROJECTILE_FROSTBITE_REFLECTION;
	private static final SecondaryEffectPolicy CLERIC_THORNS_POLICY =
		SecondaryEffectPolicy.PROJECTILE_CLERIC_THORNS;
	private static final SecondaryEffectPolicy JEWELRY_RECOIL_POLICY =
		SecondaryEffectPolicy.PROJECTILE_JEWELRY_RECOIL;
	private static final SecondaryEffectPolicy CHAIN_LIGHTNING_POLICY =
		SecondaryEffectPolicy.PROJECTILE_CHAIN_LIGHTNING;
	private static final SecondaryEffectPolicy SPLINTER_POLICY =
		SecondaryEffectPolicy.PROJECTILE_SPLINTER;
	private static final SecondaryEffectPolicy BLOOD_ROBE_SPLASH_POLICY =
		SecondaryEffectPolicy.PROJECTILE_BLOOD_ROBE_SPLASH;
	private static final SecondaryEffectPolicy DEATH_ROBE_OVERKILL_POLICY =
		SecondaryEffectPolicy.PROJECTILE_DEATH_ROBE_OVERKILL;
	private static final SecondaryEffectPolicy BALROG_MAGIC_SPLASH_POLICY =
		SecondaryEffectPolicy.PROJECTILE_BALROG_MAGIC_SPLASH;
	private static final String UNCLASSIFIED_PROJECTILE_EFFECT_KEY =
		"projectile-unclassified-compatibility";
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
	protected NpcMagicElement magicElement = NpcMagicElement.NONE;
	protected int dragonBreathDamage;
	protected boolean bloodSpell;
	protected boolean showProjectile;
	boolean canceled;
	boolean shouldChase;
	private boolean deferClericRally;
	private boolean clericDirectImpactResolved;
	private boolean clericDeferredRallyResolved;
	private int clericDirectDamageDealt;
	private int secondaryEffectDamage;
	private final ProjectileLaunchSnapshot launchSnapshot;
	private final ProjectileImpactLedger impactLedger;
	private final ProjectileResourceLedger resourceLedger;

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type) {
		this(world, caster, opponent, legacySpecification(
			caster, damage, type, true, -1, 0, 0, 0, 0,
			DuplicationStrategy.ONE_PER_MOB, type, 0, true,
			NpcMagicElement.NONE, 0, 0, 0, 0, false, 0));
	}

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type, boolean setChasing) {
		this(world, caster, opponent, legacySpecification(
			caster, damage, type, setChasing, -1, 0, 0, 0, 0,
			DuplicationStrategy.ONE_PER_MOB, type, 0, true,
			NpcMagicElement.NONE, 0, 0, 0, 0, false, 0));
	}

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type, boolean setChasing, int poisonWeaponId) {
		this(world, caster, opponent, legacySpecification(
			caster, damage, type, setChasing, poisonWeaponId, 0, 0, 0, 0,
			DuplicationStrategy.ONE_PER_MOB, type, 0, true,
			NpcMagicElement.NONE, 0, 0, 0, 0, false, 0));
	}

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type, boolean setChasing, int windAccuracyDebuffPercent, int waterMaxHitDebuffPercent, int earthAttackSpeedDebuffPercent, int fireDefenseDebuffPercent) {
		this(world, caster, opponent, legacySpecification(
			caster, damage, type, setChasing, -1, windAccuracyDebuffPercent,
			waterMaxHitDebuffPercent, earthAttackSpeedDebuffPercent,
			fireDefenseDebuffPercent, DuplicationStrategy.ONE_PER_MOB,
			type, 0, true, NpcMagicElement.NONE, 0, 0, 0, 0, false, 0));
	}

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type, boolean setChasing,
						   int windAccuracyDebuffPercent, int waterMaxHitDebuffPercent, int earthAttackSpeedDebuffPercent,
						   int fireDefenseDebuffPercent, int projectileType, int impactEffectType, boolean showProjectile) {
		this(world, caster, opponent, legacySpecification(
			caster, damage, type, setChasing, -1, windAccuracyDebuffPercent,
			waterMaxHitDebuffPercent, earthAttackSpeedDebuffPercent,
			fireDefenseDebuffPercent, DuplicationStrategy.ONE_PER_MOB,
			projectileType, impactEffectType, showProjectile,
			NpcMagicElement.NONE, 0, 0, 0, 0, false, 0));
	}

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type, boolean setChasing,
						   int windAccuracyDebuffPercent, int waterMaxHitDebuffPercent, int earthAttackSpeedDebuffPercent,
						   int fireDefenseDebuffPercent, int projectileType, int impactEffectType, boolean showProjectile,
						   NpcMagicElement magicElement) {
		this(world, caster, opponent, legacySpecification(
			caster, damage, type, setChasing, -1, windAccuracyDebuffPercent,
			waterMaxHitDebuffPercent, earthAttackSpeedDebuffPercent,
			fireDefenseDebuffPercent, DuplicationStrategy.ONE_PER_MOB,
			projectileType, impactEffectType, showProjectile,
			magicElement, 0, 0, 0, 0, false, 0));
	}

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type, boolean setChasing,
						   int windAccuracyDebuffPercent, int waterMaxHitDebuffPercent, int earthAttackSpeedDebuffPercent,
						   int fireDefenseDebuffPercent, int projectileType, int impactEffectType, boolean showProjectile,
						   NpcMagicElement magicElement, int startleProcChancePercent, int acidPoisonPower,
						   int frostbiteProcChancePercent, int splinterProcChancePercent) {
		this(world, caster, opponent, legacySpecification(
			caster, damage, type, setChasing, -1, windAccuracyDebuffPercent,
			waterMaxHitDebuffPercent, earthAttackSpeedDebuffPercent,
			fireDefenseDebuffPercent, DuplicationStrategy.ONE_PER_MOB,
			projectileType, impactEffectType, showProjectile, magicElement,
			startleProcChancePercent, acidPoisonPower,
			frostbiteProcChancePercent, splinterProcChancePercent, false, 0));
	}

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type, boolean setChasing,
						   int windAccuracyDebuffPercent, int waterMaxHitDebuffPercent, int earthAttackSpeedDebuffPercent,
						   int fireDefenseDebuffPercent, int projectileType, int impactEffectType, boolean showProjectile,
						   int startleProcChancePercent, int acidPoisonPower, int frostbiteProcChancePercent,
						   int splinterProcChancePercent) {
		this(world, caster, opponent, legacySpecification(
			caster, damage, type, setChasing, -1, windAccuracyDebuffPercent,
			waterMaxHitDebuffPercent, earthAttackSpeedDebuffPercent,
			fireDefenseDebuffPercent, DuplicationStrategy.ONE_PER_MOB,
			projectileType, impactEffectType, showProjectile,
			NpcMagicElement.NONE, startleProcChancePercent, acidPoisonPower,
			frostbiteProcChancePercent, splinterProcChancePercent, false, 0));
	}

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type, boolean setChasing,
						   int windAccuracyDebuffPercent, int waterMaxHitDebuffPercent, int earthAttackSpeedDebuffPercent,
						   int fireDefenseDebuffPercent, int projectileType, int impactEffectType, boolean showProjectile,
						   int startleProcChancePercent, int acidPoisonPower, int frostbiteProcChancePercent,
						   int splinterProcChancePercent, boolean bloodSpell) {
		this(world, caster, opponent, legacySpecification(
			caster, damage, type, setChasing, -1, windAccuracyDebuffPercent,
			waterMaxHitDebuffPercent, earthAttackSpeedDebuffPercent,
			fireDefenseDebuffPercent, DuplicationStrategy.ONE_PER_MOB,
			projectileType, impactEffectType, showProjectile,
			NpcMagicElement.NONE, startleProcChancePercent, acidPoisonPower,
			frostbiteProcChancePercent, splinterProcChancePercent,
			bloodSpell, 0));
	}

	public ProjectileEvent(final World world, final Mob caster, final Mob opponent, final int damage, final int type,
						   final boolean setChasing, final DuplicationStrategy duplicationStrategy)
	{
		this(world, caster, opponent, legacySpecification(
			caster, damage, type, setChasing, -1, 0, 0, 0, 0,
			duplicationStrategy, type, 0, true, NpcMagicElement.NONE,
			0, 0, 0, 0, false, 0));
	}

	public ProjectileEvent(final World world, final Mob caster, final Mob opponent, final int damage, final int type,
						   final boolean setChasing, final int poisonWeaponId, final int windAccuracyDebuffPercent, final int waterMaxHitDebuffPercent,
						   final int earthAttackSpeedDebuffPercent, final int fireDefenseDebuffPercent, final DuplicationStrategy duplicationStrategy,
						   final int projectileType, final int impactEffectType, final boolean showProjectile)
	{
		this(world, caster, opponent, legacySpecification(
			caster, damage, type, setChasing, poisonWeaponId,
			windAccuracyDebuffPercent, waterMaxHitDebuffPercent,
			earthAttackSpeedDebuffPercent, fireDefenseDebuffPercent,
			duplicationStrategy, projectileType, impactEffectType, showProjectile,
			NpcMagicElement.NONE, 0, 0, 0, 0, false, 0));
	}

	/** Named A06 producer path. Positional constructors remain compatibility facades. */
	public ProjectileEvent(final World world, final Mob caster,
			final Mob opponent,
			final ProjectileLaunchSpecification launchSpecification) {
		this(world, caster, opponent, launchSpecification,
			ProjectileResourceLedger.defaultFor(
				requireLaunchSpecification(launchSpecification).getProducer()));
	}

	/** Named A06 producer path with an observational launch-resource receipt. */
	public ProjectileEvent(final World world, final Mob caster,
			final Mob opponent,
			final ProjectileLaunchSpecification launchSpecification,
			final ProjectileResourceLedger resourceLedger) {
		super(world, caster, 1, "Projectile Event",
			requireLaunchSpecification(launchSpecification)
				.getDuplicationStrategy());
		if (resourceLedger == null) {
			throw new IllegalArgumentException("resourceLedger cannot be null");
		}
		this.caster = caster;
		this.opponent = opponent;
		this.damage = launchSpecification.getProposedDamage();
		this.poisonWeaponId = launchSpecification.getPoisonWeaponId();
		this.windAccuracyDebuffPercent =
			launchSpecification.getWindAccuracyDebuffPercent();
		this.waterMaxHitDebuffPercent =
			launchSpecification.getWaterMaxHitDebuffPercent();
		this.earthAttackSpeedDebuffPercent =
			launchSpecification.getEarthAttackSpeedDebuffPercent();
		this.fireDefenseDebuffPercent =
			launchSpecification.getFireDefenseDebuffPercent();
		this.type = launchSpecification.getAttackType();
		this.projectileType = launchSpecification.getProjectileType();
		this.impactEffectType = launchSpecification.getImpactEffectType();
		this.showProjectile = launchSpecification.shouldShowProjectile();
		this.shouldChase = launchSpecification.shouldChase();
		this.magicElement = launchSpecification.getMagicElement();
		this.startleProcChancePercent =
			launchSpecification.getStartleProcChancePercent();
		this.acidPoisonPower = launchSpecification.getAcidPoisonPower();
		this.frostbiteProcChancePercent =
			launchSpecification.getFrostbiteProcChancePercent();
		this.splinterProcChancePercent =
			launchSpecification.getSplinterProcChancePercent();
		this.bloodSpell = launchSpecification.isBloodSpell();
		this.dragonBreathDamage = launchSpecification.getDragonBreathDamage();
		final long launchTick = world.getServer().getCurrentTick();
		this.launchSnapshot = ProjectileLaunchSnapshot.capture(
			getUUID(), launchTick, launchTick + getDelayTicks(), caster,
			opponent, launchSpecification);
		this.impactLedger = new ProjectileImpactLedger(launchSnapshot);
		this.resourceLedger = resourceLedger;
		this.resourceLedger.bindEvent(getUUID(),
			launchSpecification.getProducer());

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
						   final boolean setChasing, final int poisonWeaponId, final int windAccuracyDebuffPercent, final int waterMaxHitDebuffPercent,
						   final int earthAttackSpeedDebuffPercent, final int fireDefenseDebuffPercent, final DuplicationStrategy duplicationStrategy,
						   final int projectileType, final int impactEffectType, final boolean showProjectile, final int dragonBreathDamage)
	{
		this(world, caster, opponent, legacySpecification(
			caster, damage, type, setChasing, poisonWeaponId,
			windAccuracyDebuffPercent, waterMaxHitDebuffPercent,
			earthAttackSpeedDebuffPercent, fireDefenseDebuffPercent,
			duplicationStrategy, projectileType, impactEffectType, showProjectile,
			NpcMagicElement.NONE, 0, 0, 0, 0, false, dragonBreathDamage));
	}

	public ProjectileEvent(final World world, final Mob caster, final Mob opponent, final int damage, final int type,
						   final boolean setChasing, final int windAccuracyDebuffPercent, final int waterMaxHitDebuffPercent,
						   final int earthAttackSpeedDebuffPercent, final int fireDefenseDebuffPercent, final DuplicationStrategy duplicationStrategy)
	{
		this(world, caster, opponent, legacySpecification(
			caster, damage, type, setChasing, -1,
			windAccuracyDebuffPercent, waterMaxHitDebuffPercent,
			earthAttackSpeedDebuffPercent, fireDefenseDebuffPercent,
			duplicationStrategy, type, 0, true, NpcMagicElement.NONE,
			0, 0, 0, 0, false, 0));
	}

	private static ProjectileLaunchSpecification requireLaunchSpecification(
			final ProjectileLaunchSpecification launchSpecification) {
		if (launchSpecification == null) {
			throw new IllegalArgumentException(
				"launchSpecification cannot be null");
		}
		return launchSpecification;
	}

	private static ProjectileLaunchSpecification legacySpecification(
			final Mob caster, final int damage, final int type,
			final boolean setChasing, final int poisonWeaponId,
			final int windAccuracyDebuffPercent,
			final int waterMaxHitDebuffPercent,
			final int earthAttackSpeedDebuffPercent,
			final int fireDefenseDebuffPercent,
			final DuplicationStrategy duplicationStrategy,
			final int projectileType, final int impactEffectType,
			final boolean showProjectile,
			final NpcMagicElement magicElement,
			final int startleProcChancePercent,
			final int acidPoisonPower,
			final int frostbiteProcChancePercent,
			final int splinterProcChancePercent,
			final boolean bloodSpell, final int dragonBreathDamage) {
		return ProjectileLaunchSpecification.builder(
			legacyProducer(caster, type, poisonWeaponId), damage, type)
			.chase(setChasing)
			.poisonWeaponId(poisonWeaponId)
			.elementalDebuffs(windAccuracyDebuffPercent,
				waterMaxHitDebuffPercent, earthAttackSpeedDebuffPercent,
				fireDefenseDebuffPercent)
			.presentation(projectileType, impactEffectType, showProjectile)
			.magicElement(magicElement)
			.dualElementProcs(startleProcChancePercent, acidPoisonPower,
				frostbiteProcChancePercent, splinterProcChancePercent)
			.bloodSpell(bloodSpell)
			.dragonBreathDamage(dragonBreathDamage)
			.duplicationStrategy(duplicationStrategy)
			.build();
	}

	private static ProjectileLaunchSpecification.Producer legacyProducer(
			final Mob caster, final int type, final int poisonWeaponId) {
		if (type == 5) {
			return ProjectileLaunchSpecification.Producer.CANNON;
		}
		if (type == 4) {
			return ProjectileLaunchSpecification.Producer.PLAYER_IBAN_MAGIC;
		}
		if (Summoning.isSummon(caster)) {
			return type == 1
				? ProjectileLaunchSpecification.Producer.SUMMON_MAGIC
				: (type == 2
					? ProjectileLaunchSpecification.Producer.SUMMON_RANGED
					: ProjectileLaunchSpecification.Producer
						.SUMMON_COMPATIBILITY);
		}
		if (caster.isNpc()) {
			return type == 1
				? ProjectileLaunchSpecification.Producer.NPC_MAGIC
				: (type == 2
					? ProjectileLaunchSpecification.Producer.NPC_RANGED
					: ProjectileLaunchSpecification.Producer
						.NPC_COMPATIBILITY);
		}
		if (caster.isPlayer() && type == 2) {
			if (RangeUtils.SHURIKENS.contains(poisonWeaponId)) {
				return ProjectileLaunchSpecification.Producer.PLAYER_SHURIKEN;
			}
			if (RangeUtils.THROWING_KNIVES.contains(poisonWeaponId)
					|| RangeUtils.THROWING_DARTS.contains(poisonWeaponId)) {
				return ProjectileLaunchSpecification.Producer.PLAYER_THROWN;
			}
			return ProjectileLaunchSpecification.Producer.PLAYER_BOW;
		}
		if (caster.isPlayer() && type == 1) {
			return ProjectileLaunchSpecification.Producer.PLAYER_MAGIC;
		}
		return ProjectileLaunchSpecification.Producer.COMPATIBILITY;
	}

	private void sendProjectile(Mob caster, Mob opponent) {
		Projectile projectile = new Projectile(caster, opponent, projectileType);
		opponent.getUpdateFlags().setProjectile(projectile);
	}

	@Override
	public void action() {
		final ProjectileImpactDecision impact = beginProjectileImpact();
		if (!impact.isAuthorized()) {
			return;
		}
		try {
			projectileDamage();
			if (caster.getSkills().getLevel(Skill.HITS.id()) > 0) {
				applyChaosAmuletChainLightning();
				if (opponent.isPlayer()) {
					final Player opponentPlayer = (Player) opponent;
					if (opponentPlayer.getCarriedItems().getEquipment().getChaosRecoilChance() > 0.0D) {
						recoilDamage(opponentPlayer, caster, secondaryEffectDamage);
					} else if (opponent.getSkills().getLevel(Skill.HITS.id()) > 0
							&& opponentPlayer.checkRingOfLife(caster)) {
						completeProjectileImpact(impact);
						return;
					}
				}
				caster.consumeAttackBasedDebuffs();
				if (caster.isPlayer()) {
					((Player) caster).consumeLeatherSetAttackBuffs();
				}
			}
			completeProjectileImpact(impact);
		} catch (final RuntimeException failure) {
			failProjectileImpact();
			throw failure;
		} catch (final Error failure) {
			failProjectileImpact();
			throw failure;
		}
	}

	private void applyChaosAmuletChainLightning() {
		if (!caster.isPlayer() || secondaryEffectDamage <= 0 || !opponent.isNpc()) {
			return;
		}
		final Player casterPlayer = (Player) caster;
		if (Summoning.isPlayerAreaEffectSuppressed(casterPlayer)) {
			return;
		}
		final double chainChance = casterPlayer.getCarriedItems().getEquipment().getChaosNecklaceChainLightningChance();
		if (chainChance <= 0.0D) {
			return;
		}

		Mob anchor = opponent;
		int chainDamage = Math.max(1, (int) Math.ceil(secondaryEffectDamage / 2.0D));
		for (int hop = 0; hop < ChainLightningTraversalPolicy.MAX_HOPS; hop++) {
			if (DataConversions.getRandom().nextDouble() >= chainChance) {
				break;
			}
			final Mob chainTarget = selectChaosChainLightningTarget(casterPlayer, anchor);
			if (chainTarget == null) {
				break;
			}
			chainTarget.getUpdateFlags().setProjectile(new Projectile(anchor, chainTarget, getChaosChainLightningProjectile(hop)));
			inflictChainLightningDamage(casterPlayer, chainTarget, chainDamage);
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

	private void inflictChainLightningDamage(final Player casterPlayer, final Mob chainTarget, int chainDamage) {
		if (chainDamage <= 0 || chainTarget.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		if (chainTarget.isPlayer()) {
			Player opponentPlayer = (Player) chainTarget;
			if (type == 1 || type == 4) {
				chainDamage = opponentPlayer.applyPotionMagicDamageReduction(chainDamage);
			} else if (type == 2 || type == 5) {
				chainDamage = opponentPlayer.applyPotionRangedDamageReduction(chainDamage);
			}
		}
		final CombatStyle chainStyle = type == 1 || type == 4
			? CombatStyle.MAGIC
			: (type == 2 || type == 5 ? CombatStyle.RANGED : null);
		final DamageRequest damageRequest = DamageRequest.resolvedLegacy(
			casterPlayer, chainTarget,
			DamageRequest.SourceCategory.OWNED_EFFECT,
			CHAIN_LIGHTNING_POLICY.getStableKey(), chainDamage)
			.eventId(getUUID())
			.style(chainStyle)
			.hitSplatType(HitSplat.TYPE_ARMOR_PROC)
			.build();
		final DamageResult damageResult = chainTarget.getWorld().getServer()
			.getResolvedDamageTransaction().apply(damageRequest);
		if (chainTarget.isNpc()) {
			Npc npc = (Npc) chainTarget;
			final int dealtDamage = damageResult.getLegacyDamageDealt();
			if (type == 1 || type == 4) {
				npc.addMageDamage(casterPlayer, dealtDamage);
			} else if (type == 2 || type == 5) {
				npc.addRangeDamage(casterPlayer, dealtDamage);
			}
		}
		if (chainTarget.isPlayer()) {
			ActionSender.sendStat((Player) chainTarget, Skill.HITS.id());
		}
		if (chainTarget == opponent && chainTarget.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			handleDeath();
		} else if (chainTarget.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			chainTarget.killedBy(caster);
		}
	}

	private Mob selectChaosChainLightningTarget(final Player player, final Mob anchor) {
		return ChainLightningTraversalPolicy.selectNext(
			player, anchor, ProductionGameRandom.INSTANCE);
	}

	private void recoilDamage(Player opponent, Mob caster, int damage) {
		final double recoilChance = opponent.getCarriedItems().getEquipment().getChaosRecoilChance();
		if (recoilChance <= 0.0D) {
			return;
		}
		final double recoilRoll = DataConversions.getRandom().nextDouble();
		final int divisor = opponent.getCarriedItems().getEquipment().getChaosRecoilDamageDivisor();
		int reflectedDamage = damage <= 0 ? 0 : Math.max(1, damage / divisor);
		final boolean proc = recoilRoll < recoilChance;
		if (!proc || reflectedDamage == 0)
			return;

		final DamageRequest damageRequest = DamageRequest.resolvedLegacy(
			opponent, caster, DamageRequest.SourceCategory.OWNED_EFFECT,
			JEWELRY_RECOIL_POLICY.getStableKey(), reflectedDamage)
			.eventId(getUUID())
			.hitSplatType(HitSplat.TYPE_ARMOR_PROC)
			.build();
		caster.getWorld().getServer().getResolvedDamageTransaction()
			.apply(damageRequest);

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
		if (!Summoning.canSummonAttack(caster, opponent)) {
			return;
		}
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
		final boolean attackSuppressed = caster.consumeOgreStaggerDebuff() || caster.consumeStartleDebuff();
		if (attackSuppressed) {
			damage = 0;
		}

		if (opponent.isPlayer()) {
			Player opponentPlayer = (Player) opponent;
			damage = opponentPlayer.applyRobeDamageMitigation(damage, magicElement);
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
		final int damageBeforeTrueDefense = damage;
		if (opponent.isPlayer() && isPrimaryProjectileAttackType()) {
			damage = TrueDefense.apply((Player) opponent, damage);
		}
		final boolean trueDefenseBlocked = damageBeforeTrueDefense > 0 && damage == 0;
		secondaryEffectDamage = damage;
		int clericPreventedDamage = 0;
		if (isClericEligibleProjectileType()) {
			final ClericDirectCombatRuntime.BeforeDamage clericDamage =
				ClericDirectCombatRuntime.beforeDirectDamage(caster, opponent, damage);
			damage = clericDamage.getDamage();
			clericPreventedDamage = clericDamage.getPreventedDamage();
		}
		int lastHits = opponent.getLevel(Skill.HITS.id());
		final int hitSplatType = Summoning.getSummonDamageHitSplatType(caster);
		final int damageDealt;
		if (isPrimaryProjectileAttackType()) {
			final CombatEngagement engagement =
				caster.getOutgoingCombatEngagement();
			final java.util.UUID encounterId = engagement != null
				&& engagement.peerOf(caster) == opponent
				? engagement.getEncounterId() : null;
			final DamageRequest damageRequest = DamageRequest.resolvedLegacy(
				caster, opponent, DamageRequest.SourceCategory.ACTOR,
				primaryProjectileEffectKey(), damage)
				.eventId(getUUID())
				.encounterId(encounterId)
				.style(primaryProjectileCombatStyle())
				.hitSplatType(hitSplatType)
				.build();
			final DamageResult damageResult = opponent.getWorld().getServer()
				.getResolvedDamageTransaction().apply(damageRequest);
			damageDealt = damageResult.getLegacyDamageDealt();
		} else if (damage >= 0) {
			final DamageRequest damageRequest = DamageRequest.resolvedLegacy(
				caster, opponent, DamageRequest.SourceCategory.ACTOR,
				UNCLASSIFIED_PROJECTILE_EFFECT_KEY, damage)
				.eventId(getUUID())
				.hitSplatType(hitSplatType)
				.build();
			final DamageResult damageResult = opponent.getWorld().getServer()
				.getResolvedDamageTransaction().apply(damageRequest);
			damageDealt = damageResult.getLegacyDamageDealt();
		} else {
			// The admin projectile hook historically accepts signed values. A
			// negative value raises Hits above max and is not damage, so it cannot
			// be represented by the non-negative resolved-damage contract.
			opponent.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
			damageDealt = Math.min(damage, lastHits);
			opponent.getUpdateFlags().setDamage(new Damage(opponent, damage));
			opponent.getUpdateFlags().addHitSplat(
				new HitSplat(opponent, hitSplatType, damage));
		}
		if (impactEffectType > 0 && !trueDefenseBlocked) {
			opponent.getUpdateFlags().setCombatEffect(new CombatEffect(opponent, impactEffectType));
		}

		if (caster.isNpc() && opponent.isPlayer()) {
			((Player) opponent).updateDamageAndBlockedDamageTracking(
				caster, damageDealt, clericPreventedDamage);
			applyBalrogMagicSplash((Npc) caster, (Player) opponent, damageDealt);
		}

		if (caster.isPlayer()) {
			Player casterPlayer = (Player) caster;
			if (opponent.isNpc()) {
				Npc npc = (Npc) opponent;
				if (type == 1 || type == 4) {
					damage = Math.min(damage, lastHits);
					npc.addMageDamage(casterPlayer, damage);
					Summoning.recordOwnerCombatSummonDamage(casterPlayer, npc, damage);
					DivineGrace.apply(casterPlayer, damage);
				}
				else if (type == 2 || type == 5) {
					damage = Math.min(damage, lastHits);
					npc.addRangeDamage(casterPlayer, damage);
					Summoning.recordOwnerCombatSummonDamage(casterPlayer, npc, damage);
					DivineGrace.apply(casterPlayer, damage);
				}
			}
			if (opponent.isPlayer()) {
				DivineGrace.apply(casterPlayer, damageDealt);
			}
		} else if (Summoning.isSummon(caster) && opponent.isNpc()) {
			Summoning.creditSummonProjectileDamage(caster, opponent, Math.min(damage, lastHits), type);
		}
		Summoning.applySummonLifesteal(caster, opponent, damageDealt);

		// Update party menu with new HITS stat.
		if (opponent.isPlayer()) {
			Player affectedPlayer = (Player) opponent;
			ActionSender.sendStat(affectedPlayer, Skill.HITS.id());
			CorrosiveAura.apply(affectedPlayer, caster, damageDealt);
			DivineRetribution.Result result = DivineRetribution.apply(affectedPlayer, caster, damageDealt);
			if (result.killedAttacker()) {
				if (type == 2 || type == 5) {
					affectedPlayer.resetRange();
				}
				caster.killedBy(affectedPlayer);
			}
			if (affectedPlayer.getConfig().WANT_PARTIES) {
				if (affectedPlayer.getParty() != null) {
					affectedPlayer.getParty().sendParty();
				}
			}
		}

		if (damageDealt > 0 && caster.isPlayer() && (type == 1 || type == 4)) {
			applyBloodRobeSplash((Player) caster, damageDealt);
		}
		if (caster.isPlayer() && isPrimaryProjectileAttackType()) {
			((Player) caster).applyBloodAmuletLifesteal(damageDealt);
		}
		if (isClericEligibleProjectileType()) {
			clericDirectDamageDealt = damageDealt;
			clericDirectImpactResolved = true;
			final ClericDirectCombatRuntime.AfterDamage clericAfter =
				ClericDirectCombatRuntime.afterExistingLifesteal(
					caster, opponent, damageDealt, !deferClericRally);
			if (clericAfter.getThornsDamage() > 0) {
				inflictClericThornsDamage(clericAfter.getThornsDamage());
			}
		}
		if (caster.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			// Thorns owns the attacker's terminal death. Still settle a simultaneous
			// primary-hit kill, but do not let a dead attacker trigger later procs.
			if (opponent.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				handleDeath();
			}
			return;
		}

		if (caster.isPlayer() && opponent.isNpc() && opponent.getSkills().getLevel(Skill.HITS.id()) > 0
			&& ((Player) caster).applyDeathRingChargeHit((Npc) opponent)) {
			handleDeath();
			return;
		}

		applySplinterOnHitEffect();
		if (opponent.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			if (caster.isPlayer() && opponent.isNpc()) {
				applyDeathRobeOverkillSplash((Player) caster, (Npc) opponent, damage - lastHits);
			}
			handleDeath();
		} else {
			if (Summoning.applySummonOnHitEffects(caster, opponent, damage)) {
				handleDeath();
				return;
			}
			applyWeaponPoison();
			applyLeatherSetOnHitEffects();
			if (!attackSuppressed) {
				applyDragonWeaponBreathDamage();
				ElderGreenDragonSpecialAttacks.maybeApplyProjectileAoe(getWorld(), caster, opponent, false);
			}
			if (opponent.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				handleDeath();
				return;
			}
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
				opponent.applyPoison(acidPoisonPower, acidPoisonPower, caster);
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
		final DamageRequest damageRequest = DamageRequest.resolvedLegacy(
			creditedSource, target, DamageRequest.SourceCategory.OWNED_EFFECT,
			FROSTBITE_REFLECTION_POLICY.getStableKey(), reflectedDamage)
			.eventId(getUUID())
			.style(CombatStyle.MAGIC)
			.hitSplatType(HitSplat.TYPE_ARMOR_PROC)
			.build();
		final DamageResult damageResult = target.getWorld().getServer()
			.getResolvedDamageTransaction().apply(damageRequest);
		if (target.isNpc() && creditedSource.isPlayer()) {
			((Npc) target).addMageDamage(
				(Player) creditedSource, damageResult.getLegacyDamageDealt());
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
		if (Summoning.isPlayerAreaEffectSuppressed(casterPlayer)) {
			return;
		}
		final Npc splinterTarget = selectSplinterTarget(casterPlayer, opponent);
		if (splinterTarget == null) {
			return;
		}
		final int splinterDamage = Math.max(1,
			(int) Math.ceil(secondaryEffectDamage / 2.0D));
		final DamageRequest damageRequest = DamageRequest.resolvedLegacy(
			casterPlayer, splinterTarget,
			DamageRequest.SourceCategory.OWNED_EFFECT,
			SPLINTER_POLICY.getStableKey(), splinterDamage)
			.eventId(getUUID())
			.style(CombatStyle.MAGIC)
			.hitSplatType(HitSplat.TYPE_ARMOR_PROC)
			.build();
		final DamageResult damageResult = splinterTarget.getWorld().getServer()
			.getResolvedDamageTransaction().apply(damageRequest);
		splinterTarget.addMageDamage(
			casterPlayer, damageResult.getLegacyDamageDealt());
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
		return SplinterTargetSelectionPolicy.select(
			casterPlayer, primaryTarget, ProductionGameRandom.INSTANCE);
	}

	private void applyWeaponPoison() {
		if (!caster.isPlayer()) {
			return;
		}
		final Player casterPlayer = (Player) caster;
		casterPlayer.removeAttribute("dragon_breath_armor_proc");
		if (damage <= 0) {
			return;
		}
		final int weaponMaxPower = PoisonPower.getWeaponMaxPoisonPower(poisonWeaponId);
		final boolean isMagicAttack = type == 1 || type == 4;
		final boolean isRangedAttack = type == 2 || type == 5;
		final int styleArmorMaxPower = isMagicAttack
			? casterPlayer.getMagicPoisonArmorMaxPower()
			: (isRangedAttack ? casterPlayer.getRangedPoisonArmorMaxPower() : 0);
		final int breathArmorMaxPower = casterPlayer.getDragonBreathArmorMaxPoisonPower();
		final int armorMaxPower = styleArmorMaxPower + breathArmorMaxPower;
		final int totalMaxPower = weaponMaxPower + armorMaxPower;
		if (totalMaxPower <= 0) {
			return;
		}

		int appliedPoisonPower = 0;
		if (weaponMaxPower > 0 && PoisonProcChance.rollWeapon(casterPlayer, opponent, poisonWeaponId)) {
			appliedPoisonPower = Math.max(appliedPoisonPower, PoisonPower.getWeaponAppliedPoisonPower(poisonWeaponId));
		}
		final boolean magicArmorPoisonProc = isMagicAttack && styleArmorMaxPower > 0
			&& PoisonProcChance.rollArmor(casterPlayer, opponent, "magic");
		final boolean rangedArmorPoisonProc = isRangedAttack && styleArmorMaxPower > 0
			&& PoisonProcChance.rollArmor(casterPlayer, opponent, "ranged");
		if (magicArmorPoisonProc) {
			appliedPoisonPower = Math.max(appliedPoisonPower, casterPlayer.getMagicPoisonArmorAppliedPower());
		}
		if (rangedArmorPoisonProc) {
			appliedPoisonPower = Math.max(appliedPoisonPower, casterPlayer.getRangedPoisonArmorAppliedPower());
		}
		if (magicArmorPoisonProc || rangedArmorPoisonProc) {
			opponent.getUpdateFlags().setProjectile(new Projectile(casterPlayer, opponent, Projectile.ACID_ARMOR_PROC));
		}
		final double breathProcChance = casterPlayer.getDragonBreathArmorProcChance();
		if (breathProcChance > 0.0D
				&& DataConversions.getRandom().nextDouble() < breathProcChance) {
			appliedPoisonPower = Math.max(appliedPoisonPower,
				casterPlayer.getDragonBreathArmorAppliedPoisonPower());
			casterPlayer.setAttribute("dragon_breath_armor_proc",
				casterPlayer.getDragonBreathArmorProcKey());
		}
		if (appliedPoisonPower <= 0) {
			return;
		}

			opponent.applyPoison(appliedPoisonPower, totalMaxPower, casterPlayer);
		if (opponent.isNpc()) {
			casterPlayer.message("@gr3@You @gr2@have @gr1@poisioned @gr2@the " + ((Npc) opponent).getDef().name + "!");
		}
	}

	private void applyLeatherSetOnHitEffects() {
		if (!caster.isPlayer() || opponent.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final Player casterPlayer = (Player) caster;
		if (damage > 0 && (type == 2 || type == 5)) {
			casterPlayer.applyElementalGiantMightDebuff(opponent);
		}
		OgreStaggeringBlowProc.tryApply(
			casterPlayer, opponent, ProductionGameRandom.INSTANCE);
		BabyDragonSmokeProc.tryApply(
			casterPlayer, opponent, ProductionGameRandom.INSTANCE);
		final int infernalMaxHit = casterPlayer.getInfernalFireProcMaxHit();
		final int infernalPieces = casterPlayer.getInfernalArmorPieceCount();
		final InfernalFireProc.Result infernal = InfernalFireProc.tryApply(
			casterPlayer, opponent, ProductionGameRandom.INSTANCE,
			procDamage -> inflictAuxiliaryMagicDamage(caster, opponent, procDamage),
			procDamageDealt -> {
				if (infernalMaxHit == CombatEffectUtil.HELLS_INFERNO_MAX_HIT
						&& opponent.isNpc()) {
					applyHellsInfernoSplash(casterPlayer, (Npc) opponent, procDamageDealt);
				}
			});
		if (infernal.isConfigured()) {
			CombatEffectUtil.sendInfernalProcDebug(casterPlayer, "projectile", opponent, damage, infernalPieces,
				infernal.getMaxHit(), infernal.getRoll(), infernal.getChance(), infernal.isTriggered(),
				infernal.getRolledDamage(), infernal.getDamageDealt());
		} else if (infernalPieces > 0) {
			CombatEffectUtil.sendInfernalProcDebug(casterPlayer, "projectile", opponent, damage, infernalPieces,
				infernal.getMaxHit(), -1.0D, 0.0D, false, 0, 0);
		}
		BlueDragonWaterProc.tryApply(casterPlayer, opponent,
			ProductionGameRandom.INSTANCE,
			procDamage -> inflictAuxiliaryTrueDamage(
				caster, opponent, procDamage));
		EarthDragonSlowProc.tryApply(casterPlayer, opponent,
			ProductionGameRandom.INSTANCE,
			procDamage -> inflictAuxiliaryTrueDamage(
				caster, opponent, procDamage));
		RedDragonFireProc.tryApply(casterPlayer, opponent,
			ProductionGameRandom.INSTANCE,
			procDamage -> inflictAuxiliaryTrueDamage(
				caster, opponent, procDamage));
		final String dragonBreathProc = casterPlayer.getAttribute("dragon_breath_armor_proc", "");
		if ("black".equals(dragonBreathProc) || "king_black".equals(dragonBreathProc)) {
			casterPlayer.getUpdateFlags().setCombatEffect(new CombatEffect(casterPlayer, CombatEffect.DRAGON_BREATH));
		}
		BlackDragonBreathFollowup.tryApply(casterPlayer, dragonBreathProc,
			ProductionGameRandom.INSTANCE,
			procDamage -> inflictAuxiliaryTrueDamage(
				caster, opponent, procDamage));
		KingBlackDragonBreathFollowup.tryApply(casterPlayer, opponent,
			dragonBreathProc, ProductionGameRandom.INSTANCE,
			procDamage -> inflictAuxiliaryTrueDamage(caster, opponent, procDamage));
		ElderGreenDragonArmorProc.tryApply(casterPlayer, damage,
			ProductionGameRandom.INSTANCE, procDamage ->
				ElderGreenDragonArmorEffect.applyProc(casterPlayer, opponent,
					procDamage));
	}

	private void applyHellsInfernoSplash(final Player player, final Npc primaryTarget,
			final int primaryDamageDealt) {
		HellsInfernoNpcSplash.apply(player, primaryTarget, primaryDamageDealt,
			(npc, splashDamage) -> inflictAuxiliaryMagicDamage(player, npc, splashDamage));
	}

	private void applyDragonWeaponBreathDamage() {
		if (dragonBreathDamage <= 0 || !(type == 2 || type == 5)
			|| opponent.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final int slashEffect = DataConversions.random(0, 1) == 0
			? CombatEffect.DRAGON_WEAPON_BREATH
			: CombatEffect.DRAGON_WEAPON_SLASH_2;
		opponent.getUpdateFlags().setCombatEffect(new CombatEffect(opponent, slashEffect));
		inflictAuxiliaryTrueDamage(caster, opponent, dragonBreathDamage);
	}

	private int applyPlayerProjectileDamageBuff(final Player player, final int damage) {
		return PlayerProjectileDamageBuff.apply(player, damage,
			type == 1 || type == 4, earthAttackSpeedDebuffPercent,
			waterMaxHitDebuffPercent, fireDefenseDebuffPercent);
	}

	private void applyBloodRobeSplash(final Player casterPlayer, final int damageDealt) {
		if (Summoning.isPlayerAreaEffectSuppressed(casterPlayer)
			|| !bloodSpell || opponent == null || !opponent.isNpc()) {
			return;
		}
		final double splashPercent = casterPlayer.getBloodRobeSpellSplashPercent();
		if (splashPercent <= 0.0D || damageDealt <= 0) {
			return;
		}
		final int splashDamage = Math.max(1, (int) Math.floor(damageDealt * splashPercent));
		for (Npc npc : PlayerOwnedNpcRadiusSelection.aroundPrimary(
				casterPlayer, (Npc) opponent, 2)) {
			inflictBloodRobeSplashDamage(casterPlayer, npc, splashDamage);
		}
	}

	private void inflictBloodRobeSplashDamage(final Player casterPlayer, final Npc npc, final int splashDamage) {
		final DamageRequest damageRequest = DamageRequest.resolvedLegacy(
			casterPlayer, npc, DamageRequest.SourceCategory.OWNED_EFFECT,
			BLOOD_ROBE_SPLASH_POLICY.getStableKey(), splashDamage)
			.eventId(getUUID())
			.style(CombatStyle.MAGIC)
			.hitSplatType(HitSplat.TYPE_ARMOR_PROC)
			.build();
		final DamageResult damageResult = npc.getWorld().getServer()
			.getResolvedDamageTransaction().apply(damageRequest);
		final int damageDealt = damageResult.getLegacyDamageDealt();
		npc.addMageDamage(casterPlayer, damageDealt);
		Summoning.recordOwnerCombatSummonDamage(casterPlayer, npc, damageDealt);
		if (npc.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			npc.setLastCombatState(CombatState.LOST);
			casterPlayer.setKillType(KillType.MAGIC);
			npc.killedBy(casterPlayer);
		}
	}

	private void applyDeathRobeOverkillSplash(final Player player, final Npc primaryTarget, final int overkillDamage) {
		DeathRobeOverkillSplash.apply(player, primaryTarget, overkillDamage,
			(npc, splashDamage) -> {
			final CombatStyle splashStyle = type == 1 || type == 4
				? CombatStyle.MAGIC : CombatStyle.RANGED;
			final DamageRequest damageRequest = DamageRequest.resolvedLegacy(
				player, npc, DamageRequest.SourceCategory.OWNED_EFFECT,
				DEATH_ROBE_OVERKILL_POLICY.getStableKey(), splashDamage)
				.eventId(getUUID())
				.style(splashStyle)
				.hitSplatType(HitSplat.TYPE_ARMOR_PROC)
				.build();
			final DamageResult damageResult = npc.getWorld().getServer()
				.getResolvedDamageTransaction().apply(damageRequest);
			final int damageDealt = damageResult.getLegacyDamageDealt();
			if (type == 1 || type == 4) {
				npc.addMageDamage(player, damageDealt);
			} else {
				npc.addRangeDamage(player, damageDealt);
			}
			Summoning.recordOwnerCombatSummonDamage(player, npc, damageDealt);
			if (npc.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				npc.setLastCombatState(CombatState.LOST);
				player.setKillType(type == 1 || type == 4 ? KillType.MAGIC : KillType.RANGED);
				npc.killedBy(player);
			}
		});
	}

	private void applyBalrogMagicSplash(final Npc balrog, final Player primaryTarget, final int primaryDamageDealt) {
		if (primaryDamageDealt <= 0 || type != 1 || balrog == null || primaryTarget == null
			|| balrog.getDef() == null || !"balrog".equalsIgnoreCase(balrog.getDef().getName())) {
			return;
		}
		final int baseSplashDamage = Math.max(1, (int) Math.ceil(primaryDamageDealt * 0.5D));
		for (Player splashTarget : balrog.getViewArea().getPlayersInView()) {
			if (splashTarget == null || splashTarget == primaryTarget || splashTarget.isRemoved()
				|| splashTarget.getSkills().getLevel(Skill.HITS.id()) <= 0
				|| !splashTarget.withinRange(primaryTarget.getLocation(), 2)
				|| !Summoning.canSummonAttack(balrog, splashTarget)) {
				continue;
			}
			int splashDamage = splashTarget.applyRobeDamageMitigation(baseSplashDamage, magicElement);
			splashDamage = splashTarget.applyPotionMagicDamageReduction(splashDamage);
			final int splashDamageBeforeTrueDefense = splashDamage;
			splashDamage = TrueDefense.apply(splashTarget, splashDamage);
			final boolean trueDefenseBlocked = splashDamageBeforeTrueDefense > 0 && splashDamage == 0;
			if (splashDamage <= 0) {
				if (trueDefenseBlocked) {
					ActionSender.sendStat(splashTarget, Skill.HITS.id());
				}
				continue;
			}
			final DamageRequest damageRequest = DamageRequest.resolvedLegacy(
				balrog, splashTarget, DamageRequest.SourceCategory.OWNED_EFFECT,
				BALROG_MAGIC_SPLASH_POLICY.getStableKey(), splashDamage)
				.eventId(getUUID())
				.style(CombatStyle.MAGIC)
				.hitSplatType(HitSplat.TYPE_ARMOR_PROC)
				.build();
			final DamageResult damageResult = splashTarget.getWorld().getServer()
				.getResolvedDamageTransaction().apply(damageRequest);
			final int damageDealt = damageResult.getLegacyDamageDealt();
			if (impactEffectType > 0 && !trueDefenseBlocked) {
				splashTarget.getUpdateFlags().setCombatEffect(new CombatEffect(splashTarget, impactEffectType));
			}
			splashTarget.updateDamageAndBlockedDamageTracking(balrog, damageDealt, 0);
			ActionSender.sendStat(splashTarget, Skill.HITS.id());
			if (splashTarget.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				splashTarget.killedBy(balrog);
			}
		}
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

		final DamageRequest damageRequest = DamageRequest.resolvedLegacy(
			hitter, target, DamageRequest.SourceCategory.OWNED_EFFECT,
			AUXILIARY_MAGIC_DAMAGE_POLICY.getStableKey(), bonusDamage)
			.eventId(getUUID())
			.style(CombatStyle.MAGIC)
			.hitSplatType(HitSplat.TYPE_ARMOR_PROC)
			.build();
		final DamageResult damageResult = target.getWorld().getServer()
			.getResolvedDamageTransaction().apply(damageRequest);
		final int damageDealt = damageResult.getLegacyDamageDealt();
		if (target.isNpc() && hitter.isPlayer()) {
			((Npc) target).addMageDamage((Player) hitter, damageDealt);
		}
		if (target.isPlayer()) {
			ActionSender.sendStat((Player) target, Skill.HITS.id());
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

		final DamageRequest damageRequest = DamageRequest.resolvedLegacy(
			hitter, target, DamageRequest.SourceCategory.OWNED_EFFECT,
			AUXILIARY_TRUE_DAMAGE_POLICY.getStableKey(), bonusDamage)
			.eventId(getUUID())
			.style(CombatStyle.MELEE)
			.hitSplatType(HitSplat.TYPE_ARMOR_PROC)
			.build();
		final DamageResult damageResult = target.getWorld().getServer()
			.getResolvedDamageTransaction().apply(damageRequest);
		if (target.isNpc() && hitter.isPlayer()) {
			((Npc) target).addCombatDamage(
				(Player) hitter, damageResult.getLegacyDamageDealt());
		}
		if (target.isPlayer()) {
			ActionSender.sendStat((Player) target, Skill.HITS.id());
		}
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			handleDeath();
		}
	}

	private void handleDeath() {
		if (caster.isPlayer()) {
			Player player = (Player) caster;
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

	private boolean isPrimaryProjectileAttackType() {
		return type == 1 || type == 2 || type == 4 || type == 5;
	}

	private CombatStyle primaryProjectileCombatStyle() {
		return type == 1 || type == 4
			? CombatStyle.MAGIC : CombatStyle.RANGED;
	}

	private String primaryProjectileEffectKey() {
		if (type == 4) {
			return "projectile-iban-primary";
		}
		if (type == 5) {
			return "projectile-cannon-primary";
		}
		final String style = type == 1 ? "magic" : "ranged";
		if (Summoning.isSummon(caster)) {
			return "projectile-summon-" + style + "-primary";
		}
		if (caster.isNpc()) {
			return "projectile-npc-" + style + "-primary";
		}
		if (type == 2 && isThrownProjectile()) {
			return "projectile-player-thrown-primary";
		}
		return "projectile-player-" + style + "-primary";
	}

	private boolean isThrownProjectile() {
		return RangeUtils.THROWING_KNIVES.contains(poisonWeaponId)
			|| RangeUtils.THROWING_DARTS.contains(poisonWeaponId)
			|| RangeUtils.SHURIKENS.contains(poisonWeaponId);
	}

	private boolean isClericEligibleProjectileType() {
		return type == 1 || type == 2 || type == 4;
	}

	/** Enters the producer-declared delayed-impact policy exactly once. */
	protected final ProjectileImpactDecision beginProjectileImpact() {
		if (!impactLedger.claimImpact()) {
			return impactLedger.duplicate();
		}
		try {
			if (canceled && launchSnapshot.getSpecification()
					.getImpactPolicy().honorsCancellation()) {
				return impactLedger.invalidate(
					ProjectileImpactDecision.Reason.EXPLICIT_CANCELLATION,
					null, null);
			}
			final ProjectileImpactDecision.Reason validation =
				ProjectileImpactValidator.validate(
					launchSnapshot, caster, opponent);
			if (validation
					!= ProjectileImpactDecision.Reason.CURRENT_POLICY_ACCEPTED) {
				return impactLedger.invalidate(
					validation,
					caster.getWorldLocation(), opponent.getWorldLocation());
			}
			return impactLedger.authorize(
				caster.getWorldLocation(), opponent.getWorldLocation());
		} catch (final RuntimeException failure) {
			impactLedger.fail();
			throw failure;
		} catch (final Error failure) {
			impactLedger.fail();
			throw failure;
		}
	}

	protected final void completeProjectileImpact(
			final ProjectileImpactDecision impact) {
		impactLedger.complete(impact);
	}

	protected final void failProjectileImpact() {
		impactLedger.fail();
	}

	public final ProjectileLaunchSnapshot getLaunchSnapshot() {
		return launchSnapshot;
	}

	public final ProjectileImpactLedger.State getProjectileImpactState() {
		return impactLedger.getState();
	}

	public final ProjectileImpactDecision getInitialProjectileImpactDecision() {
		return impactLedger.getInitialDecision();
	}

	public final int getProjectileImpactCallbackCount() {
		return impactLedger.getCallbackCount();
	}

	public final ProjectileResourceLedger getProjectileResourceLedger() {
		return resourceLedger;
	}

	/** Defers only Rally so established god-spell lifesteal can resolve first. */
	public void deferClericRally() {
		deferClericRally = true;
	}

	public void resolveDeferredClericRally() {
		if (!deferClericRally || clericDeferredRallyResolved
				|| !clericDirectImpactResolved) {
			return;
		}
		clericDeferredRallyResolved = true;
		ClericDirectCombatRuntime.applyDeferredRally(
			caster, opponent, clericDirectDamageDealt);
	}

	private void inflictClericThornsDamage(final int reflectedDamage) {
		if (reflectedDamage <= 0 || caster.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final DamageRequest damageRequest = DamageRequest.resolvedLegacy(
			opponent, caster, DamageRequest.SourceCategory.OWNED_EFFECT,
			CLERIC_THORNS_POLICY.getStableKey(), reflectedDamage)
			.eventId(getUUID())
			.style(CombatStyle.MELEE)
			.hitSplatType(HitSplat.TYPE_ARMOR_PROC)
			.build();
		final DamageResult damageResult = caster.getWorld().getServer()
			.getResolvedDamageTransaction().apply(damageRequest);
		if (caster.isNpc() && opponent.isPlayer()) {
			((Npc) caster).addCombatDamage(
				(Player) opponent, damageResult.getLegacyDamageDealt());
		}
		if (caster.isPlayer()) {
			ActionSender.sendStat((Player) caster, Skill.HITS.id());
		}
		if (caster.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			caster.killedBy(opponent);
		}
	}

	public void setCanceled(boolean b) {
		canceled = b;
	}

}
