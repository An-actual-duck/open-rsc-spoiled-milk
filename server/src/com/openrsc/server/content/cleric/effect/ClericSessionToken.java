package com.openrsc.server.content.cleric.effect;

/** Opaque process-local identity for one Player login/session instance. */
public final class ClericSessionToken {
	private ClericSessionToken() {
	}

	public static ClericSessionToken issue() {
		return new ClericSessionToken();
	}
}
