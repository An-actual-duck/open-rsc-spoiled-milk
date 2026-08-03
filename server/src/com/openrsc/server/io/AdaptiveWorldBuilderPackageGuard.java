package com.openrsc.server.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Closed, bounded and platform-portable inventory for adaptive Builder input.
 *
 * <p>The historical package loader deliberately accepts only manifest-reached
 * payloads. Adaptive authoring has a stronger boundary: no untracked file,
 * link, hard-link alias, case alias or platform-hostile path may enter the
 * isolated runtime or a copy-on-write save.</p>
 */
public final class AdaptiveWorldBuilderPackageGuard {
	public static final int MAX_FILES = 70000;
	public static final int MAX_DIRECTORIES = 70000;
	public static final long MAX_FILE_BYTES = 32L * 1024L * 1024L;
	public static final long MAX_TOTAL_BYTES = 1024L * 1024L * 1024L;

	private AdaptiveWorldBuilderPackageGuard() {
	}

	public static Inventory requireClosedPackage(Path requestedRoot)
		throws IOException {
		Inventory before = inventory(requestedRoot);
		NativeLayeredWorldPackage loaded =
			NativeLayeredWorldPackage.load(before.getRoot());
		Inventory inventory = inventory(before.getRoot());
		if (!before.getFingerprint().equals(inventory.getFingerprint())) {
			throw new IOException(
				"Adaptive layered package changed while it was being validated");
		}
		if (!loaded.getExpectedRelativeFilePaths().equals(
				inventory.getEntries().keySet())) {
			Set<String> missing = new HashSet<String>(
				loaded.getExpectedRelativeFilePaths());
			missing.removeAll(inventory.getEntries().keySet());
			Set<String> extra = new HashSet<String>(
				inventory.getEntries().keySet());
			extra.removeAll(loaded.getExpectedRelativeFilePaths());
			throw new IOException(
				"Adaptive layered package inventory is not closed; missing="
					+ sorted(missing) + "; extra=" + sorted(extra));
		}
		requireLoadedHash(
			inventory, "manifest.json", loaded.getManifestSha256());
		for (NativeLayeredTerrainSector sector
			: loaded.getTerrainSectors().values()) {
			requireLoadedHash(
				inventory, sector.getSourcePath(), sector.getSourceSha256());
		}
		for (NativeLayeredPlacementSet set
			: loaded.getPlacementSets().values()) {
			requireLoadedHash(
				inventory, set.getSourcePath(), set.getSourceSha256());
		}
		return inventory;
	}

	private static void requireLoadedHash(
		Inventory inventory, String path, String loadedSha256)
		throws IOException {
		Entry entry = inventory.getEntries().get(path);
		if (entry == null || !entry.getSha256().equals(loadedSha256)) {
			throw new IOException(
				"Adaptive layered package changed while loading: " + path);
		}
	}

	public static Inventory inventory(Path requestedRoot) throws IOException {
		Path root = requireSafeDirectory(requestedRoot);
		final Map<String, Entry> entries =
			new java.util.TreeMap<String, Entry>();
		final Set<String> foldedPaths = new HashSet<String>();
		final Set<Object> fileKeys = new HashSet<Object>();
		final long[] totalBytes = new long[] {0L};
		final int[] directoryCount = new int[] {0};
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(
				Path directory, BasicFileAttributes attributes) throws IOException {
				if (Files.isSymbolicLink(directory)) {
					throw new IOException(
						"Adaptive layered package contains a symbolic-link directory");
				}
				if (++directoryCount[0] > MAX_DIRECTORIES) {
					throw new IOException(
						"Adaptive layered package directory limit exceeded: "
							+ MAX_DIRECTORIES);
				}
				if (!directory.equals(root)) {
					validateRelativePath(portable(root.relativize(directory)));
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(
				Path file, BasicFileAttributes attributes) throws IOException {
				if (!attributes.isRegularFile() || Files.isSymbolicLink(file)) {
					throw new IOException(
						"Adaptive layered package contains a non-regular file");
				}
				if (entries.size() >= MAX_FILES) {
					throw new IOException(
						"Adaptive layered package file limit exceeded: " + MAX_FILES);
				}
				String relative = portable(root.relativize(file));
				validateRelativePath(relative);
				String folded = relative.toLowerCase(Locale.ROOT);
				if (!foldedPaths.add(folded)) {
					throw new IOException(
						"Adaptive layered package contains a case-colliding path: "
							+ relative);
				}
				long size = attributes.size();
				if (size < 1L || size > MAX_FILE_BYTES) {
					throw new IOException(
						"Adaptive layered package file size is outside 1.."
							+ MAX_FILE_BYTES + ": " + relative);
				}
				totalBytes[0] = Math.addExact(totalBytes[0], size);
				if (totalBytes[0] > MAX_TOTAL_BYTES) {
					throw new IOException(
						"Adaptive layered package byte limit exceeded: "
							+ MAX_TOTAL_BYTES);
				}
				requireSingleLink(file, attributes, fileKeys);
				entries.put(relative, new Entry(size, sha256(file)));
				return FileVisitResult.CONTINUE;
			}
		});
		if (!entries.containsKey("manifest.json")) {
			throw new IOException(
				"Adaptive layered package is missing manifest.json");
		}
		StringBuilder canonical = new StringBuilder();
		for (Map.Entry<String, Entry> entry : entries.entrySet()) {
			canonical.append(entry.getKey()).append('\0')
				.append(entry.getValue().size).append('\0')
				.append(entry.getValue().sha256).append('\n');
		}
		return new Inventory(
			root, entries, totalBytes[0], sha256(canonical.toString()));
	}

