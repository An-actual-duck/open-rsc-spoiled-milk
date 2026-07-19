package com.openrsc.layeredmaps;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read-only discovery for the first packed OpenRSC/Spoiled Milk adapter. */
final class RepositoryPreflight {
	static final String LAYOUT_ADAPTER = "spoiled-milk-repository-v1";
	static final String CONFIG_PATH = "server/myworld.conf";
	static final String SERVER_TERRAIN = "server/conf/server/data/Custom_Landscape.orsc";
	static final String CLIENT_TERRAIN = "Client_Base/Cache/video/Custom_Landscape.orsc";

	private static final int TERRAIN_ENTRY_BYTES = 48 * 48 * 10;
	private static final Pattern TERRAIN_ENTRY = Pattern.compile("h([0-3])x([0-9]+)y([0-9]+)");
	private static final Pattern CONFIG_VALUE = Pattern.compile(
		"^\\s*([A-Za-z0-9_]+)\\s*:\\s*([^#]*?)\\s*(?:#.*)?$");

	private static final String[] REQUIRED_MARKERS = {
		"server/build.xml",
		"Client_Base/build.xml"
	};

	private static final String[] SOURCE_ROOTS = {
		"server/src",
		"server/plugins",
		"Client_Base/src",
		"tools/world-builder/src"
	};
	private static final String LAYERED_REGION_TILE_SNAPSHOT =
		"server/src/com/openrsc/server/model/world/region/LayeredRegionTileSnapshot.java";

	private static final Map<String, List<String>> JAVA_SIGNALS = javaSignals();

	PreflightReport inspect(Path requestedRoot) throws PreflightException {
		try {
			Path root = canonicalRoot(requestedRoot);
			for (String marker : REQUIRED_MARKERS) {
				requiredFile(root, marker);
			}

			Path configPath = requiredFile(root, CONFIG_PATH);
			String configHash = Hashes.sha256(configPath);
			Config config = Config.read(configPath);
			PreflightReport.Configuration configuration = new PreflightReport.Configuration(
				CONFIG_PATH,
				configHash,
				config.requiredInt("client_version"),
				config.requiredInt("based_map_data"),
				config.requiredBoolean("member_world"),
				config.requiredBoolean("custom_landscape"),
				config.requiredBoolean("want_myworld"));
			validateConfiguration(configuration);

			Path serverTerrain = requiredFile(root, SERVER_TERRAIN);
			Path clientTerrain = requiredFile(root, CLIENT_TERRAIN);
			String serverTerrainHash = Hashes.sha256(serverTerrain);
			String clientTerrainHash = Hashes.sha256(clientTerrain);
			if (!serverTerrainHash.equals(clientTerrainHash)
				|| Files.size(serverTerrain) != Files.size(clientTerrain)) {
				throw new PreflightException(
					"Server and client Custom_Landscape.orsc archives are not byte-identical.");
			}

			TerrainInventory serverInventory = validateTerrainArchive(serverTerrain);
			TerrainInventory clientInventory = validateTerrainArchive(clientTerrain);
			if (!serverInventory.sameAs(clientInventory)) {
				throw new PreflightException(
					"Server and client terrain archives have different validated sector inventories.");
			}

			List<PreflightReport.SourceFile> candidates = inventoryCandidates(root, configPath);
			String fingerprint = sourceFingerprint(configuration, serverTerrainHash, candidates);
			verifyUnchanged(root, configuration, serverTerrainHash, candidates);

			PreflightReport.Terrain terrain = new PreflightReport.Terrain(
				SERVER_TERRAIN,
				CLIENT_TERRAIN,
				serverTerrainHash,
				serverInventory.sectorCount,
				serverInventory.planes);
			List<PreflightReport.Finding> findings = Arrays.asList(
				new PreflightReport.Finding(
					"info",
					"legacy-packed-coordinate-model",
					"The target uses four 944-tile packed-Y bands and requires explicit layered conversion."),
				new PreflightReport.Finding(
					"info",
					"candidate-coordinate-owners",
					candidates.size() + " candidate files require later parsing; preflight has not rewritten them."),
				new PreflightReport.Finding(
					"info",
					"read-only-preflight",
					"Discovery and hashing completed without a target mutation operation."));
			return new PreflightReport(
				LAYOUT_ADAPTER, fingerprint, configuration, terrain, candidates, findings);
		} catch (PreflightException failure) {
			throw failure;
		} catch (IOException failure) {
			throw new PreflightException(
				"Could not inspect the target repository: " + failure.getMessage(), failure);
		} catch (RuntimeException failure) {
			throw new PreflightException(
				"Target inspection failed: " + failure.getMessage(), failure);
		}
	}

