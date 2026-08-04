package orsc;

import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DMaterialFamily;
import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.Renderer3DTextureData;
import orsc.graphics.three.Renderer3DWorldChunkFrame;
import orsc.graphics.three.WorldEditorTerrainGrid;
import orsc.util.FastMath;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * RENDERER-V2 OWNER: resident chunk static-world rendering. Terrain, walls,
 * roofs, wall objects, game objects, material batches, chunk culling, and
 * terrain shadow-mask application belong here rather than in the presenter.
 */
final class OpenGLWorldChunkRenderer implements AutoCloseable {
	private static final int LEGACY_TRANSPARENT_TEXTURE = 12345678;
	private static final int NO_FALLBACK_COLOR = Integer.MIN_VALUE;
	private static final int MAX_RESIDENT_CHUNKS = 256;
	private static final int MAX_UPLOAD_DIAGNOSTIC_CHUNKS_PER_ROLE = 24;
	static final int POSITION_COMPONENT_COUNT = 3;
	private static final int COLOR_COMPONENT_COUNT = 4;
	private static final int TEXTURE_COORD_COMPONENT_COUNT = 2;
	private static final int TEXTURE_LIGHT_COMPONENT_COUNT = 4;
	private static final int LEGACY_LIGHT_COMPONENT_COUNT = 1;
	private static final int BASE_LEGACY_LIGHT_COMPONENT_COUNT = 1;
	private static final int RAW_MATERIAL_COLOR_COMPONENT_COUNT = 3;
	private static final int NORMAL_COMPONENT_COUNT = 3;
	private static final int MODEL_KIND_COMPONENT_COUNT = 1;
	private static final int MATERIAL_FAMILY_COMPONENT_COUNT = 1;
	private static final int TERRAIN_VARIATION_MASK_COMPONENT_COUNT = 1;
	private static final int TERRAIN_BLEND_COLOR_COMPONENT_COUNT = 3;
	private static final int TERRAIN_BLEND_STRENGTH_COMPONENT_COUNT = 1;
	private static final int FLOATS_PER_VERTEX =
		POSITION_COMPONENT_COUNT
			+ COLOR_COMPONENT_COUNT
			+ TEXTURE_COORD_COMPONENT_COUNT
			+ TEXTURE_LIGHT_COMPONENT_COUNT
			+ LEGACY_LIGHT_COMPONENT_COUNT
			+ BASE_LEGACY_LIGHT_COMPONENT_COUNT
			+ RAW_MATERIAL_COLOR_COMPONENT_COUNT
			+ NORMAL_COMPONENT_COUNT
			+ MODEL_KIND_COMPONENT_COUNT
			+ MATERIAL_FAMILY_COMPONENT_COUNT
			+ TERRAIN_VARIATION_MASK_COMPONENT_COUNT
			+ TERRAIN_BLEND_COLOR_COMPONENT_COUNT
			+ TERRAIN_BLEND_STRENGTH_COMPONENT_COUNT;
	private static final int COLOR_OFFSET_BYTES = POSITION_COMPONENT_COUNT * 4;
	private static final int TEXTURE_COORD_OFFSET_BYTES =
		(POSITION_COMPONENT_COUNT + COLOR_COMPONENT_COUNT) * 4;
	private static final int TEXTURE_LIGHT_OFFSET_BYTES =
		(POSITION_COMPONENT_COUNT + COLOR_COMPONENT_COUNT + TEXTURE_COORD_COMPONENT_COUNT) * 4;
	private static final int LEGACY_LIGHT_OFFSET_BYTES =
		TEXTURE_LIGHT_OFFSET_BYTES + TEXTURE_LIGHT_COMPONENT_COUNT * 4;
	private static final int BASE_LEGACY_LIGHT_OFFSET_BYTES =
		LEGACY_LIGHT_OFFSET_BYTES + LEGACY_LIGHT_COMPONENT_COUNT * 4;
	private static final int RAW_MATERIAL_COLOR_OFFSET_BYTES =
		BASE_LEGACY_LIGHT_OFFSET_BYTES + BASE_LEGACY_LIGHT_COMPONENT_COUNT * 4;
	private static final int NORMAL_OFFSET_BYTES =
		RAW_MATERIAL_COLOR_OFFSET_BYTES + RAW_MATERIAL_COLOR_COMPONENT_COUNT * 4;
	private static final int MODEL_KIND_OFFSET_BYTES =
		NORMAL_OFFSET_BYTES + NORMAL_COMPONENT_COUNT * 4;
	private static final int MATERIAL_FAMILY_OFFSET_BYTES =
		MODEL_KIND_OFFSET_BYTES + MODEL_KIND_COMPONENT_COUNT * 4;
	private static final int TERRAIN_VARIATION_MASK_OFFSET_BYTES =
		MATERIAL_FAMILY_OFFSET_BYTES + MATERIAL_FAMILY_COMPONENT_COUNT * 4;
	private static final int TERRAIN_BLEND_COLOR_OFFSET_BYTES =
		TERRAIN_VARIATION_MASK_OFFSET_BYTES + TERRAIN_VARIATION_MASK_COMPONENT_COUNT * 4;
	private static final int TERRAIN_BLEND_STRENGTH_OFFSET_BYTES =
		TERRAIN_BLEND_COLOR_OFFSET_BYTES + TERRAIN_BLEND_COLOR_COMPONENT_COUNT * 4;
	private static final int STRIDE_BYTES = FLOATS_PER_VERTEX * 4;
	private static final String SPATIAL_BATCH_TILE_SIZE_PROPERTY =
		"spoiledmilk.openglWorldChunksSpatialTileSize";
	private static final String SPATIAL_BATCH_TILE_SIZE_ENV =
		"SPOILED_MILK_OPENGL_WORLD_CHUNKS_SPATIAL_TILE_SIZE";
	private static final String CHUNK_UPLOAD_BUDGET_MS_PROPERTY =
		"spoiledmilk.openglWorldChunkUploadBudgetMs";
	private static final String CHUNK_UPLOAD_BUDGET_MS_ENV =
		"SPOILED_MILK_OPENGL_WORLD_CHUNK_UPLOAD_BUDGET_MS";
	private static final int WORLD_CHUNK_TILE_AXIS = 144;
	private static final int TERRAIN_TILE_SIZE = 128;
	private static final int BELOW_TERRAIN_DEPTH = TERRAIN_TILE_SIZE * 4;
	private static final int SKY_DOME_SEGMENTS = 40;
	private static final float[] SKY_DOME_ELEVATION_DEGREES = new float[] {
		-90.0f, -68.0f, -48.0f, -30.0f, -15.0f, 0.0f, 12.0f, 30.0f, 60.0f, 90.0f
	};
	private static final int DEFAULT_SPATIAL_BATCH_TILE_SIZE = 12;
	private static final int MIN_SPATIAL_BATCH_TILE_SIZE = 4;
	private static final int MAX_SPATIAL_BATCH_TILE_SIZE = 24;
	private static final int SPATIAL_BATCH_TILE_SIZE = readSpatialBatchTileSize();
	static final long CHUNK_UPLOAD_BUDGET_NANOS =
		Math.round(readFloat(CHUNK_UPLOAD_BUDGET_MS_PROPERTY, CHUNK_UPLOAD_BUDGET_MS_ENV, 3.0f, 0.0f, 16.0f)
			* 1_000_000.0f);
	static final int SPATIAL_BATCH_WORLD_SIZE = SPATIAL_BATCH_TILE_SIZE * 128;
	static final int SPATIAL_BATCH_AXIS =
		Math.max(1, (WORLD_CHUNK_TILE_AXIS + SPATIAL_BATCH_TILE_SIZE - 1) / SPATIAL_BATCH_TILE_SIZE);
	static final int SPATIAL_BATCH_DISABLED = -1;
	private static final float FRUSTUM_BATCH_CULL_SCREEN_PADDING = 128.0f;
	private static final float FOG_BATCH_CULL_PADDING = 0.0f;
	private static final float FOG_VISUAL_END_FRACTION = 0.88f;
	private static final float WALL_DEPTH_PRIORITY_FACTOR = -1.0f;
	private static final float WALL_DEPTH_PRIORITY_UNITS = -1.0f;
	private static final float SHADOW_PROOF_DIRECTION_X = 0.78f;
	private static final float SHADOW_PROOF_DIRECTION_Z = 0.46f;
	private static final float SHADOW_PROOF_MIN_LENGTH = 384.0f;
	private static final float SHADOW_PROOF_MAX_LENGTH = 1536.0f;
	private static final float SHADOW_PROOF_MIN_WIDTH = 32.0f;
	private static final float SHADOW_PROOF_DIAGONAL_MIN_SPAN = 48.0f;
	private static final float SHADOW_PROOF_DIRECTIONAL_ALPHA = 0.55f;
	private static final float SHADOW_PROOF_MAX_ALPHA = 0.72f;
	private static final Renderer3DModelKind[] WORLD_CHUNK_KIND_DRAW_ORDER = new Renderer3DModelKind[] {
		Renderer3DModelKind.TERRAIN,
		Renderer3DModelKind.WALL,
		Renderer3DModelKind.ROOF,
		Renderer3DModelKind.WALL_OBJECT,
		Renderer3DModelKind.GAME_OBJECT,
		Renderer3DModelKind.UNCLASSIFIED
	};

	private final LwjglBindings gl;
	private final OpenGLWorldTextureCache textureCache;
	private final OpenGLShaderProgram residentChunkShader;
	private final boolean replacementCompositeEnabled;
	private final boolean residentObjectsEnabled;
	private final boolean shadowProofEnabled;
	private final boolean spatialCullEnabled;
	private final boolean texturedShaderEnabled;
	private final boolean remasterLightingShaderEnabled;
	private final boolean rawMaterialShaderEnabled;
	private final WorldEditorTerrainGrid worldEditorTerrainGrid = new WorldEditorTerrainGrid();
	private final LinkedHashMap<WorldChunkBufferKey, WorldChunkBuffer> residentChunks =
		new LinkedHashMap<WorldChunkBufferKey, WorldChunkBuffer>(MAX_RESIDENT_CHUNKS, 0.75f, true);
	private FloatBuffer vertexUploadBuffer;
	private IntBuffer indexUploadBuffer;
	private IntBuffer materialIndexUploadBuffer;
	private FloatBuffer worldToClipMatrixBuffer;
	private FloatBuffer worldViewMatrixBuffer;
	private FloatBuffer fogColorBuffer;
	private int vertexUploadCapacity;
	private int indexUploadCapacity;
	private int materialIndexUploadCapacity;
	private int remasterShadowMaskTextureId;
	private int remasterShadowMaskTextureWidth;
	private int remasterShadowMaskTextureHeight;
	private long remasterShadowMaskUploadedSignature;
	private final RemasterShadowMaskBuilder remasterShadowMaskBuilder = new RemasterShadowMaskBuilder();
	private RemasterTerrainShadowMask activeRemasterShadowMask;
	private int remasterGlowMaskTextureId;
	private int remasterGlowMaskTextureWidth;
	private int remasterGlowMaskTextureHeight;
	private long remasterGlowMaskUploadedSignature;
	private final RemasterGlowMaskBuilder remasterGlowMaskBuilder = new RemasterGlowMaskBuilder();
	private RemasterGlowMask activeRemasterGlowMask;
	private RemasterShadowRoofCoverage cachedRemasterShadowRoofCoverage;
	private long cachedRemasterShadowRoofCoverageSignature;
	private boolean cachedRemasterShadowRoofCoverageKnown;
	private RemasterShadowInventory cachedRemasterShadowInventory;
	private long cachedRemasterShadowInventoryWorldSignature;
	private long cachedRemasterShadowInventoryObjectCasterSignature;
	private boolean cachedRemasterShadowInventoryKnown;
	private boolean remasterShadowMaskLastUpload;
	private boolean remasterShadowMaskLastUploadSkip;
	private boolean closed;

	OpenGLWorldChunkRenderer(
		LwjglBindings gl,
		OpenGLWorldTextureCache textureCache,
		OpenGLShaderProgram residentChunkShader,
		boolean replacementCompositeEnabled,
		boolean residentObjectsEnabled,
		boolean shadowProofEnabled,
		boolean spatialCullEnabled,
		boolean texturedShaderEnabled,
		boolean remasterLightingShaderEnabled,
		boolean rawMaterialShaderEnabled) {
		this.gl = gl;
		this.textureCache = textureCache;
		this.residentChunkShader = residentChunkShader;
		this.replacementCompositeEnabled = replacementCompositeEnabled;
		this.residentObjectsEnabled = residentObjectsEnabled;
		this.shadowProofEnabled = shadowProofEnabled;
		this.spatialCullEnabled = spatialCullEnabled;
		this.texturedShaderEnabled = texturedShaderEnabled;
		this.remasterLightingShaderEnabled = remasterLightingShaderEnabled;
		this.rawMaterialShaderEnabled = rawMaterialShaderEnabled;
	}

	OpenGLWorldChunkUploadStats upload(
		Renderer3DFrame frame,
		Renderer3DWorldChunkFrame chunkFrame,
		boolean atlasTextureCoordinates,
		boolean budgetedUploadsAllowed) throws Exception {
		if (chunkFrame == null || chunkFrame.getChunkCount() <= 0) {
			return OpenGLWorldChunkUploadStats.EMPTY;
		}
		if (atlasTextureCoordinates && frame != null && textureCache != null) {
			textureCache.uploadReferencedTextures(frame, chunkFrame);
		}

		int requestedChunks = 0;
		int uploadedChunks = 0;
		int reusedChunks = 0;
		int deferredChunks = 0;
		String uploadReason = "steady";
		long uploadBudgetStart = System.nanoTime();
		int cacheSizeBefore = residentChunks.size();
		int[] requestedByRole =
			new int[OpenGLWorldChunkUploadStats.ROLE_COUNT];
		int[] uploadedByRole =
			new int[OpenGLWorldChunkUploadStats.ROLE_COUNT];
		int[] reusedByRole =
			new int[OpenGLWorldChunkUploadStats.ROLE_COUNT];
		int[] deferredByRole =
			new int[OpenGLWorldChunkUploadStats.ROLE_COUNT];
		long[] uploadedBytesByRole =
			new long[OpenGLWorldChunkUploadStats.ROLE_COUNT];
		long[] uploadNanosByRole =
			new long[OpenGLWorldChunkUploadStats.ROLE_COUNT];
		int coldKeyMisses = 0;
		int alternateStorageKeyMisses = 0;
		int alternateEquivalentKeyMisses = 0;
		int existingKeyMismatches = 0;
		UploadDiagnosticDetail diagnosticDetail =
			RenderTelemetry.isOpenGLBoundaryTransitionTraceActive()
				? new UploadDiagnosticDetail(
					MAX_UPLOAD_DIAGNOSTIC_CHUNKS_PER_ROLE)
				: null;
		List<ShadowProofCaster> shadowProofCasters = shouldDrawProjectedShadowProof()
			? buildProjectedShadowProofCasters(chunkFrame)
			: Collections.<ShadowProofCaster>emptyList();
		long shadowProofSignature =
			shouldDrawProjectedShadowProof() ? shadowProofCasterSignature(shadowProofCasters) : 0L;
		boolean drawOffsetSupported =
			useResidentChunkShader(atlasTextureCoordinates);
		for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunkFrame.getChunks()) {
			int vertexCount = chunk.getVertexCount();
			int indexCount = chunk.getIndexCount();
			if (vertexCount <= 0 || indexCount <= 0) {
				continue;
			}

			requestedChunks++;
			int roleIndex =
				OpenGLWorldChunkUploadStats.roleIndex(
					chunk.getChunkRole());
			requestedByRole[roleIndex]++;
			WorldChunkBufferKey key = WorldChunkBufferKey.from(
				chunk,
				drawOffsetSupported);
			WorldChunkBuffer buffer = residentChunks.get(key);
			int brightnessBits = currentBrightnessBits();
			int fogModeBits = currentFogModeBits();
			int lightingModeBits = currentLightingModeBits();
			int geometryModeBits = currentGeometryModeBits();
			long bufferSignature =
				chunkBufferSignature(chunk, drawOffsetSupported);
			boolean bufferMatches =
				buffer != null && buffer.matches(
					bufferSignature,
					vertexCount,
					indexCount,
					chunk.getTriangleCount(),
					atlasTextureCoordinates,
					brightnessBits,
					fogModeBits,
					lightingModeBits,
					geometryModeBits,
					replacementCompositeEnabled,
					shadowProofSignature);
			String mismatchReason = buffer == null
				? "new"
				: buffer.mismatchReason(
					bufferSignature,
					vertexCount,
					indexCount,
					chunk.getTriangleCount(),
					atlasTextureCoordinates,
					brightnessBits,
					fogModeBits,
					lightingModeBits,
					geometryModeBits,
					replacementCompositeEnabled,
					shadowProofSignature,
					chunk.isObjectChunk(),
					chunk.getChunkRole());
			String diagnosticMissReason = mismatchReason;
			if (buffer == null) {
				int alternateClass = diagnosticDetail == null
					? AlternateBufferMatch.NONE
					: findAlternateBufferMatch(
						key,
						bufferSignature,
						vertexCount,
						indexCount,
						chunk.getTriangleCount(),
						atlasTextureCoordinates,
						brightnessBits,
						fogModeBits,
						lightingModeBits,
						geometryModeBits,
						replacementCompositeEnabled,
						shadowProofSignature);
				if (alternateClass
						== AlternateBufferMatch.EQUIVALENT) {
					alternateEquivalentKeyMisses++;
					diagnosticMissReason =
						"new-alternate-equivalent";
				} else if (alternateClass
						== AlternateBufferMatch.STORAGE_ONLY) {
					alternateStorageKeyMisses++;
					diagnosticMissReason =
						"new-alternate-storage";
				} else {
					coldKeyMisses++;
					diagnosticMissReason = "new-cold";
				}
			} else if (!bufferMatches) {
				existingKeyMismatches++;
			}
			if (bufferMatches) {
				reusedChunks++;
				reusedByRole[roleIndex]++;
				continue;
			}

			if (buffer == null) {
				if (shouldDeferChunkUpload(uploadBudgetStart, uploadedChunks, budgetedUploadsAllowed)) {
					deferredChunks++;
					deferredByRole[roleIndex]++;
					uploadReason = mergeUploadReason(uploadReason, "defer:" + mismatchReason);
					if (diagnosticDetail != null) {
						diagnosticDetail.record(
							chunk,
							"defer-" + diagnosticMissReason,
							0L,
							0L);
					}
					continue;
				}
				buffer = new WorldChunkBuffer(gl.glGenBuffers(), gl.glGenBuffers(), gl.glGenBuffers());
				residentChunks.put(key, buffer);
			} else if (shouldDeferChunkUpload(uploadBudgetStart, uploadedChunks, budgetedUploadsAllowed)) {
				deferredChunks++;
				deferredByRole[roleIndex]++;
				uploadReason = mergeUploadReason(uploadReason, "defer:" + mismatchReason);
				if (diagnosticDetail != null) {
					diagnosticDetail.record(
						chunk,
						"defer-" + mismatchReason,
						0L,
						0L);
				}
				continue;
			}
			uploadReason = mergeUploadReason(uploadReason, mismatchReason);
			long chunkUploadStart =
				diagnosticDetail == null
					? 0L : System.nanoTime();
			uploadChunk(
				chunk,
				buffer,
				frame,
				bufferSignature,
				vertexCount,
				indexCount,
				atlasTextureCoordinates,
				brightnessBits,
				fogModeBits,
				lightingModeBits,
				geometryModeBits,
				replacementCompositeEnabled,
				shadowProofCasters,
				shadowProofSignature);
			long chunkUploadNanos =
				diagnosticDetail == null
					? 0L
					: Math.max(
						0L,
						System.nanoTime() - chunkUploadStart);
			uploadedChunks++;
			uploadedByRole[roleIndex]++;
			uploadedBytesByRole[roleIndex] +=
				buffer.uploadedByteCount;
			uploadNanosByRole[roleIndex] +=
				chunkUploadNanos;
			if (diagnosticDetail != null) {
				diagnosticDetail.record(
					chunk,
					"upload-" + diagnosticMissReason,
					buffer.uploadedByteCount,
					chunkUploadNanos);
			}
		}

