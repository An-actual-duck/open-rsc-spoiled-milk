package com.openrsc.server.content.cleric.effect;

/** Immutable server-only origin for one recipient's transient Cleric effect. */
public final class ClericEffectOrigin {
	private final ClericSessionToken casterSession;
	private final ClericPartyMembershipToken casterMembership;
	private final ClericPartyMembershipToken recipientMembership;

	public ClericEffectOrigin(ClericSessionToken casterSession,
			ClericPartyMembershipToken casterMembership,
			ClericPartyMembershipToken recipientMembership) {
		if (casterSession == null || casterMembership == null || recipientMembership == null) {
			throw new IllegalArgumentException("Complete Cleric effect origin is required");
		}
		this.casterSession = casterSession;
		this.casterMembership = casterMembership;
		this.recipientMembership = recipientMembership;
	}

	public ClericSessionToken getCasterSession() {
		return casterSession;
	}

	public ClericPartyMembershipToken getCasterMembership() {
		return casterMembership;
	}

	public ClericPartyMembershipToken getRecipientMembership() {
		return recipientMembership;
	}

	public boolean originatedFrom(ClericSessionToken session,
			ClericPartyMembershipToken membership) {
		return casterSession == session && casterMembership == membership;
	}
}
