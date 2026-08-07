package com.openrsc.server.model.entity.death;

/** Immutable diagnostic view of one mob's death-lifecycle authority. */
public final class DeathLifecycleSnapshot {
	private final long lifecycleId;
	private final DeathLifecycleState state;
	private final DeathContext context;
	private final long duplicateAttempts;

	DeathLifecycleSnapshot(final long lifecycleId,
			final DeathLifecycleState state, final DeathContext context,
			final long duplicateAttempts) {
		this.lifecycleId = lifecycleId;
		this.state = state;
		this.context = context;
		this.duplicateAttempts = duplicateAttempts;
	}

	public long getLifecycleId() { return lifecycleId; }
	public DeathLifecycleState getState() { return state; }
	public DeathContext getContext() { return context; }
	public long getDuplicateAttempts() { return duplicateAttempts; }
}
