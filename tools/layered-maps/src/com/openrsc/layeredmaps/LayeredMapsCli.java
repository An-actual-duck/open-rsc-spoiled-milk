package com.openrsc.layeredmaps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/** Deterministic, isolated reporting commands for the staged Layered Maps tool. */
public final class LayeredMapsCli {
	private LayeredMapsCli() {
	}

	public static void main(String[] args) {
		System.exit(run(args));
	}

	static int run(String[] args) {
		if (args.length == 0 || "--help".equals(args[0]) || "help".equals(args[0])) {
			usage();
			return args.length == 0 ? 2 : 0;
		}
		if (!"preflight".equals(args[0])
			&& !"normalize".equals(args[0])
			&& !"baseline".equals(args[0])
			&& !"preservation-transitions".equals(args[0])
			&& !"preservation-package".equals(args[0])
			&& !"spoiled-milk-package".equals(args[0])
			&& !"package-check".equals(args[0])) {
			System.err.println("[layered-maps] Unknown command: " + args[0]);
			usage();
			return 2;
		}

		try {
			Map<String, String> options = options(args);
			Path root = requiredPath(options, "--root");
			Path workspace = requiredPath(options, "--workspace");
			if (!"package-check".equals(args[0]) && options.containsKey("--package")) {
				throw new IllegalArgumentException(
					"--package is valid only with package-check.");
			}
			validateWorkspace(root, workspace);

			if ("preflight".equals(args[0])) {
				runPreflight(root, workspace);
			} else if ("normalize".equals(args[0])) {
				runNormalization(root, workspace);
			} else if ("baseline".equals(args[0])) {
				runBaseline(root, workspace);
			} else if ("preservation-transitions".equals(args[0])) {
				runPreservationTransitions(root, workspace);
			} else if ("preservation-package".equals(args[0])) {
				runLayeredPackage(
					root,
					workspace,
					PreservationTerrainPackageGenerator.ContentTarget.PRESERVATION);
			} else if ("spoiled-milk-package".equals(args[0])) {
				runLayeredPackage(
					root,
					workspace,
					PreservationTerrainPackageGenerator.ContentTarget.SPOILED_MILK);
			} else {
				runPackageCheck(requiredPath(options, "--package"), workspace);
			}
			return 0;
		} catch (IllegalArgumentException failure) {
			System.err.println("[layered-maps] " + failure.getMessage());
			return 2;
		} catch (PreflightException failure) {
			System.err.println("[layered-maps] Operation refused: " + failure.getMessage());
			return 3;
		} catch (IOException failure) {
			System.err.println("[layered-maps] Could not write isolated reports: " + failure.getMessage());
			return 4;
		}
	}

	private static void runPreflight(Path root, Path workspace)
		throws PreflightException, IOException {
		PreflightReport report = new RepositoryPreflight().inspect(root);
		Files.createDirectories(workspace);
		Path jsonPath = workspace.resolve("preflight.json");
		Path markdownPath = workspace.resolve("preflight.md");
		writeAtomically(jsonPath, report.toJson());
		writeAtomically(markdownPath, report.toMarkdown());

		System.out.println("Layered Maps preflight complete");
		System.out.println("adapter=" + report.layoutAdapter);
		System.out.println("sourceFingerprint=" + report.sourceFingerprint);
		System.out.println("candidateSources=" + report.candidateSources.size());
		System.out.println("json=" + jsonPath.toAbsolutePath().normalize());
		System.out.println("markdown=" + markdownPath.toAbsolutePath().normalize());
	}

