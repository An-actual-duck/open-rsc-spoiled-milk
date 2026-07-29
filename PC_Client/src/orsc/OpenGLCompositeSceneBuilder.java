package orsc;

import orsc.graphics.Renderer2DFrame;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DMeshFrame;
import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.Renderer3DTextureData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * RENDERER-V2 OWNER: pure scene-command assembly for the OpenGL composite
 * world path. This class classifies legacy sprite commands, matches them to
 * renderer-owned world anchors, and builds ordering data without touching GL
 * state. OpenGLFramePresenter should only consume these commands for drawing.
 */
final class OpenGLCompositeSceneBuilder {
	private OpenGLCompositeSceneBuilder() {
	}

	static WorldSpriteMatch[] matchWorldSpriteAnchors(
		List<Renderer3DFrame.SpriteAnchor> anchors,
		Renderer2DFrame.SpriteCommand[] commands) {
		WorldSpriteMatch[] matches = new WorldSpriteMatch[anchors.size()];
		boolean[] usedCommands = new boolean[commands.length];
		for (int anchorIndex = 0; anchorIndex < anchors.size(); anchorIndex++) {
			Renderer3DFrame.SpriteAnchor anchor = anchors.get(anchorIndex);
			int bestCommand = -1;
			int bestScore = Integer.MAX_VALUE;
			for (int commandIndex = 0; commandIndex < commands.length; commandIndex++) {
				if (usedCommands[commandIndex]) {
					continue;
				}
				Renderer2DFrame.SpriteCommand command = commands[commandIndex];
				int score = worldSpriteMatchScore(anchor, command);
				if (score < bestScore) {
					bestScore = score;
					bestCommand = commandIndex;
				}
			}
			if (bestCommand >= 0) {
				usedCommands[bestCommand] = true;
				matches[anchorIndex] = new WorldSpriteMatch(anchor, commands[bestCommand]);
			}
		}
		return matches;
	}

	static int worldSpriteMatchScore(
		Renderer3DFrame.SpriteAnchor anchor,
		Renderer2DFrame.SpriteCommand command) {
		return worldSpriteMatchScore(anchor, command, true);
	}

	static int worldSpriteMatchScore(
		Renderer3DFrame.SpriteAnchor anchor,
		Renderer2DFrame.SpriteCommand command,
		boolean requireSpriteId) {
		if (command.getPhase() != Renderer2DFrame.Phase.SCENE) {
			return Integer.MAX_VALUE;
		}

		if (requireSpriteId && command.getLegacySpriteId() != anchor.getSpriteId()) {
			return Integer.MAX_VALUE;
		}

		int anchorLeft = anchor.getDrawX();
		int anchorTop = anchor.getDrawY();
		int anchorRight = anchorLeft + anchor.getDrawWidth();
		int anchorBottom = anchorTop + anchor.getDrawHeight();
		int commandLeft = command.getX();
		int commandTop = command.getY();
		int commandRight = commandLeft + command.getWidth();
		int commandBottom = commandTop + command.getHeight();
		int tolerance = Math.max(8, Math.max(anchor.getDrawWidth(), anchor.getDrawHeight()) / 4);
		if (commandRight < anchorLeft - tolerance
			|| commandLeft > anchorRight + tolerance
			|| commandBottom < anchorTop - tolerance
			|| commandTop > anchorBottom + tolerance) {
			return Integer.MAX_VALUE;
		}

		int anchorCenterX = anchorLeft + anchor.getDrawWidth() / 2;
		int anchorCenterY = anchorTop + anchor.getDrawHeight() / 2;
		int commandCenterX = commandLeft + command.getWidth() / 2;
		int commandCenterY = commandTop + command.getHeight() / 2;
		int centerDelta = Math.abs(anchorCenterX - commandCenterX) + Math.abs(anchorCenterY - commandCenterY);
		int sizeDelta = Math.abs(anchor.getDrawWidth() - command.getWidth())
			+ Math.abs(anchor.getDrawHeight() - command.getHeight());
		return centerDelta * 4 + sizeDelta;
	}

