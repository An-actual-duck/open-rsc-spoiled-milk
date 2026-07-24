package com.openrsc.server.plugins.authentic.skills.mining;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Skills;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.content.SkillCapes;
import com.openrsc.server.external.GameObjectDef;
import com.openrsc.server.external.ObjectMiningDef;
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

public final class Mining implements OpLocTrigger, UseLocTrigger {

	public static final int MINING_FOCUS_NO_GEODES = Skills.CONTROLLED_MODE;
	public static final int MINING_FOCUS_SOME_GEODES = Skills.AGGRESSIVE_MODE;
	public static final int MINING_FOCUS_MORE_GEODES = Skills.ACCURATE_MODE;
	public static final int MINING_FOCUS_MOST_GEODES = Skills.DEFENSIVE_MODE;
	private static final int GEM_ROCK = 588;
	private static final int GEM_ROCK_REQ_LEVEL = 40;
	private static final int GEM_ROCK_EXP = 260;
	private static final int GEM_ROCK_RESPAWN_SECONDS = 70;
	private static final double MYWORLD_GEODE_REWARD_BASE_CHANCE = 1.0D / 50.0D;
	private static final GeodeReward[] MYWORLD_MINING_GEODE_REWARDS = {
		new GeodeReward(ItemId.SMALL_GEODE.id()),
		new GeodeReward(ItemId.STANDARD_GEODE.id()),
		new GeodeReward(ItemId.LARGE_GEODE.id()),
		new GeodeReward(ItemId.HUGE_GEODE.id())
	};
	private static final int[] GEM_ROCK_GEM_IDS = {
		ItemId.UNCUT_OPAL.id(),
		ItemId.UNCUT_JADE.id(),
		ItemId.UNCUT_RED_TOPAZ.id(),
		ItemId.UNCUT_SAPPHIRE.id(),
		ItemId.UNCUT_EMERALD.id(),
		ItemId.UNCUT_RUBY.id(),
		ItemId.UNCUT_DIAMOND.id()
	};
	private static final int[] GEM_ROCK_GEM_WEIGHTS = {64, 32, 16, 8, 3, 3, 2};

	private static final class GeodeReward {
		private final int itemId;

		private GeodeReward(int itemId) {
			this.itemId = itemId;
		}
	}

	public static int getAxe(Player player) {
		int lvl = player.getSkills().getLevel(Skill.MINING.id());
		for (int i = 0; i < Formulae.miningAxeIDs.length; i++) {
			if (player.getCarriedItems().getEquipment().hasCatalogID(Formulae.miningAxeIDs[i])
				&& lvl >= Formulae.miningAxeLvls[i]) {
				return Formulae.miningAxeIDs[i];
			}
		}
		return -1;
	}

	public static int getPickaxeRequiredLevel(int axeId) {
		switch (ItemId.getById(axeId)) {
			case RUNE_PICKAXE:
				return 70;
			case ORICHALCUM_PICKAXE:
				return 62;
			case ADAMANTITE_PICKAXE:
				return 54;
			case TITAN_STEEL_PICKAXE:
				return 46;
			case MITHRIL_PICKAXE:
				return 38;
			case STEEL_PICKAXE:
				return 30;
			case IRON_PICKAXE:
				return 22;
			case BRONZE_PICKAXE:
				return 15;
			case COPPER_PICKAXE:
				return 8;
			case TIN_PICKAXE:
			default:
				return 1;
		}
	}

	public static int getPickaxeRepeat(int axeId) {
		switch (ItemId.getById(axeId)) {
			case COPPER_PICKAXE:
				return 2;
			case BRONZE_PICKAXE:
				return 3;
			case IRON_PICKAXE:
				return 5;
			case STEEL_PICKAXE:
				return 8;
			case MITHRIL_PICKAXE:
				return 12;
			case TITAN_STEEL_PICKAXE:
				return 16;
			case ADAMANTITE_PICKAXE:
				return 20;
			case ORICHALCUM_PICKAXE:
				return 24;
			case RUNE_PICKAXE:
				return 28;
			case TIN_PICKAXE:
			default:
				return 1;
		}
	}

