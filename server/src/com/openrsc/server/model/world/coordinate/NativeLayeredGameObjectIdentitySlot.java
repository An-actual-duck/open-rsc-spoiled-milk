package com.openrsc.server.model.world.coordinate;

/**
 * Assign-once carrier for native package object identity.
 *
 * <p>Reassigning the same value is idempotent. Identity cannot be cleared or
 * changed into another package placement.</p>
 */
public final class NativeLayeredGameObjectIdentitySlot {
	private volatile NativeLayeredGameObjectIdentity identity;

	public NativeLayeredGameObjectIdentity get() {
		return identity;
	}

	public synchronized void assign(
		final NativeLayeredGameObjectIdentity proposed) {
		if (proposed == null) {
			throw new NullPointerException("proposed");
		}
		if (identity != null && !identity.equals(proposed)) {
			throw new IllegalStateException(
				"Conflicting native layered object identity assignment");
		}
		identity = proposed;
	}
}
