package orsc.graphics.three;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import orsc.MiscFunctions;
import orsc.buffers.RSBufferUtils;
import orsc.util.FastMath;
import orsc.util.GenUtil;

public final class RSModel {
	private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
	private static final long FNV_PRIME = 0x100000001b3L;
	private static final int FISHING_SPOT_RIPPLE_PERIOD = 24;
	private static final int FISHING_SPOT_RIPPLE_CENTER = 12;
	private static final int FISHING_SPOT_RIPPLE_PHASE_STEP = 8;
	private static final int RENDERER_3D_OBJECT_SHADOW_MAX_FOOTPRINT = 1024;
	private static final int RENDERER_3D_WALL_OBJECT_SHADOW_MAX_FOOTPRINT = 512;
	private final int m_Vb = 12345678;
	int[] faceDiffuseLight;
	int faceHead;
	int[] faceIndexCount;
	int[][] faceIndices;
	public int key = -1;
	public boolean m_cb = false;
	public int[] facePickIndex;
	int diffuseParam1 = 32;
	int[] scenePolyNormalShift;
	int[] scenePolyNormalMagnitude;
	int[] faceTextureBack;
	int[] faceTextureFront;
	private Renderer3DModelKind renderer3DModelKind = Renderer3DModelKind.UNCLASSIFIED;
	private Renderer3DMaterialFamily renderer3DMaterialFamily = Renderer3DMaterialFamily.UNCLASSIFIED;
	private int renderer3DGlowColor;
	private int renderer3DGlowRadius;
	private int renderer3DGlowIntensity;
	private int pickBoundsScale = 1;
	boolean m_db = false;
	boolean m_dc = true;
	int m_hc = 0;
	boolean m_Kb = false;
	int m_Yb = 1;
	byte[] m_zb;
	int[] vertDiffuseLight;
	int[] vertexParam2;
	int[] vertexParam6;
	byte[] vertLightOther;
	int vertHead;
	int[] vertX;
	int[] vertXRot;
	int[] vertYRot;
	int[] vertZ;
	int[] vertZRot;
	private int appliedTransform;
	private int diffuseDirX = 180;
	private int diffuseDirY = 155;
	private int diffuseDirZ = 95;
	private int diffuseMag = 256;
	private int diffuseParam2 = 512;
	private boolean dontComputeDiffuse = false;
	private int faceCount;
	private int[] faceMaxX;
	private int[] faceMaxY;
	private int[] faceMaxZ;
	private int[] faceMinX;
	private int[] faceMinY;
	private int[] faceMinZ;
	private int[] faceNormX;
	private int[] faceNormY;
	private int[] faceNormZ;
	private int[][] faceParam1;
	private boolean m_b = false;
	private boolean m_c = false;
	private int m_hb;
	private boolean m_v = false;
	private int maxFaceDimension = 12345678;
	private int maxX;
	private int maxY;
	private int maxZ;
	private int minX;
	private int minY;
	private int minZ;
	private int rot256X;
	private int rot256Y;
	private int rot256Z;
	private int rotM_xToY;
	private int rotM_xToZ;
	private int rotM_yToX;
	private int rotM_yToZ;
	private int rotM_zToX;
	private int rotM_zToY;
	private int scaleX;
	private int scaleY;
	private int scaleZ;
	private int translateX;
	private int translateY;
	private int translateZ;
	private int vertexCount2;
	private int renderer3DTransformVersion;
	private int fishingSpotClarityAnimationState = -1;
	private int[] fishingSpotClarityBaseX;
	private int[] fishingSpotClarityBaseY;
	private int[] fishingSpotClarityBaseZ;
	private int[] fishingSpotClarityRippleGroup;
	private int[] vertXTransform;
	private int[] vertY;
	private int[] vertYTransform;
	private int[] vertZTransform;

	public RSModel(byte[] data, int offset, boolean var3) {
		try {
			int vertexCount = RSBufferUtils.get16(offset, data);
			offset += 2;
			int faceCount = RSBufferUtils.get16(offset, data);
			offset += 2;
			this.setFaceVertexCount(faceCount, vertexCount, (int) 115);
			this.faceParam1 = new int[faceCount][1];

			int j;
			for (j = 0; vertexCount > j; ++j) {
				this.vertX[j] = RSBufferUtils.readShort(data, -1, offset);
				offset += 2;
			}

			for (j = 0; j < vertexCount; ++j) {
				this.vertY[j] = RSBufferUtils.readShort(data, -1, offset);
				offset += 2;
			}

			for (j = 0; vertexCount > j; ++j) {
				this.vertZ[j] = RSBufferUtils.readShort(data, -1, offset);
				offset += 2;
			}

			this.vertHead = vertexCount;

			for (j = 0; j < faceCount; ++j) {
				this.faceIndexCount[j] = FastMath.bitwiseAnd(255, data[offset++]);
			}

			for (j = 0; j < faceCount; ++j) {
				this.faceTextureFront[j] = RSBufferUtils.readShort(data, -1, offset);
				if (this.faceTextureFront[j] == 32767) {
					this.faceTextureFront[j] = this.m_Vb;
				}

				offset += 2;
			}

			for (j = 0; faceCount > j; ++j) {
				this.faceTextureBack[j] = RSBufferUtils.readShort(data, -1, offset);
				offset += 2;
				if (32767 == this.faceTextureBack[j]) {
					this.faceTextureBack[j] = this.m_Vb;
				}
			}

			int i;
			for (j = 0; j < faceCount; ++j) {
				i = 255 & data[offset++];
				if (i != 0) {
					this.faceDiffuseLight[j] = this.m_Vb;
				} else {
					this.faceDiffuseLight[j] = 0;
				}
			}

			for (j = 0; faceCount > j; ++j) {
				this.faceIndices[j] = new int[this.faceIndexCount[j]];

				for (i = 0; this.faceIndexCount[j] > i; ++i) {
					if (vertexCount < 256) {
						this.faceIndices[j][i] = FastMath.bitwiseAnd(255, data[offset++]);
					} else {
						this.faceIndices[j][i] = RSBufferUtils.get16(offset, data);
						offset += 2;
					}
				}
			}

			this.faceHead = faceCount;
			this.m_Yb = 1;
		} catch (RuntimeException var8) {
			throw GenUtil.makeThrowable(var8,
				"ca.<init>(" + (data != null ? "{...}" : "null") + ',' + offset + ',' + var3 + ')');
		}
	}

