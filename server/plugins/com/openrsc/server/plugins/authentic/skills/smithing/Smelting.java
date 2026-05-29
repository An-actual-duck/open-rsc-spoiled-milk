package com.openrsc.server.plugins.authentic.skills.smithing;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.SkillCapes;
import com.openrsc.server.content.production.ProductionRecipe;
import com.openrsc.server.content.production.ProductionSession;
import com.openrsc.server.content.production.ProductionStarter;
import com.openrsc.server.external.Gauntlets;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.CarriedItems;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.plugins.triggers.UseLocTrigger;
import com.openrsc.server.plugins.authentic.skills.crafting.Crafting;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.MessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.openrsc.server.plugins.Functions.*;

public class Smelting implements OpLocTrigger, UseLocTrigger {

	public static final int FURNACE = SceneryId.FURNACE.id();
	public static final int LAVA_FURNACE = SceneryId.LAVA_FORGE.id();
	public static final int FURNACE_CATEGORY_BARS = ItemId.BRONZE_BAR.id();
	public static final int FURNACE_CATEGORY_RINGS = ItemId.GOLD_RING.id();
	public static final int FURNACE_CATEGORY_NECKLACES = ItemId.GOLD_NECKLACE.id();
	public static final int FURNACE_CATEGORY_AMULETS = ItemId.UNSTRUNG_GOLD_AMULET.id();
	public static final int FURNACE_CATEGORY_HOLY_SYMBOLS = ItemId.UNSTRUNG_HOLY_SYMBOL_OF_SARADOMIN.id();
	public static final int FURNACE_CATEGORY_UNHOLY_SYMBOLS = ItemId.UNSTRUNG_UNHOLY_SYMBOL_OF_ZAMORAK.id();
	public static final int FURNACE_CATEGORY_BOLTS = ItemId.CROSSBOW_BOLTS.id();
	public static final int FURNACE_CATEGORY_ARROWHEADS = ItemId.TIN_ARROW_HEADS.id();
	public static final int FURNACE_CATEGORY_DARTS = ItemId.TIN_DART_TIPS.id();
	public static final int FURNACE_CATEGORY_THROWING_KNIVES = ItemId.TIN_THROWING_KNIFE.id();
	public static final int FURNACE_CATEGORY_CANNONBALLS = ItemId.MULTI_CANNON_BALL.id();

	private static final int[] NORMAL_SMELTING_ITEMS = {
		ItemId.TIN_ORE.id(), ItemId.COPPER_ORE.id(), ItemId.IRON_ORE.id(),
		ItemId.PIG_IRON_BAR.id(), ItemId.COAL.id(), ItemId.MITHRIL_ORE.id(),
		ItemId.ADAMANTITE_ORE.id(), ItemId.RUNITE_ORE.id(), ItemId.SILVER.id(),
		ItemId.GOLD.id(), ItemId.GOLD_FAMILYCREST.id()
	};

