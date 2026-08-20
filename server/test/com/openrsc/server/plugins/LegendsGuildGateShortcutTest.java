package com.openrsc.server.plugins;

import com.openrsc.server.Server;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.authentic.quests.members.legendsquest.npcs.LegendsQuestGuildGuard;
import com.openrsc.server.plugins.authentic.quests.members.legendsquest.npcs.LegendsQuestGuildGuard.OuterGateRoute;
import com.openrsc.server.plugins.authentic.quests.members.legendsquest.obstacles.LegendsQuestGates;
import com.openrsc.server.util.rsc.DataConversions;

/** Focused outer-gate admission and trigger-ownership regression. */
public final class LegendsGuildGateShortcutTest {
	private LegendsGuildGateShortcutTest() { }

	public static void main(String[] args) throws Exception {
		Server server = new Server("myworld.conf");
		server.getEntityHandler().load();
		LegendsQuestGuildGuard outer = new LegendsQuestGuildGuard();
		LegendsQuestGates inner = new LegendsQuestGates();

		Player ineligible = player(server, "legendgateineligible", 512, 551);
		assertFalse(LegendsQuestGuildGuard.isEligibleToBeginLegendsQuest(ineligible),
			"ineligible player fails the authentic start requirements");
		assertEquals(OuterGateRoute.SPEAK_TO_GUARD, LegendsQuestGuildGuard.outerGateRoute(ineligible),
			"ineligible outside click stays outside and directs to a guard");

		Player eligible = eligiblePlayer(server, "legendgateeligible", 512, 551);
		assertTrue(LegendsQuestGuildGuard.isEligibleToBeginLegendsQuest(eligible),
			"all authentic requirements permit a new quest applicant");
		assertEquals(OuterGateRoute.ENTER, LegendsQuestGuildGuard.outerGateRoute(eligible),
			"eligible not-started player enters directly");
		assertEquals(Integer.valueOf(549), Integer.valueOf(LegendsQuestGuildGuard.outerGateDestinationY(OuterGateRoute.ENTER)),
			"outside entry moves immediately onto the guild grounds");
		assertExactEligibilityRequirements(server);

		Player progressing = player(server, "legendgateprogress", 512, 551);
		progressing.setQuestStage(Quests.LEGENDS_QUEST, 1);
		assertEquals(OuterGateRoute.ENTER, LegendsQuestGuildGuard.outerGateRoute(progressing),
			"in-progress player enters without rechecking start requirements");

		Player completed = player(server, "legendgatecomplete", 512, 551);
		completed.setQuestStage(Quests.LEGENDS_QUEST, -1);
		assertEquals(OuterGateRoute.ENTER, LegendsQuestGuildGuard.outerGateRoute(completed),
			"completed member enters directly");

		Player leaving = player(server, "legendgateleaving", 513, 549);
		assertEquals(OuterGateRoute.EXIT, LegendsQuestGuildGuard.outerGateRoute(leaving),
			"inside click always exits even when start requirements are absent");
		assertEquals(Integer.valueOf(552), Integer.valueOf(LegendsQuestGuildGuard.outerGateDestinationY(OuterGateRoute.EXIT)),
			"inside exit moves immediately outside the gate");
		assertThrows(new Runnable() { public void run() {
			LegendsQuestGuildGuard.outerGateDestinationY(OuterGateRoute.SPEAK_TO_GUARD);
		}}, "denied route cannot accidentally acquire a traversal destination");

		Npc westGuard = new Npc(server.getWorld(), NpcId.LEGENDS_GUILD_GUARD.id(), 511, 551);
		Npc eastGuard = new Npc(server.getWorld(), NpcId.LEGENDS_GUILD_GUARD.id(), 514, 551);
		assertTrue(outer.blockTalkNpc(ineligible, westGuard), "west guard retains direct Talk-to ownership");
		assertTrue(outer.blockTalkNpc(ineligible, eastGuard), "east guard retains direct Talk-to ownership");

		GameObject outerGate = new GameObject(server.getWorld(), Point.location(512, 550), 1079, 2, 0);
		GameObject innerDoor = new GameObject(server.getWorld(), Point.location(512, 540), 1080, 6, 0);
		assertTrue(outer.blockOpLoc(ineligible, outerGate, "open"), "guard plugin exclusively owns outer gate");
		assertFalse(outer.blockOpLoc(ineligible, innerDoor, "open"), "outer shortcut excludes inner hall door");
		assertFalse(inner.blockOpLoc(ineligible, outerGate, "open"), "inner-door plugin excludes outer gate");
		assertTrue(inner.blockOpLoc(ineligible, innerDoor, "open"), "inner completion gate retains ownership");

		System.out.println("Legends Guild outer gate shortcut: PASS");
	}

	private static void assertExactEligibilityRequirements(Server server) {
		int[] requiredQuests = {
			Quests.HEROS_QUEST, Quests.FAMILY_CREST, Quests.SHILO_VILLAGE,
			Quests.UNDERGROUND_PASS, Quests.WATERFALL_QUEST
		};
		for (int index = 0; index < requiredQuests.length; index++) {
			Player missing = eligiblePlayer(server, "legendgatemissing" + index, 512, 551);
			missing.setQuestStage(requiredQuests[index], 0);
			assertFalse(LegendsQuestGuildGuard.isEligibleToBeginLegendsQuest(missing),
				"required quest remains mandatory at index " + index);
		}
		Player lowPoints = eligiblePlayer(server, "legendgatelowpoints", 512, 551);
		lowPoints.setQuestPoints(106);
		assertFalse(LegendsQuestGuildGuard.isEligibleToBeginLegendsQuest(lowPoints),
			"107 quest points remain mandatory");
	}

	private static Player eligiblePlayer(Server server, String name, int x, int y) {
		Player player = player(server, name, x, y);
		player.setQuestPoints(107);
		player.setQuestStage(Quests.HEROS_QUEST, -1);
		player.setQuestStage(Quests.FAMILY_CREST, -1);
		player.setQuestStage(Quests.SHILO_VILLAGE, -1);
		player.setQuestStage(Quests.UNDERGROUND_PASS, -1);
		player.setQuestStage(Quests.WATERFALL_QUEST, -1);
		return player;
	}

	private static Player player(Server server, String name, int x, int y) {
		Player player = new Player(server.getWorld(), DataConversions.usernameToHash(name));
		player.setInitialLocation(Point.location(x, y));
		return player;
	}

	private static void assertTrue(boolean value, String label) {
		if (!value) throw new AssertionError(label);
	}
	private static void assertFalse(boolean value, String label) { assertTrue(!value, label); }
	private static void assertThrows(Runnable action, String label) {
		try { action.run(); } catch (IllegalArgumentException expected) { return; }
		throw new AssertionError(label);
	}
	private static void assertEquals(Object expected, Object actual, String label) {
		if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
	}
}
