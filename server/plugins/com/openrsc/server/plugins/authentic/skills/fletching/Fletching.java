package com.openrsc.server.plugins.authentic.skills.fletching;

import com.openrsc.server.ServerConfiguration;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.production.ProductionRecipe;
import com.openrsc.server.content.production.ProductionSession;
import com.openrsc.server.content.production.ProductionStarter;
import com.openrsc.server.content.SkillCapes;
import com.openrsc.server.external.ItemArrowHeadDef;
import com.openrsc.server.external.ItemBowStringDef;
import com.openrsc.server.external.ItemDartTipDef;
import com.openrsc.server.external.ItemLogCutDef;
import com.openrsc.server.model.container.CarriedItems;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.UseInvTrigger;
import com.openrsc.server.util.rsc.DataConversions;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

import static com.openrsc.server.plugins.Functions.*;

public class Fletching implements UseInvTrigger {

	private static final int[] staffIds = {
		ItemId.STAFF.id(),
		ItemId.PINE_STAFF.id(),
		ItemId.OAK_STAFF.id(),
		ItemId.WILLOW_STAFF.id(),
		ItemId.PALM_STAFF.id(),
		ItemId.MAPLE_STAFF.id(),
		ItemId.YEW_STAFF.id(),
		ItemId.EBONY_STAFF.id(),
		ItemId.MAGIC_WOOD_STAFF.id(),
		ItemId.BLOOD_STAFF.id()
	};

