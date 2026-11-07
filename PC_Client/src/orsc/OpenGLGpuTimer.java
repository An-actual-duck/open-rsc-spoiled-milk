package orsc;

/** Timestamp pairs allow nested pass measurement; results are never waited on. */
final class OpenGLGpuTimer implements AutoCloseable {
	interface SampleSink { void sample(String pass, long sequence, long gpuNanos); }
	private static volatile SampleSink diagnosticSink;
	static void setDiagnosticSink(SampleSink sink) { diagnosticSink = sink; }
	private static final int RING_SIZE = 8;
	private final OpenGLShadowBindings functions;
	private final int[] starts = new int[RING_SIZE], ends = new int[RING_SIZE];
	private final long[] sequences = new long[RING_SIZE];
	private final boolean[] pending = new boolean[RING_SIZE];
	private final String label;
	private int cursor;
	private boolean active;
	private long completedSamples, skippedSamples, lastNanos, submittedSamples;

	OpenGLGpuTimer(OpenGLShadowBindings functions) throws Exception {
		this(functions, "gpu");
	}
	OpenGLGpuTimer(OpenGLShadowBindings functions, String label) throws Exception {
		this.functions = functions;
		this.label = label;
		if (functions != null && functions.timerSupported) {
			try {
				for (int i = 0; i < RING_SIZE; i++) {
					starts[i] = functions.genQuery(); ends[i] = functions.genQuery();
				}
			} catch (Exception error) { close(); throw error; }
		}
	}
	void begin() throws Exception {
		if (functions == null || !functions.timerSupported) return;
		if (active) throw new IllegalStateException("GPU timer already active");
		for (int i = 0; i < RING_SIZE; i++) {
			if (pending[i] && functions.available(ends[i])) {
				long finish = functions.result(ends[i]);
				long start = functions.result(starts[i]);
				lastNanos = Math.max(0, finish - start); completedSamples++; pending[i] = false;
				SampleSink sink = diagnosticSink;
				if (sink != null) sink.sample(label, sequences[i], lastNanos);
			}
		}
		if (pending[cursor]) { skippedSamples++; return; }
		functions.timestamp(starts[cursor]); sequences[cursor] = ++submittedSamples; active = true;
	}
	void end() throws Exception {
		if (!active) return;
		functions.timestamp(ends[cursor]); pending[cursor] = true;
		cursor = (cursor + 1) % RING_SIZE; active = false;
	}
	long lastNanos() { return lastNanos; }
	long samples() { return completedSamples; }
	long skipped() { return skippedSamples; }
	public void close() throws Exception {
		if (functions == null) return;
		for (int i = 0; i < RING_SIZE; i++) {
			if (starts[i] != 0) { functions.deleteQuery(starts[i]); starts[i] = 0; }
			if (ends[i] != 0) { functions.deleteQuery(ends[i]); ends[i] = 0; }
			pending[i] = false;
		}
		active = false;
	}
}
