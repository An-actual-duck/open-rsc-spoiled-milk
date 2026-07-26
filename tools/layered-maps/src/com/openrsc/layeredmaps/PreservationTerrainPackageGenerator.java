package com.openrsc.layeredmaps;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Generates a deterministic terrain-only native review package from the
 * accepted Preservation revision-64 baseline.
 */
final class PreservationTerrainPackageGenerator {
	static final String PACKAGE_ID =
		"rsc-remastered.preservation-r64-parity-review";
	static final String PACKAGE_VERSION = "0.2.0";
	static final String REPORT_TYPE =
		"preservation-layered-parity-generation";
	static final int REPORT_SCHEMA_VERSION = 1;

	private static final String FROZEN_BASELINE =
		"tools/layered-maps/baselines/"
			+ "rsc-remastered-preservation-r64-v1.json";
	private static final int TILE_BYTES = 10;
	private static final int SECTOR_BYTES = 48 * 48 * TILE_BYTES;

	Result generate(Path requestedRoot, Path requestedWorkspace)
		throws IOException, PreflightException {
		Path root = requestedRoot.toAbsolutePath().normalize().toRealPath();
		PreservationBaselineInventory.Baseline baseline =
			new PreservationBaselineInventory().inspect(root);
		requireFrozenBaseline(root, baseline);

		Path workspace = requestedWorkspace.toAbsolutePath().normalize();
		Files.createDirectories(workspace);
		if (!Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(workspace)) {
			throw new PreflightException(
				"Preservation package workspace is missing or unsafe: "
					+ workspace);
		}
		Path packageRoot = workspace.resolve("package");
		Path stagingRoot = workspace.resolve("package.building");
		if (Files.exists(packageRoot, LinkOption.NOFOLLOW_LINKS)
			|| Files.exists(stagingRoot, LinkOption.NOFOLLOW_LINKS)) {
			throw new PreflightException(
				"Preservation package output already exists; use a fresh "
					+ "isolated workspace: " + workspace);
		}
		Files.createDirectory(stagingRoot);

		PreservationBaselineInventory.FileRecord terrainSource =
			baselineFile(baseline, "server-authentic-terrain");
		Path archivePath = root.resolve(terrainSource.path).normalize();
		List<SectorRecord> sectors =
			writeTerrain(archivePath, stagingRoot);
		if (terrainSource.archiveEntryCount == null
			|| sectors.size() != terrainSource.archiveEntryCount.longValue()) {
			throw new PreflightException(
				"Generated terrain count differs from the accepted baseline.");
		}
		PlacementConversion placements =
			writePlacements(root, baseline, stagingRoot);

		Map<String, Object> manifest =
			manifest(sectors, placements.sets);
		Path manifestPath = stagingRoot.resolve("manifest.json");
		writeNew(
			manifestPath,
			JsonDocuments.pretty(manifest).getBytes(StandardCharsets.UTF_8));
		LayeredWorldPackageManifest loaded =
			LayeredWorldPackageManifest.load(stagingRoot);
		Map<String, Integer> loadedPlacementCounts =
			loadedPlacementCounts(loaded);
		if (loaded.getTerrainSectors().size() != sectors.size()
			|| loaded.getPlacementSets().size() != placements.sets.size()
			|| loadedPlacementCounts.get("npcs").intValue()
				!= placements.count("npcs")
			|| loadedPlacementCounts.get("groundItems").intValue()
				!= placements.count("groundItems")
			|| loadedPlacementCounts.get("scenery").intValue()
				!= placements.count("scenery")
			|| loadedPlacementCounts.get("boundaries").intValue()
				!= placements.count("boundaries")) {
			throw new PreflightException(
				"Generated parity review package validation disagreed with "
					+ "its source inventory.");
		}
		String manifestSha256 = Hashes.sha256(manifestPath);
		String validationJson = loaded.toValidationJson();

		try {
			Files.move(
				stagingRoot,
				packageRoot,
				StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(stagingRoot, packageRoot);
		}

		Map<Integer, Integer> levelCounts = new LinkedHashMap<Integer, Integer>();
		long payloadBytes = 0L;
		for (SectorRecord sector : sectors) {
			Integer prior = levelCounts.get(sector.identity.getLevel());
			levelCounts.put(
				sector.identity.getLevel(),
				Integer.valueOf(prior == null ? 1 : prior.intValue() + 1));
			payloadBytes = Math.addExact(payloadBytes, sector.payloadBytes);
		}
		long sourcePlacements = 0L;
		for (PreservationBaselineInventory.FileRecord file : baseline.files) {
			if (file.recordCount != null) {
				sourcePlacements =
					Math.addExact(sourcePlacements, file.recordCount.longValue());
			}
		}
		if (sourcePlacements
			!= (long) placements.convertedCount()
				+ placements.unresolved.size()) {
			throw new PreflightException(
				"Placement conversion accounting differs from the frozen baseline.");
		}
		return new Result(
			packageRoot,
			baseline.sourceSetFingerprint,
			terrainSource.path,
			terrainSource.sha256,
			sectors.size(),
			payloadBytes,
			levelCounts,
			sourcePlacements,
			placements.convertedByFamily,
			placements.sets.size(),
			placements.unresolved,
			manifestSha256,
			loaded.getPackageFingerprint(),
			validationJson);
	}

	private static Map<String, Integer> loadedPlacementCounts(
		LayeredWorldPackageManifest loaded) {
		Map<String, Integer> result =
			new LinkedHashMap<String, Integer>();
		result.put("npcs", Integer.valueOf(0));
		result.put("groundItems", Integer.valueOf(0));
		result.put("scenery", Integer.valueOf(0));
		result.put("boundaries", Integer.valueOf(0));
		for (LayeredWorldPackageManifest.PlacementSet set
			: loaded.getPlacementSets()) {
			result.put(
				"npcs",
				Integer.valueOf(Math.addExact(
					result.get("npcs").intValue(),
					set.getNpcCount())));
			result.put(
				"groundItems",
				Integer.valueOf(Math.addExact(
					result.get("groundItems").intValue(),
					set.getGroundItemCount())));
			result.put(
				"scenery",
				Integer.valueOf(Math.addExact(
					result.get("scenery").intValue(),
					set.getSceneryCount())));
			result.put(
				"boundaries",
				Integer.valueOf(Math.addExact(
					result.get("boundaries").intValue(),
					set.getBoundaryCount())));
		}
		return result;
	}

	private static void requireFrozenBaseline(
		Path root,
		PreservationBaselineInventory.Baseline baseline)
		throws IOException, PreflightException {
		Path frozen = root.resolve(FROZEN_BASELINE).normalize();
		if (!frozen.startsWith(root)
			|| !Files.isRegularFile(frozen, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(frozen)) {
			throw new PreflightException(
				"Frozen Preservation baseline manifest is missing or unsafe.");
		}
		byte[] expected = Files.readAllBytes(frozen);
		byte[] actual = baseline.toJson().getBytes(StandardCharsets.UTF_8);
		if (!Arrays.equals(expected, actual)) {
			throw new PreflightException(
				"Preservation sources no longer reproduce the accepted frozen "
					+ "baseline; regenerate and review the baseline before conversion.");
		}
	}

	private static PreservationBaselineInventory.FileRecord baselineFile(
		PreservationBaselineInventory.Baseline baseline,
		String role) {
		for (PreservationBaselineInventory.FileRecord file : baseline.files) {
			if (role.equals(file.role)) {
				return file;
			}
		}
		throw new IllegalStateException(
			"Accepted baseline is missing required role: " + role);
	}

	private static List<SectorRecord> writeTerrain(
		Path archivePath,
		Path stagingRoot) throws IOException, PreflightException {
		List<String> names = new ArrayList<String>();
		Set<String> uniqueNames = new HashSet<String>();
		try (ZipFile archive = new ZipFile(archivePath.toFile())) {
			Enumeration<? extends ZipEntry> entries = archive.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory() || !uniqueNames.add(entry.getName())) {
					throw new PreflightException(
						"Preservation terrain contains a duplicate or directory entry: "
							+ entry.getName());
				}
				try {
					LegacyTerrainSectorCodec.decode(entry.getName());
				} catch (IllegalArgumentException failure) {
					throw new PreflightException(
						"Preservation terrain entry is unsupported: "
							+ entry.getName(),
						failure);
				}
				names.add(entry.getName());
			}
			Collections.sort(names, new Comparator<String>() {
				@Override
				public int compare(String left, String right) {
					return compareIdentity(
						LegacyTerrainSectorCodec.decode(left),
						LegacyTerrainSectorCodec.decode(right));
				}
			});

			List<SectorRecord> result = new ArrayList<SectorRecord>();
			Set<WorldMapSectorId> identities = new HashSet<WorldMapSectorId>();
			for (String name : names) {
				WorldMapSectorId identity =
					LegacyTerrainSectorCodec.decode(name);
				if (!name.equals(LegacyTerrainSectorCodec.encode(identity))
					|| !identities.add(identity)) {
					throw new PreflightException(
						"Preservation terrain identity did not round-trip: " + name);
				}
				ZipEntry entry = archive.getEntry(name);
				if (entry == null) {
					throw new PreflightException(
						"Preservation terrain entry disappeared during conversion: "
							+ name);
				}
				byte[] legacy = readExact(
					archive.getInputStream(entry),
					SECTOR_BYTES,
					name);
				byte[] nativePayload = toNativeRaw(legacy);
				if (!Arrays.equals(legacy, toLegacyRaw(nativePayload))) {
					throw new PreflightException(
						"Native terrain transform failed exact reverse verification: "
							+ name);
				}
				String relativePath = terrainPath(identity);
				Path destination = stagingRoot.resolve(relativePath).normalize();
				if (!destination.startsWith(stagingRoot)) {
					throw new PreflightException(
						"Generated terrain path escaped its package root.");
				}
				Files.createDirectories(destination.getParent());
				writeNew(destination, nativePayload);
				result.add(new SectorRecord(
					identity,
					relativePath,
					Hashes.sha256(nativePayload),
					nativePayload.length));
			}
			return result;
		}
	}

