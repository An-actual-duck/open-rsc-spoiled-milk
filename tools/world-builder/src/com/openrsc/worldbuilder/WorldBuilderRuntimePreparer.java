package com.openrsc.worldbuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Creates a new isolated Builder runtime without writing to the discovered target. */
public final class WorldBuilderRuntimePreparer {
	public static final String WORKING_DIRECTORY = "working";
	public static final String SOURCE_DIRECTORY = "source";
	public static final String SOURCE_INVENTORY = "source-snapshot.sha256";
	public static final String GENERATED_CONFIG = "working/server/world-builder.conf";
	public static final String BUILDER_DATABASE = "working/server/inc/sqlite/world_builder.db";
	public static final String BUILDER_CREDENTIAL = "working/server/inc/sqlite/world-builder.credential";
	public static final String LAYERED_REVIEW_METADATA = "layered-review.json";
	public static final String LAYERED_WORKING_PACKAGE = "working/layered-world/package";
	public static final String LAYERED_SOURCE_PACKAGE = "source/layered-world/package";

	private static final List<String> SERVER_FILES = Arrays.asList(
		"server/core.jar",
		"server/plugins.jar",
		"server/alertwords.txt",
		"server/badwords.txt",
		"server/goodwords.txt",
		"server/globalrules.txt",
		"server/ipbans.txt"
	);
	private static final List<String> SERVER_DIRECTORIES = Arrays.asList(
		"server/lib",
		"server/conf",
		"server/database"
	);
	private static final List<String> CLIENT_FILES = Arrays.asList(
		"Client_Base/Open_RSC_Client.jar"
	);
	private static final List<String> CLIENT_DIRECTORIES = Arrays.asList(
		"Client_Base/Cache"
	);

	public PreparedRuntime prepare(Path targetRoot, Path runtimeRoot, Path requestedWorkspace,
		int port, WorldBuilderDiscoveryResult source, WorldBuilderDiscoveryResult runtime)
		throws IOException, WorldBuilderDiscoveryException {
		return prepare(
			targetRoot, runtimeRoot, requestedWorkspace, port, source, runtime, null);
	}

	public PreparedRuntime prepare(Path targetRoot, Path runtimeRoot, Path requestedWorkspace,
		int port, WorldBuilderDiscoveryResult source, WorldBuilderDiscoveryResult runtime,
		WorldBuilderLayeredPackage layered)
		throws IOException, WorldBuilderDiscoveryException {
		if (port < 1 || port >= 65535) {
			throw new WorldBuilderDiscoveryException("Builder port must be between 1 and 65534.");
		}
		if (!source.contentFingerprintSha256.equals(runtime.contentFingerprintSha256)) {
			throw new WorldBuilderDiscoveryException(
				"Target definitions do not match this World Builder runtime.");
		}

		Path target = canonicalDirectory(targetRoot, "private-server root");
		Path release = canonicalDirectory(runtimeRoot, "World Builder runtime root");
		Path workspace = requestedWorkspace.toAbsolutePath().normalize();
		validateProjectName(workspace);
		if (Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)) {
			throw new WorldBuilderDiscoveryException(
				"Builder workspace already exists; existing projects are never replaced implicitly: " + workspace);
		}
		Path parent = workspace.getParent();
		if (parent == null) {
			throw new WorldBuilderDiscoveryException("Builder workspace must have a parent directory.");
		}
		Files.createDirectories(parent);
		Path stage = parent.resolve("." + workspace.getFileName() + ".staging-" + UUID.randomUUID());

