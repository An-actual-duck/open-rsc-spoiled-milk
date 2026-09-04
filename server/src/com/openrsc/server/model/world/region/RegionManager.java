package com.openrsc.server.model.world.region;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.event.rsc.GameTickEventRestorationAtomicRevalidationContract;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.Operation;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionTransactionContract;
import com.openrsc.server.event.rsc.GameTickEventRestorationCommitRequest;
import com.openrsc.server.event.rsc.GameTickEventRestorationCurrentStateRecoverySnapshot;
import com.openrsc.server.event.rsc.GameTickEventRestorationCurrentStateRecoverySnapshot.CallbackExpectation;
import com.openrsc.server.event.rsc.GameTickEventRestorationCurrentStateRecoverySnapshot.CollisionContribution;
import com.openrsc.server.event.rsc.GameTickEventRestorationCurrentStateRecoverySnapshot.CurrentScenery;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetRevalidation;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetRevalidationRequest;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetDecision;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetDecision
	.TargetOperation;
import com.openrsc.server.io.NativeLayeredTerrainTile;
import com.openrsc.server.io.WorldLoader;
import com.openrsc.server.io.NativeLayeredGroundItemPlacement;
import com.openrsc.server.io.NativeLayeredNpcPlacement;
import com.openrsc.server.io.NativeLayeredSceneryPlacement;
import com.openrsc.server.io.NativeLayeredBoundaryPlacement;
import com.openrsc.server.io.NativeLayeredPlacementSet;
import com.openrsc.server.io.NativeLayeredWorldPackage;
import com.openrsc.server.io.NativeLayeredWorldPackageCatalog;
import com.openrsc.server.io.NativeLayeredWorldRuntimeProfile;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.CombatProjectileCollision;
import com.openrsc.server.model.entity.Entity;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GameObjectCollisionRegistrationState;
import com.openrsc.server.model.entity.GameObjectType;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.NativeLayeredGameObjectRegistry;
import com.openrsc.server.model.world.coordinate.LegacyLogicalRegionAssembly;
import com.openrsc.server.model.world.coordinate.LegacyLogicalTileAddress;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.LegacyPackedRegionCoverage;
import com.openrsc.server.model.world.coordinate.LegacyPackedRegionPartition;
import com.openrsc.server.model.world.coordinate.LegacyPackedVisibilityCoverageComparison;
import com.openrsc.server.model.world.coordinate.LayeredCompatibilityPointAdapter;
import com.openrsc.server.model.world.coordinate.LayeredRegionInterestResidencyComparison;
import com.openrsc.server.model.world.coordinate.LayeredRegionInterestOwnershipLedger;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementReadiness;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementRefinementProposal;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementRefinementReassessment;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementSafetyAssessment;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionPreservationBurdenAssessment;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionDynamicObjectPreservationRecord;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerEventContinuityAssessment;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerPreservationRequirements;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerPreservationRequirements.SelectedSource;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventTargetObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionCohortAnalysis;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionActiveNpcBoundaryRequirementProjection;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredPlacementManifest;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredPopulationOutcome;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredProvenanceObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionRecipe;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionActiveNpcResidencyObservation;
import com.openrsc.server.model.world.coordinate.LayeredRegionResidencyMirror;
import com.openrsc.server.model.world.coordinate.LayeredRegionRetirementDecisionArbiter;
import com.openrsc.server.model.world.coordinate.LayeredRegionRetirementEligibilityLedger;
import com.openrsc.server.model.world.coordinate.LayeredSpatialWindowKey;
import com.openrsc.server.model.world.coordinate.NativeLayeredGameObjectIdentity;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldRegionInterestDelta;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;
import com.openrsc.server.model.world.coordinate.WorldRegionWindow;
import com.openrsc.server.external.GameObjectLoc;
import com.openrsc.server.external.DoorDef;
import com.openrsc.server.external.EntityHandler;
import com.openrsc.server.external.GameObjectDef;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RegionManager {
	private static final Logger LOGGER =
		LogManager.getLogger(RegionManager.class);
	private static final LayeredSpatialEntityIndex.GameObjectTilePredicate
		NPC_BLOCKING_SCENERY_AT_TILE =
			(gameObject, tileX, tileY) -> {
				if (!gameObject.isScenery()
					|| gameObject.getGameObjectDef().getType() == 0) {
					return false;
				}
				final int width;
				final int height;
				if (gameObject.getDirection() == 0
					|| gameObject.getDirection() == 4) {
					width = gameObject.getGameObjectDef().getWidth();
					height = gameObject.getGameObjectDef().getHeight();
				} else {
					width = gameObject.getGameObjectDef().getHeight();
					height = gameObject.getGameObjectDef().getWidth();
				}
				return tileX >= gameObject.getX()
					&& tileX < gameObject.getX() + width
					&& tileY >= gameObject.getY()
					&& tileY < gameObject.getY() + height;
			};
	public static final int MAX_LAYERED_REGIONS_PER_INTEREST_OWNER = 4096;
	public static final int MAX_LAYERED_PACKED_SOURCES_PER_RETIREMENT_PLAN =
		MAX_LAYERED_REGIONS_PER_INTEREST_OWNER
			* LayeredPackedRegionRetirementReadiness
				.MAX_PACKED_SOURCES_PER_LOGICAL_REGION;
	public static final boolean LAYERED_PACKED_REGION_RELOAD_SUPPORTED = false;
	public static final long LAYERED_REGION_RETIREMENT_COOLDOWN_TICKS = 16L;
	public static final String NATIVE_LAYERED_PLACEMENT_ID_ATTRIBUTE =
		"native-layered-package-placement-id";
	public static final String NATIVE_LAYERED_PLACEMENT_KIND_ATTRIBUTE =
		"native-layered-package-placement-kind";
	public static final String NATIVE_LAYERED_PLACEMENT_PACKAGE_ATTRIBUTE =
		"native-layered-package-placement-package";
	public static final String NATIVE_LAYERED_NPC_KIND = "npc";
	public static final String NATIVE_LAYERED_GROUND_ITEM_KIND = "ground-item";
	public static final String NATIVE_LAYERED_SCENERY_KIND = "scenery";
	public static final String NATIVE_LAYERED_BOUNDARY_KIND = "boundary";

	private final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>> regions;
	private final ConcurrentHashMap<Long, List<Region>> visibleRegionWindowCache;
	private final ConcurrentHashMap<Long, List<GameObject>> visibleObjectWindowCache;
	private final ConcurrentHashMap<Long, Set<Long>> visibleObjectWindowKeysByRegion;
	private final ConcurrentHashMap<Long, VisibleObjectSnapshot> visibleObjectSnapshotCache;
	private final ConcurrentHashMap<Long, Set<Long>> visibleObjectSnapshotKeysByRegion;
	private final AtomicLong visibleObjectSnapshotSequence;
	private final Object layeredRegionLifecycleLock;
	private final LayeredRegionResidencyMirror layeredRegionResidencyMirror;
	private final LayeredRegionInterestOwnershipLedger
		layeredRegionInterestOwnershipLedger;
	private final LayeredRegionRetirementEligibilityLedger
		layeredRegionRetirementEligibilityLedger;
	private final LayeredRegionRetirementDecisionArbiter
		layeredRegionRetirementDecisionArbiter;
	private final LayeredSpatialEntityIndex layeredSpatialEntityIndex;
	private final NativeLayeredGameObjectRegistry<GameObject>
		nativeLayeredGameObjects;
	private final NativeLayeredWorldPackageCatalog
		nativeLayeredWorldPackageCatalog;
	private final NativeLayeredWorldPackage nativeLayeredWorldPackage;
	private final NativeLayeredWorldRuntimeProfile
		nativeLayeredWorldRuntimeProfile;
	private volatile boolean nativeLayeredPlacementsPopulated;

	private final World world;

	public RegionManager(final World world) {
		this.world = world;
		if (world.getServer().getConfig()
				.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY
			&& !world.getServer().getConfig()
				.WANT_LAYERED_PLAYER_LOCATION_AUTHORITY) {
			throw new IllegalStateException(
				"Layered spatial runtime authority requires "
					+ "layered Player location authority");
		}
		if (world.getServer().getConfig()
				.WANT_LAYERED_PROTOCOL_CLIENT_AUTHORITY
			&& (!world.getServer().getConfig()
					.WANT_LAYERED_PLAYER_LOCATION_AUTHORITY
				|| !world.getServer().getConfig()
					.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY)) {
			throw new IllegalStateException(
				"Layered protocol/client authority requires layered Player "
					+ "location and spatial runtime authority");
		}
		if (world.getServer().getConfig()
				.WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE
			&& (!world.getServer().getConfig()
					.WANT_LAYERED_PLAYER_LOCATION_AUTHORITY
				|| !world.getServer().getConfig()
					.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY
				|| !world.getServer().getConfig()
					.WANT_LAYERED_PROTOCOL_CLIENT_AUTHORITY)) {
			throw new IllegalStateException(
				"Layered synthetic deep fixture requires layered Player "
					+ "location, spatial runtime, and protocol/client authority");
		}
		if (world.getServer().getConfig()
				.WANT_LAYERED_NATIVE_TERRAIN_PACKAGE
			&& (!world.getServer().getConfig()
					.WANT_LAYERED_PLAYER_LOCATION_AUTHORITY
				|| !world.getServer().getConfig()
					.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY
				|| !world.getServer().getConfig()
					.WANT_LAYERED_PROTOCOL_CLIENT_AUTHORITY)) {
			throw new IllegalStateException(
				"Layered native terrain package requires layered Player "
					+ "location, spatial runtime, and protocol/client authority");
		}
		this.nativeLayeredWorldRuntimeProfile =
			NativeLayeredWorldRuntimeProfile.fromConfiguration(
				world.getServer().getConfig()
					.LAYERED_NATIVE_WORLD_RUNTIME_PROFILE);
		this.nativeLayeredWorldPackageCatalog =
			loadNativeLayeredWorldPackages(
				world, nativeLayeredWorldRuntimeProfile);
		this.nativeLayeredWorldPackage =
			nativeLayeredWorldPackageCatalog == null
				? null
				: nativeLayeredWorldPackageCatalog.getPrimaryPackage();
		this.regions = new ConcurrentHashMap<>();
		this.visibleRegionWindowCache = new ConcurrentHashMap<>();
		this.visibleObjectWindowCache = new ConcurrentHashMap<>();
		this.visibleObjectWindowKeysByRegion = new ConcurrentHashMap<>();
		this.visibleObjectSnapshotCache = new ConcurrentHashMap<>();
		this.visibleObjectSnapshotKeysByRegion = new ConcurrentHashMap<>();
		this.visibleObjectSnapshotSequence = new AtomicLong();
		this.layeredRegionLifecycleLock = new Object();
		this.layeredRegionResidencyMirror = new LayeredRegionResidencyMirror();
		this.layeredRegionInterestOwnershipLedger =
			new LayeredRegionInterestOwnershipLedger();
		this.layeredRegionRetirementEligibilityLedger =
			new LayeredRegionRetirementEligibilityLedger(
				LAYERED_REGION_RETIREMENT_COOLDOWN_TICKS);
		this.layeredRegionRetirementDecisionArbiter =
			new LayeredRegionRetirementDecisionArbiter();
		this.layeredSpatialEntityIndex = new LayeredSpatialEntityIndex();
		this.nativeLayeredGameObjects =
			new NativeLayeredGameObjectRegistry<GameObject>();
		this.nativeLayeredPlacementsPopulated = false;
	}

	private static NativeLayeredWorldPackageCatalog loadNativeLayeredWorldPackages(
		final World world,
		final NativeLayeredWorldRuntimeProfile profile) {
		if (!world.getServer().getConfig()
				.WANT_LAYERED_NATIVE_TERRAIN_PACKAGE) {
			return null;
		}
		final String configuredPath = world.getServer().getConfig()
			.LAYERED_NATIVE_TERRAIN_PACKAGE_PATH;
		if (configuredPath == null || configuredPath.trim().isEmpty()) {
			throw new IllegalStateException(
				"Layered native terrain package path is required when its gate is enabled");
		}
		try {
			NativeLayeredWorldPackageCatalog loaded =
				NativeLayeredWorldPackageCatalog.loadConfigured(
					configuredPath.trim());
			profile.validate(loaded);
			final String configuredManifest = world.getServer().getConfig()
				.LAYERED_NATIVE_TERRAIN_MANIFEST_SHA256;
			if (profile.requiresConfiguredManifestSha256()) {
				if (configuredManifest == null
					|| !configuredManifest.matches("[0-9a-f]{64}")) {
					throw new IllegalStateException(
						"The selected World Builder profile requires an exact "
							+ "layered package manifest SHA-256");
				}
				if (!configuredManifest.equals(
						loaded.getPrimaryPackage().getManifestSha256())) {
					throw new IllegalStateException(
						"The installed World Builder package does not match "
							+ "the configured layered package manifest SHA-256");
				}
			}
			return loaded;
		} catch (IOException failure) {
			throw new IllegalStateException(
				"Could not load the private native layered terrain package: "
					+ failure.getMessage(),
				failure);
		}
	}

	public void load() {
		if (nativeLayeredWorldRuntimeProfile.skipsLegacyTerrainArchive()) {
			LOGGER.info(
				"Skipping legacy terrain archives for native layered replacement profile {}",
				nativeLayeredWorldRuntimeProfile.getId());
			return;
		}
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
			layeredRegionInterestOwnershipLedger.clear();
			layeredRegionRetirementEligibilityLedger.clear();
			layeredSpatialEntityIndex.clear();
			nativeLayeredGameObjects.reset();
			nativeLayeredPlacementsPopulated = false;
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
		if (isLayeredSpatialRuntimeAuthorityEnabled()) {
			return getLayeredLocalPlayers(entity);
		}
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
		if (isLayeredSpatialRuntimeAuthorityEnabled()) {
			Entity checked = Objects.requireNonNull(entity, "entity");
			WorldLocation location = checked.getWorldLocation();
			layeredSpatialEntityIndex.requireMembership(checked, location);
			return layeredSpatialEntityIndex.hasPlayerWithinRange(
				getLayeredVisibleRegionWindow(location), checked);
		}
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
		if (isLayeredSpatialRuntimeAuthorityEnabled()) {
			return getLayeredLocalNpcs(entity);
		}
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
		if (isLayeredSpatialRuntimeAuthorityEnabled()) {
			return getLayeredLocalObjects(entity);
		}
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

	public boolean isNpcBlockedByScenery(
		final Npc npc,
		final int tileX,
		final int tileY) {
		if (isLayeredSpatialRuntimeAuthorityEnabled()) {
			Npc checked = Objects.requireNonNull(npc, "npc");
			WorldLocation location = checked.getWorldLocation();
			layeredSpatialEntityIndex.requireMembership(checked, location);
			if (usesNativeLayeredRegionlessMembership(location)) {
				return nativeLayeredGameObjects.hasNpcBlockingSceneryAt(
					new WorldLocation(
						location.getWorldSpace(),
						new WorldCoordinate(
							tileX,
							tileY,
							location.getCoordinate().getLevel())));
			}
			return layeredSpatialEntityIndex.hasGameObjectAt(
				getLayeredVisibleRegionWindow(
					location,
					getWorld().getServer().getConfig().OBJECT_VIEW_DISTANCE),
				tileX,
				tileY,
				WorldRegionKey.REGION_SIZE,
				WorldRegionKey.REGION_SIZE,
				NPC_BLOCKING_SCENERY_AT_TILE);
		}
		for (GameObject object : getLocalObjects(npc)) {
			if (NPC_BLOCKING_SCENERY_AT_TILE.matches(
					object, tileX, tileY)) {
				return true;
			}
		}
		return false;
	}

	public Collection<GroundItem> getLocalGroundItems(final Mob entity) {
		if (isLayeredSpatialRuntimeAuthorityEnabled()) {
			return getLayeredLocalGroundItems(entity);
		}
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
		if (isLayeredSpatialRuntimeAuthorityEnabled()) {
			return buildLayeredVisibilitySnapshot(entity);
		}
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

	/**
	 * Builds the custom client's exact authoritative scene window.
	 *
	 * <p>The supplied bounds use the client's temporary runtime-coordinate
	 * carrier. They are translated back to logical coordinates before querying
	 * the layered index, then filtered as one half-open rectangle. This avoids
	 * both the legacy player-centered radius and accidental packed-Y lookups on
	 * non-surface levels.</p>
	 */
	public VisibilitySnapshot buildClientSceneVisibilitySnapshot(
		final Player observer,
		final int minRuntimeX,
		final int minRuntimeY,
		final int maxRuntimeXExclusive,
		final int maxRuntimeYExclusive) {
		final Player checked = Objects.requireNonNull(observer, "observer");
		if (!isLayeredSpatialRuntimeAuthorityEnabled()) {
			throw new IllegalStateException(
				"Exact client scene visibility requires layered spatial authority");
		}
		if (minRuntimeX >= maxRuntimeXExclusive
			|| minRuntimeY >= maxRuntimeYExclusive) {
			throw new IllegalArgumentException(
				"Exact client scene bounds must be non-empty");
		}
		final WorldLocation observerLocation = checked.getWorldLocation();
		layeredSpatialEntityIndex.requireMembership(
			checked, observerLocation);
		final Point runtimeObserver =
			toRuntimeCompatibilityPoint(observerLocation);
		if (runtimeObserver.getX() != checked.getX()
			|| runtimeObserver.getY() != checked.getY()) {
			throw new IllegalStateException(
				"Exact client scene runtime receipt disagrees with observer");
		}
		final WorldCoordinate logicalObserver =
			observerLocation.getCoordinate();
		final int runtimeOffsetX = Math.subtractExact(
			runtimeObserver.getX(), logicalObserver.getX());
		final int runtimeOffsetY = Math.subtractExact(
			runtimeObserver.getY(), logicalObserver.getY());
		final int minLogicalX = Math.subtractExact(
			minRuntimeX, runtimeOffsetX);
		final int minLogicalY = Math.subtractExact(
			minRuntimeY, runtimeOffsetY);
		final int maxLogicalXExclusive = Math.subtractExact(
			maxRuntimeXExclusive, runtimeOffsetX);
		final int maxLogicalYExclusive = Math.subtractExact(
			maxRuntimeYExclusive, runtimeOffsetY);
		final WorldRegionWindow window = new WorldRegionWindow(
			observerLocation.getWorldSpace(),
			logicalObserver.getLevel(),
			Math.floorDiv(minLogicalX, WorldRegionKey.REGION_SIZE),
			Math.floorDiv(minLogicalY, WorldRegionKey.REGION_SIZE),
			Math.floorDiv(
				Math.subtractExact(maxLogicalXExclusive, 1),
				WorldRegionKey.REGION_SIZE),
			Math.floorDiv(
				Math.subtractExact(maxLogicalYExclusive, 1),
				WorldRegionKey.REGION_SIZE));
		final LayeredSpatialEntityIndex.Snapshot snapshot =
			layeredSpatialEntityIndex.snapshot(window);
		final LinkedHashSet<Player> players =
			new LinkedHashSet<Player>();
		final LinkedHashSet<Npc> npcs =
			new LinkedHashSet<Npc>();
		final LinkedHashSet<GameObject> objects =
			new LinkedHashSet<GameObject>();
		final ArrayList<GameObject> scenery =
			new ArrayList<GameObject>();
		final ArrayList<GameObject> walls =
			new ArrayList<GameObject>();
		final LinkedHashSet<GroundItem> items =
			new LinkedHashSet<GroundItem>();
		for (final Entity candidate : snapshot.getEntities()) {
			if (!candidate.sharesSpatialDomain(checked)) {
				continue;
			}
			final WorldCoordinate coordinate =
				candidate.getWorldLocation().getCoordinate();
			if (coordinate.getX() < minLogicalX
				|| coordinate.getX() >= maxLogicalXExclusive
				|| coordinate.getY() < minLogicalY
				|| coordinate.getY() >= maxLogicalYExclusive) {
				continue;
			}
			if (candidate instanceof Player) {
				players.add((Player) candidate);
			} else if (candidate instanceof Npc) {
				npcs.add((Npc) candidate);
			} else if (candidate instanceof GameObject) {
				final GameObject object = (GameObject) candidate;
				if (objects.add(object)) {
					if (object.getType() == 0) {
						scenery.add(object);
					} else if (object.getType() == 1) {
						walls.add(object);
					}
				}
			} else if (candidate instanceof GroundItem) {
				items.add((GroundItem) candidate);
			}
		}
		final LayeredSpatialWindowKey key =
			LayeredSpatialWindowKey.exact(
				observerLocation,
				minLogicalX,
				minLogicalY,
				maxLogicalXExclusive,
				maxLogicalYExclusive);
		final int regionCount =
			Math.toIntExact(window.getRegionCount());
		return new VisibilitySnapshot(
			players,
			npcs,
			objects,
			scenery,
			walls,
			items,
			regionCount,
			regionCount,
			key,
			snapshot.getObjectVersion());
	}

	/**
	 * Copies the current static world state in the visual-only sector ring
	 * surrounding the custom client's authoritative scene.
	 */
	public StaticScenePresentationSnapshot
		buildStaticScenePresentationSnapshot(
			final Player observer,
			final int centerSectorX,
			final int centerSectorY,
			final int outerRadius,
			final int innerRadius) {
		Player checked = Objects.requireNonNull(observer, "observer");
		if (!isLayeredSpatialRuntimeAuthorityEnabled()) {
			throw new IllegalStateException(
				"Static presentation requires layered spatial authority");
		}
		if (innerRadius < 0 || outerRadius <= innerRadius) {
			throw new IllegalArgumentException(
				"Static presentation radii must define a non-empty outer ring");
		}
		WorldLocation observerLocation = checked.getWorldLocation();
		layeredSpatialEntityIndex.requireMembership(
			checked, observerLocation);
		WorldRegionWindow window = new WorldRegionWindow(
			observerLocation.getWorldSpace(),
			observerLocation.getCoordinate().getLevel(),
			Math.subtractExact(centerSectorX, outerRadius),
			Math.subtractExact(centerSectorY, outerRadius),
			Math.addExact(centerSectorX, outerRadius),
			Math.addExact(centerSectorY, outerRadius));
		LayeredSpatialEntityIndex.GameObjectSnapshot snapshot =
			layeredSpatialEntityIndex.snapshotGameObjects(window);
		final int sectorSize = WorldRegionKey.REGION_SIZE;
		final int outerMinX = Math.multiplyExact(
			Math.subtractExact(centerSectorX, outerRadius), sectorSize);
		final int outerMinY = Math.multiplyExact(
			Math.subtractExact(centerSectorY, outerRadius), sectorSize);
		final int outerMaxX = Math.multiplyExact(
			Math.addExact(centerSectorX, outerRadius + 1), sectorSize);
		final int outerMaxY = Math.multiplyExact(
			Math.addExact(centerSectorY, outerRadius + 1), sectorSize);
		final int innerMinX = Math.multiplyExact(
			Math.subtractExact(centerSectorX, innerRadius), sectorSize);
		final int innerMinY = Math.multiplyExact(
			Math.subtractExact(centerSectorY, innerRadius), sectorSize);
		final int innerMaxX = Math.multiplyExact(
			Math.addExact(centerSectorX, innerRadius + 1), sectorSize);
		final int innerMaxY = Math.multiplyExact(
			Math.addExact(centerSectorY, innerRadius + 1), sectorSize);
		ArrayList<StaticScenePresentationSnapshot.Record> scenery =
			new ArrayList<StaticScenePresentationSnapshot.Record>();
		ArrayList<StaticScenePresentationSnapshot.Record> walls =
			new ArrayList<StaticScenePresentationSnapshot.Record>();
		for (GameObject object : snapshot.getGameObjects()) {
			final int x = object.getX();
			final int y = object.getY();
			if (object.isRemoved()
				|| object.isInvisibleTo(checked)
				|| x < outerMinX || x >= outerMaxX
				|| y < outerMinY || y >= outerMaxY
				|| x >= innerMinX && x < innerMaxX
					&& y >= innerMinY && y < innerMaxY) {
				continue;
			}
			if (object.getType() == 0) {
				scenery.add(toStaticScenePresentationRecord(object));
			} else if (object.getType() == 1) {
				walls.add(toStaticScenePresentationRecord(object));
			}
		}
		Comparator<StaticScenePresentationSnapshot.Record> order =
			Comparator
				.comparingInt(
					StaticScenePresentationSnapshot.Record::getX)
				.thenComparingInt(
					StaticScenePresentationSnapshot.Record::getY)
				.thenComparingInt(
					StaticScenePresentationSnapshot.Record::getType)
				.thenComparingInt(
					StaticScenePresentationSnapshot.Record::getDirection)
				.thenComparingInt(
					StaticScenePresentationSnapshot.Record::getId);
		scenery.sort(order);
		walls.sort(order);
		return new StaticScenePresentationSnapshot(
			centerSectorX,
			centerSectorY,
			outerRadius,
			innerRadius,
			snapshot.getObjectVersion(),
			scenery,
			walls);
	}

	private static StaticScenePresentationSnapshot.Record
		toStaticScenePresentationRecord(final GameObject object) {
		return new StaticScenePresentationSnapshot.Record(
			object.getID(),
			object.getX(),
			object.getY(),
			object.getDirection(),
			object.getType());
	}

	public boolean isLayeredSpatialRuntimeAuthorityEnabled() {
		return getWorld().getServer().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
	}

	/**
	 * Returns whether this exact native location must use only the layered
	 * spatial index. Legacy and synthetic compatibility scopes retain packed
	 * Region membership.
	 */
	public boolean usesNativeLayeredRegionlessMembership(
		final WorldLocation location) {
		return isLayeredSpatialRuntimeAuthorityEnabled()
			&& hasNativeLayeredTerrain(
				Objects.requireNonNull(location, "location"));
	}

	/**
	 * Verifies the spatial membership and packed-Region carrier invariant for
	 * one live entity.
	 */
	public void requireEntitySpatialCarrier(final Entity entity) {
		Entity checked = Objects.requireNonNull(entity, "entity");
		WorldLocation location = checked.getWorldLocation();
		if (isLayeredSpatialRuntimeAuthorityEnabled()) {
			layeredSpatialEntityIndex.requireMembership(checked, location);
		}
		boolean nativeRegionless =
			usesNativeLayeredRegionlessMembership(location);
		if (nativeRegionless != (checked.getRegion() == null)) {
			throw new IllegalStateException(
				nativeRegionless
					? "Native layered entity occupies a packed Region"
					: "Legacy entity is missing packed Region membership");
		}
	}

	/**
	 * Synchronizes one Entity's authoritative logical membership. Native
	 * package scopes deliberately have no compatibility Region membership.
	 */
	public void synchronizeLayeredSpatialMembership(
		final Entity entity,
		final WorldLocation expectedPrevious,
		final WorldLocation target) {
		if (!isLayeredSpatialRuntimeAuthorityEnabled()) {
			return;
		}
		layeredSpatialEntityIndex.synchronize(
			Objects.requireNonNull(entity, "entity"),
			expectedPrevious,
			Objects.requireNonNull(target, "target"));
	}

	public void removeLayeredSpatialMembership(
		final Entity entity,
		final WorldLocation expectedLocation) {
		if (!isLayeredSpatialRuntimeAuthorityEnabled()) {
			return;
		}
		layeredSpatialEntityIndex.remove(
			Objects.requireNonNull(entity, "entity"),
			Objects.requireNonNull(expectedLocation, "expectedLocation"));
	}

	public WorldRegionKey requireLayeredSpatialMembership(
		final Entity entity) {
		Entity checked = Objects.requireNonNull(entity, "entity");
		if (!isLayeredSpatialRuntimeAuthorityEnabled()) {
			throw new IllegalStateException(
				"Layered spatial runtime authority is disabled");
		}
		WorldLocation location = checked.getWorldLocation();
		layeredSpatialEntityIndex.requireMembership(checked, location);
		return WorldRegionKey.from(location);
	}

	public int getLayeredSpatialMembershipCount() {
		return layeredSpatialEntityIndex.getMembershipCount();
	}

	/**
	 * Finds interaction entities through exact layered identity when the
	 * spatial authority gate is active, otherwise through the legacy Region.
	 * These adapters keep native callers from acquiring a packed Region solely
	 * to use it as a lookup facade.
	 */
	public GameObject findInteractionScenery(
		final Point location,
		final Entity observer) {
		if (isLayeredSpatialRuntimeAuthorityEnabled()) {
			return findLayeredGameObject(
				location, observer, GameObjectType.SCENERY, null);
		}
		return getRegion(location).getGameObject(location, observer);
	}

	public GameObject findInteractionBoundary(
		final Point location,
		final Entity observer) {
		if (isLayeredSpatialRuntimeAuthorityEnabled()) {
			return findLayeredGameObject(
				location, observer, GameObjectType.BOUNDARY, null);
		}
		return getRegion(location).getWallGameObject(location, observer);
	}

	public Npc findInteractionNpc(
		final Point location,
		final Entity observer) {
		if (isLayeredSpatialRuntimeAuthorityEnabled()) {
			return findLayeredNpc(location, observer);
		}
		return getRegion(location).getNpc(location, observer);
	}

	public Player findInteractionPlayer(
		final int x,
		final int y,
		final Entity observer,
		final boolean includeSelf) {
		if (isLayeredSpatialRuntimeAuthorityEnabled()) {
			return findLayeredPlayer(
				Point.location(x, y), observer, includeSelf);
		}
		return getRegion(x, y).getPlayer(
			x, y, observer, includeSelf);
	}

	public GroundItem findInteractionGroundItem(
		final int id,
		final Point location,
		final Entity observer) {
		if (isLayeredSpatialRuntimeAuthorityEnabled()) {
			return findLayeredGroundItem(id, location, observer);
		}
		return getRegion(location).getItem(id, location, observer);
	}

	private Collection<Player> getLayeredLocalPlayers(final Entity observer) {
		Entity checked = Objects.requireNonNull(observer, "observer");
		WorldLocation location = checked.getWorldLocation();
		layeredSpatialEntityIndex.requireMembership(checked, location);
		return layeredSpatialEntityIndex.snapshotPlayersWithinRange(
			getLayeredVisibleRegionWindow(location), checked);
	}

	private Collection<Npc> getLayeredLocalNpcs(final Entity observer) {
		LinkedHashSet<Npc> npcs = new LinkedHashSet<Npc>();
		Entity checked = Objects.requireNonNull(observer, "observer");
		WorldLocation location = checked.getWorldLocation();
		layeredSpatialEntityIndex.requireMembership(checked, location);
		for (Npc candidate : layeredSpatialEntityIndex.snapshotNpcs(
			getLayeredVisibleRegionWindow(location))) {
			if (candidate.withinRange(checked)) {
				npcs.add(candidate);
			}
		}
		return npcs;
	}

	private Collection<GameObject> getLayeredLocalObjects(final Mob observer) {
		ArrayList<GameObject> objects = new ArrayList<GameObject>();
		int distance = getWorld().getServer().getConfig().OBJECT_VIEW_DISTANCE;
		WorldLocation observerLocation = observer.getWorldLocation();
		layeredSpatialEntityIndex.requireMembership(
			observer, observerLocation);
		for (GameObject candidate : layeredSpatialEntityIndex
				.snapshotGameObjects(getLayeredVisibleRegionWindow(
					observerLocation, distance))
				.getGameObjects()) {
			if (candidate.getLocation().withinGridRange(
					observer.getLocation(), distance)) {
				objects.add(candidate);
			}
		}
		return objects;
	}

	private Collection<GroundItem> getLayeredLocalGroundItems(
		final Mob observer) {
		LinkedHashSet<GroundItem> items = new LinkedHashSet<GroundItem>();
		int distance = getWorld().getServer().getConfig().OBJECT_VIEW_DISTANCE;
		WorldLocation location = observer.getWorldLocation();
		layeredSpatialEntityIndex.requireMembership(observer, location);
		for (GroundItem candidate : layeredSpatialEntityIndex.snapshotGroundItems(
			getLayeredVisibleRegionWindow(location, distance))) {
			if (candidate.sharesSpatialDomain(observer)
				&& candidate.getLocation().withinGridRange(
					observer.getLocation(), distance)) {
				items.add(candidate);
			}
		}
		return items;
	}

	private VisibilitySnapshot buildLayeredVisibilitySnapshot(
		final Mob observer) {
		int mobDistance = getWorld().getServer().getConfig().VIEW_DISTANCE;
		int objectDistance =
			getWorld().getServer().getConfig().OBJECT_VIEW_DISTANCE;
		LayeredSpatialEntityIndex.Snapshot mobSnapshot =
			layeredSpatialSnapshot(observer, mobDistance);
		LayeredSpatialEntityIndex.Snapshot objectSnapshot =
			layeredSpatialSnapshot(observer, objectDistance);
		LinkedHashSet<Player> players = new LinkedHashSet<Player>();
		LinkedHashSet<Npc> npcs = new LinkedHashSet<Npc>();
		LinkedHashSet<GameObject> objects = new LinkedHashSet<GameObject>();
		ArrayList<GameObject> scenery = new ArrayList<GameObject>();
		ArrayList<GameObject> walls = new ArrayList<GameObject>();
		LinkedHashSet<GroundItem> items = new LinkedHashSet<GroundItem>();

		for (Entity candidate : mobSnapshot.getEntities()) {
			if (candidate instanceof Player
				&& ((Player) candidate).withinRange(observer)) {
				players.add((Player) candidate);
			} else if (candidate instanceof Npc
				&& ((Npc) candidate).withinRange(observer)) {
				npcs.add((Npc) candidate);
			}
		}
		for (Entity candidate : objectSnapshot.getEntities()) {
			if (!candidate.sharesSpatialDomain(observer)
				|| !candidate.getLocation().withinGridRange(
					observer.getLocation(), objectDistance)) {
				continue;
			}
			if (candidate instanceof GameObject) {
				GameObject object = (GameObject) candidate;
				if (objects.add(object)) {
					if (object.getType() == 0) {
						scenery.add(object);
					} else if (object.getType() == 1) {
						walls.add(object);
					}
				}
			} else if (candidate instanceof GroundItem) {
				items.add((GroundItem) candidate);
			}
		}

		LayeredSpatialWindowKey key = LayeredSpatialWindowKey.around(
			observer.getWorldLocation(),
			Math.multiplyExact(objectDistance, 8));
		return new VisibilitySnapshot(
			players, npcs, objects, scenery, walls, items,
			Math.toIntExact(mobSnapshot.getWindow().getRegionCount()),
			Math.toIntExact(objectSnapshot.getWindow().getRegionCount()),
			key, objectSnapshot.getObjectVersion());
	}

	private LayeredSpatialEntityIndex.Snapshot layeredSpatialSnapshot(
		final Entity observer,
		final int gridDistance) {
		Entity checked = Objects.requireNonNull(observer, "observer");
		WorldLocation location = checked.getWorldLocation();
		layeredSpatialEntityIndex.requireMembership(checked, location);
		return layeredSpatialEntityIndex.snapshot(
			getLayeredVisibleRegionWindow(location, gridDistance));
	}

	GameObject findLayeredGameObject(
		final Point legacyLocation,
		final Entity observer,
		final GameObjectType type,
		final Integer direction) {
		WorldLocation target = layeredInteractionTarget(
			legacyLocation, observer);
		if (target == null) {
			return null;
		}
		for (Entity candidate : layeredSpatialEntityIndex.snapshot(
			WorldRegionWindow.around(target, 0)).getEntities()) {
			if (candidate instanceof GameObject
				&& target.equals(candidate.getWorldLocation())) {
				GameObject object = (GameObject) candidate;
				if ((type == null || object.getGameObjectType() == type)
					&& (direction == null
						|| object.getDirection() == direction.intValue())
					&& !object.isInvisibleTo(observer)) {
					return object;
				}
			}
		}
		return null;
	}

	Npc findLayeredNpc(
		final Point legacyLocation,
		final Entity observer) {
		WorldLocation target = layeredInteractionTarget(
			legacyLocation, observer);
		if (target == null) {
			return null;
		}
		return layeredSpatialEntityIndex.findNpcAt(target, observer);
	}

	Player findLayeredPlayer(
		final Point legacyLocation,
		final Entity observer,
		final boolean includeSelf) {
		WorldLocation target = layeredInteractionTarget(
			legacyLocation, observer);
		if (target == null) {
			return null;
		}
		return layeredSpatialEntityIndex.findPlayerAt(
			target, observer, includeSelf);
	}

	GroundItem findLayeredGroundItem(
		final int id,
		final Point legacyLocation,
		final Entity observer) {
		WorldLocation target = layeredInteractionTarget(
			legacyLocation, observer);
		if (target == null) {
			return null;
		}
		for (Entity candidate : layeredSpatialEntityIndex.snapshot(
			WorldRegionWindow.around(target, 0)).getEntities()) {
			if (candidate instanceof GroundItem
				&& candidate.getID() == id
				&& target.equals(candidate.getWorldLocation())
				&& !candidate.isInvisibleTo(observer)) {
				return (GroundItem) candidate;
			}
		}
		return null;
	}

	private WorldLocation layeredInteractionTarget(
		final Point legacyLocation,
		final Entity observer) {
		Entity checkedObserver = Objects.requireNonNull(observer, "observer");
		WorldLocation observerLocation = checkedObserver.getWorldLocation();
		layeredSpatialEntityIndex.requireMembership(
			checkedObserver, observerLocation);
		WorldLocation target;
		try {
			target = fromRuntimeCompatibilityPoint(
				Objects.requireNonNull(
					legacyLocation, "legacyLocation"),
				observerLocation,
				false);
		} catch (IllegalArgumentException outsideScope) {
			return null;
		}
		return observerLocation.getWorldSpace().equals(target.getWorldSpace())
				&& observerLocation.getCoordinate().getLevel()
					== target.getCoordinate().getLevel()
			? target : null;
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
	 * Runs one operation only while the exact selected packed-source set remains
	 * resident under the authoritative Region lifecycle monitor.
	 *
	 * <p>The supplied boundary contains detached coordinates only and expires
	 * before the lifecycle monitor is released. Missing or mirror-inconsistent
	 * sources refuse without invoking the operation. No Region is created,
	 * removed, unloaded, or reconstructed.</p>
	 */
	public boolean withinLayeredPackedRegionSourceLifecycleBoundary(
		final LayeredPackedRegionNpcOwnerPreservationRequirements requirements,
		final LayeredPackedRegionSourceLifecycleBoundary.Operation operation) {
		LayeredPackedRegionNpcOwnerPreservationRequirements checked =
			Objects.requireNonNull(requirements, "requirements");
		LayeredPackedRegionSourceLifecycleBoundary.Operation checkedOperation =
			Objects.requireNonNull(operation, "operation");
		if (checked.getSelectedSourceCount() <= 0
			|| checked.getSelectedSourceCount()
				> MAX_LAYERED_PACKED_SOURCES_PER_RETIREMENT_PLAN
			|| checked.getSelectedSources().size()
				!= checked.getSelectedSourceCount()) {
			return false;
		}
		Set<Long> unique = new LinkedHashSet<Long>();
		for (SelectedSource source : checked.getSelectedSources()) {
			if (source == null
				|| !unique.add(Long.valueOf(packRegionCoordinateKey(
					source.getPackedRegionX(),
					source.getPackedRegionY())))) {
				return false;
			}
		}
		synchronized (layeredRegionLifecycleLock) {
			for (SelectedSource source : checked.getSelectedSources()) {
				if (peekRegionFromSectorCoordinates(
						source.getPackedRegionX(),
						source.getPackedRegionY()) == null
					|| !layeredRegionResidencyMirror
						.isPackedRegionRegistered(
							source.getPackedRegionX(),
							source.getPackedRegionY())) {
					return false;
				}
			}
			LayeredPackedRegionSourceLifecycleBoundary boundary =
				LayeredPackedRegionSourceLifecycleBoundary.open(
					checked, layeredRegionResidencyMirror.getVersion(),
					Thread.holdsLock(layeredRegionLifecycleLock));
			try {
				checkedOperation.execute(boundary);
				return true;
			} finally {
				boundary.invalidate();
			}
		}
	}

	/**
	 * Captures exact Region-local absence blockers inside an active packed-source
	 * lifecycle boundary. No absent Region is created and no runtime handle is
	 * retained by the detached result.
	 */
	public LayeredPackedRegionSourceAbsencePreflight
		captureLayeredPackedRegionSourceAbsencePreflight(
			final LayeredPackedRegionSourceLifecycleBoundary boundary) {
		LayeredPackedRegionSourceLifecycleBoundary checked =
			Objects.requireNonNull(boundary, "boundary");
		if (!Thread.holdsLock(layeredRegionLifecycleLock)
			|| !checked.isRegionLifecycleBoundaryHeld()
			|| checked.getSelectedSourceCount() <= 0
			|| checked.getSelectedSourceCount()
				> MAX_LAYERED_PACKED_SOURCES_PER_RETIREMENT_PLAN
			|| checked.getResidencyMirrorVersion()
				!= layeredRegionResidencyMirror.getVersion()) {
			throw new IllegalStateException(
				"Packed-source absence preflight lacks its lifecycle boundary");
		}
		List<LayeredPackedRegionSourceAbsencePreflight.SourceInventory>
			inventories = new ArrayList<
				LayeredPackedRegionSourceAbsencePreflight.SourceInventory>(
					checked.getSelectedSourceCount());
		for (LayeredPackedRegionSourceLifecycleBoundary.PackedSource source
			: checked.getSelectedSources()) {
			Region region = peekRegionFromSectorCoordinates(
				source.getPackedRegionX(), source.getPackedRegionY());
			if (region == null
				|| !layeredRegionResidencyMirror.isPackedRegionRegistered(
					source.getPackedRegionX(), source.getPackedRegionY())) {
				throw new IllegalStateException(
					"Packed source changed inside its lifecycle boundary");
			}
			Region.RetirementContentsSnapshot snapshot =
				region.captureRetirementContentsSnapshot();
			if (snapshot.getCollisionProductTileCount() < 0) {
				throw new IllegalStateException(
					"Packed source has unavailable collision storage");
			}
			inventories.add(
				LayeredPackedRegionSourceAbsencePreflight.SourceInventory.of(
					source.getPackedRegionX(), source.getPackedRegionY(),
					snapshot.isTileStorageAvailable(),
					snapshot.getPlayerCount(), snapshot.getNpcCount(),
					snapshot.getObjectCount(),
					snapshot.getDynamicObjectCount(),
					snapshot.getGroundItemCount(),
					snapshot.getCollisionProductTileCount()));
		}
		return LayeredPackedRegionSourceAbsencePreflight.assess(
			checked, inventories, getWorld().getServer().getCurrentTick(),
			LAYERED_PACKED_REGION_RELOAD_SUPPORTED,
			Thread.holdsLock(layeredRegionLifecycleLock));
	}

	/**
	 * Binds one already captured absence inventory to the immutable authored
	 * reconstruction definitions while the same source lifecycle boundary
	 * remains active.
	 *
	 * <p>The returned recipe is detached and inert. This method neither loads a
	 * missing source nor creates, removes, unloads, reconstructs, or registers a
	 * Region.</p>
	 */
	public LayeredPackedRegionReloadRecipe
		captureLayeredPackedRegionReloadRecipe(
			final LayeredPackedRegionSourceLifecycleBoundary boundary,
			final LayeredPackedRegionSourceAbsencePreflight preflight,
			final LayeredPackedRegionAuthoredReconstructionRecipe
				authoredRecipe) {
		LayeredPackedRegionSourceLifecycleBoundary checkedBoundary =
			Objects.requireNonNull(boundary, "boundary");
		LayeredPackedRegionSourceAbsencePreflight checkedPreflight =
			Objects.requireNonNull(preflight, "preflight");
		LayeredPackedRegionAuthoredReconstructionRecipe checkedRecipe =
			Objects.requireNonNull(authoredRecipe, "authoredRecipe");
		if (!Thread.holdsLock(layeredRegionLifecycleLock)
			|| !checkedBoundary.isRegionLifecycleBoundaryHeld()
			|| checkedBoundary.getResidencyMirrorVersion()
				!= layeredRegionResidencyMirror.getVersion()
			|| checkedBoundary.getGeneration()
				!= checkedRecipe.getGeneration()) {
			throw new IllegalStateException(
				"Packed-source reload recipe lacks its lifecycle boundary");
		}
		for (LayeredPackedRegionSourceLifecycleBoundary.PackedSource source
			: checkedBoundary.getSelectedSources()) {
			if (peekRegionFromSectorCoordinates(
					source.getPackedRegionX(),
					source.getPackedRegionY()) == null
				|| !layeredRegionResidencyMirror.isPackedRegionRegistered(
					source.getPackedRegionX(),
					source.getPackedRegionY())) {
				throw new IllegalStateException(
					"Packed source changed before reload recipe capture");
			}
		}
		return LayeredPackedRegionReloadRecipe.compose(
			checkedBoundary, checkedPreflight, checkedRecipe,
			Thread.holdsLock(layeredRegionLifecycleLock));
	}

	/**
	 * Captures a bounded authored-object census from the exact resident source
	 * set while its lifecycle boundary remains active. Shared live collision
	 * tiles are deliberately not inspected.
	 */
	public LayeredPackedRegionRuntimeAuthoredObjectObservation
		captureLayeredPackedRegionRuntimeAuthoredObjects(
			final LayeredPackedRegionSourceLifecycleBoundary boundary,
			final LayeredPackedRegionReloadRecipe reloadRecipe,
			final int maximumObjectInstances) {
		LayeredPackedRegionSourceLifecycleBoundary checkedBoundary =
			Objects.requireNonNull(boundary, "boundary");
		LayeredPackedRegionReloadRecipe checkedRecipe =
			Objects.requireNonNull(reloadRecipe, "reloadRecipe");
		if (!Thread.holdsLock(layeredRegionLifecycleLock)
			|| !checkedBoundary.isRegionLifecycleBoundaryHeld()
			|| checkedBoundary.getGeneration()
				!= checkedRecipe.getGeneration()
			|| checkedBoundary.getRequirementsObservedAtTick()
				!= checkedRecipe.getRequirementsObservedAtTick()
			|| checkedBoundary.getResidencyMirrorVersion()
				!= checkedRecipe.getResidencyMirrorVersion()
			|| checkedBoundary.getSelectedSourceCount()
				!= checkedRecipe.getSourceCount()
			|| checkedBoundary.getResidencyMirrorVersion()
				!= layeredRegionResidencyMirror.getVersion()) {
			throw new IllegalStateException(
				"Runtime authored-object observation lacks its lifecycle boundary");
		}
		List<LayeredPackedRegionRuntimeAuthoredObjectObservation.SourceCapture>
			captures = new ArrayList<
				LayeredPackedRegionRuntimeAuthoredObjectObservation
					.SourceCapture>(checkedBoundary.getSelectedSourceCount());
		for (int index = 0;
			index < checkedBoundary.getSelectedSourceCount(); index++) {
			LayeredPackedRegionSourceLifecycleBoundary.PackedSource source =
				checkedBoundary.getSelectedSources().get(index);
			LayeredPackedRegionReloadRecipe.SourceRecipe sourceRecipe =
				checkedRecipe.getSources().get(index);
			Region region = peekRegionFromSectorCoordinates(
				source.getPackedRegionX(), source.getPackedRegionY());
			if (region == null
				|| source.getPackedRegionX()
					!= sourceRecipe.getPackedRegionX()
				|| source.getPackedRegionY()
					!= sourceRecipe.getPackedRegionY()
				|| !layeredRegionResidencyMirror.isPackedRegionRegistered(
					source.getPackedRegionX(), source.getPackedRegionY())) {
				throw new IllegalStateException(
					"Runtime authored-object source changed inside its boundary");
			}
			captures.add(region.captureRuntimeAuthoredObjectSource());
		}
		return LayeredPackedRegionRuntimeAuthoredObjectObservation.observe(
			checkedRecipe, getWorld().getServer().getCurrentTick(),
			captures, maximumObjectInstances);
	}

	/**
	 * Copies one exact resident source's canonical tile states while its
	 * lifecycle boundary remains active.
	 *
	 * <p>The immutable result contains no Region or TileValue handles. It is a
	 * transient full-fidelity input whose dynamic products must be removed by a
	 * terrain-only plan before any future initialization stage.</p>
	 */
	public List<LayeredTileState>
		captureLayeredPackedRegionTerrainTileStates(
			final LayeredPackedRegionSourceLifecycleBoundary boundary,
			final int sourceOrdinal) {
		LayeredPackedRegionSourceLifecycleBoundary checked =
			Objects.requireNonNull(boundary, "boundary");
		if (!Thread.holdsLock(layeredRegionLifecycleLock)
			|| !checked.isRegionLifecycleBoundaryHeld()
			|| checked.getResidencyMirrorVersion()
				!= layeredRegionResidencyMirror.getVersion()
			|| sourceOrdinal < 0
			|| sourceOrdinal >= checked.getSelectedSourceCount()) {
			throw new IllegalStateException(
				"Terrain tile capture lacks its exact lifecycle boundary");
		}
		LayeredPackedRegionSourceLifecycleBoundary.PackedSource source =
			checked.getSelectedSources().get(sourceOrdinal);
		Region region = peekRegionFromSectorCoordinates(
			source.getPackedRegionX(), source.getPackedRegionY());
		if (region == null
			|| !layeredRegionResidencyMirror.isPackedRegionRegistered(
				source.getPackedRegionX(), source.getPackedRegionY())) {
			throw new IllegalStateException(
				"Packed source changed before terrain tile capture");
		}
		List<LayeredTileState> states =
			new ArrayList<LayeredTileState>(
				Constants.REGION_SIZE * Constants.REGION_SIZE);
		for (int localX = 0;
			localX < Constants.REGION_SIZE; localX++) {
			for (int localY = 0;
				localY < Constants.REGION_SIZE; localY++) {
				states.add(LayeredTileState.fromLegacy(
					region.getTileValue(localX, localY)));
			}
		}
		return Collections.unmodifiableList(states);
	}

	/** Opens one dormant owner and atomically assigns its first logical window. */
	public LayeredRegionInterestOwnershipLedger.OpenedOwner
		openLayeredRegionInterestOwner(final WorldRegionWindow currentWindow) {
		synchronized (layeredRegionLifecycleLock) {
			LayeredRegionInterestOwnershipLedger.OpenedOwner openedOwner =
				layeredRegionInterestOwnershipLedger.openOwner(
				currentWindow, MAX_LAYERED_REGIONS_PER_INTEREST_OWNER);
			layeredRegionRetirementEligibilityLedger.observeOwnershipChange(
				openedOwner.getChange(), getWorld().getServer().getCurrentTick());
			return openedOwner;
		}
	}

	/** Replaces one dormant owner's complete logical interest window. */
	public LayeredRegionInterestOwnershipLedger.Change
		synchronizeLayeredRegionInterestOwner(
			final LayeredRegionInterestOwnershipLedger.OwnerToken ownerToken,
		final WorldRegionWindow currentWindow) {
		synchronized (layeredRegionLifecycleLock) {
			LayeredRegionInterestOwnershipLedger.Change change =
				layeredRegionInterestOwnershipLedger.synchronizeOwner(
				ownerToken, currentWindow,
				MAX_LAYERED_REGIONS_PER_INTEREST_OWNER);
			layeredRegionRetirementEligibilityLedger.observeOwnershipChange(
				change, getWorld().getServer().getCurrentTick());
			return change;
		}
	}

	/** Closes one dormant owner; repeated cleanup remains idempotent. */
	public LayeredRegionInterestOwnershipLedger.Change
		closeLayeredRegionInterestOwner(
		final LayeredRegionInterestOwnershipLedger.OwnerToken ownerToken) {
		synchronized (layeredRegionLifecycleLock) {
			LayeredRegionInterestOwnershipLedger.Change change =
				layeredRegionInterestOwnershipLedger.closeOwner(ownerToken);
			layeredRegionRetirementEligibilityLedger.observeOwnershipChange(
				change, getWorld().getServer().getCurrentTick());
			return change;
		}
	}

	/** Returns one checked owner snapshot without exposing other owner handles. */
	public LayeredRegionInterestOwnershipLedger.OwnerSnapshot
		getLayeredRegionInterestOwnerSnapshot(
			final LayeredRegionInterestOwnershipLedger.OwnerToken ownerToken) {
		synchronized (layeredRegionLifecycleLock) {
			return layeredRegionInterestOwnershipLedger.snapshotOwner(ownerToken);
		}
	}

	/** Returns one global logical-interest count without changing residency. */
	public LayeredRegionInterestOwnershipLedger.Snapshot
		getLayeredRegionInterestOwnershipSnapshot(
			final WorldRegionKey logicalRegionKey) {
		synchronized (layeredRegionLifecycleLock) {
			return layeredRegionInterestOwnershipLedger.snapshot(logicalRegionKey);
		}
	}

	/**
	 * Returns conservative pin/cooldown evidence without changing Region
	 * residency. Retirement eligibility is not an unload or eviction order.
	 */
	public LayeredRegionRetirementEligibilityLedger.Snapshot
		getLayeredRegionRetirementEligibilitySnapshot(
			final WorldRegionKey logicalRegionKey) {
		synchronized (layeredRegionLifecycleLock) {
			LayeredRegionInterestOwnershipLedger.Snapshot ownership =
				layeredRegionInterestOwnershipLedger.snapshot(logicalRegionKey);
			LayeredRegionResidencyMirror.Snapshot residency =
				requireLayeredRegionResidencySnapshot(logicalRegionKey);
			return layeredRegionRetirementEligibilityLedger.snapshot(
				ownership, residency, getWorld().getServer().getCurrentTick());
		}
	}

	/**
	 * Captures one bounded, same-tick retirement-evidence batch. The returned
	 * snapshots remain observations only and cannot change Region lifecycle.
	 */
	public List<LayeredRegionRetirementEligibilityLedger.Snapshot>
		getLayeredRegionRetirementEligibilitySnapshots(
			final List<WorldRegionKey> logicalRegionKeys,
			final int maximumRegions) {
		if (logicalRegionKeys == null) {
			throw new NullPointerException("logicalRegionKeys");
		}
		if (maximumRegions < 0 || logicalRegionKeys.size() > maximumRegions) {
			throw new IllegalArgumentException(
				"Retirement evidence exceeds the diagnostic Region budget");
		}
		LinkedHashSet<WorldRegionKey> uniqueKeys =
			new LinkedHashSet<WorldRegionKey>(logicalRegionKeys);
		if (uniqueKeys.size() != logicalRegionKeys.size()
			|| uniqueKeys.contains(null)) {
			throw new IllegalArgumentException(
				"Retirement evidence Region keys must be non-null and unique");
		}
		synchronized (layeredRegionLifecycleLock) {
			long currentTick = getWorld().getServer().getCurrentTick();
			List<LayeredRegionRetirementEligibilityLedger.Snapshot> snapshots =
				new ArrayList<LayeredRegionRetirementEligibilityLedger.Snapshot>(
					logicalRegionKeys.size());
			for (WorldRegionKey logicalRegionKey : logicalRegionKeys) {
				snapshots.add(layeredRegionRetirementEligibilityLedger.snapshot(
					layeredRegionInterestOwnershipLedger.snapshot(logicalRegionKey),
					requireLayeredRegionResidencySnapshot(logicalRegionKey),
					currentTick));
			}
			return Collections.unmodifiableList(snapshots);
		}
	}

	/**
	 * Atomically rechecks one earlier retirement candidate against current
	 * ownership, residency, release, and cooldown state. The result is dormant
	 * evidence only and cannot change packed Region lifecycle.
	 */
	public LayeredRegionRetirementDecisionArbiter.Decision
		evaluateLayeredRegionRetirementCandidate(
			final LayeredRegionRetirementEligibilityLedger.Snapshot candidate) {
		LayeredRegionRetirementEligibilityLedger.Snapshot checkedCandidate =
			Objects.requireNonNull(candidate, "candidate");
		synchronized (layeredRegionLifecycleLock) {
			return evaluateLayeredRegionRetirementCandidateLocked(
				checkedCandidate, getWorld().getServer().getCurrentTick());
		}
	}

	/**
	 * Atomically rechecks one bounded, unique candidate batch at one server tick.
	 * No candidate is retained, consumed, unloaded, unregistered, or evicted.
	 */
	public List<LayeredRegionRetirementDecisionArbiter.Decision>
		evaluateLayeredRegionRetirementCandidates(
			final List<LayeredRegionRetirementEligibilityLedger.Snapshot> candidates,
			final int maximumRegions) {
		if (candidates == null) {
			throw new NullPointerException("candidates");
		}
		if (maximumRegions < 0
			|| maximumRegions > MAX_LAYERED_REGIONS_PER_INTEREST_OWNER
			|| candidates.size() > maximumRegions) {
			throw new IllegalArgumentException(
				"Retirement decisions exceed the candidate Region budget");
		}
		LinkedHashSet<WorldRegionKey> uniqueKeys =
			new LinkedHashSet<WorldRegionKey>();
		for (LayeredRegionRetirementEligibilityLedger.Snapshot candidate
			: candidates) {
			if (candidate == null
				|| !uniqueKeys.add(candidate.getLogicalRegionKey())) {
				throw new IllegalArgumentException(
					"Retirement decision candidates must be non-null and unique");
			}
		}
		synchronized (layeredRegionLifecycleLock) {
			long currentTick = getWorld().getServer().getCurrentTick();
			List<LayeredRegionRetirementDecisionArbiter.Decision> decisions =
				new ArrayList<LayeredRegionRetirementDecisionArbiter.Decision>(
					candidates.size());
			for (LayeredRegionRetirementEligibilityLedger.Snapshot candidate
				: candidates) {
				decisions.add(evaluateLayeredRegionRetirementCandidateLocked(
					candidate, currentTick));
			}
			return Collections.unmodifiableList(decisions);
		}
	}

	private LayeredRegionRetirementDecisionArbiter.Decision
		evaluateLayeredRegionRetirementCandidateLocked(
			final LayeredRegionRetirementEligibilityLedger.Snapshot candidate,
			final long currentTick) {
		WorldRegionKey key = candidate.getLogicalRegionKey();
		LayeredRegionRetirementEligibilityLedger.Snapshot current =
			layeredRegionRetirementEligibilityLedger.snapshot(
				layeredRegionInterestOwnershipLedger.snapshot(key),
				requireLayeredRegionResidencySnapshot(key), currentTick);
		return layeredRegionRetirementDecisionArbiter.evaluate(candidate, current);
	}

	/**
	 * Atomically rechecks one bounded candidate batch and aggregates its logical
	 * decisions into dormant packed-source readiness. The result has no Region
	 * handle and cannot unload, unregister, remove, or evict packed storage.
	 */
	public LayeredPackedRegionRetirementReadiness
		prepareLayeredPackedRegionRetirementReadiness(
			final List<LayeredRegionRetirementEligibilityLedger.Snapshot> candidates,
			final int maximumRegions) {
		if (candidates == null) {
			throw new NullPointerException("candidates");
		}
		if (maximumRegions < 0
			|| maximumRegions > MAX_LAYERED_REGIONS_PER_INTEREST_OWNER
			|| candidates.size() > maximumRegions) {
			throw new IllegalArgumentException(
				"Packed retirement readiness exceeds the candidate Region budget");
		}
		LinkedHashSet<WorldRegionKey> uniqueKeys =
			new LinkedHashSet<WorldRegionKey>();
		for (LayeredRegionRetirementEligibilityLedger.Snapshot candidate
			: candidates) {
			if (candidate == null
				|| !uniqueKeys.add(candidate.getLogicalRegionKey())) {
				throw new IllegalArgumentException(
					"Packed retirement candidates must be non-null and unique");
			}
		}
		synchronized (layeredRegionLifecycleLock) {
			long currentTick = getWorld().getServer().getCurrentTick();
			List<LayeredRegionRetirementDecisionArbiter.Decision> decisions =
				new ArrayList<LayeredRegionRetirementDecisionArbiter.Decision>(
					candidates.size());
			for (LayeredRegionRetirementEligibilityLedger.Snapshot candidate
				: candidates) {
				decisions.add(evaluateLayeredRegionRetirementCandidateLocked(
					candidate, currentTick));
			}
			return LayeredPackedRegionRetirementReadiness.fromDecisions(
				decisions, maximumRegions,
				MAX_LAYERED_PACKED_SOURCES_PER_RETIREMENT_PLAN);
		}
	}

	/**
	 * Captures read-only contents and quiescence evidence for one bounded
	 * packed-source readiness value. Counts may become stale immediately; this
	 * method cannot claim, unload, unregister, remove, or evict a Region.
	 */
	public LayeredPackedRegionRetirementSafetyAssessment
		assessLayeredPackedRegionRetirementSafety(
			final LayeredPackedRegionRetirementReadiness readiness,
			final int maximumPackedSources) {
		LayeredPackedRegionRetirementReadiness checked =
			Objects.requireNonNull(readiness, "readiness");
		if (maximumPackedSources < 0
			|| maximumPackedSources
				> MAX_LAYERED_PACKED_SOURCES_PER_RETIREMENT_PLAN
			|| checked.getSourceCount() > maximumPackedSources) {
			throw new IllegalArgumentException(
				"Packed retirement assessment exceeds the source budget");
		}
		synchronized (layeredRegionLifecycleLock) {
			List<LayeredPackedRegionRetirementSafetyAssessment.PackedSourceContents>
				contents = new ArrayList<LayeredPackedRegionRetirementSafetyAssessment
					.PackedSourceContents>(checked.getSourceCount());
			for (LayeredPackedRegionRetirementReadiness.SourceReadiness source
				: checked.getSources()) {
				Region region = peekRegionFromSectorCoordinates(
					source.getPackedRegionX(), source.getPackedRegionY());
				Region.RetirementContentsSnapshot snapshot = region == null
					? null : region.captureRetirementContentsSnapshot();
				contents.add(LayeredPackedRegionRetirementSafetyAssessment
					.PackedSourceContents.of(
						source.getPackedRegionX(), source.getPackedRegionY(),
						region != null,
						snapshot != null && snapshot.isTileStorageAvailable(),
						LAYERED_PACKED_REGION_RELOAD_SUPPORTED,
						snapshot == null ? 0 : snapshot.getPlayerCount(),
						snapshot == null ? 0 : snapshot.getNpcCount(),
						snapshot == null ? 0 : snapshot.getObjectCount(),
						snapshot == null ? 0 : snapshot.getGroundItemCount()));
			}
			return LayeredPackedRegionRetirementSafetyAssessment.assess(
				checked, contents, getWorld().getServer().getCurrentTick(),
				maximumPackedSources);
		}
	}

	/**
	 * Observes an exact refinement candidate set without loading absent Regions
	 * or manufacturing logical retirement/readiness evidence.
	 */
	public LayeredPackedRegionRetirementSafetyAssessment
		assessLayeredPackedRegionRetirementRefinementCandidates(
			final LayeredPackedRegionRetirementRefinementProposal proposal,
			final int maximumPackedSources) {
		LayeredPackedRegionRetirementRefinementProposal checked =
			Objects.requireNonNull(proposal, "proposal");
		if (maximumPackedSources < 0
			|| maximumPackedSources
				> MAX_LAYERED_PACKED_SOURCES_PER_RETIREMENT_PLAN
			|| checked.getCandidateSourceCount() > maximumPackedSources) {
			throw new IllegalArgumentException(
				"Refinement candidate assessment exceeds the source budget");
		}
		synchronized (layeredRegionLifecycleLock) {
			return assessLayeredPackedRegionRetirementRefinementCandidatesLocked(
				checked, maximumPackedSources,
				getWorld().getServer().getCurrentTick());
		}
	}

	/**
	 * Captures one bounded, read-only preservation burden inventory from the
	 * exact proposal order. Absent Regions stay absent and event ownership stays
	 * unavailable; this method cannot load, preserve, reload, or retire anything.
	 */
	public LayeredPackedRegionPreservationBurdenAssessment
		assessLayeredPackedRegionPreservationBurden(
			final LayeredPackedRegionRetirementRefinementProposal proposal,
			final int maximumPackedSources) {
		LayeredPackedRegionRetirementRefinementProposal checked =
			Objects.requireNonNull(proposal, "proposal");
		if (maximumPackedSources < 0
			|| maximumPackedSources
				> MAX_LAYERED_PACKED_SOURCES_PER_RETIREMENT_PLAN
			|| checked.getCandidateSourceCount() > maximumPackedSources) {
			throw new IllegalArgumentException(
				"Preservation burden assessment exceeds the source budget");
		}
		synchronized (layeredRegionLifecycleLock) {
			long observedAtTick = getWorld().getServer().getCurrentTick();
			List<LayeredPackedRegionRetirementSafetyAssessment.PackedSourceContents>
				contents = new ArrayList<LayeredPackedRegionRetirementSafetyAssessment
					.PackedSourceContents>(checked.getCandidateSourceCount());
			List<LayeredPackedRegionPreservationBurdenAssessment
				.PackedSourceInventory> inventories = new ArrayList<
					LayeredPackedRegionPreservationBurdenAssessment
						.PackedSourceInventory>(checked.getCandidateSourceCount());
			for (LayeredPackedRegionRetirementRefinementProposal.CandidateSource
				candidate : checked.getCandidates()) {
				Region region = peekRegionFromSectorCoordinates(
					candidate.getPackedRegionX(), candidate.getPackedRegionY());
				Region.RetirementContentsSnapshot snapshot = region == null
					? null : region.captureRetirementContentsSnapshot();
				contents.add(LayeredPackedRegionRetirementSafetyAssessment
					.PackedSourceContents.of(
						candidate.getPackedRegionX(), candidate.getPackedRegionY(),
						region != null,
						snapshot != null && snapshot.isTileStorageAvailable(),
						LAYERED_PACKED_REGION_RELOAD_SUPPORTED,
						snapshot == null ? 0 : snapshot.getPlayerCount(),
						snapshot == null ? 0 : snapshot.getNpcCount(),
						snapshot == null ? 0 : snapshot.getObjectCount(),
						snapshot == null ? 0 : snapshot.getGroundItemCount()));
				inventories.add(LayeredPackedRegionPreservationBurdenAssessment
					.currentRuntimeInventory(
						candidate.getPackedRegionX(), candidate.getPackedRegionY(),
						snapshot == null ? 0 : snapshot.getPlayerCount(),
						snapshot == null ? 0 : snapshot.getDynamicObjectCount(),
						snapshot == null ? 0 : snapshot.getGroundItemCount(),
						snapshot == null
							? -1 : snapshot.getCollisionProductTileCount()));
			}
			LayeredPackedRegionRetirementSafetyAssessment safety =
				LayeredPackedRegionRetirementSafetyAssessment
					.assessDiagnosticSelection(
						contents, observedAtTick, maximumPackedSources);
			return LayeredPackedRegionPreservationBurdenAssessment.assess(
				safety, inventories, observedAtTick, maximumPackedSources);
		}
	}

	/**
	 * Detaches bounded constructor-state records for every identity-less object
	 * in the exact proposal order. Opaque attributes and external event ownership
	 * remain explicitly outside the record; no entity is removed or retained.
	 */
	public LayeredPackedRegionDynamicObjectPreservationRecord
		captureLayeredPackedRegionDynamicObjectPreservationRecord(
			final LayeredPackedRegionRetirementRefinementProposal proposal,
			final int maximumPackedSources,
			final int maximumDynamicObjects) {
		LayeredPackedRegionRetirementRefinementProposal checked =
			Objects.requireNonNull(proposal, "proposal");
		if (maximumPackedSources < 0
			|| maximumPackedSources
				> MAX_LAYERED_PACKED_SOURCES_PER_RETIREMENT_PLAN
			|| checked.getCandidateSourceCount() > maximumPackedSources
			|| maximumDynamicObjects < 0
			|| maximumDynamicObjects
				> LayeredPackedRegionDynamicObjectPreservationRecord
					.MAXIMUM_DYNAMIC_OBJECTS) {
			throw new IllegalArgumentException(
				"Dynamic-object preservation capture exceeds its budget");
		}
		synchronized (layeredRegionLifecycleLock) {
			long observedAtTick = getWorld().getServer().getCurrentTick();
			List<LayeredPackedRegionDynamicObjectPreservationRecord
				.PackedSourceCapture> captures = new ArrayList<
					LayeredPackedRegionDynamicObjectPreservationRecord
						.PackedSourceCapture>(checked.getCandidateSourceCount());
			int capturedObjectCount = 0;
			for (LayeredPackedRegionRetirementRefinementProposal.CandidateSource
				candidate : checked.getCandidates()) {
				Region region = peekRegionFromSectorCoordinates(
					candidate.getPackedRegionX(), candidate.getPackedRegionY());
				Region.RetirementContentsSnapshot snapshot = region == null
					? null : region.captureRetirementContentsSnapshot();
				List<LayeredPackedRegionDynamicObjectPreservationRecord
					.DynamicObjectState> objects = new ArrayList<
						LayeredPackedRegionDynamicObjectPreservationRecord
							.DynamicObjectState>();
				if (snapshot != null) {
					for (Region.DynamicObjectSnapshot object
						: snapshot.getDynamicObjects()) {
						if (object.getX() / Constants.REGION_SIZE
								!= candidate.getPackedRegionX()
							|| object.getY() / Constants.REGION_SIZE
								!= candidate.getPackedRegionY()) {
							throw new IllegalStateException(
								"Dynamic object escaped its Region snapshot");
						}
						capturedObjectCount = Math.addExact(
							capturedObjectCount, 1);
						if (capturedObjectCount > maximumDynamicObjects) {
							throw new IllegalArgumentException(
								"Dynamic-object preservation capture exceeds its object budget");
						}
						objects.add(LayeredPackedRegionDynamicObjectPreservationRecord
							.DynamicObjectState.of(
								object.getObjectId(), object.getPermanentObjectId(),
								object.getX(), object.getY(), object.getDirection(),
								object.getType(), object.getOwner(),
								object.getRuntimeAttributeCount()));
					}
				}
				captures.add(LayeredPackedRegionDynamicObjectPreservationRecord
					.PackedSourceCapture.of(
						candidate.getPackedRegionX(), candidate.getPackedRegionY(),
						region != null, objects));
			}
			return LayeredPackedRegionDynamicObjectPreservationRecord.record(
				checked.getGeneration(), observedAtTick, captures,
				maximumPackedSources, maximumDynamicObjects);
		}
	}

	/**
	 * Captures bounded exact-slot evidence for every known restoration record.
	 *
	 * <p>The event inventory is already detached. Region-local object monitors
	 * provide one exact-slot copy at a time; the result deliberately does not
	 * claim atomicity with the earlier scheduler snapshot. No entity handle is
	 * returned and no object, event, callback, or Region is changed.</p>
	 */
	public LayeredPackedRegionEventTargetObservation
		captureLayeredPackedRegionEventTargetObservation(
			final LayeredPackedRegionEventOwnershipInventory inventory,
			final int maximumTargetRecords) {
		LayeredPackedRegionEventOwnershipInventory checked =
			Objects.requireNonNull(inventory, "inventory");
		if (maximumTargetRecords < 0
			|| maximumTargetRecords
				> LayeredPackedRegionEventTargetObservation
					.MAXIMUM_TARGET_RECORDS
			|| checked.getRestorationStateAvailableEventCount()
				> maximumTargetRecords) {
			throw new IllegalArgumentException(
				"Event target observation exceeds its record budget");
		}
		synchronized (layeredRegionLifecycleLock) {
			long targetObservedAtTick =
				getWorld().getServer().getCurrentTick();
			List<LayeredPackedRegionEventTargetObservation.TargetRecord>
				targets = new ArrayList<
					LayeredPackedRegionEventTargetObservation.TargetRecord>(
						checked.getRestorationStateAvailableEventCount());
			for (LayeredPackedRegionEventOwnershipInventory.EventRecord event
				: checked.getEvents()) {
				LayeredPackedRegionEventOwnershipInventory.EventRestorationState
					restoration = event.getRestorationState();
				if (restoration.getKind()
					== LayeredPackedRegionEventOwnershipInventory
						.RestorationKind.UNAVAILABLE) {
					continue;
				}
				LayeredPackedRegionEventOwnershipInventory
					.SceneryRestorationState scenery = restoration.getScenery();
				Region region = peekRegionFromSectorCoordinates(
					scenery.getX() / WorldRegionKey.REGION_SIZE,
					scenery.getY() / WorldRegionKey.REGION_SIZE);
				LayeredPackedRegionEventOwnershipInventory
					.AuthoredPlacementRestorationState authored =
						scenery.getAuthoredPlacement();
				int slotObjectCount = 0;
				int exactRestorationSceneryCount = 0;
				int exactAuthoredIdentityCount = 0;
				boolean objectBoundaryHeldDuringClassification = false;
				LayeredPackedRegionEventTargetObservation.ObservedTargetState
					observedTargetState = LayeredPackedRegionEventTargetObservation
						.ObservedTargetState.UNAVAILABLE;
				if (region != null) {
					Region.RestorationTargetMatchRequirement requirement =
						Region.RestorationTargetMatchRequirement.of(
							scenery.getObjectId(), scenery.getPermanentObjectId(),
							scenery.getX(), scenery.getY(), scenery.getDirection(),
							scenery.getType(), scenery.getOwner(),
							scenery.getRuntimeAttributeCount(),
							authored == null ? 0L : authored.getGeneration(),
							authored == null ? -1 : authored.getPackedRegionX(),
							authored == null ? -1 : authored.getPackedRegionY(),
							authored == null ? 0 : authored.getSourceOrdinal(),
							authored == null ? null
								: authored.getConstructionKind().name());
					Region.RestorationTargetBoundarySnapshot boundary =
						region.captureRestorationTargetBoundarySnapshot(
							requirement, restoration.isTargetBindingComplete());
					slotObjectCount = boundary.getSlotObjectCount();
					exactRestorationSceneryCount =
						boundary.getExactRestorationSceneryCount();
					exactAuthoredIdentityCount =
						boundary.getExactAuthoredIdentityCount();
					objectBoundaryHeldDuringClassification =
						boundary.isObjectBoundaryHeldDuringClassification();
					observedTargetState = LayeredPackedRegionEventTargetObservation
						.ObservedTargetState.valueOf(
							boundary.getObservedTargetState().name());
				}
				GameTickEventRestorationTargetDecision decision =
					GameTickEventRestorationTargetDecision.decideDetached(
						targetOperation(restoration.getKind()),
						restoration.isTargetBindingComplete(),
						authored == null ? 0L : authored.getGeneration(),
						checked.getProposalGeneration(),
						GameTickEventRestorationTargetDecision
							.ObservedTargetState.valueOf(
								observedTargetState.name()));
				targets.add(
					LayeredPackedRegionEventTargetObservation.TargetRecord
						.observe(
							event.getSnapshotOrdinal(),
							event.getRegistrationSequence(),
							scenery.getX(), scenery.getY(), region != null,
							slotObjectCount, exactRestorationSceneryCount,
							exactAuthoredIdentityCount,
							restoration.isTargetBindingComplete(),
							objectBoundaryHeldDuringClassification,
							LayeredPackedRegionEventTargetObservation.Outcome
								.valueOf(decision.getOutcome().name()),
							LayeredPackedRegionEventTargetObservation.Reason
								.valueOf(decision.getReason().name())));
			}
			return LayeredPackedRegionEventTargetObservation.observation(
				checked.getProposalGeneration(), checked.getObservedAtTick(),
				targetObservedAtTick, checked.getSchedulerInstanceIdentity(),
				targets, maximumTargetRecords);
		}
	}

	/**
	 * Revalidates one scheduler-fenced restoration target inside its real Region
	 * object boundary. The supplied request and returned value are detached;
	 * this method does not retain a scheduler/event handle or alter the target.
	 */
	public GameTickEventRestorationTargetRevalidation
		captureGameTickEventRestorationTargetRevalidation(
			final GameTickEventRestorationTargetRevalidationRequest request) {
		GameTickEventRestorationTargetRevalidationRequest checked =
			Objects.requireNonNull(request, "request");
		synchronized (layeredRegionLifecycleLock) {
			Region region = peekRegionFromSectorCoordinates(
				checked.getX() / WorldRegionKey.REGION_SIZE,
				checked.getY() / WorldRegionKey.REGION_SIZE);
			int slotObjectCount = 0;
			int exactRestorationSceneryCount = 0;
			int exactAuthoredIdentityCount = 0;
			boolean objectBoundaryHeldDuringClassification = false;
			GameTickEventRestorationTargetDecision.ObservedTargetState
				observedTargetState = GameTickEventRestorationTargetDecision
					.ObservedTargetState.UNAVAILABLE;
			if (region != null) {
				Region.RestorationTargetMatchRequirement requirement =
					Region.RestorationTargetMatchRequirement.of(
						checked.getObjectId(), checked.getPermanentObjectId(),
						checked.getX(), checked.getY(), checked.getDirection(),
						checked.getType(), null, 0,
						checked.getAuthoredGeneration(),
						checked.getAuthoredPackedRegionX(),
						checked.getAuthoredPackedRegionY(),
						checked.getAuthoredSourceOrdinal(),
						checked.getAuthoredConstructionKind());
				Region.RestorationTargetBoundarySnapshot boundary =
					region.captureRestorationTargetBoundarySnapshot(
						requirement, checked.isTargetBindingComplete());
				slotObjectCount = boundary.getSlotObjectCount();
				exactRestorationSceneryCount =
					boundary.getExactRestorationSceneryCount();
				exactAuthoredIdentityCount =
					boundary.getExactAuthoredIdentityCount();
				objectBoundaryHeldDuringClassification =
					boundary.isObjectBoundaryHeldDuringClassification();
				observedTargetState = GameTickEventRestorationTargetDecision
					.ObservedTargetState.valueOf(
						boundary.getObservedTargetState().name());
			}
			GameTickEventRestorationTargetDecision decision =
				GameTickEventRestorationTargetDecision.decideDetached(
					checked.getTargetOperation(),
					checked.isTargetBindingComplete(),
					checked.getAuthoredGeneration(),
					checked.getProposalGeneration(), observedTargetState);
			GameTickEventRestorationAtomicRevalidationContract contract =
				GameTickEventRestorationAtomicRevalidationContract.evaluate(
					GameTickEventRestorationAtomicRevalidationContract
						.BoundaryDeclaration.declare(
							checked.getSchedulerInstanceIdentity(),
							checked.getSchedulerInstanceIdentity(),
							checked.getRegistrationSequence(),
							checked.getRegistrationSequence(),
							checked.getProposalGeneration(),
							checked.getAuthoredGeneration(),
							checked.isEventExecutionBoundaryHeld(),
							checked.isSchedulerStoreBoundaryHeld(),
							checked
								.isRegistrationValidatedBeforeRegionBoundary(),
							objectBoundaryHeldDuringClassification,
							region != null),
					decision);
			return GameTickEventRestorationTargetRevalidation.observe(
				region != null, slotObjectCount,
				exactRestorationSceneryCount, exactAuthoredIdentityCount,
				observedTargetState,
				objectBoundaryHeldDuringClassification, decision, contract);
		}
	}

	private static TargetOperation targetOperation(
		final LayeredPackedRegionEventOwnershipInventory.RestorationKind kind) {
		switch (kind) {
			case SCENERY_SPAWN:
				return TargetOperation.SCENERY_SPAWN;
			case SCENERY_REMOVE:
				return TargetOperation.SCENERY_REMOVE;
			default:
				return TargetOperation.UNAVAILABLE;
		}
	}

	private static boolean matchesRestorationScenery(
		final Region.RestorationTargetObjectSnapshot object,
		final LayeredPackedRegionEventOwnershipInventory
			.SceneryRestorationState scenery) {
		return object.getObjectId() == scenery.getObjectId()
			&& object.getPermanentObjectId()
				== scenery.getPermanentObjectId()
			&& object.getX() == scenery.getX()
			&& object.getY() == scenery.getY()
			&& object.getDirection() == scenery.getDirection()
			&& object.getType() == scenery.getType()
			&& Objects.equals(object.getOwner(), scenery.getOwner())
			&& object.getRuntimeAttributeCount()
				== scenery.getRuntimeAttributeCount();
	}

	private static boolean matchesAuthoredIdentity(
		final Region.RestorationTargetObjectSnapshot object,
		final LayeredPackedRegionEventOwnershipInventory
			.SceneryRestorationState scenery) {
		LayeredPackedRegionEventOwnershipInventory
			.AuthoredPlacementRestorationState authored =
				scenery.getAuthoredPlacement();
		return authored != null && object.hasAuthoredIdentity()
			&& object.getAuthoredGeneration() == authored.getGeneration()
			&& object.getAuthoredPackedRegionX()
				== authored.getPackedRegionX()
			&& object.getAuthoredPackedRegionY()
				== authored.getPackedRegionY()
			&& object.getAuthoredSourceOrdinal()
				== authored.getSourceOrdinal()
			&& object.getAuthoredConstructionKind().equals(
				authored.getConstructionKind().name());
	}

	/**
	 * Captures one strictly newer, same-tick refinement reassessment. A null
	 * result means the server tick has not advanced yet; no evidence is sampled.
	 */
	public LayeredPackedRegionRetirementRefinementReassessment
		captureLayeredPackedRegionRetirementRefinementReassessmentIfFresh(
			final LayeredPackedRegionRetirementRefinementProposal previousProposal,
			final LayeredPackedRegionAuthoredReconstructionRecipe recipe,
			final int maximumCandidateSources,
			final int maximumSupportSources,
			final int maximumNpcInstances,
			final int maximumRelevantNpcDetails,
			final int maximumActiveNpcRequirements) {
		LayeredPackedRegionRetirementRefinementProposal previous =
			Objects.requireNonNull(previousProposal, "previousProposal");
		LayeredPackedRegionAuthoredReconstructionRecipe checkedRecipe =
			Objects.requireNonNull(recipe, "recipe");
		if (maximumCandidateSources < 0
			|| maximumCandidateSources
				> MAX_LAYERED_PACKED_SOURCES_PER_RETIREMENT_PLAN
			|| previous.getCandidateSourceCount() > maximumCandidateSources) {
			throw new IllegalArgumentException(
				"Refinement reassessment exceeds the candidate budget");
		}
		synchronized (layeredRegionLifecycleLock) {
			long observedAtTick = getWorld().getServer().getCurrentTick();
			if (!LayeredPackedRegionRetirementRefinementReassessment
				.isFreshObservationTick(previous, observedAtTick)) {
				return null;
			}
			LayeredPackedRegionRetirementSafetyAssessment safety =
				assessLayeredPackedRegionRetirementRefinementCandidatesLocked(
					previous, maximumCandidateSources, observedAtTick);
			LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort =
				LayeredPackedRegionAuthoredReconstructionCohortAnalysis.analyze(
					checkedRecipe, safety, maximumCandidateSources,
					maximumSupportSources);
			LayeredPackedRegionActiveNpcResidencyObservation activeNpcResidency =
				captureActiveNpcResidency(
					checkedRecipe, safety, observedAtTick, maximumNpcInstances,
					maximumRelevantNpcDetails);
			LayeredPackedRegionActiveNpcBoundaryRequirementProjection
				activeNpcRequirements =
					LayeredPackedRegionActiveNpcBoundaryRequirementProjection.project(
						activeNpcResidency, maximumActiveNpcRequirements);
			return LayeredPackedRegionRetirementRefinementReassessment.reassess(
				previous, safety, cohort, activeNpcRequirements,
				maximumCandidateSources, maximumSupportSources);
		}
	}

	private LayeredPackedRegionRetirementSafetyAssessment
		assessLayeredPackedRegionRetirementRefinementCandidatesLocked(
			final LayeredPackedRegionRetirementRefinementProposal proposal,
			final int maximumPackedSources,
			final long observedAtTick) {
		List<LayeredPackedRegionRetirementSafetyAssessment.PackedSourceContents>
			contents = new ArrayList<
				LayeredPackedRegionRetirementSafetyAssessment
					.PackedSourceContents>(proposal.getCandidateSourceCount());
		for (LayeredPackedRegionRetirementRefinementProposal.CandidateSource
			candidate : proposal.getCandidates()) {
			Region region = peekRegionFromSectorCoordinates(
				candidate.getPackedRegionX(), candidate.getPackedRegionY());
			Region.RetirementContentsSnapshot snapshot = region == null
				? null : region.captureRetirementContentsSnapshot();
			contents.add(LayeredPackedRegionRetirementSafetyAssessment
				.PackedSourceContents.of(
					candidate.getPackedRegionX(), candidate.getPackedRegionY(),
					region != null,
					snapshot != null && snapshot.isTileStorageAvailable(),
					LAYERED_PACKED_REGION_RELOAD_SUPPORTED,
					snapshot == null ? 0 : snapshot.getPlayerCount(),
					snapshot == null ? 0 : snapshot.getNpcCount(),
					snapshot == null ? 0 : snapshot.getObjectCount(),
					snapshot == null ? 0 : snapshot.getGroundItemCount()));
		}
		return LayeredPackedRegionRetirementSafetyAssessment
			.assessDiagnosticSelection(
				contents, observedAtTick, maximumPackedSources);
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
	 * Resolves existing packed Regions without creating them, then enters their
	 * dormant object/collision boundaries in canonical order for one read-only
	 * operation. Existing World object and tile mutations do not use this seam.
	 */
	RegionObjectCollisionMutationBoundary.Execution
		executeUnderExistingOrderedObjectCollisionBoundaries(
			final List<GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate> coordinates,
			final RegionObjectCollisionMutationBoundary.ReadOnlyOperation
				operation) {
		List<GameTickEventRestorationCollisionTransactionContract
			.PackedRegionCoordinate> checked = Objects.requireNonNull(
				coordinates, "coordinates");
		Objects.requireNonNull(operation, "operation");
		if (checked.isEmpty()
			|| checked.size()
				> RegionObjectCollisionMutationBoundary.MAXIMUM_BOUNDARIES
			|| checked.contains(null)) {
			throw new IllegalArgumentException(
				"Ordered object/collision Region set is invalid");
		}
		GameTickEventRestorationCollisionTransactionContract
			.PackedRegionCoordinate previous = null;
		for (GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate current : checked) {
			if (previous != null
				&& comparePackedRegionCoordinates(previous, current) >= 0) {
				throw new IllegalArgumentException(
					"Object/collision Regions are not in canonical order");
			}
			previous = current;
		}
		synchronized (layeredRegionLifecycleLock) {
			List<RegionObjectCollisionMutationBoundary> boundaries =
				new ArrayList<RegionObjectCollisionMutationBoundary>();
			for (GameTickEventRestorationCollisionTransactionContract
					.PackedRegionCoordinate coordinate : checked) {
				Region region = peekRegionFromSectorCoordinates(
					coordinate.getRegionX(), coordinate.getRegionY());
				if (region == null) {
					return RegionObjectCollisionMutationBoundary
						.refuseUnavailable(checked.size());
				}
				boundaries.add(
					region.getObjectCollisionMutationBoundary());
			}
			return RegionObjectCollisionMutationBoundary.executeReadOnly(
				boundaries, operation);
		}
	}

	private static int comparePackedRegionCoordinates(
		final GameTickEventRestorationCollisionTransactionContract
			.PackedRegionCoordinate left,
		final GameTickEventRestorationCollisionTransactionContract
			.PackedRegionCoordinate right) {
		int compared = Integer.compare(
			left.getRegionX(), right.getRegionX());
		return compared != 0 ? compared : Integer.compare(
			left.getRegionY(), right.getRegionY());
	}

	/**
	 * Resolves existing Regions and delegates one exact collision footprint to
	 * the ordered-boundary executor without creating missing Regions. This
	 * package-local form remains the refusal-path test seam.
	 */
	RegionCollisionFootprintMutationExecutor.Result
		applyCollisionFootprintUnderExistingOrderedBoundaries(
			final GameTickEventRestorationCollisionFootprintPlanner.Result
				footprint) {
		return applyCollisionFootprintUnderOrderedBoundaries(footprint, false);
	}

	/**
	 * Runtime entry point for one already projected object-collision footprint.
	 * Definition lookup remains outside the Region boundaries; required packed
	 * Regions are created exactly as the former World tile access created them.
	 */
	public void applyCollisionFootprintUnderOrderedBoundaries(
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			footprint) {
		RegionCollisionFootprintMutationExecutor.Result result =
			applyCollisionFootprintUnderOrderedBoundaries(footprint, true);
		if (!result.isApplied()) {
			throw new IllegalStateException(
				"Object collision mutation refused: " + result.getReason());
		}
	}

	/**
	 * Runtime seam composing exact GameObject membership and collision counters
	 * under one canonical union of their required packed Regions.
	 */
	public void applyObjectMembershipAndCollisionTransaction(
		final GameObject oldObject,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			oldUnregisterFootprint,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			oldRollbackRegisterFootprint,
		final GameObject newObject,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			newRegisterFootprint) {
		if (oldObject == null && newObject == null) {
			throw new IllegalArgumentException(
				"Object membership/collision transaction is empty");
		}
		List<GameTickEventRestorationCollisionTransactionContract
			.PackedRegionCoordinate> required =
			new ArrayList<GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate>();
		if (oldObject != null) {
			required.addAll(Objects.requireNonNull(
				oldUnregisterFootprint,
				"oldUnregisterFootprint").getRequiredRegions());
			required.addAll(Objects.requireNonNull(
				oldRollbackRegisterFootprint,
				"oldRollbackRegisterFootprint").getRequiredRegions());
		}
		if (newObject != null) {
			required.addAll(Objects.requireNonNull(
				newRegisterFootprint,
				"newRegisterFootprint").getRequiredRegions());
		}
		Collections.sort(required,
			new Comparator<GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate>() {
				@Override
				public int compare(
					final GameTickEventRestorationCollisionTransactionContract
						.PackedRegionCoordinate left,
					final GameTickEventRestorationCollisionTransactionContract
						.PackedRegionCoordinate right) {
					return comparePackedRegionCoordinates(left, right);
				}
			});
		List<GameTickEventRestorationCollisionTransactionContract
			.PackedRegionCoordinate> unique =
			new ArrayList<GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate>();
		for (GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate coordinate : required) {
			if (unique.isEmpty()
				|| comparePackedRegionCoordinates(
					unique.get(unique.size() - 1), coordinate) != 0) {
				unique.add(coordinate);
			}
		}
		synchronized (layeredRegionLifecycleLock) {
			final Map<Long, Region> resolved = new HashMap<Long, Region>();
			List<RegionObjectCollisionMutationBoundary> boundaries =
				new ArrayList<RegionObjectCollisionMutationBoundary>();
			for (GameTickEventRestorationCollisionTransactionContract
					.PackedRegionCoordinate coordinate : unique) {
				Region region = getRegionFromSectorCoordinates(
					coordinate.getRegionX(), coordinate.getRegionY());
				resolved.put(
					packRegionCoordinateKey(
						coordinate.getRegionX(), coordinate.getRegionY()),
					region);
				boundaries.add(region.getObjectCollisionMutationBoundary());
			}
			Region oldRegion = oldObject == null ? null
				: resolved.get(packRegionCoordinateKey(
					Math.floorDiv(oldObject.getX(), Constants.REGION_SIZE),
					Math.floorDiv(oldObject.getY(), Constants.REGION_SIZE)));
			Region newRegion = newObject == null ? null
				: resolved.get(packRegionCoordinateKey(
					Math.floorDiv(
						newObject.getLoc().getX(), Constants.REGION_SIZE),
					Math.floorDiv(
						newObject.getLoc().getY(), Constants.REGION_SIZE)));
			RegionObjectCollisionTransactionExecutor.Result result =
				RegionObjectCollisionTransactionExecutor.execute(
					boundaries,
					oldRegion, oldObject, oldUnregisterFootprint,
					oldRollbackRegisterFootprint,
					newRegion, newObject, newRegisterFootprint,
					new RegionCollisionFootprintMutationExecutor
						.MutableTileAccess() {
						@Override
						public TileValue getMutableTile(
							final int x, final int y) {
							int regionX = Math.floorDiv(
								x, Constants.REGION_SIZE);
							int regionY = Math.floorDiv(
								y, Constants.REGION_SIZE);
							Region region = resolved.get(
								packRegionCoordinateKey(regionX, regionY));
							return region == null ? null
								: region.getMutableTileValue(
									Math.floorMod(x, Constants.REGION_SIZE),
									Math.floorMod(y, Constants.REGION_SIZE));
						}
					},
					new RegionObjectCollisionTransactionExecutor.CacheInvalidator() {
						@Override
						public void invalidate(final Region region) {
							invalidateVisibleObjectWindowCache(region);
						}
					});
			if (!result.isApplied()) {
				throw new IllegalStateException(
					"Object membership/collision transaction refused: "
						+ result.getReason());
			}
			if (isLayeredSpatialRuntimeAuthorityEnabled()) {
				if (oldObject != null) {
					layeredSpatialEntityIndex.remove(
						oldObject, oldObject.getWorldLocation());
				}
				if (newObject != null) {
					layeredSpatialEntityIndex.synchronize(
						newObject, null, newObject.getWorldLocation());
				}
			}
		}
	}

	/**
	 * Executable consumer for one scheduler-fenced restoration request. The
	 * Store's closed Slice 142 adapter may call this seam, but no recovery,
	 * arrival, or gameplay path reaches that adapter yet.
	 */
	public RestorationCommitResult applyGameTickEventRestorationCommitRequest(
		final GameTickEventRestorationCommitRequest request) {
		GameTickEventRestorationCommitRequest checked =
			Objects.requireNonNull(request, "request");
		if (!checked.isEventExecutionBoundaryHeld()
			|| checked.isSchedulerStoreBoundaryHeld()
			|| !checked.isRegistrationRevalidated()
			|| !checked.isLifecycleBoundaryHeld()
			|| checked.getProposalGeneration()
				!= checked.getAuthoredGeneration()) {
			return RestorationCommitResult.refused(
				RestorationCommitReason.SCHEDULER_BOUNDARY_REFUSED);
		}
		final Point targetLocation = Point.location(
			checked.getX(), checked.getY());
		synchronized (layeredRegionLifecycleLock) {
			Region targetRegion = peekRegionFromSectorCoordinates(
				Math.floorDiv(checked.getX(), Constants.REGION_SIZE),
				Math.floorDiv(checked.getY(), Constants.REGION_SIZE));
			if (targetRegion == null) {
				return RestorationCommitResult.refused(
					RestorationCommitReason.TARGET_REGION_UNAVAILABLE);
			}
			GameObject candidate = checked.getType() == 0
				? targetRegion.getGameObject(targetLocation, null)
				: targetRegion.getWallGameObject(
					targetLocation, checked.getDirection());

			GameTickEventRestorationCollisionFootprintPlanner.Result
				candidateUnregister = candidate == null ? null
					: world.projectGameObjectCollisionFootprint(
						candidate, Operation.UNREGISTER, false);
			GameTickEventRestorationCollisionFootprintPlanner.Result
				candidateRollback = candidate == null ? null
					: world.projectGameObjectCollisionFootprint(
						candidate, Operation.REGISTER, false);

			GameObject desired = null;
			GameTickEventRestorationCollisionFootprintPlanner.Result
				desiredRegister = null;
			if (checked.getTargetOperation() == TargetOperation.SCENERY_SPAWN) {
				String constructionKind =
					checked.getAuthoredConstructionKind();
				if ((checked.getType() == 0
						&& !"SCENERY".equals(constructionKind)
						&& !"HARVESTING_SCENERY".equals(constructionKind))
					|| (checked.getType() == 1
						&& !"BOUNDARY".equals(constructionKind))) {
					return RestorationCommitResult.refused(
						RestorationCommitReason
							.AUTHORED_CONSTRUCTION_KIND_REFUSED);
				}
				GameObjectLoc loc = new GameObjectLoc(
					checked.getObjectId(), checked.getPermanentObjectId(),
					checked.getX(), checked.getY(), checked.getDirection(),
					checked.getType());
				try {
					loc.assignSerializedAuthoredPlacementIdentity(
						checked.getAuthoredGeneration(),
						checked.getAuthoredPackedRegionX(),
						checked.getAuthoredPackedRegionY(),
						checked.getAuthoredSourceOrdinal(), constructionKind);
				} catch (IllegalArgumentException unsupported) {
					return RestorationCommitResult.refused(
						RestorationCommitReason
							.AUTHORED_CONSTRUCTION_KIND_REFUSED);
				}
				desired = new GameObject(world, loc);
				desiredRegister = world.projectGameObjectCollisionFootprint(
					desired, Operation.REGISTER,
					checked.isForceFullBlock());
			} else if (checked.getTargetOperation()
				!= TargetOperation.SCENERY_REMOVE) {
				return RestorationCommitResult.refused(
					RestorationCommitReason.TARGET_OPERATION_REFUSED);
			}
			if ((candidateUnregister != null
					&& !candidateUnregister.isFootprintAvailable())
				|| (candidateRollback != null
					&& !candidateRollback.isFootprintAvailable())
				|| (desiredRegister != null
					&& !desiredRegister.isFootprintAvailable())) {
				return RestorationCommitResult.refused(
					RestorationCommitReason.COLLISION_FOOTPRINT_UNAVAILABLE);
			}

			List<GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate> required =
				new ArrayList<GameTickEventRestorationCollisionTransactionContract
					.PackedRegionCoordinate>();
			required.add(GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate.of(
					Math.floorDiv(checked.getX(), Constants.REGION_SIZE),
					Math.floorDiv(checked.getY(), Constants.REGION_SIZE)));
			if (candidateUnregister != null) {
				required.addAll(candidateUnregister.getRequiredRegions());
				required.addAll(candidateRollback.getRequiredRegions());
			}
			if (desiredRegister != null) {
				required.addAll(desiredRegister.getRequiredRegions());
			}
			Collections.sort(required,
				new Comparator<GameTickEventRestorationCollisionTransactionContract
					.PackedRegionCoordinate>() {
					@Override
					public int compare(
						final GameTickEventRestorationCollisionTransactionContract
							.PackedRegionCoordinate left,
						final GameTickEventRestorationCollisionTransactionContract
							.PackedRegionCoordinate right) {
						return comparePackedRegionCoordinates(left, right);
					}
				});
			List<GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate> unique =
				new ArrayList<GameTickEventRestorationCollisionTransactionContract
					.PackedRegionCoordinate>();
			for (GameTickEventRestorationCollisionTransactionContract
					.PackedRegionCoordinate coordinate : required) {
				if (unique.isEmpty()
					|| comparePackedRegionCoordinates(
						unique.get(unique.size() - 1), coordinate) != 0) {
					unique.add(coordinate);
				}
			}

			final Map<Long, Region> resolved = new HashMap<Long, Region>();
			List<RegionObjectCollisionMutationBoundary> boundaries =
				new ArrayList<RegionObjectCollisionMutationBoundary>();
			for (GameTickEventRestorationCollisionTransactionContract
					.PackedRegionCoordinate coordinate : unique) {
				Region region = peekRegionFromSectorCoordinates(
					coordinate.getRegionX(), coordinate.getRegionY());
				if (region == null) {
					return RestorationCommitResult.refused(
						RestorationCommitReason.REQUIRED_REGION_UNAVAILABLE);
				}
				resolved.put(packRegionCoordinateKey(
					coordinate.getRegionX(), coordinate.getRegionY()), region);
				boundaries.add(region.getObjectCollisionMutationBoundary());
			}

			RegionObjectCollisionTransactionExecutor.RestorationResult result =
				RegionObjectCollisionTransactionExecutor.executeRestoration(
					boundaries, targetRegion, checked, candidate,
					candidateUnregister, candidateRollback,
					desired, desiredRegister,
					new RegionCollisionFootprintMutationExecutor
						.MutableTileAccess() {
						@Override
						public TileValue getMutableTile(
							final int x, final int y) {
							Region region = resolved.get(packRegionCoordinateKey(
								Math.floorDiv(x, Constants.REGION_SIZE),
								Math.floorDiv(y, Constants.REGION_SIZE)));
							return region == null ? null
								: region.getMutableTileValue(
									Math.floorMod(x, Constants.REGION_SIZE),
									Math.floorMod(y, Constants.REGION_SIZE));
						}
					},
					new RegionObjectCollisionTransactionExecutor
						.CacheInvalidator() {
						@Override
						public void invalidate(final Region region) {
							invalidateVisibleObjectWindowCache(region);
						}
					});
			return RestorationCommitResult.from(result);
		}
	}

	/**
	 * Captures one live future callback's exact current scenery under existing
	 * Region object/collision boundaries. Missing Regions are never created.
	 */
	public CurrentStateRecoveryCaptureResult
		captureGameTickEventCurrentStateRecoverySnapshot(
			final CallbackExpectation callback,
			final boolean eventExecutionBoundaryHeld,
			final boolean stableLifecycleBoundaryHeld) {
		final CallbackExpectation checked = Objects.requireNonNull(
			callback, "callback");
		final int regionX = Math.floorDiv(
			checked.getX(), Constants.REGION_SIZE);
		final int regionY = Math.floorDiv(
			checked.getY(), Constants.REGION_SIZE);
		synchronized (layeredRegionLifecycleLock) {
			final Region targetRegion = peekRegionFromSectorCoordinates(
				regionX, regionY);
			if (targetRegion == null) {
				return CurrentStateRecoveryCaptureResult.refused(
					CurrentStateRecoveryCaptureReason
						.TARGET_REGION_UNAVAILABLE);
			}
			final Point targetLocation = Point.location(
				checked.getX(), checked.getY());
			final GameObject observed = checked.getType() == 0
				? targetRegion.getGameObject(targetLocation, null)
				: targetRegion.getWallGameObject(
					targetLocation, checked.getDirection());
			if (observed == null) {
				return CurrentStateRecoveryCaptureResult.refused(
					CurrentStateRecoveryCaptureReason
						.TARGET_NOT_EXACTLY_ONE);
			}
			if (observed.getAuthoredPlacementIdentity() == null) {
				return CurrentStateRecoveryCaptureResult.refused(
					CurrentStateRecoveryCaptureReason
						.AUTHORED_IDENTITY_UNAVAILABLE);
			}
			final long observedGeneration =
				observed.getAuthoredPlacementIdentity().getGeneration();
			final int observedPackedRegionX =
				observed.getAuthoredPlacementIdentity().getPackedRegionX();
			final int observedPackedRegionY =
				observed.getAuthoredPlacementIdentity().getPackedRegionY();
			final int observedSourceOrdinal =
				observed.getAuthoredPlacementIdentity().getSourceOrdinal();
			final String observedConstructionKind =
				observed.getAuthoredPlacementIdentity()
					.getConstructionKind().name();
			final GameObjectCollisionRegistrationState observedCollision =
				observed.getCollisionRegistrationState();
			if (observedCollision == null
				|| !observedCollision.matchesConstructor(observed)) {
				return CurrentStateRecoveryCaptureResult.refused(
					CurrentStateRecoveryCaptureReason
						.COLLISION_PROVENANCE_UNAVAILABLE);
			}

			List<GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate> required =
					new ArrayList<GameTickEventRestorationCollisionTransactionContract
						.PackedRegionCoordinate>(
							observedCollision.getRequiredRegionCount());
			for (GameObjectCollisionRegistrationState.PackedRegionCoordinate
					coordinate : observedCollision.getRequiredRegions()) {
				required.add(
					GameTickEventRestorationCollisionTransactionContract
						.PackedRegionCoordinate.of(
							coordinate.getRegionX(), coordinate.getRegionY()));
			}
			final GameTickEventRestorationCurrentStateRecoverySnapshot.Creation[]
				creation =
					new GameTickEventRestorationCurrentStateRecoverySnapshot
						.Creation[1];
			RegionObjectCollisionMutationBoundary.Execution execution =
				executeUnderExistingOrderedObjectCollisionBoundaries(
					required, heldBoundaries -> {
						synchronized (
							targetRegion.getGameObjectTransactionMonitor()) {
							if (!targetRegion
									.containsGameObjectIdentityUnderTransaction(
										observed)) {
								return;
							}
							GameObjectCollisionRegistrationState currentCollision =
								observed.getCollisionRegistrationState();
							if (observed.getAuthoredPlacementIdentity() == null
								|| observed.getAuthoredPlacementIdentity()
									.getGeneration() != observedGeneration
								|| observed.getAuthoredPlacementIdentity()
									.getPackedRegionX() != observedPackedRegionX
								|| observed.getAuthoredPlacementIdentity()
									.getPackedRegionY() != observedPackedRegionY
								|| observed.getAuthoredPlacementIdentity()
									.getSourceOrdinal() != observedSourceOrdinal
								|| !observed.getAuthoredPlacementIdentity()
									.getConstructionKind().name()
										.equals(observedConstructionKind)
								|| currentCollision == null
								|| currentCollision != observedCollision
								|| !currentCollision.matchesConstructor(observed)) {
								return;
							}
							Region.RestorationTargetBoundarySnapshot target =
								targetRegion.captureRestorationTargetBoundarySnapshot(
									Region.RestorationTargetMatchRequirement.of(
										observed.getID(),
										observed.getLoc().getPermId(),
										observed.getLoc().getX(),
										observed.getLoc().getY(),
										observed.getDirection(), observed.getType(),
										null, 0, observedGeneration,
										observedPackedRegionX,
										observedPackedRegionY,
										observedSourceOrdinal,
										observedConstructionKind),
									true);
							List<CollisionContribution> collision =
								new ArrayList<CollisionContribution>(
									currentCollision.getContributionTileCount());
							for (GameObjectCollisionRegistrationState
									.CollisionContribution contribution
										: currentCollision.getContributions()) {
								collision.add(CollisionContribution.ofCounts(
									contribution.getX(), contribution.getY(),
									contribution.getBlockingSceneryCount(),
									contribution.getDynamicCollisionCounts(),
									contribution.getDynamicProjectileCount()));
							}
							CurrentScenery current = CurrentScenery.declare(
								checked.getKind()
									== GameTickEventRestorationCurrentStateRecoverySnapshot
										.CallbackKind.SCENERY_SPAWN
									? GameTickEventRestorationCurrentStateRecoverySnapshot
										.ObservedCurrentState
											.EXACT_AUTHORED_TRANSIENT_PRESENT
									: GameTickEventRestorationCurrentStateRecoverySnapshot
										.ObservedCurrentState
											.EXACT_RESTORATION_SCENERY_PRESENT,
								observed.getID(), observed.getLoc().getPermId(),
								observed.getLoc().getX(), observed.getLoc().getY(),
								observed.getDirection(), observed.getType(),
								observed.getOwner(),
								observed.getRuntimeAttributeCount(),
								observedGeneration, observedPackedRegionX,
								observedPackedRegionY, observedSourceOrdinal,
								GameTickEventRestorationCurrentStateRecoverySnapshot
									.AuthoredConstructionKind.valueOf(
										observedConstructionKind),
								target.getSlotObjectCount(),
								eventExecutionBoundaryHeld,
								stableLifecycleBoundaryHeld,
								target.isObjectBoundaryHeldDuringClassification(),
								heldBoundaries.areAllBoundariesHeld(), true,
								collision);
							creation[0] =
								GameTickEventRestorationCurrentStateRecoverySnapshot
									.assess(checked, current);
						}
					});
			if (execution.isRefused()) {
				return CurrentStateRecoveryCaptureResult.refused(
					CurrentStateRecoveryCaptureReason
						.REQUIRED_REGION_UNAVAILABLE);
			}
			if (creation[0] == null) {
				return CurrentStateRecoveryCaptureResult.refused(
					CurrentStateRecoveryCaptureReason
						.TARGET_CHANGED_DURING_CAPTURE);
			}
			return CurrentStateRecoveryCaptureResult.from(creation[0]);
		}
	}

	public enum CurrentStateRecoveryCaptureReason {
		SNAPSHOT_AVAILABLE,
		TARGET_REGION_UNAVAILABLE,
		TARGET_NOT_EXACTLY_ONE,
		AUTHORED_IDENTITY_UNAVAILABLE,
		COLLISION_PROVENANCE_UNAVAILABLE,
		REQUIRED_REGION_UNAVAILABLE,
		TARGET_CHANGED_DURING_CAPTURE,
		SNAPSHOT_REFUSED
	}

	/** Closed read-only capture result with no live object or Region handle. */
	public static final class CurrentStateRecoveryCaptureResult {
		private final CurrentStateRecoveryCaptureReason reason;
		private final GameTickEventRestorationCurrentStateRecoverySnapshot
			.Creation creation;

		private CurrentStateRecoveryCaptureResult(
			final CurrentStateRecoveryCaptureReason reason,
			final GameTickEventRestorationCurrentStateRecoverySnapshot
				.Creation creation) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.creation = creation;
			boolean snapshotAssessment =
				reason == CurrentStateRecoveryCaptureReason.SNAPSHOT_AVAILABLE
					|| reason
						== CurrentStateRecoveryCaptureReason.SNAPSHOT_REFUSED;
			if (snapshotAssessment != (creation != null)
				|| (reason
					== CurrentStateRecoveryCaptureReason.SNAPSHOT_AVAILABLE)
					!= (creation != null && creation.isSnapshotAvailable())) {
				throw new IllegalArgumentException(
					"Current-state capture result is inconsistent");
			}
		}

		private static CurrentStateRecoveryCaptureResult refused(
			final CurrentStateRecoveryCaptureReason reason) {
			return new CurrentStateRecoveryCaptureResult(reason, null);
		}

		private static CurrentStateRecoveryCaptureResult from(
			final GameTickEventRestorationCurrentStateRecoverySnapshot
				.Creation creation) {
			return creation.isSnapshotAvailable()
				? new CurrentStateRecoveryCaptureResult(
					CurrentStateRecoveryCaptureReason.SNAPSHOT_AVAILABLE,
					creation)
				: new CurrentStateRecoveryCaptureResult(
					CurrentStateRecoveryCaptureReason.SNAPSHOT_REFUSED,
					creation);
		}

		public CurrentStateRecoveryCaptureReason getReason() { return reason; }
		public boolean isSnapshotAvailable() {
			return reason == CurrentStateRecoveryCaptureReason.SNAPSHOT_AVAILABLE;
		}
		public GameTickEventRestorationCurrentStateRecoverySnapshot getSnapshot() {
			return creation == null ? null : creation.getSnapshot();
		}
		public GameTickEventRestorationCurrentStateRecoverySnapshot.Reason
			getSnapshotReason() {
			return creation == null ? null : creation.getReason();
		}
		public boolean isRuntimeObservationPerformed() { return true; }
		public boolean isRuntimeHandleRetained() { return false; }
		public boolean isMutationAuthorized() { return false; }
		public boolean isMutationPerformed() { return false; }
		public boolean isRegionLoadingPerformed() { return false; }
		public boolean isCallbackInvoked() { return false; }
		public boolean isEventCancellation() { return false; }
		public boolean isEventReschedule() { return false; }
		public boolean isArrivalGate() { return false; }
		public boolean isVisibilityReleased() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}

	/**
	 * Reconstructs and applies one exact future-callback current state without
	 * loading a missing Region or observing/changing the scheduler callback.
	 * This package-local seam remains disconnected from production callers.
	 */
	public CurrentStateRecoveryApplicationResult
		applyGameTickEventCurrentStateRecoverySnapshot(
			final GameTickEventRestorationCurrentStateRecoverySnapshot snapshot) {
		return applyGameTickEventCurrentStateRecoverySnapshot(snapshot, true);
	}

	/** Verifies idempotent satisfaction and refuses instead of restoring. */
	public CurrentStateRecoveryApplicationResult
		verifyGameTickEventCurrentStateRecoverySnapshot(
			final GameTickEventRestorationCurrentStateRecoverySnapshot snapshot) {
		return applyGameTickEventCurrentStateRecoverySnapshot(snapshot, false);
	}

	private CurrentStateRecoveryApplicationResult
		applyGameTickEventCurrentStateRecoverySnapshot(
			final GameTickEventRestorationCurrentStateRecoverySnapshot snapshot,
			final boolean mutationAllowed) {
		return applyGameTickEventCurrentStateRecoverySnapshot(
			snapshot, new CurrentStateCollisionProjector() {
				@Override
				public GameTickEventRestorationCollisionFootprintPlanner.Result
					project(
						final GameObject object,
						final boolean forceFullBlock) {
					try {
						return world.projectGameObjectCollisionFootprint(
							object, Operation.REGISTER, forceFullBlock);
					} catch (RuntimeException unavailableDefinition) {
						return null;
					}
				}
			}, mutationAllowed);
	}

	CurrentStateRecoveryApplicationResult
		applyGameTickEventCurrentStateRecoverySnapshot(
			final GameTickEventRestorationCurrentStateRecoverySnapshot snapshot,
			final CurrentStateCollisionProjector collisionProjector) {
		return applyGameTickEventCurrentStateRecoverySnapshot(
			snapshot, collisionProjector, true);
	}

	CurrentStateRecoveryApplicationResult
		applyGameTickEventCurrentStateRecoverySnapshot(
			final GameTickEventRestorationCurrentStateRecoverySnapshot snapshot,
			final CurrentStateCollisionProjector collisionProjector,
			final boolean mutationAllowed) {
		GameTickEventRestorationCurrentStateRecoverySnapshot checked =
			Objects.requireNonNull(snapshot, "snapshot");
		CurrentStateCollisionProjector checkedProjector =
			Objects.requireNonNull(collisionProjector, "collisionProjector");
		String constructionKind = checked.getAuthoredConstructionKind().name();
		if ((checked.getType() == 0
				&& !"SCENERY".equals(constructionKind)
				&& !"HARVESTING_SCENERY".equals(constructionKind))
			|| (checked.getType() == 1
				&& !"BOUNDARY".equals(constructionKind))) {
			return CurrentStateRecoveryApplicationResult.refused(
				CurrentStateRecoveryApplicationReason
					.AUTHORED_CONSTRUCTION_KIND_REFUSED);
		}

		GameObjectLoc loc = new GameObjectLoc(
			checked.getCurrentObjectId(),
			checked.getCurrentPermanentObjectId(),
			checked.getX(), checked.getY(), checked.getDirection(),
			checked.getType());
		try {
			loc.assignSerializedAuthoredPlacementIdentity(
				checked.getAuthoredGeneration(),
				checked.getAuthoredPackedRegionX(),
				checked.getAuthoredPackedRegionY(),
				checked.getAuthoredSourceOrdinal(), constructionKind);
		} catch (IllegalArgumentException unsupportedKind) {
			return CurrentStateRecoveryApplicationResult.refused(
				CurrentStateRecoveryApplicationReason
					.AUTHORED_CONSTRUCTION_KIND_REFUSED);
		}
		GameObject current = new GameObject(world, loc);
		GameTickEventRestorationCollisionFootprintPlanner.Result footprint =
			checkedProjector.project(current, false);
		boolean forceFullBlock = false;
		if (!RegionObjectCollisionTransactionExecutor
				.matchesCurrentStateRecoveryFootprint(footprint, checked)) {
			footprint = checkedProjector.project(current, true);
			forceFullBlock = true;
		}
		if (footprint == null || !footprint.isFootprintAvailable()) {
			return CurrentStateRecoveryApplicationResult.refused(
				CurrentStateRecoveryApplicationReason
					.COLLISION_FOOTPRINT_UNAVAILABLE);
		}
		if (!RegionObjectCollisionTransactionExecutor
				.matchesCurrentStateRecoveryFootprint(footprint, checked)) {
			return CurrentStateRecoveryApplicationResult.refused(
				CurrentStateRecoveryApplicationReason
					.CURRENT_COLLISION_SNAPSHOT_MISMATCH);
		}

		final GameTickEventRestorationCollisionFootprintPlanner.Result
			selectedFootprint = footprint;
		final boolean selectedForceFullBlock = forceFullBlock;
		final Point targetLocation = Point.location(
			checked.getX(), checked.getY());
		synchronized (layeredRegionLifecycleLock) {
			Region targetRegion = peekRegionFromSectorCoordinates(
				Math.floorDiv(checked.getX(), Constants.REGION_SIZE),
				Math.floorDiv(checked.getY(), Constants.REGION_SIZE));
			if (targetRegion == null) {
				return CurrentStateRecoveryApplicationResult.refused(
					CurrentStateRecoveryApplicationReason
						.TARGET_REGION_UNAVAILABLE);
			}
			GameObject candidate = checked.getType() == 0
				? targetRegion.getGameObject(targetLocation, null)
				: targetRegion.getWallGameObject(
					targetLocation, checked.getDirection());

			final Map<Long, Region> resolved = new HashMap<Long, Region>();
			List<RegionObjectCollisionMutationBoundary> boundaries =
				new ArrayList<RegionObjectCollisionMutationBoundary>();
			for (GameTickEventRestorationCollisionTransactionContract
					.PackedRegionCoordinate coordinate
						: selectedFootprint.getRequiredRegions()) {
				Region region = peekRegionFromSectorCoordinates(
					coordinate.getRegionX(), coordinate.getRegionY());
				if (region == null) {
					return CurrentStateRecoveryApplicationResult.refused(
						CurrentStateRecoveryApplicationReason
							.REQUIRED_REGION_UNAVAILABLE);
				}
				resolved.put(packRegionCoordinateKey(
					coordinate.getRegionX(), coordinate.getRegionY()), region);
				boundaries.add(region.getObjectCollisionMutationBoundary());
			}

			RegionObjectCollisionTransactionExecutor.CurrentStateRecoveryResult
				result = RegionObjectCollisionTransactionExecutor
					.executeCurrentStateRecovery(
						boundaries, targetRegion, checked, candidate, current,
						selectedFootprint,
						new RegionCollisionFootprintMutationExecutor
							.MutableTileAccess() {
								@Override
								public TileValue getMutableTile(
									final int x, final int y) {
									Region region = resolved.get(
										packRegionCoordinateKey(
											Math.floorDiv(
												x, Constants.REGION_SIZE),
											Math.floorDiv(
												y, Constants.REGION_SIZE)));
									return region == null ? null
										: region.getMutableTileValue(
											Math.floorMod(
												x, Constants.REGION_SIZE),
											Math.floorMod(
												y, Constants.REGION_SIZE));
								}
							},
						new RegionObjectCollisionTransactionExecutor
							.CacheInvalidator() {
								@Override
								public void invalidate(final Region region) {
									if (world != null) {
										invalidateVisibleObjectWindowCache(region);
									}
								}
							}, mutationAllowed);
			return CurrentStateRecoveryApplicationResult.from(
				result, selectedForceFullBlock);
		}
	}

	interface CurrentStateCollisionProjector {
		GameTickEventRestorationCollisionFootprintPlanner.Result project(
			GameObject object, boolean forceFullBlock);
	}

	public enum CurrentStateRecoveryApplicationOutcome {
		REFUSED,
		NO_OP,
		APPLIED
	}

	public enum CurrentStateRecoveryApplicationReason {
		AUTHORED_CONSTRUCTION_KIND_REFUSED,
		COLLISION_FOOTPRINT_UNAVAILABLE,
		TARGET_REGION_UNAVAILABLE,
		REQUIRED_REGION_UNAVAILABLE,
		SNAPSHOT_CONTRACT_REFUSED,
		CURRENT_CONSTRUCTOR_REFUSED,
		CURRENT_COLLISION_SNAPSHOT_MISMATCH,
		TARGET_CHANGED_BEFORE_RECOVERY,
		TARGET_CLASSIFICATION_REFUSED,
		OBJECT_TRANSACTION_REFUSED,
		CURRENT_STATE_ALREADY_SATISFIED,
		CURRENT_STATE_RESTORED
	}

	/** Closed current-state result with no object, Region, or event handle. */
	public static final class CurrentStateRecoveryApplicationResult {
		private final CurrentStateRecoveryApplicationOutcome outcome;
		private final CurrentStateRecoveryApplicationReason reason;
		private final boolean registered;
		private final boolean forceFullBlockProjection;
		private final int boundaryCount;

		private CurrentStateRecoveryApplicationResult(
			final CurrentStateRecoveryApplicationOutcome outcome,
			final CurrentStateRecoveryApplicationReason reason,
			final boolean registered,
			final boolean forceFullBlockProjection,
			final int boundaryCount) {
			this.outcome = Objects.requireNonNull(outcome, "outcome");
			this.reason = Objects.requireNonNull(reason, "reason");
			this.registered = registered;
			this.forceFullBlockProjection = forceFullBlockProjection;
			this.boundaryCount = boundaryCount;
			if (boundaryCount < 0
				|| (outcome == CurrentStateRecoveryApplicationOutcome.APPLIED)
					!= (reason == CurrentStateRecoveryApplicationReason
						.CURRENT_STATE_RESTORED)
				|| (outcome == CurrentStateRecoveryApplicationOutcome.NO_OP)
					!= (reason == CurrentStateRecoveryApplicationReason
						.CURRENT_STATE_ALREADY_SATISFIED)
				|| registered
					!= (outcome
						== CurrentStateRecoveryApplicationOutcome.APPLIED)) {
				throw new IllegalArgumentException(
					"Current-state application result is inconsistent");
			}
		}

		private static CurrentStateRecoveryApplicationResult refused(
			final CurrentStateRecoveryApplicationReason reason) {
			return new CurrentStateRecoveryApplicationResult(
				CurrentStateRecoveryApplicationOutcome.REFUSED, reason,
				false, false, 0);
		}

		private static CurrentStateRecoveryApplicationResult from(
			final RegionObjectCollisionTransactionExecutor
				.CurrentStateRecoveryResult result,
			final boolean forceFullBlockProjection) {
			return new CurrentStateRecoveryApplicationResult(
				CurrentStateRecoveryApplicationOutcome.valueOf(
					result.getOutcome().name()),
				CurrentStateRecoveryApplicationReason.valueOf(
					result.getReason().name()),
				result.isMembershipRegistered(), forceFullBlockProjection,
				result.getBoundaryCount());
		}

		public CurrentStateRecoveryApplicationOutcome getOutcome() {
			return outcome;
		}
		public CurrentStateRecoveryApplicationReason getReason() {
			return reason;
		}
		public boolean isApplied() {
			return outcome == CurrentStateRecoveryApplicationOutcome.APPLIED;
		}
		public boolean isNoOp() {
			return outcome == CurrentStateRecoveryApplicationOutcome.NO_OP;
		}
		public boolean isRefused() {
			return outcome == CurrentStateRecoveryApplicationOutcome.REFUSED;
		}
		public boolean isMembershipRegistered() { return registered; }
		public boolean isForceFullBlockProjectionSelected() {
			return forceFullBlockProjection;
		}
		public int getBoundaryCount() { return boundaryCount; }
		public boolean isSnapshotRetained() { return false; }
		public boolean isEventHandleRetained() { return false; }
		public boolean isSchedulerStateTouched() { return false; }
		public boolean isCallbackInvoked() { return false; }
		public boolean isEventCancellation() { return false; }
		public boolean isEventReschedule() { return false; }
		public boolean isLoadingPerformed() { return false; }
		public boolean isArrivalGate() { return false; }
		public boolean isVisibilityReleased() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}

	public enum RestorationCommitOutcome { REFUSED, NO_OP, APPLIED }

	public enum RestorationCommitReason {
		SCHEDULER_BOUNDARY_REFUSED,
		TARGET_REGION_UNAVAILABLE,
		REQUIRED_REGION_UNAVAILABLE,
		AUTHORED_CONSTRUCTION_KIND_REFUSED,
		TARGET_OPERATION_REFUSED,
		COLLISION_FOOTPRINT_UNAVAILABLE,
		TARGET_CHANGED_BEFORE_COMMIT,
		TARGET_CLASSIFICATION_REFUSED,
		TRANSIENT_ROLLBACK_STATE_NOT_CONNECTED,
		TRANSIENT_ROLLBACK_SNAPSHOT_REFUSED,
		TRANSIENT_COLLISION_ROLLBACK_MISMATCH,
		OBJECT_TRANSACTION_REFUSED,
		DESIRED_STATE_ALREADY_SATISFIED,
		RESTORATION_APPLIED
	}

	/** Closed result with no entity, Region, event, or callback handle. */
	public static final class RestorationCommitResult {
		private final RestorationCommitOutcome outcome;
		private final RestorationCommitReason reason;
		private final boolean removed;
		private final boolean registered;
		private final int boundaryCount;

		private RestorationCommitResult(
			final RestorationCommitOutcome outcome,
			final RestorationCommitReason reason,
			final boolean removed,
			final boolean registered,
			final int boundaryCount) {
			this.outcome = Objects.requireNonNull(outcome, "outcome");
			this.reason = Objects.requireNonNull(reason, "reason");
			this.removed = removed;
			this.registered = registered;
			this.boundaryCount = boundaryCount;
			if (boundaryCount < 0
				|| (outcome == RestorationCommitOutcome.APPLIED)
					!= (reason == RestorationCommitReason.RESTORATION_APPLIED)
				|| (outcome == RestorationCommitOutcome.NO_OP)
					!= (reason
						== RestorationCommitReason
							.DESIRED_STATE_ALREADY_SATISFIED)
				|| (outcome != RestorationCommitOutcome.APPLIED
					&& (removed || registered))) {
				throw new IllegalArgumentException(
					"Restoration commit result is inconsistent");
			}
		}

		private static RestorationCommitResult refused(
			final RestorationCommitReason reason) {
			return new RestorationCommitResult(
				RestorationCommitOutcome.REFUSED, reason, false, false, 0);
		}

		private static RestorationCommitResult from(
			final RegionObjectCollisionTransactionExecutor.RestorationResult
				result) {
			return new RestorationCommitResult(
				RestorationCommitOutcome.valueOf(result.getOutcome().name()),
				RestorationCommitReason.valueOf(result.getReason().name()),
				result.isMembershipRemoved(),
				result.isMembershipRegistered(), result.getBoundaryCount());
		}

		public RestorationCommitOutcome getOutcome() { return outcome; }
		public RestorationCommitReason getReason() { return reason; }
		public boolean isApplied() {
			return outcome == RestorationCommitOutcome.APPLIED;
		}
		public boolean isNoOp() {
			return outcome == RestorationCommitOutcome.NO_OP;
		}
		public boolean isRefused() {
			return outcome == RestorationCommitOutcome.REFUSED;
		}
		public boolean isMembershipRemoved() { return removed; }
		public boolean isMembershipRegistered() { return registered; }
		public int getBoundaryCount() { return boundaryCount; }
		public boolean isRequestRetained() { return false; }
		public boolean isEventHandleRetained() { return false; }
		public boolean isEntityHandleRetained() { return false; }
		public boolean isRegionHandleRetained() { return false; }
		public boolean isMutationPerformed() { return isApplied(); }
		public boolean isCallbackInvoked() { return false; }
		public boolean isEventCancellation() { return false; }
		public boolean isEventReschedule() { return false; }
		public boolean isExecutableRestoration() { return false; }
		public boolean isCommitToken() { return false; }
		public boolean isArrivalGate() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}

	private RegionCollisionFootprintMutationExecutor.Result
		applyCollisionFootprintUnderOrderedBoundaries(
			final GameTickEventRestorationCollisionFootprintPlanner.Result
				footprint,
			final boolean createRequiredRegions) {
		GameTickEventRestorationCollisionFootprintPlanner.Result checked =
			Objects.requireNonNull(footprint, "footprint");
		if (!checked.isFootprintAvailable()) {
			return RegionCollisionFootprintMutationExecutor.execute(
				Collections.<RegionObjectCollisionMutationBoundary>emptyList(),
				checked,
				new RegionCollisionFootprintMutationExecutor.MutableTileAccess() {
					@Override
					public TileValue getMutableTile(final int x, final int y) {
						throw new IllegalStateException(
							"Unavailable footprint cannot access a tile");
					}
				});
		}
		synchronized (layeredRegionLifecycleLock) {
			final Map<Long, Region> resolved = new HashMap<Long, Region>();
			List<RegionObjectCollisionMutationBoundary> boundaries =
				new ArrayList<RegionObjectCollisionMutationBoundary>();
			for (GameTickEventRestorationCollisionTransactionContract
					.PackedRegionCoordinate coordinate
						: checked.getRequiredRegions()) {
				Region region = createRequiredRegions
					? getRegionFromSectorCoordinates(
						coordinate.getRegionX(), coordinate.getRegionY())
					: peekRegionFromSectorCoordinates(
						coordinate.getRegionX(), coordinate.getRegionY());
				if (region == null) {
					return RegionCollisionFootprintMutationExecutor
						.refuseRequiredRegionUnavailable();
				}
				resolved.put(
					packRegionCoordinateKey(
						coordinate.getRegionX(), coordinate.getRegionY()),
					region);
				boundaries.add(region.getObjectCollisionMutationBoundary());
			}
			return RegionCollisionFootprintMutationExecutor.execute(
				boundaries, checked,
				new RegionCollisionFootprintMutationExecutor.MutableTileAccess() {
					@Override
					public TileValue getMutableTile(final int x, final int y) {
						int regionX = Math.floorDiv(
							x, GameTickEventRestorationCollisionTransactionContract
								.PACKED_REGION_SIZE);
						int regionY = Math.floorDiv(
							y, GameTickEventRestorationCollisionTransactionContract
								.PACKED_REGION_SIZE);
						Region region = resolved.get(
							packRegionCoordinateKey(regionX, regionY));
						return region == null ? null : region.getMutableTileValue(
							Math.floorMod(
								x, GameTickEventRestorationCollisionTransactionContract
									.PACKED_REGION_SIZE),
							Math.floorMod(
								y, GameTickEventRestorationCollisionTransactionContract
									.PACKED_REGION_SIZE));
					}
				});
		}
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

	/**
	 * Returns whether the exact signed location is backed by the active native
	 * package. This is the native runtime projection selector; level number and
	 * the former synthetic fixture rectangle are not selectors.
	 */
	public boolean hasNativeLayeredTerrain(
		final WorldLocation location) {
		WorldLocation checked=Objects.requireNonNull(location,"location");
		return nativeLayeredWorldPackageCatalog != null
			&& (nativeLayeredWorldPackageCatalog.findPackage(checked).isPresent()
				||hasWorldBuilderDraftTerrain(checked));
	}

	public Optional<NativeLayeredWorldPackage> findNativeLayeredWorldPackage(
		final WorldLocation location) {
		if(nativeLayeredWorldPackageCatalog==null)
			return Optional.<NativeLayeredWorldPackage>empty();
		WorldLocation checked=Objects.requireNonNull(location,"location");
		Optional<NativeLayeredWorldPackage> source=
			nativeLayeredWorldPackageCatalog.findPackage(checked);
		if(source.isPresent())return source;
		return hasWorldBuilderDraftTerrain(checked)
			?Optional.of(nativeLayeredWorldPackage)
			:Optional.<NativeLayeredWorldPackage>empty();
	}

	private boolean hasWorldBuilderDraftTerrain(
		final WorldLocation location){
		return world.getServer().getConfig().WORLD_BUILDER_MODE
			&&world.getServer().getWorldEditorSessions()
				.hasNativeTerrainDraft(location);
	}

	public Optional<NativeLayeredWorldPackage> findNativeLayeredWorldPackage(
		final String packageId) {
		return nativeLayeredWorldPackageCatalog == null
			? Optional.<NativeLayeredWorldPackage>empty()
			: nativeLayeredWorldPackageCatalog.findPackage(
				Objects.requireNonNull(packageId, "packageId"));
	}

	public NativeLayeredWorldPackageCatalog.Transition
		prepareNativeLayeredTransition(
			final WorldLocation source,
			final WorldLocation destination,
			final boolean explicit) {
		WorldLocation checkedDestination = Objects.requireNonNull(
			destination, "destination");
		NativeLayeredWorldPackageCatalog.Transition transition = null;
		if(nativeLayeredWorldPackageCatalog!=null){
			if(hasWorldBuilderDraftTerrain(checkedDestination)
				||(source!=null&&hasWorldBuilderDraftTerrain(source))){
				transition=nativeLayeredWorldPackageCatalog
					.prepareResolvedTransition(
						source,checkedDestination,explicit,
						source==null?null:findNativeLayeredWorldPackage(source)
							.orElse(null),
						findNativeLayeredWorldPackage(checkedDestination)
							.orElse(null));
			}else{
				transition=nativeLayeredWorldPackageCatalog.prepareTransition(
					source,checkedDestination,explicit);
			}
		}
		/*
		 * Complete the non-native half of the preflight before any Player
		 * state changes. Native destinations were already checked through
		 * exact package terrain and their center presentation chunk.
		 */
		if (!hasNativeLayeredTerrain(checkedDestination)
			&& !LayeredCompatibilityPointAdapter.isSyntheticDeepLevel(
				checkedDestination)) {
			requireLegacyTerrainProjection(checkedDestination);
		}
		return transition;
	}

	public Point toRuntimeCompatibilityPoint(
		final WorldLocation location) {
		WorldLocation checked = Objects.requireNonNull(location, "location");
		return LayeredCompatibilityPointAdapter.toCompatibilityPoint(
			checked,
			getWorld().getServer().getConfig()
				.WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE,
			hasNativeLayeredTerrain(checked));
	}

	public String runtimeProjectionId(
		final WorldLocation location) {
		WorldLocation checked = Objects.requireNonNull(location, "location");
		return LayeredCompatibilityPointAdapter.projectionId(
			checked,
			getWorld().getServer().getConfig()
				.WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE,
			hasNativeLayeredTerrain(checked));
	}

	public int runtimeCompatibilityPlane(
		final WorldLocation location) {
		WorldLocation checked = Objects.requireNonNull(location, "location");
		return LayeredCompatibilityPointAdapter.compatibilityPlane(
			checked,
			getWorld().getServer().getConfig()
				.WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE,
			hasNativeLayeredTerrain(checked));
	}

	/**
	 * Reconstructs an unchanged Point proposal inside its established signed
	 * scope. Native movement may cross any adjacent package sector but cannot
	 * enter an absent tile. An explicit transition may leave native scope and
	 * is then decoded by the legacy/synthetic rollback adapter.
	 */
	public WorldLocation fromRuntimeCompatibilityPoint(
		final Point point,
		final WorldLocation currentScope,
		final boolean allowExplicitScopeExit) {
		Point checked = Objects.requireNonNull(point, "point");
		if (currentScope != null
			&& hasNativeLayeredTerrain(currentScope)) {
			WorldLocation candidate =
				LayeredCompatibilityPointAdapter.fromCompatibilityPoint(
					checked,
					currentScope,
					getWorld().getServer().getConfig()
						.WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE,
					false,
					true);
			if (hasNativeLayeredTerrain(candidate)) {
				prepareNativeLayeredTransition(
					currentScope, candidate, allowExplicitScopeExit);
				return candidate;
			}
			if (!allowExplicitScopeExit) {
				throw new IllegalArgumentException(
					"Ordinary movement cannot leave native package terrain");
			}
			WorldLocation destination =
				LayeredCompatibilityPointAdapter.fromCompatibilityPoint(
				checked,
				null,
				getWorld().getServer().getConfig()
					.WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE,
				true);
			prepareNativeLayeredTransition(
				currentScope, destination, true);
			return destination;
		}
		WorldLocation destination =
			LayeredCompatibilityPointAdapter.fromCompatibilityPoint(
			checked,
			currentScope,
			getWorld().getServer().getConfig()
				.WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE,
			allowExplicitScopeExit);
		prepareNativeLayeredTransition(
			currentScope, destination, allowExplicitScopeExit);
		return destination;
	}

	/**
	 * Resolves one logical tile through its exact legacy fragment while packed
	 * terrain remains the compatibility backend.
	 */
	public TileValue getTile(final WorldLocation location) {
		if (hasNativeLayeredTerrain(location)) {
			return nativeLayeredTile(location);
		}
		if (LayeredCompatibilityPointAdapter.isSyntheticDeepLevel(location)) {
			LayeredCompatibilityPointAdapter.toCompatibilityPoint(
				location,
				getWorld().getServer().getConfig()
					.WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE);
			return syntheticDeepFixtureTile();
		}
		Point packed = requireLegacyTerrainProjection(location);
		return getTile(packed);
	}

	public TileValue getMutableTile(final WorldLocation location) {
		if (hasNativeLayeredTerrain(location)) {
			throw new UnsupportedOperationException(
				"Native layered package terrain is immutable");
		}
		if (LayeredCompatibilityPointAdapter.isSyntheticDeepLevel(location)) {
			LayeredCompatibilityPointAdapter.toCompatibilityPoint(
				location,
				getWorld().getServer().getConfig()
					.WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE);
			throw new UnsupportedOperationException(
				"Synthetic deep fixture terrain is immutable");
		}
		Point packed = requireLegacyTerrainProjection(location);
		return getMutableTile(packed.getX(), packed.getY());
	}

	private static TileValue syntheticDeepFixtureTile() {
		TileValue tile = new TileValue();
		tile.overlay = 0;
		tile.initializeTerrainCollision();
		return tile;
	}

	private TileValue nativeLayeredTile(final WorldLocation location) {
		NativeLayeredWorldPackage owner = findNativeLayeredWorldPackage(
			location)
			.orElseThrow(() -> new IllegalStateException(
				"Native layered terrain has no package owner at "
					+ location));
		NativeLayeredTerrainTile source =
			getWorld().getServer().getConfig().WORLD_BUILDER_MODE
				?getWorld().getServer().getWorldEditorSessions()
					.resolveNativeTerrainTile(
						location,owner.findTile(location).orElse(null))
				:owner.findTile(location).orElse(null);
		if(source==null)throw new IllegalStateException(
				"Native layered terrain disappeared after startup validation: "
					+ location);
		TileValue tile = new TileValue();
		tile.overlay = (byte) source.getOverlay();
		tile.diagWallVal = (short) source.getDiagonalWall();
		tile.horizontalWallVal = (byte) source.getHorizontalWall();
		tile.verticalWallVal = (byte) source.getVerticalWall();
		tile.elevation = (byte) source.getElevation();
		tile.initializeTerrainCollision();
		NativeLayeredTerrainCollisionPlan.derive(
			source,
			nativeLayeredNeighbor(owner, location, 1, 0),
			nativeLayeredNeighbor(owner, location, 0, 1),
			this::nativeTerrainOverlayBlocks,
			this::nativeTerrainWallBlocks,
			WorldLoader::projectileClipAllowed,
			this::nativeTerrainStructuralCombatProjectileWall,
			this::nativeTerrainEnemyProjectileFenceWall)
			.applyTo(tile);
		return nativeLayeredGameObjects.applyCollision(location, tile);
	}

	private NativeLayeredTerrainTile nativeLayeredNeighbor(
		final NativeLayeredWorldPackage owner,
		final WorldLocation location,
		final int deltaX,
		final int deltaY) {
		WorldCoordinate coordinate = location.getCoordinate();
		WorldLocation neighbor = new WorldLocation(
			location.getWorldSpace(),
			new WorldCoordinate(
				Math.addExact(coordinate.getX(), deltaX),
				Math.addExact(coordinate.getY(), deltaY),
				coordinate.getLevel()));
		NativeLayeredTerrainTile source=owner.findTile(neighbor).orElse(null);
		if(!getWorld().getServer().getConfig().WORLD_BUILDER_MODE)return source;
		return getWorld().getServer().getWorldEditorSessions()
			.resolveNativeTerrainTile(neighbor,source);
	}

	private boolean nativeTerrainOverlayBlocks(final int overlayId) {
		return overlayId > 0
			&& getWorld().getServer().getEntityHandler()
				.getTileDef(overlayId - 1)
				.getObjectType() != 0;
	}

	private boolean nativeTerrainWallBlocks(final int wallId) {
		if (wallId <= 0) {
			return false;
		}
		DoorDef definition =
			getWorld().getServer().getEntityHandler()
				.getDoorDef(wallId - 1);
		return definition != null
			&& definition.getUnknown() == 0
			&& definition.getDoorType() != 0;
	}

	private CombatProjectileCollision.Cover nativeTerrainCombatProjectileCover(
			final int wallId) {
		if (!nativeTerrainWallBlocks(wallId)) {
			return CombatProjectileCollision.Cover.NONE;
		}
		return CombatProjectileCollision.boundaryCover(
			getWorld().getServer().getEntityHandler()
				.getDoorDef(wallId - 1));
	}

	private boolean nativeTerrainStructuralCombatProjectileWall(
			final int wallId) {
		return nativeTerrainCombatProjectileCover(wallId)
			== CombatProjectileCollision.Cover.STRUCTURAL;
	}

	private boolean nativeTerrainEnemyProjectileFenceWall(
			final int wallId) {
		return nativeTerrainCombatProjectileCover(wallId)
			== CombatProjectileCollision.Cover.ENEMY_ONLY_FENCE;
	}

	public NativeLayeredWorldPackage getNativeLayeredWorldPackage() {
		return nativeLayeredWorldPackage;
	}

	public int getNativeLayeredWorldPackageCount() {
		return nativeLayeredWorldPackageCatalog == null
			? 0 : nativeLayeredWorldPackageCatalog.size();
	}

	public String getNativeLayeredWorldRuntimeProfileId() {
		return nativeLayeredWorldRuntimeProfile.getId();
	}

	/**
	 * True only for an explicitly selected, validated complete-world package.
	 * Fixture packages remain additive and ordinary disabled worlds remain
	 * legacy-populated.
	 */
	public boolean replacesLegacyBasePopulation() {
		return nativeLayeredWorldPackageCatalog != null
			&& nativeLayeredWorldRuntimeProfile
				.replacesLegacyBasePopulation();
	}

	public void populateNativeLayeredPlacements() {
		if (nativeLayeredWorldPackageCatalog == null) {
			return;
		}
		if (nativeLayeredPlacementsPopulated) {
			throw new IllegalStateException(
				"Native layered placements were already populated");
		}
		int npcCount = 0;
		int groundItemCount = 0;
		int sceneryCount = 0;
		int boundaryCount = 0;
		for (NativeLayeredWorldPackage worldPackage
			: nativeLayeredWorldPackageCatalog.getPackages()) {
		for (NativeLayeredPlacementSet set
			: worldPackage.getPlacementSets().values()) {
			for (NativeLayeredNpcPlacement placement : set.getNpcs()) {
				if (world.getServer().getEntityHandler()
						.getNpcDef(placement.getNpcId()) == null) {
					throw new IllegalStateException(
						"Native layered NPC definition is unavailable: "
							+ placement.getNpcId());
				}
				WorldLocation location = placement.getStart();
				Npc npc = new Npc(
					world,
					placement.getNpcId(),
					location,
					placement.getMinX(),
					placement.getMaxX(),
					placement.getMinY(),
					placement.getMaxY());
				npc.setAuthoredRespawnSeconds(
					placement.getRespawnSeconds());
				markNativeLayeredPlacement(
					npc,
					worldPackage.getPackageId(),
					placement.getPlacementId(),
					NATIVE_LAYERED_NPC_KIND);
				world.registerNpc(npc);
				npcCount++;
			}
			for (NativeLayeredGroundItemPlacement placement
				: set.getGroundItems()) {
				if (world.getServer().getEntityHandler()
						.getItemDef(placement.getItemId()) == null) {
					throw new IllegalStateException(
						"Native layered item definition is unavailable: "
							+ placement.getItemId());
				}
				GroundItem item =
					world.registerNativeLayeredGroundItem(placement);
				if (item == null) {
					throw new IllegalStateException(
						"Native layered ground item was refused: "
							+ placement.getPlacementId());
				}
				markNativeLayeredPlacement(
					item,
					worldPackage.getPackageId(),
					placement.getPlacementId(),
					NATIVE_LAYERED_GROUND_ITEM_KIND);
				groundItemCount++;
			}
			for (NativeLayeredSceneryPlacement placement
				: set.getScenery()) {
				if (world.getServer().getEntityHandler()
						.getGameObjectDef(placement.getSceneryId()) == null) {
					throw new IllegalStateException(
						"Native layered scenery definition is unavailable: "
							+ placement.getSceneryId());
				}
				populateNativeLayeredGameObject(
					worldPackage.getPackageId(),
					placement.getPlacementId(),
					placement.getLocation(),
					placement.getSceneryId(),
					placement.getDirection(),
					GameObjectType.SCENERY,
					NATIVE_LAYERED_SCENERY_KIND);
				sceneryCount++;
			}
			for (NativeLayeredBoundaryPlacement placement
				: set.getBoundaries()) {
				if (world.getServer().getEntityHandler()
						.getDoorDef(placement.getBoundaryId()) == null) {
					throw new IllegalStateException(
						"Native layered boundary definition is unavailable: "
							+ placement.getBoundaryId());
				}
				populateNativeLayeredGameObject(
					worldPackage.getPackageId(),
					placement.getPlacementId(),
					placement.getLocation(),
					placement.getBoundaryId(),
					placement.getDirection(),
					GameObjectType.BOUNDARY,
					NATIVE_LAYERED_BOUNDARY_KIND);
				boundaryCount++;
			}
		}
		}
		nativeLayeredPlacementsPopulated = true;
		LOGGER.info(
			"Populated {} native layered package(s) with {} NPC, {} "
				+ "ground-item, {} scenery, and {} boundary placements",
			nativeLayeredWorldPackageCatalog.size(),
			npcCount,
			groundItemCount,
			sceneryCount,
			boundaryCount);
	}

	private void populateNativeLayeredGameObject(
		final String packageId,
		final String placementId,
		final WorldLocation location,
		final int objectId,
		final int direction,
		final GameObjectType type,
		final String kind) {
		if (!isLayeredSpatialRuntimeAuthorityEnabled()) {
			throw new IllegalStateException(
				"Native layered objects require layered spatial authority");
		}
		GameObject object = new GameObject(
			world,
			new GameObjectLoc(
				objectId,
				location.getCoordinate().getX(),
				location.getCoordinate().getY(),
				direction,
				type.getId()));
		GameTickEventRestorationCollisionFootprintPlanner.Result footprint =
			world.projectGameObjectCollisionFootprint(
				object, Operation.REGISTER, false);
		if (!footprint.isFootprintAvailable()) {
			throw new IllegalStateException(
				"Native layered object collision footprint is unavailable for "
					+ placementId + ": " + footprint.getReason());
		}
		for (com.openrsc.server.event.rsc
				.GameTickEventRestorationTransientRollbackSnapshot
				.CollisionContribution contribution
					: footprint.getContributions()) {
			requireNativeLayeredCollisionTerrain(
				location, placementId, contribution);
		}
		object.setInitialWorldLocation(location);
		markNativeLayeredPlacement(
			object, packageId, placementId, kind);
		if (nativeLayeredGameObjects.register(
			nativeLayeredGameObjects.getGeneration(),
			nativePlacementKey(packageId, placementId),
			location,
			type.getId(),
			direction,
			object,
			footprint,
			nativeLayeredNpcBlockingSceneryFootprint(
				object, location, placementId),
			isCombatProjectileStructuralCover(object),
			isEnemyProjectileFenceCover(object)) == null) {
			throw new IllegalStateException(
				"Native layered object population generation became stale");
		}
		object.attachNativeLayeredCollisionRegistrationState(
			GameObjectCollisionRegistrationState.capture(object, footprint));
		layeredSpatialEntityIndex.synchronize(object, null, location);
	}

	private void requireNativeLayeredCollisionTerrain(
		final WorldLocation origin,
		final String placementId,
		final com.openrsc.server.event.rsc
			.GameTickEventRestorationTransientRollbackSnapshot
			.CollisionContribution contribution) {
		WorldLocation collisionLocation = new WorldLocation(
			origin.getWorldSpace(),
			new WorldCoordinate(
				contribution.getX(),
				contribution.getY(),
				origin.getCoordinate().getLevel()));
		NativeLayeredWorldPackage collisionOwner =
			findNativeLayeredWorldPackage(collisionLocation)
				.orElse(null);
		NativeLayeredWorldPackage originOwner =
			findNativeLayeredWorldPackage(origin).orElse(null);
		if (collisionOwner == null
			|| originOwner == null
			|| collisionOwner != originOwner) {
			throw new IllegalStateException(
				"Native layered object collision leaves its package terrain for "
					+ placementId + ": " + collisionLocation);
		}
	}

	private List<WorldLocation> nativeLayeredNpcBlockingSceneryFootprint(
		final GameObject object,
		final WorldLocation origin,
		final String placementId) {
		GameObject checkedObject = Objects.requireNonNull(object, "object");
		WorldLocation checkedOrigin = Objects.requireNonNull(origin, "origin");
		if (!checkedObject.isScenery()
			|| checkedObject.getGameObjectDef().getType() == 0) {
			return Collections.emptyList();
		}
		int width;
		int height;
		if (checkedObject.getDirection() == 0
			|| checkedObject.getDirection() == 4) {
			width = checkedObject.getGameObjectDef().getWidth();
			height = checkedObject.getGameObjectDef().getHeight();
		} else {
			width = checkedObject.getGameObjectDef().getHeight();
			height = checkedObject.getGameObjectDef().getWidth();
		}
		NativeLayeredWorldPackage owner =
			findNativeLayeredWorldPackage(checkedOrigin).orElse(null);
		if (owner == null) {
			throw new IllegalStateException(
				"Native layered NPC-blocking scenery has no package owner: "
					+ placementId);
		}
		List<WorldLocation> footprint =
			new ArrayList<WorldLocation>(Math.multiplyExact(width, height));
		WorldCoordinate coordinate = checkedOrigin.getCoordinate();
		for (int offsetX = 0; offsetX < width; offsetX++) {
			for (int offsetY = 0; offsetY < height; offsetY++) {
				WorldLocation tile = new WorldLocation(
					checkedOrigin.getWorldSpace(),
					new WorldCoordinate(
						Math.addExact(coordinate.getX(), offsetX),
						Math.addExact(coordinate.getY(), offsetY),
						coordinate.getLevel()));
				if (findNativeLayeredWorldPackage(tile).orElse(null) != owner) {
					throw new IllegalStateException(
						"Native layered NPC-blocking scenery leaves its "
							+ "package terrain for " + placementId
							+ " at " + tile);
				}
				footprint.add(tile);
			}
		}
		return footprint;
	}

	public boolean hasNativeLayeredGameObjectIdentity(
		final GameObject object) {
		return object != null
			&& object.getLoc().getNativeLayeredGameObjectIdentity() != null;
	}

	public boolean prepareNativeLayeredGameObject(
		final GameObject object) {
		GameObject checked = Objects.requireNonNull(object, "object");
		NativeLayeredGameObjectIdentity identity =
			checked.getLoc().getNativeLayeredGameObjectIdentity();
		if (identity == null) {
			throw new IllegalArgumentException(
				"GameObject has no native layered identity");
		}
		NativeLayeredWorldPackage owner = findNativeLayeredWorldPackage(
			identity.getLocation()).orElse(null);
		if (owner == null
			|| !owner.getPackageId().equals(identity.getPackageId())) {
			throw new IllegalStateException(
				"Native layered object package identity differs");
		}
		if (identity.getGeneration()
			!= nativeLayeredGameObjects.getGeneration()) {
			return false;
		}
		if ((NATIVE_LAYERED_SCENERY_KIND.equals(identity.getKind())
				&& checked.getType() != GameObjectType.SCENERY.getId())
			|| (NATIVE_LAYERED_BOUNDARY_KIND.equals(identity.getKind())
				&& checked.getType() != GameObjectType.BOUNDARY.getId())) {
			throw new IllegalStateException(
				"Native layered object kind differs from its runtime type");
		}
		if (checked.getLoc().getX()
				!= identity.getLocation().getCoordinate().getX()
			|| checked.getLoc().getY()
				!= identity.getLocation().getCoordinate().getY()) {
			throw new IllegalStateException(
				"Native layered object coordinates differ from its identity");
		}
		if (checked.getLocation() == null) {
			checked.setInitialWorldLocation(identity.getLocation());
		} else if (!identity.getLocation().equals(
				checked.getWorldLocation())) {
			throw new IllegalStateException(
				"Native layered object location differs from its identity");
		}
		setNativeLayeredPlacementAttributes(
			checked,
			identity.getPackageId(),
			identity.getPlacementId(),
			identity.getKind());
		return true;
	}

	public GameObject findNativeLayeredGameObject(
		final GameObject object) {
		NativeLayeredGameObjectIdentity identity = Objects.requireNonNull(
			object, "object").getLoc().getNativeLayeredGameObjectIdentity();
		return identity == null ? null
			: nativeLayeredGameObjects.find(nativePlacementKey(identity));
	}

	public GameObject findNativeLayeredScenery(
		final WorldLocation location) {
		return nativeLayeredGameObjects.find(
			Objects.requireNonNull(location, "location"),
			GameObjectType.SCENERY.getId(),
			0);
	}

	public void inheritNativeLayeredGameObjectIdentity(
		final GameObject source,
		final GameObject replacement) {
		GameObject checkedSource = Objects.requireNonNull(source, "source");
		GameObject checkedReplacement = Objects.requireNonNull(
			replacement, "replacement");
		NativeLayeredGameObjectIdentity identity =
			checkedSource.getLoc().getNativeLayeredGameObjectIdentity();
		if (identity == null || !isNativeLayeredGameObject(checkedSource)) {
			throw new IllegalStateException(
				"Native layered replacement source identity is unavailable");
		}
		checkedReplacement.getLoc().assignNativeLayeredGameObjectIdentity(
			identity);
		if (!prepareNativeLayeredGameObject(checkedReplacement)) {
			throw new IllegalStateException(
				"Native layered replacement generation became stale");
		}
	}

	public boolean isNativeLayeredGameObject(
		final GameObject object) {
		if (!hasNativeLayeredGameObjectIdentity(object)) {
			return false;
		}
		NativeLayeredGameObjectIdentity identity =
			object.getLoc().getNativeLayeredGameObjectIdentity();
		return identity.getGeneration()
				== nativeLayeredGameObjects.getGeneration()
			&& isNativeLayeredPlacement(object, identity.getKind());
	}

	public void applyNativeLayeredGameObjectTransaction(
		final GameObject oldObject,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			oldUnregisterFootprint,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			oldRollbackRegisterFootprint,
		final GameObject newObject,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			newRegisterFootprint) {
		if (oldObject == null && newObject == null) {
			throw new IllegalArgumentException(
				"Native layered object transaction is empty");
		}
		NativeLayeredGameObjectIdentity identity = oldObject != null
			? oldObject.getLoc().getNativeLayeredGameObjectIdentity()
			: newObject.getLoc().getNativeLayeredGameObjectIdentity();
		if (identity == null
			|| (oldObject != null && !isNativeLayeredGameObject(oldObject))
			|| (newObject != null
				&& !identity.equals(newObject.getLoc()
					.getNativeLayeredGameObjectIdentity()))) {
			throw new IllegalStateException(
				"Native layered object transaction identity differs");
		}
		if (oldObject != null) {
			if (oldUnregisterFootprint == null
				|| oldUnregisterFootprint.getOperation()
					!= Operation.UNREGISTER
				|| oldRollbackRegisterFootprint == null
				|| oldRollbackRegisterFootprint.getOperation()
					!= Operation.REGISTER) {
				throw new IllegalArgumentException(
					"Native layered removal footprints are invalid");
			}
			if (oldObject.getRegion() != null
				|| oldObject.getLocation() == null
				|| oldObject.isRemoved()) {
				throw new IllegalStateException(
					"Native layered removal source state is invalid");
			}
			layeredSpatialEntityIndex.requireMembership(
				oldObject, identity.getLocation());
		}
		GameObjectCollisionRegistrationState newCollision = null;
		if (newObject != null) {
			if (newRegisterFootprint == null
				|| newRegisterFootprint.getOperation()
					!= Operation.REGISTER) {
				throw new IllegalArgumentException(
					"Native layered registration footprint is invalid");
			}
			if (newObject.getRegion() != null
				|| newObject.getLocation() == null
				|| newObject.isRemoved()
				|| newObject.getCollisionRegistrationState() != null) {
				throw new IllegalStateException(
					"Native layered registration target state is invalid");
			}
			for (com.openrsc.server.event.rsc
					.GameTickEventRestorationTransientRollbackSnapshot
					.CollisionContribution contribution
						: newRegisterFootprint.getContributions()) {
				requireNativeLayeredCollisionTerrain(
					identity.getLocation(),
					identity.getPlacementId(),
					contribution);
			}
			newCollision = GameObjectCollisionRegistrationState.capture(
				newObject, newRegisterFootprint);
		}

		List<WorldLocation> oldNpcBlockingScenery =
			oldObject == null
				? Collections.<WorldLocation>emptyList()
				: nativeLayeredNpcBlockingSceneryFootprint(
					oldObject,
					identity.getLocation(),
					identity.getPlacementId());
		List<WorldLocation> newNpcBlockingScenery =
			newObject == null
				? Collections.<WorldLocation>emptyList()
				: nativeLayeredNpcBlockingSceneryFootprint(
					newObject,
					identity.getLocation(),
					identity.getPlacementId());
		long generation = identity.getGeneration();
		if (oldObject == null) {
			if (nativeLayeredGameObjects.register(
					generation, nativePlacementKey(identity),
					identity.getLocation(), newObject.getType(),
					newObject.getDirection(), newObject,
					newRegisterFootprint,
					newNpcBlockingScenery,
					isCombatProjectileStructuralCover(newObject),
					isEnemyProjectileFenceCover(newObject)) == null) {
				return;
			}
			try {
				layeredSpatialEntityIndex.synchronize(
					newObject, null, identity.getLocation());
			} catch (RuntimeException failure) {
				nativeLayeredGameObjects.unregister(
					generation, nativePlacementKey(identity), newObject);
				throw failure;
			}
		} else if (newObject == null) {
			if (nativeLayeredGameObjects.unregister(
					generation, nativePlacementKey(identity),
					oldObject) == null) {
				return;
			}
			try {
				layeredSpatialEntityIndex.remove(
					oldObject, identity.getLocation());
			} catch (RuntimeException failure) {
				nativeLayeredGameObjects.register(
					generation, nativePlacementKey(identity),
					identity.getLocation(), oldObject.getType(),
					oldObject.getDirection(), oldObject,
					oldRollbackRegisterFootprint,
					oldNpcBlockingScenery,
					isCombatProjectileStructuralCover(oldObject),
					isEnemyProjectileFenceCover(oldObject));
				throw failure;
			}
		} else {
			if (nativeLayeredGameObjects.replace(
					generation, nativePlacementKey(identity), oldObject,
					identity.getLocation(), newObject.getType(),
					newObject.getDirection(), newObject,
					newRegisterFootprint,
					newNpcBlockingScenery,
					isCombatProjectileStructuralCover(newObject),
					isEnemyProjectileFenceCover(newObject)) == null) {
				return;
			}
			try {
				layeredSpatialEntityIndex.replace(
					oldObject, newObject, identity.getLocation());
			} catch (RuntimeException failure) {
				nativeLayeredGameObjects.replace(
					generation, nativePlacementKey(identity), newObject,
					identity.getLocation(), oldObject.getType(),
					oldObject.getDirection(), oldObject,
					oldRollbackRegisterFootprint,
					oldNpcBlockingScenery,
					isCombatProjectileStructuralCover(oldObject),
					isEnemyProjectileFenceCover(oldObject));
				throw failure;
			}
		}
		if (oldObject != null) {
			oldObject.removeNativeLayeredTransactionState();
			oldObject.clearOrderedCollisionRegistrationState();
		}
		if (newObject != null) {
			newObject.attachNativeLayeredCollisionRegistrationState(
				newCollision);
		}
	}

	private static CombatProjectileCollision.Cover combatProjectileCover(final GameObject object) {
		if (object == null || object.getID() == 1147) {
			return CombatProjectileCollision.Cover.NONE;
		}
		if (object.isScenery()) {
			return CombatProjectileCollision.sceneryCover(object.getGameObjectDef());
		}
		return CombatProjectileCollision.boundaryCover(object.getDoorDef());
	}

	private static boolean isCombatProjectileStructuralCover(final GameObject object) {
		return combatProjectileCover(object) == CombatProjectileCollision.Cover.STRUCTURAL;
	}

	private static boolean isEnemyProjectileFenceCover(final GameObject object) {
		return combatProjectileCover(object) == CombatProjectileCollision.Cover.ENEMY_ONLY_FENCE;
	}

	public int getNativeLayeredSceneryCount() {
		return nativeLayeredGameObjects.countType(
			GameObjectType.SCENERY.getId());
	}

	public int getNativeLayeredBoundaryCount() {
		return nativeLayeredGameObjects.countType(
			GameObjectType.BOUNDARY.getId());
	}

	public int getNativeLayeredObjectCollisionTileCount() {
		return nativeLayeredGameObjects.getCollisionTileCount();
	}

	public boolean areNativeLayeredPlacementsPopulated() {
		return nativeLayeredPlacementsPopulated;
	}

	public boolean isNativeLayeredPlacement(
		final Entity entity, final String kind) {
		if (entity == null
			|| nativeLayeredWorldPackageCatalog == null
			|| kind == null) {
			return false;
		}
		String packageId = entity.getAttribute(
			NATIVE_LAYERED_PLACEMENT_PACKAGE_ATTRIBUTE, "");
		NativeLayeredWorldPackage owner =
			findNativeLayeredWorldPackage(entity.getWorldLocation())
				.orElse(null);
		return kind.equals(entity.getAttribute(
				NATIVE_LAYERED_PLACEMENT_KIND_ATTRIBUTE, ""))
			&& owner != null
			&& owner.getPackageId().equals(packageId);
	}

	public void markNativeLayeredPlacement(
		final Entity entity,
		final String placementId,
		final String kind) {
		NativeLayeredWorldPackage owner = findNativeLayeredWorldPackage(
			Objects.requireNonNull(entity, "entity").getWorldLocation())
			.orElseThrow(() -> new IllegalStateException(
				"Native layered placement has no package terrain owner"));
		markNativeLayeredPlacement(
			entity, owner.getPackageId(), placementId, kind);
	}

	public void markNativeLayeredPlacement(
		final Entity entity,
		final String packageId,
		final String placementId,
		final String kind) {
		NativeLayeredWorldPackage owner =
			nativeLayeredWorldPackageCatalog == null
				? null
				: nativeLayeredWorldPackageCatalog.findPackage(
					Objects.requireNonNull(packageId, "packageId"))
					.orElse(null);
		if (owner == null) {
			throw new IllegalStateException(
				"Native layered placement package is not loaded: "
					+ packageId);
		}
		if (entity instanceof GameObject) {
			GameObject object = (GameObject) entity;
			if (!hasNativeLayeredTerrain(object.getWorldLocation())
				|| findNativeLayeredWorldPackage(object.getWorldLocation())
					.orElse(null)!=owner) {
				throw new IllegalStateException(
					"Native layered object is outside its package terrain");
			}
			object.getLoc().assignNativeLayeredGameObjectIdentity(
				new NativeLayeredGameObjectIdentity(
					owner.getPackageId(),
					nativeLayeredGameObjects.getGeneration(),
					placementId,
					kind,
					object.getWorldLocation()));
		}
		setNativeLayeredPlacementAttributes(
			entity, owner.getPackageId(), placementId, kind);
	}

	private void setNativeLayeredPlacementAttributes(
		final Entity entity,
		final String packageId,
		final String placementId,
		final String kind) {
		entity.setAttribute(
			NATIVE_LAYERED_PLACEMENT_ID_ATTRIBUTE, placementId);
		entity.setAttribute(NATIVE_LAYERED_PLACEMENT_KIND_ATTRIBUTE, kind);
		entity.setAttribute(
			NATIVE_LAYERED_PLACEMENT_PACKAGE_ATTRIBUTE,
			packageId);
	}

	private static String nativePlacementKey(
		final NativeLayeredGameObjectIdentity identity) {
		return nativePlacementKey(
			identity.getPackageId(), identity.getPlacementId());
	}

	private static String nativePlacementKey(
		final String packageId,
		final String placementId) {
		return Objects.requireNonNull(packageId, "packageId")
			+ ":" + Objects.requireNonNull(placementId, "placementId");
	}

	private Point requireLegacyTerrainProjection(
		final WorldLocation location) {
		WorldLocation checked = Objects.requireNonNull(location, "location");
		if (LayeredCompatibilityPointAdapter.isSyntheticDeepLevel(checked)) {
			return LayeredCompatibilityPointAdapter.toCompatibilityPoint(
				checked,
				getWorld().getServer().getConfig()
					.WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE);
		}
		WorldRegionKey key = WorldRegionKey.from(checked);
		LegacyLogicalTileAddress address = LegacyLogicalTileAddress.resolve(
			key,
			Math.floorMod(
				checked.getCoordinate().getX(), WorldRegionKey.REGION_SIZE),
			Math.floorMod(
				checked.getCoordinate().getY(), WorldRegionKey.REGION_SIZE));
		if (!address.isLegacyRepresentable()
			|| !checked.equals(address.getLogicalLocation())) {
			throw new IllegalArgumentException(
				"Logical tile has no exact legacy terrain projection: "
					+ checked);
		}
		return address.getLegacyPoint();
	}

	/**
	 * Captures count-only authored provenance for exact safety sources without
	 * retaining entity, Region, collection, or lifecycle handles.
	 */
	public LayeredPackedRegionAuthoredProvenanceObservation
		captureAuthoredProvenance(
			final LayeredPackedRegionAuthoredPlacementManifest manifest,
			final LayeredPackedRegionRetirementSafetyAssessment safety,
			final long observedAtTick) {
		return captureAuthoredProvenance(
			manifest, null, safety, observedAtTick);
	}

	public LayeredPackedRegionAuthoredProvenanceObservation
		captureAuthoredProvenance(
			final LayeredPackedRegionAuthoredPlacementManifest manifest,
			final LayeredPackedRegionAuthoredPopulationOutcome populationOutcome,
			final LayeredPackedRegionRetirementSafetyAssessment safety,
			final long observedAtTick) {
		LayeredPackedRegionAuthoredProvenanceObservation.Builder builder =
			LayeredPackedRegionAuthoredProvenanceObservation.builder(
				manifest, populationOutcome, safety, observedAtTick);
		synchronized (layeredRegionLifecycleLock) {
			for (Npc npc : world.getNpcs()) {
				if (npc.getAuthoredPlacementIdentity() != null) {
					builder.recordRuntimeInstance(
						npc.getAuthoredPlacementIdentity(), npc.getID(),
						npc.getX() / Constants.REGION_SIZE,
						npc.getY() / Constants.REGION_SIZE,
						!npc.isRemoved() && !npc.isRespawning());
				}
			}
			for (ConcurrentHashMap<Integer, Region> yRegions
				: regions.values()) {
				for (Region region : yRegions.values()) {
					region.recordAuthoredProvenance(builder);
				}
			}
		}
		return builder.build();
	}

	/**
	 * Captures one detached point-in-time NPC census for exact safety sources.
	 * The returned observation has no entity, arrival, retention, or lifecycle
	 * authority.
	 */
	public LayeredPackedRegionActiveNpcResidencyObservation
		captureActiveNpcResidency(
			final LayeredPackedRegionAuthoredReconstructionRecipe recipe,
			final LayeredPackedRegionRetirementSafetyAssessment safety,
			final long observedAtTick,
			final int maximumInstances,
			final int maximumRelevantDetails) {
		List<LayeredPackedRegionActiveNpcResidencyObservation.NpcInstanceSnapshot>
			instances = new ArrayList<
				LayeredPackedRegionActiveNpcResidencyObservation.NpcInstanceSnapshot>();
		synchronized (layeredRegionLifecycleLock) {
			synchronized (world.getNpcs()) {
				for (Npc npc : world.getNpcs()) {
					Point location = npc.getLocation();
					instances.add(new LayeredPackedRegionActiveNpcResidencyObservation
						.NpcInstanceSnapshot(
							npc.getAuthoredPlacementIdentity(), npc.getID(),
							location.getX() / Constants.REGION_SIZE,
							location.getY() / Constants.REGION_SIZE,
							!npc.isRemoved() && !npc.isRespawning()));
				}
			}
		}
		return LayeredPackedRegionActiveNpcResidencyObservation.observe(
			recipe, safety, observedAtTick, instances, maximumInstances,
			maximumRelevantDetails);
	}

	/**
	 * Correlates one exact proposal event inventory with a fresh bounded NPC
	 * census. This diagnostic performs no owner preservation and cannot turn a
	 * point-in-time match into lifecycle readiness.
	 */
	public LayeredPackedRegionNpcOwnerEventContinuityAssessment
		captureLayeredPackedRegionNpcOwnerEventContinuity(
			final LayeredPackedRegionRetirementRefinementProposal proposal,
			final LayeredPackedRegionEventOwnershipInventory inventory,
			final LayeredPackedRegionAuthoredReconstructionRecipe recipe,
			final int maximumCandidateSources,
			final int maximumNpcInstances,
			final int maximumRelevantNpcDetails,
			final int maximumEventDetails) {
		LayeredPackedRegionRetirementRefinementProposal checkedProposal =
			Objects.requireNonNull(proposal, "proposal");
		LayeredPackedRegionEventOwnershipInventory checkedInventory =
			Objects.requireNonNull(inventory, "inventory");
		LayeredPackedRegionAuthoredReconstructionRecipe checkedRecipe =
			Objects.requireNonNull(recipe, "recipe");
		if (checkedProposal.getGeneration()
				!= checkedInventory.getProposalGeneration()
			|| checkedProposal.getGeneration() != checkedRecipe.getGeneration()
			|| checkedProposal.getCandidateSourceCount()
				!= checkedInventory.getSourceCount()
			|| maximumCandidateSources < 0
			|| maximumCandidateSources
				> MAX_LAYERED_PACKED_SOURCES_PER_RETIREMENT_PLAN
			|| checkedProposal.getCandidateSourceCount()
				> maximumCandidateSources) {
			throw new IllegalArgumentException(
				"NPC owner-event capture parents do not align");
		}
		for (int index = 0;
			index < checkedProposal.getCandidateSourceCount(); index++) {
			LayeredPackedRegionRetirementRefinementProposal.CandidateSource
				candidate = checkedProposal.getCandidates().get(index);
			LayeredPackedRegionEventOwnershipInventory.SourceRecord source =
				checkedInventory.getSources().get(index);
			if (candidate.getPackedRegionX() != source.getPackedRegionX()
				|| candidate.getPackedRegionY() != source.getPackedRegionY()) {
				throw new IllegalArgumentException(
					"NPC owner-event capture source order does not align");
			}
		}
		synchronized (layeredRegionLifecycleLock) {
			long observedAtTick = getWorld().getServer().getCurrentTick();
			if (observedAtTick < checkedInventory.getObservedAtTick()) {
				throw new IllegalArgumentException(
					"NPC owner-event census predates its event inventory");
			}
			LayeredPackedRegionRetirementSafetyAssessment safety =
				assessLayeredPackedRegionRetirementRefinementCandidatesLocked(
					checkedProposal, maximumCandidateSources, observedAtTick);
			LayeredPackedRegionActiveNpcResidencyObservation observation =
				captureActiveNpcResidency(
					checkedRecipe, safety, observedAtTick, maximumNpcInstances,
					maximumRelevantNpcDetails);
			return LayeredPackedRegionNpcOwnerEventContinuityAssessment.assess(
				checkedInventory, observation, true, false,
				maximumEventDetails);
		}
	}

	// originally private, set to public to access for reset event
	public ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>> getRegions() {
		return regions;
	}

	/**
	 * Reads only the active server definition table and returns detached
	 * authored collision-definition scalars in exact replay order.
	 */
	public LayeredPackedRegionAuthoredCollisionDefinitionCapture
		captureLayeredPackedRegionAuthoredCollisionDefinitions(
			final LayeredPackedRegionAuthoredReplayPlan replayPlan,
			final LayeredPackedRegionIsolatedAuthoredObjectVerification
				membershipVerification) {
		if (world == null || world.getServer() == null) {
			throw new IllegalStateException(
				"Active server definition table is unavailable");
		}
		final EntityHandler entityHandler =
			world.getServer().getEntityHandler();
		if (entityHandler == null) {
			throw new IllegalStateException(
				"Active server definition table is unavailable");
		}
		return LayeredPackedRegionAuthoredCollisionDefinitionCapture.capture(
			replayPlan, membershipVerification,
			new LayeredPackedRegionAuthoredCollisionDefinitionCapture
				.DefinitionLookup() {
				@Override
				public LayeredPackedRegionAuthoredCollisionDefinitionCapture
					.DefinitionSnapshot lookupScenery(
						final int objectId) {
					GameObjectDef definition =
						entityHandler.getGameObjectDef(objectId);
					return definition == null ? null
						: LayeredPackedRegionAuthoredCollisionDefinitionCapture
							.DefinitionSnapshot.scenery(
								definition.getType(),
								definition.getWidth(),
								definition.getHeight(),
								definition.getName());
				}

				@Override
				public LayeredPackedRegionAuthoredCollisionDefinitionCapture
					.DefinitionSnapshot lookupBoundary(
						final int objectId) {
					DoorDef definition =
						entityHandler.getDoorDef(objectId);
					return definition == null ? null
						: LayeredPackedRegionAuthoredCollisionDefinitionCapture
							.DefinitionSnapshot.boundary(
								definition.getDoorType(),
								definition.getName());
				}
			});
	}

	/**
	 * Composes the read-only active definition capture with the detached
	 * collision planner. No Region or collision state is read or changed.
	 */
	public LayeredPackedRegionAuthoredCollisionFootprintPlan
		defineLayeredPackedRegionAuthoredCollisionFootprints(
			final LayeredPackedRegionAuthoredReplayPlan replayPlan,
			final LayeredPackedRegionIsolatedAuthoredObjectVerification
				membershipVerification) {
		LayeredPackedRegionAuthoredCollisionDefinitionCapture capture =
			captureLayeredPackedRegionAuthoredCollisionDefinitions(
				replayPlan, membershipVerification);
		return LayeredPackedRegionAuthoredCollisionFootprintPlan.define(
			replayPlan, membershipVerification, capture,
			Constants.objectsProjectileClipAllowed,
			Constants.MAX_WIDTH, Constants.MAX_HEIGHT);
	}

	public World getWorld() {
		return world;
	}
}
