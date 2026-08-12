package com.openrsc.server.plugins;

import com.openrsc.server.Server;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerBalances;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerData;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDialoguePlan;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerGuildAccess;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerRank;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerState;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerTaskService;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerShopService;
import com.openrsc.server.event.custom.MonsterSlayerShopRestockEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.Group;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.plugins.authentic.commands.Development;
import com.openrsc.server.plugins.custom.myworld.npcs.MonsterSlayerContacts;
import com.openrsc.server.plugins.custom.myworld.npcs.MonsterSlayerChallengeShops;
import com.openrsc.server.util.rsc.DataConversions;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Plugin-jar route guard for the six contacts, associates, and ambient NPCs. */
public final class MonsterSlayerContactsRouteTest {
	private MonsterSlayerContactsRouteTest() { }

	public static void main(String[] args) throws Exception {
		Server server = new Server("myworld.conf");
		server.getEntityHandler().load();
		MonsterSlayerContacts routes = new MonsterSlayerContacts();
		for (int id = 846; id <= 851; id++) {
			Npc npc = new Npc(server.getWorld(), id, 100 + id, 600);
			assertTrue(routes.blockTalkNpc(null, npc), "Talk-to contact " + id);
			assertTrue(routes.blockOpNpc(null, npc, "Task"), "Task contact " + id);
			assertFalse(routes.blockOpNpc(null, npc, "Trade"), "contact trade " + id);
		}
		for (int id = 852; id <= 857; id++) {
			Npc npc = new Npc(server.getWorld(), id, 100 + id, 600);
			assertTrue(routes.blockTalkNpc(null, npc), "Talk-to associate " + id);
			assertTrue(routes.blockOpNpc(null, npc, "Trade"), "Trade associate " + id);
			assertTrue(routes.blockOpNpc(null, npc, "Shop"), "Shop associate " + id);
			assertFalse(routes.blockOpNpc(null, npc, "Task"), "associate Task must retain dialogue " + id);
		}
		for (int id = 858; id <= 860; id++) {
			Npc npc = new Npc(server.getWorld(), id, 100 + id, 600);
			assertTrue(routes.blockTalkNpc(null, npc), "Talk-to ambient " + id);
			assertFalse(routes.blockOpNpc(null, npc, "Task"), "ambient task isolation " + id);
			assertFalse(routes.blockOpNpc(null, npc, "Trade"), "ambient trade isolation " + id);
		}
		shortcutAssignmentAndPromotionAreRealInteractions(server, routes);
		promotionRenderingIsExactForEveryRank(server);
		guildAccessModesAndQuestStates(server);
		associateAmbientAndOwnershipBoundaries(server, routes);
		aleFailureMessagesRemainTruthful();
		hobartFollowUpDialogueIsBoundedAndTaskIndependent();
		shopPresentationUsesTypedCostsAndTruthfulFailures(server);
		associateOperationsAndWorldRestockAreBounded(server);
		fledglingAssociateSatchelDialogueAndPurchase(server);
		veteranHeadquartersUsesBlueMoonInnPlacement(server);
		championsSectUsesTwoDistinctMithrilNpcs();
		heroesGuildSectUsesClearInteriorAdamantNpcs(server);
		fledglingPresentationAndRoamingContractIsValid(server);
		adeptRankKeepsLegacyPersistenceCompatibility();
		menuResponsesRemainVisiblePlayerSpeech();
		contactProofDialogueIsClearAndRankGated(server);
		maraAssignmentDialogueUsesAuthoritativeProgression(server);
		branDialogueUsesAuthoritativeVeteranProgression(server);
		doranDialogueUsesAuthoritativeEliteProgression(server);
		hazardWarningsAreNaturalOrderedDialogue(server);
		developmentCompletionUsesNormalSlayerProgression(server);
		System.out.println("Monster Slayer contact plugin routes: PASS");
	}

	private static void adeptRankKeepsLegacyPersistenceCompatibility() {
		assertEquals(MonsterSlayerRank.INITIATE, MonsterSlayerRank.fromCode(2), "persisted rank code two remains stable");
		assertEquals(MonsterSlayerRank.INITIATE, MonsterSlayerRank.fromKey("initiate"), "legacy rank key remains readable");
		assertEquals(MonsterSlayerRank.INITIATE, MonsterSlayerRank.fromKey("adept"), "Adept rank alias is accepted");
		assertEquals("Adept", MonsterSlayerRank.INITIATE.getDisplayName(), "rank display name changes without state migration");
		assertEquals(com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge.INITIATE,
			com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge.fromKey("initiate"), "legacy points key remains readable");
		assertEquals(com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge.INITIATE,
			com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge.fromKey("adept"), "Adept points alias is accepted");
		assertEquals("initiate", com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge.INITIATE.getCacheSuffix(), "legacy points cache key remains stable");
		assertEquals("Adept", com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge.INITIATE.getDisplayName(), "points display name is Adept");
	}

	/** Third-tier save keys remain legacy-compatible, but the live headquarters is Varrock's Blue Moon Inn. */
	private static void veteranHeadquartersUsesBlueMoonInnPlacement(Server server) throws Exception {
		JSONObject locations = new JSONObject(new String(Files.readAllBytes(Paths.get(
			"conf", "server", "defs", "locs", "MyWorldNpcLocs.json")), StandardCharsets.UTF_8));
		int[][] expected = {{848, 122, 524}, {854, 120, 524}, {860, 123, 525}};
		java.util.Set<String> starts = new java.util.HashSet<String>();
		for (int[] fixture : expected) {
			JSONObject entry = location(locations.getJSONArray("npclocs"), fixture[0]);
			JSONObject start = entry.getJSONObject("start");
			assertEquals(fixture[1], start.getInt("X"), "Blue Moon X " + fixture[0]);
			assertEquals(fixture[2], start.getInt("Y"), "Blue Moon Y " + fixture[0]);
			assertTrue(starts.add(fixture[1] + "," + fixture[2]), "unique Blue Moon start " + fixture[0]);
			assertTrue(entry.getJSONObject("min").getInt("X") < entry.getJSONObject("max").getInt("X")
				|| entry.getJSONObject("min").getInt("Y") < entry.getJSONObject("max").getInt("Y"), "Blue Moon NPC roams " + fixture[0]);
			assertTrue(entry.getJSONObject("min").getInt("X") >= 120 && entry.getJSONObject("max").getInt("X") <= 123
				&& entry.getJSONObject("min").getInt("Y") >= 524 && entry.getJSONObject("max").getInt("Y") <= 525,
				"Blue Moon roam remains in clear central floor " + fixture[0]);
		}
		JSONArray definitions = new JSONObject(new String(Files.readAllBytes(Paths.get(
			"conf", "server", "defs", "MonsterSlayerNpcDefs.json")), StandardCharsets.UTF_8)).getJSONArray("npcs");
		for (int id : new int[] {848, 854, 860}) assertTrue(location(definitions, id).getString("description").contains("Blue Moon"),
			"Blue Moon NPC description " + id);
		assertEquals("Veteran Slayer Associate", location(definitions, 854).getString("name"),
			"Veteran associate is identifiable at Blue Moon");
		int[][] sprites = {
			{848, 7, 29, 38, 99, 49}, // steel plate, legs, square shield, and sword
			{854, 3, 56, 2, -1, 110}, // female steel plate and battleaxe, no plate legs/shield
			{860, 5, 29, 2, -1, 117} // steel plate body, ordinary legs, and mace; no plate legs/shield
		};
		java.util.Set<String> appearances = new java.util.HashSet<String>();
		for (int[] fixture : sprites) {
			JSONObject definition = location(definitions, fixture[0]);
			for (int layer = 1; layer <= 5; layer++) assertEquals(fixture[layer], definition.getInt("sprites" + layer),
				"Veteran steel layer " + fixture[0] + "/" + layer);
			assertTrue(appearances.add(definition.getInt("sprites1") + ":" + definition.getInt("sprites2") + ":"
				+ definition.getInt("sprites3") + ":" + definition.getInt("sprites4") + ":" + definition.getInt("sprites5")),
				"distinct Veteran appearance " + fixture[0]);
			assertEquals(0, definition.getInt("attackable"), "Veteran NPC remains non-attackable " + fixture[0]);
		}
		String clientDefinitions = new String(Files.readAllBytes(Paths.get("..", "Client_Base", "src", "com", "openrsc", "client", "entityhandling", "EntityHandler.java")), StandardCharsets.UTF_8);
		assertTrue(clientDefinitions.contains("new int[]{7, 29, 38, 99, 49, -1, -1, -1"), "client Bran uses full steel composition");
		assertTrue(clientDefinitions.contains("new int[]{3, 56, 2, -1, 110, -1, -1, -1"), "client Veteran associate uses lighter steel composition");
		assertTrue(clientDefinitions.contains("new int[]{5, 29, 2, -1, 117, -1, -1, -1"), "client ambient Veteran uses a valid head, steel plate body, ordinary legs, and mace");
		for (int id : new int[] {848, 854, 860}) {
			JSONObject definition = location(definitions, id);
			assertTrue(clientDefinitions.contains("\"" + definition.getString("name") + "\", \"" + definition.getString("description") + "\""),
				"client/server Veteran identity parity " + id);
		}
		assertTrue(clientDefinitions.contains("new AnimationDef(\"platemailtop\", \"equipment\", 15658734"), "steel plate sprite identity is proven");
		assertTrue(clientDefinitions.contains("new AnimationDef(\"fplatemailtop\", \"equipment\", 15658734"), "female steel plate sprite identity is proven");
		assertTrue(clientDefinitions.contains("new AnimationDef(\"head2\", \"player\""), "ambient Veteran head sprite identity is proven");
		assertTrue(clientDefinitions.contains("new AnimationDef(\"legs1\", \"player\""), "ambient Veteran ordinary-leg sprite identity is proven");
		assertTrue(clientDefinitions.contains("new AnimationDef(\"mace\", \"equipment\", 15658734"), "ambient Veteran steel mace sprite identity is proven");
		assertVeteranRangeDoesNotIntersectWorldGeometry(server, locations, "conf/server/defs/locs");
	}

	private static void championsSectUsesTwoDistinctMithrilNpcs() throws Exception {
		JSONArray definitions = new JSONObject(new String(Files.readAllBytes(Paths.get(
			"conf", "server", "defs", "MonsterSlayerNpcDefs.json")), StandardCharsets.UTF_8)).getJSONArray("npcs");
		JSONObject locations = new JSONObject(new String(Files.readAllBytes(Paths.get(
			"conf", "server", "defs", "locs", "MyWorldNpcLocs.json")), StandardCharsets.UTF_8));
		int[][] expected = {
			{849, 15, 30, 39, 100, 50}, // full helm, mithril plate/legs, square shield, sword
			{855, 3, 57, 2, -1, 111} // female head/plate, ordinary legs, no shield, battleaxe
		};
		java.util.Set<String> appearances = new java.util.HashSet<String>();
		int championsDefinitions = 0;
		for (Object value : definitions) {
			JSONObject definition = (JSONObject) value;
			if (definition.getInt("id") == 849 || definition.getInt("id") == 855) championsDefinitions++;
		}
		assertEquals(2, championsDefinitions, "Champions sect remains exactly Doran and one associate");
		int championsSpawns = 0;
		for (Object value : locations.getJSONArray("npclocs")) {
			JSONObject spawn = (JSONObject) value;
			int id = spawn.getInt("id");
			if (id < 846 || id > 860) continue;
			JSONObject start = spawn.getJSONObject("start");
			if (start.getInt("X") >= 145 && start.getInt("X") <= 155
					&& start.getInt("Y") >= 550 && start.getInt("Y") <= 560) championsSpawns++;
		}
		assertEquals(2, championsSpawns, "Champions Guild retains exactly two placed Slayer NPCs");
		for (int[] fixture : expected) {
			JSONObject definition = location(definitions, fixture[0]);
			for (int layer = 1; layer <= 5; layer++) assertEquals(fixture[layer], definition.getInt("sprites" + layer),
				"Champions mithril layer " + fixture[0] + "/" + layer);
			assertTrue(appearances.add(definition.getInt("sprites1") + ":" + definition.getInt("sprites2") + ":"
				+ definition.getInt("sprites3") + ":" + definition.getInt("sprites4") + ":" + definition.getInt("sprites5")),
				"Champions NPCs have distinct silhouettes " + fixture[0]);
			assertEquals(0, definition.getInt("attackable"), "Champions Slayer NPC remains non-attackable " + fixture[0]);
			location(locations.getJSONArray("npclocs"), fixture[0]);
		}
		assertEquals("Elite Slayer Associate", location(definitions, 855).getString("name"),
			"Champions associate has rank-specific identity");
		String clientDefinitions = new String(Files.readAllBytes(Paths.get("..", "Client_Base", "src", "com", "openrsc", "client", "entityhandling", "EntityHandler.java")), StandardCharsets.UTF_8);
		assertTrue(clientDefinitions.contains("new int[]{15, 30, 39, 100, 50, -1, -1, -1"),
			"client Doran uses full mithril composition");
		assertTrue(clientDefinitions.contains("new int[]{3, 57, 2, -1, 111, -1, -1, -1"),
			"client associate uses partial mithril composition");
		assertTrue(clientDefinitions.contains("\"Elite Slayer Associate\", \"An Elite Slayer quartermaster\""),
			"client/server associate identity is synchronized");
		for (String identity : new String[] {
			"new AnimationDef(\"fullhelm\", \"equipment\", 10072780",
			"new AnimationDef(\"platemailtop\", \"equipment\", 10072780",
			"new AnimationDef(\"platemaillegs\", \"equipment\", 10072780",
			"new AnimationDef(\"squareshield\", \"equipment\", 10072780",
			"new AnimationDef(\"sword\", \"equipment\", 10072780",
			"new AnimationDef(\"fplatemailtop\", \"equipment\", 10072780",
			"new AnimationDef(\"battleaxe\", \"equipment\", 10072780"
		}) assertTrue(clientDefinitions.contains(identity), "proven mithril animation identity " + identity);
	}

