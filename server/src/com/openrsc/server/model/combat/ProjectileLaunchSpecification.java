package com.openrsc.server.model.combat;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.model.entity.npc.NpcMagicElement;

/**
 * Immutable producer-owned parameters for one delayed projectile launch.
 *
 * <p>The specification describes already-decided launch facts. It does not
 * calculate damage, spend resources, award experience, decide impact
 * eligibility, or own projectile settlement.</p>
 */
public final class ProjectileLaunchSpecification {
	/** Stable current producer identities and their broader event families. */
	public enum Producer {
		PLAYER_BOW("player-bow", "player-bow-projectile", ProjectileLaunchSnapshot.Kind.DAMAGING),
		PLAYER_THROWN("player-thrown", "player-thrown-projectile", ProjectileLaunchSnapshot.Kind.DAMAGING),
		PLAYER_SHURIKEN("player-shuriken", "player-shuriken-projectile", ProjectileLaunchSnapshot.Kind.DAMAGING),
		PLAYER_MAGIC("player-magic", "player-magic-projectile", ProjectileLaunchSnapshot.Kind.DAMAGING),
		PLAYER_IBAN_MAGIC("player-iban-magic", "iban-magic-projectile", ProjectileLaunchSnapshot.Kind.DAMAGING),
		CANNON("cannon", "cannon-projectile", ProjectileLaunchSnapshot.Kind.DAMAGING),
		NPC_RANGED("npc-ranged", "npc-ranged-projectile", ProjectileLaunchSnapshot.Kind.DAMAGING),
		NPC_MAGIC("npc-magic", "npc-magic-projectile", ProjectileLaunchSnapshot.Kind.DAMAGING),
		NPC_COMPATIBILITY("npc-compatibility", "npc-compatibility-projectile", ProjectileLaunchSnapshot.Kind.DAMAGING),
		LEGACY_NPC_RANGED("legacy-npc-ranged", "npc-ranged-projectile", ProjectileLaunchSnapshot.Kind.DAMAGING),
		SUMMON_RANGED("summon-ranged", "summon-ranged-projectile", ProjectileLaunchSnapshot.Kind.DAMAGING),
		SUMMON_MAGIC("summon-magic", "summon-magic-projectile", ProjectileLaunchSnapshot.Kind.DAMAGING),
		SUMMON_COMPATIBILITY("summon-compatibility", "summon-compatibility-projectile", ProjectileLaunchSnapshot.Kind.DAMAGING),
		ADMIN_DEBUG("admin-debug", "admin-debug-projectile", ProjectileLaunchSnapshot.Kind.DAMAGING),
		COMPATIBILITY("positional-compatibility", "compatibility-projectile", ProjectileLaunchSnapshot.Kind.DAMAGING),
		MAGIC_SCRIPTED_EFFECT("magic-scripted-effect", "custom-projectile", ProjectileLaunchSnapshot.Kind.SCRIPTED_EFFECT),
		LEGENDS_HOLY_WATER("legends-holy-water", "custom-projectile", ProjectileLaunchSnapshot.Kind.SCRIPTED_EFFECT),
		GNOME_BALL("gnome-ball", "ball-projectile", ProjectileLaunchSnapshot.Kind.BENIGN_EFFECT),
		BENIGN_COMPATIBILITY("benign-compatibility", "benign-projectile", ProjectileLaunchSnapshot.Kind.BENIGN_EFFECT);

		private final String key;
		private final String familyKey;
		private final ProjectileLaunchSnapshot.Kind kind;

		Producer(final String key, final String familyKey,
				final ProjectileLaunchSnapshot.Kind kind) {
			this.key = key;
			this.familyKey = familyKey;
			this.kind = kind;
		}

		public String getKey() {
			return key;
		}

		public String getFamilyKey() {
			return familyKey;
		}

		public ProjectileLaunchSnapshot.Kind getKind() {
			return kind;
		}
	}

	private final Producer producer;
	private final int proposedDamage;
	private final int attackType;
	private final boolean shouldChase;
	private final int poisonWeaponId;
	private final int windAccuracyDebuffPercent;
	private final int waterMaxHitDebuffPercent;
	private final int earthAttackSpeedDebuffPercent;
	private final int fireDefenseDebuffPercent;
	private final int projectileType;
	private final int impactEffectType;
	private final boolean showProjectile;
	private final NpcMagicElement magicElement;
	private final int startleProcChancePercent;
	private final int acidPoisonPower;
	private final int frostbiteProcChancePercent;
	private final int splinterProcChancePercent;
	private final boolean bloodSpell;
	private final int dragonBreathDamage;
	private final DuplicationStrategy duplicationStrategy;

	private ProjectileLaunchSpecification(final Builder builder) {
		this.producer = builder.producer;
		this.proposedDamage = builder.proposedDamage;
		this.attackType = builder.attackType;
		this.shouldChase = builder.shouldChase;
		this.poisonWeaponId = builder.poisonWeaponId;
		this.windAccuracyDebuffPercent = builder.windAccuracyDebuffPercent;
		this.waterMaxHitDebuffPercent = builder.waterMaxHitDebuffPercent;
		this.earthAttackSpeedDebuffPercent = builder.earthAttackSpeedDebuffPercent;
		this.fireDefenseDebuffPercent = builder.fireDefenseDebuffPercent;
		this.projectileType = builder.projectileType;
		this.impactEffectType = builder.impactEffectType;
		this.showProjectile = builder.showProjectile;
		this.magicElement = builder.magicElement;
		this.startleProcChancePercent = builder.startleProcChancePercent;
		this.acidPoisonPower = builder.acidPoisonPower;
		this.frostbiteProcChancePercent = builder.frostbiteProcChancePercent;
		this.splinterProcChancePercent = builder.splinterProcChancePercent;
		this.bloodSpell = builder.bloodSpell;
		this.dragonBreathDamage = builder.dragonBreathDamage;
		this.duplicationStrategy = builder.duplicationStrategy;
	}

