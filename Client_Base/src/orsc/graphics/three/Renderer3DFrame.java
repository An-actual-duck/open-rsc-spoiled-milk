package orsc.graphics.three;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Renderer3DFrame {
	private final int sourceModelCount;
	private final int fogDistance;
	private final int fogStartDistance;
	private final int viewportWidth;
	private final int viewportHeight;
	private final int centerX;
	private final int centerY;
	private final int cameraOffsetX;
	private final int cameraOffsetY;
	private final int cameraOffsetZ;
	private final int cameraRotationX;
	private final int cameraRotationY;
	private final int cameraRotationZ;
	private final int perspectiveShift;
	private final int nearPlane;
	private final Renderer3DTextureData[] textures;
	private final List<FaceCommand> worldFaces = new ArrayList<FaceCommand>();
	private final List<SpriteSubmission> spriteSubmissions = new ArrayList<SpriteSubmission>();
	private final List<CharacterSprite> characterSprites = new ArrayList<CharacterSprite>();
	private final List<SpriteAnchor> spriteAnchors = new ArrayList<SpriteAnchor>();
	private final Map<Long, FaceCommand> worldFacesByModelFace = new HashMap<Long, FaceCommand>();
	private final int[] worldFaceCountsByKind = new int[Renderer3DModelKind.values().length];
	private Renderer3DDepthFrame depthFrame;
	private Renderer3DMeshFrame meshFrame;
	private Renderer3DWorldChunkFrame worldChunkFrame = Renderer3DWorldChunkFrame.EMPTY;
	private Renderer3DRoofVisibility roofVisibility = Renderer3DRoofVisibility.VISIBLE;
	private int activePlane;

	Renderer3DFrame(
		int sourceModelCount,
		int fogDistance,
		int fogStartDistance,
		int viewportWidth,
		int viewportHeight,
		int centerX,
		int centerY,
		int cameraOffsetX,
		int cameraOffsetY,
		int cameraOffsetZ,
		int cameraRotationX,
		int cameraRotationY,
		int cameraRotationZ,
		int perspectiveShift,
		int nearPlane,
		Renderer3DTextureData[] textures) {
		this.sourceModelCount = sourceModelCount;
		this.fogDistance = fogDistance;
		this.fogStartDistance = fogStartDistance;
		this.viewportWidth = viewportWidth;
		this.viewportHeight = viewportHeight;
		this.centerX = centerX;
		this.centerY = centerY;
		this.cameraOffsetX = cameraOffsetX;
		this.cameraOffsetY = cameraOffsetY;
		this.cameraOffsetZ = cameraOffsetZ;
		this.cameraRotationX = cameraRotationX;
		this.cameraRotationY = cameraRotationY;
		this.cameraRotationZ = cameraRotationZ;
		this.perspectiveShift = perspectiveShift;
		this.nearPlane = nearPlane;
		this.textures = textures == null ? new Renderer3DTextureData[0] : textures;
	}

	void addWorldFace(
		int modelIndex,
		int faceId,
		int texture,
		int color,
		int orientation,
		int averageDepth,
		RSModel model,
		int[] faceIndices,
		int vertexCount,
		int[] light,
		int[] baseLight) {
		int[] cameraX = new int[vertexCount];
		int[] cameraY = new int[vertexCount];
		int[] cameraZ = new int[vertexCount];
		int[] screenX = new int[vertexCount];
		int[] screenY = new int[vertexCount];
		Renderer3DModelKind modelKind = model.getRenderer3DModelKind();

		for (int vertex = 0; vertex < vertexCount; vertex++) {
			int modelVertex = faceIndices[vertex];
			cameraX[vertex] = model.vertXRot[modelVertex];
			cameraY[vertex] = model.vertYRot[modelVertex];
			cameraZ[vertex] = model.vertZRot[modelVertex];
			screenX[vertex] = model.vertexParam6[modelVertex];
			screenY[vertex] = model.vertexParam2[modelVertex];
		}

		FaceCommand face = new FaceCommand(
			modelKind,
			modelIndex,
			faceId,
			texture,
			color,
			orientation,
			averageDepth,
			cameraX,
			cameraY,
			cameraZ,
			screenX,
			screenY,
			light,
			baseLight);
		this.worldFaces.add(face);
		this.worldFacesByModelFace.put(worldFaceKey(modelIndex, faceId), face);
		this.worldFaceCountsByKind[modelKind.ordinal()]++;
	}

	void recordLegacyDrawOrder(int modelIndex, int faceId, int drawOrder) {
		FaceCommand face = this.worldFacesByModelFace.get(worldFaceKey(modelIndex, faceId));
		if (face != null) {
			face.setLegacyDrawOrder(drawOrder);
		}
	}

	void recordLegacyClippedGeometry(
		int modelIndex,
		int faceId,
		int[] cameraX,
		int[] cameraY,
		int[] cameraZ,
		int[] screenX,
		int[] screenY,
		int[] light,
		int[] baseLight,
		int vertexCount) {
		FaceCommand face = this.worldFacesByModelFace.get(worldFaceKey(modelIndex, faceId));
		if (face != null) {
			face.setLegacyClippedGeometry(cameraX, cameraY, cameraZ, screenX, screenY, light, baseLight, vertexCount);
		}
	}

	void addSpriteAnchor(
		int faceId,
		int spriteId,
		int pickIndex,
		int legacyDrawOrder,
		int averageDepth,
		int cameraX,
		int cameraY,
		int cameraZ,
		int screenX,
		int screenY,
		int drawX,
		int drawY,
		int drawWidth,
		int drawHeight,
		int scale,
		int horizontalSkew,
		boolean pickable) {
		this.spriteAnchors.add(new SpriteAnchor(
			faceId,
			spriteId,
			pickIndex,
			legacyDrawOrder,
			averageDepth,
			cameraX,
			cameraY,
			cameraZ,
			screenX,
			screenY,
			drawX,
			drawY,
			drawWidth,
			drawHeight,
			scale,
			horizontalSkew,
			pickable));
	}

	void addSpriteSubmission(
		int faceId,
		int spriteId,
		int pickIndex,
		int worldX,
		int worldY,
		int worldZ,
		int sourceWidth,
		int sourceHeight,
		int cameraX,
		int cameraY,
		int cameraZ,
		int screenX,
		int screenY,
		int drawX,
		int drawY,
		int drawWidth,
		int drawHeight,
		int scale,
		int horizontalSkew,
		boolean projected,
		String cullReason) {
		this.spriteSubmissions.add(new SpriteSubmission(
			faceId,
			spriteId,
			pickIndex,
			worldX,
			worldY,
			worldZ,
			sourceWidth,
			sourceHeight,
			cameraX,
			cameraY,
			cameraZ,
			screenX,
			screenY,
			drawX,
			drawY,
			drawWidth,
			drawHeight,
			scale,
			horizontalSkew,
			projected,
			cullReason));
	}

	void addCharacterSprite(
		String kind,
		int faceId,
		int spriteId,
		int arrayIndex,
		int serverIndex,
		int entityId,
		String displayName,
		int worldX,
		int worldY,
		int worldZ,
		int visualOffsetX,
		int visualOffsetZ,
		int sourceWidth,
		int sourceHeight,
		String direction,
		boolean combatDirection,
		int combatTimeout,
		int healthCurrent,
		int healthMax,
		int damageTaken,
		int attackingNpcServerIndex,
		int attackingPlayerServerIndex,
		int combatEffectType,
		int combatEffectTime,
		boolean activeHitSplats,
		boolean projected,
		String cullReason,
		int drawX,
		int drawY,
		int drawWidth,
		int drawHeight) {
		this.characterSprites.add(new CharacterSprite(
			kind,
			faceId,
			spriteId,
			arrayIndex,
			serverIndex,
			entityId,
			displayName,
			worldX,
			worldY,
			worldZ,
			visualOffsetX,
			visualOffsetZ,
			sourceWidth,
			sourceHeight,
			direction,
			combatDirection,
			combatTimeout,
			healthCurrent,
			healthMax,
			damageTaken,
			attackingNpcServerIndex,
			attackingPlayerServerIndex,
			combatEffectType,
			combatEffectTime,
			activeHitSplats,
			projected,
			cullReason,
			drawX,
			drawY,
			drawWidth,
			drawHeight));
	}

	private static long worldFaceKey(int modelIndex, int faceId) {
		return ((long) modelIndex << 32) ^ (faceId & 0xffffffffL);
	}

	public int getSourceModelCount() {
		return sourceModelCount;
	}

	public int getFogDistance() {
		return fogDistance;
	}

	public int getFogStartDistance() {
		return fogStartDistance;
	}

	public int getViewportWidth() {
		return viewportWidth;
	}

	public int getViewportHeight() {
		return viewportHeight;
	}

	public int getCenterX() {
		return centerX;
	}

	public int getCenterY() {
		return centerY;
	}

	public int getCameraOffsetX() {
		return cameraOffsetX;
	}

	public int getCameraOffsetY() {
		return cameraOffsetY;
	}

	public int getCameraOffsetZ() {
		return cameraOffsetZ;
	}

	public int getCameraRotationX() {
		return cameraRotationX;
	}

	public int getCameraRotationY() {
		return cameraRotationY;
	}

	public int getCameraRotationZ() {
		return cameraRotationZ;
	}

	public int getPerspectiveShift() {
		return perspectiveShift;
	}

	public int getNearPlane() {
		return nearPlane;
	}

	public Renderer3DTextureData[] getTextures() {
		return textures;
	}

	public Renderer3DTextureData getTexture(int textureId) {
		if (textureId < 0 || textureId >= textures.length) {
			return null;
		}
		return textures[textureId];
	}

	public int getWorldFaceCount() {
		return worldFaces.size();
	}

	public List<FaceCommand> getWorldFaces() {
		return Collections.unmodifiableList(worldFaces);
	}

	public int getSpriteAnchorCount() {
		return spriteAnchors.size();
	}

	public List<SpriteAnchor> getSpriteAnchors() {
		return Collections.unmodifiableList(spriteAnchors);
	}

	public int getSpriteSubmissionCount() {
		return spriteSubmissions.size();
	}

	public List<SpriteSubmission> getSpriteSubmissions() {
		return Collections.unmodifiableList(spriteSubmissions);
	}

	public int getCharacterSpriteCount() {
		return characterSprites.size();
	}

	public List<CharacterSprite> getCharacterSprites() {
		return Collections.unmodifiableList(characterSprites);
	}

	public int getWorldFaceCount(Renderer3DModelKind kind) {
		if (kind == null) {
			return 0;
		}
		return worldFaceCountsByKind[kind.ordinal()];
	}

	void setDepthFrame(Renderer3DDepthFrame depthFrame) {
		this.depthFrame = depthFrame;
	}

	public Renderer3DDepthFrame getDepthFrame() {
		return depthFrame;
	}

	public void releaseDepthFrame() {
		Renderer3DDepthFrame releasedDepthFrame;
		synchronized (this) {
			releasedDepthFrame = depthFrame;
			depthFrame = null;
		}
		if (releasedDepthFrame != null) {
			releasedDepthFrame.release();
		}
	}

	void setMeshFrame(Renderer3DMeshFrame meshFrame) {
		this.meshFrame = meshFrame;
	}

	public Renderer3DMeshFrame getMeshFrame() {
		return meshFrame;
	}

	public void setWorldChunkFrame(Renderer3DWorldChunkFrame worldChunkFrame) {
		this.worldChunkFrame = worldChunkFrame == null ? Renderer3DWorldChunkFrame.EMPTY : worldChunkFrame;
	}

	public Renderer3DWorldChunkFrame getWorldChunkFrame() {
		return worldChunkFrame;
	}

	public void setRoofVisibility(Renderer3DRoofVisibility roofVisibility, int activePlane) {
		this.roofVisibility = roofVisibility == null
			? Renderer3DRoofVisibility.VISIBLE
			: roofVisibility;
		this.activePlane = Math.max(0, activePlane);
	}

	public Renderer3DRoofVisibility getRoofVisibility() {
		return roofVisibility;
	}

	public int getActivePlane() {
		return activePlane;
	}

	public boolean isWorldChunkModelKindVisible(Renderer3DModelKind modelKind, int chunkPlane) {
		return roofVisibility.isWorldChunkModelKindVisible(modelKind, activePlane, chunkPlane);
	}

	public static final class CharacterSprite {
		private final String kind;
		private final int faceId;
		private final int spriteId;
		private final int arrayIndex;
		private final int serverIndex;
		private final int entityId;
		private final String displayName;
		private final int worldX;
		private final int worldY;
		private final int worldZ;
		private final int visualOffsetX;
		private final int visualOffsetZ;
		private final int sourceWidth;
		private final int sourceHeight;
		private final String direction;
		private final boolean combatDirection;
		private final int combatTimeout;
		private final int healthCurrent;
		private final int healthMax;
		private final int damageTaken;
		private final int attackingNpcServerIndex;
		private final int attackingPlayerServerIndex;
		private final int combatEffectType;
		private final int combatEffectTime;
		private final boolean activeHitSplats;
		private final boolean projected;
		private final String cullReason;
		private final int drawX;
		private final int drawY;
		private final int drawWidth;
		private final int drawHeight;

		private CharacterSprite(
			String kind,
			int faceId,
			int spriteId,
			int arrayIndex,
			int serverIndex,
			int entityId,
			String displayName,
			int worldX,
			int worldY,
			int worldZ,
			int visualOffsetX,
			int visualOffsetZ,
			int sourceWidth,
			int sourceHeight,
			String direction,
			boolean combatDirection,
			int combatTimeout,
			int healthCurrent,
			int healthMax,
			int damageTaken,
			int attackingNpcServerIndex,
			int attackingPlayerServerIndex,
			int combatEffectType,
			int combatEffectTime,
			boolean activeHitSplats,
			boolean projected,
			String cullReason,
			int drawX,
			int drawY,
			int drawWidth,
			int drawHeight) {
			this.kind = kind == null ? "" : kind;
			this.faceId = faceId;
			this.spriteId = spriteId;
			this.arrayIndex = arrayIndex;
			this.serverIndex = serverIndex;
			this.entityId = entityId;
			this.displayName = displayName == null ? "" : displayName;
			this.worldX = worldX;
			this.worldY = worldY;
			this.worldZ = worldZ;
			this.visualOffsetX = visualOffsetX;
			this.visualOffsetZ = visualOffsetZ;
			this.sourceWidth = sourceWidth;
			this.sourceHeight = sourceHeight;
			this.direction = direction == null ? "" : direction;
			this.combatDirection = combatDirection;
			this.combatTimeout = combatTimeout;
			this.healthCurrent = healthCurrent;
			this.healthMax = healthMax;
			this.damageTaken = damageTaken;
			this.attackingNpcServerIndex = attackingNpcServerIndex;
			this.attackingPlayerServerIndex = attackingPlayerServerIndex;
			this.combatEffectType = combatEffectType;
			this.combatEffectTime = combatEffectTime;
			this.activeHitSplats = activeHitSplats;
			this.projected = projected;
			this.cullReason = cullReason == null ? "" : cullReason;
			this.drawX = drawX;
			this.drawY = drawY;
			this.drawWidth = drawWidth;
			this.drawHeight = drawHeight;
		}

		public String getKind() {
			return kind;
		}

		public int getFaceId() {
			return faceId;
		}

		public int getSpriteId() {
			return spriteId;
		}

		public int getArrayIndex() {
			return arrayIndex;
		}

		public int getServerIndex() {
			return serverIndex;
		}

		public int getEntityId() {
			return entityId;
		}

		public String getDisplayName() {
			return displayName;
		}

		public int getWorldX() {
			return worldX;
		}

		public int getWorldY() {
			return worldY;
		}

		public int getWorldZ() {
			return worldZ;
		}

		public int getVisualOffsetX() {
			return visualOffsetX;
		}

		public int getVisualOffsetZ() {
			return visualOffsetZ;
		}

		public int getSourceWidth() {
			return sourceWidth;
		}

		public int getSourceHeight() {
			return sourceHeight;
		}

		public String getDirection() {
			return direction;
		}

		public boolean isCombatDirection() {
			return combatDirection;
		}

		public int getCombatTimeout() {
			return combatTimeout;
		}

		public int getHealthCurrent() {
			return healthCurrent;
		}

		public int getHealthMax() {
			return healthMax;
		}

		public int getDamageTaken() {
			return damageTaken;
		}

		public int getAttackingNpcServerIndex() {
			return attackingNpcServerIndex;
		}

		public int getAttackingPlayerServerIndex() {
			return attackingPlayerServerIndex;
		}

		public int getCombatEffectType() {
			return combatEffectType;
		}

		public int getCombatEffectTime() {
			return combatEffectTime;
		}

		public boolean hasActiveHitSplats() {
			return activeHitSplats;
		}

		public boolean isProjected() {
			return projected;
		}

		public String getCullReason() {
			return cullReason;
		}

		public int getDrawX() {
			return drawX;
		}

		public int getDrawY() {
			return drawY;
		}

		public int getDrawWidth() {
			return drawWidth;
		}

		public int getDrawHeight() {
			return drawHeight;
		}
	}

	public static final class SpriteSubmission {
		private final int faceId;
		private final int spriteId;
		private final int pickIndex;
		private final int worldX;
		private final int worldY;
		private final int worldZ;
		private final int sourceWidth;
		private final int sourceHeight;
		private final int cameraX;
		private final int cameraY;
		private final int cameraZ;
		private final int screenX;
		private final int screenY;
		private final int drawX;
		private final int drawY;
		private final int drawWidth;
		private final int drawHeight;
		private final int scale;
		private final int horizontalSkew;
		private final boolean projected;
		private final String cullReason;

		private SpriteSubmission(
			int faceId,
			int spriteId,
			int pickIndex,
			int worldX,
			int worldY,
			int worldZ,
			int sourceWidth,
			int sourceHeight,
			int cameraX,
			int cameraY,
			int cameraZ,
			int screenX,
			int screenY,
			int drawX,
			int drawY,
			int drawWidth,
			int drawHeight,
			int scale,
			int horizontalSkew,
			boolean projected,
			String cullReason) {
			this.faceId = faceId;
			this.spriteId = spriteId;
			this.pickIndex = pickIndex;
			this.worldX = worldX;
			this.worldY = worldY;
			this.worldZ = worldZ;
			this.sourceWidth = sourceWidth;
			this.sourceHeight = sourceHeight;
			this.cameraX = cameraX;
			this.cameraY = cameraY;
			this.cameraZ = cameraZ;
			this.screenX = screenX;
			this.screenY = screenY;
			this.drawX = drawX;
			this.drawY = drawY;
			this.drawWidth = drawWidth;
			this.drawHeight = drawHeight;
			this.scale = scale;
			this.horizontalSkew = horizontalSkew;
			this.projected = projected;
			this.cullReason = cullReason == null ? "" : cullReason;
		}

		public int getFaceId() {
			return faceId;
		}

		public int getSpriteId() {
			return spriteId;
		}

		public int getPickIndex() {
			return pickIndex;
		}

		public int getWorldX() {
			return worldX;
		}

		public int getWorldY() {
			return worldY;
		}

		public int getWorldZ() {
			return worldZ;
		}

		public int getSourceWidth() {
			return sourceWidth;
		}

		public int getSourceHeight() {
			return sourceHeight;
		}

		public int getCameraX() {
			return cameraX;
		}

		public int getCameraY() {
			return cameraY;
		}

		public int getCameraZ() {
			return cameraZ;
		}

		public int getScreenX() {
			return screenX;
		}

		public int getScreenY() {
			return screenY;
		}

		public int getDrawX() {
			return drawX;
		}

		public int getDrawY() {
			return drawY;
		}

		public int getDrawWidth() {
			return drawWidth;
		}

		public int getDrawHeight() {
			return drawHeight;
		}

		public int getScale() {
			return scale;
		}

		public int getHorizontalSkew() {
			return horizontalSkew;
		}

		public boolean isProjected() {
			return projected;
		}

		public String getCullReason() {
			return cullReason;
		}
	}

	public static final class SpriteAnchor {
		private final int faceId;
		private final int spriteId;
		private final int pickIndex;
		private final int legacyDrawOrder;
		private final int averageDepth;
		private final int cameraX;
		private final int cameraY;
		private final int cameraZ;
		private final int screenX;
		private final int screenY;
		private final int drawX;
		private final int drawY;
		private final int drawWidth;
		private final int drawHeight;
		private final int scale;
		private final int horizontalSkew;
		private final boolean pickable;

		private SpriteAnchor(
			int faceId,
			int spriteId,
			int pickIndex,
			int legacyDrawOrder,
			int averageDepth,
			int cameraX,
			int cameraY,
			int cameraZ,
			int screenX,
			int screenY,
			int drawX,
			int drawY,
			int drawWidth,
			int drawHeight,
			int scale,
			int horizontalSkew,
			boolean pickable) {
			this.faceId = faceId;
			this.spriteId = spriteId;
			this.pickIndex = pickIndex;
			this.legacyDrawOrder = legacyDrawOrder;
			this.averageDepth = averageDepth;
			this.cameraX = cameraX;
			this.cameraY = cameraY;
			this.cameraZ = cameraZ;
			this.screenX = screenX;
			this.screenY = screenY;
			this.drawX = drawX;
			this.drawY = drawY;
			this.drawWidth = drawWidth;
			this.drawHeight = drawHeight;
			this.scale = scale;
			this.horizontalSkew = horizontalSkew;
			this.pickable = pickable;
		}

		public int getFaceId() {
			return faceId;
		}

		public int getSpriteId() {
			return spriteId;
		}

		public int getPickIndex() {
			return pickIndex;
		}

		public int getLegacyDrawOrder() {
			return legacyDrawOrder;
		}

		public int getAverageDepth() {
			return averageDepth;
		}

		public int getCameraX() {
			return cameraX;
		}

		public int getCameraY() {
			return cameraY;
		}

		public int getCameraZ() {
			return cameraZ;
		}

		public int getScreenX() {
			return screenX;
		}

		public int getScreenY() {
			return screenY;
		}

		public int getDrawX() {
			return drawX;
		}

		public int getDrawY() {
			return drawY;
		}

		public int getDrawWidth() {
			return drawWidth;
		}

		public int getDrawHeight() {
			return drawHeight;
		}

		public int getScale() {
			return scale;
		}

		public int getHorizontalSkew() {
			return horizontalSkew;
		}

		public boolean isPickable() {
			return pickable;
		}
	}

	public static final class FaceCommand {
		private final Renderer3DModelKind modelKind;
		private final int modelIndex;
		private final int faceId;
		private final int texture;
		private final int color;
		private final int orientation;
		private final int averageDepth;
		private final int[] cameraX;
		private final int[] cameraY;
		private final int[] cameraZ;
		private final int[] screenX;
		private final int[] screenY;
		private final int[] light;
		private final int[] baseLight;
		private final float[] textureU;
		private final float[] textureV;
		private int[] clippedCameraX;
		private int[] clippedCameraY;
		private int[] clippedCameraZ;
		private int[] clippedScreenX;
		private int[] clippedScreenY;
		private int[] clippedLight;
		private int[] clippedBaseLight;
		private float[] clippedTextureU;
		private float[] clippedTextureV;
		private int legacyDrawOrder = -1;

		private FaceCommand(
			Renderer3DModelKind modelKind,
			int modelIndex,
			int faceId,
			int texture,
			int color,
			int orientation,
			int averageDepth,
			int[] cameraX,
			int[] cameraY,
			int[] cameraZ,
			int[] screenX,
			int[] screenY,
			int[] light,
			int[] baseLight) {
			this.modelKind = modelKind;
			this.modelIndex = modelIndex;
			this.faceId = faceId;
			this.texture = texture;
			this.color = color;
			this.orientation = orientation;
			this.averageDepth = averageDepth;
			this.cameraX = cameraX;
			this.cameraY = cameraY;
			this.cameraZ = cameraZ;
			this.screenX = screenX;
			this.screenY = screenY;
			this.light = copyLight(light, cameraX.length);
			this.baseLight = copyLight(baseLight, cameraX.length);
			this.textureU = new float[cameraX.length];
			this.textureV = new float[cameraX.length];
			populateTextureCoordinates(cameraX, cameraY, cameraZ, textureU, textureV);
		}

		private static int[] copyLight(int[] source, int vertexCount) {
			if (source == null || source.length < vertexCount) {
				return new int[vertexCount];
			}
			return Arrays.copyOf(source, vertexCount);
		}

		public Renderer3DModelKind getModelKind() {
			return modelKind;
		}

		public int getModelIndex() {
			return modelIndex;
		}

		public int getFaceId() {
			return faceId;
		}

		public int getTexture() {
			return texture;
		}

		public int getColor() {
			return color;
		}

		public int getOrientation() {
			return orientation;
		}

		public int getAverageDepth() {
			return averageDepth;
		}

		public int getLegacyDrawOrder() {
			return legacyDrawOrder;
		}

		private void setLegacyDrawOrder(int legacyDrawOrder) {
			this.legacyDrawOrder = legacyDrawOrder;
		}

		public int getVertexCount() {
			return cameraX.length;
		}

		public int getRenderVertexCount() {
			return clippedCameraX == null ? cameraX.length : clippedCameraX.length;
		}

		public int[] getCameraX() {
			return cameraX;
		}

		public int[] getRenderCameraX() {
			return clippedCameraX == null ? cameraX : clippedCameraX;
		}

		public int[] getCameraY() {
			return cameraY;
		}

		public int[] getRenderCameraY() {
			return clippedCameraY == null ? cameraY : clippedCameraY;
		}

		public int[] getCameraZ() {
			return cameraZ;
		}

		public int[] getRenderCameraZ() {
			return clippedCameraZ == null ? cameraZ : clippedCameraZ;
		}

		public int[] getScreenX() {
			return screenX;
		}

		public int[] getRenderScreenX() {
			return clippedScreenX == null ? screenX : clippedScreenX;
		}

		public int[] getScreenY() {
			return screenY;
		}

		public int[] getRenderScreenY() {
			return clippedScreenY == null ? screenY : clippedScreenY;
		}

		public int[] getRenderLight() {
			return clippedLight == null ? light : clippedLight;
		}

		public int[] getRenderBaseLight() {
			return clippedBaseLight == null ? baseLight : clippedBaseLight;
		}

		public float[] getTextureU() {
			return textureU;
		}

		public float[] getRenderTextureU() {
			return clippedTextureU == null ? textureU : clippedTextureU;
		}

		public float[] getTextureV() {
			return textureV;
		}

		public float[] getRenderTextureV() {
			return clippedTextureV == null ? textureV : clippedTextureV;
		}

		private void setLegacyClippedGeometry(
			int[] cameraX,
			int[] cameraY,
			int[] cameraZ,
			int[] screenX,
			int[] screenY,
			int[] light,
			int[] baseLight,
			int vertexCount) {
			if (vertexCount < 3) {
				return;
			}

			this.clippedCameraX = Arrays.copyOf(cameraX, vertexCount);
			this.clippedCameraY = Arrays.copyOf(cameraY, vertexCount);
			this.clippedCameraZ = Arrays.copyOf(cameraZ, vertexCount);
			this.clippedScreenX = Arrays.copyOf(screenX, vertexCount);
			this.clippedScreenY = Arrays.copyOf(screenY, vertexCount);
			this.clippedLight = light == null ? new int[vertexCount] : Arrays.copyOf(light, vertexCount);
			this.clippedBaseLight = baseLight == null ? new int[vertexCount] : Arrays.copyOf(baseLight, vertexCount);
			this.clippedTextureU = new float[vertexCount];
			this.clippedTextureV = new float[vertexCount];
			populateTextureCoordinates(
				this.clippedCameraX,
				this.clippedCameraY,
				this.clippedCameraZ,
				this.clippedTextureU,
				this.clippedTextureV);
		}

		private void populateTextureCoordinates(
			int[] sourceCameraX,
			int[] sourceCameraY,
			int[] sourceCameraZ,
			float[] destinationU,
			float[] destinationV) {
			if (texture < 0 || cameraX.length < 3) {
				return;
			}

			int last = cameraX.length - 1;
			double ux = cameraX[1] - cameraX[0];
			double uy = cameraY[1] - cameraY[0];
			double uz = cameraZ[1] - cameraZ[0];
			double vx = cameraX[last] - cameraX[0];
			double vy = cameraY[last] - cameraY[0];
			double vz = cameraZ[last] - cameraZ[0];
			double uu = dot(ux, uy, uz, ux, uy, uz);
			double uv = dot(ux, uy, uz, vx, vy, vz);
			double vv = dot(vx, vy, vz, vx, vy, vz);
			double determinant = uu * vv - uv * uv;
			if (Math.abs(determinant) < 0.000001) {
				return;
			}

			for (int vertex = 0; vertex < sourceCameraX.length; vertex++) {
				double px = sourceCameraX[vertex] - cameraX[0];
				double py = sourceCameraY[vertex] - cameraY[0];
				double pz = sourceCameraZ[vertex] - cameraZ[0];
				double pu = dot(px, py, pz, ux, uy, uz);
				double pv = dot(px, py, pz, vx, vy, vz);
				destinationU[vertex] = (float) ((pu * vv - pv * uv) / determinant);
				destinationV[vertex] = (float) ((pv * uu - pu * uv) / determinant);
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
	}
}
