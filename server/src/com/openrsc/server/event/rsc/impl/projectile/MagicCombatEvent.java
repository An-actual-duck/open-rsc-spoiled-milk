package com.openrsc.server.event.rsc.impl.projectile;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.external.SpellDef;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.action.ActionType;
import com.openrsc.server.model.action.WalkToMobAction;
import com.openrsc.server.model.combat.AttackIntent;
import com.openrsc.server.model.combat.AttackTransactionResult;
import com.openrsc.server.model.combat.CombatParticipantSnapshot;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.PlayerAttackTransaction;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.handlers.SpellHandler;
import com.openrsc.server.util.rsc.MessageType;

public class MagicCombatEvent extends GameTickEvent {
	private final Player player;
	private Mob target;
	private Spells spell;
	private CombatParticipantSnapshot targetSnapshot;

	public MagicCombatEvent(final World world, final Player owner, final long tickDelay, final Mob target, final Spells spell) {
		super(world, owner, tickDelay, "Magic Combat Event", DuplicationStrategy.ONE_PER_MOB);
		this.player = owner;
		this.target = target;
		this.spell = spell;
		this.targetSnapshot = CombatParticipantSnapshot.capture(target);
	}

	public static boolean start(final Player player, final Mob target) {
		return start(player, target, AttackIntent.Source.COMPATIBILITY);
	}

	public static boolean start(final Player player, final Mob target,
			final AttackIntent.Source source) {
		if (player == null || target == null) {
			return false;
		}
		final Spells spell = player.getAutoCastSpell();
		if (!SpellHandler.isAutoCastableSpell(player, spell, true)) {
			player.resetMagicCombat();
			player.setAutoCastSpell(null);
			return false;
		}

		final AttackIntent intent = player.getAttackTransaction().issue(
			target, CombatStyle.MAGIC, AttackIntent.Channel.AUTOCAST,
			source == null ? AttackIntent.Source.COMPATIBILITY : source, spell);
		// A pending manual command wins over auto-retaliation without allowing a
		// fallback melee/ranged counterattack to overwrite it.
		if (intent == null) return true;
		final MagicCombatEvent current = player.getMagicCombatEvent();
		final MagicCombatEvent replacement = current != null && current.isRunning()
			? current : new MagicCombatEvent(player.getWorld(), player, 0, target, spell);
		final AttackTransactionResult committed = player.getAttackTransaction().commit(
			intent, new PlayerAttackTransaction.CommitAction() {
				@Override
				public boolean commit() {
					player.setWalkToAction(null);
					player.resetFollowing();
					player.resetRange();
					if (replacement == current) {
						replacement.reTarget(target, spell);
					} else {
						player.setMagicCombatEvent(replacement);
						player.getWorld().getServer().getGameEventHandler()
							.addOrUpdate(replacement);
					}
					return player.getMagicCombatEvent() == replacement
						&& replacement.isRunning();
				}
			});
		if (committed.isCommitted() && target.isNpc()) {
			Summoning.recordCombatSummonEngagement(player, (Npc) target);
		}
		return committed.isCommitted();
	}

	public Mob getTarget() {
		return target;
	}

	public void reTarget(final Mob target, final Spells spell) {
		this.target = target;
		this.spell = spell;
		this.targetSnapshot = CombatParticipantSnapshot.capture(target);
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
			&& !PathValidation.checkPath(
				player.getWorld(), player.getWorldLocation(),
				target.getWorldLocation(), false)) {
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
		if (player.getMagicCombatEvent() != this
			|| targetSnapshot == null || !targetSnapshot.matches(target)) {
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
