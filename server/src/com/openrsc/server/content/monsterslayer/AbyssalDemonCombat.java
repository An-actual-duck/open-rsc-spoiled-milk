package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.combat.CombatParticipantSnapshot;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Rapid melee, immediate adjacent spikes, and a target-owned full-action trap. */
public final class AbyssalDemonCombat {
	public static final int NPC_ID = 870, RADIUS = 1, RECOVERY_TICKS = 4;
	public static final long TRAP_MILLIS = 3000, SOLVENT_DRAIN_MILLIS = 5000;
	public static final int IMMUNITY_TICKS = 2;
	public static final String TRAP_MESSAGE = "Its sticky flesh wraps around you, you're trapped";
	public static final String RELEASE_MESSAGE = "You manage to break free";
	private static final String TRAP = "abyssal_flesh_trap", STATE = "abyssal_attack_state";
	private AbyssalDemonCombat() { }

	private static long now(Mob mob) { return mob.getWorld().getServer().getGameClock().currentTimeMillis(); }
	private static long tick(Mob mob) { return mob.getWorld().getServer().getCurrentTick(); }
	public static boolean isDemon(Mob mob) {
		return mob instanceof Npc && mob.getConfig().WANT_MYWORLD && mob.getID() == NPC_ID;
	}
	private static boolean live(Player p) {
		return p.loggedIn() && !p.isRemoved() && !p.killed && p.getLevel(Skill.HITS.id()) > 0;
	}
	private static final class Trap {
		final CombatParticipantSnapshot lifetime;
		final long expires, releaseTick, immuneUntil;
		boolean released;
		Trap(Player player) {
			lifetime = CombatParticipantSnapshot.capture(player);
			expires = now(player) + TRAP_MILLIS;
			releaseTick = tick(player) + (TRAP_MILLIS + player.getConfig().GAME_TICK - 1) / player.getConfig().GAME_TICK;
			immuneUntil = releaseTick + IMMUNITY_TICKS;
		}
	}
	private static Trap trap(Player player) {
		Trap trap = player.getAttribute(TRAP, null);
		return trap != null && trap.lifetime.matches(player) && live(player) ? trap : null;
	}
	public static boolean actionsBlocked(Mob mob) {
		if (!(mob instanceof Player) || !mob.getConfig().WANT_MYWORLD) return false;
		Trap trap = trap((Player)mob);
		return trap != null && now(mob) < trap.expires;
	}
	public static void clearTrap(Player player) { player.removeAttribute(TRAP); }

	/** Called after resolved primary damage: misses, blocked hits, and lethal hits do not trap. */
	public static void onDamage(Mob source, Mob target, int damage) {
		if (!isDemon(source) || !(target instanceof Player) || damage <= 0) return;
		Player player = (Player)target;
		if (!live(player)) return;
		if (SlayerEquipmentEffects.preventsEnemyImmobilization(source, player)) return;
		if (GiantFrogCombat.protectedBySolvent(player)) {
			// Protection covers the hit which consumes its last seconds; the next hit can trap.
			GiantFrogCombat.drainSolvent(player, SOLVENT_DRAIN_MILLIS);
			return;
		}
		Trap old = trap(player);
		if (old != null && (now(player) < old.expires || tick(player) < old.immuneUntil)) return;
		final Trap applied = new Trap(player);
		player.setAttribute(TRAP, applied);
		player.resetPath();
		player.setWalkToAction(null);
		player.resetFollowing();
		player.interruptPlugins();
		player.cancelMenuHandler();
		// Keep combat ownership: incoming attacks must continue, outgoing commits are guarded.
		player.message(TRAP_MESSAGE);
		ActionSender.sendActivePotionEffects(player);
		player.getWorld().getServer().getGameEventHandler().add(new GameTickEvent(
			player.getWorld(), null, 1, "Abyssal flesh release", DuplicationStrategy.ALLOW_MULTIPLE) {
			@Override public void run() {
				if (trap(player) != applied) { stop(); return; }
				if (now(player) < applied.expires) return;
				if (!applied.released) {
					applied.released = true;
					player.message(RELEASE_MESSAGE);
					ActionSender.sendActivePotionEffects(player);
				}
				if (AbyssalDemonCombat.tick(player) >= applied.immuneUntil) { player.removeAttribute(TRAP); stop(); }
			}
		});
	}
	public static void appendStatuses(Player player, List<ActiveStatusEntry> statuses) {
		Trap trap = trap(player);
		if (trap != null && now(player) < trap.expires)
			statuses.add(ActiveStatusEntry.slayer("slayer:abyssal_flesh", 3, GiantFrogCombat.SOLVENT_ITEM_ID,
				(int)((trap.expires - now(player) + 999) / 1000)));
	}

