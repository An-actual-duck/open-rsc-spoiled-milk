package com.openrsc.server.content;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.combat.dot.PeriodicEffectProvenance;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.KillType;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Separate three-pulse wound per player/target lifetime. No secondary-hit callbacks. */
public final class Bleed {
    public static final int THRESHOLD_PERCENT=70, PULSE_TICKS=3, PULSES=3;
    private static final String KEY="combat_bleed_wounds";
    private static final class Wounds { final Map<UUID,Wound> bySource=new HashMap<>(); }
    private Bleed() { }

    public static boolean qualifies(AttackRoll roll,int actualDamage) {
        return roll!=null && actualDamage>0 && roll.getWoundMaximum()>0
            && roll.getWoundRoll()>0 && (roll.isCritical()
                || (long)roll.getWoundRoll()*100 >= (long)roll.getWoundMaximum()*THRESHOLD_PERCENT);
    }

    private static boolean alive(Mob m) {
        return m!=null && !m.isRemoved() && !m.killed && m.getLevel(Skill.HITS.id())>0
            && (!(m instanceof Npc)||!((Npc)m).isRespawning())
            && (!(m instanceof Player)||((Player)m).loggedIn());
    }

    /** Call only for a committed, unsuppressed primary attack from a bleed-capable source. */
    public static boolean primaryHit(Mob source,Mob target,int severity,AttackRoll roll,int actualDamage) {
        if(!(source instanceof Player)||severity<1||severity>3||!source.getConfig().WANT_MYWORLD
            ||!qualifies(roll,actualDamage)||!alive(source)||!alive(target)||source==target
            ||!source.sharesSpatialDomain(target)
            ||NonCorporealState.blocks(source,target,CombatHarmNature.NONMAGICAL)
            ||target instanceof Player&&!PlayerPvpCombat.eligible((Player)source,(Player)target,CombatStyle.MELEE))return false;
        return applyWound(source,target,severity,false);
    }

    /** Stirge gimmick: every positive primary hit escalates its own wound; no player roll threshold. */
    public static boolean stirgeHit(Npc source,Player target) {
        if(!com.openrsc.server.content.monsterslayer.StirgeCombat.isStirge(source)
            ||com.openrsc.server.content.monsterslayer.StirgeCombat.protectedByBugOff(target)
            ||!alive(source)||!alive(target)||!source.sharesSpatialDomain(target)
            ||NonCorporealState.blocks(source,target,CombatHarmNature.NONMAGICAL))return false;
        return applyWound(source,target,1,true);
    }
    private static boolean applyWound(Mob source,Mob target,int severity,boolean escalating) {
        synchronized(target) {
            Wounds wounds=target.getAttribute(KEY,null);
            if(wounds==null){wounds=new Wounds();target.setAttribute(KEY,wounds);}
            Wound existing=wounds.bySource.get(source.getUUID());
            if(existing!=null&&existing.isRunning()&&existing.valid(source)) {
                existing.remaining=PULSES;
                existing.severity=escalating?Math.min(3,existing.severity+1):Math.max(existing.severity,severity);
                if(source instanceof Player)((Player)source).message("You reopen the wound");
                else ((Player)target).message("The stirge deepens your wound (Bleed "+existing.severity+").");
                return true;
            }
            if(existing!=null)existing.stop();
            Wound wound=new Wound(source,target,severity);
            if(!target.getWorld().getServer().getGameEventHandler().add(wound))return false;
            wounds.bySource.put(source.getUUID(),wound);
            if(source instanceof Player)((Player)source).message(severity==1?"You open up a minor wound":severity==2?"You open up a wound":"You open up a sizable wound");
            else ((Player)target).message("The stirge opens a bleeding wound.");
            return true;
        }
    }
    public static void clearStirgeWounds(Player target) {
        synchronized(target) {
            Wounds wounds=target.getAttribute(KEY,null);
            if(wounds!=null)for(Wound wound:new java.util.ArrayList<Wound>(wounds.bySource.values()))
                if(com.openrsc.server.content.monsterslayer.StirgeCombat.isStirge(wound.sourceSession))wound.finish();
        }
    }
    public static int severity(Mob source,Mob target) {
        synchronized(target){Wounds wounds=target.getAttribute(KEY,null);Wound wound=wounds==null?null:wounds.bySource.get(source.getUUID());return wound==null?0:wound.severity;}
    }

