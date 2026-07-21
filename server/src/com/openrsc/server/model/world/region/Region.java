package com.openrsc.server.model.world.region;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.openrsc.server.constants.Constants;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.Entity;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GameObjectType;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredProvenanceObservation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;

public class Region {
	private static final Logger LOGGER = LogManager.getLogger();

	/**
	 * The RegionManager this Region belongs to
	 */
	private final RegionManager regionManager;
	/**
	 * A list of players in this region.
	 */
	final private Multimap<Point, Player> players = Multimaps.synchronizedMultimap(LinkedHashMultimap.create());

	/**
	 * A list of NPCs in this region.
	 */
	final private Multimap<Point, Npc> npcs = Multimaps.synchronizedMultimap(LinkedHashMultimap.create());

	/**
	 * A list of objects in this region.
	 */
	final private Multimap<Point, GameObject> objects = Multimaps.synchronizedMultimap(LinkedHashMultimap.create());

	/**
	 * A list of objects in this region.
	 */
	final private Multimap<Point, GroundItem> items = Multimaps.synchronizedMultimap(LinkedHashMultimap.create());

	/**
	 * A list of tiles in this region.
	 */
	private volatile TileValue[][] tiles;

	/**
	 * The constant tile value used for this region.
	 */
	private volatile TileValue tile;

	/**
	 * The X index of this region
	 */
	private final int regionX;

	/**
	 * The Y index of this region
	 */
	private final int regionY;

	/**
	 * This constructor is used to create a blank region
	 *
	 * @param regionManager
	 * @param regionX
	 * @param regionY
	 */
	public Region(final RegionManager regionManager, final int regionX, final int regionY) {
		this.regionManager = regionManager;
		this.regionX = regionX;
		this.regionY = regionY;

		this.tiles = new TileValue[Constants.REGION_SIZE][Constants.REGION_SIZE];
		this.tile = null;

		for (int i = 0; i < Constants.REGION_SIZE; i++) {
			for (int j = 0; j < Constants.REGION_SIZE; j++) {
				tiles[i][j] = new TileValue();
			}
		}
	}

	public void unload() {
		players.clear();
		npcs.clear();
		objects.clear();
		items.clear();
		tiles = null;
		tile = null;
	}

	/**
	 * Captures entity counts and tile-storage presence without exposing the
	 * collections or their contents. This is read-only retirement evidence.
	 */
	RetirementContentsSnapshot captureRetirementContentsSnapshot() {
		synchronized (players) {
			synchronized (npcs) {
				synchronized (objects) {
					synchronized (items) {
						int dynamicObjectCount = 0;
						for (GameObject object : objects.values()) {
							if (object.getAuthoredPlacementIdentity() == null) {
								dynamicObjectCount++;
							}
						}
						return new RetirementContentsSnapshot(
							tiles != null || tile != null,
							players.size(), npcs.size(), objects.size(), items.size(),
							dynamicObjectCount, countCollisionProductTiles());
					}
				}
			}
		}
	}

	/**
	 * Counts tiles with current collision products without copying or exposing a
	 * TileValue. Mutation ownership is not captured, so callers must classify
	 * this count as partial evidence.
	 */
	private int countCollisionProductTiles() {
		TileValue shared = tile;
		if (shared != null) {
			return shared.hasCollisionProductState()
				? Constants.REGION_SIZE * Constants.REGION_SIZE : 0;
		}
		TileValue[][] expanded = tiles;
		if (expanded == null) {
			return -1;
		}
		int count = 0;
		for (int x = 0; x < Constants.REGION_SIZE; x++) {
			for (int y = 0; y < Constants.REGION_SIZE; y++) {
				TileValue value = expanded[x][y];
				if (value != null && value.hasCollisionProductState()) {
					count++;
				}
			}
		}
		return count;
	}

	/** Records detached authored identity metadata for active objects/items. */
	void recordAuthoredProvenance(
		final LayeredPackedRegionAuthoredProvenanceObservation.Builder builder) {
		if (builder == null) {
			throw new NullPointerException("builder");
		}
		synchronized (objects) {
			for (GameObject object : objects.values()) {
				if (object.getAuthoredPlacementIdentity() != null) {
					builder.recordRuntimeInstance(
						object.getAuthoredPlacementIdentity(), object.getID(),
						regionX, regionY, true);
				}
			}
		}
		synchronized (items) {
			for (GroundItem item : items.values()) {
				if (item.getAuthoredPlacementIdentity() != null) {
					builder.recordRuntimeInstance(
						item.getAuthoredPlacementIdentity(), item.getID(),
						regionX, regionY, true);
				}
			}
		}
	}

