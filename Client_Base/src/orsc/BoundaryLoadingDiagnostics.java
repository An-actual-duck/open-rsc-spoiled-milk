package orsc;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bounded, opt-in correlation for one world-boundary transition.
 *
 * <p>The ordinary renderer telemetry is intentionally broad. This trace is
 * narrower: it retains a fixed number of phase spans and frame samples in
 * memory, then emits one structured record after presentation has settled.
 * No account, chat, credential, or network-address data is retained.</p>
 */
final class BoundaryLoadingDiagnostics {
	private static final String ENABLED_PROPERTY =
		"spoiledmilk.boundaryDiagnostics";
	private static final String ENABLED_ENV =
		"SPOILED_MILK_BOUNDARY_DIAGNOSTICS";
	private static final String MAX_TRANSITIONS_PROPERTY =
		"spoiledmilk.boundaryDiagnostics.maxTransitions";
	private static final String MAX_SPANS_PROPERTY =
		"spoiledmilk.boundaryDiagnostics.maxSpans";
	private static final String POST_FRAMES_PROPERTY =
		"spoiledmilk.boundaryDiagnostics.postFrames";

	static final int DEFAULT_MAX_TRANSITIONS = 256;
	static final int DEFAULT_MAX_SPANS = 192;
	static final int DEFAULT_POST_RELEASE_FRAMES = 16;
	static final int MAX_PHASE_KEYS = 64;
	static final int MAX_OPENGL_FRAMES = 96;
	static final int MAX_CLIENT_LOOP_FRAMES = 96;
	static final int MAX_PRESENTATION_FRAMES = 96;
	static final int RECENT_OPENGL_FRAMES = 8;
	static final int RECENT_CLIENT_LOOP_FRAMES = 8;
	static final int MAX_VISITED_CENTERS = 64;
	static final int MAX_PREDICTIONS = 32;

	private static final boolean ENABLED =
		RendererDiagnosticSession.isEnabled()
			&& readBoolean(ENABLED_PROPERTY, ENABLED_ENV);
	private static final int MAX_TRANSITIONS =
		boundedInt(MAX_TRANSITIONS_PROPERTY, DEFAULT_MAX_TRANSITIONS, 1, 1024);
	private static final int MAX_SPANS =
		boundedInt(MAX_SPANS_PROPERTY, DEFAULT_MAX_SPANS, 8, 512);
	private static final int POST_RELEASE_FRAMES =
		boundedInt(
			POST_FRAMES_PROPERTY,
			DEFAULT_POST_RELEASE_FRAMES,
			1,
			64);

	private static final OpenGLFrameSample[] RECENT_FRAMES =
		createRecentFrames();
	private static final ClientLoopSample[] RECENT_CLIENT_LOOPS =
		createRecentClientLoops();
	private static final Map<String, Integer> VISITED_CENTERS =
		ENABLED
			? new LinkedHashMap<String, Integer>(
				MAX_VISITED_CENTERS,
				0.75F,
				true) {
				@Override
				protected boolean removeEldestEntry(
					Map.Entry<String, Integer> eldest) {
					return size() > MAX_VISITED_CENTERS;
				}
			}
			: null;
	private static final Map<String, PredictionSample> PREDICTIONS =
		ENABLED
			? new LinkedHashMap<String, PredictionSample>(
				MAX_PREDICTIONS,
				0.75F,
				true) {
				@Override
				protected boolean removeEldestEntry(
					Map.Entry<String, PredictionSample> eldest) {
					return size() > MAX_PREDICTIONS;
				}
			}
			: null;

	private static ThreadMXBean threadBean;
	private static com.sun.management.ThreadMXBean allocationBean;
	private static com.sun.management.OperatingSystemMXBean operatingSystemBean;
	private static List<GarbageCollectorMXBean> garbageCollectors;
	private static boolean runtimeInitialized;

	private static Trace activeTrace;
	private static final OpenGLScratch OPENGL_SCRATCH =
		ENABLED ? new OpenGLScratch() : null;
	private static long nextTraceId = 1L;
	private static int startedTransitions;
	private static long suppressedTransitions;
	private static int recentFrameWriteIndex;
	private static int recentFrameCount;
	private static int recentClientLoopWriteIndex;
	private static int recentClientLoopCount;

	private BoundaryLoadingDiagnostics() {
	}

	static boolean isEnabled() {
		return ENABLED;
	}

