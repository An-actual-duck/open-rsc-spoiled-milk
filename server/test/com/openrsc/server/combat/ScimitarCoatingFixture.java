package com.openrsc.server.combat;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.Bleed;
import com.openrsc.server.content.coatings.*;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.combat.*;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.*;
import com.openrsc.server.model.entity.player.*;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.struct.*;
import java.util.*;

public final class ScimitarCoatingFixture {
    private static int checks,serial;
    private static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    private static Player player(CurrentCombatHarness h) throws Exception {
        Player p=h.player("bleed"+(++serial),450,450);h.recordOutgoingPackets(p);p.getClientLimitations().maxItemId=65535;
        p.getSettings().setAppearance(new com.openrsc.server.model.PlayerAppearance(2,8,14,0,1,2));
        p.setPrayerBook(PrayerCatalog.GodLine.GUTHIX);
        for(int skill:new int[]{Skill.ATTACK.id(),Skill.STRENGTH.id(),Skill.PRAYER.id()})p.getSkills().setTemporaryLevelAndMaxStat(skill,99,99,false);
        return p;
    }
    private static Item add(Player p,int id) {
        check(p.getCarriedItems().getInventory().add(new Item(id)),"add "+id);
        return p.getCarriedItems().getInventory().get(p.getCarriedItems().getInventory().getLastIndexById(id));
    }
    private static void equip(Player p,int id) {
        Item item=add(p,id);EquipRequest r=new EquipRequest(p,item,EquipRequest.RequestType.FROM_INVENTORY,false);
        r.inventorySlot=p.getCarriedItems().getInventory().getLastIndexById(id);
        check(p.getCarriedItems().getEquipment().equipItem(r),"equip "+id);
    }
    private static Npc npc(CurrentCombatHarness h,int x) {
        Npc n=h.npc(NpcId.GREATER_DEMON.id(),x,450);n.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1000,1000,false);return n;
    }
    private static int hp(Npc n) {return n.getLevel(Skill.HITS.id());}
    private static void ticks(CurrentCombatHarness h,int count)throws Exception {for(int i=0;i<count;i++)h.advanceOneCombatTick();}
    public static void main(String[] args)throws Exception {
        com.openrsc.server.runtime.GameRandom rng=new com.openrsc.server.runtime.GameRandom() {
            final java.util.Random random=new java.util.Random(1);
            public int nextInt(int bound) {return random.nextInt(bound);}
            public double nextDouble() {return random.nextDouble();}
            public String describeState() {return "distribution-seed=1";}
        };
        check(ScimitarDamage.maximum(20)==18,"initial 10 percent ceiling reduction");
        // Exercise the conditional distribution, including strong defense and misses.
        for(int maximum:new int[]{5,8,12,20,40,80})for(int defense:new int[]{0,3,10,40}) {
            double old=0,now=0;int samples=200000;
            for(int i=0;i<samples;i++) {
                int roll=rng.nextIntInclusive(1,maximum),block=defense==0?0:rng.nextIntInclusive(1,defense);
                int raw=Math.max(0,roll-block),shaped=ScimitarDamage.reshape(raw,maximum-block,rng);
                old+=raw;now+=shaped;
                if(i<25)check((raw==0)==(shaped==0)&&shaped<=ScimitarDamage.maximum(maximum),"miss and cap preserved");
            }
            double error=(now-old)/samples;
            check(Math.abs(error)<0.035,"defense-aware ordinary mean: M="+maximum+" D="+defense+" error="+error);
            System.out.printf("BALANCE M=%d D=%d old=%.4f new=%.4f%n",maximum,defense,old/samples,now/samples);
        }
        check(!Bleed.qualifies(AttackRoll.normal(6).withWoundBasis(6,10),6),"below70");
        check(Bleed.qualifies(AttackRoll.normal(7).withWoundBasis(7,10),7),"exact70");
        check(!Bleed.qualifies(AttackRoll.normal(0).withWoundBasis(7,10),0),"blocked final damage");
        check(!Bleed.qualifies(AttackRoll.normal(99),99),"missing primary basis excluded");
        check(Bleed.qualifies(AttackRoll.critical(10,AttackRoll.Critical.AUTHORED).withWoundBasis(10,10),1),"delivered mitigated critical qualifies");
        try(CurrentCombatHarness h=new CurrentCombatHarness()) {
            h.server().getConfig().WANT_MYWORLD=true;h.server().getConfig().WANT_EQUIPMENT_TAB=true;
            for(int coated:ScimitarCoatingCatalog.coatedIds()) {
                int base=ScimitarCoatingCatalog.base(coated);Player p=player(h);Item weapon=add(p,base),dose=add(p,3447);
                int oldAppearance=h.server().getEntityHandler().getItemDef(base).getAppearanceId();
                check(ScimitarCoatings.apply(p,dose,weapon),"actual dose application");
                check(p.getCarriedItems().getInventory().countId(3447)==0,"dose consumed");
                check(p.getCarriedItems().getInventory().countId(ItemId.EMPTY_VIAL.id())==1,"vial returned");
                Item result=p.getCarriedItems().getInventory().get(p.getCarriedItems().getInventory().getLastIndexById(coated));
                check(result!=null&&result.getDef(h.world()).getAppearanceId()==oldAppearance,"effective held family unchanged "+base);
                check(result.getDef(h.world()).getMeleeOffense()==h.server().getEntityHandler().getItemDef(base).getMeleeOffense(),"base offense retained");
                check(!ScimitarCoatings.apply(p,add(p,3447),result),"no double coating");
                check(ScimitarCoatings.wipe(p,result,add(p,ItemId.WOOL.id())),"wool cleaning");
                check(p.getCarriedItems().getInventory().countId(base)==1&&p.getCarriedItems().getInventory().countId(coated)==0,"original weapon restored");
                check(!ScimitarCoatings.apply(p,dose,weapon),"stale identities cannot duplicate");
            }
            Player p=player(h),other=player(h);Npc a=npc(h,451),b=npc(h,452);
            AttackRoll qualifying=AttackRoll.normal(7).withWoundBasis(7,10);
            check(Bleed.primaryHit(p,a,1,qualifying,7),"first wound");
            check(Bleed.primaryHit(other,a,2,qualifying,7),"second player independently wounds same target");
            check(Bleed.primaryHit(p,b,3,qualifying,7),"same player wounds second target");
            check(Bleed.activeWounds(a)==2&&Bleed.activeWounds(b)==1,"per-source per-enemy ownership");
            int initial=hp(a);ticks(h,2);check(hp(a)==initial,"no immediate or early pulse");
            check(Bleed.primaryHit(p,a,1,qualifying,7),"reopen");
            check(Bleed.activeWounds(a)==2,"reopen cannot add another event");
            ticks(h,1);check(hp(a)<initial,"reopen does not postpone next pulse");
            ticks(h,6);check(Bleed.activeWounds(a)==0&&Bleed.activeWounds(b)==0,"three scheduled pulses end each wound");
            check(initial-hp(a)>=12&&initial-hp(a)<=24,"independent severity1 and2 pulse bounds");
            check(Bleed.primaryHit(p,a,1,qualifying,7),"new wound after completion");
            int before=hp(a);a.advanceCombatLifecycle();ticks(h,4);check(hp(a)==before&&Bleed.activeWounds(a)==0,"target lifecycle cancels old wounds");
            check(Bleed.primaryHit(other,a,1,qualifying,7),"wound before source logout");
            before=hp(a);other.setLoggedIn(false);ticks(h,3);check(hp(a)==before&&Bleed.activeWounds(a)==0,"source logout cancels");
            equip(p,ScimitarCoatingCatalog.coated(85));
            boolean opened=false;
            for(int i=0;i<80&&!opened;i++) {
                p.setOpponent(a);PvmMeleeEvent event=new PvmMeleeEvent(h.world(),p,a);p.setPvmMeleeEvent(event);event.run();event.stop();
                opened=Bleed.activeWounds(a)>0;
                ticks(h,4);
            }
            check(opened,"actual primary scimitar melee opens wound");
            Bleed.clear(a);
            check(!Bleed.primaryHit(p,a,2,CombatFormula.rollSecondaryMeleeDamage(p,a),5),"secondary formula excluded");
            check(!Bleed.primaryHit(p,a,2,CombatFormula.rollOffhandMeleeDamage(p,a),5),"offhand formula excluded");
            Object phase=new Object();NonCorporealState.tryEnter(a,phase,100,3,()->true);
            check(!Bleed.primaryHit(p,a,2,qualifying,5),"non-corporeal target cannot open a wound");
            NonCorporealState.clearOwned(a,phase);
            check(!Bleed.primaryHit(p,player(h),2,qualifying,5),"safe-zone player cannot be wounded");
            Player white=player(h);check(!white.getCarriedItems().getEquipment().ableToEquip(new Item(ScimitarCoatingCatalog.coated(2155))),"coated white scimitar retains god restriction");
            h.server().getConfig().WANT_PVP=true;
            Player attacker=player(h),victim=player(h);
            attacker.setLocation(Point.location(180,426),false);victim.setLocation(Point.location(181,426),false);
            check(attacker.getLocation().inWilderness()&&victim.getLocation().inWilderness(),"actual Wilderness fixtures");
            check(Bleed.primaryHit(attacker,victim,1,qualifying,5),"legal primary PvP wound");
            int health=victim.getLevel(Skill.HITS.id());victim.setLocation(Point.location(181,427),false);
            ticks(h,3);check(victim.getLevel(Skill.HITS.id())==health&&Bleed.activeWounds(victim)==0,"crossing Wilderness border cancels wound damage");
            Player lucky=player(h);Npc luckyTarget=npc(h,453);
            int robe=com.openrsc.server.content.EnchantingItemEffects.getWoolRobeProduct(
                com.openrsc.server.content.EnchantingItemEffects.COSMIC_ALTAR,ItemId.WOOL_WIZARD_HAT.id(),10);
            h.equip(lucky,robe,1);check(lucky.getCombatLuckChance()==.1,"actual robe luck");
            check(Bleed.primaryHit(lucky,luckyTarget,1,qualifying,5),"lucky player's wound");
            h.random().reset(1);h.random().scriptInts(0,2).scriptDoubles(0.0);
            int beforeLucky=hp(luckyTarget);
            for(GameTickEvent e:h.server().getGameEventHandler().getEvents())
                if(e.isRunning()&&e.getOwner()==luckyTarget&&e.getDescriptor().equals("Bleed wound"))e.run();
            check(beforeLucky-hp(luckyTarget)==3,"combat luck keeps better pulse roll without duplicating pulse");
            Bleed.clear(luckyTarget);
            h.server().getConfig().WANT_EQUIPMENT_TAB=false;
            Player legacy=player(h);Item legacyWeapon=add(legacy,3448);legacyWeapon.setWielded(true);
            check(ScimitarCoatingCatalog.equipped(legacy)==3448,"legacy inventory-wield weapon snapshot");
        }
        System.out.println("PASS "+checks+" scimitar balance, inventory, lifecycle and actual primary-hit checks");
    }
}
