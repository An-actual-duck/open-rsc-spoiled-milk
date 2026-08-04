package com.openrsc.server.content.cleric;

/** Confirmed primary-sigil materials in the two-tier launch scope. */
public enum ClericSigilMaterial {
	STONE("stone", 1299, 1, 1, 5, 5),
	SILVER("silver", 383, 20, 16, 10, 10);

	private final String key;
	private final int sourceItemId;
	private final int craftingLevel;
	private final int blessingLevel;
	private final int baseCraftingExperience;
	private final int baseBlessingExperience;

	ClericSigilMaterial(String key, int sourceItemId, int craftingLevel, int blessingLevel,
			int baseCraftingExperience, int baseBlessingExperience) {
		this.key = key;
		this.sourceItemId = sourceItemId;
		this.craftingLevel = craftingLevel;
		this.blessingLevel = blessingLevel;
		this.baseCraftingExperience = baseCraftingExperience;
		this.baseBlessingExperience = baseBlessingExperience;
	}

	public String getKey() {
		return key;
	}

	public int getSourceItemId() {
		return sourceItemId;
	}

	public int getCraftingLevel() {
		return craftingLevel;
	}

	public int getBlessingLevel() {
		return blessingLevel;
	}

	/** Returns player-facing base XP before the server's fixed-point conversion. */
	public int getBaseCraftingExperience() {
		return baseCraftingExperience;
	}

	/** Returns player-facing base XP before the server's fixed-point conversion. */
	public int getBaseBlessingExperience() {
		return baseBlessingExperience;
	}
}
