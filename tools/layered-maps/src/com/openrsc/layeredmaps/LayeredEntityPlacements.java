package com.openrsc.layeredmaps;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict read-only decoder for package-owned layered world placements. */
public final class LayeredEntityPlacements {
	public static final String ENCODING_V1 = "layered-entity-placements-v1";
	public static final String ENCODING_V2 = "layered-world-placements-v2";
	public static final String ENCODING = ENCODING_V2;
	private static final int MAX_PLACEMENTS = 65536;
	private static final int MAX_NPC_ROAM_RADIUS = 64;
	private static final int MAX_RESPAWN_SECONDS = 86400;
	private static final Pattern ID =
		Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");

	private final String worldSpace;
	private final int level;
	private final String encoding;
	private final List<NpcPlacement> npcs;
	private final List<GroundItemPlacement> groundItems;
	private final List<SceneryPlacement> scenery;
	private final List<BoundaryPlacement> boundaries;

	private LayeredEntityPlacements(
		String worldSpace,
		int level,
		String encoding,
		List<NpcPlacement> npcs,
		List<GroundItemPlacement> groundItems,
		List<SceneryPlacement> scenery,
		List<BoundaryPlacement> boundaries) {
		this.worldSpace = worldSpace;
		this.level = level;
		this.encoding = encoding;
		this.npcs = Collections.unmodifiableList(
			new ArrayList<NpcPlacement>(npcs));
		this.groundItems = Collections.unmodifiableList(
			new ArrayList<GroundItemPlacement>(groundItems));
		this.scenery = Collections.unmodifiableList(
			new ArrayList<SceneryPlacement>(scenery));
		this.boundaries = Collections.unmodifiableList(
			new ArrayList<BoundaryPlacement>(boundaries));
	}

