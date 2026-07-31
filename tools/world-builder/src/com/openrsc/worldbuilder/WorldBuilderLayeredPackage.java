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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strict read-only discovery record for the first layered World Builder
 * adapter.
 */
final class WorldBuilderLayeredPackage {
	static final String ADAPTER_ID = "spoiled-milk-layered-package-v1";
	static final String PROFILE_ID = "spoiled-milk-replacement";
	static final String PACKAGE_ID = "rsc-remastered.spoiled-milk-layered-world";
	static final String PACKAGE_VERSION = "0.5.0";
	static final String MANIFEST_SHA256 =
		"f914d93e7abcf40dc281c06df5010269c7a9ce4fe4a16aaa6ae11f0d90a14306";
	static final String BUILDER_DRAFT_PROFILE_ID = "spoiled-milk-builder-draft";
	private static final int RAW_SECTOR_BYTES = 48 * 48 * 10;
	private static final int MAX_LEVELS = 4096;
	private static final int MAX_TERRAIN_SECTORS = 65536;
	private static final Pattern ID =
		Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");

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
	final List<LevelRecord> levelRecords;
	final List<TerrainRecord> terrainRecords;
	final List<PlacementRecord> placementRecords;

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
		List<FileRecord> files,
		List<LevelRecord> levelRecords,
		List<TerrainRecord> terrainRecords,
		List<PlacementRecord> placementRecords) {
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
		this.levelRecords = Collections.unmodifiableList(
			new ArrayList<LevelRecord>(levelRecords));
		this.terrainRecords = Collections.unmodifiableList(
			new ArrayList<TerrainRecord>(terrainRecords));
		this.placementRecords = Collections.unmodifiableList(
			new ArrayList<PlacementRecord>(placementRecords));
	}

	static WorldBuilderLayeredPackage discover(Path requested, String requestedProfile)
		throws IOException, WorldBuilderDiscoveryException {
		if (!PROFILE_ID.equals(requestedProfile)) {
			throw new WorldBuilderDiscoveryException(
				"The first layered Builder adapter requires profile " + PROFILE_ID + ".");
		}
		WorldBuilderLayeredPackage source = discoverDraft(requested);
		if (!MANIFEST_SHA256.equals(source.manifestSha256)
			|| !source.levels.equals(Arrays.asList(
				Integer.valueOf(-2), Integer.valueOf(-1),
				Integer.valueOf(0),
				Integer.valueOf(1), Integer.valueOf(2),
				Integer.valueOf(10)))
			|| source.terrainSectorCount != 1782
			|| source.placementSetCount != 6) {
			throw new WorldBuilderDiscoveryException(
				"Layered package does not match the accepted Spoiled Milk 0.5.0 source.");
		}
		return source;
	}

	static WorldBuilderLayeredPackage discoverDraft(Path requested)
		throws IOException, WorldBuilderDiscoveryException {
		Path root = canonicalDirectory(requested);
		Path manifest = requiredFile(root, "manifest.json");
		String manifestSha256 = WorldBuilderHashes.sha256(manifest);
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
		List<LevelRecord> levelRecords = new ArrayList<LevelRecord>();
		for (Object value : array(document, "levels")) {
			Map<String,Object> level = object(value, "level");
			exactKeys(level, "level", "name", "role", "worldSpace");
			int number = integer(level, "level");
			String name = string(level, "name");
			String role = identifier(level, "role");
			if (!worldSpace.equals(string(level, "worldSpace"))
				|| !uniqueLevels.add(Integer.valueOf(number))
				|| name.isEmpty() || name.length() > 128) {
				throw new WorldBuilderDiscoveryException(
					"Layered package contains an invalid or duplicate level.");
			}
			levels.add(Integer.valueOf(number));
			levelRecords.add(new LevelRecord(number, name, role));
		}
		if (levels.isEmpty() || levels.size() > MAX_LEVELS) {
			throw new WorldBuilderDiscoveryException(
				"Layered package level count is outside 1.." + MAX_LEVELS + ".");
		}
		Collections.sort(levels);

		Set<String> referenced = new LinkedHashSet<String>();
		List<Object> terrain = array(document, "terrainSectors");
		if (terrain.isEmpty() || terrain.size() > MAX_TERRAIN_SECTORS) {
			throw new WorldBuilderDiscoveryException(
				"Layered package terrain count is outside 1.."
					+ MAX_TERRAIN_SECTORS + ".");
		}
		Set<String> terrainIdentities = new HashSet<String>();
		List<TerrainRecord> terrainRecords = new ArrayList<TerrainRecord>();
		for (Object value : terrain) {
			Map<String,Object> sector = object(value, "terrain sector");
			exactKeys(sector, "encoding", "level", "path", "sectorX", "sectorY",
				"sha256", "worldSpace");
			int level = integer(sector, "level");
			int sectorX = integer(sector, "sectorX");
			int sectorY = integer(sector, "sectorY");
			String identity = level + ":" + sectorX + ":" + sectorY;
			if (!worldSpace.equals(string(sector, "worldSpace"))
				|| !uniqueLevels.contains(Integer.valueOf(level))
				|| !terrainIdentities.add(identity)
				|| !"raw-layered-sector-v1".equals(string(sector, "encoding"))) {
				throw new WorldBuilderDiscoveryException(
					"Layered terrain declaration is unsupported.");
			}
			String path = normalizedRelative(string(sector, "path"));
			String sha256 = hash(sector, "sha256");
			registerReference(root, referenced, path, sha256);
			if (Files.size(requiredFile(root, path)) != RAW_SECTOR_BYTES) {
				throw new WorldBuilderDiscoveryException(
					"Raw layered terrain sector has an invalid size: " + path);
			}
			terrainRecords.add(new TerrainRecord(
				level, sectorX, sectorY, path, sha256));
		}

		List<Object> placements = array(document, "placementSets");
		if (placements.size() != levels.size()) {
			throw new WorldBuilderDiscoveryException(
				"Layered package requires one placement set per declared level.");
		}
		Set<String> placementIds = new HashSet<String>();
		Set<Integer> placementLevels = new HashSet<Integer>();
		List<PlacementRecord> placementRecords =
			new ArrayList<PlacementRecord>();
		for (Object value : placements) {
			Map<String,Object> placement = object(value, "placement set");
			exactKeys(placement, "encoding", "id", "level", "path", "sha256",
				"worldSpace");
			int level = integer(placement, "level");
			String id = identifier(placement, "id");
			if (!worldSpace.equals(string(placement, "worldSpace"))
				|| !uniqueLevels.contains(Integer.valueOf(level))
				|| !placementLevels.add(Integer.valueOf(level))
				|| !placementIds.add(id)
				|| !"layered-world-placements-v3".equals(
					string(placement, "encoding"))) {
				throw new WorldBuilderDiscoveryException(
					"Layered placement declaration is unsupported.");
			}
			String path = normalizedRelative(string(placement, "path"));
			String sha256 = hash(placement, "sha256");
			registerReference(root, referenced, path, sha256);
			PlacementCounts placementCounts = validatePlacementPayload(
				requiredFile(root, path), worldSpace, level);
			placementRecords.add(new PlacementRecord(
				id, level, path, sha256, placementCounts));
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
			terrain.size(), placements.size(), files, levelRecords,
			terrainRecords, placementRecords);
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

	void requireFirstDraftDescendant(WorldBuilderLayeredPackage source)
		throws WorldBuilderDiscoveryException {
		requireTerrainDraftDescendant(source);
	}

	void requireTerrainDraftDescendant(WorldBuilderLayeredPackage source)
		throws WorldBuilderDiscoveryException {
		if (source == null
			|| !PACKAGE_ID.equals(packageId)
			|| !PACKAGE_VERSION.equals(packageVersion)
			|| !worldSpace.equals(source.worldSpace)
			|| !MANIFEST_SHA256.equals(source.manifestSha256)) {
			throw new WorldBuilderDiscoveryException(
				"Layered draft does not descend from the accepted source package.");
		}
		Map<Integer,LevelRecord> currentLevels =
			new LinkedHashMap<Integer,LevelRecord>();
		for (LevelRecord record : levelRecords) {
			currentLevels.put(Integer.valueOf(record.level), record);
		}
		for (LevelRecord accepted : source.levelRecords) {
			LevelRecord current = currentLevels.get(Integer.valueOf(accepted.level));
			if (current == null || !current.same(accepted)) {
				throw new WorldBuilderDiscoveryException(
					"Layered draft changed accepted level metadata: "
						+ accepted.level);
			}
		}
		Map<String,TerrainRecord> currentTerrain =
			new LinkedHashMap<String,TerrainRecord>();
		for (TerrainRecord record : terrainRecords) {
			currentTerrain.put(record.key(), record);
		}
		for (TerrainRecord accepted : source.terrainRecords) {
			TerrainRecord current = currentTerrain.get(accepted.key());
			if (current == null || !current.same(accepted)) {
				throw new WorldBuilderDiscoveryException(
					"Layered draft changed accepted terrain: " + accepted.key());
			}
		}
		Map<Integer,PlacementRecord> currentPlacements =
			new LinkedHashMap<Integer,PlacementRecord>();
		for (PlacementRecord record : placementRecords) {
			currentPlacements.put(Integer.valueOf(record.level), record);
		}
		for (PlacementRecord accepted : source.placementRecords) {
			PlacementRecord current =
				currentPlacements.get(Integer.valueOf(accepted.level));
			if (current == null || !current.same(accepted)) {
				throw new WorldBuilderDiscoveryException(
					"Layered draft changed accepted placements: "
						+ accepted.level);
			}
		}

		Set<Integer> sourceLevels = new HashSet<Integer>(source.levels);
		for (Integer levelValue : levels) {
			if (sourceLevels.contains(levelValue)) continue;
			int level = levelValue.intValue();
			PlacementRecord placement =
				currentPlacements.get(Integer.valueOf(level));
			if (placement == null
				|| placement.boundaryCount != 0
				|| !placement.path.equals(
					"placements/global/l" + signedToken(level) + ".json")) {
				throw new WorldBuilderDiscoveryException(
					"Builder-created level " + level
						+ " may contain NPC, scenery, and ground-item "
						+ "placements only in its v3 placement set.");
			}
			List<TerrainRecord> starter = new ArrayList<TerrainRecord>();
			for (TerrainRecord record : terrainRecords) {
				if (record.level == level) starter.add(record);
			}
			requireBuilderTerrain(level, starter);
		}
	}

	private static void requireBuilderTerrain(
		int level, List<TerrainRecord> records)
		throws WorldBuilderDiscoveryException {
		if (records.size() < 9) {
			throw new WorldBuilderDiscoveryException(
				"Builder-created level " + level
					+ " must retain at least its 3x3 starter window.");
		}
		Set<String> coordinates = new HashSet<String>();
		for (TerrainRecord record : records) {
			coordinates.add(record.sectorX + ":" + record.sectorY);
			String expected = "terrain/global/l" + signedToken(level)
				+ "/x" + signedToken(record.sectorX)
				+ "-y" + signedToken(record.sectorY) + ".raw";
			if (!expected.equals(record.path)) {
				throw new WorldBuilderDiscoveryException(
					"Builder-created terrain path is not deterministic: "
						+ record.path);
			}
		}
		if (!containsThreeByThree(coordinates)) {
			throw new WorldBuilderDiscoveryException(
				"Builder-created level " + level
					+ " no longer contains a complete 3x3 starter window.");
		}
		Set<String> remaining = new HashSet<String>(coordinates);
		while (!remaining.isEmpty()) {
			Set<String> component = new HashSet<String>();
			List<String> pending = new ArrayList<String>();
			pending.add(remaining.iterator().next());
			while (!pending.isEmpty()) {
				String current = pending.remove(pending.size() - 1);
				if (!component.add(current)) continue;
				remaining.remove(current);
				int separator = current.indexOf(':');
				int x = Integer.parseInt(current.substring(0, separator));
				int y = Integer.parseInt(current.substring(separator + 1));
				for (int[] direction : new int[][] {
					{ 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }
				}) {
					String neighbor = ((long)x + direction[0]) + ":"
						+ ((long)y + direction[1]);
					if (coordinates.contains(neighbor)
						&& !component.contains(neighbor)) {
						pending.add(neighbor);
					}
				}
			}
			if (!containsThreeByThree(component)) {
				throw new WorldBuilderDiscoveryException(
					"Builder-created level " + level
						+ " has a detached component without a complete "
						+ "3x3 work-area allocation.");
			}
		}
	}

	private static boolean containsThreeByThree(Set<String> coordinates) {
		for (String candidate : coordinates) {
			int separator = candidate.indexOf(':');
			int minimumX = Integer.parseInt(candidate.substring(0, separator));
			int minimumY = Integer.parseInt(candidate.substring(separator + 1));
			boolean complete = true;
			for (int offsetX = 0; offsetX < 3 && complete; offsetX++) {
				for (int offsetY = 0; offsetY < 3; offsetY++) {
					if (!coordinates.contains(
						((long)minimumX + offsetX) + ":"
							+ ((long)minimumY + offsetY))) {
						complete = false;
						break;
					}
				}
			}
			if (complete) return true;
		}
		return false;
	}

	static String signedToken(int value) {
		return value < 0
			? "m" + Long.toString(-(long)value)
			: "p" + Integer.toString(value);
	}

	private static PlacementCounts validatePlacementPayload(
		Path path, String worldSpace, int level)
		throws IOException, WorldBuilderDiscoveryException {
		Map<String,Object> payload = WorldBuilderJsonDocuments.readObject(path);
		exactKeys(payload, "boundaries", "encoding", "groundItems", "level",
			"npcs", "scenery", "schemaVersion", "worldSpace");
		if (integer(payload, "schemaVersion") != 3
			|| !"layered-world-placements-v3".equals(
				string(payload, "encoding"))
			|| !worldSpace.equals(string(payload, "worldSpace"))
			|| level != integer(payload, "level")) {
			throw new WorldBuilderDiscoveryException(
				"Layered placement payload identity is invalid: " + path);
		}
		int npcCount = array(payload, "npcs").size();
		int groundItemCount = array(payload, "groundItems").size();
		int sceneryCount = array(payload, "scenery").size();
		int boundaryCount = array(payload, "boundaries").size();
		long count = (long)npcCount + groundItemCount
			+ sceneryCount + boundaryCount;
		if (count > 65536L) {
			throw new WorldBuilderDiscoveryException(
				"Layered placement payload exceeds 65536 entries: " + path);
		}
		return new PlacementCounts(
			npcCount, groundItemCount, sceneryCount, boundaryCount);
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

	private static String identifier(Map<String,Object> object, String key)
		throws WorldBuilderDiscoveryException {
		String value = string(object, key);
		if (!ID.matcher(value).matches()) {
			throw new WorldBuilderDiscoveryException(
				"Layered package identifier is invalid: " + key);
		}
		return value;
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

	static final class LevelRecord {
		final int level;
		final String name;
		final String role;

		LevelRecord(int level, String name, String role) {
			this.level = level;
			this.name = name;
			this.role = role;
		}

		boolean same(LevelRecord other) {
			return other != null && level == other.level
				&& name.equals(other.name) && role.equals(other.role);
		}
	}

	static final class TerrainRecord {
		final int level;
		final int sectorX;
		final int sectorY;
		final String path;
		final String sha256;

		TerrainRecord(
			int level, int sectorX, int sectorY, String path, String sha256) {
			this.level = level;
			this.sectorX = sectorX;
			this.sectorY = sectorY;
			this.path = path;
			this.sha256 = sha256;
		}

		String key() {
			return level + ":" + sectorX + ":" + sectorY;
		}

		boolean same(TerrainRecord other) {
			return other != null && level == other.level
				&& sectorX == other.sectorX && sectorY == other.sectorY
				&& path.equals(other.path) && sha256.equals(other.sha256);
		}
	}

	static final class PlacementRecord {
		final String id;
		final int level;
		final String path;
		final String sha256;
		final int placementCount;
		final int npcCount;
		final int groundItemCount;
		final int sceneryCount;
		final int boundaryCount;

		PlacementRecord(
			String id, int level, String path, String sha256,
			PlacementCounts counts) {
			this.id = id;
			this.level = level;
			this.path = path;
			this.sha256 = sha256;
			this.npcCount = counts.npcCount;
			this.groundItemCount = counts.groundItemCount;
			this.sceneryCount = counts.sceneryCount;
			this.boundaryCount = counts.boundaryCount;
			this.placementCount = counts.total();
		}

		boolean same(PlacementRecord other) {
			return other != null && id.equals(other.id)
				&& level == other.level && path.equals(other.path)
				&& sha256.equals(other.sha256)
				&& placementCount == other.placementCount;
		}
	}

	private static final class PlacementCounts {
		final int npcCount;
		final int groundItemCount;
		final int sceneryCount;
		final int boundaryCount;

		PlacementCounts(
			int npcCount,
			int groundItemCount,
			int sceneryCount,
			int boundaryCount) {
			this.npcCount = npcCount;
			this.groundItemCount = groundItemCount;
			this.sceneryCount = sceneryCount;
			this.boundaryCount = boundaryCount;
		}

		int total() {
			return npcCount + groundItemCount + sceneryCount + boundaryCount;
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
