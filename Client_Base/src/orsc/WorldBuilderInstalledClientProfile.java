package orsc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.json.JSONObject;

/**
 * Verified normal-player bootstrap for an installed World Builder package.
 *
 * <p>The descriptor is installed atomically beside the client package. Its
 * exact manifest hash replaces the legacy archive MD5 as the pre-login map
 * identity, allowing the client to wait for server-authored native terrain
 * without opening {@code Custom_Landscape.orsc}.</p>
 */
public final class WorldBuilderInstalledClientProfile {
	public static final String PROFILE_PROPERTY =
		"openrsc.worldBuilderInstalledClientProfile";
	public static final String DEFAULT_PROFILE =
		"world-builder-configs/installed-client.json";
	private static final String MANIFEST_TYPE =
		"world-builder-installed-client-profile";
	private static final Set<String> PROFILE_KEYS = keys(
		"schemaVersion", "manifestType", "active", "packageId",
		"packageVersion", "packageFingerprintSha256", "manifestSha256",
		"packageRelativePath");
	private static final Set<String> PACKAGE_KEYS = keys(
		"schemaVersion", "packageType", "packageId", "packageVersion",
		"coordinateModel", "worldSpaces", "levels", "storage",
		"terrainSectors", "placementSets");
	private static final String SHA256 = "[0-9a-f]{64}";
	private static volatile WorldBuilderInstalledClientProfile current;

	private final boolean enabled;
	private final String packageId;
	private final String packageVersion;
	private final String packageFingerprintSha256;
	private final String manifestSha256;
	private final Path packageRoot;

	private WorldBuilderInstalledClientProfile(boolean enabled, String packageId,
		String packageVersion, String packageFingerprintSha256,
		String manifestSha256, Path packageRoot) {
		this.enabled = enabled;
		this.packageId = packageId;
		this.packageVersion = packageVersion;
		this.packageFingerprintSha256 = packageFingerprintSha256;
		this.manifestSha256 = manifestSha256;
		this.packageRoot = packageRoot;
	}

	public static WorldBuilderInstalledClientProfile current() {
		WorldBuilderInstalledClientProfile loaded = current;
		if (loaded != null) return loaded;
		synchronized (WorldBuilderInstalledClientProfile.class) {
			if (current == null) {
				try {
					current = loadConfigured();
				} catch (IOException failure) {
					throw new IllegalStateException(
						"Installed World Builder client profile is invalid: "
							+ failure.getMessage(), failure);
				}
			}
			return current;
		}
	}

