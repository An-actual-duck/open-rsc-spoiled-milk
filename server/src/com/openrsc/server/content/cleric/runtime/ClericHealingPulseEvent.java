package com.openrsc.server.content.cleric.runtime;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.player.Player;

/** One recipient-owned delayed-pulse clock shared by Mend and Greater Mend. */
public final class ClericHealingPulseEvent extends GameTickEvent {
	private static final int PULSE_INTERVAL_TICKS = 1;

	private ClericHealingPulseEvent(final Player recipient) {
		super(recipient.getWorld(), recipient, PULSE_INTERVAL_TICKS,
			"Cleric Healing Pulse", DuplicationStrategy.ONE_PER_MOB);
	}

	public static void restart(final Player recipient) {
		if (recipient == null) {
			throw new IllegalArgumentException("Cleric healing recipient is required");
		}
		for (GameTickEvent event : recipient.getWorld().getServer()
				.getGameEventHandler().getEvents(ClericHealingPulseEvent.class)) {
			if (event.isRunning() && event.belongsTo(recipient)) {
				// Existing cadence belongs to the recipient, never reset by a recast.
				return;
			}
		}
		recipient.getWorld().getServer().getGameEventHandler()
			.addOrUpdate(new ClericHealingPulseEvent(recipient));
	}

	@Override
	public void run() {
		final Player recipient = getPlayerOwner();
		if (recipient == null || !recipient.isLoggedIn() || recipient.isRemoved()
				|| recipient.isUnregistering()
				|| !ClericTimedEffectRuntime.tick(recipient)) {
			stop();
		}
	}
}
