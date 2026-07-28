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
 * Fail-closed relocation of the isolated Spoiled Milk Zanaris terrain island.
 *
 * <p>The source archive remains a reviewed legacy input. Native package
 * generation discovers the exact non-void component from its known entrance,
 * verifies the accepted topology, and moves that component plus one exact
 * void-tile presentation ring to level {@value #TARGET_LEVEL}. Placement
 * anchors in the same footprint are reassigned by the package generator.</p>
 */
final class SpoiledMilkZanarisRelocation {
	static final int SOURCE_LEVEL = -1;
	static final int TARGET_LEVEL = 10;
	static final int SEED_X = 126;
	static final int SEED_Y = 686;
	static final int EXPECTED_COMPONENT_TILES = 1639;
	static final int EXPECTED_COPIED_TILES = 2206;
	static final int EXPECTED_MIN_X = 97;
	static final int EXPECTED_MIN_Y = 679;
	static final int EXPECTED_MAX_X = 180;
	static final int EXPECTED_MAX_Y = 727;
	static final int EXPECTED_TARGET_SECTORS = 4;
	static final int EXPECTED_STRUCTURAL_RING_TILES = 214;

	private static final int VOID_OVERLAY = 8;
	private static final byte[] CANONICAL_VOID_TILE = {
		0, 1, VOID_OVERLAY, 0, 0, 0, 0, 0, 0, 0
	};
	private static final Map<String, Integer> EXPECTED_PLACEMENTS =
		expectedPlacements();

	private SpoiledMilkZanarisRelocation() {
	}

	static Plan apply(Map<WorldMapSectorId, byte[]> input)
		throws PreflightException {
		Map<WorldMapSectorId, byte[]> terrain =
			new LinkedHashMap<WorldMapSectorId, byte[]>();
		for (Map.Entry<WorldMapSectorId, byte[]> entry : input.entrySet()) {
			if (entry.getValue() == null
				|| entry.getValue().length != RawLayeredTerrainSector.BYTE_COUNT) {
				throw new PreflightException(
					"Zanaris relocation received an invalid native terrain sector.");
			}
			terrain.put(
				entry.getKey(),
				Arrays.copyOf(entry.getValue(), entry.getValue().length));
		}

		WorldCoordinate seed =
			new WorldCoordinate(SEED_X, SEED_Y, SOURCE_LEVEL);
		if (isVoid(tile(terrain, seed))) {
			throw new PreflightException(
				"Zanaris relocation seed is absent or void: " + seed);
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

		Bounds bounds = Bounds.of(component);
		if (component.size() != EXPECTED_COMPONENT_TILES
			|| bounds.minimumX != EXPECTED_MIN_X
			|| bounds.minimumY != EXPECTED_MIN_Y
			|| bounds.maximumX != EXPECTED_MAX_X
			|| bounds.maximumY != EXPECTED_MAX_Y) {
			throw new PreflightException(
				"Zanaris connected terrain differs from the reviewed component: "
					+ "tiles=" + component.size() + " bounds=" + bounds + ".");
		}

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
								"Zanaris presentation ring touches another non-void "
									+ "terrain component at " + candidate + ".");
						}
					}
				}
			}
		}
		if (copied.size() != EXPECTED_COPIED_TILES) {
			throw new PreflightException(
				"Zanaris presentation ring differs from the reviewed footprint: "
					+ copied.size() + " tiles.");
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
				"Zanaris source presentation-ring structure differs from the "
					+ "reviewed footprint: " + structuralRingTiles + " tiles.");
		}

		Map<WorldCoordinate, byte[]> copiedTiles =
			new LinkedHashMap<WorldCoordinate, byte[]>();
		Set<WorldMapSectorId> targets =
			new LinkedHashSet<WorldMapSectorId>();
		for (WorldCoordinate source : copied) {
			byte[] value = tile(terrain, source);
			copiedTiles.put(
				source,
				Arrays.copyOf(value, RawLayeredTerrainSector.TILE_BYTES));
			WorldCoordinate destination = source.atLevel(TARGET_LEVEL);
			targets.add(sector(destination));
		}
		if (targets.size() != EXPECTED_TARGET_SECTORS) {
			throw new PreflightException(
				"Zanaris relocation target differs from the reviewed four-sector "
					+ "footprint.");
		}
		for (WorldMapSectorId target : targets) {
			if (terrain.containsKey(target)) {
				throw new PreflightException(
					"Zanaris relocation target sector is already allocated: "
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
			structuralRingTiles);
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
				"Zanaris relocation lost terrain allocation at " + coordinate + ".");
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
		result.put("npcs", Integer.valueOf(28));
		result.put("groundItems", Integer.valueOf(4));
		result.put("scenery", Integer.valueOf(194));
		result.put("boundaries", Integer.valueOf(6));
		return Collections.unmodifiableMap(result);
	}

	static final class Plan {
		private final Map<WorldMapSectorId, byte[]> terrain;
		private final Set<WorldCoordinate> component;
		private final Set<WorldCoordinate> copied;
		private final Bounds bounds;
		private final List<WorldMapSectorId> targetSectors;
		private final int structuralRingTiles;
		private final Map<String, Integer> relocatedPlacements =
			new LinkedHashMap<String, Integer>();

		private Plan(
			Map<WorldMapSectorId, byte[]> terrain,
			Set<WorldCoordinate> component,
			Set<WorldCoordinate> copied,
			Bounds bounds,
			List<WorldMapSectorId> targetSectors,
			int structuralRingTiles) {
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
			for (String family : EXPECTED_PLACEMENTS.keySet()) {
				relocatedPlacements.put(family, Integer.valueOf(0));
			}
		}

		Map<WorldMapSectorId, byte[]> getTerrain() {
			return terrain;
		}

		WorldCoordinate relocatePlacement(
			String family,
			WorldCoordinate source) throws PreflightException {
			if (source.getLevel() != SOURCE_LEVEL
				|| !copied.contains(source)) {
				return source;
			}
			Integer count = relocatedPlacements.get(family);
			if (count == null) {
				throw new PreflightException(
					"Unknown Zanaris placement family: " + family);
			}
			relocatedPlacements.put(
				family,
				Integer.valueOf(count.intValue() + 1));
			return source.atLevel(TARGET_LEVEL);
		}

		void verifyPlacementCounts() throws PreflightException {
			if (!EXPECTED_PLACEMENTS.equals(relocatedPlacements)) {
				throw new PreflightException(
					"Zanaris placement ownership differs from the reviewed "
						+ "component: expected " + EXPECTED_PLACEMENTS
						+ " but found " + relocatedPlacements + ".");
			}
		}

		Map<String, Object> toDocument() {
			Map<String, Object> document =
				new LinkedHashMap<String, Object>();
			document.put("id", "spoiled-milk-zanaris-to-level-10-v1");
			document.put("sourceLevel", Long.valueOf(SOURCE_LEVEL));
			document.put("targetLevel", Long.valueOf(TARGET_LEVEL));
			Map<String, Object> seed = new LinkedHashMap<String, Object>();
			seed.put("x", Long.valueOf(SEED_X));
			seed.put("y", Long.valueOf(SEED_Y));
			document.put("componentSeed", seed);
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
			Map<String, Object> placementCounts =
				new LinkedHashMap<String, Object>();
			for (Map.Entry<String, Integer> entry
				: relocatedPlacements.entrySet()) {
				placementCounts.put(
					entry.getKey(),
					Long.valueOf(entry.getValue().intValue()));
			}
			document.put(
				"relocatedPlacementsByFamily",
				placementCounts);
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
			return document;
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

		static Bounds of(Set<WorldCoordinate> coordinates)
			throws PreflightException {
			if (coordinates.isEmpty()) {
				throw new PreflightException(
					"Zanaris terrain component is empty.");
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
