package orsc;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/** Explicit desktop-only connection profile for the isolated World Builder runtime. */
public final class WorldBuilderClientProfile {
	public static final String ENABLED_PROPERTY = "openrsc.worldBuilderMode";
	public static final String HOST_PROPERTY = "openrsc.worldBuilderHost";
	public static final String PORT_PROPERTY = "openrsc.worldBuilderPort";
	public static final String CREDENTIAL_FILE_PROPERTY = "openrsc.worldBuilderCredentialFile";
	public static final String PROJECT_NAME_PROPERTY = "openrsc.worldBuilderProjectName";
	public static final String SOURCE_REVISION_PROPERTY = "openrsc.worldBuilderSourceRevision";
	public static final String LAYERED_REVIEW_PROPERTY = "openrsc.worldBuilderLayeredReview";
	public static final String LAYERED_PACKAGE_ID_PROPERTY =
		"openrsc.worldBuilderLayeredPackageId";
	public static final String LAYERED_PACKAGE_VERSION_PROPERTY =
		"openrsc.worldBuilderLayeredPackageVersion";
	public static final String LAYERED_MANIFEST_SHA256_PROPERTY =
		"openrsc.worldBuilderLayeredManifestSha256";
	public static final String LAYERED_WORLD_SPACE_PROPERTY =
		"openrsc.worldBuilderLayeredWorldSpace";
	public static final String LAYERED_LEVELS_PROPERTY =
		"openrsc.worldBuilderLayeredLevels";
	public static final String ACCOUNT_NAME = "Builder";
	private static final Pattern CREDENTIAL_PATTERN = Pattern.compile("[A-Za-z0-9]{20}");
	private static final Pattern SOURCE_REVISION_PATTERN = Pattern.compile("[0-9a-f]{64}");
	private static final Pattern PACKAGE_ID_PATTERN =
		Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
	private static WorldBuilderClientProfile current = disabled();

	private final boolean enabled;
	private final String host;
	private final int port;
	private final String credential;
	private final String projectName;
	private final String sourceRevision;
	private final boolean layeredReview;
	private final String layeredPackageId;
	private final String layeredPackageVersion;
	private final String layeredManifestSha256;
	private final String layeredWorldSpace;
	private final int[] layeredLevels;

	private WorldBuilderClientProfile(boolean enabled, String host, int port, String credential,
		String projectName, String sourceRevision, boolean layeredReview,
		String layeredPackageId, String layeredPackageVersion,
		String layeredManifestSha256, String layeredWorldSpace, int[] layeredLevels) {
		this.enabled = enabled;
		this.host = host;
		this.port = port;
		this.credential = credential;
		this.projectName = projectName;
		this.sourceRevision = sourceRevision;
		this.layeredReview = layeredReview;
		this.layeredPackageId = layeredPackageId;
		this.layeredPackageVersion = layeredPackageVersion;
		this.layeredManifestSha256 = layeredManifestSha256;
		this.layeredWorldSpace = layeredWorldSpace;
		this.layeredLevels = layeredLevels.clone();
	}

