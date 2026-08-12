package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.Cache;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.constants.ItemId;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Typed, fail-closed contact boundary shared by Talk-to and Task shortcuts. */
public final class MonsterSlayerContactService {
	private static final int[] RISING_SUN_ALE_IDS = {
		ItemId.ASGARNIAN_ALE.id(), ItemId.WIZARDS_MIND_BOMB.id(), ItemId.DWARVEN_STOUT.id()
	};

	public interface RisingSunAleTransaction {
		Item consume(Player player);
		/** Explicit selections must consume the exact drink the player chose. */
		default Item consume(Player player, int itemId) { return consume(player); }
		boolean refund(Player player, Item consumedAle);
	}
	/** Narrow persistence boundary for transaction-failure characterization. */
	public interface StateStore {
		MonsterSlayerState.Snapshot read(Cache cache, MonsterSlayerData data);
		void write(Cache cache, MonsterSlayerData data, MonsterSlayerState.Snapshot snapshot);
	}
	private final MonsterSlayerData data;
	private final MonsterSlayerTaskService tasks;
	private final RandomSource random;
	private final RisingSunAleTransaction ale;
	private final StateStore stateStore;
	private final Map<UUID, PendingSelection> previews = new HashMap<UUID, PendingSelection>();

	public MonsterSlayerContactService(MonsterSlayerData data, MonsterSlayerTaskService tasks) {
		this(data, tasks, new RandomSource() { @Override public int nextInt(int bound) { return ThreadLocalRandom.current().nextInt(bound); }}, defaultRisingSunAleTransaction());
	}

	public MonsterSlayerContactService(MonsterSlayerData data, MonsterSlayerTaskService tasks, RandomSource random) {
		this(data, tasks, random, defaultRisingSunAleTransaction());
	}
	public MonsterSlayerContactService(MonsterSlayerData data, MonsterSlayerTaskService tasks, RandomSource random, RisingSunAleTransaction ale) {
		this(data, tasks, random, ale, new StateStore() { public MonsterSlayerState.Snapshot read(Cache cache, MonsterSlayerData definitions) { return MonsterSlayerState.read(cache, definitions); } public void write(Cache cache, MonsterSlayerData definitions, MonsterSlayerState.Snapshot snapshot) { MonsterSlayerState.write(cache, definitions, snapshot); }});
	}
	public MonsterSlayerContactService(MonsterSlayerData data, MonsterSlayerTaskService tasks, RandomSource random, RisingSunAleTransaction ale, StateStore stateStore) {
		if (data == null || tasks == null || random == null || ale == null || stateStore == null) throw new IllegalArgumentException("Monster Slayer contact dependencies are required");
		this.data = data;
		this.tasks = tasks;
		this.random = random;
		this.ale = ale;
		this.stateStore = stateStore;
	}

	public Result beginIntroduction(Player player) { return changeIntroduction(player, false); }
	public Result completeIntroduction(Player player) { return changeIntroduction(player, true); }

	/** Atomically couples a Rising Sun ale with the one-time Fledgling promotion. */
	public Result completeIntroductionWithRisingSunAle(Player player) {
		return completeIntroductionWithRisingSunAle(player, -1);
	}

	/**
	 * Atomically exchanges one explicitly selected accepted drink for the first
	 * Slayer rank. A vanished/stale selection never consumes another drink.
	 */
	public Result completeIntroductionWithRisingSunAle(Player player, int selectedAleId) {
		try { synchronized (player) {
			MonsterSlayerState.Snapshot current = stateStore.read(player.getCache(), data);
			MonsterSlayerState.Snapshot next = MonsterSlayerState.completeIntroduction(current, data);
			if (selectedAleId != -1 && !hasRisingSunAle(player, selectedAleId)) {
				return Result.rejected("missing-rising-sun-ale");
			}
			Item consumedAle = selectedAleId == -1 ? ale.consume(player) : ale.consume(player, selectedAleId);
			if (consumedAle == null) return Result.rejected("missing-rising-sun-ale");
			if (!isRisingSunAle(consumedAle.getCatalogId())
				|| (selectedAleId != -1 && consumedAle.getCatalogId() != selectedAleId)) {
				try { ale.refund(player, consumedAle); } catch (RuntimeException ignored) { }
				return Result.rejected("missing-rising-sun-ale");
			}
			try { stateStore.write(player.getCache(), data, next); }
			catch (RuntimeException failure) {
				try { return ale.refund(player, consumedAle) ? Result.rejected("state-write-failed") : Result.rejected("refund-failed"); }
				catch (RuntimeException refundFailure) { return Result.rejected("refund-failed"); }
			}
			return Result.accepted(null);
		} } catch (RuntimeException failure) { return Result.rejected("invalid-state"); }
	}

