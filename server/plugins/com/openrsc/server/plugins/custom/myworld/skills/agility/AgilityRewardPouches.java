package com.openrsc.server.plugins.custom.myworld.skills.agility;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.MessageType;

import java.util.ArrayList;

import static com.openrsc.server.plugins.Functions.delay;
import static com.openrsc.server.plugins.Functions.mes;
import static com.openrsc.server.plugins.Functions.thinkbubble;

public final class AgilityRewardPouches implements OpInvTrigger {

	private static final int RARE_TABLE_SCALE = 128;
	private static final int TIER_1_RARE_WEIGHT = 6;
	private static final int TIER_2_RARE_WEIGHT = 9;
	private static final int TIER_3_RARE_WEIGHT = 12;

	private static final int TIER_1_POUCH_ID = ItemId.TIER_1_AGILITY_POUCH.id();
	private static final int TIER_2_POUCH_ID = ItemId.TIER_2_AGILITY_POUCH.id();
	private static final int TIER_3_POUCH_ID = ItemId.TIER_3_AGILITY_POUCH.id();

	private static final TierConfig TIER_1_CONFIG = new TierConfig(2, 4, TIER_1_RARE_WEIGHT,
		new RewardCategory("Ore", 14,
			new RewardOption(ItemId.TIN_ORE.id(), 20, 35, 24, true),
			new RewardOption(ItemId.COPPER_ORE.id(), 20, 35, 24, true),
			new RewardOption(ItemId.IRON_ORE.id(), 12, 24, 18, true),
			new RewardOption(ItemId.COAL.id(), 8, 18, 12, true)),
		new RewardCategory("Logs", 12,
			new RewardOption(ItemId.PINE_LOGS.id(), 25, 45, 24, true),
			new RewardOption(ItemId.OAK_LOGS.id(), 18, 35, 18, true),
			new RewardOption(ItemId.WILLOW_LOGS.id(), 10, 22, 10, true)),
		new RewardCategory("Runes", 18,
			new RewardOption(ItemId.AIR_RUNE.id(), 5, 10, 28, false),
			new RewardOption(ItemId.WATER_RUNE.id(), 5, 10, 24, false),
			new RewardOption(ItemId.EARTH_RUNE.id(), 5, 10, 24, false),
			new RewardOption(ItemId.FIRE_RUNE.id(), 5, 10, 24, false),
			new RewardOption(ItemId.MIND_RUNE.id(), 5, 10, 18, false),
			new RewardOption(ItemId.BODY_RUNE.id(), 5, 10, 14, false),
			new RewardOption(ItemId.COSMIC_RUNE.id(), 5, 10, 8, false),
			new RewardOption(ItemId.CHAOS_RUNE.id(), 5, 10, 6, false)),
		new RewardCategory("Arrows", 14,
			new RewardOption(ItemId.TIN_ARROWS.id(), 5, 10, 28, false),
			new RewardOption(ItemId.COPPER_ARROWS.id(), 5, 10, 24, false),
			new RewardOption(ItemId.BRONZE_ARROWS.id(), 5, 10, 18, false),
			new RewardOption(ItemId.IRON_ARROWS.id(), 5, 10, 14, false),
			new RewardOption(ItemId.STEEL_ARROWS.id(), 5, 10, 10, false)),
		new RewardCategory("Gems", 8,
			new RewardOption(ItemId.UNCUT_SAPPHIRE.id(), 2, 5, 18, true),
			new RewardOption(ItemId.UNCUT_EMERALD.id(), 1, 3, 10, true)),
		new RewardCategory("Herbs", 10,
			new RewardOption(ItemId.UNIDENTIFIED_GUAM_LEAF.id(), 3, 6, 24, true),
			new RewardOption(ItemId.UNIDENTIFIED_MARRENTILL.id(), 2, 5, 16, true),
			new RewardOption(ItemId.UNIDENTIFIED_TARROMIN.id(), 2, 4, 10, true)),
		new RewardCategory("Ingredients", 8,
			new RewardOption(ItemId.EYE_OF_NEWT.id(), 8, 16, 18, true),
			new RewardOption(ItemId.RED_SPIDERS_EGGS.id(), 4, 8, 12, true),
			new RewardOption(ItemId.LIMPWURT_ROOT.id(), 3, 6, 8, true)),
		new RewardCategory("Food", 8,
			new RewardOption(ItemId.SALMON.id(), 6, 10, 16, true),
			new RewardOption(ItemId.TUNA.id(), 5, 9, 12, true),
			new RewardOption(ItemId.LOBSTER.id(), 3, 6, 8, true)),
		new RewardCategory("Potions", 8,
			new RewardOption(ItemId.FULL_ATTACK_POTION.id(), 1, 2, 16, true),
			new RewardOption(ItemId.FULL_STRENGTH_POTION.id(), 1, 2, 12, true),
			new RewardOption(ItemId.FULL_DEFENSE_POTION.id(), 1, 2, 12, true),
			new RewardOption(ItemId.FULL_STAT_RESTORATION_POTION.id(), 1, 2, 10, true),
			new RewardOption(ItemId.FULL_RESTORE_PRAYER_POTION.id(), 1, 1, 6, true)));

