package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.DataConversions;

public final class DivineGrace {
	private static final double MAX_PROC_CHANCE = 0.75D;
	private static final double CURVE_POWER = 1.35D;

	private DivineGrace() {
	}

	public static boolean apply(final Player attacker, final int damageDealt) {
		if (!canProc(attacker, damageDealt)) {
			return false;
		}
		final double chance = getProcChance(attacker);
		if (DataConversions.getRandom().nextDouble() >= chance) {
			return false;
		}

		final int currentHits = attacker.getSkills().getLevel(Skill.HITS.id());
		final int maxHits = attacker.getHealingMaximumHits();
		final int healed = Math.min(damageDealt, Math.max(0, maxHits - currentHits));
		if (healed <= 0) {
			return false;
		}
		attacker.getSkills().setLevel(Skill.HITS.id(), currentHits + healed);
		attacker.getUpdateFlags().setCombatEffect(new CombatEffect(attacker, CombatEffect.DIVINE_GRACE));
		attacker.getUpdateFlags().addHitSplat(new HitSplat(attacker, HitSplat.TYPE_HEAL, healed));
		ActionSender.sendStat(attacker, Skill.HITS.id());
		return true;
	}

	private static boolean canProc(final Player attacker, final int damageDealt) {
		return attacker != null
			&& damageDealt > 0
			&& attacker.getSkills().getLevel(Skill.HITS.id()) > 0
			&& attacker.getPrayerBook() == PrayerCatalog.GodLine.SARADOMIN
			&& attacker.getCarriedItems().getEquipment().hasEquipped(ItemId.SARADOMIN_MACE.id())
			&& attacker.getPrayers().isPrayerActivated(Prayers.DIVINE_GRACE);
	}

	private static double getProcChance(final Player attacker) {
		final int currentHits = Math.max(0, attacker.getSkills().getLevel(Skill.HITS.id()));
		final int maxHits = Math.max(1, attacker.getHealingMaximumHits());
		final double missingHealthFraction = Math.max(0.0D, Math.min(1.0D, 1.0D - (currentHits / (double) maxHits)));
		return MAX_PROC_CHANCE * Math.pow(missingHealthFraction, CURVE_POWER);
	}
}
