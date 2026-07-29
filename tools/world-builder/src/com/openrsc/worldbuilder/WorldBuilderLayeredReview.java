package com.openrsc.worldbuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Strict reader for an optional prepared layered review workspace. */
final class WorldBuilderLayeredReview {
	final String packageId;
	final String packageVersion;
	final String manifestSha256;
	final String packageFingerprintSha256;
	final String worldSpace;
	final List<Integer> levels;
	final int terrainSectorCount;
	final int placementSetCount;

	private WorldBuilderLayeredReview(
		String packageId,
		String packageVersion,
		String manifestSha256,
		String packageFingerprintSha256,
		String worldSpace,
		List<Integer> levels,
		int terrainSectorCount,
		int placementSetCount) {
		this.packageId = packageId;
		this.packageVersion = packageVersion;
		this.manifestSha256 = manifestSha256;
		this.packageFingerprintSha256 = packageFingerprintSha256;
		this.worldSpace = worldSpace;
		this.levels = levels;
		this.terrainSectorCount = terrainSectorCount;
		this.placementSetCount = placementSetCount;
	}

	static WorldBuilderLayeredReview readIfPresent(Path workspace)
		throws IOException, WorldBuilderDiscoveryException {
		Path path = workspace.resolve(WorldBuilderRuntimePreparer.LAYERED_REVIEW_METADATA);
		if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			return null;
		}
		Map<String,Object> root = WorldBuilderJsonDocuments.readObject(path);
		exactKeys(root, "schemaVersion", "reviewMode", "adapter", "runtimeProfile",
			"packageId", "packageVersion", "manifestSha256",
			"packageFingerprintSha256", "worldSpace", "levels",
			"terrainSectorCount", "placementSetCount");
		if (integer(root, "schemaVersion") != 1
			|| !"read-only".equals(string(root, "reviewMode"))
			|| !WorldBuilderLayeredPackage.ADAPTER_ID.equals(string(root, "adapter"))
			|| !WorldBuilderLayeredPackage.PROFILE_ID.equals(
				string(root, "runtimeProfile"))
			|| !WorldBuilderLayeredPackage.PACKAGE_ID.equals(string(root, "packageId"))
			|| !WorldBuilderLayeredPackage.PACKAGE_VERSION.equals(
				string(root, "packageVersion"))
			|| !WorldBuilderLayeredPackage.MANIFEST_SHA256.equals(
				hash(root, "manifestSha256"))
			|| !"global".equals(string(root, "worldSpace"))) {
			throw new WorldBuilderDiscoveryException(
				"Prepared layered review metadata identity is invalid.");
		}
		String fingerprint = hash(root, "packageFingerprintSha256");
		List<Integer> levels = integerArray(root, "levels");
		if (!levels.equals(Arrays.asList(
				Integer.valueOf(-2), Integer.valueOf(-1),
				Integer.valueOf(0),
				Integer.valueOf(1), Integer.valueOf(2),
				Integer.valueOf(10)))
			|| integer(root, "terrainSectorCount") != 1782
			|| integer(root, "placementSetCount") != 6) {
			throw new WorldBuilderDiscoveryException(
				"Prepared layered review metadata counts or levels are invalid.");
		}
		Path sourcePackage =
			workspace.resolve(WorldBuilderRuntimePreparer.LAYERED_SOURCE_PACKAGE)
				.normalize();
		if (!sourcePackage.startsWith(workspace)
			|| !Files.isDirectory(sourcePackage, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(sourcePackage)) {
			throw new WorldBuilderDiscoveryException(
				"Prepared layered source package is missing or unsafe.");
		}
		WorldBuilderLayeredPackage accepted =
			WorldBuilderLayeredPackage.discover(
				sourcePackage, WorldBuilderLayeredPackage.PROFILE_ID);
		if (!fingerprint.equals(accepted.packageFingerprintSha256)) {
			throw new WorldBuilderDiscoveryException(
				"Prepared layered source package fingerprint changed.");
		}
		Path workingPackage =
			workspace.resolve(WorldBuilderRuntimePreparer.LAYERED_WORKING_PACKAGE)
				.normalize();
		if (!workingPackage.startsWith(workspace)
			|| !Files.isDirectory(workingPackage, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(workingPackage)) {
			throw new WorldBuilderDiscoveryException(
				"Prepared layered review package is missing or unsafe.");
		}
		WorldBuilderLayeredPackage verified =
			WorldBuilderLayeredPackage.discoverDraft(workingPackage);
		verified.requireFirstDraftDescendant(accepted);
		return new WorldBuilderLayeredReview(
			verified.packageId,
			verified.packageVersion,
			verified.manifestSha256,
			verified.packageFingerprintSha256,
			verified.worldSpace,
			verified.levels,
			verified.terrainSectorCount,
			verified.placementSetCount);
	}

	String levelsProperty() {
		StringBuilder value = new StringBuilder();
		for (int index = 0; index < levels.size(); index++) {
			if (index > 0) value.append(',');
			value.append(levels.get(index).intValue());
		}
		return value.toString();
	}

	boolean hasBuilderCreatedLevels() {
		for (Integer level : levels) {
			int value = level.intValue();
			if (value != -1 && value != 0 && value != 1
				&& value != 2 && value != 10) {
				return true;
			}
		}
		return false;
	}

	private static void exactKeys(Map<String,Object> object, String... names)
		throws WorldBuilderDiscoveryException {
		java.util.Set<String> expected =
			new java.util.HashSet<String>(Arrays.asList(names));
		if (!object.keySet().equals(expected)) {
			throw new WorldBuilderDiscoveryException(
				"Prepared layered review metadata contains missing or unexpected fields.");
		}
	}

	private static String string(Map<String,Object> object, String key)
		throws WorldBuilderDiscoveryException {
		Object value = object.get(key);
		if (!(value instanceof String)) {
			throw new WorldBuilderDiscoveryException(
				"Prepared layered review field is not a string: " + key);
		}
		return (String)value;
	}

	private static String hash(Map<String,Object> object, String key)
		throws WorldBuilderDiscoveryException {
		String value = string(object, key);
		if (!value.matches("[0-9a-f]{64}")) {
			throw new WorldBuilderDiscoveryException(
				"Prepared layered review hash is invalid: " + key);
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
				"Prepared layered review field is not a 32-bit integer: " + key);
		}
		return ((Long)value).intValue();
	}

	private static List<Integer> integerArray(Map<String,Object> object, String key)
		throws WorldBuilderDiscoveryException {
		Object value = object.get(key);
		if (!(value instanceof List)) {
			throw new WorldBuilderDiscoveryException(
				"Prepared layered review levels are invalid.");
		}
		java.util.ArrayList<Integer> result = new java.util.ArrayList<Integer>();
		for (Object item : (List<?>)value) {
			if (!(item instanceof Long)
				|| ((Long)item).longValue() < Integer.MIN_VALUE
				|| ((Long)item).longValue() > Integer.MAX_VALUE) {
				throw new WorldBuilderDiscoveryException(
					"Prepared layered review level is invalid.");
			}
			result.add(Integer.valueOf(((Long)item).intValue()));
		}
		return java.util.Collections.unmodifiableList(result);
	}
}
