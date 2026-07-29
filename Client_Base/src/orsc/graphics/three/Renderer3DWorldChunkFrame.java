package orsc.graphics.three;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Renderer3DWorldChunkFrame {
	public static final Renderer3DWorldChunkFrame EMPTY =
		new Renderer3DWorldChunkFrame(Collections.<ChunkMesh>emptyList(), 0, 0, 0);
	public static final int CHUNK_ROLE_WORLD = 0;
	public static final int CHUNK_ROLE_STATIC_OBJECTS = 1;
	public static final int CHUNK_ROLE_ANIMATED_OBJECTS = 2;
	private static final int TILE_SIZE = 128;

	private final List<ChunkMesh> chunks;
	private final int totalVertexCount;
	private final int totalIndexCount;
	private final int totalTriangleCount;
	private final int[] materialFamilyTriangleCounts;
	private final boolean hasVertexBounds;
	private final int minVertexX;
	private final int maxVertexX;
	private final int minVertexZ;
	private final int maxVertexZ;

	private Renderer3DWorldChunkFrame(
		List<ChunkMesh> chunks,
		int totalVertexCount,
		int totalIndexCount,
		int totalTriangleCount) {
		this.chunks = chunks;
		this.totalVertexCount = totalVertexCount;
		this.totalIndexCount = totalIndexCount;
		this.totalTriangleCount = totalTriangleCount;
		this.materialFamilyTriangleCounts = new int[Renderer3DMaterialFamily.values().length];
		boolean foundVertexBounds = false;
		int minimumVertexX = Integer.MAX_VALUE;
		int maximumVertexX = Integer.MIN_VALUE;
		int minimumVertexZ = Integer.MAX_VALUE;
		int maximumVertexZ = Integer.MIN_VALUE;
		for (ChunkMesh chunk : chunks) {
			for (Renderer3DMaterialFamily family : Renderer3DMaterialFamily.values()) {
				this.materialFamilyTriangleCounts[family.ordinal()] +=
					chunk.getMaterialFamilyTriangleCount(family);
			}
			if (chunk.hasVertexBounds()) {
				foundVertexBounds = true;
				minimumVertexX = Math.min(minimumVertexX, chunk.getMinVertexX());
				maximumVertexX = Math.max(maximumVertexX, chunk.getMaxVertexX());
				minimumVertexZ = Math.min(minimumVertexZ, chunk.getMinVertexZ());
				maximumVertexZ = Math.max(maximumVertexZ, chunk.getMaxVertexZ());
			}
		}
		this.hasVertexBounds = foundVertexBounds;
		this.minVertexX = foundVertexBounds ? minimumVertexX : 0;
		this.maxVertexX = foundVertexBounds ? maximumVertexX : 0;
		this.minVertexZ = foundVertexBounds ? minimumVertexZ : 0;
		this.maxVertexZ = foundVertexBounds ? maximumVertexZ : 0;
	}

	public static Renderer3DWorldChunkFrame fromChunks(List<ChunkMesh> chunks) {
		if (chunks == null || chunks.isEmpty()) {
			return EMPTY;
		}

		List<ChunkMesh> chunkCopy = new ArrayList<ChunkMesh>(chunks);
		int totalVertexCount = 0;
		int totalIndexCount = 0;
		int totalTriangleCount = 0;
		for (ChunkMesh chunk : chunkCopy) {
			totalVertexCount += chunk.getVertexCount();
			totalIndexCount += chunk.getIndexCount();
			totalTriangleCount += chunk.getTriangleCount();
		}
		return new Renderer3DWorldChunkFrame(
			Collections.unmodifiableList(chunkCopy),
			totalVertexCount,
			totalIndexCount,
			totalTriangleCount);
	}

	public List<ChunkMesh> getChunks() {
		return chunks;
	}

	public int getChunkCount() {
		return chunks.size();
	}

	public int getTotalVertexCount() {
		return totalVertexCount;
	}

	public int getTotalIndexCount() {
		return totalIndexCount;
	}

	public int getTotalTriangleCount() {
		return totalTriangleCount;
	}

	public boolean hasVertexBounds() {
		return hasVertexBounds;
	}

	public int getMinVertexX() {
		return minVertexX;
	}

	public int getMaxVertexX() {
		return maxVertexX;
	}

	public int getMinVertexZ() {
		return minVertexZ;
	}

	public int getMaxVertexZ() {
		return maxVertexZ;
	}

	public int getMaterialFamilyTriangleCount(Renderer3DMaterialFamily family) {
		Renderer3DMaterialFamily safeFamily = family == null
			? Renderer3DMaterialFamily.UNCLASSIFIED
			: family;
		return materialFamilyTriangleCounts[safeFamily.ordinal()];
	}

	public int[] copyMaterialFamilyTriangleCounts() {
		return materialFamilyTriangleCounts.clone();
	}

	public static final class ChunkMesh {
		private final int plane;
		private final int centerSectionX;
		private final int centerSectionY;
		private final int originWorldX;
		private final int originWorldZ;
		private final int[] vertexCoords;
		private final int vertexOffsetX;
		private final int vertexOffsetZ;
		private boolean hasVertexBounds;
		private int minVertexX;
		private int maxVertexX;
		private int minVertexZ;
		private int maxVertexZ;
		private final float[] vertexTextureU;
		private final float[] vertexTextureV;
		private final int[] vertexLights;
		private final int[] vertexNormalX;
		private final int[] vertexNormalY;
		private final int[] vertexNormalZ;
		private final int[] vertexTerrainBlendColors;
		private final int[] vertexTerrainBlendStrengths;
		private final int[] indices;
		private final int[] triangleTextures;
		private final int[] triangleFallbackColors;
		private final Renderer3DModelKind[] triangleModelKinds;
		private Renderer3DMaterialFamily[] triangleMaterialFamilies;
		private int[] materialFamilyTriangleCounts;
		private final int[] triangleTerrainVariationMasks;
		private final ShadowCaster[] shadowCasters;
		private final GlowEmitter[] glowEmitters;
		private final long[] roofCoverageBits;
		private final int roofCoverageAxis;
		private final int roofCoveredTileCount;
		private final int terrainTriangles;
		private final int wallTriangles;
		private final int roofTriangles;
		private final boolean objectChunk;
		private final int chunkRole;
		private final long signature;
		private int worldEditorTerrainGridAxis;
		private int[] worldEditorTerrainGridHeights = new int[0];
		private int worldEditorTerrainGridSignature;

		public ChunkMesh(
			int plane,
			int centerSectionX,
			int centerSectionY,
			int originWorldX,
			int originWorldZ,
			int[] vertexCoords,
			float[] vertexTextureU,
			float[] vertexTextureV,
			int[] vertexLights,
			int[] indices,
			int[] triangleTextures,
			int[] triangleFallbackColors,
			Renderer3DModelKind[] triangleModelKinds,
			int terrainTriangles,
			int wallTriangles,
			int roofTriangles,
			long signature) {
			this(
				plane,
				centerSectionX,
				centerSectionY,
				originWorldX,
				originWorldZ,
				vertexCoords,
				vertexTextureU,
				vertexTextureV,
				vertexLights,
				indices,
				triangleTextures,
				triangleFallbackColors,
				triangleModelKinds,
				null,
				terrainTriangles,
				wallTriangles,
				roofTriangles,
				false,
				CHUNK_ROLE_WORLD,
				signature);
		}

		public ChunkMesh(
			int plane,
			int centerSectionX,
			int centerSectionY,
			int originWorldX,
			int originWorldZ,
			int[] vertexCoords,
			float[] vertexTextureU,
			float[] vertexTextureV,
			int[] vertexLights,
			int[] indices,
			int[] triangleTextures,
			int[] triangleFallbackColors,
			Renderer3DModelKind[] triangleModelKinds,
			int terrainTriangles,
			int wallTriangles,
			int roofTriangles,
			boolean objectChunk,
			long signature) {
			this(
				plane,
				centerSectionX,
				centerSectionY,
				originWorldX,
				originWorldZ,
				vertexCoords,
				vertexTextureU,
				vertexTextureV,
				vertexLights,
				indices,
				triangleTextures,
				triangleFallbackColors,
				triangleModelKinds,
				null,
				terrainTriangles,
				wallTriangles,
				roofTriangles,
				objectChunk,
				objectChunk ? CHUNK_ROLE_STATIC_OBJECTS : CHUNK_ROLE_WORLD,
				signature);
		}

		public ChunkMesh(
			int plane,
			int centerSectionX,
			int centerSectionY,
			int originWorldX,
			int originWorldZ,
			int[] vertexCoords,
			float[] vertexTextureU,
			float[] vertexTextureV,
			int[] vertexLights,
			int[] indices,
			int[] triangleTextures,
			int[] triangleFallbackColors,
			Renderer3DModelKind[] triangleModelKinds,
			ShadowCaster[] shadowCasters,
			int terrainTriangles,
			int wallTriangles,
			int roofTriangles,
			boolean objectChunk,
			long signature) {
			this(
				plane,
				centerSectionX,
				centerSectionY,
				originWorldX,
				originWorldZ,
				vertexCoords,
				vertexTextureU,
				vertexTextureV,
				vertexLights,
				indices,
				triangleTextures,
				triangleFallbackColors,
				triangleModelKinds,
				shadowCasters,
				terrainTriangles,
				wallTriangles,
				roofTriangles,
				objectChunk,
				objectChunk ? CHUNK_ROLE_STATIC_OBJECTS : CHUNK_ROLE_WORLD,
				signature);
		}

		public ChunkMesh(
			int plane,
			int centerSectionX,
			int centerSectionY,
			int originWorldX,
			int originWorldZ,
			int[] vertexCoords,
			float[] vertexTextureU,
			float[] vertexTextureV,
			int[] vertexLights,
			int[] indices,
			int[] triangleTextures,
			int[] triangleFallbackColors,
			Renderer3DModelKind[] triangleModelKinds,
			ShadowCaster[] shadowCasters,
			int terrainTriangles,
			int wallTriangles,
			int roofTriangles,
			boolean objectChunk,
			int chunkRole,
			long signature) {
			this(
				plane,
				centerSectionX,
				centerSectionY,
				originWorldX,
				originWorldZ,
				vertexCoords,
				vertexTextureU,
				vertexTextureV,
				vertexLights,
				indices,
				triangleTextures,
				triangleFallbackColors,
				triangleModelKinds,
				shadowCasters,
				null,
				terrainTriangles,
				wallTriangles,
				roofTriangles,
				objectChunk,
				chunkRole,
				signature);
		}

		public ChunkMesh(
			int plane,
			int centerSectionX,
			int centerSectionY,
			int originWorldX,
			int originWorldZ,
			int[] vertexCoords,
			float[] vertexTextureU,
			float[] vertexTextureV,
			int[] vertexLights,
			int[] indices,
			int[] triangleTextures,
			int[] triangleFallbackColors,
			Renderer3DModelKind[] triangleModelKinds,
			ShadowCaster[] shadowCasters,
			GlowEmitter[] glowEmitters,
			int terrainTriangles,
			int wallTriangles,
			int roofTriangles,
			boolean objectChunk,
			int chunkRole,
			long signature) {
			this.plane = plane;
			this.centerSectionX = centerSectionX;
			this.centerSectionY = centerSectionY;
			this.originWorldX = originWorldX;
			this.originWorldZ = originWorldZ;
			this.vertexCoords = vertexCoords == null ? new int[0] : vertexCoords.clone();
			this.vertexOffsetX = 0;
			this.vertexOffsetZ = 0;
			initializeVertexBounds();
			int vertexCount = this.vertexCoords.length / 3;
			this.vertexTextureU = normalizeFloatArray(vertexTextureU, vertexCount, 0.0f);
			this.vertexTextureV = normalizeFloatArray(vertexTextureV, vertexCount, 0.0f);
			this.vertexLights = normalizeIntArray(vertexLights, vertexCount, 0);
			this.vertexTerrainBlendColors = normalizeIntArray(null, vertexCount, 0);
			this.vertexTerrainBlendStrengths = normalizeIntArray(null, vertexCount, 0);
			this.indices = indices == null ? new int[0] : indices.clone();
			this.triangleTextures = triangleTextures == null ? new int[0] : triangleTextures.clone();
			this.triangleFallbackColors =
				triangleFallbackColors == null ? new int[0] : triangleFallbackColors.clone();
			this.triangleModelKinds = normalizeKinds(triangleModelKinds, this.triangleTextures.length);
			this.triangleMaterialFamilies = normalizeFamilies(
				null,
				this.triangleModelKinds,
				this.triangleTextures.length);
			this.materialFamilyTriangleCounts = countFamilies(this.triangleMaterialFamilies);
			this.triangleTerrainVariationMasks = normalizeIntArray(null, this.triangleTextures.length, 0);
			int[][] vertexNormals = buildVertexNormals(
				this.vertexCoords,
				this.indices,
				this.triangleTextures.length,
				this.triangleModelKinds);
			this.vertexNormalX = vertexNormals[0];
			this.vertexNormalY = vertexNormals[1];
			this.vertexNormalZ = vertexNormals[2];
			this.shadowCasters = normalizeShadowCasters(shadowCasters);
			this.glowEmitters = normalizeGlowEmitters(glowEmitters);
			this.roofCoverageBits = new long[0];
			this.roofCoverageAxis = 0;
			this.roofCoveredTileCount = 0;
			this.terrainTriangles = terrainTriangles;
			this.wallTriangles = wallTriangles;
			this.roofTriangles = roofTriangles;
			this.objectChunk = objectChunk;
			this.chunkRole = normalizeChunkRole(objectChunk, chunkRole);
			this.signature = signature;
		}

		public ChunkMesh(
			int plane,
			int centerSectionX,
			int centerSectionY,
			int originWorldX,
			int originWorldZ,
			int[] vertexCoords,
			float[] vertexTextureU,
			float[] vertexTextureV,
			int[] vertexLights,
			int[] indices,
			int[] triangleTextures,
			int[] triangleFallbackColors,
			Renderer3DModelKind[] triangleModelKinds,
			ShadowCaster[] shadowCasters,
			long[] roofCoverageBits,
			int roofCoverageAxis,
			int roofCoveredTileCount,
			int terrainTriangles,
			int wallTriangles,
			int roofTriangles,
			boolean objectChunk,
			long signature) {
			this(
				plane,
				centerSectionX,
				centerSectionY,
				originWorldX,
				originWorldZ,
				vertexCoords,
				vertexTextureU,
				vertexTextureV,
				vertexLights,
				null,
				null,
				indices,
				triangleTextures,
				triangleFallbackColors,
				triangleModelKinds,
				shadowCasters,
				null,
				null,
				roofCoverageBits,
				roofCoverageAxis,
				roofCoveredTileCount,
				terrainTriangles,
				wallTriangles,
				roofTriangles,
				objectChunk,
				objectChunk ? CHUNK_ROLE_STATIC_OBJECTS : CHUNK_ROLE_WORLD,
				signature);
		}

		public ChunkMesh(
			int plane,
			int centerSectionX,
			int centerSectionY,
			int originWorldX,
			int originWorldZ,
			int[] vertexCoords,
			float[] vertexTextureU,
			float[] vertexTextureV,
			int[] vertexLights,
			int[] vertexTerrainBlendColors,
			int[] vertexTerrainBlendStrengths,
			int[] indices,
			int[] triangleTextures,
			int[] triangleFallbackColors,
			Renderer3DModelKind[] triangleModelKinds,
			ShadowCaster[] shadowCasters,
			GlowEmitter[] glowEmitters,
			int[] triangleTerrainVariationMasks,
			long[] roofCoverageBits,
			int roofCoverageAxis,
			int roofCoveredTileCount,
			int terrainTriangles,
			int wallTriangles,
			int roofTriangles,
			boolean objectChunk,
			long signature) {
			this(
				plane,
				centerSectionX,
				centerSectionY,
				originWorldX,
				originWorldZ,
				vertexCoords,
				vertexTextureU,
				vertexTextureV,
				vertexLights,
				vertexTerrainBlendColors,
				vertexTerrainBlendStrengths,
				indices,
				triangleTextures,
				triangleFallbackColors,
				triangleModelKinds,
				shadowCasters,
				glowEmitters,
				triangleTerrainVariationMasks,
				roofCoverageBits,
				roofCoverageAxis,
				roofCoveredTileCount,
				terrainTriangles,
				wallTriangles,
				roofTriangles,
				objectChunk,
				objectChunk ? CHUNK_ROLE_STATIC_OBJECTS : CHUNK_ROLE_WORLD,
				signature);
		}

		public ChunkMesh(
			int plane,
			int centerSectionX,
			int centerSectionY,
			int originWorldX,
			int originWorldZ,
			int[] vertexCoords,
			float[] vertexTextureU,
			float[] vertexTextureV,
			int[] vertexLights,
			int[] vertexTerrainBlendColors,
			int[] vertexTerrainBlendStrengths,
			int[] indices,
			int[] triangleTextures,
			int[] triangleFallbackColors,
			Renderer3DModelKind[] triangleModelKinds,
			ShadowCaster[] shadowCasters,
			int[] triangleTerrainVariationMasks,
			long[] roofCoverageBits,
			int roofCoverageAxis,
			int roofCoveredTileCount,
			int terrainTriangles,
			int wallTriangles,
			int roofTriangles,
			boolean objectChunk,
			long signature) {
			this(
				plane,
				centerSectionX,
				centerSectionY,
				originWorldX,
				originWorldZ,
				vertexCoords,
				vertexTextureU,
				vertexTextureV,
				vertexLights,
				vertexTerrainBlendColors,
				vertexTerrainBlendStrengths,
				indices,
				triangleTextures,
				triangleFallbackColors,
				triangleModelKinds,
				shadowCasters,
				null,
				triangleTerrainVariationMasks,
				roofCoverageBits,
				roofCoverageAxis,
				roofCoveredTileCount,
				terrainTriangles,
				wallTriangles,
				roofTriangles,
				objectChunk,
				signature);
		}

		public ChunkMesh(
			int plane,
			int centerSectionX,
			int centerSectionY,
			int originWorldX,
			int originWorldZ,
			int[] vertexCoords,
			float[] vertexTextureU,
			float[] vertexTextureV,
			int[] vertexLights,
			int[] vertexTerrainBlendColors,
			int[] vertexTerrainBlendStrengths,
			int[] indices,
			int[] triangleTextures,
			int[] triangleFallbackColors,
			Renderer3DModelKind[] triangleModelKinds,
			ShadowCaster[] shadowCasters,
			int[] triangleTerrainVariationMasks,
			long[] roofCoverageBits,
			int roofCoverageAxis,
			int roofCoveredTileCount,
			int terrainTriangles,
			int wallTriangles,
			int roofTriangles,
			boolean objectChunk,
			int chunkRole,
			long signature) {
			this(
				plane,
				centerSectionX,
				centerSectionY,
				originWorldX,
				originWorldZ,
				vertexCoords,
				vertexTextureU,
				vertexTextureV,
				vertexLights,
				vertexTerrainBlendColors,
				vertexTerrainBlendStrengths,
				indices,
				triangleTextures,
				triangleFallbackColors,
				triangleModelKinds,
				shadowCasters,
				null,
				triangleTerrainVariationMasks,
				roofCoverageBits,
				roofCoverageAxis,
				roofCoveredTileCount,
				terrainTriangles,
				wallTriangles,
				roofTriangles,
				objectChunk,
				chunkRole,
				signature);
		}

		public ChunkMesh(
			int plane,
			int centerSectionX,
			int centerSectionY,
			int originWorldX,
			int originWorldZ,
			int[] vertexCoords,
			float[] vertexTextureU,
			float[] vertexTextureV,
			int[] vertexLights,
			int[] vertexTerrainBlendColors,
			int[] vertexTerrainBlendStrengths,
			int[] indices,
			int[] triangleTextures,
			int[] triangleFallbackColors,
			Renderer3DModelKind[] triangleModelKinds,
			ShadowCaster[] shadowCasters,
			GlowEmitter[] glowEmitters,
			int[] triangleTerrainVariationMasks,
			long[] roofCoverageBits,
			int roofCoverageAxis,
			int roofCoveredTileCount,
			int terrainTriangles,
			int wallTriangles,
			int roofTriangles,
			boolean objectChunk,
			int chunkRole,
			long signature) {
			this.plane = plane;
			this.centerSectionX = centerSectionX;
			this.centerSectionY = centerSectionY;
			this.originWorldX = originWorldX;
			this.originWorldZ = originWorldZ;
			this.vertexCoords = vertexCoords == null ? new int[0] : vertexCoords.clone();
			this.vertexOffsetX = 0;
			this.vertexOffsetZ = 0;
			initializeVertexBounds();
			int vertexCount = this.vertexCoords.length / 3;
			this.vertexTextureU = normalizeFloatArray(vertexTextureU, vertexCount, 0.0f);
			this.vertexTextureV = normalizeFloatArray(vertexTextureV, vertexCount, 0.0f);
			this.vertexLights = normalizeIntArray(vertexLights, vertexCount, 0);
			this.vertexTerrainBlendColors = normalizeIntArray(vertexTerrainBlendColors, vertexCount, 0);
			this.vertexTerrainBlendStrengths = normalizeIntArray(vertexTerrainBlendStrengths, vertexCount, 0);
			this.indices = indices == null ? new int[0] : indices.clone();
			this.triangleTextures = triangleTextures == null ? new int[0] : triangleTextures.clone();
			this.triangleFallbackColors =
				triangleFallbackColors == null ? new int[0] : triangleFallbackColors.clone();
			this.triangleModelKinds = normalizeKinds(triangleModelKinds, this.triangleTextures.length);
			this.triangleMaterialFamilies = normalizeFamilies(
				null,
				this.triangleModelKinds,
				this.triangleTextures.length);
			this.materialFamilyTriangleCounts = countFamilies(this.triangleMaterialFamilies);
			this.triangleTerrainVariationMasks =
				normalizeIntArray(triangleTerrainVariationMasks, this.triangleTextures.length, 0);
			int[][] vertexNormals = buildVertexNormals(
				this.vertexCoords,
				this.indices,
				this.triangleTextures.length,
				this.triangleModelKinds);
			this.vertexNormalX = vertexNormals[0];
			this.vertexNormalY = vertexNormals[1];
			this.vertexNormalZ = vertexNormals[2];
			this.shadowCasters = normalizeShadowCasters(shadowCasters);
			this.glowEmitters = normalizeGlowEmitters(glowEmitters);
			this.roofCoverageAxis = roofCoverageAxis <= 0 || roofCoverageBits == null ? 0 : roofCoverageAxis;
			this.roofCoverageBits = this.roofCoverageAxis <= 0 ? new long[0] : roofCoverageBits.clone();
			this.roofCoveredTileCount = Math.max(0, roofCoveredTileCount);
			this.terrainTriangles = terrainTriangles;
			this.wallTriangles = wallTriangles;
			this.roofTriangles = roofTriangles;
			this.objectChunk = objectChunk;
			this.chunkRole = normalizeChunkRole(objectChunk, chunkRole);
			this.signature = signature;
		}

		public ChunkMesh(
			int plane,
			int centerSectionX,
			int centerSectionY,
			int originWorldX,
			int originWorldZ,
			int[] vertexCoords,
			float[] vertexTextureU,
			float[] vertexTextureV,
			int[] vertexLights,
			int[] vertexTerrainBlendColors,
			int[] vertexTerrainBlendStrengths,
			int[] indices,
			int[] triangleTextures,
			int[] triangleFallbackColors,
			Renderer3DModelKind[] triangleModelKinds,
			Renderer3DMaterialFamily[] triangleMaterialFamilies,
			ShadowCaster[] shadowCasters,
			GlowEmitter[] glowEmitters,
			int[] triangleTerrainVariationMasks,
			long[] roofCoverageBits,
			int roofCoverageAxis,
			int roofCoveredTileCount,
			int terrainTriangles,
			int wallTriangles,
			int roofTriangles,
			boolean objectChunk,
			int chunkRole,
			long signature) {
			this(
				plane,
				centerSectionX,
				centerSectionY,
				originWorldX,
				originWorldZ,
				vertexCoords,
				vertexTextureU,
				vertexTextureV,
				vertexLights,
				vertexTerrainBlendColors,
				vertexTerrainBlendStrengths,
				indices,
				triangleTextures,
				triangleFallbackColors,
				triangleModelKinds,
				shadowCasters,
				glowEmitters,
				triangleTerrainVariationMasks,
				roofCoverageBits,
				roofCoverageAxis,
				roofCoveredTileCount,
				terrainTriangles,
				wallTriangles,
				roofTriangles,
				objectChunk,
				chunkRole,
				signature);
			this.triangleMaterialFamilies = normalizeFamilies(
				triangleMaterialFamilies,
				this.triangleModelKinds,
				this.triangleTextures.length);
			this.materialFamilyTriangleCounts = countFamilies(this.triangleMaterialFamilies);
		}

		private ChunkMesh(
			ChunkMesh source,
			int additionalOffsetX,
			int additionalOffsetZ) {
			this.plane = source.plane;
			this.centerSectionX = source.centerSectionX;
			this.centerSectionY = source.centerSectionY;
			this.originWorldX = source.originWorldX;
			this.originWorldZ = source.originWorldZ;
			this.vertexCoords = source.vertexCoords;
			this.vertexOffsetX = Math.addExact(
				source.vertexOffsetX, additionalOffsetX);
			this.vertexOffsetZ = Math.addExact(
				source.vertexOffsetZ, additionalOffsetZ);
			this.hasVertexBounds = source.hasVertexBounds;
			this.minVertexX = source.hasVertexBounds
				? Math.addExact(source.minVertexX, additionalOffsetX)
				: 0;
			this.maxVertexX = source.hasVertexBounds
				? Math.addExact(source.maxVertexX, additionalOffsetX)
				: 0;
			this.minVertexZ = source.hasVertexBounds
				? Math.addExact(source.minVertexZ, additionalOffsetZ)
				: 0;
			this.maxVertexZ = source.hasVertexBounds
				? Math.addExact(source.maxVertexZ, additionalOffsetZ)
				: 0;
			this.vertexTextureU = source.vertexTextureU;
			this.vertexTextureV = source.vertexTextureV;
			this.vertexLights = source.vertexLights;
			this.vertexNormalX = source.vertexNormalX;
			this.vertexNormalY = source.vertexNormalY;
			this.vertexNormalZ = source.vertexNormalZ;
			this.vertexTerrainBlendColors =
				source.vertexTerrainBlendColors;
			this.vertexTerrainBlendStrengths =
				source.vertexTerrainBlendStrengths;
			this.indices = source.indices;
			this.triangleTextures = source.triangleTextures;
			this.triangleFallbackColors = source.triangleFallbackColors;
			this.triangleModelKinds = source.triangleModelKinds;
			this.triangleMaterialFamilies =
				source.triangleMaterialFamilies;
			this.materialFamilyTriangleCounts =
				source.materialFamilyTriangleCounts;
			this.triangleTerrainVariationMasks =
				source.triangleTerrainVariationMasks;
			this.shadowCasters = source.shadowCasters;
			this.glowEmitters = source.glowEmitters;
			this.roofCoverageBits = source.roofCoverageBits;
			this.roofCoverageAxis = source.roofCoverageAxis;
			this.roofCoveredTileCount = source.roofCoveredTileCount;
			this.terrainTriangles = source.terrainTriangles;
			this.wallTriangles = source.wallTriangles;
			this.roofTriangles = source.roofTriangles;
			this.objectChunk = source.objectChunk;
			this.chunkRole = source.chunkRole;
			long translatedSignature = source.signature;
			translatedSignature =
				(translatedSignature ^ this.vertexOffsetX)
					* 1099511628211L;
			translatedSignature =
				(translatedSignature ^ this.vertexOffsetZ)
					* 1099511628211L;
			this.signature = translatedSignature;
			this.worldEditorTerrainGridAxis =
				source.worldEditorTerrainGridAxis;
			this.worldEditorTerrainGridHeights =
				source.worldEditorTerrainGridHeights;
			this.worldEditorTerrainGridSignature =
				source.worldEditorTerrainGridSignature;
		}

		/**
		 * Re-expresses one immutable presentation-only mesh in a new client
		 * section origin without copying its large source arrays. Gameplay and
		 * editor meshes intentionally cannot use this transient render offset.
		 */
		public ChunkMesh rebasePresentation(
			int additionalOffsetX,
			int additionalOffsetZ) {
			if (additionalOffsetX == 0 && additionalOffsetZ == 0) {
				return this;
			}
			if (objectChunk
				|| shadowCasters.length != 0
				|| glowEmitters.length != 0
				|| roofCoverageAxis != 0) {
				throw new IllegalStateException(
					"Only effect-free presentation meshes may be rebased");
			}
			return new ChunkMesh(
				this, additionalOffsetX, additionalOffsetZ);
		}

		private static int normalizeChunkRole(boolean objectChunk, int chunkRole) {
			if (!objectChunk) {
				return CHUNK_ROLE_WORLD;
			}
			return chunkRole == CHUNK_ROLE_ANIMATED_OBJECTS
				? CHUNK_ROLE_ANIMATED_OBJECTS
				: CHUNK_ROLE_STATIC_OBJECTS;
		}

		private void initializeVertexBounds() {
			if (vertexCoords.length < 3) {
				hasVertexBounds = false;
				minVertexX = 0;
				maxVertexX = 0;
				minVertexZ = 0;
				maxVertexZ = 0;
				return;
			}
			int minimumVertexX = Integer.MAX_VALUE;
			int maximumVertexX = Integer.MIN_VALUE;
			int minimumVertexZ = Integer.MAX_VALUE;
			int maximumVertexZ = Integer.MIN_VALUE;
			for (int coord = 0; coord + 2 < vertexCoords.length; coord += 3) {
				int x = Math.addExact(vertexCoords[coord], vertexOffsetX);
				int z = Math.addExact(vertexCoords[coord + 2], vertexOffsetZ);
				minimumVertexX = Math.min(minimumVertexX, x);
				maximumVertexX = Math.max(maximumVertexX, x);
				minimumVertexZ = Math.min(minimumVertexZ, z);
				maximumVertexZ = Math.max(maximumVertexZ, z);
			}
			hasVertexBounds = true;
			minVertexX = minimumVertexX;
			maxVertexX = maximumVertexX;
			minVertexZ = minimumVertexZ;
			maxVertexZ = maximumVertexZ;
		}

		private static float[] normalizeFloatArray(float[] values, int count, float defaultValue) {
			float[] normalized = new float[count];
			for (int i = 0; i < normalized.length; i++) {
				normalized[i] = values == null || i >= values.length ? defaultValue : values[i];
			}
			return normalized;
		}

		private static ShadowCaster[] normalizeShadowCasters(ShadowCaster[] shadowCasters) {
			return shadowCasters == null ? new ShadowCaster[0] : shadowCasters.clone();
		}

		private static GlowEmitter[] normalizeGlowEmitters(GlowEmitter[] glowEmitters) {
			return glowEmitters == null ? new GlowEmitter[0] : glowEmitters.clone();
		}

		private static Renderer3DMaterialFamily[] normalizeFamilies(
			Renderer3DMaterialFamily[] families,
			Renderer3DModelKind[] kinds,
			int count) {
			Renderer3DMaterialFamily[] normalized = new Renderer3DMaterialFamily[count];
			for (int i = 0; i < normalized.length; i++) {
				Renderer3DMaterialFamily family = families == null || i >= families.length ? null : families[i];
				Renderer3DModelKind kind = kinds == null || i >= kinds.length
					? Renderer3DModelKind.UNCLASSIFIED
					: kinds[i];
				normalized[i] = family == null
					? Renderer3DMaterialClassifier.fallbackFor(kind)
					: family;
			}
			return normalized;
		}

		private static int[] countFamilies(Renderer3DMaterialFamily[] families) {
			int[] counts = new int[Renderer3DMaterialFamily.values().length];
			for (Renderer3DMaterialFamily family : families) {
				Renderer3DMaterialFamily safeFamily = family == null
					? Renderer3DMaterialFamily.UNCLASSIFIED
					: family;
				counts[safeFamily.ordinal()]++;
			}
			return counts;
		}

		private static int[] normalizeIntArray(int[] values, int count, int defaultValue) {
			int[] normalized = new int[count];
			for (int i = 0; i < normalized.length; i++) {
				normalized[i] = values == null || i >= values.length ? defaultValue : values[i];
			}
			return normalized;
		}

		private static Renderer3DModelKind[] normalizeKinds(Renderer3DModelKind[] kinds, int triangleCount) {
			Renderer3DModelKind[] normalized = new Renderer3DModelKind[triangleCount];
			for (int i = 0; i < normalized.length; i++) {
				Renderer3DModelKind kind = kinds == null || i >= kinds.length ? null : kinds[i];
				normalized[i] = kind == null ? Renderer3DModelKind.UNCLASSIFIED : kind;
			}
			return normalized;
		}

		private static int[][] buildVertexNormals(
			int[] vertexCoords,
			int[] indices,
			int triangleCount,
			Renderer3DModelKind[] triangleModelKinds) {
			int vertexCount = vertexCoords.length / 3;
			int[] normalX = new int[vertexCount];
			int[] normalY = new int[vertexCount];
			int[] normalZ = new int[vertexCount];
			for (int vertex = 0; vertex < vertexCount; vertex++) {
				normalY[vertex] = 256;
			}

			int limit = Math.min(triangleCount, indices.length / 3);
			Map<VertexCoordKey, NormalAccumulator> terrainNormals =
				new HashMap<VertexCoordKey, NormalAccumulator>();
			for (int triangle = 0; triangle < limit; triangle++) {
				int sourceIndex = triangle * 3;
				int first = indices[sourceIndex];
				int second = indices[sourceIndex + 1];
				int third = indices[sourceIndex + 2];
				if (!isNormalVertexIndexValid(first, vertexCount)
					|| !isNormalVertexIndexValid(second, vertexCount)
					|| !isNormalVertexIndexValid(third, vertexCount)) {
					continue;
				}
				int firstCoord = first * 3;
				int secondCoord = second * 3;
				int thirdCoord = third * 3;
				double x21 = vertexCoords[secondCoord] - vertexCoords[firstCoord];
				double y21 = vertexCoords[secondCoord + 1] - vertexCoords[firstCoord + 1];
				double z21 = vertexCoords[secondCoord + 2] - vertexCoords[firstCoord + 2];
				double x31 = vertexCoords[thirdCoord] - vertexCoords[firstCoord];
				double y31 = vertexCoords[thirdCoord + 1] - vertexCoords[firstCoord + 1];
				double z31 = vertexCoords[thirdCoord + 2] - vertexCoords[firstCoord + 2];
				double faceNormalX = z31 * y21 - z21 * y31;
				double faceNormalY = z21 * x31 - x21 * z31;
				double faceNormalZ = x21 * y31 - x31 * y21;
				boolean terrainTriangle = triangleModelKinds != null
					&& triangle < triangleModelKinds.length
					&& triangleModelKinds[triangle] == Renderer3DModelKind.TERRAIN;
				if (terrainTriangle && faceNormalY < 0.0d) {
					faceNormalX = -faceNormalX;
					faceNormalY = -faceNormalY;
					faceNormalZ = -faceNormalZ;
				}
				double magnitude = Math.sqrt(
					faceNormalX * faceNormalX
						+ faceNormalY * faceNormalY
						+ faceNormalZ * faceNormalZ);
				if (magnitude <= 0.000001d) {
					continue;
				}
				int scaledNormalX = (int) (faceNormalX * 256.0d / magnitude);
				int scaledNormalY = (int) (faceNormalY * 256.0d / magnitude);
				int scaledNormalZ = (int) (faceNormalZ * 256.0d / magnitude);
				normalX[first] = scaledNormalX;
				normalY[first] = scaledNormalY;
				normalZ[first] = scaledNormalZ;
				normalX[second] = scaledNormalX;
				normalY[second] = scaledNormalY;
				normalZ[second] = scaledNormalZ;
				normalX[third] = scaledNormalX;
				normalY[third] = scaledNormalY;
				normalZ[third] = scaledNormalZ;
				if (terrainTriangle) {
					addTerrainNormal(terrainNormals, vertexCoords, firstCoord, faceNormalX, faceNormalY, faceNormalZ);
					addTerrainNormal(terrainNormals, vertexCoords, secondCoord, faceNormalX, faceNormalY, faceNormalZ);
					addTerrainNormal(terrainNormals, vertexCoords, thirdCoord, faceNormalX, faceNormalY, faceNormalZ);
				}
			}
			if (!terrainNormals.isEmpty()) {
				applySmoothedTerrainNormals(normalX, normalY, normalZ, vertexCoords, indices, limit, triangleModelKinds, terrainNormals);
			}
			return new int[][] {normalX, normalY, normalZ};
		}

		private static void addTerrainNormal(
			Map<VertexCoordKey, NormalAccumulator> terrainNormals,
			int[] vertexCoords,
			int coord,
			double normalX,
			double normalY,
			double normalZ) {
			VertexCoordKey key = VertexCoordKey.from(vertexCoords, coord);
			NormalAccumulator accumulator = terrainNormals.get(key);
			if (accumulator == null) {
				accumulator = new NormalAccumulator();
				terrainNormals.put(key, accumulator);
			}
			accumulator.add(normalX, normalY, normalZ);
		}

		private static void applySmoothedTerrainNormals(
			int[] normalX,
			int[] normalY,
			int[] normalZ,
			int[] vertexCoords,
			int[] indices,
			int triangleLimit,
			Renderer3DModelKind[] triangleModelKinds,
			Map<VertexCoordKey, NormalAccumulator> terrainNormals) {
			int vertexCount = vertexCoords.length / 3;
			for (int triangle = 0; triangle < triangleLimit; triangle++) {
				if (triangleModelKinds == null
					|| triangle >= triangleModelKinds.length
					|| triangleModelKinds[triangle] != Renderer3DModelKind.TERRAIN) {
					continue;
				}
				int sourceIndex = triangle * 3;
				for (int offset = 0; offset < 3; offset++) {
					int vertex = indices[sourceIndex + offset];
					if (!isNormalVertexIndexValid(vertex, vertexCount)) {
						continue;
					}
					int coord = vertex * 3;
					NormalAccumulator accumulator = terrainNormals.get(VertexCoordKey.from(vertexCoords, coord));
					if (accumulator != null) {
						accumulator.writeTo(normalX, normalY, normalZ, vertex);
					}
				}
			}
		}

		private static boolean isNormalVertexIndexValid(int vertex, int vertexCount) {
			return vertex >= 0 && vertex < vertexCount;
		}

		private static final class VertexCoordKey {
			private final int x;
			private final int y;
			private final int z;

			private VertexCoordKey(int x, int y, int z) {
				this.x = x;
				this.y = y;
				this.z = z;
			}

			private static VertexCoordKey from(int[] vertexCoords, int coord) {
				return new VertexCoordKey(vertexCoords[coord], vertexCoords[coord + 1], vertexCoords[coord + 2]);
			}

			@Override
			public boolean equals(Object other) {
				if (this == other) {
					return true;
				}
				if (!(other instanceof VertexCoordKey)) {
					return false;
				}
				VertexCoordKey key = (VertexCoordKey) other;
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

		private static final class NormalAccumulator {
			private double x;
			private double y;
			private double z;

			private void add(double normalX, double normalY, double normalZ) {
				x += normalX;
				y += normalY;
				z += normalZ;
			}

			private void writeTo(int[] normalX, int[] normalY, int[] normalZ, int vertex) {
				double magnitude = Math.sqrt(x * x + y * y + z * z);
				if (magnitude <= 0.000001d) {
					return;
				}
				normalX[vertex] = (int) (x * 256.0d / magnitude);
				normalY[vertex] = (int) (y * 256.0d / magnitude);
				normalZ[vertex] = (int) (z * 256.0d / magnitude);
			}
		}

		public int getPlane() {
			return plane;
		}

		public int getCenterSectionX() {
			return centerSectionX;
		}

		public int getCenterSectionY() {
			return centerSectionY;
		}

		public int getOriginWorldX() {
			return originWorldX;
		}

		public int getOriginWorldZ() {
			return originWorldZ;
		}

		public int getVertexCount() {
			return vertexCoords.length / 3;
		}

		public boolean hasVertexBounds() {
			return hasVertexBounds;
		}

		public int getMinVertexX() {
			return minVertexX;
		}

		public int getMaxVertexX() {
			return maxVertexX;
		}

		public int getMinVertexZ() {
			return minVertexZ;
		}

		public int getMaxVertexZ() {
			return maxVertexZ;
		}

		public int getIndexCount() {
			return indices.length;
		}

		public int getTriangleCount() {
			return triangleTextures.length;
		}

		public int getTerrainTriangles() {
			return terrainTriangles;
		}

		void setWorldEditorTerrainGrid(int axis, int[] heights) {
			long expected = (long) axis * (long) axis;
			if (objectChunk || axis < 2 || heights == null || expected != heights.length) {
				worldEditorTerrainGridAxis = 0;
				worldEditorTerrainGridHeights = new int[0];
				worldEditorTerrainGridSignature = 0;
				return;
			}
			worldEditorTerrainGridAxis = axis;
			worldEditorTerrainGridHeights = heights.clone();
			worldEditorTerrainGridSignature = 31 * axis + Arrays.hashCode(worldEditorTerrainGridHeights);
		}

		public boolean hasWorldEditorTerrainGrid() {
			return worldEditorTerrainGridAxis >= 2;
		}

		public int getWorldEditorTerrainGridAxis() {
			return worldEditorTerrainGridAxis;
		}

		public int getWorldEditorTerrainGridHeight(int index) {
			return worldEditorTerrainGridHeights[index];
		}

		public int getWorldEditorTerrainGridSignature() {
			return worldEditorTerrainGridSignature;
		}

		public int getWallTriangles() {
			return wallTriangles;
		}

		public int getRoofTriangles() {
			return roofTriangles;
		}

		public boolean hasRoofCoverageData() {
			return roofCoverageAxis > 0;
		}

		public int getRoofCoveredTileCount() {
			return roofCoveredTileCount;
		}

		public boolean isRoofCoveredTile(int tileX, int tileZ) {
			if (roofCoverageAxis <= 0
				|| tileX < 0
				|| tileZ < 0
				|| tileX >= roofCoverageAxis
				|| tileZ >= roofCoverageAxis) {
				return false;
			}
			int bitIndex = tileZ + tileX * roofCoverageAxis;
			int wordIndex = bitIndex >>> 6;
			return wordIndex >= 0
				&& wordIndex < roofCoverageBits.length
				&& (roofCoverageBits[wordIndex] & (1L << (bitIndex & 63))) != 0L;
		}

		public int roofClassificationForWorldPoint(int worldX, int worldZ) {
			if (!hasRoofCoverageData()) {
				return -1;
			}
			int tileX = Math.floorDiv(worldX, TILE_SIZE);
			int tileZ = Math.floorDiv(worldZ, TILE_SIZE);
			if (tileX < 0 || tileZ < 0 || tileX >= roofCoverageAxis || tileZ >= roofCoverageAxis) {
				return -1;
			}
			return isRoofCoveredTile(tileX, tileZ) ? 1 : 0;
		}

		public boolean isObjectChunk() {
			return objectChunk;
		}

		public int getChunkRole() {
			return chunkRole;
		}

		public int getShadowCasterCount() {
			return shadowCasters.length;
		}

		public ShadowCaster getShadowCaster(int index) {
			return shadowCasters[index];
		}

		public int getGlowEmitterCount() {
			return glowEmitters.length;
		}

		public GlowEmitter getGlowEmitter(int index) {
			return glowEmitters[index];
		}

		public long getSignature() {
			return signature;
		}

		public int getVertexCoord(int coordIndex) {
			int value = vertexCoords[coordIndex];
			int axis = coordIndex % 3;
			return axis == 0
				? Math.addExact(value, vertexOffsetX)
				: axis == 2
					? Math.addExact(value, vertexOffsetZ)
					: value;
		}

		public float getVertexTextureU(int vertexIndex) {
			return vertexTextureU[vertexIndex];
		}

		public float getVertexTextureV(int vertexIndex) {
			return vertexTextureV[vertexIndex];
		}

		public int getVertexLight(int vertexIndex) {
			return vertexLights[vertexIndex];
		}

		public int getVertexNormalX(int vertexIndex) {
			return vertexNormalX[vertexIndex];
		}

		public int getVertexNormalY(int vertexIndex) {
			return vertexNormalY[vertexIndex];
		}

		public int getVertexNormalZ(int vertexIndex) {
			return vertexNormalZ[vertexIndex];
		}

		public int getVertexTerrainBlendColor(int vertexIndex) {
			return vertexTerrainBlendColors[vertexIndex];
		}

		public int getVertexTerrainBlendStrength(int vertexIndex) {
			return vertexTerrainBlendStrengths[vertexIndex];
		}

		public int getIndex(int indexOffset) {
			return indices[indexOffset];
		}

		public int getTriangleTexture(int triangleIndex) {
			return triangleTextures[triangleIndex];
		}

		public int getTriangleFallbackColor(int triangleIndex) {
			return triangleFallbackColors[triangleIndex];
		}

		public Renderer3DModelKind getTriangleModelKind(int triangleIndex) {
			return triangleModelKinds[triangleIndex];
		}

		public Renderer3DMaterialFamily getTriangleMaterialFamily(int triangleIndex) {
			return triangleMaterialFamilies[triangleIndex];
		}

		public int getMaterialFamilyTriangleCount(Renderer3DMaterialFamily family) {
			Renderer3DMaterialFamily safeFamily = family == null
				? Renderer3DMaterialFamily.UNCLASSIFIED
				: family;
			return materialFamilyTriangleCounts[safeFamily.ordinal()];
		}

		public int getTriangleTerrainVariationMask(int triangleIndex) {
			return triangleTerrainVariationMasks[triangleIndex];
		}

		public ShadowCaster[] copyShadowCasters() {
			return shadowCasters.clone();
		}

		public GlowEmitter[] copyGlowEmitters() {
			return glowEmitters.clone();
		}

		public int[] copyVertexCoords() {
			int[] copy = vertexCoords.clone();
			for (int coord = 0; coord < copy.length; coord += 3) {
				copy[coord] = Math.addExact(copy[coord], vertexOffsetX);
				copy[coord + 2] = Math.addExact(
					copy[coord + 2], vertexOffsetZ);
			}
			return copy;
		}

		public float[] copyVertexTextureU() {
			return vertexTextureU.clone();
		}

		public float[] copyVertexTextureV() {
			return vertexTextureV.clone();
		}

		public int[] copyVertexLights() {
			return vertexLights.clone();
		}

		public int[] copyVertexNormalX() {
			return vertexNormalX.clone();
		}

		public int[] copyVertexNormalY() {
			return vertexNormalY.clone();
		}

		public int[] copyVertexNormalZ() {
			return vertexNormalZ.clone();
		}

		public int[] copyVertexTerrainBlendColors() {
			return vertexTerrainBlendColors.clone();
		}

		public int[] copyVertexTerrainBlendStrengths() {
			return vertexTerrainBlendStrengths.clone();
		}

		public int[] copyIndices() {
			return indices.clone();
		}

		public int[] copyTriangleTextures() {
			return triangleTextures.clone();
		}

		public int[] copyTriangleFallbackColors() {
			return triangleFallbackColors.clone();
		}

		public Renderer3DModelKind[] copyTriangleModelKinds() {
			return triangleModelKinds.clone();
		}

		public Renderer3DMaterialFamily[] copyTriangleMaterialFamilies() {
			return triangleMaterialFamilies.clone();
		}

		public int[] copyTriangleTerrainVariationMasks() {
			return triangleTerrainVariationMasks.clone();
		}
	}

	public static final class ShadowCaster {
		private final Renderer3DModelKind modelKind;
		private final int baseX0;
		private final int baseY;
		private final int baseZ0;
		private final int baseX1;
		private final int baseZ1;
			private final int height;
			private final int width;
			private final int opacity;
			private final boolean outdoorOnly;
			private final int footprintMinX;
			private final int footprintMaxX;
			private final int footprintMinZ;
			private final int footprintMaxZ;

			public ShadowCaster(
				Renderer3DModelKind modelKind,
			int baseX0,
			int baseY,
			int baseZ0,
			int baseX1,
			int baseZ1,
				int height,
				int width,
				int opacity,
				boolean outdoorOnly) {
				this(
					modelKind,
					baseX0,
					baseY,
					baseZ0,
					baseX1,
					baseZ1,
					height,
					width,
					opacity,
					outdoorOnly,
					Math.min(baseX0, baseX1),
					Math.max(baseX0, baseX1),
					Math.min(baseZ0, baseZ1),
					Math.max(baseZ0, baseZ1));
			}

			public ShadowCaster(
				Renderer3DModelKind modelKind,
				int baseX0,
				int baseY,
				int baseZ0,
				int baseX1,
				int baseZ1,
				int height,
				int width,
				int opacity,
				boolean outdoorOnly,
				int footprintMinX,
				int footprintMaxX,
				int footprintMinZ,
				int footprintMaxZ) {
				this.modelKind = modelKind == null ? Renderer3DModelKind.UNCLASSIFIED : modelKind;
				this.baseX0 = baseX0;
				this.baseY = baseY;
			this.baseZ0 = baseZ0;
			this.baseX1 = baseX1;
			this.baseZ1 = baseZ1;
			this.height = Math.max(0, height);
				this.width = Math.max(0, width);
				this.opacity = Math.max(0, Math.min(255, opacity));
				this.outdoorOnly = outdoorOnly;
				this.footprintMinX = Math.min(footprintMinX, footprintMaxX);
				this.footprintMaxX = Math.max(footprintMinX, footprintMaxX);
				this.footprintMinZ = Math.min(footprintMinZ, footprintMaxZ);
				this.footprintMaxZ = Math.max(footprintMinZ, footprintMaxZ);
			}

		public Renderer3DModelKind getModelKind() {
			return modelKind;
		}

		public int getBaseX0() {
			return baseX0;
		}

		public int getBaseY() {
			return baseY;
		}

		public int getBaseZ0() {
			return baseZ0;
		}

		public int getBaseX1() {
			return baseX1;
		}

		public int getBaseZ1() {
			return baseZ1;
		}

		public int getHeight() {
			return height;
		}

		public int getWidth() {
			return width;
		}

		public int getOpacity() {
			return opacity;
		}

			public boolean isOutdoorOnly() {
				return outdoorOnly;
			}

			public int getFootprintMinX() {
				return footprintMinX;
			}

			public int getFootprintMaxX() {
				return footprintMaxX;
			}

			public int getFootprintMinZ() {
				return footprintMinZ;
			}

			public int getFootprintMaxZ() {
				return footprintMaxZ;
		}
	}

	public static final class GlowEmitter {
		private final Renderer3DModelKind modelKind;
		private final int centerX;
		private final int centerY;
		private final int centerZ;
		private final int radius;
		private final int color;
		private final int intensity;

		public GlowEmitter(
			Renderer3DModelKind modelKind,
			int centerX,
			int centerY,
			int centerZ,
			int radius,
			int color,
			int intensity) {
			this.modelKind = modelKind == null ? Renderer3DModelKind.UNCLASSIFIED : modelKind;
			this.centerX = centerX;
			this.centerY = centerY;
			this.centerZ = centerZ;
			this.radius = Math.max(1, radius);
			this.color = color & 0xffffff;
			this.intensity = Math.max(0, Math.min(255, intensity));
		}

		public Renderer3DModelKind getModelKind() {
			return modelKind;
		}

		public int getCenterX() {
			return centerX;
		}

		public int getCenterY() {
			return centerY;
		}

		public int getCenterZ() {
			return centerZ;
		}

		public int getRadius() {
			return radius;
		}

		public int getColor() {
			return color;
		}

		public int getIntensity() {
			return intensity;
		}
	}
}
