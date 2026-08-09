package com.openrsc.server.config;

/** Immutable selected-content profile snapshot; it neither discovers nor activates plugins. */
public final class ContentConfiguration {
	private final boolean myWorld, customQuests, customSprites, customUi, pvp;
	public ContentConfiguration(final boolean myWorld, final boolean customQuests,
			final boolean customSprites, final boolean customUi, final boolean pvp) {
		this.myWorld = myWorld; this.customQuests = customQuests;
		this.customSprites = customSprites; this.customUi = customUi; this.pvp = pvp;
	}
	public boolean isMyWorld() { return myWorld; } public boolean isCustomQuests() { return customQuests; }
	public boolean isCustomSprites() { return customSprites; } public boolean isCustomUi() { return customUi; }
	public boolean isPvp() { return pvp; }
}
