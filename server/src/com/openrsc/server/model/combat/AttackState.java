package com.openrsc.server.model.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

/** Lifetime-scoped actor authority shared by attack styles, events, and effect sources. */
public final class AttackState {
    private static final String KEY = "shared_attack_state";
    private final CombatParticipantSnapshot lifetime;
    private final StaggerState stagger = new StaggerState();
    private final AttackCadenceState cadence = new AttackCadenceState();
    private AttackState(Mob mob) { lifetime = CombatParticipantSnapshot.capture(mob); }
    private static AttackState state(Mob mob) {
        AttackState state = mob.getAttribute(KEY, null);
        if (state == null || !state.lifetime.matches(mob)) {
            state = new AttackState(mob); mob.setAttribute(KEY, state);
        }
        return state;
    }
    private static boolean live(Mob mob) {
        return mob != null && !mob.isRemoved() && !mob.killed && mob.getLevel(Skill.HITS.id()) > 0
            && (!(mob instanceof Npc) || !((Npc) mob).isRespawning())
            && (!(mob instanceof Player) || ((Player) mob).loggedIn());
    }
    public static boolean stagger(Mob mob, int attacks) {
        return live(mob) && state(mob).stagger.apply(attacks);
    }
    public static AttackCommitment commitAttack(Mob mob) {
        return live(mob) ? state(mob).stagger.commit() : AttackCommitment.normal();
    }
    public static int pendingStaggers(Mob mob) { return state(mob).stagger.pendingAttacks(); }
    public static int recoveryAttacks(Mob mob) { return state(mob).stagger.recoveryAttacks(); }
    public static void clearStagger(Mob mob) { state(mob).stagger.clear(); }
    public static void clear(Mob mob) { if (mob != null) mob.removeAttribute(KEY); }
    public static boolean ready(Mob mob) {
        return state(mob).cadence.ready(mob.getWorld().getServer().getCurrentTick());
    }
    public static void commitCadence(Mob mob, int adjustedTicks) {
        state(mob).cadence.commit(mob.getWorld().getServer().getCurrentTick(), adjustedTicks);
    }
    public static void delay(Mob mob, int ticks) {
        if (live(mob)) state(mob).cadence.addDelay(mob.getWorld().getServer().getCurrentTick(), ticks);
    }
    public static long due(Mob mob) { return state(mob).cadence.due(); }
}
