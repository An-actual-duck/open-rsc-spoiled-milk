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
        Player player=(Player)source;
        synchronized(target) {
            Wounds wounds=target.getAttribute(KEY,null);
            if(wounds==null) {wounds=new Wounds();target.setAttribute(KEY,wounds);}
            Wound existing=wounds.bySource.get(source.getUUID());
            if(existing!=null&&existing.isRunning()&&existing.valid(player)) {
                existing.remaining=PULSES;
                existing.severity=Math.max(existing.severity,severity);
                player.message("You reopen the wound");
                return true;
            }
            if(existing!=null)existing.stop();
            Wound wound=new Wound(player,target,severity);
            if(!target.getWorld().getServer().getGameEventHandler().add(wound))return false;
            wounds.bySource.put(source.getUUID(),wound);
            player.message(severity==1?"You open up a minor wound":severity==2?"You open up a wound":"You open up a sizable wound");
            return true;
        }
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
        private final Player sourceSession;
        private final long sourceLife,targetLife;
        private int severity,remaining=PULSES;
        Wound(Player source,Mob target,int severity) {
            super(target.getWorld(),target,PULSE_TICKS,"Bleed wound",DuplicationStrategy.ALLOW_MULTIPLE);
            this.target=target;sourceSession=source;sourceId=source.getUUID();sourceLife=source.getCombatLifecycle();
            targetLife=target.getCombatLifecycle();this.severity=severity;
        }
        boolean valid(Player source) {
            return source==sourceSession&&alive(source)&&alive(target)&&source.getCombatLifecycle()==sourceLife
                &&target.getCombatLifecycle()==targetLife&&source.sharesSpatialDomain(target)
                &&(!(target instanceof Player)||PlayerPvpCombat.eligible(source,(Player)target,CombatStyle.MELEE));
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
                Player source=getWorld().getPlayerByUUID(sourceId);
                Wounds wounds=target.getAttribute(KEY,null);
                if(wounds==null||wounds.bySource.get(sourceId)!=this||!valid(source)) {finish();return;}
                int low=2*severity-1;
                int damage=getWorld().getServer().getCombatRandom().nextIntInclusive(low,low+2);
                DamageResult result=getWorld().getServer().getResolvedDamageTransaction().apply(
                    DamageRequest.resolvedLegacy(source,target,DamageRequest.SourceCategory.DOT,"bleed-wound",
                        com.openrsc.server.content.monsterslayer.DarkBeastCombat.mitigate(target,damage))
                        .periodicProvenance(PeriodicEffectProvenance.player(sourceId))
                        .eventId(getUUID()).style(CombatStyle.MELEE).harmNature(CombatHarmNature.NONMAGICAL)
                        .hitSplatType(HitSplat.TYPE_STANDARD).build());
                if(target instanceof Npc)((Npc)target).addCombatDamage(source,result.getActualDamage());
                else {ActionSender.sendStat((Player)target,Skill.HITS.id());PlayerPvpCombat.recordPrimaryDamage(source,(Player)target,result.getActualDamage());}
                if(result.isTargetTerminal()) {
                    finish();source.setKillType(KillType.COMBAT);target.killedBy(source);return;
                }
                if(--remaining<=0)finish();
            }
        }
    }
}
