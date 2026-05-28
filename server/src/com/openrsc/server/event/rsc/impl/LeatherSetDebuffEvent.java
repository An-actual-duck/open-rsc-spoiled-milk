package com.openrsc.server.event.rsc.impl;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;

public class LeatherSetDebuffEvent extends GameTickEvent {

	public enum DebuffType {
		BEAR_INTIMIDATE
	}

	private final Mob mob;
	private final DebuffType debuffType;

	public LeatherSetDebuffEvent(final World world, final Mob owner, final DebuffType debuffType) {
		super(world, owner, Mob.ELEMENTAL_DEBUFF_DURATION_TICKS, "Leather Set Debuff Event", DuplicationStrategy.ONE_PER_MOB);
		this.mob = owner;
		this.debuffType = debuffType;
	}

	@Override
	public void run() {
		if (mob.isPlayer()) {
			Player player = (Player) mob;
			if (debuffType == DebuffType.BEAR_INTIMIDATE) {
				player.message("@dre@You steady yourself as the intimidation fades.");
			}
		}
		if (debuffType == DebuffType.BEAR_INTIMIDATE) {
			mob.clearBearIntimidateDebuff();
		}
	}
}
