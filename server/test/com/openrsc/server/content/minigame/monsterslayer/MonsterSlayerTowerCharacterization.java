package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.model.Cache;
import java.nio.file.Paths;
import java.util.*;
import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.*;

/** Rollout and migration tests deliberately use both rosters, not relabelled new fixtures. */
public final class MonsterSlayerTowerCharacterization {
	private static final String VERSION = "monster_slayer_state_version";
	private static final String COMPLETED = "monster_slayer_done_parts";
	private static final String[] NEW_KEYS = {"falador.giant_frog", "port_sarim.cockatrice",
		"brimhaven.banshee", "brimhaven.naga", "champions.terror_dog", "champions.bloodveld",
		"heroes.dark_beast", "legends.abyssal_demon"};
	private static final long[] PAYOUTS = {5, 8, 12, 12, 18, 20, 28, 35};
	private static final int[] KILLS = {25, 25, 25, 25, 20, 20, 15, 15};
	private static final long[] PRICES = {84, 148, 138, 110, 268, 282};
	private static final long[] TOTALS = {43, 75, 86, 88, 149, 163};
	private static final int[] POOLS = {3, 3, 4, 4, 3, 3};
	private static final CombatOdysseyMigration.LegacyData ODYSSEY =
		CombatOdysseyMigration.LegacyData.load(Paths.get("conf/server/defs/extras/CombatOdyssey.json"));

	public static void run() {
		MonsterSlayerData old = load(false, false);
		MonsterSlayerData expanded = load(true, true);
		eq(1, old.getRosterVersion(), "disabled version");
		eq(2, expanded.getRosterVersion(), "expanded version");
		for (String key : NEW_KEYS) eq(null, old.getTask(key), "disabled task absent");
		try { load(true, false); throw new AssertionError("Missing tower spawns accepted"); }
		catch (IllegalArgumentException expected) {
			check(expected.getMessage().contains("no active spawn"), "real spawn gate retained");
		}
		definitions(old, expanded);
		fixedPriceValidation();
		newTaskCredits(expanded);
		// Every valid legacy boundary, with and without a partly completed active task.
		for (int tier = 0; tier < 6; tier++) {
			Contact contact = old.getContactsInChallengeOrder().get(tier);
			for (int cursor = 0; cursor < contact.getMandatoryTasks().size(); cursor++) {
				migratePartial(old, expanded, tier, cursor, false);
				migratePartial(old, expanded, tier, cursor, true);
			}
		}
		repeatablesAndCompletedTiers(old, expanded);
		malformedAndAtomic(old, expanded);
		newAccountsAndOdyssey(expanded);
		System.out.println("Monster Slayer tower rollout/migration: PASS");
	}

	private static MonsterSlayerData load(boolean enabled, final boolean spawned) {
		return MonsterSlayerData.loadHistorical(Paths.get("conf/server/defs/extras/MonsterSlayer.json"),
			new MonsterSlayerData.ReferenceCatalog() {
				public boolean npcExists(int id) { return true; }
				public boolean npcAttackable(int id) { return true; }
				public boolean npcSpawned(int id) { return spawned || id < 863 || id > 870; }
				public boolean itemExists(int id) { return true; }
			}, enabled);
	}

	private static void fixedPriceValidation() {
		try {
			String json = new String(java.nio.file.Files.readAllBytes(Paths.get(
				"conf/server/defs/extras/MonsterSlayer.json")), java.nio.charset.StandardCharsets.UTF_8);
			MonsterSlayerData.ReferenceCatalog catalog = new MonsterSlayerData.ReferenceCatalog() {
				public boolean npcExists(int id) { return true; }
				public boolean npcAttackable(int id) { return true; }
				public boolean npcSpawned(int id) { return true; }
				public boolean itemExists(int id) { return true; }
			};
			org.json.JSONObject changedPayout = new org.json.JSONObject(json);
			changedPayout.getJSONArray("contacts").getJSONObject(0).getJSONArray("mandatoryTasks")
				.getJSONObject(0).put("pointReward", 200);
			MonsterSlayerData.parse(changedPayout, catalog); // task totals no longer set prices
			for (int i = 0; i < 6; i++) {
				org.json.JSONObject badPrice = new org.json.JSONObject(json);
				org.json.JSONObject shop = badPrice.getJSONArray("shops").getJSONObject(i);
				shop.getJSONObject("capacityUpgrade").getJSONObject("cost").put(shop.getString("challenge"), PRICES[i] + 1);
				try { MonsterSlayerData.parse(badPrice, catalog); throw new AssertionError("Bad fixed price accepted"); }
				catch (IllegalArgumentException expected) { check(expected.getMessage().contains("capacity upgrade cost"), "strict price error"); }
			}
		} catch (java.io.IOException failure) { throw new AssertionError(failure); }
	}

