package com.openrsc.server.content.cleric.effect;

import com.openrsc.server.model.entity.player.TransientEffectMembershipToken;
import com.openrsc.server.model.entity.player.TransientEffectSessionToken;

/** Immutable server-only origin for one recipient's transient Cleric effect. */
public final class ClericEffectOrigin {
	private final TransientEffectSessionToken casterSession;
	private final TransientEffectMembershipToken casterMembership;
	private final TransientEffectMembershipToken recipientMembership;

	public ClericEffectOrigin(TransientEffectSessionToken casterSession,
			TransientEffectMembershipToken casterMembership,
			TransientEffectMembershipToken recipientMembership) {
		if (casterSession == null || casterMembership == null || recipientMembership == null) {
			throw new IllegalArgumentException("Complete Cleric effect origin is required");
		}
		this.casterSession = casterSession;
		this.casterMembership = casterMembership;
		this.recipientMembership = recipientMembership;
	}

	public TransientEffectSessionToken getCasterSession() {
		return casterSession;
	}

	public TransientEffectMembershipToken getCasterMembership() {
		return casterMembership;
	}

	public TransientEffectMembershipToken getRecipientMembership() {
		return recipientMembership;
	}

	public boolean originatedFrom(TransientEffectSessionToken session,
			TransientEffectMembershipToken membership) {
		return casterSession == session && casterMembership == membership;
	}
}
