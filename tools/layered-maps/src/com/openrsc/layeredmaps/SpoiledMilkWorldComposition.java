package com.openrsc.layeredmaps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reproduces the effective authored placement composition selected by the
 * repository's Spoiled Milk development configuration.
 *
 * <p>This is intentionally separate from the frozen Preservation baseline.
 * It applies the same active source ordering, feature gates, MyWorld cleanup,
 * same-slot supersession, and harvesting reclassification used by
 * {@code WorldPopulator} before native layered replacement takes ownership.</p>
 */
final class SpoiledMilkWorldComposition {
	static final String CONFIG_PATH = "server/myworld.conf";
	static final int EXPECTED_RAW_INPUT_COUNT = 33639;
	static final int EXPECTED_NPC_COUNT = 3790;
	static final int EXPECTED_GROUND_ITEM_COUNT = 879;
	static final int EXPECTED_SCENERY_COUNT = 27887;
	static final int EXPECTED_BOUNDARY_COUNT = 971;
	static final int EXPECTED_HARVESTING_RECLASSIFICATIONS = 143;

	private static final Pattern CONFIG_VALUE = Pattern.compile(
		"^\\s*([A-Za-z0-9_]+)\\s*:\\s*([^#]*?)\\s*(?:#.*)?$");
	private static final String LOCS =
		"server/conf/server/defs/locs/";
	private static final Set<Integer> BANKER_IDS =
		Collections.unmodifiableSet(new HashSet<Integer>(Arrays.asList(
			Integer.valueOf(95),
			Integer.valueOf(224),
			Integer.valueOf(268),
			Integer.valueOf(540),
			Integer.valueOf(617))));
	private static final Set<Integer> CHRISTMAS_PARTY_IDS =
		Collections.unmodifiableSet(new HashSet<Integer>(Arrays.asList(
			Integer.valueOf(28),
			Integer.valueOf(198),
			Integer.valueOf(715),
			Integer.valueOf(812))));
	private static final Set<Integer> CHRISTMAS_SPIRIT_IDS =
		Collections.unmodifiableSet(new HashSet<Integer>(Arrays.asList(
			Integer.valueOf(830),
			Integer.valueOf(831),
			Integer.valueOf(832))));
	private static final Map<Integer, Integer> HARVESTING_SCENERY =
		harvestingScenery();

