package orsc.graphics.two;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Spritesheet-backed on-entity spell effects keyed by stable combat-effect ID. */
public final class CombatEffectAnimationCatalog {

	public static final String ON_ENTITY = "on-entity";
	public static final String PROJECTILE_MOVING = "projectile-moving";

	public static final class Definition {
		private final String key;
		private final String category;
		private final String sheetPath;
		private final int columns;
		private final int rows;
		private final int firstFrame;
		private final int frameCount;
		private final int maxTargetSize;

		private Definition(String key, String category, String sheetPath, int columns, int rows,
				int firstFrame, int frameCount, int maxTargetSize) {
			this.key = key;
			this.category = category;
			this.sheetPath = sheetPath;
			this.columns = columns;
			this.rows = rows;
			this.firstFrame = firstFrame;
			this.frameCount = frameCount;
			this.maxTargetSize = maxTargetSize;
		}

		public String getKey() { return key; }
		public String getCategory() { return category; }
		public String getSheetPath() { return sheetPath; }
		public int getColumns() { return columns; }
		public int getRows() { return rows; }
		public int getFirstFrame() { return firstFrame; }
		public int getFrameCount() { return frameCount; }
		public int getMaxTargetSize() { return maxTargetSize; }
	}

	private static final Map<Integer, Definition> DEFINITIONS;
	private static final Map<Integer, Definition[]> SEQUENCES;

