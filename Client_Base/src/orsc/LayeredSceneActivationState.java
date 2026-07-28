package orsc;

/**
 * Client-local atomic presentation barrier for one protocol-v8 scene context.
 *
 * Terrain is installed before this barrier begins. Presentation remains
 * covered until the same context has supplied both its first Player receipt
 * and a complete, materialized static-scene baseline.
 */
final class LayeredSceneActivationState {
	private int sequence;
	private boolean pending;
	private boolean playerReceipt;
	private boolean staticBaseline;
	private int completed;
	private long startedNanos;
	private long lastElapsedMillis;

	void begin(final int contextSequence) {
		if (contextSequence <= 0
			|| sequence > 0 && contextSequence <= sequence) {
			throw new IllegalArgumentException(
				"Atomic scene activation sequence is invalid");
		}
		sequence = contextSequence;
		pending = true;
		playerReceipt = false;
		staticBaseline = false;
		startedNanos = System.nanoTime();
	}

	boolean acceptPlayerReceipt(final int contextSequence) {
		if (!matches(contextSequence)) {
			return false;
		}
		playerReceipt = true;
		return finishIfComplete();
	}

	boolean acceptStaticBaseline(final int contextSequence) {
		if (!matches(contextSequence)) {
			return false;
		}
		staticBaseline = true;
		return finishIfComplete();
	}

	boolean isPending() {
		return pending;
	}

	boolean hasStarted() {
		return sequence > 0;
	}

	String summary() {
		if (sequence <= 0) {
			return "atomic idle";
		}
		return "atomic " + (pending ? "wait" : "ready")
			+ " seq " + sequence
			+ " player/base "
			+ flag(playerReceipt) + "/" + flag(staticBaseline)
			+ " done " + completed
			+ " ms " + elapsedMillis();
	}

	void reset() {
		sequence = 0;
		pending = false;
		playerReceipt = false;
		staticBaseline = false;
		completed = 0;
		startedNanos = 0L;
		lastElapsedMillis = 0L;
	}

	private boolean matches(final int contextSequence) {
		return pending && sequence == contextSequence;
	}

	private boolean finishIfComplete() {
		if (pending && playerReceipt && staticBaseline) {
			lastElapsedMillis = elapsedMillis();
			pending = false;
			completed++;
			return true;
		}
		return false;
	}

	private long elapsedMillis() {
		if (pending && startedNanos > 0L) {
			return (System.nanoTime() - startedNanos) / 1000000L;
		}
		return lastElapsedMillis;
	}

	private static String flag(final boolean value) {
		return value ? "ok" : "-";
	}
}
