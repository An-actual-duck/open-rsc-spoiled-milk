package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Slow;
import com.openrsc.server.content.monsterslayer.*;
import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.*;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.enums.OpcodeOut;

/** Additional reconciliation checks over current production clocks, packets and callbacks. */
final class SlowBoundaries {
 private static int assertions;
 static void run(CurrentCombatHarness h) throws Exception {
  pool(h); hud(h); lifetime(h); melee(h); ranged(h);
  System.out.println("PASS Slow reconciliation boundary assertions="+assertions);
 }
 private static void pool(CurrentCombatHarness h) throws Exception {
  Npc n=h.npc(3,460,440);
  for(int power:new int[]{9,10,19,20}) {
   Slow.clear(n); Slow.apply(n,power,Slow.CAP_TWO);
   eq(power,Slow.power(n),"exact threshold power");eq(power/10,Slow.tier(n),"exact threshold tier");
  }
  Slow.clear(n);
  for(int cap:new int[]{Integer.MIN_VALUE,-1,0,1,10,13,20,23,Integer.MAX_VALUE}) Slow.apply(n,Integer.MAX_VALUE,cap);
  for(int amount:new int[]{Integer.MIN_VALUE,-1,0}) Slow.apply(n,amount,12);
  eq(0,Slow.power(n),"invalid input creates no power");
  Slow.apply(n,Integer.MAX_VALUE,12);eq(12,Slow.power(n),"overflow amount bounded");
  Slow.apply(n,Integer.MAX_VALUE,22);eq(22,Slow.power(n),"overflow addition bounded");
  eq(Integer.MAX_VALUE,Slow.delay(n,Integer.MAX_VALUE),"delay saturation");
  Slow.clear(n);Slow.apply(n,1,12);
  long start=h.server().getCurrentTick();
  advanceTo(h,start+3);eq(1,Slow.power(n),"no decay before boundary");
  Slow.apply(n,8,12);eq(9,Slow.power(n),"before-boundary refill");
  advanceTo(h,start+4);
  Slow.apply(n,1,12);eq(8,Slow.power(n),"due decay settles before boundary refill");
  advanceTo(h,start+8);eq(6,Slow.power(n),"refill leaves original next boundary");
  advanceTo(h,start+20);eq(0,Slow.power(n),"subthreshold clamped zero");eq(0,Slow.cap(n),"cap released at zero");
  Slow.apply(n,22,22);for(int i=0;i<200;i++) Slow.apply(n,22,12);
  long events=h.server().getGameEventHandler().getEvents().stream().filter(e->e.isRunning()&&"Slow power decay".equals(e.getDescriptor())).count();
  eq(1,(int)events,"one running decay after repeated applications");
  Slow.clear(n);h.server().getGameEventHandler().cleanupEvents();
  check(h.server().getGameEventHandler().getEvents().stream().noneMatch(e->e.isRunning()&&"Slow power decay".equals(e.getDescriptor())),"cleared event stopped immediately");
 }
 private static void hud(CurrentCombatHarness h) throws Exception {
  Player p=h.player("slow packets",462,440);h.recordOutgoingPackets(p);
  BansheeCombat.applyEarplugs(p);
  int before=packets(h,p);
  Slow.apply(p,9,12);eq(before,packets(h,p),"subthreshold no packet");
  Slow.apply(p,1,12);eq(before+1,packets(h,p),"tier rise packet");
  ActiveStatusEntry row=slowRow(p);eq(4,row.getStableIdentity(),"Slow1 identity");eq(33,row.getIconItemId(),"earth placeholder");
  int duration=row.getRemainingSeconds();
  Slow.apply(p,2,12);eq(before+2,packets(h,p),"same tier topup refreshes once");
  check(slowRow(p).getRemainingSeconds()>duration,"topup projects later tier loss");
  for(int i=0;i<100;i++) Slow.apply(p,100,12);
  eq(before+2,packets(h,p),"capped applications silent");
  Slow.apply(p,10,22);eq(5,slowRow(p).getStableIdentity(),"Slow2 identity");
  long start=h.server().getCurrentTick();advanceTo(h,start+8);eq(4,slowRow(p).getStableIdentity(),"decay transitions to Slow1");
  Slow.clear(p);check(p.getActivePotionEffectStatuses().stream().noneMatch(s->s.getStableKey().equals("combat:slow")),"slow status cleared");
  check(BansheeCombat.protectedByEarplugs(p),"unrelated status preserved");
 }
 private static int packets(CurrentCombatHarness h,Player p)throws Exception {return h.countOutgoingPackets(p,OpcodeOut.SEND_ACTIVE_POTION_EFFECTS);}
 private static ActiveStatusEntry slowRow(Player p){return p.getActivePotionEffectStatuses().stream().filter(s->s.getStableKey().equals("combat:slow")).findFirst().orElseThrow(()->new AssertionError("missing Slow row"));}
 private static void lifetime(CurrentCombatHarness h) throws Exception {
  Player p=h.player("slow session",464,440);Slow.apply(p,22,22);
  Slow.commit(p,Slow.Cadence.MELEE,8);p.sessionId++;
  eq(0,Slow.power(p),"replacement session drops pool");check(Slow.ready(p,Slow.Cadence.MELEE),"replacement session invalidates deadline");
  Slow.apply(p,10,12);advanceTo(h,h.server().getCurrentTick()+4);eq(8,Slow.power(p),"old decay cannot mutate new session");
  h.logout(p);eq(0,Slow.power(p),"actual logout clears");
  Player replacement=h.player("slow session",464,440);eq(0,Slow.power(replacement),"relogin has no transient pool");
  Npc n=h.npc(3,465,440);Slow.apply(n,22,22);n.superRemove();eq(0,Slow.power(n),"actual NPC removal clears");
  Npc respawn=h.npc(3,466,440);Slow.apply(respawn,22,22);respawn.remove();eq(0,Slow.power(respawn),"respawning NPC clears");
  for(int id:new int[]{865,866,868}) {
   Npc special=h.npc(id,467,440);Slow.apply(special,22,22);
   if(id==865)BansheeCombat.recordAttack(special,3);else if(id==866)NagaCombat.recordAttack(special,3);else BloodveldCombat.recordAttack(special,3);
   special.advanceCombatLifecycle();
   check(id==865?BansheeCombat.attackReady(special):id==866?NagaCombat.attackReady(special):BloodveldCombat.attackReady(special),"special lifetime invalidates old cooldown");
  }
  Slow.clear(p);Slow.clear(replacement);
 }
 private static void melee(CurrentCombatHarness h) throws Exception {
  for(int id:new int[]{3,864,865,866,867,868,869,870}) {
   Npc n=h.npc(id,480,440);Player p=h.player("slow melee "+id,481,440);
   p.getCache().store("setting_auto_retaliate",false);
   p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),10000,10000,false);
   boolean open=false;
   for(int x=480;x<520&&!open;x++)for(int y=440;y<480&&!open;y++) {
    n.setLocation(Point.location(x,y));p.setLocation(Point.location(x+1,y));
    open=PathValidation.checkAdjacentDistance(n,p,true,false);
   }
   check(open,"native melee area found");
   // Keep one-shot creature mechanics separate; solvent protects the player, never Slow.
   GiantFrogCombat.applySolvent(p);
   PvmMeleeEvent e=new PvmMeleeEvent(h.world(),n,p);n.setPvmMeleeEvent(e);
   Slow.apply(n,22,22);h.random().scriptInts(99,99,99,99,99,99);
   int before=n.getHitsMade();e.run();
   check(n.getHitsMade()>before,"actual NPC melee committed "+id);
   eq(id==870?3:4,(int)e.getDelayTicks(),"actual slowed NPC melee delay "+id);
   long due=h.server().getCurrentTick()+e.getDelayTicks();Slow.clear(n);
   int hits=n.getHitsMade();
   PvmMeleeEvent replacement=new PvmMeleeEvent(h.world(),n,p);n.setPvmMeleeEvent(replacement);
   replacement.run();eq(hits,n.getHitsMade(),"event replacement cannot bypass latched Slow "+id);
   advanceTo(h,due);h.random().scriptInts(99,99,99,99);replacement.run();
   check(n.getHitsMade()>hits,"NPC ready at captured deadline "+id+" running="+replacement.isRunning()+" current="+n.isCurrentPvmMeleeEvent(replacement)+" hp="+n.getLevel(3)+" removed="+n.isRemoved()+" positions="+n.getLocation()+"/"+p.getLocation()+" slowReady="+Slow.ready(n,Slow.Cadence.MELEE)+" tick="+h.server().getCurrentTick()+" due="+due);
   replacement.stop();n.resetCombatEvent();p.resetCombatEvent();
  }
 }
 private static void ranged(CurrentCombatHarness h) throws Exception {
  for(boolean throwing:new boolean[]{false,true}) {
   Player p=h.player("slow range "+throwing,440,440);Npc n=h.npc(3,442,440);
   h.openCombatProjectileRectangle(440,445,440,445);
   p.getClientLimitations().maxItemId=Integer.MAX_VALUE;
   n.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),10000,10000,false);
   h.equip(p,throwing?ItemId.BRONZE_THROWING_DART.id():ItemId.SHORTBOW.id(),throwing?100:1);
   if(!throwing)h.equip(p,ItemId.TIN_ARROWS.id(),100);
   Slow.apply(p,22,22);
   GameTickEvent e=throwing?new ThrowingEvent(h.world(),p,1,n):new RangeEvent(h.world(),p,1,n);
   if(throwing)p.setThrowingEvent((ThrowingEvent)e);else p.setRangeEvent((RangeEvent)e);
   e.run();long due=p.<Long>getAttribute("can_range_again",0L);
   check(due>=h.server().getCurrentTick()+3,"actual ranged/thrown commitment "+throwing+" weapon="+p.getRangeEquip()+" path="+PathValidation.checkCombatProjectilePath(h.world(),p.getWorldLocation(),n.getWorldLocation())+" running="+e.isRunning()+" due="+due+" tick="+h.server().getCurrentTick());
   Slow.clear(p);
   if(throwing)((ThrowingEvent)e).reTarget(n);else ((RangeEvent)e).reTarget(n);
   advanceTo(h,h.server().getCurrentTick()+1);e.run();
   check(!Slow.ready(p,Slow.Cadence.RANGED),"retarget preserves captured deadline "+throwing);
   advanceTo(h,due);e.run();check(p.<Long>getAttribute("can_range_again",0L)>due,"ranged/thrown resumes at deadline "+throwing);
   e.stop();p.resetRange();
  }
 }
 private static void advanceTo(CurrentCombatHarness h,long tick)throws Exception{while(h.server().getCurrentTick()<tick)h.advanceOneCombatTick();}
 private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
 private static void eq(int expected,int actual,String message){check(expected==actual,message+" expected="+expected+" actual="+actual);}
}
