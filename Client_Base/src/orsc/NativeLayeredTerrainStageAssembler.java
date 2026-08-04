package orsc;

import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Bounded, connection-local assembler for oversized layered-terrain stages.
 * No terrain cache or scene state is mutated until a complete CRC-verified
 * stage is returned to {@link PacketHandler}.
 */
public final class NativeLayeredTerrainStageAssembler {
	public static final int TRANSPORT_PROTOCOL_VERSION = 6;
	public static final int MAX_UNPAGED_STAGE_BYTES = 65_532;
	public static final int MAX_PAGE_FRAGMENT_BYTES = 24_000;
	public static final int MAX_ASSEMBLED_STAGE_BYTES = 1_048_576;
	public static final int PAGE_ENVELOPE_BYTES = 23;

	private Assembly pending;

	public synchronized CompletedStage accept(
			final int stageSequence,
			final int contextSequence,
			final int totalBytes,
			final int expectedCrc32,
			final int pageIndex,
			final int pageCount,
			final byte[] fragment,
			final int completedStageSequence,
			final int activeContextSequence) {
		if (!validPage(
				stageSequence,
				contextSequence,
				totalBytes,
				pageIndex,
				pageCount,
				fragment,
				completedStageSequence,
				activeContextSequence)) {
			if (pending != null
				&& pending.matches(stageSequence, contextSequence)) {
				pending = null;
			}
			return null;
		}

		if (pending == null) {
			if (pageIndex != 0) {
				return null;
			}
			pending = new Assembly(
				stageSequence,
				contextSequence,
				totalBytes,
				expectedCrc32,
				pageCount);
		} else if (!pending.matches(
				stageSequence,
				contextSequence,
				totalBytes,
				expectedCrc32,
				pageCount)) {
			if (pageIndex != 0
				|| stageSequence <= pending.stageSequence) {
				return null;
			}
			pending = new Assembly(
				stageSequence,
				contextSequence,
				totalBytes,
				expectedCrc32,
				pageCount);
		}

		if (pageIndex < pending.nextPageIndex) {
			return null;
		}
		if (pageIndex > pending.nextPageIndex) {
			pending = null;
			return null;
		}

		System.arraycopy(
			fragment,
			0,
			pending.bytes,
			pending.receivedBytes,
			fragment.length);
		pending.receivedBytes += fragment.length;
		pending.nextPageIndex++;
		if (pending.nextPageIndex < pending.pageCount) {
			return null;
		}

		final Assembly completed = pending;
		pending = null;
		if (completed.receivedBytes != completed.totalBytes
			|| crc32(completed.bytes) != completed.expectedCrc32) {
			return null;
		}
		return new CompletedStage(
			completed.stageSequence,
			completed.contextSequence,
			completed.bytes);
	}

	public synchronized void reset() {
		pending = null;
	}

	public synchronized boolean hasPendingStage() {
		return pending != null;
	}

	public synchronized int getBufferedByteCount() {
		return pending == null ? 0 : pending.receivedBytes;
	}

	private static boolean validPage(
			final int stageSequence,
			final int contextSequence,
			final int totalBytes,
			final int pageIndex,
			final int pageCount,
			final byte[] fragment,
			final int completedStageSequence,
			final int activeContextSequence) {
		if (stageSequence <= completedStageSequence
			|| contextSequence <= 0
			|| contextSequence != activeContextSequence
			|| totalBytes <= MAX_UNPAGED_STAGE_BYTES
			|| totalBytes > MAX_ASSEMBLED_STAGE_BYTES
			|| fragment == null) {
			return false;
		}
		final int expectedPageCount = Math.addExact(
			totalBytes,
			MAX_PAGE_FRAGMENT_BYTES - 1) / MAX_PAGE_FRAGMENT_BYTES;
		if (pageCount != expectedPageCount
			|| pageCount <= 1
			|| pageCount > 0xFFFF
			|| pageIndex < 0
			|| pageIndex >= pageCount) {
			return false;
		}
		final int pageOffset = Math.multiplyExact(
			pageIndex, MAX_PAGE_FRAGMENT_BYTES);
		final int expectedFragmentBytes = Math.min(
			MAX_PAGE_FRAGMENT_BYTES,
			totalBytes - pageOffset);
		return fragment.length == expectedFragmentBytes;
	}

	private static int crc32(final byte[] bytes) {
		final CRC32 crc = new CRC32();
		crc.update(bytes, 0, bytes.length);
		return (int) crc.getValue();
	}

	private static final class Assembly {
		private final int stageSequence;
		private final int contextSequence;
		private final int totalBytes;
		private final int expectedCrc32;
		private final int pageCount;
		private final byte[] bytes;
		private int nextPageIndex;
		private int receivedBytes;

		private Assembly(
				final int stageSequence,
				final int contextSequence,
				final int totalBytes,
				final int expectedCrc32,
				final int pageCount) {
			this.stageSequence = stageSequence;
			this.contextSequence = contextSequence;
			this.totalBytes = totalBytes;
			this.expectedCrc32 = expectedCrc32;
			this.pageCount = pageCount;
			this.bytes = new byte[totalBytes];
		}

		private boolean matches(
				final int otherStageSequence,
				final int otherContextSequence) {
			return stageSequence == otherStageSequence
				&& contextSequence == otherContextSequence;
		}

		private boolean matches(
				final int otherStageSequence,
				final int otherContextSequence,
				final int otherTotalBytes,
				final int otherCrc32,
				final int otherPageCount) {
			return matches(otherStageSequence, otherContextSequence)
				&& totalBytes == otherTotalBytes
				&& expectedCrc32 == otherCrc32
				&& pageCount == otherPageCount;
		}
	}

	public static final class CompletedStage {
		private final int stageSequence;
		private final int contextSequence;
		private final byte[] bytes;

		private CompletedStage(
				final int stageSequence,
				final int contextSequence,
				final byte[] bytes) {
			this.stageSequence = stageSequence;
			this.contextSequence = contextSequence;
			this.bytes = bytes;
		}

		public int getStageSequence() {
			return stageSequence;
		}

		public int getContextSequence() {
			return contextSequence;
		}

		public byte[] copyBytes() {
			return Arrays.copyOf(bytes, bytes.length);
		}
	}
}
