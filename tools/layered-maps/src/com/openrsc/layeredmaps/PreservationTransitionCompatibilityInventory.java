package com.openrsc.layeredmaps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Read-only provenance inventory for the transition behavior that must remain
 * active while the Preservation map is promoted to native layered storage.
 *
 * <p>This intentionally does not pretend Java control flow is a declarative
 * edge graph. ObjectTelePoints is normalized exactly; script owners are pinned
 * by path and hash and remain compatibility-runtime behavior until reviewed.
 */
final class PreservationTransitionCompatibilityInventory {
	static final String REPORT_TYPE =
		"rsc-remastered-preservation-transition-compatibility-inventory";
	static final String REPORT_SCHEMA =
		"preservation-transition-compatibility-v1";
	static final int SCHEMA_VERSION = 1;

	private static final String AUTHENTIC_ROOT =
		"server/plugins/com/openrsc/server/plugins/authentic";
	private static final String FROZEN_INVENTORY =
		"tools/layered-maps/baselines/"
			+ "preservation-transition-compatibility-v1.json";
	private static final Pattern TELEPORT_CALL =
		Pattern.compile("\\.teleport\\s*\\(");
	private static final Pattern LOCATION_CALL =
		Pattern.compile("\\.set(?:World)?Location\\s*\\(");
	private static final Pattern TELEPOINT_REFERENCE =
		Pattern.compile("\\bTelePoint\\b");
	private static final Pattern OWNER_SIGNAL =
		Pattern.compile(
			"(?i)\\b(?:teleport|telepoint)\\b|\\.set(?:World)?Location\\s*\\(");
	private static final List<String> BRIDGE_SOURCES =
		Collections.unmodifiableList(Arrays.asList(
			"server/src/com/openrsc/server/external/EntityHandler.java",
			"server/src/com/openrsc/server/model/TelePoint.java",
			"server/src/com/openrsc/server/net/rsc/handlers/GameObjectWallAction.java",
			"server/plugins/com/openrsc/server/plugins/authentic/defaults/Ladders.java"));