	private static void newTaskCredits(MonsterSlayerData data) {
		for (int i = 0; i < NEW_KEYS.length; i++) {
			String key = NEW_KEYS[i];
			Contact contact = data.getContact(key.substring(0, key.indexOf('.')));
			int tier = contact.getChallenge().getCode();
			for (boolean repeatable : new boolean[] {false, true}) {
				Task task = data.getTask(key + (repeatable ? ".repeatable" : ""));
				MonsterSlayerState.Snapshot state = seed(data, repeatable ? tier + 1 : tier,
					repeatable ? 0 : contact.getMandatoryTasks().indexOf(task), task.getKey(), 0);
				eq(MonsterSlayerState.TaskResult.Reason.WRONG_NPC,
					MonsterSlayerState.recordEligibleKill(state, data, 19).getReason(), "wrong family rejected");
				for (int kill = 1; kill <= KILLS[i]; kill++) {
					state = MonsterSlayerState.recordEligibleKill(state, data, 863 + i).getSnapshot();
					eq(321L + (kill == KILLS[i] ? PAYOUTS[i] : 0L), state.getBalances().get(contact.getChallenge()),
						"exact completion-only payout");
				}
				eq(501L, state.getTasksCompleted(), "one completion");
				eq(MonsterSlayerState.TaskResult.Reason.NO_ACTIVE_TASK,
					MonsterSlayerState.recordEligibleKill(state, data, 863 + i).getReason(), "cannot duplicate completion");
			}
		}
	}

	private static void definitions(MonsterSlayerData old, MonsterSlayerData data) {
		int tasks = 0, kills = 0, index = 0;
		Set<MonsterSlayerHazard> hazards = EnumSet.noneOf(MonsterSlayerHazard.class);
		for (Contact contact : data.getContactsInChallengeOrder()) {
			List<String> surviving = new ArrayList<String>(), original = new ArrayList<String>();
			long points = 0;
			for (Task task : contact.getMandatoryTasks()) {
				tasks++; kills += task.getRequiredKills(); points += task.getPointReward();
				hazards.addAll(task.getHazards());
				if (old.getTask(task.getKey()) != null) {
					surviving.add(task.getKey());
					Task before = old.getTask(task.getKey());
					eq(before.getRequiredKills(), task.getRequiredKills(), "old kills preserved");
					eq(before.getPointReward(), task.getPointReward(), "old payout preserved");
					eq(before.getFamilyKey(), task.getFamilyKey(), "old family preserved");
					eq(before.getHazards(), task.getHazards(), "old warnings preserved");
				}
			}
			for (Task task : old.getContact(contact.getKey()).getMandatoryTasks()) original.add(task.getKey());
			eq(original, surviving, "old relative order");
			eq(TOTALS[index], points, "tier payout total");
			eq(POOLS[index], contact.getRepeatableTasks().size(), "repeatable pool");
			for (Task task : contact.getRepeatableTasks()) eq(1, task.getWeight(), "equal weight");
			eq(PRICES[index], MonsterSlayerData.mandatoryCapacityUpgradeCost(
				contact.getChallenge(), Collections.singletonMap(contact.getKey(), contact)), "fixed backpack price");
			index++;
		}
		eq(43, tasks, "task total"); eq(1283, kills, "kill total");
		for (int i = 0; i < NEW_KEYS.length; i++) for (String suffix : new String[] {"", ".repeatable"}) {
			Task task = data.getTask(NEW_KEYS[i] + suffix);
			eq(KILLS[i], task.getRequiredKills(), "approved kills");
			eq(PAYOUTS[i], task.getPointReward(), "approved payout");
			eq(Collections.singletonList(863 + i), data.getFamily(task.getFamilyKey()).getNpcIds(), "NPC identity");
			check(task.getHazards().get(0).getPreparationLines().length > 0, "typed preparation");
		}
		for (MonsterSlayerHazard hazard : MonsterSlayerHazard.values())
			if (hazard != MonsterSlayerHazard.BALROG && hazard != MonsterSlayerHazard.ELDER_DRAGON)
				check(hazards.contains(hazard), "historical hazard coverage");
		List<Task> hero = data.getContact("legends").getMandatoryTasks();
		eq("legends.king_black_dragon", hero.get(hero.size() - 1).getKey(), "KBD capstone");
	}

