package com.openrsc.worldbuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Materializes the server-authored terrain journal into the isolated layered
 * working package while the launcher owns the workspace lock.
 */
final class WorldBuilderLayeredTerrainDraftJournal {
	static final String RELATIVE_PATH =
		"working/layered-world/terrain-draft-v1.tsv";
	private static final String HEADER =
		"world-builder-layered-terrain-draft-v1";
	private static final int SECTOR_SIZE = 48;
	private static final int TILE_BYTES = 10;
	private static final int MAX_TILES = 4096;
	private static final int MAX_SECTORS = 64;

	CommitResult commitIfPresentLocked(Path requestedWorkspace)
		throws IOException, WorldBuilderDiscoveryException {
		Path workspace = canonicalWorkspace(requestedWorkspace);
		Path journalPath = workspace.resolve(RELATIVE_PATH).normalize();
		requireContained(workspace, journalPath, "terrain draft journal");
		if (!Files.exists(journalPath, LinkOption.NOFOLLOW_LINKS)) return null;
		if (!Files.isRegularFile(journalPath, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(journalPath)
			|| Files.size(journalPath) > 1_048_576L) {
			throw new WorldBuilderDiscoveryException(
				"Layered terrain draft journal is missing or unsafe.");
		}
		Journal journal = read(journalPath);
		WorldBuilderSourceSnapshot.verify(workspace);
		Path sourceRoot = workspace.resolve(
			WorldBuilderRuntimePreparer.LAYERED_SOURCE_PACKAGE).normalize();
		Path packageRoot = workspace.resolve(
			WorldBuilderRuntimePreparer.LAYERED_WORKING_PACKAGE).normalize();
		requireDirectory(workspace, sourceRoot, "layered source package");
		requireDirectory(workspace, packageRoot, "layered working package");
		WorldBuilderLayeredPackage source =
			WorldBuilderLayeredPackage.discover(
				sourceRoot, WorldBuilderLayeredPackage.PROFILE_ID);
		WorldBuilderLayeredPackage current =
			WorldBuilderLayeredPackage.discoverDraft(packageRoot);
		current.requireTerrainDraftDescendant(source);
		if (!journal.baseManifestSha256.equals(current.manifestSha256)) {
			throw new WorldBuilderDiscoveryException(
				"Layered terrain draft journal was authored against a different "
					+ "working manifest.");
		}

		Path parent = packageRoot.getParent();
		String transaction = UUID.randomUUID().toString();
		Path stage = parent.resolve(".package.terrain-draft-" + transaction);
		Path backup = parent.resolve(".package.rollback-" + transaction);
		boolean originalMoved = false;
		boolean draftMoved = false;
		try {
			copyTree(packageRoot, stage);
			apply(stage, source, current, journal);
			WorldBuilderLayeredPackage candidate =
				WorldBuilderLayeredPackage.discoverDraft(stage);
			candidate.requireTerrainDraftDescendant(source);
			if (candidate.terrainSectorCount
					!= current.terrainSectorCount + journal.sectors.size()) {
				throw new WorldBuilderDiscoveryException(
					"Terrain draft candidate sector count is inconsistent.");
			}
			WorldBuilderSourceSnapshot.verify(workspace);
			moveDirectory(packageRoot, backup);
			originalMoved = true;
			moveDirectory(stage, packageRoot);
			draftMoved = true;
			WorldBuilderLayeredReview installed =
				WorldBuilderLayeredReview.readIfPresent(workspace);
			if (installed == null
				|| !installed.manifestSha256.equals(candidate.manifestSha256)) {
				throw new WorldBuilderDiscoveryException(
					"Installed terrain draft failed complete workspace validation.");
			}
			WorldBuilderSourceSnapshot.verify(workspace);
			Files.delete(journalPath);
			originalMoved = false;
			deleteTreeQuietly(backup);
			return new CommitResult(
				journal.tiles.size(), journal.sectors.size(),
				installed.manifestSha256,
				installed.packageFingerprintSha256);
		} catch (IOException failure) {
			rollback(packageRoot, stage, backup, originalMoved, draftMoved);
			throw failure;
		} catch (WorldBuilderDiscoveryException failure) {
			rollback(packageRoot, stage, backup, originalMoved, draftMoved);
			throw failure;
		} catch (RuntimeException failure) {
			rollback(packageRoot, stage, backup, originalMoved, draftMoved);
			throw failure;
		} finally {
			if (!draftMoved) deleteTreeQuietly(stage);
		}
	}

	private static void apply(
		Path packageRoot,
		WorldBuilderLayeredPackage source,
		WorldBuilderLayeredPackage current,
		Journal journal)
		throws IOException, WorldBuilderDiscoveryException {
		Path manifestPath = packageRoot.resolve("manifest.json");
		Map<String,Object> manifest =
			WorldBuilderJsonDocuments.readObject(manifestPath);
		List<Object> terrain = array(manifest, "terrainSectors");
		Map<String,Map<String,Object>> declarations =
			new LinkedHashMap<String,Map<String,Object>>();
		Set<String> occupied = new HashSet<String>();
		for (Object value : terrain) {
			Map<String,Object> record = object(value);
			String key = key(number(record, "level"), number(record, "sectorX"),
				number(record, "sectorY"));
			declarations.put(key, record);
			occupied.add(key);
		}
		Set<Integer> sourceLevels = new HashSet<Integer>(source.levels);
		for (SectorGrowth growth : journal.sectors) {
			if (sourceLevels.contains(Integer.valueOf(growth.level))
				|| !current.levels.contains(Integer.valueOf(growth.level))) {
				throw new WorldBuilderDiscoveryException(
					"Terrain sector growth is restricted to a Builder-created level.");
			}
			String identity = key(growth.level, growth.sectorX, growth.sectorY);
			if (occupied.contains(identity)) {
				throw new WorldBuilderDiscoveryException(
					"Terrain draft tries to recreate an allocated sector: "
						+ identity);
			}
			if (!adjacent(occupied, growth.level, growth.sectorX, growth.sectorY)) {
				throw new WorldBuilderDiscoveryException(
					"Terrain sector growth must share an edge with allocated terrain: "
						+ identity);
			}
			String relative = terrainPath(
				growth.level, growth.sectorX, growth.sectorY);
			Path payload = packageRoot.resolve(relative).normalize();
			requireContained(packageRoot, payload, relative);
			Files.createDirectories(payload.getParent());
			Files.write(payload, voidTerrain(), StandardOpenOption.CREATE_NEW);
			Map<String,Object> declaration = new LinkedHashMap<String,Object>();
			declaration.put("encoding", "raw-layered-sector-v1");
			declaration.put("level", Long.valueOf(growth.level));
			declaration.put("path", relative);
			declaration.put("sectorX", Long.valueOf(growth.sectorX));
			declaration.put("sectorY", Long.valueOf(growth.sectorY));
			declaration.put("sha256", WorldBuilderHashes.sha256(payload));
			declaration.put("worldSpace", "global");
			terrain.add(declaration);
			declarations.put(identity, declaration);
			occupied.add(identity);
		}

		Map<Path,byte[]> changedPayloads = new LinkedHashMap<Path,byte[]>();
		Map<Path,Map<String,Object>> changedDeclarations =
			new HashMap<Path,Map<String,Object>>();
		for (TileEdit edit : journal.tiles) {
			if (sourceLevels.contains(Integer.valueOf(edit.level))
				|| !current.levels.contains(Integer.valueOf(edit.level))) {
				throw new WorldBuilderDiscoveryException(
					"Terrain editing is restricted to a Builder-created level.");
			}
			int sectorX = Math.floorDiv(edit.x, SECTOR_SIZE);
			int sectorY = Math.floorDiv(edit.y, SECTOR_SIZE);
			Map<String,Object> declaration =
				declarations.get(key(edit.level, sectorX, sectorY));
			if (declaration == null) {
				throw new WorldBuilderDiscoveryException(
					"Terrain edit targets an unallocated sector.");
			}
			String relative = text(declaration, "path");
			if (!relative.equals(terrainPath(edit.level, sectorX, sectorY))) {
				throw new WorldBuilderDiscoveryException(
					"Terrain edit payload path is not deterministic.");
			}
			Path payload = packageRoot.resolve(relative).normalize();
			requireContained(packageRoot, payload, relative);
			byte[] bytes = changedPayloads.get(payload);
			if (bytes == null) {
				bytes = Files.readAllBytes(payload);
				if (bytes.length != SECTOR_SIZE * SECTOR_SIZE * TILE_BYTES) {
					throw new WorldBuilderDiscoveryException(
						"Terrain edit payload has an invalid size.");
				}
				changedPayloads.put(payload, bytes);
				changedDeclarations.put(payload, declaration);
			}
			int localX = Math.floorMod(edit.x, SECTOR_SIZE);
			int localY = Math.floorMod(edit.y, SECTOR_SIZE);
			int offset = (localX * SECTOR_SIZE + localY) * TILE_BYTES;
			bytes[offset] = (byte)edit.elevation;
			bytes[offset + 1] = (byte)edit.texture;
			bytes[offset + 2] = (byte)edit.overlay;
			bytes[offset + 3] = (byte)edit.roof;
			bytes[offset + 4] = (byte)edit.verticalWall;
			bytes[offset + 5] = (byte)edit.horizontalWall;
			bytes[offset + 6] = (byte)(edit.diagonal >>> 24);
			bytes[offset + 7] = (byte)(edit.diagonal >>> 16);
			bytes[offset + 8] = (byte)(edit.diagonal >>> 8);
			bytes[offset + 9] = (byte)edit.diagonal;
		}
		for (Map.Entry<Path,byte[]> changed : changedPayloads.entrySet()) {
			Files.write(changed.getKey(), changed.getValue(),
				StandardOpenOption.TRUNCATE_EXISTING);
			changedDeclarations.get(changed.getKey()).put(
				"sha256", WorldBuilderHashes.sha256(changed.getKey()));
		}
		sortTerrain(terrain);
		Path stagedManifest = packageRoot.resolve(".manifest.terrain-draft");
		Files.write(
			stagedManifest,
			WorldBuilderJsonDocuments.pretty(manifest)
				.getBytes(StandardCharsets.UTF_8),
			StandardOpenOption.CREATE_NEW);
		moveFile(stagedManifest, manifestPath);
	}

	private static Journal read(Path path)
		throws IOException, WorldBuilderDiscoveryException {
		List<String> lines = Files.readAllLines(path, StandardCharsets.US_ASCII);
		if (lines.size() < 4 || !HEADER.equals(lines.get(0))) {
			throw new WorldBuilderDiscoveryException(
				"Layered terrain draft journal header is invalid.");
		}
		String base = field(lines.get(1), "base-manifest-sha256");
		if (!base.matches("[0-9a-f]{64}")) {
			throw new WorldBuilderDiscoveryException(
				"Layered terrain draft base manifest is invalid.");
		}
		int tileCount = boundedCount(field(lines.get(2), "tile-count"), MAX_TILES);
		int sectorCount =
			boundedCount(field(lines.get(3), "sector-count"), MAX_SECTORS);
		if (tileCount == 0 && sectorCount == 0) {
			throw new WorldBuilderDiscoveryException(
				"Layered terrain draft journal is empty.");
		}
		if (lines.size() != 4 + sectorCount + tileCount) {
			throw new WorldBuilderDiscoveryException(
				"Layered terrain draft journal count is inconsistent.");
		}
		List<SectorGrowth> sectors = new ArrayList<SectorGrowth>();
		List<TileEdit> tiles = new ArrayList<TileEdit>();
		Set<String> sectorKeys = new HashSet<String>();
		Set<String> tileKeys = new HashSet<String>();
		int index = 4;
		for (int item = 0; item < sectorCount; item++, index++) {
			String[] values = lines.get(index).split("\\t", -1);
			if (values.length != 4 || !"sector".equals(values[0])) {
				throw new WorldBuilderDiscoveryException(
					"Layered terrain sector-growth record is malformed.");
			}
			SectorGrowth growth = new SectorGrowth(
				signed(values[1]), signed(values[2]), signed(values[3]));
			if (!sectorKeys.add(key(
				growth.level, growth.sectorX, growth.sectorY))) {
				throw new WorldBuilderDiscoveryException(
					"Layered terrain journal contains duplicate sector growth.");
			}
			sectors.add(growth);
		}
		for (int item = 0; item < tileCount; item++, index++) {
			String[] values = lines.get(index).split("\\t", -1);
			if (values.length != 11 || !"tile".equals(values[0])) {
				throw new WorldBuilderDiscoveryException(
					"Layered terrain tile record is malformed.");
			}
			TileEdit tile = new TileEdit(
				signed(values[1]), coordinate(values[2]), coordinate(values[3]),
				unsignedByte(values[4]), unsignedByte(values[5]),
				unsignedByte(values[6]), unsignedByte(values[7]),
				unsignedByte(values[8]), unsignedByte(values[9]),
				signed(values[10]));
			if (!tileKeys.add(key(tile.level, tile.x, tile.y))) {
				throw new WorldBuilderDiscoveryException(
					"Layered terrain journal contains duplicate tile edits.");
			}
			tiles.add(tile);
		}
		return new Journal(base, sectors, tiles);
	}

	private static String field(String line, String name)
		throws WorldBuilderDiscoveryException {
		String prefix = name + "\t";
		if (!line.startsWith(prefix) || line.length() == prefix.length()) {
			throw new WorldBuilderDiscoveryException(
				"Layered terrain draft journal field is invalid: " + name);
		}
		return line.substring(prefix.length());
	}

	private static int boundedCount(String value, int maximum)
		throws WorldBuilderDiscoveryException {
		int result = signed(value);
		if (result < 0 || result > maximum) {
			throw new WorldBuilderDiscoveryException(
				"Layered terrain draft journal count is out of range.");
		}
		return result;
	}

	private static int coordinate(String value)
		throws WorldBuilderDiscoveryException {
		int result = signed(value);
		if (result < 0 || result > 32767) {
			throw new WorldBuilderDiscoveryException(
				"Layered terrain draft coordinate is out of range.");
		}
		return result;
	}

	private static int unsignedByte(String value)
		throws WorldBuilderDiscoveryException {
		int result = signed(value);
		if (result < 0 || result > 255) {
			throw new WorldBuilderDiscoveryException(
				"Layered terrain draft byte is out of range.");
		}
		return result;
	}

	private static int signed(String value)
		throws WorldBuilderDiscoveryException {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException failure) {
			throw new WorldBuilderDiscoveryException(
				"Layered terrain draft integer is invalid.");
		}
	}