		int evictedChunks = evictOverflow();
		return new OpenGLWorldChunkUploadStats(
			requestedChunks,
			uploadedChunks,
			reusedChunks,
			deferredChunks,
			evictedChunks,
			uploadReason,
			Math.max(0L, System.nanoTime() - uploadBudgetStart),
			budgetedUploadsAllowed ? CHUNK_UPLOAD_BUDGET_NANOS : 0L,
			requestedByRole,
			uploadedByRole,
			reusedByRole,
			deferredByRole,
			uploadedBytesByRole,
			uploadNanosByRole,
			coldKeyMisses,
			alternateStorageKeyMisses,
			alternateEquivalentKeyMisses,
			existingKeyMismatches,
			cacheSizeBefore,
			residentChunks.size(),
			diagnosticDetail == null
				? "" : diagnosticDetail.summary(),
			diagnosticDetail != null
				&& diagnosticDetail.isTruncated());
	}

	private int findAlternateBufferMatch(
		WorldChunkBufferKey requestedKey,
		long signature,
		int vertexCount,
		int indexCount,
		int triangleCount,
		boolean atlasTextureCoordinates,
		int brightnessBits,
		int fogModeBits,
		int lightingModeBits,
		int geometryModeBits,
		boolean replacementCompositeDrawOnly,
		long shadowProofSignature) {
		boolean storageMatch = false;
		for (Map.Entry<WorldChunkBufferKey, WorldChunkBuffer> entry
				: residentChunks.entrySet()) {
			WorldChunkBufferKey candidateKey = entry.getKey();
			if (candidateKey.equals(requestedKey)
				|| candidateKey.plane != requestedKey.plane
				|| candidateKey.objectOnly
					!= requestedKey.objectOnly
				|| candidateKey.chunkRole
					!= requestedKey.chunkRole) {
				continue;
			}
			WorldChunkBuffer candidate = entry.getValue();
			if (candidate.signature != signature) {
				continue;
			}
			storageMatch = true;
			if (candidate.matches(
					signature,
					vertexCount,
					indexCount,
					triangleCount,
					atlasTextureCoordinates,
					brightnessBits,
					fogModeBits,
					lightingModeBits,
					geometryModeBits,
					replacementCompositeDrawOnly,
					shadowProofSignature)) {
				return AlternateBufferMatch.EQUIVALENT;
			}
		}
		return storageMatch
			? AlternateBufferMatch.STORAGE_ONLY
			: AlternateBufferMatch.NONE;
	}

	private String mergeUploadReason(String currentReason, String nextReason) {
		if (nextReason == null || nextReason.trim().isEmpty()) {
			return currentReason;
		}
		if (currentReason == null || currentReason.length() == 0 || "steady".equals(currentReason)) {
			return nextReason;
		}
		if (currentReason.equals(nextReason) || currentReason.indexOf(nextReason) >= 0) {
			return currentReason;
		}
		return currentReason + "+" + nextReason;
	}

	private long chunkBufferSignature(
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		boolean drawOffsetSupported) {
		return drawOffsetSupported
			? chunk.getStorageSignature()
			: chunk.getSignature();
	}

	private boolean shouldDeferChunkUpload(
		long uploadBudgetStart,
		int uploadedChunks,
		boolean budgetedUploadsAllowed) {
		return budgetedUploadsAllowed
			&& CHUNK_UPLOAD_BUDGET_NANOS > 0L
			&& uploadedChunks > 0
			&& System.nanoTime() - uploadBudgetStart >= CHUNK_UPLOAD_BUDGET_NANOS;
	}

	OpenGLWorldChunkDrawStats drawDiagnostic(
		Renderer3DFrame frame,
		boolean filled,
		boolean textured) throws Exception {
		Renderer3DWorldChunkFrame chunkFrame = frame.getWorldChunkFrame();
		if (chunkFrame == null || chunkFrame.getChunkCount() <= 0 || residentChunks.isEmpty()) {
			return OpenGLWorldChunkDrawStats.EMPTY;
		}
		if ((filled || textured) && textureCache != null) {
			textureCache.uploadReferencedTextures(frame, chunkFrame);
		}

		boolean shaderActive = useResidentChunkShader(textured);
		FloatBuffer residentWorldToClipMatrix = null;
		FloatBuffer residentWorldViewMatrix = null;
		loadWorldProjectionAndView(frame);
		float[] batchCullViewMatrix = worldViewMatrix(frame);
		if (shaderActive) {
			residentWorldToClipMatrix = putWorldToClipMatrix(
				multiply(projectionMatrix(frame), batchCullViewMatrix));
			residentWorldViewMatrix = putWorldViewMatrix(batchCullViewMatrix);
		}
		if (shaderActive) {
			gl.glDisable(gl.GL_FOG);
		} else {
			configureFog(frame);
		}
		if (shaderActive) {
			bindResidentChunkShaderPass(
				frame,
				residentWorldToClipMatrix,
				residentWorldViewMatrix);
		}
		if (textured) {
			gl.glEnable(gl.GL_TEXTURE_2D);
			gl.glDisable(gl.GL_BLEND);
			gl.glEnable(gl.GL_ALPHA_TEST);
			gl.glAlphaFunc(gl.GL_GREATER, 0.5f);
		} else {
			gl.glDisable(gl.GL_TEXTURE_2D);
			gl.glDisable(gl.GL_ALPHA_TEST);
			gl.glEnable(gl.GL_BLEND);
			gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		}
		gl.glEnable(gl.GL_DEPTH_TEST);
		gl.glClear(gl.GL_DEPTH_BUFFER_BIT);
		boolean wireGeometry = RendererGeometrySettings.getMode() == RendererGeometrySettings.Mode.WIRE;
		gl.glPolygonMode(gl.GL_FRONT_AND_BACK, wireGeometry || (!filled && !textured) ? gl.GL_LINE : gl.GL_FILL);
		if (shaderActive) {
			gl.glDisableClientState(gl.GL_VERTEX_ARRAY);
			gl.glDisableClientState(gl.GL_COLOR_ARRAY);
			gl.glDisableClientState(gl.GL_TEXTURE_COORD_ARRAY);
		} else {
			gl.glEnableClientState(gl.GL_VERTEX_ARRAY);
			gl.glEnableClientState(gl.GL_COLOR_ARRAY);
			if (textured) {
				gl.glEnableClientState(gl.GL_TEXTURE_COORD_ARRAY);
			}
		}
		int drawnChunks = 0;
		int drawnTriangles = 0;
		int drawnTerrainTriangles = 0;
		int drawnWallTriangles = 0;
		int drawnRoofTriangles = 0;
		int drawnGameObjectTriangles = 0;
		int drawnWallObjectTriangles = 0;
		int drawnOtherTriangles = 0;
		int fallbackTriangles = 0;
		int skippedTriangles = 0;
		WorldChunkDrawAccumulator accumulator = new WorldChunkDrawAccumulator();
		try {
			if (textured) {
				drawChunkDiagnosticLayers(
					frame,
					chunkFrame,
					true,
					false,
					accumulator,
					batchCullViewMatrix,
					shaderActive,
					residentWorldToClipMatrix,
					residentWorldViewMatrix);
				gl.glEnable(gl.GL_BLEND);
				gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
				gl.glEnable(gl.GL_ALPHA_TEST);
				gl.glAlphaFunc(gl.GL_GREATER, 0.0f);
				drawChunkDiagnosticLayers(
					frame,
					chunkFrame,
					true,
					true,
					accumulator,
					batchCullViewMatrix,
					shaderActive,
					residentWorldToClipMatrix,
					residentWorldViewMatrix);
			} else {
				drawChunkDiagnosticLayers(
					frame,
					chunkFrame,
					false,
					false,
					accumulator,
					batchCullViewMatrix,
					false,
					null,
					null);
			}
			drawnChunks = accumulator.drawnChunkCount();
			drawnTriangles = accumulator.drawnTriangles;
			drawnTerrainTriangles = accumulator.drawnTerrainTriangles;
			drawnWallTriangles = accumulator.drawnWallTriangles;
			drawnRoofTriangles = accumulator.drawnRoofTriangles;
			drawnGameObjectTriangles = accumulator.drawnGameObjectTriangles;
			drawnWallObjectTriangles = accumulator.drawnWallObjectTriangles;
			drawnOtherTriangles = accumulator.drawnOtherTriangles;
			fallbackTriangles = accumulator.fallbackTriangles;
			skippedTriangles = accumulator.skippedTriangles;
		} finally {
			if (shaderActive && residentChunkShader != null) {
				residentChunkShader.unbindWorldTextureAttributes();
				gl.glUseProgram(0);
			}
			gl.glDisableClientState(gl.GL_TEXTURE_COORD_ARRAY);
			gl.glDisableClientState(gl.GL_COLOR_ARRAY);
			gl.glDisableClientState(gl.GL_VERTEX_ARRAY);
			gl.glPolygonMode(gl.GL_FRONT_AND_BACK, gl.GL_FILL);
			gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, 0);
			gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0);
			gl.glDepthMask(true);
			gl.glDisable(gl.GL_DEPTH_TEST);
			gl.glDisable(gl.GL_FOG);
			gl.glDisable(gl.GL_BLEND);
			gl.glDisable(gl.GL_ALPHA_TEST);
			gl.glDisable(gl.GL_CULL_FACE);
			gl.glEnable(gl.GL_TEXTURE_2D);
			gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
		}
		return new OpenGLWorldChunkDrawStats(
			accumulator.consideredChunkCount(),
			drawnChunks,
			accumulator.culledChunkCount(),
			drawnTriangles,
			drawnTerrainTriangles,
			drawnWallTriangles,
			drawnRoofTriangles,
			drawnGameObjectTriangles,
			drawnWallObjectTriangles,
			drawnOtherTriangles,
			fallbackTriangles,
			skippedTriangles,
			accumulator.consideredBatches,
			accumulator.drawnBatches,
			accumulator.culledBatches,
			accumulator.drawCalls,
			accumulator.textureBinds,
			accumulator.shadowProofChunks,
			accumulator.shadowProofIndices);
	}

	void drawWorldEditorBuildGrid(Renderer3DFrame frame) throws Exception {
		if (!WorldEditorBuildSettings.isEnabled() || frame == null) {
			return;
		}
		int[] segments = worldEditorTerrainGrid.segments(frame.getWorldChunkFrame(), frame.getActivePlane());
		if (segments.length == 0) {
			return;
		}

		loadWorldProjectionAndView(frame);
		gl.glUseProgram(0);
		gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0);
		gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, 0);
		gl.glDisableClientState(gl.GL_VERTEX_ARRAY);
		gl.glDisableClientState(gl.GL_COLOR_ARRAY);
		gl.glDisableClientState(gl.GL_TEXTURE_COORD_ARRAY);
		gl.glDisable(gl.GL_TEXTURE_2D);
		gl.glDisable(gl.GL_ALPHA_TEST);
		gl.glDisable(gl.GL_FOG);
		gl.glDisable(gl.GL_CULL_FACE);
		gl.glEnable(gl.GL_DEPTH_TEST);
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		gl.glDepthMask(false);
		gl.glLineWidth(1.25f);
		gl.glColor4f(0.38f, 0.88f, 0.95f, 0.72f);
		try {
			gl.glBegin(gl.GL_LINES);
			for (int offset = 0; offset + 5 < segments.length; offset += 6) {
				gl.glVertex3f(segments[offset], segments[offset + 1] - 2.0f, segments[offset + 2]);
				gl.glVertex3f(segments[offset + 3], segments[offset + 4] - 2.0f, segments[offset + 5]);
			}
			gl.glEnd();
		} finally {
			gl.glLineWidth(1.0f);
			gl.glDepthMask(true);
			gl.glDisable(gl.GL_DEPTH_TEST);
			gl.glDisable(gl.GL_BLEND);
			gl.glDisable(gl.GL_ALPHA_TEST);
			gl.glDisable(gl.GL_FOG);
			gl.glDisable(gl.GL_CULL_FACE);
			gl.glEnable(gl.GL_TEXTURE_2D);
			gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
		}
	}

	void drawBelowTerrainDepthFloor(Renderer3DFrame frame) throws Exception {
		if (OpenGLBelowTerrainSettings.getMode() != OpenGLBelowTerrainSettings.Mode.DEPTH_FLOOR
			|| frame == null) {
			return;
		}
		BelowTerrainFloor floor = BelowTerrainFloor.from(frame.getWorldChunkFrame(), frame.getActivePlane());
		if (floor == null) {
			return;
		}

		loadWorldProjectionAndView(frame);
		gl.glUseProgram(0);
		gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0);
		gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, 0);
		gl.glDisableClientState(gl.GL_VERTEX_ARRAY);
		gl.glDisableClientState(gl.GL_COLOR_ARRAY);
		gl.glDisableClientState(gl.GL_TEXTURE_COORD_ARRAY);
		gl.glDisable(gl.GL_TEXTURE_2D);
		gl.glDisable(gl.GL_ALPHA_TEST);
		gl.glDisable(gl.GL_BLEND);
		gl.glDisable(gl.GL_FOG);
		gl.glDisable(gl.GL_CULL_FACE);
		gl.glEnable(gl.GL_DEPTH_TEST);
		gl.glDepthMask(false);
		gl.glColor4f(0.004f, 0.005f, 0.008f, 1.0f);
		try {
			gl.glBegin(gl.GL_QUADS);
			gl.glVertex3f(floor.minX, floor.y, floor.minZ);
			gl.glVertex3f(floor.maxX, floor.y, floor.minZ);
			gl.glVertex3f(floor.maxX, floor.y, floor.maxZ);
			gl.glVertex3f(floor.minX, floor.y, floor.maxZ);
			gl.glEnd();
		} finally {
			gl.glDepthMask(true);
			gl.glDisable(gl.GL_DEPTH_TEST);
			gl.glDisable(gl.GL_BLEND);
			gl.glDisable(gl.GL_ALPHA_TEST);
			gl.glDisable(gl.GL_FOG);
			gl.glDisable(gl.GL_CULL_FACE);
			gl.glEnable(gl.GL_TEXTURE_2D);
			gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
		}
	}

	void drawWorldAnchoredSky(
		Renderer3DFrame frame,
		RendererDayNightCycle.Presentation presentation) throws Exception {
		if (frame == null || presentation == null) {
			return;
		}

		loadMatrix(gl.GL_PROJECTION, projectionMatrix(frame));
		loadMatrix(gl.GL_MODELVIEW, skyViewMatrix(frame));
		gl.glUseProgram(0);
		gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0);
		gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, 0);
		gl.glDisableClientState(gl.GL_VERTEX_ARRAY);
		gl.glDisableClientState(gl.GL_COLOR_ARRAY);
		gl.glDisableClientState(gl.GL_TEXTURE_COORD_ARRAY);
		gl.glDisable(gl.GL_TEXTURE_2D);
		gl.glDisable(gl.GL_ALPHA_TEST);
		gl.glDisable(gl.GL_BLEND);
		gl.glDisable(gl.GL_FOG);
		gl.glDisable(gl.GL_CULL_FACE);
		gl.glDisable(gl.GL_DEPTH_TEST);
		float radius = Math.max(2048.0f, frame.getFogDistance() * 0.92f);
		try {
			drawWorldSkyDome(radius, presentation);
			float skyBrightness = clampStatic(
				(presentation.skyRed + presentation.skyGreen + presentation.skyBlue) / 3.0f,
				0.0f,
				1.0f);
			float starVisibility = smoothSkyAmount((0.58f - skyBrightness) / 0.40f);
			float cloudVisibility = smoothSkyAmount((skyBrightness - 0.20f) / 0.42f);
			drawWorldSkyMilkyWay(radius, starVisibility);
			drawWorldSkyStars(radius, starVisibility);
			drawWorldSkyClouds(radius, presentation, cloudVisibility);
		} finally {
			gl.glDisable(gl.GL_DEPTH_TEST);
			gl.glDisable(gl.GL_BLEND);
			gl.glDisable(gl.GL_ALPHA_TEST);
			gl.glDisable(gl.GL_FOG);
			gl.glDisable(gl.GL_CULL_FACE);
			gl.glEnable(gl.GL_TEXTURE_2D);
			gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
		}
	}

	private void drawWorldSkyClouds(
		float radius,
		RendererDayNightCycle.Presentation presentation,
		float visibility) throws Exception {
		if (visibility <= 0.001f) {
			return;
		}
		float red = mixSky(presentation.fogRed, 1.0f, 0.70f);
		float green = mixSky(presentation.fogGreen, 1.0f, 0.70f);
		float blue = mixSky(presentation.fogBlue, 1.0f, 0.74f);
		float alpha = 0.26f * visibility;
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		drawWorldSkyCloudCluster(radius, -18.0f, 8.0f, 1.00f, 0, red, green, blue, alpha);
		drawWorldSkyCloudCluster(radius, -34.0f, 42.0f, 0.82f, 1, red, green, blue, alpha * 0.74f);
		drawWorldSkyCloudCluster(radius, -51.0f, 75.0f, 0.76f, 2, red, green, blue, alpha * 0.66f);
		drawWorldSkyCloudCluster(radius, -22.0f, 108.0f, 1.12f, 0, red, green, blue, alpha * 0.94f);
		drawWorldSkyCloudCluster(radius, -39.0f, 143.0f, 0.91f, 1, red, green, blue, alpha * 0.72f);
		drawWorldSkyCloudCluster(radius, -17.0f, 177.0f, 0.86f, 2, red, green, blue, alpha * 0.80f);
		drawWorldSkyCloudCluster(radius, -46.0f, 210.0f, 0.80f, 0, red, green, blue, alpha * 0.70f);
		drawWorldSkyCloudCluster(radius, -27.0f, 241.0f, 1.04f, 1, red, green, blue, alpha * 0.86f);
		drawWorldSkyCloudCluster(radius, -15.0f, 274.0f, 1.08f, 0, red, green, blue, alpha * 0.90f);
		drawWorldSkyCloudCluster(radius, -43.0f, 303.0f, 0.74f, 2, red, green, blue, alpha * 0.68f);
		drawWorldSkyCloudCluster(radius, -25.0f, 334.0f, 0.94f, 1, red, green, blue, alpha * 0.78f);
		drawWorldSkyCloudCluster(radius, -58.0f, 356.0f, 0.68f, 2, red, green, blue, alpha * 0.58f);
		gl.glDisable(gl.GL_BLEND);
	}

	private void drawWorldSkyCloudCluster(
		float radius,
		float elevationDegrees,
		float azimuthDegrees,
		float scale,
		int variant,
		float red,
		float green,
		float blue,
		float alpha) throws Exception {
		if (variant == 1) {
			drawWorldSkyWispyCloud(radius, elevationDegrees, azimuthDegrees, scale, red, green, blue, alpha);
			return;
		}
		if (variant == 2) {
			drawWorldSkyTowerCloud(radius, elevationDegrees, azimuthDegrees, scale, red, green, blue, alpha);
			return;
		}
		drawWorldSkyCumulusCloud(radius, elevationDegrees, azimuthDegrees, scale, red, green, blue, alpha);
	}

	private void drawWorldSkyCumulusCloud(
		float radius,
		float elevationDegrees,
		float azimuthDegrees,
		float scale,
		float red,
		float green,
		float blue,
		float alpha) throws Exception {
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.000f, 0.008f * scale, 0.082f * scale, 0.019f * scale,
			red, green, blue, alpha * 0.34f, 24);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			-0.036f * scale, -0.003f * scale, 0.041f * scale, 0.026f * scale,
			red, green, blue, alpha * 0.58f, 20);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			-0.009f * scale, -0.015f * scale, 0.054f * scale, 0.037f * scale,
			red, green, blue, alpha * 0.88f, 24);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.034f * scale, -0.007f * scale, 0.049f * scale, 0.030f * scale,
			red, green, blue, alpha * 0.72f, 22);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.012f * scale, -0.031f * scale, 0.036f * scale, 0.026f * scale,
			red, green, blue, alpha * 0.62f, 20);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.020f * scale, 0.012f * scale, 0.076f * scale, 0.015f * scale,
			red, green, blue, alpha * 0.25f, 24);
	}

	private void drawWorldSkyWispyCloud(
		float radius,
		float elevationDegrees,
		float azimuthDegrees,
		float scale,
		float red,
		float green,
		float blue,
		float alpha) throws Exception {
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			-0.030f * scale, 0.000f, 0.092f * scale, 0.013f * scale,
			red, green, blue, alpha * 0.32f, 26);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.045f * scale, -0.009f * scale, 0.078f * scale, 0.012f * scale,
			red, green, blue, alpha * 0.28f, 24);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			-0.056f * scale, -0.015f * scale, 0.048f * scale, 0.018f * scale,
			red, green, blue, alpha * 0.38f, 20);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.003f, -0.021f * scale, 0.050f * scale, 0.022f * scale,
			red, green, blue, alpha * 0.46f, 20);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.061f * scale, 0.008f * scale, 0.060f * scale, 0.010f * scale,
			red, green, blue, alpha * 0.22f, 22);
	}

	private void drawWorldSkyTowerCloud(
		float radius,
		float elevationDegrees,
		float azimuthDegrees,
		float scale,
		float red,
		float green,
		float blue,
		float alpha) throws Exception {
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.000f, 0.012f * scale, 0.076f * scale, 0.018f * scale,
			red, green, blue, alpha * 0.30f, 24);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			-0.032f * scale, -0.004f * scale, 0.043f * scale, 0.027f * scale,
			red, green, blue, alpha * 0.58f, 20);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.022f * scale, -0.012f * scale, 0.051f * scale, 0.034f * scale,
			red, green, blue, alpha * 0.76f, 22);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.004f, -0.046f * scale, 0.040f * scale, 0.040f * scale,
			red, green, blue, alpha * 0.84f, 22);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			-0.010f * scale, -0.076f * scale, 0.027f * scale, 0.030f * scale,
			red, green, blue, alpha * 0.64f, 18);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.045f * scale, 0.004f * scale, 0.047f * scale, 0.022f * scale,
			red, green, blue, alpha * 0.46f, 20);
	}

	private void drawWorldSkyMilkyWay(float radius, float visibility) throws Exception {
		if (visibility <= 0.001f) {
			return;
		}
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		for (int patch = 0; patch < 18; patch++) {
			float azimuth = patch * 20.0f + 7.0f;
			double bandAngle = Math.toRadians(azimuth - 32.0f);
			float elevation = -43.0f + 23.0f * (float) Math.sin(bandAngle);
			float rotation = 19.0f * (float) Math.cos(bandAngle);
			drawWorldSkySoftDisc(radius, elevation, azimuth,
				0.0f, 0.0f, 0.145f, 0.026f,
				0.70f, 0.76f, 1.0f, visibility * 0.030f, 28, rotation);
			drawWorldSkySoftDisc(radius, elevation - 2.5f, azimuth + 4.0f,
				0.0f, 0.0f, 0.095f, 0.017f,
				0.92f, 0.88f, 1.0f, visibility * 0.022f, 24, rotation);
		}
		for (int star = 0; star < 180; star++) {
			float azimuth = (star * 71.317f + 13.0f) % 360.0f;
			double bandAngle = Math.toRadians(azimuth - 32.0f);
			float centerElevation = -43.0f + 23.0f * (float) Math.sin(bandAngle);
			float spread = (((star * 43 + 19) % 101) - 50) / 5.2f;
			float size = 0.00062f + ((star * 17 + 3) % 9) * 0.000055f;
			float alpha = visibility * (0.18f + ((star * 23 + 5) % 37) / 100.0f);
			drawWorldSkySoftDisc(radius, centerElevation + spread, azimuth,
				0.0f, 0.0f, size, size,
				0.78f, 0.84f, 1.0f, alpha, 10);
		}
		gl.glDisable(gl.GL_BLEND);
	}

	private void drawWorldSkyStars(float radius, float visibility) throws Exception {
		if (visibility <= 0.001f) {
			return;
		}
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		for (int star = 0; star < 180; star++) {
			float azimuth = (star * 137.50777f + (star % 7) * 9.35f) % 360.0f;
			float elevation = -8.0f - ((star * 53 + 17) % 79);
			float brightness = 0.42f + ((star * 29 + 11) % 59) / 100.0f;
			float size = 0.00115f + ((star * 31 + 7) % 13) * 0.000075f;
			if (star % 17 == 0) {
				size *= 1.85f;
				brightness = 1.0f;
			}
			float red = star % 9 == 0 ? 1.0f : 0.88f;
			float green = star % 11 == 0 ? 0.84f : 0.93f;
			float blue = star % 9 == 0 ? 0.74f : 1.0f;
			drawWorldSkySoftDisc(radius, elevation, azimuth,
				0.0f, 0.0f, size, size, red, green, blue, visibility * brightness, 12);
		}
		gl.glDisable(gl.GL_BLEND);
	}

	private void drawWorldSkySoftDisc(
		float radius,
		float elevationDegrees,
		float azimuthDegrees,
		float tangentOffsetFraction,
		float verticalOffsetFraction,
		float tangentRadiusFraction,
		float verticalRadiusFraction,
		float red,
		float green,
		float blue,
		float alpha,
		int segments) throws Exception {
		drawWorldSkySoftDisc(
			radius,
			elevationDegrees,
			azimuthDegrees,
			tangentOffsetFraction,
			verticalOffsetFraction,
			tangentRadiusFraction,
			verticalRadiusFraction,
			red,
			green,
			blue,
			alpha,
			segments,
			0.0f);
	}

	private void drawWorldSkySoftDisc(
		float radius,
		float elevationDegrees,
		float azimuthDegrees,
		float tangentOffsetFraction,
		float verticalOffsetFraction,
		float tangentRadiusFraction,
		float verticalRadiusFraction,
		float red,
		float green,
		float blue,
		float alpha,
		int segments,
		float rotationDegrees) throws Exception {
		double elevation = Math.toRadians(elevationDegrees);
		double azimuth = Math.toRadians(azimuthDegrees);
		float cosElevation = (float) Math.cos(elevation);
		float sinElevation = (float) Math.sin(elevation);
		float sinAzimuth = (float) Math.sin(azimuth);
		float cosAzimuth = (float) Math.cos(azimuth);
		float directionX = cosElevation * sinAzimuth;
		float directionY = sinElevation;
		float directionZ = cosElevation * cosAzimuth;
		float centerRadius = radius * 0.965f;
		float tangentX = cosAzimuth;
		float tangentY = 0.0f;
		float tangentZ = -sinAzimuth;
		float verticalX = -sinElevation * sinAzimuth;
		float verticalY = cosElevation;
		float verticalZ = -sinElevation * cosAzimuth;
		if (rotationDegrees != 0.0f) {
			double rotation = Math.toRadians(rotationDegrees);
			float rotationCos = (float) Math.cos(rotation);
			float rotationSin = (float) Math.sin(rotation);
			float rotatedTangentX = tangentX * rotationCos + verticalX * rotationSin;
			float rotatedTangentY = tangentY * rotationCos + verticalY * rotationSin;
			float rotatedTangentZ = tangentZ * rotationCos + verticalZ * rotationSin;
			float rotatedVerticalX = verticalX * rotationCos - tangentX * rotationSin;
			float rotatedVerticalY = verticalY * rotationCos - tangentY * rotationSin;
			float rotatedVerticalZ = verticalZ * rotationCos - tangentZ * rotationSin;
			tangentX = rotatedTangentX;
			tangentY = rotatedTangentY;
			tangentZ = rotatedTangentZ;
			verticalX = rotatedVerticalX;
			verticalY = rotatedVerticalY;
			verticalZ = rotatedVerticalZ;
		}
		float tangentOffset = radius * tangentOffsetFraction;
		float verticalOffset = radius * verticalOffsetFraction;
		float centerX = directionX * centerRadius + tangentX * tangentOffset + verticalX * verticalOffset;
		float centerY = directionY * centerRadius + tangentY * tangentOffset + verticalY * verticalOffset;
		float centerZ = directionZ * centerRadius + tangentZ * tangentOffset + verticalZ * verticalOffset;
		float tangentRadius = radius * tangentRadiusFraction;
		float verticalRadius = radius * verticalRadiusFraction;

		gl.glBegin(gl.GL_TRIANGLES);
		for (int segment = 0; segment < segments; segment++) {
			gl.glColor4f(red, green, blue, clampStatic(alpha, 0.0f, 1.0f));
			drawWorldSkySoftDiscVertex(centerX, centerY, centerZ);
			gl.glColor4f(red, green, blue, 0.0f);
			drawWorldSkySoftDiscEdge(
				centerX,
				centerY,
				centerZ,
				tangentX,
				tangentY,
				tangentZ,
				verticalX,
				verticalY,
				verticalZ,
				tangentRadius,
				verticalRadius,
				segment * Math.PI * 2.0 / segments);
			drawWorldSkySoftDiscEdge(
				centerX,
				centerY,
				centerZ,
				tangentX,
				tangentY,
				tangentZ,
				verticalX,
				verticalY,
				verticalZ,
				tangentRadius,
				verticalRadius,
				(segment + 1) * Math.PI * 2.0 / segments);
		}
		gl.glEnd();
	}

	private void drawWorldSkySoftDiscEdge(
		float centerX,
		float centerY,
		float centerZ,
		float tangentX,
		float tangentY,
		float tangentZ,
		float verticalX,
		float verticalY,
		float verticalZ,
		float tangentRadius,
		float verticalRadius,
		double angle) throws Exception {
		float tangentAmount = tangentRadius * (float) Math.cos(angle);
		float verticalAmount = verticalRadius * (float) Math.sin(angle);
		drawWorldSkySoftDiscVertex(
			centerX + tangentX * tangentAmount + verticalX * verticalAmount,
			centerY + tangentY * tangentAmount + verticalY * verticalAmount,
			centerZ + tangentZ * tangentAmount + verticalZ * verticalAmount);
	}

	private void drawWorldSkySoftDiscVertex(float x, float y, float z) throws Exception {
		gl.glVertex3f(x, y, z);
	}

	private void drawWorldSkyDome(
		float radius,
		RendererDayNightCycle.Presentation presentation) throws Exception {
		gl.glBegin(gl.GL_QUADS);
		for (int ring = 0; ring + 1 < SKY_DOME_ELEVATION_DEGREES.length; ring++) {
			float upperElevation = SKY_DOME_ELEVATION_DEGREES[ring];
			float lowerElevation = SKY_DOME_ELEVATION_DEGREES[ring + 1];
			for (int segment = 0; segment < SKY_DOME_SEGMENTS; segment++) {
				float firstAzimuth = segment * 360.0f / SKY_DOME_SEGMENTS;
				float secondAzimuth = (segment + 1) * 360.0f / SKY_DOME_SEGMENTS;
				applyWorldSkyColor(presentation, upperElevation);
				drawWorldSkyVertex(radius, upperElevation, firstAzimuth);
				drawWorldSkyVertex(radius, upperElevation, secondAzimuth);
				applyWorldSkyColor(presentation, lowerElevation);
				drawWorldSkyVertex(radius, lowerElevation, secondAzimuth);
				drawWorldSkyVertex(radius, lowerElevation, firstAzimuth);
			}
		}
		gl.glEnd();
	}

	private void drawWorldSkyVertex(float radius, float elevationDegrees, float azimuthDegrees) throws Exception {
		double elevation = Math.toRadians(elevationDegrees);
		double azimuth = Math.toRadians(azimuthDegrees);
		float horizontalRadius = radius * (float) Math.cos(elevation);
		gl.glVertex3f(
			horizontalRadius * (float) Math.sin(azimuth),
			radius * (float) Math.sin(elevation),
			horizontalRadius * (float) Math.cos(azimuth));
	}

	private void applyWorldSkyColor(
		RendererDayNightCycle.Presentation presentation,
		float elevationDegrees) throws Exception {
		float horizonRed = presentation.fogRed;
		float horizonGreen = presentation.fogGreen;
		float horizonBlue = presentation.fogBlue;
		if (elevationDegrees >= 0.0f) {
			float below = smoothSkyAmount(elevationDegrees / 90.0f);
			gl.glColor4f(
				mixSky(horizonRed, horizonRed * 0.34f, below),
				mixSky(horizonGreen, horizonGreen * 0.36f, below),
				mixSky(horizonBlue, horizonBlue * 0.44f, below),
				1.0f);
			return;
		}

		float altitude = smoothSkyAmount(-elevationDegrees / 90.0f);
		float zenithRed = clampStatic(presentation.skyRed * 0.58f, 0.0f, 1.0f);
		float zenithGreen = clampStatic(presentation.skyGreen * 0.70f, 0.0f, 1.0f);
		float zenithBlue = clampStatic(presentation.skyBlue * 1.08f, 0.0f, 1.0f);
		gl.glColor4f(
			mixSky(horizonRed, zenithRed, altitude),
			mixSky(horizonGreen, zenithGreen, altitude),
			mixSky(horizonBlue, zenithBlue, altitude),
			1.0f);
	}

	private static float smoothSkyAmount(float value) {
		float clamped = clampStatic(value, 0.0f, 1.0f);
		return clamped * clamped * (3.0f - 2.0f * clamped);
	}

	private static float mixSky(float from, float to, float amount) {
		return from + (to - from) * clampStatic(amount, 0.0f, 1.0f);
	}

	private static final class BelowTerrainFloor {
		private final float minX;
		private final float maxX;
		private final float y;
		private final float minZ;
		private final float maxZ;

		private BelowTerrainFloor(float minX, float maxX, float y, float minZ, float maxZ) {
			this.minX = minX;
			this.maxX = maxX;
			this.y = y;
			this.minZ = minZ;
			this.maxZ = maxZ;
		}

		private static BelowTerrainFloor from(Renderer3DWorldChunkFrame frame, int activePlane) {
			if (frame == null) {
				return null;
			}
			for (Renderer3DWorldChunkFrame.ChunkMesh chunk : frame.getChunks()) {
				if (!isActiveTerrainChunk(chunk, activePlane)) {
					continue;
				}
				BelowTerrainFloor floor = chunk.hasWorldEditorTerrainGrid()
					? fromTerrainGrid(chunk)
					: fromTerrainTriangles(chunk);
				if (floor != null) {
					return floor;
				}
			}
			return null;
		}

		private static BelowTerrainFloor fromTerrainGrid(Renderer3DWorldChunkFrame.ChunkMesh chunk) {
			int axis = chunk.getWorldEditorTerrainGridAxis();
			if (axis < 2) {
				return null;
			}
			int maxY = Integer.MIN_VALUE;
			for (int index = 0; index < axis * axis; index++) {
				maxY = Math.max(maxY, chunk.getWorldEditorTerrainGridHeight(index));
			}
			float maxCoord = (axis - 1) * TERRAIN_TILE_SIZE;
			return new BelowTerrainFloor(
				0.0f,
				maxCoord,
				maxY + BELOW_TERRAIN_DEPTH,
				0.0f,
				maxCoord);
		}

		private static BelowTerrainFloor fromTerrainTriangles(Renderer3DWorldChunkFrame.ChunkMesh chunk) {
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int maxY = Integer.MIN_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxZ = Integer.MIN_VALUE;
			int triangleCount = Math.min(chunk.getTriangleCount(), chunk.getIndexCount() / 3);
			for (int triangle = 0; triangle < triangleCount; triangle++) {
				if (chunk.getTriangleModelKind(triangle) != Renderer3DModelKind.TERRAIN) {
					continue;
				}
				for (int corner = 0; corner < 3; corner++) {
					int vertex = chunk.getIndex(triangle * 3 + corner);
					if (vertex < 0 || vertex >= chunk.getVertexCount()) {
						continue;
					}
					int coord = vertex * POSITION_COMPONENT_COUNT;
					minX = Math.min(minX, chunk.getVertexCoord(coord));
					maxX = Math.max(maxX, chunk.getVertexCoord(coord));
					maxY = Math.max(maxY, chunk.getVertexCoord(coord + 1));
					minZ = Math.min(minZ, chunk.getVertexCoord(coord + 2));
					maxZ = Math.max(maxZ, chunk.getVertexCoord(coord + 2));
				}
			}
			if (minX == Integer.MAX_VALUE || minZ == Integer.MAX_VALUE) {
				return null;
			}
			return new BelowTerrainFloor(minX, maxX, maxY + BELOW_TERRAIN_DEPTH, minZ, maxZ);
		}

		private static boolean isActiveTerrainChunk(
			Renderer3DWorldChunkFrame.ChunkMesh chunk,
			int activePlane) {
			return chunk != null
				&& chunk.getChunkRole() == Renderer3DWorldChunkFrame.CHUNK_ROLE_WORLD
				&& chunk.getPlane() == activePlane
				&& (chunk.getTerrainTriangles() > 0 || chunk.hasWorldEditorTerrainGrid());
		}
	}

	boolean canApplyRemasterTerrainShadowMaskInChunkShader(boolean textured) {
		return useResidentChunkShader(textured) && remasterLightingShaderEnabled;
	}

	RemasterShadowInventory inspectRemasterShadowInventory(
		Renderer3DWorldChunkFrame chunkFrame) {
		if (chunkFrame == null || chunkFrame.getChunkCount() <= 0) {
			return RemasterShadowInventory.EMPTY;
		}
		long worldSignature = RemasterShadowMaskBuilder.remasterShadowWorldSignature(chunkFrame);
		long objectCasterSignature = chunkFrame.getObjectShadowCasterSignature();
		if (cachedRemasterShadowInventoryKnown
			&& cachedRemasterShadowInventoryWorldSignature == worldSignature
			&& cachedRemasterShadowInventoryObjectCasterSignature == objectCasterSignature
			&& cachedRemasterShadowInventory != null) {
			return cachedRemasterShadowInventory;
		}
		cachedRemasterShadowInventory = RemasterShadowClassifier.inspectInventory(
			chunkFrame,
			remasterShadowRoofCoverage(chunkFrame, worldSignature));
		cachedRemasterShadowInventoryWorldSignature = worldSignature;
		cachedRemasterShadowInventoryObjectCasterSignature = objectCasterSignature;
		cachedRemasterShadowInventoryKnown = true;
		return cachedRemasterShadowInventory;
	}

	void drawRemasterShadowInventoryDebug(Renderer3DFrame frame) throws Exception {
		Renderer3DWorldChunkFrame chunkFrame = frame == null ? null : frame.getWorldChunkFrame();
		if (chunkFrame == null || chunkFrame.getChunkCount() <= 0) {
			return;
		}

		loadWorldProjectionAndView(frame);
		gl.glUseProgram(0);
		gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0);
		gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, 0);
		gl.glDisableClientState(gl.GL_VERTEX_ARRAY);
		gl.glDisableClientState(gl.GL_COLOR_ARRAY);
		gl.glDisableClientState(gl.GL_TEXTURE_COORD_ARRAY);
		gl.glDisable(gl.GL_TEXTURE_2D);
		gl.glDisable(gl.GL_ALPHA_TEST);
		gl.glDisable(gl.GL_FOG);
		gl.glDisable(gl.GL_CULL_FACE);
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		gl.glDepthMask(false);
		try {
			long worldSignature = RemasterShadowMaskBuilder.remasterShadowWorldSignature(chunkFrame);
			RemasterShadowRoofCoverage roofCoverage =
				remasterShadowRoofCoverage(chunkFrame, worldSignature);
			drawRemasterShadowReceiverDebug(chunkFrame, roofCoverage);
			drawRemasterShadowCasterDebug(chunkFrame, roofCoverage);
		} finally {
			gl.glDisable(gl.GL_POLYGON_OFFSET_FILL);
			gl.glLineWidth(1.0f);
			gl.glDepthMask(true);
			gl.glDisable(gl.GL_BLEND);
			gl.glDisable(gl.GL_DEPTH_TEST);
			gl.glDisable(gl.GL_ALPHA_TEST);
			gl.glDisable(gl.GL_FOG);
			gl.glDisable(gl.GL_CULL_FACE);
			gl.glEnable(gl.GL_TEXTURE_2D);
			gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
		}
	}

	private void drawRemasterShadowReceiverDebug(
		Renderer3DWorldChunkFrame chunkFrame,
		RemasterShadowRoofCoverage roofCoverage) throws Exception {
		gl.glDisable(gl.GL_DEPTH_TEST);
		gl.glBegin(gl.GL_TRIANGLES);
		for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunkFrame.getChunks()) {
			int triangleCount = Math.min(chunk.getTriangleCount(), chunk.getIndexCount() / 3);
			for (int triangle = 0; triangle < triangleCount; triangle++) {
				if (chunk.getTriangleModelKind(triangle) != Renderer3DModelKind.TERRAIN) {
					continue;
				}
				applyRemasterShadowReceiverDebugColor(RemasterShadowClassifier.roofClassificationForTriangle(roofCoverage, chunk, triangle));
				int indexBase = triangle * 3;
				for (int corner = 0; corner < 3; corner++) {
					drawChunkVertex(chunk, chunk.getIndex(indexBase + corner));
				}
			}
		}
		gl.glEnd();
	}

	private void drawRemasterShadowCasterDebug(
		Renderer3DWorldChunkFrame chunkFrame,
		RemasterShadowRoofCoverage roofCoverage) throws Exception {
		gl.glDisable(gl.GL_DEPTH_TEST);
		gl.glLineWidth(2.0f);
		for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunkFrame.getChunks()) {
			int casterCount = chunk.getShadowCasterCount();
			for (int index = 0; index < casterCount; index++) {
				Renderer3DWorldChunkFrame.ShadowCaster caster = chunk.getShadowCaster(index);
				if (!RemasterShadowClassifier.isEligibleCaster(caster)) {
					continue;
				}
				applyRemasterShadowCasterDebugColor(caster.getModelKind());
				drawRemasterShadowCasterOutline(caster, 0.0f);
			}
		}

		gl.glLineWidth(4.0f);
		gl.glColor4f(1.0f, 0.05f, 0.0f, 0.95f);
		for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunkFrame.getChunks()) {
			int casterCount = chunk.getShadowCasterCount();
			for (int index = 0; index < casterCount; index++) {
				Renderer3DWorldChunkFrame.ShadowCaster caster = chunk.getShadowCaster(index);
				if (RemasterShadowClassifier.isClippingCandidate(caster)) {
					drawRemasterShadowCasterTopEdge(caster, 16.0f);
				}
			}
		}

		gl.glLineWidth(3.0f);
		gl.glColor4f(0.25f, 0.45f, 1.0f, 0.95f);
		for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunkFrame.getChunks()) {
			int casterCount = chunk.getShadowCasterCount();
			for (int index = 0; index < casterCount; index++) {
				Renderer3DWorldChunkFrame.ShadowCaster caster = chunk.getShadowCaster(index);
				if (RemasterShadowClassifier.isEligibleCaster(caster)
					&& RemasterShadowClassifier.roofClassificationForCaster(roofCoverage, chunk, caster) > 0) {
					drawRemasterShadowCasterTopEdge(caster, 32.0f);
				}
			}
		}

		gl.glLineWidth(2.0f);
		gl.glColor4f(0.1f, 1.0f, 0.15f, 0.85f);
		for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunkFrame.getChunks()) {
			int casterCount = chunk.getShadowCasterCount();
			for (int index = 0; index < casterCount; index++) {
				Renderer3DWorldChunkFrame.ShadowCaster caster = chunk.getShadowCaster(index);
				if (RemasterShadowClassifier.isEligibleCaster(caster)
					&& RemasterShadowClassifier.roofClassificationForCaster(roofCoverage, chunk, caster) == 0) {
					drawRemasterShadowCasterTopEdge(caster, 48.0f);
				}
			}
		}
	}

	private void applyRemasterShadowReceiverDebugColor(int classification) throws Exception {
		if (classification > 0) {
			gl.glColor4f(1.0f, 0.62f, 0.0f, 0.32f);
		} else if (classification == 0) {
			gl.glColor4f(0.0f, 0.82f, 1.0f, 0.20f);
		} else {
			gl.glColor4f(1.0f, 1.0f, 1.0f, 0.18f);
		}
	}

	private void applyRemasterShadowCasterDebugColor(Renderer3DModelKind kind) throws Exception {
		if (kind == Renderer3DModelKind.WALL) {
			gl.glColor4f(1.0f, 0.88f, 0.05f, 0.95f);
		} else if (kind == Renderer3DModelKind.GAME_OBJECT) {
			gl.glColor4f(1.0f, 0.2f, 0.95f, 0.95f);
		} else if (kind == Renderer3DModelKind.WALL_OBJECT) {
			gl.glColor4f(0.0f, 1.0f, 0.85f, 0.95f);
		} else {
			gl.glColor4f(1.0f, 1.0f, 1.0f, 0.8f);
		}
	}

	private void drawRemasterShadowCasterOutline(
		Renderer3DWorldChunkFrame.ShadowCaster caster,
		float yOffset) throws Exception {
		float baseY = caster.getBaseY() + yOffset;
		float topY = caster.getBaseY() + caster.getHeight() + yOffset;
		gl.glBegin(gl.GL_LINES);
		gl.glVertex3f(caster.getBaseX0(), baseY, caster.getBaseZ0());
		gl.glVertex3f(caster.getBaseX1(), baseY, caster.getBaseZ1());
		gl.glVertex3f(caster.getBaseX0(), topY, caster.getBaseZ0());
		gl.glVertex3f(caster.getBaseX1(), topY, caster.getBaseZ1());
		gl.glVertex3f(caster.getBaseX0(), baseY, caster.getBaseZ0());
		gl.glVertex3f(caster.getBaseX0(), topY, caster.getBaseZ0());
		gl.glVertex3f(caster.getBaseX1(), baseY, caster.getBaseZ1());
		gl.glVertex3f(caster.getBaseX1(), topY, caster.getBaseZ1());
		gl.glEnd();
	}

	private void drawRemasterShadowCasterTopEdge(
		Renderer3DWorldChunkFrame.ShadowCaster caster,
		float yOffset) throws Exception {
		float y = caster.getBaseY() + caster.getHeight() + yOffset;
		gl.glBegin(gl.GL_LINES);
		gl.glVertex3f(caster.getBaseX0(), y, caster.getBaseZ0());
		gl.glVertex3f(caster.getBaseX1(), y, caster.getBaseZ1());
		gl.glEnd();
	}

	private void drawChunkVertex(Renderer3DWorldChunkFrame.ChunkMesh chunk, int vertex) throws Exception {
		int coord = vertex * POSITION_COMPONENT_COUNT;
		gl.glVertex3f(
			chunk.getVertexCoord(coord),
			chunk.getVertexCoord(coord + 1),
			chunk.getVertexCoord(coord + 2));
	}

	void drawRemasterTerrainShadowMask(Renderer3DFrame frame) throws Exception {
		if (!prepareRemasterTerrainShadowMask(frame)) {
			return;
		}
		RemasterTerrainShadowMask shadowMask = activeRemasterShadowMask;
		Renderer3DWorldChunkFrame chunkFrame = frame == null ? null : frame.getWorldChunkFrame();
		if (chunkFrame == null || chunkFrame.getChunkCount() <= 0 || shadowMask == null) {
			return;
		}
		long worldSignature = RemasterShadowMaskBuilder.remasterShadowWorldSignature(chunkFrame);
		RemasterShadowRoofCoverage roofCoverage = remasterShadowRoofCoverage(chunkFrame, worldSignature);

		loadWorldProjectionAndView(frame);
		gl.glUseProgram(0);
		gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0);
		gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, 0);
		gl.glDisableClientState(gl.GL_VERTEX_ARRAY);
		gl.glDisableClientState(gl.GL_COLOR_ARRAY);
		gl.glDisableClientState(gl.GL_TEXTURE_COORD_ARRAY);
		gl.glActiveTexture(gl.GL_TEXTURE0);
		gl.glEnable(gl.GL_TEXTURE_2D);
		gl.glBindTexture(gl.GL_TEXTURE_2D, remasterShadowMaskTextureId);
		gl.glDisable(gl.GL_ALPHA_TEST);
		gl.glDisable(gl.GL_FOG);
		gl.glDisable(gl.GL_CULL_FACE);
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		gl.glEnable(gl.GL_DEPTH_TEST);
		gl.glEnable(gl.GL_POLYGON_OFFSET_FILL);
		gl.glPolygonOffset(-4.0f, -4.0f);
		gl.glDepthMask(false);
		try {
			gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
			gl.glBegin(gl.GL_TRIANGLES);
			for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunkFrame.getChunks()) {
				drawRemasterTerrainShadowMaskChunk(roofCoverage, shadowMask, chunk);
			}
			gl.glEnd();
		} finally {
			gl.glDisable(gl.GL_POLYGON_OFFSET_FILL);
			gl.glDepthMask(true);
			gl.glDisable(gl.GL_DEPTH_TEST);
			gl.glDisable(gl.GL_BLEND);
			gl.glDisable(gl.GL_ALPHA_TEST);
			gl.glDisable(gl.GL_FOG);
			gl.glDisable(gl.GL_CULL_FACE);
			gl.glBindTexture(gl.GL_TEXTURE_2D, 0);
			gl.glEnable(gl.GL_TEXTURE_2D);
			gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
		}
	}

	boolean prepareRemasterTerrainShadowMask(Renderer3DFrame frame) throws Exception {
		activeRemasterShadowMask = null;
		Renderer3DWorldChunkFrame chunkFrame = frame == null ? null : frame.getWorldChunkFrame();
		if (chunkFrame == null || chunkFrame.getChunkCount() <= 0) {
			return false;
		}
		long worldSignature = RemasterShadowMaskBuilder.remasterShadowWorldSignature(chunkFrame);
		RemasterShadowRoofCoverage roofCoverage = remasterShadowRoofCoverage(chunkFrame, worldSignature);
		long buildStart = RenderTelemetry.now();
		RemasterShadowMaskBuild shadowBuild =
			remasterShadowMaskBuilder.build(chunkFrame, roofCoverage, worldSignature);
		long buildNanos = RenderTelemetry.elapsedSince(buildStart);
		if (shadowBuild == null || shadowBuild.mask == null) {
			return false;
		}
		RemasterTerrainShadowMask shadowMask = shadowBuild.mask;
		long uploadStart = RenderTelemetry.now();
		uploadRemasterTerrainShadowMask(shadowMask);
		long uploadNanos = RenderTelemetry.elapsedSince(uploadStart);
		RenderTelemetry.recordOpenGLRemasterShadowMask(
			shadowMask.width,
			shadowMask.height,
			shadowMask.visiblePixels,
			shadowBuild.cacheHit,
			shadowBuild.rebuild,
			remasterShadowMaskLastUpload,
			remasterShadowMaskLastUploadSkip,
			shadowBuild.stripCasterCount,
			shadowBuild.softSceneryCasterCount,
			shadowBuild.contactCasterCount,
			shadowBuild.reason,
			buildNanos,
			uploadNanos);
		activeRemasterShadowMask = shadowMask;
		return true;
	}

	void clearPreparedRemasterTerrainShadowMask() {
		activeRemasterShadowMask = null;
	}

	private boolean prepareRemasterGlowMask(Renderer3DFrame frame) throws Exception {
		activeRemasterGlowMask = null;
		Renderer3DWorldChunkFrame chunkFrame = frame == null ? null : frame.getWorldChunkFrame();
		if (chunkFrame == null || chunkFrame.getChunkCount() <= 0) {
			return false;
		}
		RemasterGlowMaskBuild glowBuild = remasterGlowMaskBuilder.build(chunkFrame);
		if (glowBuild == null || glowBuild.mask == null) {
			return false;
		}
		RemasterGlowMask glowMask = glowBuild.mask;
		uploadRemasterGlowMask(glowMask);
		activeRemasterGlowMask = glowMask;
		return true;
	}

	private RemasterShadowRoofCoverage remasterShadowRoofCoverage(
		Renderer3DWorldChunkFrame chunkFrame,
		long worldSignature) {
		if (cachedRemasterShadowRoofCoverageKnown
			&& cachedRemasterShadowRoofCoverageSignature == worldSignature
			&& cachedRemasterShadowRoofCoverage != null) {
			return cachedRemasterShadowRoofCoverage;
		}
		cachedRemasterShadowRoofCoverage = RemasterShadowRoofCoverage.from(chunkFrame);
		cachedRemasterShadowRoofCoverageSignature = worldSignature;
		cachedRemasterShadowRoofCoverageKnown = true;
		return cachedRemasterShadowRoofCoverage;
	}

	private void drawRemasterTerrainShadowMaskChunk(
		RemasterShadowRoofCoverage roofCoverage,
		RemasterTerrainShadowMask shadowMask,
		Renderer3DWorldChunkFrame.ChunkMesh chunk) throws Exception {
		int triangleCount = Math.min(chunk.getTriangleCount(), chunk.getIndexCount() / 3);
		for (int triangle = 0; triangle < triangleCount; triangle++) {
			if (chunk.getTriangleModelKind(triangle) != Renderer3DModelKind.TERRAIN) {
				continue;
			}
			if (RemasterShadowClassifier.roofClassificationForTriangle(roofCoverage, chunk, triangle) != 0) {
				continue;
			}
			int indexBase = triangle * 3;
			int vertex0 = chunk.getIndex(indexBase);
			int vertex1 = chunk.getIndex(indexBase + 1);
			int vertex2 = chunk.getIndex(indexBase + 2);
			drawRemasterTerrainShadowMaskVertex(shadowMask, chunk, vertex0);
			drawRemasterTerrainShadowMaskVertex(shadowMask, chunk, vertex1);
			drawRemasterTerrainShadowMaskVertex(shadowMask, chunk, vertex2);
		}
	}

	private void drawRemasterTerrainShadowMaskVertex(
		RemasterTerrainShadowMask shadowMask,
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		int vertex) throws Exception {
		int coord = vertex * POSITION_COMPONENT_COUNT;
		float x = chunk.getVertexCoord(coord);
		float z = chunk.getVertexCoord(coord + 2);
		gl.glTexCoord2f(shadowMask.u(x), shadowMask.v(z));
		gl.glVertex3f(x, chunk.getVertexCoord(coord + 1), z);
	}



	private void uploadRemasterTerrainShadowMask(RemasterTerrainShadowMask shadowMask) throws Exception {
		if (remasterShadowMaskTextureId == 0) {
			remasterShadowMaskTextureId = gl.glGenTextures();
			gl.glBindTexture(gl.GL_TEXTURE_2D, remasterShadowMaskTextureId);
			gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
			gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
			gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_WRAP_S, gl.GL_CLAMP_TO_EDGE);
			gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_WRAP_T, gl.GL_CLAMP_TO_EDGE);
		} else {
			gl.glBindTexture(gl.GL_TEXTURE_2D, remasterShadowMaskTextureId);
		}
		if (remasterShadowMaskUploadedSignature == shadowMask.signature
			&& remasterShadowMaskTextureWidth == shadowMask.width
			&& remasterShadowMaskTextureHeight == shadowMask.height) {
			remasterShadowMaskLastUpload = false;
			remasterShadowMaskLastUploadSkip = true;
			return;
		}
		remasterShadowMaskLastUpload = true;
		remasterShadowMaskLastUploadSkip = false;
		if (remasterShadowMaskTextureWidth != shadowMask.width
			|| remasterShadowMaskTextureHeight != shadowMask.height) {
			gl.glTexImage2D(
				gl.GL_TEXTURE_2D,
				0,
				gl.GL_RGBA,
				shadowMask.width,
				shadowMask.height,
				0,
				gl.GL_RGBA,
				gl.GL_UNSIGNED_BYTE,
				shadowMask.pixels());
			remasterShadowMaskTextureWidth = shadowMask.width;
			remasterShadowMaskTextureHeight = shadowMask.height;
		} else {
			gl.glTexSubImage2D(
				gl.GL_TEXTURE_2D,
				0,
				0,
				0,
				shadowMask.width,
				shadowMask.height,
				gl.GL_RGBA,
				gl.GL_UNSIGNED_BYTE,
				shadowMask.pixels());
		}
		remasterShadowMaskUploadedSignature = shadowMask.signature;
	}

	private void uploadRemasterGlowMask(RemasterGlowMask glowMask) throws Exception {
		if (remasterGlowMaskTextureId == 0) {
			remasterGlowMaskTextureId = gl.glGenTextures();
			gl.glBindTexture(gl.GL_TEXTURE_2D, remasterGlowMaskTextureId);
			gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
			gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
			gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_WRAP_S, gl.GL_CLAMP_TO_EDGE);
			gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_WRAP_T, gl.GL_CLAMP_TO_EDGE);
		} else {
			gl.glBindTexture(gl.GL_TEXTURE_2D, remasterGlowMaskTextureId);
		}
		if (remasterGlowMaskUploadedSignature == glowMask.signature
			&& remasterGlowMaskTextureWidth == glowMask.width
			&& remasterGlowMaskTextureHeight == glowMask.height) {
			return;
		}
		if (remasterGlowMaskTextureWidth != glowMask.width
			|| remasterGlowMaskTextureHeight != glowMask.height) {
			gl.glTexImage2D(
				gl.GL_TEXTURE_2D,
				0,
				gl.GL_RGBA,
				glowMask.width,
				glowMask.height,
				0,
				gl.GL_RGBA,
				gl.GL_UNSIGNED_BYTE,
				glowMask.pixels());
			remasterGlowMaskTextureWidth = glowMask.width;
			remasterGlowMaskTextureHeight = glowMask.height;
		} else {
			gl.glTexSubImage2D(
				gl.GL_TEXTURE_2D,
				0,
				0,
				0,
				glowMask.width,
				glowMask.height,
				gl.GL_RGBA,
				gl.GL_UNSIGNED_BYTE,
				glowMask.pixels());
		}
		remasterGlowMaskUploadedSignature = glowMask.signature;
	}



	ResidentChunkReadiness inspectDrawableResidentStaticWorld(
		Renderer3DFrame frame,
		Renderer3DWorldChunkFrame chunkFrame,
		boolean textured) {
		if (chunkFrame == null || chunkFrame.getChunkCount() <= 0 || residentChunks.isEmpty()) {
			return ResidentChunkReadiness.notRequested("no-resident-chunks");
		}
		long shadowProofSignature = currentShadowProofSignature(chunkFrame);
		boolean matchedBuffer = false;
		int requiredResidentChunks = 0;
		int matchedResidentChunks = 0;
		int drawableTerrainBatches = 0;
		int drawableWallBatches = 0;
		int drawableRoofBatches = 0;
		boolean drawOffsetSupported = useResidentChunkShader(textured);
		for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunkFrame.getChunks()) {
			if (chunk.getVertexCount() <= 0 || chunk.getIndexCount() <= 0) {
				continue;
			}
			requiredResidentChunks++;
			WorldChunkBufferKey key = WorldChunkBufferKey.from(
				chunk,
				drawOffsetSupported);
			WorldChunkBuffer buffer = residentChunks.get(key);
			if (buffer == null || !buffer.matches(
				chunkBufferSignature(chunk, drawOffsetSupported),
				chunk.getVertexCount(),
				chunk.getIndexCount(),
				chunk.getTriangleCount(),
				textured,
				currentBrightnessBits(),
				currentFogModeBits(),
				currentLightingModeBits(),
				currentGeometryModeBits(),
				replacementCompositeEnabled,
				shadowProofSignature)) {
				continue;
			}
			matchedBuffer = true;
			matchedResidentChunks++;
			for (WorldChunkMaterialBatch batch : buffer.materialBatches) {
				if (batch.indexCount <= 0 || !shouldDrawChunkModelKind(frame, chunk, batch.kind)) {
					continue;
				}
				if (!isDrawableResidentStaticBatch(frame, batch, textured)) {
					continue;
				}
				if (batch.kind == Renderer3DModelKind.TERRAIN) {
					drawableTerrainBatches++;
				} else if (batch.kind == Renderer3DModelKind.WALL) {
					drawableWallBatches++;
				} else if (batch.kind == Renderer3DModelKind.ROOF) {
					drawableRoofBatches++;
				}
			}
		}
		if (!matchedBuffer) {
			return ResidentChunkReadiness.notRequested("no-matching-buffers");
		}
		if (matchedResidentChunks < requiredResidentChunks) {
			return new ResidentChunkReadiness(
				false,
				"resident-loading " + matchedResidentChunks + "/" + requiredResidentChunks,
				drawableTerrainBatches,
				drawableWallBatches,
				drawableRoofBatches);
		}
		if (drawableTerrainBatches <= 0) {
			return new ResidentChunkReadiness(
				false,
				"no-drawable-terrain",
				drawableTerrainBatches,
				drawableWallBatches,
				drawableRoofBatches);
		}
		return new ResidentChunkReadiness(
			true,
			"active",
			drawableTerrainBatches,
			drawableWallBatches,
			drawableRoofBatches);
	}

	private boolean isDrawableResidentStaticBatch(
		Renderer3DFrame frame,
		WorldChunkMaterialBatch batch,
		boolean textured) {
		return !textured
			|| textureRegionForBatch(frame, batch) != null
			|| fallbackColorForBatch(frame, batch) != NO_FALLBACK_COLOR;
	}

	private void drawChunkDiagnosticLayers(
		Renderer3DFrame frame,
		Renderer3DWorldChunkFrame chunkFrame,
		boolean textured,
		boolean transparentPass,
		WorldChunkDrawAccumulator accumulator,
		float[] batchCullViewMatrix,
		boolean shaderActive,
		FloatBuffer residentWorldToClipMatrix,
		FloatBuffer residentWorldViewMatrix) throws Exception {
		for (Renderer3DModelKind kind : WORLD_CHUNK_KIND_DRAW_ORDER) {
			drawChunkDiagnosticPass(
				frame,
				chunkFrame,
				textured,
				transparentPass,
				kind,
				accumulator,
				batchCullViewMatrix,
				shaderActive,
				residentWorldToClipMatrix,
				residentWorldViewMatrix);
		}
	}

	private boolean shouldDrawProjectedShadowProof() {
		RendererLightingSettings.Mode lightingMode = RendererLightingSettings.getMode();
		return shadowProofEnabled && lightingMode == RendererLightingSettings.Mode.DIRECTIONAL;
	}

	private long currentShadowProofSignature(Renderer3DWorldChunkFrame chunkFrame) {
		if (!shouldDrawProjectedShadowProof()) {
			return 0L;
		}
		return shadowProofCasterSignature(buildProjectedShadowProofCasters(chunkFrame));
	}

	private long shadowProofCasterSignature(List<ShadowProofCaster> casters) {
		long hash = 0xcbf29ce484222325L;
		if (casters == null || casters.isEmpty()) {
			return hash;
		}
		for (ShadowProofCaster caster : casters) {
			hash = shadowProofMix(hash, (int) caster.key);
			hash = shadowProofMix(hash, (int) (caster.key >>> 32));
			hash = shadowProofMix(hash, Float.floatToIntBits(caster.baseX0));
			hash = shadowProofMix(hash, Float.floatToIntBits(caster.baseZ0));
			hash = shadowProofMix(hash, Float.floatToIntBits(caster.baseX1));
			hash = shadowProofMix(hash, Float.floatToIntBits(caster.baseZ1));
			hash = shadowProofMix(hash, Float.floatToIntBits(caster.centerX));
			hash = shadowProofMix(hash, Float.floatToIntBits(caster.centerZ));
			hash = shadowProofMix(hash, Float.floatToIntBits(caster.fallbackY));
			hash = shadowProofMix(hash, Float.floatToIntBits(caster.height));
			hash = shadowProofMix(hash, Float.floatToIntBits(caster.length));
			hash = shadowProofMix(hash, Float.floatToIntBits(caster.halfWidth));
			hash = shadowProofMix(hash, Float.floatToIntBits(caster.opacity));
		}
		return hash;
	}

	private static long shadowProofMix(long hash, int value) {
		hash ^= value & 0xffffffffL;
		return hash * 0x100000001b3L;
	}



	private List<ShadowProofCaster> buildProjectedShadowProofCasters(Renderer3DWorldChunkFrame chunkFrame) {
		if (chunkFrame == null || chunkFrame.getChunkCount() <= 0) {
			return Collections.emptyList();
		}
		Map<Long, ShadowProofCaster> castersByKey = new LinkedHashMap<Long, ShadowProofCaster>();
		for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunkFrame.getChunks()) {
			addProjectedShadowProofCasters(castersByKey, chunk);
		}
		return new ArrayList<ShadowProofCaster>(castersByKey.values());
	}

	private List<ShadowProofCaster> buildProjectedShadowProofCasters(Renderer3DWorldChunkFrame.ChunkMesh chunk) {
		Map<Long, ShadowProofCaster> castersByKey = new LinkedHashMap<Long, ShadowProofCaster>();
		addProjectedShadowProofCasters(castersByKey, chunk);
		return new ArrayList<ShadowProofCaster>(castersByKey.values());
	}

	private void addProjectedShadowProofCasters(
		Map<Long, ShadowProofCaster> castersByKey,
		Renderer3DWorldChunkFrame.ChunkMesh chunk) {
		if (castersByKey == null || chunk == null) {
			return;
		}
		if (chunk.getShadowCasterCount() > 0) {
			for (int index = 0; index < chunk.getShadowCasterCount(); index++) {
				Renderer3DWorldChunkFrame.ShadowCaster source = chunk.getShadowCaster(index);
				if (!shouldCastProjectedShadowProof(source.getModelKind())) {
					continue;
				}
				ShadowProofCaster caster = ShadowProofCaster.from(source);
				if (caster == null) {
					continue;
				}
				addProjectedShadowProofCaster(castersByKey, caster);
			}
			return;
		}

		int triangleCount = Math.min(chunk.getTriangleCount(), chunk.getIndexCount() / 3);
		for (int triangle = 0; triangle < triangleCount; triangle++) {
			if (!shouldCastProjectedShadowProof(chunk.getTriangleModelKind(triangle))) {
				continue;
			}
			ShadowProofCaster caster = ShadowProofCaster.from(chunk, triangle);
			if (caster == null) {
				continue;
			}
			addProjectedShadowProofCaster(castersByKey, caster);
		}
	}

	private void addProjectedShadowProofCaster(
		Map<Long, ShadowProofCaster> castersByKey,
		ShadowProofCaster caster) {
		ShadowProofCaster current = castersByKey.get(caster.key);
		if (current == null || caster.height > current.height) {
			castersByKey.put(caster.key, caster);
		}
	}

	private boolean shouldCastProjectedShadowProof(Renderer3DModelKind kind) {
		if (kind == null) {
			return false;
		}
		return kind == Renderer3DModelKind.WALL
			|| kind == Renderer3DModelKind.GAME_OBJECT
			|| kind == Renderer3DModelKind.WALL_OBJECT;
	}

	private float projectedShadowProofAlpha(
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		int triangle,
		List<ShadowProofCaster> shadowProofCasters) {
		int sourceIndex = triangle * 3;
		float x = 0.0f;
		float z = 0.0f;
		for (int index = 0; index < 3; index++) {
			int vertex = chunk.getIndex(sourceIndex + index);
			int coord = vertex * POSITION_COMPONENT_COUNT;
			x += chunk.getVertexCoord(coord);
			z += chunk.getVertexCoord(coord + 2);
		}
		x /= 3.0f;
		z /= 3.0f;
		float baseAlpha = SHADOW_PROOF_DIRECTIONAL_ALPHA;
		float alpha = 0.0f;
		for (ShadowProofCaster caster : shadowProofCasters) {
			alpha += caster.alphaAt(x, z, baseAlpha);
			if (alpha >= SHADOW_PROOF_MAX_ALPHA) {
				return SHADOW_PROOF_MAX_ALPHA;
			}
		}
		return alpha;
	}

	private static final class ShadowProofCaster {
		private final long key;
		private final float baseX0;
		private final float baseZ0;
		private final float baseX1;
		private final float baseZ1;
		private final float centerX;
		private final float centerZ;
		private final float fallbackY;
		private final float height;
		private final float length;
		private final float halfWidth;
		private final float edgeX;
		private final float edgeZ;
		private final float edgeLength;
		private final float directionX;
		private final float directionZ;
		private final float opacity;

		private ShadowProofCaster(
			long key,
			float baseX0,
			float baseZ0,
			float baseX1,
			float baseZ1,
			float fallbackY,
			float height,
			float length,
			float halfWidth,
			float opacity) {
			float edgeDx = baseX1 - baseX0;
			float edgeDz = baseZ1 - baseZ0;
			float edgeLength = (float) Math.sqrt(edgeDx * edgeDx + edgeDz * edgeDz);
			float directionLength = (float) Math.sqrt(
				SHADOW_PROOF_DIRECTION_X * SHADOW_PROOF_DIRECTION_X
					+ SHADOW_PROOF_DIRECTION_Z * SHADOW_PROOF_DIRECTION_Z);
			this.key = key;
			this.baseX0 = baseX0;
			this.baseZ0 = baseZ0;
			this.baseX1 = baseX1;
			this.baseZ1 = baseZ1;
			this.centerX = (baseX0 + baseX1) * 0.5f;
			this.centerZ = (baseZ0 + baseZ1) * 0.5f;
			this.fallbackY = fallbackY;
			this.height = height;
			this.length = length;
			this.halfWidth = halfWidth;
			this.edgeLength = edgeLength;
			this.edgeX = edgeDx / Math.max(0.0001f, edgeLength);
			this.edgeZ = edgeDz / Math.max(0.0001f, edgeLength);
			this.directionX = SHADOW_PROOF_DIRECTION_X / Math.max(0.0001f, directionLength);
			this.directionZ = SHADOW_PROOF_DIRECTION_Z / Math.max(0.0001f, directionLength);
			this.opacity = clampStatic(opacity, 0.0f, 1.0f);
		}

		private float alphaAt(float x, float z, float baseAlpha) {
			float determinant = edgeX * directionZ - edgeZ * directionX;
			if (edgeLength > 0.0001f && Math.abs(determinant) > 0.08f) {
				float px = x - baseX0;
				float pz = z - baseZ0;
				float edgeAlong = (px * directionZ - pz * directionX) / determinant;
				float shadowAlong = (edgeX * pz - edgeZ * px) / determinant;
				if (shadowAlong < 0.0f || shadowAlong > length) {
					return 0.0f;
				}
				if (edgeAlong < -halfWidth || edgeAlong > edgeLength + halfWidth) {
					return 0.0f;
				}
				float sideFade = Math.min(
					(edgeAlong + halfWidth) / Math.max(16.0f, halfWidth),
					(edgeLength + halfWidth - edgeAlong) / Math.max(16.0f, halfWidth));
				float endFade = (length - shadowAlong) / Math.max(64.0f, length * 0.22f);
				return baseAlpha * opacity * clampStatic(Math.min(sideFade, endFade), 0.0f, 1.0f);
			}
			return alphaAtCenterFallback(x, z, baseAlpha);
		}

		private float alphaAtCenterFallback(float x, float z, float baseAlpha) {
			float dx = x - centerX;
			float dz = z - centerZ;
			float along = dx * directionX + dz * directionZ;
			if (along < 0.0f || along > length) {
				return 0.0f;
			}
			float across = Math.abs(dx * -directionZ + dz * directionX);
			if (across > halfWidth) {
				return 0.0f;
			}
			float sideFade = (halfWidth - across) / Math.max(16.0f, halfWidth * 0.25f);
			float endFade = (length - along) / Math.max(64.0f, length * 0.22f);
			return baseAlpha * opacity * clampStatic(Math.min(sideFade, endFade), 0.0f, 1.0f);
		}

		private static ShadowProofCaster from(Renderer3DWorldChunkFrame.ChunkMesh chunk, int triangle) {
			int sourceIndex = triangle * 3;
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int minY = Integer.MAX_VALUE;
			int maxY = Integer.MIN_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (int index = 0; index < 3; index++) {
				int vertex = chunk.getIndex(sourceIndex + index);
				int coord = vertex * POSITION_COMPONENT_COUNT;
				int x = chunk.getVertexCoord(coord);
				int y = chunk.getVertexCoord(coord + 1);
				int z = chunk.getVertexCoord(coord + 2);
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
				minZ = Math.min(minZ, z);
				maxZ = Math.max(maxZ, z);
			}
			int spanX = maxX - minX;
			int spanZ = maxZ - minZ;
			if (spanX > SHADOW_PROOF_DIAGONAL_MIN_SPAN && spanZ > SHADOW_PROOF_DIAGONAL_MIN_SPAN) {
				return null;
			}
			float height = Math.max(0.0f, maxY - minY);
			float length = clampStatic(
				SHADOW_PROOF_MIN_LENGTH + height * 1.25f,
				SHADOW_PROOF_MIN_LENGTH,
				SHADOW_PROOF_MAX_LENGTH);
			float width = Math.max(spanX, spanZ);
			float centerX = (minX + maxX) * 0.5f;
			float centerZ = (minZ + maxZ) * 0.5f;
			return new ShadowProofCaster(
				casterKey(spanX >= spanZ, centerX, centerZ, width),
				spanX >= spanZ ? minX : centerX,
				spanX >= spanZ ? centerZ : minZ,
				spanX >= spanZ ? maxX : centerX,
				spanX >= spanZ ? centerZ : maxZ,
				minY,
				height,
				length,
				Math.max(SHADOW_PROOF_MIN_WIDTH, width * 0.55f),
				0.75f);
		}

		private static ShadowProofCaster from(Renderer3DWorldChunkFrame.ShadowCaster source) {
			int spanX = Math.abs(source.getBaseX1() - source.getBaseX0());
			int spanZ = Math.abs(source.getBaseZ1() - source.getBaseZ0());
			if (spanX > SHADOW_PROOF_DIAGONAL_MIN_SPAN && spanZ > SHADOW_PROOF_DIAGONAL_MIN_SPAN) {
				return null;
			}
			float height = Math.max(0.0f, source.getHeight());
			float length = clampStatic(
				SHADOW_PROOF_MIN_LENGTH + height * 3.0f,
				SHADOW_PROOF_MIN_LENGTH,
				SHADOW_PROOF_MAX_LENGTH);
			float width = Math.max(source.getWidth(), Math.max(spanX, spanZ));
			float centerX = (source.getBaseX0() + source.getBaseX1()) * 0.5f;
			float centerZ = (source.getBaseZ0() + source.getBaseZ1()) * 0.5f;
			float baseX0 = source.getBaseX0();
			float baseZ0 = source.getBaseZ0();
			float baseX1 = source.getBaseX1();
			float baseZ1 = source.getBaseZ1();
			if (source.getModelKind() == Renderer3DModelKind.GAME_OBJECT) {
				float directionLength = (float) Math.sqrt(
					SHADOW_PROOF_DIRECTION_X * SHADOW_PROOF_DIRECTION_X
						+ SHADOW_PROOF_DIRECTION_Z * SHADOW_PROOF_DIRECTION_Z);
				float normalX = -SHADOW_PROOF_DIRECTION_Z / Math.max(0.0001f, directionLength);
				float normalZ = SHADOW_PROOF_DIRECTION_X / Math.max(0.0001f, directionLength);
				float halfWidth = Math.max(SHADOW_PROOF_MIN_WIDTH, width * 0.5f);
				baseX0 = centerX - normalX * halfWidth;
				baseZ0 = centerZ - normalZ * halfWidth;
				baseX1 = centerX + normalX * halfWidth;
				baseZ1 = centerZ + normalZ * halfWidth;
			}
			return new ShadowProofCaster(
				casterKey(spanX >= spanZ, centerX, centerZ, width),
				baseX0,
				baseZ0,
				baseX1,
				baseZ1,
				source.getBaseY(),
				height,
				length,
				Math.max(SHADOW_PROOF_MIN_WIDTH, width * 0.55f),
				source.getOpacity() / 255.0f);
		}

		private static long casterKey(boolean xMajor, float centerX, float centerZ, float width) {
			float along = xMajor ? centerX : centerZ;
			float across = xMajor ? centerZ : centerX;
			long result = xMajor ? 1L : 2L;
			result = result * 31L + Math.round(along / 128.0f);
			result = result * 31L + Math.round(across / 64.0f);
			result = result * 31L + Math.round(width / 128.0f);
			return result;
		}
	}

	private static float clampStatic(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private void drawChunkDiagnosticPass(
		Renderer3DFrame frame,
		Renderer3DWorldChunkFrame chunkFrame,
		boolean textured,
		boolean transparentPass,
		Renderer3DModelKind modelKind,
		WorldChunkDrawAccumulator accumulator,
		float[] batchCullViewMatrix,
		boolean shaderActive,
		FloatBuffer residentWorldToClipMatrix,
		FloatBuffer residentWorldViewMatrix) throws Exception {
		boolean wallDepthPriority = modelKind == Renderer3DModelKind.WALL;
		if (wallDepthPriority) {
			enableWallDepthPriority();
		}
		boolean residentObjectCull = shouldCullResidentObjectBatches(modelKind);
		if (residentObjectCull) {
			gl.glEnable(gl.GL_CULL_FACE);
			gl.glCullFace(gl.GL_BACK);
		}
		long shadowProofSignature = currentShadowProofSignature(chunkFrame);
		try {
			for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunkFrame.getChunks()) {
				if (!shouldDrawChunkModelKind(frame, chunk, modelKind)) {
					continue;
				}
				WorldChunkBufferKey key = WorldChunkBufferKey.from(
					chunk,
					shaderActive);
				WorldChunkBuffer buffer = residentChunks.get(key);
				if (buffer == null || !buffer.matches(
					chunkBufferSignature(chunk, shaderActive),
					chunk.getVertexCount(),
					chunk.getIndexCount(),
					chunk.getTriangleCount(),
					textured,
					currentBrightnessBits(),
					currentFogModeBits(),
					currentLightingModeBits(),
					currentGeometryModeBits(),
					replacementCompositeEnabled,
					shadowProofSignature)) {
					continue;
				}
				int drawOffsetX = shaderActive
					? chunk.getVertexOffsetX() - buffer.uploadedVertexOffsetX
					: 0;
				int drawOffsetZ = shaderActive
					? chunk.getVertexOffsetZ() - buffer.uploadedVertexOffsetZ
					: 0;
				if (shaderActive) {
					residentChunkShader.setChunkOffsets(
						drawOffsetX,
						drawOffsetZ,
						chunk.getLogicalWorldOffsetX(),
						chunk.getLogicalWorldOffsetZ());
				}
				accumulator.recordConsideredChunk(key);
				gl.glBindBuffer(gl.GL_ARRAY_BUFFER, buffer.vertexBufferId);
				gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, buffer.materialIndexBufferId);
				if (!shaderActive) {
					gl.glVertexPointer(POSITION_COMPONENT_COUNT, gl.GL_FLOAT, STRIDE_BYTES, 0L);
					bindChunkVertexAttributes(textured);
				}
				Boolean shaderTextureEnabled = null;
				int chunkDrawnTriangles = 0;
				WorldChunkDrawRange pendingRange = null;
				for (WorldChunkMaterialBatch batch : buffer.materialBatches) {
					if (batch.indexCount <= 0) {
						continue;
					}
					if (batch.kind != modelKind) {
						continue;
					}
					if (textured && isTransparentChunkBatch(frame, batch) != transparentPass) {
						continue;
					}
					accumulator.recordConsideredBatch();
					if (isFrustumCulledChunkBatch(
							frame,
							batch,
							batchCullViewMatrix,
							drawOffsetX,
							drawOffsetZ)
						|| isFogCulledChunkBatch(
							frame,
							batch,
							batchCullViewMatrix,
							drawOffsetX,
							drawOffsetZ)) {
						accumulator.recordCulledBatch();
						accumulator.recordSkippedBatch(batch.indexCount / 3);
						continue;
					}
					WorldChunkBatchDrawState drawState = chunkBatchDrawState(frame, batch, textured);
					if (drawState.bindResult == WorldChunkBatchBindResult.SKIPPED) {
						accumulator.recordSkippedBatch(batch.indexCount / 3);
						continue;
					}
					if (pendingRange != null && !pendingRange.canAppend(batch, drawState)) {
						flushChunkDrawRange(pendingRange, accumulator);
						pendingRange = null;
					}
					if (pendingRange == null) {
						bindChunkBatchDrawState(drawState, accumulator, shaderActive);
						if (shaderActive) {
							if (shaderTextureEnabled == null
								|| shaderTextureEnabled.booleanValue() != drawState.textureEnabled) {
								residentChunkShader.setTextureEnabled(drawState.textureEnabled);
								bindResidentChunkShaderAttributes(drawState.textureEnabled);
								shaderTextureEnabled = Boolean.valueOf(drawState.textureEnabled);
							}
						}
						pendingRange = new WorldChunkDrawRange(batch, drawState);
					} else {
						pendingRange.append(batch);
					}
					accumulator.recordDrawnBatch();
					int batchTriangles = batch.indexCount / 3;
					chunkDrawnTriangles += batchTriangles;
					accumulator.recordBatch(batch.kind, batchTriangles);
					if (drawState.bindResult == WorldChunkBatchBindResult.FLAT_FALLBACK) {
						accumulator.recordFallbackBatch(batchTriangles);
					}
				}
				flushChunkDrawRange(pendingRange, accumulator);
				if (chunkDrawnTriangles > 0) {
					accumulator.recordChunk(key);
				}
			}
		} finally {
			if (shaderActive) {
				residentChunkShader.setChunkOffsets(
					0.0f, 0.0f, 0.0f, 0.0f);
			}
			if (residentObjectCull) {
				gl.glDisable(gl.GL_CULL_FACE);
			}
			if (wallDepthPriority) {
				disableWallDepthPriority();
			}
		}
	}

	private boolean shouldCullResidentObjectBatches(Renderer3DModelKind modelKind) {
		return residentObjectsEnabled
			&& (modelKind == Renderer3DModelKind.GAME_OBJECT
				|| modelKind == Renderer3DModelKind.WALL_OBJECT);
	}

	private void enableWallDepthPriority() throws Exception {
		gl.glEnable(gl.GL_POLYGON_OFFSET_FILL);
		gl.glPolygonOffset(WALL_DEPTH_PRIORITY_FACTOR, WALL_DEPTH_PRIORITY_UNITS);
	}

	private void disableWallDepthPriority() throws Exception {
		gl.glPolygonOffset(0.0f, 0.0f);
		gl.glDisable(gl.GL_POLYGON_OFFSET_FILL);
	}

	private boolean shouldDrawChunkModelKind(
		Renderer3DFrame frame,
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		Renderer3DModelKind modelKind) {
		if (replacementCompositeEnabled
			&& (modelKind == Renderer3DModelKind.GAME_OBJECT
				|| modelKind == Renderer3DModelKind.WALL_OBJECT)
			&& !residentObjectsEnabled) {
			return false;
		}
		return frame == null
			|| chunk == null
			|| frame.isWorldChunkModelKindVisible(modelKind, chunk.getPlane());
	}

	private void bindChunkVertexAttributes(boolean textured) throws Exception {
		if (textured) {
			gl.glEnableClientState(gl.GL_COLOR_ARRAY);
			gl.glColorPointer(
				TEXTURE_LIGHT_COMPONENT_COUNT,
				gl.GL_FLOAT,
				STRIDE_BYTES,
				TEXTURE_LIGHT_OFFSET_BYTES);
			gl.glEnableClientState(gl.GL_TEXTURE_COORD_ARRAY);
			gl.glTexCoordPointer(
				TEXTURE_COORD_COMPONENT_COUNT,
				gl.GL_FLOAT,
				STRIDE_BYTES,
				TEXTURE_COORD_OFFSET_BYTES);
		} else {
			gl.glEnableClientState(gl.GL_COLOR_ARRAY);
			gl.glColorPointer(COLOR_COMPONENT_COUNT, gl.GL_FLOAT, STRIDE_BYTES, COLOR_OFFSET_BYTES);
		}
	}

	private boolean useResidentChunkShader(boolean textured) {
		return textured && texturedShaderEnabled && residentChunkShader != null;
	}

	private void bindResidentChunkShaderPass(
		Renderer3DFrame frame,
		FloatBuffer residentWorldToClipMatrix,
		FloatBuffer residentWorldViewMatrix) throws Exception {
		boolean remasterLightingEnabled =
			remasterLightingShaderEnabled
				&& RendererLightingSettings.getMode() == RendererLightingSettings.Mode.DIRECTIONAL;
		boolean rawMaterialMode = rawMaterialShaderEnabled;
		if (remasterLightingEnabled) {
			prepareRemasterGlowMask(frame);
		} else {
			activeRemasterGlowMask = null;
		}
		residentChunkShader.useResidentChunk(
			residentWorldToClipMatrix,
			residentWorldViewMatrix,
			true,
			rawMaterialMode,
			remasterLightingEnabled,
			frame,
			activeRemasterShadowMask,
			activeRemasterGlowMask);
		bindPreparedRemasterShadowMask();
		bindPreparedRemasterGlowMask();
	}

	private void bindResidentChunkShaderAttributes(boolean textureEnabled) throws Exception {
		residentChunkShader.bindWorldParityAttributes(
			POSITION_COMPONENT_COUNT,
			TEXTURE_COORD_COMPONENT_COUNT,
			textureEnabled ? TEXTURE_LIGHT_COMPONENT_COUNT : COLOR_COMPONENT_COUNT,
			RAW_MATERIAL_COLOR_COMPONENT_COUNT,
			BASE_LEGACY_LIGHT_COMPONENT_COUNT,
			NORMAL_COMPONENT_COUNT,
			MODEL_KIND_COMPONENT_COUNT,
			MATERIAL_FAMILY_COMPONENT_COUNT,
			TERRAIN_VARIATION_MASK_COMPONENT_COUNT,
			TERRAIN_BLEND_COLOR_COMPONENT_COUNT,
			TERRAIN_BLEND_STRENGTH_COMPONENT_COUNT,
			STRIDE_BYTES,
			0L,
			TEXTURE_COORD_OFFSET_BYTES,
			textureEnabled ? TEXTURE_LIGHT_OFFSET_BYTES : COLOR_OFFSET_BYTES,
			RAW_MATERIAL_COLOR_OFFSET_BYTES,
			BASE_LEGACY_LIGHT_OFFSET_BYTES,
			NORMAL_OFFSET_BYTES,
			MODEL_KIND_OFFSET_BYTES,
			MATERIAL_FAMILY_OFFSET_BYTES,
			TERRAIN_VARIATION_MASK_OFFSET_BYTES,
			TERRAIN_BLEND_COLOR_OFFSET_BYTES,
			TERRAIN_BLEND_STRENGTH_OFFSET_BYTES);
	}

	private void bindPreparedRemasterShadowMask() throws Exception {
		if (activeRemasterShadowMask == null || remasterShadowMaskTextureId == 0) {
			return;
		}
		gl.glActiveTexture(gl.GL_TEXTURE1);
		gl.glBindTexture(gl.GL_TEXTURE_2D, remasterShadowMaskTextureId);
		gl.glActiveTexture(gl.GL_TEXTURE0);
	}

	private void bindPreparedRemasterGlowMask() throws Exception {
		if (activeRemasterGlowMask == null || remasterGlowMaskTextureId == 0) {
			return;
		}
		gl.glActiveTexture(gl.GL_TEXTURE2);
		gl.glBindTexture(gl.GL_TEXTURE_2D, remasterGlowMaskTextureId);
		gl.glActiveTexture(gl.GL_TEXTURE0);
	}

	private boolean isTransparentChunkBatch(Renderer3DFrame frame, WorldChunkMaterialBatch batch) {
		OpenGLTextureRegion region = textureRegionForBatch(frame, batch);
		return region != null && region.hasTransparency();
	}

	private boolean isFrustumCulledChunkBatch(
		Renderer3DFrame frame,
		WorldChunkMaterialBatch batch,
		float[] viewMatrix,
		int drawOffsetX,
		int drawOffsetZ) {
		if (frame == null || batch == null || viewMatrix == null || !batch.hasBounds()) {
			return false;
		}
		float near = Math.max(1.0f, frame.getNearPlane());
		float scale = (float) (1 << Math.max(0, Math.min(24, frame.getPerspectiveShift())));
		float viewportWidth = Math.max(1.0f, frame.getViewportWidth());
		float viewportHeight = Math.max(1.0f, frame.getViewportHeight());
		float centerX = frame.getCenterX();
		float centerY = frame.getCenterY();
		float minScreenX = Float.MAX_VALUE;
		float maxScreenX = -Float.MAX_VALUE;
		float minScreenY = Float.MAX_VALUE;
		float maxScreenY = -Float.MAX_VALUE;
		for (int xIndex = 0; xIndex < 2; xIndex++) {
			float x =
				(xIndex == 0 ? batch.minX : batch.maxX)
					+ drawOffsetX;
			for (int yIndex = 0; yIndex < 2; yIndex++) {
				float y = yIndex == 0 ? batch.minY : batch.maxY;
				for (int zIndex = 0; zIndex < 2; zIndex++) {
					float z =
						(zIndex == 0 ? batch.minZ : batch.maxZ)
							+ drawOffsetZ;
					float cameraX = viewMatrix[0] * x + viewMatrix[1] * y + viewMatrix[2] * z + viewMatrix[3];
					float cameraY = viewMatrix[4] * x + viewMatrix[5] * y + viewMatrix[6] * z + viewMatrix[7];
					float cameraZ = viewMatrix[8] * x + viewMatrix[9] * y + viewMatrix[10] * z + viewMatrix[11];
					if (cameraZ <= near) {
						return false;
					}
					float screenX = centerX + scale * cameraX / cameraZ;
					float screenY = centerY - scale * cameraY / cameraZ;
					minScreenX = Math.min(minScreenX, screenX);
					maxScreenX = Math.max(maxScreenX, screenX);
					minScreenY = Math.min(minScreenY, screenY);
					maxScreenY = Math.max(maxScreenY, screenY);
				}
			}
		}
		if (minScreenX == Float.MAX_VALUE || minScreenY == Float.MAX_VALUE) {
			return false;
		}
		return maxScreenX < -FRUSTUM_BATCH_CULL_SCREEN_PADDING
			|| minScreenX > viewportWidth + FRUSTUM_BATCH_CULL_SCREEN_PADDING
			|| maxScreenY < -FRUSTUM_BATCH_CULL_SCREEN_PADDING
			|| minScreenY > viewportHeight + FRUSTUM_BATCH_CULL_SCREEN_PADDING;
	}

	private boolean isFogCulledChunkBatch(
		Renderer3DFrame frame,
		WorldChunkMaterialBatch batch,
		float[] viewMatrix,
		int drawOffsetX,
		int drawOffsetZ) {
		if (frame == null || batch == null || viewMatrix == null || !batch.hasSpatialBounds()) {
			return false;
		}
		float fogEnd = frame.getFogDistance() + FOG_BATCH_CULL_PADDING;
		float nearestCameraZ = Float.MAX_VALUE;
		for (int xIndex = 0; xIndex < 2; xIndex++) {
			float x =
				(xIndex == 0 ? batch.minX : batch.maxX)
					+ drawOffsetX;
			for (int yIndex = 0; yIndex < 2; yIndex++) {
				float y = yIndex == 0 ? batch.minY : batch.maxY;
				for (int zIndex = 0; zIndex < 2; zIndex++) {
					float z =
						(zIndex == 0 ? batch.minZ : batch.maxZ)
							+ drawOffsetZ;
					float cameraZ = viewMatrix[8] * x + viewMatrix[9] * y + viewMatrix[10] * z + viewMatrix[11];
					if (cameraZ < nearestCameraZ) {
						nearestCameraZ = cameraZ;
					}
				}
			}
		}
		return nearestCameraZ > fogEnd;
	}

	private void bindChunkTexture(int textureId, WorldChunkDrawAccumulator accumulator) throws Exception {
		gl.glActiveTexture(gl.GL_TEXTURE0);
		if (accumulator.boundTextureId == textureId) {
			return;
		}
		gl.glBindTexture(gl.GL_TEXTURE_2D, textureId);
		accumulator.boundTextureId = textureId;
		accumulator.recordTextureBind();
	}

	private OpenGLTextureRegion textureRegionForBatch(Renderer3DFrame frame, WorldChunkMaterialBatch batch) {
		if (textureCache == null || batch == null) {
			return null;
		}
		int textureId = effectiveChunkTextureId(frame, batch.textureId, batch.fallbackColor);
		return textureId == LEGACY_TRANSPARENT_TEXTURE ? null : textureCache.getRegion(frame, textureId);
	}

	private WorldChunkBatchDrawState chunkBatchDrawState(
		Renderer3DFrame frame,
		WorldChunkMaterialBatch batch,
		boolean textured) {
		if (!textured) {
			return WorldChunkBatchDrawState.UNTEXTURED;
		}
		OpenGLTextureRegion region = textureRegionForBatch(frame, batch);
		if (region == null) {
			return fallbackColorForBatch(frame, batch) == NO_FALLBACK_COLOR
				? WorldChunkBatchDrawState.SKIPPED
				: WorldChunkBatchDrawState.FLAT_FALLBACK;
		}
		return WorldChunkBatchDrawState.textured(region.getTextureId());
	}

	private void bindChunkBatchDrawState(
		WorldChunkBatchDrawState drawState,
		WorldChunkDrawAccumulator accumulator,
		boolean shaderActive) throws Exception {
		if (drawState == null || drawState.bindResult == WorldChunkBatchBindResult.SKIPPED) {
			return;
		}
		if (drawState.bindResult == WorldChunkBatchBindResult.UNTEXTURED) {
			return;
		}
		if (drawState.bindResult == WorldChunkBatchBindResult.FLAT_FALLBACK) {
			gl.glDisable(gl.GL_TEXTURE_2D);
			gl.glDisable(gl.GL_ALPHA_TEST);
			gl.glDisable(gl.GL_BLEND);
			if (!shaderActive) {
				gl.glDisableClientState(gl.GL_TEXTURE_COORD_ARRAY);
				gl.glEnableClientState(gl.GL_COLOR_ARRAY);
				gl.glColorPointer(COLOR_COMPONENT_COUNT, gl.GL_FLOAT, STRIDE_BYTES, COLOR_OFFSET_BYTES);
			}
			gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
			return;
		}
		gl.glEnable(gl.GL_TEXTURE_2D);
		gl.glEnable(gl.GL_ALPHA_TEST);
		gl.glAlphaFunc(gl.GL_GREATER, 0.5f);
		gl.glDisable(gl.GL_BLEND);
		if (!shaderActive) {
			gl.glEnableClientState(gl.GL_COLOR_ARRAY);
			gl.glColorPointer(
				TEXTURE_LIGHT_COMPONENT_COUNT,
				gl.GL_FLOAT,
				STRIDE_BYTES,
				TEXTURE_LIGHT_OFFSET_BYTES);
			gl.glEnableClientState(gl.GL_TEXTURE_COORD_ARRAY);
			gl.glTexCoordPointer(
				TEXTURE_COORD_COMPONENT_COUNT,
				gl.GL_FLOAT,
				STRIDE_BYTES,
				TEXTURE_COORD_OFFSET_BYTES);
		}
		bindChunkTexture(drawState.textureId, accumulator);
		gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
	}

	private void flushChunkDrawRange(
		WorldChunkDrawRange range,
		WorldChunkDrawAccumulator accumulator) throws Exception {
		if (range == null || range.indexCount <= 0) {
			return;
		}
		gl.glDrawElements(
			gl.GL_TRIANGLES,
			range.indexCount,
			gl.GL_UNSIGNED_INT,
			range.startIndex * 4L);
		accumulator.recordDrawCall();
	}

	private int fallbackColorForBatch(Renderer3DFrame frame, WorldChunkMaterialBatch batch) {
		int textureId = effectiveChunkTextureId(frame, batch.textureId, batch.fallbackColor);
		int textureAverageColor = averageTextureColor(frame, textureId);
		if (textureAverageColor != NO_FALLBACK_COLOR) {
			return textureAverageColor;
		}
		int fallbackTextureAverageColor = averageTextureColor(frame, batch.fallbackColor);
		if (fallbackTextureAverageColor != NO_FALLBACK_COLOR) {
			return fallbackTextureAverageColor;
		}
		if (batch.textureId == LEGACY_TRANSPARENT_TEXTURE && batch.fallbackColor != LEGACY_TRANSPARENT_TEXTURE) {
			if (isFrameTextureReference(frame, batch.fallbackColor)) {
				return NO_FALLBACK_COLOR;
			}
			return batch.fallbackColor & 0xFFFFFF;
		}
		return NO_FALLBACK_COLOR;
	}

	private boolean isFrameTextureReference(Renderer3DFrame frame, int textureId) {
		return frame != null && textureId >= 0 && textureId < frame.getTextureCount();
	}

	private int averageTextureColor(Renderer3DFrame frame, int textureId) {
		if (frame == null || textureId < 0) {
			return NO_FALLBACK_COLOR;
		}
		Renderer3DTextureData texture = frame.getTexture(textureId);
		if (texture == null || texture.getOpaquePixelCount() <= 0) {
			return NO_FALLBACK_COLOR;
		}
		return texture.getAverageOpaqueRgb();
	}

	private void putFlatFallbackColor(int color) throws Exception {
		float brightness = RendererDayNightCycle.currentBrightnessMultiplier();
		gl.glColor4f(
			brightnessColor(((color >> 16) & 0xFF) / 255.0f, brightness),
			brightnessColor(((color >> 8) & 0xFF) / 255.0f, brightness),
			brightnessColor((color & 0xFF) / 255.0f, brightness),
			1.0f);
	}

	private float brightnessColor(float color, float brightness) {
		float adjusted = color * brightness;
		if (adjusted < 0.0f) {
			return 0.0f;
		}
		if (adjusted > 1.0f) {
			return 1.0f;
		}
		return adjusted;
	}

	private int currentBrightnessBits() {
		if (shouldUseUnbakedRemasterMaterialBase()) {
			return 0;
		}
		return Float.floatToIntBits(RendererDayNightCycle.currentBrightnessMultiplier());
	}

	private int currentFogModeBits() {
		return RendererFogSettings.getMode().ordinal();
	}

	private int currentLightingModeBits() {
		return RendererLightingSettings.getMode().ordinal();
	}

	private int currentGeometryModeBits() {
		return RendererGeometrySettings.getMode().ordinal();
	}

	private boolean shouldUseSpatialMaterialBatches(Renderer3DWorldChunkFrame.ChunkMesh chunk) {
		return spatialCullEnabled && (chunk == null || !chunk.isObjectChunk());
	}

	static int spatialBatchTileSize() {
		return SPATIAL_BATCH_TILE_SIZE;
	}

	private static int readSpatialBatchTileSize() {
		String value = System.getProperty(SPATIAL_BATCH_TILE_SIZE_PROPERTY);
		if (value == null || value.trim().isEmpty()) {
			value = System.getenv(SPATIAL_BATCH_TILE_SIZE_ENV);
		}
		if (value == null || value.trim().isEmpty()) {
			return DEFAULT_SPATIAL_BATCH_TILE_SIZE;
		}
		try {
			int parsed = Integer.parseInt(value.trim());
			if (parsed < MIN_SPATIAL_BATCH_TILE_SIZE || parsed > MAX_SPATIAL_BATCH_TILE_SIZE) {
				System.out.println(
					"[renderer-v2] Invalid spatial chunk tile size '" + value
						+ "'; using " + DEFAULT_SPATIAL_BATCH_TILE_SIZE + ".");
				return DEFAULT_SPATIAL_BATCH_TILE_SIZE;
			}
			return parsed;
		} catch (NumberFormatException ignored) {
			System.out.println(
				"[renderer-v2] Invalid spatial chunk tile size '" + value
					+ "'; using " + DEFAULT_SPATIAL_BATCH_TILE_SIZE + ".");
			return DEFAULT_SPATIAL_BATCH_TILE_SIZE;
		}
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
			if (Float.isNaN(parsed) || parsed < minValue || parsed > maxValue) {
				System.out.println(
					"[renderer-v2] Invalid world chunk upload budget '" + value
						+ "'; using " + defaultValue + "ms.");
				return defaultValue;
			}
			return parsed;
		} catch (NumberFormatException ignored) {
			System.out.println(
				"[renderer-v2] Invalid world chunk upload budget '" + value
					+ "'; using " + defaultValue + "ms.");
			return defaultValue;
		}
	}

	private void loadWorldProjectionAndView(Renderer3DFrame frame) throws Exception {
		loadMatrix(gl.GL_PROJECTION, projectionMatrix(frame));
		loadMatrix(gl.GL_MODELVIEW, worldViewMatrix(frame));
	}

	private void loadMatrix(int matrixMode, float[] matrix) throws Exception {
		if (worldToClipMatrixBuffer == null) {
			worldToClipMatrixBuffer = ByteBuffer
				.allocateDirect(16 * 4)
				.order(ByteOrder.nativeOrder())
				.asFloatBuffer();
		}
		worldToClipMatrixBuffer.clear();
		for (int column = 0; column < 4; column++) {
			for (int row = 0; row < 4; row++) {
				worldToClipMatrixBuffer.put(matrix[row * 4 + column]);
			}
		}
		worldToClipMatrixBuffer.flip();
		gl.glMatrixMode(matrixMode);
		gl.glLoadMatrixf(worldToClipMatrixBuffer);
	}

	private FloatBuffer putWorldToClipMatrix(float[] matrix) {
		if (worldToClipMatrixBuffer == null) {
			worldToClipMatrixBuffer = ByteBuffer
				.allocateDirect(16 * 4)
				.order(ByteOrder.nativeOrder())
				.asFloatBuffer();
		}
		return putColumnMajorMatrix(worldToClipMatrixBuffer, matrix);
	}

	private FloatBuffer putWorldViewMatrix(float[] matrix) {
		if (worldViewMatrixBuffer == null) {
			worldViewMatrixBuffer = ByteBuffer
				.allocateDirect(16 * 4)
				.order(ByteOrder.nativeOrder())
				.asFloatBuffer();
		}
		return putColumnMajorMatrix(worldViewMatrixBuffer, matrix);
	}

	private FloatBuffer putColumnMajorMatrix(FloatBuffer buffer, float[] matrix) {
		buffer.clear();
		for (int column = 0; column < 4; column++) {
			for (int row = 0; row < 4; row++) {
				buffer.put(matrix[row * 4 + column]);
			}
		}
		buffer.flip();
		return buffer;
	}

	private float[] worldViewMatrix(Renderer3DFrame frame) {
		float[] view = translationMatrix(
			-frame.getCameraOffsetX(),
			-frame.getCameraOffsetY(),
			-frame.getCameraOffsetZ());
		if (frame.getCameraRotationZ() != 0) {
			view = multiply(rotationZMatrix(frame.getCameraRotationZ()), view);
		}
		if (frame.getCameraRotationY() != 0) {
			view = multiply(rotationYMatrix(frame.getCameraRotationY()), view);
		}
		if (frame.getCameraRotationX() != 0) {
			view = multiply(rotationXMatrix(frame.getCameraRotationX()), view);
		}
		return view;
	}

	private float[] skyViewMatrix(Renderer3DFrame frame) {
		float[] view = translationMatrix(0.0f, 0.0f, 0.0f);
		if (frame.getCameraRotationZ() != 0) {
			view = multiply(rotationZMatrix(frame.getCameraRotationZ()), view);
		}
		if (frame.getCameraRotationY() != 0) {
			view = multiply(rotationYMatrix(frame.getCameraRotationY()), view);
		}
		if (frame.getCameraRotationX() != 0) {
			view = multiply(rotationXMatrix(frame.getCameraRotationX()), view);
		}
		return view;
	}

	private void configureFog(Renderer3DFrame frame) throws Exception {
		if (RendererFogSettings.getMode() == RendererFogSettings.Mode.OFF) {
			gl.glDisable(gl.GL_FOG);
			return;
		}
		if (fogColorBuffer == null) {
			fogColorBuffer = ByteBuffer.allocateDirect(4 * 4)
				.order(ByteOrder.nativeOrder())
				.asFloatBuffer();
		}
		RendererDayNightCycle.Presentation presentation = RendererDayNightCycle.currentPresentation();
		fogColorBuffer.clear();
		fogColorBuffer
			.put(presentation.fogRed)
			.put(presentation.fogGreen)
			.put(presentation.fogBlue)
			.put(1.0f)
			.flip();
		gl.glFogi(gl.GL_FOG_MODE, gl.GL_LINEAR);
		gl.glFogf(gl.GL_FOG_START, visualFogStart(frame));
		gl.glFogf(gl.GL_FOG_END, visualFogEnd(frame));
		gl.glFogfv(gl.GL_FOG_COLOR, fogColorBuffer);
		gl.glEnable(gl.GL_FOG);
	}

	private float visualFogStart(Renderer3DFrame frame) {
		float fogStart = frame.getFogStartDistance();
		float fogEnd = frame.getFogDistance();
		float fogRange = Math.max(1.0f, fogEnd - fogStart);
		return Math.max(0.0f, fogStart - fogRange * 0.42f);
	}

	private float visualFogEnd(Renderer3DFrame frame) {
		float fogEnd = frame.getFogDistance();
		float visualFogStart = visualFogStart(frame);
		float fogRange = Math.max(1.0f, fogEnd - visualFogStart);
		return visualFogStart + fogRange * FOG_VISUAL_END_FRACTION;
	}

	private float[] projectionMatrix(Renderer3DFrame frame) {
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

	private float[] translationMatrix(float x, float y, float z) {
		return new float[] {
			1.0f, 0.0f, 0.0f, x,
			0.0f, 1.0f, 0.0f, y,
			0.0f, 0.0f, 1.0f, z,
			0.0f, 0.0f, 0.0f, 1.0f
		};
	}

	private float[] rotationZMatrix(int angle) {
		float sin = sin1024(angle);
		float cos = cos1024(angle);
		return new float[] {
			cos, sin, 0.0f, 0.0f,
			-sin, cos, 0.0f, 0.0f,
			0.0f, 0.0f, 1.0f, 0.0f,
			0.0f, 0.0f, 0.0f, 1.0f
		};
	}

	private float[] rotationYMatrix(int angle) {
		float sin = sin1024(angle);
		float cos = cos1024(angle);
		return new float[] {
			cos, 0.0f, sin, 0.0f,
			0.0f, 1.0f, 0.0f, 0.0f,
			-sin, 0.0f, cos, 0.0f,
			0.0f, 0.0f, 0.0f, 1.0f
		};
	}

	private float[] rotationXMatrix(int angle) {
		float sin = sin1024(angle);
		float cos = cos1024(angle);
		return new float[] {
			1.0f, 0.0f, 0.0f, 0.0f,
			0.0f, cos, -sin, 0.0f,
			0.0f, sin, cos, 0.0f,
			0.0f, 0.0f, 0.0f, 1.0f
		};
	}

	private float[] multiply(float[] left, float[] right) {
		float[] result = new float[16];
		for (int row = 0; row < 4; row++) {
			for (int column = 0; column < 4; column++) {
				float value = 0.0f;
				for (int index = 0; index < 4; index++) {
					value += left[row * 4 + index] * right[index * 4 + column];
				}
				result[row * 4 + column] = value;
			}
		}
		return result;
	}

	private float sin1024(int angle) {
		return FastMath.trigTable1024[angle & 1023] / 32768.0f;
	}

	private float cos1024(int angle) {
		return FastMath.trigTable1024[(angle & 1023) + 1024] / 32768.0f;
	}

	private void uploadChunk(
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		WorldChunkBuffer buffer,
		Renderer3DFrame frame,
		long bufferSignature,
		int vertexCount,
		int indexCount,
		boolean atlasTextureCoordinates,
		int brightnessBits,
		int fogModeBits,
		int lightingModeBits,
		int geometryModeBits,
		boolean replacementCompositeDrawOnly,
		List<ShadowProofCaster> shadowProofCasters,
		long shadowProofSignature) throws Exception {
		ensureUploadBuffers(vertexCount, indexCount);
		copyChunkVertices(chunk, frame, vertexCount, atlasTextureCoordinates, shadowProofCasters);
		copyChunkIndices(chunk, indexCount);
		WorldChunkMaterialBatch[] materialBatches =
			copyMaterialIndices(chunk, indexCount, replacementCompositeDrawOnly, shouldUseSpatialMaterialBatches(chunk));
		int bufferUsage = chunkBufferUsage(chunk);
		long uploadedByteCount =
			(long) vertexUploadBuffer.remaining() * 4L
				+ (long) indexUploadBuffer.remaining() * 4L
				+ (long) materialIndexUploadBuffer.remaining() * 4L;

		gl.glBindBuffer(gl.GL_ARRAY_BUFFER, buffer.vertexBufferId);
		gl.glBufferData(gl.GL_ARRAY_BUFFER, vertexUploadBuffer, bufferUsage);
		gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, buffer.indexBufferId);
		gl.glBufferData(gl.GL_ELEMENT_ARRAY_BUFFER, indexUploadBuffer, bufferUsage);
		gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, buffer.materialIndexBufferId);
		gl.glBufferData(gl.GL_ELEMENT_ARRAY_BUFFER, materialIndexUploadBuffer, bufferUsage);
		gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, 0);
		gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0);

		buffer.signature = bufferSignature;
		buffer.uploadedVertexOffsetX = chunk.getVertexOffsetX();
		buffer.uploadedVertexOffsetZ = chunk.getVertexOffsetZ();
		buffer.vertexCount = vertexCount;
		buffer.indexCount = indexCount;
		buffer.triangleCount = chunk.getTriangleCount();
		buffer.atlasTextureCoordinates = atlasTextureCoordinates;
		buffer.brightnessBits = brightnessBits;
		buffer.fogModeBits = fogModeBits;
		buffer.lightingModeBits = lightingModeBits;
		buffer.geometryModeBits = geometryModeBits;
		buffer.replacementCompositeDrawOnly = replacementCompositeDrawOnly;
		buffer.materialBatches = materialBatches;
		buffer.shadowProofSignature = shadowProofSignature;
		buffer.uploadedByteCount = uploadedByteCount;
	}

	private int chunkBufferUsage(Renderer3DWorldChunkFrame.ChunkMesh chunk) {
		return chunk != null
			&& chunk.getChunkRole() == Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS
			? gl.GL_STREAM_DRAW
			: gl.GL_STATIC_DRAW;
	}

	private WorldChunkMaterialBatch[] copyMaterialIndices(
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		int indexCount,
		boolean replacementCompositeDrawOnly,
		boolean spatialBatches) {
		int triangleCount = Math.min(chunk.getTriangleCount(), indexCount / 3);
		Map<WorldChunkMaterialKey, WorldChunkMaterialBatchBuilder> batchesByMaterial =
			new LinkedHashMap<WorldChunkMaterialKey, WorldChunkMaterialBatchBuilder>();
		for (int triangle = 0; triangle < triangleCount; triangle++) {
			if (!shouldIncludeChunkTriangleInResidentDrawBuffer(chunk, triangle, replacementCompositeDrawOnly)) {
				continue;
			}
			WorldChunkMaterialKey key = WorldChunkMaterialKey.from(chunk, triangle, spatialBatches);
			WorldChunkMaterialBatchBuilder batch = batchesByMaterial.get(key);
			if (batch == null) {
				batch = new WorldChunkMaterialBatchBuilder(key);
				batchesByMaterial.put(key, batch);
			}
			batch.indexCount += 3;
			batch.includeTriangleBounds(chunk, triangle);
		}

		int nextIndex = 0;
		List<WorldChunkMaterialBatchBuilder> batchBuilders =
			new ArrayList<WorldChunkMaterialBatchBuilder>(batchesByMaterial.values());
		for (WorldChunkMaterialBatchBuilder batch : batchBuilders) {
			batch.startIndex = nextIndex;
			batch.writeIndex = nextIndex;
			nextIndex += batch.indexCount;
		}

		materialIndexUploadBuffer.clear();
		materialIndexUploadBuffer.limit(nextIndex);
		for (int triangle = 0; triangle < triangleCount; triangle++) {
			if (!shouldIncludeChunkTriangleInResidentDrawBuffer(chunk, triangle, replacementCompositeDrawOnly)) {
				continue;
			}
			WorldChunkMaterialBatchBuilder batch =
				batchesByMaterial.get(WorldChunkMaterialKey.from(chunk, triangle, spatialBatches));
			int sourceIndex = triangle * 3;
			materialIndexUploadBuffer.put(batch.writeIndex++, chunk.getIndex(sourceIndex));
			materialIndexUploadBuffer.put(batch.writeIndex++, chunk.getIndex(sourceIndex + 1));
			materialIndexUploadBuffer.put(batch.writeIndex++, chunk.getIndex(sourceIndex + 2));
		}
		materialIndexUploadBuffer.position(0);

		WorldChunkMaterialBatch[] batches = new WorldChunkMaterialBatch[batchBuilders.size()];
		for (int i = 0; i < batchBuilders.size(); i++) {
			batches[i] = batchBuilders.get(i).toBatch();
		}
		return batches;
	}

	private boolean shouldIncludeChunkTriangleInResidentDrawBuffer(
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		int triangle,
		boolean replacementCompositeDrawOnly) {
		if (!replacementCompositeDrawOnly) {
			return true;
		}
		Renderer3DModelKind kind = chunk.getTriangleModelKind(triangle);
		return residentObjectsEnabled
			|| (kind != Renderer3DModelKind.GAME_OBJECT && kind != Renderer3DModelKind.WALL_OBJECT);
	}

	private void ensureUploadBuffers(int vertexCount, int indexCount) {
		int requiredVertexFloats = vertexCount * FLOATS_PER_VERTEX;
		if (vertexUploadBuffer == null || vertexUploadCapacity < requiredVertexFloats) {
			vertexUploadCapacity = growCapacity(vertexUploadCapacity, requiredVertexFloats);
			vertexUploadBuffer = ByteBuffer
				.allocateDirect(vertexUploadCapacity * 4)
				.order(ByteOrder.nativeOrder())
				.asFloatBuffer();
		}

		if (indexUploadBuffer == null || indexUploadCapacity < indexCount) {
			indexUploadCapacity = growCapacity(indexUploadCapacity, indexCount);
			indexUploadBuffer = ByteBuffer
				.allocateDirect(indexUploadCapacity * 4)
				.order(ByteOrder.nativeOrder())
				.asIntBuffer();
		}

		if (materialIndexUploadBuffer == null || materialIndexUploadCapacity < indexCount) {
			materialIndexUploadCapacity = growCapacity(materialIndexUploadCapacity, indexCount);
			materialIndexUploadBuffer = ByteBuffer
				.allocateDirect(materialIndexUploadCapacity * 4)
				.order(ByteOrder.nativeOrder())
				.asIntBuffer();
		}
	}

	private int growCapacity(int currentCapacity, int requiredCapacity) {
		int capacity = Math.max(1024, currentCapacity);
		while (capacity < requiredCapacity) {
			capacity *= 2;
		}
		return capacity;
	}

	private void copyChunkVertices(
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		Renderer3DFrame frame,
		int vertexCount,
		boolean atlasTextureCoordinates,
		List<ShadowProofCaster> shadowProofCasters) {
		vertexUploadBuffer.clear();
		int[] smoothDiffuseLights = buildChunkSmoothDiffuseLights(chunk, vertexCount);
		float[] bakedTerrainShadowMask = buildBakedTerrainShadowMask(chunk, shadowProofCasters);
		boolean unbakedRemasterBase = shouldUseUnbakedRemasterMaterialBase();
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			int coord = vertex * POSITION_COMPONENT_COUNT;
			int triangle = vertex / 3;
			OpenGLTextureRegion textureRegion =
				atlasTextureCoordinates ? textureRegionForVertex(frame, chunk, vertex) : null;
			int effectiveTextureId = effectiveChunkTextureId(
				frame,
				chunk.getTriangleTexture(triangle),
				chunk.getTriangleFallbackColor(triangle));
			int rawMaterialColor = rawMaterialColorForTriangle(frame, chunk, triangle);
			vertexUploadBuffer.put((float) chunk.getVertexCoord(coord));
			vertexUploadBuffer.put((float) chunk.getVertexCoord(coord + 1));
			vertexUploadBuffer.put((float) chunk.getVertexCoord(coord + 2));
			int legacyLight = chunkLegacyLight(chunk, vertex, smoothDiffuseLights);
			int baseLegacyLight = legacyLight;
			legacyLight = applyBakedTerrainShadow(legacyLight, bakedTerrainShadowMask, triangle);
			putChunkMaterialColor(
				materialColorForTriangle(frame, chunk, triangle),
				rawMaterialColor,
				effectiveTextureId,
				legacyLight,
				unbakedRemasterBase);
			vertexUploadBuffer.put(chunkTextureU(textureRegion, chunk.getVertexTextureU(vertex)));
			vertexUploadBuffer.put(chunkTextureV(textureRegion, chunk.getVertexTextureV(vertex)));
			putChunkTextureLight(unbakedRemasterBase ? 1.0f : textureLightFactor(legacyLight));
			putChunkShaderInputs(chunk, vertex, triangle, legacyLight, baseLegacyLight, rawMaterialColor);
		}
		vertexUploadBuffer.flip();
	}

	private boolean shouldUseUnbakedRemasterMaterialBase() {
		return remasterLightingShaderEnabled
			&& !rawMaterialShaderEnabled
			&& RendererLightingSettings.getMode() == RendererLightingSettings.Mode.DIRECTIONAL;
	}

	private void putChunkShaderInputs(
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		int vertex,
		int triangle,
		int legacyLight,
		int baseLegacyLight,
		int rawMaterialColor) {
		vertexUploadBuffer.put((float) clampLegacyLight(legacyLight));
		vertexUploadBuffer.put((float) clampLegacyLight(baseLegacyLight));
		vertexUploadBuffer.put(((rawMaterialColor >> 16) & 0xFF) / 255.0f);
		vertexUploadBuffer.put(((rawMaterialColor >> 8) & 0xFF) / 255.0f);
		vertexUploadBuffer.put((rawMaterialColor & 0xFF) / 255.0f);
		vertexUploadBuffer.put(chunk.getVertexNormalX(vertex) / 256.0f);
		vertexUploadBuffer.put(chunk.getVertexNormalY(vertex) / 256.0f);
		vertexUploadBuffer.put(chunk.getVertexNormalZ(vertex) / 256.0f);
		Renderer3DModelKind modelKind = triangle >= 0 && triangle < chunk.getTriangleCount()
			? chunk.getTriangleModelKind(triangle)
			: Renderer3DModelKind.UNCLASSIFIED;
		vertexUploadBuffer.put((float) modelKind.ordinal());
		Renderer3DMaterialFamily materialFamily = triangle >= 0 && triangle < chunk.getTriangleCount()
			? chunk.getTriangleMaterialFamily(triangle)
			: Renderer3DMaterialFamily.UNCLASSIFIED;
		vertexUploadBuffer.put((float) materialFamily.getShaderId());
		vertexUploadBuffer.put(triangle >= 0 && triangle < chunk.getTriangleCount()
			? (float) chunk.getTriangleTerrainVariationMask(triangle)
			: 0.0f);
		int terrainBlendColor = chunk.getVertexTerrainBlendColor(vertex);
		vertexUploadBuffer.put(((terrainBlendColor >> 16) & 0xFF) / 255.0f);
		vertexUploadBuffer.put(((terrainBlendColor >> 8) & 0xFF) / 255.0f);
		vertexUploadBuffer.put((terrainBlendColor & 0xFF) / 255.0f);
		vertexUploadBuffer.put(chunk.getVertexTerrainBlendStrength(vertex) / 255.0f);
	}

	private float[] buildBakedTerrainShadowMask(
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		List<ShadowProofCaster> shadowProofCasters) {
		if (!shouldDrawProjectedShadowProof()
			|| chunk == null
			|| shadowProofCasters == null
			|| shadowProofCasters.isEmpty()
			|| chunk.getTerrainTriangles() <= 0) {
			return null;
		}
		int triangleCount = Math.min(chunk.getTriangleCount(), chunk.getIndexCount() / 3);
		float[] shadowMask = new float[triangleCount];
		for (int triangle = 0; triangle < triangleCount; triangle++) {
			if (chunk.getTriangleModelKind(triangle) != Renderer3DModelKind.TERRAIN) {
				continue;
			}
			shadowMask[triangle] = projectedShadowProofAlpha(chunk, triangle, shadowProofCasters);
		}
		return shadowMask;
	}

	private int applyBakedTerrainShadow(int legacyLight, float[] bakedTerrainShadowMask, int triangle) {
		if (bakedTerrainShadowMask == null || triangle < 0 || triangle >= bakedTerrainShadowMask.length) {
			return legacyLight;
		}
		float shadow = bakedTerrainShadowMask[triangle];
		if (shadow <= 0.0f) {
			return legacyLight;
		}
		return clampLegacyLight(legacyLight + Math.round(shadow * 230.0f));
	}

	private int[] buildChunkSmoothDiffuseLights(Renderer3DWorldChunkFrame.ChunkMesh chunk, int vertexCount) {
		int[] diffuseLights = new int[vertexCount];
		if (RendererGeometrySettings.getMode() == RendererGeometrySettings.Mode.FACETED
			|| RendererGeometrySettings.getMode() == RendererGeometrySettings.Mode.WIRE) {
			int triangleCount = Math.min(chunk.getTriangleCount(), chunk.getIndexCount() / 3);
			for (int triangle = 0; triangle < triangleCount; triangle++) {
				int faceDiffuse = chunkFaceDiffuseLight(chunk, triangle);
				int sourceIndex = triangle * 3;
				for (int i = 0; i < 3; i++) {
					int vertex = chunk.getIndex(sourceIndex + i);
					if (isChunkVertexIndexValid(chunk, vertex, vertexCount)) {
						diffuseLights[vertex] = faceDiffuse;
					}
				}
			}
			return diffuseLights;
		}
		int triangleCount = Math.min(chunk.getTriangleCount(), chunk.getIndexCount() / 3);
		Map<ChunkVertexKey, SmoothDiffuseAccumulator> terrainDiffuseByVertex =
			new HashMap<ChunkVertexKey, SmoothDiffuseAccumulator>();
		for (int triangle = 0; triangle < triangleCount; triangle++) {
			if (chunk.getTriangleModelKind(triangle) != Renderer3DModelKind.TERRAIN) {
				continue;
			}
			int diffuse = chunkFaceDiffuseLight(chunk, triangle);
			int sourceIndex = triangle * 3;
			for (int i = 0; i < 3; i++) {
				int vertex = chunk.getIndex(sourceIndex + i);
				if (!isChunkVertexIndexValid(chunk, vertex, vertexCount)) {
					continue;
				}
				ChunkVertexKey key = ChunkVertexKey.from(chunk, vertex);
				SmoothDiffuseAccumulator accumulator = terrainDiffuseByVertex.get(key);
				if (accumulator == null) {
					accumulator = new SmoothDiffuseAccumulator();
					terrainDiffuseByVertex.put(key, accumulator);
				}
				accumulator.add(diffuse);
			}
		}

		for (int triangle = 0; triangle < triangleCount; triangle++) {
			boolean smoothTerrain = chunk.getTriangleModelKind(triangle) == Renderer3DModelKind.TERRAIN;
			int faceDiffuse = smoothTerrain ? 0 : chunkFaceDiffuseLight(chunk, triangle);
			int sourceIndex = triangle * 3;
			for (int i = 0; i < 3; i++) {
				int vertex = chunk.getIndex(sourceIndex + i);
				if (!isChunkVertexIndexValid(chunk, vertex, vertexCount)) {
					continue;
				}
				if (smoothTerrain) {
					SmoothDiffuseAccumulator accumulator =
						terrainDiffuseByVertex.get(ChunkVertexKey.from(chunk, vertex));
					diffuseLights[vertex] = accumulator == null ? 0 : accumulator.average();
				} else {
					diffuseLights[vertex] = faceDiffuse;
				}
			}
		}
		return diffuseLights;
	}

	private boolean isChunkVertexIndexValid(
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		int vertex,
		int expectedVertexCount) {
		return vertex >= 0 && vertex < expectedVertexCount && vertex < chunk.getVertexCount();
	}

	private int materialColorForTriangle(
		Renderer3DFrame frame,
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		int triangle) {
		if (triangle < 0 || triangle >= chunk.getTriangleCount()) {
			return modelKindDiagnosticColor(Renderer3DModelKind.UNCLASSIFIED);
		}
		int fallbackColor = chunk.getTriangleFallbackColor(triangle);
		int textureId = effectiveChunkTextureId(frame, chunk.getTriangleTexture(triangle), fallbackColor);
		int textureAverageColor = averageTextureColor(frame, textureId);
		if (textureAverageColor != NO_FALLBACK_COLOR) {
			return textureAverageColor;
		}
		int fallbackTextureAverageColor = averageTextureColor(frame, fallbackColor);
		if (fallbackTextureAverageColor != NO_FALLBACK_COLOR) {
			return fallbackTextureAverageColor;
		}
		if (chunk.getTriangleTexture(triangle) == LEGACY_TRANSPARENT_TEXTURE
			&& fallbackColor != LEGACY_TRANSPARENT_TEXTURE
			&& !isFrameTextureReference(frame, fallbackColor)) {
			return fallbackColor & 0xFFFFFF;
		}
		return modelKindDiagnosticColor(chunk.getTriangleModelKind(triangle));
	}

	private int rawMaterialColorForTriangle(
		Renderer3DFrame frame,
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		int triangle) {
		if (triangle < 0 || triangle >= chunk.getTriangleCount()) {
			return modelKindDiagnosticColor(Renderer3DModelKind.UNCLASSIFIED);
		}
		int fallbackColor = chunk.getTriangleFallbackColor(triangle);
		int textureId = chunk.getTriangleTexture(triangle);
		if (textureId == LEGACY_TRANSPARENT_TEXTURE
			&& fallbackColor != LEGACY_TRANSPARENT_TEXTURE
			&& !isFrameTextureReference(frame, fallbackColor)) {
			return fallbackColor & 0xFFFFFF;
		}
		int fallbackTextureAverageColor = averageTextureColor(frame, fallbackColor);
		if (fallbackTextureAverageColor != NO_FALLBACK_COLOR) {
			return fallbackTextureAverageColor;
		}
		return materialColorForTriangle(frame, chunk, triangle);
	}

	private OpenGLTextureRegion textureRegionForVertex(
		Renderer3DFrame frame,
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		int vertex) {
		if (textureCache == null || frame == null) {
			return null;
		}
		int triangle = vertex / 3;
		if (triangle < 0 || triangle >= chunk.getTriangleCount()) {
			return null;
		}
		int textureId = effectiveChunkTextureId(
			frame,
			chunk.getTriangleTexture(triangle),
			chunk.getTriangleFallbackColor(triangle));
		return textureId == LEGACY_TRANSPARENT_TEXTURE ? null : textureCache.getRegion(frame, textureId);
	}

	private int effectiveChunkTextureId(Renderer3DFrame frame, int textureId, int fallbackColor) {
		if (textureId != LEGACY_TRANSPARENT_TEXTURE) {
			return textureId;
		}
		return isFrameTextureReference(frame, fallbackColor)
			? fallbackColor
			: textureId;
	}

	private float chunkTextureU(OpenGLTextureRegion region, float sourceU) {
		if (region == null) {
			return 0.0f;
		}
		return region.getU0() + (region.getU1() - region.getU0()) * clampUnit(sourceU);
	}

	private float chunkTextureV(OpenGLTextureRegion region, float sourceV) {
		if (region == null) {
			return 0.0f;
		}
		return region.getV0() + (region.getV1() - region.getV0()) * clampUnit(sourceV);
	}

	private float clampUnit(float value) {
		if (Float.isNaN(value) || value <= 0.0f) {
			return 0.0f;
		}
		if (value >= 1.0f) {
			return 1.0f;
		}
		return value;
	}

	private int modelKindDiagnosticColor(Renderer3DModelKind kind) {
		if (kind == Renderer3DModelKind.TERRAIN) {
			return 0x26F28C;
		} else if (kind == Renderer3DModelKind.WALL) {
			return 0xFF594C;
		} else if (kind == Renderer3DModelKind.ROOF) {
			return 0xFFCC33;
		}
		return 0xBF72FF;
	}

	private void putChunkMaterialColor(
		int color,
		int rawMaterialColor,
		int textureId,
		int legacyLight,
		boolean unbakedRemasterBase) {
		if (unbakedRemasterBase) {
			putUnbakedChunkMaterialColor(rawMaterialColor, textureId);
			return;
		}
		int shadedColor = shadedChunkMaterialColor(color, textureId, legacyLight);
		float brightness = RendererDayNightCycle.currentBrightnessMultiplier();
		vertexUploadBuffer.put(brightnessColor(((shadedColor >> 16) & 0xFF) / 255.0f, brightness));
		vertexUploadBuffer.put(brightnessColor(((shadedColor >> 8) & 0xFF) / 255.0f, brightness));
		vertexUploadBuffer.put(brightnessColor((shadedColor & 0xFF) / 255.0f, brightness));
		vertexUploadBuffer.put(1.0f);
	}

	private void putUnbakedChunkMaterialColor(int rawMaterialColor, int textureId) {
		int color = textureId >= 0 && textureId != LEGACY_TRANSPARENT_TEXTURE
			? 0xFFFFFF
			: rawMaterialColor;
		vertexUploadBuffer.put(((color >> 16) & 0xFF) / 255.0f);
		vertexUploadBuffer.put(((color >> 8) & 0xFF) / 255.0f);
		vertexUploadBuffer.put((color & 0xFF) / 255.0f);
		vertexUploadBuffer.put(1.0f);
	}

	private int shadedChunkMaterialColor(int color, int textureId, int legacyLight) {
		int normalizedLight = clampLegacyLight(legacyLight);
		if (textureId < 0 || textureId == LEGACY_TRANSPARENT_TEXTURE) {
			return legacyFlatResourceColor(color, normalizedLight);
		}
		return legacyTextureShadeColor(color, legacyTextureShadeBand(normalizedLight));
	}

	private int chunkLegacyLight(
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		int vertex,
		int[] diffuseLights) {
		int triangle = vertex / 3;
		Renderer3DModelKind modelKind = triangle >= 0 && triangle < chunk.getTriangleCount()
			? chunk.getTriangleModelKind(triangle)
			: Renderer3DModelKind.UNCLASSIFIED;
		int vertexLightOther = clampLegacyLight(chunk.getVertexLight(vertex));
		int diffuseLight = diffuseLights == null || vertex < 0 || vertex >= diffuseLights.length
			? 0
			: diffuseLights[vertex];
		if (modelKind == Renderer3DModelKind.GAME_OBJECT
			|| modelKind == Renderer3DModelKind.WALL_OBJECT) {
			return applyLightingModeLegacyLight(vertexLightOther);
		}
		return applyLightingModeLegacyLight(96 + vertexLightOther + diffuseLight);
	}

	private int applyLightingModeLegacyLight(int legacyLight) {
		return clampLegacyLight(legacyLight);
	}

	private int chunkFaceDiffuseLight(Renderer3DWorldChunkFrame.ChunkMesh chunk, int triangle) {
		int sourceIndex = triangle * 3;
		if (triangle < 0 || sourceIndex + 2 >= chunk.getIndexCount()) {
			return 0;
		}
		int first = chunk.getIndex(sourceIndex);
		if (!isChunkVertexIndexValid(chunk, first, chunk.getVertexCount())) {
			return 0;
		}
		int scaledNormalX = chunk.getVertexNormalX(first);
		int scaledNormalY = chunk.getVertexNormalY(first);
		int scaledNormalZ = chunk.getVertexNormalZ(first);
		if (scaledNormalX == 0 && scaledNormalY == 0 && scaledNormalZ == 0) {
			return 0;
		}
		return (int) ((scaledNormalY * -10.0d + scaledNormalX * -50.0d + scaledNormalZ * -50.0d) / 106.0d);
	}

	private int legacyFlatResourceColor(int color, int legacyLight) {
		int shade = 255 - legacyLight;
		int shadeSquared = shade * shade;
		int red = (((color >> 16) & 0xFF) * shadeSquared) / 65536;
		int green = (((color >> 8) & 0xFF) * shadeSquared) / 65536;
		int blue = ((color & 0xFF) * shadeSquared) / 65536;
		return red << 16 | green << 8 | blue;
	}

	private int legacyTextureShadeColor(int color, int shadeBand) {
		switch (shadeBand) {
			case 1:
				return (color - (color >>> 3)) & 0xFFFFFF;
			case 2:
				return (color - (color >>> 2)) & 0xFFFFFF;
			case 3:
				return (color - (color >>> 3) - (color >>> 2)) & 0xFFFFFF;
			default:
				return color;
		}
	}

	private void putChunkTextureLight(float light) {
		vertexUploadBuffer.put(light);
		vertexUploadBuffer.put(light);
		vertexUploadBuffer.put(light);
		vertexUploadBuffer.put(1.0f);
	}

	private float legacyTextureLightFactor(int legacyLight) {
		switch (legacyTextureShadeBand(legacyLight)) {
			case 1:
				return 216.0f / 248.0f;
			case 2:
				return 184.0f / 248.0f;
			case 3:
				return 152.0f / 248.0f;
			default:
				return 1.0f;
		}
	}

	private float textureLightFactor(int legacyLight) {
		return legacyTextureLightFactor(legacyLight);
	}

	private int legacyTextureShadeBand(int legacyLight) {
		return clampLegacyLight(legacyLight) >> 6;
	}

	private int clampLegacyLight(int legacyLight) {
		if (legacyLight < 0) {
			return 0;
		}
		if (legacyLight > 255) {
			return 255;
		}
		return legacyLight;
	}

	private static final class ChunkVertexKey {
		private final int x;
		private final int y;
		private final int z;

		private ChunkVertexKey(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		private static ChunkVertexKey from(Renderer3DWorldChunkFrame.ChunkMesh chunk, int vertex) {
			int coord = vertex * POSITION_COMPONENT_COUNT;
			return new ChunkVertexKey(
				chunk.getVertexCoord(coord),
				chunk.getVertexCoord(coord + 1),
				chunk.getVertexCoord(coord + 2));
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof ChunkVertexKey)) {
				return false;
			}
			ChunkVertexKey key = (ChunkVertexKey) other;
			return x == key.x && y == key.y && z == key.z;
		}

		@Override
		public int hashCode() {
			int result = x;
			result = 31 * result + y;
			result = 31 * result + z;
			return result;
		}
	}

	private static final class SmoothDiffuseAccumulator {
		private int total;
		private int count;

		private void add(int diffuse) {
			total += diffuse;
			count++;
		}

		private int average() {
			return count <= 0 ? 0 : total / count;
		}
	}

	private static final class AlternateBufferMatch {
		private static final int NONE = 0;
		private static final int STORAGE_ONLY = 1;
		private static final int EQUIVALENT = 2;

		private AlternateBufferMatch() {
		}
	}

	private static final class UploadDiagnosticDetail {
		private final int maxChunksPerRole;
		private final int[] recordedByRole =
			new int[OpenGLWorldChunkUploadStats.ROLE_COUNT];
		private final int[] omittedByRole =
			new int[OpenGLWorldChunkUploadStats.ROLE_COUNT];
		private final StringBuilder detail = new StringBuilder();

		private UploadDiagnosticDetail(int maxChunksPerRole) {
			this.maxChunksPerRole =
				Math.max(1, maxChunksPerRole);
		}

		private void record(
			Renderer3DWorldChunkFrame.ChunkMesh chunk,
			String status,
			long uploadedBytes,
			long uploadNanos) {
			int roleIndex =
				OpenGLWorldChunkUploadStats.roleIndex(
					chunk.getChunkRole());
			if (recordedByRole[roleIndex]
					>= maxChunksPerRole) {
				omittedByRole[roleIndex]++;
				return;
			}
			recordedByRole[roleIndex]++;
			if (detail.length() > 0) {
				detail.append(';');
			}
			detail.append(roleLabel(roleIndex))
				.append("@p").append(chunk.getPlane())
				.append(":s")
				.append(chunk.getCenterSectionX())
				.append(',').append(chunk.getCenterSectionY())
				.append(":o")
				.append(chunk.getOriginWorldX())
				.append(',').append(chunk.getOriginWorldZ())
				.append(":sig")
				.append(Long.toHexString(
					chunk.getStorageSignature()))
				.append(":v").append(chunk.getVertexCount())
				.append(":i").append(chunk.getIndexCount())
				.append(':').append(status)
				.append(":bytes").append(uploadedBytes)
				.append(":nanos").append(uploadNanos);
		}

		private String summary() {
			if (!isTruncated()) {
				return detail.toString();
			}
			return detail.toString()
				+ ";omitted="
				+ omittedByRole[OpenGLWorldChunkUploadStats.ROLE_WORLD]
				+ ","
				+ omittedByRole[
					OpenGLWorldChunkUploadStats
						.ROLE_STATIC_OBJECTS]
				+ ","
				+ omittedByRole[
					OpenGLWorldChunkUploadStats
						.ROLE_ANIMATED_OBJECTS];
		}

		private boolean isTruncated() {
			for (int omitted : omittedByRole) {
				if (omitted > 0) {
					return true;
				}
			}
			return false;
		}

		private static String roleLabel(int roleIndex) {
			if (roleIndex
					== OpenGLWorldChunkUploadStats
						.ROLE_STATIC_OBJECTS) {
				return "static";
			}
			if (roleIndex
					== OpenGLWorldChunkUploadStats
						.ROLE_ANIMATED_OBJECTS) {
				return "animated";
			}
			return "world";
		}
	}

	private void copyChunkIndices(Renderer3DWorldChunkFrame.ChunkMesh chunk, int indexCount) {
		indexUploadBuffer.clear();
		for (int index = 0; index < indexCount; index++) {
			indexUploadBuffer.put(chunk.getIndex(index));
		}
		indexUploadBuffer.flip();
	}

	private int evictOverflow() throws Exception {
		int evictedChunks = 0;
		Iterator<Map.Entry<WorldChunkBufferKey, WorldChunkBuffer>> iterator = residentChunks.entrySet().iterator();
		while (residentChunks.size() > MAX_RESIDENT_CHUNKS && iterator.hasNext()) {
			Map.Entry<WorldChunkBufferKey, WorldChunkBuffer> eldest = iterator.next();
			eldest.getValue().close(gl);
			iterator.remove();
			evictedChunks++;
		}
		return evictedChunks;
	}

	void clearResidentWorldSession() throws Exception {
		if (closed) {
			return;
		}
		clearResidentResources();
	}

	@Override
	public void close() throws Exception {
		if (closed) {
			return;
		}
		closed = true;
		clearResidentResources();
	}

	private void clearResidentResources() throws Exception {
		for (WorldChunkBuffer buffer : residentChunks.values()) {
			buffer.close(gl);
		}
		residentChunks.clear();
		if (remasterShadowMaskTextureId != 0) {
			gl.glDeleteTextures(remasterShadowMaskTextureId);
			remasterShadowMaskTextureId = 0;
			remasterShadowMaskTextureWidth = 0;
			remasterShadowMaskTextureHeight = 0;
			remasterShadowMaskUploadedSignature = 0L;
		}
		if (remasterGlowMaskTextureId != 0) {
			gl.glDeleteTextures(remasterGlowMaskTextureId);
			remasterGlowMaskTextureId = 0;
			remasterGlowMaskTextureWidth = 0;
			remasterGlowMaskTextureHeight = 0;
			remasterGlowMaskUploadedSignature = 0L;
		}
		remasterShadowMaskBuilder.clear();
		remasterGlowMaskBuilder.clear();
		activeRemasterGlowMask = null;
		cachedRemasterShadowRoofCoverage = null;
		cachedRemasterShadowRoofCoverageSignature = 0L;
		cachedRemasterShadowRoofCoverageKnown = false;
		cachedRemasterShadowInventory = null;
		cachedRemasterShadowInventoryWorldSignature = 0L;
		cachedRemasterShadowInventoryObjectCasterSignature = 0L;
		cachedRemasterShadowInventoryKnown = false;
		remasterShadowMaskLastUpload = false;
		remasterShadowMaskLastUploadSkip = false;
	}
}




