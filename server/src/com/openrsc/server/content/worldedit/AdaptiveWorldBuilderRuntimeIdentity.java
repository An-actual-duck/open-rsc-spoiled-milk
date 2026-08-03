package com.openrsc.server.content.worldedit;

import com.openrsc.server.ServerConfiguration;
import com.openrsc.server.io.AdaptiveWorldBuilderPackageGuard;
import com.openrsc.server.io.NativeLayeredWorldPackage;
import com.openrsc.server.io.NativeLayeredTerrainSector;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Stable identities and exact session binding for the opt-in adaptive runtime. */
public final class AdaptiveWorldBuilderRuntimeIdentity {
	public static final String PROFILE_ID = "adaptive-world-builder";
	public static final String CAPABILITY_ID =
		"adaptive-world-builder-runtime-capability-v1";
	public static final String SESSION_SCHEMA =
		"adaptive-world-builder-session-v1";
	public static final String SERVER_BUILD_ID =
		"core-framework-adaptive-builder-server-v1";
	public static final String CLIENT_BUILD_ID =
		"core-framework-adaptive-builder-client-v1";
	public static final String LOADER_ID =
		"generic-signed-layered-loader-v1";
	public static final String AUTHORING_ID =
		"generic-signed-layered-authoring-v1";
	public static final String DEFINITION_CONTRACT_ID =
		"world-builder-definition-catalog-binding-v1";
	public static final String ASSET_CONTRACT_ID =
		"world-builder-client-asset-binding-v1";
	public static final String PROTOCOL_ID =
		"world-builder-native-layered-protocol-v1";
	public static final String EFFECTIVE_COMPOSITION_ID =
		"world-builder-effective-static-composition-v1";
	public static final String PACKAGE_SCHEMA_ID =
		"layered-world-package-v1";
	public static final String PLACEMENT_ENCODING_ID =
		NativeLayeredWorldPackage.WORLD_PLACEMENT_ENCODING_V3;
	public static final int CLIENT_VERSION = 10048;
	public static final String ORIGIN_ADOPTED = "target-layered";
	public static final String ORIGIN_CONVERTED = "target-packed";
	public static final String ORIGIN_EMPTY = "standalone-empty";

	private static final Pattern ID =
		Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

	private AdaptiveWorldBuilderRuntimeIdentity() {
	}

	public static boolean isAdaptive(ServerConfiguration config) {
		return config != null
			&& config.WORLD_BUILDER_ADAPTIVE_MODE
			&& PROFILE_ID.equals(config.LAYERED_NATIVE_WORLD_RUNTIME_PROFILE);
	}

	public static void validateConfiguredIdentities(ServerConfiguration config) {
		if (!isAdaptive(config)) {
			throw new IllegalArgumentException(
				"Adaptive World Builder identities require the explicit adaptive profile");
		}
		requireId("project origin", config.WORLD_BUILDER_PROJECT_ORIGIN);
		if (!ORIGIN_ADOPTED.equals(config.WORLD_BUILDER_PROJECT_ORIGIN)
			&& !ORIGIN_CONVERTED.equals(config.WORLD_BUILDER_PROJECT_ORIGIN)
			&& !ORIGIN_EMPTY.equals(config.WORLD_BUILDER_PROJECT_ORIGIN)) {
			throw new IllegalArgumentException(
				"Adaptive World Builder project origin is unsupported");
		}
		requireId("definition identity", config.WORLD_BUILDER_DEFINITION_ID);
		requireSha("definition SHA-256", config.WORLD_BUILDER_DEFINITION_SHA256);
		requireId("asset identity", config.WORLD_BUILDER_ASSET_ID);
		requireSha("asset SHA-256", config.WORLD_BUILDER_ASSET_SHA256);
		requireSha(
			"source baseline inventory SHA-256",
			config.WORLD_BUILDER_SOURCE_BASELINE_INVENTORY_SHA256);
		requireSha(
			"working package manifest SHA-256",
			config.LAYERED_NATIVE_TERRAIN_MANIFEST_SHA256);
		requireSha(
			"working package inventory SHA-256",
			config.LAYERED_NATIVE_TERRAIN_INVENTORY_SHA256);
		if (!WorldSpaceId.GLOBAL.getValue().equals(
				config.WORLD_BUILDER_INITIAL_WORLD_SPACE)) {
			throw new IllegalArgumentException(
				"Adaptive World Builder currently requires initial world space global");
		}
		if (!coordinate(config.WORLD_BUILDER_INITIAL_X)
			|| !coordinate(config.WORLD_BUILDER_INITIAL_Y)) {
			throw new IllegalArgumentException(
				"Adaptive World Builder initial coordinates must be 0..32767");
		}
		if (config.CLIENT_VERSION != CLIENT_VERSION) {
			throw new IllegalArgumentException(
				"Adaptive World Builder requires client protocol build "
					+ CLIENT_VERSION);
		}
		if (ORIGIN_EMPTY.equals(config.WORLD_BUILDER_PROJECT_ORIGIN)
			&& (config.WORLD_BUILDER_INITIAL_LEVEL != 0
				|| config.WORLD_BUILDER_INITIAL_X != 0
				|| config.WORLD_BUILDER_INITIAL_Y != 0)) {
			throw new IllegalArgumentException(
				"Standalone empty mode must begin at global layer 0, coordinate 0,0");
		}
	}

