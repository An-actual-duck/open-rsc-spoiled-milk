package com.openrsc.server.plugins.custom.myworld.skills.gathering;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.SingleEvent;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.authentic.skills.woodcutting.Woodcutting;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;
import com.openrsc.server.util.rsc.MessageType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.openrsc.server.plugins.Functions.*;

public class ResourceSeeds implements OpInvTrigger, OpLocTrigger {

	private static final int DEFAULT_NODE_LIFETIME_MS = 60000;
	private static final int DEFAULT_NODE_YIELDS = 3;
	private static final int DEFAULT_NODE_ACTION_DELAY = 8;

	private static final Map<Integer, ResourceSeedDefinition> SEEDS_BY_ITEM = new HashMap<>();
	private static final Map<Integer, ResourceSeedDefinition> SEEDS_BY_SCENERY = new HashMap<>();
	private static final Map<String, ResourceNodeState> ACTIVE_NODES = new ConcurrentHashMap<>();

	static {
		register(new ResourceSeedDefinition(
			ItemId.TIN_SEED.id(),
			SceneryId.TIN_TREE.id(),
			ItemId.TIN_ORE.id(),
			Skill.WOODCUTTING.id(),
			ToolBubble.TREE,
			"tin tree",
			"tin ore",
			"A magical tree flecked with tin.",
			2,
			5,
			25,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(
			ItemId.COPPER_SEED.id(),
			SceneryId.COPPER_TREE.id(),
			ItemId.COPPER_ORE.id(),
			Skill.WOODCUTTING.id(),
			ToolBubble.TREE,
			"copper tree",
			"copper ore",
			"A magical tree flecked with copper.",
			2,
			5,
			25,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(
			ItemId.IRON_SEED.id(),
			SceneryId.IRON_TREE.id(),
			ItemId.IRON_ORE.id(),
			Skill.WOODCUTTING.id(),
			ToolBubble.TREE,
			"iron tree",
			"iron ore",
			"A magical tree flecked with iron.",
			2,
			5,
			25,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(
			ItemId.COAL_SEED.id(),
			SceneryId.COAL_TREE.id(),
			ItemId.COAL.id(),
			Skill.WOODCUTTING.id(),
			ToolBubble.TREE,
			"coal tree",
			"coal",
			"A magical tree darkened by coal.",
			2,
			5,
			25,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(
			ItemId.MITHRIL_SEED.id(),
			SceneryId.MITHRIL_TREE.id(),
			ItemId.MITHRIL_ORE.id(),
			Skill.WOODCUTTING.id(),
			ToolBubble.TREE,
			"mithril tree",
			"mithril ore",
			"A magical tree veined with mithril.",
			2,
			5,
			100,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(
			ItemId.SILVER_SEED.id(),
			SceneryId.SILVER_TREE.id(),
			ItemId.SILVER.id(),
			Skill.WOODCUTTING.id(),
			ToolBubble.TREE,
			"silver tree",
			"silver ore",
			"A magical tree shining with silver.",
			2,
			5,
			25,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(
			ItemId.GOLD_SEED.id(),
			SceneryId.GOLD_TREE.id(),
			ItemId.GOLD.id(),
			Skill.WOODCUTTING.id(),
			ToolBubble.TREE,
			"gold tree",
			"gold ore",
			"A magical tree shining with gold.",
			2,
			5,
			25,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(
			ItemId.ADAMANTITE_SEED.id(),
			SceneryId.ADAMANTITE_TREE.id(),
			ItemId.ADAMANTITE_ORE.id(),
			Skill.WOODCUTTING.id(),
			ToolBubble.TREE,
			"adamantite tree",
			"adamantite ore",
			"A magical tree veined with adamantite.",
			2,
			5,
			25,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(
			ItemId.RUNITE_SEED.id(),
			SceneryId.RUNITE_TREE.id(),
			ItemId.RUNITE_ORE.id(),
			Skill.WOODCUTTING.id(),
			ToolBubble.TREE,
			"runite tree",
			"runite ore",
			"A magical tree veined with runite.",
			2,
			5,
			25,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(
			ItemId.POTATO_SEED.id(),
			SceneryId.RESOURCE_PLANT.id(),
			ItemId.POTATO.id(),
			Skill.HARVESTING.id(),
			ToolBubble.PLANT,
			"resource plant",
			"potatoes",
			"A magical plant ready to harvest.",
			2,
			5,
			72,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(
			ItemId.ONION_SEED.id(),
			SceneryId.RESOURCE_PLANT.id(),
			ItemId.ONION.id(),
			Skill.HARVESTING.id(),
			ToolBubble.PLANT,
			"resource plant",
			"onions",
			"A magical plant ready to harvest.",
			2,
			5,
			72,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(
			ItemId.GARLIC_SEED.id(),
			SceneryId.RESOURCE_PLANT.id(),
			ItemId.GARLIC.id(),
			Skill.HARVESTING.id(),
			ToolBubble.PLANT,
			"resource plant",
			"garlic",
			"A magical plant ready to harvest.",
			2,
			5,
			72,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(
			ItemId.REDBERRY_SEED.id(),
			SceneryId.RESOURCE_PLANT.id(),
			ItemId.REDBERRIES.id(),
			Skill.HARVESTING.id(),
			ToolBubble.PLANT,
			"resource plant",
			"redberries",
			"A magical plant ready to harvest.",
			2,
			5,
			72,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(
			ItemId.LIMPWURT_SEED.id(),
			SceneryId.RESOURCE_PLANT.id(),
			ItemId.LIMPWURT_ROOT.id(),
			Skill.HARVESTING.id(),
			ToolBubble.PLANT,
			"resource plant",
			"limpwurt roots",
			"A magical plant ready to harvest.",
			2,
			5,
			72,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(
			ItemId.SNAPE_GRASS_SEED.id(),
			SceneryId.RESOURCE_PLANT.id(),
			ItemId.SNAPE_GRASS.id(),
			Skill.HARVESTING.id(),
			ToolBubble.PLANT,
			"resource plant",
			"snape grass",
			"A magical plant ready to harvest.",
			2,
			5,
			72,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(
			ItemId.GUAM_SEED.id(),
			SceneryId.RESOURCE_PLANT.id(),
			ItemId.GUAM_LEAF.id(),
			Skill.HARVESTING.id(),
			ToolBubble.PLANT,
			"resource plant",
			"guam leaves",
			"A magical plant ready to harvest.",
			2,
			5,
			72,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(
			ItemId.RANARR_SEED.id(),
			SceneryId.RESOURCE_PLANT.id(),
			ItemId.RANARR_WEED.id(),
			Skill.HARVESTING.id(),
			ToolBubble.PLANT,
			"resource plant",
			"ranarr weeds",
			"A magical plant ready to harvest.",
			2,
			5,
			72,
			DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS
		));
		register(new ResourceSeedDefinition(ItemId.WOODCUTTING_KNOWLEDGE_SEED.id(), SceneryId.KNOWLEDGE_TREE.id(),
			ItemId.NOTHING.id(), Skill.WOODCUTTING.id(), ToolBubble.TREE, "knowledge tree", "Woodcutting experience",
			"A magical tree humming with knowledge.", 100, 400, 0, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS, YieldType.XP));
		register(new ResourceSeedDefinition(ItemId.WOODCUTTING_MONEY_SEED.id(), SceneryId.MONEY_TREE.id(),
			ItemId.COINS.id(), Skill.WOODCUTTING.id(), ToolBubble.TREE, "money tree", "coins",
			"A magical tree glittering with coins.", 500, 2000, 0, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS, YieldType.GOLD));
		register(new ResourceSeedDefinition(ItemId.HARVESTING_KNOWLEDGE_SEED.id(), SceneryId.KNOWLEDGE_PLANT.id(),
			ItemId.NOTHING.id(), Skill.HARVESTING.id(), ToolBubble.PLANT, "knowledge plant", "Harvesting experience",
			"A magical plant humming with knowledge.", 100, 400, 0, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS, YieldType.XP));
		register(new ResourceSeedDefinition(ItemId.HARVESTING_MONEY_SEED.id(), SceneryId.MONEY_PLANT.id(),
			ItemId.COINS.id(), Skill.HARVESTING.id(), ToolBubble.PLANT, "money plant", "coins",
			"A magical plant glittering with coins.", 500, 2000, 0, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS, YieldType.GOLD));
		register(new ResourceSeedDefinition(ItemId.OAK_LOG_SEED.id(), SceneryId.RESOURCE_TREE.id(),
			ItemId.OAK_LOGS.id(), Skill.WOODCUTTING.id(), ToolBubble.TREE, "resource tree", "oak logs",
			"A magical tree ready to harvest.", 2, 5, 25, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS));
		register(new ResourceSeedDefinition(ItemId.WILLOW_LOG_SEED.id(), SceneryId.RESOURCE_TREE.id(),
			ItemId.WILLOW_LOGS.id(), Skill.WOODCUTTING.id(), ToolBubble.TREE, "resource tree", "willow logs",
			"A magical tree ready to harvest.", 2, 5, 25, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS));
		register(new ResourceSeedDefinition(ItemId.PALM_LOG_SEED.id(), SceneryId.RESOURCE_TREE.id(),
			ItemId.PALM_LOGS.id(), Skill.WOODCUTTING.id(), ToolBubble.TREE, "resource tree", "palm logs",
			"A magical tree ready to harvest.", 2, 5, 25, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS));
		register(new ResourceSeedDefinition(ItemId.MAPLE_LOG_SEED.id(), SceneryId.RESOURCE_TREE.id(),
			ItemId.MAPLE_LOGS.id(), Skill.WOODCUTTING.id(), ToolBubble.TREE, "resource tree", "maple logs",
			"A magical tree ready to harvest.", 2, 5, 25, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS));
		register(new ResourceSeedDefinition(ItemId.YEW_LOG_SEED.id(), SceneryId.RESOURCE_TREE.id(),
			ItemId.YEW_LOGS.id(), Skill.WOODCUTTING.id(), ToolBubble.TREE, "resource tree", "yew logs",
			"A magical tree ready to harvest.", 2, 5, 25, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS));
		register(new ResourceSeedDefinition(ItemId.EBONY_LOG_SEED.id(), SceneryId.RESOURCE_TREE.id(),
			ItemId.EBONY_LOGS.id(), Skill.WOODCUTTING.id(), ToolBubble.TREE, "resource tree", "ebony logs",
			"A magical tree ready to harvest.", 2, 5, 25, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS));
		register(new ResourceSeedDefinition(ItemId.MAGIC_LOG_SEED.id(), SceneryId.RESOURCE_TREE.id(),
			ItemId.MAGIC_LOGS.id(), Skill.WOODCUTTING.id(), ToolBubble.TREE, "resource tree", "magic logs",
			"A magical tree ready to harvest.", 2, 5, 25, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS));
		register(new ResourceSeedDefinition(ItemId.BLOOD_LOG_SEED.id(), SceneryId.RESOURCE_TREE.id(),
			ItemId.BLOOD_LOGS.id(), Skill.WOODCUTTING.id(), ToolBubble.TREE, "resource tree", "blood logs",
			"A magical tree ready to harvest.", 2, 5, 25, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS));
		register(new ResourceSeedDefinition(ItemId.SAPPHIRE_SEED.id(), SceneryId.RESOURCE_TREE.id(),
			ItemId.UNCUT_SAPPHIRE.id(), Skill.WOODCUTTING.id(), ToolBubble.TREE, "resource tree", "uncut sapphires",
			"A magical tree ready to harvest.", 1, 3, 25, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS));
		register(new ResourceSeedDefinition(ItemId.EMERALD_SEED.id(), SceneryId.RESOURCE_TREE.id(),
			ItemId.UNCUT_EMERALD.id(), Skill.WOODCUTTING.id(), ToolBubble.TREE, "resource tree", "uncut emeralds",
			"A magical tree ready to harvest.", 1, 3, 25, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS));
		register(new ResourceSeedDefinition(ItemId.RUBY_SEED.id(), SceneryId.RESOURCE_TREE.id(),
			ItemId.UNCUT_RUBY.id(), Skill.WOODCUTTING.id(), ToolBubble.TREE, "resource tree", "uncut rubies",
			"A magical tree ready to harvest.", 1, 3, 25, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS));
		register(new ResourceSeedDefinition(ItemId.DIAMOND_SEED.id(), SceneryId.RESOURCE_TREE.id(),
			ItemId.UNCUT_DIAMOND.id(), Skill.WOODCUTTING.id(), ToolBubble.TREE, "resource tree", "uncut diamonds",
			"A magical tree ready to harvest.", 1, 3, 25, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS));
		register(new ResourceSeedDefinition(ItemId.DRAGONSTONE_SEED.id(), SceneryId.RESOURCE_TREE.id(),
			ItemId.UNCUT_DRAGONSTONE.id(), Skill.WOODCUTTING.id(), ToolBubble.TREE, "resource tree", "uncut dragonstones",
			"A magical tree ready to harvest.", 1, 1, 25, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,
			DEFAULT_NODE_LIFETIME_MS));
		registerHarvestingSeed(ItemId.SALMON_FOOD_SEED.id(), ItemId.SALMON.id(), "salmon", 2, 4);
		registerHarvestingSeed(ItemId.TUNA_FOOD_SEED.id(), ItemId.TUNA.id(), "tuna", 2, 4);
		registerHarvestingSeed(ItemId.LOBSTER_FOOD_SEED.id(), ItemId.LOBSTER.id(), "lobsters", 2, 4);
		registerHarvestingSeed(ItemId.SWORDFISH_FOOD_SEED.id(), ItemId.SWORDFISH.id(), "swordfish", 1, 3);
		registerHarvestingSeed(ItemId.SHARK_FOOD_SEED.id(), ItemId.SHARK.id(), "sharks", 1, 3);
		registerHarvestingSeed(ItemId.STEW_FOOD_SEED.id(), ItemId.STEW.id(), "stews", 2, 3);
		registerHarvestingSeed(ItemId.MEAT_PIE_FOOD_SEED.id(), ItemId.MEAT_PIE.id(), "meat pies", 2, 3);
		registerHarvestingSeed(ItemId.APPLE_PIE_FOOD_SEED.id(), ItemId.APPLE_PIE.id(), "apple pies", 2, 3);
		registerHarvestingSeed(ItemId.PLAIN_PIZZA_FOOD_SEED.id(), ItemId.PLAIN_PIZZA.id(), "plain pizzas", 2, 3);
		registerHarvestingSeed(ItemId.MEAT_PIZZA_FOOD_SEED.id(), ItemId.MEAT_PIZZA.id(), "meat pizzas", 2, 3);
		registerHarvestingSeed(ItemId.CHOCOLATE_CAKE_FOOD_SEED.id(), ItemId.CHOCOLATE_CAKE.id(), "chocolate cakes", 1, 3);
		registerHarvestingSeed(ItemId.ANCHOVIE_PIZZA_FOOD_SEED.id(), ItemId.ANCHOVIE_PIZZA.id(), "anchovie pizzas", 2, 3);
		registerHarvestingSeed(ItemId.PINEAPPLE_PIZZA_FOOD_SEED.id(), ItemId.PINEAPPLE_PIZZA.id(), "pineapple pizzas", 1, 3);
		registerHarvestingSeed(ItemId.MARRENTILL_SEED.id(), ItemId.MARRENTILL.id(), "marrentill", 2, 5);
		registerHarvestingSeed(ItemId.TARROMIN_SEED.id(), ItemId.TARROMIN.id(), "tarromin", 2, 5);
		registerHarvestingSeed(ItemId.HARRALANDER_SEED.id(), ItemId.HARRALANDER.id(), "harralander", 2, 5);
		registerHarvestingSeed(ItemId.IRIT_SEED.id(), ItemId.IRIT_LEAF.id(), "irit leaves", 2, 4);
		registerHarvestingSeed(ItemId.AVANTOE_SEED.id(), ItemId.AVANTOE.id(), "avantoe", 2, 4);
		registerHarvestingSeed(ItemId.KWUARM_SEED.id(), ItemId.KWUARM.id(), "kwuarm", 1, 3);
		registerHarvestingSeed(ItemId.CADANTINE_SEED.id(), ItemId.CADANTINE.id(), "cadantine", 1, 3);
		registerHarvestingSeed(ItemId.DWARF_WEED_SEED.id(), ItemId.DWARF_WEED.id(), "dwarf weed", 1, 3);
		registerHarvestingSeed(ItemId.EYE_OF_NEWT_SEED.id(), ItemId.EYE_OF_NEWT.id(), "eyes of newt", 2, 5);
		registerHarvestingSeed(ItemId.GROUND_UNICORN_HORN_SEED.id(), ItemId.GROUND_UNICORN_HORN.id(), "ground unicorn horn", 2, 4);
		registerHarvestingSeed(ItemId.GROUND_BLUE_DRAGON_SCALE_SEED.id(), ItemId.GROUND_BLUE_DRAGON_SCALE.id(), "ground blue dragon scale", 1, 3);
		registerHarvestingSeed(ItemId.WHITE_BERRIES_SEED.id(), ItemId.WHITE_BERRIES.id(), "white berries", 2, 4);
		registerHarvestingSeed(ItemId.RED_SPIDERS_EGGS_SEED.id(), ItemId.RED_SPIDERS_EGGS.id(), "red spiders' eggs", 1, 3);
		registerHarvestingSeed(ItemId.JANGERBERRIES_SEED.id(), ItemId.JANGERBERRIES.id(), "jangerberries", 2, 4);
		registerHarvestingSeed(ItemId.WINE_OF_ZAMORAK_SEED.id(), ItemId.WINE_OF_ZAMORAK.id(), "wine of zamorak", 1, 2);
	}

	private static void registerHarvestingSeed(int seedId, int rewardItemId, String rewardLabel, int minYield, int maxYield) {
		register(new ResourceSeedDefinition(seedId, SceneryId.RESOURCE_PLANT.id(), rewardItemId,
			Skill.HARVESTING.id(), ToolBubble.PLANT, "resource plant", rewardLabel,
			"A magical plant ready to harvest.", minYield, maxYield, 72, DEFAULT_NODE_YIELDS,
			DEFAULT_NODE_ACTION_DELAY, DEFAULT_NODE_LIFETIME_MS));
	}

	private enum ToolBubble {
		TREE,
		PLANT
	}

	private enum YieldType {
		ITEM,
		XP,
		GOLD
	}

	private static final class ResourceSeedDefinition {
		private final int seedId;
		private final int sceneryId;
		private final int rewardItemId;
		private final int skillId;
		private final ToolBubble toolBubble;
		private final String nodeName;
		private final String rewardName;
		private final String examine;
		private final int minYield;
		private final int maxYield;
		private final int xpPerYield;
		private final int totalYields;
		private final int actionDelay;
		private final int lifetimeMs;
		private final YieldType yieldType;

		private ResourceSeedDefinition(
			int seedId,
			int sceneryId,
			int rewardItemId,
			int skillId,
			ToolBubble toolBubble,
			String nodeName,
			String rewardName,
			String examine,
			int minYield,
			int maxYield,
			int xpPerYield,
			int totalYields,
			int actionDelay,
			int lifetimeMs
		) {
			this(
				seedId,
				sceneryId,
				rewardItemId,
				skillId,
				toolBubble,
				nodeName,
				rewardName,
				examine,
				minYield,
				maxYield,
				xpPerYield,
				totalYields,
				actionDelay,
				lifetimeMs,
				YieldType.ITEM
			);
		}

		private ResourceSeedDefinition(
			int seedId,
			int sceneryId,
			int rewardItemId,
			int skillId,
			ToolBubble toolBubble,
			String nodeName,
			String rewardName,
			String examine,
			int minYield,
			int maxYield,
			int xpPerYield,
			int totalYields,
			int actionDelay,
			int lifetimeMs,
			YieldType yieldType
		) {
			this.seedId = seedId;
			this.sceneryId = sceneryId;
			this.rewardItemId = rewardItemId;
			this.skillId = skillId;
			this.toolBubble = toolBubble;
			this.nodeName = nodeName;
			this.rewardName = rewardName;
			this.examine = examine;
			this.minYield = minYield;
			this.maxYield = maxYield;
			this.xpPerYield = xpPerYield;
			this.totalYields = totalYields;
			this.actionDelay = actionDelay;
			this.lifetimeMs = lifetimeMs;
			this.yieldType = yieldType;
		}
	}

	private static final class ResourceNodeState {
		private final ResourceSeedDefinition definition;
		private final String owner;
		private int remainingYields;

		private ResourceNodeState(ResourceSeedDefinition definition, String owner) {
			this.definition = definition;
			this.owner = owner;
			this.remainingYields = definition.totalYields;
		}
	}

	@Override
	public boolean blockOpInv(Player player, Integer invIndex, Item item, String command) {
		return player.getConfig().WANT_MYWORLD
			&& SEEDS_BY_ITEM.containsKey(item.getCatalogId())
			&& "open".equalsIgnoreCase(command);
	}

	@Override
	public void onOpInv(Player player, Integer invIndex, Item item, String command) {
		if (!blockOpInv(player, invIndex, item, command)) {
			return;
		}
		ResourceSeedDefinition definition = SEEDS_BY_ITEM.get(item.getCatalogId());
		Point plantLocation = findPlantLocation(player);
		if (plantLocation == null) {
			player.message("you can't plant that here");
			return;
		}
		if (player.getCarriedItems().remove(new Item(definition.seedId, 1)) == -1) {
			return;
		}
		mes("you open the small seed case");
		delay(3);
		mes("and drop a seed by your feet");
		delay(3);

		GameObject object = new GameObject(
			player.getWorld(),
			plantLocation,
			definition.sceneryId,
			0,
			0,
			player.getUsername()
		);
		String key = nodeKey(object);
		ACTIVE_NODES.put(key, new ResourceNodeState(definition, player.getUsername()));
		player.getWorld().registerGameObject(object);
		GameObject registered = player.getWorld().getRegionManager()
			.getRegion(object.getLocation())
			.getGameObject(object.getLocation(), player);
		boolean registrationSucceeded = registered != null && registered.getID() == object.getID();
		if (!registrationSucceeded) {
			ACTIVE_NODES.remove(key);
			give(player, definition.seedId, 1);
			player.message("The seed fails to take root.");
			return;
		}
		scheduleNodeRemoval(player, object, key);
		player.message(formatArticle(definition.nodeName) + " magically sprouts nearby");
	}

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return player.getConfig().WANT_MYWORLD
			&& SEEDS_BY_SCENERY.containsKey(obj.getID())
			&& ("chop".equalsIgnoreCase(command) || "harvest".equalsIgnoreCase(command) || "examine".equalsIgnoreCase(command));
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (!blockOpLoc(player, obj, command)) {
			return;
		}
		if ("examine".equalsIgnoreCase(command)) {
			ResourceNodeState state = ACTIVE_NODES.get(nodeKey(obj));
			ResourceSeedDefinition definition = state != null ? state.definition : SEEDS_BY_SCENERY.get(obj.getID());
			player.message(definition.examine);
			return;
		}
		ResourceNodeState state = ACTIVE_NODES.get(nodeKey(obj));
		if (state == null) {
			player.message("The plant is already fading.");
			return;
		}
		if (!state.owner.equalsIgnoreCase(player.getUsername())) {
			player.message("This is not yours to harvest.");
			return;
		}
		if (!player.withinRange(obj, 2)) {
			return;
		}
		if (state.remainingYields <= 0) {
			removeNode(obj);
			return;
		}

		startbatch(state.remainingYields);
		harvestResourceNode(player, obj, state);
	}

	private void harvestResourceNode(Player player, GameObject obj, ResourceNodeState state) {
		ResourceSeedDefinition definition = state.definition;
		GameObject current = player.getWorld().getRegionManager()
			.getRegion(obj.getLocation())
			.getGameObject(obj.getLocation(), player);
		if (current == null || current.getID() != obj.getID() || current.isRemoved()) {
			stopbatch();
			return;
		}
		if (state.remainingYields <= 0) {
			removeNode(current);
			stopbatch();
			return;
		}
		if (!player.withinRange(current, 2)) {
			stopbatch();
			return;
		}

			ActionSender.sendActionProgressBar(player, getToolBubbleId(player, definition.toolBubble), definition.actionDelay);
			player.playerServerMessage(MessageType.QUEST, "You gather from the " + definition.nodeName + "...");
			delay(definition.actionDelay);
			if (ifinterrupted() || !player.withinRange(current, 2)) {
				stopbatch();
				return;
			}
			grantYield(player, current, definition);

		state.remainingYields--;
		if (state.remainingYields <= 0) {
			player.message("The " + definition.nodeName + " crumbles away.");
			removeNode(current);
			stopbatch();
			return;
		}

		updatebatch();
		if (!ifinterrupted() && !isbatchcomplete()) {
			delay();
			harvestResourceNode(player, current, state);
		}
	}

	private void grantYield(Player player, GameObject current, ResourceSeedDefinition definition) {
		int quantity = DataConversions.random(definition.minYield, definition.maxYield);
		if (definition.yieldType == YieldType.XP) {
			int xp = player.getSkills().getLevel(definition.skillId) * quantity;
			player.incExp(definition.skillId, xp, true);
			player.playerServerMessage(MessageType.QUEST, "You absorb " + xp + " " + definition.rewardName + ".");
			return;
		}
		if (definition.yieldType == YieldType.GOLD) {
			int coins = player.getSkills().getLevel(definition.skillId) * quantity;
			give(player, ItemId.COINS.id(), coins);
			player.playerServerMessage(MessageType.QUEST, "You harvest " + coins + " coins.");
			return;
		}

		int storedQuantity = Math.min(quantity, player.getCarriedItems().getInventory().getFreeSlots());
		if (storedQuantity > 0) {
			give(player, definition.rewardItemId, storedQuantity);
		}
		int droppedQuantity = quantity - storedQuantity;
		if (droppedQuantity > 0) {
			dropOverflow(player, current, definition.rewardItemId, droppedQuantity);
		}
		player.incExp(definition.skillId, definition.xpPerYield, true);
		player.playerServerMessage(MessageType.QUEST,
			storedQuantity > 1 ? "You harvest " + storedQuantity + " " + definition.rewardName + "."
				: storedQuantity == 1 ? "You harvest 1 " + definition.rewardName + "."
				: "You find " + definition.rewardName + ", but have no room to keep it.");
		if (droppedQuantity > 0) {
			player.playerServerMessage(MessageType.QUEST,
				droppedQuantity > 1 ? droppedQuantity + " " + definition.rewardName + " fall to the ground."
					: "1 " + definition.rewardName + " falls to the ground.");
		}
	}

	private void scheduleNodeRemoval(Player player, GameObject object, String key) {
		object.getWorld().getServer().getGameEventHandler().add(new SingleEvent(
			object.getWorld(),
			null,
			ACTIVE_NODES.get(key).definition.lifetimeMs,
			"MyWorld Resource Seed Removal"
		) {
			@Override
			public void action() {
				ResourceNodeState state = ACTIVE_NODES.get(key);
				if (state == null) {
					return;
				}
				GameObject current = player.getWorld().getRegionManager()
					.getRegion(object.getLocation())
					.getGameObject(object.getLocation(), player);
				if (current != null && current.getID() == object.getID() && key.equals(nodeKey(current))) {
					removeNode(current);
				}
			}
		});
	}

	private static void removeNode(GameObject object) {
		ACTIVE_NODES.remove(nodeKey(object));
		object.getWorld().unregisterGameObject(object);
	}

	private static Point findPlantLocation(Player player) {
		int[][] offsets = {
			{0, -1},
			{1, 0},
			{0, 1},
			{-1, 0},
			{1, -1},
			{1, 1},
			{-1, 1},
			{-1, -1}
		};
		for (int[] offset : offsets) {
			Point location = Point.location(player.getX() + offset[0], player.getY() + offset[1]);
			if (!player.getWorld().withinWorld(location.getX(), location.getY())) {
				continue;
			}
			if (player.getWorld().getTile(location) == null
				|| (player.getWorld().getTile(location).traversalMask & CollisionFlag.FULL_BLOCK) != 0
				|| !PathValidation.checkAdjacentDistance(player.getWorld(), player.getX(), player.getY(),
					location.getX(), location.getY(), true, false)) {
				continue;
			}
			GameObject existing = player.getWorld().getRegionManager()
				.getRegion(location)
				.getGameObject(location, player);
			if (existing == null && !hasMobAt(player, location)) {
				return location;
			}
		}
		return null;
	}

	private static boolean hasMobAt(Player player, Point location) {
		for (Player nearbyPlayer : player.getViewArea().getPlayersInView()) {
			if (!nearbyPlayer.isRemoved() && nearbyPlayer.getLocation().equals(location)) {
				return true;
			}
		}
		for (Npc npc : player.getViewArea().getNpcsInView()) {
			if (!npc.isRemoved() && !npc.isRespawning() && npc.getLocation().equals(location)) {
				return true;
			}
		}
		return false;
	}

	private static String nodeKey(GameObject object) {
		return object.getID() + ":" + object.getLoc().getX() + ":" + object.getLoc().getY() + ":" + object.getOwner();
	}

	private static void register(ResourceSeedDefinition definition) {
		SEEDS_BY_ITEM.put(definition.seedId, definition);
		SEEDS_BY_SCENERY.put(definition.sceneryId, definition);
	}

	private static int getToolBubbleId(Player player, ToolBubble toolBubble) {
		if (toolBubble == ToolBubble.PLANT) {
			for (int shearsId : Formulae.harvestingShearsIDs) {
				if (player.getCarriedItems().getEquipment().hasCatalogID(shearsId)) {
					return shearsId;
				}
			}
			return ItemId.SHEARS.id();
		}
		int axeId = Woodcutting.getAxe(player);
		return axeId == -1 ? ItemId.BRONZE_AXE.id() : axeId;
	}

	private static String formatArticle(String name) {
		String article = name.matches("(?i)^[aeiou].*") ? "an " : "a ";
		return article + name;
	}

	private static void dropOverflow(Player player, GameObject obj, int itemId, int amount) {
		if (amount <= 0) {
			return;
		}
		boolean stackable = new Item(itemId).getDef(player.getWorld()).isStackable();
		if (stackable) {
			player.getWorld().registerItem(new GroundItem(player.getWorld(), itemId, obj.getX(), obj.getY(), amount, player));
			return;
		}
		for (int i = 0; i < amount; i++) {
			player.getWorld().registerItem(new GroundItem(player.getWorld(), itemId, obj.getX(), obj.getY(), 1, player));
		}
	}
}
