package com.openrsc.client.entityhandling.defs;

import java.util.Map.Entry;

/**
 * Shared client display metadata for the stable elemental spell indexes.
 *
 * Names remain owned by SpellDef. Classification is derived from and validated
 * against its authoritative level, type, and rune requirements so display-name
 * changes cannot alter spell behavior or presentation metadata.
 */
public final class ElementalSpellDisplayMetadata {

	private static final int FIRE_RUNE = 31;
	private static final int WATER_RUNE = 32;
	private static final int AIR_RUNE = 33;
	private static final int EARTH_RUNE = 34;
	private static final int MIND_RUNE = 35;
	private static final int DEATH_RUNE = 38;
	private static final int CHAOS_RUNE = 41;
	private static final int BLOOD_RUNE = 619;

	public enum Classification {
		AIR("Unsteady"),
		WATER("Dampen"),
		EARTH("Slow"),
		FIRE("Scorch"),
		THUNDER("Startle"),
		ICE("Frostbite"),
		ACID("Corrode"),
		WOOD("Splinter");

		private final String effectName;

		Classification(String effectName) {
			this.effectName = effectName;
		}

		public String getEffectName() {
			return effectName;
		}
	}

	private final int spellIndex;
	private final String spellName;
	private final int tier;
	private final Classification classification;
	private final double baseDamage;
	private final double damageCapPercent;
	private final String damageWord;
	private final String effectName;

	private ElementalSpellDisplayMetadata(int spellIndex, SpellDef spell, int tier,
		Classification classification) {
		this.spellIndex = spellIndex;
		this.spellName = spell.getName();
		this.tier = tier;
		this.classification = classification;
		this.baseDamage = getTierBaseDamage(tier) * (isDual(classification) ? 1.2D : 1.0D);
		this.damageCapPercent = (tier + 1) * 0.20D;
		this.damageWord = getTierDamageWord(tier);
		this.effectName = isDual(classification)
			? classification.getEffectName()
			: getTierEffectQualifier(tier) + " " + classification.getEffectName();
	}

	public static ElementalSpellDisplayMetadata resolve(int spellIndex, SpellDef spell) {
		int tier = getStableTier(spellIndex);
		if (tier == 0 || spell == null || spell.getSpellType() != 2) {
			return null;
		}

		boolean expectedDual = isStableDualIndex(spellIndex);
		int catalystRune = getTierCatalystRune(tier);
		int elementalAmount = tier;
		int catalystAmount = 0;
		int fireAmount = 0;
		int waterAmount = 0;
		int airAmount = 0;
		int earthAmount = 0;
		int distinctRunes = 0;

		for (Entry<Integer, Integer> rune : spell.getRunesRequired()) {
			distinctRunes++;
			int runeId = rune.getKey();
			int amount = rune.getValue();
			if (runeId == catalystRune) {
				catalystAmount = amount;
			} else if (runeId == FIRE_RUNE) {
				fireAmount = amount;
			} else if (runeId == WATER_RUNE) {
				waterAmount = amount;
			} else if (runeId == AIR_RUNE) {
				airAmount = amount;
			} else if (runeId == EARTH_RUNE) {
				earthAmount = amount;
			} else {
				return null;
			}
		}

		int expectedElements = expectedDual ? 2 : 1;
		if (catalystAmount != 1
			|| distinctRunes != expectedElements + 1
			|| spell.getRuneCount() != distinctRunes
			|| spell.getReqLevel() != getExpectedLevel(tier, expectedDual)) {
			return null;
		}

		int matchedElements = 0;
		matchedElements += fireAmount == elementalAmount ? 1 : 0;
		matchedElements += waterAmount == elementalAmount ? 1 : 0;
		matchedElements += airAmount == elementalAmount ? 1 : 0;
		matchedElements += earthAmount == elementalAmount ? 1 : 0;
		if (matchedElements != expectedElements) {
			return null;
		}

		Classification classification = classify(fireAmount, waterAmount, airAmount,
			earthAmount, elementalAmount, expectedDual);
		return classification == null ? null
			: new ElementalSpellDisplayMetadata(spellIndex, spell, tier, classification);
	}

	private static int getStableTier(int spellIndex) {
		if (spellIndex >= 0 && spellIndex <= 7) {
			return 1;
		}
		if (spellIndex >= 15 && spellIndex <= 22) {
			return 2;
		}
		if (spellIndex >= 26 && spellIndex <= 33) {
			return 3;
		}
		if (spellIndex >= 36 && spellIndex <= 39) {
			return 4;
		}
		return 0;
	}

	private static boolean isStableDualIndex(int spellIndex) {
		return (spellIndex >= 4 && spellIndex <= 7)
			|| (spellIndex >= 19 && spellIndex <= 22)
			|| (spellIndex >= 30 && spellIndex <= 33);
	}

	private static int getTierCatalystRune(int tier) {
		switch (tier) {
			case 1:
				return MIND_RUNE;
			case 2:
				return CHAOS_RUNE;
			case 3:
				return DEATH_RUNE;
			default:
				return BLOOD_RUNE;
		}
	}

	private static int getExpectedLevel(int tier, boolean dual) {
		if (dual) {
			return tier == 1 ? 10 : tier == 2 ? 30 : 50;
		}
		return tier == 1 ? 1 : tier == 2 ? 20 : tier == 3 ? 40 : 60;
	}

	private static Classification classify(int fire, int water, int air, int earth,
		int amount, boolean dual) {
		if (!dual) {
			if (air == amount) return Classification.AIR;
			if (water == amount) return Classification.WATER;
			if (earth == amount) return Classification.EARTH;
			if (fire == amount) return Classification.FIRE;
			return null;
		}
		if (fire == amount && water == amount) return Classification.THUNDER;
		if (air == amount && water == amount) return Classification.ICE;
		if (fire == amount && earth == amount) return Classification.ACID;
		if (earth == amount && water == amount) return Classification.WOOD;
		return null;
	}

	private static boolean isDual(Classification classification) {
		return classification == Classification.THUNDER || classification == Classification.ICE
			|| classification == Classification.ACID || classification == Classification.WOOD;
	}

	private static double getTierBaseDamage(int tier) {
		return tier == 1 ? 4.0D : tier == 2 ? 6.0D : tier == 3 ? 8.0D : 10.0D;
	}

	private static String getTierDamageWord(int tier) {
		return tier == 1 ? "minor" : tier == 2 ? "moderate" : tier == 3 ? "major" : "heavy";
	}

	private static String getTierEffectQualifier(int tier) {
    return tier == 1 ? "Weaker" : tier == 2 ? "Weak" : tier == 3 ? "Strong" : "Stronger";
	}

	public int getSpellIndex() {
		return spellIndex;
	}

	public String getSpellName() {
		return spellName;
	}

	public int getTier() {
		return tier;
	}

	public Classification getClassification() {
		return classification;
	}

	public boolean isDualElement() {
		return isDual(classification);
	}

	public double getBaseDamage() {
		return baseDamage;
	}

	public double getDamageCapPercent() {
		return damageCapPercent;
	}

	public String getDamageWord() {
		return damageWord;
	}

	public String getEffectName() {
		return effectName;
	}

	public String getGuideTooltip() {
    if (classification == Classification.THUNDER) {
      return "Deals "
          + damageWord
          + " damage. Startle staggers the opponent for 1 attack; then 5 unstaggered attacks before"
          + " reapplication.";
    }
		if (isDualElement()) {
			return "Deals " + damageWord + " damage. Can " + effectName + ".";
		}
		return "Deals " + damageWord + " damage. Applies " + effectName + ".";
	}
}
