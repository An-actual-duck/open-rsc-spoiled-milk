package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Slow;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeEventNpc;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Real legacy ranged events, production ticks and actor-owned retarget deadlines. */
final class SlowLegacyNpcRange {
    static void run(CurrentCombatHarness h) throws Exception {
        h.openCombatProjectileRectangle(440,445,440,445);
        retarget(h, true);
        retarget(h, false);
        gainAfterOrdinaryShot(h);
        replacementLifetime(h);
        System.out.println("PASS legacy NPC ranged: cleared/gained Slow retarget deadlines, exact due ticks, ordinary cadence and replacement lifetime");
    }

    private static Player victim(CurrentCombatHarness h, String name, int x) {
        Player p=h.player(name,x,440);
        p.getCache().store("setting_auto_retaliate",false);
        p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1000,1000,false);
        return p;
    }

    private static RangeEventNpc event(CurrentCombatHarness h,Npc npc,Player target) {
        RangeEventNpc event=new RangeEventNpc(h.world(),npc,target);
        npc.setRangeEventNpc(event);
        return event;
    }

    private static Set<ProjectileEvent> projectiles(CurrentCombatHarness h) {
        // ProjectileEvent.equals is not launch identity: two distinct shots may compare equal.
        Set<ProjectileEvent> found=Collections.newSetFromMap(new IdentityHashMap<ProjectileEvent,Boolean>());
        for(GameTickEvent e:h.server().getGameEventHandler().getEvents())
            if(e instanceof ProjectileEvent)found.add((ProjectileEvent)e);
        return found;
    }

    private static int newShots(CurrentCombatHarness h,Set<ProjectileEvent> before) {
        int count=0;
        for(ProjectileEvent e:projectiles(h))if(!before.contains(e))count++;
        return count;
    }

    private static void fire(CurrentCombatHarness h,RangeEventNpc event,boolean expected,String reason) {
        Set<ProjectileEvent> before=projectiles(h);
        event.doRun();
        check(newShots(h,before)==(expected?1:0),reason);
    }

    private static void retarget(CurrentCombatHarness h,boolean clear) throws Exception {
        Npc npc=h.npc(3,440,440);
        Player firstTarget=victim(h,clear?"clear first":"gain first",443);
        Player newTarget=victim(h,clear?"clear next":"gain next",444);
        Slow.apply(npc,clear?22:12,clear?22:12);
        RangeEventNpc first=event(h,npc,firstTarget);
        long start=h.server().getCurrentTick();
        fire(h,first,true,"initial slowed legacy shot actually launches");
        int delay=clear?5:4;
        check(first.getDelayTicks()==delay,"legacy shot captures its Slow tier once");
        for(int i=0;i<(clear?3:1);i++)h.advanceOneCombatTick();
        if(clear)Slow.clear(npc);else Slow.apply(npc,22,22);
        RangeEventNpc replacement=event(h,npc,newTarget);
        check(!first.isRunning(),"retarget stops old event ownership");
        while(h.server().getCurrentTick()<start+delay) {
            fire(h,replacement,false,"replacement cannot bypass or recapture committed Slow deadline");
            Set<ProjectileEvent> before=projectiles(h);
            h.advanceOneCombatTick();
            check(newShots(h,before)==0,"production scheduler cannot launch before committed due tick");
        }
        check(h.server().getCurrentTick()==start+delay,"exact original due tick reached");
        fire(h,replacement,true,"retarget resumes exactly at original due tick");
        check(replacement.getDelayTicks()==(clear?3:5),"only the next commitment captures current Slow");
        npc.setRangeEventNpc(null);npc.advanceCombatLifecycle();
    }

    private static void gainAfterOrdinaryShot(CurrentCombatHarness h) throws Exception {
        Npc npc=h.npc(3,440,440);Player p=victim(h,"ordinary gain",443);
        RangeEventNpc first=event(h,npc,p);
        long start=h.server().getCurrentTick();
        fire(h,first,true,"ordinary NPC shot launches");
        check(first.getDelayTicks()==3,"no-Slow ordinary cadence remains three ticks");
        first.stop(); // Isolate its committed delay from scheduler callbacks during retarget.
        h.advanceOneCombatTick();Slow.apply(npc,22,22);
        while(h.server().getCurrentTick()<start+3)h.advanceOneCombatTick();
        RangeEventNpc replacement=event(h,npc,p);
        fire(h,replacement,true,"new Slow cannot postpone an already committed ordinary due tick");
        check(replacement.getDelayTicks()==5,"gained Slow affects the following shot only");
        npc.setRangeEventNpc(null);npc.advanceCombatLifecycle();
    }

    private static void replacementLifetime(CurrentCombatHarness h) throws Exception {
        Npc npc=h.npc(3,440,440);Player p=victim(h,"lifetime next",443);
        Slow.apply(npc,22,22);
        RangeEventNpc first=event(h,npc,p);
        fire(h,first,true,"old lifetime launches slowed shot");
        h.advanceOneCombatTick();
        npc.advanceCombatLifecycle();
        RangeEventNpc replacement=event(h,npc,p);
        fire(h,replacement,true,"new lifetime is not held by old actor deadline");
        check(replacement.getDelayTicks()==3,"new lifetime does not inherit Slow tier");
        npc.setRangeEventNpc(null);npc.advanceCombatLifecycle();
    }

    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }
}