	public static int getPickaxeTier(int axeId) {
		switch (ItemId.getById(axeId)) {
			case COPPER_PICKAXE:
				return 2;
			case BRONZE_PICKAXE:
				return 3;
			case IRON_PICKAXE:
				return 4;
			case STEEL_PICKAXE:
				return 5;
			case MITHRIL_PICKAXE:
				return 6;
			case TITAN_STEEL_PICKAXE:
				return 7;
			case ADAMANTITE_PICKAXE:
				return 8;
			case ORICHALCUM_PICKAXE:
				return 9;
			case RUNE_PICKAXE:
				return 10;
			case TIN_PICKAXE:
			default:
				return 1;
		}
	}

	public static String getMiningFocusLabel(int combatStyle) {
		switch (combatStyle) {
			case MINING_FOCUS_NO_GEODES:
				return "Just the ore";
			case MINING_FOCUS_SOME_GEODES:
				return "A few geodes";
			case MINING_FOCUS_MORE_GEODES:
				return "Plenty of geodes";
			case MINING_FOCUS_MOST_GEODES:
				return "Lots of geodes";
			default:
				return "A few geodes";
		}
	}

	private static int getMiningFocus(Player player) {
		return player.getCombatStyle();
	}

	private static double getRandomGeodeChance(Player player) {
		return Formulae.gatheringSideRewardChanceForFocus(getMiningFocus(player), MYWORLD_GEODE_REWARD_BASE_CHANCE);
	}

	@Override
	public void onOpLoc(Player player, final GameObject object, String command) {
		if ((command.equals("mine") || command.equals("prospect"))
			&& object.getID() != 1227) {
			if (command.equals("mine") && player.getConfig().GATHER_TOOL_ON_SCENERY) {
				player.playerServerMessage(MessageType.QUEST, "You need to use the pickaxe on the rock to mine it");
				return;
			}
			handleMiningEntry(player, object, command);
		}
	}

	private void handleMiningEntry(Player player, final GameObject object, String command) {
		if (object.getID() == 269) {
			if (command.equalsIgnoreCase("mine")) {
				if (getAxe(player) != -1) {
					if (getCurrentLevel(player, Skill.MINING.id()) >= 50) {
						player.message("you manage to dig a way through the rockslide");
						if (player.getX() <= 425) {
							player.teleport(428, 438);
						} else {
							player.teleport(425, 438);
						}
					} else {
						player.playerServerMessage(MessageType.QUEST, "You need a mining level of 50 to clear the rockslide");
					}
				} else {
					player.playerServerMessage(MessageType.QUEST, "you need a pickaxe to clear the rockslide");
				}
			} else if (command.equalsIgnoreCase("prospect")) {
				player.playerServerMessage(MessageType.QUEST, "these rocks contain nothing interesting");
				player.playerServerMessage(MessageType.QUEST, "they are just in the way");
			}
		} else if (object.getID() == 770) {
			if (getAxe(player) != -1) {
				mes("you mine the rock");
				delay(3);
				mes("and break of several large chunks");
				delay(3);
				give(player, ItemId.ROCKS.id(), 1);
			} else {
				player.message("you need a pickaxe to mine this rock");
			}
		} else if (object.getID() == 1026) { // watchtower - rock of dalgroth
			if (command.equalsIgnoreCase("mine")) {
				if (player.getQuestStage(Quests.WATCHTOWER) == 9) {
					if (getAxe(player) == -1) {
						player.playerServerMessage(MessageType.QUEST, "You need a pickaxe to mine the rock");
						return;
					}
					if (getCurrentLevel(player, Skill.MINING.id()) < 40) {
						player.playerServerMessage(MessageType.QUEST, "You need a mining level of 40 to mine this crystal out");
						return;
					}
					if (player.getCarriedItems().hasCatalogID(ItemId.POWERING_CRYSTAL4.id(), Optional.empty())) {
						say(player, null, "I already have this crystal",
							"There is no benefit to getting another");
						return;
					}
					player.playSound("mine");
					// special bronze pick bubble for rock of dalgroth - see wiki
					thinkbubble(new Item(ItemId.BRONZE_PICKAXE.id()));
					player.message("You have a swing at the rock!");
					player.playerServerMessage(MessageType.QUEST, "You swing your pick at the rock...");
					player.message("A crack appears in the rock and you prize a crystal out");
					give(player, ItemId.POWERING_CRYSTAL4.id(), 1);
				} else {
					say(player, null, "I can't touch it...",
						"Perhaps it is linked with the shaman some way ?");
				}
			} else if (command.equalsIgnoreCase("prospect")) {
				player.playSound("prospect");
				player.playerServerMessage(MessageType.QUEST, "You examine the rock for ores...");
				player.playerServerMessage(MessageType.QUEST, "This rock contains a crystal!");
			}
		} else {
			handleMining(object, player, player.click);
		}
	}

