package com.openrsc.server;

/**
 * Selects a bounded scene-baseline delivery budget from exact remaining work.
 *
 * <p>Protocol-v8 atomic activation must not wait a complete world tick merely
 * because an ordinary scene needs slightly more than the steady-state page
 * burst. It also must not turn an unusually dense scene into an unbounded
 * game-thread write. This policy permits same-tick completion only below both
 * an explicit page cap and an explicit wire-byte cap. Oversized products keep
 * the ordinary bounded paging behavior and are surfaced by diagnostics.</p>
 */
final class SceneBaselineDeliveryPolicy {
	static final int ATOMIC_COMPLETE_MAX_PAGES = 16;
	static final long ATOMIC_COMPLETE_MAX_WIRE_BYTES = 96L * 1024L;

	enum Mode {
		EMPTY,
		STANDARD_BOUNDED,
		ATOMIC_COMPLETE,
		ATOMIC_OVERSIZED_FALLBACK
	}

	static final class Decision {
		private final int pageLimit;
		private final Mode mode;

		private Decision(final int pageLimit, final Mode mode) {
			this.pageLimit = pageLimit;
			this.mode = mode;
		}

		int getPageLimit() {
			return pageLimit;
		}

		Mode getMode() {
			return mode;
		}

		boolean completesAtomicProduct() {
			return mode == Mode.ATOMIC_COMPLETE;
		}
	}

	private SceneBaselineDeliveryPolicy() {
	}

	static Decision decide(
			final int standardPageLimit,
			final boolean atomicActivationPending,
			final int remainingPages,
			final long remainingWireBytes) {
		if (standardPageLimit <= 0) {
			throw new IllegalArgumentException(
				"Standard page limit must be positive");
		}
		if (remainingPages < 0 || remainingWireBytes < 0L) {
			throw new IllegalArgumentException(
				"Remaining baseline work cannot be negative");
		}
		if (remainingPages == 0) {
			return new Decision(0, Mode.EMPTY);
		}
		if (!atomicActivationPending) {
			return new Decision(
				standardPageLimit, Mode.STANDARD_BOUNDED);
		}
		if (remainingPages <= ATOMIC_COMPLETE_MAX_PAGES
			&& remainingWireBytes <= ATOMIC_COMPLETE_MAX_WIRE_BYTES) {
			return new Decision(remainingPages, Mode.ATOMIC_COMPLETE);
		}
		return new Decision(
			standardPageLimit, Mode.ATOMIC_OVERSIZED_FALLBACK);
	}

	static int remainingPageCount(
			final int recordCount,
			final int pageCursor,
			final int pageSize) {
		final long remainingRecords = remainingRecordCount(
			recordCount, pageCursor, pageSize);
		if (remainingRecords == 0L) {
			return 0;
		}
		return Math.toIntExact(
			(remainingRecords + pageSize - 1L) / pageSize);
	}

	static long remainingWireBytes(
			final int recordCount,
			final int pageCursor,
			final int pageSize,
			final int fixedPayloadBytes,
			final int recordBytes,
			final int frameOverheadBytes) {
		if (fixedPayloadBytes < 0
			|| recordBytes < 0
			|| frameOverheadBytes < 0) {
			throw new IllegalArgumentException(
				"Baseline byte sizes cannot be negative");
		}
		final long remainingRecords = remainingRecordCount(
			recordCount, pageCursor, pageSize);
		final int remainingPages = remainingPageCount(
			recordCount, pageCursor, pageSize);
		return Math.addExact(
			Math.multiplyExact(
				(long)remainingPages,
				(long)fixedPayloadBytes + frameOverheadBytes),
			Math.multiplyExact(remainingRecords, (long)recordBytes));
	}

	private static long remainingRecordCount(
			final int recordCount,
			final int pageCursor,
			final int pageSize) {
		if (recordCount < 0 || pageCursor < 0) {
			throw new IllegalArgumentException(
				"Baseline record count and cursor cannot be negative");
		}
		if (pageSize <= 0) {
			throw new IllegalArgumentException(
				"Baseline page size must be positive");
		}
		return Math.max(
			0L,
			(long)recordCount
				- Math.multiplyExact((long)pageCursor, (long)pageSize));
	}
}