	private static final SmeltRecipe[] RECIPES = {
		new SmeltRecipe(ItemId.TIN_BAR.id(), 1, 15, 1, ingredient(ItemId.TIN_ORE.id(), 1)),
		new SmeltRecipe(ItemId.COPPER_BAR.id(), 8, 15, 1, ingredient(ItemId.COPPER_ORE.id(), 1)),
		new SmeltRecipe(ItemId.BRONZE_BAR.id(), 15, 25, 1,
			ingredient(ItemId.TIN_ORE.id(), 1), ingredient(ItemId.COPPER_ORE.id(), 1)),
		new SmeltRecipe(ItemId.SILVER_BAR.id(), 20, 54, 1, ingredient(ItemId.SILVER.id(), 1)),
		new SmeltRecipe(ItemId.IRON_BAR.id(), 22, 50, 1, ingredient(ItemId.IRON_ORE.id(), 1)),
		new SmeltRecipe(ItemId.STEEL_BAR.id(), 30, 70, 1,
			ingredient(ItemId.COAL.id(), 1), either(ItemId.PIG_IRON_BAR.id(), ItemId.IRON_ORE.id(), 1)),
		new SmeltRecipe(ItemId.MITHRIL_BAR.id(), 38, 120, 1,
			ingredient(ItemId.MITHRIL_ORE.id(), 1), ingredient(ItemId.COAL.id(), 2)),
		new SmeltRecipe(ItemId.GOLD_BAR.id(), 40, 90, 1, ingredient(ItemId.GOLD.id(), 1)),
		new SmeltRecipe(ItemId.TITAN_STEEL_BAR.id(), 46, 170, 1,
			ingredient(ItemId.MITHRIL_ORE.id(), 1), ingredient(ItemId.SILVER.id(), 1), ingredient(ItemId.COAL.id(), 3)),
		new SmeltRecipe(ItemId.ADAMANTITE_BAR.id(), 54, 150, 1,
			ingredient(ItemId.ADAMANTITE_ORE.id(), 1), ingredient(ItemId.COAL.id(), 4)),
		new SmeltRecipe(ItemId.ORICHALCUM_BAR.id(), 62, 190, 2,
			ingredient(ItemId.MITHRIL_ORE.id(), 1), ingredient(ItemId.ADAMANTITE_ORE.id(), 1),
			ingredient(ItemId.SILVER.id(), 1), ingredient(ItemId.GOLD.id(), 1), ingredient(ItemId.COAL.id(), 5)),
		new SmeltRecipe(ItemId.RUNITE_BAR.id(), 70, 200, 1,
			ingredient(ItemId.RUNITE_ORE.id(), 1), ingredient(ItemId.COAL.id(), 6))
	};

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return obj.getID() == FURNACE;
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (obj.getID() != FURNACE) {
			return;
		}
		if (!isInFurnaceRange(player, obj)) {
			return;
		}
		openSmeltingInterface(player);
	}

	@Override
	public void onUseLoc(Player player, GameObject obj, Item item) {
		if (obj.getID() == FURNACE) {
			if (item.getCatalogId() == ItemId.STEEL_BAR.id()
				&& player.getWorld().canYield(new Item(ItemId.MULTI_CANNON_BALL.id()))
				&& player.getConfig().MEMBER_WORLD) {
				if (player.getCarriedItems().hasCatalogID(ItemId.CANNON_AMMO_MOULD.id())) {
					int repeat = 1;
					if (config().BATCH_PROGRESSION) {
						repeat = player.getCarriedItems().getInventory().countId(item.getCatalogId(), Optional.of(false));
					}
					startbatch(repeat);
					handleCannonBallSmelting(player);
				} else {
					player.message("you heat the steel bar");
				}
				return;
			}
			SmeltRecipe directRecipe = getDirectOreRecipe(item.getCatalogId());
			if (directRecipe != null) {
				int repeat = 1;
				if (config().BATCH_PROGRESSION) {
					repeat = maxDirectSmelts(player, directRecipe);
				}
				makeSmeltingProduction(player, directRecipe, repeat);
				return;
			}
			if (DataConversions.inArray(NORMAL_SMELTING_ITEMS, item.getCatalogId())) {
				player.message("Use the furnace directly to choose what to smelt");
				return;
			}
			return;
		}

		if (obj.getID() == LAVA_FURNACE) {
			handleLavaFurnace(player, item);
		}
	}

	private void openSmeltingInterface(Player player) {
		if (ActionSender.isRetroClient(player)) {
			player.message("Use ore on the furnace to smelt with this client");
			return;
		}
		ProductionSession session = createFurnaceCategorySession(player);
		if (session == null) {
			player.message("Nothing interesting happens");
			return;
		}
		player.setAttribute("production_session", session);
		player.setAttribute("production_starter", (ProductionStarter) Smelting::beginFurnaceCategoryFromInterface);
		ActionSender.showProductionInterface(player, session);
	}

	private ProductionSession createFurnaceCategorySession(Player player) {
		List<ProductionRecipe> recipes = new ArrayList<>();
		recipes.add(categoryRecipe(FURNACE_CATEGORY_BARS, true, -1));
		recipes.add(categoryRecipe(FURNACE_CATEGORY_RINGS, has(player, ItemId.RING_MOULD.id()), ItemId.RING_MOULD.id()));
		recipes.add(categoryRecipe(FURNACE_CATEGORY_NECKLACES, has(player, ItemId.NECKLACE_MOULD.id()), ItemId.NECKLACE_MOULD.id()));
		recipes.add(categoryRecipe(FURNACE_CATEGORY_AMULETS, has(player, ItemId.AMULET_MOULD.id()), ItemId.AMULET_MOULD.id()));
		recipes.add(categoryRecipe(FURNACE_CATEGORY_HOLY_SYMBOLS, has(player, ItemId.HOLY_SYMBOL_MOULD.id()), ItemId.HOLY_SYMBOL_MOULD.id()));
		recipes.add(categoryRecipe(FURNACE_CATEGORY_UNHOLY_SYMBOLS, has(player, ItemId.UNHOLY_SYMBOL_MOULD.id()), ItemId.UNHOLY_SYMBOL_MOULD.id()));
		recipes.add(categoryRecipe(FURNACE_CATEGORY_BOLTS, has(player, ItemId.BOLT_MOULD.id()), ItemId.BOLT_MOULD.id()));
		recipes.add(categoryRecipe(FURNACE_CATEGORY_ARROWHEADS, has(player, ItemId.ARROWHEAD_MOULD.id()), ItemId.ARROWHEAD_MOULD.id()));
		recipes.add(categoryRecipe(FURNACE_CATEGORY_DARTS, has(player, ItemId.DART_MOULD.id()), ItemId.DART_MOULD.id()));
		recipes.add(categoryRecipe(FURNACE_CATEGORY_THROWING_KNIVES, has(player, ItemId.THROWING_KNIFE_MOULD.id()), ItemId.THROWING_KNIFE_MOULD.id()));
		recipes.add(categoryRecipe(FURNACE_CATEGORY_CANNONBALLS, has(player, ItemId.CANNON_AMMO_MOULD.id()), ItemId.CANNON_AMMO_MOULD.id()));
		return new ProductionSession(ProductionSession.TYPE_FURNACE_CATEGORY, "What would you like to do?", -1, recipes);
	}

	private ProductionRecipe categoryRecipe(int itemId, boolean materialsMet, int mouldId) {
		if (mouldId < 0) {
			return new ProductionRecipe(itemId, 1, 1, 1, true, materialsMet);
		}
		return new ProductionRecipe(itemId, 1, 1, 1, true, materialsMet,
			new int[]{mouldId}, new int[]{-1}, new int[]{1});
	}

	private boolean has(Player player, int itemId) {
		return player.getCarriedItems().getInventory().countId(itemId, Optional.of(false)) > 0;
	}

	private ProductionSession createSmeltingProductionSession(Player player) {
		List<ProductionRecipe> recipes = new ArrayList<>();
		for (SmeltRecipe recipe : RECIPES) {
			recipes.add(new ProductionRecipe(recipe.barId, recipe.requiredLevel,
				recipe.totalInputAmount(), recipe.outputAmount,
				getCurrentLevel(player, Skill.SMITHING.id()) >= recipe.requiredLevel,
				recipe.hasMaterials(player), recipe.ingredientItemIds(),
				recipe.ingredientFallbackItemIds(), recipe.ingredientAmounts()));
		}
		return new ProductionSession(ProductionSession.TYPE_SMELTING, "Choose a bar to smelt", -1, recipes);
	}

	public static boolean beginProductionFromInterface(Player player, ProductionSession session, int itemId, int quantity) {
		if (session == null || !session.isType(ProductionSession.TYPE_SMELTING)) {
			return false;
		}
		if (quantity < 1) {
			player.message("Choose at least one bar to smelt");
			return false;
		}
		SmeltRecipe recipe = getRecipe(itemId);
		if (recipe == null) {
			player.message("Nothing interesting happens");
			return false;
		}
		return new Smelting().makeSmeltingProduction(player, recipe, quantity);
	}

	public static boolean beginFurnaceCategoryFromInterface(Player player, ProductionSession session, int itemId, int quantity) {
		if (session == null || !session.isType(ProductionSession.TYPE_FURNACE_CATEGORY)) {
			return false;
		}
		Smelting smelting = new Smelting();
		if (itemId == FURNACE_CATEGORY_BARS) {
			ProductionSession smeltingSession = smelting.createSmeltingProductionSession(player);
			player.setAttribute("production_session", smeltingSession);
			player.setAttribute("production_starter", (ProductionStarter) Smelting::beginProductionFromInterface);
			ActionSender.showProductionInterface(player, smeltingSession);
			return true;
		}
		if (itemId == FURNACE_CATEGORY_CANNONBALLS) {
			return smelting.openCannonballProduction(player);
		}
		return new Crafting().openFurnaceCategory(player, itemId);
	}

	private boolean openCannonballProduction(Player player) {
		if (!player.getWorld().canYield(new Item(ItemId.MULTI_CANNON_BALL.id()))
			|| !player.getConfig().MEMBER_WORLD) {
			player.message("Nothing interesting happens");
			return false;
		}
		int level = getCurrentLevel(player, Skill.SMITHING.id());
		int steelCount = player.getCarriedItems().getInventory().countId(ItemId.STEEL_BAR.id(), Optional.of(false));
		boolean hasMould = player.getCarriedItems().hasCatalogID(ItemId.CANNON_AMMO_MOULD.id());
		List<ProductionRecipe> recipes = new ArrayList<>();
		recipes.add(new ProductionRecipe(ItemId.MULTI_CANNON_BALL.id(), 30, 1, 1,
			level >= 30, steelCount >= 1 && hasMould,
			new int[]{ItemId.STEEL_BAR.id(), ItemId.CANNON_AMMO_MOULD.id()},
			new int[]{-1, -1}, new int[]{1, 1}));
		ProductionSession cannonSession = new ProductionSession(ProductionSession.TYPE_SMELTING, "Choose cannonballs to cast", ItemId.STEEL_BAR.id(), recipes);
		player.setAttribute("production_session", cannonSession);
		player.setAttribute("production_starter", (ProductionStarter) Smelting::beginCannonballProductionFromInterface);
		ActionSender.showProductionInterface(player, cannonSession);
		return true;
	}

	public static boolean beginCannonballProductionFromInterface(Player player, ProductionSession session, int itemId, int quantity) {
		if (session == null || !session.isType(ProductionSession.TYPE_SMELTING)
			|| itemId != ItemId.MULTI_CANNON_BALL.id()) {
			return false;
		}
		if (quantity < 1) {
			player.message("Choose at least one item to make");
			return false;
		}
		startbatch(quantity);
		new Smelting().handleCannonBallSmelting(player);
		return true;
	}

	private boolean makeSmeltingProduction(Player player, SmeltRecipe recipe, int requestedCount) {
		if (getCurrentLevel(player, Skill.SMITHING.id()) < recipe.requiredLevel) {
			player.playerServerMessage(MessageType.QUEST,
				"You need to be at least level-" + recipe.requiredLevel + " smithing to smelt "
					+ getBarName(player, recipe));
			return false;
		}
		if (!recipe.hasMaterials(player)) {
			player.playerServerMessage(MessageType.QUEST, recipe.requirementMessage(player));
			return false;
		}
		if (config().WANT_FATIGUE && config().STOP_SKILLING_FATIGUED >= 2
			&& player.getFatigue() >= player.MAX_FATIGUE) {
			player.message("You are too tired to smelt this ore");
			return false;
		}

		int made = 0;
		startbatch(requestedCount);
		while (!ifinterrupted() && !isbatchcomplete()) {
			if (!recipe.hasMaterials(player)) {
				break;
			}
			if (config().WANT_FATIGUE && config().STOP_SKILLING_FATIGUED >= 2
				&& player.getFatigue() >= player.MAX_FATIGUE) {
				player.message("You are too tired to smelt this ore");
				break;
			}

			player.playerServerMessage(MessageType.QUEST, recipe.startMessage(player));
			delay(3);
			recipe.consumeMaterials(player);
			thinkbubble(new Item(recipe.barId));

			if (recipe.barId == ItemId.IRON_BAR.id() && DataConversions.random(0, 1) == 1) {
				if (natureRingSteadiesIronSmelt(player)) {
					player.message("@or1@Your nature ring steadies the smelt");
					give(player, ItemId.IRON_BAR.id(), 1);
					player.playerServerMessage(MessageType.QUEST, "You retrieve a bar of iron");
					player.incExp(Skill.SMITHING.id(), recipe.xp, true);
				} else {
					player.message("The ore is too impure, but you recover a bar of pig iron");
					give(player, ItemId.PIG_IRON_BAR.id(), 1);
				}
			} else {
				for (int i = 0; i < recipe.outputAmount; i++) {
					give(player, recipe.barId, 1);
				}
				player.playerServerMessage(MessageType.QUEST, "You retrieve "
					+ (recipe.outputAmount > 1 ? recipe.outputAmount + " bars" : "a bar")
					+ " of " + getBarName(player, recipe));
				int xp = recipe.xp;
				if (hasGoldsmithingBonus(player, recipe.barId)) {
					xp += 45;
				}
				player.incExp(Skill.SMITHING.id(), xp, true);
			}

			made++;
			updatebatch();
		}
		return made > 0;
	}

	private boolean natureRingSteadiesIronSmelt(Player player) {
		return false;
	}

	private boolean hasGoldsmithingBonus(Player player, int barId) {
		return barId == ItemId.GOLD_BAR.id()
			&& player.getCarriedItems().getEquipment().hasEquipped(ItemId.GAUNTLETS_OF_GOLDSMITHING.id())
			&& player.getCache().getInt("famcrest_gauntlets") == Gauntlets.GOLDSMITHING.id();
	}

	private void handleLavaFurnace(Player player, Item item) {
		int stage = player.getCache().hasKey("miniquest_dwarf_youth_rescue")
			? player.getCache().getInt("miniquest_dwarf_youth_rescue") : -1;
		if (stage != 2) {
			player.message("You don't have permission to use this");
			return;
		}
		int amount = getDragonMetalSmeltBars(item.getCatalogId());
		if (amount <= 0) {
			player.message("Nothing interesting happens");
			return;
		}
		if (getCurrentLevel(player, Skill.SMITHING.id()) < 90) {
			player.message("90 smithing is required to use this forge");
			return;
		}
		if (player.getCarriedItems().remove(new Item(item.getCatalogId())) > -1) {
			player.message("You smelt the " + item.getDef(player.getWorld()).getName() + "...");
			delay(5);
			player.message("And retrieve " + amount + " dragon bar" + (amount > 1 ? "s" : ""));
			give(player, ItemId.DRAGON_BAR.id(), amount);
		}
	}

	private int getDragonMetalSmeltBars(int itemId) {
		if (itemId == ItemId.DRAGON_SWORD.id()
			|| itemId == ItemId.DRAGON_AXE.id()
			|| itemId == ItemId.DRAGON_MEDIUM_HELMET.id()
			|| itemId == ItemId.RIGHT_HALF_DRAGON_SQUARE_SHIELD.id()
			|| itemId == ItemId.LEFT_HALF_DRAGON_SQUARE_SHIELD.id()
			|| itemId == ItemId.DRAGON_SQUARE_SHIELD.id()
			|| itemId == ItemId.DRAGON_2_HANDED_SWORD.id()
			|| itemId == ItemId.LARGE_DRAGON_HELMET.id()
			|| itemId == ItemId.DRAGON_KITE_SHIELD.id()
			|| itemId == ItemId.DRAGON_PLATE_MAIL_BODY.id()
			|| itemId == ItemId.DRAGON_PLATE_MAIL_TOP.id()
			|| itemId == ItemId.DRAGON_PLATE_MAIL_LEGS.id()
			|| itemId == ItemId.DRAGON_PLATED_SKIRT.id()
			|| itemId == ItemId.DRAGON_DAGGER.id()
			|| itemId == ItemId.POISONED_DRAGON_DAGGER.id()
			|| itemId == ItemId.DRAGON_WOODCUTTING_AXE.id()
			|| itemId == ItemId.DRAGON_BATTLE_AXE.id()) {
			return 1;
		}
		return 0;
	}

	private void handleCannonBallSmelting(Player player) {
		if (getCurrentLevel(player, Skill.SMITHING.id()) < 30) {
			player.message("You need at least level 30 smithing to make cannon balls");
			return;
		}
		if (player.getQuestStage(Quests.DWARF_CANNON) != -1) {
			player.message("You need to complete the dwarf cannon quest");
			return;
		}
		if (config().WANT_FATIGUE && config().STOP_SKILLING_FATIGUED >= 2
			&& player.getFatigue() >= player.MAX_FATIGUE) {
			player.message("You are too tired to smelt a cannon ball");
			return;
		}
		if (player.getCarriedItems().getInventory().countId(ItemId.STEEL_BAR.id(), Optional.of(false)) < 1) {
			player.message("You have no steel bars left");
			return;
		}

		thinkbubble(new Item(ItemId.MULTI_CANNON_BALL.id(), 1));
		int messagedelay = config().BATCH_PROGRESSION ? 1 : 2;
		mes("you heat the steel bar into a liquid state");
		delay(messagedelay);
		mes("and pour it into your cannon ball mould");
		delay(messagedelay);
		mes("you then leave it to cool for a short while");
		delay(messagedelay);

		player.getCarriedItems().remove(new Item(ItemId.STEEL_BAR.id()));
		if (config().WANT_FATIGUE && config().STOP_SKILLING_FATIGUED == 1
			&& player.getFatigue() >= player.MAX_FATIGUE) {
			player.message("you are too tired to lift the ammo");
			player.getWorld().registerItem(new GroundItem(player.getWorld(),
				ItemId.MULTI_CANNON_BALL.id(), player.getX(), player.getY(), 1, player));
			return;
		}
		player.incExp(Skill.SMITHING.id(), 100, true);
		player.getCarriedItems().getInventory().add(new Item(ItemId.MULTI_CANNON_BALL.id()));
		if (config().DWARVEN_RING_BONUS > 0) {
			player.getCarriedItems().getInventory().add(new Item(ItemId.MULTI_CANNON_BALL.id(), config().DWARVEN_RING_BONUS));
		}
		player.message("it's very heavy");

		updatebatch();
		if (!ifinterrupted() && !isbatchcomplete()) {
			player.message("you repeat the process");
			delay();
			handleCannonBallSmelting(player);
		}
	}

	private boolean isInFurnaceRange(Player player, GameObject obj) {
		if (obj.getLocation().equals(Point.location(399, 840))) {
			return !((player.getLocation().getY() == 841 && !player.withinRange(obj, 2))
				&& !player.withinRange90Deg(obj, 2));
		}
		return player.withinRange(obj, 1) || player.withinRange90Deg(obj, 2);
	}

	private static SmeltRecipe getRecipe(int itemId) {
		for (SmeltRecipe recipe : RECIPES) {
			if (recipe.barId == itemId) {
				return recipe;
			}
		}
		return null;
	}

	private static SmeltRecipe getDirectOreRecipe(int itemId) {
		if (itemId == ItemId.TIN_ORE.id()) {
			return getRecipe(ItemId.TIN_BAR.id());
		}
		if (itemId == ItemId.COPPER_ORE.id()) {
			return getRecipe(ItemId.COPPER_BAR.id());
		}
		if (itemId == ItemId.IRON_ORE.id()) {
			return getRecipe(ItemId.IRON_BAR.id());
		}
		if (itemId == ItemId.SILVER.id()) {
			return getRecipe(ItemId.SILVER_BAR.id());
		}
		if (itemId == ItemId.GOLD.id()) {
			return getRecipe(ItemId.GOLD_BAR.id());
		}
		if (itemId == ItemId.MITHRIL_ORE.id()) {
			return getRecipe(ItemId.MITHRIL_BAR.id());
		}
		if (itemId == ItemId.ADAMANTITE_ORE.id()) {
			return getRecipe(ItemId.ADAMANTITE_BAR.id());
		}
		if (itemId == ItemId.RUNITE_ORE.id()) {
			return getRecipe(ItemId.RUNITE_BAR.id());
		}
		return null;
	}

	private static int maxDirectSmelts(Player player, SmeltRecipe recipe) {
		int max = Integer.MAX_VALUE;
		for (Ingredient ingredient : recipe.ingredients) {
			max = Math.min(max, ingredient.availableCount(player) / ingredient.amount);
		}
		return Math.max(1, max == Integer.MAX_VALUE ? 1 : max);
	}

	private static Ingredient ingredient(int itemId, int amount) {
		return new Ingredient(itemId, -1, amount);
	}

	private static Ingredient either(int preferredItemId, int fallbackItemId, int amount) {
		return new Ingredient(preferredItemId, fallbackItemId, amount);
	}

	private static String getBarName(Player player, SmeltRecipe recipe) {
		return player.getWorld().getServer().getEntityHandler().getItemDef(recipe.barId)
			.getName().toLowerCase().replace("bar", "").trim();
	}

	@Override
	public boolean blockUseLoc(Player player, GameObject obj, Item item) {
		if (obj.getID() == FURNACE) {
			return item.getCatalogId() == ItemId.STEEL_BAR.id()
				|| DataConversions.inArray(NORMAL_SMELTING_ITEMS, item.getCatalogId());
		}
		return obj.getID() == LAVA_FURNACE && item.getCatalogId() != ItemId.TREATED_HIDE.id();
	}

	private static final class SmeltRecipe {
		private final int barId;
		private final int requiredLevel;
		private final int xp;
		private final int outputAmount;
		private final Ingredient[] ingredients;

		private SmeltRecipe(int barId, int requiredLevel, int xp, int outputAmount, Ingredient... ingredients) {
			this.barId = barId;
			this.requiredLevel = requiredLevel;
			this.xp = xp;
			this.outputAmount = outputAmount;
			this.ingredients = ingredients;
		}

		private int totalInputAmount() {
			int total = 0;
			for (Ingredient ingredient : ingredients) {
				total += ingredient.amount;
			}
			return total;
		}

		private boolean hasMaterials(Player player) {
			for (Ingredient ingredient : ingredients) {
				if (!ingredient.has(player)) {
					return false;
				}
			}
			return true;
		}

		private int[] ingredientItemIds() {
			int[] ids = new int[ingredients.length];
			for (int i = 0; i < ingredients.length; i++) {
				ids[i] = ingredients[i].itemId;
			}
			return ids;
		}

		private int[] ingredientFallbackItemIds() {
			int[] ids = new int[ingredients.length];
			for (int i = 0; i < ingredients.length; i++) {
				ids[i] = ingredients[i].fallbackItemId;
			}
			return ids;
		}

		private int[] ingredientAmounts() {
			int[] amounts = new int[ingredients.length];
			for (int i = 0; i < ingredients.length; i++) {
				amounts[i] = ingredients[i].amount;
			}
			return amounts;
		}

		private void consumeMaterials(Player player) {
			for (Ingredient ingredient : ingredients) {
				ingredient.consume(player);
			}
		}

		private String requirementMessage(Player player) {
			StringBuilder builder = new StringBuilder("You need ");
			for (int i = 0; i < ingredients.length; i++) {
				if (i > 0) {
					builder.append(i == ingredients.length - 1 ? " and " : ", ");
				}
				builder.append(ingredients[i].describe(player));
			}
			builder.append(" to smelt ").append(getBarName(player, this));
			return builder.toString();
		}

		private String startMessage(Player player) {
			return "You place the materials for " + getBarName(player, this) + " into the furnace";
		}
	}

	private static final class Ingredient {
		private final int itemId;
		private final int fallbackItemId;
		private final int amount;

		private Ingredient(int itemId, int fallbackItemId, int amount) {
			this.itemId = itemId;
			this.fallbackItemId = fallbackItemId;
			this.amount = amount;
		}

		private boolean has(Player player) {
			return count(player, itemId) >= amount || (fallbackItemId > -1 && count(player, fallbackItemId) >= amount);
		}

		private void consume(Player player) {
			int selectedItemId = count(player, itemId) >= amount ? itemId : fallbackItemId;
			for (int i = 0; i < amount; i++) {
				player.getCarriedItems().remove(new Item(selectedItemId));
			}
		}

		private String describe(Player player) {
			String name = player.getWorld().getServer().getEntityHandler().getItemDef(itemId).getName().toLowerCase();
			if (fallbackItemId > -1) {
				name += " or " + player.getWorld().getServer().getEntityHandler().getItemDef(fallbackItemId).getName().toLowerCase();
			}
			return amount + " " + name;
		}

		private static int count(Player player, int itemId) {
			return player.getCarriedItems().getInventory().countId(itemId, Optional.of(false));
		}

		private int availableCount(Player player) {
			int available = count(player, itemId);
			if (fallbackItemId > -1) {
				available = Math.max(available, count(player, fallbackItemId));
			}
			return available;
		}
	}
}
