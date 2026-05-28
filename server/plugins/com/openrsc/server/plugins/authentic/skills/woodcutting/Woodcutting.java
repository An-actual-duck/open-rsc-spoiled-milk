package com.openrsc.server.plugins.authentic.skills.woodcutting;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Skills;
import com.openrsc.server.content.SkillCapes;
import com.openrsc.server.external.ObjectWoodcuttingDef;
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

import java.util.Optional;

import static com.openrsc.server.plugins.Functions.*;

public class Woodcutting implements OpLocTrigger, UseLocTrigger {

	private static final double MYWORLD_SEED_REWARD_BASE_CHANCE = 1.0D / 50.0D;
	public static final int WOODCUTTING_FOCUS_NO_SEEDS = Skills.CONTROLLED_MODE;
	public static final int WOODCUTTING_FOCUS_SOME_SEEDS = Skills.AGGRESSIVE_MODE;
	public static final int WOODCUTTING_FOCUS_MORE_SEEDS = Skills.ACCURATE_MODE;
	public static final int WOODCUTTING_FOCUS_MOST_SEEDS = Skills.DEFENSIVE_MODE;
	private static final SeedReward[] MYWORLD_WOODCUTTING_SEED_REWARDS = {
		new SeedReward(ItemId.WOODCUTTING_KNOWLEDGE_SEED.id(), 1, 45),
		new SeedReward(ItemId.WOODCUTTING_MONEY_SEED.id(), 1, 45),
		new SeedReward(ItemId.OAK_LOG_SEED.id(), 3, 32),
		new SeedReward(ItemId.WILLOW_LOG_SEED.id(), 4, 28),
		new SeedReward(ItemId.PALM_LOG_SEED.id(), 5, 24),
		new SeedReward(ItemId.MAPLE_LOG_SEED.id(), 6, 20),
		new SeedReward(ItemId.YEW_LOG_SEED.id(), 7, 16),
		new SeedReward(ItemId.EBONY_LOG_SEED.id(), 8, 12),
		new SeedReward(ItemId.MAGIC_LOG_SEED.id(), 9, 8),
		new SeedReward(ItemId.BLOOD_LOG_SEED.id(), 10, 4),
		new SeedReward(ItemId.IRON_SEED.id(), 3, 15),
		new SeedReward(ItemId.COAL_SEED.id(), 4, 12),
		new SeedReward(ItemId.SILVER_SEED.id(), 4, 10),
		new SeedReward(ItemId.GOLD_SEED.id(), 5, 8),
		new SeedReward(ItemId.MITHRIL_SEED.id(), 6, 8),
		new SeedReward(ItemId.ADAMANTITE_SEED.id(), 8, 4),
		new SeedReward(ItemId.RUNITE_SEED.id(), 10, 1),
		new SeedReward(ItemId.SAPPHIRE_SEED.id(), 3, 5),
		new SeedReward(ItemId.EMERALD_SEED.id(), 5, 4),
		new SeedReward(ItemId.RUBY_SEED.id(), 7, 3),
		new SeedReward(ItemId.DIAMOND_SEED.id(), 9, 2),
		new SeedReward(ItemId.DRAGONSTONE_SEED.id(), 10, 1)
	};

	private static final class SeedReward {
		private final int itemId;
		private final int tier;
		private final int weight;

		private SeedReward(int itemId, int tier, int weight) {
			this.itemId = itemId;
			this.tier = tier;
			this.weight = weight;
		}
	}

	public static int getAxe(Player player) {
		int lvl = player.getSkills().getLevel(Skill.WOODCUTTING.id());
		for (int i = 0; i < Formulae.woodcuttingAxeIDs.length; i++) {
			if (player.getCarriedItems().getEquipment().hasCatalogID(Formulae.woodcuttingAxeIDs[i])
				&& lvl >= Formulae.woodcuttingAxeLvls[i]) {
				return Formulae.woodcuttingAxeIDs[i];
			}
		}
		return -1;
	}

	public static int getAxeTier(int axeId) {
		switch (ItemId.getById(axeId)) {
			case COPPER_AXE:
				return 2;
			case BRONZE_AXE:
				return 3;
			case IRON_AXE:
			case BLACK_AXE:
				return 4;
			case STEEL_AXE:
				return 5;
			case MITHRIL_AXE:
				return 6;
			case TITAN_STEEL_AXE:
				return 7;
			case ADAMANTITE_AXE:
				return 8;
			case ORICHALCUM_AXE:
				return 9;
			case RUNE_AXE:
				return 10;
			case DRAGON_AXE:
			case DRAGON_WOODCUTTING_AXE:
				return 11;
			case TIN_AXE:
			default:
				return 1;
		}
	}