	public RSModel(int vertexCount, int faceCount) {
		try {
			this.setFaceVertexCount(faceCount, vertexCount, (int) 69);
			this.faceParam1 = new int[faceCount][1];

			for (int face = 0; face < faceCount; face++) {
				this.faceParam1[face][0] = face;
			}

		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "ca.<init>(" + vertexCount + ',' + faceCount + ')');
		}
	}

	RSModel(int vertexLimit, int faceLimit, boolean var3, boolean var4, boolean var5, boolean var6, boolean var7) {
		try {
			this.m_c = var4;
			this.m_b = var7;
			this.m_db = var6;
			this.m_v = var3;
			this.dontComputeDiffuse = var5;
			this.setFaceVertexCount(faceLimit, vertexLimit, (int) 69);
		} catch (RuntimeException var9) {
			throw GenUtil.makeThrowable(var9, "ca.<init>(" + vertexLimit + ',' + faceLimit + ',' + var3 + ',' + var4
				+ ',' + var5 + ',' + var6 + ',' + var7 + ')');
		}
	}

	private RSModel(RSModel[] models, int modelCount) {
		try {
			this.addModels(0, models, true, modelCount);
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4,
				"ca.<init>(" + (models != null ? "{...}" : "null") + ',' + modelCount + ')');
		}
	}

	void ensureCapacity(int minVertexCapacity, int minFaceCapacity) {
		if (minVertexCapacity > this.vertexCount2) {
			int vertexCapacity = growCapacity(this.vertexCount2, minVertexCapacity);
			this.vertY = Arrays.copyOf(this.vertY, vertexCapacity);
			this.vertLightOther = Arrays.copyOf(this.vertLightOther, vertexCapacity);
			this.vertX = Arrays.copyOf(this.vertX, vertexCapacity);
			this.vertZ = Arrays.copyOf(this.vertZ, vertexCapacity);
			this.vertDiffuseLight = Arrays.copyOf(this.vertDiffuseLight, vertexCapacity);
			if (this.vertexParam2 != null) {
				this.vertexParam2 = Arrays.copyOf(this.vertexParam2, vertexCapacity);
			}
			if (this.vertYRot != null) {
				this.vertYRot = Arrays.copyOf(this.vertYRot, vertexCapacity);
			}
			if (this.vertXRot != null) {
				this.vertXRot = Arrays.copyOf(this.vertXRot, vertexCapacity);
			}
			if (this.vertZRot != null) {
				this.vertZRot = Arrays.copyOf(this.vertZRot, vertexCapacity);
			}
			if (this.vertexParam6 != null) {
				this.vertexParam6 = Arrays.copyOf(this.vertexParam6, vertexCapacity);
			}
			if (this.m_v) {
				this.vertXTransform = this.vertX;
				this.vertZTransform = this.vertZ;
				this.vertYTransform = this.vertY;
			} else {
				this.vertXTransform = Arrays.copyOf(this.vertXTransform, vertexCapacity);
				this.vertZTransform = Arrays.copyOf(this.vertZTransform, vertexCapacity);
				this.vertYTransform = Arrays.copyOf(this.vertYTransform, vertexCapacity);
			}
			if (this.fishingSpotClarityBaseX != null) {
				this.fishingSpotClarityBaseX = Arrays.copyOf(this.fishingSpotClarityBaseX, vertexCapacity);
				this.fishingSpotClarityBaseY = Arrays.copyOf(this.fishingSpotClarityBaseY, vertexCapacity);
				this.fishingSpotClarityBaseZ = Arrays.copyOf(this.fishingSpotClarityBaseZ, vertexCapacity);
				this.fishingSpotClarityRippleGroup = Arrays.copyOf(this.fishingSpotClarityRippleGroup, vertexCapacity);
			}
			this.vertexCount2 = vertexCapacity;
		}

		if (minFaceCapacity > this.faceCount) {
			int faceCapacity = growCapacity(this.faceCount, minFaceCapacity);
			if (this.m_zb != null) {
				this.m_zb = Arrays.copyOf(this.m_zb, faceCapacity);
			}
			if (this.facePickIndex != null) {
				this.facePickIndex = Arrays.copyOf(this.facePickIndex, faceCapacity);
			}
			this.scenePolyNormalMagnitude = Arrays.copyOf(this.scenePolyNormalMagnitude, faceCapacity);
			this.scenePolyNormalShift = Arrays.copyOf(this.scenePolyNormalShift, faceCapacity);
			this.faceTextureBack = Arrays.copyOf(this.faceTextureBack, faceCapacity);
			this.faceTextureFront = Arrays.copyOf(this.faceTextureFront, faceCapacity);
			this.faceIndices = Arrays.copyOf(this.faceIndices, faceCapacity);
			this.faceIndexCount = Arrays.copyOf(this.faceIndexCount, faceCapacity);
			this.faceDiffuseLight = Arrays.copyOf(this.faceDiffuseLight, faceCapacity);
			if (this.faceMaxX != null) {
				this.faceMaxX = Arrays.copyOf(this.faceMaxX, faceCapacity);
				this.faceMinY = Arrays.copyOf(this.faceMinY, faceCapacity);
				this.faceMinZ = Arrays.copyOf(this.faceMinZ, faceCapacity);
				this.faceMaxY = Arrays.copyOf(this.faceMaxY, faceCapacity);
				this.faceMaxZ = Arrays.copyOf(this.faceMaxZ, faceCapacity);
				this.faceMinX = Arrays.copyOf(this.faceMinX, faceCapacity);
			}
			if (this.faceNormY != null) {
				this.faceNormY = Arrays.copyOf(this.faceNormY, faceCapacity);
				this.faceNormZ = Arrays.copyOf(this.faceNormZ, faceCapacity);
				this.faceNormX = Arrays.copyOf(this.faceNormX, faceCapacity);
			}
			if (this.faceParam1 != null) {
				this.faceParam1 = Arrays.copyOf(this.faceParam1, faceCapacity);
			}
			this.faceCount = faceCapacity;
		}
	}

	private static int growCapacity(int currentCapacity, int requiredCapacity) {
		int grownCapacity = Math.max(1, currentCapacity);
		while (grownCapacity < requiredCapacity) {
			int increment = Math.max(128, grownCapacity / 2);
			grownCapacity += increment;
			if (grownCapacity < 0) {
				return requiredCapacity;
			}
		}
		return grownCapacity;
	}

	public int hideBackFacesMatching(int texture) {
		int replaced = 0;
		for (int face = 0; face < this.faceHead; face++) {
			if (this.faceTextureBack[face] == texture) {
				this.faceTextureBack[face] = this.m_Vb;
				replaced++;
			}
		}
		return replaced;
	}

	public int tintVisibleFaces(int materialResource) {
		int replaced = 0;
		for (int face = 0; face < this.faceHead; face++) {
			if (isVisibleMaterial(this.faceTextureFront[face]) && this.faceTextureFront[face] != materialResource) {
				this.faceTextureFront[face] = materialResource;
				replaced++;
			}
			if (isVisibleMaterial(this.faceTextureBack[face]) && this.faceTextureBack[face] != materialResource) {
				this.faceTextureBack[face] = materialResource;
				replaced++;
			}
		}

		if (replaced > 0) {
			this.m_Yb = 1;
			this.renderer3DTransformVersion++;
		}
		return replaced;
	}

	private boolean isVisibleMaterial(int material) {
		return material != this.m_Vb && material != Scene.TRANSPARENT;
	}

	public int hideFacesUsingVerticesOutsideLocalBounds(int maxAbsX, int maxAbsY, int maxAbsZ) {
		boolean[] outlierVertices = new boolean[this.vertHead];
		for (int vertex = 0; vertex < this.vertHead; vertex++) {
			outlierVertices[vertex] =
				Math.abs(this.vertX[vertex]) > maxAbsX
					|| Math.abs(this.vertY[vertex]) > maxAbsY
					|| Math.abs(this.vertZ[vertex]) > maxAbsZ;
		}

		int replaced = 0;
		for (int face = 0; face < this.faceHead; face++) {
			if (!faceUsesOutlierVertex(face, outlierVertices)) {
				continue;
			}
			if (this.faceTextureFront[face] != this.m_Vb || this.faceTextureBack[face] != this.m_Vb) {
				replaced++;
			}
			this.faceTextureFront[face] = this.m_Vb;
			this.faceTextureBack[face] = this.m_Vb;
		}

		if (replaced > 0) {
			for (int vertex = 0; vertex < outlierVertices.length; vertex++) {
				if (outlierVertices[vertex]) {
					this.vertX[vertex] = 0;
					this.vertY[vertex] = 0;
					this.vertZ[vertex] = 0;
				}
			}
			this.m_Yb = 1;
			this.renderer3DTransformVersion++;
		}
		return replaced;
	}

	private boolean faceUsesOutlierVertex(int face, boolean[] outlierVertices) {
		int[] indices = this.faceIndices[face];
		int indexCount = this.faceIndexCount[face];
		for (int index = 0; index < indexCount; index++) {
			int vertex = indices[index];
			if (vertex >= 0 && vertex < outlierVertices.length && outlierVertices[vertex]) {
				return true;
			}
		}
		return false;
	}

	public RSModel withFishingSpotClarityOverlay() {
		RSModel overlay = createFishingSpotClarityOverlay();
		RSModel combined = new RSModel(new RSModel[] {this, overlay}, 2);
		combined.m_cb = this.m_cb;
		combined.m_hc = this.m_hc;
		combined.renderer3DModelKind = this.renderer3DModelKind;
		combined.renderer3DMaterialFamily = this.renderer3DMaterialFamily;
		combined.copyRenderer3DGlowEmitterFrom(this);
		combined.pickBoundsScale = this.pickBoundsScale;
		return combined;
	}

	public boolean animateFishingSpotClarityOverlay(int animationFrame, int phaseOffset) {
		if (this.fishingSpotClarityRippleGroup == null) {
			return false;
		}
		int state = Math.floorMod(animationFrame + phaseOffset, FISHING_SPOT_RIPPLE_PERIOD);
		if (state == this.fishingSpotClarityAnimationState) {
			return false;
		}
		this.fishingSpotClarityAnimationState = state;
		boolean changed = false;
		for (int vertex = 0; vertex < this.vertHead; vertex++) {
			int group = this.fishingSpotClarityRippleGroup[vertex];
			if (group < 0) {
				continue;
			}
			int localState = Math.floorMod(state + group * FISHING_SPOT_RIPPLE_PHASE_STEP,
				FISHING_SPOT_RIPPLE_PERIOD);
			int wave = FISHING_SPOT_RIPPLE_CENTER - Math.abs(FISHING_SPOT_RIPPLE_CENTER - localState);
			int scale = 250 + wave * 4;
			this.vertX[vertex] = this.fishingSpotClarityBaseX[vertex] * scale / 256;
			this.vertZ[vertex] = this.fishingSpotClarityBaseZ[vertex] * scale / 256;
			this.vertY[vertex] = this.fishingSpotClarityBaseY[vertex] - wave / 5;
			changed = true;
		}
		if (changed) {
			this.m_Yb = 1;
			this.renderer3DTransformVersion++;
		}
		return changed;
	}

	private static RSModel createFishingSpotClarityOverlay() {
		RSModel model = new RSModel(96, 40);
		int white = GenUtil.colorToResource(245, 255, 255);
		int cyan = GenUtil.colorToResource(112, 220, 255);
		int blue = GenUtil.colorToResource(54, 155, 210);

		markFishingSpotRippleVertices(model, addHorizontalQuad(model, -34, -38, 34, -38, 3, -3, cyan), 0);
		markFishingSpotRippleVertices(model, addHorizontalQuad(model, -34, 38, 34, 38, 3, -3, cyan), 0);
		markFishingSpotRippleVertices(model, addHorizontalQuad(model, -42, -28, -42, 28, 3, -3, blue), 0);
		markFishingSpotRippleVertices(model, addHorizontalQuad(model, 42, -28, 42, 28, 3, -3, blue), 0);
		markFishingSpotRippleVertices(model, addHorizontalQuad(model, -18, -21, 18, -21, 2, -5, white), 1);
		markFishingSpotRippleVertices(model, addHorizontalQuad(model, -18, 21, 18, 21, 2, -5, white), 1);
		markFishingSpotRippleVertices(model, addHorizontalQuad(model, -24, -12, -24, 12, 2, -5, cyan), 1);
		markFishingSpotRippleVertices(model, addHorizontalQuad(model, 24, -12, 24, 12, 2, -5, cyan), 1);

		addBubblePyramid(model, 0, 0, 8, -8, 15, white);
		addBubblePyramid(model, -18, 12, 6, -7, 11, cyan);
		addBubblePyramid(model, 19, -11, 5, -7, 10, white);
		addBubblePyramid(model, 10, 23, 4, -8, 8, cyan);

		model.setDiffuseLightAndColor(-50, -10, -50, 44, 56, true, -74);
		return model;
	}

	private static int[] addHorizontalQuad(RSModel model, int x1, int z1, int x2, int z2, int halfWidth, int y,
										   int color) {
		int[] vertices = new int[4];
		if (x1 == x2) {
			vertices[0] = model.insertVertex(x1 - halfWidth, y, z1);
			vertices[1] = model.insertVertex(x1 + halfWidth, y, z1);
			vertices[2] = model.insertVertex(x2 + halfWidth, y, z2);
			vertices[3] = model.insertVertex(x2 - halfWidth, y, z2);
		} else {
			vertices[0] = model.insertVertex(x1, y, z1 - halfWidth);
			vertices[1] = model.insertVertex(x2, y, z2 - halfWidth);
			vertices[2] = model.insertVertex(x2, y, z2 + halfWidth);
			vertices[3] = model.insertVertex(x1, y, z1 + halfWidth);
		}
		addFaceLit(model, 4, vertices, color);
		return vertices;
	}

	private static void markFishingSpotRippleVertices(RSModel model, int[] vertices, int group) {
		for (int vertex : vertices) {
			model.markFishingSpotRippleVertex(vertex, group);
		}
	}

	private void markFishingSpotRippleVertex(int vertex, int group) {
		if (vertex < 0 || vertex >= this.vertHead) {
			return;
		}
		ensureFishingSpotClarityMetadata();
		this.fishingSpotClarityBaseX[vertex] = this.vertX[vertex];
		this.fishingSpotClarityBaseY[vertex] = this.vertY[vertex];
		this.fishingSpotClarityBaseZ[vertex] = this.vertZ[vertex];
		this.fishingSpotClarityRippleGroup[vertex] = group;
	}

	private void ensureFishingSpotClarityMetadata() {
		if (this.fishingSpotClarityRippleGroup != null
			&& this.fishingSpotClarityRippleGroup.length >= this.vertexCount2) {
			return;
		}
		int length = Math.max(this.vertexCount2, this.vertHead);
		int[] newBaseX = new int[length];
		int[] newBaseY = new int[length];
		int[] newBaseZ = new int[length];
		int[] newGroups = new int[length];
		for (int i = 0; i < newGroups.length; i++) {
			newGroups[i] = -1;
		}
		if (this.fishingSpotClarityRippleGroup != null) {
			System.arraycopy(this.fishingSpotClarityBaseX, 0, newBaseX, 0, this.fishingSpotClarityBaseX.length);
			System.arraycopy(this.fishingSpotClarityBaseY, 0, newBaseY, 0, this.fishingSpotClarityBaseY.length);
			System.arraycopy(this.fishingSpotClarityBaseZ, 0, newBaseZ, 0, this.fishingSpotClarityBaseZ.length);
			System.arraycopy(this.fishingSpotClarityRippleGroup, 0, newGroups, 0,
				this.fishingSpotClarityRippleGroup.length);
		}
		this.fishingSpotClarityBaseX = newBaseX;
		this.fishingSpotClarityBaseY = newBaseY;
		this.fishingSpotClarityBaseZ = newBaseZ;
		this.fishingSpotClarityRippleGroup = newGroups;
	}

	private void copyFishingSpotClarityVertexMetadataFrom(RSModel source, int sourceVertex, int destVertex) {
		if (source.fishingSpotClarityRippleGroup == null
			|| sourceVertex < 0
			|| sourceVertex >= source.fishingSpotClarityRippleGroup.length
			|| destVertex < 0
			|| destVertex >= this.vertHead
			|| source.fishingSpotClarityRippleGroup[sourceVertex] < 0) {
			return;
		}
		ensureFishingSpotClarityMetadata();
		this.fishingSpotClarityBaseX[destVertex] = source.fishingSpotClarityBaseX[sourceVertex];
		this.fishingSpotClarityBaseY[destVertex] = source.fishingSpotClarityBaseY[sourceVertex];
		this.fishingSpotClarityBaseZ[destVertex] = source.fishingSpotClarityBaseZ[sourceVertex];
		this.fishingSpotClarityRippleGroup[destVertex] = source.fishingSpotClarityRippleGroup[sourceVertex];
	}

	private static void addBubblePyramid(RSModel model, int x, int z, int radius, int baseY, int height, int color) {
		int north = model.insertVertex(x, baseY, z - radius);
		int east = model.insertVertex(x + radius, baseY, z);
		int south = model.insertVertex(x, baseY, z + radius);
		int west = model.insertVertex(x - radius, baseY, z);
		int apex = model.insertVertex(x, baseY - height, z);

		addFaceLit(model, 3, new int[] {apex, east, north}, color);
		addFaceLit(model, 3, new int[] {apex, south, east}, color);
		addFaceLit(model, 3, new int[] {apex, west, south}, color);
		addFaceLit(model, 3, new int[] {apex, north, west}, color);
		addFaceLit(model, 4, new int[] {north, east, south, west}, color);
	}

	private static void addFaceLit(RSModel model, int count, int[] indices, int color) {
		int face = model.insertFace(count, indices, color, color, false);
		if (face >= 0) {
			model.faceDiffuseLight[face] = model.m_Vb;
		}
	}

	public void setRenderer3DModelKind(Renderer3DModelKind renderer3DModelKind) {
		this.renderer3DModelKind = renderer3DModelKind == null
			? Renderer3DModelKind.UNCLASSIFIED
			: renderer3DModelKind;
	}

	public Renderer3DModelKind getRenderer3DModelKind() {
		return renderer3DModelKind;
	}

	public void setRenderer3DMaterialFamily(Renderer3DMaterialFamily renderer3DMaterialFamily) {
		this.renderer3DMaterialFamily = renderer3DMaterialFamily == null
			? Renderer3DMaterialFamily.UNCLASSIFIED
			: renderer3DMaterialFamily;
	}

	public Renderer3DMaterialFamily getRenderer3DMaterialFamily() {
		return renderer3DMaterialFamily;
	}

	public void setRenderer3DGlowEmitter(int color, int radius, int intensity) {
		this.renderer3DGlowColor = color & 0xffffff;
		this.renderer3DGlowRadius = Math.max(1, radius);
		this.renderer3DGlowIntensity = Math.max(0, Math.min(255, intensity));
	}

	public void clearRenderer3DGlowEmitter() {
		this.renderer3DGlowColor = 0;
		this.renderer3DGlowRadius = 0;
		this.renderer3DGlowIntensity = 0;
	}

	public boolean hasRenderer3DGlowEmitter() {
		return renderer3DGlowRadius > 0 && renderer3DGlowIntensity > 0;
	}

	public int getRenderer3DGlowColor() {
		return renderer3DGlowColor;
	}

	public int getRenderer3DGlowRadius() {
		return renderer3DGlowRadius;
	}

	public int getRenderer3DGlowIntensity() {
		return renderer3DGlowIntensity;
	}

	private void copyRenderer3DGlowEmitterFrom(RSModel source) {
		this.renderer3DGlowColor = source.renderer3DGlowColor;
		this.renderer3DGlowRadius = source.renderer3DGlowRadius;
		this.renderer3DGlowIntensity = source.renderer3DGlowIntensity;
	}

	public int getRenderer3DTransformVersion() {
		return renderer3DTransformVersion;
	}

	public static Renderer3DWorldChunkFrame.ChunkMesh buildRenderer3DObjectChunkMesh(
		int plane,
		int centerSectionX,
		int centerSectionY,
		int originWorldX,
		int originWorldZ,
		RSModel[] models,
		int modelCount) {
		return buildRenderer3DObjectChunkMesh(
			plane,
			centerSectionX,
			centerSectionY,
			originWorldX,
			originWorldZ,
			models,
			modelCount,
			Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS);
	}

	public static Renderer3DWorldChunkFrame.ChunkMesh buildRenderer3DObjectChunkMesh(
		int plane,
		int centerSectionX,
		int centerSectionY,
		int originWorldX,
		int originWorldZ,
		RSModel[] models,
		int modelCount,
		int chunkRole) {
		ObjectChunkMeshBuilder builder = new ObjectChunkMeshBuilder(
			plane,
			centerSectionX,
			centerSectionY,
			originWorldX,
			originWorldZ,
			chunkRole);
		int limit = Math.min(modelCount, models == null ? 0 : models.length);
		for (int index = 0; index < limit; index++) {
			builder.addModel(models[index]);
		}
		return builder.build();
	}

	public void setPickBoundsScale(int pickBoundsScale) {
		this.pickBoundsScale = Math.max(1, pickBoundsScale);
	}

	public int getPickBoundsScale() {
		return pickBoundsScale;
	}

	public String debugTransformedBoundsSummary(int faceIndex) {
		this.resetTransformCache(7972);
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int vertex = 0; vertex < this.vertHead; vertex++) {
			minX = Math.min(minX, this.vertXTransform[vertex]);
			maxX = Math.max(maxX, this.vertXTransform[vertex]);
			minY = Math.min(minY, this.vertYTransform[vertex]);
			maxY = Math.max(maxY, this.vertYTransform[vertex]);
			minZ = Math.min(minZ, this.vertZTransform[vertex]);
			maxZ = Math.max(maxZ, this.vertZTransform[vertex]);
		}
		StringBuilder builder = new StringBuilder();
		builder.append("verts=").append(this.vertHead)
			.append(" faces=").append(this.faceHead)
			.append(" bounds=")
			.append(minX).append("..").append(maxX).append(',')
			.append(minY).append("..").append(maxY).append(',')
			.append(minZ).append("..").append(maxZ);
		if (faceIndex >= 0 && faceIndex < this.faceHead) {
			appendDebugFaceBounds(builder, faceIndex);
		}
		return builder.toString();
	}

	private void appendDebugFaceBounds(StringBuilder builder, int faceIndex) {
		int vertexCount = this.faceIndexCount[faceIndex];
		int[] indices = this.faceIndices[faceIndex];
		if (vertexCount <= 0 || indices == null) {
			builder.append(" face=").append(faceIndex).append(":empty");
			return;
		}
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		builder.append(" face=").append(faceIndex).append(" vertices=");
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			int modelVertex = indices[vertex];
			if (modelVertex < 0 || modelVertex >= this.vertHead) {
				continue;
			}
			if (vertex > 0) {
				builder.append(';');
			}
			int x = this.vertXTransform[modelVertex];
			int y = this.vertYTransform[modelVertex];
			int z = this.vertZTransform[modelVertex];
			builder.append(modelVertex).append(':').append(x).append(',').append(y).append(',').append(z);
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
			minZ = Math.min(minZ, z);
			maxZ = Math.max(maxZ, z);
		}
		builder.append(" faceBounds=")
			.append(minX).append("..").append(maxX).append(',')
			.append(minY).append("..").append(maxY).append(',')
			.append(minZ).append("..").append(maxZ);
	}

	private RSModel(RSModel[] var1, int var2, boolean var3, boolean var4, boolean noDiffuseCompute, boolean var6) {
		try {
			this.dontComputeDiffuse = noDiffuseCompute;
			this.m_c = var4;
			this.m_db = var6;
			this.m_v = var3;
			this.addModels(0, var1, false, var2);
		} catch (RuntimeException var8) {
			throw GenUtil.makeThrowable(var8, "ca.<init>(" + (var1 != null ? "{...}" : "null") + ',' + var2 + ',' + var3
				+ ',' + var4 + ',' + noDiffuseCompute + ',' + var6 + ')');
		}
	}

	public RSModel(String var1) {
		try {
			byte[] var23;
			try {
				InputStream var5 = GenUtil.chooseStreamFor(var1);
				DataInputStream var6 = new DataInputStream(var5);
				var23 = new byte[3];
				int var21 = 0;
				this.m_hb = 0;

				while (true) {
					if (var21 >= 3) {
						int var22 = this.getFaceParam1((byte) 76, (byte[]) var23);
						var21 = 0;
						this.m_hb = 0;

						for (var23 = new byte[var22]; var22 > var21; var21 += var6.read(var23, var21, var22 - var21)) {
							;
						}

						var6.close();
						break;
					}

					var21 += var6.read(var23, var21, 3 - var21);
				}
			} catch (IOException var19) {
				this.faceHead = 0;
				this.vertHead = 0;
				return;
			}

			int var24 = this.getFaceParam1((byte) 76, (byte[]) var23);
			int var25 = this.getFaceParam1((byte) 76, (byte[]) var23);
			this.setFaceVertexCount(var25, var24, (int) 97);
			this.faceParam1 = new int[var25][];

			int face;
			for (face = 0; var24 > face; ++face) {
				int var7 = this.getFaceParam1((byte) 76, (byte[]) var23);
				int var8 = this.getFaceParam1((byte) 76, (byte[]) var23);
				int var9 = this.getFaceParam1((byte) 76, (byte[]) var23);
				this.insertVertex(var7, var8, var9);
			}

			for (face = 0; face < var25; ++face) {
				int var10 = this.getFaceParam1((byte) 76, (byte[]) var23);
				int var11 = this.getFaceParam1((byte) 76, (byte[]) var23);
				int var12 = this.getFaceParam1((byte) 76, (byte[]) var23);
				int var13 = this.getFaceParam1((byte) 76, (byte[]) var23);
				this.diffuseParam2 = this.getFaceParam1((byte) 76, (byte[]) var23);
				this.diffuseParam1 = this.getFaceParam1((byte) 76, (byte[]) var23);
				int var26 = this.getFaceParam1((byte) 76, (byte[]) var23);
				int[] var16 = new int[var10];

				for (int var17 = 0; var17 < var10; ++var17) {
					var16[var17] = this.getFaceParam1((byte) 76, (byte[]) var23);
				}

				int[] fP1 = new int[var13];

				for (int i = 0; var13 > i; ++i) {
					fP1[i] = this.getFaceParam1((byte) 76, (byte[]) var23);
				}

				int fID = this.insertFace(var10, var16, var11, var12, false);
				this.faceParam1[face] = fP1;
				if (var26 == 0) {
					this.faceDiffuseLight[fID] = 0;
				} else {
					this.faceDiffuseLight[fID] = this.m_Vb;
				}
			}

			this.m_Yb = 1;
		} catch (RuntimeException var20) {
			throw GenUtil.makeThrowable(var20, "ca.<init>(" + (var1 != null ? "{...}" : "null") + ')');
		}
	}

	final RSModel copyModel(boolean var1, int var2, boolean noDiffuse, boolean var4, boolean var5) {
		try {

			RSModel[] var6 = new RSModel[]{this};
			RSModel copy = new RSModel(var6, 1, var4, var5, noDiffuse, var1);
			copy.m_hc = this.m_hc;
			copy.renderer3DModelKind = this.renderer3DModelKind;
			copy.renderer3DMaterialFamily = this.renderer3DMaterialFamily;
			copy.copyRenderer3DGlowEmitterFrom(this);
			copy.pickBoundsScale = this.pickBoundsScale;
			return copy;
		} catch (RuntimeException var9) {
			throw GenUtil.makeThrowable(var9,
				"ca.Q(" + var1 + ',' + var2 + ',' + noDiffuse + ',' + var4 + ',' + var5 + ')');
		}
	}

	private void addModels(int offset, RSModel[] models, boolean var3, int modelCount) {
		try {

			int faceCount = 0;
			int vertCount = 0;

			int i;
			for (i = 0; i < modelCount; ++i) {
				faceCount += models[i].faceHead;
				vertCount += models[i].vertHead;
			}

			this.setFaceVertexCount(faceCount, vertCount, (int) 88);
			if (var3) {
				this.faceParam1 = new int[faceCount][];
			}

			for (i = offset; i < modelCount; ++i) {
				RSModel model = models[i];
				model.commitTransform((byte) -28);
				this.diffuseMag = model.diffuseMag;
				this.diffuseDirX = model.diffuseDirX;
				this.diffuseDirY = model.diffuseDirY;
				this.diffuseParam2 = model.diffuseParam2;
				this.diffuseDirZ = model.diffuseDirZ;
				this.diffuseParam1 = model.diffuseParam1;

				for (int f = 0; model.faceHead > f; ++f) {
					int[] newIndices = new int[model.faceIndexCount[f]];
					int[] srcIndices = model.faceIndices[f];

					int v;
					for (v = 0; model.faceIndexCount[f] > v; ++v) {
						newIndices[v] = this.insertVertex(model.vertX[srcIndices[v]], model.vertY[srcIndices[v]],
							model.vertZ[srcIndices[v]]);
						this.copyFishingSpotClarityVertexMetadataFrom(model, srcIndices[v], newIndices[v]);
					}

					v = this.insertFace(model.faceIndexCount[f], newIndices, model.faceTextureFront[f],
						model.faceTextureBack[f], false);
					this.faceDiffuseLight[v] = model.faceDiffuseLight[f];
					this.scenePolyNormalShift[v] = model.scenePolyNormalShift[f];
					this.scenePolyNormalMagnitude[v] = model.scenePolyNormalMagnitude[f];
					if (var3) {
						int j;
						if (modelCount <= 1) {
							this.faceParam1[v] = new int[model.faceParam1[f].length];

							for (j = 0; j < model.faceParam1[f].length; ++j) {
								this.faceParam1[v][j] = model.faceParam1[f][j];
							}
						} else {
							this.faceParam1[v] = new int[model.faceParam1[f].length + 1];
							this.faceParam1[v][0] = i;

							for (j = 0; j < model.faceParam1[f].length; ++j) {
								this.faceParam1[v][1 + j] = model.faceParam1[f][j];
							}
						}
					}
				}
			}

			this.m_Yb = 1;
		} catch (RuntimeException var14) {
			throw GenUtil.makeThrowable(var14, "ca.KA(" + offset + ',' + (models != null ? "{...}" : "null") + ','
				+ var3 + ',' + modelCount + ')');
		}
	}

	public final void addRotation(int rotX, int rotY, int rotZ) {
		try {

			this.rot256X = rotX + this.rot256X & 255;
			this.rot256Y = rotY + this.rot256Y & 255;
			this.rot256Z = 255 & this.rot256Z + rotZ;
			this.computeAppliedTransform();
			this.m_Yb = 1;
			this.renderer3DTransformVersion++;
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "ca.GA(" + rotZ + ',' + "dummy" + ',' + rotY + ',' + rotX + ')');
		}
	}

	/**
	 * 0-255 -> 0-1
	 */
	private void applyRotMatrix(int xToZ, int zToY, int yToX, int yToZ, int zToX, int xToY, byte var7) {
		try {


			for (int i = 0; i < this.vertHead; ++i) {
				if (yToX != 0) {
					this.vertXTransform[i] += this.vertYTransform[i] * yToX >> 8;
				}
				if (yToZ != 0) {
					this.vertZTransform[i] += yToZ * this.vertYTransform[i] >> 8;
				}
				if (zToX != 0) {
					this.vertXTransform[i] += zToX * this.vertZTransform[i] >> 8;
				}
				if (zToY != 0) {
					this.vertYTransform[i] += zToY * this.vertZTransform[i] >> 8;
				}
				if (xToZ != 0) {
					this.vertZTransform[i] += xToZ * this.vertXTransform[i] >> 8;
				}
				if (xToY != 0) {
					this.vertYTransform[i] += this.vertXTransform[i] * xToY >> 8;
				}
			}

		} catch (RuntimeException var10) {
			throw GenUtil.makeThrowable(var10,
				"ca.E(" + xToZ + ',' + zToY + ',' + yToX + ',' + yToZ + ',' + zToX + ',' + xToY + ',' + var7 + ')');
		}
	}

	private void calculateBoundingBoxes(int var1) {
		try {

			this.minX = 999999;
			this.minZ = 999999;
			this.maxZ = -999999;
			this.minY = var1;
			this.maxX = -999999;
			this.maxY = -999999;
			this.maxFaceDimension = -999999;

			for (int face = 0; face < this.faceHead; ++face) {
				int[] fIndex = this.faceIndices[face];
				int fIndexCount = this.faceIndexCount[face];
				int vID = fIndex[0];
				int minZ;
				int maxZ = minZ = this.vertZTransform[vID];
				int minY;
				int maxY = minY = this.vertYTransform[vID];
				int minX;
				int maxX = minX = this.vertXTransform[vID];

				for (int vert = 0; fIndexCount > vert; ++vert) {
					vID = fIndex[vert];
					if (this.vertZTransform[vID] >= minZ) {
						if (this.vertZTransform[vID] > maxZ) {
							maxZ = this.vertZTransform[vID];
						}
					} else {
						minZ = this.vertZTransform[vID];
					}

					if (this.vertYTransform[vID] < minY) {
						minY = this.vertYTransform[vID];
					} else if (this.vertYTransform[vID] > maxY) {
						maxY = this.vertYTransform[vID];
					}

					if (minX <= this.vertXTransform[vID]) {
						if (this.vertXTransform[vID] > maxX) {
							maxX = this.vertXTransform[vID];
						}
					} else {
						minX = this.vertXTransform[vID];
					}
				}

				if (!this.m_c) {
					this.faceMinX[face] = minX;
					this.faceMaxX[face] = maxX;
					this.faceMinY[face] = minY;
					this.faceMaxY[face] = maxY;
					this.faceMinZ[face] = minZ;
					this.faceMaxZ[face] = maxZ;
				}

				if (maxX - minX > this.maxFaceDimension) {
					this.maxFaceDimension = maxX - minX;
				}

				if (maxY - minY > this.maxFaceDimension) {
					this.maxFaceDimension = maxY - minY;
				}

				if (this.maxX < maxX) {
					this.maxX = maxX;
				}

				if (maxZ > this.maxZ) {
					this.maxZ = maxZ;
				}

				if (maxZ - minZ > this.maxFaceDimension) {
					this.maxFaceDimension = maxZ - minZ;
				}

				if (this.maxY < maxY) {
					this.maxY = maxY;
				}

				if (minX < this.minX) {
					this.minX = minX;
				}

				if (minY < this.minY) {
					this.minY = minY;
				}

				if (this.minZ > minZ) {
					this.minZ = minZ;
				}
			}

		} catch (RuntimeException var13) {
			throw GenUtil.makeThrowable(var13, "ca.C(" + var1 + ')');
		}
	}

	private void clearRotDataAndParams26(byte var1) {
		try {
			if (var1 < 49) {
				this.setDiffuseLight(40, 102, 104, 108, -20, -89);
			}

			this.vertexParam2 = new int[this.vertHead];
			this.vertXRot = new int[this.vertHead];
			this.vertYRot = new int[this.vertHead];
			this.vertZRot = new int[this.vertHead];
			this.vertexParam6 = new int[this.vertHead];

		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "ca.H(" + var1 + ')');
		}
	}

	@Override
	public final RSModel clone() {
		try {

			RSModel[] var2 = new RSModel[]{this};
			RSModel var3 = new RSModel(var2, 1);
			var3.m_cb = this.m_cb;
			var3.m_hc = this.m_hc;
			var3.renderer3DModelKind = this.renderer3DModelKind;
			var3.renderer3DMaterialFamily = this.renderer3DMaterialFamily;
			var3.copyRenderer3DGlowEmitterFrom(this);
			var3.pickBoundsScale = this.pickBoundsScale;
			return var3;
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "ca.IA(" + -2 + ')');
		}
	}

	private void commitTransform(byte var1) {
		try {
			this.resetTransformCache((int) 7972);


			for (int i = 0; i < this.vertHead; ++i) {
				this.vertX[i] = this.vertXTransform[i];
				this.vertY[i] = this.vertYTransform[i];
				this.vertZ[i] = this.vertZTransform[i];
			}

			this.rot256Z = 0;
			this.rot256X = 0;
			this.rotM_xToZ = 256;
			this.rotM_zToY = 256;
			this.translateY = 0;
			this.rot256Y = 0;
			this.rotM_xToY = 256;
			this.rotM_zToX = 256;
			this.scaleX = 256;
			this.appliedTransform = 0;
			this.translateX = 0;
			this.scaleY = 256;
			this.rotM_yToX = 256;
			this.scaleZ = 256;
			this.translateZ = 0;
			this.rotM_yToZ = 256;
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "ca.R(" + -28 + ')');
		}
	}

	private void computeAppliedTransform() {
		try {

			if (this.rotM_yToX == 256 && this.rotM_yToZ == 256 && this.rotM_zToX == 256 && this.rotM_zToY == 256
				&& this.rotM_xToZ == 256 && this.rotM_xToY == 256) {
				if (this.scaleX == 256 && this.scaleY == 256 && this.scaleZ == 256) {
					if (this.rot256X == 0 && this.rot256Y == 0 && this.rot256Z == 0) {
						if (this.translateX == 0 && this.translateY == 0 && this.translateZ == 0) {
							this.appliedTransform = 0;
						} else {
							this.appliedTransform = 1;
						}
					} else {
						this.appliedTransform = 2;
					}
				} else {
					this.appliedTransform = 3;
				}
			} else {
				this.appliedTransform = 4;
			}
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "ca.A(" + "dummy" + ')');
		}
	}

	private void computeDiffuse(int var1) {
		try {

			if (!this.dontComputeDiffuse) {
				int diffuseDivide = this.diffuseParam2 * this.diffuseMag >> 8;

				for (int f = 0; this.faceHead > f; ++f) {
					if (this.faceDiffuseLight[f] != this.m_Vb) {
						this.faceDiffuseLight[f] = (this.faceNormY[f] * this.diffuseDirY
							+ this.faceNormX[f] * this.diffuseDirX + this.diffuseDirZ * this.faceNormZ[f])
							/ diffuseDivide;
					}
				}

				int[] tmpXNorm = new int[this.vertHead];
				int[] tmpYNorm = new int[this.vertHead];
				int[] tmpZNorm = new int[this.vertHead];
				int[] faceCount = new int[this.vertHead];

				int var7;
				for (var7 = 0; this.vertHead > var7; ++var7) {
					tmpXNorm[var7] = 0;
					tmpYNorm[var7] = 0;
					tmpZNorm[var7] = 0;
					faceCount[var7] = 0;
				}

				var7 = -16 / ((var1 + 55) / 32);

				int i;
				for (i = 0; i < this.faceHead; ++i) {
					if (this.m_Vb == this.faceDiffuseLight[i]) {
						for (int fi = 0; this.faceIndexCount[i] > fi; ++fi) {
							int fVert = this.faceIndices[i][fi];
							tmpXNorm[fVert] += this.faceNormX[i];
							tmpYNorm[fVert] += this.faceNormY[i];
							tmpZNorm[fVert] += this.faceNormZ[i];
							++faceCount[fVert];
						}
					}
				}

				for (i = 0; this.vertHead > i; ++i) {
					if (faceCount[i] > 0) {
						this.vertDiffuseLight[i] = (tmpZNorm[i] * this.diffuseDirZ + tmpXNorm[i] * this.diffuseDirX
							+ tmpYNorm[i] * this.diffuseDirY) / (diffuseDivide * faceCount[i]);
					}
				}

			}
		} catch (RuntimeException var11) {
			throw GenUtil.makeThrowable(var11, "ca.FA(" + var1 + ')');
		}
	}

	private void computeNormals(byte var1) {
		try {

			if (!this.dontComputeDiffuse || !this.m_c) {
				if (var1 == 14) {
					for (int face = 0; this.faceHead > face; ++face) {
						int[] var3 = this.faceIndices[face];
						int xp = this.vertXTransform[var3[0]];
						int yp = this.vertYTransform[var3[0]];
						int zp = this.vertZTransform[var3[0]];

						int x21 = this.vertXTransform[var3[1]] - xp;
						int y21 = this.vertYTransform[var3[1]] - yp;
						int z21 = this.vertZTransform[var3[1]] - zp;

						int x31 = this.vertXTransform[var3[2]] - xp;
						int y31 = this.vertYTransform[var3[2]] - yp;
						int z31 = this.vertZTransform[var3[2]] - zp;

						int xN = z31 * y21 - z21 * y31;
						int yN = z21 * x31 - x21 * z31;
						int zN = x21 * y31 - x31 * y21;

						while (xN > 8192 || yN > 8192 || zN > 8192 || xN < -8192 || yN < -8192 || zN < -8192) {
							yN >>= 1;
							zN >>= 1;
							xN >>= 1;
						}

						int mag = (int) (Math.sqrt((double) (yN * yN + xN * xN + zN * zN)) * 256.0D);
						if (mag <= 0) {
							mag = 1;
						}

						this.faceNormX[face] = xN * 65536 / mag;
						this.faceNormY[face] = yN * 65536 / mag;
						this.faceNormZ[face] = zN * 65535 / mag;
						this.scenePolyNormalShift[face] = -1;
					}

					this.computeDiffuse(var1 ^ -85);
				}
			}
		} catch (RuntimeException var17) {
			throw GenUtil.makeThrowable(var17, "ca.K(" + var1 + ')');
		}
	}

	private void copyFaceTo(int[] faceVert, RSModel dest, int faceVertCount, int srcFaceID, int var5) {
		try {

			int[] nVerts = new int[faceVertCount];

			int i;
			for (i = 0; i < faceVertCount; ++i) {
				int vID = nVerts[i] = dest.insertVertex(this.vertX[faceVert[i]], this.vertY[faceVert[i]],
					this.vertZ[faceVert[i]]);
				dest.vertDiffuseLight[vID] = this.vertDiffuseLight[faceVert[i]];
				dest.vertLightOther[vID] = this.vertLightOther[faceVert[i]];
			}

			if (var5 != 5916) {
				this.scaleX = 77;
			}

			i = dest.insertFace(faceVertCount, nVerts, this.faceTextureFront[srcFaceID],
				this.faceTextureBack[srcFaceID], false);
			if (!dest.m_db && !this.m_db) {
				dest.facePickIndex[i] = this.facePickIndex[srcFaceID];
			}

			dest.faceDiffuseLight[i] = this.faceDiffuseLight[srcFaceID];
			dest.scenePolyNormalShift[i] = this.scenePolyNormalShift[srcFaceID];
			dest.scenePolyNormalMagnitude[i] = this.scenePolyNormalMagnitude[srcFaceID];
		} catch (RuntimeException var9) {
			throw GenUtil.makeThrowable(var9, "ca.D(" + (faceVert != null ? "{...}" : "null") + ','
				+ (dest != null ? "{...}" : "null") + ',' + faceVertCount + ',' + srcFaceID + ',' + var5 + ')');
		}
	}

	public final void copyRot256AndTranslateFrom(RSModel model, int var2) {
		try {
			if (var2 != 6029) {
				this.appliedTransform = -128;
			}


			this.translateX = model.translateX;
			this.translateY = model.translateY;
			this.translateZ = model.translateZ;
			this.rot256X = model.rot256X;
			this.rot256Y = model.rot256Y;
			this.rot256Z = model.rot256Z;
			this.computeAppliedTransform();
			this.m_Yb = 1;
			this.renderer3DTransformVersion++;
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "ca.AA(" + "{...}" + ',' + var2 + ')');
		}
	}

	final RSModel[] divideModelByGrid(int var1, int xDim, int zGridSize, int var4, int outModels, int limitVertCount,
									  int xGridSize, boolean var8, int var9) {
		try {

			this.commitTransform((byte) -28);
			int[] outVertCount = new int[outModels];
			int[] outFaceCount = new int[outModels];

			for (int i = 0; outModels > i; ++i) {
				outVertCount[i] = 0;
				outFaceCount[i] = 0;
			}

			for (int i = 0; i < this.faceHead; ++i) {
				int sumX = 0;
				int sumZ = 0;
				int faceVertCount = this.faceIndexCount[i];
				int[] faceVert = this.faceIndices[i];

				for (int j = 0; faceVertCount > j; ++j) {
					sumX += this.vertX[faceVert[j]];
					sumZ += this.vertZ[faceVert[j]];
				}

				int id = sumX / (faceVertCount * xGridSize) + sumZ / (zGridSize * faceVertCount) * xDim;
				outVertCount[id] += faceVertCount;
				++outFaceCount[id];
			}

			RSModel[] output = new RSModel[outModels];

			for (int i = 0; outModels > i; ++i) {
				if (limitVertCount < outVertCount[i]) {
					outVertCount[i] = limitVertCount;
				}

				output[i] = new RSModel(outVertCount[i], outFaceCount[i], true, true, true, var8, true);
				output[i].renderer3DModelKind = this.renderer3DModelKind;
				output[i].renderer3DMaterialFamily = this.renderer3DMaterialFamily;
				output[i].copyRenderer3DGlowEmitterFrom(this);
				output[i].diffuseParam2 = this.diffuseParam2;
				output[i].diffuseParam1 = this.diffuseParam1;
			}

			for (int f = 0; this.faceHead > f; ++f) {
				int sumX = 0;
				int sumZ = 0;
				int srcIndexCount = this.faceIndexCount[f];
				int[] srcIndices = this.faceIndices[f];

				for (int i = 0; i < srcIndexCount; ++i) {
					sumX += this.vertX[srcIndices[i]];
					sumZ += this.vertZ[srcIndices[i]];
				}

				int id = sumX / (srcIndexCount * xGridSize) + xDim * (sumZ / (zGridSize * srcIndexCount));
				this.copyFaceTo(srcIndices, output[id], srcIndexCount, f, 5916);
			}

			for (int sumX = 0; outModels > sumX; ++sumX) {
				output[sumX].clearRotDataAndParams26((byte) 71);
			}
			return output;
		} catch (RuntimeException var19) {
			throw GenUtil.makeThrowable(var19, "ca.J(" + 0 + ',' + xDim + ',' + zGridSize + ',' + var4 + ',' + outModels
				+ ',' + limitVertCount + ',' + xGridSize + ',' + var8 + ',' + var9 + ')');
		}
	}

	private int getFaceParam1(byte var1, byte[] data) {
		try {


			while (data[this.m_hb] == 10 || data[this.m_hb] == 13) {
				++this.m_hb;
			}

			int var3 = MiscFunctions.class14_s_d[255 & data[this.m_hb++]];
			int var4 = MiscFunctions.class14_s_d[255 & data[this.m_hb++]];
			int var5 = MiscFunctions.class14_s_d[data[this.m_hb++] & 255];
			int param = var4 * 64 - 131072 + var3 * 4096 + var5;
			if (param == 123456) {
				param = this.m_Vb;
			}

			return param;
		} catch (RuntimeException var7) {
			throw GenUtil.makeThrowable(var7, "ca.JA(" + 76 + ',' + (data != null ? "{...}" : "null") + ')');
		}
	}

	public final int insertFace(int indexCount, int[] indices, int texFront, int texBack, boolean var5) {
		try {

			if (var5) {
				this.addRotation(23, 10, 30);
			}

			this.ensureCapacity(this.vertexCount2, this.faceHead + 1);
			this.faceIndexCount[this.faceHead] = indexCount;
			this.faceIndices[this.faceHead] = indices;
			this.faceTextureFront[this.faceHead] = texFront;
			this.faceTextureBack[this.faceHead] = texBack;
			if (this.faceParam1 != null && this.faceParam1[this.faceHead] == null) {
				this.faceParam1[this.faceHead] = new int[]{this.faceHead};
			}
			this.m_Yb = 1;
			return this.faceHead++;
		} catch (RuntimeException var7) {
			throw GenUtil.makeThrowable(var7, "ca.P(" + indexCount + ',' + (indices != null ? "{...}" : "null") + ','
				+ texFront + ',' + texBack + ',' + var5 + ')');
		}
	}

	public final int insertVertex(int x, int y, int z) {
		try {


			for (int i = 0; this.vertHead > i; ++i) {
				if (x == this.vertX[i] && y == this.vertY[i] && z == this.vertZ[i]) {
					return i;
				}
			}

			this.ensureCapacity(this.vertHead + 1, this.faceCount);
			this.vertX[this.vertHead] = x;
			this.vertY[this.vertHead] = y;
			this.vertZ[this.vertHead] = z;
			return this.vertHead++;
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "ca.I(" + x + ',' + z + ',' + y + ',' + "dummy" + ')');
		}
	}

	final int insertVertex2(boolean var1, int z, int x, int y) {
		try {

			this.ensureCapacity(this.vertHead + 1, this.faceCount);
			this.vertX[this.vertHead] = x;
			this.vertY[this.vertHead] = y;
			this.vertZ[this.vertHead] = z;
			return this.vertHead++;
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "ca.S(" + false + ',' + z + ',' + x + ',' + y + ')');
		}
	}

	final void removeFacesAndOrVerts(int deleteVerts, int var2, int deleteFaces) {
		try {

			this.faceHead -= deleteFaces;

			if (this.faceHead < 0) {
				this.faceHead = 0;
			}

			this.vertHead -= deleteVerts;
			if (this.vertHead < 0) {
				this.vertHead = 0;
			}

		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5, "ca.F(" + deleteVerts + ',' + var2 + ',' + deleteFaces + ')');
		}
	}

	final void resetFaceVertHead(int var1) {
		try {
			this.faceHead = 0;
			this.vertHead = 0;

		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "ca.M(" + 1 + ')');
		}
	}

	private void resetTransformCache(int var1) {
		try {
			if (var1 != 7972) {
				this.translate2(120, 57, 20);
			}


			int i;
			if (this.m_Yb == 2) {
				this.m_Yb = 0;

				for (i = 0; i < this.vertHead; ++i) {
					this.vertXTransform[i] = this.vertX[i];
					this.vertYTransform[i] = this.vertY[i];
					this.vertZTransform[i] = this.vertZ[i];
				}

				this.maxX = 9999999;
				this.maxY = 9999999;
				this.minY = -9999999;
				this.maxZ = 9999999;
				this.minZ = -9999999;
				this.maxFaceDimension = 9999999;
				this.minX = -9999999;
			} else if (this.m_Yb == 1) {
				this.m_Yb = 0;

				for (i = 0; this.vertHead > i; ++i) {
					this.vertXTransform[i] = this.vertX[i];
					this.vertYTransform[i] = this.vertY[i];
					this.vertZTransform[i] = this.vertZ[i];
				}

				if (this.appliedTransform >= 2) {
					this.rotate256(-53, this.rot256X, this.rot256Z, this.rot256Y);
				}

				if (this.appliedTransform >= 3) {
					this.scale(this.scaleX, -27483, this.scaleZ, this.scaleY);
				}

				if (this.appliedTransform >= 4) {
					this.applyRotMatrix(this.rotM_xToZ, this.rotM_zToY, this.rotM_yToX, this.rotM_yToZ, this.rotM_zToX,
						this.rotM_xToY, (byte) -127);
				}

				if (this.appliedTransform >= 1) {
					this.translate(var1 - 7972, this.translateY, this.translateZ, this.translateX);
				}

				this.calculateBoundingBoxes(999999);
				this.computeNormals((byte) 14);
			}

		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "ca.V(" + var1 + ')');
		}
	}

	private static final class ObjectChunkMeshBuilder {
		private static final int INITIAL_NUMERIC_CAPACITY = 64;

		private final int plane;
		private final int centerSectionX;
		private final int centerSectionY;
		private final int originWorldX;
		private final int originWorldZ;
		private final int chunkRole;
		private final IntArrayBuilder vertexCoords =
			new IntArrayBuilder(INITIAL_NUMERIC_CAPACITY * 3);
		private final FloatArrayBuilder vertexTextureU =
			new FloatArrayBuilder(INITIAL_NUMERIC_CAPACITY);
		private final FloatArrayBuilder vertexTextureV =
			new FloatArrayBuilder(INITIAL_NUMERIC_CAPACITY);
		private final IntArrayBuilder vertexLights =
			new IntArrayBuilder(INITIAL_NUMERIC_CAPACITY);
		private final IntArrayBuilder indices =
			new IntArrayBuilder(INITIAL_NUMERIC_CAPACITY);
		private final IntArrayBuilder triangleTextures =
			new IntArrayBuilder(INITIAL_NUMERIC_CAPACITY / 3);
		private final IntArrayBuilder triangleFallbackColors =
			new IntArrayBuilder(INITIAL_NUMERIC_CAPACITY / 3);
		private final List<Renderer3DModelKind> triangleModelKinds = new ArrayList<Renderer3DModelKind>();
		private final List<Renderer3DMaterialFamily> triangleMaterialFamilies =
			new ArrayList<Renderer3DMaterialFamily>();
		private final List<Renderer3DWorldChunkFrame.ShadowCaster> shadowCasters =
			new ArrayList<Renderer3DWorldChunkFrame.ShadowCaster>();
		private final List<Renderer3DWorldChunkFrame.GlowEmitter> glowEmitters =
			new ArrayList<Renderer3DWorldChunkFrame.GlowEmitter>();

		private ObjectChunkMeshBuilder(
			int plane,
			int centerSectionX,
			int centerSectionY,
			int originWorldX,
			int originWorldZ,
			int chunkRole) {
			this.plane = plane;
			this.centerSectionX = centerSectionX;
			this.centerSectionY = centerSectionY;
			this.originWorldX = originWorldX;
			this.originWorldZ = originWorldZ;
			this.chunkRole = chunkRole;
		}

		private void addModel(RSModel model) {
			if (model == null) {
				return;
			}
			Renderer3DModelKind kind = model.getRenderer3DModelKind();
			Renderer3DMaterialFamily family = model.getRenderer3DMaterialFamily();
				if (kind != Renderer3DModelKind.GAME_OBJECT && kind != Renderer3DModelKind.WALL_OBJECT) {
					return;
				}
				model.resetTransformCache(7972);
				addModelShadowCaster(kind, model);
				addModelGlowEmitter(kind, model);
				for (int face = 0; face < model.faceHead; face++) {
				int vertexCount = model.faceIndexCount[face];
				if (vertexCount < 3) {
					continue;
				}
				int[] faceVertexCoords = new int[vertexCount * 3];
				int[] frontVertexLights = new int[vertexCount];
				int[] backVertexLights = new int[vertexCount];
				for (int vertex = 0; vertex < vertexCount; vertex++) {
					int modelVertex = model.faceIndices[face][vertex];
					int coord = vertex * 3;
					faceVertexCoords[coord] = model.vertXTransform[modelVertex];
					faceVertexCoords[coord + 1] = model.vertYTransform[modelVertex];
					faceVertexCoords[coord + 2] = model.vertZTransform[modelVertex];
					frontVertexLights[vertex] = objectLegacyLightForVertex(model, face, modelVertex, true);
					backVertexLights[vertex] = objectLegacyLightForVertex(model, face, modelVertex, false);
				}
				addFace(
					kind,
					family,
					model.faceTextureFront[face],
					model.faceTextureBack[face],
					faceVertexCoords,
					frontVertexLights,
					backVertexLights);
			}
		}

		private int objectLegacyLightForVertex(RSModel model, int face, int modelVertex, boolean frontSide) {
			if (model.faceDiffuseLight[face] == model.m_Vb) {
				int vertexOther = model.vertLightOther == null ? 0 : model.vertLightOther[modelVertex];
				int vertexDiffuse = model.vertDiffuseLight == null ? 0 : model.vertDiffuseLight[modelVertex];
				return frontSide
					? model.diffuseParam1 + vertexOther - vertexDiffuse
					: model.diffuseParam1 + vertexOther + vertexDiffuse;
			}
			return frontSide
				? model.diffuseParam1 - model.faceDiffuseLight[face]
				: model.diffuseParam1 + model.faceDiffuseLight[face];
		}

		private void addFace(
			Renderer3DModelKind kind,
			Renderer3DMaterialFamily family,
			int frontMaterial,
			int backMaterial,
			int[] faceVertexCoords,
			int[] frontVertexLights,
			int[] backVertexLights) {
			int vertexCount = faceVertexCoords == null ? 0 : faceVertexCoords.length / 3;
			if (vertexCount < 3) {
				return;
			}
			boolean visibleFront = isVisibleMaterial(frontMaterial);
			boolean visibleBack = isVisibleMaterial(backMaterial);
			if (visibleFront) {
				addMaterialSide(kind, family, frontMaterial, faceVertexCoords, frontVertexLights, false);
			}
			if (visibleBack) {
				addMaterialSide(kind, family, backMaterial, faceVertexCoords, backVertexLights, true);
			}
			if (visibleFront != visibleBack) {
				int visibleMaterial = visibleFront ? frontMaterial : backMaterial;
				addMaterialSide(
					kind,
					family,
					visibleMaterial,
					faceVertexCoords,
					visibleFront ? backVertexLights : frontVertexLights,
					visibleFront);
			}
		}

		private boolean isVisibleMaterial(int material) {
			return material != Scene.TRANSPARENT;
		}

		private void addMaterialSide(
			Renderer3DModelKind kind,
			Renderer3DMaterialFamily family,
			int material,
			int[] faceVertexCoords,
			int[] faceVertexLights,
			boolean reversed) {
			if (material == Scene.TRANSPARENT) {
				return;
			}
			int vertexCount = faceVertexCoords == null ? 0 : faceVertexCoords.length / 3;
			if (vertexCount < 3) {
				return;
			}
			ResolvedMaterial resolved = resolveMaterial(material);
			float[] textureU = new float[vertexCount];
			float[] textureV = new float[vertexCount];
			populateTextureCoordinates(resolved.texture, faceVertexCoords, textureU, textureV);
			for (int vertex = 1; vertex < vertexCount - 1; vertex++) {
				if (reversed) {
					addTriangle(
						kind,
						family,
						resolved.texture,
						resolved.fallbackColor,
						faceVertexCoords,
						faceVertexLights,
						textureU,
						textureV,
						0,
						vertex + 1,
						vertex);
				} else {
					addTriangle(
						kind,
						family,
						resolved.texture,
						resolved.fallbackColor,
						faceVertexCoords,
						faceVertexLights,
						textureU,
						textureV,
						0,
						vertex,
						vertex + 1);
				}
			}
		}

		private void addTriangle(
			Renderer3DModelKind kind,
			Renderer3DMaterialFamily family,
			int texture,
			int fallbackColor,
			int[] faceVertexCoords,
			int[] faceVertexLights,
			float[] textureU,
			float[] textureV,
			int a,
			int b,
			int c) {
			int baseVertex = vertexCoords.size() / 3;
			addVertex(faceVertexCoords, faceVertexLights, textureU, textureV, a);
			addVertex(faceVertexCoords, faceVertexLights, textureU, textureV, b);
			addVertex(faceVertexCoords, faceVertexLights, textureU, textureV, c);
			indices.add(baseVertex);
			indices.add(baseVertex + 1);
			indices.add(baseVertex + 2);
			triangleTextures.add(texture);
			triangleFallbackColors.add(fallbackColor);
			triangleModelKinds.add(kind);
			triangleMaterialFamilies.add(family == null
				? Renderer3DMaterialClassifier.fallbackFor(kind)
				: family);
		}

		private void addModelShadowCaster(Renderer3DModelKind kind, RSModel model) {
			if (model.vertHead <= 0) {
				return;
			}
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int minY = Integer.MAX_VALUE;
			int maxY = Integer.MIN_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (int vertex = 0; vertex < model.vertHead; vertex++) {
				minX = Math.min(minX, model.vertXTransform[vertex]);
				maxX = Math.max(maxX, model.vertXTransform[vertex]);
				minY = Math.min(minY, model.vertYTransform[vertex]);
				maxY = Math.max(maxY, model.vertYTransform[vertex]);
				minZ = Math.min(minZ, model.vertZTransform[vertex]);
				maxZ = Math.max(maxZ, model.vertZTransform[vertex]);
			}
			if (minX == Integer.MAX_VALUE || minY == Integer.MAX_VALUE || minZ == Integer.MAX_VALUE) {
				return;
			}
			int spanX = maxX - minX;
			int spanZ = maxZ - minZ;
			int height = maxY - minY;
			if (height <= 0 || (spanX <= 0 && spanZ <= 0)) {
				return;
			}
			if (!isPlausibleModelShadowCaster(kind, spanX, spanZ)) {
				return;
			}
			int centerX = (minX + maxX) / 2;
			int centerZ = (minZ + maxZ) / 2;
			int baseX0;
			int baseZ0;
			int baseX1;
			int baseZ1;
			if (spanX >= spanZ) {
				baseX0 = minX;
				baseZ0 = centerZ;
				baseX1 = maxX;
				baseZ1 = centerZ;
			} else {
				baseX0 = centerX;
				baseZ0 = minZ;
				baseX1 = centerX;
				baseZ1 = maxZ;
			}
			int width = Math.max(1, Math.max(spanX, spanZ));
			int opacity = kind == Renderer3DModelKind.WALL_OBJECT ? 176 : 144;
			shadowCasters.add(new Renderer3DWorldChunkFrame.ShadowCaster(
				kind,
				baseX0,
				minY,
				baseZ0,
				baseX1,
				baseZ1,
					height,
					width,
					opacity,
					true,
					minX,
					maxX,
					minZ,
					maxZ));
			}

		private boolean isPlausibleModelShadowCaster(Renderer3DModelKind kind, int spanX, int spanZ) {
			int maxSpan = kind == Renderer3DModelKind.WALL_OBJECT
				? RENDERER_3D_WALL_OBJECT_SHADOW_MAX_FOOTPRINT
				: RENDERER_3D_OBJECT_SHADOW_MAX_FOOTPRINT;
			return spanX <= maxSpan && spanZ <= maxSpan;
		}

		private void addModelGlowEmitter(Renderer3DModelKind kind, RSModel model) {
			if (!model.hasRenderer3DGlowEmitter() || model.vertHead <= 0) {
				return;
			}
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int minY = Integer.MAX_VALUE;
			int maxY = Integer.MIN_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (int vertex = 0; vertex < model.vertHead; vertex++) {
				minX = Math.min(minX, model.vertXTransform[vertex]);
				maxX = Math.max(maxX, model.vertXTransform[vertex]);
				minY = Math.min(minY, model.vertYTransform[vertex]);
				maxY = Math.max(maxY, model.vertYTransform[vertex]);
				minZ = Math.min(minZ, model.vertZTransform[vertex]);
				maxZ = Math.max(maxZ, model.vertZTransform[vertex]);
			}
			if (minX == Integer.MAX_VALUE || minY == Integer.MAX_VALUE || minZ == Integer.MAX_VALUE) {
				return;
			}
			int spanX = maxX - minX;
			int spanZ = maxZ - minZ;
			int footprintRadius = Math.max(spanX, spanZ) / 2 + model.getRenderer3DGlowRadius();
			glowEmitters.add(new Renderer3DWorldChunkFrame.GlowEmitter(
				kind,
				(minX + maxX) / 2,
				(minY + maxY) / 2,
				(minZ + maxZ) / 2,
				Math.max(model.getRenderer3DGlowRadius(), footprintRadius),
				model.getRenderer3DGlowColor(),
				model.getRenderer3DGlowIntensity()));
		}

		private void addVertex(
			int[] faceVertexCoords,
			int[] faceVertexLights,
			float[] textureU,
			float[] textureV,
			int vertex) {
			int coord = vertex * 3;
			vertexCoords.add(faceVertexCoords[coord]);
			vertexCoords.add(faceVertexCoords[coord + 1]);
			vertexCoords.add(faceVertexCoords[coord + 2]);
			vertexTextureU.add(textureU[vertex]);
			vertexTextureV.add(textureV[vertex]);
			vertexLights.add(
				faceVertexLights == null || vertex >= faceVertexLights.length ? 0 : faceVertexLights[vertex]);
		}

		private ResolvedMaterial resolveMaterial(int material) {
			if (material < 0) {
				return new ResolvedMaterial(Scene.TRANSPARENT, resourceToRgb(material));
			}
			return new ResolvedMaterial(material, Scene.TRANSPARENT);
		}

		private int resourceToRgb(int resource) {
			if (resource == Scene.TRANSPARENT) {
				return 0;
			}
			if (resource >= 0) {
				return resource;
			}
			int encoded = -(resource + 1);
			int red = (encoded & 0x7C00) >> 10;
			int green = (encoded & 0x3E0) >> 5;
			int blue = encoded & 0x1F;
			return (red << 19) + (green << 11) + (blue << 3);
		}

		private void populateTextureCoordinates(
			int texture,
			int[] faceVertexCoords,
			float[] textureU,
			float[] textureV) {
			if (texture < 0 || faceVertexCoords.length < 9) {
				return;
			}
			int last = faceVertexCoords.length / 3 - 1;
			double ux = faceVertexCoords[3] - faceVertexCoords[0];
			double uy = faceVertexCoords[4] - faceVertexCoords[1];
			double uz = faceVertexCoords[5] - faceVertexCoords[2];
			double vx = faceVertexCoords[last * 3] - faceVertexCoords[0];
			double vy = faceVertexCoords[last * 3 + 1] - faceVertexCoords[1];
			double vz = faceVertexCoords[last * 3 + 2] - faceVertexCoords[2];
			double uu = dot(ux, uy, uz, ux, uy, uz);
			double uv = dot(ux, uy, uz, vx, vy, vz);
			double vv = dot(vx, vy, vz, vx, vy, vz);
			double determinant = uu * vv - uv * uv;
			if (Math.abs(determinant) < 0.000001) {
				return;
			}
			for (int vertex = 0; vertex < textureU.length; vertex++) {
				int coord = vertex * 3;
				double px = faceVertexCoords[coord] - faceVertexCoords[0];
				double py = faceVertexCoords[coord + 1] - faceVertexCoords[1];
				double pz = faceVertexCoords[coord + 2] - faceVertexCoords[2];
				double pu = dot(px, py, pz, ux, uy, uz);
				double pv = dot(px, py, pz, vx, vy, vz);
				textureU[vertex] = (float) ((pu * vv - pv * uv) / determinant);
				textureV[vertex] = (float) ((pv * uu - pu * uv) / determinant);
			}
		}

		private static double dot(
			double leftX,
			double leftY,
			double leftZ,
			double rightX,
			double rightY,
			double rightZ) {
			return leftX * rightX + leftY * rightY + leftZ * rightZ;
		}

		private static final class ResolvedMaterial {
			private final int texture;
			private final int fallbackColor;

			private ResolvedMaterial(int texture, int fallbackColor) {
				this.texture = texture;
				this.fallbackColor = fallbackColor;
			}
		}

		private Renderer3DWorldChunkFrame.ChunkMesh build() {
			int[] vertexArray = vertexCoords.toArray();
			float[] textureUArray = vertexTextureU.toArray();
			float[] textureVArray = vertexTextureV.toArray();
			int[] lightArray = vertexLights.toArray();
			int[] indexArray = indices.toArray();
			int[] textureArray = triangleTextures.toArray();
			int[] fallbackArray = triangleFallbackColors.toArray();
			Renderer3DModelKind[] kindArray =
				triangleModelKinds.toArray(new Renderer3DModelKind[triangleModelKinds.size()]);
			Renderer3DMaterialFamily[] familyArray = triangleMaterialFamilies.toArray(
				new Renderer3DMaterialFamily[triangleMaterialFamilies.size()]);
			Renderer3DWorldChunkFrame.ShadowCaster[] shadowCasterArray =
				shadowCasters.toArray(new Renderer3DWorldChunkFrame.ShadowCaster[shadowCasters.size()]);
			Renderer3DWorldChunkFrame.GlowEmitter[] glowEmitterArray =
				glowEmitters.toArray(new Renderer3DWorldChunkFrame.GlowEmitter[glowEmitters.size()]);
			long signature = signature(
				vertexArray,
				textureUArray,
				textureVArray,
				lightArray,
				indexArray,
				textureArray,
				fallbackArray,
				kindArray,
				familyArray,
				shadowCasterArray,
				glowEmitterArray);
			return new Renderer3DWorldChunkFrame.ChunkMesh(
				plane,
				centerSectionX,
				centerSectionY,
				originWorldX,
				originWorldZ,
				vertexArray,
				textureUArray,
				textureVArray,
				lightArray,
				null,
				null,
				indexArray,
				textureArray,
				fallbackArray,
				kindArray,
				familyArray,
				shadowCasterArray,
				glowEmitterArray,
				null,
				null,
				0,
				0,
				0,
				0,
				0,
				true,
				chunkRole,
				signature);
		}

		private static final class IntArrayBuilder {
			private int[] values;
			private int size;

			private IntArrayBuilder(int initialCapacity) {
				values = new int[Math.max(0, initialCapacity)];
			}

			private void add(int value) {
				ensureCapacity(size + 1);
				values[size++] = value;
			}

			private int size() {
				return size;
			}

			private int[] toArray() {
				return size == 0 ? new int[0] : Arrays.copyOf(values, size);
			}

			private void ensureCapacity(int requiredCapacity) {
				if (requiredCapacity <= values.length) {
					return;
				}
				int grownCapacity = values.length <= 0
					? 1
					: values.length + (values.length >> 1) + 1;
				if (grownCapacity < requiredCapacity) {
					grownCapacity = requiredCapacity;
				}
				values = Arrays.copyOf(values, grownCapacity);
			}
		}

		private static final class FloatArrayBuilder {
			private float[] values;
			private int size;

			private FloatArrayBuilder(int initialCapacity) {
				values = new float[Math.max(0, initialCapacity)];
			}

			private void add(float value) {
				ensureCapacity(size + 1);
				values[size++] = value;
			}

			private float[] toArray() {
				return size == 0 ? new float[0] : Arrays.copyOf(values, size);
			}

			private void ensureCapacity(int requiredCapacity) {
				if (requiredCapacity <= values.length) {
					return;
				}
				int grownCapacity = values.length <= 0
					? 1
					: values.length + (values.length >> 1) + 1;
				if (grownCapacity < requiredCapacity) {
					grownCapacity = requiredCapacity;
				}
				values = Arrays.copyOf(values, grownCapacity);
			}
		}

		private long signature(
			int[] vertexArray,
			float[] textureUArray,
			float[] textureVArray,
			int[] lightArray,
			int[] indexArray,
			int[] textureArray,
			int[] fallbackArray,
			Renderer3DModelKind[] kindArray,
			Renderer3DMaterialFamily[] familyArray,
			Renderer3DWorldChunkFrame.ShadowCaster[] shadowCasterArray,
			Renderer3DWorldChunkFrame.GlowEmitter[] glowEmitterArray) {
			long hash = FNV_OFFSET_BASIS;
			hash = mix(hash, plane);
			hash = mix(hash, centerSectionX);
			hash = mix(hash, centerSectionY);
			hash = mix(hash, originWorldX);
			hash = mix(hash, originWorldZ);
			hash = mix(hash, chunkRole);
			for (int value : vertexArray) {
				hash = mix(hash, value);
			}
			for (float value : textureUArray) {
				hash = mix(hash, Float.floatToIntBits(value));
			}
			for (float value : textureVArray) {
				hash = mix(hash, Float.floatToIntBits(value));
			}
			for (int value : lightArray) {
				hash = mix(hash, value);
			}
			for (int value : indexArray) {
				hash = mix(hash, value);
			}
			for (int value : textureArray) {
				hash = mix(hash, value);
			}
			for (int value : fallbackArray) {
				hash = mix(hash, value);
			}
			for (Renderer3DModelKind kind : kindArray) {
				hash = mix(hash, kind.ordinal());
			}
			for (Renderer3DMaterialFamily family : familyArray) {
				hash = mix(hash, family.getShaderId());
			}
			for (Renderer3DWorldChunkFrame.ShadowCaster caster : shadowCasterArray) {
				hash = mix(hash, caster.getModelKind().ordinal());
				hash = mix(hash, caster.getBaseX0());
				hash = mix(hash, caster.getBaseY());
				hash = mix(hash, caster.getBaseZ0());
				hash = mix(hash, caster.getBaseX1());
				hash = mix(hash, caster.getBaseZ1());
				hash = mix(hash, caster.getHeight());
				hash = mix(hash, caster.getWidth());
				hash = mix(hash, caster.getOpacity());
				hash = mix(hash, caster.isOutdoorOnly() ? 1 : 0);
			}
			for (Renderer3DWorldChunkFrame.GlowEmitter emitter : glowEmitterArray) {
				hash = mix(hash, emitter.getModelKind().ordinal());
				hash = mix(hash, emitter.getCenterX());
				hash = mix(hash, emitter.getCenterY());
				hash = mix(hash, emitter.getCenterZ());
				hash = mix(hash, emitter.getRadius());
				hash = mix(hash, emitter.getColor());
				hash = mix(hash, emitter.getIntensity());
			}
			return hash;
		}

		private static long mix(long hash, int value) {
			hash ^= value & 0xffffffffL;
			return hash * FNV_PRIME;
		}
	}

	final void rotate1024(int yOffset, int vParamSrc, int xOffset, byte var4, int zOffset, int rotY, int rotZ, int rotX,
						  int zTop) {
		try {

			this.resetTransformCache((int) 7972);
			if (this.minZ <= MiscFunctions.frustumMinX && this.maxZ >= MiscFunctions.frustumMaxX
				&& this.minX <= MiscFunctions.frustumMinY && this.maxX >= MiscFunctions.frustumMaxY
				&& this.minY <= MiscFunctions.frustumNearZ && this.maxY >= MiscFunctions.frustumFarZ) {
				this.m_dc = true;
				int xy_xy = 0;
				if (var4 > -105) {
					this.clearRotDataAndParams26((byte) -103);
				}

				int xy_yy = 0;
				int yz_zy = 0;
				int yz_zz = 0;
				int xz_xz = 0;
				if (rotZ != 0) {
					xy_yy = FastMath.trigTable1024[1024 + rotZ];
					xy_xy = FastMath.trigTable1024[rotZ];
				}

				int xz_xx = 0;
				if (rotX != 0) {
					yz_zy = FastMath.trigTable1024[rotX];
					yz_zz = FastMath.trigTable1024[rotX + 1024];
				}

				if (rotY != 0) {
					xz_xz = FastMath.trigTable1024[rotY];
					xz_xx = FastMath.trigTable1024[rotY + 1024];
				}

				for (int var17 = 0; this.vertHead > var17; ++var17) {
					int x0 = this.vertXTransform[var17] - xOffset;
					int yO = this.vertYTransform[var17] - yOffset;
					int zO = this.vertZTransform[var17] - zOffset;

					int tmp;
					if (rotZ != 0) {
						tmp = yO * xy_xy + xy_yy * x0 >> 15;
						yO = yO * xy_yy - x0 * xy_xy >> 15;
						x0 = tmp;
					}

					if (rotY != 0) {
						tmp = xz_xx * x0 + zO * xz_xz >> 15;
						zO = xz_xx * zO - x0 * xz_xz >> 15;
						x0 = tmp;
					}

					if (rotX != 0) {
						tmp = yO * yz_zz - yz_zy * zO >> 15;
						zO = yz_zy * yO + yz_zz * zO >> 15;
						yO = tmp;
					}

					if (zO < zTop) {
						this.vertexParam6[var17] = x0 << vParamSrc;
					} else {
						this.vertexParam6[var17] = (x0 << vParamSrc) / zO;
					}

					if (zO < zTop) {
						this.vertexParam2[var17] = yO << vParamSrc;
					} else {
						this.vertexParam2[var17] = (yO << vParamSrc) / zO;
					}

					this.vertXRot[var17] = x0;
					this.vertYRot[var17] = yO;
					this.vertZRot[var17] = zO;
				}

			} else {
				this.m_dc = false;
			}
		} catch (RuntimeException var21) {
			throw GenUtil.makeThrowable(var21, "ca.U(" + yOffset + ',' + vParamSrc + ',' + xOffset + ',' + var4 + ','
				+ zOffset + ',' + rotY + ',' + rotZ + ',' + rotX + ',' + zTop + ')');
		}
	}

	private void rotate256(int var1, int rotX, int rotZ, int rotY) {
		try {

			if (var1 >= -14) {
				this.faceNormY = (int[]) null;
			}

			for (int v = 0; v < this.vertHead; ++v) {
				int tmp;
				if (rotZ != 0) {
					int xx = FastMath.trigTable256[rotZ + 256];
					int xy = FastMath.trigTable256[rotZ];
					tmp = this.vertXTransform[v] * xx + this.vertYTransform[v] * xy >> 15;
					this.vertYTransform[v] = this.vertYTransform[v] * xx - xy * this.vertXTransform[v] >> 15;
					this.vertXTransform[v] = tmp;
				}

				if (rotX != 0) {
					int yz = FastMath.trigTable256[rotX];
					int yy = FastMath.trigTable256[256 + rotX];
					tmp = yy * this.vertYTransform[v] - yz * this.vertZTransform[v] >> 15;
					this.vertZTransform[v] = yz * this.vertYTransform[v] + yy * this.vertZTransform[v] >> 15;
					this.vertYTransform[v] = tmp;
				}

				if (rotY != 0) {
					int xz = FastMath.trigTable256[rotY];
					int xx = FastMath.trigTable256[256 + rotY];
					tmp = xz * this.vertZTransform[v] + this.vertXTransform[v] * xx >> 15;
					this.vertZTransform[v] = this.vertZTransform[v] * xx - this.vertXTransform[v] * xz >> 15;
					this.vertXTransform[v] = tmp;
				}
			}

		} catch (RuntimeException var9) {
			throw GenUtil.makeThrowable(var9, "ca.W(" + var1 + ',' + rotX + ',' + rotZ + ',' + rotY + ')');
		}
	}

	/**
	 * 0-255 -> 0-1
	 */
	private void scale(int xScale, int var2, int zScale, int yScale) {
		try {

			if (var2 != -27483) {
				this.insertVertex(-7, -31, -82);
			}

			for (int i = 0; this.vertHead > i; ++i) {
				this.vertXTransform[i] = this.vertXTransform[i] * xScale >> 8;
				this.vertYTransform[i] = this.vertYTransform[i] * yScale >> 8;
				this.vertZTransform[i] = zScale * this.vertZTransform[i] >> 8;
			}

		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "ca.BA(" + xScale + ',' + var2 + ',' + zScale + ',' + yScale + ')');
		}
	}

	final void setDiffuseDir(boolean var1, int dirX, int dirY, int dirZ) {
		try {
			if (var1) {
				this.m_Yb = 71;
			}


			if (!this.dontComputeDiffuse) {
				this.diffuseDirZ = dirZ;
				this.diffuseDirY = dirY;
				this.diffuseDirX = dirX;
				this.diffuseMag = (int) Math.sqrt((double) (dirZ * dirZ + dirY * dirY + dirX * dirX));
				this.computeDiffuse(52);
			}
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "ca.B(" + var1 + ',' + dirX + ',' + dirY + ',' + dirZ + ')');
		}
	}

	final void setDiffuseLight(int var1, int var2, int diffuseDirY, int var4, int diffuseDirX, int diffuseDirZ) {
		try {

			this.diffuseParam1 = 256 - var2 * 4;
			this.diffuseParam2 = (64 - var1) * 16 + 128;
			if (var4 > -110) {
				this.diffuseDirY = -67;
			}

			if (!this.dontComputeDiffuse) {
				this.diffuseDirX = diffuseDirX;
				this.diffuseDirZ = diffuseDirZ;
				this.diffuseDirY = diffuseDirY;
				this.diffuseMag = (int) Math.sqrt(
					(double) (diffuseDirZ * diffuseDirZ + diffuseDirY * diffuseDirY + diffuseDirX * diffuseDirX));
				this.computeDiffuse(-102);
			}
		} catch (RuntimeException var8) {
			throw GenUtil.makeThrowable(var8, "ca.G(" + var1 + ',' + var2 + ',' + diffuseDirY + ',' + var4 + ','
				+ diffuseDirX + ',' + diffuseDirZ + ')');
		}
	}

	public final void setDiffuseLightAndColor(int dirX, int dirY, int dirZ, int p1, int p2, boolean var5, int var7) {
		try {
			this.diffuseParam2 = (64 - p2) * 16 + 128;
			this.diffuseParam1 = 256 - p1 * 4;

			if (!this.dontComputeDiffuse) {
				for (int i = 0; i < this.faceHead; ++i) {
					if (var5) {
						this.faceDiffuseLight[i] = this.m_Vb;
					} else {
						this.faceDiffuseLight[i] = 0;
					}
				}

				this.diffuseDirX = dirX;
				this.diffuseDirZ = dirZ;
				this.diffuseDirY = dirY;
				this.diffuseMag = (int) Math.sqrt((double) (dirY * dirY + dirX * dirX + dirZ * dirZ));
				this.computeDiffuse(-121);
			}
		} catch (RuntimeException var10) {
			throw GenUtil.makeThrowable(var10,
				"ca.N(" + dirZ + ',' + p1 + ',' + dirY + ',' + dirX + ',' + var5 + ',' + p2 + ',' + var7 + ')');
		}
	}

	private void setFaceVertexCount(int faceCount, int vertexCount, int var3) {
		try {
			if (!this.m_db) {
				this.m_zb = new byte[faceCount];
				this.facePickIndex = new int[faceCount];
			}

			this.scenePolyNormalMagnitude = new int[faceCount];
			this.vertY = new int[vertexCount];
			this.vertLightOther = new byte[vertexCount];
			this.vertX = new int[vertexCount];
			this.scenePolyNormalShift = new int[faceCount];
			this.vertZ = new int[vertexCount];
			this.faceTextureBack = new int[faceCount];
			this.vertDiffuseLight = new int[vertexCount];
			this.faceTextureFront = new int[faceCount];
			this.faceIndices = new int[faceCount][];
			this.faceIndexCount = new int[faceCount];
			if (!this.m_b) {
				this.vertexParam2 = new int[vertexCount];
				this.vertYRot = new int[vertexCount];
				this.vertXRot = new int[vertexCount];
				this.vertZRot = new int[vertexCount];
				this.vertexParam6 = new int[vertexCount];
			}


			this.faceDiffuseLight = new int[faceCount];
			this.rotM_zToX = 256;
			this.rot256Y = 0;
			this.rotM_xToZ = 256;
			this.rotM_yToX = 256;
			this.scaleZ = 256;
			if (!this.m_c) {
				this.faceMaxX = new int[faceCount];
				this.faceMinY = new int[faceCount];
				this.faceMinZ = new int[faceCount];
				this.faceMaxY = new int[faceCount];
				this.faceMaxZ = new int[faceCount];
				this.faceMinX = new int[faceCount];
			}

			if (!this.dontComputeDiffuse || !this.m_c) {
				this.faceNormY = new int[faceCount];
				this.faceNormZ = new int[faceCount];
				this.faceNormX = new int[faceCount];
			}

			this.rotM_yToZ = 256;
			this.faceHead = 0;
			this.rot256X = 0;
			this.translateZ = 0;
			this.scaleX = 256;
			if (!this.m_v) {
				this.vertXTransform = new int[vertexCount];
				this.vertZTransform = new int[vertexCount];
				this.vertYTransform = new int[vertexCount];
			} else {
				this.vertXTransform = this.vertX;
				this.vertZTransform = this.vertZ;
				this.vertYTransform = this.vertY;
			}

			this.translateX = 0;
			this.vertexCount2 = vertexCount;
			this.rotM_xToY = 256;
			this.rotM_zToY = 256;
			this.translateY = 0;
			this.appliedTransform = 0;
			this.vertHead = 0;
			this.scaleY = 256;
			if (var3 <= 68) {
				this.faceIndexCount = (int[]) null;
			}

			this.faceCount = faceCount;
			this.rot256Z = 0;
		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5, "ca.T(" + faceCount + ',' + vertexCount + ',' + var3 + ')');
		}
	}

	final void setRot256(int rotX, int rotY, int rotZ) {
		try {

			this.rot256X = rotX & 255;
			this.rot256Y = rotY & 255;
			this.rot256Z = rotZ & 255;
			this.computeAppliedTransform();
			this.m_Yb = 1;
			this.renderer3DTransformVersion++;
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "ca.EA(" + rotZ + ',' + "dummy" + ',' + rotX + ',' + rotY + ')');
		}
	}

	public final void setTranslate(int tX, int tY, int tZ) {
		try {
			this.translateY = tY;
			this.translateX = tX;
			this.translateZ = tZ;

			this.computeAppliedTransform();
			this.m_Yb = 1;
			this.renderer3DTransformVersion++;
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "ca.L(" + tY + ',' + "dummy" + ',' + tZ + ',' + tX + ')');
		}
	}

	final void setVertexLightOther(int id, int val) {
		try {

			this.vertLightOther[id] = (byte) val;
		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5, "ca.HA(" + id + ',' + val + ',' + -61 + ')');
		}
	}

	private void translate(int vOff, int yt, int zt, int xt) {
		try {


			for (int i = vOff; this.vertHead > i; ++i) {
				this.vertXTransform[i] += xt;
				this.vertYTransform[i] += yt;
				this.vertZTransform[i] += zt;
			}

		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "ca.DA(" + vOff + ',' + yt + ',' + zt + ',' + xt + ')');
		}
	}

	public final void translate2(int tX, int tY, int tZ) {
		try {
			this.translateY += tY;
			this.translateZ += tZ;
			this.translateX += tX;

			this.computeAppliedTransform();
			this.m_Yb = 1;
			this.renderer3DTransformVersion++;
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "ca.O(" + tX + ',' + tZ + ',' + tY + ',' + true + ')');
		}
	}
}