	private static boolean adjacent(
		Set<String> occupied, int level, int sectorX, int sectorY) {
		return occupied.contains(key(level, sectorX + 1, sectorY))
			|| occupied.contains(key(level, sectorX - 1, sectorY))
			|| occupied.contains(key(level, sectorX, sectorY + 1))
			|| occupied.contains(key(level, sectorX, sectorY - 1));
	}

	private static byte[] voidTerrain() {
		byte[] result = new byte[SECTOR_SIZE * SECTOR_SIZE * TILE_BYTES];
		for (int offset = 0; offset < result.length; offset += TILE_BYTES) {
			result[offset + 1] = 1;
			result[offset + 2] = 8;
		}
		return result;
	}

	private static String terrainPath(int level, int sectorX, int sectorY) {
		return "terrain/global/l" + WorldBuilderLayeredPackage.signedToken(level)
			+ "/x" + WorldBuilderLayeredPackage.signedToken(sectorX)
			+ "-y" + WorldBuilderLayeredPackage.signedToken(sectorY) + ".raw";
	}

	private static String key(int level, int x, int y) {
		return level + ":" + x + ":" + y;
	}

	private static void sortTerrain(List<Object> values) {
		Collections.sort(values, new Comparator<Object>() {
			@Override
			public int compare(Object left, Object right) {
				Map<String,Object> a = object(left);
				Map<String,Object> b = object(right);
				int result = Integer.compare(number(a, "level"), number(b, "level"));
				if (result == 0) result = Integer.compare(
					number(a, "sectorX"), number(b, "sectorX"));
				if (result == 0) result = Integer.compare(
					number(a, "sectorY"), number(b, "sectorY"));
				return result;
			}
		});
	}

