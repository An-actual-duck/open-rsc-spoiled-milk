package com.openrsc.server.content.worldedit;

import com.openrsc.server.ServerConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves every authored-world read/write through one explicit storage owner.
 * Normal servers retain their historical process-relative layout. Builder mode
 * is fail-closed and may access authored files only below its prepared working tree.
 */
public final class WorldEditStorageContext {
	public static final String WORKSPACE_PROPERTY = "openrsc.worldBuilderWorkspaceRoot";

	private final boolean builderMode;
	private final boolean adaptiveMode;
	private final Path workspaceRoot;
	private final Path sourceRoot;
	private final Path workingRoot;
	private final Path serverRoot;
	private final Path clientRoot;
	private final Path configDirectory;
	private final Path builderTerrainBackups;
	private final Path sourceLayeredBaselinePackage;

	private WorldEditStorageContext(boolean builderMode, boolean adaptiveMode,
		Path workspaceRoot, Path sourceRoot,
		Path workingRoot, Path serverRoot, Path clientRoot, Path configDirectory,
		Path builderTerrainBackups, Path sourceLayeredBaselinePackage) {
		this.builderMode = builderMode;
		this.adaptiveMode = adaptiveMode;
		this.workspaceRoot = workspaceRoot;
		this.sourceRoot = sourceRoot;
		this.workingRoot = workingRoot;
		this.serverRoot = serverRoot;
		this.clientRoot = clientRoot;
		this.configDirectory = configDirectory;
		this.builderTerrainBackups = builderTerrainBackups;
		this.sourceLayeredBaselinePackage = sourceLayeredBaselinePackage;
	}

	public static WorldEditStorageContext create(ServerConfiguration config) throws IOException {
		if (!config.WORLD_BUILDER_MODE) {
			Path server = Paths.get("").toAbsolutePath().normalize();
			Path client = server.resolve("../Client_Base").normalize();
			return new WorldEditStorageContext(false, false, null, null, null,
				server, client, server.resolve(config.CONFIG_DIR).normalize(), null, null);
		}

		String configuredRoot = System.getProperty(WORKSPACE_PROPERTY, "").trim();
		if (configuredRoot.isEmpty()) {
			throw new IOException("World Builder workspace property is required: " + WORKSPACE_PROPERTY);
		}
		Path workspace = requireDirectory(Paths.get(configuredRoot), "World Builder workspace");
		Path source = requireContainedDirectory(workspace, workspace.resolve("source"), "source snapshot");
		Path working = requireContainedDirectory(workspace, workspace.resolve("working"), "working tree");
		boolean adaptive = AdaptiveWorldBuilderRuntimeIdentity.isAdaptive(config);
		Path server = requireContainedDirectory(
			workspace,
			adaptive ? working.resolve("runtime/server") : working.resolve("server"),
			"working server");
		Path client = requireContainedDirectory(
			workspace,
			adaptive ? working.resolve("runtime/client") : working.resolve("Client_Base"),
			"working client");
		Path actualCwd = Paths.get("").toAbsolutePath().normalize().toRealPath();
		if (!actualCwd.equals(server)) {
			throw new IOException("World Builder server must run from its validated working/server directory.");
		}
		Path configDirectory = requireContainedDirectory(
			workspace, server.resolve(config.CONFIG_DIR), "working configuration");
		if (adaptive) {
			Path baseline = requireContainedDirectory(
				workspace,
				source.resolve("layered-baseline/package"),
				"immutable layered baseline");
			Path workingPackageLocation =
				working.resolve("layered-world/package").normalize();
			requireContainedPath(
				workspace, workingPackageLocation,
				"layered working package");
			AdaptiveWorldBuilderPackagePublisher.recover(
				workingPackageLocation);
			Path workingPackage = requireContainedDirectory(
				workspace, workingPackageLocation,
				"layered working package");
			if (Files.isSameFile(baseline, workingPackage)) {
				throw new IOException(
					"Layered working package aliases the immutable source baseline.");
			}
			Path run = requireContainedDirectory(
				workspace, workspace.resolve("run"), "project runtime state");
			return new WorldEditStorageContext(
				true, true, workspace, source, working, server, client,
				configDirectory, run.resolve("terrain-backups"), baseline);
		}
		Path serverTerrain = configDirectory.resolve("data/Custom_Landscape.orsc").normalize();
		Path clientTerrain = client.resolve("Cache/video/Custom_Landscape.orsc").normalize();
		requireContainedRegularFile(workspace, serverTerrain, "working server terrain");
		requireContainedRegularFile(workspace, clientTerrain, "working client terrain");
		Path backups = workspace.resolve("backups/terrain").normalize();
		requireContainedPath(workspace, backups, "terrain backups");
		return new WorldEditStorageContext(true, false, workspace, source, working,
			server, client, configDirectory, backups, null);
	}