	static synchronized void resetForTests() {
		current = null;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public String mapIdentity() {
		if (!enabled) throw new IllegalStateException(
			"Installed World Builder map identity is unavailable");
		return manifestSha256;
	}

	public String packageId() {
		return packageId;
	}

	public String packageVersion() {
		return packageVersion;
	}

	public String packageFingerprintSha256() {
		return packageFingerprintSha256;
	}

	public Path packageRoot() {
		return packageRoot;
	}

	private static WorldBuilderInstalledClientProfile loadConfigured()
		throws IOException {
		String configured = System.getProperty(PROFILE_PROPERTY, DEFAULT_PROFILE).trim();
		if (configured.isEmpty()) throw new IOException(
			"Installed client profile path is empty");
		Path profile = Paths.get(configured).toAbsolutePath().normalize();
		if (!Files.exists(profile, LinkOption.NOFOLLOW_LINKS)) return disabled();
		requireRegular(profile, "installed client profile");
		Path profileDirectory = profile.getParent();
		if (profileDirectory == null || profileDirectory.getParent() == null) {
			throw new IOException("Installed client profile has no client root");
		}
		Path clientRoot = profileDirectory.getParent().toRealPath();
		JSONObject document = object(profile, "installed client profile");
		requireKeys(document, PROFILE_KEYS, "installed client profile");
		if (exactInt(document, "schemaVersion") != 1
			|| !MANIFEST_TYPE.equals(text(document, "manifestType"))) {
			throw new IOException("Installed client profile identity is unsupported");
		}
		Object rawActive = document.opt("active");
		if (!(rawActive instanceof Boolean)) throw new IOException(
			"Installed client profile active flag is invalid");
		if (!((Boolean)rawActive).booleanValue()) return disabled();
		String packageId = identifier(document, "packageId");
		String packageVersion = identifier(document, "packageVersion");
		String packageFingerprint = hash(document, "packageFingerprintSha256");
		String manifestHash = hash(document, "manifestSha256");
		String relative = text(document, "packageRelativePath");
		Path relativePath = Paths.get(relative).normalize();
		if (relativePath.isAbsolute() || relativePath.getNameCount() < 4
			|| !"world-builder".equals(relativePath.getName(0).toString())
			|| !"packages".equals(relativePath.getName(1).toString())
			|| !packageFingerprint.equals(relativePath.getName(2).toString())
			|| !"package".equals(relativePath.getFileName().toString())
			|| !relativePath.toString().replace('\\', '/').equals(relative)) {
			throw new IOException("Installed client package path is unsafe");
		}
		Path packageRoot = clientRoot.resolve(relativePath).normalize();
		if (!packageRoot.startsWith(clientRoot)
			|| !Files.isDirectory(packageRoot, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(packageRoot)) {
			throw new IOException("Installed client package root is missing or unsafe");
		}
		Path manifest = packageRoot.resolve("manifest.json").normalize();
		requireRegular(manifest, "installed package manifest");
		if (!manifestHash.equals(sha256(manifest))) throw new IOException(
			"Installed package manifest SHA-256 does not match the client profile");
		JSONObject packageDocument = object(manifest, "installed package manifest");
		requireKeys(packageDocument, PACKAGE_KEYS, "installed package manifest");
		if (exactInt(packageDocument, "schemaVersion") != 1
			|| !"layered-world".equals(text(packageDocument, "packageType"))
			|| !"signed-layered-v1".equals(
				text(packageDocument, "coordinateModel"))
			|| !packageId.equals(text(packageDocument, "packageId"))
			|| !packageVersion.equals(text(packageDocument, "packageVersion"))) {
			throw new IOException("Installed package identity does not match the client profile");
		}
		return new WorldBuilderInstalledClientProfile(true, packageId,
			packageVersion, packageFingerprint, manifestHash,
			packageRoot.toRealPath());
	}

	private static WorldBuilderInstalledClientProfile disabled() {
		return new WorldBuilderInstalledClientProfile(
			false, "", "", "", "", null);
	}

	private static JSONObject object(Path path, String label) throws IOException {
		try {
			return new JSONObject(new String(
				Files.readAllBytes(path), StandardCharsets.UTF_8));
		} catch (RuntimeException malformed) {
			throw new IOException(label + " JSON is malformed", malformed);
		}
	}

	private static void requireRegular(Path path, String label) throws IOException {
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(path)) {
			throw new IOException(label + " is missing or unsafe");
		}
	}

	private static void requireKeys(
		JSONObject value, Set<String> expected, String label) throws IOException {
		if (!new TreeSet<String>(value.keySet()).equals(expected)) {
			throw new IOException(label + " fields are not exact");
		}
	}

	private static int exactInt(JSONObject value, String key) throws IOException {
		Object raw = value.opt(key);
		if (!(raw instanceof Number)
			|| ((Number)raw).longValue() != ((Number)raw).doubleValue()
			|| ((Number)raw).longValue() < Integer.MIN_VALUE
			|| ((Number)raw).longValue() > Integer.MAX_VALUE) {
			throw new IOException("Installed client profile " + key + " is invalid");
		}
		return ((Number)raw).intValue();
	}

	private static String identifier(JSONObject value, String key)
		throws IOException {
		String result = text(value, key);
		if (!result.matches("[a-z0-9][a-z0-9._-]{0,127}")) {
			throw new IOException("Installed client profile " + key + " is invalid");
		}
		return result;
	}

	private static String hash(JSONObject value, String key) throws IOException {
		String result = text(value, key).toLowerCase(Locale.ROOT);
		if (!result.matches(SHA256)) throw new IOException(
			"Installed client profile " + key + " is invalid");
		return result;
	}

	private static String text(JSONObject value, String key) throws IOException {
		Object raw = value.opt(key);
		if (!(raw instanceof String) || ((String)raw).isEmpty()) {
			throw new IOException("Installed client profile " + key + " is invalid");
		}
		return (String)raw;
	}

	private static String sha256(Path path) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[8192];
			try (InputStream input = Files.newInputStream(path)) {
				int read;
				while ((read = input.read(buffer)) >= 0) {
					if (read > 0) digest.update(buffer, 0, read);
				}
			}
			StringBuilder result = new StringBuilder(64);
			for (byte current : digest.digest()) {
				result.append(String.format(Locale.ROOT, "%02x", current & 0xff));
			}
			return result.toString();
		} catch (java.security.NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}

	private static Set<String> keys(String... values) {
		return new TreeSet<String>(Arrays.asList(values));
	}
}