	private void handleMining(final GameObject rock, Player player, int click) {
		if (isTemporaryDepletedOreRock(rock)) {
			handleDepletedOreRock(rock, player, click);
			return;
		}

		if (rock.getID() == GEM_ROCK) {
			handleGemRockMining(rock, player, click);
			return;
		}

		if (!player.withinRange(rock, 1)) {
			return;
		}

		final ObjectMiningDef def = player.getWorld().getServer().getEntityHandler().getObjectMiningDef(rock.getID());
		final int axeId = getAxe(player);
		int repeat = 1;
		final int mineLvl = player.getSkills().getLevel(Skill.MINING.id());
		final int mineXP = player.getSkills().getExperience(Skill.MINING.id());
		int reqlvl = getPickaxeRequiredLevel(axeId);

		if (player.click == 1) {
			player.playSound("prospect");
			player.playerServerMessage(MessageType.QUEST, "You examine the rock for ores...");
			delay(3);
			if (rock.getID() == 496) {
				// Tutorial Island rock handler
				mes("This rock contains " + new Item(def.getOreId()).getDef(player.getWorld()).getName(),
						"Sometimes you won't find the ore but trying again may find it",
						"If a rock contains a high level ore",
						"You will not find it until you increase your mining level");
				if (player.getCache().hasKey("tutorial") && player.getCache().getInt("tutorial") == 49)
					player.getCache().set("tutorial", 50);
			} else {
				if (def == null || def.getRespawnTime() < 1) {
					player.playerServerMessage(MessageType.QUEST, "You fail to find anything interesting");
				}
				// Before the fatigue system (13 November 2002) it was possible to fail prospecting
				// which could happen based on "some chance" when the player had the level to mine the rock
				// and always failed when the player did not meet the level to mine the rock
				// here we set it as config option
				else if (player.getConfig().CAN_PROSPECT_FAIL
					&& (DataConversions.random(0, 3) != 1 || reqlvl > mineLvl)) {
					player.playerServerMessage(MessageType.QUEST, "You fail to find any ore in the rock");
				} else {
					player.playerServerMessage(MessageType.QUEST, "This rock contains " + new Item(def.getOreId()).getDef(player.getWorld()).getName());
				}
			}
			return;
		}

		if (axeId < 0 || reqlvl > mineLvl) {
			mes("You need a pickaxe to mine this rock");
			delay(3);
			mes("You do not have a pickaxe which you have the mining level to use");
			delay(3);
			return;
		}
		if (player.click == 0 && (def == null || (def.getRespawnTime() < 1 && rock.getID() != 496) || (def.getOreId() == 315 && player.getQuestStage(Quests.FAMILY_CREST) < 6))) {
			player.playSound("mine");
			int pickBubbleId = player.getClientLimitations().supportsTypedPickaxes ? ItemId.IRON_PICKAXE.id() : ItemId.BRONZE_PICKAXE.id();
			ActionSender.sendActionProgressBar(player, pickBubbleId, 3); // authentic to only show the original pickaxe sprite
			player.playerServerMessage(MessageType.QUEST, "You swing your pick at the rock...");
			delay(3);
			player.playerServerMessage(MessageType.QUEST, "There is currently no ore available in this rock");
			return;
		}
		if (config().STOP_SKILLING_FATIGUED >= 1
			&& player.getFatigue() >= player.MAX_FATIGUE) {
			// authentically on fatigued, shows pickaxe that would have been used
			thinkbubble(new Item(axeId));
			player.playerServerMessage(MessageType.QUEST, "You are too tired to mine this rock");
			return;
		}
		if (rock.getID() == 496 && mineXP >= 210) {
			player.message("Thats enough mining for now");
			return;
		}

		startbatch(repeat);
		batchMining(player, rock, def, axeId, mineLvl);
	}

