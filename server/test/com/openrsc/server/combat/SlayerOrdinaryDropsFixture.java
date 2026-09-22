package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcDrops;
import com.openrsc.server.content.DropTable;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Exact production RNG tickets, independent equipment odds and actual death delivery. */
public final class SlayerOrdinaryDropsFixture {
	// Item, quantity, weight, noted. Specialty occupies the first 64 tickets.
	private static final int[][][] ORDINARY = {
		{ // Giant frog
			{ItemId.UNIDENTIFIED_GUAM_LEAF.id(), 1, 20, 1},
			{ItemId.UNIDENTIFIED_MARRENTILL.id(), 1, 16, 1},
			{ItemId.UNIDENTIFIED_TARROMIN.id(), 1, 12, 1},
			{ItemId.UNIDENTIFIED_HARRALANDER.id(), 1, 8, 1},
			{ItemId.UNIDENTIFIED_RANARR_WEED.id(), 1, 6, 1},
			{ItemId.UNIDENTIFIED_IRIT_LEAF.id(), 1, 2, 1},
			{ItemId.COINS.id(), 20, 20, 0},
			{ItemId.COINS.id(), 60, 10, 0},
			{ItemId.COINS.id(), 160, 2, 0},
			{ItemId.WATER_RUNE.id(), 10, 8, 0},
			{ItemId.IRON_ARROWS.id(), 10, 8, 0},
			{ItemId.FEATHER.id(), 15, 8, 0},
			{ItemId.COPPER_ORE.id(), 3, 4, 1},
		},
		{ // Cockatrice
			{ItemId.UNCUT_SAPPHIRE.id(), 1, 28, 0},
			{ItemId.UNCUT_EMERALD.id(), 1, 20, 0},
			{ItemId.UNCUT_RUBY.id(), 1, 12, 0},
			{ItemId.UNCUT_DIAMOND.id(), 1, 4, 0},
			{ItemId.COINS.id(), 35, 20, 0},
			{ItemId.COINS.id(), 105, 10, 0},
			{ItemId.COINS.id(), 280, 2, 0},
			{ItemId.AIR_RUNE.id(), 20, 8, 0},
			{ItemId.IRON_ARROWS.id(), 15, 8, 0},
			{ItemId.COAL.id(), 3, 8, 1},
			{ItemId.SILVER_BAR.id(), 1, 4, 1},
		},
		{ // Banshee
			{ItemId.AIR_RUNE.id(), 50, 12, 0},
			{ItemId.WATER_RUNE.id(), 50, 12, 0},
			{ItemId.EARTH_RUNE.id(), 50, 12, 0},
			{ItemId.FIRE_RUNE.id(), 50, 12, 0},
			{ItemId.CHAOS_RUNE.id(), 15, 8, 0},
			{ItemId.DEATH_RUNE.id(), 5, 4, 0},
			{ItemId.LAW_RUNE.id(), 3, 4, 0},
			{ItemId.COINS.id(), 50, 20, 0},
			{ItemId.COINS.id(), 150, 10, 0},
			{ItemId.COINS.id(), 400, 2, 0},
			{ItemId.UNIDENTIFIED_HARRALANDER.id(), 1, 8, 1},
			{ItemId.STEEL_ARROWS.id(), 15, 8, 0},
			{ItemId.COAL.id(), 5, 8, 1},
			{ItemId.GOLD_BAR.id(), 1, 4, 1},
		},
		{ // Naga
			{ItemId.SALMON.id(), 4, 20, 1},
			{ItemId.TUNA.id(), 4, 20, 1},
			{ItemId.LOBSTER.id(), 3, 16, 1},
			{ItemId.SWORDFISH.id(), 2, 8, 1},
			{ItemId.COINS.id(), 60, 20, 0},
			{ItemId.COINS.id(), 180, 10, 0},
			{ItemId.COINS.id(), 480, 2, 0},
			{ItemId.WATER_RUNE.id(), 30, 8, 0},
			{ItemId.STEEL_ARROWS.id(), 20, 8, 0},
			{ItemId.COAL.id(), 5, 8, 1},
			{ItemId.UNCUT_SAPPHIRE.id(), 1, 4, 0},
		},
		{ // Terror dog
			{ItemId.STEEL_ARROWS.id(), 40, 16, 0},
			{ItemId.MITHRIL_ARROWS.id(), 30, 16, 0},
			{ItemId.TITAN_STEEL_ARROWS.id(), 20, 8, 0},
			{ItemId.STEEL_BOLTS.id(), 30, 8, 0},
			{ItemId.MITHRIL_BOLTS.id(), 20, 8, 0},
			{ItemId.MITHRIL_SHURIKEN.id(), 10, 8, 0},
			{ItemId.COINS.id(), 75, 20, 0},
			{ItemId.COINS.id(), 225, 10, 0},
			{ItemId.COINS.id(), 600, 2, 0},
			{ItemId.CHAOS_RUNE.id(), 10, 8, 0},
			{ItemId.COAL.id(), 8, 8, 1},
			{ItemId.UNIDENTIFIED_HARRALANDER.id(), 2, 8, 1},
			{ItemId.GOLD_BAR.id(), 2, 4, 1},
		},
		{ // Bloodveld
			{ItemId.UNIDENTIFIED_IRIT_LEAF.id(), 2, 14, 1},
			{ItemId.UNIDENTIFIED_AVANTOE.id(), 2, 14, 1},
			{ItemId.UNIDENTIFIED_KWUARM.id(), 2, 12, 1},
			{ItemId.UNIDENTIFIED_CADANTINE.id(), 2, 10, 1},
			{ItemId.UNIDENTIFIED_DWARF_WEED.id(), 2, 10, 1},
			{ItemId.UNIDENTIFIED_TORSTOL.id(), 1, 4, 1},
			{ItemId.COINS.id(), 85, 20, 0},
			{ItemId.COINS.id(), 255, 10, 0},
			{ItemId.COINS.id(), 680, 2, 0},
			{ItemId.NATURE_RUNE.id(), 8, 8, 0},
			{ItemId.MITHRIL_ARROWS.id(), 20, 8, 0},
			{ItemId.COAL.id(), 10, 8, 1},
			{ItemId.UNCUT_RUBY.id(), 1, 4, 0},
		},
		{ // Dark beast
			{ItemId.CHAOS_RUNE.id(), 35, 8, 0},
			{ItemId.NATURE_RUNE.id(), 15, 12, 0},
			{ItemId.LAW_RUNE.id(), 15, 12, 0},
			{ItemId.DEATH_RUNE.id(), 20, 16, 0},
			{ItemId.BLOOD_RUNE.id(), 10, 16, 0},
			{ItemId.COINS.id(), 105, 20, 0},
			{ItemId.COINS.id(), 315, 10, 0},
			{ItemId.COINS.id(), 840, 2, 0},
			{ItemId.ADAMANTITE_ARROWS.id(), 15, 8, 0},
			{ItemId.COAL.id(), 12, 8, 1},
			{ItemId.GOLD_BAR.id(), 3, 8, 1},
			{ItemId.UNCUT_DIAMOND.id(), 1, 4, 0},
		},
		{ // Abyssal demon
			{ItemId.ADAMANTITE_ARROWS.id(), 30, 16, 0},
			{ItemId.ORICHALCUM_ARROWS.id(), 20, 16, 0},
			{ItemId.RUNE_ARROWS.id(), 10, 8, 0},
			{ItemId.ADAMANTITE_BOLTS.id(), 20, 8, 0},
			{ItemId.ORICHALCUM_BOLTS.id(), 15, 8, 0},
			{ItemId.RUNE_SHURIKEN.id(), 5, 8, 0},
			{ItemId.COINS.id(), 125, 20, 0},
			{ItemId.COINS.id(), 375, 10, 0},
			{ItemId.COINS.id(), 1000, 2, 0},
			{ItemId.DEATH_RUNE.id(), 15, 8, 0},
			{ItemId.COAL.id(), 15, 8, 1},
			{ItemId.MITHRIL_BAR.id(), 3, 8, 1},
			{ItemId.UNCUT_DIAMOND.id(), 1, 4, 0},
		},
	};
	private static final int[][] EQUIPMENT = {
		{868, ItemId.ORICHALCUM_LARGE_HELMET.id(), 1000},
		{868, ItemId.ORICHALCUM_GREAVES.id(), 1000},
		{868, ItemId.ORICHALCUM_GAUNTLETS.id(), 1000},
		{868, ItemId.ORICHALCUM_PLATE_MAIL_LEGS.id(), 2000},
		{868, ItemId.ORICHALCUM_PLATE_MAIL_BODY.id(), 2000},
		{869, ItemId.BLOOD_LONGBOW.id(), 2000},
		{869, ItemId.LARGE_RUNE_HELMET.id(), 1000},
		{869, ItemId.RUNITE_GAUNTLETS.id(), 1000},
		{869, ItemId.RUNITE_GREAVES.id(), 1000},
		{870, ItemId.RUNE_PLATE_MAIL_LEGS.id(), 1000},
	};

	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness h = new CurrentCombatHarness()) {
			Player player = h.player("tower loot test", 440, 440);
			player.getClientLimitations().maxItemId = Integer.MAX_VALUE;
			NpcDrops drops = h.world().getNpcDrops();
			verifyOrdinary(h, player, drops);
			verifyEquipment(h, player, drops);
			verifyModifiers(h, player, drops);
			verifyDelivery(h, player);
			String oldBlueDragon = drops.getDropTable(202).toString();
			h.server().getConfig().WANT_MYWORLD = false;
			check(drops.rollSlayerEquipmentDrops(868, player, 1).isEmpty(), "equipment is MyWorld-only");
			drops.unload();
			drops.load();
			for (int npcId = 863; npcId <= 870; npcId++)
				check(drops.getDropTable(npcId) == null, "no other-world tower table");
			h.server().getConfig().WANT_MYWORLD = true;
			drops.unload();
			drops.load();
			check(oldBlueDragon.equals(drops.getDropTable(202).toString()), "existing NPC loot unchanged");
			verifyOrdinary(h, player, drops);
			verifyEquipment(h, player, drops); // Reload never duplicates entries.
			System.out.println("PASS Slayer ordinary loot: all tickets, specialty weights, coin scaling, stackable supplies, exact independent equipment odds, luck/contribution, ground delivery and reload/world isolation");
		}
	}

	private static void verifyOrdinary(CurrentCombatHarness h, Player player, NpcDrops drops) {
		for (int family = 0; family < ORDINARY.length; family++) {
			DropTable table = drops.getDropTable(863 + family);
			check(table.getTotalWeight() == 128, "fixed ordinary budget");
			int ticket = 0;
			for (int[] row : ORDINARY[family]) {
				ItemDefinition definition = h.server().getEntityHandler().getItemDef(row[0]);
				check(definition != null, "valid supply");
				check(definition.isStackable() || (row[3] == 1 && definition.isNoteable()), "stackable/noted supplies");
				check(row[3] == 0 || !definition.isStackable(), "do not note natural stacks");
				check(table.hasItemDrop(row[0], row[1], row[2], row[3] == 1), "documented row");
				for (int i = 0; i < row[2]; i++, ticket++) {
					h.random().reset(1);
					h.random().scriptInts(ticket);
					List<Item> rolled = table.rollItem(player);
					check(rolled.size() == 1, "one ordinary supply outcome");
					Item item = rolled.get(0);
					check(item.getCatalogId() == row[0] && item.getAmount() == row[1]
						&& item.getNoted() == (row[3] == 1), "exact supply ticket " + ticket);
				}
			}
			check(ticket == 124, "remaining four tickets access shared rare table");
			for (; ticket < 128; ticket++) {
				h.random().reset(1);
				h.random().scriptInts(ticket, 1).scriptDoubles(0.0);
				assertSingle(table.rollItem(player), ItemId.UNCUT_SAPPHIRE.id());
			}
		}
	}

	private static List<int[]> equipmentFor(int npcId) {
		List<int[]> entries = new ArrayList<>();
		for (int[] entry : EQUIPMENT) if (entry[0] == npcId) entries.add(entry);
		return entries;
	}

	private static void verifyEquipment(CurrentCombatHarness h, Player player, NpcDrops drops) {
		for (int npcId = 863; npcId <= 870; npcId++) {
			List<int[]> entries = equipmentFor(npcId);
			for (int index = 0; index < entries.size(); index++) {
				int[] expected = entries.get(index);
				List<Integer> draws = new ArrayList<>();
				for (int i = 0; i < entries.size(); i++) {
					draws.add(i == index ? 99 : 100);
					if (i == index) draws.add(0);
				}
				h.random().reset(1);
				h.random().scriptInts(draws.toArray(new Integer[0])).scriptDoubles(0.0);
				assertSingle(drops.rollSlayerEquipmentDrops(npcId, player, 1), expected[1]);
				check(h.random().describeState().contains("int(" + expected[2] * 100 + ")=99"),
					"exact equipment denominator");
			}
			Integer[] misses = new Integer[entries.size()];
			Arrays.fill(misses, 100);
			h.random().reset(1);
			h.random().scriptInts(misses);
			check(drops.rollSlayerEquipmentDrops(npcId, player, 1).isEmpty(), "first losing ticket");
			Integer[] wins = new Integer[entries.size() * 2];
			Arrays.fill(wins, 0);
			Double[] gates = new Double[entries.size()];
			Arrays.fill(gates, 0.0);
			h.random().reset(1);
			h.random().scriptInts(wins).scriptDoubles(gates);
			List<Item> together = drops.rollSlayerEquipmentDrops(npcId, player, 1);
			check(together.size() == entries.size(), "independent equipment can co-drop");
			for (int i = 0; i < entries.size(); i++)
				check(together.get(i).getCatalogId() == entries.get(i)[1], "all specified pieces only");
		}
		check(drops.rollSlayerEquipmentDrops(1, player, 1).isEmpty(), "unrelated NPC equipment untouched");
		check(drops.rollSlayerEquipmentDrops(870, null, 1).isEmpty(), "null owner safe");
	}

	private static void verifyModifiers(CurrentCombatHarness h, Player player, NpcDrops drops) throws ReflectiveOperationException {
		h.random().reset(1);
		h.random().scriptInts(0).scriptDoubles(0.5);
		check(drops.rollSlayerEquipmentDrops(870, player, 0.5).isEmpty(), "contribution rejects at boundary");
		h.random().reset(1);
		h.random().scriptInts(0, 0).scriptDoubles(0.49);
		assertSingle(drops.rollSlayerEquipmentDrops(870, player, 0.5), ItemId.RUNE_PLATE_MAIL_LEGS.id());
		Player lucky = h.player("lucky tower loot", 442, 440);
		lucky.activatePotionOfLuck(20, 600_000L);
		h.random().reset(1);
		h.random().scriptInts(119, 0).scriptDoubles(0.0);
		assertSingle(drops.rollSlayerEquipmentDrops(870, lucky, 1), ItemId.RUNE_PLATE_MAIL_LEGS.id());
		check(h.random().describeState().contains("int(100020)=119"), "fractional rare weight preserved");
		h.random().reset(1);
		h.random().scriptInts(120);
		check(drops.rollSlayerEquipmentDrops(870, lucky, 1).isEmpty(), "luck boundary");
		int ring = -1, necklace = -1;
		for (int id = 0; id < 3333; id++) {
			if (EnchantingItemEffects.getWealthAdditionalRollChance(id) > 0) ring = id;
			if (EnchantingItemEffects.getCosmicNecklaceStandardDropChance(id) > 0) necklace = id;
		}
		check(ring >= 0 && necklace >= 0, "existing jewelry");
		Player wealthy = h.player("wealthy tower loot", 444, 440);
		h.equip(wealthy, ring, 1);
		h.random().reset(1);
		h.random().scriptInts(100, 0, 0).scriptDoubles(0.0, 0.0);
		assertSingle(drops.rollSlayerEquipmentDrops(870, wealthy, 1), ItemId.RUNE_PLATE_MAIL_LEGS.id());
		h.random().reset(1);
		h.random().scriptInts(0, 0).scriptDoubles(0.0);
		assertSingle(drops.rollSlayerEquipmentDrops(870, wealthy, 1), ItemId.RUNE_PLATE_MAIL_LEGS.id());
		String trace = h.random().describeState();
		check(trace.indexOf("int(100000)") == trace.lastIndexOf("int(100000)"), "no double rare reward");
		Player cosmic = h.player("cosmic tower loot", 446, 440);
		h.equip(cosmic, necklace, 1);
		h.random().reset(1);
		h.random().scriptInts(100, 0).scriptDoubles(0.0);
		check(drops.rollSlayerEquipmentDrops(870, cosmic, 1).isEmpty(), "standard bonus cannot reroll rare equipment");
		h.random().reset(1);
		h.random().scriptInts(64, 64).scriptDoubles(0.0);
		check(drops.getDropTable(863).rollItem(cosmic).size() == 2, "standard bonus still applies to supplies");
	}

	private static void verifyDelivery(CurrentCombatHarness h, Player player) {
		for (int family = 0; family < ORDINARY.length; family++) {
			Npc npc = h.npc(863 + family, 460 + family * 2, 450);
			npc.updateRegion();
			Integer[] wins = new Integer[40];
			Double[] gates = new Double[20];
			Arrays.fill(wins, 0);
			Arrays.fill(gates, 0.0);
			h.random().reset(1);
			h.random().scriptInts(wins).scriptDoubles(gates);
			npc.dropItems(player);
			int[] row = ORDINARY[family][0];
			GroundItem supply = ground(h, npc, player, row[0]);
			check(supply != null && supply.getAmount() == row[1] && supply.getNoted() == (row[3] == 1),
				"supply reaches ground with quantity/noting");
			for (int[] equipment : equipmentFor(npc.getID()))
				check(ground(h, npc, player, equipment[1]) != null, "equipment reaches ground");
			int[] materials = {3333,3339,3399,3335,3336,3337,3338,3340};
			check(ground(h, npc, player, materials[family]) != null, "baseline material retained");
			check(ground(h, npc, player, family == 7 ? ItemId.DEMON_ASH.id() : ItemId.BONES.id()) != null,
				"correct remains retained");
			if (family == 7) {
				check(ground(h, npc, player, 3347) != null && ground(h, npc, player, 3348) != null,
					"both components coexist with rune legs and ammo");
				check(ground(h, npc, player, ItemId.BONES.id()) == null, "no demon bones");
			}
		}
	}

	private static GroundItem ground(CurrentCombatHarness h, Npc npc, Player owner, int itemId) {
		return h.world().getRegionManager().getRegion(npc.getLocation()).getItem(itemId, npc.getLocation(), owner);
	}

	private static void assertSingle(List<Item> items, int id) {
		check(items.size() == 1 && items.get(0).getCatalogId() == id && items.get(0).getAmount() == 1
			&& !items.get(0).getNoted(), "one unnoted item " + id + ": " + items);
	}

	private static void check(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}
}
