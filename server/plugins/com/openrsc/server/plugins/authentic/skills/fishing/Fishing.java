package com.openrsc.server.plugins.authentic.skills.fishing;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Skills;
import com.openrsc.server.external.EntityHandler;
import com.openrsc.server.external.GameObjectDef;
import com.openrsc.server.external.ObjectFishDef;
import com.openrsc.server.external.ObjectFishingDef;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.plugins.triggers.UseLocTrigger;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;
import com.openrsc.server.util.rsc.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.openrsc.server.plugins.Functions.*;

public class Fishing implements OpLocTrigger, UseLocTrigger {

	private static final Logger LOGGER = LogManager.getLogger(Fishing.class);
	public static final int TUTORIAL_FISH_ID = 493;
	public static final int DEPLETED_FISH_ROCK_ID = 668;
	private static final int[] MYWORLD_FISHING_OBJECT_IDS = {192, 193, 194, 261, 271, 376, 493, 557};
	private static final int[] MYWORLD_ROD_IDS = {
		ItemId.FISHING_ROD.id(),
		ItemId.PINE_FISHING_ROD.id(),
		ItemId.OAK_FISHING_ROD.id(),
		ItemId.WILLOW_FISHING_ROD.id(),
		ItemId.PALM_FISHING_ROD.id(),
		ItemId.MAPLE_FISHING_ROD.id(),
		ItemId.YEW_FISHING_ROD.id(),
		ItemId.EBONY_FISHING_ROD.id(),
		ItemId.MAGIC_FISHING_ROD.id(),
		ItemId.BLOOD_FISHING_ROD.id()
	};
	private static final int[] MYWORLD_ROD_LEVELS = {1, 8, 15, 22, 30, 38, 46, 54, 62, 70};
	private static final int MYWORLD_ROD_CATCH_TIER_SPAN = 3;
	public static final int FISHING_FOCUS_NO_FINDS = Skills.CONTROLLED_MODE;
	public static final int FISHING_FOCUS_SOME_FINDS = Skills.AGGRESSIVE_MODE;
	public static final int FISHING_FOCUS_MORE_FINDS = Skills.ACCURATE_MODE;
	public static final int FISHING_FOCUS_MOST_FINDS = Skills.DEFENSIVE_MODE;
	private static final double MYWORLD_FISHING_SPECIAL_BASE_CHANCE = 1.0D / 30.0D;

	private static final FishEntry SHRIMP = new FishEntry(ItemId.RAW_SHRIMP.id(), 1, 1, 40);
	private static final FishEntry SARDINE = new FishEntry(ItemId.RAW_SARDINE.id(), 1, 7, 80);
	private static final FishEntry HERRING = new FishEntry(ItemId.RAW_HERRING.id(), 2, 13, 120);
	private static final FishEntry MACKEREL = new FishEntry(ItemId.RAW_MACKEREL.id(), 3, 16, 80);
	private static final FishEntry ANCHOVIES = new FishEntry(ItemId.RAW_ANCHOVIES.id(), 3, 19, 160);
	private static final FishEntry TROUT = new FishEntry(ItemId.RAW_TROUT.id(), 4, 25, 200);
	private static final FishEntry COD = new FishEntry(ItemId.RAW_COD.id(), 4, 25, 180);
	private static final FishEntry PIKE = new FishEntry(ItemId.RAW_PIKE.id(), 5, 31, 240);
	private static final FishEntry SALMON = new FishEntry(ItemId.RAW_SALMON.id(), 5, 37, 280);
	private static final FishEntry TUNA = new FishEntry(ItemId.RAW_TUNA.id(), 6, 43, 320);
	private static final FishEntry LOBSTER = new FishEntry(ItemId.RAW_LOBSTER.id(), 7, 49, 360);
	private static final FishEntry SWORDFISH = new FishEntry(ItemId.RAW_SWORDFISH.id(), 8, 55, 400);
	private static final FishEntry BASS = new FishEntry(ItemId.RAW_BASS.id(), 8, 55, 400);
	private static final FishEntry LAVA_EEL = new FishEntry(ItemId.RAW_LAVA_EEL.id(), 9, 62, 360);
	private static final FishEntry SHARK = new FishEntry(ItemId.RAW_SHARK.id(), 10, 70, 440);

	private static final FishEntry[] RIVER_FISH = {TROUT, PIKE, SALMON};
	private static final FishEntry[] SMALL_NET_FISH = {SHRIMP, SARDINE, HERRING, ANCHOVIES};
	private static final FishEntry[] BIG_NET_FISH = {MACKEREL, COD, BASS, TUNA, LOBSTER, SWORDFISH, SHARK};
	private static final FishEntry[] GNOME_MIXED_FISH = {SHRIMP, SARDINE, HERRING, ANCHOVIES, TROUT, PIKE, SALMON, TUNA, LOBSTER, SWORDFISH};
	private static final FishEntry[] SEA_MIXED_FISH = {SHRIMP, SARDINE, HERRING, ANCHOVIES, MACKEREL, COD, BASS, TUNA, LOBSTER, SWORDFISH, SHARK};
	private static final FishEntry[] PLATFORM_FISH = {SHRIMP, SARDINE, HERRING, ANCHOVIES, TUNA, LOBSTER, SWORDFISH};
	private static final FishEntry[] RIVER_SEA_FISH = {TROUT, PIKE, SALMON, TUNA, LOBSTER, SWORDFISH};
	private static final FishEntry[] LAVA_FISH = {LAVA_EEL};

