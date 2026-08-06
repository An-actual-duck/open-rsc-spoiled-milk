package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.DataConversions;

public final class DivineRetribution {
	private static final String DAMAGE_EFFECT_KEY = "divine-retribution";
	private static final double MAX_PROC_CHANCE = 0.90D;
	private static final double DAMAGE_SCALE = 10.0D;
	private static final double CURVE_POWER = 1.4D;

	private DivineRetribution() {
	}

	public static Result apply(final Player defender, final Mob attacker, final int incomingDamage) {
		if (!canProc(defender, attacker, incomingDamage)) {
			return Result.noProc();
		}
		final double chance = getProcChance(incomingDamage);
		if (DataConversions.getRandom().nextDouble() >= chance) {
			return Result.noProc();
		}

		final int reflectedDamage = incomingDamage * 2;
		attacker.getUpdateFlags().setCombatEffect(new CombatEffect(attacker, CombatEffect.DIVINE_RETRIBUTION));
		final DamageRequest damageRequest = DamageRequest.resolvedLegacy(
			defender, attacker, DamageRequest.SourceCategory.OWNED_EFFECT,
			DAMAGE_EFFECT_KEY, reflectedDamage)
			.style(CombatStyle.MELEE)
			.hitSplatType(HitSplat.TYPE_ARMOR_PROC)
			.build();
		final DamageResult damageResult = attacker.getWorld().getServer()
			.getResolvedDamageTransaction().apply(damageRequest);
		if (attacker.isNpc()) {
			((Npc) attacker).addCombatDamage(
				defender, damageResult.getLegacyDamageDealt());
		} else if (attacker.isPlayer()) {
			ActionSender.sendStat((Player) attacker, Skill.HITS.id());
		}
		return new Result(true, reflectedDamage, attacker.getSkills().getLevel(Skill.HITS.id()) <= 0);
	}

	private static boolean canProc(final Player defender, final Mob attacker, final int incomingDamage) {
		return defender != null
			&& attacker != null
			&& incomingDamage > 0
			&& attacker.getSkills().getLevel(Skill.HITS.id()) > 0
			&& defender.getPrayerBook() == PrayerCatalog.GodLine.ZAMORAK
			&& defender.getCarriedItems().getEquipment().hasEquipped(ItemId.ZAMORAK_MACE.id())
			&& defender.getPrayers().isPrayerActivated(Prayers.DIVINE_RETRIBUTION);
	}

	private static double getProcChance(final int damage) {
		final double scaledChance = 1.0D - Math.exp(-damage / DAMAGE_SCALE);
		return MAX_PROC_CHANCE * Math.pow(scaledChance, CURVE_POWER);
	}

	public static final class Result {
		private static final Result NO_PROC = new Result(false, 0, false);
		private final boolean proc;
		private final int damage;
		private final boolean killedAttacker;

		private Result(final boolean proc, final int damage, final boolean killedAttacker) {
			this.proc = proc;
			this.damage = damage;
			this.killedAttacker = killedAttacker;
		}

		private static Result noProc() {
			return NO_PROC;
		}

		public boolean didProc() {
			return proc;
		}

		public int getDamage() {
			return damage;
		}

		public boolean killedAttacker() {
			return killedAttacker;
		}
	}
}
