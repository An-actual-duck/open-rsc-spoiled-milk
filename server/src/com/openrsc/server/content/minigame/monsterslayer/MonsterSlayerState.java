package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.model.Cache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Contact;
import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Family;
import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task;

/** Sole owner of Monster Slayer cache keys and validation of their typed snapshot. */
public final class MonsterSlayerState {
	public static final int STATE_VERSION = 1;
	public static final int MIGRATION_VERSION = 1;

	private static final String STATE_VERSION_KEY = "monster_slayer_state_version";
	private static final String INTRO_STAGE_KEY = "monster_slayer_intro_stage";
	private static final String RANK_KEY = "monster_slayer_rank";
	private static final String BALANCE_PREFIX = "monster_slayer_balance_";
	private static final String ACTIVE_TASK_KEY = "monster_slayer_active_task";
	private static final String ACTIVE_KILLS_KEY = "monster_slayer_active_kills";
	private static final String MANDATORY_PREFIX = "monster_slayer_mandatory_";
	private static final String TASKS_COMPLETED_KEY = "monster_slayer_tasks_completed";
	private static final String INVENTORY_UPGRADES_KEY = "monster_slayer_inventory_upgrades";
	private static final String MIGRATION_VERSION_KEY = "monster_slayer_migration_version";
	private static final String LEGACY_STATUS_KEY = "monster_slayer_legacy_status";
	private static final String LEGACY_PRESTIGE_KEY = "monster_slayer_legacy_prestige";

	private MonsterSlayerState() {
	}

