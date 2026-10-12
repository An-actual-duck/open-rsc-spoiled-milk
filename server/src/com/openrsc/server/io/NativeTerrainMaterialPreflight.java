package com.openrsc.server.io;

import com.openrsc.server.external.EntityHandler;
import com.openrsc.server.model.world.coordinate.WorldMapSectorId;
import java.util.Objects;

/** Effective material selectors checked over the complete catalog before population. */
public final class NativeTerrainMaterialPreflight {
	public interface Definitions {
		boolean tile(int id);
		boolean door(int id);
		boolean roof(int id);
		boolean scenery(int id);
	}
	private NativeTerrainMaterialPreflight() {}
	public static void validate(NativeLayeredWorldPackageCatalog catalog, EntityHandler handler) {
		TerrainRoofDefinitionCatalog roofs = TerrainRoofDefinitionCatalog.shared();
		validate(catalog, new Definitions() {
			public boolean tile(int id) { return handler.getTileDef(id) != null; }
			public boolean door(int id) { return handler.getDoorDef(id) != null; }
			public boolean roof(int id) { return roofs.hasDefinition(id); }
			public boolean scenery(int id) { return handler.getGameObjectDef(id) != null; }
		});
	}
	public static void validate(NativeLayeredWorldPackageCatalog catalog, Definitions definitions) {
		Objects.requireNonNull(catalog, "catalog"); Objects.requireNonNull(definitions, "definitions");
		for (NativeLayeredWorldPackage loaded : catalog.getPackages()) {
			for (NativeLayeredTerrainSector sector : loaded.getTerrainSectors().values()) {
				for (int x = 0; x < NativeLayeredTerrainSector.SIZE; x++) {
					for (int y = 0; y < NativeLayeredTerrainSector.SIZE; y++) {
						validateTile(loaded.getPackageId(), sector.getIdentity(), x, y, sector.getTile(x, y), definitions);
					}
				}
			}
		}
	}
	public static void validateTile(String packageId, WorldMapSectorId sector, int x, int y,
		NativeLayeredTerrainTile tile, Definitions definitions) {
		int overlay = tile.getOverlay();
		int collisionOverlay = overlay == 250 ? 2 : overlay;
		if (overlay != 255 && collisionOverlay > 0 && !definitions.tile(collisionOverlay - 1)) {
			unavailable(packageId, sector, x, y, "overlay", overlay, collisionOverlay - 1);
		}
		int vertical = tile.getVerticalWall(), horizontal = tile.getHorizontalWall(), roof = tile.getRoof();
		if (vertical > 0 && !definitions.door(vertical - 1)) unavailable(packageId, sector, x, y, "verticalWall", vertical, vertical - 1);
		if (horizontal > 0 && !definitions.door(horizontal - 1)) unavailable(packageId, sector, x, y, "horizontalWall", horizontal, horizontal - 1);
		if (roof > 0 && !definitions.roof(roof - 1)) unavailable(packageId, sector, x, y, "roof", roof, roof - 1);
		int diagonal = tile.getDiagonalWall();
		if (diagonal > 0 && diagonal < 12000 && !definitions.door(diagonal - 1)) {
			unavailable(packageId, sector, x, y, "diagonalWall", diagonal, diagonal - 1);
		} else if (diagonal > 12000 && diagonal < 24000 && !definitions.door(diagonal - 12001)) {
			unavailable(packageId, sector, x, y, "diagonalWall", diagonal, diagonal - 12001);
		} else if (diagonal > 48000 && diagonal < 60000 && !definitions.scenery(diagonal - 48001)) {
			unavailable(packageId, sector, x, y, "embeddedScenery", diagonal, diagonal - 48001);
		}
		// Raw diagonal control endpoints, unused ranges and negative Java carriers
		// retain all bits; none is silently treated as an ordinary definition ID.
		// Ground texture is the complete byte color palette, not a TileDef index.
	}
	private static void unavailable(String packageId, WorldMapSectorId sector, int x, int y,
		String field, int raw, int definition) {
		long worldX = (long) sector.getSectorX() * NativeLayeredTerrainSector.SIZE + x;
		long worldY = (long) sector.getSectorY() * NativeLayeredTerrainSector.SIZE + y;
		throw new IllegalStateException("Native terrain material is unavailable: package=" + packageId
			+ " sector=" + sector + " tile=" + worldX + "," + worldY + " field=" + field
			+ " raw=" + Integer.toUnsignedString(raw) + " definition=" + definition);
	}
}
