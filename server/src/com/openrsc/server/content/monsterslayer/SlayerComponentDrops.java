package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.content.DropTable;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.runtime.GameRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Independent component rolls, separate from ordinary loot and Slayer task credit. */
public final class SlayerComponentDrops {
	private final Map<Integer, List<DropTable>> componentRolls = new HashMap<>();

	public SlayerComponentDrops() {
        // Independent uncommon roll: one raw scale, base chance 1/16.
        // Keep the naga's existing hide, supplies and shared rare table intact.
        DropTable scales = new DropTable("Naga scales");
        scales.addItemDrop(MyWorldItemId.NAGA_SCALES, 1, 100);
        scales.addEmptyDrop(1500);
        componentRolls.computeIfAbsent(NagaCombat.NPC_ID, ignored -> new ArrayList<>()).add(scales);

        add(StirgeCombat.NPC_ID, MyWorldItemId.SANGUINARY_GLAND, 128);
		add(GiantFrogCombat.NPC_ID, MyWorldItemId.STICKY_SALIVA_GLAND, 128);
		add(CockatriceCombat.NPC_ID, MyWorldItemId.COCKATRICE_EYE, 128);
		add(BansheeCombat.NPC_ID, MyWorldItemId.FROZEN_TEAR, 1000);
		add(TerrorDogCombat.NPC_ID, MyWorldItemId.TERROR_FANG, 1000);
		add(BloodveldCombat.NPC_ID, MyWorldItemId.LEACH_TONGUE, 1000);
		add(DarkBeastCombat.NPC_ID, MyWorldItemId.LIGHTNING_HORN, 2000);
		add(AbyssalDemonCombat.NPC_ID, MyWorldItemId.ABYSSAL_VERTIBRAE, 128);
		add(AbyssalDemonCombat.NPC_ID, MyWorldItemId.ABYSSAL_RIB, 2000);
	}

	private void add(int npcId, int itemId, int denominator) {
		DropTable reward = new DropTable("Slayer component " + itemId, true);
		reward.addItemDrop(itemId, 1, 1);
		DropTable access = new DropTable("Slayer component access " + itemId);
		// Same rare-table weight and wealth-ring rules as ordinary rare loot.
		// Hundredths preserve small luck bonuses when DropTable rounds weights.
		access.addTableDrop(reward, 100);
		access.addEmptyDrop((denominator - 1) * 100);
		componentRolls.computeIfAbsent(npcId, ignored -> new ArrayList<>()).add(access);
	}

	public ArrayList<Item> rollRare(int npcId, Player owner, double contributionScale) {
		ArrayList<Item> result = new ArrayList<>();
		if (owner == null || !owner.getConfig().WANT_MYWORLD) return result;
		List<DropTable> tables = componentRolls.get(npcId);
		if (tables != null) {
			for (DropTable table : tables) {
				// Each component gets its own roll; rare components keep the existing
				// contribution/luck rules. Common scales use ordinary loot rules.
				result.addAll(table.rollPersonalLoot(owner, contributionScale));
			}
		}
		return result;
	}

	/** Base quantities, before the existing guaranteed-drop equipment bonus. */
	public Item baseline(int npcId, GameRandom random) {
		switch (npcId) {
			case GiantFrogCombat.NPC_ID: return new Item(MyWorldItemId.GIANT_FROG_HIDE);
			case CockatriceCombat.NPC_ID:
				return new Item(MyWorldItemId.COCKATRICE_FEATHERS, 1 + random.nextInt(3));
			case BansheeCombat.NPC_ID: return new Item(MyWorldItemId.ECTOPLASM);
			case NagaCombat.NPC_ID: return new Item(MyWorldItemId.NAGA_HIDE);
			case TerrorDogCombat.NPC_ID: return new Item(MyWorldItemId.TERROR_DOG_HIDE);
			case BloodveldCombat.NPC_ID: return new Item(MyWorldItemId.BLOODVELD_HIDE);
			case DarkBeastCombat.NPC_ID: return new Item(MyWorldItemId.DARK_BEAST_HIDE);
			case AbyssalDemonCombat.NPC_ID:
				return new Item(MyWorldItemId.SLIMEY_RESIDUE, 1 + random.nextInt(3));
			default: return null;
		}
	}
}
