package com.openrsc.server.plugins.custom.skills.harvesting;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Skills;
import com.openrsc.server.content.SkillCapes;
import com.openrsc.server.external.ObjectHarvestingDef;
import com.openrsc.server.model.TimePoint;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.custom.minigames.ABoneToPick;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;
import com.openrsc.server.util.rsc.MessageType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openrsc.server.plugins.Functions.*;

public final class Harvesting implements OpLocTrigger {

	private static final double MYWORLD_SEED_REWARD_BASE_CHANCE = 1.0D / 50.0D;
	private static final int HARVESTING_TOOL_REWARD_TIER_SPAN = 3;
	public static final int HARVESTING_FOCUS_NO_SEEDS = Skills.CONTROLLED_MODE;
	public static final int HARVESTING_FOCUS_SOME_SEEDS = Skills.AGGRESSIVE_MODE;
	public static final int HARVESTING_FOCUS_MORE_SEEDS = Skills.ACCURATE_MODE;
	public static final int HARVESTING_FOCUS_MOST_SEEDS = Skills.DEFENSIVE_MODE;
	private static final SeedReward[] MYWORLD_HARVESTING_SEED_REWARDS = {
		new SeedReward(ItemId.HARVESTING_KNOWLEDGE_SEED.id(), 1, 45),
		new SeedReward(ItemId.HARVESTING_MONEY_SEED.id(), 1, 45),
		new SeedReward(ItemId.GUAM_SEED.id(), 2, 36),
		new SeedReward(ItemId.EYE_OF_NEWT_SEED.id(), 2, 36),
		new SeedReward(ItemId.SALMON_FOOD_SEED.id(), 3, 30),
		new SeedReward(ItemId.STEW_FOOD_SEED.id(), 3, 30),
		new SeedReward(ItemId.MARRENTILL_SEED.id(), 3, 30),
		new SeedReward(ItemId.GROUND_UNICORN_HORN_SEED.id(), 3, 30),
		new SeedReward(ItemId.TUNA_FOOD_SEED.id(), 4, 24),
		new SeedReward(ItemId.MEAT_PIE_FOOD_SEED.id(), 4, 24),
		new SeedReward(ItemId.APPLE_PIE_FOOD_SEED.id(), 4, 24),
		new SeedReward(ItemId.TARROMIN_SEED.id(), 4, 24),
		new SeedReward(ItemId.SNAPE_GRASS_SEED.id(), 4, 24),
		new SeedReward(ItemId.LOBSTER_FOOD_SEED.id(), 5, 18),
		new SeedReward(ItemId.PLAIN_PIZZA_FOOD_SEED.id(), 5, 18),
		new SeedReward(ItemId.HARRALANDER_SEED.id(), 5, 18),
		new SeedReward(ItemId.RANARR_SEED.id(), 5, 18),
		new SeedReward(ItemId.JANGERBERRIES_SEED.id(), 5, 18),
		new SeedReward(ItemId.GROUND_BLUE_DRAGON_SCALE_SEED.id(), 5, 18),
		new SeedReward(ItemId.SWORDFISH_FOOD_SEED.id(), 6, 13),
		new SeedReward(ItemId.MEAT_PIZZA_FOOD_SEED.id(), 6, 13),
		new SeedReward(ItemId.CHOCOLATE_CAKE_FOOD_SEED.id(), 6, 13),
		new SeedReward(ItemId.IRIT_SEED.id(), 6, 13),
		new SeedReward(ItemId.WHITE_BERRIES_SEED.id(), 6, 13),
		new SeedReward(ItemId.ANCHOVIE_PIZZA_FOOD_SEED.id(), 7, 9),
		new SeedReward(ItemId.AVANTOE_SEED.id(), 7, 9),
		new SeedReward(ItemId.RED_SPIDERS_EGGS_SEED.id(), 7, 9),
		new SeedReward(ItemId.LIMPWURT_SEED.id(), 7, 9),
		new SeedReward(ItemId.SHARK_FOOD_SEED.id(), 8, 6),
		new SeedReward(ItemId.PINEAPPLE_PIZZA_FOOD_SEED.id(), 8, 6),
		new SeedReward(ItemId.KWUARM_SEED.id(), 8, 6),
		new SeedReward(ItemId.CADANTINE_SEED.id(), 9, 3),
		new SeedReward(ItemId.WINE_OF_ZAMORAK_SEED.id(), 9, 3),
		new SeedReward(ItemId.DWARF_WEED_SEED.id(), 10, 1)
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

	enum HarvestingEvents {
		NEGLECTED(-1),
		NONE(0),
		WATER(1),
		SOIL(2);

		private final int id;

		HarvestingEvents(int id) {
			this.id = id;
		}

		public int getID() {
			return id;
		}
	}

	private static class ItemLevelXPTrio {
		private int itemId;
		private int level;
		private int xp;
		ItemLevelXPTrio(int itemId, int level, int xp) {
			this.itemId = itemId;
			this.level = level;
			this.xp = xp;
		}

		int getItemId() { return itemId; }

		int getLevel() {
			return level;
		}

		int getXp() {
			return xp;
		}
	}

	enum HerbsProduce {
		HERB(1274, new ItemLevelXPTrio(ItemId.UNIDENTIFIED_GUAM_LEAF.id(), 5, 50),
			new ItemLevelXPTrio(ItemId.UNIDENTIFIED_MARRENTILL.id(),12, 60),
			new ItemLevelXPTrio(ItemId.UNIDENTIFIED_TARROMIN.id(), 19, 72),
			new ItemLevelXPTrio(ItemId.UNIDENTIFIED_HARRALANDER.id(), 26, 96),
			new ItemLevelXPTrio(ItemId.UNIDENTIFIED_RANARR_WEED.id(), 33, 122),
			new ItemLevelXPTrio(ItemId.UNIDENTIFIED_IRIT_LEAF.id(), 40, 194),
			new ItemLevelXPTrio(ItemId.UNIDENTIFIED_AVANTOE.id(), 47, 246),
			new ItemLevelXPTrio(ItemId.UNIDENTIFIED_KWUARM.id(), 54, 312),
			new ItemLevelXPTrio(ItemId.UNIDENTIFIED_CADANTINE.id(), 61, 480),
			new ItemLevelXPTrio(ItemId.UNIDENTIFIED_DWARF_WEED.id(), 70, 768)),
		SEAWEED(1280, new ItemLevelXPTrio(ItemId.SEAWEED.id(), 23, 84),
			new ItemLevelXPTrio(ItemId.EDIBLE_SEAWEED.id(), 23, 84)),
		LIMPWURTROOT(1281, new ItemLevelXPTrio(ItemId.LIMPWURT_ROOT.id(), 40, 144)),
		SNAPEGRASS(1273, new ItemLevelXPTrio(ItemId.SNAPE_GRASS.id(), 58, 328));

		private int objId;
		private ArrayList<ItemLevelXPTrio> produceTable;
		HerbsProduce(int objId, ItemLevelXPTrio... produce) {
			this.objId = objId;
			produceTable = new ArrayList<>();
			produceTable.addAll(Arrays.asList(produce));
		}

		public static HerbsProduce find(int objId) {
			for (HerbsProduce h : HerbsProduce.values()) {
				if (h.objId == objId) {
					return h;
				}
			}
			return null;
		}

		public ItemLevelXPTrio get(int itemId) {
			for (ItemLevelXPTrio i : produceTable) {
				if (i.itemId == itemId) {
					return i;
				}
			}
			return null;
		}

	}

	private final int[] itemsFruitTree = new int[]{
		ItemId.LEMON.id(), ItemId.LIME.id(), ItemId.RED_APPLE.id(),
		ItemId.ORANGE.id(), ItemId.GRAPEFRUIT.id(),
	};

	private final int[] itemsRegPalm = new int[]{
		ItemId.BANANA.id(), ItemId.COCONUT.id(),
	};

	private final int[] itemsOtherPalm = new int[]{
		ItemId.PAPAYA.id(),
	};

	private final int[] itemsBush = new int[]{
		ItemId.REDBERRIES.id(), ItemId.CADAVABERRIES.id(), ItemId.DWELLBERRIES.id(),
		ItemId.JANGERBERRIES.id(), ItemId.WHITE_BERRIES.id(),
	};

	private final int[] itemsAllotments = new int[]{
		ItemId.CABBAGE.id(), ItemId.RED_CABBAGE.id(), ItemId.WHITE_PUMPKIN.id(),
		ItemId.POTATO.id(), ItemId.ONION.id(), ItemId.GARLIC.id()
	};

	private int chanceAskSoil = 5;
	private int chanceAskWatering = 7;
	public static final int WOOL_UNLOCK_LEVEL = 1;
	public static final int WOOL_HARVESTING_EXP = 20;

	public static int getTool(Player player) {
		int lvl = player.getSkills().getLevel(Skill.HARVESTING.id());
		for (int i = 0; i < Formulae.harvestingShearsIDs.length; i++) {
			int shearsId = Formulae.harvestingShearsIDs[i];
			if (player.getCarriedItems().getEquipment().hasCatalogID(shearsId)
				&& lvl >= getShearsRequiredLevel(shearsId)) {
				return shearsId;
			}
		}
		return ItemId.NOTHING.id();
	}

	public static int getShearsRequiredLevel(int shearsId) {
		for (int i = 0; i < Formulae.harvestingShearsIDs.length; i++) {
			if (Formulae.harvestingShearsIDs[i] == shearsId) {
				return Formulae.harvestingShearsLvls[i];
			}
		}
		return 1;
	}

	public static int getShearsTier(int shearsId) {
		for (int i = 0; i < Formulae.harvestingShearsIDs.length; i++) {
			if (Formulae.harvestingShearsIDs[i] == shearsId) {
				return Formulae.harvestingShearsIDs.length - i;
			}
		}
		return 1;
	}

	public static int getRequiredShearsTierForLevel(int harvestingRequirement) {
		int tier = 1;
		for (int i = 0; i < Formulae.harvestingShearsLvls.length; i++) {
			if (harvestingRequirement >= Formulae.harvestingShearsLvls[i]) {
				tier = Formulae.harvestingShearsIDs.length - i;
				break;
			}
		}
		return tier;
	}

	private static boolean hasRequiredShearsTier(int toolId, int harvestingRequirement) {
		return getShearsTier(toolId) >= getRequiredShearsTierForLevel(harvestingRequirement);
	}

	private static int getMinimumShearsRewardTier(int shearsTier) {
		return Math.max(1, shearsTier - HARVESTING_TOOL_REWARD_TIER_SPAN);
	}

	private static boolean isInShearsRewardTierRange(int shearsTier, int harvestingRequirement) {
		int produceTier = getRequiredShearsTierForLevel(harvestingRequirement);
		return produceTier >= getMinimumShearsRewardTier(shearsTier) && produceTier <= shearsTier;
	}

	private static String requiredShearsTierMessage(int harvestingRequirement) {
		return "tier " + getRequiredShearsTierForLevel(harvestingRequirement) + " harvesting shears";
	}

	public static boolean isShears(int itemId) {
		for (int shearsId : Formulae.harvestingShearsIDs) {
			if (shearsId == itemId) {
				return true;
			}
		}
		return false;
	}

	public static String getHarvestingFocusLabel(int combatStyle) {
		switch (combatStyle) {
			case HARVESTING_FOCUS_NO_SEEDS:
				return "No seeds for me";
			case HARVESTING_FOCUS_SOME_SEEDS:
				return "A few seeds";
			case HARVESTING_FOCUS_MORE_SEEDS:
				return "More seeds";
			case HARVESTING_FOCUS_MOST_SEEDS:
				return "Even more seeds!";
			default:
				return "A few seeds";
		}
	}

	private static double getHarvestingSeedRewardChance(Player player) {
		switch (player.getCombatStyle()) {
			case HARVESTING_FOCUS_NO_SEEDS:
				return 0.0D;
			case HARVESTING_FOCUS_SOME_SEEDS:
				return MYWORLD_SEED_REWARD_BASE_CHANCE;
			case HARVESTING_FOCUS_MORE_SEEDS:
				return MYWORLD_SEED_REWARD_BASE_CHANCE * 1.5D;
			case HARVESTING_FOCUS_MOST_SEEDS:
				return MYWORLD_SEED_REWARD_BASE_CHANCE * 2.0D;
			default:
				return MYWORLD_SEED_REWARD_BASE_CHANCE;
		}
	}

	@Override
	public void onOpLoc(Player player, final GameObject object, String command) {
		// Harvest of Xmas Tree
		if (object.getID() == 1238) {
			startbatch(10);
			handleXmasHarvesting(player, object);
		} else if (object.getID() == SceneryId.PUMPKIN.id()) {
			if (ABoneToPick.getStage(player) != ABoneToPick.COMPLETED) {
				if (!config().A_BONE_TO_PICK) {
					mes("These aren't yours; you should probably leave them be");
				} else {
					ABoneToPick.pumpkinPatchDialogue(player);
				}
			} else {
				handleHarvesting(object, player, player.click);
			}
		} else if (command.equalsIgnoreCase("clip")) {
			handleClipHarvesting(object, player, player.click);
		} else {
			handleHarvesting(object, player, player.click);
		}
	}

	private void handleXmasHarvesting(Player player, GameObject object) {
		player.playerServerMessage(MessageType.QUEST, "You attempt to grab a present...");
		delay(4);
		if (ifinterrupted() || !harvestingChecks(object, player)) {
			return;
		}

		final Item present = new Item(ItemId.PRESENT.id());
		if (getProduce(1, 99)) {
			//check if the tree still has gifts
			GameObject obj = player.getViewArea().getGameObject(object.getID(), object.getX(), object.getY());
			if (obj == null) {
				player.playerServerMessage(MessageType.QUEST, "You fail to take from the tree");
				return;
			} else {
				player.getCarriedItems().getInventory().add(present);
				player.playerServerMessage(MessageType.QUEST, "You get a nice looking present");
			}
			if (DataConversions.random(1, 1000) <= 100) {
				obj = player.getViewArea().getGameObject(object.getID(), object.getX(), object.getY());
				int depletedId = 1239;
				if (obj != null && obj.getID() == object.getID()) {
					GameObject newObject = new GameObject(player.getWorld(), object.getLocation(), depletedId, object.getDirection(), object.getType());
					player.getWorld().replaceGameObject(object, newObject);
					player.getWorld().delayedSpawnObject(obj.getLoc(), 300 * 1000);
				}
				return;
			}
		} else {
			player.playerServerMessage(MessageType.QUEST, "You fail to take from the tree");
			if (!isbatchcomplete()) {
				GameObject checkObj = player.getViewArea().getGameObject(object.getID(), object.getX(), object.getY());
				if (checkObj == null) {
					return;
				}
			}
		}

		// Repeat
		updatebatch();
		if (!ifinterrupted() && !isbatchcomplete()) {
			handleXmasHarvesting(player, object);
		}
	}

	private void handleClipHarvesting(final GameObject object, final Player player,
								  final int click) {
		if (!harvestingChecks(object, player)) return;

		GameObject obj = player.getViewArea().getGameObject(object.getID(), object.getX(), object.getY());
		final String objName = obj.getGameObjectDef().getName().toLowerCase();
		final HerbsProduce prodEnum = HerbsProduce.find(object.getID());
		int reqLevel = prodEnum != null ? prodEnum.produceTable.get(0).getLevel() : 1;

		int toolId = getTool(player);
		if (toolId == ItemId.NOTHING.id()) {
			player.playerServerMessage(MessageType.QUEST,
				"You need harvesting shears you can use to clip from this harvesting spot");
			return;
		}
		if (!hasRequiredShearsTier(toolId, reqLevel)) {
			player.playerServerMessage(MessageType.QUEST, "You need " + requiredShearsTierMessage(reqLevel)
				+ " to clip from the " + objName);
			return;
		}

		startbatch(1);
		batchClipping(player, object, objName, prodEnum, toolId);
	}

	private void batchClipping(Player player, GameObject object, String objName, HerbsProduce prodEnum, int toolId) {
		ActionSender.sendActionProgressBar(player, toolId, 4);
		player.playerServerMessage(MessageType.QUEST, "You attempt to clip from the spot...");
		delay(4);
		if (ifinterrupted() || !harvestingChecks(object, player)) {
			return;
		}

		// herb uses herb drop table
		// seaweed 1/4 chance to be edible
		int prodId = !objName.contains("herb")
			? (objName.contains("sea weed") && DataConversions.random(1, 4) == 1 ? prodEnum.produceTable.get(1).getItemId()
			: prodEnum.produceTable.get(0).getItemId() ) : calculateHerbDropForShearsTier(prodEnum, getShearsTier(toolId));
		if (objName.contains("herb")) {
			final double qualityChance = player.getCarriedItems().getEquipment().getCosmicAmuletHerbQualityChance();
			if (qualityChance > 0.0D && DataConversions.getRandom().nextDouble() < qualityChance) {
				final int rerollId = calculateHerbDropForShearsTier(prodEnum, getShearsTier(toolId));
				if (prodEnum.get(rerollId) != null && prodEnum.get(rerollId).getLevel() > prodEnum.get(prodId).getLevel()) {
					prodId = rerollId;
				}
			}
		}
		int reqLevel = prodEnum.produceTable.get(0).getLevel();
		final Item produce = new Item(prodId);
		if (config().WANT_FATIGUE) {
			if (config().STOP_SKILLING_FATIGUED >= 1
				&& player.getFatigue() >= player.MAX_FATIGUE) {
				player.playerServerMessage(MessageType.QUEST, "You are too tired to get produce");
				return;
			}
		}
		if (!hasRequiredShearsTier(toolId, reqLevel)) {
			player.playerServerMessage(MessageType.QUEST, "You need " + requiredShearsTierMessage(reqLevel)
				+ " to clip from the " + objName);
			return;
		}

		GameObject obj = player.getViewArea().getGameObject(object.getID(), object.getX(), object.getY());
		if (obj == null) {
			player.playerServerMessage(MessageType.QUEST, "You fail to clip the plant");
			return;
		} else {
			int quantity = Formulae.calcGatheringYield(prodEnum.get(prodId).getLevel(), player.getSkills().getLevel(Skill.HARVESTING.id()), getShearsTier(toolId));
			if (SkillCapes.shouldActivate(player, ItemId.HARVESTING_CAPE)) {
				player.playerServerMessage(MessageType.QUEST, "@or2@Your Harvesting cape activates, yielding double produce");
				quantity *= 2;
			}
			int bankedQuantity = player.getCarriedItems().getEquipment().bankSkillingDropWithLawRing(new Item(produce.getCatalogId(), quantity));
			int remainingQuantity = quantity - bankedQuantity;
			int storedQuantity = Math.min(remainingQuantity, player.getCarriedItems().getInventory().getFreeSlots());
			if (storedQuantity > 0) {
				give(player, produce.getCatalogId(), storedQuantity);
			}
			int overflowQuantity = remainingQuantity - storedQuantity;
			if (overflowQuantity > 0) {
				dropOverflow(player, object, produce.getCatalogId(), overflowQuantity);
			}
			int successfulQuantity = bankedQuantity + storedQuantity;
			player.playerServerMessage(MessageType.QUEST, "You get " + (objName.contains("herb")
				? (successfulQuantity > 1 ? successfulQuantity + " herbs" : successfulQuantity == 1 ? "a herb" : "a herb, but have no room to keep it")
				: successfulQuantity > 0 ? "some " + (objName.contains(" ") ? objName.substring(objName.lastIndexOf(" ") + 1) : "produce")
				: "some produce, but have no room to keep it"));
			if (overflowQuantity > 0) {
				player.playerServerMessage(MessageType.QUEST, "Any excess falls to the ground because you have no room");
			}

			player.incExp(Skill.HARVESTING.id(), prodEnum.get(prodId).getXp() * quantity, true);

			if (player.getCarriedItems().getEquipment().getCosmicAmuletExtraResourceChance() > 0.0D
				&& DataConversions.getRandom().nextDouble() < player.getCarriedItems().getEquipment().getCosmicAmuletExtraResourceChance()
				&& !player.getCarriedItems().getInventory().full()) {
				player.getCarriedItems().getInventory().add(new Item(produce.getCatalogId(), 1));
				player.playerServerMessage(MessageType.QUEST, "Your amulet draws out more produce.");
			}
			if (player.getConfig().WANT_MYWORLD && bankedQuantity + storedQuantity > 0) {
				maybeAwardMyWorldHarvestingSeed(player, object, getShearsTier(toolId));
			}

		}
		int depId = 1270;
		if (obj != null && obj.getID() == object.getID()) {
			GameObject newObject = new GameObject(player.getWorld(), object.getLocation(), depId, object.getDirection(), object.getType());
			player.getWorld().replaceGameObject(object, newObject);
			player.getWorld().delayedSpawnObject(obj.getLoc(), DataConversions.random(60, 240) * 1000);
			return;
		}

		// Repeat
		updatebatch();
		if (!ifinterrupted() && !isbatchcomplete()) {
			batchClipping(player, object, objName, prodEnum, toolId);
		}
	}

	private void handleHarvesting(final GameObject object, final Player player, final int click) {
		if (!harvestingChecks(object, player)) {
			player.message("I can't get close enough.");
			return;
		}

		final ObjectHarvestingDef def = player.getWorld().getServer().getEntityHandler().getObjectHarvestingDef(object.getID());

		final int toolId = getTool(player);
		if (toolId == ItemId.NOTHING.id()) {
			player.playerServerMessage(MessageType.QUEST, "You need harvesting shears you can use to gather produce");
			return;
		}

		startbatch(1);
		batchHarvest(player, toolId, object, def);
	}

	private void batchHarvest(Player player, int toolId, GameObject object, ObjectHarvestingDef def) {
		final AtomicInteger evt = new AtomicInteger(HarvestingEvents.NONE.getID());
		if (toolId != ItemId.NOTHING.id()) ActionSender.sendActionProgressBar(player, toolId, 4);
		player.playerServerMessage(MessageType.QUEST, "You attempt to get some produce...");
		delay(4);
		if (ifinterrupted() || !harvestingChecks(object, player)) {
			return;
		}

		// Player is on Death Island
		if (player.getConfig().DEATH_ISLAND && player.getX() > 957 && player.getX() < 1000 && player.getY() > 153 && player.getY() < 190) {
			ActionSender.sendRemoveProgressBar(player);
			switch (object.getID()) {
				case 1264: // Pumpkin
					player.playerServerMessage(MessageType.QUEST, "@whi@Death: Hey, those are my pumpkins!");
					break;
				case 1266: // Onion
					player.playerServerMessage(MessageType.QUEST, "@whi@Death: Hey, those are my onions!");
					break;
				case 1256: // Redberry bush
					player.playerServerMessage(MessageType.QUEST, "@whi@Death: Hey, those are my redberries!");
					break;
				default:
					player.playerServerMessage(MessageType.QUEST, "@whi@Death: Hey, that's my produce!");
					break;
			}
			delay(3);
			player.playerServerMessage(MessageType.QUEST, "@whi@Death: Don't you know how rude it is to just harvest someone else's crops?");
			delay(5);
			player.playerServerMessage(MessageType.QUEST, "@yel@" + player.getUsername() + ": Why are you growing White Pumpkins?");
			delay(3);
			player.playerServerMessage(MessageType.QUEST, "@yel@" + player.getUsername() + ": The pies won't be orange if you use those.");
			delay(3);
			player.playerServerMessage(MessageType.QUEST, "@whi@Death: I can't actually figure out how to grow the orange ones");
			delay(3);
			player.playerServerMessage(MessageType.QUEST, "@whi@Death: But I can get the right colour by dyeing it.");
			delay(3);
			player.playerServerMessage(MessageType.QUEST, "@yel@" + player.getUsername() + ": Please tell me you don't put onions and redberries in your pumpkin pies.");
			delay(3);
			player.playerServerMessage(MessageType.QUEST, "@whi@Death: Haven't got a complaint yet! You only need a little to dye it.");
			return;
		}

		final Item produce = new Item(def.getProdId());
		if (config().WANT_FATIGUE) {
			if (config().STOP_SKILLING_FATIGUED >= 1
				&& player.getFatigue() >= player.MAX_FATIGUE) {
				player.playerServerMessage(MessageType.QUEST, "You are too tired to get produce");
				return;
			}
		}
		if (!hasRequiredShearsTier(toolId, def.getReqLevel())) {
			player.playerServerMessage(MessageType.QUEST, "You need " + requiredShearsTierMessage(def.getReqLevel())
				+ " to get produce from here");
			return;
		}

		if (evt.get() == HarvestingEvents.NEGLECTED.getID()) {
			player.playerServerMessage(MessageType.QUEST, "But the spot seems weak, you decide to wait");
		} else {
			//check if the object is still up
			GameObject obj = player.getViewArea().getGameObject(object.getID(), object.getX(), object.getY());
			if (obj == null) {
				player.playerServerMessage(MessageType.QUEST, "You fail to obtain some usable produce");
				return;
			} else {
				String itemName = produce.getDef(player.getWorld()).getName().toLowerCase();
				int quantity = Formulae.calcGatheringYield(def.getReqLevel(), player.getSkills().getLevel(Skill.HARVESTING.id()), getShearsTier(toolId));
				// if player did soil (or have an active one) they get small chance for another produce
				if (DataConversions.random(1, chanceAskSoil * 3) == 1
					&& evt.get() == HarvestingEvents.SOIL.getID()) {
					quantity += 1;
				}
				if (SkillCapes.shouldActivate(player, ItemId.HARVESTING_CAPE)) {
					player.playerServerMessage(MessageType.QUEST, "@or2@Your Harvesting cape activates, yielding double produce");
					quantity *= 2;
				}
				int bankedQuantity = player.getCarriedItems().getEquipment().bankSkillingDropWithLawRing(new Item(produce.getCatalogId(), quantity));
				int remainingQuantity = quantity - bankedQuantity;
				int storedQuantity = Math.min(remainingQuantity, player.getCarriedItems().getInventory().getFreeSlots());
				if (storedQuantity > 0) {
					give(player, produce.getCatalogId(), storedQuantity);
				}
				int overflowQuantity = remainingQuantity - storedQuantity;
				if (overflowQuantity > 0) {
					dropOverflow(player, object, produce.getCatalogId(), overflowQuantity);
				}
				int successfulQuantity = bankedQuantity + storedQuantity;
				player.playerServerMessage(MessageType.QUEST, successfulQuantity > 0
					? "You get " + (itemName.endsWith("s") ? "some " : (startsWithVowel(itemName) ? "an " : "a ")) + itemName
					: "You get " + (itemName.endsWith("s") ? "some " : (startsWithVowel(itemName) ? "an " : "a ")) + itemName + ", but have no room to keep it");
				if (overflowQuantity > 0) {
					player.playerServerMessage(MessageType.QUEST, "Any excess falls to the ground because you have no room");
				}

				player.incExp(Skill.HARVESTING.id(), def.getExp() * quantity, true);
				if (player.getConfig().WANT_MYWORLD && bankedQuantity + storedQuantity > 0) {
					maybeAwardMyWorldHarvestingSeed(player, object, getShearsTier(toolId));
				}

			}
			obj = player.getViewArea().getGameObject(object.getID(), object.getX(), object.getY());
			int depId = 1270;
			int prodId = def.getProdId();
			if (DataConversions.inArray(itemsFruitTree, prodId)) {
				depId = 1252; //exhausted tree
			} else if (DataConversions.inArray(itemsRegPalm, prodId)) {
				depId = 1253; //exhausted palm
			} else if (DataConversions.inArray(itemsOtherPalm, prodId)) {
				depId = 1254; //exhausted palm2
			} else if (prodId == ItemId.FRESH_PINEAPPLE.id()) {
				depId = 1255; //exhausted pineapple
			} else if (DataConversions.inArray(itemsBush, prodId)) {
				depId = 1261; //depleted bush
			} else if (prodId == ItemId.TOMATO.id()) {
				depId = 1271; //depleted tomato
			} else if (prodId == ItemId.CORN.id()) {
				depId = 1272; //depleted corn
			} else if (prodId == ItemId.DRAGONFRUIT.id()) {
				depId = 1294; //depleted dragonfruit
			}
			if (obj != null && obj.getID() == object.getID() && def.getRespawnTime() > 0) {
				GameObject newObject = new GameObject(player.getWorld(), object.getLocation(), depId, object.getDirection(), object.getType());
				player.getWorld().replaceGameObject(object, newObject);
				player.getWorld().delayedSpawnObject(obj.getLoc(), def.getRespawnTime() * 1000);
			}
			return;
		}

		// Repeat
		updatebatch();
		if (!ifinterrupted() && !isbatchcomplete()) {
			GameObject obj = player.getViewArea().getGameObject(object.getID(), object.getX(), object.getY());
			batchHarvest(player, toolId, obj, def);
		}
	}

	private boolean harvestingChecks(final GameObject obj, final Player player) {
		boolean canReach = player.withinRange(obj, 1);
		return canReach;
	}

	private int calculateHerbDropForShearsTier(HerbsProduce prodEnum, int shearsTier) {
		int fallbackId = ItemId.UNIDENTIFIED_GUAM_LEAF.id();
		for (ItemLevelXPTrio produce : prodEnum.produceTable) {
			if (isInShearsRewardTierRange(shearsTier, produce.getLevel())) {
				fallbackId = produce.getItemId();
			}
		}
		for (int attempts = 0; attempts < 25; attempts++) {
			int herbId = Formulae.calculateHerbDrop();
			ItemLevelXPTrio produce = prodEnum.get(herbId);
			if (produce != null && isInShearsRewardTierRange(shearsTier, produce.getLevel())) {
				return herbId;
			}
		}
		return fallbackId;
	}

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		if (obj.getID() == SceneryId.RESOURCE_PLANT.id()
			|| obj.getID() == SceneryId.KNOWLEDGE_PLANT.id()
			|| obj.getID() == SceneryId.MONEY_PLANT.id()) {
			return false;
		}
		return command.equalsIgnoreCase("harvest") ||
			command.equalsIgnoreCase("clip") || (command.equals("collect") && obj.getID() == 1238);
	}

