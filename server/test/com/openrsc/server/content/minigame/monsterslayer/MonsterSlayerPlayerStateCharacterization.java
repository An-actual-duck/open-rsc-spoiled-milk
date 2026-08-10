package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.model.Cache;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/** Executable regression coverage for the durable, non-player-visible state slice. */
public final class MonsterSlayerPlayerStateCharacterization {
	private MonsterSlayerPlayerStateCharacterization() {
	}

	public static void main(String[] args) {
		MonsterSlayerData data = MonsterSlayerData.load(Paths.get(
			"conf", "server", "defs", "extras", "MonsterSlayer.json"), acceptingCatalog());
		CombatOdysseyMigration.LegacyData legacyData = CombatOdysseyMigration.LegacyData.load(
			Paths.get("conf", "server", "defs", "extras", "CombatOdyssey.json"));

		newAccountDefaultsPersistAndReconnect(data, legacyData);
		legacyAccountsKeepEvidenceButStartWithZeroBalances(data, legacyData);
		legacyVariantsAndFailuresRemainSafe(data, legacyData);
		invalidEntitlementsQuarantineWithoutWrites(data, legacyData);
		malformedStateQuarantinesWithoutWrites(data, legacyData);
		derivedCapacityUsesStableExplicitBits();
		taskAssignmentAndCompletionAreExactOnce(data);
		beerIntroductionIsOneTimeAndRankSafe(data);
		promotionAcknowledgementIsTypedAndIdempotent(data);
		typedPromotionPlansAreBoundedAndOrdered();
		approvedShopDefinitionsHaveStableLaunchShape(data);
		repeatablesAndHazardsUseDeclaredLaunchPolicy(data);
		headlessShopPreflightKeepsTypedCostsAndStockBounded(data);
		cacheWritesRestoreOnlyOwnedKeysAfterRuntimeFailure(data);
		failureDiagnosticsAreDuplicateSuppressedAndBounded();

		System.out.println("Monster Slayer player-state characterization: PASS");
	}

	private static void newAccountDefaultsPersistAndReconnect(MonsterSlayerData data,
			CombatOdysseyMigration.LegacyData legacyData) {
		Cache cache = new Cache();
		cache.store("unrelated_legacy_key", "preserved");
		MonsterSlayerState.LoadResult first = MonsterSlayerState.initialize(cache, data, legacyData);
		equals(MonsterSlayerState.LoadResult.Status.MIGRATED, first.getStatus(), "new account migration");
		equals(CombatOdysseyMigration.Classification.NONE, first.getClassification(), "new account class");
		equals(30, first.getSnapshot().getDerivedInventoryCapacity(), "base capacity");
		for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) {
			equals(0L, first.getSnapshot().getBalances().get(challenge), "new balance " + challenge);
		}
		equals("preserved", cache.getString("unrelated_legacy_key"), "unrelated cache preservation");

		Map<String, Object> persisted = new LinkedHashMap<String, Object>(cache.getCacheMap());
		MonsterSlayerState.LoadResult reconnect = MonsterSlayerState.initialize(cache, data, legacyData);
		equals(MonsterSlayerState.LoadResult.Status.LOADED, reconnect.getStatus(), "reconnect status");
		equals(persisted, cache.getCacheMap(), "reconnect does not duplicate writes");

