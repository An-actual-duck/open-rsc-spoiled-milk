package orsc;

import com.openrsc.client.model.Sprite;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.Renderer2DSettings;
import orsc.graphics.RendererSpriteTransform;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DDepthFrame;
import orsc.graphics.three.Renderer3DMeshFrame;
import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.Renderer3DSettings;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/*
 * RENDERER-V2 OWNER: input forwarding, frame pass orchestration, and remaining
 * composite glue. GLFW window and viewport presentation state belong to
 * OpenGLWindowController and OpenGLViewportPresenter.
 *
 * LEGACY BRIDGE: methods in this class that replay software framebuffer pixels,
 * projected mesh order, or legacy sprite commands are compatibility paths.
 * Prefer moving new world/render systems into focused owner classes.
 */
final class OpenGLFramePresenter implements AutoCloseable {
	private static final int INITIAL_WIDTH = RenderSurfaceSettings.getWidth();
	private static final int INITIAL_HEIGHT = RenderSurfaceSettings.getHeight();
	private static final int VISIBLE_SPRITE_ATLAS_FULL = -1;
	private static final String OPENGL_VSYNC_PROPERTY = "spoiledmilk.openglVsync";
	private static final String OPENGL_VSYNC_ENV = "SPOILED_MILK_OPENGL_VSYNC";
	private static final boolean OPENGL_VSYNC_ENABLED =
		readBoolean(OPENGL_VSYNC_PROPERTY, OPENGL_VSYNC_ENV, false);
	private static final String WORLD_MESH_PROPERTY = "spoiledmilk.openglWorldMesh";
	private static final String WORLD_MESH_ENV = "SPOILED_MILK_OPENGL_WORLD_MESH";
	static final boolean WORLD_MESH_ENABLED = readBoolean(WORLD_MESH_PROPERTY, WORLD_MESH_ENV);
	static final String WORLD_MESH_VISIBLE_PROPERTY = "spoiledmilk.openglWorldMeshVisible";
	static final String WORLD_MESH_VISIBLE_ENV = "SPOILED_MILK_OPENGL_WORLD_MESH_VISIBLE";
	static final boolean WORLD_MESH_VISIBLE =
		WORLD_MESH_ENABLED && readBoolean(WORLD_MESH_VISIBLE_PROPERTY, WORLD_MESH_VISIBLE_ENV);
	static final String WORLD_MESH_TEXTURED_VISIBLE_PROPERTY = "spoiledmilk.openglWorldMeshTexturedVisible";
	static final String WORLD_MESH_TEXTURED_VISIBLE_ENV = "SPOILED_MILK_OPENGL_WORLD_TEXTURED_VISIBLE";
	static final boolean WORLD_MESH_TEXTURED_VISIBLE =
		WORLD_MESH_ENABLED && readBoolean(WORLD_MESH_TEXTURED_VISIBLE_PROPERTY, WORLD_MESH_TEXTURED_VISIBLE_ENV);
	static final String WORLD_MESH_TEXTURED_STATIC_VISIBLE_PROPERTY =
		"spoiledmilk.openglWorldMeshTexturedStaticVisible";
	static final String WORLD_MESH_TEXTURED_STATIC_VISIBLE_ENV =
		"SPOILED_MILK_OPENGL_WORLD_TEXTURED_STATIC_VISIBLE";
	static final boolean WORLD_MESH_TEXTURED_STATIC_VISIBLE =
		WORLD_MESH_TEXTURED_VISIBLE
			&& readBoolean(WORLD_MESH_TEXTURED_STATIC_VISIBLE_PROPERTY, WORLD_MESH_TEXTURED_STATIC_VISIBLE_ENV);
	static final String WORLD_CHUNKS_VISIBLE_PROPERTY = "spoiledmilk.openglWorldChunksVisible";
	static final String WORLD_CHUNKS_VISIBLE_ENV = "SPOILED_MILK_OPENGL_WORLD_CHUNKS_VISIBLE";
	static final boolean WORLD_CHUNKS_VISIBLE =
		WORLD_MESH_ENABLED && readBoolean(WORLD_CHUNKS_VISIBLE_PROPERTY, WORLD_CHUNKS_VISIBLE_ENV);
	static final String WORLD_CHUNKS_FILLED_VISIBLE_PROPERTY = "spoiledmilk.openglWorldChunksFilledVisible";
	static final String WORLD_CHUNKS_FILLED_VISIBLE_ENV =
		"SPOILED_MILK_OPENGL_WORLD_CHUNKS_FILLED_VISIBLE";
	static final boolean WORLD_CHUNKS_FILLED_VISIBLE =
		WORLD_MESH_ENABLED
			&& readBoolean(WORLD_CHUNKS_FILLED_VISIBLE_PROPERTY, WORLD_CHUNKS_FILLED_VISIBLE_ENV);
	static final String WORLD_CHUNKS_TEXTURED_VISIBLE_PROPERTY = "spoiledmilk.openglWorldChunksTexturedVisible";
	static final String WORLD_CHUNKS_TEXTURED_VISIBLE_ENV =
		"SPOILED_MILK_OPENGL_WORLD_CHUNKS_TEXTURED_VISIBLE";
	static final boolean WORLD_CHUNKS_TEXTURED_VISIBLE =
		WORLD_MESH_ENABLED
			&& readBoolean(WORLD_CHUNKS_TEXTURED_VISIBLE_PROPERTY, WORLD_CHUNKS_TEXTURED_VISIBLE_ENV);
	private static final String WORLD_CHUNKS_TEXTURED_SHADER_PROPERTY =
		"spoiledmilk.openglWorldChunksTexturedShader";
	private static final String WORLD_CHUNKS_TEXTURED_SHADER_ENV =
		"SPOILED_MILK_OPENGL_WORLD_CHUNKS_TEXTURED_SHADER";
	private static final String WORLD_CHUNKS_RAW_MATERIAL_SHADER_PROPERTY =
		"spoiledmilk.openglWorldChunksRawMaterialShader";
	private static final String WORLD_CHUNKS_RAW_MATERIAL_SHADER_ENV =
		"SPOILED_MILK_OPENGL_WORLD_CHUNKS_RAW_MATERIAL_SHADER";
	private static final String WORLD_CHUNKS_REMASTER_LIGHTING_SHADER_PROPERTY =
		"spoiledmilk.openglWorldChunksRemasterLightingShader";
	private static final String WORLD_CHUNKS_REMASTER_LIGHTING_SHADER_ENV =
		"SPOILED_MILK_OPENGL_WORLD_CHUNKS_REMASTER_LIGHTING_SHADER";
	private static final boolean WORLD_CHUNKS_RAW_MATERIAL_SHADER =
		WORLD_CHUNKS_TEXTURED_VISIBLE
			&& readBoolean(WORLD_CHUNKS_RAW_MATERIAL_SHADER_PROPERTY, WORLD_CHUNKS_RAW_MATERIAL_SHADER_ENV, false);
	private static final boolean WORLD_CHUNKS_REMASTER_LIGHTING_SHADER =
		WORLD_CHUNKS_TEXTURED_VISIBLE
			&& !WORLD_CHUNKS_RAW_MATERIAL_SHADER
			&& readBoolean(
				WORLD_CHUNKS_REMASTER_LIGHTING_SHADER_PROPERTY,
				WORLD_CHUNKS_REMASTER_LIGHTING_SHADER_ENV,
				true);
	private static final boolean WORLD_CHUNKS_TEXTURED_SHADER =
		WORLD_CHUNKS_TEXTURED_VISIBLE
			&& (WORLD_CHUNKS_RAW_MATERIAL_SHADER
				|| WORLD_CHUNKS_REMASTER_LIGHTING_SHADER
				|| readBoolean(WORLD_CHUNKS_TEXTURED_SHADER_PROPERTY, WORLD_CHUNKS_TEXTURED_SHADER_ENV, false));
	static final String WORLD_CHUNKS_REPLACEMENT_COMPOSITE_PROPERTY =
		"spoiledmilk.openglWorldChunksReplacementComposite";
	static final String WORLD_CHUNKS_REPLACEMENT_COMPOSITE_ENV =
		"SPOILED_MILK_OPENGL_WORLD_CHUNKS_REPLACEMENT_COMPOSITE";
	static final boolean WORLD_CHUNKS_REPLACEMENT_COMPOSITE =
		WORLD_CHUNKS_TEXTURED_VISIBLE
			&& readBoolean(WORLD_CHUNKS_REPLACEMENT_COMPOSITE_PROPERTY, WORLD_CHUNKS_REPLACEMENT_COMPOSITE_ENV);
	static final String WORLD_CHUNKS_TRUSTED_REPLACEMENT_PROPERTY =
		"spoiledmilk.openglWorldChunksTrustedReplacement";
	static final String WORLD_CHUNKS_TRUSTED_REPLACEMENT_ENV =
		"SPOILED_MILK_OPENGL_WORLD_CHUNKS_TRUSTED_REPLACEMENT";
	static final boolean WORLD_CHUNKS_TRUSTED_REPLACEMENT =
		WORLD_CHUNKS_REPLACEMENT_COMPOSITE
			&& readBoolean(WORLD_CHUNKS_TRUSTED_REPLACEMENT_PROPERTY, WORLD_CHUNKS_TRUSTED_REPLACEMENT_ENV, false);
	static final String WORLD_CHUNKS_RESIDENT_OBJECTS_PROPERTY =
		"spoiledmilk.openglWorldChunksResidentObjects";
	static final String WORLD_CHUNKS_RESIDENT_OBJECTS_ENV =
		"SPOILED_MILK_OPENGL_WORLD_CHUNKS_RESIDENT_OBJECTS";
	static final boolean WORLD_CHUNKS_RESIDENT_OBJECTS =
		WORLD_CHUNKS_REPLACEMENT_COMPOSITE
			&& readBoolean(WORLD_CHUNKS_RESIDENT_OBJECTS_PROPERTY, WORLD_CHUNKS_RESIDENT_OBJECTS_ENV, false);
	private static final String WORLD_CHUNKS_SHADOW_PROOF_PROPERTY =
		"spoiledmilk.openglWorldChunkShadowProof";
	private static final String WORLD_CHUNKS_SHADOW_PROOF_ENV =
		"SPOILED_MILK_OPENGL_WORLD_CHUNK_SHADOW_PROOF";
	private static final boolean WORLD_CHUNKS_SHADOW_PROOF =
		WORLD_CHUNKS_REPLACEMENT_COMPOSITE
			&& readBoolean(WORLD_CHUNKS_SHADOW_PROOF_PROPERTY, WORLD_CHUNKS_SHADOW_PROOF_ENV, false);
	private static final String REMASTER_SHADOW_INVENTORY_DEBUG_PROPERTY =
		"spoiledmilk.remasterShadowInventoryDebug";
	private static final String REMASTER_SHADOW_INVENTORY_DEBUG_ENV =
		"SPOILED_MILK_REMASTER_SHADOW_INVENTORY_DEBUG";
	private static final boolean REMASTER_SHADOW_INVENTORY_DEBUG =
		WORLD_CHUNKS_REPLACEMENT_COMPOSITE
			&& readBoolean(
				REMASTER_SHADOW_INVENTORY_DEBUG_PROPERTY,
				REMASTER_SHADOW_INVENTORY_DEBUG_ENV,
				false);
	private static final String REMASTER_TERRAIN_SHADOW_MASK_PROPERTY =
		"spoiledmilk.remasterTerrainShadowMask";
	private static final String REMASTER_TERRAIN_SHADOW_MASK_ENV =
		"SPOILED_MILK_REMASTER_TERRAIN_SHADOW_MASK";
	private static final String REMASTER_TERRAIN_SHADOW_MASK_DEBUG_PROPERTY =
		"spoiledmilk.remasterTerrainShadowMaskDebug";
	private static final String REMASTER_TERRAIN_SHADOW_MASK_DEBUG_ENV =
		"SPOILED_MILK_REMASTER_TERRAIN_SHADOW_MASK_DEBUG";
	private static final boolean REMASTER_TERRAIN_SHADOW_MASK =
		WORLD_CHUNKS_REPLACEMENT_COMPOSITE
			&& readBoolean(
				REMASTER_TERRAIN_SHADOW_MASK_PROPERTY,
				REMASTER_TERRAIN_SHADOW_MASK_ENV,
				readBoolean(
					REMASTER_TERRAIN_SHADOW_MASK_DEBUG_PROPERTY,
					REMASTER_TERRAIN_SHADOW_MASK_DEBUG_ENV,
					true));
	private static final String WORLD_CHUNKS_SPATIAL_CULL_PROPERTY =
		"spoiledmilk.openglWorldChunksSpatialCull";
	private static final String WORLD_CHUNKS_SPATIAL_CULL_ENV =
		"SPOILED_MILK_OPENGL_WORLD_CHUNKS_SPATIAL_CULL";
	static final boolean WORLD_CHUNKS_SPATIAL_CULL =
		WORLD_CHUNKS_REPLACEMENT_COMPOSITE
			&& readBoolean(WORLD_CHUNKS_SPATIAL_CULL_PROPERTY, WORLD_CHUNKS_SPATIAL_CULL_ENV, false);
	static final String WORLD_STATIC_TEXTURES_PROPERTY =
		"spoiledmilk.openglWorldStaticTextures";
	static final String WORLD_STATIC_TEXTURES_ENV =
		"SPOILED_MILK_OPENGL_WORLD_STATIC_TEXTURES";
	static final boolean WORLD_STATIC_TEXTURES =
		WORLD_MESH_TEXTURED_STATIC_VISIBLE
			&& readBoolean(WORLD_STATIC_TEXTURES_PROPERTY, WORLD_STATIC_TEXTURES_ENV, true);
	private static final String WORLD_MESH_TEXTURED_ALPHA_PROPERTY = "spoiledmilk.openglWorldTexturedAlpha";
	private static final String WORLD_MESH_TEXTURED_ALPHA_ENV = "SPOILED_MILK_OPENGL_WORLD_TEXTURED_ALPHA";
	static final float TEXTURED_DIAGNOSTIC_ALPHA =
		readFloat(WORLD_MESH_TEXTURED_ALPHA_PROPERTY, WORLD_MESH_TEXTURED_ALPHA_ENV, 1.0f, 0.0f, 1.0f);
	static final String WORLD_MESH_TEXTURED_SHADER_PROPERTY =
		"spoiledmilk.openglWorldMeshTexturedShader";
	static final String WORLD_MESH_TEXTURED_SHADER_ENV =
		"SPOILED_MILK_OPENGL_WORLD_TEXTURED_SHADER";
	static final boolean WORLD_MESH_TEXTURED_SHADER =
		WORLD_MESH_TEXTURED_VISIBLE
			&& readBoolean(WORLD_MESH_TEXTURED_SHADER_PROPERTY, WORLD_MESH_TEXTURED_SHADER_ENV, true);
	static final String WORLD_REPLACEMENT_COMPOSITE_PROPERTY =
		"spoiledmilk.openglWorldReplacementComposite";
	static final String WORLD_REPLACEMENT_COMPOSITE_ENV =
		"SPOILED_MILK_OPENGL_WORLD_REPLACEMENT_COMPOSITE";
	static final boolean WORLD_REPLACEMENT_COMPOSITE =
		WORLD_MESH_TEXTURED_STATIC_VISIBLE
			&& readBoolean(WORLD_REPLACEMENT_COMPOSITE_PROPERTY, WORLD_REPLACEMENT_COMPOSITE_ENV, true);
	static final String WORLD_SPRITES_VISIBLE_PROPERTY = "spoiledmilk.openglWorldSpritesVisible";
	static final String WORLD_SPRITES_VISIBLE_ENV = "SPOILED_MILK_OPENGL_WORLD_SPRITES_VISIBLE";
	static final boolean WORLD_SPRITES_VISIBLE =
		WORLD_MESH_ENABLED && readBoolean(WORLD_SPRITES_VISIBLE_PROPERTY, WORLD_SPRITES_VISIBLE_ENV);
	private static final float WORLD_SPRITE_DIAGNOSTIC_ALPHA = 0.65f;
	private static final int VISIBLE_SPRITE_RESTORE_COLOR_TOLERANCE = 8;
	private static final String WORLD_COMPOSITE_DEBUG_PROPERTY =
		"spoiledmilk.openglWorldCompositeDebug";
	private static final String WORLD_COMPOSITE_DEBUG_ENV =
		"SPOILED_MILK_OPENGL_WORLD_COMPOSITE_DEBUG";
	private static final boolean WORLD_COMPOSITE_DEBUG =
		readBoolean(WORLD_COMPOSITE_DEBUG_PROPERTY, WORLD_COMPOSITE_DEBUG_ENV);
	private static final int WORLD_COMPOSITE_DEBUG_LOG_LIMIT = 80;
	private static final int DEBUG_OVERLAY_ATLAS_WIDTH = 1024;
	private static final int DEBUG_OVERLAY_ATLAS_HEIGHT = 512;
	private static final int DEBUG_OVERLAY_MAX_TEXTURE_WIDTH = DEBUG_OVERLAY_ATLAS_WIDTH - 2;
	private static final int DEBUG_OVERLAY_MAX_TEXTURE_HEIGHT = DEBUG_OVERLAY_ATLAS_HEIGHT - 2;
	private static final long DEBUG_OVERLAY_TEXTURE_UPDATE_NANOS = 250_000_000L;
	private static final String FRAME_CAPTURE_PROPERTY = "spoiledmilk.openglFrameCapture";
	private static final String FRAME_CAPTURE_ENV = "SPOILED_MILK_OPENGL_FRAME_CAPTURE";
	private static final boolean FRAME_CAPTURE_HOTKEY_ENABLED =
		readBoolean(FRAME_CAPTURE_PROPERTY, FRAME_CAPTURE_ENV, false);
	static final String FRAME_CAPTURE_DIR_PROPERTY = "spoiledmilk.openglFrameCaptureDir";
	static final String FRAME_CAPTURE_DIR_ENV = "SPOILED_MILK_OPENGL_FRAME_CAPTURE_DIR";
	private static final int FRAME_CAPTURE_BURST_FRAMES = 12;
	private static final int RESIDENT_WORLD_SESSION_CLEAR_FRAME_THRESHOLD = 8;
	private static final int UNIT_QUAD_VERTEX_COUNT = 4;
	private static final int UNIT_QUAD_INDEX_COUNT = 6;
	private static final int UNIT_QUAD_FLOATS_PER_VERTEX = 4;
	private static final int UNIT_QUAD_POSITION_COMPONENTS = 2;
	private static final int UNIT_QUAD_TEXCOORD_COMPONENTS = 2;
	private static final int UNIT_QUAD_TEXCOORD_OFFSET_BYTES =
		UNIT_QUAD_POSITION_COMPONENTS * 4;
	private static final int UNIT_QUAD_STRIDE_BYTES =
		UNIT_QUAD_FLOATS_PER_VERTEX * 4;
	private static final int TILE_SIZE = 128;
	private static final int UNDERGROUND_WORLD_TILE_Z_THRESHOLD = 3000;
	private final Object frameLock = new Object();
	private final String title;
	private final boolean inputEnabled;
	private final boolean primaryWindow;
	private final FrameBufferPool frameBufferPool = new FrameBufferPool();
	private final OpenGLViewportPresenter viewportPresenter;

	private volatile boolean started;
	private volatile boolean closed;
	private volatile boolean disabled;
	private Frame pendingFrame;
	private Thread renderThread;

	private LwjglBindings gl;
	private OpenGLWindowController windowController;
	private int textureId;
	private int textureWidth;
	private int textureHeight;
	private OpenGLSpriteTextureCache spriteTextureCache;
	private OpenGLGlyphTextureCache glyphTextureCache;
	private OpenGLDynamicTextureAtlas visibleSpriteTextureAtlas;
	private OpenGLDynamicTextureAtlas debugOverlayTextureAtlas;
	private OpenGLTextureRegion debugOverlayTextureRegion;
	private int debugOverlayTextureWidth;
	private int debugOverlayTextureHeight;
	private long debugOverlayNextTextureUpdateNanos;
	private OpenGLWorldTextureCache worldTextureCache;
	OpenGLWorldMeshRenderer worldMeshRenderer;
	private OpenGLWorldChunkRenderer worldChunkRenderer;
	private OpenGLWorldSpriteRenderer worldSpriteRenderer;
	private OpenGLWorldSpriteDrawController worldSpriteDrawController;
	private OpenGLShaderProgram projectedWorldShader;
	private OpenGLShaderProgram residentChunkShader;
	private int unitQuadVertexBufferId;
	private int unitQuadIndexBufferId;
	private int screenQuadVertexBufferId;
	private FloatBuffer screenQuadUploadBuffer;
	private int legacySceneSpriteRestoreCommands;
	private int legacySceneSpriteRestoreFallbacks;
	private int legacySceneSpriteRestoreFallbackPixels;
	private int legacyEntitySpriteDebugFrames;
	private int legacyEntitySpriteDebugCommands;
	private int legacyEntitySpriteDebugVisiblePixels;
	private int legacyEntitySpriteDebugDirectPixels;
	private int legacyEntitySpriteDebugFallbacks;
	private int legacyEntitySpriteDebugSkipped;
	private int legacyEntitySpriteDebugAtlasFull;
	private int legacyEntitySpriteDebugFirstId = -1;
	private int legacyEntitySpriteDebugLastId = -1;
	final Map<Integer, LegacyEntitySpriteDebugStats> legacyEntitySpriteDebugById =
		new LinkedHashMap<Integer, LegacyEntitySpriteDebugStats>();
	final List<LegacyEntitySpriteDepthEvaluation> legacyEntitySpriteDepthEvaluations =
		new ArrayList<LegacyEntitySpriteDepthEvaluation>();
	private OpenGLInputBridge inputBridge;
	private int worldCompositeDebugLogs;
	private volatile boolean frameCaptureRequested;
	private volatile int frameCaptureBurstRemaining;
	private int frameCaptureSequence;
	private long frameCaptureBurstSequence;
	private long activeFrameCaptureBurstId;
	private int frameCaptureBurstFrameIndex;
	private int activeFrameCaptureBurstFrameIndex = -1;
	private long activeFrameCaptureStartedNanos;
	private long activeFrameCaptureInputNanos;
	private long activeFrameCaptureLayerNanos;
	private boolean frameCaptureBurstCompletionLogged = true;
	private OpenGLFrameCapture activeFrameCapture;
	private long phaseCaptureNanos;
	private FloatBuffer cameraToClipMatrixBuffer;
	int worldSpriteDepthDrawCommands;
	int worldSpriteDepthTextureBatches;
	private boolean previousFrameHadResidentWorldChunks;
	private int residentWorldMissingFrameCount;

