package orsc;

import orsc.graphics.three.Renderer3DWorldChunkFrame;

final class OpenGLWorldChunkUploadStats {
	static final int ROLE_WORLD = 0;
	static final int ROLE_STATIC_OBJECTS = 1;
	static final int ROLE_ANIMATED_OBJECTS = 2;
	static final int ROLE_COUNT = 3;

	static final OpenGLWorldChunkUploadStats EMPTY =
		new OpenGLWorldChunkUploadStats(
			0,
			0,
			0,
			0,
			0,
			"empty",
			0L,
			OpenGLWorldChunkRenderer.CHUNK_UPLOAD_BUDGET_NANOS,
			new int[ROLE_COUNT],
			new int[ROLE_COUNT],
			new int[ROLE_COUNT],
			new int[ROLE_COUNT],
			new long[ROLE_COUNT],
			new long[ROLE_COUNT],
			0,
			0,
			0,
			0,
			0,
			0,
			"",
			false);

	final int requestedChunks;
	final int uploadedChunks;
	final int reusedChunks;
	final int deferredChunks;
	final int evictedChunks;
	final String reason;
	final long budgetUsedNanos;
	final long budgetLimitNanos;
	private final int[] requestedByRole;
	private final int[] uploadedByRole;
	private final int[] reusedByRole;
	private final int[] deferredByRole;
	private final long[] uploadedBytesByRole;
	private final long[] uploadNanosByRole;
	final int coldKeyMisses;
	final int alternateStorageKeyMisses;
	final int alternateEquivalentKeyMisses;
	final int existingKeyMismatches;
	final int cacheSizeBefore;
	final int cacheSizeAfter;
	final String diagnosticDetail;
	final boolean diagnosticDetailTruncated;

	OpenGLWorldChunkUploadStats(
		int requestedChunks,
		int uploadedChunks,
		int reusedChunks,
		int deferredChunks,
		int evictedChunks,
		String reason,
		long budgetUsedNanos,
		long budgetLimitNanos,
		int[] requestedByRole,
		int[] uploadedByRole,
		int[] reusedByRole,
		int[] deferredByRole,
		long[] uploadedBytesByRole,
		long[] uploadNanosByRole,
		int coldKeyMisses,
		int alternateStorageKeyMisses,
		int alternateEquivalentKeyMisses,
		int existingKeyMismatches,
		int cacheSizeBefore,
		int cacheSizeAfter,
		String diagnosticDetail,
		boolean diagnosticDetailTruncated) {
		this.requestedChunks = requestedChunks;
		this.uploadedChunks = uploadedChunks;
		this.reusedChunks = reusedChunks;
		this.deferredChunks = deferredChunks;
		this.evictedChunks = evictedChunks;
		this.reason = reason == null || reason.trim().isEmpty() ? "unknown" : reason;
		this.budgetUsedNanos = Math.max(0L, budgetUsedNanos);
		this.budgetLimitNanos = Math.max(0L, budgetLimitNanos);
		this.requestedByRole = normalizeIntRoles(requestedByRole);
		this.uploadedByRole = normalizeIntRoles(uploadedByRole);
		this.reusedByRole = normalizeIntRoles(reusedByRole);
		this.deferredByRole = normalizeIntRoles(deferredByRole);
		this.uploadedBytesByRole = normalizeLongRoles(uploadedBytesByRole);
		this.uploadNanosByRole = normalizeLongRoles(uploadNanosByRole);
		this.coldKeyMisses = Math.max(0, coldKeyMisses);
		this.alternateStorageKeyMisses = Math.max(0, alternateStorageKeyMisses);
		this.alternateEquivalentKeyMisses = Math.max(0, alternateEquivalentKeyMisses);
		this.existingKeyMismatches = Math.max(0, existingKeyMismatches);
		this.cacheSizeBefore = Math.max(0, cacheSizeBefore);
		this.cacheSizeAfter = Math.max(0, cacheSizeAfter);
		this.diagnosticDetail =
			diagnosticDetail == null ? "" : diagnosticDetail;
		this.diagnosticDetailTruncated = diagnosticDetailTruncated;
	}

	int requestedForRole(int chunkRole) {
		return requestedByRole[roleIndex(chunkRole)];
	}

	int uploadedForRole(int chunkRole) {
		return uploadedByRole[roleIndex(chunkRole)];
	}

	int reusedForRole(int chunkRole) {
		return reusedByRole[roleIndex(chunkRole)];
	}

	int deferredForRole(int chunkRole) {
		return deferredByRole[roleIndex(chunkRole)];
	}

	long uploadedBytesForRole(int chunkRole) {
		return uploadedBytesByRole[roleIndex(chunkRole)];
	}

	long uploadNanosForRole(int chunkRole) {
		return uploadNanosByRole[roleIndex(chunkRole)];
	}

	static int roleIndex(int chunkRole) {
		if (chunkRole
				== Renderer3DWorldChunkFrame
					.CHUNK_ROLE_STATIC_OBJECTS) {
			return ROLE_STATIC_OBJECTS;
		}
		if (chunkRole
				== Renderer3DWorldChunkFrame
					.CHUNK_ROLE_ANIMATED_OBJECTS) {
			return ROLE_ANIMATED_OBJECTS;
		}
		return ROLE_WORLD;
	}

	private static int[] normalizeIntRoles(int[] values) {
		int[] normalized = new int[ROLE_COUNT];
		if (values != null) {
			System.arraycopy(
				values,
				0,
				normalized,
				0,
				Math.min(values.length, normalized.length));
		}
		for (int index = 0; index < normalized.length; index++) {
			normalized[index] = Math.max(0, normalized[index]);
		}
		return normalized;
	}

	private static long[] normalizeLongRoles(long[] values) {
		long[] normalized = new long[ROLE_COUNT];
		if (values != null) {
			System.arraycopy(
				values,
				0,
				normalized,
				0,
				Math.min(values.length, normalized.length));
		}
		for (int index = 0; index < normalized.length; index++) {
			normalized[index] = Math.max(0L, normalized[index]);
		}
		return normalized;
	}
}