	private static final class State {
		final CombatParticipantSnapshot lifetime;
		long nextAttack, recoveryUntil, nextSpikes;
		State(Npc npc) { lifetime = CombatParticipantSnapshot.capture(npc); }
	}
	private static State state(Npc npc) {
		State state = npc.getAttribute(STATE, null);
		if (state == null || !state.lifetime.matches(npc)) {
			state = new State(npc); npc.setAttribute(STATE, state);
		}
		return state;
	}
	public static boolean recovering(Mob mob) {
		if (!isDemon(mob)) return false;
		State state = state((Npc)mob);
		if (tick(mob) >= state.recoveryUntil) return false;
		mob.resetPath();
		return true;
	}
	/** Called only once melee reach is established. True consumes this attack opportunity. */
	public static boolean beforeMelee(Mob source, Mob target, boolean suppressed, BiConsumer<Player,Integer> strike) {
		if (!isDemon(source)) return false;
		Npc npc = (Npc)source;
		State state = state(npc);
		long tick = tick(npc);
		if (tick < state.nextAttack || recovering(npc)) return true;
		state.nextAttack = tick + 1;
		if (suppressed) return true;
		if (target instanceof Player && tick >= state.nextSpikes
			&& npc.getWorld().getServer().getCombatRandom().nextInt(100) < 20) {
			startSpikes(npc, strike);
			return true;
		}
		npc.getUpdateFlags().setCombatEffect(new CombatEffect(npc, CombatEffect.ABYSSAL_STAB));
		return false;
	}
	/** Immediate hit, not a delayed range check: frame one is a brief visual anticipation only. */
	public static void startSpikes(Npc npc, BiConsumer<Player,Integer> strike) {
		if (!isDemon(npc) || recovering(npc) || npc.isRemoved() || npc.isRespawning() || npc.getLevel(Skill.HITS.id()) <= 0) return;
		final State state = state(npc);
		final long start = tick(npc);
		state.recoveryUntil = start + RECOVERY_TICKS;
		state.nextAttack = state.recoveryUntil;
		state.nextSpikes = state.recoveryUntil + 4;
		npc.resetPath();
		npc.getUpdateFlags().setCombatEffect(new CombatEffect(npc, CombatEffect.ABYSSAL_SPIKES));
		for (Player player : new ArrayList<Player>(npc.getViewArea().getPlayersInView())) {
			if (!state.lifetime.matches(npc) || npc.getLevel(Skill.HITS.id()) <= 0) break;
			if (!live(player) || !npc.sharesSpatialDomain(player) || !npc.withinRange(player, RADIUS)
				|| !PathValidation.checkEnemyCombatProjectilePath(npc.getWorld(), npc.getWorldLocation(), player.getWorldLocation())) continue;
			strike.accept(player, CombatFormula.doMeleeDamage(npc, player));
		}
		npc.consumeAttackBasedDebuffs();
		npc.getWorld().getServer().getGameEventHandler().add(new GameTickEvent(
			npc.getWorld(), null, 1, "Abyssal spikes recovery", DuplicationStrategy.ALLOW_MULTIPLE) {
			@Override public void run() {
				if (!state.lifetime.matches(npc)) { stop(); return; }
				if (npc.isRemoved() || npc.isRespawning() || npc.getLevel(Skill.HITS.id()) <= 0 || AbyssalDemonCombat.tick(npc) >= state.recoveryUntil) {
					// Do not overwrite a fresh stab produced earlier in this same tick.
					CombatEffect pending = npc.getUpdateFlags().getCombatEffect().get();
					if (pending == null || pending.getEffectType() != CombatEffect.ABYSSAL_STAB)
						npc.getUpdateFlags().setCombatEffect(new CombatEffect(npc, CombatEffect.ABYSSAL_CANCEL));
					stop(); return;
				}
				npc.resetPath();
				if (AbyssalDemonCombat.tick(npc) >= start + 2)
					npc.getUpdateFlags().setCombatEffect(new CombatEffect(npc, CombatEffect.ABYSSAL_RISE));
			}
		});
	}
}