enum WorldChunkBatchBindResult {
	TEXTURED,
	FLAT_FALLBACK,
	UNTEXTURED,
	SKIPPED
}

final class WorldChunkBatchDrawState {
	static final WorldChunkBatchDrawState FLAT_FALLBACK =
		new WorldChunkBatchDrawState(WorldChunkBatchBindResult.FLAT_FALLBACK, -1, false);
	static final WorldChunkBatchDrawState UNTEXTURED =
		new WorldChunkBatchDrawState(WorldChunkBatchBindResult.UNTEXTURED, -1, false);
	static final WorldChunkBatchDrawState SKIPPED =
		new WorldChunkBatchDrawState(WorldChunkBatchBindResult.SKIPPED, -1, false);

	final WorldChunkBatchBindResult bindResult;
	final int textureId;
	final boolean textureEnabled;

	private WorldChunkBatchDrawState(
		WorldChunkBatchBindResult bindResult,
		int textureId,
		boolean textureEnabled) {
		this.bindResult = bindResult == null ? WorldChunkBatchBindResult.SKIPPED : bindResult;
		this.textureId = textureId;
		this.textureEnabled = textureEnabled;
	}

	static WorldChunkBatchDrawState textured(int textureId) {
		return new WorldChunkBatchDrawState(WorldChunkBatchBindResult.TEXTURED, textureId, true);
	}

