package com.openrsc.server.net.rsc;

import com.openrsc.server.net.PacketFrameLengthGuard;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Pure bounded paging policy for an already serialized layered-terrain stage.
 * Small stages retain their original single-packet representation.
 */
public final class LayeredTerrainStagePaging {
	public static final int TRANSPORT_PROTOCOL_VERSION = 6;
	public static final int MAX_PAGE_FRAGMENT_BYTES = 24_000;
	public static final int MAX_ASSEMBLED_STAGE_BYTES = 1_048_576;
	public static final int PAGE_ENVELOPE_BYTES = 23;

	private LayeredTerrainStagePaging() {
	}

	public static boolean requiresPaging(final int serializedBytes) {
		if (serializedBytes < 0
			|| serializedBytes > MAX_ASSEMBLED_STAGE_BYTES) {
			throw new IllegalArgumentException(
				"Layered terrain stage length is invalid: "
					+ serializedBytes);
		}
		return serializedBytes
			> PacketFrameLengthGuard.MAX_SIMPLIFIED_PAYLOAD_BYTES;
	}

	public static List<Page> split(
			final byte[] serializedStage,
			final int stageSequence,
			final int contextSequence) {
		if (serializedStage == null
			|| serializedStage.length == 0
			|| !requiresPaging(serializedStage.length)) {
			throw new IllegalArgumentException(
				"Only oversized non-empty terrain stages may be paged");
		}
		if (stageSequence <= 0 || contextSequence <= 0) {
			throw new IllegalArgumentException(
				"Layered terrain page identity is invalid");
		}
		final int pageCount = Math.addExact(
			serializedStage.length,
			MAX_PAGE_FRAGMENT_BYTES - 1) / MAX_PAGE_FRAGMENT_BYTES;
		if (pageCount <= 1 || pageCount > 0xFFFF) {
			throw new IllegalArgumentException(
				"Layered terrain page count is invalid");
		}
		final int crc32 = crc32(serializedStage);
		final List<Page> pages = new ArrayList<Page>(pageCount);
		for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
			final int offset = Math.multiplyExact(
				pageIndex, MAX_PAGE_FRAGMENT_BYTES);
			final int fragmentLength = Math.min(
				MAX_PAGE_FRAGMENT_BYTES,
				serializedStage.length - offset);
			pages.add(new Page(
				stageSequence,
				contextSequence,
				serializedStage.length,
				crc32,
				pageIndex,
				pageCount,
				Arrays.copyOfRange(
					serializedStage, offset, offset + fragmentLength)));
		}
		return Collections.unmodifiableList(pages);
	}

	private static int crc32(final byte[] bytes) {
		final CRC32 crc = new CRC32();
		crc.update(bytes, 0, bytes.length);
		return (int) crc.getValue();
	}

	public static final class Page {
		private final int stageSequence;
		private final int contextSequence;
		private final int totalBytes;
		private final int crc32;
		private final int pageIndex;
		private final int pageCount;
		private final byte[] fragment;

		private Page(
				final int stageSequence,
				final int contextSequence,
				final int totalBytes,
				final int crc32,
				final int pageIndex,
				final int pageCount,
				final byte[] fragment) {
			this.stageSequence = stageSequence;
			this.contextSequence = contextSequence;
			this.totalBytes = totalBytes;
			this.crc32 = crc32;
			this.pageIndex = pageIndex;
			this.pageCount = pageCount;
			this.fragment = fragment;
		}

		public int getStageSequence() {
			return stageSequence;
		}

		public int getContextSequence() {
			return contextSequence;
		}

		public int getTotalBytes() {
			return totalBytes;
		}

		public int getCrc32() {
			return crc32;
		}

		public int getPageIndex() {
			return pageIndex;
		}

		public int getPageCount() {
			return pageCount;
		}

		public byte[] copyFragment() {
			return Arrays.copyOf(fragment, fragment.length);
		}

		public int getFragmentLength() {
			return fragment.length;
		}

		public byte[] toWirePayload() {
			final ByteBuffer output = ByteBuffer.allocate(
				PAGE_ENVELOPE_BYTES + fragment.length);
			output.put((byte) TRANSPORT_PROTOCOL_VERSION);
			output.putInt(stageSequence);
			output.putInt(contextSequence);
			output.putInt(totalBytes);
			output.putInt(crc32);
			output.putShort((short) pageIndex);
			output.putShort((short) pageCount);
			output.putShort((short) fragment.length);
			output.put(fragment);
			return output.array();
		}
	}
}
