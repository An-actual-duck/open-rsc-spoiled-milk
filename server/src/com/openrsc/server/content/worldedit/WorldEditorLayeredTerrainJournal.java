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
	private static final String COMBINED_HEADER =
		"world-builder-layered-draft-v2";
	private static final String AUTHORING_HEADER =
		"world-builder-layered-draft-v3";
	private static final int MAX_TILES = 4096;
	private static final int MAX_SECTORS = 64;
	private static final int MAX_SCENERY = 4096;
	private static final int MAX_NPCS = 4096;

	private WorldEditorLayeredTerrainJournal() {
	}

	public static SaveResult save(
		Path journal,
		String baseManifestSha256,
		Collection<SectorGrowth> requestedSectors,
		Collection<TileEdit> requestedTiles)
		throws IOException {
		return save(
			journal, baseManifestSha256, requestedSectors, requestedTiles,
			Collections.<SceneryEdit>emptyList(),
			Collections.<NpcEdit>emptyList(), false, false);
	}

	public static SaveResult save(
		Path journal,
		String baseManifestSha256,
		Collection<SectorGrowth> requestedSectors,
		Collection<TileEdit> requestedTiles,
		Collection<SceneryEdit> requestedScenery)
		throws IOException {
		return save(
			journal, baseManifestSha256, requestedSectors, requestedTiles,
			requestedScenery, Collections.<NpcEdit>emptyList(), true, false);
	}

	public static SaveResult save(
		Path journal,
		String baseManifestSha256,
		Collection<SectorGrowth> requestedSectors,
		Collection<TileEdit> requestedTiles,
		Collection<SceneryEdit> requestedScenery,
		Collection<NpcEdit> requestedNpcs)
		throws IOException {
		return save(
			journal, baseManifestSha256, requestedSectors, requestedTiles,
			requestedScenery, requestedNpcs, true, true);
	}

	private static SaveResult save(
		Path journal,
		String baseManifestSha256,
		Collection<SectorGrowth> requestedSectors,
		Collection<TileEdit> requestedTiles,
		Collection<SceneryEdit> requestedScenery,
		Collection<NpcEdit> requestedNpcs,
		boolean combined,
		boolean authoring)
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
		List<SceneryEdit> scenery = new ArrayList<SceneryEdit>(
			requestedScenery == null
				? Collections.<SceneryEdit>emptyList() : requestedScenery);
		List<NpcEdit> npcs = new ArrayList<NpcEdit>(
			requestedNpcs == null
				? Collections.<NpcEdit>emptyList() : requestedNpcs);
		if (sectors.size() > MAX_SECTORS || tiles.size() > MAX_TILES
			|| scenery.size() > MAX_SCENERY || npcs.size() > MAX_NPCS) {
			throw new IllegalArgumentException(
				"Layered draft exceeds its bounded journal.");
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
		Collections.sort(scenery, new Comparator<SceneryEdit>() {
			@Override
			public int compare(SceneryEdit left, SceneryEdit right) {
				int value = Integer.compare(left.level, right.level);
				if (value == 0) value = Integer.compare(left.x, right.x);
				if (value == 0) value = Integer.compare(left.y, right.y);
				return value;
			}
		});
		Collections.sort(npcs, new Comparator<NpcEdit>() {
			@Override
			public int compare(NpcEdit left, NpcEdit right) {
				int value = Integer.compare(left.level, right.level);
				if (value == 0) value = Integer.compare(left.startX, right.startX);
				if (value == 0) value = Integer.compare(left.startY, right.startY);
				if (value == 0) value = left.placementId.compareTo(right.placementId);
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
		identities.clear();
		Set<String> placementIds = new HashSet<String>();
		for (SceneryEdit edit : scenery) {
			if (!identities.add(edit.level + ":" + edit.x + ":" + edit.y)) {
				throw new IllegalArgumentException(
					"Layered draft contains duplicate scenery slots.");
			}
			if (!placementIds.add(edit.placementId)) {
				throw new IllegalArgumentException(
					"Layered draft contains duplicate scenery placement IDs.");
			}
		}
		for (NpcEdit edit : npcs) {
			if (!placementIds.add(edit.placementId)) {
				throw new IllegalArgumentException(
					"Layered draft contains duplicate placement IDs.");
			}
		}
		if (sectors.isEmpty() && tiles.isEmpty() && scenery.isEmpty()
			&& npcs.isEmpty()) {
			Files.deleteIfExists(journal);
			Files.deleteIfExists(
				journal.resolveSibling(journal.getFileName() + ".tmp"));
			return new SaveResult(journal, 0, 0, 0, 0);
		}
		StringBuilder output = new StringBuilder(
			180 + sectors.size() * 40 + tiles.size() * 80
				+ scenery.size() * 100 + npcs.size() * 140);
		output.append(authoring ? AUTHORING_HEADER
				: combined ? COMBINED_HEADER : HEADER).append('\n')
			.append("base-manifest-sha256\t")
			.append(baseManifestSha256).append('\n')
			.append("tile-count\t").append(tiles.size()).append('\n')
			.append("sector-count\t").append(sectors.size()).append('\n');
		if (combined) {
			output.append("scenery-count\t").append(scenery.size()).append('\n');
		}
		if (authoring) {
			output.append("npc-count\t").append(npcs.size()).append('\n');
		}
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
		for (SceneryEdit edit : scenery) {
			output.append("scenery\t")
				.append(edit.remove ? "remove" : "upsert").append('\t')
				.append(edit.level).append('\t')
				.append(edit.x).append('\t').append(edit.y).append('\t')
				.append(edit.placementId).append('\t')
				.append(edit.sceneryId).append('\t')
				.append(edit.direction).append('\n');
		}
		for (NpcEdit edit : npcs) {
			output.append("npc\t")
				.append(edit.remove ? "remove" : "upsert").append('\t')
				.append(edit.level).append('\t')
				.append(edit.startX).append('\t').append(edit.startY).append('\t')
				.append(edit.placementId).append('\t')
				.append(edit.npcId).append('\t')
				.append(edit.minX).append('\t').append(edit.minY).append('\t')
				.append(edit.maxX).append('\t').append(edit.maxY).append('\n');
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
		return new SaveResult(
			journal, tiles.size(), sectors.size(), scenery.size(), npcs.size());
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

	public static final class SceneryEdit {
		private static final java.util.regex.Pattern ID =
			java.util.regex.Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
		public final boolean remove;
		public final int level;
		public final int x;
		public final int y;
		public final String placementId;
		public final int sceneryId;
		public final int direction;

		public SceneryEdit(
			boolean remove,
			int level,
			int x,
			int y,
			String placementId,
			int sceneryId,
			int direction) {
			if (x < 0 || x > 32767 || y < 0 || y > 32767
				|| placementId == null || !ID.matcher(placementId).matches()
				|| sceneryId < 0 || direction < 0 || direction > 8) {
				throw new IllegalArgumentException(
					"Layered scenery journal edit is outside supported bounds.");
			}
			this.remove = remove;
			this.level = level;
			this.x = x;
			this.y = y;
			this.placementId = placementId;
			this.sceneryId = sceneryId;
			this.direction = direction;
		}
	}

	public static final class NpcEdit {
		public final boolean remove;
		public final int level;
		public final int startX;
		public final int startY;
		public final String placementId;
		public final int npcId;
		public final int minX;
		public final int minY;
		public final int maxX;
		public final int maxY;

		public NpcEdit(
			boolean remove,
			int level,
			int startX,
			int startY,
			String placementId,
			int npcId,
			int minX,
			int minY,
			int maxX,
			int maxY) {
			if (!coordinate(startX) || !coordinate(startY)
				|| !coordinate(minX) || !coordinate(minY)
				|| !coordinate(maxX) || !coordinate(maxY)
				|| placementId == null
				|| !SceneryEdit.ID.matcher(placementId).matches()
				|| npcId < 0 || minX > startX || startX > maxX
				|| minY > startY || startY > maxY
				|| maxX - minX > 128 || maxY - minY > 128) {
				throw new IllegalArgumentException(
					"Layered NPC journal edit is outside supported bounds.");
			}
			this.remove = remove;
			this.level = level;
			this.startX = startX;
			this.startY = startY;
			this.placementId = placementId;
			this.npcId = npcId;
			this.minX = minX;
			this.minY = minY;
			this.maxX = maxX;
			this.maxY = maxY;
		}
	}

	public static final class SaveResult {
		public final Path journal;
		public final int tileCount;
		public final int sectorCount;
		public final int sceneryCount;
		public final int npcCount;

		SaveResult(
			Path journal, int tileCount, int sectorCount, int sceneryCount,
			int npcCount) {
			this.journal = journal;
			this.tileCount = tileCount;
			this.sectorCount = sectorCount;
			this.sceneryCount = sceneryCount;
			this.npcCount = npcCount;
		}
	}

	private static boolean rawByte(int value) {
		return value >= 0 && value <= 255;
	}

	private static boolean coordinate(int value) {
		return value >= 0 && value <= 32767;
	}
}
