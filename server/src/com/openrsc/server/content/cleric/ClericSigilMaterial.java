package com.openrsc.server.content.cleric;

/** Confirmed primary-sigil materials in the two-tier launch scope. */
public enum ClericSigilMaterial {
	STONE("stone"),
	SILVER("silver");

	private final String key;

	ClericSigilMaterial(String key) {
		this.key = key;
	}

	public String getKey() {
		return key;
	}
}
