package com.openrsc.server.content.cleric;

import java.util.Objects;

/** Immutable launch primary-sigil vector; it does not perform Devotion accounting. */
public final class ClericSigilCost {
	private final int stone;
	private final int silver;

	private ClericSigilCost(int stone, int silver) {
		if (stone < 0 || silver < 0 || stone + silver <= 0) {
			throw new IllegalArgumentException("Cleric sigil counts must form a positive vector");
		}
		this.stone = stone;
		this.silver = silver;
	}

	public static ClericSigilCost forLaunchTier(int spellTier) {
		switch (spellTier) {
			case 1:
				return new ClericSigilCost(1, 0);
			case 2:
				return new ClericSigilCost(2, 1);
			default:
				throw new IllegalArgumentException("Unsupported Cleric launch spell tier: " + spellTier);
		}
	}

	public int getCount(ClericSigilMaterial material) {
		if (material == null) {
			throw new IllegalArgumentException("Cleric sigil material is required");
		}
		switch (material) {
			case STONE:
				return stone;
			case SILVER:
				return silver;
			default:
				throw new IllegalArgumentException("Unsupported Cleric sigil material: " + material);
		}
	}

	public int getTotalCount() {
		return Math.addExact(stone, silver);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ClericSigilCost)) {
			return false;
		}
		ClericSigilCost cost = (ClericSigilCost) other;
		return stone == cost.stone && silver == cost.silver;
	}

	@Override
	public int hashCode() {
		return Objects.hash(stone, silver);
	}

	@Override
	public String toString() {
		return "ClericSigilCost{stone=" + stone + ", silver=" + silver + '}';
	}
}