	private static void heroesGuildSectUsesClearInteriorAdamantNpcs(Server server) throws Exception {
		JSONArray definitions = new JSONObject(new String(Files.readAllBytes(Paths.get(
			"conf", "server", "defs", "MonsterSlayerNpcDefs.json")), StandardCharsets.UTF_8)).getJSONArray("npcs");
		JSONObject locations = new JSONObject(new String(Files.readAllBytes(Paths.get(
			"conf", "server", "defs", "locs", "MyWorldNpcLocs.json")), StandardCharsets.UTF_8));
		int[][] expected = {
			{850, 16, 58, 40, 101, 51}, // full helm, female adamant plate, legs, shield, sword
			{856, 7, 31, 2, -1, 112} // head, adamant plate, ordinary legs, no shield, battleaxe
		};
		int[][] placement = {
			{850, 372, 1381, 371, 1380, 372, 1382},
			{856, 374, 1381, 373, 1380, 374, 1382}
		};
		java.util.Set<String> appearances = new java.util.HashSet<String>();
		java.util.Set<String> starts = new java.util.HashSet<String>();
		for (int[] fixture : expected) {
			JSONObject definition = location(definitions, fixture[0]);
			for (int layer = 1; layer <= 5; layer++) assertEquals(fixture[layer],
				definition.getInt("sprites" + layer), "Heroes adamant layer " + fixture[0] + "/" + layer);
			assertTrue(appearances.add(definition.getInt("sprites1") + ":" + definition.getInt("sprites2")
				+ ":" + definition.getInt("sprites3") + ":" + definition.getInt("sprites4")
				+ ":" + definition.getInt("sprites5")), "Heroes NPCs have distinct silhouettes " + fixture[0]);
			assertEquals(0, definition.getInt("attackable"), "Heroes Slayer NPC remains non-attackable " + fixture[0]);
		}
		for (int[] fixture : placement) {
			JSONObject spawn = location(locations.getJSONArray("npclocs"), fixture[0]);
			JSONObject start = spawn.getJSONObject("start"), min = spawn.getJSONObject("min"), max = spawn.getJSONObject("max");
			assertEquals(fixture[1], start.getInt("X"), "Heroes interior start X " + fixture[0]);
			assertEquals(fixture[2], start.getInt("Y"), "Heroes interior start Y " + fixture[0]);
			assertEquals(fixture[3], min.getInt("X"), "Heroes roam min X " + fixture[0]);
			assertEquals(fixture[4], min.getInt("Y"), "Heroes roam min Y " + fixture[0]);
			assertEquals(fixture[5], max.getInt("X"), "Heroes roam max X " + fixture[0]);
			assertEquals(fixture[6], max.getInt("Y"), "Heroes roam max Y " + fixture[0]);
			assertTrue(starts.add(start.getInt("X") + "," + start.getInt("Y")),
				"Heroes Slayer starts remain unique " + fixture[0]);
			assertTrue(min.getInt("X") >= 371 && max.getInt("X") <= 374
				&& min.getInt("Y") >= 1380 && max.getInt("Y") <= 1382,
				"Heroes roaming stays inside the clear upper room " + fixture[0]);
		}
		assertTrue(rangesAreDisjoint(location(locations.getJSONArray("npclocs"), 850),
			location(locations.getJSONArray("npclocs"), 856)),
			"Heroes Slayer NPC roaming pockets do not overlap");
		assertEquals("Champion Slayer Associate", location(definitions, 856).getString("name"),
			"Heroes associate has rank-specific identity");

		int heroesSpawns = 0;
		for (Object value : locations.getJSONArray("npclocs")) {
			JSONObject spawn = (JSONObject) value;
			int id = spawn.getInt("id");
			if (id < 846 || id > 860) continue;
			JSONObject start = spawn.getJSONObject("start");
			if (start.getInt("X") >= 369 && start.getInt("X") <= 376
					&& start.getInt("Y") >= 1380 && start.getInt("Y") <= 1383) heroesSpawns++;
		}
		assertEquals(2, heroesSpawns, "Heroes Guild retains exactly two placed Slayer NPCs");
		assertHeroesRangeDoesNotIntersectWorldPlacements(server, locations, "conf/server/defs/locs");

		server.getWorld().getRegionManager().load();
		for (int[] fixture : placement) {
			for (int x = fixture[3]; x <= fixture[5]; x++) {
				for (int y = fixture[4]; y <= fixture[6]; y++) {
					assertTrue((server.getWorld().getTile(x, y).traversalMask
						& com.openrsc.server.util.rsc.CollisionFlag.FULL_BLOCK) == 0,
						"Heroes roam tile is walkable in authoritative terrain " + fixture[0] + " @ " + x + "," + y);
					assertTrue(com.openrsc.server.model.PathValidation.checkPath(server.getWorld(),
						Point.location(fixture[1], fixture[2]), Point.location(x, y), true),
						"Heroes roam tile is connected to its start " + fixture[0] + " @ " + x + "," + y);
				}
			}
		}
		assertTrue(com.openrsc.server.model.PathValidation.checkPath(server.getWorld(),
			Point.location(374, 1382), Point.location(372, 1381), true),
			"clear tile beside the guild ladder reaches Sella without crossing a wall");

		String clientDefinitions = new String(Files.readAllBytes(Paths.get("..", "Client_Base", "src", "com",
			"openrsc", "client", "entityhandling", "EntityHandler.java")), StandardCharsets.UTF_8);
		assertTrue(clientDefinitions.contains("new int[]{16, 58, 40, 101, 51, -1, -1, -1"),
			"client Sella uses full adamant composition");
		assertTrue(clientDefinitions.contains("new int[]{7, 31, 2, -1, 112, -1, -1, -1"),
			"client associate uses partial adamant composition");
		assertTrue(clientDefinitions.contains("\"Champion Slayer Associate\", \"A Champion Slayer supplier\""),
			"client/server Heroes associate identity is synchronized");
		for (String identity : new String[] {
			"new AnimationDef(\"fullhelm\", \"equipment\", 11717785",
			"new AnimationDef(\"fplatemailtop\", \"equipment\", 11717785",
			"new AnimationDef(\"platemailtop\", \"equipment\", 11717785",
			"new AnimationDef(\"platemaillegs\", \"equipment\", 11717785",
			"new AnimationDef(\"squareshield\", \"equipment\", 11717785",
			"new AnimationDef(\"sword\", \"equipment\", 11717785",
			"new AnimationDef(\"battleaxe\", \"equipment\", 11717785"
		}) assertTrue(clientDefinitions.contains(identity), "proven adamant animation identity " + identity);
	}

	private static void assertHeroesRangeDoesNotIntersectWorldPlacements(Server server,
			JSONObject locations, String directory) throws Exception {
		for (String pattern : new String[] {"*SceneryLocs*.json", "*BoundaryLocs*.json"}) {
			try (java.nio.file.DirectoryStream<java.nio.file.Path> files = Files.newDirectoryStream(Paths.get(directory), pattern)) {
				for (java.nio.file.Path file : files) {
					JSONObject objects = new JSONObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
					String collection = pattern.startsWith("*Scenery") ? "sceneries" : "boundaries";
					for (Object value : objects.getJSONArray(collection)) {
						JSONObject entry = (JSONObject) value;
						int width = 1, height = 1;
						if ("sceneries".equals(collection)) {
							com.openrsc.server.external.GameObjectDef object = server.getEntityHandler().getGameObjectDef(entry.getInt("id"));
							if (object == null) throw new AssertionError("Unknown scenery definition " + entry.getInt("id"));
							int direction = entry.getInt("direction");
							width = direction == 0 || direction == 4 ? object.getWidth() : object.getHeight();
							height = direction == 0 || direction == 4 ? object.getHeight() : object.getWidth();
						}
						assertRangesDoNotIntersect(locations, new int[] {850, 856},
							entry.getJSONObject("pos").getInt("X"), entry.getJSONObject("pos").getInt("Y"),
							width, height, "Heroes roam intersects " + file + " id=" + entry.getInt("id"));
					}
				}
			}
		}
		for (String pattern : new String[] {"NpcLocs*.json", "MyWorldNpcLocs.json"}) {
			try (java.nio.file.DirectoryStream<java.nio.file.Path> files = Files.newDirectoryStream(Paths.get(directory), pattern)) {
				for (java.nio.file.Path file : files) {
					JSONObject npcs = new JSONObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
					for (Object value : npcs.getJSONArray("npclocs")) {
						JSONObject other = (JSONObject) value;
						if (other.getInt("id") == 850 || other.getInt("id") == 856) continue;
						for (int hero : new int[] {850, 856}) assertTrue(rangesAreDisjoint(
							location(locations.getJSONArray("npclocs"), hero), other),
							"Heroes roam avoids existing NPC " + other.getInt("id") + " in " + file + " npc=" + hero);
					}
				}
			}
		}
	}

	private static boolean rangesAreDisjoint(JSONObject left, JSONObject right) {
		JSONObject leftMin = left.getJSONObject("min"), leftMax = left.getJSONObject("max");
		JSONObject rightMin = right.getJSONObject("min"), rightMax = right.getJSONObject("max");
		return leftMax.getInt("X") < rightMin.getInt("X") || leftMin.getInt("X") > rightMax.getInt("X")
			|| leftMax.getInt("Y") < rightMin.getInt("Y") || leftMin.getInt("Y") > rightMax.getInt("Y");
	}

	private static void menuResponsesRemainVisiblePlayerSpeech() {
		assertEquals("Yes please.", MonsterSlayerContacts.selectedChoice(0, "Yes please.", "Not now."), "acceptance choice is spoken");
		assertEquals("Not now.", MonsterSlayerContacts.selectedChoice(1, "Yes please.", "Not now."), "decline choice is spoken");
		assertTrue(MonsterSlayerContacts.selectedChoice(-1, "Yes please.") == null, "cancelled choice is silent");
		assertTrue(MonsterSlayerContacts.selectedChoice(2, "Yes please.") == null, "out-of-range choice is silent");
	}

	/** Fledgling NPCs must agree with the hard-coded client definition layer order. */
	private static void fledglingPresentationAndRoamingContractIsValid(Server server) throws Exception {
		JSONObject locations = new JSONObject(new String(Files.readAllBytes(Paths.get(
			"conf", "server", "defs", "locs", "MyWorldNpcLocs.json")), StandardCharsets.UTF_8));
		JSONArray definitions = new JSONObject(new String(Files.readAllBytes(Paths.get(
			"conf", "server", "defs", "MonsterSlayerNpcDefs.json")), StandardCharsets.UTF_8)).getJSONArray("npcs");
		java.util.Set<String> starts = new java.util.HashSet<String>();
		int[][] expectedSprites = {
			{846, 6, 27, 36, 97, 47, -1, -1, -1}, // head, bronze plate body, bronze legs, bronze shield, bronze sword
			{852, 3, 54, 2, -1, 108, -1, -1, -1}, // female head, female bronze plate body, legs, no shield, bronze weapon
			{858, 7, 27, 2, -1, 115, -1, -1, -1} // bearded head, bronze plate body, legs, no shield, bronze weapon
		};
		java.util.Set<String> appearances = new java.util.HashSet<String>();
		for (int[] expected : expectedSprites) {
			int id = expected[0];
			JSONObject spawn = location(locations.getJSONArray("npclocs"), id);
			JSONObject start = spawn.getJSONObject("start");
			String coordinate = start.getInt("X") + "," + start.getInt("Y");
			assertTrue(starts.add(coordinate), "unique Slayer NPC start " + id);
			assertTrue(spawn.getJSONObject("min").getInt("X") < spawn.getJSONObject("max").getInt("X")
				|| spawn.getJSONObject("min").getInt("Y") < spawn.getJSONObject("max").getInt("Y"), "bounded roaming " + id);
			JSONObject definition = location(definitions, id);
			assertEquals(0, definition.getInt("attackable"), "Slayer NPC stays non-attackable " + id);
			for (int layer = 1; layer <= 8; layer++) assertEquals(expected[layer], definition.getInt("sprites" + layer), "server effective layer " + id + "/" + layer);
			assertTrue(appearances.add(definition.getInt("sprites1") + ":" + definition.getInt("sprites2") + ":" + definition.getInt("sprites3") + ":" + definition.getInt("sprites4") + ":" + definition.getInt("sprites5")), "distinct Fledgling appearance " + id);
		}
		String clientDefinitions = new String(Files.readAllBytes(Paths.get("..", "Client_Base", "src", "com", "openrsc", "client", "entityhandling", "EntityHandler.java")), StandardCharsets.UTF_8);
		String clientShopInterface = new String(Files.readAllBytes(Paths.get("..", "Client_Base", "src", "com", "openrsc", "interfaces", "misc", "DoSkillInterface.java")), StandardCharsets.UTF_8);
		assertTrue(clientDefinitions.contains("new int[]{6, 1, 2, -1, 109, 70, 45"), "existing Dwarf proves head sprite 6 in slot zero");
		assertTrue(clientDefinitions.contains("new int[]{19, 34, 43, -1, 49"), "existing White Knight proves plate body and legs in shirt/pants slots");
		assertTrue(clientDefinitions.contains("new int[]{6, 27, 36, 97, 47, -1, -1, -1"), "client Hobart bronze plate composition");
		assertTrue(clientDefinitions.contains("new int[]{3, 4, 2, -1, -1, -1, -1, 87"), "existing Gardener proves female head, body, and legs slots");
		assertTrue(clientDefinitions.contains("new AnimationDef(\"fplatemailtop\", \"equipment\", 15654365"), "female iron plate-top animation exists");
		assertTrue(clientDefinitions.contains("new int[]{3, 54, 2, -1, 108, -1, -1, -1"), "client Fledgling associate female bronze plate composition");
		assertTrue(clientDefinitions.contains("\"Fledgling Slayer Associate\", \"A Fledgling Slayer supplier\""), "client associate display name");
		assertTrue(clientDefinitions.contains("new int[]{7, 27, 2, -1, 115, -1, -1, -1"), "client Fledgling ambient bronze plate composition");
		assertTrue(clientDefinitions.contains("new int[]{3, 55, 37, 98, 48, -1, -1, -1"), "client female Adept leader iron plate composition");
		assertTrue(clientDefinitions.contains("new int[]{6, 28, 2, -1, 109, -1, -1, -1"), "client Adept associate iron plate composition");
		assertTrue(clientDefinitions.contains("new int[]{7, 28, 2, -1, 116, -1, -1, -1"), "client Adept ambient iron plate composition");
		assertTrue(clientDefinitions.contains("\"Adept Slayer Associate\", \"An Adept Slayer supplier\""), "client Adept associate display name");
		assertTrue(clientDefinitions.contains("\"Adept Monster Slayer\", \"A practical hunter\""), "client Adept ambient display name");
		assertTrue(clientShopInterface.contains("{\"Fledgling\", \"Adept\", \"Veteran\", \"Elite\", \"Champion\", \"Hero\"}"), "client point-shop labels present Adept");
		assertAdeptPresentationParity(definitions);
		assertNoFledglingRoamAreaIntersectsWorldGeometry(server, locations, "conf/server/defs/locs");
		for (MonsterSlayerDialoguePlan.Step step : MonsterSlayerDialoguePlan.promotion(0)) assertTrue(step.getText().length() <= 48, "short Hobart promotion line: " + step.getText());
		assertEquals("I brought you an Asgarnian ale.", com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerContactService.risingSunAleOfferLabel(267), "natural Asgarnian response");
		assertEquals("I brought you a Wizard's mind bomb.", com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerContactService.risingSunAleOfferLabel(268), "natural Wizard response");
		assertEquals("I brought you a Dwarven stout.", com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerContactService.risingSunAleOfferLabel(269), "natural Dwarven response");
		assertEquals("Right, show me your stamp then to prove your membership.", MonsterSlayerContacts.hobartMembershipProofLines()[0], "Hobart requests proof");
		assertEquals("My thirst! Quickly! A drink!", MonsterSlayerContacts.hobartMembershipProofLines()[5], "Hobart gives first task");
		for (String line : MonsterSlayerContacts.hobartMembershipProofLines()) assertTrue(line.length() <= 80, "single-thought Hobart membership line: " + line);
		assertEquals("Splendid! Now hold out your hand.", MonsterSlayerContacts.hobartDrinkReturnLines()[0], "Hobart receives drink");
		assertEquals("The most official of stamps. Welcome aboard.", MonsterSlayerContacts.hobartDrinkReturnLines()[1], "Hobart enrolls recruit");
	}