	private static PlacementConversion writePlacements(
		Path root,
		PreservationBaselineInventory.Baseline baseline,
		Path stagingRoot) throws IOException, PreflightException {
		Map<Integer, PlacementBucket> buckets =
			new LinkedHashMap<Integer, PlacementBucket>();
		for (int level : new int[] {0, 1, 2, -1}) {
			buckets.put(
				Integer.valueOf(level),
				new PlacementBucket(level));
		}
		Map<String, Integer> converted =
			new LinkedHashMap<String, Integer>();
		converted.put("npcs", Integer.valueOf(0));
		converted.put("groundItems", Integer.valueOf(0));
		converted.put("scenery", Integer.valueOf(0));
		converted.put("boundaries", Integer.valueOf(0));
		List<UnresolvedPlacement> unresolved =
			new ArrayList<UnresolvedPlacement>();

		convertBoundaries(
			root,
			baselineFile(baseline, "base-boundaries"),
			buckets,
			converted);
		convertScenery(
			root,
			baselineFile(baseline, "base-scenery"),
			buckets,
			converted);
		convertNpcs(
			root,
			baselineFile(baseline, "base-npcs"),
			buckets,
			converted,
			unresolved);
		convertGroundItems(
			root,
			baselineFile(baseline, "base-ground-items"),
			buckets,
			converted);

		List<PlacementSetRecord> sets =
			new ArrayList<PlacementSetRecord>();
		for (PlacementBucket bucket : buckets.values()) {
			Map<String, Object> document = map();
			document.put("schemaVersion", Long.valueOf(3));
			document.put("encoding", LayeredEntityPlacements.ENCODING_V3);
			document.put("worldSpace", "global");
			document.put("level", Long.valueOf(bucket.level));
			document.put("npcs", bucket.npcs);
			document.put("groundItems", bucket.groundItems);
			document.put("scenery", bucket.scenery);
			document.put("boundaries", bucket.boundaries);
			String relativePath =
				"placements/global/l" + signedToken(bucket.level) + ".json";
			Path destination = stagingRoot.resolve(relativePath).normalize();
			if (!destination.startsWith(stagingRoot)) {
				throw new PreflightException(
					"Generated placement path escaped its package root.");
			}
			Files.createDirectories(destination.getParent());
			writeNew(
				destination,
				JsonDocuments.pretty(document).getBytes(StandardCharsets.UTF_8));
			sets.add(new PlacementSetRecord(
				"preservation-r64-l" + signedToken(bucket.level),
				bucket.level,
				relativePath,
				Hashes.sha256(destination),
				bucket.count()));
		}
		return new PlacementConversion(
			sets,
			converted,
			unresolved);
	}

