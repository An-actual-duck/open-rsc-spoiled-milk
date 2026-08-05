package com.openrsc.server.content;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.HitSplat;

/** Applies the shared Black Unicorn set heal for manual and automatic offerings. */
public final class BlackUnicornOfferingHealing {
	private BlackUnicornOfferingHealing() {
	}

	public static int apply(final Player player, final int itemId, final int amount) {
		if (player == null) {
			return 0;
		}
		final int currentHits = player.getSkills().getLevel(Skill.HITS.id());
		final int healed = BlackUnicornOfferingEffect.calculateHealing(
			itemId, amount,
			player.getCarriedItems().getEquipment().hasFullBlackUnicornHideSet(),
			currentHits, player.getHealingMaximumHits());
		if (healed <= 0) {
			return 0;
		}
		player.getSkills().setLevel(Skill.HITS.id(), currentHits + healed, true);
		player.getUpdateFlags().addHitSplat(new HitSplat(player, HitSplat.TYPE_HEAL, healed));
		if (player.getConfig().WANT_PARTIES && player.getParty() != null) {
			player.getParty().sendParty();
		}
		return healed;
	}
}
