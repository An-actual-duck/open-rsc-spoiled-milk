package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldMapSectorId;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import com.openrsc.server.model.world.coordinate.LayeredCompatibilityPointAdapter;
import java.io.IOException;
import java.nio.ByteBuffer;
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
	public static final String RLE_ENCODING = "rle-layered-sector-v1";
	public static final String RAW_ENCODING = "raw-layered-sector-v1";
	public static final String RLE_TILE_ORDER = "x-major-y-minor";
	public static final String ENTITY_PLACEMENT_ENCODING_V1 =
		"layered-entity-placements-v1";
	public static final String WORLD_PLACEMENT_ENCODING_V2 =
		"layered-world-placements-v2";
	public static final String ENTITY_PLACEMENT_ENCODING =
		WORLD_PLACEMENT_ENCODING_V2;
	public static final String RUNTIME_PROJECTION_ID =
		LayeredCompatibilityPointAdapter.NATIVE_LAYERED_PACKAGE_ID;

	private static final long MAX_JSON_BYTES = 16L * 1024L * 1024L;
	private static final int MAX_WORLD_SPACES = 128;
	private static final int MAX_LEVELS = 4096;
	private static final int MAX_TERRAIN_SECTORS = 65536;
	private static final int MAX_PLACEMENT_SETS = 4096;
	private static final int MAX_PLACEMENTS_PER_SET = 65536;
	private static final int MAX_NPC_ROAM_RADIUS = 64;
	private static final int MAX_GROUND_ITEM_RESPAWN_SECONDS = 86400;
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
	private final Map<String, NativeLayeredPlacementSet> placementSets;
	private final String manifestSha256;

	private NativeLayeredWorldPackage(
		Path packageRoot,
		String packageId,
		String packageVersion,
		int presentationChunkSize,
		Map<String, String> worldSpaceKinds,
		Set<LevelKey> levels,
		Map<WorldMapSectorId, NativeLayeredTerrainSector> terrainSectors,
		Map<String, NativeLayeredPlacementSet> placementSets,
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
		this.placementSets = Collections.unmodifiableMap(
			new LinkedHashMap<String, NativeLayeredPlacementSet>(placementSets));
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
				"terrainSectors",
				"placementSets");
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
			Set<String> payloadPaths = new HashSet<String>();
			Map<WorldMapSectorId, NativeLayeredTerrainSector> sectors =
				readTerrainSectors(
					root,
					array(manifest, "terrainSectors"),
					levels,
					payloadPaths);
			Map<String, NativeLayeredPlacementSet> placements =
				readPlacementSets(
					root,
					array(manifest, "placementSets"),
					levels,
					payloadPaths,
					sectors);

			return new NativeLayeredWorldPackage(
				root,
				packageId,
				packageVersion,
				presentationChunkSize,
				worldSpaces,
				levels,
				sectors,
				placements,
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
		readTerrainSectors(
			Path root,
			JSONArray values,
			Set<LevelKey> levels,
			Set<String> paths)
			throws IOException {
		if (values.length() < 1 || values.length() > MAX_TERRAIN_SECTORS) {
			throw new IOException(
				"terrainSectors count must be 1.." + MAX_TERRAIN_SECTORS);
		}
		Map<WorldMapSectorId, NativeLayeredTerrainSector> result =
			new LinkedHashMap<WorldMapSectorId, NativeLayeredTerrainSector>();
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
			if (!UNIFORM_ENCODING.equals(encoding)
				&& !RLE_ENCODING.equals(encoding)
				&& !RAW_ENCODING.equals(encoding)) {
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
			NativeLayeredTerrainSector sector;
			if (UNIFORM_ENCODING.equals(encoding)) {
				sector = NativeLayeredTerrainSector.uniform(
					identity,
					readUniformTile(payloadPath),
					encoding,
					relativePath,
					expectedSha256);
			} else if (RLE_ENCODING.equals(encoding)) {
				sector = NativeLayeredTerrainSector.ofTiles(
					identity,
					readRleTiles(payloadPath),
					encoding,
					relativePath,
					expectedSha256);
			} else {
				sector = NativeLayeredTerrainSector.ofTiles(
					identity,
					readRawTiles(payloadPath),
					encoding,
					relativePath,
					expectedSha256);
			}
			result.put(identity, sector);
		}
		return result;
	}

	private static Map<String, NativeLayeredPlacementSet> readPlacementSets(
		Path root,
		JSONArray values,
		Set<LevelKey> levels,
		Set<String> paths,
		Map<WorldMapSectorId, NativeLayeredTerrainSector> terrainSectors)
		throws IOException {
		if (values.length() > MAX_PLACEMENT_SETS) {
			throw new IOException(
				"placementSets count must be 0.." + MAX_PLACEMENT_SETS);
		}
		Map<String, NativeLayeredPlacementSet> result =
			new LinkedHashMap<String, NativeLayeredPlacementSet>();
		Set<String> placementIds = new HashSet<String>();
		for (int index = 0; index < values.length(); index++) {
			JSONObject value = object(values, index, "placementSets");
			exactKeys(
				value,
				"placementSets[" + index + "]",
				"id",
				"worldSpace",
				"level",
				"encoding",
				"path",
				"sha256");
			String id = matchedString(value, "id", ID);
			if (result.containsKey(id)) {
				throw new IOException("Duplicate placement-set ID: " + id);
			}
			WorldSpaceId worldSpace =
				new WorldSpaceId(matchedString(value, "worldSpace", ID));
			int level = signedInt(value, "level");
			if (!levels.contains(new LevelKey(worldSpace, level))) {
				throw new IOException(
					"placementSets[" + index
						+ "] references an undeclared level: "
						+ worldSpace + " " + level);
			}
			String encoding = matchedString(value, "encoding", ID);
			if (!ENTITY_PLACEMENT_ENCODING_V1.equals(encoding)
				&& !WORLD_PLACEMENT_ENCODING_V2.equals(encoding)) {
				throw new IOException(
					"Placement payload encoding is unsupported by this loader: "
						+ encoding);
			}
			String relativePath = safeRelativePath(string(value, "path"));
			if (!paths.add(relativePath)) {
				throw new IOException(
					"Package payload path is reused: " + relativePath);
			}
			String expectedSha256 = matchedString(value, "sha256", SHA256);
			Path payloadPath = requiredFile(root, relativePath);
			if (!expectedSha256.equals(sha256(payloadPath))) {
				throw new IOException(
					"Placement payload hash differs from manifest: " + relativePath);
			}
			NativeLayeredPlacementSet set = readPlacementSet(
				payloadPath,
				id,
				worldSpace,
				level,
				encoding,
				relativePath,
				expectedSha256,
				placementIds);
			validatePlacementTerrainCoverage(set, terrainSectors);
			result.put(id, set);
		}
		return result;
	}

	private static NativeLayeredPlacementSet readPlacementSet(
		Path path,
		String id,
		WorldSpaceId worldSpace,
		int level,
		String encoding,
		String relativePath,
		String sha256,
		Set<String> placementIds) throws IOException {
		JSONObject document = readObject(path);
		int schemaVersion = signedInt(document, "schemaVersion");
		String payloadEncoding = string(document, "encoding");
		boolean version1 =
			schemaVersion == 1
				&& ENTITY_PLACEMENT_ENCODING_V1.equals(payloadEncoding);
		boolean version2 =
			schemaVersion == 2
				&& WORLD_PLACEMENT_ENCODING_V2.equals(payloadEncoding);
		if (!version1 && !version2) {
			throw new IOException(
				"Placement schemaVersion/encoding pair is unsupported");
		}
		if (!encoding.equals(payloadEncoding)) {
			throw new IOException(
				"Placement payload encoding differs from its manifest record: "
					+ relativePath);
		}
		if (version1) {
			exactKeys(
				document,
				"entity placement set",
				"schemaVersion",
				"encoding",
				"worldSpace",
				"level",
				"npcs",
				"groundItems");
		} else {
			exactKeys(
				document,
				"world placement set",
				"schemaVersion",
				"encoding",
				"worldSpace",
				"level",
				"npcs",
				"groundItems",
				"scenery",
				"boundaries");
		}
		requireString(document, "worldSpace", worldSpace.getValue());
		requireInt(document, "level", level);
		JSONArray npcValues = array(document, "npcs");
		JSONArray itemValues = array(document, "groundItems");
		JSONArray sceneryValues = version2
			? array(document, "scenery") : new JSONArray();
		JSONArray boundaryValues = version2
			? array(document, "boundaries") : new JSONArray();
		int placementCount = Math.addExact(
			Math.addExact(npcValues.length(), itemValues.length()),
			Math.addExact(sceneryValues.length(), boundaryValues.length()));
		if (placementCount < 1
			|| npcValues.length() > MAX_PLACEMENTS_PER_SET
			|| itemValues.length() > MAX_PLACEMENTS_PER_SET
			|| sceneryValues.length() > MAX_PLACEMENTS_PER_SET
			|| boundaryValues.length() > MAX_PLACEMENTS_PER_SET
			|| placementCount > MAX_PLACEMENTS_PER_SET) {
			throw new IOException(
				"World placement set count must be 1.."
					+ MAX_PLACEMENTS_PER_SET);
		}
		java.util.List<NativeLayeredNpcPlacement> npcs =
			new java.util.ArrayList<NativeLayeredNpcPlacement>();
		for (int index = 0; index < npcValues.length(); index++) {
			JSONObject value = object(npcValues, index, "npcs");
			exactKeys(
				value,
				"npcs[" + index + "]",
				"placementId",
				"npcId",
				"start",
				"roamRadius");
			String placementId = uniquePlacementId(
				value, index, "npcs", placementIds);
			int npcId = nonNegativeInt(value, "npcId");
			int roamRadius = nonNegativeInt(value, "roamRadius");
			if (roamRadius > MAX_NPC_ROAM_RADIUS) {
				throw new IOException(
					"npcs[" + index + "].roamRadius must be 0.."
						+ MAX_NPC_ROAM_RADIUS);
			}
			npcs.add(new NativeLayeredNpcPlacement(
				placementId,
				npcId,
				readLocation(
					object(value, "start"),
					"npcs[" + index + "].start",
					worldSpace,
					level),
				roamRadius));
		}
		java.util.List<NativeLayeredGroundItemPlacement> groundItems =
			new java.util.ArrayList<NativeLayeredGroundItemPlacement>();
		for (int index = 0; index < itemValues.length(); index++) {
			JSONObject value = object(itemValues, index, "groundItems");
			exactKeys(
				value,
				"groundItems[" + index + "]",
				"placementId",
				"itemId",
				"position",
				"amount",
				"respawnSeconds");
			String placementId = uniquePlacementId(
				value, index, "groundItems", placementIds);
			int itemId = nonNegativeInt(value, "itemId");
			int amount = positiveInt(value, "amount");
			int respawnSeconds = positiveInt(value, "respawnSeconds");
			if (respawnSeconds > MAX_GROUND_ITEM_RESPAWN_SECONDS) {
				throw new IOException(
					"groundItems[" + index + "].respawnSeconds must be 1.."
						+ MAX_GROUND_ITEM_RESPAWN_SECONDS);
			}
			groundItems.add(new NativeLayeredGroundItemPlacement(
				placementId,
				itemId,
				readLocation(
					object(value, "position"),
					"groundItems[" + index + "].position",
					worldSpace,
					level),
				amount,
				respawnSeconds));
		}
		java.util.List<NativeLayeredSceneryPlacement> scenery =
			new java.util.ArrayList<NativeLayeredSceneryPlacement>();
		Set<String> scenerySlots = new HashSet<String>();
		for (int index = 0; index < sceneryValues.length(); index++) {
			JSONObject value = object(sceneryValues, index, "scenery");
			exactKeys(
				value,
				"scenery[" + index + "]",
				"placementId",
				"sceneryId",
				"position",
				"direction");
			String placementId = uniquePlacementId(
				value, index, "scenery", placementIds);
			WorldLocation location = readLocation(
				object(value, "position"),
				"scenery[" + index + "].position",
				worldSpace,
				level);
			int direction = direction(value, "direction");
			String slot = location.toString();
			if (!scenerySlots.add(slot)) {
				throw new IOException(
					"Duplicate scenery slot at " + slot);
			}
			scenery.add(new NativeLayeredSceneryPlacement(
				placementId,
				nonNegativeInt(value, "sceneryId"),
				location,
				direction));
		}
		java.util.List<NativeLayeredBoundaryPlacement> boundaries =
			new java.util.ArrayList<NativeLayeredBoundaryPlacement>();
		Set<String> boundarySlots = new HashSet<String>();
		for (int index = 0; index < boundaryValues.length(); index++) {
			JSONObject value = object(boundaryValues, index, "boundaries");
			exactKeys(
				value,
				"boundaries[" + index + "]",
				"placementId",
				"boundaryId",
				"position",
				"direction");
			String placementId = uniquePlacementId(
				value, index, "boundaries", placementIds);
			WorldLocation location = readLocation(
				object(value, "position"),
				"boundaries[" + index + "].position",
				worldSpace,
				level);
			int direction = direction(value, "direction");
			String slot = location.toString() + ":" + direction;
			if (!boundarySlots.add(slot)) {
				throw new IOException(
					"Duplicate boundary slot at " + slot);
			}
			boundaries.add(new NativeLayeredBoundaryPlacement(
				placementId,
				nonNegativeInt(value, "boundaryId"),
				location,
				direction));
		}
		return new NativeLayeredPlacementSet(
			id,
			worldSpace,
			level,
			encoding,
			relativePath,
			sha256,
			npcs,
			groundItems,
			scenery,
			boundaries);
	}

	private static String uniquePlacementId(
		JSONObject value,
		int index,
		String label,
		Set<String> placementIds) throws IOException {
		String placementId = matchedString(value, "placementId", ID);
		if (!placementIds.add(placementId)) {
			throw new IOException(
				"Duplicate package placement ID at "
					+ label + "[" + index + "]: " + placementId);
		}
		return placementId;
	}

	private static WorldLocation readLocation(
		JSONObject value,
		String label,
		WorldSpaceId worldSpace,
		int level) throws IOException {
		exactKeys(value, label, "x", "y");
		return new WorldLocation(
			worldSpace,
			new WorldCoordinate(
				signedInt(value, "x"),
				signedInt(value, "y"),
				level));
	}

	private static void validatePlacementTerrainCoverage(
		NativeLayeredPlacementSet set,
		Map<WorldMapSectorId, NativeLayeredTerrainSector> terrainSectors)
		throws IOException {
		for (NativeLayeredNpcPlacement npc : set.getNpcs()) {
			WorldCoordinate start = npc.getStart().getCoordinate();
			for (int deltaX = -npc.getRoamRadius();
				deltaX <= npc.getRoamRadius();
				deltaX++) {
				for (int deltaY = -npc.getRoamRadius();
					deltaY <= npc.getRoamRadius();
					deltaY++) {
					requirePlacementTerrain(
						new WorldLocation(
							npc.getStart().getWorldSpace(),
							new WorldCoordinate(
								Math.addExact(start.getX(), deltaX),
								Math.addExact(start.getY(), deltaY),
								start.getLevel())),
						npc.getPlacementId(),
						terrainSectors);
				}
			}
		}
		for (NativeLayeredGroundItemPlacement item : set.getGroundItems()) {
			requirePlacementTerrain(
				item.getLocation(), item.getPlacementId(), terrainSectors);
		}
		for (NativeLayeredSceneryPlacement scenery : set.getScenery()) {
			requirePlacementTerrain(
				scenery.getLocation(),
				scenery.getPlacementId(),
				terrainSectors);
		}
		for (NativeLayeredBoundaryPlacement boundary : set.getBoundaries()) {
			requirePlacementTerrain(
				boundary.getLocation(),
				boundary.getPlacementId(),
				terrainSectors);
		}
	}

	private static void requirePlacementTerrain(
		WorldLocation location,
		String placementId,
		Map<WorldMapSectorId, NativeLayeredTerrainSector> terrainSectors)
		throws IOException {
		NativeLayeredTerrainSector sector =
			terrainSectors.get(WorldMapSectorId.from(location));
		if (sector == null) {
			throw new IOException(
				"Placement has no package terrain at "
					+ placementId + ": " + location);
		}
	}

	private static NativeLayeredTerrainTile readUniformTile(Path path)
		throws IOException {
		JSONObject document = readObject(path);
		exactKeys(document, "uniform sector", "schemaVersion", "encoding", "size", "tile");
		requireInt(document, "schemaVersion", 1);
		requireString(document, "encoding", UNIFORM_ENCODING);
		requireInt(document, "size", NativeLayeredTerrainSector.SIZE);
		return readTerrainTile(object(document, "tile"), "uniform sector tile");
	}

	private static NativeLayeredTerrainTile[] readRleTiles(Path path)
		throws IOException {
		JSONObject document = readObject(path);
		exactKeys(
			document,
			"RLE sector",
			"schemaVersion",
			"encoding",
			"size",
			"tileOrder",
			"runs");
		requireInt(document, "schemaVersion", 1);
		requireString(document, "encoding", RLE_ENCODING);
		requireInt(document, "size", NativeLayeredTerrainSector.SIZE);
		requireString(document, "tileOrder", RLE_TILE_ORDER);
		JSONArray runs = array(document, "runs");
		if (runs.length() < 1 || runs.length() > NativeLayeredTerrainSector.TILE_COUNT) {
			throw new IOException(
				"RLE sector runs count must be 1.."
					+ NativeLayeredTerrainSector.TILE_COUNT);
		}

		NativeLayeredTerrainTile[] tiles =
			new NativeLayeredTerrainTile[NativeLayeredTerrainSector.TILE_COUNT];
		int expanded = 0;
		for (int index = 0; index < runs.length(); index++) {
			JSONObject run = object(runs, index, "runs");
			exactKeys(run, "runs[" + index + "]", "count", "tile");
			int count = signedInt(run, "count");
			if (count <= 0 || count > tiles.length - expanded) {
				throw new IOException(
					"runs[" + index + "].count exceeds the remaining sector capacity");
			}
			NativeLayeredTerrainTile tile =
				readTerrainTile(object(run, "tile"), "runs[" + index + "].tile");
			Arrays.fill(tiles, expanded, expanded + count, tile);
			expanded += count;
		}
		if (expanded != tiles.length) {
			throw new IOException(
				"RLE sector runs must expand to exactly " + tiles.length
					+ " tiles but expanded to " + expanded);
		}
		return tiles;
	}

	private static NativeLayeredTerrainTile[] readRawTiles(Path path)
		throws IOException {
		final int tileBytes = 10;
		final int expectedBytes =
			NativeLayeredTerrainSector.TILE_COUNT * tileBytes;
		byte[] payload = Files.readAllBytes(path);
		if (payload.length != expectedBytes) {
			throw new IOException(
				"Raw sector must contain exactly " + expectedBytes
					+ " bytes but contained " + payload.length);
		}
		ByteBuffer input = ByteBuffer.wrap(payload);
		NativeLayeredTerrainTile[] tiles =
			new NativeLayeredTerrainTile[NativeLayeredTerrainSector.TILE_COUNT];
		for (int index = 0; index < tiles.length; index++) {
			tiles[index] = new NativeLayeredTerrainTile(
				input.get() & 0xff,
				input.get() & 0xff,
				input.get() & 0xff,
				input.get() & 0xff,
				input.get() & 0xff,
				input.get() & 0xff,
				input.getInt());
		}
		if (input.hasRemaining()) {
			throw new IOException("Raw sector decoder left trailing bytes");
		}
		return tiles;
	}

	private static NativeLayeredTerrainTile readTerrainTile(
		JSONObject tile, String label) throws IOException {
		exactKeys(
			tile,
			label,
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

	public Optional<NativeLayeredTerrainChunk> findPresentationChunk(
		WorldSpaceId worldSpace,
		int level,
		int chunkX,
		int chunkY) {
		long minimumX = (long) chunkX * presentationChunkSize;
		long minimumY = (long) chunkY * presentationChunkSize;
		long maximumX = minimumX + presentationChunkSize - 1L;
		long maximumY = minimumY + presentationChunkSize - 1L;
		if (minimumX < Integer.MIN_VALUE || maximumX > Integer.MAX_VALUE
			|| minimumY < Integer.MIN_VALUE || maximumY > Integer.MAX_VALUE) {
			return Optional.empty();
		}
		int sectorX = Math.floorDiv((int) minimumX, NativeLayeredTerrainSector.SIZE);
		int sectorY = Math.floorDiv((int) minimumY, NativeLayeredTerrainSector.SIZE);
		if (sectorX != Math.floorDiv((int) maximumX, NativeLayeredTerrainSector.SIZE)
			|| sectorY
				!= Math.floorDiv((int) maximumY, NativeLayeredTerrainSector.SIZE)) {
			throw new IllegalStateException(
				"Presentation chunk crosses its 48-tile storage page");
		}
		WorldMapSectorId sectorId =
			new WorldMapSectorId(worldSpace, level, sectorX, sectorY);
		NativeLayeredTerrainSector sector = terrainSectors.get(sectorId);
		if (sector == null) {
			return Optional.empty();
		}

		int firstLocalX = Math.floorMod((int) minimumX, NativeLayeredTerrainSector.SIZE);
		int firstLocalY = Math.floorMod((int) minimumY, NativeLayeredTerrainSector.SIZE);
		NativeLayeredTerrainTile[] tiles = new NativeLayeredTerrainTile[
			presentationChunkSize * presentationChunkSize];
		for (int localX = 0; localX < presentationChunkSize; localX++) {
			for (int localY = 0; localY < presentationChunkSize; localY++) {
				tiles[localX * presentationChunkSize + localY] =
					sector.getTile(firstLocalX + localX, firstLocalY + localY);
			}
		}
		return Optional.of(new NativeLayeredTerrainChunk(
			worldSpace,
			level,
			chunkX,
			chunkY,
			presentationChunkSize,
			sectorId,
			sector.getSourceEncoding(),
			sector.getSourceSha256(),
			tiles));
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

	public int getPlacementSetCount() {
		return placementSets.size();
	}

	public int getNpcPlacementCount() {
		int count = 0;
		for (NativeLayeredPlacementSet set : placementSets.values()) {
			count = Math.addExact(count, set.getNpcs().size());
		}
		return count;
	}

	public int getGroundItemPlacementCount() {
		int count = 0;
		for (NativeLayeredPlacementSet set : placementSets.values()) {
			count = Math.addExact(count, set.getGroundItems().size());
		}
		return count;
	}

	public int getSceneryPlacementCount() {
		int count = 0;
		for (NativeLayeredPlacementSet set : placementSets.values()) {
			count = Math.addExact(count, set.getScenery().size());
		}
		return count;
	}

	public int getBoundaryPlacementCount() {
		int count = 0;
		for (NativeLayeredPlacementSet set : placementSets.values()) {
			count = Math.addExact(count, set.getBoundaries().size());
		}
		return count;
	}

	public String getManifestSha256() {
		return manifestSha256;
	}

	public Map<WorldMapSectorId, NativeLayeredTerrainSector> getTerrainSectors() {
		return terrainSectors;
	}

	public Map<String, NativeLayeredPlacementSet> getPlacementSets() {
		return placementSets;
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

	private static int nonNegativeInt(JSONObject value, String key)
		throws IOException {
		int result = signedInt(value, key);
		if (result < 0) {
			throw new IOException(key + " must be non-negative");
		}
		return result;
	}

	private static int positiveInt(JSONObject value, String key)
		throws IOException {
		int result = signedInt(value, key);
		if (result <= 0) {
			throw new IOException(key + " must be positive");
		}
		return result;
	}

	private static int direction(JSONObject value, String key)
		throws IOException {
		int result = signedInt(value, key);
		if (result < 0 || result > 7) {
			throw new IOException(key + " must be 0..7");
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