	public static Builder builder(final Producer producer,
			final int proposedDamage, final int attackType) {
		return new Builder(producer, proposedDamage, attackType);
	}

	public Producer getProducer() {
		return producer;
	}

	public String getProducerKey() {
		return producer.getKey();
	}

	public String getFamilyKey() {
		return producer.getFamilyKey();
	}

	public ProjectileLaunchSnapshot.Kind getKind() {
		return producer.getKind();
	}

	public int getProposedDamage() {
		return proposedDamage;
	}

	public int getAttackType() {
		return attackType;
	}

	public boolean shouldChase() {
		return shouldChase;
	}

	public int getPoisonWeaponId() {
		return poisonWeaponId;
	}

	public int getWindAccuracyDebuffPercent() {
		return windAccuracyDebuffPercent;
	}

	public int getWaterMaxHitDebuffPercent() {
		return waterMaxHitDebuffPercent;
	}

	public int getEarthAttackSpeedDebuffPercent() {
		return earthAttackSpeedDebuffPercent;
	}

	public int getFireDefenseDebuffPercent() {
		return fireDefenseDebuffPercent;
	}

	public int getProjectileType() {
		return projectileType;
	}

	public int getImpactEffectType() {
		return impactEffectType;
	}

	public boolean shouldShowProjectile() {
		return showProjectile;
	}

	public NpcMagicElement getMagicElement() {
		return magicElement;
	}

	public int getStartleProcChancePercent() {
		return startleProcChancePercent;
	}

	public int getAcidPoisonPower() {
		return acidPoisonPower;
	}

	public int getFrostbiteProcChancePercent() {
		return frostbiteProcChancePercent;
	}

	public int getSplinterProcChancePercent() {
		return splinterProcChancePercent;
	}

	public boolean isBloodSpell() {
		return bloodSpell;
	}

	public int getDragonBreathDamage() {
		return dragonBreathDamage;
	}

	public DuplicationStrategy getDuplicationStrategy() {
		return duplicationStrategy;
	}

	public static final class Builder {
		private final Producer producer;
		private final int proposedDamage;
		private final int attackType;
		private boolean shouldChase = true;
		private int poisonWeaponId = -1;
		private int windAccuracyDebuffPercent;
		private int waterMaxHitDebuffPercent;
		private int earthAttackSpeedDebuffPercent;
		private int fireDefenseDebuffPercent;
		private int projectileType;
		private int impactEffectType;
		private boolean showProjectile = true;
		private NpcMagicElement magicElement = NpcMagicElement.NONE;
		private int startleProcChancePercent;
		private int acidPoisonPower;
		private int frostbiteProcChancePercent;
		private int splinterProcChancePercent;
		private boolean bloodSpell;
		private int dragonBreathDamage;
		private DuplicationStrategy duplicationStrategy =
			DuplicationStrategy.ONE_PER_MOB;

		private Builder(final Producer producer, final int proposedDamage,
				final int attackType) {
			if (producer == null) {
				throw new IllegalArgumentException("producer cannot be null");
			}
			this.producer = producer;
			this.proposedDamage = proposedDamage;
			this.attackType = attackType;
			this.projectileType = attackType;
		}

		public Builder chase(final boolean shouldChase) {
			this.shouldChase = shouldChase;
			return this;
		}

		public Builder poisonWeaponId(final int poisonWeaponId) {
			this.poisonWeaponId = poisonWeaponId;
			return this;
		}

		public Builder elementalDebuffs(
				final int windAccuracyDebuffPercent,
				final int waterMaxHitDebuffPercent,
				final int earthAttackSpeedDebuffPercent,
				final int fireDefenseDebuffPercent) {
			this.windAccuracyDebuffPercent = windAccuracyDebuffPercent;
			this.waterMaxHitDebuffPercent = waterMaxHitDebuffPercent;
			this.earthAttackSpeedDebuffPercent = earthAttackSpeedDebuffPercent;
			this.fireDefenseDebuffPercent = fireDefenseDebuffPercent;
			return this;
		}

		public Builder presentation(final int projectileType,
				final int impactEffectType, final boolean showProjectile) {
			this.projectileType = projectileType;
			this.impactEffectType = impactEffectType;
			this.showProjectile = showProjectile;
			return this;
		}

		public Builder magicElement(final NpcMagicElement magicElement) {
			this.magicElement = magicElement == null
				? NpcMagicElement.NONE : magicElement;
			return this;
		}

		public Builder dualElementProcs(
				final int startleProcChancePercent,
				final int acidPoisonPower,
				final int frostbiteProcChancePercent,
				final int splinterProcChancePercent) {
			this.startleProcChancePercent = startleProcChancePercent;
			this.acidPoisonPower = acidPoisonPower;
			this.frostbiteProcChancePercent = frostbiteProcChancePercent;
			this.splinterProcChancePercent = splinterProcChancePercent;
			return this;
		}

		public Builder bloodSpell(final boolean bloodSpell) {
			this.bloodSpell = bloodSpell;
			return this;
		}

		public Builder dragonBreathDamage(final int dragonBreathDamage) {
			this.dragonBreathDamage = dragonBreathDamage;
			return this;
		}

		public Builder duplicationStrategy(
				final DuplicationStrategy duplicationStrategy) {
			if (duplicationStrategy == null) {
				throw new IllegalArgumentException(
					"duplicationStrategy cannot be null");
			}
			this.duplicationStrategy = duplicationStrategy;
			return this;
		}

		public ProjectileLaunchSpecification build() {
			return new ProjectileLaunchSpecification(this);
		}
	}
}
