package com.openrsc.server.content.worldedit;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Process-lifetime lock for one explicitly selected isolated Builder project. */
final class AdaptiveWorldBuilderProjectLock implements Closeable {
	private final Path path;
	private final FileChannel channel;
	private final FileLock lock;

	private AdaptiveWorldBuilderProjectLock(
		Path path, FileChannel channel, FileLock lock) {
		this.path = path;
		this.channel = channel;
		this.lock = lock;
	}

	static AdaptiveWorldBuilderProjectLock acquire(
		WorldEditStorageContext storage, Path controlDirectory) throws IOException {
		Path path = storage.validateGeneratedPath(
			controlDirectory.resolve("adaptive-runtime.lock"),
			"adaptive runtime lock");
		if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
				|| Files.isSymbolicLink(path)) {
				throw new IOException("Adaptive runtime lock is unsafe");
			}
			requireSingleLink(path);
		}
		FileChannel channel = FileChannel.open(
			path, StandardOpenOption.CREATE, StandardOpenOption.READ,
			StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
		try {
			FileLock lock;
			try {
				lock = channel.tryLock();
			} catch (OverlappingFileLockException unavailable) {
				lock = null;
			}
			if (lock == null) {
				throw new IOException(
					"Another adaptive World Builder runtime owns this project");
			}
			channel.truncate(0L);
			channel.position(0L);
			channel.write(ByteBuffer.wrap(
				("adaptive-world-builder-runtime-lock-v1\n")
					.getBytes(StandardCharsets.US_ASCII)));
			channel.force(true);
			return new AdaptiveWorldBuilderProjectLock(path, channel, lock);
		} catch (IOException failure) {
			channel.close();
			throw failure;
		}
	}

	@Override
	public void close() throws IOException {
		IOException failure = null;
		try {
			lock.release();
		} catch (IOException releaseFailure) {
			failure = releaseFailure;
		}
		try {
			channel.close();
		} catch (IOException closeFailure) {
			if (failure == null) failure = closeFailure;
			else failure.addSuppressed(closeFailure);
		}
		try {
			Files.deleteIfExists(path);
		} catch (IOException deleteFailure) {
			if (failure == null) failure = deleteFailure;
			else failure.addSuppressed(deleteFailure);
		}
		if (failure != null) throw failure;
	}

	private static void requireSingleLink(Path path) throws IOException {
		try {
			Object count = Files.getAttribute(
				path, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
			if (count instanceof Number && ((Number)count).longValue() != 1L) {
				throw new IOException("Adaptive runtime lock is hard linked");
			}
		} catch (UnsupportedOperationException unsupported) {
			path.toRealPath();
		} catch (IllegalArgumentException unsupported) {
			path.toRealPath();
		}
	}
}