	private static final TierConfig TIER_2_CONFIG = new TierConfig(2, 4, TIER_2_RARE_WEIGHT,
		new RewardCategory("Ore", 12,
			new RewardOption(ItemId.IRON_ORE.id(), 20, 35, 18, true),
			new RewardOption(ItemId.COAL.id(), 18, 30, 18, true),
			new RewardOption(ItemId.MITHRIL_ORE.id(), 8, 16, 10, true)),
		new RewardCategory("Logs", 10,
			new RewardOption(ItemId.OAK_LOGS.id(), 25, 40, 18, true),
			new RewardOption(ItemId.WILLOW_LOGS.id(), 18, 30, 18, true),
			new RewardOption(ItemId.MAPLE_LOGS.id(), 10, 20, 10, true)),
		new RewardCategory("Runes", 16,
			new RewardOption(ItemId.COSMIC_RUNE.id(), 5, 10, 16, false),
			new RewardOption(ItemId.CHAOS_RUNE.id(), 5, 10, 16, false),
			new RewardOption(ItemId.NATURE_RUNE.id(), 5, 10, 10, false),
			new RewardOption(ItemId.LAW_RUNE.id(), 5, 10, 6, false),
			new RewardOption(ItemId.DEATH_RUNE.id(), 5, 10, 4, false)),
		new RewardCategory("Arrows", 14,
			new RewardOption(ItemId.IRON_ARROWS.id(), 5, 10, 18, false),
			new RewardOption(ItemId.STEEL_ARROWS.id(), 5, 10, 18, false),
			new RewardOption(ItemId.MITHRIL_ARROWS.id(), 5, 10, 12, false)),
		new RewardCategory("Gems", 8,
			new RewardOption(ItemId.UNCUT_SAPPHIRE.id(), 3, 6, 12, true),
			new RewardOption(ItemId.UNCUT_EMERALD.id(), 2, 5, 12, true),
			new RewardOption(ItemId.UNCUT_RUBY.id(), 1, 3, 6, true)),
		new RewardCategory("Herbs", 10,
			new RewardOption(ItemId.UNIDENTIFIED_MARRENTILL.id(), 3, 6, 14, true),
			new RewardOption(ItemId.UNIDENTIFIED_TARROMIN.id(), 3, 5, 14, true),
			new RewardOption(ItemId.UNIDENTIFIED_HARRALANDER.id(), 2, 4, 10, true),
			new RewardOption(ItemId.UNIDENTIFIED_RANARR_WEED.id(), 1, 3, 6, true)),
		new RewardCategory("Ingredients", 8,
			new RewardOption(ItemId.RED_SPIDERS_EGGS.id(), 5, 10, 12, true),
			new RewardOption(ItemId.LIMPWURT_ROOT.id(), 4, 8, 12, true),
			new RewardOption(ItemId.SNAPE_GRASS.id(), 4, 8, 8, true)),
		new RewardCategory("Food", 8,
			new RewardOption(ItemId.TUNA.id(), 6, 10, 12, true),
			new RewardOption(ItemId.LOBSTER.id(), 5, 8, 14, true),
			new RewardOption(ItemId.SWORDFISH.id(), 4, 7, 10, true)),
		new RewardCategory("Potions", 8,
			new RewardOption(ItemId.FULL_ATTACK_POTION.id(), 1, 2, 12, true),
			new RewardOption(ItemId.FULL_STRENGTH_POTION.id(), 1, 2, 12, true),
			new RewardOption(ItemId.FULL_DEFENSE_POTION.id(), 1, 2, 12, true),
			new RewardOption(ItemId.FULL_STAT_RESTORATION_POTION.id(), 1, 2, 8, true),
			new RewardOption(ItemId.FULL_RESTORE_PRAYER_POTION.id(), 1, 2, 8, true)));

