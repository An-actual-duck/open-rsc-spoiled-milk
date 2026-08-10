package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import java.util.HashMap;
import java.util.Map;
import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Reward;
import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Shop;

/** Headless typed-currency shop boundary; dialogue/UI callers remain future work. */
public final class MonsterSlayerShopService {
	private final MonsterSlayerData data;
	private final Map<String, Integer> stock = new HashMap<String, Integer>();
	public MonsterSlayerShopService(MonsterSlayerData data) {
		this.data = data;
		for (Shop shop : data.getShops()) for (MonsterSlayerDefinitions.Category category : shop.getCategories())
			for (Reward reward : category.getRewards()) stock.put(reward.getKey(), reward.getStock());
	}
	public synchronized int getStock(String rewardKey) { Integer value = stock.get(rewardKey); return value == null ? -1 : value; }
	public synchronized void restock() { for (Shop shop : data.getShops()) for (MonsterSlayerDefinitions.Category c : shop.getCategories()) for (Reward r : c.getRewards()) stock.put(r.getKey(), Math.min(r.getStock(), stock.get(r.getKey()) + r.getRestockAmount())); }
	public Result redeem(Player player, String shopKey, String rewardKey, long quantity) {
		try {
			Shop shop = data.getShop(shopKey); Reward reward = reward(shop, rewardKey);
			if (shop == null || reward == null || quantity <= 0L || !rankAllows(player, shop)) return Result.rejected("unavailable");
			long output = reward.outputAmountFor(quantity); if (output > Integer.MAX_VALUE) return Result.rejected("quantity");
			synchronized (this) {
				if (getStock(rewardKey) < quantity) return Result.rejected("stock");
				synchronized (player.getCarriedItems().getInventory().getItems()) {
					if (!player.getCarriedItems().getInventory().canHold(reward.getItemId(), (int) output)) return Result.rejected("inventory");
					MonsterSlayerState.Snapshot current = MonsterSlayerState.read(player.getCache(), data);
					MonsterSlayerState.SpendProposal spent = MonsterSlayerState.proposeSpend(current, data, reward.getCost(), quantity);
					if (!spent.isSuccessful()) return Result.rejected("points");
					MonsterSlayerState.write(player.getCache(), data, spent.getSnapshot()); stock.put(rewardKey, getStock(rewardKey) - (int) quantity);
					if (!player.getCarriedItems().getInventory().add(new Item(reward.getItemId(), (int) output), false)) {
						stock.put(rewardKey, getStock(rewardKey) + (int) quantity); MonsterSlayerState.write(player.getCache(), data, spent.getReceipt().refund(spent.getSnapshot(), data)); return Result.rejected("grant");
					}
					return Result.success();
				}
			}
		} catch (RuntimeException failure) { return Result.rejected("failure"); }
	}
	public Result purchaseCapacity(Player player, String shopKey) {
		try {
			Shop shop = data.getShop(shopKey); if (shop == null || !rankAllows(player, shop)) return Result.rejected("unavailable");
			MonsterSlayerState.InventoryUpgrade upgrade = MonsterSlayerState.InventoryUpgrade.valueOf(shopKey.toUpperCase());
			MonsterSlayerState.Snapshot current = MonsterSlayerState.read(player.getCache(), data);
			MonsterSlayerState.SpendProposal proposal = MonsterSlayerState.proposeInventoryUpgrade(current, data, upgrade, shop.getCapacityUpgrade().getCost());
			if (!proposal.isSuccessful()) return Result.rejected("locked-or-points"); MonsterSlayerState.write(player.getCache(), data, proposal.getSnapshot()); return Result.success();
		} catch (RuntimeException failure) { return Result.rejected("failure"); }
	}
	private Reward reward(Shop shop, String key) { if (shop == null) return null; for (MonsterSlayerDefinitions.Category c : shop.getCategories()) for (Reward r : c.getRewards()) if (r.getKey().equals(key)) return r; return null; }
	private boolean rankAllows(Player p, Shop shop) { return p != null && MonsterSlayerState.read(p.getCache(), data).getRank().isAtLeast(MonsterSlayerRank.fromCode(shop.getChallenge().getCode() + 1)); }
	public static final class Result { private final boolean success; private final String reason; private Result(boolean ok,String why){success=ok;reason=why;} static Result success(){return new Result(true,null);} static Result rejected(String why){return new Result(false,why);} public boolean isSuccessful(){return success;} public String getReason(){return reason;} }
}