	private static void runNormalization(Path root, Path workspace)
		throws PreflightException, IOException {
		NormalizationResult result = new WorldNormalizer().normalize(root);
		Files.createDirectories(workspace);
		Path jsonPath = workspace.resolve("world-inventory.json");
		Path summaryPath = workspace.resolve("normalization-summary.json");
		Path markdownPath = workspace.resolve("normalization.md");
		Path classificationJsonPath = workspace.resolve("coordinate-owner-classification.json");
		Path classificationMarkdownPath = workspace.resolve("coordinate-owner-classification.md");
		Path occurrencesJsonPath = workspace.resolve("java-coordinate-occurrences.json");
		Path occurrencesMarkdownPath = workspace.resolve("java-coordinate-occurrences.md");
		writeAtomically(jsonPath, result.toJson());
		writeAtomically(summaryPath, result.toSummaryJson());
		writeAtomically(markdownPath, result.toMarkdown());
		writeAtomically(classificationJsonPath, result.ownerClassification.toJson());
		writeAtomically(classificationMarkdownPath, result.ownerClassification.toMarkdown());
		writeAtomically(occurrencesJsonPath, result.coordinateOccurrences.toJson());
		writeAtomically(occurrencesMarkdownPath, result.coordinateOccurrences.toMarkdown());

		System.out.println("Layered Maps normalization complete");
		System.out.println("sourceFingerprint=" + result.sourceFingerprint);
		System.out.println("inventoryFingerprint=" + result.inventoryFingerprint);
		System.out.println("terrainSectors=" + result.terrainSectorCount);
		System.out.println("placementRecords=" + result.placementRecordCount);
		System.out.println("transitionEdges=" + result.transitionCount);
		System.out.println("unresolvedCoordinates=" + result.unresolvedCoordinateCount);
		System.out.println("classifiedSourceOwners=" + result.ownerClassification.sourceOwnerCount);
		System.out.println("classificationFingerprint="
			+ result.ownerClassification.classificationFingerprint);
		System.out.println("coordinateOccurrences=" + result.coordinateOccurrences.occurrenceCount);
		System.out.println("occurrenceFingerprint="
			+ result.coordinateOccurrences.occurrenceFingerprint);
		System.out.println("json=" + jsonPath.toAbsolutePath().normalize());
		System.out.println("summaryJson=" + summaryPath.toAbsolutePath().normalize());
		System.out.println("markdown=" + markdownPath.toAbsolutePath().normalize());
		System.out.println("classificationJson=" + classificationJsonPath.toAbsolutePath().normalize());
		System.out.println("classificationMarkdown="
			+ classificationMarkdownPath.toAbsolutePath().normalize());
		System.out.println("occurrencesJson=" + occurrencesJsonPath.toAbsolutePath().normalize());
		System.out.println("occurrencesMarkdown="
			+ occurrencesMarkdownPath.toAbsolutePath().normalize());
	}

	private static void runBaseline(Path root, Path workspace)
		throws PreflightException, IOException {
		PreservationBaselineInventory.Baseline baseline =
			new PreservationBaselineInventory().inspect(root);
		Files.createDirectories(workspace);
		Path jsonPath = workspace.resolve("preservation-baseline.json");
		Path markdownPath = workspace.resolve("preservation-baseline.md");
		writeAtomically(jsonPath, baseline.toJson());
		writeAtomically(markdownPath, baseline.toMarkdown());

		System.out.println("RSC Remastered Preservation baseline complete");
		System.out.println("baselineId=" + PreservationBaselineInventory.BASELINE_ID);
		System.out.println("sourceSetFingerprint=" + baseline.sourceSetFingerprint);
		System.out.println("sourceFiles=" + baseline.files.size());
		System.out.println("json=" + jsonPath.toAbsolutePath().normalize());
		System.out.println("markdown=" + markdownPath.toAbsolutePath().normalize());
	}

	private static void runPackageCheck(Path packageRoot, Path workspace)
		throws PreflightException, IOException {
		LayeredWorldPackageManifest manifest =
			LayeredWorldPackageManifest.load(packageRoot);
		Files.createDirectories(workspace);
		Path jsonPath = workspace.resolve("package-validation.json");
		writeAtomically(jsonPath, manifest.toValidationJson());

		System.out.println("Layered world package validation complete");
		System.out.println("packageId=" + manifest.getPackageId());
		System.out.println("packageVersion=" + manifest.getPackageVersion());
		System.out.println("packageFingerprint=" + manifest.getPackageFingerprint());
		System.out.println("worldSpaces=" + manifest.getWorldSpaces().size());
		System.out.println("levels=" + manifest.getLevels().size());
		System.out.println("terrainSectors=" + manifest.getTerrainSectors().size());
		System.out.println("placementSets=" + manifest.getPlacementSets().size());
		System.out.println("json=" + jsonPath.toAbsolutePath().normalize());
	}

	private static void runPreservationTransitions(Path root, Path workspace)
		throws PreflightException, IOException {
		PreservationTransitionCompatibilityInventory.Result result =
			new PreservationTransitionCompatibilityInventory().inspect(root);
		Files.createDirectories(workspace);
		Path jsonPath = workspace.resolve("transition-compatibility.json");
		Path markdownPath = workspace.resolve("transition-compatibility.md");
		writeAtomically(jsonPath, result.toJson());
		writeAtomically(markdownPath, result.toMarkdown());

		System.out.println(
			"Preservation transition compatibility inventory complete");
		System.out.println("inventoryFingerprint=" + result.fingerprint);
		System.out.println("explicitEdges=" + result.explicitEdgeCount);
		System.out.println(
			"scriptedSourceFiles=" + result.scriptedSourceFileCount);
		System.out.println("teleportCalls=" + result.teleportCallCount);
		System.out.println(
			"locationMutationCalls=" + result.locationMutationCallCount);
		System.out.println("json=" + jsonPath.toAbsolutePath().normalize());
		System.out.println(
			"markdown=" + markdownPath.toAbsolutePath().normalize());
	}

