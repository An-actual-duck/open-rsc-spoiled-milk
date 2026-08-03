package com.openrsc.server.content.cleric;

import java.util.Arrays;

/** Immutable common metadata shared by later Cleric transport and spell handlers. */
public final class ClericSpellDefinition {
	private final ClericSpellId id;
	private final String displayName;
	private final ClericAlignment alignment;
	private final int worshipLevel;
	private final int spellTier;
	private final int radius;
	private final boolean affectsCaster;
	private final ClericSigilCost primarySigilCost;
	private final int[] holyPowerThresholds;

	ClericSpellDefinition(ClericSpellId id, String displayName, ClericAlignment alignment,
			int worshipLevel, int spellTier, int radius, boolean affectsCaster,
			ClericSigilCost primarySigilCost, int... holyPowerThresholds) {
		if (id == null || alignment == null || primarySigilCost == null) {
			throw new IllegalArgumentException("Cleric spell identity, alignment, and cost are required");
		}
		if (displayName == null || displayName.trim().isEmpty()) {
			throw new IllegalArgumentException("Cleric spell display name is required");
		}
		if (worshipLevel < 1 || spellTier < 1 || radius < 1) {
			throw new IllegalArgumentException("Cleric spell level, tier, and radius must be positive");
		}
		validateThresholds(holyPowerThresholds);
		this.id = id;
		this.displayName = displayName;
		this.alignment = alignment;
		this.worshipLevel = worshipLevel;
		this.spellTier = spellTier;
		this.radius = radius;
		this.affectsCaster = affectsCaster;
		this.primarySigilCost = primarySigilCost;
		this.holyPowerThresholds = holyPowerThresholds.clone();
	}

	private static void validateThresholds(int[] thresholds) {
		if (thresholds == null || thresholds.length == 0 || thresholds[0] != 0) {
			throw new IllegalArgumentException("Cleric Holy Power thresholds must begin at zero");
		}
		for (int index = 1; index < thresholds.length; index++) {
			if (thresholds[index] <= thresholds[index - 1]) {
				throw new IllegalArgumentException("Cleric Holy Power thresholds must increase strictly");
			}
		}
	}

	public ClericSpellId getId() {
		return id;
	}

	public int getStableCode() {
		return id.getCode();
	}

	public String getStableKey() {
		return id.getKey();
	}

	public String getDisplayName() {
		return displayName;
	}

	public ClericAlignment getAlignment() {
		return alignment;
	}

	public int getWorshipLevel() {
		return worshipLevel;
	}

	public int getSpellTier() {
		return spellTier;
	}

	public int getRadius() {
		return radius;
	}

	public boolean affectsCaster() {
		return affectsCaster;
	}

	public ClericSigilCost getPrimarySigilCost() {
		return primarySigilCost;
	}

	public int[] getHolyPowerThresholds() {
		return holyPowerThresholds.clone();
	}

	public int getEffectRankCount() {
		return holyPowerThresholds.length;
	}

	/** Returns the one-based authored effect rank snapshotted at cast time. */
	public int resolveEffectRank(int holyPower) {
		if (holyPower < 0) {
			throw new IllegalArgumentException("Holy Power cannot be negative: " + holyPower);
		}
		int rank = 1;
		for (int index = 1; index < holyPowerThresholds.length; index++) {
			if (holyPower < holyPowerThresholds[index]) {
				break;
			}
			rank = index + 1;
		}
		return rank;
	}

	@Override
	public String toString() {
		return "ClericSpellDefinition{id=" + id + ", worshipLevel=" + worshipLevel
			+ ", spellTier=" + spellTier + ", holyPowerThresholds="
			+ Arrays.toString(holyPowerThresholds) + '}';
	}
}
