package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldMapSectorId;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Strict read-only native package source. It decodes detached terrain but owns
 * no World, Region, collision, placement, packet, or client authority.
 */
public final class NativeLayeredWorldPackage {
	public static final int SCHEMA_VERSION = 1;
	public static final String PACKAGE_TYPE = "layered-world";
	public static final String COORDINATE_MODEL = "signed-layered-v1";
	public static final String UNIFORM_ENCODING = "uniform-layered-sector-v1";

	private static final long MAX_JSON_BYTES = 16L * 1024L * 1024L;
	private static final int MAX_WORLD_SPACES = 128;
	private static final int MAX_LEVELS = 4096;
	private static final int MAX_TERRAIN_SECTORS = 65536;
	private static final Pattern ID =
		Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
	private static final Pattern VERSION =
		Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9._-]+)?");
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

	private final Path packageRoot;
	private final String packageId;
	private final String packageVersion;
	private final int presentationChunkSize;
	private final Map<String, String> worldSpaceKinds;
	private final Set<LevelKey> levels;
	private final Map<WorldMapSectorId, NativeLayeredTerrainSector> terrainSectors;
	private final String manifestSha256;

	private NativeLayeredWorldPackage(
		Path packageRoot,
		String packageId,
		String packageVersion,
		int presentationChunkSize,
		Map<String, String> worldSpaceKinds,
		Set<LevelKey> levels,
		Map<WorldMapSectorId, NativeLayeredTerrainSector> terrainSectors,
		String manifestSha256) {
		this.packageRoot = packageRoot;
		this.packageId = packageId;
		this.packageVersion = packageVersion;
		this.presentationChunkSize = presentationChunkSize;
		this.worldSpaceKinds = Collections.unmodifiableMap(
			new LinkedHashMap<String, String>(worldSpaceKinds));
		this.levels = Collections.unmodifiableSet(new HashSet<LevelKey>(levels));
		this.terrainSectors = Collections.unmodifiableMap(
			new LinkedHashMap<WorldMapSectorId, NativeLayeredTerrainSector>(
				terrainSectors));
		this.manifestSha256 = manifestSha256;
	}

	public static NativeLayeredWorldPackage load(Path requestedRoot) throws IOException {
		Path root = canonicalDirectory(requestedRoot);
		Path manifestPath = requiredFile(root, "manifest.json");
		try {
			JSONObject manifest = readObject(manifestPath);
			exactKeys(
				manifest,
				"package manifest",
				"schemaVersion",
				"packageType",
				"packageId",
				"packageVersion",
				"coordinateModel",
				"storage",
				"worldSpaces",
				"levels",
				"terrainSectors");
			requireInt(manifest, "schemaVersion", SCHEMA_VERSION);
			requireString(manifest, "packageType", PACKAGE_TYPE);
			requireString(manifest, "coordinateModel", COORDINATE_MODEL);
			String packageId = matchedString(manifest, "packageId", ID);
			String packageVersion = matchedString(manifest, "packageVersion", VERSION);

			JSONObject storage = object(manifest, "storage");
			exactKeys(storage, "storage", "sectorSize", "presentationChunkSize");
			requireInt(storage, "sectorSize", NativeLayeredTerrainSector.SIZE);
			int presentationChunkSize = signedInt(storage, "presentationChunkSize");
			if (presentationChunkSize <= 0
				|| presentationChunkSize > NativeLayeredTerrainSector.SIZE
				|| NativeLayeredTerrainSector.SIZE % presentationChunkSize != 0) {
				throw new IOException(
					"presentationChunkSize must be a positive divisor of 48");
			}

			Map<String, String> worldSpaces =
				readWorldSpaces(array(manifest, "worldSpaces"));
			Set<LevelKey> levels = readLevels(array(manifest, "levels"), worldSpaces);
			Map<WorldMapSectorId, NativeLayeredTerrainSector> sectors =
				readTerrainSectors(root, array(manifest, "terrainSectors"), levels);

			return new NativeLayeredWorldPackage(
				root,
				packageId,
				packageVersion,
				presentationChunkSize,
				worldSpaces,
				levels,
				sectors,
				sha256(manifestPath));
		} catch (JSONException failure) {
			throw new IOException(
				"Native layered package JSON is invalid: " + failure.getMessage(), failure);
		} catch (IllegalArgumentException failure) {
			throw new IOException(
				"Native layered package value is invalid: " + failure.getMessage(), failure);
		}
	}

	private static Map<String, String> readWorldSpaces(JSONArray values)
		throws IOException {
		if (values.length() < 1 || values.length() > MAX_WORLD_SPACES) {
			throw new IOException(
				"worldSpaces count must be 1.." + MAX_WORLD_SPACES);
		}
		Map<String, String> result = new LinkedHashMap<String, String>();
		for (int index = 0; index < values.length(); index++) {
			JSONObject value = object(values, index, "worldSpaces");
			exactKeys(value, "worldSpaces[" + index + "]", "id", "kind");
			String id = matchedString(value, "id", ID);
			String kind = string(value, "kind");
			if (!"static".equals(kind) && !"instance-template".equals(kind)) {
				throw new IOException(
					"worldSpaces[" + index + "].kind is unsupported: " + kind);
			}
			if (result.put(id, kind) != null) {
				throw new IOException("Duplicate world-space ID: " + id);
			}
			new WorldSpaceId(id);
		}
		if (!result.containsKey(WorldSpaceId.GLOBAL.getValue())) {
			throw new IOException("A layered package must declare global world space");
		}
		return result;
	}

	private static Set<LevelKey> readLevels(
		JSONArray values, Map<String, String> worldSpaces) throws IOException {
		if (values.length() < 1 || values.length() > MAX_LEVELS) {
			throw new IOException("levels count must be 1.." + MAX_LEVELS);
		}
		Set<LevelKey> result = new HashSet<LevelKey>();
		for (int index = 0; index < values.length(); index++) {
			JSONObject value = object(values, index, "levels");
			exactKeys(
				value,
				"levels[" + index + "]",
				"worldSpace",
				"level",
				"name",
				"role");
			String worldSpace = matchedString(value, "worldSpace", ID);
			if (!worldSpaces.containsKey(worldSpace)) {
				throw new IOException(
					"levels[" + index + "] references unknown world space: " + worldSpace);
			}
			String name = string(value, "name");
			if (name.isEmpty() || name.length() > 128) {
				throw new IOException("levels[" + index + "].name length must be 1..128");
			}
			matchedString(value, "role", ID);
			LevelKey key =
				new LevelKey(new WorldSpaceId(worldSpace), signedInt(value, "level"));
			if (!result.add(key)) {
				throw new IOException(
					"Duplicate level declaration: " + worldSpace + " " + key.level);
			}
		}
		return result;
	}

	private static Map<WorldMapSectorId, NativeLayeredTerrainSector>
		readTerrainSectors(Path root, JSONArray values, Set<LevelKey> levels)
			throws IOException {
		if (values.length() < 1 || values.length() > MAX_TERRAIN_SECTORS) {
			throw new IOException(
				"terrainSectors count must be 1.." + MAX_TERRAIN_SECTORS);
		}
		Map<WorldMapSectorId, NativeLayeredTerrainSector> result =
			new LinkedHashMap<WorldMapSectorId, NativeLayeredTerrainSector>();
		Set<String> paths = new HashSet<String>();
		for (int index = 0; index < values.length(); index++) {
			JSONObject value = object(values, index, "terrainSectors");
			exactKeys(
				value,
				"terrainSectors[" + index + "]",
				"worldSpace",
				"level",
				"sectorX",
				"sectorY",
				"encoding",
				"path",
				"sha256");
			WorldSpaceId worldSpace =
				new WorldSpaceId(matchedString(value, "worldSpace", ID));
			int level = signedInt(value, "level");
			if (!levels.contains(new LevelKey(worldSpace, level))) {
				throw new IOException(
					"terrainSectors[" + index + "] references an undeclared level: "
						+ worldSpace + " " + level);
			}
			WorldMapSectorId identity = new WorldMapSectorId(
				worldSpace,
				level,
				signedInt(value, "sectorX"),
				signedInt(value, "sectorY"));
			if (result.containsKey(identity)) {
				throw new IOException("Duplicate terrain sector identity: " + identity);
			}
			String encoding = matchedString(value, "encoding", ID);
			if (!UNIFORM_ENCODING.equals(encoding)) {
				throw new IOException(
					"Terrain payload encoding is unsupported by this loader: " + encoding);
			}
			String relativePath = safeRelativePath(string(value, "path"));
			if (!paths.add(relativePath)) {
				throw new IOException("Terrain payload path is reused: " + relativePath);
			}
			String expectedSha256 = matchedString(value, "sha256", SHA256);
			Path payloadPath = requiredFile(root, relativePath);
			String actualSha256 = sha256(payloadPath);
			if (!expectedSha256.equals(actualSha256)) {
				throw new IOException(
					"Terrain payload hash differs from manifest: " + relativePath);
			}
			NativeLayeredTerrainTile tile = readUniformTile(payloadPath);
			result.put(
				identity,
				NativeLayeredTerrainSector.uniform(
					identity,
					tile,
					encoding,
					relativePath,
					expectedSha256));
		}
		return result;
	}

	private static NativeLayeredTerrainTile readUniformTile(Path path)
		throws IOException {
		JSONObject document = readObject(path);
		exactKeys(document, "uniform sector", "schemaVersion", "encoding", "size", "tile");
		requireInt(document, "schemaVersion", 1);
		requireString(document, "encoding", UNIFORM_ENCODING);
		requireInt(document, "size", NativeLayeredTerrainSector.SIZE);
		JSONObject tile = object(document, "tile");
		exactKeys(
			tile,
			"uniform sector tile",
			"elevation",
			"texture",
			"overlay",
			"roof",
			"verticalWall",
			"horizontalWall",
			"diagonalWall");
		long rawDiagonal = unsignedInt(tile, "diagonalWall");
		return new NativeLayeredTerrainTile(
			unsignedByte(tile, "elevation"),
			unsignedByte(tile, "texture"),
			unsignedByte(tile, "overlay"),
			unsignedByte(tile, "roof"),
			unsignedByte(tile, "verticalWall"),
			unsignedByte(tile, "horizontalWall"),
			(int) rawDiagonal);
	}

	public Optional<NativeLayeredTerrainSector> findSector(WorldMapSectorId identity) {
		return Optional.ofNullable(terrainSectors.get(identity));
	}

	public Optional<NativeLayeredTerrainTile> findTile(WorldLocation location) {
		NativeLayeredTerrainSector sector =
			terrainSectors.get(WorldMapSectorId.from(location));
		if (sector == null) {
			return Optional.empty();
		}
		WorldCoordinate coordinate = location.getCoordinate();
		return Optional.of(sector.getTile(coordinate.getLocalX(), coordinate.getLocalY()));
	}

	public boolean declaresLevel(WorldSpaceId worldSpace, int level) {
		return levels.contains(new LevelKey(worldSpace, level));
	}

	public Path getPackageRoot() {
		return packageRoot;
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

	public int getWorldSpaceCount() {
		return worldSpaceKinds.size();
	}

	public int getLevelCount() {
		return levels.size();
	}

	public int getTerrainSectorCount() {
		return terrainSectors.size();
	}

	public String getManifestSha256() {
		return manifestSha256;
	}

	public Map<WorldMapSectorId, NativeLayeredTerrainSector> getTerrainSectors() {
		return terrainSectors;
	}

	private static JSONObject readObject(Path path) throws IOException {
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(path)) {
			throw new IOException("Required JSON is missing or unsafe: " + path);
		}
		long size = Files.size(path);
		if (size < 2L || size > MAX_JSON_BYTES) {
			throw new IOException("JSON size is outside the accepted range: " + path);
		}
		try {
			String value = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(path)))
				.toString();
			return new JSONObject(value);
		} catch (CharacterCodingException failure) {
			throw new IOException("JSON is not valid UTF-8: " + path, failure);
		}
	}

	private static Path canonicalDirectory(Path requestedRoot) throws IOException {
		if (requestedRoot == null) {
			throw new IOException("A native layered package directory is required");
		}
		Path normalized = requestedRoot.toAbsolutePath().normalize();
		if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(normalized)) {
			throw new IOException(
				"Native layered package root is missing or unsafe: " + normalized);
		}
		return normalized.toRealPath();
	}

	private static Path requiredFile(Path root, String relative) throws IOException {
		Path candidate = root.resolve(relative).normalize();
		if (!candidate.startsWith(root)
			|| !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(candidate)) {
			throw new IOException(
				"Native layered package file is missing or unsafe: " + relative);
		}
		Path real = candidate.toRealPath();
		if (!real.startsWith(root)) {
			throw new IOException(
				"Native layered package file escapes its root: " + relative);
		}
		return real;
	}

	private static String safeRelativePath(String value) throws IOException {
		if (value.isEmpty() || value.indexOf('\\') >= 0) {
			throw new IOException(
				"Native layered package paths must use non-empty forward-slash paths");
		}
		Path path = Paths.get(value);
		if (path.isAbsolute()
			|| !path.normalize().equals(path)
			|| ".".equals(path.toString())) {
			throw new IOException(
				"Native layered package path must be normalized and relative: " + value);
		}
		return value;
	}

	private static JSONObject object(JSONObject parent, String key) throws IOException {
		Object value = parent.opt(key);
		if (!(value instanceof JSONObject)) {
			throw new IOException(key + " must be an object");
		}
		return (JSONObject) value;
	}

	private static JSONObject object(JSONArray parent, int index, String label)
		throws IOException {
		Object value = parent.opt(index);
		if (!(value instanceof JSONObject)) {
			throw new IOException(label + "[" + index + "] must be an object");
		}
		return (JSONObject) value;
	}

	private static JSONArray array(JSONObject parent, String key) throws IOException {
		Object value = parent.opt(key);
		if (!(value instanceof JSONArray)) {
			throw new IOException(key + " must be an array");
		}
		return (JSONArray) value;
	}

	private static void exactKeys(JSONObject value, String label, String... keys)
		throws IOException {
		Set<String> expected = new HashSet<String>(Arrays.asList(keys));
		if (!value.keySet().equals(expected)) {
			throw new IOException(
				label + " fields differ from the native package v1 contract");
		}
	}

	private static void requireInt(JSONObject value, String key, int expected)
		throws IOException {
		int actual = signedInt(value, key);
		if (actual != expected) {
			throw new IOException(
				key + " must be " + expected + " but was " + actual);
		}
	}

	private static int signedInt(JSONObject value, String key) throws IOException {
		Object raw = value.opt(key);
		if (!(raw instanceof Integer) && !(raw instanceof Long)) {
			throw new IOException(key + " must be a signed 32-bit integer");
		}
		long result = ((Number) raw).longValue();
		if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
			throw new IOException(key + " must be a signed 32-bit integer");
		}
		return (int) result;
	}

	private static int unsignedByte(JSONObject value, String key) throws IOException {
		int result = signedInt(value, key);
		if (result < 0 || result > 255) {
			throw new IOException(key + " must be an unsigned byte");
		}
		return result;
	}

	private static long unsignedInt(JSONObject value, String key) throws IOException {
		Object raw = value.opt(key);
		if (!(raw instanceof Integer) && !(raw instanceof Long)) {
			throw new IOException(key + " must be an unsigned 32-bit integer");
		}
		long result = ((Number) raw).longValue();
		if (result < 0L || result > 0xffffffffL) {
			throw new IOException(key + " must be an unsigned 32-bit integer");
		}
		return result;
	}

	private static void requireString(
		JSONObject value, String key, String expected) throws IOException {
		String actual = string(value, key);
		if (!expected.equals(actual)) {
			throw new IOException(key + " must be " + expected + " but was " + actual);
		}
	}

	private static String string(JSONObject value, String key) throws IOException {
		Object raw = value.opt(key);
		if (!(raw instanceof String)) {
			throw new IOException(key + " must be a string");
		}
		return (String) raw;
	}

	private static String matchedString(
		JSONObject value, String key, Pattern pattern) throws IOException {
		String result = string(value, key);
		if (!pattern.matcher(result).matches()) {
			throw new IOException(
				key + " must match " + pattern.pattern() + ": " + result);
		}
		return result;
	}

	private static String sha256(Path path) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException("SHA-256 is unavailable", failure);
		}
		byte[] buffer = new byte[64 * 1024];
		try (java.io.InputStream input = Files.newInputStream(path)) {
			int count;
			while ((count = input.read(buffer)) != -1) {
				digest.update(buffer, 0, count);
			}
		}
		StringBuilder result = new StringBuilder(64);
		for (byte part : digest.digest()) {
			result.append(String.format("%02x", part & 0xff));
		}
		return result.toString();
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
