package com.openrsc.server.content;

/**
 * Authoritative fuel conversion used while the Foundry Dragon support summon
 * is active.
 */
public final class FoundryDragonSmeltingCost {
	public static final int FIRE_RUNES_PER_COAL = 5;
	public static final int NATURE_RUNES_PER_COAL = 1;

	private FoundryDragonSmeltingCost() {
	}

	public static int fireRunesForCoal(final int coalAmount) {
		return scaledRuneCost(coalAmount, FIRE_RUNES_PER_COAL);
	}

	public static int natureRunesForCoal(final int coalAmount) {
		return scaledRuneCost(coalAmount, NATURE_RUNES_PER_COAL);
	}

	private static int scaledRuneCost(final int coalAmount, final int runesPerCoal) {
		if (coalAmount < 0) {
			throw new IllegalArgumentException("Coal amount cannot be negative");
		}
		return Math.multiplyExact(coalAmount, runesPerCoal);
	}
}
