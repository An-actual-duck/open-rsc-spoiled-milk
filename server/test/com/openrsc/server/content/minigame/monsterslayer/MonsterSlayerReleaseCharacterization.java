package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.model.Cache;
import java.nio.file.Paths;
import java.util.*;
import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.*;

/** Synthetic saved caches, including every historical task boundary; no accounts or JDBC. */
public final class MonsterSlayerReleaseCharacterization {
    private static int checks, migrations;
    private static final MonsterSlayerData.ReferenceCatalog CATALOG = new MonsterSlayerData.ReferenceCatalog() {
        public boolean npcExists(int id) { return true; }
        public boolean npcAttackable(int id) { return true; }
        public boolean npcSpawned(int id) { return true; }
        public boolean itemExists(int id) { return true; }
    };
    private static final CombatOdysseyMigration.LegacyData LEGACY = CombatOdysseyMigration.LegacyData.load(
        Paths.get("conf/server/defs/extras/CombatOdyssey.json"));

    public static void main(String[] args) { run(); }

    public static void run() {
        MonsterSlayerData current = MonsterSlayerData.load(Paths.get("conf/server/defs/extras/MonsterSlayer.json"), CATALOG, true);
        check(current.getRosterVersion() == 5, "release is a new roster version");
        MonsterSlayerData disabled = MonsterSlayerData.load(Paths.get("conf/server/defs/extras/MonsterSlayer.json"), CATALOG, false);
        check(disabled.getRosterVersion() == 3 && disabled.getTask("champions.death_wings") == null,
            "disabled profile retains v3 and excludes release additions");
        for (int version = 1; version <= 4; version++) {
            MonsterSlayerData old = current.historicalRoster(version);
            check(old != null && old.getRosterVersion() == version, "historical roster identity " + version);
            check(old.getTask("champions.death_wings") == null, "historical task lists unchanged " + version);
            check((old.getTask("falador.giant_frog") != null) == (version == 2 || version == 4), "historical rollout identity");
            check((old.getTask("falador.bears") != null) == (version <= 2), "historical retirement identity");
            int tier = 0;
            for (Contact contact : old.getContactsInChallengeOrder()) {
                for (int cursor = 0; cursor < contact.getMandatoryTasks().size(); cursor++) {
                    Task task = contact.getMandatoryTasks().get(cursor);
                    verify(old, current, seed(old, tier, cursor, null, 0));
                    verify(old, current, seed(old, tier, cursor, task.getKey(), task.getRequiredKills() - 1));
                }
                for (Task task : contact.getRepeatableTasks()) {
                    verify(old, current, seed(old, tier + 1, 0, task.getKey(), task.getRequiredKills() - 1));
                }
                tier++;
            }
            verify(old, current, seed(old, 6, 0, null, 0));
            Cache unstamped = new Cache();
            MonsterSlayerState.initialize(unstamped, old, LEGACY);
            verify(old, current, MonsterSlayerState.read(unstamped, old));
        }
        deathWingBoundary(current);
        rollbackFailsClosed(current, disabled);
        System.out.println("Monster Slayer release migration: PASS " + checks + " assertions, " + migrations
            + " synthetic v1-v4 migrations; ranks, pending identities, balances, retired tasks, relog and rollback");
    }

