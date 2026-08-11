package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.model.entity.player.Player;

/** Narrow server API for future dialogue and NPC-death integration. */
public final class MonsterSlayerTaskService {
	private final MonsterSlayerData data;

	public MonsterSlayerTaskService(MonsterSlayerData data) {
		if (data == null) throw new IllegalArgumentException("Monster Slayer data is required");
		this.data = data;
	}

	public MonsterSlayerState.TaskResult assignMandatory(Player player, String contactKey) {
		player = requirePlayer(player);
		synchronized (player) { return apply(player, MonsterSlayerState.assignMandatory(
			MonsterSlayerState.read(player.getCache(), data), data, contactKey)); }
	}

	public MonsterSlayerState.TaskResult assignRepeatable(Player player, String contactKey, String taskKey) {
		player = requirePlayer(player);
		synchronized (player) { return apply(player, MonsterSlayerState.assignRepeatable(
			MonsterSlayerState.read(player.getCache(), data), data, contactKey, taskKey)); }
	}

	/** Invoked only after NPC lifecycle code has established player eligibility. */
	public MonsterSlayerState.TaskResult creditEligibleKill(Player player, int npcId) {
		player = requirePlayer(player);
		synchronized (player) { return apply(player, MonsterSlayerState.recordEligibleKill(
			MonsterSlayerState.read(player.getCache(), data), data, npcId)); }
	}

	/**
	 * Development-only completion seam. The command caller is responsible for
	 * privilege gating; this service deliberately replays the normal typed kill
	 * transition so rewards, mandatory cursors, rank changes, and exact-once
	 * completion retain their production semantics.
	 */
	public MonsterSlayerState.TaskResult completeActiveTaskForDevelopment(Player player) {
		player = requirePlayer(player);
		synchronized (player) {
			MonsterSlayerState.Snapshot current = MonsterSlayerState.read(player.getCache(), data);
			MonsterSlayerState.TaskResult result = MonsterSlayerState.completeActiveTaskForDevelopment(current, data);
			return apply(player, result);
		}
	}

	public MonsterSlayerState.DevelopmentResult advanceOneRankForDevelopment(Player player) {
		player = requirePlayer(player);
		synchronized (player) {
			MonsterSlayerState.DevelopmentResult result = MonsterSlayerState.advanceOneRankForDevelopment(
				MonsterSlayerState.read(player.getCache(), data), data);
			if (result.isAccepted()) MonsterSlayerState.write(player.getCache(), data, result.getSnapshot());
			return result;
		}
	}

	public MonsterSlayerState.DevelopmentResult setBalanceForDevelopment(Player player,
			MonsterSlayerChallenge challenge, long amount) {
		player = requirePlayer(player);
		synchronized (player) {
			MonsterSlayerState.DevelopmentResult result = MonsterSlayerState.setBalanceForDevelopment(
				MonsterSlayerState.read(player.getCache(), data), data, challenge, amount);
			if (result.isAccepted()) MonsterSlayerState.write(player.getCache(), data, result.getSnapshot());
			return result;
		}
	}

	/**
	 * Optional progression must not be allowed to escape into the authoritative
	 * NPC death lifecycle. The caller still owns diagnostics and continues with
	 * other contributors after a failed credit.
	 */
	public CreditResult tryCreditEligibleKill(Player player, int npcId) {
		try {
			return CreditResult.success(creditEligibleKill(player, npcId));
		} catch (MonsterSlayerState.ValidationException ex) {
			return CreditResult.failure("invalid-state");
		} catch (ArithmeticException ex) {
			return CreditResult.failure("arithmetic-overflow");
		} catch (IllegalArgumentException ex) {
			if (ex.getMessage() != null && ex.getMessage().contains(" balance outside")) {
				return CreditResult.failure("balance-cap");
			}
			return CreditResult.failure("invalid-transition");
		} catch (RuntimeException ex) {
			// Slayer is optional at this lifecycle boundary. Never let an
			// unexpected cache/runtime failure abort an otherwise valid death.
			return CreditResult.failure("runtime-failure");
		}
	}

	/**
	 * Returns the player-facing remaining-target message only for a persisted,
	 * non-final task credit. Completion keeps its existing presentation path.
	 */
	public String progressMessage(MonsterSlayerState.TaskResult result) {
		if (result == null || result.getReason() != MonsterSlayerState.TaskResult.Reason.PROGRESSED) {
			return null;
		}
		MonsterSlayerState.Snapshot snapshot = result.getSnapshot();
		MonsterSlayerDefinitions.Task task = snapshot == null
			? null : data.getTask(snapshot.getActiveTaskKey());
		if (task == null) return null;
		MonsterSlayerDefinitions.Family family = data.getFamily(task.getFamilyKey());
		int remaining = task.getRequiredKills() - snapshot.getActiveKills();
		if (family == null || remaining <= 0) return null;
		return "You have " + remaining + " "
			+ displayNameForRemaining(task.getDisplayName(family.getDisplayName()), remaining) + " left to kill.";
	}

	private static String displayNameForRemaining(String displayName, int remaining) {
		if (remaining != 1 || displayName == null || !displayName.endsWith("s")) {
			return displayName;
		}
		if (displayName.endsWith("ves")) {
			return displayName.substring(0, displayName.length() - 3) + "f";
		}
		if (displayName.endsWith("ies")) {
			return displayName.substring(0, displayName.length() - 3) + "y";
		}
		return displayName.substring(0, displayName.length() - 1);
	}

	private MonsterSlayerState.TaskResult apply(Player player, MonsterSlayerState.TaskResult result) {
		if (result.isAccepted()) MonsterSlayerState.write(player.getCache(), data, result.getSnapshot());
		return result;
	}

	private static Player requirePlayer(Player player) {
		if (player == null || player.isRemoved()) throw new IllegalArgumentException("Active player is required");
		return player;
	}

	public static final class CreditResult {
		private final MonsterSlayerState.TaskResult taskResult;
		private final String failureReason;
		private CreditResult(MonsterSlayerState.TaskResult taskResult, String failureReason) {
			this.taskResult = taskResult;
			this.failureReason = failureReason;
		}
		private static CreditResult success(MonsterSlayerState.TaskResult result) {
			return new CreditResult(result, null);
		}
		private static CreditResult failure(String reason) { return new CreditResult(null, reason); }
		public boolean isSuccessful() { return failureReason == null; }
		public MonsterSlayerState.TaskResult getTaskResult() { return taskResult; }
		public String getFailureReason() { return failureReason; }
	}
}
