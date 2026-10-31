package com.openrsc.server.combat;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Devotion;
import com.openrsc.server.content.cleric.*;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.*;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.*;
import com.openrsc.server.model.world.coordinate.*;
import com.openrsc.server.net.rsc.handlers.*;
import com.openrsc.server.net.rsc.struct.incoming.*;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.plugins.custom.myworld.skills.blessing.*;
import com.openrsc.server.plugins.triggers.*;
import java.util.*;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
/** Maintained native World + packet ingress + real plugin/inventory/XP. No listeners or account database. */
public final class GraceBlightWellInfusionFixture {
 static int checks; static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static Inventory inv(Player p){return p.getCarriedItems().getInventory();}
 static int count(Player p,int id){return inv(p).countId(id,Optional.of(false));}
 static void clear(Player p){synchronized(inv(p).getItems()){inv(p).getItems().clear();}p.resetAll();p.setFatigue(0);}
 static void level(Player p,int n){p.getSkills().setExperienceAndLevel(Skill.BLESSING.id(),p.getSkills().experienceForLevel(Math.max(1,n)),n,false);}
 static double xp(Player p){return p.getSkills().getExperience(Skill.BLESSING.id());}
 static int[] devotion(Player p){return Arrays.stream(PrayerCatalog.GodLine.values()).mapToInt(g->Devotion.getHalfOfferingUnits(p,g)).toArray();}
 static void drain(CurrentCombatHarness h)throws Exception{h.advanceOneCombatTick();Field f=CurrentCombatHarness.class.getDeclaredField("pluginExecutor");f.setAccessible(true);((ExecutorService)f.get(h)).submit(()->{}).get();}
 static void packet(CurrentCombatHarness h,Player p,GameObject o,boolean use)throws Exception{
  if(use){ItemOnObjectStruct s=new ItemOnObjectStruct();s.setOpcode(OpcodeIn.USE_ITEM_ON_SCENERY);s.coordObject=o.getLocation();s.slotID=0;new ItemUseOnObject().process(s,p);}
  else{TargetObjectStruct s=new TargetObjectStruct();s.setOpcode(OpcodeIn.OBJECT_COMMAND);s.coordObject=o.getLocation();new GameObjectAction().process(s,p);}
  check(p.getWalkToAction()!=null&&p.getWalkToAction().shouldExecute(),"packet creates ordinary land-side walking action");p.getWalkToAction().execute();drain(h);
 }
 public static void main(String[] a)throws Exception{try(CurrentCombatHarness h=new CurrentCombatHarness()){
  h.server().getConfig().WANT_MYWORLD=true;h.server().getConfig().WANT_FATIGUE=false;
  h.world().getRegionManager().preflightNativeLayeredPlacements();h.world().getRegionManager().populateNativeLayeredPlacements();
  Player p=h.player("infusefix",441,440);p.getClientLimitations().maxItemId=Integer.MAX_VALUE;h.recordOutgoingPackets(p);SigilWells plugin=new SigilWells();h.installPlugin(OpLocTrigger.class,plugin);h.installPlugin(UseLocTrigger.class,plugin);
  GameObject ichor=h.world().getRegionManager().findNativeLayeredScenery(WorldLocation.global(new WorldCoordinate(440,440,0)));
  GameObject divine=h.world().getRegionManager().findNativeLayeredScenery(WorldLocation.global(new WorldCoordinate(440,448,0)));
  for(PrayerCatalog.GodLine g:PrayerCatalog.GodLine.values())Devotion.setDevotionLevel(p,g,500);
  for(ClericWellSigil sigil:ClericWellSigil.values())for(int n:new int[]{0,1,10,11,15,16,25,26,99}){
   clear(p);level(p,n);GameObject o=sigil.acceptsWell(1354)?ichor:divine;p.teleport(o.getX()+1,o.getY(),false);inv(p).add(new Item(sigil.getRawItemId(),3));double before=xp(p);int[] gods=devotion(p);
   plugin.onOpLoc(p,o,"Dip sigils");int mult=ClericSigilProductionCatalog.getOutputMultiplier(n,sigil.getMaterial());
   check(count(p,sigil.getInfusedItemId())==3*mult,"correct output "+sigil+" level"+n);check(count(p,sigil.getRawItemId())==(mult==0?3:0),"exact raw consumption "+sigil+" level"+n+" raw"+count(p,sigil.getRawItemId())+" mult"+mult);
   int expected=mult==0?0:ClericSigilProductionCatalog.getDiminishingInternalExperience(sigil.getMaterial().getBaseBlessingExperience(),3,mult);
   check(xp(p)-before==expected,"exact altar diminishing internal XP "+sigil+" level"+n+" got"+(xp(p)-before)+" expected"+expected);
   check(Arrays.equals(gods,devotion(p)),"all god devotion balances unchanged");double after=xp(p);plugin.onOpLoc(p,o,"Dip sigils");check(xp(p)==after,"repeated request cannot duplicate XP");
  }
  clear(p);level(p,16);p.teleport(441,448,false);inv(p).add(new Item(3299,2));inv(p).add(new Item(3307,2));packet(h,p,divine,false);check(count(p,3300)==4&&count(p,3308)==2,"actual Dip packet processes both grades");
  clear(p);level(p,1);inv(p).add(new Item(3299));inv(p).add(new Item(3307));plugin.onOpLoc(p,divine,"Dip sigils");check(count(p,3300)==1&&count(p,3307)==1,"underlevel silver unchanged while stone succeeds");
  clear(p);level(p,11);p.teleport(441,440,false);inv(p).add(new Item(3426,3));packet(h,p,ichor,true);check(count(p,3427)==6&&count(p,3426)==0,"actual inventory-on-scenery packet converts selected grade");
  for(int id:new int[]{3299,3300,3293,10}){clear(p);level(p,26);inv(p).add(new Item(id));double before=xp(p);plugin.onUseLoc(p,ichor,inv(p).get(0));check(count(p,id)==1&&xp(p)==before,"wrong purpose/infused/god/unrelated refusal "+id);}
  clear(p);level(p,26);Item stale=new Item(3426);inv(p).add(stale);inv(p).getItems().clear();inv(p).add(new Item(3426));plugin.onUseLoc(p,ichor,stale);check(count(p,3426)==1&&count(p,3427)==0,"removed same-catalog item instance refused");plugin.onUseLoc(p,ichor,new Item(3426));check(count(p,3426)==1,"forged same-catalog instance refused");
  clear(p);level(p,26);inv(p).add(new Item(3426));ItemOnObjectStruct queued=new ItemOnObjectStruct();queued.setOpcode(OpcodeIn.USE_ITEM_ON_SCENERY);queued.coordObject=ichor.getLocation();queued.slotID=0;new ItemUseOnObject().process(queued,p);check(p.getWalkToAction()!=null,"stale packet captured original item");inv(p).getItems().clear();inv(p).add(new Item(3426));p.getWalkToAction().execute();drain(h);check(count(p,3426)==1&&count(p,3427)==0,"real queued UseLoc rejects replaced same-catalog inventory instance");
  h.server().getConfig().WANT_FATIGUE=true;h.server().getConfig().STOP_SKILLING_FATIGUED=2;p.setFatigue(p.MAX_FATIGUE);plugin.onOpLoc(p,ichor,"Dip sigils");check(count(p,3426)==1,"fatigue refusal preserves input");h.server().getConfig().WANT_FATIGUE=false;
  clear(p);level(p,11);inv(p).add(new Item(3426));int capacity=inv(p).getCapacity();for(int i=1;i<capacity;i++)inv(p).add(new Item(259));plugin.onOpLoc(p,ichor,"Dip sigils");check(inv(p).size()==capacity&&count(p,3427)==2,"full inventory reuses source slot");
  clear(p);level(p,11);p.teleport(441,440,false);inv(p).add(new Item(3426,3));double example=xp(p);plugin.onOpLoc(p,ichor,"Dip sigils");check(count(p,3427)==6&&xp(p)-example==90,"independent level11 example: three stone to six,90 internal XP");
  clear(p);level(p,26);inv(p).getItems().add(new Item(3426,1,true));double unchanged=xp(p);plugin.onOpLoc(p,ichor,"Dip sigils");plugin.onUseLoc(p,ichor,inv(p).get(0));check(inv(p).get(0).getNoted()&&xp(p)==unchanged,"noted inputs refused on both routes");
  clear(p);level(p,26);inv(p).add(new Item(3426));p.getClientLimitations().maxItemId=3426;plugin.onOpLoc(p,ichor,"Dip sigils");check(count(p,3426)==1&&count(p,3427)==0,"limited client refuses output without consumption");p.getClientLimitations().maxItemId=Integer.MAX_VALUE;
  h.server().getConfig().RESTRICT_ITEM_ID=3426;plugin.onOpLoc(p,ichor,"Dip sigils");check(count(p,3426)==1,"world output restriction preserves source");h.server().getConfig().RESTRICT_ITEM_ID=-1;
  clear(p);level(p,99);h.server().getConfig().SHORT_MAX_STACKS=true;inv(p).getItems().add(new Item(3426,10000));plugin.onOpLoc(p,ichor,"Dip sigils");check(count(p,3426)==10000&&count(p,3427)==0,"short-stack output cap refuses before consumption");h.server().getConfig().SHORT_MAX_STACKS=false;
  clear(p);level(p,26);inv(p).add(new Item(3426));inv(p).add(new Item(3428));p.getClientLimitations().maxItemId=3428;plugin.onOpLoc(p,ichor,"Dip sigils");check(count(p,3427)==3&&count(p,3428)==1&&count(p,3429)==0,"one output restriction does not block valid other grade");p.getClientLimitations().maxItemId=Integer.MAX_VALUE;
  SigilProduction altars=new SigilProduction();int[] altarIds={19,235,144}, rawGod={3293,3295,3297};PrayerCatalog.GodLine[] godLines={PrayerCatalog.GodLine.SARADOMIN,PrayerCatalog.GodLine.GUTHIX,PrayerCatalog.GodLine.ZAMORAK};
  for(int i=0;i<3;i++){clear(p);level(p,11);GameObject altar=new GameObject(h.world(),Point.location(441,441),altarIds[i],0,0);altar.setInitialLocation(Point.location(441,441));inv(p).add(new Item(rawGod[i],3));int old=Devotion.getHalfOfferingUnits(p,godLines[i]);double oldXp=xp(p);altars.onUseLoc(p,altar,inv(p).get(0));check(count(p,rawGod[i]+1)==6&&xp(p)-oldXp==90&&Devotion.getHalfOfferingUnits(p,godLines[i])==old-3,"matching god altar retains yield XP and Devotion "+i);
   clear(p);inv(p).add(new Item(rawGod[(i+1)%3]));int[] oldGods=devotion(p);altars.onUseLoc(p,altar,inv(p).get(0));check(count(p,rawGod[(i+1)%3])==1&&Arrays.equals(oldGods,devotion(p)),"wrong god altar refusal");
   clear(p);inv(p).add(new Item(3299));oldGods=devotion(p);altars.onUseLoc(p,altar,inv(p).get(0));check(count(p,3299)==1&&count(p,3300)==0&&Arrays.equals(oldGods,devotion(p)),"Grace no longer blessed at god altar");
  }
  p.setClientVersion(10065);p.getClientLimitations().maxItemId=Integer.MAX_VALUE;
  for(ClericSigilMaterial material:ClericSigilMaterial.values()){
   int output=material==ClericSigilMaterial.STONE?3426:3428;
   for(int crafting:new int[]{material.getCraftingLevel()-1,material.getCraftingLevel()}){
    clear(p);p.getSkills().setExperienceAndLevel(Skill.CRAFTING.id(),p.getSkills().experienceForLevel(Math.max(1,crafting)),crafting,false);
    inv(p).add(new Item(com.openrsc.server.constants.ItemId.CHISEL.id()));inv(p).add(new Item(material.getSourceItemId()));
    altars.onUseInv(p,0,inv(p).get(0),inv(p).get(1));
    com.openrsc.server.content.production.ProductionSession session=p.getAttribute("production_session",null);
    check(session!=null&&session.getRecipes().size()==5&&session.getRecipeByItemId(output)!=null,"ordinary crafting selection includes fifth Blight recipe");
    Field tasks=com.openrsc.server.event.rsc.PluginTask.class.getDeclaredField("tasksMap");tasks.setAccessible(true);
    @SuppressWarnings("unchecked") Map<String,com.openrsc.server.event.rsc.PluginTask> contexts=(Map<String,com.openrsc.server.event.rsc.PluginTask>)tasks.get(null);
    contexts.put(Thread.currentThread().getName(),new com.openrsc.server.event.rsc.PluginTask(h.world(),p,"useinv",new Object[0]){public int action(){return 0;}public synchronized void pause(int ticks){}});
    double oldCraft=p.getSkills().getExperience(Skill.CRAFTING.id());boolean crafted;
    try{crafted=SigilProduction.beginProductionFromInterface(p,session,output,1);}finally{contexts.remove(Thread.currentThread().getName());}
    boolean allowed=crafting>=material.getCraftingLevel();check(crafted==allowed&&count(p,output)==(allowed?1:0)&&count(p,material.getSourceItemId())==(allowed?0:1),"actual Blight carving level/input/output "+material);
    check(p.getSkills().getExperience(Skill.CRAFTING.id())-oldCraft==(allowed?4*material.getBaseCraftingExperience():0),"actual Blight crafting XP");
   }
  }
  clear(p);level(p,99);Item huge=new Item(3426,Integer.MAX_VALUE);inv(p).getItems().add(huge);double before=xp(p);plugin.onOpLoc(p,ichor,"Dip sigils");check(inv(p).get(0)==huge&&xp(p)==before,"output overflow refuses before mutation");
  System.out.println("PASS "+checks+" Grace/Blight actual packet, production, yield, XP, identity, fatigue, full inventory and overflow checks");
 }}
}
