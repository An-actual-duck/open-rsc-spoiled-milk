package com.openrsc.worldbuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exact target path, capability, and configuration contract for layered imports. */
final class WorldBuilderLayeredImportConfiguration {
	static final String IMPORT_MODE = "layered-package-v1";
	static final String TARGET_PACKAGE_ROOT =
		"server/conf/server/data/world-builder-layered/package";
	static final String CONFIGURED_PACKAGE_PATH =
		"conf/server/data/world-builder-layered/package";
	static final String RUNTIME_PROFILE = "spoiled-milk-world-builder-export";
	static final String CAPABILITY_MARKER =
		"server/world-builder-layered-import-v1.marker";
	private static final String CAPABILITY_CONTENT =
		"spoiled-milk-world-builder-layered-import-v1\n"
			+ "runtime-profile=spoiled-milk-world-builder-export\n"
			+ "manifest-pin=layered_native_terrain_manifest_sha256\n";
	private static final Pattern CONFIG_LINE = Pattern.compile(
		"^(\\s*)([A-Za-z0-9_]+)\\s*:\\s*([^#]*?)(\\s*(?:#.*)?)$");

	private WorldBuilderLayeredImportConfiguration() {
	}

	static Prepared prepare(Path targetRoot, Path configPath, String manifestSha256)
		throws IOException, WorldBuilderDiscoveryException {
		if (manifestSha256 == null || !manifestSha256.matches("[0-9a-f]{64}")) {
			throw new WorldBuilderDiscoveryException(
				"Layered export manifest SHA-256 is invalid.");
		}
		verifyCapability(targetRoot);
		List<String> source = Files.readAllLines(configPath, StandardCharsets.UTF_8);
		LinkedHashMap<String, String> overrides = overrides(manifestSha256);
		LinkedHashMap<String, String> before = new LinkedHashMap<String, String>();
		for (String line : source) {
			Matcher matcher = CONFIG_LINE.matcher(line);
			if (!matcher.matches() || !overrides.containsKey(matcher.group(2))) {
				continue;
			}
			String key = matcher.group(2);
			if (before.put(key, matcher.group(3).trim()) != null) {
				throw new WorldBuilderDiscoveryException(
					"Selected configuration contains duplicate layered runtime key: " + key);
			}
		}
		List<String> rendered = WorldBuilderConfigWriter.render(
			source, overrides, "# Installed by Spoiled Milk World Builder 2");
		String separator = System.lineSeparator();
		byte[] bytes = (String.join(separator, rendered) + separator)
			.getBytes(StandardCharsets.UTF_8);
		List<Change> changes = new ArrayList<Change>();
		for (Map.Entry<String, String> override : overrides.entrySet()) {
			String oldValue = before.containsKey(override.getKey())
				? before.get(override.getKey()) : "<absent>";
			if (!override.getValue().equals(oldValue)) {
				changes.add(new Change(override.getKey(), oldValue, override.getValue()));
			}
		}
		return new Prepared(bytes, WorldBuilderHashes.sha256(bytes), changes);
	}

	static void verifyInstalled(Path targetRoot, Path configPath,
		String manifestSha256, String expectedFileSha256)
		throws IOException, WorldBuilderDiscoveryException {
		verifyCapability(targetRoot);
		if (!Files.isRegularFile(configPath, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(configPath)
			|| !expectedFileSha256.equals(WorldBuilderHashes.sha256(configPath))) {
			throw new WorldBuilderDiscoveryException(
				"Installed layered target configuration did not verify.");
		}
		LinkedHashMap<String, String> expected = overrides(manifestSha256);
		LinkedHashMap<String, String> found = new LinkedHashMap<String, String>();
		for (String line : Files.readAllLines(configPath, StandardCharsets.UTF_8)) {
			Matcher matcher = CONFIG_LINE.matcher(line);
			if (!matcher.matches() || !expected.containsKey(matcher.group(2))) continue;
			if (found.put(matcher.group(2), matcher.group(3).trim()) != null) {
				throw new WorldBuilderDiscoveryException(
					"Installed layered target configuration contains a duplicate key.");
			}
		}
		if (!expected.equals(found)) {
			throw new WorldBuilderDiscoveryException(
				"Installed layered target configuration is incomplete or changed.");
		}
	}

	private static LinkedHashMap<String, String> overrides(String manifestSha256) {
		LinkedHashMap<String, String> values = new LinkedHashMap<String, String>();
		values.put("want_sync_scene_baseline", "true");
		values.put("want_layered_player_location_authority", "true");
		values.put("want_layered_spatial_runtime_authority", "true");
		values.put("want_layered_protocol_client_authority", "true");
		values.put("want_layered_synthetic_deep_fixture", "false");
		values.put("want_layered_native_terrain_package", "true");
		values.put("want_layered_native_terrain_residency", "true");
		values.put("want_layered_native_terrain_readiness", "true");
		values.put("want_layered_native_terrain_prediction", "true");
		values.put("want_layered_native_terrain_symmetric_residency", "true");
		values.put("want_layered_native_terrain_atomic_activation", "true");
		values.put("layered_native_terrain_package_path", CONFIGURED_PACKAGE_PATH);
		values.put("layered_native_terrain_manifest_sha256", manifestSha256);
		values.put("layered_native_world_runtime_profile", RUNTIME_PROFILE);
		return values;
	}

	private static void verifyCapability(Path targetRoot)
		throws IOException, WorldBuilderDiscoveryException {
		Path marker = targetRoot.resolve(CAPABILITY_MARKER).normalize();
		if (!marker.startsWith(targetRoot)
			|| !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(marker)
			|| !CAPABILITY_CONTENT.equals(new String(
				Files.readAllBytes(marker), StandardCharsets.US_ASCII))) {
			throw new WorldBuilderDiscoveryException(
				"Target does not advertise layered World Builder import support. "
					+ "Use the matching Spoiled Milk private-server release.");
		}
	}

	static final class Prepared {
		final byte[] bytes;
		final String sha256;
		final List<Change> changes;

		Prepared(byte[] bytes, String sha256, List<Change> changes) {
			this.bytes = bytes;
			this.sha256 = sha256;
			this.changes = java.util.Collections.unmodifiableList(
				new ArrayList<Change>(changes));
		}
	}

	static final class Change {
		final String key;
		final String before;
		final String after;

		Change(String key, String before, String after) {
			this.key = key;
			this.before = before;
			this.after = after;
		}
	}
}