	private static final FishingLocation[] MYWORLD_FISHING_LOCATIONS = {
		new FishingLocation(489, 25, 500, 38, RIVER_FISH),
		new FishingLocation(254, 290, 259, 294, SMALL_NET_FISH),
		new FishingLocation(524, 424, 524, 427, RIVER_FISH),
		new FishingLocation(736, 434, 764, 520, GNOME_MIXED_FISH),
		new FishingLocation(657, 473, 663, 473, RIVER_FISH),
		new FishingLocation(585, 496, 596, 505, BIG_NET_FISH),
		new FishingLocation(645, 499, 661, 524, RIVER_FISH),
		new FishingLocation(398, 500, 418, 507, SEA_MIXED_FISH),
		new FishingLocation(208, 501, 212, 507, RIVER_FISH),
		new FishingLocation(616, 543, 619, 543, RIVER_FISH),
		new FishingLocation(420, 551, 422, 551, RIVER_FISH),
		new FishingLocation(480, 608, 496, 621, PLATFORM_FISH),
		new FishingLocation(125, 629, 125, 631, RIVER_FISH),
		new FishingLocation(221, 645, 233, 664, SMALL_NET_FISH),
		new FishingLocation(368, 678, 373, 687, PLATFORM_FISH),
		new FishingLocation(246, 681, 328, 745, SMALL_NET_FISH),
		new FishingLocation(689, 702, 696, 706, RIVER_FISH),
		new FishingLocation(453, 710, 453, 710, new FishEntry[]{TUNA, LOBSTER, SWORDFISH}),
		new FishingLocation(85, 718, 89, 719, SMALL_NET_FISH),
		new FishingLocation(196, 726, 196, 726, new FishEntry[]{SHRIMP}),
		new FishingLocation(395, 754, 395, 754, SMALL_NET_FISH),
		new FishingLocation(443, 800, 453, 810, RIVER_SEA_FISH),
		new FishingLocation(389, 833, 399, 836, RIVER_FISH),
		new FishingLocation(74, 1639, 74, 1639, new FishEntry[]{TUNA, LOBSTER, SWORDFISH}),
		new FishingLocation(373, 3374, 373, 3374, LAVA_FISH)
	};

	private static final class FishEntry {
		private final int itemId;
		private final int tier;
		private final int legacyLevel;
		private final int exp;

		private FishEntry(int itemId, int tier, int legacyLevel, int exp) {
			this.itemId = itemId;
			this.tier = tier;
			this.legacyLevel = legacyLevel;
			this.exp = exp;
		}
	}

	private static final class FishingSpecialReward {
		private final int itemId;
		private final int tier;
		private final int weight;

		private FishingSpecialReward(int itemId, int tier, int weight) {
			this.itemId = itemId;
			this.tier = tier;
			this.weight = weight;
		}
	}

	private static final FishingSpecialReward[] MYWORLD_FISHING_STATIC_SPECIAL_REWARDS = {
		new FishingSpecialReward(ItemId.OYSTER.id(), 1, 24),
		new FishingSpecialReward(ItemId.SEAWEED.id(), 1, 20),
		new FishingSpecialReward(ItemId.CASKET.id(), 1, 8)
	};

	private static final FishingSpecialReward[] MYWORLD_FISHING_LEATHER_SPECIAL_REWARDS = {
		new FishingSpecialReward(ItemId.COW_HIDE_GLOVES.id(), 1, 10),
		new FishingSpecialReward(ItemId.COW_HIDE_BOOTS.id(), 1, 10),
		new FishingSpecialReward(ItemId.GOBLIN_HIDE_GLOVES.id(), 1, 10),
		new FishingSpecialReward(ItemId.GOBLIN_HIDE_BOOTS.id(), 1, 10),
		new FishingSpecialReward(ItemId.UNICORN_HIDE_GLOVES.id(), 2, 9),
		new FishingSpecialReward(ItemId.UNICORN_HIDE_BOOTS.id(), 2, 9),
		new FishingSpecialReward(ItemId.BEAR_HIDE_GLOVES.id(), 2, 9),
		new FishingSpecialReward(ItemId.BEAR_HIDE_BOOTS.id(), 2, 9),
		new FishingSpecialReward(ItemId.BLACK_UNICORN_HIDE_GLOVES.id(), 2, 9),
		new FishingSpecialReward(ItemId.BLACK_UNICORN_HIDE_BOOTS.id(), 2, 9),
		new FishingSpecialReward(ItemId.SCORPION_CARAPACE_GLOVES.id(), 2, 9),
		new FishingSpecialReward(ItemId.SCORPION_CARAPACE_BOOTS.id(), 2, 9),
		new FishingSpecialReward(ItemId.WOLF_GLOVES.id(), 3, 8),
		new FishingSpecialReward(ItemId.WOLF_BOOTS.id(), 3, 8),
		new FishingSpecialReward(ItemId.SPIDER_GLOVES.id(), 3, 8),
		new FishingSpecialReward(ItemId.SPIDER_BOOTS.id(), 3, 8),
		new FishingSpecialReward(ItemId.GIANT_GLOVES.id(), 3, 8),
		new FishingSpecialReward(ItemId.GIANT_BOOTS.id(), 3, 8),
		new FishingSpecialReward(ItemId.OGRE_GLOVES.id(), 4, 7),
		new FishingSpecialReward(ItemId.OGRE_BOOTS.id(), 4, 7),
		new FishingSpecialReward(ItemId.BABY_DRAGON_GLOVES.id(), 4, 7),
		new FishingSpecialReward(ItemId.BABY_DRAGON_BOOTS.id(), 4, 7),
		new FishingSpecialReward(ItemId.MAGIC_SPIDER_GLOVES.id(), 5, 6),
		new FishingSpecialReward(ItemId.MAGIC_SPIDER_BOOTS.id(), 5, 6),
		new FishingSpecialReward(ItemId.MOSS_GIANT_GLOVES.id(), 5, 6),
		new FishingSpecialReward(ItemId.MOSS_GIANT_BOOTS.id(), 5, 6),
		new FishingSpecialReward(ItemId.ICE_GIANT_GLOVES.id(), 5, 6),
		new FishingSpecialReward(ItemId.ICE_GIANT_BOOTS.id(), 5, 6),
		new FishingSpecialReward(ItemId.DEMON_GLOVES.id(), 6, 5),
		new FishingSpecialReward(ItemId.DEMON_BOOTS.id(), 6, 5),
		new FishingSpecialReward(ItemId.HELLHOUND_GLOVES.id(), 7, 4),
		new FishingSpecialReward(ItemId.HELLHOUND_BOOTS.id(), 7, 4),
		new FishingSpecialReward(ItemId.FIRE_GIANT_GLOVES.id(), 7, 4),
		new FishingSpecialReward(ItemId.FIRE_GIANT_BOOTS.id(), 7, 4),
		new FishingSpecialReward(ItemId.BLUE_DRAGON_GLOVES.id(), 7, 4),
		new FishingSpecialReward(ItemId.BLUE_DRAGON_BOOTS.id(), 7, 4),
		new FishingSpecialReward(ItemId.DRAGON_GLOVES.id(), 7, 4),
		new FishingSpecialReward(ItemId.DRAGON_BOOTS.id(), 7, 4),
		new FishingSpecialReward(ItemId.RED_DRAGON_GLOVES.id(), 8, 3),
		new FishingSpecialReward(ItemId.RED_DRAGON_BOOTS.id(), 8, 3),
		new FishingSpecialReward(ItemId.BLACK_DEMON_GLOVES.id(), 8, 3),
		new FishingSpecialReward(ItemId.BLACK_DEMON_BOOTS.id(), 8, 3),
		new FishingSpecialReward(ItemId.BLACK_DRAGON_GLOVES.id(), 9, 2),
		new FishingSpecialReward(ItemId.BLACK_DRAGON_BOOTS.id(), 9, 2),
		new FishingSpecialReward(ItemId.BALROG_GLOVES.id(), 9, 2),
		new FishingSpecialReward(ItemId.BALROG_BOOTS.id(), 9, 2),
		new FishingSpecialReward(ItemId.KING_BLACK_DRAGON_GLOVES.id(), 10, 1),
		new FishingSpecialReward(ItemId.KING_BLACK_DRAGON_BOOTS.id(), 10, 1)
	};