	private static final TierConfig TIER_3_CONFIG = new TierConfig(3, 4, TIER_3_RARE_WEIGHT,
		new RewardCategory("Ore", 12,
			new RewardOption(ItemId.COAL.id(), 25, 40, 14, true),
			new RewardOption(ItemId.MITHRIL_ORE.id(), 15, 24, 14, true),
			new RewardOption(ItemId.ADAMANTITE_ORE.id(), 8, 14, 10, true),
			new RewardOption(ItemId.RUNITE_ORE.id(), 2, 5, 4, true)),
		new RewardCategory("Logs", 10,
			new RewardOption(ItemId.WILLOW_LOGS.id(), 20, 35, 10, true),
			new RewardOption(ItemId.MAPLE_LOGS.id(), 18, 30, 14, true),
			new RewardOption(ItemId.YEW_LOGS.id(), 8, 16, 10, true),
			new RewardOption(ItemId.MAGIC_LOGS.id(), 3, 7, 4, true)),
		new RewardCategory("Runes", 16,
			new RewardOption(ItemId.LAW_RUNE.id(), 5, 10, 12, false),
			new RewardOption(ItemId.DEATH_RUNE.id(), 5, 10, 12, false),
			new RewardOption(ItemId.BLOOD_RUNE.id(), 5, 10, 8, false),
			new RewardOption(ItemId.SOUL_RUNE.id(), 5, 10, 6, false)),
		new RewardCategory("Arrows", 14,
			new RewardOption(ItemId.MITHRIL_ARROWS.id(), 5, 10, 12, false),
			new RewardOption(ItemId.TITAN_STEEL_ARROWS.id(), 5, 10, 12, false),
			new RewardOption(ItemId.ORICHALCUM_ARROWS.id(), 5, 10, 10, false),
			new RewardOption(ItemId.RUNE_ARROWS.id(), 5, 10, 6, false)),
		new RewardCategory("Gems", 8,
			new RewardOption(ItemId.UNCUT_EMERALD.id(), 3, 6, 10, true),
			new RewardOption(ItemId.UNCUT_RUBY.id(), 2, 4, 12, true),
			new RewardOption(ItemId.UNCUT_DIAMOND.id(), 1, 3, 8, true)),
		new RewardCategory("Herbs", 10,
			new RewardOption(ItemId.UNIDENTIFIED_RANARR_WEED.id(), 2, 4, 10, true),
			new RewardOption(ItemId.UNIDENTIFIED_IRIT_LEAF.id(), 2, 4, 12, true),
			new RewardOption(ItemId.UNIDENTIFIED_AVANTOE.id(), 2, 4, 10, true),
			new RewardOption(ItemId.UNIDENTIFIED_KWUARM.id(), 1, 3, 8, true),
			new RewardOption(ItemId.UNIDENTIFIED_CADANTINE.id(), 1, 2, 6, true)),
		new RewardCategory("Ingredients", 8,
			new RewardOption(ItemId.LIMPWURT_ROOT.id(), 5, 10, 10, true),
			new RewardOption(ItemId.SNAPE_GRASS.id(), 5, 10, 10, true),
			new RewardOption(ItemId.WHITE_BERRIES.id(), 4, 8, 8, true)),
		new RewardCategory("Food", 8,
			new RewardOption(ItemId.LOBSTER.id(), 6, 10, 10, true),
			new RewardOption(ItemId.SWORDFISH.id(), 5, 9, 12, true),
			new RewardOption(ItemId.SHARK.id(), 3, 6, 8, true)),
		new RewardCategory("Potions", 8,
			new RewardOption(ItemId.FULL_RESTORE_PRAYER_POTION.id(), 1, 2, 12, true),
			new RewardOption(ItemId.FULL_SUPER_ATTACK_POTION.id(), 1, 2, 10, true),
			new RewardOption(ItemId.FULL_SUPER_DEFENSE_POTION.id(), 1, 2, 10, true),
			new RewardOption(ItemId.FULL_SUPER_STRENGTH_POTION.id(), 1, 2, 10, true)));

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
		final TierConfig config = getTierConfig(pouchId);
		final ArrayList<Item> rewards = new ArrayList<>();
		final ArrayList<RewardCategory> availableCategories = new ArrayList<>();
		for (final RewardCategory category : config.categories) {
			availableCategories.add(category);
		}