	public static LayeredEntityPlacements load(Path path)
		throws IOException, PreflightException {
		Map<String, Object> document = JsonDocuments.readObject(path);
		int schemaVersion = integer(document, "schemaVersion");
		String encoding = string(document, "encoding");
		boolean version1 =
			schemaVersion == 1 && ENCODING_V1.equals(encoding);
		boolean version2 =
			schemaVersion == 2 && ENCODING_V2.equals(encoding);
		if (!version1 && !version2) {
			throw new PreflightException(
				"Placement schemaVersion/encoding pair is unsupported.");
		}
		if (version1) {
			exactKeys(
				document,
				"entity placement set",
				"schemaVersion",
				"encoding",
				"worldSpace",
				"level",
				"npcs",
				"groundItems");
		} else {
			exactKeys(
				document,
				"world placement set",
				"schemaVersion",
				"encoding",
				"worldSpace",
				"level",
				"npcs",
				"groundItems",
				"scenery",
				"boundaries");
		}
		String worldSpace = matchedString(document, "worldSpace", ID);
		int level = integer(document, "level");
		List<Object> npcValues = array(document, "npcs");
		List<Object> itemValues = array(document, "groundItems");
		List<Object> sceneryValues = version2
			? array(document, "scenery") : Collections.<Object>emptyList();
		List<Object> boundaryValues = version2
			? array(document, "boundaries") : Collections.<Object>emptyList();
		int placementCount = Math.addExact(
			Math.addExact(npcValues.size(), itemValues.size()),
			Math.addExact(sceneryValues.size(), boundaryValues.size()));
		if (placementCount < 1
			|| npcValues.size() > MAX_PLACEMENTS
			|| itemValues.size() > MAX_PLACEMENTS
			|| sceneryValues.size() > MAX_PLACEMENTS
			|| boundaryValues.size() > MAX_PLACEMENTS
			|| placementCount > MAX_PLACEMENTS) {
			throw new PreflightException(
				"World placement set count must be 1.." + MAX_PLACEMENTS + ".");
		}

		Set<String> placementIds = new HashSet<String>();
		List<NpcPlacement> npcs = new ArrayList<NpcPlacement>();
		for (int index = 0; index < npcValues.size(); index++) {
			Map<String, Object> value =
				object(npcValues.get(index), "npcs[" + index + "]");
			exactKeys(
				value,
				"npcs[" + index + "]",
				"placementId",
				"npcId",
				"start",
				"roamRadius");
			String placementId = uniquePlacementId(
				value, "npcs[" + index + "]", placementIds);
			int roamRadius = nonNegativeInt(value, "roamRadius");
			if (roamRadius > MAX_NPC_ROAM_RADIUS) {
				throw new PreflightException(
					"npcs[" + index + "].roamRadius must be 0.."
						+ MAX_NPC_ROAM_RADIUS + ".");
			}
			Position start = position(
				object(value.get("start"), "npcs[" + index + "].start"),
				"npcs[" + index + "].start");
			npcs.add(new NpcPlacement(
				placementId,
				nonNegativeInt(value, "npcId"),
				start.x,
				start.y,
				roamRadius));
		}

		List<GroundItemPlacement> groundItems =
			new ArrayList<GroundItemPlacement>();
		for (int index = 0; index < itemValues.size(); index++) {
			Map<String, Object> value =
				object(itemValues.get(index), "groundItems[" + index + "]");
			exactKeys(
				value,
				"groundItems[" + index + "]",
				"placementId",
				"itemId",
				"position",
				"amount",
				"respawnSeconds");
			String placementId = uniquePlacementId(
				value, "groundItems[" + index + "]", placementIds);
			int respawnSeconds = positiveInt(value, "respawnSeconds");
			if (respawnSeconds > MAX_RESPAWN_SECONDS) {
				throw new PreflightException(
					"groundItems[" + index + "].respawnSeconds must be 1.."
						+ MAX_RESPAWN_SECONDS + ".");
			}
			Position location = position(
				object(
					value.get("position"),
					"groundItems[" + index + "].position"),
				"groundItems[" + index + "].position");
			groundItems.add(new GroundItemPlacement(
				placementId,
				nonNegativeInt(value, "itemId"),
				location.x,
				location.y,
				positiveInt(value, "amount"),
				respawnSeconds));
		}
		List<SceneryPlacement> scenery =
			new ArrayList<SceneryPlacement>();
		Set<String> scenerySlots = new HashSet<String>();
		for (int index = 0; index < sceneryValues.size(); index++) {
			Map<String, Object> value =
				object(sceneryValues.get(index), "scenery[" + index + "]");
			exactKeys(
				value,
				"scenery[" + index + "]",
				"placementId",
				"sceneryId",
				"position",
				"direction");
			String placementId = uniquePlacementId(
				value, "scenery[" + index + "]", placementIds);
			Position location = position(
				object(value.get("position"),
					"scenery[" + index + "].position"),
				"scenery[" + index + "].position");
			int direction = direction(value, "direction");
			String slot = location.x + ":" + location.y;
			if (!scenerySlots.add(slot)) {
				throw new PreflightException(
					"Duplicate scenery slot at " + slot + ".");
			}
			scenery.add(new SceneryPlacement(
				placementId,
				nonNegativeInt(value, "sceneryId"),
				location.x,
				location.y,
				direction));
		}
		List<BoundaryPlacement> boundaries =
			new ArrayList<BoundaryPlacement>();
		Set<String> boundarySlots = new HashSet<String>();
		for (int index = 0; index < boundaryValues.size(); index++) {
			Map<String, Object> value =
				object(boundaryValues.get(index), "boundaries[" + index + "]");
			exactKeys(
				value,
				"boundaries[" + index + "]",
				"placementId",
				"boundaryId",
				"position",
				"direction");
			String placementId = uniquePlacementId(
				value, "boundaries[" + index + "]", placementIds);
			Position location = position(
				object(value.get("position"),
					"boundaries[" + index + "].position"),
				"boundaries[" + index + "].position");
			int direction = direction(value, "direction");
			String slot =
				location.x + ":" + location.y + ":" + direction;
			if (!boundarySlots.add(slot)) {
				throw new PreflightException(
					"Duplicate boundary slot at " + slot + ".");
			}
			boundaries.add(new BoundaryPlacement(
				placementId,
				nonNegativeInt(value, "boundaryId"),
				location.x,
				location.y,
				direction));
		}
		return new LayeredEntityPlacements(
			worldSpace, level, encoding,
			npcs, groundItems, scenery, boundaries);
	}

	public String getWorldSpace() {
		return worldSpace;
	}

	public int getLevel() {
		return level;
	}

	public String getEncoding() {
		return encoding;
	}

	public List<NpcPlacement> getNpcs() {
		return npcs;
	}

	public List<GroundItemPlacement> getGroundItems() {
		return groundItems;
	}

	public List<SceneryPlacement> getScenery() {
		return scenery;
	}

	public List<BoundaryPlacement> getBoundaries() {
		return boundaries;
	}

	private static Position position(Map<String, Object> value, String label)
		throws PreflightException {
		exactKeys(value, label, "x", "y");
		return new Position(integer(value, "x"), integer(value, "y"));
	}

	private static String uniquePlacementId(
		Map<String, Object> value,
		String label,
		Set<String> placementIds) throws PreflightException {
		String result = matchedString(value, "placementId", ID);
		if (!placementIds.add(result)) {
			throw new PreflightException(
				"Duplicate placement ID at " + label + ": " + result + ".");
		}
		return result;
	}

	private static Map<String, Object> object(Object value, String label)
		throws PreflightException {
		if (!(value instanceof Map)) {
			throw new PreflightException(label + " must be an object.");
		}
		return JsonDocuments.object(value);
	}

	private static List<Object> array(Map<String, Object> parent, String key)
		throws PreflightException {
		Object value = parent.get(key);
		if (!(value instanceof List)) {
			throw new PreflightException(key + " must be an array.");
		}
		return JsonDocuments.array(value);
	}