	private static final class FishingLocation {
		private final int minX;
		private final int minY;
		private final int maxX;
		private final int maxY;
		private final FishEntry[] fish;

		private FishingLocation(int minX, int minY, int maxX, int maxY, FishEntry[] fish) {
			this.minX = minX;
			this.minY = minY;
			this.maxX = maxX;
			this.maxY = maxY;
			this.fish = fish;
		}

		private boolean contains(int x, int y) {
			return x >= minX && x <= maxX && y >= minY && y <= maxY;
		}
	}

	public ObjectFishDef getFish(ObjectFishingDef objectFishingDef, int fishingLevel) {
		return objectFishingDef.fishingAttemptResult(fishingLevel);
	}

	@Override
	public void onOpLoc(Player player, final GameObject object, String command) {
		String lowerCommand = command.toLowerCase();
		if (player.getConfig().WANT_MYWORLD && isMyWorldFishingSpot(object)
			&& (lowerCommand.equals("fish") || lowerCommand.equals("examine") || isLegacyFishingCommand(lowerCommand))) {
			handleMyWorldFishing(object, player, lowerCommand);
			return;
		}
		if (
			lowerCommand.equals("lure")
				|| lowerCommand.equals("bait")
				|| lowerCommand.equals("net")
				|| lowerCommand.equals("harpoon")
				|| lowerCommand.equals("cage")
		) {
			if (player.getConfig().GATHER_TOOL_ON_SCENERY) {
				player.playerServerMessage(MessageType.QUEST, "You need to use the appropriate tool on the spot to " + command + " the fish");
				return;
			}
			handleFishing(object, player, player.click, command);
		}
	}

	private void handleMyWorldFishing(final GameObject object, Player player, final String command) {
		final EntityHandler entityHandler = player.getWorld().getServer().getEntityHandler();
		final FishingLocation location = getMyWorldFishingLocation(object);
		if (location == null) {
			player.playerServerMessage(MessageType.QUEST, "You don't see any fish worth catching here.");
			return;
		}
		if (command.equals("examine")) {
			player.playerServerMessage(MessageType.QUEST, getMyWorldExamineText(entityHandler, location));
			return;
		}
		if (!player.withinRange(object, 1) || isFatigued(player)) {
			return;
		}
		if (object.getID() == TUTORIAL_FISH_ID && player.getSkills().getExperience(Skill.FISHING.id()) >= 200) {
			mes("that's enough fishing for now");
			delay(3);
			mes("go through the next door to continue the tutorial");
			delay(3);
			return;
		}

		final int rodId = getEquippedFishingRod(player);
		if (rodId < 0) {
			player.playerServerMessage(MessageType.QUEST, "You need to equip a fishing rod to fish here.");
			return;
		}
		final int rodTier = getFishingRodTier(rodId);
		final int requiredLevel = getFishingRodRequiredLevel(rodTier);
		if (player.getSkills().getLevel(Skill.FISHING.id()) < requiredLevel) {
			player.playerServerMessage(MessageType.QUEST, "You need a Fishing level of " + requiredLevel + " to use that rod.");
			return;
		}

		List<FishEntry> eligibleFish = getEligibleFish(location, rodTier);
		if (eligibleFish.isEmpty()) {
			player.playerServerMessage(MessageType.QUEST, "This rod isn't appropriate for any fish available here");
			return;
		}

		int repeat = 1;
		if (config().BATCH_PROGRESSION) {
			repeat = Formulae.getRepeatTimes(player, Skill.FISHING.id());
		}

		startbatch(repeat);
		batchMyWorldFishing(entityHandler, player, object, location, eligibleFish, rodId, rodTier);
	}

