package orsc;

import orsc.graphics.Renderer2DFrame;
import orsc.graphics.three.Renderer3DMaterialFamily;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

import java.awt.image.BufferedImage;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RenderTelemetry {
	private static final String ENABLED_PROPERTY = "spoiledmilk.rendererTelemetry";
	private static final String ENABLED_ENV = "SPOILED_MILK_RENDERER_TELEMETRY";
	private static final String REPORT_INTERVAL_PROPERTY = "spoiledmilk.rendererTelemetryInterval";
	private static final String SLOW_FRAME_MS_PROPERTY = "spoiledmilk.rendererSlowFrameMs";

	private static final int RECENT_SAMPLE_LIMIT = 120;
	private static final boolean RUNTIME_ENABLED = readBoolean(ENABLED_PROPERTY, ENABLED_ENV);
	private static final int REPORT_INTERVAL = Math.max(1, readInt(REPORT_INTERVAL_PROPERTY, 300));
	private static final int WINDOW_SAMPLE_LIMIT = Math.max(512, REPORT_INTERVAL * 2);
	private static final int RUNTIME_TOP_THREAD_LIMIT = 8;
	private static final long SLOW_FRAME_NANOS = Math.max(1L, readInt(SLOW_FRAME_MS_PROPERTY, 35)) * 1_000_000L;
	private static final long SLOW_REPORT_THROTTLE_NANOS = 1_000_000_000L;

	private static final StageStats frameStats = new StageStats();
	private static final StageStats clientLoopStats = new StageStats();
	private static final StageStats clientLoopSleepStats = new StageStats();
	private static final StageStats clientLoopUpdateStats = new StageStats();
	private static final StageStats clientLoopRepositionStats = new StageStats();
	private static final StageStats clientLoopDrawStats = new StageStats();
	private static final StageStats sceneRenderStats = new StageStats();
	private static final StageStats sceneModelRotateStats = new StageStats();
	private static final StageStats sceneWorldCullStats = new StageStats();
	private static final StageStats sceneDepthExportStats = new StageStats();
	private static final StageStats sceneLegacyDrawStats = new StageStats();
	private static final StageStats sceneMeshExportStats = new StageStats();
	private static final StageStats worldSectionLoadStats = new StageStats();
	private static final StageStats worldSectionLoadResetStats = new StageStats();
	private static final StageStats worldSectionLoadActivePlaneStats = new StageStats();
	private static final StageStats worldSectionLoadUpperPlanesStats = new StageStats();
	private static final StageStats worldSectionLoadBridgeStats = new StageStats();
	private static final StageStats worldSectionLoadChunkFrameStats = new StageStats();
	private static final StageStats worldSectionLoadPreloadStats = new StageStats();
	private static final StageStats commitStats = new StageStats();
	private static final StageStats scalarResizeStats = new StageStats();
	private static final StageStats backingCopyStats = new StageStats();
	private static final StageStats presentStats = new StageStats();
	private static final StageStats setGameImageStats = new StageStats();
	private static final StageStats sourceCopyStats = new StageStats();
	private static final StageStats paintImmediateStats = new StageStats();
	private static final StageStats viewportPaintStats = new StageStats();
	private static final StageStats viewportScaleStats = new StageStats();
	private static final StageStats smoothScaleStats = new StageStats();
	private static final StageStats gpuPresenterStats = new StageStats();
	private static final StageStats openGLSnapshotStats = new StageStats();
	private static final StageStats openGLUploadStats = new StageStats();
	private static final StageStats openGLRenderStats = new StageStats();
	private static final StageStats openGLFrameIntervalStats = new StageStats();
	private static final StageStats openGLBaseStats = new StageStats();
	private static final StageStats openGLWorldStats = new StageStats();
	private static final StageStats openGLWorldSpriteStats = new StageStats();
	private static final StageStats openGLWorldChunkUploadBudgetStats = new StageStats();
	private static final StageStats openGLWorldChunkUploadPhaseStats = new StageStats();
	private static final StageStats openGLWorldProjectedMeshPhaseStats = new StageStats();
	private static final StageStats openGLWorldChunkDrawPhaseStats = new StageStats();
	private static final StageStats openGLRemasterShadowMaskBuildStats = new StageStats();
	private static final StageStats openGLRemasterShadowMaskUploadStats = new StageStats();
	private static final StageStats openGLSpriteOverlayStats = new StageStats();
	private static final StageStats openGLDebugOverlayStats = new StageStats();
	private static final StageStats openGLSwapStats = new StageStats();
	private static final CounterStats spriteOverlayCapturedStats = new CounterStats();
	private static final CounterStats spriteOverlayStaticReplayStats = new CounterStats();
	private static final CounterStats spriteOverlayVisibleReplayStats = new CounterStats();
	private static final CounterStats spriteOverlaySkippedOrderedStats = new CounterStats();
	private static final CounterStats spriteOverlaySkippedInvisibleStats = new CounterStats();
	private static final CounterStats spriteOverlaySkippedAtlasFullStats = new CounterStats();
	private static final CounterStats spriteOverlayVisiblePixelStats = new CounterStats();
	private static final CounterStats spriteOverlaySceneCommandStats = new CounterStats();
	private static final CounterStats spriteOverlayWorldCommandStats = new CounterStats();
	private static final CounterStats spriteOverlayUiCommandStats = new CounterStats();
	private static final CounterStats spriteOverlayUnknownCommandStats = new CounterStats();
	private static final CounterStats spriteOverlayDirectSceneStats = new CounterStats();
	private static final CounterStats spriteOverlayDirectWorldStats = new CounterStats();
	private static final CounterStats spriteOverlayDirectUiStats = new CounterStats();
	private static final CounterStats spriteOverlayDirectUnknownStats = new CounterStats();
	private static final CounterStats spriteOverlayVisibleSceneStats = new CounterStats();
	private static final CounterStats spriteOverlayVisibleWorldStats = new CounterStats();
	private static final CounterStats spriteOverlayVisibleUiStats = new CounterStats();
	private static final CounterStats spriteOverlayVisibleUnknownStats = new CounterStats();
	private static final CounterStats legacySceneSpriteRestoreCommandStats = new CounterStats();
	private static final CounterStats legacySceneSpriteRestoreFallbackStats = new CounterStats();
	private static final CounterStats legacySceneSpriteRestoreFallbackPixelStats = new CounterStats();
	private static final CounterStats spriteCaptureAttemptStats = new CounterStats();
	private static final CounterStats spriteCaptureAcceptedStats = new CounterStats();
	private static final CounterStats spriteCaptureReplacedUiStats = new CounterStats();
	private static final CounterStats spriteCaptureUiBaseStats = new CounterStats();
	private static final CounterStats spriteCaptureSkippedAlphaStats = new CounterStats();
	private static final CounterStats spriteCaptureSkippedBoundsStats = new CounterStats();
	private static final CounterStats spriteCaptureSkippedSourceStats = new CounterStats();
	private static final CounterStats spriteCaptureSkippedTransformStats = new CounterStats();
	private static final CounterStats spriteCaptureSkippedInterlaceStats = new CounterStats();
	private static final CounterStats spriteCaptureSkippedOverflowStats = new CounterStats();
	private static final CounterStats spriteCaptureSkippedInvalidStats = new CounterStats();
	private static final CounterStats textCaptureDrawStats = new CounterStats();
	private static final CounterStats textCaptureGlyphStats = new CounterStats();
	private static final CounterStats textCaptureReplacedUiStats = new CounterStats();
	private static final CounterStats textCaptureSceneStats = new CounterStats();
	private static final CounterStats textCaptureWorldStats = new CounterStats();
	private static final CounterStats textCaptureUiStats = new CounterStats();
	private static final CounterStats textCaptureUnknownStats = new CounterStats();
	private static final CounterStats primitiveCaptureDrawStats = new CounterStats();
	private static final CounterStats primitiveCaptureReplacedUiStats = new CounterStats();
	private static final CounterStats rotatedSpriteDrawStats = new CounterStats();
	private static final CounterStats rotatedSpriteCapturedUiStats = new CounterStats();
	private static final CounterStats circleDrawStats = new CounterStats();
	private static final CounterStats circleCapturedUiStats = new CounterStats();
	private static final CounterStats nativeUiBlockSpriteStats = new CounterStats();
	private static final CounterStats nativeUiBlockTextStats = new CounterStats();
	private static final CounterStats nativeUiBlockPrimitiveStats = new CounterStats();
	private static final CounterStats nativeUiBlockMinimapStats = new CounterStats();
	private static final CounterStats nativeUiBlockGradientStats = new CounterStats();
	private static final CounterStats nativeUiBlockClearStats = new CounterStats();
	private static final CounterStats nativeUiBlockCircleStats = new CounterStats();
	private static final CounterStats nativeUiBlockPixelStats = new CounterStats();
	private static final CounterStats nativeUiBaseEligibleStats = new CounterStats();
	private static final CounterStats renderer2DSpriteCommandAttemptStats = new CounterStats();
	private static final CounterStats renderer2DSpriteCommandAcceptedStats = new CounterStats();
	private static final CounterStats renderer2DSpriteCommandDroppedStats = new CounterStats();
	private static final CounterStats renderer2DTextCommandAttemptStats = new CounterStats();
	private static final CounterStats renderer2DTextCommandAcceptedStats = new CounterStats();
	private static final CounterStats renderer2DTextCommandDroppedStats = new CounterStats();
	private static final CounterStats renderer2DPrimitiveCommandAttemptStats = new CounterStats();
	private static final CounterStats renderer2DPrimitiveCommandAcceptedStats = new CounterStats();
	private static final CounterStats renderer2DPrimitiveCommandDroppedStats = new CounterStats();
	private static final CounterStats renderer2DRotatedSpriteCommandAttemptStats = new CounterStats();
	private static final CounterStats renderer2DRotatedSpriteCommandAcceptedStats = new CounterStats();
	private static final CounterStats renderer2DRotatedSpriteCommandDroppedStats = new CounterStats();
	private static final CounterStats renderer2DCircleCommandAttemptStats = new CounterStats();
	private static final CounterStats renderer2DCircleCommandAcceptedStats = new CounterStats();
	private static final CounterStats renderer2DCircleCommandDroppedStats = new CounterStats();
	private static final CounterStats clientLoopUpdateCountStats = new CounterStats();
	private static final CounterStats clientLoopSkippedDrawStats = new CounterStats();
	private static final CounterStats clientLoopSleepRequestStats = new CounterStats();
	private static final CounterStats clientLoopStepSizeStats = new CounterStats();
	private static final CounterStats worldGeometryModelStats = new CounterStats();
	private static final CounterStats worldGeometryFaceStats = new CounterStats();
	private static final CounterStats worldSpriteAnchorStats = new CounterStats();
	private static final CounterStats worldGeometryTerrainFaceStats = new CounterStats();
	private static final CounterStats worldGeometryWallFaceStats = new CounterStats();
	private static final CounterStats worldGeometryRoofFaceStats = new CounterStats();
	private static final CounterStats worldGeometryGameObjectFaceStats = new CounterStats();
	private static final CounterStats worldGeometryWallObjectFaceStats = new CounterStats();
	private static final CounterStats worldGeometryOtherFaceStats = new CounterStats();
	private static final CounterStats worldDepthConsideredFaceStats = new CounterStats();
	private static final CounterStats worldDepthFaceStats = new CounterStats();
	private static final CounterStats worldDepthRejectedFaceStats = new CounterStats();
	private static final CounterStats worldDepthTriangleStats = new CounterStats();
	private static final CounterStats worldDepthPixelWriteStats = new CounterStats();
	private static final CounterStats worldMeshVertexStats = new CounterStats();
	private static final CounterStats worldMeshIndexStats = new CounterStats();
	private static final CounterStats worldMeshTriangleStats = new CounterStats();
	private static final CounterStats worldMeshTexturedTriangleStats = new CounterStats();
	private static final CounterStats worldMeshFlatColorTriangleStats = new CounterStats();
	private static final CounterStats worldMeshTransparentTriangleStats = new CounterStats();
	private static final CounterStats worldMeshSkippedTriangleStats = new CounterStats();
	private static final CounterStats openGLWorldMeshVertexStats = new CounterStats();
	private static final CounterStats openGLWorldMeshIndexStats = new CounterStats();
	private static final CounterStats openGLWorldMeshTriangleStats = new CounterStats();
	private static final CounterStats openGLWorldMeshUploadStats = new CounterStats();
	private static final CounterStats openGLWorldMeshReuseStats = new CounterStats();
	private static final CounterStats openGLWorldMeshDrawTriangleStats = new CounterStats();
	private static final CounterStats openGLWorldMeshDrawOccluderTriangleStats = new CounterStats();
	private static final CounterStats openGLWorldMeshDrawBatchStats = new CounterStats();
	private static final CounterStats openGLWorldMeshDrawCallStats = new CounterStats();
	private static final CounterStats openGLWorldChunkStats = new CounterStats();
	private static final CounterStats openGLWorldChunkVertexStats = new CounterStats();
	private static final CounterStats openGLWorldChunkIndexStats = new CounterStats();
	private static final CounterStats openGLWorldChunkTriangleStats = new CounterStats();
	private static final CounterStats openGLWorldMaterialUnclassifiedStats = new CounterStats();
	private static final CounterStats openGLWorldMaterialTerrainStats = new CounterStats();
	private static final CounterStats openGLWorldMaterialWaterStats = new CounterStats();
	private static final CounterStats openGLWorldMaterialWallStats = new CounterStats();
	private static final CounterStats openGLWorldMaterialRoofStats = new CounterStats();
	private static final CounterStats openGLWorldMaterialSceneryStats = new CounterStats();
	private static final CounterStats openGLWorldMaterialFoliageStats = new CounterStats();
	private static final CounterStats openGLWorldMaterialOreStats = new CounterStats();
	private static final CounterStats openGLWorldMaterialEmissiveStats = new CounterStats();
	private static final CounterStats openGLWorldMaterialEffectStats = new CounterStats();
	private static final CounterStats openGLWorldChunkRequestedStats = new CounterStats();
	private static final CounterStats openGLWorldChunkUploadStats = new CounterStats();
	private static final CounterStats openGLWorldChunkReuseStats = new CounterStats();
	private static final CounterStats openGLWorldChunkDeferredStats = new CounterStats();
	private static final CounterStats openGLWorldChunkEvictStats = new CounterStats();
	private static final CounterStats openGLWorldChunkConsideredStats = new CounterStats();
	private static final CounterStats openGLWorldChunkDrawStats = new CounterStats();
	private static final CounterStats openGLWorldChunkCulledStats = new CounterStats();
	private static final CounterStats openGLWorldChunkBatchConsideredStats = new CounterStats();
	private static final CounterStats openGLWorldChunkBatchDrawStats = new CounterStats();
	private static final CounterStats openGLWorldChunkBatchCulledStats = new CounterStats();
	private static final CounterStats openGLWorldChunkDrawTriangleStats = new CounterStats();
	private static final CounterStats openGLWorldChunkDrawTerrainStats = new CounterStats();
	private static final CounterStats openGLWorldChunkDrawWallStats = new CounterStats();
	private static final CounterStats openGLWorldChunkDrawRoofStats = new CounterStats();
	private static final CounterStats openGLWorldChunkDrawGameObjectStats = new CounterStats();
	private static final CounterStats openGLWorldChunkDrawWallObjectStats = new CounterStats();
	private static final CounterStats openGLWorldChunkDrawOtherStats = new CounterStats();
	private static final CounterStats openGLWorldChunkDrawFallbackStats = new CounterStats();
	private static final CounterStats openGLWorldChunkDrawSkippedStats = new CounterStats();
	private static final CounterStats openGLWorldChunkDrawCallStats = new CounterStats();
	private static final CounterStats openGLWorldChunkTextureBindStats = new CounterStats();
	private static final CounterStats openGLWorldChunkShadowChunkStats = new CounterStats();
	private static final CounterStats openGLWorldChunkShadowIndexStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowReceiverChunkStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowReceiverTriangleStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowCasterStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowWallCasterStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowGameObjectCasterStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowWallObjectCasterStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowOutdoorOnlyCasterStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowClippingCandidateStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowRoofedReceiverStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowOutdoorReceiverStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowUnknownReceiverStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowRoofedCasterStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowOutdoorCasterStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowUnknownCasterStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowSunlightEligibleCasterStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowSunlightSuppressedRoofedCasterStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowSunlightSuppressedUnknownCasterStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowMaskWidthStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowMaskHeightStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowMaskVisiblePixelStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowMaskCacheHitStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowMaskRebuildStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowMaskUploadCountStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowMaskUploadSkipStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowMaskStripCasterStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowMaskSoftSceneryCasterStats = new CounterStats();
	private static final CounterStats openGLRemasterShadowMaskContactCasterStats = new CounterStats();
	private static final CounterStats openGLResidentChunkReplacementRequestedStats = new CounterStats();
	private static final CounterStats openGLResidentChunkReplacementActiveStats = new CounterStats();
	private static final CounterStats openGLResidentChunkReplacementFallbackStats = new CounterStats();
	private static final CounterStats openGLResidentChunkDrawableTerrainBatchStats = new CounterStats();
	private static final CounterStats openGLResidentChunkDrawableWallBatchStats = new CounterStats();
	private static final CounterStats openGLResidentChunkDrawableRoofBatchStats = new CounterStats();
	private static final CounterStats openGLWorldTextureReferencedStats = new CounterStats();
	private static final CounterStats openGLWorldTextureCachedStats = new CounterStats();
	private static final CounterStats openGLWorldTextureUploadedStats = new CounterStats();
	private static final CounterStats openGLWorldTextureMissingStats = new CounterStats();
	private static final CounterStats openGLWorldTextureAtlasStats = new CounterStats();
	private static final CounterStats openGLWorldSpriteAnchorStats = new CounterStats();
	private static final CounterStats openGLWorldSpriteMatchedStats = new CounterStats();
	private static final CounterStats openGLWorldSpriteDrawnStats = new CounterStats();
	private static final CounterStats openGLWorldSpriteOwnerAnchorStats = new CounterStats();
	private static final CounterStats openGLWorldSpriteLegacyFallbackStats = new CounterStats();
	private static final CounterStats openGLWorldSpriteUnmatchedStats = new CounterStats();
	private static final CounterStats openGLWorldSpriteSnapshotGroupStats = new CounterStats();
	private static final CounterStats openGLWorldSpriteSnapshotLayerStats = new CounterStats();
	private static final CounterStats openGLWorldSpriteSnapshotCompatibilityFallbackStats = new CounterStats();
	private static final CounterStats openGLWorldEntityConsideredStats = new CounterStats();
	private static final CounterStats openGLWorldEntityDrawnStats = new CounterStats();
	private static final CounterStats openGLWorldEntityCulledStats = new CounterStats();

	private static final Map<String, AllocationStats> allocationStats = new LinkedHashMap<>();

	private static long repaintRequests;
	private static long paintImmediateRequests;
	private static long nearestScalePaints;
	private static long interpolationScalePaints;
	private static long gpuPresenterPaints;
	private static long openGLFrames;
	private static long openGLDroppedFrames;
	private static long openGLFramesWindow;
	private static long openGLDroppedFramesWindow;
	private static long lastOpenGLFrameNanos;
	private static long lastClientLoopSampleNanos;
	private static long openGLWorldChunkUploadBudgetLimitNanos;
	private static String openGLWorldChunkUploadReason = "steady";
	private static final int OPENGL_BOUNDARY_TRANSITION_TRACE_FRAMES = 90;
	private static long openGLBoundaryTransitionTraceId;
	private static int openGLBoundaryTransitionTraceFramesRemaining;
	private static int openGLBoundaryTransitionTraceFrameIndex;
	private static int openGLBoundaryTransitionSectionX;
	private static int openGLBoundaryTransitionSectionY;
	private static long lastReportNanos;
	private static String openGLRemasterShadowMaskReason = "not-built";
	private static String openGLResidentChunkReplacementReason = "not-requested";
	private static long diagnosticLastFrameNanos;
	private static int diagnosticLastSourceWidth;
	private static int diagnosticLastSourceHeight;
	private static float diagnosticLastScalar;
	private static String diagnosticLastScalingType = "unknown";
	private static String diagnosticLastFramePath = "unknown";
	private static boolean diagnosticCameraStateKnown;
	private static int diagnosticCameraZoomSetting;
	private static int diagnosticCameraZoomSettingMin;
	private static int diagnosticCameraZoomSettingMax;
	private static int diagnosticCameraBaseZoom;
	private static int diagnosticCameraEffectiveZoom;
	private static int diagnosticCameraPitch;
	private static int diagnosticCameraRotation;
	private static boolean diagnosticCameraFirstPerson;
	private static String diagnosticCameraFogMode = "unknown";
	private static int diagnosticCameraDrawDistanceWorldUnits;
	private static int diagnosticCameraFogStartDistanceWorldUnits;
	private static int diagnosticCameraDrawDistanceTiles;
	private static int diagnosticCameraFogStartDistanceTiles;
	private static final DiagnosticMetric[] DIAGNOSTIC_METRICS = buildDiagnosticMetrics();
	private static long diagnosticLastGcCount = -1L;
	private static long diagnosticLastGcTimeMillis = -1L;
	private static final Map<String, Long> diagnosticLastGcCountByName = new LinkedHashMap<>();
	private static final Map<String, Long> diagnosticLastGcTimeByName = new LinkedHashMap<>();
	private static long diagnosticLastRuntimeSampleNanos = -1L;
	private static long diagnosticLastProcessCpuTimeNanos = -1L;
	private static final Map<Long, RuntimeThreadSample> diagnosticLastThreadSamples =
		new LinkedHashMap<Long, RuntimeThreadSample>();

	private RenderTelemetry() {
	}

	public static boolean isEnabled() {
		return isCollectionEnabled();
	}

	public static long now() {
		return isCollectionEnabled() ? System.nanoTime() : 0L;
	}

	public static long elapsedSince(long startNanos) {
		return isCollectionEnabled() && startNanos != 0L ? System.nanoTime() - startNanos : 0L;
	}

	private static boolean isCollectionEnabled() {
		return RUNTIME_ENABLED || RendererDebugSettings.isOverlayEnabled();
	}

	static void recordFrame(
		long totalNanos,
		long commitNanos,
		long scalarResizeNanos,
		long backingCopyNanos,
		long presentNanos,
		int sourceWidth,
		int sourceHeight,
		float scalar,
		Object scalingType,
		String framePath) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			frameStats.record(totalNanos);
			commitStats.record(commitNanos);
			scalarResizeStats.record(scalarResizeNanos);
			backingCopyStats.record(backingCopyNanos);
			presentStats.record(presentNanos);
			diagnosticLastFrameNanos = totalNanos;
			diagnosticLastSourceWidth = sourceWidth;
			diagnosticLastSourceHeight = sourceHeight;
			diagnosticLastScalar = scalar;
			diagnosticLastScalingType = String.valueOf(scalingType);
			diagnosticLastFramePath = framePath == null ? "unknown" : framePath;

			boolean slowFrame = totalNanos >= SLOW_FRAME_NANOS;
			long now = System.nanoTime();
			if (RUNTIME_ENABLED
				&& (frameStats.count % REPORT_INTERVAL == 0
					|| (slowFrame && now - lastReportNanos >= SLOW_REPORT_THROTTLE_NANOS))) {
				lastReportNanos = now;
				printReport(slowFrame, totalNanos, sourceWidth, sourceHeight, scalar, scalingType, framePath);
			}
		}
	}

	static void recordSceneRender(long nanos) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			sceneRenderStats.record(nanos);
		}
	}

	static void recordClientLoop(
		long loopNanos,
		long sleepNanos,
		long updateNanos,
		long repositionNanos,
		long drawNanos,
		int updateCount,
		int sleepRequestMillis,
		int stepSize,
		boolean skippedDraw) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			lastClientLoopSampleNanos = System.nanoTime();
			clientLoopStats.record(loopNanos);
			clientLoopSleepStats.record(sleepNanos);
			clientLoopUpdateStats.record(updateNanos);
			clientLoopRepositionStats.record(repositionNanos);
			clientLoopDrawStats.record(drawNanos);
			clientLoopUpdateCountStats.record(updateCount);
			clientLoopSkippedDrawStats.record(skippedDraw ? 1 : 0);
			clientLoopSleepRequestStats.record(sleepRequestMillis);
			clientLoopStepSizeStats.record(stepSize);
		}
	}

	public static void recordScene3DPhases(
		long modelRotateNanos,
		long worldCullNanos,
		long depthExportNanos,
		long legacyDrawNanos,
		long meshExportNanos) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			sceneModelRotateStats.record(modelRotateNanos);
			sceneWorldCullStats.record(worldCullNanos);
			sceneDepthExportStats.record(depthExportNanos);
			sceneLegacyDrawStats.record(legacyDrawNanos);
			sceneMeshExportStats.record(meshExportNanos);
		}
	}

	public static void recordWorldSectionLoad(long nanos) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			worldSectionLoadStats.record(nanos);
			RendererDiagnosticSession.Record event =
				RendererDiagnosticSession.newEventRecord("renderer.world-section-load");
			if (event != null) {
				event.number("loadNanos", nanos);
				event.number("rendererFrameSequence", frameStats.count);
				RendererDiagnosticSession.writeEventRecord(event);
			}
		}
	}

	public static void recordRenderer2DOverflowEvent(String stream, int limit) {
		RendererDiagnosticSession.Record event =
			RendererDiagnosticSession.newEventRecord("renderer.2d-command-overflow");
		if (event == null) {
			return;
		}
		event.string("stream", stream);
		event.number("limit", limit);
		synchronized (RenderTelemetry.class) {
			event.number("rendererFrameSequence", frameStats.count);
		}
		RendererDiagnosticSession.writeEventRecord(event);
	}

	public static void recordWorldSectionLoadPhases(
		long resetNanos,
		long activePlaneNanos,
		long upperPlanesNanos,
		long bridgeNanos,
		long chunkFrameNanos,
		long preloadNanos) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			worldSectionLoadResetStats.record(resetNanos);
			worldSectionLoadActivePlaneStats.record(activePlaneNanos);
			worldSectionLoadUpperPlanesStats.record(upperPlanesNanos);
			worldSectionLoadBridgeStats.record(bridgeNanos);
			worldSectionLoadChunkFrameStats.record(chunkFrameNanos);
			worldSectionLoadPreloadStats.record(preloadNanos);
		}
	}

	public static void recordWorldSectionTransition(
		String path,
		int plane,
		int sectionX,
		int sectionY,
		long totalNanos,
		long resetNanos,
		long activePlaneNanos,
		long upperPlanesNanos,
		long bridgeNanos,
		long chunkFrameNanos,
		long activeChunkBuildNanos,
		long predictiveClearNanos,
		long symmetricComposeNanos,
		long preloadNanos) {
		if (isCollectionEnabled()) {
			synchronized (RenderTelemetry.class) {
				worldSectionLoadStats.record(totalNanos);
				worldSectionLoadResetStats.record(resetNanos);
				worldSectionLoadActivePlaneStats.record(activePlaneNanos);
				worldSectionLoadUpperPlanesStats.record(upperPlanesNanos);
				worldSectionLoadBridgeStats.record(bridgeNanos);
				worldSectionLoadChunkFrameStats.record(chunkFrameNanos);
				worldSectionLoadPreloadStats.record(preloadNanos);
				if (RendererDiagnosticSession.isEnabled()
					&& path != null
					&& path.startsWith("native-")) {
					openGLBoundaryTransitionTraceId++;
					openGLBoundaryTransitionTraceFramesRemaining =
						OPENGL_BOUNDARY_TRANSITION_TRACE_FRAMES;
					openGLBoundaryTransitionTraceFrameIndex = 0;
					openGLBoundaryTransitionSectionX = sectionX;
					openGLBoundaryTransitionSectionY = sectionY;
				}
			}
			RendererDiagnosticSession.Record event =
				RendererDiagnosticSession.newEventRecord(
					"renderer.world-section-transition");
			if (event != null) {
				event.string("path", path);
				event.number("plane", plane);
				event.number("sectionX", sectionX);
				event.number("sectionY", sectionY);
				event.number("totalNanos", totalNanos);
				event.number("resetNanos", resetNanos);
				event.number("activePlaneNanos", activePlaneNanos);
				event.number("upperPlanesNanos", upperPlanesNanos);
				event.number("bridgeNanos", bridgeNanos);
				event.number("chunkFrameNanos", chunkFrameNanos);
				event.number(
					"activeChunkBuildNanos",
					activeChunkBuildNanos);
				event.number(
					"predictiveClearNanos",
					predictiveClearNanos);
				event.number(
					"symmetricComposeNanos",
					symmetricComposeNanos);
				event.number("preloadNanos", preloadNanos);
				RendererDiagnosticSession.writeEventRecord(event);
			}
		}
		String summary =
			"WORLD_SECTION_TRANSITION"
				+ " path=" + path
				+ " plane=" + plane
				+ " center=" + sectionX + "," + sectionY
				+ " totalMs=" + formatMillisRoot(totalNanos)
				+ " resetMs=" + formatMillisRoot(resetNanos)
				+ " activeMs=" + formatMillisRoot(activePlaneNanos)
				+ " upperMs=" + formatMillisRoot(upperPlanesNanos)
				+ " bridgeMs=" + formatMillisRoot(bridgeNanos)
				+ " frameMs=" + formatMillisRoot(chunkFrameNanos)
				+ " frameBuildMs="
					+ formatMillisRoot(activeChunkBuildNanos)
				+ " frameClearMs="
					+ formatMillisRoot(predictiveClearNanos)
				+ " frameComposeMs="
					+ formatMillisRoot(symmetricComposeNanos)
				+ " preloadMs=" + formatMillisRoot(preloadNanos);
		System.out.println(summary);
		ClientRuntimeLogger.log(summary);
	}

	static void recordOpenGLBoundaryTransitionFrame(
		int chunkCount,
		int triangleCount,
		OpenGLWorldChunkUploadStats uploadStats,
		boolean replacementRequested,
		boolean residentReady,
		boolean residentActive,
		String replacementReason,
		boolean projectedWorldDrawn,
		boolean residentWorldDrawn,
		boolean remasterShadowPrepared,
		boolean explicitRemasterShadowRequested,
		OpenGLWorldChunkDrawStats drawStats,
		long chunkUploadNanos,
		long projectedDrawNanos,
		long residentDrawNanos) {
		if (!RendererDiagnosticSession.isEnabled()) {
			return;
		}
		synchronized (RenderTelemetry.class) {
			if (openGLBoundaryTransitionTraceFramesRemaining <= 0) {
				return;
			}
			OpenGLWorldChunkUploadStats safeUpload =
				uploadStats == null
					? OpenGLWorldChunkUploadStats.EMPTY
					: uploadStats;
			OpenGLWorldChunkDrawStats safeDraw =
				drawStats == null
					? OpenGLWorldChunkDrawStats.EMPTY
					: drawStats;
			boolean shadowOverProjectedFallback =
				projectedWorldDrawn
					&& !residentWorldDrawn
					&& explicitRemasterShadowRequested;
			RendererDiagnosticSession.Record event =
				RendererDiagnosticSession.newEventRecord(
					"renderer.boundary-transition-frame");
			if (event != null) {
				event.number(
					"transitionId",
					openGLBoundaryTransitionTraceId);
				event.number(
					"transitionFrame",
					openGLBoundaryTransitionTraceFrameIndex);
				event.number(
					"rendererFrameSequence",
					frameStats.count);
				event.number(
					"sectionX",
					openGLBoundaryTransitionSectionX);
				event.number(
					"sectionY",
					openGLBoundaryTransitionSectionY);
				event.number("world.chunkCount", chunkCount);
				event.number("world.triangleCount", triangleCount);
				event.number(
					"upload.requested",
					safeUpload.requestedChunks);
				event.number(
					"upload.uploaded",
					safeUpload.uploadedChunks);
				event.number(
					"upload.reused",
					safeUpload.reusedChunks);
				event.number(
					"upload.deferred",
					safeUpload.deferredChunks);
				appendOpenGLBoundaryUploadRole(
					event,
					"world",
					Renderer3DWorldChunkFrame.CHUNK_ROLE_WORLD,
					safeUpload);
				appendOpenGLBoundaryUploadRole(
					event,
					"staticObjects",
					Renderer3DWorldChunkFrame
						.CHUNK_ROLE_STATIC_OBJECTS,
					safeUpload);
				appendOpenGLBoundaryUploadRole(
					event,
					"animatedObjects",
					Renderer3DWorldChunkFrame
						.CHUNK_ROLE_ANIMATED_OBJECTS,
					safeUpload);
				event.number(
					"upload.miss.cold",
					safeUpload.coldKeyMisses);
				event.number(
					"upload.miss.alternateStorage",
					safeUpload.alternateStorageKeyMisses);
				event.number(
					"upload.miss.alternateEquivalent",
					safeUpload.alternateEquivalentKeyMisses);
				event.number(
					"upload.miss.existingMismatch",
					safeUpload.existingKeyMismatches);
				event.number(
					"upload.cache.before",
					safeUpload.cacheSizeBefore);
				event.number(
					"upload.cache.after",
					safeUpload.cacheSizeAfter);
				event.string("upload.reason", safeUpload.reason);
				event.string(
					"upload.detail",
					safeUpload.diagnosticDetail);
				event.bool(
					"upload.detailTruncated",
					safeUpload.diagnosticDetailTruncated);
				event.number("upload.phaseNanos", chunkUploadNanos);
				event.bool(
					"ownership.replacementRequested",
					replacementRequested);
				event.bool("ownership.residentReady", residentReady);
				event.bool("ownership.residentActive", residentActive);
				event.string(
					"ownership.replacementReason",
					replacementReason);
				event.bool(
					"draw.projectedWorld",
					projectedWorldDrawn);
				event.bool(
					"draw.residentWorld",
					residentWorldDrawn);
				event.number(
					"draw.projectedNanos",
					projectedDrawNanos);
				event.number(
					"draw.residentNanos",
					residentDrawNanos);
				event.number(
					"draw.residentChunks",
					safeDraw.drawnChunks);
				event.number(
					"draw.residentTriangles",
					safeDraw.drawnTriangles);
				event.number(
					"draw.shadowProofChunks",
					safeDraw.shadowProofChunks);
				event.bool(
					"shadow.remasterPrepared",
					remasterShadowPrepared);
				event.bool(
					"shadow.explicitRemasterRequested",
					explicitRemasterShadowRequested);
				event.bool(
					"shadow.overProjectedFallback",
					shadowOverProjectedFallback);
				RendererDiagnosticSession.writeEventRecord(event);
			}
			openGLBoundaryTransitionTraceFrameIndex++;
			openGLBoundaryTransitionTraceFramesRemaining--;
		}
	}

	static boolean isOpenGLBoundaryTransitionTraceActive() {
		if (!RendererDiagnosticSession.isEnabled()) {
			return false;
		}
		synchronized (RenderTelemetry.class) {
			return openGLBoundaryTransitionTraceFramesRemaining > 0;
		}
	}

	private static void appendOpenGLBoundaryUploadRole(
		RendererDiagnosticSession.Record event,
		String label,
		int chunkRole,
		OpenGLWorldChunkUploadStats upload) {
		String prefix = "upload.role." + label + ".";
		event.number(
			prefix + "requested",
			upload.requestedForRole(chunkRole));
		event.number(
			prefix + "uploaded",
			upload.uploadedForRole(chunkRole));
		event.number(
			prefix + "reused",
			upload.reusedForRole(chunkRole));
		event.number(
			prefix + "deferred",
			upload.deferredForRole(chunkRole));
		event.number(
			prefix + "bytes",
			upload.uploadedBytesForRole(chunkRole));
		event.number(
			prefix + "nanos",
			upload.uploadNanosForRole(chunkRole));
	}

	public static void recordWorldModelTransition(
		String path,
		int plane,
		int sectionX,
		int sectionY,
		boolean activePlane,
		long totalNanos,
		long sectionWindowNanos,
		long productNanos,
		long terrainNanos,
		long wallNanos,
		long roofNanos) {
		if (isCollectionEnabled()) {
			RendererDiagnosticSession.Record event =
				RendererDiagnosticSession.newEventRecord(
					"renderer.world-model-transition");
			if (event != null) {
				event.string("path", path);
				event.number("plane", plane);
				event.number("sectionX", sectionX);
				event.number("sectionY", sectionY);
				event.bool("activePlane", activePlane);
				event.number("totalNanos", totalNanos);
				event.number("sectionWindowNanos", sectionWindowNanos);
				event.number("productNanos", productNanos);
				event.number("terrainNanos", terrainNanos);
				event.number("wallNanos", wallNanos);
				event.number("roofNanos", roofNanos);
				RendererDiagnosticSession.writeEventRecord(event);
			}
		}
		String summary =
			"WORLD_MODEL_TRANSITION"
				+ " path=" + path
				+ " plane=" + plane
				+ " center=" + sectionX + "," + sectionY
				+ " active=" + (activePlane ? 1 : 0)
				+ " totalMs=" + formatMillisRoot(totalNanos)
				+ " windowMs=" + formatMillisRoot(sectionWindowNanos)
				+ " productMs=" + formatMillisRoot(productNanos)
				+ " terrainMs=" + formatMillisRoot(terrainNanos)
				+ " wallMs=" + formatMillisRoot(wallNanos)
				+ " roofMs=" + formatMillisRoot(roofNanos);
		System.out.println(summary);
		ClientRuntimeLogger.log(summary);
	}

	public static void recordClientRegionTransition(
		String path,
		boolean hardAreaLoad,
		boolean planeChanged,
		int baseDeltaX,
		int baseDeltaZ,
		int sceneryCount,
		int wallCount,
		int playerCount,
		int npcCount,
		long totalNanos,
		long dematerializeNanos,
		long worldLoadNanos,
		long staticRebaseNanos,
		long staticMaterializeNanos,
		long groundItemsNanos,
		long actorRebaseNanos,
		long finalizeNanos,
		long baselineNanos) {
		if (isCollectionEnabled()) {
			RendererDiagnosticSession.Record event =
				RendererDiagnosticSession.newEventRecord(
					"renderer.client-region-transition");
			if (event != null) {
				event.string("path", path);
				event.bool("hardAreaLoad", hardAreaLoad);
				event.bool("planeChanged", planeChanged);
				event.number("baseDeltaX", baseDeltaX);
				event.number("baseDeltaZ", baseDeltaZ);
				event.number("sceneryCount", sceneryCount);
				event.number("wallCount", wallCount);
				event.number("playerCount", playerCount);
				event.number("npcCount", npcCount);
				event.number("totalNanos", totalNanos);
				event.number("dematerializeNanos", dematerializeNanos);
				event.number("worldLoadNanos", worldLoadNanos);
				event.number("staticRebaseNanos", staticRebaseNanos);
				event.number("staticMaterializeNanos", staticMaterializeNanos);
				event.number("groundItemsNanos", groundItemsNanos);
				event.number("actorRebaseNanos", actorRebaseNanos);
				event.number("finalizeNanos", finalizeNanos);
				event.number("baselineNanos", baselineNanos);
				RendererDiagnosticSession.writeEventRecord(event);
			}
		}
		String summary =
			"CLIENT_REGION_TRANSITION"
				+ " path=" + path
				+ " hard=" + (hardAreaLoad ? 1 : 0)
				+ " planeChanged=" + (planeChanged ? 1 : 0)
				+ " delta=" + baseDeltaX + "," + baseDeltaZ
				+ " entities=" + sceneryCount + "/" + wallCount
					+ "/" + playerCount + "/" + npcCount
				+ " totalMs=" + formatMillisRoot(totalNanos)
				+ " dematerializeMs="
					+ formatMillisRoot(dematerializeNanos)
				+ " worldMs=" + formatMillisRoot(worldLoadNanos)
				+ " staticRebaseMs=" + formatMillisRoot(staticRebaseNanos)
				+ " staticMaterializeMs="
					+ formatMillisRoot(staticMaterializeNanos)
				+ " groundMs=" + formatMillisRoot(groundItemsNanos)
				+ " actorsMs=" + formatMillisRoot(actorRebaseNanos)
				+ " finalizeMs=" + formatMillisRoot(finalizeNanos)
				+ " baselineMs=" + formatMillisRoot(baselineNanos);
		System.out.println(summary);
		ClientRuntimeLogger.log(summary);
	}

	public static String worldSectionLoadSummary() {
		synchronized (RenderTelemetry.class) {
			return formatMillis(worldSectionLoadStats.average())
				+ "/" + formatMillis(worldSectionLoadStats.max)
				+ "/" + formatMillis(worldSectionLoadStats.recentAverage())
				+ "ms | loads " + worldSectionLoadStats.count;
		}
	}

	public static String worldSectionLoadPhaseSummary() {
		synchronized (RenderTelemetry.class) {
			return formatMillis(worldSectionLoadResetStats.recentAverage())
				+ "/" + formatMillis(worldSectionLoadActivePlaneStats.recentAverage())
				+ "/" + formatMillis(worldSectionLoadUpperPlanesStats.recentAverage())
				+ "/" + formatMillis(worldSectionLoadBridgeStats.recentAverage())
				+ "/" + formatMillis(worldSectionLoadChunkFrameStats.recentAverage())
				+ "/" + formatMillis(worldSectionLoadPreloadStats.recentAverage()) + "ms";
		}
	}

	public static String worldChunkUploadDeferredSummary() {
		synchronized (RenderTelemetry.class) {
			return formatCount(openGLWorldChunkDeferredStats.recentAverage());
		}
	}

	public static String worldChunkUploadBudgetSummary() {
		synchronized (RenderTelemetry.class) {
			return formatMillis(openGLWorldChunkUploadBudgetStats.recentAverage())
				+ "/"
				+ formatMillis(openGLWorldChunkUploadBudgetLimitNanos)
				+ "ms";
		}
	}

	public static String worldMaterialFamilySummary() {
		synchronized (RenderTelemetry.class) {
			return "u/t/wa/w/r/s/f/o/em/ef "
				+ formatCount(openGLWorldMaterialUnclassifiedStats.recentAverage()) + "/"
				+ formatCount(openGLWorldMaterialTerrainStats.recentAverage()) + "/"
				+ formatCount(openGLWorldMaterialWaterStats.recentAverage()) + "/"
				+ formatCount(openGLWorldMaterialWallStats.recentAverage()) + "/"
				+ formatCount(openGLWorldMaterialRoofStats.recentAverage()) + "/"
				+ formatCount(openGLWorldMaterialSceneryStats.recentAverage()) + "/"
				+ formatCount(openGLWorldMaterialFoliageStats.recentAverage()) + "/"
				+ formatCount(openGLWorldMaterialOreStats.recentAverage()) + "/"
				+ formatCount(openGLWorldMaterialEmissiveStats.recentAverage()) + "/"
				+ formatCount(openGLWorldMaterialEffectStats.recentAverage());
		}
	}

	public static String worldSpriteOwnershipSummary() {
		synchronized (RenderTelemetry.class) {
			return formatCount(openGLWorldSpriteOwnerAnchorStats.recentAverage()) + "/"
				+ formatCount(openGLWorldSpriteLegacyFallbackStats.recentAverage()) + "/"
				+ formatCount(openGLWorldSpriteUnmatchedStats.recentAverage());
		}
	}

	public static String renderer2DCommandLimitSummary() {
		synchronized (RenderTelemetry.class) {
			return "s " + commandLimitSummary(
				renderer2DSpriteCommandAcceptedStats,
				renderer2DSpriteCommandDroppedStats,
				Renderer2DFrame.SPRITE_COMMAND_LIMIT)
				+ " | t " + commandLimitSummary(
					renderer2DTextCommandAcceptedStats,
					renderer2DTextCommandDroppedStats,
					Renderer2DFrame.TEXT_COMMAND_LIMIT)
				+ " | p " + commandLimitSummary(
					renderer2DPrimitiveCommandAcceptedStats,
					renderer2DPrimitiveCommandDroppedStats,
					Renderer2DFrame.PRIMITIVE_COMMAND_LIMIT)
				+ " | r " + commandLimitSummary(
					renderer2DRotatedSpriteCommandAcceptedStats,
					renderer2DRotatedSpriteCommandDroppedStats,
					Renderer2DFrame.ROTATED_SPRITE_COMMAND_LIMIT)
				+ " | c " + commandLimitSummary(
					renderer2DCircleCommandAcceptedStats,
					renderer2DCircleCommandDroppedStats,
					Renderer2DFrame.CIRCLE_COMMAND_LIMIT);
		}
	}

	private static String commandLimitSummary(CounterStats accepted, CounterStats dropped, int limit) {
		return formatCount(accepted.latest())
			+ "/" + formatCount(accepted.max)
			+ "/" + formatCount(dropped.latest())
			+ "@" + limit;
	}

	private static String commandLimitAverageSummary(
		CounterStats attempted,
		CounterStats accepted,
		CounterStats dropped) {
		return formatCount(attempted.average())
			+ "/" + formatCount(accepted.average())
			+ "/" + formatCount(dropped.average());
	}

	static void recordCameraState(
		int zoomSetting,
		int zoomSettingMin,
		int zoomSettingMax,
		int baseZoom,
		int effectiveZoom,
		int pitch,
		int rotation,
		boolean firstPerson,
		String fogMode,
		int drawDistanceWorldUnits,
		int fogStartDistanceWorldUnits,
		int drawDistanceTiles,
		int fogStartDistanceTiles) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			diagnosticCameraStateKnown = true;
			diagnosticCameraZoomSetting = zoomSetting;
			diagnosticCameraZoomSettingMin = zoomSettingMin;
			diagnosticCameraZoomSettingMax = zoomSettingMax;
			diagnosticCameraBaseZoom = baseZoom;
			diagnosticCameraEffectiveZoom = effectiveZoom;
			diagnosticCameraPitch = pitch;
			diagnosticCameraRotation = rotation;
			diagnosticCameraFirstPerson = firstPerson;
			diagnosticCameraFogMode =
				fogMode == null || fogMode.isEmpty() ? "unknown" : fogMode;
			diagnosticCameraDrawDistanceWorldUnits = drawDistanceWorldUnits;
			diagnosticCameraFogStartDistanceWorldUnits = fogStartDistanceWorldUnits;
			diagnosticCameraDrawDistanceTiles = drawDistanceTiles;
			diagnosticCameraFogStartDistanceTiles = fogStartDistanceTiles;
		}
	}

	static void recordWorldGeometryFrame(
		int sourceModels,
		int worldFaces,
		int spriteAnchors,
		int terrainFaces,
		int wallFaces,
		int roofFaces,
		int gameObjectFaces,
		int wallObjectFaces,
		int otherFaces,
		int depthConsideredFaces,
		int depthFaces,
		int depthRejectedFaces,
		int depthTriangles,
		int depthPixelWrites,
		int meshVertices,
		int meshIndices,
		int meshTriangles,
		int meshTexturedTriangles,
		int meshFlatColorTriangles,
		int meshTransparentTriangles,
		int meshSkippedTriangles) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			worldGeometryModelStats.record(sourceModels);
			worldGeometryFaceStats.record(worldFaces);
			worldSpriteAnchorStats.record(spriteAnchors);
			worldGeometryTerrainFaceStats.record(terrainFaces);
			worldGeometryWallFaceStats.record(wallFaces);
			worldGeometryRoofFaceStats.record(roofFaces);
			worldGeometryGameObjectFaceStats.record(gameObjectFaces);
			worldGeometryWallObjectFaceStats.record(wallObjectFaces);
			worldGeometryOtherFaceStats.record(otherFaces);
			worldDepthConsideredFaceStats.record(depthConsideredFaces);
			worldDepthFaceStats.record(depthFaces);
			worldDepthRejectedFaceStats.record(depthRejectedFaces);
			worldDepthTriangleStats.record(depthTriangles);
			worldDepthPixelWriteStats.record(depthPixelWrites);
			worldMeshVertexStats.record(meshVertices);
			worldMeshIndexStats.record(meshIndices);
			worldMeshTriangleStats.record(meshTriangles);
			worldMeshTexturedTriangleStats.record(meshTexturedTriangles);
			worldMeshFlatColorTriangleStats.record(meshFlatColorTriangles);
			worldMeshTransparentTriangleStats.record(meshTransparentTriangles);
			worldMeshSkippedTriangleStats.record(meshSkippedTriangles);
		}
	}

	static void recordOpenGLWorldMeshFrame(int vertices, int indices, int triangles) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLWorldMeshVertexStats.record(vertices);
			openGLWorldMeshIndexStats.record(indices);
			openGLWorldMeshTriangleStats.record(triangles);
		}
	}

	static void recordOpenGLWorldMeshUpload(boolean reused) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLWorldMeshUploadStats.record(reused ? 0 : 1);
			openGLWorldMeshReuseStats.record(reused ? 1 : 0);
		}
	}

	static void recordOpenGLWorldMeshDraw(int triangles, int occluderTriangles, int batches, int drawCalls) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLWorldMeshDrawTriangleStats.record(triangles);
			openGLWorldMeshDrawOccluderTriangleStats.record(occluderTriangles);
			openGLWorldMeshDrawBatchStats.record(batches);
			openGLWorldMeshDrawCallStats.record(drawCalls);
		}
	}

	static void recordOpenGLWorldChunkFrame(int chunks, int vertices, int indices, int triangles) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLWorldChunkStats.record(chunks);
			openGLWorldChunkVertexStats.record(vertices);
			openGLWorldChunkIndexStats.record(indices);
			openGLWorldChunkTriangleStats.record(triangles);
		}
	}

	static void recordOpenGLWorldMaterialFamilies(Renderer3DWorldChunkFrame chunkFrame) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLWorldMaterialUnclassifiedStats.record(
				familyCount(chunkFrame, Renderer3DMaterialFamily.UNCLASSIFIED));
			openGLWorldMaterialTerrainStats.record(familyCount(chunkFrame, Renderer3DMaterialFamily.TERRAIN));
			openGLWorldMaterialWaterStats.record(familyCount(chunkFrame, Renderer3DMaterialFamily.WATER));
			openGLWorldMaterialWallStats.record(familyCount(chunkFrame, Renderer3DMaterialFamily.WALL));
			openGLWorldMaterialRoofStats.record(familyCount(chunkFrame, Renderer3DMaterialFamily.ROOF));
			openGLWorldMaterialSceneryStats.record(familyCount(chunkFrame, Renderer3DMaterialFamily.SCENERY));
			openGLWorldMaterialFoliageStats.record(familyCount(chunkFrame, Renderer3DMaterialFamily.FOLIAGE));
			openGLWorldMaterialOreStats.record(familyCount(chunkFrame, Renderer3DMaterialFamily.ORE));
			openGLWorldMaterialEmissiveStats.record(familyCount(chunkFrame, Renderer3DMaterialFamily.EMISSIVE));
			openGLWorldMaterialEffectStats.record(familyCount(chunkFrame, Renderer3DMaterialFamily.EFFECT));
		}
	}

	private static int familyCount(
		Renderer3DWorldChunkFrame chunkFrame,
		Renderer3DMaterialFamily family) {
		return chunkFrame == null ? 0 : chunkFrame.getMaterialFamilyTriangleCount(family);
	}

	static void recordOpenGLWorldChunkUpload(
		int requested,
		int uploaded,
		int reused,
		int deferred,
		int evicted,
		String reason,
		long budgetUsedNanos,
		long budgetLimitNanos) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLWorldChunkRequestedStats.record(requested);
			openGLWorldChunkUploadStats.record(uploaded);
			openGLWorldChunkReuseStats.record(reused);
			openGLWorldChunkDeferredStats.record(deferred);
			openGLWorldChunkEvictStats.record(evicted);
			String normalizedReason =
				reason == null || reason.trim().isEmpty() ? "unknown" : reason;
			recordDiagnosticReasonChange(
				"renderer.chunk-upload-reason-change",
				openGLWorldChunkUploadReason,
				normalizedReason);
			openGLWorldChunkUploadReason = normalizedReason;
			openGLWorldChunkUploadBudgetStats.record(budgetUsedNanos);
			openGLWorldChunkUploadBudgetLimitNanos = Math.max(0L, budgetLimitNanos);
		}
	}

	static void recordOpenGLWorldChunkDraw(
		int drawnChunks,
		int drawnTriangles,
		int drawnTerrainTriangles,
		int drawnWallTriangles,
		int drawnRoofTriangles,
		int drawnGameObjectTriangles,
		int drawnWallObjectTriangles,
		int drawnOtherTriangles,
		int fallbackTriangles,
		int skippedTriangles,
		int shadowChunks,
		int shadowIndices,
		int consideredChunks,
		int culledChunks,
		int consideredBatches,
		int drawnBatches,
		int culledBatches,
		int drawCalls,
		int textureBinds) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLWorldChunkConsideredStats.record(consideredChunks);
			openGLWorldChunkDrawStats.record(drawnChunks);
			openGLWorldChunkCulledStats.record(culledChunks);
			openGLWorldChunkBatchConsideredStats.record(consideredBatches);
			openGLWorldChunkBatchDrawStats.record(drawnBatches);
			openGLWorldChunkBatchCulledStats.record(culledBatches);
			openGLWorldChunkDrawTriangleStats.record(drawnTriangles);
			openGLWorldChunkDrawTerrainStats.record(drawnTerrainTriangles);
			openGLWorldChunkDrawWallStats.record(drawnWallTriangles);
			openGLWorldChunkDrawRoofStats.record(drawnRoofTriangles);
			openGLWorldChunkDrawGameObjectStats.record(drawnGameObjectTriangles);
			openGLWorldChunkDrawWallObjectStats.record(drawnWallObjectTriangles);
			openGLWorldChunkDrawOtherStats.record(drawnOtherTriangles);
			openGLWorldChunkDrawFallbackStats.record(fallbackTriangles);
			openGLWorldChunkDrawSkippedStats.record(skippedTriangles);
			openGLWorldChunkDrawCallStats.record(drawCalls);
			openGLWorldChunkTextureBindStats.record(textureBinds);
			openGLWorldChunkShadowChunkStats.record(shadowChunks);
			openGLWorldChunkShadowIndexStats.record(shadowIndices);
		}
	}

	static void recordOpenGLRemasterShadowInventory(
		int receiverChunks,
		int receiverTriangles,
		int totalCasters,
		int wallCasters,
		int gameObjectCasters,
		int wallObjectCasters,
		int outdoorOnlyCasters,
		int clippingCandidates,
		int roofedReceivers,
		int outdoorReceivers,
		int unknownReceivers,
		int roofedCasters,
		int outdoorCasters,
		int unknownCasters,
		int sunlightEligibleCasters,
		int sunlightSuppressedRoofedCasters,
		int sunlightSuppressedUnknownCasters) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLRemasterShadowReceiverChunkStats.record(receiverChunks);
			openGLRemasterShadowReceiverTriangleStats.record(receiverTriangles);
			openGLRemasterShadowCasterStats.record(totalCasters);
			openGLRemasterShadowWallCasterStats.record(wallCasters);
			openGLRemasterShadowGameObjectCasterStats.record(gameObjectCasters);
			openGLRemasterShadowWallObjectCasterStats.record(wallObjectCasters);
			openGLRemasterShadowOutdoorOnlyCasterStats.record(outdoorOnlyCasters);
			openGLRemasterShadowClippingCandidateStats.record(clippingCandidates);
			openGLRemasterShadowRoofedReceiverStats.record(roofedReceivers);
			openGLRemasterShadowOutdoorReceiverStats.record(outdoorReceivers);
			openGLRemasterShadowUnknownReceiverStats.record(unknownReceivers);
			openGLRemasterShadowRoofedCasterStats.record(roofedCasters);
			openGLRemasterShadowOutdoorCasterStats.record(outdoorCasters);
			openGLRemasterShadowUnknownCasterStats.record(unknownCasters);
			openGLRemasterShadowSunlightEligibleCasterStats.record(sunlightEligibleCasters);
			openGLRemasterShadowSunlightSuppressedRoofedCasterStats.record(sunlightSuppressedRoofedCasters);
			openGLRemasterShadowSunlightSuppressedUnknownCasterStats.record(sunlightSuppressedUnknownCasters);
		}
	}

	static void recordOpenGLRemasterShadowMask(
		int width,
		int height,
		int visiblePixels,
		boolean cacheHit,
		boolean rebuilt,
		boolean uploaded,
		boolean uploadSkipped,
		int stripCasters,
		int softSceneryCasters,
		int contactCasters,
		String reason,
		long buildNanos,
		long uploadNanos) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLRemasterShadowMaskWidthStats.record(width);
			openGLRemasterShadowMaskHeightStats.record(height);
			openGLRemasterShadowMaskVisiblePixelStats.record(visiblePixels);
			openGLRemasterShadowMaskCacheHitStats.record(cacheHit ? 1 : 0);
			openGLRemasterShadowMaskRebuildStats.record(rebuilt ? 1 : 0);
			openGLRemasterShadowMaskUploadCountStats.record(uploaded ? 1 : 0);
			openGLRemasterShadowMaskUploadSkipStats.record(uploadSkipped ? 1 : 0);
			openGLRemasterShadowMaskStripCasterStats.record(stripCasters);
			openGLRemasterShadowMaskSoftSceneryCasterStats.record(softSceneryCasters);
			openGLRemasterShadowMaskContactCasterStats.record(contactCasters);
			String normalizedReason =
				reason == null || reason.trim().isEmpty() ? "unknown" : reason;
			recordDiagnosticReasonChange(
				"renderer.shadow-mask-reason-change",
				openGLRemasterShadowMaskReason,
				normalizedReason);
			openGLRemasterShadowMaskReason = normalizedReason;
			openGLRemasterShadowMaskBuildStats.record(buildNanos);
			openGLRemasterShadowMaskUploadStats.record(uploadNanos);
		}
	}

	static void recordOpenGLResidentChunkReplacement(
		boolean requested,
		boolean active,
		String reason,
		int drawableTerrainBatches,
		int drawableWallBatches,
		int drawableRoofBatches) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLResidentChunkReplacementRequestedStats.record(requested ? 1 : 0);
			openGLResidentChunkReplacementActiveStats.record(active ? 1 : 0);
			openGLResidentChunkReplacementFallbackStats.record(requested && !active ? 1 : 0);
			openGLResidentChunkDrawableTerrainBatchStats.record(drawableTerrainBatches);
			openGLResidentChunkDrawableWallBatchStats.record(drawableWallBatches);
			openGLResidentChunkDrawableRoofBatchStats.record(drawableRoofBatches);
			String normalizedReason =
				reason == null || reason.trim().isEmpty() ? "unknown" : reason;
			recordDiagnosticReasonChange(
				"renderer.resident-chunk-reason-change",
				openGLResidentChunkReplacementReason,
				normalizedReason);
			openGLResidentChunkReplacementReason = normalizedReason;
		}
	}

	private static void recordDiagnosticReasonChange(
		String eventType,
		String previousReason,
		String currentReason) {
		if (currentReason.equals(previousReason)) {
			return;
		}
		RendererDiagnosticSession.recordReasonChange(
			eventType,
			previousReason,
			currentReason,
			frameStats.count);
	}

	static void recordOpenGLWorldTextureFrame(
		int referencedTextures,
		int cachedTextures,
		int uploadedTextures,
		int missingTextures,
		int atlases) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLWorldTextureReferencedStats.record(referencedTextures);
			openGLWorldTextureCachedStats.record(cachedTextures);
			openGLWorldTextureUploadedStats.record(uploadedTextures);
			openGLWorldTextureMissingStats.record(missingTextures);
			openGLWorldTextureAtlasStats.record(atlases);
		}
	}

	static void recordOpenGLWorldSpriteFrame(int anchors, int matched, int drawn) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLWorldSpriteAnchorStats.record(anchors);
			openGLWorldSpriteMatchedStats.record(matched);
			openGLWorldSpriteDrawnStats.record(drawn);
			openGLWorldEntityConsideredStats.record(anchors);
			openGLWorldEntityDrawnStats.record(drawn);
			openGLWorldEntityCulledStats.record(Math.max(0, anchors - drawn));
		}
	}

	static void recordOpenGLWorldSpriteOwnershipFrame(
		int ownerAnchors,
		int legacyFallbacks,
		int unmatched) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLWorldSpriteOwnerAnchorStats.record(ownerAnchors);
			openGLWorldSpriteLegacyFallbackStats.record(legacyFallbacks);
			openGLWorldSpriteUnmatchedStats.record(unmatched);
		}
	}

	static void recordOpenGLWorldSpriteSnapshotFrame(
		int snapshotGroups,
		int snapshotLayers,
		int compatibilityFallbackCommands) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLWorldSpriteSnapshotGroupStats.record(snapshotGroups);
			openGLWorldSpriteSnapshotLayerStats.record(snapshotLayers);
			openGLWorldSpriteSnapshotCompatibilityFallbackStats.record(
				compatibilityFallbackCommands);
		}
	}

	static void recordSetGameImage(
		long totalNanos,
		long sourceCopyNanos,
		long paintImmediateNanos,
		boolean repaintRequested,
		boolean paintImmediateRequested) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			setGameImageStats.record(totalNanos);
			sourceCopyStats.record(sourceCopyNanos);
			paintImmediateStats.record(paintImmediateNanos);
			if (repaintRequested) {
				repaintRequests++;
			}
			if (paintImmediateRequested) {
				paintImmediateRequests++;
			}
		}
	}

	static void recordViewportPaint(
		long totalNanos,
		long scaleNanos,
		boolean nearestScale,
		boolean interpolationScale) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			viewportPaintStats.record(totalNanos);
			viewportScaleStats.record(scaleNanos);
			if (nearestScale) {
				nearestScalePaints++;
			}
			if (interpolationScale) {
				interpolationScalePaints++;
			}
		}
	}

	static void recordSmoothScale(long nanos) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			smoothScaleStats.record(nanos);
		}
	}

	static void recordGpuPresenter(long nanos) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			gpuPresenterStats.record(nanos);
			gpuPresenterPaints++;
		}
	}

	static void recordOpenGLSnapshot(long nanos) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLSnapshotStats.record(nanos);
		}
	}

	static void recordOpenGLFrame(long uploadNanos, long renderNanos) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			long now = System.nanoTime();
			if (lastOpenGLFrameNanos != 0L && now > lastOpenGLFrameNanos) {
				openGLFrameIntervalStats.record(now - lastOpenGLFrameNanos);
			}
			lastOpenGLFrameNanos = now;
			openGLUploadStats.record(uploadNanos);
			openGLRenderStats.record(renderNanos);
			openGLFrames++;
			openGLFramesWindow++;
		}
	}

	static void resetOpenGLFramePacing() {
		synchronized (RenderTelemetry.class) {
			lastOpenGLFrameNanos = 0L;
			openGLFrameIntervalStats.resetRecent();
		}
	}

	public static String observedOpenGLFps(int fallbackFps) {
		synchronized (RenderTelemetry.class) {
			long frameInterval = openGLFrameIntervalStats.recentAverage();
			if (frameInterval <= 0L) {
				frameInterval = openGLRenderStats.recentAverage();
			}
			if (frameInterval <= 0L) {
				return String.valueOf(fallbackFps);
			}
			int observed = (int) Math.round(1_000_000_000.0 / frameInterval);
			return String.valueOf(Math.max(0, Math.min(Math.max(1, fallbackFps), observed)));
		}
	}

	static void recordOpenGLFramePhases(
		long baseNanos,
		long worldNanos,
		long worldSpriteNanos,
		long spriteOverlayNanos,
		long debugOverlayNanos,
		long swapNanos) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLBaseStats.record(baseNanos);
			openGLWorldStats.record(worldNanos);
			openGLWorldSpriteStats.record(worldSpriteNanos);
			openGLSpriteOverlayStats.record(spriteOverlayNanos);
			openGLDebugOverlayStats.record(debugOverlayNanos);
			openGLSwapStats.record(swapNanos);
		}
	}

	static void recordOpenGLWorldPhaseBreakdown(
		long chunkUploadNanos,
		long projectedMeshNanos,
		long chunkDrawNanos) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLWorldChunkUploadPhaseStats.record(chunkUploadNanos);
			openGLWorldProjectedMeshPhaseStats.record(projectedMeshNanos);
			openGLWorldChunkDrawPhaseStats.record(chunkDrawNanos);
		}
	}

	static void recordOpenGLDroppedFrame() {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			openGLDroppedFrames++;
			openGLDroppedFramesWindow++;
		}
	}

	static void recordSpriteCaptureStats(Renderer2DFrame.CaptureStats captureStats) {
		if (!isCollectionEnabled() || captureStats == null) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			spriteCaptureAttemptStats.record(captureStats.getAttempts());
			spriteCaptureAcceptedStats.record(captureStats.getCaptured());
			spriteCaptureReplacedUiStats.record(captureStats.getReplacedUi());
			spriteCaptureUiBaseStats.record(captureStats.getUiBaseCaptured());
			spriteCaptureSkippedAlphaStats.record(captureStats.getSkippedAlpha());
			spriteCaptureSkippedBoundsStats.record(captureStats.getSkippedBounds());
			spriteCaptureSkippedSourceStats.record(captureStats.getSkippedSource());
			spriteCaptureSkippedTransformStats.record(captureStats.getSkippedTransform());
			spriteCaptureSkippedInterlaceStats.record(captureStats.getSkippedInterlace());
			spriteCaptureSkippedOverflowStats.record(captureStats.getSkippedOverflow());
			spriteCaptureSkippedInvalidStats.record(captureStats.getSkippedInvalid());
			textCaptureDrawStats.record(captureStats.getTextDraws());
			textCaptureGlyphStats.record(captureStats.getTextGlyphs());
			textCaptureReplacedUiStats.record(captureStats.getTextReplacedUi());
			textCaptureSceneStats.record(captureStats.getTextScene());
			textCaptureWorldStats.record(captureStats.getTextWorld());
			textCaptureUiStats.record(captureStats.getTextUi());
			textCaptureUnknownStats.record(captureStats.getTextUnknown());
			primitiveCaptureDrawStats.record(captureStats.getPrimitiveDraws());
			primitiveCaptureReplacedUiStats.record(captureStats.getPrimitiveReplacedUi());
			rotatedSpriteDrawStats.record(captureStats.getRotatedSpriteDraws());
			rotatedSpriteCapturedUiStats.record(captureStats.getRotatedSpriteCapturedUi());
			circleDrawStats.record(captureStats.getCircleDraws());
			circleCapturedUiStats.record(captureStats.getCircleCapturedUi());
			nativeUiBlockSpriteStats.record(captureStats.getNativeUiBlockSprite());
			nativeUiBlockTextStats.record(captureStats.getNativeUiBlockText());
			nativeUiBlockPrimitiveStats.record(captureStats.getNativeUiBlockPrimitive());
			nativeUiBlockMinimapStats.record(captureStats.getNativeUiBlockMinimap());
			nativeUiBlockGradientStats.record(captureStats.getNativeUiBlockGradient());
			nativeUiBlockClearStats.record(captureStats.getNativeUiBlockClear());
			nativeUiBlockCircleStats.record(captureStats.getNativeUiBlockCircle());
			nativeUiBlockPixelStats.record(captureStats.getNativeUiBlockPixel());
			nativeUiBaseEligibleStats.record(captureStats.isNativeUiBaseEligible() ? 1 : 0);
		}
	}

	static void recordRenderer2DCommandLimits(Renderer2DFrame.CaptureStats captureStats) {
		if (!isCollectionEnabled() || captureStats == null) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			renderer2DSpriteCommandAttemptStats.record(captureStats.getSpriteCommandAttempts());
			renderer2DSpriteCommandAcceptedStats.record(captureStats.getSpriteCommandsCaptured());
			renderer2DSpriteCommandDroppedStats.record(captureStats.getSpriteCommandsSkippedOverflow());
			renderer2DTextCommandAttemptStats.record(captureStats.getTextCommandAttempts());
			renderer2DTextCommandAcceptedStats.record(captureStats.getTextCommandsCaptured());
			renderer2DTextCommandDroppedStats.record(captureStats.getTextCommandsSkippedOverflow());
			renderer2DPrimitiveCommandAttemptStats.record(captureStats.getPrimitiveCommandAttempts());
			renderer2DPrimitiveCommandAcceptedStats.record(captureStats.getPrimitiveCommandsCaptured());
			renderer2DPrimitiveCommandDroppedStats.record(captureStats.getPrimitiveCommandsSkippedOverflow());
			renderer2DRotatedSpriteCommandAttemptStats.record(captureStats.getRotatedSpriteCommandAttempts());
			renderer2DRotatedSpriteCommandAcceptedStats.record(captureStats.getRotatedSpriteCommandsCaptured());
			renderer2DRotatedSpriteCommandDroppedStats.record(captureStats.getRotatedSpriteCommandsSkippedOverflow());
			renderer2DCircleCommandAttemptStats.record(captureStats.getCircleCommandAttempts());
			renderer2DCircleCommandAcceptedStats.record(captureStats.getCircleCommandsCaptured());
			renderer2DCircleCommandDroppedStats.record(captureStats.getCircleCommandsSkippedOverflow());
		}
	}

	static void recordSpriteOverlayFrame(
		int captured,
		int staticReplayed,
		int visibleReplayed,
		int skippedOrdered,
		int skippedInvisible,
		int skippedAtlasFull,
		int visiblePixels,
		int sceneCommands,
		int worldCommands,
		int uiCommands,
		int unknownCommands,
		int directSceneCommands,
		int directWorldCommands,
		int directUiCommands,
		int directUnknownCommands,
		int visibleSceneCommands,
		int visibleWorldCommands,
		int visibleUiCommands,
		int visibleUnknownCommands) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			spriteOverlayCapturedStats.record(captured);
			spriteOverlayStaticReplayStats.record(staticReplayed);
			spriteOverlayVisibleReplayStats.record(visibleReplayed);
			spriteOverlaySkippedOrderedStats.record(skippedOrdered);
			spriteOverlaySkippedInvisibleStats.record(skippedInvisible);
			spriteOverlaySkippedAtlasFullStats.record(skippedAtlasFull);
			spriteOverlayVisiblePixelStats.record(visiblePixels);
			spriteOverlaySceneCommandStats.record(sceneCommands);
			spriteOverlayWorldCommandStats.record(worldCommands);
			spriteOverlayUiCommandStats.record(uiCommands);
			spriteOverlayUnknownCommandStats.record(unknownCommands);
			spriteOverlayDirectSceneStats.record(directSceneCommands);
			spriteOverlayDirectWorldStats.record(directWorldCommands);
			spriteOverlayDirectUiStats.record(directUiCommands);
			spriteOverlayDirectUnknownStats.record(directUnknownCommands);
			spriteOverlayVisibleSceneStats.record(visibleSceneCommands);
			spriteOverlayVisibleWorldStats.record(visibleWorldCommands);
			spriteOverlayVisibleUiStats.record(visibleUiCommands);
			spriteOverlayVisibleUnknownStats.record(visibleUnknownCommands);
		}
	}

	static void recordLegacySceneSpriteRestore(int commands, int fallbacks, int fallbackPixels) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			legacySceneSpriteRestoreCommandStats.record(commands);
			legacySceneSpriteRestoreFallbackStats.record(fallbacks);
			legacySceneSpriteRestoreFallbackPixelStats.record(fallbackPixels);
		}
	}

	static void recordImageAllocation(String label, int width, int height, int imageType) {
		if (!isCollectionEnabled()) {
			return;
		}

		synchronized (RenderTelemetry.class) {
			AllocationStats stats = allocationStats.get(label);
			if (stats == null) {
				stats = new AllocationStats();
				allocationStats.put(label, stats);
			}
			stats.record(width, height, bytesPerPixel(imageType));
		}
	}

	static Snapshot snapshot() {
		synchronized (RenderTelemetry.class) {
			return new Snapshot(
				isCollectionEnabled(),
				frameStats.count,
				formatMillis(frameStats.average()),
				formatMillis(frameStats.max),
				formatMillis(sceneRenderStats.average()),
				formatMillis(sceneModelRotateStats.average()),
				formatMillis(sceneWorldCullStats.average()),
				formatMillis(sceneDepthExportStats.average()),
				formatMillis(sceneLegacyDrawStats.average()),
				formatMillis(sceneMeshExportStats.average()),
				formatMillis(setGameImageStats.average()),
				formatMillis(sourceCopyStats.average()),
				formatMillis(openGLSnapshotStats.average()),
				formatMillis(openGLUploadStats.average()),
				formatMillis(openGLRenderStats.average()),
				formatMillis(openGLBaseStats.average()),
				formatMillis(openGLWorldStats.average()),
				formatMillis(openGLWorldSpriteStats.average()),
				formatMillis(openGLSpriteOverlayStats.average()),
				formatMillis(openGLDebugOverlayStats.average()),
				formatMillis(openGLSwapStats.average()),
				formatMillis(openGLWorldChunkUploadPhaseStats.average()),
				formatMillis(openGLWorldProjectedMeshPhaseStats.average()),
				formatMillis(openGLWorldChunkDrawPhaseStats.average()),
				openGLFrames,
				openGLDroppedFrames,
				repaintRequests,
				recentFrameSummary(),
				recentOpenGLTimingSummary(),
				recentOpenGLPhaseSummary(),
				recentScenePhaseSummary(),
				recentClientLoopSummary(),
				formatCount(spriteOverlayCapturedStats.average()),
				formatCount(spriteOverlayStaticReplayStats.average()),
				formatCount(spriteOverlayVisibleReplayStats.average()),
				formatCount(spriteOverlaySkippedOrderedStats.average()),
				formatCount(spriteOverlaySkippedInvisibleStats.average()),
				formatCount(spriteOverlaySkippedAtlasFullStats.average()),
				formatCount(spriteOverlayVisiblePixelStats.average()),
				formatCount(spriteOverlaySceneCommandStats.average()),
				formatCount(spriteOverlayWorldCommandStats.average()),
				formatCount(spriteOverlayUiCommandStats.average()),
				formatCount(spriteOverlayUnknownCommandStats.average()),
				formatCount(spriteOverlayDirectSceneStats.average()),
				formatCount(spriteOverlayDirectWorldStats.average()),
				formatCount(spriteOverlayDirectUiStats.average()),
				formatCount(spriteOverlayDirectUnknownStats.average()),
				formatCount(spriteOverlayVisibleSceneStats.average()),
				formatCount(spriteOverlayVisibleWorldStats.average()),
				formatCount(spriteOverlayVisibleUiStats.average()),
				formatCount(spriteOverlayVisibleUnknownStats.average()),
				formatCount(legacySceneSpriteRestoreCommandStats.average()),
				formatCount(legacySceneSpriteRestoreFallbackStats.average()),
				formatCount(legacySceneSpriteRestoreFallbackPixelStats.average()),
				formatCount(spriteCaptureAttemptStats.average()),
				formatCount(spriteCaptureAcceptedStats.average()),
				formatCount(spriteCaptureReplacedUiStats.average()),
				formatCount(spriteCaptureUiBaseStats.average()),
				formatCount(spriteCaptureSkippedAlphaStats.average()),
				formatCount(spriteCaptureSkippedBoundsStats.average()),
				formatCount(spriteCaptureSkippedSourceStats.average()),
				formatCount(spriteCaptureSkippedTransformStats.average()),
				formatCount(spriteCaptureSkippedInterlaceStats.average()),
				formatCount(spriteCaptureSkippedOverflowStats.average()),
				formatCount(spriteCaptureSkippedInvalidStats.average()),
				formatCount(textCaptureDrawStats.average()),
				formatCount(textCaptureGlyphStats.average()),
				formatCount(textCaptureReplacedUiStats.average()),
				formatCount(textCaptureSceneStats.average()),
				formatCount(textCaptureWorldStats.average()),
				formatCount(textCaptureUiStats.average()),
				formatCount(textCaptureUnknownStats.average()),
				formatCount(primitiveCaptureDrawStats.average()),
				formatCount(primitiveCaptureReplacedUiStats.average()),
				formatCount(rotatedSpriteDrawStats.average()),
				formatCount(rotatedSpriteCapturedUiStats.average()),
				formatCount(circleDrawStats.average()),
				formatCount(circleCapturedUiStats.average()),
				formatCount(nativeUiBlockSpriteStats.average()),
				formatCount(nativeUiBlockTextStats.average()),
				formatCount(nativeUiBlockPrimitiveStats.average()),
				formatCount(nativeUiBlockMinimapStats.average()),
				formatCount(nativeUiBlockGradientStats.average()),
				formatCount(nativeUiBlockClearStats.average()),
				formatCount(nativeUiBlockCircleStats.average()),
				formatCount(nativeUiBlockPixelStats.average()),
				formatCount(nativeUiBaseEligibleStats.average()),
				formatCount(worldGeometryModelStats.average()),
				formatCount(worldGeometryFaceStats.average()),
				formatCount(worldSpriteAnchorStats.average()),
				formatCount(worldGeometryTerrainFaceStats.average()),
				formatCount(worldGeometryWallFaceStats.average()),
				formatCount(worldGeometryRoofFaceStats.average()),
				formatCount(worldGeometryGameObjectFaceStats.average()),
				formatCount(worldGeometryWallObjectFaceStats.average()),
				formatCount(worldGeometryOtherFaceStats.average()),
				formatCount(worldDepthFaceStats.average()),
				formatCount(worldDepthTriangleStats.average()),
				formatCount(worldDepthPixelWriteStats.average()),
				formatVisibility(
					worldDepthConsideredFaceStats,
					worldDepthFaceStats,
					worldDepthRejectedFaceStats,
					false),
				formatCount(worldMeshVertexStats.average()),
				formatCount(worldMeshIndexStats.average()),
				formatCount(worldMeshTriangleStats.average()),
				formatCount(openGLWorldMeshVertexStats.average()),
				formatCount(openGLWorldMeshIndexStats.average()),
				formatCount(openGLWorldMeshTriangleStats.average()),
				formatCount(openGLWorldMeshDrawTriangleStats.average()),
				formatCount(openGLWorldMeshDrawOccluderTriangleStats.average()),
				formatCount(openGLWorldMeshDrawBatchStats.average()),
				formatCount(openGLWorldMeshDrawCallStats.average()),
				formatCount(openGLWorldChunkStats.average()),
				formatCount(openGLWorldChunkVertexStats.average()),
				formatCount(openGLWorldChunkIndexStats.average()),
				formatCount(openGLWorldChunkTriangleStats.average()),
				formatCount(openGLWorldChunkRequestedStats.average()),
				formatCount(openGLWorldChunkUploadStats.average()),
				formatCount(openGLWorldChunkReuseStats.average()),
				formatCount(openGLWorldChunkEvictStats.average()),
				openGLWorldChunkUploadReason,
				formatVisibility(
					openGLWorldChunkConsideredStats,
					openGLWorldChunkDrawStats,
					openGLWorldChunkCulledStats,
					false)
					+ " | batch c/d/cull " + formatVisibility(
						openGLWorldChunkBatchConsideredStats,
						openGLWorldChunkBatchDrawStats,
						openGLWorldChunkBatchCulledStats,
						false),
				formatCount(openGLWorldChunkDrawCallStats.average())
					+ "/" + formatCount(openGLWorldChunkTextureBindStats.average()),
				formatCount(openGLWorldChunkDrawStats.average()),
				formatCount(openGLWorldChunkDrawTriangleStats.average()),
				formatCount(openGLWorldChunkDrawTerrainStats.average()),
				formatCount(openGLWorldChunkDrawWallStats.average()),
				formatCount(openGLWorldChunkDrawRoofStats.average()),
				formatCount(openGLWorldChunkDrawGameObjectStats.average()),
				formatCount(openGLWorldChunkDrawWallObjectStats.average()),
				formatCount(openGLWorldChunkDrawOtherStats.average()),
				formatCount(openGLWorldChunkDrawFallbackStats.average()),
				formatCount(openGLWorldChunkDrawSkippedStats.average()),
				formatCount(openGLRemasterShadowReceiverChunkStats.average()),
				formatCount(openGLRemasterShadowReceiverTriangleStats.average()),
				formatCount(openGLRemasterShadowCasterStats.average()),
				formatCount(openGLRemasterShadowWallCasterStats.average()),
				formatCount(openGLRemasterShadowGameObjectCasterStats.average()),
				formatCount(openGLRemasterShadowWallObjectCasterStats.average()),
				formatCount(openGLRemasterShadowOutdoorOnlyCasterStats.average()),
				formatCount(openGLRemasterShadowClippingCandidateStats.average()),
				formatCount(openGLRemasterShadowRoofedReceiverStats.average()),
				formatCount(openGLRemasterShadowOutdoorReceiverStats.average()),
				formatCount(openGLRemasterShadowUnknownReceiverStats.average()),
				formatCount(openGLRemasterShadowRoofedCasterStats.average()),
				formatCount(openGLRemasterShadowOutdoorCasterStats.average()),
				formatCount(openGLRemasterShadowUnknownCasterStats.average()),
				formatCount(openGLRemasterShadowSunlightEligibleCasterStats.average()),
				formatCount(openGLRemasterShadowSunlightSuppressedRoofedCasterStats.average()),
				formatCount(openGLRemasterShadowSunlightSuppressedUnknownCasterStats.average()),
				formatCount(openGLRemasterShadowMaskWidthStats.average())
					+ "x" + formatCount(openGLRemasterShadowMaskHeightStats.average())
					+ "/" + formatCount(openGLRemasterShadowMaskVisiblePixelStats.average()),
				formatMillis(openGLRemasterShadowMaskBuildStats.average())
					+ "/" + formatMillis(openGLRemasterShadowMaskUploadStats.average()),
				formatCount(openGLRemasterShadowMaskCacheHitStats.average())
					+ "/" + formatCount(openGLRemasterShadowMaskRebuildStats.average())
					+ "/" + formatCount(openGLRemasterShadowMaskUploadCountStats.average())
					+ "/" + formatCount(openGLRemasterShadowMaskUploadSkipStats.average()),
				formatCount(openGLRemasterShadowMaskStripCasterStats.average())
					+ "/" + formatCount(openGLRemasterShadowMaskSoftSceneryCasterStats.average())
					+ "/" + formatCount(openGLRemasterShadowMaskContactCasterStats.average()),
				openGLRemasterShadowMaskReason,
				formatCount(openGLResidentChunkReplacementRequestedStats.average()),
				formatCount(openGLResidentChunkReplacementActiveStats.average()),
				formatCount(openGLResidentChunkReplacementFallbackStats.average()),
				openGLResidentChunkReplacementReason,
				formatCount(openGLResidentChunkDrawableTerrainBatchStats.average()),
				formatCount(openGLResidentChunkDrawableWallBatchStats.average()),
				formatCount(openGLResidentChunkDrawableRoofBatchStats.average()),
				formatCount(openGLWorldSpriteAnchorStats.average()),
				formatCount(openGLWorldSpriteMatchedStats.average()),
				formatCount(openGLWorldSpriteDrawnStats.average()),
				formatVisibility(
					openGLWorldEntityConsideredStats,
					openGLWorldEntityDrawnStats,
					openGLWorldEntityCulledStats,
					false),
				allocationSummary());
		}
	}

	private static void printReport(
		boolean slowFrame,
		long lastFrameNanos,
		int sourceWidth,
		int sourceHeight,
		float scalar,
		Object scalingType,
		String framePath) {
		StringBuilder summary = new StringBuilder();
		summary.append("[renderer-v2 telemetry] frames=").append(frameStats.count);
		if (slowFrame) {
			summary.append(" slow-frame");
		}
		summary.append(" source=").append(sourceWidth).append('x').append(sourceHeight);
		summary.append(" scalar=").append(scalar);
		summary.append(" mode=").append(scalingType);
		summary.append(" path=").append(framePath);
		summary.append(" last=").append(formatMillis(lastFrameNanos)).append("ms");
		summary.append(" window=").append(formatMillis(frameStats.windowAverage())).append("ms");
		summary.append(" windowMax=").append(formatMillis(frameStats.windowMax)).append("ms");
		summary.append(" avg=").append(formatMillis(frameStats.average())).append("ms");
		summary.append(" max=").append(formatMillis(frameStats.max)).append("ms");
		System.out.println(summary);

		System.out.println(
			"[renderer-v2 telemetry] draw avg ms: scene=" + formatMillis(sceneRenderStats.average())
				+ " commit=" + formatMillis(commitStats.average())
				+ " resize=" + formatMillis(scalarResizeStats.average())
				+ " copy=" + formatMillis(backingCopyStats.average())
				+ " present=" + formatMillis(presentStats.average()));

		System.out.println(
			"[renderer-v2 telemetry] draw window ms: scene=" + formatMillis(sceneRenderStats.windowAverage())
				+ " commit=" + formatMillis(commitStats.windowAverage())
				+ " resize=" + formatMillis(scalarResizeStats.windowAverage())
				+ " copy=" + formatMillis(backingCopyStats.windowAverage())
				+ " present=" + formatMillis(presentStats.windowAverage()));

		System.out.println(
			"[renderer-v2 telemetry] scene phases avg ms: rotate=" + formatMillis(sceneModelRotateStats.average())
				+ " cull=" + formatMillis(sceneWorldCullStats.average())
				+ " legacy=" + formatMillis(sceneLegacyDrawStats.average())
				+ " depth=" + formatMillis(sceneDepthExportStats.average())
				+ " mesh=" + formatMillis(sceneMeshExportStats.average()));

		System.out.println(
			"[renderer-v2 telemetry] scene phases window ms: rotate=" + formatMillis(sceneModelRotateStats.windowAverage())
				+ " cull=" + formatMillis(sceneWorldCullStats.windowAverage())
				+ " legacy=" + formatMillis(sceneLegacyDrawStats.windowAverage())
				+ " depth=" + formatMillis(sceneDepthExportStats.windowAverage())
				+ " mesh=" + formatMillis(sceneMeshExportStats.windowAverage()));

		System.out.println(
			"[renderer-v2 telemetry] world geometry avg: models=" + formatCount(worldGeometryModelStats.average())
				+ " faces=" + formatCount(worldGeometryFaceStats.average())
				+ " anchors=" + formatCount(worldSpriteAnchorStats.average())
				+ " terrain/wall/roof/obj/wobj/other="
				+ formatCount(worldGeometryTerrainFaceStats.average())
				+ "/" + formatCount(worldGeometryWallFaceStats.average())
				+ "/" + formatCount(worldGeometryRoofFaceStats.average())
				+ "/" + formatCount(worldGeometryGameObjectFaceStats.average())
				+ "/" + formatCount(worldGeometryWallObjectFaceStats.average())
				+ "/" + formatCount(worldGeometryOtherFaceStats.average())
				+ " depth f/t/p=" + formatCount(worldDepthFaceStats.average())
				+ "/" + formatCount(worldDepthTriangleStats.average())
				+ "/" + formatCount(worldDepthPixelWriteStats.average())
				+ " depth c/a/r=" + formatVisibility(
					worldDepthConsideredFaceStats,
					worldDepthFaceStats,
					worldDepthRejectedFaceStats,
					false)
				+ " mesh v/i/t=" + formatCount(worldMeshVertexStats.average())
				+ "/" + formatCount(worldMeshIndexStats.average())
				+ "/" + formatCount(worldMeshTriangleStats.average())
				+ " glmesh v/i/t=" + formatCount(openGLWorldMeshVertexStats.average())
				+ "/" + formatCount(openGLWorldMeshIndexStats.average())
				+ "/" + formatCount(openGLWorldMeshTriangleStats.average())
				+ " glchunks c/v/i/t=" + formatCount(openGLWorldChunkStats.average())
				+ "/" + formatCount(openGLWorldChunkVertexStats.average())
				+ "/" + formatCount(openGLWorldChunkIndexStats.average())
				+ "/" + formatCount(openGLWorldChunkTriangleStats.average())
				+ " chunk req/up/reuse/evict=" + formatCount(openGLWorldChunkRequestedStats.average())
				+ "/" + formatCount(openGLWorldChunkUploadStats.average())
				+ "/" + formatCount(openGLWorldChunkReuseStats.average())
				+ "/" + formatCount(openGLWorldChunkEvictStats.average())
				+ " chunk vis c/d/cull=" + formatVisibility(
					openGLWorldChunkConsideredStats,
					openGLWorldChunkDrawStats,
					openGLWorldChunkCulledStats,
					false)
				+ " batch c/d/cull=" + formatVisibility(
					openGLWorldChunkBatchConsideredStats,
					openGLWorldChunkBatchDrawStats,
					openGLWorldChunkBatchCulledStats,
					false)
				+ " chunk draw c/t=" + formatCount(openGLWorldChunkDrawStats.average())
				+ "/" + formatCount(openGLWorldChunkDrawTriangleStats.average())
				+ " chunk draw t/w/r/go/wo/o=" + formatCount(openGLWorldChunkDrawTerrainStats.average())
				+ "/" + formatCount(openGLWorldChunkDrawWallStats.average())
				+ "/" + formatCount(openGLWorldChunkDrawRoofStats.average())
				+ "/" + formatCount(openGLWorldChunkDrawGameObjectStats.average())
				+ "/" + formatCount(openGLWorldChunkDrawWallObjectStats.average())
				+ "/" + formatCount(openGLWorldChunkDrawOtherStats.average())
				+ " chunk draw fallback/skip=" + formatCount(openGLWorldChunkDrawFallbackStats.average())
				+ "/" + formatCount(openGLWorldChunkDrawSkippedStats.average())
				+ " chunk submit calls/binds=" + formatCount(openGLWorldChunkDrawCallStats.average())
				+ "/" + formatCount(openGLWorldChunkTextureBindStats.average())
				+ " chunk shadow c/i=" + formatCount(openGLWorldChunkShadowChunkStats.average())
				+ "/" + formatCount(openGLWorldChunkShadowIndexStats.average())
				+ " remaster shadow recv c/t=" + formatCount(openGLRemasterShadowReceiverChunkStats.average())
				+ "/" + formatCount(openGLRemasterShadowReceiverTriangleStats.average())
				+ " casters all/w/go/wo/out/clip="
				+ formatCount(openGLRemasterShadowCasterStats.average())
				+ "/" + formatCount(openGLRemasterShadowWallCasterStats.average())
				+ "/" + formatCount(openGLRemasterShadowGameObjectCasterStats.average())
				+ "/" + formatCount(openGLRemasterShadowWallObjectCasterStats.average())
				+ "/" + formatCount(openGLRemasterShadowOutdoorOnlyCasterStats.average())
				+ "/" + formatCount(openGLRemasterShadowClippingCandidateStats.average())
				+ " resident req/active/fallback="
				+ formatCount(openGLResidentChunkReplacementRequestedStats.average())
				+ "/" + formatCount(openGLResidentChunkReplacementActiveStats.average())
				+ "/" + formatCount(openGLResidentChunkReplacementFallbackStats.average())
				+ " resident batches t/w/r="
				+ formatCount(openGLResidentChunkDrawableTerrainBatchStats.average())
				+ "/" + formatCount(openGLResidentChunkDrawableWallBatchStats.average())
				+ "/" + formatCount(openGLResidentChunkDrawableRoofBatchStats.average())
				+ " reason=" + openGLResidentChunkReplacementReason
				+ " uploads/reused=" + formatCount(openGLWorldMeshUploadStats.average())
				+ "/" + formatCount(openGLWorldMeshReuseStats.average()));

		System.out.println(
			"[renderer-v2 telemetry] world geometry window: models=" + formatCount(worldGeometryModelStats.windowAverage())
				+ " faces=" + formatCount(worldGeometryFaceStats.windowAverage())
				+ " anchors=" + formatCount(worldSpriteAnchorStats.windowAverage())
				+ " terrain/wall/roof/obj/wobj/other="
				+ formatCount(worldGeometryTerrainFaceStats.windowAverage())
				+ "/" + formatCount(worldGeometryWallFaceStats.windowAverage())
				+ "/" + formatCount(worldGeometryRoofFaceStats.windowAverage())
				+ "/" + formatCount(worldGeometryGameObjectFaceStats.windowAverage())
				+ "/" + formatCount(worldGeometryWallObjectFaceStats.windowAverage())
				+ "/" + formatCount(worldGeometryOtherFaceStats.windowAverage())
				+ " depth f/t/p=" + formatCount(worldDepthFaceStats.windowAverage())
				+ "/" + formatCount(worldDepthTriangleStats.windowAverage())
				+ "/" + formatCount(worldDepthPixelWriteStats.windowAverage())
				+ " depth c/a/r=" + formatVisibility(
					worldDepthConsideredFaceStats,
					worldDepthFaceStats,
					worldDepthRejectedFaceStats,
					true)
				+ " mesh v/i/t=" + formatCount(worldMeshVertexStats.windowAverage())
				+ "/" + formatCount(worldMeshIndexStats.windowAverage())
				+ "/" + formatCount(worldMeshTriangleStats.windowAverage()));

		System.out.println(
			"[renderer-v2 telemetry] opengl world window: glmesh v/i/t="
				+ formatCount(openGLWorldMeshVertexStats.windowAverage())
				+ "/" + formatCount(openGLWorldMeshIndexStats.windowAverage())
				+ "/" + formatCount(openGLWorldMeshTriangleStats.windowAverage())
				+ " glchunks c/v/i/t=" + formatCount(openGLWorldChunkStats.windowAverage())
				+ "/" + formatCount(openGLWorldChunkVertexStats.windowAverage())
				+ "/" + formatCount(openGLWorldChunkIndexStats.windowAverage())
				+ "/" + formatCount(openGLWorldChunkTriangleStats.windowAverage())
				+ " chunk req/up/reuse/evict=" + formatCount(openGLWorldChunkRequestedStats.windowAverage())
				+ "/" + formatCount(openGLWorldChunkUploadStats.windowAverage())
				+ "/" + formatCount(openGLWorldChunkReuseStats.windowAverage())
				+ "/" + formatCount(openGLWorldChunkEvictStats.windowAverage())
				+ " chunk vis c/d/cull=" + formatVisibility(
					openGLWorldChunkConsideredStats,
					openGLWorldChunkDrawStats,
					openGLWorldChunkCulledStats,
					true)
				+ " batch c/d/cull=" + formatVisibility(
					openGLWorldChunkBatchConsideredStats,
					openGLWorldChunkBatchDrawStats,
					openGLWorldChunkBatchCulledStats,
					true)
				+ " chunk draw c/t=" + formatCount(openGLWorldChunkDrawStats.windowAverage())
				+ "/" + formatCount(openGLWorldChunkDrawTriangleStats.windowAverage())
				+ " chunk draw t/w/r/go/wo/o=" + formatCount(openGLWorldChunkDrawTerrainStats.windowAverage())
				+ "/" + formatCount(openGLWorldChunkDrawWallStats.windowAverage())
				+ "/" + formatCount(openGLWorldChunkDrawRoofStats.windowAverage())
				+ "/" + formatCount(openGLWorldChunkDrawGameObjectStats.windowAverage())
				+ "/" + formatCount(openGLWorldChunkDrawWallObjectStats.windowAverage())
				+ "/" + formatCount(openGLWorldChunkDrawOtherStats.windowAverage())
				+ " fallback/skip=" + formatCount(openGLWorldChunkDrawFallbackStats.windowAverage())
				+ "/" + formatCount(openGLWorldChunkDrawSkippedStats.windowAverage())
				+ " chunk submit calls/binds=" + formatCount(openGLWorldChunkDrawCallStats.windowAverage())
				+ "/" + formatCount(openGLWorldChunkTextureBindStats.windowAverage())
				+ " remaster shadow recv c/t=" + formatCount(openGLRemasterShadowReceiverChunkStats.windowAverage())
				+ "/" + formatCount(openGLRemasterShadowReceiverTriangleStats.windowAverage())
				+ " casters all/w/go/wo/out/clip="
				+ formatCount(openGLRemasterShadowCasterStats.windowAverage())
				+ "/" + formatCount(openGLRemasterShadowWallCasterStats.windowAverage())
				+ "/" + formatCount(openGLRemasterShadowGameObjectCasterStats.windowAverage())
				+ "/" + formatCount(openGLRemasterShadowWallObjectCasterStats.windowAverage())
				+ "/" + formatCount(openGLRemasterShadowOutdoorOnlyCasterStats.windowAverage())
				+ "/" + formatCount(openGLRemasterShadowClippingCandidateStats.windowAverage())
				+ " resident req/active/fallback="
				+ formatCount(openGLResidentChunkReplacementRequestedStats.windowAverage())
				+ "/" + formatCount(openGLResidentChunkReplacementActiveStats.windowAverage())
				+ "/" + formatCount(openGLResidentChunkReplacementFallbackStats.windowAverage())
				+ " batches t/w/r="
				+ formatCount(openGLResidentChunkDrawableTerrainBatchStats.windowAverage())
				+ "/" + formatCount(openGLResidentChunkDrawableWallBatchStats.windowAverage())
				+ "/" + formatCount(openGLResidentChunkDrawableRoofBatchStats.windowAverage())
				+ " reason=" + openGLResidentChunkReplacementReason
				+ " mesh draw tri/occ/b/calls=" + formatCount(openGLWorldMeshDrawTriangleStats.windowAverage())
				+ "/" + formatCount(openGLWorldMeshDrawOccluderTriangleStats.windowAverage())
				+ "/" + formatCount(openGLWorldMeshDrawBatchStats.windowAverage())
				+ "/" + formatCount(openGLWorldMeshDrawCallStats.windowAverage())
				+ " uploads/reused=" + formatCount(openGLWorldMeshUploadStats.windowAverage())
				+ "/" + formatCount(openGLWorldMeshReuseStats.windowAverage()));

		System.out.println(
			"[renderer-v2 telemetry] world mesh materials avg: textured="
				+ formatCount(worldMeshTexturedTriangleStats.average())
				+ " flat=" + formatCount(worldMeshFlatColorTriangleStats.average())
				+ " transparent=" + formatCount(worldMeshTransparentTriangleStats.average())
				+ " skipped=" + formatCount(worldMeshSkippedTriangleStats.average()));

		System.out.println(
			"[renderer-v2 telemetry] resident material families window "
				+ worldMaterialFamilySummary());

		System.out.println(
			"[renderer-v2 telemetry] world mesh materials window: textured="
				+ formatCount(worldMeshTexturedTriangleStats.windowAverage())
				+ " flat=" + formatCount(worldMeshFlatColorTriangleStats.windowAverage())
				+ " transparent=" + formatCount(worldMeshTransparentTriangleStats.windowAverage())
				+ " skipped=" + formatCount(worldMeshSkippedTriangleStats.windowAverage()));

		System.out.println(
			"[renderer-v2 telemetry] opengl world textures avg: referenced="
				+ formatCount(openGLWorldTextureReferencedStats.average())
				+ " cached=" + formatCount(openGLWorldTextureCachedStats.average())
				+ " uploaded=" + formatCount(openGLWorldTextureUploadedStats.average())
				+ " missing=" + formatCount(openGLWorldTextureMissingStats.average())
				+ " atlases=" + formatCount(openGLWorldTextureAtlasStats.average()));

		System.out.println(
			"[renderer-v2 telemetry] opengl world sprites avg: anchors="
				+ formatCount(openGLWorldSpriteAnchorStats.average())
				+ " matched=" + formatCount(openGLWorldSpriteMatchedStats.average())
				+ " drawn=" + formatCount(openGLWorldSpriteDrawnStats.average())
				+ " entity vis c/d/cull=" + formatVisibility(
					openGLWorldEntityConsideredStats,
					openGLWorldEntityDrawnStats,
					openGLWorldEntityCulledStats,
					false));

		System.out.println(
			"[renderer-v2 telemetry] composite sprite ownership avg: exact="
				+ formatCount(openGLWorldSpriteOwnerAnchorStats.average())
				+ " fallback=" + formatCount(openGLWorldSpriteLegacyFallbackStats.average())
				+ " unmatched=" + formatCount(openGLWorldSpriteUnmatchedStats.average()));
		System.out.println(
			"[renderer-v2 telemetry] renderer sprite snapshots avg: groups="
				+ formatCount(openGLWorldSpriteSnapshotGroupStats.average())
				+ " layers=" + formatCount(openGLWorldSpriteSnapshotLayerStats.average())
				+ " compatibilityFallback="
				+ formatCount(openGLWorldSpriteSnapshotCompatibilityFallbackStats.average()));

		System.out.println(
			"[renderer-v2 telemetry] presentation avg ms: setImage=" + formatMillis(setGameImageStats.average())
				+ " sourceCopy=" + formatMillis(sourceCopyStats.average())
				+ " paintImmediate=" + formatMillis(paintImmediateStats.average())
				+ " viewportPaint=" + formatMillis(viewportPaintStats.average())
				+ " viewportScale=" + formatMillis(viewportScaleStats.average()));

		System.out.println(
			"[renderer-v2 telemetry] scale ops avg ms: smooth=" + formatMillis(smoothScaleStats.average())
				+ " gpuPresenter=" + formatMillis(gpuPresenterStats.average())
				+ " nearestPaints=" + nearestScalePaints
				+ " interpolationPaints=" + interpolationScalePaints
				+ " gpuPaints=" + gpuPresenterPaints);

		System.out.println(
			"[renderer-v2 telemetry] opengl avg ms: snapshot=" + formatMillis(openGLSnapshotStats.average())
				+ " upload=" + formatMillis(openGLUploadStats.average())
				+ " render=" + formatMillis(openGLRenderStats.average())
				+ " frames=" + openGLFrames
				+ " dropped=" + openGLDroppedFrames);

		System.out.println(
			"[renderer-v2 telemetry] opengl window ms: snapshot=" + formatMillis(openGLSnapshotStats.windowAverage())
				+ " upload=" + formatMillis(openGLUploadStats.windowAverage())
				+ " render=" + formatMillis(openGLRenderStats.windowAverage())
				+ " frames=" + openGLFramesWindow
				+ " dropped=" + openGLDroppedFramesWindow
				+ " phases b/w/ws/o/db/s=" + formatMillis(openGLBaseStats.windowAverage())
				+ "/" + formatMillis(openGLWorldStats.windowAverage())
				+ "/" + formatMillis(openGLWorldSpriteStats.windowAverage())
				+ "/" + formatMillis(openGLSpriteOverlayStats.windowAverage())
				+ "/" + formatMillis(openGLDebugOverlayStats.windowAverage())
				+ "/" + formatMillis(openGLSwapStats.windowAverage())
				+ " world split chunk/proj/chdraw=" + formatMillis(openGLWorldChunkUploadPhaseStats.windowAverage())
				+ "/" + formatMillis(openGLWorldProjectedMeshPhaseStats.windowAverage())
				+ "/" + formatMillis(openGLWorldChunkDrawPhaseStats.windowAverage()));

		System.out.println(
			"[renderer-v2 telemetry] sprite overlay avg: captured=" + formatCount(spriteOverlayCapturedStats.average())
				+ " static=" + formatCount(spriteOverlayStaticReplayStats.average())
				+ " visible=" + formatCount(spriteOverlayVisibleReplayStats.average())
				+ " skipOrdered=" + formatCount(spriteOverlaySkippedOrderedStats.average())
				+ " skipInvisible=" + formatCount(spriteOverlaySkippedInvisibleStats.average())
				+ " skipAtlasFull=" + formatCount(spriteOverlaySkippedAtlasFullStats.average())
				+ " visiblePixels=" + formatCount(spriteOverlayVisiblePixelStats.average()));

		System.out.println(
			"[renderer-v2 telemetry] sprite capture avg: attempts=" + formatCount(spriteCaptureAttemptStats.average())
				+ " accepted=" + formatCount(spriteCaptureAcceptedStats.average())
				+ " replacedUi=" + formatCount(spriteCaptureReplacedUiStats.average())
				+ " uiBase=" + formatCount(spriteCaptureUiBaseStats.average())
				+ " skipAlpha=" + formatCount(spriteCaptureSkippedAlphaStats.average())
				+ " skipBounds=" + formatCount(spriteCaptureSkippedBoundsStats.average())
				+ " skipSource=" + formatCount(spriteCaptureSkippedSourceStats.average())
				+ " skipTransform=" + formatCount(spriteCaptureSkippedTransformStats.average())
				+ " skipInterlace=" + formatCount(spriteCaptureSkippedInterlaceStats.average())
				+ " skipOverflow=" + formatCount(spriteCaptureSkippedOverflowStats.average())
				+ " skipInvalid=" + formatCount(spriteCaptureSkippedInvalidStats.average()));

		System.out.println(
			"[renderer-v2 telemetry] text capture avg: draws=" + formatCount(textCaptureDrawStats.average())
				+ " glyphs=" + formatCount(textCaptureGlyphStats.average())
				+ " replacedUi=" + formatCount(textCaptureReplacedUiStats.average())
				+ " scene=" + formatCount(textCaptureSceneStats.average())
				+ " world=" + formatCount(textCaptureWorldStats.average())
				+ " ui=" + formatCount(textCaptureUiStats.average())
				+ " unknown=" + formatCount(textCaptureUnknownStats.average()));

		System.out.println(
			"[renderer-v2 telemetry] primitive capture avg: draws=" + formatCount(primitiveCaptureDrawStats.average())
				+ " replacedUi=" + formatCount(primitiveCaptureReplacedUiStats.average())
				+ " baseReady=" + formatCount(nativeUiBaseEligibleStats.average()));

		System.out.println(
			"[renderer-v2 telemetry] native UI base avg: rotated="
				+ formatCount(rotatedSpriteDrawStats.average())
				+ "/" + formatCount(rotatedSpriteCapturedUiStats.average())
				+ " circle=" + formatCount(circleDrawStats.average())
				+ "/" + formatCount(circleCapturedUiStats.average())
				+ " blockers s/t/p/m=" + formatCount(nativeUiBlockSpriteStats.average())
				+ "/" + formatCount(nativeUiBlockTextStats.average())
				+ "/" + formatCount(nativeUiBlockPrimitiveStats.average())
				+ "/" + formatCount(nativeUiBlockMinimapStats.average())
				+ " g/circ/pix/clear=" + formatCount(nativeUiBlockGradientStats.average())
				+ "/" + formatCount(nativeUiBlockCircleStats.average())
				+ "/" + formatCount(nativeUiBlockPixelStats.average())
				+ "/" + formatCount(nativeUiBlockClearStats.average()));

		System.out.println(
			"[renderer-v2 telemetry] 2d command capture avg attempted/accepted/dropped: sprite="
				+ commandLimitAverageSummary(
					renderer2DSpriteCommandAttemptStats,
					renderer2DSpriteCommandAcceptedStats,
					renderer2DSpriteCommandDroppedStats)
				+ " text=" + commandLimitAverageSummary(
					renderer2DTextCommandAttemptStats,
					renderer2DTextCommandAcceptedStats,
					renderer2DTextCommandDroppedStats)
				+ " primitive=" + commandLimitAverageSummary(
					renderer2DPrimitiveCommandAttemptStats,
					renderer2DPrimitiveCommandAcceptedStats,
					renderer2DPrimitiveCommandDroppedStats)
				+ " rotated=" + commandLimitAverageSummary(
					renderer2DRotatedSpriteCommandAttemptStats,
					renderer2DRotatedSpriteCommandAcceptedStats,
					renderer2DRotatedSpriteCommandDroppedStats)
				+ " circle=" + commandLimitAverageSummary(
					renderer2DCircleCommandAttemptStats,
					renderer2DCircleCommandAcceptedStats,
					renderer2DCircleCommandDroppedStats));

		System.out.println(
			"[renderer-v2 telemetry] sprite phases avg: scene=" + formatCount(spriteOverlaySceneCommandStats.average())
				+ " world=" + formatCount(spriteOverlayWorldCommandStats.average())
				+ " ui=" + formatCount(spriteOverlayUiCommandStats.average())
				+ " unknown=" + formatCount(spriteOverlayUnknownCommandStats.average()));

		System.out.println(
			"[renderer-v2 telemetry] sprite direct phases avg: scene=" + formatCount(spriteOverlayDirectSceneStats.average())
				+ " world=" + formatCount(spriteOverlayDirectWorldStats.average())
				+ " ui=" + formatCount(spriteOverlayDirectUiStats.average())
				+ " unknown=" + formatCount(spriteOverlayDirectUnknownStats.average()));

		System.out.println(
			"[renderer-v2 telemetry] sprite visible phases avg: scene=" + formatCount(spriteOverlayVisibleSceneStats.average())
				+ " world=" + formatCount(spriteOverlayVisibleWorldStats.average())
				+ " ui=" + formatCount(spriteOverlayVisibleUiStats.average())
				+ " unknown=" + formatCount(spriteOverlayVisibleUnknownStats.average()));

		System.out.println(
			"[renderer-v2 telemetry] presents: repaint=" + repaintRequests
				+ " paintImmediate=" + paintImmediateRequests
				+ " allocations=" + allocationSummary());
		writeDiagnosticTelemetry(
			slowFrame ? "slow-frame" : "periodic",
			lastFrameNanos,
			sourceWidth,
			sourceHeight,
			scalar,
			scalingType,
			framePath);
		if (slowFrame) {
			RendererDiagnosticSession.Record event =
				RendererDiagnosticSession.newEventRecord("renderer.slow-frame");
			if (event != null) {
				event.number("rendererFrameSequence", frameStats.count);
				event.number("frameNanos", lastFrameNanos);
				event.number("thresholdNanos", SLOW_FRAME_NANOS);
				event.number("sourceWidth", sourceWidth);
				event.number("sourceHeight", sourceHeight);
				RendererDiagnosticSession.writeEventRecord(event);
			}
		}
		resetReportWindow();
	}

	private static void writeDiagnosticTelemetry(
		String trigger,
		long lastFrameNanos,
		int sourceWidth,
		int sourceHeight,
		float scalar,
		Object scalingType,
		String framePath) {
		DiagnosticRuntimeSnapshot runtimeSnapshot = captureDiagnosticRuntimeSnapshot();
		RendererDiagnosticSession.Record record =
			RendererDiagnosticSession.newTelemetryRecord(trigger, frameStats.count);
		if (record == null) {
			return;
		}

		record.number("frame.lastNanos", lastFrameNanos);
		record.number("frame.sourceWidth", sourceWidth);
		record.number("frame.sourceHeight", sourceHeight);
		record.number("frame.scalar", scalar);
		record.string("frame.scalingType", String.valueOf(scalingType));
		record.string("frame.path", framePath);
		record.bool("camera.stateKnown", diagnosticCameraStateKnown);
		record.number("camera.zoomSetting", diagnosticCameraZoomSetting);
		record.number("camera.zoomSettingMin", diagnosticCameraZoomSettingMin);
		record.number("camera.zoomSettingMax", diagnosticCameraZoomSettingMax);
		record.number("camera.baseZoom", diagnosticCameraBaseZoom);
		record.number("camera.effectiveZoom", diagnosticCameraEffectiveZoom);
		record.number("camera.pitch", diagnosticCameraPitch);
		record.number("camera.rotation", diagnosticCameraRotation);
		record.bool("camera.firstPerson", diagnosticCameraFirstPerson);
		record.string("camera.fogMode", diagnosticCameraFogMode);
		record.number(
			"camera.drawDistanceWorldUnits",
			diagnosticCameraDrawDistanceWorldUnits);
		record.number(
			"camera.fogStartDistanceWorldUnits",
			diagnosticCameraFogStartDistanceWorldUnits);
		record.number("camera.drawDistanceTiles", diagnosticCameraDrawDistanceTiles);
		record.number("camera.fogStartDistanceTiles", diagnosticCameraFogStartDistanceTiles);
		record.number("config.reportIntervalFrames", REPORT_INTERVAL);
		record.number("config.slowFrameNanos", SLOW_FRAME_NANOS);
		record.number("config.recentSampleLimit", RECENT_SAMPLE_LIMIT);
		record.number("config.renderer2D.spriteCommandLimit", Renderer2DFrame.SPRITE_COMMAND_LIMIT);
		record.number("config.renderer2D.textCommandLimit", Renderer2DFrame.TEXT_COMMAND_LIMIT);
		record.number("config.renderer2D.primitiveCommandLimit", Renderer2DFrame.PRIMITIVE_COMMAND_LIMIT);
		record.number(
			"config.renderer2D.rotatedSpriteCommandLimit",
			Renderer2DFrame.ROTATED_SPRITE_COMMAND_LIMIT);
		record.number("config.renderer2D.circleCommandLimit", Renderer2DFrame.CIRCLE_COMMAND_LIMIT);

		for (DiagnosticMetric metric : DIAGNOSTIC_METRICS) {
			try {
				Object value = metric.field.get(null);
				if (value instanceof StageStats) {
					appendDiagnosticStage(record, metric.name, (StageStats) value);
				} else if (value instanceof CounterStats) {
					appendDiagnosticCounter(record, metric.name, (CounterStats) value);
				}
			} catch (IllegalAccessException ignored) {
			}
		}

		record.number("presentation.repaintRequests", repaintRequests);
		record.number("presentation.paintImmediateRequests", paintImmediateRequests);
		record.number("presentation.nearestScalePaints", nearestScalePaints);
		record.number("presentation.interpolationScalePaints", interpolationScalePaints);
		record.number("presentation.gpuPresenterPaints", gpuPresenterPaints);
		record.number("openGL.frames.lifetime", openGLFrames);
		record.number("openGL.droppedFrames.lifetime", openGLDroppedFrames);
		record.number("openGL.frames.window", openGLFramesWindow);
		record.number("openGL.droppedFrames.window", openGLDroppedFramesWindow);
		record.number("openGL.lastFrameNanos", lastOpenGLFrameNanos);
		record.number("openGL.chunkUploadBudget.limitNanos", openGLWorldChunkUploadBudgetLimitNanos);
		record.string("openGL.chunkUploadBudget.reason", openGLWorldChunkUploadReason);
		record.string("openGL.remasterShadowMask.reason", openGLRemasterShadowMaskReason);
		record.string("openGL.residentChunkReplacement.reason", openGLResidentChunkReplacementReason);

		for (Map.Entry<String, AllocationStats> entry : allocationStats.entrySet()) {
			String key = "allocation." + diagnosticKey(entry.getKey());
			record.number(key + ".count", entry.getValue().count);
			record.number(key + ".estimatedBytes", entry.getValue().estimatedBytes);
		}

		Runtime runtime = Runtime.getRuntime();
		MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
		MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
		record.number("runtime.uptimeMillis", ManagementFactory.getRuntimeMXBean().getUptime());
		appendDiagnosticMemoryUsage(record, "runtime.heap", heap);
		appendDiagnosticMemoryUsage(record, "runtime.nonHeap", nonHeap);
		record.number("runtime.memory.freeBytes", runtime.freeMemory());
		record.number("runtime.memory.totalBytes", runtime.totalMemory());
		record.number("runtime.memory.maxBytes", runtime.maxMemory());
		record.number("runtime.availableProcessors", runtime.availableProcessors());
		appendDiagnosticRuntimeSnapshot(record, runtimeSnapshot);

		long gcCount = 0L;
		long gcTimeMillis = 0L;
		List<GarbageCollectorMXBean> collectors =
			new ArrayList<>(ManagementFactory.getGarbageCollectorMXBeans());
		Collections.sort(collectors, Comparator.comparing(GarbageCollectorMXBean::getName));
		for (GarbageCollectorMXBean collector : collectors) {
			long collectorCount = collector.getCollectionCount();
			long collectorTime = collector.getCollectionTime();
			String collectorName = collector.getName();
			String collectorKey = "runtime.gcCollector." + diagnosticKey(collectorName);
			record.string(collectorKey + ".name", collectorName);
			record.number(collectorKey + ".collectionCount", collectorCount);
			record.number(collectorKey + ".collectionTimeMillis", collectorTime);
			Long previousCount = diagnosticLastGcCountByName.get(collectorName);
			Long previousTime = diagnosticLastGcTimeByName.get(collectorName);
			record.number(
				collectorKey + ".collectionCountDelta",
				collectorCount < 0L || previousCount == null
					? 0L
					: Math.max(0L, collectorCount - previousCount));
			record.number(
				collectorKey + ".collectionTimeMillisDelta",
				collectorTime < 0L || previousTime == null
					? 0L
					: Math.max(0L, collectorTime - previousTime));
			diagnosticLastGcCountByName.put(collectorName, collectorCount);
			diagnosticLastGcTimeByName.put(collectorName, collectorTime);
			if (collectorCount >= 0L) {
				gcCount += collectorCount;
			}
			if (collectorTime >= 0L) {
				gcTimeMillis += collectorTime;
			}
		}
		record.number("runtime.gc.collectionCount", gcCount);
		record.number("runtime.gc.collectionTimeMillis", gcTimeMillis);
		record.number(
			"runtime.gc.collectionCountDelta",
			diagnosticLastGcCount < 0L ? 0L : Math.max(0L, gcCount - diagnosticLastGcCount));
		record.number(
			"runtime.gc.collectionTimeMillisDelta",
			diagnosticLastGcTimeMillis < 0L
				? 0L
				: Math.max(0L, gcTimeMillis - diagnosticLastGcTimeMillis));
		diagnosticLastGcCount = gcCount;
		diagnosticLastGcTimeMillis = gcTimeMillis;

		List<MemoryPoolMXBean> memoryPools =
			new ArrayList<>(ManagementFactory.getMemoryPoolMXBeans());
		Collections.sort(memoryPools, Comparator.comparing(MemoryPoolMXBean::getName));
		for (MemoryPoolMXBean pool : memoryPools) {
			if (!pool.isValid()) {
				continue;
			}
			String key = "runtime.memoryPool." + diagnosticKey(pool.getName());
			record.string(key + ".name", pool.getName());
			record.string(key + ".type", pool.getType() == MemoryType.HEAP ? "heap" : "non-heap");
			appendDiagnosticMemoryUsage(record, key + ".usage", pool.getUsage());
			appendDiagnosticMemoryUsage(record, key + ".peak", pool.getPeakUsage());
			appendDiagnosticMemoryUsage(record, key + ".collection", pool.getCollectionUsage());
		}

		List<BufferPoolMXBean> bufferPools =
			new ArrayList<>(ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class));
		Collections.sort(bufferPools, Comparator.comparing(BufferPoolMXBean::getName));
		for (BufferPoolMXBean pool : bufferPools) {
			String key = "runtime.bufferPool." + diagnosticKey(pool.getName());
			record.string(key + ".name", pool.getName());
			record.number(key + ".count", pool.getCount());
			record.number(key + ".memoryUsedBytes", pool.getMemoryUsed());
			record.number(key + ".totalCapacityBytes", pool.getTotalCapacity());
		}

		RendererDiagnosticSession.writeTelemetry(record);
	}

	private static DiagnosticRuntimeSnapshot captureDiagnosticRuntimeSnapshot() {
		long sampleNanos = System.nanoTime();
		long sampleWallNanos = diagnosticLastRuntimeSampleNanos < 0L
			? 0L
			: Math.max(0L, sampleNanos - diagnosticLastRuntimeSampleNanos);
		diagnosticLastRuntimeSampleNanos = sampleNanos;

		long processCpuTimeNanos = -1L;
		long freePhysicalMemoryBytes = -1L;
		long totalPhysicalMemoryBytes = -1L;
		long freeSwapBytes = -1L;
		long totalSwapBytes = -1L;
		double processLoad = -1.0D;
		double systemLoad = -1.0D;
		java.lang.management.OperatingSystemMXBean operatingSystem =
			ManagementFactory.getOperatingSystemMXBean();
		if (operatingSystem instanceof com.sun.management.OperatingSystemMXBean) {
			com.sun.management.OperatingSystemMXBean extended =
				(com.sun.management.OperatingSystemMXBean) operatingSystem;
			processCpuTimeNanos = extended.getProcessCpuTime();
			processLoad = extended.getProcessCpuLoad();
			systemLoad = extended.getSystemCpuLoad();
			freePhysicalMemoryBytes = extended.getFreePhysicalMemorySize();
			totalPhysicalMemoryBytes = extended.getTotalPhysicalMemorySize();
			freeSwapBytes = extended.getFreeSwapSpaceSize();
			totalSwapBytes = extended.getTotalSwapSpaceSize();
		}
		long processCpuTimeDeltaNanos =
			processCpuTimeNanos < 0L || diagnosticLastProcessCpuTimeNanos < 0L
				? 0L
				: Math.max(0L, processCpuTimeNanos - diagnosticLastProcessCpuTimeNanos);
		diagnosticLastProcessCpuTimeNanos = processCpuTimeNanos;

		ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
		boolean threadCpuSupported = threadBean.isThreadCpuTimeSupported();
		if (threadCpuSupported && !threadBean.isThreadCpuTimeEnabled()) {
			try {
				threadBean.setThreadCpuTimeEnabled(true);
			} catch (RuntimeException ignored) {
				threadCpuSupported = false;
			}
		}
		com.sun.management.ThreadMXBean allocationBean =
			threadBean instanceof com.sun.management.ThreadMXBean
				? (com.sun.management.ThreadMXBean) threadBean
				: null;
		boolean threadAllocationSupported =
			allocationBean != null && allocationBean.isThreadAllocatedMemorySupported();
		if (threadAllocationSupported && !allocationBean.isThreadAllocatedMemoryEnabled()) {
			try {
				allocationBean.setThreadAllocatedMemoryEnabled(true);
			} catch (RuntimeException ignored) {
				threadAllocationSupported = false;
			}
		}

		long[] threadIds = threadBean.getAllThreadIds();
		ThreadInfo[] threadInfos = threadBean.getThreadInfo(threadIds);
		long[] threadCpuTimes = new long[threadIds.length];
		long[] threadAllocatedBytes = new long[threadIds.length];
		Arrays.fill(threadCpuTimes, -1L);
		Arrays.fill(threadAllocatedBytes, -1L);
		if (threadCpuSupported) {
			if (allocationBean != null) {
				threadCpuTimes = allocationBean.getThreadCpuTime(threadIds);
			} else {
				for (int i = 0; i < threadIds.length; i++) {
					threadCpuTimes[i] = threadBean.getThreadCpuTime(threadIds[i]);
				}
			}
		}
		if (threadAllocationSupported) {
			threadAllocatedBytes = allocationBean.getThreadAllocatedBytes(threadIds);
		}

		Map<Long, RuntimeThreadSample> currentSamples =
			new LinkedHashMap<Long, RuntimeThreadSample>();
		List<RuntimeThreadDelta> threadDeltas = new ArrayList<RuntimeThreadDelta>();
		long javaThreadCpuTimeDeltaNanos = 0L;
		long javaThreadAllocatedBytesDelta = 0L;
		for (int i = 0; i < threadIds.length; i++) {
			ThreadInfo info = threadInfos[i];
			if (info == null) {
				continue;
			}
			RuntimeThreadSample current = new RuntimeThreadSample(
				threadIds[i],
				info.getThreadName(),
				info.getThreadState().name(),
				threadCpuTimes[i],
				threadAllocatedBytes[i]);
			currentSamples.put(threadIds[i], current);
			RuntimeThreadSample previous = diagnosticLastThreadSamples.get(threadIds[i]);
			long cpuDelta = delta(current.cpuTimeNanos, previous == null ? -1L : previous.cpuTimeNanos);
			long allocatedDelta =
				delta(current.allocatedBytes, previous == null ? -1L : previous.allocatedBytes);
			if (cpuDelta >= 0L) {
				javaThreadCpuTimeDeltaNanos += cpuDelta;
			}
			if (allocatedDelta >= 0L) {
				javaThreadAllocatedBytesDelta += allocatedDelta;
			}
			threadDeltas.add(new RuntimeThreadDelta(current, cpuDelta, allocatedDelta));
		}
		diagnosticLastThreadSamples.clear();
		diagnosticLastThreadSamples.putAll(currentSamples);

		return new DiagnosticRuntimeSnapshot(
			sampleWallNanos,
			processCpuTimeNanos,
			processCpuTimeDeltaNanos,
			processLoad,
			systemLoad,
			freePhysicalMemoryBytes,
			totalPhysicalMemoryBytes,
			freeSwapBytes,
			totalSwapBytes,
			threadCpuSupported,
			threadAllocationSupported,
			javaThreadCpuTimeDeltaNanos,
			javaThreadAllocatedBytesDelta,
			threadDeltas);
	}

	private static long delta(long current, long previous) {
		return current < 0L || previous < 0L ? -1L : Math.max(0L, current - previous);
	}

	private static void appendDiagnosticRuntimeSnapshot(
		RendererDiagnosticSession.Record record,
		DiagnosticRuntimeSnapshot snapshot) {
		if (snapshot == null) {
			return;
		}
		record.number("runtime.cpu.sampleWallNanos", snapshot.sampleWallNanos);
		record.number("runtime.cpu.processTimeNanos", snapshot.processCpuTimeNanos);
		record.number("runtime.cpu.processTimeDeltaNanos", snapshot.processCpuTimeDeltaNanos);
		record.number(
			"runtime.cpu.processCoreUtilization",
			rate(snapshot.processCpuTimeDeltaNanos, snapshot.sampleWallNanos));
		record.number("runtime.cpu.processLoad", snapshot.processLoad);
		record.number("runtime.cpu.systemLoad", snapshot.systemLoad);
		record.number("runtime.cpu.javaThreadTimeDeltaNanos", snapshot.javaThreadCpuTimeDeltaNanos);
		record.number(
			"runtime.cpu.unattributedTimeDeltaNanos",
			Math.max(
				0L,
				snapshot.processCpuTimeDeltaNanos - snapshot.javaThreadCpuTimeDeltaNanos));
		record.number("runtime.os.freePhysicalMemoryBytes", snapshot.freePhysicalMemoryBytes);
		record.number("runtime.os.totalPhysicalMemoryBytes", snapshot.totalPhysicalMemoryBytes);
		record.number("runtime.os.freeSwapBytes", snapshot.freeSwapBytes);
		record.number("runtime.os.totalSwapBytes", snapshot.totalSwapBytes);
		record.bool("runtime.thread.cpuSupported", snapshot.threadCpuSupported);
		record.bool("runtime.thread.allocationSupported", snapshot.threadAllocationSupported);
		record.number("runtime.thread.count", snapshot.threadDeltas.size());
		record.number(
			"runtime.allocation.javaThreadBytesDelta",
			snapshot.javaThreadAllocatedBytesDelta);
		record.number(
			"runtime.allocation.javaThreadBytesPerSecond",
			perSecond(snapshot.javaThreadAllocatedBytesDelta, snapshot.sampleWallNanos));

		appendNamedRuntimeThread(
			record,
			"clientLoop",
			"Spoiled Milk Client Loop",
			snapshot.threadDeltas,
			snapshot.sampleWallNanos);
		appendNamedRuntimeThread(
			record,
			"openGLPresenter",
			"Spoiled Milk OpenGL Presenter",
			snapshot.threadDeltas,
			snapshot.sampleWallNanos);
		appendNamedRuntimeThread(
			record,
			"worldPreload",
			"world-sector-preload",
			snapshot.threadDeltas,
			snapshot.sampleWallNanos);
		appendNamedRuntimeThread(
			record,
			"awtEventQueue",
			"AWT-EventQueue-0",
			snapshot.threadDeltas,
			snapshot.sampleWallNanos);

		List<RuntimeThreadDelta> byCpu =
			new ArrayList<RuntimeThreadDelta>(snapshot.threadDeltas);
		Collections.sort(byCpu, new Comparator<RuntimeThreadDelta>() {
			@Override
			public int compare(RuntimeThreadDelta left, RuntimeThreadDelta right) {
				int byDelta = Long.compare(right.cpuTimeDeltaNanos, left.cpuTimeDeltaNanos);
				return byDelta != 0 ? byDelta : left.sample.name.compareTo(right.sample.name);
			}
		});
		appendTopRuntimeThreads(record, "runtime.thread.topCpu", byCpu, snapshot.sampleWallNanos);

		List<RuntimeThreadDelta> byAllocation =
			new ArrayList<RuntimeThreadDelta>(snapshot.threadDeltas);
		Collections.sort(byAllocation, new Comparator<RuntimeThreadDelta>() {
			@Override
			public int compare(RuntimeThreadDelta left, RuntimeThreadDelta right) {
				int byDelta = Long.compare(
					right.allocatedBytesDelta,
					left.allocatedBytesDelta);
				return byDelta != 0 ? byDelta : left.sample.name.compareTo(right.sample.name);
			}
		});
		appendTopRuntimeThreads(
			record,
			"runtime.thread.topAllocation",
			byAllocation,
			snapshot.sampleWallNanos);
	}

	private static void appendNamedRuntimeThread(
		RendererDiagnosticSession.Record record,
		String keyName,
		String threadName,
		List<RuntimeThreadDelta> deltas,
		long sampleWallNanos) {
		for (RuntimeThreadDelta thread : deltas) {
			if (threadName.equals(thread.sample.name)) {
				appendRuntimeThread(
					record,
					"runtime.thread.named." + keyName,
					thread,
					sampleWallNanos);
				return;
			}
		}
		record.bool("runtime.thread.named." + keyName + ".present", false);
	}

	private static void appendTopRuntimeThreads(
		RendererDiagnosticSession.Record record,
		String key,
		List<RuntimeThreadDelta> threads,
		long sampleWallNanos) {
		int count = Math.min(RUNTIME_TOP_THREAD_LIMIT, threads.size());
		record.number(key + ".count", count);
		for (int i = 0; i < count; i++) {
			appendRuntimeThread(record, key + "." + i, threads.get(i), sampleWallNanos);
		}
	}

	private static void appendRuntimeThread(
		RendererDiagnosticSession.Record record,
		String key,
		RuntimeThreadDelta thread,
		long sampleWallNanos) {
		record.bool(key + ".present", true);
		record.number(key + ".id", thread.sample.id);
		record.string(key + ".name", thread.sample.name);
		record.string(key + ".state", thread.sample.state);
		record.number(key + ".cpuTimeNanos", thread.sample.cpuTimeNanos);
		record.number(key + ".cpuTimeDeltaNanos", thread.cpuTimeDeltaNanos);
		record.number(
			key + ".cpuCoreUtilization",
			rate(thread.cpuTimeDeltaNanos, sampleWallNanos));
		record.number(key + ".allocatedBytes", thread.sample.allocatedBytes);
		record.number(key + ".allocatedBytesDelta", thread.allocatedBytesDelta);
		record.number(
			key + ".allocatedBytesPerSecond",
			perSecond(thread.allocatedBytesDelta, sampleWallNanos));
	}

	private static double rate(long value, long duration) {
		return value < 0L || duration <= 0L ? -1.0D : value / (double) duration;
	}

	private static double perSecond(long value, long durationNanos) {
		return value < 0L || durationNanos <= 0L
			? -1.0D
			: value * 1_000_000_000.0D / durationNanos;
	}

	private static void appendDiagnosticMemoryUsage(
		RendererDiagnosticSession.Record record,
		String key,
		MemoryUsage usage) {
		if (usage == null) {
			return;
		}
		record.number(key + ".initBytes", usage.getInit());
		record.number(key + ".usedBytes", usage.getUsed());
		record.number(key + ".committedBytes", usage.getCommitted());
		record.number(key + ".maxBytes", usage.getMax());
	}

	static void recordDiagnosticBoundary(String trigger) {
		if (!RendererDiagnosticSession.isEnabled()) {
			return;
		}
		synchronized (RenderTelemetry.class) {
			writeDiagnosticTelemetry(
				trigger,
				diagnosticLastFrameNanos,
				diagnosticLastSourceWidth,
				diagnosticLastSourceHeight,
				diagnosticLastScalar,
				diagnosticLastScalingType,
				diagnosticLastFramePath);
		}
	}

	static void recordPerformancePhaseBoundary(String action) {
		if (!RendererDiagnosticSession.isEnabled()) {
			return;
		}
		synchronized (RenderTelemetry.class) {
			writeDiagnosticTelemetry(
				"performance-phase-" + action,
				diagnosticLastFrameNanos,
				diagnosticLastSourceWidth,
				diagnosticLastSourceHeight,
				diagnosticLastScalar,
				diagnosticLastScalingType,
				diagnosticLastFramePath);
			if ("start".equals(action) || "stop".equals(action)) {
				resetReportWindow();
			}
		}
	}

	static long currentFrameSequence() {
		synchronized (RenderTelemetry.class) {
			return frameStats.count;
		}
	}

	static void appendMovementCorrelation(
		RendererDiagnosticSession.Record record,
		long movementSampleNanos) {
		if (record == null) {
			return;
		}
		synchronized (RenderTelemetry.class) {
			record.number("frame.rendererFrameSequence", frameStats.count);
			record.number("frame.clientLoopSequence", clientLoopStats.count);
			record.number("frame.clientLoopLatestNanos", clientLoopStats.latest());
			record.number("frame.clientLoopRecentAverageNanos", clientLoopStats.recentAverage());
			record.number("frame.clientLoopRecentMaxNanos", clientLoopStats.recentMax());
			record.number("frame.renderLatestNanos", frameStats.latest());
			record.number("frame.renderRecentAverageNanos", frameStats.recentAverage());
			record.number("frame.renderRecentMaxNanos", frameStats.recentMax());
			record.number("frame.openGLRenderLatestNanos", openGLRenderStats.latest());
			record.number(
				"frame.clientLoopSampleAgeNanos",
				lastClientLoopSampleNanos == 0L
					? -1L
					: Math.max(0L, movementSampleNanos - lastClientLoopSampleNanos));
		}
	}

	private static void appendDiagnosticStage(
		RendererDiagnosticSession.Record record,
		String name,
		StageStats stats) {
		String key = "stage." + name;
		StagePercentiles percentiles = stats.windowPercentiles();
		record.number(key + ".lifetime.count", stats.count);
		record.number(key + ".lifetime.totalNanos", stats.total);
		record.number(key + ".lifetime.averageNanos", stats.average());
		record.number(key + ".lifetime.maxNanos", stats.max);
		record.number(key + ".window.count", stats.windowCount);
		record.number(key + ".window.totalNanos", stats.windowTotal);
		record.number(key + ".window.averageNanos", stats.windowAverage());
		record.number(key + ".window.maxNanos", stats.windowMax);
		record.number(key + ".window.retainedSampleCount", percentiles.sampleCount);
		record.number(
			key + ".window.droppedSampleCount",
			Math.max(0L, stats.windowCount - percentiles.sampleCount));
		record.number(key + ".window.p50Nanos", percentiles.p50Nanos);
		record.number(key + ".window.p95Nanos", percentiles.p95Nanos);
		record.number(key + ".window.p99Nanos", percentiles.p99Nanos);
		if (hasRawFrameDistribution(name)) {
			record.numbers(key + ".window.samplesNanos", percentiles.samplesNanos);
		}
		record.number(key + ".recent.count", stats.recentCount);
		record.number(key + ".recent.averageNanos", stats.recentAverage());
		record.number(key + ".recent.latestNanos", stats.latest());
	}

	private static boolean hasRawFrameDistribution(String name) {
		return "frame".equals(name)
			|| "clientLoop".equals(name)
			|| "clientLoopUpdate".equals(name)
			|| "clientLoopDraw".equals(name)
			|| "sceneRender".equals(name)
			|| "sceneWorldCull".equals(name)
			|| "sceneDepthExport".equals(name)
			|| "sceneLegacyDraw".equals(name)
			|| "openGLRender".equals(name)
			|| "openGLWorld".equals(name)
			|| "openGLWorldChunkUploadPhase".equals(name)
			|| "openGLWorldChunkDrawPhase".equals(name)
			|| "openGLWorldSprite".equals(name)
			|| "openGLSpriteOverlay".equals(name)
			|| "openGLSwap".equals(name);
	}

	private static void appendDiagnosticCounter(
		RendererDiagnosticSession.Record record,
		String name,
		CounterStats stats) {
		String key = "counter." + name;
		record.number(key + ".lifetime.count", stats.count);
		record.number(key + ".lifetime.total", stats.total);
		record.number(key + ".lifetime.average", stats.average());
		record.number(key + ".lifetime.max", stats.max);
		record.number(key + ".window.count", stats.windowCount);
		record.number(key + ".window.total", stats.windowTotal);
		record.number(key + ".window.average", stats.windowAverage());
		record.number(key + ".window.max", stats.windowMax);
		record.number(key + ".recent.count", stats.recentCount);
		record.number(key + ".recent.average", stats.recentAverage());
		record.number(key + ".recent.latest", stats.latest());
	}

	private static DiagnosticMetric[] buildDiagnosticMetrics() {
		List<DiagnosticMetric> metrics = new ArrayList<DiagnosticMetric>();
		for (Field field : RenderTelemetry.class.getDeclaredFields()) {
			if (field.getType() != StageStats.class && field.getType() != CounterStats.class) {
				continue;
			}
			field.setAccessible(true);
			String name = field.getName();
			if (name.endsWith("Stats")) {
				name = name.substring(0, name.length() - "Stats".length());
			}
			metrics.add(new DiagnosticMetric(name, field));
		}
		Collections.sort(metrics, Comparator.comparing(metric -> metric.name));
		return metrics.toArray(new DiagnosticMetric[metrics.size()]);
	}

	private static String diagnosticKey(String value) {
		if (value == null || value.isEmpty()) {
			return "unknown";
		}
		StringBuilder key = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			key.append(Character.isLetterOrDigit(ch) || ch == '.' || ch == '-' ? ch : '_');
		}
		return key.toString();
	}

	private static void resetReportWindow() {
		StageStats[] stageStats = {
			frameStats,
			clientLoopStats,
			clientLoopSleepStats,
			clientLoopUpdateStats,
			clientLoopRepositionStats,
			clientLoopDrawStats,
			sceneRenderStats,
			sceneModelRotateStats,
			sceneWorldCullStats,
			sceneDepthExportStats,
			sceneLegacyDrawStats,
			sceneMeshExportStats,
			worldSectionLoadStats,
			worldSectionLoadResetStats,
			worldSectionLoadActivePlaneStats,
			worldSectionLoadUpperPlanesStats,
			worldSectionLoadBridgeStats,
			worldSectionLoadChunkFrameStats,
			worldSectionLoadPreloadStats,
			commitStats,
			scalarResizeStats,
			backingCopyStats,
			presentStats,
			setGameImageStats,
			sourceCopyStats,
			paintImmediateStats,
			viewportPaintStats,
			viewportScaleStats,
			smoothScaleStats,
			gpuPresenterStats,
			openGLSnapshotStats,
			openGLUploadStats,
			openGLRenderStats,
			openGLFrameIntervalStats,
			openGLBaseStats,
			openGLWorldStats,
			openGLWorldSpriteStats,
			openGLWorldChunkUploadBudgetStats,
			openGLWorldChunkUploadPhaseStats,
			openGLWorldProjectedMeshPhaseStats,
			openGLWorldChunkDrawPhaseStats,
			openGLRemasterShadowMaskBuildStats,
			openGLRemasterShadowMaskUploadStats,
			openGLSpriteOverlayStats,
			openGLDebugOverlayStats,
			openGLSwapStats
		};
		for (StageStats stats : stageStats) {
			stats.resetWindow();
		}

		CounterStats[] counterStats = {
			spriteOverlayCapturedStats,
			spriteOverlayStaticReplayStats,
			spriteOverlayVisibleReplayStats,
			spriteOverlaySkippedOrderedStats,
			spriteOverlaySkippedInvisibleStats,
			spriteOverlaySkippedAtlasFullStats,
			spriteOverlayVisiblePixelStats,
			spriteOverlaySceneCommandStats,
			spriteOverlayWorldCommandStats,
			spriteOverlayUiCommandStats,
			spriteOverlayUnknownCommandStats,
			spriteOverlayDirectSceneStats,
			spriteOverlayDirectWorldStats,
			spriteOverlayDirectUiStats,
			spriteOverlayDirectUnknownStats,
			spriteOverlayVisibleSceneStats,
			spriteOverlayVisibleWorldStats,
			spriteOverlayVisibleUiStats,
			spriteOverlayVisibleUnknownStats,
			legacySceneSpriteRestoreCommandStats,
			legacySceneSpriteRestoreFallbackStats,
			legacySceneSpriteRestoreFallbackPixelStats,
			spriteCaptureAttemptStats,
			spriteCaptureAcceptedStats,
			spriteCaptureReplacedUiStats,
			spriteCaptureUiBaseStats,
			spriteCaptureSkippedAlphaStats,
			spriteCaptureSkippedBoundsStats,
			spriteCaptureSkippedSourceStats,
			spriteCaptureSkippedTransformStats,
			spriteCaptureSkippedInterlaceStats,
			spriteCaptureSkippedOverflowStats,
			spriteCaptureSkippedInvalidStats,
			textCaptureDrawStats,
			textCaptureGlyphStats,
			textCaptureReplacedUiStats,
			textCaptureSceneStats,
			textCaptureWorldStats,
			textCaptureUiStats,
			textCaptureUnknownStats,
			primitiveCaptureDrawStats,
			primitiveCaptureReplacedUiStats,
			rotatedSpriteDrawStats,
			rotatedSpriteCapturedUiStats,
			circleDrawStats,
			circleCapturedUiStats,
			nativeUiBlockSpriteStats,
			nativeUiBlockTextStats,
			nativeUiBlockPrimitiveStats,
			nativeUiBlockMinimapStats,
			nativeUiBlockGradientStats,
			nativeUiBlockClearStats,
			nativeUiBlockCircleStats,
			nativeUiBlockPixelStats,
			nativeUiBaseEligibleStats,
			renderer2DSpriteCommandAttemptStats,
			renderer2DSpriteCommandAcceptedStats,
			renderer2DSpriteCommandDroppedStats,
			renderer2DTextCommandAttemptStats,
			renderer2DTextCommandAcceptedStats,
			renderer2DTextCommandDroppedStats,
			renderer2DPrimitiveCommandAttemptStats,
			renderer2DPrimitiveCommandAcceptedStats,
			renderer2DPrimitiveCommandDroppedStats,
			renderer2DRotatedSpriteCommandAttemptStats,
			renderer2DRotatedSpriteCommandAcceptedStats,
			renderer2DRotatedSpriteCommandDroppedStats,
			renderer2DCircleCommandAttemptStats,
			renderer2DCircleCommandAcceptedStats,
			renderer2DCircleCommandDroppedStats,
			clientLoopUpdateCountStats,
			clientLoopSkippedDrawStats,
			clientLoopSleepRequestStats,
			clientLoopStepSizeStats,
			worldGeometryModelStats,
			worldGeometryFaceStats,
			worldSpriteAnchorStats,
			worldGeometryTerrainFaceStats,
			worldGeometryWallFaceStats,
			worldGeometryRoofFaceStats,
			worldGeometryGameObjectFaceStats,
			worldGeometryWallObjectFaceStats,
			worldGeometryOtherFaceStats,
			worldDepthConsideredFaceStats,
			worldDepthFaceStats,
			worldDepthRejectedFaceStats,
			worldDepthTriangleStats,
			worldDepthPixelWriteStats,
			worldMeshVertexStats,
			worldMeshIndexStats,
			worldMeshTriangleStats,
			worldMeshTexturedTriangleStats,
			worldMeshFlatColorTriangleStats,
			worldMeshTransparentTriangleStats,
			worldMeshSkippedTriangleStats,
			openGLWorldMeshVertexStats,
			openGLWorldMeshIndexStats,
			openGLWorldMeshTriangleStats,
			openGLWorldMeshUploadStats,
			openGLWorldMeshReuseStats,
			openGLWorldMeshDrawTriangleStats,
			openGLWorldMeshDrawOccluderTriangleStats,
			openGLWorldMeshDrawBatchStats,
			openGLWorldMeshDrawCallStats,
			openGLWorldChunkStats,
			openGLWorldChunkVertexStats,
			openGLWorldChunkIndexStats,
			openGLWorldChunkTriangleStats,
			openGLWorldMaterialUnclassifiedStats,
			openGLWorldMaterialTerrainStats,
			openGLWorldMaterialWaterStats,
			openGLWorldMaterialWallStats,
			openGLWorldMaterialRoofStats,
			openGLWorldMaterialSceneryStats,
			openGLWorldMaterialFoliageStats,
			openGLWorldMaterialOreStats,
			openGLWorldMaterialEmissiveStats,
			openGLWorldMaterialEffectStats,
			openGLWorldChunkRequestedStats,
			openGLWorldChunkUploadStats,
			openGLWorldChunkReuseStats,
			openGLWorldChunkDeferredStats,
			openGLWorldChunkEvictStats,
			openGLWorldChunkConsideredStats,
			openGLWorldChunkDrawStats,
			openGLWorldChunkCulledStats,
			openGLWorldChunkBatchConsideredStats,
			openGLWorldChunkBatchDrawStats,
			openGLWorldChunkBatchCulledStats,
			openGLWorldChunkDrawTriangleStats,
			openGLWorldChunkDrawTerrainStats,
			openGLWorldChunkDrawWallStats,
			openGLWorldChunkDrawRoofStats,
			openGLWorldChunkDrawGameObjectStats,
			openGLWorldChunkDrawWallObjectStats,
			openGLWorldChunkDrawOtherStats,
			openGLWorldChunkDrawFallbackStats,
			openGLWorldChunkDrawSkippedStats,
			openGLWorldChunkDrawCallStats,
			openGLWorldChunkTextureBindStats,
			openGLWorldChunkShadowChunkStats,
			openGLWorldChunkShadowIndexStats,
			openGLRemasterShadowReceiverChunkStats,
			openGLRemasterShadowReceiverTriangleStats,
			openGLRemasterShadowCasterStats,
			openGLRemasterShadowWallCasterStats,
			openGLRemasterShadowGameObjectCasterStats,
			openGLRemasterShadowWallObjectCasterStats,
			openGLRemasterShadowOutdoorOnlyCasterStats,
			openGLRemasterShadowClippingCandidateStats,
			openGLRemasterShadowRoofedReceiverStats,
			openGLRemasterShadowOutdoorReceiverStats,
			openGLRemasterShadowUnknownReceiverStats,
			openGLRemasterShadowRoofedCasterStats,
			openGLRemasterShadowOutdoorCasterStats,
			openGLRemasterShadowUnknownCasterStats,
			openGLRemasterShadowSunlightEligibleCasterStats,
			openGLRemasterShadowSunlightSuppressedRoofedCasterStats,
			openGLRemasterShadowSunlightSuppressedUnknownCasterStats,
			openGLRemasterShadowMaskWidthStats,
			openGLRemasterShadowMaskHeightStats,
			openGLRemasterShadowMaskVisiblePixelStats,
			openGLRemasterShadowMaskCacheHitStats,
			openGLRemasterShadowMaskRebuildStats,
			openGLRemasterShadowMaskUploadCountStats,
			openGLRemasterShadowMaskUploadSkipStats,
			openGLRemasterShadowMaskStripCasterStats,
			openGLRemasterShadowMaskSoftSceneryCasterStats,
			openGLRemasterShadowMaskContactCasterStats,
			openGLResidentChunkReplacementRequestedStats,
			openGLResidentChunkReplacementActiveStats,
			openGLResidentChunkReplacementFallbackStats,
			openGLResidentChunkDrawableTerrainBatchStats,
			openGLResidentChunkDrawableWallBatchStats,
			openGLResidentChunkDrawableRoofBatchStats,
			openGLWorldTextureReferencedStats,
			openGLWorldTextureCachedStats,
			openGLWorldTextureUploadedStats,
			openGLWorldTextureMissingStats,
			openGLWorldTextureAtlasStats,
			openGLWorldSpriteAnchorStats,
			openGLWorldSpriteMatchedStats,
			openGLWorldSpriteDrawnStats,
			openGLWorldSpriteOwnerAnchorStats,
			openGLWorldSpriteLegacyFallbackStats,
			openGLWorldSpriteUnmatchedStats,
			openGLWorldSpriteSnapshotGroupStats,
			openGLWorldSpriteSnapshotLayerStats,
			openGLWorldSpriteSnapshotCompatibilityFallbackStats,
			openGLWorldEntityConsideredStats,
			openGLWorldEntityDrawnStats,
			openGLWorldEntityCulledStats
		};
		for (CounterStats stats : counterStats) {
			stats.resetWindow();
		}
		openGLFramesWindow = 0L;
		openGLDroppedFramesWindow = 0L;
	}

	private static String recentFrameSummary() {
		long frameNanos = frameStats.recentAverage();
		return formatMillis(frameNanos)
			+ "/" + formatMillis(sceneRenderStats.recentAverage())
			+ "/" + formatMillis(openGLRenderStats.recentAverage())
			+ "ms | drawfps " + formatFramesPerSecond(frameNanos);
	}

	private static String recentOpenGLTimingSummary() {
		return formatMillis(openGLSnapshotStats.recentAverage())
			+ "/" + formatMillis(openGLUploadStats.recentAverage())
			+ "/" + formatMillis(openGLRenderStats.recentAverage())
			+ "ms";
	}

	private static String recentOpenGLPhaseSummary() {
		return formatMillis(openGLBaseStats.recentAverage())
			+ "/" + formatMillis(openGLWorldStats.recentAverage())
			+ "/" + formatMillis(openGLWorldSpriteStats.recentAverage())
			+ "/" + formatMillis(openGLSpriteOverlayStats.recentAverage())
			+ "/" + formatMillis(openGLDebugOverlayStats.recentAverage())
			+ "/" + formatMillis(openGLSwapStats.recentAverage())
			+ "ms";
	}

	private static String recentScenePhaseSummary() {
		return formatMillis(sceneModelRotateStats.recentAverage())
			+ "/" + formatMillis(sceneWorldCullStats.recentAverage())
			+ "/" + formatMillis(sceneDepthExportStats.recentAverage())
			+ "/" + formatMillis(sceneLegacyDrawStats.recentAverage())
			+ "/" + formatMillis(sceneMeshExportStats.recentAverage())
			+ "ms";
	}

	private static String recentClientLoopSummary() {
		return formatMillis(clientLoopStats.recentAverage())
			+ "/" + formatMillis(clientLoopSleepStats.recentAverage())
			+ "/" + formatMillis(clientLoopUpdateStats.recentAverage())
			+ "/" + formatMillis(clientLoopRepositionStats.recentAverage())
			+ "/" + formatMillis(clientLoopDrawStats.recentAverage())
			+ "ms | upd/skip/sleep/step "
			+ formatCount(clientLoopUpdateCountStats.recentAverage())
			+ "/" + formatCount(clientLoopSkippedDrawStats.recentAverage())
			+ "/" + formatCount(clientLoopSleepRequestStats.recentAverage())
			+ "/" + formatCount(clientLoopStepSizeStats.recentAverage());
	}

	private static String allocationSummary() {
		if (allocationStats.isEmpty()) {
			return "none";
		}

		StringBuilder summary = new StringBuilder();
		boolean first = true;
		for (Map.Entry<String, AllocationStats> entry : allocationStats.entrySet()) {
			if (!first) {
				summary.append(", ");
			}
			first = false;
			AllocationStats stats = entry.getValue();
			summary.append(entry.getKey())
				.append('=')
				.append(stats.count)
				.append('/')
				.append(formatMegabytes(stats.estimatedBytes))
				.append("MB");
		}
		return summary.toString();
	}

	private static String formatMillis(long nanos) {
		return String.format("%.3f", nanos / 1_000_000.0);
	}

	private static String formatMillisRoot(long nanos) {
		return String.format(
			java.util.Locale.ROOT,
			"%.3f",
			nanos / 1_000_000.0D);
	}

	private static String formatMegabytes(long bytes) {
		return String.format("%.2f", bytes / (1024.0 * 1024.0));
	}

	private static String formatCount(double count) {
		if (count >= 100.0) {
			return String.valueOf(Math.round(count));
		}
		return String.format("%.1f", count);
	}

	private static String formatFramesPerSecond(long averageFrameNanos) {
		if (averageFrameNanos <= 0L) {
			return "0.0";
		}
		return String.format("%.1f", 1_000_000_000.0 / averageFrameNanos);
	}

	private static String formatVisibility(
		CounterStats consideredStats,
		CounterStats drawnStats,
		CounterStats culledStats,
		boolean window) {
		double considered = window ? consideredStats.windowAverage() : consideredStats.average();
		double drawn = window ? drawnStats.windowAverage() : drawnStats.average();
		double culled = window ? culledStats.windowAverage() : culledStats.average();
		return formatCount(considered) + "/" + formatCount(drawn) + "/" + formatCount(culled);
	}

	private static boolean readBoolean(String propertyName, String envName) {
		String value = System.getProperty(propertyName);
		if (value == null || value.trim().isEmpty()) {
			value = System.getenv(envName);
		}
		if (value == null) {
			return false;
		}

		value = value.trim();
		return "true".equalsIgnoreCase(value)
			|| "1".equals(value)
			|| "yes".equalsIgnoreCase(value)
			|| "on".equalsIgnoreCase(value);
	}

	private static int readInt(String propertyName, int defaultValue) {
		String value = System.getProperty(propertyName);
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}

		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static int bytesPerPixel(int imageType) {
		if (imageType == BufferedImage.TYPE_3BYTE_BGR) {
			return 3;
		}
		return 4;
	}

	private static final class StageStats {
		private long count;
		private long total;
		private long max;
		private long windowCount;
		private long windowTotal;
		private long windowMax;
		private final long[] windowSamples = new long[WINDOW_SAMPLE_LIMIT];
		private int windowSampleIndex;
		private int windowSampleCount;
		private final long[] recentSamples = new long[RECENT_SAMPLE_LIMIT];
		private int recentIndex;
		private int recentCount;
		private long recentTotal;

		private void record(long nanos) {
			count++;
			total += nanos;
			if (nanos > max) {
				max = nanos;
			}
			windowCount++;
			windowTotal += nanos;
			if (nanos > windowMax) {
				windowMax = nanos;
			}
			recordWindowSample(nanos);
			recordRecent(nanos);
		}

		private long average() {
			return count == 0L ? 0L : total / count;
		}

		private long windowAverage() {
			return windowCount == 0L ? 0L : windowTotal / windowCount;
		}

		private long recentAverage() {
			return recentCount == 0 ? 0L : recentTotal / recentCount;
		}

		private long latest() {
			if (recentCount == 0) {
				return 0L;
			}
			int latestIndex = (recentIndex - 1 + recentSamples.length) % recentSamples.length;
			return recentSamples[latestIndex];
		}

		private long recentMax() {
			long recentMax = 0L;
			for (int i = 0; i < recentCount; i++) {
				recentMax = Math.max(recentMax, recentSamples[i]);
			}
			return recentMax;
		}

		private void resetWindow() {
			windowCount = 0L;
			windowTotal = 0L;
			windowMax = 0L;
			windowSampleIndex = 0;
			windowSampleCount = 0;
		}

		private void resetRecent() {
			recentIndex = 0;
			recentCount = 0;
			recentTotal = 0L;
		}

		private void recordRecent(long nanos) {
			if (recentCount < recentSamples.length) {
				recentSamples[recentIndex] = nanos;
				recentTotal += nanos;
				recentCount++;
			} else {
				recentTotal -= recentSamples[recentIndex];
				recentSamples[recentIndex] = nanos;
				recentTotal += nanos;
			}
			recentIndex = (recentIndex + 1) % recentSamples.length;
		}

		private void recordWindowSample(long nanos) {
			windowSamples[windowSampleIndex] = nanos;
			windowSampleIndex = (windowSampleIndex + 1) % windowSamples.length;
			if (windowSampleCount < windowSamples.length) {
				windowSampleCount++;
			}
		}

		private StagePercentiles windowPercentiles() {
			long[] samples = new long[windowSampleCount];
			if (windowSampleCount < windowSamples.length) {
				System.arraycopy(windowSamples, 0, samples, 0, windowSampleCount);
			} else {
				int tail = windowSamples.length - windowSampleIndex;
				System.arraycopy(windowSamples, windowSampleIndex, samples, 0, tail);
				System.arraycopy(windowSamples, 0, samples, tail, windowSampleIndex);
			}
			long[] sorted = Arrays.copyOf(samples, samples.length);
			Arrays.sort(sorted);
			return new StagePercentiles(
				samples,
				percentile(sorted, 0.50D),
				percentile(sorted, 0.95D),
				percentile(sorted, 0.99D));
		}

		private long percentile(long[] sorted, double proportion) {
			if (sorted.length == 0) {
				return 0L;
			}
			int index = (int) Math.ceil(proportion * sorted.length) - 1;
			return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
		}
	}

	private static final class StagePercentiles {
		private final long[] samplesNanos;
		private final int sampleCount;
		private final long p50Nanos;
		private final long p95Nanos;
		private final long p99Nanos;

		private StagePercentiles(
			long[] samplesNanos,
			long p50Nanos,
			long p95Nanos,
			long p99Nanos) {
			this.samplesNanos = samplesNanos;
			this.sampleCount = samplesNanos.length;
			this.p50Nanos = p50Nanos;
			this.p95Nanos = p95Nanos;
			this.p99Nanos = p99Nanos;
		}
	}

	private static final class DiagnosticRuntimeSnapshot {
		private final long sampleWallNanos;
		private final long processCpuTimeNanos;
		private final long processCpuTimeDeltaNanos;
		private final double processLoad;
		private final double systemLoad;
		private final long freePhysicalMemoryBytes;
		private final long totalPhysicalMemoryBytes;
		private final long freeSwapBytes;
		private final long totalSwapBytes;
		private final boolean threadCpuSupported;
		private final boolean threadAllocationSupported;
		private final long javaThreadCpuTimeDeltaNanos;
		private final long javaThreadAllocatedBytesDelta;
		private final List<RuntimeThreadDelta> threadDeltas;

		private DiagnosticRuntimeSnapshot(
			long sampleWallNanos,
			long processCpuTimeNanos,
			long processCpuTimeDeltaNanos,
			double processLoad,
			double systemLoad,
			long freePhysicalMemoryBytes,
			long totalPhysicalMemoryBytes,
			long freeSwapBytes,
			long totalSwapBytes,
			boolean threadCpuSupported,
			boolean threadAllocationSupported,
			long javaThreadCpuTimeDeltaNanos,
			long javaThreadAllocatedBytesDelta,
			List<RuntimeThreadDelta> threadDeltas) {
			this.sampleWallNanos = sampleWallNanos;
			this.processCpuTimeNanos = processCpuTimeNanos;
			this.processCpuTimeDeltaNanos = processCpuTimeDeltaNanos;
			this.processLoad = processLoad;
			this.systemLoad = systemLoad;
			this.freePhysicalMemoryBytes = freePhysicalMemoryBytes;
			this.totalPhysicalMemoryBytes = totalPhysicalMemoryBytes;
			this.freeSwapBytes = freeSwapBytes;
			this.totalSwapBytes = totalSwapBytes;
			this.threadCpuSupported = threadCpuSupported;
			this.threadAllocationSupported = threadAllocationSupported;
			this.javaThreadCpuTimeDeltaNanos = javaThreadCpuTimeDeltaNanos;
			this.javaThreadAllocatedBytesDelta = javaThreadAllocatedBytesDelta;
			this.threadDeltas = threadDeltas;
		}
	}

	private static final class RuntimeThreadSample {
		private final long id;
		private final String name;
		private final String state;
		private final long cpuTimeNanos;
		private final long allocatedBytes;

		private RuntimeThreadSample(
			long id,
			String name,
			String state,
			long cpuTimeNanos,
			long allocatedBytes) {
			this.id = id;
			this.name = name;
			this.state = state;
			this.cpuTimeNanos = cpuTimeNanos;
			this.allocatedBytes = allocatedBytes;
		}
	}

	private static final class RuntimeThreadDelta {
		private final RuntimeThreadSample sample;
		private final long cpuTimeDeltaNanos;
		private final long allocatedBytesDelta;

		private RuntimeThreadDelta(
			RuntimeThreadSample sample,
			long cpuTimeDeltaNanos,
			long allocatedBytesDelta) {
			this.sample = sample;
			this.cpuTimeDeltaNanos = cpuTimeDeltaNanos;
			this.allocatedBytesDelta = allocatedBytesDelta;
		}
	}

	private static final class DiagnosticMetric {
		private final String name;
		private final Field field;

		private DiagnosticMetric(String name, Field field) {
			this.name = name;
			this.field = field;
		}
	}

	private static final class AllocationStats {
		private long count;
		private long estimatedBytes;

		private void record(int width, int height, int bytesPerPixel) {
			count++;
			estimatedBytes += (long) width * (long) height * (long) bytesPerPixel;
		}
	}

	private static final class CounterStats {
		private long count;
		private long total;
		private long max;
		private long windowCount;
		private long windowTotal;
		private long windowMax;
		private final int[] recentSamples = new int[RECENT_SAMPLE_LIMIT];
		private int recentIndex;
		private int recentCount;
		private long recentTotal;

		private void record(int value) {
			count++;
			total += value;
			if (value > max) {
				max = value;
			}
			windowCount++;
			windowTotal += value;
			if (value > windowMax) {
				windowMax = value;
			}
			recordRecent(value);
		}

		private double average() {
			return count == 0L ? 0.0 : total / (double) count;
		}

		private double windowAverage() {
			return windowCount == 0L ? 0.0 : windowTotal / (double) windowCount;
		}

		private double recentAverage() {
			return recentCount == 0 ? 0.0 : recentTotal / (double) recentCount;
		}

		private int latest() {
			if (recentCount == 0) {
				return 0;
			}
			int latestIndex = (recentIndex - 1 + recentSamples.length) % recentSamples.length;
			return recentSamples[latestIndex];
		}

		private void resetWindow() {
			windowCount = 0L;
			windowTotal = 0L;
			windowMax = 0L;
		}

		private void recordRecent(int value) {
			if (recentCount < recentSamples.length) {
				recentSamples[recentIndex] = value;
				recentTotal += value;
				recentCount++;
			} else {
				recentTotal -= recentSamples[recentIndex];
				recentSamples[recentIndex] = value;
				recentTotal += value;
			}
			recentIndex = (recentIndex + 1) % recentSamples.length;
		}
	}

	static final class Snapshot {
		final boolean enabled;
		final long frameCount;
		final String frameAverageMs;
		final String frameMaxMs;
		final String sceneAverageMs;
		final String sceneModelRotateAverageMs;
		final String sceneWorldCullAverageMs;
		final String sceneDepthExportAverageMs;
		final String sceneLegacyDrawAverageMs;
		final String sceneMeshExportAverageMs;
		final String setImageAverageMs;
		final String sourceCopyAverageMs;
		final String openGLSnapshotAverageMs;
		final String openGLUploadAverageMs;
		final String openGLRenderAverageMs;
		final String openGLBaseAverageMs;
		final String openGLWorldAverageMs;
		final String openGLWorldSpriteAverageMs;
		final String openGLSpriteOverlayAverageMs;
		final String openGLDebugOverlayAverageMs;
		final String openGLSwapAverageMs;
		final String openGLWorldChunkUploadPhaseAverageMs;
		final String openGLWorldProjectedMeshPhaseAverageMs;
		final String openGLWorldChunkDrawPhaseAverageMs;
		final long openGLFrames;
		final long openGLDroppedFrames;
		final long repaintRequests;
		final String recentFrameSummary;
		final String recentOpenGLTimingSummary;
		final String recentOpenGLPhaseSummary;
		final String recentScenePhaseSummary;
		final String recentClientLoopSummary;
		final String spriteOverlayCapturedAverage;
		final String spriteOverlayStaticReplayAverage;
		final String spriteOverlayVisibleReplayAverage;
		final String spriteOverlaySkippedOrderedAverage;
		final String spriteOverlaySkippedInvisibleAverage;
		final String spriteOverlaySkippedAtlasFullAverage;
		final String spriteOverlayVisiblePixelsAverage;
		final String spriteOverlaySceneCommandAverage;
		final String spriteOverlayWorldCommandAverage;
		final String spriteOverlayUiCommandAverage;
		final String spriteOverlayUnknownCommandAverage;
		final String spriteOverlayDirectSceneAverage;
		final String spriteOverlayDirectWorldAverage;
		final String spriteOverlayDirectUiAverage;
		final String spriteOverlayDirectUnknownAverage;
		final String spriteOverlayVisibleSceneAverage;
		final String spriteOverlayVisibleWorldAverage;
		final String spriteOverlayVisibleUiAverage;
		final String spriteOverlayVisibleUnknownAverage;
		final String legacySceneSpriteRestoreCommandAverage;
		final String legacySceneSpriteRestoreFallbackAverage;
		final String legacySceneSpriteRestoreFallbackPixelAverage;
		final String spriteCaptureAttemptAverage;
		final String spriteCaptureAcceptedAverage;
		final String spriteCaptureReplacedUiAverage;
		final String spriteCaptureUiBaseAverage;
		final String spriteCaptureSkippedAlphaAverage;
		final String spriteCaptureSkippedBoundsAverage;
		final String spriteCaptureSkippedSourceAverage;
		final String spriteCaptureSkippedTransformAverage;
		final String spriteCaptureSkippedInterlaceAverage;
		final String spriteCaptureSkippedOverflowAverage;
		final String spriteCaptureSkippedInvalidAverage;
		final String textCaptureDrawAverage;
		final String textCaptureGlyphAverage;
		final String textCaptureReplacedUiAverage;
		final String textCaptureSceneAverage;
		final String textCaptureWorldAverage;
		final String textCaptureUiAverage;
		final String textCaptureUnknownAverage;
		final String primitiveCaptureDrawAverage;
		final String primitiveCaptureReplacedUiAverage;
		final String rotatedSpriteDrawAverage;
		final String rotatedSpriteCapturedUiAverage;
		final String circleDrawAverage;
		final String circleCapturedUiAverage;
		final String nativeUiBlockSpriteAverage;
		final String nativeUiBlockTextAverage;
		final String nativeUiBlockPrimitiveAverage;
		final String nativeUiBlockMinimapAverage;
		final String nativeUiBlockGradientAverage;
		final String nativeUiBlockClearAverage;
		final String nativeUiBlockCircleAverage;
		final String nativeUiBlockPixelAverage;
		final String nativeUiBaseEligibleAverage;
		final String worldGeometryModelAverage;
		final String worldGeometryFaceAverage;
		final String worldSpriteAnchorAverage;
		final String worldGeometryTerrainFaceAverage;
		final String worldGeometryWallFaceAverage;
		final String worldGeometryRoofFaceAverage;
		final String worldGeometryGameObjectFaceAverage;
		final String worldGeometryWallObjectFaceAverage;
		final String worldGeometryOtherFaceAverage;
		final String worldDepthFaceAverage;
		final String worldDepthTriangleAverage;
		final String worldDepthPixelWriteAverage;
		final String worldDepthCullAverage;
		final String worldMeshVertexAverage;
		final String worldMeshIndexAverage;
		final String worldMeshTriangleAverage;
		final String openGLWorldMeshVertexAverage;
		final String openGLWorldMeshIndexAverage;
		final String openGLWorldMeshTriangleAverage;
		final String openGLWorldMeshDrawTriangleAverage;
		final String openGLWorldMeshDrawOccluderTriangleAverage;
		final String openGLWorldMeshDrawBatchAverage;
		final String openGLWorldMeshDrawCallAverage;
		final String openGLWorldChunkAverage;
		final String openGLWorldChunkVertexAverage;
		final String openGLWorldChunkIndexAverage;
		final String openGLWorldChunkTriangleAverage;
		final String openGLWorldChunkRequestedAverage;
		final String openGLWorldChunkUploadAverage;
		final String openGLWorldChunkReuseAverage;
		final String openGLWorldChunkEvictAverage;
		final String openGLWorldChunkUploadReason;
		final String openGLWorldChunkVisibilityAverage;
		final String openGLWorldChunkSubmitAverage;
		final String openGLWorldChunkDrawAverage;
		final String openGLWorldChunkDrawTriangleAverage;
		final String openGLWorldChunkDrawTerrainAverage;
		final String openGLWorldChunkDrawWallAverage;
		final String openGLWorldChunkDrawRoofAverage;
		final String openGLWorldChunkDrawGameObjectAverage;
		final String openGLWorldChunkDrawWallObjectAverage;
		final String openGLWorldChunkDrawOtherAverage;
		final String openGLWorldChunkDrawFallbackAverage;
		final String openGLWorldChunkDrawSkippedAverage;
		final String openGLRemasterShadowReceiverChunkAverage;
		final String openGLRemasterShadowReceiverTriangleAverage;
		final String openGLRemasterShadowCasterAverage;
		final String openGLRemasterShadowWallCasterAverage;
		final String openGLRemasterShadowGameObjectCasterAverage;
		final String openGLRemasterShadowWallObjectCasterAverage;
		final String openGLRemasterShadowOutdoorOnlyCasterAverage;
		final String openGLRemasterShadowClippingCandidateAverage;
		final String openGLRemasterShadowRoofedReceiverAverage;
		final String openGLRemasterShadowOutdoorReceiverAverage;
		final String openGLRemasterShadowUnknownReceiverAverage;
		final String openGLRemasterShadowRoofedCasterAverage;
		final String openGLRemasterShadowOutdoorCasterAverage;
		final String openGLRemasterShadowUnknownCasterAverage;
		final String openGLRemasterShadowSunlightEligibleCasterAverage;
		final String openGLRemasterShadowSunlightSuppressedRoofedCasterAverage;
		final String openGLRemasterShadowSunlightSuppressedUnknownCasterAverage;
		final String openGLRemasterShadowMaskSizeAverage;
		final String openGLRemasterShadowMaskTimingAverageMs;
		final String openGLRemasterShadowMaskCacheAverage;
		final String openGLRemasterShadowMaskCasterAverage;
		final String openGLRemasterShadowMaskReason;
		final String openGLResidentChunkReplacementRequestedAverage;
		final String openGLResidentChunkReplacementActiveAverage;
		final String openGLResidentChunkReplacementFallbackAverage;
		final String openGLResidentChunkReplacementReason;
		final String openGLResidentChunkDrawableTerrainBatchAverage;
		final String openGLResidentChunkDrawableWallBatchAverage;
		final String openGLResidentChunkDrawableRoofBatchAverage;
		final String openGLWorldSpriteAnchorAverage;
		final String openGLWorldSpriteMatchedAverage;
		final String openGLWorldSpriteDrawnAverage;
		final String openGLWorldEntityVisibilityAverage;
		final String allocations;

		private Snapshot(
			boolean enabled,
			long frameCount,
			String frameAverageMs,
			String frameMaxMs,
			String sceneAverageMs,
			String sceneModelRotateAverageMs,
			String sceneWorldCullAverageMs,
			String sceneDepthExportAverageMs,
			String sceneLegacyDrawAverageMs,
			String sceneMeshExportAverageMs,
			String setImageAverageMs,
			String sourceCopyAverageMs,
			String openGLSnapshotAverageMs,
			String openGLUploadAverageMs,
			String openGLRenderAverageMs,
			String openGLBaseAverageMs,
			String openGLWorldAverageMs,
			String openGLWorldSpriteAverageMs,
			String openGLSpriteOverlayAverageMs,
			String openGLDebugOverlayAverageMs,
			String openGLSwapAverageMs,
			String openGLWorldChunkUploadPhaseAverageMs,
			String openGLWorldProjectedMeshPhaseAverageMs,
			String openGLWorldChunkDrawPhaseAverageMs,
			long openGLFrames,
			long openGLDroppedFrames,
			long repaintRequests,
			String recentFrameSummary,
			String recentOpenGLTimingSummary,
			String recentOpenGLPhaseSummary,
			String recentScenePhaseSummary,
			String recentClientLoopSummary,
			String spriteOverlayCapturedAverage,
			String spriteOverlayStaticReplayAverage,
			String spriteOverlayVisibleReplayAverage,
			String spriteOverlaySkippedOrderedAverage,
			String spriteOverlaySkippedInvisibleAverage,
			String spriteOverlaySkippedAtlasFullAverage,
			String spriteOverlayVisiblePixelsAverage,
			String spriteOverlaySceneCommandAverage,
			String spriteOverlayWorldCommandAverage,
			String spriteOverlayUiCommandAverage,
			String spriteOverlayUnknownCommandAverage,
			String spriteOverlayDirectSceneAverage,
			String spriteOverlayDirectWorldAverage,
			String spriteOverlayDirectUiAverage,
			String spriteOverlayDirectUnknownAverage,
			String spriteOverlayVisibleSceneAverage,
			String spriteOverlayVisibleWorldAverage,
			String spriteOverlayVisibleUiAverage,
			String spriteOverlayVisibleUnknownAverage,
			String legacySceneSpriteRestoreCommandAverage,
			String legacySceneSpriteRestoreFallbackAverage,
			String legacySceneSpriteRestoreFallbackPixelAverage,
			String spriteCaptureAttemptAverage,
			String spriteCaptureAcceptedAverage,
			String spriteCaptureReplacedUiAverage,
			String spriteCaptureUiBaseAverage,
			String spriteCaptureSkippedAlphaAverage,
			String spriteCaptureSkippedBoundsAverage,
			String spriteCaptureSkippedSourceAverage,
			String spriteCaptureSkippedTransformAverage,
			String spriteCaptureSkippedInterlaceAverage,
			String spriteCaptureSkippedOverflowAverage,
			String spriteCaptureSkippedInvalidAverage,
			String textCaptureDrawAverage,
			String textCaptureGlyphAverage,
			String textCaptureReplacedUiAverage,
			String textCaptureSceneAverage,
			String textCaptureWorldAverage,
			String textCaptureUiAverage,
			String textCaptureUnknownAverage,
			String primitiveCaptureDrawAverage,
			String primitiveCaptureReplacedUiAverage,
			String rotatedSpriteDrawAverage,
			String rotatedSpriteCapturedUiAverage,
			String circleDrawAverage,
			String circleCapturedUiAverage,
			String nativeUiBlockSpriteAverage,
			String nativeUiBlockTextAverage,
			String nativeUiBlockPrimitiveAverage,
			String nativeUiBlockMinimapAverage,
			String nativeUiBlockGradientAverage,
			String nativeUiBlockClearAverage,
			String nativeUiBlockCircleAverage,
			String nativeUiBlockPixelAverage,
			String nativeUiBaseEligibleAverage,
			String worldGeometryModelAverage,
			String worldGeometryFaceAverage,
			String worldSpriteAnchorAverage,
			String worldGeometryTerrainFaceAverage,
			String worldGeometryWallFaceAverage,
			String worldGeometryRoofFaceAverage,
			String worldGeometryGameObjectFaceAverage,
			String worldGeometryWallObjectFaceAverage,
			String worldGeometryOtherFaceAverage,
			String worldDepthFaceAverage,
			String worldDepthTriangleAverage,
			String worldDepthPixelWriteAverage,
			String worldDepthCullAverage,
			String worldMeshVertexAverage,
			String worldMeshIndexAverage,
			String worldMeshTriangleAverage,
			String openGLWorldMeshVertexAverage,
			String openGLWorldMeshIndexAverage,
			String openGLWorldMeshTriangleAverage,
			String openGLWorldMeshDrawTriangleAverage,
			String openGLWorldMeshDrawOccluderTriangleAverage,
			String openGLWorldMeshDrawBatchAverage,
			String openGLWorldMeshDrawCallAverage,
			String openGLWorldChunkAverage,
			String openGLWorldChunkVertexAverage,
			String openGLWorldChunkIndexAverage,
			String openGLWorldChunkTriangleAverage,
			String openGLWorldChunkRequestedAverage,
			String openGLWorldChunkUploadAverage,
			String openGLWorldChunkReuseAverage,
			String openGLWorldChunkEvictAverage,
			String openGLWorldChunkUploadReason,
			String openGLWorldChunkVisibilityAverage,
			String openGLWorldChunkSubmitAverage,
			String openGLWorldChunkDrawAverage,
			String openGLWorldChunkDrawTriangleAverage,
			String openGLWorldChunkDrawTerrainAverage,
			String openGLWorldChunkDrawWallAverage,
			String openGLWorldChunkDrawRoofAverage,
			String openGLWorldChunkDrawGameObjectAverage,
			String openGLWorldChunkDrawWallObjectAverage,
			String openGLWorldChunkDrawOtherAverage,
			String openGLWorldChunkDrawFallbackAverage,
			String openGLWorldChunkDrawSkippedAverage,
			String openGLRemasterShadowReceiverChunkAverage,
			String openGLRemasterShadowReceiverTriangleAverage,
			String openGLRemasterShadowCasterAverage,
			String openGLRemasterShadowWallCasterAverage,
			String openGLRemasterShadowGameObjectCasterAverage,
			String openGLRemasterShadowWallObjectCasterAverage,
			String openGLRemasterShadowOutdoorOnlyCasterAverage,
			String openGLRemasterShadowClippingCandidateAverage,
			String openGLRemasterShadowRoofedReceiverAverage,
			String openGLRemasterShadowOutdoorReceiverAverage,
			String openGLRemasterShadowUnknownReceiverAverage,
			String openGLRemasterShadowRoofedCasterAverage,
			String openGLRemasterShadowOutdoorCasterAverage,
			String openGLRemasterShadowUnknownCasterAverage,
			String openGLRemasterShadowSunlightEligibleCasterAverage,
			String openGLRemasterShadowSunlightSuppressedRoofedCasterAverage,
			String openGLRemasterShadowSunlightSuppressedUnknownCasterAverage,
			String openGLRemasterShadowMaskSizeAverage,
			String openGLRemasterShadowMaskTimingAverageMs,
			String openGLRemasterShadowMaskCacheAverage,
			String openGLRemasterShadowMaskCasterAverage,
			String openGLRemasterShadowMaskReason,
			String openGLResidentChunkReplacementRequestedAverage,
			String openGLResidentChunkReplacementActiveAverage,
			String openGLResidentChunkReplacementFallbackAverage,
			String openGLResidentChunkReplacementReason,
			String openGLResidentChunkDrawableTerrainBatchAverage,
			String openGLResidentChunkDrawableWallBatchAverage,
			String openGLResidentChunkDrawableRoofBatchAverage,
			String openGLWorldSpriteAnchorAverage,
			String openGLWorldSpriteMatchedAverage,
			String openGLWorldSpriteDrawnAverage,
			String openGLWorldEntityVisibilityAverage,
			String allocations) {
			this.enabled = enabled;
			this.frameCount = frameCount;
			this.frameAverageMs = frameAverageMs;
			this.frameMaxMs = frameMaxMs;
			this.sceneAverageMs = sceneAverageMs;
			this.sceneModelRotateAverageMs = sceneModelRotateAverageMs;
			this.sceneWorldCullAverageMs = sceneWorldCullAverageMs;
			this.sceneDepthExportAverageMs = sceneDepthExportAverageMs;
			this.sceneLegacyDrawAverageMs = sceneLegacyDrawAverageMs;
			this.sceneMeshExportAverageMs = sceneMeshExportAverageMs;
			this.setImageAverageMs = setImageAverageMs;
			this.sourceCopyAverageMs = sourceCopyAverageMs;
			this.openGLSnapshotAverageMs = openGLSnapshotAverageMs;
			this.openGLUploadAverageMs = openGLUploadAverageMs;
			this.openGLRenderAverageMs = openGLRenderAverageMs;
			this.openGLBaseAverageMs = openGLBaseAverageMs;
			this.openGLWorldAverageMs = openGLWorldAverageMs;
			this.openGLWorldSpriteAverageMs = openGLWorldSpriteAverageMs;
			this.openGLSpriteOverlayAverageMs = openGLSpriteOverlayAverageMs;
			this.openGLDebugOverlayAverageMs = openGLDebugOverlayAverageMs;
			this.openGLSwapAverageMs = openGLSwapAverageMs;
			this.openGLWorldChunkUploadPhaseAverageMs = openGLWorldChunkUploadPhaseAverageMs;
			this.openGLWorldProjectedMeshPhaseAverageMs = openGLWorldProjectedMeshPhaseAverageMs;
			this.openGLWorldChunkDrawPhaseAverageMs = openGLWorldChunkDrawPhaseAverageMs;
			this.openGLFrames = openGLFrames;
			this.openGLDroppedFrames = openGLDroppedFrames;
			this.repaintRequests = repaintRequests;
			this.recentFrameSummary = recentFrameSummary;
			this.recentOpenGLTimingSummary = recentOpenGLTimingSummary;
			this.recentOpenGLPhaseSummary = recentOpenGLPhaseSummary;
			this.recentScenePhaseSummary = recentScenePhaseSummary;
			this.recentClientLoopSummary = recentClientLoopSummary;
			this.spriteOverlayCapturedAverage = spriteOverlayCapturedAverage;
			this.spriteOverlayStaticReplayAverage = spriteOverlayStaticReplayAverage;
			this.spriteOverlayVisibleReplayAverage = spriteOverlayVisibleReplayAverage;
			this.spriteOverlaySkippedOrderedAverage = spriteOverlaySkippedOrderedAverage;
			this.spriteOverlaySkippedInvisibleAverage = spriteOverlaySkippedInvisibleAverage;
			this.spriteOverlaySkippedAtlasFullAverage = spriteOverlaySkippedAtlasFullAverage;
			this.spriteOverlayVisiblePixelsAverage = spriteOverlayVisiblePixelsAverage;
			this.spriteOverlaySceneCommandAverage = spriteOverlaySceneCommandAverage;
			this.spriteOverlayWorldCommandAverage = spriteOverlayWorldCommandAverage;
			this.spriteOverlayUiCommandAverage = spriteOverlayUiCommandAverage;
			this.spriteOverlayUnknownCommandAverage = spriteOverlayUnknownCommandAverage;
			this.spriteOverlayDirectSceneAverage = spriteOverlayDirectSceneAverage;
			this.spriteOverlayDirectWorldAverage = spriteOverlayDirectWorldAverage;
			this.spriteOverlayDirectUiAverage = spriteOverlayDirectUiAverage;
			this.spriteOverlayDirectUnknownAverage = spriteOverlayDirectUnknownAverage;
			this.spriteOverlayVisibleSceneAverage = spriteOverlayVisibleSceneAverage;
			this.spriteOverlayVisibleWorldAverage = spriteOverlayVisibleWorldAverage;
			this.spriteOverlayVisibleUiAverage = spriteOverlayVisibleUiAverage;
			this.spriteOverlayVisibleUnknownAverage = spriteOverlayVisibleUnknownAverage;
			this.legacySceneSpriteRestoreCommandAverage = legacySceneSpriteRestoreCommandAverage;
			this.legacySceneSpriteRestoreFallbackAverage = legacySceneSpriteRestoreFallbackAverage;
			this.legacySceneSpriteRestoreFallbackPixelAverage = legacySceneSpriteRestoreFallbackPixelAverage;
			this.spriteCaptureAttemptAverage = spriteCaptureAttemptAverage;
			this.spriteCaptureAcceptedAverage = spriteCaptureAcceptedAverage;
			this.spriteCaptureReplacedUiAverage = spriteCaptureReplacedUiAverage;
			this.spriteCaptureUiBaseAverage = spriteCaptureUiBaseAverage;
			this.spriteCaptureSkippedAlphaAverage = spriteCaptureSkippedAlphaAverage;
			this.spriteCaptureSkippedBoundsAverage = spriteCaptureSkippedBoundsAverage;
			this.spriteCaptureSkippedSourceAverage = spriteCaptureSkippedSourceAverage;
			this.spriteCaptureSkippedTransformAverage = spriteCaptureSkippedTransformAverage;
			this.spriteCaptureSkippedInterlaceAverage = spriteCaptureSkippedInterlaceAverage;
			this.spriteCaptureSkippedOverflowAverage = spriteCaptureSkippedOverflowAverage;
			this.spriteCaptureSkippedInvalidAverage = spriteCaptureSkippedInvalidAverage;
			this.textCaptureDrawAverage = textCaptureDrawAverage;
			this.textCaptureGlyphAverage = textCaptureGlyphAverage;
			this.textCaptureReplacedUiAverage = textCaptureReplacedUiAverage;
			this.textCaptureSceneAverage = textCaptureSceneAverage;
			this.textCaptureWorldAverage = textCaptureWorldAverage;
			this.textCaptureUiAverage = textCaptureUiAverage;
			this.textCaptureUnknownAverage = textCaptureUnknownAverage;
			this.primitiveCaptureDrawAverage = primitiveCaptureDrawAverage;
			this.primitiveCaptureReplacedUiAverage = primitiveCaptureReplacedUiAverage;
			this.rotatedSpriteDrawAverage = rotatedSpriteDrawAverage;
			this.rotatedSpriteCapturedUiAverage = rotatedSpriteCapturedUiAverage;
			this.circleDrawAverage = circleDrawAverage;
			this.circleCapturedUiAverage = circleCapturedUiAverage;
			this.nativeUiBlockSpriteAverage = nativeUiBlockSpriteAverage;
			this.nativeUiBlockTextAverage = nativeUiBlockTextAverage;
			this.nativeUiBlockPrimitiveAverage = nativeUiBlockPrimitiveAverage;
			this.nativeUiBlockMinimapAverage = nativeUiBlockMinimapAverage;
			this.nativeUiBlockGradientAverage = nativeUiBlockGradientAverage;
			this.nativeUiBlockClearAverage = nativeUiBlockClearAverage;
			this.nativeUiBlockCircleAverage = nativeUiBlockCircleAverage;
			this.nativeUiBlockPixelAverage = nativeUiBlockPixelAverage;
			this.nativeUiBaseEligibleAverage = nativeUiBaseEligibleAverage;
			this.worldGeometryModelAverage = worldGeometryModelAverage;
			this.worldGeometryFaceAverage = worldGeometryFaceAverage;
			this.worldSpriteAnchorAverage = worldSpriteAnchorAverage;
			this.worldGeometryTerrainFaceAverage = worldGeometryTerrainFaceAverage;
			this.worldGeometryWallFaceAverage = worldGeometryWallFaceAverage;
			this.worldGeometryRoofFaceAverage = worldGeometryRoofFaceAverage;
			this.worldGeometryGameObjectFaceAverage = worldGeometryGameObjectFaceAverage;
			this.worldGeometryWallObjectFaceAverage = worldGeometryWallObjectFaceAverage;
			this.worldGeometryOtherFaceAverage = worldGeometryOtherFaceAverage;
			this.worldDepthFaceAverage = worldDepthFaceAverage;
			this.worldDepthTriangleAverage = worldDepthTriangleAverage;
			this.worldDepthPixelWriteAverage = worldDepthPixelWriteAverage;
			this.worldDepthCullAverage = worldDepthCullAverage;
			this.worldMeshVertexAverage = worldMeshVertexAverage;
			this.worldMeshIndexAverage = worldMeshIndexAverage;
			this.worldMeshTriangleAverage = worldMeshTriangleAverage;
			this.openGLWorldMeshVertexAverage = openGLWorldMeshVertexAverage;
			this.openGLWorldMeshIndexAverage = openGLWorldMeshIndexAverage;
			this.openGLWorldMeshTriangleAverage = openGLWorldMeshTriangleAverage;
			this.openGLWorldMeshDrawTriangleAverage = openGLWorldMeshDrawTriangleAverage;
			this.openGLWorldMeshDrawOccluderTriangleAverage = openGLWorldMeshDrawOccluderTriangleAverage;
			this.openGLWorldMeshDrawBatchAverage = openGLWorldMeshDrawBatchAverage;
			this.openGLWorldMeshDrawCallAverage = openGLWorldMeshDrawCallAverage;
			this.openGLWorldChunkAverage = openGLWorldChunkAverage;
			this.openGLWorldChunkVertexAverage = openGLWorldChunkVertexAverage;
			this.openGLWorldChunkIndexAverage = openGLWorldChunkIndexAverage;
			this.openGLWorldChunkTriangleAverage = openGLWorldChunkTriangleAverage;
			this.openGLWorldChunkRequestedAverage = openGLWorldChunkRequestedAverage;
			this.openGLWorldChunkUploadAverage = openGLWorldChunkUploadAverage;
			this.openGLWorldChunkReuseAverage = openGLWorldChunkReuseAverage;
			this.openGLWorldChunkEvictAverage = openGLWorldChunkEvictAverage;
			this.openGLWorldChunkUploadReason = openGLWorldChunkUploadReason;
			this.openGLWorldChunkVisibilityAverage = openGLWorldChunkVisibilityAverage;
			this.openGLWorldChunkSubmitAverage = openGLWorldChunkSubmitAverage;
			this.openGLWorldChunkDrawAverage = openGLWorldChunkDrawAverage;
			this.openGLWorldChunkDrawTriangleAverage = openGLWorldChunkDrawTriangleAverage;
			this.openGLWorldChunkDrawTerrainAverage = openGLWorldChunkDrawTerrainAverage;
			this.openGLWorldChunkDrawWallAverage = openGLWorldChunkDrawWallAverage;
			this.openGLWorldChunkDrawRoofAverage = openGLWorldChunkDrawRoofAverage;
			this.openGLWorldChunkDrawGameObjectAverage = openGLWorldChunkDrawGameObjectAverage;
			this.openGLWorldChunkDrawWallObjectAverage = openGLWorldChunkDrawWallObjectAverage;
			this.openGLWorldChunkDrawOtherAverage = openGLWorldChunkDrawOtherAverage;
			this.openGLWorldChunkDrawFallbackAverage = openGLWorldChunkDrawFallbackAverage;
			this.openGLWorldChunkDrawSkippedAverage = openGLWorldChunkDrawSkippedAverage;
			this.openGLRemasterShadowReceiverChunkAverage = openGLRemasterShadowReceiverChunkAverage;
			this.openGLRemasterShadowReceiverTriangleAverage = openGLRemasterShadowReceiverTriangleAverage;
			this.openGLRemasterShadowCasterAverage = openGLRemasterShadowCasterAverage;
			this.openGLRemasterShadowWallCasterAverage = openGLRemasterShadowWallCasterAverage;
			this.openGLRemasterShadowGameObjectCasterAverage = openGLRemasterShadowGameObjectCasterAverage;
			this.openGLRemasterShadowWallObjectCasterAverage = openGLRemasterShadowWallObjectCasterAverage;
			this.openGLRemasterShadowOutdoorOnlyCasterAverage = openGLRemasterShadowOutdoorOnlyCasterAverage;
			this.openGLRemasterShadowClippingCandidateAverage = openGLRemasterShadowClippingCandidateAverage;
			this.openGLRemasterShadowRoofedReceiverAverage = openGLRemasterShadowRoofedReceiverAverage;
			this.openGLRemasterShadowOutdoorReceiverAverage = openGLRemasterShadowOutdoorReceiverAverage;
			this.openGLRemasterShadowUnknownReceiverAverage = openGLRemasterShadowUnknownReceiverAverage;
			this.openGLRemasterShadowRoofedCasterAverage = openGLRemasterShadowRoofedCasterAverage;
			this.openGLRemasterShadowOutdoorCasterAverage = openGLRemasterShadowOutdoorCasterAverage;
			this.openGLRemasterShadowUnknownCasterAverage = openGLRemasterShadowUnknownCasterAverage;
			this.openGLRemasterShadowSunlightEligibleCasterAverage = openGLRemasterShadowSunlightEligibleCasterAverage;
			this.openGLRemasterShadowSunlightSuppressedRoofedCasterAverage =
				openGLRemasterShadowSunlightSuppressedRoofedCasterAverage;
			this.openGLRemasterShadowSunlightSuppressedUnknownCasterAverage =
				openGLRemasterShadowSunlightSuppressedUnknownCasterAverage;
			this.openGLRemasterShadowMaskSizeAverage = openGLRemasterShadowMaskSizeAverage;
			this.openGLRemasterShadowMaskTimingAverageMs = openGLRemasterShadowMaskTimingAverageMs;
			this.openGLRemasterShadowMaskCacheAverage = openGLRemasterShadowMaskCacheAverage;
			this.openGLRemasterShadowMaskCasterAverage = openGLRemasterShadowMaskCasterAverage;
			this.openGLRemasterShadowMaskReason = openGLRemasterShadowMaskReason;
			this.openGLResidentChunkReplacementRequestedAverage = openGLResidentChunkReplacementRequestedAverage;
			this.openGLResidentChunkReplacementActiveAverage = openGLResidentChunkReplacementActiveAverage;
			this.openGLResidentChunkReplacementFallbackAverage = openGLResidentChunkReplacementFallbackAverage;
			this.openGLResidentChunkReplacementReason = openGLResidentChunkReplacementReason;
			this.openGLResidentChunkDrawableTerrainBatchAverage = openGLResidentChunkDrawableTerrainBatchAverage;
			this.openGLResidentChunkDrawableWallBatchAverage = openGLResidentChunkDrawableWallBatchAverage;
			this.openGLResidentChunkDrawableRoofBatchAverage = openGLResidentChunkDrawableRoofBatchAverage;
			this.openGLWorldSpriteAnchorAverage = openGLWorldSpriteAnchorAverage;
			this.openGLWorldSpriteMatchedAverage = openGLWorldSpriteMatchedAverage;
			this.openGLWorldSpriteDrawnAverage = openGLWorldSpriteDrawnAverage;
			this.openGLWorldEntityVisibilityAverage = openGLWorldEntityVisibilityAverage;
			this.allocations = allocations;
		}
	}
}
