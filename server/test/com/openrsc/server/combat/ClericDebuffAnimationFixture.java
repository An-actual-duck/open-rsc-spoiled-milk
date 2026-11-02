package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.cleric.ClericResourceCost;
import com.openrsc.server.content.cleric.ClericSpellCatalog;
import com.openrsc.server.content.cleric.ClericSpellDefinition;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.ClericBlessingContract;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Real hostile casts and NPC appearance encoders: no listeners, accounts or map changes. */
public final class ClericDebuffAnimationFixture {
  static int checks;
  static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  static void appearances(CurrentCombatHarness h,Player viewer,Npc first,Npc second,int animation,boolean ordinary)throws Exception{
    ClericBuffAnimationFixture.packets(viewer).clear();
    Method m=h.server().getGameUpdater().getClass().getDeclaredMethod("updateNpcAppearances",Player.class);m.setAccessible(true);m.invoke(h.server().getGameUpdater(),viewer);
    List<String> actual=new ArrayList<String>();
    for(Packet packet:ClericBuffAnimationFixture.packets(viewer))if(packet.getID()==104){
      io.netty.buffer.ByteBuf b=packet.getBuffer().duplicate();int records=b.readUnsignedShort();
      for(int i=0;i<records;i++){
        int index=b.readUnsignedShort(),type=b.readUnsignedByte();
        if(type==10)actual.add(index+":"+b.readUnsignedByte());
        else if(type==16)b.readUnsignedByte(); // Independent phase baseline, never a VFX record.
        else throw new AssertionError("Unexpected appearance record "+type);
      }
      check(b.readableBytes()==0,"packet counts match exact serialized records");
    }
    List<String>want=new ArrayList<String>();
    if(animation>0){want.add(first.getIndex()+":"+animation);if(ordinary)want.add(first.getIndex()+":34");want.add(second.getIndex()+":"+animation);}
    check(actual.equals(want),"observer sees each NPC sequence and normal effects exactly once: "+actual);
  }
  public static void main(String[]args)throws Exception{
    try(CurrentCombatHarness h=new CurrentCombatHarness()){
      h.server().getConfig().WANT_MYWORLD=true;h.server().getConfig().WANT_FATIGUE=false;
      h.world().getRegionManager().preflightNativeLayeredPlacements();h.world().getRegionManager().populateNativeLayeredPlacements();
      Player caster=h.player("cursevisualcaster",450,450),one=h.player("cursevisualone",449,450),two=h.player("cursevisualtwo",450,449);
      for(Player p:new Player[]{caster,one,two}){p.setClientVersion(ClericBlessingContract.CLIENT_VERSION);p.getClientLimitations().maxItemId=65535;h.recordOutgoingPackets(p);ClericBlessingsFixture.skill(p,Skill.PRAYER,160,99);}
      int[] ids={104,112,113,101,110,111,107,114,115};
      for(int code=22;code<=30;code++){
        Npc a=h.npc(3,451,450),b=h.npc(3,451,451);for(Npc n:new Npc[]{a,b})n.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1000,1000,false);
        for(Player p:new Player[]{caster,one,two}){p.getLocalNpcs().clear();p.getLocalNpcs().add(a);p.getLocalNpcs().add(b);p.resetAfterUpdate();}
        a.resetAfterUpdate();b.resetAfterUpdate();ClericSpellDefinition spell=ClericSpellCatalog.fromCode(code);
        ClericBlessingsFixture.fund(caster,spell);ClericBlessingsFixture.packet(caster,code);
        for(Npc n:new Npc[]{a,b})check(n.getUpdateFlags().getCombatEffects().size()==1&&n.getUpdateFlags().getCombatEffects().get(0).getEffectType()==ids[code-22],"one cumulative named-component sequence per useful NPC");
        for(Player p:new Player[]{caster,one,two})check(!p.getUpdateFlags().hasCombatEffect(),"hostile casts never attach animations to players");
        for(ClericResourceCost cost:spell.getResourceCosts())check(ClericBlessingsFixture.count(caster,cost.getItemId())==100-cost.getCount(),"two targets consume one resource transaction");
        for(Player p:new Player[]{caster,one,two})appearances(h,p,a,b,ids[code-22],false);
        a.getUpdateFlags().setCombatEffect(new CombatEffect(a,34));for(Player p:new Player[]{one,two})appearances(h,p,a,b,ids[code-22],true);
        if(spell.getSpellTier()==3){
          a.resetAfterUpdate();b.resetAfterUpdate();ClericSpellDefinition lower=ClericSpellCatalog.fromCode(code-2);ClericBlessingsFixture.fund(caster,lower);ClericBlessingsFixture.packet(caster,code-2);
          check(!a.getUpdateFlags().hasCombatEffect()&&!b.getUpdateFlags().hasCombatEffect(),"no-op tier downgrade emits no sequence");
          for(ClericResourceCost cost:lower.getResourceCosts())check(ClericBlessingsFixture.count(caster,cost.getItemId())==100,"no-op tier downgrade refunds sigils");
          appearances(h,one,a,b,0,false);
        }
        a.resetAfterUpdate();b.resetAfterUpdate();caster.getCarriedItems().getInventory().getItems().clear();
        ClericBlessingsFixture.packet(caster,code);check(!a.getUpdateFlags().hasCombatEffect()&&!b.getUpdateFlags().hasCombatEffect(),"unfunded refresh remains silent");
        h.world().unregisterNpc(a);h.world().unregisterNpc(b);
      }
      for(ClericSpellDefinition d:ClericSpellCatalog.getAll())check(d.getPresentation().hasOnEntityAnimation()==(d.getId().isBlessing()||d.isHostile()),"all eighteen god spells animated; Unify-only NONE");
      System.out.println("PASS "+checks+" actual nine-curse/two-target/two-observer/cost/no-op/appearance assertions");
    }
  }
}