	boolean isCompatibleWith(WorldChunkBatchDrawState other) {
		return other != null
			&& bindResult == other.bindResult
			&& textureId == other.textureId
			&& textureEnabled == other.textureEnabled;
	}
}

final class WorldChunkDrawRange {
	final int startIndex;
	final WorldChunkBatchDrawState drawState;
	int indexCount;

	WorldChunkDrawRange(WorldChunkMaterialBatch batch, WorldChunkBatchDrawState drawState) {
		this.startIndex = batch == null ? 0 : batch.startIndex;
		this.indexCount = batch == null ? 0 : batch.indexCount;
		this.drawState = drawState;
	}

	boolean canAppend(WorldChunkMaterialBatch batch, WorldChunkBatchDrawState nextState) {
		return batch != null
			&& drawState != null
			&& drawState.isCompatibleWith(nextState)
			&& batch.startIndex == startIndex + indexCount;
	}

	void append(WorldChunkMaterialBatch batch) {
		if (batch != null) {
			indexCount += batch.indexCount;
		}
	}
}

final class WorldChunkDrawAccumulator {
	final Set<WorldChunkBufferKey> consideredChunkKeys = new HashSet<WorldChunkBufferKey>();
	final Set<WorldChunkBufferKey> drawnChunkKeys = new HashSet<WorldChunkBufferKey>();
	int drawnTriangles;
	int drawnTerrainTriangles;
	int drawnWallTriangles;
	int drawnRoofTriangles;
	int drawnGameObjectTriangles;
	int drawnWallObjectTriangles;
	int drawnOtherTriangles;
	int fallbackTriangles;
	int skippedTriangles;
	int consideredBatches;
	int drawnBatches;
	int culledBatches;
	int drawCalls;
	int textureBinds;
	int boundTextureId = -1;
	int shadowProofChunks;
	int shadowProofIndices;