	/** Immutable Region-local counts; never an entity or tile handle. */
	static final class RetirementContentsSnapshot {
		private final boolean tileStorageAvailable;
		private final int playerCount;
		private final int npcCount;
		private final int objectCount;
		private final int groundItemCount;
		private final int dynamicObjectCount;
		private final int collisionProductTileCount;

		private RetirementContentsSnapshot(
			final boolean tileStorageAvailable,
			final int playerCount,
			final int npcCount,
			final int objectCount,
			final int groundItemCount,
			final int dynamicObjectCount,
			final int collisionProductTileCount) {
			this.tileStorageAvailable = tileStorageAvailable;
			this.playerCount = playerCount;
			this.npcCount = npcCount;
			this.objectCount = objectCount;
			this.groundItemCount = groundItemCount;
			this.dynamicObjectCount = dynamicObjectCount;
			this.collisionProductTileCount = collisionProductTileCount;
		}

		boolean isTileStorageAvailable() { return tileStorageAvailable; }
		int getPlayerCount() { return playerCount; }
		int getNpcCount() { return npcCount; }
		int getObjectCount() { return objectCount; }
		int getGroundItemCount() { return groundItemCount; }
		int getDynamicObjectCount() { return dynamicObjectCount; }
		int getCollisionProductTileCount() {
			return collisionProductTileCount;
		}
	}

	/**
	 * Gets the list of players.
	 *
	 * @return The list of players.
	 */
	public Collection<Player> getPlayers() {
		return players.values();
	}

	/**
	 * Gets the list of NPCs.
	 *
	 * @return The list of NPCs.
	 */
	protected Collection<Npc> getNpcs() {
		return npcs.values();
	}

	/**
	 * Gets the list of objects.
	 *
	 * @return The list of objects.
	 */
	public Collection<GameObject> getGameObjects() {
		return objects.values();
	}

	protected Collection<GroundItem> getGroundItems() {
		return items.values();
	}

	public void removeEntity(Entity entity) {
		removeEntity(entity.getLocation(), entity);
	}

	public void removeEntity(Point location, final Entity entity) {
		if (entity.isPlayer()) {
			players.remove(location, entity);
		} else if (entity.isNpc()) {
			npcs.remove(location, entity);
		} else if (entity instanceof GameObject) {
			objects.remove(location, entity);
			regionManager.invalidateVisibleObjectWindowCache(this);
		} else if (entity instanceof GroundItem) {
			items.remove(location, entity);
		}
	}

	public void addEntity(final Entity entity) {
		if (entity.isRemoved()) {
			return;
		}
		switch (entity.getEntityType()) {
			case PLAYER:
				players.put(entity.getLocation(), (Player) entity);
				break;
			case NPC:
				npcs.put(entity.getLocation(), (Npc) entity);
				break;
			case GAME_OBJECT:
				objects.put(entity.getLocation(), (GameObject) entity);
				regionManager.invalidateVisibleObjectWindowCache(this);
				break;
			case GROUND_ITEM:
				items.put(entity.getLocation(), (GroundItem) entity);
				break;
		}
	}

	private String stringifyEntities(String title, Multimap<Point, ? extends Entity> multimap) {
		StringBuilder sb = new StringBuilder(2000);
		sb.append(title).append(":\n");
		for (Entity entity : multimap.values()) {
			sb.append("\t").append(entity).append("\n");
		}
		return sb.toString();
	}

	public String toString() {
		return toString(true, true, true, true);
	}

	public String toString(final boolean debugPlayers, final boolean debugNpcs, final boolean debugItems, final boolean debugObjects) {
		final StringBuilder sb = new StringBuilder(2000);
		if (debugPlayers) {
			sb.append(stringifyEntities("Players", players)).append("\n");
		}
		if (debugNpcs) {
			sb.append(stringifyEntities("Npcs", npcs)).append("\n");
		}
		if (debugItems) {
			sb.append(stringifyEntities("Items", items)).append("\n");
		}
		if (debugObjects) {
			sb.append(stringifyEntities("Objects", objects)).append("\n");
		}
		return sb.toString();
	}

