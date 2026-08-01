package com.openrsc.worldbuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Reopens and validates an immutable canonical export before import. */
final class WorldBuilderExportBundle {
	final Path root;
	final Path manifestPath;
	final String manifestSha256;
	final WorldBuilderExportManifest manifest;
	final Map<String, ExportedFile> files;
	final Path layeredPackageRoot;

	private WorldBuilderExportBundle(Path root, Path manifestPath, String manifestSha256,
		WorldBuilderExportManifest manifest, Map<String, ExportedFile> files,
		Path layeredPackageRoot) {
		this.root = root;
		this.manifestPath = manifestPath;
		this.manifestSha256 = manifestSha256;
		this.manifest = manifest;
		this.files = java.util.Collections.unmodifiableMap(files);
		this.layeredPackageRoot = layeredPackageRoot;
	}

	static WorldBuilderExportBundle open(Path requested)
		throws IOException, WorldBuilderDiscoveryException {
		Path normalized = requested.toAbsolutePath().normalize();
		if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(normalized)) {
			throw new WorldBuilderDiscoveryException(
				"Export directory is missing or unsafe: " + normalized);
		}
		Path root = normalized.toRealPath();
		Path manifestPath = requiredFile(root, root.resolve("manifest.json"), "export manifest");
		WorldBuilderExportManifest manifest = WorldBuilderExportManifest.read(manifestPath);
		if (manifest.changedFileCount < 1) {
			throw new WorldBuilderDiscoveryException("An export with no changes cannot be imported.");
		}

		Map<String, ExportedFile> files = new LinkedHashMap<String, ExportedFile>();
		Set<Path> expected = new HashSet<Path>();
		expected.add(manifestPath);
		for (WorldBuilderExportManifest.FileRecord record : manifest.files) {
			Path path = requiredFile(root, root.resolve(record.bundlePath), record.logicalName);
			expected.add(path.normalize());
			if (Files.size(path) != record.size
				|| !record.sha256.equals(WorldBuilderHashes.sha256(path))) {
				throw new WorldBuilderDiscoveryException(
					"Export file verification failed: " + record.bundlePath);
			}
			if (!manifest.isLayered()) {
				int entries = validateContent(record.logicalName, path);
				if (!record.sourcePresent && record.changed != (entries > 0)) {
					throw new WorldBuilderDiscoveryException(
						"Export absent-source change state is inconsistent: " + record.bundlePath);
				}
				if ("terrain".equals(record.logicalName) && !record.sourcePresent) {
					throw new WorldBuilderDiscoveryException("Export terrain has no source revision.");
				}
			}
			files.put(record.logicalName, new ExportedFile(record, path));
		}

		Path summary = requiredFile(root, root.resolve("CHANGE-SUMMARY.txt"), "change summary");
		expected.add(summary.normalize());
		long summarySize = Files.size(summary);
		if (summarySize < 1 || summarySize > 1_048_576L) {
			throw new WorldBuilderDiscoveryException("Export change summary has an invalid size.");
		}
		String summaryText = new String(Files.readAllBytes(summary), StandardCharsets.UTF_8);
		if (!summaryText.startsWith("Spoiled Milk World Builder Export\n")) {
			throw new WorldBuilderDiscoveryException("Export change summary is invalid.");
		}
		try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
			java.util.Iterator<Path> iterator = stream.iterator();
			while (iterator.hasNext()) {
				Path path = iterator.next();
				if (path.equals(root) || Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
					continue;
				}
				if (Files.isSymbolicLink(path) || !expected.contains(path.normalize())) {
					throw new WorldBuilderDiscoveryException(
						"Export contains an unexpected or unsafe file: " + root.relativize(path));
				}
			}
		}
		Path layeredPackageRoot = null;
		if (manifest.isLayered()) {
			layeredPackageRoot = requiredDirectory(root,
				root.resolve("authored/layered-world/package"),
				"layered export package");
			WorldBuilderLayeredPackage layered =
				WorldBuilderLayeredPackage.discoverDraft(layeredPackageRoot);
			if (!manifest.layeredPackageManifestSha256.equals(layered.manifestSha256)
				|| !manifest.layeredPackageFingerprintSha256.equals(
					layered.packageFingerprintSha256)
				|| layered.files.size() != manifest.files.size()) {
				throw new WorldBuilderDiscoveryException(
					"Layered export package identity or inventory did not verify.");
			}
			for (WorldBuilderLayeredPackage.FileRecord file : layered.files) {
				ExportedFile exported = files.get(file.relativePath);
				if (exported == null || exported.record.size != file.size
					|| !exported.record.sha256.equals(file.sha256)) {
					throw new WorldBuilderDiscoveryException(
						"Layered export package inventory did not verify: "
							+ file.relativePath);
				}
			}
		}
		return new WorldBuilderExportBundle(root, manifestPath,
			WorldBuilderHashes.sha256(manifestPath), manifest, files,
			layeredPackageRoot);
	}

	ExportedFile required(String logicalName) throws WorldBuilderDiscoveryException {
		ExportedFile file = files.get(logicalName);
		if (file == null) {
			throw new WorldBuilderDiscoveryException("Export is missing " + logicalName + ".");
		}
		return file;
	}

	private static int validateContent(String logicalName, Path path)
		throws IOException, WorldBuilderDiscoveryException {
		if ("terrain".equals(logicalName)) {
			WorldBuilderDiscovery.validateTerrainArchive(path);
			return -1;
		}
		if ("sceneryLocs".equals(logicalName)) {
			return WorldBuilderJsonDocuments.validateSceneryLocs(path);
		}
		if ("sceneryRemovals".equals(logicalName)) {
			return WorldBuilderJsonDocuments.validateSceneryRemovals(path);
		}
		if ("npcLocs".equals(logicalName)) {
			return WorldBuilderJsonDocuments.validateNpcLocs(path);
		}
		return WorldBuilderJsonDocuments.validateNpcRemovals(path);
	}

	private static Path requiredFile(Path root, Path path, String label)
		throws IOException, WorldBuilderDiscoveryException {
		Path normalized = path.toAbsolutePath().normalize();
		if (!normalized.startsWith(root) || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(normalized)) {
			throw new WorldBuilderDiscoveryException(label + " is missing or unsafe: " + normalized);
		}
		Path real = normalized.toRealPath();
		if (!real.startsWith(root)) {
			throw new WorldBuilderDiscoveryException(label + " resolves outside the export.");
		}
		return real;
	}

	private static Path requiredDirectory(Path root, Path path, String label)
		throws IOException, WorldBuilderDiscoveryException {
		Path normalized = path.toAbsolutePath().normalize();
		if (!normalized.startsWith(root)
			|| !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(normalized)) {
			throw new WorldBuilderDiscoveryException(
				label + " is missing or unsafe: " + normalized);
		}
		Path real = normalized.toRealPath();
		if (!real.startsWith(root)) {
			throw new WorldBuilderDiscoveryException(label + " resolves outside the export.");
		}
		return real;
	}

	static final class ExportedFile {
		final WorldBuilderExportManifest.FileRecord record;
		final Path path;

		ExportedFile(WorldBuilderExportManifest.FileRecord record, Path path) {
			this.record = record;
			this.path = path;
		}
	}
}