	void recordConsideredChunk(WorldChunkBufferKey key) {
		consideredChunkKeys.add(key);
	}

	void recordChunk(WorldChunkBufferKey key) {
		drawnChunkKeys.add(key);
	}

	int consideredChunkCount() {
		return consideredChunkKeys.size();
	}

	int drawnChunkCount() {
		return drawnChunkKeys.size();
	}

	int culledChunkCount() {
		return Math.max(0, consideredChunkCount() - drawnChunkCount());
	}

	void recordConsideredBatch() {
		consideredBatches++;
	}

	void recordDrawnBatch() {
		drawnBatches++;
	}

	void recordCulledBatch() {
		culledBatches++;
	}

	void recordDrawCall() {
		drawCalls++;
	}

	void recordTextureBind() {
		textureBinds++;
	}

	void recordBatch(Renderer3DModelKind kind, int triangles) {
		drawnTriangles += triangles;
		if (kind == Renderer3DModelKind.TERRAIN) {
			drawnTerrainTriangles += triangles;
		} else if (kind == Renderer3DModelKind.WALL) {
			drawnWallTriangles += triangles;
		} else if (kind == Renderer3DModelKind.ROOF) {
			drawnRoofTriangles += triangles;
		} else if (kind == Renderer3DModelKind.GAME_OBJECT) {
			drawnGameObjectTriangles += triangles;
		} else if (kind == Renderer3DModelKind.WALL_OBJECT) {
			drawnWallObjectTriangles += triangles;
		} else {
			drawnOtherTriangles += triangles;
		}
	}

