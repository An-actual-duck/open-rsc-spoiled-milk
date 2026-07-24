package com.openrsc.server.model.entity.npc;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.util.rsc.DataConversions;

public enum NpcAttackStyleProfile {
	MELEE,
	PURE_RANGED,
	PURE_MAGIC,
	MELEE_RANGED,
	MELEE_FREQUENT_MAGIC,
	MELEE_MAGIC,
	MELEE_RARE_MAGIC;

	private static final int DEFAULT_PROJECTILE_RANGE = 5;
	private static final int FIRE_PILLAR_FIRE_DEFENSE_DEBUFF_PERCENT = 12;

	private static boolean isKolodionIntroForm(final Npc npc) {
		return npc != null && npc.getID() == NpcId.KOLODION_HUMAN.id();
	}

	private static boolean isKolodionOgreForm(final Npc npc) {
		return npc != null && npc.getID() == NpcId.KOLODION_OGRE.id();
	}

	private static boolean isKolodionSpiderForm(final Npc npc) {
		return npc != null && npc.getID() == NpcId.KOLODION_SPIDER.id();
	}

	private static boolean isKolodionSoulessForm(final Npc npc) {
		return npc != null && npc.getID() == NpcId.KOLODION_SOULESS.id();
	}

	private static boolean isKolodionDemonForm(final Npc npc) {
		return npc != null && npc.getID() == NpcId.KOLODION_DEMON.id();
	}

	public boolean isProjectilePrimary() {
		return this == PURE_RANGED || this == PURE_MAGIC;
	}

	public boolean usesRangedProjectiles() {
		return this == PURE_RANGED || this == MELEE_RANGED;
	}

	public boolean usesMagicProjectiles() {
		return this == PURE_MAGIC || this == MELEE_FREQUENT_MAGIC || this == MELEE_MAGIC || this == MELEE_RARE_MAGIC;
	}

	public int getProjectileRange(final Npc npc) {
		if (npc != null && npc.getDef() != null && npc.getDef().getProjectileRange() > 0) {
			return npc.getDef().getProjectileRange();
		}
		return DEFAULT_PROJECTILE_RANGE;
	}

	public boolean prefersProjectileAtDistance(final Npc npc, final int distance) {
		if (distance > getProjectileRange(npc)) {
			return false;
		}
		if (isProjectilePrimary()) {
			return true;
		}
		return distance > 1 || rollsPreferredProjectileAttack();
	}

	private boolean rollsPreferredProjectileAttack() {
		switch (this) {
			case MELEE_RANGED:
			case MELEE_MAGIC:
				return DataConversions.getRandom().nextInt(100) < 65;
			case MELEE_FREQUENT_MAGIC:
				return DataConversions.getRandom().nextInt(100) < 85;
			case MELEE_RARE_MAGIC:
				return DataConversions.getRandom().nextInt(100) < 10;
			default:
				return false;
		}
	}

	public double getMagicSpellPower(final Npc npc) {
		return Math.max(1.0D, npc.getMagicOffense() / 12.0D);
	}

	public int getRangedOffense(final Npc npc) {
		if (!usesRangedProjectiles()) {
			return 0;
		}
		return Math.max(1, Math.max(npc.getDef().getAtt(), npc.getDef().getStr()));
	}

	public int getRangedProjectileVisual(final Npc npc) {
		if (npc == null || npc.getDef() == null || npc.getDef().getName() == null) {
			return Projectile.ARROW;
		}
		final String name = npc.getDef().getName().toLowerCase();
		switch (name) {
			case "thief":
			case "rogue":
			case "head thief":
			case "pirate":
			case "bandit":
				return Projectile.THROWING_KNIFE;
			case "tribesman":
				return Projectile.THROWING_DART;
			case "yanille watchman":
				return Projectile.BOLT;
			default:
				return Projectile.ARROW;
		}
	}

	public int getMagicProjectileVisual(final Npc npc) {
		return getMagicProjectileVisual(npc, getMagicElement(npc));
	}