	private void batchMyWorldFishing(
			EntityHandler entityHandler,
			Player player,
			GameObject object,
			FishingLocation location,
			List<FishEntry> eligibleFish,
			int rodId,
			int rodTier
	) {
		final Inventory inventory = player.getCarriedItems().getInventory();
		if (inventory.full()) {
			player.playerServerMessage(MessageType.QUEST, "Your inventory is too full to hold any more fish");
			return;
		}

		player.playerServerMessage(MessageType.QUEST, "You attempt to catch some fish");
		player.playSound("fish");
		int delay = getMyWorldFishingDelay(player, rodTier);
		ActionSender.sendActionProgressBar(player, rodId, delay);
		delay(delay);
		if (ifinterrupted() || !player.withinRange(object, 1)) {
			return;
		}

		if (isFatigued(player)) {
			return;
		}
		GameObject obj = player.getViewArea().getGameObject(object.getID(), object.getX(), object.getY());
		if (player.getConfig().SHARED_GATHERING_RESOURCES && obj == null) {
			player.playerServerMessage(MessageType.QUEST, "You fail to catch anything");
			return;
		}

		FishEntry caught = rollMyWorldFish(player, eligibleFish, rodTier);
		Item fish = new Item(caught.itemId);
		sendCatchMessage(player, fish);
		if (player.getCarriedItems().getEquipment().bankSkillingDropWithLawRing(fish) <= 0) {
			inventory.add(fish);
		}
		player.incExp(Skill.FISHING.id(), caught.exp, true);
		maybeAwardMyWorldFishingSpecialReward(player, rodTier);

		if (player.getCarriedItems().getEquipment().getCosmicAmuletExtraResourceChance() > 0.0D
			&& DataConversions.getRandom().nextDouble() < player.getCarriedItems().getEquipment().getCosmicAmuletExtraResourceChance()
			&& !inventory.full()) {
			inventory.add(new Item(fish.getCatalogId(), 1));
			player.playerServerMessage(MessageType.QUEST, "Your amulet glimmers and another catch comes with it.");
		}

		ObjectFishingDef depletionDef = getAnyFishingDef(entityHandler, object);
		if (depletionDef != null) {
			handleDepletableFishing(player, depletionDef, object);
		}

		GameObject fishingSpot = player.getViewArea().getGameObject(object.getID(), object.getX(), object.getY());
		if (fishingSpot == null) {
			stopbatch();
			return;
		}

		updatebatch();
		if (!ifinterrupted() && !isbatchcomplete()) {
			List<FishEntry> updatedEligibleFish = getEligibleFish(location, rodTier);
			if (updatedEligibleFish.isEmpty()) {
				player.playerServerMessage(MessageType.QUEST, "This rod isn't appropriate for any fish available here");
				stopbatch();
				return;
			}
			delay();
			batchMyWorldFishing(entityHandler, player, object, location, updatedEligibleFish, rodId, rodTier);
		}
	}

	private int getMyWorldFishingDelay(Player player, int rodTier) {
		int effectiveLevel = Formulae.calcGatheringEffectiveLevel(player.getSkills().getLevel(Skill.FISHING.id()), rodTier);
		return Math.max(2, 5 - (Math.max(0, effectiveLevel - 1) / 35));
	}

	private FishEntry rollMyWorldFish(Player player, List<FishEntry> eligibleFish, int rodTier) {
		int effectiveLevel = Formulae.calcGatheringEffectiveLevel(player.getSkills().getLevel(Skill.FISHING.id()), rodTier);
		int[] weights = new int[eligibleFish.size()];
		int totalWeight = 0;
		for (int index = 0; index < eligibleFish.size(); index++) {
			FishEntry fish = eligibleFish.get(index);
			int levelWeight = Math.max(1, effectiveLevel - fish.legacyLevel + 10);
			int tierWeight = fish.tier * fish.tier;
			int weight = levelWeight * tierWeight;
			weights[index] = weight;
			totalWeight += weight;
		}
		int roll = DataConversions.random(1, totalWeight);
		for (int index = 0; index < eligibleFish.size(); index++) {
			roll -= weights[index];
			if (roll <= 0) {
				return eligibleFish.get(index);
			}
		}
		return eligibleFish.get(eligibleFish.size() - 1);
	}

	private List<FishEntry> getEligibleFish(FishingLocation location, int rodTier) {
		List<FishEntry> eligibleFish = new ArrayList<FishEntry>();
		int minimumTier = getMinimumFishingRodCatchTier(rodTier);
		for (FishEntry fish : location.fish) {
			if (fish.tier >= minimumTier && fish.tier <= rodTier) {
				eligibleFish.add(fish);
			}
		}
		return eligibleFish;
	}

	private int getMinimumFishingRodCatchTier(int rodTier) {
		return Math.max(1, rodTier - MYWORLD_ROD_CATCH_TIER_SPAN);
	}

	public static String getFishingFocusLabel(int combatStyle) {
		switch (combatStyle) {
			case FISHING_FOCUS_NO_FINDS:
				return "Just the fish";
			case FISHING_FOCUS_SOME_FINDS:
				return "A little loot";
			case FISHING_FOCUS_MORE_FINDS:
				return "Plenty of loot";
			case FISHING_FOCUS_MOST_FINDS:
				return "Lots of loot";
			default:
				return "A little loot";
		}
	}

	private static int getFishingFocus(Player player) {
		return player.getCombatStyle();
	}

	private static double getFishingSpecialRewardChance(Player player) {
		switch (getFishingFocus(player)) {
			case FISHING_FOCUS_NO_FINDS:
				return 0.0D;
			case FISHING_FOCUS_SOME_FINDS:
				return MYWORLD_FISHING_SPECIAL_BASE_CHANCE;
			case FISHING_FOCUS_MORE_FINDS:
				return MYWORLD_FISHING_SPECIAL_BASE_CHANCE * 1.5D;
			case FISHING_FOCUS_MOST_FINDS:
				return MYWORLD_FISHING_SPECIAL_BASE_CHANCE * 2.0D;
			default:
				return MYWORLD_FISHING_SPECIAL_BASE_CHANCE;
		}
	}

	private void maybeAwardMyWorldFishingSpecialReward(Player player, int rodTier) {
		double rewardChance = getFishingSpecialRewardChance(player);
		if (rewardChance <= 0.0D || DataConversions.getRandom().nextDouble() >= rewardChance) {
			return;
		}
		Inventory inventory = player.getCarriedItems().getInventory();
		if (inventory.full()) {
			player.playerServerMessage(MessageType.QUEST, "You spot something else in the water, but your inventory is too full to hold it.");
			return;
		}
		FishingSpecialReward reward = rollMyWorldFishingSpecialReward(rodTier);
		Item item = new Item(reward.itemId, 1);
		inventory.add(item);
		player.playerServerMessage(MessageType.QUEST, "You also find " + formatFishingSpecialRewardName(player, item) + ".");
		maybeDoubleRareGatheringReward(player, item, "Your cosmic amulet glimmers and another find appears.");
	}

	private void maybeDoubleRareGatheringReward(Player player, Item item, String message) {
		double chance = player.getCarriedItems().getEquipment().getCosmicAmuletRareGatheringDoubleChance();
		if (chance <= 0.0D || DataConversions.getRandom().nextDouble() >= chance) {
			return;
		}
		Item extra = new Item(item.getCatalogId(), 1);
		if (player.getCarriedItems().getInventory().full()) {
			player.getWorld().registerItem(new GroundItem(player.getWorld(), item.getCatalogId(), player.getX(), player.getY(), 1, player));
		} else {
			player.getCarriedItems().getInventory().add(extra);
		}
		player.playerServerMessage(MessageType.QUEST, message);
	}

