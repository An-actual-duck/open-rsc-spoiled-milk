package com.openrsc.server.event.rsc.impl;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;

public class ElementalDebuffEvent extends GameTickEvent {

	public enum DebuffType {
		WIND,
		WATER,
		EARTH,
		FIRE
	}

	private final Mob mob;
	private final DebuffType debuffType;

	public ElementalDebuffEvent(World world, Mob owner, DebuffType debuffType) {
		super(world, owner, Mob.ELEMENTAL_DEBUFF_DURATION_TICKS, "Elemental Debuff Event", DuplicationStrategy.ONE_PER_MOB);
		this.mob = owner;
		this.debuffType = debuffType;
	}

	@Override
	public void run() {
		if (mob.isPlayer()) {
			Player player = (Player) mob;
			switch (debuffType) {
				case WIND:
					player.message("@whi@Unsteady fades.");
					break;
				case WATER:
					player.message("@cya@Dampen fades.");
					break;
				case EARTH:
					player.message("@gre@Slow fades.");
					break;
				case FIRE:
					player.message("@red@Scorch fades.");
					break;
			}
		}
		switch (debuffType) {
			case WIND:
				mob.clearWindDebuff();
				break;
			case WATER:
				mob.clearWaterMaxHitDebuff();
				break;
			case EARTH:
				mob.clearEarthAttackSpeedDebuff();
				break;
			case FIRE:
				mob.clearFireDefenseDebuff();
				break;
		}
	}
}
