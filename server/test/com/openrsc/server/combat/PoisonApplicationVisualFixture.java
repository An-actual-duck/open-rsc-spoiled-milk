package com.openrsc.server.combat;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.combat.dot.PoisonTargetState;
/** Shared application hook: real target state and event lifecycle; no live accounts. */
public final class PoisonApplicationVisualFixture {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static long visuals(Mob target){return target.getUpdateFlags().getCombatEffects().stream().filter(e->e.getEffectType()==CombatEffect.POISON_APPLIED).count();}
 public static void main(String[]args)throws Exception{
  try(CurrentCombatHarness h=new CurrentCombatHarness()){
   Player source=h.player("poison source",450,450),target=h.player("poison target",451,450);
   h.recordOutgoingPackets(source);h.recordOutgoingPackets(target);
   source.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1000,1000,false);
   target.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1000,1000,false);
   Npc npc=h.npc(2,451,451);npc.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1000,1000,false);
   npc.applyPoison(0,100,source);check(visuals(npc)==0,"invalid input silent");
   npc.applyPoison(40,100,source);check(visuals(npc)==1&&npc.getCurrentPoisonPower()==40,"player poisons NPC");
   npc.applyPoison(40,100,source);check(visuals(npc)==2&&npc.getCurrentPoisonPower()==80,"extra dose strengthens poison and animates");
   npc.applyPoison(40,100,source);npc.applyPoison(40,100,source);
   check(visuals(npc)==4&&npc.getCurrentPoisonPower()==100,"accepted capped reapplication animates without changing cap");
   target.applyPoison(40,100,npc);check(visuals(target)==1&&target.getCurrentPoisonPower()==40,"NPC poisons player");
   npc.getUpdateFlags().reset();target.getUpdateFlags().reset();
   for(int i=0;i<8;i++)h.advanceOneCombatTick();
   check(visuals(npc)==0&&visuals(target)==0,"poison pulses do not replay application");
   Player restored=h.player("poison restored",452,450);h.recordOutgoingPackets(restored);
   restored.restoreDurablePoison(PoisonTargetState.of(40,100,null));
   check(visuals(restored)==0&&restored.getCurrentPoisonPower()==40,"login restoration is silent");
   Npc dead=h.npc(2,452,451);dead.killed=true;dead.applyPoison(40,100,source);
   check(visuals(dead)==0&&dead.getCurrentPoisonPower()==0,"ineligible NPC silent");
   CombatEffect effect=new CombatEffect(target,CombatEffect.POISON_APPLIED);
   source.setClientVersion(10061);check(!effect.isVisibleTo(source),"prior client omits unsupported effect");
   source.setClientVersion(10062);target.setClientVersion(10062);
   check(effect.isVisibleTo(source)&&effect.isVisibleTo(target),"new observers see poison on player");
   source.setClientVersion(235);check(!effect.isVisibleTo(source),"classic protocol omits custom effect");
  }
  System.out.println("PASS "+checks+" poison application, reapplication, pulse, restore and viewer checks");
 }
}
