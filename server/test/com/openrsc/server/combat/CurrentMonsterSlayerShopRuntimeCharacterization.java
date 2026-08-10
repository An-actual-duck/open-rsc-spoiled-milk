package com.openrsc.server.combat;

import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerContactService;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerCost;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerData;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerRank;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerShopService;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerState;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerBalances;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Executes production shop transactions with deterministic item grants and runtime contention. */
final class CurrentMonsterSlayerShopRuntimeCharacterization {
	static void runtimeTransactionsAreAtomic(CurrentCombatHarness h) throws Exception {
		MonsterSlayerData data = data();
		basicRedemptionAndRollback(h, data);
		everyShopDeductsItsTypedCost(h, data);
		capacityEntitlementsAreOrderedAndDoNotChangeActiveInventory(h, data);
		concurrentRedemptionAndEntitlementPurchasesAreAtomic(h, data);
		fullAndMalformedPlayersRemainUntouched(h, data);
	}

	static void contactRoutesAreRankedAndSingleAssignment(CurrentCombatHarness h) throws Exception {
		MonsterSlayerData data = data();
		MonsterSlayerContactService contacts = new MonsterSlayerContactService(data, new com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerTaskService(data));
		Player recruit = h.player("mssbeer", 850, 790);
		MonsterSlayerState.write(recruit.getCache(), data, MonsterSlayerState.defaults(data));
		assertTrue(contacts.beginBeerIntroduction(recruit).isAccepted(), "beer introduction begins");
		assertTrue(contacts.completeBeerIntroduction(recruit).isAccepted(), "beer introduction completes");
		assertFalse(contacts.completeBeerIntroduction(recruit).isAccepted(), "beer introduction cannot complete twice");
		for (int tier = 0; tier < data.getContacts().size(); tier++) {
			MonsterSlayerDefinitions.Contact contact = data.getContacts().get(tier);
			Player player = h.player("msscontact" + tier, 860 + tier, 790);
			state(player, data, 0L, prefixMask(tier), tier);
			assertTrue(contacts.requestTask(player, contact.getKey()).isAccepted(), "contact assignment " + contact.getKey());
			assertFalse(contacts.requestTask(player, contact.getKey()).isAccepted(), "contact duplicate assignment " + contact.getKey());
		}
	}

	private static void basicRedemptionAndRollback(CurrentCombatHarness h, MonsterSlayerData data) throws Exception {
		AtomicInteger grants = new AtomicInteger();
		MonsterSlayerShopService shops = new MonsterSlayerShopService(data, countingGrant(grants));
		Player player = h.player("msshoptx", 780, 790); state(player, data, 40L, 0, 0);
		assertTrue(shops.redeem(player, "falador", "falador.brawn", 2).isSuccessful(), "real redeem");
		assertEquals(1, grants.get(), "one output grant");
		assertEquals(8, shops.getStock("falador.brawn"), "stock decremented");
		assertEquals(36L, balances(player, data).get(MonsterSlayerChallenge.FLEDGLING), "exact point deduction");
		shops.restock();
		assertEquals(9, shops.getStock("falador.brawn"), "bounded one-step restock");
		assertFalse(shops.redeem(player, "falador", "falador.brawn", Long.MAX_VALUE).isSuccessful(), "quantity overflow");

		Player failed = h.player("msshopfail", 790, 790); state(failed, data, 40L, 0, 0);
		failed.getCache().store("unrelated", "keep");
		Map<String, Object> before = new LinkedHashMap<String, Object>(failed.getCache().getCacheMap());
		MonsterSlayerShopService rejecting = new MonsterSlayerShopService(data, rejectingGrant());
		assertFalse(rejecting.redeem(failed, "falador", "falador.brawn", 1).isSuccessful(), "false grant");
		assertEquals(before, failed.getCache().getCacheMap(), "false grant rollback preserves cache");
		assertEquals(10, rejecting.getStock("falador.brawn"), "false grant rollback preserves stock");
		MonsterSlayerShopService throwing = new MonsterSlayerShopService(data, throwingGrant());
		assertFalse(throwing.redeem(failed, "falador", "falador.brawn", 1).isSuccessful(), "throwing grant");
		assertEquals(before, failed.getCache().getCacheMap(), "throwing grant rollback preserves cache");
		assertEquals(10, throwing.getStock("falador.brawn"), "throwing grant rollback preserves stock");
	}

