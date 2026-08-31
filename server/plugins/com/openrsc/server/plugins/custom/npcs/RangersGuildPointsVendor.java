package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.content.RangersGuildPoints;
import com.openrsc.server.content.production.ProductionRecipe;
import com.openrsc.server.content.production.ProductionSession;
import com.openrsc.server.content.production.ProductionStarter;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import java.util.ArrayList;
import java.util.List;

import static com.openrsc.server.plugins.Functions.npcsay;

public class RangersGuildPointsVendor implements TalkNpcTrigger, OpNpcTrigger {
	private static final Category[] CATEGORIES = new Category[] {
		new Category("Longbows", ItemId.LONGBOW, new Reward[] {
			new Reward(ItemId.LONGBOW, 125),
			new Reward(ItemId.OAK_LONGBOW, 200),
			new Reward(ItemId.PINE_LONGBOW, 300),
			new Reward(ItemId.WILLOW_LONGBOW, 450),
			new Reward(ItemId.PALM_LONGBOW, 625),
			new Reward(ItemId.MAPLE_LONGBOW, 875),
			new Reward(ItemId.YEW_LONGBOW, 1250),
			new Reward(ItemId.MAGIC_LONGBOW, 2000),
			new Reward(ItemId.EBONY_LONGBOW, 2875),
			new Reward(ItemId.BLOOD_LONGBOW, 4000),
		}),
		new Category("Shortbows", ItemId.SHORTBOW, new Reward[] {
			new Reward(ItemId.SHORTBOW, 100),
			new Reward(ItemId.OAK_SHORTBOW, 175),
			new Reward(ItemId.PINE_SHORTBOW, 250),
			new Reward(ItemId.WILLOW_SHORTBOW, 375),
			new Reward(ItemId.PALM_SHORTBOW, 550),
			new Reward(ItemId.MAPLE_SHORTBOW, 775),
			new Reward(ItemId.YEW_SHORTBOW, 1125),
			new Reward(ItemId.MAGIC_SHORTBOW, 1750),
			new Reward(ItemId.EBONY_SHORTBOW, 2500),
			new Reward(ItemId.BLOOD_SHORTBOW, 3500),
		}),
		new Category("Crossbows", ItemId.CROSSBOW, new Reward[] {
			new Reward(ItemId.CROSSBOW, 125),
			new Reward(ItemId.OAK_CROSSBOW, 250),
			new Reward(ItemId.WILLOW_CROSSBOW, 400),
			new Reward(ItemId.PALM_CROSSBOW, 625),
			new Reward(ItemId.MAPLE_CROSSBOW, 875),
			new Reward(ItemId.YEW_CROSSBOW, 1250),
			new Reward(ItemId.MAGIC_CROSSBOW, 2500),
			new Reward(ItemId.EBONY_CROSSBOW, 3250),
			new Reward(ItemId.BLOOD_CROSSBOW, 4250),
		}),
		new Category("Throwing Knives", ItemId.BRONZE_THROWING_KNIFE, new Reward[] {
			new Reward(ItemId.TIN_THROWING_KNIFE, 1),
			new Reward(ItemId.COPPER_THROWING_KNIFE, 2),
			new Reward(ItemId.BRONZE_THROWING_KNIFE, 3),
			new Reward(ItemId.IRON_THROWING_KNIFE, 4),
			new Reward(ItemId.STEEL_THROWING_KNIFE, 5),
			new Reward(ItemId.MITHRIL_THROWING_KNIFE, 6),
			new Reward(ItemId.TITAN_STEEL_THROWING_KNIFE, 8),
			new Reward(ItemId.ADAMANTITE_THROWING_KNIFE, 10),
			new Reward(ItemId.ORICHALCUM_THROWING_KNIFE, 14),
			new Reward(ItemId.RUNE_THROWING_KNIFE, 20),
		}),
		new Category("Darts", ItemId.BRONZE_THROWING_DART, new Reward[] {
			new Reward(ItemId.TIN_THROWING_DART, 1),
			new Reward(ItemId.COPPER_THROWING_DART, 2),
			new Reward(ItemId.BRONZE_THROWING_DART, 3),
			new Reward(ItemId.IRON_THROWING_DART, 4),
			new Reward(ItemId.STEEL_THROWING_DART, 5),
			new Reward(ItemId.MITHRIL_THROWING_DART, 6),
			new Reward(ItemId.TITAN_STEEL_THROWING_DART, 8),
			new Reward(ItemId.ADAMANTITE_THROWING_DART, 10),
			new Reward(ItemId.ORICHALCUM_THROWING_DART, 14),
			new Reward(ItemId.RUNE_THROWING_DART, 20),
		}),
		new Category("Arrows", ItemId.BRONZE_ARROWS, new Reward[] {
			new Reward(ItemId.TIN_ARROWS, 1),
			new Reward(ItemId.COPPER_ARROWS, 2),
			new Reward(ItemId.BRONZE_ARROWS, 3),
			new Reward(ItemId.IRON_ARROWS, 4),
			new Reward(ItemId.STEEL_ARROWS, 5),
			new Reward(ItemId.MITHRIL_ARROWS, 6),
			new Reward(ItemId.TITAN_STEEL_ARROWS, 8),
			new Reward(ItemId.ADAMANTITE_ARROWS, 10),
			new Reward(ItemId.ORICHALCUM_ARROWS, 14),
			new Reward(ItemId.RUNE_ARROWS, 20),
			new Reward(ItemId.DRAGON_ARROWS, 30),
			new Reward(ItemId.POISON_DRAGON_ARROWS, 30),
		}),
		new Category("Bolts", ItemId.BRONZE_BOLTS, new Reward[] {
			new Reward(ItemId.CROSSBOW_BOLTS, 1),
			new Reward(ItemId.COPPER_BOLTS, 2),
			new Reward(ItemId.BRONZE_BOLTS, 3),
			new Reward(ItemId.IRON_BOLTS, 4),
			new Reward(ItemId.STEEL_BOLTS, 5),
			new Reward(ItemId.MITHRIL_BOLTS, 6),
			new Reward(ItemId.TITAN_STEEL_BOLTS, 8),
			new Reward(ItemId.ADAMANTITE_BOLTS, 10),
			new Reward(ItemId.ORICHALCUM_BOLTS, 14),
			new Reward(ItemId.RUNE_BOLTS, 20),
			new Reward(ItemId.DRAGON_BOLTS, 30),
			new Reward(ItemId.POISON_DRAGON_BOLTS, 30),
		}),
		new Category("Shuriken", ItemId.BRONZE_SHURIKEN, new Reward[] {
			new Reward(ItemId.TIN_SHURIKEN, 1),
			new Reward(ItemId.COPPER_SHURIKEN, 2),
			new Reward(ItemId.BRONZE_SHURIKEN, 3),
			new Reward(ItemId.IRON_SHURIKEN, 4),
			new Reward(ItemId.STEEL_SHURIKEN, 5),
			new Reward(ItemId.MITHRIL_SHURIKEN, 6),
			new Reward(ItemId.TITAN_STEEL_SHURIKEN, 8),
			new Reward(ItemId.ADAMANTITE_SHURIKEN, 10),
			new Reward(ItemId.ORICHALCUM_SHURIKEN, 14),
			new Reward(ItemId.RUNE_SHURIKEN, 20),
		}),
	};

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return isPointsVendor(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		npcsay(player, npc,
			"Rangers Guild points come from ranged kills in the basement",
			"Giants give 7, skeletons 12, lesser demons 16",
			"And green dragons give 22 points",
			"I can redeem them for ranged supplies");
		openCategoryInterface(player);
	}

