package com.openrsc.layeredmaps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict, read-only descriptor for a native signed layered-world package. */
public final class LayeredWorldPackageManifest {
	public static final int SCHEMA_VERSION = 1;
	public static final String PACKAGE_TYPE = "layered-world";
	public static final String COORDINATE_MODEL = "signed-layered-v1";
	public static final int STORAGE_SECTOR_SIZE = 48;

	private static final Pattern ID =
		Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
	private static final Pattern VERSION =
		Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9._-]+)?");
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

	private final String packageId;
	private final String packageVersion;
	private final int presentationChunkSize;
	private final List<WorldSpace> worldSpaces;
	private final List<Level> levels;
	private final List<TerrainSector> terrainSectors;
	private final String packageFingerprint;

	private LayeredWorldPackageManifest(
		String packageId,
		String packageVersion,
		int presentationChunkSize,
		List<WorldSpace> worldSpaces,
		List<Level> levels,
		List<TerrainSector> terrainSectors,
		String packageFingerprint) {
		this.packageId = packageId;
		this.packageVersion = packageVersion;
		this.presentationChunkSize = presentationChunkSize;
		this.worldSpaces = Collections.unmodifiableList(
			new ArrayList<WorldSpace>(worldSpaces));
		this.levels = Collections.unmodifiableList(new ArrayList<Level>(levels));
		this.terrainSectors = Collections.unmodifiableList(
			new ArrayList<TerrainSector>(terrainSectors));
		this.packageFingerprint = packageFingerprint;
	}

	public static LayeredWorldPackageManifest load(Path requestedPackageRoot)
		throws IOException, PreflightException {
		Path packageRoot = canonicalDirectory(requestedPackageRoot);
		Path manifestPath = requiredFile(packageRoot, "manifest.json");
		Map<String, Object> document = JsonDocuments.readObject(manifestPath);
		exactKeys(document, "package manifest",
			"schemaVersion", "packageType", "packageId", "packageVersion",
			"coordinateModel", "storage", "worldSpaces", "levels", "terrainSectors");
		requireInt(document, "schemaVersion", SCHEMA_VERSION);
		requireString(document, "packageType", PACKAGE_TYPE);
		requireString(document, "coordinateModel", COORDINATE_MODEL);
		String packageId = matchedString(document, "packageId", ID);
		String packageVersion = matchedString(document, "packageVersion", VERSION);

		Map<String, Object> storage = object(document, "storage");
		exactKeys(storage, "storage", "sectorSize", "presentationChunkSize");
		requireInt(storage, "sectorSize", STORAGE_SECTOR_SIZE);
		int presentationChunkSize = integer(storage, "presentationChunkSize");
		if (presentationChunkSize <= 0
			|| presentationChunkSize > STORAGE_SECTOR_SIZE
			|| STORAGE_SECTOR_SIZE % presentationChunkSize != 0) {
			throw new PreflightException(
				"presentationChunkSize must be a positive divisor of 48.");
		}

		List<WorldSpace> worldSpaces = readWorldSpaces(array(document, "worldSpaces"));
		Set<String> worldSpaceIds = new HashSet<String>();
		for (WorldSpace worldSpace : worldSpaces) {
			if (!worldSpaceIds.add(worldSpace.id.getValue())) {
				throw new PreflightException(
					"Duplicate world-space ID: " + worldSpace.id);
			}
		}
		if (!worldSpaceIds.contains(WorldSpaceId.GLOBAL.getValue())) {
			throw new PreflightException(
				"A layered world package must declare the global world space.");
		}

		List<Level> levels = readLevels(array(document, "levels"), worldSpaceIds);
		Set<LevelKey> levelKeys = new HashSet<LevelKey>();
		for (Level level : levels) {
			if (!levelKeys.add(new LevelKey(level.worldSpace, level.level))) {
				throw new PreflightException(
					"Duplicate level declaration: " + level.worldSpace + " " + level.level);
			}
		}

		List<TerrainSector> terrainSectors = readTerrainSectors(
			packageRoot, array(document, "terrainSectors"), levelKeys);
		if (terrainSectors.isEmpty()) {
			throw new PreflightException(
				"A layered world package must declare at least one terrain sector.");
		}

		String packageFingerprint = Hashes.sha256(JsonDocuments.canonical(document));
		return new LayeredWorldPackageManifest(
			packageId,
			packageVersion,
			presentationChunkSize,
			worldSpaces,
			levels,
			terrainSectors,
			packageFingerprint);
	}

	private static List<WorldSpace> readWorldSpaces(List<Object> values)
		throws PreflightException {
		if (values.isEmpty()) {
			throw new PreflightException("worldSpaces must not be empty.");
		}
		List<WorldSpace> result = new ArrayList<WorldSpace>();
		for (int index = 0; index < values.size(); index++) {
			Map<String, Object> value = object(values.get(index), "worldSpaces[" + index + "]");
			exactKeys(value, "worldSpaces[" + index + "]", "id", "kind");
			String id = matchedString(value, "id", ID);
			String kind = string(value, "kind");
			if (!"static".equals(kind) && !"instance-template".equals(kind)) {
				throw new PreflightException(
					"worldSpaces[" + index + "].kind is unsupported: " + kind);
			}
			try {
				result.add(new WorldSpace(new WorldSpaceId(id), kind));
			} catch (IllegalArgumentException failure) {
				throw new PreflightException(
					"worldSpaces[" + index + "].id is invalid: " + id, failure);
			}
		}
		return result;
	}

	private static List<Level> readLevels(
		List<Object> values, Set<String> worldSpaceIds) throws PreflightException {
		if (values.isEmpty()) {
			throw new PreflightException("levels must not be empty.");
		}
		List<Level> result = new ArrayList<Level>();
		for (int index = 0; index < values.size(); index++) {
			Map<String, Object> value = object(values.get(index), "levels[" + index + "]");
			exactKeys(value, "levels[" + index + "]",
				"worldSpace", "level", "name", "role");
			String worldSpace = matchedString(value, "worldSpace", ID);
			if (!worldSpaceIds.contains(worldSpace)) {
				throw new PreflightException(
					"levels[" + index + "] references unknown world space: " + worldSpace);
			}
			int level = integer(value, "level");
			String name = boundedString(value, "name", 1, 128);
			String role = matchedString(value, "role", ID);
			result.add(new Level(new WorldSpaceId(worldSpace), level, name, role));
		}
		return result;
	}

	private static List<TerrainSector> readTerrainSectors(
		Path packageRoot,
		List<Object> values,
		Set<LevelKey> levelKeys) throws IOException, PreflightException {
		List<TerrainSector> result = new ArrayList<TerrainSector>();
		Set<WorldMapSectorId> identities = new HashSet<WorldMapSectorId>();
		Set<String> paths = new HashSet<String>();
		for (int index = 0; index < values.size(); index++) {
			Map<String, Object> value =
				object(values.get(index), "terrainSectors[" + index + "]");
			exactKeys(value, "terrainSectors[" + index + "]",
				"worldSpace", "level", "sectorX", "sectorY",
				"encoding", "path", "sha256");
			String worldSpace = matchedString(value, "worldSpace", ID);
			int level = integer(value, "level");
			LevelKey levelKey = new LevelKey(new WorldSpaceId(worldSpace), level);
			if (!levelKeys.contains(levelKey)) {
				throw new PreflightException(
					"terrainSectors[" + index + "] references an undeclared level: "
						+ worldSpace + " " + level);
			}
			WorldMapSectorId identity = new WorldMapSectorId(
				levelKey.worldSpace,
				level,
				integer(value, "sectorX"),
				integer(value, "sectorY"));
			if (!identities.add(identity)) {
				throw new PreflightException(
					"Duplicate terrain sector identity: " + identity);
			}
			String encoding = matchedString(value, "encoding", ID);
			String relativePath = safeRelativePath(string(value, "path"));
			if (!paths.add(relativePath)) {
				throw new PreflightException(
					"Terrain payload path is reused: " + relativePath);
			}
			String expectedHash = matchedString(value, "sha256", SHA256);
			Path payload = requiredFile(packageRoot, relativePath);
			String actualHash = Hashes.sha256(payload);
			if (!expectedHash.equals(actualHash)) {
				throw new PreflightException(
					"Terrain payload hash differs from manifest: " + relativePath);
			}
			if (UniformLayeredTerrainSector.ENCODING.equals(encoding)) {
				UniformLayeredTerrainSector.load(payload);
			} else {
				throw new PreflightException(
					"Terrain payload encoding is unsupported by this loader: " + encoding);
			}
			result.add(new TerrainSector(
				identity, encoding, relativePath, expectedHash, Files.size(payload)));
		}
		return result;
	}

	public String toValidationJson() {
		Map<String, Object> document = new LinkedHashMap<String, Object>();
		document.put("schemaVersion", Long.valueOf(1));
		document.put("reportType", "layered-world-package-validation");
		document.put("packageId", packageId);
		document.put("packageVersion", packageVersion);
		document.put("coordinateModel", COORDINATE_MODEL);
		document.put("storageSectorSize", Long.valueOf(STORAGE_SECTOR_SIZE));
		document.put("presentationChunkSize", Long.valueOf(presentationChunkSize));
		document.put("worldSpaceCount", Long.valueOf(worldSpaces.size()));
		document.put("levelCount", Long.valueOf(levels.size()));
		document.put("terrainSectorCount", Long.valueOf(terrainSectors.size()));
		document.put("packageFingerprintSha256", packageFingerprint);
		List<Object> levelDocuments = new ArrayList<Object>();
		for (Level level : levels) {
			Map<String, Object> value = new LinkedHashMap<String, Object>();
			value.put("worldSpace", level.worldSpace.getValue());
			value.put("level", Long.valueOf(level.level));
			value.put("name", level.name);
			value.put("role", level.role);
			levelDocuments.add(value);
		}
		document.put("levels", levelDocuments);
		return JsonDocuments.pretty(document);
	}

	public String getPackageId() {
		return packageId;
	}

	public String getPackageVersion() {
		return packageVersion;
	}

	public int getPresentationChunkSize() {
		return presentationChunkSize;
	}

	public List<WorldSpace> getWorldSpaces() {
		return worldSpaces;
	}

	public List<Level> getLevels() {
		return levels;
	}

	public List<TerrainSector> getTerrainSectors() {
		return terrainSectors;
	}

	public String getPackageFingerprint() {
		return packageFingerprint;
	}

	private static Path canonicalDirectory(Path requested)
		throws IOException, PreflightException {
		if (requested == null) {
			throw new PreflightException("A package directory is required.");
		}
		Path normalized = requested.toAbsolutePath().normalize();
		if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(normalized)) {
			throw new PreflightException(
				"Layered package root is missing or unsafe: " + normalized);
		}
		return normalized.toRealPath();
	}

	private static Path requiredFile(Path root, String relative)
		throws IOException, PreflightException {
		Path candidate = root.resolve(relative).normalize();
		if (!candidate.startsWith(root)
			|| !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(candidate)) {
			throw new PreflightException(
				"Layered package file is missing or unsafe: " + relative);
		}
		Path real = candidate.toRealPath();
		if (!real.startsWith(root)) {
			throw new PreflightException(
				"Layered package file escapes its root: " + relative);
		}
		return real;
	}

	private static String safeRelativePath(String value) throws PreflightException {
		if (value.isEmpty() || value.indexOf('\\') >= 0) {
			throw new PreflightException(
				"Layered package paths must be non-empty forward-slash paths.");
		}
		Path path = Paths.get(value);
		if (path.isAbsolute() || !path.normalize().equals(path)
			|| ".".equals(path.toString())) {
			throw new PreflightException(
				"Layered package path must be normalized and relative: " + value);
		}
		return value;
	}

	private static Map<String, Object> object(
		Map<String, Object> parent, String key) throws PreflightException {
		Object value = parent.get(key);
		return object(value, key);
	}

	private static Map<String, Object> object(Object value, String label)
		throws PreflightException {
		if (!(value instanceof Map)) {
			throw new PreflightException(label + " must be an object.");
		}
		return JsonDocuments.object(value);
	}

	private static List<Object> array(
		Map<String, Object> parent, String key) throws PreflightException {
		Object value = parent.get(key);
		if (!(value instanceof List)) {
			throw new PreflightException(key + " must be an array.");
		}
		return JsonDocuments.array(value);
	}

	private static void exactKeys(
		Map<String, Object> value, String label, String... keys)
		throws PreflightException {
		Set<String> expected = new HashSet<String>(Arrays.asList(keys));
		if (!value.keySet().equals(expected)) {
			throw new PreflightException(
				label + " fields differ from the v1 contract. Expected " + expected
					+ " but found " + value.keySet() + ".");
		}
	}

	private static void requireInt(
		Map<String, Object> value, String key, int expected) throws PreflightException {
		int actual = integer(value, key);
		if (actual != expected) {
			throw new PreflightException(
				key + " must be " + expected + " but was " + actual + ".");
		}
	}

	private static int integer(Map<String, Object> value, String key)
		throws PreflightException {
		Object raw = value.get(key);
		if (!(raw instanceof Long)
			|| (Long) raw < Integer.MIN_VALUE || (Long) raw > Integer.MAX_VALUE) {
			throw new PreflightException(key + " must be a signed 32-bit integer.");
		}
		return ((Long) raw).intValue();
	}

	private static void requireString(
		Map<String, Object> value, String key, String expected) throws PreflightException {
		String actual = string(value, key);
		if (!expected.equals(actual)) {
			throw new PreflightException(
				key + " must be " + expected + " but was " + actual + ".");
		}
	}

	private static String string(Map<String, Object> value, String key)
		throws PreflightException {
		Object raw = value.get(key);
		if (!(raw instanceof String)) {
			throw new PreflightException(key + " must be a string.");
		}
		return (String) raw;
	}

	private static String boundedString(
		Map<String, Object> value, String key, int minimum, int maximum)
		throws PreflightException {
		String result = string(value, key);
		if (result.length() < minimum || result.length() > maximum) {
			throw new PreflightException(
				key + " length must be " + minimum + ".." + maximum + ".");
		}
		return result;
	}

	private static String matchedString(
		Map<String, Object> value, String key, Pattern pattern) throws PreflightException {
		String result = string(value, key);
		if (!pattern.matcher(result).matches()) {
			throw new PreflightException(
				key + " must match " + pattern.pattern() + ": " + result);
		}
		return result;
	}

	public static final class WorldSpace {
		private final WorldSpaceId id;
		private final String kind;

		WorldSpace(WorldSpaceId id, String kind) {
			this.id = id;
			this.kind = kind;
		}

		public WorldSpaceId getId() {
			return id;
		}

		public String getKind() {
			return kind;
		}
	}

	public static final class Level {
		private final WorldSpaceId worldSpace;
		private final int level;
		private final String name;
		private final String role;

		Level(WorldSpaceId worldSpace, int level, String name, String role) {
			this.worldSpace = worldSpace;
			this.level = level;
			this.name = name;
			this.role = role;
		}

		public WorldSpaceId getWorldSpace() {
			return worldSpace;
		}

		public int getLevel() {
			return level;
		}

		public String getName() {
			return name;
		}

		public String getRole() {
			return role;
		}
	}

	public static final class TerrainSector {
		private final WorldMapSectorId identity;
		private final String encoding;
		private final String path;
		private final String sha256;
		private final long size;

		TerrainSector(
			WorldMapSectorId identity,
			String encoding,
			String path,
			String sha256,
			long size) {
			this.identity = identity;
			this.encoding = encoding;
			this.path = path;
			this.sha256 = sha256;
			this.size = size;
		}

		public WorldMapSectorId getIdentity() {
			return identity;
		}

		public String getEncoding() {
			return encoding;
		}

		public String getPath() {
			return path;
		}

		public String getSha256() {
			return sha256;
		}

		public long getSize() {
			return size;
		}
	}

	private static final class LevelKey {
		final WorldSpaceId worldSpace;
		final int level;

		LevelKey(WorldSpaceId worldSpace, int level) {
			this.worldSpace = worldSpace;
			this.level = level;
		}

		@Override
		public boolean equals(Object other) {
			return this == other
				|| other instanceof LevelKey
					&& level == ((LevelKey) other).level
					&& worldSpace.equals(((LevelKey) other).worldSpace);
		}

		@Override
		public int hashCode() {
			return 31 * worldSpace.hashCode() + level;
		}
	}
}
