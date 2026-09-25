package com.openrsc.server.combat;

import com.openrsc.server.content.Slow;
import com.openrsc.server.content.monsterslayer.*;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeUtils;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.handlers.SpellHandler;
import java.lang.reflect.Method;

/** New shared Slow contract, using the production game clock/event scheduler. */
public final class SlowFixture {
 public static void main(String[] args) throws Exception {
  try(CurrentCombatHarness h = new CurrentCombatHarness()) {
   Player p=h.player("slow player",440,440);
   Npc n=h.npc(3,441,440);
   Slow.apply(p,8,Slow.CAP_ONE); eq(8,Slow.power(p),"subthreshold retained"); eq(0,Slow.tier(p),"no early tier");
   Slow.apply(p,8,Slow.CAP_ONE); eq(12,Slow.power(p),"tier1 hard cap");
   Slow.apply(p,12,Slow.CAP_TWO); eq(22,Slow.power(p),"higher cap adopted");
   Slow.apply(p,99,Slow.CAP_ONE); eq(22,Slow.power(p),"lower source uses retained higher cap");
   check(p.getActivePotionEffectStatuses().stream().anyMatch(s->s.getStableKey().equals("combat:slow")&&s.getStableIdentity()==5),"Slow2 HUD");
   long start=h.server().getCurrentTick();
   while(h.server().getCurrentTick()<start+3) h.advanceOneCombatTick();
   Slow.apply(p,1,Slow.CAP_ONE);
   while(h.server().getCurrentTick()<=start+4) h.advanceOneCombatTick();
   eq(20,Slow.power(p),"reapply did not reset four-tick deadline");
   while(h.server().getCurrentTick()<=start+8) h.advanceOneCombatTick();
   eq(18,Slow.power(p),"tier transition"); eq(1,Slow.tier(p),"Slow1");
   Slow.apply(p,2,Slow.CAP_ONE); eq(20,Slow.power(p),"lower source rebuilds retained Slow2");
   while(Slow.power(p)>0) h.advanceOneCombatTick();
   eq(0,Slow.cap(p),"zero releases cap");
   Slow.apply(p,99,Slow.CAP_ONE); eq(12,Slow.power(p),"fresh lower cap");
   p.advanceCombatLifecycle(); eq(0,Slow.power(p),"death/lifecycle clear");
   check(p.getActivePotionEffectStatuses().stream().noneMatch(s->s.getStableKey().equals("combat:slow")),"HUD cleared");
   Slow.apply(n,22,Slow.CAP_TWO); n.advanceCombatLifecycle(); eq(0,Slow.power(n),"NPC respawn does not retain state");
   Player out=h.player("slow logout",442,440); Slow.apply(out,12,Slow.CAP_ONE); h.logout(out); eq(0,Slow.power(out),"logout inactive");
   for(Mob actor:new Mob[]{p,n}) {
    Slow.clear(actor); int range=RangeUtils.getAdjustedRangeDelayTicks(actor,3);
    PvmMeleeEvent event=new PvmMeleeEvent(h.world(),actor,actor==p?n:p);
    Method adjusted=PvmMeleeEvent.class.getDeclaredMethod("getAdjustedMeleeDelayTicks",Mob.class,int.class); adjusted.setAccessible(true);
    int base=(Integer)adjusted.invoke(event,actor,3);
    Slow.apply(actor,22,Slow.CAP_TWO);
    eq(base+2,(Integer)adjusted.invoke(event,actor,3),"player/NPC melee additive");
    eq(range+2,RangeUtils.getAdjustedRangeDelayTicks(actor,3),"player/NPC ranged additive");
   }
   h.equip(p,MyWorldItemId.DAGGER_OF_TERROR,1);
   Method adjusted=PvmMeleeEvent.class.getDeclaredMethod("getAdjustedMeleeDelayTicks",Mob.class,int.class); adjusted.setAccessible(true);
   eq(3,(Integer)adjusted.invoke(new PvmMeleeEvent(h.world(),p,n),p,3),"terror dagger not immune");
   for(int id:new int[]{865,866,868}) {
    Npc special=h.npc(id,445,440); Slow.apply(special,22,Slow.CAP_TWO);
    if(id==865) BansheeCombat.recordAttack(special,3);
    else if(id==866) NagaCombat.recordAttack(special,3);
    else BloodveldCombat.recordAttack(special,3);
    long scheduled=h.server().getCurrentTick(); Slow.clear(special);
    while(h.server().getCurrentTick()<scheduled+4) h.advanceOneCombatTick();
    check(!(id==865?BansheeCombat.attackReady(special):id==866?NagaCombat.attackReady(special):BloodveldCombat.attackReady(special)),"latched special cooldown survives clear");
    h.advanceOneCombatTick();
    check(id==865?BansheeCombat.attackReady(special):id==866?NagaCombat.attackReady(special):BloodveldCombat.attackReady(special),"special ready at five ticks");
   }
   Slow.clear(p); p.setCastTimer(0); Slow.apply(p,22,Slow.CAP_TWO);
   SpellHandler.finalizeSpell(p,h.server().getEntityHandler().getSpellDef(Spells.EARTH_STRIKE),null,false);
   p.setCastTimer(0); // Isolate the latched deadline from existing wall-clock normal cast timer.
   check(!p.combatCastTimer(false),"combat finalizer schedules Slow"); check(p.castTimer(false),"utility cast unaffected");
   Slow.clear(p); check(!p.combatCastTimer(false),"cleansing cannot shorten scheduled cast");
   h.clock().advanceMillis(h.server().getConfig().MILLISECONDS_BETWEEN_CASTS+2*h.server().getConfig().GAME_TICK);
   check(p.combatCastTimer(false),"combat deadline reached");
   p.scheduleCombatCast(); Slow.apply(p,22,Slow.CAP_TWO);
   h.clock().advanceMillis(h.server().getConfig().MILLISECONDS_BETWEEN_CASTS);
   check(p.combatCastTimer(false),"new Slow cannot postpone existing cast");
   for(int rank=1;rank<=4;rank++) {Slow.clear(n); Slow.earthSpell(n,rank*3);eq(2+rank*2,Slow.power(n),"earth rank power");eq(rank<=2?12:22,Slow.cap(n),"earth rank cap");}
   actualMagicGates(h);
   actualNpcProjectileGates(h);
   Npc beast=h.npc(869,480,440); Slow.apply(beast,22,Slow.CAP_TWO);
   long chargeStart=h.server().getCurrentTick(); DarkBeastCombat.startCharge(beast); Slow.clear(beast);
   while(h.server().getCurrentTick()<chargeStart+11)h.advanceOneCombatTick();
   check(!DarkBeastCombat.charging(beast)&&!DarkBeastCombat.attackReady(beast),"charge ended but latched Slow still delays attack");
   while(h.server().getCurrentTick()<chargeStart+13)h.advanceOneCombatTick();
   check(DarkBeastCombat.attackReady(beast),"postcharge extra delay expires");
   Npc demon=h.npc(870,482,440); Slow.apply(demon,22,Slow.CAP_TWO);
   long spikeStart=h.server().getCurrentTick(); AbyssalDemonCombat.startSpikes(demon,(victim,hit)->{}); Slow.clear(demon);
   while(h.server().getCurrentTick()<spikeStart+4)h.advanceOneCombatTick();
   check(!AbyssalDemonCombat.recovering(demon),"Slow does not extend spike animation/movement recovery");
   check(AbyssalDemonCombat.beforeMelee(demon,p,true,(victim,hit)->{}),"pending attack cooldown remains after recovery");
   while(h.server().getCurrentTick()<spikeStart+6)h.advanceOneCombatTick();
   check(!AbyssalDemonCombat.beforeMelee(demon,n,false,(victim,hit)->{}),"stab resumes after latched Slow");
  }
  System.out.println("PASS: shared Slow caps, decay, lifetimes, HUD, melee/range, dagger, special cooldowns, combat cast deadline");
 }
 private static void actualMagicGates(CurrentCombatHarness h) throws Exception {
  h.openCombatProjectileRectangle(440,449,440,445);
  Player p=h.player("slow casting",440,440); Npc n=h.npc(com.openrsc.server.constants.NpcId.GREATER_DEMON.id(),442,440);
  p.getClientLimitations().maxItemId=Integer.MAX_VALUE;
  p.getSkills().setTemporaryLevelAndMaxStat(com.openrsc.server.constants.Skill.MAGIC.id(),99,99,false);
  n.getSkills().setTemporaryLevelAndMaxStat(3,1000,1000,false);
  com.openrsc.server.external.SpellDef def=h.server().getEntityHandler().getSpellDef(Spells.EARTH_STRIKE);
  for(java.util.Map.Entry<Integer,Integer> rune:def.getRunesRequired()) p.getCarriedItems().getInventory().add(new com.openrsc.server.model.container.Item(rune.getKey(),100));
  int rune=def.getRunesRequired().iterator().next().getKey();
  Slow.apply(p,22,Slow.CAP_TWO);
  com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(0);
  com.openrsc.server.net.rsc.struct.incoming.SpellStruct packet=new com.openrsc.server.net.rsc.struct.incoming.SpellStruct();
  packet.setOpcode(com.openrsc.server.net.rsc.enums.OpcodeIn.CAST_ON_NPC); packet.spell=Spells.EARTH_STRIKE; packet.targetIndex=n.getIndex();
  new SpellHandler().process(packet,p);
  check(p.getWalkToAction()!=null&&p.getWalkToAction().shouldExecute(),"manual initial cast queued");
  p.getWalkToAction().execute(); p.setWalkToAction(null);
  long after=p.getCarriedItems().getInventory().countId(rune);
  check(after<100,"manual spell actually committed runes");
  p.setCastTimer(0); Slow.clear(p);
  new SpellHandler().process(packet,p);
  check(p.getWalkToAction()==null&&p.getCarriedItems().getInventory().countId(rune)==after,"manual gate retains scheduled Slow after clear");
  p.setAutoCastSpell(Spells.EARTH_STRIKE);
  com.openrsc.server.event.rsc.impl.projectile.MagicCombatEvent event=new com.openrsc.server.event.rsc.impl.projectile.MagicCombatEvent(h.world(),p,0,n,Spells.EARTH_STRIKE);
  p.setMagicCombatEvent(event); event.run();
  check(p.getWalkToAction()==null&&p.getCarriedItems().getInventory().countId(rune)==after,"autocast gate retains scheduled Slow");
  h.clock().advanceMillis(h.server().getConfig().MILLISECONDS_BETWEEN_CASTS+2*h.server().getConfig().GAME_TICK);
  com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(0);
  event.run();
  check(p.getWalkToAction()!=null&&p.getWalkToAction().shouldExecute(),"autocast resumes at deadline");
  p.getWalkToAction().execute(); p.setWalkToAction(null);
  check(p.getCarriedItems().getInventory().countId(rune)<after,"autocast actually committed");
 }
 private static void actualNpcProjectileGates(CurrentCombatHarness h) throws Exception {
  for(int id:new int[]{863,865}) {
   h.openCombatProjectileRectangle(470,474,440,442);
   Npc npc=h.npc(id,470,440); Player target=h.player("slow shot "+id,473,440);
   target.getSkills().setTemporaryLevelAndMaxStat(3,1000,1000,false);
   npc.setChasing(target); Slow.apply(npc,22,Slow.CAP_TWO);
   h.clock().advanceMillis(10000);
   Method fire=npc.getBehavior().getClass().getDeclaredMethod("tryProjectileAttack",long.class); fire.setAccessible(true);
   fire.invoke(npc.getBehavior(),h.clock().currentTimeMillis());
   long timer=npc.getCombatTimer(); Slow.clear(npc); h.clock().advanceMillis(10000);
   fire.invoke(npc.getBehavior(),h.clock().currentTimeMillis());
   check(timer==npc.getCombatTimer(),"NPC ranged/magic gate holds despite clear and expired legacy millis timer");
   for(int tick=0;tick<9;tick++)h.advanceOneCombatTick();
   fire.invoke(npc.getBehavior(),h.clock().currentTimeMillis());
   check(timer<npc.getCombatTimer(),"NPC ranged/magic resumes at fixed tick deadline");
  }
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static void eq(int expected,int actual,String message){check(expected==actual,message+" expected="+expected+" actual="+actual);}
}
