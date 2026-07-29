package com.openrsc.server.net.rsc;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.zip.Deflater;

/**
 * Lazily owns one compressed native-terrain wire product per immutable sector
 * slot and content identity.
 *
 * <p>The cache deliberately accepts an unevaluated raw-byte supplier. A hit
 * therefore avoids both rebuilding the fixed-width sector image and running
 * the compressor. Reusing a slot with a different content identity replaces
 * the old product, which bounds Builder/reload revisions to one entry per
 * logical sector.</p>
 */
public final class NativeLayeredTerrainWireCache {
	private final Map<String, Entry> entries = new HashMap<String, Entry>();

	public synchronized Lookup getOrCompress(
		final String slotIdentity,
		final String contentIdentity,
		final int expectedRawBytes,
		final Supplier<byte[]> rawBytesSupplier) {
		String checkedSlot = requireIdentity(slotIdentity, "slot");
		String checkedContent = requireIdentity(contentIdentity, "content");
		if (expectedRawBytes <= 0 || expectedRawBytes > 16 * 1024 * 1024) {
			throw new IllegalArgumentException(
				"Native terrain raw wire length is invalid");
		}
		Supplier<byte[]> checkedSupplier = Objects.requireNonNull(
			rawBytesSupplier, "rawBytesSupplier");
		Entry cached = entries.get(checkedSlot);
		if (cached != null
			&& cached.contentIdentity.equals(checkedContent)) {
			return new Lookup(
				cached.compressedBytes,
				expectedRawBytes,
				true,
				0L,
				entries.size());
		}

		long buildStart = System.nanoTime();
		byte[] rawBytes = Objects.requireNonNull(
			checkedSupplier.get(), "rawBytesSupplier result");
		if (rawBytes.length != expectedRawBytes) {
			throw new IllegalArgumentException(
				"Native terrain raw wire length differs from its sector size");
		}
		byte[] compressedBytes = compress(rawBytes);
		long buildNanos = System.nanoTime() - buildStart;
		entries.put(
			checkedSlot,
			new Entry(checkedContent, compressedBytes));
		return new Lookup(
			compressedBytes,
			expectedRawBytes,
			false,
			buildNanos,
			entries.size());
	}

	public synchronized int size() {
		return entries.size();
	}

	public synchronized void clear() {
		entries.clear();
	}

	private static String requireIdentity(
		final String value,
		final String label) {
		String checked = Objects.requireNonNull(value, label + "Identity");
		if (checked.isEmpty() || checked.length() > 1024) {
			throw new IllegalArgumentException(
				"Native terrain " + label + " identity is invalid");
		}
		return checked;
	}

	private static byte[] compress(final byte[] source) {
		final Deflater compressor = new Deflater(Deflater.BEST_SPEED);
		try {
			compressor.setInput(source);
			compressor.finish();
			final byte[] buffer = new byte[source.length + 128];
			final int length = compressor.deflate(buffer);
			if (!compressor.finished() || length <= 0
				|| length > 0xFFFF) {
				throw new IllegalStateException(
					"Native terrain sector compression exceeded one packet field");
			}
			byte[] result = new byte[length];
			System.arraycopy(buffer, 0, result, 0, length);
			return result;
		} finally {
			compressor.end();
		}
	}

	private static final class Entry {
		private final String contentIdentity;
		private final byte[] compressedBytes;

		private Entry(
			final String contentIdentity,
			final byte[] compressedBytes) {
			this.contentIdentity = contentIdentity;
			this.compressedBytes = compressedBytes;
		}
	}

	public static final class Lookup {
		private final byte[] compressedBytes;
		private final int rawBytes;
		private final boolean cacheHit;
		private final long buildNanos;
		private final int cacheEntries;

		private Lookup(
			final byte[] compressedBytes,
			final int rawBytes,
			final boolean cacheHit,
			final long buildNanos,
			final int cacheEntries) {
			this.compressedBytes = compressedBytes;
			this.rawBytes = rawBytes;
			this.cacheHit = cacheHit;
			this.buildNanos = buildNanos;
			this.cacheEntries = cacheEntries;
		}

		/**
		 * Returns the cache-owned immutable packet payload. Callers must not
		 * modify the returned array.
		 */
		public byte[] getCompressedBytes() {
			return compressedBytes;
		}

		public int getRawBytes() {
			return rawBytes;
		}

		public int getCompressedByteCount() {
			return compressedBytes.length;
		}

		public boolean isCacheHit() {
			return cacheHit;
		}

		public long getBuildNanos() {
			return buildNanos;
		}

		public int getCacheEntries() {
			return cacheEntries;
		}
	}
}