	private void batchMining(Player player, GameObject rock, ObjectMiningDef def, int axeId, int mineLvl) {
		player.playSound("mine");
		int pickBubbleId = player.getClientLimitations().supportsTypedPickaxes ? ItemId.IRON_PICKAXE.id() : ItemId.BRONZE_PICKAXE.id();
		ActionSender.sendActionProgressBar(player, pickBubbleId, 3); // authentic to only show the original pickaxe sprite
		player.playerServerMessage(MessageType.QUEST, "You swing your pick at the rock...");
		delay(3);
		if (ifinterrupted() || !player.withinRange(rock, 1)) {
			return;
		}

		final Item ore = new Item(def.getOreId());
		if (config().WANT_FATIGUE) {
			if (config().STOP_SKILLING_FATIGUED >= 1
				&& player.getFatigue() >= player.MAX_FATIGUE) {
				// authentically on fatigued, shows pickaxe that would have been used
				thinkbubble(new Item(axeId));
				player.playerServerMessage(MessageType.QUEST, "You are too tired to mine this rock");
				return;
			}
		}
		if (mineLvl >= def.getReqLevel()) {
			GameObject obj = player.getViewArea().getGameObject(rock.getID(), rock.getX(), rock.getY());
			if (!player.getConfig().SHARED_GATHERING_RESOURCES || obj != null) {
				// Successful mining attempt
				// It is authentic to allow multiple players to get the rock if they have already started mining it.
				// In retro mechanic, if other player had depleted it you would not get it
				// In both cases if there is no ore in the rock, there will be no retry
				int quantity = Formulae.calcGatheringYield(def.getReqLevel(), mineLvl, getPickaxeTier(axeId));

				if (SkillCapes.shouldActivate(player, ItemId.MINING_CAPE)) {
					thinkbubble(new Item(ItemId.MINING_CAPE.id(), 1));
					quantity *= 2;
				}
				if (maybeAwardMyWorldMiningGeode(player, rock, def.getReqLevel())) {
					player.incExp(Skill.MINING.id(), def.getExp() * quantity, true);
				} else {
					int rewardQuantity = quantity + addGatheringAmuletBonusOre(player, ore.getCatalogId(), quantity);
					int bankedQuantity = player.getCarriedItems().getEquipment().bankSkillingDropWithLawRing(new Item(ore.getCatalogId(), rewardQuantity));
					int remainingQuantity = rewardQuantity - bankedQuantity;
					int storedQuantity = Math.min(remainingQuantity, player.getCarriedItems().getInventory().getFreeSlots());
					int successfulQuantity = bankedQuantity + storedQuantity;
					if (successfulQuantity > 1) {
						player.playerServerMessage(MessageType.QUEST, "You manage to obtain " + successfulQuantity + " " + ore.getDef(player.getWorld()).getName().toLowerCase());
					} else if (successfulQuantity == 1) {
						player.playerServerMessage(MessageType.QUEST, "You manage to obtain some " + ore.getDef(player.getWorld()).getName().toLowerCase());
					} else {
						player.playerServerMessage(MessageType.QUEST, "You manage to obtain some " + ore.getDef(player.getWorld()).getName().toLowerCase() + ", but have no room to keep it");
					}
					if (storedQuantity > 0) {
						give(player, ore.getCatalogId(), storedQuantity);
					}
					int overflowQuantity = remainingQuantity - storedQuantity;
					if (overflowQuantity > 0) {
						dropOverflow(player, rock, ore.getCatalogId(), overflowQuantity);
						player.playerServerMessage(MessageType.QUEST, "Any excess falls to the ground because you have no room");
					}
					player.incExp(Skill.MINING.id(), def.getExp() * quantity, true);
				}
			} else {
				player.playerServerMessage(MessageType.QUEST, "You only succeed in scratching the rock");
			}
			if (rock.getID() == 496 && player.getCache().hasKey("tutorial") && player.getCache().getInt("tutorial") == 51) {
				player.getCache().set("tutorial", 52);
			}
			if (def.getRespawnTime() > 0) {
				changeloc(rock, resourceRespawnMillis(def.getRespawnTime()), SceneryId.ROCK_GENERIC.id());
			}
			return;
		} else {
			if (rock.getID() == 496) {
				player.playerServerMessage(MessageType.QUEST, "You fail to make any real impact on the rock");
			} else {
				player.playerServerMessage(MessageType.QUEST, "You only succeed in scratching the rock");
				if (!isbatchcomplete()) {
					GameObject checkObj = player.getViewArea().getGameObject(rock.getID(), rock.getX(), rock.getY());
					if (checkObj == null) {
						return;
					}
				}
			}
		}

		GameObject obj = player.getViewArea().getGameObject(rock.getID(), rock.getX(), rock.getY());
		if(obj == null) {
			// There is no more ore in the rock, end batch
			stopbatch();
			return;
		}

		// Repeat
		updatebatch();
		boolean customBatch = config().BATCH_PROGRESSION;
		if (!isbatchcomplete()) {
			if (!customBatch || !ifinterrupted()) {
				batchMining(player, rock, def, axeId, mineLvl);
			}
		}
	}

