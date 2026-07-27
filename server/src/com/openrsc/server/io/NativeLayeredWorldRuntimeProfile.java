package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.LayeredCompatibilityPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Explicit startup ownership policy for a native layered package catalog.
 *
 * <p>The fixture profile supplements the ordinary legacy population. Complete
 * Preservation and Spoiled Milk profiles are independently pinned replacement
 * distributions and must never be selected by package shape alone.</p>
 */
public enum NativeLayeredWorldRuntimeProfile {
	FIXTURE_ADDITIVE("fixture-additive", false),
	PRESERVATION_R64_REPLACEMENT("preservation-r64-replacement", true),
	SPOILED_MILK_REPLACEMENT("spoiled-milk-replacement", true),
	SPOILED_MILK_BUILDER_DRAFT("spoiled-milk-builder-draft", true);

	public static final String DEFAULT_ID = "fixture-additive";
	public static final String PRESERVATION_PACKAGE_ID =
		"rsc-remastered.preservation-r64-parity-review";
	public static final String PRESERVATION_PACKAGE_VERSION = "0.4.0";
	public static final String PRESERVATION_MANIFEST_SHA256 =
		"560dae205d13c2034b38f52d8bb6841ee56c245fadc8e9d18361ace1346cd73f";
	public static final String SPOILED_MILK_PACKAGE_ID =
		"rsc-remastered.spoiled-milk-layered-world";
	public static final String SPOILED_MILK_PACKAGE_VERSION = "0.2.0";
	public static final String SPOILED_MILK_MANIFEST_SHA256 =
		"fab8d7d1a51e948a7d8b18769eb0b3e9f5abf9e30538abfedba4d90374b1447b";
	private static final int VANILLA_MAX_BOUNDARY_ID = 213;
	private static final int VANILLA_MAX_SCENERY_ID = 1189;
	private static final int VANILLA_MAX_NPC_ID = 793;
	private static final int VANILLA_MAX_ITEM_ID = 1289;

	private final String id;
	private final boolean replacesLegacyBasePopulation;

	NativeLayeredWorldRuntimeProfile(
		final String id,
		final boolean replacesLegacyBasePopulation) {
		this.id = id;
		this.replacesLegacyBasePopulation = replacesLegacyBasePopulation;
	}

	public static NativeLayeredWorldRuntimeProfile fromConfiguration(
		final String requested) {
		final String value = requested == null
			? "" : requested.trim().toLowerCase(Locale.ROOT);
		for (NativeLayeredWorldRuntimeProfile profile : values()) {
			if (profile.id.equals(value)) {
				return profile;
			}
		}
		throw new IllegalArgumentException(
			"Unknown native layered world runtime profile: " + requested);
	}

	public String getId() {
		return id;
	}

	public boolean replacesLegacyBasePopulation() {
		return replacesLegacyBasePopulation;
	}

	public void validate(final NativeLayeredWorldPackageCatalog catalog) {
		if (catalog == null) {
			throw new IllegalArgumentException(
				"Native layered world runtime profile requires a package catalog");
		}
		for (NativeLayeredWorldPackage worldPackage : catalog.getPackages()) {
			if (worldPackage.getPresentationChunkSize() != 24) {
				throw new IllegalStateException(
					"Native layered runtime requires 24-tile presentation chunks");
			}
		}
		switch (this) {
			case FIXTURE_ADDITIVE:
				validateFixture(catalog.getPrimaryPackage());
				return;
			case PRESERVATION_R64_REPLACEMENT:
				validatePreservation(catalog);
				return;
			case SPOILED_MILK_REPLACEMENT:
				validateSpoiledMilk(catalog);
				return;
			case SPOILED_MILK_BUILDER_DRAFT:
				validateSpoiledMilkBuilderDraft(catalog);
				return;
			default:
				throw new IllegalStateException(
					"Unhandled native layered world runtime profile: " + this);
		}
	}