	public boolean isBuilderMode() {
		return builderMode;
	}

	public boolean isAdaptiveMode() {
		return adaptiveMode;
	}

	public Path workspaceRoot() {
		return workspaceRoot;
	}

	public Path sourceRoot() {
		return sourceRoot;
	}

	public Path workingRoot() {
		return workingRoot;
	}

	public Path configDirectory() {
		return configDirectory;
	}

	public Path sourceLayeredBaselinePackage() throws IOException {
		if (!adaptiveMode || sourceLayeredBaselinePackage == null) {
			throw new IOException(
				"An immutable layered baseline is available only in adaptive Builder mode.");
		}
		return requireContainedDirectory(
			workspaceRoot, sourceLayeredBaselinePackage,
			"immutable layered baseline");
	}

	public Path layeredWorkingPackage() throws IOException {
		if (!builderMode) {
			throw new IOException(
				"Layered draft storage is restricted to World Builder mode.");
		}
		return requireContainedDirectory(
			workspaceRoot,
			workingRoot.resolve("layered-world/package").normalize(),
			"layered working package");
	}

	public Path layeredWorkingPackageLocation() throws IOException {
		if (!adaptiveMode) {
			throw new IOException(
				"A generic layered package location requires adaptive Builder mode.");
		}
		Path location =
			workingRoot.resolve("layered-world/package").normalize();
		requireContainedPath(
			workspaceRoot, location, "layered working package");
		return location;
	}