		Cache roundTrip = new Cache();
		for (Map.Entry<String, Object> entry : cache.getCacheMap().entrySet()) {
			roundTrip.put(entry.getKey(), entry.getValue());
		}
		MonsterSlayerState.Snapshot reloaded = MonsterSlayerState.read(roundTrip, data);
		equals(0, reloaded.getInventoryUpgrades(), "round-trip entitlement mask");
		equals(30, reloaded.getDerivedInventoryCapacity(), "round-trip derived capacity");
	}

	private static void legacyAccountsKeepEvidenceButStartWithZeroBalances(MonsterSlayerData data,
			CombatOdysseyMigration.LegacyData legacyData) {
		Cache partial = new Cache();
		partial.store("combat_odyssey", "1");
		MonsterSlayerState.LoadResult partialResult = MonsterSlayerState.initialize(partial, data, legacyData);
		equals(MonsterSlayerState.LoadResult.Status.MIGRATED, partialResult.getStatus(), "partial migration");
		equals(MonsterSlayerRank.FLEDGLING, partialResult.getSnapshot().getRank(), "partial rank");
		equals("1", partial.getString("combat_odyssey"), "partial legacy state preserved");
		assertZeroBalances(partialResult.getSnapshot());

		Cache completed = new Cache();
		completed.store("co_prestige", 2);
		MonsterSlayerState.LoadResult completedResult = MonsterSlayerState.initialize(completed, data, legacyData);
		equals(MonsterSlayerState.LoadResult.Status.MIGRATED, completedResult.getStatus(), "completed migration");
		equals(MonsterSlayerRank.LEGEND, completedResult.getSnapshot().getRank(), "completed rank");
		equals(2, completed.getInt("co_prestige"), "prestige preserved");
		assertZeroBalances(completedResult.getSnapshot());
	}

	private static void legacyVariantsAndFailuresRemainSafe(MonsterSlayerData data,
			CombatOdysseyMigration.LegacyData legacyData) {
		assertLegacyClassification(data, legacyData, "2", null, null,
			CombatOdysseyMigration.Classification.PARTIAL, MonsterSlayerRank.FLEDGLING);
		assertLegacyClassification(data, legacyData, "0:-1:0", 0L, null,
			CombatOdysseyMigration.Classification.PARTIAL, MonsterSlayerRank.FLEDGLING);
		assertLegacyClassification(data, legacyData, "13:0:1", 0L, null,
			CombatOdysseyMigration.Classification.COMPLETED_UNCLAIMED, MonsterSlayerRank.LEGEND);

		Cache malformedLegacy = new Cache();
		malformedLegacy.store("combat_odyssey", "bad:legacy");
		Map<String, Object> before = new LinkedHashMap<String, Object>(malformedLegacy.getCacheMap());
		MonsterSlayerState.LoadResult result = MonsterSlayerState.initialize(malformedLegacy, data, legacyData);
		equals(MonsterSlayerState.LoadResult.Status.QUARANTINED, result.getStatus(), "malformed legacy");
		equals(before, malformedLegacy.getCacheMap(), "malformed legacy preserves evidence");
	}

	private static void assertLegacyClassification(MonsterSlayerData data,
			CombatOdysseyMigration.LegacyData legacyData, String state, Long progress, Integer prestige,
			CombatOdysseyMigration.Classification classification, MonsterSlayerRank rank) {
		Cache cache = new Cache();
		cache.store("combat_odyssey", state);
		if (progress != null) {
			cache.store("co_tier_progress", progress.longValue());
		}
		if (prestige != null) {
			cache.store("co_prestige", prestige.intValue());
		}
		MonsterSlayerState.LoadResult result = MonsterSlayerState.initialize(cache, data, legacyData);
		equals(MonsterSlayerState.LoadResult.Status.MIGRATED, result.getStatus(), "legacy variant status");
		equals(classification, result.getClassification(), "legacy variant class");
		equals(rank, result.getSnapshot().getRank(), "legacy variant rank");
		assertZeroBalances(result.getSnapshot());
	}

	private static void invalidEntitlementsQuarantineWithoutWrites(MonsterSlayerData data,
			CombatOdysseyMigration.LegacyData legacyData) {
		assertQuarantined(data, legacyData, 0x40, "unknown entitlement bit");
		assertQuarantined(data, legacyData, 0x02, "non-prefix entitlement bit");
	}

	private static void assertQuarantined(MonsterSlayerData data,
			CombatOdysseyMigration.LegacyData legacyData, int mask, String label) {
		Cache cache = new Cache();
		cache.store("monster_slayer_inventory_upgrades", mask);
		Map<String, Object> before = new LinkedHashMap<String, Object>(cache.getCacheMap());
		MonsterSlayerState.LoadResult result = MonsterSlayerState.initialize(cache, data, legacyData);
		equals(MonsterSlayerState.LoadResult.Status.QUARANTINED, result.getStatus(), label);
		equals(before, cache.getCacheMap(), label + " preserves evidence");
	}

	private static void malformedStateQuarantinesWithoutWrites(MonsterSlayerData data,
			CombatOdysseyMigration.LegacyData legacyData) {
		Cache cache = new Cache();
		cache.store("monster_slayer_balance_fledgling", "not-a-long");
		Map<String, Object> before = new LinkedHashMap<String, Object>(cache.getCacheMap());
		MonsterSlayerState.LoadResult result = MonsterSlayerState.initialize(cache, data, legacyData);
		equals(MonsterSlayerState.LoadResult.Status.QUARANTINED, result.getStatus(), "malformed balance");
		equals(before, cache.getCacheMap(), "malformed balance preserves evidence");
	}

	private static void derivedCapacityUsesStableExplicitBits() {
		equals(30, MonsterSlayerState.InventoryUpgrade.derivedCapacity(0), "capacity at no entitlements");
		equals(31, MonsterSlayerState.InventoryUpgrade.derivedCapacity(0x01), "Falador capacity");
		equals(40, MonsterSlayerState.InventoryUpgrade.derivedCapacity(0x3f), "full capacity");
	}

	private static void taskAssignmentAndCompletionAreExactOnce(MonsterSlayerData data) {
		Map<String, Integer> cursors = zeroCursors(data);
		MonsterSlayerState.Snapshot fledgling = MonsterSlayerState.create(2, MonsterSlayerRank.FLEDGLING,
			MonsterSlayerBalances.zero(), cursors, null, 0, 0L, 0, 1,
			MonsterSlayerState.LegacyStatus.NONE, 0, data);
		MonsterSlayerState.TaskResult rejected = MonsterSlayerState.assignMandatory(fledgling, data, "port_sarim");
		equals(MonsterSlayerState.TaskResult.Reason.RANK, rejected.getReason(), "wrong contact rank");
		MonsterSlayerState.TaskResult assigned = MonsterSlayerState.assignMandatory(fledgling, data, "falador");
		equals(MonsterSlayerState.TaskResult.Reason.ASSIGNED, assigned.getReason(), "mandatory assignment");
		equals("falador.goblins", assigned.getSnapshot().getActiveTaskKey(), "first deterministic task");
		equals(MonsterSlayerState.TaskResult.Reason.WRONG_NPC,
			MonsterSlayerState.recordEligibleKill(assigned.getSnapshot(), data, 19).getReason(), "wrong family");
		MonsterSlayerState.Snapshot progressing = assigned.getSnapshot();
		for (int kill = 0; kill < 39; kill++) progressing = MonsterSlayerState.recordEligibleKill(progressing, data, 62).getSnapshot();
		equals(39, progressing.getActiveKills(), "bounded progress");
		MonsterSlayerState.TaskResult completion = MonsterSlayerState.recordEligibleKill(progressing, data, 62);
		equals(MonsterSlayerState.TaskResult.Reason.COMPLETED, completion.getReason(), "completion");
		equals(2L, completion.getSnapshot().getBalances().get(MonsterSlayerChallenge.FLEDGLING), "native award only");
		equals(1, completion.getSnapshot().getMandatoryCursors().get("falador"), "cursor advanced once");
		equals(MonsterSlayerState.TaskResult.Reason.NO_ACTIVE_TASK,
			MonsterSlayerState.recordEligibleKill(completion.getSnapshot(), data, 19).getReason(), "duplicate callback rejected");

		Map<String, Integer> repeatableCursors = zeroCursors(data);
		repeatableCursors.put("falador", data.getContact("falador").getMandatoryTasks().size());
		MonsterSlayerState.Snapshot initiate = MonsterSlayerState.create(2, MonsterSlayerRank.INITIATE,
			MonsterSlayerBalances.zero(), repeatableCursors, null, 0, 0L, 0, 1,
			MonsterSlayerState.LegacyStatus.NONE, 0, data);
		equals(MonsterSlayerState.TaskResult.Reason.INVALID_REPEATABLE,
			MonsterSlayerState.assignRepeatable(initiate, data, "falador", "port_sarim.pirates.repeatable").getReason(),
			"cross-contact repeatable rejected");
		equals(MonsterSlayerState.TaskResult.Reason.ASSIGNED,
			MonsterSlayerState.assignRepeatable(initiate, data, "falador", "falador.goblins.repeatable").getReason(),
			"eligible repeatable assignment");
	}

	private static void beerIntroductionIsOneTimeAndRankSafe(MonsterSlayerData data) {
		MonsterSlayerState.Snapshot fresh = MonsterSlayerState.defaults(data);
		MonsterSlayerState.Snapshot pending = MonsterSlayerState.beginIntroduction(fresh, data);
		equals(1, pending.getIntroStage(), "beer introduction begins without rank");
		equals(MonsterSlayerRank.UNSTAMPED, pending.getRank(), "beer introduction rank remains unstamped");
		MonsterSlayerState.Snapshot fledgling = MonsterSlayerState.completeIntroduction(pending, data);
		equals(2, fledgling.getIntroStage(), "beer introduction completes once");
		equals(MonsterSlayerRank.FLEDGLING, fledgling.getRank(), "beer awards Fledgling");
		boolean rejected = false;
		try { MonsterSlayerState.completeIntroduction(fledgling, data); } catch (MonsterSlayerState.ValidationException expected) { rejected = true; }
		assertTrue(rejected, "duplicate beer completion is rejected");
	}

	private static void promotionAcknowledgementIsTypedAndIdempotent(MonsterSlayerData data) {
		Map<String, Integer> cursors = zeroCursors(data);
		cursors.put("falador", data.getContact("falador").getMandatoryTasks().size());
		MonsterSlayerState.Snapshot promoted = MonsterSlayerState.create(2, MonsterSlayerRank.INITIATE,
			MonsterSlayerBalances.zero(), cursors, null, 0, 0L, 0, 1,
			MonsterSlayerState.LegacyStatus.NONE, 0, data);
		MonsterSlayerState.Snapshot acknowledged = MonsterSlayerState.acknowledgePromotion(promoted, data, "falador");
		assertTrue(acknowledged.isPromotionAcknowledged("falador", data), "promotion acknowledgement persisted in typed state");
		equals(acknowledged, MonsterSlayerState.acknowledgePromotion(acknowledged, data, "falador"), "promotion acknowledgement is idempotent");
	}

	private static void typedPromotionPlansAreBoundedAndOrdered() {
		String[][] required = {{"Excellent work! You've done a fine job culling those monsters.", "There seem to be just as many as before."}, {"You did what you said you would. That's worth more than loud talk."}, {"Hah. I knew you had it in you. You're Elite now; take the badge."}, {"Splendid work! You faced the test and did not blink."}, {"You completed the work, even when it was hard. That is the part people remember."}, {"You've completed your journey for now. You've done well.", "And what's my new rank?", "And what use would you make of it?", "...Legend, then?", "If you continue to earn it."}};
		for (int tier = 0; tier < 6; tier++) {
			java.util.List<MonsterSlayerDialoguePlan.Step> plan = MonsterSlayerDialoguePlan.promotion(tier);
			assertTrue(!plan.isEmpty(), "promotion plan exists " + tier);
			for (MonsterSlayerDialoguePlan.Step step : plan) { assertTrue(step.getText().length() <= 255, "promotion line bounded " + tier); assertTrue(step.getSpeaker() != null, "promotion speaker typed " + tier); }
			for (String text : required[tier]) { boolean found = false; for (MonsterSlayerDialoguePlan.Step step : plan) if (text.equals(step.getText())) found = true; assertTrue(found, "promotion exact line " + text); }
		}
		equals(MonsterSlayerDialoguePlan.Speaker.NPC, MonsterSlayerDialoguePlan.promotion(5).get(0).getSpeaker(), "Legend NPC first");
		equals(MonsterSlayerDialoguePlan.Speaker.PLAYER, MonsterSlayerDialoguePlan.promotion(5).get(1).getSpeaker(), "Legend player reply");
	}

	private static void approvedShopDefinitionsHaveStableLaunchShape(MonsterSlayerData data) {
		equals(6, data.getShops().size(), "six Slayer shops");
		long[] capacityPrices = {42L, 75L, 70L, 58L, 135L, 140L};
		for (int index = 0; index < data.getShops().size(); index++) {
			MonsterSlayerDefinitions.Shop shop = data.getShops().get(index);
			equals(capacityPrices[index], shop.getCapacityUpgrade().getCost().get(shop.getChallenge()),
				"capacity price " + shop.getKey());
			equals(1, shop.getCategories().size(), "one approved category " + shop.getKey());
			equals(4, shop.getCategories().get(0).getRewards().size(), "four consumables " + shop.getKey());
			for (MonsterSlayerDefinitions.Reward reward : shop.getCategories().get(0).getRewards()) {
				equals(10, reward.getStock(), "launch stock " + reward.getKey());
				equals(1, reward.getRestockAmount(), "restock amount " + reward.getKey());
				reward.getCost().validateForShop(shop.getChallenge(), true);
			}
		}
	}

	private static void repeatablesAndHazardsUseDeclaredLaunchPolicy(MonsterSlayerData data) {
		java.util.EnumSet<MonsterSlayerHazard> hazards = java.util.EnumSet.noneOf(MonsterSlayerHazard.class);
		for (MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) {
			for (MonsterSlayerDefinitions.Task task : contact.getRepeatableTasks()) {
				equals(1, task.getWeight(), "equal repeatable weight " + task.getKey());
				hazards.addAll(task.getHazards());
			}
			for (MonsterSlayerDefinitions.Task task : contact.getMandatoryTasks()) hazards.addAll(task.getHazards());
		}
		for (MonsterSlayerHazard hazard : MonsterSlayerHazard.values()) assertTrue(hazards.contains(hazard), "declared hazard coverage " + hazard);
	}

	private static void headlessShopPreflightKeepsTypedCostsAndStockBounded(MonsterSlayerData data) {
		MonsterSlayerShopService shops = new MonsterSlayerShopService(data);
		Map<MonsterSlayerChallenge, Long> amounts = new LinkedHashMap<MonsterSlayerChallenge, Long>();
		for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) amounts.put(challenge, 0L);
		amounts.put(MonsterSlayerChallenge.FLEDGLING, 42L);
		MonsterSlayerState.Snapshot player = MonsterSlayerState.create(2, MonsterSlayerRank.FLEDGLING,
			MonsterSlayerBalances.of(amounts), zeroCursors(data), null, 0, 0L, 0, 1,
			MonsterSlayerState.LegacyStatus.NONE, 0, data);
		MonsterSlayerShopService.RedemptionProposal accepted = shops.proposeRedemption(player,
			"falador", "falador.brawn", 2L);
		assertTrue(accepted.isSuccessful(), "Fledgling typed redemption preflight");
		equals(2, accepted.getOutput(), "typed output multiplication");
		assertFalse(shops.proposeRedemption(player, "falador", "falador.brawn", 11L).isSuccessful(),
			"stock rejects oversized quantity");
		assertFalse(shops.proposeRedemption(player, "port_sarim", "port_sarim.brawn", 1L).isSuccessful(),
			"rank gate rejects later shop");
		MonsterSlayerShopService.CapacityProposal capacity = shops.proposeCapacityPurchase(player, "falador");
		assertTrue(capacity.isSuccessful(), "first capacity entitlement proposal");
		equals(31, capacity.getSnapshot().getDerivedInventoryCapacity(), "proposal adds only Falador entitlement");
		assertFalse(shops.proposeCapacityPurchase(capacity.getSnapshot(), "falador").isSuccessful(),
			"duplicate capacity entitlement is rejected");
		assertFalse(shops.proposeCapacityPurchase(capacity.getSnapshot(), "port_sarim").isSuccessful(),
			"rank gate remains authoritative for later entitlement");
		shops.restock();
		equals(10, shops.getStock("falador.brawn"), "restock keeps initial maximum");
	}

	private static void cacheWritesRestoreOnlyOwnedKeysAfterRuntimeFailure(MonsterSlayerData data) {
		FailingCache cache = new FailingCache();
		cache.store("unrelated_key", "must-survive");
		MonsterSlayerState.write(cache, data, MonsterSlayerState.defaults(data));
		Map<String, Object> before = new LinkedHashMap<String, Object>(cache.getCacheMap());
		cache.failAfterWrites(5);
		boolean failed = false;
		try {
			MonsterSlayerState.write(cache, data, MonsterSlayerState.defaults(data));
		} catch (IllegalStateException expected) {
			failed = true;
		}
		assertTrue(failed, "injected cache write failure propagates to optional caller");
		equals(before, cache.getCacheMap(), "failed write restores exact owned cache snapshot");
		equals("must-survive", cache.getString("unrelated_key"), "failed write preserves unrelated key");
	}

	private static void failureDiagnosticsAreDuplicateSuppressedAndBounded() {
		MonsterSlayerFailureDiagnostics.resetForTests();
		java.util.UUID first = new java.util.UUID(0L, 1L);
		assertTrue(MonsterSlayerFailureDiagnostics.shouldLog(first, 19, "invalid-state"),
			"first diagnostic is retained");
		assertFalse(MonsterSlayerFailureDiagnostics.shouldLog(first, 19, "invalid-state"),
			"duplicate diagnostic is suppressed");
		for (int index = 0; index < 300; index++) {
			MonsterSlayerFailureDiagnostics.shouldLog(new java.util.UUID(1L, index), 19, "runtime-failure");
		}
		assertTrue(MonsterSlayerFailureDiagnostics.retainedEntryCount() <= 256,
			"diagnostic retention remains bounded");
	}

	private static Map<String, Integer> zeroCursors(MonsterSlayerData data) {
		Map<String, Integer> result = new LinkedHashMap<String, Integer>();
		for (MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) result.put(contact.getKey(), 0);
		return result;
	}

	private static void assertZeroBalances(MonsterSlayerState.Snapshot snapshot) {
		for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) {
			equals(0L, snapshot.getBalances().get(challenge), "zero migrated balance " + challenge);
		}
		equals(0, snapshot.getInventoryUpgrades(), "zero migrated entitlements");
	}

	private static MonsterSlayerData.ReferenceCatalog acceptingCatalog() {
		return new MonsterSlayerData.ReferenceCatalog() {
			public boolean npcExists(int npcId) { return true; }
			public boolean npcAttackable(int npcId) { return true; }
			public boolean npcSpawned(int npcId) { return true; }
			public boolean itemExists(int itemId) { return true; }
		};
	}

	private static void equals(Object expected, Object actual, String label) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(label + ": expected " + expected + " but was " + actual);
		}
	}

	private static void assertTrue(boolean value, String label) {
		if (!value) throw new AssertionError(label);
	}

	private static void assertFalse(boolean value, String label) {
		assertTrue(!value, label);
	}

	private static final class FailingCache extends Cache {
		private int failAfter = Integer.MAX_VALUE;
		private int writes;

		private void failAfterWrites(int count) {
			writes = 0;
			failAfter = count;
		}

		private void beforeWrite() {
			if (++writes == failAfter) throw new IllegalStateException("injected cache failure");
		}

		@Override public void set(String key, int value) { beforeWrite(); super.set(key, value); }
		@Override public void store(String key, int value) { beforeWrite(); super.store(key, value); }
		@Override public void store(String key, long value) { beforeWrite(); super.store(key, value); }
		@Override public void store(String key, String value) { beforeWrite(); super.store(key, value); }
		@Override public void remove(String key) { beforeWrite(); super.remove(key); }
	}
}
