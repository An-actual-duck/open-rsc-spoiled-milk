package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.model.PlayerAppearance;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.handlers.GameObjectAction;
import com.openrsc.server.net.rsc.handlers.ItemEquip;
import com.openrsc.server.net.rsc.handlers.NpcCommand;
import com.openrsc.server.net.rsc.struct.incoming.EquipStruct;
import com.openrsc.server.net.rsc.struct.incoming.TargetMobStruct;
import com.openrsc.server.net.rsc.struct.incoming.TargetObjectStruct;
import com.openrsc.server.plugins.authentic.defaults.Default;
import com.openrsc.server.plugins.authentic.misc.Sheep;
import com.openrsc.server.plugins.shared.EntranaRestrictions;
import com.openrsc.server.plugins.custom.skills.harvesting.Harvesting;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public final class HarvestingRequirementsFixture {
	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness harness = new CurrentCombatHarness()) {
			harness.server().getConfig().WANT_FATIGUE = false;
			harness.installPlugin(OpLocTrigger.class, new Harvesting());
			harness.installPlugin(OpNpcTrigger.class, new Sheep());
			harness.installDefaultPlugin(new Default());
			Player player = newPlayer(harness, "harvestfix", 441);
			Item tinShears = addItem(player, ItemId.SHEARS.id());
			Item bronzeShears = addItem(player, ItemId.BRONZE_SHEARS.id());

			GameObject flax = place(harness, SceneryId.FLAX, 440, 440);
			GameObject tomato = place(harness, SceneryId.TOMATO_PLANT, 441, 439);
			GameObject seaweed = place(harness, SceneryId.SEA_WEED, 441, 441);
			double xpBefore = harvestingXp(player);
			expect(click(harness, player, flax, OpcodeIn.OBJECT_COMMAND),
				"You need at least Tin shears to harvest this.");
			expect(click(harness, player, tomato, OpcodeIn.OBJECT_COMMAND),
				"You need at least Bronze shears to harvest this.");
			check(count(player, ItemId.FLAX) == 0 && count(player, ItemId.TOMATO) == 0
				&& harvestingXp(player) == xpBefore, "Carried shears must give neither produce nor XP");

			equip(harness, player, tinShears);
			expect(click(harness, player, flax, OpcodeIn.OBJECT_COMMAND), "You get a flax");
			check(count(player, ItemId.FLAX) == 1 && harvestingXp(player) - xpBefore == 40,
				"Tin shears must yield one flax and 40 internal XP at level 1");

			setLevel(player, 1);
			xpBefore = harvestingXp(player);
			expect(click(harness, player, tomato, OpcodeIn.OBJECT_COMMAND),
				"You need at least Bronze shears to harvest this.");
			expect(click(harness, player, seaweed, OpcodeIn.OBJECT_COMMAND),
				"You need at least Iron shears to clip this.");
			check(count(player, ItemId.TOMATO) == 0 && harvestingXp(player) == xpBefore,
				"An equipped tool below the minimum tier must give neither produce nor XP");

			equip(harness, player, bronzeShears);
			expect(click(harness, player, tomato, OpcodeIn.OBJECT_COMMAND), "You get a tomato");
			check(count(player, ItemId.TOMATO) == 1 && harvestingXp(player) - xpBefore == 112,
				"Bronze shears must yield one tomato and 112 internal XP at level 1");

			setLevel(player, 1);
			xpBefore = harvestingXp(player);
			equip(harness, player, addItem(player, ItemId.IRON_SHEARS.id()));
			expect(click(harness, player, seaweed, OpcodeIn.OBJECT_COMMAND), "You get some weed");
			check(count(player, ItemId.SEAWEED) + count(player, ItemId.EDIBLE_SEAWEED) == 1
				&& harvestingXp(player) - xpBefore == 84, "Iron shears must clip one seaweed and grant 84 internal XP at level 1");

			harness.server().getConfig().BATCH_PROGRESSION = false;
			harness.server().getEntityHandler().load();
			setLevel(player, 1);
			equip(harness, player, addItem(player, ItemId.SHEARS.id()));
			flax = place(harness, SceneryId.FLAX, 442, 440);
			expect(click(harness, player, flax, OpcodeIn.OBJECT_COMMAND), "Nothing interesting happens");
			expect(click(harness, player, flax, OpcodeIn.OBJECT_COMMAND2), "You get a flax");
			check(count(player, ItemId.FLAX) == 2, "Secondary Harvest must still work with batch progression disabled");

			Player shepherd = newPlayer(harness, "sheepfix", 446);
			Npc sheep = harness.npc(NpcId.SHEEP.id(), 446, 441);
			expect(shear(harness, shepherd, sheep), "You need at least Tin shears to shear this sheep.");
			check(count(shepherd, ItemId.WOOL) == 0 && harvestingXp(shepherd) == 0,
				"Shearing without equipped shears must give neither wool nor XP");

			Item exaltedShears = addItem(shepherd, MyWorldItemId.EXALTED_RUNE_SHEARS);
			harness.server().getConfig().MEMBER_WORLD = false;
			equipPacket(shepherd, exaltedShears);
			check(!shepherd.getCarriedItems().getEquipment().hasEquipped(exaltedShears.getCatalogId()),
				"Exalted Rune shears must keep their members-only restriction");
			harness.server().getConfig().MEMBER_WORLD = true;
			equip(harness, shepherd, exaltedShears);
			shepherd.checkEquipment2();
			check(shepherd.getCarriedItems().getEquipment().hasEquipped(exaltedShears.getCatalogId()),
				"Equipment revalidation must retain Exalted Rune shears at level 1");
			check(EntranaRestrictions.itemIsBlocked(shepherd, bronzeShears)
				&& EntranaRestrictions.itemIsBlocked(shepherd, exaltedShears),
				"Removing equipment level gates must preserve Entrana's shears restrictions");
			expect(shear(harness, shepherd, sheep), "You get some wool");
			int wool = count(shepherd, ItemId.WOOL);
			check(wool > 0 && harvestingXp(shepherd) == wool * 20,
				"Exalted Rune shears must give wool and 20 internal XP per unit at level 1");

			GameObject table = place(harness, SceneryId.TABLE, 445, 440);
			expect(click(harness, shepherd, table, OpcodeIn.OBJECT_COMMAND), "Nothing interesting happens");
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

	private static int count(Player player, ItemId itemId) {
		return player.getCarriedItems().getInventory().countId(itemId.id());
	}

	private static double harvestingXp(Player player) {
		return player.getSkills().getExperience(Skill.HARVESTING.id());
	}

	private static Item addItem(Player player, int itemId) {
		Item item = new Item(itemId);
		check(player.getCarriedItems().getInventory().add(item), "Item must enter inventory: " + itemId);
		return item;
	}

	private static Player newPlayer(CurrentCombatHarness harness, String name, int x) throws Exception {
		Player player = harness.player(name, x, 440);
		harness.recordOutgoingPackets(player);
		player.getSettings().setAppearance(new PlayerAppearance(2, 8, 14, 0, 1, 2));
		player.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		setLevel(player, 1);
		return player;
	}

	private static void equipPacket(Player player, Item item) throws Exception {
		EquipStruct equip = new EquipStruct();
		equip.setOpcode(OpcodeIn.ITEM_EQUIP_FROM_INVENTORY);
		equip.slotIndex = player.getCarriedItems().getInventory().getItems().indexOf(item);
		new ItemEquip().process(equip, player);
	}

	private static void equip(CurrentCombatHarness harness, Player player, Item item) throws Exception {
		equipPacket(player, item);
		harness.advanceOneCombatTick();
		check(player.getCarriedItems().getEquipment().hasEquipped(item.getCatalogId()),
			"Ordinary inventory equip must work at Harvesting level 1 for " + item.getCatalogId());
	}

	private static List<String> shear(CurrentCombatHarness harness, Player player, Npc sheep) throws Exception {
		List<Packet> outgoing = outgoing(player);
		int packetStart = outgoing.size();
		TargetMobStruct click = new TargetMobStruct();
		click.setOpcode(OpcodeIn.NPC_COMMAND);
		click.serverIndex = sheep.getIndex();
		new NpcCommand().process(click, player);
		return runAction(harness, player, outgoing, packetStart);
	}

	@SuppressWarnings("unchecked")
	private static List<Packet> outgoing(Player player) throws ReflectiveOperationException {
		return (List<Packet>) CurrentCombatHarness.readPrivateField(player, "outgoingPackets");
	}

	private static List<String> click(CurrentCombatHarness harness, Player player, GameObject object, OpcodeIn opcode)
			throws Exception {
		List<Packet> outgoing = outgoing(player);
		int packetStart = outgoing.size();
		TargetObjectStruct click = new TargetObjectStruct();
		click.setOpcode(opcode);
		click.coordObject = object.getLocation();
		new GameObjectAction().process(click, player);
		return runAction(harness, player, outgoing, packetStart);
	}

	private static List<String> runAction(CurrentCombatHarness harness, Player player, List<Packet> outgoing,
			int packetStart) throws Exception {
		check(player.getWalkToAction() != null && player.getWalkToAction().shouldExecute(),
			"Click must resolve to a reachable action");
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
