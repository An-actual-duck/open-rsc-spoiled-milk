package com.openrsc.server.model.world.coordinate;

/**
 * One assign-once observational carrier for authored placement provenance.
 * Reassigning the same value is idempotent; assigning a conflicting identity
 * is refused. The slot cannot clear identity or perform lifecycle work.
 */
public final class LayeredAuthoredPlacementIdentitySlot {
	private volatile LayeredAuthoredPlacementIdentity identity;

	public LayeredAuthoredPlacementIdentity get() {
		return identity;
	}

	public synchronized void assign(
		final LayeredAuthoredPlacementIdentity proposed) {
		if (proposed == null) {
			throw new NullPointerException("proposed");
		}
		if (identity != null && !identity.equals(proposed)) {
			throw new IllegalStateException(
				"Conflicting authored placement identity assignment");
		}
		identity = proposed;
	}
}