	public static boolean hasRisingSunAle(Player player) {
		if (player == null) return false;
		return eligibleRisingSunAleIds(player).length > 0;
	}

	/** Returns each carried accepted drink once, in stable player-facing order. */
	public static int[] eligibleRisingSunAleIds(Player player) {
		if (player == null) return new int[0];
		int count = 0;
		for (int id : RISING_SUN_ALE_IDS) if (hasRisingSunAle(player, id)) count++;
		int[] result = new int[count];
		int index = 0;
		for (int id : RISING_SUN_ALE_IDS) if (hasRisingSunAle(player, id)) result[index++] = id;
		return result;
	}

	/** Maps an offered menu index to its selected item, or -1 for decline/stale input. */
	public static int selectedRisingSunAleId(int[] offeredAleIds, int menuSelection) {
		return offeredAleIds != null && menuSelection >= 0 && menuSelection < offeredAleIds.length
			? offeredAleIds[menuSelection] : -1;
	}

	public static String risingSunAleOfferLabel(int itemId) {
		if (itemId == ItemId.ASGARNIAN_ALE.id()) return "Here's your Asgarnian ale.";
		if (itemId == ItemId.WIZARDS_MIND_BOMB.id()) return "Here's your Wizard's mind bomb.";
		if (itemId == ItemId.DWARVEN_STOUT.id()) return "Here's your Dwarven stout.";
		throw new IllegalArgumentException("Not an accepted Hobart drink: " + itemId);
	}

	private static boolean hasRisingSunAle(Player player, int itemId) {
		return isRisingSunAle(itemId) && player.getCarriedItems().getInventory().countId(itemId) > 0;
	}

	private static boolean isRisingSunAle(int itemId) {
		for (int id : RISING_SUN_ALE_IDS) if (id == itemId) return true;
		return false;
	}

	private static RisingSunAleTransaction defaultRisingSunAleTransaction() {
		return new RisingSunAleTransaction() {
			@Override public Item consume(Player player) {
				for (int id : RISING_SUN_ALE_IDS) {
					Item ale = new Item(id);
					if (player.getCarriedItems().remove(ale) != -1) return ale;
				}
				return null;
			}
			@Override public Item consume(Player player, int itemId) {
				if (!isRisingSunAle(itemId)) return null;
				Item ale = new Item(itemId);
				return player.getCarriedItems().remove(ale) != -1 ? ale : null;
			}
			@Override public boolean refund(Player player, Item consumedAle) {
				return consumedAle != null && player.getCarriedItems().getInventory().add(consumedAle, false);
			}
		};
	}

	public Result requestTask(Player player, String contactKey) {
		try {
			MonsterSlayerState.Snapshot snapshot = stateStore.read(player.getCache(), data);
			MonsterSlayerDefinitions.Contact contact = data.getContact(contactKey);
			if (contact == null) return Result.rejected("unknown-contact");
			if (snapshot.getActiveTaskKey() != null) return Result.rejected("active-task");
			if (!snapshot.getRank().isAtLeast(contact.getRequiredRank())) return Result.rejected("rank");
			MonsterSlayerState.TaskResult result = snapshot.getRank() == contact.getRequiredRank()
				? tasks.assignMandatory(player, contactKey) : null;
			if (result == null || result.getReason() == MonsterSlayerState.TaskResult.Reason.MANDATORY_COMPLETE) {
				MonsterSlayerDefinitions.Task repeatable = consumePreview(player, snapshot, contact);
				result = repeatable == null ? result : tasks.assignRepeatable(player, contactKey, repeatable.getKey());
			}
			return result != null && result.isAccepted() ? Result.accepted(result) : Result.rejected(result == null ? "invalid-state" : result.getReason().name().toLowerCase());
		} catch (RuntimeException failure) { return Result.rejected("invalid-state"); }
	}

