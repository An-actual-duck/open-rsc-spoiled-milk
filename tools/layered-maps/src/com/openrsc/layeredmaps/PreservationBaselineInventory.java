package com.openrsc.layeredmaps;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Deterministic inventory of the repository's Preservation revision-64 map baseline. */
final class PreservationBaselineInventory {
	static final String BASELINE_ID = "rsc-remastered-preservation-r64-v1";
	static final String MANIFEST_TYPE = "rsc-remastered-vanilla-map-baseline";
	static final int SCHEMA_VERSION = 1;

	private static final int TERRAIN_ENTRY_BYTES = 48 * 48 * 10;
	private static final Pattern TERRAIN_ENTRY =
		Pattern.compile("h([0-3])x([0-9]+)y([0-9]+)");
	private static final Pattern CONFIG_VALUE = Pattern.compile(
		"^\\s*([A-Za-z0-9_]+)\\s*:\\s*([^#]*?)\\s*(?:#.*)?$");

	private static final String CONFIG_PATH =
		"legacy/docs/inherited-openrsc/server-configs/preservation.conf";
	private static final String SERVER_ORSC =
		"server/conf/server/data/Authentic_Landscape.orsc";
	private static final String CLIENT_ORSC =
		"Client_Base/Cache/video/Authentic_Landscape.orsc";

	private static final List<FileSpec> FILES = Collections.unmodifiableList(Arrays.asList(
		new FileSpec("preservation-configuration", CONFIG_PATH, null, false),
		new FileSpec("preservation-sqlite-seed",
			"legacy/docs/inherited-openrsc/sqlite-seeds/preservation.db", null, false),
		new FileSpec("server-map-free",
			"server/conf/server/data/maps/maps64.jag", null, false),
		new FileSpec("server-map-members",
			"server/conf/server/data/maps/maps64.mem", null, false),
		new FileSpec("server-land-free",
			"server/conf/server/data/maps/land64.jag", null, false),
		new FileSpec("server-land-members",
			"server/conf/server/data/maps/land64.mem", null, false),
		new FileSpec("server-authentic-terrain", SERVER_ORSC, null, true),
		new FileSpec("client-authentic-terrain", CLIENT_ORSC, null, true),
		new FileSpec("base-boundaries",
			"server/conf/server/defs/locs/BoundaryLocs.json", "boundaries", false),
		new FileSpec("base-scenery",
			"server/conf/server/defs/locs/SceneryLocs.json", "sceneries", false),
		new FileSpec("base-npcs",
			"server/conf/server/defs/locs/NpcLocs.json", "npclocs", false),
		new FileSpec("base-ground-items",
			"server/conf/server/defs/locs/GroundItems.json", "grounditems", false)));

	Baseline inspect(Path requestedRoot) throws PreflightException {
		try {
			Path root = canonicalRoot(requestedRoot);
			Map<String, String> config = readConfig(requiredFile(root, CONFIG_PATH));
			requireInt(config, "location_data", 1);
			requireInt(config, "based_map_data", 64);
			requireBoolean(config, "member_world", true);
			requireBoolean(config, "custom_landscape", false);
			if (config.containsKey("want_myworld")) {
				requireBoolean(config, "want_myworld", false);
			}

			List<FileRecord> files = new ArrayList<FileRecord>();
			for (FileSpec spec : FILES) {
				Path path = requiredFile(root, spec.path);
				Long recordCount = spec.recordKey == null
					? null : Long.valueOf(recordCount(path, spec.recordKey));
				Long archiveEntryCount = spec.terrainArchive
					? Long.valueOf(terrainEntryCount(path)) : null;
				files.add(new FileRecord(
					spec.role,
					spec.path,
					Files.size(path),
					Hashes.sha256(path),
					recordCount,
					archiveEntryCount));
			}

			FileRecord serverTerrain = find(files, "server-authentic-terrain");
			FileRecord clientTerrain = find(files, "client-authentic-terrain");
			if (serverTerrain.size != clientTerrain.size
				|| !serverTerrain.sha256.equals(clientTerrain.sha256)
				|| !serverTerrain.archiveEntryCount.equals(clientTerrain.archiveEntryCount)) {
				throw new PreflightException(
					"Server and client Authentic_Landscape.orsc inputs are not identical.");
			}

			Map<String, Object> document = new LinkedHashMap<String, Object>();
			document.put("schemaVersion", Long.valueOf(SCHEMA_VERSION));
			document.put("manifestType", MANIFEST_TYPE);
			document.put("baselineId", BASELINE_ID);
			document.put("sourceCoordinateModel", LegacyPackedCoordinateCodec.ID);
			document.put("configuration", configuration(config));
			List<Object> fileDocuments = new ArrayList<Object>();
			for (FileRecord file : files) {
				fileDocuments.add(file.toDocument());
			}
			document.put("files", fileDocuments);
			String sourceSetFingerprint = Hashes.sha256(JsonDocuments.canonical(document));
			document.put("sourceSetFingerprintSha256", sourceSetFingerprint);
			return new Baseline(document, files, sourceSetFingerprint);
		} catch (PreflightException failure) {
			throw failure;
		} catch (IOException failure) {
			throw new PreflightException(
				"Could not inventory the Preservation baseline: " + failure.getMessage(), failure);
		} catch (RuntimeException failure) {
			throw new PreflightException(
				"Preservation baseline inspection failed: " + failure.getMessage(), failure);
		}
	}

