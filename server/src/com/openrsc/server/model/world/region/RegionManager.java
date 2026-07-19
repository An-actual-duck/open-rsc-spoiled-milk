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
import com.openrsc.server.model.world.coordinate.LegacyLogicalRegionAssembly;
import com.openrsc.server.model.world.coordinate.LegacyLogicalTileAddress;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.LegacyPackedRegionCoverage;
import com.openrsc.server.model.world.coordinate.LegacyPackedRegionPartition;
import com.openrsc.server.model.world.coordinate.LegacyPackedVisibilityCoverageComparison;
import com.openrsc.server.model.world.coordinate.LayeredRegionInterestResidencyComparison;
import com.openrsc.server.model.world.coordinate.LayeredRegionResidencyMirror;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldRegionInterestDelta;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;
import com.openrsc.server.model.world.coordinate.WorldRegionWindow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
	private final Object layeredRegionLifecycleLock;
	private final LayeredRegionResidencyMirror layeredRegionResidencyMirror;

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
		this.layeredRegionLifecycleLock = new Object();
		this.layeredRegionResidencyMirror = new LayeredRegionResidencyMirror();
	}

	public void load() {
		// TODO: The WorldLoader.loadWorld() should accept a RegionManager as an argument and place regions there.
		getWorld().getWorldLoader().loadWorld();
	}

	public void unload() {
		synchronized (layeredRegionLifecycleLock) {
			for (final ConcurrentHashMap<Integer, Region> yRegionList : regions.values()) {
				for (final Region region : yRegionList.values()) {
					region.unload();
				}
			}
			regions.clear();
			layeredRegionResidencyMirror.clear();
		}
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
		ConcurrentHashMap<Integer, Region> yRegions = regions.get(regionX);
		Region region = yRegions == null ? null : yRegions.get(regionY);
		if (region != null) {
			return region;
		}
		// Region construction and logical-residency registration are one lifecycle
		// boundary. Existing Region and tile lookup remain packed and authoritative.
		synchronized (layeredRegionLifecycleLock) {
			yRegions = regions.get(regionX);
			if (yRegions == null) {
				yRegions = new ConcurrentHashMap<Integer, Region>();
				regions.put(regionX, yRegions);
			}
			region = yRegions.get(regionY);
			if (region == null) {
				region = new Region(this, regionX, regionY);
				yRegions.put(regionY, region);
				layeredRegionResidencyMirror.registerPackedRegion(regionX, regionY);
			}
			return region;
		}
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
	 * Projects one packed cell into exact logical tile fragments.
	 *
	 * <p>This does not access a Region, its tile grid, entities, or caches.</p>
	 */
	public LegacyPackedRegionPartition getLayeredRegionPartition(
		final int packedRegionX,
		final int packedRegionY) {
		return LegacyPackedRegionPartition.fromPackedRegionCoordinates(
			packedRegionX, packedRegionY);
	}

	/**
	 * Projects one logical key into its ordered legacy packed-cell fragments.
	 *
	 * <p>This does not access a Region, its tile grid, entities, or caches.</p>
	 */
	public LegacyLogicalRegionAssembly getLegacyLogicalRegionAssembly(
		final WorldRegionKey logicalRegionKey) {
		return LegacyLogicalRegionAssembly.fromLogicalRegionKey(logicalRegionKey);
	}

	/**
	 * Returns a checked, versioned logical view of current packed Region
	 * residency. No Region is created and no tile or collision state is cached.
	 */
	public LayeredRegionResidencyMirror.Snapshot
		getLayeredRegionResidencySnapshot(final WorldRegionKey logicalRegionKey) {
		synchronized (layeredRegionLifecycleLock) {
			return requireLayeredRegionResidencySnapshot(logicalRegionKey);
		}
	}

	/**
	 * Compares one bounded logical interest change with current residency without
	 * loading, retaining, releasing, or evicting any Region.
	 */
	public LayeredRegionInterestResidencyComparison
		compareLayeredRegionInterestResidency(
			final WorldRegionWindow previousWindow,
			final WorldRegionWindow currentWindow,
			final int maximumRegionsPerWindow) {
		WorldRegionInterestDelta delta = WorldRegionInterestDelta.between(
			previousWindow, currentWindow, maximumRegionsPerWindow);
		synchronized (layeredRegionLifecycleLock) {
			List<LayeredRegionResidencyMirror.Snapshot> snapshots =
				new ArrayList<LayeredRegionResidencyMirror.Snapshot>(
					delta.getEntered().size() + delta.getRetained().size()
						+ delta.getExited().size());
			appendLayeredRegionResidencySnapshots(snapshots, delta.getEntered());
			appendLayeredRegionResidencySnapshots(snapshots, delta.getRetained());
			appendLayeredRegionResidencySnapshots(snapshots, delta.getExited());
			return LayeredRegionInterestResidencyComparison.compare(delta, snapshots);
		}
	}

	private void appendLayeredRegionResidencySnapshots(
		final List<LayeredRegionResidencyMirror.Snapshot> snapshots,
		final List<WorldRegionKey> keys) {
		for (WorldRegionKey key : keys) {
			snapshots.add(requireLayeredRegionResidencySnapshot(key));
		}
	}

	private LayeredRegionResidencyMirror.Snapshot
		requireLayeredRegionResidencySnapshot(final WorldRegionKey logicalRegionKey) {
		LayeredRegionResidencyMirror.Snapshot snapshot =
			layeredRegionResidencyMirror.snapshot(logicalRegionKey);
		for (LayeredRegionResidencyMirror.SourceResidency source
			: snapshot.getSources()) {
			boolean packedResident = peekRegionFromSectorCoordinates(
				source.getPackedRegionX(), source.getPackedRegionY()) != null;
			if (packedResident != source.isResident()) {
				throw new IllegalStateException(
					"Layered Region residency mirror differs from packed storage");
			}
		}
		return snapshot;
	}

	/**
	 * Projects one logical region-local tile into its checked packed source.
	 *
	 * <p>This does not access a Region, TileValue, entity, or cache.</p>
	 */
	public LegacyLogicalTileAddress getLegacyLogicalTileAddress(
		final WorldRegionKey logicalRegionKey,
		final int logicalLocalX,
		final int logicalLocalY) {
		return LegacyLogicalTileAddress.resolve(
			logicalRegionKey, logicalLocalX, logicalLocalY);
	}

	/**
	 * Copies one logical region's supported packed tile values into a detached
	 * read-only snapshot. Current packed Regions remain authoritative.
	 */
	public LayeredRegionTileSnapshot getLayeredRegionTileSnapshot(
		final WorldRegionKey logicalRegionKey) {
		return LayeredRegionTileSnapshot.capture(
			logicalRegionKey,
			new LayeredRegionTileSnapshot.PackedTileSource() {
				@Override
				public boolean hasPackedRegion(
					final int packedRegionX,
					final int packedRegionY) {
					return peekRegionFromSectorCoordinates(
						packedRegionX, packedRegionY) != null;
				}

				@Override
				public TileValue readPackedTile(
					final int packedRegionX,
					final int packedRegionY,
					final int packedLocalX,
					final int packedLocalY) {
					Region region = peekRegionFromSectorCoordinates(
						packedRegionX, packedRegionY);
					return region == null ? null
						: region.getTileValue(packedLocalX, packedLocalY);
				}
			});
	}

	/**
	 * Compares one direct packed tile with its detached logical snapshot state.
	 * No Region is created and neither state becomes authoritative.
	 */
	public LayeredTileStateParityComparison compareLayeredTileState(
		final Point packedPoint) {
		return compareLayeredTileState(
			LegacyPackedPointAdapter.fromLegacyPoint(packedPoint));
	}

	/**
	 * Compares one logical tile with its current direct packed source when one
	 * exists. Unsupported and unloaded sources remain explicit.
	 */
	public LayeredTileStateParityComparison compareLayeredTileState(
		final WorldLocation logicalLocation) {
		WorldRegionKey key = WorldRegionKey.from(logicalLocation);
		LayeredRegionTileSnapshot snapshot = getLayeredRegionTileSnapshot(key);
		return compareLayeredTileState(logicalLocation, snapshot);
	}

	/**
	 * Compares the 3x3 logical tile neighborhood around one packed center.
	 * The comparison is detached and does not affect movement or collision.
	 */
	public LayeredTileNeighborhoodParityComparison
		compareLayeredTileNeighborhood(final Point packedCenter) {
		return compareLayeredTileNeighborhood(
			LegacyPackedPointAdapter.fromLegacyPoint(packedCenter));
	}

	/**
	 * Compares one bounded logical neighborhood with its direct packed sources.
	 * Logical snapshots are reused within this call but are not cached.
	 */
	public LayeredTileNeighborhoodParityComparison
		compareLayeredTileNeighborhood(final WorldLocation logicalCenter) {
		Map<WorldRegionKey, LayeredRegionTileSnapshot> snapshots =
			new HashMap<WorldRegionKey, LayeredRegionTileSnapshot>();
		List<LayeredTileStateParityComparison> cells =
			new ArrayList<LayeredTileStateParityComparison>(
				LayeredTileNeighborhoodParityComparison.CELL_COUNT);
		for (int offsetY = -LayeredTileNeighborhoodParityComparison.RADIUS;
			offsetY <= LayeredTileNeighborhoodParityComparison.RADIUS;
			offsetY++) {
			for (int offsetX = -LayeredTileNeighborhoodParityComparison.RADIUS;
				offsetX <= LayeredTileNeighborhoodParityComparison.RADIUS;
				offsetX++) {
				WorldLocation location = LayeredTileNeighborhoodParityComparison.offset(
					logicalCenter, offsetX, offsetY);
				WorldRegionKey key = WorldRegionKey.from(location);
				LayeredRegionTileSnapshot snapshot = snapshots.get(key);
				if (snapshot == null) {
					snapshot = getLayeredRegionTileSnapshot(key);
					snapshots.put(key, snapshot);
				}
				cells.add(compareLayeredTileState(location, snapshot));
			}
		}
		return LayeredTileNeighborhoodParityComparison.of(
			logicalCenter, cells);
	}

	/**
	 * Compares one dormant adjacent tile-mask decision around a packed center.
	 * Existing movement and PathValidation remain authoritative.
	 */
	public LayeredAdjacentStepCollisionComparison
		compareLayeredAdjacentStepCollision(
			final Point packedCenter,
			final int offsetX,
			final int offsetY) {
		return compareLayeredAdjacentStepCollision(
			LegacyPackedPointAdapter.fromLegacyPoint(packedCenter),
			offsetX,
			offsetY);
	}

	/**
	 * Compares logical and direct packed tile-mask decisions without moving a
	 * Player, changing a path, or creating a Region.
	 */
	public LayeredAdjacentStepCollisionComparison
		compareLayeredAdjacentStepCollision(
			final WorldLocation logicalCenter,
			final int offsetX,
			final int offsetY) {
		return LayeredAdjacentStepCollisionComparison.of(
			compareLayeredTileNeighborhood(logicalCenter), offsetX, offsetY);
	}

	/**
	 * Compares all eight adjacent directions while reusing one detached 3x3
	 * neighborhood. Results are row-major with the center omitted.
	 */
	public List<LayeredAdjacentStepCollisionComparison>
		compareLayeredAdjacentStepCollisions(final Point packedCenter) {
		return compareLayeredAdjacentStepCollisions(
			LegacyPackedPointAdapter.fromLegacyPoint(packedCenter));
	}

	/** Returns an immutable eight-direction comparison without persistent cache. */
	public List<LayeredAdjacentStepCollisionComparison>
		compareLayeredAdjacentStepCollisions(final WorldLocation logicalCenter) {
		LayeredTileNeighborhoodParityComparison neighborhood =
			compareLayeredTileNeighborhood(logicalCenter);
		List<LayeredAdjacentStepCollisionComparison> comparisons =
			new ArrayList<LayeredAdjacentStepCollisionComparison>(8);
		for (int offsetY = -1; offsetY <= 1; offsetY++) {
			for (int offsetX = -1; offsetX <= 1; offsetX++) {
				if (offsetX != 0 || offsetY != 0) {
					comparisons.add(LayeredAdjacentStepCollisionComparison.of(
						neighborhood, offsetX, offsetY));
				}
			}
		}
		return Collections.unmodifiableList(comparisons);
	}

	/**
	 * Compares an already expanded adjacent-step route without selecting,
	 * mutating, or executing a Path. Existing movement remains authoritative.
	 */
	public LayeredTraversalCollisionComparison compareLayeredTraversalCollision(
		final List<WorldLocation> route) {
		if (route == null) {
			throw new NullPointerException("route");
		}
		if (route.size() < 2
			|| route.size() > LayeredTraversalCollisionComparison.MAXIMUM_STEP_COUNT + 1) {
			throw new IllegalArgumentException(
				"Layered traversal route must contain 2-"
					+ (LayeredTraversalCollisionComparison.MAXIMUM_STEP_COUNT + 1)
					+ " locations");
		}
		List<LayeredAdjacentStepCollisionComparison> comparisons =
			new ArrayList<LayeredAdjacentStepCollisionComparison>(route.size() - 1);
		WorldLocation source = route.get(0);
		if (source == null) {
			throw new NullPointerException("route[0]");
		}
		for (int index = 1; index < route.size(); index++) {
			WorldLocation destination = route.get(index);
			if (destination == null) {
				throw new NullPointerException("route[" + index + "]");
			}
			if (!source.getWorldSpace().equals(destination.getWorldSpace())
				|| source.getCoordinate().getLevel()
					!= destination.getCoordinate().getLevel()) {
				throw new IllegalArgumentException(
					"Layered traversal steps cannot change world-space or level");
			}
			int offsetX = Math.subtractExact(
				destination.getCoordinate().getX(), source.getCoordinate().getX());
			int offsetY = Math.subtractExact(
				destination.getCoordinate().getY(), source.getCoordinate().getY());
			if (offsetX < -1 || offsetX > 1 || offsetY < -1 || offsetY > 1
				|| (offsetX == 0 && offsetY == 0)) {
				throw new IllegalArgumentException(
					"Layered traversal route must contain adjacent, distinct locations");
			}
			comparisons.add(compareLayeredAdjacentStepCollision(
				source, offsetX, offsetY));
			source = destination;
		}
		return LayeredTraversalCollisionComparison.of(comparisons);
	}

	private LayeredTileStateParityComparison compareLayeredTileState(
		final WorldLocation logicalLocation,
		final LayeredRegionTileSnapshot snapshot) {
		WorldRegionKey key = WorldRegionKey.from(logicalLocation);
		LegacyLogicalTileAddress address = LegacyLogicalTileAddress.resolve(
			key,
			logicalLocation.getCoordinate().getLocalX(),
			logicalLocation.getCoordinate().getLocalY());
		Region directRegion = address.isLegacyRepresentable()
			? peekRegionFromSectorCoordinates(
				address.getPackedRegionX(), address.getPackedRegionY())
			: null;
		TileValue directTile = directRegion == null ? null
			: directRegion.getTileValue(
				address.getPackedLocalX(), address.getPackedLocalY());
		return LayeredTileStateParityComparison.compare(
			logicalLocation, snapshot, directRegion != null, directTile);
	}

	private Region peekRegionFromSectorCoordinates(
		final int packedRegionX,
		final int packedRegionY) {
		ConcurrentHashMap<Integer, Region> yRegions = regions.get(packedRegionX);
		return yRegions == null ? null : yRegions.get(packedRegionY);
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
