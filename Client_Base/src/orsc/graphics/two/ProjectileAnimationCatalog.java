package orsc.graphics.two;

import com.openrsc.client.entityhandling.EntityHandler.PROJECTILE_TYPES;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spritesheet-backed moving-projectile fallbacks.
 *
 * Runtime keys describe reusable visual types, not the spell or NPC that first
 * used the art. Explicit projectile assignments can replace these fallbacks
 * later without renaming the underlying asset.
 */
public final class ProjectileAnimationCatalog {

	public static final String CATEGORY = "projectile-moving";

	public static final class Segment {
		private final String sheetPath;
		private final int columns;
		private final int rows;
		private final int firstFrame;
		private final int frameCount;

		private Segment(String sheetPath, int columns, int rows, int firstFrame, int frameCount) {
			this.sheetPath = sheetPath;
			this.columns = columns;
			this.rows = rows;
			this.firstFrame = firstFrame;
			this.frameCount = frameCount;
		}

		public String getSheetPath() { return sheetPath; }
		public int getColumns() { return columns; }
		public int getRows() { return rows; }
		public int getFirstFrame() { return firstFrame; }
		public int getFrameCount() { return frameCount; }
	}

	public static final class Definition {
		private final String key;
		private final String sourceLabel;
		private final String sheetPath;
		private final int columns;
		private final int rows;
		private final int firstFrame;
		private final int frameCount;
		private final int maxTargetSize;

		private Definition(String key, String sourceLabel, String sheetPath,
				int columns, int rows, int firstFrame, int frameCount, int maxTargetSize) {
			this.key = key;
			this.sourceLabel = sourceLabel;
			this.sheetPath = sheetPath;
			this.columns = columns;
			this.rows = rows;
			this.firstFrame = firstFrame;
			this.frameCount = frameCount;
			this.maxTargetSize = maxTargetSize;
		}

		public String getKey() {
			return key;
		}

		public String getSourceLabel() {
			return sourceLabel;
		}

		public String getSheetPath() {
			return sheetPath;
		}

		public int getColumns() {
			return columns;
		}

		public int getRows() {
			return rows;
		}

		public int getFirstFrame() {
			return firstFrame;
		}

		public int getFrameCount() {
			return frameCount;
		}

		public int getMaxTargetSize() {
			return maxTargetSize;
		}
	}

	private static final Map<String, Definition> DEFINITIONS;
	private static final Map<Integer, String> PROJECTILE_FALLBACKS;
	private static final Map<String, Segment[]> STARTUP_SEGMENTS;
	private static final Map<String, Segment[]> IMPACT_SEGMENTS;
	private static final Map<String, Integer> IMPACT_SCREEN_SIZES;

