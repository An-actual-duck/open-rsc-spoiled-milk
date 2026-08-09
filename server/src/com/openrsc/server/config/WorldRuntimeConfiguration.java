package com.openrsc.server.config;

/** Immutable world/tick projection used only at startup composition boundaries. */
public final class WorldRuntimeConfiguration {
	private final int gameTick, walkingTick, viewDistance, objectViewDistance;
	private final int respawnX, respawnY;
	public WorldRuntimeConfiguration(final int gameTick, final int walkingTick,
			final int viewDistance, final int objectViewDistance, final int respawnX, final int respawnY) {
		this.gameTick = gameTick; this.walkingTick = walkingTick; this.viewDistance = viewDistance;
		this.objectViewDistance = objectViewDistance; this.respawnX = respawnX; this.respawnY = respawnY;
	}
	public int getGameTick() { return gameTick; } public int getWalkingTick() { return walkingTick; }
	public int getViewDistance() { return viewDistance; } public int getObjectViewDistance() { return objectViewDistance; }
	public int getRespawnX() { return respawnX; } public int getRespawnY() { return respawnY; }
}