	private void maybeAwardMyWorldHarvestingSeed(Player player, GameObject object, int shearsTier) {
		if (DataConversions.getRandom().nextDouble() >= getHarvestingSeedRewardChance(player)) {
			return;
		}
		SeedReward reward = rollMyWorldHarvestingSeed(shearsTier);
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
		player.playerServerMessage(MessageType.QUEST, "You find " + formatSeedName(seedName) + " among the produce.");
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

	private SeedReward rollMyWorldHarvestingSeed(int shearsTier) {
		int totalWeight = 0;
		for (SeedReward reward : MYWORLD_HARVESTING_SEED_REWARDS) {
			totalWeight += Formulae.adjustedSideRewardWeightForToolTier(reward.tier, shearsTier, reward.weight);
		}
		if (totalWeight <= 0) {
			return null;
		}
		int roll = DataConversions.random(1, totalWeight);
		for (SeedReward reward : MYWORLD_HARVESTING_SEED_REWARDS) {
			int adjustedWeight = Formulae.adjustedSideRewardWeightForToolTier(reward.tier, shearsTier, reward.weight);
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
		return (startsWithVowel(seedName) ? "an " : "a ") + seedName;
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

	private int checkCare(GameObject obj, Player player) {
		long timestamp = System.currentTimeMillis() + 3 * 60000;
		if (DataConversions.random(1, chanceAskWatering) == 1) {
			if (player.getAttribute("watered", null) == null
				|| expiredAction(obj, player, "watered")) {
				if (!player.getCarriedItems().hasCatalogID(ItemId.WATERING_CAN.id(), Optional.of(false))) {
					return HarvestingEvents.NEGLECTED.getID();
				}
				player.playerServerMessage(MessageType.QUEST, "You water the harvesting spot");
				player.setAttribute("watered", new TimePoint(obj.getX(), obj.getY(), timestamp));
				updateUsesWateringCan(player);
			}
			return HarvestingEvents.WATER.getID();
		} else if (DataConversions.random(1, chanceAskSoil) == 1) {
			if (player.getAttribute("soiled", null) == null
				|| expiredAction(obj, player, "soiled")) {
				if (!player.getCarriedItems().hasCatalogID(ItemId.SOIL.id(), Optional.of(false))) {
					return HarvestingEvents.NEGLECTED.getID();
				}
				player.playerServerMessage(MessageType.QUEST, "You add soil to the spot");
				player.setAttribute("soiled", new TimePoint(obj.getX(), obj.getY(), timestamp));
				player.getCarriedItems().remove(new Item(ItemId.SOIL.id()));
				player.getCarriedItems().getInventory().add(new Item(ItemId.BUCKET.id()));
			}
			return HarvestingEvents.SOIL.getID();
		}
		return HarvestingEvents.NONE.getID();
	}

	private void updateUsesWateringCan(Player player) {
		if (!player.getCache().hasKey("uses_wcan")) {
			player.getCache().set("uses_wcan", 1);
		} else {
			int uses = player.getCache().getInt("uses_wcan");
			if (uses >= 4) {
				player.getCarriedItems().remove(new Item(ItemId.WATERING_CAN.id()));
				player.getCarriedItems().getInventory().add(new Item(ItemId.EMPTY_WATERING_CAN.id()));
				player.getCache().remove("uses_wcan");
			} else {
				player.getCache().put("uses_wcan", uses + 1);
			}
		}
	}

	private boolean expiredAction(GameObject obj, Player player, String key) {
		Object testObj = player.getAttribute(key);
		if (!(testObj instanceof TimePoint)) {
			return true;
		} else {
			TimePoint tp = (TimePoint) testObj;
			//expired or from distinct place
			return System.currentTimeMillis() - tp.getTimestamp() > 0 || !obj.getLocation().equals(tp.getLocation());
		}
	}

	private boolean getProduce(int reqLevel, int harvestingLevel) {
		return Formulae.calcGatheringSuccessfulLegacy(reqLevel, harvestingLevel, 0);
	}
}
