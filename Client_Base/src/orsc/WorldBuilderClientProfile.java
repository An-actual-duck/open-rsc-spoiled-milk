package orsc;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Explicit desktop-only connection profile for the isolated World Builder runtime. */
public final class WorldBuilderClientProfile {
	public static final String ENABLED_PROPERTY = "openrsc.worldBuilderMode";
	public static final String HOST_PROPERTY = "openrsc.worldBuilderHost";
	public static final String PORT_PROPERTY = "openrsc.worldBuilderPort";
	public static final String CREDENTIAL_FILE_PROPERTY = "openrsc.worldBuilderCredentialFile";
	public static final String PROJECT_NAME_PROPERTY = "openrsc.worldBuilderProjectName";
	public static final String SOURCE_REVISION_PROPERTY = "openrsc.worldBuilderSourceRevision";
	public static final String ADAPTIVE_PROPERTY =
		"openrsc.worldBuilderAdaptiveMode";
	public static final String RUNTIME_BINDING_FILE_PROPERTY =
		"openrsc.worldBuilderRuntimeBindingFile";
	public static final String DEFINITION_EVIDENCE_FILE_PROPERTY =
		"openrsc.worldBuilderDefinitionEvidenceFile";
	public static final String ASSET_EVIDENCE_FILE_PROPERTY =
		"openrsc.worldBuilderAssetEvidenceFile";
	public static final String LAYERED_REVIEW_PROPERTY = "openrsc.worldBuilderLayeredReview";
	public static final String LAYERED_TERRAIN_DRAFT_PROPERTY =
		"openrsc.worldBuilderLayeredTerrainDraft";
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
	private static final int MAX_ADAPTIVE_LEVELS = 64;
	private static WorldBuilderClientProfile current = disabled();

	private final boolean enabled;
	private final String host;
	private final int port;
	private final String credential;
	private final String projectName;
	private final String sourceRevision;
	private final boolean layeredReview;
	private final boolean layeredTerrainDraft;
	private final String layeredPackageId;
	private final String layeredPackageVersion;
	private final String layeredManifestSha256;
	private final String layeredWorldSpace;
	private final int[] layeredLevels;
	private final NavigableSet<Integer> activeLayeredLevels =
		new TreeSet<Integer>();
	private final boolean adaptive;
	private final AdaptiveWorldBuilderClientSession adaptiveSession;
	private boolean adaptiveServerBindingAccepted;
	private boolean adaptiveNativeContextAccepted;

