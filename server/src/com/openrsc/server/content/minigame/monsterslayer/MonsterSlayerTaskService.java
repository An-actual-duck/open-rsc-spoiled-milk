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
		return apply(player, MonsterSlayerState.assignMandatory(
			MonsterSlayerState.read(requirePlayer(player).getCache(), data), data, contactKey));
	}

	public MonsterSlayerState.TaskResult assignRepeatable(Player player, String contactKey, String taskKey) {
		return apply(player, MonsterSlayerState.assignRepeatable(
			MonsterSlayerState.read(requirePlayer(player).getCache(), data), data, contactKey, taskKey));
	}

	/** Invoked only after NPC lifecycle code has established player eligibility. */
	public MonsterSlayerState.TaskResult creditEligibleKill(Player player, int npcId) {
		return apply(player, MonsterSlayerState.recordEligibleKill(
			MonsterSlayerState.read(requirePlayer(player).getCache(), data), data, npcId));
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
			return CreditResult.failure("invalid-transition");
		}
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
