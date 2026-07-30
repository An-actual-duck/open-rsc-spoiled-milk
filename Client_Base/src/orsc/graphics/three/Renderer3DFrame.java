package orsc.graphics.three;

import orsc.graphics.Renderer2DFrame;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Renderer3DFrame {
	private static final int MAX_RETAINED_WORLD_FACE_STORAGES = 3;
	private static final int MAX_POOLED_FACE_VERTEX_COUNT = 64;
	private static final ArrayDeque<WorldFaceStorage> AVAILABLE_WORLD_FACE_STORAGES =
		new ArrayDeque<WorldFaceStorage>();
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
	private final long textureCatalogSignature;
	private final WorldFaceStorage worldFaceStorage;
	private final List<FaceCommand> worldFaces;
	private final List<FaceCommand> worldFacesView;
	private final List<SpriteSubmission> spriteSubmissions = new ArrayList<SpriteSubmission>();
	private final List<CharacterSprite> characterSprites = new ArrayList<CharacterSprite>();
	private final List<SpriteAnchor> spriteAnchors = new ArrayList<SpriteAnchor>();
	private final List<WorldSpriteSnapshot> worldSpriteSnapshots = new ArrayList<WorldSpriteSnapshot>();
	private final List<WorldSpriteSnapshot> worldSpriteSnapshotsView =
		Collections.unmodifiableList(worldSpriteSnapshots);
	private final Map<Long, FaceCommand> worldFacesByModelFace;
	private final int[] worldFaceCountsByKind;
	private Renderer3DDepthFrame depthFrame;
	private Renderer3DMeshFrame meshFrame;
	private Renderer3DWorldChunkFrame worldChunkFrame = Renderer3DWorldChunkFrame.EMPTY;
	private Renderer3DRoofVisibility roofVisibility = Renderer3DRoofVisibility.VISIBLE;
	private int activePlane;
	private boolean worldFaceStorageReleased;

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
		this.worldFaceStorage = acquireWorldFaceStorage();
		this.worldFaces = worldFaceStorage.worldFaces;
		this.worldFacesView = worldFaceStorage.worldFacesView;
		this.worldFacesByModelFace = worldFaceStorage.worldFacesByModelFace;
		this.worldFaceCountsByKind = worldFaceStorage.worldFaceCountsByKind;
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
		this.textures = textures == null
			? new Renderer3DTextureData[0]
			: textures.clone();
		this.textureCatalogSignature = textureCatalogSignature(this.textures);
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
		Renderer3DModelKind modelKind = model.getRenderer3DModelKind();

		FaceCommand face = worldFaceStorage.acquireFaceCommand(vertexCount);
		face.reset(
			modelKind,
			modelIndex,
			faceId,
			texture,
			color,
			orientation,
			averageDepth,
			model,
			faceIndices,
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

	int addSpriteAnchor(
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
		int anchorIndex = this.spriteAnchors.size();
		SpriteAnchor anchor = new SpriteAnchor(
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
			pickable);
		this.spriteAnchors.add(anchor);
		this.worldSpriteSnapshots.add(new WorldSpriteSnapshot(
			anchorIndex,
			anchor,
			findSpriteSubmission(faceId),
			findCharacterSprite(faceId)));
		return anchorIndex;
	}

	public boolean recordWorldSpriteLayer(
		int anchorIndex,
		int legacyDrawOrder,
		Renderer2DFrame.SpriteCommand command) {
		if (command == null
			|| command.getPhase() != Renderer2DFrame.Phase.SCENE
			|| anchorIndex < 0
			|| anchorIndex >= worldSpriteSnapshots.size()) {
			return false;
		}
		WorldSpriteSnapshot snapshot = worldSpriteSnapshots.get(anchorIndex);
		SpriteAnchor anchor = snapshot == null ? null : snapshot.getAnchor();
		if (anchor == null
			|| anchor.getLegacyDrawOrder() != legacyDrawOrder
			|| command.getSceneSpriteAnchorIndex() != anchorIndex
			|| command.getSceneSpriteDrawOrder() != legacyDrawOrder
			|| (command.getLegacySpriteId() >= 0
				&& command.getLegacySpriteId() != anchor.getSpriteId())) {
			return false;
		}
		snapshot.addLayer(command);
		return true;
	}

	private SpriteSubmission findSpriteSubmission(int faceId) {
		for (int index = spriteSubmissions.size() - 1; index >= 0; index--) {
			SpriteSubmission submission = spriteSubmissions.get(index);
			if (submission != null && submission.getFaceId() == faceId) {
				return submission;
			}
		}
		return null;
	}

	private CharacterSprite findCharacterSprite(int faceId) {
		for (int index = characterSprites.size() - 1; index >= 0; index--) {
			CharacterSprite character = characterSprites.get(index);
			if (character != null && character.getFaceId() == faceId) {
				return character;
			}
		}
		return null;
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

	private static synchronized WorldFaceStorage acquireWorldFaceStorage() {
		WorldFaceStorage selected = null;
		for (WorldFaceStorage candidate : AVAILABLE_WORLD_FACE_STORAGES) {
			if (selected == null || candidate.faceCommandCapacity > selected.faceCommandCapacity) {
				selected = candidate;
			}
		}
		if (selected != null) {
			AVAILABLE_WORLD_FACE_STORAGES.remove(selected);
			return selected;
		}
		return new WorldFaceStorage();
	}

	private static synchronized void releaseWorldFaceStorage(WorldFaceStorage storage) {
		if (storage == null) {
			return;
		}
		storage.recycleActiveFaces();
		if (AVAILABLE_WORLD_FACE_STORAGES.size() >= MAX_RETAINED_WORLD_FACE_STORAGES) {
			WorldFaceStorage smallestRetained = null;
			for (WorldFaceStorage candidate : AVAILABLE_WORLD_FACE_STORAGES) {
				if (smallestRetained == null
					|| candidate.faceCommandCapacity < smallestRetained.faceCommandCapacity) {
					smallestRetained = candidate;
				}
			}
			if (smallestRetained != null
				&& smallestRetained.faceCommandCapacity >= storage.faceCommandCapacity) {
				return;
			}
			AVAILABLE_WORLD_FACE_STORAGES.remove(smallestRetained);
		}
		AVAILABLE_WORLD_FACE_STORAGES.addFirst(storage);
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

	public int getTextureCount() {
		return textures.length;
	}

	public long getTextureCatalogSignature() {
		return textureCatalogSignature;
	}

	public Renderer3DTextureData getTexture(int textureId) {
		if (textureId < 0 || textureId >= textures.length) {
			return null;
		}
		return textures[textureId];
	}

	private static long textureCatalogSignature(Renderer3DTextureData[] textures) {
		long signature = 1469598103934665603L;
		signature = mixSignature(signature, textures.length);
		for (int textureId = 0; textureId < textures.length; textureId++) {
			Renderer3DTextureData texture = textures[textureId];
			signature = mixSignature(signature, textureId);
			signature = mixSignature(
				signature,
				texture == null ? 0L : texture.getSignature());
		}
		return signature;
	}

	private static long mixSignature(long signature, long value) {
		signature ^= value;
		return signature * 1099511628211L;
	}

	public int getWorldFaceCount() {
		return worldFaces.size();
	}

	public List<FaceCommand> getWorldFaces() {
		return worldFacesView;
	}

	public int getSpriteAnchorCount() {
		return spriteAnchors.size();
	}

	public List<SpriteAnchor> getSpriteAnchors() {
		return Collections.unmodifiableList(spriteAnchors);
	}

	public int getWorldSpriteSnapshotCount() {
		return worldSpriteSnapshots.size();
	}

	public List<WorldSpriteSnapshot> getWorldSpriteSnapshots() {
		return worldSpriteSnapshotsView;
	}

	public WorldSpriteSnapshot getWorldSpriteSnapshot(int anchorIndex) {
		if (anchorIndex < 0 || anchorIndex >= worldSpriteSnapshots.size()) {
			return null;
		}
		return worldSpriteSnapshots.get(anchorIndex);
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

	public void release() {
		releaseDepthFrame();
		WorldFaceStorage releasedWorldFaceStorage;
		synchronized (this) {
			if (worldFaceStorageReleased) {
				return;
			}
			worldFaceStorageReleased = true;
			releasedWorldFaceStorage = worldFaceStorage;
		}
		releaseWorldFaceStorage(releasedWorldFaceStorage);
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

	public static final class WorldSpriteSnapshot {
		private final int anchorIndex;
		private final SpriteAnchor anchor;
		private final SpriteSubmission submission;
		private final CharacterSprite character;
		private final List<Renderer2DFrame.SpriteCommand> layers =
			new ArrayList<Renderer2DFrame.SpriteCommand>();
		private final List<Renderer2DFrame.SpriteCommand> layersView =
			Collections.unmodifiableList(layers);

		private WorldSpriteSnapshot(
			int anchorIndex,
			SpriteAnchor anchor,
			SpriteSubmission submission,
			CharacterSprite character) {
			this.anchorIndex = anchorIndex;
			this.anchor = anchor;
			this.submission = submission;
			this.character = character;
		}

		private void addLayer(Renderer2DFrame.SpriteCommand command) {
			layers.add(command);
		}

		public int getAnchorIndex() {
			return anchorIndex;
		}

		public SpriteAnchor getAnchor() {
			return anchor;
		}

		public SpriteSubmission getSubmission() {
			return submission;
		}

		public CharacterSprite getCharacter() {
			return character;
		}

		public List<Renderer2DFrame.SpriteCommand> getLayers() {
			return layersView;
		}

		public int getLayerCount() {
			return layers.size();
		}

		public boolean ownsLayer(Renderer2DFrame.SpriteCommand command) {
			for (Renderer2DFrame.SpriteCommand layer : layers) {
				if (layer == command) {
					return true;
				}
			}
			return false;
		}

		public int getPickIndex() {
			return anchor == null ? -1 : anchor.getPickIndex();
		}

		public boolean isPickable() {
			return anchor != null && anchor.isPickable();
		}
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

	private static final class WorldFaceStorage {
		private final List<FaceCommand> worldFaces = new ArrayList<FaceCommand>();
		private final List<FaceCommand> worldFacesView = Collections.unmodifiableList(worldFaces);
		private final Map<Long, FaceCommand> worldFacesByModelFace = new HashMap<Long, FaceCommand>();
		private final int[] worldFaceCountsByKind =
			new int[Renderer3DModelKind.values().length];
		private final ArrayDeque<FaceCommand>[] reusableFacesByVertexCount;
		private int faceCommandCapacity;

		@SuppressWarnings("unchecked")
		private WorldFaceStorage() {
			this.reusableFacesByVertexCount =
				(ArrayDeque<FaceCommand>[]) new ArrayDeque<?>[MAX_POOLED_FACE_VERTEX_COUNT + 1];
		}

		private FaceCommand acquireFaceCommand(int vertexCount) {
			if (vertexCount >= 0 && vertexCount <= MAX_POOLED_FACE_VERTEX_COUNT) {
				ArrayDeque<FaceCommand> reusableFaces =
					reusableFacesByVertexCount[vertexCount];
				if (reusableFaces != null && !reusableFaces.isEmpty()) {
					return reusableFaces.removeFirst();
				}
				faceCommandCapacity++;
			}
			return new FaceCommand(vertexCount);
		}

		private void recycleActiveFaces() {
			for (FaceCommand face : worldFaces) {
				int vertexCount = face.getVertexCount();
				if (vertexCount < 0 || vertexCount > MAX_POOLED_FACE_VERTEX_COUNT) {
					continue;
				}
				ArrayDeque<FaceCommand> reusableFaces =
					reusableFacesByVertexCount[vertexCount];
				if (reusableFaces == null) {
					reusableFaces = new ArrayDeque<FaceCommand>();
					reusableFacesByVertexCount[vertexCount] = reusableFaces;
				}
				reusableFaces.addFirst(face);
			}
			worldFaces.clear();
			worldFacesByModelFace.clear();
			Arrays.fill(worldFaceCountsByKind, 0);
		}
	}

	public static final class FaceCommand {
		private Renderer3DModelKind modelKind;
		private int modelIndex;
		private int faceId;
		private int texture;
		private int color;
		private int orientation;
		private int averageDepth;
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
		private int clippedVertexCount;

		private FaceCommand(int vertexCount) {
			this.cameraX = new int[vertexCount];
			this.cameraY = new int[vertexCount];
			this.cameraZ = new int[vertexCount];
			this.screenX = new int[vertexCount];
			this.screenY = new int[vertexCount];
			this.light = new int[vertexCount];
			this.baseLight = new int[vertexCount];
			this.textureU = new float[vertexCount];
			this.textureV = new float[vertexCount];
		}

		private void reset(
			Renderer3DModelKind modelKind,
			int modelIndex,
			int faceId,
			int texture,
			int color,
			int orientation,
			int averageDepth,
			RSModel model,
			int[] faceIndices,
			int[] sourceLight,
			int[] sourceBaseLight) {
			this.modelKind = modelKind;
			this.modelIndex = modelIndex;
			this.faceId = faceId;
			this.texture = texture;
			this.color = color;
			this.orientation = orientation;
			this.averageDepth = averageDepth;
			this.legacyDrawOrder = -1;
			this.clippedVertexCount = 0;
			for (int vertex = 0; vertex < cameraX.length; vertex++) {
				int modelVertex = faceIndices[vertex];
				cameraX[vertex] = model.vertXRot[modelVertex];
				cameraY[vertex] = model.vertYRot[modelVertex];
				cameraZ[vertex] = model.vertZRot[modelVertex];
				screenX[vertex] = model.vertexParam6[modelVertex];
				screenY[vertex] = model.vertexParam2[modelVertex];
			}
			copyLight(sourceLight, light);
			copyLight(sourceBaseLight, baseLight);
			populateTextureCoordinates(cameraX, cameraY, cameraZ, textureU, textureV);
		}

		private static void copyLight(int[] source, int[] destination) {
			if (source == null || source.length < destination.length) {
				Arrays.fill(destination, 0);
				return;
			}
			System.arraycopy(source, 0, destination, 0, destination.length);
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
			return hasClippedGeometry() ? clippedVertexCount : cameraX.length;
		}

		public int[] getCameraX() {
			return cameraX;
		}

		public int[] getRenderCameraX() {
			return hasClippedGeometry() ? clippedCameraX : cameraX;
		}

		public int[] getCameraY() {
			return cameraY;
		}

		public int[] getRenderCameraY() {
			return hasClippedGeometry() ? clippedCameraY : cameraY;
		}

		public int[] getCameraZ() {
			return cameraZ;
		}

		public int[] getRenderCameraZ() {
			return hasClippedGeometry() ? clippedCameraZ : cameraZ;
		}

		public int[] getScreenX() {
			return screenX;
		}

		public int[] getRenderScreenX() {
			return hasClippedGeometry() ? clippedScreenX : screenX;
		}

		public int[] getScreenY() {
			return screenY;
		}

		public int[] getRenderScreenY() {
			return hasClippedGeometry() ? clippedScreenY : screenY;
		}

		public int[] getRenderLight() {
			return hasClippedGeometry() ? clippedLight : light;
		}

		public int[] getRenderBaseLight() {
			return hasClippedGeometry() ? clippedBaseLight : baseLight;
		}

		public float[] getTextureU() {
			return textureU;
		}

		public float[] getRenderTextureU() {
			return hasClippedGeometry() ? clippedTextureU : textureU;
		}

		public float[] getTextureV() {
			return textureV;
		}

		public float[] getRenderTextureV() {
			return hasClippedGeometry() ? clippedTextureV : textureV;
		}

		private boolean hasClippedGeometry() {
			return clippedVertexCount >= 3;
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

			if (this.clippedCameraX == null || this.clippedCameraX.length != vertexCount) {
				this.clippedCameraX = new int[vertexCount];
				this.clippedCameraY = new int[vertexCount];
				this.clippedCameraZ = new int[vertexCount];
				this.clippedScreenX = new int[vertexCount];
				this.clippedScreenY = new int[vertexCount];
				this.clippedLight = new int[vertexCount];
				this.clippedBaseLight = new int[vertexCount];
				this.clippedTextureU = new float[vertexCount];
				this.clippedTextureV = new float[vertexCount];
			}
			System.arraycopy(cameraX, 0, this.clippedCameraX, 0, vertexCount);
			System.arraycopy(cameraY, 0, this.clippedCameraY, 0, vertexCount);
			System.arraycopy(cameraZ, 0, this.clippedCameraZ, 0, vertexCount);
			System.arraycopy(screenX, 0, this.clippedScreenX, 0, vertexCount);
			System.arraycopy(screenY, 0, this.clippedScreenY, 0, vertexCount);
			copyClippedLight(light, this.clippedLight, vertexCount);
			copyClippedLight(baseLight, this.clippedBaseLight, vertexCount);
			this.clippedVertexCount = vertexCount;
			populateTextureCoordinates(
				this.clippedCameraX,
				this.clippedCameraY,
				this.clippedCameraZ,
				this.clippedTextureU,
				this.clippedTextureV);
		}

		private static void copyClippedLight(int[] source, int[] destination, int vertexCount) {
			Arrays.fill(destination, 0);
			if (source != null) {
				System.arraycopy(source, 0, destination, 0, Math.min(source.length, vertexCount));
			}
		}

		private void populateTextureCoordinates(
			int[] sourceCameraX,
			int[] sourceCameraY,
			int[] sourceCameraZ,
			float[] destinationU,
			float[] destinationV) {
			Arrays.fill(destinationU, 0.0f);
			Arrays.fill(destinationV, 0.0f);
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