	private static Path requireSafeDirectory(Path requested) throws IOException {
		if (requested == null) {
			throw new IOException("Adaptive layered package directory is required");
		}
		Path normalized = requested.toAbsolutePath().normalize();
		Path current = normalized.getRoot();
		if (current == null) {
			throw new IOException("Adaptive layered package path has no root");
		}
		for (Path part : normalized) {
			current = current.resolve(part);
			if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
				&& Files.isSymbolicLink(current)) {
				throw new IOException(
					"Adaptive layered package path contains a symbolic link");
			}
		}
		if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(normalized)) {
			throw new IOException(
				"Adaptive layered package directory is missing or unsafe");
		}
		return normalized.toRealPath();
	}

	private static void requireSingleLink(
		Path file, BasicFileAttributes attributes, Set<Object> fileKeys)
		throws IOException {
		Object key = attributes.fileKey();
		if (key != null && !fileKeys.add(key)) {
			throw new IOException(
				"Adaptive layered package contains a repeated file identity");
		}
		try {
			Object raw = Files.getAttribute(
				file, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
			if (raw instanceof Number && ((Number) raw).longValue() != 1L) {
				throw new IOException(
					"Adaptive layered package contains a hard-linked file");
			}
		} catch (UnsupportedOperationException ignored) {
			file.toRealPath();
		} catch (IllegalArgumentException ignored) {
			file.toRealPath();
		}
	}

	private static void validateRelativePath(String relative) throws IOException {
		if (relative.isEmpty() || relative.startsWith("/")
			|| relative.endsWith("/") || relative.contains("//")) {
			throw new IOException(
				"Adaptive layered package contains an invalid relative path");
		}
		for (String component : relative.split("/", -1)) {
			if (component.isEmpty() || ".".equals(component)
				|| "..".equals(component)
				|| component.endsWith(".") || component.endsWith(" ")) {
				throw new IOException(
					"Adaptive layered package path is not portable: " + relative);
			}
			for (int index = 0; index < component.length(); index++) {
				char value = component.charAt(index);
				if (value < 32 || value == 127 || "<>:\"\\|?*".indexOf(value) >= 0) {
					throw new IOException(
						"Adaptive layered package path is not portable: " + relative);
				}
			}
			String base = component;
			int dot = base.indexOf('.');
			if (dot >= 0) base = base.substring(0, dot);
			String upper = base.toUpperCase(Locale.ROOT);
			if ("CON".equals(upper) || "PRN".equals(upper)
				|| "AUX".equals(upper) || "NUL".equals(upper)
				|| upper.matches("COM[1-9]") || upper.matches("LPT[1-9]")) {
				throw new IOException(
					"Adaptive layered package path uses a Windows device name: "
						+ relative);
			}
		}
	}

	private static String portable(Path path) {
		return path.toString().replace(path.getFileSystem().getSeparator(), "/");
	}

	private static String sha256(Path path) throws IOException {
		MessageDigest digest = digest();
		byte[] buffer = new byte[64 * 1024];
		try (InputStream input = Files.newInputStream(path)) {
			int count;
			while ((count = input.read(buffer)) != -1) {
				digest.update(buffer, 0, count);
			}
		}
		return hex(digest.digest());
	}

	private static String sha256(String value) {
		MessageDigest digest = digest();
		digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		return hex(digest.digest());
	}

	private static MessageDigest digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private static String hex(byte[] value) {
		StringBuilder result = new StringBuilder(value.length * 2);
		for (byte part : value) result.append(String.format("%02x", part & 0xff));
		return result.toString();
	}

	private static List<String> sorted(Set<String> values) {
		List<String> result = new ArrayList<String>(values);
		Collections.sort(result);
		return result;
	}

	public static final class Entry {
		private final long size;
		private final String sha256;

		private Entry(long size, String sha256) {
			this.size = size;
			this.sha256 = sha256;
		}

		public long getSize() { return size; }
		public String getSha256() { return sha256; }
	}

	public static final class Inventory {
		private final Path root;
		private final Map<String, Entry> entries;
		private final long totalBytes;
		private final String fingerprint;

		private Inventory(
			Path root, Map<String, Entry> entries, long totalBytes,
			String fingerprint) {
			this.root = root;
			this.entries = Collections.unmodifiableMap(
				new LinkedHashMap<String, Entry>(entries));
			this.totalBytes = totalBytes;
			this.fingerprint = fingerprint;
		}

		public Path getRoot() { return root; }
		public Map<String, Entry> getEntries() { return entries; }
		public long getTotalBytes() { return totalBytes; }
		public String getFingerprint() { return fingerprint; }
	}
}
