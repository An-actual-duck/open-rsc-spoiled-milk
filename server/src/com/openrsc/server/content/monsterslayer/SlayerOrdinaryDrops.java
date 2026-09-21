package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.DropTable;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** MyWorld tower supplies and independent equipment drops; component odds are untouched. */
public final class SlayerOrdinaryDrops {
	private final Map<Integer, List<DropTable>> equipment = new HashMap<>();

	public SlayerOrdinaryDrops() {
		addEquipment(BloodveldCombat.NPC_ID, ItemId.ORICHALCUM_LARGE_HELMET, 1000);
		addEquipment(BloodveldCombat.NPC_ID, ItemId.ORICHALCUM_GREAVES, 1000);
		addEquipment(BloodveldCombat.NPC_ID, ItemId.ORICHALCUM_GAUNTLETS, 1000);
		addEquipment(BloodveldCombat.NPC_ID, ItemId.ORICHALCUM_PLATE_MAIL_LEGS, 2000);
		addEquipment(BloodveldCombat.NPC_ID, ItemId.ORICHALCUM_PLATE_MAIL_BODY, 2000);
		addEquipment(DarkBeastCombat.NPC_ID, ItemId.BLOOD_LONGBOW, 2000);
		addEquipment(DarkBeastCombat.NPC_ID, ItemId.LARGE_RUNE_HELMET, 1000);
		addEquipment(DarkBeastCombat.NPC_ID, ItemId.RUNITE_GAUNTLETS, 1000);
		addEquipment(DarkBeastCombat.NPC_ID, ItemId.RUNITE_GREAVES, 1000);
		addEquipment(AbyssalDemonCombat.NPC_ID, ItemId.RUNE_PLATE_MAIL_LEGS, 1000);
	}

