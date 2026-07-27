package com.openrsc.worldbuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict read-only discovery record for the first layered World Builder
 * adapter.
 */
final class WorldBuilderLayeredPackage {
	static final String ADAPTER_ID = "spoiled-milk-layered-package-v1";
	static final String PROFILE_ID = "spoiled-milk-replacement";
	static final String PACKAGE_ID = "rsc-remastered.spoiled-milk-layered-world";
	static final String PACKAGE_VERSION = "0.2.0";
	static final String MANIFEST_SHA256 =
		"fab8d7d1a51e948a7d8b18769eb0b3e9f5abf9e30538abfedba4d90374b1447b";

	final Path root;
	final String packageId;
	final String packageVersion;
	final String manifestSha256;
	final String packageFingerprintSha256;
	final String worldSpace;
	final List<Integer> levels;
	final int terrainSectorCount;
	final int placementSetCount;
	final List<FileRecord> files;

	private WorldBuilderLayeredPackage(
		Path root,
		String packageId,
		String packageVersion,
		String manifestSha256,
		String packageFingerprintSha256,
		String worldSpace,
		List<Integer> levels,
		int terrainSectorCount,
		int placementSetCount,
		List<FileRecord> files) {
		this.root = root;
		this.packageId = packageId;
		this.packageVersion = packageVersion;
		this.manifestSha256 = manifestSha256;
		this.packageFingerprintSha256 = packageFingerprintSha256;
		this.worldSpace = worldSpace;
		this.levels = Collections.unmodifiableList(new ArrayList<Integer>(levels));
		this.terrainSectorCount = terrainSectorCount;
		this.placementSetCount = placementSetCount;
		this.files = Collections.unmodifiableList(new ArrayList<FileRecord>(files));
	}

	static WorldBuilderLayeredPackage discover(Path requested, String requestedProfile)
		throws IOException, WorldBuilderDiscoveryException {
		if (!PROFILE_ID.equals(requestedProfile)) {
			throw new WorldBuilderDiscoveryException(
				"The first layered Builder adapter requires profile " + PROFILE_ID + ".");
		}
		Path root = canonicalDirectory(requested);
		Path manifest = requiredFile(root, "manifest.json");
		String manifestSha256 = WorldBuilderHashes.sha256(manifest);
		if (!MANIFEST_SHA256.equals(manifestSha256)) {
			throw new WorldBuilderDiscoveryException(
				"Layered package manifest does not match the accepted Spoiled Milk 0.2.0 package.");
		}
		Map<String,Object> document = WorldBuilderJsonDocuments.readObject(manifest);
		exactKeys(document, "coordinateModel", "levels", "packageId", "packageType",
			"packageVersion", "placementSets", "schemaVersion", "storage",
			"terrainSectors", "worldSpaces");
		if (integer(document, "schemaVersion") != 1
			|| !"layered-world".equals(string(document, "packageType"))
			|| !"signed-layered-v1".equals(string(document, "coordinateModel"))
			|| !PACKAGE_ID.equals(string(document, "packageId"))
			|| !PACKAGE_VERSION.equals(string(document, "packageVersion"))) {
			throw new WorldBuilderDiscoveryException(
				"Layered package identity or coordinate model is unsupported.");
		}

		Map<String,Object> storage = object(document.get("storage"), "storage");
		exactKeys(storage, "presentationChunkSize", "sectorSize");
		if (integer(storage, "sectorSize") != 48
			|| integer(storage, "presentationChunkSize") != 24) {
			throw new WorldBuilderDiscoveryException(
				"Layered Builder requires 48-tile storage and 24-tile presentation chunks.");
		}

		List<Object> worldSpaces = array(document, "worldSpaces");
		if (worldSpaces.size() != 1) {
			throw new WorldBuilderDiscoveryException(
				"The accepted Spoiled Milk package must declare exactly one world space.");
		}
		Map<String,Object> worldSpaceRecord = object(worldSpaces.get(0), "worldSpaces[0]");
		exactKeys(worldSpaceRecord, "id", "kind");
		String worldSpace = string(worldSpaceRecord, "id");
		if (!"global".equals(worldSpace)
			|| !"static".equals(string(worldSpaceRecord, "kind"))) {
			throw new WorldBuilderDiscoveryException(
				"The accepted Spoiled Milk package must declare global world space.");
		}

		List<Integer> levels = new ArrayList<Integer>();
		Set<Integer> uniqueLevels = new HashSet<Integer>();
		for (Object value : array(document, "levels")) {
			Map<String,Object> level = object(value, "level");
			exactKeys(level, "level", "name", "role", "worldSpace");
			int number = integer(level, "level");
			if (!worldSpace.equals(string(level, "worldSpace"))
				|| !uniqueLevels.add(Integer.valueOf(number))) {
				throw new WorldBuilderDiscoveryException(
					"Layered package contains an invalid or duplicate level.");
			}
			string(level, "name");
			string(level, "role");
			levels.add(Integer.valueOf(number));
		}
		Collections.sort(levels);
		if (!levels.equals(Arrays.asList(
				Integer.valueOf(-1), Integer.valueOf(0),
				Integer.valueOf(1), Integer.valueOf(2)))) {
			throw new WorldBuilderDiscoveryException(
				"The accepted Spoiled Milk 0.2.0 package levels are incomplete.");
		}

		Set<String> referenced = new LinkedHashSet<String>();
		List<Object> terrain = array(document, "terrainSectors");
		if (terrain.isEmpty()) {
			throw new WorldBuilderDiscoveryException("Layered package has no terrain sectors.");
		}
		for (Object value : terrain) {
			Map<String,Object> sector = object(value, "terrain sector");
			exactKeys(sector, "encoding", "level", "path", "sectorX", "sectorY",
				"sha256", "worldSpace");
			if (!worldSpace.equals(string(sector, "worldSpace"))
				|| !uniqueLevels.contains(Integer.valueOf(integer(sector, "level")))
				|| !"raw-layered-sector-v1".equals(string(sector, "encoding"))) {
				throw new WorldBuilderDiscoveryException(
					"Layered terrain declaration is unsupported.");
			}
			integer(sector, "sectorX");
			integer(sector, "sectorY");
			registerReference(root, referenced, string(sector, "path"),
				hash(sector, "sha256"));
		}

		List<Object> placements = array(document, "placementSets");
		if (placements.size() != levels.size()) {
			throw new WorldBuilderDiscoveryException(
				"Layered package requires one placement set per declared level.");
		}
		for (Object value : placements) {
			Map<String,Object> placement = object(value, "placement set");
			exactKeys(placement, "encoding", "id", "level", "path", "sha256",
				"worldSpace");
			if (!worldSpace.equals(string(placement, "worldSpace"))
				|| !uniqueLevels.contains(Integer.valueOf(integer(placement, "level")))
				|| !"layered-world-placements-v3".equals(
					string(placement, "encoding"))
				|| string(placement, "id").isEmpty()) {
				throw new WorldBuilderDiscoveryException(
					"Layered placement declaration is unsupported.");
			}
			registerReference(root, referenced, string(placement, "path"),
				hash(placement, "sha256"));
		}

		List<FileRecord> files = inventory(root);
		Set<String> actual = new LinkedHashSet<String>();
		for (FileRecord file : files) {
			actual.add(file.relativePath);
		}
		Set<String> expected = new LinkedHashSet<String>();
		expected.add("manifest.json");
		expected.addAll(referenced);
		if (!actual.equals(expected)) {
			throw new WorldBuilderDiscoveryException(
				"Layered package contains missing or untracked files.");
		}
		MessageDigest fingerprint = WorldBuilderHashes.newDigest();
		for (FileRecord file : files) {
			WorldBuilderHashes.updateText(fingerprint, file.relativePath);
			WorldBuilderHashes.updateText(fingerprint, file.sha256);
			WorldBuilderHashes.updateText(fingerprint, Long.toString(file.size));
		}
		return new WorldBuilderLayeredPackage(
			root, PACKAGE_ID, PACKAGE_VERSION, manifestSha256,
			WorldBuilderHashes.hex(fingerprint.digest()), worldSpace, levels,
			terrain.size(), placements.size(), files);
	}