    public static int activeWounds(Mob target) {
        synchronized(target) {
            Wounds wounds=target.getAttribute(KEY,null);
            return wounds==null?0:wounds.bySource.size();
        }
    }

    public static void clear(Mob target) {
        synchronized(target) {
            Wounds wounds=target.getAttribute(KEY,null);
            target.removeAttribute(KEY);
            if(wounds!=null)for(Wound wound:wounds.bySource.values())wound.stop();
        }
    }

    private static final class Wound extends GameTickEvent {
        private final Mob target;
        private final UUID sourceId;
        private final Mob sourceSession;
        private final long sourceLife,targetLife;
        private int severity,remaining=PULSES;
        Wound(Mob source,Mob target,int severity) {
            super(target.getWorld(),target,PULSE_TICKS,"Bleed wound",DuplicationStrategy.ALLOW_MULTIPLE);
            this.target=target;sourceSession=source;sourceId=source.getUUID();sourceLife=source.getCombatLifecycle();
            targetLife=target.getCombatLifecycle();this.severity=severity;
        }
        boolean valid(Mob source) {
            return source==sourceSession&&alive(source)&&alive(target)&&source.getCombatLifecycle()==sourceLife
                &&target.getCombatLifecycle()==targetLife&&source.sharesSpatialDomain(target)
                &&(!(source instanceof Player)||!(target instanceof Player)||PlayerPvpCombat.eligible((Player)source,(Player)target,CombatStyle.MELEE))
                &&(!(source instanceof Npc)||!(target instanceof Player)||!com.openrsc.server.content.monsterslayer.StirgeCombat.protectedByBugOff((Player)target));
        }
        private void finish() {
            stop();
            Wounds wounds=target.getAttribute(KEY,null);
            if(wounds!=null&&wounds.bySource.get(sourceId)==this) {
                wounds.bySource.remove(sourceId);
                if(wounds.bySource.isEmpty())target.removeAttribute(KEY);
            }
        }
        public void run() {
            synchronized(target) {
                Mob source=sourceSession instanceof Player?getWorld().getPlayerByUUID(sourceId):sourceSession;
                Wounds wounds=target.getAttribute(KEY,null);
                if(wounds==null||wounds.bySource.get(sourceId)!=this||!valid(source)) {finish();return;}
                int low=2*severity-1;
                int damage=source instanceof Player?CombatLuck.higher((Player)source,getWorld().getServer().getCombatRandom(),
                    () -> getWorld().getServer().getCombatRandom().nextIntInclusive(low,low+2))
                    :getWorld().getServer().getCombatRandom().nextIntInclusive(low,low+2);
                DamageResult result=getWorld().getServer().getResolvedDamageTransaction().apply(
                    DamageRequest.resolvedLegacy(source,target,DamageRequest.SourceCategory.DOT,"bleed-wound",
                        com.openrsc.server.content.monsterslayer.DarkBeastCombat.mitigate(target,damage))
                        .periodicProvenance(source instanceof Player?PeriodicEffectProvenance.player(sourceId):PeriodicEffectProvenance.npc(sourceId,sourceLife))
                        .eventId(getUUID()).style(CombatStyle.MELEE).harmNature(CombatHarmNature.NONMAGICAL)
                        .hitSplatType(HitSplat.TYPE_STANDARD).build());
                if(target instanceof Npc&&source instanceof Player)((Npc)target).addCombatDamage((Player)source,result.getActualDamage());
                else {ActionSender.sendStat((Player)target,Skill.HITS.id());if(source instanceof Player)PlayerPvpCombat.recordPrimaryDamage(source,(Player)target,result.getActualDamage());}
                com.openrsc.server.content.monsterslayer.StirgeCombat.drink(source,result.getActualDamage());
                if(result.isTargetTerminal()) {
                    finish();source.setKillType(KillType.COMBAT);target.killedBy(source);return;
                }
                if(--remaining<=0)finish();
            }
        }
    }
}