	private static Path canonicalWorkspace(Path requested)
		throws IOException, WorldBuilderDiscoveryException {
		Path workspace = requested.toAbsolutePath().normalize();
		if (!Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(workspace)) {
			throw new WorldBuilderDiscoveryException(
				"Prepared World Builder workspace is missing or unsafe.");
		}
		return workspace.toRealPath();
	}

	private static void requireDirectory(Path root, Path path, String label)
		throws IOException, WorldBuilderDiscoveryException {
		requireContained(root, path, label);
		if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(path)
			|| !path.toRealPath().startsWith(root)) {
			throw new WorldBuilderDiscoveryException(
				"Prepared " + label + " is missing or unsafe.");
		}
	}

	private static void requireContained(Path root, Path path, String label)
		throws WorldBuilderDiscoveryException {
		if (!path.startsWith(root)) {
			throw new WorldBuilderDiscoveryException(
				"Layered terrain draft path escapes the workspace: " + label);
		}
	}

	private static void copyTree(final Path source, final Path destination)
		throws IOException {
		Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(
				Path directory, BasicFileAttributes attributes) throws IOException {
				if (Files.isSymbolicLink(directory)) {
					throw new IOException(
						"Layered package contains a symbolic link: " + directory);
				}
				Files.createDirectories(
					destination.resolve(source.relativize(directory)));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(
				Path file, BasicFileAttributes attributes) throws IOException {
				if (Files.isSymbolicLink(file) || !attributes.isRegularFile()) {
					throw new IOException(
						"Layered package contains an unsupported entry: " + file);
				}
				Path target = destination.resolve(source.relativize(file));
				Files.createDirectories(target.getParent());
				Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void moveFile(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
				StandardCopyOption.REPLACE_EXISTING);
		} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void moveDirectory(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
			Files.move(source, target);
		}
	}

	private static void rollback(
		Path packageRoot, Path stage, Path backup,
		boolean originalMoved, boolean draftMoved) {
		try {
			if (draftMoved && Files.exists(packageRoot, LinkOption.NOFOLLOW_LINKS)) {
				deleteTree(packageRoot);
			}
			if (originalMoved && Files.exists(backup, LinkOption.NOFOLLOW_LINKS)
				&& !Files.exists(packageRoot, LinkOption.NOFOLLOW_LINKS)) {
				moveDirectory(backup, packageRoot);
			}
		} catch (Exception rollbackFailure) {
			throw new IllegalStateException(
				"Terrain draft failed and automatic workspace rollback also failed. "
					+ "The immutable source snapshot remains unchanged.",
				rollbackFailure);
		} finally {
			deleteTreeQuietly(stage);
		}
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(
				Path file, BasicFileAttributes attributes) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(
				Path directory, IOException failure) throws IOException {
				if (failure != null) throw failure;
				Files.delete(directory);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void deleteTreeQuietly(Path root) {
		try {
			deleteTree(root);
		} catch (Exception ignored) {
		}
	}

	@SuppressWarnings("unchecked")
	private static List<Object> array(Map<String,Object> root, String key)
		throws WorldBuilderDiscoveryException {
		Object value = root.get(key);
		if (!(value instanceof List)) {
			throw new WorldBuilderDiscoveryException(
				"Layered manifest field is not an array: " + key);
		}
		return (List<Object>)value;
	}

	@SuppressWarnings("unchecked")
	private static Map<String,Object> object(Object value) {
		return (Map<String,Object>)value;
	}

	private static int number(Map<String,Object> value, String key) {
		return ((Long)value.get(key)).intValue();
	}

	private static String text(Map<String,Object> value, String key) {
		return (String)value.get(key);
	}

	static final class CommitResult {
		final int tileCount;
		final int sectorCount;
		final String manifestSha256;
		final String packageFingerprintSha256;

		CommitResult(
			int tileCount, int sectorCount,
			String manifestSha256, String packageFingerprintSha256) {
			this.tileCount = tileCount;
			this.sectorCount = sectorCount;
			this.manifestSha256 = manifestSha256;
			this.packageFingerprintSha256 = packageFingerprintSha256;
		}
	}

	private static final class Journal {
		final String baseManifestSha256;
		final List<SectorGrowth> sectors;
		final List<TileEdit> tiles;

		Journal(
			String baseManifestSha256,
			List<SectorGrowth> sectors,
			List<TileEdit> tiles) {
			this.baseManifestSha256 = baseManifestSha256;
			this.sectors = sectors;
			this.tiles = tiles;
		}
	}

	private static final class SectorGrowth {
		final int level;
		final int sectorX;
		final int sectorY;

		SectorGrowth(int level, int sectorX, int sectorY) {
			this.level = level;
			this.sectorX = sectorX;
			this.sectorY = sectorY;
		}
	}

	private static final class TileEdit {
		final int level;
		final int x;
		final int y;
		final int elevation;
		final int texture;
		final int overlay;
		final int roof;
		final int verticalWall;
		final int horizontalWall;
		final int diagonal;

		TileEdit(
			int level, int x, int y,
			int elevation, int texture, int overlay, int roof,
			int verticalWall, int horizontalWall, int diagonal) {
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
}
