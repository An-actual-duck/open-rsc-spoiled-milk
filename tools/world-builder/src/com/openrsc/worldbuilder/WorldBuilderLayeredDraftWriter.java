package com.openrsc.worldbuilder;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Offline transaction boundary for workspace-owned native layered drafts.
 *
 * The source snapshot and target game files are never writable inputs. The
 * first operation creates one terrain-only signed level and keeps every
 * ordinary edit/export path disabled.
 */
final class WorldBuilderLayeredDraftWriter {
	private static final int SECTOR_SIZE = 48;
	private static final int TILE_BYTES = 10;
	private static final Pattern ROLE =
		Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");

	CreateLevelResult createLevel(
		Path requestedWorkspace,
		int level,
		int anchorX,
		int anchorY,
		String requestedName,
		String requestedRole)
		throws IOException, WorldBuilderDiscoveryException {
		String name = checkedName(requestedName);
		String role = checkedRole(requestedRole);
		Path workspace = canonicalWorkspace(requestedWorkspace);
		Path lockPath = workspace.getParent().resolve(
			"." + workspace.getFileName() + ".world-builder.lock");
		try (FileChannel channel = FileChannel.open(
			lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
			FileLock lock;
			try {
				lock = channel.tryLock();
			} catch (OverlappingFileLockException busy) {
				lock = null;
			}
			if (lock == null) {
				throw new WorldBuilderDiscoveryException(
					"Close the World Builder before creating a level in this workspace.");
			}
			try {
				return createLevelLocked(
					workspace, level, anchorX, anchorY, name, role);
			} finally {
				lock.release();
			}
		}
	}

	private CreateLevelResult createLevelLocked(
		Path workspace,
		int level,
		int anchorX,
		int anchorY,
		String name,
		String role)
		throws IOException, WorldBuilderDiscoveryException {
		WorldBuilderSourceSnapshot.verify(workspace);
		WorldBuilderLayeredReview review =
			WorldBuilderLayeredReview.readIfPresent(workspace);
		if (review == null) {
			throw new WorldBuilderDiscoveryException(
				"Create Level requires a prepared layered World Builder workspace.");
		}
		Path sourceRoot =
			workspace.resolve(WorldBuilderRuntimePreparer.LAYERED_SOURCE_PACKAGE)
				.normalize();
		Path packageRoot =
			workspace.resolve(WorldBuilderRuntimePreparer.LAYERED_WORKING_PACKAGE)
				.normalize();
		requireContainedDirectory(workspace, sourceRoot, "layered source package");
		requireContainedDirectory(workspace, packageRoot, "layered working package");
		WorldBuilderLayeredPackage source =
			WorldBuilderLayeredPackage.discover(
				sourceRoot, WorldBuilderLayeredPackage.PROFILE_ID);
		WorldBuilderLayeredPackage current =
			WorldBuilderLayeredPackage.discoverDraft(packageRoot);
		current.requireFirstDraftDescendant(source);
		if (current.levels.contains(Integer.valueOf(level))) {
			throw new WorldBuilderDiscoveryException(
				"Layer " + level + " is already declared in this draft.");
		}

		int centerSectorX = Math.floorDiv(anchorX, SECTOR_SIZE);
		int centerSectorY = Math.floorDiv(anchorY, SECTOR_SIZE);
		if (centerSectorX == Integer.MIN_VALUE
			|| centerSectorX == Integer.MAX_VALUE
			|| centerSectorY == Integer.MIN_VALUE
			|| centerSectorY == Integer.MAX_VALUE) {
			throw new WorldBuilderDiscoveryException(
				"Create Level anchor is too close to the signed coordinate limit.");
		}
		int minimumSectorX = centerSectorX - 1;
		int maximumSectorX = centerSectorX + 1;
		int minimumSectorY = centerSectorY - 1;
		int maximumSectorY = centerSectorY + 1;

		Path parent = packageRoot.getParent();
		String transaction = UUID.randomUUID().toString();
		Path stage = parent.resolve(".package.create-level-" + transaction);
		Path backup = parent.resolve(".package.rollback-" + transaction);
		boolean originalMoved = false;
		boolean draftMoved = false;
		try {
			copyTree(packageRoot, stage);
			writeLevel(
				stage, level, name, role, anchorX, anchorY,
				minimumSectorX, maximumSectorX,
				minimumSectorY, maximumSectorY);
			WorldBuilderLayeredPackage candidate =
				WorldBuilderLayeredPackage.discoverDraft(stage);
			candidate.requireFirstDraftDescendant(source);
			if (!candidate.levels.contains(Integer.valueOf(level))
				|| candidate.terrainSectorCount != current.terrainSectorCount + 9
				|| candidate.placementSetCount != current.placementSetCount + 1) {
				throw new WorldBuilderDiscoveryException(
					"Create Level candidate did not produce the expected package shape.");
			}
			WorldBuilderSourceSnapshot.verify(workspace);

			moveDirectory(packageRoot, backup);
			originalMoved = true;
			moveDirectory(stage, packageRoot);
			draftMoved = true;
			WorldBuilderLayeredReview installed =
				WorldBuilderLayeredReview.readIfPresent(workspace);
			if (installed == null
				|| !installed.levels.contains(Integer.valueOf(level))) {
				throw new WorldBuilderDiscoveryException(
					"Installed layered draft did not pass complete workspace validation.");
			}
			WorldBuilderSourceSnapshot.verify(workspace);
			originalMoved = false;
			deleteTreeQuietly(backup);
			return new CreateLevelResult(
				level, anchorX, anchorY, name, role,
				minimumSectorX, maximumSectorX,
				minimumSectorY, maximumSectorY,
				installed.manifestSha256,
				installed.packageFingerprintSha256,
				installed.terrainSectorCount,
				installed.placementSetCount);
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

	private static void writeLevel(
		Path packageRoot,
		int level,
		String name,
		String role,
		int anchorX,
		int anchorY,
		int minimumSectorX,
		int maximumSectorX,
		int minimumSectorY,
		int maximumSectorY)
		throws IOException, WorldBuilderDiscoveryException {
		Path manifestPath = packageRoot.resolve("manifest.json");
		Map<String,Object> manifest =
			WorldBuilderJsonDocuments.readObject(manifestPath);
		List<Object> levels = array(manifest, "levels");
		List<Object> terrain = array(manifest, "terrainSectors");
		List<Object> placements = array(manifest, "placementSets");

		Map<String,Object> levelRecord = new LinkedHashMap<String,Object>();
		levelRecord.put("level", Long.valueOf(level));
		levelRecord.put("name", name);
		levelRecord.put("role", role);
		levelRecord.put("worldSpace", "global");
		levels.add(levelRecord);

		for (int sectorX = minimumSectorX; sectorX <= maximumSectorX; sectorX++) {
			for (int sectorY = minimumSectorY; sectorY <= maximumSectorY; sectorY++) {
				byte[] starterTerrain =
					starterTerrain(sectorX, sectorY, anchorX, anchorY);
				String relative = terrainPath(level, sectorX, sectorY);
				Path payload = packageRoot.resolve(relative).normalize();
				requireContained(packageRoot, payload, relative);
				Files.createDirectories(payload.getParent());
				Files.write(payload, starterTerrain, StandardOpenOption.CREATE_NEW);
				Map<String,Object> record = new LinkedHashMap<String,Object>();
				record.put("encoding", "raw-layered-sector-v1");
				record.put("level", Long.valueOf(level));
				record.put("path", relative);
				record.put("sectorX", Long.valueOf(sectorX));
				record.put("sectorY", Long.valueOf(sectorY));
				record.put("sha256", WorldBuilderHashes.sha256(payload));
				record.put("worldSpace", "global");
				terrain.add(record);
			}
		}

		String placementPath =
			"placements/global/l" + WorldBuilderLayeredPackage.signedToken(level)
				+ ".json";
		Path placementPayload = packageRoot.resolve(placementPath).normalize();
		requireContained(packageRoot, placementPayload, placementPath);
		Files.createDirectories(placementPayload.getParent());
		Map<String,Object> empty = new LinkedHashMap<String,Object>();
		empty.put("boundaries", new ArrayList<Object>());
		empty.put("encoding", "layered-world-placements-v3");
		empty.put("groundItems", new ArrayList<Object>());
		empty.put("level", Long.valueOf(level));
		empty.put("npcs", new ArrayList<Object>());
		empty.put("scenery", new ArrayList<Object>());
		empty.put("schemaVersion", Long.valueOf(3));
		empty.put("worldSpace", "global");
		Files.write(
			placementPayload,
			WorldBuilderJsonDocuments.pretty(empty).getBytes(StandardCharsets.UTF_8),
			StandardOpenOption.CREATE_NEW);
		Map<String,Object> placementRecord = new LinkedHashMap<String,Object>();
		placementRecord.put("encoding", "layered-world-placements-v3");
		placementRecord.put(
			"id", "spoiled-milk-builder-l"
				+ WorldBuilderLayeredPackage.signedToken(level));
		placementRecord.put("level", Long.valueOf(level));
		placementRecord.put("path", placementPath);
		placementRecord.put("sha256", WorldBuilderHashes.sha256(placementPayload));
		placementRecord.put("worldSpace", "global");
		placements.add(placementRecord);

		sortByLevel(levels);
		sortPlacements(placements);
		sortTerrain(terrain);
		Path stagedManifest = packageRoot.resolve(".manifest.create-level");
		Files.write(
			stagedManifest,
			WorldBuilderJsonDocuments.pretty(manifest)
				.getBytes(StandardCharsets.UTF_8),
			StandardOpenOption.CREATE_NEW);
		moveFile(stagedManifest, manifestPath);
	}

	private static byte[] starterTerrain(
		int sectorX, int sectorY, int anchorX, int anchorY) {
		byte[] result = new byte[SECTOR_SIZE * SECTOR_SIZE * TILE_BYTES];
		for (int localX = 0; localX < SECTOR_SIZE; localX++) {
			for (int localY = 0; localY < SECTOR_SIZE; localY++) {
				int offset = (localX * SECTOR_SIZE + localY) * TILE_BYTES;
				result[offset + 1] = 1;
				long worldX = (long)sectorX * SECTOR_SIZE + localX;
				long worldY = (long)sectorY * SECTOR_SIZE + localY;
				boolean anchorPad =
					Math.abs(worldX - anchorX) <= 1L
						&& Math.abs(worldY - anchorY) <= 1L;
				result[offset + 2] = (byte)(anchorPad ? 0 : 8);
			}
		}
		return result;
	}

	private static void sortByLevel(List<Object> values) {
		Collections.sort(values, new Comparator<Object>() {
			@Override
			public int compare(Object left, Object right) {
				return Integer.compare(number(left, "level"), number(right, "level"));
			}
		});
	}

	private static void sortPlacements(List<Object> values) {
		Collections.sort(values, new Comparator<Object>() {
			@Override
			public int compare(Object left, Object right) {
				int level = Integer.compare(
					number(left, "level"), number(right, "level"));
				return level != 0 ? level
					: text(left, "id").compareTo(text(right, "id"));
			}
		});
	}

	private static void sortTerrain(List<Object> values) {
		Collections.sort(values, new Comparator<Object>() {
			@Override
			public int compare(Object left, Object right) {
				int result = Integer.compare(
					number(left, "level"), number(right, "level"));
				if (result == 0) result = Integer.compare(
					number(left, "sectorX"), number(right, "sectorX"));
				if (result == 0) result = Integer.compare(
					number(left, "sectorY"), number(right, "sectorY"));
				return result;
			}
		});
	}

	private static String terrainPath(int level, int sectorX, int sectorY) {
		return "terrain/global/l" + WorldBuilderLayeredPackage.signedToken(level)
			+ "/x" + WorldBuilderLayeredPackage.signedToken(sectorX)
			+ "-y" + WorldBuilderLayeredPackage.signedToken(sectorY) + ".raw";
	}

	private static String checkedName(String value)
		throws WorldBuilderDiscoveryException {
		String result = value == null ? "" : value.trim();
		if (result.isEmpty() || result.length() > 128) {
			throw new WorldBuilderDiscoveryException(
				"Create Level name must contain 1 to 128 characters.");
		}
		for (int index = 0; index < result.length(); index++) {
			if (Character.isISOControl(result.charAt(index))) {
				throw new WorldBuilderDiscoveryException(
					"Create Level name cannot contain control characters.");
			}
		}
		return result;
	}

	private static String checkedRole(String value)
		throws WorldBuilderDiscoveryException {
		String result = value == null ? "" : value.trim();
		if (!ROLE.matcher(result).matches()) {
			throw new WorldBuilderDiscoveryException(
				"Create Level role must use lowercase letters, digits, dots, "
					+ "underscores, or hyphens.");
		}
		return result;
	}

	static String defaultName(int level) {
		if (level < 0) {
			return "Underground level " + Long.toString(-(long)level);
		}
		if (level > 0) return "Upper level " + level;
		return "Surface";
	}

	static String defaultRole(int level) {
		if (level < 0) {
			return "underground-level-" + Long.toString(-(long)level);
		}
		if (level > 0) return "upper-level-" + level;
		return "surface";
	}

	private static Path canonicalWorkspace(Path requested)
		throws IOException, WorldBuilderDiscoveryException {
		if (requested == null) {
			throw new WorldBuilderDiscoveryException(
				"A World Builder workspace is required.");
		}
		Path workspace = requested.toAbsolutePath().normalize();
		if (!Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(workspace)) {
			throw new WorldBuilderDiscoveryException(
				"Prepared World Builder workspace is missing or unsafe: " + workspace);
		}
		return workspace.toRealPath();
	}

	private static void requireContainedDirectory(
		Path root, Path path, String label)
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
				"Create Level path escapes the workspace: " + label);
		}
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
		Path packageRoot,
		Path stage,
		Path backup,
		boolean originalMoved,
		boolean draftMoved) {
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
				"Create Level failed and automatic workspace rollback also failed. "
					+ "The immutable source snapshot remains unchanged.",
				rollbackFailure);
		} finally {
			deleteTreeQuietly(stage);
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

	private static int number(Object value, String key) {
		return ((Long)object(value).get(key)).intValue();
	}

	private static String text(Object value, String key) {
		return (String)object(value).get(key);
	}

	static final class CreateLevelResult {
		final int level;
		final int anchorX;
		final int anchorY;
		final String name;
		final String role;
		final int minimumSectorX;
		final int maximumSectorX;
		final int minimumSectorY;
		final int maximumSectorY;
		final String manifestSha256;
		final String packageFingerprintSha256;
		final int terrainSectorCount;
		final int placementSetCount;

		CreateLevelResult(
			int level,
			int anchorX,
			int anchorY,
			String name,
			String role,
			int minimumSectorX,
			int maximumSectorX,
			int minimumSectorY,
			int maximumSectorY,
			String manifestSha256,
			String packageFingerprintSha256,
			int terrainSectorCount,
			int placementSetCount) {
			this.level = level;
			this.anchorX = anchorX;
			this.anchorY = anchorY;
			this.name = name;
			this.role = role;
			this.minimumSectorX = minimumSectorX;
			this.maximumSectorX = maximumSectorX;
			this.minimumSectorY = minimumSectorY;
			this.maximumSectorY = maximumSectorY;
			this.manifestSha256 = manifestSha256;
			this.packageFingerprintSha256 = packageFingerprintSha256;
			this.terrainSectorCount = terrainSectorCount;
			this.placementSetCount = placementSetCount;
		}

		String toJson() {
			Map<String,Object> result = new LinkedHashMap<String,Object>();
			result.put("schemaVersion", Long.valueOf(1));
			result.put("operation", "create-level");
			result.put("level", Long.valueOf(level));
			result.put("name", name);
			result.put("role", role);
			Map<String,Object> anchor = new LinkedHashMap<String,Object>();
			anchor.put("x", Long.valueOf(anchorX));
			anchor.put("y", Long.valueOf(anchorY));
			result.put("anchor", anchor);
			Map<String,Object> sectors = new LinkedHashMap<String,Object>();
			sectors.put("minimumX", Long.valueOf(minimumSectorX));
			sectors.put("maximumX", Long.valueOf(maximumSectorX));
			sectors.put("minimumY", Long.valueOf(minimumSectorY));
			sectors.put("maximumY", Long.valueOf(maximumSectorY));
			sectors.put("count", Long.valueOf(9));
			result.put("starterSectors", sectors);
			result.put("manifestSha256", manifestSha256);
			result.put(
				"packageFingerprintSha256", packageFingerprintSha256);
			result.put("terrainSectorCount", Long.valueOf(terrainSectorCount));
			result.put("placementSetCount", Long.valueOf(placementSetCount));
			result.put("sourceSnapshotUnchanged", Boolean.TRUE);
			result.put("exportEnabled", Boolean.FALSE);
			result.put("restartRequired", Boolean.TRUE);
			return WorldBuilderJsonDocuments.pretty(result);
		}
	}
}