	public static String getWoodcuttingFocusLabel(int combatStyle) {
		switch (combatStyle) {
			case WOODCUTTING_FOCUS_NO_SEEDS:
				return "No seeds for me";
			case WOODCUTTING_FOCUS_SOME_SEEDS:
				return "A few seeds";
			case WOODCUTTING_FOCUS_MORE_SEEDS:
				return "More seeds";
			case WOODCUTTING_FOCUS_MOST_SEEDS:
				return "Even more seeds!";
			default:
				return "A few seeds";
		}
	}

	private static double getWoodcuttingSeedRewardChance(Player player) {
		switch (player.getCombatStyle()) {
			case WOODCUTTING_FOCUS_NO_SEEDS:
				return 0.0D;
			case WOODCUTTING_FOCUS_SOME_SEEDS:
				return MYWORLD_SEED_REWARD_BASE_CHANCE;
			case WOODCUTTING_FOCUS_MORE_SEEDS:
				return MYWORLD_SEED_REWARD_BASE_CHANCE * 1.5D;
			case WOODCUTTING_FOCUS_MOST_SEEDS:
				return MYWORLD_SEED_REWARD_BASE_CHANCE * 2.0D;
			default:
				return MYWORLD_SEED_REWARD_BASE_CHANCE;
		}
	}

	@Override
	public boolean blockOpLoc(final Player player, final GameObject obj,
							  final String command) {
		final ObjectWoodcuttingDef def = player.getWorld().getServer().getEntityHandler().getObjectWoodcuttingDef(obj.getID());
		return (command.equals("chop") && def != null && obj.getID() != 245);
	}

	private void handleWoodcutting(final GameObject object, final Player player,
								   final int click) {
		final ObjectWoodcuttingDef def = player.getWorld().getServer().getEntityHandler().getObjectWoodcuttingDef(object.getID());
		/*if (player.isBusy()) {
			return;
		}*/
		if (!player.withinRange(object, 2)) {
			return;
		}
		if (def == null) { // This shouldn't happen
			player.message("Nothing interesting happens");
			return;
		}
		if (object.getID() == 88) {
			player.playerServerMessage(MessageType.QUEST, "The tree lashes out at you as you begin to chop it.");
			player.damage(Math.max(1, (int) Math.ceil(player.getSkills().getLevel(Skill.HITS.id()) * 0.05D)));
		}
		if (def.getReqLevel() > 1 && !config().MEMBER_WORLD) {
			player.message(player.MEMBER_MESSAGE);
			return;
		}
		if (config().WANT_FATIGUE) {
			if (config().STOP_SKILLING_FATIGUED >= 1
				&& player.getFatigue() >= player.MAX_FATIGUE) {
				player.playerServerMessage(MessageType.QUEST, "You are too tired to cut the tree");
				return;
			}
		}
		if (player.getSkills().getLevel(Skill.WOODCUTTING.id()) < def.getReqLevel()) {
			player.message("You need a woodcutting level of " + def.getReqLevel() + " to axe this tree");
			return;
		}

		// determine axe, highest tier axes are authentically searched for first
		int axeId = getAxe(player);
		if (axeId < 0) {
			player.playerServerMessage(MessageType.QUEST, "You need an axe to chop this tree down");
			return;
		}

		startbatch(1);
		batchWoodcutting(player, object, def, axeId);
	}

