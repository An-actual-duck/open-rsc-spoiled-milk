package com.openrsc.server.model.combat;

import com.openrsc.server.content.ElderGreenDragonArmorEffect;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.runtime.GameRandom;

/** Shared trigger policy for the content-owned Elder Green Dragon armor effect. */
public final class ElderGreenDragonArmorProc {
	private ElderGreenDragonArmorProc() {
	}

	@FunctionalInterface
	public interface ProcPayload {
		void apply(int primaryDamageRoll);
	}

	/**
	 * Applies the identical positive-primary chance and payload trigger.
	 *
	 * <p>The callback remains responsible for all Elder Breath content: source
	 * and target validation, settlement, burn, target selection, and lifecycle.</p>
	 */
	public static boolean tryApply(final Player source,
			final int primaryDamage, final GameRandom random,
			final ProcPayload procPayload) {
		if (primaryDamage <= 0
				|| source.getElderGreenDragonArmorProcChance() <= 0.0D) {
			return false;
		}
		if (random.nextDouble() >= source.getElderGreenDragonArmorProcChance()) {
			return false;
		}
		procPayload.apply(random.nextIntInclusive(0,
			ElderGreenDragonArmorEffect.MAX_TRUE_DAMAGE));
		return true;
	}
}
