package com.openrsc.server.model.entity.player;

/**
 * Content-neutral lifecycle boundary for transient recipient-owned effects.
 * Content modules implement the state; the player lifecycle only clears it.
 */
public interface TransientEffectState {
	int clearAll();

	int clearOriginatingFrom(TransientEffectSessionToken session,
		TransientEffectMembershipToken membership);

	static TransientEffectState empty() {
		return Empty.INSTANCE;
	}

	final class Empty implements TransientEffectState {
		private static final Empty INSTANCE = new Empty();

		private Empty() {
		}

		@Override
		public int clearAll() {
			return 0;
		}

		@Override
		public int clearOriginatingFrom(TransientEffectSessionToken session,
				TransientEffectMembershipToken membership) {
			if (session == null || membership == null) {
				throw new IllegalArgumentException("Complete transient-effect origin is required");
			}
			return 0;
		}
	}
}