	static {
		LinkedHashMap<String, Definition> definitions = new LinkedHashMap<String, Definition>();
		define(definitions, "acid-basic", "Acid VFX 1", "acid-basic/Acid VFX 01.png", 16, 1, 0, 10);
		define(definitions, "earth-basic", "Earth projectile", "earth-basic/Earth projectile Spritesheet .png", 9, 2, 0, 6);
		define(definitions, "fire-basic", "Firebolt", "fire-basic/Firebolt SpriteSheet.png", 11, 1, 0, 4);
		define(definitions, "ice-basic", "Ice VFX 1", "ice-basic/IceVFX 1 Repeatable.png", 10, 1, 0, 10);
		define(definitions, "thunder-basic", "Thunder Ball", "thunder-basic/Thunder ball wo blur.png", 9, 2, 0, 9);
		define(definitions, "water-basic", "Water Ball", "water-basic/WaterBall - Startup and Infinite.png", 5, 5, 0, 21);
		define(definitions, "wind-basic", "Wind projectile", "wind-basic/Projectile 2.png", 8, 1, 0, 8);
		define(definitions, "wood-basic", "Wood VFX 01", "wood-basic/Wood VFX 01 Repeatable.png", 8, 1, 0, 8);
		define(definitions, "holy-basic", "Holy VFX 01", "holy-basic/Holy VFX 01 Repeatable.png", 8, 1, 0, 8);
		define(definitions, "arrow-basic", "Arrow", "arrow-basic/arrow.png", 1, 1, 0, 1);
		define(definitions, "bolt-basic", "Crossbow bolt", "bolt-basic/bolt-basic.png", 1, 1, 0, 1);
		define(definitions, "dart-basic", "Throwing dart", "dart-basic/dart.png", 1, 1, 0, 1);
		define(definitions, "throwing-knife-basic", "Throwing knife", "throwing-knife-basic/throwing-knife-basic.png", 8, 1, 0, 8);
		define(definitions, "shuriken-basic", "Shuriken", "shuriken-basic/shuriken-basic.png", 8, 1, 0, 8);
		define(definitions, "thunder-2", "Thunder bird", "thunder-2/Projectile/Projectile 2 wo blur.png", 16, 1, 0, 16);
		define(definitions, "acid-basic-2", "Acid armor proc", "acid-basic-2/Acid VFX 03(56x48).png", 16, 1, 0, 16);
		define(definitions, "electric-disc-basic", "Electric Disc", "Eletric Disc (32x32)/Eletricity Disc wo blur.png", 13, 1, 7, 6);
		DEFINITIONS = Collections.unmodifiableMap(definitions);

		LinkedHashMap<String, Segment[]> startupSegments = new LinkedHashMap<String, Segment[]>();
		phases(startupSegments, "ice-basic",
			segment("ice-basic/Ice VFX 1 Start.png", 3, 1, 0, 3));
		phases(startupSegments, "holy-basic",
			segment("holy-basic/Holy VFX 01 Initial.png", 2, 1, 0, 2));
		phases(startupSegments, "electric-disc-basic",
			segment("Eletric Disc (32x32)/Eletricity Disc wo blur.png", 13, 1, 0, 6));
		STARTUP_SEGMENTS = Collections.unmodifiableMap(startupSegments);

		LinkedHashMap<String, Segment[]> impactSegments = new LinkedHashMap<String, Segment[]>();
		phases(impactSegments, "acid-basic",
			segment("acid-basic/Acid VFX 01.png", 16, 1, 10, 6));
		phases(impactSegments, "earth-basic",
			segment("earth-basic/Earth projectile Spritesheet .png", 9, 2, 6, 10));
		phases(impactSegments, "fire-basic",
			segment("fire-basic/Firebolt SpriteSheet.png", 11, 1, 5, 6));
		phases(impactSegments, "ice-basic",
			segment("ice-basic/Ice VFX 1 Hit.png", 8, 1, 0, 8));
		phases(impactSegments, "thunder-basic",
			segment("thunder-basic/Thunder ball wo blur.png", 9, 2, 9, 7));
		phases(impactSegments, "water-basic",
			segment("water-basic/WaterBall - Impact.png", 4, 4, 0, 16));
		phases(impactSegments, "wind-basic",
			segment("wind-basic/Projectile 2 impact.png", 6, 1, 0, 6));
		phases(impactSegments, "wood-basic",
			segment("wood-basic/Wood VFX 01 Hit.png", 7, 1, 0, 7));
		phases(impactSegments, "holy-basic",
			segment("holy-basic/Holy VFX 01 Impact.png", 7, 1, 0, 7));
		phases(impactSegments, "thunder-2",
			segment("thunder-2/Hit/Thunder hit wo blur.png", 6, 1, 0, 6));
		IMPACT_SEGMENTS = Collections.unmodifiableMap(impactSegments);

		LinkedHashMap<String, Integer> impactScreenSizes = new LinkedHashMap<String, Integer>();
		impactSize(impactScreenSizes, "fire-basic", 48);
		impactSize(impactScreenSizes, "water-basic", 48);
		IMPACT_SCREEN_SIZES = Collections.unmodifiableMap(impactScreenSizes);

		LinkedHashMap<Integer, String> fallbacks = new LinkedHashMap<Integer, String>();
		fallback(fallbacks, PROJECTILE_TYPES.FIREBALL, "fire-basic");
		fallback(fallbacks, PROJECTILE_TYPES.WIND_ARROW, "wind-basic");
		fallback(fallbacks, PROJECTILE_TYPES.ROCK_THROW, "earth-basic");
		fallback(fallbacks, PROJECTILE_TYPES.WATER_BALL, "water-basic");
		fallback(fallbacks, PROJECTILE_TYPES.THROWING_KNIFE, "throwing-knife-basic");
		fallback(fallbacks, PROJECTILE_TYPES.ARROW, "arrow-basic");
		fallback(fallbacks, PROJECTILE_TYPES.THROWING_DART, "dart-basic");
		fallback(fallbacks, PROJECTILE_TYPES.CLAWS_OF_GUTHIX, "holy-basic");
		fallback(fallbacks, PROJECTILE_TYPES.THUNDER_BALL, "thunder-basic");
		fallback(fallbacks, PROJECTILE_TYPES.ICICLE_SHOT, "ice-basic");
		fallback(fallbacks, PROJECTILE_TYPES.ACID_DROP, "acid-basic");
		fallback(fallbacks, PROJECTILE_TYPES.BRANCH_SPORE, "wood-basic");
		fallback(fallbacks, PROJECTILE_TYPES.BOLT, "bolt-basic");
		fallback(fallbacks, PROJECTILE_TYPES.ENEMY_FIRE_BASIC, "fire-basic");
		fallback(fallbacks, PROJECTILE_TYPES.HOLY_MAGIC, "holy-basic");
		fallback(fallbacks, PROJECTILE_TYPES.SHURIKEN, "shuriken-basic");
		fallback(fallbacks, PROJECTILE_TYPES.ENEMY_AIR_BASIC, "wind-basic");
		fallback(fallbacks, PROJECTILE_TYPES.ENEMY_WATER_BASIC, "water-basic");
		fallback(fallbacks, PROJECTILE_TYPES.BLUE_DRAGON_MAGIC, "water-basic");
		fallback(fallbacks, PROJECTILE_TYPES.CHAIN_LIGHTNING_A, "thunder-basic");
		fallback(fallbacks, PROJECTILE_TYPES.CHAIN_LIGHTNING_B, "thunder-basic");
		fallback(fallbacks, PROJECTILE_TYPES.CHAIN_LIGHTNING_C, "thunder-basic");
		fallback(fallbacks, PROJECTILE_TYPES.THUNDER_BIRD, "thunder-2");
		fallback(fallbacks, PROJECTILE_TYPES.EARTH_LEAD_2, "earth-basic");
		fallback(fallbacks, PROJECTILE_TYPES.FIRE_LEAD_2, "fire-basic");
		fallback(fallbacks, PROJECTILE_TYPES.ICE_LEAD_2, "ice-basic");
		fallback(fallbacks, PROJECTILE_TYPES.ACID_LEAD_2, "acid-basic");
		fallback(fallbacks, PROJECTILE_TYPES.WOOD_LEAD_2, "wood-basic");
		fallback(fallbacks, PROJECTILE_TYPES.ACID_ARMOR_PROC, "acid-basic-2");
		fallback(fallbacks, PROJECTILE_TYPES.ELECTRIC_DISC, "electric-disc-basic");
		PROJECTILE_FALLBACKS = Collections.unmodifiableMap(fallbacks);
	}

