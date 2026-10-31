package com.openrsc.server.content;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.combat.CombatParticipantSnapshot;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.List;

/** Transient shared combat Slow. Only committed combat cadence consumes the tier. */
public final class Slow {
	public static final int POWER_PER_TIER = 10, CAP_BUFFER = 2;
	public static final int CAP_ONE = POWER_PER_TIER + CAP_BUFFER;
	public static final int CAP_TWO = 2 * POWER_PER_TIER + CAP_BUFFER;
	public static final int DECAY_POWER = 2, DECAY_TICKS = 4;
	private static final String KEY = "combat_slow_power";

	private Slow() { }
	private static long tick(Mob mob) { return mob.getWorld().getServer().getCurrentTick(); }

	private static final class State {
		final CombatParticipantSnapshot lifetime;
		int power, cap;
		long nextDecay;
		GameTickEvent decay;
		State(Mob mob) {
			lifetime = CombatParticipantSnapshot.capture(mob);
			nextDecay = tick(mob) + DECAY_TICKS;
		}
	}

	private static boolean live(Mob mob) {
		return mob != null && !mob.isRemoved() && !mob.killed && mob.getLevel(Skill.HITS.id()) > 0
			&& (!(mob instanceof Npc) || !((Npc)mob).isRespawning())
			&& (!(mob instanceof Player) || ((Player)mob).loggedIn());
	}

	private static void discard(Mob mob, State state) {
		mob.removeAttribute(KEY);
		if (state.decay != null) state.decay.stop();
	}

	/** Settle due decay before any read/application, independent of scheduler callback order. */
	private static State state(Mob mob) {
		if (mob == null) return null;
		State state = mob.getAttribute(KEY, null);
		if (state == null) return null;
		if (!state.lifetime.matches(mob) || !live(mob)) {
			discard(mob, state);
			return null;
		}
		long now = tick(mob);
		if (now >= state.nextDecay) {
			int previous = state.power / POWER_PER_TIER;
			long pulses = (now - state.nextDecay) / DECAY_TICKS + 1;
			// At most eleven pulses can matter; bound multiplication even after a large clock jump.
			state.power -= (int)Math.min(state.power, Math.min(pulses, CAP_TWO) * DECAY_POWER);
			if (state.power == 0) discard(mob, state);
			else state.nextDecay += pulses * DECAY_TICKS;
			int current = state.power / POWER_PER_TIER;
			if (previous != current) notifyPlayer(mob);
			if (state.power == 0) return null;
		}
		return state;
	}

	public static int power(Mob mob) { State s = state(mob); return s == null ? 0 : s.power; }
	public static int cap(Mob mob) { State s = state(mob); return s == null ? 0 : s.cap; }
	public static int tier(Mob mob) { return power(mob) / POWER_PER_TIER; }

	/** Call once at commitment, never to move a deadline during readiness polling. */
	public static int delay(Mob mob, int baseTicks) {
		return (int)Math.min(Integer.MAX_VALUE, Math.max(1L, baseTicks) + tier(mob));
	}

	public enum Cadence { MELEE, RANGED }
	/** Actor cadence persists even without Slow, across style/event replacement. */
	public static void commit(Mob mob, Cadence cadence, int adjustedTicks) {
        com.openrsc.server.model.combat.AttackState.commitCadence(mob, adjustedTicks);
    }
    public static boolean ready(Mob mob, Cadence cadence) {
        return com.openrsc.server.model.combat.AttackState.ready(mob);
    }

	public static void apply(final Mob mob, int amount, int sourceCap) {
		if (!live(mob) || amount <= 0 || (sourceCap != CAP_ONE && sourceCap != CAP_TWO)) return;
		State existing = state(mob);
		final int oldPower = existing == null ? 0 : existing.power;
		if (existing == null) {
			existing = new State(mob);
			mob.setAttribute(KEY, existing);
			final State created = existing;
			created.decay = new GameTickEvent(mob.getWorld(), null, 1,
				"Slow power decay", DuplicationStrategy.ALLOW_MULTIPLE) {
				@Override public void run() {
					if (mob.getAttribute(KEY, null) != created) { stop(); return; }
					if (state(mob) != created) stop();
				}
			};
			mob.getWorld().getServer().getGameEventHandler().add(created.decay);
		}
		existing.cap = Math.max(existing.cap, sourceCap);
		existing.power = (int)Math.min(existing.cap, (long)existing.power + amount);
		// Tier transitions and visible same-tier top-ups are silent. Capped applications are no-ops.
		if (oldPower != existing.power && existing.power >= POWER_PER_TIER) notifyPlayer(mob);
	}

	private static void notifyPlayer(Mob mob) {
		if (mob instanceof Player) ActionSender.sendActivePotionEffects((Player)mob);
	}

	public static void clear(Mob mob) {
		if (mob == null) return;
		State previous = mob.getAttribute(KEY, null);
		if (previous == null) return;
		discard(mob, previous);
		// Death can already have marked the actor killed; read raw state to clear its visible HUD.
		if (previous.power >= POWER_PER_TIER) notifyPlayer(mob);
	}

	/** The published projectile envelope transports elemental rank * 3, not Slow power. */
	public static void earthSpell(Mob mob, int legacyStrength) {
		if (legacyStrength <= 0) return;
		int rank = Math.max(1, Math.min(4, legacyStrength / 3));
		apply(mob, 2 + rank * 2, rank <= 2 ? CAP_ONE : CAP_TWO);
	}

	public static void appendStatus(Player player, List<ActiveStatusEntry> statuses) {
		State s = state(player);
		int tier = s == null ? 0 : s.power / POWER_PER_TIER;
		if (tier == 0) return;
		long pulses = (s.power - tier * POWER_PER_TIER) / DECAY_POWER + 1;
		long ticks = Math.max(1, s.nextDecay - tick(player) + (pulses - 1) * DECAY_TICKS);
		long seconds = (ticks * player.getConfig().GAME_TICK + 999) / 1000;
		statuses.add(ActiveStatusEntry.slow(tier, (int)Math.max(1, Math.min(Integer.MAX_VALUE, seconds))));
	}
}