	Result inspect(Path requestedRoot)
		throws IOException, PreflightException {
		Path root = requestedRoot.toAbsolutePath().normalize().toRealPath();
		Path configPath = requiredFile(root, CONFIG_PATH);
		Map<String, String> config = readConfig(configPath);
		requireConfig(config);

		List<SourceFile> sources = new ArrayList<SourceFile>();
		List<Record> boundaries = records(
			root,
			sources,
			"base-boundaries",
			LOCS + "BoundaryLocs.json",
			"boundaries");
		boundaries.addAll(records(
			root,
			sources,
			"custom-quest-boundaries",
			LOCS + "BoundaryLocsCustomQuest.json",
			"boundaries"));

		List<Record> scenery = new ArrayList<Record>();
		for (String[] source : new String[][] {
			{"base-scenery", "SceneryLocs.json"},
			{"discontinued-scenery", "SceneryLocsDiscontinued.json"},
			{"decorated-mod-room-scenery", "SceneryLocsModRoom.json"},
			{"runecraft-scenery", "SceneryLocsRunecraft.json"},
			{"harvesting-scenery", "SceneryLocsHarvesting.json"},
			{"custom-quest-scenery", "SceneryLocsCustomQuest.json"},
			{"expansion-scenery", "SceneryLocsExpansion.json"},
			{"woodcutting-guild-scenery", "SceneryLocsWoodcuttingGuild.json"},
			{"other-scenery", "SceneryLocsOther.json"}
		}) {
			scenery.addAll(records(
				root,
				sources,
				source[0],
				LOCS + source[1],
				"sceneries"));
		}

		List<Record> npcs = new ArrayList<Record>();
		for (String[] source : new String[][] {
			{"base-npcs", "NpcLocs.json"},
			{"discontinued-npcs", "NpcLocsDiscontinued.json"},
			{"decorated-mod-room-npcs", "NpcLocsModRoom.json"},
			{"runecraft-npcs", "NpcLocsRunecraft.json"},
			{"auction-npcs", "NpcLocsAuction.json"},
			{"harvesting-npcs", "NpcLocsHarvesting.json"},
			{"custom-quest-npcs", "NpcLocsCustomQuest.json"},
			{"other-npcs", "NpcLocsOther.json"},
			{"myworld-npcs", "MyWorldNpcLocs.json"}
		}) {
			npcs.addAll(records(
				root,
				sources,
				source[0],
				LOCS + source[1],
				"npclocs"));
		}

		List<Record> groundItems = new ArrayList<Record>();
		for (String[] source : new String[][] {
			{"base-ground-items", "GroundItems.json"},
			{"harvesting-ground-items", "GroundItemsHarvesting.json"},
			{"custom-quest-ground-items", "GroundItemsCustomQuest.json"}
		}) {
			groundItems.addAll(records(
				root,
				sources,
				source[0],
				LOCS + source[1],
				"grounditems"));
		}
		List<Record> myWorldScenery = records(
			root,
			sources,
			"myworld-scenery",
			LOCS + "MyWorldSceneryLocs.json",
			"sceneries");

		int rawInputCount = Math.addExact(
			Math.addExact(
				boundaries.size(),
				Math.addExact(scenery.size(), myWorldScenery.size())),
			Math.addExact(npcs.size(), groundItems.size()));
		if (rawInputCount != EXPECTED_RAW_INPUT_COUNT) {
			throw new PreflightException(
				"Spoiled Milk active placement inputs changed: expected "
					+ EXPECTED_RAW_INPUT_COUNT + " records but found "
					+ rawInputCount + ".");
		}

		List<Record> sceneryRemovals = records(
			root,
			sources,
			"myworld-scenery-removals",
			LOCS + "MyWorldSceneryRemovals.json",
			"scenery_removals");
		Set<String> removedScenerySlots = new HashSet<String>();
		for (Record removal : sceneryRemovals) {
			removedScenerySlots.add(positionSlot(removal.value, "pos"));
		}
		int sceneryBeforeRemoval = scenery.size();
		List<Record> retainedScenery = new ArrayList<Record>();
		for (Record record : scenery) {
			if (!removedScenerySlots.contains(
					positionSlot(record.value, "pos"))) {
				retainedScenery.add(record);
			}
		}
		int appliedSceneryRemovalCount =
			sceneryBeforeRemoval - retainedScenery.size();
		retainedScenery.addAll(myWorldScenery);
		CollapseResult effectiveScenery = collapse(
			retainedScenery, false);
		CollapseResult effectiveBoundaries = collapse(boundaries, true);

		GroundComposition ground =
			composeGroundItems(groundItems);
		int harvestingScenerySupersessions = 0;
		LinkedHashMap<String, Record> sceneryBySlot =
			new LinkedHashMap<String, Record>(effectiveScenery.bySlot);
		for (Record record : ground.harvestingScenery) {
			String slot = positionSlot(record.value, "pos");
			if (sceneryBySlot.remove(slot) != null) {
				harvestingScenerySupersessions++;
			}
			sceneryBySlot.put(slot, record);
		}

		NpcComposition effectiveNpcs =
			composeNpcs(npcs, records(
				root,
				sources,
				"myworld-npc-removals",
				LOCS + "MyWorldNpcRemovals.json",
				"npc_removals"));

		List<Record> finalScenery =
			new ArrayList<Record>(sceneryBySlot.values());
		List<Record> finalBoundaries =
			new ArrayList<Record>(effectiveBoundaries.bySlot.values());
		requireCount("NPC", effectiveNpcs.records.size(), EXPECTED_NPC_COUNT);
		requireCount(
			"ground-item",
			ground.groundItems.size(),
			EXPECTED_GROUND_ITEM_COUNT);
		requireCount("scenery", finalScenery.size(), EXPECTED_SCENERY_COUNT);
		requireCount(
			"boundary",
			finalBoundaries.size(),
			EXPECTED_BOUNDARY_COUNT);
		requireCount(
			"harvesting reclassification",
			ground.harvestingScenery.size(),
			EXPECTED_HARVESTING_RECLASSIFICATIONS);

		Map<String, Integer> rawCounts = counts(
			npcs.size(),
			groundItems.size(),
			Math.addExact(sceneryBeforeRemoval, myWorldScenery.size()),
			boundaries.size());
		Map<String, Integer> effectiveCounts = counts(
			effectiveNpcs.records.size(),
			ground.groundItems.size(),
			finalScenery.size(),
			finalBoundaries.size());
		Map<String, Integer> transformations =
			new LinkedHashMap<String, Integer>();
		transformations.put(
			"myWorldSceneryRemovalsApplied",
			Integer.valueOf(appliedSceneryRemovalCount));
		transformations.put(
			"scenerySameTileSupersessions",
			Integer.valueOf(effectiveScenery.supersededCount));
		transformations.put(
			"boundarySameSlotSupersessions",
			Integer.valueOf(effectiveBoundaries.supersededCount));
		transformations.put(
			"groundItemSameTileSupersessions",
			Integer.valueOf(ground.supersededGroundItems));
		transformations.put(
			"harvestingGroundItemsReclassified",
			Integer.valueOf(ground.harvestingScenery.size()));
		transformations.put(
			"harvestingScenerySupersessions",
			Integer.valueOf(harvestingScenerySupersessions));
		transformations.put(
			"eventPolicyNpcRemovals",
			Integer.valueOf(effectiveNpcs.eventPolicyRemovals));
		transformations.put(
			"tutorialIslandNpcRemovals",
			Integer.valueOf(effectiveNpcs.tutorialRemovals));
		transformations.put(
			"bankerClusterNpcRemovals",
			Integer.valueOf(effectiveNpcs.bankerRemovals));
		transformations.put(
			"myWorldNpcRemovalsApplied",
			Integer.valueOf(effectiveNpcs.explicitRemovals));

		return new Result(
			configPath,
			Hashes.sha256(configPath),
			sources,
			rawInputCount,
			rawCounts,
			effectiveCounts,
			transformations,
			effectiveNpcs.records,
			ground.groundItems,
			finalScenery,
			finalBoundaries);
	}