	static WorldSpriteAnchorMatch classifyWorldSpriteAnchorMatch(
		Frame frame,
		Renderer2DFrame.SpriteCommand command,
		Renderer3DFrame.SpriteAnchor anchor) {
		if (frame == null || frame.renderer3DFrame == null || command == null || anchor == null) {
			return WorldSpriteAnchorMatch.unmatched();
		}
		if (findOwnedSpriteAnchor(frame, command) == anchor) {
			return new WorldSpriteAnchorMatch("owner-anchor", 0);
		}
		int strictScore = worldSpriteMatchScore(anchor, command, true);
		if (strictScore != Integer.MAX_VALUE) {
			return new WorldSpriteAnchorMatch("strict-id-bounds", strictScore);
		}
		int relaxedScore = worldSpriteMatchScore(anchor, command, false);
		if (relaxedScore != Integer.MAX_VALUE) {
			return new WorldSpriteAnchorMatch(
				command.getLegacySpriteId() == anchor.getSpriteId() ? "relaxed-bounds" : "relaxed-cross-id",
				relaxedScore);
		}
		if (command.getLegacySpriteId() == anchor.getSpriteId()) {
			return new WorldSpriteAnchorMatch("id-only", Integer.MAX_VALUE);
		}
		return WorldSpriteAnchorMatch.unmatched();
	}

	static boolean isLegacySceneSpriteCommand(Renderer2DFrame.SpriteCommand command) {
		return command != null
			&& command.getPhase() == Renderer2DFrame.Phase.SCENE
			&& command.getLegacySpriteId() >= 0;
	}

	static boolean isLegacyEntitySpriteCommand(Renderer2DFrame.SpriteCommand command) {
		if (!isLegacySceneSpriteCommand(command)) {
			return false;
		}
		int legacySpriteId = command.getLegacySpriteId();
		return (legacySpriteId >= 5000 && legacySpriteId < 20000)
			|| (legacySpriteId >= 20000 && legacySpriteId < 40000);
	}

	static boolean isLegacyGroundItemSpriteCommand(Renderer2DFrame.SpriteCommand command) {
		if (!isLegacySceneSpriteCommand(command)) {
			return false;
		}
		int legacySpriteId = command.getLegacySpriteId();
		return legacySpriteId >= 40000 && legacySpriteId < 50000;
	}

	static boolean isOpenGLCompositeWorldSpriteCommand(Renderer2DFrame.SpriteCommand command) {
		return isLegacyEntitySpriteCommand(command) || isLegacyGroundItemSpriteCommand(command);
	}

	static List<WorldSpriteCommand> buildWorldSpriteCommands(
		Frame frame,
		Renderer2DFrame.SpriteCommand[] commands) {
		List<WorldSpriteCommand> entityCommands = new ArrayList<WorldSpriteCommand>();
		for (Renderer2DFrame.SpriteCommand command : commands) {
			if (!isOpenGLCompositeWorldSpriteCommand(command)) {
				continue;
			}
			entityCommands.add(buildWorldSpriteCommand(frame, command));
		}
		Collections.sort(entityCommands, new Comparator<WorldSpriteCommand>() {
			@Override
			public int compare(WorldSpriteCommand left, WorldSpriteCommand right) {
				if (left.legacyDrawOrder != right.legacyDrawOrder) {
					return left.legacyDrawOrder < right.legacyDrawOrder ? -1 : 1;
				}
				int leftSequence = left.command.getSequence();
				int rightSequence = right.command.getSequence();
				if (leftSequence != rightSequence) {
					return leftSequence < rightSequence ? -1 : 1;
				}
				return 0;
			}
		});
		return entityCommands;
	}