	private static final int[] staffCraftingLevels = {1, 8, 15, 22, 30, 38, 46, 54, 62, 70};
	private static final int[] staffCraftingExp = {12, 20, 28, 37, 46, 57, 68, 80, 92, 120};
	private static final int[] fishingRodIds = {
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
	private static final int[] fishingRodCraftingLevels = {1, 8, 15, 22, 30, 38, 46, 54, 62, 70};
	private static final int[] fishingRodCraftingExp = {12, 20, 28, 37, 46, 57, 68, 80, 92, 120};
	private static final int[] crossbowIds = {
		ItemId.CROSSBOW.id(),
		ItemId.PHOENIX_CROSSBOW.id(),
		ItemId.OAK_CROSSBOW.id(),
		ItemId.WILLOW_CROSSBOW.id(),
		ItemId.PALM_CROSSBOW.id(),
		ItemId.MAPLE_CROSSBOW.id(),
		ItemId.YEW_CROSSBOW.id(),
		ItemId.EBONY_CROSSBOW.id(),
		ItemId.MAGIC_CROSSBOW.id(),
		ItemId.BLOOD_CROSSBOW.id()
	};
	private static final int[] crossbowCraftingLevels = {9, 16, 23, 30, 37, 44, 51, 58, 65, 72};
	private static final int[] crossbowCraftingExp = {45, 78, 110, 145, 185, 230, 280, 335, 395, 460};

	private static final int[] attachmentIds = {
		ItemId.ARROW_SHAFTS.id(),
		ItemId.TIN_DART_TIPS.id(),
		ItemId.COPPER_DART_TIPS.id(),
		ItemId.BRONZE_DART_TIPS.id(),
		ItemId.IRON_DART_TIPS.id(),
		ItemId.STEEL_DART_TIPS.id(),
		ItemId.MITHRIL_DART_TIPS.id(),
		ItemId.TITAN_STEEL_DART_TIPS.id(),
		ItemId.ADAMANTITE_DART_TIPS.id(),
		ItemId.ORICHALCUM_DART_TIPS.id(),
		ItemId.RUNE_DART_TIPS.id(),
	};

	private static final int[] unstrungBows = {
		ItemId.UNSTRUNG_SHORTBOW.id(),
		ItemId.UNSTRUNG_LONGBOW.id(),
		ItemId.UNSTRUNG_PINE_SHORTBOW.id(),
		ItemId.UNSTRUNG_PINE_LONGBOW.id(),
		ItemId.UNSTRUNG_OAK_SHORTBOW.id(),
		ItemId.UNSTRUNG_OAK_LONGBOW.id(),
		ItemId.UNSTRUNG_WILLOW_SHORTBOW.id(),
		ItemId.UNSTRUNG_WILLOW_LONGBOW.id(),
		ItemId.UNSTRUNG_PALM_SHORTBOW.id(),
		ItemId.UNSTRUNG_PALM_LONGBOW.id(),
		ItemId.UNSTRUNG_MAPLE_SHORTBOW.id(),
		ItemId.UNSTRUNG_MAPLE_LONGBOW.id(),
		ItemId.UNSTRUNG_YEW_SHORTBOW.id(),
		ItemId.UNSTRUNG_YEW_LONGBOW.id(),
		ItemId.UNSTRUNG_EBONY_SHORTBOW.id(),
		ItemId.UNSTRUNG_EBONY_LONGBOW.id(),
		ItemId.UNSTRUNG_MAGIC_SHORTBOW.id(),
		ItemId.UNSTRUNG_MAGIC_LONGBOW.id(),
		ItemId.UNSTRUNG_BLOOD_SHORTBOW.id(),
		ItemId.UNSTRUNG_BLOOD_LONGBOW.id()
	};

	private static final int[] arrowHeads = {
		ItemId.TIN_ARROW_HEADS.id(),
		ItemId.COPPER_ARROW_HEADS.id(),
		ItemId.BRONZE_ARROW_HEADS.id(),
		ItemId.IRON_ARROW_HEADS.id(),
		ItemId.STEEL_ARROW_HEADS.id(),
		ItemId.MITHRIL_ARROW_HEADS.id(),
		ItemId.TITAN_STEEL_ARROW_HEADS.id(),
		ItemId.ADAMANTITE_ARROW_HEADS.id(),
		ItemId.ORICHALCUM_ARROW_HEADS.id(),
		ItemId.RUNE_ARROW_HEADS.id(),
	};

	private static final int[] logIds = {
		ItemId.LOGS.id(),
		ItemId.PINE_LOGS.id(),
		ItemId.OAK_LOGS.id(),
		ItemId.WILLOW_LOGS.id(),
		ItemId.PALM_LOGS.id(),
		ItemId.MAPLE_LOGS.id(),
		ItemId.YEW_LOGS.id(),
		ItemId.EBONY_LOGS.id(),
		ItemId.MAGIC_LOGS.id(),
		ItemId.BLOOD_LOGS.id()
	};

	@Override
	public boolean blockUseInv(Player player, Integer invIndex, Item item1, Item item2) {
		int item1ID = item1.getCatalogId();
		int item2ID = item2.getCatalogId();

		// Adding feathers to shafts.
		if (item1ID == ItemId.FEATHER.id() && DataConversions.inArray(attachmentIds, item2.getCatalogId())) {
			return true;
		} else if (item2ID == ItemId.FEATHER.id() && DataConversions.inArray(attachmentIds, item1.getCatalogId())) {
			return true;

			// Adding bow strings to unstrung bows.
		} else if (item1ID == ItemId.BOW_STRING.id() && DataConversions.inArray(unstrungBows, item2.getCatalogId())) {
			return true;
		} else if (item2ID == ItemId.BOW_STRING.id() && DataConversions.inArray(unstrungBows, item1.getCatalogId())) {
			return true;

			// Add arrow heads to headless arrows.
		} else if (item1ID == ItemId.HEADLESS_ARROWS.id() && DataConversions.inArray(arrowHeads, item2.getCatalogId())) {
			return true;
		} else if (item2ID == ItemId.HEADLESS_ARROWS.id() && DataConversions.inArray(arrowHeads, item1.getCatalogId())) {
			return true;

			// Use knife on logs.
		} else if (item1ID == ItemId.KNIFE.id() && DataConversions.inArray(logIds, item2.getCatalogId()) && Skill.CRAFTING.id() != Skill.NONE.id()) {
			return true;
		} else if (item2ID == ItemId.KNIFE.id() && DataConversions.inArray(logIds, item1.getCatalogId()) && Skill.CRAFTING.id() != Skill.NONE.id()) {
			return true;

			// Cut oyster pearls.
		} else if (item1ID == ItemId.CHISEL.id() && (item2.getCatalogId() == ItemId.QUEST_OYSTER_PEARLS.id()
			|| item2.getCatalogId() == ItemId.OYSTER_PEARLS.id())) {
			return true;
		} else if (item2ID == ItemId.CHISEL.id() && (item1.getCatalogId() == ItemId.QUEST_OYSTER_PEARLS.id()
			|| item1.getCatalogId() == ItemId.OYSTER_PEARLS.id())) {
			return true;

			// Add oyster pearl bolt tips to bolts.
		} else if (item1ID == ItemId.OYSTER_PEARL_BOLT_TIPS.id() && item2ID == ItemId.CROSSBOW_BOLTS.id()) {
			return true;
		} else if (item2ID == ItemId.OYSTER_PEARL_BOLT_TIPS.id() && item1ID == ItemId.CROSSBOW_BOLTS.id()) {
			return true;
		}

		return false;
	}

	@Override
	public void onUseInv(Player player, Integer invIndex, Item item1, Item item2) {
		int item1ID = item1.getCatalogId();
		int item2ID = item2.getCatalogId();

		// Adding feathers to shafts.
		if (item1ID == ItemId.FEATHER.id() && DataConversions.inArray(attachmentIds, item2.getCatalogId())) {
			attachFeathers(player, item1, item2);
		} else if (item2ID == ItemId.FEATHER.id() && DataConversions.inArray(attachmentIds, item1.getCatalogId())) {
			attachFeathers(player, item2, item1);

			// Adding bow strings to unstrung bows.
		} else if (item1ID == ItemId.BOW_STRING.id() && DataConversions.inArray(unstrungBows, item2.getCatalogId())) {
			doBowString(player, item1, item2);
		} else if (item2ID == ItemId.BOW_STRING.id() && DataConversions.inArray(unstrungBows, item1.getCatalogId())) {
			doBowString(player, item2, item1);

			// Add arrow heads to headless arrows.
		} else if (item1ID == ItemId.HEADLESS_ARROWS.id() && DataConversions.inArray(arrowHeads, item2.getCatalogId())) {
			doArrowHeads(player, item1, item2);
		} else if (item2ID == ItemId.HEADLESS_ARROWS.id() && DataConversions.inArray(arrowHeads, item1.getCatalogId())) {
			doArrowHeads(player, item2, item1);

			// Use knife on logs.
		} else if (item1ID == ItemId.KNIFE.id() && DataConversions.inArray(logIds, item2.getCatalogId()) && Skill.CRAFTING.id() != Skill.NONE.id()) {
			doLogCut(player, item1, item2);
		} else if (item2ID == ItemId.KNIFE.id() && DataConversions.inArray(logIds, item1.getCatalogId()) && Skill.CRAFTING.id() != Skill.NONE.id()) {
			doLogCut(player, item2, item1);

			// Cut oyster pearls.
		} else if (item1ID == ItemId.CHISEL.id() && (item2.getCatalogId() == ItemId.QUEST_OYSTER_PEARLS.id() || item2.getCatalogId() == ItemId.OYSTER_PEARLS.id())) {
			doPearlCut(player, item1, item2);
		} else if (item2ID == ItemId.CHISEL.id() && (item1.getCatalogId() == ItemId.QUEST_OYSTER_PEARLS.id() || item1.getCatalogId() == ItemId.OYSTER_PEARLS.id())) {
			doPearlCut(player, item2, item1);

			// Add oyster pearl bolt tips to bolts.
		} else if (item1ID == ItemId.OYSTER_PEARL_BOLT_TIPS.id() && item2ID == ItemId.CROSSBOW_BOLTS.id()) {
			doBoltMake(player, item2, item1);
		} else if (item2ID == ItemId.OYSTER_PEARL_BOLT_TIPS.id() && item1ID == ItemId.CROSSBOW_BOLTS.id()) {
			doBoltMake(player, item1, item2);
		}
	}

	private void attachFeathers(Player player, final Item feathers, final Item attachment) {
		if (!config().MEMBER_WORLD) {
			player.sendMemberErrorMessage();
			return;
		}

		final int resultID;
		int experience = 4;
		if (attachment.getCatalogId() == ItemId.ARROW_SHAFTS.id()) {
			resultID = ItemId.HEADLESS_ARROWS.id();
		} else {
			ItemDartTipDef dartDef = player.getWorld().getServer().getEntityHandler()
				.getItemDartTipDef(attachment.getCatalogId());
			if (dartDef == null) {
				return;
			}
			if (player.getSkills().getLevel(Skill.CRAFTING.id()) < dartDef.getReqLevel()) {
				player.message("You need a crafting skill of "
					+ dartDef.getReqLevel() + " or above to do that");
				return;
			}
			resultID = dartDef.getDartID();
			experience = dartDef.getExp();
		}
		if (!canReceive(player, new Item(resultID))) {
			player.message("Your client does not support the desired object");
			return;
		}

		int repeat = 1;
		if (config().BATCH_PROGRESSION) {
			repeat = 5;
		}

		startbatch(repeat);
		batchFeathers(player, feathers, attachment, resultID, experience);
	}

	private void batchFeathers(Player player, Item feathers, Item attachment, int resultID, int experience) {
		ServerConfiguration config = config();
		CarriedItems ci = player.getCarriedItems();
		boolean authenticClientUpdates = !config().CUSTOM_IMPROVEMENTS;
		while (!ifinterrupted() && !isbatchcomplete()) {
			feathers = ci.getInventory().get(
				ci.getInventory().getLastIndexById(feathers.getCatalogId(), Optional.of(false))
			);
			attachment = ci.getInventory().get(
				ci.getInventory().getLastIndexById(attachment.getCatalogId(), Optional.of(false))
			);
			if (feathers == null || attachment == null) {
				break;
			}
			int loopAmount = Math.min(10, feathers.getAmount());
			loopAmount = Math.min(loopAmount, attachment.getAmount());
			player.message("You attach feathers to some of your "
				+ attachment.getDef(player.getWorld()).getName());
			int timesLooped = 0;
			for (int i = 0; i < loopAmount; ++i) {
				if (checkFatigue(player)) {
					return;
				}

				ci.remove(new Item(feathers.getCatalogId(), 1), authenticClientUpdates);
				ci.remove(new Item(attachment.getCatalogId(), 1), authenticClientUpdates);
				ci.getInventory().add(new Item(resultID), authenticClientUpdates);
				if (authenticClientUpdates) {
					player.incExp(Skill.CRAFTING.id(), experience, true);
				}
				timesLooped++;
			}
			if (!authenticClientUpdates) {
				ActionSender.sendInventory(player);
				player.incExp(Skill.CRAFTING.id(), experience * timesLooped, true);
			}
			updatebatch();
		}
	}

	private void doArrowHeads(Player player, final Item headlessArrows, final Item arrowHeads) {
		if (!config().MEMBER_WORLD) {
			player.sendMemberErrorMessage();
			return;
		}
		final ItemArrowHeadDef headDef = player.getWorld().getServer().getEntityHandler()
			.getItemArrowHeadDef(arrowHeads.getCatalogId());
		if (headDef == null) {
			return;
		}

		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < headDef.getReqLevel()) {
			player.message("You need a crafting skill of "
				+ headDef.getReqLevel() + " or above to do that");
			return;
		}

		player.message("You attach "
			+ arrowHeads.getDef(player.getWorld()).getName().toLowerCase()
			+ " to some of your arrows");
		int repeat = 1;
		if (config().BATCH_PROGRESSION) {
			repeat = 5;
		}

		startbatch(repeat);
		batchArrowheads(player, headlessArrows, arrowHeads, headDef);
	}

