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
		"rsc-remastered.preservation-r64-terrain-review";
	static final String PACKAGE_VERSION = "0.1.0";
	static final String REPORT_TYPE =
		"preservation-layered-terrain-generation";
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

		Map<String, Object> manifest =
			manifest(sectors);
		Path manifestPath = stagingRoot.resolve("manifest.json");
		writeNew(
			manifestPath,
			JsonDocuments.pretty(manifest).getBytes(StandardCharsets.UTF_8));
		LayeredWorldPackageManifest loaded =
			LayeredWorldPackageManifest.load(stagingRoot);
		if (loaded.getTerrainSectors().size() != sectors.size()
			|| !loaded.getPlacementSets().isEmpty()) {
			throw new PreflightException(
				"Generated terrain review package validation disagreed with "
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
		long unconvertedPlacements = 0L;
		for (PreservationBaselineInventory.FileRecord file : baseline.files) {
			if (file.recordCount != null) {
				unconvertedPlacements =
					Math.addExact(unconvertedPlacements, file.recordCount.longValue());
			}
		}
		return new Result(
			packageRoot,
			baseline.sourceSetFingerprint,
			terrainSource.path,
			terrainSource.sha256,
			sectors.size(),
			payloadBytes,
			levelCounts,
			unconvertedPlacements,
			manifestSha256,
			loaded.getPackageFingerprint(),
			validationJson);
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

	private static Map<String, Object> manifest(List<SectorRecord> sectors) {
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
		document.put("placementSets", new ArrayList<Object>());
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
			long unconvertedPlacementCount,
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
			this.unconvertedPlacementCount = unconvertedPlacementCount;
			this.manifestSha256 = manifestSha256;
			this.packageFingerprint = packageFingerprint;
			this.validationJson = validationJson;
		}

		String toJson() {
			Map<String, Object> document = map();
			document.put("schemaVersion", Long.valueOf(REPORT_SCHEMA_VERSION));
			document.put("reportType", REPORT_TYPE);
			document.put("reviewState", "terrain-only");
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
			document.put("placementSetsGenerated", Long.valueOf(0));
			document.put(
				"unconvertedPlacementRecords",
				Long.valueOf(unconvertedPlacementCount));
			document.put("packageId", PACKAGE_ID);
			document.put("packageVersion", PACKAGE_VERSION);
			document.put("manifestSha256", manifestSha256);
			document.put("packageFingerprintSha256", packageFingerprint);
			return JsonDocuments.pretty(document);
		}

		String toMarkdown() {
			StringBuilder out = new StringBuilder();
			out.append("# Preservation Layered Terrain Review Package\n\n");
			out.append("- Review state: `terrain-only`\n");
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
			out.append("- Unconverted placement records: ")
				.append(unconvertedPlacementCount).append("\n\n");
			out.append(
				"This package remains inside the isolated review workspace. "
					+ "It is not eligible for runtime promotion or game export "
					+ "until placement and transition conversion are complete.\n");
			return out.toString();
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
}
