package com.openrsc.server.plugins.authentic.skills.crafting;

import com.google.common.collect.ImmutableMap;
import com.openrsc.server.ServerConfiguration;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.production.ProductionRecipe;
import com.openrsc.server.content.production.ProductionSession;
import com.openrsc.server.content.production.ProductionStarter;
import com.openrsc.server.external.ItemCraftingDef;
import com.openrsc.server.external.ItemGemDef;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.CarriedItems;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.UseInvTrigger;
import com.openrsc.server.plugins.triggers.UseLocTrigger;
import com.openrsc.server.plugins.authentic.skills.smithing.Smelting;
import com.openrsc.server.plugins.authentic.skills.smithing.ShiloFurnaceAccess;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;
import com.openrsc.server.util.rsc.MathUtil;
import com.openrsc.server.util.rsc.MessageType;
import org.apache.commons.lang.StringUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.openrsc.server.plugins.Functions.*;

public class Crafting implements UseInvTrigger,
	UseLocTrigger {

	/**
	 * World instance
	 */
	private int[] gemsThatFail = new int[]{
		ItemId.UNCUT_RED_TOPAZ.id(),
		ItemId.UNCUT_JADE.id(),
		ItemId.UNCUT_OPAL.id(),
	};
	private int[] itemsFurnance = new int[]{
		ItemId.SILVER_BAR.id(),
		ItemId.GOLD_BAR.id(),
		ItemId.SODA_ASH.id(),
		ItemId.SAND.id(),
		ItemId.GOLD_BAR_FAMILYCREST.id(),
	};
	private int[] itemsOven = new int[]{
		ItemId.UNFIRED_POT.id(),
		ItemId.UNFIRED_PIE_DISH.id(),
		ItemId.UNFIRED_BOWL.id()
	};
	private static final int[] MODERN_CASTING_BARS = {
		ItemId.TIN_BAR.id(), ItemId.COPPER_BAR.id(), ItemId.BRONZE_BAR.id(),
		ItemId.IRON_BAR.id(), ItemId.STEEL_BAR.id(), ItemId.MITHRIL_BAR.id(),
		ItemId.TITAN_STEEL_BAR.id(), ItemId.ADAMANTITE_BAR.id(),
		ItemId.ORICHALCUM_BAR.id(), ItemId.RUNITE_BAR.id()
	};

	private static final String ring = "ring";
	private static final String Necklace = "Necklace";
	private static final String amulet = "amulet";
	private static final String Gold = "Gold";
	private static final String Sapphire = "Sapphire";
	private static final String Emerald = "Emerald";
	private static final String Ruby = "Ruby";
	private static final String Diamond = "Diamond";
	private static final String Dragonstone = "Dragonstone";
	private static final String dragonstone = "dragonstone";

	private enum JewelryCategory {
		ALL,
		RINGS,
		NECKLACES,
		AMULETS
	}

	private static final Map<String, Mould> goldMoulds = new ImmutableMap.Builder<String, Mould>()
		.put(ring, new Mould("ring", ItemId.RING_MOULD.id(), "You need a ring mould to make a gold ring"))
		.put(Necklace, new Mould("Necklace", ItemId.NECKLACE_MOULD.id(), "You need a necklace mould to make a gold necklace"))
		.put(amulet, new Mould("amulet", ItemId.AMULET_MOULD.id(), "You need an amulet mould to make a gold amulet"))
		.build();

	private static final class HideArmorRecipe {
		private final int materialId;
		private final String materialName;
		private final int tier;
		private final int equipLevel;
		private final int coifId;
		private final int glovesId;
		private final int bootsId;
		private final int chapsId;
		private final int cuirassId;

		private HideArmorRecipe(int materialId, String materialName, int tier, int equipLevel,
			int coifId, int glovesId, int bootsId, int chapsId, int cuirassId) {
			this.materialId = materialId;
			this.materialName = materialName;
			this.tier = tier;
			this.equipLevel = equipLevel;
			this.coifId = coifId;
			this.glovesId = glovesId;
			this.bootsId = bootsId;
			this.chapsId = chapsId;
			this.cuirassId = cuirassId;
		}
	}

	private static final class HideArmorPiece {
		private final String optionName;
		private final int resultId;
		private final int materialCost;
		private final int reqLvl;
		private final int exp;

		private HideArmorPiece(String optionName, int resultId, int materialCost, int reqLvl, int exp) {
			this.optionName = optionName;
			this.resultId = resultId;
			this.materialCost = materialCost;
			this.reqLvl = reqLvl;
			this.exp = exp;
		}
	}

	private static final class WoolGarmentRecipe {
		private final String optionName;
		private final int resultId;
		private final int woolCost;
		private final int reqLvl;
		private final int exp;

		private WoolGarmentRecipe(String optionName, int resultId, int woolCost, int reqLvl, int exp) {
			this.optionName = optionName;
			this.resultId = resultId;
			this.woolCost = woolCost;
			this.reqLvl = reqLvl;
			this.exp = exp;
		}
	}

	private static final class PotteryRecipe {
		private final String optionName;
		private final int resultId;
		private final int reqLvl;
		private final int exp;
		private final boolean gatedByItemSupport;

		private PotteryRecipe(String optionName, int resultId, int reqLvl, int exp, boolean gatedByItemSupport) {
			this.optionName = optionName;
			this.resultId = resultId;
			this.reqLvl = reqLvl;
			this.exp = exp;
			this.gatedByItemSupport = gatedByItemSupport;
		}
	}

	private static final class GlassBlowingRecipe {
		private final String optionName;
		private final int resultId;
		private final int reqLvl;
		private final int exp;
		private final String resultGen;

		private GlassBlowingRecipe(String optionName, int resultId, int reqLvl, int exp, String resultGen) {
			this.optionName = optionName;
			this.resultId = resultId;
			this.reqLvl = reqLvl;
			this.exp = exp;
			this.resultGen = resultGen;
		}
	}

	private static final class RangedMouldRecipe {
		private final String optionName;
		private final int resultId;
		private final int mouldId;
		private final int amount;
		private final int reqLvl;
		private final int exp;

		private RangedMouldRecipe(String optionName, int resultId, int mouldId, int amount, int reqLvl, int exp) {
			this.optionName = optionName;
			this.resultId = resultId;
			this.mouldId = mouldId;
			this.amount = amount;
			this.reqLvl = reqLvl;
			this.exp = exp;
		}
	}

	private static final WoolGarmentRecipe[] WOOL_GARMENT_RECIPES = {
		new WoolGarmentRecipe("Wool hat", ItemId.WOOL_WIZARD_HAT.id(), 1, 1, 6),
		new WoolGarmentRecipe("Wool gloves", ItemId.WOOL_GLOVES.id(), 2, 1, 12),
		new WoolGarmentRecipe("Wool boots", ItemId.WOOL_BOOTS.id(), 2, 1, 12),
		new WoolGarmentRecipe("Wool robe top", ItemId.WOOL_ROBE_TOP.id(), 4, 1, 24),
		new WoolGarmentRecipe("Wool robe bottom", ItemId.WOOL_ROBE_SKIRT.id(), 3, 1, 18),
		new WoolGarmentRecipe("White cape", ItemId.WOOL_CAPE.id(), 2, 1, 12),
	};

	private static final PotteryRecipe[] POTTERY_RECIPES = {
		new PotteryRecipe("Pie dish", ItemId.UNFIRED_PIE_DISH.id(), 4, 60, false),
		new PotteryRecipe("Pot", ItemId.UNFIRED_POT.id(), 1, 25, false),
		new PotteryRecipe("Bowl", ItemId.UNFIRED_BOWL.id(), 7, 40, true),
	};

	private static final GlassBlowingRecipe[] GLASS_BLOWING_RECIPES = {
		new GlassBlowingRecipe("Vial", ItemId.EMPTY_VIAL.id(), 33, 140, "vials"),
		new GlassBlowingRecipe("Beer glass", ItemId.BEER_GLASS.id(), 1, 70, "beer glasses"),
	};

	private static final int BROWN_APRON_COW_HIDE_COST = 2;
	private static final int BROWN_APRON_CRAFTING_LEVEL = 1;
	private static final int BROWN_APRON_CRAFTING_EXP = 12;
	private static final int THREAD_USES_PER_LEATHER_MATERIAL = 2;

	public final static int[] silver_moulds = {
		ItemId.HOLY_SYMBOL_MOULD.id(), // "You need a Holy symbol mould to make a holy symbol!"
		ItemId.UNHOLY_SYMBOL_MOULD.id(),
		ItemId.GUTHIX_SYMBOL_MOULD.id(),
	};

	public final static int[] gems = {
		ItemId.NOTHING.id(),
		ItemId.SAPPHIRE.id(),
		ItemId.EMERALD.id(),
		ItemId.RUBY.id(),
		ItemId.DIAMOND.id(),
		ItemId.DRAGONSTONE.id(),
		ItemId.OPAL.id(),
	};

	@Override
	public void onUseInv(Player player, Integer invIndex, Item item1, Item item2) {
		int item1ID = item1.getCatalogId();
		int item2ID = item2.getCatalogId();
		CarriedItems carriedItems = player.getCarriedItems();
		Inventory inventory = carriedItems.getInventory();
		if (item1ID == ItemId.CHISEL.id()) {
			doCutGem(player, item1, item2);
		} else if (item2ID == ItemId.CHISEL.id()) {
			doCutGem(player, item2, item1);
		} else if (item1ID == ItemId.GLASSBLOWING_PIPE.id()) {
			doGlassBlowing(player, item1, item2);
		} else if (item2ID == ItemId.GLASSBLOWING_PIPE.id()) {
			doGlassBlowing(player, item2, item1);
		} else if (item1ID == ItemId.NEEDLE.id()) {
			if (item2ID == ItemId.TEDDY_HEAD.id() || item2ID == ItemId.TEDDY_BOTTOM.id()) {
				makeTeddy(player, inventory, carriedItems);
			} else if (item2ID == ItemId.BALL_OF_WOOL.id()) {
				beginWoolCrafting(player);
			} else {
				makeLeather(player, item1, item2);
			}
		} else if (item2ID == ItemId.NEEDLE.id()) {
			if (item1ID == ItemId.TEDDY_HEAD.id() || item1ID == ItemId.TEDDY_BOTTOM.id()) {
				makeTeddy(player, inventory, carriedItems);
			} else if (item1ID == ItemId.BALL_OF_WOOL.id()) {
				beginWoolCrafting(player);
			} else {
				makeLeather(player, item2, item1);
			}
		} else if (item1ID == ItemId.BALL_OF_WOOL.id()) {
			useWool(player, item1, item2);
		} else if (item2ID == ItemId.BALL_OF_WOOL.id()) {
			useWool(player, item2, item1);
		} else if ((item1ID == ItemId.BUCKET_OF_WATER.id() || item1ID == ItemId.JUG_OF_WATER.id()) && item2ID == ItemId.CLAY.id()) {
			useWater(player, item1, item2);
		} else if ((item2ID == ItemId.BUCKET_OF_WATER.id() || item2ID == ItemId.JUG_OF_WATER.id()) && item1ID == ItemId.CLAY.id()) {
			useWater(player, item2, item1);
		} else if (item1ID == ItemId.MOLTEN_GLASS.id() && item2ID == ItemId.LENS_MOULD.id() || item1ID == ItemId.LENS_MOULD.id() && item2ID == ItemId.MOLTEN_GLASS.id()) {
			if (getQuestStage(player, Quests.OBSERVATORY_QUEST) >= 0 && getQuestStage(player, Quests.OBSERVATORY_QUEST) < 5) {
				say(player, null, "Perhaps I should speak to the professor first");
				return;
			}
			if (getCurrentLevel(player, Skill.CRAFTING.id()) < 10) {
				player.message("Sorry, you need a crafting level");
				player.message("Of 10 or above to use this object");
				//Authentically bugged and wouldn't stop the player from actually making the lens.
			}
			if (carriedItems.remove(new Item(ItemId.MOLTEN_GLASS.id())) > -1) {
				player.message("You pour the molten glass into the mould");
				player.message("And clasp it together");
				player.message("It produces a small convex glass disc");
				inventory.add(new Item(ItemId.LENS.id()));
			}
		} else {
			player.message("Nothing interesting happens");
		}
	}

	private void makeTeddy(Player player, Inventory inventory, CarriedItems carriedItems) {
		if (inventory.hasInInventory(ItemId.TEDDY_HEAD.id())
			&& inventory.hasInInventory(ItemId.TEDDY_BOTTOM.id())
			&& inventory.hasInInventory(ItemId.THREAD.id())) {
			if (getCurrentLevel(player, Skill.CRAFTING.id()) < 15) {
				player.message("You need level 15 crafting to fix the teddy");
				return;
			}

			int stage = player.getCache().hasKey("miniquest_dwarf_youth_rescue") ? player.getCache().getInt("miniquest_dwarf_youth_rescue") : -1;
			if (stage < 1) {
				player.message("I'd better get these parts back to the kid");
				return;
			}
			carriedItems.remove(new Item(ItemId.TEDDY_HEAD.id()));
			carriedItems.remove(new Item(ItemId.TEDDY_BOTTOM.id()));
			carriedItems.remove(new Item(ItemId.THREAD.id()));
			carriedItems.getInventory().add(new Item(ItemId.TEDDY.id()));
			player.message("You stitch together the teddy parts");

		} else {
			player.message("You need the two teddy halves and some thread");
		}
	}

	@Override
	public void onUseLoc(final Player player, GameObject obj, final Item item) {
		if (!craftingChecks(obj, item, player)) return;
		beginCrafting(item, player);
	}

	private boolean craftingChecks(final GameObject obj, final Item item, final Player player) {
		// allowed item on crafting game objects
		if (!craftingTypeChecks(obj, item, player)) return false;

		if (item.getItemStatus().getNoted()) return false;

		if (ShiloFurnaceAccess.isShiloFurnace(obj)) {
			// furnace in shilo village
			if ((player.getLocation().getY() == 841 && !player.withinRange(obj, 2)) && !player.withinRange90Deg(obj, 2)) {
				return false;
			}
		} else {
			// some furnaces the player is 2 spaces away
			if (!player.withinRange(obj, 1) && !player.withinRange90Deg(obj, 2)) {
				return false;
			}
		}
		if (ShiloFurnaceAccess.isShiloFurnace(obj) && !ShiloFurnaceAccess.ensureUnlocked(player)) {
			return false;
		}

		if (item.getCatalogId() == ItemId.SODA_ASH.id() || item.getCatalogId() == ItemId.SAND.id()) { // Soda Ash or Sand (Glass)
			if (player.getCarriedItems().getInventory().countId(ItemId.SODA_ASH.id(), Optional.of(false)) < 1) {
				player.playerServerMessage(MessageType.QUEST, "You need some soda ash to make glass");
				return false;
			} else if (player.getCarriedItems().getInventory().countId(ItemId.SAND.id(), Optional.of(false)) < 1) {
				player.playerServerMessage(MessageType.QUEST, "You need some sand to make glass");
				return false;
			}
		}

		return true;
	}

	private boolean craftingTypeChecks(final GameObject obj, final Item item, final Player player) {
		boolean furnace = obj.getID() == SceneryId.FURNACE.id()|| obj.getID() == SceneryId.FURNACE_UNDERGROUND_PASS.id();
		boolean furnaceItem = DataConversions.inArray(itemsFurnance, item.getCatalogId());
		boolean rangedCastingBar = isModernMetalBar(item.getCatalogId());
		boolean jewelryBar = item.getCatalogId() == ItemId.SILVER_BAR.id() || item.getCatalogId() == ItemId.GOLD_BAR.id();
		boolean potteryOven = obj.getID() == SceneryId.POTTERY_OVEN.id();
		boolean potteryItem = DataConversions.inArray(itemsOven, item.getCatalogId());
		boolean potteryWheel = obj.getID() == SceneryId.POTTERY_WHEEL.id();
		boolean softClay = item.getCatalogId() == ItemId.SOFT_CLAY.id();

		// Checks to make sure you're using the right item with the right object.
		return (furnace && (furnaceItem || rangedCastingBar))
			|| (potteryOven && potteryItem)
			|| (potteryWheel && softClay);
	}

	private void beginCrafting(final Item item, final Player player) {
		if (item.getCatalogId() == ItemId.SODA_ASH.id() || item.getCatalogId() == ItemId.SAND.id()) {
			doGlassMaking(item, player);
			return;
		} else if (DataConversions.inArray(itemsOven, item.getCatalogId())) {
			doPotteryFiring(item, player);
			return;
		}

		if (isModernMetalBar(item.getCatalogId())) {
			doRangedMouldCasting(item, player);
			return;
		}

		if (item.getCatalogId() == ItemId.SOFT_CLAY.id() && !ActionSender.isRetroClient(player)) {
			ProductionSession session = createPotteryProductionSession(player);
			if (session != null) {
				if (!session.hasAnyCraftableRecipe()) {
					player.message("You are not skilled enough for that yet");
					return;
				}
				player.setAttribute("production_session", session);
				player.setAttribute("production_starter", (ProductionStarter) Crafting::beginProductionFromInterface);
				ActionSender.showProductionInterface(player, session);
				return;
			}
		}

		player.playerServerMessage(MessageType.QUEST,"what would you like to make");

		int maxItemId = player.getConfig().RESTRICT_ITEM_ID;
		if (item.getCatalogId() == ItemId.GOLD_BAR.id() || item.getCatalogId() == ItemId.GOLD_BAR_FAMILYCREST.id()) {
			doGoldJewelry(item, player);
		} else if (item.getCatalogId() == ItemId.SILVER_BAR.id()) {
			doSilverJewelry(item, player);
		} else if (item.getCatalogId() == ItemId.SOFT_CLAY.id() && MathUtil.maxUnsigned(maxItemId, ItemId.UNFIRED_POT.id()) == maxItemId) {
			doPotteryMolding(item, player);
		}
	}

	private void doGoldJewelry(final Item goldBarItem, final Player player) {
		ItemCraftingDef def = null;
		if (!ActionSender.isRetroClient(player)
			&& player.getConfig().WANT_BETTER_JEWELRY_CRAFTING
			&& goldBarItem.getCatalogId() == ItemId.GOLD_BAR.id()) {
			ProductionSession session = createGoldJewelryProductionSession(player);
			if (session != null) {
				if (!session.hasAnyCraftableRecipe()) {
					player.message("You are not skilled enough for that yet");
					return;
				}
				player.setAttribute("production_session", session);
				player.setAttribute("production_starter", (ProductionStarter) Crafting::beginProductionFromInterface);
				ActionSender.showProductionInterface(player, session);
				return;
			}
		}
		if (player.getConfig().WANT_BETTER_JEWELRY_CRAFTING) {
			if (!player.getCarriedItems().hasCatalogID(ItemId.AMULET_MOULD.id()) &&
				!player.getCarriedItems().hasCatalogID(ItemId.NECKLACE_MOULD.id()) &&
				!player.getCarriedItems().hasCatalogID(ItemId.RING_MOULD.id())) {
				player.message("You need a mould to craft jewelry");
				return;
			}
			def = getDesiredGoldCraftingAutoDetection(goldBarItem, player);
			if (def == null) {
				// No definition found
				player.message("Nothing interesting happens");
				return;
			}
		} else {
			def = getDesiredGoldCraftingAuthentic(goldBarItem, player);
			if (def == null) {
				return;
			}
		}

		if (def.itemID == ItemId.NOTHING.id()) {
		    // not an authentic message
			player.message("You have no reason to make that item.");
			return;
		}

		int repeat = 1;
		if (player.getConfig().BATCH_PROGRESSION) {
			if (goldBarItem.getCatalogId() != ItemId.GOLD_BAR_FAMILYCREST.id()) { // Perfect gold bars shouldn't be batched
				int mostThatCouldBeMade = 0;
				if (def.getReqGem() == ItemId.NOTHING.id()) {
					mostThatCouldBeMade = player.getCarriedItems().getInventory().countId(goldBarItem.getCatalogId(), Optional.of(false));
				} else {
					mostThatCouldBeMade = Math.min(
						player.getCarriedItems().getInventory().countId(def.getReqGem(), Optional.of(false)),
						player.getCarriedItems().getInventory().countId(goldBarItem.getCatalogId(), Optional.of(false))
					);
				}
				if (mostThatCouldBeMade > 1) {
					int howMany = multi(player, "Make all", "Make 1", "Make 3", "Make 5", "Make 10", "Make all but one");
					switch (howMany) {
						case 1:
							repeat = Math.min(1, mostThatCouldBeMade);
							break;
						case 2:
							repeat = Math.min(3, mostThatCouldBeMade);
							break;
						case 3:
							repeat = Math.min(5, mostThatCouldBeMade);
							break;
						case 4:
							repeat = Math.min(10, mostThatCouldBeMade);
							break;
						case 5:
							if (mostThatCouldBeMade > 1) {
								repeat = mostThatCouldBeMade - 1;
							} else {
								player.playerServerMessage(MessageType.QUEST, "Okay, all done making zero of your item.");
								return;
							}
							break;
						case 0:
							repeat = mostThatCouldBeMade;
					}
				} else {
					repeat = Math.min(1, mostThatCouldBeMade);
				}
			}
		}

		startbatch(repeat);
		batchGoldJewelry(player, goldBarItem, def);
	}

	/* determine all possible jewelry that can be crafted by a player
	   and display it to the user in a list ordered such that the highest xp products
	   are at the top
	 */
	private ItemCraftingDef getDesiredGoldCraftingAutoDetection(Item item, Player player) {
		ArrayList<String> options = new ArrayList<>();
		ArrayList<Integer> itemIds = new ArrayList<>();
		if (player.getCarriedItems().hasCatalogID(ItemId.AMULET_MOULD.id())) {
			if (player.getCarriedItems().hasCatalogID(ItemId.DRAGONSTONE.id())) {
				options.add("Dragonstone amulet");
				itemIds.add(ItemId.UNSTRUNG_DRAGONSTONE_AMULET.id());
			}
			if (player.getCarriedItems().hasCatalogID(ItemId.DIAMOND.id())) {
				options.add("Diamond amulet");
				itemIds.add(ItemId.UNSTRUNG_DIAMOND_AMULET.id());
			}
			if (player.getCarriedItems().hasCatalogID(ItemId.RUBY.id())) {
				options.add("Ruby amulet");
				itemIds.add(ItemId.UNSTRUNG_RUBY_AMULET.id());
			}
			if (player.getCarriedItems().hasCatalogID(ItemId.EMERALD.id())) {
				options.add("Emerald amulet");
				itemIds.add(ItemId.UNSTRUNG_EMERALD_AMULET.id());
			}
			if (player.getCarriedItems().hasCatalogID(ItemId.SAPPHIRE.id())) {
				options.add("Sapphire amulet");
				itemIds.add(ItemId.UNSTRUNG_SAPPHIRE_AMULET.id());
			}
			options.add("Gold amulet");
			itemIds.add(ItemId.UNSTRUNG_GOLD_AMULET.id());
		}
		if (player.getCarriedItems().hasCatalogID(ItemId.NECKLACE_MOULD.id())) {
			if (player.getCarriedItems().hasCatalogID(ItemId.DRAGONSTONE.id())) {
				options.add("Dragonstone necklace");
				itemIds.add(ItemId.DRAGONSTONE_NECKLACE.id());
			}
			if (player.getCarriedItems().hasCatalogID(ItemId.DIAMOND.id())) {
				options.add("Diamond necklace");
				itemIds.add(ItemId.DIAMOND_NECKLACE.id());
			}
			if (player.getCarriedItems().hasCatalogID(ItemId.RUBY.id())) {
				options.add("Ruby necklace");
				itemIds.add(ItemId.RUBY_NECKLACE.id());
			}
			if (player.getCarriedItems().hasCatalogID(ItemId.EMERALD.id())) {
				options.add("Emerald necklace");
				itemIds.add(ItemId.EMERALD_NECKLACE.id());
			}
			if (player.getCarriedItems().hasCatalogID(ItemId.SAPPHIRE.id())) {
				options.add("Sapphire necklace");
				itemIds.add(ItemId.SAPPHIRE_NECKLACE.id());
			}
			options.add("Gold necklace");
			itemIds.add(ItemId.GOLD_NECKLACE.id());
		}
		if (player.getCarriedItems().hasCatalogID(ItemId.RING_MOULD.id())) {
			if (player.getCarriedItems().hasCatalogID(ItemId.DRAGONSTONE.id())) {
				options.add("Dragonstone ring");
				itemIds.add(ItemId.DRAGONSTONE_RING.id());
			}
			if (player.getCarriedItems().hasCatalogID(ItemId.DIAMOND.id())) {
				options.add("Diamond ring");
				itemIds.add(ItemId.DIAMOND_RING.id());
			}
			if (player.getCarriedItems().hasCatalogID(ItemId.RUBY.id())) {
				options.add("Ruby ring");
				itemIds.add(ItemId.RUBY_RING.id());
			}
			if (player.getCarriedItems().hasCatalogID(ItemId.EMERALD.id())) {
				options.add("Emerald ring");
				itemIds.add(ItemId.EMERALD_RING.id());
			}
			if (player.getCarriedItems().hasCatalogID(ItemId.SAPPHIRE.id())) {
				options.add("Sapphire ring");
				itemIds.add(ItemId.SAPPHIRE_RING.id());
			}
			options.add("Gold ring");
			itemIds.add(ItemId.GOLD_RING.id());
		}

		thinkbubble(new Item(ItemId.GOLD_BAR.id())); // bubble will be displayed after menu
		if (options.size() == 0) {
			player.playerServerMessage(MessageType.QUEST, "You do not have any moulds...!");
			return null;
		}
		String[] finalOptions = new String[Math.min(options.size(), player.getClientLimitations().maxDialogueOptions)];
		System.arraycopy(options.toArray(), 0, finalOptions, 0, Math.min(finalOptions.length, options.size()));
		int menu = multi(player, finalOptions);
		if (menu < 0 || menu > finalOptions.length) {
			return null;
		}
		return player.getWorld().getServer().getEntityHandler().getCraftingDef(itemIds.get(menu));
	}

	private ItemCraftingDef getDesiredGoldCraftingAuthentic(Item item, Player player) {
		// select type
		String[] options;
		if (!player.getConfig().WANT_EQUIPMENT_TAB) { // TODO: this is not a very good way to detect other than Cabbage server config
			options = new String[]{
				ring,
				Necklace,
				amulet
			};
		} else {
			options = new String[]{
				ring,
				Necklace,
				amulet
			};
		}

		thinkbubble(new Item(ItemId.GOLD_BAR.id())); // bubble will be displayed after menu
		int shapeSelection = multi(player, options);
		if (shapeSelection < 0 || shapeSelection > options.length - 1) {
			return null;
		}
		String jewelryShape = options[shapeSelection];

		if (!hasRequiredMould(player, jewelryShape)) return null;

		boolean gemUsed = false;
		if (!player.getConfig().WANT_EQUIPMENT_TAB) { // TODO: this is not a very good way to detect other than Cabbage server config
			player.playerServerMessage(MessageType.QUEST,
				"Would you like to put a gem in the " + jewelryShape.toLowerCase() + "?");
			options = new String[]{
				"Yes",
				"No"
			};
			int gemUsedOption = multi(player, options);
			if (gemUsedOption == -1) return null;
			gemUsed = gemUsedOption == 0;
		}

		// select gem
		options = new String[]{
			Sapphire,
			Emerald,
			Ruby,
			Diamond
		};
		if (player.getConfig().MEMBER_WORLD) {
			if (player.getConfig().WANT_EQUIPMENT_TAB) { // TODO: this is not a very good way to detect Cabbage server config
				options = new String[]{
					Gold,
					Sapphire,
					Emerald,
					Ruby,
					Diamond,
					Dragonstone
				};
			} else {
				options = new String[]{
					Sapphire,
					Emerald,
					Ruby,
					Diamond,
					dragonstone
				};

				// Dragonstone should be capitalized only when making a Necklace
				if (jewelryShape.equals(Necklace)) {
					options[4] = Dragonstone;
				}
			}
		}

		String gem = Gold;
		if (gemUsed) {
			player.playerServerMessage(MessageType.QUEST, "what sort of gem do you want to put in the " + jewelryShape + "?");
			int gemMultiSelection = multi(player, options);
			if (gemMultiSelection < 0 || gemMultiSelection > options.length)
				return null;

			gem = options[gemMultiSelection];
		}

		return getCraftingDefByShapeAndGem(player, jewelryShape, gem);
	}

	private ItemCraftingDef getCraftingDefByShapeAndGem(Player player, String shape, String gem) {
		int craftingProductItemId = -1;
		switch (shape) {
			case ring: {
				switch (gem) {
					case Gold:
						craftingProductItemId = ItemId.GOLD_RING.id();
						break;
					case Sapphire:
						craftingProductItemId = ItemId.SAPPHIRE_RING.id();
						break;
					case Emerald:
						craftingProductItemId = ItemId.EMERALD_RING.id();
						break;
					case Ruby:
						craftingProductItemId = ItemId.RUBY_RING.id();
						break;
					case Diamond:
						craftingProductItemId = ItemId.DIAMOND_RING.id();
						break;
					case Dragonstone:
					case dragonstone:
						craftingProductItemId = ItemId.DRAGONSTONE_RING.id();
						break;
				}
				break;
			}
			case Necklace: {
				switch (gem) {
					case Gold:
						craftingProductItemId = ItemId.GOLD_NECKLACE.id();
						break;
					case Sapphire:
						craftingProductItemId = ItemId.SAPPHIRE_NECKLACE.id();
						break;
					case Emerald:
						craftingProductItemId = ItemId.EMERALD_NECKLACE.id();
						break;
					case Ruby:
						craftingProductItemId = ItemId.RUBY_NECKLACE.id();
						break;
					case Diamond:
						craftingProductItemId = ItemId.DIAMOND_NECKLACE.id();
						break;
					case Dragonstone:
					case dragonstone:
						craftingProductItemId = ItemId.DRAGONSTONE_NECKLACE.id();
						break;
				}
				break;
			}
			case amulet: {
				switch (gem) {
					case Gold:
						craftingProductItemId = ItemId.UNSTRUNG_GOLD_AMULET.id();
						break;
					case Sapphire:
						craftingProductItemId = ItemId.UNSTRUNG_SAPPHIRE_AMULET.id();
						break;
					case Emerald:
						craftingProductItemId = ItemId.UNSTRUNG_EMERALD_AMULET.id();
						break;
					case Ruby:
						craftingProductItemId = ItemId.UNSTRUNG_RUBY_AMULET.id();
						break;
					case Diamond:
						craftingProductItemId = ItemId.UNSTRUNG_DIAMOND_AMULET.id();
						break;
					case Dragonstone:
					case dragonstone:
						craftingProductItemId = ItemId.UNSTRUNG_DRAGONSTONE_AMULET.id();
						break;
				}
				break;
			}
			default:
				return null;
		}

		return player.getWorld().getServer().getEntityHandler().getCraftingDef(craftingProductItemId);

	}

	private boolean hasRequiredMould(Player player, String jewelryShape) {
		if (player.getCarriedItems().getInventory().countId(goldMoulds.get(jewelryShape).itemId, Optional.of(false)) < 1) {
			player.playerServerMessage(MessageType.QUEST, goldMoulds.get(jewelryShape).failString);
			return false;
		}
		return true;
	}

	private void batchGoldJewelry(Player player, Item item, ItemCraftingDef def) {
		if (!canReceive(player, new Item(def.getItemID()))) {
			player.message("Your client does not support the desired object");
			return;
		}
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < def.getReqLevel()) {
			player.playerServerMessage(MessageType.QUEST, "You need a crafting skill of level " + def.getReqLevel() + " to make this");
			return;
		}
		if (checkFatigue(player)) return;

        // Get last gold bar in inventory.
        Item goldBar = player.getCarriedItems().getInventory().get(
            player.getCarriedItems().getInventory().getLastIndexById(item.getCatalogId(), Optional.of(false))
        );
        if (goldBar == null) {
            // this message is inauthentic; authentically can't happen b/c there's no batching
            player.message("You don't have a gold bar.");
            return;
        }

        Item result;
        if (goldBar.getCatalogId() == ItemId.GOLD_BAR_FAMILYCREST.id() && def.getItemID() == ItemId.RUBY_RING.id()) {
            result = new Item(ItemId.RUBY_RING_FAMILYCREST.id(), 1);
        } else if (goldBar.getCatalogId() == ItemId.GOLD_BAR_FAMILYCREST.id() && def.getItemID() == ItemId.RUBY_NECKLACE.id()) {
            result = new Item(ItemId.RUBY_NECKLACE_FAMILYCREST.id(), 1);
        } else {
            result = new Item(def.getItemID(), 1);
        }

        // Get last gem in inventory.
		while (!ifinterrupted() && !isbatchcomplete()) {
			goldBar = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(item.getCatalogId(), Optional.of(false))
			);
			if (goldBar == null) {
				break;
			}

			delay(3);
			if (def.getReqGem() != ItemId.NOTHING.id()) {
				Item gemItem = player.getCarriedItems().getInventory().get(
					player.getCarriedItems().getInventory().getLastIndexById(def.getReqGem(), Optional.of(false))
				);
				if (gemItem == null) {
					tellPlayerNoGem(player, def);
					break;
				}
			}

			tellPlayerSuccessfullyProducedCraftingProduct(player, def);
			player.getCarriedItems().remove(goldBar);
			if (def.getReqGem() != ItemId.NOTHING.id()) {
				player.getCarriedItems().remove(new Item(def.getReqGem()));
			}
			player.getCarriedItems().getInventory().add(result);
			player.incExp(Skill.CRAFTING.id(), def.getExp(), true);
			updatebatch();
		}
	}

	private void tellPlayerSuccessfullyProducedCraftingProduct(Player player, ItemCraftingDef def) {
		switch (ItemId.getById(def.getItemID())) {
			case GOLD_RING:
				player.playerServerMessage(MessageType.QUEST, "You make a gold ring");
				return;
			case GOLD_NECKLACE:
				player.playerServerMessage(MessageType.QUEST, "You make a gold necklace");
				return;
			case UNSTRUNG_GOLD_AMULET:
				player.playerServerMessage(MessageType.QUEST, "You make a gold amulet");
				return;
			case SAPPHIRE_RING:
				player.playerServerMessage(MessageType.QUEST, "You make a Sapphire ring");
				return;
			case SAPPHIRE_NECKLACE:
				player.playerServerMessage(MessageType.QUEST, "You make a Sapphire necklace");
				return;
			case UNSTRUNG_SAPPHIRE_AMULET:
				player.playerServerMessage(MessageType.QUEST, "You make a Sapphire amulet");
				return;
			case EMERALD_RING:
				player.playerServerMessage(MessageType.QUEST, "You make an Emerald ring");
				return;
			case EMERALD_NECKLACE:
				player.playerServerMessage(MessageType.QUEST, "You make an Emerald necklace");
				return;
			case UNSTRUNG_EMERALD_AMULET:
				player.playerServerMessage(MessageType.QUEST, "You make an Emerald amulet");
				return;
			case RUBY_RING:
				player.playerServerMessage(MessageType.QUEST, "You make a ruby ring");
				return;
			case RUBY_NECKLACE:
				player.playerServerMessage(MessageType.QUEST, "You make a ruby necklace");
				return;
			case UNSTRUNG_RUBY_AMULET:
				player.playerServerMessage(MessageType.QUEST, "You make a ruby amulet");
				return;
			case DIAMOND_RING:
				player.playerServerMessage(MessageType.QUEST, "You make a diamond ring");
				return;
			case DIAMOND_NECKLACE:
				player.playerServerMessage(MessageType.QUEST, "You make a diamond necklace");
				return;
			case UNSTRUNG_DIAMOND_AMULET:
				player.playerServerMessage(MessageType.QUEST, "You make a diamond amulet");
				return;
			case DRAGONSTONE_RING:
				player.playerServerMessage(MessageType.QUEST, "You make a dragonstone ring");
				return;
			case DRAGONSTONE_NECKLACE:
				player.playerServerMessage(MessageType.QUEST, "You make a dragonstone necklace");
				return;
			case UNSTRUNG_DRAGONSTONE_AMULET:
				player.playerServerMessage(MessageType.QUEST, "You make a dragonstone amulet");
				return;
			default:
				player.playerServerMessage(MessageType.QUEST, "Programmer has not defined a message for successfully crafting this product.");
				player.playerServerMessage(MessageType.QUEST, "Please report this.");
		}
	}

	private void tellPlayerNoGem(Player player, ItemCraftingDef def) {
		switch (ItemId.getById(def.getItemID())) {
			case SAPPHIRE_RING:
				player.playerServerMessage(MessageType.QUEST, "You do not have a cut sapphire to make a sapphire ring");
				return;
			case SAPPHIRE_NECKLACE:
				player.playerServerMessage(MessageType.QUEST, "You do not have a cut sapphire to make a sapphire necklace");
				return;
			case UNSTRUNG_SAPPHIRE_AMULET:
				player.playerServerMessage(MessageType.QUEST, "You do not have a cut sapphire to make a sapphire amulet");
				return;
			case EMERALD_RING:
				player.playerServerMessage(MessageType.QUEST, "You do not have a cut Emerald to make a Emerald ring");
				return;
			case EMERALD_NECKLACE:
				player.playerServerMessage(MessageType.QUEST, "You do not have a cut Emerald to make a Emerald necklace");
				return;
			case UNSTRUNG_EMERALD_AMULET:
				player.playerServerMessage(MessageType.QUEST, "You do not have a cut Emerald to make a Emerald amulet");
				return;
			case RUBY_RING:
				player.playerServerMessage(MessageType.QUEST, "You do not have a cut ruby to make a ruby ring");
				return;
			case RUBY_NECKLACE:
				player.playerServerMessage(MessageType.QUEST, "You do not have a cut ruby to make a ruby necklace");
				return;
			case UNSTRUNG_RUBY_AMULET:
				player.playerServerMessage(MessageType.QUEST, "You do not have a cut ruby to make a ruby amulet");
				return;
			case DIAMOND_RING:
				player.playerServerMessage(MessageType.QUEST, "You do not have a cut diamond to make a diamond ring");
				return;
			case DIAMOND_NECKLACE:
				player.playerServerMessage(MessageType.QUEST, "You do not have a cut diamond to make a diamond necklace");
				return;
			case UNSTRUNG_DIAMOND_AMULET:
				player.playerServerMessage(MessageType.QUEST, "You do not have a cut diamond to make a diamond amulet");
				return;
			case DRAGONSTONE_RING:
				player.playerServerMessage(MessageType.QUEST, "You do not have a cut dragonstone to make a dragonstone ring");
				return;
			case DRAGONSTONE_NECKLACE:
				player.playerServerMessage(MessageType.QUEST, "You do not have a cut dragonstone to make a dragonstone necklace");
				return;
			case UNSTRUNG_DRAGONSTONE_AMULET:
				player.playerServerMessage(MessageType.QUEST, "You do not have a dragonstone to make a dragonstone amulet");
				return;
			default:
				player.playerServerMessage(MessageType.QUEST, "Programmer has not defined a message for failing to have the required gem.");
				player.playerServerMessage(MessageType.QUEST, "Please report this.");
				return;
		}
	}

	private void doSilverJewelry(final Item item, final Player player) {
		if (!ActionSender.isRetroClient(player)) {
			ProductionSession session = createSilverJewelryProductionSession(player);
			if (session != null) {
				if (!session.hasAnyCraftableRecipe()) {
					player.message("You are not skilled enough for that yet");
					return;
				}
				player.setAttribute("production_session", session);
				player.setAttribute("production_starter", (ProductionStarter) Crafting::beginProductionFromInterface);
				ActionSender.showProductionInterface(player, session);
				return;
			}
		}
		AtomicReference<String> reply = new AtomicReference<String>();

		// select type
		ArrayList<String> options = new ArrayList<>();
		options.addAll(Arrays.asList(
			"Symbol of Saradomin"
		));
		int maxItemId = player.getConfig().RESTRICT_ITEM_ID;
		int jewelryId = ItemId.UNSTRUNG_UNHOLY_SYMBOL_OF_ZAMORAK.id();
		if (MathUtil.maxUnsigned(maxItemId, jewelryId) == maxItemId) {
			options.add("Symbol of Zamorak");
		}
		jewelryId = ItemId.UNSTRUNG_GUTHIX_SYMBOL.id();
		if (MathUtil.maxUnsigned(maxItemId, jewelryId) == maxItemId) {
			options.add("Symbol of Guthix");
		}
		String[] finalOptions = new String[options.size()];
		int type = multi(player, options.toArray(finalOptions));
		if (type < 0 || type > finalOptions.length) {
			return;
		}
		reply.set(finalOptions[type]);

		final int[] results = {
			ItemId.UNSTRUNG_HOLY_SYMBOL_OF_SARADOMIN.id(),
			ItemId.UNSTRUNG_UNHOLY_SYMBOL_OF_ZAMORAK.id(),
			ItemId.UNSTRUNG_GUTHIX_SYMBOL.id()
		};
		if (player.getCarriedItems().getInventory().countId(silver_moulds[type], Optional.of(false)) <= 0) {
			player.message("You need a " + player.getWorld().getServer().getEntityHandler().getItemDef(silver_moulds[type]).getName() + " to make a " + reply.get() + "!");
			return;
		}

		int repeat = 1;
		if (player.getConfig().BATCH_PROGRESSION) {
			repeat = player.getCarriedItems().getInventory().countId(item.getCatalogId(), Optional.of(false));
		}

		startbatch(repeat);
		batchSilverJewelry(player, item, results, type, reply);
	}

	private void batchSilverJewelry(Player player, Item item, int[] results, int type, AtomicReference<String> reply) {
		Item result = new Item(results[type]);
		if (!canReceive(player, result)) {
			player.message("Your client does not support the desired object");
			return;
		}
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < 16) {
			player.playerServerMessage(MessageType.QUEST, "You need a crafting skill of level 16 to make this");
			return;
		}
		if (checkFatigue(player)) return;

		while (!ifinterrupted() && !isbatchcomplete()) {
			Item silverMould = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(silver_moulds[type], Optional.of(false))
			);
			if (silverMould == null) {
				player.message("You need a " + player.getWorld().getServer().getEntityHandler().getItemDef(silver_moulds[type]).getName() + " to make a " + reply.get() + "!");
				break;
			}

			Item silver = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(item.getCatalogId(), Optional.of(false))
			);
			if (silver == null) {
				break;
			}

			thinkbubble(silver);
			player.getCarriedItems().remove(silver);
			player.playerServerMessage(MessageType.QUEST, "You make a " + result.getDef(player.getWorld()).getName());
			player.getCarriedItems().getInventory().add(result);
			player.incExp(Skill.CRAFTING.id(), 200, true);
			updatebatch();
		}
	}

	private void doPotteryMolding(final Item item, final Player player) {
		if (!ActionSender.isRetroClient(player)) {
			ProductionSession session = createPotteryProductionSession(player);
			if (session != null) {
				if (!session.hasAnyCraftableRecipe()) {
					player.message("You are not skilled enough for that yet");
					return;
				}
				player.setAttribute("production_session", session);
				player.setAttribute("production_starter", (ProductionStarter) Crafting::beginProductionFromInterface);
				ActionSender.showProductionInterface(player, session);
				return;
			}
		}

		ArrayList<String> options = new ArrayList<>();
		options.addAll(Arrays.asList(
			"Pie dish",
			"Pot"
		));
		int maxItemId = player.getConfig().RESTRICT_ITEM_ID;
		int bowlId = ItemId.UNFIRED_BOWL.id();
		if (MathUtil.maxUnsigned(maxItemId, bowlId) == maxItemId) {
			options.add("Bowl");
		}
		String[] finalOptions = new String[options.size()];
		int type = multi(player, options.toArray(finalOptions));
		if (type < 0 || type > finalOptions.length) {
			return;
		}

		int reqLvl, exp;
		Item result;
		AtomicReference<String> msg = new AtomicReference<String>();
		switch (type) {
			case 1:
				result = new Item(ItemId.UNFIRED_POT.id(), 1);
				reqLvl = 1;
				exp = 25;
				// should not use this, as pot is made at level 1
				msg.set("a pot");
				break;
			case 0:
				result = new Item(ItemId.UNFIRED_PIE_DISH.id(), 1);
				reqLvl = !player.getConfig().OLD_SKILL_DEFS ? 4 : 3;
				exp = !player.getConfig().OLD_SKILL_DEFS ? 60 : 30;
				msg.set("pie dishes");
				break;
			case 2:
				result = new Item(ItemId.UNFIRED_BOWL.id(), 1);
				reqLvl = !player.getConfig().OLD_SKILL_DEFS ? 7 : 5;
				exp = !player.getConfig().OLD_SKILL_DEFS ? 40 : 30;
				msg.set("a bowl");
				break;
			default:
				player.message("Nothing interesting happens");
				return;
		}

		int repeat = 1;
		if (player.getConfig().BATCH_PROGRESSION) {
			repeat = player.getCarriedItems().getInventory().countId(item.getCatalogId(), Optional.of(false));
		}

		startbatch(repeat);
		batchPotteryMoulding(player, item, reqLvl, result, msg, exp);
	}

	private void batchPotteryMoulding(Player player, Item item, int reqLvl, Item result, AtomicReference<String> msg, int exp) {
		if (!canReceive(player, result)) {
			player.message("Your client does not support the desired object");
			return;
		}
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < reqLvl) {
			player.playerServerMessage(MessageType.QUEST, "You need to have a crafting of level " + reqLvl + " or higher to make " + msg.get());
			return;
		}
		if (checkFatigue(player)) return;

		while (!ifinterrupted() && !isbatchcomplete()) {
			Item softClay = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(item.getCatalogId(), Optional.of(false))
			);
			if (softClay == null) {
				break;
			}

			player.getCarriedItems().remove(softClay);
			thinkbubble(softClay);
			player.playerServerMessage(MessageType.QUEST, "you make the clay into a " + potteryItemName(result.getDef(player.getWorld()).getName()));
			player.getCarriedItems().getInventory().add(result);
			player.incExp(Skill.CRAFTING.id(), exp, true);
			updatebatch();
		}
	}

	private void doPotteryFiring(final Item item, final Player player) {
		int reqLvl, xp;
		Item result;
		AtomicReference<String> msg = new AtomicReference<String>();
		switch (ItemId.getById(item.getCatalogId())) {
			case UNFIRED_POT:
				result = new Item(ItemId.POT.id(), 1);
				reqLvl = 1;
				xp = !player.getConfig().OLD_SKILL_DEFS ? 25 : 0;
				// should not use this, as pot is made at level 1
				msg.set("a pot");
				break;
			case UNFIRED_PIE_DISH:
				result = new Item(ItemId.PIE_DISH.id(), 1);
				reqLvl = !player.getConfig().OLD_SKILL_DEFS ? 4 : 3;
				xp = !player.getConfig().OLD_SKILL_DEFS ? 40 : 30;
				msg.set("pie dishes");
				break;
			case UNFIRED_BOWL:
				result = new Item(ItemId.BOWL.id(), 1);
				reqLvl = !player.getConfig().OLD_SKILL_DEFS ? 7 : 5;
				xp = !player.getConfig().OLD_SKILL_DEFS ? 60 : 30;
				msg.set("a bowl");
				break;
			default:
				player.message("Nothing interesting happens");
				return;
		}

		final int exp = xp;

		int repeat = 1;
		if (player.getConfig().BATCH_PROGRESSION) {
			repeat = player.getCarriedItems().getInventory().countId(item.getCatalogId(), Optional.of(false));
		}

		startbatch(repeat);
		batchPotteryFiring(player, item, reqLvl, result, msg, exp);
	}

	private void batchPotteryFiring(Player player, Item item, int reqLvl, Item result, AtomicReference<String> msg, int exp) {
		if (!canReceive(player, result)) {
			player.message("Your client does not support the desired object");
			return;
		}
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < reqLvl) {
			player.playerServerMessage(MessageType.QUEST, "You need to have a crafting of level " + reqLvl + " or higher to make " + msg.get());
			return;
		}
		if (checkFatigue(player)) return;

		while (!ifinterrupted() && !isbatchcomplete()) {
			Item unfiredClay = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(item.getCatalogId(), Optional.of(false))
			);
			if (unfiredClay == null) {
				break;
			}

			thinkbubble(unfiredClay);
			String potteryItem = potteryItemName(item.getDef(player.getWorld()).getName());
			player.playerServerMessage(MessageType.QUEST, "You put the " + potteryItem + " in the oven");
			player.getCarriedItems().remove(unfiredClay);

			if (Formulae.crackPot(reqLvl, player.getSkills().getLevel(Skill.CRAFTING.id()))) {
				player.playerServerMessage(MessageType.QUEST, "The "
					+ potteryItem + " cracks in the oven, you throw it away.");
			} else {
				player.playerServerMessage(MessageType.QUEST, "the "
					+ potteryItem + " hardens in the oven");

				String finishedPotteryItem = result.getDef(player.getWorld()).getName().toLowerCase();
				if (finishedPotteryItem.equals("pie dish")) {
					finishedPotteryItem = "dish";
				}

				player.playerServerMessage(MessageType.QUEST, "You remove a "
					+ finishedPotteryItem
					+ " from the oven");
				player.getCarriedItems().getInventory().add(result);
				player.incExp(Skill.CRAFTING.id(), exp, true);
			}
			updatebatch();
		}
	}

	private void doGlassMaking(final Item item, final Player player) {
		int otherItem = item.getCatalogId() == ItemId.SAND.id() ? ItemId.SODA_ASH.id() : ItemId.SAND.id();
		int repeat = 1;
		if (player.getConfig().BATCH_PROGRESSION) {
			repeat = player.getCarriedItems().getInventory().countId(item.getCatalogId(), Optional.of(false));
			repeat = Math.min(player.getCarriedItems().getInventory().countId(otherItem, Optional.of(false)), repeat);
		}

		startbatch(repeat);
		batchGlassMaking(player, item, otherItem);
	}

	private void batchGlassMaking(Player player, Item item, int otherItem) {
		if (!canReceive(player, new Item(ItemId.MOLTEN_GLASS.id()))) {
			player.message("Your client does not support the desired object");
			return;
		}
		if (checkFatigue(player)) return;

		Inventory inventory = player.getCarriedItems().getInventory();
		while (!ifinterrupted() && !isbatchcomplete()) {
			Item item1 = inventory.get(
				inventory.getLastIndexById(otherItem, Optional.of(false))
			);
			Item item2 = inventory.get(
				inventory.getLastIndexById(item.getCatalogId(), Optional.of(false))
			);
			if (item1 == null || item2 == null) {
				break;
			}

			thinkbubble(item2);
			player.playerServerMessage(MessageType.QUEST, "you heat the sand and soda ash in the furnace to make glass");
			player.getCarriedItems().remove(item1);
			player.getCarriedItems().remove(item2);
			inventory.add(new Item(ItemId.MOLTEN_GLASS.id(), 1));
			inventory.add(new Item(ItemId.BUCKET.id(), 1));
			player.incExp(Skill.CRAFTING.id(), 80, true);
			updatebatch();
		}
	}

	private void doGlassBlowing(Player player, final Item pipe, final Item glass) {
		if (glass.getCatalogId() != ItemId.MOLTEN_GLASS.id()) {
			return;
		}

		if (!ActionSender.isRetroClient(player)) {
			ProductionSession session = createGlassBlowingProductionSession(player);
			if (session != null) {
				if (!session.hasAnyCraftableRecipe()) {
					player.message("You are not skilled enough for that yet");
					return;
				}
				player.setAttribute("production_session", session);
				player.setAttribute("production_starter", (ProductionStarter) Crafting::beginProductionFromInterface);
				ActionSender.showProductionInterface(player, session);
				return;
			}
		}
		player.message("what would you like to make?");

		String[] options = new String[]{
			"Vial",
			"Beer glass"
		};

		int type = multi(player, options);
		if (type < 0 || type > 1) {
			return;
		}

		Item result;
		int reqLvl, exp;
		String resultGen;
		switch (type) {
			case 0:
				result = new Item(ItemId.EMPTY_VIAL.id(), 1);
				reqLvl = 33;
				exp = 140;
				resultGen = "vials";
				break;
			case 1:
				result = new Item(ItemId.BEER_GLASS.id(), 1);
				reqLvl = 1;
				exp = 70;
				// should not use this, as beer glass is made at level 1
				resultGen = "beer glasses";
				break;
			default:
				return;
		}

		int repeat = 1;
		if (player.getConfig().BATCH_PROGRESSION) {
			repeat = player.getCarriedItems().getInventory().countId(glass.getCatalogId(), Optional.of(false));
		}

		startbatch(repeat);
		batchGlassBlowing(player, glass, result, reqLvl, exp, resultGen);
	}

	private void batchGlassBlowing(Player player, Item glass, Item result, int reqLvl, int exp, String resultGen) {
		if (!canReceive(player, result)) {
			player.message("Your client does not support the desired object");
			return;
		}
		Inventory inventory = player.getCarriedItems().getInventory();
		ServerConfiguration config = player.getConfig();
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < reqLvl) {
			player.message(
				"You need a crafting level of " + reqLvl + " to make " + resultGen);
			return;
		}
		if (checkFatigue(player)) return;

		while (!ifinterrupted() && !isbatchcomplete()) {
			glass = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(glass.getCatalogId(), Optional.of(false))
			);
			if (glass == null) {
				break;
			}

			player.getCarriedItems().remove(glass);
			String message = "You make a " + result.getDef(player.getWorld()).getName();

			int amount = 1;
			if (result.getCatalogId() == ItemId.EMPTY_VIAL.id()) {
				if (config.WANT_CUSTOM_QUESTS) {
					double breakChance = 91.66667 - getCurrentLevel(player, Skill.CRAFTING.id()) / 1.32;
					for (int loop = 0; loop < 5; ++loop) {
						double hit = new Random().nextDouble() * 99;
						if (hit > breakChance) {
							amount++;
						}
					}
					message = "You make " + amount + " vial" + (amount != 1 ? "s" : "");
					if (player.getLocation().inBounds(418, 559, 421, 563)) {
						result.getItemStatus().setNoted(true);
					}
				}
			}

			player.playerServerMessage(MessageType.QUEST, message);

			if (result.getNoted()) {
				result.getItemStatus().setAmount(amount);
				inventory.add(result);
			}
			else {
				for (int i = 0; i < amount; i++) {
					inventory.add(result);
				}
			}

			player.incExp(Skill.CRAFTING.id(), exp, true);
			updatebatch();
		}
	}

	private void doCutGem(Player player, final Item chisel, final Item gem) {
		final ItemGemDef gemDef = player.getWorld().getServer().getEntityHandler().getItemGemDef(gem.getCatalogId());
		if (gemDef == null) {
			player.message("Nothing interesting happens");
			return;
		}

		int repeat = 1;
		if (player.getConfig().BATCH_PROGRESSION) {
			repeat = Math.min(30, player.getCarriedItems().getInventory().countId(gem.getCatalogId(), Optional.of(false)));
		}

		startbatch(repeat);
		batchGemCutting(player, gem, gemDef);
	}

	private void batchGemCutting(Player player, Item gem, ItemGemDef gemDef) {
		if (!canReceive(player, new Item(gemDef.getGemID()))) {
			player.message("Your client does not support the desired object");
			return;
		}
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < gemDef.getReqLevel()) {
			boolean pluralize = gemDef.getGemID() <= ItemId.UNCUT_DRAGONSTONE.id();
			player.playerServerMessage(MessageType.QUEST,
				"you need a crafting level of " + gemDef.getReqLevel()
					+ " to cut " + (gem.getDef(player.getWorld()).getName().contains("ruby") ? "rubies" : gem.getDef(player.getWorld()).getName().replaceFirst("(?i)uncut ", "") + (pluralize ? "s" : "")));
			return;
		}
		if (checkFatigue(player)) return;

		while (!ifinterrupted() && !isbatchcomplete()) {
			Item item = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(gem.getCatalogId(), Optional.of(false)));
			if (item == null) {
				break;
			}

			Item gemToCut = new Item(item.getCatalogId(), 1, item.getNoted(), item.getItemId());
			Item cutGem = new Item(gemDef.getGemID(), 1);
			int freedSlots = item.getAmount() <= 1 ? 1 : 0;
			if (!player.getCarriedItems().getInventory().canHold(cutGem, freedSlots)) {
				player.message("You do not have enough inventory space to cut another gem");
				stopbatch();
				break;
			}
			delay(2);
			if (player.getCarriedItems().remove(gemToCut) < 0) {
				break;
			}
			if (DataConversions.inArray(gemsThatFail, gem.getCatalogId()) &&
				Formulae.smashGem(gem.getCatalogId(), gemDef.getReqLevel(), player.getSkills().getLevel(Skill.CRAFTING.id()))) {
				player.message("You miss hit the chisel and smash the " + cutGem.getDef(player.getWorld()).getName() + " to pieces!");
				player.getCarriedItems().getInventory().add(new Item(ItemId.CRUSHED_GEMSTONE.id()));

				if (gem.getCatalogId() == ItemId.UNCUT_RED_TOPAZ.id()) {
					player.incExp(Skill.CRAFTING.id(), 25, true);
				} else if (gem.getCatalogId() == ItemId.UNCUT_JADE.id()) {
					player.incExp(Skill.CRAFTING.id(), 20, true);
				} else {
					player.incExp(Skill.CRAFTING.id(), 15, true);
				}
			} else {
				player.getCarriedItems().getInventory().add(cutGem, true);
				String gemName = cutGem.getDef(player.getWorld()).getName();
				if (!DataConversions.inArray(gemsThatFail, gem.getCatalogId())) {
					gemName.toLowerCase();
				} else {
					if (gemName.equals("red topaz")) {
						gemName = "Red Topaz";
					} else {
						gemName = StringUtils.capitalize(gemName);
					}
				}
				player.message("You cut the " + gemName);
				player.playSound("chisel");
				player.incExp(Skill.CRAFTING.id(), gemDef.getExp(), true);
			}

			updatebatch();
		}
	}

	private void makeLeather(Player player, final Item needle, final Item leather) {
		if (leather.getCatalogId() == ItemId.COW_HIDE.id()) {
			makeBrownApron(player, leather);
			return;
		}

		HideArmorRecipe recipe = getHideArmorRecipe(leather.getCatalogId());
		if (recipe == null) {
			player.message("Nothing interesting happens");
			return;
		}

		if (player.getCarriedItems().getInventory().countId(ItemId.THREAD.id(), Optional.of(false)) < 1) {
			player.message("You need some thread to make anything out of leather");
			return;
		}

		if (!ActionSender.isRetroClient(player)) {
			ProductionSession session = createLeatherProductionSession(player, leather);
			if (session != null) {
				if (!session.hasAnyCraftableRecipe()) {
					player.message("You are not skilled enough for that yet");
					return;
				}
				player.setAttribute("production_session", session);
				player.setAttribute("production_starter", (ProductionStarter) Crafting::beginProductionFromInterface);
				ActionSender.showProductionInterface(player, session);
				return;
			}
		}

		HideArmorPiece[] pieces = getHideArmorPieces(recipe);
		String[] options = new String[pieces.length + 1];
		for (int i = 0; i < pieces.length; i++) {
			options[i] = pieces[i].optionName;
		}
		options[pieces.length] = "Cancel";

		int type = multi(player, options);
		if (type < 0 || type >= pieces.length) {
			return;
		}

		HideArmorPiece piece = pieces[type];
		Item result = new Item(piece.resultId, 1);
		int availableMaterial = player.getCarriedItems().getInventory().countId(leather.getCatalogId(), Optional.of(false));
		int threadCost = getLeatherThreadCost(piece.materialCost);
		if (availableMaterial < piece.materialCost) {
			player.message("You need " + piece.materialCost + " pieces of material to make that");
			return;
		}
		if (getAvailableThreadUses(player) < threadCost) {
			player.message("You need more thread to make that");
			return;
		}

		int repeat = 1;
		if (player.getConfig().BATCH_PROGRESSION) {
			repeat = Math.min(30, Math.max(1, Math.min(
				availableMaterial / piece.materialCost,
				getAvailableThreadUses(player) / threadCost)));
		}

		startbatch(repeat);
		batchLeather(player, leather, result, piece.materialCost, threadCost, piece.reqLvl, piece.exp);
	}

	private void makeBrownApron(Player player, final Item cowHide) {
		if (player.getCarriedItems().getInventory().countId(ItemId.THREAD.id(), Optional.of(false)) < 1) {
			player.message("You need some thread to make anything out of leather");
			return;
		}

		if (!ActionSender.isRetroClient(player)) {
			ProductionSession session = createBrownApronProductionSession(player);
			if (session != null) {
				if (!session.hasAnyCraftableRecipe()) {
					player.message("You are not skilled enough for that yet");
					return;
				}
				player.setAttribute("production_session", session);
				player.setAttribute("production_starter", (ProductionStarter) Crafting::beginProductionFromInterface);
				ActionSender.showProductionInterface(player, session);
				return;
			}
		}

		if (!canStartBrownApronRecipe(player)) {
			return;
		}

		int repeat = 1;
		if (player.getConfig().BATCH_PROGRESSION) {
			int availableMaterial = player.getCarriedItems().getInventory().countId(cowHide.getCatalogId(), Optional.of(false)) / BROWN_APRON_COW_HIDE_COST;
			repeat = Math.min(availableMaterial, getAvailableThreadUses(player));
		}

		startbatch(repeat);
		batchBrownApron(player);
	}

	private void batchLeather(Player player, Item leather, Item result, int materialCost, int threadCost, int reqLvl, int exp) {
		if (!canReceive(player, result)) {
			player.message("Your client does not support the desired object");
			return;
		}
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < reqLvl) {
			player.playerServerMessage(MessageType.QUEST, "You need to have a crafting of level " + reqLvl + " or higher to make " + result.getDef(player.getWorld()).getName());
			return;
		}
		if (checkFatigue(player)) return;

		while (!ifinterrupted() && !isbatchcomplete()) {
			if (player.getCarriedItems().getInventory().countId(leather.getCatalogId(), Optional.of(false)) < materialCost) {
				break;
			}
			if (getAvailableThreadUses(player) < threadCost) {
				player.message("You need more thread to make that");
				break;
			}
			delay(3);
			for (int i = 0; i < materialCost; i++) {
				Item item = player.getCarriedItems().getInventory().get(
					player.getCarriedItems().getInventory().getLastIndexById(leather.getCatalogId(), Optional.of(false))
				);
				if (item == null) {
					return;
				}
				player.getCarriedItems().remove(item);
			}
			player.message("You make some " + result.getDef(player.getWorld()).getName());
			player.getCarriedItems().getInventory().add(result);
			player.incExp(Skill.CRAFTING.id(), exp, true);
			updatebatch();
			consumeThreadUses(player, threadCost);
		}
	}

	private void batchBrownApron(Player player) {
		Item result = new Item(ItemId.BROWN_APRON.id(), 1);
		if (!canReceive(player, result)) {
			player.message("Your client does not support the desired object");
			return;
		}
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < BROWN_APRON_CRAFTING_LEVEL) {
			player.playerServerMessage(MessageType.QUEST, "You need to have a crafting of level " + BROWN_APRON_CRAFTING_LEVEL + " or higher to make " + result.getDef(player.getWorld()).getName());
			return;
		}
		if (checkFatigue(player)) return;

		while (!ifinterrupted() && !isbatchcomplete()) {
			if (player.getCarriedItems().getInventory().countId(ItemId.COW_HIDE.id(), Optional.of(false)) < BROWN_APRON_COW_HIDE_COST) {
				break;
			}
			for (int i = 0; i < BROWN_APRON_COW_HIDE_COST; i++) {
				Item item = player.getCarriedItems().getInventory().get(
					player.getCarriedItems().getInventory().getLastIndexById(ItemId.COW_HIDE.id(), Optional.of(false))
				);
				if (item == null) {
					return;
				}
				player.getCarriedItems().remove(item);
			}
			player.message("You make a " + result.getDef(player.getWorld()).getName());
			player.getCarriedItems().getInventory().add(result);
			player.incExp(Skill.CRAFTING.id(), BROWN_APRON_CRAFTING_EXP, true);
			updatebatch();
			if (!consumeThreadUse(player)) {
				break;
			}
		}
	}

	private HideArmorRecipe getHideArmorRecipe(int materialId) {
		switch (ItemId.getById(materialId)) {
			case LEATHER:
				return new HideArmorRecipe(materialId, "Cow hide", 1, 1,
					ItemId.COW_HIDE_COIF.id(), ItemId.COW_HIDE_GLOVES.id(), ItemId.COW_HIDE_BOOTS.id(),
					ItemId.COW_HIDE_CHAPS.id(), ItemId.COW_HIDE_CUIRASS.id());
			case GOBLIN_LEATHER:
				return new HideArmorRecipe(materialId, "Goblin hide", 1, 1,
					ItemId.GOBLIN_HIDE_COIF.id(), ItemId.GOBLIN_HIDE_GLOVES.id(), ItemId.GOBLIN_HIDE_BOOTS.id(),
					ItemId.GOBLIN_HIDE_CHAPS.id(), ItemId.GOBLIN_HIDE_CUIRASS.id());
			case UNICORN_LEATHER:
				return new HideArmorRecipe(materialId, "Unicorn hide", 2, 8,
					ItemId.UNICORN_HIDE_COIF.id(), ItemId.UNICORN_HIDE_GLOVES.id(), ItemId.UNICORN_HIDE_BOOTS.id(),
					ItemId.UNICORN_HIDE_CHAPS.id(), ItemId.UNICORN_HIDE_CUIRASS.id());
			case BEAR_LEATHER:
				return new HideArmorRecipe(materialId, "Bear hide", 2, 8,
					ItemId.BEAR_HIDE_COIF.id(), ItemId.BEAR_HIDE_GLOVES.id(), ItemId.BEAR_HIDE_BOOTS.id(),
					ItemId.BEAR_HIDE_CHAPS.id(), ItemId.BEAR_HIDE_CUIRASS.id());
			case BLACK_UNICORN_LEATHER:
				return new HideArmorRecipe(materialId, "Black unicorn hide", 2, 8,
					ItemId.BLACK_UNICORN_HIDE_COIF.id(), ItemId.BLACK_UNICORN_HIDE_GLOVES.id(), ItemId.BLACK_UNICORN_HIDE_BOOTS.id(),
					ItemId.BLACK_UNICORN_HIDE_CHAPS.id(), ItemId.BLACK_UNICORN_HIDE_CUIRASS.id());
			case CURED_SCORPION_CARAPACE:
				return new HideArmorRecipe(materialId, "Scorpion carapace", 2, 8,
					ItemId.SCORPION_CARAPACE_COIF.id(), ItemId.SCORPION_CARAPACE_GLOVES.id(), ItemId.SCORPION_CARAPACE_BOOTS.id(),
					ItemId.SCORPION_CARAPACE_CHAPS.id(), ItemId.SCORPION_CARAPACE_CUIRASS.id());
			case WOLF_LEATHER:
				return new HideArmorRecipe(materialId, "Wolf hide", 3, 15,
					ItemId.WOLF_COIF.id(), ItemId.WOLF_GLOVES.id(), ItemId.WOLF_BOOTS.id(),
					ItemId.WOLF_CHAPS.id(), ItemId.WOLF_CUIRASS.id());
			case CURED_SPIDER_CARAPACE:
				return new HideArmorRecipe(materialId, "Spider carapace", 3, 15,
					ItemId.SPIDER_COIF.id(), ItemId.SPIDER_GLOVES.id(), ItemId.SPIDER_BOOTS.id(),
					ItemId.SPIDER_CHAPS.id(), ItemId.SPIDER_CUIRASS.id());
			case GIANT_LEATHER:
				return new HideArmorRecipe(materialId, "Giant hide", 3, 15,
					ItemId.GIANT_COIF.id(), ItemId.GIANT_GLOVES.id(), ItemId.GIANT_BOOTS.id(),
					ItemId.GIANT_CHAPS.id(), ItemId.GIANT_CUIRASS.id());
			case OGRE_LEATHER:
				return new HideArmorRecipe(materialId, "Ogre hide", 4, 22,
					ItemId.OGRE_COIF.id(), ItemId.OGRE_GLOVES.id(), ItemId.OGRE_BOOTS.id(),
					ItemId.OGRE_CHAPS.id(), ItemId.OGRE_CUIRASS.id());
			case BABY_DRAGON_LEATHER:
				return new HideArmorRecipe(materialId, "Baby dragon hide", 4, 22,
					ItemId.BABY_DRAGON_COIF.id(), ItemId.BABY_DRAGON_GLOVES.id(), ItemId.BABY_DRAGON_BOOTS.id(),
					ItemId.BABY_DRAGON_CHAPS.id(), ItemId.BABY_DRAGON_CUIRASS.id());
			case CURED_MAGIC_SPIDER_CARAPACE:
				return new HideArmorRecipe(materialId, "Magic spider carapace", 5, 30,
					ItemId.MAGIC_SPIDER_COIF.id(), ItemId.MAGIC_SPIDER_GLOVES.id(), ItemId.MAGIC_SPIDER_BOOTS.id(),
					ItemId.MAGIC_SPIDER_CHAPS.id(), ItemId.MAGIC_SPIDER_CUIRASS.id());
			case MOSS_GIANT_LEATHER:
				return new HideArmorRecipe(materialId, "Moss giant hide", 5, 30,
					ItemId.MOSS_GIANT_COIF.id(), ItemId.MOSS_GIANT_GLOVES.id(), ItemId.MOSS_GIANT_BOOTS.id(),
					ItemId.MOSS_GIANT_CHAPS.id(), ItemId.MOSS_GIANT_CUIRASS.id());
			case ICE_GIANT_LEATHER:
				return new HideArmorRecipe(materialId, "Ice giant hide", 5, 30,
					ItemId.ICE_GIANT_COIF.id(), ItemId.ICE_GIANT_GLOVES.id(), ItemId.ICE_GIANT_BOOTS.id(),
					ItemId.ICE_GIANT_CHAPS.id(), ItemId.ICE_GIANT_CUIRASS.id());
			case DEMON_LEATHER:
				return new HideArmorRecipe(materialId, "Demon hide", 6, 38,
					ItemId.DEMON_COIF.id(), ItemId.DEMON_GLOVES.id(), ItemId.DEMON_BOOTS.id(),
					ItemId.DEMON_CHAPS.id(), ItemId.DEMON_CUIRASS.id());
			case HELLHOUND_LEATHER:
				return new HideArmorRecipe(materialId, "Hellhound hide", 7, 46,
					ItemId.HELLHOUND_COIF.id(), ItemId.HELLHOUND_GLOVES.id(), ItemId.HELLHOUND_BOOTS.id(),
					ItemId.HELLHOUND_CHAPS.id(), ItemId.HELLHOUND_CUIRASS.id());
			case FIRE_GIANT_LEATHER:
				return new HideArmorRecipe(materialId, "Fire giant hide", 7, 46,
					ItemId.FIRE_GIANT_COIF.id(), ItemId.FIRE_GIANT_GLOVES.id(), ItemId.FIRE_GIANT_BOOTS.id(),
					ItemId.FIRE_GIANT_CHAPS.id(), ItemId.FIRE_GIANT_CUIRASS.id());
			case BLUE_DRAGON_LEATHER:
				return new HideArmorRecipe(materialId, "Blue dragon hide", 7, 46,
					ItemId.BLUE_DRAGON_COIF.id(), ItemId.BLUE_DRAGON_GLOVES.id(), ItemId.BLUE_DRAGON_BOOTS.id(),
					ItemId.BLUE_DRAGON_CHAPS.id(), ItemId.BLUE_DRAGON_CUIRASS.id());
			case DRAGON_LEATHER:
				return new HideArmorRecipe(materialId, "Earth dragon hide", 7, 46,
					ItemId.DRAGON_COIF.id(), ItemId.DRAGON_GLOVES.id(), ItemId.DRAGON_BOOTS.id(),
					ItemId.DRAGON_CHAPS.id(), ItemId.DRAGON_CUIRASS.id());
			case RED_DRAGON_LEATHER:
				return new HideArmorRecipe(materialId, "Red dragon hide", 8, 54,
					ItemId.RED_DRAGON_COIF.id(), ItemId.RED_DRAGON_GLOVES.id(), ItemId.RED_DRAGON_BOOTS.id(),
					ItemId.RED_DRAGON_CHAPS.id(), ItemId.RED_DRAGON_CUIRASS.id());
			case BLACK_DEMON_LEATHER:
				return new HideArmorRecipe(materialId, "Black demon hide", 8, 54,
					ItemId.BLACK_DEMON_COIF.id(), ItemId.BLACK_DEMON_GLOVES.id(), ItemId.BLACK_DEMON_BOOTS.id(),
					ItemId.BLACK_DEMON_CHAPS.id(), ItemId.BLACK_DEMON_CUIRASS.id());
			case BLACK_DRAGON_LEATHER:
				return new HideArmorRecipe(materialId, "Black dragon hide", 9, 62,
					ItemId.BLACK_DRAGON_COIF.id(), ItemId.BLACK_DRAGON_GLOVES.id(), ItemId.BLACK_DRAGON_BOOTS.id(),
					ItemId.BLACK_DRAGON_CHAPS.id(), ItemId.BLACK_DRAGON_CUIRASS.id());
			case BALROG_LEATHER:
				return new HideArmorRecipe(materialId, "Balrog hide", 10, 70,
					ItemId.BALROG_COIF.id(), ItemId.BALROG_GLOVES.id(), ItemId.BALROG_BOOTS.id(),
					ItemId.BALROG_CHAPS.id(), ItemId.BALROG_CUIRASS.id());
			case ELDER_GREEN_DRAGON_LEATHER:
				return new HideArmorRecipe(materialId, "Elder green dragon hide", 11, 80,
					ItemId.ELDER_GREEN_DRAGON_COIF.id(), ItemId.ELDER_GREEN_DRAGON_GLOVES.id(), ItemId.ELDER_GREEN_DRAGON_BOOTS.id(),
					ItemId.ELDER_GREEN_DRAGON_CHAPS.id(), ItemId.ELDER_GREEN_DRAGON_CUIRASS.id());
			default:
				return null;
		}
	}

	private HideArmorPiece[] getHideArmorPieces(HideArmorRecipe recipe) {
		int baseLevel = getTierCraftingBaseLevel(recipe.tier);
		return new HideArmorPiece[]{
			new HideArmorPiece("Coif", recipe.coifId, 1, baseLevel, getHideArmorExp(recipe.tier, 1)),
			new HideArmorPiece("Gloves", recipe.glovesId, 2, baseLevel + 1, getHideArmorExp(recipe.tier, 2)),
			new HideArmorPiece("Boots", recipe.bootsId, 2, baseLevel + 1, getHideArmorExp(recipe.tier, 2)),
			new HideArmorPiece("Chaps", recipe.chapsId, 3, baseLevel + 2, getHideArmorExp(recipe.tier, 3)),
			new HideArmorPiece("Cuirass", recipe.cuirassId, 4, baseLevel + 3, getHideArmorExp(recipe.tier, 4))
		};
	}

	private int getTierCraftingBaseLevel(int tier) {
		switch (tier) {
			case 1:
				return 1;
			case 2:
				return 8;
			case 3:
				return 15;
			case 4:
				return 22;
			case 5:
				return 30;
			case 6:
				return 38;
			case 7:
				return 46;
			case 8:
				return 54;
			case 9:
				return 62;
			case 10:
				return 70;
			case 11:
				return 80;
			default:
				return 1;
		}
	}

	private int getHideArmorExp(int tier, int materialCost) {
		return tier * materialCost * 12;
	}

	private void craftWoolGarment(Player player) {
		String[] options = new String[WOOL_GARMENT_RECIPES.length];
		for (int index = 0; index < WOOL_GARMENT_RECIPES.length; index++) {
			WoolGarmentRecipe recipe = WOOL_GARMENT_RECIPES[index];
			options[index] = recipe.optionName + " (" + recipe.woolCost + " wool)";
		}

		int choice = multi(player, options);
		if (choice < 0 || choice >= WOOL_GARMENT_RECIPES.length) {
			return;
		}

		WoolGarmentRecipe recipe = WOOL_GARMENT_RECIPES[choice];
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < recipe.reqLvl) {
			player.message("You need a crafting skill of " + recipe.reqLvl + " or above to do that");
			return;
		}

		if (player.getCarriedItems().getInventory().countId(ItemId.BALL_OF_WOOL.id(), Optional.of(false)) < recipe.woolCost) {
			player.message("You need " + recipe.woolCost + " balls of wool to make that");
			return;
		}

		if (checkFatigue(player)) {
			return;
		}

		for (int used = 0; used < recipe.woolCost; used++) {
			if (player.getCarriedItems().remove(new Item(ItemId.BALL_OF_WOOL.id())) == -1) {
				return;
			}
		}

		player.message("You carefully stitch the wool into a " + recipe.optionName.toLowerCase());
		player.getCarriedItems().getInventory().add(new Item(recipe.resultId));
		player.incExp(Skill.CRAFTING.id(), recipe.exp, true);
	}

	private void beginWoolCrafting(Player player) {
		if (!ActionSender.isRetroClient(player)) {
			ProductionSession session = createWoolProductionSession(player);
			if (session != null) {
				if (!session.hasAnyCraftableRecipe()) {
					player.message("You are not skilled enough for that yet");
					return;
				}
				player.setAttribute("production_session", session);
				player.setAttribute("production_starter", (ProductionStarter) Crafting::beginProductionFromInterface);
				ActionSender.showProductionInterface(player, session);
				return;
			}
		}
		craftWoolGarment(player);
	}

	public static boolean beginProductionFromInterface(Player player, ProductionSession session, int itemId, int quantity) {
		if (session == null || !session.isType(ProductionSession.TYPE_CRAFTING)) {
			return false;
		}
		if (quantity < 1) {
			player.message("Choose at least one item to make");
			return false;
		}
		Crafting crafting = new Crafting();
		int inputId = session.getInputItemId();
		if (inputId == ItemId.SILVER_BAR.id()) {
			int type = crafting.getSilverJewelryProductionType(player, itemId);
			if (type < 0) {
				player.message("Nothing interesting happens");
				return false;
			}
			if (!crafting.canStartSilverJewelryRecipe(player, type)) {
				return false;
			}
			int availableBars = player.getCarriedItems().getInventory().countId(ItemId.SILVER_BAR.id(), Optional.of(false));
			int makeCount = Math.min(quantity, availableBars);
			if (makeCount < 1) {
				player.message("You need more materials to make that");
				return false;
			}
			final int[] results = {
				ItemId.UNSTRUNG_HOLY_SYMBOL_OF_SARADOMIN.id(),
				ItemId.UNSTRUNG_UNHOLY_SYMBOL_OF_ZAMORAK.id(),
				ItemId.UNSTRUNG_GUTHIX_SYMBOL.id()
			};
			AtomicReference<String> reply = new AtomicReference<>(player.getWorld().getServer().getEntityHandler().getItemDef(results[type]).getName());
			Item silverBar = new Item(ItemId.SILVER_BAR.id());
			startbatch(player, makeCount);
			crafting.batchSilverJewelry(player, silverBar, results, type, reply);
			return true;
		}
		if (inputId == ItemId.GOLD_BAR.id()) {
			ItemCraftingDef def = crafting.getGoldJewelryProductionDef(player, itemId);
			if (def == null) {
				player.message("Nothing interesting happens");
				return false;
			}
			int mouldId = crafting.getRequiredGoldMouldId(itemId);
			if (!crafting.canStartGoldJewelryRecipe(player, def, mouldId)) {
				return false;
			}
			int availableBars = player.getCarriedItems().getInventory().countId(ItemId.GOLD_BAR.id(), Optional.of(false));
			int availableGems = def.getReqGem() == ItemId.NOTHING.id()
				? availableBars
				: player.getCarriedItems().getInventory().countId(def.getReqGem(), Optional.of(false));
			int makeCount = Math.min(quantity, Math.min(availableBars, availableGems));
			if (makeCount < 1) {
				player.message("You need more materials to make that");
				return false;
			}
			Item goldBar = new Item(ItemId.GOLD_BAR.id());
			startbatch(player, makeCount);
			crafting.batchGoldJewelry(player, goldBar, def);
			return true;
		}
		if (inputId == ItemId.BALL_OF_WOOL.id()) {
			WoolGarmentRecipe recipe = crafting.getWoolRecipeByItemId(itemId);
			if (recipe == null || !crafting.canStartWoolRecipe(player, recipe)) {
				return false;
			}
			int available = player.getCarriedItems().getInventory().countId(ItemId.BALL_OF_WOOL.id(), Optional.of(false)) / recipe.woolCost;
			int makeCount = Math.min(quantity, available);
			if (makeCount < 1) {
				player.message("You need more materials to make that");
				return false;
			}
			startbatch(player, makeCount);
			crafting.batchWoolGarment(player, recipe);
			return true;
		}
		if (inputId == ItemId.SOFT_CLAY.id()) {
			PotteryRecipe recipe = crafting.getPotteryRecipeByItemId(player, itemId);
			if (recipe == null || !crafting.canStartPotteryRecipe(player, recipe)) {
				return false;
			}
			int available = player.getCarriedItems().getInventory().countId(ItemId.SOFT_CLAY.id(), Optional.of(false));
			int makeCount = Math.min(quantity, available);
			if (makeCount < 1) {
				player.message("You need more materials to make that");
				return false;
			}
			Item softClay = new Item(ItemId.SOFT_CLAY.id());
			Item result = new Item(recipe.resultId, 1);
			AtomicReference<String> msg = new AtomicReference<>(crafting.getPotteryBatchMessage(recipe.resultId));
			startbatch(player, makeCount);
			crafting.batchPotteryMoulding(player, softClay, recipe.reqLvl, result, msg, recipe.exp);
			return true;
		}
		if (inputId == ItemId.MOLTEN_GLASS.id()) {
			GlassBlowingRecipe recipe = crafting.getGlassBlowingRecipeByItemId(itemId);
			if (recipe == null || !crafting.canStartGlassRecipe(player, recipe)) {
				return false;
			}
			int available = player.getCarriedItems().getInventory().countId(ItemId.MOLTEN_GLASS.id(), Optional.of(false));
			int makeCount = Math.min(quantity, available);
			if (makeCount < 1) {
				player.message("You need more materials to make that");
				return false;
			}
			Item glass = new Item(ItemId.MOLTEN_GLASS.id());
			Item result = new Item(recipe.resultId, 1);
			startbatch(player, makeCount);
			crafting.batchGlassBlowing(player, glass, result, recipe.reqLvl, recipe.exp, recipe.resultGen);
			return true;
		}
		if (crafting.isFurnaceMetalCategory(inputId)) {
			ProductionRecipe productionRecipe = session.getRecipeByItemId(itemId);
			int[] ingredientIds = productionRecipe == null ? new int[0] : productionRecipe.getIngredientItemIds();
			int barId = ingredientIds.length > 0 ? ingredientIds[0] : -1;
			if (!crafting.isModernMetalBar(barId)) {
				player.message("Nothing interesting happens");
				return false;
			}
			RangedMouldRecipe recipe = crafting.getRangedMouldRecipeForCategory(barId, inputId);
			if (recipe == null || recipe.resultId != itemId) {
				player.message("Nothing interesting happens");
				return false;
			}
			return crafting.beginRangedMouldProduction(player, barId, recipe, quantity);
		}
		if (crafting.isModernMetalBar(inputId)) {
			RangedMouldRecipe recipe = crafting.getRangedMouldRecipeByItemId(inputId, itemId);
			return crafting.beginRangedMouldProduction(player, inputId, recipe, quantity);
		}

		if (inputId == ItemId.COW_HIDE.id() && itemId == ItemId.BROWN_APRON.id()) {
			if (!crafting.canStartBrownApronRecipe(player)) {
				return false;
			}
			int availableMaterial = player.getCarriedItems().getInventory().countId(inputId, Optional.of(false)) / BROWN_APRON_COW_HIDE_COST;
			int availableThreadUses = crafting.getAvailableThreadUses(player);
			int makeCount = Math.min(quantity, Math.min(availableMaterial, availableThreadUses));
			if (makeCount < 1) {
				player.message("You need more materials to make that");
				return false;
			}
			startbatch(player, makeCount);
			crafting.batchBrownApron(player);
			return true;
		}

		HideArmorRecipe hideRecipe = crafting.getHideArmorRecipe(inputId);
		if (hideRecipe == null) {
			return false;
		}
		HideArmorPiece piece = crafting.getHideArmorPieceByItemId(hideRecipe, itemId);
		if (piece == null || !crafting.canStartLeatherRecipe(player, inputId, piece)) {
			return false;
		}
		int availableMaterial = player.getCarriedItems().getInventory().countId(inputId, Optional.of(false)) / piece.materialCost;
		int availableThreadUses = crafting.getAvailableThreadUses(player);
		int threadCost = crafting.getLeatherThreadCost(piece.materialCost);
		int availableByThread = availableThreadUses / threadCost;
		int makeCount = Math.min(quantity, Math.min(availableMaterial, availableByThread));
		if (makeCount < 1) {
			player.message("You need more materials to make that");
			return false;
		}
		Item leather = new Item(inputId);
		Item result = new Item(piece.resultId, 1);
		startbatch(player, makeCount);
		crafting.batchLeather(player, leather, result, piece.materialCost, threadCost, piece.reqLvl, piece.exp);
		return true;
	}

	public boolean openFurnaceCategory(Player player, int categoryId) {
		ProductionSession session;
		if (categoryId == Smelting.FURNACE_CATEGORY_RINGS) {
			session = createGoldJewelryProductionSession(player, JewelryCategory.RINGS);
		} else if (categoryId == Smelting.FURNACE_CATEGORY_NECKLACES) {
			session = createGoldJewelryProductionSession(player, JewelryCategory.NECKLACES);
		} else if (categoryId == Smelting.FURNACE_CATEGORY_AMULETS) {
			session = createGoldJewelryProductionSession(player, JewelryCategory.AMULETS);
		} else if (categoryId == Smelting.FURNACE_CATEGORY_HOLY_SYMBOLS) {
			session = createSilverJewelryProductionSession(player, 0);
		} else if (categoryId == Smelting.FURNACE_CATEGORY_UNHOLY_SYMBOLS) {
			session = createSilverJewelryProductionSession(player, 1);
		} else if (categoryId == Smelting.FURNACE_CATEGORY_GUTHIX_SYMBOLS) {
			session = createSilverJewelryProductionSession(player, 2);
		} else if (isFurnaceMetalCategory(categoryId)) {
			session = createRangedMouldCategoryProductionSession(player, categoryId);
			if (session == null) {
				return false;
			}
		} else {
			player.message("Nothing interesting happens");
			return false;
		}

		if (session == null) {
			player.message("Nothing interesting happens");
			return false;
		}
		if (!session.hasAnyCraftableRecipe()) {
			player.message("You are not skilled enough or lack the needed materials");
			return false;
		}
		player.setAttribute("production_session", session);
		player.setAttribute("production_starter", (ProductionStarter) Crafting::beginProductionFromInterface);
		ActionSender.showProductionInterface(player, session);
		return true;
	}

	private boolean isFurnaceMetalCategory(int categoryId) {
		return categoryId == Smelting.FURNACE_CATEGORY_BOLTS
			|| categoryId == Smelting.FURNACE_CATEGORY_ARROWHEADS
			|| categoryId == Smelting.FURNACE_CATEGORY_DARTS
			|| categoryId == Smelting.FURNACE_CATEGORY_THROWING_KNIVES
			|| categoryId == Smelting.FURNACE_CATEGORY_SHURIKEN;
	}

	private ProductionSession createLeatherProductionSession(Player player, Item leather) {
		if (leather.getCatalogId() == ItemId.COW_HIDE.id()) {
			return createBrownApronProductionSession(player);
		}

		HideArmorRecipe recipe = getHideArmorRecipe(leather.getCatalogId());
		if (recipe == null) {
			return null;
		}
		HideArmorPiece[] pieces = getHideArmorPieces(recipe);
		List<ProductionRecipe> recipes = new ArrayList<>();
		int level = player.getSkills().getLevel(Skill.CRAFTING.id());
		int materialCount = player.getCarriedItems().getInventory().countId(leather.getCatalogId(), Optional.of(false));
		int threadUses = getAvailableThreadUses(player);
		for (HideArmorPiece piece : pieces) {
			int threadCost = getLeatherThreadCost(piece.materialCost);
			recipes.add(new ProductionRecipe(piece.resultId, piece.reqLvl, piece.materialCost, 1,
				level >= piece.reqLvl, materialCount >= piece.materialCost && threadUses >= threadCost,
				new int[]{leather.getCatalogId(), ItemId.THREAD.id()},
				new int[]{-1, -1}, new int[]{piece.materialCost, threadCost}));
		}
		return new ProductionSession(ProductionSession.TYPE_CRAFTING, "Choose a leather item to craft", leather.getCatalogId(), recipes);
	}

	private ProductionSession createBrownApronProductionSession(Player player) {
		List<ProductionRecipe> recipes = new ArrayList<>();
		int level = player.getSkills().getLevel(Skill.CRAFTING.id());
		int materialCount = player.getCarriedItems().getInventory().countId(ItemId.COW_HIDE.id(), Optional.of(false));
		int threadUses = getAvailableThreadUses(player);
		recipes.add(new ProductionRecipe(ItemId.BROWN_APRON.id(), BROWN_APRON_CRAFTING_LEVEL, BROWN_APRON_COW_HIDE_COST, 1,
			level >= BROWN_APRON_CRAFTING_LEVEL, materialCount >= BROWN_APRON_COW_HIDE_COST && threadUses >= 1,
			new int[]{ItemId.COW_HIDE.id(), ItemId.THREAD.id()},
			new int[]{-1, -1}, new int[]{BROWN_APRON_COW_HIDE_COST, 1}));
		return new ProductionSession(ProductionSession.TYPE_CRAFTING, "Choose a leather item to craft", ItemId.COW_HIDE.id(), recipes);
	}

	private ProductionSession createWoolProductionSession(Player player) {
		List<ProductionRecipe> recipes = new ArrayList<>();
		int level = player.getSkills().getLevel(Skill.CRAFTING.id());
		int materialCount = player.getCarriedItems().getInventory().countId(ItemId.BALL_OF_WOOL.id(), Optional.of(false));
		for (WoolGarmentRecipe recipe : WOOL_GARMENT_RECIPES) {
			recipes.add(new ProductionRecipe(recipe.resultId, recipe.reqLvl, recipe.woolCost, 1,
				level >= recipe.reqLvl, materialCount >= recipe.woolCost,
				new int[]{ItemId.BALL_OF_WOOL.id()}, new int[]{-1}, new int[]{recipe.woolCost}));
		}
		return new ProductionSession(ProductionSession.TYPE_CRAFTING, "Choose a wool item to craft", ItemId.BALL_OF_WOOL.id(), recipes);
	}

	private ProductionSession createPotteryProductionSession(Player player) {
		List<ProductionRecipe> recipes = new ArrayList<>();
		int level = player.getSkills().getLevel(Skill.CRAFTING.id());
		int materialCount = player.getCarriedItems().getInventory().countId(ItemId.SOFT_CLAY.id(), Optional.of(false));
		for (PotteryRecipe recipe : POTTERY_RECIPES) {
			if (recipe.gatedByItemSupport
				&& MathUtil.maxUnsigned(player.getConfig().RESTRICT_ITEM_ID, recipe.resultId) != player.getConfig().RESTRICT_ITEM_ID) {
				continue;
			}
			recipes.add(new ProductionRecipe(recipe.resultId, recipe.reqLvl, 1, 1,
				level >= recipe.reqLvl, materialCount >= 1,
				new int[]{ItemId.SOFT_CLAY.id()}, new int[]{-1}, new int[]{1}));
		}
		return recipes.isEmpty() ? null
			: new ProductionSession(ProductionSession.TYPE_CRAFTING, "Choose a pottery item to shape", ItemId.SOFT_CLAY.id(), recipes);
	}

	private ProductionSession createGlassBlowingProductionSession(Player player) {
		List<ProductionRecipe> recipes = new ArrayList<>();
		int level = player.getSkills().getLevel(Skill.CRAFTING.id());
		int materialCount = player.getCarriedItems().getInventory().countId(ItemId.MOLTEN_GLASS.id(), Optional.of(false));
		for (GlassBlowingRecipe recipe : GLASS_BLOWING_RECIPES) {
			recipes.add(new ProductionRecipe(recipe.resultId, recipe.reqLvl, 1, 1,
				level >= recipe.reqLvl, materialCount >= 1,
				new int[]{ItemId.MOLTEN_GLASS.id()}, new int[]{-1}, new int[]{1}));
		}
		return recipes.isEmpty() ? null
			: new ProductionSession(ProductionSession.TYPE_CRAFTING, "Choose a glass item to blow", ItemId.MOLTEN_GLASS.id(), recipes);
	}

	private ProductionSession createGoldJewelryProductionSession(Player player) {
		return createGoldJewelryProductionSession(player, JewelryCategory.ALL);
	}

	private ProductionSession createGoldJewelryProductionSession(Player player, JewelryCategory category) {
		List<ProductionRecipe> recipes = new ArrayList<>();
		int level = player.getSkills().getLevel(Skill.CRAFTING.id());
		int barCount = player.getCarriedItems().getInventory().countId(ItemId.GOLD_BAR.id(), Optional.of(false));
		for (int outputId : getGoldJewelryProductionOutputIds(player)) {
			if (!isGoldJewelryInCategory(outputId, category)) {
				continue;
			}
			ItemCraftingDef def = player.getWorld().getServer().getEntityHandler().getCraftingDef(outputId);
			if (def == null) {
				continue;
			}
			int mouldId = getRequiredGoldMouldId(outputId);
			boolean hasMould = mouldId <= 0 || player.getCarriedItems().getInventory().countId(mouldId, Optional.of(false)) > 0;
			boolean hasGem = def.getReqGem() == ItemId.NOTHING.id()
				|| player.getCarriedItems().getInventory().countId(def.getReqGem(), Optional.of(false)) > 0;
			recipes.add(goldJewelryProductionRecipe(outputId, def, mouldId,
				level >= def.getReqLevel(), barCount >= 1 && hasMould && hasGem));
		}
		return recipes.isEmpty() ? null
			: new ProductionSession(ProductionSession.TYPE_CRAFTING, "Choose jewelry to craft", ItemId.GOLD_BAR.id(), recipes);
	}

	private ProductionRecipe goldJewelryProductionRecipe(int outputId, ItemCraftingDef def, int mouldId,
		boolean levelMet, boolean materialsMet) {
		List<Integer> ingredientIds = new ArrayList<>();
		List<Integer> fallbackIds = new ArrayList<>();
		List<Integer> amounts = new ArrayList<>();
		addProductionIngredient(ingredientIds, fallbackIds, amounts, ItemId.GOLD_BAR.id(), 1);
		if (mouldId > 0) {
			addProductionIngredient(ingredientIds, fallbackIds, amounts, mouldId, 1);
		}
		if (def.getReqGem() != ItemId.NOTHING.id()) {
			addProductionIngredient(ingredientIds, fallbackIds, amounts, def.getReqGem(), 1);
		}
		return new ProductionRecipe(outputId, def.getReqLevel(), 1, 1, levelMet, materialsMet,
			toIntArray(ingredientIds), toIntArray(fallbackIds), toIntArray(amounts));
	}

	private void addProductionIngredient(List<Integer> ingredientIds, List<Integer> fallbackIds, List<Integer> amounts,
		int itemId, int amount) {
		ingredientIds.add(itemId);
		fallbackIds.add(-1);
		amounts.add(amount);
	}

	private int[] toIntArray(List<Integer> values) {
		int[] result = new int[values.size()];
		for (int i = 0; i < values.size(); i++) {
			result[i] = values.get(i);
		}
		return result;
	}

	private boolean isGoldJewelryInCategory(int outputId, JewelryCategory category) {
		switch (category) {
			case RINGS:
				return outputId == ItemId.GOLD_RING.id()
					|| outputId == ItemId.SAPPHIRE_RING.id()
					|| outputId == ItemId.EMERALD_RING.id()
					|| outputId == ItemId.RUBY_RING.id()
					|| outputId == ItemId.DIAMOND_RING.id()
					|| outputId == ItemId.DRAGONSTONE_RING.id();
			case NECKLACES:
				return outputId == ItemId.GOLD_NECKLACE.id()
					|| outputId == ItemId.SAPPHIRE_NECKLACE.id()
					|| outputId == ItemId.EMERALD_NECKLACE.id()
					|| outputId == ItemId.RUBY_NECKLACE.id()
					|| outputId == ItemId.DIAMOND_NECKLACE.id()
					|| outputId == ItemId.DRAGONSTONE_NECKLACE.id();
			case AMULETS:
				return outputId == ItemId.UNSTRUNG_GOLD_AMULET.id()
					|| outputId == ItemId.UNSTRUNG_SAPPHIRE_AMULET.id()
					|| outputId == ItemId.UNSTRUNG_EMERALD_AMULET.id()
					|| outputId == ItemId.UNSTRUNG_RUBY_AMULET.id()
					|| outputId == ItemId.UNSTRUNG_DIAMOND_AMULET.id()
					|| outputId == ItemId.UNSTRUNG_DRAGONSTONE_AMULET.id();
			case ALL:
			default:
				return true;
		}
	}

	private ProductionSession createSilverJewelryProductionSession(Player player) {
		return createSilverJewelryProductionSession(player, -1);
	}

	private ProductionSession createSilverJewelryProductionSession(Player player, int onlyType) {
		List<ProductionRecipe> recipes = new ArrayList<>();
		int barCount = player.getCarriedItems().getInventory().countId(ItemId.SILVER_BAR.id(), Optional.of(false));
		int maxItemId = player.getConfig().RESTRICT_ITEM_ID;
		int[] results = {
			ItemId.UNSTRUNG_HOLY_SYMBOL_OF_SARADOMIN.id(),
			ItemId.UNSTRUNG_UNHOLY_SYMBOL_OF_ZAMORAK.id(),
			ItemId.UNSTRUNG_GUTHIX_SYMBOL.id()
		};
		for (int type = 0; type < results.length; type++) {
			if (onlyType >= 0 && type != onlyType) {
				continue;
			}
			if (type == 1 && MathUtil.maxUnsigned(maxItemId, ItemId.UNSTRUNG_UNHOLY_SYMBOL_OF_ZAMORAK.id()) != maxItemId) {
				continue;
			}
			if (type == 2 && MathUtil.maxUnsigned(maxItemId, ItemId.UNSTRUNG_GUTHIX_SYMBOL.id()) != maxItemId) {
				continue;
			}
			boolean hasMould = player.getCarriedItems().getInventory().countId(silver_moulds[type], Optional.of(false)) > 0;
			recipes.add(new ProductionRecipe(results[type], 16, 1, 1, player.getSkills().getLevel(Skill.CRAFTING.id()) >= 16,
				barCount >= 1 && hasMould,
				new int[]{ItemId.SILVER_BAR.id(), silver_moulds[type]},
				new int[]{-1, -1}, new int[]{1, 1}));
		}
		return recipes.isEmpty() ? null
			: new ProductionSession(ProductionSession.TYPE_CRAFTING, "Choose silver jewelry to craft", ItemId.SILVER_BAR.id(), recipes);
	}

	private void doRangedMouldCasting(final Item bar, final Player player) {
		if (!player.getConfig().CAN_FEATURE_MEMBS) {
			player.message("This feature is members only");
			return;
		}
		ProductionSession session = createRangedMouldProductionSession(player, bar.getCatalogId());
		if (session == null) {
			player.message("You need a ranged weapon mould to cast ranged weapons");
			return;
		}
		if (!ActionSender.isRetroClient(player)) {
			if (!session.hasAnyCraftableRecipe()) {
				player.message("You are not skilled enough for that yet");
				return;
			}
			player.setAttribute("production_session", session);
			player.setAttribute("production_starter", (ProductionStarter) Crafting::beginProductionFromInterface);
			ActionSender.showProductionInterface(player, session);
			return;
		}

		List<RangedMouldRecipe> recipes = getAvailableRangedMouldRecipes(player, bar.getCatalogId());
		if (recipes.isEmpty()) {
			player.message("You need a ranged weapon mould to cast ranged weapons");
			return;
		}
		String[] options = new String[recipes.size() + 1];
		for (int i = 0; i < recipes.size(); i++) {
			options[i] = recipes.get(i).optionName;
		}
		options[recipes.size()] = "Cancel";
		int option = multi(player, options);
		if (option < 0 || option >= recipes.size()) {
			return;
		}
		RangedMouldRecipe recipe = recipes.get(option);
		if (!canStartRangedMouldRecipe(player, bar.getCatalogId(), recipe)) {
			return;
		}
		startbatch(player.getConfig().BATCH_PROGRESSION
			? player.getCarriedItems().getInventory().countId(bar.getCatalogId(), Optional.of(false))
			: 1);
		batchRangedMouldCasting(player, bar, recipe);
	}

	private ProductionSession createRangedMouldProductionSession(Player player, int barId) {
		return createRangedMouldProductionSession(player, barId, -1);
	}

	private ProductionSession createRangedMouldProductionSession(Player player, int barId, int categoryId) {
		List<RangedMouldRecipe> availableRecipes = getAvailableRangedMouldRecipes(player, barId, categoryId);
		if (availableRecipes.isEmpty()) {
			return null;
		}
		List<ProductionRecipe> recipes = new ArrayList<>();
		int level = player.getSkills().getLevel(Skill.CRAFTING.id());
		int barCount = player.getCarriedItems().getInventory().countId(barId, Optional.of(false));
		for (RangedMouldRecipe recipe : availableRecipes) {
			recipes.add(new ProductionRecipe(recipe.resultId, recipe.reqLvl, 1, recipe.amount,
				level >= recipe.reqLvl, barCount >= 1 && hasRangedMould(player, recipe.mouldId),
				new int[]{barId, recipe.mouldId}, new int[]{-1, -1}, new int[]{1, 1}));
		}
		return new ProductionSession(ProductionSession.TYPE_CRAFTING, "Choose ranged weapon parts to cast", barId, recipes);
	}

	private ProductionSession createRangedMouldCategoryProductionSession(Player player, int categoryId) {
		int mouldId = getFurnaceCategoryMouldId(categoryId);
		if (mouldId < 0 || !hasRangedMould(player, mouldId)) {
			player.message("You need the correct mould to make this");
			return null;
		}
		List<ProductionRecipe> recipes = new ArrayList<>();
		int level = player.getSkills().getLevel(Skill.CRAFTING.id());
		for (int barId : MODERN_CASTING_BARS) {
			RangedMouldRecipe recipe = getRangedMouldRecipeForCategory(barId, categoryId);
			if (recipe == null || recipe.resultId <= 0
				|| MathUtil.maxUnsigned(player.getConfig().RESTRICT_ITEM_ID, recipe.resultId) != player.getConfig().RESTRICT_ITEM_ID) {
				continue;
			}
			int barCount = player.getCarriedItems().getInventory().countId(barId, Optional.of(false));
			recipes.add(new ProductionRecipe(recipe.resultId, recipe.reqLvl, 1, recipe.amount,
				level >= recipe.reqLvl, barCount >= 1 && hasRangedMould(player, recipe.mouldId),
				new int[]{barId}, new int[]{-1}, new int[]{1}));
		}
		return recipes.isEmpty() ? null
			: new ProductionSession(ProductionSession.TYPE_CRAFTING, getFurnaceCategoryProductionTitle(categoryId), categoryId, recipes);
	}

	private List<RangedMouldRecipe> getAvailableRangedMouldRecipes(Player player, int barId) {
		return getAvailableRangedMouldRecipes(player, barId, -1);
	}

	private List<RangedMouldRecipe> getAvailableRangedMouldRecipes(Player player, int barId, int categoryId) {
		List<RangedMouldRecipe> recipes = new ArrayList<>();
		if (categoryId < 0 || categoryId == Smelting.FURNACE_CATEGORY_BOLTS) {
			addRangedMouldRecipe(recipes, player, barId, getBoltId(barId), ItemId.BOLT_MOULD.id(), "Bolts", 5, 0);
		}
		if (categoryId < 0 || categoryId == Smelting.FURNACE_CATEGORY_ARROWHEADS) {
			addRangedMouldRecipe(recipes, player, barId, getArrowHeadsId(barId), ItemId.ARROWHEAD_MOULD.id(), "Arrow heads", 5, 0);
		}
		if (categoryId < 0 || categoryId == Smelting.FURNACE_CATEGORY_DARTS) {
			addRangedMouldRecipe(recipes, player, barId, getDartTipsId(barId), ItemId.DART_MOULD.id(), "Dart tips", 5, 2);
		}
		if (categoryId < 0 || categoryId == Smelting.FURNACE_CATEGORY_THROWING_KNIVES) {
			addRangedMouldRecipe(recipes, player, barId, getThrowingKnifeId(barId), ItemId.THROWING_KNIFE_MOULD.id(), "Throwing knives", 5, 4);
		}
		if (categoryId < 0 || categoryId == Smelting.FURNACE_CATEGORY_SHURIKEN) {
			addRangedMouldRecipe(recipes, player, barId, getShurikenId(barId), ItemId.SHURIKEN_MOULD.id(), "Shuriken", 9, 4);
		}
		return recipes;
	}

	private String getFurnaceCategoryProductionTitle(int categoryId) {
		if (categoryId == Smelting.FURNACE_CATEGORY_BOLTS) {
			return "Choose bolts to cast";
		}
		if (categoryId == Smelting.FURNACE_CATEGORY_ARROWHEADS) {
			return "Choose arrow heads to cast";
		}
		if (categoryId == Smelting.FURNACE_CATEGORY_DARTS) {
			return "Choose dart tips to cast";
		}
		if (categoryId == Smelting.FURNACE_CATEGORY_THROWING_KNIVES) {
			return "Choose throwing knives to cast";
		}
		if (categoryId == Smelting.FURNACE_CATEGORY_SHURIKEN) {
			return "Choose shuriken to cast";
		}
		return "Choose ranged weapon parts to cast";
	}

	private boolean beginRangedMouldProduction(Player player, int barId, RangedMouldRecipe recipe, int quantity) {
		if (recipe == null || !canStartRangedMouldRecipe(player, barId, recipe)) {
			return false;
		}
		int available = player.getCarriedItems().getInventory().countId(barId, Optional.of(false));
		int makeCount = Math.min(quantity, available);
		if (makeCount < 1) {
			player.message("You need more materials to make that");
			return false;
		}
		Item bar = new Item(barId);
		Crafting crafting = this;
		startbatch(player, makeCount);
		crafting.batchRangedMouldCasting(player, bar, recipe);
		return true;
	}

	private int getFurnaceCategoryMouldId(int categoryId) {
		if (categoryId == Smelting.FURNACE_CATEGORY_BOLTS) {
			return ItemId.BOLT_MOULD.id();
		}
		if (categoryId == Smelting.FURNACE_CATEGORY_ARROWHEADS) {
			return ItemId.ARROWHEAD_MOULD.id();
		}
		if (categoryId == Smelting.FURNACE_CATEGORY_DARTS) {
			return ItemId.DART_MOULD.id();
		}
		if (categoryId == Smelting.FURNACE_CATEGORY_THROWING_KNIVES) {
			return ItemId.THROWING_KNIFE_MOULD.id();
		}
		if (categoryId == Smelting.FURNACE_CATEGORY_SHURIKEN) {
			return ItemId.SHURIKEN_MOULD.id();
		}
		return -1;
	}

	private RangedMouldRecipe getRangedMouldRecipeForCategory(int barId, int categoryId) {
		if (categoryId == Smelting.FURNACE_CATEGORY_BOLTS) {
			return getRangedMouldRecipeByItemId(barId, getBoltId(barId));
		}
		if (categoryId == Smelting.FURNACE_CATEGORY_ARROWHEADS) {
			return getRangedMouldRecipeByItemId(barId, getArrowHeadsId(barId));
		}
		if (categoryId == Smelting.FURNACE_CATEGORY_DARTS) {
			return getRangedMouldRecipeByItemId(barId, getDartTipsId(barId));
		}
		if (categoryId == Smelting.FURNACE_CATEGORY_THROWING_KNIVES) {
			return getRangedMouldRecipeByItemId(barId, getThrowingKnifeId(barId));
		}
		if (categoryId == Smelting.FURNACE_CATEGORY_SHURIKEN) {
			return getRangedMouldRecipeByItemId(barId, getShurikenId(barId));
		}
		return null;
	}

	private void addRangedMouldRecipe(List<RangedMouldRecipe> recipes, Player player, int barId, int resultId, int mouldId,
									 String optionName, int amount, int levelOffset) {
		if (resultId <= 0 || MathUtil.maxUnsigned(player.getConfig().RESTRICT_ITEM_ID, resultId) != player.getConfig().RESTRICT_ITEM_ID) {
			return;
		}
		int tier = getMetalTier(barId);
		if (tier < 1) {
			return;
		}
		if (!hasRangedMould(player, mouldId)) {
			return;
		}
		int reqLvl = getModernMetalBaseLevel(tier) + levelOffset;
		int exp = getRangedMouldCraftingExp(tier, amount, levelOffset);
		recipes.add(new RangedMouldRecipe(optionName, resultId, mouldId, amount, reqLvl, exp));
	}

	private RangedMouldRecipe getRangedMouldRecipeByItemId(int barId, int itemId) {
		int tier = getMetalTier(barId);
		if (tier < 1) {
			return null;
		}
		if (getBoltId(barId) == itemId) {
			return new RangedMouldRecipe("Bolts", itemId, ItemId.BOLT_MOULD.id(), 5, getModernMetalBaseLevel(tier), getRangedMouldCraftingExp(tier, 5, 0));
		}
		if (getArrowHeadsId(barId) == itemId) {
			return new RangedMouldRecipe("Arrow heads", itemId, ItemId.ARROWHEAD_MOULD.id(), 5, getModernMetalBaseLevel(tier), getRangedMouldCraftingExp(tier, 5, 0));
		}
		if (getDartTipsId(barId) == itemId) {
			return new RangedMouldRecipe("Dart tips", itemId, ItemId.DART_MOULD.id(), 5, getModernMetalBaseLevel(tier) + 2, getRangedMouldCraftingExp(tier, 5, 2));
		}
		if (getThrowingKnifeId(barId) == itemId) {
			return new RangedMouldRecipe("Throwing knives", itemId, ItemId.THROWING_KNIFE_MOULD.id(), 5, getModernMetalBaseLevel(tier) + 4, getRangedMouldCraftingExp(tier, 5, 4));
		}
		if (getShurikenId(barId) == itemId) {
			return new RangedMouldRecipe("Shuriken", itemId, ItemId.SHURIKEN_MOULD.id(), 9, getModernMetalBaseLevel(tier) + 4, getRangedMouldCraftingExp(tier, 9, 4));
		}
		return null;
	}

	private boolean canStartRangedMouldRecipe(Player player, int barId, RangedMouldRecipe recipe) {
		if (!canReceive(player, new Item(recipe.resultId))) {
			player.message("Your client does not support the desired object");
			return false;
		}
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < recipe.reqLvl) {
			player.message("You need a crafting skill of level " + recipe.reqLvl + " to make this");
			return false;
		}
		if (player.getCarriedItems().getInventory().countId(barId, Optional.of(false)) < 1) {
			player.message("You need a bar of metal to make this");
			return false;
		}
		if (!hasRangedMould(player, recipe.mouldId)) {
			player.message("You need a " + player.getWorld().getServer().getEntityHandler().getItemDef(recipe.mouldId).getName().toLowerCase() + " to make this");
			return false;
		}
		return true;
	}

	private void batchRangedMouldCasting(Player player, Item bar, RangedMouldRecipe recipe) {
		if (!canStartRangedMouldRecipe(player, bar.getCatalogId(), recipe)) {
			return;
		}
		if (checkFatigue(player)) {
			return;
		}
		while (!ifinterrupted() && !isbatchcomplete()) {
			Item currentBar = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(bar.getCatalogId(), Optional.of(false))
			);
			if (currentBar == null) {
				player.message("You need a bar of metal to make this");
				break;
			}

			delay(3);
			player.message("You pour the metal into the mould");
			player.getCarriedItems().remove(currentBar);
			player.getCarriedItems().getInventory().add(new Item(recipe.resultId, recipe.amount));
			player.incExp(Skill.CRAFTING.id(), recipe.exp, true);
			updatebatch();
		}
	}

	private boolean hasRangedMould(Player player, int mouldId) {
		return player.getCarriedItems().getInventory().countId(mouldId, Optional.of(false)) > 0;
	}

	private int getRangedMouldCraftingExp(int tier, int amount, int levelOffset) {
		return (tier * 8) + amount + levelOffset;
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

	private int getArrowHeadsId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_ARROW_HEADS.id();
			case COPPER_BAR:
				return ItemId.COPPER_ARROW_HEADS.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_ARROW_HEADS.id();
			case IRON_BAR:
				return ItemId.IRON_ARROW_HEADS.id();
			case STEEL_BAR:
				return ItemId.STEEL_ARROW_HEADS.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_ARROW_HEADS.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_ARROW_HEADS.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_ARROW_HEADS.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_ARROW_HEADS.id();
			case RUNITE_BAR:
				return ItemId.RUNE_ARROW_HEADS.id();
			default:
				return -1;
		}
	}

	private int getBoltId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.CROSSBOW_BOLTS.id();
			case COPPER_BAR:
				return ItemId.COPPER_BOLTS.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_BOLTS.id();
			case IRON_BAR:
				return ItemId.IRON_BOLTS.id();
			case STEEL_BAR:
				return ItemId.STEEL_BOLTS.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_BOLTS.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_BOLTS.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_BOLTS.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_BOLTS.id();
			case RUNITE_BAR:
				return ItemId.RUNE_BOLTS.id();
			default:
				return -1;
		}
	}

	private int getThrowingDartId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_THROWING_DART.id();
			case COPPER_BAR:
				return ItemId.COPPER_THROWING_DART.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_THROWING_DART.id();
			case IRON_BAR:
				return ItemId.IRON_THROWING_DART.id();
			case STEEL_BAR:
				return ItemId.STEEL_THROWING_DART.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_THROWING_DART.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_THROWING_DART.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_THROWING_DART.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_THROWING_DART.id();
			case RUNITE_BAR:
				return ItemId.RUNE_THROWING_DART.id();
			default:
				return -1;
		}
	}

	private int getDartTipsId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_DART_TIPS.id();
			case COPPER_BAR:
				return ItemId.COPPER_DART_TIPS.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_DART_TIPS.id();
			case IRON_BAR:
				return ItemId.IRON_DART_TIPS.id();
			case STEEL_BAR:
				return ItemId.STEEL_DART_TIPS.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_DART_TIPS.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_DART_TIPS.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_DART_TIPS.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_DART_TIPS.id();
			case RUNITE_BAR:
				return ItemId.RUNE_DART_TIPS.id();
			default:
				return -1;
		}
	}

	private int getThrowingKnifeId(int barId) {
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

	private int getShurikenId(int barId) {
		switch (ItemId.getById(barId)) {
			case TIN_BAR:
				return ItemId.TIN_SHURIKEN.id();
			case COPPER_BAR:
				return ItemId.COPPER_SHURIKEN.id();
			case BRONZE_BAR:
				return ItemId.BRONZE_SHURIKEN.id();
			case IRON_BAR:
				return ItemId.IRON_SHURIKEN.id();
			case STEEL_BAR:
				return ItemId.STEEL_SHURIKEN.id();
			case MITHRIL_BAR:
				return ItemId.MITHRIL_SHURIKEN.id();
			case TITAN_STEEL_BAR:
				return ItemId.TITAN_STEEL_SHURIKEN.id();
			case ADAMANTITE_BAR:
				return ItemId.ADAMANTITE_SHURIKEN.id();
			case ORICHALCUM_BAR:
				return ItemId.ORICHALCUM_SHURIKEN.id();
			case RUNITE_BAR:
				return ItemId.RUNE_SHURIKEN.id();
			default:
				return -1;
		}
	}

	private int[] getGoldJewelryProductionOutputIds(Player player) {
		List<Integer> ids = new ArrayList<>();
		ids.add(ItemId.GOLD_RING.id());
		ids.add(ItemId.SAPPHIRE_RING.id());
		ids.add(ItemId.EMERALD_RING.id());
		ids.add(ItemId.RUBY_RING.id());
		ids.add(ItemId.DIAMOND_RING.id());
		if (player.getConfig().MEMBER_WORLD) {
			ids.add(ItemId.DRAGONSTONE_RING.id());
		}

		ids.add(ItemId.GOLD_NECKLACE.id());
		ids.add(ItemId.SAPPHIRE_NECKLACE.id());
		ids.add(ItemId.EMERALD_NECKLACE.id());
		ids.add(ItemId.RUBY_NECKLACE.id());
		ids.add(ItemId.DIAMOND_NECKLACE.id());
		if (player.getConfig().MEMBER_WORLD) {
			ids.add(ItemId.DRAGONSTONE_NECKLACE.id());
		}

		ids.add(ItemId.UNSTRUNG_GOLD_AMULET.id());
		ids.add(ItemId.UNSTRUNG_SAPPHIRE_AMULET.id());
		ids.add(ItemId.UNSTRUNG_EMERALD_AMULET.id());
		ids.add(ItemId.UNSTRUNG_RUBY_AMULET.id());
		ids.add(ItemId.UNSTRUNG_DIAMOND_AMULET.id());
		if (player.getConfig().MEMBER_WORLD) {
			ids.add(ItemId.UNSTRUNG_DRAGONSTONE_AMULET.id());
		}

		int[] output = new int[ids.size()];
		for (int i = 0; i < ids.size(); i++) {
			output[i] = ids.get(i);
		}
		return output;
	}

	private ItemCraftingDef getGoldJewelryProductionDef(Player player, int itemId) {
		int mouldId = getRequiredGoldMouldId(itemId);
		if (mouldId <= 0) {
			return null;
		}
		return player.getWorld().getServer().getEntityHandler().getCraftingDef(itemId);
	}

	private int getRequiredGoldMouldId(int itemId) {
		switch (ItemId.getById(itemId)) {
			case GOLD_RING:
			case SAPPHIRE_RING:
			case EMERALD_RING:
			case RUBY_RING:
			case DIAMOND_RING:
			case DRAGONSTONE_RING:
				return ItemId.RING_MOULD.id();
			case GOLD_NECKLACE:
			case SAPPHIRE_NECKLACE:
			case EMERALD_NECKLACE:
			case RUBY_NECKLACE:
			case DIAMOND_NECKLACE:
			case DRAGONSTONE_NECKLACE:
				return ItemId.NECKLACE_MOULD.id();
			case UNSTRUNG_GOLD_AMULET:
			case UNSTRUNG_SAPPHIRE_AMULET:
			case UNSTRUNG_EMERALD_AMULET:
			case UNSTRUNG_RUBY_AMULET:
			case UNSTRUNG_DIAMOND_AMULET:
			case UNSTRUNG_DRAGONSTONE_AMULET:
				return ItemId.AMULET_MOULD.id();
			default:
				return -1;
		}
	}

	private int getSilverJewelryProductionType(Player player, int itemId) {
		if (itemId == ItemId.UNSTRUNG_HOLY_SYMBOL_OF_SARADOMIN.id()) {
			return 0;
		}
		if (itemId == ItemId.UNSTRUNG_UNHOLY_SYMBOL_OF_ZAMORAK.id()
			&& MathUtil.maxUnsigned(player.getConfig().RESTRICT_ITEM_ID, ItemId.UNSTRUNG_UNHOLY_SYMBOL_OF_ZAMORAK.id()) == player.getConfig().RESTRICT_ITEM_ID) {
			return 1;
		}
		if (itemId == ItemId.UNSTRUNG_GUTHIX_SYMBOL.id()
			&& MathUtil.maxUnsigned(player.getConfig().RESTRICT_ITEM_ID, ItemId.UNSTRUNG_GUTHIX_SYMBOL.id()) == player.getConfig().RESTRICT_ITEM_ID) {
			return 2;
		}
		return -1;
	}

	private boolean canStartGoldJewelryRecipe(Player player, ItemCraftingDef def, int mouldId) {
		if (!canReceive(player, new Item(def.getItemID()))) {
			player.message("Your client does not support the desired object");
			return false;
		}
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < def.getReqLevel()) {
			player.playerServerMessage(MessageType.QUEST, "You need a crafting skill of level " + def.getReqLevel() + " to make this");
			return false;
		}
		if (checkFatigue(player)) {
			return false;
		}
		if (player.getCarriedItems().getInventory().countId(ItemId.GOLD_BAR.id(), Optional.of(false)) < 1) {
			player.message("You don't have a gold bar.");
			return false;
		}
		if (mouldId > 0 && player.getCarriedItems().getInventory().countId(mouldId, Optional.of(false)) < 1) {
			player.message("You need a " + player.getWorld().getServer().getEntityHandler().getItemDef(mouldId).getName() + " to make this");
			return false;
		}
		if (def.getReqGem() != ItemId.NOTHING.id()
			&& player.getCarriedItems().getInventory().countId(def.getReqGem(), Optional.of(false)) < 1) {
			tellPlayerNoGem(player, def);
			return false;
		}
		return true;
	}

	private boolean canStartSilverJewelryRecipe(Player player, int type) {
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < 16) {
			player.playerServerMessage(MessageType.QUEST, "You need a crafting skill of level 16 to make this");
			return false;
		}
		if (checkFatigue(player)) {
			return false;
		}
		if (player.getCarriedItems().getInventory().countId(ItemId.SILVER_BAR.id(), Optional.of(false)) < 1) {
			player.message("You need a silver bar to make this");
			return false;
		}
		if (player.getCarriedItems().getInventory().countId(silver_moulds[type], Optional.of(false)) < 1) {
			player.message("You need a " + player.getWorld().getServer().getEntityHandler().getItemDef(silver_moulds[type]).getName() + " to make this");
			return false;
		}
		return true;
	}

	private HideArmorPiece getHideArmorPieceByItemId(HideArmorRecipe recipe, int itemId) {
		for (HideArmorPiece piece : getHideArmorPieces(recipe)) {
			if (piece.resultId == itemId) {
				return piece;
			}
		}
		return null;
	}

	private WoolGarmentRecipe getWoolRecipeByItemId(int itemId) {
		for (WoolGarmentRecipe recipe : WOOL_GARMENT_RECIPES) {
			if (recipe.resultId == itemId) {
				return recipe;
			}
		}
		return null;
	}

	private PotteryRecipe getPotteryRecipeByItemId(Player player, int itemId) {
		for (PotteryRecipe recipe : POTTERY_RECIPES) {
			if (recipe.resultId != itemId) {
				continue;
			}
			if (recipe.gatedByItemSupport
				&& MathUtil.maxUnsigned(player.getConfig().RESTRICT_ITEM_ID, recipe.resultId) != player.getConfig().RESTRICT_ITEM_ID) {
				return null;
			}
			return recipe;
		}
		return null;
	}

	private GlassBlowingRecipe getGlassBlowingRecipeByItemId(int itemId) {
		for (GlassBlowingRecipe recipe : GLASS_BLOWING_RECIPES) {
			if (recipe.resultId == itemId) {
				return recipe;
			}
		}
		return null;
	}

	private boolean canStartLeatherRecipe(Player player, int leatherId, HideArmorPiece piece) {
		Item result = new Item(piece.resultId, 1);
		int threadCost = getLeatherThreadCost(piece.materialCost);
		if (!canReceive(player, result)) {
			player.message("Your client does not support the desired object");
			return false;
		}
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < piece.reqLvl) {
			player.playerServerMessage(MessageType.QUEST, "You need to have a crafting of level " + piece.reqLvl + " or higher to make " + result.getDef(player.getWorld()).getName());
			return false;
		}
		if (player.getCarriedItems().getInventory().countId(leatherId, Optional.of(false)) < piece.materialCost) {
			player.message("You need " + piece.materialCost + " pieces of material to make that");
			return false;
		}
		if (getAvailableThreadUses(player) < threadCost) {
			player.message("You need more thread to make that");
			return false;
		}
		if (checkFatigue(player)) {
			return false;
		}
		return true;
	}

	private boolean canStartBrownApronRecipe(Player player) {
		Item result = new Item(ItemId.BROWN_APRON.id(), 1);
		if (!canReceive(player, result)) {
			player.message("Your client does not support the desired object");
			return false;
		}
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < BROWN_APRON_CRAFTING_LEVEL) {
			player.playerServerMessage(MessageType.QUEST, "You need to have a crafting of level " + BROWN_APRON_CRAFTING_LEVEL + " or higher to make " + result.getDef(player.getWorld()).getName());
			return false;
		}
		if (player.getCarriedItems().getInventory().countId(ItemId.COW_HIDE.id(), Optional.of(false)) < BROWN_APRON_COW_HIDE_COST) {
			player.message("You need " + BROWN_APRON_COW_HIDE_COST + " pieces of material to make that");
			return false;
		}
		if (getAvailableThreadUses(player) < 1) {
			player.message("You need some thread to make anything out of leather");
			return false;
		}
		if (checkFatigue(player)) {
			return false;
		}
		return true;
	}

	private boolean canStartWoolRecipe(Player player, WoolGarmentRecipe recipe) {
		Item result = new Item(recipe.resultId, 1);
		if (!canReceive(player, result)) {
			player.message("Your client does not support the desired object");
			return false;
		}
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < recipe.reqLvl) {
			player.message("You need a crafting skill of " + recipe.reqLvl + " or above to do that");
			return false;
		}
		if (player.getCarriedItems().getInventory().countId(ItemId.BALL_OF_WOOL.id(), Optional.of(false)) < recipe.woolCost) {
			player.message("You need " + recipe.woolCost + " balls of wool to make that");
			return false;
		}
		if (checkFatigue(player)) {
			return false;
		}
		return true;
	}

	private boolean canStartPotteryRecipe(Player player, PotteryRecipe recipe) {
		Item result = new Item(recipe.resultId, 1);
		if (!canReceive(player, result)) {
			player.message("Your client does not support the desired object");
			return false;
		}
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < recipe.reqLvl) {
			player.playerServerMessage(MessageType.QUEST, "You need to have a crafting of level " + recipe.reqLvl + " or higher to make " + getPotteryBatchMessage(recipe.resultId));
			return false;
		}
		if (player.getCarriedItems().getInventory().countId(ItemId.SOFT_CLAY.id(), Optional.of(false)) < 1) {
			player.message("You need some soft clay to make that");
			return false;
		}
		if (checkFatigue(player)) {
			return false;
		}
		return true;
	}

	private boolean canStartGlassRecipe(Player player, GlassBlowingRecipe recipe) {
		Item result = new Item(recipe.resultId, 1);
		if (!canReceive(player, result)) {
			player.message("Your client does not support the desired object");
			return false;
		}
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < recipe.reqLvl) {
			player.message("You need a crafting level of " + recipe.reqLvl + " to make " + recipe.resultGen);
			return false;
		}
		if (player.getCarriedItems().getInventory().countId(ItemId.MOLTEN_GLASS.id(), Optional.of(false)) < 1) {
			player.message("You need some molten glass to make that");
			return false;
		}
		if (checkFatigue(player)) {
			return false;
		}
		return true;
	}

	private int getAvailableThreadUses(Player player) {
		int threadCount = player.getCarriedItems().getInventory().countId(ItemId.THREAD.id(), Optional.of(false));
		if (threadCount < 1) {
			return 0;
		}
		int usedParts = player.getCache().hasKey("part_reel_thread") ? player.getCache().getInt("part_reel_thread") : 0;
		return Math.max(0, (threadCount * 5) - usedParts);
	}

	private int getLeatherThreadCost(int materialCost) {
		return materialCost * THREAD_USES_PER_LEATHER_MATERIAL;
	}

	private String getPotteryBatchMessage(int resultId) {
		switch (ItemId.getById(resultId)) {
			case UNFIRED_POT:
				return "a pot";
			case UNFIRED_PIE_DISH:
				return "pie dishes";
			case UNFIRED_BOWL:
				return "a bowl";
			default:
				return "that";
		}
	}

	private void batchWoolGarment(Player player, WoolGarmentRecipe recipe) {
		if (!canReceive(player, new Item(recipe.resultId))) {
			player.message("Your client does not support the desired object");
			return;
		}
		if (player.getSkills().getLevel(Skill.CRAFTING.id()) < recipe.reqLvl) {
			player.message("You need a crafting skill of " + recipe.reqLvl + " or above to do that");
			return;
		}
		if (checkFatigue(player)) {
			return;
		}
		while (!ifinterrupted() && !isbatchcomplete()) {
			if (player.getCarriedItems().getInventory().countId(ItemId.BALL_OF_WOOL.id(), Optional.of(false)) < recipe.woolCost) {
				player.message("You need " + recipe.woolCost + " balls of wool to make that");
				break;
			}
			delay(3);
			for (int used = 0; used < recipe.woolCost; used++) {
				if (player.getCarriedItems().remove(new Item(ItemId.BALL_OF_WOOL.id())) == -1) {
					return;
				}
			}
			player.message("You carefully stitch the wool into a " + recipe.optionName.toLowerCase());
			player.getCarriedItems().getInventory().add(new Item(recipe.resultId));
			player.incExp(Skill.CRAFTING.id(), recipe.exp, true);
			updatebatch();
		}
	}

	private void useWool(Player player, final Item woolBall, final Item item) {
		int newID;
		switch (ItemId.getById(item.getCatalogId())) {
			case UNSTRUNG_HOLY_SYMBOL_OF_SARADOMIN:
				newID = ItemId.UNBLESSED_HOLY_SYMBOL.id();
				break;
			case UNSTRUNG_UNHOLY_SYMBOL_OF_ZAMORAK:
				newID = ItemId.UNBLESSED_UNHOLY_SYMBOL_OF_ZAMORAK.id();
				break;
			case UNSTRUNG_GUTHIX_SYMBOL:
				newID = ItemId.UNBLESSED_GUTHIX_SYMBOL.id();
				break;
			case UNSTRUNG_GOLD_AMULET:
				newID = ItemId.GOLD_AMULET.id();
				break;
			case UNSTRUNG_SAPPHIRE_AMULET:
				newID = ItemId.SAPPHIRE_AMULET.id();
				break;
			case UNSTRUNG_EMERALD_AMULET:
				newID = ItemId.EMERALD_AMULET.id();
				break;
			case UNSTRUNG_RUBY_AMULET:
				newID = ItemId.RUBY_AMULET.id();
				break;
			case UNSTRUNG_DIAMOND_AMULET:
				newID = ItemId.DIAMOND_AMULET.id();
				break;
			case UNSTRUNG_DRAGONSTONE_AMULET:
				newID = ItemId.UNENCHANTED_DRAGONSTONE_AMULET.id();
				break;
			default:
				return;
		}
		int woolAmount = player.getCarriedItems().getInventory().countId(woolBall.getCatalogId(), Optional.of(false));
		int amuletAmount = player.getCarriedItems().getInventory().countId(item.getCatalogId(), Optional.of(false));

		int repeat = 1;
		if (player.getConfig().BATCH_PROGRESSION) {
			repeat = Math.min(woolAmount, amuletAmount);
		}

		startbatch(repeat);
		batchString(player, item, woolBall, newID);
	}

	private void batchString(Player player, Item item, Item woolBall, int newID) {
		while (!ifinterrupted() && !isbatchcomplete()) {
			item = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(item.getCatalogId(), Optional.of(false))
			);
			woolBall = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(woolBall.getCatalogId(), Optional.of(false))
			);
			if (item == null || woolBall == null) {
				break;
			}

			player.getCarriedItems().remove(woolBall);
			player.getCarriedItems().remove(item);
			player.message("You put some string on your " + item.getDef(player.getWorld()).getName().toLowerCase());
			player.getCarriedItems().getInventory().add(new Item(newID));
			updatebatch();
		}
	}

	private void useWater(Player player, Item water, Item item) {
		int repeat = 1;
		if (player.getConfig().BATCH_PROGRESSION) {
			repeat = Math.min(player.getCarriedItems().getInventory().countId(water.getCatalogId(), Optional.of(false)),
				player.getCarriedItems().getInventory().countId(item.getCatalogId(), Optional.of(false)));
		}

		startbatch(repeat);
		batchWaterClay(player, water, item);
	}

	private void batchWaterClay(Player player, Item water, Item item) {
		int jugID = Formulae.getEmptyJug(water.getCatalogId());
		if (jugID == -1) return;

		while (!ifinterrupted() && !isbatchcomplete()) {
			water = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(water.getCatalogId(), Optional.of(false))
			);
			item = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(item.getCatalogId(), Optional.of(false))
			);
			if (water == null || item == null) {
				break;
			}
			player.getCarriedItems().remove(water);
			player.getCarriedItems().remove(item);
			player.getCarriedItems().getInventory().add(new Item(jugID));
			player.getCarriedItems().getInventory().add(new Item(ItemId.SOFT_CLAY.id()));
			player.message("You mix the clay and water");
			player.message("You now have some soft workable clay");
			updatebatch();
		}
	}

	private boolean consumeThreadUse(Player player) {
		if (!player.getCache().hasKey("part_reel_thread")) {
			player.getCache().set("part_reel_thread", 1);
			return player.getCarriedItems().getInventory().countId(ItemId.THREAD.id(), Optional.of(false)) > 0;
		}

		int parts = player.getCache().getInt("part_reel_thread");
		if (parts >= 4) {
			player.message("You use up one of your reels of thread");
			player.getCache().remove("part_reel_thread");
			player.getCarriedItems().remove(new Item(ItemId.THREAD.id()));
			return player.getCarriedItems().getInventory().countId(ItemId.THREAD.id(), Optional.of(false)) > 0;
		}

		player.getCache().put("part_reel_thread", parts + 1);
		return true;
	}

	private void consumeThreadUses(Player player, int amount) {
		for (int i = 0; i < amount; i++) {
			consumeThreadUse(player);
		}
	}

	private String potteryItemName(String rawName) {
		String uncapName = rawName.toLowerCase();
		if (uncapName.startsWith("unfired ")) {
			return uncapName.substring(8);
		}
		return uncapName;
	}

	private boolean checkFatigue(Player player) {
		if (player.getConfig().WANT_FATIGUE
				&& player.getConfig().STOP_SKILLING_FATIGUED >= 2
				&& player.getFatigue() >= player.MAX_FATIGUE) {
			player.message("You are too tired to craft");
			return true;
		}
		return false;
	}

	@Override
	public boolean blockUseInv(Player player, Integer invIndex, Item item1, Item item2) {
		ItemGemDef gemDef = player.getWorld().getServer().getEntityHandler().getItemGemDef(item1.getCatalogId());
		ItemGemDef gemDef2 = player.getWorld().getServer().getEntityHandler().getItemGemDef(item2.getCatalogId());
		int item1ID = item1.getCatalogId();
		int item2ID = item2.getCatalogId();
		if (item1ID == ItemId.CHISEL.id() && (gemDef != null || gemDef2 != null)) {
			return true;
		} else if (item2ID == ItemId.CHISEL.id() && (gemDef != null || gemDef2 != null)) {
			return true;
		} else if (item1ID == ItemId.GLASSBLOWING_PIPE.id()) {
			return true;
		} else if (item2ID == ItemId.GLASSBLOWING_PIPE.id()) {
			return true;
		} else if (item1ID == ItemId.NEEDLE.id()) {
			return true;
		} else if (item2ID == ItemId.NEEDLE.id()) {
			return true;
		} else if (item1ID == ItemId.BALL_OF_WOOL.id()) {
			return true;
		} else if (item2ID == ItemId.BALL_OF_WOOL.id()) {
			return true;
		} else if ((item1ID == ItemId.BUCKET_OF_WATER.id() || item1ID == ItemId.JUG_OF_WATER.id()) && item2ID == ItemId.CLAY.id()) {
			return true;
		} else if ((item2ID == ItemId.BUCKET_OF_WATER.id() || item2ID == ItemId.JUG_OF_WATER.id()) && item1ID == ItemId.CLAY.id()) {
			return true;
		} else
			return item1ID == ItemId.MOLTEN_GLASS.id() && item2ID == ItemId.LENS_MOULD.id() || item1ID == ItemId.LENS_MOULD.id() && item2ID == ItemId.MOLTEN_GLASS.id();
	}

	@Override
	public boolean blockUseLoc(Player player, GameObject obj, Item item) {
		return craftingTypeChecks(obj, item, player);
	}
}
