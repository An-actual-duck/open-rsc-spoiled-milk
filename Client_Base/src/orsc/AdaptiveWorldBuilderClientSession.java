package orsc;

import com.openrsc.client.entityhandling.EntityHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict server-produced identity proof required before adaptive auto-login. */
public final class AdaptiveWorldBuilderClientSession {
	public static final String SESSION_SCHEMA =
		"adaptive-world-builder-session-v1";
	public static final String CAPABILITY_ID =
		"adaptive-world-builder-runtime-capability-v1";
	public static final String SERVER_BUILD_ID =
		"core-framework-adaptive-builder-server-v1";
	public static final String CLIENT_BUILD_ID =
		"core-framework-adaptive-builder-client-v1";
	public static final String LOADER_ID =
		"generic-signed-layered-loader-v1";
	public static final String AUTHORING_ID =
		"generic-signed-layered-authoring-v1";
	public static final String DEFINITION_CONTRACT_ID =
		"world-builder-definition-catalog-binding-v1";
	public static final String ASSET_CONTRACT_ID =
		"world-builder-client-asset-binding-v1";
	public static final String PROTOCOL_ID =
		"world-builder-native-layered-protocol-v1";
	public static final String EFFECTIVE_COMPOSITION_ID =
		"world-builder-effective-static-composition-v1";
	public static final String PACKAGE_SCHEMA_ID =
		"layered-world-package-v1";
	public static final String COORDINATE_MODEL = "signed-layered-v1";
	public static final String PLACEMENT_ENCODING =
		"layered-world-placements-v3";
	public static final String PROFILE_ID = "adaptive-world-builder";
	private static final long MAX_BINDING_BYTES = 1024L * 1024L;
	private static final long MAX_COMPOSITION_BYTES = 64L * 1024L * 1024L;
	private static final long MAX_EVIDENCE_BYTES = 1024L * 1024L * 1024L;
	private static final Pattern ID =
		Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
	private static final Pattern VERSION =
		Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9._-]+)?");
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
	private static final Set<String> EXACT_KEYS = Collections.unmodifiableSet(
		new HashSet<String>(Arrays.asList(
			"assetContract", "assetIdentity", "assetSha256", "authoring",
			"capability", "clientBuild", "clientVersion", "coordinateModel",
			"definitionContract", "definitionIdentity", "definitionSha256",
			"effectiveComposition", "effectiveCompositionSha256", "initialLevel",
			"initialWorldSpace", "initialX", "initialY", "loader",
			"levels", "manifestSha256", "packageId", "packageInventorySha256",
			"packageSchema", "packageVersion", "placementEncoding", "profile",
			"projectOrigin", "protocol", "requiredBoundaryIds", "requiredItemIds",
			"requiredNpcIds", "requiredSceneryIds", "requiredTileIds",
			"serverBuild", "sourceBaselineInventorySha256")));

	private final Path bindingFile;
	private final Path workspaceRoot;
	private final Map<String, String> fields;
	private final String token;
	private final int[] tileIds;
	private final int[] boundaryIds;
	private final int[] sceneryIds;
	private final int[] npcIds;
	private final int[] itemIds;
	private final int[] levels;

	private AdaptiveWorldBuilderClientSession(
		Path bindingFile, Path workspaceRoot,
		Map<String, String> fields, String token,
		int[] tileIds, int[] boundaryIds, int[] sceneryIds,
		int[] npcIds, int[] itemIds, int[] levels) {
		this.bindingFile = bindingFile;
		this.workspaceRoot = workspaceRoot;
		this.fields = Collections.unmodifiableMap(
			new LinkedHashMap<String, String>(fields));
		this.token = token;
		this.tileIds = tileIds;
		this.boundaryIds = boundaryIds;
		this.sceneryIds = sceneryIds;
		this.npcIds = npcIds;
		this.itemIds = itemIds;
		this.levels = levels;
	}

	public static AdaptiveWorldBuilderClientSession load(Path requested) {
		try {
			Path path = safeRegularFile(requested, MAX_BINDING_BYTES, "runtime binding");
			Path workspace = requireProjectBindingLocation(path);
			byte[] bytes = Files.readAllBytes(path);
			for (byte value : bytes) {
				if ((value & 0xff) > 127) {
					throw new IllegalArgumentException(
						"Adaptive World Builder runtime binding must be ASCII");
				}
			}
			String document = new String(bytes, StandardCharsets.US_ASCII);
			if (document.indexOf('\r') >= 0 || !document.endsWith("\n")) {
				throw new IllegalArgumentException(
					"Adaptive World Builder runtime binding is not canonical");
			}
			String[] lines = document.split("\n", -1);
			if (lines.length < 3 || !SESSION_SCHEMA.equals(lines[0])
				|| !lines[lines.length - 1].isEmpty()) {
				throw new IllegalArgumentException(
					"Adaptive World Builder runtime binding schema is invalid");
			}
			Map<String, String> fields = new java.util.TreeMap<String, String>();
			String prior = "";
			for (int index = 1; index < lines.length - 1; index++) {
				int separator = lines[index].indexOf('=');
				if (separator < 1 || lines[index].indexOf('=', separator + 1) >= 0) {
					throw new IllegalArgumentException(
						"Adaptive World Builder runtime binding row is invalid");
				}
				String key = lines[index].substring(0, separator);
				String value = lines[index].substring(separator + 1);
				if (key.compareTo(prior) <= 0 || fields.put(key, value) != null) {
					throw new IllegalArgumentException(
						"Adaptive World Builder runtime binding keys are not canonical");
				}
				prior = key;
			}
			if (!EXACT_KEYS.equals(fields.keySet())) {
				throw new IllegalArgumentException(
					"Adaptive World Builder runtime binding keys are incomplete");
			}
			validateConstants(fields);
			String token = sha256(bytes);
			Path composition = safeRegularFile(
				path.resolveSibling("effective-static-composition.json"),
				MAX_COMPOSITION_BYTES, "effective composition evidence");
			if (!fields.get("effectiveCompositionSha256").equals(
					sha256(composition))) {
				throw new IllegalArgumentException(
					"Adaptive effective composition evidence hash mismatch");
			}
			return new AdaptiveWorldBuilderClientSession(
				path, workspace, fields, token,
				parseIds(fields.get("requiredTileIds")),
				parseIds(fields.get("requiredBoundaryIds")),
				parseIds(fields.get("requiredSceneryIds")),
				parseIds(fields.get("requiredNpcIds")),
				parseIds(fields.get("requiredItemIds")),
				parseSignedIntegers(fields.get("levels"), "levels"));
		} catch (IllegalArgumentException failure) {
			throw failure;
		} catch (Exception failure) {
			throw new IllegalArgumentException(
				"Unable to validate adaptive World Builder runtime binding", failure);
		}
	}

	public String token() { return token; }
	public String packageId() { return fields.get("packageId"); }
	public String packageVersion() { return fields.get("packageVersion"); }
	public String manifestSha256() { return fields.get("manifestSha256"); }
	public String initialWorldSpace() { return fields.get("initialWorldSpace"); }
	public int initialLevel() { return integer(fields, "initialLevel"); }
	public int initialX() { return integer(fields, "initialX"); }
	public int initialY() { return integer(fields, "initialY"); }
	public String definitionIdentity() { return fields.get("definitionIdentity"); }
	public String assetIdentity() { return fields.get("assetIdentity"); }
	public int[] levels() { return levels.clone(); }

	public void requirePackageIdentity(
		String packageId, String packageVersion, String manifestSha256) {
		if (!packageId().equals(packageId)
			|| !packageVersion().equals(packageVersion)
			|| !manifestSha256().equals(manifestSha256)) {
			throw new IllegalArgumentException(
				"Adaptive server terrain package identity differs from the bound session");
		}
	}

	public void requireEvidence(Path definitionEvidence, Path assetEvidence) {
		try {
			Path definitions = safeRegularFile(
				definitionEvidence, MAX_EVIDENCE_BYTES,
				"client definition evidence");
			Path assets = safeRegularFile(
				assetEvidence, MAX_EVIDENCE_BYTES, "client asset evidence");
			Path working = safeDirectory(
				workspaceRoot.resolve("working"), "adaptive project working tree");
			if (!definitions.startsWith(working) || !assets.startsWith(working)) {
				throw new IllegalArgumentException(
					"Adaptive client evidence must remain in the isolated project working tree");
			}
			if (!fields.get("definitionSha256").equals(sha256(definitions))) {
				throw new IllegalArgumentException(
					"Adaptive client definition evidence hash mismatch");
			}
			if (!fields.get("assetSha256").equals(sha256(assets))) {
				throw new IllegalArgumentException(
					"Adaptive client asset evidence hash mismatch");
			}
		} catch (IllegalArgumentException failure) {
			throw failure;
		} catch (Exception failure) {
			throw new IllegalArgumentException(
				"Unable to validate adaptive client evidence", failure);
		}
	}

	public Path requireCredential(Path requested) {
		try {
			Path credential = safeRegularFile(
				requested, 64L, "adaptive Builder credential");
			Path working = safeDirectory(
				workspaceRoot.resolve("working"),
				"adaptive project working tree");
			Path run = safeDirectory(
				workspaceRoot.resolve("run"), "adaptive project run directory");
			if (!credential.startsWith(working) && !credential.startsWith(run)) {
				throw new IllegalArgumentException(
					"Adaptive Builder credential must remain in project-owned runtime state");
			}
			return credential;
		} catch (IllegalArgumentException failure) {
			throw failure;
		} catch (Exception failure) {
			throw new IllegalArgumentException(
				"Unable to validate adaptive Builder credential", failure);
		}
	}

	/** Called only after the client has loaded its actual definition registry. */
	public void requireClientDefinitions() {
		for (int id : tileIds) require("tile", id, new Lookup() {
			@Override public Object get(int value) { return EntityHandler.getTileDef(value); }
		});
		for (int id : boundaryIds) require("boundary", id, new Lookup() {
			@Override public Object get(int value) { return EntityHandler.getDoorDef(value); }
		});
		for (int id : sceneryIds) require("scenery", id, new Lookup() {
			@Override public Object get(int value) { return EntityHandler.getObjectDef(value); }
		});
		for (int id : npcIds) require("NPC", id, new Lookup() {
			@Override public Object get(int value) { return EntityHandler.getNpcDef(value); }
		});
		for (int id : itemIds) require("item", id, new Lookup() {
			@Override public Object get(int value) { return EntityHandler.findItem(value, false); }
		});
	}

	private static void validateConstants(Map<String, String> fields) {
		expect(fields, "assetContract", ASSET_CONTRACT_ID);
		expect(fields, "authoring", AUTHORING_ID);
		expect(fields, "capability", CAPABILITY_ID);
		expect(fields, "clientBuild", CLIENT_BUILD_ID);
		expect(fields, "clientVersion", Integer.toString(Config.CLIENT_VERSION));
		expect(fields, "coordinateModel", COORDINATE_MODEL);
		expect(fields, "definitionContract", DEFINITION_CONTRACT_ID);
		expect(fields, "effectiveComposition", EFFECTIVE_COMPOSITION_ID);
		expect(fields, "loader", LOADER_ID);
		expect(fields, "packageSchema", PACKAGE_SCHEMA_ID);
		expect(fields, "placementEncoding", PLACEMENT_ENCODING);
		expect(fields, "profile", PROFILE_ID);
		expect(fields, "protocol", PROTOCOL_ID);
		expect(fields, "serverBuild", SERVER_BUILD_ID);
		matched(fields, "assetIdentity", ID);
		matched(fields, "assetSha256", SHA256);
		matched(fields, "definitionIdentity", ID);
		matched(fields, "definitionSha256", SHA256);
		matched(fields, "effectiveCompositionSha256", SHA256);
		matched(fields, "manifestSha256", SHA256);
		matched(fields, "packageId", ID);
		matched(fields, "packageInventorySha256", SHA256);
		matched(fields, "packageVersion", VERSION);
		matched(fields, "sourceBaselineInventorySha256", SHA256);
		if (!"global".equals(fields.get("initialWorldSpace"))) {
			throw new IllegalArgumentException(
				"Adaptive client supports only the bound global world space");
		}
		int x = integer(fields, "initialX");
		int y = integer(fields, "initialY");
		if (x < 0 || x > 32767 || y < 0 || y > 32767) {
			throw new IllegalArgumentException(
				"Adaptive initial coordinates are outside the client carrier range");
		}
		String origin = fields.get("projectOrigin");
		if (!"target-layered".equals(origin)
			&& !"target-packed".equals(origin)
			&& !"standalone-empty".equals(origin)) {
			throw new IllegalArgumentException(
				"Adaptive project origin is unsupported");
		}
		if ("standalone-empty".equals(origin)
			&& (integer(fields, "initialLevel") != 0 || x != 0 || y != 0)) {
			throw new IllegalArgumentException(
				"Standalone empty client must begin at global layer 0, coordinate 0,0");
		}
		int[] levels = parseSignedIntegers(fields.get("levels"), "levels");
		boolean initialDeclared = false;
		for (int level : levels) {
			if (level == integer(fields, "initialLevel")) initialDeclared = true;
		}
		if (!initialDeclared) {
			throw new IllegalArgumentException(
				"Adaptive initial level is not declared by the package");
		}
	}

	private static Path safeRegularFile(Path requested, long maximum, String label)
		throws IOException {
		if (requested == null) throw new IOException(label + " path is required");
		Path normalized = requested.toAbsolutePath().normalize();
		Path current = normalized.getRoot();
		if (current == null) throw new IOException(label + " path has no root");
		for (Path part : normalized) {
			current = current.resolve(part);
			if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
				&& Files.isSymbolicLink(current)) {
				throw new IOException(label + " path contains a symbolic link");
			}
		}
		if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(normalized)) {
			throw new IOException(label + " is missing or unsafe");
		}
		long size = Files.size(normalized);
		if (size < 1L || size > maximum) {
			throw new IOException(label + " size is outside its bound");
		}
		try {
			Object links = Files.getAttribute(
				normalized, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
			if (links instanceof Number && ((Number) links).longValue() != 1L) {
				throw new IOException(label + " is hard linked");
			}
		} catch (UnsupportedOperationException ignored) {
			normalized.toRealPath();
		} catch (IllegalArgumentException ignored) {
			normalized.toRealPath();
		}
		return normalized.toRealPath();
	}

	private static Path requireProjectBindingLocation(Path binding)
		throws IOException {
		if (!"runtime-binding.properties".equals(
				binding.getFileName().toString())) {
			throw new IOException(
				"Adaptive runtime binding has an unexpected file name");
		}
		Path control = binding.getParent();
		Path run = control == null ? null : control.getParent();
		Path workspace = run == null ? null : run.getParent();
		if (control == null || run == null || workspace == null
			|| !"world-builder".equals(control.getFileName().toString())
			|| !"run".equals(run.getFileName().toString())) {
			throw new IOException(
				"Adaptive runtime binding is outside the project control layout");
		}
		Path checkedWorkspace = safeDirectory(
			workspace, "adaptive project workspace");
		Path expectedControl = safeDirectory(
			checkedWorkspace.resolve("run/world-builder"),
			"adaptive project control directory");
		if (!binding.startsWith(expectedControl)) {
			throw new IOException(
				"Adaptive runtime binding escapes the project control directory");
		}
		return checkedWorkspace;
	}

	private static Path safeDirectory(Path requested, String label)
		throws IOException {
		Path normalized = requested.toAbsolutePath().normalize();
		Path current = normalized.getRoot();
		if (current == null) throw new IOException(label + " path has no root");
		for (Path part : normalized) {
			current = current.resolve(part);
			if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
				&& Files.isSymbolicLink(current)) {
				throw new IOException(label + " path contains a symbolic link");
			}
		}
		if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(normalized)) {
			throw new IOException(label + " is missing or unsafe");
		}
		return normalized.toRealPath();
	}

	private static int[] parseIds(String value) {
		if (value.isEmpty()) return new int[0];
		String[] parts = value.split(",", -1);
		if (parts.length > 65536) {
			throw new IllegalArgumentException(
				"Adaptive definition inventory exceeds its bound");
		}
		int[] result = new int[parts.length];
		int prior = -1;
		for (int index = 0; index < parts.length; index++) {
			try {
				result[index] = Integer.parseInt(parts[index]);
			} catch (NumberFormatException failure) {
				throw new IllegalArgumentException(
					"Adaptive definition inventory is invalid");
			}
			if (result[index] < 0 || result[index] <= prior) {
				throw new IllegalArgumentException(
					"Adaptive definition inventory is not canonical");
			}
			prior = result[index];
		}
		return result;
	}

	private static int[] parseSignedIntegers(String value, String label) {
		if (value == null || value.isEmpty()) {
			throw new IllegalArgumentException(
				"Adaptive " + label + " inventory is empty");
		}
		String[] parts = value.split(",", -1);
		if (parts.length > 64) {
			throw new IllegalArgumentException(
				"Adaptive " + label + " inventory exceeds its bound");
		}
		int[] result = new int[parts.length];
		for (int index = 0; index < parts.length; index++) {
			try {
				result[index] = Integer.parseInt(parts[index]);
			} catch (NumberFormatException failure) {
				throw new IllegalArgumentException(
					"Adaptive " + label + " inventory is invalid");
			}
			if (index > 0 && result[index] <= result[index - 1]) {
				throw new IllegalArgumentException(
					"Adaptive " + label + " inventory is not canonical");
			}
		}
		return result;
	}

	private static void require(String family, int id, Lookup lookup) {
		try {
			if (lookup.get(id) == null) throw new Exception();
		} catch (Exception failure) {
			throw new IllegalArgumentException(
				"Adaptive client " + family + " definition is unavailable: " + id);
		}
	}

	private static void expect(
		Map<String, String> fields, String key, String expected) {
		if (!expected.equals(fields.get(key))) {
			throw new IllegalArgumentException(
				"Adaptive runtime " + key + " identity mismatch");
		}
	}

	private static void matched(
		Map<String, String> fields, String key, Pattern pattern) {
		String value = fields.get(key);
		if (value == null || !pattern.matcher(value).matches()) {
			throw new IllegalArgumentException(
				"Adaptive runtime " + key + " identity is invalid");
		}
	}

	private static int integer(Map<String, String> fields, String key) {
		try {
			return Integer.parseInt(fields.get(key));
		} catch (Exception failure) {
			throw new IllegalArgumentException(
				"Adaptive runtime " + key + " is invalid");
		}
	}

	private static String sha256(Path path) throws IOException {
		MessageDigest digest = digest();
		byte[] buffer = new byte[64 * 1024];
		try (InputStream input = Files.newInputStream(path)) {
			int count;
			while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
		}
		return hex(digest.digest());
	}

	private static String sha256(byte[] bytes) {
		MessageDigest digest = digest();
		digest.update(bytes);
		return hex(digest.digest());
	}

	private static MessageDigest digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private static String hex(byte[] bytes) {
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
		return result.toString();
	}

	private interface Lookup {
		Object get(int value);
	}
}
