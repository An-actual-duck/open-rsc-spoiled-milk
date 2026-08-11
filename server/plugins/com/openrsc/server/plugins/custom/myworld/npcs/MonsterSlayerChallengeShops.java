package com.openrsc.server.plugins.custom.myworld.npcs;

import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerData;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerShopService;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerState;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;

/** Dialogue presentation for the typed Monster Slayer reward economy. */
public final class MonsterSlayerChallengeShops {
	private MonsterSlayerChallengeShops() { }

	public static void open(Player player, Npc npc, String shopKey) {
		try {
			MonsterSlayerData data = player.getWorld().getMonsterSlayerData();
			MonsterSlayerDefinitions.Shop shop = data.getShop(shopKey);
			MonsterSlayerShopService service = player.getWorld().getMonsterSlayerShopService();
			if (shop == null || service == null) { player.message("This challenge shop is not available right now."); return; }
		npcsay(player, npc, "Welcome to the " + challengeName(shop.getChallenge()) + " Challenge Shop.",
			"Inventory expansion is not available yet.");
			browse(player, npc, shop, service);
		} catch (RuntimeException failure) { player.message("Your Monster Slayer record needs staff attention."); }
	}

	/** A bounded, iterative dialogue state machine.  The old recursive menu could
	 * grow one Java frame for every Back, cancel, or successful purchase. */
	private static void browse(Player player, Npc npc, MonsterSlayerDefinitions.Shop shop, MonsterSlayerShopService service) {
		List<MonsterSlayerDefinitions.Category> categories = shop.getCategories();
		if (categories.isEmpty()) { npcsay(player, npc, "There is nothing in this shop yet."); return; }
		int screen = 0;
		MonsterSlayerDefinitions.Category category = null;
		MonsterSlayerDefinitions.Reward reward = null;
		for (int steps = 0; steps < 128; steps++) {
			if (screen == 0) {
				if (categories.size() == 1) { category = categories.get(0); screen = 1; continue; }
				String[] options = new String[categories.size() + 1];
				for (int index = 0; index < categories.size(); index++) options[index] = categories.get(index).getLabel();
				options[options.length - 1] = "Never mind.";
				int choice = multi(player, options);
				if (choice < 0 || choice >= categories.size()) return;
				category = categories.get(choice); screen = 1; continue;
			}
			if (screen == 1) {
				List<MonsterSlayerDefinitions.Reward> rewards = category.getRewards();
				String[] options = new String[rewards.size() + 2];
				for (int index = 0; index < rewards.size(); index++) options[index] = itemName(player, rewards.get(index)) + " (" + service.getStock(rewards.get(index).getKey()) + " in stock)";
				options[rewards.size()] = "Back"; options[rewards.size() + 1] = "Cancel";
				int choice = multi(player, options);
				if (choice < 0 || choice == rewards.size() + 1) return;
				if (choice == rewards.size()) { screen = 0; continue; }
				reward = rewards.get(choice); screen = 2; continue;
			}
			MonsterSlayerState.Snapshot state = MonsterSlayerState.read(player.getCache(), player.getWorld().getMonsterSlayerData());
			npcsay(player, npc, itemName(player, reward) + ": " + reward.getAmount() + " per purchase; " + service.getStock(reward.getKey()) + " in stock.", costSummary(reward, 1L, state));
			int choice = multi(player, "Buy 1", "Buy 5", "Buy 10", "Back", "Cancel");
			if (choice < 0 || choice == 4) return;
			if (choice == 3) { screen = 1; continue; }
			long quantity = choice == 0 ? 1L : choice == 1 ? 5L : 10L;
			if (quantity > 1L) {
				npcsay(player, npc, "Buy " + quantity + " purchases?", costSummary(reward, quantity, state));
				if (multi(player, "Confirm purchase", "Cancel") != 0) { screen = 1; continue; }
			}
			MonsterSlayerShopService.Result result = service.redeem(player, shop.getKey(), reward.getKey(), quantity);
			if (!result.isSuccessful()) player.message(redemptionFailureMessage(result.getReason()));
			else {
				MonsterSlayerState.Snapshot refreshed = MonsterSlayerState.read(player.getCache(), player.getWorld().getMonsterSlayerData());
				npcsay(player, npc, "Purchase complete. " + service.getStock(reward.getKey()) + " remain in stock.", costSummary(reward, quantity, refreshed));
			}
			screen = 1;
		}
		player.message("The challenge shop menu has been safely closed.");
	}

	public static String costSummary(MonsterSlayerDefinitions.Reward reward, long quantity, MonsterSlayerState.Snapshot state) {
		if (reward == null || state == null || quantity <= 0L) return "That purchase is not available.";
		try {
			List<String> components = new ArrayList<String>();
			for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) {
				long cost = reward.getCost().multiply(quantity).get(challenge);
				if (cost > 0L) components.add(cost + " " + label(challenge) + " (you: " + state.getBalances().get(challenge) + ")");
			}
			return "Cost: " + join(components, "; ") + ".";
		} catch (RuntimeException failure) { return "That quantity is not available."; }
	}

	public static String redemptionFailureMessage(String reason) {
		if ("points".equals(reason)) return "You do not have all of the required challenge points for that.";
		if ("stock".equals(reason)) return "That reward is sold out or its stock changed.";
		if ("inventory".equals(reason)) return "You do not have enough inventory space for that.";
		if ("quantity".equals(reason)) return "Choose a valid smaller quantity.";
		if ("grant".equals(reason)) return "The reward could not be delivered. Your points and stock were restored.";
		if ("failure".equals(reason)) return "Your Monster Slayer record needs staff attention.";
		return "That reward is not available to you.";
	}

	private static String itemName(Player player, MonsterSlayerDefinitions.Reward reward) { return player.getWorld().getServer().getEntityHandler().getItemDef(reward.getItemId()).getName(); }
	private static String label(MonsterSlayerChallenge challenge) { return challengeName(challenge) + " points"; }
	private static String challengeName(MonsterSlayerChallenge challenge) { String value = challenge.name().toLowerCase(Locale.ROOT); return Character.toUpperCase(value.charAt(0)) + value.substring(1); }
	private static String join(List<String> values, String separator) { StringBuilder joined = new StringBuilder(); for (String value : values) { if (joined.length() > 0) joined.append(separator); joined.append(value); } return joined.toString(); }
}