	public Path layeredTerrainDraftJournal() throws IOException {
		if (!builderMode) {
			throw new IOException(
				"Layered draft storage is restricted to World Builder mode.");
		}
		Path journal =
			workingRoot.resolve("layered-world/terrain-draft-v1.tsv").normalize();
		requireContainedPath(workspaceRoot, journal, "layered terrain draft journal");
		if (Files.exists(journal, LinkOption.NOFOLLOW_LINKS)
			&& (!Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS)
				|| Files.isSymbolicLink(journal))) {
			throw new IOException(
				"Layered terrain draft journal is unsafe: " + journal);
		}
		return journal;
	}

	public Path terrainArchive(ServerConfiguration config) throws IOException {
		String name = config.WANT_CUSTOM_LANDSCAPE ? "Custom_Landscape.orsc"
			: (config.MEMBER_WORLD ? "Authentic_Landscape.orsc" : "F2PLandscape.orsc");
		Path archive = configDirectory.resolve("data").resolve(name).normalize();
		if (builderMode) {
			requireContainedRegularFile(workspaceRoot, archive, "working terrain archive");
		}
		return archive;
	}

	public Path clientTerrainArchive() throws IOException {
		Path archive = clientRoot.resolve("Cache/video/Custom_Landscape.orsc").normalize();
		if (builderMode) {
			requireContainedRegularFile(workspaceRoot, archive, "working client terrain archive");
		}
		return archive;
	}

	public Path terrainBackupDirectory(Path terrainArchive) throws IOException {
		Path backups = builderMode ? builderTerrainBackups
			: terrainArchive.getParent().resolve("world-editor-backups").normalize();
		if (builderMode) {
			requireContainedPath(workspaceRoot, backups, "terrain backup directory");
		}
		return backups;
	}

	public void validateWorkingAuthoredFile(Path requested) throws IOException {
		if (!builderMode) {
			return;
		}
		Path candidate = requested.toAbsolutePath().normalize();
		if (!candidate.startsWith(workingRoot)) {
			throw new IOException("Authored world output escapes the World Builder working tree.");
		}
		requireContainedPath(workspaceRoot, candidate, "authored world output");
		if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)
			&& (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(candidate))) {
			throw new IOException("Authored world output is not a safe regular file: " + candidate);
		}
		Path parent = candidate.getParent();
		if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(parent) || !parent.toRealPath().startsWith(workingRoot)) {
			throw new IOException("Authored world output directory is missing or unsafe: " + parent);
		}
	}

	/** Validates a generated runtime path without creating or deleting it. */
	public Path validateGeneratedPath(Path requested, String label)
		throws IOException {
		if (!builderMode) {
			throw new IOException(
				"Generated World Builder state requires isolated Builder mode.");
		}
		Path candidate = requested.toAbsolutePath().normalize();
		requireContainedPath(workspaceRoot, candidate, label);
		if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)
			&& Files.isSymbolicLink(candidate)) {
			throw new IOException(label + " is a symbolic link.");
		}
		Path parent = candidate.getParent();
		if (parent == null
			|| !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(parent)
			|| !parent.toRealPath().startsWith(workspaceRoot)) {
			throw new IOException(label + " parent is missing or unsafe.");
		}
		return candidate;
	}

	public Path validateRuntimeEvidenceFile(String requested, String label)
		throws IOException {
		if (!adaptiveMode || requested == null || requested.trim().isEmpty()) {
			throw new IOException(label + " is required in adaptive Builder mode.");
		}
		Path candidate = Paths.get(requested.trim());
		if (!candidate.isAbsolute()) candidate = serverRoot.resolve(candidate);
		candidate = candidate.toAbsolutePath().normalize();
		if (!candidate.startsWith(workingRoot)) {
			throw new IOException(label + " must remain in the isolated working tree.");
		}
		requireContainedRegularFile(workspaceRoot, candidate, label);
		try {
			Object links = Files.getAttribute(
				candidate, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
			if (links instanceof Number && ((Number)links).longValue() != 1L) {
				throw new IOException(label + " is hard linked.");
			}
		} catch (UnsupportedOperationException unsupported) {
			candidate.toRealPath();
		} catch (IllegalArgumentException unsupported) {
			candidate.toRealPath();
		}
		return candidate.toRealPath();
	}

	private static Path requireDirectory(Path requested, String label) throws IOException {
		Path normalized = requested.toAbsolutePath().normalize();
		if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized)) {
			throw new IOException(label + " is missing or unsafe: " + normalized);
		}
		return normalized.toRealPath();
	}

	private static Path requireContainedDirectory(Path root, Path requested, String label) throws IOException {
		requireContainedPath(root, requested, label);
		if (!Files.isDirectory(requested, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(requested)) {
			throw new IOException(label + " is missing or unsafe: " + requested);
		}
		Path real = requested.toRealPath();
		if (!real.startsWith(root)) {
			throw new IOException(label + " resolves outside the World Builder workspace.");
		}
		return real;
	}

	private static void requireContainedRegularFile(Path root, Path requested, String label) throws IOException {
		requireContainedPath(root, requested, label);
		if (!Files.isRegularFile(requested, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(requested)) {
			throw new IOException(label + " is missing or unsafe: " + requested);
		}
		if (!requested.toRealPath().startsWith(root)) {
			throw new IOException(label + " resolves outside the World Builder workspace.");
		}
	}

	private static void requireContainedPath(Path root, Path requested, String label) throws IOException {
		Path candidate = requested.toAbsolutePath().normalize();
		if (!candidate.startsWith(root)) {
			throw new IOException(label + " escapes the World Builder workspace.");
		}
		Path current = root;
		for (Path part : root.relativize(candidate)) {
			current = current.resolve(part);
			if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
				throw new IOException(label + " contains a symbolic link: " + current);
			}
		}
	}
}