	OpenGLFramePresenter(String title, boolean inputEnabled, boolean primaryWindow) {
		this.title = title;
		this.inputEnabled = inputEnabled;
		this.primaryWindow = primaryWindow;
		viewportPresenter = new OpenGLViewportPresenter(primaryWindow, INITIAL_WIDTH, INITIAL_HEIGHT);
	}

	void present(BufferedImage image, float scalar, ScaledWindow.ScalingAlgorithm scalingAlgorithm) {
		present(image, scalar, scalingAlgorithm, Renderer2DFrame.EMPTY);
	}

	void present(
		BufferedImage image,
		float scalar,
		ScaledWindow.ScalingAlgorithm scalingAlgorithm,
		Renderer2DFrame renderer2DFrame) {
		present(image, scalar, scalingAlgorithm, renderer2DFrame, null);
	}

	void present(
		BufferedImage image,
		float scalar,
		ScaledWindow.ScalingAlgorithm scalingAlgorithm,
		Renderer2DFrame renderer2DFrame,
		Renderer3DFrame renderer3DFrame) {
		present(image, scalar, scalingAlgorithm, renderer2DFrame, renderer3DFrame, null);
	}

	void present(
		BufferedImage image,
		float scalar,
		ScaledWindow.ScalingAlgorithm scalingAlgorithm,
		Renderer2DFrame renderer2DFrame,
		Renderer3DFrame renderer3DFrame,
		String[] rendererDebugOverlayLines) {
		if (image == null || closed || disabled) {
			if (renderer3DFrame != null) {
				renderer3DFrame.releaseDepthFrame();
			}
			return;
		}
		if (renderer2DFrame == null) {
			renderer2DFrame = Renderer2DFrame.EMPTY;
		}

		ensureStarted();
		if (disabled) {
			if (renderer3DFrame != null) {
				renderer3DFrame.releaseDepthFrame();
			}
			return;
		}

		long snapshotStart = RenderTelemetry.now();
		Frame frame = Frame.fromImage(
			image,
			scalar,
			scalingAlgorithm,
			frameBufferPool,
			renderer2DFrame,
			renderer3DFrame,
			rendererDebugOverlayLines);
		RenderTelemetry.recordOpenGLSnapshot(RenderTelemetry.elapsedSince(snapshotStart));

		synchronized (frameLock) {
			if (pendingFrame != null) {
				RenderTelemetry.recordOpenGLDroppedFrame();
				pendingFrame.release();
			}
			pendingFrame = frame;
			frameLock.notifyAll();
		}
	}

	private synchronized void ensureStarted() {
		if (started || closed || disabled) {
			return;
		}

		started = true;
		renderThread = new Thread(this::renderLoop, "Spoiled Milk OpenGL Presenter");
		renderThread.setDaemon(!primaryWindow);
		renderThread.start();
	}

	private void renderLoop() {
		Renderer3DSettings.setOpenGLPresentationAvailable(false);
		try {
			gl = LwjglBindings.load();
			windowController = new OpenGLWindowController(
				gl,
				title,
				primaryWindow,
				new OpenGLWindowController.Delegate() {
					@Override
					public void contextCreated() {
						logOpenGLDevice();
					}

					@Override
					public void releaseInputState() {
						OpenGLFramePresenter.this.releaseInputState();
					}

					@Override
					public void suppressKeysUntilRelease() {
						OpenGLFramePresenter.this.suppressKeysUntilRelease();
					}

					@Override
					public void saveWindowSettings() {
						mudclient.saveOpenGLWindowSettings();
					}

					@Override
					public void closeClient() {
						closeClientFromOpenGLWindow();
					}

					@Override
					public void log(String message) {
						OpenGLFramePresenter.log(message);
					}
				});
			if (!windowController.initializeGlfw()) {
				disable("OpenGL presenter disabled: GLFW could not initialize.");
				return;
			}

			windowController.createWindow(INITIAL_WIDTH, INITIAL_HEIGHT, OPENGL_VSYNC_ENABLED);
			initializeOpenGLResources();
			initializeInputBridge();
			Renderer3DSettings.setOpenGLPresentationAvailable(true);
			log("OpenGL presenter active.");
			if (inputEnabled) {
				log("OpenGL input bridge active.");
			}
			if (primaryWindow) {
				log("OpenGL primary window active; Swing client window is hidden.");
			}
			if (Renderer2DSettings.isOpenGLSpriteOverlayEnabled()) {
				log("OpenGL sprite overlay probe active: mode="
					+ Renderer2DSettings.getOpenGLSpriteOverlayModeId()
					+ ".");
			}
			if (WORLD_MESH_ENABLED) {
				log("OpenGL world mesh upload active"
					+ (WORLD_MESH_VISIBLE ? " with visible wireframe diagnostic draw" : "")
					+ (WORLD_MESH_TEXTURED_VISIBLE ? " with visible textured diagnostic draw" : "")
					+ (WORLD_CHUNKS_VISIBLE ? " with visible world chunk diagnostic draw" : "")
					+ (WORLD_CHUNKS_FILLED_VISIBLE ? " with visible filled world chunk diagnostic draw" : "")
					+ (WORLD_CHUNKS_TEXTURED_VISIBLE ? " with visible textured world chunk diagnostic draw" : "")
					+ ".");
				if (WORLD_MESH_TEXTURED_VISIBLE) {
					log("OpenGL world textured diagnostic alpha: " + TEXTURED_DIAGNOSTIC_ALPHA);
				}
				if (WORLD_MESH_TEXTURED_SHADER) {
					log("OpenGL projected world shader path active.");
				} else if (WORLD_MESH_TEXTURED_VISIBLE) {
					log("OpenGL projected world shader path disabled; fixed-function fallback active.");
				}
				if (WORLD_CHUNKS_RAW_MATERIAL_SHADER) {
					log("OpenGL resident chunk raw material shader path active; baked lighting ignored.");
				} else if (WORLD_CHUNKS_REMASTER_LIGHTING_SHADER) {
					log("OpenGL resident chunk remaster-capable shader path active.");
				} else if (WORLD_CHUNKS_TEXTURED_SHADER) {
					log("OpenGL resident chunk parity shader path active.");
				} else if (WORLD_CHUNKS_TEXTURED_VISIBLE) {
					log("OpenGL resident chunk shader path disabled; fixed-function fallback active.");
				}
				if (WORLD_MESH_TEXTURED_STATIC_VISIBLE) {
					log("OpenGL static world texture sampling: "
						+ (WORLD_STATIC_TEXTURES ? "enabled" : "flat-material fallback")
						+ ".");
					log("OpenGL world geometry projection: camera-space perspective.");
				}
				if (WORLD_REPLACEMENT_COMPOSITE) {
					log("OpenGL world replacement composite active.");
				}
				if (WORLD_CHUNKS_REPLACEMENT_COMPOSITE) {
					log("OpenGL resident chunk replacement proof active; projected ownership is guarded.");
				}
				if (WORLD_CHUNKS_TRUSTED_REPLACEMENT) {
					log("OpenGL resident chunk trusted replacement ownership active.");
				}
				if (REMASTER_SHADOW_INVENTORY_DEBUG) {
					log("OpenGL remaster shadow inventory debug overlay active.");
				}
				if (REMASTER_TERRAIN_SHADOW_MASK) {
					log("OpenGL remaster terrain shadow mask active for Directional lighting.");
				}
			}
			if (WORLD_SPRITES_VISIBLE) {
				log("OpenGL world sprite diagnostic active.");
			}
			if (WORLD_COMPOSITE_DEBUG) {
				log("OpenGL world composite debug logging active.");
			}
			log("OpenGL scale mode: "
				+ viewportPresenter.currentScaleMode().id
				+ (primaryWindow ? " automatic" : ""));

			boolean windowCloseRequested = false;
			while (!closed) {
				windowCloseRequested = windowController.shouldClose();
				if (windowCloseRequested) {
					break;
				}

				Frame frame = takeLatestFrame();
				if (frame != null) {
					try {
						renderFrame(frame);
					} finally {
						frame.release();
					}
				} else {
					windowController.pollEvents();
					processInput();
				}
			}
			log("OpenGL presenter loop ended: closed=" + closed + ", windowCloseRequested=" + windowCloseRequested);
		} catch (ClassNotFoundException e) {
			disable("OpenGL presenter disabled: LWJGL jars were not found. Run scripts/download-lwjgl.sh and rebuild.");
		} catch (Throwable t) {
			disable("OpenGL presenter disabled after an unexpected error: " + t.getMessage());
			t.printStackTrace();
		} finally {
			cleanup();
		}
	}