	static List<OpenGLCompositeSceneCommand> buildSceneCommands(
		Frame frame,
		Renderer2DFrame.SpriteCommand[] commands) {
		List<OpenGLCompositeSceneCommand> sceneCommands = new ArrayList<OpenGLCompositeSceneCommand>();
		List<WorldSpriteCommand> worldSpriteCommands = buildWorldSpriteCommands(frame, commands);
		for (WorldSpriteCommand worldSpriteCommand : worldSpriteCommands) {
			sceneCommands.add(OpenGLCompositeSceneCommand.worldSprite(worldSpriteCommand));
		}
		Collections.sort(sceneCommands, new Comparator<OpenGLCompositeSceneCommand>() {
			@Override
			public int compare(OpenGLCompositeSceneCommand left, OpenGLCompositeSceneCommand right) {
				if (left.legacyDrawOrder != right.legacyDrawOrder) {
					return left.legacyDrawOrder < right.legacyDrawOrder ? -1 : 1;
				}
				if (left.sequence != right.sequence) {
					return left.sequence < right.sequence ? -1 : 1;
				}
				return left.kind.ordinal() - right.kind.ordinal();
			}
		});
		return sceneCommands;
	}

	static WorldSpriteCommand buildWorldSpriteCommand(
		Frame frame,
		Renderer2DFrame.SpriteCommand command) {
		Renderer3DFrame.SpriteAnchor anchor = findSpriteAnchor(frame, command);
		WorldSpriteAnchorMatch anchorMatch = classifyWorldSpriteAnchorMatch(frame, command, anchor);
		return new WorldSpriteCommand(command, anchor, anchorMatch);
	}

	static Set<Long> buildFrontOccluderFaceKeys(
		Frame frame,
		List<WorldSpriteCommand> worldSpriteCommands,
		int minExclusiveOrder,
		int maxExclusiveOrder) {
		if (frame == null || frame.renderer3DFrame == null || worldSpriteCommands == null) {
			return Collections.emptySet();
		}
		List<Renderer3DFrame.FaceCommand> faces = frame.renderer3DFrame.getWorldFaces();
		if (faces == null || faces.isEmpty()) {
			return Collections.emptySet();
		}
		Set<Long> faceKeys = new HashSet<Long>();
		for (Renderer3DFrame.FaceCommand face : faces) {
			int order = face.getLegacyDrawOrder();
			if (order <= minExclusiveOrder || order < 0) {
				continue;
			}
			if (maxExclusiveOrder != Integer.MAX_VALUE && order >= maxExclusiveOrder) {
				continue;
			}
			if (!isFrontOccluderKind(face.getModelKind())) {
				continue;
			}
			int[] faceBounds = boundsForFace(face.getRenderScreenX(), face.getRenderScreenY());
			if (countOverlappingWorldSpriteCommands(faceBounds, worldSpriteCommands) > 0) {
				faceKeys.add(openGLCompositeModelFaceKey(face.getModelIndex(), face.getFaceId()));
			}
		}
		return faceKeys;
	}