	private static Path canonicalRoot(Path requestedRoot) throws IOException, PreflightException {
		if (requestedRoot == null) {
			throw new PreflightException("A repository root is required.");
		}
		Path normalized = requestedRoot.toAbsolutePath().normalize();
		if (!Files.isDirectory(normalized)) {
			throw new PreflightException("Repository root is not a directory: " + normalized);
		}
		return normalized.toRealPath();
	}

	private static Path requiredFile(Path root, String relative)
		throws IOException, PreflightException {
		Path candidate = containedPath(root, relative);
		if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
			throw new PreflightException("Required repository file is missing: " + relative);
		}
		Path real = candidate.toRealPath();
		if (!real.startsWith(root)) {
			throw new PreflightException("Repository path escapes its root: " + relative);
		}
		if (!Files.isRegularFile(real)) {
			throw new PreflightException("Expected a regular repository file: " + relative);
		}
		return real;
	}

	private static Path containedPath(Path root, String relative) throws PreflightException {
		Path candidate = root.resolve(relative).normalize();
		if (!candidate.startsWith(root)) {
			throw new PreflightException("Repository path escapes its root: " + relative);
		}
		return candidate;
	}

	private static void validateConfiguration(PreflightReport.Configuration configuration)
		throws PreflightException {
		if (!configuration.memberWorld) {
			throw new PreflightException(
				"The first layout adapter requires member_world: true.");
		}
		if (!configuration.customLandscape) {
			throw new PreflightException(
				"The first layout adapter requires custom_landscape: true.");
		}
		if (!configuration.wantMyWorld) {
			throw new PreflightException(
				"The first layout adapter requires want_myworld: true.");
		}
	}

	private static TerrainInventory validateTerrainArchive(Path archive)
		throws IOException, PreflightException {
		TerrainInventory inventory = new TerrainInventory();
		Set<String> names = new HashSet<String>();
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory()) {
					throw new PreflightException(
						"Terrain archive contains an unsupported directory entry: " + entry.getName());
				}
				Matcher matcher = TERRAIN_ENTRY.matcher(entry.getName());
				if (!matcher.matches()) {
					throw new PreflightException(
						"Terrain archive contains an unsupported entry: " + entry.getName());
				}
				if (!names.add(entry.getName())) {
					throw new PreflightException(
						"Terrain archive contains a duplicate entry: " + entry.getName());
				}
				int byteCount = countBytes(zip.getInputStream(entry), TERRAIN_ENTRY_BYTES + 1);
				if (byteCount != TERRAIN_ENTRY_BYTES) {
					throw new PreflightException(
						"Terrain entry has invalid decoded size " + byteCount + ": " + entry.getName());
				}
				inventory.include(
					Integer.parseInt(matcher.group(1)),
					Integer.parseInt(matcher.group(2)),
					Integer.parseInt(matcher.group(3)));
			}
		}
		if (inventory.sectorCount == 0) {
			throw new PreflightException("Terrain archive contains no sectors: " + archive.getFileName());
		}
		return inventory;
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

	private static List<PreflightReport.SourceFile> inventoryCandidates(Path root, Path configPath)
		throws IOException, PreflightException {
		Map<String, Candidate> candidates = new TreeMap<String, Candidate>();
		addCandidate(root, candidates, "configuration", configPath,
			Collections.singletonList("repository-config"));

		Path locs = containedPath(root, "server/conf/server/defs/locs");
		if (!Files.isDirectory(locs)) {
			throw new PreflightException(
				"Coordinate-bearing location directory is missing: server/conf/server/defs/locs");
		}
		for (Path path : regularFiles(locs, ".json")) {
			addCandidate(root, candidates, "placement", path,
				Collections.singletonList("location-data"));
		}

		Path telepoints = containedPath(root, "server/conf/server/defs/extras/ObjectTelePoints.xml");
		if (Files.exists(telepoints, LinkOption.NOFOLLOW_LINKS)) {
			addCandidate(root, candidates, "transition", requiredFile(root,
				"server/conf/server/defs/extras/ObjectTelePoints.xml"),
				Collections.singletonList("transition-data"));
		}

		for (String sourceRoot : SOURCE_ROOTS) {
			Path directory = containedPath(root, sourceRoot);
			if (!Files.isDirectory(directory)) {
				continue;
			}
			for (Path path : regularFiles(directory, ".java")) {
				List<String> signals = coordinateSignals(path);
				if (!signals.isEmpty()) {
					String relative = root.relativize(path.toRealPath())
						.toString().replace('\\', '/');
					String role = relative.startsWith(
						"server/src/com/openrsc/server/model/world/coordinate/")
						|| LAYERED_REGION_TILE_SNAPSHOT.equals(relative)
						? "server-layered-coordinate-contract"
						: sourceRoot.startsWith("Client_Base")
							? "client-coordinate-source"
							: sourceRoot.startsWith("tools/world-builder")
								? "builder-coordinate-source"
								: "server-coordinate-source";
					addCandidate(root, candidates, role, path, signals);
				}
			}
		}

		List<PreflightReport.SourceFile> result = new ArrayList<PreflightReport.SourceFile>();
		for (Candidate candidate : candidates.values()) {
			List<String> signals = new ArrayList<String>(candidate.signals);
			Collections.sort(signals);
			result.add(new PreflightReport.SourceFile(
				candidate.role,
				candidate.relativePath,
				Files.size(candidate.path),
				Hashes.sha256(candidate.path),
				signals));
		}
		return result;
	}

	private static List<Path> regularFiles(Path directory, String suffix) throws IOException {
		List<Path> result = new ArrayList<Path>();
		try (Stream<Path> paths = Files.walk(directory)) {
			paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
				.filter(path -> path.getFileName().toString().endsWith(suffix))
				.forEach(result::add);
		}
		Collections.sort(result);
		return result;
	}

	private static List<String> coordinateSignals(Path path) throws IOException {
		String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
		List<String> result = new ArrayList<String>();
		for (Map.Entry<String, List<String>> signal : JAVA_SIGNALS.entrySet()) {
			for (String needle : signal.getValue()) {
				if (text.contains(needle)) {
					result.add(signal.getKey());
					break;
				}
			}
		}
		return result;
	}

	private static void addCandidate(Path root, Map<String, Candidate> candidates,
		String role, Path path, List<String> signals) throws IOException, PreflightException {
		Path real = path.toRealPath();
		if (!real.startsWith(root)) {
			throw new PreflightException("Candidate source escapes repository root: " + path);
		}
		String relative = root.relativize(real).toString().replace('\\', '/');
		Candidate candidate = candidates.get(relative);
		if (candidate == null) {
			candidate = new Candidate(role, relative, real);
			candidates.put(relative, candidate);
		}
		candidate.signals.addAll(signals);
	}

	private static String sourceFingerprint(
		PreflightReport.Configuration configuration,
		String terrainHash,
		List<PreflightReport.SourceFile> candidates) {
		StringBuilder canonical = new StringBuilder();
		canonical.append("adapter=").append(LAYOUT_ADAPTER).append('\n');
		canonical.append("coordinateModel=").append(PreflightReport.COORDINATE_MODEL).append('\n');
		canonical.append("legacyCodec=").append(LegacyPackedCoordinateCodec.ID).append('\n');
		canonical.append("config=").append(configuration.path).append('|')
			.append(configuration.sha256).append('|')
			.append(configuration.clientVersion).append('|')
			.append(configuration.basedMapData).append('|')
			.append(configuration.memberWorld).append('|')
			.append(configuration.customLandscape).append('|')
			.append(configuration.wantMyWorld).append('\n');
		canonical.append("terrain=").append(terrainHash).append('\n');
		for (PreflightReport.SourceFile source : candidates) {
			canonical.append(source.role).append('|').append(source.path).append('|')
				.append(source.size).append('|').append(source.sha256).append('|');
			for (String signal : source.signals) {
				canonical.append(signal).append(',');
			}
			canonical.append('\n');
		}
		return Hashes.sha256(canonical.toString());
	}

	private static void verifyUnchanged(
		Path root,
		PreflightReport.Configuration configuration,
		String terrainHash,
		List<PreflightReport.SourceFile> candidates) throws IOException, PreflightException {
		if (!configuration.sha256.equals(Hashes.sha256(requiredFile(root, configuration.path)))) {
			throw new PreflightException("Configuration changed during preflight; run it again.");
		}
		if (!terrainHash.equals(Hashes.sha256(requiredFile(root, SERVER_TERRAIN)))
			|| !terrainHash.equals(Hashes.sha256(requiredFile(root, CLIENT_TERRAIN)))) {
			throw new PreflightException("Terrain archives changed during preflight; run it again.");
		}
		for (PreflightReport.SourceFile source : candidates) {
			Path path = requiredFile(root, source.path);
			if (Files.size(path) != source.size || !Hashes.sha256(path).equals(source.sha256)) {
				throw new PreflightException(
					"Candidate coordinate source changed during preflight: " + source.path);
			}
		}
	}

	private static Map<String, List<String>> javaSignals() {
		Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
		result.put("teleport-call", Arrays.asList("teleport(", ".teleport("));
		result.put("point-construction", Arrays.asList("Point.location(", "new Point("));
		result.put("area-bounds", Arrays.asList("new Area(", ".inBounds("));
		result.put("packed-floor-stride", Arrays.asList("FLOOR_OFFSET", "distanceBetweenFloors", "944"));
		result.put("terrain-section-addressing", Arrays.asList("SECTION_SIZE", "REGION_SIZE"));
		return result;
	}

	private static final class Candidate {
		final String role;
		final String relativePath;
		final Path path;
		final Set<String> signals = new HashSet<String>();

		Candidate(String role, String relativePath, Path path) {
			this.role = role;
			this.relativePath = relativePath;
			this.path = path;
		}
	}

	private static final class TerrainInventory {
		int sectorCount;
		final Map<Integer, PreflightReport.PlaneStats> planes =
			new TreeMap<Integer, PreflightReport.PlaneStats>();

		void include(int plane, int sectorX, int sectorY) {
			PreflightReport.PlaneStats stats = planes.get(plane);
			if (stats == null) {
				stats = new PreflightReport.PlaneStats();
				planes.put(plane, stats);
			}
			stats.include(sectorX, sectorY);
			sectorCount++;
		}

		boolean sameAs(TerrainInventory other) {
			if (sectorCount != other.sectorCount || !planes.keySet().equals(other.planes.keySet())) {
				return false;
			}
			for (Integer plane : planes.keySet()) {
				PreflightReport.PlaneStats left = planes.get(plane);
				PreflightReport.PlaneStats right = other.planes.get(plane);
				if (left.sectorCount != right.sectorCount
					|| left.minSectorX != right.minSectorX || left.maxSectorX != right.maxSectorX
					|| left.minSectorY != right.minSectorY || left.maxSectorY != right.maxSectorY) {
					return false;
				}
			}
			return true;
		}
	}

	private static final class Config {
		private final Map<String, String> values;

		private Config(Map<String, String> values) {
			this.values = values;
		}

		static Config read(Path path) throws IOException, PreflightException {
			Map<String, String> values = new HashMap<String, String>();
			for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
				Matcher matcher = CONFIG_VALUE.matcher(line);
				if (!matcher.matches()) {
					continue;
				}
				String key = matcher.group(1).toLowerCase(Locale.ROOT);
				String previous = values.put(key, matcher.group(2).trim());
				if (previous != null) {
					throw new PreflightException("Configuration key appears more than once: " + key);
				}
			}
			return new Config(values);
		}

		int requiredInt(String key) throws PreflightException {
			String value = required(key);
			try {
				return Integer.parseInt(value);
			} catch (NumberFormatException failure) {
				throw new PreflightException("Configuration value must be an integer: " + key);
			}
		}

		boolean requiredBoolean(String key) throws PreflightException {
			String value = required(key).toLowerCase(Locale.ROOT);
			if ("true".equals(value)) {
				return true;
			}
			if ("false".equals(value)) {
				return false;
			}
			throw new PreflightException("Configuration value must be true or false: " + key);
		}

		private String required(String key) throws PreflightException {
			String value = values.get(key);
			if (value == null || value.isEmpty()) {
				throw new PreflightException("Required configuration value is missing: " + key);
			}
			return value;
		}
	}
}
