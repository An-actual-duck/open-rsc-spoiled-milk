package com.openrsc.server.model.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.KillType;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.states.HostileState;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.UUID;

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

    /** Automatic/area recipients exclude allies, independently of area-damage suppression. */
    public static boolean eligibleEnemy(Player source,Player target) {
        return source!=target && !PlayerCombatSafety.blocks(source,target)
            && !com.openrsc.server.util.rsc.CombatEffectUtil.arePvpAllies(source,target)
            && CombatEligibility.evaluate(CombatEligibilityRequest.builder(source,target,
                CombatEligibilityPhase.COMPATIBILITY,CombatStyle.MAGIC)
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
        attacker=com.openrsc.server.content.Summoning.killCredit(attacker);
        if (!applies(recipient,attacker) || !recipient.getAutoRetaliate()
            || !eligible(recipient,(Player)attacker,CombatStyle.MELEE)) return;
        Mob current=recipient.getOutgoingCombatTarget();
        if (current!=null && !current.isRemoved() && current.getLevel(Skill.HITS.id())>0) return;
        recipient.startPvmCounterCombat(attacker);
    }

    public static void recordPrimaryDamage(Mob source,Player target,int damage) {
        Mob credited=com.openrsc.server.content.Summoning.killCredit(source);
        if (!(credited instanceof Player) || !applies(credited,target)
                || PlayerCombatSafety.blocks(source,target))return;
        Player owner=(Player)credited;
        target.updateDamageAndBlockedDamageTracking(owner,damage,0);
        com.openrsc.server.content.Summoning.recordOwnerCombatSummonDamage(owner,target,damage);
        owner.setSkulledOn(target);
        target.setCombatTimer();
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
            if(com.openrsc.server.content.Summoning.killCredit(attacker) instanceof Player) return true;
        return false;
    }

    /** Player recipients of an existing PvE secondary-damage family. */
    public static int secondaryDamage(Player source,Mob primary,Player target,int damage,
            CombatStyle style,String effectKey,UUID eventId,AttackRoll roll) {
        if(!com.openrsc.server.util.rsc.CombatEffectUtil
                .isPlayerOwnedPvpSplashTarget(source,primary,target))return 0;
		source.setSkulledOn(target);
		target.setCombatTimer();
        damage=target.applyRobeDamageMitigation(Math.max(0,damage));
        if(style==CombatStyle.MAGIC)damage=target.applyPotionMagicDamageReduction(damage);
        else if(style==CombatStyle.MELEE) damage=target.applyPotionMeleeDamageReduction(damage);
        else if(style==CombatStyle.RANGED || style==CombatStyle.THROWING) damage=target.applyPotionRangedDamageReduction(damage);
        damage=com.openrsc.server.content.TrueDefense.apply(target,damage);
        DamageResult result=target.getWorld().getServer().getResolvedDamageTransaction().apply(
            DamageRequest.resolvedLegacy(source,target,DamageRequest.SourceCategory.OWNED_EFFECT,
                effectKey,damage).eventId(eventId).style(style)
                .hitSplatType(HitSplat.TYPE_STANDARD).attackRoll(roll).build());
        ActionSender.sendStat(target,Skill.HITS.id());
        if(result.isTargetTerminal()) {
            source.setKillType(style==CombatStyle.MAGIC?KillType.MAGIC
                :style==CombatStyle.MELEE?KillType.COMBAT:KillType.RANGED);
            target.killedBy(source);
        } else retaliate(target,source);
        return result.getActualDamage();
    }
}