	public static synchronized WorldBuilderClientProfile initializeFromSystemProperties() {
		String enabledValue = System.getProperty(ENABLED_PROPERTY, "false").trim();
		if (!"true".equalsIgnoreCase(enabledValue) && !"false".equalsIgnoreCase(enabledValue)) {
			throw new IllegalArgumentException(ENABLED_PROPERTY + " must be true or false");
		}
		if (!Boolean.parseBoolean(enabledValue)) {
			current = disabled();
			return current;
		}

		String host = System.getProperty(HOST_PROPERTY, "127.0.0.1").trim();
		if (!isLoopbackAddress(host)) {
			throw new IllegalArgumentException("World Builder host must resolve only to loopback addresses");
		}
		int port;
		try {
			port = Integer.parseInt(System.getProperty(PORT_PROPERTY, "").trim());
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("World Builder port is invalid");
		}
		if (port < 1 || port > 65535) {
			throw new IllegalArgumentException("World Builder port is invalid");
		}

		String credentialFile = System.getProperty(CREDENTIAL_FILE_PROPERTY, "").trim();
		if (credentialFile.isEmpty()) {
			throw new IllegalArgumentException("World Builder credential file is required");
		}
		String credential = readCredential(Paths.get(credentialFile).toAbsolutePath().normalize());
		String projectName = validateProjectName(System.getProperty(PROJECT_NAME_PROPERTY, "Builder Project"));
		String sourceRevision = System.getProperty(SOURCE_REVISION_PROPERTY, "").trim().toLowerCase();
		if (!SOURCE_REVISION_PATTERN.matcher(sourceRevision).matches()) {
			throw new IllegalArgumentException("World Builder source revision is invalid");
		}
		boolean layeredReview = strictBoolean(
			LAYERED_REVIEW_PROPERTY,
			System.getProperty(LAYERED_REVIEW_PROPERTY, "false"));
		String layeredPackageId = "";
		String layeredPackageVersion = "";
		String layeredManifestSha256 = "";
		String layeredWorldSpace = "";
		int[] layeredLevels = new int[0];
		if (layeredReview) {
			layeredPackageId = requiredIdentifier(
				LAYERED_PACKAGE_ID_PROPERTY,
				System.getProperty(LAYERED_PACKAGE_ID_PROPERTY, ""));
			layeredPackageVersion = requiredText(
				LAYERED_PACKAGE_VERSION_PROPERTY,
				System.getProperty(LAYERED_PACKAGE_VERSION_PROPERTY, ""));
			layeredManifestSha256 =
				System.getProperty(LAYERED_MANIFEST_SHA256_PROPERTY, "")
					.trim().toLowerCase();
			if (!SOURCE_REVISION_PATTERN.matcher(layeredManifestSha256).matches()) {
				throw new IllegalArgumentException(
					LAYERED_MANIFEST_SHA256_PROPERTY + " is invalid");
			}
			layeredWorldSpace = requiredIdentifier(
				LAYERED_WORLD_SPACE_PROPERTY,
				System.getProperty(LAYERED_WORLD_SPACE_PROPERTY, ""));
			layeredLevels = parseLevels(
				System.getProperty(LAYERED_LEVELS_PROPERTY, ""));
		}
		current = new WorldBuilderClientProfile(
			true, host, port, credential, projectName, sourceRevision,
			layeredReview, layeredPackageId, layeredPackageVersion,
			layeredManifestSha256, layeredWorldSpace, layeredLevels);
		return current;
	}

	public static WorldBuilderClientProfile current() {
		return current;
	}

	public static boolean isEnabled() {
		return current.enabled;
	}

	public void applyConnection() {
		if (!enabled) {
			return;
		}
		Config.SERVER_IP = host;
		Config.SERVER_PORT = port;
	}

	public String username() {
		return ACCOUNT_NAME;
	}

	public String credential() {
		return credential;
	}

	public String projectName() {
		return projectName;
	}

	public String sourceRevisionShort() {
		return sourceRevision == null ? "" : sourceRevision.substring(0, 12);
	}

	public boolean isLayeredReview() {
		return enabled && layeredReview;
	}

	public String layeredPackageId() {
		return layeredPackageId;
	}

	public String layeredPackageVersion() {
		return layeredPackageVersion;
	}

	public String layeredManifestShort() {
		return layeredManifestSha256 == null || layeredManifestSha256.length() < 12
			? "" : layeredManifestSha256.substring(0, 12);
	}

	public String layeredWorldSpace() {
		return layeredWorldSpace;
	}

	public String layeredLevelsLabel() {
		StringBuilder label = new StringBuilder();
		for (int index = 0; index < layeredLevels.length; index++) {
			if (index > 0) label.append(',');
			label.append(layeredLevels[index]);
		}
		return label.toString();
	}

