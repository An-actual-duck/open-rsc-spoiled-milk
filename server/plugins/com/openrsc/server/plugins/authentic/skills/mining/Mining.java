package com.openrsc.server.plugins.authentic.skills.mining;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Skills;
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

	public static final int MINING_FOCUS_NO_GEMS = Skills.CONTROLLED_MODE;
	public static final int MINING_FOCUS_SOME_GEMS = Skills.AGGRESSIVE_MODE;
	public static final int MINING_FOCUS_MORE_GEMS = Skills.ACCURATE_MODE;
	public static final int MINING_FOCUS_MOST_GEMS = Skills.DEFENSIVE_MODE;
	private static final double MYWORLD_GEM_REWARD_BASE_CHANCE = 1.0D / 50.0D;

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
			case MINING_FOCUS_NO_GEMS:
				return "Just the ore";
			case MINING_FOCUS_SOME_GEMS:
				return "A few gems";
			case MINING_FOCUS_MORE_GEMS:
				return "Plenty of gems";
			case MINING_FOCUS_MOST_GEMS:
				return "Lots of gems";
			default:
				return "A few gems";
		}
	}

	private static int getMiningFocus(Player player) {
		return player.getCombatStyle();
	}

	private static double getRandomGemChance(Player player) {
		double baseChance = MYWORLD_GEM_REWARD_BASE_CHANCE * player.getCarriedItems().getEquipment().getCosmicAmuletGemChanceMultiplier();
		switch (getMiningFocus(player)) {
			case MINING_FOCUS_NO_GEMS:
				return 0.0D;
			case MINING_FOCUS_SOME_GEMS:
				return baseChance;
			case MINING_FOCUS_MORE_GEMS:
				return baseChance * 1.5D;
			case MINING_FOCUS_MOST_GEMS:
				return baseChance * 2.0D;
			default:
				return baseChance;
		}
	}

	@Override
	public void onOpLoc(Player player, final GameObject object, String command) {
		if ((command.equals("mine") || command.equals("prospect"))
			&& object.getID() != 588 && object.getID() != 1227) {
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

		if (rock.getID() == SceneryId.ROCK_GENERIC.id() || rock.getID() == SceneryId.ROCK_GENERIC2.id()) {
			handleStoneMining(rock, player, click);
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
				double gemChance = getRandomGemChance(player);
				double gemRoll = DataConversions.getRandom().nextDouble();
				boolean gemProc = gemChance > 0.0D && gemRoll < gemChance;
				if (gemProc) {
				player.playSound("foundgem");
				Item gem = new Item(getGem(), 1);
				if (player.getCarriedItems().getEquipment().bankSkillingDropWithLawRing(gem) > 0) {
					player.playerServerMessage(MessageType.QUEST, "You just found a" + gem.getDef(player.getWorld()).getName().toLowerCase().replaceAll("uncut", "") + "!");
					maybeDoubleRareGatheringReward(player, gem, rock, "Your cosmic amulet glimmers and another gem appears.");
				} else if (!player.getCarriedItems().getInventory().full()) {
					player.getCarriedItems().getInventory().add(gem);
					player.playerServerMessage(MessageType.QUEST, "You just found a" + gem.getDef(player.getWorld()).getName().toLowerCase().replaceAll("uncut", "") + "!");
					maybeDoubleRareGatheringReward(player, gem, rock, "Your cosmic amulet glimmers and another gem appears.");
				} else {
					player.getWorld().registerItem(new GroundItem(player.getWorld(), gem.getCatalogId(), rock.getX(), rock.getY(), 1, player));
					player.playerServerMessage(MessageType.QUEST, "You find a gem, but have no room to keep it, so it falls to the ground");
				}
			}
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
					int bankedQuantity = player.getCarriedItems().getEquipment().bankSkillingDropWithLawRing(new Item(ore.getCatalogId(), quantity));
					int remainingQuantity = quantity - bankedQuantity;
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

					if (player.getCarriedItems().getEquipment().getCosmicAmuletExtraResourceChance() > 0.0D
						&& DataConversions.getRandom().nextDouble() < player.getCarriedItems().getEquipment().getCosmicAmuletExtraResourceChance()
						&& !player.getCarriedItems().getInventory().full()) {
						give(player, ore.getCatalogId(), 1);
						player.playerServerMessage(MessageType.QUEST, "Your amulet resonates and you pull out extra ore.");
					}
				} else {
					player.playerServerMessage(MessageType.QUEST, "You only succeed in scratching the rock");
				}
				if (rock.getID() == 496 && player.getCache().hasKey("tutorial") && player.getCache().getInt("tutorial") == 51) {
					player.getCache().set("tutorial", 52);
				}
				if (def.getRespawnTime() > 0) {
					changeloc(rock, def.getRespawnTime() * 1000, SceneryId.ROCK_GENERIC.id());
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

	private void handleStoneMining(final GameObject rock, Player player, int click) {
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
			player.playerServerMessage(MessageType.QUEST, "This rock contains stone");
			return;
		}

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
		startbatch(1);
		batchStoneMining(player, rock, axeId);
	}

	private void batchStoneMining(Player player, GameObject rock, int axeId) {
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
		int quantity = Formulae.calcGatheringYield(1, player.getSkills().getLevel(Skill.MINING.id()), getPickaxeTier(axeId));
		int bankedQuantity = player.getCarriedItems().getEquipment().bankSkillingDropWithLawRing(new Item(ItemId.RUNE_STONE.id(), quantity));
		int remainingQuantity = quantity - bankedQuantity;
		int storedQuantity = Math.min(remainingQuantity, player.getCarriedItems().getInventory().getFreeSlots());
		if (storedQuantity > 0) {
			give(player, ItemId.RUNE_STONE.id(), storedQuantity);
		}
		int overflowQuantity = remainingQuantity - storedQuantity;
		if (overflowQuantity > 0) {
			dropOverflow(player, rock, ItemId.RUNE_STONE.id(), overflowQuantity);
		}
		int successfulQuantity = bankedQuantity + storedQuantity;
		player.playerServerMessage(MessageType.QUEST,
			successfulQuantity > 1 ? "You manage to obtain " + successfulQuantity + " stone"
				: successfulQuantity == 1 ? "You manage to obtain some stone"
				: "You manage to obtain some stone, but have no room to keep it");
		if (overflowQuantity > 0) {
			player.playerServerMessage(MessageType.QUEST, "Any excess falls to the ground because you have no room");
		}
		player.incExp(Skill.MINING.id(), 20 * quantity, true);

		updatebatch();
		if (!isbatchcomplete()) {
			if (!config().BATCH_PROGRESSION || !ifinterrupted()) {
				batchStoneMining(player, rock, axeId);
			}
		}
	}

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return (command.equals("mine") || command.equals("prospect"))
			&& obj.getID() != 588 && obj.getID() != 1227;
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
			player.getWorld().registerItem(new GroundItem(player.getWorld(), itemId, object.getX(), object.getY(), amount, player));
			return;
		}
		for (int i = 0; i < amount; i++) {
			player.getWorld().registerItem(new GroundItem(player.getWorld(), itemId, object.getX(), object.getY(), 1, player));
		}
	}

	private void maybeDoubleRareGatheringReward(Player player, Item item, GameObject object, String message) {
		double chance = player.getCarriedItems().getEquipment().getCosmicAmuletRareGatheringDoubleChance();
		if (chance <= 0.0D || DataConversions.getRandom().nextDouble() >= chance) {
			return;
		}
		Item extra = new Item(item.getCatalogId(), 1);
		if (player.getCarriedItems().getEquipment().bankSkillingDropWithLawRing(extra) <= 0) {
			if (player.getCarriedItems().getInventory().full()) {
				player.getWorld().registerItem(new GroundItem(player.getWorld(), item.getCatalogId(), object.getX(), object.getY(), 1, player));
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
			&& def != null && def.command1.equalsIgnoreCase("mine") && object.getID() != 588 && object.getID() != 1227) {
			player.click = 0;
			handleMiningEntry(player, object, "mine");
		}
	}

	@Override
	public boolean blockUseLoc(Player player, GameObject obj, Item item) {
		final GameObjectDef def = player.getWorld().getServer().getEntityHandler().getGameObjectDef(obj.getID());
		return (inArray(item.getCatalogId(), Formulae.miningAxeIDs) && (player.getConfig().GATHER_TOOL_ON_SCENERY || !player.getClientLimitations().supportsClickMine)
			&& def != null && def.command1.equalsIgnoreCase("mine") && obj.getID() != 588 && obj.getID() != 1227);
	}
}