		final int desiredDrops = DataConversions.random(config.minDrops, config.maxDrops);
		final int localDrops = Math.min(desiredDrops, availableCategories.size());
		for (int i = 0; i < localDrops; i++) {
			final RewardCategory category = rollCategory(availableCategories);
			if (category == null) {
				break;
			}
			availableCategories.remove(category);
			rewards.add(category.rollItem(player));
		}

		final Item rareReward = maybeRollRareTable(player, config.rareWeight);
		if (rareReward != null) {
			rewards.add(rareReward);
		}

		if (rewards.isEmpty()) {
			rewards.add(new Item(ItemId.COINS.id(), 250));
		}
		return rewards;
	}

	private static RewardCategory rollCategory(final ArrayList<RewardCategory> availableCategories) {
		int totalWeight = 0;
		for (final RewardCategory category : availableCategories) {
			totalWeight += category.weight;
		}
		if (totalWeight <= 0) {
			return null;
		}

		int hit = DataConversions.random(0, totalWeight - 1);
		int sum = 0;
		for (final RewardCategory category : availableCategories) {
			sum += category.weight;
			if (hit < sum) {
				return category;
			}
		}
		return availableCategories.get(availableCategories.size() - 1);
	}

	private static Item maybeRollRareTable(final Player player, final int rareWeight) {
		if (rareWeight <= 0 || DataConversions.random(0, RARE_TABLE_SCALE - 1) >= rareWeight) {
			return null;
		}
		if (player.getWorld().getNpcDrops().getRareDropTable() == null) {
			return null;
		}

		final ArrayList<Item> rareRoll = player.getWorld().getNpcDrops().getRareDropTable().rollItem(player);
		return rareRoll.isEmpty() ? null : rareRoll.get(0);
	}

	private static TierConfig getTierConfig(final int pouchId) {
		if (pouchId == TIER_1_POUCH_ID) {
			return TIER_1_CONFIG;
		}
		if (pouchId == TIER_2_POUCH_ID) {
			return TIER_2_CONFIG;
		}
		if (pouchId == TIER_3_POUCH_ID) {
			return TIER_3_CONFIG;
		}
		throw new IllegalArgumentException("Unsupported agility pouch id: " + pouchId);
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

	private static final class TierConfig {
		private final int minDrops;
		private final int maxDrops;
		private final int rareWeight;
		private final RewardCategory[] categories;

		private TierConfig(final int minDrops, final int maxDrops, final int rareWeight, final RewardCategory... categories) {
			this.minDrops = minDrops;
			this.maxDrops = maxDrops;
			this.rareWeight = rareWeight;
			this.categories = categories;
		}
	}

	private static final class RewardCategory {
		private final String name;
		private final int weight;
		private final RewardOption[] options;

		private RewardCategory(final String name, final int weight, final RewardOption... options) {
			this.name = name;
			this.weight = weight;
			this.options = options;
		}

		private Item rollItem(final Player player) {
			int totalWeight = 0;
			for (final RewardOption option : options) {
				totalWeight += option.weight;
			}
			if (totalWeight <= 0) {
				throw new IllegalStateException("Reward category has no weight: " + name);
			}

			int hit = DataConversions.random(0, totalWeight - 1);
			int sum = 0;
			for (final RewardOption option : options) {
				sum += option.weight;
				if (hit < sum) {
					return option.rollItem(player);
				}
			}
			return options[options.length - 1].rollItem(player);
		}
	}

	private static final class RewardOption {
		private final int itemId;
		private final int minAmount;
		private final int maxAmount;
		private final int weight;
		private final boolean preferNoted;

		private RewardOption(final int itemId, final int minAmount, final int maxAmount, final int weight, final boolean preferNoted) {
			this.itemId = itemId;
			this.minAmount = minAmount;
			this.maxAmount = maxAmount;
			this.weight = weight;
			this.preferNoted = preferNoted;
		}

		private Item rollItem(final Player player) {
			final int amount = DataConversions.random(minAmount, maxAmount);
			final ItemDefinition definition = player.getWorld().getServer().getEntityHandler().getItemDef(itemId);
			final boolean noted = preferNoted && definition != null && definition.isNoteable();
			return new Item(itemId, amount, noted);
		}
	}
}