	String toMetadataJson() {
		StringBuilder json = new StringBuilder(768);
		json.append("{\n")
			.append("  \"schemaVersion\": 1,\n")
			.append("  \"reviewMode\": \"read-only\",\n")
			.append("  \"adapter\": \"").append(ADAPTER_ID).append("\",\n")
			.append("  \"runtimeProfile\": \"").append(PROFILE_ID).append("\",\n")
			.append("  \"packageId\": \"").append(packageId).append("\",\n")
			.append("  \"packageVersion\": \"").append(packageVersion).append("\",\n")
			.append("  \"manifestSha256\": \"").append(manifestSha256).append("\",\n")
			.append("  \"packageFingerprintSha256\": \"")
			.append(packageFingerprintSha256).append("\",\n")
			.append("  \"worldSpace\": \"").append(worldSpace).append("\",\n")
			.append("  \"levels\": [");
		for (int index = 0; index < levels.size(); index++) {
			if (index > 0) json.append(", ");
			json.append(levels.get(index).intValue());
		}
		json.append("],\n")
			.append("  \"terrainSectorCount\": ").append(terrainSectorCount).append(",\n")
			.append("  \"placementSetCount\": ").append(placementSetCount).append("\n")
			.append("}\n");
		return json.toString();
	}

	private static void registerReference(
		Path root, Set<String> referenced, String relative, String expectedSha256)
		throws IOException, WorldBuilderDiscoveryException {
		String normalized = normalizedRelative(relative);
		if (!referenced.add(normalized)) {
			throw new WorldBuilderDiscoveryException(
				"Layered package path is referenced more than once: " + normalized);
		}
		Path file = requiredFile(root, normalized);
		if (!expectedSha256.equals(WorldBuilderHashes.sha256(file))) {
			throw new WorldBuilderDiscoveryException(
				"Layered package payload hash changed: " + normalized);
		}
	}

