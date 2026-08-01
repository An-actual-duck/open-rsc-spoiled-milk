package com.openrsc.worldbuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Publishes a validated immutable full-package export for World Builder 2. */
final class WorldBuilderLayeredExporter {
	private static final String BUNDLE_PREFIX = "authored/layered-world/package/";

	private WorldBuilderLayeredExporter() {
	}

	static WorldBuilderExporter.ExportResult exportLocked(Path workspace,
		String builderVersion, String sourceCommit, WorldBuilderLayeredReview review)
		throws IOException, WorldBuilderDiscoveryException {
		Path pendingJournal = workspace.resolve(
			WorldBuilderLayeredTerrainDraftJournal.RELATIVE_PATH).normalize();
		if (Files.exists(pendingJournal, LinkOption.NOFOLLOW_LINKS)) {
			throw new WorldBuilderDiscoveryException(
				"Layered editor changes are still pending. Save and close World Builder "
					+ "before exporting.");
		}
		WorldBuilderProjectSource project = WorldBuilderProjectSource.read(
			workspace.resolve("source/project-source.json"));
		Path sourceRoot = packageDirectory(workspace,
			WorldBuilderRuntimePreparer.LAYERED_SOURCE_PACKAGE, "layered source package");
		Path workingRoot = packageDirectory(workspace,
			WorldBuilderRuntimePreparer.LAYERED_WORKING_PACKAGE, "layered working package");
		WorldBuilderLayeredPackage source = WorldBuilderLayeredPackage.discover(
			sourceRoot, WorldBuilderLayeredPackage.PROFILE_ID);
		WorldBuilderLayeredPackage working =
			WorldBuilderLayeredPackage.discoverDraft(workingRoot);
		working.requireFirstDraftDescendant(source);
		if (!review.packageFingerprintSha256.equals(working.packageFingerprintSha256)) {
			throw new WorldBuilderDiscoveryException(
				"Layered workspace metadata does not match the saved working package.");
		}

		Map<String, WorldBuilderLayeredPackage.FileRecord> sourceFiles =
			new LinkedHashMap<String, WorldBuilderLayeredPackage.FileRecord>();
		for (WorldBuilderLayeredPackage.FileRecord file : source.files) {
			sourceFiles.put(file.relativePath, file);
		}
		List<LayeredFile> files = new ArrayList<LayeredFile>();
		int changedCount = 0;
		int addedCount = 0;
		for (WorldBuilderLayeredPackage.FileRecord file : working.files) {
			WorldBuilderLayeredPackage.FileRecord original =
				sourceFiles.get(file.relativePath);
			boolean changed = original == null || !original.sha256.equals(file.sha256);
			if (changed) {
				changedCount++;
				if (original == null) addedCount++;
			}
			files.add(new LayeredFile(file.relativePath, workingRoot.resolve(file.relativePath),
				file.size, file.sha256, original != null,
				original == null ? "" : original.sha256, changed));
		}
		if (changedCount == 0) {
			return WorldBuilderExporter.ExportResult.noChanges(
				workspace, project.sourceFingerprint);
		}

		String publicationFingerprint = publicationFingerprint(working, source,
			builderVersion, sourceCommit, project.sourceFingerprint);
		String exportName = "export-" + publicationFingerprint.substring(0, 16);
		Path exports = workspace.resolve("exports").normalize();
		requireContained(workspace, exports, "exports directory");
		if (Files.exists(exports, LinkOption.NOFOLLOW_LINKS)
			&& (!Files.isDirectory(exports, LinkOption.NOFOLLOW_LINKS)
				|| Files.isSymbolicLink(exports))) {
			throw new WorldBuilderDiscoveryException("Exports path is unsafe: " + exports);
		}
		Files.createDirectories(exports);
		Path published = exports.resolve(exportName).normalize();
		String manifest = manifest(builderVersion, sourceCommit, project, source,
			working, files, changedCount, addedCount);
		String summary = summary(workspace.getFileName().toString(), project,
			source, working, files, changedCount, addedCount);
		if (Files.exists(published, LinkOption.NOFOLLOW_LINKS)) {
			validatePublished(published, manifest, summary);
			return WorldBuilderExporter.ExportResult.published(true, published,
				working.packageFingerprintSha256, changedCount,
				project.sourceFingerprint);
		}

		Path stage = exports.resolve("." + exportName + ".staging-" + UUID.randomUUID());
		try {
			Files.createDirectory(stage);
			for (LayeredFile file : files) {
				Path destination = stage.resolve(BUNDLE_PREFIX + file.relativePath).normalize();
				requireContained(stage, destination, file.relativePath);
				Files.createDirectories(destination.getParent());
				Files.copy(file.sourcePath, destination, StandardCopyOption.COPY_ATTRIBUTES);
			}
			Files.write(stage.resolve("manifest.json"),
				manifest.getBytes(StandardCharsets.UTF_8));
			Files.write(stage.resolve("CHANGE-SUMMARY.txt"),
				summary.getBytes(StandardCharsets.UTF_8));
			validatePublished(stage, manifest, summary);
			try {
				Files.move(stage, published, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(stage, published);
			}
			validatePublished(published, manifest, summary);
			return WorldBuilderExporter.ExportResult.published(false, published,
				working.packageFingerprintSha256, changedCount,
				project.sourceFingerprint);
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

	private static String manifest(String version, String commit,
		WorldBuilderProjectSource project, WorldBuilderLayeredPackage source,
		WorldBuilderLayeredPackage working, List<LayeredFile> files,
		int changedCount, int addedCount) {
		StringBuilder json = new StringBuilder(4096 + files.size() * 320);
		json.append("{\n  \"schemaVersion\": 1,\n")
			.append("  \"manifestType\": \"world-builder-layered-export\",\n")
			.append("  \"builderVersion\": \"").append(escape(version)).append("\",\n")
			.append("  \"sourceCommit\": \"").append(commit).append("\",\n")
			.append("  \"layoutAdapter\": \"")
			.append(WorldBuilderLayeredPackage.ADAPTER_ID).append("\",\n")
			.append("  \"sourceFingerprintSha256\": \"")
			.append(project.sourceFingerprint).append("\",\n")
			.append("  \"contentFingerprintSha256\": \"")
			.append(project.contentFingerprint).append("\",\n")
			.append("  \"layeredSourceManifestSha256\": \"")
			.append(source.manifestSha256).append("\",\n")
			.append("  \"layeredSourcePackageFingerprintSha256\": \"")
			.append(source.packageFingerprintSha256).append("\",\n")
			.append("  \"layeredPackageManifestSha256\": \"")
			.append(working.manifestSha256).append("\",\n")
			.append("  \"layeredPackageFingerprintSha256\": \"")
			.append(working.packageFingerprintSha256).append("\",\n")
			.append("  \"files\": [\n");
		for (int index = 0; index < files.size(); index++) {
			LayeredFile file = files.get(index);
			json.append("    {\"logicalName\": \"").append(escape(file.relativePath))
				.append("\", \"bundlePath\": \"").append(BUNDLE_PREFIX)
				.append(escape(file.relativePath)).append("\", \"size\": ")
				.append(file.size).append(", \"sha256\": \"").append(file.sha256)
				.append("\", \"sourcePresent\": ").append(file.sourcePresent)
				.append(", \"sourceSha256\": \"").append(file.sourceSha256)
				.append("\", \"changed\": ").append(file.changed).append("}")
				.append(index + 1 < files.size() ? "," : "").append('\n');
		}
		json.append("  ],\n  \"changeSummary\": {\"changedFileCount\": ")
			.append(changedCount).append(", \"addedFileCount\": ").append(addedCount)
			.append(", \"replacedFileCount\": ")
			.append(changedCount - addedCount).append("}\n}\n");
		return json.toString();
	}

	private static String summary(String projectName, WorldBuilderProjectSource project,
		WorldBuilderLayeredPackage source, WorldBuilderLayeredPackage working,
		List<LayeredFile> files, int changedCount, int addedCount) {
		StringBuilder text = new StringBuilder(8192);
		text.append("Spoiled Milk World Builder Export\n\n")
			.append("Format: signed-layered package\nProject: ").append(projectName)
			.append("\nTarget source revision: ").append(project.sourceFingerprint)
			.append("\nLayered source package: ").append(source.packageFingerprintSha256)
			.append("\nExported package: ").append(working.packageFingerprintSha256)
			.append("\nChanged package files: ").append(changedCount)
			.append(" (added ").append(addedCount).append(", replaced ")
			.append(changedCount - addedCount).append(")\n")
			.append("Levels: ").append(working.levels)
			.append("\nTerrain sectors: ").append(working.terrainSectorCount)
			.append("\nPlacement sets: ").append(working.placementSetCount)
			.append("\n\nChanged package paths:\n");
		int listed = 0;
		for (LayeredFile file : files) {
			if (!file.changed) continue;
			if (listed < 200) text.append("- ").append(file.relativePath).append('\n');
			listed++;
		}
		if (listed > 200) {
			text.append("- ... ").append(listed - 200)
				.append(" additional changed paths are recorded in manifest.json\n");
		}
		return text.toString();
	}

	private static void validatePublished(Path root, String manifest, String summary)
		throws IOException, WorldBuilderDiscoveryException {
		WorldBuilderExportBundle bundle = WorldBuilderExportBundle.open(root);
		if (!bundle.manifest.isLayered()
			|| !manifest.equals(new String(Files.readAllBytes(bundle.manifestPath),
				StandardCharsets.UTF_8))
			|| !summary.equals(new String(Files.readAllBytes(
				root.resolve("CHANGE-SUMMARY.txt")), StandardCharsets.UTF_8))) {
			throw new WorldBuilderDiscoveryException(
				"Published layered export did not verify exactly.");
		}
	}

	private static String publicationFingerprint(WorldBuilderLayeredPackage working,
		WorldBuilderLayeredPackage source, String version, String commit,
		String targetSourceFingerprint) {
		MessageDigest digest = WorldBuilderHashes.newDigest();
		WorldBuilderHashes.updateText(digest, working.packageFingerprintSha256);
		WorldBuilderHashes.updateText(digest, source.packageFingerprintSha256);
		WorldBuilderHashes.updateText(digest, version);
		WorldBuilderHashes.updateText(digest, commit);
		WorldBuilderHashes.updateText(digest, targetSourceFingerprint);
		return WorldBuilderHashes.hex(digest.digest());
	}

	private static Path packageDirectory(Path workspace, String relative, String label)
		throws IOException, WorldBuilderDiscoveryException {
		Path path = workspace.resolve(relative).normalize();
		requireContained(workspace, path, label);
		if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(path) || !path.toRealPath().startsWith(workspace)) {
			throw new WorldBuilderDiscoveryException(label + " is missing or unsafe.");
		}
		return path.toRealPath();
	}

	private static void requireContained(Path root, Path path, String label)
		throws WorldBuilderDiscoveryException {
		if (!path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
			throw new WorldBuilderDiscoveryException(label + " escapes its root.");
		}
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"")
			.replace("\n", "\\n").replace("\r", "\\r");
	}

	private static void deleteTree(Path root) {
		if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
		try {
			Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
				@Override public FileVisitResult visitFile(Path file,
					BasicFileAttributes attributes) throws IOException {
					Files.deleteIfExists(file);
					return FileVisitResult.CONTINUE;
				}
				@Override public FileVisitResult postVisitDirectory(Path directory,
					IOException failure) throws IOException {
					if (failure != null) throw failure;
					Files.deleteIfExists(directory);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException ignored) {
		}
	}

	private static final class LayeredFile {
		final String relativePath;
		final Path sourcePath;
		final long size;
		final String sha256;
		final boolean sourcePresent;
		final String sourceSha256;
		final boolean changed;

		LayeredFile(String relativePath, Path sourcePath, long size, String sha256,
			boolean sourcePresent, String sourceSha256, boolean changed) {
			this.relativePath = relativePath;
			this.sourcePath = sourcePath;
			this.size = size;
			this.sha256 = sha256;
			this.sourcePresent = sourcePresent;
			this.sourceSha256 = sourceSha256;
			this.changed = changed;
		}
	}
}
