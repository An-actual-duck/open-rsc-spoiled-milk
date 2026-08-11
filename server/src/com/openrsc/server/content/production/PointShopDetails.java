package com.openrsc.server.content.production;

import java.util.Arrays;

/**
 * Additive presentation data for a non-inventory point shop.  The purchase
 * authority remains in the owning plugin; this only gives the custom client
 * enough information to render balances, per-item costs, and live stock.
 */
public final class PointShopDetails {
	private final int[] pointCodes;
	private int[] balances;
	private final int[][] recipeCostCodes;
	private final int[][] recipeCostAmounts;
	private int[] recipeStock;

	public PointShopDetails(int[] pointCodes, int[] balances, int[][] recipeCostCodes,
		int[][] recipeCostAmounts, int[] recipeStock) {
		if (pointCodes == null || balances == null || recipeCostCodes == null
			|| recipeCostAmounts == null || recipeStock == null
			|| pointCodes.length != balances.length || recipeCostCodes.length != recipeCostAmounts.length
			|| recipeCostCodes.length != recipeStock.length) {
			throw new IllegalArgumentException("Invalid point-shop presentation data");
		}
		this.pointCodes = pointCodes.clone();
		this.balances = balances.clone();
		this.recipeCostCodes = copy(recipeCostCodes);
		this.recipeCostAmounts = copy(recipeCostAmounts);
		this.recipeStock = recipeStock.clone();
		for (int i = 0; i < this.recipeCostCodes.length; i++) {
			if (this.recipeCostCodes[i].length != this.recipeCostAmounts[i].length) {
				throw new IllegalArgumentException("Point-shop cost arrays must match");
			}
		}
	}

	public synchronized int[] getPointCodes() { return pointCodes.clone(); }
	public synchronized int[] getBalances() { return balances.clone(); }
	public synchronized int[][] getRecipeCostCodes() { return copy(recipeCostCodes); }
	public synchronized int[][] getRecipeCostAmounts() { return copy(recipeCostAmounts); }
	public synchronized int[] getRecipeStock() { return recipeStock.clone(); }

	/** Refreshes a still-open panel after a successful redemption. */
	public synchronized void refresh(int[] newBalances, int[] newRecipeStock) {
		if (newBalances == null || newRecipeStock == null || newBalances.length != balances.length
			|| newRecipeStock.length != recipeStock.length) {
			throw new IllegalArgumentException("Invalid point-shop refresh data");
		}
		balances = newBalances.clone();
		recipeStock = newRecipeStock.clone();
	}

	private static int[][] copy(int[][] values) {
		int[][] copy = new int[values.length][];
		for (int i = 0; i < values.length; i++) copy[i] = values[i] == null ? new int[0] : values[i].clone();
		return copy;
	}
}
