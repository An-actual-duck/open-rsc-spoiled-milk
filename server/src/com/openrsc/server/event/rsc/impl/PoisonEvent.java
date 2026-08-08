package com.openrsc.server.event.rsc.impl;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Leach;
import com.openrsc.server.content.PoisonPowerReduction;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.world.World;

import java.util.UUID;

public class PoisonEvent extends GameTickEvent {

	private static final int TICK_DELAY = 8;
	private static final int POWER_DRAIN_PER_TICK = 3;

	final private Mob mob;

	private int poisonPower;
	private UUID poisonOwnerId;

	public PoisonEvent(World world, Mob owner, int poisonPower, UUID poisonOwnerId) {
		super(world, owner, TICK_DELAY, "Poison Event", DuplicationStrategy.ONE_PER_MOB);
		this.mob = owner;
		this.poisonPower = poisonPower;
		this.poisonOwnerId = poisonOwnerId;
	}

	@Override
	public void run() {
		if (mob.isNpc() && !((Npc) mob).canReceivePoison()) {
			mob.curePoison();
			return;
		}
		if (PoisonPowerReduction.shouldCure(poisonPower)) {
			mob.curePoison();
			return;
		}
		int damage = (int) Math.round((poisonPower / 10));
		int poisonDrain = POWER_DRAIN_PER_TICK;
		if (mob.isPlayer()) {
			Player player = (Player) mob;
			poisonDrain += player.getCarriedItems().getEquipment().getNatureCleansingPoisonDecayBonus();
		}
		poisonPower -= poisonDrain;
		mob.setPoisonDamage(poisonPower);
		if (mob.isPlayer()) {
			Player player = (Player) mob;
			player.message("@gr3@You @gr2@are @gr1@poisioned! @gr2@You @gr3@lose @gr2@" + damage + " @gr1@health.");
			player.getCache().set("poisoned", poisonPower);
		}
		if (damage > 0) {
			final int actualDamage = mob.damageAndGetActualDamage(damage, HitSplat.TYPE_POISON);
			applyLeach(actualDamage);
		}
	}

	private void applyLeach(final int damage) {
		if (poisonOwnerId == null) {
			return;
		}
		final Player poisonOwner = getWorld().getPlayerByUUID(poisonOwnerId);
		if (poisonOwner == null || poisonOwner.isRemoved() || poisonOwner.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}
		final double leachPercent = poisonOwner.getCarriedItems().getEquipment().getBloodNecklaceLeachPercent();
		if (leachPercent <= 0.0D) {
			return;
		}
		Leach.heal(poisonOwner, damage, leachPercent);
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

	public void setPoisonOwnerId(final UUID poisonOwnerId) {
		this.poisonOwnerId = poisonOwnerId;
	}

	//Part of Poison NPC feature
	public int getPoisonPower() {
		return poisonPower;
	}

}