	Result inspect(final Path requestedRoot) throws PreflightException {
		try {
			final Path root = canonicalRoot(requestedRoot);
			final PreservationBaselineInventory.Baseline baseline =
				new PreservationBaselineInventory().inspect(root);
			final NormalizationResult normalization =
				new WorldNormalizer().normalize(root);
			final Map<String, Object> transitionGraph =
				new LinkedHashMap<String, Object>(JsonDocuments.object(
					normalization.document.get("transitionGraph")));

			final List<Object> scriptFiles = new ArrayList<Object>();
			int teleportCalls = 0;
			int locationCalls = 0;
			int telepointReferences = 0;
			for (Path path : authenticJavaSources(root)) {
				final String source = readUtf8(path);
				if (!OWNER_SIGNAL.matcher(source).find()) {
					continue;
				}
				final int fileTeleportCalls = count(TELEPORT_CALL, source);
				final int fileLocationCalls = count(LOCATION_CALL, source);
				final int fileTelepointReferences =
					count(TELEPOINT_REFERENCE, source);
				teleportCalls = Math.addExact(
					teleportCalls, fileTeleportCalls);
				locationCalls = Math.addExact(locationCalls, fileLocationCalls);
				telepointReferences = Math.addExact(
					telepointReferences, fileTelepointReferences);

				final Map<String, Object> file = fileRecord(
					root, path, scriptFamily(root.relativize(path).toString()));
				file.put("teleportCallCount", Long.valueOf(fileTeleportCalls));
				file.put("locationMutationCallCount",
					Long.valueOf(fileLocationCalls));
				file.put("telePointReferenceCount",
					Long.valueOf(fileTelepointReferences));
				scriptFiles.add(file);
			}
			if (scriptFiles.isEmpty()) {
				throw new PreflightException(
					"Preservation transition script-owner selection is empty.");
			}

			final List<Object> bridgeFiles = new ArrayList<Object>();
			for (String relative : BRIDGE_SOURCES) {
				bridgeFiles.add(fileRecord(
					root, requiredFile(root, relative),
					"runtime-transition-bridge"));
			}

			final Map<String, Object> scriptedSources =
				new LinkedHashMap<String, Object>();
			scriptedSources.put("sourceRoot", AUTHENTIC_ROOT);
			scriptedSources.put(
				"selectionRule",
				"Java files containing teleport/TelePoint or setLocation/"
					+ "setWorldLocation lexical signals");
			scriptedSources.put("semanticStatus",
				"script-owned-unparsed");
			scriptedSources.put("runtimeTreatment",
				"compatibility-runtime-preserved");
			scriptedSources.put("sourceFileCount",
				Long.valueOf(scriptFiles.size()));
			scriptedSources.put("teleportCallCount",
				Long.valueOf(teleportCalls));
			scriptedSources.put("locationMutationCallCount",
				Long.valueOf(locationCalls));
			scriptedSources.put("telePointReferenceCount",
				Long.valueOf(telepointReferences));
			scriptedSources.put("files", scriptFiles);
			scriptedSources.put("sourceSetFingerprintSha256",
				Hashes.sha256(JsonDocuments.canonical(scriptedSources)));

			final Map<String, Object> bridge = new LinkedHashMap<String, Object>();
			bridge.put("runtimeTreatment",
				"required-compatibility-bridge");
			bridge.put("files", bridgeFiles);
			bridge.put("sourceSetFingerprintSha256",
				Hashes.sha256(JsonDocuments.canonical(bridgeFiles)));

			final Map<String, Object> coverage = new LinkedHashMap<String, Object>();
			coverage.put("explicitDataEdgeCount",
				transitionGraph.get("edgeCount"));
			coverage.put("explicitDataStatus",
				"losslessly-normalized");
			coverage.put("scriptedSourceFileCount",
				Long.valueOf(scriptFiles.size()));
			coverage.put("scriptedSemanticStatus",
				"not-yet-declarative");
			coverage.put("completeDeclarativeGraph", Boolean.FALSE);

			final Map<String, Object> policy = new LinkedHashMap<String, Object>();
			policy.put("topologyClassificationIsDescriptive", Boolean.TRUE);
			policy.put("longDistanceTransitionsRemainValid", Boolean.TRUE);
			policy.put("scriptBehaviorMayBeSilentlyRewritten", Boolean.FALSE);
			policy.put("runtimePromotionMode",
				"retain-legacy-transition-consumers");
			policy.put("creatorExpansionPolicy",
				"report-findings-and-let-creator-adapt");

			final Map<String, Object> fingerprintBody =
				new LinkedHashMap<String, Object>();
			fingerprintBody.put("parentBaselineId",
				PreservationBaselineInventory.BASELINE_ID);
			fingerprintBody.put("parentBaselineFingerprintSha256",
				baseline.sourceSetFingerprint);
			fingerprintBody.put("explicitTransitionGraph", transitionGraph);
			fingerprintBody.put("scriptedSources", scriptedSources);
			fingerprintBody.put("runtimeBridge", bridge);
			fingerprintBody.put("policy", policy);
			final String fingerprint =
				Hashes.sha256(JsonDocuments.canonical(fingerprintBody));

			final Map<String, Object> document =
				new LinkedHashMap<String, Object>();
			document.put("schemaVersion", Long.valueOf(SCHEMA_VERSION));
			document.put("reportType", REPORT_TYPE);
			document.put("reportSchema", REPORT_SCHEMA);
			document.put("parentBaselineId",
				PreservationBaselineInventory.BASELINE_ID);
			document.put("parentBaselineFingerprintSha256",
				baseline.sourceSetFingerprint);
			document.put("inventoryFingerprintSha256", fingerprint);
			document.put("explicitTransitionGraph", transitionGraph);
			document.put("scriptedSources", scriptedSources);
			document.put("runtimeBridge", bridge);
			document.put("declarativeCoverage", coverage);
			document.put("policy", policy);
			requireFrozenInventory(root, frozenInventory(
				baseline.sourceSetFingerprint,
				transitionGraph,
				scriptedSources,
				bridge,
				fingerprint));

			return new Result(
				document,
				fingerprint,
				((Number) transitionGraph.get("edgeCount")).intValue(),
				scriptFiles.size(),
				teleportCalls,
				locationCalls);
		} catch (PreflightException failure) {
			throw failure;
		} catch (IOException failure) {
			throw new PreflightException(
				"Could not inventory Preservation transition compatibility "
					+ "sources: " + failure.getMessage(),
				failure);
		} catch (RuntimeException failure) {
			throw new PreflightException(
				"Preservation transition compatibility inventory failed: "
					+ failure.getMessage(),
				failure);
		}
	}

