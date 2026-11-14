package com.openrsc.server.combat;
import com.openrsc.server.constants.NpcDrops;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import java.util.List;

/** Exact uncommon thresholds and real ground delivery, over an isolated world. */
public final class BatWingDropsFixture {
 private static int checks;
 private static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 private static void wing(List<Item> items,int id){check(items.size()==1&&items.get(0).getCatalogId()==id&&items.get(0).getAmount()==1&&!items.get(0).getItemStatus().getNoted(),"one matching unnoted wing "+id);}
 public static void main(String[]args)throws Exception {
  try(CurrentCombatHarness h=new CurrentCombatHarness()) {
   h.server().getConfig().WANT_MYWORLD=true;h.recordDropLogs();
   Player p=h.player("wing tester",440,440);p.getClientLimitations().maxItemId=65535;
   NpcDrops drops=h.world().getNpcDrops();String[] names={"Giant bat wing","Death wing wing","Stirge wing"};int[] ids={43,768,882};
   check(NpcDrops.BAT_WING_DROP_DENOMINATOR==16,"uncommon base chance");
   for(int i=0;i<3;i++) {
    int item=3467+i;ItemDefinition def=h.server().getEntityHandler().getItemDef(item);
    check(def!=null&&def.getName().equals(names[i]),"requested name");
    check(!def.isStackable()&&def.isNoteable()&&!def.isUntradable()&&!def.isWieldable(),"tradeable, noteable crafting material");
    h.random().reset(1);h.random().scriptDoubles(0.0);wing(drops.rollHiddenUniqueDrops(ids[i],1),item);
    h.random().reset(1);h.random().scriptDoubles(Math.nextDown(1.0/16));wing(drops.rollHiddenUniqueDrops(ids[i],1),item);
    h.random().reset(1);h.random().scriptDoubles(1.0/16);check(drops.rollHiddenUniqueDrops(ids[i],1).isEmpty(),"first losing full-contribution boundary");
    h.random().reset(1);h.random().scriptDoubles(1.0/320);check(drops.rollHiddenUniqueDrops(ids[i],0).isEmpty(),"existing five-percent contribution floor boundary");
    h.random().reset(1);h.random().scriptDoubles(Math.nextDown(1.0/32));wing(drops.rollHiddenUniqueDrops(ids[i],.5),item);
    h.random().reset(1);h.random().scriptDoubles(1.0/32);check(drops.rollHiddenUniqueDrops(ids[i],.5).isEmpty(),"personal contribution scales the independent chance");
    Npc npc=h.npc(ids[i],442+i*2,440);npc.updateRegion();
    h.random().reset(8);h.random().scriptInts(0,0,0,0,0,0,0).scriptDoubles(0.0,0.0,0.0,0.0,0.0,0.0);
    npc.dropItems(p);
    GroundItem ground=h.world().getRegionManager().getRegion(npc.getLocation()).getItem(item,npc.getLocation(),p);
    check(ground!=null&&ground.getAmount()==1,"actual NPC ground delivery "+ids[i]);
   }
   h.random().reset(1);h.random().scriptInts(0,0).scriptDoubles(0.0,0.0);
   wing(drops.rollHiddenUniqueDrops(882,1),3469);
   List<Item> gland=drops.rollSlayerComponentDrops(882,p,1);
   check(gland.size()==1&&gland.get(0).getCatalogId()==3466,"Stirge wing and gland can both succeed independently");
   h.random().reset(1);h.random().scriptInts(100).scriptDoubles(0.0);
   wing(drops.rollHiddenUniqueDrops(882,1),3469);check(drops.rollSlayerComponentDrops(882,p,1).isEmpty(),"wing can succeed while gland fails");
   check(drops.rollHiddenUniqueDrops(44,1).isEmpty(),"unrelated NPC receives no wing");
   check(h.awaitRecordedDropLogs()>0,"ground drop logs complete against fake database");
   h.server().getConfig().WANT_MYWORLD=false;drops.unload();drops.load();
   for(int npc:ids)check(drops.rollHiddenUniqueDrops(npc,1).isEmpty(),"feature gated outside MyWorld");
  }
  System.out.println("PASS bat wings: "+checks+" checks; exact odds, contribution, names/flags, all three real ground deliveries, gland independence and world gating.");
 }
}
