package com.openrsc.server.content.worldedit;

import com.openrsc.server.Server;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/** Loopback Builder-only readiness and graceful-shutdown control channel. */
public final class WorldBuilderRuntimeControl {
	public static final String CONTROL_DIRECTORY_PROPERTY = "openrsc.worldBuilderControlDirectory";
	public static final String DEFAULT_CONTROL_DIRECTORY = "run/world-builder";
	public static final String READY_FILE = "ready";
	public static final String SHUTDOWN_FILE = "shutdown.request";
	private static final Logger LOGGER = LogManager.getLogger(WorldBuilderRuntimeControl.class);

	private WorldBuilderRuntimeControl() {
	}

	public static void start(final Server server) throws IOException {
		if (!server.getConfig().WORLD_BUILDER_MODE) {
			return;
		}
		final Path directory = resolveControlDirectory();
		Files.createDirectories(directory);
		if (Files.isSymbolicLink(directory)) {
			throw new IOException("World Builder control directory cannot be a symbolic link");
		}
		server.getWorldEditStorage().validateGeneratedPath(
			directory.resolve(READY_FILE), "World Builder readiness file");
		server.getWorldEditStorage().validateGeneratedPath(
			directory.resolve(SHUTDOWN_FILE), "World Builder shutdown request");
		final Path ready = directory.resolve(READY_FILE);
		final Path shutdown = directory.resolve(SHUTDOWN_FILE);
		Files.deleteIfExists(ready);
		Files.deleteIfExists(shutdown);
		if (AdaptiveWorldBuilderRuntimeIdentity.isAdaptive(server.getConfig())) {
			server.setAdaptiveWorldBuilderRuntimeSession(
				AdaptiveWorldBuilderRuntimeSession.publish(server, directory));
		}
		Path stagedReady = directory.resolve(READY_FILE + ".tmp");
		Files.write(stagedReady, "ready\n".getBytes(StandardCharsets.US_ASCII));
		try {
			Files.move(stagedReady, ready, StandardCopyOption.ATOMIC_MOVE,
				StandardCopyOption.REPLACE_EXISTING);
		} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
			Files.move(stagedReady, ready, StandardCopyOption.REPLACE_EXISTING);
		}

		Thread watcher = new Thread(new Runnable() {
			@Override
			public void run() {
				while (server.isRunning()) {
					try {
						if (Files.isRegularFile(shutdown, LinkOption.NOFOLLOW_LINKS)) {
							Files.deleteIfExists(shutdown);
							Files.deleteIfExists(ready);
							LOGGER.info("World Builder launcher requested a clean local shutdown");
							server.shutdown(0);
							return;
						}
						Thread.sleep(200L);
					} catch (InterruptedException interrupted) {
						Thread.currentThread().interrupt();
						return;
					} catch (IOException failure) {
						LOGGER.error("World Builder runtime control failed", failure);
						return;
					}
				}
			}
		}, "World Builder Runtime Control");
		watcher.setDaemon(true);
		watcher.start();
	}

	static Path resolveControlDirectory() throws IOException {
		Path runtimeRoot = Paths.get("").toAbsolutePath().normalize();
		String configured = System.getProperty(CONTROL_DIRECTORY_PROPERTY, DEFAULT_CONTROL_DIRECTORY);
		Path directory = Paths.get(configured);
		if (!directory.isAbsolute()) {
			directory = runtimeRoot.resolve(directory);
		}
		directory = directory.toAbsolutePath().normalize();
		if (!directory.startsWith(runtimeRoot)) {
			throw new IOException("World Builder control directory must remain inside the isolated runtime");
		}
		return directory;
	}
}