	private static GroundComposition composeGroundItems(
		List<Record> records) throws PreflightException {
		LinkedHashMap<String, Record> bySlot =
			new LinkedHashMap<String, Record>();
		int superseded = 0;
		for (Record record : records) {
			String slot = positionSlot(record.value, "pos");
			if (bySlot.remove(slot) != null) {
				superseded++;
			}
			bySlot.put(slot, record);
		}
		List<Record> groundItems = new ArrayList<Record>();
		List<Record> harvesting = new ArrayList<Record>();
		for (Record record : bySlot.values()) {
			int itemId = integer(record.value, "id");
			Integer sceneryId = HARVESTING_SCENERY.get(
				Integer.valueOf(itemId));
			if (sceneryId == null) {
				groundItems.add(record);
				continue;
			}
			Map<String, Object> value =
				new LinkedHashMap<String, Object>();
			value.put("id", Long.valueOf(sceneryId.intValue()));
			value.put("pos", copyPosition(record.value.get("pos")));
			value.put("direction", Long.valueOf(0));
			harvesting.add(record.reclassified(
				"harvesting-ground-item-scenery", value));
		}
		return new GroundComposition(
			groundItems, harvesting, superseded);
	}

	private static NpcComposition composeNpcs(
		List<Record> input,
		List<Record> removalRecords) throws PreflightException {
		List<Record> eventFiltered = new ArrayList<Record>();
		int eventPolicyRemovals = 0;
		for (Record record : input) {
			Map<String, Object> value = record.value;
			int npcId = integer(value, "id");
			int startX = positionCoordinate(value, "start", "X");
			if (npcId == 814) {
				value = copyNpc(
					value,
					position(317, 1607),
					position(314, 1603),
					position(319, 1608));
				record = record.withValue(value);
				startX = 317;
			}
			if ((npcId == 817 && startX < 600)
				|| (startX == 320 && CHRISTMAS_PARTY_IDS.contains(
					Integer.valueOf(npcId)))
				|| CHRISTMAS_SPIRIT_IDS.contains(Integer.valueOf(npcId))
				|| npcId == 835) {
				eventPolicyRemovals++;
				continue;
			}
			eventFiltered.add(record);
		}

		List<Record> cleaned = new ArrayList<Record>();
		List<int[]> bankerClusters = new ArrayList<int[]>();
		int tutorialRemovals = 0;
		int bankerRemovals = 0;
		for (Record record : eventFiltered) {
			int npcId = integer(record.value, "id");
			int x = positionCoordinate(record.value, "start", "X");
			int y = positionCoordinate(record.value, "start", "Y");
			if (x >= 190 && x <= 245 && y >= 710 && y <= 760) {
				tutorialRemovals++;
				continue;
			}
			if (BANKER_IDS.contains(Integer.valueOf(npcId))
				&& !keepBanker(x, y, bankerClusters)) {
				bankerRemovals++;
				continue;
			}
			cleaned.add(record);
		}

		Set<String> removals = new HashSet<String>();
		for (Record record : removalRecords) {
			removals.add(npcKey(record.value));
		}
		List<Record> result = new ArrayList<Record>();
		int explicitRemovals = 0;
		for (Record record : cleaned) {
			if (removals.contains(npcKey(record.value))) {
				explicitRemovals++;
				continue;
			}
			result.add(record);
		}
		return new NpcComposition(
			result,
			eventPolicyRemovals,
			tutorialRemovals,
			bankerRemovals,
			explicitRemovals);
	}