	static List<StaticWorldCommand> buildStaticWorldCommands(Frame frame) {
		if (frame == null || frame.renderer3DFrame == null || frame.renderer3DFrame.getMeshFrame() == null) {
			return Collections.emptyList();
		}
		Renderer3DMeshFrame meshFrame = frame.renderer3DFrame.getMeshFrame();
		Renderer3DModelKind[] modelKinds = meshFrame.getTriangleModelKinds();
		int[] modelIndices = meshFrame.getTriangleModelIndices();
		int[] faceIds = meshFrame.getTriangleFaceIds();
		int[] legacyDrawOrders = meshFrame.getTriangleLegacyDrawOrders();
		int triangleCount = Math.min(
			meshFrame.getTriangleCount(),
			Math.min(
				Math.min(modelKinds.length, modelIndices.length),
				Math.min(faceIds.length, legacyDrawOrders.length)));
		Map<Renderer3DModelKind, StaticWorldCommandBuilder> builders =
			new LinkedHashMap<Renderer3DModelKind, StaticWorldCommandBuilder>();
		for (int triangle = 0; triangle < triangleCount; triangle++) {
			Renderer3DModelKind modelKind = modelKinds[triangle] == null
				? Renderer3DModelKind.UNCLASSIFIED
				: modelKinds[triangle];
			StaticWorldCommandBuilder builder = builders.get(modelKind);
			if (builder == null) {
				builder = new StaticWorldCommandBuilder(modelKind);
				builders.put(modelKind, builder);
			}
			builder.addTriangle(
				openGLCompositeModelFaceKey(modelIndices[triangle], faceIds[triangle]),
				legacyDrawOrders[triangle]);
		}
		List<StaticWorldCommand> commands = new ArrayList<StaticWorldCommand>(builders.size());
		for (StaticWorldCommandBuilder builder : builders.values()) {
			commands.add(builder.build());
		}
		return commands;
	}

	static List<StaticWorldMaterialTriangle> buildStaticWorldMaterialTriangles(Frame frame) {
		if (frame == null || frame.renderer3DFrame == null || frame.renderer3DFrame.getMeshFrame() == null) {
			return Collections.emptyList();
		}
		Renderer3DMeshFrame meshFrame = frame.renderer3DFrame.getMeshFrame();
		int[] textures = meshFrame.getTriangleTextures();
		Renderer3DModelKind[] modelKinds = meshFrame.getTriangleModelKinds();
		int[] modelIndices = meshFrame.getTriangleModelIndices();
		int[] faceIds = meshFrame.getTriangleFaceIds();
		int triangleCount = Math.min(
			meshFrame.getTriangleCount(),
			Math.min(Math.min(textures.length, modelKinds.length), Math.min(modelIndices.length, faceIds.length)));
		List<StaticWorldMaterialTriangle> triangles =
			new ArrayList<StaticWorldMaterialTriangle>(triangleCount);
		for (int triangle = 0; triangle < triangleCount; triangle++) {
			int textureId = textures[triangle];
			Renderer3DTextureData textureData = textureId >= 0 ? meshFrame.getTexture(textureId) : null;
			triangles.add(new StaticWorldMaterialTriangle(
				triangle,
				OpenGLStaticWorldMaterials.classify(textureId, textureData),
				modelKinds[triangle] == null ? Renderer3DModelKind.UNCLASSIFIED : modelKinds[triangle],
				modelIndices[triangle],
				faceIds[triangle],
				textureId,
				textureData != null && textureData.hasTransparency()));
		}
		return triangles;
	}

	static Renderer3DFrame.SpriteAnchor findSpriteAnchor(
		Frame frame,
		Renderer2DFrame.SpriteCommand command) {
		if (frame == null || frame.renderer3DFrame == null || command == null) {
			return null;
		}
		Renderer3DFrame.SpriteAnchor ownedAnchor = findOwnedSpriteAnchor(frame, command);
		if (ownedAnchor != null) {
			return ownedAnchor;
		}
		int legacySpriteId = command.getLegacySpriteId();
		List<Renderer3DFrame.SpriteAnchor> anchors = frame.renderer3DFrame.getSpriteAnchors();
		Renderer3DFrame.SpriteAnchor bestAnchor = null;
		int bestScore = Integer.MAX_VALUE;
		Renderer3DFrame.SpriteAnchor exactIdAnchor = null;
		for (Renderer3DFrame.SpriteAnchor anchor : anchors) {
			if (anchor.getSpriteId() == legacySpriteId && exactIdAnchor == null) {
				exactIdAnchor = anchor;
			}
			int score = worldSpriteMatchScore(anchor, command);
			if (score < bestScore) {
				bestScore = score;
				bestAnchor = anchor;
			}
		}
		if (bestAnchor != null) {
			return bestAnchor;
		}
		if (exactIdAnchor != null) {
			return exactIdAnchor;
		}
		for (Renderer3DFrame.SpriteAnchor anchor : anchors) {
			int score = worldSpriteMatchScore(anchor, command, false);
			if (score < bestScore) {
				bestScore = score;
				bestAnchor = anchor;
			}
		}
		if (bestAnchor != null) {
			return bestAnchor;
		}
		for (Renderer3DFrame.SpriteAnchor anchor : anchors) {
			if (anchor.getSpriteId() == legacySpriteId) {
				return anchor;
			}
		}
		return null;
	}

