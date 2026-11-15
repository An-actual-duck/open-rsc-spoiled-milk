package com.openrsc.server.combat;
import com.openrsc.server.constants.*;
import com.openrsc.server.content.coatings.*;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.UseInvTrigger;
public final class CoatingRecipesFixture {
 private static int checks,serial;
 private static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 private static Player player(CurrentCombatHarness h,int level)throws Exception {
  Player p=h.player("recipe"+(++serial),440,440);p.getClientLimitations().maxItemId=65535;
  p.setQuestStage(Quests.DRUIDIC_RITUAL,-1);
  p.getSkills().setTemporaryLevelAndMaxStat(Skill.HERBLAW.id(),level,level,false);return p;
 }
 private static Item add(Player p,int id,int amount,boolean noted) {
  check(p.getCarriedItems().getInventory().add(new Item(id,amount,noted)),"add ingredient");
  for(Item i:p.getCarriedItems().getInventory().getItems())if(i.getCatalogId()==id&&i.getNoted()==noted)return i;
  throw new AssertionError("missing added item");
 }
 private static int count(Player p,int id){return p.getCarriedItems().getInventory().countId(id);}
 public static void main(String[]args)throws Exception {
  try(CurrentCombatHarness h=new CurrentCombatHarness()) {
   h.server().getConfig().WANT_MYWORLD=true;h.server().getConfig().MEMBER_WORLD=true;h.server().getConfig().WANT_FATIGUE=false;
   UseInvTrigger action=(UseInvTrigger)Class.forName("com.openrsc.server.plugins.custom.itemactions.ScimitarCoatingActions").getConstructor().newInstance();
   check(BattleaxeCoatings.UNFINISHED==457&&BattleaxeCoatings.LEVEL==46,"viscous Harralander at 46");
   check(h.server().getEntityHandler().getItemHerbDef(447).getReqLevel()<=23,"base available before first coating");
   check(!BattleaxeCoatings.recipePair(460,3342),"old Avantoe recipe no longer routed");
   for(int level:new int[]{32,33,46})for(int quantity:new int[]{19,20,25})for(int pair=0;pair<6;pair++) {
    Player p=player(h,level);Item[] in={add(p,457,1,false),add(p,3466,1,false),add(p,3470,quantity,false)};
    int[][] pairs={{0,1},{1,0},{0,2},{2,0},{1,2},{2,1}};
    while(p.getCarriedItems().getInventory().size()<p.getCarriedItems().getInventory().getCapacity())add(p,ItemId.CHISEL.id(),1,false);
    int before=p.getSkills().getExperience(Skill.HERBLAW.id());
    check(action.blockUseInv(p,0,in[pairs[pair][0]],in[pairs[pair][1]]),"all six ingredient pair orders routed");
    action.onUseInv(p,0,in[pairs[pair][0]],in[pairs[pair][1]]);
    boolean success=level>=33&&quantity>=20;
    check(count(p,3447)==(success?1:0),"level and quantity boundary");
    check(count(p,457)==(success?0:1)&&count(p,3466)==(success?0:1)&&count(p,3470)==quantity-(success?20:0),"exact consumption and leftover scales at full inventory");
    Player control=player(h,level);int cb=control.getSkills().getExperience(Skill.HERBLAW.id());if(success)control.incExp(Skill.HERBLAW.id(),275,true);
    check(p.getSkills().getExperience(Skill.HERBLAW.id())-before==control.getSkills().getExperience(Skill.HERBLAW.id())-cb,"275 XP only on success");
    if(success)check(!ScimitarCoatings.mix(p,in[0],in[1])&&count(p,3447)==1,"stale inventory references cannot duplicate potion");
   }
   for(int mode=0;mode<7;mode++) {
    Player p=player(h,99);Item base=add(p,457,1,mode==0),gland=add(p,3466,1,mode==1),scales=add(p,3470,20,mode==2);
    if(mode==3)p.setQuestStage(Quests.DRUIDIC_RITUAL,0);
    if(mode==4)p.getClientLimitations().maxItemId=3446;
    if(mode==5){p.getCarriedItems().getInventory().getItems().remove(gland);gland=new Item(3466);}
    if(mode==6){p.getCarriedItems().getInventory().getItems().remove(base);base=add(p,460,1,false);}
    int size=p.getCarriedItems().getInventory().size(),before=p.getSkills().getExperience(Skill.HERBLAW.id());
    check(!ScimitarCoatings.mix(p,base,gland),"invalid recipe refused "+mode);
    check(p.getCarriedItems().getInventory().size()==size&&count(p,3447)==0&&scales.getAmount()==20&&p.getCarriedItems().getInventory().getItems().contains(scales)&&p.getSkills().getExperience(Skill.HERBLAW.id())==before,"invalid recipe loses nothing "+mode);
   }
  }
  System.out.println("PASS coating recipes: "+checks+" checks; actual plugin, full inventory, six pair orders, level/quantity gates, exact XP, leftovers and stale/noted/client/quest rejections.");
 }
}