	private static void convertBoundaries(
		Path root,
		PreservationBaselineInventory.FileRecord source,
		Map<Integer, PlacementBucket> buckets,
		Map<String, Integer> converted)
		throws IOException, PreflightException {
		List<Object> values = sourceRecords(root, source, "boundaries");
		for (int index = 0; index < values.size(); index++) {
			Map<String, Object> value =
				sourceObject(values.get(index), "boundaries[" + index + "]");
			requireSourceKeys(
				value,
				"boundaries[" + index + "]",
				"id",
				"pos",
				"direction");
			WorldCoordinate position = sourcePosition(
				value.get("pos"),
				"boundaries[" + index + "].pos");
			Map<String, Object> record = map();
			record.put(
				"placementId",
				placementId("boundary", index));
			record.put("boundaryId", sourceNonNegativeInt(value, "id"));
			record.put("position", position(position));
			record.put(
				"direction",
				sourceDirection(value, "direction"));
			bucket(buckets, position.getLevel()).boundaries.add(record);
			increment(converted, "boundaries");
		}
	}

	private static void convertScenery(
		Path root,
		PreservationBaselineInventory.FileRecord source,
		Map<Integer, PlacementBucket> buckets,
		Map<String, Integer> converted)
		throws IOException, PreflightException {
		List<Object> values = sourceRecords(root, source, "sceneries");
		for (int index = 0; index < values.size(); index++) {
			Map<String, Object> value =
				sourceObject(values.get(index), "sceneries[" + index + "]");
			requireSourceKeys(
				value,
				"sceneries[" + index + "]",
				"id",
				"pos",
				"direction");
			WorldCoordinate position = sourcePosition(
				value.get("pos"),
				"sceneries[" + index + "].pos");
			Map<String, Object> record = map();
			record.put(
				"placementId",
				placementId("scenery", index));
			record.put("sceneryId", sourceNonNegativeInt(value, "id"));
			record.put("position", position(position));
			record.put(
				"direction",
				sourceDirection(value, "direction"));
			bucket(buckets, position.getLevel()).scenery.add(record);
			increment(converted, "scenery");
		}
	}