	private static void everyShopDeductsItsTypedCost(CurrentCombatHarness h, MonsterSlayerData data) throws Exception {
		AtomicInteger grants = new AtomicInteger();
		MonsterSlayerShopService shops = new MonsterSlayerShopService(data, countingGrant(grants));
		for (int tier = 0; tier < data.getShops().size(); tier++) {
			MonsterSlayerDefinitions.Shop shop = data.getShops().get(tier);
			MonsterSlayerDefinitions.Reward reward = shop.getCategories().get(0).getRewards().get(0);
			Player player = h.player("mssshop" + tier, 800 + tier, 790);
			state(player, data, 1000L, prefixMask(tier), tier);
			Map<MonsterSlayerChallenge, Long> before = balances(player, data).asMap();
			assertTrue(shops.redeem(player, shop.getKey(), reward.getKey(), 1).isSuccessful(), "runtime redemption tier " + tier);
			assertTypedDeduction(before, balances(player, data).asMap(), reward.getCost(), "shop tier " + tier);
			assertEquals(reward.getStock() - 1, shops.getStock(reward.getKey()), "shop tier stock " + tier);
		}
		assertEquals(6, grants.get(), "one output per shop redemption");
	}

	private static void capacityEntitlementsAreOrderedAndDoNotChangeActiveInventory(CurrentCombatHarness h, MonsterSlayerData data) throws Exception {
		MonsterSlayerShopService shops = new MonsterSlayerShopService(data, rejectingGrant());
		Player outOfOrder = h.player("mssorder", 820, 790); state(outOfOrder, data, 1000L, 0, 5);
		Map<String, Object> outOfOrderBefore = new LinkedHashMap<String, Object>(outOfOrder.getCache().getCacheMap());
		assertFalse(shops.purchaseCapacity(outOfOrder, data.getShops().get(1).getKey()).isSuccessful(), "out-of-order capacity purchase");
		assertEquals(outOfOrderBefore, outOfOrder.getCache().getCacheMap(), "out-of-order capacity leaves state unchanged");

		Player player = h.player("msscapacity", 821, 790); state(player, data, 1000L, 0, 5);
		for (int tier = 0; tier < data.getShops().size(); tier++) {
			MonsterSlayerDefinitions.Shop shop = data.getShops().get(tier);
			MonsterSlayerState.Snapshot before = MonsterSlayerState.read(player.getCache(), data);
			assertTrue(shops.purchaseCapacity(player, shop.getKey()).isSuccessful(), "ordered capacity purchase " + tier);
			MonsterSlayerState.Snapshot after = MonsterSlayerState.read(player.getCache(), data);
			assertTypedDeduction(before.getBalances().asMap(), after.getBalances().asMap(), shop.getCapacityUpgrade().getCost(), "capacity tier " + tier);
			assertEquals((1 << (tier + 1)) - 1, after.getInventoryUpgrades(), "capacity mask tier " + tier);
			assertEquals(Inventory.MAX_SIZE, player.getCarriedItems().getInventory().getFreeSlots(), "active inventory remains thirty slots " + tier);
			assertEquals(30 + capacityBonusThrough(tier), after.getDerivedInventoryCapacity(), "future capacity entitlement " + tier);
		}
		assertEquals(63, MonsterSlayerState.read(player.getCache(), data).getInventoryUpgrades(), "reloaded ordered capacity mask");
		assertFalse(shops.purchaseCapacity(player, data.getShops().get(5).getKey()).isSuccessful(), "duplicate capacity purchase");
	}