	private void batchWoodcutting(Player player, GameObject object, ObjectWoodcuttingDef def, int axeId) {
		player.playerServerMessage(MessageType.QUEST, "You swing your " + player.getWorld().getServer().getEntityHandler().getItemDef(axeId).getName().toLowerCase() + " at the tree...");
		ActionSender.sendActionProgressBar(player, axeId, 3);
		delay(3);
		if (ifinterrupted() || !player.withinRange(object, 2)) {
			return;
		}

		final Item log = new Item(def.getLogId());
		if (config().WANT_FATIGUE) {
			if (config().STOP_SKILLING_FATIGUED >= 1
				&& player.getFatigue() >= player.MAX_FATIGUE) {
				player.playerServerMessage(MessageType.QUEST, "You are too tired to cut the tree");
				return;
			}
		}
		if (player.getSkills().getLevel(Skill.WOODCUTTING.id()) < def.getReqLevel()) {
			player.message("You need a woodcutting level of " + def.getReqLevel() + " to axe this tree");
			return;
		}

		// New trees update; map32 introduced new trees & made woodcut xp no longer be scaled
		boolean isOldWoodcut = (player.getConfig().SCALED_WOODCUT_XP || player.getConfig().BASED_MAP_DATA < 32) && def.getLogId() == ItemId.LOGS.id();
		GameObject obj = player.getViewArea().getGameObject(object.getID(), object.getX(), object.getY());
		if (!player.getConfig().SHARED_GATHERING_RESOURCES || obj != null) {
			int quantity = Formulae.calcGatheringYield(def.getReqLevel(), player.getSkills().getLevel(Skill.WOODCUTTING.id()), getAxeTier(axeId));
			int bankedQuantity = player.getCarriedItems().getEquipment().bankSkillingDropWithLawRing(new Item(def.getLogId(), quantity));
			int remainingQuantity = quantity - bankedQuantity;
			int storedQuantity = Math.min(remainingQuantity, player.getCarriedItems().getInventory().getFreeSlots());
			if (storedQuantity > 0) {
				give(player, def.getLogId(), storedQuantity);
			}
			int overflowQuantity = remainingQuantity - storedQuantity;
			if (overflowQuantity > 0) {
				dropOverflow(player, object, def.getLogId(), overflowQuantity);
			}
			int successfulQuantity = bankedQuantity + storedQuantity;
			player.playerServerMessage(MessageType.QUEST,
				successfulQuantity > 1 ? "You get " + successfulQuantity + " logs"
					: successfulQuantity == 1 ? "You get some wood"
					: "You get some wood, but have no room to keep it");
			if (overflowQuantity > 0) {
				player.playerServerMessage(MessageType.QUEST, "Any excess falls to the ground because you have no room");
			}
			if (isOldWoodcut) {
				player.incExp(Skill.WOODCUTTING.id(), getExpRetro(player.getSkills().getMaxStat(Skill.WOODCUTTING.id()), 25) * quantity, true);
			} else {
				player.incExp(Skill.WOODCUTTING.id(), def.getExp() * quantity, true);
			}

			if (player.getCarriedItems().getEquipment().getCosmicAmuletExtraResourceChance() > 0.0D
				&& DataConversions.getRandom().nextDouble() < player.getCarriedItems().getEquipment().getCosmicAmuletExtraResourceChance()
				&& !player.getCarriedItems().getInventory().full()) {
				player.getCarriedItems().getInventory().add(new Item(def.getLogId()));
				player.playerServerMessage(MessageType.QUEST, "Your amulet hums and another log appears.");
			}

			if (player.getConfig().WANT_MYWORLD && bankedQuantity + storedQuantity > 0) {
				maybeAwardMyWorldWoodcuttingSeed(player, object, getAxeTier(axeId));
			}

		} else {
			player.playerServerMessage(MessageType.QUEST, "You slip and fail to hit the tree");
		}

		int stumpId = getTreeStumpId(object, def);
		if (obj != null && obj.getID() == object.getID() && def.getRespawnTime() > 0) {
			GameObject newObject = new GameObject(player.getWorld(), object.getLocation(), stumpId, object.getDirection(), object.getType());
			player.getWorld().replaceGameObject(object, newObject);
			player.getWorld().delayedSpawnObject(obj.getLoc(), def.getRespawnTime() * 1000);
			return;
		}

		// If tree has felled, stop the batch.
		obj = player.getViewArea().getGameObject(object.getID(), object.getX(), object.getY());
		if (obj == null) {
			stopbatch();
			return;
		}

		// Repeat
		updatebatch();
		if (!ifinterrupted() && !isbatchcomplete()) {
			delay();
			batchWoodcutting(player, object, def, axeId);
		}
	}

	@Override
	public void onOpLoc(final Player player, final GameObject object, final String command) {
		final ObjectWoodcuttingDef def = player.getWorld().getServer().getEntityHandler().getObjectWoodcuttingDef(object.getID());
		if (command.equals("chop") && def != null && object.getID() != 245) {
			if (player.getConfig().GATHER_TOOL_ON_SCENERY) {
				player.playerServerMessage(MessageType.QUEST, "You need to use the axe on the tree to chop it");
				return;
			}
			handleWoodcutting(object, player, player.click);
		}
	}

	/**
	 * Should we get a log from the tree?
	 */
	public boolean getLog(ObjectWoodcuttingDef def, int woodcutLevel, int axeId) {
		double roll = Math.random();
		return def.getRate(woodcutLevel, axeId) > roll;
	}

	public static int getExpRetro(int level, int baseExp) {
		return (int) ((baseExp + (level * 1.75)) * 4);
	}

	@Override
	public void onUseLoc(Player player, GameObject object, Item item) {
		final ObjectWoodcuttingDef def = player.getWorld().getServer().getEntityHandler().getObjectWoodcuttingDef(object.getID());
		if (inArray(item.getCatalogId(), Formulae.woodcuttingAxeIDs) && (player.getConfig().GATHER_TOOL_ON_SCENERY || !player.getClientLimitations().supportsClickWoodcut)
			&& def != null && object.getID() != 245) {
			handleWoodcutting(object, player, 0);
		}
	}