	private GameObject getGameObject(Point location, Entity observer, GameObjectType type, Integer direction) {
		return objects.get(location)
			.stream()
			.filter(obj -> type == null || obj.getGameObjectType() == type)
			.filter(obj -> observer == null || !obj.isInvisibleTo(observer))
			.filter(obj -> direction == null || obj.getDirection() == direction)
			.findFirst()
			.orElse(null);
	}

	public GameObject getGameObject(Point location) {
		return getGameObject(location, null, null, null);
	}

	public GameObject getGameObject(Point location, Entity entity) {
		return getGameObject(location, entity, GameObjectType.SCENERY, null);
	}

	public GameObject getWallGameObject(Point location, int direction) {
		return getGameObject(location, null, GameObjectType.BOUNDARY, direction);
	}

	public GameObject getWallGameObject(Point location, Entity entity) {
		return getGameObject(location, entity, GameObjectType.BOUNDARY, null);
	}

	public Npc getNpc(Point location, Entity observer) {
		return npcs.get(location)
			.stream()
			.filter(npc -> observer == null || !npc.isInvisibleTo(observer))
			.findFirst()
			.orElse(null);
	}

	public Player getPlayer(int x, int y, Entity observer, boolean includeSelf) {
		return players.get(new Point(x, y))
			.stream()
			.filter(player -> observer == null || !player.isInvisibleTo(observer))
			.filter(player -> observer == null || (!includeSelf || player.equals(observer)))
			.findFirst()
			.orElse(null);
	}

	public Player getPlayer(int x, int y, Entity observer) {
		return getPlayer(x, y, observer, true);
	}

	public GroundItem getItem(final int id, final Point location, final Entity observer) {
		return items.get(location)
				.stream()
				.filter(item -> id == item.getID())
				.filter(item -> observer == null || !item.isInvisibleTo(observer))
				.findFirst()
				.orElse(null);
	}

	public TileValue getTileValue(final int regionX, final int regionY) {
		TileValue shared = tile;
		TileValue[][] expanded = tiles;
		return shared != null ? shared : expanded[regionX][regionY];
	}

	/** Expands a uniform region before returning a tile that will be mutated. */
	public TileValue getMutableTileValue(final int regionX, final int regionY) {
		TileValue[][] expanded = tiles;
		if (expanded != null) {
			return expanded[regionX][regionY];
		}
		synchronized (this) {
			if (tile != null) {
				TileValue shared = tile;
				expanded = new TileValue[Constants.REGION_SIZE][Constants.REGION_SIZE];
				for (int x = 0; x < Constants.REGION_SIZE; x++) {
					for (int y = 0; y < Constants.REGION_SIZE; y++) {
						expanded[x][y] = shared.copy();
					}
				}
				tiles = expanded;
				tile = null;
			}
			return tiles[regionX][regionY];
		}
	}

	public TileValue getTileValue(final Point regionPoint) {
		return getTileValue(regionPoint.getX(), regionPoint.getY());
	}

	public RegionManager getRegionManager() {
		return regionManager;
	}

	public int getRegionX() {
		return regionX;
	}

	public int getRegionY() {
		return regionY;
	}

	public void checkRegionValues() {
		boolean allTilesEqual = true;
		final TileValue firstTile = tiles[0][0];
		for (int i = 0; i < Constants.REGION_SIZE && allTilesEqual; i++) {
			for (int j = 0; j < Constants.REGION_SIZE && allTilesEqual; j++) {
				allTilesEqual = allTilesEqual && firstTile.equals(tiles[i][j]);
			}
		}

		if (allTilesEqual) {
			tile = tiles[0][0];
			tiles = null;
		}
	}

	@Override
	public boolean equals(Object obj) {
		if(obj == this) {
			return true;
		}
		if(obj instanceof Region) {
			Region other = (Region) obj;
			return other.regionX == regionX && other.regionY == regionY;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return new Point(regionX, regionY).hashCode();
	}
}
