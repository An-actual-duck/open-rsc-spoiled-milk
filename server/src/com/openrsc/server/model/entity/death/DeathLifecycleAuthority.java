package com.openrsc.server.model.entity.death;

import com.openrsc.server.model.entity.Mob;

/**
 * Per-mob exactly-once ownership for ordinary reward-eligible death.
 *
 * <p>The authority records identity and lifecycle state only. It deliberately
 * does not mutate Hits, the legacy {@code killed} projection, combat state,
 * rewards, plugins, packets, removal, or respawn. Existing callers retain all
 * of those policies and their established order.</p>
 */
public final class DeathLifecycleAuthority {
	private final Mob owner;
	private long lifecycleId = 1L;
	private DeathLifecycleState state = DeathLifecycleState.ALIVE;
	private DeathContext activeContext;
	private long duplicateAttempts;

	public DeathLifecycleAuthority(final Mob owner) {
		if (owner == null) {
			throw new IllegalArgumentException(
				"death lifecycle owner cannot be null");
		}
		this.owner = owner;
	}

	public synchronized DeathTransition tryBegin(final Mob killer) {
		if (state != DeathLifecycleState.ALIVE) {
			duplicateAttempts++;
			return DeathTransition.duplicate(activeContext);
		}
		activeContext = new DeathContext(lifecycleId, owner, killer);
		state = DeathLifecycleState.DYING;
		return DeathTransition.started(activeContext);
	}

	public synchronized boolean markDead(final DeathContext context) {
		if (!matches(context) || state != DeathLifecycleState.DYING) {
			return false;
		}
		state = DeathLifecycleState.DEAD;
		return true;
	}

	public synchronized boolean markRespawning(final DeathContext context) {
		if (!matches(context)
				|| (state != DeathLifecycleState.DYING
				&& state != DeathLifecycleState.DEAD)) {
			return false;
		}
		state = DeathLifecycleState.RESPAWNING;
		return true;
	}

	/** Restores the same mob after its established respawn callback. */
	public synchronized boolean completeRespawn(final DeathContext context) {
		if (!matches(context) || state != DeathLifecycleState.RESPAWNING) {
			return false;
		}
		resetLifecycle();
		return true;
	}

	/** Restores a scripted/tutorial survivor without treating it as a death. */
	public synchronized boolean revive(final DeathContext context) {
		if (!matches(context) || state != DeathLifecycleState.DYING) {
			return false;
		}
		resetLifecycle();
		return true;
	}

	/**
	 * Releases ownership when legacy NPC death processing throws before removal.
	 * The original path left {@code killed == false} and allowed a later retry.
	 */
	public synchronized boolean abandon(final DeathContext context) {
		if (!matches(context) || state != DeathLifecycleState.DYING) {
			return false;
		}
		resetLifecycle();
		return true;
	}

	public synchronized DeathContext getActiveContext() {
		return activeContext;
	}

	public synchronized DeathLifecycleSnapshot snapshot() {
		return new DeathLifecycleSnapshot(
			lifecycleId, state, activeContext, duplicateAttempts);
	}

	private boolean matches(final DeathContext context) {
		return context != null && context == activeContext
			&& context.getLifecycleId() == lifecycleId
			&& context.matchesTarget(owner);
	}

	private void resetLifecycle() {
		lifecycleId++;
		state = DeathLifecycleState.ALIVE;
		activeContext = null;
		duplicateAttempts = 0L;
	}
}