	public static WorldLocation initialLocation(ServerConfiguration config) {
		validateConfiguredIdentities(config);
		return WorldLocation.global(new WorldCoordinate(
			config.WORLD_BUILDER_INITIAL_X,
			config.WORLD_BUILDER_INITIAL_Y,
			config.WORLD_BUILDER_INITIAL_LEVEL));
	}

	public static void validateOriginPackage(
		ServerConfiguration config, NativeLayeredWorldPackage worldPackage) {
		WorldLocation initial = initialLocation(config);
		if (worldPackage == null || !worldPackage.findTile(initial).isPresent()) {
			throw new IllegalArgumentException(
				"Adaptive initial location has no package terrain");
		}
		if (!ORIGIN_EMPTY.equals(config.WORLD_BUILDER_PROJECT_ORIGIN)) {
			return;
		}
		if (worldPackage.getWorldSpaceCount() != 1
			|| worldPackage.getLevelCount() != 1
			|| worldPackage.getTerrainSectorCount() != 1
			|| worldPackage.getPlacementSetCount() != 1
			|| worldPackage.getNpcPlacementCount() != 0
			|| worldPackage.getGroundItemPlacementCount() != 0
			|| worldPackage.getSceneryPlacementCount() != 0
			|| worldPackage.getBoundaryPlacementCount() != 0
			|| !worldPackage.declaresLevel(WorldSpaceId.GLOBAL, 0)) {
			throw new IllegalArgumentException(
				"Standalone empty mode requires exactly one empty global level");
		}
		NativeLayeredTerrainSector sector = worldPackage.getTerrainSectors()
			.values().iterator().next();
		if (!WorldSpaceId.GLOBAL.equals(sector.getIdentity().getWorldSpace())
			|| sector.getIdentity().getLevel() != 0
			|| sector.getIdentity().getSectorX() != 0
			|| sector.getIdentity().getSectorY() != 0) {
			throw new IllegalArgumentException(
				"Standalone empty mode requires global level 0 sector 0,0");
		}
		for (int x = 0; x < NativeLayeredTerrainSector.SIZE; x++) {
			for (int y = 0; y < NativeLayeredTerrainSector.SIZE; y++) {
				if (!sector.getTile(x, y).isWorldBuilderVoid()) {
					throw new IllegalArgumentException(
						"Standalone empty terrain differs from the canonical void tile");
				}
			}
		}
	}

