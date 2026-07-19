package com.openrsc.server.model.world.region;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.Entity;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.coordinate.LegacyPackedRegionCoverage;
import com.openrsc.server.model.world.coordinate.LegacyPackedVisibilityCoverageComparison;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;
import com.openrsc.server.model.world.coordinate.WorldRegionWindow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RegionManager {
	private final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>> regions;
	private final ConcurrentHashMap<Long, List<Region>> visibleRegionWindowCache;
	private final ConcurrentHashMap<Long, List<GameObject>> visibleObjectWindowCache;
	private final ConcurrentHashMap<Long, Set<Long>> visibleObjectWindowKeysByRegion;
	private final ConcurrentHashMap<Long, VisibleObjectSnapshot> visibleObjectSnapshotCache;
	private final ConcurrentHashMap<Long, Set<Long>> visibleObjectSnapshotKeysByRegion;
	private final AtomicLong visibleObjectSnapshotSequence;

	private final World world;

	public RegionManager(final World world) {
		this.world = world;
		this.regions = new ConcurrentHashMap<>();
		this.visibleRegionWindowCache = new ConcurrentHashMap<>();
		this.visibleObjectWindowCache = new ConcurrentHashMap<>();
		this.visibleObjectWindowKeysByRegion = new ConcurrentHashMap<>();
		this.visibleObjectSnapshotCache = new ConcurrentHashMap<>();
		this.visibleObjectSnapshotKeysByRegion = new ConcurrentHashMap<>();
		this.visibleObjectSnapshotSequence = new AtomicLong();
	}

	public void load() {
		// TODO: The WorldLoader.loadWorld() should accept a RegionManager as an argument and place regions there.
		getWorld().getWorldLoader().loadWorld();
	}

	public void unload() {
		for (final ConcurrentHashMap<Integer, Region> yRegionList : regions.values()) {
			for (final Region region : yRegionList.values()) {
				region.unload();
			}
		}
		regions.clear();
		visibleRegionWindowCache.clear();
		visibleObjectWindowCache.clear();
		visibleObjectWindowKeysByRegion.clear();
		visibleObjectSnapshotCache.clear();
		visibleObjectSnapshotKeysByRegion.clear();
	}

	/**
	 * Gets the local players around an entity.
	 *
	 * @param entity The entity.
	 * @return The collection of local players.
	 */
	public Collection<Player> getLocalPlayers(final Entity entity) {
		final LinkedHashSet<Player> localPlayers = new LinkedHashSet<Player>();
		for (final Region region : getVisibleRegionWindow(entity.getLocation())) {
			for (final Player player : region.getPlayers()) {
				if (player.withinRange(entity)) {
					localPlayers.add(player);
				}
			}
		}
		return localPlayers;
	}

	public boolean hasLocalPlayers(final Entity entity) {
		for (final Region region : getVisibleRegionWindow(entity.getLocation())) {
			for (final Player player : region.getPlayers()) {
				if (player.withinRange(entity)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Gets the local NPCs around an entity.
	 *
	 * @param entity The entity.
	 * @return The collection of local NPCs.
	 */
	public Collection<Npc> getLocalNpcs(final Entity entity) {
		final LinkedHashSet<Npc> localNpcs = new LinkedHashSet<>();
		for (final Region region : getVisibleRegionWindow(entity.getLocation())) {
			for (final Npc npc : region.getNpcs()) {
				if (npc.withinRange(entity)) {
					localNpcs.add(npc);
				}
			}
		}
		return localNpcs;
	}

	public Collection<GameObject> getLocalObjects(final Mob entity) {
		LinkedHashSet<GameObject> localObjects = new LinkedHashSet<GameObject>();
		for (final Iterator<Region> region = getVisibleRegionWindow(entity.getLocation(), getWorld().getServer().getConfig().OBJECT_VIEW_DISTANCE).iterator(); region.hasNext(); ) {
			Collection<GameObject> objects = region.next().getGameObjects();
			synchronized (objects) {
				for (final Iterator<GameObject> o = objects.iterator(); o.hasNext(); ) {
					final GameObject gameObject = o.next();
					if (gameObject
						.getLocation()
						.withinGridRange(
							entity.getLocation(),
							getWorld().getServer().getConfig().OBJECT_VIEW_DISTANCE
						)
					) {
						localObjects.add(gameObject);
					}
				}
			}
		}
		return localObjects;
	}

	public Collection<GroundItem> getLocalGroundItems(final Mob entity) {
		final LinkedHashSet<GroundItem> localItems = new LinkedHashSet<GroundItem>();
		for (final Region region : getVisibleRegionWindow(entity.getLocation(), getWorld().getServer().getConfig().OBJECT_VIEW_DISTANCE)) {
			for (final GroundItem o : region.getGroundItems()) {
				if (o.getLocation().withinGridRange(entity.getLocation(), getWorld().getServer().getConfig().OBJECT_VIEW_DISTANCE)) {
					localItems.add(o);
				}
			}
		}
		return localItems;
	}

	public VisibilitySnapshot buildVisibilitySnapshot(final Mob entity) {
		final LinkedHashSet<Player> localPlayers = new LinkedHashSet<>();
		final LinkedHashSet<Npc> localNpcs = new LinkedHashSet<>();
		final LinkedHashSet<GroundItem> localItems = new LinkedHashSet<>();

		final List<Region> mobRegions = getVisibleRegionWindow(entity.getLocation());
		for (final Region region : mobRegions) {
			for (final Player player : region.getPlayers()) {
				if (player.withinRange(entity)) {
					localPlayers.add(player);
				}
			}
			for (final Npc npc : region.getNpcs()) {
				if (npc.withinRange(entity)) {
					localNpcs.add(npc);
				}
			}
		}

		final List<Region> objectRegions = getVisibleRegionWindow(entity.getLocation(), getWorld().getServer().getConfig().OBJECT_VIEW_DISTANCE);
		final VisibleObjectSnapshot visibleObjects = getVisibleObjectSnapshot(entity.getLocation(), objectRegions);
		for (final Region region : objectRegions) {
			for (final GroundItem groundItem : region.getGroundItems()) {
				if (groundItem.getLocation().withinGridRange(entity.getLocation(), getWorld().getServer().getConfig().OBJECT_VIEW_DISTANCE)) {
					localItems.add(groundItem);
				}
			}
		}

		return new VisibilitySnapshot(
			localPlayers,
			localNpcs,
			visibleObjects.gameObjects,
			visibleObjects.sceneryObjects,
			visibleObjects.wallObjects,
			localItems,
			mobRegions.size(),
			objectRegions.size(),
			visibleObjects.cacheKey,
			visibleObjects.version);
	}

	public void invalidateVisibleObjectWindowCache() {
		final int entriesCleared = visibleObjectWindowCache.size() + visibleObjectSnapshotCache.size();
		visibleObjectWindowCache.clear();
		visibleObjectWindowKeysByRegion.clear();
		visibleObjectSnapshotCache.clear();
		visibleObjectSnapshotKeysByRegion.clear();
		getWorld().getServer().recordVisibilityObjectCacheClear(entriesCleared);
	}

	public void invalidateVisibleObjectWindowCache(final Region changedRegion) {
		final long regionKey = packRegionCoordinateKey(changedRegion.getRegionX(), changedRegion.getRegionY());
		final Set<Long> affectedWindowKeys = visibleObjectWindowKeysByRegion.remove(regionKey);
		final Set<Long> affectedSnapshotKeys = visibleObjectSnapshotKeysByRegion.remove(regionKey);

		int entriesCleared = 0;
		if (affectedWindowKeys != null) {
			for (final Long affectedWindowKey : affectedWindowKeys) {
				if (visibleObjectWindowCache.remove(affectedWindowKey) != null) {
					entriesCleared++;
				}
				removeCacheKeyFromRegionIndex(visibleObjectWindowKeysByRegion, affectedWindowKey);
			}
		}
		if (affectedSnapshotKeys != null) {
			for (final Long affectedSnapshotKey : affectedSnapshotKeys) {
				if (visibleObjectSnapshotCache.remove(affectedSnapshotKey) != null) {
					entriesCleared++;
				}
				removeCacheKeyFromRegionIndex(visibleObjectSnapshotKeysByRegion, affectedSnapshotKey);
			}
		}
		getWorld().getServer().recordVisibilityObjectCacheClear(entriesCleared);
	}

	private void removeCacheKeyFromRegionIndex(
		final ConcurrentHashMap<Long, Set<Long>> index,
		final Long removedCacheKey) {
		for (final Set<Long> indexedCacheKeys : index.values()) {
			indexedCacheKeys.remove(removedCacheKey);
		}
	}

	private VisibleObjectSnapshot getVisibleObjectSnapshot(final Point location, final List<Region> objectRegions) {
		final long cacheKey = packObjectSnapshotKey(location, getWorld().getServer().getConfig().OBJECT_VIEW_DISTANCE);
		final VisibleObjectSnapshot cached = visibleObjectSnapshotCache.get(cacheKey);
		if (cached != null) {
			getWorld().getServer().recordVisibilityObjectSnapshotCacheAccess(true);
			return cached;
		}

		final VisibleObjectSnapshot built = buildVisibleObjectSnapshot(cacheKey, location, objectRegions);
		final VisibleObjectSnapshot previous = visibleObjectSnapshotCache.putIfAbsent(cacheKey, built);
		getWorld().getServer().recordVisibilityObjectSnapshotCacheAccess(previous != null);
		if (previous == null) {
			indexCacheKeyByRegion(visibleObjectSnapshotKeysByRegion, cacheKey, objectRegions);
		}
		return previous == null ? built : previous;
	}

	private VisibleObjectSnapshot buildVisibleObjectSnapshot(
		final long cacheKey,
		final Point location,
		final List<Region> objectRegions) {
		final LinkedHashSet<GameObject> localObjects = new LinkedHashSet<>();
		final ArrayList<GameObject> localSceneryObjects = new ArrayList<>();
		final ArrayList<GameObject> localWallObjects = new ArrayList<>();
		for (final GameObject gameObject : getVisibleObjectWindow(location, objectRegions)) {
			if (gameObject.getLocation().withinGridRange(
				location,
				getWorld().getServer().getConfig().OBJECT_VIEW_DISTANCE)) {
				localObjects.add(gameObject);
				if (gameObject.getType() == 0) {
					localSceneryObjects.add(gameObject);
				} else if (gameObject.getType() == 1) {
					localWallObjects.add(gameObject);
				}
			}
		}

		return new VisibleObjectSnapshot(
			cacheKey,
			visibleObjectSnapshotSequence.incrementAndGet(),
			Collections.unmodifiableSet(localObjects),
			Collections.unmodifiableList(localSceneryObjects),
			Collections.unmodifiableList(localWallObjects));
	}

	private List<GameObject> getVisibleObjectWindow(final Point location, final List<Region> objectRegions) {
		final long cacheKey = packRegionWindowKey(location, getWorld().getServer().getConfig().OBJECT_VIEW_DISTANCE);
		final List<GameObject> cached = visibleObjectWindowCache.get(cacheKey);
		if (cached != null) {
			getWorld().getServer().recordVisibilityObjectCacheAccess(true);
			return cached;
		}

		final List<GameObject> built = buildVisibleObjectWindow(objectRegions);
		final List<GameObject> previous = visibleObjectWindowCache.putIfAbsent(cacheKey, built);
		getWorld().getServer().recordVisibilityObjectCacheAccess(previous != null);
		if (previous == null) {
			indexCacheKeyByRegion(visibleObjectWindowKeysByRegion, cacheKey, objectRegions);
		}
		return previous == null ? built : previous;
	}

	private void indexCacheKeyByRegion(
		final ConcurrentHashMap<Long, Set<Long>> index,
		final long cacheKey,
		final List<Region> objectRegions) {
		for (final Region region : objectRegions) {
			index
				.computeIfAbsent(packRegionCoordinateKey(region.getRegionX(), region.getRegionY()),
					ignored -> ConcurrentHashMap.newKeySet())
				.add(cacheKey);
		}
	}

	private List<GameObject> buildVisibleObjectWindow(final List<Region> objectRegions) {
		final ArrayList<GameObject> visible = new ArrayList<>();
		for (final Region region : objectRegions) {
			final Collection<GameObject> objects = region.getGameObjects();
			synchronized (objects) {
				visible.addAll(objects);
			}
		}

		return Collections.unmodifiableList(visible);
	}

	/**
	 * Gets regions within range of the given location
	 * @param location location
	 * @return regions within range of the given location
	 */
	public LinkedHashSet<Region> getVisibleRegions(final Point location) {
		return new LinkedHashSet<>(getVisibleRegionWindow(location));
	}

	private List<Region> getVisibleRegionWindow(final Point location) {
		return getVisibleRegionWindow(location, getWorld().getServer().getConfig().VIEW_DISTANCE);
	}

	private List<Region> getVisibleRegionWindow(final Point location, final int gridDistance) {
		// View distance is in multiples of 8
		final int viewDistance = gridDistance << 3;

		final int minRegionX = Math.floorDiv(location.getX() - viewDistance, Constants.REGION_SIZE);
		final int maxRegionX = Math.floorDiv(location.getX() + viewDistance, Constants.REGION_SIZE);
		final int minRegionY = Math.floorDiv(location.getY() - viewDistance, Constants.REGION_SIZE);
		final int maxRegionY = Math.floorDiv(location.getY() + viewDistance, Constants.REGION_SIZE);
		final long cacheKey = packRegionWindowKey(minRegionX, minRegionY, maxRegionX, maxRegionY);

		final List<Region> cached = visibleRegionWindowCache.get(cacheKey);
		if (cached != null) {
			getWorld().getServer().recordVisibilityRegionCacheAccess(true);
			return cached;
		}

		final List<Region> built = buildVisibleRegionWindow(minRegionX, minRegionY, maxRegionX, maxRegionY);
		final List<Region> previous = visibleRegionWindowCache.putIfAbsent(cacheKey, built);
		getWorld().getServer().recordVisibilityRegionCacheAccess(previous != null);
		return previous == null ? built : previous;
	}

	private List<Region> buildVisibleRegionWindow(
		final int minRegionX,
		final int minRegionY,
		final int maxRegionX,
		final int maxRegionY) {
		final ArrayList<Region> visible = new ArrayList<>(
			Math.max(1, (maxRegionX - minRegionX + 1) * (maxRegionY - minRegionY + 1)));

		for(int x = minRegionX; x <= maxRegionX; x++) {
			for(int y = minRegionY; y <= maxRegionY; y++) {
				final Region tmpRegion = getRegionFromSectorCoordinates(x, y);
				if (tmpRegion != null) {
					visible.add(tmpRegion);
				}
			}
		}

		return Collections.unmodifiableList(visible);
	}

	private long packRegionWindowKey(final Point location, final int gridDistance) {
		// View distance is in multiples of 8
		final int viewDistance = gridDistance << 3;

		final int minRegionX = Math.floorDiv(location.getX() - viewDistance, Constants.REGION_SIZE);
		final int maxRegionX = Math.floorDiv(location.getX() + viewDistance, Constants.REGION_SIZE);
		final int minRegionY = Math.floorDiv(location.getY() - viewDistance, Constants.REGION_SIZE);
		final int maxRegionY = Math.floorDiv(location.getY() + viewDistance, Constants.REGION_SIZE);
		return packRegionWindowKey(minRegionX, minRegionY, maxRegionX, maxRegionY);
	}

	private long packRegionWindowKey(
		final int minRegionX,
		final int minRegionY,
		final int maxRegionX,
		final int maxRegionY) {
		return ((long) (minRegionX & 0xFFFF) << 48)
			| ((long) (minRegionY & 0xFFFF) << 32)
			| ((long) (maxRegionX & 0xFFFF) << 16)
			| (maxRegionY & 0xFFFFL);
	}

	private long packRegionCoordinateKey(final int regionX, final int regionY) {
		return ((long) regionX << 32) ^ (regionY & 0xFFFFFFFFL);
	}

	private long packObjectSnapshotKey(final Point location, final int gridDistance) {
		return ((long) ((location.getX() >> 3) & 0xFFFF) << 48)
			| ((long) ((location.getY() >> 3) & 0xFFFF) << 32)
			| (gridDistance & 0xFFFFFFFFL);
	}

	private static final class VisibleObjectSnapshot {
		private final long cacheKey;
		private final long version;
		private final Collection<GameObject> gameObjects;
		private final Collection<GameObject> sceneryObjects;
		private final Collection<GameObject> wallObjects;

		private VisibleObjectSnapshot(
			final long cacheKey,
			final long version,
			final Collection<GameObject> gameObjects,
			final Collection<GameObject> sceneryObjects,
			final Collection<GameObject> wallObjects) {
			this.cacheKey = cacheKey;
			this.version = version;
			this.gameObjects = gameObjects;
			this.sceneryObjects = sceneryObjects;
			this.wallObjects = wallObjects;
		}
	}

	/**
	 * Gets the regions surrounding a location.
	 *
	 * @param location The location.
	 * @return The regions surrounding the location.
	 */
	public LinkedHashSet<Region> getSurroundingRegions(final Point location) {
		final int regionX = location.getX() / Constants.REGION_SIZE;
		final int regionY = location.getY() / Constants.REGION_SIZE;

		final LinkedHashSet<Region> surrounding = new LinkedHashSet<Region>();
		surrounding.add(getRegionFromSectorCoordinates(regionX, regionY));
		final int[] xMod = {-1, +1, -1, 0, +1, 0, -1, +1};
		final int[] yMod = {-1, +1, 0, -1, 0, +1, +1, -1};
		for (int i = 0; i < xMod.length; i++) {
			final Region tmpRegion = getRegionFromSectorCoordinates(regionX + xMod[i], regionY + yMod[i]);
			if (tmpRegion != null) {
				surrounding.add(tmpRegion);
			}
		}
		return surrounding;
	}

	private Region getRegionFromSectorCoordinates(final int regionX, final int regionY) {
		// Create a new HashMap if it doesn't exist.
		if (!getRegions().containsKey(regionX)) {
			getRegions().put(regionX, new ConcurrentHashMap<>());
		}

		if (!getRegions().get(regionX).containsKey(regionY)) {
			getRegions().get(regionX).put(regionY, new Region(this, regionX, regionY));
		}

		return getRegions().get(regionX).get(regionY);
	}

	public Region getRegion(final int x, final int y) {
		final int regionX = x / Constants.REGION_SIZE;
		final int regionY = y / Constants.REGION_SIZE;
		return getRegionFromSectorCoordinates(regionX, regionY);
	}

	public Region getRegion(final Point objectCoordinates) {
		return getRegion(objectCoordinates.getX(), objectCoordinates.getY());
	}

	/**
	 * Projects a packed point into the future level-aware region identity.
	 *
	 * <p>This does not perform a lookup in the current packed region maps.</p>
	 */
	public WorldRegionKey getLayeredRegionKey(final Point objectCoordinates) {
		return WorldRegionKey.fromLegacyPoint(objectCoordinates);
	}

	/**
	 * Calculates a level-aware region identity without consulting packed storage.
	 */
	public WorldRegionKey getLayeredRegionKey(final WorldLocation location) {
		return WorldRegionKey.from(location);
	}

	/**
	 * Projects one current packed region cell into every logical key it overlaps.
	 *
	 * <p>This does not access or mutate the current packed region maps.</p>
	 */
	public LegacyPackedRegionCoverage getLayeredRegionCoverage(
		final int packedRegionX,
		final int packedRegionY) {
		return LegacyPackedRegionCoverage.fromPackedRegionCoordinates(
			packedRegionX, packedRegionY);
	}

	/**
	 * Compares packed candidate-region coverage to one logical interest window.
	 *
	 * <p>This is projection-only and does not consult packed maps or caches.</p>
	 */
	public LegacyPackedVisibilityCoverageComparison compareLayeredVisibleRegionCoverage(
		final Point location,
		final int gridDistance,
		final int maximumPackedCells,
		final int maximumLogicalKeys) {
		return LegacyPackedVisibilityCoverageComparison.compare(
			location, gridDistance, maximumPackedCells, maximumLogicalKeys);
	}

	/**
	 * Projects the configured visibility bounds without consulting packed region
	 * storage or its caches.
	 */
	public WorldRegionWindow getLayeredVisibleRegionWindow(final WorldLocation location) {
		return getLayeredVisibleRegionWindow(
			location, getWorld().getServer().getConfig().VIEW_DISTANCE);
	}

	/**
	 * Projects legacy view-distance units into one level-qualified logical window.
	 */
	public WorldRegionWindow getLayeredVisibleRegionWindow(
		final WorldLocation location,
		final int gridDistance) {
		if (gridDistance < 0) {
			throw new IllegalArgumentException("Grid distance must not be negative");
		}
		return WorldRegionWindow.around(location, Math.multiplyExact(gridDistance, 8));
	}

	/**
	 * Are the given coords within the world boundaries
	 */
	public boolean withinWorld(final int x, final int y) {
		return x >= 0 && x < Constants.MAX_WIDTH && y >= 0 && y < Constants.MAX_HEIGHT;
	}

	public TileValue getTile(final int x, final int y) {
		if (!withinWorld(x, y)) {
			return null;
		}

		return getRegion(x, y).getTileValue(x % Constants.REGION_SIZE, y % Constants.REGION_SIZE);
	}

	public TileValue getMutableTile(final int x, final int y) {
		if (!withinWorld(x, y)) {
			return null;
		}
		return getRegion(x, y).getMutableTileValue(x % Constants.REGION_SIZE, y % Constants.REGION_SIZE);
	}

	public TileValue getTile(final Point point) {
		return getTile(point.getX(), point.getY());
	}

	// originally private, set to public to access for reset event
	public ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>> getRegions() {
		return regions;
	}

	public World getWorld() {
		return world;
	}
}