	public boolean declaresLayer(int level) {
		for (int declared : layeredLevels) {
			if (declared == level) return true;
		}
		return false;
	}

	private static WorldBuilderClientProfile disabled() {
		return new WorldBuilderClientProfile(
			false, null, 0, null, "", "", false, "", "", "", "", new int[0]);
	}

	private static boolean strictBoolean(String property, String value) {
		String normalized = value == null ? "" : value.trim();
		if (!"true".equalsIgnoreCase(normalized)
			&& !"false".equalsIgnoreCase(normalized)) {
			throw new IllegalArgumentException(property + " must be true or false");
		}
		return Boolean.parseBoolean(normalized);
	}

	private static String requiredIdentifier(String property, String value) {
		String normalized = value == null ? "" : value.trim();
		if (!PACKAGE_ID_PATTERN.matcher(normalized).matches()) {
			throw new IllegalArgumentException(property + " is invalid");
		}
		return normalized;
	}

	private static String requiredText(String property, String value) {
		String normalized = value == null ? "" : value.trim();
		if (normalized.isEmpty() || normalized.length() > 64) {
			throw new IllegalArgumentException(property + " is invalid");
		}
		for (int index = 0; index < normalized.length(); index++) {
			if (Character.isISOControl(normalized.charAt(index))) {
				throw new IllegalArgumentException(property + " is invalid");
			}
		}
		return normalized;
	}

	private static int[] parseLevels(String value) {
		String normalized = value == null ? "" : value.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException(
				LAYERED_LEVELS_PROPERTY + " is required");
		}
		String[] values = normalized.split(",", -1);
		if (values.length < 1 || values.length > 64) {
			throw new IllegalArgumentException(
				LAYERED_LEVELS_PROPERTY + " is invalid");
		}
		int[] result = new int[values.length];
		for (int index = 0; index < values.length; index++) {
			try {
				result[index] = Integer.parseInt(values[index].trim());
			} catch (NumberFormatException exception) {
				throw new IllegalArgumentException(
					LAYERED_LEVELS_PROPERTY + " is invalid");
			}
			for (int prior = 0; prior < index; prior++) {
				if (result[prior] == result[index]) {
					throw new IllegalArgumentException(
						LAYERED_LEVELS_PROPERTY + " contains a duplicate");
				}
			}
		}
		return result;
	}

	private static String validateProjectName(String value) {
		String name = value == null ? "" : value.trim();
		if (name.isEmpty() || name.length() > 64) {
			throw new IllegalArgumentException("World Builder project name is invalid");
		}
		for (int index = 0; index < name.length(); index++) {
			if (Character.isISOControl(name.charAt(index))) {
				throw new IllegalArgumentException("World Builder project name is invalid");
			}
		}
		return name;
	}

	private static String readCredential(Path path) {
		try {
			if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
				throw new IllegalArgumentException("World Builder credential file is not a regular file");
			}
			long size = Files.size(path);
			if (size < 1 || size > 64) {
				throw new IllegalArgumentException("World Builder credential file has an invalid size");
			}
			String credential = new String(Files.readAllBytes(path), StandardCharsets.US_ASCII).trim();
			if (!CREDENTIAL_PATTERN.matcher(credential).matches()) {
				throw new IllegalArgumentException("World Builder credential file is invalid");
			}
			return credential;
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalArgumentException("Unable to read World Builder credential file", exception);
		}
	}

	private static boolean isLoopbackAddress(String address) {
		if (address == null || address.isEmpty()) {
			return false;
		}
		try {
			InetAddress[] resolved = InetAddress.getAllByName(address);
			if (resolved.length == 0) {
				return false;
			}
			for (InetAddress candidate : resolved) {
				if (!candidate.isLoopbackAddress()) {
					return false;
				}
			}
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}
}