	private FishingSpecialReward rollMyWorldFishingSpecialReward(int rodTier) {
		int totalWeight = 0;
		for (FishingSpecialReward reward : MYWORLD_FISHING_STATIC_SPECIAL_REWARDS) {
			totalWeight += reward.weight;
		}
		for (FishingSpecialReward reward : MYWORLD_FISHING_LEATHER_SPECIAL_REWARDS) {
			totalWeight += Formulae.adjustedSideRewardWeightForToolTier(reward.tier, rodTier, reward.weight);
		}
		int roll = DataConversions.random(1, totalWeight);
		for (FishingSpecialReward reward : MYWORLD_FISHING_STATIC_SPECIAL_REWARDS) {
			roll -= reward.weight;
			if (roll <= 0) {
				return reward;
			}
		}
		for (FishingSpecialReward reward : MYWORLD_FISHING_LEATHER_SPECIAL_REWARDS) {
			int adjustedWeight = Formulae.adjustedSideRewardWeightForToolTier(reward.tier, rodTier, reward.weight);
			if (adjustedWeight <= 0) {
				continue;
			}
			roll -= adjustedWeight;
			if (roll <= 0) {
				return reward;
			}
		}
		return MYWORLD_FISHING_STATIC_SPECIAL_REWARDS[0];
	}

	private String formatFishingSpecialRewardName(Player player, Item item) {
		String name = item.getDef(player.getWorld()).getName().toLowerCase();
		if (name.endsWith("boots") || name.endsWith("gloves")) {
			return "some " + name;
		}
		if (name.startsWith("oyster")) {
			return "an " + name;
		}
		return "a " + name;
	}

	private String getMyWorldExamineText(EntityHandler entityHandler, FishingLocation location) {
		StringBuilder builder = new StringBuilder("This spot can produce: ");
		for (int index = 0; index < location.fish.length; index++) {
			if (index > 0) {
				builder.append(index == location.fish.length - 1 ? ", and " : ", ");
			}
			builder.append(getFishName(entityHandler, location.fish[index].itemId));
		}
		builder.append(".");
		return builder.toString();
	}

	private String getFishName(EntityHandler entityHandler, int itemId) {
		String name = entityHandler.getItemDef(itemId).getName().toLowerCase();
		return name.startsWith("raw ") ? name.substring(4) : name;
	}

	private void sendCatchMessage(Player player, Item fish) {
		switch (ItemId.getById(fish.getCatalogId())) {
			case RAW_SHARK:
				player.playerServerMessage(MessageType.QUEST, "You catch a shark!");
				break;
			case RAW_SHRIMP:
				player.playerServerMessage(MessageType.QUEST, "You catch some shrimps");
				break;
			case RAW_ANCHOVIES:
				player.playerServerMessage(MessageType.QUEST, "You catch some anchovies");
				break;
			case OYSTER:
				player.playerServerMessage(MessageType.QUEST, "You catch an oyster shell");
				break;
			default:
				String fishName = fish.getDef(player.getWorld()).getName().toLowerCase().replace("raw ", "");
				player.playerServerMessage(MessageType.QUEST, "You catch a " + fishName);
				break;
		}
	}

	private FishingLocation getMyWorldFishingLocation(GameObject object) {
		for (FishingLocation location : MYWORLD_FISHING_LOCATIONS) {
			if (location.contains(object.getX(), object.getY())) {
				return location;
			}
		}
		return null;
	}

	private boolean isMyWorldFishingSpot(GameObject object) {
		return inArray(object.getID(), MYWORLD_FISHING_OBJECT_IDS);
	}

	private boolean isFishingRod(int itemId) {
		return inArray(itemId, MYWORLD_ROD_IDS);
	}

	private boolean isRetiredNormalFishingTool(int itemId) {
		return itemId == ItemId.NET.id()
			|| itemId == ItemId.BIG_NET.id()
			|| itemId == ItemId.FLY_FISHING_ROD.id()
			|| itemId == ItemId.OILY_FISHING_ROD.id()
			|| itemId == ItemId.LOBSTER_POT.id()
			|| itemId == ItemId.HARPOON.id();
	}

	private int getEquippedFishingRod(Player player) {
		for (int index = MYWORLD_ROD_IDS.length - 1; index >= 0; index--) {
			int rodId = MYWORLD_ROD_IDS[index];
			if (player.getCarriedItems().getEquipment().hasCatalogID(rodId)) {
				return rodId;
			}
		}
		return -1;
	}

	private int getFishingRodTier(int rodId) {
		for (int index = 0; index < MYWORLD_ROD_IDS.length; index++) {
			if (MYWORLD_ROD_IDS[index] == rodId) {
				return index + 1;
			}
		}
		return 0;
	}

	private int getFishingRodRequiredLevel(int rodTier) {
		if (rodTier < 1 || rodTier > MYWORLD_ROD_LEVELS.length) {
			return 1;
		}
		return MYWORLD_ROD_LEVELS[rodTier - 1];
	}

	private ObjectFishingDef getAnyFishingDef(EntityHandler entityHandler, GameObject object) {
		ObjectFishingDef def = entityHandler.getObjectFishingDef(object.getID(), 0);
		if (def != null) {
			return def;
		}
		return entityHandler.getObjectFishingDef(object.getID(), 1);
	}

	private boolean isLegacyFishingCommand(String command) {
		return command.equals("lure") || command.equals("bait")
			|| command.equals("net") || command.equals("harpoon") || command.equals("cage");
	}

	private void handleFishing(final GameObject object, Player player, final int click, final String command) {
		final EntityHandler entityHandler = player.getWorld().getServer().getEntityHandler();

		final ObjectFishingDef def = entityHandler.getObjectFishingDef(object.getID(), click);
		final Inventory inventory = player.getCarriedItems().getInventory();

		if (def == null || !player.withinRange(object, 1) || isFatigued(player)) {
			return;
		}

		if (object.getID() == TUTORIAL_FISH_ID && player.getSkills().getExperience(Skill.FISHING.id()) >= 200) {
			mes("that's enough fishing for now");
			delay(3);
			mes("go through the next door to continue the tutorial");
			delay(3);
			return;
		}

		if (!isFishingLevelOk(entityHandler, object, player, command, def)) {
			return;
		}

		if(!hasNet(def, entityHandler, player, inventory, command)) {
			return;
		}

		if(!hasBait(def, player, inventory)) {
			return;
		}

		int repeat = 1;
		if (config().BATCH_PROGRESSION) {
			repeat = Formulae.getRepeatTimes(player, Skill.FISHING.id());
		}

		startbatch(repeat);
		batchFishing(entityHandler, player, def, object);
	}

