package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.LayeredCompatibilityPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static com.openrsc.server.io.NativeLayeredWorldRuntimeProfile.*;

/** Explicit historical distribution/fixture assertions, never generic capability. */
final class NativeLayeredWorldDistributionPolicy {
	private static final int VANILLA_MAX_BOUNDARY_ID = 213;
	private static final int VANILLA_MAX_SCENERY_ID = 1189;
	private static final int VANILLA_MAX_NPC_ID = 793;
	private static final int VANILLA_MAX_ITEM_ID = 1289;

	private NativeLayeredWorldDistributionPolicy() {}
	static void validate(NativeLayeredWorldRuntimeProfile profile, NativeLayeredWorldPackageCatalog catalog) {
		switch (profile) {
			case FIXTURE_ADDITIVE: validateFixture(catalog.getPrimaryPackage()); return;
			case PRESERVATION_R64_REPLACEMENT: validatePreservation(catalog); return;
			case SPOILED_MILK_REPLACEMENT: validateSpoiledMilk(catalog); return;
			case SPOILED_MILK_BUILDER_DRAFT:
			case SPOILED_MILK_WORLD_BUILDER_EXPORT: validateSpoiledMilkBuilderDraft(catalog); return;
			case SPOILED_MILK_INSTALLED_REVIEW: validateSpoiledMilkEditorInstalled(catalog); return;
			default: throw new IllegalArgumentException("Not an explicit distribution profile: " + profile);
		}
	}
	private static void validateSpoiledMilkEditorInstalled(
		final NativeLayeredWorldPackageCatalog catalog) {
		if (catalog.size() != 1) {
			throw new IllegalStateException(
				"The spoiled-milk-editor-installed profile requires exactly one package");
		}
		final NativeLayeredWorldPackage loaded = catalog.getPrimaryPackage();
		if (!SPOILED_MILK_PACKAGE_ID.equals(loaded.getPackageId())
			|| !SPOILED_MILK_PACKAGE_VERSION.equals(loaded.getPackageVersion())
			|| loaded.getWorldSpaceCount() != 1
			|| loaded.getLevelCount() < 6
			|| loaded.getTerrainSectorCount() < 1
			|| loaded.getPlacementSetCount() != loaded.getLevelCount()) {
			throw new IllegalStateException(
				"The spoiled-milk-editor-installed profile requires a complete "
					+ "content-addressed Spoiled Milk layered package");
		}
		for (int level : new int[] {-2, -1, 0, 1, 2, 10}) {
			if (!loaded.declaresLevel(WorldSpaceId.GLOBAL, level)) {
				throw new IllegalStateException(
					"The installed Spoiled Milk package is missing global level " + level);
			}
		}
		final Set<Integer> placementLevels = new HashSet<Integer>();
		for (NativeLayeredPlacementSet set : loaded.getPlacementSets().values()) {
			String encoding = set.getSourceEncoding();
			if (!WorldSpaceId.GLOBAL.equals(set.getWorldSpace())
				|| !(NativeLayeredWorldPackage.WORLD_PLACEMENT_ENCODING_V3.equals(encoding)
					|| NativeLayeredWorldPackage.WORLD_PLACEMENT_ENCODING_V4.equals(encoding)
					|| NativeLayeredWorldPackage.WORLD_PLACEMENT_ENCODING_V5.equals(encoding))
				|| !placementLevels.add(Integer.valueOf(set.getLevel()))) {
				throw new IllegalStateException(
					"The installed Spoiled Milk package requires one global v3/v4/v5 "
						+ "placement set per declared level");
			}
		}
		if (placementLevels.size() != loaded.getLevelCount()) {
			throw new IllegalStateException(
				"The installed Spoiled Milk package placement levels are incomplete");
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
				3789,
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
			|| loaded.getNpcPlacementCount() < 3789
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
				requireBuilderSourcePlacementCounts(set, 2400, 534, 22235, 676);
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
