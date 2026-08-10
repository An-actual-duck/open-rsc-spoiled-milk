package com.openrsc.server.plugins;

import com.openrsc.server.Server;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.plugins.custom.myworld.npcs.MonsterSlayerContacts;

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
		System.out.println("Monster Slayer contact plugin routes: PASS");
	}

	private static void assertTrue(boolean value, String label) { if (!value) throw new AssertionError(label); }
	private static void assertFalse(boolean value, String label) { assertTrue(!value, label); }
}