	private void batchArrowheads(Player player, Item headlessArrows, Item arrowHeads, ItemArrowHeadDef headDef) {
		if (!canReceive(player, new Item(headDef.getArrowID()))) {
			player.message("Your client does not support the desired object");
			return;
		}

		int skillCapeMultiplier = SkillCapes.shouldActivate(player, ItemId.CRAFTING_CAPE) ? 2 : 1;
		CarriedItems ci = player.getCarriedItems();
		boolean authenticClientUpdates = !config().CUSTOM_IMPROVEMENTS;
		while (!ifinterrupted() && !isbatchcomplete()) {
			headlessArrows = ci.getInventory().get(
				ci.getInventory().getLastIndexById(headlessArrows.getCatalogId(), Optional.of(false))
			);
			arrowHeads = ci.getInventory().get(
				ci.getInventory().getLastIndexById(arrowHeads.getCatalogId(), Optional.of(false))
			);
			if (headlessArrows == null || arrowHeads == null) {
				break;
			}
			int loopAmount = Math.min(10, headlessArrows.getAmount());
			loopAmount = Math.min(loopAmount, arrowHeads.getAmount());
			int timesLooped = 0;
			for (int i = 0; i < loopAmount; ++i) {
				if (player.getSkills().getLevel(Skill.CRAFTING.id()) < headDef.getReqLevel()) {
					player.message("You need a crafting skill of "
						+ headDef.getReqLevel() + " or above to do that");
					return;
				}
				if (checkFatigue(player)) {
					return;
				}

				ci.remove(new Item(headlessArrows.getCatalogId(), 1), authenticClientUpdates);
				ci.remove(new Item(arrowHeads.getCatalogId(), 1), authenticClientUpdates);
				ci.getInventory().add(new Item(headDef.getArrowID(), skillCapeMultiplier), authenticClientUpdates);

				if (authenticClientUpdates) {
					player.incExp(Skill.CRAFTING.id(), headDef.getExp() * skillCapeMultiplier, true);
				}
				timesLooped++;
			}
			if (!authenticClientUpdates) {
				ActionSender.sendInventory(player);
				player.incExp(Skill.CRAFTING.id(), headDef.getExp() * skillCapeMultiplier * timesLooped, true);
			}
			updatebatch();
		}
	}

