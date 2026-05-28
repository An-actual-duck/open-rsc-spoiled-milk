package com.openrsc.server.model.entity.npc;

import com.openrsc.server.util.rsc.DataConversions;

public enum NpcAttackStyleProfile {
	MELEE,
	PURE_RANGED,
	PURE_MAGIC,
	MELEE_RANGED,
	MELEE_MAGIC;

	private static final int DEFAULT_PROJECTILE_RANGE = 5;

	public boolean isProjectilePrimary() {
		return this == PURE_RANGED || this == PURE_MAGIC;
	}

	public boolean usesRangedProjectiles() {
		return this == PURE_RANGED || this == MELEE_RANGED;
	}

	public boolean usesMagicProjectiles() {
		return this == PURE_MAGIC || this == MELEE_MAGIC;
	}

	public int getProjectileRange() {
		return DEFAULT_PROJECTILE_RANGE;
	}

	public boolean prefersProjectileAtDistance(final int distance) {
		if (distance > getProjectileRange()) {
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
			default:
				return false;
		}
	}

	public double getMagicSpellPower(final Npc npc) {
		return Math.max(1.0D, getMagicOffense(npc) / 12.0D);
	}

	public int getRangedOffense(final Npc npc) {
		if (!usesRangedProjectiles()) {
			return 0;
		}
		return Math.max(1, Math.max(npc.getDef().getAtt(), npc.getDef().getStr()));
	}

	public int getMagicOffense(final Npc npc) {
		if (!usesMagicProjectiles()) {
			return 0;
		}
		return Math.max(1, Math.max(npc.getDef().getAtt(), npc.getDef().getStr()));
	}

	public static NpcAttackStyleProfile forNpc(final Npc npc) {
		if (npc == null || npc.getDef() == null || npc.getDef().getName() == null) {
			return MELEE;
		}

		final String name = npc.getDef().getName().toLowerCase();
		switch (name) {
			case "darkwizard":
			case "wizard":
			case "chaos druid":
			case "druid":
			case "witch":
			case "necromancer":
			case "skeleton mage":
				return PURE_MAGIC;
			case "gnome guard":
			case "thief":
			case "rogue":
			case "head thief":
				return PURE_RANGED;
			case "battle mage":
			case "monk of zamorak":
			case "chaos druid warrior":
			case "paladin":
			case "lesser demon":
			case "greater demon":
			case "black demon":
			case "moss giant":
			case "ice giant":
			case "fire giant":
			case "delrith":
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
			case "mercenary":
			case "mercenary captain":
			case "draft mercenary guard":
			case "khazard troop":
			case "pirate":
			case "bandit":
			case "tribesman":
			case "yanille watchman":
			case "bedabin nomad guard":
			case "gnome baller":
				return MELEE_RANGED;
			default:
				return MELEE;
		}
	}
}
