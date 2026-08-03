package com.openrsc.server.content.worldedit;

import com.openrsc.server.external.EntityHandler;
import com.openrsc.server.io.NativeLayeredBoundaryPlacement;
import com.openrsc.server.io.NativeLayeredGroundItemPlacement;
import com.openrsc.server.io.NativeLayeredNpcPlacement;
import com.openrsc.server.io.NativeLayeredPlacementSet;
import com.openrsc.server.io.NativeLayeredSceneryPlacement;
import com.openrsc.server.io.NativeLayeredTerrainSector;
import com.openrsc.server.io.NativeLayeredTerrainTile;
import com.openrsc.server.io.NativeLayeredWorldPackage;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** Validates every definition reference before an adaptive session can bind. */
public final class AdaptiveWorldBuilderDefinitionInventory {
	private static final int MAX_ROOF_TEXTURE = 6;

	private AdaptiveWorldBuilderDefinitionInventory() {
	}

	public static Result validate(
		EntityHandler definitions, NativeLayeredWorldPackage worldPackage) {
		if (definitions == null || worldPackage == null) {
			throw new IllegalArgumentException(
				"Adaptive definition validation requires loaded definitions and package");
		}
		Set<Integer> tileIds = new TreeSet<Integer>();
		Set<Integer> boundaryIds = new TreeSet<Integer>();
		Set<Integer> sceneryIds = new TreeSet<Integer>();
		Set<Integer> npcIds = new TreeSet<Integer>();
		Set<Integer> itemIds = new TreeSet<Integer>();
		for (NativeLayeredTerrainSector sector
			: worldPackage.getTerrainSectors().values()) {
			for (int x = 0; x < NativeLayeredTerrainSector.SIZE; x++) {
				for (int y = 0; y < NativeLayeredTerrainSector.SIZE; y++) {
					NativeLayeredTerrainTile tile = sector.getTile(x, y);
					if (tile.getRoof() > MAX_ROOF_TEXTURE) {
						throw new IllegalStateException(
							"Adaptive terrain references undefined roof texture "
								+ tile.getRoof() + " at " + sector.getIdentity());
					}
					int overlay = tile.getOverlay() == 250 ? 2 : tile.getOverlay();
					if (overlay != 0) tileIds.add(Integer.valueOf(overlay - 1));
					collectWall(tile.getVerticalWall(), boundaryIds, sector);
					collectWall(tile.getHorizontalWall(), boundaryIds, sector);
					int diagonal = tile.getDiagonalWall();
					if (diagonal != 0) {
						if (diagonal < 1 || diagonal >= 24000 || diagonal == 12000) {
							throw new IllegalStateException(
								"Adaptive terrain has unsupported diagonal wall encoding "
									+ diagonal + " at " + sector.getIdentity());
						}
						collectWall(
							diagonal > 12000 ? diagonal - 12000 : diagonal,
							boundaryIds, sector);
					}
				}
			}
		}
		for (NativeLayeredPlacementSet set
			: worldPackage.getPlacementSets().values()) {
			for (NativeLayeredNpcPlacement placement : set.getNpcs()) {
				npcIds.add(Integer.valueOf(placement.getNpcId()));
			}
			for (NativeLayeredGroundItemPlacement placement
				: set.getGroundItems()) {
				itemIds.add(Integer.valueOf(placement.getItemId()));
			}
			for (NativeLayeredSceneryPlacement placement : set.getScenery()) {
				sceneryIds.add(Integer.valueOf(placement.getSceneryId()));
			}
			for (NativeLayeredBoundaryPlacement placement : set.getBoundaries()) {
				boundaryIds.add(Integer.valueOf(placement.getBoundaryId()));
			}
		}
		for (Integer id : tileIds) {
			try {
				if (definitions.getTileDef(id.intValue()) == null) throw new Exception();
			} catch (Exception failure) {
				throw unavailable("tile", id.intValue());
			}
		}
		for (Integer id : boundaryIds) {
			try {
				if (definitions.getDoorDef(id.intValue()) == null) throw new Exception();
			} catch (Exception failure) {
				throw unavailable("boundary", id.intValue());
			}
		}
		for (Integer id : sceneryIds) {
			try {
				if (definitions.getGameObjectDef(id.intValue()) == null) throw new Exception();
			} catch (Exception failure) {
				throw unavailable("scenery", id.intValue());
			}
		}
		for (Integer id : npcIds) {
			try {
				if (definitions.getNpcDef(id.intValue()) == null) throw new Exception();
			} catch (Exception failure) {
				throw unavailable("NPC", id.intValue());
			}
		}
		for (Integer id : itemIds) {
			try {
				if (definitions.getItemDef(id.intValue()) == null) throw new Exception();
			} catch (Exception failure) {
				throw unavailable("item", id.intValue());
			}
		}
		return new Result(tileIds, boundaryIds, sceneryIds, npcIds, itemIds);
	}

	private static void collectWall(
		int raw, Set<Integer> boundaryIds, NativeLayeredTerrainSector sector) {
		if (raw == 0) return;
		if (raw < 1 || raw > 255) {
			throw new IllegalStateException(
				"Adaptive terrain has unsupported wall value " + raw
					+ " at " + sector.getIdentity());
		}
		boundaryIds.add(Integer.valueOf(raw - 1));
	}

	private static IllegalStateException unavailable(String family, int id) {
		return new IllegalStateException(
			"Adaptive package " + family + " definition is unavailable: " + id);
	}

	private static String csv(Set<Integer> values) {
		StringBuilder result = new StringBuilder();
		for (Integer value : values) {
			if (result.length() > 0) result.append(',');
			result.append(value.intValue());
		}
		return result.toString();
	}

	public static final class Result {
		private final Set<Integer> tileIds;
		private final Set<Integer> boundaryIds;
		private final Set<Integer> sceneryIds;
		private final Set<Integer> npcIds;
		private final Set<Integer> itemIds;

		private Result(
			Set<Integer> tileIds, Set<Integer> boundaryIds,
			Set<Integer> sceneryIds, Set<Integer> npcIds,
			Set<Integer> itemIds) {
			this.tileIds = Collections.unmodifiableSet(
				new TreeSet<Integer>(tileIds));
			this.boundaryIds = Collections.unmodifiableSet(
				new TreeSet<Integer>(boundaryIds));
			this.sceneryIds = Collections.unmodifiableSet(
				new TreeSet<Integer>(sceneryIds));
			this.npcIds = Collections.unmodifiableSet(new TreeSet<Integer>(npcIds));
			this.itemIds = Collections.unmodifiableSet(new TreeSet<Integer>(itemIds));
		}

		public String tileIdsCsv() { return csv(tileIds); }
		public String boundaryIdsCsv() { return csv(boundaryIds); }
		public String sceneryIdsCsv() { return csv(sceneryIds); }
		public String npcIdsCsv() { return csv(npcIds); }
		public String itemIdsCsv() { return csv(itemIds); }
	}
}