	private void doBowString(Player player, final Item bowString, final Item bow) {
		if (!config().MEMBER_WORLD) {
			player.sendMemberErrorMessage();
			return;
		}
		final ItemBowStringDef stringDef = player.getWorld().getServer().getEntityHandler()
			.getItemBowStringDef(bow.getCatalogId());
		if (stringDef == null) {
			return;
		}
		int repeat = 1;
		if (config().BATCH_PROGRESSION) {
			int bowtimes = player.getCarriedItems().getInventory().countId(bow.getCatalogId(), Optional.of(false));
			int stringtimes = player.getCarriedItems().getInventory().countId(bowString.getCatalogId(), Optional.of(false));
			repeat = Math.min(bowtimes, stringtimes);
		}

		startbatch(repeat);
		batchStringing(player, bow, bowString, stringDef);
	}

	private void batchStringing(Player player, Item bow, Item bowString, ItemBowStringDef stringDef) {
		if (!canReceive(player, new Item(stringDef.getBowID()))) {
			player.message("Your client does not support the desired object");
			return;
		}
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < stringDef.getReqLevel()) {
			player.message("You need a crafting skill of "
				+ stringDef.getReqLevel() + " or above to do that");
			return;
		}
		if (checkFatigue(player)) {
			return;
		}
		while (!ifinterrupted() && !isbatchcomplete()) {
			bow = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(bow.getCatalogId(), Optional.of(false))
			);
			bowString = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(bowString.getCatalogId(), Optional.of(false))
			);
			if (bow == null || bowString == null) {
				break;
			}

			player.getCarriedItems().remove(bowString);
			player.getCarriedItems().remove(bow);
			player.message("You add a string to the bow");
			player.getCarriedItems().getInventory().add(new Item(stringDef.getBowID(), 1));
			player.incExp(Skill.CRAFTING.id(), stringDef.getExp(), true);
			updatebatch();
		}
	}

	private void doLogCut(final Player player, final Item knife,
						  final Item log) {
		if (!config().MEMBER_WORLD) {
			player.sendMemberErrorMessage();
			return;
		}
		final ItemLogCutDef cutDef = player.getWorld().getServer().getEntityHandler().getItemLogCutDef(log.getCatalogId());
		if (cutDef == null) {
			return;
		}

		if (!ActionSender.isRetroClient(player)) {
			ProductionSession session = createLogCutProductionSession(player, log, cutDef);
			if (session != null) {
				if (!session.hasAnyCraftableRecipe()) {
					player.message("You are not skilled enough for that yet");
					return;
				}
				player.setAttribute("production_session", session);
				player.setAttribute("production_starter", (ProductionStarter) Fletching::beginProductionFromInterface);
				ActionSender.showProductionInterface(player, session);
				return;
			}
		}

		boolean logConfig = config().MORE_SHAFTS_PER_BETTER_LOG;
		boolean canMakeShafts = logConfig || log.getCatalogId() == ItemId.LOGS.id();

		player.message("What would you like to make?");

		java.util.List<String> options = new java.util.ArrayList<>();
		if (canMakeShafts) {
			options.add("Make arrow shafts");
		}
		options.add("Make shortbow");
		options.add("Make longbow");
		options.add("Make fishing rod");
		options.add("Make staff");

		int type = multi(player, options.toArray(new String[0]));
		if (type < 0 || type >= options.size()) {
			return;
		}

		String selected = options.get(type);
		int reqLvl;
		int exp;
		int id = ItemId.NOTHING.id();
		String cutMessage = null;
		boolean craftingRecipe = true;
		if ("Make arrow shafts".equals(selected)) {
			id = ItemId.ARROW_SHAFTS.id();
			reqLvl = cutDef.getShaftLvl();
			exp = cutDef.getShaftExp();
			cutMessage = "You carefully cut the wood into " + getNumberOfShafts(player, log.getCatalogId())
				+ " arrow shafts";
		} else if ("Make shortbow".equals(selected)) {
			id = cutDef.getShortbowID();
			reqLvl = cutDef.getShortbowLvl();
			exp = cutDef.getShortbowExp();
			cutMessage = "You carefully cut the wood into a shortbow";
		} else if ("Make longbow".equals(selected)) {
			id = cutDef.getLongbowID();
			reqLvl = cutDef.getLongbowLvl();
			exp = cutDef.getLongbowExp();
			cutMessage = "You carefully cut the wood into a longbow";
		} else if ("Make fishing rod".equals(selected)) {
			id = getFishingRodResultId(log.getCatalogId());
			reqLvl = getFishingRodCraftingLevel(log.getCatalogId());
			exp = getFishingRodCraftingExp(log.getCatalogId());
			cutMessage = "You carefully shape the wood into a fishing rod";
		} else {
			id = getStaffResultId(log.getCatalogId());
			reqLvl = getStaffCraftingLevel(log.getCatalogId());
			exp = getStaffCraftingExp(log.getCatalogId());
			cutMessage = "You carefully shape the wood into a staff";
		}

		int repeat = 1;
		if (config().BATCH_PROGRESSION) {
			repeat = player.getCarriedItems().getInventory().countId(log.getCatalogId(), Optional.of(false));
		}

		startbatch(repeat);
		batchLogCutting(player, log, id, reqLvl, exp, cutMessage, craftingRecipe);
	}

	public static boolean beginProductionFromInterface(Player player, ProductionSession session, int itemId, int quantity) {
		if (session == null || !session.isType(ProductionSession.TYPE_CRAFTING)) {
			return false;
		}
		if (quantity < 1) {
			player.message("Choose at least one item to make");
			return false;
		}
		Fletching fletching = new Fletching();
		LogCutRecipe recipe = fletching.getLogCutRecipeByItemId(player, session.getInputItemId(), itemId);
		if (recipe == null) {
			player.message("Nothing interesting happens");
			return false;
		}
		if (!fletching.canStartLogCutRecipe(player, session.getInputItemId(), recipe)) {
			return false;
		}
		int availableLogs = player.getCarriedItems().getInventory().countId(session.getInputItemId(), Optional.of(false));
		int makeCount = Math.min(quantity, availableLogs);
		if (makeCount < 1) {
			player.message("You need more materials to make that");
			return false;
		}
		Item log = new Item(session.getInputItemId());
		startbatch(makeCount);
		fletching.batchLogCutting(player, log, recipe.resultId, recipe.requiredLevel, recipe.exp, recipe.message, recipe.craftingRecipe);
		return true;
	}

	private void batchLogCutting(Player player, Item log, int id, int reqLvl, int exp, String cutMessage, boolean craftingRecipe) {
		if (!canReceive(player, new Item(id))) {
			player.message("Your client does not support the desired object");
			return;
		}
		Skill activeSkill = Skill.CRAFTING;
		String skillName = "crafting";
		if (player.getSkills().getLevel(activeSkill.id()) < reqLvl) {
			player.message("You need a " + skillName + " skill of " + reqLvl + " or above to do that");
			return;
		}
		if (checkFatigue(player)) {
			return;
		}
		while (!ifinterrupted() && !isbatchcomplete()) {
			log = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(log.getCatalogId(), Optional.of(false))
			);
			if (log == null) {
				break;
			}
			if (player.getCarriedItems().remove(log) > -1) {
				player.message(cutMessage);
				give(player, id, id == ItemId.ARROW_SHAFTS.id() ? getNumberOfShafts(player, log.getCatalogId()) : 1);
				player.incExp(activeSkill.id(), exp, true);
			}
			updatebatch();
		}
	}

	private ProductionSession createLogCutProductionSession(final Player player, final Item log, final ItemLogCutDef cutDef) {
		List<ProductionRecipe> recipes = new ArrayList<>();
		for (LogCutRecipe recipe : getLogCutRecipes(player, log.getCatalogId(), cutDef)) {
			Skill activeSkill = Skill.CRAFTING;
			int level = player.getSkills().getLevel(activeSkill.id());
			int materialCount = player.getCarriedItems().getInventory().countId(log.getCatalogId(), Optional.of(false));
			int outputAmount = recipe.resultId == ItemId.ARROW_SHAFTS.id() ? getNumberOfShafts(player, log.getCatalogId()) : 1;
			recipes.add(new ProductionRecipe(recipe.resultId, recipe.requiredLevel, 1, outputAmount,
				level >= recipe.requiredLevel, materialCount >= 1));
		}
		return recipes.isEmpty() ? null
			: new ProductionSession(ProductionSession.TYPE_CRAFTING, "Choose an item to shape", log.getCatalogId(), recipes);
	}

	private LogCutRecipe getLogCutRecipeByItemId(final Player player, final int logId, final int itemId) {
		final ItemLogCutDef cutDef = player.getWorld().getServer().getEntityHandler().getItemLogCutDef(logId);
		if (cutDef == null) {
			return null;
		}
		for (LogCutRecipe recipe : getLogCutRecipes(player, logId, cutDef)) {
			if (recipe.resultId == itemId) {
				return recipe;
			}
		}
		return null;
	}

	private List<LogCutRecipe> getLogCutRecipes(final Player player, final int logId, final ItemLogCutDef cutDef) {
		List<LogCutRecipe> recipes = new ArrayList<>();
		boolean logConfig = player.getConfig().MORE_SHAFTS_PER_BETTER_LOG;
		boolean canMakeShafts = logConfig || logId == ItemId.LOGS.id();
		if (canMakeShafts) {
			recipes.add(new LogCutRecipe(ItemId.ARROW_SHAFTS.id(), cutDef.getShaftLvl(), cutDef.getShaftExp(),
				"You carefully cut the wood into " + getNumberOfShafts(player, logId) + " arrow shafts", true));
		}
		recipes.add(new LogCutRecipe(cutDef.getShortbowID(), cutDef.getShortbowLvl(), cutDef.getShortbowExp(),
			"You carefully cut the wood into a shortbow", true));
		recipes.add(new LogCutRecipe(cutDef.getLongbowID(), cutDef.getLongbowLvl(), cutDef.getLongbowExp(),
			"You carefully cut the wood into a longbow", true));
		recipes.add(new LogCutRecipe(getCrossbowResultId(logId), getCrossbowCraftingLevel(logId), getCrossbowCraftingExp(logId),
			"You carefully shape the wood into a crossbow", true));
		recipes.add(new LogCutRecipe(getFishingRodResultId(logId), getFishingRodCraftingLevel(logId), getFishingRodCraftingExp(logId),
			"You carefully shape the wood into a fishing rod", true));
		recipes.add(new LogCutRecipe(getStaffResultId(logId), getStaffCraftingLevel(logId), getStaffCraftingExp(logId),
			"You carefully shape the wood into a staff", true));
		return recipes;
	}

	private boolean canStartLogCutRecipe(final Player player, final int logId, final LogCutRecipe recipe) {
		if (!canReceive(player, new Item(recipe.resultId))) {
			player.message("Your client does not support the desired object");
			return false;
		}
		Skill activeSkill = Skill.CRAFTING;
		String skillName = "crafting";
		if (player.getSkills().getLevel(activeSkill.id()) < recipe.requiredLevel) {
			player.message("You need a " + skillName + " skill of " + recipe.requiredLevel + " or above to do that");
			return false;
		}
		if (checkFatigue(player)) {
			return false;
		}
		if (player.getCarriedItems().getInventory().countId(logId, Optional.of(false)) < 1) {
			player.message("You need some logs to do that");
			return false;
		}
		return true;
	}

	private static final class LogCutRecipe {
		private final int resultId;
		private final int requiredLevel;
		private final int exp;
		private final String message;
		private final boolean craftingRecipe;

		private LogCutRecipe(int resultId, int requiredLevel, int exp, String message, boolean craftingRecipe) {
			this.resultId = resultId;
			this.requiredLevel = requiredLevel;
			this.exp = exp;
			this.message = message;
			this.craftingRecipe = craftingRecipe;
		}
	}

	private int getStaffResultId(final int logId) {
		for (int index = 0; index < logIds.length; index++) {
			if (logIds[index] == logId) {
				return staffIds[index];
			}
		}
		return ItemId.STAFF.id();
	}

	private int getCrossbowResultId(final int logId) {
		for (int index = 0; index < logIds.length; index++) {
			if (logIds[index] == logId) {
				return crossbowIds[index];
			}
		}
		return ItemId.CROSSBOW.id();
	}

	private int getFishingRodResultId(final int logId) {
		for (int index = 0; index < logIds.length; index++) {
			if (logIds[index] == logId) {
				return fishingRodIds[index];
			}
		}
		return ItemId.FISHING_ROD.id();
	}

	private int getCrossbowCraftingLevel(final int logId) {
		for (int index = 0; index < logIds.length; index++) {
			if (logIds[index] == logId) {
				return crossbowCraftingLevels[index];
			}
		}
		return crossbowCraftingLevels[0];
	}

	private int getCrossbowCraftingExp(final int logId) {
		for (int index = 0; index < logIds.length; index++) {
			if (logIds[index] == logId) {
				return crossbowCraftingExp[index];
			}
		}
		return crossbowCraftingExp[0];
	}

	private int getFishingRodCraftingLevel(final int logId) {
		for (int index = 0; index < logIds.length; index++) {
			if (logIds[index] == logId) {
				return fishingRodCraftingLevels[index];
			}
		}
		return fishingRodCraftingLevels[0];
	}

	private int getFishingRodCraftingExp(final int logId) {
		for (int index = 0; index < logIds.length; index++) {
			if (logIds[index] == logId) {
				return fishingRodCraftingExp[index];
			}
		}
		return fishingRodCraftingExp[0];
	}

	private int getStaffCraftingLevel(final int logId) {
		for (int index = 0; index < logIds.length; index++) {
			if (logIds[index] == logId) {
				return staffCraftingLevels[index];
			}
		}
		return staffCraftingLevels[0];
	}

	private int getStaffCraftingExp(final int logId) {
		for (int index = 0; index < logIds.length; index++) {
			if (logIds[index] == logId) {
				return staffCraftingExp[index];
			}
		}
		return staffCraftingExp[0];
	}

	private int getNumberOfShafts(final Player player, final int logId) {

		if (!player.getConfig().MORE_SHAFTS_PER_BETTER_LOG) return 10;
		for (int i = 0; i < logIds.length; ++i) {
			if (logId == logIds[i]) {
				return 10 + (i * 5);
			}
		}
		return 10;
	}

	private void doPearlCut(final Player player, final Item chisel, final Item pearl) {
		if (!config().MEMBER_WORLD) {
			player.sendMemberErrorMessage();
			return;
		}

		int amount;
		if (pearl.getCatalogId() == ItemId.QUEST_OYSTER_PEARLS.id()) {
			amount = 25;
		} else if (pearl.getCatalogId() == ItemId.OYSTER_PEARLS.id()) {
			amount = 2;
		} else {
			player.message("Nothing interesting happens");
			return;
		}

		final int amt = amount;
		final int exp = 25;
		final int pearlID = pearl.getCatalogId();

		int repeat = 1;
		if (config().BATCH_PROGRESSION) {
			repeat = player.getCarriedItems().getInventory().countId(pearlID, Optional.of(false));
		}

		startbatch(repeat);
		batchPearlCutting(player, pearl, amount);
	}

	private void batchPearlCutting(Player player, Item pearl, int amount) {
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < 34) {
			player.message("You need a crafting skill of 34 to do that");
			return;
		}
		if (checkFatigue(player)) {
			return;
		}

		while (!ifinterrupted() && !isbatchcomplete()) {
			pearl = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(pearl.getCatalogId(), Optional.of(false))
			);
			if (pearl == null) {
				break;
			}

			player.getCarriedItems().remove(new Item(pearl.getCatalogId()));
			player.message("you chisel the pearls into small bolt tips");
			give(player, ItemId.OYSTER_PEARL_BOLT_TIPS.id(), amount);
			player.incExp(Skill.CRAFTING.id(), 100, true);
			updatebatch();
		}
	}

	private void doBoltMake(final Player player, final Item bolts, final Item tips) {
		if (!config().MEMBER_WORLD) {
			player.sendMemberErrorMessage();
			return;
		}

		if (tips.getCatalogId() != ItemId.OYSTER_PEARL_BOLT_TIPS.id()) { // not pearl tips
			player.message("Nothing interesting happens");
			return;
		}

		int repeat = 1; // 1 + 1000 for authentic behaviour
		if (config().BATCH_PROGRESSION) {
			repeat = 5;
		}
		startbatch(repeat);
		batchBolts(player, bolts, tips);
	}

	private void batchBolts(Player player, Item bolts, Item tips) {
		ServerConfiguration config = config();
		CarriedItems ci = player.getCarriedItems();
		int skillCapeMultiplier = SkillCapes.shouldActivate(player, ItemId.CRAFTING_CAPE) ? 2 : 1;
		boolean authenticClientUpdates = !config().CUSTOM_IMPROVEMENTS;
		while (!ifinterrupted() && !isbatchcomplete()) {
			bolts = ci.getInventory().get(
				ci.getInventory().getLastIndexById(bolts.getCatalogId(), Optional.of(false))
			);
			tips = ci.getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(tips.getCatalogId(), Optional.of(false))
			);
			if (bolts == null || tips == null) {
				break;
			}
			int loopCount = Math.min(10, bolts.getAmount());
			loopCount = Math.min(loopCount, tips.getAmount());
			int timesLooped = 0;
			for (int i = 0; i < loopCount; ++i) {
				if (player.getSkills().getLevel(Skill.CRAFTING.id()) < 34) {
					player.message("You need a crafting skill of 34 to do that");
					return;
				}
				if (checkFatigue(player)) {
					return;
				}
				ci.remove(new Item(bolts.getCatalogId(), 1), authenticClientUpdates);
				ci.remove(new Item(tips.getCatalogId(), 1), authenticClientUpdates);
				ci.getInventory().add(new Item(ItemId.OYSTER_PEARL_BOLTS.id(), skillCapeMultiplier), authenticClientUpdates);
				if (authenticClientUpdates) {
					player.incExp(Skill.CRAFTING.id(), 25 * skillCapeMultiplier, true);
				}
				timesLooped++;
			}
			if (!authenticClientUpdates) {
				ActionSender.sendInventory(player);
				player.incExp(Skill.CRAFTING.id(), 25 * skillCapeMultiplier * timesLooped, true);
			}
			updatebatch();
		}
	}

	private boolean checkFatigue(Player player) {
		if (player.getConfig().WANT_FATIGUE) {
			if (player.getFatigue() >= player.MAX_FATIGUE) {
				if (player.getConfig().STOP_SKILLING_FATIGUED >= 2) {
					player.message("You are too tired to train");
					return true;
				}
			}
		}
		return false;
	}
}