	public int getMagicProjectileVisual(final Npc npc, final NpcMagicElement element) {
		if (npc == null || npc.getDef() == null || npc.getDef().getName() == null) {
			return Projectile.MAGIC;
		}
		if (isKolodionSpiderForm(npc)) {
			return Projectile.ACID_DROP;
		}
		final String name = npc.getDef().getName().toLowerCase();
		if (element == NpcMagicElement.NONE && isHolyMagicNpcName(name)) {
			return Projectile.HOLY_MAGIC;
		}
		switch (element) {
			case AIR:
				return Projectile.WIND_ARROW;
			case WATER:
				return Projectile.WATER_BALL;
			case EARTH:
				return Projectile.ROCK_THROW;
			case FIRE:
				return Projectile.FIREBALL;
			case THUNDER:
				return Projectile.THUNDER_BALL;
			case WOOD:
				return Projectile.BRANCH_SPORE;
			default:
				return Projectile.MAGIC;
		}
	}

	public int getMagicImpactEffect(final Npc npc, final NpcMagicElement element) {
		final String name = npc == null || npc.getDef() == null || npc.getDef().getName() == null
			? ""
			: npc.getDef().getName().toLowerCase();
		if (isKolodionIntroForm(npc)) {
			return CombatEffect.NONE;
		}
		if (isKolodionOgreForm(npc)) {
			return getKolodionOgreImpactEffect(element);
		}
		if (isKolodionSpiderForm(npc)) {
			return CombatEffect.ACID_GUSH;
		}
		if (isKolodionSoulessForm(npc)) {
			return getKolodionSoulessImpactEffect(element);
		}
		if (isKolodionDemonForm(npc)) {
			return CombatEffect.FIRE_PILLAR;
		}
		final int enemySpecificEffect = CombatEffect.enemyMagicAttackEffect(name);
		if (enemySpecificEffect != CombatEffect.NONE) {
			return enemySpecificEffect;
		}
		if (isDragon(npc)) {
			return getDragonMagicImpactEffect(element);
		}
		return CombatEffect.NONE;
	}

	private static int getKolodionOgreImpactEffect(final NpcMagicElement element) {
		switch (element) {
			case AIR:
				return CombatEffect.WIND_BEAM;
			case EARTH:
				return CombatEffect.EARTH_BURST;
			default:
				return CombatEffect.NONE;
		}
	}

	private static int getKolodionSoulessImpactEffect(final NpcMagicElement element) {
		switch (element) {
			case AIR:
				return CombatEffect.TORNADO;
			case THUNDER:
				return CombatEffect.THUNDER_STRIKE;
			case WOOD:
				return CombatEffect.BATTERING_RAM;
			default:
				return CombatEffect.NONE;
		}
	}

	private static int getDragonMagicImpactEffect(final NpcMagicElement element) {
		switch (element) {
			case EARTH:
				return CombatEffect.EARTH_BURST;
			case WATER:
				return CombatEffect.WATER_ERUPTION;
			case FIRE:
				return CombatEffect.EXPLOSION;
			default:
				return CombatEffect.NONE;
		}
	}

	private static boolean isHolyMagicNpcName(final String name) {
		return name.contains("zamorak")
			|| name.contains("saradomin")
			|| name.contains("druid")
			|| name.contains("priest")
			|| isGodKnightNpcName(name)
			|| "monk".equals(name)
			|| name.startsWith("monk ");
	}

	private static boolean isGodKnightNpcName(final String name) {
		return name.contains("black knight")
			|| name.contains("white knight")
			|| name.contains("grey knight");
	}

	private static boolean prefersHolyMagicOnly(final String name) {
		return "monk".equals(name) || name.contains("priest");
	}

	public int getMagicOffense(final Npc npc) {
		if (!usesMagicProjectiles()) {
			return 0;
		}
		return Math.max(1, Math.max(npc.getDef().getAtt(), npc.getDef().getStr()));
	}

