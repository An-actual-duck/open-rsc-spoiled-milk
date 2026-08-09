package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.entity.player.Player;

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

	/**
	 * The Foundry Dragon only replaces coal with Fire and Nature runes. Callers
	 * must verify the full pre-mitigation amount exists before using this method.
	 */
	public static int costAfterRunePreservation(final Player player, final int runeId, final int amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("Rune amount cannot be negative");
		}
		if (runeId != ItemId.FIRE_RUNE.id() && runeId != ItemId.NATURE_RUNE.id()) {
			throw new IllegalArgumentException("Foundry Dragon only preserves Fire and Nature runes");
		}
		return RuneCostPreservation.shouldPreserve(player, runeId) ? 0 : amount;
	}

	private static int scaledRuneCost(final int coalAmount, final int runesPerCoal) {
		if (coalAmount < 0) {
			throw new IllegalArgumentException("Coal amount cannot be negative");
		}
		return Math.multiplyExact(coalAmount, runesPerCoal);
	}
}
