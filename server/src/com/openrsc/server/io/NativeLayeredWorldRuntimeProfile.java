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
 * <p>The fixture profile supplements the ordinary legacy population. The
 * Preservation profile is a pinned complete-world replacement and must never
 * be selected by package shape alone.</p>
 */
public enum NativeLayeredWorldRuntimeProfile {
	FIXTURE_ADDITIVE("fixture-additive", false),
	PRESERVATION_R64_REPLACEMENT("preservation-r64-replacement", true);

	public static final String DEFAULT_ID = "fixture-additive";
	public static final String PRESERVATION_PACKAGE_ID =
		"rsc-remastered.preservation-r64-parity-review";
	public static final String PRESERVATION_PACKAGE_VERSION = "0.3.0";
	public static final String PRESERVATION_MANIFEST_SHA256 =
		"ccb3e4514de96d7c5f60b1c2cee8e9b4ea83fec5c82860c2107f84c69869cc7e";

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
		if (catalog.size() != 1) {
			throw new IllegalStateException(
				"The preservation-r64-replacement profile requires exactly "
					+ "one package");
		}
		final NativeLayeredWorldPackage loaded = catalog.getPrimaryPackage();
		if (!PRESERVATION_PACKAGE_ID.equals(loaded.getPackageId())
			|| !PRESERVATION_PACKAGE_VERSION.equals(
				loaded.getPackageVersion())
			|| !PRESERVATION_MANIFEST_SHA256.equals(
				loaded.getManifestSha256())) {
			throw new IllegalStateException(
				"The preservation-r64-replacement profile requires the exact "
					+ "reviewed Preservation package identity, version, and "
					+ "manifest");
		}
		if (loaded.getWorldSpaceCount() != 1
			|| loaded.getLevelCount() != 4
			|| loaded.getTerrainSectorCount() != 1764
			|| loaded.getPlacementSetCount() != 4
			|| loaded.getNpcPlacementCount() != 3612
			|| loaded.getGroundItemPlacementCount() != 1016
			|| loaded.getSceneryPlacementCount() != 26770
			|| loaded.getBoundaryPlacementCount() != 966) {
			throw new IllegalStateException(
				"The preservation-r64-replacement profile package counts do "
					+ "not match the accepted complete-world review");
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
					"The preservation-r64-replacement profile is missing "
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
					"The preservation-r64-replacement profile requires one "
						+ "global v3 placement set per accepted level");
			}
		}
		if (!expectedLevels.equals(placementLevels)) {
			throw new IllegalStateException(
				"The preservation-r64-replacement placement levels do not "
					+ "match the accepted complete-world review");
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
