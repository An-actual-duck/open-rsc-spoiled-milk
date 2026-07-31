package com.openrsc.worldbuilder;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Owns the isolated local server/client lifecycle for one prepared workspace. */
public final class WorldBuilderProcessSupervisor {
	private static final long DEFAULT_READY_TIMEOUT_MILLIS = 60_000L;
	private static final long SHUTDOWN_TIMEOUT_MILLIS = 20_000L;
	private static final Pattern SOURCE_FINGERPRINT = Pattern.compile(
		"\\\"sourceFingerprintSha256\\\"\\s*:\\s*\\\"([0-9a-f]{64})\\\"");
	private static final Pattern RUNTIME_PORT = Pattern.compile(
		"\\\"port\\\"\\s*:\\s*([0-9]+)");

	public int runPrepared(Path requestedWorkspace, int port)
		throws IOException, WorldBuilderDiscoveryException, InterruptedException {
		Path workspace = validateWorkspace(requestedWorkspace, port);
		return superviseWithCommands(
			workspace, port, null, null, DEFAULT_READY_TIMEOUT_MILLIS);
	}

	int superviseWithCommands(Path workspace, int port, List<String> serverCommand,
		List<String> clientCommand, long readyTimeoutMillis)
		throws IOException, WorldBuilderDiscoveryException, InterruptedException {
		Path lockPath = workspace.getParent().resolve("." + workspace.getFileName() + ".world-builder.lock");
		try (FileChannel lockChannel = FileChannel.open(lockPath,
			StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
			FileLock lock;
			try {
				lock = lockChannel.tryLock();
			} catch (OverlappingFileLockException busy) {
				lock = null;
			}
			if (lock == null) {
				throw new WorldBuilderDiscoveryException(
					"This World Builder workspace is already running: " + workspace);
			}
			try {
				boolean preparedCommands = serverCommand == null && clientCommand == null;
				if ((serverCommand == null) != (clientCommand == null)) {
					throw new IllegalArgumentException(
						"World Builder server and client commands must both be supplied.");
				}
				if (preparedCommands) {
					commitPendingLayeredTerrain(workspace);
					validateWorkspace(workspace, port);
					serverCommand = defaultServerCommand(workspace);
					clientCommand = defaultClientCommand(workspace, port);
				}
				int exit = superviseLocked(
					workspace, port, serverCommand, clientCommand, readyTimeoutMillis);
				if (preparedCommands && exit == 0) {
					commitPendingLayeredTerrain(workspace);
					validateWorkspace(workspace, port);
				} else if (preparedCommands) {
					System.err.println(
						"World Builder did not close cleanly; any saved layered "
							+ "terrain journal was retained without committing.");
				}
				return exit;
			} finally {
				lock.release();
			}
		}
	}

	private static void commitPendingLayeredTerrain(Path workspace)
		throws IOException, WorldBuilderDiscoveryException {
		WorldBuilderLayeredTerrainDraftJournal.CommitResult committed =
			new WorldBuilderLayeredTerrainDraftJournal()
				.commitIfPresentLocked(workspace);
		if (committed != null) {
			System.out.println(
				"Committed layered Builder draft: "
					+ committed.levelCount + " levels, "
					+ committed.tileCount + " tiles, "
					+ committed.sectorCount + " sectors, "
					+ committed.sceneryCount + " scenery edits, "
					+ committed.npcCount + " NPC edits, "
					+ committed.groundItemCount
					+ " ground-item edits, manifest "
					+ committed.manifestSha256.substring(0, 12));
		}
	}

	private int superviseLocked(Path workspace, int port, List<String> serverCommand,
		List<String> clientCommand, long readyTimeoutMillis)
		throws IOException, WorldBuilderDiscoveryException, InterruptedException {
		Path run = workspace.resolve("run");
		Path logs = workspace.resolve("logs");
		Path control = workspace.resolve("working/server/run/world-builder");
		Path ready = control.resolve("ready");
		Path shutdown = control.resolve("shutdown.request");
		Path credential = workspace.resolve(WorldBuilderRuntimePreparer.BUILDER_CREDENTIAL);
		Files.createDirectories(run);
		Files.createDirectories(logs);
		Files.createDirectories(control);
		Files.deleteIfExists(ready);
		Files.deleteIfExists(shutdown);

		Path serverLog = logs.resolve("server.log");
		Path clientLog = logs.resolve("client.log");
		rotateLog(serverLog);
		rotateLog(clientLog);

		final Process[] active = new Process[2];
		Thread shutdownHook = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					requestShutdown(shutdown);
				} catch (Exception ignored) {
				}
				for (Process process : active) {
					if (process != null && process.isAlive()) {
						process.destroy();
					}
				}
			}
		}, "World Builder Launcher Shutdown");
		Runtime.getRuntime().addShutdownHook(shutdownHook);

		int clientExit = -1;
		int serverExit = -1;
		boolean serverFailedFirst = false;
		try {
			active[0] = startProcess(serverCommand, workspace.resolve("working/server"), serverLog);
			writePid(run.resolve("server.pid"), active[0]);
			waitForReady(active[0], ready, credential, port, readyTimeoutMillis);

			active[1] = startProcess(clientCommand, workspace.resolve("working/Client_Base"), clientLog);
			writePid(run.resolve("client.pid"), active[1]);
			while (active[1].isAlive() && active[0].isAlive()) {
				Thread.sleep(200L);
			}
			serverFailedFirst = !active[0].isAlive() && active[1].isAlive();
			if (serverFailedFirst) {
				serverExit = active[0].exitValue();
				active[1].destroy();
				if (!active[1].waitFor(5L, TimeUnit.SECONDS)) {
					destroyForcibly(active[1]);
				}
			}
			clientExit = active[1].waitFor();
			requestShutdown(shutdown);
			if (active[0].isAlive() && !active[0].waitFor(SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
				active[0].destroy();
				if (!active[0].waitFor(5L, TimeUnit.SECONDS)) {
					destroyForcibly(active[0]);
				}
			}
			serverExit = active[0].waitFor();
			return serverFailedFirst || serverExit != 0 ? 5 : clientExit;
		} finally {
			if (active[1] != null && active[1].isAlive()) {
				active[1].destroy();
			}
			if (active[0] != null && active[0].isAlive()) {
				try {
					requestShutdown(shutdown);
					if (!active[0].waitFor(5L, TimeUnit.SECONDS)) {
						active[0].destroy();
					}
				} catch (Exception ignored) {
					active[0].destroy();
				}
			}
			Files.deleteIfExists(run.resolve("server.pid"));
			Files.deleteIfExists(run.resolve("client.pid"));
			Files.deleteIfExists(ready);
			writeLastRun(run.resolve("last-run.json"), serverExit, clientExit, serverFailedFirst);
			try {
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
			} catch (IllegalStateException ignored) {
			}
		}
	}

	private static Path validateWorkspace(Path requestedWorkspace, int port)
		throws IOException, WorldBuilderDiscoveryException {
		if (port < 1 || port >= 65535) {
			throw new WorldBuilderDiscoveryException("Builder port must be between 1 and 65534.");
		}
		Path workspace = requestedWorkspace.toAbsolutePath().normalize();
		if (!Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(workspace)) {
			throw new WorldBuilderDiscoveryException("Prepared Builder workspace is missing: " + workspace);
		}
		workspace = workspace.toRealPath();
		for (String relative : Arrays.asList(
			"working/server/core.jar",
			"working/server/plugins.jar",
			"working/server/world-builder.conf",
			"working/server/inc/sqlite/world_builder.db",
			"working/Client_Base/Open_RSC_Client.jar",
			"project-source.json",
			"source-snapshot.sha256",
			"runtime.json")) {
			Path file = workspace.resolve(relative).normalize();
			if (!file.startsWith(workspace)
				|| !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
				|| Files.isSymbolicLink(file)) {
				throw new WorldBuilderDiscoveryException("Prepared Builder runtime is incomplete: " + relative);
			}
		}
		WorldBuilderSourceSnapshot.verify(workspace);
		WorldBuilderLayeredReview.readIfPresent(workspace);
		int preparedPort = readPreparedPort(workspace);
		if (port != preparedPort) {
			throw new WorldBuilderDiscoveryException(
				"Requested Builder port " + port + " does not match prepared project port "
					+ preparedPort + ".");
		}
		return workspace;
	}

	static List<String> defaultServerCommand(Path workspace) {
		String java = javaExecutable();
		String credential = workspace.resolve(WorldBuilderRuntimePreparer.BUILDER_CREDENTIAL).toString();
		String control = workspace.resolve("working/server/run/world-builder").toString();
		String classpath = String.join(System.getProperty("path.separator"),
			"lib/*", "core.jar", "plugins.jar");
		return Arrays.asList(
			java,
			"-Xms256m",
			"-Xmx1536m",
			"-Dopenrsc.worldBuilderCredentialFile=" + credential,
			"-Dopenrsc.worldBuilderControlDirectory=" + control,
			"-Dopenrsc.worldBuilderWorkspaceRoot=" + workspace,
			"-cp",
			classpath,
			"com.openrsc.server.Server",
			"world-builder.conf");
	}

	static List<String> defaultClientCommand(Path workspace, int port) {
		String credential = workspace.resolve(WorldBuilderRuntimePreparer.BUILDER_CREDENTIAL).toString();
		String projectName = workspace.getFileName().toString();
		String sourceRevision = readSourceRevision(workspace);
		WorldBuilderLayeredReview layered = readLayeredReview(workspace);
		List<String> command = new ArrayList<String>(Arrays.asList(
			javaExecutable(),
			"-Xms512m",
			"-Xmx2g",
			// The Builder's primary renderer owns its own LWJGL context. Avoid also
			// enabling Java2D's OpenGL pipeline. Start in the same borderless/vsynced
			// presentation used by the normal client while retaining the in-game
			// window-mode toggle for users who prefer a bounded window.
			"-Dsun.java2d.opengl=false",
			"-Dspoiledmilk.openglWindowMode=borderless-fullscreen",
			"-Dspoiledmilk.openglVsync=true",
			"-Dopenrsc.worldBuilderMode=true",
			"-Dopenrsc.worldBuilderHost=127.0.0.1",
			"-Dopenrsc.worldBuilderPort=" + port,
			"-Dopenrsc.worldBuilderCredentialFile=" + credential,
			"-Dopenrsc.worldBuilderProjectName=" + projectName,
			"-Dopenrsc.worldBuilderSourceRevision=" + sourceRevision));
		if (layered != null) {
			command.add("-Dopenrsc.worldBuilderLayeredReview=true");
			command.add("-Dopenrsc.worldBuilderLayeredTerrainDraft="
				+ layered.hasBuilderCreatedLevels());
			command.add("-Dopenrsc.worldBuilderLayeredPackageId=" + layered.packageId);
			command.add("-Dopenrsc.worldBuilderLayeredPackageVersion="
				+ layered.packageVersion);
			command.add("-Dopenrsc.worldBuilderLayeredManifestSha256="
				+ layered.manifestSha256);
			command.add("-Dopenrsc.worldBuilderLayeredWorldSpace="
				+ layered.worldSpace);
			command.add("-Dopenrsc.worldBuilderLayeredLevels="
				+ layered.levelsProperty());
		}
		command.addAll(Arrays.asList(
			"-jar",
			"Open_RSC_Client.jar"));
		return command;
	}

	private static WorldBuilderLayeredReview readLayeredReview(Path workspace) {
		try {
			return WorldBuilderLayeredReview.readIfPresent(workspace);
		} catch (IOException failure) {
			throw new IllegalStateException(
				"Prepared World Builder layered review metadata is invalid", failure);
		} catch (WorldBuilderDiscoveryException failure) {
			throw new IllegalStateException(
				"Prepared World Builder layered review metadata is invalid", failure);
		}
	}

	private static String readSourceRevision(Path workspace) {
		try {
			Path metadata = workspace.resolve("runtime.json");
			if (Files.size(metadata) > 16_384L) {
				throw new IOException("runtime metadata is unexpectedly large");
			}
			Matcher matcher = SOURCE_FINGERPRINT.matcher(
				new String(Files.readAllBytes(metadata), StandardCharsets.UTF_8));
			if (!matcher.find()) {
				throw new IOException("source revision is missing");
			}
			return matcher.group(1);
		} catch (IOException failure) {
			throw new IllegalStateException("Prepared World Builder source revision is invalid", failure);
		}
	}

	static int readPreparedPort(Path requestedWorkspace)
		throws IOException, WorldBuilderDiscoveryException {
		Path workspace = requestedWorkspace.toAbsolutePath().normalize();
		Path metadata = workspace.resolve("runtime.json");
		if (!Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(metadata) || Files.size(metadata) > 16_384L) {
			throw new WorldBuilderDiscoveryException(
				"Prepared World Builder runtime metadata is missing or unsafe.");
		}
		Matcher matcher = RUNTIME_PORT.matcher(
			new String(Files.readAllBytes(metadata), StandardCharsets.UTF_8));
		if (!matcher.find()) {
			throw new WorldBuilderDiscoveryException(
				"Prepared World Builder runtime port is missing.");
		}
		int port;
		try {
			port = Integer.parseInt(matcher.group(1));
		} catch (NumberFormatException invalid) {
			port = 0;
		}
		if (port < 1 || port >= 65535) {
			throw new WorldBuilderDiscoveryException(
				"Prepared World Builder runtime port is invalid.");
		}
		return port;
	}

	private static String javaExecutable() {
		String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
			? "java.exe" : "java";
		Path bundled = Paths.get(System.getProperty("java.home"), "bin", executable);
		return Files.isRegularFile(bundled) ? bundled.toString() : executable;
	}

	private static Process startProcess(List<String> command, Path directory, Path log) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(new ArrayList<String>(command));
		builder.directory(directory.toFile());
		builder.redirectErrorStream(true);
		builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
		return builder.start();
	}

	private static void waitForReady(Process server, Path ready, Path credential, int port,
		long timeoutMillis) throws IOException, WorldBuilderDiscoveryException, InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
		while (System.nanoTime() < deadline) {
			if (!server.isAlive()) {
				throw new WorldBuilderDiscoveryException(
					"World Builder server exited before it became ready (exit " + server.exitValue() + ").");
			}
			if (Files.isRegularFile(ready, LinkOption.NOFOLLOW_LINKS)
				&& Files.isRegularFile(credential, LinkOption.NOFOLLOW_LINKS)
				&& loopbackPortAcceptsConnections(port)) {
				return;
			}
			Thread.sleep(100L);
		}
		throw new WorldBuilderDiscoveryException(
			"World Builder server did not become ready within " + timeoutMillis + "ms.");
	}

	private static boolean loopbackPortAcceptsConnections(int port) {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
			return true;
		} catch (IOException ignored) {
			return false;
		}
	}

	private static void requestShutdown(Path shutdown) throws IOException {
		Files.createDirectories(shutdown.getParent());
		Path staged = shutdown.resolveSibling(shutdown.getFileName() + ".tmp");
		Files.write(staged, "shutdown\n".getBytes(StandardCharsets.US_ASCII));
		try {
			Files.move(staged, shutdown, StandardCopyOption.ATOMIC_MOVE,
				StandardCopyOption.REPLACE_EXISTING);
		} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
			Files.move(staged, shutdown, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void rotateLog(Path log) throws IOException {
		Files.createDirectories(log.getParent());
		if (!Files.exists(log, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		Path previous = log.resolveSibling(log.getFileName() + ".previous");
		Files.deleteIfExists(previous);
		Files.move(log, previous, StandardCopyOption.REPLACE_EXISTING);
	}

	private static void writePid(Path destination, Process process) throws IOException {
		Files.write(destination, (processId(process) + "\n").getBytes(StandardCharsets.US_ASCII));
	}

	private static long processId(Process process) {
		try {
			Method pid = Process.class.getMethod("pid");
			return ((Number)pid.invoke(process)).longValue();
		} catch (Exception ignored) {
			String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
			int at = runtimeName.indexOf('@');
			if (at > 0) {
				try {
					return Long.parseLong(runtimeName.substring(0, at));
				} catch (NumberFormatException ignoredAgain) {
				}
			}
			return -1L;
		}
	}

	private static void destroyForcibly(Process process) {
		try {
			Method method = Process.class.getMethod("destroyForcibly");
			method.invoke(process);
		} catch (Exception ignored) {
			process.destroy();
		}
	}

	private static void writeLastRun(Path destination, int serverExit, int clientExit,
		boolean serverFailedFirst) throws IOException {
		String json = "{\n"
			+ "  \"schemaVersion\": 1,\n"
			+ "  \"serverExit\": " + serverExit + ",\n"
			+ "  \"clientExit\": " + clientExit + ",\n"
			+ "  \"serverFailedFirst\": " + serverFailedFirst + "\n"
			+ "}\n";
		Files.write(destination, json.getBytes(StandardCharsets.UTF_8));
	}
}