	private boolean isTemporaryDepletedOreRock(GameObject rock) {
		int id = rock.getID();
		if (id != SceneryId.ROCK_GENERIC.id() && id != SceneryId.ROCK_GENERIC2.id()) {
			return false;
		}
		int permanentId = rock.getLoc().getPermId();
		return permanentId > 0
			&& permanentId != id
			&& permanentId != SceneryId.ROCK_GENERIC.id()
			&& permanentId != SceneryId.ROCK_GENERIC2.id();
	}

	private void handleDepletedOreRock(final GameObject rock, Player player, int click) {
		if (!player.withinRange(rock, 1)) {
			return;
		}

		if (click == 1) {
			player.playSound("prospect");
			player.playerServerMessage(MessageType.QUEST, "You examine the rock for ores...");
			delay(3);
			player.playerServerMessage(MessageType.QUEST, "There is currently no ore available in this rock");
			return;
		}

		final int axeId = getAxe(player);
		final int mineLvl = player.getSkills().getLevel(Skill.MINING.id());
		int reqlvl = getPickaxeRequiredLevel(axeId);
		if (axeId < 0 || reqlvl > mineLvl) {
			mes("You need a pickaxe to mine this rock");
			delay(3);
			mes("You do not have a pickaxe which you have the mining level to use");
			delay(3);
			return;
		}
		if (config().STOP_SKILLING_FATIGUED >= 1
			&& player.getFatigue() >= player.MAX_FATIGUE) {
			thinkbubble(new Item(axeId));
			player.playerServerMessage(MessageType.QUEST, "You are too tired to mine this rock");
			return;
		}

		player.playSound("mine");
		int pickBubbleId = player.getClientLimitations().supportsTypedPickaxes ? ItemId.IRON_PICKAXE.id() : ItemId.BRONZE_PICKAXE.id();
		ActionSender.sendActionProgressBar(player, pickBubbleId, 3);
		player.playerServerMessage(MessageType.QUEST, "You swing your pick at the rock...");
		delay(3);
		player.playerServerMessage(MessageType.QUEST, "There is currently no ore available in this rock");
	}

	private int resourceRespawnMillis(int respawnSeconds) {
		return Math.max(1, (respawnSeconds * 1000) / 2);
	}

