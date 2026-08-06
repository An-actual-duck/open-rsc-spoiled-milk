package com.openrsc.server.runtime;

import com.openrsc.server.model.combat.DamageResult;

/**
 * Optional read-only observer for factual outcomes from current combat paths.
 * Implementations must not mutate participants or make gameplay decisions.
 */
public interface CombatDamageObserver {
	CombatDamageObserver NONE = new CombatDamageObserver() {
		@Override
		public boolean isEnabled() {
			return false;
		}

		@Override
		public void onDamageObserved(final DamageResult result) {
			// Intentional production no-op.
		}
	};

	default boolean isEnabled() {
		return true;
	}

	void onDamageObserved(DamageResult result);
}
