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
		veteranHeadquartersUsesBlueMoonInnPlacement();
		fledglingPresentationAndRoamingContractIsValid(server);
		adeptRankKeepsLegacyPersistenceCompatibility();
		menuResponsesRemainVisiblePlayerSpeech();
		contactProofDialogueIsClearAndRankGated(server);
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
	private static void veteranHeadquartersUsesBlueMoonInnPlacement() throws Exception {
		JSONObject locations = new JSONObject(new String(Files.readAllBytes(Paths.get(
			"conf", "server", "defs", "locs", "MyWorldNpcLocs.json")), StandardCharsets.UTF_8));
		int[][] expected = {{848, 118, 517}, {854, 120, 517}, {860, 122, 517}};
		for (int[] fixture : expected) {
			JSONObject entry = location(locations.getJSONArray("npclocs"), fixture[0]);
			JSONObject start = entry.getJSONObject("start");
			assertEquals(fixture[1], start.getInt("X"), "Blue Moon X " + fixture[0]);
			assertEquals(fixture[2], start.getInt("Y"), "Blue Moon Y " + fixture[0]);
			assertTrue(entry.getJSONObject("min").getInt("X") < entry.getJSONObject("max").getInt("X")
				|| entry.getJSONObject("min").getInt("Y") < entry.getJSONObject("max").getInt("Y"), "Blue Moon NPC roams " + fixture[0]);
		}
		JSONArray definitions = new JSONObject(new String(Files.readAllBytes(Paths.get(
			"conf", "server", "defs", "MonsterSlayerNpcDefs.json")), StandardCharsets.UTF_8)).getJSONArray("npcs");
		for (int id : new int[] {848, 854, 860}) assertTrue(location(definitions, id).getString("description").contains("Blue Moon"),
			"Blue Moon NPC description " + id);
		assertEquals("Veteran Slayer Associate", location(definitions, 854).getString("name"),
			"Veteran associate is identifiable at Blue Moon");
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
		assertEquals("You need an Adept sticker first. Hobart in Falador can help.", MonsterSlayerContacts.contactRefusal(1), "Mara directs ineligible players to Hobart");
		assertTrue(MonsterSlayerContacts.contactRefusal(2).contains("Mara in Port Sarim"), "later contacts direct players to the previous giver");
		assertEquals("That was your final Fledgling task.", MonsterSlayerDialoguePlan.promotion(0).get(0).getText(), "Fledgling final-task dialogue is unique");
		assertTrue(containsDialogue(MonsterSlayerDialoguePlan.promotion(0), "You are now an Adept."), "promotion states Adept rank");
		assertTrue(containsDialogue(MonsterSlayerDialoguePlan.promotion(0), "Here is your official Adept sticker."), "promotion gives official sticker");
		MonsterSlayerData data = server.getWorld().getMonsterSlayerData();
		MonsterSlayerState.Snapshot fledgling = MonsterSlayerState.completeIntroduction(MonsterSlayerState.beginIntroduction(MonsterSlayerState.defaults(data), data), data);
		assertEquals(MonsterSlayerState.TaskResult.Reason.RANK, MonsterSlayerState.assignMandatory(fledgling, data, "port_sarim").getReason(), "Fledgling cannot receive Adept work");
		Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
		for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) cursors.put(contact.getKey(), 0);
		cursors.put("falador", data.getContact("falador").getMandatoryTasks().size());
		MonsterSlayerState.Snapshot adept = MonsterSlayerState.create(2, MonsterSlayerRank.INITIATE, MonsterSlayerBalances.zero(), cursors, null, 0, 0L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data);
		assertEquals(MonsterSlayerState.TaskResult.Reason.ASSIGNED, MonsterSlayerState.assignMandatory(adept, data, "port_sarim").getReason(), "eligible Adept receives normal Port Sarim assignment");
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
		int[] capacities = {31, 32, 33, 35, 37, 40};
		for (int index = 0; index < data.getShops().size(); index++) {
			com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Shop shop = data.getShops().get(index);
			long price = shop.getCapacityUpgrade().getCost().get(shop.getChallenge());
			String quote = MonsterSlayerContacts.backpackUpgradeQuote(index == 0 ? 30 : capacities[index - 1],
				capacities[index], price, shop.getChallenge());
			assertTrue(quote.contains(" " + price + " "), "associate quote uses exact own-tier price " + shop.getKey());
			assertTrue(quote.contains(" to " + capacities[index] + " slots"), "associate quote names resulting capacity " + shop.getKey());
			assertTrue(quote.contains(shop.getChallenge().name().substring(0, 1)), "associate quote names own tier " + shop.getKey());
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
			"Keep your blade sharp and your excuses shorter.",
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
		assertTrue(after.getActiveTaskKey() != null, "promotion resumes with a repeatable task");
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
