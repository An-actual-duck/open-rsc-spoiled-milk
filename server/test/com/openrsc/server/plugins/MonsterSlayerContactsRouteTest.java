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
		guildAccessModesAndQuestStates(server);
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
}
