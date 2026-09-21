package com.openrsc.server.combat;

import com.openrsc.server.content.minigame.monsterslayer.*;
import com.openrsc.server.content.monsterslayer.*;
import com.openrsc.server.content.production.*;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Real shop purchases, UI price vectors and schema isolation for five shared protections. */
public final class SlayerGimmickShopFixture {
	private static final int[] ITEMS = {3318, 3321, 3324, 3327, 3330};
	private static final long[] PRICES = {5, 8, 12, 18, 28};
	private static final String[] KEYS = {"slime_solvent", "eye_drops", "wax_earplugs", "dog_treats", "static_discharge_wipes"};
	private static final String[] TASKS = {"falador.giant_frog", "port_sarim.cockatrice", "brimhaven.banshee", "champions.terror_dog", "heroes.dark_beast"};
	private static final Path PATH = Paths.get("conf/server/defs/extras/MonsterSlayer.json");
	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness h = new CurrentCombatHarness()) {
			MonsterSlayerData.ReferenceCatalog catalog = new MonsterSlayerData.ReferenceCatalog() {
				public boolean npcExists(int id) { return true; }
				public boolean npcAttackable(int id) { return true; }
				public boolean npcSpawned(int id) { return true; }
				public boolean itemExists(int id) { return h.server().getEntityHandler().getItemDef(id) != null; }
			};
			MonsterSlayerData data = MonsterSlayerData.load(PATH, catalog);
			MonsterSlayerData expanded = MonsterSlayerData.load(PATH, catalog, true);
			h.installMonsterSlayerData(data);
			MonsterSlayerShopService service = new MonsterSlayerShopService(data);
			Method session = Class.forName("com.openrsc.server.plugins.custom.myworld.npcs.MonsterSlayerChallengeShops")
				.getDeclaredMethod("session", Player.class, MonsterSlayerDefinitions.Shop.class,
					MonsterSlayerShopService.class, MonsterSlayerState.Snapshot.class);
			session.setAccessible(true);
			for (MonsterSlayerDefinitions.Shop shop : data.getShops()) {
				for (int i = 0; i < ITEMS.length; i++) {
					MonsterSlayerChallenge tier = MonsterSlayerChallenge.fromCode(i);
					MonsterSlayerDefinitions.Reward reward = reward(shop, ITEMS[i]);
					check(reward.getAmount() == 1 && reward.getIngredients().isEmpty(), "one item, no materials");
					check(reward.getCost().asMap().size() == 1 && reward.getCost().get(tier) == PRICES[i], "single source currency");
					check(reward(expanded.getShop(shop.getKey()), ITEMS[i]).getCost().equals(reward.getCost()), "rollout-independent supply access");
					check(expanded.getTask(TASKS[i]).getPointReward() == PRICES[i]
						&& expanded.getTask(TASKS[i] + ".repeatable").getPointReward() == PRICES[i], "one matching task payout");
					check(!h.server().getEntityHandler().getItemDef(ITEMS[i]).isStackable(), "nonstackable full-use item");
					check(uses(i, ITEMS[i]) == 3 && uses(i, ITEMS[i] + 1) == 2 && uses(i, ITEMS[i] + 2) == 1, "three-use variants");
					String key = shop.getKey() + "." + KEYS[i];
					check(reward.getKey().equals(key) && service.getStock(key) == -1, "stable key and unlimited supply");
					Player p = buyer(h, data, tier, PRICES[i] * 5);
					ProductionSession ui = (ProductionSession)session.invoke(null, p, shop, service, MonsterSlayerState.read(p.getCache(), data));
					int row = -1;
					for (int j = 0; j < ui.getRecipes().size(); j++) if (ui.getRecipes().get(j).getItemId() == ITEMS[i]) row = j;
					check(row >= 0 && ui.getRecipes().get(row).getIngredientItemIds().length == 0, "visible protection recipe");
					check(Arrays.equals(ui.getPointShopDetails().getRecipeCostCodes()[row], new int[]{i})
						&& Arrays.equals(ui.getPointShopDetails().getRecipeCostAmounts()[row], new int[]{(int)PRICES[i]}), "UI uses source tier, not shop tier");
					check(service.redeem(p, shop.getKey(), key, 3).isSuccessful(), "buy several full-use items");
					check(p.getCarriedItems().getInventory().countId(ITEMS[i]) == 3 && balance(p, data, tier) == PRICES[i] * 2, "bulk amount and spend");
					check(service.redeem(p, shop.getKey(), key, 2).isSuccessful(), "repeat purchase allowed");
					check(p.getCarriedItems().getInventory().countId(ITEMS[i]) == 5 && balance(p, data, tier) == 0, "five separate full-use items");
					for (MonsterSlayerChallenge other : MonsterSlayerChallenge.values())
						if (other != tier) check(balance(p, data, other) == 1000, "other currencies untouched");
					check("points".equals(service.redeem(p, shop.getKey(), key, 1).getReason()), "other points cannot substitute");
					check(MonsterSlayerState.read(p.getCache(), data).getRank() == MonsterSlayerRank.FLEDGLING, "no purchase rank gate");
					Player full = buyer(h, data, tier, PRICES[i]);
					while (full.getCarriedItems().getInventory().size() < full.getCarriedItems().getInventory().getCapacity())
						full.getCarriedItems().getInventory().add(new Item(259));
					check("inventory".equals(service.redeem(full, shop.getKey(), key, 1).getReason())
						&& balance(full, data, tier) == PRICES[i], "full inventory does not consume points");
					Player poor = buyer(h, data, tier, PRICES[i] - 1);
					check("points".equals(service.redeem(poor, shop.getKey(), key, 1).getReason())
						&& poor.getCarriedItems().getInventory().countId(ITEMS[i]) == 0, "one point short rejected");
				}
			}
			validation(catalog);
			CurrentMonsterSlayerShopRuntimeCharacterization.runtimeTransactionsAreAtomic(h);
			System.out.println("PASS Slayer protections: all five supplies in all six shops, task-equivalent single-tier prices, full three-use items, UI cost vectors, bulk purchases, no currency substitution, inventory rejection and schema isolation");
		}
	}

	private static void validation(MonsterSlayerData.ReferenceCatalog catalog) throws Exception {
		String json = new String(Files.readAllBytes(PATH), StandardCharsets.UTF_8);
		Method parse = MonsterSlayerData.class.getDeclaredMethod("parse", JSONObject.class, MonsterSlayerData.ReferenceCatalog.class);
		parse.setAccessible(true);
		for (int variant = 0; variant < 7; variant++) {
			JSONObject bad = new JSONObject(json);
			JSONArray categories = bad.getJSONArray("shops").getJSONObject(0).getJSONArray("categories");
			JSONObject wipe = categories.getJSONObject(categories.length() - 1).getJSONArray("rewards").getJSONObject(4);
			if (variant == 0) wipe.getJSONObject("cost").put("FLEDGLING", 1);
			if (variant == 1) wipe.put("cost", new JSONObject().put("HERO", 28));
			if (variant == 2) wipe.put("amount", 3);
			if (variant == 3) wipe.put("ingredients", new JSONArray().put(new JSONObject().put("itemId", 3333).put("amount", 1)));
			if (variant == 4) wipe.put("itemId", 3331); // Partial-use item gets no cross-tier exception.
			if (variant == 5) wipe.put("itemId", 259); // Nor does an arbitrary item.
			if (variant == 6) categories.getJSONObject(0).getJSONArray("rewards").getJSONObject(0)
				.put("cost", new JSONObject().put("CHAMPION", 28));
			try { parse.invoke(null, bad, catalog); throw new AssertionError("Invalid protection cost accepted " + variant); }
			catch (InvocationTargetException rejected) {
				check(rejected.getCause() instanceof IllegalArgumentException, "validation error type");
			}
		}
	}

	private static MonsterSlayerDefinitions.Reward reward(MonsterSlayerDefinitions.Shop shop, int id) {
		MonsterSlayerDefinitions.Reward found = null;
		for (MonsterSlayerDefinitions.Category category : shop.getCategories()) {
			for (MonsterSlayerDefinitions.Reward reward : category.getRewards()) if (reward.getItemId() == id) {
				check(found == null, "no duplicate supply listing");
				found = reward;
			}
		}
		check(found != null, "supply in every shop");
		return found;
	}

	private static int serial;
	private static Player buyer(CurrentCombatHarness h, MonsterSlayerData data, MonsterSlayerChallenge tier, long amount) {
		Player player = h.player("supply" + serial++, 450, 450);
		player.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		Map<MonsterSlayerChallenge, Long> balances = new EnumMap<>(MonsterSlayerChallenge.class);
		for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) balances.put(challenge, challenge == tier ? amount : 1000);
		Map<String, Integer> cursors = new LinkedHashMap<>();
		for (MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) cursors.put(contact.getKey(), 0);
		MonsterSlayerState.write(player.getCache(), data, MonsterSlayerState.create(2, MonsterSlayerRank.FLEDGLING,
			MonsterSlayerBalances.of(balances), cursors, null, 0, 0L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data));
		return player;
	}
	private static long balance(Player p, MonsterSlayerData data, MonsterSlayerChallenge tier) {
		return MonsterSlayerState.read(p.getCache(), data).getBalances().get(tier);
	}
	private static int uses(int index, int itemId) {
		switch (index) {
			case 0: return GiantFrogCombat.solventDoses(itemId);
			case 1: return CockatriceCombat.doses(itemId);
			case 2: return BansheeCombat.uses(itemId);
			case 3: return TerrorDogCombat.uses(itemId);
			default: return DarkBeastCombat.uses(itemId);
		}
	}
	private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
