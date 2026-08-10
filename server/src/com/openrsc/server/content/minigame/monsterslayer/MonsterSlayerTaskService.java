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

	private MonsterSlayerState.TaskResult apply(Player player, MonsterSlayerState.TaskResult result) {
		if (result.isAccepted()) MonsterSlayerState.write(player.getCache(), data, result.getSnapshot());
		return result;
	}

	private static Player requirePlayer(Player player) {
		if (player == null || player.isRemoved()) throw new IllegalArgumentException("Active player is required");
		return player;
	}
}
