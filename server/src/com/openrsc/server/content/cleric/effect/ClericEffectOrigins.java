package com.openrsc.server.content.cleric.effect;

import com.openrsc.server.content.party.Party;
import com.openrsc.server.content.party.PartyPlayer;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.TransientEffectMembershipToken;

import java.util.ArrayList;

/** Content-side construction and validation for opaque Cleric effect origins. */
public final class ClericEffectOrigins {
	private ClericEffectOrigins() {
	}

	public static ClericEffectOrigin current(Player caster, Player recipient) {
		if (caster == null || recipient == null || caster == recipient) {
			throw new IllegalArgumentException("Distinct Cleric caster and recipient are required");
		}
		Party party = recipient.getParty();
		TransientEffectMembershipToken casterMembership =
			caster.getTransientEffectMembershipToken();
		TransientEffectMembershipToken recipientMembership =
			recipient.getTransientEffectMembershipToken();
		if (party == null || caster.getParty() != party || casterMembership == null
				|| recipientMembership == null || !caster.isLoggedIn()
				|| caster.isUnregistering()) {
			throw new IllegalStateException("Cleric effect origin is not current");
		}
		return new ClericEffectOrigin(caster.getTransientEffectSessionToken(),
			casterMembership, recipientMembership);
	}

	public static ClericEffectOriginValidator validatorFor(final Player recipient) {
		if (recipient == null) {
			throw new IllegalArgumentException("Cleric effect recipient is required");
		}
		return new ClericEffectOriginValidator() {
			@Override
			public boolean isCurrent(ClericEffectOrigin origin) {
				return isCurrentFor(recipient, origin);
			}
		};
	}

	private static boolean isCurrentFor(Player recipient, ClericEffectOrigin origin) {
		Party party = recipient.getParty();
		if (origin == null || party == null
				|| recipient.getTransientEffectMembershipToken()
					!= origin.getRecipientMembership()) {
			return false;
		}
		for (PartyPlayer member : new ArrayList<PartyPlayer>(party.getPlayers())) {
			Player caster = member.getPlayerReference();
			if (caster != null && caster.isLoggedIn() && !caster.isUnregistering()
					&& caster.getParty() == party
					&& caster.getTransientEffectSessionToken() == origin.getCasterSession()
					&& caster.getTransientEffectMembershipToken()
						== origin.getCasterMembership()) {
				return true;
			}
		}
		return false;
	}
}
