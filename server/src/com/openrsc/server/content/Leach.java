package com.openrsc.server.content;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.net.rsc.ActionSender;

public final class Leach {
	private Leach() {
	}

	public static int calculateHealing(final int damageDealt, final double leachPercent, final int missingHits) {
		if (damageDealt <= 0 || leachPercent <= 0.0D || missingHits <= 0) {
			return 0;
		}
		final int healing = Math.max(1, (int) Math.floor(damageDealt * leachPercent));
		return Math.min(healing, missingHits);
	}

	public static int heal(final Player player, final int damageDealt, final double leachPercent) {
		if (player == null || player.isRemoved()) {
			return 0;
		}
		final int maxHits = player.getHealingMaximumHits();
		final int currentHits = player.getSkills().getLevel(Skill.HITS.id());
		final int healed = calculateHealing(damageDealt, leachPercent, maxHits - currentHits);
		if (healed <= 0) {
			return 0;
		}
		player.getSkills().setLevel(Skill.HITS.id(), currentHits + healed);
		player.getUpdateFlags().addHitSplat(new HitSplat(player, HitSplat.TYPE_HEAL, healed));
		ActionSender.sendStat(player, Skill.HITS.id());
		return healed;
	}
}
