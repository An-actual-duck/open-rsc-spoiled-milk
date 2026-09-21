package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Reward;
import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Shop;

/** Authoritative, atomic ingredient-and-typed-currency Slayer shop purchases. */
public final class MonsterSlayerShopService {
	private final MonsterSlayerData data;
	private final ItemGrant itemGrant;
	public MonsterSlayerShopService(MonsterSlayerData data) {
		this(data, new InventoryItemGrant());
	}
	/** Injection point permits deterministic transaction verification without a database item-id allocator. */
	public MonsterSlayerShopService(MonsterSlayerData data, ItemGrant itemGrant) {
		if (data == null) throw new IllegalArgumentException("Monster Slayer data is required");
		if (itemGrant == null) throw new IllegalArgumentException("Monster Slayer item grant is required");
		this.data = data;
		this.itemGrant = itemGrant;
	}
	/** Pure one-time entitlement preflight; caller owns persistence. */
	public CapacityProposal proposeCapacityPurchase(MonsterSlayerState.Snapshot current, String shopKey) {
		try {
			Shop shop = data.getShop(shopKey);
			if (shop == null || current == null) return CapacityProposal.rejected("unavailable");
			MonsterSlayerState.InventoryUpgrade upgrade = MonsterSlayerState.InventoryUpgrade.valueOf(shopKey.toUpperCase());
			MonsterSlayerState.SpendProposal spend = MonsterSlayerState.proposeInventoryUpgrade(current, data,
				upgrade, shop.getCapacityUpgrade().getCost());
			return spend.isSuccessful() ? CapacityProposal.accepted(spend) : CapacityProposal.rejected("locked-or-points");
		} catch (RuntimeException ex) { return CapacityProposal.rejected("failure"); }
	}
	/** Slayer rewards intentionally have infinite stock, like Rangers' Guild rewards. */
	public int getStock(String rewardKey) { return -1; }
	/** Retained as a harmless compatibility hook for the world event. */
	public void restock() { }
	/** Pure preflight used by both UI callers and deterministic economy tests. */
	public synchronized RedemptionProposal proposeRedemption(MonsterSlayerState.Snapshot current,
			String shopKey, String rewardKey, long quantity) {
		Shop shop = data.getShop(shopKey); Reward reward = reward(shop, rewardKey);
		if (quantity <= 0L) return RedemptionProposal.rejected("quantity");
		if (shop == null || reward == null || current == null) return RedemptionProposal.rejected("unavailable");
		try {
			long output = reward.outputAmountFor(quantity);
			if (output > Integer.MAX_VALUE) return RedemptionProposal.rejected("quantity");
			for (MonsterSlayerDefinitions.Ingredient ingredient : reward.getIngredients())
				if (ingredient.amountFor(quantity) > Integer.MAX_VALUE) return RedemptionProposal.rejected("quantity");
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
					return exchange(player, proposal, quantity);
				}
			}
		} catch (RuntimeException failure) { return Result.rejected("failure"); }
	}
	private Result exchange(Player player, RedemptionProposal proposal, long quantity) {
		Inventory inventory = player.getCarriedItems().getInventory();
		List<Item> items = inventory.getItems();
		synchronized (items) {
			// Preserve actual instances (including identity, durability and attributes), not fresh item copies.
			List<Item> original = new ArrayList<Item>(items);
			int[] amounts = new int[original.size()];
			int[] durability = new int[original.size()];
			for (int i = 0; i < original.size(); i++) {
				amounts[i] = original.get(i).getAmount();
				durability[i] = original.get(i).getItemStatus().getDurability();
			}
			boolean complete = false, charged = false;
			try {
				for (MonsterSlayerDefinitions.Ingredient ingredient : proposal.reward.getIngredients()) {
					long remaining = ingredient.amountFor(quantity);
					for (Iterator<Item> it = items.iterator(); it.hasNext() && remaining > 0;) {
						Item item = it.next();
						if (item.getCatalogId() != ingredient.getItemId() || item.getNoted() || item.isWielded()) continue;
						int take = (int)Math.min(remaining, item.getAmount());
						remaining -= take;
						if (take == item.getAmount()) it.remove();
						else item.changeAmount(-take);
					}
					if (remaining > 0) return Result.rejected("ingredients");
				}
				// Check after reserving materials: consuming a stack can free the output slot.
				if (!inventory.canHold(proposal.reward.getItemId(), proposal.output)) return Result.rejected("inventory");
				MonsterSlayerState.write(player.getCache(), data, proposal.spend.getSnapshot());
				charged = true;
				if (!itemGrant.grant(player, proposal.reward.getItemId(), proposal.output)) return Result.rejected("grant");
				complete = true;
				return Result.success();
			} catch (RuntimeException failure) {
				return Result.rejected("grant");
			} finally {
				if (!complete) {
					items.clear();
					for (int i = 0; i < original.size(); i++) {
						original.get(i).setAmount(amounts[i]);
						original.get(i).getItemStatus().setDurability(durability[i]);
					}
					items.addAll(original);
					if (charged) MonsterSlayerState.write(player.getCache(), data,
						proposal.spend.getReceipt().refund(proposal.spend.getSnapshot(), data));
				}
				ActionSender.sendInventory(player);
			}
		}
	}
	public Result purchaseCapacity(Player player, String shopKey) {
		try {
			if (player == null) return Result.rejected("unavailable");
			synchronized (player) {
			Shop shop = data.getShop(shopKey); if (shop == null) return Result.rejected("unavailable");
			MonsterSlayerState.Snapshot current = MonsterSlayerState.read(player.getCache(), data);
			CapacityProposal proposal = proposeCapacityPurchase(current, shopKey);
			if (!proposal.isSuccessful()) return Result.rejected(proposal.getReason());
			MonsterSlayerState.write(player.getCache(), data, proposal.spend.getSnapshot());
			return Result.success();
			}
		} catch (RuntimeException failure) { return Result.rejected("failure"); }
	}
	private Reward reward(Shop shop, String key) { if (shop == null) return null; for (MonsterSlayerDefinitions.Category c : shop.getCategories()) for (Reward r : c.getRewards()) if (r.getKey().equals(key)) return r; return null; }
	public static final class RedemptionProposal { private final Reward reward; private final int output; private final MonsterSlayerState.SpendProposal spend; private final String reason; private RedemptionProposal(Reward r,int o,MonsterSlayerState.SpendProposal s,String why){reward=r;output=o;spend=s;reason=why;} static RedemptionProposal accepted(Reward r,int o,MonsterSlayerState.SpendProposal s){return new RedemptionProposal(r,o,s,null);} static RedemptionProposal rejected(String why){return new RedemptionProposal(null,0,null,why);} public boolean isSuccessful(){return reason==null;} public String getReason(){return reason;} public int getOutput(){return output;} }
	public static final class CapacityProposal { private final MonsterSlayerState.SpendProposal spend; private final String reason; private CapacityProposal(MonsterSlayerState.SpendProposal s,String why){spend=s;reason=why;} static CapacityProposal accepted(MonsterSlayerState.SpendProposal s){return new CapacityProposal(s,null);} static CapacityProposal rejected(String why){return new CapacityProposal(null,why);} public boolean isSuccessful(){return reason==null;} public String getReason(){return reason;} public MonsterSlayerState.Snapshot getSnapshot(){return spend == null ? null : spend.getSnapshot();} }
	public interface ItemGrant { boolean grant(Player player, int itemId, int amount); }
	private static final class InventoryItemGrant implements ItemGrant {
		public boolean grant(Player player, int itemId, int amount) {
			return player.getCarriedItems().getInventory().add(new Item(itemId, amount), true);
		}
	}
	public static final class Result { private final boolean success; private final String reason; private Result(boolean ok,String why){success=ok;reason=why;} static Result success(){return new Result(true,null);} static Result rejected(String why){return new Result(false,why);} public boolean isSuccessful(){return success;} public String getReason(){return reason;} }
}
