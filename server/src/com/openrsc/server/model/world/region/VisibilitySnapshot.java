package com.openrsc.server.model.world.region;

import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.LayeredSpatialWindowKey;

import java.util.ArrayList;
import java.util.Collection;

public final class VisibilitySnapshot {
	private final Collection<Player> players;
	private final Collection<Npc> npcs;
	private final Collection<GameObject> gameObjects;
	private final Collection<GameObject> sceneryObjects;
	private final Collection<GameObject> wallObjects;
	private final Collection<GroundItem> groundItems;
	private final int mobRegionCount;
	private final int objectRegionCount;
	private final long objectSnapshotKey;
	private final long objectSnapshotVersion;
	private final LayeredSpatialWindowKey layeredObjectSnapshotKey;

	public VisibilitySnapshot(
		final Collection<Player> players,
		final Collection<Npc> npcs,
		final Collection<GameObject> gameObjects,
		final Collection<GroundItem> groundItems,
		final int mobRegionCount,
		final int objectRegionCount) {
		this(players, npcs, gameObjects, splitGameObjects(gameObjects, 0), splitGameObjects(gameObjects, 1),
			groundItems, mobRegionCount, objectRegionCount, 0L, null, 0L);
	}

	public VisibilitySnapshot(
		final Collection<Player> players,
		final Collection<Npc> npcs,
		final Collection<GameObject> gameObjects,
		final Collection<GameObject> sceneryObjects,
		final Collection<GameObject> wallObjects,
		final Collection<GroundItem> groundItems,
		final int mobRegionCount,
		final int objectRegionCount) {
		this(players, npcs, gameObjects, sceneryObjects, wallObjects, groundItems,
			mobRegionCount, objectRegionCount, 0L, null, 0L);
	}

	public VisibilitySnapshot(
		final Collection<Player> players,
		final Collection<Npc> npcs,
		final Collection<GameObject> gameObjects,
		final Collection<GameObject> sceneryObjects,
		final Collection<GameObject> wallObjects,
		final Collection<GroundItem> groundItems,
		final int mobRegionCount,
		final int objectRegionCount,
		final long objectSnapshotKey,
		final long objectSnapshotVersion) {
		this(players, npcs, gameObjects, sceneryObjects, wallObjects,
			groundItems, mobRegionCount, objectRegionCount, objectSnapshotKey,
			null, objectSnapshotVersion);
	}

	public VisibilitySnapshot(
		final Collection<Player> players,
		final Collection<Npc> npcs,
		final Collection<GameObject> gameObjects,
		final Collection<GameObject> sceneryObjects,
		final Collection<GameObject> wallObjects,
		final Collection<GroundItem> groundItems,
		final int mobRegionCount,
		final int objectRegionCount,
		final LayeredSpatialWindowKey layeredObjectSnapshotKey,
		final long objectSnapshotVersion) {
		this(players, npcs, gameObjects, sceneryObjects, wallObjects,
			groundItems, mobRegionCount, objectRegionCount, 0L,
			layeredObjectSnapshotKey, objectSnapshotVersion);
	}

	private VisibilitySnapshot(
		final Collection<Player> players,
		final Collection<Npc> npcs,
		final Collection<GameObject> gameObjects,
		final Collection<GameObject> sceneryObjects,
		final Collection<GameObject> wallObjects,
		final Collection<GroundItem> groundItems,
		final int mobRegionCount,
		final int objectRegionCount,
		final long objectSnapshotKey,
		final LayeredSpatialWindowKey layeredObjectSnapshotKey,
		final long objectSnapshotVersion) {
		this.players = players;
		this.npcs = npcs;
		this.gameObjects = gameObjects;
		this.sceneryObjects = sceneryObjects;
		this.wallObjects = wallObjects;
		this.groundItems = groundItems;
		this.mobRegionCount = mobRegionCount;
		this.objectRegionCount = objectRegionCount;
		this.objectSnapshotKey = objectSnapshotKey;
		this.layeredObjectSnapshotKey = layeredObjectSnapshotKey;
		this.objectSnapshotVersion = objectSnapshotVersion;
	}

	private static Collection<GameObject> splitGameObjects(final Collection<GameObject> gameObjects, final int type) {
		final ArrayList<GameObject> objects = new ArrayList<>();
		for (final GameObject gameObject : gameObjects) {
			if (gameObject.getType() == type) {
				objects.add(gameObject);
			}
		}
		return objects;
	}

	public Collection<Player> getPlayers() {
		return players;
	}

	public Collection<Npc> getNpcs() {
		return npcs;
	}

	public Collection<GameObject> getGameObjects() {
		return gameObjects;
	}

	public Collection<GameObject> getSceneryObjects() {
		return sceneryObjects;
	}

	public Collection<GameObject> getWallObjects() {
		return wallObjects;
	}

	public Collection<GroundItem> getGroundItems() {
		return groundItems;
	}

	public int getSceneryCount() {
		return sceneryObjects.size();
	}

	public int getWallObjectCount() {
		return wallObjects.size();
	}

	public int getMobRegionCount() {
		return mobRegionCount;
	}

	public int getObjectRegionCount() {
		return objectRegionCount;
	}

	public long getObjectSnapshotKey() {
		return layeredObjectSnapshotKey == null
			? objectSnapshotKey : layeredObjectSnapshotKey.hashCode();
	}

	public LayeredSpatialWindowKey getLayeredObjectSnapshotKey() {
		return layeredObjectSnapshotKey;
	}

	public long getObjectSnapshotVersion() {
		return objectSnapshotVersion;
	}
}