	private static void validateFixture(
		final NativeLayeredWorldPackage loaded) {
		if (!loaded.declaresLevel(
				WorldSpaceId.GLOBAL,
				LayeredCompatibilityPointAdapter.SYNTHETIC_DEEP_LEVEL)) {
			throw new IllegalStateException(
				"Native layered fixture package does not declare level -2");
		}
		if (loaded.getPlacementSetCount() != 1
			|| loaded.getNpcPlacementCount() != 1
			|| loaded.getGroundItemPlacementCount() != 1
			|| loaded.getSceneryPlacementCount() != 2
			|| loaded.getBoundaryPlacementCount() != 2) {
			throw new IllegalStateException(
				"The fixture-additive profile requires exactly one placement "
					+ "set, NPC, ground item, two scenery objects, and two "
					+ "boundaries in its primary package");
		}
		final WorldLocation entry = WorldLocation.global(
			new WorldCoordinate(
				LayeredCompatibilityPointAdapter.SYNTHETIC_DEEP_ENTRY_X,
				LayeredCompatibilityPointAdapter.SYNTHETIC_DEEP_ENTRY_Y,
				LayeredCompatibilityPointAdapter.SYNTHETIC_DEEP_LEVEL));
		LayeredCompatibilityPointAdapter.toCompatibilityPoint(
			entry, false, true);
		final NativeLayeredTerrainTile entryTile = loaded.findTile(entry)
			.orElseThrow(() -> new IllegalStateException(
				"Native layered fixture package has no owner-route entry tile at "
					+ entry));
		if (entryTile.getOverlay() != 0
			|| entryTile.getVerticalWall() != 0
			|| entryTile.getHorizontalWall() != 0
			|| entryTile.getDiagonalWall() != 0) {
			throw new IllegalStateException(
				"The fixture-additive entry requires passable wall-free "
					+ "overlay-0 terrain at " + entry);
		}
		for (NativeLayeredPlacementSet set
			: loaded.getPlacementSets().values()) {
			for (NativeLayeredNpcPlacement npc : set.getNpcs()) {
				requireNativeLocation(loaded, npc.getStart());
			}
			for (NativeLayeredGroundItemPlacement item
				: set.getGroundItems()) {
				requireNativeLocation(loaded, item.getLocation());
			}
		}
	}

	private static void validatePreservation(
		final NativeLayeredWorldPackageCatalog catalog) {
		validateCompleteWorld(
			catalog,
			"preservation-r64-replacement",
			PRESERVATION_PACKAGE_ID,
			PRESERVATION_PACKAGE_VERSION,
			PRESERVATION_MANIFEST_SHA256,
			1764,
			3610,
			1010,
			26765,
			966,
			true);
	}

	private static void validateSpoiledMilk(
		final NativeLayeredWorldPackageCatalog catalog) {
		validateCompleteWorld(
			catalog,
			"spoiled-milk-replacement",
			SPOILED_MILK_PACKAGE_ID,
			SPOILED_MILK_PACKAGE_VERSION,
			SPOILED_MILK_MANIFEST_SHA256,
			1771,
			3775,
			882,
			27886,
			972,
			false);
	}

	private static void validateSpoiledMilkBuilderDraft(
		final NativeLayeredWorldPackageCatalog catalog) {
		if (catalog.size() != 1) {
			throw new IllegalStateException(
				"The spoiled-milk-builder-draft profile requires exactly one package");
		}
		final NativeLayeredWorldPackage loaded = catalog.getPrimaryPackage();
		if (!SPOILED_MILK_PACKAGE_ID.equals(loaded.getPackageId())
			|| !SPOILED_MILK_PACKAGE_VERSION.equals(loaded.getPackageVersion())
			|| loaded.getWorldSpaceCount() != 1
			|| loaded.getLevelCount() < 4
			|| loaded.getTerrainSectorCount() < 1771
			|| loaded.getPlacementSetCount() != loaded.getLevelCount()
			|| loaded.getNpcPlacementCount() != 3775
			|| loaded.getGroundItemPlacementCount() != 882
			|| loaded.getSceneryPlacementCount() != 27886
			|| loaded.getBoundaryPlacementCount() != 972) {
			throw new IllegalStateException(
				"The spoiled-milk-builder-draft profile requires an additive "
					+ "terrain-only descendant of the accepted Spoiled Milk package");
		}
		for (int level : new int[] {-1, 0, 1, 2}) {
			if (!loaded.declaresLevel(WorldSpaceId.GLOBAL, level)) {
				throw new IllegalStateException(
					"The Spoiled Milk Builder draft is missing global level " + level);
			}
		}
		final Set<Integer> placementLevels = new HashSet<Integer>();
		for (NativeLayeredPlacementSet set : loaded.getPlacementSets().values()) {
			if (!WorldSpaceId.GLOBAL.equals(set.getWorldSpace())
				|| !"layered-world-placements-v3".equals(
					set.getSourceEncoding())
				|| !placementLevels.add(Integer.valueOf(set.getLevel()))) {
				throw new IllegalStateException(
					"The Spoiled Milk Builder draft requires one global v3 "
						+ "placement set per declared level");
			}
		}
		if (placementLevels.size() != loaded.getLevelCount()) {
			throw new IllegalStateException(
				"The Spoiled Milk Builder draft placement levels are incomplete");
		}
	}