	private static void assertAdeptPresentationParity(JSONArray definitions) {
		int[][] expected = {
			{847, 3, 55, 37, 98, 48}, // female head, female iron plate, iron legs, shield, sword
			{853, 6, 28, 2, -1, 109}, // male head, iron plate, legs, no shield, iron weapon
			{859, 7, 28, 2, -1, 116} // bearded head, iron plate, legs, no shield, iron weapon
		};
		java.util.Set<String> appearances = new java.util.HashSet<String>();
		for (int[] fixture : expected) {
			JSONObject npc = location(definitions, fixture[0]);
			for (int layer = 1; layer <= 5; layer++) assertEquals(fixture[layer], npc.getInt("sprites" + layer), "Adept effective layer " + fixture[0] + "/" + layer);
			assertTrue(appearances.add(npc.getInt("sprites1") + ":" + npc.getInt("sprites2") + ":" + npc.getInt("sprites3") + ":" + npc.getInt("sprites4") + ":" + npc.getInt("sprites5")), "distinct Adept appearance " + fixture[0]);
		}
		assertEquals("Adept Slayer Associate", location(definitions, 853).getString("name"), "server Adept associate display name");
		assertEquals("Adept Monster Slayer", location(definitions, 859).getString("name"), "server Adept ambient display name");
	}

	private static void assertNoFledglingRoamAreaIntersectsWorldGeometry(Server server, JSONObject locations, String directory) throws Exception {
		try (java.nio.file.DirectoryStream<java.nio.file.Path> files = Files.newDirectoryStream(Paths.get(directory), "*SceneryLocs*.json")) {
			for (java.nio.file.Path file : files) {
				JSONObject scenery = new JSONObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
				for (Object value : scenery.getJSONArray("sceneries")) {
					JSONObject entry = (JSONObject) value;
					assertFledglingRangeDoesNotIntersect(locations, entry.getJSONObject("pos").getInt("X"), entry.getJSONObject("pos").getInt("Y"), entry.getInt("id"), entry.getInt("direction"), server, "scenery " + file);
				}
			}
		}
		try (java.nio.file.DirectoryStream<java.nio.file.Path> files = Files.newDirectoryStream(Paths.get(directory), "*BoundaryLocs*.json")) {
			for (java.nio.file.Path file : files) {
				JSONObject boundaries = new JSONObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
				for (Object value : boundaries.getJSONArray("boundaries")) {
					JSONObject entry = (JSONObject) value;
					assertFledglingRangeDoesNotIntersect(locations, entry.getJSONObject("pos").getInt("X"), entry.getJSONObject("pos").getInt("Y"), entry.getInt("id"), entry.getInt("direction"), null, "boundary " + file);
				}
			}
		}
	}

	private static void assertVeteranRangeDoesNotIntersectWorldGeometry(Server server, JSONObject locations, String directory) throws Exception {
		for (String pattern : new String[] {"*SceneryLocs*.json", "*BoundaryLocs*.json"}) {
			try (java.nio.file.DirectoryStream<java.nio.file.Path> files = Files.newDirectoryStream(Paths.get(directory), pattern)) {
				for (java.nio.file.Path file : files) {
					JSONObject objects = new JSONObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
					String collection = pattern.startsWith("*Scenery") ? "sceneries" : "boundaries";
					for (Object value : objects.getJSONArray(collection)) {
						JSONObject entry = (JSONObject) value;
						int width = 1, height = 1;
						if ("sceneries".equals(collection)) {
							com.openrsc.server.external.GameObjectDef object = server.getEntityHandler().getGameObjectDef(entry.getInt("id"));
							if (object == null) throw new AssertionError("Unknown scenery definition " + entry.getInt("id"));
							int direction = entry.getInt("direction");
							width = direction == 0 || direction == 4 ? object.getWidth() : object.getHeight();
							height = direction == 0 || direction == 4 ? object.getHeight() : object.getWidth();
						}
						assertRangesDoNotIntersect(locations, new int[] {848, 854, 860},
							entry.getJSONObject("pos").getInt("X"), entry.getJSONObject("pos").getInt("Y"),
							width, height, "Veteran roam intersects " + file + " id=" + entry.getInt("id"));
					}
				}
			}
		}
		java.util.Set<String> veteranStarts = new java.util.HashSet<String>();
		for (int id : new int[] {848, 854, 860}) {
			JSONObject start = location(locations.getJSONArray("npclocs"), id).getJSONObject("start");
			veteranStarts.add(start.getInt("X") + "," + start.getInt("Y"));
		}
		try (java.nio.file.DirectoryStream<java.nio.file.Path> files = Files.newDirectoryStream(Paths.get(directory), "NpcLocs*.json")) {
			for (java.nio.file.Path file : files) {
				JSONObject npcs = new JSONObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
				for (Object value : npcs.getJSONArray("npclocs")) {
					JSONObject entry = (JSONObject) value, start = entry.getJSONObject("start");
					assertFalse(veteranStarts.contains(start.getInt("X") + "," + start.getInt("Y")),
						"Veteran start does not overlap existing NPC " + entry.getInt("id") + " in " + file);
				}
			}
		}
	}

	private static void assertRangesDoNotIntersect(JSONObject locations, int[] npcIds,
			int x, int y, int width, int height, String label) {
		for (int npcId : npcIds) {
			JSONObject range = location(locations.getJSONArray("npclocs"), npcId);
			JSONObject min = range.getJSONObject("min"), max = range.getJSONObject("max");
			assertTrue(max.getInt("X") < x || min.getInt("X") >= x + width
				|| max.getInt("Y") < y || min.getInt("Y") >= y + height, label + " npc=" + npcId);
		}
	}

	private static void assertFledglingRangeDoesNotIntersect(JSONObject locations, int x, int y, int id, int direction, Server server, String source) {
		int width = 1, height = 1;
		if (server != null) {
			com.openrsc.server.external.GameObjectDef object = server.getEntityHandler().getGameObjectDef(id);
			if (object == null) throw new AssertionError("Unknown scenery definition " + id);
			width = direction == 0 || direction == 4 ? object.getWidth() : object.getHeight();
			height = direction == 0 || direction == 4 ? object.getHeight() : object.getWidth();
		}
		for (int fledgling : new int[] {846, 852, 858}) {
			JSONObject range = location(locations.getJSONArray("npclocs"), fledgling);
			JSONObject min = range.getJSONObject("min"), max = range.getJSONObject("max");
			assertTrue(max.getInt("X") < x || min.getInt("X") >= x + width || max.getInt("Y") < y || min.getInt("Y") >= y + height,
				"Fledgling roam intersects " + source + " id=" + id);
		}
	}

	private static JSONObject location(JSONArray entries, int id) {
		for (int index = 0; index < entries.length(); index++) {
			JSONObject entry = entries.getJSONObject(index);
			if (entry.getInt("id") == id) return entry;
		}
		throw new AssertionError("Missing Monster Slayer location " + id);
	}

	private static void developmentCompletionUsesNormalSlayerProgression(Server server) throws Exception {
		MonsterSlayerData data = MonsterSlayerData.load(Paths.get("conf", "server", "defs", "extras", "MonsterSlayer.json"), new MonsterSlayerData.ReferenceCatalog() { public boolean npcExists(int id) { return true; } public boolean npcAttackable(int id) { return true; } public boolean npcSpawned(int id) { return true; } public boolean itemExists(int id) { return true; }});
		MonsterSlayerTaskService tasks = new MonsterSlayerTaskService(data);
		install(server, "monsterSlayerData", data); install(server, "monsterSlayerTaskService", tasks);
		Player developer = player(server, "slayerdevcomplete", 260, 600);
		developer.setGroupID(Group.DEV);
		MonsterSlayerState.Snapshot enrolled = MonsterSlayerState.completeIntroduction(MonsterSlayerState.beginIntroduction(MonsterSlayerState.defaults(data), data), data);
		MonsterSlayerState.write(developer.getCache(), data, enrolled);
		Map<com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge, Long> startingBalances = MonsterSlayerState.read(developer.getCache(), data).getBalances().asMap();
		assertTrue(tasks.assignMandatory(developer, "falador").isAccepted(), "preparation fixture has an incompatible active task");
		Development.prepareMonsterSlayerRankTasksForDevelopment(developer);
		MonsterSlayerState.Snapshot prepared = MonsterSlayerState.read(developer.getCache(), data);
		assertEquals(MonsterSlayerRank.FLEDGLING, prepared.getRank(), "prepare command never promotes Fledgling");
		assertEquals(data.getContact("falador").getMandatoryTasks().size() - 1, prepared.getMandatoryCursors().get("falador").intValue(), "prepare command leaves only final Fledgling task");
		assertTrue(prepared.getActiveTaskKey() == null, "prepare command clears incompatible active task");
		assertEquals(startingBalances, prepared.getBalances().asMap(), "prepare command grants no points");
		assertTrue(tasks.assignMandatory(developer, "falador").isAccepted(), "prepared Fledgling contact assigns final mandatory task normally");
		MonsterSlayerState.Snapshot finalAssigned = MonsterSlayerState.read(developer.getCache(), data);
		assertEquals(data.getContact("falador").getMandatoryTasks().get(data.getContact("falador").getMandatoryTasks().size() - 1).getKey(), finalAssigned.getActiveTaskKey(), "prepared contact chooses exact final task");
		MonsterSlayerState.TaskResult finalCompletion = tasks.completeActiveTaskForDevelopment(developer);
		assertEquals(MonsterSlayerState.TaskResult.Reason.COMPLETED, finalCompletion.getReason(), "normal final-task completion remains available after preparation");
		assertEquals(MonsterSlayerRank.INITIATE, MonsterSlayerState.read(developer.getCache(), data).getRank(), "only final normal completion promotes to Adept");
		MonsterSlayerState.write(developer.getCache(), data, enrolled);
		assertTrue(tasks.assignMandatory(developer, "falador").isAccepted(), "developer fixture task assigns");
		Development.completeMonsterSlayerTaskForDevelopment(developer);
		MonsterSlayerState.Snapshot completed = MonsterSlayerState.read(developer.getCache(), data);
		assertTrue(completed.getActiveTaskKey() == null, "developer command clears active task through normal completion");
		assertEquals(1L, completed.getTasksCompleted(), "developer command counts exactly one completion");
		assertEquals(2L, completed.getBalances().get(com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge.FLEDGLING), "developer command awards declared points");
		Development.completeMonsterSlayerTaskForDevelopment(developer);
		assertEquals(1L, MonsterSlayerState.read(developer.getCache(), data).getTasksCompleted(), "developer command cannot duplicate an inactive task");
		Map<com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge, Long> pointsBeforeRankUp = MonsterSlayerState.read(developer.getCache(), data).getBalances().asMap();
		Development.advanceMonsterSlayerRankForDevelopment(developer);
		MonsterSlayerState.Snapshot initiate = MonsterSlayerState.read(developer.getCache(), data);
		assertEquals(MonsterSlayerRank.INITIATE, initiate.getRank(), "rankup advances exactly one rank");
		assertEquals(data.getContact("falador").getMandatoryTasks().size(), initiate.getMandatoryCursors().get("falador").intValue(), "rankup completes only skipped contact cursor");
		assertEquals(pointsBeforeRankUp, initiate.getBalances().asMap(), "rankup awards no skipped task points");
		for (int rank = MonsterSlayerRank.INITIATE.getCode(); rank < MonsterSlayerRank.LEGEND.getCode(); rank++) Development.advanceMonsterSlayerRankForDevelopment(developer);
		MonsterSlayerState.Snapshot legend = MonsterSlayerState.read(developer.getCache(), data);
		assertEquals(MonsterSlayerRank.LEGEND, legend.getRank(), "repeated rankup reaches legend coherently");
		Map<String, Object> legendBefore = new LinkedHashMap<String, Object>(developer.getCache().getCacheMap());
		Development.advanceMonsterSlayerRankForDevelopment(developer);
		assertEquals(legendBefore, developer.getCache().getCacheMap(), "maximum rankup leaves state untouched");
		Map<com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge, Long> balancesBeforeSet = MonsterSlayerState.read(developer.getCache(), data).getBalances().asMap();
		Development.setMonsterSlayerPointsForDevelopment(developer, "setslayerpoints", new String[] {"hero", "123"});
		Map<com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge, Long> balancesAfterSet = MonsterSlayerState.read(developer.getCache(), data).getBalances().asMap();
		assertEquals(123L, balancesAfterSet.get(com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge.HERO), "point command sets exact selected balance");
		for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge challenge : com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge.values()) if (challenge != com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge.HERO) assertEquals(balancesBeforeSet.get(challenge), balancesAfterSet.get(challenge), "point command preserves balance " + challenge);
		Map<String, Object> validPointState = new LinkedHashMap<String, Object>(developer.getCache().getCacheMap());
		Development.setMonsterSlayerPointsForDevelopment(developer, "setslayerpoints", new String[] {"hero", "-1"});
		Development.setMonsterSlayerPointsForDevelopment(developer, "setslayerpoints", new String[] {"unknown", "1"});
		Development.setMonsterSlayerPointsForDevelopment(developer, "setslayerpoints", new String[] {"hero", "2000000001"});
		assertEquals(validPointState, developer.getCache().getCacheMap(), "invalid point commands leave all state untouched");

