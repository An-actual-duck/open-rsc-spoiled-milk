package com.openrsc.server.combat;
import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.content.monsterslayer.SlayerComponentDrops;
import com.openrsc.server.content.monsterslayer.NagaCombat;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.external.ItemDefinition;
import java.util.List;
public final class NagaScalesFixture {
 private static int checks;
 private static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 public static void main(String[]args)throws Exception {
  try(CurrentCombatHarness h=new CurrentCombatHarness()) {
   h.server().getConfig().WANT_MYWORLD=true;h.recordDropLogs();
   Player p=h.player("scale tester",440,440);p.getClientLimitations().maxItemId=65535;
   ItemDefinition d=h.server().getEntityHandler().getItemDef(MyWorldItemId.NAGA_SCALES);
   check(d!=null&&d.getName().equals("Naga scales"),"name and ID");
   check(d.isStackable()&&!d.isNoteable()&&!d.isUntradable()&&!d.isWieldable(),"stackable tradeable ingredient");
   SlayerComponentDrops drops=new SlayerComponentDrops();int total=0;
   for(int roll=0;roll<1200;roll++) {
    h.random().reset(1);h.random().scriptInts(roll);
    List<Item> result=drops.rollRare(NagaCombat.NPC_ID,p,1);
    if(roll<300){check(result.size()==1&&result.get(0).getCatalogId()==3470&&result.get(0).getAmount()==roll/100+1,"quantity boundary "+roll);total++;}
    else check(result.isEmpty(),"empty boundary "+roll);
   }
   check(total==300,"exact 300 of 1200 common outcomes");
   h.random().reset(1);h.random().scriptInts(0);
   check(drops.rollRare(NagaCombat.NPC_ID,p,0).size()==1,"common scales do not accidentally inherit rare-item contribution gate");
   check(drops.baseline(NagaCombat.NPC_ID,h.random()).getCatalogId()==MyWorldItemId.NAGA_HIDE,"hide retained");
   check(h.world().getNpcDrops().getDropTable(NagaCombat.NPC_ID).hasItemDrop(com.openrsc.server.constants.ItemId.SALMON.id(),4,20,true),"original salmon supplies retained");
   com.openrsc.server.model.entity.npc.Npc naga=h.npc(NagaCombat.NPC_ID,442,440);naga.updateRegion();
   h.random().reset(8);h.random().scriptInts(0,0,0,0,0,0,0).scriptDoubles(0.0,0.0,0.0,0.0);
   naga.dropItems(p);
   com.openrsc.server.model.entity.GroundItem ground=h.world().getRegionManager().getRegion(naga.getLocation()).getItem(3470,naga.getLocation(),p);
   check(ground!=null&&ground.getAmount()==1,"actual naga ground drop delivery");
   check(h.awaitRecordedDropLogs()>0,"drop logs finish against fake database");
   h.server().getConfig().WANT_MYWORLD=false;
   check(drops.rollRare(NagaCombat.NPC_ID,p,1).isEmpty(),"world gate");
  }
  System.out.println("PASS Naga scales: "+checks+" checks; exact common odds, 1/2/3 quantities, flags and existing loot.");
 }
}