	private ProjectileAnimationCatalog() {
	}

	private static void define(Map<String, Definition> definitions, String key, String sourceLabel,
			String sheetPath, int columns, int rows, int firstFrame, int frameCount) {
		if (definitions.containsKey(key)) {
			throw new IllegalStateException("Duplicate projectile animation key: " + key);
		}
		if (columns <= 0 || rows <= 0 || firstFrame < 0 || frameCount <= 0
			|| firstFrame + frameCount > columns * rows) {
			throw new IllegalArgumentException("Invalid spritesheet geometry for " + key);
		}
		definitions.put(key, new Definition(key, sourceLabel, sheetPath,
			columns, rows, firstFrame, frameCount, 64));
	}

	private static void fallback(Map<Integer, String> fallbacks, PROJECTILE_TYPES projectile, String key) {
		if (!DEFINITIONS.containsKey(key)) {
			throw new IllegalStateException("Unknown projectile animation fallback key: " + key);
		}
		if (fallbacks.put(projectile.id(), key) != null) {
			throw new IllegalStateException("Duplicate projectile animation fallback: " + projectile.name());
		}
	}

	private static Segment segment(String sheetPath, int columns, int rows, int firstFrame, int frameCount) {
		if (columns <= 0 || rows <= 0 || firstFrame < 0 || frameCount <= 0
			|| firstFrame + frameCount > columns * rows) {
			throw new IllegalArgumentException("Invalid projectile phase: " + sheetPath);
		}
		return new Segment(sheetPath, columns, rows, firstFrame, frameCount);
	}

	private static void phases(Map<String, Segment[]> phases, String key, Segment... segments) {
		if (!DEFINITIONS.containsKey(key) || segments == null || segments.length == 0) {
			throw new IllegalArgumentException("Invalid projectile phase key: " + key);
		}
		if (phases.put(key, segments.clone()) != null) {
			throw new IllegalStateException("Duplicate projectile phases: " + key);
		}
	}

	private static void impactSize(Map<String, Integer> sizes, String key, int screenSize) {
		if (!DEFINITIONS.containsKey(key) || screenSize <= 0) {
			throw new IllegalArgumentException("Invalid projectile impact size: " + key);
		}
		if (sizes.put(key, screenSize) != null) {
			throw new IllegalStateException("Duplicate projectile impact size: " + key);
		}
	}

	public static Definition getDefinition(String key) {
		return DEFINITIONS.get(key);
	}

	public static Definition getProjectileFallback(int projectileId) {
		return getDefinition(PROJECTILE_FALLBACKS.get(projectileId));
	}

	public static Segment[] getStartupSegments(String key) {
		Segment[] segments = STARTUP_SEGMENTS.get(key);
		return segments == null ? new Segment[0] : segments.clone();
	}

	public static Segment[] getImpactSegments(String key) {
		Segment[] segments = IMPACT_SEGMENTS.get(key);
		return segments == null ? new Segment[0] : segments.clone();
	}

	public static int getImpactScreenSize(String key) {
		Integer screenSize = IMPACT_SCREEN_SIZES.get(key);
		return screenSize == null ? 64 : screenSize.intValue();
	}

	public static Map<String, Definition> getDefinitions() {
		return DEFINITIONS;
	}

	public static Map<Integer, String> getProjectileFallbacks() {
		return PROJECTILE_FALLBACKS;
	}
}
