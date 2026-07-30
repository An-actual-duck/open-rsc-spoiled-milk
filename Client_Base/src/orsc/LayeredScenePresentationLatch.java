package orsc;

/**
 * Keeps the last complete same-scope scene visible across atomic activation.
 *
 * <p>The network activation barrier can complete between rendered gameplay
 * frames. Releasing the old frame immediately would let the presenter observe
 * the newly installed terrain before the client has rebuilt the matching
 * scenery/object chunks. The first assembled frame can still precede the last
 * static-scene baseline update, so this latch requires two consecutive
 * matching static-world signatures before releasing it. A hard sample bound
 * prevents an incorrectly classified continuously-changing chunk from holding
 * the old presentation forever.</p>
 */
final class LayeredScenePresentationLatch {
	static final int MAX_FRESH_FRAME_SAMPLES = 8;

	private boolean pending;
	private boolean retainPresentedFrame;
	private boolean awaitingFreshFrame;
	private boolean candidateKnown;
	private long candidateStaticWorldSignature;
	private int candidateStaticChunkCount;
	private int freshFrameSamples;
	private boolean lastReleaseStable;

	void begin(boolean retainCurrentFrame) {
		pending = true;
		retainPresentedFrame = retainCurrentFrame;
		awaitingFreshFrame = false;
		resetFreshFrameCandidate();
	}

	void updatePending(boolean nextPending) {
		if (nextPending) {
			pending = true;
			return;
		}
		if (pending && retainPresentedFrame) {
			awaitingFreshFrame = true;
		}
		pending = false;
		if (!awaitingFreshFrame) {
			retainPresentedFrame = false;
		}
	}

	boolean shouldRetainLastPresentedFrame() {
		return pending && retainPresentedFrame || awaitingFreshFrame;
	}

	boolean completeFreshFrame(
		long staticWorldSignature,
		int staticChunkCount) {
		if (!awaitingFreshFrame) {
			return false;
		}
		freshFrameSamples++;
		boolean stable =
			staticChunkCount > 0
				&& candidateKnown
				&& candidateStaticChunkCount == staticChunkCount
				&& candidateStaticWorldSignature
					== staticWorldSignature;
		if (!stable
			&& freshFrameSamples < MAX_FRESH_FRAME_SAMPLES) {
			candidateKnown = staticChunkCount > 0;
			candidateStaticWorldSignature = staticWorldSignature;
			candidateStaticChunkCount = staticChunkCount;
			return false;
		}
		awaitingFreshFrame = false;
		retainPresentedFrame = false;
		lastReleaseStable = stable;
		return true;
	}

	int getFreshFrameSamples() {
		return freshFrameSamples;
	}

	boolean wasLastReleaseStable() {
		return lastReleaseStable;
	}

	void reset() {
		pending = false;
		retainPresentedFrame = false;
		awaitingFreshFrame = false;
		resetFreshFrameCandidate();
	}

	private void resetFreshFrameCandidate() {
		candidateKnown = false;
		candidateStaticWorldSignature = 0L;
		candidateStaticChunkCount = 0;
		freshFrameSamples = 0;
		lastReleaseStable = false;
	}
}
