package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.content.Slow;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeEventNpc;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.net.rsc.handlers.SpellHandler;
import com.openrsc.server.util.rsc.DataConversions;
import java.util.Random;

/** Source mapping and existing zero-hit/RNG policies through production effect adapters. */
final class SlowProducers {
 static void run(CurrentCombatHarness h) throws Exception {
  Player source=h.player("slow sources",440,440);Npc target=h.npc(3,442,440);
  target.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1000,1000,false);
  h.openCombatProjectileRectangle(440,445,440,445);
  for(int rank=1;rank<=4;rank++)for(int damage:new int[]{0,1}) {
   Slow.clear(target);
   ProjectileEvent impact=new ProjectileEvent(h.world(),source,target,damage,1,false,0,0,rank*3,0);
   int flight=(int)impact.getDelayTicks();Slow.apply(source,22,22);
   check(impact.getDelayTicks()==flight,"in-flight deadline unchanged by source Slow");
   impact.action();
   check(Slow.power(target)==(damage==0?0:2+rank*2),"earth impact positive/zero policy rank="+rank);
   if(damage>0)check(Slow.cap(target)==(rank<=2?12:22),"earth impact source cap");
   impact.action();check(Slow.power(target)==(damage==0?0:2+rank*2),"duplicate impact does not reapply");
  }
  SpellHandler handler=new SpellHandler();
  for(Spells spell:new Spells[]{Spells.FLAMES_OF_ZAMORAK,Spells.ZAMORAKS_APOCOLYPSE}) {
   boolean advanced=spell==Spells.ZAMORAKS_APOCOLYPSE;
   for(int damage:new int[]{0,1}) {
    Slow.clear(target);
    CurrentCombatHarness.invokePrivate(handler,"applyGodSpellSpecialEffect",new Class<?>[]{Player.class,Mob.class,Spells.class,int.class,boolean.class},source,target,spell,damage,true);
    check(Slow.power(target)==(damage==0?0:advanced?12:8),"Withering positive/zero policy");
   }
   target.getSkills().setLevel(Skill.HITS.id(),0);Slow.clear(target);
   CurrentCombatHarness.invokePrivate(handler,"applyGodSpellSpecialEffect",new Class<?>[]{Player.class,Mob.class,Spells.class,int.class,boolean.class},source,target,spell,1,true);
   check(Slow.power(target)==0,"Withering requires survivor");target.getSkills().setLevel(Skill.HITS.id(),1000);
  }
  Slow.clear(target);
  h.random().reset(123);h.random().scriptDoubles(0.049999,0.05);
  check(CombatFormula.rollElementalSwordProcChance(source),"earth sword existing 5 percent inclusive lower roll");
  check(!CombatFormula.rollElementalSwordProcChance(source),"earth sword existing 5 percent upper boundary");
  String transcript=h.random().describeState();
  CombatFormula.applyElementalSwordProcDebuff(target,CombatEffect.EARTH_SWORD);
  check(Slow.power(target)==10&&Slow.cap(target)==12,"earth sword maps to shared pool");
  check(transcript.equals(h.random().describeState()),"Slow adapter adds no sword random draws");
  for(int id=1895;id<=1899;id++)h.equip(source,id,1);
  check(source.hasFullMossGiantSet(),"grandfathered moss set fixture");
  long success=0;while(new Random(success).nextDouble()>=0.2)success++;
  for(long seed:new long[]{success,0}) {
   Random expected=new Random(seed);boolean proc=expected.nextDouble()<0.2;
   DataConversions.getRandom().setSeed(seed);Slow.clear(target);source.applyElementalGiantMightDebuff(target);
   check(Slow.power(target)==(proc?10:0),"moss existing 20 percent proc");
   check(DataConversions.getRandom().nextLong()==expected.nextLong(),"moss consumes exactly its original chance draw");
  }
  Slow.clear(source);Slow.clear(target);
  Npc legacy=h.npc(3,440,440);Player victim=h.player("slow legacy shot",443,440);
  victim.getCache().store("setting_auto_retaliate",false);
  victim.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1000,1000,false);
  RangeEventNpc legacyEvent=new RangeEventNpc(h.world(),legacy,victim);legacy.setRangeEventNpc(legacyEvent);
  Slow.apply(legacy,22,22);legacyEvent.run();
  check(legacyEvent.getDelayTicks()==5,"actual legacy NPC ranged action captures two Slow ticks");
  Slow.clear(legacy);check(legacyEvent.getDelayTicks()==5,"legacy NPC deadline remains after clearing");
  check(h.server().getGameEventHandler().getEvents().stream().anyMatch(e->e instanceof ProjectileEvent
   && ((ProjectileEvent)e).getLaunchSnapshot().getSourceSnapshot().matches(legacy)),"legacy NPC owns its launched projectile");
  legacyEvent.stop();legacy.setRangeEventNpc(null);
  System.out.println("PASS Slow source adapters: earth impacts 4 ranks/zero/duplicate, Withering, sword and moss RNG");
 }
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
