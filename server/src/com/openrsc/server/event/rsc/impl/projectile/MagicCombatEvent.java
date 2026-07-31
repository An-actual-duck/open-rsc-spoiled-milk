package com.openrsc.server.event.rsc.impl.projectile;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.external.SpellDef;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.action.ActionType;
import com.openrsc.server.model.action.WalkToMobAction;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.handlers.SpellHandler;
import com.openrsc.server.util.rsc.MessageType;

public class MagicCombatEvent extends GameTickEvent {
	private final Player player;
	private Mob target;
	private Spells spell;

	public MagicCombatEvent(final World world, final Player owner, final long tickDelay, final Mob target, final Spells spell) {
		super(world, owner, tickDelay, "Magic Combat Event", DuplicationStrategy.ONE_PER_MOB);
		this.player = owner;
		this.target = target;
		this.spell = spell;
	}

	public static boolean start(final Player player, final Mob target) {
		if (player == null || target == null) {
			return false;
		}
		final Spells spell = player.getAutoCastSpell();
		if (!SpellHandler.isAutoCastableSpell(player, spell, true)) {
			player.resetMagicCombat();
			player.setAutoCastSpell(null);
			return false;
		}

		player.setWalkToAction(null);
		player.resetFollowing();
		player.resetRange();
		MagicCombatEvent event = player.getMagicCombatEvent();
		if (event != null && event.isRunning()) {
			event.reTarget(target, spell);
			return true;
		}

		event = new MagicCombatEvent(player.getWorld(), player, 0, target, spell);
		player.setMagicCombatEvent(event);
		player.getWorld().getServer().getGameEventHandler().addOrUpdate(event);
		return true;
	}

	public Mob getTarget() {
		return target;
	}

	public void reTarget(final Mob target, final Spells spell) {
		this.target = target;
		this.spell = spell;
		player.setWalkToAction(null);
		player.resetFollowing();
		setDelayTicks(0);
	}

	public void restart() {
		running = true;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof MagicCombatEvent) {
			MagicCombatEvent e = (MagicCombatEvent) o;
			return e.belongsTo(getOwner());
		}
		return false;
	}

	@Override
	public void run() {
		if (!running) {
			return;
		}
		if (!canContinue()) {
			clearActiveEvent();
			return;
		}
		if (player.getWalkToAction() != null) {
			return;
		}
		if (!player.castTimer(player.getConfig().RAPID_CAST_SPELLS)) {
			return;
		}

		final SpellDef spellDef = player.getWorld().getServer().getEntityHandler().getSpellDef(spell);
		if (spellDef == null || !SpellHandler.hasRequiredRunesForAutoCast(player, spellDef)) {
			player.playerServerMessage(MessageType.QUEST, "You don't have all the reagents you need for this spell");
			clearActiveEvent();
			return;
		}

		final int spellRange = player.getConfig().SPELL_RANGE_DISTANCE + RangeUtils.PLAYER_COMBAT_RANGE_BONUS;
		final int approachRange = RangeUtils.getApproachRadius(spellRange);
		if (!player.withinRange(target, spellRange)) {
			if (getOwner().nextStep(getOwner().getX(), getOwner().getY(), target) == null) {
				player.message("I can't get close enough");
				clearActiveEvent();
				return;
			}
			player.setFollowing(target, approachRange, false);
			player.setWalkToAction(new WalkToMobAction(player, target, approachRange, false, ActionType.ATTACKMAGIC) {
				@Override
				public void executeInternal() {
					getPlayer().resetFollowing();
					getPlayer().setWalkToAction(null);
					MagicCombatEvent.this.setDelayTicks(0);
				}
			});
			return;
		}

		if (player.withinRange(target, spellRange)
			&& !PathValidation.checkPath(player.getWorld(), player.getLocation(), target.getLocation())) {
			player.playerServerMessage(MessageType.QUEST, "I can't get a clear shot from here");
			player.resetPath();
			clearActiveEvent();
			return;
		}

		SpellHandler.queueAutoCastCombatSpell(player, target, spell);
		setDelayTicks(1);
	}

	private boolean canContinue() {
		if (player == null || target == null || spell == null) {
			return false;
		}
		if (!player.loggedIn()
			|| player.getSkills().getLevel(Skill.HITS.id()) <= 0
			|| target.isRemoved()
			|| target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return false;
		}
		if (player.getAutoCastSpell() != spell) {
			return false;
		}
		if (!SpellHandler.isAutoCastableSpell(player, spell, false)) {
			return false;
		}
		return !target.isPlayer() || player.checkAttack(target, true);
	}

	private void clearActiveEvent() {
		stop();
		if (player != null && player.getMagicCombatEvent() == this) {
			player.setMagicCombatEvent(null);
		}
	}
}
