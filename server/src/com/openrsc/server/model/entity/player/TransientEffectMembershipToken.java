package com.openrsc.server.model.entity.player;

/** Opaque process-local identity for one uninterrupted party-membership tenure. */
public final class TransientEffectMembershipToken {
	private TransientEffectMembershipToken() {
	}

	public static TransientEffectMembershipToken issue() {
		return new TransientEffectMembershipToken();
	}
}
