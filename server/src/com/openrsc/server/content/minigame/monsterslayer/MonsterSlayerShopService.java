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
	private final ItemGrant itemGrant;
	private final Map<String, Integer> stock = new HashMap<String, Integer>();
	public MonsterSlayerShopService(MonsterSlayerData data) {
		this(data, new InventoryItemGrant());
	}
	/** Injection point permits deterministic transaction verification without a database item-id allocator. */
	public MonsterSlayerShopService(MonsterSlayerData data, ItemGrant itemGrant) {
		if (data == null) throw new IllegalArgumentException("Monster Slayer data is required");
		if (itemGrant == null) throw new IllegalArgumentException("Monster Slayer item grant is required");
		this.data = data;
		this.itemGrant = itemGrant;
		for (Shop shop : data.getShops()) for (MonsterSlayerDefinitions.Category category : shop.getCategories())
			for (Reward reward : category.getRewards()) stock.put(reward.getKey(), reward.getStock());
	}
	/** Pure one-time entitlement preflight; caller owns persistence. */
	public CapacityProposal proposeCapacityPurchase(MonsterSlayerState.Snapshot current, String shopKey) {
		try {
			Shop shop = data.getShop(shopKey);
			if (shop == null || current == null || !current.getRank().isAtLeast(
				MonsterSlayerRank.fromCode(shop.getChallenge().getCode() + 1))) return CapacityProposal.rejected("unavailable");
			MonsterSlayerState.InventoryUpgrade upgrade = MonsterSlayerState.InventoryUpgrade.valueOf(shopKey.toUpperCase());
			MonsterSlayerState.SpendProposal spend = MonsterSlayerState.proposeInventoryUpgrade(current, data,
				upgrade, shop.getCapacityUpgrade().getCost());
			return spend.isSuccessful() ? CapacityProposal.accepted(spend) : CapacityProposal.rejected("locked-or-points");
		} catch (RuntimeException ex) { return CapacityProposal.rejected("failure"); }
	}
	public synchronized int getStock(String rewardKey) { Integer value = stock.get(rewardKey); return value == null ? -1 : value; }
	public synchronized void restock() { for (Shop shop : data.getShops()) for (MonsterSlayerDefinitions.Category c : shop.getCategories()) for (Reward r : c.getRewards()) stock.put(r.getKey(), Math.min(r.getStock(), stock.get(r.getKey()) + r.getRestockAmount())); }
	/** Pure preflight used by both UI callers and deterministic economy tests. */
	public synchronized RedemptionProposal proposeRedemption(MonsterSlayerState.Snapshot current,
			String shopKey, String rewardKey, long quantity) {
		Shop shop = data.getShop(shopKey); Reward reward = reward(shop, rewardKey);
		if (shop == null || reward == null || quantity <= 0L || current == null
			|| !current.getRank().isAtLeast(MonsterSlayerRank.fromCode(shop.getChallenge().getCode() + 1))) return RedemptionProposal.rejected("unavailable");
		if (getStock(rewardKey) < quantity) return RedemptionProposal.rejected("stock");
		try {
			long output = reward.outputAmountFor(quantity);
			if (output > Integer.MAX_VALUE) return RedemptionProposal.rejected("quantity");
			MonsterSlayerState.SpendProposal spend = MonsterSlayerState.proposeSpend(current, data, reward.getCost(), quantity);
			return spend.isSuccessful() ? RedemptionProposal.accepted(reward, (int) output, spend) : RedemptionProposal.rejected("points");
		} catch (RuntimeException ex) { return RedemptionProposal.rejected("quantity"); }
	}
	public Result redeem(Player player, String shopKey, String rewardKey, long quantity) {
		try {
			if (player == null) return Result.rejected("unavailable");
			synchronized (player) {
				synchronized (this) {
					MonsterSlayerState.Snapshot current = MonsterSlayerState.read(player.getCache(), data);
					RedemptionProposal proposal = proposeRedemption(current, shopKey, rewardKey, quantity);
					if (!proposal.isSuccessful()) return Result.rejected(proposal.getReason());
					if (!player.getCarriedItems().getInventory().canHold(proposal.reward.getItemId(), proposal.output)) return Result.rejected("inventory");
					MonsterSlayerState.SpendProposal spent = proposal.spend;
					MonsterSlayerState.write(player.getCache(), data, spent.getSnapshot()); stock.put(rewardKey, getStock(rewardKey) - (int) quantity);
					try {
						if (!itemGrant.grant(player, proposal.reward.getItemId(), proposal.output)) return rollback(player, rewardKey, quantity, spent);
					} catch (RuntimeException failure) { return rollback(player, rewardKey, quantity, spent); }
					return Result.success();
				}
			}
		} catch (RuntimeException failure) { return Result.rejected("failure"); }
	}
	private Result rollback(Player player, String rewardKey, long quantity, MonsterSlayerState.SpendProposal spent) {
		stock.put(rewardKey, getStock(rewardKey) + (int) quantity);
		MonsterSlayerState.write(player.getCache(), data, spent.getReceipt().refund(spent.getSnapshot(), data));
		return Result.rejected("grant");
	}
	public Result purchaseCapacity(Player player, String shopKey) {
		try {
			if (player == null) return Result.rejected("unavailable");
			synchronized (player) {
			Shop shop = data.getShop(shopKey); if (shop == null || !rankAllows(player, shop)) return Result.rejected("unavailable");
			MonsterSlayerState.Snapshot current = MonsterSlayerState.read(player.getCache(), data);
			CapacityProposal proposal = proposeCapacityPurchase(current, shopKey);
			if (!proposal.isSuccessful()) return Result.rejected(proposal.getReason()); MonsterSlayerState.write(player.getCache(), data, proposal.spend.getSnapshot()); return Result.success();
			}
		} catch (RuntimeException failure) { return Result.rejected("failure"); }
	}
	private Reward reward(Shop shop, String key) { if (shop == null) return null; for (MonsterSlayerDefinitions.Category c : shop.getCategories()) for (Reward r : c.getRewards()) if (r.getKey().equals(key)) return r; return null; }
	private boolean rankAllows(Player p, Shop shop) { return p != null && MonsterSlayerState.read(p.getCache(), data).getRank().isAtLeast(MonsterSlayerRank.fromCode(shop.getChallenge().getCode() + 1)); }
	public static final class RedemptionProposal { private final Reward reward; private final int output; private final MonsterSlayerState.SpendProposal spend; private final String reason; private RedemptionProposal(Reward r,int o,MonsterSlayerState.SpendProposal s,String why){reward=r;output=o;spend=s;reason=why;} static RedemptionProposal accepted(Reward r,int o,MonsterSlayerState.SpendProposal s){return new RedemptionProposal(r,o,s,null);} static RedemptionProposal rejected(String why){return new RedemptionProposal(null,0,null,why);} public boolean isSuccessful(){return reason==null;} public String getReason(){return reason;} public int getOutput(){return output;} }
	public static final class CapacityProposal { private final MonsterSlayerState.SpendProposal spend; private final String reason; private CapacityProposal(MonsterSlayerState.SpendProposal s,String why){spend=s;reason=why;} static CapacityProposal accepted(MonsterSlayerState.SpendProposal s){return new CapacityProposal(s,null);} static CapacityProposal rejected(String why){return new CapacityProposal(null,why);} public boolean isSuccessful(){return reason==null;} public String getReason(){return reason;} public MonsterSlayerState.Snapshot getSnapshot(){return spend == null ? null : spend.getSnapshot();} }
	public interface ItemGrant { boolean grant(Player player, int itemId, int amount); }
	private static final class InventoryItemGrant implements ItemGrant { public boolean grant(Player player, int itemId, int amount) { return player.getCarriedItems().getInventory().add(new Item(itemId, amount), false); } }
	public static final class Result { private final boolean success; private final String reason; private Result(boolean ok,String why){success=ok;reason=why;} static Result success(){return new Result(true,null);} static Result rejected(String why){return new Result(false,why);} public boolean isSuccessful(){return success;} public String getReason(){return reason;} }
}