	private static void convertNpcs(
		Path root,
		PreservationBaselineInventory.FileRecord source,
		Map<Integer, PlacementBucket> buckets,
		Map<String, Integer> converted,
		List<UnresolvedPlacement> unresolved)
		throws IOException, PreflightException {
		List<Object> values = sourceRecords(root, source, "npclocs");
		for (int index = 0; index < values.size(); index++) {
			Map<String, Object> value =
				sourceObject(values.get(index), "npclocs[" + index + "]");
			requireSourceKeys(
				value,
				"npclocs[" + index + "]",
				"id",
				"start",
				"min",
				"max");
			PackedSourcePosition start = packedSourcePosition(
				value.get("start"),
				"npclocs[" + index + "].start");
			PackedSourcePosition minimum = packedSourcePosition(
				value.get("min"),
				"npclocs[" + index + "].min");
			PackedSourcePosition maximum = packedSourcePosition(
				value.get("max"),
				"npclocs[" + index + "].max");
			if (!start.decodable()
				|| !minimum.decodable()
				|| !maximum.decodable()
				|| start.plane() != minimum.plane()
				|| start.plane() != maximum.plane()) {
				unresolved.add(new UnresolvedPlacement(
					"npc",
					source.role,
					source.path,
					index,
					sourceNonNegativeInt(value, "id"),
					"roam-bound-crosses-or-exceeds-start-level",
					start,
					minimum,
					maximum));
				continue;
			}
			WorldCoordinate startCoordinate = start.decode();
			WorldCoordinate minimumCoordinate = minimum.decode();
			WorldCoordinate maximumCoordinate = maximum.decode();
			if (minimumCoordinate.getX() > maximumCoordinate.getX()
				|| minimumCoordinate.getY() > maximumCoordinate.getY()
				|| startCoordinate.getX() < minimumCoordinate.getX()
				|| startCoordinate.getX() > maximumCoordinate.getX()
				|| startCoordinate.getY() < minimumCoordinate.getY()
				|| startCoordinate.getY() > maximumCoordinate.getY()) {
				throw new PreflightException(
					"Legacy NPC bounds are ordered incorrectly at source index "
						+ index + ".");
			}
			Map<String, Object> bounds = map();
			bounds.put("minimum", position(minimumCoordinate));
			bounds.put("maximum", position(maximumCoordinate));
			Map<String, Object> record = map();
			record.put("placementId", placementId("npc", index));
			record.put("npcId", sourceNonNegativeInt(value, "id"));
			record.put("start", position(startCoordinate));
			record.put("roamBounds", bounds);
			bucket(buckets, startCoordinate.getLevel()).npcs.add(record);
			increment(converted, "npcs");
		}
	}

	private static void convertGroundItems(
		Path root,
		PreservationBaselineInventory.FileRecord source,
		Map<Integer, PlacementBucket> buckets,
		Map<String, Integer> converted)
		throws IOException, PreflightException {
		List<Object> values = sourceRecords(root, source, "grounditems");
		for (int index = 0; index < values.size(); index++) {
			Map<String, Object> value =
				sourceObject(values.get(index), "grounditems[" + index + "]");
			requireSourceKeys(
				value,
				"grounditems[" + index + "]",
				"id",
				"pos",
				"amount",
				"respawn");
			WorldCoordinate position = sourcePosition(
				value.get("pos"),
				"grounditems[" + index + "].pos");
			Map<String, Object> record = map();
			record.put(
				"placementId",
				placementId("ground-item", index));
			record.put("itemId", sourceNonNegativeInt(value, "id"));
			record.put("position", position(position));
			record.put("amount", sourcePositiveInt(value, "amount"));
			record.put(
				"respawnSeconds",
				sourcePositiveInt(value, "respawn"));
			bucket(buckets, position.getLevel()).groundItems.add(record);
			increment(converted, "groundItems");
		}
	}

	private static List<Object> sourceRecords(
		Path root,
		PreservationBaselineInventory.FileRecord source,
		String key) throws IOException, PreflightException {
		Path path = root.resolve(source.path).normalize();
		Map<String, Object> document = JsonDocuments.readObject(path);
		requireSourceKeys(document, source.path, key);
		Object raw = document.get(key);
		if (!(raw instanceof List)) {
			throw new PreflightException(
				"Placement source field must be an array: "
					+ source.path + " " + key);
		}
		List<Object> values = JsonDocuments.array(raw);
		if (source.recordCount == null
			|| values.size() != source.recordCount.longValue()) {
			throw new PreflightException(
				"Placement source count differs from frozen baseline: "
					+ source.path);
		}
		return values;
	}

	private static Map<String, Object> sourceObject(
		Object value, String label) throws PreflightException {
		if (!(value instanceof Map)) {
			throw new PreflightException(label + " must be an object.");
		}
		return JsonDocuments.object(value);
	}

	private static void requireSourceKeys(
		Map<String, Object> value, String label, String... keys)
		throws PreflightException {
		Set<String> expected =
			new HashSet<String>(Arrays.asList(keys));
		if (!value.keySet().equals(expected)) {
			throw new PreflightException(
				label + " fields differ from the frozen source contract.");
		}
	}

	private static int sourceInt(Map<String, Object> value, String key)
		throws PreflightException {
		Object raw = value.get(key);
		if (!(raw instanceof Long)
			|| (Long) raw < Integer.MIN_VALUE
			|| (Long) raw > Integer.MAX_VALUE) {
			throw new PreflightException(
				key + " must be a signed 32-bit integer.");
		}
		return ((Long) raw).intValue();
	}

