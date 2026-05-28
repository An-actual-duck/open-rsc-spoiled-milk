package com.openrsc.server.event.rsc.impl;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;

public class WaterSlowEvent extends GameTickEvent {

	private final Mob mob;

	public WaterSlowEvent(World world, Mob owner) {
		super(world, owner, 24, "Water Slow Event", DuplicationStrategy.ONE_PER_MOB);
		this.mob = owner;
	}

	@Override
	public void run() {
		if (mob.isPlayer()) {
			Player player = (Player) mob;
			player.message("@cya@The slowing water magic wears off.");
		}
		mob.clearWaterSlow();
	}
}
