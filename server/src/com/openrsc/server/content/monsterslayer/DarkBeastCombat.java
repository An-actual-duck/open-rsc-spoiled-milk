package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.model.combat.CombatParticipantSnapshot;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcCombatProfile;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;

/** One-shot target acquisition; marks are not a range tether or an immunity buff. */
public final class DarkBeastCombat {
	public static final int NPC_ID = 869, WIPE_ID = 3330, RADIUS = 4, CHARGE_TICKS = 10;
	// Pose-only messages carried by the existing NPC combat-effect update.
	public static final int PULSE_LOW = 78, PULSE_HIGH = 79, DISCHARGE = 80, CANCEL = 81;
	private static final String STATE = "dark_beast_combat", MARKS = "dark_beast_marks";
	private DarkBeastCombat() { }
	private static final class State {
		final CombatParticipantSnapshot lifetime;
		boolean halfHealthUsed;
		long nextDecision, recoveryUntil;
		volatile Charge charge;
		State(Npc npc) { lifetime = CombatParticipantSnapshot.capture(npc); }
	}
	private static State state(Npc npc) {
		State state = npc.getAttribute(STATE, null);
		if (state == null || !state.lifetime.matches(npc)) {
			state = new State(npc); npc.setAttribute(STATE, state);
		}
		return state;
	}
	public static boolean isDarkBeast(Mob mob) {
		return mob instanceof Npc && mob.getConfig().WANT_MYWORLD && mob.getID() == NPC_ID;
	}
	public static boolean charging(Mob mob) {
		if (!isDarkBeast(mob)) return false;
		State state = mob.getAttribute(STATE, null);
		return state != null && state.lifetime.matches(mob) && state.charge != null;
	}
	public static int mitigate(Mob target, int damage) {
		return damage > 0 && charging(target) ? damage / 2 : damage;
	}
	/** Called before chasing or attacking. Random decisions are shared across combat paths. */
	public static boolean tryAttack(Mob source, Mob target) {
		if (!isDarkBeast(source)) return false;
		Npc npc = (Npc) source;
		if (npc.isRemoved() || npc.isRespawning() || npc.getLevel(Skill.HITS.id()) <= 0) return false;
		State state = state(npc);
		long tick = npc.getWorld().getServer().getCurrentTick();
		if (state.charge != null || tick < state.recoveryUntil) { npc.resetPath(); return true; }
		if (!(target instanceof Player) || !live((Player)target) || !npc.sharesSpatialDomain(target)
			|| !npc.withinRange(target, RADIUS)) return false;
		boolean half = !state.halfHealthUsed && npc.getLevel(Skill.HITS.id()) * 2 <= npc.getSkills().getMaxStat(Skill.HITS.id());
		if (!half && tick < state.nextDecision) return false;
		state.nextDecision = tick + 3;
		if (!half && npc.getWorld().getServer().getCombatRandom().nextInt(100) >= 20) return false;
		if (half) state.halfHealthUsed = true;
		npc.face(target);
		startCharge(npc);
		return true;
	}
	private static boolean live(Player player) {
		return player.loggedIn() && !player.isRemoved() && !player.killed && player.getLevel(Skill.HITS.id()) > 0;
	}
	@SuppressWarnings("unchecked")
	private static Map<Charge, CombatParticipantSnapshot> marks(Player player) {
		synchronized (player) {
			Map<Charge, CombatParticipantSnapshot> marks = player.getAttribute(MARKS, null);
			if (marks == null) { marks = new ConcurrentHashMap<>(); player.setAttribute(MARKS, marks); }
			return marks;
		}
	}
	public static int markCount(Player player) {
		Map<Charge, CombatParticipantSnapshot> marks = marks(player);
		marks.entrySet().removeIf(entry -> !entry.getValue().matches(player) || !entry.getKey().valid());
		return marks.size();
	}
	public static int uses(int id) { return id >= WIPE_ID && id <= WIPE_ID + 2 ? WIPE_ID + 3 - id : 0; }
	public static void wipe(Player player) {
		marks(player).clear();
		player.message("You wipe away the static charge.");
		ActionSender.sendActivePotionEffects(player);
	}
	public static void appendStatuses(Player player, List<ActiveStatusEntry> statuses) {
		if (markCount(player) == 0) return;
		long remaining = 0, tick = player.getWorld().getServer().getCurrentTick();
		for (Charge charge : marks(player).keySet()) remaining = Math.max(remaining, charge.due - tick);
		int seconds = (int)Math.max(1, (remaining * player.getConfig().GAME_TICK + 999) / 1000);
		statuses.add(ActiveStatusEntry.item("slayer:static_charge", WIPE_ID, seconds));
	}
	public static void startCharge(Npc npc) {
		if (!isDarkBeast(npc) || charging(npc) || npc.isRemoved() || npc.isRespawning() || npc.getLevel(Skill.HITS.id()) <= 0) return;
		State state = state(npc);
		Charge charge = new Charge(npc, state);
		state.charge = charge;
		npc.resetPath();
		npc.getUpdateFlags().setCombatEffect(new CombatEffect(npc, PULSE_LOW));
		for (Player player : npc.getViewArea().getPlayersInView()) {
			if (!live(player) || !npc.sharesSpatialDomain(player) || !npc.withinRange(player, RADIUS)) continue;
			charge.players.add(player);
			marks(player).put(charge, CombatParticipantSnapshot.capture(player));
			player.message("The air feels tingly");
			ActionSender.sendActivePotionEffects(player);
		}
		npc.getWorld().getServer().getGameEventHandler().add(charge);
	}
	private static final class Charge extends GameTickEvent {
		final Npc npc;
		final State state;
		final long start, due;
		final List<Player> players = new ArrayList<>();
		Charge(Npc npc, State state) {
			// Unowned so death/removal still executes cleanup on all captured players.
			super(npc.getWorld(), null, 1, "Dark beast lightning charge", DuplicationStrategy.ALLOW_MULTIPLE);
			this.npc = npc; this.state = state;
			start = npc.getWorld().getServer().getCurrentTick(); due = start + CHARGE_TICKS;
		}
		boolean valid() {
			return state.lifetime.matches(npc) && !npc.isRemoved() && !npc.isRespawning()
				&& npc.getLevel(Skill.HITS.id()) > 0 && state.charge == this;
		}
		@Override public void run() {
			long tick = npc.getWorld().getServer().getCurrentTick();
			if (!valid()) { finish(false); return; }
			npc.resetPath();
			if (tick < due) {
				npc.getUpdateFlags().setCombatEffect(new CombatEffect(npc, (tick-start)%2 == 0 ? PULSE_LOW : PULSE_HIGH));
				return;
			}
			finish(true);
		}
		private void finish(boolean discharge) {
			stop();
			if (state.lifetime.matches(npc)) {
				npc.getUpdateFlags().setCombatEffect(new CombatEffect(npc, discharge ? DISCHARGE : CANCEL));
			}
			// Drop resistance before retaliation effects are evaluated on release.
			state.charge = null;
			state.recoveryUntil = npc.getWorld().getServer().getCurrentTick() + 1;
			state.nextDecision = state.recoveryUntil + 3;
			for (Player player : players) {
				CombatParticipantSnapshot victim = marks(player).remove(this);
				if (victim == null || !victim.matches(player) || !live(player)) continue;
				ActionSender.sendActivePotionEffects(player);
				if (discharge) strike(npc, player);
			}
			players.clear();
		}
	}
	private static void strike(Npc npc, Player player) {
		player.getUpdateFlags().setCombatEffect(new CombatEffect(player, CombatEffect.THUNDER_STRIKE));
		int damage = CombatFormula.calculateMagicDamage(npc, player, NpcCombatProfile.resolve(npc).getMagicSpellPower());
		damage = player.applyRobeDamageMitigation(damage);
		damage = player.applyPotionMagicDamageReduction(damage);
		damage = com.openrsc.server.content.Summoning.applySummonDamageAbsorption(player, npc, damage);
		damage = com.openrsc.server.content.TrueDefense.apply(player, damage);
		com.openrsc.server.content.cleric.runtime.ClericDirectCombatRuntime.BeforeDamage cleric =
			com.openrsc.server.content.cleric.runtime.ClericDirectCombatRuntime.beforeDirectDamage(npc, player, damage);
		damage = cleric.getDamage();
		DamageResult result = player.getWorld().getServer().getResolvedDamageTransaction().apply(
			DamageRequest.resolvedLegacy(npc, player, DamageRequest.SourceCategory.OWNED_EFFECT, "dark-beast-lightning", damage)
				.style(CombatStyle.MAGIC).build());
		player.updateDamageAndBlockedDamageTracking(npc, result.getActualDamage(), cleric.getPreventedDamage());
		ActionSender.sendStat(player, Skill.HITS.id());
		if (player.getConfig().WANT_PARTIES && player.getParty() != null) player.getParty().sendParty();
		player.setLastOpponent(npc); player.setCombatTimer();
		if (player.getLevel(Skill.HITS.id()) <= 0) player.killedBy(npc);
		else player.checkRingOfLife(npc);
	}
}