		Player ordinary = player(server, "slayernocommand", 261, 600);
		MonsterSlayerState.write(ordinary.getCache(), data, enrolled);
		assertTrue(tasks.assignMandatory(ordinary, "falador").isAccepted(), "ordinary fixture task assigns");
		Development.completeMonsterSlayerTaskForDevelopment(ordinary);
		assertTrue(MonsterSlayerState.read(ordinary.getCache(), data).getActiveTaskKey() != null, "non-developer command cannot mutate Slayer task state");
		Map<String, Object> ordinaryBefore = new LinkedHashMap<String, Object>(ordinary.getCache().getCacheMap());
		Development.advanceMonsterSlayerRankForDevelopment(ordinary);
		Development.setMonsterSlayerPointsForDevelopment(ordinary, "setslayerpoints", new String[] {"fledgling", "999"});
		Development.prepareMonsterSlayerRankTasksForDevelopment(ordinary);
		assertEquals(ordinaryBefore, ordinary.getCache().getCacheMap(), "non-developer economy commands cannot mutate Slayer state");
	}

	private static void contactProofDialogueIsClearAndRankGated(Server server) {
		assertEquals("Are you here to slay monsters?", MonsterSlayerContacts.contactGreeting(1), "Mara asks why the player came");
		assertEquals("Yes, I am.", MonsterSlayerContacts.contactChoices(1)[0], "Mara yes response is natural player speech");
		assertEquals("No, not today.", MonsterSlayerContacts.contactChoices(1)[1], "Mara no response ends naturally");
		assertEquals("Let's see that Adept sticker.", MonsterSlayerContacts.contactProof(1), "Mara requests the Adept proof");
		assertEquals("Oh, I don't have one.", MonsterSlayerContacts.missingProofResponse(1), "Mara proof refusal includes the player's missing-sticker reply");
		assertEquals("You need an Adept sticker first. Hobart in Falador can help.", MonsterSlayerContacts.contactRefusal(1), "Mara directs ineligible players to Hobart");
		assertTrue(MonsterSlayerContacts.contactRefusal(2).contains("Mara in Port Sarim"), "later contacts direct players to the previous giver");
		assertEquals("That was your final Fledgling task.", MonsterSlayerDialoguePlan.promotion(0).get(0).getText(), "Fledgling final-task dialogue is unique");
		assertTrue(containsDialogue(MonsterSlayerDialoguePlan.promotion(0), "You are now an Adept."), "promotion states Adept rank");
		assertTrue(containsDialogue(MonsterSlayerDialoguePlan.promotion(0), "Here is your official Adept sticker."), "promotion gives official sticker");
		assertTrue(containsDialogue(MonsterSlayerDialoguePlan.promotion(0), "You can now access the Fledgling shop."), "promotion identifies the unlocked shop");
		assertTrue(containsDialogue(MonsterSlayerDialoguePlan.promotion(0), "Just speak with my associate over there."), "promotion directs the player to the associate");
		assertTrue(containsDialogue(MonsterSlayerDialoguePlan.promotion(0), "He knows a thing or two about satchels as well."), "promotion explains associate satchel expertise");
		assertFalse(containsDialogue(MonsterSlayerDialoguePlan.promotion(0), "You've earned Fledgling Slayer Points."), "promotion does not repeat the removed point line");
		assertFalse(containsDialogue(MonsterSlayerDialoguePlan.promotion(0), "My associate can trade them for supplies."), "promotion does not repeat the removed associate line");
		MonsterSlayerData data = server.getWorld().getMonsterSlayerData();
		MonsterSlayerState.Snapshot fledgling = MonsterSlayerState.completeIntroduction(MonsterSlayerState.beginIntroduction(MonsterSlayerState.defaults(data), data), data);
		assertEquals(MonsterSlayerState.TaskResult.Reason.RANK, MonsterSlayerState.assignMandatory(fledgling, data, "port_sarim").getReason(), "Fledgling cannot receive Adept work");
		Player ineligible = player(server, "maraproofdenied", 270, 600);
		MonsterSlayerState.write(ineligible.getCache(), data, fledgling);
		RecordingDialogue deniedDialogue = new RecordingDialogue(0);
		new MonsterSlayerContacts(deniedDialogue).onTalkNpc(ineligible, new Npc(server.getWorld(), 847, 271, 600));
		assertEquals(java.util.Arrays.asList("N:Are you here to slay monsters?", "P:Yes, I am.", "N:Let's see that Adept sticker.", "P:Oh, I don't have one.", "N:You need an Adept sticker first. Hobart in Falador can help."), deniedDialogue.events, "ineligible Mara Talk-to is greeting, response, proof, player acknowledgement, then refusal");
		Player declining = player(server, "maraproofno", 272, 600);
		MonsterSlayerState.write(declining.getCache(), data, fledgling);
		RecordingDialogue noDialogue = new RecordingDialogue(1);
		new MonsterSlayerContacts(noDialogue).onTalkNpc(declining, new Npc(server.getWorld(), 847, 273, 600));
		assertEquals(java.util.Arrays.asList("N:Are you here to slay monsters?", "P:No, not today."), noDialogue.events, "Mara no response ends before proof or refusal");
		Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
		for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) cursors.put(contact.getKey(), 0);
		cursors.put("falador", data.getContact("falador").getMandatoryTasks().size());
		MonsterSlayerState.Snapshot adept = MonsterSlayerState.create(2, MonsterSlayerRank.INITIATE, MonsterSlayerBalances.zero(), cursors, null, 0, 0L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data);
		assertEquals(MonsterSlayerState.TaskResult.Reason.ASSIGNED, MonsterSlayerState.assignMandatory(adept, data, "port_sarim").getReason(), "eligible Adept receives normal Port Sarim assignment");
		Player eligible = player(server, "maraproofyes", 274, 600);
		MonsterSlayerState.write(eligible.getCache(), data, adept);
		RecordingDialogue eligibleDialogue = new RecordingDialogue(0);
		new MonsterSlayerContacts(eligibleDialogue).onTalkNpc(eligible, new Npc(server.getWorld(), 847, 275, 600));
		assertEquals(java.util.Arrays.asList("N:Are you here to slay monsters?", "P:Yes, I am.", "N:Let's see that Adept sticker.", "P:Right here!", "N:Right, you must be the newest among the Adepts.", "N:Getting here means you can swing a sword.", "N:Better than a goblin can stab a spear.", "N:Glad to have you."), eligibleDialogue.events.subList(0, 8), "Mara first task welcome follows proof acknowledgement in exact order");
		assertTrue(eligibleDialogue.events.get(8).startsWith("N:Your next task is to slay "), "Mara welcome flows directly into first assignment text");
		assertTrue(MonsterSlayerState.read(eligible.getCache(), data).getActiveTaskKey() != null, "eligible Mara Talk-to proceeds into normal assignment");
		RecordingDialogue activeFirstDialogue = new RecordingDialogue(0);
		new MonsterSlayerContacts(activeFirstDialogue).onTalkNpc(eligible, new Npc(server.getWorld(), 847, 275, 600));
		assertFalse(containsAnyNpcLine(activeFirstDialogue.events, MonsterSlayerContacts.maraFirstTaskWelcome()), "assigned first task prevents Mara welcome from repeating");
		Player shortcutFirst = player(server, "marafirstshortcut", 274, 601);
		MonsterSlayerState.write(shortcutFirst.getCache(), data, adept);
		RecordingDialogue shortcutDialogue = new RecordingDialogue();
		new MonsterSlayerContacts(shortcutDialogue).onOpNpc(shortcutFirst, new Npc(server.getWorld(), 847, 275, 601), "Task");
		assertFalse(containsAnyNpcLine(shortcutDialogue.events, MonsterSlayerContacts.maraFirstTaskWelcome()), "Mara Task shortcut skips first-task social welcome");
		assertTrue(MonsterSlayerState.read(shortcutFirst.getCache(), data).getActiveTaskKey() != null, "Mara Task shortcut retains authoritative assignment");
		Player eligibleHobart = player(server, "hobartproofyes", 275, 600);
		MonsterSlayerState.write(eligibleHobart.getCache(), data, fledgling);
		RecordingDialogue hobartDialogue = new RecordingDialogue(0);
		new MonsterSlayerContacts(hobartDialogue).onTalkNpc(eligibleHobart, new Npc(server.getWorld(), 846, 276, 600));
		assertEquals(java.util.Arrays.asList("N:" + MonsterSlayerContacts.contactGreeting(0), "P:Yes please.", "N:" + MonsterSlayerContacts.contactProof(0), "P:Right here!"), hobartDialogue.events.subList(0, 4), "eligible Hobart Talk-to acknowledges proof before assignment");
		Player pendingPromotion = player(server, "hobartpromotionfirst", 276, 600);
		MonsterSlayerState.write(pendingPromotion.getCache(), data, MonsterSlayerState.create(2, MonsterSlayerRank.INITIATE, MonsterSlayerBalances.zero(), cursors, null, 0, 0L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data));
		RecordingDialogue promotionDialogue = new RecordingDialogue();
		new MonsterSlayerContacts(promotionDialogue).onTalkNpc(pendingPromotion, new Npc(server.getWorld(), 846, 277, 600));
		assertEquals("N:That was your final Fledgling task.", promotionDialogue.events.get(0), "pending promotion starts before normal greeting");
		assertFalse(promotionDialogue.events.contains("N:" + MonsterSlayerContacts.contactGreeting(0)), "pending promotion suppresses normal greeting");
		assertTrue(MonsterSlayerState.read(pendingPromotion.getCache(), data).isPromotionAcknowledged("falador", data), "promotion is acknowledged after its one interaction");
		assertTrue(MonsterSlayerState.read(pendingPromotion.getCache(), data).getActiveTaskKey() == null, "promotion interaction does not assign another task");
		RecordingDialogue afterPromotion = new RecordingDialogue(1);
		new MonsterSlayerContacts(afterPromotion).onTalkNpc(pendingPromotion, new Npc(server.getWorld(), 846, 278, 600));
		assertEquals(java.util.Arrays.asList("N:" + MonsterSlayerContacts.contactGreeting(0), "P:Not now."), afterPromotion.events, "acknowledged promotion does not repeat and normal dialogue resumes");
	}

	private static void maraAssignmentDialogueUsesAuthoritativeProgression(Server server) {
		MonsterSlayerData data = server.getWorld().getMonsterSlayerData();
		String[] welcome = MonsterSlayerContacts.maraFirstTaskWelcome();
		assertEquals(4, welcome.length, "Mara first-task welcome has exact bounded length");
		String[] expectedRemarks = {
			"Steady hands make lighter work.",
			"Take your time and do the job properly.",
			"Keep your footing. Strength is no use flat on your back.",
			"A hard day's work is still just a day. You'll manage.",
			"Pack what you need, and mind yourself out there."
		};
		for (int index = 0; index < expectedRemarks.length; index++) {
			String remark = MonsterSlayerContacts.maraAssignmentRemark(index);
			assertEquals(expectedRemarks[index], remark, "approved Mara assignment remark " + index);
			assertTrue(remark.length() <= 64, "Mara assignment remark fits dialogue " + index);
			for (int hobart = 0; hobart < 5; hobart++) assertFalse(remark.equals(MonsterSlayerContacts.hobartFollowUpRemark(hobart)), "Mara remark remains distinct from Hobart " + index + "/" + hobart);
		}
		assertThrows(new Runnable() { public void run() { MonsterSlayerContacts.maraAssignmentRemark(expectedRemarks.length); }}, "Mara remark selection is bounded");

		Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
		for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) cursors.put(contact.getKey(), 0);
		cursors.put("falador", data.getContact("falador").getMandatoryTasks().size());
		cursors.put("port_sarim", 1);
		MonsterSlayerState.Snapshot laterAdept = MonsterSlayerState.create(2, MonsterSlayerRank.INITIATE, MonsterSlayerBalances.zero(), cursors, null, 0, 1L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data);
		assertFalse(MonsterSlayerContacts.shouldUseMaraFirstTaskWelcome(1, laterAdept), "later mandatory cursor suppresses Mara welcome");
		assertTrue(MonsterSlayerContacts.shouldUseMaraAssignmentRemark(1, laterAdept), "later mandatory cursor enables Mara remark");
		Player later = player(server, "maralatermandatory", 280, 600);
		MonsterSlayerState.write(later.getCache(), data, laterAdept);
		RecordingDialogue laterDialogue = new RecordingDialogue(0);
		new MonsterSlayerContacts(laterDialogue).onTalkNpc(later, new Npc(server.getWorld(), 847, 281, 600));
		assertFalse(containsAnyNpcLine(laterDialogue.events, welcome), "later mandatory assignment never repeats Mara welcome");
		assertTrue(containsAnyNpcLine(laterDialogue.events, expectedRemarks), "later mandatory assignment includes one Mara remark");

		cursors.put("port_sarim", data.getContact("port_sarim").getMandatoryTasks().size());
		MonsterSlayerState.Snapshot repeatable = MonsterSlayerState.create(2, MonsterSlayerRank.VETERAN, MonsterSlayerBalances.zero(), cursors, null, 0, 9L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data);
		repeatable = MonsterSlayerState.acknowledgePromotion(repeatable, data, "port_sarim");
		assertFalse(MonsterSlayerContacts.shouldUseMaraFirstTaskWelcome(1, repeatable), "repeatable state suppresses Mara welcome");
		assertTrue(MonsterSlayerContacts.shouldUseMaraAssignmentRemark(1, repeatable), "repeatable state enables Mara remark");
		Player repeatPlayer = player(server, "mararepeatable", 282, 600);
		MonsterSlayerState.write(repeatPlayer.getCache(), data, repeatable);
		RecordingDialogue repeatDialogue = new RecordingDialogue(0);
		new MonsterSlayerContacts(repeatDialogue).onTalkNpc(repeatPlayer, new Npc(server.getWorld(), 847, 283, 600));
		assertFalse(containsAnyNpcLine(repeatDialogue.events, welcome), "repeatable assignment never uses Mara welcome");
		assertTrue(containsAnyNpcLine(repeatDialogue.events, expectedRemarks), "repeatable assignment includes one Mara remark");

		Player pending = player(server, "maraveteranpromotion", 284, 600);
		MonsterSlayerState.Snapshot pendingState = MonsterSlayerState.create(2, MonsterSlayerRank.VETERAN, MonsterSlayerBalances.zero(), cursors, null, 0, 9L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data);
		MonsterSlayerState.write(pending.getCache(), data, pendingState);
		RecordingDialogue promotion = new RecordingDialogue();
		new MonsterSlayerContacts(promotion).onTalkNpc(pending, new Npc(server.getWorld(), 847, 285, 600));
		assertEquals(java.util.Arrays.asList("N:Well that was it, the last one.", "N:At this point I'd say you've proven yourself.", "N:I award you Veteran status.", "N:Please accept this button as proof of your rank.", "P:I'm honored. Thank you.", "P:But um...", "P:Why does it say 'I heart PS'?", "N:To show your Port Sarim pride!", "P:Right, of course."), promotion.events, "Mara Veteran promotion has exact speaker and line order");
		MonsterSlayerState.Snapshot promoted = MonsterSlayerState.read(pending.getCache(), data);
		assertTrue(promoted.isPromotionAcknowledged("port_sarim", data), "Mara promotion is acknowledged after rendering");
		assertTrue(promoted.getActiveTaskKey() == null, "Mara promotion interception assigns no task");
		RecordingDialogue afterPromotion = new RecordingDialogue(1);
		new MonsterSlayerContacts(afterPromotion).onTalkNpc(pending, new Npc(server.getWorld(), 847, 286, 600));
		assertEquals(java.util.Arrays.asList("N:Are you here to slay monsters?", "P:No, not today."), afterPromotion.events, "acknowledged Mara promotion does not repeat");
	}

	private static void hazardWarningsAreNaturalOrderedDialogue(Server server) {
		assertEquals("You should expect Worship drain.", MonsterSlayerContacts.hazardWarningLine(
			com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.PRAYER_DRAIN),
			"Prayer-drain compatibility key is presented as Worship drain");
		assertEquals("You should prepare for the desert heat.", MonsterSlayerContacts.hazardWarningLine(
			com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.DESERT_HEAT),
			"desert heat warning");
		assertEquals("You should know this work is in the Wilderness.", MonsterSlayerContacts.hazardWarningLine(
			com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.WILDERNESS),
			"Wilderness warning");
		assertEquals("You should bring an antidote for poison.", MonsterSlayerContacts.hazardWarningLine(
			com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.POISON),
			"poison warning");
		assertEquals("You should prepare for dragon fire.", MonsterSlayerContacts.hazardWarningLine(
			com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.DRAGON_FIRE),
			"dragon-fire warning");

		MonsterSlayerData data = server.getWorld().getMonsterSlayerData();
		com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task multiHazard =
			data.getTask("heroes.green_dragons");
		assertEquals(java.util.Arrays.asList(
			"You should know this work is in the Wilderness.",
			"You should prepare for dragon fire."),
			java.util.Arrays.asList(MonsterSlayerContacts.hazardWarningLines(multiHazard)),
			"multiple hazards remain separate and preserve definition order");
		for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task task : data.getTasks()) {
			for (String warning : MonsterSlayerContacts.hazardWarningLines(task)) {
				assertFalse(warning.contains(":"), "generated Slayer warning has no colon for " + task.getKey());
				assertFalse(warning.contains(";"), "generated Slayer warning has no semicolon for " + task.getKey());
			}
		}

		Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
		int contactIndex = 0;
		for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) {
			cursors.put(contact.getKey(), contactIndex < 4 ? contact.getMandatoryTasks().size()
				: contactIndex == 4 ? 2 : 0);
			contactIndex++;
		}
		Player player = player(server, "slayermultiwarning", 287, 600);
		player.setQuestStage(Quests.HEROS_QUEST, -1);
		MonsterSlayerState.write(player.getCache(), data, MonsterSlayerState.create(2,
			MonsterSlayerRank.CHAMPION, MonsterSlayerBalances.zero(), cursors, null, 0,
			0L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data));
		RecordingDialogue dialogue = new RecordingDialogue();
		new MonsterSlayerContacts(dialogue).onOpNpc(player,
			new Npc(server.getWorld(), 850, 287, 600), "Task");
		assertEquals("N:You should know this work is in the Wilderness.", dialogue.events.get(0),
			"first hazard renders before assignment");
		assertEquals("N:You should prepare for dragon fire.", dialogue.events.get(1),
			"second hazard renders separately before assignment");
		assertEquals("N:Your next task is to slay 18 Green dragons.", dialogue.events.get(2),
			"task assignment follows every hazard warning");
		assertEquals("heroes.green_dragons", MonsterSlayerState.read(player.getCache(), data).getActiveTaskKey(),
			"warning presentation preserves authoritative assignment");

		for (int index = 0; index < 6; index++) {
			for (String line : MonsterSlayerContacts.associateSupplyLines(index)) {
				assertFalse(line.contains(":"), "associate supply dialogue has no list formatting " + index);
				assertFalse(line.contains(";"), "associate supply dialogue has no joined thoughts " + index);
			}
		}
	}

	private static void branDialogueUsesAuthoritativeVeteranProgression(Server server) {
		MonsterSlayerData data = server.getWorld().getMonsterSlayerData();
		assertEquals("Button?", MonsterSlayerContacts.contactProof(2), "Bran restores natural proof punctuation");
		assertTrue(MonsterSlayerContacts.contactProof(2).endsWith("?"), "question marks remain valid dialogue punctuation");
		String[] welcome = MonsterSlayerContacts.branFirstTaskWelcome();
		assertEquals(java.util.Arrays.asList(
			"Hah! A new Veteran!",
			"Veterans are the best of the best!",
			"Let's see if you can prove it."), java.util.Arrays.asList(welcome),
			"Bran first-task welcome is exact and boisterous");
		for (String line : welcome) {
			assertFalse(line.contains(":"), "Bran welcome avoids list-like colon formatting");
			assertFalse(line.contains(";"), "Bran welcome avoids joined semicolon formatting");
		}
		assertTrue(welcome[0].contains("!"), "natural exclamation punctuation remains valid");

		Map<String, Integer> firstCursors = veteranCursors(data, 0);
		MonsterSlayerState.Snapshot firstState = MonsterSlayerState.create(2, MonsterSlayerRank.VETERAN,
			MonsterSlayerBalances.zero(), firstCursors, null, 0, 0L, 0, 1,
			MonsterSlayerState.LegacyStatus.NONE, 0, data);
		assertTrue(MonsterSlayerContacts.shouldUseBranFirstTaskWelcome(2, firstState),
			"authoritative Veteran cursor zero enables Bran welcome");
		assertFalse(MonsterSlayerContacts.shouldUseBranAssignmentRemark(2, firstState),
			"first Veteran assignment keeps its established welcome without an extra random remark");
		Player first = player(server, "slayerbranfirst", 288, 600);
		MonsterSlayerState.write(first.getCache(), data, firstState);
		RecordingDialogue firstDialogue = new RecordingDialogue(0);
		new MonsterSlayerContacts(firstDialogue).onTalkNpc(first, new Npc(server.getWorld(), 848, 288, 600));
		assertEquals(java.util.Arrays.asList(
			"N:" + MonsterSlayerContacts.contactGreeting(2),
			"P:Yes please.",
			"N:Button?",
			"P:Right here!",
			"N:Hah! A new Veteran!",
			"N:Veterans are the best of the best!",
			"N:Let's see if you can prove it."), firstDialogue.events.subList(0, 7),
			"Bran welcome follows proof and precedes first assignment");
		assertEquals("N:Your next task is to slay 45 Jogres.", firstDialogue.events.get(7),
			"Bran first assignment follows welcome");
		assertEquals("brimhaven.jogres", MonsterSlayerState.read(first.getCache(), data).getActiveTaskKey(),
			"Bran welcome preserves authoritative first assignment");

		RecordingDialogue activeDialogue = new RecordingDialogue(0);
		new MonsterSlayerContacts(activeDialogue).onTalkNpc(first, new Npc(server.getWorld(), 848, 289, 600));
		assertFalse(containsAnyNpcLine(activeDialogue.events, welcome), "active first task suppresses Bran welcome repeat");

		Player shortcut = player(server, "slayerbranshortcut", 290, 600);
		MonsterSlayerState.write(shortcut.getCache(), data, firstState);
		RecordingDialogue shortcutDialogue = new RecordingDialogue();
		new MonsterSlayerContacts(shortcutDialogue).onOpNpc(shortcut, new Npc(server.getWorld(), 848, 290, 600), "Task");
		assertFalse(containsAnyNpcLine(shortcutDialogue.events, welcome), "Bran Task shortcut skips first-task social welcome");
		assertTrue(MonsterSlayerState.read(shortcut.getCache(), data).getActiveTaskKey() != null,
			"Bran shortcut retains authoritative assignment");

		MonsterSlayerState.Snapshot laterState = MonsterSlayerState.create(2, MonsterSlayerRank.VETERAN,
			MonsterSlayerBalances.zero(), veteranCursors(data, 1), null, 0, 1L, 0, 1,
			MonsterSlayerState.LegacyStatus.NONE, 0, data);
		assertFalse(MonsterSlayerContacts.shouldUseBranFirstTaskWelcome(2, laterState),
			"later Veteran cursor suppresses Bran welcome");
		assertTrue(MonsterSlayerContacts.shouldUseBranAssignmentRemark(2, laterState),
			"later Veteran work enables Bran assignment flavour");
		assertFalse(MonsterSlayerContacts.shouldUseBranAssignmentRemark(1, laterState),
			"Bran assignment flavour remains contact-specific");

		String[] generalRemarks = {
			"Now that's work worthy of a Veteran!",
			"Ha! Show them why we're the best!",
			"Make it loud enough to hear from the Blue Moon!",
			"A proper hunt! I almost envy you!",
			"Go on! Give me something worth boasting about!"
		};
		com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task jogres =
			data.getTask("brimhaven.jogres");
		for (int selection = 0; selection < generalRemarks.length; selection++) {
			assertEquals(generalRemarks[selection], MonsterSlayerContacts.branAssignmentRemark(jogres, selection),
				"hazard-free Bran remark remains in the bounded personality set " + selection);
			String normalized = generalRemarks[selection].toLowerCase();
			for (String unsupportedFact : new String[] {"antidote", "desert heat", "wilderness", "worship drain", "dragon fire"}) {
				assertFalse(normalized.contains(unsupportedFact),
					"hazard-free Bran flavour does not invent preparation facts " + unsupportedFact);
			}
			assertTrue(generalRemarks[selection].length() <= 64,
				"Bran personality line remains concise " + selection);
		}
		assertEquals(generalRemarks[0], MonsterSlayerContacts.branAssignmentRemark(jogres, generalRemarks.length),
			"Bran personality selection wraps within its bounded set");
		assertThrows(new Runnable() { public void run() { MonsterSlayerContacts.branAssignmentRemark(jogres, -1); }},
			"negative Bran dialogue selection is rejected");

		String[] hazardTaskKeys = {
			"falador.desert_wolves", "falador.black_unicorns", "port_sarim.shadow_spiders",
			"brimhaven.poison_spiders", "port_sarim.baby_blue_dragons"
		};
		String[][] hazardRemarks = {
			{"Hot work! Prepare for the desert heat!", "The desert heat is part of the fight! Prepare for it!"},
			{"Wilderness work! Keep your wits about you!", "You're headed into the Wilderness! Go prepared!"},
			{"They drain Worship! Plan your supplies around it!", "Expect Worship drain! Don't rely on full Worship points!"},
			{"Poison on this one! Bring an antidote and keep swinging!", "Pack an antidote! Be ready before the poison sets in!"},
			{"Dragon fire! Take the right protection!", "Expect dragon fire! Prepare before you face it!"}
		};
		for (int taskIndex = 0; taskIndex < hazardTaskKeys.length; taskIndex++) {
			com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task task =
				data.getTask(hazardTaskKeys[taskIndex]);
			for (int selection = 0; selection < hazardRemarks[taskIndex].length; selection++) {
				assertEquals(hazardRemarks[taskIndex][selection],
					MonsterSlayerContacts.branAssignmentRemark(task, selection),
					"Bran advice derives from authoritative hazard metadata " + hazardTaskKeys[taskIndex] + "/" + selection);
				assertTrue(hazardRemarks[taskIndex][selection].length() <= 64,
					"Bran preparation advice remains concise " + hazardTaskKeys[taskIndex] + "/" + selection);
			}
		}
		com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task multipleHazards =
			data.getTask("heroes.green_dragons");
		assertEquals(hazardRemarks[1][0], MonsterSlayerContacts.branAssignmentRemark(multipleHazards, 0),
			"first declared hazard selects Wilderness advice");
		assertEquals(hazardRemarks[4][0], MonsterSlayerContacts.branAssignmentRemark(multipleHazards, 1),
			"second declared hazard selects dragon-fire advice");
		assertEquals(hazardRemarks[1][1], MonsterSlayerContacts.branAssignmentRemark(multipleHazards, 2),
			"multi-hazard random selection remains bounded across advice variants");

		Player later = player(server, "slayerbranlater", 293, 600);
		MonsterSlayerState.write(later.getCache(), data, laterState);
		RecordingDialogue laterDialogue = new RecordingDialogue(0);
		new MonsterSlayerContacts(laterDialogue).onTalkNpc(later, new Npc(server.getWorld(), 848, 293, 600));
		assertFalse(containsAnyNpcLine(laterDialogue.events, welcome),
			"later Bran assignment does not repeat the first-task welcome");
		assertTrue(containsAnyNpcLine(laterDialogue.events, generalRemarks),
			"later hazard-free Bran assignment uses exactly one bounded personality remark");
		assertEquals("N:Your next task is to slay 40 Karamja wolves.",
			laterDialogue.events.get(laterDialogue.events.size() - 1),
			"Bran flavour preserves the authoritative task assignment line");

		MonsterSlayerState.Snapshot poisonState = MonsterSlayerState.create(2, MonsterSlayerRank.VETERAN,
			MonsterSlayerBalances.zero(), veteranCursors(data, 3), null, 0, 3L, 0, 1,
			MonsterSlayerState.LegacyStatus.NONE, 0, data);
		Player poison = player(server, "slayerbranpoison", 294, 600);
		MonsterSlayerState.write(poison.getCache(), data, poisonState);
		RecordingDialogue poisonDialogue = new RecordingDialogue(0);
		new MonsterSlayerContacts(poisonDialogue).onTalkNpc(poison, new Npc(server.getWorld(), 848, 294, 600));
		int warningIndex = poisonDialogue.events.indexOf("N:You should bring an antidote for poison.");
		int assignmentIndex = poisonDialogue.events.indexOf("N:Your next task is to slay 35 Poison spiders.");
		assertTrue(warningIndex >= 0 && assignmentIndex == warningIndex + 2,
			"authoritative poison warning, Bran advice, and assignment retain their order");
		assertTrue(poisonDialogue.events.get(warningIndex + 1).equals("N:" + hazardRemarks[3][0])
			|| poisonDialogue.events.get(warningIndex + 1).equals("N:" + hazardRemarks[3][1]),
			"poison metadata selects only accurate antidote advice");

		Map<String, Integer> promotedCursors = veteranCursors(data,
			data.getContact("brimhaven").getMandatoryTasks().size());
		Player promoted = player(server, "slayerbranpromotion", 291, 600);
		MonsterSlayerState.write(promoted.getCache(), data, MonsterSlayerState.create(2,
			MonsterSlayerRank.ELITE, MonsterSlayerBalances.zero(), promotedCursors, null, 0,
			6L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data));
		RecordingDialogue promotion = new RecordingDialogue();
		new MonsterSlayerContacts(promotion).onTalkNpc(promoted, new Npc(server.getWorld(), 848, 291, 600));
		assertEquals(java.util.Arrays.asList(
			"N:Hah! You did it! Every last task!",
			"N:You've earned Elite rank.",
			"N:Take this badge.",
			"N:But listen.",
			"N:The fun is over now.",
			"N:Elite work begins inside the true guilds.",
			"N:Not everyone comes back from that work.",
			"N:And come back any time to slay more with",
			"N:The best of the best!"), promotion.events,
			"Bran promotion drops the bluster and warns about inner-guild work");
		assertTrue(MonsterSlayerState.read(promoted.getCache(), data).isPromotionAcknowledged("brimhaven", data),
			"Bran promotion is acknowledged after exact dialogue");
		assertFalse(promotion.events.contains("N:" + MonsterSlayerContacts.contactGreeting(2)),
			"pending Bran promotion intercepts normal greeting");
		MonsterSlayerState.Snapshot acknowledged = MonsterSlayerState.read(promoted.getCache(), data);
		assertFalse(MonsterSlayerContacts.shouldUseBranFirstTaskWelcome(2, acknowledged),
			"completed Veteran chain cannot repeat first-task welcome");
		RecordingDialogue afterPromotion = new RecordingDialogue(1);
		new MonsterSlayerContacts(afterPromotion).onTalkNpc(promoted, new Npc(server.getWorld(), 848, 292, 600));
		assertEquals(java.util.Arrays.asList("N:" + MonsterSlayerContacts.contactGreeting(2), "P:Not now."),
			afterPromotion.events, "acknowledged Bran promotion does not repeat and normal route resumes");
	}

	private static void doranDialogueUsesAuthoritativeEliteProgression(Server server) {
		MonsterSlayerData data = server.getWorld().getMonsterSlayerData();
		java.util.List<MonsterSlayerDialoguePlan.Step> welcome = MonsterSlayerDialoguePlan.doranFirstTaskWelcome();
		assertDialoguePlan(welcome, new MonsterSlayerDialoguePlan.Speaker[] {
			MonsterSlayerDialoguePlan.Speaker.NPC,
			MonsterSlayerDialoguePlan.Speaker.NPC,
			MonsterSlayerDialoguePlan.Speaker.PLAYER,
			MonsterSlayerDialoguePlan.Speaker.NPC,
			MonsterSlayerDialoguePlan.Speaker.NPC
		}, new String[] {
			"Welcome to your first true stint in a guild sect.",
			"You're part of the Champions now!",
			"Than-",
			"You're welcome! Best not dilly-dally.",
			"Monsters won't be slaying themselves."
		}, "Doran first-task welcome");

		MonsterSlayerState.Snapshot firstState = MonsterSlayerState.create(2, MonsterSlayerRank.ELITE,
			MonsterSlayerBalances.zero(), eliteCursors(data, 0), null, 0, 0L, 0, 1,
			MonsterSlayerState.LegacyStatus.NONE, 0, data);
		assertTrue(MonsterSlayerContacts.shouldUseDoranFirstTaskWelcome(3, firstState),
			"authoritative Champions cursor zero enables Doran welcome");
		assertFalse(MonsterSlayerContacts.shouldUseDoranAssignmentRemark(3, firstState),
			"Doran first assignment does not add a random remark to its welcome");
		Player first = player(server, "slayerdoranfirst", 300, 600);
		first.setQuestPoints(32);
		MonsterSlayerState.write(first.getCache(), data, firstState);
		RecordingDialogue firstDialogue = new RecordingDialogue(0);
		new MonsterSlayerContacts(firstDialogue).onTalkNpc(first,
			new Npc(server.getWorld(), 849, 300, 600));
		assertEquals(java.util.Arrays.asList(
			"N:" + MonsterSlayerContacts.contactGreeting(3),
			"P:Yes please.",
			"N:Badge, if you please!",
			"P:Right here!",
			"N:Welcome to your first true stint in a guild sect.",
			"N:You're part of the Champions now!",
			"P:Than-",
			"N:You're welcome! Best not dilly-dally.",
			"N:Monsters won't be slaying themselves.",
			"N:Your next task is to slay 40 Ice giants."), firstDialogue.events,
			"Doran typed welcome follows proof and preserves first assignment");
		assertEquals("champions.ice_giants",
			MonsterSlayerState.read(first.getCache(), data).getActiveTaskKey(),
			"Doran welcome preserves authoritative Champions progression");

		Player shortcut = player(server, "slayerdoranshortcut", 301, 600);
		shortcut.setQuestPoints(32);
		MonsterSlayerState.write(shortcut.getCache(), data, firstState);
		RecordingDialogue shortcutDialogue = new RecordingDialogue();
		new MonsterSlayerContacts(shortcutDialogue).onOpNpc(shortcut,
			new Npc(server.getWorld(), 849, 301, 600), "Task");
		assertEquals(java.util.Arrays.asList("N:Your next task is to slay 40 Ice giants."),
			shortcutDialogue.events, "Doran Task shortcut skips the social welcome and random flavour");

		String[] generalRemarks = {
			"Right! Keep steady, finish the job, and report back!",
			"Good! Straight to it, then straight back!",
			"Ha! A fine assignment for an Elite!",
			"No need for speeches! You know the work!",
			"Off you go! We'll celebrate when it's done!"
		};
		com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task iceGiants =
			data.getTask("champions.ice_giants");
		for (int selection = 0; selection < generalRemarks.length; selection++) {
			assertEquals(generalRemarks[selection],
				MonsterSlayerContacts.doranAssignmentRemark(iceGiants, selection),
				"bounded Doran personality remark " + selection);
			assertTrue(generalRemarks[selection].length() <= 64,
				"Doran personality remark remains concise " + selection);
			String normalized = generalRemarks[selection].toLowerCase();
			for (String unsupportedFact : new String[] {
				"antidote", "desert heat", "wilderness", "worship drain", "dragon fire"
			}) assertFalse(normalized.contains(unsupportedFact),
				"hazard-free Doran flavour does not invent preparation advice " + unsupportedFact);
		}
		assertEquals(generalRemarks[0],
			MonsterSlayerContacts.doranAssignmentRemark(iceGiants, generalRemarks.length),
			"Doran personality selection wraps within its bounded set");
		assertThrows(new Runnable() { public void run() {
			MonsterSlayerContacts.doranAssignmentRemark(iceGiants, -1);
		}}, "negative Doran dialogue selection is rejected");

		String[] hazardTaskKeys = {
			"falador.desert_wolves", "falador.black_unicorns", "port_sarim.shadow_spiders",
			"brimhaven.poison_spiders", "port_sarim.baby_blue_dragons"
		};
		String[][] hazardRemarks = {
			{"Desert heat! Prepare for it, then get moving!", "Hot work ahead! Pack for the desert heat!"},
			{"Wilderness work! Stay sharp and get it done!", "Into the Wilderness! Keep your wits about you!"},
			{"Worship drain! Plan your supplies around it!", "Expect Worship drain! Prepare, then press on!"},
			{"Poison! Bring an antidote and you'll manage!", "Pack an antidote! No sense losing to poison!"},
			{"Dragon fire! Take the right protection!", "Expect dragon fire! Prepare before you charge in!"}
		};
		for (int taskIndex = 0; taskIndex < hazardTaskKeys.length; taskIndex++) {
			com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task task =
				data.getTask(hazardTaskKeys[taskIndex]);
			for (int selection = 0; selection < hazardRemarks[taskIndex].length; selection++)
				assertEquals(hazardRemarks[taskIndex][selection],
					MonsterSlayerContacts.doranAssignmentRemark(task, selection),
					"Doran advice derives from authoritative hazard metadata "
						+ hazardTaskKeys[taskIndex] + "/" + selection);
		}
		com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task multipleHazards =
			data.getTask("heroes.green_dragons");
		assertEquals(hazardRemarks[1][0], MonsterSlayerContacts.doranAssignmentRemark(multipleHazards, 0),
			"Doran uses the first declared hazard for the first deterministic selection");
		assertEquals(hazardRemarks[4][0], MonsterSlayerContacts.doranAssignmentRemark(multipleHazards, 1),
			"Doran uses the second declared hazard for the second deterministic selection");

		MonsterSlayerState.Snapshot laterState = MonsterSlayerState.create(2, MonsterSlayerRank.ELITE,
			MonsterSlayerBalances.zero(), eliteCursors(data, 1), null, 0, 1L, 0, 1,
			MonsterSlayerState.LegacyStatus.NONE, 0, data);
		Player later = player(server, "slayerdoranlater", 302, 600);
		later.setQuestPoints(32);
		MonsterSlayerState.write(later.getCache(), data, laterState);
		RecordingDialogue laterDialogue = new RecordingDialogue(0);
		new MonsterSlayerContacts(laterDialogue).onTalkNpc(later,
			new Npc(server.getWorld(), 849, 302, 600));
		assertFalse(containsAnyNpcLine(laterDialogue.events,
			new String[] {"Welcome to your first true stint in a guild sect."}),
			"later Doran assignment does not repeat the first-task welcome");
		assertTrue(containsAnyNpcLine(laterDialogue.events, generalRemarks),
			"later Doran assignment uses one bounded personality remark");
		assertEquals("N:Your next task is to slay 30 Lesser demons.",
			laterDialogue.events.get(laterDialogue.events.size() - 1),
			"Doran flavour preserves authoritative later assignment");

		Map<String, Integer> promotedCursors = eliteCursors(data,
			data.getContact("champions").getMandatoryTasks().size());
		Player promoted = player(server, "slayerdoranpromotion", 303, 600);
		promoted.setQuestPoints(32);
		MonsterSlayerState.write(promoted.getCache(), data, MonsterSlayerState.create(2,
			MonsterSlayerRank.CHAMPION, MonsterSlayerBalances.zero(), promotedCursors,
			null, 0, 3L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data));
		RecordingDialogue promotion = new RecordingDialogue();
		new MonsterSlayerContacts(promotion).onTalkNpc(promoted,
			new Npc(server.getWorld(), 849, 303, 600));
		assertEquals(java.util.Arrays.asList(
			"N:'Grats on making it this far!",
			"N:I knew you had it in you.",
			"P:Than-",
			"N:Best not keep the Heroes' sect waiting.",
			"N:I present to you the latest and greatest.",
			"N:Monster Slayer Guild Medal!",
			"P:...",
			"N:Well, aren't you going to say thank you?",
			"P:Th-",
			"N:Off you go!"), promotion.events,
			"Doran promotion preserves exact interruption speakers and order");
		assertTrue(MonsterSlayerState.read(promoted.getCache(), data)
			.isPromotionAcknowledged("champions", data),
			"Doran promotion is acknowledged after exact dialogue");
		assertTrue(MonsterSlayerState.read(promoted.getCache(), data).getActiveTaskKey() == null,
			"Doran promotion interaction assigns no task");

		Player associatePlayer = player(server, "slayerdoranassociate", 304, 600);
		associatePlayer.setQuestPoints(32);
		MonsterSlayerState.write(associatePlayer.getCache(), data, MonsterSlayerState.create(2,
			MonsterSlayerRank.CHAMPION, MonsterSlayerBalances.zero(), promotedCursors,
			null, 0, 3L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data));
		RecordingDialogue associate = new RecordingDialogue(2);
		new MonsterSlayerContacts(associate).onTalkNpc(associatePlayer,
			new Npc(server.getWorld(), 855, 304, 600));
		assertEquals(java.util.Arrays.asList(
			"N:Doran is a nice guy, but you can never get a word in edgewise.",
			"N:A Champion is welcome at this quartermaster's counter.",
			"P:No thanks."), associate.events,
			"Elite associate prepends the approved Doran observation");
	}

	private static Map<String, Integer> eliteCursors(MonsterSlayerData data, int eliteCursor) {
		Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
		for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Contact contact
				: data.getContactsInChallengeOrder()) {
			if ("falador".equals(contact.getKey()) || "port_sarim".equals(contact.getKey())
					|| "brimhaven".equals(contact.getKey()))
				cursors.put(contact.getKey(), contact.getMandatoryTasks().size());
			else if ("champions".equals(contact.getKey())) cursors.put(contact.getKey(), eliteCursor);
			else cursors.put(contact.getKey(), 0);
		}
		return cursors;
	}

	private static void assertDialoguePlan(java.util.List<MonsterSlayerDialoguePlan.Step> actual,
			MonsterSlayerDialoguePlan.Speaker[] speakers, String[] text, String context) {
		assertEquals(speakers.length, actual.size(), context + " step count");
		assertEquals(speakers.length, text.length, context + " fixture count");
		for (int index = 0; index < speakers.length; index++) {
			assertEquals(speakers[index], actual.get(index).getSpeaker(), context + " speaker " + index);
			assertEquals(text[index], actual.get(index).getText(), context + " text " + index);
		}
	}

	private static Map<String, Integer> veteranCursors(MonsterSlayerData data, int veteranCursor) {
		Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
		for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) {
			if ("falador".equals(contact.getKey()) || "port_sarim".equals(contact.getKey())) cursors.put(contact.getKey(), contact.getMandatoryTasks().size());
			else if ("brimhaven".equals(contact.getKey())) cursors.put(contact.getKey(), veteranCursor);
			else cursors.put(contact.getKey(), 0);
		}
		return cursors;
	}

	private static boolean containsAnyNpcLine(java.util.List<String> events, String[] lines) {
		for (String line : lines) if (events.contains("N:" + line)) return true;
		return false;
	}

	private static final class RecordingDialogue implements MonsterSlayerContacts.DialogueRenderer {
		private final java.util.List<String> events = new java.util.ArrayList<String>();
		private final int[] selections;
		private int selectionIndex;
		private RecordingDialogue(int... selections) { this.selections = selections; }
		public boolean render(Player player, Npc npc, MonsterSlayerDialoguePlan.Step step) { events.add((step.getSpeaker() == MonsterSlayerDialoguePlan.Speaker.NPC ? "N:" : "P:") + step.getText()); return true; }
		public void npc(Player player, Npc npc, String... text) { for (String line : text) events.add("N:" + line); }
		public void player(Player player, Npc npc, String text) { events.add("P:" + text); }
		public int choose(Player player, String... choices) { return selections[selectionIndex++]; }
		public void pause() { events.add("D:1"); }
	}

	private static boolean containsDialogue(java.util.List<MonsterSlayerDialoguePlan.Step> steps, String expected) {
		for (MonsterSlayerDialoguePlan.Step step : steps) if (expected.equals(step.getText())) return true;
		return false;
	}

	private static void associateOperationsAndWorldRestockAreBounded(Server server) {
		for (int index = 0; index < 6; index++) {
			assertTrue(MonsterSlayerContacts.isAssociateShopOperation("Trade"), "associate trade operation " + index);
			assertTrue(MonsterSlayerContacts.isAssociateShopOperation("Shop"), "associate shop operation " + index);
			assertFalse(MonsterSlayerContacts.isAssociateShopOperation("Task"), "associate task is not shop operation " + index);
			assertTrue(MonsterSlayerContacts.associateGreeting(index).length() > 0, "associate talk dialogue " + index);
			assertTrue(MonsterSlayerContacts.associateSupplyLine(index).length() > 0, "associate supply dialogue " + index);
		}
		MonsterSlayerData data = server.getWorld().getMonsterSlayerData();
		for (int index = 0; index < data.getShops().size(); index++) {
			com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Shop shop = data.getShops().get(index);
			long price = shop.getCapacityUpgrade().getCost().get(shop.getChallenge());
			String quote = MonsterSlayerContacts.satchelUpgradeCostLine(price, shop.getChallenge());
			assertTrue(quote.contains(" " + price + " "), "associate quote uses exact own-tier price " + shop.getKey());
			assertTrue(quote.contains(shop.getChallenge().getDisplayName().toLowerCase() + " coins"), "associate quote names own tier currency " + shop.getKey());
		}
		MonsterSlayerShopService service = new MonsterSlayerShopService(data);
		String reward = "falador.brawn";
		assertEquals(-1, service.getStock(reward), "Slayer reward stock is infinite");
		try {
			Field serviceField = server.getWorld().getClass().getDeclaredField("monsterSlayerShopService");
			serviceField.setAccessible(true); serviceField.set(server.getWorld(), service);
		} catch (Exception failure) { throw new AssertionError("install world-owned shop service", failure); }
		service.restock(); // compatibility event has no stock work for infinite rewards
		assertEquals(-1, service.getStock(reward), "restock preserves infinite stock");
		MonsterSlayerShopRestockEvent event = new MonsterSlayerShopRestockEvent(server.getWorld());
		event.run();
		assertEquals(-1, service.getStock(reward), "world event preserves infinite stock");
		assertEquals(MonsterSlayerShopRestockEvent.INTERVAL_MS, 60000L, "stable world restock interval");
	}

	private static void fledglingAssociateSatchelDialogueAndPurchase(Server server) {
		MonsterSlayerData data = server.getWorld().getMonsterSlayerData();
		Npc associate = new Npc(server.getWorld(), 852, 241, 600);
		Player locked = player(server, "slayersatchellocked", 241, 600);
		MonsterSlayerState.Snapshot fledgling = MonsterSlayerState.completeIntroduction(
			MonsterSlayerState.beginIntroduction(MonsterSlayerState.defaults(data), data), data);
		MonsterSlayerState.write(locked.getCache(), data, fledgling);
		RecordingDialogue lockedDialogue = new RecordingDialogue();
		new MonsterSlayerContacts(lockedDialogue).onTalkNpc(locked, associate);
		assertEquals(java.util.Arrays.asList(
			"N:Sorry, you gotta get a promotion before I can sell you anything.",
			"N:Them's the rules."), lockedDialogue.events, "Fledgling associate promotion gate is concise");

		Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
		for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) cursors.put(contact.getKey(), 0);
		cursors.put("falador", data.getContact("falador").getMandatoryTasks().size());
		Player greeting = adeptPlayer(server, data, "slayersatchelgreeting", 242, cursors, MonsterSlayerBalances.zero());
		RecordingDialogue greetingDialogue = new RecordingDialogue(2);
		new MonsterSlayerContacts(greetingDialogue).onTalkNpc(greeting, associate);
		assertEquals(java.util.Arrays.asList(
			"N:Congratulations on becoming an Adept.",
			"N:I can show you my wares now.",
			"N:Or perhaps you'd like an upgrade to your satchel?",
			"P:No thanks."), greetingDialogue.events, "unlocked Fledgling associate greeting and spoken decline");

		Player insufficient = adeptPlayer(server, data, "slayersatchelpoor", 243, cursors, MonsterSlayerBalances.zero());
		RecordingDialogue insufficientDialogue = new RecordingDialogue(1, 0);
		new MonsterSlayerContacts(insufficientDialogue).onTalkNpc(insufficient, associate);
		assertEquals(30, MonsterSlayerState.read(insufficient.getCache(), data).getDerivedInventoryCapacity(), "insufficient purchase keeps base capacity");
		assertEquals(java.util.Arrays.asList(
			"N:Congratulations on becoming an Adept.",
			"N:I can show you my wares now.",
			"N:Or perhaps you'd like an upgrade to your satchel?",
			"P:Can you upgrade my satchel?",
			"N:I can, but it'll cost you 84 fledgling coins.",
			"N:I can only do one upgrade per satchel as well.",
			"P:Totally worth it.",
			"N:Sorry, but you don't have enough to cover the cost."), insufficientDialogue.events,
			"insufficient Fledgling satchel flow quotes before authoritative rejection");

		MonsterSlayerBalances exactPrice = MonsterSlayerBalances.zero().credit(
			com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge.FLEDGLING, 84L);
		Player declined = adeptPlayer(server, data, "slayersatcheldecline", 244, cursors, exactPrice);
		RecordingDialogue declinedDialogue = new RecordingDialogue(1, 1);
		new MonsterSlayerContacts(declinedDialogue).onTalkNpc(declined, associate);
		assertEquals(30, MonsterSlayerState.read(declined.getCache(), data).getDerivedInventoryCapacity(), "declined purchase keeps base capacity");
		assertEquals(84L, MonsterSlayerState.read(declined.getCache(), data).getBalances().get(
			com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge.FLEDGLING), "declined purchase spends nothing");
		assertTrue(declinedDialogue.events.contains("P:No thanks."), "satchel decline is spoken player dialogue");

		Player buyer = adeptPlayer(server, data, "slayersatchelbuyer", 245, cursors, exactPrice);
		RecordingDialogue purchaseDialogue = new RecordingDialogue(1, 0);
		new MonsterSlayerContacts(purchaseDialogue).onTalkNpc(buyer, associate);
		MonsterSlayerState.Snapshot purchased = MonsterSlayerState.read(buyer.getCache(), data);
		assertEquals(31, purchased.getDerivedInventoryCapacity(), "successful Fledgling satchel purchase adds one slot");
		assertEquals(0L, purchased.getBalances().get(com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge.FLEDGLING), "successful Fledgling satchel purchase deducts exact price");
		assertEquals(java.util.Arrays.asList(
			"N:Congratulations on becoming an Adept.",
			"N:I can show you my wares now.",
			"N:Or perhaps you'd like an upgrade to your satchel?",
			"P:Can you upgrade my satchel?",
			"N:I can, but it'll cost you 84 fledgling coins.",
			"N:I can only do one upgrade per satchel as well.",
			"P:Totally worth it.",
			"N:Okay, hold on while I stitch this.",
			"D:1",
			"N:Done! I'm sure you can fit at least one more thing now."), purchaseDialogue.events,
			"successful Fledgling satchel purchase preserves exact speaker and pause order");

		RecordingDialogue duplicateDialogue = new RecordingDialogue(1);
		new MonsterSlayerContacts(duplicateDialogue).onTalkNpc(buyer, associate);
		assertTrue(duplicateDialogue.events.contains("N:Looks like I already did this upgrade."), "duplicate satchel purchase is identified before confirmation");
		assertEquals(31, MonsterSlayerState.read(buyer.getCache(), data).getDerivedInventoryCapacity(), "duplicate satchel inquiry changes nothing");
	}

	private static Player adeptPlayer(Server server, MonsterSlayerData data, String name, int x,
			Map<String, Integer> cursors, MonsterSlayerBalances balances) {
		Player player = player(server, name, x, 600);
		MonsterSlayerState.write(player.getCache(), data, MonsterSlayerState.create(2,
			MonsterSlayerRank.INITIATE, balances, cursors, null, 0, 0L, 0, 1,
			MonsterSlayerState.LegacyStatus.NONE, 0, data));
		return player;
	}

	private static void aleFailureMessagesRemainTruthful() {
		assertEquals("You haven't got a Rising Sun ale yet. Visit the barmaid and come back.", MonsterSlayerContacts.aleFailureMessage("missing-rising-sun-ale"), "missing Rising Sun ale message");
		assertEquals("Your Rising Sun ale was returned, but your Monster Slayer rank could not be recorded. Please try again.", MonsterSlayerContacts.aleFailureMessage("state-write-failed"), "state-write refund message");
		assertEquals("Your rank record failed and your Rising Sun ale could not be returned. Please contact staff.", MonsterSlayerContacts.aleFailureMessage("refund-failed"), "refund failure message");
		assertEquals("Your Monster Slayer record needs staff attention.", MonsterSlayerContacts.aleFailureMessage("invalid-state"), "invalid state message");
	}

	private static void hobartFollowUpDialogueIsBoundedAndTaskIndependent() {
		String[] expected = {
			"Right then, back to it. Try not to make a spectacle of yourself.",
			"Good. I was starting to think you'd gone soft.",
			"Come back for more work anytime.",
			"There's always another mess needing a capable pair of hands.",
			"That's the spirit. Don't keep the monsters waiting."
		};
		for (int index = 0; index < expected.length; index++) {
			assertEquals(expected[index], MonsterSlayerContacts.hobartFollowUpRemark(index),
				"bounded Hobart follow-up line " + index);
			assertTrue(expected[index].length() <= 255, "Hobart follow-up line protocol bound " + index);
		}
		assertThrows(new Runnable() { public void run() {
			MonsterSlayerContacts.hobartFollowUpRemark(expected.length);
		}}, "out-of-range Hobart dialogue is rejected");
		assertFalse(MonsterSlayerContacts.shouldUseHobartFollowUpRemark(0, 0L),
			"first Hobart task remains fully deterministic");
		assertTrue(MonsterSlayerContacts.shouldUseHobartFollowUpRemark(0, 1L),
			"completed Hobart work enables a bounded follow-up remark");
		assertFalse(MonsterSlayerContacts.shouldUseHobartFollowUpRemark(1, 10L),
			"other contacts keep their existing dialogue");
	}

	private static void shopPresentationUsesTypedCostsAndTruthfulFailures(Server server) {
		MonsterSlayerData data = server.getWorld().getMonsterSlayerData();
		MonsterSlayerState.Snapshot state = MonsterSlayerState.defaults(data);
		for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Shop shop : data.getShops()) {
			for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Category category : shop.getCategories()) {
				for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Reward reward : category.getRewards()) {
					String summary = MonsterSlayerChallengeShops.costSummary(reward, 2L, state);
					for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge challenge : com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge.values()) {
						long component = reward.getCost().get(challenge);
						if (component > 0L) assertTrue(summary.contains((component * 2L) + " " + challenge.getDisplayName() + " points (you: 0)"), "typed double cost " + reward.getKey() + " " + challenge);
					}
				}
			}
		}
		assertEquals("You do not have all of the required challenge points for that.", MonsterSlayerChallengeShops.redemptionFailureMessage("points"), "typed point failure");
		assertEquals("That reward is sold out or its stock changed.", MonsterSlayerChallengeShops.redemptionFailureMessage("stock"), "stale stock failure");
		assertEquals("You do not have enough inventory space for that.", MonsterSlayerChallengeShops.redemptionFailureMessage("inventory"), "inventory failure");
		assertEquals("Choose a valid smaller quantity.", MonsterSlayerChallengeShops.redemptionFailureMessage("quantity"), "quantity failure");
		assertEquals("The reward could not be delivered. Your points and stock were restored.", MonsterSlayerChallengeShops.redemptionFailureMessage("grant"), "rollback failure");
	}
	private static void guildAccessModesAndQuestStates(Server server) {
		Player player = player(server, "slayerguildaccess", 206, 600);
		player.setQuestPoints(31);
		server.getConfig().INFLUENCE_INSTEAD_QP = false;
		assertFalse(MonsterSlayerGuildAccess.allows(player, 3), "Champions quest-point lock");
		player.setQuestPoints(32);
		assertTrue(MonsterSlayerGuildAccess.allows(player, 3), "Champions quest-point access");
		player.setQuestStage(Quests.HEROS_QUEST, 1);
		assertFalse(MonsterSlayerGuildAccess.allows(player, 4), "Heroes incomplete lock");
		player.setQuestStage(Quests.HEROS_QUEST, -1);
		assertTrue(MonsterSlayerGuildAccess.allows(player, 4), "Heroes complete access");
		player.setQuestStage(Quests.LEGENDS_QUEST, 10);
		assertFalse(MonsterSlayerGuildAccess.allows(player, 5), "Legends incomplete lock");
		player.setQuestStage(Quests.LEGENDS_QUEST, 11);
		assertTrue(MonsterSlayerGuildAccess.allows(player, 5), "Legends in-progress access");
		player.setQuestStage(Quests.LEGENDS_QUEST, -1);
		assertTrue(MonsterSlayerGuildAccess.allows(player, 5), "Legends complete access");
	}

	private static void shortcutAssignmentAndPromotionAreRealInteractions(Server server, MonsterSlayerContacts routes) throws Exception {
		MonsterSlayerData data = MonsterSlayerData.load(Paths.get("conf", "server", "defs", "extras", "MonsterSlayer.json"), new MonsterSlayerData.ReferenceCatalog() { public boolean npcExists(int id) { return true; } public boolean npcAttackable(int id) { return true; } public boolean npcSpawned(int id) { return true; } public boolean itemExists(int id) { return true; }});
		install(server, "monsterSlayerData", data); install(server, "monsterSlayerTaskService", new MonsterSlayerTaskService(data));
		Player fledgling = player(server, "slayerroutef", 200, 600);
		MonsterSlayerState.Snapshot fresh = MonsterSlayerState.defaults(data);
		MonsterSlayerState.write(fledgling.getCache(), data, MonsterSlayerState.completeIntroduction(MonsterSlayerState.beginIntroduction(fresh, data), data));
		routes.onOpNpc(fledgling, new Npc(server.getWorld(), 846, 201, 600), "Task");
		assertTrue(MonsterSlayerState.read(fledgling.getCache(), data).getActiveTaskKey() != null, "Task interaction assigns a real task");

		Player promoted = player(server, "slayerroutei", 202, 600);
		Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
		for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) cursors.put(contact.getKey(), 0);
		cursors.put("falador", data.getContact("falador").getMandatoryTasks().size());
		MonsterSlayerState.write(promoted.getCache(), data, MonsterSlayerState.create(2, MonsterSlayerRank.INITIATE, MonsterSlayerBalances.zero(), cursors, null, 0, 0L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data));
		final java.util.List<MonsterSlayerDialoguePlan.Step> rendered = new java.util.ArrayList<MonsterSlayerDialoguePlan.Step>();
		routes = new MonsterSlayerContacts(new MonsterSlayerContacts.DialogueRenderer() { public boolean render(Player player, Npc npc, MonsterSlayerDialoguePlan.Step step) { rendered.add(step); return true; }});
		routes.onOpNpc(promoted, new Npc(server.getWorld(), 846, 203, 600), "Task");
		MonsterSlayerState.Snapshot after = MonsterSlayerState.read(promoted.getCache(), data);
		assertTrue(after.isPromotionAcknowledged("falador", data), "promotion dialogue interaction is acknowledged once");
		assertTrue(after.getActiveTaskKey() == null, "promotion acknowledgement ends the interaction without assigning work");
		assertEquals(MonsterSlayerDialoguePlan.promotion(0).size(), rendered.size(), "promotion renders every planned step");
		for (int step = 0; step < rendered.size(); step++) assertTrue(rendered.get(step).getText().length() <= 255, "promotion line bound " + step);
		Player aborted = player(server, "slayerrouteabort", 204, 600);
		MonsterSlayerState.write(aborted.getCache(), data, MonsterSlayerState.create(2, MonsterSlayerRank.INITIATE, MonsterSlayerBalances.zero(), cursors, null, 0, 0L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data));
		new MonsterSlayerContacts(new MonsterSlayerContacts.DialogueRenderer() { public boolean render(Player player, Npc npc, MonsterSlayerDialoguePlan.Step step) { return false; }}).onOpNpc(aborted, new Npc(server.getWorld(), 846, 205, 600), "Task");
		assertFalse(MonsterSlayerState.read(aborted.getCache(), data).isPromotionAcknowledged("falador", data), "aborted promotion is never acknowledged");
	}

	private static void promotionRenderingIsExactForEveryRank(Server server) throws Exception {
		MonsterSlayerData data = server.getWorld().getMonsterSlayerData();
		for (int index = 0; index < data.getContactsInChallengeOrder().size(); index++) {
			final java.util.List<MonsterSlayerDialoguePlan.Step> rendered = new java.util.ArrayList<MonsterSlayerDialoguePlan.Step>();
			MonsterSlayerContacts routes = new MonsterSlayerContacts(new MonsterSlayerContacts.DialogueRenderer() { public boolean render(Player player, Npc npc, MonsterSlayerDialoguePlan.Step step) { rendered.add(step); return true; }});
			com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Contact contact = data.getContactsInChallengeOrder().get(index);
			Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
			for (int cursor = 0; cursor < data.getContactsInChallengeOrder().size(); cursor++) {
				com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Contact candidate = data.getContactsInChallengeOrder().get(cursor);
				cursors.put(candidate.getKey(), cursor <= index ? candidate.getMandatoryTasks().size() : 0);
			}
			Player player = player(server, "slayerpromotion" + index, 220 + index, 600);
			player.setQuestPoints(32); player.setQuestStage(Quests.HEROS_QUEST, -1); player.setQuestStage(Quests.LEGENDS_QUEST, -1);
			MonsterSlayerState.write(player.getCache(), data, MonsterSlayerState.create(2, contact.getAwardedRank(), MonsterSlayerBalances.zero(), cursors, null, 0, 0L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data));
			routes.onOpNpc(player, new Npc(server.getWorld(), 846 + index, 220 + index, 600), "Task");
			java.util.List<MonsterSlayerDialoguePlan.Step> expected = MonsterSlayerDialoguePlan.promotion(index);
			assertEquals(expected.size(), rendered.size(), "promotion renders exact step count " + contact.getKey());
			for (int step = 0; step < expected.size(); step++) {
				assertEquals(expected.get(step).getText(), rendered.get(step).getText(), "promotion exact text " + contact.getKey() + " step " + step);
				assertEquals(expected.get(step).getSpeaker(), rendered.get(step).getSpeaker(), "promotion exact speaker " + contact.getKey() + " step " + step);
				assertTrue(rendered.get(step).getText().length() <= 255, "promotion bounded line " + contact.getKey() + " step " + step);
			}
			assertTrue(MonsterSlayerState.read(player.getCache(), data).isPromotionAcknowledged(contact.getKey(), data), "promotion acknowledged after complete render " + contact.getKey());
		}
	}

	private static void associateAmbientAndOwnershipBoundaries(Server server, MonsterSlayerContacts routes) {
		MonsterSlayerData data = server.getWorld().getMonsterSlayerData();
		Player player = player(server, "slayerassociate", 240, 600);
		Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
		for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) cursors.put(contact.getKey(), 0);
		cursors.put("falador", data.getContact("falador").getMandatoryTasks().size());
		MonsterSlayerState.write(player.getCache(), data, MonsterSlayerState.create(2, MonsterSlayerRank.INITIATE, MonsterSlayerBalances.zero(), cursors, null, 0, 0L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data));
		Map<String, Object> before = new LinkedHashMap<String, Object>(player.getCache().getCacheMap());
		routes.onOpNpc(player, new Npc(server.getWorld(), 852, 241, 600), "Trade");
		assertEquals(before, player.getCache().getCacheMap(), "unlocked associate has no progression authority");
		routes.onTalkNpc(player, new Npc(server.getWorld(), 858, 242, 600));
		assertEquals(before, player.getCache().getCacheMap(), "ambient NPC has no progression authority");

		Player denied = player(server, "slayerassociatedenied", 243, 600);
		for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) cursors.put(contact.getKey(), contact.getMandatoryTasks().size());
		MonsterSlayerState.write(denied.getCache(), data, MonsterSlayerState.create(2, MonsterSlayerRank.LEGEND, MonsterSlayerBalances.zero(), cursors, null, 0, 0L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data));
		Map<String, Object> deniedBefore = new LinkedHashMap<String, Object>(denied.getCache().getCacheMap());
		server.getConfig().INFLUENCE_INSTEAD_QP = false; denied.setQuestPoints(0);
		routes.onOpNpc(denied, new Npc(server.getWorld(), 855, 244, 600), "Trade");
		assertEquals(deniedBefore, denied.getCache().getCacheMap(), "guild-denied associate has no progression authority");

		for (int id : new int[] {111, 253, 735, 785}) {
			Npc npc = new Npc(server.getWorld(), id, 245, 600);
			assertFalse(routes.blockTalkNpc(player, npc), "unrelated NPC talk ownership " + id);
			assertFalse(routes.blockOpNpc(player, npc, "Task"), "unrelated NPC task ownership " + id);
			routes.onOpNpc(player, npc, "Task");
			assertEquals(before, player.getCache().getCacheMap(), "unrelated NPC cannot mutate Slayer state " + id);
		}
	}

	private static void install(Server server, String name, Object value) throws Exception { Field field = server.getWorld().getClass().getDeclaredField(name); field.setAccessible(true); field.set(server.getWorld(), value); }

	private static Player player(Server server, String name, int x, int y) {
		Player player = new Player(server.getWorld(), DataConversions.usernameToHash(name));
		player.setClientVersion(server.getConfig().CLIENT_VERSION);
		player.setClientLimitations(ClientLimitations.forVersion(server.getConfig().CLIENT_VERSION));
		player.setInitialLocation(Point.location(x, y));
		server.getWorld().getPlayers().add(player); player.updateRegion(); player.setBusy(false); player.setLoggedIn(true);
		return player;
	}

	private static void assertTrue(boolean value, String label) { if (!value) throw new AssertionError(label); }
	private static void assertFalse(boolean value, String label) { assertTrue(!value, label); }
	private static void assertThrows(Runnable action, String label) {
		try { action.run(); } catch (IllegalArgumentException expected) { return; }
		throw new AssertionError(label);
	}
	private static void assertEquals(int expected, int actual, String label) { if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", got " + actual); }
	private static void assertEquals(String expected, String actual, String label) { if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual); }
	private static void assertEquals(Map<String, Object> expected, Map<String, Object> actual, String label) { if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual); }
	private static void assertEquals(Object expected, Object actual, String label) { if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual); }
}
