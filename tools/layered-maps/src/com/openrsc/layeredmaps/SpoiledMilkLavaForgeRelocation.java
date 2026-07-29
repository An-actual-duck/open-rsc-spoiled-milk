package com.openrsc.layeredmaps;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fail-closed relocation of the isolated Spoiled Milk lava-forge dungeon.
 *
 * <p>The reviewed overlay-8 cut separates the demon/lava-forge component from
 * the nearby Taverley blue-dragon dungeon. Native package generation moves
 * only the exact lava-forge component and its one-tile presentation ring to
 * level {@value #TARGET_LEVEL}. The neighboring dragon component and all of
 * its placements are counted and verified as an explicit negative guard.</p>
 */
final class SpoiledMilkLavaForgeRelocation {
	static final int SOURCE_LEVEL = -1;
	static final int TARGET_LEVEL = -2;
	static final int SEED_X = 329;
	static final int SEED_Y = 587;
	static final int EXPECTED_COMPONENT_TILES = 2170;
	static final int EXPECTED_COPIED_TILES = 2374;
	static final int EXPECTED_MIN_X = 288;
	static final int EXPECTED_MIN_Y = 576;
	static final int EXPECTED_MAX_X = 335;
	static final int EXPECTED_MAX_Y = 623;
	static final int EXPECTED_TARGET_SECTORS = 7;
	static final int EXPECTED_STRUCTURAL_RING_TILES = 0;

	static final int DRAGON_GUARD_SEED_X = 341;
	static final int DRAGON_GUARD_SEED_Y = 587;
	static final int EXPECTED_DRAGON_COMPONENT_TILES = 2955;
	static final int EXPECTED_DRAGON_MIN_X = 325;
	static final int EXPECTED_DRAGON_MIN_Y = 480;
	static final int EXPECTED_DRAGON_MAX_X = 423;
	static final int EXPECTED_DRAGON_MAX_Y = 604;
	static final int EXPECTED_DRAGON_SEPARATION = 6;

	private static final int VOID_OVERLAY = 8;
	private static final byte[] CANONICAL_VOID_TILE = {
		0, 1, VOID_OVERLAY, 0, 0, 0, 0, 0, 0, 0
	};
	private static final Map<String, Integer> EXPECTED_PLACEMENTS =
		expectedPlacements();
	private static final Map<String, Integer> EXPECTED_DRAGON_PLACEMENTS =
		expectedDragonPlacements();

	private SpoiledMilkLavaForgeRelocation() {
	}

	static Plan apply(Map<WorldMapSectorId, byte[]> input)
		throws PreflightException {
		Map<WorldMapSectorId, byte[]> terrain =
			new LinkedHashMap<WorldMapSectorId, byte[]>();
		for (Map.Entry<WorldMapSectorId, byte[]> entry : input.entrySet()) {
			if (entry.getValue() == null
				|| entry.getValue().length != RawLayeredTerrainSector.BYTE_COUNT) {
				throw new PreflightException(
					"Lava-forge relocation received an invalid native terrain sector.");
			}
			terrain.put(
				entry.getKey(),
				Arrays.copyOf(entry.getValue(), entry.getValue().length));
		}

		WorldCoordinate seed =
			new WorldCoordinate(SEED_X, SEED_Y, SOURCE_LEVEL);
		Set<WorldCoordinate> component =
			connectedNonVoidComponent(terrain, seed, "lava-forge");
		Bounds bounds = Bounds.of(component, "Lava-forge");
		if (component.size() != EXPECTED_COMPONENT_TILES
			|| bounds.minimumX != EXPECTED_MIN_X
			|| bounds.minimumY != EXPECTED_MIN_Y
			|| bounds.maximumX != EXPECTED_MAX_X
			|| bounds.maximumY != EXPECTED_MAX_Y) {
			throw new PreflightException(
				"Lava-forge connected terrain differs from the reviewed "
					+ "component: tiles=" + component.size()
					+ " bounds=" + bounds + ".");
		}

		WorldCoordinate dragonSeed = new WorldCoordinate(
			DRAGON_GUARD_SEED_X,
			DRAGON_GUARD_SEED_Y,
			SOURCE_LEVEL);
		Set<WorldCoordinate> dragonComponent =
			connectedNonVoidComponent(
				terrain, dragonSeed, "Taverley blue-dragon");
		Bounds dragonBounds =
			Bounds.of(dragonComponent, "Taverley blue-dragon");
		if (dragonComponent.size() != EXPECTED_DRAGON_COMPONENT_TILES
			|| dragonBounds.minimumX != EXPECTED_DRAGON_MIN_X
			|| dragonBounds.minimumY != EXPECTED_DRAGON_MIN_Y
			|| dragonBounds.maximumX != EXPECTED_DRAGON_MAX_X
			|| dragonBounds.maximumY != EXPECTED_DRAGON_MAX_Y) {
			throw new PreflightException(
				"Protected Taverley blue-dragon terrain differs from the "
					+ "reviewed component: tiles=" + dragonComponent.size()
					+ " bounds=" + dragonBounds + ".");
		}
		int dragonSeparation =
			closestChebyshevDistance(component, dragonComponent);
		if (dragonSeparation != EXPECTED_DRAGON_SEPARATION) {
			throw new PreflightException(
				"Lava-forge and protected blue-dragon terrain separation "
					+ "differs from the reviewed "
					+ EXPECTED_DRAGON_SEPARATION + " tiles: "
					+ dragonSeparation + ".");
		}
		Map<WorldCoordinate, byte[]> dragonTerrain =
			copyTiles(terrain, dragonComponent);

		Set<WorldCoordinate> copied =
			new LinkedHashSet<WorldCoordinate>();
		for (WorldCoordinate coordinate : component) {
			for (int deltaX = -1; deltaX <= 1; deltaX++) {
				for (int deltaY = -1; deltaY <= 1; deltaY++) {
					WorldCoordinate candidate = new WorldCoordinate(
						Math.addExact(coordinate.getX(), deltaX),
						Math.addExact(coordinate.getY(), deltaY),
						SOURCE_LEVEL);
					byte[] value = tile(terrain, candidate);
					if (value != null) {
						copied.add(candidate);
						if (!isVoid(value) && !component.contains(candidate)) {
							throw new PreflightException(
								"Lava-forge presentation ring touches another "
									+ "non-void terrain component at "
									+ candidate + ".");
						}
					}
				}
			}
		}
		if (copied.size() != EXPECTED_COPIED_TILES) {
			throw new PreflightException(
				"Lava-forge presentation ring differs from the reviewed "
					+ "footprint: " + copied.size() + " tiles.");
		}
		int structuralRingTiles = 0;
		for (WorldCoordinate coordinate : copied) {
			if (!component.contains(coordinate)
				&& hasStructure(tile(terrain, coordinate))) {
				structuralRingTiles++;
			}
		}
		if (structuralRingTiles != EXPECTED_STRUCTURAL_RING_TILES) {
			throw new PreflightException(
				"Lava-forge source presentation-ring structure differs from "
					+ "the reviewed footprint: "
					+ structuralRingTiles + " tiles.");
		}

		Map<WorldCoordinate, byte[]> copiedTiles =
			copyTiles(terrain, copied);
		Set<WorldMapSectorId> targets =
			new LinkedHashSet<WorldMapSectorId>();
		for (WorldCoordinate source : copied) {
			targets.add(sector(source.atLevel(TARGET_LEVEL)));
		}
		if (targets.size() != EXPECTED_TARGET_SECTORS) {
			throw new PreflightException(
				"Lava-forge relocation target differs from the reviewed "
					+ EXPECTED_TARGET_SECTORS + "-sector footprint.");
		}
		for (WorldMapSectorId target : targets) {
			if (terrain.containsKey(target)) {
				throw new PreflightException(
					"Lava-forge relocation target sector is already allocated: "
						+ target + ".");
			}
			terrain.put(target, canonicalVoidSector());
		}
		for (Map.Entry<WorldCoordinate, byte[]> entry
			: copiedTiles.entrySet()) {
			writeTile(
				terrain,
				entry.getKey().atLevel(TARGET_LEVEL),
				entry.getValue());
		}
		for (WorldCoordinate source : copied) {
			writeTile(terrain, source, CANONICAL_VOID_TILE);
		}
		verifyTilesUnchanged(
			terrain,
			dragonTerrain,
			"Protected Taverley blue-dragon terrain");

		List<WorldMapSectorId> orderedTargets =
			new ArrayList<WorldMapSectorId>(targets);
		Collections.sort(
			orderedTargets,
			new Comparator<WorldMapSectorId>() {
				@Override
				public int compare(
					WorldMapSectorId left,
					WorldMapSectorId right) {
					int x = Integer.compare(
						left.getSectorX(), right.getSectorX());
					return x != 0
						? x
						: Integer.compare(
							left.getSectorY(), right.getSectorY());
				}
			});
		return new Plan(
			terrain,
			component,
			copied,
			bounds,
			orderedTargets,
			structuralRingTiles,
			dragonComponent,
			dragonBounds,
			dragonSeparation);
	}

	private static Set<WorldCoordinate> connectedNonVoidComponent(
		Map<WorldMapSectorId, byte[]> terrain,
		WorldCoordinate seed,
		String label) throws PreflightException {
		if (isVoid(tile(terrain, seed))) {
			throw new PreflightException(
				"Reviewed " + label + " relocation seed is absent or void: "
					+ seed);
		}
		Set<WorldCoordinate> component =
			new LinkedHashSet<WorldCoordinate>();
		Deque<WorldCoordinate> pending = new ArrayDeque<WorldCoordinate>();
		component.add(seed);
		pending.add(seed);
		while (!pending.isEmpty()) {
			WorldCoordinate current = pending.removeFirst();
			for (int[] direction : new int[][] {
				{-1, 0}, {1, 0}, {0, -1}, {0, 1}
			}) {
				WorldCoordinate neighbor = new WorldCoordinate(
					Math.addExact(current.getX(), direction[0]),
					Math.addExact(current.getY(), direction[1]),
					SOURCE_LEVEL);
				if (!component.contains(neighbor)
					&& !isVoid(tile(terrain, neighbor))) {
					component.add(neighbor);
					pending.addLast(neighbor);
				}
			}
		}
		return component;
	}

	private static int closestChebyshevDistance(
		Set<WorldCoordinate> left,
		Set<WorldCoordinate> right) {
		int closest = Integer.MAX_VALUE;
		for (WorldCoordinate first : left) {
			for (WorldCoordinate second : right) {
				int distance = Math.max(
					Math.abs(first.getX() - second.getX()),
					Math.abs(first.getY() - second.getY()));
				closest = Math.min(closest, distance);
			}
		}
		return closest;
	}

	private static Map<WorldCoordinate, byte[]> copyTiles(
		Map<WorldMapSectorId, byte[]> terrain,
		Set<WorldCoordinate> coordinates) throws PreflightException {
		Map<WorldCoordinate, byte[]> result =
			new LinkedHashMap<WorldCoordinate, byte[]>();
		for (WorldCoordinate coordinate : coordinates) {
			byte[] value = tile(terrain, coordinate);
			if (value == null) {
				throw new PreflightException(
					"Lava-forge relocation lost reviewed terrain at "
						+ coordinate + ".");
			}
			result.put(
				coordinate,
				Arrays.copyOf(value, value.length));
		}
		return result;
	}

	private static void verifyTilesUnchanged(
		Map<WorldMapSectorId, byte[]> terrain,
		Map<WorldCoordinate, byte[]> expected,
		String label) throws PreflightException {
		for (Map.Entry<WorldCoordinate, byte[]> entry
			: expected.entrySet()) {
			if (!Arrays.equals(
					entry.getValue(),
					tile(terrain, entry.getKey()))) {
				throw new PreflightException(
					label + " changed at " + entry.getKey() + ".");
			}
		}
	}

	private static byte[] canonicalVoidSector() {
		byte[] result =
			new byte[RawLayeredTerrainSector.BYTE_COUNT];
		for (int offset = 0; offset < result.length;
			offset += RawLayeredTerrainSector.TILE_BYTES) {
			System.arraycopy(
				CANONICAL_VOID_TILE,
				0,
				result,
				offset,
				RawLayeredTerrainSector.TILE_BYTES);
		}
		return result;
	}

	private static WorldMapSectorId sector(WorldCoordinate coordinate) {
		return new WorldMapSectorId(
			WorldSpaceId.GLOBAL,
			coordinate.getLevel(),
			coordinate.getSectorX(),
			coordinate.getSectorY());
	}

	private static byte[] tile(
		Map<WorldMapSectorId, byte[]> terrain,
		WorldCoordinate coordinate) {
		byte[] sector = terrain.get(sector(coordinate));
		if (sector == null) {
			return null;
		}
		int offset = tileOffset(coordinate);
		return Arrays.copyOfRange(
			sector,
			offset,
			offset + RawLayeredTerrainSector.TILE_BYTES);
	}

	private static void writeTile(
		Map<WorldMapSectorId, byte[]> terrain,
		WorldCoordinate coordinate,
		byte[] value) throws PreflightException {
		byte[] sector = terrain.get(sector(coordinate));
		if (sector == null) {
			throw new PreflightException(
				"Lava-forge relocation lost terrain allocation at "
					+ coordinate + ".");
		}
		System.arraycopy(
			value,
			0,
			sector,
			tileOffset(coordinate),
			RawLayeredTerrainSector.TILE_BYTES);
	}

	private static int tileOffset(WorldCoordinate coordinate) {
		return Math.multiplyExact(
			Math.addExact(
				Math.multiplyExact(
					coordinate.getLocalX(),
					RawLayeredTerrainSector.SIZE),
				coordinate.getLocalY()),
			RawLayeredTerrainSector.TILE_BYTES);
	}

	private static boolean isVoid(byte[] tile) {
		return tile == null
			|| (tile[2] & 0xff) == VOID_OVERLAY;
	}

	private static boolean hasStructure(byte[] tile) {
		if (tile == null) {
			return false;
		}
		for (int index = 4; index < tile.length; index++) {
			if (tile[index] != 0) {
				return true;
			}
		}
		return false;
	}

	private static Map<String, Integer> expectedPlacements() {
		Map<String, Integer> result =
			new LinkedHashMap<String, Integer>();
		result.put("npcs", Integer.valueOf(20));
		result.put("groundItems", Integer.valueOf(1));
		result.put("scenery", Integer.valueOf(3));
		result.put("boundaries", Integer.valueOf(0));
		return Collections.unmodifiableMap(result);
	}

	private static Map<String, Integer> expectedDragonPlacements() {
		Map<String, Integer> result =
			new LinkedHashMap<String, Integer>();
		result.put("npcs", Integer.valueOf(83));
		result.put("groundItems", Integer.valueOf(10));
		result.put("scenery", Integer.valueOf(217));
		result.put("boundaries", Integer.valueOf(11));
		return Collections.unmodifiableMap(result);
	}

	static final class Plan {
		private final Map<WorldMapSectorId, byte[]> terrain;
		private final Set<WorldCoordinate> component;
		private final Set<WorldCoordinate> copied;
		private final Bounds bounds;
		private final List<WorldMapSectorId> targetSectors;
		private final int structuralRingTiles;
		private final Set<WorldCoordinate> dragonComponent;
		private final Bounds dragonBounds;
		private final int dragonSeparation;
		private final Map<String, Integer> relocatedPlacements =
			new LinkedHashMap<String, Integer>();
		private final Map<String, Integer> protectedDragonPlacements =
			new LinkedHashMap<String, Integer>();

		private Plan(
			Map<WorldMapSectorId, byte[]> terrain,
			Set<WorldCoordinate> component,
			Set<WorldCoordinate> copied,
			Bounds bounds,
			List<WorldMapSectorId> targetSectors,
			int structuralRingTiles,
			Set<WorldCoordinate> dragonComponent,
			Bounds dragonBounds,
			int dragonSeparation) {
			this.terrain = terrain;
			this.component =
				Collections.unmodifiableSet(
					new LinkedHashSet<WorldCoordinate>(component));
			this.copied =
				Collections.unmodifiableSet(
					new LinkedHashSet<WorldCoordinate>(copied));
			this.bounds = bounds;
			this.targetSectors =
				Collections.unmodifiableList(
					new ArrayList<WorldMapSectorId>(targetSectors));
			this.structuralRingTiles = structuralRingTiles;
			this.dragonComponent =
				Collections.unmodifiableSet(
					new LinkedHashSet<WorldCoordinate>(dragonComponent));
			this.dragonBounds = dragonBounds;
			this.dragonSeparation = dragonSeparation;
			for (String family : EXPECTED_PLACEMENTS.keySet()) {
				relocatedPlacements.put(family, Integer.valueOf(0));
				protectedDragonPlacements.put(family, Integer.valueOf(0));
			}
		}

		Map<WorldMapSectorId, byte[]> getTerrain() {
			return terrain;
		}

		WorldCoordinate relocatePlacement(
			String family,
			WorldCoordinate source) throws PreflightException {
			if (source.getLevel() != SOURCE_LEVEL) {
				return source;
			}
			Integer relocatedCount = relocatedPlacements.get(family);
			Integer protectedCount = protectedDragonPlacements.get(family);
			if (relocatedCount == null || protectedCount == null) {
				throw new PreflightException(
					"Unknown lava-forge placement family: " + family);
			}
			if (dragonComponent.contains(source)) {
				protectedDragonPlacements.put(
					family,
					Integer.valueOf(protectedCount.intValue() + 1));
				return source;
			}
			if (!copied.contains(source)) {
				return source;
			}
			relocatedPlacements.put(
				family,
				Integer.valueOf(relocatedCount.intValue() + 1));
			return source.atLevel(TARGET_LEVEL);
		}

		void verifyPlacementCounts() throws PreflightException {
			if (!EXPECTED_PLACEMENTS.equals(relocatedPlacements)) {
				throw new PreflightException(
					"Lava-forge placement ownership differs from the reviewed "
						+ "component: expected " + EXPECTED_PLACEMENTS
						+ " but found " + relocatedPlacements + ".");
			}
			if (!EXPECTED_DRAGON_PLACEMENTS.equals(
					protectedDragonPlacements)) {
				throw new PreflightException(
					"Protected Taverley blue-dragon placement ownership "
						+ "differs from the reviewed component: expected "
						+ EXPECTED_DRAGON_PLACEMENTS + " but found "
						+ protectedDragonPlacements + ".");
			}
		}

		Map<String, Object> toDocument() {
			Map<String, Object> document =
				new LinkedHashMap<String, Object>();
			document.put(
				"id",
				"spoiled-milk-lava-forge-to-level-minus-2-v1");
			document.put("sourceLevel", Long.valueOf(SOURCE_LEVEL));
			document.put("targetLevel", Long.valueOf(TARGET_LEVEL));
			document.put(
				"componentSeed",
				coordinateDocument(SEED_X, SEED_Y));
			document.put(
				"connectedNonVoidTiles",
				Long.valueOf(component.size()));
			document.put(
				"copiedTilesIncludingPresentationRing",
				Long.valueOf(copied.size()));
			document.put("bounds", bounds.toDocument());
			List<Object> sectors = new ArrayList<Object>();
			for (WorldMapSectorId target : targetSectors) {
				Map<String, Object> value =
					new LinkedHashMap<String, Object>();
				value.put(
					"sectorX",
					Long.valueOf(target.getSectorX()));
				value.put(
					"sectorY",
					Long.valueOf(target.getSectorY()));
				sectors.add(value);
			}
			document.put("targetSectors", sectors);
			document.put(
				"relocatedPlacementsByFamily",
				countDocument(relocatedPlacements));
			document.put(
				"sourceCopiedFootprintClearedToVoid",
				Boolean.TRUE);
			document.put(
				"sourceClearedTiles",
				Long.valueOf(copied.size()));
			document.put(
				"sourceClearedVoidRingTiles",
				Long.valueOf(copied.size() - component.size()));
			document.put(
				"sourceClearedStructuralRingTiles",
				Long.valueOf(structuralRingTiles));
			document.put("xAndYPreserved", Boolean.TRUE);

			Map<String, Object> dragon =
				new LinkedHashMap<String, Object>();
			dragon.put("label", "Taverley blue-dragon dungeon");
			dragon.put(
				"componentSeed",
				coordinateDocument(
					DRAGON_GUARD_SEED_X,
					DRAGON_GUARD_SEED_Y));
			dragon.put(
				"connectedNonVoidTiles",
				Long.valueOf(dragonComponent.size()));
			dragon.put("bounds", dragonBounds.toDocument());
			dragon.put(
				"minimumChebyshevSeparationTiles",
				Long.valueOf(dragonSeparation));
			dragon.put("terrainRemainsOnSourceLevel", Boolean.TRUE);
			dragon.put("terrainByteExactAfterRelocation", Boolean.TRUE);
			dragon.put(
				"placementsRemainingOnSourceLevelByFamily",
				countDocument(protectedDragonPlacements));
			document.put("protectedNeighbor", dragon);
			return document;
		}

		private static Map<String, Object> coordinateDocument(
			int x,
			int y) {
			Map<String, Object> result =
				new LinkedHashMap<String, Object>();
			result.put("x", Long.valueOf(x));
			result.put("y", Long.valueOf(y));
			return result;
		}

		private static Map<String, Object> countDocument(
			Map<String, Integer> counts) {
			Map<String, Object> result =
				new LinkedHashMap<String, Object>();
			for (Map.Entry<String, Integer> entry : counts.entrySet()) {
				result.put(
					entry.getKey(),
					Long.valueOf(entry.getValue().intValue()));
			}
			return result;
		}
	}

	private static final class Bounds {
		final int minimumX;
		final int minimumY;
		final int maximumX;
		final int maximumY;

		private Bounds(
			int minimumX,
			int minimumY,
			int maximumX,
			int maximumY) {
			this.minimumX = minimumX;
			this.minimumY = minimumY;
			this.maximumX = maximumX;
			this.maximumY = maximumY;
		}

		static Bounds of(
			Set<WorldCoordinate> coordinates,
			String label) throws PreflightException {
			if (coordinates.isEmpty()) {
				throw new PreflightException(
					label + " terrain component is empty.");
			}
			int minimumX = Integer.MAX_VALUE;
			int minimumY = Integer.MAX_VALUE;
			int maximumX = Integer.MIN_VALUE;
			int maximumY = Integer.MIN_VALUE;
			for (WorldCoordinate coordinate : coordinates) {
				minimumX = Math.min(minimumX, coordinate.getX());
				minimumY = Math.min(minimumY, coordinate.getY());
				maximumX = Math.max(maximumX, coordinate.getX());
				maximumY = Math.max(maximumY, coordinate.getY());
			}
			return new Bounds(
				minimumX, minimumY, maximumX, maximumY);
		}

		Map<String, Object> toDocument() {
			Map<String, Object> document =
				new LinkedHashMap<String, Object>();
			document.put("minimumX", Long.valueOf(minimumX));
			document.put("minimumY", Long.valueOf(minimumY));
			document.put("maximumX", Long.valueOf(maximumX));
			document.put("maximumY", Long.valueOf(maximumY));
			return document;
		}

		@Override
		public String toString() {
			return minimumX + "," + minimumY + ".."
				+ maximumX + "," + maximumY;
		}
	}
}