	public int getMagicAcidPoisonPower(final Npc npc, final NpcMagicElement element) {
		if (isKolodionSpiderForm(npc)) {
			return 40;
		}
		return 0;
	}

	public int getMagicStartleProcChancePercent(final Npc npc, final NpcMagicElement element) {
		if (isKolodionSoulessForm(npc) && element == NpcMagicElement.THUNDER) {
			return 25;
		}
		return 0;
	}

	public int getMagicSplinterProcChancePercent(final Npc npc, final NpcMagicElement element) {
		if (isKolodionSoulessForm(npc) && element == NpcMagicElement.WOOD) {
			return 25;
		}
		return 0;
	}

	public int getMagicFireDefenseDebuffPercent(final Npc npc, final NpcMagicElement element) {
		if (isKolodionDemonForm(npc) && element == NpcMagicElement.FIRE) {
			return FIRE_PILLAR_FIRE_DEFENSE_DEBUFF_PERCENT;
		}
		return 0;
	}

	public NpcMagicElement getMagicElement(final Npc npc) {
		if (!usesMagicProjectiles() || npc == null || npc.getDef() == null || npc.getDef().getName() == null) {
			return NpcMagicElement.NONE;
		}
		final String name = npc.getDef().getName().toLowerCase();
		if (isKolodionIntroForm(npc)) {
			return randomElement(NpcMagicElement.AIR, NpcMagicElement.WATER, NpcMagicElement.EARTH, NpcMagicElement.FIRE);
		}
		if (isKolodionOgreForm(npc)) {
			return randomElement(NpcMagicElement.AIR, NpcMagicElement.EARTH);
		}
		if (isKolodionSpiderForm(npc)) {
			return NpcMagicElement.NONE;
		}
		if (isKolodionSoulessForm(npc)) {
			return randomElement(NpcMagicElement.AIR, NpcMagicElement.THUNDER, NpcMagicElement.WOOD);
		}
		if (isKolodionDemonForm(npc)) {
			return NpcMagicElement.FIRE;
		}
		if (isHolyMagicNpcName(name)
			|| "lucien".equals(name)
			|| "salarin the twisted".equals(name)
			|| "otherworldly being".equals(name)) {
			return NpcMagicElement.NONE;
		}
		if (isDragon(npc)) {
			return getDragonMagicElement(npc);
		}
		switch (name) {
			case "darkwizard":
				return randomElement(NpcMagicElement.FIRE, NpcMagicElement.AIR);
			case "witch":
				return randomElement(NpcMagicElement.EARTH, NpcMagicElement.AIR);
			case "wizard":
				return randomElement(NpcMagicElement.WATER, NpcMagicElement.AIR);
			case "necromancer":
				return randomElement(NpcMagicElement.FIRE, NpcMagicElement.WATER);
			case "skeleton mage":
				return randomElement(NpcMagicElement.FIRE, NpcMagicElement.EARTH);
			case "ghost":
			case "nazastarool ghost":
				return randomElement(NpcMagicElement.WATER, NpcMagicElement.EARTH);
			case "battle mage":
				return randomElement(NpcMagicElement.AIR, NpcMagicElement.WATER, NpcMagicElement.EARTH, NpcMagicElement.FIRE);
			case "lesser demon":
			case "greater demon":
			case "chronozon":
			case "black demon":
			case "balrog":
			case "fire giant":
			case "delrith":
			case "fire warrior":
			case "the fire warrior of lesarkus":
				return NpcMagicElement.FIRE;
			case "ice giant":
			case "ice warrior":
			case "ice queen":
				return NpcMagicElement.WATER;
			case "moss giant":
			case "tree spirit":
				return NpcMagicElement.EARTH;
			default:
				if (name.contains("wizard")) {
					return randomElement(NpcMagicElement.WATER, NpcMagicElement.AIR);
				}
				return NpcMagicElement.NONE;
		}
	}