	private static void validateCompleteWorld(
		final NativeLayeredWorldPackageCatalog catalog,
		final String profileId,
		final String packageId,
		final String packageVersion,
		final String manifestSha256,
		final int terrainSectorCount,
		final int npcCount,
		final int groundItemCount,
		final int sceneryCount,
		final int boundaryCount,
		final boolean vanillaOnly) {
		if (catalog.size() != 1) {
			throw new IllegalStateException(
				"The " + profileId + " profile requires exactly "
					+ "one package");
		}
		final NativeLayeredWorldPackage loaded = catalog.getPrimaryPackage();
		if (!packageId.equals(loaded.getPackageId())
			|| !packageVersion.equals(
				loaded.getPackageVersion())
			|| !manifestSha256.equals(
				loaded.getManifestSha256())) {
			throw new IllegalStateException(
				"The " + profileId + " profile requires the exact "
					+ "reviewed package identity, version, and "
					+ "manifest");
		}
		if (loaded.getWorldSpaceCount() != 1
			|| loaded.getLevelCount() != 4
			|| loaded.getTerrainSectorCount() != terrainSectorCount
			|| loaded.getPlacementSetCount() != 4
			|| loaded.getNpcPlacementCount() != npcCount
			|| loaded.getGroundItemPlacementCount() != groundItemCount
			|| loaded.getSceneryPlacementCount() != sceneryCount
			|| loaded.getBoundaryPlacementCount() != boundaryCount) {
			throw new IllegalStateException(
				"The " + profileId + " profile package counts do "
					+ "not match the accepted complete-world review");
		}
		if (vanillaOnly) {
			validatePreservationDefinitionIds(loaded);
		}
		final Set<Integer> expectedLevels = new HashSet<Integer>(
			Arrays.asList(
				Integer.valueOf(-1),
				Integer.valueOf(0),
				Integer.valueOf(1),
				Integer.valueOf(2)));
		for (Integer level : expectedLevels) {
			if (!loaded.declaresLevel(
					WorldSpaceId.GLOBAL, level.intValue())) {
				throw new IllegalStateException(
					"The " + profileId + " profile is missing "
						+ "global level " + level);
			}
		}
		final Set<Integer> placementLevels = new HashSet<Integer>();
		for (NativeLayeredPlacementSet set
			: loaded.getPlacementSets().values()) {
			if (!WorldSpaceId.GLOBAL.equals(set.getWorldSpace())
				|| !"layered-world-placements-v3".equals(
					set.getSourceEncoding())
				|| !placementLevels.add(Integer.valueOf(set.getLevel()))) {
				throw new IllegalStateException(
					"The " + profileId + " profile requires one "
						+ "global v3 placement set per accepted level");
			}
		}
		if (!expectedLevels.equals(placementLevels)) {
			throw new IllegalStateException(
				"The " + profileId + " placement levels do not "
					+ "match the accepted complete-world review");
		}
	}

	private static void validatePreservationDefinitionIds(
		final NativeLayeredWorldPackage loaded) {
		for (NativeLayeredPlacementSet set
			: loaded.getPlacementSets().values()) {
			for (NativeLayeredNpcPlacement npc : set.getNpcs()) {
				requireVanillaDefinition(
					"NPC",
					npc.getPlacementId(),
					npc.getNpcId(),
					VANILLA_MAX_NPC_ID);
			}
			for (NativeLayeredGroundItemPlacement item
				: set.getGroundItems()) {
				requireVanillaDefinition(
					"ground item",
					item.getPlacementId(),
					item.getItemId(),
					VANILLA_MAX_ITEM_ID);
			}
			for (NativeLayeredSceneryPlacement scenery
				: set.getScenery()) {
				requireVanillaDefinition(
					"scenery",
					scenery.getPlacementId(),
					scenery.getSceneryId(),
					VANILLA_MAX_SCENERY_ID);
			}
			for (NativeLayeredBoundaryPlacement boundary
				: set.getBoundaries()) {
				requireVanillaDefinition(
					"boundary",
					boundary.getPlacementId(),
					boundary.getBoundaryId(),
					VANILLA_MAX_BOUNDARY_ID);
			}
		}
	}

	private static void requireVanillaDefinition(
		final String family,
		final String placementId,
		final int definitionId,
		final int maximumDefinitionId) {
		if (definitionId > maximumDefinitionId) {
			throw new IllegalStateException(
				"The preservation-r64-replacement profile refuses non-vanilla "
					+ family + " definition " + definitionId + " at "
					+ placementId);
		}
	}

	private static void requireNativeLocation(
		final NativeLayeredWorldPackage loaded,
		final WorldLocation location) {
		LayeredCompatibilityPointAdapter.toCompatibilityPoint(
			location, false, true);
		if (!loaded.findTile(location).isPresent()) {
			throw new IllegalStateException(
				"Native layered placement has no package terrain at "
					+ location);
		}
	}
}