	public static Snapshot read(Cache cache, MonsterSlayerData data) {
		if (cache == null) {
			throw new IllegalArgumentException("Player cache is required");
		}
		Map<String, Object> values = cache.getCacheMap();
		Map<MonsterSlayerChallenge, Long> balances =
			new LinkedHashMap<MonsterSlayerChallenge, Long>();
		for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) {
			balances.put(challenge, readLong(values, balanceKey(challenge), 0L));
		}
		Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
		for (Contact contact : data.getContactsInChallengeOrder()) {
			cursors.put(contact.getKey(), readInteger(values, cursorKey(contact.getKey()), 0));
		}
		String activeTask = readString(values, ACTIVE_TASK_KEY, null);
		Snapshot snapshot = new Snapshot(
			readInteger(values, STATE_VERSION_KEY, STATE_VERSION),
			readInteger(values, INTRO_STAGE_KEY, 0),
			MonsterSlayerRank.fromCode(readInteger(values, RANK_KEY, MonsterSlayerRank.UNSTAMPED.getCode())),
			MonsterSlayerBalances.of(balances),
			cursors,
			activeTask,
			readInteger(values, ACTIVE_KILLS_KEY, 0),
			readLong(values, TASKS_COMPLETED_KEY, 0L),
			readInteger(values, INVENTORY_UPGRADES_KEY, 0),
			readInteger(values, MIGRATION_VERSION_KEY, 0),
			LegacyStatus.fromCode(readInteger(values, LEGACY_STATUS_KEY, LegacyStatus.NONE.getCode())),
			readInteger(values, LEGACY_PRESTIGE_KEY, 0)
		);
		validate(snapshot, data);
		return snapshot;
	}

	/** Validates the full snapshot before changing any cache entry. */
	public static void write(Cache cache, MonsterSlayerData data, Snapshot snapshot) {
		if (cache == null) {
			throw new IllegalArgumentException("Player cache is required");
		}
		validate(snapshot, data);
		cache.set(STATE_VERSION_KEY, snapshot.stateVersion);
		cache.set(INTRO_STAGE_KEY, snapshot.introStage);
		cache.set(RANK_KEY, snapshot.rank.getCode());
		for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) {
			cache.store(balanceKey(challenge), snapshot.balances.get(challenge));
		}
		if (snapshot.activeTaskKey == null) {
			cache.remove(ACTIVE_TASK_KEY);
		} else {
			cache.store(ACTIVE_TASK_KEY, snapshot.activeTaskKey);
		}
		cache.set(ACTIVE_KILLS_KEY, snapshot.activeKills);
		for (Contact contact : data.getContactsInChallengeOrder()) {
			cache.set(cursorKey(contact.getKey()), snapshot.mandatoryCursors.get(contact.getKey()));
		}
		cache.store(TASKS_COMPLETED_KEY, snapshot.tasksCompleted);
		cache.set(INVENTORY_UPGRADES_KEY, snapshot.inventoryUpgrades);
		cache.set(MIGRATION_VERSION_KEY, snapshot.migrationVersion);
		cache.set(LEGACY_STATUS_KEY, snapshot.legacyStatus.getCode());
		cache.set(LEGACY_PRESTIGE_KEY, snapshot.legacyPrestige);
	}

	public static Snapshot defaults(MonsterSlayerData data) {
		Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
		for (Contact contact : data.getContactsInChallengeOrder()) {
			cursors.put(contact.getKey(), 0);
		}
		return new Snapshot(STATE_VERSION, 0, MonsterSlayerRank.UNSTAMPED,
			MonsterSlayerBalances.zero(), cursors, null, 0, 0L, 0, 0,
			LegacyStatus.NONE, 0);
	}

	/**
	 * Performs the only runtime cache integration for this foundation slice.
	 * A valid completed migration is read-only; malformed state remains in place
	 * for diagnosis instead of being silently replaced with progression.
	 */
	public static LoadResult initialize(Cache cache, MonsterSlayerData data,
			CombatOdysseyMigration.LegacyData legacyData) {
		if (cache == null || data == null || legacyData == null) {
			throw new IllegalArgumentException("Monster Slayer load inputs are required");
		}
		final Snapshot current;
		try {
			current = read(cache, data);
		} catch (ValidationException ex) {
			return LoadResult.quarantined(ex.getMessage());
		}
		if (current.getMigrationVersion() == MIGRATION_VERSION) {
			return LoadResult.loaded(current);
		}
		Map<String, Object> values = cache.getCacheMap();
		CombatOdysseyMigration.Result migration = CombatOdysseyMigration.propose(
			CombatOdysseyMigration.LegacySnapshot.of(
				values.get("combat_odyssey"), values.get("co_tier_progress"),
				values.get("co_prestige")),
			legacyData, data, current);
		if (!migration.isSuccessful()) {
			return LoadResult.quarantined(migration.getFailure() + ": " + migration.getDiagnostic());
		}
		write(cache, data, migration.getProposal());
		return LoadResult.migrated(migration.getProposal(), migration.getClassification());
	}

	public static Snapshot create(int introStage, MonsterSlayerRank rank,
			MonsterSlayerBalances balances, Map<String, Integer> mandatoryCursors,
			String activeTaskKey, int activeKills, long tasksCompleted, int inventoryUpgrades,
			int migrationVersion,
			LegacyStatus legacyStatus, int legacyPrestige, MonsterSlayerData data) {
		Snapshot snapshot = new Snapshot(STATE_VERSION, introStage, rank, balances, mandatoryCursors,
			activeTaskKey, activeKills, tasksCompleted, inventoryUpgrades, migrationVersion,
			legacyStatus, legacyPrestige);
		validate(snapshot, data);
		return snapshot;
	}

	public static void validate(Snapshot snapshot, MonsterSlayerData data) {
		if (snapshot == null || data == null) {
			throw new ValidationException("Monster Slayer snapshot and definitions are required");
		}
		if (snapshot.stateVersion != STATE_VERSION) {
			throw new ValidationException("Unsupported Monster Slayer state version");
		}
		if (snapshot.introStage < 0 || snapshot.introStage > 2) {
			throw new ValidationException("Monster Slayer intro stage is outside 0..2");
		}
		if (snapshot.rank == null || snapshot.balances == null || snapshot.legacyStatus == null) {
			throw new ValidationException("Monster Slayer typed state is incomplete");
		}
		if (snapshot.rank.isAtLeast(MonsterSlayerRank.FLEDGLING) && snapshot.introStage != 2) {
			throw new ValidationException("A stamped Monster Slayer rank requires the completed introduction");
		}
		if (snapshot.tasksCompleted < 0L) {
			throw new ValidationException("Monster Slayer lifetime completion count is negative");
		}
		InventoryUpgrade.validateMask(snapshot.inventoryUpgrades);
		if (snapshot.migrationVersion < 0 || snapshot.migrationVersion > MIGRATION_VERSION) {
			throw new ValidationException("Monster Slayer migration version is unsupported");
		}
		if (snapshot.legacyPrestige < 0) {
			throw new ValidationException("Monster Slayer legacy prestige is negative");
		}
		if (snapshot.legacyStatus == LegacyStatus.COMPLETED_CLAIMED && snapshot.legacyPrestige < 1) {
			throw new ValidationException("Claimed Odyssey completion requires legacy prestige");
		}

		List<Contact> contacts = data.getContactsInChallengeOrder();
		if (snapshot.mandatoryCursors.size() != contacts.size()) {
			throw new ValidationException("Monster Slayer mandatory cursor keys are incomplete");
		}
		int completedContacts = Math.max(0, snapshot.rank.getCode() - 1);
		for (int index = 0; index < contacts.size(); index++) {
			Contact contact = contacts.get(index);
			Integer cursorValue = snapshot.mandatoryCursors.get(contact.getKey());
			if (cursorValue == null) {
				throw new ValidationException("Missing Monster Slayer cursor for " + contact.getKey());
			}
			int cursor = cursorValue;
			int length = contact.getMandatoryTasks().size();
			if (cursor < 0 || cursor > length) {
				throw new ValidationException("Monster Slayer cursor is out of range for " + contact.getKey());
			}
			if (index < completedContacts && cursor != length) {
				throw new ValidationException("Monster Slayer rank is ahead of " + contact.getKey());
			}
			if (index == completedContacts && completedContacts < contacts.size() && cursor == length) {
				throw new ValidationException("Monster Slayer rank did not advance after " + contact.getKey());
			}
			if (index > completedContacts && cursor != 0) {
				throw new ValidationException("Monster Slayer cursor is ahead of rank for " + contact.getKey());
			}
			if (snapshot.rank == MonsterSlayerRank.UNSTAMPED && cursor != 0) {
				throw new ValidationException("Unstamped Monster Slayer state has task progress");
			}
		}

		validateActiveTask(snapshot, data, contacts);
	}

	private static void validateActiveTask(Snapshot snapshot, MonsterSlayerData data, List<Contact> contacts) {
		if (snapshot.activeTaskKey == null) {
			if (snapshot.activeKills != 0) {
				throw new ValidationException("Monster Slayer kills exist without an active task");
			}
			return;
		}
		Task active = data.getTask(snapshot.activeTaskKey);
		if (active == null) {
			throw new ValidationException("Unknown Monster Slayer active task key");
		}
		if (snapshot.activeKills < 0 || snapshot.activeKills > active.getRequiredKills()) {
			throw new ValidationException("Monster Slayer active kills are outside task bounds");
		}
		for (Contact contact : contacts) {
			List<Task> ownedTasks = active.isRepeatable()
				? contact.getRepeatableTasks() : contact.getMandatoryTasks();
			for (int index = 0; index < ownedTasks.size(); index++) {
				if (!ownedTasks.get(index).getKey().equals(active.getKey())) {
					continue;
				}
				int cursor = snapshot.mandatoryCursors.get(contact.getKey());
				if (active.isRepeatable()) {
					if (cursor != contact.getMandatoryTasks().size()
						|| !snapshot.rank.isAtLeast(contact.getAwardedRank())) {
						throw new ValidationException("Repeatable Monster Slayer task belongs to an incomplete contact");
					}
				} else if (cursor != index || snapshot.rank != contact.getRequiredRank()) {
					throw new ValidationException("Mandatory Monster Slayer task does not match the active cursor");
				}
				return;
			}
		}
		throw new ValidationException("Monster Slayer task has no contact owner");
	}

	public static SpendProposal proposeSpend(Snapshot current, MonsterSlayerData data,
			MonsterSlayerCost unitCost, long quantity) {
		validate(current, data);
		MonsterSlayerBalances.SpendResult result = current.balances.trySpend(unitCost, quantity);
		if (!result.isSuccessful()) {
			return SpendProposal.insufficient(current);
		}
		Snapshot spent = current.withBalances(result.getBalances());
		validate(spent, data);
		return SpendProposal.success(spent, new RefundReceipt(result.getReceipt()));
	}

	/** Assigns only the deterministic next task for an eligible contact. */
	public static TaskResult assignMandatory(Snapshot current, MonsterSlayerData data,
			String contactKey) {
		validate(current, data);
		Contact contact = data.getContact(contactKey);
		if (contact == null) return TaskResult.rejected(current, TaskResult.Reason.UNKNOWN_CONTACT);
		if (current.activeTaskKey != null) return TaskResult.rejected(current, TaskResult.Reason.ACTIVE_TASK);
		if (current.rank != contact.getRequiredRank()) return TaskResult.rejected(current, TaskResult.Reason.RANK);
		int cursor = current.mandatoryCursors.get(contact.getKey());
		if (cursor >= contact.getMandatoryTasks().size()) {
			return TaskResult.rejected(current, TaskResult.Reason.MANDATORY_COMPLETE);
		}
		Snapshot assigned = current.withActiveTask(contact.getMandatoryTasks().get(cursor).getKey(), 0);
		validate(assigned, data);
		return TaskResult.assigned(assigned);
	}

	/** Assigns a caller-selected stable repeatable key after contact/rank validation. */
	public static TaskResult assignRepeatable(Snapshot current, MonsterSlayerData data,
			String contactKey, String taskKey) {
		validate(current, data);
		Contact contact = data.getContact(contactKey);
		if (contact == null) return TaskResult.rejected(current, TaskResult.Reason.UNKNOWN_CONTACT);
		if (current.activeTaskKey != null) return TaskResult.rejected(current, TaskResult.Reason.ACTIVE_TASK);
		if (!current.rank.isAtLeast(contact.getAwardedRank())) {
			return TaskResult.rejected(current, TaskResult.Reason.RANK);
		}
		for (Task task : contact.getRepeatableTasks()) {
			if (task.getKey().equals(taskKey)) {
				Snapshot assigned = current.withActiveTask(task.getKey(), 0);
				validate(assigned, data);
				return TaskResult.assigned(assigned);
			}
		}
		return TaskResult.rejected(current, TaskResult.Reason.INVALID_REPEATABLE);
	}

	/** Applies one already-validated eligible death. It is safe to call repeatedly. */
	public static TaskResult recordEligibleKill(Snapshot current, MonsterSlayerData data, int npcId) {
		validate(current, data);
		if (current.activeTaskKey == null) return TaskResult.rejected(current, TaskResult.Reason.NO_ACTIVE_TASK);
		Task task = data.getTask(current.activeTaskKey);
		FamilyOwner owner = findOwner(data, task);
		if (owner == null) return TaskResult.rejected(current, TaskResult.Reason.INVALID_STATE);
		Family family = data.getFamily(task.getFamilyKey());
		if (family == null || !family.getNpcIds().contains(npcId)) {
			return TaskResult.rejected(current, TaskResult.Reason.WRONG_NPC);
		}
		int kills = current.activeKills + 1;
		if (kills < task.getRequiredKills()) {
			Snapshot progressed = current.withActiveTask(task.getKey(), kills);
			validate(progressed, data);
			return TaskResult.progressed(progressed);
		}
		MonsterSlayerBalances balances = current.balances.credit(owner.contact.getChallenge(), task.getPointReward());
		Map<String, Integer> cursors = new LinkedHashMap<String, Integer>(current.mandatoryCursors);
		MonsterSlayerRank rank = current.rank;
		if (!task.isRepeatable()) {
			int nextCursor = cursors.get(owner.contact.getKey()) + 1;
			cursors.put(owner.contact.getKey(), nextCursor);
			if (nextCursor == owner.contact.getMandatoryTasks().size()) rank = owner.contact.getAwardedRank();
		}
		Snapshot completed = new Snapshot(current.stateVersion, current.introStage, rank, balances,
			cursors, null, 0, Math.addExact(current.tasksCompleted, 1L), current.inventoryUpgrades,
			current.migrationVersion, current.legacyStatus, current.legacyPrestige);
		validate(completed, data);
		return TaskResult.completed(completed, task.getPointReward(), owner.contact.getChallenge());
	}

	private static FamilyOwner findOwner(MonsterSlayerData data, Task task) {
		for (Contact contact : data.getContactsInChallengeOrder()) {
			for (Task candidate : task.isRepeatable() ? contact.getRepeatableTasks() : contact.getMandatoryTasks()) {
				if (candidate.getKey().equals(task.getKey())) return new FamilyOwner(contact);
			}
		}
		return null;
	}

	private static final class FamilyOwner {
		private final Contact contact;
		private FamilyOwner(Contact contact) { this.contact = contact; }
	}

	private static int readInteger(Map<String, Object> values, String key, int defaultValue) {
		Object value = values.get(key);
		if (value == null) {
			return defaultValue;
		}
		if (!(value instanceof Integer)) {
			throw new ValidationException("Monster Slayer cache type mismatch for " + key);
		}
		return (Integer) value;
	}

	private static long readLong(Map<String, Object> values, String key, long defaultValue) {
		Object value = values.get(key);
		if (value == null) {
			return defaultValue;
		}
		if (!(value instanceof Long)) {
			throw new ValidationException("Monster Slayer cache type mismatch for " + key);
		}
		return (Long) value;
	}

	private static String readString(Map<String, Object> values, String key, String defaultValue) {
		Object value = values.get(key);
		if (value == null) {
			return defaultValue;
		}
		if (!(value instanceof String)) {
			throw new ValidationException("Monster Slayer cache type mismatch for " + key);
		}
		return (String) value;
	}

	private static String balanceKey(MonsterSlayerChallenge challenge) {
		return BALANCE_PREFIX + challenge.getCacheSuffix();
	}

	private static String cursorKey(String contactKey) {
		return MANDATORY_PREFIX + contactKey;
	}

	/**
	 * Stable entitlement mapping. Bits are explicit contact identities, never
	 * enum ordinals or JSON array positions. Inventory behavior remains unchanged
	 * until the later capacity-activation slice consumes this derived value.
	 */
	public enum InventoryUpgrade {
		FALADOR("falador", 0x01, 1),
		PORT_SARIM("port_sarim", 0x02, 1),
		BRIMHAVEN("brimhaven", 0x04, 1),
		CHAMPIONS("champions", 0x08, 2),
		HEROES("heroes", 0x10, 2),
		LEGENDS("legends", 0x20, 3);

		private final String contactKey;
		private final int bit;
		private final int capacityIncrease;

		InventoryUpgrade(String contactKey, int bit, int capacityIncrease) {
			this.contactKey = contactKey;
			this.bit = bit;
			this.capacityIncrease = capacityIncrease;
		}

		public String getContactKey() { return contactKey; }
		public int getBit() { return bit; }
		public int getCapacityIncrease() { return capacityIncrease; }

		public static void validateMask(int mask) {
			if ((mask & ~allowedMask()) != 0) {
				throw new ValidationException("Monster Slayer inventory upgrades contain unknown bits");
			}
			boolean missingEarlier = false;
			for (InventoryUpgrade upgrade : values()) {
				boolean present = (mask & upgrade.bit) != 0;
				if (!present) {
					missingEarlier = true;
				} else if (missingEarlier) {
					throw new ValidationException("Monster Slayer inventory upgrades are not a prefix");
				}
			}
		}

		public static int derivedCapacity(int mask) {
			validateMask(mask);
			int capacity = 30;
			for (InventoryUpgrade upgrade : values()) {
				if ((mask & upgrade.bit) != 0) {
					capacity += upgrade.capacityIncrease;
				}
			}
			return capacity;
		}

		private static int allowedMask() {
			int result = 0;
			for (InventoryUpgrade upgrade : values()) {
				result |= upgrade.bit;
			}
			return result;
		}
	}

	public enum LegacyStatus {
		NONE(0),
		PARTIAL(1),
		COMPLETED_UNCLAIMED(2),
		COMPLETED_CLAIMED(3);

		private final int code;

		LegacyStatus(int code) {
			this.code = code;
		}

		public int getCode() {
			return code;
		}

		public static LegacyStatus fromCode(int code) {
			for (LegacyStatus status : values()) {
				if (status.code == code) {
					return status;
				}
			}
			throw new ValidationException("Unknown Monster Slayer legacy status code");
		}
	}

	public static final class LoadResult {
		public enum Status { LOADED, MIGRATED, QUARANTINED }

		private final Status status;
		private final Snapshot snapshot;
		private final CombatOdysseyMigration.Classification classification;
		private final String diagnostic;

		private LoadResult(Status status, Snapshot snapshot,
				CombatOdysseyMigration.Classification classification, String diagnostic) {
			this.status = status;
			this.snapshot = snapshot;
			this.classification = classification;
			this.diagnostic = diagnostic;
		}

		private static LoadResult loaded(Snapshot snapshot) {
			return new LoadResult(Status.LOADED, snapshot,
				CombatOdysseyMigration.Classification.ALREADY_MIGRATED, null);
		}

		private static LoadResult migrated(Snapshot snapshot,
				CombatOdysseyMigration.Classification classification) {
			return new LoadResult(Status.MIGRATED, snapshot, classification, null);
		}

		private static LoadResult quarantined(String diagnostic) {
			return new LoadResult(Status.QUARANTINED, null, null, diagnostic);
		}

		public Status getStatus() { return status; }
		public Snapshot getSnapshot() { return snapshot; }
		public CombatOdysseyMigration.Classification getClassification() { return classification; }
		public String getDiagnostic() { return diagnostic; }
	}

	public static final class TaskResult {
		public enum Reason {
			ASSIGNED, PROGRESSED, COMPLETED, UNKNOWN_CONTACT, ACTIVE_TASK, RANK,
			MANDATORY_COMPLETE, INVALID_REPEATABLE, NO_ACTIVE_TASK, WRONG_NPC, INVALID_STATE
		}
		private final Snapshot snapshot;
		private final Reason reason;
		private final long awardedPoints;
		private final MonsterSlayerChallenge awardedChallenge;
		private TaskResult(Snapshot snapshot, Reason reason, long awardedPoints,
				MonsterSlayerChallenge awardedChallenge) {
			this.snapshot = snapshot;
			this.reason = reason;
			this.awardedPoints = awardedPoints;
			this.awardedChallenge = awardedChallenge;
		}
		private static TaskResult assigned(Snapshot snapshot) { return new TaskResult(snapshot, Reason.ASSIGNED, 0L, null); }
		private static TaskResult progressed(Snapshot snapshot) { return new TaskResult(snapshot, Reason.PROGRESSED, 0L, null); }
		private static TaskResult completed(Snapshot snapshot, long points, MonsterSlayerChallenge challenge) {
			return new TaskResult(snapshot, Reason.COMPLETED, points, challenge);
		}
		private static TaskResult rejected(Snapshot snapshot, Reason reason) { return new TaskResult(snapshot, reason, 0L, null); }
		public Snapshot getSnapshot() { return snapshot; }
		public Reason getReason() { return reason; }
		public long getAwardedPoints() { return awardedPoints; }
		public MonsterSlayerChallenge getAwardedChallenge() { return awardedChallenge; }
		public boolean isAccepted() { return reason == Reason.ASSIGNED || reason == Reason.PROGRESSED || reason == Reason.COMPLETED; }
	}

	public static final class Snapshot {
		private final int stateVersion;
		private final int introStage;
		private final MonsterSlayerRank rank;
		private final MonsterSlayerBalances balances;
		private final Map<String, Integer> mandatoryCursors;
		private final String activeTaskKey;
		private final int activeKills;
		private final long tasksCompleted;
		private final int inventoryUpgrades;
		private final int migrationVersion;
		private final LegacyStatus legacyStatus;
		private final int legacyPrestige;

		private Snapshot(int stateVersion, int introStage, MonsterSlayerRank rank,
				MonsterSlayerBalances balances, Map<String, Integer> mandatoryCursors,
				String activeTaskKey, int activeKills, long tasksCompleted, int inventoryUpgrades,
				int migrationVersion,
				LegacyStatus legacyStatus, int legacyPrestige) {
			this.stateVersion = stateVersion;
			this.introStage = introStage;
			this.rank = rank;
			this.balances = balances;
			this.mandatoryCursors = mandatoryCursors == null
				? Collections.<String, Integer>emptyMap()
				: Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(mandatoryCursors));
			this.activeTaskKey = activeTaskKey;
			this.activeKills = activeKills;
			this.tasksCompleted = tasksCompleted;
			this.inventoryUpgrades = inventoryUpgrades;
			this.migrationVersion = migrationVersion;
			this.legacyStatus = legacyStatus;
			this.legacyPrestige = legacyPrestige;
		}

		private Snapshot withBalances(MonsterSlayerBalances updated) {
			return new Snapshot(stateVersion, introStage, rank, updated, mandatoryCursors,
				activeTaskKey, activeKills, tasksCompleted, inventoryUpgrades, migrationVersion,
				legacyStatus, legacyPrestige);
		}

		private Snapshot withActiveTask(String taskKey, int kills) {
			return new Snapshot(stateVersion, introStage, rank, balances, mandatoryCursors,
				taskKey, kills, tasksCompleted, inventoryUpgrades, migrationVersion,
				legacyStatus, legacyPrestige);
		}

		public int getIntroStage() { return introStage; }
		public MonsterSlayerRank getRank() { return rank; }
		public MonsterSlayerBalances getBalances() { return balances; }
		public Map<String, Integer> getMandatoryCursors() { return mandatoryCursors; }
		public String getActiveTaskKey() { return activeTaskKey; }
		public int getActiveKills() { return activeKills; }
		public long getTasksCompleted() { return tasksCompleted; }
		public int getInventoryUpgrades() { return inventoryUpgrades; }
		public int getDerivedInventoryCapacity() {
			return InventoryUpgrade.derivedCapacity(inventoryUpgrades);
		}
		public int getMigrationVersion() { return migrationVersion; }
		public LegacyStatus getLegacyStatus() { return legacyStatus; }
		public int getLegacyPrestige() { return legacyPrestige; }

		public boolean isComplete(MonsterSlayerData data) {
			if (rank != MonsterSlayerRank.LEGEND) {
				return false;
			}
			for (Contact contact : data.getContactsInChallengeOrder()) {
				if (mandatoryCursors.get(contact.getKey()) != contact.getMandatoryTasks().size()) {
					return false;
				}
			}
			return true;
		}
	}

	public static final class SpendProposal {
		private final boolean successful;
		private final Snapshot snapshot;
		private final RefundReceipt receipt;

		private SpendProposal(boolean successful, Snapshot snapshot, RefundReceipt receipt) {
			this.successful = successful;
			this.snapshot = snapshot;
			this.receipt = receipt;
		}

		private static SpendProposal success(Snapshot snapshot, RefundReceipt receipt) {
			return new SpendProposal(true, snapshot, receipt);
		}

		private static SpendProposal insufficient(Snapshot unchanged) {
			return new SpendProposal(false, unchanged, null);
		}

		public boolean isSuccessful() { return successful; }
		public Snapshot getSnapshot() { return snapshot; }

		public RefundReceipt getReceipt() {
			if (!successful) {
				throw new IllegalStateException("An unsuccessful spend has no refund receipt");
			}
			return receipt;
		}
	}

	public static final class RefundReceipt {
		private final MonsterSlayerBalances.RefundReceipt balanceReceipt;

		private RefundReceipt(MonsterSlayerBalances.RefundReceipt balanceReceipt) {
			this.balanceReceipt = balanceReceipt;
		}

		public synchronized Snapshot refund(Snapshot current, MonsterSlayerData data) {
			validate(current, data);
			Snapshot refunded = current.withBalances(balanceReceipt.refund(current.balances));
			validate(refunded, data);
			return refunded;
		}

		public boolean isRefunded() {
			return balanceReceipt.isRefunded();
		}
	}

	public static final class ValidationException extends IllegalArgumentException {
		public ValidationException(String message) {
			super(message);
		}
	}
}
