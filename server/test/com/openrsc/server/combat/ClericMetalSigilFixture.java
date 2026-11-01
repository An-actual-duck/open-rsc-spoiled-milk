package com.openrsc.server.combat;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.Devotion;
import com.openrsc.server.content.cleric.*;
import com.openrsc.server.content.cleric.effect.ClericEffectFamily;
import com.openrsc.server.content.cleric.runtime.ClericHostileRuntime;
import com.openrsc.server.content.production.ProductionSession;
import com.openrsc.server.event.rsc.PluginTask;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.*;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.*;
import com.openrsc.server.plugins.authentic.skills.smithing.Smelting;
import com.openrsc.server.plugins.custom.myworld.skills.blessing.*;
import java.lang.reflect.*;
import java.util.*;

/** Actual inventories, furnace/carving callbacks, charging and mixed-cost casts. No listeners/accounts. */
public final class ClericMetalSigilFixture {
    private static int checks;
    private static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
    private static Inventory inv(Player p) { return p.getCarriedItems().getInventory(); }
    private static int count(Player p, int id) { return inv(p).countId(id, Optional.of(false)); }
    private static void clear(Player p) { ClericBlessingsFixture.clear(p); p.setFatigue(0); }
    private static void level(Player p, Skill skill, int level) {
        p.getSkills().setTemporaryLevelAndMaxStat(skill.id(), level, level, false);
    }
    @SuppressWarnings("unchecked")
    private static Map<String, PluginTask> contexts() throws Exception {
        Field f = PluginTask.class.getDeclaredField("tasksMap"); f.setAccessible(true);
        return (Map<String, PluginTask>)f.get(null);
    }
    private static boolean run(CurrentCombatHarness h, Player p, Runnable pause, java.util.function.BooleanSupplier call) throws Exception {
        Map<String,PluginTask> map=contexts();
        PluginTask task=new PluginTask(h.world(),p,"production",new Object[0]) {
            public int action() { return 0; }
            public synchronized void pause(int ticks) { pause.run(); }
        };
        map.put(Thread.currentThread().getName(),task);
        try { return call.getAsBoolean(); } finally { map.remove(Thread.currentThread().getName()); }
    }
    private static ProductionSession menu(Player p, GameObject furnace) {
        new Smelting().onOpLoc(p,furnace,"Smelt");
        ProductionSession categories=p.getAttribute("production_session",null);
        check(categories.getRecipeByItemId(3433)!=null,"furnace exposes normal blank-sigil category");
        check(Smelting.beginFurnaceCategoryFromInterface(p,categories,3433,1),"ordinary category callback opens blank menu");
        return p.getAttribute("production_session",null);
    }
    public static void main(String[] args) throws Exception {
        try(CurrentCombatHarness h=new CurrentCombatHarness()) {
            h.server().getConfig().WANT_MYWORLD=true; h.server().getConfig().WANT_FATIGUE=false;
            h.world().getRegionManager().preflightNativeLayeredPlacements();
            h.world().getRegionManager().populateNativeLayeredPlacements();
            Player p=h.player("metal sigils",450,450);p.setClientVersion(10055);p.getClientLimitations().maxItemId=65535;h.recordOutgoingPackets(p);
            level(p,Skill.CRAFTING,99);level(p,Skill.SMITHING,99);level(p,Skill.BLESSING,99);
            GameObject furnace=new GameObject(h.world(),Point.location(449,450),SceneryId.FURNACE.id(),0,0);furnace.setInitialLocation(Point.location(449,450));
            for(int blank:new int[]{3431,3432}) {
                clear(p); int bar=blank==3431?384:3430; inv(p).add(new Item(bar));inv(p).add(new Item(3433));
                ProductionSession session=menu(p,furnace);
                check(session.getRecipes().size()==2,"both silver and Moonsilver available");
                check(session.getRecipeByItemId(blank).getOutputAmount()==3,"recipe advertises three blanks");
                check(session.getRecipeByItemId(blank).getIngredientItemIds().length==1
                    &&session.getRecipeByItemId(blank).getIngredientItemIds()[0]==bar,"mould not listed as consumed ingredient");
                check(run(h,p,()->{},()->SigilMoulding.beginProductionFromInterface(p,session,blank,1)),"actual moulding callback accepted");
                check(count(p,bar)==0&&count(p,blank)==3&&count(p,3433)==1,"one bar makes three nonstacked blanks and keeps mould");
                check(inv(p).size()==4,"blank output really occupies three inventory spaces");
                check(!SigilMoulding.beginProductionFromInterface(p,session,blank,1)&&count(p,blank)==3,"completed session replay cannot duplicate");
                Set<Long> ids=new HashSet<Long>();for(Item item:inv(p).getItems())check(ids.add(item.getItemId()),"unique persistent output identities");
            }
            ClericSigilMaterial material=ClericSigilMaterial.MOONSILVER;
            for(int free:new int[]{0,1,2}) {
                clear(p);inv(p).add(new Item(3430));inv(p).add(new Item(3433));
                while(inv(p).size()<inv(p).getCapacity()-free)inv(p).add(new Item(259));
                SigilMoulding.rememberFurnace(p,furnace);
                check(SigilMoulding.mouldOne(p,material)==(free==2),"capacity boundary requires two extra spaces");
                check(count(p,3430)==(free==2?0:1)&&count(p,3432)==(free==2?3:0),"capacity rejection retains bar and never partial blanks");
            }
            clear(p);inv(p).add(new Item(3430));SigilMoulding.rememberFurnace(p,furnace);
            check(!SigilMoulding.mouldOne(p,material)&&count(p,3430)==1,"missing mould keeps bar");
            inv(p).add(new Item(3433));level(p,Skill.CRAFTING,39);
            check(!SigilMoulding.mouldOne(p,material)&&count(p,3430)==1,"underlevel keeps bar");level(p,Skill.CRAFTING,40);
            p.teleport(455,455,false);check(!SigilMoulding.mouldOne(p,material)&&count(p,3430)==1,"moved away from furnace cannot finish");p.teleport(450,450,false);
            p.getClientLimitations().maxItemId=3431;check(!SigilMoulding.mouldOne(p,material)&&count(p,3430)==1,"old output admission preserves bar");p.getClientLimitations().maxItemId=65535;
            h.server().getConfig().RESTRICT_ITEM_ID=3431;check(!SigilMoulding.mouldOne(p,material)&&count(p,3430)==1,"world output admission preserves bar");h.server().getConfig().RESTRICT_ITEM_ID=-1;
            clear(p);inv(p).add(new Item(3430));inv(p).add(new Item(3433));ProductionSession interrupted=menu(p,furnace);
            check(run(h,p,()->p.teleport(455,455,false),()->SigilMoulding.beginProductionFromInterface(p,interrupted,3432,1)),"admitted delayed batch");
            check(count(p,3430)==1&&count(p,3432)==0,"movement during delay cancels without consumption");p.teleport(450,450,false);
            clear(p);inv(p).add(new Item(3430));inv(p).add(new Item(3433));ProductionSession missing=menu(p,furnace);
            run(h,p,()->inv(p).remove(inv(p).get(inv(p).getLastIndexById(3433,Optional.of(false))),false),()->SigilMoulding.beginProductionFromInterface(p,missing,3432,1));
            check(count(p,3430)==1&&count(p,3432)==0,"mould removed during delay cancels whole conversion");
            clear(p);inv(p).add(new Item(3430));inv(p).add(new Item(3430));inv(p).add(new Item(3433));ProductionSession batch=menu(p,furnace);
            final int[] pauses={0};run(h,p,()->{if(++pauses[0]==2)p.teleport(455,455,false);},()->SigilMoulding.beginProductionFromInterface(p,batch,3432,2));
            check(count(p,3430)==1&&count(p,3432)==3,"interrupted partial batch retains unprocessed bar and complete first three");p.teleport(450,450,false);
            clear(p);inv(p).add(new Item(3430));inv(p).add(new Item(3433));ProductionSession valid=menu(p,furnace);
            check(!SigilMoulding.beginProductionFromInterface(p,valid,3443,1)&&count(p,3430)==1,"forged charged output refused");
            check(!SigilMoulding.beginProductionFromInterface(p,new ProductionSession(valid.getType(),valid.getTitle(),valid.getInputItemId(),valid.getRecipes()),3432,1),"forged session identity refused");
            clear(p);inv(p).add(new Item(3433));inv(p).getItems().add(new Item(3430,1,true));SigilMoulding.rememberFurnace(p,furnace);
            check(!SigilMoulding.mouldOne(p,material)&&count(p,3432)==0,"noted bar cannot be moulded");


            clear(p);inv(p).add(new Item(384));inv(p).add(new Item(3433));
            new com.openrsc.server.plugins.authentic.skills.crafting.Crafting().onUseLoc(p,furnace,inv(p).get(0));
            ProductionSession silverMenu=p.getAttribute("production_session",null);
            check(silverMenu.getRecipeByItemId(3431)!=null&&silverMenu.getRecipeByItemId(44)!=null,"using silver bar retains symbols and offers blanks");
            check(!SigilMoulding.beginProductionFromInterface(p,silverMenu,3432,1)&&count(p,384)==1,"silver session cannot request Moonsilver output");
            int beforeXp=p.getSkills().getExperience(Skill.CRAFTING.id());
            check(run(h,p,()->{},()->com.openrsc.server.plugins.authentic.skills.crafting.Crafting.beginProductionFromInterface(p,silverMenu,3431,1)),"silver bar direct recipe delegates safely");
            check(count(p,3431)==3&&count(p,384)==0&&p.getSkills().getExperience(Skill.CRAFTING.id())-beforeXp==40,"silver mould earns10displayedCraftingXP");
            // Ordinary furnace Bars menu actually smelts the existing silver/mithril bar pair.
            clear(p);inv(p).add(new Item(384));inv(p).add(new Item(173));new Smelting().onOpLoc(p,furnace,"Smelt");
            ProductionSession cats=p.getAttribute("production_session",null);Smelting.beginFurnaceCategoryFromInterface(p,cats,Smelting.FURNACE_CATEGORY_BARS,1);
            ProductionSession bars=p.getAttribute("production_session",null);check(bars.getRecipeByItemId(3430).getRequiredLevel()==38,"alloy appears at Smithing38");
            check(bars.getRecipeByItemId(384).getIngredientItemIds()[0]==383&&bars.getRecipeByItemId(384).getRequiredLevel()==20,"ordinary raw silver smelting retained");
            check(run(h,p,()->{},()->Smelting.beginProductionFromInterface(p,bars,3430,1)),"native alloy recipe executes");
            check(count(p,384)==0&&count(p,173)==0&&count(p,3430)==1,"exact1silver+1mithril yields1Moonsilver");
            clear(p);inv(p).add(new Item(384));check(!Smelting.beginProductionFromInterface(p,bars,3430,1)&&count(p,384)==1,"missing alloy ingredient never partially consumed");
            clear(p);inv(p).add(new Item(384));inv(p).add(new Item(173));level(p,Skill.SMITHING,37);
            check(!Smelting.beginProductionFromInterface(p,bars,3430,1)&&count(p,384)==1&&count(p,173)==1,"underlevel alloy retains both bars");level(p,Skill.SMITHING,99);


            clear(p);inv(p).add(new Item(384));inv(p).add(new Item(173));
            check(!run(h,p,()->p.teleport(455,455,false),()->Smelting.beginProductionFromInterface(p,bars,3430,1)),"alloy movement cancellation reports no completed bar");
            check(count(p,384)==1&&count(p,173)==1&&count(p,3430)==0,"alloy movement during delay retains both inputs");p.teleport(450,450,false);
            p.getClientLimitations().maxItemId=3429;
            check(!Smelting.beginProductionFromInterface(p,bars,3430,1)&&count(p,384)==1&&count(p,173)==1,"alloy output admission preserves both inputs");p.getClientLimitations().maxItemId=65535;
            SigilProduction carving=new SigilProduction();
            clear(p);inv(p).add(new Item(ItemId.CHISEL.id()));inv(p).add(new Item(383));
            check(!carving.blockUseInv(p,0,inv(p).get(0),inv(p).get(1)),"raw silver nugget no longer carves directly");
            for(ClericSigilMaterial metal:new ClericSigilMaterial[]{ClericSigilMaterial.SILVER,material})for(int output:ClericSigilProductionCatalog.getCarvingOutputItemIds(metal)) {
                clear(p);level(p,Skill.CRAFTING,99);inv(p).add(new Item(ItemId.CHISEL.id()));inv(p).add(new Item(metal.getSourceItemId()));
                carving.onUseInv(p,0,inv(p).get(0),inv(p).get(1));ProductionSession carve=p.getAttribute("production_session",null);
                check(run(h,p,()->{},()->SigilProduction.beginProductionFromInterface(p,carve,output,1)),"each of five metal carvings accessible");
                check(count(p,metal.getSourceItemId())==0&&count(p,output)==1,"one blank yields one carved sigil");
            }
            for(ClericSigilItemId raw:ClericSigilProductionCatalog.getUnblessedIdentities(material))if(raw.getAlignment()!=ClericAlignment.NEUTRAL) {
                clear(p);level(p,Skill.BLESSING,50);int altarId=raw.getAlignment()==ClericAlignment.SARADOMIN?19:raw.getAlignment()==ClericAlignment.GUTHIX?235:144;
                GameObject altar=new GameObject(h.world(),Point.location(449,450),altarId,0,0);altar.setInitialLocation(Point.location(449,450));
                for(PrayerCatalog.GodLine god:PrayerCatalog.GodLine.values())Devotion.setDevotionLevel(p,god,500);
                inv(p).add(new Item(raw.getItemId()));carving.onUseLoc(p,altar,inv(p).get(0));
                check(count(p,raw.getItemId())==0&&count(p,raw.getItemId()+1)==2,"Moonsilver god altar retains50level duplication");
            }
            for(ClericSpellDefinition spell:ClericSpellCatalog.getAll())if(spell.getAlignment()!=ClericAlignment.NEUTRAL) {
                List<ClericResourceCost> costs=spell.getResourceCosts();check(costs.size()==(spell.getSpellTier()==1?2:4),"current-and-previous-grade cost count");
                for(ClericSigilMaterial grade:ClericSigilMaterial.values()) {
                    int expected=spell.getSpellTier()==1?(grade==ClericSigilMaterial.STONE?1:0):spell.getSpellTier()==2?(grade==material?0:1):(grade==ClericSigilMaterial.STONE?0:1);
                    check(spell.getPrimarySigilCost().getCount(grade)==expected,"primary source vector agrees with tier contract");
                }
            }
            // Actual native packet admission: every missing part of a four-resource curse rejects without any spend/effect.
            Npc target=h.npc(3,451,450);ClericSpellDefinition curse=ClericSpellCatalog.fromCode(24);
            for(ClericResourceCost absent:curse.getResourceCosts()) {
                clear(p);level(p,Skill.PRAYER,99);ClericBlessingsFixture.fund(p,curse);p.getCarriedItems().remove(new Item[]{new Item(absent.getItemId(),100)},false);
                ClericBlessingsFixture.packet(p,24);
                for(ClericResourceCost cost:curse.getResourceCosts())check(count(p,cost.getItemId())==(cost.getItemId()==absent.getItemId()?0:100),"missing mixed cost refuses atomically");
                check(ClericHostileRuntime.effect(target,ClericEffectFamily.GUTHIX_HOSTILE)==null,"missing mixed cost applies no curse");
            }

            Player ally=h.player("sigil ally",450,451), second=h.player("sigil ally two",450,452);
            h.recordOutgoingPackets(ally);h.recordOutgoingPackets(second);ClericBlessingsFixture.party(h,p,ally,second);
            ClericSpellDefinition blessing=ClericSpellCatalog.fromCode(14);
            for(ClericResourceCost absent:blessing.getResourceCosts()) {
                clear(p);clear(ally);clear(second);level(p,Skill.PRAYER,99);ClericBlessingsFixture.fund(p,blessing);
                p.getCarriedItems().remove(new Item[]{new Item(absent.getItemId(),100)},false);
                ClericBlessingsFixture.packet(p,14);
                for(ClericResourceCost cost:blessing.getResourceCosts())check(count(p,cost.getItemId())==(cost.getItemId()==absent.getItemId()?0:100),"missing blessing grade refuses atomically");
                check(com.openrsc.server.content.cleric.runtime.ClericTimedEffectRuntime.blessing(ally,ClericEffectFamily.SARADOMIN_BLESSING)==null,"missing grade applies no blessing");
            }
            clear(p);clear(ally);clear(second);level(p,Skill.PRAYER,99);ClericBlessingsFixture.fund(p,blessing);
            ClericBlessingsFixture.packet(p,14);
            for(ClericResourceCost cost:blessing.getResourceCosts())check(count(p,cost.getItemId())==99,"four-resource blessing cost spent once for multiple recipients");
            check(com.openrsc.server.content.cleric.runtime.ClericTimedEffectRuntime.blessing(ally,ClericEffectFamily.SARADOMIN_BLESSING)!=null
                &&com.openrsc.server.content.cleric.runtime.ClericTimedEffectRuntime.blessing(second,ClericEffectFamily.SARADOMIN_BLESSING)!=null,"one cast reaches both party allies");
            System.out.println("PASS "+checks+" native metal-sigil production, inventory, charging and mixed-cost assertions");
        }
    }
}