	private static Map<String, Object> configuration(Map<String, String> values) {
		Map<String, Object> configuration = new LinkedHashMap<String, Object>();
		configuration.put("path", CONFIG_PATH);
		Map<String, Object> selectors = new LinkedHashMap<String, Object>();
		selectors.put("locationData", Long.valueOf(1));
		selectors.put("basedMapData", Long.valueOf(64));
		selectors.put("memberWorld", Boolean.TRUE);
		selectors.put("customLandscape", Boolean.FALSE);
		selectors.put("wantMyWorld", Boolean.FALSE);
		selectors.put("wantMyWorldSource",
			values.containsKey("want_myworld") ? "explicit" : "default");
		configuration.put("selectors", selectors);
		return configuration;
	}

	private static int recordCount(Path path, String key)
		throws IOException, PreflightException {
		Map<String, Object> root = JsonDocuments.readObject(path);
		if (root.size() != 1 || !(root.get(key) instanceof List)) {
			throw new PreflightException(
				"Placement file must contain exactly one array named " + key + ": " + path);
		}
		return JsonDocuments.array(root.get(key)).size();
	}

	private static int terrainEntryCount(Path path)
		throws IOException, PreflightException {
		int count = 0;
		Set<String> names = new HashSet<String>();
		try (ZipFile archive = new ZipFile(path.toFile())) {
			Enumeration<? extends ZipEntry> entries = archive.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory() || !TERRAIN_ENTRY.matcher(entry.getName()).matches()) {
					throw new PreflightException(
						"Authentic terrain has an unsupported entry: " + entry.getName());
				}
				if (!names.add(entry.getName())) {
					throw new PreflightException(
						"Authentic terrain has a duplicate entry: " + entry.getName());
				}
				if (countBytes(archive.getInputStream(entry), TERRAIN_ENTRY_BYTES + 1)
					!= TERRAIN_ENTRY_BYTES) {
					throw new PreflightException(
						"Authentic terrain entry has an invalid decoded size: " + entry.getName());
				}
				count++;
			}
		}
		if (count == 0) {
			throw new PreflightException("Authentic terrain archive contains no sectors: " + path);
		}
		return count;
	}

	private static int countBytes(InputStream input, int limit) throws IOException {
		try (InputStream stream = input) {
			byte[] buffer = new byte[8192];
			int total = 0;
			while (total < limit) {
				int count = stream.read(buffer, 0, Math.min(buffer.length, limit - total));
				if (count == -1) {
					break;
				}
				total += count;
			}
			return total;
		}
	}

	private static Map<String, String> readConfig(Path path)
		throws IOException, PreflightException {
		Map<String, String> values = new HashMap<String, String>();
		for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			Matcher matcher = CONFIG_VALUE.matcher(line);
			if (!matcher.matches()) {
				continue;
			}
			String key = matcher.group(1).toLowerCase(Locale.ROOT);
			if (values.put(key, matcher.group(2).trim()) != null) {
				throw new PreflightException(
					"Preservation configuration key appears more than once: " + key);
			}
		}
		return values;
	}

	private static void requireInt(
		Map<String, String> values, String key, int expected) throws PreflightException {
		String value = required(values, key);
		try {
			if (Integer.parseInt(value) != expected) {
				throw new PreflightException(
					"Preservation selector " + key + " must be " + expected + ".");
			}
		} catch (NumberFormatException failure) {
			throw new PreflightException(
				"Preservation selector " + key + " must be an integer.", failure);
		}
	}

	private static void requireBoolean(
		Map<String, String> values, String key, boolean expected) throws PreflightException {
		String value = required(values, key).toLowerCase(Locale.ROOT);
		if (!Boolean.toString(expected).equals(value)) {
			throw new PreflightException(
				"Preservation selector " + key + " must be " + expected + ".");
		}
	}

	private static String required(Map<String, String> values, String key)
		throws PreflightException {
		String value = values.get(key);
		if (value == null || value.isEmpty()) {
			throw new PreflightException(
				"Preservation configuration is missing selector: " + key);
		}
		return value;
	}

	private static Path canonicalRoot(Path requestedRoot)
		throws IOException, PreflightException {
		if (requestedRoot == null) {
			throw new PreflightException("A repository root is required.");
		}
		Path root = requestedRoot.toAbsolutePath().normalize();
		if (!Files.isDirectory(root)) {
			throw new PreflightException("Repository root is not a directory: " + root);
		}
		return root.toRealPath();
	}

	private static Path requiredFile(Path root, String relative)
		throws IOException, PreflightException {
		Path candidate = root.resolve(relative).normalize();
		if (!candidate.startsWith(root)
			|| !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(candidate)) {
			throw new PreflightException(
				"Required Preservation baseline file is missing or unsafe: " + relative);
		}
		Path real = candidate.toRealPath();
		if (!real.startsWith(root)) {
			throw new PreflightException(
				"Preservation baseline path escapes the repository: " + relative);
		}
		return real;
	}

	private static FileRecord find(List<FileRecord> files, String role) {
		for (FileRecord file : files) {
			if (role.equals(file.role)) {
				return file;
			}
		}
		throw new IllegalStateException("Missing internal baseline role: " + role);
	}

	static final class Baseline {
		final Map<String, Object> document;
		final List<FileRecord> files;
		final String sourceSetFingerprint;

		Baseline(
			Map<String, Object> document,
			List<FileRecord> files,
			String sourceSetFingerprint) {
			this.document = Collections.unmodifiableMap(
				new LinkedHashMap<String, Object>(document));
			this.files = Collections.unmodifiableList(new ArrayList<FileRecord>(files));
			this.sourceSetFingerprint = sourceSetFingerprint;
		}

		String toJson() {
			return JsonDocuments.pretty(document);
		}

		String toMarkdown() {
			StringBuilder out = new StringBuilder();
			out.append("# RSC Remastered Preservation Baseline\n\n");
			out.append("- Baseline ID: `").append(BASELINE_ID).append("`\n");
			out.append("- Coordinate source: `").append(LegacyPackedCoordinateCodec.ID)
				.append("`\n");
			out.append("- Source-set SHA-256: `").append(sourceSetFingerprint).append("`\n\n");
			out.append("| Role | Path | Bytes | Records/entries | SHA-256 |\n");
			out.append("| --- | --- | ---: | ---: | --- |\n");
			for (FileRecord file : files) {
				Long count = file.recordCount == null
					? file.archiveEntryCount : file.recordCount;
				out.append("| ").append(file.role).append(" | `").append(file.path)
					.append("` | ").append(file.size).append(" | ")
					.append(count == null ? "-" : count.toString()).append(" | `")
					.append(file.sha256).append("` |\n");
			}
			return out.toString();
		}
	}

	static final class FileRecord {
		final String role;
		final String path;
		final long size;
		final String sha256;
		final Long recordCount;
		final Long archiveEntryCount;

		FileRecord(
			String role,
			String path,
			long size,
			String sha256,
			Long recordCount,
			Long archiveEntryCount) {
			this.role = role;
			this.path = path;
			this.size = size;
			this.sha256 = sha256;
			this.recordCount = recordCount;
			this.archiveEntryCount = archiveEntryCount;
		}

		Map<String, Object> toDocument() {
			Map<String, Object> result = new LinkedHashMap<String, Object>();
			result.put("role", role);
			result.put("path", path);
			result.put("size", Long.valueOf(size));
			result.put("sha256", sha256);
			if (recordCount != null) {
				result.put("recordCount", recordCount);
			}
			if (archiveEntryCount != null) {
				result.put("archiveEntryCount", archiveEntryCount);
			}
			return result;
		}
	}

	private static final class FileSpec {
		final String role;
		final String path;
		final String recordKey;
		final boolean terrainArchive;

		FileSpec(String role, String path, String recordKey, boolean terrainArchive) {
			this.role = role;
			this.path = path;
			this.recordKey = recordKey;
			this.terrainArchive = terrainArchive;
		}
	}
}