	private static NpcMagicElement getDragonMagicElement(final Npc npc) {
		switch (npc.getID()) {
			case 196: // GREEN_DRAGON
				return NpcMagicElement.EARTH;
			case 202: // BLUE_DRAGON
			case 203: // BABY_BLUE_DRAGON
				return NpcMagicElement.WATER;
			case 201: // RED_DRAGON
			case 291: // BLACK_DRAGON
			case 477: // KING_BLACK_DRAGON
			default:
				return NpcMagicElement.FIRE;
		}
	}

	private static NpcMagicElement randomElement(final NpcMagicElement... elements) {
		if (elements == null || elements.length == 0) {
			return NpcMagicElement.NONE;
		}
		return elements[DataConversions.getRandom().nextInt(elements.length)];
	}

	private static boolean isDragon(final Npc npc) {
		return npc.getID() == NpcId.DRAGON.id()
			|| npc.getID() == NpcId.RED_DRAGON.id()
			|| npc.getID() == NpcId.BLUE_DRAGON.id()
			|| npc.getID() == NpcId.BABY_BLUE_DRAGON.id()
			|| npc.getID() == NpcId.BLACK_DRAGON.id()
			|| npc.getID() == NpcId.KING_BLACK_DRAGON.id()
			|| npc.getDef().getName().toLowerCase().contains("dragon");
	}

	public static NpcAttackStyleProfile forNpc(final Npc npc) {
		if (npc == null || npc.getDef() == null || npc.getDef().getName() == null) {
			return MELEE;
		}

		if (isKolodionIntroForm(npc)) {
			return PURE_MAGIC;
		}
		if (isKolodionOgreForm(npc)) {
			return MELEE_FREQUENT_MAGIC;
		}
		if (isKolodionSpiderForm(npc)) {
			return MELEE_FREQUENT_MAGIC;
		}
		if (isKolodionSoulessForm(npc)) {
			return MELEE_FREQUENT_MAGIC;
		}
		if (isKolodionDemonForm(npc)) {
			return MELEE_RARE_MAGIC;
		}
		final String name = npc.getDef().getName().toLowerCase();
		if (isDragon(npc)) {
			return MELEE_MAGIC;
		}
		if (name.contains("wizard")) {
			return PURE_MAGIC;
		}
		if (prefersHolyMagicOnly(name)) {
			return PURE_MAGIC;
		}
		if (isGodKnightNpcName(name)) {
			return MELEE_RARE_MAGIC;
		}
		switch (name) {
			case "darkwizard":
			case "chaos druid":
			case "druid":
			case "witch":
			case "necromancer":
			case "skeleton mage":
				return PURE_MAGIC;
			case "gnome guard":
			case "guard":
			case "jailguard":
			case "carnillean guard":
			case "goblin guard":
			case "ogre guard":
			case "bedabin nomad guard":
			case "draft mercenary guard":
			case "rowdy guard":
			case "shantay pass guard":
			case "thief":
			case "rogue":
			case "head thief":
				return PURE_RANGED;
			case "monk of zamorak":
			case "chaos druid warrior":
			case "paladin":
			case "lesser demon":
			case "greater demon":
			case "black demon":
			case "balrog":
			case "moss giant":
			case "ice giant":
			case "fire giant":
			case "delrith":
			case "fire warrior":
			case "lucien":
			case "ghost":
			case "tree spirit":
			case "ice warrior":
			case "ice queen":
			case "the fire warrior of lesarkus":
			case "chronozon":
			case "nazastarool ghost":
			case "otherworldly being":
			case "salarin the twisted":
				return MELEE_MAGIC;
			case "battle mage":
				return PURE_MAGIC;
			case "mercenary":
			case "mercenary captain":
			case "khazard troop":
			case "gnome troop":
			case "pirate":
			case "bandit":
			case "tribesman":
			case "yanille watchman":
				return MELEE_RANGED;
			default:
				return MELEE;
		}
	}
}
