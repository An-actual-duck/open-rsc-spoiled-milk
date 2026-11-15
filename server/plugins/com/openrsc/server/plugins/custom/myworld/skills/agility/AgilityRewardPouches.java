package com.openrsc.server.plugins.custom.myworld.skills.agility;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import com.openrsc.server.util.rsc.MessageType;

import java.util.ArrayList;

import static com.openrsc.server.plugins.Functions.delay;
import static com.openrsc.server.plugins.Functions.mes;
import static com.openrsc.server.plugins.Functions.thinkbubble;

public final class AgilityRewardPouches implements OpInvTrigger {


	private static final int TIER_1_POUCH_ID = ItemId.TIER_1_AGILITY_POUCH.id();
	private static final int TIER_2_POUCH_ID = ItemId.TIER_2_AGILITY_POUCH.id();
	private static final int TIER_3_POUCH_ID = ItemId.TIER_3_AGILITY_POUCH.id();

	public static void awardCompletionPouch(final Player player, final int pouchId) {
		final Item pouch = new Item(pouchId, 1);
		player.getCarriedItems().getInventory().add(pouch);
		player.playerServerMessage(MessageType.QUEST, "You receive a " + pouch.getDef(player.getWorld()).getName() + ".");
	}

	@Override
	public boolean blockOpInv(final Player player, final Integer invIndex, final Item item, final String command) {
		return isAgilityPouch(item.getCatalogId());
	}

	@Override
	public void onOpInv(final Player player, final Integer invIndex, final Item item, final String command) {
		final int pouchId = item.getCatalogId();
		if (!isAgilityPouch(pouchId)) {
			return;
		}

        // Validate the configured table before opening/consuming a chest.
        com.openrsc.server.content.AgilityPouchRewards.forPlayer(player);
		thinkbubble(item);
		mes("You loosen the tie and open the pouch.");
		delay(2);

		if (player.getCarriedItems().remove(new Item(pouchId, 1)) == -1) {
			return;
		}

		final ArrayList<Item> rewards = rollRewardItems(player, pouchId);
		for (final Item reward : rewards) {
			player.getCarriedItems().getInventory().add(reward);
		}
		player.playerServerMessage(MessageType.QUEST, "You pull out " + formatRewardList(rewards, player) + ".");
	}

	private static ArrayList<Item> rollRewardItems(final Player player, final int pouchId) {
        com.openrsc.server.content.AgilityPouchRewards.Catalog table=com.openrsc.server.content.AgilityPouchRewards.forPlayer(player);
        ArrayList<Item> rewards=table.rollNormal(player,pouchId,player.getWorld().getServer().getCombatRandom());
        rewards.addAll(com.openrsc.server.content.AgilityUtilityEquipment.rollUniques(player,
            com.openrsc.server.content.AgilityUtilityEquipment.pouchTier(pouchId),player.getWorld().getServer().getCombatRandom()));
        return rewards;
    }

	private static boolean isAgilityPouch(final int itemId) {
		return itemId == TIER_1_POUCH_ID
			|| itemId == TIER_2_POUCH_ID
			|| itemId == TIER_3_POUCH_ID;
	}

	private static String formatRewardList(final ArrayList<Item> rewards, final Player player) {
		final ArrayList<String> parts = new ArrayList<>();
		for (final Item reward : rewards) {
			parts.add(formatReward(reward, player));
		}
		if (parts.size() == 1) {
			return parts.get(0);
		}
		if (parts.size() == 2) {
			return parts.get(0) + " and " + parts.get(1);
		}

		final StringBuilder builder = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				builder.append(i == parts.size() - 1 ? ", and " : ", ");
			}
			builder.append(parts.get(i));
		}
		return builder.toString();
	}

	private static String formatReward(final Item reward, final Player player) {
		final String rewardName = reward.getDef(player.getWorld()).getName();
		final String prefix = reward.getNoted() ? "noted " : "";
		return reward.getAmount() > 1 ? reward.getAmount() + " x " + prefix + rewardName : "a " + prefix + rewardName;
	}

}
