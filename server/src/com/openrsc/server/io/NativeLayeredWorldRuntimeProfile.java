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
	SPOILED_MILK_BUILDER_DRAFT("spoiled-milk-builder-draft", true),
	SPOILED_MILK_WORLD_BUILDER_EXPORT(
		"spoiled-milk-world-builder-export", true),
	ADAPTIVE_WORLD_BUILDER("adaptive-world-builder", true);

	public static final String DEFAULT_ID = "fixture-additive";
	public static final String PRESERVATION_PACKAGE_ID =
		"rsc-remastered.preservation-r64-parity-review";
	public static final String PRESERVATION_PACKAGE_VERSION = "0.4.0";
	public static final String PRESERVATION_MANIFEST_SHA256 =
		"560dae205d13c2034b38f52d8bb6841ee56c245fadc8e9d18361ace1346cd73f";
	public static final String SPOILED_MILK_PACKAGE_ID =
		"rsc-remastered.spoiled-milk-layered-world";
	public static final String SPOILED_MILK_PACKAGE_VERSION = "0.5.0";
	public static final String SPOILED_MILK_MANIFEST_SHA256 =
		"f914d93e7abcf40dc281c06df5010269c7a9ce4fe4a16aaa6ae11f0d90a14306";
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

	public boolean requiresConfiguredManifestSha256() {
		return this == SPOILED_MILK_WORLD_BUILDER_EXPORT
			|| this == ADAPTIVE_WORLD_BUILDER;
	}

	public boolean requiresConfiguredInventorySha256() {
		return this == ADAPTIVE_WORLD_BUILDER;
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
			case SPOILED_MILK_WORLD_BUILDER_EXPORT:
				validateSpoiledMilkBuilderDraft(catalog);
				return;
			case ADAPTIVE_WORLD_BUILDER:
				validateAdaptiveWorldBuilder(catalog);
				return;
			default:
				throw new IllegalStateException(
					"Unhandled native layered world runtime profile: " + this);
		}
	}

	private static void validateAdaptiveWorldBuilder(
		final NativeLayeredWorldPackageCatalog catalog) {
		if (catalog.size() != 1) {
			throw new IllegalStateException(
				"The adaptive-world-builder profile requires exactly one package");
		}
		final NativeLayeredWorldPackage loaded = catalog.getPrimaryPackage();
		if (loaded.getWorldSpaceCount() != 1
			|| !"static".equals(loaded.getWorldSpaceKinds().get("global"))) {
			throw new IllegalStateException(
				"The adaptive-world-builder profile requires one static global world space");
		}
		if (loaded.getLevelCount() < 1 || loaded.getLevelCount() > 64
			|| loaded.getTerrainSectorCount() < 1
			|| loaded.getTerrainSectorCount() > 8192
			|| loaded.getPlacementSetCount() != loaded.getLevelCount()) {
			throw new IllegalStateException(
				"The adaptive-world-builder package exceeds its bounded level, "
					+ "terrain, or placement-set contract");
		}
		long placementCount = (long) loaded.getNpcPlacementCount()
			+ loaded.getGroundItemPlacementCount()
			+ loaded.getSceneryPlacementCount()
			+ loaded.getBoundaryPlacementCount();
		if (placementCount > 100000L) {
			throw new IllegalStateException(
				"The adaptive-world-builder package exceeds 100000 placements");
		}
		Set<Integer> declaredLevels = new HashSet<Integer>();
		for (NativeLayeredWorldPackage.LevelDeclaration level
			: loaded.getLevelDeclarations()) {
			if (!WorldSpaceId.GLOBAL.equals(level.getWorldSpace())
				|| !declaredLevels.add(Integer.valueOf(level.getLevel()))) {
				throw new IllegalStateException(
					"The adaptive-world-builder package has ambiguous level ownership");
			}
			boolean hasTerrain = false;
			for (com.openrsc.server.model.world.coordinate.WorldMapSectorId sector
				: loaded.getTerrainSectors().keySet()) {
				if (WorldSpaceId.GLOBAL.equals(sector.getWorldSpace())
					&& sector.getLevel() == level.getLevel()) {
					hasTerrain = true;
					break;
				}
			}
			if (!hasTerrain) {
				throw new IllegalStateException(
					"The adaptive-world-builder package has a level without terrain: "
						+ level.getLevel());
			}
		}
		Set<Integer> placementLevels = new HashSet<Integer>();
		for (NativeLayeredPlacementSet set : loaded.getPlacementSets().values()) {
			if (!WorldSpaceId.GLOBAL.equals(set.getWorldSpace())
				|| !NativeLayeredWorldPackage.WORLD_PLACEMENT_ENCODING_V3.equals(
					set.getSourceEncoding())
				|| !placementLevels.add(Integer.valueOf(set.getLevel()))) {
				throw new IllegalStateException(
					"The adaptive-world-builder profile requires one global v3 "
						+ "placement set per level");
			}
		}
		if (!declaredLevels.equals(placementLevels)) {
			throw new IllegalStateException(
				"The adaptive-world-builder placement levels are incomplete");
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
				1782,
				3775,
				879,
				27887,
			971,
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
			|| loaded.getLevelCount() < 6
			|| loaded.getTerrainSectorCount() < 1782
			|| loaded.getPlacementSetCount() != loaded.getLevelCount()
			|| loaded.getNpcPlacementCount() < 3775
			|| loaded.getGroundItemPlacementCount() < 879
			|| loaded.getSceneryPlacementCount() < 27887
			|| loaded.getBoundaryPlacementCount() != 971) {
			throw new IllegalStateException(
				"The spoiled-milk-builder-draft profile requires an additive "
				+ "terrain/NPC/scenery/ground-item descendant of the accepted "
				+ "Spoiled Milk package");
		}
		for (int level : new int[] {-2, -1, 0, 1, 2, 10}) {
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
			if (set.getLevel() == -1) {
				requireBuilderSourcePlacementCounts(set, 1140, 257, 4177, 159);
			} else if (set.getLevel() == -2) {
				requireBuilderSourcePlacementCounts(set, 20, 1, 3, 0);
			} else if (set.getLevel() == 0) {
				requireBuilderSourcePlacementCounts(set, 2386, 534, 22235, 676);
			} else if (set.getLevel() == 1) {
				requireBuilderSourcePlacementCounts(set, 164, 61, 1079, 94);
			} else if (set.getLevel() == 2) {
				requireBuilderSourcePlacementCounts(set, 37, 22, 199, 36);
			} else if (set.getLevel() == 10) {
				requireBuilderSourcePlacementCounts(set, 28, 4, 194, 6);
			} else if (!set.getBoundaries().isEmpty()) {
				throw new IllegalStateException(
					"Builder-created levels may not contain authored boundaries");
			}
		}
		if (placementLevels.size() != loaded.getLevelCount()) {
			throw new IllegalStateException(
				"The Spoiled Milk Builder draft placement levels are incomplete");
		}
	}

	private static void requireBuilderSourcePlacementCounts(
		NativeLayeredPlacementSet set,
		int npcCount,
		int groundItemCount,
		int sceneryCount,
		int boundaryCount) {
		if (set.getNpcs().size() != npcCount
			|| set.getGroundItems().size() != groundItemCount
			|| set.getScenery().size() != sceneryCount
			|| set.getBoundaries().size() != boundaryCount) {
			throw new IllegalStateException(
				"The Spoiled Milk Builder draft changed accepted placements "
					+ "on global level " + set.getLevel());
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
		final Set<Integer> expectedLevels = new HashSet<Integer>(
			vanillaOnly
				? Arrays.asList(
					Integer.valueOf(-1),
					Integer.valueOf(0),
					Integer.valueOf(1),
					Integer.valueOf(2))
				: Arrays.asList(
					Integer.valueOf(-2),
					Integer.valueOf(-1),
					Integer.valueOf(0),
					Integer.valueOf(1),
					Integer.valueOf(2),
					Integer.valueOf(10)));
		if (loaded.getWorldSpaceCount() != 1
			|| loaded.getLevelCount() != expectedLevels.size()
			|| loaded.getTerrainSectorCount() != terrainSectorCount
			|| loaded.getPlacementSetCount() != expectedLevels.size()
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