	/** Uses the same selection as assignment so callers can present warnings first. */
	public MonsterSlayerDefinitions.Task previewTask(Player player, String contactKey) {
		try {
			MonsterSlayerState.Snapshot snapshot = stateStore.read(player.getCache(), data);
			MonsterSlayerDefinitions.Contact contact = data.getContact(contactKey);
			if (contact == null || !snapshot.getRank().isAtLeast(contact.getRequiredRank())) return null;
			int cursor = snapshot.getMandatoryCursors().get(contactKey).intValue();
			if (snapshot.getRank() == contact.getRequiredRank() && cursor < contact.getMandatoryTasks().size()) return contact.getMandatoryTasks().get(cursor);
			MonsterSlayerDefinitions.Task selected = selectRepeatable(contact);
			if (selected != null) synchronized (previews) { previews.put(player.getUUID(), new PendingSelection(contactKey, snapshot.getTasksCompleted(), selected)); }
			return selected;
		} catch (RuntimeException failure) { return null; }
	}

	private MonsterSlayerDefinitions.Task consumePreview(Player player, MonsterSlayerState.Snapshot snapshot, MonsterSlayerDefinitions.Contact contact) {
		synchronized (previews) {
			PendingSelection pending = previews.remove(player.getUUID());
			if (pending != null && pending.contactKey.equals(contact.getKey()) && pending.tasksCompleted == snapshot.getTasksCompleted()) return pending.task;
		}
		return selectRepeatable(contact);
	}

	private MonsterSlayerDefinitions.Task selectRepeatable(MonsterSlayerDefinitions.Contact contact) {
		if (contact.getRepeatableTasks().isEmpty()) return null;
		return contact.getRepeatableTasks().get(random.nextInt(contact.getRepeatableTasks().size()));
	}

	public Result acknowledgePromotion(Player player, String contactKey) {
		try { synchronized (player) {
			MonsterSlayerState.Snapshot current = stateStore.read(player.getCache(), data);
			MonsterSlayerState.Snapshot next = MonsterSlayerState.acknowledgePromotion(current, data, contactKey);
			stateStore.write(player.getCache(), data, next); return Result.accepted(null);
		} } catch (RuntimeException failure) { return Result.rejected("invalid-state"); }
	}

	public interface RandomSource { int nextInt(int bound); }
	private static final class PendingSelection {
		private final String contactKey; private final long tasksCompleted; private final MonsterSlayerDefinitions.Task task;
		private PendingSelection(String contactKey, long tasksCompleted, MonsterSlayerDefinitions.Task task) { this.contactKey = contactKey; this.tasksCompleted = tasksCompleted; this.task = task; }
	}

	private Result changeIntroduction(Player player, boolean complete) {
		try {
			synchronized (player) {
				MonsterSlayerState.Snapshot current = stateStore.read(player.getCache(), data);
				MonsterSlayerState.Snapshot next = complete
					? MonsterSlayerState.completeIntroduction(current, data)
					: MonsterSlayerState.beginIntroduction(current, data);
				stateStore.write(player.getCache(), data, next);
				return Result.accepted(null);
			}
		} catch (RuntimeException failure) { return Result.rejected("invalid-state"); }
	}

	public static final class Result {
		private final boolean accepted; private final String reason; private final MonsterSlayerState.TaskResult task;
		private Result(boolean accepted, String reason, MonsterSlayerState.TaskResult task) { this.accepted = accepted; this.reason = reason; this.task = task; }
		static Result accepted(MonsterSlayerState.TaskResult task) { return new Result(true, null, task); }
		static Result rejected(String reason) { return new Result(false, reason, null); }
		public boolean isAccepted() { return accepted; }
		public String getReason() { return reason; }
		public MonsterSlayerState.TaskResult getTaskResult() { return task; }
	}
}
