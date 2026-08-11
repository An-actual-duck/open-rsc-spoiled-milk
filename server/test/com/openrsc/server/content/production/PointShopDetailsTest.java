package com.openrsc.server.content.production;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PointShopDetailsTest {
	@Test
	void preservesTypedMultiCurrencyCostsAndRefreshesOnlyLiveFields() {
		PointShopDetails details = new PointShopDetails(
			new int[] {0, 1}, new int[] {20, 7},
			new int[][] {{0, 1}, {1}}, new int[][] {{3, 2}, {6}}, new int[] {10, 4});
		assertArrayEquals(new int[] {0, 1}, details.getRecipeCostCodes()[0]);
		assertArrayEquals(new int[] {3, 2}, details.getRecipeCostAmounts()[0]);
		details.refresh(new int[] {17, 5}, new int[] {9, 4});
		assertArrayEquals(new int[] {17, 5}, details.getBalances());
		assertArrayEquals(new int[] {9, 4}, details.getRecipeStock());
		assertArrayEquals(new int[] {3, 2}, details.getRecipeCostAmounts()[0]);
	}

	@Test
	void rejectsMalformedTypedCurrencyPayloads() {
		assertThrows(IllegalArgumentException.class, () -> new PointShopDetails(
			new int[] {0}, new int[] {1, 2}, new int[0][], new int[0][], new int[0]));
		assertThrows(IllegalArgumentException.class, () -> new PointShopDetails(
			new int[] {0}, new int[] {1}, new int[][] {{0}}, new int[][] {{1, 2}}, new int[] {1}));
	}
}
