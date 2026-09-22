package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.external.SpellDef;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.net.rsc.enums.OpcodeIn;

import java.util.Map.Entry;

/** Stable, side-effect-free classification and presentation metadata for server spells. */
final class SpellClassification {
	private SpellClassification() {
	}

	static boolean isMobCastOpcode(final OpcodeIn opcode) {
		return opcode == OpcodeIn.CAST_ON_NPC || opcode == OpcodeIn.PLAYER_CAST_PVP;
	}

	static boolean doesRingMatchSpellElement(final int ringId, final Spells spellEnum) {
		if (spellEnum == null) {
			return false;
		}
		if (ringId >= 1673 && ringId <= 1677) {
			return isAirSpell(spellEnum);
		}
		if (ringId >= 1678 && ringId <= 1682) {
			return isWaterSpell(spellEnum);
		}
		if (ringId >= 1683 && ringId <= 1687) {
			return isEarthSpell(spellEnum);
		}
		if (ringId >= 1688 && ringId <= 1692) {
			return isFireSpell(spellEnum);
		}
		return false;
	}

	static boolean isAirSpell(final Spells spellEnum) {
		return spellEnum == Spells.WIND_STRIKE
			|| spellEnum == Spells.WIND_BOLT
			|| spellEnum == Spells.WIND_BOLT_R
			|| spellEnum == Spells.WIND_BLAST
			|| spellEnum == Spells.WIND_WAVE;
	}

	static boolean isWaterSpell(final Spells spellEnum) {
		return spellEnum == Spells.WATER_STRIKE
			|| spellEnum == Spells.WATER_BOLT
			|| spellEnum == Spells.WATER_BLAST
			|| spellEnum == Spells.WATER_WAVE;
	}

	static boolean isEarthSpell(final Spells spellEnum) {
		return spellEnum == Spells.EARTH_STRIKE
			|| spellEnum == Spells.EARTH_BOLT
			|| spellEnum == Spells.EARTH_BLAST
			|| spellEnum == Spells.EARTH_WAVE;
	}

	static boolean isFireSpell(final Spells spellEnum) {
		return spellEnum == Spells.FIRE_STRIKE
			|| spellEnum == Spells.FIRE_BOLT
			|| spellEnum == Spells.FIRE_BLAST
			|| spellEnum == Spells.FIRE_WAVE;
	}

	static boolean isMindSpell(final Spells spellEnum) {
		return spellEnum == Spells.WIND_STRIKE
			|| spellEnum == Spells.WATER_STRIKE
			|| spellEnum == Spells.EARTH_STRIKE
			|| spellEnum == Spells.FIRE_STRIKE
			|| spellEnum == Spells.THUNDER_BALL
			|| spellEnum == Spells.ICICLE_SHOT
			|| spellEnum == Spells.ACID_DROP
			|| spellEnum == Spells.BRANCH_SPORE;
	}

	static boolean isThunderSpell(final Spells spellEnum) {
		return spellEnum == Spells.THUNDER_BALL
			|| spellEnum == Spells.THUNDER_SPLASH
			|| spellEnum == Spells.THUNDER_STRIKE;
	}

	static boolean isAcidSpell(final Spells spellEnum) {
		return spellEnum == Spells.ACID_DROP
			|| spellEnum == Spells.ACID_FROG
			|| spellEnum == Spells.ACID_GUSH;
	}

	static boolean isIceSpell(final Spells spellEnum) {
		return spellEnum == Spells.ICICLE_SHOT
			|| spellEnum == Spells.ICE_BURST
			|| spellEnum == Spells.ICE_CRYSTAL;
	}

	static com.openrsc.server.model.entity.npc.NpcMagicElement getResistanceElement(final Spells spell) {
		if (isIceSpell(spell)) return com.openrsc.server.model.entity.npc.NpcMagicElement.ICE;
		if (isFireSpell(spell)) return com.openrsc.server.model.entity.npc.NpcMagicElement.FIRE;
		return com.openrsc.server.model.entity.npc.NpcMagicElement.NONE;
	}

	static boolean isWoodSpell(final Spells spellEnum) {
		return spellEnum == Spells.BRANCH_SPORE
			|| spellEnum == Spells.WOOD_DRILL
			|| spellEnum == Spells.BATTERING_RAM;
	}

