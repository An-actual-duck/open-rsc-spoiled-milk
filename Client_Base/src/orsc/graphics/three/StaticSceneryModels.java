package orsc.graphics.three;

import java.util.Locale;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.DoorDef;
import com.openrsc.client.entityhandling.defs.GameObjectDef;
import orsc.util.GenUtil;

/** Shared model construction for installed and detached static scenery. */
public final class StaticSceneryModels {
	private static final String TIN_ROCK_MODEL_NAME = "tinrock1";
	private static final int TIN_ROCK_LEGACY_RED_BACK_FACE = -15361;
	private static final int DRAGON_SULFUR_ROCK_OBJECT_ID = 1328;
	private static final int DRAGON_SULFUR_ROCK_COLOR_RESOURCE = GenUtil.colorToResource(255, 112, 16);
	private static final String FISHING_SPOT_MODEL_NAME = "fishing";
	private static final String WATCHTOWER_MODEL_NAME = "watchtower";
	private static final int WATCHTOWER_LEGACY_OUTLIER_MODEL_COORDINATE = 2048;
	private static final int REMASTER_FIRE_GLOW_COLOR = 0xff661c;
	private static final int REMASTER_TORCH_GLOW_RADIUS = 256;
	private static final int REMASTER_TORCH_GLOW_INTENSITY = 132;
	private static final int REMASTER_FIRE_GLOW_RADIUS = 320;
	private static final int REMASTER_FIRE_GLOW_INTENSITY = 170;
	private static final int REMASTER_FURNACE_GLOW_RADIUS = 384;
	private static final int REMASTER_FURNACE_GLOW_INTENSITY = 130;

	private StaticSceneryModels() { }

