package com.openrsc.server.model.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.states.HostileState;
import com.openrsc.server.net.rsc.ActionSender;

/** MyWorld player combat uses the same actor-owned melee event as PvM. */
public final class PlayerPvpCombat {
    private PlayerPvpCombat() { }

    public static boolean applies(Mob source, Mob target) {
        return source instanceof Player && target instanceof Player
            && source.getConfig().WANT_MYWORLD;
    }

    public static boolean eligible(Player source, Player target, CombatStyle style) {
        return CombatEligibility.evaluate(CombatEligibilityRequest.builder(source, target,
            CombatEligibilityPhase.COMMIT, style)
            .currentParticipants(true).registration(true).sameSpatialDomain(true)
            .summonRules(true).playerAttackRules(true).build()).isAllowed();
    }

    public static void startMelee(Player actor, Player target) {
        if (!applies(actor,target) || !eligible(actor,target,CombatStyle.MELEE)) return;
        PvmMeleeEvent current=actor.getPvmMeleeEvent();
        if (current!=null && current.isRunning() && current.getTarget()==target) return;
        boolean alreadyIncoming=target.hasIncomingAttackFrom(actor);
        actor.resetAll();
        actor.resetPath();
        actor.resetRange();
        actor.resetFollowing();
        actor.setOpponent(target);
        actor.setHostile(target,HostileState.HostilityType.PROVOKED);
        actor.setCombatTimer();
        actor.setSkulledOn(target);
        target.setCombatTimer();
        target.getTrade().resetAll();
        if (target.isSleeping()) {
            ActionSender.sendWakeUp(target,false,false);
            ActionSender.sendFatigue(target);
        }
        PvmMeleeEvent event=new PvmMeleeEvent(actor.getWorld(),actor,target);
        actor.setPvmMeleeEvent(event);
        actor.getWorld().getServer().getGameEventHandler().addOrUpdate(event);
        if (!alreadyIncoming) {
            target.message("You are under attack!");
            ActionSender.sendSound(target,"underattack");
        }
        retaliate(target,actor);
    }

    /** Incoming players never overwrite a living manual or automatic target. */
    public static void retaliate(Player recipient, Mob attacker) {
        if (!applies(recipient,attacker) || !recipient.getAutoRetaliate()
            || !eligible(recipient,(Player)attacker,CombatStyle.MELEE)) return;
        Mob current=recipient.getOutgoingCombatTarget();
        if (current!=null && !current.isRemoved() && current.getLevel(Skill.HITS.id())>0) return;
        recipient.startPvmCounterCombat(attacker);
    }

    public static boolean canReplaceMelee(Player actor, Mob target) {
        PvmMeleeEvent current=actor.getPvmMeleeEvent();
        return actor.getConfig().WANT_MYWORLD && current!=null
            && (target instanceof Player || current.getTarget() instanceof Player);
    }

    public static boolean hasPlayerEngagement(Player player) {
        if (!player.getConfig().WANT_MYWORLD) return false;
        if (player.getOutgoingCombatTarget() instanceof Player) return true;
        for (Mob attacker:player.getIncomingCombatAttackers())
            if(attacker instanceof Player) return true;
        return false;
    }
}
