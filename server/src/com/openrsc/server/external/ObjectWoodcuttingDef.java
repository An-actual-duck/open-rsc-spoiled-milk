package com.openrsc.server.external;

import com.openrsc.server.util.rsc.Formulae;

/**
 * The definition wrapper for trees
 */
public final class ObjectWoodcuttingDef {

	/**
	 * How much experience identifying gives
	 */
	private int exp;

	/**
	 * Percent chance the tree will fall
	 */
	private int fell;

	/**
	 * The id of the log this turns into
	 */
	private int logId;

	/**
	 * Woodcut level required to cut
	 */
	private int requiredLevel;

	/**
	 * How long the tree takes to respawn afterwards
	 */
	private int respawnTime;

	// Math.random() must be less than these to succeed
	private int lowBronze;
	private int highBronze;
	private double[] rateBronze;
	private int lowIron;
	private int highIron;
	private double[] rateIron;
	private int lowSteel;
	private int highSteel;
	private double[] rateSteel;
	private int lowBlack;
	private int highBlack;
	private double[] rateBlack;
	private int lowMithril;
	private int highMithril;
	private double[] rateMithril;
	private int lowAdamantite;
	private int highAdamantite;
	private double[] rateAdamantite;
	private int lowRune;
	private int highRune;
	private double[] rateRune;
	private int lowDragon;
	private int highDragon;
	private double[] rateDragon;

	public int getExp() {
		return exp;
	}

	public int getFell() {
		return fell;
	}

	public int getLogId() {
		return logId;
	}

	public int getReqLevel() {
		return requiredLevel;
	}

	public int getRespawnTime() {
		return respawnTime;
	}

	public double getRate(int level, int axe) {
		switch (axe) {
			default:
			case 2047:
				return rateBronze[level];
			case 2048:
				return rateIron[level];
			case 87:
				return rateSteel[level];
			case 428:
				return rateBlack[level];
			case 12:
				return rateMithril[level];
			case 88:
				return rateAdamantite[level];
			case 203:
				return rateRune[level];
			case 2023:
				return getExtendedRate(level, 1);
			case 204:
				return getExtendedRate(level, 2);
			case 2034:
				return getExtendedRate(level, 3);
			case 405:
				return getExtendedRate(level, 4);
			case 1480:
				return getExtendedRate(level, 5);
		}
	}

	private double getExtendedRate(int level, int step) {
		double base = rateRune[level];
		double diff = rateDragon[level] - rateRune[level];
		return Math.max(0.0D, Math.min(1.0D, base + (diff * step)));
	}

	public void calculateWoodRates() {
		final int maxLevelToCalcFor = 143;

		rateBronze = new double[maxLevelToCalcFor];
		for (int level = 0; level < maxLevelToCalcFor; level++) {
			if (level >= requiredLevel) {
				rateBronze[level] = Formulae.interp(lowBronze, highBronze, level);
			}
		}
		rateIron = new double[maxLevelToCalcFor];
		for (int level = 0; level < maxLevelToCalcFor; level++) {
			if (level >= requiredLevel) {
				rateIron[level] = Formulae.interp(lowIron, highIron, level);
			}
		}
		rateSteel = new double[maxLevelToCalcFor];
		for (int level = 0; level < maxLevelToCalcFor; level++) {
			if (level >= requiredLevel) {
				rateSteel[level] = Formulae.interp(lowSteel, highSteel, level);
			}
		}
		rateBlack = new double[maxLevelToCalcFor];
		for (int level = 0; level < maxLevelToCalcFor; level++) {
			if (level >= requiredLevel) {
				rateBlack[level] = Formulae.interp(lowBlack, highBlack, level);
			}
		}
		rateMithril = new double[maxLevelToCalcFor];
		for (int level = 0; level < maxLevelToCalcFor; level++) {
			if (level >= requiredLevel) {
				rateMithril[level] = Formulae.interp(lowMithril, highMithril, level);
			}
		}
		rateAdamantite = new double[maxLevelToCalcFor];
		for (int level = 0; level < maxLevelToCalcFor; level++) {
			if (level >= requiredLevel) {
				rateAdamantite[level] = Formulae.interp(lowAdamantite, highAdamantite, level);
			}
		}
		rateRune = new double[maxLevelToCalcFor];
		for (int level = 0; level < maxLevelToCalcFor; level++) {
			if (level >= requiredLevel) {
				rateRune[level] = Formulae.interp(lowRune, highRune, level);
			}
		}
		rateDragon = new double[maxLevelToCalcFor];
		for (int level = 0; level < maxLevelToCalcFor; level++) {
			if (level >= requiredLevel) {
				rateDragon[level] = Formulae.interp(lowDragon, highDragon, level);
			}
		}
	}
}
