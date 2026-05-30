package com.openrsc.server.plugins.authentic.skills.smithing;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.production.ProductionRecipe;
import com.openrsc.server.content.production.ProductionSession;
import com.openrsc.server.content.production.ProductionStarter;
import com.openrsc.server.external.ItemSmithingDef;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.custom.minigames.ABoneToPick;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.plugins.triggers.UseLocTrigger;
import com.openrsc.server.util.rsc.Formulae;
import com.openrsc.server.util.rsc.MathUtil;
import com.openrsc.server.util.rsc.MessageType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.openrsc.server.plugins.Functions.*;

public class Smithing implements UseLocTrigger, OpLocTrigger {

	private final int DORICS_ANVIL = 177;
	private final int ANVIL = 50;
	private final int LAVA_ANVIL = 1285;
	private static final int[] MODERN_ANVIL_BARS = {
		ItemId.TIN_BAR.id(), ItemId.COPPER_BAR.id(), ItemId.BRONZE_BAR.id(),
		ItemId.IRON_BAR.id(), ItemId.STEEL_BAR.id(), ItemId.MITHRIL_BAR.id(),
		ItemId.TITAN_STEEL_BAR.id(), ItemId.ADAMANTITE_BAR.id(),
		ItemId.ORICHALCUM_BAR.id(), ItemId.RUNITE_BAR.id()
	};

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return obj.getID() == ANVIL || obj.getID() == DORICS_ANVIL;
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (obj.getID() != ANVIL && obj.getID() != DORICS_ANVIL) {
			return;
		}
		if (obj.getID() == DORICS_ANVIL && !allowDorics(player)) {
			return;
		}
		if (!player.withinRange(obj, 1)) {
			return;
		}
		if (player.getCarriedItems().getInventory().countId(ItemId.HAMMER.id(), Optional.of(false)) < 1) {
			player.playerServerMessage(MessageType.QUEST, "You need a hammer to work the metal with.");
			return;
		}
		if (ActionSender.isRetroClient(player)) {
			player.message("To forge items use the metal you wish to work with the anvil");
			return;
		}
		ProductionSession session = createSmithingMaterialSession(player);
		player.setAttribute("production_session", session);
		player.setAttribute("production_starter", (ProductionStarter) Smithing::beginMaterialSelectionFromInterface);
		ActionSender.showProductionInterface(player, session);
	}

	@Override
	public boolean blockUseLoc(Player player, GameObject obj, Item item) {
		return obj.getID() == ANVIL
			|| obj.getID() == DORICS_ANVIL
			|| obj.getID() == LAVA_ANVIL;
	}

	@Override
	public void onUseLoc(final Player player, GameObject obj, final Item item) {
		if ((obj.getID() == DORICS_ANVIL || obj.getID() == ANVIL)
			&& item.getCatalogId() == ItemId.ALUMINIUM_BAR.id()) {
			ABoneToPick.makeAluminiumCog(player);
			return;
		}

		if (obj.getID() == LAVA_ANVIL) {
			if (player.getCache().hasKey("miniquest_dwarf_youth_rescue")
			&& player.getCache().getInt("miniquest_dwarf_youth_rescue") == 2) {
				if (item.getCatalogId() == ItemId.DRAGON_BAR.id()) {
					if (!player.getCarriedItems().getInventory().hasInInventory(ItemId.HAMMER.id()))
					{
						player.message("You need a hammer to do that");
						return;
					}
					if (getCurrentLevel(player, Skill.SMITHING.id()) < 90) {
						player.message("You need 90 smithing to work dragon metal");
						return;
					}
					if (player.getCarriedItems().remove(new Item(ItemId.DRAGON_BAR.id())) > -1) {
						give(player, ItemId.DRAGON_METAL_CHAIN.id(), 50);
						player.incExp(Skill.SMITHING.id(), 1000, true);
					}
				} else
					player.message("Nothing interesting happens");
			}
			return;
		}
		// Doric's Anvil
		if (obj.getID() == DORICS_ANVIL && !allowDorics(player)) return;

		if (!smithingChecks(obj, item, player)) return;

		beginSmithing(item, player);
	}

	private boolean allowDorics(Player player) {
		if (player.getQuestStage(Quests.DORICS_QUEST) > -1) {
			Npc doric = ifnearvisnpc(player, NpcId.DORIC.id(), 20);
			if (doric != null) {
				npcsay(player, doric, "Heh who said you could use that?");
			}
			//message likely not given out, see https://classic.runescape.wiki/w/Transcript:Doric?diff=79647&oldid=79230
			//player.message("You need to finish Doric's quest to use this anvil");
			return false;
		}
		return true;
	}

	private boolean smithingChecks(final GameObject obj, final Item item, final Player player) {

		// Not an anvil or Doric's Anvil...
		if (!(obj.getID() == 50 || obj.getID() == 177)) return false;

		if (!player.withinRange(obj, 1)) {
			return false;
		}

		// Using hammer with anvil.
		if (item.getCatalogId() == ItemId.HAMMER.id()) {
			player.message("To forge items use the metal you wish to work with the anvil");
			return false;
		}

		int minSmithingLevel = minSmithingLevel(item.getCatalogId());

		// Special Dragon Square Shield Case
		if (minSmithingLevel < 0 && item.getCatalogId() != ItemId.RIGHT_HALF_DRAGON_SQUARE_SHIELD.id() && item.getCatalogId() != ItemId.LEFT_HALF_DRAGON_SQUARE_SHIELD.id()) {
			player.message("Nothing interesting happens");
			return false;
		}

		int maxItemId = player.getConfig().RESTRICT_ITEM_ID;
		boolean worldSupportsGoldSmithing = !player.getConfig().LACKS_GOLD_SMITHING && MathUtil.maxUnsigned(maxItemId, ItemId.GOLDEN_BOWL.id()) == maxItemId;
		if (item.getCatalogId() == ItemId.GOLD_BAR.id() && !worldSupportsGoldSmithing) {
			player.message("Nothing interesting happens");
			return false;
		}

		if (player.getSkills().getLevel(Skill.SMITHING.id()) < minSmithingLevel) {
			if (item.getCatalogId() != ItemId.GOLD_BAR.id()) {
				player.message("You need at least level "
					+ minSmithingLevel + " smithing to work with "
					+ item.getDef(player.getWorld()).getName().toLowerCase().replaceAll("bar", ""));
			} else {
				// not entirely sure should give message here or this one is once Legends started
				// on OSRS the advice is to try to use furnace
				// Logg tested before legends but with level past 50 and was "You're not quite sure what to make from the gold.."
				player.message("You need at least level 50 smithing to work gold...");
			}
			return false;
		}

		if (player.getCarriedItems().getInventory().countId(ItemId.HAMMER.id(), Optional.of(false)) < 1) {
			player.playerServerMessage(MessageType.QUEST, "You need a hammer to work the metal with.");
			return false;
		}

		return true;
	}

	private void beginSmithing(final Item item, final Player player) {

		// Combining Dragon Square Shield Halves
		if (item.getCatalogId() == ItemId.RIGHT_HALF_DRAGON_SQUARE_SHIELD.id() || item.getCatalogId() == ItemId.LEFT_HALF_DRAGON_SQUARE_SHIELD.id()) {
			attemptDragonSquareCombine(item, player);
			return;
		}

		int maxItemId = player.getConfig().RESTRICT_ITEM_ID;
		boolean worldSupportsGoldSmithing = !player.getConfig().LACKS_GOLD_SMITHING && MathUtil.maxUnsigned(maxItemId, ItemId.GOLDEN_BOWL.id()) == maxItemId;
		// Failure to make a gold bowl without Legend's Quest.
		if (item.getCatalogId() == ItemId.GOLD_BAR.id() && worldSupportsGoldSmithing && player.getQuestStage(Quests.LEGENDS_QUEST) >= 0 && player.getQuestStage(Quests.LEGENDS_QUEST) <= 2) {
			player.message("You're not quite sure what to make from the gold..");
			return;
		}

		// Gold
		if (item.getCatalogId() == ItemId.GOLD_BAR.id()) {
			if (worldSupportsGoldSmithing) {
				player.message("What would you like to make?");
				handleGoldSmithing(player);
			} else {
				player.message("Nothing interesting happens");
			}
			return;
		}

		if (!ActionSender.isRetroClient(player)) {
			ProductionSession session = createSmithingProductionSession(player, item);
			if (session != null) {
				if (!session.hasAnyCraftableRecipe()) {
					player.message("You are not skilled enough for that yet");
					return;
				}
				player.setAttribute("production_session", session);
				player.setAttribute("production_starter", (ProductionStarter) Smithing::beginProductionFromInterface);
				ActionSender.showProductionInterface(player, session);
				return;
			}
		}

		player.message("What would you like to make?");

		handleSmithing(item, player);

	}

	private void attemptDragonSquareCombine(Item item, Player player) {
		if (player.getSkills().getLevel(Skill.SMITHING.id()) < 60) {
			player.message("You need a smithing ability of at least 60 to complete this task.");
		}
		// non-kosher this message
		else if (player.getCarriedItems().getInventory().countId(ItemId.RIGHT_HALF_DRAGON_SQUARE_SHIELD.id(), Optional.of(false)) < 1
				|| player.getCarriedItems().getInventory().countId(ItemId.LEFT_HALF_DRAGON_SQUARE_SHIELD.id(), Optional.of(false)) < 1) {
			player.message("You need the two shield halves to repair the shield.");
		} else {
			mes("You set to work trying to fix the ancient shield.");
			delay(2);
			mes("You hammer long and hard and use all of your skill.");
			delay(2);
			mes("Eventually, it is ready...");
			delay(2);
			mes("You have repaired the Dragon Square Shield.");
			delay(2);
			if (player.getCarriedItems().remove(new Item(ItemId.RIGHT_HALF_DRAGON_SQUARE_SHIELD.id()),
				new Item(ItemId.LEFT_HALF_DRAGON_SQUARE_SHIELD.id()))) {
				player.getCarriedItems().getInventory().add(new Item(ItemId.DRAGON_SQUARE_SHIELD.id()));
				player.incExp(Skill.SMITHING.id(), 300, true);
			}
		}
	}

	private void handleGoldSmithing(Player player) {
		int goldOption = multi(player, "Golden bowl.", "Cancel");

		if (goldOption == 1) return;

		if (!player.getConfig().MEMBER_WORLD) {
			player.message("This feature is members only");
			return;
		}

		if (!canReceive(player, new Item(ItemId.GOLDEN_BOWL.id()))) {
			player.message("Your client does not support the desired object");
			return;
		}

		/*if (player.isBusy()) {
			return;
		}*/
		if (goldOption == 0) {
			if (player.getCarriedItems().getInventory().countId(ItemId.GOLD_BAR.id(), Optional.of(false)) < 2) {
				player.message("You need two bars of gold to make this item.");
				return;
			}

			final int toMake = player.getConfig().BATCH_PROGRESSION ?
				(player.getCarriedItems().getInventory().countId(ItemId.GOLD_BAR.id(), Optional.of(false)) / 2) : 1;
			startbatch(toMake);
			batchGoldSmithing(player);
		}
	}

	private void batchGoldSmithing(final Player player) {
		while (!ifinterrupted() && !isbatchcomplete()) {
			if (player.getCarriedItems().getInventory().countId(ItemId.GOLD_BAR.id(), Optional.of(false)) < 2) {
				player.message("You need two bars of gold to make this item.");
				break;
			}

			mes("You hammer the metal...");
			if (!Formulae.breakGoldenItem(50, player.getSkills().getLevel(Skill.SMITHING.id()))) {
				for (int x = 0; x < 2; x++) {
					player.getCarriedItems().remove(new Item(ItemId.GOLD_BAR.id()));
				}
				player.message("You forge a beautiful bowl made out of solid gold.");
				player.getCarriedItems().getInventory().add(new Item(ItemId.GOLDEN_BOWL.id(), 1));
				player.incExp(Skill.SMITHING.id(), 120, true);
			} else {
				player.message("You make a mistake forging the bowl..");
				player.message("You pour molten gold all over the floor..");
				player.getCarriedItems().remove(new Item(ItemId.GOLD_BAR.id()));
				player.incExp(Skill.SMITHING.id(), 4, true);
			}
			updatebatch();
		}
	}

	private void handleSmithing(final Item item, final Player player) {

		// First Smithing Menu
		int firstType = firstMenu(item, player);
		if (firstType < 0) return;

		if (isModernMetalBar(item.getCatalogId()) && firstType == 1) {
			handleModernMetalArmorSmithing(item, player);
			return;
		}

		/*if (player.isBusy()) {
			return;
		}*/

		// Second Smithing Menu
		int secondType = secondMenu(item, player, firstType);
		if (secondType < 0) return;

		// Distribute to the correct function to make our final choice
		int toMake = chooseItem(player, secondType);

		if (toMake == -1) {
			return;
		}

		if (isModernMetalBar(item.getCatalogId())) {
			final ItemSmithingDef def = getModernWeaponOrMissileRecipe(item.getCatalogId(), toMake);
			if (def == null) {
				player.message("Nothing interesting happens");
				return;
			}
			int makeCount = getCount(def, item, player);
			if (makeCount == -1) return;
			startbatch(makeCount);
			batchSmithing(player, item, def);
			return;
		}

		final ItemSmithingDef def = player.getWorld().getServer().getEntityHandler().getSmithingDef((getBarType(item.getCatalogId()) * 24) + toMake);

		if (def == null) {
			// No definition found
			player.message("Nothing interesting happens");
			return;
		}

		int makeCount = getCount(def, item, player);

		if (makeCount == -1) return;

		startbatch(makeCount);
		batchSmithing(player, item, def);
	}

	private void batchSmithing(Player player, Item item, ItemSmithingDef def) {
		if (!canReceive(player, new Item(def.getItemID()))) {
			player.message("Your client does not support the desired object");
			return;
		}
		if (player.getSkills().getLevel(Skill.SMITHING.id()) < def.getRequiredLevel()) {
			player.message("You need to be at least level "
				+ def.getRequiredLevel() + " smithing to do that");
			return;
		}
		if (player.getCarriedItems().getInventory().countId(item.getCatalogId(), Optional.of(false)) < def.getRequiredBars()) {
			player.message("You need " + def.getRequiredBars() + " bars of metal to make this item");
			return;
		}
		if (player.getConfig().WANT_FATIGUE) {
			if (player.getConfig().STOP_SKILLING_FATIGUED >= 2
				&& player.getFatigue() >= player.MAX_FATIGUE) {
				player.message("You are too tired to smith");
				return;
			}
		}
		while (!ifinterrupted() && !isbatchcomplete()) {
			if (player.getCarriedItems().getInventory().countId(item.getCatalogId(), Optional.of(false)) < def.getRequiredBars()) {
				break;
			}
			player.playSound("anvil");
			for (int x = 0; x < def.getRequiredBars(); x++) {
				player.getCarriedItems().remove(new Item(item.getCatalogId()));
			}

			thinkbubble(item);
			if (player.getWorld().getServer().getEntityHandler().getItemDef(def.getItemID()).isStackable()) {
				player.playerServerMessage(MessageType.QUEST, "You hammer the metal and make " + def.getAmount() + " "
					+ player.getWorld().getServer().getEntityHandler().getItemDef(def.getItemID()).getName().toLowerCase());
				player.getCarriedItems().getInventory().add(
					new Item(def.getItemID(), def.getAmount()));
			} else {
				player.playerServerMessage(MessageType.QUEST, "You hammer the metal and make a "
					+ player.getWorld().getServer().getEntityHandler().getItemDef(def.getItemID()).getName().toLowerCase());
				for (int x = 0; x < def.getAmount(); x++) {
					player.getCarriedItems().getInventory().add(new Item(def.getItemID(), 1));
				}
			}
			player.incExp(Skill.SMITHING.id(), getSmithingExp(item.getCatalogId(), def.getRequiredBars()), true);
			updatebatch();
		}
	}

	public static boolean beginProductionFromInterface(Player player, ProductionSession session, int itemId, int quantity) {
		if (session == null || !session.isType(ProductionSession.TYPE_SMITHING)) {
			return false;
		}
		if (quantity < 1) {
			player.message("Choose at least one item to make");
			return false;
		}
		Smithing smithing = new Smithing();
		ItemSmithingDef def = smithing.getProductionRecipeDef(player, session.getInputItemId(), itemId);
		if (def == null) {
			player.message("Nothing interesting happens");
			return false;
		}

		Item bar = new Item(session.getInputItemId());
		if (!smithing.canStartRecipe(player, bar, def)) {
			return false;
		}

		int availableCount = player.getCarriedItems().getInventory().countId(session.getInputItemId(), Optional.of(false)) / def.getRequiredBars();
		int makeCount = Math.min(quantity, availableCount);
		if (makeCount < 1) {
			player.message("You need more materials to make that");
			return false;
		}

		startbatch(player, makeCount);
		smithing.batchSmithing(player, bar, def);
		return true;
	}

	public static boolean beginMaterialSelectionFromInterface(Player player, ProductionSession session, int itemId, int quantity) {
		if (session == null || !session.isType(ProductionSession.TYPE_SMITHING_MATERIAL)) {
			return false;
		}
		Smithing smithing = new Smithing();
		if (!smithing.isModernMetalBar(itemId)) {
			player.message("Nothing interesting happens");
			return false;
		}
		if (player.getCarriedItems().getInventory().countId(ItemId.HAMMER.id(), Optional.of(false)) < 1) {
			player.playerServerMessage(MessageType.QUEST, "You need a hammer to work the metal with.");
			return false;
		}
		if (player.getCarriedItems().getInventory().countId(itemId, Optional.of(false)) < 1) {
			player.message("You do not have any of that metal to work with");
			return false;
		}
		ProductionSession smithingSession = smithing.createSmithingProductionSession(player, new Item(itemId));
		if (smithingSession == null || !smithingSession.hasAnyCraftableRecipe()) {
			player.message("You are not skilled enough for that yet");
			return false;
		}
		player.setAttribute("production_session", smithingSession);
		player.setAttribute("production_starter", (ProductionStarter) Smithing::beginProductionFromInterface);
		ActionSender.showProductionInterface(player, smithingSession);
		return true;
	}

	private boolean canStartRecipe(Player player, Item item, ItemSmithingDef def) {
		if (!canReceive(player, new Item(def.getItemID()))) {
			player.message("Your client does not support the desired object");
			return false;
		}
		if (player.getSkills().getLevel(Skill.SMITHING.id()) < def.getRequiredLevel()) {
			player.message("You need to be at least level " + def.getRequiredLevel() + " smithing to do that");
			return false;
		}
		if (player.getCarriedItems().getInventory().countId(item.getCatalogId(), Optional.of(false)) < def.getRequiredBars()) {
			player.message("You need " + def.getRequiredBars() + " bars of metal to make this item");
			return false;
		}
		if (player.getCarriedItems().getInventory().countId(ItemId.HAMMER.id(), Optional.of(false)) < 1) {
			player.message("You need a hammer to work the metal with.");
			return false;
		}
		return true;
	}

	private ProductionSession createSmithingProductionSession(Player player, Item item) {
		if (!isModernMetalBar(item.getCatalogId())) {
			return null;
		}

		List<ProductionRecipe> recipes = new ArrayList<>();
		addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), 0);
		addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), 2);
		addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), 3);
		addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), 4);
		addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), 5);
		addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), 6);
		addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), 7);
		addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), 21);
		addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), 8);
		addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), 9);
		if (player.getConfig().CAN_FEATURE_MEMBS) {
			addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), 10);
		}
		addModernArmorRecipe(recipes, player, item.getCatalogId(), 0);
		addModernArmorRecipe(recipes, player, item.getCatalogId(), 1);
		addModernArmorRecipe(recipes, player, item.getCatalogId(), 2);
		addModernArmorRecipe(recipes, player, item.getCatalogId(), 3);
		addModernArmorRecipe(recipes, player, item.getCatalogId(), 4);
		addModernArmorRecipe(recipes, player, item.getCatalogId(), 5);
		addModernArmorRecipe(recipes, player, item.getCatalogId(), 6);
		addModernSpecialRecipe(recipes, player, item.getCatalogId());

		if (recipes.isEmpty()) {
			return null;
		}
		return new ProductionSession(ProductionSession.TYPE_SMITHING, "Choose an item to smith", item.getCatalogId(), recipes);
	}

	private ProductionSession createSmithingMaterialSession(Player player) {
		List<ProductionRecipe> recipes = new ArrayList<>();
		int level = player.getSkills().getLevel(Skill.SMITHING.id());
		for (int barId : MODERN_ANVIL_BARS) {
			int requiredLevel = minSmithingLevel(barId);
			boolean levelMet = requiredLevel > -1 && level >= requiredLevel;
			boolean materialsMet = player.getCarriedItems().getInventory().countId(barId, Optional.of(false)) > 0;
			recipes.add(new ProductionRecipe(barId, requiredLevel, 1, 1, levelMet, materialsMet));
		}
		return new ProductionSession(ProductionSession.TYPE_SMITHING_MATERIAL, "Choose a metal to work", ItemId.HAMMER.id(), recipes);
	}

	private void addModernWeaponOrMissileRecipe(List<ProductionRecipe> recipes, Player player, int barId, int toMake) {
		ItemSmithingDef def = getModernWeaponOrMissileRecipe(barId, toMake);
		if (def != null) {
			recipes.add(toProductionRecipe(player, barId, def));
		}
	}

	private void addModernArmorRecipe(List<ProductionRecipe> recipes, Player player, int barId, int option) {
		ItemSmithingDef def = getModernArmorRecipe(barId, option);
		if (def != null) {
			recipes.add(toProductionRecipe(player, barId, def));
		}
	}

	private void addModernSpecialRecipe(List<ProductionRecipe> recipes, Player player, int barId) {
		ItemSmithingDef def = getModernSpecialRecipe(player, barId);
		if (def != null) {
			recipes.add(toProductionRecipe(player, barId, def));
		}
	}

	private ProductionRecipe toProductionRecipe(Player player, int barId, ItemSmithingDef def) {
		int level = player.getSkills().getLevel(Skill.SMITHING.id());
		int materialCount = player.getCarriedItems().getInventory().countId(barId, Optional.of(false));
		return new ProductionRecipe(def.getItemID(), def.getRequiredLevel(), def.getRequiredBars(), def.getAmount(),
			level >= def.getRequiredLevel(), materialCount >= def.getRequiredBars());
	}

	private ItemSmithingDef getProductionRecipeDef(Player player, int barId, int itemId) {
		if (!isModernMetalBar(barId)) {
			return null;
		}
		int[] weaponIds = {0, 2, 3, 4, 5, 6, 7, 21, 8, 9, 10};
		for (int toMake : weaponIds) {
			if (toMake == 10 && !player.getConfig().CAN_FEATURE_MEMBS) {
				continue;
			}
			ItemSmithingDef def = getModernWeaponOrMissileRecipe(barId, toMake);
			if (def != null && def.getItemID() == itemId) {
				return def;
			}
		}
		for (int option = 0; option <= 6; option++) {
			ItemSmithingDef def = getModernArmorRecipe(barId, option);
			if (def != null && def.getItemID() == itemId) {
				return def;
			}
		}
		ItemSmithingDef specialDef = getModernSpecialRecipe(player, barId);
		if (specialDef != null && specialDef.getItemID() == itemId) {
			return specialDef;
		}
		return null;
	}

	private int firstMenu(Item item, Player player) {
		int option;
		ArrayList<String> options = new ArrayList<>();
		int maxItemId = player.getConfig().RESTRICT_ITEM_ID;

		// Steel Bar
		if (item.getCatalogId() == ItemId.STEEL_BAR.id()) {
			options.addAll(Arrays.asList(
				"Make Weapon",
				"Make Armour"
			));
			if (MathUtil.maxUnsigned(maxItemId, ItemId.NAILS.id()) == maxItemId) {
				options.add("Make Nails");
			}
			options.add("Cancel");
			String[] finalOptions = new String[options.size()];
			option = multi(player, options.toArray(finalOptions));

			// Cancel
			if (option == finalOptions.length - 1) return -1;

			if (option > 1) {
				if (option == 2) {
					option = 3;
				}
			}

			return option;
		}

		// Bronze Bar
		if (item.getCatalogId() == ItemId.BRONZE_BAR.id()) {
			options.addAll(Arrays.asList(
				"Make Weapon",
				"Make Armour"
			));
			if (MathUtil.maxUnsigned(maxItemId, ItemId.BRONZE_WIRE.id()) == maxItemId) {
				options.add("Make Craft Item");
			}
			options.add("Cancel");
			String[] finalOptions = new String[options.size()];
			option = multi(player, options.toArray(finalOptions));

			// Cancel
			if (option == finalOptions.length - 1) return -1;

			if (option > 1) {
				if (option == 2) {
					option = 3;
				}
			}

			return option;
		}

		// Any other bar.
		options.addAll(Arrays.asList(
			"Make Weapon",
			"Make Armour"
		));
		options.add("Cancel");
		String[] finalOptions = new String[options.size()];
		option = multi(player, options.toArray(finalOptions));

		if (option == finalOptions.length - 1) return -1;

		return option;
	}

	private void handleModernMetalArmorSmithing(final Item item, final Player player) {
		player.message("Choose a piece of armour to make");
		int option = multi(player, "Helmet", "Gauntlets", "Greaves", "Shield", "Legs", "Body");
		if (option < 0) {
			return;
		}

		final ItemSmithingDef def = getModernArmorRecipe(item.getCatalogId(), option);
		if (def == null) {
			player.message("Nothing interesting happens");
			return;
		}

		int makeCount = getCount(def, item, player);
		if (makeCount == -1) {
			return;
		}

		startbatch(makeCount);
		batchSmithing(player, item, def);
	}

	private ItemSmithingDef getModernArmorRecipe(int barId, int option) {
		int tier = getMetalTier(barId);
		if (tier < 1) {
			return null;
		}

		int baseLevel = getModernMetalBaseLevel(tier);
		ItemSmithingDef def = new ItemSmithingDef();
		def.amount = 1;

		switch (option) {
			case 0:
				def.bars = 1;
				def.level = baseLevel;
				def.itemID = getModernHelmetId(barId);
				break;
			case 1:
				def.bars = 2;
				def.level = baseLevel + 2;
				def.itemID = getModernGauntletId(barId);
				break;
			case 2:
				def.bars = 2;
				def.level = baseLevel + 2;
				def.itemID = getModernGreavesId(barId);
				break;
			case 3:
				def.bars = 3;
				def.level = baseLevel + 4;
				def.itemID = getModernShieldId(barId);
				break;
			case 4:
				def.bars = 3;
				def.level = baseLevel + 4;
				def.itemID = getModernLegsId(barId);
				break;
			case 5:
				def.bars = 4;
				def.level = baseLevel + 6;
				def.itemID = getModernBodyId(barId);
				break;
			case 6:
				def.bars = 3;
				def.level = baseLevel + 4;
				def.itemID = getModernPaladinShieldId(barId);
				break;
			default:
				return null;
		}

		return def.itemID >= 0 ? def : null;
	}

	private int secondMenu(Item item, Player player, int firstType) {

		int offset = 0;

		ArrayList<String> options = new ArrayList<>();

		// Weapon
		if (firstType == 0) {
			player.message("Choose a type of weapon to make");
			options.addAll(Arrays.asList(
				"Dagger",
				"Sword",
				"Axe",
				"Mace"
			));
			String[] finalOptions = new String[options.size()];
			int option = multi(player, options.toArray(finalOptions));

			if (option > 0) ++option;
			return option;
		}

		offset += 5;

		// Armour
		if (firstType == 1) {
			player.message("Choose a type of armour to make");
			int option = multi(player, "Helmet", "Shield", "Armour");
			// Cancel
			if (option < 0) return -1;

			return offset + option;
		}

		offset += 3;

		if (firstType == 3) {

			// Nails
			if (item.getCatalogId() == ItemId.STEEL_BAR.id()) {
				makeNails(item, player);
			}

			// Bronze Wire
			else if (item.getCatalogId() == ItemId.BRONZE_BAR.id()) {
				makeWire(item, player);
			}
		}

		return -1;
	}

	private void makeNails(Item item, Player player) {
		if (!canReceive(player, new Item(ItemId.NAILS.id()))) {
			player.message("Your client does not support the desired object");
			return;
		}

		if (player.getSkills().getLevel(Skill.SMITHING.id()) < 34) {
			player.message("You need to be at least level 34 smithing to do that");
			return;
		}
		if (player.getCarriedItems().getInventory().countId(ItemId.STEEL_BAR.id(), Optional.of(false)) < 1) {
			player.playerServerMessage(MessageType.QUEST, "You need 1 bar of metal to make this item");
			return;
		}
		thinkbubble(item);
		player.getCarriedItems().remove(new Item(ItemId.STEEL_BAR.id()));
		player.playerServerMessage(MessageType.QUEST, "You hammer the metal and make some nails");
		player.getCarriedItems().getInventory().add(new Item(ItemId.NAILS.id(), 2));
		player.incExp(Skill.SMITHING.id(), 150, true);
	}

	private void makeWire(Item item, Player player) {
		player.message("What sort of craft item do you want to make?");
		int bronzeWireOption = multi(player, "Bronze Wire(1 bar)", "Cancel");

		if (bronzeWireOption == 1) return;

		if (!player.getConfig().MEMBER_WORLD) {
			player.message("This feature is members only");
			return;
		}

		if (!canReceive(player, new Item(ItemId.BRONZE_WIRE.id()))) {
			player.message("Your client does not support the desired object");
			return;
		}

		/*if (player.isBusy()) {
			return;
		}*/
		if (player.getSkills().getLevel(Skill.SMITHING.id()) < 4) {
			player.message("You need to be at least level 4 smithing to do that");
			return;
		}
		if (player.getCarriedItems().getInventory().countId(ItemId.BRONZE_BAR.id(), Optional.of(false)) < 1) {
			player.playerServerMessage(MessageType.QUEST, "You need 1 bar of metal to make this item");
			return;
		}
		if (bronzeWireOption == 0) {
			thinkbubble(item);
			player.getCarriedItems().remove(new Item(ItemId.BRONZE_BAR.id()));
			player.playerServerMessage(MessageType.QUEST, "You hammer the Bronze Bar and make some bronze wire");
			player.getCarriedItems().getInventory().add(new Item(ItemId.BRONZE_WIRE.id(), 1));
			player.incExp(Skill.SMITHING.id(), 50, true);
		}
	}

	private int chooseItem(Player player, int secondType) {
		// Dagger
		if (secondType == 0) return 0;

			// Throwing Knife
		else if (secondType == 1) {
			if (!player.getConfig().MEMBER_WORLD) {
				player.message("This feature is members only");
				return -1;
			}
			return 1;
		}

		// Sword
		else if (secondType == 2) return swordChoice(player);

			// Axe
		else if (secondType == 3) return axeChoice(player);

			// Mace
		else if (secondType == 4) return 9;

			// Helmet
		else if (secondType == 5) return helmetChoice(player);

			// Shield
		else if (secondType == 6) return shieldChoice(player);

			// Armour
		else if (secondType == 7) return armourChoice(player);

		return -1;
	}

	private int swordChoice(Player player) {
		player.message("What sort of sword do you want to make?");
		int option = multi(player, "Short sword",
			"Long sword (2 bars)", "Scimitar (2 bars)",
			"2-handed sword (3 bars)");
		if (option == 0) return 2; // Short Sword
		else if (option == 1) return 3; // Long Sword
		else if (option == 2) return 4; // Scimitar
		else if (option == 3) return 5; // 2-handed Sword
		return -1;
	}

	private int axeChoice(Player player) {
		player.message("What sort of axe do you want to make?");
		int option = multi(player, "Hatchet", "Pickaxe", "Shears", "Battle Axe (3 bars)");
		if (option == 0) return 6; // Hatchet
		else if (option == 1) return 7; // Pickaxe
		else if (option == 2) return 21; // Shears
		else if (option == 3) return 8; // Battle Axe
		return -1;
	}

	private int helmetChoice(Player player) {
		player.message("What sort of helmet do you want to make?");
		int option = multi(player, "Large Helmet (2 bars)");
		if (option == 0) return 11; // Large Helmet
		return -1;
	}

	private int shieldChoice(Player player) {
		player.message("What sort of shield do you want to make?");
		int option = multi(player, "Paladin Shield (3 bars)");
		if (option == 0) return 13; // Paladin Shield
		return -1;
	}

	private int armourChoice(Player player) {
		player.message("What sort of armour do you want to make?");
		ArrayList<String> options = new ArrayList<>();
		options.add("Plate mail body (5 bars)");
		options.add("Plate mail legs (3 bars)");
		options.add("Plated Skirt (3 bars)");
		if (player.getConfig().WANT_CUSTOM_SPRITES) {
			options.add("Plate mail top (5 bars)");
		}

		String[] finalOptions = new String[options.size()];

		int option = multi(player, options.toArray(finalOptions));

		if (option == 0) return 15; // Plate Mail Body
		else if (option == 1) return 16; // Plate Mail Legs
		else if (option == 2) return 17; // Plated Skirt
		else if (player.getConfig().WANT_CUSTOM_SPRITES) {
			if (option == 3) {
				return 23; // Plate mail top
			}
		}
		return -1;
	}

	private int getCount(ItemSmithingDef def, Item item, Player player) {
		int count = 1;
		if (player.getConfig().BATCH_PROGRESSION) {
			String[] options = {
				"Make 1",
				"Make 5",
				"Make 10",
				"Make All"
			};

			count = multi(player, options);

			if (count == -1) {
				return -1;
			}

			int maximumMakeCount = player.getCarriedItems().getInventory().countId(item.getCatalogId(), Optional.of(false)) / def.getRequiredBars();

			return count != 3
				? Integer.parseInt(options[count].replaceAll("Make ", ""))
				: maximumMakeCount;
		}

		return count;
	}

	/**
	 * Gets the smithing exp for the given amount of the right bars
	 */
	public int getSmithingExp(int barID, int barCount) {
		int[] exps = {25, 40, 50, 100, 150, 200, 240, 250, 300, 350};
		int type = getBarType(barID);
		if (type < 0) {
			return 0;
		}
		return (exps[type] * barCount);
	}

	/**
	 * Gets the min level required to smith a bar
	 */
	public int minSmithingLevel(int barID) {
		int[] levels = {1, 8, 15, 22, 30, 38, 46, 54, 62, 70};
		int type = getBarType(barID);
		if (type < 0) {
			return -1;
		}
		return levels[type];
	}

	/**
	 * Gets the type of bar we have
	 */
	public int getBarType(int barID) {
		switch (ItemId.getById(barID)) {
			case TIN_BAR:
				return 0;
			case COPPER_BAR:
				return 1;
			case BRONZE_BAR:
				return 2;
			case IRON_BAR:
				return 3;
			case STEEL_BAR:
				return 4;
			case GOLD_BAR:
			case MITHRIL_BAR:
				return 5;
			case TITAN_STEEL_BAR:
				return 6;
			case ADAMANTITE_BAR:
				return 7;
			case ORICHALCUM_BAR:
				return 8;
			case RUNITE_BAR:
				return 9;
			default:
				break;
		}
		return -1;
	}

	private boolean isModernMetalBar(int barId) {
		ItemId id = ItemId.getById(barId);
		return id == ItemId.TIN_BAR || id == ItemId.COPPER_BAR || id == ItemId.BRONZE_BAR
			|| id == ItemId.IRON_BAR || id == ItemId.STEEL_BAR || id == ItemId.MITHRIL_BAR
			|| id == ItemId.TITAN_STEEL_BAR || id == ItemId.ADAMANTITE_BAR
			|| id == ItemId.ORICHALCUM_BAR || id == ItemId.RUNITE_BAR;
	}

	private int getMetalTier(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return 1;
			case COPPER_BAR:
				return 2;
			case BRONZE_BAR:
				return 3;
			case IRON_BAR:
				return 4;
			case STEEL_BAR:
				return 5;
			case MITHRIL_BAR:
				return 6;
			case TITAN_STEEL_BAR:
				return 7;
			case ADAMANTITE_BAR:
				return 8;
			case ORICHALCUM_BAR:
				return 9;
			case RUNITE_BAR:
				return 10;
			default:
				return -1;
		}
	}

	private int getModernMetalBaseLevel(int tier) {
		int[] levels = {0, 1, 8, 15, 22, 30, 38, 46, 54, 62, 70};
		return levels[tier];
	}

	private int getModernHelmetId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_LARGE_HELMET.id();
			case COPPER_BAR:
				return ItemId.COPPER_LARGE_HELMET.id();
			case BRONZE_BAR:
				return ItemId.LARGE_BRONZE_HELMET.id();
			case IRON_BAR:
				return ItemId.LARGE_IRON_HELMET.id();
			case STEEL_BAR:
				return ItemId.LARGE_STEEL_HELMET.id();
			case MITHRIL_BAR:
				return ItemId.LARGE_MITHRIL_HELMET.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_LARGE_HELMET.id();
			case ADAMANTITE_BAR:
				return ItemId.LARGE_ADAMANTITE_HELMET.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_LARGE_HELMET.id();
			case RUNITE_BAR:
				return ItemId.LARGE_RUNE_HELMET.id();
			default:
				return -1;
		}
	}

	private int getModernGauntletId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_GAUNTLETS.id();
			case COPPER_BAR:
				return ItemId.COPPER_GAUNTLETS.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_GAUNTLETS.id();
			case IRON_BAR:
				return ItemId.IRON_GAUNTLETS.id();
			case STEEL_BAR:
				return ItemId.STEEL_GAUNTLETS.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_GAUNTLETS.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_GAUNTLETS.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_GAUNTLETS.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_GAUNTLETS.id();
			case RUNITE_BAR:
				return ItemId.RUNITE_GAUNTLETS.id();
			default:
				return -1;
		}
	}

	private int getModernGreavesId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_GREAVES.id();
			case COPPER_BAR:
				return ItemId.COPPER_GREAVES.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_GREAVES.id();
			case IRON_BAR:
				return ItemId.IRON_GREAVES.id();
			case STEEL_BAR:
				return ItemId.STEEL_GREAVES.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_GREAVES.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_GREAVES.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_GREAVES.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_GREAVES.id();
			case RUNITE_BAR:
				return ItemId.RUNITE_GREAVES.id();
			default:
				return -1;
		}
	}

	private int getModernShieldId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_SQUARE_SHIELD.id();
			case COPPER_BAR:
				return ItemId.COPPER_SQUARE_SHIELD.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_SQUARE_SHIELD.id();
			case IRON_BAR:
				return ItemId.IRON_SQUARE_SHIELD.id();
			case STEEL_BAR:
				return ItemId.STEEL_SQUARE_SHIELD.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_SQUARE_SHIELD.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_SQUARE_SHIELD.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_SQUARE_SHIELD.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_SQUARE_SHIELD.id();
			case RUNITE_BAR:
				return ItemId.RUNE_SQUARE_SHIELD.id();
			default:
				return -1;
		}
	}

	private int getModernPaladinShieldId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_KITE_SHIELD.id();
			case COPPER_BAR:
				return ItemId.COPPER_KITE_SHIELD.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_KITE_SHIELD.id();
			case IRON_BAR:
				return ItemId.IRON_KITE_SHIELD.id();
			case STEEL_BAR:
				return ItemId.STEEL_KITE_SHIELD.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_KITE_SHIELD.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_KITE_SHIELD.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_KITE_SHIELD.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_KITE_SHIELD.id();
			case RUNITE_BAR:
				return ItemId.RUNE_KITE_SHIELD.id();
			default:
				return -1;
		}
	}

	private int getModernLegsId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_PLATE_MAIL_LEGS.id();
			case COPPER_BAR:
				return ItemId.COPPER_PLATE_MAIL_LEGS.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_PLATE_MAIL_LEGS.id();
			case IRON_BAR:
				return ItemId.IRON_PLATE_MAIL_LEGS.id();
			case STEEL_BAR:
				return ItemId.STEEL_PLATE_MAIL_LEGS.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_PLATE_MAIL_LEGS.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_PLATE_MAIL_LEGS.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_PLATE_MAIL_LEGS.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_PLATE_MAIL_LEGS.id();
			case RUNITE_BAR:
				return ItemId.RUNE_PLATE_MAIL_LEGS.id();
			default:
				return -1;
		}
	}

	private int getModernBodyId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_PLATE_MAIL_BODY.id();
			case COPPER_BAR:
				return ItemId.COPPER_PLATE_MAIL_BODY.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_PLATE_MAIL_BODY.id();
			case IRON_BAR:
				return ItemId.IRON_PLATE_MAIL_BODY.id();
			case STEEL_BAR:
				return ItemId.STEEL_PLATE_MAIL_BODY.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_PLATE_MAIL_BODY.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_PLATE_MAIL_BODY.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_PLATE_MAIL_BODY.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_PLATE_MAIL_BODY.id();
			case RUNITE_BAR:
				return ItemId.RUNE_PLATE_MAIL_BODY.id();
			default:
				return -1;
		}
	}

	private ItemSmithingDef getModernWeaponOrMissileRecipe(int barId, int toMake) {
		int tier = getMetalTier(barId);
		if (tier < 1) {
			return null;
		}

		int baseLevel = getModernMetalBaseLevel(tier);
		ItemSmithingDef def = new ItemSmithingDef();
		def.amount = 1;

		switch (toMake) {
			case 0:
				def.bars = 1;
				def.level = baseLevel;
				def.itemID = getModernDaggerId(barId);
				break;
			case 1:
				def.bars = 1;
				def.level = baseLevel;
				def.itemID = getModernThrowingKnifeId(barId);
				break;
			case 2:
				def.bars = 1;
				def.level = baseLevel;
				def.itemID = getModernShortSwordId(barId);
				break;
			case 3:
				def.bars = 2;
				def.level = baseLevel;
				def.itemID = getModernLongSwordId(barId);
				break;
			case 4:
				def.bars = 2;
				def.level = baseLevel;
				def.itemID = getModernScimitarId(barId);
				break;
			case 5:
				def.bars = 3;
				def.level = baseLevel;
				def.itemID = getModernTwoHandedSwordId(barId);
				break;
			case 6:
				def.bars = 1;
				def.level = baseLevel;
				def.itemID = getModernAxeId(barId);
				break;
			case 7:
				def.bars = 1;
				def.level = baseLevel;
				def.itemID = getModernPickaxeId(barId);
				break;
			case 8:
				def.bars = 3;
				def.level = baseLevel;
				def.itemID = getModernBattleAxeId(barId);
				break;
			case 9:
				def.bars = 1;
				def.level = baseLevel;
				def.itemID = getModernMaceId(barId);
				break;
			case 21:
				def.bars = 1;
				def.level = baseLevel;
				def.itemID = getModernShearsId(barId);
				break;
			case 10:
				def.bars = 1;
				def.level = baseLevel;
				def.itemID = getModernSpearId(barId);
				break;
			default:
				return null;
		}

		return def.itemID >= 0 ? def : null;
	}

	private ItemSmithingDef getModernSpecialRecipe(Player player, int barId) {
		ItemSmithingDef def = new ItemSmithingDef();
		switch (ItemId.getById(barId)) {
			case BRONZE_BAR:
				if (!player.getWorld().getServer().getConfig().MEMBER_WORLD || !canReceive(player, new Item(ItemId.BRONZE_WIRE.id()))) {
					return null;
				}
				def.bars = 1;
				def.amount = 1;
				def.level = 4;
				def.itemID = ItemId.BRONZE_WIRE.id();
				return def;
			case STEEL_BAR:
				if (!canReceive(player, new Item(ItemId.NAILS.id()))) {
					return null;
				}
				def.bars = 1;
				def.amount = 2;
				def.level = 34;
				def.itemID = ItemId.NAILS.id();
				return def;
			default:
				return null;
		}
	}

	private boolean hasDartMould(Player player) {
		return player.getCarriedItems().hasCatalogID(ItemId.DART_MOULD.id(), Optional.of(false));
	}

	private boolean hasThrowingKnifeMould(Player player) {
		return player.getCarriedItems().hasCatalogID(ItemId.THROWING_KNIFE_MOULD.id(), Optional.of(false));
	}

	private boolean isModernThrowingKnifeItem(int itemId) {
		switch (ItemId.getById(itemId)) {
			case TIN_THROWING_KNIFE:
			case COPPER_THROWING_KNIFE:
			case BRONZE_THROWING_KNIFE:
			case IRON_THROWING_KNIFE:
			case STEEL_THROWING_KNIFE:
			case MITHRIL_THROWING_KNIFE:
			case TITAN_STEEL_THROWING_KNIFE:
			case ADAMANTITE_THROWING_KNIFE:
			case ORICHALCUM_THROWING_KNIFE:
			case RUNE_THROWING_KNIFE:
				return true;
			default:
				return false;
		}
	}

	private int getModernDaggerId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_DAGGER.id();
			case COPPER_BAR:
				return ItemId.COPPER_DAGGER.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_DAGGER.id();
			case IRON_BAR:
				return ItemId.IRON_DAGGER.id();
			case STEEL_BAR:
				return ItemId.STEEL_DAGGER.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_DAGGER.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_DAGGER.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_DAGGER.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_DAGGER.id();
			case RUNITE_BAR:
				return ItemId.RUNE_DAGGER.id();
			default:
				return -1;
		}
	}

	private int getModernThrowingKnifeId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_THROWING_KNIFE.id();
			case COPPER_BAR:
				return ItemId.COPPER_THROWING_KNIFE.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_THROWING_KNIFE.id();
			case IRON_BAR:
				return ItemId.IRON_THROWING_KNIFE.id();
			case STEEL_BAR:
				return ItemId.STEEL_THROWING_KNIFE.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_THROWING_KNIFE.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_THROWING_KNIFE.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_THROWING_KNIFE.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_THROWING_KNIFE.id();
			case RUNITE_BAR:
				return ItemId.RUNE_THROWING_KNIFE.id();
			default:
				return -1;
		}
	}

	private int getModernShortSwordId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_SHORT_SWORD.id();
			case COPPER_BAR:
				return ItemId.COPPER_SHORT_SWORD.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_SHORT_SWORD.id();
			case IRON_BAR:
				return ItemId.IRON_SHORT_SWORD.id();
			case STEEL_BAR:
				return ItemId.STEEL_SHORT_SWORD.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_SHORT_SWORD.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_SHORT_SWORD.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_SHORT_SWORD.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_SHORT_SWORD.id();
			case RUNITE_BAR:
				return ItemId.RUNE_SHORT_SWORD.id();
			default:
				return -1;
		}
	}

	private int getModernLongSwordId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_LONG_SWORD.id();
			case COPPER_BAR:
				return ItemId.COPPER_LONG_SWORD.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_LONG_SWORD.id();
			case IRON_BAR:
				return ItemId.IRON_LONG_SWORD.id();
			case STEEL_BAR:
				return ItemId.STEEL_LONG_SWORD.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_LONG_SWORD.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_LONG_SWORD.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_LONG_SWORD.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_LONG_SWORD.id();
			case RUNITE_BAR:
				return ItemId.RUNE_LONG_SWORD.id();
			default:
				return -1;
		}
	}

	private int getModernScimitarId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_SCIMITAR.id();
			case COPPER_BAR:
				return ItemId.COPPER_SCIMITAR.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_SCIMITAR.id();
			case IRON_BAR:
				return ItemId.IRON_SCIMITAR.id();
			case STEEL_BAR:
				return ItemId.STEEL_SCIMITAR.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_SCIMITAR.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_SCIMITAR.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_SCIMITAR.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_SCIMITAR.id();
			case RUNITE_BAR:
				return ItemId.RUNE_SCIMITAR.id();
			default:
				return -1;
		}
	}

	private int getModernTwoHandedSwordId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_2_HANDED_SWORD.id();
			case COPPER_BAR:
				return ItemId.COPPER_2_HANDED_SWORD.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_2_HANDED_SWORD.id();
			case IRON_BAR:
				return ItemId.IRON_2_HANDED_SWORD.id();
			case STEEL_BAR:
				return ItemId.STEEL_2_HANDED_SWORD.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_2_HANDED_SWORD.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_2_HANDED_SWORD.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_2_HANDED_SWORD.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_2_HANDED_SWORD.id();
			case RUNITE_BAR:
				return ItemId.RUNE_2_HANDED_SWORD.id();
			default:
				return -1;
		}
	}

	private int getModernAxeId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_AXE.id();
			case COPPER_BAR:
				return ItemId.COPPER_AXE.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_AXE.id();
			case IRON_BAR:
				return ItemId.IRON_AXE.id();
			case STEEL_BAR:
				return ItemId.STEEL_AXE.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_AXE.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_AXE.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_AXE.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_AXE.id();
			case RUNITE_BAR:
				return ItemId.RUNE_AXE.id();
			default:
				return -1;
		}
	}

	private int getModernBattleAxeId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_BATTLE_AXE.id();
			case COPPER_BAR:
				return ItemId.COPPER_BATTLE_AXE.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_BATTLE_AXE.id();
			case IRON_BAR:
				return ItemId.IRON_BATTLE_AXE.id();
			case STEEL_BAR:
				return ItemId.STEEL_BATTLE_AXE.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_BATTLE_AXE.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_BATTLE_AXE.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_BATTLE_AXE.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_BATTLE_AXE.id();
			case RUNITE_BAR:
				return ItemId.RUNE_BATTLE_AXE.id();
			default:
				return -1;
		}
	}

	private int getModernPickaxeId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_PICKAXE.id();
			case COPPER_BAR:
				return ItemId.COPPER_PICKAXE.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_PICKAXE.id();
			case IRON_BAR:
				return ItemId.IRON_PICKAXE.id();
			case STEEL_BAR:
				return ItemId.STEEL_PICKAXE.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_PICKAXE.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_PICKAXE.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_PICKAXE.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_PICKAXE.id();
			case RUNITE_BAR:
				return ItemId.RUNE_PICKAXE.id();
			default:
				return -1;
		}
	}

	private int getModernShearsId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.SHEARS.id();
			case COPPER_BAR:
				return ItemId.COPPER_SHEARS.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_SHEARS.id();
			case IRON_BAR:
				return ItemId.IRON_SHEARS.id();
			case STEEL_BAR:
				return ItemId.STEEL_SHEARS.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_SHEARS.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_SHEARS.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_SHEARS.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_SHEARS.id();
			case RUNITE_BAR:
				return ItemId.RUNE_SHEARS.id();
			default:
				return -1;
		}
	}

	private int getModernMaceId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_MACE.id();
			case COPPER_BAR:
				return ItemId.COPPER_MACE.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_MACE.id();
			case IRON_BAR:
				return ItemId.IRON_MACE.id();
			case STEEL_BAR:
				return ItemId.STEEL_MACE.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_MACE.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_MACE.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_MACE.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_MACE.id();
			case RUNITE_BAR:
				return ItemId.RUNE_MACE.id();
			default:
				return -1;
		}
	}

	private int getModernSpearId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_SPEAR.id();
			case COPPER_BAR:
				return ItemId.COPPER_SPEAR.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_SPEAR.id();
			case IRON_BAR:
				return ItemId.IRON_SPEAR.id();
			case STEEL_BAR:
				return ItemId.STEEL_SPEAR.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_SPEAR.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_SPEAR.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_SPEAR.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_SPEAR.id();
			case RUNITE_BAR:
				return ItemId.RUNE_SPEAR.id();
			default:
				return -1;
		}
	}

}