	private static int sourceNonNegativeInt(
		Map<String, Object> value, String key) throws PreflightException {
		int result = sourceInt(value, key);
		if (result < 0) {
			throw new PreflightException(key + " must be non-negative.");
		}
		return result;
	}

	private static int sourcePositiveInt(
		Map<String, Object> value, String key) throws PreflightException {
		int result = sourceInt(value, key);
		if (result <= 0) {
			throw new PreflightException(key + " must be positive.");
		}
		return result;
	}

	private static int sourceDirection(
		Map<String, Object> value, String key) throws PreflightException {
		int result = sourceInt(value, key);
		if (result < 0 || result > 7) {
			throw new PreflightException(key + " must be 0..7.");
		}
		return result;
	}

	private static PackedSourcePosition packedSourcePosition(
		Object value, String label) throws PreflightException {
		Map<String, Object> position = sourceObject(value, label);
		requireSourceKeys(position, label, "X", "Y");
		return new PackedSourcePosition(
			sourceInt(position, "X"),
			sourceInt(position, "Y"));
	}

	private static WorldCoordinate sourcePosition(
		Object value, String label) throws PreflightException {
		PackedSourcePosition packed = packedSourcePosition(value, label);
		if (!packed.decodable()) {
			throw new PreflightException(
				label + " is outside the legacy four-level coordinate model.");
		}
		return packed.decode();
	}

	private static Map<String, Object> position(WorldCoordinate coordinate) {
		Map<String, Object> result = map();
		result.put("x", Long.valueOf(coordinate.getX()));
		result.put("y", Long.valueOf(coordinate.getY()));
		return result;
	}

	private static String placementId(String family, int sourceIndex) {
		return "preservation-r64." + family + "."
			+ String.format(Locale.ROOT, "%06d", sourceIndex);
	}

	private static PlacementBucket bucket(
		Map<Integer, PlacementBucket> buckets, int level)
		throws PreflightException {
		PlacementBucket result = buckets.get(Integer.valueOf(level));
		if (result == null) {
			throw new PreflightException(
				"Placement decoded to an undeclared Preservation level: "
					+ level);
		}
		return result;
	}

	private static void increment(
		Map<String, Integer> counts, String family) {
		counts.put(
			family,
			Integer.valueOf(counts.get(family).intValue() + 1));
	}

	private static int compareIdentity(
		WorldMapSectorId left, WorldMapSectorId right) {
		int level = compareLevel(left.getLevel(), right.getLevel());
		if (level != 0) {
			return level;
		}
		int x = Integer.compare(left.getSectorX(), right.getSectorX());
		return x != 0
			? x
			: Integer.compare(left.getSectorY(), right.getSectorY());
	}

	private static int compareLevel(int left, int right) {
		int category = Integer.compare(
			levelCategory(left), levelCategory(right));
		if (category != 0) {
			return category;
		}
		if (left < 0) {
			return Long.compare(-(long) left, -(long) right);
		}
		return Integer.compare(left, right);
	}

	private static int levelCategory(int level) {
		return level == 0 ? 0 : level > 0 ? 1 : 2;
	}

	private static byte[] readExact(
		InputStream input,
		int expected,
		String label) throws IOException, PreflightException {
		try (InputStream stream = input;
			ByteArrayOutputStream output = new ByteArrayOutputStream(expected)) {
			byte[] buffer = new byte[8192];
			int count;
			while ((count = stream.read(buffer)) != -1) {
				output.write(buffer, 0, count);
				if (output.size() > expected) {
					throw new PreflightException(
						"Preservation terrain entry exceeds " + expected
							+ " bytes: " + label);
				}
			}
			byte[] result = output.toByteArray();
			if (result.length != expected) {
				throw new PreflightException(
					"Preservation terrain entry must contain exactly " + expected
						+ " bytes: " + label);
			}
			return result;
		}
	}

	private static byte[] toNativeRaw(byte[] legacy) {
		byte[] result = Arrays.copyOf(legacy, legacy.length);
		for (int offset = 0; offset < result.length; offset += TILE_BYTES) {
			result[offset + 4] = legacy[offset + 5];
			result[offset + 5] = legacy[offset + 4];
		}
		return result;
	}

	private static byte[] toLegacyRaw(byte[] nativePayload) {
		byte[] result = Arrays.copyOf(nativePayload, nativePayload.length);
		for (int offset = 0; offset < result.length; offset += TILE_BYTES) {
			result[offset + 4] = nativePayload[offset + 5];
			result[offset + 5] = nativePayload[offset + 4];
		}
		return result;
	}

	private static String terrainPath(WorldMapSectorId identity) {
		return "terrain/global/l" + signedToken(identity.getLevel())
			+ "/x" + signedToken(identity.getSectorX())
			+ "-y" + signedToken(identity.getSectorY()) + ".raw";
	}

	private static String signedToken(int value) {
		return value < 0
			? "m" + Long.toString(-(long) value)
			: "p" + Integer.toString(value);
	}

