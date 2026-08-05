package com.openrsc.server.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoundryDragonSmeltingCostTest {
	@Test
	void everyCoalTierUsesFiveFireAndOneNatureRunePerCoal() {
		for (int coal = 1; coal <= 6; coal++) {
			assertEquals(coal * 5, FoundryDragonSmeltingCost.fireRunesForCoal(coal));
			assertEquals(coal, FoundryDragonSmeltingCost.natureRunesForCoal(coal));
		}
	}

	@Test
	void zeroCoalHasNoReplacementCost() {
		assertEquals(0, FoundryDragonSmeltingCost.fireRunesForCoal(0));
		assertEquals(0, FoundryDragonSmeltingCost.natureRunesForCoal(0));
	}

	@Test
	void invalidOrOverflowingCostsFailInsteadOfWrapping() {
		assertThrows(IllegalArgumentException.class,
			() -> FoundryDragonSmeltingCost.fireRunesForCoal(-1));
		assertThrows(ArithmeticException.class,
			() -> FoundryDragonSmeltingCost.fireRunesForCoal(Integer.MAX_VALUE));
	}
}