		try {
			Files.createDirectory(stage);
			Path working = stage.resolve(WORKING_DIRECTORY);
			Path sourceSnapshot = stage.resolve(SOURCE_DIRECTORY);
			Files.createDirectories(working);
			Files.createDirectories(sourceSnapshot);
			for (String relative : SERVER_DIRECTORIES) {
				copyTree(requiredDirectory(release, relative), working.resolve(relative));
			}
			for (String relative : CLIENT_DIRECTORIES) {
				copyTree(requiredDirectory(release, relative), working.resolve(relative));
			}
			for (String generatedClientState : Arrays.asList(
				"Client_Base/Cache/credentials.txt",
				"Client_Base/Cache/uid.dat",
				"Client_Base/Cache/ip.txt",
				"Client_Base/Cache/port.txt",
				"Client_Base/Cache/discord_inuse.txt")) {
				Files.deleteIfExists(working.resolve(generatedClientState));
			}
			for (String relative : SERVER_FILES) {
				copyFile(requiredFile(release, relative), working.resolve(relative));
			}
			for (String relative : CLIENT_FILES) {
				copyFile(requiredFile(release, relative), working.resolve(relative));
			}
			Files.write(working.resolve("server/connections.conf"),
				"db_type: sqlite\n".getBytes(StandardCharsets.UTF_8));

			Path seed = requiredFile(release, "server/inc/sqlite/myworld_seed.db");
			copyFile(seed, stage.resolve(BUILDER_DATABASE));
			Files.deleteIfExists(stage.resolve(BUILDER_CREDENTIAL));

			for (WorldBuilderDiscoveryResult.SourceFile file : source.files) {
				Path destination = working.resolve(file.relativePath).normalize();
				Path snapshot = sourceSnapshot.resolve(file.relativePath).normalize();
				ensureContained(working, destination, file.relativePath);
				ensureContained(sourceSnapshot, snapshot, file.relativePath);
				if (!file.present) {
					Files.deleteIfExists(destination);
					continue;
				}
				Path sourceFile = requiredFile(target, file.relativePath);
				if (Files.size(sourceFile) != file.size
					|| !WorldBuilderHashes.sha256(sourceFile).equals(file.sha256)) {
					throw new WorldBuilderDiscoveryException(
						"Target world file changed during workspace preparation: " + file.relativePath);
				}
				copyFile(sourceFile, destination);
				copyFile(sourceFile, snapshot);
				if (!WorldBuilderHashes.sha256(destination).equals(file.sha256)) {
					throw new WorldBuilderDiscoveryException(
						"Workspace copy verification failed: " + file.relativePath);
				}
				if (!WorldBuilderHashes.sha256(snapshot).equals(file.sha256)) {
					throw new WorldBuilderDiscoveryException(
						"Source snapshot verification failed: " + file.relativePath);
				}
			}
			if (layered != null) {
				Path workingPackage = stage.resolve(LAYERED_WORKING_PACKAGE);
				Path sourcePackage = stage.resolve(LAYERED_SOURCE_PACKAGE);
				copyTree(layered.root, workingPackage);
				copyTree(layered.root, sourcePackage);
				for (WorldBuilderLayeredPackage.FileRecord file : layered.files) {
					Path workingFile = requiredFile(workingPackage, file.relativePath);
					Path sourceFile = requiredFile(sourcePackage, file.relativePath);
					if (Files.size(workingFile) != file.size
						|| Files.size(sourceFile) != file.size
						|| !file.sha256.equals(WorldBuilderHashes.sha256(workingFile))
						|| !file.sha256.equals(WorldBuilderHashes.sha256(sourceFile))) {
						throw new WorldBuilderDiscoveryException(
							"Layered package workspace copy verification failed: "
								+ file.relativePath);
					}
				}
				byte[] layeredMetadata =
					layered.toMetadataJson().getBytes(StandardCharsets.UTF_8);
				Files.write(stage.resolve(LAYERED_REVIEW_METADATA), layeredMetadata);
				Files.write(sourceSnapshot.resolve(LAYERED_REVIEW_METADATA), layeredMetadata);
			}

			Path selectedConfig = requiredFile(target, source.selectedConfig);
			if (!WorldBuilderHashes.sha256(selectedConfig).equals(source.selectedConfigSha256)) {
				throw new WorldBuilderDiscoveryException(
					"Selected configuration changed during workspace preparation.");
			}
			copyFile(selectedConfig, sourceSnapshot.resolve(source.selectedConfig));
			Path generatedConfig = stage.resolve(GENERATED_CONFIG);
			Files.createDirectories(generatedConfig.getParent());
			WorldBuilderConfigWriter.write(
				selectedConfig, generatedConfig, overrides(port, workspace, layered));

			Files.createDirectories(stage.resolve("logs"));
			Files.createDirectories(stage.resolve("run"));
			byte[] projectManifest = source.toJson().getBytes(StandardCharsets.UTF_8);
			Files.write(stage.resolve("project-source.json"), projectManifest);
			Files.write(sourceSnapshot.resolve("project-source.json"), projectManifest);
			Files.write(stage.resolve(SOURCE_INVENTORY),
				sourceInventory(source, projectManifest, layered)
					.getBytes(StandardCharsets.UTF_8));
			Files.write(stage.resolve("runtime.json"), runtimeJson(port, source).getBytes(StandardCharsets.UTF_8));

			try {
				Files.move(stage, workspace, StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
				Files.move(stage, workspace);
			}
			return new PreparedRuntime(workspace, port, source.sourceFingerprintSha256);
		} catch (IOException failure) {
			deleteTree(stage);
			throw failure;
		} catch (WorldBuilderDiscoveryException failure) {
			deleteTree(stage);
			throw failure;
		} catch (RuntimeException failure) {
			deleteTree(stage);
			throw failure;
		}
	}

