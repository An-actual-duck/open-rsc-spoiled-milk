package com.openrsc.server.io;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the bounded, content-addressed package installed by RSC World Editor.
 *
 * <p>The ordinary packed configuration is deliberately inert.  Only the exact
 * active {@code primary} layered representation enables this path.  Before the
 * server is configured, both installed package roles are independently
 * inventoried, compared, and bound to the package fingerprint in their paths.</p>
 */
public final class WorldBuilderInstalledMapActivation {
	public static final String CONFIGURATION_RELATIVE =
		"server/world-builder-configs/primary.json";
	public static final String RUNTIME_PROFILE =
		"spoiled-milk-editor-installed";

	private static final int MAX_CONFIGURATION_BYTES = 1024 * 1024;
	private static final int MAX_FILES = 65536;
	private static final long MAX_FILE_BYTES = 128L * 1024L * 1024L;
	private static final long MAX_PACKAGE_BYTES = 2L * 1024L * 1024L * 1024L;
	private static final Pattern SERVER_PACKAGE = Pattern.compile(
		"server/world-builder/packages/([0-9a-f]{64})/package");
	private static final Pattern CLIENT_PACKAGE = Pattern.compile(
		"Client_Base/world-builder/packages/([0-9a-f]{64})/package");
	private static final Set<String> CONFIGURATION_KEYS =
		Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
			"schemaVersion", "manifestType", "configurationId", "active",
			"representation", "serverMapRelativePath", "clientMapRelativePath",
			"serverRuntimeRelativePath", "clientRuntimeRelativePath",
			"serverDefinitionCatalogRelativePath",
			"clientDefinitionCatalogRelativePath", "assets", "placements")));

	private WorldBuilderInstalledMapActivation() {
	}

	public static Optional<Activation> discover() throws IOException {
		Path targetRoot = locateTargetRoot();
		if (targetRoot == null) {
			return Optional.empty();
		}
		Path configuration = requiredRegularFile(
			targetRoot, CONFIGURATION_RELATIVE, MAX_CONFIGURATION_BYTES);
		JSONObject document;
		try {
			document = new JSONObject(new String(
				Files.readAllBytes(configuration), StandardCharsets.UTF_8));
		} catch (JSONException malformed) {
			throw new IOException(
				"World Builder primary configuration is invalid JSON", malformed);
		}
		validateConfigurationIdentity(document);
		if ("packed".equals(document.getString("representation"))) {
			return Optional.empty();
		}
		validateLayeredConfiguration(document);

		String serverRelative = document.getString("serverMapRelativePath");
		String clientRelative = document.getString("clientMapRelativePath");
		Matcher serverMatch = SERVER_PACKAGE.matcher(serverRelative);
		Matcher clientMatch = CLIENT_PACKAGE.matcher(clientRelative);
		if (!serverMatch.matches() || !clientMatch.matches()
			|| !serverMatch.group(1).equals(clientMatch.group(1))) {
			throw new IOException(
				"World Builder layered configuration does not select one matching "
					+ "content-addressed server/client package");
		}

		String fingerprint = serverMatch.group(1);
		Path serverRoot = requiredDirectory(targetRoot, serverRelative);
		Path clientRoot = requiredDirectory(targetRoot, clientRelative);
		List<FileRecord> serverFiles = inventory(serverRoot);
		List<FileRecord> clientFiles = inventory(clientRoot);
		if (!serverFiles.equals(clientFiles)) {
			throw new IOException(
				"Installed World Builder server/client package roles differ");
		}
		String actualFingerprint = fingerprint(serverFiles);
		if (!fingerprint.equals(actualFingerprint)) {
			throw new IOException(
				"Installed World Builder package fingerprint does not match its path");
		}

		NativeLayeredWorldPackage loaded = NativeLayeredWorldPackage.load(serverRoot);
		NativeLayeredWorldPackage clientLoaded = NativeLayeredWorldPackage.load(clientRoot);
		if (!loaded.getManifestSha256().equals(clientLoaded.getManifestSha256())) {
			throw new IOException(
				"Installed World Builder package manifests do not agree");
		}
		return Optional.of(new Activation(
			serverRoot, clientRoot, fingerprint, loaded.getManifestSha256()));
	}

	private static void validateLayeredConfiguration(JSONObject value)
		throws IOException {
		try {
			if (!"layered".equals(value.getString("representation"))) {
				throw new IOException(
					"World Builder layered primary configuration identity is invalid");
			}
			JSONArray placements = value.getJSONArray("placements");
			if (placements.length() != 0) {
				throw new IOException(
					"World Builder layered configuration must keep placements in-package");
			}
			if (value.getJSONArray("assets").length() < 1) {
				throw new IOException(
					"World Builder layered configuration has no rendering asset evidence");
			}
		} catch (JSONException malformed) {
			throw new IOException(
				"World Builder layered primary configuration is malformed", malformed);
		}
	}

	private static void validateConfigurationIdentity(JSONObject value)
		throws IOException {
		try {
			String representation = value.getString("representation");
			if (!value.keySet().equals(CONFIGURATION_KEYS)
				|| value.getInt("schemaVersion") != 1
				|| !"world-builder-map-configuration".equals(
					value.getString("manifestType"))
				|| !"primary".equals(value.getString("configurationId"))
				|| !value.getBoolean("active")
				|| !("packed".equals(representation)
					|| "layered".equals(representation))) {
				throw new IOException(
					"World Builder primary configuration identity is invalid");
			}
		} catch (JSONException malformed) {
			throw new IOException(
				"World Builder primary configuration is malformed", malformed);
		}
	}

	private static Path locateTargetRoot() throws IOException {
		String requested = System.getProperty("openrsc.worldBuilderTargetRoot", "").trim();
		if (!requested.isEmpty()) {
			Path root = Paths.get(requested).toAbsolutePath().normalize();
			return Files.isRegularFile(root.resolve(CONFIGURATION_RELATIVE),
				LinkOption.NOFOLLOW_LINKS) ? root : null;
		}
		Path working = Paths.get("").toAbsolutePath().normalize();
		if (Files.isRegularFile(working.resolve(CONFIGURATION_RELATIVE),
			LinkOption.NOFOLLOW_LINKS)) {
			return working;
		}
		if (working.getFileName() != null
			&& "server".equals(working.getFileName().toString())
			&& working.getParent() != null
			&& Files.isRegularFile(
				working.resolve("world-builder-configs/primary.json"),
				LinkOption.NOFOLLOW_LINKS)) {
			return working.getParent();
		}
		return null;
	}

	private static Path requiredDirectory(Path targetRoot, String relative)
		throws IOException {
		Path result = targetRoot.resolve(relative).normalize();
		if (!result.startsWith(targetRoot)
			|| !Files.isDirectory(result, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(result)) {
			throw new IOException("Unsafe World Builder package directory: " + relative);
		}
		return result.toRealPath(LinkOption.NOFOLLOW_LINKS);
	}

	private static Path requiredRegularFile(
		Path targetRoot, String relative, long maximumBytes) throws IOException {
		Path result = targetRoot.resolve(relative).normalize();
		if (!result.startsWith(targetRoot)
			|| !Files.isRegularFile(result, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(result)
			|| Files.size(result) > maximumBytes) {
			throw new IOException("Unsafe World Builder file: " + relative);
		}
		return result;
	}

	private static List<FileRecord> inventory(final Path root) throws IOException {
		final List<FileRecord> files = new ArrayList<FileRecord>();
		final long[] total = {0L};
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(
				Path directory, BasicFileAttributes attributes) throws IOException {
				if (Files.isSymbolicLink(directory) || !attributes.isDirectory()) {
					throw new IOException("Unsafe directory in World Builder package");
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
				throws IOException {
				if (files.size() >= MAX_FILES || Files.isSymbolicLink(file)
					|| !attributes.isRegularFile() || attributes.size() > MAX_FILE_BYTES) {
					throw new IOException("Unsafe or oversized World Builder package file");
				}
				total[0] = Math.addExact(total[0], attributes.size());
				if (total[0] > MAX_PACKAGE_BYTES) {
					throw new IOException("World Builder package exceeds bounded size");
				}
				String relative = root.relativize(file).toString().replace('\\', '/');
				if (relative.isEmpty() || relative.startsWith("/")
					|| relative.contains("//") || relative.contains("../")) {
					throw new IOException("Unsafe World Builder package entry path");
				}
				files.add(new FileRecord(relative, sha256(file), attributes.size()));
				return FileVisitResult.CONTINUE;
			}
		});
		Collections.sort(files);
		if (files.isEmpty() || !"manifest.json".equals(files.get(0).relativePath)) {
			throw new IOException("World Builder package has no manifest inventory");
		}
		return files;
	}

	private static String fingerprint(List<FileRecord> files) {
		MessageDigest digest = newDigest();
		for (FileRecord file : files) {
			updateText(digest, file.relativePath);
			updateText(digest, file.sha256);
			updateText(digest, Long.toString(file.size));
		}
		return hex(digest.digest());
	}

	private static String sha256(Path path) throws IOException {
		MessageDigest digest = newDigest();
		try (InputStream input = Files.newInputStream(
			path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
			byte[] buffer = new byte[8192];
			int count;
			while ((count = input.read(buffer)) >= 0) {
				if (count > 0) digest.update(buffer, 0, count);
			}
		}
		return hex(digest.digest());
	}

	private static MessageDigest newDigest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private static void updateText(MessageDigest digest, String value) {
		digest.update(value.getBytes(StandardCharsets.UTF_8));
		digest.update((byte)0);
	}

	private static String hex(byte[] bytes) {
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
			result.append(Character.forDigit(value & 0x0f, 16));
		}
		return result.toString();
	}

	public static final class Activation {
		private final Path serverPackageRoot;
		private final Path clientPackageRoot;
		private final String packageFingerprintSha256;
		private final String manifestSha256;

		private Activation(Path serverPackageRoot, Path clientPackageRoot,
			String packageFingerprintSha256, String manifestSha256) {
			this.serverPackageRoot = serverPackageRoot;
			this.clientPackageRoot = clientPackageRoot;
			this.packageFingerprintSha256 = packageFingerprintSha256;
			this.manifestSha256 = manifestSha256;
		}

		public Path getServerPackageRoot() { return serverPackageRoot; }
		public Path getClientPackageRoot() { return clientPackageRoot; }
		public String getPackageFingerprintSha256() {
			return packageFingerprintSha256;
		}
		public String getManifestSha256() { return manifestSha256; }
	}

	private static final class FileRecord implements Comparable<FileRecord> {
		final String relativePath;
		final String sha256;
		final long size;

		FileRecord(String relativePath, String sha256, long size) {
			this.relativePath = relativePath;
			this.sha256 = sha256;
			this.size = size;
		}

		@Override
		public int compareTo(FileRecord other) {
			return relativePath.compareTo(other.relativePath);
		}

		@Override
		public boolean equals(Object other) {
			if (!(other instanceof FileRecord)) return false;
			FileRecord value = (FileRecord)other;
			return relativePath.equals(value.relativePath)
				&& sha256.equals(value.sha256) && size == value.size;
		}

		@Override
		public int hashCode() {
			return relativePath.hashCode();
		}
	}
}
