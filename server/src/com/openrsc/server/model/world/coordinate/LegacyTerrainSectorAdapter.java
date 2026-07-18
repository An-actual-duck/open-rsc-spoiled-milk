package com.openrsc.server.model.world.coordinate;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checked server codec for legacy terrain archive entry names. */
public final class LegacyTerrainSectorAdapter {
	public static final String ID = "legacy-terrain-sector-name-v1";
	public static final int ARCHIVE_SECTOR_X_OFFSET = 48;
	public static final int ARCHIVE_SECTOR_Y_OFFSET = 37;

	private static final Pattern ENTRY = Pattern.compile("h([0-3])x([0-9]+)y([0-9]+)");

	private LegacyTerrainSectorAdapter() {
	}

	public static WorldMapSectorId decode(String entryName) {
		Objects.requireNonNull(entryName, "entryName");
		Matcher matcher = ENTRY.matcher(entryName);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Unsupported legacy terrain entry: " + entryName);
		}
		return fromLegacySector(
			parseIndex(matcher.group(1), "plane"),
			parseIndex(matcher.group(2), "sector X"),
			parseIndex(matcher.group(3), "sector Y"));
	}

	public static WorldMapSectorId fromLegacySector(
		int legacyPlane,
		int archiveSectorX,
		int archiveSectorY) {
		if (archiveSectorX < 0 || archiveSectorY < 0) {
			throw new IllegalArgumentException("Legacy archive sector indices must be non-negative");
		}
		return new WorldMapSectorId(
			WorldSpaceId.GLOBAL,
			LegacyPackedPointAdapter.levelForLegacyPlane(legacyPlane),
			Math.subtractExact(archiveSectorX, ARCHIVE_SECTOR_X_OFFSET),
			Math.subtractExact(archiveSectorY, ARCHIVE_SECTOR_Y_OFFSET));
	}

	public static String encode(WorldMapSectorId sector) {
		Objects.requireNonNull(sector, "sector");
		if (!WorldSpaceId.GLOBAL.equals(sector.getWorldSpace())) {
			throw new IllegalArgumentException(
				"Legacy terrain entry cannot represent world space: " + sector.getWorldSpace());
		}
		int plane = LegacyPackedPointAdapter.legacyPlaneForLevel(sector.getLevel());
		int archiveSectorX = Math.addExact(sector.getSectorX(), ARCHIVE_SECTOR_X_OFFSET);
		int archiveSectorY = Math.addExact(sector.getSectorY(), ARCHIVE_SECTOR_Y_OFFSET);
		if (archiveSectorX < 0 || archiveSectorY < 0) {
			throw new IllegalArgumentException(
				"Logical sector is outside the non-negative legacy archive grid: " + sector);
		}
		return "h" + plane + "x" + archiveSectorX + "y" + archiveSectorY;
	}

	private static int parseIndex(String text, String label) {
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException failure) {
			throw new IllegalArgumentException(
				"Legacy terrain " + label + " exceeds signed 32-bit range: " + text, failure);
		}
	}
}