	private static Map<String, Object> frozenInventory(
		final String baselineFingerprint,
		final Map<String, Object> transitionGraph,
		final Map<String, Object> scriptedSources,
		final Map<String, Object> bridge,
		final String inventoryFingerprint) {
		final Map<String, Object> explicit =
			new LinkedHashMap<String, Object>();
		explicit.put("path", transitionGraph.get("sourcePath"));
		explicit.put("sha256", transitionGraph.get("sourceSha256"));
		explicit.put("edgeCount", transitionGraph.get("edgeCount"));
		explicit.put("normalizedEdgeCount",
			transitionGraph.get("normalizedEdgeCount"));
		explicit.put("unresolvedEdgeCount",
			transitionGraph.get("unresolvedEdgeCount"));

		final Map<String, Object> scripted =
			new LinkedHashMap<String, Object>();
		scripted.put("sourceRoot", scriptedSources.get("sourceRoot"));
		scripted.put("sourceFileCount",
			scriptedSources.get("sourceFileCount"));
		scripted.put("teleportCallCount",
			scriptedSources.get("teleportCallCount"));
		scripted.put("locationMutationCallCount",
			scriptedSources.get("locationMutationCallCount"));
		scripted.put("telePointReferenceCount",
			scriptedSources.get("telePointReferenceCount"));
		scripted.put("sourceSetFingerprintSha256",
			scriptedSources.get("sourceSetFingerprintSha256"));

		final Map<String, Object> runtimeBridge =
			new LinkedHashMap<String, Object>();
		runtimeBridge.put("sourceFileCount",
			Long.valueOf(JsonDocuments.array(bridge.get("files")).size()));
		runtimeBridge.put("sourceSetFingerprintSha256",
			bridge.get("sourceSetFingerprintSha256"));

		final Map<String, Object> result =
			new LinkedHashMap<String, Object>();
		result.put("schemaVersion", Long.valueOf(SCHEMA_VERSION));
		result.put("manifestType",
			"rsc-remastered-preservation-transition-compatibility-lock");
		result.put("parentBaselineId",
			PreservationBaselineInventory.BASELINE_ID);
		result.put("parentBaselineFingerprintSha256", baselineFingerprint);
		result.put("inventoryFingerprintSha256", inventoryFingerprint);
		result.put("explicitTransitionSource", explicit);
		result.put("scriptedSourceSet", scripted);
		result.put("runtimeBridgeSourceSet", runtimeBridge);
		return result;
	}

	private static void requireFrozenInventory(
		final Path root,
		final Map<String, Object> actual) throws IOException, PreflightException {
		final Path frozen = requiredFile(root, FROZEN_INVENTORY);
		final byte[] expected = Files.readAllBytes(frozen);
		final byte[] generated = JsonDocuments.pretty(actual)
			.getBytes(StandardCharsets.UTF_8);
		if (!Arrays.equals(expected, generated)) {
			throw new PreflightException(
				"Preservation transition compatibility sources no longer "
					+ "reproduce the accepted frozen inventory; regenerate and "
					+ "review the inventory before runtime promotion.");
		}
	}

	private static List<Path> authenticJavaSources(final Path root)
		throws IOException, PreflightException {
		final Path sourceRoot = requiredDirectory(root, AUTHENTIC_ROOT);
		final List<Path> result = new ArrayList<Path>();
		try (Stream<Path> paths = Files.walk(sourceRoot)) {
			paths.filter(path -> Files.isRegularFile(
					path, LinkOption.NOFOLLOW_LINKS))
				.filter(path -> !Files.isSymbolicLink(path))
				.filter(path -> path.getFileName().toString().endsWith(".java"))
				.forEach(result::add);
		}
		Collections.sort(result);
		return result;
	}

	private static Map<String, Object> fileRecord(
		final Path root,
		final Path path,
		final String family) throws IOException {
		final Map<String, Object> result =
			new LinkedHashMap<String, Object>();
		result.put("path", root.relativize(path).toString().replace('\\', '/'));
		result.put("family", family);
		result.put("size", Long.valueOf(Files.size(path)));
		result.put("sha256", Hashes.sha256(path));
		return result;
	}

