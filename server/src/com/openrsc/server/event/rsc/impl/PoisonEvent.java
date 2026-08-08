package com.openrsc.server.event.rsc.impl;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Leach;
import com.openrsc.server.content.PoisonPowerReduction;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.combat.dot.PeriodicEffectProvenance;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.model.world.World;

import java.util.UUID;

public class PoisonEvent extends GameTickEvent {

	private static final int TICK_DELAY = 8;
	private static final int POWER_DRAIN_PER_TICK = 3;

	final private Mob mob;

	private int poisonPower;
	private UUID poisonOwnerId;
	private PeriodicEffectProvenance provenance;

	public PoisonEvent(World world, Mob owner, int poisonPower, UUID poisonOwnerId) {
		this(world, owner, poisonPower, poisonOwnerId == null ? null
			: PeriodicEffectProvenance.player(poisonOwnerId), true);
	}

	public PoisonEvent(World world, Mob owner, int poisonPower,
			final PeriodicEffectProvenance provenance,
			final boolean typedProvenance) {
		super(world, owner, TICK_DELAY, "Poison Event", DuplicationStrategy.ONE_PER_MOB);
		this.mob = owner;
		this.poisonPower = poisonPower;
		this.provenance = provenance;
		this.poisonOwnerId = provenance != null && provenance.isPlayer()
			? provenance.getSourceId() : null;
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
		}
		if (damage > 0) {
			final int actualDamage = settleTypedPoisonDamage(damage);
			applyLeach(actualDamage);
		}
	}

	private int settleTypedPoisonDamage(final int requestedDamage) {
		int resolvedDamage = requestedDamage;
		if (mob.isPlayer()) {
			final Player player = (Player) mob;
			player.setAttribute("last_damage_taken_at", System.currentTimeMillis());
			resolvedDamage = player.applyGoblinTenacity(resolvedDamage);
		}
		final Mob source = resolveLiveSource();
		final DamageRequest request = DamageRequest.resolvedLegacy(source, mob,
			DamageRequest.SourceCategory.DOT, "generic-poison", resolvedDamage)
			.eventId(getUUID())
			.hitSplatType(HitSplat.TYPE_POISON)
			.build();
		final DamageResult result = getWorld().getServer()
			.getResolvedDamageTransaction().apply(request);
		if (mob.isPlayer()) {
			ActionSender.sendStat((Player) mob, Skill.HITS.id());
		}
		if (mob.isNpc() && source instanceof Player) {
			((Npc) mob).addCombatDamage((Player) source,
				result.getActualDamage());
		}
		if (result.isTargetTerminal()) {
			if (source != null) {
				mob.killedBy(source);
			} else if (mob.isNpc()) {
				// A source-less periodic kill must still end the NPC lifetime, but
				// must not manufacture player reward/credit from a stale opponent.
				((Npc) mob).remove();
			} else {
				mob.killedBy(null);
			}
		}
		return result.getActualDamage();
	}

	private Mob resolveLiveSource() {
		if (provenance == null || provenance.getSourceId() == null) {
			return null;
		}
		if (provenance.isPlayer()) {
			final Player player = getWorld().getPlayerByUUID(
				provenance.getSourceId());
			return player == null || player.isRemoved()
				|| player.getLevel(Skill.HITS.id()) <= 0 ? null : player;
		}
		if (provenance.getSourceKind()
			== com.openrsc.server.model.combat.dot.PeriodicEffectSourceKind.NPC) {
			final Npc npc = getWorld().getNpcByUUID(provenance.getSourceId());
			return npc == null || npc.isRemoved() ? null : npc;
		}
		return null;
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
		this.provenance = poisonOwnerId == null ? null
			: PeriodicEffectProvenance.player(poisonOwnerId);
	}

	public void setProvenance(final PeriodicEffectProvenance provenance) {
		this.provenance = provenance;
		this.poisonOwnerId = provenance != null && provenance.isPlayer()
			? provenance.getSourceId() : null;
	}

	//Part of Poison NPC feature
	public int getPoisonPower() {
		return poisonPower;
	}

}