	@Override
	public boolean blockUseLoc(Player player, GameObject obj, Item item) {
		final ObjectWoodcuttingDef def = player.getWorld().getServer().getEntityHandler().getObjectWoodcuttingDef(obj.getID());
		return (inArray(item.getCatalogId(), Formulae.woodcuttingAxeIDs) && (player.getConfig().GATHER_TOOL_ON_SCENERY || !player.getClientLimitations().supportsClickWoodcut)
			&& def != null && obj.getID() != 245);
	}

	private int getTreeStumpId(GameObject object, ObjectWoodcuttingDef def) {
		if (def.getLogId() == ItemId.LOGS.id() || def.getLogId() == ItemId.MAGIC_LOGS.id()) {
			return 4;
		}
		switch (object.getID()) {
			case 32:
			case 33:
			case 88:
			case 553:
			case 1176:
			case 1240:
			case 4480:
				return 4;
			default:
				return 314;
		}
	}

	private boolean woodcuttingSkillcape(final Player player) {
		if (SkillCapes.shouldActivate(player, ItemId.WOODCUTTING_CAPE)) {
			mes("@gre@Your woodcutting cape prevents the tree from falling");
			return true;
		}
		return false;
	}

	private void maybeAwardMyWorldWoodcuttingSeed(Player player, GameObject object, int axeTier) {
		if (DataConversions.getRandom().nextDouble() >= getWoodcuttingSeedRewardChance(player)) {
			return;
		}
		SeedReward reward = rollMyWorldWoodcuttingSeed(axeTier);
		if (reward == null) {
			return;
		}
		Item seed = new Item(reward.itemId, 1);
		String seedName = seed.getDef(player.getWorld()).getName().toLowerCase();
		if (player.getCarriedItems().getInventory().full()) {
			player.getWorld().registerItem(new GroundItem(player.getWorld(), reward.itemId, object.getX(), object.getY(), 1, player));
			player.playerServerMessage(MessageType.QUEST, "You find " + formatSeedName(seedName) + ", but it falls to the ground.");
			return;
		}
		player.getCarriedItems().getInventory().add(seed);
		player.playerServerMessage(MessageType.QUEST, "You find " + formatSeedName(seedName) + " among the branches.");
		maybeDoubleRareGatheringReward(player, seed, "Your cosmic amulet glimmers and another seed appears.");
	}

	private void maybeDoubleRareGatheringReward(Player player, Item item, String message) {
		double chance = player.getCarriedItems().getEquipment().getCosmicAmuletRareGatheringDoubleChance();
		if (chance <= 0.0D || DataConversions.getRandom().nextDouble() >= chance) {
			return;
		}
		if (player.getCarriedItems().getInventory().full()) {
			player.getWorld().registerItem(new GroundItem(player.getWorld(), item.getCatalogId(), player.getX(), player.getY(), 1, player));
		} else {
			player.getCarriedItems().getInventory().add(new Item(item.getCatalogId(), 1));
		}
		player.playerServerMessage(MessageType.QUEST, message);
	}

	private SeedReward rollMyWorldWoodcuttingSeed(int axeTier) {
		int totalWeight = 0;
		for (SeedReward reward : MYWORLD_WOODCUTTING_SEED_REWARDS) {
			totalWeight += Formulae.adjustedSideRewardWeightForToolTier(reward.tier, axeTier, reward.weight);
		}
		if (totalWeight <= 0) {
			return null;
		}
		int roll = DataConversions.random(1, totalWeight);
		for (SeedReward reward : MYWORLD_WOODCUTTING_SEED_REWARDS) {
			int adjustedWeight = Formulae.adjustedSideRewardWeightForToolTier(reward.tier, axeTier, reward.weight);
			if (adjustedWeight <= 0) {
				continue;
			}
			roll -= adjustedWeight;
			if (roll <= 0) {
				return reward;
			}
		}
		return null;
	}

	private String formatSeedName(String seedName) {
		if (seedName.startsWith("adamantite") || seedName.startsWith("iron")) {
			return "an " + seedName;
		}
		return "a " + seedName;
	}

	private void dropOverflow(Player player, GameObject object, int itemId, int amount) {
		if (amount <= 0) {
			return;
		}
		if (new Item(itemId).getDef(player.getWorld()).isStackable()) {
			player.getWorld().registerItem(new GroundItem(player.getWorld(), itemId, object.getX(), object.getY(), amount, player));
			return;
		}
		for (int i = 0; i < amount; i++) {
			player.getWorld().registerItem(new GroundItem(player.getWorld(), itemId, object.getX(), object.getY(), 1, player));
		}
	}
}
