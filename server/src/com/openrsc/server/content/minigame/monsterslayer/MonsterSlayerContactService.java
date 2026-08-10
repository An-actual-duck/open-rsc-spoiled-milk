package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.model.entity.player.Player;

/** Typed, fail-closed contact boundary shared by Talk-to and Task shortcuts. */
public final class MonsterSlayerContactService {
	private final MonsterSlayerData data;
	private final MonsterSlayerTaskService tasks;

	public MonsterSlayerContactService(MonsterSlayerData data, MonsterSlayerTaskService tasks) {
		if (data == null || tasks == null) throw new IllegalArgumentException("Monster Slayer contact dependencies are required");
		this.data = data;
		this.tasks = tasks;
	}

	public Result beginBeerIntroduction(Player player) { return changeIntroduction(player, false); }
	public Result completeBeerIntroduction(Player player) { return changeIntroduction(player, true); }

	public Result requestTask(Player player, String contactKey) {
		try {
			MonsterSlayerState.Snapshot snapshot = MonsterSlayerState.read(player.getCache(), data);
			MonsterSlayerDefinitions.Contact contact = data.getContact(contactKey);
			if (contact == null) return Result.rejected("unknown-contact");
			if (snapshot.getActiveTaskKey() != null) return Result.rejected("active-task");
			if (snapshot.getRank() != contact.getRequiredRank()) return Result.rejected("rank");
			MonsterSlayerState.TaskResult result = tasks.assignMandatory(player, contactKey);
			if (result.getReason() == MonsterSlayerState.TaskResult.Reason.MANDATORY_COMPLETE) {
				for (MonsterSlayerDefinitions.Task repeatable : contact.getRepeatableTasks()) {
					result = tasks.assignRepeatable(player, contactKey, repeatable.getKey());
					if (result.isAccepted()) break;
				}
			}
			return result.isAccepted() ? Result.accepted(result) : Result.rejected(result.getReason().name().toLowerCase());
		} catch (RuntimeException failure) { return Result.rejected("invalid-state"); }
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
