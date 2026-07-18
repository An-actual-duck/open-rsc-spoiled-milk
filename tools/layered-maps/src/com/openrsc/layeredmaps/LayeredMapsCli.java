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

/** Slice 1 command line: deterministic, read-only target preflight. */
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
		if (!"preflight".equals(args[0]) && !"normalize".equals(args[0])) {
			System.err.println("[layered-maps] Unknown command: " + args[0]);
			usage();
			return 2;
		}

		try {
			Map<String, String> options = options(args);
			Path root = requiredPath(options, "--root");
			Path workspace = requiredPath(options, "--workspace");
			validateWorkspace(root, workspace);

			if ("preflight".equals(args[0])) {
				runPreflight(root, workspace);
			} else {
				runNormalization(root, workspace);
			}
			return 0;
		} catch (IllegalArgumentException failure) {
			System.err.println("[layered-maps] " + failure.getMessage());
			return 2;
		} catch (PreflightException failure) {
			System.err.println("[layered-maps] Preflight refused: " + failure.getMessage());
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
		writeAtomically(jsonPath, result.toJson());
		writeAtomically(summaryPath, result.toSummaryJson());
		writeAtomically(markdownPath, result.toMarkdown());

		System.out.println("Layered Maps normalization complete");
		System.out.println("sourceFingerprint=" + result.sourceFingerprint);
		System.out.println("inventoryFingerprint=" + result.inventoryFingerprint);
		System.out.println("terrainSectors=" + result.terrainSectorCount);
		System.out.println("placementRecords=" + result.placementRecordCount);
		System.out.println("transitionEdges=" + result.transitionCount);
		System.out.println("unresolvedCoordinates=" + result.unresolvedCoordinateCount);
		System.out.println("json=" + jsonPath.toAbsolutePath().normalize());
		System.out.println("summaryJson=" + summaryPath.toAbsolutePath().normalize());
		System.out.println("markdown=" + markdownPath.toAbsolutePath().normalize());
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
			if (!"--root".equals(name) && !"--workspace".equals(name)) {
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
		System.err.println("Usage: layered-maps <preflight|normalize> --root PATH --workspace PATH");
	}
}