	private void handleGemRockMining(final GameObject rock, Player player, int click) {
		if (!player.withinRange(rock, 1)) {
			return;
		}

		final int axeId = getAxe(player);
		final int mineLvl = player.getSkills().getLevel(Skill.MINING.id());
		int reqlvl = getPickaxeRequiredLevel(axeId);

		if (click == 1) {
			player.playSound("prospect");
			player.playerServerMessage(MessageType.QUEST, "You examine the rock for ores...");
			delay(3);
			player.playerServerMessage(MessageType.QUEST, "This rock contains gems");
			return;
		}

		if (axeId < 0 || reqlvl > mineLvl) {
			mes("You need a pickaxe to mine this rock");
			delay(3);
			mes("You do not have a pickaxe which you have the mining level to use");
			delay(3);
			return;
		}
		if (mineLvl < GEM_ROCK_REQ_LEVEL) {
			player.playerServerMessage(MessageType.QUEST, "You need a mining level of " + GEM_ROCK_REQ_LEVEL + " to mine this rock");
			return;
		}
		if (config().STOP_SKILLING_FATIGUED >= 1
			&& player.getFatigue() >= player.MAX_FATIGUE) {
			thinkbubble(new Item(axeId));
			player.playerServerMessage(MessageType.QUEST, "You are too tired to mine this rock");
			return;
		}
		startbatch(30);
		batchGemRockMining(player, rock, axeId);
	}

