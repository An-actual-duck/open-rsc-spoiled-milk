package com.openrsc.server.content.worldedit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Durable, deterministic handoff from the live Builder draft to its launcher. */
public final class WorldEditorLayeredTerrainJournal {
	private static final String HEADER =
		"world-builder-layered-terrain-draft-v1";
	private static final int MAX_TILES = 4096;
	private static final int MAX_SECTORS = 64;

	private WorldEditorLayeredTerrainJournal() {
	}

	public static SaveResult save(
		Path journal,
		String baseManifestSha256,
		Collection<SectorGrowth> requestedSectors,
		Collection<TileEdit> requestedTiles)
		throws IOException {
		if (journal == null || baseManifestSha256 == null
			|| !baseManifestSha256.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException(
				"Layered terrain journal base identity is invalid.");
		}
		List<SectorGrowth> sectors = new ArrayList<SectorGrowth>(
			requestedSectors == null
				? Collections.<SectorGrowth>emptyList() : requestedSectors);
		List<TileEdit> tiles = new ArrayList<TileEdit>(
			requestedTiles == null
				? Collections.<TileEdit>emptyList() : requestedTiles);
		if (sectors.size() > MAX_SECTORS || tiles.size() > MAX_TILES) {
			throw new IllegalArgumentException(
				"Layered terrain draft exceeds its bounded journal.");
		}
		Collections.sort(sectors, new Comparator<SectorGrowth>() {
			@Override
			public int compare(SectorGrowth left, SectorGrowth right) {
				int value = Integer.compare(left.level, right.level);
				if (value == 0) value = Integer.compare(left.sectorX, right.sectorX);
				if (value == 0) value = Integer.compare(left.sectorY, right.sectorY);
				return value;
			}
		});
		Collections.sort(tiles, new Comparator<TileEdit>() {
			@Override
			public int compare(TileEdit left, TileEdit right) {
				int value = Integer.compare(left.level, right.level);
				if (value == 0) value = Integer.compare(left.x, right.x);
				if (value == 0) value = Integer.compare(left.y, right.y);
				return value;
			}
		});
		Set<String> identities = new HashSet<String>();
		for (SectorGrowth sector : sectors) {
			if (!identities.add(
				sector.level + ":" + sector.sectorX + ":" + sector.sectorY)) {
				throw new IllegalArgumentException(
					"Layered terrain journal contains duplicate sector growth.");
			}
		}
		identities.clear();
		for (TileEdit tile : tiles) {
			if (!identities.add(tile.level + ":" + tile.x + ":" + tile.y)) {
				throw new IllegalArgumentException(
					"Layered terrain journal contains duplicate tile edits.");
			}
		}
		StringBuilder output = new StringBuilder(
			160 + sectors.size() * 40 + tiles.size() * 80);
		output.append(HEADER).append('\n')
			.append("base-manifest-sha256\t")
			.append(baseManifestSha256).append('\n')
			.append("tile-count\t").append(tiles.size()).append('\n')
			.append("sector-count\t").append(sectors.size()).append('\n');
		for (SectorGrowth sector : sectors) {
			output.append("sector\t").append(sector.level).append('\t')
				.append(sector.sectorX).append('\t')
				.append(sector.sectorY).append('\n');
		}
		for (TileEdit tile : tiles) {
			output.append("tile\t").append(tile.level).append('\t')
				.append(tile.x).append('\t').append(tile.y).append('\t')
				.append(tile.elevation).append('\t')
				.append(tile.texture).append('\t')
				.append(tile.overlay).append('\t')
				.append(tile.roof).append('\t')
				.append(tile.verticalWall).append('\t')
				.append(tile.horizontalWall).append('\t')
				.append(tile.diagonal).append('\n');
		}
		Files.createDirectories(journal.getParent());
		Path staged = journal.resolveSibling(journal.getFileName() + ".tmp");
		if (Files.exists(staged, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException(
				"Layered terrain journal staging path already exists.");
		}
		Files.write(
			staged,
			output.toString().getBytes(StandardCharsets.US_ASCII),
			StandardOpenOption.CREATE_NEW);
		try {
			try {
				Files.move(
					staged, journal, StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
				Files.move(staged, journal, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(staged);
		}
		return new SaveResult(journal, tiles.size(), sectors.size());
	}

	public static final class SectorGrowth {
		public final int level;
		public final int sectorX;
		public final int sectorY;

		public SectorGrowth(int level, int sectorX, int sectorY) {
			this.level = level;
			this.sectorX = sectorX;
			this.sectorY = sectorY;
		}
	}

	public static final class TileEdit {
		public final int level;
		public final int x;
		public final int y;
		public final int elevation;
		public final int texture;
		public final int overlay;
		public final int roof;
		public final int verticalWall;
		public final int horizontalWall;
		public final int diagonal;

		public TileEdit(
			int level, int x, int y,
			int elevation, int texture, int overlay, int roof,
			int verticalWall, int horizontalWall, int diagonal) {
			if (x < 0 || x > 32767 || y < 0 || y > 32767
				|| !rawByte(elevation) || !rawByte(texture)
				|| !rawByte(overlay) || !rawByte(roof)
				|| !rawByte(verticalWall) || !rawByte(horizontalWall)) {
				throw new IllegalArgumentException(
					"Layered terrain journal tile is outside supported bounds.");
			}
			this.level = level;
			this.x = x;
			this.y = y;
			this.elevation = elevation;
			this.texture = texture;
			this.overlay = overlay;
			this.roof = roof;
			this.verticalWall = verticalWall;
			this.horizontalWall = horizontalWall;
			this.diagonal = diagonal;
		}
	}

	public static final class SaveResult {
		public final Path journal;
		public final int tileCount;
		public final int sectorCount;

		SaveResult(Path journal, int tileCount, int sectorCount) {
			this.journal = journal;
			this.tileCount = tileCount;
			this.sectorCount = sectorCount;
		}
	}

	private static boolean rawByte(int value) {
		return value >= 0 && value <= 255;
	}
}
