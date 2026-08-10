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
}