	void recordFallbackBatch(int triangles) {
		fallbackTriangles += triangles;
	}

	void recordSkippedBatch(int triangles) {
		skippedTriangles += triangles;
	}

	void recordShadowProof(int chunks, int indices) {
		shadowProofChunks += chunks;
		shadowProofIndices += indices;
	}
}

final class WorldChunkMaterialBatch {
	final Renderer3DModelKind kind;
	final int textureId;
	final int fallbackColor;
	final int startIndex;
	final int indexCount;
	final int spatialX;
	final int spatialZ;
	final int minX;
	final int maxX;
	final int minY;
	final int maxY;
	final int minZ;
	final int maxZ;

	WorldChunkMaterialBatch(
		Renderer3DModelKind kind,
		int textureId,
		int fallbackColor,
		int startIndex,
		int indexCount,
		int spatialX,
		int spatialZ,
		int minX,
		int maxX,
		int minY,
		int maxY,
		int minZ,
		int maxZ) {
		this.kind = kind == null ? Renderer3DModelKind.UNCLASSIFIED : kind;
		this.textureId = textureId;
		this.fallbackColor = fallbackColor;
		this.startIndex = Math.max(0, startIndex);
		this.indexCount = Math.max(0, indexCount);
		this.spatialX = spatialX;
		this.spatialZ = spatialZ;
		this.minX = minX;
		this.maxX = maxX;
		this.minY = minY;
		this.maxY = maxY;
		this.minZ = minZ;
		this.maxZ = maxZ;
	}

