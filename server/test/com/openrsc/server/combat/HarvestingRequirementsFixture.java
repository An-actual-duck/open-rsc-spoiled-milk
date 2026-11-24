package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.handlers.GameObjectAction;
import com.openrsc.server.net.rsc.struct.incoming.TargetObjectStruct;
import com.openrsc.server.plugins.authentic.defaults.Default;
import com.openrsc.server.plugins.custom.skills.harvesting.Harvesting;
import com.openrsc.server.plugins.triggers.OpLocTrigger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public final class HarvestingRequirementsFixture {
	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness harness = new CurrentCombatHarness()) {
			harness.server().getConfig().WANT_FATIGUE = false;
			harness.installPlugin(OpLocTrigger.class, new Harvesting());
			harness.installDefaultPlugin(new Default());
			Player player = harness.player("harvestfix", 441, 440);
			harness.recordOutgoingPackets(player);
			player.getClientLimitations().maxItemId = Integer.MAX_VALUE;
			setLevel(player, 1);
			check(player.getCarriedItems().getInventory().add(new Item(ItemId.SHEARS.id())),
				"Tin shears must start in the inventory");

			GameObject flax = place(harness, SceneryId.FLAX, 440, 440);
			expect(click(harness, player, flax, OpcodeIn.OBJECT_COMMAND),
				"You need at least Tin shears to harvest this.");
			check(flaxCount(player) == 0, "Carried Tin shears cannot harvest flax");

			harness.equip(player, ItemId.SHEARS.id(), 1);
			expect(click(harness, player, flax, OpcodeIn.OBJECT_COMMAND), "You get a flax");
			check(flaxCount(player) == 1, "Equipped Tin shears must yield flax at level 1, count=" + flaxCount(player));

			GameObject tomato = place(harness, SceneryId.TOMATO_PLANT, 441, 439);
			expect(click(harness, player, tomato, OpcodeIn.OBJECT_COMMAND),
				"You need at least Bronze shears to harvest this.",
				"You need level 15 Harvesting to use Bronze shears.");
			setLevel(player, 15);
			expect(click(harness, player, tomato, OpcodeIn.OBJECT_COMMAND),
				"You need at least Bronze shears to harvest this.");
			harness.equip(player, ItemId.RUNE_SHEARS.id(), 1);
			expect(click(harness, player, tomato, OpcodeIn.OBJECT_COMMAND),
				"You need level 70 Harvesting to use your Rune shears.");

			GameObject seaweed = place(harness, SceneryId.SEA_WEED, 441, 441);
			setLevel(player, 22);
			harness.equip(player, ItemId.SHEARS.id(), 1);
			expect(click(harness, player, seaweed, OpcodeIn.OBJECT_COMMAND),
				"You need at least Iron shears to clip this.");
			harness.equip(player, ItemId.IRON_SHEARS.id(), 1);
			setLevel(player, 21);
			expect(click(harness, player, seaweed, OpcodeIn.OBJECT_COMMAND),
				"You need level 22 Harvesting to use your Iron shears.");
			setLevel(player, 22);
			expect(click(harness, player, seaweed, OpcodeIn.OBJECT_COMMAND), "You get some weed");

			harness.server().getConfig().BATCH_PROGRESSION = false;
			harness.server().getEntityHandler().load();
			setLevel(player, 1);
			harness.equip(player, ItemId.SHEARS.id(), 1);
			flax = place(harness, SceneryId.FLAX, 442, 440);
			expect(click(harness, player, flax, OpcodeIn.OBJECT_COMMAND), "Nothing interesting happens");
			expect(click(harness, player, flax, OpcodeIn.OBJECT_COMMAND2), "You get a flax");
			check(flaxCount(player) == 2, "Secondary Harvest must still work with batch progression disabled");

			// Use a second tile and player so crop respawns cannot affect the scenery check.
			Player visitor = harness.player("sceneryfix", 446, 440);
			harness.recordOutgoingPackets(visitor);
			GameObject table = place(harness, SceneryId.TABLE, 445, 440);
			expect(click(harness, visitor, table, OpcodeIn.OBJECT_COMMAND), "Nothing interesting happens");
		}
		System.out.println("PASS flax harvesting, Harvest and Clip requirements, and scenery fallback");
	}

	private static GameObject place(CurrentCombatHarness harness, SceneryId scenery, int x, int y) {
		harness.openTile(x, y);
		GameObject object = new GameObject(harness.world(), Point.location(x, y), scenery.id(), 0, 0);
		harness.world().registerGameObject(object);
		return object;
	}

	private static void setLevel(Player player, int level) {
		player.getSkills().setExperienceAndLevel(Skill.HARVESTING.id(),
			player.getSkills().experienceForLevel(level), level, false);
	}

	private static int flaxCount(Player player) {
		return player.getCarriedItems().getInventory().countId(ItemId.FLAX.id());
	}

	private static List<String> click(CurrentCombatHarness harness, Player player, GameObject object, OpcodeIn opcode)
			throws Exception {
		@SuppressWarnings("unchecked")
		List<Packet> outgoing = (List<Packet>) CurrentCombatHarness.readPrivateField(player, "outgoingPackets");
		int packetStart = outgoing.size();
		TargetObjectStruct click = new TargetObjectStruct();
		click.setOpcode(opcode);
		click.coordObject = object.getLocation();
		new GameObjectAction().process(click, player);
		check(player.getWalkToAction() != null && player.getWalkToAction().shouldExecute(),
			"Click must resolve to a reachable scenery action");
		harness.server().getGameUpdater().executeWalkToActions(player);
		for (int tick = 0; tick < 5; tick++) {
			harness.advanceOneCombatTick();
		}

		int messageOpcode = ActionSender.getGenerator(player).fromOpcodeEnum(OpcodeOut.SEND_SERVER_MESSAGE, player).getOpcode();
		return outgoing.subList(packetStart, outgoing.size()).stream()
			.filter(packet -> packet.getID() == messageOpcode)
			.map(packet -> packet.getBuffer().toString(StandardCharsets.ISO_8859_1))
			.collect(Collectors.toList());
	}

	private static void expect(List<String> messages, String... expected) {
		for (String message : expected) {
			check(messages.stream().anyMatch(payload -> payload.contains(message)),
				"Missing message " + message + " in " + messages);
		}
	}

	private static void check(boolean condition, String error) {
		if (!condition) {
			throw new AssertionError(error);
		}
	}
}
