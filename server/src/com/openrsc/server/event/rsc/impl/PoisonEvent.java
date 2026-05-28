package com.openrsc.server.event.rsc.impl;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.world.World;

public class PoisonEvent extends GameTickEvent {

	private static final int TICK_DELAY = 8;
	private static final int POWER_DRAIN_PER_TICK = 3;

	final private Mob mob;

	private int poisonPower;

	public PoisonEvent(World world, Mob owner, int poisonPower) {
		super(world, owner, TICK_DELAY, "Poison Event", DuplicationStrategy.ALLOW_MULTIPLE);
		this.mob = owner;
		this.poisonPower = poisonPower;
	}

	@Override
	public void run() {
		if (poisonPower < 10) {
			mob.curePoison();
			return;
		}
		int powerBefore = poisonPower;
		int damageBeforeMitigation = (int) Math.round((poisonPower / 10));
		int damage = damageBeforeMitigation;
		int poisonDrain = POWER_DRAIN_PER_TICK;
		if (mob.isPlayer()) {
			Player player = (Player) mob;
			poisonDrain += player.getCarriedItems().getEquipment().getNatureAmuletPoisonDecayBonus();
			damage = Math.max(0, damage - player.getNatureRobePoisonMitigation());
		}
		poisonPower -= poisonDrain;
		mob.setPoisonDamage(poisonPower);
		if (mob.isPlayer()) {
			Player player = (Player) mob;
			player.message("@gr3@You @gr2@are @gr1@poisioned! @gr2@You @gr3@lose @gr2@" + damage + " @gr1@health.");
			player.getCache().set("poisoned", poisonPower);
		}
			if (damage > 0) {
				mob.damage(damage, HitSplat.TYPE_POISON);
			}
	}

	private String describeMob() {
		if (mob.isPlayer()) {
			return "player:" + ((Player) mob).getUsername();
		}
		if (mob.isNpc()) {
			Npc npc = (Npc) mob;
			return "npc:" + npc.getID() + ":\"" + npc.getDef().getName() + "\"";
		}
		return "mob";
	}

	public void setPoisonPower(int int1) {
		poisonPower = int1;
	}

	//Part of Poison NPC feature
	public int getPoisonPower() {
		return poisonPower;
	}

}
