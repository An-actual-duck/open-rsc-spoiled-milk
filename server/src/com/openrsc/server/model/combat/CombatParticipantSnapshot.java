package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;

import java.util.UUID;

/** Identity for one live combat-participant lifetime. */
public final class CombatParticipantSnapshot {
	private final UUID identity;
	private final long lifecycle;
	private final Integer playerSessionId;
	private final World world;

	private CombatParticipantSnapshot(final UUID identity,
			final long lifecycle, final Integer playerSessionId,
			final World world) {
		this.identity = identity;
		this.lifecycle = lifecycle;
		this.playerSessionId = playerSessionId;
		this.world = world;
	}

	public static CombatParticipantSnapshot capture(final Mob participant) {
		if (participant == null) {
			throw new IllegalArgumentException("participant cannot be null");
		}
		return new CombatParticipantSnapshot(
			participant.getUUID(), participant.getCombatLifecycle(),
			participant.isPlayer()
				? Integer.valueOf(((Player) participant).sessionId) : null,
			participant.getWorld());
	}

	public boolean matches(final Mob participant) {
		return matchesIdentityAndSession(participant)
			&& lifecycle == participant.getCombatLifecycle();
	}

	/** Matches stable object/world identity and a player's exact login session. */
	public boolean matchesIdentityAndSession(final Mob participant) {
		if (participant == null || participant.getWorld() != world
				|| !identity.equals(participant.getUUID())) {
			return false;
		}
		if (participant.isPlayer()) {
			return playerSessionId != null
				&& playerSessionId.intValue()
					== ((Player) participant).sessionId;
		}
		return playerSessionId == null;
	}
}