	private boolean hasBait(ObjectFishingDef def, Player player, Inventory inventory) {
		final int baitId = def.getBaitId();
		if (baitId >= 0) {
			if (inventory.countId(baitId, Optional.of(false)) <= 0) {
				player.playerServerMessage(MessageType.QUEST, outOfBait(baitId));
				return false;
			}
		}
		return true;
	}

	private boolean hasNet(
			ObjectFishingDef def,
			EntityHandler entityHandler,
			Player player,
			Inventory inventory,
			String command
	) {
		final int netId = def.getNetId();
		if (inventory.countId(netId, Optional.of(false)) <= 0) {
			player.playerServerMessage(MessageType.QUEST,
					"You need a "
							+ entityHandler.getItemDef(netId).getName().toLowerCase()
							+ " to " + (command.equals("lure") || command.equals("bait") ? command : def.getBaitId() > 0 ? "bait" : "catch") + " "
							+ (!command.contains("cage") ? "these fish"
							: entityHandler.getItemDef(def.getFishDefs()[0].getId()).getName().toLowerCase()
							.substring(4) + "s"));
			return false;
		}
		return true;
	}

	private boolean isFishingLevelOk(
			EntityHandler entityHandler,
			GameObject object,
			Player player,
			String command,
			ObjectFishingDef def
	) {
		if (player.getSkills().getLevel(Skill.FISHING.id()) < def.getReqLevel(player.getWorld())) {
			player.playerServerMessage(
					MessageType.QUEST,
					"You need at least level " + def.getReqLevel(player.getWorld()) + " "
				+ fishingRequirementString(object, command) + " "
				+ (!command.contains("cage") ? "these fish"
				: entityHandler.getItemDef(def.getFishDefs()[0].getId()).getName().toLowerCase().substring(4) + "s")
			);
			return false;
		}
		return true;
	}

	public void testBigNetFishing(int level, int trials, Player player) {
		ObjectFishingDef bigNet = player.getWorld().getServer().getEntityHandler().getObjectFishingDef(261, 0);
		if (bigNet == null) {
			LOGGER.error("Somehow bigNet fishing spot isn't defined. Check your cache files.");
			return;
		}

		List<ObjectFishDef> fishLst = new ArrayList<ObjectFishDef>();

		for (int i = 0; i < trials; i++) {
			doBigNetFishingRoll(fishLst, bigNet, level);
		}

		// tally results
		int[] results = new int[1290];
		for (ObjectFishDef fish : fishLst) {
			results[fish.getId()]++;
		}

		mes("@whi@At level @gre@" + level + "@whi@ in @gre@" + trials + "@whi@ attempts:");
		for (int i = 0; i < 1290; i++) {
			if (results[i] > 0) {
				mes( "@whi@We got @gre@" + results[i] + "@whi@ of id @mag@" + i);
			}
		}
	}

