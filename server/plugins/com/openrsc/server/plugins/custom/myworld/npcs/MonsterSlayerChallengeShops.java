package com.openrsc.server.plugins.custom.myworld.npcs;

import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerData;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerShopService;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerState;
import com.openrsc.server.content.production.PointShopDetails;
import com.openrsc.server.content.production.ProductionRecipe;
import com.openrsc.server.content.production.ProductionSession;
import com.openrsc.server.content.production.ProductionStarter;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;

import java.util.ArrayList;
import java.util.List;

/** Graphical, server-authoritative presentation for the six Slayer shops. */
public final class MonsterSlayerChallengeShops {
	private MonsterSlayerChallengeShops() { }

	public static void open(Player player, Npc npc, String shopKey) {
		open(player, shopKey);
	}

	public static void open(Player player, String shopKey) {
		try {
			MonsterSlayerData data = player.getWorld().getMonsterSlayerData();
			MonsterSlayerDefinitions.Shop shop = data.getShop(shopKey);
			MonsterSlayerShopService service = player.getWorld().getMonsterSlayerShopService();
			MonsterSlayerState.Snapshot state = MonsterSlayerState.read(player.getCache(), data);
			if (shop == null || service == null || !state.getRank().isAtLeast(
				com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerRank.fromCode(shop.getChallenge().getCode() + 1))) {
				player.message("You aren't high enough rank to use this shop.");
				return;
			}
			ProductionSession session = session(player, shop, service, state);
			player.setAttribute("production_session", session);
			player.setAttribute("production_starter", (ProductionStarter) MonsterSlayerChallengeShops::redeemFromInterface);
			ActionSender.showProductionInterface(player, session);
		} catch (RuntimeException failure) {
			player.message("Your Monster Slayer record needs staff attention.");
		}
	}

	public static boolean redeemFromInterface(Player player, ProductionSession session, int itemId, int quantity) {
		if (session == null || !session.isType(ProductionSession.TYPE_MONSTER_SLAYER_REDEMPTION)
			|| quantity < 1 || session.getRecipeByItemId(itemId) == null) return false;
		String shopKey = session.getMemoryKey();
		if (shopKey == null || !shopKey.startsWith("monster-slayer-shop:")) return false;
		shopKey = shopKey.substring("monster-slayer-shop:".length());
		MonsterSlayerData data = player.getWorld().getMonsterSlayerData();
		MonsterSlayerDefinitions.Shop shop = data.getShop(shopKey);
		MonsterSlayerDefinitions.Reward reward = findReward(shop, itemId);
		MonsterSlayerShopService service = player.getWorld().getMonsterSlayerShopService();
		if (shop == null || reward == null || service == null) return false;
		MonsterSlayerShopService.Result result = service.redeem(player, shopKey, reward.getKey(), quantity);
		if (!result.isSuccessful()) {
			player.message(redemptionFailureMessage(result.getReason()));
			return false;
		}
		refreshDetails(player, session, shop, service);
		player.message("Purchase complete.");
		return true;
	}