	private static void runLayeredPackage(
		Path root,
		Path workspace,
		PreservationTerrainPackageGenerator.ContentTarget target)
		throws PreflightException, IOException {
		PreservationTerrainPackageGenerator.Result result =
			new PreservationTerrainPackageGenerator(target).generate(
				root, workspace);
		Path reportJson = workspace.resolve("generation-report.json");
		Path reportMarkdown = workspace.resolve("generation-report.md");
		Path validationJson = workspace.resolve("package-validation.json");
		writeAtomically(reportJson, result.toJson());
		writeAtomically(reportMarkdown, result.toMarkdown());
		writeAtomically(validationJson, result.validationJson);

		System.out.println(
			target
				== PreservationTerrainPackageGenerator.ContentTarget.SPOILED_MILK
				? "Spoiled Milk layered world package generated"
				: "Preservation layered parity package generated");
		System.out.println(
			"reviewState="
				+ (target
					== PreservationTerrainPackageGenerator.ContentTarget.SPOILED_MILK
						? "production-approved"
						: "transitions-pending"));
		System.out.println(
			"runtimePromotionApproved="
				+ (target
					== PreservationTerrainPackageGenerator.ContentTarget.SPOILED_MILK));
		System.out.println("baselineSha256=" + result.baselineFingerprint);
		System.out.println("terrainSectors=" + result.terrainSectorCount);
		System.out.println("terrainPayloadBytes=" + result.terrainPayloadBytes);
		System.out.println("sourcePlacements=" + result.sourcePlacementCount);
		System.out.println("convertedPlacements="
			+ result.convertedPlacementCount());
		System.out.println("excludedSourcePlacements="
			+ result.excludedSourcePlacementCount);
		System.out.println("unconvertedPlacements="
			+ result.unconvertedPlacementCount);
		System.out.println("package=" + result.packageRoot);
		System.out.println("report=" + reportJson);
		System.out.println("validation=" + validationJson);
	}

	private static Map<String, String> options(String[] args) {
		Map<String, String> result = new HashMap<String, String>();
		for (int index = 1; index < args.length; index += 2) {
			if (index + 1 >= args.length || !args[index].startsWith("--")) {
				throw new IllegalArgumentException(
					"Options must be --name value pairs. Required: --root and --workspace.");
			}
			String previous = result.put(args[index], args[index + 1]);
			if (previous != null) {
				throw new IllegalArgumentException("Option appears more than once: " + args[index]);
			}
		}
		for (String name : result.keySet()) {
			if (!"--root".equals(name)
				&& !"--workspace".equals(name)
				&& !"--package".equals(name)) {
				throw new IllegalArgumentException("Unknown option: " + name);
			}
		}
		return result;
	}

	private static Path requiredPath(Map<String, String> options, String name) {
		String value = options.get(name);
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException("Required option is missing: " + name);
		}
		return Paths.get(value).toAbsolutePath().normalize();
	}

	private static void validateWorkspace(Path requestedRoot, Path workspace) throws IOException {
		Path root = requestedRoot.toAbsolutePath().normalize();
		if (Files.exists(root)) {
			root = root.toRealPath();
		}
		if (workspace.equals(root)) {
			throw new IllegalArgumentException("Workspace must not be the repository root.");
		}
		if (workspace.startsWith(root)) {
			Path allowed = root.resolve("tools/layered-maps/workspace").normalize();
			if (!workspace.startsWith(allowed)) {
				throw new IllegalArgumentException(
					"A workspace inside the repository must remain under tools/layered-maps/workspace.");
			}
		}
	}

	private static void writeAtomically(Path destination, String content) throws IOException {
		Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
		Files.write(temporary, content.getBytes(StandardCharsets.UTF_8));
		try {
			Files.move(temporary, destination,
				StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void usage() {
		System.err.println(
			"Usage: layered-maps <preflight|normalize|baseline"
				+ "|preservation-transitions|preservation-package"
				+ "|spoiled-milk-package|package-check>"
				+ " --root PATH --workspace PATH [--package PATH]");
	}
}