	private WorldBuilderClientProfile(boolean enabled, String host, int port, String credential,
		String projectName, String sourceRevision, boolean layeredReview,
		boolean layeredTerrainDraft,
		String layeredPackageId, String layeredPackageVersion,
		String layeredManifestSha256, String layeredWorldSpace, int[] layeredLevels,
		boolean adaptive, AdaptiveWorldBuilderClientSession adaptiveSession) {
		this.enabled = enabled;
		this.host = host;
		this.port = port;
		this.credential = credential;
		this.projectName = projectName;
		this.sourceRevision = sourceRevision;
		this.layeredReview = layeredReview;
		this.layeredTerrainDraft = layeredTerrainDraft;
		this.layeredPackageId = layeredPackageId;
		this.layeredPackageVersion = layeredPackageVersion;
		this.layeredManifestSha256 = layeredManifestSha256;
		this.layeredWorldSpace = layeredWorldSpace;
		this.layeredLevels = layeredLevels.clone();
		for (int level : this.layeredLevels) {
			this.activeLayeredLevels.add(Integer.valueOf(level));
		}
		this.adaptive = adaptive;
		this.adaptiveSession = adaptiveSession;
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
		Path credentialPath =
			Paths.get(credentialFile).toAbsolutePath().normalize();
		String projectName = validateProjectName(System.getProperty(PROJECT_NAME_PROPERTY, "Builder Project"));
		String sourceRevision = System.getProperty(SOURCE_REVISION_PROPERTY, "").trim().toLowerCase();
		if (!SOURCE_REVISION_PATTERN.matcher(sourceRevision).matches()) {
			throw new IllegalArgumentException("World Builder source revision is invalid");
		}
		boolean adaptive = strictBoolean(
			ADAPTIVE_PROPERTY, System.getProperty(ADAPTIVE_PROPERTY, "false"));
		boolean layeredReview = strictBoolean(
			LAYERED_REVIEW_PROPERTY,
			System.getProperty(LAYERED_REVIEW_PROPERTY, "false"));
		boolean layeredTerrainDraft = strictBoolean(
			LAYERED_TERRAIN_DRAFT_PROPERTY,
			System.getProperty(LAYERED_TERRAIN_DRAFT_PROPERTY, "false"));
		if (layeredTerrainDraft && !layeredReview) {
			throw new IllegalArgumentException(
				LAYERED_TERRAIN_DRAFT_PROPERTY + " requires layered review");
		}
		String layeredPackageId = "";
		String layeredPackageVersion = "";
		String layeredManifestSha256 = "";
		String layeredWorldSpace = "";
		int[] layeredLevels = new int[0];
		AdaptiveWorldBuilderClientSession adaptiveSession = null;
		if (adaptive) {
			String bindingFile = System.getProperty(
				RUNTIME_BINDING_FILE_PROPERTY, "").trim();
			if (bindingFile.isEmpty()) {
				throw new IllegalArgumentException(
					RUNTIME_BINDING_FILE_PROPERTY + " is required");
			}
			adaptiveSession = AdaptiveWorldBuilderClientSession.load(
				Paths.get(bindingFile));
			String definitionEvidence = System.getProperty(
				DEFINITION_EVIDENCE_FILE_PROPERTY, "").trim();
			String assetEvidence = System.getProperty(
				ASSET_EVIDENCE_FILE_PROPERTY, "").trim();
			if (definitionEvidence.isEmpty() || assetEvidence.isEmpty()) {
				throw new IllegalArgumentException(
					"Adaptive client definition and asset evidence files are required");
			}
			adaptiveSession.requireEvidence(
				Paths.get(definitionEvidence), Paths.get(assetEvidence));
			credentialPath = adaptiveSession.requireCredential(credentialPath);
			layeredReview = true;
			layeredTerrainDraft = true;
			layeredPackageId = adaptiveSession.packageId();
			layeredPackageVersion = adaptiveSession.packageVersion();
			layeredManifestSha256 = adaptiveSession.manifestSha256();
			layeredWorldSpace = adaptiveSession.initialWorldSpace();
			layeredLevels = adaptiveSession.levels();
		} else if (layeredReview) {
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
		String credential = readCredential(credentialPath);
		current = new WorldBuilderClientProfile(
			true, host, port, credential, projectName, sourceRevision,
			layeredReview, layeredTerrainDraft,
			layeredPackageId, layeredPackageVersion,
			layeredManifestSha256, layeredWorldSpace, layeredLevels,
			adaptive, adaptiveSession);
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

	public boolean isLayeredTerrainDraft() {
		return enabled && layeredReview && layeredTerrainDraft;
	}

	public boolean isAdaptive() {
		return enabled && adaptive && adaptiveSession != null;
	}

	/**
	 * The content-neutral adaptive runtime is the only client profile allowed
	 * to start without a packed legacy landscape archive.
	 */
	public boolean isStrictAdaptiveTerrain() {
		return isAdaptive();
	}

	public String strictAdaptiveMapIdentity() {
		if (!isStrictAdaptiveTerrain()) {
			throw new IllegalStateException(
				"Strict adaptive terrain identity is unavailable");
		}
		return adaptiveSession.manifestSha256();
	}

	/** Called only for the server-authored editor-open receipt after builderbind. */
	public synchronized void acceptAdaptiveServerBinding() {
		if (isStrictAdaptiveTerrain()) {
			adaptiveServerBindingAccepted = true;
		}
	}

	/**
	 * Accepts one server scene scope only after the authenticated adaptive
	 * binding and exact native layered protocol have both been established.
	 */
	public synchronized void validateAdaptiveNativeTerrainContext(
		int protocolVersion,
		String worldSpace,
		int level,
		int x,
		int y,
		NativeLayeredTerrainSnapshot terrain) {
		if (!isStrictAdaptiveTerrain()) {
			return;
		}
		if (!adaptiveServerBindingAccepted) {
			throw new IllegalStateException(
				"Strict adaptive terrain arrived before authenticated Builder binding");
		}
		if (protocolVersion
				!= LayeredSceneContextState.ATOMIC_NATIVE_LAYERED_PROTOCOL_VERSION) {
			throw new IllegalStateException(
				"Strict adaptive terrain requires the atomic native layered protocol");
		}
		if (terrain == null
			|| terrain.getProtocolVersion() != protocolVersion
			|| !adaptiveSession.packageIdentity().equals(
				terrain.packageIdentity())
			|| !adaptiveSession.initialWorldSpace().equals(worldSpace)
			|| (!declaresLayer(level) && !layeredTerrainDraft)
			|| !terrain.covers(worldSpace, level, x, y)) {
			throw new IllegalStateException(
				"Strict adaptive terrain context does not match the bound native package");
		}
		if (!declaresLayer(level)
			&& activeLayeredLevels.size() >= MAX_ADAPTIVE_LEVELS) {
			throw new IllegalStateException(
				"Strict adaptive terrain context exceeds the signed-level limit");
		}
	}

	/**
	 * Commits a previously validated server terrain context. The startup
	 * binding remains immutable; a newly authored level lives only in this
	 * authenticated Builder process until the normal project save publishes it.
	 */
	public synchronized void acceptAdaptiveNativeTerrainContext(
		int protocolVersion,
		String worldSpace,
		int level,
		int x,
		int y,
		NativeLayeredTerrainSnapshot terrain) {
		if (!isStrictAdaptiveTerrain()) {
			return;
		}
		validateAdaptiveNativeTerrainContext(
			protocolVersion, worldSpace, level, x, y, terrain);
		activeLayeredLevels.add(Integer.valueOf(level));
		// The immutable binding supplies the first-run location, while the isolated
		// Builder database may truthfully restore a later creator position. The
		// authenticated server context is still restricted to the exact bound
		// package, active level set, world space, and resident terrain coverage above.
		adaptiveNativeContextAccepted = true;
	}

	public synchronized boolean isAdaptiveWorldStateReady(
		boolean initialRegionLoaded,
		boolean nativeTerrainResident) {
		return !isStrictAdaptiveTerrain()
			|| adaptiveServerBindingAccepted
				&& adaptiveNativeContextAccepted
				&& initialRegionLoaded
				&& nativeTerrainResident;
	}

	public synchronized void resetAdaptiveRuntimeState() {
		adaptiveServerBindingAccepted = false;
		adaptiveNativeContextAccepted = false;
	}

	public boolean canAuthorLevel(int level) {
		return isAdaptive() || !isHistoricalSourceLevel(level);
	}

	public String runtimeBindingToken() {
		if (!isAdaptive()) {
			throw new IllegalStateException(
				"Adaptive runtime binding is unavailable");
		}
		return adaptiveSession.token();
	}

	public void requireNativePackageIdentity(
		String packageId, String packageVersion, String manifestSha256) {
		if (isAdaptive()) {
			adaptiveSession.requirePackageIdentity(
				packageId, packageVersion, manifestSha256);
		}
	}

	public void requireClientDefinitions() {
		if (isAdaptive()) adaptiveSession.requireClientDefinitions();
	}

	public ProjectContentBundle contentBundle() {
		return isAdaptive()
			? adaptiveSession.contentBundle() : ProjectContentBundle.empty();
	}

	public boolean hasAuthoringDefinitionBinding() {
		return isAdaptive();
	}

	/** Exact project-bound IDs; meaningful only for the adaptive profile. */
	public int[] definitionIds(String family) {
		return isAdaptive() ? adaptiveSession.definitionIds(family) : new int[0];
	}

	public boolean isDefinitionAllowed(String family, int id) {
		return !isAdaptive() || adaptiveSession.allowsDefinition(family, id);
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

	public synchronized String layeredLevelsLabel() {
		StringBuilder label = new StringBuilder();
		int index = 0;
		for (Integer level : activeLayeredLevels) {
			if (index++ > 0) label.append(',');
			label.append(level.intValue());
		}
		return label.toString();
	}

	public synchronized boolean declaresLayer(int level) {
		return activeLayeredLevels.contains(Integer.valueOf(level));
	}

	private static WorldBuilderClientProfile disabled() {
		return new WorldBuilderClientProfile(
			false, null, 0, null, "", "", false, false,
			"", "", "", "", new int[0], false, null);
	}

	private static boolean isHistoricalSourceLevel(int level) {
		return level == -2 || level == -1 || level == 0
			|| level == 1 || level == 2 || level == 10;
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