	private void batchGemRockMining(Player player, GameObject rock, int axeId) {
		if (config().STOP_SKILLING_FATIGUED >= 1
			&& player.getFatigue() >= player.MAX_FATIGUE) {
			thinkbubble(new Item(axeId));
			player.playerServerMessage(MessageType.QUEST, "You are too tired to mine this rock");
			return;
		}
		player.playSound("mine");
		int pickBubbleId = player.getClientLimitations().supportsTypedPickaxes ? ItemId.IRON_PICKAXE.id() : ItemId.BRONZE_PICKAXE.id();
		ActionSender.sendActionProgressBar(player, pickBubbleId, 3);
		player.playerServerMessage(MessageType.QUEST, "You swing your pick at the rock...");
		delay(3);
		if (ifinterrupted() || !player.withinRange(rock, 1)) {
			return;
		}

		int quantity = Formulae.calcGatheringYield(GEM_ROCK_REQ_LEVEL, player.getSkills().getLevel(Skill.MINING.id()), getPickaxeTier(axeId));
		if (SkillCapes.shouldActivate(player, ItemId.MINING_CAPE)) {
			thinkbubble(new Item(ItemId.MINING_CAPE.id(), 1));
			quantity *= 2;
		}
		for (int i = 0; i < quantity; i++) {
			awardGemRockGem(player, rock);
		}
		player.incExp(Skill.MINING.id(), GEM_ROCK_EXP * quantity, true);
		changeloc(rock, resourceRespawnMillis(GEM_ROCK_RESPAWN_SECONDS), SceneryId.ROCK_GENERIC.id());
	}

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return (command.equalsIgnoreCase("mine") || command.equalsIgnoreCase("prospect"))
			&& obj.getID() != 1227;
	}

	/**
	 * Returns a gem ID
	 */
	public int getGem() {
		int rand = DataConversions.random(0, 100);
		if (rand < 10) {
			return ItemId.UNCUT_DIAMOND.id();
		} else if (rand < 30) {
			return ItemId.UNCUT_RUBY.id();
		} else if (rand < 60) {
			return ItemId.UNCUT_EMERALD.id();
		} else {
			return ItemId.UNCUT_SAPPHIRE.id();
		}
	}

	private int getGemRockGem() {
		return Formulae.weightedRandomChoice(GEM_ROCK_GEM_IDS, GEM_ROCK_GEM_WEIGHTS);
	}

	private void awardGemRockGem(Player player, GameObject rock) {
		Item gem = new Item(getGemRockGem(), 1);
		if (player.getCarriedItems().getEquipment().bankSkillingDropWithLawRing(gem) > 0) {
			player.playerServerMessage(MessageType.QUEST, minedGemString(gem.getCatalogId()));
			maybeDoubleRareGatheringReward(player, gem, rock, "Your cosmic Bangel glimmers and another gem appears.");
		} else if (!player.getCarriedItems().getInventory().full()) {
			player.getCarriedItems().getInventory().add(gem);
			player.playerServerMessage(MessageType.QUEST, minedGemString(gem.getCatalogId()));
			maybeDoubleRareGatheringReward(player, gem, rock, "Your cosmic Bangel glimmers and another gem appears.");
		} else {
			player.getWorld().registerItem(new GroundItem(player.getWorld(), gem.getCatalogId(), player.getX(), player.getY(), 1, player));
			player.playerServerMessage(MessageType.QUEST, "You mine a gem, but have no room to keep it, so it falls to the ground");
		}
	}

	private String minedGemString(int gemID) {
		if (gemID == ItemId.UNCUT_OPAL.id()) {
			return "You just mined an Opal!";
		} else if (gemID == ItemId.UNCUT_JADE.id()) {
			return "You just mined a piece of Jade!";
		} else if (gemID == ItemId.UNCUT_RED_TOPAZ.id()) {
			return "You just mined a Red Topaz!";
		} else if (gemID == ItemId.UNCUT_SAPPHIRE.id()) {
			return "You just found a sapphire!";
		} else if (gemID == ItemId.UNCUT_EMERALD.id()) {
			return "You just found an emerald!";
		} else if (gemID == ItemId.UNCUT_RUBY.id()) {
			return "You just found a ruby!";
		} else if (gemID == ItemId.UNCUT_DIAMOND.id()) {
			return "You just found a diamond!";
		}
		return "You just mined a gem!";
	}

	private int calcAxeBonus(int axeId) {
		//If server doesn't use batching, pickaxe shouldn't improve gathering chance
		if (!config().BATCH_PROGRESSION) {
			return 0;
		}
		switch (ItemId.getById(axeId)) {
			case BRONZE_PICKAXE:
				return 2;
			case IRON_PICKAXE:
				return 4;
			case STEEL_PICKAXE:
				return 8;
			case MITHRIL_PICKAXE:
				return 16;
			case TITAN_STEEL_PICKAXE:
				return 24;
			case ADAMANTITE_PICKAXE:
				return 32;
			case ORICHALCUM_PICKAXE:
				return 40;
			case RUNE_PICKAXE:
				return 48;
			case COPPER_PICKAXE:
				return 1;
			case TIN_PICKAXE:
			default:
				return 0;
		}
	}

	/**
	 * Should we can get an ore from the rock?
	 */
	private boolean getOre(ObjectMiningDef def, int miningLevel, int axeId) {
		return Formulae.calcGatheringSuccessfulLegacy(def.getReqLevel(), miningLevel, calcAxeBonus(axeId));
	}

	private void dropOverflow(Player player, GameObject object, int itemId, int amount) {
		if (amount <= 0) {
			return;
		}
		if (new Item(itemId).getDef(player.getWorld()).isStackable()) {
			player.getWorld().registerItem(new GroundItem(player.getWorld(), itemId, player.getX(), player.getY(), amount, player));
			return;
		}
		for (int i = 0; i < amount; i++) {
			player.getWorld().registerItem(new GroundItem(player.getWorld(), itemId, player.getX(), player.getY(), 1, player));
		}
	}

	private int addGatheringAmuletBonusOre(Player player, int oreId, int oreCount) {
		final int bonusOre = EnchantingItemEffects.consumeGatheringAmuletBonusItems(player,
			Skill.MINING.id(), oreId, oreCount);
		if (bonusOre > 0) {
			player.playerServerMessage(MessageType.QUEST, "@gre@Your miner's Bangel produces "
				+ bonusOre + " extra ore" + (bonusOre == 1 ? "." : "s."));
		}
		return bonusOre;
	}

	private boolean maybeAwardMyWorldMiningGeode(Player player, GameObject rock, int nodeRequiredLevel) {
		double geodeChance = getRandomGeodeChance(player);
		if (geodeChance <= 0.0D || DataConversions.getRandom().nextDouble() >= geodeChance) {
			return false;
		}
		GeodeReward reward = rollMyWorldMiningGeode(nodeRequiredLevel);
		if (reward == null) {
			return false;
		}
		player.playSound("foundgem");
		Item geode = new Item(reward.itemId, 1);
		String geodeName = geode.getDef(player.getWorld()).getName().toLowerCase();
		if (player.getCarriedItems().getEquipment().bankSkillingDropWithLawRing(geode) > 0) {
			player.playerServerMessage(MessageType.QUEST, "You find a " + geodeName + ".");
			maybeDoubleRareGatheringReward(player, geode, rock, "Your cosmic Bangel glimmers and another geode appears.");
		} else if (!player.getCarriedItems().getInventory().full()) {
			player.getCarriedItems().getInventory().add(geode);
			player.playerServerMessage(MessageType.QUEST, "You find a " + geodeName + ".");
			maybeDoubleRareGatheringReward(player, geode, rock, "Your cosmic Bangel glimmers and another geode appears.");
		} else {
			player.getWorld().registerItem(new GroundItem(player.getWorld(), geode.getCatalogId(), player.getX(), player.getY(), 1, player));
			player.playerServerMessage(MessageType.QUEST, "You find a geode, but have no room to keep it, so it falls to the ground");
		}
		return true;
	}

	private GeodeReward rollMyWorldMiningGeode(int nodeRequiredLevel) {
		int[] weights = getGeodeSizeWeights(nodeRequiredLevel);
		int roll = DataConversions.random(1, 100);
		for (int i = 0; i < MYWORLD_MINING_GEODE_REWARDS.length; i++) {
			roll -= weights[i];
			if (roll <= 0) {
				return MYWORLD_MINING_GEODE_REWARDS[i];
			}
		}
		return MYWORLD_MINING_GEODE_REWARDS[0];
	}

	private static int[] getGeodeSizeWeights(int nodeRequiredLevel) {
		int level = Math.max(1, Math.min(99, nodeRequiredLevel));
		int progress = level - 1;
		int hugeWeight = 1 + (progress * 19 / 98);
		int largeWeight = 9 + (progress * 21 / 98);
		int standardWeight = 20 + (progress * 5 / 98);
		int smallWeight = 100 - standardWeight - largeWeight - hugeWeight;
		return new int[] {smallWeight, standardWeight, largeWeight, hugeWeight};
	}

	private void maybeDoubleRareGatheringReward(Player player, Item item, GameObject object, String message) {
		double chance = player.getCarriedItems().getEquipment().getCosmicAmuletRareGatheringDoubleChance();
		if (chance <= 0.0D || DataConversions.getRandom().nextDouble() >= chance) {
			return;
		}
		Item extra = new Item(item.getCatalogId(), 1);
		if (player.getCarriedItems().getEquipment().bankSkillingDropWithLawRing(extra) <= 0) {
			if (player.getCarriedItems().getInventory().full()) {
				player.getWorld().registerItem(new GroundItem(player.getWorld(), item.getCatalogId(), player.getX(), player.getY(), 1, player));
			} else {
				player.getCarriedItems().getInventory().add(extra);
			}
		}
		player.playerServerMessage(MessageType.QUEST, message);
	}

	@Override
	public void onUseLoc(Player player, GameObject object, Item item) {
		final GameObjectDef def = player.getWorld().getServer().getEntityHandler().getGameObjectDef(object.getID());
		if (inArray(item.getCatalogId(), Formulae.miningAxeIDs) && (player.getConfig().GATHER_TOOL_ON_SCENERY || !player.getClientLimitations().supportsClickMine)
			&& def != null && def.command1.equalsIgnoreCase("mine") && object.getID() != 1227) {
			player.click = 0;
			handleMiningEntry(player, object, "mine");
		}
	}

	@Override
	public boolean blockUseLoc(Player player, GameObject obj, Item item) {
		final GameObjectDef def = player.getWorld().getServer().getEntityHandler().getGameObjectDef(obj.getID());
		return (inArray(item.getCatalogId(), Formulae.miningAxeIDs) && (player.getConfig().GATHER_TOOL_ON_SCENERY || !player.getClientLimitations().supportsClickMine)
			&& def != null && def.command1.equalsIgnoreCase("mine") && obj.getID() != 1227);
	}
}