	public static Map<String, String> bindingFields(
		ServerConfiguration config,
		NativeLayeredWorldPackage worldPackage,
		AdaptiveWorldBuilderPackageGuard.Inventory inventory,
		String effectiveCompositionSha256,
		String requiredTileIds,
		String requiredBoundaryIds,
		String requiredSceneryIds,
		String requiredNpcIds,
		String requiredItemIds) {
		validateConfiguredIdentities(config);
		if (worldPackage == null || inventory == null) {
			throw new IllegalArgumentException(
				"Adaptive World Builder binding requires a validated package");
		}
		requireSha("effective composition SHA-256", effectiveCompositionSha256);
		Map<String, String> fields = new TreeMap<String, String>();
		fields.put("assetContract", ASSET_CONTRACT_ID);
		fields.put("assetIdentity", config.WORLD_BUILDER_ASSET_ID);
		fields.put("assetSha256", config.WORLD_BUILDER_ASSET_SHA256);
		fields.put("authoring", AUTHORING_ID);
		fields.put("capability", CAPABILITY_ID);
		fields.put("clientBuild", CLIENT_BUILD_ID);
		fields.put("clientVersion", Integer.toString(CLIENT_VERSION));
		fields.put("coordinateModel", NativeLayeredWorldPackage.COORDINATE_MODEL);
		fields.put("definitionContract", DEFINITION_CONTRACT_ID);
		fields.put("definitionIdentity", config.WORLD_BUILDER_DEFINITION_ID);
		fields.put("definitionSha256", config.WORLD_BUILDER_DEFINITION_SHA256);
		fields.put("effectiveComposition", EFFECTIVE_COMPOSITION_ID);
		fields.put("effectiveCompositionSha256", effectiveCompositionSha256);
		fields.put("initialLevel", Integer.toString(config.WORLD_BUILDER_INITIAL_LEVEL));
		fields.put("initialWorldSpace", config.WORLD_BUILDER_INITIAL_WORLD_SPACE);
		fields.put("initialX", Integer.toString(config.WORLD_BUILDER_INITIAL_X));
		fields.put("initialY", Integer.toString(config.WORLD_BUILDER_INITIAL_Y));
		fields.put("loader", LOADER_ID);
		fields.put("levels", globalLevels(worldPackage));
		fields.put("manifestSha256", worldPackage.getManifestSha256());
		fields.put("packageId", worldPackage.getPackageId());
		fields.put("packageInventorySha256", inventory.getFingerprint());
		fields.put("packageSchema", PACKAGE_SCHEMA_ID);
		fields.put("packageVersion", worldPackage.getPackageVersion());
		fields.put("placementEncoding", PLACEMENT_ENCODING_ID);
		fields.put("profile", PROFILE_ID);
		fields.put("projectOrigin", config.WORLD_BUILDER_PROJECT_ORIGIN);
		fields.put("protocol", PROTOCOL_ID);
		fields.put("requiredBoundaryIds", checkedIdList(requiredBoundaryIds));
		fields.put("requiredItemIds", checkedIdList(requiredItemIds));
		fields.put("requiredNpcIds", checkedIdList(requiredNpcIds));
		fields.put("requiredSceneryIds", checkedIdList(requiredSceneryIds));
		fields.put("requiredTileIds", checkedIdList(requiredTileIds));
		fields.put("serverBuild", SERVER_BUILD_ID);
		fields.put(
			"sourceBaselineInventorySha256",
			config.WORLD_BUILDER_SOURCE_BASELINE_INVENTORY_SHA256);
		return Collections.unmodifiableMap(
			new LinkedHashMap<String, String>(fields));
	}

	private static String globalLevels(NativeLayeredWorldPackage worldPackage) {
		List<Integer> levels = new ArrayList<Integer>();
		for (NativeLayeredWorldPackage.LevelDeclaration level
			: worldPackage.getLevelDeclarations()) {
			if (!WorldSpaceId.GLOBAL.equals(level.getWorldSpace())) {
				throw new IllegalArgumentException(
					"Adaptive World Builder supports only the global world space");
			}
			levels.add(Integer.valueOf(level.getLevel()));
		}
		Collections.sort(levels);
		StringBuilder result = new StringBuilder();
		for (Integer level : levels) {
			if (result.length() > 0) result.append(',');
			result.append(level.intValue());
		}
		if (result.length() == 0) {
			throw new IllegalArgumentException(
				"Adaptive World Builder package has no global levels");
		}
		return result.toString();
	}

	public static String canonicalSession(Map<String, String> fields) {
		StringBuilder value = new StringBuilder();
		value.append(SESSION_SCHEMA).append('\n');
		for (Map.Entry<String, String> entry
			: new TreeMap<String, String>(fields).entrySet()) {
			if (entry.getKey().indexOf('=') >= 0 || entry.getKey().indexOf('\n') >= 0
				|| entry.getValue() == null || entry.getValue().indexOf('\n') >= 0
				|| entry.getValue().indexOf('\r') >= 0) {
				throw new IllegalArgumentException(
					"Adaptive World Builder session field is not canonical");
			}
			value.append(entry.getKey()).append('=').append(entry.getValue())
				.append('\n');
		}
		return value.toString();
	}

	public static String fingerprint(Map<String, String> fields) {
		return sha256(canonicalSession(fields));
	}

	private static String checkedIdList(String value) {
		String checked = value == null ? "" : value;
		if (!checked.isEmpty() && !checked.matches("[0-9]+(?:,[0-9]+)*")) {
			throw new IllegalArgumentException(
				"Adaptive World Builder definition ID inventory is invalid");
		}
		return checked;
	}

	private static void requireId(String label, String value) {
		if (value == null || !ID.matcher(value).matches()) {
			throw new IllegalArgumentException(
				"Adaptive World Builder " + label + " is invalid");
		}
	}

	private static void requireSha(String label, String value) {
		if (value == null || !SHA256.matcher(value).matches()) {
			throw new IllegalArgumentException(
				"Adaptive World Builder " + label + " is invalid");
		}
	}

	private static boolean coordinate(int value) {
		return value >= 0 && value <= 32767;
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(value.getBytes(StandardCharsets.US_ASCII));
			StringBuilder result = new StringBuilder(64);
			for (byte part : hashed) {
				result.append(String.format("%02x", part & 0xff));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}
}
