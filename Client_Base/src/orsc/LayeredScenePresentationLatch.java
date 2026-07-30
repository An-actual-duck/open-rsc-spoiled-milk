package orsc;

/**
 * Keeps the last complete same-scope scene visible across atomic activation.
 *
 * <p>The network activation barrier can complete between rendered gameplay
 * frames. Releasing the old frame immediately would let the presenter observe
 * the newly installed terrain before the client has rebuilt the matching
 * scenery/object chunks. This latch therefore survives barrier completion and
 * releases only after one fresh gameplay scene frame has been assembled.</p>
 */
final class LayeredScenePresentationLatch {
	private boolean pending;
	private boolean retainPresentedFrame;
	private boolean awaitingFreshFrame;

	void begin(boolean retainCurrentFrame) {
		pending = true;
		retainPresentedFrame = retainCurrentFrame;
		awaitingFreshFrame = false;
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

	boolean completeFreshFrame() {
		if (!awaitingFreshFrame) {
			return false;
		}
		awaitingFreshFrame = false;
		retainPresentedFrame = false;
		return true;
	}

	void reset() {
		pending = false;
		retainPresentedFrame = false;
		awaitingFreshFrame = false;
	}
}
