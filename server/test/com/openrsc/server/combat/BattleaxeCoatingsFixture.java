package com.openrsc.server.combat;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.*;
import com.openrsc.server.content.coatings.*;
import com.openrsc.server.external.*;
import com.openrsc.server.model.container.*;
import com.openrsc.server.model.entity.*;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.PoisonEvent;
import com.openrsc.server.plugins.triggers.*;
import java.lang.reflect.*;
import java.util.*;

/** Actual game paths: exact item exchanges, resolved melee, shared effects and save snapshot. */
public final class BattleaxeCoatingsFixture {
    private static int assertions;
    private static final int[] BASES={89,90,91,92,93,205,429,2002,2013,2024,2035,2156,2752,3118,3269};
    private static final int[] TIERS={4,5,6,8,10,3,5,1,2,7,9,5,11,5,12};
    private static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private static Player player(CurrentCombatHarness h,String name)throws Exception {
        Player p=h.player(name,440,440);p.getClientLimitations().maxItemId=Integer.MAX_VALUE;h.recordOutgoingPackets(p);
        p.getSettings().setAppearance(new com.openrsc.server.model.PlayerAppearance(0,0,0,0,1,2));
        p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1000,1000,false);
        p.setQuestStage(Quests.DRUIDIC_RITUAL,-1);return p;
    }
    private static Item add(Player p,int id){p.getCarriedItems().getInventory().add(new Item(id));return find(p,id);}
    private static Item find(Player p,int id){for(Item i:p.getCarriedItems().getInventory().getItems())if(i.getCatalogId()==id)return i;return null;}
    public static void main(String[] args)throws Exception {
        try(CurrentCombatHarness h=new CurrentCombatHarness()) {
            h.server().getConfig().MEMBER_WORLD=true;h.server().getConfig().WANT_FATIGUE=false;
            Object actions=Class.forName("com.openrsc.server.plugins.custom.itemactions.BattleaxeCoatingActions").getConstructor().newInstance();
            UseInvTrigger use=(UseInvTrigger)actions;OpInvTrigger op=(OpInvTrigger)actions;
            for(int n=0;n<BASES.length;n++) {
                int base=BASES[n],coated=3411+n;
                check(BattleaxeCoatingCatalog.coated(base)==coated&&BattleaxeCoatingCatalog.base(coated)==base,"explicit full roster mapping");
                ItemDefinition b=h.server().getEntityHandler().getItemDef(base),c=h.server().getEntityHandler().getItemDef(coated);
                for(String getter:new String[]{"getMeleeOffense","getRangedOffense","getMagicOffense","getWeaponSpeed","getMeleeDefense","getRangedDefense","getMagicDefense","getRequiredLevel","getRequiredSkillIndex","getArmourBonus","getWeaponAimBonus","getWeaponPowerBonus","getPrayerBonus","getMagicBonus","getWearableId","getWieldPosition","isMembersOnly","isStackable","isUntradable","isNoteable","isWieldable"}) {
                    Method method=ItemDefinition.class.getMethod(getter);check(method.invoke(b).equals(method.invoke(c)),"inherited effective definition "+base+" "+getter);
                }
                check(c.getDefaultPrice()==b.getDefaultPrice()+300,"approved coating economy");
                check(PoisonPower.getWeaponAppliedPoisonPower(coated)==TIERS[n]*2&&PoisonPower.getWeaponMaxPoisonPower(coated)==TIERS[n]*5,"half poison across all15 including sidegrades");
                check(PoisonPower.getWeaponAppliedPoisonPower(base)==0,"no intrinsic plain weapon poison");
                for(boolean reverse:new boolean[]{false,true}) {
                    Player p=player(h,"coat"+n+reverse);Item weapon=add(p,base),dose=add(p,3410);
                    long identity=weapon.getItemId();weapon.getItemStatus().setDurability(73);
                    while(p.getCarriedItems().getInventory().size()<p.getCarriedItems().getInventory().getCapacity())add(p,ItemId.CHISEL.id());
                    check(use.blockUseInv(p,0,reverse?weapon:dose,reverse?dose:weapon),"both application orders routed");
                    use.onUseInv(p,0,reverse?weapon:dose,reverse?dose:weapon);
                    Item result=find(p,coated);check(result!=null&&result.getItemId()==identity&&result.getItemStatus().getDurability()==73,"full inventory application preserves identity/durability");
                    check(find(p,base)==null&&find(p,3410)==null&&find(p,ItemId.EMPTY_VIAL.id())!=null,"one dose for one weapon and empty vial");
                    check(!BattleaxeCoatings.apply(p,dose,weapon),"stale refs cannot duplicate application");
                    snapshot(p,result);
                    check(op.blockOpInv(p,0,result,"Wipe clean"),"explicit wipe action");op.onOpInv(p,0,result,"Wipe clean");
                    check(find(p,base)!=null&&find(p,base).getItemId()==identity&&find(p,3410)==null,"wipe returns base with no refund");
                    check(!BattleaxeCoatings.wipe(p,result),"stale wipe cannot duplicate");
                }
                // Invalid, noted, equipped, unsupported and identical coating cases preserve all inputs.
                for(int mode=0;mode<6;mode++) {
                    Player p=player(h,"reject"+n+"-"+mode);Item weapon=add(p,mode==3?coated:base),dose=add(p,3410);
                    if(mode==0)weapon.getItemStatus().setNoted(true);
                    if(mode==1)dose.getItemStatus().setNoted(true);
                    if(mode==2)weapon.setWielded(true);
                    if(mode==4)p.getClientLimitations().maxItemId=3410;
                    Item supplied=mode==5?weapon.copy():weapon;
                    check(!BattleaxeCoatings.apply(p,dose,supplied),"invalid conversion rejected "+mode);
                    check(p.getCarriedItems().getInventory().size()==2&&find(p,3410)==dose,"rejection never loses a dose "+mode);
                }
                Player p=player(h,"hitcoat"+n);h.equip(p,coated,1);
                Npc target=h.npc(3,441,440);target.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1000,1000,false);
                PvmMeleeEvent event=new PvmMeleeEvent(h.world(),p,target);
                Method hit=PvmMeleeEvent.class.getDeclaredMethod("inflictDamage",Mob.class,Mob.class,int.class,boolean.class,boolean.class);hit.setAccessible(true);
                Method poison=PvmMeleeEvent.class.getDeclaredMethod("applyWeaponPoison",Mob.class,Mob.class,int.class);poison.setAccessible(true);
                com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(0);
                poison.invoke(event,p,target,5);hit.invoke(event,p,target,5,false,false);
                check(Slow.power(target)==4&&Slow.cap(target)==12&&Slow.tier(target)==0,"actual primary resolved hit adds flat4/cap12 at every tier");
                check(target.getCurrentPoisonPower()==TIERS[n]*2&&target.getPoisonMaxPower()==TIERS[n]*5,"actual first standard weapon proc uses half payload");
                check(p.getAttribute("poison_proc_weapon_chance",-1)==50,"exactly one first weapon proc, no duplicate roll");
                int before=Slow.power(target);
                hit.invoke(event,p,target,0,false,false);hit.invoke(event,p,target,5,true,false);hit.invoke(event,p,target,5,false,true);
                check(Slow.power(target)==before,"zero/suppressed/offhand paths never add Slow");
                for(int pulse=0;pulse<5;pulse++)hit.invoke(event,p,target,1,false,false);
                check(Slow.power(target)==12&&Slow.tier(target)==1,"single source saturates Slow1 with two power buffer");
                Slow.apply(target,22,Slow.CAP_TWO);hit.invoke(event,p,target,1,false,false);
                check(Slow.power(target)==22&&Slow.cap(target)==22,"retained higher shared cap is not lowered");
                long start=h.server().getCurrentTick();while(h.server().getCurrentTick()<start+4)h.advanceOneCombatTick();
                check(Slow.power(target)==20,"coating does not reset shared four-tick decay");
                target.advanceCombatLifecycle();check(Slow.power(target)==0,"target lifecycle clears shared state");
                Npc dying=h.npc(3,441,440);dying.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1,1,false);
                hit.invoke(event,p,dying,10,false,false);check(Slow.power(dying)==0,"killing hit never applies Slow");
            }
            recipes(h,use);
            poisonBoundaries(h);
            armorPoisonPolicy(h);
            mitigatedPoisonTiming(h);
            policyAndSnapshot(h);
        }
        System.out.println("PASS battleaxe coatings: "+assertions+" assertions; all15 templates, atomic recipe/apply/wipe, actual primary damage and poison paths");
    }

    private static void recipes(CurrentCombatHarness h,UseInvTrigger use)throws Exception {
        check(h.server().getEntityHandler().getItemHerbDef(450).getReqLevel()==46,"unfinished Avantoe prerequisite aligned50 to46");
        Object herblaw=Class.forName("com.openrsc.server.plugins.authentic.skills.herblaw.Herblaw").getConstructor().newInstance();
        Method unfinishedMix=herblaw.getClass().getDeclaredMethod("batchPotionMaking",Player.class,Item.class,ItemHerbDef.class,Item.class);unfinishedMix.setAccessible(true);
        for(int level:new int[]{45,46,50}) {
            Player maker=player(h,"unfinished"+level);maker.getSkills().setTemporaryLevelAndMaxStat(Skill.HERBLAW.id(),level,level,false);
            Item herb=add(maker,450),water=add(maker,ItemId.VIAL.id());
            com.openrsc.server.plugins.Functions.startbatch(maker,1);unfinishedMix.invoke(herblaw,maker,herb,h.server().getEntityHandler().getItemHerbDef(450),water);
            check((find(maker,460)!=null)==(level>=46),"actual unfinished prerequisite boundary "+level);
            check((find(maker,450)!=null)==(level<46)&&(find(maker,ItemId.VIAL.id())!=null)==(level<46),"unfinished recipe input preservation "+level);
        }
        for(int level:new int[]{45,46,50})for(boolean reverse:new boolean[]{false,true}) {
            Player p=player(h,"recipe"+level+reverse);p.getSkills().setTemporaryLevelAndMaxStat(Skill.HERBLAW.id(),level,level,false);
            Item unfinished=add(p,460),eye=add(p,3342),gland=add(p,3341);int before=p.getSkills().getExperience(Skill.HERBLAW.id());
            use.onUseInv(p,0,reverse?eye:unfinished,reverse?unfinished:eye);
            check((find(p,3410)!=null)==(level>=46),"actual recipe boundary "+level);
            check((find(p,460)!=null)==(level<46)&&(find(p,3342)!=null)==(level<46)&&(find(p,3341)!=null)==(level<46),"atomic triple input removal or no-loss rejection");
            if(level>=46) {
                check(p.getCarriedItems().getInventory().countId(3410)==1,"one single-use dose, never three");
                Player control=player(h,"xpcontrol"+level+reverse);control.getSkills().setTemporaryLevelAndMaxStat(Skill.HERBLAW.id(),level,level,false);
                int controlBefore=control.getSkills().getExperience(Skill.HERBLAW.id());control.incExp(Skill.HERBLAW.id(),300,true);
                check(p.getSkills().getExperience(Skill.HERBLAW.id())-before==control.getSkills().getExperience(Skill.HERBLAW.id())-controlBefore,"exact recipe300XP under ordinary multiplier path");
                check(!BattleaxeCoatings.mix(p,unfinished,eye),"stale recipe cannot duplicate output");
            }else check(p.getSkills().getExperience(Skill.HERBLAW.id())==before,"rejected recipe awards no XP");
        }
        for(int missing:new int[]{460,3341,3342}) {
            Player p=player(h,"missing"+missing);p.getSkills().setTemporaryLevelAndMaxStat(Skill.HERBLAW.id(),99,99,false);
            for(int id:new int[]{460,3341,3342})if(id!=missing)add(p,id);
            Item a=p.getCarriedItems().getInventory().get(0),b=p.getCarriedItems().getInventory().get(1);
            check(!BattleaxeCoatings.mix(p,a,b)&&p.getCarriedItems().getInventory().size()==2,"missing ingredient no-loss "+missing);
        }
        Player p=player(h,"cross poison");Item dose=add(p,3410),other=add(p,ItemId.POISONED_RUNE_DAGGER.id());
        check(!BattleaxeCoatings.apply(p,dose,other)&&find(p,3410)==dose,"cannot coat ordinary poisoned weapon");
        Item coated=add(p,BattleaxeCoatingCatalog.coated(93));Item ordinary=add(p,ItemId.WEAPON_POISON.id());
        UseInvTrigger poisoning=(UseInvTrigger)Class.forName("com.openrsc.server.plugins.authentic.itemactions.InvItemPoisoning").getConstructor().newInstance();
        poisoning.onUseInv(p,0,ordinary,coated);
        check(find(p,ItemId.WEAPON_POISON.id())==ordinary&&find(p,coated.getCatalogId())==coated,"ordinary poison rejected without loss on coated weapon");
    }

    private static void poisonBoundaries(CurrentCombatHarness h)throws Exception {
        Player p=player(h,"mixed coat source"),other=player(h,"strong poison source");Npc target=h.npc(3,441,440);
        target.getSkills().setTemporaryLevelAndMaxStat(3,1000,1000,false);
        h.equip(p,BattleaxeCoatingCatalog.coated(2002),1);
        PvmMeleeEvent event=new PvmMeleeEvent(h.world(),p,target);Method poison=PvmMeleeEvent.class.getDeclaredMethod("applyWeaponPoison",Mob.class,Mob.class,int.class);poison.setAccessible(true);
        com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(0);poison.invoke(event,p,target,10);
        check(target.getCurrentPoisonPower()==2&&target.getPoisonMaxPower()==5,"Tin no minimum applied/cap");
        ((PoisonEvent)target.getAttribute("poisonEvent",null)).run();check(target.getCurrentPoisonPower()==0,"Tin subthreshold uses normal cleanup without invented grace");
        h.advanceOneCombatTick(); // retire the stopped ONE_PER_MOB event before a new lifetime stream
        target.applyPoison(40,100,other);
        p.removeAttribute("poison_proc_weapon_source");com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(0);poison.invoke(event,p,target,10);
        check(target.getCurrentPoisonPower()==42&&target.getPoisonMaxPower()==100,"weak Tin still contributes to stronger target-owned pool");
        check(target.getPoisonProvenance().getSourceId().equals(p.getUUID()),"positive weak addition transfers ownership through maintained policy");
        ((PoisonEvent)target.getAttribute("poisonEvent",null)).run();check(target.getCurrentPoisonPower()==39,"existing natural poison drain3 unchanged");
        int before=target.getCurrentPoisonPower();poison.invoke(event,p,target,0);check(target.getCurrentPoisonPower()==before,"zero damage never procs poison");
        for(int id:new int[]{1890,1891,1892,1893,1894})h.equip(p,id,1);
        // Carapace has cleanse rather than poison; apply no invented poison bonus.
        check(p.getMeleePoisonArmorMaxPower()==0,"cleanse armor not conflated with poison armor");
        Player victim=player(h,"PVP coat target");BattleaxeCoatings.primaryHit(p,victim,BattleaxeCoatingCatalog.coated(93),10);
        check(Slow.power(victim)==0,"no PvP Slow producer");
        h.server().getConfig().SHUFFLE_PID_ORDER=false;
        com.openrsc.server.event.rsc.impl.combat.CombatEvent pvp=new com.openrsc.server.event.rsc.impl.combat.CombatEvent(h.world(),p,victim);
        Method pvpp= pvp.getClass().getDeclaredMethod("applyWeaponPoison",Mob.class,Mob.class,int.class);pvpp.setAccessible(true);
        pvpp.invoke(pvp,p,victim,10);check(victim.getCurrentPoisonPower()==0,"coated poison excluded from PvP while plain armor semantics retained");
    }

    private static void armorPoisonPolicy(CurrentCombatHarness h)throws Exception {
        // Current MyWorld style armor poison is retired. Exercise its preserved
        // legacy profile as well, using the exact same helper and native set.
        h.server().getConfig().WANT_MYWORLD=false;
        try {
            for(int weapon:new int[]{BattleaxeCoatingCatalog.coated(2002),ItemId.POISONED_TIN_SPEAR.id()}) {
                Player p=player(h,"armorpolicy"+weapon);for(int id=1860;id<=1864;id++)h.equip(p,id,1);h.equip(p,weapon,1);
                Npc n=h.npc(3,441,440);n.getSkills().setTemporaryLevelAndMaxStat(3,1000,1000,false);
                PvmMeleeEvent event=new PvmMeleeEvent(h.world(),p,n);Method poison=PvmMeleeEvent.class.getDeclaredMethod("applyWeaponPoison",Mob.class,Mob.class,int.class);poison.setAccessible(true);
                com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(0);poison.invoke(event,p,n,10);
                check(n.getCurrentPoisonPower()==5,"weapon/armor uses unchanged max-applied aggregation");
                check(n.getPoisonMaxPower()==(BattleaxeCoatingCatalog.isCoated(weapon)?15:20),"armor contribution unchanged beside weaker weapon cap");
                check(p.getAttribute("poison_proc_weapon_chance",-1)==50&&p.getAttribute("poison_proc_armor_melee_chance",-1)==40,"one weapon and one armor proc exactly");
                java.util.Random control=new java.util.Random(0);control.nextInt(100);control.nextInt(100);
                check(com.openrsc.server.util.rsc.DataConversions.getRandom().nextInt(100)==control.nextInt(100),"no extra poison/Slow RNG changes armor sequence");
            }
        } finally {h.server().getConfig().WANT_MYWORLD=true;}
    }

    private static void mitigatedPoisonTiming(CurrentCombatHarness h)throws Exception {
        for(int weapon:new int[]{BattleaxeCoatingCatalog.coated(2002),ItemId.POISONED_TIN_SPEAR.id()}) {
            Player p=player(h,"mitigated"+weapon);h.equip(p,weapon,1);Npc target=h.npc(869,441,440);
            target.getSkills().setTemporaryLevelAndMaxStat(3,1000,1000,false);
            com.openrsc.server.content.monsterslayer.DarkBeastCombat.startCharge(target);
            check(com.openrsc.server.content.monsterslayer.DarkBeastCombat.charging(target),"real downstream mitigation prepared");
            PvmMeleeEvent event=new PvmMeleeEvent(h.world(),p,target);
            Method poison=PvmMeleeEvent.class.getDeclaredMethod("applyWeaponPoison",Mob.class,Mob.class,int.class);poison.setAccessible(true);
            Method hit=PvmMeleeEvent.class.getDeclaredMethod("inflictDamage",Mob.class,Mob.class,int.class,boolean.class,boolean.class);hit.setAccessible(true);
            com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(0);poison.invoke(event,p,target,1);hit.invoke(event,p,target,1,false,false);
            check(target.getLevel(3)==1000&&Slow.power(target)==0,"formula-positive but downstream-zero damage cannot apply Slow");
            check(target.getCurrentPoisonPower()==(BattleaxeCoatingCatalog.isCoated(weapon)?2:4),"coated and ordinary poison retain existing pre-mitigation policy");
            check(p.getAttribute("poison_proc_weapon_chance",-1)==50,"downstream zero does not reroll/revert ordinary proc state");
        }
    }

    private static void policyAndSnapshot(CurrentCombatHarness h)throws Exception {
        Player p=player(h,"family preservation");Method family=Equipment.class.getDeclaredMethod("isBattleaxe",Item.class);family.setAccessible(true);
        for(int base:BASES)check(family.invoke(p.getCarriedItems().getEquipment(),new Item(base)).equals(family.invoke(p.getCarriedItems().getEquipment(),new Item(BattleaxeCoatingCatalog.coated(base)))),"exact inherited high-roll membership including Grey/Exalted");
        Npc target=h.npc(3,441,440);target.getSkills().setTemporaryLevelAndMaxStat(3,1000,1000,false);
        Method committed=PvmMeleeEvent.class.getDeclaredMethod("inflictDamage",Mob.class,Mob.class,int.class,boolean.class,boolean.class,int.class);committed.setAccessible(true);
        PvmMeleeEvent event=new PvmMeleeEvent(h.world(),p,target);
        h.equip(p,BattleaxeCoatingCatalog.coated(93),1);committed.invoke(event,p,target,1,false,false,93);
        check(Slow.power(target)==0,"equipping coating after commitment cannot retroactively add Slow");
        h.equip(p,93,1);committed.invoke(event,p,target,1,false,false,BattleaxeCoatingCatalog.coated(93));
        check(Slow.power(target)==4,"swapping away after commitment retains original hit's coating");
        h.server().getConfig().EQUIP_QUEST_ITEMS_WITHOUT_QUESTS=false;
        p.getSkills().setTemporaryLevelAndMaxStat(Skill.MELEE.id(),99,99,false);
        Field equipment=Equipment.class.getDeclaredField("list");equipment.setAccessible(true);Arrays.fill((Item[])equipment.get(p.getCarriedItems().getEquipment()),null);
        Item dragon=add(p,BattleaxeCoatingCatalog.coated(2752));
        check(!p.getCarriedItems().getEquipment().equipItem(new com.openrsc.server.model.struct.EquipRequest(p,dragon,com.openrsc.server.model.struct.EquipRequest.RequestType.FROM_INVENTORY,false),false),"coated Dragon retains Hero quest gate");
        p.setQuestStage(Quests.HEROS_QUEST,-1);
        check(p.getCarriedItems().getEquipment().equipItem(new com.openrsc.server.model.struct.EquipRequest(p,dragon,com.openrsc.server.model.struct.EquipRequest.RequestType.FROM_INVENTORY,false),false),"coated Dragon equips after Hero quest");
    }

    // Capture the real database save representation into memory; no DB constructor,
    // connection, query, credentials or account is touched.
    public static final class MemorySnapshot extends com.openrsc.server.database.impl.mysql.MySqlGameDatabase {
        com.openrsc.server.database.struct.PlayerInventory[] rows;
        private MemorySnapshot(){super(null);}
        @Override public void savePlayerInventory(int id,com.openrsc.server.database.struct.PlayerInventory[] rows){this.rows=rows;}
    }
    private static void snapshot(Player p,Item item)throws Exception {
        Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        MemorySnapshot db=(MemorySnapshot)((sun.misc.Unsafe)field.get(null)).allocateInstance(MemorySnapshot.class);
        db.savePlayerInventory(p);boolean found=false;
        for(com.openrsc.server.database.struct.PlayerInventory row:db.rows)if(row.itemId==item.getItemId()) {
            Item restored=new Item(row.catalogID,row.amount,row.noted,row.itemId);restored.getItemStatus().setDurability(row.durability);
            check(restored.getCatalogId()==item.getCatalogId()&&restored.getItemId()==item.getItemId()&&restored.getItemStatus().getDurability()==73,"maintained save snapshot round-trip retains coated identity/durability");found=true;
        }
        check(found,"coated instance appears in actual persistence snapshot");
    }
}
