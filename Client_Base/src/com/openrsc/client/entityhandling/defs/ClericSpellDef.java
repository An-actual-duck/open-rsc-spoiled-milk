package com.openrsc.client.entityhandling.defs;

/** Immutable Cleric spell metadata supplied by the authoritative server catalog. */
public final class ClericSpellDef {
	private final int stableCode;
	private final String stableKey;
	private final String name;
	private final String description;
	private final String alignment;
	private final int worshipLevel;
	private final int spellTier;
	private final int radius;
	private final boolean affectsCaster;
	private final int spellbookIconItemId;
	private final int stoneSigilItemId;
	private final int stoneSigilCount;
	private final int silverSigilItemId;
	private final int silverSigilCount;
	private final int casterIconItemId;
	private final int casterAnimationId;

	public ClericSpellDef(int stableCode, String stableKey, String name, String description,
			String alignment, int worshipLevel, int spellTier, int radius,
			boolean affectsCaster, int spellbookIconItemId, int stoneSigilItemId,
			int stoneSigilCount, int silverSigilItemId, int silverSigilCount,
			int casterIconItemId, int casterAnimationId) {
		if (stableCode < 0 || isBlank(stableKey) || isBlank(name) || isBlank(description)
				|| isBlank(alignment)) {
			throw new IllegalArgumentException("Cleric spell identity and display metadata are required");
		}
		if (worshipLevel < 1 || spellTier < 1 || radius < 1 || spellbookIconItemId < 0
				|| stoneSigilItemId < 0 || stoneSigilCount < 1 || silverSigilCount < 0
				|| (silverSigilCount > 0 && silverSigilItemId < 0)
				|| casterIconItemId < -1 || casterAnimationId < -1) {
			throw new IllegalArgumentException("Invalid Cleric spell presentation metadata");
		}
		this.stableCode = stableCode;
		this.stableKey = stableKey;
		this.name = name;
		this.description = description;
		this.alignment = alignment;
		this.worshipLevel = worshipLevel;
		this.spellTier = spellTier;
		this.radius = radius;
		this.affectsCaster = affectsCaster;
		this.spellbookIconItemId = spellbookIconItemId;
		this.stoneSigilItemId = stoneSigilItemId;
		this.stoneSigilCount = stoneSigilCount;
		this.silverSigilItemId = silverSigilItemId;
		this.silverSigilCount = silverSigilCount;
		this.casterIconItemId = casterIconItemId;
		this.casterAnimationId = casterAnimationId;
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

	public int getStableCode() { return stableCode; }
	public String getStableKey() { return stableKey; }
	public String getName() { return name; }
	public String getDescription() { return description; }
	public String getAlignment() { return alignment; }
	public int getWorshipLevel() { return worshipLevel; }
	public int getSpellTier() { return spellTier; }
	public int getRadius() { return radius; }
	public boolean affectsCaster() { return affectsCaster; }
	public int getSpellbookIconItemId() { return spellbookIconItemId; }
	public int getStoneSigilItemId() { return stoneSigilItemId; }
	public int getStoneSigilCount() { return stoneSigilCount; }
	public int getSilverSigilItemId() { return silverSigilItemId; }
	public int getSilverSigilCount() { return silverSigilCount; }
	public int getCasterIconItemId() { return casterIconItemId; }
	public int getCasterAnimationId() { return casterAnimationId; }
	public boolean hasCasterIcon() { return casterIconItemId >= 0; }
	public boolean hasCasterAnimation() { return casterAnimationId >= 0; }
}