	private static Map<String, Object> manifest(
		List<SectorRecord> sectors,
		List<PlacementSetRecord> placementSets) {
		Map<String, Object> document = map();
		document.put("schemaVersion", Long.valueOf(1));
		document.put("packageType", "layered-world");
		document.put("packageId", PACKAGE_ID);
		document.put("packageVersion", PACKAGE_VERSION);
		document.put("coordinateModel", "signed-layered-v1");
		Map<String, Object> storage = map();
		storage.put("sectorSize", Long.valueOf(48));
		storage.put("presentationChunkSize", Long.valueOf(24));
		document.put("storage", storage);

		List<Object> worldSpaces = new ArrayList<Object>();
		Map<String, Object> global = map();
		global.put("id", "global");
		global.put("kind", "static");
		worldSpaces.add(global);
		document.put("worldSpaces", worldSpaces);

		List<Object> levels = new ArrayList<Object>();
		Set<Integer> seen = new HashSet<Integer>();
		for (SectorRecord sector : sectors) {
			int level = sector.identity.getLevel();
			if (!seen.add(Integer.valueOf(level))) {
				continue;
			}
			Map<String, Object> value = map();
			value.put("worldSpace", "global");
			value.put("level", Long.valueOf(level));
			value.put("name", levelName(level));
			value.put("role", levelRole(level));
			levels.add(value);
		}
		document.put("levels", levels);

		List<Object> terrain = new ArrayList<Object>();
		for (SectorRecord sector : sectors) {
			Map<String, Object> value = map();
			value.put("worldSpace", "global");
			value.put("level", Long.valueOf(sector.identity.getLevel()));
			value.put("sectorX", Long.valueOf(sector.identity.getSectorX()));
			value.put("sectorY", Long.valueOf(sector.identity.getSectorY()));
			value.put("encoding", RawLayeredTerrainSector.ENCODING);
			value.put("path", sector.relativePath);
			value.put("sha256", sector.sha256);
			terrain.add(value);
		}
		document.put("terrainSectors", terrain);
		List<Object> placementDocuments = new ArrayList<Object>();
		for (PlacementSetRecord set : placementSets) {
			Map<String, Object> value = map();
			value.put("id", set.id);
			value.put("worldSpace", "global");
			value.put("level", Long.valueOf(set.level));
			value.put("encoding", LayeredEntityPlacements.ENCODING_V3);
			value.put("path", set.relativePath);
			value.put("sha256", set.sha256);
			placementDocuments.add(value);
		}
		document.put("placementSets", placementDocuments);
		return document;
	}

	private static String levelName(int level) {
		switch (level) {
			case 0:
				return "Surface";
			case 1:
				return "Upper level 1";
			case 2:
				return "Upper level 2";
			case -1:
				return "Underground";
			default:
				return "Level " + level;
		}
	}

	private static String levelRole(int level) {
		switch (level) {
			case 0:
				return "surface";
			case 1:
				return "upper-level-1";
			case 2:
				return "upper-level-2";
			case -1:
				return "underground";
			default:
				return level < 0 ? "underground" : "upper-level";
		}
	}

	private static void writeNew(Path destination, byte[] content)
		throws IOException {
		Files.write(
			destination,
			content,
			StandardOpenOption.CREATE_NEW,
			StandardOpenOption.WRITE);
	}

	private static Map<String, Object> map() {
		return new LinkedHashMap<String, Object>();
	}

	static final class Result {
		final Path packageRoot;
		final String baselineFingerprint;
		final String sourceTerrainPath;
		final String sourceTerrainSha256;
		final int terrainSectorCount;
		final long terrainPayloadBytes;
		final Map<Integer, Integer> sectorCountByLevel;
		final long sourcePlacementCount;
		final Map<String, Integer> convertedPlacementCountByFamily;
		final int placementSetCount;
		final List<UnresolvedPlacement> unresolvedPlacements;
		final long unconvertedPlacementCount;
		final String manifestSha256;
		final String packageFingerprint;
		final String validationJson;

		Result(
			Path packageRoot,
			String baselineFingerprint,
			String sourceTerrainPath,
			String sourceTerrainSha256,
			int terrainSectorCount,
			long terrainPayloadBytes,
			Map<Integer, Integer> sectorCountByLevel,
			long sourcePlacementCount,
			Map<String, Integer> convertedPlacementCountByFamily,
			int placementSetCount,
			List<UnresolvedPlacement> unresolvedPlacements,
			String manifestSha256,
			String packageFingerprint,
			String validationJson) {
			this.packageRoot = packageRoot;
			this.baselineFingerprint = baselineFingerprint;
			this.sourceTerrainPath = sourceTerrainPath;
			this.sourceTerrainSha256 = sourceTerrainSha256;
			this.terrainSectorCount = terrainSectorCount;
			this.terrainPayloadBytes = terrainPayloadBytes;
			this.sectorCountByLevel = Collections.unmodifiableMap(
				new LinkedHashMap<Integer, Integer>(sectorCountByLevel));
			this.sourcePlacementCount = sourcePlacementCount;
			this.convertedPlacementCountByFamily =
				Collections.unmodifiableMap(
					new LinkedHashMap<String, Integer>(
						convertedPlacementCountByFamily));
			this.placementSetCount = placementSetCount;
			this.unresolvedPlacements = Collections.unmodifiableList(
				new ArrayList<UnresolvedPlacement>(unresolvedPlacements));
			this.unconvertedPlacementCount = unresolvedPlacements.size();
			this.manifestSha256 = manifestSha256;
			this.packageFingerprint = packageFingerprint;
			this.validationJson = validationJson;
		}

