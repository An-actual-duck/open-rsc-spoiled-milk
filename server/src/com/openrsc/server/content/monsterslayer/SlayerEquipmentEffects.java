package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

/** Application-time prevention only; never use this to erase an already-active lock. */
public final class SlayerEquipmentEffects {
	private SlayerEquipmentEffects() { }

	public static boolean preventsEnemyImmobilization(Mob source, Player target) {
		// Player-sourced/PvP immobilization needs a separate design review before PvP returns.
		return source instanceof Npc && target != null && target.getConfig().WANT_MYWORLD
			&& target.getCarriedItems().getEquipment().hasEquipped(MyWorldItemId.SHIELD_OF_MOBILITY);
	}
}