	private static boolean keepBanker(
		int x, int y, List<int[]> clusters) {
		for (int[] cluster : clusters) {
			int centerX = cluster[0] / cluster[2];
			int centerY = cluster[1] / cluster[2];
			if (Math.abs(x - centerX) <= 8
				&& Math.abs(y - centerY) <= 8) {
				cluster[0] += x;
				cluster[1] += y;
				cluster[2]++;
				if (cluster[3] >= 2) {
					return false;
				}
				cluster[3]++;
				return true;
			}
		}
		clusters.add(new int[] {x, y, 1, 1});
		return true;
	}

	private static CollapseResult collapse(
		List<Record> records, boolean directionIsPartOfSlot)
		throws PreflightException {
		LinkedHashMap<String, Record> bySlot =
			new LinkedHashMap<String, Record>();
		int superseded = 0;
		for (Record record : records) {
			String slot = positionSlot(record.value, "pos");
			if (directionIsPartOfSlot) {
				slot += ":" + integer(record.value, "direction");
			}
			if (bySlot.remove(slot) != null) {
				superseded++;
			}
			bySlot.put(slot, record);
		}
		return new CollapseResult(bySlot, superseded);
	}

	private static List<Record> records(
		Path root,
		List<SourceFile> sources,
		String role,
		String relativePath,
		String key) throws IOException, PreflightException {
		Path path = requiredFile(root, relativePath);
		Map<String, Object> document = JsonDocuments.readObject(path);
		if (document.size() != 1 || !(document.get(key) instanceof List)) {
			throw new PreflightException(
				"Spoiled Milk placement source must contain exactly one "
					+ "array named " + key + ": " + relativePath);
		}
		List<Object> values = JsonDocuments.array(document.get(key));
		sources.add(new SourceFile(
			role,
			relativePath,
			Hashes.sha256(path),
			values.size()));
		List<Record> result = new ArrayList<Record>();
		for (int index = 0; index < values.size(); index++) {
			if (!(values.get(index) instanceof Map)) {
				throw new PreflightException(
					"Spoiled Milk placement source entry must be an object: "
						+ relativePath + " index " + index);
			}
			Map<String, Object> value =
				JsonDocuments.object(values.get(index));
			if ("sceneries".equals(key)
				&& value.size() == 4
				&& value.containsKey("type")
				&& value.get("type") == null) {
				Map<String, Object> normalized =
					new LinkedHashMap<String, Object>();
				normalized.put("id", value.get("id"));
				normalized.put("pos", value.get("pos"));
				normalized.put("direction", value.get("direction"));
				value = normalized;
			}
			result.add(new Record(
				role,
				relativePath,
				index,
				value));
		}
		return result;
	}