	/** Called after shared rare tables and existing-family adjustments have loaded. */
	public void register(Map<Integer, DropTable> target, DropTable rare) {
		DropTable table;

		// Giant frog: Herbs, 64/128 of the ordinary roll.
		table = new DropTable("Giant frog Slayer supplies");
		table.addItemDrop(ItemId.UNIDENTIFIED_GUAM_LEAF.id(), 1, 20, true);
		table.addItemDrop(ItemId.UNIDENTIFIED_MARRENTILL.id(), 1, 16, true);
		table.addItemDrop(ItemId.UNIDENTIFIED_TARROMIN.id(), 1, 12, true);
		table.addItemDrop(ItemId.UNIDENTIFIED_HARRALANDER.id(), 1, 8, true);
		table.addItemDrop(ItemId.UNIDENTIFIED_RANARR_WEED.id(), 1, 6, true);
		table.addItemDrop(ItemId.UNIDENTIFIED_IRIT_LEAF.id(), 1, 2, true);
		addCoins(table, 20);
		table.addItemDrop(ItemId.WATER_RUNE.id(), 10, 8);
		table.addItemDrop(ItemId.IRON_ARROWS.id(), 10, 8);
		table.addItemDrop(ItemId.FEATHER.id(), 15, 8);
		table.addItemDrop(ItemId.COPPER_ORE.id(), 3, 4, true);
		finish(target, GiantFrogCombat.NPC_ID, table, rare);

		// Cockatrice: Gems, 64/128 of the ordinary roll.
		table = new DropTable("Cockatrice Slayer supplies");
		table.addItemDrop(ItemId.UNCUT_SAPPHIRE.id(), 1, 28);
		table.addItemDrop(ItemId.UNCUT_EMERALD.id(), 1, 20);
		table.addItemDrop(ItemId.UNCUT_RUBY.id(), 1, 12);
		table.addItemDrop(ItemId.UNCUT_DIAMOND.id(), 1, 4);
		addCoins(table, 35);
		table.addItemDrop(ItemId.AIR_RUNE.id(), 20, 8);
		table.addItemDrop(ItemId.IRON_ARROWS.id(), 15, 8);
		table.addItemDrop(ItemId.COAL.id(), 3, 8, true);
		table.addItemDrop(ItemId.SILVER_BAR.id(), 1, 4, true);
		finish(target, CockatriceCombat.NPC_ID, table, rare);

		// Banshee: Runes, 64/128 of the ordinary roll.
		table = new DropTable("Banshee Slayer supplies");
		table.addItemDrop(ItemId.AIR_RUNE.id(), 50, 12);
		table.addItemDrop(ItemId.WATER_RUNE.id(), 50, 12);
		table.addItemDrop(ItemId.EARTH_RUNE.id(), 50, 12);
		table.addItemDrop(ItemId.FIRE_RUNE.id(), 50, 12);
		table.addItemDrop(ItemId.CHAOS_RUNE.id(), 15, 8);
		table.addItemDrop(ItemId.DEATH_RUNE.id(), 5, 4);
		table.addItemDrop(ItemId.LAW_RUNE.id(), 3, 4);
		addCoins(table, 50);
		table.addItemDrop(ItemId.UNIDENTIFIED_HARRALANDER.id(), 1, 8, true);
		table.addItemDrop(ItemId.STEEL_ARROWS.id(), 15, 8);
		table.addItemDrop(ItemId.COAL.id(), 5, 8, true);
		table.addItemDrop(ItemId.GOLD_BAR.id(), 1, 4, true);
		finish(target, BansheeCombat.NPC_ID, table, rare);

		// Naga: Food, 64/128 of the ordinary roll.
		table = new DropTable("Naga Slayer supplies");
		table.addItemDrop(ItemId.SALMON.id(), 4, 20, true);
		table.addItemDrop(ItemId.TUNA.id(), 4, 20, true);
		table.addItemDrop(ItemId.LOBSTER.id(), 3, 16, true);
		table.addItemDrop(ItemId.SWORDFISH.id(), 2, 8, true);
		addCoins(table, 60);
		table.addItemDrop(ItemId.WATER_RUNE.id(), 30, 8);
		table.addItemDrop(ItemId.STEEL_ARROWS.id(), 20, 8);
		table.addItemDrop(ItemId.COAL.id(), 5, 8, true);
		table.addItemDrop(ItemId.UNCUT_SAPPHIRE.id(), 1, 4);
		finish(target, NagaCombat.NPC_ID, table, rare);

		// Terror dog: Ammo, 64/128 of the ordinary roll.
		table = new DropTable("Terror dog Slayer supplies");
		table.addItemDrop(ItemId.STEEL_ARROWS.id(), 40, 16);
		table.addItemDrop(ItemId.MITHRIL_ARROWS.id(), 30, 16);
		table.addItemDrop(ItemId.TITAN_STEEL_ARROWS.id(), 20, 8);
		table.addItemDrop(ItemId.STEEL_BOLTS.id(), 30, 8);
		table.addItemDrop(ItemId.MITHRIL_BOLTS.id(), 20, 8);
		table.addItemDrop(ItemId.MITHRIL_SHURIKEN.id(), 10, 8);
		addCoins(table, 75);
		table.addItemDrop(ItemId.CHAOS_RUNE.id(), 10, 8);
		table.addItemDrop(ItemId.COAL.id(), 8, 8, true);
		table.addItemDrop(ItemId.UNIDENTIFIED_HARRALANDER.id(), 2, 8, true);
		table.addItemDrop(ItemId.GOLD_BAR.id(), 2, 4, true);
		finish(target, TerrorDogCombat.NPC_ID, table, rare);

		// Bloodveld: Higher-tier herbs, 64/128 of the ordinary roll.
		table = new DropTable("Bloodveld Slayer supplies");
		table.addItemDrop(ItemId.UNIDENTIFIED_IRIT_LEAF.id(), 2, 14, true);
		table.addItemDrop(ItemId.UNIDENTIFIED_AVANTOE.id(), 2, 14, true);
		table.addItemDrop(ItemId.UNIDENTIFIED_KWUARM.id(), 2, 12, true);
		table.addItemDrop(ItemId.UNIDENTIFIED_CADANTINE.id(), 2, 10, true);
		table.addItemDrop(ItemId.UNIDENTIFIED_DWARF_WEED.id(), 2, 10, true);
		table.addItemDrop(ItemId.UNIDENTIFIED_TORSTOL.id(), 1, 4, true);
		addCoins(table, 85);
		table.addItemDrop(ItemId.NATURE_RUNE.id(), 8, 8);
		table.addItemDrop(ItemId.MITHRIL_ARROWS.id(), 20, 8);
		table.addItemDrop(ItemId.COAL.id(), 10, 8, true);
		table.addItemDrop(ItemId.UNCUT_RUBY.id(), 1, 4);
		finish(target, BloodveldCombat.NPC_ID, table, rare);

		// Dark beast: Higher-tier runes, 64/128 of the ordinary roll.
		table = new DropTable("Dark beast Slayer supplies");
		table.addItemDrop(ItemId.CHAOS_RUNE.id(), 35, 8);
		table.addItemDrop(ItemId.NATURE_RUNE.id(), 15, 12);
		table.addItemDrop(ItemId.LAW_RUNE.id(), 15, 12);
		table.addItemDrop(ItemId.DEATH_RUNE.id(), 20, 16);
		table.addItemDrop(ItemId.BLOOD_RUNE.id(), 10, 16);
		addCoins(table, 105);
		table.addItemDrop(ItemId.ADAMANTITE_ARROWS.id(), 15, 8);
		table.addItemDrop(ItemId.COAL.id(), 12, 8, true);
		table.addItemDrop(ItemId.GOLD_BAR.id(), 3, 8, true);
		table.addItemDrop(ItemId.UNCUT_DIAMOND.id(), 1, 4);
		finish(target, DarkBeastCombat.NPC_ID, table, rare);

		// Abyssal demon: Higher-tier ammo, 64/128 of the ordinary roll.
		table = new DropTable("Abyssal demon Slayer supplies");
		table.addItemDrop(ItemId.ADAMANTITE_ARROWS.id(), 30, 16);
		table.addItemDrop(ItemId.ORICHALCUM_ARROWS.id(), 20, 16);
		table.addItemDrop(ItemId.RUNE_ARROWS.id(), 10, 8);
		table.addItemDrop(ItemId.ADAMANTITE_BOLTS.id(), 20, 8);
		table.addItemDrop(ItemId.ORICHALCUM_BOLTS.id(), 15, 8);
		table.addItemDrop(ItemId.RUNE_SHURIKEN.id(), 5, 8);
		addCoins(table, 125);
		table.addItemDrop(ItemId.DEATH_RUNE.id(), 15, 8);
		table.addItemDrop(ItemId.COAL.id(), 15, 8, true);
		table.addItemDrop(ItemId.MITHRIL_BAR.id(), 3, 8, true);
		table.addItemDrop(ItemId.UNCUT_DIAMOND.id(), 1, 4);
		finish(target, AbyssalDemonCombat.NPC_ID, table, rare);
	}

