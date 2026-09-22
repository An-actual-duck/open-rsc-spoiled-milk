package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.content.production.*;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import java.lang.reflect.*;
import java.util.*;

/** Exercises existing tanning, production recipes, actual crafting and equipment with new materials. */
public final class SlayerLeatherFixture {
	private static final int[] HIDE = {3333,3335,3336,3337,3338,3392};
	private static final int[] BASE = {3356,3368,3374,3380,3386,3393};
	private static final int[] TIER = {2,5,6,6,7,4}, LEVEL = {8,30,38,38,46,22};
	private static final int[] COST = {1,2,2,3,4}, OFFSET = {0,1,1,2,3}, SLOT = {5,8,9,7,6};
	private static final int[][] PROFILE = {{10,20,35},{20,65,95},{55,55,55},{30,30,30},{120,100,120},{45,4,4}};
	public static void main(String[] args) throws Exception {
		try(CurrentCombatHarness h = new CurrentCombatHarness()) {
			com.openrsc.server.model.entity.npc.Npc ugthanki = h.npc(653,450,451);
			check(ugthanki.getMeleeDefense()==45 && ugthanki.getRangedDefense()==4 && ugthanki.getMagicDefense()==4,"Ugthanki effective source defenses");
			com.openrsc.server.content.DropTable drops = h.world().getNpcDrops().getDropTable(653);
			check(drops.hasItemDrop(3392,1,0,false),"guaranteed Ugthanki hide");
			check(drops.hasItemDrop(ItemId.RAW_UGTHANKI_MEAT.id(),1,0,false),"existing meat retained");
			Player looter=h.player("ugthanki loot",449,451);
			for(int roll=0;roll<8;roll++) {
				int hides=0;
				for(Item item:drops.clone(drops.getDescription()).invariableItems(looter)) if(item.getCatalogId()==3392) hides+=item.getAmount();
				check(hides==1,"off-task guaranteed hide quantity");
			}
			ItemDefinition pendant=h.server().getEntityHandler().getItemDef(3355);
			check(pendant.getMeleeOffense()==0 && pendant.getRangedOffense()==0 && pendant.getMagicOffense()==0,
				"literal zero stats never load as patch sentinels");
			Class<?> rackType = Class.forName("com.openrsc.server.plugins.custom.skills.crafting.TanningRack");
			Object rack = rackType.getConstructor().newInstance();
			Class<?> craftingType = Class.forName("com.openrsc.server.plugins.authentic.skills.crafting.Crafting");
			Object crafting = craftingType.getConstructor().newInstance();
			Method find = rackType.getDeclaredMethod("getProcess",int.class); find.setAccessible(true);
			Method sessionMethod = craftingType.getDeclaredMethod("createLeatherProductionSession",Player.class,Item.class); sessionMethod.setAccessible(true);
			Method produce = craftingType.getMethod("beginProductionFromInterface",Player.class,ProductionSession.class,int.class,int.class);
			for(int family=0;family<BASE.length;family++) {
				int hide=HIDE[family], base=BASE[family];
				Player p=h.player("leather test"+family,440+family,440);
				p.getClientLimitations().maxItemId=Integer.MAX_VALUE;
				p.getCarriedItems().getInventory().add(new Item(hide,12));
				Object process=find.invoke(rack,hide); check(process!=null,"tanning registered");
				Method tan=rackType.getDeclaredMethod("beginTanning",Player.class,process.getClass()); tan.setAccessible(true);
				check(field(process,"levelRequired")==LEVEL[family] && field(process,"experience")==5*(TIER[family]+1),"standard tanning rung");
				p.getSkills().setTemporaryLevelAndMaxStat(Skill.CRAFTING.id(),LEVEL[family]-1,LEVEL[family]-1,false);
				tan.invoke(rack,p,process); check(p.getCarriedItems().getInventory().countId(hide)==12,"low level cannot tan");
				p.getSkills().setTemporaryLevelAndMaxStat(Skill.CRAFTING.id(),99,99,false);
				tan.invoke(rack,p,process);
				check(p.getCarriedItems().getInventory().countId(hide)==0 && p.getCarriedItems().getInventory().countId(base)==12,"one-to-one tanning");
				p.getCarriedItems().getInventory().add(new Item(ItemId.THREAD.id(),5));
				p.getCarriedItems().getInventory().add(new Item(ItemId.NEEDLE.id()));
				p.getSkills().setTemporaryLevelAndMaxStat(Skill.CRAFTING.id(),LEVEL[family]-1,LEVEL[family]-1,false);
				ProductionSession low=(ProductionSession)sessionMethod.invoke(crafting,p,new Item(base));
				check(!low.hasAnyCraftableRecipe(),"low level interface flags");
				check(!(Boolean)produce.invoke(null,p,low,base+1,1),"low level production rejected");
				p.getSkills().setTemporaryLevelAndMaxStat(Skill.CRAFTING.id(),99,99,false);
				ProductionSession session=(ProductionSession)sessionMethod.invoke(crafting,p,new Item(base));
				check(session.getRecipes().size()==5,"all five slots available");
				int[] totals=new int[3];
				for(int piece=0;piece<5;piece++) {
					int id=base+piece+1;
					ProductionRecipe recipe=session.getRecipeByItemId(id);
					check(recipe.getRequiredLevel()==LEVEL[family]+OFFSET[piece],"crafting level by slot");
					check(Arrays.equals(recipe.getIngredientItemIds(),new int[]{base,ItemId.THREAD.id()})
						&& Arrays.equals(recipe.getIngredientAmounts(),new int[]{COST[piece],2*COST[piece]}),"materials and thread uses");
					ItemDefinition d=h.server().getEntityHandler().getItemDef(id);
					check(d.getWieldPosition()==SLOT[piece] && d.isWieldable() && !d.isUntradable() && !d.isStackable(),"slot and trading");
					check(d.getRequiredLevel()==0 && d.getRequiredSkillIndex()==-1,"ordinary leather wear rules");
					int[] expected=allocate((int)Math.ceil(TIER[family]*COST[piece]*.9),PROFILE[family]);
					for(int style=0;style<3;style++) totals[style]+=expected[style];
					check(Math.max(0,d.getMeleeDefense())==expected[0] && Math.max(0,d.getRangedDefense())==expected[1]
						&& Math.max(0,d.getMagicDefense())==expected[2],"exact tier budget and source defense ratio "+id);
					p.getSkills().setTemporaryLevelAndMaxStat(Skill.CRAFTING.id(),99,99,false);
					check((Boolean)produce.invoke(null,p,session,id,1),"craft actual piece "+id);
					check(p.getCarriedItems().getInventory().countId(id)==1,"one crafted output");
					h.equip(p,id,1);
				}
				check(p.getCarriedItems().getInventory().countId(base)==0,"twelve leather consumed");
				check(p.getCarriedItems().getEquipment().getMeleeDefense()==totals[0]
					&& p.getCarriedItems().getEquipment().getRangedDefense()==totals[1]
					&& p.getCarriedItems().getEquipment().getMagicDefense()==totals[2],"equipped totals preserve the tier budget");
				check(p.getCarriedItems().getInventory().countId(ItemId.THREAD.id())==1 && p.getCache().getInt("part_reel_thread")==4,"24 thread uses consumed");
				for(String detector:new String[]{"hasFullUnicornHideSet","hasFullBabyDragonSet","hasFullMossGiantSet","hasFullDemonSet","hasFullBlueDragonSet"})
					check(!(Boolean)p.getCarriedItems().getEquipment().getClass().getMethod(detector).invoke(p.getCarriedItems().getEquipment()),"no borrowed set effect");
			}
			check(find.invoke(rack,3339)==null && find.invoke(rack,3340)==null,"feathers and residue cannot be tanned");
			check(sessionMethod.invoke(crafting,h.player("excluded armor",450,450),new Item(3339))==null
				&& sessionMethod.invoke(crafting,h.player("excluded residue",451,450),new Item(3340))==null,"no excluded armor recipes or null-enum crash");
			// Retired Banshee materials remain valid possessions, but never expose production.
			Player retired = h.player("retired banshee",452,450);
			retired.getClientLimitations().maxItemId = Integer.MAX_VALUE;
			retired.getSkills().setTemporaryLevelAndMaxStat(Skill.CRAFTING.id(),99,99,false);
			retired.getCarriedItems().getInventory().add(new Item(ItemId.NEEDLE.id()));
			retired.getCarriedItems().getInventory().add(new Item(ItemId.THREAD.id(),5));
			for (int id : new int[]{MyWorldItemId.BANSHEE_HIDE, MyWorldItemId.BANSHEE_LEATHER, MyWorldItemId.ECTOPLASM}) {
				check(retired.getCarriedItems().getInventory().add(new Item(id,4)), "existing/new material can be held");
				check(find.invoke(rack,id)==null, "no Banshee tanning route " + id);
				check(sessionMethod.invoke(crafting,retired,new Item(id))==null, "no Banshee production menu " + id);
			}
			Method available=rackType.getDeclaredMethod("getAvailableProcesses",Player.class); available.setAccessible(true);
			check(((List<?>)available.invoke(rack,retired)).isEmpty(), "tanning menu hides old Banshee materials and Ectoplasm");
			for (int id=MyWorldItemId.BANSHEE_COIF;id<=MyWorldItemId.BANSHEE_CUIRASS;id++) {
				ProductionSession stale=new ProductionSession(ProductionSession.TYPE_CRAFTING,"Old Banshee recipe",
					MyWorldItemId.BANSHEE_LEATHER,Collections.singletonList(new ProductionRecipe(id,1,1,1,true,true)));
				check(!(Boolean)produce.invoke(null,retired,stale,id,1), "stale production request rejected " + id);
				check(retired.getCarriedItems().getInventory().countId(id)==0, "no retired armor created");
				ItemDefinition legacy=h.server().getEntityHandler().getItemDef(id);
				check(legacy!=null && legacy.isWieldable() && !legacy.isUntradable(), "legacy definition/trading preserved");
				check(retired.getCarriedItems().getInventory().add(new Item(id)), "legacy armor holdings remain readable");
				h.equip(retired,id,1);
			}
			check(retired.getCarriedItems().getInventory().countId(MyWorldItemId.BANSHEE_LEATHER)==4
				&& retired.getCarriedItems().getInventory().countId(ItemId.THREAD.id())==5, "rejected production consumes nothing");
			System.out.println("PASS Slayer leather: 6 tanning families, 30 crafted pieces, Ugthanki drops/source defenses, tier budgets/source ratios, levels, thread use, equipment slots, no borrowed bonuses and Banshee withdrawal/preservation");
		}
	}
	private static int field(Object object,String name)throws Exception { Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.getInt(object); }
	private static int[] allocate(int budget,int[] weights) {
		int total=Arrays.stream(weights).sum(); int[] result=new int[3], remainder=new int[3];
		for(int i=0;i<3;i++){result[i]=budget*weights[i]/total;remainder[i]=budget*weights[i]%total;}
		Integer[] order={0,1,2}; Arrays.sort(order,(a,b)->remainder[a]==remainder[b]?a-b:remainder[b]-remainder[a]);
		int left=budget-Arrays.stream(result).sum(); for(int i=0;i<left;i++)result[order[i]]++;
		return result;
	}
	private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
