package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcDrops;
import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import java.util.List;

/** Scripted production RNG: exact boundaries, independent rolls and real ground delivery. */
public final class SlayerComponentDropsFixture {
	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness h = new CurrentCombatHarness()) {
			Player player = h.player("parts test", 440, 440);
			player.getClientLimitations().maxItemId = Integer.MAX_VALUE;
			NpcDrops drops = h.world().getNpcDrops();
			for (int id = 3333; id <= 3348; id++) {
				ItemDefinition def = h.server().getEntityHandler().getItemDef(id);
				check(def != null && !def.isUntradable() && !def.isWieldable(), "tradable material " + id);
				check(def.isStackable() == (id == 3339 || id == 3340), "stack rule " + id);
				check(def.isNoteable() == !def.isStackable(), "noting rule " + id);
			}
			int[] baseline = {3333, 3339, 3334, 3335, 3336, 3337, 3338, 3340};
			for (int index = 0; index < baseline.length; index++) {
				for (int draw = 0; draw < 3; draw++) {
					h.random().reset(1);
					h.random().scriptInts(draw);
					Item material = drops.getSlayerBaselineDrop(863 + index);
					check(material.getCatalogId() == baseline[index], "material identity " + index);
					check(material.getAmount() == (index == 1 || index == 7 ? draw + 1 : 1), "uniform quantity");
				}
			}
			check(drops.getSlayerBaselineDrop(1) == null, "unrelated NPC baseline untouched");
			check(drops.rollSlayerComponentDrops(866, player, 1).isEmpty(), "Naga has no rare Tail");
			check(drops.rollSlayerComponentDrops(1, player, 1).isEmpty(), "unrelated NPC rare loot untouched");
			int[][] singles = {{863,3341,128}, {864,3342,128}, {865,3343,1000},
				{867,3344,1000}, {868,3345,1000}, {869,3346,2000}};
			for (int[] entry : singles) {
				h.random().reset(1);
				h.random().scriptInts(99, 0).scriptDoubles(0.0);
				assertDrop(drops.rollSlayerComponentDrops(entry[0], player, 1), entry[1]);
				check(h.random().describeState().contains("int(" + entry[2] * 100 + ")=99"), "exact denominator");
				h.random().reset(1);
				h.random().scriptInts(100);
				check(drops.rollSlayerComponentDrops(entry[0], player, 1).isEmpty(), "first losing boundary");
			}
			h.random().reset(1);
			h.random().scriptInts(0, 0, 0, 0).scriptDoubles(0.0, 0.0);
			List<Item> both = drops.rollSlayerComponentDrops(870, player, 1);
			check(both.size() == 2 && both.get(0).getCatalogId() == 3347 && both.get(1).getCatalogId() == 3348,
				"rib and vertebra independently drop together");
			check(h.random().describeState().contains("int(12800)")
				&& h.random().describeState().contains("int(200000)"), "abyssal base denominators");
			h.random().reset(1);
			h.random().scriptInts(100, 0, 0).scriptDoubles(0.0);
			assertDrop(drops.rollSlayerComponentDrops(870, player, 1), 3348);
			h.random().reset(1);
			h.random().scriptInts(0, 0, 100).scriptDoubles(0.0);
			assertDrop(drops.rollSlayerComponentDrops(870, player, 1), 3347);

			// Existing personal-loot contribution gate still applies; no task required.
			h.random().reset(1);
			h.random().scriptInts(0).scriptDoubles(0.5);
			check(drops.rollSlayerComponentDrops(863, player, 0.5).isEmpty(), "contribution gate rejects at boundary");
			h.random().reset(1);
			h.random().scriptInts(0, 0).scriptDoubles(0.49);
			assertDrop(drops.rollSlayerComponentDrops(863, player, 0.5), 3341);

			Player lucky = h.player("lucky parts", 442, 440);
			lucky.activatePotionOfLuck(20, 600_000L);
			h.random().reset(1);
			h.random().scriptInts(119, 0).scriptDoubles(0.0);
			assertDrop(drops.rollSlayerComponentDrops(863, lucky, 1), 3341);
			check(h.random().describeState().contains("int(12820)=119"), "existing luck weights applied once");
			h.random().reset(1);
			h.random().scriptInts(120);
			check(drops.rollSlayerComponentDrops(863, lucky, 1).isEmpty(), "luck upper boundary");

			Player wealthy = h.player("wealth parts", 444, 440);
			int ring = -1, necklace = -1;
			for (int id = 0; id < 3333; id++) {
				if (EnchantingItemEffects.getWealthAdditionalRollChance(id) > 0) ring = id;
				if (EnchantingItemEffects.getCosmicNecklaceStandardDropChance(id) > 0) necklace = id;
			}
			check(ring >= 0 && necklace >= 0, "existing luck jewelry found");
			h.equip(wealthy, ring, 1);
			h.random().reset(1);
			h.random().scriptInts(100, 0, 0).scriptDoubles(0.0, 0.0);
			assertDrop(drops.rollSlayerComponentDrops(863, wealthy, 1), 3341);
			h.random().reset(1);
			h.random().scriptInts(0, 0).scriptDoubles(0.0);
			assertDrop(drops.rollSlayerComponentDrops(863, wealthy, 1), 3341);
			String wealthDraws = h.random().describeState();
			check(wealthDraws.indexOf("int(12800)") == wealthDraws.lastIndexOf("int(12800)"),
				"successful rare reward gets no second access roll");
			Player cosmic = h.player("cosmic parts", 446, 440);
			h.equip(cosmic, necklace, 1);
			h.random().reset(1);
			h.random().scriptInts(100, 0).scriptDoubles(0.0);
			check(drops.rollSlayerComponentDrops(863, cosmic, 1).isEmpty(), "standard-only bonus cannot reroll rare parts");

			// Actual NPC drop path: guaranteed materials + correct remains, no combat/task prerequisite.
			int[] rare = {3341, 3342, 3343, -1, 3344, 3345, 3346, 3347};
			for (int index = 0; index < baseline.length; index++) {
				Npc npc = h.npc(863 + index, 450 + index * 2, 450);
				npc.updateRegion();
				h.random().reset(8);
				h.random().scriptInts(0, 0, 0, 0, 0).scriptDoubles(0.0, 0.0);
				npc.dropItems(player);
				GroundItem material = ground(h, npc, player, baseline[index]);
				check(material != null && material.getAmount() >= 1, "ground material " + index);
				int remains = index == 7 ? ItemId.DEMON_ASH.id() : ItemId.BONES.id();
				check(ground(h, npc, player, remains) != null, "ground remains " + index);
				if (rare[index] >= 0) check(ground(h, npc, player, rare[index]) != null,
					"ground rare component " + index);
				if (index == 7) check(ground(h, npc, player, 3348) != null,
					"rib and vertebra reach ground together");
				if (index == 7) check(ground(h, npc, player, ItemId.BONES.id()) == null,
					"abyssal demon never drops ordinary bones");
			}
			h.server().getConfig().WANT_MYWORLD = false;
			check(drops.getSlayerBaselineDrop(863) == null, "baseline disabled outside MyWorld");
			check(drops.rollSlayerComponentDrops(863, player, 1).isEmpty(), "rares disabled outside MyWorld");
			drops.unload();
			drops.load();
			check(!drops.isDemon(870) && drops.getDropTable(863) == null, "other-world registration unaffected");
			h.server().getConfig().WANT_MYWORLD = true;
			drops.unload();
			drops.load();
			h.random().reset(1);
			h.random().scriptInts(0, 0).scriptDoubles(0.0);
			assertDrop(drops.rollSlayerComponentDrops(863, player, 1), MyWorldItemId.STICKY_SALIVA_GLAND);
			System.out.println("PASS Slayer components: definitions, quantities, exact odds, independence, luck, contribution, ground delivery and world gating");
		}
	}

	private static GroundItem ground(CurrentCombatHarness h, Npc npc, Player observer, int id) {
		// Region lookup routes to layered membership when NPCs are regionless.
		return h.world().getRegionManager().getRegion(npc.getLocation()).getItem(id, npc.getLocation(), observer);
	}

	private static void assertDrop(List<Item> items, int id) {
		check(items.size() == 1 && items.get(0).getCatalogId() == id && items.get(0).getAmount() == 1,
			"exactly one component " + id + ": " + items);
	}
	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