		String toJson() {
			Map<String, Object> document = map();
			document.put("schemaVersion", Long.valueOf(REPORT_SCHEMA_VERSION));
			document.put("reportType", REPORT_TYPE);
			document.put("reviewState", "placements-incomplete");
			document.put("runtimePromotionApproved", Boolean.FALSE);
			document.put(
				"baselineId",
				PreservationBaselineInventory.BASELINE_ID);
			document.put(
				"baselineSourceSetSha256",
				baselineFingerprint);
			document.put("sourceCoordinateModel", LegacyPackedCoordinateCodec.ID);
			document.put("targetCoordinateModel", "signed-layered-v1");
			document.put("sourceTerrainPath", sourceTerrainPath);
			document.put("sourceTerrainSha256", sourceTerrainSha256);
			document.put("terrainEncoding", RawLayeredTerrainSector.ENCODING);
			document.put("terrainSectorCount", Long.valueOf(terrainSectorCount));
			document.put("terrainPayloadBytes", Long.valueOf(terrainPayloadBytes));
			Map<String, Object> counts = map();
			for (Map.Entry<Integer, Integer> entry
				: sectorCountByLevel.entrySet()) {
				counts.put(
					Integer.toString(entry.getKey()),
					Long.valueOf(entry.getValue().intValue()));
			}
			document.put("sectorCountByLevel", counts);
			document.put("legacyRoundTripVerified", Boolean.TRUE);
			document.put(
				"placementEncoding",
				LayeredEntityPlacements.ENCODING_V3);
			document.put(
				"sourcePlacementRecords",
				Long.valueOf(sourcePlacementCount));
			Map<String, Object> placementCounts = map();
			for (Map.Entry<String, Integer> entry
				: convertedPlacementCountByFamily.entrySet()) {
				placementCounts.put(
					entry.getKey(),
					Long.valueOf(entry.getValue().intValue()));
			}
			document.put(
				"convertedPlacementRecordsByFamily",
				placementCounts);
			document.put(
				"convertedPlacementRecords",
				Long.valueOf(convertedPlacementCount()));
			document.put(
				"placementSetsGenerated",
				Long.valueOf(placementSetCount));
			document.put(
				"unconvertedPlacementRecords",
				Long.valueOf(unconvertedPlacementCount));
			List<Object> unresolved = new ArrayList<Object>();
			for (UnresolvedPlacement placement : unresolvedPlacements) {
				unresolved.add(placement.toDocument());
			}
			document.put("unresolvedPlacements", unresolved);
			document.put("packageId", PACKAGE_ID);
			document.put("packageVersion", PACKAGE_VERSION);
			document.put("manifestSha256", manifestSha256);
			document.put("packageFingerprintSha256", packageFingerprint);
			return JsonDocuments.pretty(document);
		}

		String toMarkdown() {
			StringBuilder out = new StringBuilder();
			out.append("# Preservation Layered Parity Review Package\n\n");
			out.append("- Review state: `placements-incomplete`\n");
			out.append("- Runtime promotion approved: `false`\n");
			out.append("- Baseline: `")
				.append(PreservationBaselineInventory.BASELINE_ID)
				.append("`\n");
			out.append("- Baseline SHA-256: `")
				.append(baselineFingerprint).append("`\n");
			out.append("- Package: `").append(PACKAGE_ID).append("@")
				.append(PACKAGE_VERSION).append("`\n");
			out.append("- Terrain encoding: `")
				.append(RawLayeredTerrainSector.ENCODING).append("`\n");
			out.append("- Terrain sectors: ").append(terrainSectorCount)
				.append("\n");
			out.append("- Terrain payload bytes: ").append(terrainPayloadBytes)
				.append("\n");
			out.append("- Exact legacy reverse transform: `verified`\n");
			out.append("- Placement encoding: `")
				.append(LayeredEntityPlacements.ENCODING_V3)
				.append("`\n");
			out.append("- Converted placement records: ")
				.append(convertedPlacementCount()).append(" / ")
				.append(sourcePlacementCount).append("\n");
			out.append("- Unconverted placement records: ")
				.append(unconvertedPlacementCount).append("\n\n");
			for (UnresolvedPlacement placement : unresolvedPlacements) {
				out.append("- Unresolved `")
					.append(placement.family)
					.append("` source index ")
					.append(placement.sourceIndex)
					.append(": `")
					.append(placement.reason)
					.append("`\n");
			}
			out.append("\n");
			out.append(
				"This package remains inside the isolated review workspace. "
					+ "It is not eligible for runtime promotion or game export "
					+ "until every placement anomaly and transition is reviewed "
					+ "and complete-world replacement ownership is proven.\n");
			return out.toString();
		}