	static void initialize() {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			initializeRuntime();
		}
	}

	static long now() {
		return ENABLED ? System.nanoTime() : 0L;
	}

	static void beginContextTransition(
		int protocolVersion,
		int contextSequence,
		int serverTick,
		int logicalX,
		int logicalY,
		int logicalLevel,
		long startedNanos) {
		if (!ENABLED) {
			return;
		}
		CompletedTrace superseded;
		synchronized (BoundaryLoadingDiagnostics.class) {
			initializeRuntime();
			if (startedTransitions >= MAX_TRANSITIONS) {
				suppressedTransitions++;
				superseded = finishActiveLocked(
					"superseded",
					System.nanoTime());
			} else {
				superseded = finishActiveLocked(
					"superseded",
					System.nanoTime());
				long safeStart =
					startedNanos > 0L
						? startedNanos : System.nanoTime();
				activeTrace = new Trace(
					nextTraceId++,
					protocolVersion,
					contextSequence,
					serverTick,
					logicalX,
					logicalY,
					logicalLevel,
					safeStart,
					RuntimeSnapshot.capture());
				startedTransitions++;
				copyRecentFrames(activeTrace);
				copyRecentClientLoops(activeTrace);
			}
		}
		writeCompleted(superseded);
	}

	static void updateDestination(
		int logicalLevel,
		int centerX,
		int centerY,
		boolean scopeChanged,
		boolean predictedPublished) {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			Trace trace = activeTrace;
			if (trace == null) {
				return;
			}
			trace.logicalLevel = logicalLevel;
			trace.centerX = centerX;
			trace.centerY = centerY;
			trace.scopeChanged = scopeChanged;
			trace.predictedPublished = predictedPublished;
			String key = centerKey(logicalLevel, centerX, centerY);
			Integer prior = VISITED_CENTERS.get(key);
			trace.priorVisits = prior == null ? 0 : prior.intValue();
			VISITED_CENTERS.put(
				key,
				Integer.valueOf(trace.priorVisits + 1));
			PredictionSample prediction = PREDICTIONS.remove(key);
			if (prediction != null) {
				trace.prediction = prediction;
			}
		}
	}

	static void recordPhase(
		String owner,
		String phase,
		long startedNanos,
		long durationNanos) {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			if (activeTrace != null) {
				activeTrace.recordPhase(
					phaseKey(owner, phase),
					startedNanos,
					durationNanos,
					threadKind());
			}
		}
	}

	static void recordPacket(
		int opcode,
		int bytes,
		long startedNanos,
		long durationNanos) {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			if (activeTrace == null) {
				return;
			}
			activeTrace.packetCount++;
			activeTrace.packetBytes += Math.max(0, bytes);
			activeTrace.packetNanos += Math.max(0L, durationNanos);
			activeTrace.recordPhase(
				"packet.opcode-" + opcode,
				startedNanos,
				durationNanos,
				threadKind());
		}
	}

	static void recordRegionTransition(
		boolean hardAreaLoad,
		boolean planeChanged,
		int baseDeltaX,
		int baseDeltaZ,
		int sceneryCount,
		int wallCount,
		int playerCount,
		int npcCount) {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			if (activeTrace == null) {
				return;
			}
			activeTrace.regionObserved = true;
			activeTrace.hardAreaLoad = hardAreaLoad;
			activeTrace.planeChanged = planeChanged;
			activeTrace.baseDeltaX = baseDeltaX;
			activeTrace.baseDeltaZ = baseDeltaZ;
			activeTrace.sceneryCount = Math.max(0, sceneryCount);
			activeTrace.wallCount = Math.max(0, wallCount);
			activeTrace.playerCount = Math.max(0, playerCount);
			activeTrace.npcCount = Math.max(0, npcCount);
		}
	}

	static void recordStaticPresentationBuild(
		int inputs,
		int cacheHits,
		int cacheMisses,
		long inputNanos,
		long meshNanos,
		long meshCpuNanos,
		boolean parallel,
		int workers) {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			if (activeTrace == null) {
				return;
			}
			activeTrace.staticInputs = Math.max(0, inputs);
			activeTrace.staticCacheHits = Math.max(0, cacheHits);
			activeTrace.staticCacheMisses = Math.max(0, cacheMisses);
			activeTrace.staticMeshCpuNanos =
				Math.max(0L, meshCpuNanos);
			activeTrace.staticParallel = parallel;
			activeTrace.staticWorkers = Math.max(0, workers);
			activeTrace.recordPhase(
				"scenery.input",
				System.nanoTime() - Math.max(0L, inputNanos),
				inputNanos,
				threadKind());
			activeTrace.recordPhase(
				"scenery.mesh",
				System.nanoTime() - Math.max(0L, meshNanos),
				meshNanos,
				threadKind());
		}
	}

	static void recordDiskRead(
		String source,
		long bytes,
		long startedNanos,
		long durationNanos) {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			if (activeTrace == null) {
				return;
			}
			activeTrace.diskReads++;
			activeTrace.diskBytes += Math.max(0L, bytes);
			activeTrace.diskNanos += Math.max(0L, durationNanos);
			activeTrace.recordPhase(
				"disk." + safeKey(source),
				startedNanos,
				durationNanos,
				threadKind());
		}
	}

	static void recordLockWait(
		String lock,
		long startedNanos,
		long durationNanos) {
		if (!ENABLED || durationNanos <= 0L) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			if (activeTrace == null) {
				return;
			}
			activeTrace.lockWaitCount++;
			activeTrace.lockWaitNanos += durationNanos;
			activeTrace.recordPhase(
				"lock." + safeKey(lock),
				startedNanos,
				durationNanos,
				threadKind());
		}
	}

	static void recordPrediction(
		int logicalLevel,
		int centerX,
		int centerY,
		int protocolVersion,
		long decodeNanos,
		long buildNanos,
		long queuedToReadyNanos,
		boolean productCacheHit,
		int triangleCount,
		int reusedChunks,
		int builtChunks) {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			PREDICTIONS.put(
				centerKey(logicalLevel, centerX, centerY),
				new PredictionSample(
					protocolVersion,
					decodeNanos,
					buildNanos,
					queuedToReadyNanos,
					productCacheHit,
					triangleCount,
					reusedChunks,
					builtChunks,
					System.nanoTime()));
		}
	}

	static void recordOpenGLWorldFrame(
		int chunkCount,
		int triangleCount,
		long uploadedBytes,
		int requestedChunks,
		int uploadedChunks,
		int reusedChunks,
		int deferredChunks,
		long chunkUploadNanos,
		long projectedDrawNanos,
		long residentDrawNanos,
		boolean shadowPrepared,
		boolean shadowRequested) {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			OpenGLScratch scratch = openGLScratch();
			scratch.chunkCount = Math.max(0, chunkCount);
			scratch.triangleCount = Math.max(0, triangleCount);
			scratch.uploadedBytes = Math.max(0L, uploadedBytes);
			scratch.requestedChunks = Math.max(0, requestedChunks);
			scratch.uploadedChunks = Math.max(0, uploadedChunks);
			scratch.reusedChunks = Math.max(0, reusedChunks);
			scratch.deferredChunks = Math.max(0, deferredChunks);
			scratch.chunkUploadNanos = Math.max(0L, chunkUploadNanos);
			scratch.projectedDrawNanos = Math.max(0L, projectedDrawNanos);
			scratch.residentDrawNanos = Math.max(0L, residentDrawNanos);
			scratch.shadowPrepared = shadowPrepared;
			scratch.shadowRequested = shadowRequested;
		}
	}

	static void recordOpenGLShadow(
		long buildNanos,
		long uploadNanos,
		boolean reused) {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			OpenGLScratch scratch = openGLScratch();
			scratch.shadowBuildNanos += Math.max(0L, buildNanos);
			scratch.shadowUploadNanos += Math.max(0L, uploadNanos);
			scratch.shadowReused &= reused;
		}
	}

	static void recordOpenGLPhases(
		long baseNanos,
		long worldNanos,
		long worldSpriteNanos,
		long spriteOverlayNanos,
		long debugOverlayNanos,
		long swapNanos) {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			OpenGLScratch scratch = openGLScratch();
			scratch.baseNanos = Math.max(0L, baseNanos);
			scratch.worldNanos = Math.max(0L, worldNanos);
			scratch.worldSpriteNanos = Math.max(0L, worldSpriteNanos);
			scratch.spriteOverlayNanos = Math.max(0L, spriteOverlayNanos);
			scratch.debugOverlayNanos = Math.max(0L, debugOverlayNanos);
			scratch.swapNanos = Math.max(0L, swapNanos);
		}
	}

	static void recordOpenGLFrame(
		long sequence,
		long uploadNanos,
		long renderNanos,
		long intervalNanos) {
		if (!ENABLED) {
			return;
		}
		CompletedTrace completed = null;
		synchronized (BoundaryLoadingDiagnostics.class) {
			OpenGLScratch scratch = OPENGL_SCRATCH;
			OpenGLFrameSample sample =
				RECENT_FRAMES[recentFrameWriteIndex];
			scratch.writeSample(
				sample,
				sequence,
				System.nanoTime(),
				uploadNanos,
				renderNanos,
				intervalNanos);
			scratch.reset();
			rememberRecentFrame();
			if (activeTrace != null) {
				activeTrace.recordOpenGLFrame(sample.copy());
				completed = finishAfterSettledFrameLocked(sample.observedNanos);
			}
		}
		writeCompleted(completed);
	}

	static void recordPresentationFrame(
		long sequence,
		long totalNanos,
		long commitNanos,
		long presentNanos) {
		if (!ENABLED) {
			return;
		}
		CompletedTrace completed = null;
		synchronized (BoundaryLoadingDiagnostics.class) {
			if (activeTrace != null) {
				activeTrace.recordPresentationFrame(
					sequence,
					System.nanoTime(),
					totalNanos,
					commitNanos,
					presentNanos);
				if (activeTrace.openGLFrameCount == 0) {
					completed =
						finishAfterSettledFrameLocked(System.nanoTime());
				}
			}
		}
		writeCompleted(completed);
	}

	static void recordClientLoop(
		long sequence,
		long loopNanos,
		long sleepNanos,
		long updateNanos,
		long repositionNanos,
		long drawNanos,
		int updateCount,
		int sleepRequestMillis,
		int stepSize,
		boolean skippedDraw) {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			ClientLoopSample sample =
				RECENT_CLIENT_LOOPS[recentClientLoopWriteIndex];
			sample.set(
				sequence,
				System.nanoTime(),
				loopNanos,
				sleepNanos,
				updateNanos,
				repositionNanos,
				drawNanos,
				updateCount,
				sleepRequestMillis,
				stepSize,
				skippedDraw);
			rememberRecentClientLoop();
			if (activeTrace != null) {
				activeTrace.recordClientLoop(sample);
			}
		}
	}

	static void recordPresentationRetention() {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			if (activeTrace != null) {
				activeTrace.recordPresentationRetention(
					System.nanoTime());
			}
		}
	}

	static void recordPresentationProductsReady(boolean ready) {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			if (activeTrace == null) {
				return;
			}
			long now = System.nanoTime();
			if (ready) {
				if (activeTrace.presentationProductsReadyNanos == 0L) {
					activeTrace.presentationProductsReadyNanos = now;
				}
			} else if (activeTrace.presentationProductsWaitNanos == 0L) {
				activeTrace.presentationProductsWaitNanos = now;
			}
		}
	}

	static void recordOpenGLPresenterWait(
		long startedNanos,
		long durationNanos,
		boolean acquiredFrame,
		boolean waited) {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			if (activeTrace == null) {
				return;
			}
			if (acquiredFrame) {
				activeTrace.openGLPresenterAcquiredFrames++;
			}
			if (waited) {
				activeTrace.openGLPresenterWaitCount++;
				activeTrace.openGLPresenterWaitNanos +=
					Math.max(0L, durationNanos);
				activeTrace.openGLPresenterWaitMaxNanos =
					Math.max(
						activeTrace.openGLPresenterWaitMaxNanos,
						Math.max(0L, durationNanos));
				if (activeTrace.openGLPresenterWaitFirstNanos == 0L) {
					activeTrace.openGLPresenterWaitFirstNanos =
						startedNanos > 0L
							? startedNanos : System.nanoTime();
				}
			}
		}
	}

	static void recordOpenGLFrameDequeued(long submittedNanos) {
		if (!ENABLED || submittedNanos <= 0L) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			if (activeTrace == null) {
				return;
			}
			long queueNanos =
				Math.max(0L, System.nanoTime() - submittedNanos);
			activeTrace.openGLQueueSamples++;
			activeTrace.openGLQueueNanos += queueNanos;
			activeTrace.openGLQueueMaxNanos =
				Math.max(activeTrace.openGLQueueMaxNanos, queueNanos);
		}
	}

	static void recordAtomicActivationProgress(
		boolean completed,
		boolean playerReceipt,
		boolean staticBaseline,
		long elapsedMillis) {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			if (activeTrace == null) {
				return;
			}
			long now = System.nanoTime();
			if (playerReceipt && !activeTrace.atomicPlayerReceipt) {
				activeTrace.atomicPlayerReceiptNanos = now;
			}
			if (staticBaseline && !activeTrace.atomicStaticBaseline) {
				activeTrace.atomicStaticBaselineNanos = now;
			}
			activeTrace.atomicPlayerReceipt |= playerReceipt;
			activeTrace.atomicStaticBaseline |= staticBaseline;
			activeTrace.atomicElapsedNanos =
				Math.max(
					activeTrace.atomicElapsedNanos,
					Math.max(0L, elapsedMillis) * 1_000_000L);
			if (completed) {
				activeTrace.atomicCompletedNanos = now;
			}
		}
	}

	static void recordPresentationRelease(
		int samples,
		boolean stable,
		int chunks,
		int triangles) {
		if (!ENABLED) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			if (activeTrace == null) {
				return;
			}
			activeTrace.presentationReleaseNanos = System.nanoTime();
			activeTrace.presentationSamples = Math.max(0, samples);
			activeTrace.presentationStable = stable;
			activeTrace.releaseChunkCount = Math.max(0, chunks);
			activeTrace.releaseTriangleCount = Math.max(0, triangles);
		}
	}

	static void appendCorrelation(RendererDiagnosticSession.Record record) {
		if (!ENABLED || record == null) {
			return;
		}
		synchronized (BoundaryLoadingDiagnostics.class) {
			if (activeTrace != null) {
				record.number("boundary.traceId", activeTrace.traceId);
				record.number(
					"boundary.contextSequence",
					activeTrace.contextSequence);
			}
		}
	}

	static void flushOnSessionClose() {
		if (!ENABLED) {
			return;
		}
		CompletedTrace completed;
		synchronized (BoundaryLoadingDiagnostics.class) {
			completed =
				finishActiveLocked("session-close", System.nanoTime());
		}
		writeCompleted(completed);
	}

	private static CompletedTrace finishAfterSettledFrameLocked(long nowNanos) {
		if (activeTrace == null
			|| activeTrace.presentationReleaseNanos == 0L) {
			return null;
		}
		int postFrames =
			activeTrace.openGLFrameCount > 0
				? activeTrace.openGLFramesAfterRelease
				: activeTrace.presentationFramesAfterRelease;
		return postFrames >= POST_RELEASE_FRAMES
			? finishActiveLocked("settled", nowNanos)
			: null;
	}

	private static CompletedTrace finishActiveLocked(
		String reason,
		long finishedNanos) {
		Trace trace = activeTrace;
		if (trace == null) {
			return null;
		}
		activeTrace = null;
		return new CompletedTrace(
			trace,
			reason,
			finishedNanos,
			RuntimeSnapshot.capture(),
			suppressedTransitions);
	}

	private static void writeCompleted(CompletedTrace completed) {
		if (completed == null) {
			return;
		}
		RendererDiagnosticSession.Record event =
			RendererDiagnosticSession.newEventRecord(
				"boundary.transition-summary");
		if (event == null) {
			return;
		}
		completed.appendTo(event);
		RendererDiagnosticSession.writeEventRecord(event);
	}

	private static void copyRecentFrames(Trace trace) {
		int count = Math.min(recentFrameCount, RECENT_OPENGL_FRAMES);
		int start =
			Math.floorMod(
				recentFrameWriteIndex - count,
				RECENT_OPENGL_FRAMES);
		for (int index = 0; index < count; index++) {
			OpenGLFrameSample sample =
				RECENT_FRAMES[
					(start + index) % RECENT_OPENGL_FRAMES];
			if (sample != null) {
				trace.recordOpenGLFrame(sample.copy());
			}
		}
	}

	private static void copyRecentClientLoops(Trace trace) {
		int count = Math.min(
			recentClientLoopCount,
			RECENT_CLIENT_LOOP_FRAMES);
		int start =
			Math.floorMod(
				recentClientLoopWriteIndex - count,
				RECENT_CLIENT_LOOP_FRAMES);
		for (int index = 0; index < count; index++) {
			ClientLoopSample sample =
				RECENT_CLIENT_LOOPS[
					(start + index) % RECENT_CLIENT_LOOP_FRAMES];
			if (sample != null) {
				trace.recordClientLoop(sample);
			}
		}
	}

	private static void rememberRecentFrame() {
		recentFrameWriteIndex =
			(recentFrameWriteIndex + 1) % RECENT_OPENGL_FRAMES;
		recentFrameCount =
			Math.min(RECENT_OPENGL_FRAMES, recentFrameCount + 1);
	}

	private static void rememberRecentClientLoop() {
		recentClientLoopWriteIndex =
			(recentClientLoopWriteIndex + 1)
				% RECENT_CLIENT_LOOP_FRAMES;
		recentClientLoopCount =
			Math.min(
				RECENT_CLIENT_LOOP_FRAMES,
				recentClientLoopCount + 1);
	}

	private static OpenGLScratch openGLScratch() {
		return OPENGL_SCRATCH;
	}

	private static OpenGLFrameSample[] createRecentFrames() {
		if (!ENABLED) {
			return null;
		}
		OpenGLFrameSample[] samples =
			new OpenGLFrameSample[RECENT_OPENGL_FRAMES];
		for (int index = 0; index < samples.length; index++) {
			samples[index] = new OpenGLFrameSample();
		}
		return samples;
	}

	private static ClientLoopSample[] createRecentClientLoops() {
		if (!ENABLED) {
			return null;
		}
		ClientLoopSample[] samples =
			new ClientLoopSample[RECENT_CLIENT_LOOP_FRAMES];
		for (int index = 0; index < samples.length; index++) {
			samples[index] = new ClientLoopSample();
		}
		return samples;
	}

	private static void initializeRuntime() {
		if (runtimeInitialized) {
			return;
		}
		threadBean = ManagementFactory.getThreadMXBean();
		if (threadBean.isThreadCpuTimeSupported()
			&& !threadBean.isThreadCpuTimeEnabled()) {
			try {
				threadBean.setThreadCpuTimeEnabled(true);
			} catch (RuntimeException ignored) {
			}
		}
		if (threadBean.isThreadContentionMonitoringSupported()
			&& !threadBean.isThreadContentionMonitoringEnabled()) {
			try {
				threadBean.setThreadContentionMonitoringEnabled(true);
			} catch (RuntimeException ignored) {
			}
		}
		if (threadBean instanceof com.sun.management.ThreadMXBean) {
			allocationBean =
				(com.sun.management.ThreadMXBean) threadBean;
			if (allocationBean.isThreadAllocatedMemorySupported()
				&& !allocationBean.isThreadAllocatedMemoryEnabled()) {
				try {
					allocationBean.setThreadAllocatedMemoryEnabled(true);
				} catch (RuntimeException ignored) {
				}
			}
		}
		java.lang.management.OperatingSystemMXBean os =
			ManagementFactory.getOperatingSystemMXBean();
		if (os instanceof com.sun.management.OperatingSystemMXBean) {
			operatingSystemBean =
				(com.sun.management.OperatingSystemMXBean) os;
		}
		garbageCollectors =
			new ArrayList<GarbageCollectorMXBean>(
				ManagementFactory.getGarbageCollectorMXBeans());
		/*
		 * Prewarm management-bean paths at diagnostic startup, not on the
		 * first measured boundary.
		 */
		RuntimeSnapshot.capture();
		runtimeInitialized = true;
	}

	private static String centerKey(
		int logicalLevel,
		int centerX,
		int centerY) {
		return logicalLevel + ":" + centerX + ":" + centerY;
	}

	private static String phaseKey(String owner, String phase) {
		return safeKey(owner) + "." + safeKey(phase);
	}

	private static String safeKey(String value) {
		if (value == null || value.isEmpty()) {
			return "unknown";
		}
		String normalized = value.toLowerCase(Locale.ROOT);
		StringBuilder safe = new StringBuilder(48);
		for (int index = 0;
			index < normalized.length() && safe.length() < 48;
			index++) {
			char ch = normalized.charAt(index);
			if (Character.isLetterOrDigit(ch)
				|| ch == '.'
				|| ch == '-'
				|| ch == '_') {
				safe.append(ch);
			} else if (safe.length() > 0
				&& safe.charAt(safe.length() - 1) != '-') {
				safe.append('-');
			}
		}
		return safe.length() == 0 ? "unknown" : safe.toString();
	}

	private static String threadKind() {
		String name = Thread.currentThread().getName();
		if ("Spoiled Milk Client Loop".equals(name)) {
			return "client";
		}
		if ("Spoiled Milk OpenGL Presenter".equals(name)) {
			return "opengl";
		}
		if ("world-sector-preload".equals(name)) {
			return "preload";
		}
		if (name != null && name.startsWith("resident-object-build-")) {
			return "object-build";
		}
		if (name != null && name.startsWith("AWT-EventQueue")) {
			return "awt";
		}
		return "other";
	}

	private static boolean readBoolean(
		String property,
		String environment) {
		String value = System.getProperty(property);
		if (value == null || value.trim().isEmpty()) {
			value = System.getenv(environment);
		}
		return value != null
			&& Boolean.parseBoolean(value.trim());
	}

	private static int boundedInt(
		String property,
		int fallback,
		int minimum,
		int maximum) {
		String value = System.getProperty(property);
		if (value == null || value.trim().isEmpty()) {
			return fallback;
		}
		try {
			return Math.max(
				minimum,
				Math.min(maximum, Integer.parseInt(value.trim())));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static long delta(long current, long previous) {
		return current < 0L || previous < 0L
			? -1L
			: Math.max(0L, current - previous);
	}

	private static long sum(long[] values, int count) {
		long total = 0L;
		for (int index = 0; index < count; index++) {
			total += Math.max(0L, values[index]);
		}
		return total;
	}

	private static long percentile(
		long[] values,
		int count,
		double percentile) {
		if (count <= 0) {
			return 0L;
		}
		long[] sorted = Arrays.copyOf(values, count);
		Arrays.sort(sorted);
		int index =
			Math.max(
				0,
				Math.min(
					count - 1,
					(int) Math.ceil(count * percentile) - 1));
		return sorted[index];
	}

	private static String direction(Trace trace) {
		if (trace.planeChanged) {
			return "level";
		}
		int absX = Math.abs(trace.baseDeltaX);
		int absZ = Math.abs(trace.baseDeltaZ);
		if (absX == 0 && absZ == 0) {
			return "none";
		}
		if (absX > 48 || absZ > 48) {
			return "relocation";
		}
		return absX > 0 && absZ > 0 ? "diagonal" : "cardinal";
	}

	private static final class Trace {
		private final long traceId;
		private final int protocolVersion;
		private final int contextSequence;
		private final int serverTick;
		private final int logicalX;
		private final int logicalY;
		private int logicalLevel;
		private final long startedNanos;
		private final RuntimeSnapshot runtimeStart;

		private int centerX = Integer.MIN_VALUE;
		private int centerY = Integer.MIN_VALUE;
		private boolean scopeChanged;
		private boolean predictedPublished;
		private int priorVisits;
		private PredictionSample prediction;

		private final String[] phaseNames =
			new String[MAX_PHASE_KEYS];
		private final long[] phaseTotals =
			new long[MAX_PHASE_KEYS];
		private final long[] phaseMax =
			new long[MAX_PHASE_KEYS];
		private final long[] phaseCounts =
			new long[MAX_PHASE_KEYS];
		private int phaseKeyCount;
		private final String[] spanNames =
			new String[MAX_SPANS];
		private final String[] spanThreads =
			new String[MAX_SPANS];
		private final long[] spanOffsets =
			new long[MAX_SPANS];
		private final long[] spanDurations =
			new long[MAX_SPANS];
		private int spanCount;
		private long droppedSpans;

		private final OpenGLFrameSample[] openGLFrames =
			new OpenGLFrameSample[MAX_OPENGL_FRAMES];
		private int openGLFrameCount;
		private long droppedOpenGLFrames;
		private int openGLFramesAfterRelease;
		private final long[] clientLoopSequences =
			new long[MAX_CLIENT_LOOP_FRAMES];
		private final long[] clientLoopOffsets =
			new long[MAX_CLIENT_LOOP_FRAMES];
		private final long[] clientLoopTotal =
			new long[MAX_CLIENT_LOOP_FRAMES];
		private final long[] clientLoopSleep =
			new long[MAX_CLIENT_LOOP_FRAMES];
		private final long[] clientLoopUpdate =
			new long[MAX_CLIENT_LOOP_FRAMES];
		private final long[] clientLoopReposition =
			new long[MAX_CLIENT_LOOP_FRAMES];
		private final long[] clientLoopDraw =
			new long[MAX_CLIENT_LOOP_FRAMES];
		private final long[] clientLoopUpdateCount =
			new long[MAX_CLIENT_LOOP_FRAMES];
		private final long[] clientLoopSleepRequestMillis =
			new long[MAX_CLIENT_LOOP_FRAMES];
		private final long[] clientLoopStepSize =
			new long[MAX_CLIENT_LOOP_FRAMES];
		private final long[] clientLoopSkippedDraw =
			new long[MAX_CLIENT_LOOP_FRAMES];
		private int clientLoopFrameCount;
		private long droppedClientLoopFrames;
		private final long[] presentationSequences =
			new long[MAX_PRESENTATION_FRAMES];
		private final long[] presentationOffsets =
			new long[MAX_PRESENTATION_FRAMES];
		private final long[] presentationTotal =
			new long[MAX_PRESENTATION_FRAMES];
		private final long[] presentationCommit =
			new long[MAX_PRESENTATION_FRAMES];
		private final long[] presentationPresent =
			new long[MAX_PRESENTATION_FRAMES];
		private int presentationFrameCount;
		private long droppedPresentationFrames;
		private int presentationFramesAfterRelease;

		private long packetCount;
		private long packetBytes;
		private long packetNanos;
		private int diskReads;
		private long diskBytes;
		private long diskNanos;
		private int lockWaitCount;
		private long lockWaitNanos;

		private boolean regionObserved;
		private boolean hardAreaLoad;
		private boolean planeChanged;
		private int baseDeltaX;
		private int baseDeltaZ;
		private int sceneryCount;
		private int wallCount;
		private int playerCount;
		private int npcCount;

		private int staticInputs;
		private int staticCacheHits;
		private int staticCacheMisses;
		private long staticMeshCpuNanos;
		private boolean staticParallel;
		private int staticWorkers;

		private boolean atomicPlayerReceipt;
		private boolean atomicStaticBaseline;
		private long atomicPlayerReceiptNanos;
		private long atomicStaticBaselineNanos;
		private long atomicElapsedNanos;
		private long atomicCompletedNanos;
		private long presentationProductsWaitNanos;
		private long presentationProductsReadyNanos;
		private int presentationRetentionCount;
		private long presentationRetentionFirstNanos;
		private long presentationRetentionLastNanos;
		private long presentationReleaseNanos;
		private int presentationSamples;
		private boolean presentationStable;
		private int releaseChunkCount;
		private int releaseTriangleCount;
		private int openGLPresenterWaitCount;
		private long openGLPresenterWaitNanos;
		private long openGLPresenterWaitMaxNanos;
		private long openGLPresenterWaitFirstNanos;
		private int openGLPresenterAcquiredFrames;
		private int openGLQueueSamples;
		private long openGLQueueNanos;
		private long openGLQueueMaxNanos;

		private Trace(
			long traceId,
			int protocolVersion,
			int contextSequence,
			int serverTick,
			int logicalX,
			int logicalY,
			int logicalLevel,
			long startedNanos,
			RuntimeSnapshot runtimeStart) {
			this.traceId = traceId;
			this.protocolVersion = protocolVersion;
			this.contextSequence = contextSequence;
			this.serverTick = serverTick;
			this.logicalX = logicalX;
			this.logicalY = logicalY;
			this.logicalLevel = logicalLevel;
			this.startedNanos = startedNanos;
			this.runtimeStart = runtimeStart;
		}

		private void recordPhase(
			String name,
			long phaseStartedNanos,
			long durationNanos,
			String thread) {
			long safeDuration = Math.max(0L, durationNanos);
			int keyIndex = -1;
			for (int index = 0; index < phaseKeyCount; index++) {
				if (phaseNames[index].equals(name)) {
					keyIndex = index;
					break;
				}
			}
			if (keyIndex < 0 && phaseKeyCount < MAX_PHASE_KEYS) {
				keyIndex = phaseKeyCount++;
				phaseNames[keyIndex] = name;
			}
			if (keyIndex >= 0) {
				phaseTotals[keyIndex] += safeDuration;
				phaseMax[keyIndex] =
					Math.max(phaseMax[keyIndex], safeDuration);
				phaseCounts[keyIndex]++;
			}
			if (spanCount >= spanNames.length) {
				droppedSpans++;
				return;
			}
			spanNames[spanCount] = name;
			spanThreads[spanCount] = thread;
			spanOffsets[spanCount] =
				phaseStartedNanos <= 0L
					? 0L
					: phaseStartedNanos - startedNanos;
			spanDurations[spanCount] = safeDuration;
			spanCount++;
		}

		private void recordOpenGLFrame(OpenGLFrameSample sample) {
			if (openGLFrameCount >= openGLFrames.length) {
				droppedOpenGLFrames++;
				return;
			}
			openGLFrames[openGLFrameCount++] = sample;
			if (presentationReleaseNanos > 0L
				&& sample.observedNanos >= presentationReleaseNanos) {
				openGLFramesAfterRelease++;
			}
		}

		private void recordClientLoop(ClientLoopSample sample) {
			if (clientLoopFrameCount >= clientLoopSequences.length) {
				droppedClientLoopFrames++;
				return;
			}
			int index = clientLoopFrameCount++;
			clientLoopSequences[index] = sample.sequence;
			clientLoopOffsets[index] =
				sample.observedNanos - startedNanos;
			clientLoopTotal[index] = sample.loopNanos;
			clientLoopSleep[index] = sample.sleepNanos;
			clientLoopUpdate[index] = sample.updateNanos;
			clientLoopReposition[index] = sample.repositionNanos;
			clientLoopDraw[index] = sample.drawNanos;
			clientLoopUpdateCount[index] = sample.updateCount;
			clientLoopSleepRequestMillis[index] =
				sample.sleepRequestMillis;
			clientLoopStepSize[index] = sample.stepSize;
			clientLoopSkippedDraw[index] =
				sample.skippedDraw ? 1L : 0L;
		}

		private void recordPresentationRetention(long observedNanos) {
			presentationRetentionCount++;
			if (presentationRetentionFirstNanos == 0L) {
				presentationRetentionFirstNanos = observedNanos;
			}
			presentationRetentionLastNanos = observedNanos;
		}

		private void recordPresentationFrame(
			long sequence,
			long observedNanos,
			long totalNanos,
			long commitNanos,
			long presentNanos) {
			if (presentationFrameCount
					>= presentationSequences.length) {
				droppedPresentationFrames++;
				return;
			}
			int index = presentationFrameCount++;
			presentationSequences[index] = sequence;
			presentationOffsets[index] =
				observedNanos - startedNanos;
			presentationTotal[index] = Math.max(0L, totalNanos);
			presentationCommit[index] = Math.max(0L, commitNanos);
			presentationPresent[index] = Math.max(0L, presentNanos);
			if (presentationReleaseNanos > 0L
				&& observedNanos >= presentationReleaseNanos) {
				presentationFramesAfterRelease++;
			}
		}
	}

	private static final class PredictionSample {
		private final int protocolVersion;
		private final long decodeNanos;
		private final long buildNanos;
		private final long queuedToReadyNanos;
		private final boolean productCacheHit;
		private final int triangleCount;
		private final int reusedChunks;
		private final int builtChunks;
		private final long readyNanos;

		private PredictionSample(
			int protocolVersion,
			long decodeNanos,
			long buildNanos,
			long queuedToReadyNanos,
			boolean productCacheHit,
			int triangleCount,
			int reusedChunks,
			int builtChunks,
			long readyNanos) {
			this.protocolVersion = protocolVersion;
			this.decodeNanos = Math.max(0L, decodeNanos);
			this.buildNanos = Math.max(0L, buildNanos);
			this.queuedToReadyNanos = Math.max(0L, queuedToReadyNanos);
			this.productCacheHit = productCacheHit;
			this.triangleCount = Math.max(0, triangleCount);
			this.reusedChunks = Math.max(0, reusedChunks);
			this.builtChunks = Math.max(0, builtChunks);
			this.readyNanos = readyNanos;
		}
	}

	private static final class OpenGLScratch {
		private long baseNanos;
		private long worldNanos;
		private long worldSpriteNanos;
		private long spriteOverlayNanos;
		private long debugOverlayNanos;
		private long swapNanos;
		private int chunkCount;
		private int triangleCount;
		private long uploadedBytes;
		private int requestedChunks;
		private int uploadedChunks;
		private int reusedChunks;
		private int deferredChunks;
		private long chunkUploadNanos;
		private long projectedDrawNanos;
		private long residentDrawNanos;
		private long shadowBuildNanos;
		private long shadowUploadNanos;
		private boolean shadowReused = true;
		private boolean shadowPrepared;
		private boolean shadowRequested;

		private void writeSample(
			OpenGLFrameSample sample,
			long sequence,
			long observedNanos,
			long uploadNanos,
			long renderNanos,
			long intervalNanos) {
			sample.set(
				sequence, observedNanos, uploadNanos, renderNanos,
				intervalNanos, baseNanos, worldNanos,
				worldSpriteNanos, spriteOverlayNanos,
				debugOverlayNanos, swapNanos, chunkCount,
				triangleCount, uploadedBytes, requestedChunks,
				uploadedChunks, reusedChunks, deferredChunks,
				chunkUploadNanos, projectedDrawNanos,
				residentDrawNanos, shadowBuildNanos,
				shadowUploadNanos, shadowReused,
				shadowPrepared, shadowRequested);
		}

		private void reset() {
			baseNanos = 0L;
			worldNanos = 0L;
			worldSpriteNanos = 0L;
			spriteOverlayNanos = 0L;
			debugOverlayNanos = 0L;
			swapNanos = 0L;
			chunkCount = 0;
			triangleCount = 0;
			uploadedBytes = 0L;
			requestedChunks = 0;
			uploadedChunks = 0;
			reusedChunks = 0;
			deferredChunks = 0;
			chunkUploadNanos = 0L;
			projectedDrawNanos = 0L;
			residentDrawNanos = 0L;
			shadowBuildNanos = 0L;
			shadowUploadNanos = 0L;
			shadowReused = true;
			shadowPrepared = false;
			shadowRequested = false;
		}
	}

	private static final class OpenGLFrameSample {
		private long sequence;
		private long observedNanos;
		private long uploadNanos;
		private long renderNanos;
		private long intervalNanos;
		private long baseNanos;
		private long worldNanos;
		private long worldSpriteNanos;
		private long spriteOverlayNanos;
		private long debugOverlayNanos;
		private long swapNanos;
		private int chunkCount;
		private int triangleCount;
		private long uploadedBytes;
		private int requestedChunks;
		private int uploadedChunks;
		private int reusedChunks;
		private int deferredChunks;
		private long chunkUploadNanos;
		private long projectedDrawNanos;
		private long residentDrawNanos;
		private long shadowBuildNanos;
		private long shadowUploadNanos;
		private boolean shadowReused;
		private boolean shadowPrepared;
		private boolean shadowRequested;

		private OpenGLFrameSample() {
		}

		private void set(
			long sequence,
			long observedNanos,
			long uploadNanos,
			long renderNanos,
			long intervalNanos,
			long baseNanos,
			long worldNanos,
			long worldSpriteNanos,
			long spriteOverlayNanos,
			long debugOverlayNanos,
			long swapNanos,
			int chunkCount,
			int triangleCount,
			long uploadedBytes,
			int requestedChunks,
			int uploadedChunks,
			int reusedChunks,
			int deferredChunks,
			long chunkUploadNanos,
			long projectedDrawNanos,
			long residentDrawNanos,
			long shadowBuildNanos,
			long shadowUploadNanos,
			boolean shadowReused,
			boolean shadowPrepared,
			boolean shadowRequested) {
			this.sequence = sequence;
			this.observedNanos = observedNanos;
			this.uploadNanos = Math.max(0L, uploadNanos);
			this.renderNanos = Math.max(0L, renderNanos);
			this.intervalNanos = Math.max(0L, intervalNanos);
			this.baseNanos = Math.max(0L, baseNanos);
			this.worldNanos = Math.max(0L, worldNanos);
			this.worldSpriteNanos = Math.max(0L, worldSpriteNanos);
			this.spriteOverlayNanos = Math.max(0L, spriteOverlayNanos);
			this.debugOverlayNanos = Math.max(0L, debugOverlayNanos);
			this.swapNanos = Math.max(0L, swapNanos);
			this.chunkCount = Math.max(0, chunkCount);
			this.triangleCount = Math.max(0, triangleCount);
			this.uploadedBytes = Math.max(0L, uploadedBytes);
			this.requestedChunks = Math.max(0, requestedChunks);
			this.uploadedChunks = Math.max(0, uploadedChunks);
			this.reusedChunks = Math.max(0, reusedChunks);
			this.deferredChunks = Math.max(0, deferredChunks);
			this.chunkUploadNanos = Math.max(0L, chunkUploadNanos);
			this.projectedDrawNanos = Math.max(0L, projectedDrawNanos);
			this.residentDrawNanos = Math.max(0L, residentDrawNanos);
			this.shadowBuildNanos = Math.max(0L, shadowBuildNanos);
			this.shadowUploadNanos = Math.max(0L, shadowUploadNanos);
			this.shadowReused = shadowReused;
			this.shadowPrepared = shadowPrepared;
			this.shadowRequested = shadowRequested;
		}

		private OpenGLFrameSample copy() {
			OpenGLFrameSample copy = new OpenGLFrameSample();
			copy.set(
				sequence, observedNanos, uploadNanos, renderNanos,
				intervalNanos, baseNanos, worldNanos,
				worldSpriteNanos, spriteOverlayNanos,
				debugOverlayNanos, swapNanos, chunkCount,
				triangleCount, uploadedBytes, requestedChunks,
				uploadedChunks, reusedChunks, deferredChunks,
				chunkUploadNanos, projectedDrawNanos,
				residentDrawNanos, shadowBuildNanos,
				shadowUploadNanos, shadowReused,
				shadowPrepared, shadowRequested);
			return copy;
		}
	}

	private static final class ClientLoopSample {
		private long sequence;
		private long observedNanos;
		private long loopNanos;
		private long sleepNanos;
		private long updateNanos;
		private long repositionNanos;
		private long drawNanos;
		private int updateCount;
		private int sleepRequestMillis;
		private int stepSize;
		private boolean skippedDraw;

		private void set(
			long sequence,
			long observedNanos,
			long loopNanos,
			long sleepNanos,
			long updateNanos,
			long repositionNanos,
			long drawNanos,
			int updateCount,
			int sleepRequestMillis,
			int stepSize,
			boolean skippedDraw) {
			this.sequence = sequence;
			this.observedNanos = observedNanos;
			this.loopNanos = Math.max(0L, loopNanos);
			this.sleepNanos = Math.max(0L, sleepNanos);
			this.updateNanos = Math.max(0L, updateNanos);
			this.repositionNanos = Math.max(0L, repositionNanos);
			this.drawNanos = Math.max(0L, drawNanos);
			this.updateCount = Math.max(0, updateCount);
			this.sleepRequestMillis =
				Math.max(0, sleepRequestMillis);
			this.stepSize = Math.max(0, stepSize);
			this.skippedDraw = skippedDraw;
		}
	}

	private static final class RuntimeSnapshot {
		private final long capturedNanos;
		private final long captureDurationNanos;
		private final long heapUsedBytes;
		private final long gcCount;
		private final long gcTimeMillis;
		private final long processCpuNanos;
		private final ThreadGroupSample client;
		private final ThreadGroupSample openGL;
		private final ThreadGroupSample preload;
		private final ThreadGroupSample objectBuild;

		private RuntimeSnapshot(
			long capturedNanos,
			long captureDurationNanos,
			long heapUsedBytes,
			long gcCount,
			long gcTimeMillis,
			long processCpuNanos,
			ThreadGroupSample client,
			ThreadGroupSample openGL,
			ThreadGroupSample preload,
			ThreadGroupSample objectBuild) {
			this.capturedNanos = capturedNanos;
			this.captureDurationNanos = captureDurationNanos;
			this.heapUsedBytes = heapUsedBytes;
			this.gcCount = gcCount;
			this.gcTimeMillis = gcTimeMillis;
			this.processCpuNanos = processCpuNanos;
			this.client = client;
			this.openGL = openGL;
			this.preload = preload;
			this.objectBuild = objectBuild;
		}

		private static RuntimeSnapshot capture() {
			long captureStart = System.nanoTime();
			Runtime runtime = Runtime.getRuntime();
			long heapUsed =
				Math.max(
					0L,
					runtime.totalMemory() - runtime.freeMemory());
			long gcCount = 0L;
			long gcTime = 0L;
			if (garbageCollectors != null) {
				for (GarbageCollectorMXBean collector
						: garbageCollectors) {
					long count = collector.getCollectionCount();
					long time = collector.getCollectionTime();
					if (count >= 0L) {
						gcCount += count;
					}
					if (time >= 0L) {
						gcTime += time;
					}
				}
			}
			long processCpu =
				operatingSystemBean == null
					? -1L
					: operatingSystemBean.getProcessCpuTime();
			ThreadGroupSample client = new ThreadGroupSample();
			ThreadGroupSample openGL = new ThreadGroupSample();
			ThreadGroupSample preload = new ThreadGroupSample();
			ThreadGroupSample objectBuild = new ThreadGroupSample();
			if (threadBean != null) {
				long[] ids = threadBean.getAllThreadIds();
				ThreadInfo[] infos =
					threadBean.getThreadInfo(ids, 0);
				for (int index = 0; index < ids.length; index++) {
					ThreadInfo info = infos[index];
					if (info == null) {
						continue;
					}
					ThreadGroupSample target = null;
					String name = info.getThreadName();
					if ("Spoiled Milk Client Loop".equals(name)) {
						target = client;
					} else if ("Spoiled Milk OpenGL Presenter".equals(name)) {
						target = openGL;
					} else if ("world-sector-preload".equals(name)) {
						target = preload;
					} else if (name != null
						&& name.startsWith("resident-object-build-")) {
						target = objectBuild;
					}
					if (target == null) {
						continue;
					}
					long cpu =
						threadBean.isThreadCpuTimeSupported()
								&& threadBean.isThreadCpuTimeEnabled()
							? threadBean.getThreadCpuTime(ids[index])
							: -1L;
					long allocated =
						allocationBean != null
								&& allocationBean
									.isThreadAllocatedMemorySupported()
								&& allocationBean
									.isThreadAllocatedMemoryEnabled()
							? allocationBean
								.getThreadAllocatedBytes(ids[index])
							: -1L;
					target.add(
						cpu,
						allocated,
						info.getBlockedCount(),
						info.getBlockedTime(),
						info.getWaitedCount(),
						info.getWaitedTime());
				}
			}
			long captured = System.nanoTime();
			return new RuntimeSnapshot(
				captured,
				captured - captureStart,
				heapUsed,
				gcCount,
				gcTime,
				processCpu,
				client,
				openGL,
				preload,
				objectBuild);
		}

	}

	private static final class ThreadGroupSample {
		private int threads;
		private long cpuNanos;
		private long allocatedBytes;
		private long blockedCount;
		private long blockedTimeMillis;
		private long waitedCount;
		private long waitedTimeMillis;

		private void add(
			long cpu,
			long allocated,
			long blockedCount,
			long blockedTimeMillis,
			long waitedCount,
			long waitedTimeMillis) {
			threads++;
			if (cpu >= 0L) {
				cpuNanos += cpu;
			}
			if (allocated >= 0L) {
				allocatedBytes += allocated;
			}
			this.blockedCount += Math.max(0L, blockedCount);
			this.blockedTimeMillis +=
				Math.max(0L, blockedTimeMillis);
			this.waitedCount += Math.max(0L, waitedCount);
			this.waitedTimeMillis +=
				Math.max(0L, waitedTimeMillis);
		}
	}

	private static final class CompletedTrace {
		private final Trace trace;
		private final String reason;
		private final long finishedNanos;
		private final RuntimeSnapshot runtimeEnd;
		private final long suppressedTransitions;

		private CompletedTrace(
			Trace trace,
			String reason,
			long finishedNanos,
			RuntimeSnapshot runtimeEnd,
			long suppressedTransitions) {
			this.trace = trace;
			this.reason = reason;
			this.finishedNanos = finishedNanos;
			this.runtimeEnd = runtimeEnd;
			this.suppressedTransitions = suppressedTransitions;
		}

		private void appendTo(RendererDiagnosticSession.Record event) {
			event.number("traceId", trace.traceId);
			event.string("completion", reason);
			event.number("protocolVersion", trace.protocolVersion);
			event.number("contextSequence", trace.contextSequence);
			event.number("serverTick", trace.serverTick);
			event.number("logicalX", trace.logicalX);
			event.number("logicalY", trace.logicalY);
			event.number("logicalLevel", trace.logicalLevel);
			event.number("centerX", trace.centerX);
			event.number("centerY", trace.centerY);
			event.number(
				"durationNanos",
				Math.max(0L, finishedNanos - trace.startedNanos));
			event.bool("scopeChanged", trace.scopeChanged);
			event.bool(
				"prediction.published",
				trace.predictedPublished);
			event.bool(
				"prediction.matched",
				trace.prediction != null);
			event.number("visit.priorCount", trace.priorVisits);
			event.bool("visit.return", trace.priorVisits > 0);
			event.string("crossing.kind", direction(trace));
			event.bool("region.observed", trace.regionObserved);
			event.bool("region.hard", trace.hardAreaLoad);
			event.bool("region.planeChanged", trace.planeChanged);
			event.number("region.baseDeltaX", trace.baseDeltaX);
			event.number("region.baseDeltaZ", trace.baseDeltaZ);
			event.number("region.sceneryCount", trace.sceneryCount);
			event.number("region.wallCount", trace.wallCount);
			event.number("region.playerCount", trace.playerCount);
			event.number("region.npcCount", trace.npcCount);

			event.number("packet.count", trace.packetCount);
			event.number("packet.bytes", trace.packetBytes);
			event.number("packet.totalNanos", trace.packetNanos);
			event.number("disk.reads", trace.diskReads);
			event.number("disk.bytes", trace.diskBytes);
			event.number("disk.totalNanos", trace.diskNanos);
			event.number("lock.waitCount", trace.lockWaitCount);
			event.number("lock.waitNanos", trace.lockWaitNanos);

			event.number("scenery.inputs", trace.staticInputs);
			event.number(
				"scenery.cacheHits",
				trace.staticCacheHits);
			event.number(
				"scenery.cacheMisses",
				trace.staticCacheMisses);
			event.number(
				"scenery.meshCpuNanos",
				trace.staticMeshCpuNanos);
			event.bool(
				"scenery.parallel",
				trace.staticParallel);
			event.number("scenery.workers", trace.staticWorkers);

			event.bool(
				"atomic.playerReceipt",
				trace.atomicPlayerReceipt);
			event.bool(
				"atomic.staticBaseline",
				trace.atomicStaticBaseline);
			event.number(
				"atomic.playerReceiptOffsetNanos",
				trace.atomicPlayerReceiptNanos == 0L
					? -1L
					: trace.atomicPlayerReceiptNanos
						- trace.startedNanos);
			event.number(
				"atomic.staticBaselineOffsetNanos",
				trace.atomicStaticBaselineNanos == 0L
					? -1L
					: trace.atomicStaticBaselineNanos
						- trace.startedNanos);
			event.number(
				"atomic.elapsedNanos",
				trace.atomicElapsedNanos);
			event.number(
				"atomic.completedOffsetNanos",
				trace.atomicCompletedNanos == 0L
					? -1L
					: trace.atomicCompletedNanos
						- trace.startedNanos);
			event.number(
				"presentation.productsWaitOffsetNanos",
				trace.presentationProductsWaitNanos == 0L
					? -1L
					: trace.presentationProductsWaitNanos
						- trace.startedNanos);
			event.number(
				"presentation.productsReadyOffsetNanos",
				trace.presentationProductsReadyNanos == 0L
					? -1L
					: trace.presentationProductsReadyNanos
						- trace.startedNanos);
			event.number(
				"presentation.retainedAttempts",
				trace.presentationRetentionCount);
			event.number(
				"presentation.retainedFirstOffsetNanos",
				trace.presentationRetentionFirstNanos == 0L
					? -1L
					: trace.presentationRetentionFirstNanos
						- trace.startedNanos);
			event.number(
				"presentation.retainedLastOffsetNanos",
				trace.presentationRetentionLastNanos == 0L
					? -1L
					: trace.presentationRetentionLastNanos
						- trace.startedNanos);
			event.number(
				"presentation.releaseOffsetNanos",
				trace.presentationReleaseNanos == 0L
					? -1L
					: trace.presentationReleaseNanos
						- trace.startedNanos);
			event.number(
				"presentation.stabilitySamples",
				trace.presentationSamples);
			event.bool(
				"presentation.stable",
				trace.presentationStable);
			event.number(
				"presentation.releaseChunks",
				trace.releaseChunkCount);
			event.number(
				"presentation.releaseTriangles",
				trace.releaseTriangleCount);
			event.number(
				"opengl.presenterWait.count",
				trace.openGLPresenterWaitCount);
			event.number(
				"opengl.presenterWait.totalNanos",
				trace.openGLPresenterWaitNanos);
			event.number(
				"opengl.presenterWait.maxNanos",
				trace.openGLPresenterWaitMaxNanos);
			event.number(
				"opengl.presenterWait.firstOffsetNanos",
				trace.openGLPresenterWaitFirstNanos == 0L
					? -1L
					: trace.openGLPresenterWaitFirstNanos
						- trace.startedNanos);
			event.number(
				"opengl.presenterWait.acquiredFrames",
				trace.openGLPresenterAcquiredFrames);
			event.number(
				"opengl.queue.count",
				trace.openGLQueueSamples);
			event.number(
				"opengl.queue.totalNanos",
				trace.openGLQueueNanos);
			event.number(
				"opengl.queue.maxNanos",
				trace.openGLQueueMaxNanos);

			event.strings(
				"phase.names",
				Arrays.copyOf(
					trace.phaseNames,
					trace.phaseKeyCount));
			event.numbers(
				"phase.totalNanos",
				Arrays.copyOf(
					trace.phaseTotals,
					trace.phaseKeyCount));
			event.numbers(
				"phase.maxNanos",
				Arrays.copyOf(
					trace.phaseMax,
					trace.phaseKeyCount));
			event.numbers(
				"phase.counts",
				Arrays.copyOf(
					trace.phaseCounts,
					trace.phaseKeyCount));
			event.strings(
				"span.names",
				Arrays.copyOf(
					trace.spanNames,
					trace.spanCount));
			event.strings(
				"span.threads",
				Arrays.copyOf(
					trace.spanThreads,
					trace.spanCount));
			event.numbers(
				"span.offsetNanos",
				Arrays.copyOf(
					trace.spanOffsets,
					trace.spanCount));
			event.numbers(
				"span.durationNanos",
				Arrays.copyOf(
					trace.spanDurations,
					trace.spanCount));
			event.number("span.dropped", trace.droppedSpans);
			event.number(
				"diagnostics.suppressedTransitions",
				suppressedTransitions);

			appendOpenGLFrames(event);
			appendClientLoopFrames(event);
			appendPresentationFrames(event);
			appendRuntime(event);
			appendPrediction(event);
		}

		private void appendOpenGLFrames(
			RendererDiagnosticSession.Record event) {
			int count = trace.openGLFrameCount;
			long[] sequence = new long[count];
			long[] offset = new long[count];
			long[] upload = new long[count];
			long[] render = new long[count];
			long[] interval = new long[count];
			long[] base = new long[count];
			long[] world = new long[count];
			long[] worldSprite = new long[count];
			long[] sprite = new long[count];
			long[] debug = new long[count];
			long[] swap = new long[count];
			long[] chunks = new long[count];
			long[] triangles = new long[count];
			long[] uploadedBytes = new long[count];
			long[] requested = new long[count];
			long[] uploaded = new long[count];
			long[] reused = new long[count];
			long[] deferred = new long[count];
			long[] chunkUpload = new long[count];
			long[] projectedDraw = new long[count];
			long[] residentDraw = new long[count];
			long[] shadowBuild = new long[count];
			long[] shadowUpload = new long[count];
			long[] shadowReused = new long[count];
			long[] shadowPrepared = new long[count];
			long[] shadowRequested = new long[count];
			for (int index = 0; index < count; index++) {
				OpenGLFrameSample sample =
					trace.openGLFrames[index];
				sequence[index] = sample.sequence;
				offset[index] =
					sample.observedNanos - trace.startedNanos;
				upload[index] = sample.uploadNanos;
				render[index] = sample.renderNanos;
				interval[index] = sample.intervalNanos;
				base[index] = sample.baseNanos;
				world[index] = sample.worldNanos;
				worldSprite[index] = sample.worldSpriteNanos;
				sprite[index] = sample.spriteOverlayNanos;
				debug[index] = sample.debugOverlayNanos;
				swap[index] = sample.swapNanos;
				chunks[index] = sample.chunkCount;
				triangles[index] = sample.triangleCount;
				uploadedBytes[index] = sample.uploadedBytes;
				requested[index] = sample.requestedChunks;
				uploaded[index] = sample.uploadedChunks;
				reused[index] = sample.reusedChunks;
				deferred[index] = sample.deferredChunks;
				chunkUpload[index] = sample.chunkUploadNanos;
				projectedDraw[index] =
					sample.projectedDrawNanos;
				residentDraw[index] =
					sample.residentDrawNanos;
				shadowBuild[index] = sample.shadowBuildNanos;
				shadowUpload[index] =
					sample.shadowUploadNanos;
				shadowReused[index] =
					sample.shadowReused ? 1L : 0L;
				shadowPrepared[index] =
					sample.shadowPrepared ? 1L : 0L;
				shadowRequested[index] =
					sample.shadowRequested ? 1L : 0L;
			}
			event.number("frame.opengl.count", count);
			event.number(
				"frame.opengl.dropped",
				trace.droppedOpenGLFrames);
			event.numbers("frame.opengl.sequence", sequence);
			event.numbers("frame.opengl.offsetNanos", offset);
			event.numbers("frame.opengl.uploadNanos", upload);
			event.numbers("frame.opengl.renderNanos", render);
			event.numbers("frame.opengl.intervalNanos", interval);
			event.numbers("frame.opengl.baseNanos", base);
			event.numbers("frame.opengl.worldNanos", world);
			event.numbers(
				"frame.opengl.worldSpriteNanos",
				worldSprite);
			event.numbers(
				"frame.opengl.spriteOverlayNanos",
				sprite);
			event.numbers(
				"frame.opengl.debugOverlayNanos",
				debug);
			event.numbers("frame.opengl.swapNanos", swap);
			event.numbers("frame.opengl.chunkCount", chunks);
			event.numbers(
				"frame.opengl.triangleCount",
				triangles);
			event.numbers(
				"frame.opengl.uploadedBytes",
				uploadedBytes);
			event.numbers(
				"frame.opengl.requestedChunks",
				requested);
			event.numbers(
				"frame.opengl.uploadedChunks",
				uploaded);
			event.numbers(
				"frame.opengl.reusedChunks",
				reused);
			event.numbers(
				"frame.opengl.deferredChunks",
				deferred);
			event.numbers(
				"frame.opengl.chunkUploadNanos",
				chunkUpload);
			event.numbers(
				"frame.opengl.projectedDrawNanos",
				projectedDraw);
			event.numbers(
				"frame.opengl.residentDrawNanos",
				residentDraw);
			event.numbers(
				"frame.opengl.shadowBuildNanos",
				shadowBuild);
			event.numbers(
				"frame.opengl.shadowUploadNanos",
				shadowUpload);
			event.numbers(
				"frame.opengl.shadowReused",
				shadowReused);
			event.numbers(
				"frame.opengl.shadowPrepared",
				shadowPrepared);
			event.numbers(
				"frame.opengl.shadowRequested",
				shadowRequested);
			event.number(
				"frame.opengl.renderP50Nanos",
				percentile(render, count, 0.50D));
			event.number(
				"frame.opengl.renderP95Nanos",
				percentile(render, count, 0.95D));
			event.number(
				"frame.opengl.renderP99Nanos",
				percentile(render, count, 0.99D));
			event.number(
				"frame.opengl.renderMaxNanos",
				percentile(render, count, 1.0D));
			event.number(
				"frame.opengl.intervalP50Nanos",
				percentile(interval, count, 0.50D));
			event.number(
				"frame.opengl.intervalP95Nanos",
				percentile(interval, count, 0.95D));
			event.number(
				"frame.opengl.intervalP99Nanos",
				percentile(interval, count, 0.99D));
			event.number(
				"frame.opengl.intervalMaxNanos",
				percentile(interval, count, 1.0D));
		}

		private void appendClientLoopFrames(
			RendererDiagnosticSession.Record event) {
			int count = trace.clientLoopFrameCount;
			event.number("frame.client.count", count);
			event.number(
				"frame.client.dropped",
				trace.droppedClientLoopFrames);
			event.numbers(
				"frame.client.sequence",
				Arrays.copyOf(trace.clientLoopSequences, count));
			event.numbers(
				"frame.client.offsetNanos",
				Arrays.copyOf(trace.clientLoopOffsets, count));
			event.numbers(
				"frame.client.loopNanos",
				Arrays.copyOf(trace.clientLoopTotal, count));
			event.numbers(
				"frame.client.sleepNanos",
				Arrays.copyOf(trace.clientLoopSleep, count));
			event.numbers(
				"frame.client.updateNanos",
				Arrays.copyOf(trace.clientLoopUpdate, count));
			event.numbers(
				"frame.client.repositionNanos",
				Arrays.copyOf(trace.clientLoopReposition, count));
			event.numbers(
				"frame.client.drawNanos",
				Arrays.copyOf(trace.clientLoopDraw, count));
			event.numbers(
				"frame.client.updateCount",
				Arrays.copyOf(trace.clientLoopUpdateCount, count));
			event.numbers(
				"frame.client.sleepRequestMillis",
				Arrays.copyOf(
					trace.clientLoopSleepRequestMillis,
					count));
			event.numbers(
				"frame.client.stepSize",
				Arrays.copyOf(trace.clientLoopStepSize, count));
			event.numbers(
				"frame.client.skippedDraw",
				Arrays.copyOf(trace.clientLoopSkippedDraw, count));
			event.number(
				"frame.client.loopP50Nanos",
				percentile(trace.clientLoopTotal, count, 0.50D));
			event.number(
				"frame.client.loopP95Nanos",
				percentile(trace.clientLoopTotal, count, 0.95D));
			event.number(
				"frame.client.loopP99Nanos",
				percentile(trace.clientLoopTotal, count, 0.99D));
			event.number(
				"frame.client.loopMaxNanos",
				percentile(trace.clientLoopTotal, count, 1.0D));
		}

		private void appendPresentationFrames(
			RendererDiagnosticSession.Record event) {
			int count = trace.presentationFrameCount;
			event.number("frame.presentation.count", count);
			event.number(
				"frame.presentation.dropped",
				trace.droppedPresentationFrames);
			event.numbers(
				"frame.presentation.sequence",
				Arrays.copyOf(
					trace.presentationSequences,
					count));
			event.numbers(
				"frame.presentation.offsetNanos",
				Arrays.copyOf(
					trace.presentationOffsets,
					count));
			event.numbers(
				"frame.presentation.totalNanos",
				Arrays.copyOf(
					trace.presentationTotal,
					count));
			event.numbers(
				"frame.presentation.commitNanos",
				Arrays.copyOf(
					trace.presentationCommit,
					count));
			event.numbers(
				"frame.presentation.presentNanos",
				Arrays.copyOf(
					trace.presentationPresent,
					count));
			event.number(
				"frame.presentation.totalP50Nanos",
				percentile(
					trace.presentationTotal,
					count,
					0.50D));
			event.number(
				"frame.presentation.totalP95Nanos",
				percentile(
					trace.presentationTotal,
					count,
					0.95D));
			event.number(
				"frame.presentation.totalP99Nanos",
				percentile(
					trace.presentationTotal,
					count,
					0.99D));
			event.number(
				"frame.presentation.totalMaxNanos",
				percentile(
					trace.presentationTotal,
					count,
					1.0D));
		}

		private void appendRuntime(
			RendererDiagnosticSession.Record event) {
			RuntimeSnapshot start = trace.runtimeStart;
			RuntimeSnapshot end = runtimeEnd;
			event.number(
				"runtime.snapshotOverheadNanos",
				start.captureDurationNanos
					+ end.captureDurationNanos);
			event.number(
				"runtime.heapDeltaBytes",
				end.heapUsedBytes - start.heapUsedBytes);
			event.number(
				"runtime.gcCountDelta",
				delta(end.gcCount, start.gcCount));
			event.number(
				"runtime.gcTimeMillisDelta",
				delta(end.gcTimeMillis, start.gcTimeMillis));
			event.number(
				"runtime.processCpuNanosDelta",
				delta(
					end.processCpuNanos,
					start.processCpuNanos));
			appendThreadDelta(
				event,
				"runtime.thread.client",
				start.client,
				end.client);
			appendThreadDelta(
				event,
				"runtime.thread.opengl",
				start.openGL,
				end.openGL);
			appendThreadDelta(
				event,
				"runtime.thread.preload",
				start.preload,
				end.preload);
			appendThreadDelta(
				event,
				"runtime.thread.objectBuild",
				start.objectBuild,
				end.objectBuild);
		}

		private void appendPrediction(
			RendererDiagnosticSession.Record event) {
			PredictionSample prediction = trace.prediction;
			if (prediction == null) {
				return;
			}
			event.number(
				"prediction.protocolVersion",
				prediction.protocolVersion);
			event.number(
				"prediction.decodeNanos",
				prediction.decodeNanos);
			event.number(
				"prediction.buildNanos",
				prediction.buildNanos);
			event.number(
				"prediction.queuedToReadyNanos",
				prediction.queuedToReadyNanos);
			event.bool(
				"prediction.productCacheHit",
				prediction.productCacheHit);
			event.number(
				"prediction.triangleCount",
				prediction.triangleCount);
			event.number(
				"prediction.reusedChunks",
				prediction.reusedChunks);
			event.number(
				"prediction.builtChunks",
				prediction.builtChunks);
			event.number(
				"prediction.leadNanos",
				Math.max(
					0L,
					trace.startedNanos
						- prediction.readyNanos));
		}

		private static void appendThreadDelta(
			RendererDiagnosticSession.Record event,
			String key,
			ThreadGroupSample start,
			ThreadGroupSample end) {
			event.number(key + ".threads", end.threads);
			event.number(
				key + ".cpuNanosDelta",
				delta(end.cpuNanos, start.cpuNanos));
			event.number(
				key + ".allocatedBytesDelta",
				delta(
					end.allocatedBytes,
					start.allocatedBytes));
			event.number(
				key + ".blockedCountDelta",
				delta(
					end.blockedCount,
					start.blockedCount));
			event.number(
				key + ".blockedTimeMillisDelta",
				delta(
					end.blockedTimeMillis,
					start.blockedTimeMillis));
			event.number(
				key + ".waitedCountDelta",
				delta(
					end.waitedCount,
					start.waitedCount));
			event.number(
				key + ".waitedTimeMillisDelta",
				delta(
					end.waitedTimeMillis,
					start.waitedTimeMillis));
		}
	}
}