	@Override
	public boolean blockOpNpc(Player player, Npc npc, String command) {
		return isPointsVendor(npc) && command.equalsIgnoreCase("Redeem");
	}

	@Override
	public void onOpNpc(Player player, Npc npc, String command) {
		openCategoryInterface(player);
	}

	public static boolean beginRedemptionFromInterface(Player player, ProductionSession session, int itemId, int quantity) {
		if (session == null) {
			return false;
		}
		if (session.isType(ProductionSession.TYPE_RANGERS_REDEMPTION_CATEGORY)) {
			Category category = getCategoryByIcon(itemId);
			if (category == null) {
				return false;
			}
			openRewardInterface(player, category);
			return true;
		}
		if (!session.isType(ProductionSession.TYPE_RANGERS_REDEMPTION)) {
			return false;
		}

		Reward reward = getReward(itemId);
		if (reward == null) {
			return false;
		}
		return redeem(player, session, reward, quantity);
	}

	private static void openCategoryInterface(Player player) {
		List<ProductionRecipe> recipes = new ArrayList<ProductionRecipe>();
		for (Category category : CATEGORIES) {
			recipes.add(new ProductionRecipe(category.icon.id(), 1, 1, 1, true, true));
		}
		ProductionSession session = new ProductionSession(
			ProductionSession.TYPE_RANGERS_REDEMPTION_CATEGORY,
			"Redeem Rangers Guild points",
			-1,
			RangersGuildPoints.getPoints(player),
			recipes, "rangers-guild-points", null);
		player.setAttribute("production_session", session);
		player.setAttribute("production_starter", (ProductionStarter) RangersGuildPointsVendor::beginRedemptionFromInterface);
		ActionSender.showProductionInterface(player, session);
	}