	private static String scriptFamily(final String requestedPath) {
		final String path = requestedPath.replace('\\', '/');
		final String prefix = AUTHENTIC_ROOT + "/";
		final String relative = path.startsWith(prefix)
			? path.substring(prefix.length()) : path;
		final int slash = relative.indexOf('/');
		return slash < 0 ? "root" : relative.substring(0, slash);
	}

	private static int count(final Pattern pattern, final String source) {
		int result = 0;
		final Matcher matcher = pattern.matcher(source);
		while (matcher.find()) {
			result = Math.addExact(result, 1);
		}
		return result;
	}

	private static String readUtf8(final Path path) throws IOException {
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	private static Path canonicalRoot(final Path requested)
		throws IOException, PreflightException {
		if (requested == null) {
			throw new PreflightException("A repository root is required.");
		}
		final Path root = requested.toAbsolutePath().normalize();
		if (!Files.isDirectory(root)) {
			throw new PreflightException(
				"Repository root is not a directory: " + root);
		}
		return root.toRealPath();
	}

	private static Path requiredDirectory(
		final Path root,
		final String relative) throws IOException, PreflightException {
		final Path candidate = root.resolve(relative).normalize();
		if (!candidate.startsWith(root)
			|| !Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(candidate)) {
			throw new PreflightException(
				"Transition source directory is missing or unsafe: " + relative);
		}
		final Path real = candidate.toRealPath();
		if (!real.startsWith(root)) {
			throw new PreflightException(
				"Transition source directory escapes the repository: "
					+ relative);
		}
		return real;
	}

	private static Path requiredFile(
		final Path root,
		final String relative) throws IOException, PreflightException {
		final Path candidate = root.resolve(relative).normalize();
		if (!candidate.startsWith(root)
			|| !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(candidate)) {
			throw new PreflightException(
				"Transition source file is missing or unsafe: " + relative);
		}
		final Path real = candidate.toRealPath();
		if (!real.startsWith(root)) {
			throw new PreflightException(
				"Transition source file escapes the repository: " + relative);
		}
		return real;
	}

	static final class Result {
		final Map<String, Object> document;
		final String fingerprint;
		final int explicitEdgeCount;
		final int scriptedSourceFileCount;
		final int teleportCallCount;
		final int locationMutationCallCount;

		Result(
			final Map<String, Object> document,
			final String fingerprint,
			final int explicitEdgeCount,
			final int scriptedSourceFileCount,
			final int teleportCallCount,
			final int locationMutationCallCount) {
			this.document = Collections.unmodifiableMap(
				new LinkedHashMap<String, Object>(document));
			this.fingerprint = fingerprint;
			this.explicitEdgeCount = explicitEdgeCount;
			this.scriptedSourceFileCount = scriptedSourceFileCount;
			this.teleportCallCount = teleportCallCount;
			this.locationMutationCallCount = locationMutationCallCount;
		}

		String toJson() {
			return JsonDocuments.pretty(document);
		}

		String toMarkdown() {
			final StringBuilder out = new StringBuilder();
			out.append("# Preservation Transition Compatibility Inventory\n\n");
			out.append("- Parent baseline: `")
				.append(PreservationBaselineInventory.BASELINE_ID)
				.append("`\n");
			out.append("- Inventory SHA-256: `").append(fingerprint)
				.append("`\n");
			out.append("- Explicit ObjectTelePoints edges: ")
				.append(explicitEdgeCount).append("\n");
			out.append("- Script owners retained for compatibility: ")
				.append(scriptedSourceFileCount).append("\n");
			out.append("- Lexical teleport/location-mutation call sites: ")
				.append(teleportCallCount).append('/')
				.append(locationMutationCallCount).append("\n\n");
			out.append("The explicit XML edges are losslessly normalized. "
				+ "Java transition owners are fingerprinted, not interpreted: "
				+ "their quest gates, random offsets, scripted transports, and "
				+ "unconventional topology remain runtime compatibility behavior "
				+ "until a later semantic migration.\n\n");
			out.append("This inventory is supplementary execution provenance. "
				+ "It does not alter the accepted 12-file vanilla map baseline, "
				+ "declare a complete transition graph, or authorize export.\n");
			return out.toString();
		}
	}
}
