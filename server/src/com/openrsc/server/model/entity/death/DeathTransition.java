package com.openrsc.server.model.entity.death;

import java.util.Objects;

/** Result of atomically attempting to acquire one mob's death lifecycle. */
public final class DeathTransition {
	public enum Status {
		STARTED,
		DUPLICATE
	}

	private final Status status;
	private final DeathContext context;

	private DeathTransition(final Status status,
			final DeathContext context) {
		this.status = Objects.requireNonNull(status, "status");
		this.context = Objects.requireNonNull(context, "context");
	}

	static DeathTransition started(final DeathContext context) {
		return new DeathTransition(Status.STARTED, context);
	}

	static DeathTransition duplicate(final DeathContext context) {
		return new DeathTransition(Status.DUPLICATE, context);
	}

	public Status getStatus() { return status; }
	public DeathContext getContext() { return context; }
	public boolean isStarted() { return status == Status.STARTED; }
	public boolean isDuplicate() { return status == Status.DUPLICATE; }
}