		private int convertedPlacementCount() {
			int result = 0;
			for (Integer count : convertedPlacementCountByFamily.values()) {
				result = Math.addExact(result, count.intValue());
			}
			return result;
		}
	}

	private static final class SectorRecord {
		final WorldMapSectorId identity;
		final String relativePath;
		final String sha256;
		final long payloadBytes;

		SectorRecord(
			WorldMapSectorId identity,
			String relativePath,
			String sha256,
			long payloadBytes) {
			this.identity = identity;
			this.relativePath = relativePath;
			this.sha256 = sha256;
			this.payloadBytes = payloadBytes;
		}
	}

	private static final class PlacementBucket {
		final int level;
		final List<Object> npcs = new ArrayList<Object>();
		final List<Object> groundItems = new ArrayList<Object>();
		final List<Object> scenery = new ArrayList<Object>();
		final List<Object> boundaries = new ArrayList<Object>();

		PlacementBucket(int level) {
			this.level = level;
		}

		int count() {
			return Math.addExact(
				Math.addExact(npcs.size(), groundItems.size()),
				Math.addExact(scenery.size(), boundaries.size()));
		}
	}

	private static final class PlacementSetRecord {
		final String id;
		final int level;
		final String relativePath;
		final String sha256;
		final int placementCount;

		PlacementSetRecord(
			String id,
			int level,
			String relativePath,
			String sha256,
			int placementCount) {
			this.id = id;
			this.level = level;
			this.relativePath = relativePath;
			this.sha256 = sha256;
			this.placementCount = placementCount;
		}
	}

	private static final class PlacementConversion {
		final List<PlacementSetRecord> sets;
		final Map<String, Integer> convertedByFamily;
		final List<UnresolvedPlacement> unresolved;

		PlacementConversion(
			List<PlacementSetRecord> sets,
			Map<String, Integer> convertedByFamily,
			List<UnresolvedPlacement> unresolved) {
			this.sets = Collections.unmodifiableList(
				new ArrayList<PlacementSetRecord>(sets));
			this.convertedByFamily = Collections.unmodifiableMap(
				new LinkedHashMap<String, Integer>(convertedByFamily));
			this.unresolved = Collections.unmodifiableList(
				new ArrayList<UnresolvedPlacement>(unresolved));
		}

		int count(String family) {
			Integer count = convertedByFamily.get(family);
			return count == null ? 0 : count.intValue();
		}

		int convertedCount() {
			int result = 0;
			for (Integer count : convertedByFamily.values()) {
				result = Math.addExact(result, count.intValue());
			}
			return result;
		}
	}

	private static final class PackedSourcePosition {
		final int x;
		final int y;

		PackedSourcePosition(int x, int y) {
			this.x = x;
			this.y = y;
		}

		boolean decodable() {
			return x >= 0
				&& x <= LegacyPackedCoordinateCodec.MAX_LEGACY_X
				&& y >= LegacyPackedCoordinateCodec.MIN_PACKED_Y
				&& y <= LegacyPackedCoordinateCodec.MAX_PACKED_Y;
		}

		int plane() {
			return Math.floorDiv(
				y,
				LegacyPackedCoordinateCodec.LEVEL_STRIDE);
		}

		WorldCoordinate decode() {
			return LegacyPackedCoordinateCodec.decode(x, y);
		}

		Map<String, Object> toDocument() {
			Map<String, Object> result = map();
			result.put("x", Long.valueOf(x));
			result.put("y", Long.valueOf(y));
			return result;
		}
	}

	private static final class UnresolvedPlacement {
		final String family;
		final String sourceRole;
		final String sourcePath;
		final int sourceIndex;
		final int sourceDefinitionId;
		final String reason;
		final PackedSourcePosition start;
		final PackedSourcePosition minimum;
		final PackedSourcePosition maximum;

		UnresolvedPlacement(
			String family,
			String sourceRole,
			String sourcePath,
			int sourceIndex,
			int sourceDefinitionId,
			String reason,
			PackedSourcePosition start,
			PackedSourcePosition minimum,
			PackedSourcePosition maximum) {
			this.family = family;
			this.sourceRole = sourceRole;
			this.sourcePath = sourcePath;
			this.sourceIndex = sourceIndex;
			this.sourceDefinitionId = sourceDefinitionId;
			this.reason = reason;
			this.start = start;
			this.minimum = minimum;
			this.maximum = maximum;
		}

		Map<String, Object> toDocument() {
			Map<String, Object> result = map();
			result.put("family", family);
			result.put("sourceRole", sourceRole);
			result.put("sourcePath", sourcePath);
			result.put("sourceIndex", Long.valueOf(sourceIndex));
			result.put(
				"sourceDefinitionId",
				Long.valueOf(sourceDefinitionId));
			result.put("reason", reason);
			result.put("startPacked", start.toDocument());
			result.put("minimumPacked", minimum.toDocument());
			result.put("maximumPacked", maximum.toDocument());
			return result;
		}
	}
}
