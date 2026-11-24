package com.openrsc.server.external;

/**
 * The definition wrapper for harvesting objects
 */
public final class ObjectHarvestingDef {

	/**
	 * Harvesting experience per unit of produce
	 */
	private int exp;

	/**
	 * Percent chance the object will become exhausted
	 */
	private int exhaust;

	/**
	 * The id of the produce obtained
	 */
	private int prodId;

	/** Minimum equipped shears tier for this resource. */
	private int minimumShearsTier;

	/** Harvesting level used as the baseline for scaling yield. */
	private int yieldBaselineLevel;

	/**
	 * How long the object takes to respawn afterwards
	 */
	private int respawnTime;

	public int getExp() {
		return exp;
	}

	public int getExhaust() {
		return exhaust;
	}

	public int getProdId() {
		return prodId;
	}

	public int getMinimumShearsTier() {
		return minimumShearsTier;
	}

	public int getYieldBaselineLevel() {
		return yieldBaselineLevel;
	}

	public int getRespawnTime() {
		return respawnTime;
	}

}
