package com.openrsc.server.external;

public class SkillDef {

	//Allow for different exp curves
	public enum EXP_CURVE {
		ORIGINAL
	}

	int id;

	String longName;
	String shortName;
	String displayName;

	EXP_CURVE expCurve;
	int minLevel, maxLevel;

	public SkillDef(String longName, String shortName, int minLevel, int maxLevel, EXP_CURVE curve, int id) {
		this(longName, shortName, shortName, minLevel, maxLevel, curve, id);
	}

	public SkillDef(String longName, String shortName, String displayName, int minLevel, int maxLevel, EXP_CURVE curve, int id) {
		this.longName = longName;
		this.shortName = shortName;
		this.displayName = displayName;
		this.minLevel = minLevel;
		this.maxLevel = maxLevel;
		this.expCurve = curve;
		this.id = id;
	}

	public String getLongName() { return this.longName; }
	public String getShortName() { return this.shortName; }
	public String getDisplayName() { return this.displayName; }

	public EXP_CURVE getExpCurve() { return this.expCurve; }

	public int getMinLevel() { return this.minLevel; }
	public int getMaxLevel() { return this.maxLevel; }

}