	private static void openRewardInterface(Player player, Category category) {
		int points = RangersGuildPoints.getPoints(player);
		List<ProductionRecipe> recipes = new ArrayList<ProductionRecipe>();
		for (Reward reward : category.rewards) {
			recipes.add(new ProductionRecipe(
				reward.itemId.id(),
				1,
				reward.cost,
				reward.amount,
				true,
				points >= reward.cost));
		}
		ProductionSession session = new ProductionSession(
			ProductionSession.TYPE_RANGERS_REDEMPTION,
			"Redeem points - " + category.label,
			-1,
			points,
			recipes, "rangers-guild-points", null);
		player.setAttribute("production_session", session);
		player.setAttribute("production_starter", (ProductionStarter) RangersGuildPointsVendor::beginRedemptionFromInterface);
		ActionSender.showProductionInterface(player, session);
	}

	private static boolean redeem(Player player, ProductionSession session, Reward reward, int quantity) {
		if (quantity < 1) {
			return false;
		}

		long totalCost = (long) reward.cost * (long) quantity;
		long totalAmount = (long) reward.amount * (long) quantity;
		if (totalCost > Integer.MAX_VALUE || totalAmount > Integer.MAX_VALUE) {
			player.message("Choose a smaller quantity");
			return false;
		}

		if (RangersGuildPoints.getPoints(player) < totalCost) {
			player.message("You need " + pointsLabel((int) totalCost) + " for that");
			return false;
		}

		Item item = new Item(reward.itemId.id(), (int) totalAmount);
		if (!player.getCarriedItems().getInventory().canHold(item)) {
			player.message("You don't have enough inventory space for that");
			return false;
		}

		if (!RangersGuildPoints.spendPoints(player, (int) totalCost)) {
			player.message("You don't have enough points for that");
			return false;
		}
		if (!player.getCarriedItems().getInventory().add(item)) {
			RangersGuildPoints.addPoints(player, (int) totalCost);
			player.message("Something went wrong adding that item");
			return false;
		}

		player.message("You redeem " + pointsLabel((int) totalCost) + " for " + item.getAmount()
			+ " x " + item.getDef(player.getWorld()).getName() + ".");
		session.setResourceAmount(RangersGuildPoints.getPoints(player));
		return true;
	}

	private boolean isPointsVendor(Npc npc) {
		return npc.getID() == NpcId.RANGERS_GUILD_POINTS_VENDOR.id();
	}

	private static Category getCategoryByIcon(int itemId) {
		for (Category category : CATEGORIES) {
			if (category.icon.id() == itemId) {
				return category;
			}
		}
		return null;
	}

	private static Reward getReward(int itemId) {
		for (Category category : CATEGORIES) {
			for (Reward reward : category.rewards) {
				if (reward.itemId.id() == itemId) {
					return reward;
				}
			}
		}
		return null;
	}

	private static String pointsLabel(int points) {
		return points + " Rangers Guild point" + (points == 1 ? "" : "s");
	}

	private static final class Category {
		private final String label;
		private final ItemId icon;
		private final Reward[] rewards;

		private Category(String label, ItemId icon, Reward[] rewards) {
			this.label = label;
			this.icon = icon;
			this.rewards = rewards;
		}
	}

	private static final class Reward {
		private final ItemId itemId;
		private final int amount;
		private final int cost;

		private Reward(ItemId itemId, int cost) {
			this.itemId = itemId;
			this.amount = 1;
			this.cost = cost;
		}
	}
}