	private void initializeOpenGLResources() throws Exception {
		textureId = gl.glGenTextures();
		gl.glBindTexture(gl.GL_TEXTURE_2D, textureId);
		gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_WRAP_S, gl.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_WRAP_T, gl.GL_CLAMP_TO_EDGE);
		gl.glEnable(gl.GL_TEXTURE_2D);
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		initializeUnitQuadBuffers();
		initializeScreenQuadBuffers();
		worldSpriteRenderer = new OpenGLWorldSpriteRenderer(gl);
		worldSpriteRenderer.initialize();
		initializeShaderPrograms();
		spriteTextureCache = new OpenGLSpriteTextureCache(gl);
		glyphTextureCache = new OpenGLGlyphTextureCache(gl);
		if (Renderer2DSettings.isOpenGLSpriteOverlayEnabled() || Renderer2DSettings.canReplayUiOverOpenGLWorld()) {
			visibleSpriteTextureAtlas = OpenGLDynamicTextureAtlas.create(gl, 2048, 2048);
		}
		worldSpriteDrawController = new OpenGLWorldSpriteDrawController(
			gl,
			visibleSpriteTextureAtlas,
			worldSpriteRenderer,
			new OpenGLWorldSpriteDrawController.Delegate() {
				@Override
				public boolean hasActiveFrameCapture() {
					return activeFrameCapture != null;
				}

				@Override
				public DynamicTextureData buildDepthVisibleEntitySpriteTexture(
					Frame frame,
					Renderer2DFrame.SpriteCommand command,
					Renderer3DFrame.SpriteAnchor anchor,
					boolean[] clippedSceneRestoreMask,
					WorldSpriteAnchorMatch providedAnchorMatch) {
					return OpenGLFramePresenter.this.buildDepthVisibleEntitySpriteTexture(
						frame,
						command,
						anchor,
						clippedSceneRestoreMask,
						providedAnchorMatch);
				}

				@Override
				public boolean drawDynamicSpriteTexture(
					Renderer2DFrame.SpriteCommand command,
					DynamicTextureData textureData,
					float alpha) throws Exception {
					return OpenGLFramePresenter.this.drawOpenGLCompositeDynamicSpriteTexture(
						command,
						textureData,
						alpha);
				}

				@Override
				public void drawFallbackSprite(Renderer2DFrame.SpriteCommand command) throws Exception {
					OpenGLFramePresenter.this.drawSpriteCommand(command);
				}

				@Override
				public void useWorldMeshProjection(Frame frame) throws Exception {
					OpenGLFramePresenter.this.useWorldMeshProjection(frame);
				}

				@Override
				public void useSourceProjection(int sourceWidth, int sourceHeight) throws Exception {
					OpenGLFramePresenter.this.useSourceProjection(sourceWidth, sourceHeight);
				}
			});
		debugOverlayTextureAtlas = OpenGLDynamicTextureAtlas.create(
			gl,
			DEBUG_OVERLAY_ATLAS_WIDTH,
			DEBUG_OVERLAY_ATLAS_HEIGHT);
		if (WORLD_MESH_ENABLED) {
			worldTextureCache = new OpenGLWorldTextureCache(gl);
			worldMeshRenderer = new OpenGLWorldMeshRenderer(
				gl,
				worldTextureCache,
				projectedWorldShader,
				WORLD_MESH_TEXTURED_STATIC_VISIBLE,
				WORLD_STATIC_TEXTURES,
				TEXTURED_DIAGNOSTIC_ALPHA,
				WORLD_MESH_TEXTURED_SHADER);
			worldChunkRenderer = new OpenGLWorldChunkRenderer(
				gl,
				worldTextureCache,
				residentChunkShader,
				WORLD_CHUNKS_REPLACEMENT_COMPOSITE,
				WORLD_CHUNKS_RESIDENT_OBJECTS,
				WORLD_CHUNKS_SHADOW_PROOF,
				WORLD_CHUNKS_SPATIAL_CULL,
				WORLD_CHUNKS_TEXTURED_SHADER,
				WORLD_CHUNKS_REMASTER_LIGHTING_SHADER,
				WORLD_CHUNKS_RAW_MATERIAL_SHADER);
		}
		gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
	}

	private void initializeShaderPrograms() {
		try {
			projectedWorldShader = OpenGLShaderProgram.createProjectedWorld(gl);
			gl.glUseProgram(0);
			log("OpenGL projected world shader program compiled.");
		} catch (Throwable t) {
			projectedWorldShader = null;
			try {
				gl.glUseProgram(0);
			} catch (Throwable ignored) {
			}
			log("OpenGL projected world shader program unavailable: " + t.getMessage());
		}
		try {
			residentChunkShader = OpenGLShaderProgram.createResidentChunkParity(gl);
			gl.glUseProgram(0);
			log("OpenGL resident chunk shader program compiled.");
		} catch (Throwable t) {
			residentChunkShader = null;
			try {
				gl.glUseProgram(0);
			} catch (Throwable ignored) {
			}
			log("OpenGL resident chunk shader program unavailable: " + t.getMessage());
		}
	}

	private void logOpenGLDevice() {
		try {
			String renderer = gl.glGetString(gl.GL_RENDERER);
			String version = gl.glGetString(gl.GL_VERSION);
			String vendor = gl.glGetString(gl.GL_VENDOR);
			RendererDiagnosticSession.recordOpenGLDevice(renderer, version, vendor);
			log("OpenGL device: "
				+ renderer
				+ " | "
				+ version
				+ " | "
				+ vendor);
		} catch (Throwable t) {
			log("OpenGL device info unavailable: " + t.getMessage());
		}
	}

	private Frame takeLatestFrame() throws InterruptedException {
		synchronized (frameLock) {
			if (pendingFrame == null && !closed) {
				frameLock.wait(16L);
			}

			Frame frame = pendingFrame;
			pendingFrame = null;
			return frame;
		}
	}

	private void renderFrame(Frame frame) throws Exception {
		OpenGLWindowController.SurfaceSize surface =
			windowController.prepareFrame(frame.targetWidth, frame.targetHeight);
		viewportPresenter.update(
			surface.framebufferWidth,
			surface.framebufferHeight,
			surface.windowWidth,
			surface.windowHeight,
			frame.sourceWidth,
			frame.sourceHeight);
		OpenGLViewportPresenter.Viewport framebufferViewport =
			viewportPresenter.framebufferViewport();

		long uploadStart = RenderTelemetry.now();
		uploadTexture(frame);
		long uploadNanos = RenderTelemetry.elapsedSince(uploadStart);
		boolean frameHasResidentWorldChunks = hasResidentWorldChunkFrame(frame);
		if (frameHasResidentWorldChunks) {
			residentWorldMissingFrameCount = 0;
			previousFrameHadResidentWorldChunks = true;
		} else if (previousFrameHadResidentWorldChunks) {
			residentWorldMissingFrameCount++;
			if (residentWorldMissingFrameCount >= RESIDENT_WORLD_SESSION_CLEAR_FRAME_THRESHOLD) {
				clearResidentWorldChunkSession();
				previousFrameHadResidentWorldChunks = false;
				residentWorldMissingFrameCount = 0;
			}
		}
		boolean worldReplacementComposite = shouldUseOpenGLWorldReplacementComposite(frame);
		OpenGLFrameCapture frameCapture = beginFrameCaptureIfRequested(frame, worldReplacementComposite);
		activeFrameCapture = frameCapture;
		Renderer2DFrame.CaptureStats renderer2DCaptureStats = frame.renderer2DFrame.getCaptureStats();
		RenderTelemetry.recordSpriteCaptureStats(renderer2DCaptureStats);
		RenderTelemetry.recordRenderer2DCommandLimits(renderer2DCaptureStats);

		long baseStart = RenderTelemetry.now();
		phaseCaptureNanos = 0L;
		prepareBaseFramebufferPass();
		clearFrameBackground(frame, framebufferViewport);
		gl.glViewport(
			framebufferViewport.x,
			framebufferViewport.y,
			framebufferViewport.width,
			framebufferViewport.height);
		if (!worldReplacementComposite) {
			runOpenGLPass(() -> drawTexturedQuad());
		}
		long baseNanos = RenderTelemetry.elapsedSince(baseStart);
		captureLayer(frameCapture, "01-base");

		long worldStart = RenderTelemetry.now();
		phaseCaptureNanos = 0L;
		runOpenGLPass(() -> drawWorldMesh(frame, worldReplacementComposite));
		long worldNanos = RenderTelemetry.elapsedSince(worldStart);
		captureLayer(frameCapture, "02-opengl-world");

		long worldSpriteStart = RenderTelemetry.now();
		phaseCaptureNanos = 0L;
		if (!worldReplacementComposite) {
			runOpenGLPass(() -> drawWorldSprites(frame));
		}
		long worldSpriteNanos = RenderTelemetry.elapsedSince(worldSpriteStart);
		captureLayer(frameCapture, "03-world-sprites");

		long spriteOverlayStart = RenderTelemetry.now();
		phaseCaptureNanos = 0L;
		runOpenGLPass(() -> drawSpriteOverlay(frame, worldReplacementComposite));
		long spriteOverlayNanos = Math.max(0L, RenderTelemetry.elapsedSince(spriteOverlayStart) - phaseCaptureNanos);
		captureLayer(frameCapture, "05-sprite-overlay");

		long debugOverlayStart = RenderTelemetry.now();
		phaseCaptureNanos = 0L;
		runOpenGLPass(() -> drawRendererDebugOverlay(frame));
		long debugOverlayNanos = RenderTelemetry.elapsedSince(debugOverlayStart);
		captureLayer(frameCapture, "06-debug-overlay");
		finishFrameCapture(frameCapture, frame, worldReplacementComposite);
		activeFrameCapture = null;

		long swapStart = RenderTelemetry.now();
		windowController.swapBuffers();
		long swapNanos = RenderTelemetry.elapsedSince(swapStart);
		windowController.pollEvents();
		processInput();

		RenderTelemetry.recordOpenGLFramePhases(
			baseNanos,
			worldNanos,
			worldSpriteNanos,
			spriteOverlayNanos,
			debugOverlayNanos,
			swapNanos);
		long renderNanos = uploadNanos
			+ baseNanos
			+ worldNanos
			+ worldSpriteNanos
			+ spriteOverlayNanos
			+ debugOverlayNanos
			+ swapNanos;
		RenderTelemetry.recordOpenGLFrame(uploadNanos, renderNanos);
	}

	private OpenGLFrameCapture beginFrameCaptureIfRequested(Frame frame, boolean worldReplacementComposite) {
		if (!FRAME_CAPTURE_HOTKEY_ENABLED) {
			return null;
		}
		if (frameCaptureRequested) {
			frameCaptureRequested = false;
			if (frameCaptureBurstRemaining <= 0) {
				frameCaptureBurstRemaining = FRAME_CAPTURE_BURST_FRAMES;
			}
		}
		if (frameCaptureBurstRemaining <= 0) {
			return null;
		}
		frameCaptureBurstRemaining--;
		activeFrameCaptureBurstFrameIndex = frameCaptureBurstFrameIndex++;
		activeFrameCaptureStartedNanos = System.nanoTime();
		activeFrameCaptureInputNanos = 0L;
		activeFrameCaptureLayerNanos = 0L;
		OpenGLFrameCapture capture = null;
		try {
			capture = OpenGLFrameCapture.create(
				++frameCaptureSequence,
				frame,
				worldReplacementComposite,
				this);
			capture.writeFrameInputs(frame, this);
			activeFrameCaptureInputNanos = System.nanoTime() - activeFrameCaptureStartedNanos;
			RendererDiagnosticSession.recordCaptureFrame(
				"started",
				activeFrameCaptureBurstId,
				activeFrameCaptureBurstFrameIndex,
				capture.sequence,
				RenderTelemetry.currentFrameSequence(),
				capture.directory,
				activeFrameCaptureInputNanos,
				activeFrameCaptureInputNanos,
				0L,
				0L,
				false,
				null,
				capture.getArtifactNames());
			log("OpenGL frame capture started: "
				+ capture.getDirectoryPath()
				+ " burstRemaining="
				+ frameCaptureBurstRemaining);
			return capture;
		} catch (Throwable t) {
			activeFrameCaptureInputNanos = System.nanoTime() - activeFrameCaptureStartedNanos;
			log("OpenGL frame capture could not start: " + t.getMessage());
			RendererDiagnosticSession.recordCaptureFrame(
				"failed",
				activeFrameCaptureBurstId,
				activeFrameCaptureBurstFrameIndex,
				capture == null ? frameCaptureSequence : capture.sequence,
				RenderTelemetry.currentFrameSequence(),
				capture == null ? null : capture.directory,
				activeFrameCaptureInputNanos,
				activeFrameCaptureInputNanos,
				activeFrameCaptureLayerNanos,
				0L,
				true,
				t.getClass().getName() + ": " + t.getMessage(),
				capture == null ? new String[0] : capture.getArtifactNames());
			completeFrameCaptureBurstIfNeeded();
			return null;
		}
	}

	private void captureLayer(OpenGLFrameCapture capture, String layerName) {
		if (capture == null || capture.hasFailed()) {
			return;
		}
		long captureStart = RenderTelemetry.now();
		try {
			capture.writeLayer(layerName, readCurrentViewportImage());
		} catch (Throwable t) {
			capture.markFailed(t);
			log("OpenGL frame capture failed at " + layerName + ": " + t.getMessage());
		} finally {
			long captureNanos = RenderTelemetry.elapsedSince(captureStart);
			phaseCaptureNanos += captureNanos;
			activeFrameCaptureLayerNanos += captureNanos;
		}
	}

	private void finishFrameCapture(
		OpenGLFrameCapture capture,
		Frame frame,
		boolean worldReplacementComposite) {
		if (capture == null) {
			return;
		}
		Throwable finishFailure = null;
		long finishStartedNanos = System.nanoTime();
		long layerNanosBeforeFinish = activeFrameCaptureLayerNanos;
		try {
			if (!capture.hasFailed()) {
				long finalLayerStart = RenderTelemetry.now();
				try {
					capture.writeLayer("07-final", readCurrentViewportImage());
				} finally {
					activeFrameCaptureLayerNanos += RenderTelemetry.elapsedSince(finalLayerStart);
				}
			}
			capture.writeEntityRestoreStats(this);
			capture.writeEntityDepthEvaluations(this);
			capture.writeWorldSpriteBatchStats(this);
			capture.writeShaderVertexParityStats(this);
			capture.writeCharacterSprites(frame, legacyEntitySpriteDebugById);
			capture.writeSummary(frame, worldReplacementComposite, capture.hasFailed());
			log("OpenGL frame capture written: " + capture.getDirectoryPath());
		} catch (Throwable t) {
			finishFailure = t;
			capture.markFailed(t);
			log("OpenGL frame capture could not finish: " + t.getMessage());
		} finally {
			long finishTotalNanos = System.nanoTime() - finishStartedNanos;
			long finalLayerNanos = Math.max(0L, activeFrameCaptureLayerNanos - layerNanosBeforeFinish);
			long finishCaptureNanos = Math.max(0L, finishTotalNanos - finalLayerNanos);
			String failure = capture.getFailureMessage();
			if (failure == null && finishFailure != null) {
				failure = finishFailure.getClass().getName() + ": " + finishFailure.getMessage();
			}
			RendererDiagnosticSession.recordCaptureFrame(
				capture.hasFailed() ? "failed" : "completed",
				activeFrameCaptureBurstId,
				activeFrameCaptureBurstFrameIndex,
				capture.sequence,
				RenderTelemetry.currentFrameSequence(),
				capture.directory,
				System.nanoTime() - activeFrameCaptureStartedNanos,
				activeFrameCaptureInputNanos,
				activeFrameCaptureLayerNanos,
				finishCaptureNanos,
				capture.hasFailed(),
				failure,
				capture.getArtifactNames());
			completeFrameCaptureBurstIfNeeded();
		}
	}

	private void completeFrameCaptureBurstIfNeeded() {
		if (frameCaptureBurstRemaining > 0 || frameCaptureBurstCompletionLogged) {
			return;
		}
		frameCaptureBurstCompletionLogged = true;
		RendererDiagnosticSession.Record event =
			RendererDiagnosticSession.newEventRecord("capture.burst.completed");
		if (event != null) {
			event.number("burstId", activeFrameCaptureBurstId);
			event.number("capturedFrames", frameCaptureBurstFrameIndex);
			event.number("rendererFrameSequence", RenderTelemetry.currentFrameSequence());
			RendererDiagnosticSession.writeEventRecord(event);
		}
		RenderTelemetry.recordDiagnosticBoundary("capture-burst-after");
	}

	private BufferedImage readCurrentViewportImage() throws Exception {
		OpenGLViewportPresenter.Viewport viewport = viewportPresenter.framebufferViewport();
		int width = Math.max(1, viewport.width);
		int height = Math.max(1, viewport.height);
		ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
		gl.glReadPixels(
			viewport.x,
			viewport.y,
			width,
			height,
			gl.GL_RGBA,
			gl.GL_UNSIGNED_BYTE,
			pixels);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			int sourceRow = height - 1 - y;
			for (int x = 0; x < width; x++) {
				int offset = (sourceRow * width + x) * 4;
				int red = pixels.get(offset) & 0xFF;
				int green = pixels.get(offset + 1) & 0xFF;
				int blue = pixels.get(offset + 2) & 0xFF;
				int alpha = pixels.get(offset + 3) & 0xFF;
				image.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
			}
		}
		return image;
	}

	private boolean shouldUseOpenGLWorldReplacementComposite(Frame frame) {
		return shouldUseProjectedWorldReplacementComposite(frame)
			|| shouldUseResidentChunkReplacementComposite(frame);
	}

	private boolean shouldUseProjectedWorldReplacementComposite(Frame frame) {
		if (!Renderer2DSettings.canReplayUiOverOpenGLWorld()
			|| !hasScenePhaseCommands(frame)
			|| frame.renderer3DFrame == null) {
			return false;
		}
		Renderer3DMeshFrame meshFrame = frame.renderer3DFrame.getMeshFrame();
		return WORLD_REPLACEMENT_COMPOSITE
			&& !Renderer3DSettings.canSkipProjectedWorldCapture()
			&& meshFrame != null
			&& meshFrame.getTriangleCount() > 0;
	}

	private boolean shouldUseResidentChunkReplacementComposite(Frame frame) {
		if (!isResidentChunkReplacementRequested(frame)
			|| !WORLD_CHUNKS_TRUSTED_REPLACEMENT) {
			return false;
		}
		if (Renderer3DSettings.canSkipLegacyWorldRaster()
			&& Renderer3DSettings.canSkipProjectedWorldCapture()) {
			return true;
		}
		if (worldChunkRenderer == null) {
			return false;
		}
		Renderer3DWorldChunkFrame worldChunkFrame = frame.renderer3DFrame.getWorldChunkFrame();
		return worldChunkRenderer.inspectDrawableResidentStaticWorld(
			frame.renderer3DFrame,
			worldChunkFrame,
			WORLD_CHUNKS_TEXTURED_VISIBLE).canReplace;
	}

	private boolean isResidentChunkReplacementRequested(Frame frame) {
		if (!Renderer2DSettings.canReplayUiOverOpenGLWorld()
			|| !hasScenePhaseCommands(frame)
			|| frame.renderer3DFrame == null) {
			return false;
		}
		Renderer3DWorldChunkFrame worldChunkFrame = frame.renderer3DFrame.getWorldChunkFrame();
		return WORLD_CHUNKS_REPLACEMENT_COMPOSITE
			&& (!WORLD_CHUNKS_RESIDENT_OBJECTS || frame.renderer3DFrame.getDepthFrame() != null)
			&& worldChunkFrame != null
			&& worldChunkFrame.getChunkCount() > 0;
	}

	private boolean hasResidentWorldChunkFrame(Frame frame) {
		if (frame == null || frame.renderer3DFrame == null) {
			return false;
		}
		Renderer3DWorldChunkFrame worldChunkFrame = frame.renderer3DFrame.getWorldChunkFrame();
		return worldChunkFrame != null && worldChunkFrame.getChunkCount() > 0;
	}

	private void clearResidentWorldChunkSession() throws Exception {
		if (worldChunkRenderer != null) {
			worldChunkRenderer.clearResidentWorldSession();
		}
	}

	private boolean hasScenePhaseCommands(Frame frame) {
		if (frame == null || frame.renderer2DFrame == null) {
			return false;
		}
		for (Renderer2DFrame.SpriteCommand command : frame.renderer2DFrame.getSpriteCommands()) {
			if (command.getPhase() == Renderer2DFrame.Phase.SCENE) {
				return true;
			}
		}
		return false;
	}

	private void prepareBaseFramebufferPass() throws Exception {
		gl.glDisable(gl.GL_DEPTH_TEST);
		gl.glDisable(gl.GL_SCISSOR_TEST);
		gl.glDisable(gl.GL_BLEND);
		gl.glEnable(gl.GL_TEXTURE_2D);
		gl.glBindTexture(gl.GL_TEXTURE_2D, textureId);
		gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
		gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
	}

	private void clearFrameBackground(
		Frame frame,
		OpenGLViewportPresenter.Viewport viewport) throws Exception {
		gl.glDisable(gl.GL_SCISSOR_TEST);
		gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		gl.glClear(gl.GL_COLOR_BUFFER_BIT);

		RendererDayNightCycle.Presentation presentation = RendererDayNightCycle.currentPresentation();
		gl.glEnable(gl.GL_SCISSOR_TEST);
		gl.glScissor(viewport.x, viewport.y, viewport.width, viewport.height);
		gl.glViewport(viewport.x, viewport.y, viewport.width, viewport.height);
		if (shouldDrawSkyBackdrop(frame)) {
			drawSkyBackdrop(frame, presentation);
		}
		gl.glDisable(gl.GL_SCISSOR_TEST);
	}

	private boolean shouldDrawSkyBackdrop(Frame frame) {
		return !isUndergroundFrame(frame);
	}

	private boolean isUndergroundFrame(Frame frame) {
		if (frame == null || frame.renderer3DFrame == null) {
			return false;
		}
		Renderer3DWorldChunkFrame worldChunkFrame = frame.renderer3DFrame.getWorldChunkFrame();
		if (worldChunkFrame == null) {
			return false;
		}
		Renderer3DWorldChunkFrame.ChunkMesh primaryWorldChunk = findPrimaryWorldChunk(worldChunkFrame);
		return primaryWorldChunk != null && isUndergroundPrimaryWorldChunk(primaryWorldChunk);
	}

	private Renderer3DWorldChunkFrame.ChunkMesh findPrimaryWorldChunk(Renderer3DWorldChunkFrame worldChunkFrame) {
		Renderer3DWorldChunkFrame.ChunkMesh firstWorldChunk = null;
		for (Renderer3DWorldChunkFrame.ChunkMesh chunk : worldChunkFrame.getChunks()) {
			if (chunk.getChunkRole() != Renderer3DWorldChunkFrame.CHUNK_ROLE_WORLD) {
				continue;
			}
			if (firstWorldChunk == null) {
				firstWorldChunk = chunk;
			}
			// Overworld frames intentionally include upper-plane support chunks; the active
			// terrain chunk is the reliable sky/underground owner.
			if (chunk.getTerrainTriangles() > 0) {
				return chunk;
			}
		}
		return firstWorldChunk;
	}

	private boolean isUndergroundPrimaryWorldChunk(Renderer3DWorldChunkFrame.ChunkMesh chunk) {
		return chunk.getPlane() != 0
			|| worldUnitToTile(chunk.getOriginWorldZ()) >= UNDERGROUND_WORLD_TILE_Z_THRESHOLD;
	}

	private static int worldUnitToTile(int worldUnit) {
		return Math.floorDiv(worldUnit, TILE_SIZE);
	}

	private void drawSkyBackdrop(Frame frame, RendererDayNightCycle.Presentation presentation) throws Exception {
		if (OpenGLSkySettings.getMode() == OpenGLSkySettings.Mode.WORLD_DOME
			&& worldChunkRenderer != null
			&& frame != null
			&& frame.renderer3DFrame != null) {
			worldChunkRenderer.drawWorldAnchoredSky(frame.renderer3DFrame, presentation);
			return;
		}
		useUnitProjection();
		gl.glDisable(gl.GL_DEPTH_TEST);
		gl.glDisable(gl.GL_TEXTURE_2D);
		gl.glDisable(gl.GL_BLEND);

		float pitchOffset = skyPitchOffset(frame);
		drawSkyGradient(presentation, pitchOffset);
		float skyBrightness = clamp01((presentation.skyRed + presentation.skyGreen + presentation.skyBlue) / 3.0f);
		float skyRotation = skyRotationOffset(frame);
		float cloudAlpha = 0.055f + skyBrightness * 0.095f;
		float cloudRed = mix(presentation.fogRed, 1.0f, 0.34f);
		float cloudGreen = mix(presentation.fogGreen, 1.0f, 0.34f);
		float cloudBlue = mix(presentation.fogBlue, 1.0f, 0.34f);
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		drawSkyCloudGroup(0.18f, skyRotation, pitchOffset, 0.18f, 0.19f, cloudRed, cloudGreen, cloudBlue, cloudAlpha);
		drawSkyCloudGroup(0.62f, skyRotation, pitchOffset, 0.29f, 0.15f, cloudRed, cloudGreen, cloudBlue, cloudAlpha * 0.70f);
		drawSkyCloudGroup(0.88f, skyRotation, pitchOffset, 0.41f, 0.13f, cloudRed, cloudGreen, cloudBlue, cloudAlpha * 0.48f);
		drawSkyCloudGroup(0.42f, skyRotation * 0.92f, pitchOffset, -0.04f, 0.12f, cloudRed, cloudGreen, cloudBlue, cloudAlpha * 0.42f);
		gl.glDisable(gl.GL_BLEND);
		gl.glEnable(gl.GL_TEXTURE_2D);
		gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
	}

	private void drawSkyGradient(RendererDayNightCycle.Presentation presentation, float pitchOffset) throws Exception {
		float topRed = clamp01(presentation.skyRed * 0.82f);
		float topGreen = clamp01(presentation.skyGreen * 0.86f);
		float topBlue = clamp01(presentation.skyBlue * 1.05f);
		float horizonRed = mix(presentation.skyRed, presentation.fogRed, 0.78f);
		float horizonGreen = mix(presentation.skyGreen, presentation.fogGreen, 0.78f);
		float horizonBlue = mix(presentation.skyBlue, presentation.fogBlue, 0.78f);
		float topY = -0.42f + pitchOffset;
		float horizonY = 1.12f + pitchOffset;
		drawUnitSolidQuad(0.0f, -1.0f, 1.0f, topY, topRed, topGreen, topBlue, 1.0f);
		drawUnitGradientQuad(
			0.0f,
			topY,
			1.0f,
			horizonY,
			topRed,
			topGreen,
			topBlue,
			horizonRed,
			horizonGreen,
			horizonBlue);
		drawUnitSolidQuad(0.0f, horizonY, 1.0f, 2.0f, horizonRed, horizonGreen, horizonBlue, 1.0f);
	}

	private void drawUnitGradientQuad(
		float x0,
		float y0,
		float x1,
		float y1,
		float topRed,
		float topGreen,
		float topBlue,
		float bottomRed,
		float bottomGreen,
		float bottomBlue) throws Exception {
		gl.glBegin(gl.GL_QUADS);
		gl.glColor4f(topRed, topGreen, topBlue, 1.0f);
		gl.glVertex3f(x0, y0, 0.0f);
		gl.glVertex3f(x1, y0, 0.0f);
		gl.glColor4f(bottomRed, bottomGreen, bottomBlue, 1.0f);
		gl.glVertex3f(x1, y1, 0.0f);
		gl.glVertex3f(x0, y1, 0.0f);
		gl.glEnd();
	}

	private float skyRotationOffset(Frame frame) {
		float cameraYaw = 0.0f;
		if (frame != null && frame.renderer3DFrame != null) {
			cameraYaw = wrap01(frame.renderer3DFrame.getCameraRotationY() / 1024.0f);
		}
		return wrap01(cameraYaw + RendererDayNightCycle.currentCycleFraction() * 0.18f);
	}

	private float skyPitchOffset(Frame frame) {
		if (frame == null || frame.renderer3DFrame == null) {
			return 0.0f;
		}
		float pitch = frame.renderer3DFrame.getCameraRotationX() & 1023;
		float normalizedPitch = (pitch - 912.0f) / 255.0f;
		return clamp(normalizedPitch, -0.75f, 0.75f) * 1.05f;
	}

	private void drawSkyCloudGroup(
		float baseX,
		float rotation,
		float pitchOffset,
		float y,
		float scale,
		float red,
		float green,
		float blue,
		float alpha) throws Exception {
		float x = wrap01(baseX + rotation);
		float cloudY = y + pitchOffset;
		for (int repeat = -1; repeat <= 1; repeat++) {
			float shiftedX = x + repeat;
			for (int verticalRepeat = -1; verticalRepeat <= 1; verticalRepeat++) {
				float shiftedY = cloudY + verticalRepeat;
				if (shiftedY < -0.45f || shiftedY > 1.45f) {
					continue;
				}
				drawSoftSkyEllipse(shiftedX - scale * 0.62f, shiftedY + scale * 0.10f, scale * 0.78f, scale * 0.25f, red, green, blue, alpha * 0.45f);
				drawSoftSkyEllipse(shiftedX, shiftedY + scale * 0.12f, scale * 1.05f, scale * 0.34f, red, green, blue, alpha * 0.74f);
				drawSoftSkyEllipse(shiftedX + scale * 0.66f, shiftedY + scale * 0.09f, scale * 0.72f, scale * 0.24f, red, green, blue, alpha * 0.48f);
				drawSoftSkyEllipse(shiftedX - scale * 0.26f, shiftedY - scale * 0.12f, scale * 0.46f, scale * 0.20f, red, green, blue, alpha * 0.54f);
				drawSoftSkyEllipse(shiftedX + scale * 0.22f, shiftedY - scale * 0.17f, scale * 0.52f, scale * 0.22f, red, green, blue, alpha * 0.62f);
			}
		}
	}

	private void drawSoftSkyEllipse(
		float centerX,
		float centerY,
		float radiusX,
		float radiusY,
		float red,
		float green,
		float blue,
		float alpha) throws Exception {
		int strips = 17;
		for (int strip = 0; strip < strips; strip++) {
			float position = (strip + 0.5f) / strips * 2.0f - 1.0f;
			float widthFactor = (float) Math.sqrt(Math.max(0.0f, 1.0f - position * position));
			float edgeFade = 1.0f - Math.abs(position);
			float stripAlpha = alpha * edgeFade * edgeFade;
			float y0 = centerY + position * radiusY - radiusY / strips;
			float y1 = centerY + position * radiusY + radiusY / strips;
			drawUnitSolidQuad(
				centerX - radiusX * widthFactor,
				y0,
				centerX + radiusX * widthFactor,
				y1,
				red,
				green,
				blue,
				stripAlpha);
		}
	}

	private void drawSkyStars(float rotation, float pitchOffset, float alpha) throws Exception {
		if (alpha <= 0.01f) {
			return;
		}
		drawSkyStar(0.08f, 0.11f, rotation, pitchOffset, 0.0021f, 1.0f, 0.86f, 0.18f, alpha * 0.72f);
		drawSkyStar(0.17f, 0.32f, rotation, pitchOffset, 0.0016f, 0.92f, 0.96f, 1.0f, alpha * 0.54f);
		drawSkyStar(0.25f, 0.19f, rotation, pitchOffset, 0.0019f, 1.0f, 0.92f, 0.30f, alpha * 0.74f);
		drawSkyStar(0.34f, 0.40f, rotation, pitchOffset, 0.0014f, 1.0f, 0.80f, 0.12f, alpha * 0.52f);
		drawSkyStar(0.46f, 0.14f, rotation, pitchOffset, 0.0022f, 0.96f, 0.98f, 1.0f, alpha * 0.70f);
		drawSkyStar(0.56f, 0.27f, rotation, pitchOffset, 0.0015f, 1.0f, 0.88f, 0.20f, alpha * 0.58f);
		drawSkyStar(0.66f, 0.08f, rotation, pitchOffset, 0.0018f, 0.93f, 0.97f, 1.0f, alpha * 0.58f);
		drawSkyStar(0.78f, 0.35f, rotation, pitchOffset, 0.0020f, 1.0f, 0.82f, 0.14f, alpha * 0.68f);
		drawSkyStar(0.89f, 0.18f, rotation, pitchOffset, 0.0015f, 0.97f, 0.98f, 1.0f, alpha * 0.48f);
		drawSkyStar(0.96f, 0.43f, rotation, pitchOffset, 0.0018f, 1.0f, 0.86f, 0.22f, alpha * 0.60f);
		drawSkyStar(0.12f, -0.08f, rotation, pitchOffset, 0.0015f, 1.0f, 0.78f, 0.10f, alpha * 0.46f);
		drawSkyStar(0.39f, -0.03f, rotation, pitchOffset, 0.0017f, 1.0f, 0.84f, 0.16f, alpha * 0.56f);
		drawSkyStar(0.53f, 0.49f, rotation, pitchOffset, 0.0014f, 1.0f, 0.78f, 0.10f, alpha * 0.44f);
		drawSkyStar(0.72f, -0.12f, rotation, pitchOffset, 0.0019f, 1.0f, 0.82f, 0.14f, alpha * 0.62f);
		drawSkyStar(0.84f, 0.04f, rotation, pitchOffset, 0.0015f, 1.0f, 0.80f, 0.12f, alpha * 0.50f);
		drawSkyStar(0.03f, 0.28f, rotation, pitchOffset, 0.0014f, 1.0f, 0.82f, 0.12f, alpha * 0.52f);
		drawSkyStar(0.14f, 0.49f, rotation, pitchOffset, 0.0017f, 0.95f, 0.98f, 1.0f, alpha * 0.48f);
		drawSkyStar(0.21f, 0.04f, rotation, pitchOffset, 0.0013f, 1.0f, 0.76f, 0.08f, alpha * 0.44f);
		drawSkyStar(0.29f, 0.55f, rotation, pitchOffset, 0.0016f, 1.0f, 0.86f, 0.18f, alpha * 0.54f);
		drawSkyStar(0.41f, 0.25f, rotation, pitchOffset, 0.0015f, 0.94f, 0.97f, 1.0f, alpha * 0.50f);
		drawSkyStar(0.49f, 0.36f, rotation, pitchOffset, 0.0018f, 1.0f, 0.80f, 0.12f, alpha * 0.66f);
		drawSkyStar(0.59f, 0.02f, rotation, pitchOffset, 0.0014f, 1.0f, 0.78f, 0.10f, alpha * 0.48f);
		drawSkyStar(0.64f, 0.48f, rotation, pitchOffset, 0.0015f, 1.0f, 0.84f, 0.18f, alpha * 0.52f);
		drawSkyStar(0.71f, 0.22f, rotation, pitchOffset, 0.0016f, 0.94f, 0.98f, 1.0f, alpha * 0.50f);
		drawSkyStar(0.81f, 0.53f, rotation, pitchOffset, 0.0017f, 1.0f, 0.80f, 0.12f, alpha * 0.58f);
		drawSkyStar(0.92f, 0.30f, rotation, pitchOffset, 0.0015f, 1.0f, 0.76f, 0.08f, alpha * 0.54f);
		drawSkyStar(0.98f, 0.07f, rotation, pitchOffset, 0.0013f, 0.96f, 0.98f, 1.0f, alpha * 0.42f);
		drawSkyStar(0.31f, -0.16f, rotation, pitchOffset, 0.0016f, 1.0f, 0.82f, 0.14f, alpha * 0.48f);
		drawSkyStar(0.61f, -0.07f, rotation, pitchOffset, 0.0015f, 1.0f, 0.78f, 0.08f, alpha * 0.46f);
		drawSkyStar(0.74f, 0.60f, rotation, pitchOffset, 0.0014f, 1.0f, 0.84f, 0.16f, alpha * 0.42f);
	}

	private void drawSkyStar(
		float baseX,
		float y,
		float rotation,
		float pitchOffset,
		float size,
		float red,
		float green,
		float blue,
		float alpha) throws Exception {
		float x = wrap01(baseX + rotation * 1.35f);
		float starY = y + pitchOffset;
		for (int repeat = -1; repeat <= 1; repeat++) {
			float shiftedX = x + repeat;
			for (int verticalRepeat = -1; verticalRepeat <= 1; verticalRepeat++) {
				float shiftedY = starY + verticalRepeat;
				if (shiftedY < -0.08f || shiftedY > 1.08f) {
					continue;
				}
				drawUnitSolidQuad(
					shiftedX - size * 3.2f,
					shiftedY - size * 3.2f,
					shiftedX + size * 3.2f,
					shiftedY + size * 3.2f,
					red,
					green,
					blue,
					alpha * 0.08f);
				drawUnitSolidQuad(
					shiftedX - size * 1.9f,
					shiftedY - size * 1.9f,
					shiftedX + size * 1.9f,
					shiftedY + size * 1.9f,
					red,
					green,
					blue,
					alpha * 0.16f);
				drawUnitSolidQuad(
					shiftedX - size,
					shiftedY - size,
					shiftedX + size,
					shiftedY + size,
					red,
					green,
					blue,
					alpha * 1.15f);
			}
		}
	}

	private void drawUnitSolidQuad(
		float x0,
		float y0,
		float x1,
		float y1,
		float red,
		float green,
		float blue,
		float alpha) throws Exception {
		gl.glColor4f(red, green, blue, clamp01(alpha));
		gl.glBegin(gl.GL_QUADS);
		gl.glVertex3f(x0, y0, 0.0f);
		gl.glVertex3f(x1, y0, 0.0f);
		gl.glVertex3f(x1, y1, 0.0f);
		gl.glVertex3f(x0, y1, 0.0f);
		gl.glEnd();
	}

	private static float mix(float from, float to, float amount) {
		return from + (to - from) * clamp01(amount);
	}

	private static float wrap01(float value) {
		float wrapped = value - (float) Math.floor(value);
		return wrapped < 0.0f ? wrapped + 1.0f : wrapped;
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static float clamp01(float value) {
		if (value <= 0.0f) {
			return 0.0f;
		}
		if (value >= 1.0f) {
			return 1.0f;
		}
		return value;
	}

	private void runOpenGLPass(OpenGLPassAction action) throws Exception {
		gl.glPushAttrib(gl.GL_ALL_ATTRIB_BITS);
		gl.glPushClientAttrib(gl.GL_CLIENT_ALL_ATTRIB_BITS);
		try {
			action.run();
		} finally {
			gl.glPopClientAttrib();
			gl.glPopAttrib();
		}
	}

	private void uploadTexture(Frame frame) throws Exception {
		gl.glBindTexture(gl.GL_TEXTURE_2D, textureId);
		applyTextureFilter(gl.GL_NEAREST);

		if (textureWidth != frame.sourceWidth || textureHeight != frame.sourceHeight) {
			gl.glTexImage2D(
				gl.GL_TEXTURE_2D,
				0,
				gl.GL_RGBA,
				frame.sourceWidth,
				frame.sourceHeight,
				0,
				gl.GL_RGBA,
				gl.GL_UNSIGNED_BYTE,
				frame.pixels());
			textureWidth = frame.sourceWidth;
			textureHeight = frame.sourceHeight;
		} else {
			gl.glTexSubImage2D(
				gl.GL_TEXTURE_2D,
				0,
				0,
				0,
				frame.sourceWidth,
				frame.sourceHeight,
				gl.GL_RGBA,
				gl.GL_UNSIGNED_BYTE,
				frame.pixels());
		}
	}

	private void drawTexturedQuad() throws Exception {
		gl.glDisable(gl.GL_DEPTH_TEST);
		gl.glDisable(gl.GL_SCISSOR_TEST);
		gl.glDisable(gl.GL_BLEND);
		gl.glEnable(gl.GL_TEXTURE_2D);
		gl.glBindTexture(gl.GL_TEXTURE_2D, textureId);
		gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
		useUnitProjection();

		applyPixelTextureFilter();
		drawUnitTexturedQuad();
	}

	private void applyPixelTextureFilter() throws Exception {
		applyTextureFilter(gl.GL_NEAREST);
	}

	private void applyTextTextureFilter() throws Exception {
		applyTextureFilter(
			viewportPresenter.textSmoothingAlpha() > 0.0f ? gl.GL_LINEAR : gl.GL_NEAREST);
	}

	private void applyTextureFilter(int filter) throws Exception {
		gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MIN_FILTER, filter);
		gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MAG_FILTER, filter);
	}

	private RemasterShadowInventory inspectRemasterShadowInventory(
		Renderer3DWorldChunkFrame worldChunkFrame) {
		if (worldChunkRenderer != null) {
			return worldChunkRenderer.inspectRemasterShadowInventory(worldChunkFrame);
		}
		return RemasterShadowClassifier.inspectInventory(worldChunkFrame);
	}

	private void drawWorldMesh(Frame frame, boolean worldReplacementComposite) throws Exception {
		long chunkUploadPhaseNanos = 0L;
		long projectedMeshPhaseNanos = 0L;
		long chunkDrawPhaseNanos = 0L;
		Renderer3DMeshFrame renderer3DMeshFrame =
			frame.renderer3DFrame == null ? null : frame.renderer3DFrame.getMeshFrame();
		Renderer3DWorldChunkFrame worldChunkFrame =
			frame.renderer3DFrame == null ? Renderer3DWorldChunkFrame.EMPTY : frame.renderer3DFrame.getWorldChunkFrame();
		RenderTelemetry.recordOpenGLWorldChunkFrame(
			worldChunkFrame.getChunkCount(),
			worldChunkFrame.getTotalVertexCount(),
			worldChunkFrame.getTotalIndexCount(),
			worldChunkFrame.getTotalTriangleCount());
		RenderTelemetry.recordOpenGLWorldMaterialFamilies(
			worldChunkFrame);
		RemasterShadowInventory shadowInventory = RenderTelemetry.isEnabled()
			? inspectRemasterShadowInventory(worldChunkFrame)
			: RemasterShadowInventory.EMPTY;
		RenderTelemetry.recordOpenGLRemasterShadowInventory(
			shadowInventory.receiverChunks,
			shadowInventory.receiverTriangles,
			shadowInventory.totalCasters,
			shadowInventory.wallCasters,
			shadowInventory.gameObjectCasters,
			shadowInventory.wallObjectCasters,
			shadowInventory.outdoorOnlyCasters,
			shadowInventory.clippingCandidates,
			shadowInventory.roofedReceivers,
			shadowInventory.outdoorReceivers,
			shadowInventory.unknownReceivers,
			shadowInventory.roofedCasters,
			shadowInventory.outdoorCasters,
			shadowInventory.unknownCasters,
			shadowInventory.sunlightEligibleCasters,
			shadowInventory.sunlightSuppressedRoofedCasters,
			shadowInventory.sunlightSuppressedUnknownCasters);
		boolean canDrawProjectedMesh =
			WORLD_MESH_ENABLED
				&& worldMeshRenderer != null
				&& renderer3DMeshFrame != null
				&& renderer3DMeshFrame.getTriangleCount() > 0;
		boolean canDrawProjectedStaticFallback =
			canDrawProjectedMesh
				&& !Renderer3DSettings.canSkipProjectedWorldCapture()
				&& (WORLD_MESH_VISIBLE || WORLD_MESH_TEXTURED_VISIBLE);
		if (WORLD_MESH_ENABLED && worldChunkRenderer != null) {
			long chunkUploadPhaseStart = RenderTelemetry.now();
			boolean budgetResidentChunkUploads =
				canDrawProjectedStaticFallback && !Renderer3DSettings.canSkipLegacyWorldRaster();
			OpenGLWorldChunkUploadStats chunkUploadStats = worldChunkRenderer.upload(
				frame.renderer3DFrame,
				worldChunkFrame,
				WORLD_CHUNKS_TEXTURED_VISIBLE,
				budgetResidentChunkUploads);
			chunkUploadPhaseNanos = RenderTelemetry.elapsedSince(chunkUploadPhaseStart);
			RenderTelemetry.recordOpenGLWorldChunkUpload(
				chunkUploadStats.requestedChunks,
				chunkUploadStats.uploadedChunks,
				chunkUploadStats.reusedChunks,
				chunkUploadStats.deferredChunks,
				chunkUploadStats.evictedChunks,
				chunkUploadStats.reason,
				chunkUploadStats.budgetUsedNanos,
				chunkUploadStats.budgetLimitNanos);
		}
		boolean requestedResidentChunkReplacementComposite =
			isResidentChunkReplacementRequested(frame);
		ResidentChunkReadiness residentChunkReadiness =
			requestedResidentChunkReplacementComposite && worldChunkRenderer != null
				? worldChunkRenderer.inspectDrawableResidentStaticWorld(
					frame.renderer3DFrame,
					worldChunkFrame,
					WORLD_CHUNKS_TEXTURED_VISIBLE)
				: ResidentChunkReadiness.notRequested(
					requestedResidentChunkReplacementComposite ? "no-chunk-renderer" : "not-requested");
		boolean residentChunkReplacementComposite =
			worldReplacementComposite
				&& requestedResidentChunkReplacementComposite
				&& WORLD_CHUNKS_TRUSTED_REPLACEMENT
				&& residentChunkReadiness.canReplace;
		boolean residentChunksReadyThisFrame =
			requestedResidentChunkReplacementComposite
				&& WORLD_CHUNKS_TRUSTED_REPLACEMENT
				&& residentChunkReadiness.canReplace;
		String residentReplacementReason = residentReplacementReason(
			worldReplacementComposite,
			requestedResidentChunkReplacementComposite,
			residentChunkReplacementComposite,
			residentChunkReadiness);
		RenderTelemetry.recordOpenGLResidentChunkReplacement(
			requestedResidentChunkReplacementComposite,
			residentChunkReplacementComposite,
			residentReplacementReason,
			residentChunkReadiness.drawableTerrainBatches,
			residentChunkReadiness.drawableWallBatches,
			residentChunkReadiness.drawableRoofBatches);
		boolean remasterTerrainShadowMaskPrepared = false;
		if (worldChunkRenderer != null) {
			worldChunkRenderer.clearPreparedRemasterTerrainShadowMask();
		}
		// RENDERER-V2 OWNER: resident chunks are the preferred static-world path.
		// Projected mesh drawing below remains only as fallback/diagnostic bridge.
		boolean drawProjectedMeshVisible =
			!residentChunkReplacementComposite
				&& !residentChunksReadyThisFrame
				&& (WORLD_MESH_VISIBLE || WORLD_MESH_TEXTURED_VISIBLE);
		// LEGACY BRIDGE: projected object mesh covers scenery/wall objects only
		// while resident object chunks are unavailable or disabled.
		boolean drawProjectedObjectMeshVisible =
			residentChunkReplacementComposite
				&& WORLD_MESH_TEXTURED_VISIBLE
				&& !WORLD_CHUNKS_RESIDENT_OBJECTS;
		if (canDrawProjectedMesh && drawProjectedMeshVisible) {
			useWorldMeshProjection(frame);
			long projectedMeshPhaseStart = RenderTelemetry.now();
			worldMeshRenderer.uploadAndMaybeDrawOwnedBaseWorld(
				renderer3DMeshFrame,
				WORLD_MESH_VISIBLE,
				WORLD_MESH_TEXTURED_VISIBLE,
				cameraToClipMatrixBuffer);
			projectedMeshPhaseNanos += RenderTelemetry.elapsedSince(projectedMeshPhaseStart);
			useSourceProjection(frame.sourceWidth, frame.sourceHeight);
		} else if (canDrawProjectedMesh && !residentChunkReplacementComposite) {
			long projectedMeshPhaseStart = RenderTelemetry.now();
			worldMeshRenderer.uploadAndMaybeDraw(renderer3DMeshFrame, false, false, null);
			projectedMeshPhaseNanos += RenderTelemetry.elapsedSince(projectedMeshPhaseStart);
		}
		boolean drawResidentChunkFallback =
			!worldReplacementComposite
				&& (!canDrawProjectedStaticFallback || residentChunksReadyThisFrame);
		boolean drawResidentChunkDiagnostic =
			(WORLD_CHUNKS_VISIBLE || WORLD_CHUNKS_FILLED_VISIBLE || WORLD_CHUNKS_TEXTURED_VISIBLE)
				&& (!WORLD_CHUNKS_REPLACEMENT_COMPOSITE
					|| residentChunkReplacementComposite
					|| residentChunksReadyThisFrame
					|| drawResidentChunkFallback);
		if (shouldDrawRemasterTerrainShadowMask()
			&& drawResidentChunkDiagnostic
			&& worldChunkRenderer != null
			&& frame.renderer3DFrame != null
			&& worldChunkRenderer.canApplyRemasterTerrainShadowMaskInChunkShader(WORLD_CHUNKS_TEXTURED_VISIBLE)) {
			remasterTerrainShadowMaskPrepared =
				worldChunkRenderer.prepareRemasterTerrainShadowMask(frame.renderer3DFrame);
		}
		if (drawResidentChunkDiagnostic
			&& worldChunkRenderer != null
			&& frame.renderer3DFrame != null) {
			long chunkDrawPhaseStart = RenderTelemetry.now();
			OpenGLWorldChunkDrawStats chunkDrawStats =
				worldChunkRenderer.drawDiagnostic(
					frame.renderer3DFrame,
					WORLD_CHUNKS_FILLED_VISIBLE,
					WORLD_CHUNKS_TEXTURED_VISIBLE);
			chunkDrawPhaseNanos = RenderTelemetry.elapsedSince(chunkDrawPhaseStart);
			RenderTelemetry.recordOpenGLWorldChunkDraw(
				chunkDrawStats.drawnChunks,
				chunkDrawStats.drawnTriangles,
				chunkDrawStats.drawnTerrainTriangles,
				chunkDrawStats.drawnWallTriangles,
				chunkDrawStats.drawnRoofTriangles,
				chunkDrawStats.drawnGameObjectTriangles,
				chunkDrawStats.drawnWallObjectTriangles,
				chunkDrawStats.drawnOtherTriangles,
				chunkDrawStats.fallbackTriangles,
				chunkDrawStats.skippedTriangles,
				chunkDrawStats.shadowProofChunks,
				chunkDrawStats.shadowProofIndices,
				chunkDrawStats.consideredChunks,
				chunkDrawStats.culledChunks,
				chunkDrawStats.consideredBatches,
				chunkDrawStats.drawnBatches,
				chunkDrawStats.culledBatches,
				chunkDrawStats.drawCalls,
				chunkDrawStats.textureBinds);
			worldChunkRenderer.drawBelowTerrainDepthFloor(frame.renderer3DFrame);
			useSourceProjection(frame.sourceWidth, frame.sourceHeight);
		}
		if (canDrawProjectedMesh && drawProjectedObjectMeshVisible) {
			useWorldMeshProjection(frame);
			long projectedMeshPhaseStart = RenderTelemetry.now();
			worldMeshRenderer.uploadAndMaybeDrawObjects(renderer3DMeshFrame, cameraToClipMatrixBuffer);
			projectedMeshPhaseNanos += RenderTelemetry.elapsedSince(projectedMeshPhaseStart);
			useSourceProjection(frame.sourceWidth, frame.sourceHeight);
		}
		if (REMASTER_SHADOW_INVENTORY_DEBUG
			&& worldChunkRenderer != null
			&& frame.renderer3DFrame != null) {
			worldChunkRenderer.drawRemasterShadowInventoryDebug(frame.renderer3DFrame);
			useSourceProjection(frame.sourceWidth, frame.sourceHeight);
		}
		if (shouldDrawRemasterTerrainShadowMask()
			&& worldChunkRenderer != null
			&& frame.renderer3DFrame != null
			&& !remasterTerrainShadowMaskPrepared) {
			worldChunkRenderer.drawRemasterTerrainShadowMask(frame.renderer3DFrame);
			useSourceProjection(frame.sourceWidth, frame.sourceHeight);
		}
		if (WorldEditorBuildSettings.isEnabled()
			&& worldChunkRenderer != null
			&& frame.renderer3DFrame != null) {
			worldChunkRenderer.drawWorldEditorBuildGrid(frame.renderer3DFrame);
			useSourceProjection(frame.sourceWidth, frame.sourceHeight);
		}
		RenderTelemetry.recordOpenGLWorldPhaseBreakdown(
			chunkUploadPhaseNanos,
			projectedMeshPhaseNanos,
			chunkDrawPhaseNanos);
	}

	private String residentReplacementReason(
		boolean worldReplacementComposite,
		boolean requestedResidentChunkReplacementComposite,
		boolean residentChunkReplacementComposite,
		ResidentChunkReadiness residentChunkReadiness) {
		if (requestedResidentChunkReplacementComposite
			&& !residentChunkReplacementComposite
			&& residentChunkReadiness.canReplace
			&& !worldReplacementComposite) {
			return "base-active-this-frame";
		}
		if (requestedResidentChunkReplacementComposite
			&& !residentChunkReplacementComposite
			&& residentChunkReadiness.canReplace
			&& !WORLD_CHUNKS_TRUSTED_REPLACEMENT) {
			return "not-trusted";
		}
		return residentChunkReadiness.reason;
	}



	private void drawWorldSprites(Frame frame) throws Exception {
		if (!WORLD_SPRITES_VISIBLE
			|| spriteTextureCache == null
			|| frame.renderer3DFrame == null
			|| frame.renderer2DFrame == null
			|| !hasVisibleOpenGLWorldPass()) {
			return;
		}

		List<Renderer3DFrame.SpriteAnchor> anchors = frame.renderer3DFrame.getSpriteAnchors();
		Renderer2DFrame.SpriteCommand[] commands = frame.renderer2DFrame.getSpriteCommands();
		if (anchors.isEmpty() || commands.length == 0) {
			RenderTelemetry.recordOpenGLWorldSpriteFrame(anchors.size(), 0, 0);
			return;
		}

		WorldSpriteMatch[] matches = matchWorldSpriteAnchors(anchors, commands);
		int matched = 0;
		for (WorldSpriteMatch match : matches) {
			if (match != null) {
				matched++;
			}
		}
		if (matched == 0) {
			RenderTelemetry.recordOpenGLWorldSpriteFrame(anchors.size(), 0, 0);
			return;
		}

		useSourceProjection(frame.sourceWidth, frame.sourceHeight);
		gl.glEnable(gl.GL_TEXTURE_2D);
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		gl.glDisable(gl.GL_DEPTH_TEST);
		int drawn = 0;
		try {
			for (WorldSpriteMatch match : matches) {
				if (match == null) {
					continue;
				}
				drawWorldSpriteMatch(match);
				drawn++;
			}
		} finally {
			gl.glDisable(gl.GL_BLEND);
			gl.glEnable(gl.GL_TEXTURE_2D);
			gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
		}
		RenderTelemetry.recordOpenGLWorldSpriteFrame(anchors.size(), matched, drawn);
		useSourceProjection(frame.sourceWidth, frame.sourceHeight);
	}

	private boolean hasVisibleOpenGLWorldPass() {
		return WORLD_MESH_VISIBLE
			|| WORLD_MESH_TEXTURED_VISIBLE
			|| WORLD_CHUNKS_VISIBLE
			|| WORLD_CHUNKS_FILLED_VISIBLE
			|| WORLD_CHUNKS_TEXTURED_VISIBLE;
	}

	private WorldSpriteMatch[] matchWorldSpriteAnchors(
		List<Renderer3DFrame.SpriteAnchor> anchors,
		Renderer2DFrame.SpriteCommand[] commands) {
		return OpenGLCompositeSceneBuilder.matchWorldSpriteAnchors(anchors, commands);
	}

	private WorldSpriteAnchorMatch classifyWorldSpriteAnchorMatch(
		Frame frame,
		Renderer2DFrame.SpriteCommand command,
		Renderer3DFrame.SpriteAnchor anchor) {
		return OpenGLCompositeSceneBuilder.classifyWorldSpriteAnchorMatch(frame, command, anchor);
	}

	private void drawWorldSpriteMatch(WorldSpriteMatch match) throws Exception {
		Renderer2DFrame.SpriteCommand command = match.command;
		Renderer3DFrame.SpriteAnchor anchor = match.anchor;
		OpenGLTextureRegion region = uploadSpriteTexture(command.getSprite(), command.getTransform());
		float commandAlpha = command.getAlpha() / (float) Renderer2DFrame.SpriteCommand.FULL_ALPHA;
		float spriteAlphaScale = WORLD_REPLACEMENT_COMPOSITE ? 1.0f : WORLD_SPRITE_DIAGNOSTIC_ALPHA;
		float alpha = spriteAlphaScale * commandAlpha;
		float topX0 = command.getTopX16() / 65536.0f;
		float bottomX0 = command.getBottomX16() / 65536.0f;
		float y0 = command.getY();
		float y1 = command.getY() + command.getHeight();
		float topX1 = topX0 + command.getWidth();
		float bottomX1 = bottomX0 + command.getWidth();
		float uSpan = region.getU1() - region.getU0();
		float vSpan = region.getV1() - region.getV0();
		float u0 = region.getU0() + uSpan * command.getSourceX() / region.getWidth();
		float v0 = region.getV0() + vSpan * command.getSourceY() / region.getHeight();
		float u1 = region.getU0() + uSpan * (command.getSourceX() + command.getSourceWidth()) / region.getWidth();
		float v1 = region.getV0() + vSpan * (command.getSourceY() + command.getSourceHeight()) / region.getHeight();
		float leftU = command.isMirrorX() ? u1 : u0;
		float rightU = command.isMirrorX() ? u0 : u1;

		gl.glBindTexture(gl.GL_TEXTURE_2D, region.getTextureId());
		applyPixelTextureFilter();
		useWorldToneColor(alpha);
		drawScreenQuad(
			topX0,
			y0,
			leftU,
			v0,
			topX1,
			y0,
			rightU,
			v0,
			bottomX1,
			y1,
			rightU,
			v1,
			bottomX0,
			y1,
			leftU,
			v1);
	}

	private void useWorldToneColor(float alpha) throws Exception {
		RendererDayNightCycle.Presentation presentation = RendererDayNightCycle.currentPresentation();
		gl.glColor4f(
			presentation.redMultiplier,
			presentation.greenMultiplier,
			presentation.blueMultiplier,
			alpha);
	}

	private void drawSpriteOverlay(Frame frame, boolean worldReplacementComposite) throws Exception {
		Renderer2DFrame renderer2DFrame = frame.renderer2DFrame;
		boolean replayOpenGLWorldUi = worldReplacementComposite;
		if ((!Renderer2DSettings.isOpenGLSpriteOverlayEnabled() && !replayOpenGLWorldUi)
			|| renderer2DFrame == null) {
			return;
		}

		Renderer2DFrame.SpriteCommand[] commands = renderer2DFrame.getSpriteCommands();
		Renderer2DFrame.TextCommand[] textCommands = renderer2DFrame.getTextCommands();
		Renderer2DFrame.PrimitiveCommand[] primitiveCommands = renderer2DFrame.getPrimitiveCommands();
		Renderer2DFrame.RotatedSpriteCommand[] rotatedSpriteCommands = renderer2DFrame.getRotatedSpriteCommands();
		Renderer2DFrame.CircleCommand[] circleCommands = renderer2DFrame.getCircleCommands();
		boolean replayNativeBaseCommands = shouldReplayNativeBaseCommands(renderer2DFrame);
		if (!Renderer2DSettings.isOpenGLSpriteOverlayEnabled() && replayOpenGLWorldUi) {
			drawOpenGLWorldCompositeOverlay(
				frame,
				renderer2DFrame,
				commands,
				textCommands,
				primitiveCommands,
				rotatedSpriteCommands,
				circleCommands);
			return;
		}
		if (Renderer2DSettings.isNativeUiOverlayMode()
			&& !Renderer2DSettings.canReplaceUiSpritesWithOpenGL()) {
			RenderTelemetry.recordSpriteOverlayFrame(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
			return;
		}
		if (commands.length == 0
			&& textCommands.length == 0
			&& (!replayNativeBaseCommands || primitiveCommands.length == 0)
			&& (!replayNativeBaseCommands || rotatedSpriteCommands.length == 0)
			&& (!replayNativeBaseCommands || circleCommands.length == 0)) {
			RenderTelemetry.recordSpriteOverlayFrame(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
			return;
		}

		int staticReplayed = 0;
		int visibleReplayed = 0;
		int skippedOrdered = 0;
		int skippedInvisible = 0;
		int skippedAtlasFull = 0;
		int visiblePixels = 0;
		int sceneCommands = 0;
		int worldCommands = 0;
		int uiCommands = 0;
		int unknownCommands = 0;
		int[] directReplayedByPhase = new int[Renderer2DFrame.Phase.values().length];
		int[] visibleReplayedByPhase = new int[Renderer2DFrame.Phase.values().length];
		if (visibleSpriteTextureAtlas != null) {
			visibleSpriteTextureAtlas.beginFrame();
		}
		useSourceProjection(renderer2DFrame.getWidth(), renderer2DFrame.getHeight());
		boolean visibleReplay = Renderer2DSettings.canReplayVisibleOrderSensitiveSpritesAfterFrame();
		boolean phaseAwareReplay = Renderer2DSettings.canReplayPhaseAwareSpritesAfterFrame();
		boolean replayUiOverlayCommands =
			!Renderer2DSettings.isNativeUiOverlayMode() || replayNativeBaseCommands;
		for (Renderer2DFrame.SpriteCommand command : commands) {
			switch (command.getPhase()) {
				case SCENE:
					sceneCommands++;
					break;
				case WORLD_OVERLAY:
					worldCommands++;
					break;
				case UI_OVERLAY:
					uiCommands++;
					break;
				default:
					unknownCommands++;
					break;
			}
			if (phaseAwareReplay) {
				if (command.getPhase() == Renderer2DFrame.Phase.UI_OVERLAY && !replayUiOverlayCommands) {
					continue;
				}
				if (command.getPhase() == Renderer2DFrame.Phase.UI_OVERLAY && !command.requiresOrderedReplay()) {
					continue;
				} else {
					int commandVisiblePixels = drawVisibleSpriteCommand(frame, command);
					if (commandVisiblePixels > 0) {
						visibleReplayed++;
						visiblePixels += commandVisiblePixels;
						visibleReplayedByPhase[phaseIndex(command.getPhase())]++;
					} else if (commandVisiblePixels == VISIBLE_SPRITE_ATLAS_FULL) {
						skippedAtlasFull++;
					} else {
						skippedInvisible++;
					}
				}
				continue;
			}
			if (visibleReplay) {
				int commandVisiblePixels = drawVisibleSpriteCommand(frame, command);
				if (commandVisiblePixels > 0) {
					visibleReplayed++;
					visiblePixels += commandVisiblePixels;
					visibleReplayedByPhase[phaseIndex(command.getPhase())]++;
				} else if (commandVisiblePixels == VISIBLE_SPRITE_ATLAS_FULL) {
					skippedAtlasFull++;
				} else {
					skippedInvisible++;
				}
				continue;
			}
			if (command.requiresOrderedReplay()
				&& !Renderer2DSettings.canReplayOrderSensitiveSpritesAfterFrame()) {
				skippedOrdered++;
				continue;
			}
			drawSpriteCommand(command);
			staticReplayed++;
			directReplayedByPhase[phaseIndex(command.getPhase())]++;
		}
		if (phaseAwareReplay && replayUiOverlayCommands) {
			int spriteIndex = 0;
			int textIndex = 0;
			int primitiveIndex = 0;
			int rotatedSpriteIndex = 0;
			int circleIndex = 0;
			while (true) {
				while (spriteIndex < commands.length
					&& (commands[spriteIndex].getPhase() != Renderer2DFrame.Phase.UI_OVERLAY
						|| commands[spriteIndex].requiresOrderedReplay())) {
					spriteIndex++;
				}
				while (textIndex < textCommands.length
					&& textCommands[textIndex].getPhase() != Renderer2DFrame.Phase.UI_OVERLAY) {
					textIndex++;
				}
				while (primitiveIndex < primitiveCommands.length
					&& (!replayNativeBaseCommands
						|| primitiveCommands[primitiveIndex].getPhase() != Renderer2DFrame.Phase.UI_OVERLAY)) {
					primitiveIndex++;
				}
				while (rotatedSpriteIndex < rotatedSpriteCommands.length
					&& (!replayNativeBaseCommands
						|| rotatedSpriteCommands[rotatedSpriteIndex].getPhase() != Renderer2DFrame.Phase.UI_OVERLAY)) {
					rotatedSpriteIndex++;
				}
				while (circleIndex < circleCommands.length
					&& (!replayNativeBaseCommands
						|| circleCommands[circleIndex].getPhase() != Renderer2DFrame.Phase.UI_OVERLAY)) {
					circleIndex++;
				}

				boolean hasSprite = spriteIndex < commands.length;
				boolean hasText = textIndex < textCommands.length;
				boolean hasPrimitive = replayNativeBaseCommands && primitiveIndex < primitiveCommands.length;
				boolean hasRotatedSprite =
					replayNativeBaseCommands && rotatedSpriteIndex < rotatedSpriteCommands.length;
				boolean hasCircle = replayNativeBaseCommands && circleIndex < circleCommands.length;
				if (!hasSprite && !hasText && !hasPrimitive && !hasRotatedSprite && !hasCircle) {
					break;
				}
				int nextSequence = Integer.MAX_VALUE;
				if (hasSprite) {
					nextSequence = Math.min(nextSequence, commands[spriteIndex].getSequence());
				}
				if (hasText) {
					nextSequence = Math.min(nextSequence, textCommands[textIndex].getSequence());
				}
				if (hasPrimitive) {
					nextSequence = Math.min(nextSequence, primitiveCommands[primitiveIndex].getSequence());
				}
				if (hasRotatedSprite) {
					nextSequence = Math.min(nextSequence, rotatedSpriteCommands[rotatedSpriteIndex].getSequence());
				}
				if (hasCircle) {
					nextSequence = Math.min(nextSequence, circleCommands[circleIndex].getSequence());
				}

				if (hasSprite && commands[spriteIndex].getSequence() == nextSequence) {
					Renderer2DFrame.SpriteCommand command = commands[spriteIndex++];
					drawSpriteCommand(command);
					staticReplayed++;
					directReplayedByPhase[phaseIndex(command.getPhase())]++;
				} else if (hasText && textCommands[textIndex].getSequence() == nextSequence) {
					drawTextCommand(textCommands[textIndex++]);
				} else if (hasPrimitive && primitiveCommands[primitiveIndex].getSequence() == nextSequence) {
					drawPrimitiveCommand(primitiveCommands[primitiveIndex++]);
				} else if (hasRotatedSprite && rotatedSpriteCommands[rotatedSpriteIndex].getSequence() == nextSequence) {
					drawRotatedSpriteCommand(rotatedSpriteCommands[rotatedSpriteIndex++]);
				} else {
					drawCircleCommand(circleCommands[circleIndex++]);
				}
			}
		}
		gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
		gl.glEnable(gl.GL_TEXTURE_2D);
		RenderTelemetry.recordSpriteOverlayFrame(
			commands.length,
			staticReplayed,
			visibleReplayed,
			skippedOrdered,
			skippedInvisible,
			skippedAtlasFull,
			visiblePixels,
			sceneCommands,
			worldCommands,
			uiCommands,
			unknownCommands,
			directReplayedByPhase[Renderer2DFrame.Phase.SCENE.ordinal()],
			directReplayedByPhase[Renderer2DFrame.Phase.WORLD_OVERLAY.ordinal()],
			directReplayedByPhase[Renderer2DFrame.Phase.UI_OVERLAY.ordinal()],
			directReplayedByPhase[Renderer2DFrame.Phase.UNKNOWN.ordinal()],
			visibleReplayedByPhase[Renderer2DFrame.Phase.SCENE.ordinal()],
			visibleReplayedByPhase[Renderer2DFrame.Phase.WORLD_OVERLAY.ordinal()],
			visibleReplayedByPhase[Renderer2DFrame.Phase.UI_OVERLAY.ordinal()],
			visibleReplayedByPhase[Renderer2DFrame.Phase.UNKNOWN.ordinal()]);
	}

	private void drawOpenGLWorldCompositeOverlay(
		Frame frame,
		Renderer2DFrame renderer2DFrame,
		Renderer2DFrame.SpriteCommand[] commands,
		Renderer2DFrame.TextCommand[] textCommands,
		Renderer2DFrame.PrimitiveCommand[] primitiveCommands,
		Renderer2DFrame.RotatedSpriteCommand[] rotatedSpriteCommands,
		Renderer2DFrame.CircleCommand[] circleCommands) throws Exception {
		// LEGACY BRIDGE: this pass replays legacy 2D commands around the OpenGL
		// world until UI, overlays, and world effects have native renderer-v2 owners.
		legacySceneSpriteRestoreCommands = 0;
		legacySceneSpriteRestoreFallbacks = 0;
		legacySceneSpriteRestoreFallbackPixels = 0;
		resetLegacyEntitySpriteDebugFrame();
		int sceneCommands = 0;
		int worldCommands = 0;
		int uiCommands = 0;
		int unknownCommands = 0;
		for (Renderer2DFrame.SpriteCommand command : commands) {
			switch (command.getPhase()) {
				case SCENE:
					sceneCommands++;
					break;
				case WORLD_OVERLAY:
					worldCommands++;
					break;
				case UI_OVERLAY:
					uiCommands++;
					break;
				default:
					unknownCommands++;
					break;
			}
		}

		if (sceneCommands == 0
			&& worldCommands == 0
			&& unknownCommands == 0
			&& uiCommands == 0
			&& !hasUiTextCommand(textCommands)
			&& !hasUiPrimitiveCommand(primitiveCommands)
			&& !hasUiRotatedSpriteCommand(rotatedSpriteCommands)
			&& !hasUiCircleCommand(circleCommands)) {
			RenderTelemetry.recordSpriteOverlayFrame(
				commands.length,
				0,
				0,
				0,
				0,
				0,
				0,
				sceneCommands,
				worldCommands,
				uiCommands,
				unknownCommands,
				0,
				0,
				0,
				0,
				0,
				0,
				0,
				0);
			return;
		}

		useSourceProjection(renderer2DFrame.getWidth(), renderer2DFrame.getHeight());
		gl.glDisable(gl.GL_DEPTH_TEST);
		gl.glEnable(gl.GL_TEXTURE_2D);
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);

		int staticReplayed = 0;
		int[] directReplayedByPhase = new int[Renderer2DFrame.Phase.values().length];
		int[] visibleReplayedByPhase = new int[Renderer2DFrame.Phase.values().length];
		if (visibleSpriteTextureAtlas != null) {
			visibleSpriteTextureAtlas.beginFrame();
		}
		int visibleReplayed = 0;
		int skippedInvisible = 0;
		int skippedAtlasFull = 0;
		int visiblePixels = 0;
		useSourceProjection(renderer2DFrame.getWidth(), renderer2DFrame.getHeight());
		gl.glDisable(gl.GL_DEPTH_TEST);
		gl.glEnable(gl.GL_TEXTURE_2D);
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		boolean[] directSpriteMask =
			buildOpenGLCompositeDirectOverlayCoverageMask(
				commands,
				textCommands,
				primitiveCommands,
				renderer2DFrame.getWidth(),
				renderer2DFrame.getHeight());
		for (Renderer2DFrame.SpriteCommand command : commands) {
			if (command.getPhase() != Renderer2DFrame.Phase.SCENE
				|| isOpenGLCompositeWorldSpriteCommand(command)
				|| isOpenGLCompositeDirectSpriteCommand(command)) {
				logCompositeSpriteCommand("skip-visible", command, 0);
				continue;
			}
			int commandVisiblePixels = drawVisibleSpriteCommand(frame, command, directSpriteMask);
			logCompositeSpriteCommand(
				"visible-scene",
				command,
				commandVisiblePixels);
			if (commandVisiblePixels > 0) {
				visibleReplayed++;
				visiblePixels += commandVisiblePixels;
				visibleReplayedByPhase[phaseIndex(command.getPhase())]++;
			} else if (commandVisiblePixels == VISIBLE_SPRITE_ATLAS_FULL) {
				skippedAtlasFull++;
			} else {
				skippedInvisible++;
			}
		}
		captureLayer(activeFrameCapture, "04-scene-restore");
		// Scene restoration has already been issued. Reuse its proven atlas from the
		// beginning so overlay uploads cannot fall back when a complex scene fills it.
		if (visibleSpriteTextureAtlas != null) {
			visibleSpriteTextureAtlas.beginFrame();
		}
		List<OpenGLCompositeSceneCommand> compositeSceneCommands =
			buildOpenGLCompositeSceneCommands(frame, commands);
		for (OpenGLCompositeSceneCommand sceneCommand : compositeSceneCommands) {
			WorldSpriteCommand worldSpriteCommand = sceneCommand.worldSpriteCommand;
			Renderer2DFrame.SpriteCommand command = worldSpriteCommand.command;
			logCompositeSpriteCommand("direct-entity", command, command.getWidth() * command.getHeight());
			directReplayedByPhase[phaseIndex(command.getPhase())]++;
		}
		staticReplayed += drawOpenGLCompositeWorldSpriteCommands(frame, compositeSceneCommands);
		captureLayer(activeFrameCapture, "04b-entity-sprites");
		captureLayer(activeFrameCapture, "04c-ordered-static-overlays");

		int spriteIndex = 0;
		int textIndex = 0;
		int primitiveIndex = 0;
		int rotatedSpriteIndex = 0;
		int circleIndex = 0;
		while (true) {
			while (spriteIndex < commands.length
				&& (!isOpenGLCompositeDirectSpriteCommand(commands[spriteIndex])
					|| isOpenGLCompositeWorldSpriteCommand(commands[spriteIndex]))) {
				spriteIndex++;
			}
			while (textIndex < textCommands.length
				&& !isOpenGLWorldOverlayPhase(textCommands[textIndex].getPhase())) {
				textIndex++;
			}
			while (primitiveIndex < primitiveCommands.length
				&& !isOpenGLWorldOverlayPhase(primitiveCommands[primitiveIndex].getPhase())) {
				primitiveIndex++;
			}
			while (rotatedSpriteIndex < rotatedSpriteCommands.length
				&& !isOpenGLWorldOverlayPhase(rotatedSpriteCommands[rotatedSpriteIndex].getPhase())) {
				rotatedSpriteIndex++;
			}
			while (circleIndex < circleCommands.length
				&& !isOpenGLWorldOverlayPhase(circleCommands[circleIndex].getPhase())) {
				circleIndex++;
			}

			boolean hasSprite = spriteIndex < commands.length;
			boolean hasText = textIndex < textCommands.length;
			boolean hasPrimitive = primitiveIndex < primitiveCommands.length;
			boolean hasRotatedSprite = rotatedSpriteIndex < rotatedSpriteCommands.length;
			boolean hasCircle = circleIndex < circleCommands.length;
			if (!hasSprite && !hasText && !hasPrimitive && !hasRotatedSprite && !hasCircle) {
				break;
			}

			int nextSequence = Integer.MAX_VALUE;
			if (hasSprite) {
				nextSequence = Math.min(nextSequence, commands[spriteIndex].getSequence());
			}
			if (hasText) {
				nextSequence = Math.min(nextSequence, textCommands[textIndex].getSequence());
			}
			if (hasPrimitive) {
				nextSequence = Math.min(nextSequence, primitiveCommands[primitiveIndex].getSequence());
			}
			if (hasRotatedSprite) {
				nextSequence = Math.min(nextSequence, rotatedSpriteCommands[rotatedSpriteIndex].getSequence());
			}
			if (hasCircle) {
				nextSequence = Math.min(nextSequence, circleCommands[circleIndex].getSequence());
			}

			if (hasSprite && commands[spriteIndex].getSequence() == nextSequence) {
				Renderer2DFrame.SpriteCommand command = commands[spriteIndex++];
				logCompositeSpriteCommand("direct-overlay", command, command.getWidth() * command.getHeight());
				drawOpenGLCompositeDirectSpriteCommand(command);
				staticReplayed++;
				directReplayedByPhase[phaseIndex(command.getPhase())]++;
			} else if (hasText && textCommands[textIndex].getSequence() == nextSequence) {
				drawTextCommand(textCommands[textIndex++]);
			} else if (hasPrimitive && primitiveCommands[primitiveIndex].getSequence() == nextSequence) {
				drawPrimitiveCommand(primitiveCommands[primitiveIndex++]);
			} else if (hasRotatedSprite && rotatedSpriteCommands[rotatedSpriteIndex].getSequence() == nextSequence) {
				drawRotatedSpriteCommand(rotatedSpriteCommands[rotatedSpriteIndex++]);
			} else {
				drawCircleCommand(circleCommands[circleIndex++]);
			}
		}

		gl.glDisable(gl.GL_BLEND);
		gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
		gl.glEnable(gl.GL_TEXTURE_2D);
		RenderTelemetry.recordLegacySceneSpriteRestore(
			legacySceneSpriteRestoreCommands,
			legacySceneSpriteRestoreFallbacks,
			legacySceneSpriteRestoreFallbackPixels);
		logLegacyEntitySpriteDebugFrame();
		RenderTelemetry.recordSpriteOverlayFrame(
			commands.length,
			staticReplayed,
			visibleReplayed,
			0,
			skippedInvisible,
			skippedAtlasFull,
			visiblePixels,
			sceneCommands,
			worldCommands,
			uiCommands,
			unknownCommands,
			directReplayedByPhase[Renderer2DFrame.Phase.SCENE.ordinal()],
			directReplayedByPhase[Renderer2DFrame.Phase.WORLD_OVERLAY.ordinal()],
			directReplayedByPhase[Renderer2DFrame.Phase.UI_OVERLAY.ordinal()],
			directReplayedByPhase[Renderer2DFrame.Phase.UNKNOWN.ordinal()],
			visibleReplayedByPhase[Renderer2DFrame.Phase.SCENE.ordinal()],
			visibleReplayedByPhase[Renderer2DFrame.Phase.WORLD_OVERLAY.ordinal()],
			visibleReplayedByPhase[Renderer2DFrame.Phase.UI_OVERLAY.ordinal()],
			visibleReplayedByPhase[Renderer2DFrame.Phase.UNKNOWN.ordinal()]);
	}

	private boolean isOpenGLWorldOverlayPhase(Renderer2DFrame.Phase phase) {
		return phase == Renderer2DFrame.Phase.WORLD_OVERLAY
			|| phase == Renderer2DFrame.Phase.UI_OVERLAY;
	}

	private boolean isOpenGLCompositeDirectSpriteCommand(Renderer2DFrame.SpriteCommand command) {
		if (command == null) {
			return false;
		}
		return isOpenGLWorldOverlayPhase(command.getPhase())
			|| (command.getPhase() == Renderer2DFrame.Phase.SCENE
				&& command.getAlpha() < Renderer2DFrame.SpriteCommand.FULL_ALPHA);
	}

	private void logCompositeSpriteCommand(
		String stage,
		Renderer2DFrame.SpriteCommand command,
		int visiblePixels) {
		if (!WORLD_COMPOSITE_DEBUG
			|| command == null
			|| worldCompositeDebugLogs >= WORLD_COMPOSITE_DEBUG_LOG_LIMIT
			|| !intersectsUpperLeftSource(command)) {
			return;
		}
		worldCompositeDebugLogs++;
		log("OpenGL composite sprite "
			+ stage
			+ " phase="
			+ command.getPhase()
			+ " seq="
			+ command.getSequence()
			+ " legacySprite="
			+ command.getLegacySpriteId()
			+ " dst="
			+ command.getX()
			+ ","
			+ command.getY()
			+ " "
			+ command.getWidth()
			+ "x"
			+ command.getHeight()
			+ " src="
			+ command.getSourceX()
			+ ","
			+ command.getSourceY()
			+ " "
			+ command.getSourceWidth()
			+ "x"
			+ command.getSourceHeight()
			+ " alpha="
			+ command.getAlpha()
			+ " visible="
			+ visiblePixels);
	}

	private boolean intersectsUpperLeftSource(Renderer2DFrame.SpriteCommand command) {
		int left = Math.min(command.getTopX16(), command.getBottomX16()) >> 16;
		int right = (Math.max(command.getTopX16(), command.getBottomX16()) >> 16) + command.getWidth();
		int top = command.getY();
		int bottom = command.getY() + command.getHeight();
		int limitX = Math.max(96, viewportPresenter.sourceWidth() / 3);
		int limitY = Math.max(96, viewportPresenter.sourceHeight() / 3);
		return right > 0 && bottom > 0 && left < limitX && top < limitY;
	}

	private boolean hasUiTextCommand(Renderer2DFrame.TextCommand[] commands) {
		for (Renderer2DFrame.TextCommand command : commands) {
			if (command.getPhase() == Renderer2DFrame.Phase.UI_OVERLAY) {
				return true;
			}
		}
		return false;
	}

	private boolean hasUiPrimitiveCommand(Renderer2DFrame.PrimitiveCommand[] commands) {
		for (Renderer2DFrame.PrimitiveCommand command : commands) {
			if (command.getPhase() == Renderer2DFrame.Phase.UI_OVERLAY) {
				return true;
			}
		}
		return false;
	}

	private boolean hasUiRotatedSpriteCommand(Renderer2DFrame.RotatedSpriteCommand[] commands) {
		for (Renderer2DFrame.RotatedSpriteCommand command : commands) {
			if (command.getPhase() == Renderer2DFrame.Phase.UI_OVERLAY) {
				return true;
			}
		}
		return false;
	}

	private boolean hasUiCircleCommand(Renderer2DFrame.CircleCommand[] commands) {
		for (Renderer2DFrame.CircleCommand command : commands) {
			if (command.getPhase() == Renderer2DFrame.Phase.UI_OVERLAY) {
				return true;
			}
		}
		return false;
	}

	private int phaseIndex(Renderer2DFrame.Phase phase) {
		return phase == null ? Renderer2DFrame.Phase.UNKNOWN.ordinal() : phase.ordinal();
	}

	private boolean shouldReplayNativeBaseCommands(Renderer2DFrame renderer2DFrame) {
		return Renderer2DSettings.canPresentUiBaseFrame()
			&& renderer2DFrame != null
			&& renderer2DFrame.getCaptureStats().isNativeUiBaseEligible();
	}

	private void drawPrimitiveCommand(Renderer2DFrame.PrimitiveCommand command) throws Exception {
		prepareOverlaySolidReplayState();
		float red = ((command.getColor() >> 16) & 0xFF) / 255.0f;
		float green = ((command.getColor() >> 8) & 0xFF) / 255.0f;
		float blue = (command.getColor() & 0xFF) / 255.0f;
		float alpha = command.getAlpha() / 256.0f;
		float x0 = command.getX();
		float y0 = command.getY();
		float x1 = command.getX() + command.getWidth();
		float y1 = command.getY() + command.getHeight();

		gl.glDisable(gl.GL_TEXTURE_2D);
		try {
			gl.glColor4f(red, green, blue, alpha);
			drawScreenSolidQuad(x0, y0, x1, y1);
		} finally {
			gl.glEnable(gl.GL_TEXTURE_2D);
		}
	}

	private void drawCircleCommand(Renderer2DFrame.CircleCommand command) throws Exception {
		prepareOverlaySolidReplayState();
		float red = ((command.getColor() >> 16) & 0xFF) / 255.0f;
		float green = ((command.getColor() >> 8) & 0xFF) / 255.0f;
		float blue = (command.getColor() & 0xFF) / 255.0f;
		float alpha = command.getAlpha() / 256.0f;
		int radius = command.getRadius();
		int radiusSquared = radius * radius;

		gl.glDisable(gl.GL_TEXTURE_2D);
		try {
			gl.glColor4f(red, green, blue, alpha);
			for (int py = command.getY() - radius; py <= command.getY() + radius; py++) {
				int dy = py - command.getY();
				int horizHalf = (int) Math.sqrt((double) (radiusSquared - dy * dy));
				float x0 = command.getX() - horizHalf;
				float x1 = command.getX() + horizHalf + 1.0f;
				float y0 = py;
				float y1 = py + 1.0f;
				drawScreenSolidQuad(x0, y0, x1, y1);
			}
		} finally {
			gl.glEnable(gl.GL_TEXTURE_2D);
		}
	}

	private void drawRotatedSpriteCommand(Renderer2DFrame.RotatedSpriteCommand command) throws Exception {
		prepareOverlayTexturedReplayState();
		OpenGLTextureRegion region = uploadSpriteTexture(command.getSprite(), command.isTransparentMask());

		enableSourceClip(
			command.getClipLeft(),
			command.getClipTop(),
			command.getClipRight(),
			command.getClipBottom());
		try {
			gl.glBindTexture(gl.GL_TEXTURE_2D, region.getTextureId());
			applyPixelTextureFilter();
			gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
			drawScreenQuad(
				command.getX0(),
				command.getY0(),
				region.getU0(),
				region.getV0(),
				command.getX1(),
				command.getY1(),
				region.getU1(),
				region.getV0(),
				command.getX2(),
				command.getY2(),
				region.getU1(),
				region.getV1(),
				command.getX3(),
				command.getY3(),
				region.getU0(),
				region.getV1());
		} finally {
			disableSourceClip();
		}
	}

	private void enableSourceClip(int clipLeft, int clipTop, int clipRight, int clipBottom) throws Exception {
		OpenGLViewportPresenter.Viewport viewport = viewportPresenter.framebufferViewport();
		int sourceWidth = viewportPresenter.sourceWidth();
		int sourceHeight = viewportPresenter.sourceHeight();
		int left = Math.max(0, Math.min(sourceWidth, clipLeft));
		int top = Math.max(0, Math.min(sourceHeight, clipTop));
		int right = Math.max(left, Math.min(sourceWidth, clipRight));
		int bottom = Math.max(top, Math.min(sourceHeight, clipBottom));
		double scaleX = viewport.width / (double) sourceWidth;
		double scaleY = viewport.height / (double) sourceHeight;
		int x0 = viewport.x + (int) Math.floor(left * scaleX);
		int x1 = viewport.x + (int) Math.ceil(right * scaleX);
		int y0 = viewport.y + (int) Math.floor((sourceHeight - bottom) * scaleY);
		int y1 = viewport.y + (int) Math.ceil((sourceHeight - top) * scaleY);
		x0 = clamp(x0, viewport.x, viewport.x + viewport.width);
		x1 = clamp(x1, x0, viewport.x + viewport.width);
		y0 = clamp(y0, viewport.y, viewport.y + viewport.height);
		y1 = clamp(y1, y0, viewport.y + viewport.height);

		gl.glEnable(gl.GL_SCISSOR_TEST);
		gl.glScissor(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
	}

	private void disableSourceClip() throws Exception {
		gl.glDisable(gl.GL_SCISSOR_TEST);
	}

	private void drawSpriteCommand(Renderer2DFrame.SpriteCommand command) throws Exception {
		prepareOverlayTexturedReplayState();
		OpenGLTextureRegion region = uploadSpriteTexture(command.getSprite(), command.getTransform());
		float alpha = command.getAlpha() / (float) Renderer2DFrame.SpriteCommand.FULL_ALPHA;
		float topX0 = command.getTopX16() / 65536.0f;
		float bottomX0 = command.getBottomX16() / 65536.0f;
		float y0 = command.getY();
		float y1 = command.getY() + command.getHeight();
		float topX1 = topX0 + command.getWidth();
		float bottomX1 = bottomX0 + command.getWidth();
		float uSpan = region.getU1() - region.getU0();
		float vSpan = region.getV1() - region.getV0();
		float u0 = region.getU0() + uSpan * command.getSourceX() / region.getWidth();
		float v0 = region.getV0() + vSpan * command.getSourceY() / region.getHeight();
		float u1 = region.getU0() + uSpan * (command.getSourceX() + command.getSourceWidth()) / region.getWidth();
		float v1 = region.getV0() + vSpan * (command.getSourceY() + command.getSourceHeight()) / region.getHeight();
		float leftU = command.isMirrorX() ? u1 : u0;
		float rightU = command.isMirrorX() ? u0 : u1;

		gl.glBindTexture(gl.GL_TEXTURE_2D, region.getTextureId());
		applyPixelTextureFilter();
		gl.glColor4f(1.0f, 1.0f, 1.0f, alpha);
		drawScreenQuad(
			topX0,
			y0,
			leftU,
			v0,
			topX1,
			y0,
			rightU,
			v0,
			bottomX1,
			y1,
			rightU,
			v1,
			bottomX0,
			y1,
			leftU,
			v1);
	}

	private void drawTextCommand(Renderer2DFrame.TextCommand command) throws Exception {
		if (glyphTextureCache == null) {
			return;
		}

		prepareOverlayTexturedReplayState();
		for (Renderer2DFrame.TextCommand.GlyphCommand glyph : command.getGlyphs()) {
			OpenGLTextureRegion region = glyphTextureCache.getOrUpload(glyph);
			float red = ((glyph.getColor() >> 16) & 0xFF) / 255.0f;
			float green = ((glyph.getColor() >> 8) & 0xFF) / 255.0f;
			float blue = (glyph.getColor() & 0xFF) / 255.0f;
			float x0 = glyph.getX();
			float y0 = glyph.getY();
			float x1 = glyph.getX() + glyph.getWidth();
			float y1 = glyph.getY() + glyph.getHeight();

			gl.glBindTexture(gl.GL_TEXTURE_2D, region.getTextureId());
			applyTextTextureFilter();
			gl.glColor4f(red, green, blue, 1.0f);
			drawScreenTexturedQuad(
				x0,
				y0,
				x1,
				y1,
				region.getU0(),
				region.getV0(),
				region.getU1(),
				region.getV1());
		}
	}

	private void drawRendererDebugOverlay(Frame frame) throws Exception {
		if (frame.rendererDebugOverlayLines == null
			|| frame.rendererDebugOverlayLines.length == 0
			|| debugOverlayTextureAtlas == null) {
			return;
		}

		long now = RenderTelemetry.now();
		if (debugOverlayTextureRegion == null || now >= debugOverlayNextTextureUpdateNanos) {
			DynamicTextureData textureData = buildRendererDebugOverlayTexture(frame.rendererDebugOverlayLines);
			if (textureData == null || textureData.visiblePixelCount <= 0) {
				return;
			}
			debugOverlayTextureAtlas.beginFrame();
			OpenGLTextureRegion uploadedRegion = debugOverlayTextureAtlas.upload(textureData);
			if (uploadedRegion != null) {
				debugOverlayTextureRegion = uploadedRegion;
				debugOverlayTextureWidth = textureData.width;
				debugOverlayTextureHeight = textureData.height;
				debugOverlayNextTextureUpdateNanos = now + DEBUG_OVERLAY_TEXTURE_UPDATE_NANOS;
			}
		}

		OpenGLTextureRegion region = debugOverlayTextureRegion;
		if (region == null) {
			return;
		}

		useSourceProjection(frame.sourceWidth, frame.sourceHeight);
		prepareOverlayTexturedReplayState();
		gl.glBindTexture(gl.GL_TEXTURE_2D, region.getTextureId());
		applyPixelTextureFilter();
		gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
		float x0 = 6.0f;
		float y0 = 6.0f;
		float x1 = x0 + debugOverlayTextureWidth;
		float y1 = y0 + debugOverlayTextureHeight;
		drawScreenTexturedQuad(
			x0,
			y0,
			x1,
			y1,
			region.getU0(),
			region.getV0(),
			region.getU1(),
			region.getV1());
	}

	private static DynamicTextureData buildRendererDebugOverlayTexture(String[] lines) {
		BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D scratchGraphics = scratch.createGraphics();
		Font font = new Font("Monospaced", Font.PLAIN, 12);
		int width;
		int height;
		int padding = 6;
		int lineHeight;
		try {
			scratchGraphics.setFont(font);
			FontMetrics metrics = scratchGraphics.getFontMetrics();
			int maxWidth = 0;
			for (String line : lines) {
				if (line != null && !line.isEmpty()) {
					maxWidth = Math.max(maxWidth, metrics.stringWidth(line));
				}
			}
			lineHeight = metrics.getHeight();
			width = Math.max(1, Math.min(DEBUG_OVERLAY_MAX_TEXTURE_WIDTH, maxWidth + padding * 2 + 1));
			height = Math.max(1, Math.min(DEBUG_OVERLAY_MAX_TEXTURE_HEIGHT, lines.length * lineHeight + padding * 2 + 1));
		} finally {
			scratchGraphics.dispose();
		}

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setFont(font);
			FontMetrics metrics = graphics.getFontMetrics();
			graphics.setClip(0, 0, width, height);
			graphics.setComposite(AlphaComposite.SrcOver.derive(0.76f));
			graphics.setColor(Color.black);
			graphics.fillRect(0, 0, width, height);
			graphics.setComposite(AlphaComposite.SrcOver);
			graphics.setColor(new Color(70, 170, 255));
			graphics.drawRect(0, 0, width - 1, height - 1);
			int y = padding + metrics.getAscent();
			for (int i = 0; i < lines.length; i++) {
				graphics.setColor(i == 0 ? new Color(190, 230, 255) : Color.white);
				graphics.drawString(lines[i] == null ? "" : lines[i], padding, y);
				y += lineHeight;
			}
		} finally {
			graphics.dispose();
		}

		ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
		int visiblePixelCount = 0;
		int[] rowPixels = new int[width];
		for (int y = 0; y < height; y++) {
			image.getRGB(0, y, width, 1, rowPixels, 0, width);
			for (int x = 0; x < width; x++) {
				int pixel = rowPixels[x];
				int alpha = (pixel >>> 24) & 0xFF;
				if (alpha != 0) {
					visiblePixelCount++;
				}
				pixels.put((byte) ((pixel >> 16) & 0xFF));
				pixels.put((byte) ((pixel >> 8) & 0xFF));
				pixels.put((byte) (pixel & 0xFF));
				pixels.put((byte) alpha);
			}
		}
		pixels.flip();
		return new DynamicTextureData(width, height, pixels, visiblePixelCount);
	}

	private void prepareOverlayTexturedReplayState() throws Exception {
		gl.glDisable(gl.GL_DEPTH_TEST);
		gl.glDisable(gl.GL_ALPHA_TEST);
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		gl.glEnable(gl.GL_TEXTURE_2D);
	}

	private void prepareOverlaySolidReplayState() throws Exception {
		gl.glDisable(gl.GL_DEPTH_TEST);
		gl.glDisable(gl.GL_ALPHA_TEST);
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		gl.glDisable(gl.GL_TEXTURE_2D);
	}

	private int drawVisibleSpriteCommand(Frame frame, Renderer2DFrame.SpriteCommand command) throws Exception {
		return drawVisibleSpriteCommand(frame, command, null);
	}

	private int drawVisibleSpriteCommand(
		Frame frame,
		Renderer2DFrame.SpriteCommand command,
		boolean[] clippedSceneRestoreMask) throws Exception {
		// LEGACY BRIDGE: visible sprite replay reconstructs remaining legacy scene
		// or overlay commands. Entity/item world sprites should keep moving toward
		// explicit world-space submissions and depth ownership.
		if (visibleSpriteTextureAtlas == null) {
			return 0;
		}

		DynamicTextureData textureData = buildVisibleSpriteTexture(frame, command, clippedSceneRestoreMask);
		boolean legacySceneSpriteCommand = isLegacySceneSpriteCommand(command);
		boolean legacyEntitySpriteCommand = isLegacyEntitySpriteCommand(command);
		int directPixelCount = 0;
		boolean usedDirectFallback = false;
		if (legacySceneSpriteCommand) {
			legacySceneSpriteRestoreCommands++;
		}
		if (textureData == null
			&& clippedSceneRestoreMask != null
			&& legacySceneSpriteCommand) {
			textureData = buildVisibleSpriteTexture(frame, command, null);
		}
		if (legacySceneSpriteCommand && !legacyEntitySpriteCommand) {
			DynamicTextureData directTextureData = null;
			if (textureData == null) {
				directTextureData = buildDirectSpriteTexture(command);
				directPixelCount = directTextureData == null ? 0 : directTextureData.visiblePixelCount;
				textureData = directTextureData;
				if (directTextureData != null) {
					legacySceneSpriteRestoreFallbacks++;
					legacySceneSpriteRestoreFallbackPixels += directTextureData.visiblePixelCount;
					usedDirectFallback = true;
				}
			} else {
				directTextureData = buildDirectSpriteTexture(command);
				directPixelCount = directTextureData == null ? 0 : directTextureData.visiblePixelCount;
				if (directTextureData != null
					&& textureData.visiblePixelCount * 10 < directTextureData.visiblePixelCount) {
					textureData = directTextureData;
					legacySceneSpriteRestoreFallbacks++;
					legacySceneSpriteRestoreFallbackPixels += directTextureData.visiblePixelCount;
					usedDirectFallback = true;
				}
			}
		}
		if (legacyEntitySpriteCommand && textureData == null) {
			// LEGACY BRIDGE: last-resort depth reconstruction for old entity scene
			// commands. Do not turn a zero-visible entity into a full direct sprite.
			Renderer3DFrame.SpriteAnchor anchor = findSpriteAnchor(frame, command);
			DynamicTextureData depthTextureData =
				buildDepthVisibleEntitySpriteTexture(frame, command, anchor, null, null);
			if (depthTextureData != null) {
				textureData = depthTextureData;
				legacySceneSpriteRestoreFallbacks++;
				legacySceneSpriteRestoreFallbackPixels += depthTextureData.visiblePixelCount;
				usedDirectFallback = true;
				directPixelCount = depthTextureData.visiblePixelCount;
			}
		}
		if (textureData == null) {
			if (legacyEntitySpriteCommand) {
				if (directPixelCount == 0) {
					DynamicTextureData directTextureData = buildDirectSpriteTexture(command);
					directPixelCount = directTextureData == null ? 0 : directTextureData.visiblePixelCount;
				}
				recordLegacyEntitySpriteDebug(command, 0, directPixelCount, false, false, true);
			}
			return 0;
		}

		OpenGLTextureRegion region = visibleSpriteTextureAtlas.upload(textureData);
		if (region == null) {
			if (legacyEntitySpriteCommand) {
				recordLegacyEntitySpriteDebug(
					command,
					textureData.visiblePixelCount,
					directPixelCount,
					usedDirectFallback,
					true,
					false);
			}
			return VISIBLE_SPRITE_ATLAS_FULL;
		}
		drawSpriteRegion(command, region, region.getU0(), region.getU1(), region.getV0(), region.getV1());
		if (legacyEntitySpriteCommand) {
			recordLegacyEntitySpriteDebug(
				command,
				textureData.visiblePixelCount,
				directPixelCount,
				usedDirectFallback,
				false,
				false);
		}
		return textureData.visiblePixelCount;
	}

	private boolean isLegacySceneSpriteCommand(Renderer2DFrame.SpriteCommand command) {
		return OpenGLCompositeSceneBuilder.isLegacySceneSpriteCommand(command);
	}

	private boolean isLegacyEntitySpriteCommand(Renderer2DFrame.SpriteCommand command) {
		return OpenGLCompositeSceneBuilder.isLegacyEntitySpriteCommand(command);
	}

	private boolean isLegacyGroundItemSpriteCommand(Renderer2DFrame.SpriteCommand command) {
		return OpenGLCompositeSceneBuilder.isLegacyGroundItemSpriteCommand(command);
	}

	private boolean isOpenGLCompositeWorldSpriteCommand(Renderer2DFrame.SpriteCommand command) {
		return OpenGLCompositeSceneBuilder.isOpenGLCompositeWorldSpriteCommand(command);
	}

	private void resetLegacyEntitySpriteDebugFrame() {
		legacyEntitySpriteDebugCommands = 0;
		legacyEntitySpriteDebugVisiblePixels = 0;
		legacyEntitySpriteDebugDirectPixels = 0;
		legacyEntitySpriteDebugFallbacks = 0;
		legacyEntitySpriteDebugSkipped = 0;
		legacyEntitySpriteDebugAtlasFull = 0;
		legacyEntitySpriteDebugFirstId = -1;
		legacyEntitySpriteDebugLastId = -1;
		legacyEntitySpriteDebugById.clear();
		legacyEntitySpriteDepthEvaluations.clear();
	}

	private void recordLegacyEntitySpriteDebug(
		Renderer2DFrame.SpriteCommand command,
		int visiblePixels,
		int directPixels,
		boolean fallback,
		boolean atlasFull,
		boolean skipped) {
		legacyEntitySpriteDebugCommands++;
		legacyEntitySpriteDebugVisiblePixels += Math.max(0, visiblePixels);
		legacyEntitySpriteDebugDirectPixels += Math.max(0, directPixels);
		if (fallback) {
			legacyEntitySpriteDebugFallbacks++;
		}
		if (atlasFull) {
			legacyEntitySpriteDebugAtlasFull++;
		}
		if (skipped) {
			legacyEntitySpriteDebugSkipped++;
		}
		int legacySpriteId = command.getLegacySpriteId();
		if (legacyEntitySpriteDebugFirstId < 0) {
			legacyEntitySpriteDebugFirstId = legacySpriteId;
		}
		legacyEntitySpriteDebugLastId = legacySpriteId;
		LegacyEntitySpriteDebugStats stats = legacyEntitySpriteDebugById.get(legacySpriteId);
		if (stats == null) {
			stats = new LegacyEntitySpriteDebugStats(legacySpriteId);
			legacyEntitySpriteDebugById.put(legacySpriteId, stats);
		}
		stats.record(command, visiblePixels, directPixels, fallback, atlasFull, skipped);
	}

	private void recordLegacyEntitySpriteDepthFallbackMiss(
		Renderer2DFrame.SpriteCommand command,
		int sourcePixels,
		int occludedPixels,
		int clippedPixels,
		int outOfBoundsPixels,
		Renderer3DFrame.SpriteAnchor anchor) {
		int legacySpriteId = command.getLegacySpriteId();
		LegacyEntitySpriteDebugStats stats = legacyEntitySpriteDebugById.get(legacySpriteId);
		if (stats == null) {
			stats = new LegacyEntitySpriteDebugStats(legacySpriteId);
			legacyEntitySpriteDebugById.put(legacySpriteId, stats);
		}
		stats.recordDepthFallbackMiss(sourcePixels, occludedPixels, clippedPixels, outOfBoundsPixels, anchor);
	}

	private void recordLegacyEntitySpriteDepthEvaluation(
		Renderer2DFrame.SpriteCommand command,
		int sourcePixels,
		int visiblePixels,
		int occludedPixels,
		int clippedPixels,
		int outOfBoundsPixels,
		int terrainOccludedPixels,
		int wallOccludedPixels,
		int roofOccludedPixels,
		int gameObjectOccludedPixels,
		int wallObjectOccludedPixels,
		int minOccluderLegacyDrawOrder,
		int maxOccluderLegacyDrawOrder,
		int minOccluderDepth,
		int maxOccluderDepth,
		EntitySpriteOccluderFaceStats dominantOccluderFace,
		WorldSpriteAnchorMatch anchorMatch,
		Renderer3DFrame.SpriteAnchor anchor) {
		int legacySpriteId = command.getLegacySpriteId();
		LegacyEntitySpriteDebugStats stats = legacyEntitySpriteDebugById.get(legacySpriteId);
		if (stats == null) {
			stats = new LegacyEntitySpriteDebugStats(legacySpriteId);
			legacyEntitySpriteDebugById.put(legacySpriteId, stats);
		}
		stats.recordDepthEvaluation(
			sourcePixels,
			visiblePixels,
			occludedPixels,
			clippedPixels,
			outOfBoundsPixels,
			anchor);
		legacyEntitySpriteDepthEvaluations.add(new LegacyEntitySpriteDepthEvaluation(
			legacyEntitySpriteDepthEvaluations.size(),
			command,
			sourcePixels,
			visiblePixels,
			occludedPixels,
			clippedPixels,
			outOfBoundsPixels,
			terrainOccludedPixels,
			wallOccludedPixels,
			roofOccludedPixels,
			gameObjectOccludedPixels,
			wallObjectOccludedPixels,
			minOccluderLegacyDrawOrder,
			maxOccluderLegacyDrawOrder,
			minOccluderDepth,
			maxOccluderDepth,
			dominantOccluderFace,
			anchorMatch,
			anchor));
	}

	private static EntitySpriteOccluderFaceStats dominantOccluderFace(
		Map<Integer, EntitySpriteOccluderFaceStats> occluderFaceStatsById) {
		EntitySpriteOccluderFaceStats dominant = null;
		for (EntitySpriteOccluderFaceStats stats : occluderFaceStatsById.values()) {
			if (dominant == null
				|| stats.pixels > dominant.pixels
				|| (stats.pixels == dominant.pixels && stats.faceId < dominant.faceId)) {
				dominant = stats;
			}
		}
		return dominant;
	}

	private void logLegacyEntitySpriteDebugFrame() {
		if (legacyEntitySpriteDebugCommands <= 0) {
			return;
		}
		legacyEntitySpriteDebugFrames++;
		if (legacyEntitySpriteDebugFrames % 30 != 0) {
			return;
		}
		log("OpenGL entity sprite restore frame commands="
			+ legacyEntitySpriteDebugCommands
			+ " visiblePixels="
			+ legacyEntitySpriteDebugVisiblePixels
			+ " directPixels="
			+ legacyEntitySpriteDebugDirectPixels
			+ " fallbacks="
			+ legacyEntitySpriteDebugFallbacks
			+ " skipped="
			+ legacyEntitySpriteDebugSkipped
			+ " atlasFull="
			+ legacyEntitySpriteDebugAtlasFull
			+ " firstId="
			+ legacyEntitySpriteDebugFirstId
			+ " lastId="
			+ legacyEntitySpriteDebugLastId);
		log("OpenGL entity sprite restore ids " + describeLegacyEntitySpriteDebugIds());
	}

	private String describeLegacyEntitySpriteDebugIds() {
		if (legacyEntitySpriteDebugById.isEmpty()) {
			return "none";
		}
		List<LegacyEntitySpriteDebugStats> stats =
			new ArrayList<LegacyEntitySpriteDebugStats>(legacyEntitySpriteDebugById.values());
		Collections.sort(stats, new Comparator<LegacyEntitySpriteDebugStats>() {
			@Override
			public int compare(LegacyEntitySpriteDebugStats a, LegacyEntitySpriteDebugStats b) {
				if (a.skipped != b.skipped) {
					return b.skipped - a.skipped;
				}
				if (a.visiblePixels != b.visiblePixels) {
					return a.visiblePixels - b.visiblePixels;
				}
				return a.legacySpriteId - b.legacySpriteId;
			}
		});
		StringBuilder builder = new StringBuilder();
		int count = Math.min(8, stats.size());
		for (int i = 0; i < count; i++) {
			if (i > 0) {
				builder.append(" | ");
			}
			builder.append(stats.get(i).describe());
		}
		return builder.toString();
	}

	private void drawOpenGLCompositeEntitySpriteCommand(
		Frame frame,
		Renderer2DFrame.SpriteCommand command) throws Exception {
		if (worldSpriteDrawController != null) {
			worldSpriteDrawController.drawEntitySpriteCommand(
				frame,
				buildOpenGLCompositeWorldSpriteCommand(frame, command));
		}
	}

	private int drawOpenGLCompositeWorldSpriteCommands(
		Frame frame,
		List<OpenGLCompositeSceneCommand> sceneCommands) throws Exception {
		int drawn = worldSpriteDrawController == null
			? 0
			: worldSpriteDrawController.drawSceneCommands(frame, sceneCommands);
		worldSpriteDepthDrawCommands = drawn;
		worldSpriteDepthTextureBatches = drawn;
		return drawn;
	}

	private void initializeUnitQuadBuffers() throws Exception {
		unitQuadVertexBufferId = gl.glGenBuffers();
		unitQuadIndexBufferId = gl.glGenBuffers();
		FloatBuffer vertices = ByteBuffer
			.allocateDirect(UNIT_QUAD_VERTEX_COUNT * UNIT_QUAD_FLOATS_PER_VERTEX * 4)
			.order(ByteOrder.nativeOrder())
			.asFloatBuffer();
		putUnitQuadVertex(vertices, 0.0f, 0.0f, 0.0f, 0.0f);
		putUnitQuadVertex(vertices, 1.0f, 0.0f, 1.0f, 0.0f);
		putUnitQuadVertex(vertices, 1.0f, 1.0f, 1.0f, 1.0f);
		putUnitQuadVertex(vertices, 0.0f, 1.0f, 0.0f, 1.0f);
		vertices.flip();
		IntBuffer indices = ByteBuffer
			.allocateDirect(UNIT_QUAD_INDEX_COUNT * 4)
			.order(ByteOrder.nativeOrder())
			.asIntBuffer();
		putQuadIndices(indices);
		gl.glBindBuffer(gl.GL_ARRAY_BUFFER, unitQuadVertexBufferId);
		gl.glBufferData(gl.GL_ARRAY_BUFFER, vertices, gl.GL_STATIC_DRAW);
		gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0);
		gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, unitQuadIndexBufferId);
		gl.glBufferData(gl.GL_ELEMENT_ARRAY_BUFFER, indices, gl.GL_STATIC_DRAW);
		gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, 0);
	}

	private static void putUnitQuadVertex(FloatBuffer vertices, float x, float y, float u, float v) {
		vertices.put(x);
		vertices.put(y);
		vertices.put(u);
		vertices.put(v);
	}

	private static void putQuadIndices(IntBuffer indices) {
		indices.put(0);
		indices.put(1);
		indices.put(2);
		indices.put(0);
		indices.put(2);
		indices.put(3);
		indices.flip();
	}

	private void deleteUnitQuadBuffers() throws Exception {
		if (unitQuadVertexBufferId != 0) {
			gl.glDeleteBuffers(unitQuadVertexBufferId);
			unitQuadVertexBufferId = 0;
		}
		if (unitQuadIndexBufferId != 0) {
			gl.glDeleteBuffers(unitQuadIndexBufferId);
			unitQuadIndexBufferId = 0;
		}
	}

	private void initializeScreenQuadBuffers() throws Exception {
		screenQuadVertexBufferId = gl.glGenBuffers();
		screenQuadUploadBuffer = ByteBuffer
			.allocateDirect(UNIT_QUAD_VERTEX_COUNT * UNIT_QUAD_FLOATS_PER_VERTEX * 4)
			.order(ByteOrder.nativeOrder())
			.asFloatBuffer();
	}

	private void deleteScreenQuadBuffers() throws Exception {
		if (screenQuadVertexBufferId != 0) {
			gl.glDeleteBuffers(screenQuadVertexBufferId);
			screenQuadVertexBufferId = 0;
		}
		screenQuadUploadBuffer = null;
	}

	private void drawUnitTexturedQuad() throws Exception {
		if (unitQuadVertexBufferId == 0 || unitQuadIndexBufferId == 0) {
			return;
		}
		gl.glBindBuffer(gl.GL_ARRAY_BUFFER, unitQuadVertexBufferId);
		gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, unitQuadIndexBufferId);
		gl.glEnableClientState(gl.GL_VERTEX_ARRAY);
		gl.glVertexPointer(
			UNIT_QUAD_POSITION_COMPONENTS,
			gl.GL_FLOAT,
			UNIT_QUAD_STRIDE_BYTES,
			0L);
		gl.glEnableClientState(gl.GL_TEXTURE_COORD_ARRAY);
		gl.glTexCoordPointer(
			UNIT_QUAD_TEXCOORD_COMPONENTS,
			gl.GL_FLOAT,
			UNIT_QUAD_STRIDE_BYTES,
			UNIT_QUAD_TEXCOORD_OFFSET_BYTES);
		try {
			gl.glDrawElements(
				gl.GL_TRIANGLES,
				UNIT_QUAD_INDEX_COUNT,
				gl.GL_UNSIGNED_INT,
				0L);
		} finally {
			gl.glDisableClientState(gl.GL_TEXTURE_COORD_ARRAY);
			gl.glDisableClientState(gl.GL_VERTEX_ARRAY);
			gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, 0);
			gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0);
		}
	}

	private void drawScreenSolidQuad(float x0, float y0, float x1, float y1) throws Exception {
		drawScreenQuad(
			x0,
			y0,
			0.0f,
			0.0f,
			x1,
			y0,
			0.0f,
			0.0f,
			x1,
			y1,
			0.0f,
			0.0f,
			x0,
			y1,
			0.0f,
			0.0f);
	}

	private void drawScreenTexturedQuad(
		float x0,
		float y0,
		float x1,
		float y1,
		float u0,
		float v0,
		float u1,
		float v1) throws Exception {
		drawScreenQuad(
			x0,
			y0,
			u0,
			v0,
			x1,
			y0,
			u1,
			v0,
			x1,
			y1,
			u1,
			v1,
			x0,
			y1,
			u0,
			v1);
	}

	private void drawScreenQuad(
		float x0,
		float y0,
		float u0,
		float v0,
		float x1,
		float y1,
		float u1,
		float v1,
		float x2,
		float y2,
		float u2,
		float v2,
		float x3,
		float y3,
		float u3,
		float v3) throws Exception {
		if (screenQuadUploadBuffer == null || screenQuadVertexBufferId == 0 || unitQuadIndexBufferId == 0) {
			return;
		}
		screenQuadUploadBuffer.clear();
		putUnitQuadVertex(screenQuadUploadBuffer, x0, y0, u0, v0);
		putUnitQuadVertex(screenQuadUploadBuffer, x1, y1, u1, v1);
		putUnitQuadVertex(screenQuadUploadBuffer, x2, y2, u2, v2);
		putUnitQuadVertex(screenQuadUploadBuffer, x3, y3, u3, v3);
		screenQuadUploadBuffer.flip();
		gl.glBindBuffer(gl.GL_ARRAY_BUFFER, screenQuadVertexBufferId);
		gl.glBufferData(gl.GL_ARRAY_BUFFER, screenQuadUploadBuffer, gl.GL_STREAM_DRAW);
		gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, unitQuadIndexBufferId);
		gl.glEnableClientState(gl.GL_VERTEX_ARRAY);
		gl.glVertexPointer(
			UNIT_QUAD_POSITION_COMPONENTS,
			gl.GL_FLOAT,
			UNIT_QUAD_STRIDE_BYTES,
			0L);
		gl.glEnableClientState(gl.GL_TEXTURE_COORD_ARRAY);
		gl.glTexCoordPointer(
			UNIT_QUAD_TEXCOORD_COMPONENTS,
			gl.GL_FLOAT,
			UNIT_QUAD_STRIDE_BYTES,
			UNIT_QUAD_TEXCOORD_OFFSET_BYTES);
		try {
			gl.glDrawElements(
				gl.GL_TRIANGLES,
				UNIT_QUAD_INDEX_COUNT,
				gl.GL_UNSIGNED_INT,
				0L);
		} finally {
			gl.glDisableClientState(gl.GL_TEXTURE_COORD_ARRAY);
			gl.glDisableClientState(gl.GL_VERTEX_ARRAY);
			gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, 0);
			gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0);
		}
	}

	List<WorldSpriteCommand> buildOpenGLCompositeWorldSpriteCommands(
		Frame frame,
		Renderer2DFrame.SpriteCommand[] commands) {
		return OpenGLCompositeSceneBuilder.buildWorldSpriteCommands(frame, commands);
	}

	List<OpenGLCompositeSceneCommand> buildOpenGLCompositeSceneCommands(
		Frame frame,
		Renderer2DFrame.SpriteCommand[] commands) {
		return OpenGLCompositeSceneBuilder.buildSceneCommands(frame, commands);
	}

	private WorldSpriteCommand buildOpenGLCompositeWorldSpriteCommand(
		Frame frame,
		Renderer2DFrame.SpriteCommand command) {
		return OpenGLCompositeSceneBuilder.buildWorldSpriteCommand(frame, command);
	}

	Set<Long> buildOpenGLCompositeFrontOccluderFaceKeys(
		Frame frame,
		List<WorldSpriteCommand> worldSpriteCommands,
		int minExclusiveOrder,
		int maxExclusiveOrder) {
		return OpenGLCompositeSceneBuilder.buildFrontOccluderFaceKeys(
			frame,
			worldSpriteCommands,
			minExclusiveOrder,
			maxExclusiveOrder);
	}

	List<StaticWorldCommand> buildOpenGLCompositeStaticWorldCommands(Frame frame) {
		return OpenGLCompositeSceneBuilder.buildStaticWorldCommands(frame);
	}

	List<StaticWorldMaterialTriangle> buildOpenGLCompositeStaticWorldMaterialTriangles(Frame frame) {
		return OpenGLCompositeSceneBuilder.buildStaticWorldMaterialTriangles(frame);
	}

	static int openGLCompositeModelIndex(long modelFaceKey) {
		return OpenGLCompositeSceneBuilder.openGLCompositeModelIndex(modelFaceKey);
	}

	static int openGLCompositeFaceId(long modelFaceKey) {
		return OpenGLCompositeSceneBuilder.openGLCompositeFaceId(modelFaceKey);
	}

	private void drawOpenGLCompositeDirectSpriteCommand(Renderer2DFrame.SpriteCommand command) throws Exception {
		if (command.getPhase() == Renderer2DFrame.Phase.UI_OVERLAY || visibleSpriteTextureAtlas == null) {
			drawSpriteCommand(command);
			return;
		}

		DynamicTextureData textureData = buildDirectSpriteTexture(command);
		if (textureData == null) {
			drawSpriteCommand(command);
			return;
		}
		if (!drawOpenGLCompositeDynamicSpriteTexture(
			command,
			textureData,
			command.getAlpha() / (float) Renderer2DFrame.SpriteCommand.FULL_ALPHA)) {
			drawSpriteCommand(command);
		}
	}

	private boolean drawOpenGLCompositeDynamicSpriteTexture(
		Renderer2DFrame.SpriteCommand command,
		DynamicTextureData textureData,
		float alpha) throws Exception {
		if (visibleSpriteTextureAtlas == null || textureData == null) {
			return false;
		}
		OpenGLTextureRegion region = visibleSpriteTextureAtlas.upload(textureData);
		if (region == null) {
			visibleSpriteTextureAtlas.beginFrame();
			region = visibleSpriteTextureAtlas.upload(textureData);
			if (region == null) {
				return false;
			}
		}
		drawSpriteRegion(command, region, region.getU0(), region.getU1(), region.getV0(), region.getV1(), alpha);
		return true;
	}

	private Renderer3DFrame.SpriteAnchor findSpriteAnchor(Frame frame, Renderer2DFrame.SpriteCommand command) {
		return OpenGLCompositeSceneBuilder.findSpriteAnchor(frame, command);
	}

	private DynamicTextureData buildDepthVisibleEntitySpriteTexture(
		Frame frame,
		Renderer2DFrame.SpriteCommand command,
		Renderer3DFrame.SpriteAnchor anchor,
		boolean[] clippedSceneRestoreMask,
		WorldSpriteAnchorMatch providedAnchorMatch) {
		if (frame == null || command == null || anchor == null || frame.renderer3DFrame == null) {
			return null;
		}
		Renderer3DDepthFrame depthFrame = frame.renderer3DFrame.getDepthFrame();
		if (depthFrame == null) {
			return null;
		}
		Sprite sprite = command.getSprite();
		int[] sourcePixels = sprite.getPixels();
		int spriteWidth = sprite.getWidth();
		int spriteHeight = sprite.getHeight();
		int width = command.getWidth();
		int height = command.getHeight();
		int alpha = command.getAlpha();
		if (sourcePixels == null
			|| sourcePixels.length < spriteWidth * spriteHeight
			|| width <= 0
			|| height <= 0
			|| alpha <= 0) {
			return null;
		}

		ByteBuffer overlayPixels = ByteBuffer.allocateDirect(width * height * 4);
		long xDelta16 = (long) command.getBottomX16() - command.getTopX16();
		int visiblePixelCount = 0;
		int depthFallbackSourcePixels = 0;
		int depthFallbackOccludedPixels = 0;
		int depthFallbackClippedPixels = 0;
		int depthFallbackOutOfBoundsPixels = 0;
		int terrainOccludedPixels = 0;
		int wallOccludedPixels = 0;
		int roofOccludedPixels = 0;
		int gameObjectOccludedPixels = 0;
		int wallObjectOccludedPixels = 0;
		int minOccluderLegacyDrawOrder = Integer.MAX_VALUE;
		int maxOccluderLegacyDrawOrder = Integer.MIN_VALUE;
		int minOccluderDepth = Integer.MAX_VALUE;
		int maxOccluderDepth = Integer.MIN_VALUE;
		Map<Integer, EntitySpriteOccluderFaceStats> occluderFaceStatsById =
			new HashMap<Integer, EntitySpriteOccluderFaceStats>();
		WorldSpriteAnchorMatch anchorMatch = providedAnchorMatch == null
			? classifyWorldSpriteAnchorMatch(frame, command, anchor)
			: providedAnchorMatch;

		for (int row = 0; row < height; row++) {
			int screenY = command.getY() + row;
			long rowLeft16 = command.getTopX16() + xDelta16 * row / height;
			int screenX0 = (int) (rowLeft16 >> 16);
			int sourceY = (int) ((command.getSourceStartY16() + (long) row * command.getSourceScaleY16()) >> 16);
			for (int column = 0; column < width; column++) {
				int rgba = 0;
				int screenX = screenX0 + column;
				int sourceX = (int) ((command.getSourceStartX16() + (long) column * command.getSourceScaleX16()) >> 16);
				if (sourceX < 0
					|| sourceX >= spriteWidth
					|| sourceY < 0
					|| sourceY >= spriteHeight) {
					depthFallbackOutOfBoundsPixels++;
					overlayPixels.putInt(rgba);
					continue;
				}
				int sourcePixel = sourcePixels[sourceY * spriteWidth + sourceX];
				if (!orsc.graphics.RendererTransparency.isVisibleSpritePixel(sourcePixel)) {
					overlayPixels.putInt(rgba);
					continue;
				}
				depthFallbackSourcePixels++;
				if (screenX < 0
					|| screenX >= frame.sourceWidth
					|| screenY < 0
					|| screenY >= frame.sourceHeight) {
					depthFallbackOutOfBoundsPixels++;
					overlayPixels.putInt(rgba);
					continue;
				}
				if (isSceneRestoreClipped(clippedSceneRestoreMask, frame.sourceWidth, screenX, screenY)) {
					depthFallbackClippedPixels++;
					overlayPixels.putInt(rgba);
					continue;
				}
				int spriteDepth = estimateEntitySpriteDepth(anchor, screenY);
				int depthTolerance = Math.max(32, spriteDepth / 96);
				Renderer3DModelKind occluderKind = depthFrame.getEntityOccluderKindAfterSprite(
					screenX,
					screenY,
					anchor.getLegacyDrawOrder(),
					spriteDepth,
					depthTolerance);
				if (occluderKind != null) {
					depthFallbackOccludedPixels++;
					if (occluderKind == Renderer3DModelKind.TERRAIN) {
						terrainOccludedPixels++;
					} else if (occluderKind == Renderer3DModelKind.WALL) {
						wallOccludedPixels++;
					} else if (occluderKind == Renderer3DModelKind.ROOF) {
						roofOccludedPixels++;
					} else if (occluderKind == Renderer3DModelKind.GAME_OBJECT) {
						gameObjectOccludedPixels++;
					} else if (occluderKind == Renderer3DModelKind.WALL_OBJECT) {
						wallObjectOccludedPixels++;
					}
					int occluderLegacyDrawOrder = depthFrame.getLegacyDrawOrderAt(screenX, screenY);
					if (occluderLegacyDrawOrder >= 0) {
						minOccluderLegacyDrawOrder =
							Math.min(minOccluderLegacyDrawOrder, occluderLegacyDrawOrder);
						maxOccluderLegacyDrawOrder =
							Math.max(maxOccluderLegacyDrawOrder, occluderLegacyDrawOrder);
					}
					int occluderDepth = depthFrame.getDepthAt(screenX, screenY);
					if (occluderDepth != Integer.MAX_VALUE) {
						minOccluderDepth = Math.min(minOccluderDepth, occluderDepth);
						maxOccluderDepth = Math.max(maxOccluderDepth, occluderDepth);
					}
					int occluderFaceId = depthFrame.getFaceIdAt(screenX, screenY);
					if (occluderFaceId >= 0) {
						EntitySpriteOccluderFaceStats occluderStats =
							occluderFaceStatsById.get(occluderFaceId);
						if (occluderStats == null) {
							occluderStats = new EntitySpriteOccluderFaceStats(
								occluderKind,
								occluderFaceId,
								depthFrame.getModelIndexAt(screenX, screenY));
							occluderFaceStatsById.put(occluderFaceId, occluderStats);
						}
						occluderStats.record(occluderLegacyDrawOrder, occluderDepth);
					}
					overlayPixels.putInt(rgba);
					continue;
				}
				int replayRgb = command.getTransform().apply(sourcePixel)
					& orsc.graphics.RendererTransparency.RGB_MASK;
				// Depth masking owns visibility only. Sprite opacity is applied by
				// drawSpriteRegion, keeping it out of the texture alpha channel.
				rgba = (replayRgb << 8) | Renderer2DFrame.SpriteCommand.FULL_ALPHA;
				visiblePixelCount++;
				overlayPixels.putInt(rgba);
			}
		}

		recordLegacyEntitySpriteDepthEvaluation(
			command,
			depthFallbackSourcePixels,
			visiblePixelCount,
			depthFallbackOccludedPixels,
			depthFallbackClippedPixels,
			depthFallbackOutOfBoundsPixels,
			terrainOccludedPixels,
			wallOccludedPixels,
			roofOccludedPixels,
			gameObjectOccludedPixels,
			wallObjectOccludedPixels,
			minOccluderLegacyDrawOrder,
			maxOccluderLegacyDrawOrder,
			minOccluderDepth,
			maxOccluderDepth,
			dominantOccluderFace(occluderFaceStatsById),
			anchorMatch,
			anchor);
		if (visiblePixelCount == 0) {
			recordLegacyEntitySpriteDepthFallbackMiss(
				command,
				depthFallbackSourcePixels,
				depthFallbackOccludedPixels,
				depthFallbackClippedPixels,
				depthFallbackOutOfBoundsPixels,
				anchor);
		}
		overlayPixels.flip();
		return new DynamicTextureData(width, height, overlayPixels, visiblePixelCount);
	}

	private int estimateEntitySpriteDepth(Renderer3DFrame.SpriteAnchor anchor, int screenY) {
		return OpenGLWorldSpriteRenderer.estimateEntitySpriteDepth(anchor, screenY);
	}

	private DynamicTextureData buildDirectSpriteTexture(Renderer2DFrame.SpriteCommand command) {
		return OpenGLSpriteTextureBuilder.buildDirectSpriteTexture(command);
	}

	private DynamicTextureData buildVisibleSpriteTexture(
		Frame frame,
		Renderer2DFrame.SpriteCommand command,
		boolean[] clippedSceneRestoreMask) {
		// LEGACY BRIDGE: samples the software framebuffer to recover only pixels
		// that legacy rendering made visible. Do not use this as a new visibility
		// source when renderer-v2 depth/command data exists.
		Sprite sprite = command.getSprite();
		int[] sourcePixels = sprite.getPixels();
		int spriteWidth = sprite.getWidth();
		int spriteHeight = sprite.getHeight();
		int width = command.getWidth();
		int height = command.getHeight();
		if (sourcePixels == null
			|| sourcePixels.length < spriteWidth * spriteHeight
			|| width <= 0
			|| height <= 0) {
			return null;
		}

		ByteBuffer finalPixels = frame.pixels();
		ByteBuffer visiblePixels = ByteBuffer.allocateDirect(width * height * 4);
		long xDelta16 = (long) command.getBottomX16() - command.getTopX16();
		int visiblePixelCount = 0;

		for (int row = 0; row < height; row++) {
			int screenY = command.getY() + row;
			long rowLeft16 = command.getTopX16() + xDelta16 * row / height;
			int screenX0 = (int) (rowLeft16 >> 16);
			int sourceY = (int) ((command.getSourceStartY16() + (long) row * command.getSourceScaleY16()) >> 16);

			for (int column = 0; column < width; column++) {
				int rgba = 0;
				int screenX = screenX0 + column;
				int sourceX = (int) ((command.getSourceStartX16() + (long) column * command.getSourceScaleX16()) >> 16);
				if (screenX >= 0
					&& screenX < frame.sourceWidth
					&& screenY >= 0
					&& screenY < frame.sourceHeight
					&& sourceX >= 0
					&& sourceX < spriteWidth
					&& sourceY >= 0
					&& sourceY < spriteHeight) {
					int sourcePixel = sourcePixels[sourceY * spriteWidth + sourceX];
					if (orsc.graphics.RendererTransparency.isVisibleSpritePixel(sourcePixel)
						&& !isSceneRestoreClipped(clippedSceneRestoreMask, frame.sourceWidth, screenX, screenY)) {
						int replayRgb = command.getTransform().apply(sourcePixel) & orsc.graphics.RendererTransparency.RGB_MASK;
						int finalRgb = (finalPixels.getInt((screenY * frame.sourceWidth + screenX) * 4) >>> 8)
							& orsc.graphics.RendererTransparency.RGB_MASK;
						if (matchesRestoredSpriteColor(finalRgb, replayRgb)) {
							rgba = (replayRgb << 8) | 0xFF;
							visiblePixelCount++;
						}
					}
				}
				visiblePixels.putInt(rgba);
			}
		}

		if (visiblePixelCount == 0) {
			return null;
		}
		visiblePixels.flip();
		return new DynamicTextureData(width, height, visiblePixels, visiblePixelCount);
	}

	private boolean isSceneRestoreClipped(boolean[] mask, int sourceWidth, int x, int y) {
		if (mask == null || sourceWidth <= 0 || x < 0 || y < 0) {
			return false;
		}
		int index = y * sourceWidth + x;
		return index >= 0 && index < mask.length && mask[index];
	}

	private boolean[] buildOpenGLCompositeDirectOverlayCoverageMask(
		Renderer2DFrame.SpriteCommand[] commands,
		Renderer2DFrame.TextCommand[] textCommands,
		Renderer2DFrame.PrimitiveCommand[] primitiveCommands,
		int sourceWidth,
		int sourceHeight) {
		if (sourceWidth <= 0 || sourceHeight <= 0) {
			return null;
		}

		boolean[] mask = null;
		if (commands != null) {
			for (Renderer2DFrame.SpriteCommand command : commands) {
				if (!isOpenGLCompositeDirectSpriteCommand(command)) {
					continue;
				}
				Sprite sprite = command.getSprite();
				int[] sourcePixels = sprite.getPixels();
				int spriteWidth = sprite.getWidth();
				int spriteHeight = sprite.getHeight();
				int width = command.getWidth();
				int height = command.getHeight();
				if (sourcePixels == null
					|| sourcePixels.length < spriteWidth * spriteHeight
					|| width <= 0
					|| height <= 0) {
					continue;
				}

				if (mask == null) {
					mask = new boolean[sourceWidth * sourceHeight];
				}
				long xDelta16 = (long) command.getBottomX16() - command.getTopX16();
				for (int row = 0; row < height; row++) {
					int screenY = command.getY() + row;
					if (screenY < 0 || screenY >= sourceHeight) {
						continue;
					}
					long rowLeft16 = command.getTopX16() + xDelta16 * row / height;
					int screenX0 = (int) (rowLeft16 >> 16);
					int sourceY = (int) ((command.getSourceStartY16() + (long) row * command.getSourceScaleY16()) >> 16);
					if (sourceY < 0 || sourceY >= spriteHeight) {
						continue;
					}
					for (int column = 0; column < width; column++) {
						int screenX = screenX0 + column;
						if (screenX < 0 || screenX >= sourceWidth) {
							continue;
						}
						int sourceX = (int) ((command.getSourceStartX16() + (long) column * command.getSourceScaleX16()) >> 16);
						if (sourceX < 0 || sourceX >= spriteWidth) {
							continue;
						}
						int sourcePixel = sourcePixels[sourceY * spriteWidth + sourceX];
						if (orsc.graphics.RendererTransparency.isVisibleSpritePixel(sourcePixel)) {
							mask[screenY * sourceWidth + screenX] = true;
						}
					}
				}
			}
		}
		if (primitiveCommands != null) {
			for (Renderer2DFrame.PrimitiveCommand command : primitiveCommands) {
				if (!isOpenGLWorldOverlayPhase(command.getPhase())) {
					continue;
				}
				mask = markOverlayRectangle(mask, sourceWidth, sourceHeight, command.getX(), command.getY(),
					command.getWidth(), command.getHeight());
			}
		}
		if (textCommands != null) {
			for (Renderer2DFrame.TextCommand command : textCommands) {
				if (!isOpenGLWorldOverlayPhase(command.getPhase())) {
					continue;
				}
				for (Renderer2DFrame.TextCommand.GlyphCommand glyph : command.getGlyphs()) {
					mask = markOverlayRectangle(mask, sourceWidth, sourceHeight, glyph.getX(), glyph.getY(),
						glyph.getWidth(), glyph.getHeight());
				}
			}
		}
		return mask;
	}

	private boolean[] markOverlayRectangle(
		boolean[] mask,
		int sourceWidth,
		int sourceHeight,
		int x,
		int y,
		int width,
		int height) {
		if (width <= 0 || height <= 0) {
			return mask;
		}
		int left = clamp(x, 0, sourceWidth);
		int top = clamp(y, 0, sourceHeight);
		int right = clamp(x + width, left, sourceWidth);
		int bottom = clamp(y + height, top, sourceHeight);
		if (left >= right || top >= bottom) {
			return mask;
		}
		if (mask == null) {
			mask = new boolean[sourceWidth * sourceHeight];
		}
		for (int row = top; row < bottom; row++) {
			int offset = row * sourceWidth;
			for (int column = left; column < right; column++) {
				mask[offset + column] = true;
			}
		}
		return mask;
	}

	private boolean matchesRestoredSpriteColor(int finalRgb, int replayRgb) {
		if (finalRgb == replayRgb) {
			return true;
		}

		int redDelta = Math.abs(((finalRgb >> 16) & 0xFF) - ((replayRgb >> 16) & 0xFF));
		int greenDelta = Math.abs(((finalRgb >> 8) & 0xFF) - ((replayRgb >> 8) & 0xFF));
		int blueDelta = Math.abs((finalRgb & 0xFF) - (replayRgb & 0xFF));
		return redDelta <= VISIBLE_SPRITE_RESTORE_COLOR_TOLERANCE
			&& greenDelta <= VISIBLE_SPRITE_RESTORE_COLOR_TOLERANCE
			&& blueDelta <= VISIBLE_SPRITE_RESTORE_COLOR_TOLERANCE;
	}

	private void drawSpriteRegion(
		Renderer2DFrame.SpriteCommand command,
		OpenGLTextureRegion region,
		float leftU,
		float rightU,
		float v0,
		float v1) throws Exception {
		drawSpriteRegion(
			command,
			region,
			leftU,
			rightU,
			v0,
			v1,
			command.getAlpha() / (float) Renderer2DFrame.SpriteCommand.FULL_ALPHA);
	}

	private void drawSpriteRegion(
		Renderer2DFrame.SpriteCommand command,
		OpenGLTextureRegion region,
		float leftU,
		float rightU,
		float v0,
		float v1,
		float alpha) throws Exception {
		float topX0 = command.getTopX16() / 65536.0f;
		float bottomX0 = command.getBottomX16() / 65536.0f;
		float y0 = command.getY();
		float y1 = command.getY() + command.getHeight();
		float topX1 = topX0 + command.getWidth();
		float bottomX1 = bottomX0 + command.getWidth();

		gl.glBindTexture(gl.GL_TEXTURE_2D, region.getTextureId());
		applyPixelTextureFilter();
		gl.glColor4f(1.0f, 1.0f, 1.0f, alpha);
		drawScreenQuad(
			topX0,
			y0,
			leftU,
			v0,
			topX1,
			y0,
			rightU,
			v0,
			bottomX1,
			y1,
			rightU,
			v1,
			bottomX0,
			y1,
			leftU,
			v1);
	}

	private void useUnitProjection() throws Exception {
		gl.glMatrixMode(gl.GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glOrtho(0.0, 1.0, 1.0, 0.0, -1.0, 1.0);
		gl.glMatrixMode(gl.GL_MODELVIEW);
		gl.glLoadIdentity();
	}

	private void useSourceProjection(int sourceWidth, int sourceHeight) throws Exception {
		gl.glMatrixMode(gl.GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glOrtho(0.0, sourceWidth, sourceHeight, 0.0, -1.0, 1.0);
		gl.glMatrixMode(gl.GL_MODELVIEW);
		gl.glLoadIdentity();
	}

	private void useWorldMeshProjection(Frame frame) throws Exception {
		if (frame == null || frame.renderer3DFrame == null) {
			throw new IllegalArgumentException("camera-space world projection requires a renderer3D frame");
		}
		if (cameraToClipMatrixBuffer == null) {
			cameraToClipMatrixBuffer = ByteBuffer
				.allocateDirect(16 * 4)
				.order(ByteOrder.nativeOrder())
				.asFloatBuffer();
		}
		float[] matrix = cameraToClipProjectionMatrix(frame.renderer3DFrame);
		cameraToClipMatrixBuffer.clear();
		for (int column = 0; column < 4; column++) {
			for (int row = 0; row < 4; row++) {
				cameraToClipMatrixBuffer.put(matrix[row * 4 + column]);
			}
		}
		cameraToClipMatrixBuffer.flip();
		gl.glMatrixMode(gl.GL_PROJECTION);
		gl.glLoadMatrixf(cameraToClipMatrixBuffer);
		gl.glMatrixMode(gl.GL_MODELVIEW);
		gl.glLoadIdentity();
	}

	private static float[] cameraToClipProjectionMatrix(Renderer3DFrame frame) {
		float viewportWidth = Math.max(1.0f, frame.getViewportWidth());
		float viewportHeight = Math.max(1.0f, frame.getViewportHeight());
		float scale = (float) (1 << Math.max(0, Math.min(24, frame.getPerspectiveShift())));
		float near = Math.max(1.0f, frame.getNearPlane());
		float far = Math.max(near + 1.0f, frame.getFogDistance());
		float depthA = (far + near) / (far - near);
		float depthB = -(2.0f * far * near) / (far - near);
		return new float[] {
			2.0f * scale / viewportWidth, 0.0f, 2.0f * frame.getCenterX() / viewportWidth - 1.0f, 0.0f,
			0.0f, -2.0f * scale / viewportHeight, 1.0f - 2.0f * frame.getCenterY() / viewportHeight, 0.0f,
			0.0f, 0.0f, depthA, depthB,
			0.0f, 0.0f, 1.0f, 0.0f
		};
	}

	private void initializeInputBridge() throws Exception {
		inputBridge = new OpenGLInputBridge(
			inputEnabled,
			gl,
			windowController.window(),
			new OpenGLInputBridge.Delegate() {
				@Override
				public int mapMouseX(double cursorX) {
					return OpenGLFramePresenter.this.mapMouseX(cursorX);
				}

				@Override
				public int mapMouseY(double cursorY) {
					return OpenGLFramePresenter.this.mapMouseY(cursorY);
				}

				@Override
				public void requestFrameCaptureBurst() {
					OpenGLFramePresenter.this.requestFrameCaptureBurst();
				}

				@Override
				public void log(String message) {
					OpenGLFramePresenter.log(message);
				}
			},
			FRAME_CAPTURE_HOTKEY_ENABLED,
			FRAME_CAPTURE_BURST_FRAMES);
		inputBridge.initialize();
		inputBridge.installCallbacks();
	}

	private int mapMouseX(double cursorX) {
		return viewportPresenter.mapMouseX(cursorX);
	}

	private int mapMouseY(double cursorY) {
		return viewportPresenter.mapMouseY(cursorY);
	}

	private void releaseInputState() {
		if (inputBridge != null) {
			inputBridge.releaseInputState();
		}
	}

	private void suppressKeysUntilRelease() {
		if (inputBridge != null) {
			inputBridge.suppressKeysUntilRelease();
		}
	}

	private void processInput() throws Exception {
		if (inputBridge != null) {
			inputBridge.process();
		}
	}

	private void requestFrameCaptureBurst() {
		activeFrameCaptureBurstId = ++frameCaptureBurstSequence;
		frameCaptureBurstFrameIndex = 0;
		frameCaptureBurstCompletionLogged = false;
		frameCaptureRequested = true;
		frameCaptureBurstRemaining = FRAME_CAPTURE_BURST_FRAMES;
		RendererDiagnosticSession.Record event =
			RendererDiagnosticSession.newEventRecord("capture.burst.requested");
		if (event != null) {
			event.number("burstId", activeFrameCaptureBurstId);
			event.number("plannedFrames", FRAME_CAPTURE_BURST_FRAMES);
			event.number("rendererFrameSequence", RenderTelemetry.currentFrameSequence());
			RendererDiagnosticSession.writeEventRecord(event);
		}
		RenderTelemetry.recordDiagnosticBoundary("capture-burst-before");
	}

	private void cleanup() {
		Renderer3DSettings.setOpenGLPresentationAvailable(false);
		releaseInputState();
		if (windowController != null) {
			windowController.prepareForShutdown();
		}
		try {
			if (gl != null && windowController != null && windowController.hasWindow()) {
				if (spriteTextureCache != null) {
					spriteTextureCache.close();
					spriteTextureCache = null;
				}
				if (glyphTextureCache != null) {
					glyphTextureCache.close();
					glyphTextureCache = null;
				}
				if (visibleSpriteTextureAtlas != null) {
					visibleSpriteTextureAtlas.close();
					visibleSpriteTextureAtlas = null;
				}
				if (debugOverlayTextureAtlas != null) {
					debugOverlayTextureAtlas.close();
					debugOverlayTextureAtlas = null;
				}
				if (worldMeshRenderer != null) {
					worldMeshRenderer.close();
					worldMeshRenderer = null;
				}
				if (worldChunkRenderer != null) {
					worldChunkRenderer.close();
					worldChunkRenderer = null;
				}
				if (worldTextureCache != null) {
					worldTextureCache.close();
					worldTextureCache = null;
				}
				if (projectedWorldShader != null) {
					projectedWorldShader.close();
					projectedWorldShader = null;
				}
				if (residentChunkShader != null) {
					residentChunkShader.close();
					residentChunkShader = null;
				}
				if (worldSpriteRenderer != null) {
					worldSpriteRenderer.close();
					worldSpriteRenderer = null;
				}
				deleteScreenQuadBuffers();
				deleteUnitQuadBuffers();
				if (textureId != 0) {
					gl.glDeleteTextures(textureId);
					textureId = 0;
					textureWidth = 0;
					textureHeight = 0;
				}
				if (inputBridge != null) {
					inputBridge.close();
					inputBridge = null;
				}
			}
		} catch (Throwable t) {
			logCleanupFailure("release OpenGL resources", t);
		} finally {
			if (windowController != null) {
				windowController.shutdown(closed);
			}
		}
	}

	private static void logCleanupFailure(String operation, Throwable failure) {
		String message = failure.getMessage();
		log(
			"OpenGL presenter cleanup failure during "
				+ operation
				+ ": "
				+ failure.getClass().getName()
				+ (message == null || message.isEmpty() ? "" : ": " + message));
	}

	private void disable(String message) {
		Renderer3DSettings.setOpenGLPresentationAvailable(false);
		disabled = true;
		log(message);
		synchronized (frameLock) {
			if (pendingFrame != null) {
				pendingFrame.release();
				pendingFrame = null;
			}
			frameLock.notifyAll();
		}
	}

	@Override
	public void close() {
		log("OpenGL presenter close requested.");
		closed = true;
		synchronized (frameLock) {
			if (pendingFrame != null) {
				pendingFrame.release();
				pendingFrame = null;
			}
			frameLock.notifyAll();
		}
	}

	private static void log(String message) {
		OpenGLRendererLog.log(message);
	}

	private static boolean readBoolean(String propertyName, String envName) {
		return readBoolean(propertyName, envName, false);
	}

	private static boolean readBoolean(String propertyName, String envName, boolean defaultValue) {
		String value = System.getProperty(propertyName);
		if (value == null || value.trim().isEmpty()) {
			value = System.getenv(envName);
		}
		if (value == null) {
			return defaultValue;
		}

		value = value.trim();
		return "true".equalsIgnoreCase(value)
			|| "1".equals(value)
			|| "yes".equalsIgnoreCase(value)
			|| "on".equalsIgnoreCase(value);
	}

	private static boolean shouldDrawRemasterTerrainShadowMask() {
		return REMASTER_TERRAIN_SHADOW_MASK
			&& RendererLightingSettings.getMode() == RendererLightingSettings.Mode.DIRECTIONAL;
	}

	private static float readFloat(
		String propertyName,
		String envName,
		float defaultValue,
		float minValue,
		float maxValue) {
		String value = System.getProperty(propertyName);
		if (value == null || value.trim().isEmpty()) {
			value = System.getenv(envName);
		}
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}

		try {
			float parsed = Float.parseFloat(value.trim());
			if (Float.isNaN(parsed)) {
				return defaultValue;
			}
			return Math.max(minValue, Math.min(maxValue, parsed));
		} catch (NumberFormatException ignored) {
			return defaultValue;
		}
	}

	@SuppressWarnings("unused")
	private OpenGLTextureRegion uploadSpriteTexture(Sprite sprite) throws Exception {
		return uploadSpriteTexture(sprite, RendererSpriteTransform.IDENTITY);
	}

	private OpenGLTextureRegion uploadSpriteTexture(Sprite sprite, RendererSpriteTransform transform) throws Exception {
		if (spriteTextureCache == null) {
			throw new IllegalStateException("OpenGL sprite texture cache is not initialized");
		}
		return spriteTextureCache.getOrUpload(sprite, transform);
	}

	private OpenGLTextureRegion uploadSpriteTexture(Sprite sprite, boolean transparentMask) throws Exception {
		if (spriteTextureCache == null) {
			throw new IllegalStateException("OpenGL sprite texture cache is not initialized");
		}
		return spriteTextureCache.getOrUpload(sprite, RendererSpriteTransform.IDENTITY, transparentMask);
	}

	private static void closeClientFromOpenGLWindow() {
		EventQueue.invokeLater(() -> {
			if (OpenRSC.jframe != null) {
				OpenRSC.jframe.dispatchEvent(new WindowEvent(OpenRSC.jframe, WindowEvent.WINDOW_CLOSING));
			} else if (OpenRSC.applet != null) {
				OpenRSC.applet.close();
			}
		});
	}

	private interface OpenGLPassAction {
		void run() throws Exception;
	}

}
