package com.openrsc.server.content.cleric;

import java.util.Objects;

/** Immutable current-and-previous-grade god-sigil vector; no Devotion accounting. */
public final class ClericSigilCost {
	private final int stone;
	private final int silver;
	private final int moonsilver;

	private ClericSigilCost(int stone, int silver, int moonsilver) {
		if (stone < 0 || silver < 0 || moonsilver < 0 || stone + silver + moonsilver <= 0) {
			throw new IllegalArgumentException("Cleric sigil counts must form a positive vector");
		}
		this.stone = stone;
		this.silver = silver;
		this.moonsilver = moonsilver;
	}

	public static ClericSigilCost forLaunchTier(int spellTier) {
		switch (spellTier) {
			case 1:
				return new ClericSigilCost(1, 0, 0);
			case 2:
				return new ClericSigilCost(1, 1, 0);
            case 3: return new ClericSigilCost(0, 1, 1);
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
			case MOONSILVER:
				return moonsilver;
			default:
				throw new IllegalArgumentException("Unsupported Cleric sigil material: " + material);
		}
	}

	public int getTotalCount() {
		return Math.addExact(Math.addExact(stone, silver), moonsilver);
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
		return stone == cost.stone && silver == cost.silver && moonsilver == cost.moonsilver;
	}

	@Override
	public int hashCode() {
		return Objects.hash(stone, silver, moonsilver);
	}

	@Override
	public String toString() {
		return "ClericSigilCost{stone=" + stone + ", silver=" + silver + ", moonsilver=" + moonsilver + '}';
	}
}
