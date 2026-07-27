package com.openrsc.worldbuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Initial read-only command-line boundary for World Builder project discovery. */
public final class WorldBuilderCli {
	private WorldBuilderCli() {
	}

	public static void main(String[] args) {
		int result = run(args);
		if (result != 0) {
			System.exit(result);
		}
	}

	static int run(String[] args) {
		if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
			usage();
			return args.length == 0 ? 2 : 0;
		}
		if ("prepare".equals(args[0])) {
			return prepare(args);
		}
		if ("launch".equals(args[0])) {
			int prepared = prepare(args);
			return prepared == 0 ? runPrepared(args, true) : prepared;
		}
		if ("run".equals(args[0])) {
			return runPrepared(args, false);
		}
		if ("create-level".equals(args[0])) {
			return createLevel(args);
		}
		if ("export".equals(args[0])) {
			return export(args);
		}
		if ("import".equals(args[0])) {
			return importChanges(args);
		}
		if ("undo-import".equals(args[0])) {
			return undoImport(args);
		}
		if ("export-import".equals(args[0])) {
			return exportImport(args);
		}
		if ("undo-latest-import".equals(args[0])) {
			return undoLatestImport(args);
		}
		if (!"discover".equals(args[0])) {
			System.err.println("ERROR: Unsupported World Builder command: " + args[0]);
			usage();
			return 2;
		}

		Path root = null;
		String config = WorldBuilderDiscovery.DEFAULT_CONFIG;
		String expectedContent = null;
		for (int index = 1; index < args.length; index++) {
			String argument = args[index];
			if ("--server-root".equals(argument) && index + 1 < args.length) {
				root = Paths.get(args[++index]);
			} else if ("--config".equals(argument) && index + 1 < args.length) {
				config = args[++index];
			} else if ("--expected-content-sha256".equals(argument) && index + 1 < args.length) {
				expectedContent = args[++index];
			} else {
				System.err.println("ERROR: Unknown or incomplete argument: " + argument);
				usage();
				return 2;
			}
		}
		if (root == null) {
			System.err.println("ERROR: --server-root is required.");
			usage();
			return 2;
		}

