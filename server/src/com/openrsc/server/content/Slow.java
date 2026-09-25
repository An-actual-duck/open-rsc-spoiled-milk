package com.openrsc.server.content;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.combat.CombatParticipantSnapshot;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.List;

/** Transient shared combat Slow. Only scheduling code consumes the tier, never movement. */
public final class Slow {
	public static final int POWER_PER_TIER = 10, CAP_BUFFER = 2;
	public static final int CAP_ONE = POWER_PER_TIER + CAP_BUFFER, CAP_TWO = 2 * POWER_PER_TIER + CAP_BUFFER;
	public static final int DECAY_POWER = 2, DECAY_TICKS = 4;
	private static final String KEY = "combat_slow_power";
	private Slow() { }
	private static long tick(Mob mob) { return mob.getWorld().getServer().getCurrentTick(); }
	private static final class State {
		final CombatParticipantSnapshot lifetime;
		int power, cap;
		long nextDecay;
		State(Mob mob) { lifetime = CombatParticipantSnapshot.capture(mob); nextDecay = tick(mob) + DECAY_TICKS; }
	}
	private static boolean live(Mob mob) {
		return mob != null && !mob.isRemoved() && !mob.killed && mob.getLevel(Skill.HITS.id()) > 0
			&& (!(mob instanceof Player) || ((Player)mob).loggedIn());
	}
	private static State state(Mob mob) {
		if (mob == null) return null;
		State state = mob.getAttribute(KEY, null);
		return state != null && state.lifetime.matches(mob) && live(mob) ? state : null;
	}
	public static int power(Mob mob) { State s = state(mob); return s == null ? 0 : s.power; }
	public static int cap(Mob mob) { State s = state(mob); return s == null ? 0 : s.cap; }
	public static int tier(Mob mob) { return power(mob) / POWER_PER_TIER; }
	/** Call once when committing a next-action deadline; do not use in readiness polling. */
	public static int delay(Mob mob, int baseTicks) { return baseTicks + tier(mob); }
	public static void apply(final Mob mob, int amount, int sourceCap) {
		if (!live(mob) || amount <= 0 || sourceCap <= 0) return;
		final int oldTier = tier(mob);
		final int oldPower = power(mob);
		State existing = state(mob);
		if (existing == null) {
			existing = new State(mob);
			mob.setAttribute(KEY, existing);
			final State created = existing;
			mob.getWorld().getServer().getGameEventHandler().add(new GameTickEvent(mob.getWorld(), null, 1,
				"Slow power decay", DuplicationStrategy.ALLOW_MULTIPLE) {
				@Override public void run() {
					if (state(mob) != created) { stop(); return; }
					if (Slow.tick(mob) < created.nextDecay) return;
					int before = tier(mob);
					created.power = Math.max(0, created.power - DECAY_POWER);
					created.nextDecay += DECAY_TICKS;
					if (created.power == 0) { mob.removeAttribute(KEY); stop(); }
					notifyTier(mob, before);
				}
			});
		}
		existing.cap = Math.max(existing.cap, sourceCap);
		existing.power = (int)Math.min(existing.cap, (long)existing.power + amount);
		notifyTier(mob, oldTier);
		// A same-tier top-up can extend the HUD countdown; silently refresh that
		// packet only on an actual change, never send repeated chat/application spam.
		if (mob instanceof Player && tier(mob) > 0 && oldTier == tier(mob) && oldPower != existing.power)
			ActionSender.sendActivePotionEffects((Player)mob);
	}
	private static void notifyTier(Mob mob, int previous) {
		if (mob instanceof Player && previous != tier(mob)) ActionSender.sendActivePotionEffects((Player)mob);
	}
	public static void clear(Mob mob) {
		int previous = tier(mob); mob.removeAttribute(KEY); notifyTier(mob, previous);
	}
	/** Legacy projectile envelope uses elemental rank * 3; preserve its published constructor contract. */
	public static void earthSpell(Mob mob, int legacyStrength) {
		int rank = Math.max(1, Math.min(4, legacyStrength / 3));
		apply(mob, 2 + rank * 2, rank <= 2 ? CAP_ONE : CAP_TWO);
	}
	public static void appendStatus(Player player, List<ActiveStatusEntry> statuses) {
		State s = state(player);
		int tier = tier(player);
		if (s == null || tier == 0) return;
		long pulses = (s.power - tier * POWER_PER_TIER) / DECAY_POWER + 1;
		long ticks = Math.max(1, s.nextDecay - tick(player) + (pulses - 1) * DECAY_TICKS);
		statuses.add(ActiveStatusEntry.slow(tier, Math.max(1, (int)((ticks * player.getConfig().GAME_TICK + 999) / 1000))));
	}
}
