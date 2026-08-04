package com.openrsc.server.content.cleric.effect;

/** Opaque process-local identity for one uninterrupted party-membership tenure. */
public final class ClericPartyMembershipToken {
	private ClericPartyMembershipToken() {
	}

	public static ClericPartyMembershipToken issue() {
		return new ClericPartyMembershipToken();
	}
}