	private static LinkedHashMap<String, String> overrides(
		int port, Path workspace, WorldBuilderLayeredPackage layered) {
		LinkedHashMap<String, String> values = new LinkedHashMap<String, String>();
		String productName = layered == null
			? "Spoiled Milk World Builder"
			: "Spoiled Milk World Builder 2";
		values.put("world_builder_mode", "true");
		values.put("server_name", productName);
		values.put("server_name_welcome", productName);
		values.put("welcome_text", "Local isolated World Builder");
		values.put("server_bind_address", "127.0.0.1");
		values.put("server_port", Integer.toString(port));
		values.put("ws_server_port", Integer.toString(port + 1));
		values.put("want_feature_websockets", "false");
		values.put("db_name", "world_builder");
		values.put("max_players", "1");
		values.put("max_players_per_ip", "1");
		values.put("want_packet_register", "false");
		values.put("allow_in_game_world_editor", "true");
		values.put("custom_landscape", "true");
		values.put("want_myworld", "true");
		values.put("is_localhost_restricted", "true");
		values.put("want_pcap_logging", "false");
		values.put("avatar_generator", "false");
		values.put("monitor_online", "false");
		values.put("monitor_automatic_shutdown", "false");
		values.put("want_auto_server_shutdown", "false");
		values.put("want_discord_auction_updates", "false");
		values.put("want_discord_monitoring_updates", "false");
		values.put("want_discord_staff_commands", "false");
		values.put("want_discord_report_abuse_updates", "false");
		values.put("want_discord_naughty_words_updates", "false");
		values.put("want_discord_general_logging", "false");
		values.put("want_discord_bot", "false");
		values.put("want_discord_downtime_reports", "false");
		if (layered != null) {
			values.put("world_builder_layered_review_mode", "true");
			values.put("want_layered_player_location_authority", "true");
			values.put("want_layered_spatial_runtime_authority", "true");
			values.put("want_layered_protocol_client_authority", "true");
			values.put("want_layered_synthetic_deep_fixture", "false");
			values.put("want_layered_native_terrain_package", "true");
			values.put(
				"layered_native_terrain_package_path",
				workspace.resolve(LAYERED_WORKING_PACKAGE)
					.toAbsolutePath().normalize().toString());
			values.put(
				"layered_native_world_runtime_profile",
				WorldBuilderLayeredPackage.BUILDER_DRAFT_PROFILE_ID);
		}
		return values;
	}

	private static void validateProjectName(Path workspace) throws WorldBuilderDiscoveryException {
		Path fileName = workspace.getFileName();
		String name = fileName == null ? "" : fileName.toString().trim();
		if (name.isEmpty() || name.length() > 64) {
			throw new WorldBuilderDiscoveryException(
				"Builder project folder name must contain 1 to 64 characters.");
		}
		for (int index = 0; index < name.length(); index++) {
			if (Character.isISOControl(name.charAt(index))) {
				throw new WorldBuilderDiscoveryException(
					"Builder project folder name cannot contain control characters.");
			}
		}
	}

	private static String runtimeJson(int port, WorldBuilderDiscoveryResult source) {
		return "{\n"
			+ "  \"schemaVersion\": 1,\n"
			+ "  \"runtimeType\": \"isolated-world-builder\",\n"
			+ "  \"host\": \"127.0.0.1\",\n"
			+ "  \"port\": " + port + ",\n"
			+ "  \"sourceFingerprintSha256\": \"" + source.sourceFingerprintSha256 + "\"\n"
			+ "}\n";
	}

