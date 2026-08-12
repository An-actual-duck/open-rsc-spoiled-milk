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
import com.openrsc.server.content.market.MarketInventoryAdmission;
import com.openrsc.server.net.rsc.handlers.PlayerTradeHandler;
import com.openrsc.server.constants.ItemId;
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
		h.installMonsterSlayerData(data);
		risingSunAleTransactionOutcomes(h, data);
		basicRedemptionAndRollback(h, data);
		everyShopDeductsItsTypedCost(h, data);
		capacityEntitlementsAreOrderedAndPersistCapacity(h, data);
		concurrentRedemptionAndEntitlementPurchasesAreAtomic(h, data);
		fullAndMalformedPlayersRemainUntouched(h, data);
		capacityAwareAdmissionPreservesItems(h, data);
	}

	/** Exercises the exact capacity seams used by bank, trade, market, and item actions. */
	private static void capacityAwareAdmissionPreservesItems(CurrentCombatHarness h,
			MonsterSlayerData data) {
		int[] upgrades = {0, 0x3f};
		int[] capacities = {Inventory.BASE_SIZE, Inventory.MAX_SUPPORTED_SIZE};
		for (int fixture = 0; fixture < capacities.length; fixture++) {
			final int capacity = capacities[fixture];
			Player player = h.player("msscapacityadmission" + fixture, 900 + fixture, 790);
			state(player, data, 0L, upgrades[fixture], 5);
			Inventory inventory = player.getCarriedItems().getInventory();
			assertEquals(capacity, inventory.getCapacity(), "authoritative active capacity " + capacity);
			for (int slot = 0; slot < capacity; slot++) {
				assertTrue(inventory.isValidSlot(slot), "active inventory slot " + capacity + ":" + slot);
			}
			assertFalse(inventory.isValidSlot(capacity), "locked inventory slot " + capacity);

			for (int slot = 0; slot < capacity - 1; slot++) inventory.getItems().add(new Item(259, 1));
			assertTrue(inventory.canHold(new Item(259, 1)), "one remaining banking slot " + capacity);
			assertTrue(MarketInventoryAdmission.canReceive(inventory, 259, 1, false),
				"market purchase/return fits final slot " + capacity);
			inventory.getItems().add(new Item(259, 1));
			assertTrue(inventory.full(), "full inventory " + capacity);
			assertFalse(inventory.canHold(new Item(259, 1)), "banking rejects overflow " + capacity);
			assertFalse(MarketInventoryAdmission.canReceive(inventory, 259, 1, false),
				"market purchase/return rejects overflow " + capacity);

			Item outgoing = inventory.getItems().get(0);
			java.util.List<Item> offer = java.util.Collections.singletonList(new Item(
				outgoing.getCatalogId(), outgoing.getAmount(), outgoing.getNoted()));
			assertEquals(1, PlayerTradeHandler.availableSlotsAfterOffer(inventory, offer),
				"trade reclaims offered slot " + capacity);
			for (int equipmentSlot = 0; equipmentSlot < 15; equipmentSlot++) {
				int encoded = Inventory.EQUIPMENT_ACTION_SLOT_OFFSET + equipmentSlot;
				assertTrue(Inventory.isEquipmentActionSlot(encoded),
					"encoded equipment slot " + equipmentSlot);
				assertEquals(equipmentSlot, Inventory.equipmentSlotFromActionSlot(encoded),
					"equipment action round-trip " + equipmentSlot);
			}
			assertFalse(Inventory.isEquipmentActionSlot(Inventory.EQUIPMENT_ACTION_SLOT_OFFSET - 1),
				"inventory slot 39 never aliases equipment");
			assertFalse(Inventory.isEquipmentActionSlot(Inventory.EQUIPMENT_ACTION_SLOT_OFFSET + 15),
				"equipment action upper bound");
		}
	}

	private static void risingSunAleTransactionOutcomes(CurrentCombatHarness h, MonsterSlayerData data) {
		defaultTransactionAcceptsEveryRisingSunAle(h, data);
		offeredDrinkMenusAreCompleteAndBounded(h);
		explicitDrinkSelectionAndRollbackAreExact(h, data);
		final AtomicInteger consumed = new AtomicInteger();
		final AtomicInteger refunded = new AtomicInteger();
		MonsterSlayerContactService.RisingSunAleTransaction transaction = new MonsterSlayerContactService.RisingSunAleTransaction() {
			public Item consume(Player player) { consumed.incrementAndGet(); return new Item(ItemId.DWARVEN_STOUT.id()); }
			public boolean refund(Player player, Item ale) { refunded.incrementAndGet(); assertEquals(ItemId.DWARVEN_STOUT.id(), ale.getCatalogId(), "rollback preserves exact ale"); return true; }
		};
		MonsterSlayerContactService contacts = new MonsterSlayerContactService(data, new com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerTaskService(data), new MonsterSlayerContactService.RandomSource() { public int nextInt(int bound) { return 0; }}, transaction);
		Player missing = h.player("mssmissingale", 840, 790);
		MonsterSlayerState.write(missing.getCache(), data, MonsterSlayerState.beginIntroduction(MonsterSlayerState.defaults(data), data));
		MonsterSlayerContactService missingAle = new MonsterSlayerContactService(data, new com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerTaskService(data), new MonsterSlayerContactService.RandomSource() { public int nextInt(int bound) { return 0; }}, new MonsterSlayerContactService.RisingSunAleTransaction() { public Item consume(Player player) { return null; } public boolean refund(Player player, Item ale) { return true; }});
		assertEquals("missing-rising-sun-ale", missingAle.completeIntroductionWithRisingSunAle(missing).getReason(), "missing Rising Sun ale result");
		assertEquals(0, consumed.get(), "missing Rising Sun ale does not consume");
		Player recruit = h.player("mssaletransaction", 841, 790);
		MonsterSlayerState.write(recruit.getCache(), data, MonsterSlayerState.beginIntroduction(MonsterSlayerState.defaults(data), data));
		recruit.getCache().store("unrelated_ale_fixture", "preserved");
		MonsterSlayerContactService.Result aleResult = contacts.completeIntroductionWithRisingSunAle(recruit);
		assertTrue(aleResult.isAccepted(), "Rising Sun ale transaction succeeds: " + aleResult.getReason());
		assertEquals(1, consumed.get(), "Rising Sun ale consumes exactly once");
		assertFalse(contacts.completeIntroductionWithRisingSunAle(recruit).isAccepted(), "duplicate Rising Sun ale submission rejected");
		assertEquals(1, consumed.get(), "duplicate does not consume again");
		assertEquals("preserved", recruit.getCache().getString("unrelated_ale_fixture"), "unrelated cache preserved");
		assertEquals(0, refunded.get(), "successful transaction does not refund");

		Player writeFailure = h.player("mssalewritefailure", 842, 790);
		MonsterSlayerState.write(writeFailure.getCache(), data, MonsterSlayerState.beginIntroduction(MonsterSlayerState.defaults(data), data));
		final AtomicInteger writeRefunds = new AtomicInteger();
		MonsterSlayerContactService.Result failedWrite = contacts(data, new MonsterSlayerContactService.RisingSunAleTransaction() {
			public Item consume(Player player) { return new Item(ItemId.WIZARDS_MIND_BOMB.id()); }
			public boolean refund(Player player, Item ale) { writeRefunds.incrementAndGet(); assertEquals(ItemId.WIZARDS_MIND_BOMB.id(), ale.getCatalogId(), "Mind Bomb rollback preserves exact ale"); return true; }
		}, failingWrites()).completeIntroductionWithRisingSunAle(writeFailure);
		assertEquals("state-write-failed", failedWrite.getReason(), "failed cache write gives truthful result");
		assertEquals(1, writeRefunds.get(), "failed cache write refunds exactly once");
		assertEquals(1, MonsterSlayerState.read(writeFailure.getCache(), data).getIntroStage(), "failed cache write preserves introduction state");

		Player refundFailure = h.player("mssalerefundfailure", 843, 790);
		MonsterSlayerState.write(refundFailure.getCache(), data, MonsterSlayerState.beginIntroduction(MonsterSlayerState.defaults(data), data));
		MonsterSlayerContactService.Result failedRefund = contacts(data, new MonsterSlayerContactService.RisingSunAleTransaction() {
			public Item consume(Player player) { return new Item(ItemId.ASGARNIAN_ALE.id()); }
			public boolean refund(Player player, Item ale) { return false; }
		}, failingWrites()).completeIntroductionWithRisingSunAle(refundFailure);
		assertEquals("refund-failed", failedRefund.getReason(), "failed refund is explicit");

		final Player concurrent = h.player("mssaleconcurrent", 844, 790);
		MonsterSlayerState.write(concurrent.getCache(), data, MonsterSlayerState.beginIntroduction(MonsterSlayerState.defaults(data), data));
		final AtomicInteger concurrentConsumes = new AtomicInteger();
		final MonsterSlayerContactService concurrentContacts = contacts(data, new MonsterSlayerContactService.RisingSunAleTransaction() {
			public Item consume(Player player) { concurrentConsumes.incrementAndGet(); return new Item(ItemId.ASGARNIAN_ALE.id()); }
			public boolean refund(Player player, Item ale) { return true; }
		}, normalStore());
		MonsterSlayerContactService.Result[] submissions = concurrentAleSubmissions(concurrentContacts, concurrent);
		assertEquals(1, successful(submissions[0], submissions[1]), "one simultaneous Rising Sun ale submission succeeds");
		assertEquals(1, concurrentConsumes.get(), "simultaneous duplicate consumes once");
	}

	private static void offeredDrinkMenusAreCompleteAndBounded(CurrentCombatHarness h) {
		int[] acceptedAles = {ItemId.ASGARNIAN_ALE.id(), ItemId.WIZARDS_MIND_BOMB.id(), ItemId.DWARVEN_STOUT.id()};
		for (int mask = 0; mask < 8; mask++) {
			Player player = h.player("mssalemenu" + mask, 850 + mask, 790);
			for (int index = 0; index < acceptedAles.length; index++) {
				if ((mask & (1 << index)) != 0) {
					player.getCarriedItems().getInventory().getItems().add(new Item(acceptedAles[index], 2, false,
						20_000_500L + mask * 10L + index));
				}
			}
			int[] offered = MonsterSlayerContactService.eligibleRisingSunAleIds(player);
			int[] expected = new int[Integer.bitCount(mask)];
			int output = 0;
			for (int index = 0; index < acceptedAles.length; index++) if ((mask & (1 << index)) != 0) expected[output++] = acceptedAles[index];
			assertEquals(java.util.Arrays.toString(expected), java.util.Arrays.toString(offered), "every drink subset is offered once " + mask);
			for (int index = 0; index < offered.length; index++) {
				assertEquals(acceptedAles[indexOf(acceptedAles, offered[index])], MonsterSlayerContactService.selectedRisingSunAleId(offered, index),
					"explicit menu selection maps to offered drink " + mask + ":" + index);
				assertTrue(MonsterSlayerContactService.risingSunAleOfferLabel(offered[index]).startsWith("I brought you "),
					"offered drink has player-facing label " + offered[index]);
			}
			assertEquals(-1, MonsterSlayerContactService.selectedRisingSunAleId(offered, offered.length),
				"Not yet declines without selecting a drink " + mask);
		}
	}

	private static void explicitDrinkSelectionAndRollbackAreExact(CurrentCombatHarness h, MonsterSlayerData data) {
		int[] acceptedAles = {ItemId.ASGARNIAN_ALE.id(), ItemId.WIZARDS_MIND_BOMB.id(), ItemId.DWARVEN_STOUT.id()};
		for (int selected : acceptedAles) {
			Player player = h.player("mssaleselected" + selected, 870 + selected % 10, 790);
			MonsterSlayerState.write(player.getCache(), data,
				MonsterSlayerState.beginIntroduction(MonsterSlayerState.defaults(data), data));
			for (int index = 0; index < acceptedAles.length; index++) player.getCarriedItems().getInventory().getItems().add(
				new Item(acceptedAles[index], 1, false, 20_000_700L + selected * 10L + index));
			MonsterSlayerContactService service = new MonsterSlayerContactService(data,
				new com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerTaskService(data));
			assertTrue(service.completeIntroductionWithRisingSunAle(player, selected).isAccepted(),
				"selected drink completes introduction " + selected);
			for (int id : acceptedAles) assertEquals(id == selected ? 0 : 1,
				player.getCarriedItems().getInventory().countId(id), "only selected drink is consumed " + selected + ":" + id);
		}

		Player stale = h.player("mssalestale", 881, 790);
		MonsterSlayerState.write(stale.getCache(), data,
			MonsterSlayerState.beginIntroduction(MonsterSlayerState.defaults(data), data));
		stale.getCarriedItems().getInventory().getItems().add(new Item(ItemId.ASGARNIAN_ALE.id(), 1, false, 20_000_900L));
		stale.getCarriedItems().getInventory().getItems().add(new Item(ItemId.DWARVEN_STOUT.id(), 1, false, 20_000_901L));
		int[] offered = MonsterSlayerContactService.eligibleRisingSunAleIds(stale);
		int selected = MonsterSlayerContactService.selectedRisingSunAleId(offered, 0);
		stale.getCarriedItems().remove(new Item(selected));
		MonsterSlayerContactService service = new MonsterSlayerContactService(data,
			new com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerTaskService(data));
		assertEquals("missing-rising-sun-ale", service.completeIntroductionWithRisingSunAle(stale, selected).getReason(),
			"stale menu selection does not substitute another drink");
		assertEquals(1, stale.getCarriedItems().getInventory().countId(ItemId.DWARVEN_STOUT.id()),
			"stale selection preserves other offered drink");
		assertEquals(1, MonsterSlayerState.read(stale.getCache(), data).getIntroStage(),
			"stale selection preserves pending introduction");

		final AtomicInteger refunded = new AtomicInteger();
		Player rollback = h.player("mssaleselectedrollback", 882, 790);
		MonsterSlayerState.write(rollback.getCache(), data,
			MonsterSlayerState.beginIntroduction(MonsterSlayerState.defaults(data), data));
		MonsterSlayerContactService transactional = contacts(data, new MonsterSlayerContactService.RisingSunAleTransaction() {
			public Item consume(Player player) { throw new AssertionError("explicit selection must use selected consume path"); }
			public Item consume(Player player, int itemId) {
				Item selected = new Item(itemId);
				return player.getCarriedItems().remove(selected) != -1 ? selected : null;
			}
			public boolean refund(Player player, Item item) {
				refunded.incrementAndGet(); assertEquals(ItemId.WIZARDS_MIND_BOMB.id(), item.getCatalogId(), "rollback refunds selected drink");
				player.getCarriedItems().getInventory().getItems().add(item);
				return true;
			}
		}, failingWrites());
		rollback.getCarriedItems().getInventory().getItems().add(new Item(ItemId.WIZARDS_MIND_BOMB.id(), 1, false, 20_000_902L));
		assertEquals("state-write-failed", transactional.completeIntroductionWithRisingSunAle(rollback, ItemId.WIZARDS_MIND_BOMB.id()).getReason(),
			"selected drink rollback is reported");
		assertEquals(1, refunded.get(), "selected drink is refunded exactly once");
		assertEquals(1, rollback.getCarriedItems().getInventory().countId(ItemId.WIZARDS_MIND_BOMB.id()),
			"selected drink is restored after persistence failure");
	}

	private static int indexOf(int[] values, int value) {
		for (int index = 0; index < values.length; index++) if (values[index] == value) return index;
		throw new AssertionError("missing accepted drink " + value);
	}

	private static void defaultTransactionAcceptsEveryRisingSunAle(CurrentCombatHarness h,
		MonsterSlayerData data) {
		int[] acceptedAles = {ItemId.ASGARNIAN_ALE.id(), ItemId.WIZARDS_MIND_BOMB.id(), ItemId.DWARVEN_STOUT.id()};
		for (int index = 0; index < acceptedAles.length; index++) {
			int aleId = acceptedAles[index];
			Player player = h.player("mssale" + aleId, 840 + index, 790);
			MonsterSlayerState.write(player.getCache(), data,
				MonsterSlayerState.beginIntroduction(MonsterSlayerState.defaults(data), data));
			// Harness players deliberately bypass the normal login registry. Seed the
			// fixture container directly; production additions require that registry.
			player.getCarriedItems().getInventory().getItems().add(new Item(ItemId.BEER.id(), 1, false, 20_000_000L + index * 10L));
			player.getCarriedItems().getInventory().getItems().add(new Item(aleId, 1, false, 20_000_001L + index * 10L));
			assertTrue(MonsterSlayerContactService.hasRisingSunAle(player), "accepted Rising Sun ale is recognized " + aleId);
			MonsterSlayerContactService service = new MonsterSlayerContactService(data,
				new com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerTaskService(data));
			assertTrue(service.completeIntroductionWithRisingSunAle(player).isAccepted(), "accepted Rising Sun ale completes introduction " + aleId);
			assertEquals(0, player.getCarriedItems().getInventory().countId(aleId), "consumes exact Rising Sun ale " + aleId);
			assertEquals(1, player.getCarriedItems().getInventory().countId(ItemId.BEER.id()), "ordinary Beer is not consumed " + aleId);
		}
		Player beerOnly = h.player("mssbeeronly", 844, 790);
		beerOnly.getCarriedItems().getInventory().getItems().add(new Item(ItemId.BEER.id(), 1, false, 20_000_100L));
		assertFalse(MonsterSlayerContactService.hasRisingSunAle(beerOnly), "ordinary Beer is not a Rising Sun ale");
	}

	private static MonsterSlayerContactService contacts(MonsterSlayerData data, MonsterSlayerContactService.RisingSunAleTransaction ale, MonsterSlayerContactService.StateStore store) {
		return new MonsterSlayerContactService(data, new com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerTaskService(data), new MonsterSlayerContactService.RandomSource() { public int nextInt(int bound) { return 0; }}, ale, store);
	}
	private static MonsterSlayerContactService.StateStore normalStore() { return new MonsterSlayerContactService.StateStore() { public MonsterSlayerState.Snapshot read(com.openrsc.server.model.Cache cache, MonsterSlayerData data) { return MonsterSlayerState.read(cache, data); } public void write(com.openrsc.server.model.Cache cache, MonsterSlayerData data, MonsterSlayerState.Snapshot snapshot) { MonsterSlayerState.write(cache, data, snapshot); }}; }
	private static MonsterSlayerContactService.StateStore failingWrites() { return new MonsterSlayerContactService.StateStore() { public MonsterSlayerState.Snapshot read(com.openrsc.server.model.Cache cache, MonsterSlayerData data) { return MonsterSlayerState.read(cache, data); } public void write(com.openrsc.server.model.Cache cache, MonsterSlayerData data, MonsterSlayerState.Snapshot snapshot) { throw new IllegalStateException("fixture write failure"); }}; }
	private static MonsterSlayerContactService.Result[] concurrentAleSubmissions(final MonsterSlayerContactService contacts, final Player player) {
		final CountDownLatch ready = new CountDownLatch(2); final CountDownLatch start = new CountDownLatch(1); final AtomicReference<MonsterSlayerContactService.Result> first = new AtomicReference<MonsterSlayerContactService.Result>(); final AtomicReference<MonsterSlayerContactService.Result> second = new AtomicReference<MonsterSlayerContactService.Result>();
		Thread one = new Thread(new Runnable() { public void run() { ready.countDown(); await(start); first.set(contacts.completeIntroductionWithRisingSunAle(player)); }}, "monster-slayer-ale-first");
		Thread two = new Thread(new Runnable() { public void run() { ready.countDown(); await(start); second.set(contacts.completeIntroductionWithRisingSunAle(player)); }}, "monster-slayer-ale-second");
		one.start(); two.start(); await(ready); start.countDown(); join(one); join(two); return new MonsterSlayerContactService.Result[] {first.get(), second.get()};
	}
	private static MonsterSlayerContactService.Result[] concurrentAssignments(final MonsterSlayerContactService contacts, final Player player, final String contactKey) {
		final CountDownLatch ready = new CountDownLatch(2); final CountDownLatch start = new CountDownLatch(1); final AtomicReference<MonsterSlayerContactService.Result> first = new AtomicReference<MonsterSlayerContactService.Result>(); final AtomicReference<MonsterSlayerContactService.Result> second = new AtomicReference<MonsterSlayerContactService.Result>();
		Thread one = new Thread(new Runnable() { public void run() { ready.countDown(); await(start); first.set(contacts.requestTask(player, contactKey)); }}, "monster-slayer-task-first");
		Thread two = new Thread(new Runnable() { public void run() { ready.countDown(); await(start); second.set(contacts.requestTask(player, contactKey)); }}, "monster-slayer-task-second");
		one.start(); two.start(); await(ready); start.countDown(); join(one); join(two); return new MonsterSlayerContactService.Result[] {first.get(), second.get()};
	}
	private static void await(CountDownLatch latch) { try { latch.await(); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError("interrupted fixture", failure); } }
	private static void join(Thread thread) { try { thread.join(); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError("interrupted fixture", failure); } }

	static void contactRoutesAreRankedAndSingleAssignment(CurrentCombatHarness h) throws Exception {
		MonsterSlayerData data = data();
		MonsterSlayerContactService contacts = new MonsterSlayerContactService(data, new com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerTaskService(data));
		Player recruit = h.player("mssbeer", 850, 790);
		MonsterSlayerState.write(recruit.getCache(), data, MonsterSlayerState.defaults(data));
		assertTrue(contacts.beginIntroduction(recruit).isAccepted(), "introduction begins");
		assertTrue(contacts.completeIntroduction(recruit).isAccepted(), "introduction completes");
		assertFalse(contacts.completeIntroduction(recruit).isAccepted(), "introduction cannot complete twice");
		for (int tier = 0; tier < data.getContacts().size(); tier++) {
			MonsterSlayerDefinitions.Contact contact = data.getContacts().get(tier);
			Player player = h.player("msscontact" + tier, 860 + tier, 790);
			state(player, data, 0L, prefixMask(tier), tier);
			assertTrue(contacts.requestTask(player, contact.getKey()).isAccepted(), "contact assignment " + contact.getKey());
			assertFalse(contacts.requestTask(player, contact.getKey()).isAccepted(), "contact duplicate assignment " + contact.getKey());
		}
		final Player contended = h.player("msscontactconcurrent", 868, 790); state(contended, data, 0L, 0, 0);
		MonsterSlayerContactService.Result[] contendedResults = concurrentAssignments(contacts, contended, "falador");
		assertEquals(1, successful(contendedResults[0], contendedResults[1]), "one concurrent task assignment succeeds");
		assertTrue(MonsterSlayerState.read(contended.getCache(), data).getActiveTaskKey() != null, "concurrent task assignment leaves one active task");
		int fixture = 0;
		for (int tier = 0; tier < data.getContacts().size(); tier++) for (int pick = 0; pick < data.getContacts().get(tier).getRepeatableTasks().size(); pick++) {
			final int selected = pick; final MonsterSlayerDefinitions.Contact contact = data.getContacts().get(tier);
			MonsterSlayerContactService randomized = new MonsterSlayerContactService(data, new com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerTaskService(data), new MonsterSlayerContactService.RandomSource() { public int nextInt(int bound) { return selected; }});
			Player repeatable = h.player("mssrepeat" + fixture, 870 + fixture++, 790); state(repeatable, data, 0L, 0, tier + 1);
			Map<String, Object> beforePreview = new LinkedHashMap<String, Object>(repeatable.getCache().getCacheMap());
			MonsterSlayerDefinitions.Task preview = randomized.previewTask(repeatable, contact.getKey());
			assertEquals(contact.getRepeatableTasks().get(pick).getKey(), preview.getKey(), "injectable repeatable pick " + contact.getKey() + " " + pick);
			assertEquals(beforePreview, repeatable.getCache().getCacheMap(), "warning preview is before state write " + contact.getKey() + " " + pick);
			assertTrue(randomized.requestTask(repeatable, contact.getKey()).isAccepted(), "previewed repeatable assigns " + contact.getKey() + " " + pick);
			assertEquals(preview.getKey(), MonsterSlayerState.read(repeatable.getCache(), data).getActiveTaskKey(), "preview equals committed repeatable " + contact.getKey() + " " + pick);
		}
		Player corrupt = h.player("msscontactcorrupt", 895, 790); state(corrupt, data, 0L, 0, 0);
		corrupt.getCache().store("monster_slayer_rank", "corrupt"); Map<String, Object> corruptBefore = new LinkedHashMap<String, Object>(corrupt.getCache().getCacheMap());
		assertFalse(contacts.requestTask(corrupt, "falador").isAccepted(), "corrupt assignment state rejected");
		assertEquals(corruptBefore, corrupt.getCache().getCacheMap(), "corrupt assignment leaves state untouched");
	}

	private static void basicRedemptionAndRollback(CurrentCombatHarness h, MonsterSlayerData data) throws Exception {
		AtomicInteger grants = new AtomicInteger();
		MonsterSlayerShopService shops = new MonsterSlayerShopService(data, countingGrant(grants));
		Player player = h.player("msshoptx", 780, 790); state(player, data, 40L, 0, 0);
		assertTrue(shops.redeem(player, "falador", "falador.brawn", 2).isSuccessful(), "real redeem");
		assertEquals(1, grants.get(), "one output grant");
		assertEquals(-1, shops.getStock("falador.brawn"), "infinite reward stock");
		assertEquals(36L, balances(player, data).get(MonsterSlayerChallenge.FLEDGLING), "exact point deduction");
		shops.restock();
		assertEquals(-1, shops.getStock("falador.brawn"), "restock is irrelevant for infinite rewards");
		assertEquals("quantity", shops.redeem(player, "falador", "falador.brawn", 0).getReason(), "zero quantity is explicit");
		assertEquals("quantity", shops.redeem(player, "falador", "falador.brawn", -1).getReason(), "negative quantity is explicit");
		assertFalse(shops.redeem(player, "falador", "falador.brawn", Long.MAX_VALUE).isSuccessful(), "quantity overflow");

		Player failed = h.player("msshopfail", 790, 790); state(failed, data, 40L, 0, 0);
		failed.getCache().store("unrelated", "keep");
		Map<String, Object> before = new LinkedHashMap<String, Object>(failed.getCache().getCacheMap());
		MonsterSlayerShopService rejecting = new MonsterSlayerShopService(data, rejectingGrant());
		assertFalse(rejecting.redeem(failed, "falador", "falador.brawn", 1).isSuccessful(), "false grant");
		assertEquals(before, failed.getCache().getCacheMap(), "false grant rollback preserves cache");
		assertEquals(-1, rejecting.getStock("falador.brawn"), "false grant retains infinite stock");
		MonsterSlayerShopService throwing = new MonsterSlayerShopService(data, throwingGrant());
		assertFalse(throwing.redeem(failed, "falador", "falador.brawn", 1).isSuccessful(), "throwing grant");
		assertEquals(before, failed.getCache().getCacheMap(), "throwing grant rollback preserves cache");
		assertEquals(-1, throwing.getStock("falador.brawn"), "throwing grant retains infinite stock");
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
			assertEquals(-1, shops.getStock(reward.getKey()), "shop tier has infinite stock " + tier);
		}
		assertEquals(6, grants.get(), "one output per shop redemption");
	}

	private static void capacityEntitlementsAreOrderedAndPersistCapacity(CurrentCombatHarness h, MonsterSlayerData data) throws Exception {
		MonsterSlayerShopService shops = new MonsterSlayerShopService(data, rejectingGrant());
		Player insufficient = h.player("mssinsufficientcapacity", 819, 790);
		long firstPrice = data.getShop("falador").getCapacityUpgrade().getCost().get(MonsterSlayerChallenge.FLEDGLING);
		state(insufficient, data, firstPrice - 1L, 0, 0);
		Map<String, Object> insufficientBefore = new LinkedHashMap<String, Object>(insufficient.getCache().getCacheMap());
		assertFalse(shops.purchaseCapacity(insufficient, "falador").isSuccessful(), "insufficient points reject capacity purchase");
		assertEquals(insufficientBefore, insufficient.getCache().getCacheMap(), "insufficient capacity purchase leaves state unchanged");

		Player outOfOrder = h.player("mssorder", 820, 790); state(outOfOrder, data, 1000L, 0, 5);
		Map<String, Object> outOfOrderBefore = new LinkedHashMap<String, Object>(outOfOrder.getCache().getCacheMap());
		assertFalse(shops.purchaseCapacity(outOfOrder, data.getShops().get(1).getKey()).isSuccessful(), "out-of-order capacity purchase");
		assertEquals(outOfOrderBefore, outOfOrder.getCache().getCacheMap(), "out-of-order capacity leaves state unchanged");

		Player player = h.player("msscapacity", 821, 790); state(player, data, 1000L, 0, 5);
		for (int tier = 0; tier < data.getShops().size(); tier++) {
			MonsterSlayerDefinitions.Shop shop = data.getShops().get(tier);
			MonsterSlayerState.Snapshot before = MonsterSlayerState.read(player.getCache(), data);
			Map<String, Object> declinedBefore = new LinkedHashMap<String, Object>(player.getCache().getCacheMap());
			assertTrue(shops.proposeCapacityPurchase(before, shop.getKey()).isSuccessful(), "capacity confirmation proposal " + tier);
			assertEquals(declinedBefore, player.getCache().getCacheMap(), "declined capacity confirmation leaves state unchanged " + tier);
			assertTrue(shops.purchaseCapacity(player, shop.getKey()).isSuccessful(), "ordered capacity purchase " + tier);
			MonsterSlayerState.Snapshot after = MonsterSlayerState.read(player.getCache(), data);
			assertTypedDeduction(before.getBalances().asMap(), after.getBalances().asMap(), shop.getCapacityUpgrade().getCost(), "capacity tier " + tier);
			assertEquals((1 << (tier + 1)) - 1, after.getInventoryUpgrades(), "capacity mask tier " + tier);
			assertEquals(30 + capacityBonusThrough(tier), after.getDerivedInventoryCapacity(), "active capacity entitlement " + tier);
			assertEquals(after.getDerivedInventoryCapacity(), player.getCarriedItems().getInventory().getCapacity(), "live capacity follows entitlement " + tier);
			if (tier == 0) {
				for (int slot = 0; slot < Inventory.BASE_SIZE; slot++) player.getCarriedItems().getInventory().getItems().add(new Item(259, 1));
				assertTrue(player.getCarriedItems().getInventory().canHold(new Item(259, 1)), "newly unlocked slot accepts an item");
				player.getCarriedItems().getInventory().getItems().add(new Item(259, 1));
				assertEquals(31, player.getCarriedItems().getInventory().size(), "newly unlocked item remains in inventory");
				assertEquals(31, MonsterSlayerState.read(player.getCache(), data).getDerivedInventoryCapacity(), "newly unlocked slot persists across state reload");
			}
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
		assertEquals(-1, shops.getStock("falador.brawn"), "concurrent purchases retain infinite stock");

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
		for (int i = 0; i < Inventory.BASE_SIZE; i++) full.getCarriedItems().getInventory().getItems().add(new Item(259, 1));
		Map<String, Object> fullBefore = new LinkedHashMap<String, Object>(full.getCache().getCacheMap());
		assertFalse(shops.redeem(full, "falador", fullInventoryReward, 1).isSuccessful(), "full inventory redemption rejected");
		assertEquals(fullBefore, full.getCache().getCacheMap(), "full inventory leaves points untouched");
		assertEquals(-1, shops.getStock(fullInventoryReward), "full inventory retains infinite stock");
		assertEquals(Inventory.BASE_SIZE, full.getCarriedItems().getInventory().size(), "full inventory leaves items untouched");
		assertEquals(0, fullGrants.get(), "full inventory never invokes item grant");

		Player malformed = h.player("mssmalformed", 841, 790); state(malformed, data, 40L, 0, 0);
		malformed.getCache().store("monster_slayer_balance_fledgling", "corrupt-evidence");
		Map<String, Object> malformedBefore = new LinkedHashMap<String, Object>(malformed.getCache().getCacheMap());
		assertFalse(shops.redeem(malformed, "falador", "falador.brawn", 1).isSuccessful(), "malformed persisted state rejected");
		assertEquals(malformedBefore, malformed.getCache().getCacheMap(), "malformed persisted state remains untouched");
		assertEquals(-1, shops.getStock("falador.brawn"), "malformed state retains infinite stock");
	}

	private static MonsterSlayerShopService.ItemGrant countingGrant(final AtomicInteger grants) { return new MonsterSlayerShopService.ItemGrant() { public boolean grant(Player player, int itemId, int amount) { grants.incrementAndGet(); return true; }}; }
	private static MonsterSlayerShopService.ItemGrant rejectingGrant() { return new MonsterSlayerShopService.ItemGrant() { public boolean grant(Player player, int itemId, int amount) { return false; }}; }
	private static MonsterSlayerShopService.ItemGrant throwingGrant() { return new MonsterSlayerShopService.ItemGrant() { public boolean grant(Player player, int itemId, int amount) { throw new IllegalStateException("fixture"); }}; }
	private static MonsterSlayerShopService.Result[] concurrently(final Callable<MonsterSlayerShopService.Result> first, final Callable<MonsterSlayerShopService.Result> second) throws Exception { final CountDownLatch ready = new CountDownLatch(2); final CountDownLatch start = new CountDownLatch(1); final AtomicReference<MonsterSlayerShopService.Result> firstResult = new AtomicReference<MonsterSlayerShopService.Result>(); final AtomicReference<MonsterSlayerShopService.Result> secondResult = new AtomicReference<MonsterSlayerShopService.Result>(); final AtomicReference<Throwable> failure = new AtomicReference<Throwable>(); Thread firstThread = new Thread(new Runnable() { public void run() { executeConcurrent(first, ready, start, firstResult, failure); }}, "monster-slayer-shop-first"); Thread secondThread = new Thread(new Runnable() { public void run() { executeConcurrent(second, ready, start, secondResult, failure); }}, "monster-slayer-shop-second"); firstThread.start(); secondThread.start(); ready.await(); start.countDown(); firstThread.join(); secondThread.join(); if (failure.get() != null) throw new AssertionError("concurrent shop fixture failed", failure.get()); return new MonsterSlayerShopService.Result[] {firstResult.get(), secondResult.get()}; }
	private static void executeConcurrent(Callable<MonsterSlayerShopService.Result> operation, CountDownLatch ready, CountDownLatch start, AtomicReference<MonsterSlayerShopService.Result> result, AtomicReference<Throwable> failure) { try { ready.countDown(); start.await(); result.set(operation.call()); } catch (Throwable thrown) { failure.compareAndSet(null, thrown); } }
	private static MonsterSlayerBalances balances(Player player, MonsterSlayerData data) { return MonsterSlayerState.read(player.getCache(), data).getBalances(); }
	private static int successful(MonsterSlayerShopService.Result first, MonsterSlayerShopService.Result second) { return (first.isSuccessful() ? 1 : 0) + (second.isSuccessful() ? 1 : 0); }
	private static int successful(MonsterSlayerContactService.Result first, MonsterSlayerContactService.Result second) { return (first.isAccepted() ? 1 : 0) + (second.isAccepted() ? 1 : 0); }
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