	private void batchFishing(
			EntityHandler entityHandler,
			Player player,
			ObjectFishingDef def,
			GameObject object
	) {
		final Inventory inventory = player.getCarriedItems().getInventory();
		final int netId = def.getNetId();
		final int baitId = def.getBaitId();

		player.playerServerMessage(MessageType.QUEST, "You attempt to catch " + tryToCatchFishString(def));
		player.playSound("fish");
		ActionSender.sendActionProgressBar(player, def.getNetId(), 4);
		delay(4);
		if (ifinterrupted() || !player.withinRange(object, 1)) {
			return;
		}

		if(!hasBait(def, player, inventory)) {
			return;
		}

		if (isFatigued(player)) {
			return;
		}

		List<ObjectFishDef> fishLst = new ArrayList<ObjectFishDef>();
		GameObject obj = player.getViewArea().getGameObject(object.getID(), object.getX(), object.getY());

		ObjectFishDef aFishDef;
		if (object.getID() == TUTORIAL_FISH_ID) { // Tutorial Island Shrimp
			aFishDef = getFish(def, player.getSkills().getLevel(Skill.FISHING.id()));
			if (aFishDef != null) fishLst.add(aFishDef);

			if (fishLst.size() > 0) {
				player.playerServerMessage(MessageType.QUEST, "You catch some shrimps");
				inventory.add(new Item(fishLst.get(0).getId()));
				player.incExp(Skill.FISHING.id(), fishLst.get(0).getExp(), true);
				if (player.getCache().hasKey("tutorial") && player.getCache().getInt("tutorial") == 41) {
					player.getCache().set("tutorial", 42);
				}
			} else {
				// it is authentic that this message only appears until you have caught your first shrimp.
				if (player.getCache().hasKey("tutorial") && player.getCache().getInt("tutorial") == 41) {
					player.message("keep trying, you'll catch something soon");
				} else {
					player.playerServerMessage(MessageType.QUEST, "You fail to catch anything");
				}
			}
		} else if (netId == ItemId.BIG_NET.id()) {

			ObjectFishingDef bigNet = entityHandler.getObjectFishingDef(261, 0);
			if (bigNet == null) {
				LOGGER.error("Somehow bigNet fishing spot isn't defined. Check your cache files.");
				return;
			}

			// add the fish gained to fishLst, report how many rolls were able to be done
			int fishRolls = doBigNetFishingRoll(fishLst, bigNet, player.getSkills().getLevel(Skill.FISHING.id()));

			//check if the spot is still active
			if (player.getConfig().SHARED_GATHERING_RESOURCES && obj == null) {
				player.playerServerMessage(MessageType.QUEST, "You fail to catch anything");
				return;
			}
			// award the fish
			for (ObjectFishDef fishDef : fishLst) {
				Item fish = new Item(fishDef.getId());
				switch (ItemId.getById(fishDef.getId())) {
					// NOTICE: Don't obfuscate this by making it a one liner.
					// Needed to be on separate lines for Language Translations.
					case RAW_BASS:
						player.playerServerMessage(MessageType.QUEST, "You catch a bass");
						break;
					case RAW_COD:
						player.playerServerMessage(MessageType.QUEST, "You catch a cod");
						break;
					case RAW_MACKEREL:
						player.playerServerMessage(MessageType.QUEST, "You catch a mackerel");
						break;
					case OYSTER:
						player.playerServerMessage(MessageType.QUEST, "You catch an oyster shell");
						break;
					case CASKET:
						player.playerServerMessage(MessageType.QUEST, "You catch a casket");
						break;
					case BOOTS:
						player.playerServerMessage(MessageType.QUEST, "You catch some boots");
						break;
					case COW_HIDE_GLOVES:
						player.playerServerMessage(MessageType.QUEST, "You catch some gloves");
						break;
					case SEAWEED:
						player.playerServerMessage(MessageType.QUEST, "You catch some seaweed");
						break;
					default:
						player.playerServerMessage(MessageType.QUEST, "You catch something really surprising: a bug! Please report this bug!");
						break;
				}
				player.getCarriedItems().getInventory().add(fish);
				player.incExp(Skill.FISHING.id(), fishDef.getExp(), true);

				if (player.getCarriedItems().getEquipment().getCosmicAmuletExtraResourceChance() > 0.0D
					&& DataConversions.getRandom().nextDouble() < player.getCarriedItems().getEquipment().getCosmicAmuletExtraResourceChance()
					&& !player.getCarriedItems().getInventory().full()) {
					player.getCarriedItems().getInventory().add(new Item(fish.getCatalogId(), 1));
					player.playerServerMessage(MessageType.QUEST, "Your amulet glimmers and another catch comes with it.");
				}
			}
			if (fishLst.size() == 0 && fishRolls == 9) {
				// An erroneous (mostly authentic) additional check on fishRolls here,
				// so that this message doesn't appear unless all rolls were possible at the player's fishing level.
				//
				// It was likely a NPE or array index out of bounds in the original source, like checking the result
				// of the fish in some array while awarding the fish, or just referencing some null value...
				// Because there's only 7 / 9  of the fish expected, the fishing script crashes & doesn't make it to this message.
				//
				// It would be rather ugly to emulate that at this time, so instead I'm checking fishRolls
				// Which is functionally the same except our script doesn't crash.
				player.playerServerMessage(MessageType.QUEST, "You fail to catch anything");
			}

			// Check if fishing spot should inauthentically deplete
			if (fishLst.size() > 0) {
				handleDepletableFishing(player, def, object);
			}
		} else { // NOT big net fishing & NOT tutorial island shrimp; normal fishing
			// Roll for fish to be given to user
			aFishDef = getFish(def, player.getSkills().getLevel(Skill.FISHING.id()));
			if (aFishDef != null) fishLst.add(aFishDef);

			if (fishLst.size() == 0) {
				player.playerServerMessage(MessageType.QUEST, "You fail to catch anything");
				if (!isbatchcomplete()) {
					GameObject checkObj = player.getViewArea().getGameObject(object.getID(), object.getX(), object.getY());
					if (checkObj == null) {
						return;
					}
				}
			} else {
				//check if the spot is still active
				if (player.getConfig().SHARED_GATHERING_RESOURCES && obj == null) {
					player.playerServerMessage(MessageType.QUEST, "You fail to catch anything");
					return;
				}

				// Award the fish
				Item fish = new Item(fishLst.get(0).getId());

				// check & remove bait
				if (baitId >= 0) {
					int idx = player.getCarriedItems().getInventory().getLastIndexById(baitId, Optional.of(false));
					Item bait = player.getCarriedItems().getInventory().get(idx);
					if (bait == null) {
						// should not be reachable unless threading bug; this was already checked
						if (player.getCarriedItems().getInventory().countId(baitId, Optional.of(false)) <= 0) {
							player.playerServerMessage(MessageType.QUEST, outOfBait(baitId));
							return;
						}
					}
					player.getCarriedItems().remove(new Item(bait.getCatalogId(), 1, false, bait.getItemId()));
				}

				switch (ItemId.getById(fish.getCatalogId())) {
					// NOTICE: Don't obfuscate this by making it a one liner.
					// Needed to be on separate lines for Language Translations.
					case RAW_SHARK:
						player.playerServerMessage(MessageType.QUEST, "You catch a shark!");
						break;
					case RAW_SHRIMP:
						player.playerServerMessage(MessageType.QUEST, "You catch some shrimps");
						break;
					case RAW_ANCHOVIES:
						player.playerServerMessage(MessageType.QUEST, "You catch some anchovies");
						break;
					default:
						// TODO: may need to separate all these out for Language Translations
						String fishName = fish.getDef(player.getWorld()).getName().toLowerCase().replace("raw ", "");
						player.playerServerMessage(MessageType.QUEST, "You catch a " + fishName);
						break;
				}

				inventory.add(fish);
				player.incExp(Skill.FISHING.id(), fishLst.get(0).getExp(), true);

				// Inauthentically check if the fishing spot should deplete
				handleDepletableFishing(player, def, object);
			}
		}

		// If object has depleted, kill batch
		GameObject fishingSpot = player.getViewArea().getGameObject(object.getID(), object.getX(), object.getY());
		if (fishingSpot == null) {
			stopbatch();
			return;
		}

		// Repeat
		updatebatch();
		if (!ifinterrupted() && !isbatchcomplete()) {
			delay();
			batchFishing(entityHandler, player, def, object);
		}
	}

	private void handleDepletableFishing(Player player, ObjectFishingDef def, GameObject object) {
		if (config().FISHING_SPOTS_DEPLETABLE && DataConversions.random(1, 250) <= def.getDepletion()) {
			GameObject obj = player.getViewArea().getGameObject(object.getID(), object.getX(), object.getY());
			if (obj != null && obj.getID() == object.getID() && def.getRespawnTime() > 0) {
				GameObject newObject = new GameObject(
						player.getWorld(),
						object.getLocation(),
						DEPLETED_FISH_ROCK_ID,
						object.getDirection(),
						object.getType()
				);
				player.getWorld().replaceGameObject(object, newObject);
				player.getWorld().delayedSpawnObject(
						obj.getLoc(),
						def.getRespawnTime() * config().GAME_TICK,
						true
				);
			}
		}
	}

