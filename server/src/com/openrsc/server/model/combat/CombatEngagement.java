package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.Mob;

import java.util.UUID;

/**
 * One lifecycle-aware relationship with independently owned attack directions.
 * A counterattack shares the encounter identity but can end independently.
 */
public final class CombatEngagement {
	private final UUID encounterId = UUID.randomUUID();
	private final Mob first;
	private final Mob second;
	private final CombatParticipantSnapshot firstSnapshot;
	private final CombatParticipantSnapshot secondSnapshot;
	private final long startTick;
	private Direction firstToSecond;
	private Direction secondToFirst;

	CombatEngagement(final Mob initiator, final Mob target, final long tick) {
		if (initiator == null || target == null || initiator == target) {
			throw new IllegalArgumentException(
				"an engagement requires two distinct participants");
		}
		first = initiator;
		second = target;
		firstSnapshot = CombatParticipantSnapshot.capture(initiator);
		secondSnapshot = CombatParticipantSnapshot.capture(target);
		startTick = tick;
	}

	synchronized void activate(final Mob source, final CombatStyle style,
			final boolean legacyOpponentProjection, final long tick) {
		requireParticipant(source);
		Direction direction = directionFrom(source);
		if (direction == null) {
			direction = new Direction(style, legacyOpponentProjection, tick);
			setDirectionFrom(source, direction);
		} else {
			direction.activate(style, legacyOpponentProjection, tick);
		}
	}

	synchronized boolean close(final Mob source,
			final CombatEngagementTerminalReason reason, final long tick) {
		requireParticipant(source);
		final Direction direction = directionFrom(source);
		if (direction == null || !direction.active) {
			return false;
		}
		direction.active = false;
		direction.terminalReason = normalize(reason);
		direction.lastTick = Math.max(direction.lastTick, tick);
		return true;
	}

	public UUID getEncounterId() {
		return encounterId;
	}

	public long getStartTick() {
		return startTick;
	}

	public Mob peerOf(final Mob participant) {
		requireParticipant(participant);
		return participant == first ? second : first;
	}

	public boolean contains(final Mob participant) {
		return participant == first || participant == second;
	}

	public boolean hasCurrentParticipantIdentity() {
		return firstSnapshot.matches(first) && secondSnapshot.matches(second);
	}

	public synchronized boolean isDirectionActive(final Mob source) {
		requireParticipant(source);
		final Direction direction = directionFrom(source);
		return direction != null && direction.active;
	}

	public synchronized boolean isMutual() {
		return isDirectionActive(first) && isDirectionActive(second);
	}

	public synchronized CombatStyle getStyleFrom(final Mob source) {
		requireParticipant(source);
		final Direction direction = directionFrom(source);
		return direction == null ? null : direction.style;
	}

	public synchronized boolean projectsLegacyOpponentFrom(final Mob source) {
		requireParticipant(source);
		final Direction direction = directionFrom(source);
		return direction != null && direction.active
			&& direction.legacyOpponentProjection;
	}

	public synchronized CombatEngagementTerminalReason getTerminalReasonFrom(
			final Mob source) {
		requireParticipant(source);
		final Direction direction = directionFrom(source);
		return direction == null ? null : direction.terminalReason;
	}

	private Direction directionFrom(final Mob source) {
		return source == first ? firstToSecond : secondToFirst;
	}

	private void setDirectionFrom(final Mob source, final Direction direction) {
		if (source == first) {
			firstToSecond = direction;
		} else {
			secondToFirst = direction;
		}
	}

	private void requireParticipant(final Mob participant) {
		if (!contains(participant)) {
			throw new IllegalArgumentException(
				"mob is not part of engagement " + encounterId);
		}
	}

	private static CombatEngagementTerminalReason normalize(
			final CombatEngagementTerminalReason reason) {
		return reason == null
			? CombatEngagementTerminalReason.EVENT_ENDED : reason;
	}

	private static final class Direction {
		private CombatStyle style;
		private boolean legacyOpponentProjection;
		private long lastTick;
		private boolean active;
		private CombatEngagementTerminalReason terminalReason;

		private Direction(final CombatStyle style,
				final boolean legacyOpponentProjection, final long tick) {
			activate(style, legacyOpponentProjection, tick);
		}

		private void activate(final CombatStyle style,
				final boolean legacyOpponentProjection, final long tick) {
			this.style = style == null ? CombatStyle.MELEE : style;
			this.legacyOpponentProjection = active
				? this.legacyOpponentProjection || legacyOpponentProjection
				: legacyOpponentProjection;
			lastTick = tick;
			active = true;
			terminalReason = null;
		}
	}
}