	private static void concurrentRedemptionAndEntitlementPurchasesAreAtomic(CurrentCombatHarness h, MonsterSlayerData data) throws Exception {
		AtomicInteger grants = new AtomicInteger();
		final MonsterSlayerShopService shops = new MonsterSlayerShopService(data, countingGrant(grants));
		final Player buyer = h.player("mssconcurrent", 830, 790); state(buyer, data, 2L, 0, 0);
		MonsterSlayerShopService.Result[] redemptions = concurrently(new Callable<MonsterSlayerShopService.Result>() { public MonsterSlayerShopService.Result call() { return shops.redeem(buyer, "falador", "falador.brawn", 1); }}, new Callable<MonsterSlayerShopService.Result>() { public MonsterSlayerShopService.Result call() { return shops.redeem(buyer, "falador", "falador.brawn", 1); }});
		assertEquals(1, successful(redemptions[0], redemptions[1]), "one concurrent redemption succeeds");
		assertEquals(1, grants.get(), "no duplicate concurrent output");
		assertEquals(0L, balances(buyer, data).get(MonsterSlayerChallenge.FLEDGLING), "no concurrent double spend");
		assertEquals(9, shops.getStock("falador.brawn"), "no negative concurrent stock");

		final Player capacityBuyer = h.player("mssconcurrentcapacity", 831, 790); state(capacityBuyer, data, 1000L, 0, 5);
		MonsterSlayerShopService.Result[] purchases = concurrently(new Callable<MonsterSlayerShopService.Result>() { public MonsterSlayerShopService.Result call() { return shops.purchaseCapacity(capacityBuyer, "falador"); }}, new Callable<MonsterSlayerShopService.Result>() { public MonsterSlayerShopService.Result call() { return shops.purchaseCapacity(capacityBuyer, "falador"); }});
		assertEquals(1, successful(purchases[0], purchases[1]), "one concurrent entitlement succeeds");
		assertEquals(1, MonsterSlayerState.read(capacityBuyer.getCache(), data).getInventoryUpgrades(), "no duplicate concurrent entitlement");
	}

	private static void fullAndMalformedPlayersRemainUntouched(CurrentCombatHarness h, MonsterSlayerData data) throws Exception {
		AtomicInteger fullGrants = new AtomicInteger();
		MonsterSlayerShopService shops = new MonsterSlayerShopService(data, countingGrant(fullGrants));
		Player full = h.player("mssfull", 840, 790); state(full, data, 40L, 0, 0);
		String fullInventoryReward = data.getShop("falador").getCategories().get(0).getRewards().get(0).getKey();
		for (int i = 0; i < Inventory.MAX_SIZE; i++) full.getCarriedItems().getInventory().getItems().add(new Item(259, 1));
		Map<String, Object> fullBefore = new LinkedHashMap<String, Object>(full.getCache().getCacheMap());
		assertFalse(shops.redeem(full, "falador", fullInventoryReward, 1).isSuccessful(), "full inventory redemption rejected");
		assertEquals(fullBefore, full.getCache().getCacheMap(), "full inventory leaves points untouched");
		assertEquals(10, shops.getStock(fullInventoryReward), "full inventory leaves stock untouched");
		assertEquals(Inventory.MAX_SIZE, full.getCarriedItems().getInventory().size(), "full inventory leaves items untouched");
		assertEquals(0, fullGrants.get(), "full inventory never invokes item grant");

		Player malformed = h.player("mssmalformed", 841, 790); state(malformed, data, 40L, 0, 0);
		malformed.getCache().store("monster_slayer_balance_fledgling", "corrupt-evidence");
		Map<String, Object> malformedBefore = new LinkedHashMap<String, Object>(malformed.getCache().getCacheMap());
		assertFalse(shops.redeem(malformed, "falador", "falador.brawn", 1).isSuccessful(), "malformed persisted state rejected");
		assertEquals(malformedBefore, malformed.getCache().getCacheMap(), "malformed persisted state remains untouched");
		assertEquals(10, shops.getStock("falador.brawn"), "malformed state leaves stock untouched");
	}

