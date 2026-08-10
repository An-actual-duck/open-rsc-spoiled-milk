package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.model.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Typed, fail-closed contact boundary shared by Talk-to and Task shortcuts. */
public final class MonsterSlayerContactService {
	private final MonsterSlayerData data;
	private final MonsterSlayerTaskService tasks;
	private final RandomSource random;
	private final Map<UUID, PendingSelection> previews = new HashMap<UUID, PendingSelection>();

	public MonsterSlayerContactService(MonsterSlayerData data, MonsterSlayerTaskService tasks) {
		this(data, tasks, new RandomSource() { @Override public int nextInt(int bound) { return ThreadLocalRandom.current().nextInt(bound); }});
	}

	public MonsterSlayerContactService(MonsterSlayerData data, MonsterSlayerTaskService tasks, RandomSource random) {
		if (data == null || tasks == null || random == null) throw new IllegalArgumentException("Monster Slayer contact dependencies are required");
		this.data = data;
		this.tasks = tasks;
		this.random = random;
	}

	public Result beginBeerIntroduction(Player player) { return changeIntroduction(player, false); }
	public Result completeBeerIntroduction(Player player) { return changeIntroduction(player, true); }

	public Result requestTask(Player player, String contactKey) {
		try {
			MonsterSlayerState.Snapshot snapshot = MonsterSlayerState.read(player.getCache(), data);
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
			MonsterSlayerState.Snapshot snapshot = MonsterSlayerState.read(player.getCache(), data);
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
			MonsterSlayerState.Snapshot current = MonsterSlayerState.read(player.getCache(), data);
			MonsterSlayerState.Snapshot next = MonsterSlayerState.acknowledgePromotion(current, data, contactKey);
			MonsterSlayerState.write(player.getCache(), data, next); return Result.accepted(null);
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
				MonsterSlayerState.Snapshot current = MonsterSlayerState.read(player.getCache(), data);
				MonsterSlayerState.Snapshot next = complete
					? MonsterSlayerState.completeIntroduction(current, data)
					: MonsterSlayerState.beginIntroduction(current, data);
				MonsterSlayerState.write(player.getCache(), data, next);
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