	static int getSpellProjectileVisual(final Spells spellEnum) {
		if (spellEnum == null) {
			return Projectile.MAGIC;
		}
		switch (spellEnum) {
			case WIND_STRIKE:
				return Projectile.WIND_ARROW;
			case EARTH_STRIKE:
				return Projectile.ROCK_THROW;
			case WATER_STRIKE:
				return Projectile.WATER_BALL;
			case FIRE_STRIKE:
				return Projectile.FIREBALL;
			case THUNDER_BALL:
				return Projectile.THUNDER_BALL;
			case ICICLE_SHOT:
				return Projectile.ICICLE_SHOT;
			case ACID_DROP:
				return Projectile.ACID_DROP;
			case BRANCH_SPORE:
				return Projectile.BRANCH_SPORE;
			case WIND_BOLT:
				return Projectile.WIND_STATIC_2;
			case WATER_BOLT:
				return Projectile.WATER_STATIC_2;
			case EARTH_BOLT:
				return Projectile.EARTH_LEAD_2;
			case FIRE_BOLT:
				return Projectile.FIRE_LEAD_2;
			case THUNDER_SPLASH:
				return Projectile.THUNDER_BIRD;
			case ICE_BURST:
				return Projectile.ICE_LEAD_2;
			case ACID_FROG:
				return Projectile.ACID_LEAD_2;
			case WOOD_DRILL:
				return Projectile.WOOD_LEAD_2;
			default:
				return Projectile.MAGIC;
		}
	}

	static int getSpellImpactEffect(final Spells spellEnum) {
		if (spellEnum == null) {
			return 0;
		}
		switch (spellEnum) {
			case EARTH_BOLT:
				return CombatEffect.EARTH_HAMMER;
			case FIRE_BOLT:
				return CombatEffect.FIRE_CLAW;
			case THUNDER_SPLASH:
				return CombatEffect.THUNDER_SPLASH;
			case ICE_BURST:
				return CombatEffect.ICE_BURST;
			case ACID_FROG:
				return CombatEffect.ACID_FROG;
			case WOOD_DRILL:
				return CombatEffect.WOOD_DRILL;
			case WIND_BLAST:
				return CombatEffect.TORNADO;
			case EARTH_BLAST:
				return CombatEffect.EARTH_BURST;
			case WATER_BLAST:
				return CombatEffect.WATER_ERUPTION;
			case FIRE_BLAST:
				return CombatEffect.EXPLOSION;
			case THUNDER_STRIKE:
				return CombatEffect.THUNDER_STRIKE;
			case ICE_CRYSTAL:
				return CombatEffect.ICE_CRYSTAL;
			case ACID_GUSH:
				return CombatEffect.ACID_GUSH;
			case BATTERING_RAM:
				return CombatEffect.BATTERING_RAM;
			case WIND_WAVE:
				return CombatEffect.WIND_BEAM;
			case EARTH_WAVE:
				return CombatEffect.EARTH_IMPALE;
			case WATER_WAVE:
				return CombatEffect.WATER_VORTEX;
			case FIRE_WAVE:
				return CombatEffect.FIRE_PILLAR;
			default:
				return 0;
		}
	}

	static boolean shouldShowSpellProjectile(final Spells spellEnum, final int impactEffect) {
		if (impactEffect <= 0) {
			return true;
		}
		return spellEnum == Spells.EARTH_BOLT
			|| spellEnum == Spells.FIRE_BOLT
			|| spellEnum == Spells.THUNDER_SPLASH
			|| spellEnum == Spells.ICE_BURST
			|| spellEnum == Spells.ACID_FROG
			|| spellEnum == Spells.WOOD_DRILL;
	}

	static int getGodSpellProjectileVisual(final Spells spellEnum) {
		return Projectile.HOLY_MAGIC;
	}

	static int getGodSpellImpactEffect(final Spells spellEnum) {
		if (spellEnum == Spells.CLAWS_OF_GUTHIX) {
			return CombatEffect.EYE_OF_GUTHIX;
		}
		if (spellEnum == Spells.CLAW_OF_GUTHIX) {
			return CombatEffect.CLAW_OF_GUTHIX;
		}
		if (spellEnum == Spells.SARADOMIN_STRIKE) {
			return CombatEffect.SARADOMIN_STRIKE;
		}
		if (spellEnum == Spells.SARADOMIN_SOUL_SLASH) {
			return CombatEffect.SARADOMIN_SOUL_SLASH;
		}
		if (spellEnum == Spells.FLAMES_OF_ZAMORAK) {
			return CombatEffect.ZAMORAKS_VOID;
		}
		if (spellEnum == Spells.ZAMORAKS_APOCOLYPSE) {
			return CombatEffect.ZAMORAKS_APOCOLYPSE;
		}
		return 0;
	}

	static int getHealCombatEffect(final Spells spellEnum) {
		if (spellEnum == Spells.WEAK_HEAL) {
			return CombatEffect.LESSER_HEAL;
		}
		if (spellEnum == Spells.STRONG_HEAL) {
			return CombatEffect.GREATER_HEAL;
		}
		return 0;
	}

	static boolean isGuthixGodSpell(final Spells spellEnum) {
		return spellEnum == Spells.CLAWS_OF_GUTHIX || spellEnum == Spells.CLAW_OF_GUTHIX;
	}

	static boolean isSaradominGodSpell(final Spells spellEnum) {
		return spellEnum == Spells.SARADOMIN_STRIKE || spellEnum == Spells.SARADOMIN_SOUL_SLASH;
	}