	static Renderer3DFrame.SpriteAnchor findOwnedSpriteAnchor(
		Frame frame,
		Renderer2DFrame.SpriteCommand command) {
		if (frame == null || frame.renderer3DFrame == null || command == null) {
			return null;
		}
		int anchorIndex = command.getSceneSpriteAnchorIndex();
		List<Renderer3DFrame.SpriteAnchor> anchors = frame.renderer3DFrame.getSpriteAnchors();
		if (anchorIndex < 0 || anchorIndex >= anchors.size()) {
			return null;
		}
		Renderer3DFrame.SpriteAnchor anchor = anchors.get(anchorIndex);
		if (anchor == null
			|| (command.getLegacySpriteId() >= 0
				&& command.getLegacySpriteId() != anchor.getSpriteId())
			|| (command.getSceneSpriteDrawOrder() >= 0
				&& command.getSceneSpriteDrawOrder() != anchor.getLegacyDrawOrder())) {
			return null;
		}
		return anchor;
	}

	private static boolean isFrontOccluderKind(Renderer3DModelKind kind) {
		return kind == Renderer3DModelKind.WALL
			|| kind == Renderer3DModelKind.GAME_OBJECT
			|| kind == Renderer3DModelKind.WALL_OBJECT;
	}

	private static int countOverlappingWorldSpriteCommands(
		int[] faceBounds,
		List<WorldSpriteCommand> worldSpriteCommands) {
		if (faceBounds == null) {
			return 0;
		}
		int overlappingSpriteCommands = 0;
		for (WorldSpriteCommand worldSpriteCommand : worldSpriteCommands) {
			if (worldSpriteCommand == null || worldSpriteCommand.command == null) {
				continue;
			}
			Renderer2DFrame.SpriteCommand command = worldSpriteCommand.command;
			int[] spriteBounds = new int[] {
				command.getX(),
				command.getY(),
				command.getX() + command.getWidth(),
				command.getY() + command.getHeight()
			};
			if (overlapsBounds(faceBounds, spriteBounds)) {
				overlappingSpriteCommands++;
			}
		}
		return overlappingSpriteCommands;
	}

	private static boolean overlapsBounds(int[] first, int[] second) {
		return first != null
			&& second != null
			&& first.length >= 4
			&& second.length >= 4
			&& first[0] < second[2]
			&& first[2] > second[0]
			&& first[1] < second[3]
			&& first[3] > second[1];
	}

	private static int[] boundsForFace(int[] xs, int[] ys) {
		if (xs == null || ys == null || xs.length == 0 || xs.length != ys.length) {
			return null;
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int i = 0; i < xs.length; i++) {
			minX = Math.min(minX, xs[i]);
			minY = Math.min(minY, ys[i]);
			maxX = Math.max(maxX, xs[i]);
			maxY = Math.max(maxY, ys[i]);
		}
		return new int[] { minX, minY, maxX, maxY };
	}

	static long openGLCompositeModelFaceKey(int modelIndex, int faceId) {
		return ((long) modelIndex << 32) ^ (faceId & 0xffffffffL);
	}

	static int openGLCompositeModelIndex(long modelFaceKey) {
		return (int) (modelFaceKey >> 32);
	}

	static int openGLCompositeFaceId(long modelFaceKey) {
		return (int) modelFaceKey;
	}
}