    private static MonsterSlayerState.Snapshot seed(MonsterSlayerData data, int tier, int cursor, String active, int kills) {
        Map<String,Integer> counts = new LinkedHashMap<String,Integer>();
        int index = 0;
        for (Contact contact : data.getContactsInChallengeOrder()) {
            counts.put(contact.getKey(), index < tier ? contact.getMandatoryTasks().size() : index == tier ? cursor : 0);
            index++;
        }
        MonsterSlayerBalances balances = MonsterSlayerBalances.zero();
        for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) balances = balances.credit(challenge, 321);
        return MonsterSlayerState.create(2, MonsterSlayerRank.fromCode(tier + 1), balances, counts, active, kills,
            500, 3, 1, MonsterSlayerState.LegacyStatus.COMPLETED_CLAIMED, 2, data);
    }

    private static void verify(MonsterSlayerData old, MonsterSlayerData current, MonsterSlayerState.Snapshot before) {
        migrations++;
        Cache cache = new Cache();
        MonsterSlayerState.write(cache, old, before);
        cache.store("unrelated", "keep");
        Map<String,Object> original = new LinkedHashMap<String,Object>(cache.getCacheMap());
        MonsterSlayerState.Snapshot after = MonsterSlayerState.read(cache, current);
        check(original.equals(cache.getCacheMap()), "read-only migration proposal");
        check(after.getRank().isAtLeast(before.getRank()), "earned rank never relocked");
        check(after.getTasksCompleted() == before.getTasksCompleted(), "no migration completion reward");
        check(after.getInventoryUpgrades() == before.getInventoryUpgrades(), "inventory entitlement preserved");
        check(after.getMigrationVersion() == before.getMigrationVersion() && after.getLegacyStatus() == before.getLegacyStatus()
            && after.getLegacyPrestige() == before.getLegacyPrestige(), "legacy status preserved");
        for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) {
            check(after.getBalances().get(challenge) == before.getBalances().get(challenge), "no migration currency");
        }
        Set<String> expected = new LinkedHashSet<String>();
        for (Contact contact : current.getContactsInChallengeOrder()) {
            for (Task task : contact.getMandatoryTasks()) {
                if (before.getRank().isAtLeast(contact.getAwardedRank()) || before.getCompletedMandatoryTasks().contains(task.getKey()))
                    expected.add(task.getKey());
            }
            check(before.isPromotionAcknowledged(contact.getKey(), old) == after.isPromotionAcknowledged(contact.getKey(), current),
                "promotion acknowledgement unchanged");
            Task pending = null;
            for (Task task : contact.getMandatoryTasks()) if (!expected.contains(task.getKey())) { pending = task; break; }
            check(after.nextMandatoryTask(contact) == pending, "deterministic first unfinished task");
        }
        check(after.getCompletedMandatoryTasks().equals(expected), "stable completed identities plus earned-rank grandfathering only");
        boolean retired = before.getActiveTaskKey() != null && current.getTask(before.getActiveTaskKey()) == null;
        check(Objects.equals(after.getActiveTaskKey(), retired ? null : before.getActiveTaskKey()), "active task retained or retired");
        check(after.getActiveKills() == (retired ? 0 : before.getActiveKills()), "partial kill credit retained or retired");
        check(MonsterSlayerState.initialize(cache, current, LEGACY).getStatus() == MonsterSlayerState.LoadResult.Status.MIGRATED,
            "production initialization persists upgrade");
        Map<String,Object> persisted = new LinkedHashMap<String,Object>(cache.getCacheMap());
        check(MonsterSlayerState.initialize(cache, current, LEGACY).getStatus() == MonsterSlayerState.LoadResult.Status.LOADED,
            "relogin loads without remigration");
        check(persisted.equals(cache.getCacheMap()) && "keep".equals(cache.getString("unrelated")), "relogin is idempotent");
        MonsterSlayerState.Snapshot reloaded = MonsterSlayerState.read(cache, current);
        if (reloaded.getActiveTaskKey() != null) {
            Task active = current.getTask(reloaded.getActiveTaskKey());
            long points = reloaded.getBalances().get(owner(current, active).getChallenge());
            MonsterSlayerState.TaskResult done = MonsterSlayerState.completeActiveTaskForDevelopment(reloaded, current);
            check(done.getReason() == MonsterSlayerState.TaskResult.Reason.COMPLETED, "migrated pending task can finish");
            check(done.getSnapshot().getTasksCompleted() == before.getTasksCompleted() + 1, "one lifetime completion");
            check(done.getSnapshot().getBalances().get(owner(current, active).getChallenge()) == points + active.getPointReward(), "one payout");
            int npc = current.getFamily(active.getFamilyKey()).getNpcIds().get(0);
            check(!MonsterSlayerState.recordEligibleKill(done.getSnapshot(), current, npc).isAccepted(), "no duplicate payout");
        }
    }

    private static Contact owner(MonsterSlayerData data, Task task) {
        for (Contact contact : data.getContacts()) if (contact.getMandatoryTasks().contains(task) || contact.getRepeatableTasks().contains(task)) return contact;
        throw new AssertionError("unowned task " + task.getKey());
    }

    private static void deathWingBoundary(MonsterSlayerData current) {
        MonsterSlayerData old = current.historicalRoster(4);
        Contact contact = old.getContact("champions");
        Task last = old.getTask("champions.greater_demons");
        Cache cache = new Cache();
        MonsterSlayerState.write(cache, old, seed(old, 3, contact.getMandatoryTasks().indexOf(last), last.getKey(), 19));
        MonsterSlayerState.Snapshot migrated = MonsterSlayerState.read(cache, current);
        check(last.getKey().equals(migrated.getActiveTaskKey()), "in-flight task is not replaced by new insertion");
        MonsterSlayerState.Snapshot done = MonsterSlayerState.recordEligibleKill(migrated, current, 184).getSnapshot();
        check(done.getRank() == MonsterSlayerRank.ELITE, "unfinished Death wings prevents only new promotion");
        check("champions.death_wings".equals(done.nextMandatoryTask(current.getContact("champions")).getKey()), "new task is next after in-flight completion");
        MonsterSlayerState.Snapshot assigned = MonsterSlayerState.assignMandatory(done, current, "champions").getSnapshot();
        check(MonsterSlayerState.recordEligibleKill(assigned, current, 43).getReason() == MonsterSlayerState.TaskResult.Reason.WRONG_NPC,
            "giant bats do not count as Death wings");
        done = MonsterSlayerState.completeActiveTaskForDevelopment(assigned, current).getSnapshot();
        check(done.getRank() == MonsterSlayerRank.CHAMPION, "Death wings completes tier4 normally");
        Task death = current.getTask("champions.death_wings");
        check(death.getRequiredKills() == 20 && death.getPointReward() == 18, "modest adjacent tier4 balance");
        check(current.getTask("champions.death_wings.repeatable").getWeight() == 1, "ordinary repeatable inclusion");
    }

    private static void rollbackFailsClosed(MonsterSlayerData current, MonsterSlayerData disabled) {
        Cache cache = new Cache();
        MonsterSlayerState.write(cache, current, seed(current, 6, 0, null, 0));
        Map<String,Object> original = new LinkedHashMap<String,Object>(cache.getCacheMap());
        check(MonsterSlayerState.initialize(cache, disabled, LEGACY).getStatus() == MonsterSlayerState.LoadResult.Status.QUARANTINED,
            "disabling an already enabled release roster fails closed");
        check(original.equals(cache.getCacheMap()), "rollback leaves evidence unchanged");
        cache.store("monster_slayer_active_task", "unknown.task");
        original = new LinkedHashMap<String,Object>(cache.getCacheMap());
        check(MonsterSlayerState.initialize(cache, current, LEGACY).getStatus() == MonsterSlayerState.LoadResult.Status.QUARANTINED,
            "invalid active task not silently dropped");
        check(original.equals(cache.getCacheMap()), "invalid state retained");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
