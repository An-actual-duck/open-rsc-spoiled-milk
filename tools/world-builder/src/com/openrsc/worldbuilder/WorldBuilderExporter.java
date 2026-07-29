package com.openrsc.worldbuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Validates and atomically publishes a deterministic five-file authored export. */
public final class WorldBuilderExporter {
	private static final String WORKING_CONFIG = "server/world-builder.conf";
	private static final String TERRAIN_SERVER = "server/conf/server/data/Custom_Landscape.orsc";
	private static final String TERRAIN_CLIENT = "Client_Base/Cache/video/Custom_Landscape.orsc";
	private static final String SCENERY_LOCS = "server/conf/server/defs/locs/MyWorldSceneryLocs.json";
	private static final String SCENERY_REMOVALS = "server/conf/server/defs/locs/MyWorldSceneryRemovals.json";
	private static final String NPC_LOCS = "server/conf/server/defs/locs/MyWorldNpcLocs.json";
	private static final String NPC_REMOVALS = "server/conf/server/defs/locs/MyWorldNpcRemovals.json";

	public ExportResult export(Path requestedWorkspace, String builderVersion, String sourceCommit)
		throws IOException, WorldBuilderDiscoveryException {
		validateVersion(builderVersion);
		if (sourceCommit == null || !sourceCommit.matches("[0-9a-f]{40}")) {
			throw new WorldBuilderDiscoveryException("Source commit must be exactly 40 lowercase hexadecimal characters.");
		}
		Path workspace = canonicalWorkspace(requestedWorkspace);
		Path lockPath = workspace.getParent().resolve("." + workspace.getFileName() + ".world-builder.lock");
		try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
			FileLock lock;
			try { lock = channel.tryLock(); } catch (OverlappingFileLockException busy) { lock = null; }
			if (lock == null) {
				throw new WorldBuilderDiscoveryException("Close World Builder before exporting this project.");
			}
			try {
				return exportLocked(workspace, builderVersion, sourceCommit);
			} finally {
				lock.release();
			}
		}
	}

	private ExportResult exportLocked(Path workspace, String builderVersion, String sourceCommit)
		throws IOException, WorldBuilderDiscoveryException {
		for (String pid : Arrays.asList("run/server.pid", "run/client.pid")) {
			if (Files.exists(workspace.resolve(pid), LinkOption.NOFOLLOW_LINKS)) {
				throw new WorldBuilderDiscoveryException("Close World Builder before exporting this project.");
			}
		}
		WorldBuilderSourceSnapshot.verify(workspace);
		if (WorldBuilderLayeredReview.readIfPresent(workspace) != null) {
			throw new WorldBuilderDiscoveryException(
				"Layered World Builder review projects are read-only; "
					+ "native package export is not implemented yet.");
		}
		WorldBuilderProjectSource project =
			WorldBuilderProjectSource.read(workspace.resolve("source/project-source.json"));
		Path working = requireDirectory(workspace, workspace.resolve("working"), "working tree");
		new WorldBuilderDiscovery().discover(working, WORKING_CONFIG, project.contentFingerprint);

		Path serverTerrain = requireFile(working, working.resolve(TERRAIN_SERVER), "working server terrain");
		Path clientTerrain = requireFile(working, working.resolve(TERRAIN_CLIENT), "working client terrain");
		String terrainHash = WorldBuilderHashes.sha256(serverTerrain);
		if (!terrainHash.equals(WorldBuilderHashes.sha256(clientTerrain))) {
			throw new WorldBuilderDiscoveryException("Working server and client terrain archives differ.");
		}
		WorldBuilderDiscovery.validateTerrainArchive(serverTerrain);

		List<ExportFile> files = new ArrayList<ExportFile>();
		files.add(ExportFile.terrain("terrain", "authored/Custom_Landscape.orsc",
			serverTerrain, terrainHash, project.required("serverTerrain")));
		files.add(overlay("sceneryLocs", "authored/MyWorldSceneryLocs.json", SCENERY_LOCS,
			"sceneries", working, project.required("sceneryLocs")));
		files.add(overlay("sceneryRemovals", "authored/MyWorldSceneryRemovals.json", SCENERY_REMOVALS,
			"scenery_removals", working, project.required("sceneryRemovals")));
		files.add(overlay("npcLocs", "authored/MyWorldNpcLocs.json", NPC_LOCS,
			"npclocs", working, project.required("npcLocs")));
		files.add(overlay("npcRemovals", "authored/MyWorldNpcRemovals.json", NPC_REMOVALS,
			"npc_removals", working, project.required("npcRemovals")));

		int changedCount = 0; for (ExportFile file : files) if (file.changed) changedCount++;
		if (changedCount == 0) return ExportResult.noChanges(workspace, project.sourceFingerprint);

		String authoredFingerprint = authoredFingerprint(files);
		String publicationFingerprint = publicationFingerprint(
			authoredFingerprint, builderVersion, sourceCommit, project.sourceFingerprint);
		String exportName = "export-" + publicationFingerprint.substring(0, 16);
		Path exports = workspace.resolve("exports").normalize();
		requireContained(workspace, exports, "exports directory");
		if (Files.exists(exports, LinkOption.NOFOLLOW_LINKS)
			&& (!Files.isDirectory(exports, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(exports))) {
			throw new WorldBuilderDiscoveryException("Exports path is unsafe: " + exports);
		}
		Files.createDirectories(exports);
		Path published = exports.resolve(exportName);
		String manifest = manifest(builderVersion, sourceCommit, project, files, changedCount);
		String summary = summary(workspace.getFileName().toString(), project, files);
		if (Files.exists(published, LinkOption.NOFOLLOW_LINKS)) {
			validatePublished(published, files, manifest, summary);
			return ExportResult.published(true, published, authoredFingerprint, changedCount, project.sourceFingerprint);
		}

		Path stage = exports.resolve("." + exportName + ".staging-" + UUID.randomUUID());
		try {
			Files.createDirectory(stage);
			for (ExportFile file : files) {
				Path destination = stage.resolve(file.bundlePath).normalize();
				requireContained(stage, destination, file.bundlePath);
				Files.createDirectories(destination.getParent());
				if (file.workingPath != null) Files.copy(file.workingPath, destination, StandardCopyOption.COPY_ATTRIBUTES);
				else Files.write(destination, file.generatedBytes);
			}
			Files.write(stage.resolve("manifest.json"), manifest.getBytes(StandardCharsets.UTF_8));
			Files.write(stage.resolve("CHANGE-SUMMARY.txt"), summary.getBytes(StandardCharsets.UTF_8));
			validatePublished(stage, files, manifest, summary);
			try { Files.move(stage, published, StandardCopyOption.ATOMIC_MOVE); }
			catch (AtomicMoveNotSupportedException unsupported) { Files.move(stage, published); }
			validatePublished(published, files, manifest, summary);
			return ExportResult.published(false, published, authoredFingerprint, changedCount, project.sourceFingerprint);
		} catch (IOException failure) {
			deleteTree(stage); throw failure;
		} catch (WorldBuilderDiscoveryException failure) {
			deleteTree(stage); throw failure;
		} catch (RuntimeException failure) {
			deleteTree(stage); throw failure;
		}
	}

	private static ExportFile overlay(String logicalName, String bundlePath, String relative,
		String rootName, Path working, WorldBuilderProjectSource.FileState source)
		throws IOException, WorldBuilderDiscoveryException {
		Path path = working.resolve(relative).normalize();
		byte[] generated = null; int entries;
		if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			requireFile(working, path, logicalName);
			entries = validateOverlay(logicalName, path);
		} else {
			if (source.present) throw new WorldBuilderDiscoveryException("Working overlay is missing: " + relative);
			generated = ("{\n\t\"" + rootName + "\": []\n}\n").getBytes(StandardCharsets.UTF_8);
			entries = 0;
		}
		String hash = generated == null ? WorldBuilderHashes.sha256(path) : WorldBuilderHashes.sha256(generated);
		boolean changed = source.present ? !hash.equals(source.sha256) : entries > 0;
		return new ExportFile(logicalName, bundlePath, generated == null ? path : null, generated,
			generated == null ? Files.size(path) : generated.length, hash, source.present,
			source.sha256, changed, entries);
	}

	private static int validateOverlay(String logicalName, Path path)
		throws IOException, WorldBuilderDiscoveryException {
		if ("sceneryLocs".equals(logicalName)) return WorldBuilderJsonDocuments.validateSceneryLocs(path);
		if ("sceneryRemovals".equals(logicalName)) return WorldBuilderJsonDocuments.validateSceneryRemovals(path);
		if ("npcLocs".equals(logicalName)) return WorldBuilderJsonDocuments.validateNpcLocs(path);
		return WorldBuilderJsonDocuments.validateNpcRemovals(path);
	}

	private static void validatePublished(Path root, List<ExportFile> files, String manifest, String summary)
		throws IOException, WorldBuilderDiscoveryException {
		if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
			throw new WorldBuilderDiscoveryException("Export directory is missing or unsafe: " + root);
		}
		java.util.HashSet<Path> expected = new java.util.HashSet<Path>();
		for (ExportFile file : files) {
			Path path = root.resolve(file.bundlePath).normalize();
			expected.add(path);
			requireFile(root, path, file.logicalName);
			if (Files.size(path) != file.size || !file.sha256.equals(WorldBuilderHashes.sha256(path))) {
				throw new WorldBuilderDiscoveryException("Export file verification failed: " + file.bundlePath);
			}
			if ("terrain".equals(file.logicalName)) {
				WorldBuilderDiscovery.validateTerrainArchive(path);
			} else {
				validateOverlay(file.logicalName, path);
			}
		}
		Path manifestPath = root.resolve("manifest.json");
		Path summaryPath = root.resolve("CHANGE-SUMMARY.txt");
		expected.add(manifestPath);
		expected.add(summaryPath);
		requireFile(root, manifestPath, "manifest");
		requireFile(root, summaryPath, "change summary");
		if (!manifest.equals(new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8))) {
			throw new WorldBuilderDiscoveryException("Export manifest verification failed.");
		}
		if (!summary.equals(new String(Files.readAllBytes(summaryPath), StandardCharsets.UTF_8))) {
			throw new WorldBuilderDiscoveryException("Export summary verification failed.");
		}
		WorldBuilderExportManifest.read(manifestPath);
		try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
			java.util.Iterator<Path> iterator = stream.iterator();
			while (iterator.hasNext()) {
				Path path = iterator.next();
				if (path.equals(root) || Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
				if (Files.isSymbolicLink(path) || !expected.contains(path.normalize())) {
					throw new WorldBuilderDiscoveryException(
						"Export contains an unexpected or unsafe file: " + root.relativize(path));
				}
			}
		}
	}

	private static String authoredFingerprint(List<ExportFile> files) {
		MessageDigest digest = WorldBuilderHashes.newDigest();
		for (ExportFile file : files) {
			WorldBuilderHashes.updateText(digest, file.logicalName);
			WorldBuilderHashes.updateText(digest, file.sha256);
			WorldBuilderHashes.updateText(digest, Long.toString(file.size));
		}
		return WorldBuilderHashes.hex(digest.digest());
	}

	private static String publicationFingerprint(String authoredFingerprint, String version,
		String commit, String sourceFingerprint) {
		MessageDigest digest = WorldBuilderHashes.newDigest();
		WorldBuilderHashes.updateText(digest, authoredFingerprint);
		WorldBuilderHashes.updateText(digest, version);
		WorldBuilderHashes.updateText(digest, commit);
		WorldBuilderHashes.updateText(digest, sourceFingerprint);
		return WorldBuilderHashes.hex(digest.digest());
	}

	private static String manifest(String version, String commit, WorldBuilderProjectSource project,
		List<ExportFile> files, int changedCount) {
		StringBuilder json = new StringBuilder(2048);
		json.append("{\n  \"schemaVersion\": 1,\n  \"manifestType\": \"world-builder-export\",\n");
		json.append("  \"builderVersion\": \"").append(escape(version))
			.append("\",\n  \"sourceCommit\": \"").append(commit).append("\",\n");
		json.append("  \"layoutAdapter\": \"").append(escape(project.layoutAdapter))
			.append("\",\n  \"sourceFingerprintSha256\": \"").append(project.sourceFingerprint)
			.append("\",\n  \"contentFingerprintSha256\": \"").append(project.contentFingerprint)
			.append("\",\n  \"files\": [\n");
		for (int index = 0; index < files.size(); index++) {
			ExportFile file = files.get(index);
			json.append("    {\"logicalName\": \"").append(file.logicalName)
				.append("\", \"bundlePath\": \"").append(file.bundlePath)
				.append("\", \"size\": ").append(file.size)
				.append(", \"sha256\": \"").append(file.sha256)
				.append("\", \"sourcePresent\": ").append(file.sourcePresent)
				.append(", \"sourceSha256\": \"").append(file.sourceSha256)
				.append("\", \"changed\": ").append(file.changed).append("}")
				.append(index + 1 < files.size() ? "," : "").append('\n');
		}
		boolean terrain = files.get(0).changed;
		boolean scenery = files.get(1).changed || files.get(2).changed;
		boolean npc = files.get(3).changed || files.get(4).changed;
		json.append("  ],\n  \"changeSummary\": {\"changedFileCount\": ").append(changedCount)
			.append(", \"terrainChanged\": ").append(terrain)
			.append(", \"sceneryChanged\": ").append(scenery)
			.append(", \"npcChanged\": ").append(npc).append("}\n}\n");
		return json.toString();
	}

	private static String summary(String projectName, WorldBuilderProjectSource project,
		List<ExportFile> files) {
		StringBuilder text = new StringBuilder();
		text.append("Spoiled Milk World Builder Export\n\nProject: ").append(projectName)
			.append("\nSource revision: ").append(project.sourceFingerprint)
			.append("\n\nChanged authored files:\n");
		for (ExportFile file : files) {
			if (file.changed) text.append("- ").append(file.bundlePath).append("\n");
		}
		text.append("\nWorking content:\n- terrain sectors: validated\n- scenery locations: ")
			.append(files.get(1).entries).append("\n- scenery removals: ")
			.append(files.get(2).entries).append("\n- NPC locations: ")
			.append(files.get(3).entries).append("\n- NPC removals: ")
			.append(files.get(4).entries).append("\n");
		return text.toString();
	}

	private static Path canonicalWorkspace(Path requested)
		throws IOException, WorldBuilderDiscoveryException {
		Path workspace = requested.toAbsolutePath().normalize();
		if (!Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(workspace)) {
			throw new WorldBuilderDiscoveryException("Prepared Builder workspace is missing or unsafe: " + workspace);
		}
		return workspace.toRealPath();
	}

	private static Path requireDirectory(Path root, Path path, String label)
		throws IOException, WorldBuilderDiscoveryException {
		requireContained(root, path, label);
		if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
			throw new WorldBuilderDiscoveryException(label + " is missing or unsafe: " + path);
		}
		Path real = path.toRealPath();
		if (!real.startsWith(root)) throw new WorldBuilderDiscoveryException(label + " resolves outside the workspace.");
		return real;
	}

	private static Path requireFile(Path root, Path path, String label)
		throws IOException, WorldBuilderDiscoveryException {
		requireContained(root, path, label);
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)
			|| !path.toRealPath().startsWith(root)) {
			throw new WorldBuilderDiscoveryException(label + " is missing or unsafe: " + path);
		}
		return path;
	}

	private static void requireContained(Path root, Path path, String label)
		throws WorldBuilderDiscoveryException {
		if (!path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
			throw new WorldBuilderDiscoveryException(label + " escapes its workspace root.");
		}
	}

	private static void validateVersion(String value) throws WorldBuilderDiscoveryException {
		if (value == null || value.trim().isEmpty() || value.length() > 64) {
			throw new WorldBuilderDiscoveryException("Builder version must contain 1 to 64 characters.");
		}
		for (int index = 0; index < value.length(); index++) {
			if (Character.isISOControl(value.charAt(index))) {
				throw new WorldBuilderDiscoveryException("Builder version contains control characters.");
			}
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
				@Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
					Files.deleteIfExists(file); return FileVisitResult.CONTINUE;
				}
				@Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
					if (failure != null) throw failure;
					Files.deleteIfExists(directory); return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException ignored) {
		}
	}

	private static final class ExportFile {
		final String logicalName,bundlePath;final Path workingPath;final byte[] generatedBytes;
		final long size;final String sha256,sourceSha256;final boolean sourcePresent,changed;final int entries;
		ExportFile(String logical,String bundle,Path working,byte[] generated,long size,String hash,
			boolean sourcePresent,String sourceSha256,boolean changed,int entries){
			this.logicalName=logical;this.bundlePath=bundle;this.workingPath=working;this.generatedBytes=generated;
			this.size=size;this.sha256=hash;this.sourcePresent=sourcePresent;this.sourceSha256=sourceSha256;
			this.changed=changed;this.entries=entries;
		}
		static ExportFile terrain(String logical,String bundle,Path path,String hash,
			WorldBuilderProjectSource.FileState source)throws IOException{
			return new ExportFile(logical,bundle,path,null,Files.size(path),hash,source.present,
				source.sha256,!source.present||!hash.equals(source.sha256),-1);
		}
	}

	public static final class ExportResult {
		public final String status;public final boolean existing;public final Path exportDirectory;
		public final String authoredFingerprint,sourceFingerprint;public final int changedFileCount;
		private ExportResult(String status,boolean existing,Path directory,String authored,int changed,String source){
			this.status=status;this.existing=existing;this.exportDirectory=directory;this.authoredFingerprint=authored;
			this.changedFileCount=changed;this.sourceFingerprint=source;
		}
		static ExportResult noChanges(Path workspace,String source){return new ExportResult("no-changes",false,null,"",0,source);}
		static ExportResult published(boolean existing,Path path,String fingerprint,int changed,String source){
			return new ExportResult("exported",existing,path,fingerprint,changed,source);
		}
		public String toJson(){
			StringBuilder json=new StringBuilder();
			json.append("{\n  \"status\": \"").append(status).append("\",\n  \"existing\": ").append(existing)
				.append(",\n  \"changedFileCount\": ").append(changedFileCount)
				.append(",\n  \"sourceFingerprintSha256\": \"").append(sourceFingerprint).append("\"");
			if(exportDirectory!=null)json.append(",\n  \"exportDirectory\": \"").append(escape(exportDirectory.toString()))
				.append("\",\n  \"authoredFingerprintSha256\": \"").append(authoredFingerprint).append("\"");
			json.append("\n}\n");return json.toString();
		}
	}
}
