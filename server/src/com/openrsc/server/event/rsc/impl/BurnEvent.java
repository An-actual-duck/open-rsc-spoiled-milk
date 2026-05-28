package com.openrsc.server.event.rsc.impl;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;

public class BurnEvent extends GameTickEvent {

	private final Mob mob;
	private final int burnDamage;
	private int pulsesRemaining;

	public BurnEvent(World world, Mob owner, int burnDamage, int pulsesRemaining) {
		super(world, owner, 8, "Burn Event", DuplicationStrategy.ONE_PER_MOB);
		this.mob = owner;
		this.burnDamage = burnDamage;
		this.pulsesRemaining = pulsesRemaining;
	}

	@Override
	public void run() {
		if (burnDamage <= 0 || pulsesRemaining <= 0) {
			mob.extinguish();
			return;
		}

		pulsesRemaining--;

		if (mob.isPlayer()) {
			Player player = (Player) mob;
			player.message("@or2@You are burning! You lose @or1@" + burnDamage + " @or2@health.");
			if (pulsesRemaining > 0) {
				player.getCache().set("burn_damage", burnDamage);
				player.getCache().set("burn_pulses", pulsesRemaining);
			} else {
				player.getCache().remove("burn_damage");
				player.getCache().remove("burn_pulses");
			}
		}

		mob.damage(burnDamage);

		if (pulsesRemaining <= 0) {
			mob.extinguish();
		}
	}
}