	private int doBigNetFishingRoll(List<ObjectFishDef> fishLst, ObjectFishingDef bigNet, int playerLevel) {
		// Roll for fish. Each of the 8 fish get 1 roll each except mackerel, those get 2 rolls.
		// Based on jmod tweet & consistent with the data we have on this. The double mackerel roll can be seen in replays.

		int fishRolls = 0;
		for (ObjectFishDef fish : bigNet.getFishDefs()) {
			if (playerLevel >= fish.getReqLevel()) {
				int rolls = (fish.getId() == ItemId.RAW_MACKEREL.id() ? 2 : 1); // mackerel get 2 rolls, all others get 1 roll
				for (int roll = 0; roll < rolls; roll++) {
					fishRolls++;
					if (fish.rate[playerLevel] > Math.random()) {
						fishLst.add(fish);
					}
				}
			}
		}
		return fishRolls; // mmmm, delicious fish rolls...
	}

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		//special hemenster fishing spots
		if (obj.getID() == 351 || obj.getID() == 352 || obj.getID() == 353 || obj.getID() == 354)
			return false;
		String lowerCommand = command.toLowerCase();
		if (player.getConfig().WANT_MYWORLD && isMyWorldFishingSpot(obj)
			&& (lowerCommand.equals("fish") || lowerCommand.equals("examine") || isLegacyFishingCommand(lowerCommand))) {
			return true;
		}
		if (isLegacyFishingCommand(lowerCommand)) {
			return true;
		}
		return false;
	}

	private String outOfBait(int baitId) {
		if (baitId == ItemId.FISHING_BAIT.id()) {
			return "You don't have any fishing bait left"; // /1e_Luis/Quests/Heroes Quest/Heroes Quest Pt1
		}
		if (baitId == ItemId.FEATHER.id()) {
			return "You don't have any feathers left to lure the fish"; // /flying sno/flying sno (redacted chat) replays/fsnom2@aol.com/07-30-2018 09.06.45
		}
		return "You are out of an unknown bait. Please report this.";
	}

	private String fishingRequirementString(GameObject obj, String command) {
		String name = "";
		if (command.equals("bait")) {
			name = "fishing to bait";
		} else if (command.equals("lure")) {
			name = "fishing to lure";
		} else if (command.equals("net")) {
			name = "fishing to net";
		} else if (command.equals("harpoon")) {
			name = "fishing to harpoon";
		} else if (command.equals("cage")) {
			name = "fishing to catch";
		}
		return name;
	}

	private String tryToCatchFishString(ObjectFishingDef def) {
		String name = "";
		if (def.getNetId() == ItemId.NET.id()) {
			name = "some fish";
		} else if (def.getNetId() == ItemId.LOBSTER_POT.id()) {
			name = "a lobster";
		} else {
			name = "a fish";
		}
		return name;
	}

	private boolean isFatigued(Player player) {
		if (config().WANT_FATIGUE) {
			if (config().STOP_SKILLING_FATIGUED >= 1
				&& player.getFatigue() >= player.MAX_FATIGUE) {
				player.playerServerMessage(MessageType.QUEST,"You are too tired to catch this fish");
				return true;
			}
		}
		return false;
	}

	@Override
	public void onUseLoc(Player player, GameObject object, Item item) {
		if (player.getConfig().WANT_MYWORLD && isMyWorldFishingSpot(object)) {
			if (isFishingRod(item.getCatalogId())) {
				handleMyWorldFishing(object, player, "fish");
			} else if (isRetiredNormalFishingTool(item.getCatalogId())) {
				player.playerServerMessage(MessageType.QUEST, "You need to equip a fishing rod to fish here.");
			}
			return;
		}
		final GameObjectDef def = player.getWorld().getServer().getEntityHandler().getGameObjectDef(object.getID());
		if (inArray(item.getCatalogId(), Formulae.fishingToolIDs) && (player.getConfig().GATHER_TOOL_ON_SCENERY || !player.getClientLimitations().supportsClickFish) && def != null &&
			inArray(new String[]{def.command1.toLowerCase(), def.command2.toLowerCase()}, "lure", "bait", "net", "harpoon", "cage")) {
			String command = "";
			if (item.getCatalogId() == ItemId.NET.id() || item.getCatalogId() == ItemId.BIG_NET.id()) {
				command = "net";
			} else if (item.getCatalogId() == ItemId.FISHING_ROD.id() || item.getCatalogId() == ItemId.OILY_FISHING_ROD.id()) {
				command = "bait";
			} else if (item.getCatalogId() == ItemId.FLY_FISHING_ROD.id()) {
				command = "lure";
			} else if (item.getCatalogId() == ItemId.LOBSTER_POT.id()) {
				command = "cage";
			} else if (item.getCatalogId() == ItemId.HARPOON.id()) {
				command = "harpoon";
			}
			if (inArray(command, def.command1.toLowerCase(), def.command2.toLowerCase())) {
				player.click = command.equalsIgnoreCase(def.command1) ? 0 : 1;
				handleFishing(object, player, player.click, command);
			} else {
				player.message("Nothing interesting happens");
			}
		}
	}

	@Override
	public boolean blockUseLoc(Player player, GameObject obj, Item item) {
		//special hemenster fishing spots
		if (obj.getID() == 351 || obj.getID() == 352 || obj.getID() == 353 || obj.getID() == 354)
			return false;
		if (player.getConfig().WANT_MYWORLD && isMyWorldFishingSpot(obj)
			&& (isFishingRod(item.getCatalogId()) || isRetiredNormalFishingTool(item.getCatalogId()))) {
			return true;
		}
		final GameObjectDef def = player.getWorld().getServer().getEntityHandler().getGameObjectDef(obj.getID());
		if (inArray(item.getCatalogId(), Formulae.fishingToolIDs) && (player.getConfig().GATHER_TOOL_ON_SCENERY || !player.getClientLimitations().supportsClickFish) && def != null &&
			inArray(new String[]{def.command1.toLowerCase(), def.command2.toLowerCase()}, "lure", "bait", "net", "harpoon", "cage")) {
			return true;
		}
		return false;
	}
}