	private static void addCoins(DropTable table, int combatLevel) {
		table.addItemDrop(ItemId.COINS.id(), combatLevel, 20);
		table.addItemDrop(ItemId.COINS.id(), combatLevel * 3, 10);
		table.addItemDrop(ItemId.COINS.id(), combatLevel * 8, 2);
	}

	private static void finish(Map<Integer, DropTable> target, int npcId, DropTable table, DropTable rare) {
		table.addTableDrop(rare, 4);
		if (table.getTotalWeight() != 128) throw new IllegalStateException("Slayer loot budget: " + npcId);
		target.put(npcId, table);
	}

	private void addEquipment(int npcId, ItemId item, int denominator) {
		DropTable reward = new DropTable("Slayer equipment " + item.id(), true);
		reward.addItemDrop(item.id(), 1, 1);
		DropTable access = new DropTable("Slayer equipment access " + item.id());
		// Hundredths retain fractional potion-luck weighting, as with Slayer components.
		access.addTableDrop(reward, 100);
		access.addEmptyDrop((denominator - 1) * 100);
		equipment.computeIfAbsent(npcId, ignored -> new ArrayList<>()).add(access);
	}

	public ArrayList<Item> rollEquipment(int npcId, Player owner, double contributionScale) {
		ArrayList<Item> result = new ArrayList<>();
		if (owner == null || !owner.getConfig().WANT_MYWORLD) return result;
		List<DropTable> tables = equipment.get(npcId);
		if (tables != null) {
			for (DropTable table : tables) result.addAll(table.rollPersonalLoot(owner, contributionScale));
		}
		return result;
	}
}
