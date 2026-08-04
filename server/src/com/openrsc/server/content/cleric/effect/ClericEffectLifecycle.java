package com.openrsc.server.content.cleric.effect;

import com.openrsc.server.model.entity.player.TransientEffectMembershipToken;
import com.openrsc.server.model.entity.player.TransientEffectSessionToken;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Idempotent lifecycle cleanup that never holds multiple registry locks. */
public final class ClericEffectLifecycle {
	private ClericEffectLifecycle() {
	}

	public static int clearRecipient(ClericEffectRegistry recipient) {
		if (recipient == null) {
			throw new IllegalArgumentException("Cleric effect recipient registry is required");
		}
		return recipient.clearAll();
	}

	public static int endMembership(ClericEffectRegistry departingRecipient,
			TransientEffectSessionToken departingSession,
			TransientEffectMembershipToken departingMembership,
			Iterable<ClericEffectRegistry> partyRecipientRegistries) {
		if (departingRecipient == null || departingSession == null
				|| departingMembership == null || partyRecipientRegistries == null) {
			throw new IllegalArgumentException("Complete Cleric membership cleanup state is required");
		}

		int removed = departingRecipient.clearAll();
		Set<ClericEffectRegistry> visited = Collections.newSetFromMap(
			new IdentityHashMap<ClericEffectRegistry, Boolean>());
		visited.add(departingRecipient);
		for (ClericEffectRegistry recipient : partyRecipientRegistries) {
			if (recipient != null && visited.add(recipient)) {
				removed += recipient.clearOriginatingFrom(departingSession, departingMembership);
			}
		}
		return removed;
	}
}
