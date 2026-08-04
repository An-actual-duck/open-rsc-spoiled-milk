package com.openrsc.server.model.entity.player;

/** Opaque process-local identity for one player login/session instance. */
public final class TransientEffectSessionToken {
	private TransientEffectSessionToken() {
	}

	public static TransientEffectSessionToken issue() {
		return new TransientEffectSessionToken();
	}
}