	private static void exactKeys(
		Map<String, Object> value, String label, String... keys)
		throws PreflightException {
		Set<String> expected = new HashSet<String>(Arrays.asList(keys));
		if (!value.keySet().equals(expected)) {
			throw new PreflightException(
				label + " fields differ from the v1 contract.");
		}
	}

	private static void requireInt(
		Map<String, Object> value, String key, int expected)
		throws PreflightException {
		int actual = integer(value, key);
		if (actual != expected) {
			throw new PreflightException(
				key + " must be " + expected + " but was " + actual + ".");
		}
	}

	private static int integer(Map<String, Object> value, String key)
		throws PreflightException {
		Object raw = value.get(key);
		if (!(raw instanceof Long)
			|| (Long) raw < Integer.MIN_VALUE
			|| (Long) raw > Integer.MAX_VALUE) {
			throw new PreflightException(
				key + " must be a signed 32-bit integer.");
		}
		return ((Long) raw).intValue();
	}

	private static int nonNegativeInt(
		Map<String, Object> value, String key) throws PreflightException {
		int result = integer(value, key);
		if (result < 0) {
			throw new PreflightException(key + " must be non-negative.");
		}
		return result;
	}

	private static int positiveInt(
		Map<String, Object> value, String key) throws PreflightException {
		int result = integer(value, key);
		if (result <= 0) {
			throw new PreflightException(key + " must be positive.");
		}
		return result;
	}

	private static int direction(
		Map<String, Object> value, String key) throws PreflightException {
		int result = integer(value, key);
		if (result < 0 || result > 7) {
			throw new PreflightException(key + " must be 0..7.");
		}
		return result;
	}

	private static void requireString(
		Map<String, Object> value, String key, String expected)
		throws PreflightException {
		String actual = string(value, key);
		if (!expected.equals(actual)) {
			throw new PreflightException(
				key + " must be " + expected + " but was " + actual + ".");
		}
	}

	private static String string(Map<String, Object> value, String key)
		throws PreflightException {
		Object raw = value.get(key);
		if (!(raw instanceof String)) {
			throw new PreflightException(key + " must be a string.");
		}
		return (String) raw;
	}

	private static String matchedString(
		Map<String, Object> value, String key, Pattern pattern)
		throws PreflightException {
		String result = string(value, key);
		if (!pattern.matcher(result).matches()) {
			throw new PreflightException(
				key + " must match " + pattern.pattern() + ": " + result + ".");
		}
		return result;
	}

	private static final class Position {
		final int x;
		final int y;

		Position(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}

	public static final class NpcPlacement {
		private final String placementId;
		private final int npcId;
		private final int x;
		private final int y;
		private final int roamRadius;

		NpcPlacement(
			String placementId, int npcId, int x, int y, int roamRadius) {
			this.placementId = placementId;
			this.npcId = npcId;
			this.x = x;
			this.y = y;
			this.roamRadius = roamRadius;
		}

		public String getPlacementId() { return placementId; }
		public int getNpcId() { return npcId; }
		public int getX() { return x; }
		public int getY() { return y; }
		public int getRoamRadius() { return roamRadius; }
	}

	public static final class GroundItemPlacement {
		private final String placementId;
		private final int itemId;
		private final int x;
		private final int y;
		private final int amount;
		private final int respawnSeconds;

		GroundItemPlacement(
			String placementId,
			int itemId,
			int x,
			int y,
			int amount,
			int respawnSeconds) {
			this.placementId = placementId;
			this.itemId = itemId;
			this.x = x;
			this.y = y;
			this.amount = amount;
			this.respawnSeconds = respawnSeconds;
		}

		public String getPlacementId() { return placementId; }
		public int getItemId() { return itemId; }
		public int getX() { return x; }
		public int getY() { return y; }
		public int getAmount() { return amount; }
		public int getRespawnSeconds() { return respawnSeconds; }
	}

	public static final class SceneryPlacement {
		private final String placementId;
		private final int sceneryId;
		private final int x;
		private final int y;
		private final int direction;

		SceneryPlacement(
			String placementId,
			int sceneryId,
			int x,
			int y,
			int direction) {
			this.placementId = placementId;
			this.sceneryId = sceneryId;
			this.x = x;
			this.y = y;
			this.direction = direction;
		}

		public String getPlacementId() { return placementId; }
		public int getSceneryId() { return sceneryId; }
		public int getX() { return x; }
		public int getY() { return y; }
		public int getDirection() { return direction; }
	}

	public static final class BoundaryPlacement {
		private final String placementId;
		private final int boundaryId;
		private final int x;
		private final int y;
		private final int direction;

		BoundaryPlacement(
			String placementId,
			int boundaryId,
			int x,
			int y,
			int direction) {
			this.placementId = placementId;
			this.boundaryId = boundaryId;
			this.x = x;
			this.y = y;
			this.direction = direction;
		}

		public String getPlacementId() { return placementId; }
		public int getBoundaryId() { return boundaryId; }
		public int getX() { return x; }
		public int getY() { return y; }
		public int getDirection() { return direction; }
	}
}