	boolean hasSpatialBounds() {
		return spatialX != OpenGLWorldChunkRenderer.SPATIAL_BATCH_DISABLED
			&& spatialZ != OpenGLWorldChunkRenderer.SPATIAL_BATCH_DISABLED;
	}

	boolean hasBounds() {
		return indexCount > 0 && minX <= maxX && minY <= maxY && minZ <= maxZ;
	}
}

final class WorldChunkMaterialBatchBuilder {
	final WorldChunkMaterialKey key;
	int startIndex;
	int writeIndex;
	int indexCount;
	int minX = Integer.MAX_VALUE;
	int maxX = Integer.MIN_VALUE;
	int minY = Integer.MAX_VALUE;
	int maxY = Integer.MIN_VALUE;
	int minZ = Integer.MAX_VALUE;
	int maxZ = Integer.MIN_VALUE;

	WorldChunkMaterialBatchBuilder(WorldChunkMaterialKey key) {
		this.key = key;
	}

	WorldChunkMaterialBatch toBatch() {
		return new WorldChunkMaterialBatch(
			key.kind,
			key.textureId,
			key.fallbackColor,
			startIndex,
			indexCount,
			key.spatialX,
			key.spatialZ,
			minX == Integer.MAX_VALUE ? 0 : minX,
			maxX == Integer.MIN_VALUE ? 0 : maxX,
			minY == Integer.MAX_VALUE ? 0 : minY,
			maxY == Integer.MIN_VALUE ? 0 : maxY,
			minZ == Integer.MAX_VALUE ? 0 : minZ,
			maxZ == Integer.MIN_VALUE ? 0 : maxZ);
	}

