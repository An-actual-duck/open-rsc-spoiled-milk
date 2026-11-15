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
   check(!d.isStackable()&&d.isNoteable()&&!d.isUntradable()&&!d.isWieldable(),"non-stackable noteable ingredient");
   SlayerComponentDrops drops=new SlayerComponentDrops();int total=0;
   for(int roll=0;roll<12800;roll++) {
    h.random().reset(1);h.random().scriptInts(roll);
    List<Item> result=drops.rollRare(NagaCombat.NPC_ID,p,1);
    if(roll<100){check(result.size()==1&&result.get(0).getCatalogId()==3470&&result.get(0).getAmount()==1,"quantity boundary "+roll);total++;}
    else check(result.isEmpty(),"empty boundary "+roll);
   }
   check(total==100,"exact 100 of 12800 rare outcomes");
   h.random().reset(1);h.random().scriptInts(0,0).scriptDoubles(0.0);
   check(drops.rollRare(NagaCombat.NPC_ID,p,0).size()==1,"rare ingredient retains contribution-floor chance");
   h.random().reset(1);h.random().scriptInts(0,0).scriptDoubles(0.99);
   check(drops.rollRare(NagaCombat.NPC_ID,p,0).isEmpty(),"rare ingredient follows shared contribution gate");
   check(drops.baseline(NagaCombat.NPC_ID,h.random()).getCatalogId()==MyWorldItemId.NAGA_HIDE,"hide retained");
   check(h.world().getNpcDrops().getDropTable(NagaCombat.NPC_ID).hasItemDrop(com.openrsc.server.constants.ItemId.SALMON.id(),4,20,true),"original salmon supplies retained");
   com.openrsc.server.model.entity.npc.Npc naga=h.npc(NagaCombat.NPC_ID,442,440);naga.updateRegion();
   h.random().reset(8);h.random().scriptInts(0,0,0,0,0,0,0).scriptDoubles(0.0,0.0,0.0,0.0);
   naga.dropItems(p);
   com.openrsc.server.model.entity.GroundItem ground=h.world().getRegionManager().getRegion(naga.getLocation()).getItem(3470,naga.getLocation(),p);
   check(ground!=null&&ground.getAmount()==1,"actual naga ground drop delivery");
   check(h.awaitRecordedDropLogs()>0,"drop logs finish against fake database");
   com.openrsc.server.plugins.triggers.UseInvTrigger herblaw=(com.openrsc.server.plugins.triggers.UseInvTrigger)Class.forName("com.openrsc.server.plugins.authentic.skills.herblaw.Herblaw").getConstructor().newInstance();
   for(int mode=0;mode<7;mode++) {
    Player grinder=h.player("grinder"+mode,440,440);grinder.getClientLimitations().maxItemId=mode==4?3470:65535;
    grinder.getCarriedItems().getInventory().add(new Item(com.openrsc.server.constants.ItemId.PESTLE_AND_MORTAR.id()));
    grinder.getCarriedItems().getInventory().add(new Item(mode==6?467:3470,1,mode==2));
    Item tool=grinder.getCarriedItems().getInventory().get(0),raw=grinder.getCarriedItems().getInventory().get(1);
    int xp=grinder.getSkills().getExperience(com.openrsc.server.constants.Skill.HERBLAW.id());
    if(mode==3)grinder.getCarriedItems().getInventory().getItems().remove(tool);
    if(mode==5)h.server().getConfig().MEMBER_WORLD=false;
    while(grinder.getCarriedItems().getInventory().size()<grinder.getCarriedItems().getInventory().getCapacity())grinder.getCarriedItems().getInventory().add(new Item(com.openrsc.server.constants.ItemId.CHISEL.id()));
    check(herblaw.blockUseInv(grinder,0,tool,raw),"shared grinding plugin handles scales");
    com.openrsc.server.event.rsc.PluginTask context=new com.openrsc.server.event.rsc.PluginTask(h.world(),grinder,"useinv",new Object[0]) {
     public int action(){return 0;}
     public synchronized void pause(int ticks){}
    };
    java.lang.reflect.Field contextsField=com.openrsc.server.event.rsc.PluginTask.class.getDeclaredField("tasksMap");contextsField.setAccessible(true);
    @SuppressWarnings("unchecked") java.util.Map<String,com.openrsc.server.event.rsc.PluginTask> contexts=(java.util.Map<String,com.openrsc.server.event.rsc.PluginTask>)contextsField.get(null);
    contexts.put(Thread.currentThread().getName(),context);
    try {if(mode==1)herblaw.onUseInv(grinder,0,raw,tool);else herblaw.onUseInv(grinder,0,tool,raw);}
    finally {contexts.remove(Thread.currentThread().getName());}

    boolean success=mode<2||mode==6;int groundId=mode==6?472:3471;
    check(grinder.getCarriedItems().getInventory().countId(groundId)==(success?1:0),"one powder only with valid raw scale and tool "+mode);
    check(grinder.getCarriedItems().getInventory().getItems().stream().anyMatch(i->i==tool)==(mode!=3),"pestle retained");
    check(grinder.getSkills().getExperience(com.openrsc.server.constants.Skill.HERBLAW.id())==xp,"grinding matches vanilla no-XP rule");
    h.server().getConfig().MEMBER_WORLD=true;
   }
   h.server().getConfig().WANT_MYWORLD=false;
   check(drops.rollRare(NagaCombat.NPC_ID,p,1).isEmpty(),"world gate");
  }
  System.out.println("PASS Naga scales: "+checks+" checks; exact 1/128 base odds, single quantity, flags and existing loot.");
 }
}