	public static RSModel gameObject(RSModel model, GameObjectDef def, int objectId,
		int localX, int localZ, int direction, int tileSize, int index, int elevation) {
		int xSize;
		int zSize;
		if (direction == 0 || direction == 4) {
			xSize = def.getWidth();
			zSize = def.getHeight();
		} else {
			xSize = def.getHeight();
			zSize = def.getWidth();
		}
		int x = (2 * localX + xSize) * tileSize / 2;
		int z = (2 * localZ + zSize) * tileSize / 2;
		model.key = -1 - index;
		model.addRotation(0, direction * 32, 0);
		model.setDiffuseLightAndColor(
			-50, -10, -50, 48, 48, true, 117);
		model.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);
		model.setTranslate(x, -elevation, z);
		if (objectId == 74) {
			model.translate2(0, -480, 0);
		}
		return prepareGameObjectInstanceModel(
			objectId, model);
	}

	public static RSModel wallObject(int objectId, int localX, int localZ, int direction,
		int tileSize, int index, int elevation1, int elevation2) {
		DoorDef definition = EntityHandler.getDoorDef(objectId);
		if (definition == null
			|| elevation1 == World.PRESENTATION_ELEVATION_UNAVAILABLE
			|| elevation2 == World.PRESENTATION_ELEVATION_UNAVAILABLE) {
			return null;
		}
		int x1 = localX;
		int x2 = x1;
		int y1 = localZ;
		int y2 = y1;
		if (direction == 1) {
			y2++;
		}
		if (direction == 0) {
			x2++;
		}
		if (direction == 2) {
			x1++;
			y2++;
		}
		if (direction == 3) {
			x2++;
			y2++;
		}
		int x1World = x1 * tileSize;
		int y1World = y1 * tileSize;
		int x2World = x2 * tileSize;
		int y2World = y2 * tileSize;
		int height = definition.getWallObjectHeight();
		RSModel model = new RSModel(4, 1);
		int v1 = model.insertVertex(
			x1World, -elevation1, y1World);
		int v2 = model.insertVertex(
			x1World, -elevation1 - height, y1World);
		int v3 = model.insertVertex(
			x2World, -height - elevation2, y2World);
		int v4 = model.insertVertex(
			x2World, -elevation2, y2World);
		model.insertFace(
			4,
			new int[] {v1, v2, v3, v4},
			definition.getModelVar2(),
			definition.getModelVar3(),
			false);
		model.setRenderer3DModelKind(
			Renderer3DModelKind.WALL_OBJECT);
		model.setDiffuseLightAndColor(
			-50, -10, -50, 60, 24, false, -95);
		model.key = -1 - index;
		applyRenderer3DMaterialMetadata(
			Renderer3DModelKind.WALL_OBJECT,
			objectId,
			model);
		return model;
	}

	public static void applyRenderer3DMaterialMetadata(Renderer3DModelKind kind, int objectId, RSModel model) {
		if (model == null) {
			return;
		}
		model.clearRenderer3DGlowEmitter();
		GameObjectDef def = kind == Renderer3DModelKind.GAME_OBJECT
			? EntityHandler.getObjectDef(objectId)
			: null;
		Renderer3DGlowSpec glowSpec = renderer3DGlowSpecFor(def);
		model.setRenderer3DMaterialFamily(
			Renderer3DMaterialClassifier.classifyObject(kind, def, glowSpec != null));
		if (glowSpec == null) {
			return;
		}
		model.setRenderer3DGlowEmitter(
			glowSpec.color,
			glowSpec.radius,
			glowSpec.intensity);
	}

	private static Renderer3DGlowSpec renderer3DGlowSpecFor(GameObjectDef def) {
		if (def == null) {
			return null;
		}
		String modelName = lowerOrEmpty(def.getObjectModel());
		String objectName = lowerOrEmpty(def.getName());
		if (modelName.startsWith("torcha") || modelName.startsWith("skulltorcha")) {
			return new Renderer3DGlowSpec(
				REMASTER_FIRE_GLOW_COLOR,
				REMASTER_TORCH_GLOW_RADIUS,
				REMASTER_TORCH_GLOW_INTENSITY);
		}
		if (modelName.startsWith("firea")
			|| modelName.startsWith("fireplacea")
			|| modelName.startsWith("firespell")
			|| objectName.equals("flames")) {
			return new Renderer3DGlowSpec(
				REMASTER_FIRE_GLOW_COLOR,
				REMASTER_FIRE_GLOW_RADIUS,
				REMASTER_FIRE_GLOW_INTENSITY);
		}
		if (modelName.equals("furnace")
			|| modelName.equals("cave furnace")
			|| objectName.contains("furnace")
			|| objectName.contains("lava forge")) {
			return new Renderer3DGlowSpec(
				REMASTER_FIRE_GLOW_COLOR,
				REMASTER_FURNACE_GLOW_RADIUS,
				REMASTER_FURNACE_GLOW_INTENSITY);
		}
		return null;
	}

	public static RSModel prepareGameObjectInstanceModel(int objectId, RSModel model) {
		if (model == null) {
			return null;
		}

		GameObjectDef definition = EntityHandler.getObjectDef(objectId);
		model.setInteractionOnly(definition != null && definition.isInteractionOnly());

		if (objectId == DRAGON_SULFUR_ROCK_OBJECT_ID) {
			model.tintVisibleFaces(DRAGON_SULFUR_ROCK_COLOR_RESOURCE);
		}
		applyRenderer3DMaterialMetadata(
			Renderer3DModelKind.GAME_OBJECT,
			objectId,
			model);
		return model;
	}

	public static RSModel normalizeLoadedModel(String modelName, RSModel model) {
		if (TIN_ROCK_MODEL_NAME.equals(modelName)) {
			model.hideBackFacesMatching(TIN_ROCK_LEGACY_RED_BACK_FACE);
		}
		if (WATCHTOWER_MODEL_NAME.equals(modelName)) {
			model.hideFacesUsingVerticesOutsideLocalBounds(
				WATCHTOWER_LEGACY_OUTLIER_MODEL_COORDINATE,
				WATCHTOWER_LEGACY_OUTLIER_MODEL_COORDINATE,
				WATCHTOWER_LEGACY_OUTLIER_MODEL_COORDINATE);
		}
		if (FISHING_SPOT_MODEL_NAME.equals(modelName)) {
			return model.withFishingSpotClarityOverlay();
		}
		return model;
	}

	private static final class Renderer3DGlowSpec {
		private final int color;
		private final int radius;
		private final int intensity;

		private Renderer3DGlowSpec(int color, int radius, int intensity) {
			this.color = color;
			this.radius = radius;
			this.intensity = intensity;
		}
	}

	private static String lowerOrEmpty(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}
}