	private static MonsterSlayerState.Snapshot seed(MonsterSlayerData old, int tier, int cursor,
			String active, int kills) {
		Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
		int index = 0;
		for (Contact contact : old.getContactsInChallengeOrder()) {
			cursors.put(contact.getKey(), index < tier ? contact.getMandatoryTasks().size() : index == tier ? cursor : 0);
			index++;
		}
		MonsterSlayerBalances balances = MonsterSlayerBalances.zero();
		for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) balances = balances.credit(challenge, 321);
		return MonsterSlayerState.create(2, MonsterSlayerRank.fromCode(tier + 1), balances,
			cursors, active, kills, 500L, 3, 1, MonsterSlayerState.LegacyStatus.NONE, 0, old);
	}

	private static void migratePartial(MonsterSlayerData old, MonsterSlayerData expanded,
			int tier, int cursor, boolean active) {
		Contact before = old.getContactsInChallengeOrder().get(tier);
		Task originalActive = before.getMandatoryTasks().get(cursor);
		int kills = active ? Math.min(3, originalActive.getRequiredKills() - 1) : 0;
		MonsterSlayerState.Snapshot prior = seed(old, tier, cursor, active ? originalActive.getKey() : null, kills);
		Cache cache = new Cache();
		MonsterSlayerState.write(cache, old, prior);
		cache.store("unrelated", "keep");
		Map<String, Object> original = new LinkedHashMap<String, Object>(cache.getCacheMap());
		MonsterSlayerState.read(cache, expanded);
		eq(original, cache.getCacheMap(), "read is pure");
		MonsterSlayerState.LoadResult loaded = MonsterSlayerState.initialize(cache, expanded, ODYSSEY);
		eq(MonsterSlayerState.LoadResult.Status.MIGRATED, loaded.getStatus(), "migration");
		MonsterSlayerState.Snapshot state = loaded.getSnapshot();
		eq(prior.getRank(), state.getRank(), "rank preserved");
		eq(prior.getBalances().asMap(), state.getBalances().asMap(), "balances preserved");
		eq(prior.getTasksCompleted(), state.getTasksCompleted(), "no waiver payouts");
		eq(prior.getActiveTaskKey(), state.getActiveTaskKey(), "active identity preserved");
		eq(kills, state.getActiveKills(), "partial kills preserved");
		eq(3, state.getInventoryUpgrades(), "entitlements preserved");
		eq("keep", cache.getString("unrelated"), "unrelated evidence preserved");
		Map<String, Object> migrated = new LinkedHashMap<String, Object>(cache.getCacheMap());
		eq(MonsterSlayerState.LoadResult.Status.LOADED,
			MonsterSlayerState.initialize(cache, expanded, ODYSSEY).getStatus(), "idempotent");
		eq(migrated, cache.getCacheMap(), "no reconnect writes");

		Contact current = expanded.getContact(before.getKey());
		Set<String> expected = new LinkedHashSet<String>();
		long reward = 0;
		for (Task task : current.getMandatoryTasks()) {
			if (!state.getCompletedMandatoryTasks().contains(task.getKey())) {
				expected.add(task.getKey()); reward += task.getPointReward();
			}
		}
		Set<String> performed = new LinkedHashSet<String>();
		while (state.getRank() == current.getRequiredRank()) {
			if (state.getActiveTaskKey() == null) state = MonsterSlayerState.assignMandatory(state, expanded, current.getKey()).getSnapshot();
			check(performed.add(state.getActiveTaskKey()), "no old task replay");
			state = MonsterSlayerState.completeActiveTaskForDevelopment(state, expanded).getSnapshot();
			// Persist/reload between tasks, including the non-prefix migration interval.
			MonsterSlayerState.write(cache, expanded, state);
			state = MonsterSlayerState.read(cache, expanded);
		}
		eq(expected, performed, "all unfinished and inserted tasks required");
		eq(321L + reward, state.getBalances().get(current.getChallenge()), "only actual tasks rewarded");
		eq(500L + performed.size(), state.getTasksCompleted(), "only actual completions counted");
		check(MonsterSlayerState.acknowledgePromotion(state, expanded, current.getKey())
			.isPromotionAcknowledged(current.getKey(), expanded), "pending ceremony preserved");
	}

	private static void repeatablesAndCompletedTiers(MonsterSlayerData old, MonsterSlayerData expanded) {
		for (int tier = 1; tier <= 6; tier++) {
			Contact completed = old.getContactsInChallengeOrder().get(tier - 1);
			for (Task repeatable : completed.getRepeatableTasks()) {
				Cache cache = new Cache();
				MonsterSlayerState.Snapshot prior = seed(old, tier, 0, repeatable.getKey(), 3);
				prior = MonsterSlayerState.acknowledgePromotion(prior, old, completed.getKey());
				MonsterSlayerState.write(cache, old, prior);
				MonsterSlayerState.Snapshot after = MonsterSlayerState.initialize(cache, expanded, ODYSSEY).getSnapshot();
				for (Map.Entry<String, Object> entry : cache.getCacheMap().entrySet()) {
					if (!entry.getKey().startsWith("monster_slayer_done_")) continue;
					check(entry.getKey().length() <= 32, "completion cache key fits MySQL");
					check(entry.getValue().toString().length() <= 150, "completion cache value fits MySQL");
				}
				eq(repeatable.getKey(), after.getActiveTaskKey(), "repeatable retained");
				eq(3, after.getActiveKills(), "repeatable kills retained");
				check(after.isPromotionAcknowledged(completed.getKey(), expanded), "ack retained");
				eq(tier == 6, after.isComplete(expanded), "fully complete grandfathering");
				eq(500L, after.getTasksCompleted(), "waivers not completions");
			}
		}
	}

	private static void malformedAndAtomic(MonsterSlayerData old, MonsterSlayerData expanded) {
		for (String key : new String[] {VERSION, "monster_slayer_mandatory_falador", "monster_slayer_rank",
				"monster_slayer_active_task", COMPLETED}) {
			Cache cache = new Cache();
			MonsterSlayerState.write(cache, old, seed(old, 0, 0, null, 0));
			if (key.equals(VERSION)) cache.set(key, 99);
			else if (key.equals("monster_slayer_rank")) cache.set(key, 7);
			else if (key.equals("monster_slayer_mandatory_falador")) cache.set(key, 99);
			else cache.store(key, "falador.giant_frog");
			Map<String, Object> before = new LinkedHashMap<String, Object>(cache.getCacheMap());
			eq(MonsterSlayerState.LoadResult.Status.QUARANTINED,
				MonsterSlayerState.initialize(cache, expanded, ODYSSEY).getStatus(), "bad old snapshot rejected");
			eq(before, cache.getCacheMap(), "corrupt evidence untouched");
		}
		Cache cache = new Cache();
		MonsterSlayerState.write(cache, old, seed(old, 0, 0, null, 0));
		MonsterSlayerState.initialize(cache, expanded, ODYSSEY);
		Map<String, Object> good = new LinkedHashMap<String, Object>(cache.getCacheMap());
		eq(MonsterSlayerState.LoadResult.Status.QUARANTINED,
			MonsterSlayerState.initialize(cache, old, ODYSSEY).getStatus(), "no silent downgrade after enabling");
		eq(good, cache.getCacheMap(), "downgrade preserves state");
		for (String bad : new String[] {"unknown", "falador.giant_frog", "falador.goblins,falador.goblins"}) {
			cache.set(COMPLETED, 1);
			cache.store("monster_slayer_done_0", bad);
			eq(MonsterSlayerState.LoadResult.Status.QUARANTINED,
				MonsterSlayerState.initialize(cache, expanded, ODYSSEY).getStatus(), "bad identities rejected");
		}
		// Fail every write position to exercise rollback of the entire roster migration.
		for (int failAt = 1; failAt <= 45; failAt++) {
			FailingCache failing = new FailingCache();
			MonsterSlayerState.write(failing, old, seed(old, 2, 2, "brimhaven.moss_giants", 4));
			failing.store("unrelated", "keep");
			Map<String, Object> before = new LinkedHashMap<String, Object>(failing.getCacheMap());
			failing.remaining = failAt;
			try { MonsterSlayerState.initialize(failing, expanded, ODYSSEY); }
			catch (IllegalStateException expected) { eq(before, failing.getCacheMap(), "atomic migration rollback"); }
		}
	}

	private static void newAccountsAndOdyssey(MonsterSlayerData expanded) {
		Cache fresh = new Cache();
		MonsterSlayerState.Snapshot state = MonsterSlayerState.initialize(fresh, expanded, ODYSSEY).getSnapshot();
		eq(MonsterSlayerRank.UNSTAMPED, state.getRank(), "fresh account");
		eq(2, fresh.getInt(VERSION), "fresh version");
		Cache legacy = new Cache(); legacy.store("co_prestige", 2);
		state = MonsterSlayerState.initialize(legacy, expanded, ODYSSEY).getSnapshot();
		check(state.isComplete(expanded), "Odyssey completed recognition remains independent");
		eq(0L, state.getTasksCompleted(), "Odyssey waivers no payout");
	}

	private static class FailingCache extends Cache {
		int remaining = Integer.MAX_VALUE;
		private void touch() { if (--remaining == 0) throw new IllegalStateException("injected"); }
		@Override public void set(String k, int v) { touch(); super.set(k, v); }
		@Override public void store(String k, String v) { touch(); super.store(k, v); }
		@Override public void store(String k, long v) { touch(); super.store(k, v); }
		@Override public void remove(String k) { touch(); super.remove(k); }
	}
	private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
	private static void eq(Object expected, Object actual, String label) {
		if (!Objects.equals(expected, actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
	}
}