	private static Path requiredFile(Path root, String relativePath)
		throws IOException, PreflightException {
		Path path = root.resolve(relativePath).normalize();
		if (!path.startsWith(root)
			|| !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(path)) {
			throw new PreflightException(
				"Spoiled Milk composition source is missing or unsafe: "
					+ relativePath);
		}
		return path;
	}

	private static Map<String, String> readConfig(Path path)
		throws IOException, PreflightException {
		Map<String, String> values = new HashMap<String, String>();
		for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			Matcher matcher = CONFIG_VALUE.matcher(line);
			if (!matcher.matches()) {
				continue;
			}
			String key = matcher.group(1).toLowerCase(Locale.ROOT);
			if (values.put(key, matcher.group(2).trim()) != null) {
				throw new PreflightException(
					"Spoiled Milk configuration key appears more than once: "
						+ key);
			}
		}
		return values;
	}

	private static void requireConfig(Map<String, String> values)
		throws PreflightException {
		requireInt(values, "location_data", 2);
		requireInt(values, "based_map_data", 64);
		for (String key : new String[] {
			"member_world",
			"want_fixed_broken_mechanics",
			"want_decorated_mod_room",
			"want_runecraft",
			"spawn_auction_npcs",
			"want_harvesting",
			"want_custom_quests",
			"want_woodcutting_guild",
			"want_myworld",
			"death_island",
			"custom_landscape"
		}) {
			requireBoolean(values, key, true);
		}
		requireBoolean(values, "spawn_iron_man_npcs", false);
		requireBoolean(values, "esters_bunnies", false);
		requireBoolean(values, "mice_to_meet_you", false);
		requireBoolean(values, "a_lumbridge_carol", false);
		requireBoolean(values, "army_of_obscurity", false);
	}

	private static void requireInt(
		Map<String, String> values, String key, int expected)
		throws PreflightException {
		String raw = values.get(key);
		try {
			if (raw != null && Integer.parseInt(raw) == expected) {
				return;
			}
		} catch (NumberFormatException ignored) {
		}
		throw new PreflightException(
			"Spoiled Milk package requires " + key + "=" + expected + ".");
	}

	private static void requireBoolean(
		Map<String, String> values, String key, boolean expected)
		throws PreflightException {
		String raw = values.get(key);
		boolean actual = raw != null
			? "true".equalsIgnoreCase(raw)
				|| "yes".equalsIgnoreCase(raw)
				|| "on".equalsIgnoreCase(raw)
				|| "1".equals(raw)
			: false;
		if (actual != expected) {
			throw new PreflightException(
				"Spoiled Milk package requires " + key + "="
					+ expected + ".");
		}
	}

	private static int integer(Map<String, Object> value, String key)
		throws PreflightException {
		Object raw = value.get(key);
		if (!(raw instanceof Long)
			|| (Long) raw < Integer.MIN_VALUE
			|| (Long) raw > Integer.MAX_VALUE) {
			throw new PreflightException(
				"Spoiled Milk source field " + key
					+ " must be a signed 32-bit integer.");
		}
		return ((Long) raw).intValue();
	}

	private static int positionCoordinate(
		Map<String, Object> value, String positionKey, String coordinateKey)
		throws PreflightException {
		Object raw = value.get(positionKey);
		if (!(raw instanceof Map)) {
			throw new PreflightException(
				"Spoiled Milk source field " + positionKey
					+ " must be a position object.");
		}
		return integer(JsonDocuments.object(raw), coordinateKey);
	}

	private static String positionSlot(
		Map<String, Object> value, String positionKey)
		throws PreflightException {
		return positionCoordinate(value, positionKey, "X") + ":"
			+ positionCoordinate(value, positionKey, "Y");
	}

	private static String npcKey(Map<String, Object> value)
		throws PreflightException {
		return integer(value, "id") + ":"
			+ positionSlot(value, "start") + ":"
			+ positionSlot(value, "min") + ":"
			+ positionSlot(value, "max");
	}

	private static Map<String, Object> copyNpc(
		Map<String, Object> source,
		Map<String, Object> start,
		Map<String, Object> minimum,
		Map<String, Object> maximum) throws PreflightException {
		Map<String, Object> result =
			new LinkedHashMap<String, Object>();
		result.put("id", Long.valueOf(integer(source, "id")));
		result.put("start", start);
		result.put("min", minimum);
		result.put("max", maximum);
		return result;
	}

	private static Map<String, Object> copyPosition(Object raw)
		throws PreflightException {
		if (!(raw instanceof Map)) {
			throw new PreflightException(
				"Spoiled Milk source position must be an object.");
		}
		Map<String, Object> source = JsonDocuments.object(raw);
		return position(integer(source, "X"), integer(source, "Y"));
	}

	private static Map<String, Object> position(int x, int y) {
		Map<String, Object> result =
			new LinkedHashMap<String, Object>();
		result.put("X", Long.valueOf(x));
		result.put("Y", Long.valueOf(y));
		return result;
	}

	private static Map<Integer, Integer> harvestingScenery() {
		Map<Integer, Integer> result =
			new HashMap<Integer, Integer>();
		result.put(Integer.valueOf(18), Integer.valueOf(1262));
		result.put(Integer.valueOf(55), Integer.valueOf(1257));
		result.put(Integer.valueOf(143), Integer.valueOf(1283));
		result.put(Integer.valueOf(165), Integer.valueOf(1274));
		result.put(Integer.valueOf(236), Integer.valueOf(1256));
		result.put(Integer.valueOf(241), Integer.valueOf(1266));
		result.put(Integer.valueOf(320), Integer.valueOf(1268));
		result.put(Integer.valueOf(422), Integer.valueOf(1327));
		result.put(Integer.valueOf(469), Integer.valueOf(1273));
		result.put(Integer.valueOf(471), Integer.valueOf(1260));
		result.put(Integer.valueOf(622), Integer.valueOf(1280));
		result.put(Integer.valueOf(765), Integer.valueOf(1258));
		result.put(Integer.valueOf(936), Integer.valueOf(1259));
		return Collections.unmodifiableMap(result);
	}

	private static Map<String, Integer> counts(
		int npcs, int groundItems, int scenery, int boundaries) {
		Map<String, Integer> result =
			new LinkedHashMap<String, Integer>();
		result.put("npcs", Integer.valueOf(npcs));
		result.put("groundItems", Integer.valueOf(groundItems));
		result.put("scenery", Integer.valueOf(scenery));
		result.put("boundaries", Integer.valueOf(boundaries));
		return result;
	}

	private static void requireCount(
		String label, int actual, int expected) throws PreflightException {
		if (actual != expected) {
			throw new PreflightException(
				"Spoiled Milk effective " + label + " count changed: expected "
					+ expected + " but found " + actual + ".");
		}
	}

	static final class Record {
		final String role;
		final String path;
		final int sourceIndex;
		final Map<String, Object> value;

		Record(
			String role,
			String path,
			int sourceIndex,
			Map<String, Object> value) {
			this.role = role;
			this.path = path;
			this.sourceIndex = sourceIndex;
			this.value = value;
		}

		Record withValue(Map<String, Object> replacement) {
			return new Record(role, path, sourceIndex, replacement);
		}

		Record reclassified(
			String replacementRole, Map<String, Object> replacement) {
			return new Record(
				replacementRole, path, sourceIndex, replacement);
		}

		String placementId(String family) {
			String filename = path.substring(path.lastIndexOf('/') + 1)
				.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("(^-+|-+$)", "");
			return "spoiled-milk." + family + "." + filename + "."
				+ String.format(Locale.ROOT, "%06d", sourceIndex);
		}
	}

	static final class Result {
		final Path configPath;
		final String configSha256;
		final List<SourceFile> sources;
		final int rawInputCount;
		final Map<String, Integer> rawCounts;
		final Map<String, Integer> effectiveCounts;
		final Map<String, Integer> transformations;
		final List<Record> npcs;
		final List<Record> groundItems;
		final List<Record> scenery;
		final List<Record> boundaries;

		Result(
			Path configPath,
			String configSha256,
			List<SourceFile> sources,
			int rawInputCount,
			Map<String, Integer> rawCounts,
			Map<String, Integer> effectiveCounts,
			Map<String, Integer> transformations,
			List<Record> npcs,
			List<Record> groundItems,
			List<Record> scenery,
			List<Record> boundaries) {
			this.configPath = configPath;
			this.configSha256 = configSha256;
			this.sources = Collections.unmodifiableList(
				new ArrayList<SourceFile>(sources));
			this.rawInputCount = rawInputCount;
			this.rawCounts = Collections.unmodifiableMap(
				new LinkedHashMap<String, Integer>(rawCounts));
			this.effectiveCounts = Collections.unmodifiableMap(
				new LinkedHashMap<String, Integer>(effectiveCounts));
			this.transformations = Collections.unmodifiableMap(
				new LinkedHashMap<String, Integer>(transformations));
			this.npcs = Collections.unmodifiableList(
				new ArrayList<Record>(npcs));
			this.groundItems = Collections.unmodifiableList(
				new ArrayList<Record>(groundItems));
			this.scenery = Collections.unmodifiableList(
				new ArrayList<Record>(scenery));
			this.boundaries = Collections.unmodifiableList(
				new ArrayList<Record>(boundaries));
		}

		int effectiveCount() {
			int result = 0;
			for (Integer count : effectiveCounts.values()) {
				result = Math.addExact(result, count.intValue());
			}
			return result;
		}

		Map<String, Object> toDocument() {
			Map<String, Object> document =
				new LinkedHashMap<String, Object>();
			document.put("policy", "myworld-config-effective-world-v1");
			document.put("configurationPath", CONFIG_PATH);
			document.put("configurationSha256", configSha256);
			document.put(
				"rawInputPlacementRecords",
				Long.valueOf(rawInputCount));
			document.put("rawInputRecordsByFamily", numberMap(rawCounts));
			document.put(
				"effectivePlacementRecords",
				Long.valueOf(effectiveCount()));
			document.put(
				"effectiveRecordsByFamily",
				numberMap(effectiveCounts));
			document.put("transformations", numberMap(transformations));
			List<Object> sourceDocuments = new ArrayList<Object>();
			for (SourceFile source : sources) {
				sourceDocuments.add(source.toDocument());
			}
			document.put("sources", sourceDocuments);
			return document;
		}
	}

	private static Map<String, Object> numberMap(
		Map<String, Integer> values) {
		Map<String, Object> result =
			new LinkedHashMap<String, Object>();
		for (Map.Entry<String, Integer> entry : values.entrySet()) {
			result.put(
				entry.getKey(),
				Long.valueOf(entry.getValue().intValue()));
		}
		return result;
	}

	static final class SourceFile {
		final String role;
		final String path;
		final String sha256;
		final int recordCount;

		SourceFile(
			String role, String path, String sha256, int recordCount) {
			this.role = role;
			this.path = path;
			this.sha256 = sha256;
			this.recordCount = recordCount;
		}

		Map<String, Object> toDocument() {
			Map<String, Object> result =
				new LinkedHashMap<String, Object>();
			result.put("role", role);
			result.put("path", path);
			result.put("sha256", sha256);
			result.put("recordCount", Long.valueOf(recordCount));
			return result;
		}
	}

	private static final class CollapseResult {
		final LinkedHashMap<String, Record> bySlot;
		final int supersededCount;

		CollapseResult(
			LinkedHashMap<String, Record> bySlot, int supersededCount) {
			this.bySlot = bySlot;
			this.supersededCount = supersededCount;
		}
	}

	private static final class GroundComposition {
		final List<Record> groundItems;
		final List<Record> harvestingScenery;
		final int supersededGroundItems;

		GroundComposition(
			List<Record> groundItems,
			List<Record> harvestingScenery,
			int supersededGroundItems) {
			this.groundItems = groundItems;
			this.harvestingScenery = harvestingScenery;
			this.supersededGroundItems = supersededGroundItems;
		}
	}

	private static final class NpcComposition {
		final List<Record> records;
		final int eventPolicyRemovals;
		final int tutorialRemovals;
		final int bankerRemovals;
		final int explicitRemovals;

		NpcComposition(
			List<Record> records,
			int eventPolicyRemovals,
			int tutorialRemovals,
			int bankerRemovals,
			int explicitRemovals) {
			this.records = records;
			this.eventPolicyRemovals = eventPolicyRemovals;
			this.tutorialRemovals = tutorialRemovals;
			this.bankerRemovals = bankerRemovals;
			this.explicitRemovals = explicitRemovals;
		}
	}
}
