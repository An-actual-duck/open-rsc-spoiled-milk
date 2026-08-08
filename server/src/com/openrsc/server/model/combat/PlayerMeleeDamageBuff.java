package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.player.Player;

/** Shared player-only melee damage composition for primary and Scythe paths. */
public final class PlayerMeleeDamageBuff {
	private PlayerMeleeDamageBuff() { }

	public static int apply(final Player player, final int damage) {
		if (damage <= 0) return damage;
		final int buffedDamage = Math.max(0, (int) Math.floor(
			damage * player.getLeatherSetMeleeDamageMultiplier()));
		return player.applyBearMaulDamage(buffedDamage);
	}
}
