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
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.plugins.custom.myworld.npcs.MonsterSlayerContacts;
import com.openrsc.server.util.rsc.DataConversions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.lang.reflect.Field;
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
		System.out.println("Monster Slayer contact plugin routes: PASS");
	}

	private static void guildAccessModesAndQuestStates(Server server) {
		Player player = player(server, "slayerguildaccess", 206, 600);
		player.setQuestPoints(31);
		server.getConfig().INFLUENCE_INSTEAD_QP = false;
		assertFalse(MonsterSlayerGuildAccess.allows(player, 3), "Champions quest-point lock");
		player.setQuestPoints(32);
		assertTrue(MonsterSlayerGuildAccess.allows(player, 3), "Champions quest-point access");
		server.getConfig().INFLUENCE_INSTEAD_QP = true;
		assertFalse(MonsterSlayerGuildAccess.allows(player, 3), "Champions Influence mode fails closed without enabled Influence skill");
		server.getConfig().INFLUENCE_INSTEAD_QP = false;
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
	private static void assertEquals(int expected, int actual, String label) { if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", got " + actual); }
	private static void assertEquals(String expected, String actual, String label) { if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual); }
	private static void assertEquals(Map<String, Object> expected, Map<String, Object> actual, String label) { if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual); }
	private static void assertEquals(Object expected, Object actual, String label) { if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual); }
}