	static {
		LinkedHashMap<Integer, Definition> definitions = new LinkedHashMap<Integer, Definition>();
		define(definitions, 1, "explosion-vfx-15", ON_ENTITY,
			"explosions/Explosion VFX 15(32x32).png", 13, 1, 0, 13, 64);
		define(definitions, 2, "explosion-vfx-17", ON_ENTITY,
			"explosions/Explosion VFX 17(48x64).png", 11, 1, 0, 11, 64);
		define(definitions, 6, "fire-2", ON_ENTITY, "fire-2/Fire Claw.png", 9, 1, 0, 9, 64);
		define(definitions, 8, "earth-2", ON_ENTITY, "earth-2/Earth Hammer (48x48).png", 5, 5, 0, 21, 64);
		define(definitions, 7, "wind-4", ON_ENTITY, "wind-4/Wind Beam.png", 14, 1, 0, 14, 64);
		define(definitions, 9, "water-3", ON_ENTITY,
			"water-3/Water Splash 01 - Spritesheet.png", 5, 4, 0, 20, 64);
		define(definitions, 10, "explosion-vfx-3", ON_ENTITY,
			"explosions/Explosion VFX 3(48x48).png", 13, 1, 0, 13, 64);
		define(definitions, 12, "earth-4", ON_ENTITY,
			"earth-4/Earth Impale 64x64.png", 17, 1, 0, 17, 64);
		define(definitions, 14, "fire-4", ON_ENTITY, "fire-4/Fire Beam.png", 10, 1, 0, 10, 64);
		define(definitions, 15, "explosion-vfx-11", ON_ENTITY,
			"explosions/Explosion VFX 11(64x32).png", 16, 1, 0, 16, 64);
		define(definitions, 16, "lesser-heal", ON_ENTITY,
			"lesser-heal/Buff n Debuff P1 03.png", 16, 1, 0, 16, 64);
		define(definitions, 17, "greater-heal", ON_ENTITY,
			"greater-heal/Buff n Debuff P07 04.png", 19, 1, 0, 19, 64);
		define(definitions, 18, "holy-vfx-09", ON_ENTITY,
			"Holy VFX 09/Holy Effect 09(16x16).png", 12, 1, 0, 12, 32);
		define(definitions, 19, "dark-11", ON_ENTITY,
			"dark-11/Dark VFX 11 (32x48).png", 14, 1, 0, 14, 64);
		define(definitions, 22, "dark-10", ON_ENTITY,
			"dark-10/Dark VFX10 (48x48).png", 26, 1, 0, 26, 64);
		define(definitions, 23, "dark-4", ON_ENTITY,
			"dark-4/Dark VFX 4 (48x56).png", 31, 1, 0, 31, 64);
		define(definitions, 24, "dark-12", ON_ENTITY,
			"dark-12/Dark VFX 12 (48x48).png", 13, 1, 0, 13, 64);
		define(definitions, 25, "dark-6-diagonal", ON_ENTITY,
			"dark-6/Dark VFX 6 Diagonal (48x64).png", 16, 1, 0, 16, 64);
		define(definitions, 30, "thunder-2-hit", PROJECTILE_MOVING,
			"thunder-2/Hit/Thunder hit wo blur.png", 6, 1, 0, 6, 64);
		define(definitions, 31, "ice-2", ON_ENTITY, "ice-2/Ice VFX 3(48x48).png", 22, 1, 0, 20, 64);
		define(definitions, 32, "acid-2", ON_ENTITY, "acid-2/Acid VFX 09(72x80).png", 23, 1, 0, 23, 64);
		define(definitions, 33, "wood-2", ON_ENTITY, "wood-2/Wood VFX 04(32x48).png", 16, 1, 0, 15, 64);
		define(definitions, 34, "thunder-3", ON_ENTITY,
			"thunder-3/Thunderstrike wo blur.png", 13, 1, 0, 13, 64);
		define(definitions, 35, "ice-3", ON_ENTITY,
			"ice-3/Ice VFX 2(64x64).png", 34, 1, 0, 34, 64);
		define(definitions, 36, "acid-3", ON_ENTITY,
			"acid-3/Acid VFX 04(48x48).png", 23, 1, 0, 23, 64);
		define(definitions, 37, "wood-3", ON_ENTITY,
			"wood-3/Wood VFX 08(56x56).png", 14, 1, 0, 14, 64);
		define(definitions, 42, "explosion-vfx-15", ON_ENTITY,
			"explosions/Explosion VFX 15(32x32).png", 13, 1, 0, 13, 64);
		define(definitions, 45, "explosion-vfx-17", ON_ENTITY,
			"explosions/Explosion VFX 17(48x64).png", 11, 1, 0, 11, 64);
		define(definitions, 63, "burn", ON_ENTITY,
			"burn/Buff n Debuff P07 03.png", 16, 1, 0, 16, 64);
		define(definitions, 58, "fire-slash-1", ON_ENTITY,
			"fire-slashes/Slash 1.png", 13, 1, 0, 13, 64);
		define(definitions, 59, "fire-hit-3", ON_ENTITY,
			"Fire combo/Fire Hit 3.png", 17, 1, 0, 17, 64);
		define(definitions, 61, "wood-6", ON_ENTITY,
			"wood-6/Wood VFX 05(48x48).png", 17, 1, 0, 17, 64);
		define(definitions, 64, "bubble-shield", ON_ENTITY,
			"bubble-shield/true-defense.png", 22, 1, 0, 22, 64);
		define(definitions, 65, "teleport", ON_ENTITY,
			"teleport/Buff n Debuff P1 04.png", 24, 1, 0, 24, 64);
		define(definitions, 66, "fire-slash-2", ON_ENTITY,
			"fire-slashes/Slash 2.png", 12, 1, 0, 12, 64);
		define(definitions, 67, "cleric-mend", ON_ENTITY,
			"heal-alt/heal-alt.png", 27, 1, 0, 27, 48);
		define(definitions, 68, "cleric-fervor", ON_ENTITY,
			"fist/fist.png", 14, 1, 0, 14, 48);
		define(definitions, 69, "cleric-purify", ON_ENTITY,
			"cleanse 2/cleanse 2.png", 17, 1, 0, 17, 48);
		define(definitions, 70, "cleric-restore", ON_ENTITY,
			"cleanse/cleanse.png", 17, 1, 0, 17, 48);
		define(definitions, 71, "cleric-ward", ON_ENTITY,
			"holy-shield/holy-shield.png", 17, 1, 0, 17, 48);
		define(definitions, 72, "cleric-greater-mend", ON_ENTITY,
			"greater-heal-alt/greater-heal-alt.png", 19, 1, 0, 19, 48);
		define(definitions, 73, "cleric-zeal", ON_ENTITY,
			"Holy VFX 03/Holy VFX 03(64x80).png", 25, 1, 0, 25, 48);
		define(definitions, 74, "cleric-thorns", ON_ENTITY,
			"thorns/thorns.png", 21, 1, 0, 21, 32);
		define(definitions, 75, "cleric-aegis", ON_ENTITY,
			"Holy VFX 04/Holy VFX 04(48x48).png", 17, 1, 0, 17, 48);
		define(definitions, 76, "cleric-rally", ON_ENTITY,
			"sword-clash/sword-clash.png", 11, 1, 0, 11, 48);
		define(definitions, 77, "cleric-respite", ON_ENTITY,
			"heart-pop/heart-pop.png", 18, 1, 0, 18, 48);

		LinkedHashMap<Integer, Definition[]> sequences = new LinkedHashMap<Integer, Definition[]>();
		for (Map.Entry<Integer, Definition> entry : definitions.entrySet()) {
			sequences.put(entry.getKey(), new Definition[] {entry.getValue()});
		}
		defineSequence(definitions, sequences, 4,
			definition("earth-3", ON_ENTITY, "earth-3/Earth Burst (64x48).png", 13, 3, 0, 7, 64),
			definition("earth-3", ON_ENTITY, "earth-3/Earth Burst (64x48).png", 13, 3, 13, 10, 64),
			definition("earth-3", ON_ENTITY, "earth-3/Earth Burst (64x48).png", 13, 3, 26, 10, 64));
		defineSequence(definitions, sequences, 11,
			definition("wind-3", ON_ENTITY, "wind-3/Tornado.png", 6, 3, 0, 5, 64),
			definition("wind-3", ON_ENTITY, "wind-3/Tornado.png", 6, 3, 6, 6, 64),
			definition("wind-3", ON_ENTITY, "wind-3/Tornado.png", 6, 3, 12, 6, 64));
		defineSequence(definitions, sequences, 13,
			definition("water-4-start", ON_ENTITY, "water-4/Water Blast - Start.png", 4, 3, 0, 12, 64),
			definition("water-4-end", ON_ENTITY, "water-4/Water Blast - End.png", 3, 3, 0, 9, 64));
		defineSequence(definitions, sequences, 20,
			definition("dark-7-repeatable", ON_ENTITY,
				"dark-7/Dark VFX 7 Repeatable (48x48).png", 12, 1, 0, 12, 64),
			definition("dark-7-ending", ON_ENTITY,
				"dark-7/Dark VFX 7 Ending (48x48).png", 17, 1, 0, 17, 64));
		defineSequence(definitions, sequences, 21,
			definition("thunder-explosion-start", ON_ENTITY,
				"thunder-explosion/Start Explosion wo blur.png", 13, 1, 0, 13, 64),
			definition("thunder-explosion", ON_ENTITY,
				"thunder-explosion/Explosion wo blur.png", 14, 1, 0, 14, 64));
		DEFINITIONS = Collections.unmodifiableMap(definitions);
		SEQUENCES = Collections.unmodifiableMap(sequences);
	}