	private static MonsterSlayerShopService.ItemGrant countingGrant(final AtomicInteger grants) { return new MonsterSlayerShopService.ItemGrant() { public boolean grant(Player player, int itemId, int amount) { grants.incrementAndGet(); return true; }}; }
	private static MonsterSlayerShopService.ItemGrant rejectingGrant() { return new MonsterSlayerShopService.ItemGrant() { public boolean grant(Player player, int itemId, int amount) { return false; }}; }
	private static MonsterSlayerShopService.ItemGrant throwingGrant() { return new MonsterSlayerShopService.ItemGrant() { public boolean grant(Player player, int itemId, int amount) { throw new IllegalStateException("fixture"); }}; }
	private static MonsterSlayerShopService.Result[] concurrently(final Callable<MonsterSlayerShopService.Result> first, final Callable<MonsterSlayerShopService.Result> second) throws Exception { final CountDownLatch ready = new CountDownLatch(2); final CountDownLatch start = new CountDownLatch(1); final AtomicReference<MonsterSlayerShopService.Result> firstResult = new AtomicReference<MonsterSlayerShopService.Result>(); final AtomicReference<MonsterSlayerShopService.Result> secondResult = new AtomicReference<MonsterSlayerShopService.Result>(); final AtomicReference<Throwable> failure = new AtomicReference<Throwable>(); Thread firstThread = new Thread(new Runnable() { public void run() { executeConcurrent(first, ready, start, firstResult, failure); }}, "monster-slayer-shop-first"); Thread secondThread = new Thread(new Runnable() { public void run() { executeConcurrent(second, ready, start, secondResult, failure); }}, "monster-slayer-shop-second"); firstThread.start(); secondThread.start(); ready.await(); start.countDown(); firstThread.join(); secondThread.join(); if (failure.get() != null) throw new AssertionError("concurrent shop fixture failed", failure.get()); return new MonsterSlayerShopService.Result[] {firstResult.get(), secondResult.get()}; }
	private static void executeConcurrent(Callable<MonsterSlayerShopService.Result> operation, CountDownLatch ready, CountDownLatch start, AtomicReference<MonsterSlayerShopService.Result> result, AtomicReference<Throwable> failure) { try { ready.countDown(); start.await(); result.set(operation.call()); } catch (Throwable thrown) { failure.compareAndSet(null, thrown); } }
	private static MonsterSlayerBalances balances(Player player, MonsterSlayerData data) { return MonsterSlayerState.read(player.getCache(), data).getBalances(); }
	private static int successful(MonsterSlayerShopService.Result first, MonsterSlayerShopService.Result second) { return (first.isSuccessful() ? 1 : 0) + (second.isSuccessful() ? 1 : 0); }
	private static int prefixMask(int tier) { return tier == 0 ? 0 : (1 << tier) - 1; }
	private static int capacityBonusThrough(int tier) { int[] gains = {1, 1, 1, 2, 2, 3}; int total = 0; for (int i = 0; i <= tier; i++) total += gains[i]; return total; }
	private static void assertTypedDeduction(Map<MonsterSlayerChallenge, Long> before, Map<MonsterSlayerChallenge, Long> after, MonsterSlayerCost cost, String message) { for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) assertEquals(before.get(challenge).longValue() - cost.get(challenge), after.get(challenge).longValue(), message + " " + challenge); }
	private static MonsterSlayerData data() { return MonsterSlayerData.load(Paths.get("conf", "server", "defs", "extras", "MonsterSlayer.json"), new MonsterSlayerData.ReferenceCatalog() { public boolean npcExists(int id) { return true; } public boolean npcAttackable(int id) { return true; } public boolean npcSpawned(int id) { return true; } public boolean itemExists(int id) { return true; }}); }
	private static void state(Player player, MonsterSlayerData data, long points, int upgrades, int tier) { Map<MonsterSlayerChallenge, Long> balances = new LinkedHashMap<MonsterSlayerChallenge, Long>(); for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) balances.put(challenge, points); Map<String, Integer> cursors = new LinkedHashMap<String, Integer>(); int index = 0; for (MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) cursors.put(contact.getKey(), index++ < tier ? contact.getMandatoryTasks().size() : 0); MonsterSlayerState.write(player.getCache(), data, MonsterSlayerState.create(2, MonsterSlayerRank.fromCode(tier + 1), MonsterSlayerBalances.of(balances), cursors, null, 0, 0L, upgrades, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data)); }
	private static void assertTrue(boolean value, String message) { if (!value) throw new AssertionError(message); }
	private static void assertFalse(boolean value, String message) { assertTrue(!value, message); }
	private static void assertEquals(long expected, long actual, String message) { if (expected != actual) throw new AssertionError(message + " expected=" + expected + " actual=" + actual); }
	private static void assertEquals(int expected, int actual, String message) { if (expected != actual) throw new AssertionError(message + " expected=" + expected + " actual=" + actual); }
	private static void assertEquals(Object expected, Object actual, String message) { if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(message + " expected=" + expected + " actual=" + actual); }
}