	private static List<FileRecord> inventory(Path root)
		throws IOException, WorldBuilderDiscoveryException {
		List<FileRecord> files = new ArrayList<FileRecord>();
		try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
			java.util.Iterator<Path> iterator = paths.iterator();
			while (iterator.hasNext()) {
				Path path = iterator.next();
				if (path.equals(root)) continue;
				if (Files.isSymbolicLink(path)) {
					throw new WorldBuilderDiscoveryException(
						"Layered package contains a symbolic link.");
				}
				if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
					String relative = root.relativize(path).toString().replace('\\', '/');
					files.add(new FileRecord(relative, Files.size(path),
						WorldBuilderHashes.sha256(path)));
				} else if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
					throw new WorldBuilderDiscoveryException(
						"Layered package contains an unsupported entry.");
				}
			}
		}
		Collections.sort(files);
		return files;
	}

	private static Path canonicalDirectory(Path requested)
		throws IOException, WorldBuilderDiscoveryException {
		if (requested == null) {
			throw new WorldBuilderDiscoveryException(
				"A layered package directory is required.");
		}
		Path normalized = requested.toAbsolutePath().normalize();
		if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(normalized)) {
			throw new WorldBuilderDiscoveryException(
				"Layered package root is missing or unsafe: " + normalized);
		}
		return normalized.toRealPath();
	}

	private static Path requiredFile(Path root, String relative)
		throws IOException, WorldBuilderDiscoveryException {
		Path candidate = root.resolve(normalizedRelative(relative)).normalize();
		if (!candidate.startsWith(root)
			|| !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(candidate)) {
			throw new WorldBuilderDiscoveryException(
				"Layered package file is missing or unsafe: " + relative);
		}
		Path real = candidate.toRealPath();
		if (!real.startsWith(root)) {
			throw new WorldBuilderDiscoveryException(
				"Layered package file escapes its root: " + relative);
		}
		return real;
	}

	private static String normalizedRelative(String value)
		throws WorldBuilderDiscoveryException {
		if (value == null || value.isEmpty() || value.indexOf('\\') >= 0) {
			throw new WorldBuilderDiscoveryException("Layered package path is invalid.");
		}
		Path relative = java.nio.file.Paths.get(value).normalize();
		String normalized = relative.toString().replace('\\', '/');
		if (relative.isAbsolute() || relative.startsWith("..")
			|| !normalized.equals(value)) {
			throw new WorldBuilderDiscoveryException(
				"Layered package path is not normalized: " + value);
		}
		return normalized;
	}

	private static Map<String,Object> object(Object value, String label)
		throws WorldBuilderDiscoveryException {
		if (!(value instanceof Map)) {
			throw new WorldBuilderDiscoveryException(
				"Layered package field is not an object: " + label);
		}
		@SuppressWarnings("unchecked") Map<String,Object> result =
			(Map<String,Object>)value;
		return result;
	}

	private static List<Object> array(Map<String,Object> object, String key)
		throws WorldBuilderDiscoveryException {
		Object value = object.get(key);
		if (!(value instanceof List)) {
			throw new WorldBuilderDiscoveryException(
				"Layered package field is not an array: " + key);
		}
		@SuppressWarnings("unchecked") List<Object> result = (List<Object>)value;
		return result;
	}

	private static String string(Map<String,Object> object, String key)
		throws WorldBuilderDiscoveryException {
		Object value = object.get(key);
		if (!(value instanceof String)) {
			throw new WorldBuilderDiscoveryException(
				"Layered package field is not a string: " + key);
		}
		return (String)value;
	}

	private static String hash(Map<String,Object> object, String key)
		throws WorldBuilderDiscoveryException {
		String value = string(object, key);
		if (!value.matches("[0-9a-f]{64}")) {
			throw new WorldBuilderDiscoveryException(
				"Layered package hash is invalid: " + key);
		}
		return value;
	}

	private static int integer(Map<String,Object> object, String key)
		throws WorldBuilderDiscoveryException {
		Object value = object.get(key);
		if (!(value instanceof Long)
			|| ((Long)value).longValue() < Integer.MIN_VALUE
			|| ((Long)value).longValue() > Integer.MAX_VALUE) {
			throw new WorldBuilderDiscoveryException(
				"Layered package field is not a 32-bit integer: " + key);
		}
		return ((Long)value).intValue();
	}

	private static void exactKeys(Map<String,Object> object, String... keys)
		throws WorldBuilderDiscoveryException {
		Set<String> expected = new HashSet<String>(Arrays.asList(keys));
		if (!object.keySet().equals(expected)) {
			throw new WorldBuilderDiscoveryException(
				"Layered package contains missing or unexpected fields.");
		}
	}

	static final class FileRecord implements Comparable<FileRecord> {
		final String relativePath;
		final long size;
		final String sha256;

		FileRecord(String relativePath, long size, String sha256) {
			this.relativePath = relativePath;
			this.size = size;
			this.sha256 = sha256;
		}

		@Override
		public int compareTo(FileRecord other) {
			return relativePath.compareTo(other.relativePath);
		}
	}
}