	private CombatEffectAnimationCatalog() {
	}

	private static void define(Map<Integer, Definition> definitions, int effectType, String key,
			String category, String sheetPath, int columns, int rows, int firstFrame,
			int frameCount, int maxTargetSize) {
		if (definitions.containsKey(effectType)) {
			throw new IllegalStateException("Duplicate combat effect animation: " + effectType);
		}
		if (columns <= 0 || rows <= 0 || firstFrame < 0 || frameCount <= 0
			|| firstFrame + frameCount > columns * rows || maxTargetSize <= 0) {
			throw new IllegalArgumentException("Invalid combat effect animation: " + key);
		}
		definitions.put(effectType, new Definition(key, category, sheetPath, columns, rows,
			firstFrame, frameCount, maxTargetSize));
	}

	private static Definition definition(String key, String category, String sheetPath,
			int columns, int rows, int firstFrame, int frameCount, int maxTargetSize) {
		if (columns <= 0 || rows <= 0 || firstFrame < 0 || frameCount <= 0
			|| firstFrame + frameCount > columns * rows || maxTargetSize <= 0) {
			throw new IllegalArgumentException("Invalid combat effect animation segment: " + key);
		}
		return new Definition(key, category, sheetPath, columns, rows, firstFrame,
			frameCount, maxTargetSize);
	}

	private static void defineSequence(Map<Integer, Definition> definitions,
			Map<Integer, Definition[]> sequences, int effectType, Definition... sequence) {
		if (definitions.containsKey(effectType) || sequence == null || sequence.length == 0) {
			throw new IllegalStateException("Invalid combat effect animation sequence: " + effectType);
		}
		definitions.put(effectType, sequence[0]);
		sequences.put(effectType, sequence.clone());
	}

	public static Definition getDefinition(int effectType) {
		return DEFINITIONS.get(effectType);
	}

	public static Definition[] getSequence(int effectType) {
		Definition[] sequence = SEQUENCES.get(effectType);
		return sequence == null ? null : sequence.clone();
	}

	public static Map<Integer, Definition> getDefinitions() {
		return DEFINITIONS;
	}
}
