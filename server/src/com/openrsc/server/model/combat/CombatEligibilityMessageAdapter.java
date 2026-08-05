package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.player.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Preserves current player feedback outside the pure eligibility service. */
public final class CombatEligibilityMessageAdapter {
	private CombatEligibilityMessageAdapter() { }

	public static void sendLegacyAttackMessage(final Player player,
			final CombatEligibilityDecision decision) {
		for (String message : legacyAttackMessages(player, decision)) {
			player.message(message);
		}
	}

	public static List<String> legacyAttackMessages(final Player player,
			final CombatEligibilityDecision decision) {
		if (player == null || decision == null || decision.isAllowed()) {
			return Collections.emptyList();
		}
		return legacyAttackMessages(player, decision.getReason(),
			decision.getDetailValue());
	}

	public static List<String> legacyAttackMessages(final Player player,
			final CombatEligibilityReason reason, final int detailValue) {
		if (player == null || reason == null
			|| reason == CombatEligibilityReason.ALLOWED) {
			return Collections.emptyList();
		}
		switch (reason) {
			case PVP_DISABLED:
				return Collections.singletonList(player.getConfig().WANT_MYWORLD
					? "This is a PvM-only world"
					: "You can't attack other players on this world");
			case PARTY_MEMBER:
				return Collections.singletonList("You can't attack your party members");
			case PK_MODE_DISABLED:
			case TARGET_INVULNERABLE:
				return Collections.singletonList("You are not allowed to attack that person");
			case PK_LUMBRIDGE_RESTRICTED:
				return Collections.singletonList("You can't attack other players here. Move out of Lumbridge");
			case PK_BANKER_RESTRICTED:
				return Collections.singletonList("You cannot attack other players in the vicinity of a banker");
			case PK_LEVEL_MISMATCH:
				return Collections.singletonList("You can only attack players with combat close to your own");
			case SOURCE_OUTSIDE_WILDERNESS:
			case TARGET_OUTSIDE_WILDERNESS:
				return Collections.singletonList("You can't attack other players here. Move to the wilderness");
			case SOURCE_WILDERNESS_LEVEL_MISMATCH:
			case TARGET_WILDERNESS_LEVEL_MISMATCH:
				return Arrays.asList(
					"You can only attack players within "
						+ detailValue + " levels of your own here",
					"Move further into the wilderness for less restrictions");
			default:
				return Collections.emptyList();
		}
	}
}
