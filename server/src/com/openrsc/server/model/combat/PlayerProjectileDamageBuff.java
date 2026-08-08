package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.player.Player;

/** Current elemental-leather primary-magic damage composition for projectiles. */
public final class PlayerProjectileDamageBuff {
	private PlayerProjectileDamageBuff() { }

	public static int apply(final Player player, final int damage,
			final boolean magicAttack, final int earthDebuffPercent,
			final int waterDebuffPercent, final int fireDebuffPercent) {
		if (damage <= 0 || !magicAttack) return damage;
		final double multiplier = selectMultiplier(earthDebuffPercent,
			waterDebuffPercent, fireDebuffPercent,
			player.getEarthMagicDamageMultiplier(),
			player.getWaterMagicDamageMultiplier(),
			player.getFireMagicDamageMultiplier());
		return Math.max(0, (int) Math.floor(damage * multiplier));
	}

	/** Retains the historical Earth, Water, Fire selection order. */
	public static double selectMultiplier(final int earthDebuffPercent,
			final int waterDebuffPercent, final int fireDebuffPercent,
			final double earthMultiplier, final double waterMultiplier,
			final double fireMultiplier) {
		if (earthDebuffPercent > 0) return earthMultiplier;
		if (waterDebuffPercent > 0) return waterMultiplier;
		if (fireDebuffPercent > 0) return fireMultiplier;
		return 1.0D;
	}
}