	static boolean isZamorakGodSpell(final Spells spellEnum) {
		return spellEnum == Spells.FLAMES_OF_ZAMORAK || spellEnum == Spells.ZAMORAKS_APOCOLYPSE;
	}

	static boolean isAdvancedGodSpell(final Spells spellEnum) {
		return spellEnum == Spells.ZAMORAKS_APOCOLYPSE
			|| spellEnum == Spells.SARADOMIN_SOUL_SLASH
			|| spellEnum == Spells.CLAW_OF_GUTHIX;
	}

	static int getElementalSpellTier(final Spells spellEnum) {
		final double capPercent = getSpellDamageCapPercent(spellEnum);
		if (capPercent <= 0.40D) {
			return 1;
		}
		if (capPercent <= 0.60D) {
			return 2;
		}
		if (capPercent <= 0.80D) {
			return 3;
		}
		return 4;
	}

	static int getWindAccuracyDebuffPercent(final Spells spellEnum) {
		return getElementalSpellTier(spellEnum) * 5;
	}

	static int getWaterMaxHitDebuffPercent(final Spells spellEnum) {
		return getElementalSpellTier(spellEnum) * 5;
	}

	static int getEarthAttackSpeedDebuffPercent(final Spells spellEnum) {
		return getElementalSpellTier(spellEnum) * 3;
	}

	static int getFireDefenseDebuffPercent(final Spells spellEnum) {
		return getElementalSpellTier(spellEnum) * 3;
	}

	static int getDualElementProcChancePercent(final Spells spellEnum) {
		switch (getElementalSpellTier(spellEnum)) {
			case 1:
				return 7;
			case 2:
				return 15;
			case 3:
				return 25;
			default:
				return 0;
		}
	}

	static int getStartleProcChancePercent(final Spells spellEnum) {
		return isThunderSpell(spellEnum) ? getDualElementProcChancePercent(spellEnum) : 0;
	}

	static int getAcidPoisonPower(final Spells spellEnum) {
		return isAcidSpell(spellEnum) ? 10 + (getElementalSpellTier(spellEnum) * 10) : 0;
	}

	static int getFrostbiteProcChancePercent(final Spells spellEnum) {
		return isIceSpell(spellEnum) ? getDualElementProcChancePercent(spellEnum) : 0;
	}

	static int getSplinterProcChancePercent(final Spells spellEnum) {
		return isWoodSpell(spellEnum) ? getDualElementProcChancePercent(spellEnum) : 0;
	}

	static boolean isBloodSpell(final SpellDef spell) {
		if (spell == null) {
			return false;
		}
		for (Entry<Integer, Integer> rune : spell.getRunesRequired()) {
			if (rune.getKey() == ItemId.BLOOD_RUNE.id()) {
				return true;
			}
		}
		return false;
	}

	static double getSpellDamageCapPercent(final Spells spellEnum) {
		if (spellEnum == null) {
			return 1.0D;
		}
		switch (spellEnum) {
			case WIND_STRIKE:
			case WATER_STRIKE:
			case EARTH_STRIKE:
			case FIRE_STRIKE:
			case THUNDER_BALL:
			case ICICLE_SHOT:
			case ACID_DROP:
			case BRANCH_SPORE:
				return 0.40D;
			case WIND_BOLT:
			case WATER_BOLT:
			case EARTH_BOLT:
			case FIRE_BOLT:
			case THUNDER_SPLASH:
			case ICE_BURST:
			case ACID_FROG:
			case WOOD_DRILL:
				return 0.60D;
			case WIND_BLAST:
			case WATER_BLAST:
			case EARTH_BLAST:
			case FIRE_BLAST:
			case IBAN_BLAST:
			case THUNDER_STRIKE:
			case ICE_CRYSTAL:
			case ACID_GUSH:
			case BATTERING_RAM:
				return 0.80D;
			case WIND_WAVE:
			case WATER_WAVE:
			case EARTH_WAVE:
			case FIRE_WAVE:
				return 1.0D;
			default:
				return 1.0D;
		}
	}

	static boolean isBoostSpell(Spells spellEnum) {
		return spellEnum == Spells.THICK_SKIN || spellEnum == Spells.BURST_OF_STRENGTH
			|| spellEnum == Spells.CAMOFLAUGE || spellEnum == Spells.ROCK_SKIN;
	}

	static boolean isHealSpell(Spells spellEnum) {
		return spellEnum == Spells.WEAK_HEAL
			|| spellEnum == Spells.STRONG_HEAL;
	}

	static boolean isTeleportSpell(Spells spellEnum) {
		return spellEnum == Spells.VARROCK_TELEPORT
			|| spellEnum == Spells.LUMBRIDGE_TELEPORT
			|| spellEnum == Spells.FALADOR_TELEPORT
			|| spellEnum == Spells.CAMELOT_TELEPORT
			|| spellEnum == Spells.ARDOUGNE_TELEPORT
			|| spellEnum == Spells.WATCHTOWER_TELEPORT;
	}

}
