package com.openrsc.server.combat;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.content.Bleed;
/** Actual HP settlement and pulse presentation; style colors must not change combat. */
public final class HitSplatStyleColorFixture {
 static void check(boolean yes,String detail){if(!yes)throw new AssertionError(detail);}
 public static void main(String[] args)throws Exception {
  try(CurrentCombatHarness h=new CurrentCombatHarness()) {
   Player p=h.player("color styles",450,450);Npc n=h.npc(61,451,450);
   n.getSkills().setTemporaryLevelAndMaxStat(3,1000,1000,false);
   CombatStyle[] styles={CombatStyle.MELEE,CombatStyle.RANGED,CombatStyle.THROWING,CombatStyle.MAGIC};
   int[] expected={HitSplat.TYPE_STANDARD,HitSplat.TYPE_RANGED,HitSplat.TYPE_RANGED,HitSplat.TYPE_MAGIC};
   for(int i=0;i<styles.length;i++) {
    int hp=n.getLevel(3),before=n.getUpdateFlags().getHitSplats().size();
    DamageRequest r=DamageRequest.resolvedLegacy(p,n,DamageRequest.SourceCategory.ACTOR,"style-color",5).style(styles[i]).build();
    DamageResult result=h.server().getResolvedDamageTransaction().apply(r);
    check(n.getLevel(3)==hp-5&&result.getActualDamage()==5,"color must not change HP settlement");
    check(n.getUpdateFlags().getHitSplats().size()==before+1&&n.getUpdateFlags().getHitSplats().get(before).getType()==expected[i],"actual direct style color "+styles[i]);
   }
   for(int type:new int[]{HitSplat.TYPE_POISON,HitSplat.TYPE_ARMOR_PROC,HitSplat.TYPE_HEAL,HitSplat.TYPE_SUMMON,HitSplat.TYPE_RECOIL,HitSplat.TYPE_BURN,HitSplat.TYPE_BLEED}) {
    DamageRequest r=DamageRequest.resolvedLegacy(p,n,DamageRequest.SourceCategory.OWNED_EFFECT,"explicit-color",5).style(CombatStyle.MAGIC).hitSplatType(type).build();
    check(HitSplat.typeForDamage(r)==type,"explicit effect/source color wins "+type);
   }
   check(HitSplat.typeForDamage(DamageRequest.resolvedLegacy(p,n,DamageRequest.SourceCategory.DOT,"legacy-dot",1).style(CombatStyle.MAGIC).build())==HitSplat.TYPE_STANDARD,"unclassified DOT is not guessed from originating style");
   int before=n.getUpdateFlags().getHitSplats().size();
   check(Bleed.primaryHit(p,n,2,AttackRoll.normal(10).withWoundBasis(10,10),10),"qualifying player wound");
   for(int i=0;i<12;i++)h.advanceOneCombatTick();
   check(n.getUpdateFlags().getHitSplats().size()==before+3,"exactly three independent bleed pulses");
   for(int i=before;i<before+3;i++) {
    HitSplat pulse=n.getUpdateFlags().getHitSplats().get(i);
    check(pulse.getType()==HitSplat.TYPE_BLEED&&pulse.getAmount()>=3&&pulse.getAmount()<=5,"bleed color with unchanged severity2 damage");
   }
   Npc stirge=h.npc(882,452,450);p.getSkills().setTemporaryLevelAndMaxStat(3,100,100,false);
   before=p.getUpdateFlags().getHitSplats().size();
   check(Bleed.stirgeHit(stirge,p),"Stirge wound applies");
   for(int i=0;i<12;i++)h.advanceOneCombatTick();
   check(p.getUpdateFlags().getHitSplats().size()==before+3,"three NPC-to-player bleed pulses");
   for(int i=before;i<before+3;i++)check(p.getUpdateFlags().getHitSplats().get(i).getType()==HitSplat.TYPE_BLEED,"NPC bleed uses same crimson marker");
  }
  System.out.println("PASS direct style colors, special source precedence, unchanged HP settlement and player/NPC three-pulse bleed colors");
 }
}