		try {
			WorldBuilderDiscoveryResult discovered =
				new WorldBuilderDiscovery().discover(root, config, expectedContent);
			System.out.print(discovered.toJson());
			return 0;
		} catch (WorldBuilderDiscoveryException refusal) {
			System.err.println("ERROR: " + refusal.getMessage());
			return 3;
		}
	}

	private static int createLevel(String[] args) {
		Path workspace = null;
		Integer level = null;
		Integer anchorX = null;
		Integer anchorY = null;
		String name = null;
		String role = null;
		for (int index = 1; index < args.length; index++) {
			String argument = args[index];
			if ("--workspace".equals(argument) && index + 1 < args.length) {
				workspace = Paths.get(args[++index]);
			} else if ("--level".equals(argument) && index + 1 < args.length) {
				level = parseIntOption("--level", args[++index]);
				if (level == null) return 2;
			} else if ("--anchor-x".equals(argument) && index + 1 < args.length) {
				anchorX = parseIntOption("--anchor-x", args[++index]);
				if (anchorX == null) return 2;
			} else if ("--anchor-y".equals(argument) && index + 1 < args.length) {
				anchorY = parseIntOption("--anchor-y", args[++index]);
				if (anchorY == null) return 2;
			} else if ("--name".equals(argument) && index + 1 < args.length) {
				name = args[++index];
			} else if ("--role".equals(argument) && index + 1 < args.length) {
				role = args[++index];
			} else {
				System.err.println(
					"ERROR: Unknown or incomplete argument: " + argument);
				usage();
				return 2;
			}
		}
		if (workspace == null || level == null
			|| anchorX == null || anchorY == null) {
			System.err.println(
				"ERROR: create-level requires --workspace, --level, "
					+ "--anchor-x, and --anchor-y.");
			usage();
			return 2;
		}
		if (name == null) {
			name = WorldBuilderLayeredDraftWriter.defaultName(level.intValue());
		}
		if (role == null) {
			role = WorldBuilderLayeredDraftWriter.defaultRole(level.intValue());
		}
		try {
			WorldBuilderLayeredDraftWriter.CreateLevelResult result =
				new WorldBuilderLayeredDraftWriter().createLevel(
					workspace,
					level.intValue(),
					anchorX.intValue(),
					anchorY.intValue(),
					name,
					role);
			System.out.print(result.toJson());
			return 0;
		} catch (WorldBuilderDiscoveryException refusal) {
			System.err.println("ERROR: " + refusal.getMessage());
			return 3;
		} catch (Exception failure) {
			System.err.println(
				"ERROR: Could not create layered Builder level: "
					+ failure.getMessage());
			return 4;
		}
	}

	private static Integer parseIntOption(String option, String value) {
		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException failure) {
			System.err.println(
				"ERROR: " + option + " must be a signed 32-bit integer.");
			return null;
		}
	}

	private static int runPrepared(String[] args, boolean launchArguments) {
		Path workspace = null;
		int port = 0;
		for (int index = 1; index < args.length; index++) {
			String argument = args[index];
			if ("--workspace".equals(argument) && index + 1 < args.length) {
				workspace = Paths.get(args[++index]);
			} else if ("--port".equals(argument) && index + 1 < args.length) {
				try {
					port = Integer.parseInt(args[++index]);
				} catch (NumberFormatException failure) {
					System.err.println("ERROR: --port must be numeric.");
					return 2;
				}
			} else if (launchArguments && isPreparationOption(argument) && index + 1 < args.length) {
				index++;
			} else {
				System.err.println("ERROR: Unknown or incomplete argument: " + argument);
				usage();
				return 2;
			}
		}
		if (workspace == null || (launchArguments && port == 0)) {
			System.err.println("ERROR: run requires --workspace; launch also requires --port.");
			usage();
			return 2;
		}
		try {
			if (port == 0) {
				port = WorldBuilderProcessSupervisor.readPreparedPort(workspace);
			}
			System.out.println("Starting isolated World Builder. Logs: "
				+ workspace.toAbsolutePath().normalize().resolve("logs"));
			int result = new WorldBuilderProcessSupervisor().runPrepared(workspace, port);
			if (result == 0) {
				System.out.println("World Builder closed cleanly.");
			} else {
				System.err.println("ERROR: World Builder stopped with exit code " + result + ".");
			}
			return result;
		} catch (WorldBuilderDiscoveryException refusal) {
			System.err.println("ERROR: " + refusal.getMessage());
			return 3;
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			System.err.println("ERROR: World Builder launcher was interrupted.");
			return 130;
		} catch (Exception failure) {
			System.err.println("ERROR: World Builder launch failed: " + failure.getMessage());
			return 4;
		}
	}

	private static int exportImport(String[] args) {
		Path workspace = null;
		Path target = null;
		String builderVersion = null;
		String sourceCommit = null;
		for (int index = 1; index < args.length; index++) {
			String argument = args[index];
			if ("--workspace".equals(argument) && index + 1 < args.length) {
				workspace = Paths.get(args[++index]);
			} else if ("--target-root".equals(argument) && index + 1 < args.length) {
				target = Paths.get(args[++index]);
			} else if ("--builder-version".equals(argument) && index + 1 < args.length) {
				builderVersion = args[++index];
			} else if ("--source-commit".equals(argument) && index + 1 < args.length) {
				sourceCommit = args[++index];
			} else {
				System.err.println("ERROR: Unknown or incomplete argument: " + argument);
				usage();
				return 2;
			}
		}
		if (workspace == null || target == null || builderVersion == null || sourceCommit == null) {
			System.err.println("ERROR: export-import requires --workspace, --target-root, "
				+ "--builder-version, and --source-commit.");
			usage();
			return 2;
		}
		try {
			WorldBuilderExporter.ExportResult exported = new WorldBuilderExporter().export(
				workspace, builderVersion, sourceCommit);
			System.out.print(exported.toJson());
			if (exported.exportDirectory == null) {
				System.out.println("No saved map changes are available to import.");
				return 0;
			}
			WorldBuilderImporter importer = new WorldBuilderImporter();
			System.out.println("Import preview:");
			System.out.print(importer.preview(workspace, exported.exportDirectory, target).toJson());
			if (!confirm("IMPORT", "Type IMPORT to install these map changes, or press Enter to cancel: ")) {
				System.out.println("Import cancelled; the target private server was not changed.");
				return 0;
			}
			System.out.print(importer.apply(workspace, exported.exportDirectory, target).toJson());
			return 0;
		} catch (WorldBuilderDiscoveryException refusal) {
			System.err.println("ERROR: " + refusal.getMessage());
			return 3;
		} catch (Exception failure) {
			System.err.println("ERROR: Could not export and import Builder changes: "
				+ failure.getMessage());
			return 4;
		}
	}

	private static int undoLatestImport(String[] args) {
		Path workspace = null;
		Path target = null;
		for (int index = 1; index < args.length; index++) {
			String argument = args[index];
			if ("--workspace".equals(argument) && index + 1 < args.length) {
				workspace = Paths.get(args[++index]);
			} else if ("--target-root".equals(argument) && index + 1 < args.length) {
				target = Paths.get(args[++index]);
			} else {
				System.err.println("ERROR: Unknown or incomplete argument: " + argument);
				usage();
				return 2;
			}
		}
		if (workspace == null || target == null) {
			System.err.println(
				"ERROR: undo-latest-import requires --workspace and --target-root.");
			usage();
			return 2;
		}
		try {
			WorldBuilderImporter importer = new WorldBuilderImporter();
			System.out.println("Undo preview:");
			System.out.print(importer.previewRollback(workspace, target).toJson());
			if (!confirm("UNDO", "Type UNDO to restore the previous map files, or press Enter to cancel: ")) {
				System.out.println("Undo cancelled; the target private server was not changed.");
				return 0;
			}
			System.out.print(importer.rollback(workspace, target).toJson());
			return 0;
		} catch (WorldBuilderDiscoveryException refusal) {
			System.err.println("ERROR: " + refusal.getMessage());
			return 3;
		} catch (Exception failure) {
			System.err.println("ERROR: Could not undo the latest Builder import: "
				+ failure.getMessage());
			return 4;
		}
	}

	private static boolean confirm(String expected, String prompt) throws Exception {
		System.out.print(prompt);
		System.out.flush();
		String response = new BufferedReader(new InputStreamReader(System.in, "UTF-8")).readLine();
		return expected.equals(response == null ? "" : response.trim());
	}

	private static int export(String[] args) {
		Path workspace = null; String builderVersion = null; String sourceCommit = null;
		for (int index = 1; index < args.length; index++) {
			String argument = args[index];
			if ("--workspace".equals(argument) && index + 1 < args.length) workspace = Paths.get(args[++index]);
			else if ("--builder-version".equals(argument) && index + 1 < args.length) builderVersion = args[++index];
			else if ("--source-commit".equals(argument) && index + 1 < args.length) sourceCommit = args[++index];
			else { System.err.println("ERROR: Unknown or incomplete argument: " + argument); usage(); return 2; }
		}
		if (workspace == null || builderVersion == null || sourceCommit == null) {
			System.err.println("ERROR: export requires --workspace, --builder-version, and --source-commit.");
			usage(); return 2;
		}
		try {
			System.out.print(new WorldBuilderExporter().export(workspace, builderVersion, sourceCommit).toJson());
			return 0;
		} catch (WorldBuilderDiscoveryException refusal) {
			System.err.println("ERROR: " + refusal.getMessage()); return 3;
		} catch (Exception failure) {
			System.err.println("ERROR: Could not export Builder project: " + failure.getMessage()); return 4;
		}
	}

	private static int importChanges(String[] args) {
		Path workspace = null;
		Path export = null;
		Path target = null;
		boolean dryRun = false;
		boolean apply = false;
		for (int index = 1; index < args.length; index++) {
			String argument = args[index];
			if ("--workspace".equals(argument) && index + 1 < args.length) {
				workspace = Paths.get(args[++index]);
			} else if ("--export".equals(argument) && index + 1 < args.length) {
				export = Paths.get(args[++index]);
			} else if ("--target-root".equals(argument) && index + 1 < args.length) {
				target = Paths.get(args[++index]);
			} else if ("--dry-run".equals(argument)) {
				dryRun = true;
			} else if ("--apply".equals(argument)) {
				apply = true;
			} else {
				System.err.println("ERROR: Unknown or incomplete argument: " + argument);
				usage();
				return 2;
			}
		}
		if (workspace == null || export == null || target == null || dryRun == apply) {
			System.err.println("ERROR: import requires --workspace, --export, --target-root, "
				+ "and exactly one of --dry-run or --apply.");
			usage();
			return 2;
		}
		try {
			WorldBuilderImporter importer = new WorldBuilderImporter();
			System.out.print(dryRun
				? importer.preview(workspace, export, target).toJson()
				: importer.apply(workspace, export, target).toJson());
			return 0;
		} catch (WorldBuilderDiscoveryException refusal) {
			System.err.println("ERROR: " + refusal.getMessage());
			return 3;
		} catch (Exception failure) {
			System.err.println("ERROR: Could not process Builder import: " + failure.getMessage());
			return 4;
		}
	}

	private static int undoImport(String[] args) {
		Path workspace = null;
		Path target = null;
		boolean dryRun = false;
		boolean apply = false;
		for (int index = 1; index < args.length; index++) {
			String argument = args[index];
			if ("--workspace".equals(argument) && index + 1 < args.length) {
				workspace = Paths.get(args[++index]);
			} else if ("--target-root".equals(argument) && index + 1 < args.length) {
				target = Paths.get(args[++index]);
			} else if ("--dry-run".equals(argument)) {
				dryRun = true;
			} else if ("--apply".equals(argument)) {
				apply = true;
			} else {
				System.err.println("ERROR: Unknown or incomplete argument: " + argument);
				usage();
				return 2;
			}
		}
		if (workspace == null || target == null || dryRun == apply) {
			System.err.println("ERROR: undo-import requires --workspace, --target-root, "
				+ "and exactly one of --dry-run or --apply.");
			usage();
			return 2;
		}
		try {
			WorldBuilderImporter importer = new WorldBuilderImporter();
			System.out.print(dryRun
				? importer.previewRollback(workspace, target).toJson()
				: importer.rollback(workspace, target).toJson());
			return 0;
		} catch (WorldBuilderDiscoveryException refusal) {
			System.err.println("ERROR: " + refusal.getMessage());
			return 3;
		} catch (Exception failure) {
			System.err.println("ERROR: Could not undo Builder import: " + failure.getMessage());
			return 4;
		}
	}

	private static boolean isPreparationOption(String argument) {
		return "--server-root".equals(argument)
			|| "--runtime-root".equals(argument)
			|| "--config".equals(argument)
			|| "--runtime-config".equals(argument)
			|| "--layered-package".equals(argument)
			|| "--layered-profile".equals(argument);
	}

	private static int prepare(String[] args) {
		Path targetRoot = null;
		Path runtimeRoot = null;
		Path workspace = null;
		String config = WorldBuilderDiscovery.DEFAULT_CONFIG;
		String runtimeConfig = WorldBuilderDiscovery.DEFAULT_CONFIG;
		Path layeredPackagePath = null;
		String layeredProfile = null;
		int port = 0;
		for (int index = 1; index < args.length; index++) {
			String argument = args[index];
			if ("--server-root".equals(argument) && index + 1 < args.length) {
				targetRoot = Paths.get(args[++index]);
			} else if ("--runtime-root".equals(argument) && index + 1 < args.length) {
				runtimeRoot = Paths.get(args[++index]);
			} else if ("--workspace".equals(argument) && index + 1 < args.length) {
				workspace = Paths.get(args[++index]);
			} else if ("--config".equals(argument) && index + 1 < args.length) {
				config = args[++index];
			} else if ("--runtime-config".equals(argument) && index + 1 < args.length) {
				runtimeConfig = args[++index];
			} else if ("--layered-package".equals(argument) && index + 1 < args.length) {
				layeredPackagePath = Paths.get(args[++index]);
			} else if ("--layered-profile".equals(argument) && index + 1 < args.length) {
				layeredProfile = args[++index];
			} else if ("--port".equals(argument) && index + 1 < args.length) {
				try {
					port = Integer.parseInt(args[++index]);
				} catch (NumberFormatException failure) {
					System.err.println("ERROR: --port must be numeric.");
					return 2;
				}
			} else {
				System.err.println("ERROR: Unknown or incomplete argument: " + argument);
				usage();
				return 2;
			}
		}
		if (targetRoot == null || runtimeRoot == null || workspace == null || port == 0) {
			System.err.println("ERROR: prepare requires --server-root, --runtime-root, --workspace, and --port.");
			usage();
			return 2;
		}

		try {
			WorldBuilderDiscovery discovery = new WorldBuilderDiscovery();
			WorldBuilderDiscoveryResult runtime = discovery.discover(runtimeRoot, runtimeConfig, null);
			WorldBuilderDiscoveryResult source = discovery.discover(
				targetRoot, config, runtime.contentFingerprintSha256);
			if ((layeredPackagePath == null) != (layeredProfile == null)) {
				throw new WorldBuilderDiscoveryException(
					"--layered-package and --layered-profile must be supplied together.");
			}
			WorldBuilderLayeredPackage layered = layeredPackagePath == null
				? null
				: WorldBuilderLayeredPackage.discover(
					layeredPackagePath, layeredProfile);
			WorldBuilderRuntimePreparer.PreparedRuntime prepared =
				new WorldBuilderRuntimePreparer().prepare(
					targetRoot, runtimeRoot, workspace, port, source, runtime, layered);
			System.out.print(prepared.toJson());
			return 0;
		} catch (WorldBuilderDiscoveryException refusal) {
			System.err.println("ERROR: " + refusal.getMessage());
			return 3;
		} catch (Exception failure) {
			System.err.println("ERROR: Could not prepare isolated Builder runtime: " + failure.getMessage());
			return 4;
		}
	}

	private static void usage() {
		System.err.println("Usage:\n  WorldBuilderCli discover --server-root <path>"
			+ " [--config server/myworld.conf]"
			+ " [--expected-content-sha256 <sha256>]"
			+ "\n  WorldBuilderCli prepare --server-root <path> --runtime-root <path>"
			+ " --workspace <path> --port <port>"
			+ " [--config server/myworld.conf] [--runtime-config server/myworld.conf]"
			+ " [--layered-package <package> --layered-profile spoiled-milk-replacement]"
			+ "\n  WorldBuilderCli launch <same arguments as prepare>"
			+ "\n  WorldBuilderCli run --workspace <prepared-path> [--port <port>]");
		System.err.println("  WorldBuilderCli export --workspace <prepared-path>"
			+ " --builder-version <version> --source-commit <40-hex>");
		System.err.println("  WorldBuilderCli import --workspace <prepared-path>"
			+ " --export <export-directory> --target-root <private-server-root>"
			+ " (--dry-run | --apply)");
		System.err.println("  WorldBuilderCli undo-import --workspace <prepared-path>"
			+ " --target-root <private-server-root> (--dry-run | --apply)");
		System.err.println("  WorldBuilderCli export-import --workspace <prepared-path>"
			+ " --target-root <private-server-root> --builder-version <version>"
			+ " --source-commit <40-hex>");
		System.err.println("  WorldBuilderCli undo-latest-import --workspace <prepared-path>"
			+ " --target-root <private-server-root>");
		System.err.println("  WorldBuilderCli create-level --workspace <prepared-path>"
			+ " --level <signed-level> --anchor-x <x> --anchor-y <y>"
			+ " [--name <name>] [--role <role>]");
	}
}