	private static ProductionSession session(Player player, MonsterSlayerDefinitions.Shop shop,
		MonsterSlayerShopService service, MonsterSlayerState.Snapshot state) {
		List<MonsterSlayerDefinitions.Reward> rewards = rewards(shop);
		List<ProductionRecipe> recipes = new ArrayList<ProductionRecipe>();
		int[][] costCodes = new int[rewards.size()][];
		int[][] costAmounts = new int[rewards.size()][];
		int[] stock = new int[rewards.size()];
		for (int i = 0; i < rewards.size(); i++) {
			MonsterSlayerDefinitions.Reward reward = rewards.get(i);
			recipes.add(new ProductionRecipe(reward.getItemId(), 1, 1, reward.getAmount(), true, true));
			List<Integer> codes = new ArrayList<Integer>();
			List<Integer> amounts = new ArrayList<Integer>();
			for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) {
				long amount = reward.getCost().get(challenge);
				if (amount > 0L) { codes.add(challenge.getCode()); amounts.add(clamp(amount)); }
			}
			costCodes[i] = ints(codes);
			costAmounts[i] = ints(amounts);
			stock[i] = service.getStock(reward.getKey());
		}
		int[] codes = new int[MonsterSlayerChallenge.values().length];
		int[] balances = new int[codes.length];
		for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) {
			codes[challenge.getCode()] = challenge.getCode();
			balances[challenge.getCode()] = clamp(state.getBalances().get(challenge));
		}
		PointShopDetails details = new PointShopDetails(codes, balances, costCodes, costAmounts, stock);
		return new ProductionSession(ProductionSession.TYPE_MONSTER_SLAYER_REDEMPTION,
			"Monster Slayer " + title(shop.getChallenge()) + " Shop", -1, 0, recipes,
			"monster-slayer-shop:" + shop.getKey(), details);
	}
	private static void refreshDetails(Player player, ProductionSession session, MonsterSlayerDefinitions.Shop shop,
		MonsterSlayerShopService service) {
		if (session.getPointShopDetails() == null) return;
		MonsterSlayerState.Snapshot state = MonsterSlayerState.read(player.getCache(), player.getWorld().getMonsterSlayerData());
		int[] balances = new int[MonsterSlayerChallenge.values().length];
		for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values())
			balances[challenge.getCode()] = clamp(state.getBalances().get(challenge));
		List<MonsterSlayerDefinitions.Reward> rewards = rewards(shop);
		int[] stock = new int[rewards.size()];
		for (int i = 0; i < rewards.size(); i++) stock[i] = service.getStock(rewards.get(i).getKey());
		session.getPointShopDetails().refresh(balances, stock);
	}

	private static List<MonsterSlayerDefinitions.Reward> rewards(MonsterSlayerDefinitions.Shop shop) {
		List<MonsterSlayerDefinitions.Reward> all = new ArrayList<MonsterSlayerDefinitions.Reward>();
		for (MonsterSlayerDefinitions.Category category : shop.getCategories()) all.addAll(category.getRewards());
		return all;
	}
	private static MonsterSlayerDefinitions.Reward findReward(MonsterSlayerDefinitions.Shop shop, int itemId) {
		if (shop == null) return null;
		for (MonsterSlayerDefinitions.Reward reward : rewards(shop)) if (reward.getItemId() == itemId) return reward;
		return null;
	}
	private static int[] ints(List<Integer> values) { int[] result = new int[values.size()]; for (int i = 0; i < result.length; i++) result[i] = values.get(i); return result; }
	private static int clamp(long value) { return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value); }
	private static String title(MonsterSlayerChallenge challenge) { String value = challenge.name().toLowerCase(java.util.Locale.ROOT); return Character.toUpperCase(value.charAt(0)) + value.substring(1); }

	public static String redemptionFailureMessage(String reason) {
		if ("points".equals(reason)) return "You do not have all of the required challenge points for that.";
		if ("stock".equals(reason)) return "That reward is sold out or its stock changed.";
		if ("inventory".equals(reason)) return "You do not have enough inventory space for that.";
		if ("quantity".equals(reason)) return "Choose a valid smaller quantity.";
		if ("grant".equals(reason)) return "The reward could not be delivered. Your points and stock were restored.";
		if ("failure".equals(reason)) return "Your Monster Slayer record needs staff attention.";
		return "That reward is not available to you.";
	}

	/** Readable test/diagnostic summary; graphical clients receive the typed fields directly. */
	public static String costSummary(MonsterSlayerDefinitions.Reward reward, long quantity,
		MonsterSlayerState.Snapshot state) {
		if (reward == null || state == null || quantity < 1L) return "That purchase is not available.";
		StringBuilder text = new StringBuilder("Cost: ");
		for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) {
			long cost = reward.getCost().multiply(quantity).get(challenge);
			if (cost <= 0L) continue;
			if (text.length() > 6) text.append("; ");
			text.append(cost).append(' ').append(title(challenge)).append(" (you: ")
				.append(state.getBalances().get(challenge)).append(')');
		}
		return text.append('.').toString();
	}
}
