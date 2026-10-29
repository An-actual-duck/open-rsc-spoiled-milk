package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.AntidoteCleansing;
import com.openrsc.server.content.PoisonPower;
import com.openrsc.server.event.rsc.impl.PoisonEvent;
import com.openrsc.server.external.ItemHerbSecond;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import java.lang.reflect.Field;

public final class AntidoteCleansingFixture {
 private static int assertions;
 public static void main(String[] args) throws Exception {
  int titan=0,steel=0;
  for(ItemId id:ItemId.values())if(id.name().startsWith("POISON")){
   if(id.name().contains("TITAN_STEEL")){check(PoisonPower.getWeaponAppliedPoisonPower(id.id())==28&&PoisonPower.getWeaponMaxPoisonPower(id.id())==70,"Titan "+id);titan++;}
   else if(id.name().contains("STEEL")){check(PoisonPower.getWeaponAppliedPoisonPower(id.id())==20&&PoisonPower.getWeaponMaxPoisonPower(id.id())==50,"Steel "+id);steel++;}
  }
  check(titan==5&&steel>0,"all named Titan/ordinary Steel families covered");
  try(CurrentCombatHarness h=new CurrentCombatHarness()){
   Player p=h.player("cleanse potion",440,440);
   p.getClientLimitations().maxItemId=Integer.MAX_VALUE;
   p.getSkills().setTemporaryLevelAndMaxStat(3,100,100,false);
   p.applyPoison(100,100);
   check(AntidoteCleansing.activate(p,3402)&&AntidoteCleansing.bonus(p)==5,"weak activates");
   check(p.getCurrentPoisonPower()==100&&!p.isAntidoteProtected(),"no cure or immunity");
   p.setAttribute("antidote_cleanse_expiry",System.currentTimeMillis()+1000);
   check(AntidoteCleansing.activate(p,3403)&&AntidoteCleansing.remainingMillis(p)>599000,"same tier refresh");
   check(AntidoteCleansing.activate(p,1474)&&AntidoteCleansing.bonus(p)==10,"normal upgrades");
   check(AntidoteCleansing.activate(p,3405)&&AntidoteCleansing.bonus(p)==20,"strong replaces rather than stacking");
   long expiry=p.getAttribute("antidote_cleanse_expiry",0L);
   p.getCarriedItems().getInventory().add(new Item(3402));
   OpInvTrigger drink=(OpInvTrigger)Class.forName("com.openrsc.server.plugins.authentic.itemactions.Drinkables").newInstance();
   check(drink.blockOpInv(p,0,new Item(3402),"Drink"),"new IDs dispatch without ItemId enum");
   drink.onOpInv(p,0,p.getCarriedItems().getInventory().get(0),"Drink");
   check(p.getCarriedItems().getInventory().countId(3402)==1&&p.getAttribute("antidote_cleanse_expiry",0L)==expiry,"weak rejected without consumption or refresh");
   check(p.getActivePotionEffectStatuses().stream().anyMatch(s->"potion:antidote_cleanse".equals(s.getStableKey())&&s.getIconItemId()==3405),"HUD item identity");
   p.getSettings().setAppearance(new com.openrsc.server.model.PlayerAppearance(0,0,0,0,1,2));
   for(int id=1890;id<1895;id++)h.equip(p,id,1);
   h.equip(p,1657,1);
   PoisonEvent event=p.getAttribute("poisonEvent",null);event.run();
   check(p.getCurrentPoisonPower()==67&&p.getLevel(3)==90,"additive 3+5+5+20; damage before drain");
   event.setPoisonPower(10);p.setPoisonDamage(10);event.run();
   check(p.getCurrentPoisonPower()==0&&p.getPoisonDamage()==0&&p.getAttribute("poisonEvent",null)==null,"oversized drain cures safely in same pulse");
   check(!p.getCache().hasKey(com.openrsc.server.model.combat.dot.PoisonDurableRecord.CACHE_KEY),"durable poison removed");
   p.setAttribute("antidote_cleanse_expiry",System.currentTimeMillis()-1);
   check(AntidoteCleansing.bonus(p)==0,"expiration");
   check(p.getAttribute("antidote_cleanse_power",-1)==-1&&AntidoteCleansing.itemId(p)==-1,"expired session attributes removed");
   check(p.getActivePotionEffectStatuses().stream().noneMatch(status->"potion:antidote_cleanse".equals(status.getStableKey())),"expired HUD status removed");
   Player fresh=h.player("fresh antidote session",460,460);
   check(AntidoteCleansing.bonus(fresh)==0,"new session does not inherit antidote attributes");
   check(!p.getCache().hasKey("antidote_cleanse_power")&&!p.getCache().hasKey("antidote_cleanse_expiry"),"antidote is not durable poison persistence");
   check(AntidoteCleansing.activate(p,3402),"weak allowed after expiry");
   for(int[] row:new int[][]{{3400,3402,8,160},{219,1474,22,220},{3401,3405,38,300}}){
    ItemHerbSecond recipe=h.server().getEntityHandler().getItemHerbSecond(row[0],455);
    check(recipe!=null&&recipe.getPotionID()==row[1]&&recipe.getReqLevel()==row[2]&&recipe.getExp()==row[3],"Marrentill recipe "+row[0]);
   }
   verifyActualRecipes(h);
   Object combine=Class.forName("com.openrsc.server.plugins.custom.itemactions.CombinePotions").newInstance();
   Field table=combine.getClass().getDeclaredField("combinePotions");table.setAccessible(true);
   for(int first:new int[]{3402,1474,3405}){
    boolean found=false;for(int[] row:(int[][])table.get(combine))if(java.util.Arrays.equals(row,new int[]{first+2,first+1,first}))found=true;
    check(found,"three-dose decant family "+first);
    Player decanter=h.player("decant "+first,460,461);decanter.getClientLimitations().maxItemId=Integer.MAX_VALUE;
    decanter.getCarriedItems().getInventory().add(new Item(first+2));decanter.getCarriedItems().getInventory().add(new Item(first+1));
    h.server().getConfig().WANT_DECANTING=true;
    bindContext(h,decanter);
    try {((com.openrsc.server.plugins.triggers.UseInvTrigger)combine).onUseInv(decanter,0,new Item(first+2),new Item(first+1));}
    finally {unbindContext();}
    check(decanter.getCarriedItems().getInventory().countId(first)==1&&decanter.getCarriedItems().getInventory().countId(first+1)==0&&decanter.getCarriedItems().getInventory().countId(first+2)==0,"actual decant yields three doses "+first);
    check(decanter.getCarriedItems().getInventory().countId(ItemId.EMPTY_VIAL.id())==1,"actual decant retains empty vial "+first);
    for(int n=0;n<3;n++){
     int id=first+n;
     check(AntidoteCleansing.doses(id)==3-n,"dose count");check(java.util.Arrays.asList(h.server().getEntityHandler().getItemDef(id).getCommand()).contains("Drink"),"effective drink item "+id);
     Player drinker=h.player("dose "+id,450+n,450);
     drinker.getClientLimitations().maxItemId=Integer.MAX_VALUE;
     drinker.applyPoison(40,40);
     drinker.getCarriedItems().getInventory().add(new Item(id));
     drink.onOpInv(drinker,0,drinker.getCarriedItems().getInventory().get(0),"Drink");
     check(drinker.getCarriedItems().getInventory().countId(id)==0&&drinker.getCarriedItems().getInventory().countId(n<2?id+1:ItemId.EMPTY_VIAL.id())==1,"actual dose depletion "+id);
     check(AntidoteCleansing.bonus(drinker)==AntidoteCleansing.powerForItem(id)&&drinker.getCurrentPoisonPower()==40&&!drinker.isAntidoteProtected(),"actual effect "+id);
     Player noted=h.player("noted "+id,453+n,450);
     noted.getClientLimitations().maxItemId=Integer.MAX_VALUE;
     noted.getCarriedItems().getInventory().add(new Item(id,1,true));
     drink.onOpInv(noted,0,noted.getCarriedItems().getInventory().get(0),"Drink");
     check(AntidoteCleansing.bonus(noted)==0&&noted.getCarriedItems().getInventory().get(0).getItemStatus().getNoted(),"noted potion rejected "+id);
    }
   }
  }
  System.out.println("PASS: Titan poison families, antidote tiers/refresh/rejection, 33 cleansing, safe cure, recipes/doses/HUD; "+assertions+" assertions");
 }
 private static void verifyActualRecipes(CurrentCombatHarness h)throws Exception {
  Object herblaw=Class.forName("com.openrsc.server.plugins.authentic.skills.herblaw.Herblaw").getConstructor().newInstance();
  java.lang.reflect.Method batch=herblaw.getClass().getDeclaredMethod("batchPotionSecondary",Player.class,Item.class,Item.class,ItemHerbSecond.class,java.util.concurrent.atomic.AtomicReference.class,int.class);batch.setAccessible(true);
  h.server().getConfig().MEMBER_WORLD=true;h.server().getConfig().WANT_FATIGUE=false;
  for(int[] row:new int[][]{{3400,3402,8},{219,1474,22},{3401,3405,38}})for(boolean qualified:new boolean[]{false,true}) {
   Player maker=h.player("recipe "+row[0]+qualified,465,465);maker.getClientLimitations().maxItemId=Integer.MAX_VALUE;h.recordOutgoingPackets(maker);
   maker.setQuestStage(com.openrsc.server.constants.Quests.DRUIDIC_RITUAL,-1);
   int level=row[2]-(qualified?0:1);maker.getSkills().setTemporaryLevelAndMaxStat(com.openrsc.server.constants.Skill.HERBLAW.id(),level,level,false);
   maker.getCarriedItems().getInventory().add(new Item(455));maker.getCarriedItems().getInventory().add(new Item(row[0]));
   ItemHerbSecond recipe=h.server().getEntityHandler().getItemHerbSecond(row[0],455);
   bindContext(h,maker);
   try {
    com.openrsc.server.plugins.Functions.startbatch(maker,1);
    batch.invoke(herblaw,maker,new Item(455),new Item(row[0]),recipe,new java.util.concurrent.atomic.AtomicReference<Item>(),1);
   } finally {unbindContext();}
   check(maker.getCarriedItems().getInventory().countId(row[1])==(qualified?1:0),"actual recipe output/level gate "+row[0]);
   check(maker.getCarriedItems().getInventory().countId(455)==(qualified?0:1)&&maker.getCarriedItems().getInventory().countId(row[0])==(qualified?0:1),"actual recipe input consumption or rejection preservation "+row[0]);
   if(qualified)check(AntidoteCleansing.doses(row[1])==3,"recipe still produces three doses");
  }
 }
 @SuppressWarnings("unchecked")
 private static java.util.Map<String,com.openrsc.server.event.rsc.PluginTask> contexts()throws Exception {
  Field field=com.openrsc.server.event.rsc.PluginTask.class.getDeclaredField("tasksMap");field.setAccessible(true);
  return (java.util.Map<String,com.openrsc.server.event.rsc.PluginTask>)field.get(null);
 }
 private static void bindContext(CurrentCombatHarness h,Player p)throws Exception {
  contexts().put(Thread.currentThread().getName(),new com.openrsc.server.event.rsc.PluginTask(h.world(),p,"useinv",new Object[0]) {public int action(){return 0;}});
 }
 private static void unbindContext()throws Exception {contexts().remove(Thread.currentThread().getName());}
 private static void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
}