	private static String sourceInventory(
		WorldBuilderDiscoveryResult source,
		byte[] projectManifest,
		WorldBuilderLayeredPackage layered) {
		StringBuilder inventory = new StringBuilder(1024);
		inventory.append("world-builder-source-v1\n");
		for (WorldBuilderDiscoveryResult.SourceFile file : source.files) {
			inventory.append(file.present ? file.sha256 : "-").append('\t')
				.append(file.relativePath).append('\n');
		}
		inventory.append(source.selectedConfigSha256).append('\t')
			.append(source.selectedConfig).append('\n');
		inventory.append(WorldBuilderHashes.sha256(projectManifest)).append('\t')
			.append("project-source.json\n");
		if (layered != null) {
			for (WorldBuilderLayeredPackage.FileRecord file : layered.files) {
				inventory.append(file.sha256).append('\t')
					.append("layered-world/package/")
					.append(file.relativePath).append('\n');
			}
			byte[] metadata =
				layered.toMetadataJson().getBytes(StandardCharsets.UTF_8);
			inventory.append(WorldBuilderHashes.sha256(metadata)).append('\t')
				.append(LAYERED_REVIEW_METADATA).append('\n');
		}
		return inventory.toString();
	}

	private static Path canonicalDirectory(Path requested, String label)
		throws IOException, WorldBuilderDiscoveryException {
		Path normalized = requested.toAbsolutePath().normalize();
		if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
			throw new WorldBuilderDiscoveryException(label + " is not a directory: " + normalized);
		}
		return normalized.toRealPath();
	}

	private static Path requiredDirectory(Path root, String relative)
		throws IOException, WorldBuilderDiscoveryException {
		Path path = root.resolve(relative).normalize();
		ensureContained(root, path, relative);
		if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
			throw new WorldBuilderDiscoveryException("World Builder runtime directory is missing: " + relative);
		}
		return path;
	}

	private static Path requiredFile(Path root, String relative)
		throws IOException, WorldBuilderDiscoveryException {
		Path path = root.resolve(relative).normalize();
		ensureContained(root, path, relative);
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
			throw new WorldBuilderDiscoveryException("Required file is missing or unsafe: " + relative);
		}
		return path;
	}

	private static void ensureContained(Path root, Path candidate, String label)
		throws WorldBuilderDiscoveryException {
		if (!candidate.startsWith(root)) {
			throw new WorldBuilderDiscoveryException("Path escapes its root: " + label);
		}
	}

	private static void copyFile(Path source, Path destination) throws IOException {
		Files.createDirectories(destination.getParent());
		Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING,
			StandardCopyOption.COPY_ATTRIBUTES);
	}

	private static void copyTree(final Path source, final Path destination) throws IOException {
		Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
				throws IOException {
				if (Files.isSymbolicLink(directory)) {
					throw new IOException("Runtime directory contains a symbolic link: " + directory);
				}
				Path relative = source.relativize(directory);
				Files.createDirectories(destination.resolve(relative));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				if (!attributes.isRegularFile() || Files.isSymbolicLink(file)) {
					throw new IOException("Runtime tree contains an unsupported entry: " + file);
				}
				copyFile(file, destination.resolve(source.relativize(file)));
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void deleteTree(Path root) {
		if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		try {
			Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
					Files.deleteIfExists(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
					if (failure != null) {
						throw failure;
					}
					Files.deleteIfExists(directory);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException ignored) {
		}
	}

	public static final class PreparedRuntime {
		public final Path workspace;
		public final int port;
		public final String sourceFingerprintSha256;

		PreparedRuntime(Path workspace, int port, String sourceFingerprintSha256) {
			this.workspace = workspace;
			this.port = port;
			this.sourceFingerprintSha256 = sourceFingerprintSha256;
		}

		public String toJson() {
			return "{\n"
				+ "  \"status\": \"prepared\",\n"
				+ "  \"workspace\": \"" + escape(workspace.toString()) + "\",\n"
				+ "  \"host\": \"127.0.0.1\",\n"
				+ "  \"port\": " + port + ",\n"
				+ "  \"sourceFingerprintSha256\": \"" + sourceFingerprintSha256 + "\"\n"
				+ "}\n";
		}

		private static String escape(String value) {
			return value.replace("\\", "\\\\").replace("\"", "\\\"");
		}
	}
}