	void includeTriangleBounds(Renderer3DWorldChunkFrame.ChunkMesh chunk, int triangle) {
		int sourceIndex = triangle * 3;
		for (int index = 0; index < 3; index++) {
			int vertex = chunk.getIndex(sourceIndex + index);
			int coord = vertex * OpenGLWorldChunkRenderer.POSITION_COMPONENT_COUNT;
			int x = chunk.getVertexCoord(coord);
			int y = chunk.getVertexCoord(coord + 1);
			int z = chunk.getVertexCoord(coord + 2);
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
			minZ = Math.min(minZ, z);
			maxZ = Math.max(maxZ, z);
		}
	}
}

final class WorldChunkMaterialKey {
	final Renderer3DModelKind kind;
	final int textureId;
	final int fallbackColor;
	final int spatialX;
	final int spatialZ;

	WorldChunkMaterialKey(
		Renderer3DModelKind kind,
		int textureId,
		int fallbackColor,
		int spatialX,
		int spatialZ) {
		this.kind = kind == null ? Renderer3DModelKind.UNCLASSIFIED : kind;
		this.textureId = textureId;
		this.fallbackColor = fallbackColor;
		this.spatialX = spatialX;
		this.spatialZ = spatialZ;
	}

	static WorldChunkMaterialKey from(
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		int triangle,
		boolean spatialBatches) {
		int spatialX = OpenGLWorldChunkRenderer.SPATIAL_BATCH_DISABLED;
		int spatialZ = OpenGLWorldChunkRenderer.SPATIAL_BATCH_DISABLED;
		if (spatialBatches) {
			int sourceIndex = triangle * 3;
			int xTotal = 0;
			int zTotal = 0;
			for (int index = 0; index < 3; index++) {
				int vertex = chunk.getIndex(sourceIndex + index);
				int coord = vertex * OpenGLWorldChunkRenderer.POSITION_COMPONENT_COUNT;
				xTotal += chunk.getVertexCoord(coord);
				zTotal += chunk.getVertexCoord(coord + 2);
			}
			spatialX = spatialCell(xTotal / 3);
			spatialZ = spatialCell(zTotal / 3);
		}
		return new WorldChunkMaterialKey(
			chunk.getTriangleModelKind(triangle),
			chunk.getTriangleTexture(triangle),
			chunk.getTriangleFallbackColor(triangle),
			spatialX,
			spatialZ);
	}

	static int spatialCell(int coordinate) {
		int cell = coordinate / OpenGLWorldChunkRenderer.SPATIAL_BATCH_WORLD_SIZE;
		if (cell < 0) {
			return 0;
		}
		if (cell >= OpenGLWorldChunkRenderer.SPATIAL_BATCH_AXIS) {
			return OpenGLWorldChunkRenderer.SPATIAL_BATCH_AXIS - 1;
		}
		return cell;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof WorldChunkMaterialKey)) {
			return false;
		}
		WorldChunkMaterialKey key = (WorldChunkMaterialKey) other;
		return kind == key.kind
			&& textureId == key.textureId
			&& fallbackColor == key.fallbackColor
			&& spatialX == key.spatialX
			&& spatialZ == key.spatialZ;
	}

	@Override
	public int hashCode() {
		int result = kind.hashCode();
		result = 31 * result + textureId;
		result = 31 * result + fallbackColor;
		result = 31 * result + spatialX;
		result = 31 * result + spatialZ;
		return result;
	}
}

final class WorldChunkBufferKey {
	final int plane;
	final int centerSectionX;
	final int centerSectionY;
	final int originWorldX;
	final int originWorldZ;
	final boolean objectOnly;
	final int chunkRole;
	final boolean drawOffsetStorage;
	final long storageSignature;

	WorldChunkBufferKey(
		int plane,
		int centerSectionX,
		int centerSectionY,
		int originWorldX,
		int originWorldZ,
		boolean objectOnly,
		int chunkRole,
		boolean drawOffsetStorage,
		long storageSignature) {
		this.plane = plane;
		this.centerSectionX = centerSectionX;
		this.centerSectionY = centerSectionY;
		this.originWorldX = originWorldX;
		this.originWorldZ = originWorldZ;
		this.objectOnly = objectOnly;
		this.chunkRole = chunkRole;
		this.drawOffsetStorage = drawOffsetStorage;
		this.storageSignature = storageSignature;
	}

	static WorldChunkBufferKey from(Renderer3DWorldChunkFrame.ChunkMesh chunk) {
		return from(chunk, false);
	}

	static WorldChunkBufferKey from(
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		boolean drawOffsetSupported) {
		/*
		 * The resident shader applies presentation rebases as a draw offset.
		 * Its VBO therefore owns immutable mesh storage, not the current
		 * section-center/origin presentation. Keeping positional keys on that
		 * path made every adjacent rebase upload unchanged storage again. The
		 * fixed-function path cannot apply an offset and deliberately retains
		 * the original position-specific identity.
		 */
		return new WorldChunkBufferKey(
			chunk.getPlane(),
			drawOffsetSupported ? 0 : chunk.getCenterSectionX(),
			drawOffsetSupported ? 0 : chunk.getCenterSectionY(),
			drawOffsetSupported ? 0 : chunk.getOriginWorldX(),
			drawOffsetSupported ? 0 : chunk.getOriginWorldZ(),
			chunk.isObjectChunk(),
			chunk.getChunkRole(),
			drawOffsetSupported,
			drawOffsetSupported ? chunk.getStorageSignature() : 0L);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof WorldChunkBufferKey)) {
			return false;
		}
		WorldChunkBufferKey key = (WorldChunkBufferKey) other;
		if (plane != key.plane
			|| objectOnly != key.objectOnly
			|| chunkRole != key.chunkRole
			|| drawOffsetStorage != key.drawOffsetStorage) {
			return false;
		}
		if (drawOffsetStorage) {
			return storageSignature == key.storageSignature;
		}
		return centerSectionX == key.centerSectionX
			&& centerSectionY == key.centerSectionY
			&& originWorldX == key.originWorldX
			&& originWorldZ == key.originWorldZ;
	}

	@Override
	public int hashCode() {
		int result = plane;
		result = 31 * result + centerSectionX;
		result = 31 * result + centerSectionY;
		result = 31 * result + originWorldX;
		result = 31 * result + originWorldZ;
		result = 31 * result + (objectOnly ? 1 : 0);
		result = 31 * result + chunkRole;
		result = 31 * result + (drawOffsetStorage ? 1 : 0);
		result = 31 * result
			+ (int) (storageSignature ^ (storageSignature >>> 32));
		return result;
	}
}

final class WorldChunkBuffer {
	final int vertexBufferId;
	final int indexBufferId;
	final int materialIndexBufferId;
	long signature;
	int uploadedVertexOffsetX;
	int uploadedVertexOffsetZ;
	int vertexCount;
	int indexCount;
	int triangleCount;
	boolean atlasTextureCoordinates;
	int brightnessBits;
	int fogModeBits;
	int lightingModeBits;
	int geometryModeBits;
	boolean replacementCompositeDrawOnly;
	WorldChunkMaterialBatch[] materialBatches = new WorldChunkMaterialBatch[0];
	long shadowProofSignature;
	long uploadedByteCount;

	WorldChunkBuffer(int vertexBufferId, int indexBufferId, int materialIndexBufferId) {
		this.vertexBufferId = vertexBufferId;
		this.indexBufferId = indexBufferId;
		this.materialIndexBufferId = materialIndexBufferId;
	}

	boolean matches(
		long signature,
		int vertexCount,
		int indexCount,
		int triangleCount,
		boolean atlasTextureCoordinates,
		int brightnessBits,
		int fogModeBits,
		int lightingModeBits,
		int geometryModeBits,
		boolean replacementCompositeDrawOnly,
		long shadowProofSignature) {
		return this.signature == signature
			&& this.vertexCount == vertexCount
			&& this.indexCount == indexCount
			&& this.triangleCount == triangleCount
			&& this.atlasTextureCoordinates == atlasTextureCoordinates
			&& this.brightnessBits == brightnessBits
			&& this.fogModeBits == fogModeBits
			&& this.lightingModeBits == lightingModeBits
			&& this.geometryModeBits == geometryModeBits
			&& this.replacementCompositeDrawOnly == replacementCompositeDrawOnly
			&& this.shadowProofSignature == shadowProofSignature;
	}

	String mismatchReason(
		long signature,
		int vertexCount,
		int indexCount,
		int triangleCount,
		boolean atlasTextureCoordinates,
		int brightnessBits,
		int fogModeBits,
		int lightingModeBits,
		int geometryModeBits,
		boolean replacementCompositeDrawOnly,
		long shadowProofSignature,
		boolean objectChunk,
		int chunkRole) {
		if (this.signature != signature) {
			if (!objectChunk) {
				return "static-signature";
			}
			return chunkRole == Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS
				? "animated-object-signature"
				: "static-object-signature";
		}
		if (this.vertexCount != vertexCount) {
			return objectChunk ? "object-vertices" : "static-vertices";
		}
		if (this.indexCount != indexCount) {
			return objectChunk ? "object-indices" : "static-indices";
		}
		if (this.triangleCount != triangleCount) {
			return objectChunk ? "object-triangles" : "static-triangles";
		}
		if (this.atlasTextureCoordinates != atlasTextureCoordinates) {
			return "texture-coords";
		}
		if (this.brightnessBits != brightnessBits) {
			return "brightness";
		}
		if (this.fogModeBits != fogModeBits) {
			return "fog";
		}
		if (this.lightingModeBits != lightingModeBits) {
			return "lighting";
		}
		if (this.geometryModeBits != geometryModeBits) {
			return "geometry";
		}
		if (this.replacementCompositeDrawOnly != replacementCompositeDrawOnly) {
			return "replacement-mode";
		}
		if (this.shadowProofSignature != shadowProofSignature) {
			return "shadow-proof";
		}
		return "unknown";
	}

	void close(LwjglBindings gl) throws Exception {
		gl.glDeleteBuffers(vertexBufferId);
		gl.glDeleteBuffers(indexBufferId);
		gl.glDeleteBuffers(materialIndexBufferId);
	}
}
