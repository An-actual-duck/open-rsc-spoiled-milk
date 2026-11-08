package com.openrsc.server.net.rsc;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;

/** GameThread-owned, bounded intake for ordinary idle ground walking only. */
public final class IdleWalkCommandAdmission {
	public static final int PER_PLAYER_POLL = 2;
	public static final int PER_PLAYER_TICK = 8;
	public static final int GLOBAL_PER_POLL = 8;
	private int nextIndex;
	private int remaining;

	/**
	 * Rotates admission priority without changing authoritative movement order.
	 * Two live array passes avoid player snapshots and remain bounded by the
	 * existing fixed player registry. Empty/raw-barrier queues never inspect
	 * broad ownership or qualified world coordinates.
	 */
	public int poll(final World world) {
		if (!world.getServer().getConfig().WANT_MYWORLD
			|| !world.getServer().getConfig().WANT_CUSTOM_WALK_SPEED
			|| world.getPlayers().count() == 0) return 0;
		remaining = GLOBAL_PER_POLL;
		final int start = nextIndex;
		world.getPlayers().forEachLive(player -> {
			if (player.getIndex() >= start) admit(player);
		});
		world.getPlayers().forEachLive(player -> {
			if (player.getIndex() < start) admit(player);
		});
		return GLOBAL_PER_POLL - remaining;
	}

	private void admit(final Player player) {
		if (remaining <= 0) return;
		int consumed = player.processIdleWalkCommands(Math.min(PER_PLAYER_POLL, remaining));
		if (consumed > 0) {
			remaining -= consumed;
			nextIndex = player.getIndex() + 1;
		}
	}
}
