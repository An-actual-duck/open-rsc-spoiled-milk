package com.openrsc.server.combat;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.*;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.projectile.*;
import com.openrsc.server.model.combat.ProjectileResourceLedger;
import com.openrsc.server.model.container.*;
import com.openrsc.server.model.entity.player.*;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.struct.*;
import java.lang.reflect.*;
import java.util.*;

/** Loaded definitions, real equipment/prayers and actual ranged launch resources. No listeners/accounts. */
public final class GodCrossbowFixture {
    private static int checks, serial;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void prayer(Player p,int level) {p.getSkills().setTemporaryLevelAndMaxStat(Skill.PRAYER.id(),level,99,false);}
    private static Player player(CurrentCombatHarness h,PrayerCatalog.GodLine god)throws Exception {
        Player p=h.player("godbow"+(++serial),450,450);h.recordOutgoingPackets(p);p.getClientLimitations().maxItemId=65535;
        p.getSettings().setAppearance(new com.openrsc.server.model.PlayerAppearance(2,8,14,0,1,2));
        p.getSkills().setTemporaryLevelAndMaxStat(Skill.RANGED.id(),80,80,false);prayer(p,99);p.setPrayerBook(god);return p;
    }
    private static void equip(Player p,int id,int amount) {
        check(p.getCarriedItems().getInventory().add(new Item(id,amount)),"inventory insertion "+id);
        int slot=p.getCarriedItems().getInventory().getLastIndexById(id);
        EquipRequest r=new EquipRequest(p,p.getCarriedItems().getInventory().get(slot),EquipRequest.RequestType.FROM_INVENTORY,false);r.inventorySlot=slot;
        check(p.getCarriedItems().getEquipment().equipItem(r),"actual equip "+id);
    }
    private static void unequip(Player p,Equipment.EquipmentSlot slot) {
        Item item=p.getCarriedItems().getEquipment().get(slot.getIndex());
        UnequipRequest r=new UnequipRequest(p,item,UnequipRequest.RequestType.FROM_EQUIPMENT,false);r.equipmentSlot=slot;
        check(p.getCarriedItems().getEquipment().unequipItem(r),"actual unequip "+slot);
    }
    private static ProjectileEvent fire(CurrentCombatHarness h,Player p)throws Exception {
        h.openCombatProjectileRectangle(448,456,449,453);Npc target=h.npc(NpcId.GREATER_DEMON.id(),452,450);
        target.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1000,1000,false);
        RangeEvent range=new RangeEvent(h.world(),p,0,target);p.setRangeEvent(range);range.run();
        for(GameTickEvent e:h.server().getGameEventHandler().getEvents())
            if(e instanceof ProjectileEvent&&((ProjectileEvent)e).getLaunchSnapshot().getSourceSnapshot().matches(p))return(ProjectileEvent)e;
        throw new AssertionError("actual ranged launch missing");
    }
    public static void main(String[] args)throws Exception {
        try(CurrentCombatHarness h=new CurrentCombatHarness()) {
            h.server().getConfig().WANT_MYWORLD=true;h.server().getConfig().WANT_EQUIPMENT_TAB=true;h.server().getConfig().WANT_FATIGUE=false;
            int[] bows={3444,3445,3446};PrayerCatalog.GodLine[] gods={PrayerCatalog.GodLine.SARADOMIN,PrayerCatalog.GodLine.ZAMORAK,PrayerCatalog.GodLine.GUTHIX};
            String[] names={"Saradomin's Divine Bolts","Zamorak's Bolts of Wrath","Guthix's Thorned Bolts"};
            for(int i=0;i<3;i++) {
                int bow=bows[i];PrayerCatalog.GodLine god=gods[i];Player p=player(h,god);
                check(PrayerCatalog.getDefinition(god,15).getKind()==PrayerCatalog.PrayerKind.SPECIAL,"mace slot preserved");
                check(PrayerCatalog.getDefinition(god,16).getName().equals(names[i]),"named book prayer");
                check(!p.getPrayers().canActivate(16),"requires matching equipped crossbow");
                check(h.server().getEntityHandler().getItemDef(bow).getRangedOffense()==38,"tier11 base");
                check(h.server().getEntityHandler().getItemDef(bow).getRequiredSkillIndex()==Skill.RANGED.id(),"Ranged equip gate");
                check(h.server().getEntityHandler().getItemDef(bow).isUntradable(),"untradeable artifact");
                check(RangeUtils.isCrossbow(bow)&&RangeUtils.canFire(bow,ItemId.POISON_DRAGON_BOLTS.id()),"ordinary top bolts compatible");
                p.getSkills().setTemporaryLevelAndMaxStat(Skill.PRAYER.id(),1,1,false);check(p.getCarriedItems().getEquipment().ableToEquip(new Item(bow)),"no extra Worship equip requirement");prayer(p,80);
                equip(p,bow,1);check(p.getPrayers().getAllocatedPoints()==0,"no reserve just for equip");
                prayer(p,79);check(!p.getPrayers().canActivate(16),"79 points rejected");prayer(p,80);check(p.getPrayers().canActivate(16),"80 boundary accepted");
                p.getPrayers().setPrayer(0,true);check(!p.getPrayers().canActivate(16),"other prayers consume same budget");p.getPrayers().setPrayer(0,false);
                p.getPrayers().setPrayer(16,true);check(p.getPrayers().getAllocatedPoints()==80,"80 allocated");
                check(p.getCarriedItems().getEquipment().getDisplayedRangedOffense()==50,"38 plus tier6 virtual power");
                equip(p,ItemId.POISON_DRAGON_BOLTS.id(),20);check(p.getCarriedItems().getEquipment().getDisplayedRangedOffense()==50,"physical poison bolts cannot add power");
                prayer(p,99);equip(p,GodCrossbows.cape(god),1);check(p.getPrayers().getAllocatedPoints()==60,"matching cape reserves60");
                check(p.getCarriedItems().getEquipment().getDisplayedRangedOffense()==54,"cape tier8 power");
                prayer(p,50);check(p.getPrayerAllocationPoints()==60&&GodCrossbows.usesHolyBolts(p),"no independent activation level gate: budget="+p.getPrayerAllocationPoints()+", reserve="+p.getPrayers().getAllocatedPoints()+", gearPrayer="+p.getPrayerPoints());
                prayer(p,49);p.getPrayers().deactivateOverflowingPrayers();check(!p.getPrayers().isPrayerActivated(16),"underfunded cape reserve deactivates");
                prayer(p,50);check(p.getPrayers().canActivate(16),"60 points after cape bonus");p.getPrayers().setPrayer(16,true);
                unequip(p,Equipment.EquipmentSlot.SLOT_CAPE);check(!p.getPrayers().isPrayerActivated(16),"cape removal deactivates when80 cannot fit");
                prayer(p,99);p.getPrayers().setPrayer(16,true);unequip(p,Equipment.EquipmentSlot.SLOT_MAINHAND);check(!p.getPrayers().isPrayerActivated(16),"weapon removal deactivates");
                equip(p,bow,1);p.getPrayers().setPrayer(16,true);p.setPrayerBook(gods[(i+1)%3]);check(!p.getPrayers().isPrayerActivated(16),"book change clears prayer");
                check(!p.getCarriedItems().getEquipment().ableToEquip(new Item(bow)),"opposing book cannot equip");
                p.setPrayerBook(god);prayer(p,99);Devotion.setDevotionLevel(p,god,800);
                Method pool=GodArtifacts.class.getDeclaredMethod("getUnclaimedArtifacts",Player.class,PrayerCatalog.GodLine.class);pool.setAccessible(true);
                List<?> all=(List<?>)pool.invoke(null,p,god);check(all.size()==4,"four artifacts per god");
                for(Object id:all)if(((Integer)id)!=bow)p.getCache().store("god_artifact_claimed_"+god.name().toLowerCase()+"_"+id,true);
                check(GodArtifacts.isEligible(p,god),"legacy three-artifact account eligible");
                check(((List<?>)pool.invoke(null,p,god)).equals(Collections.singletonList(bow)),"only new crossbow remains");
                p.getCache().store("god_artifact_claimed_"+god.name().toLowerCase()+"_"+bow,true);check(!GodArtifacts.isEligible(p,god),"fourth claim exhausts pool");
            }
            for(int ammo:new int[]{-1,ItemId.TIN_ARROWS.id(),ItemId.POISON_DRAGON_BOLTS.id()}) {
                Player p=player(h,PrayerCatalog.GodLine.SARADOMIN);equip(p,3444,1);if(ammo>0)equip(p,ammo,20);p.getPrayers().setPrayer(16,true);
                ProjectileEvent event=fire(h,p);ProjectileResourceLedger ledger=(ProjectileResourceLedger)CurrentCombatHarness.readPrivateField(event,"resourceLedger");
                check(ledger.getItemCosts().isEmpty()&&ledger.getRecoveries().isEmpty(),"holy launch has no physical ammo ledger");
                check(event.getLaunchSnapshot().getSpecification().getPoisonWeaponId()==-1,"holy launch cannot inherit bolt poison");
                if(ammo>0)check(p.getCarriedItems().getEquipment().getAmmoItem().getAmount()==20,"overridden ammo untouched");
                event.stop();p.getRangeEvent().stop();
            }
            Player normal=player(h,PrayerCatalog.GodLine.SARADOMIN);equip(normal,3444,1);equip(normal,ItemId.MITHRIL_BOLTS.id(),20);
            ProjectileEvent event=fire(h,normal);ProjectileResourceLedger ledger=(ProjectileResourceLedger)CurrentCombatHarness.readPrivateField(event,"resourceLedger");
            check(normal.getCarriedItems().getEquipment().getAmmoItem().getAmount()==19,"prayer off consumes ordinary bolt");
            check(ledger.getItemCosts().size()==1&&ledger.getRecoveries().size()==1,"ordinary ammo resources retained");
            check(event.getLaunchSnapshot().getSpecification().getPoisonWeaponId()==ItemId.MITHRIL_BOLTS.id(),"ordinary ammo identity retained");
            event.stop();normal.getRangeEvent().stop();
            h.server().getConfig().WANT_EQUIPMENT_TAB=false;
            Player legacy=player(h,PrayerCatalog.GodLine.GUTHIX);legacy.getCarriedItems().getInventory().add(new Item(3446));
            legacy.getCarriedItems().getInventory().get(legacy.getCarriedItems().getInventory().getLastIndexById(3446)).setWielded(true);
            check(legacy.getPrayers().canActivate(16),"legacy inventory mode can activate");legacy.getPrayers().setPrayer(16,true);
            check(legacy.getCarriedItems().getEquipment().getDisplayedRangedOffense()==50,"legacy virtual ammo power");
            event=fire(h,legacy);ledger=(ProjectileResourceLedger)CurrentCombatHarness.readPrivateField(event,"resourceLedger");
            check(ledger.getItemCosts().isEmpty()&&ledger.getRecoveries().isEmpty(),"legacy holy launch has no physical cost/recovery");
        }
        System.out.println("PASS "+checks+" god-crossbow definition, reward, equip, prayer, power and actual launch checks");
    }
}
