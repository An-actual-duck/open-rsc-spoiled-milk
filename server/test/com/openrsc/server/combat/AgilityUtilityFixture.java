package com.openrsc.server.combat;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.AgilityUtilityEquipment;
import com.openrsc.server.model.container.*;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.struct.*;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.external.ItemDefinition;
import java.util.*;
import java.lang.reflect.*;

/** Isolated real equipment, cache, walking queue, XP pipeline, reward and obstacle integrations. */
public final class AgilityUtilityFixture {
    private static int checks,serial;
    private static final int B=3472,G=3473,C=3474;
    private static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    private static Player player(CurrentCombatHarness h)throws Exception{
        Player p=h.player("utility"+(++serial),450,450);h.recordOutgoingPackets(p);p.getClientLimitations().maxItemId=65535;
        p.getSkills().setTemporaryLevelAndMaxStat(Skill.AGILITY.id(),99,99,false);
        p.getSettings().setAppearance(new com.openrsc.server.model.PlayerAppearance(2,8,14,0,1,2));return p;
    }
    private static void equip(Player p,int id){
        check(p.getCarriedItems().getInventory().add(new Item(id)),"insert "+id);int slot=p.getCarriedItems().getInventory().getLastIndexById(id);
        EquipRequest r=new EquipRequest(p,p.getCarriedItems().getInventory().get(slot),EquipRequest.RequestType.FROM_INVENTORY,false);r.inventorySlot=slot;
        check(p.getCarriedItems().getEquipment().equipItem(r),"equip "+id);
    }
    private static void remove(Player p,Equipment.EquipmentSlot slot){
        UnequipRequest r=new UnequipRequest(p,p.getCarriedItems().getEquipment().get(slot.getIndex()),UnequipRequest.RequestType.FROM_EQUIPMENT,false);r.equipmentSlot=slot;
        check(p.getCarriedItems().getEquipment().unequipItem(r),"unequip");
    }
    private static int xp(Player p){return p.getSkills().getExperience(Skill.AGILITY.id());}
    private static void steps(Player p,int count){for(int i=0;i<count;i++)AgilityUtilityEquipment.walkedTile(p);}
    private static void resetBoot(Player p){p.getCache().set(AgilityUtilityEquipment.BOOT_CHARGE,100);p.getCache().set(AgilityUtilityEquipment.BOOT_PROGRESS,0);}
    private static List<Item> uniques(CurrentCombatHarness h,Player p,int tier,Integer...rolls){h.random().reset(1);h.random().scriptInts(rolls);return AgilityUtilityEquipment.rollUniques(p,tier,h.random());}
    public static void main(String[] args)throws Exception{
        try(CurrentCombatHarness h=new CurrentCombatHarness()){
            h.server().getConfig().WANT_MYWORLD=true;h.server().getConfig().WANT_EQUIPMENT_TAB=true;h.server().getConfig().WANT_FATIGUE=false;
            h.server().getConfig().WANT_CUSTOM_WALK_SPEED=true;h.server().getConfig().WALKING_TICK=430;h.server().getConfig().PLAYER_BLOCKING=0;
            h.openCombatProjectileRectangle(440,470,440,455);
            for(int id:new int[]{B,G,C}){
                ItemDefinition d=h.server().getEntityHandler().getItemDef(id);
                check(d!=null&&d.isWieldable()&&d.isUntradable()&&!d.isNoteable(),"utility flags "+id);
                check(d.getArmourBonus()==0&&d.getMeleeDefense()==0&&d.getRangedDefense()==0&&d.getMagicDefense()==0&&d.getMagicBonus()==0&&d.getPrayerBonus()==0,"zero stats "+id);
            }
            Player p=player(h);equip(p,B);check(AgilityUtilityEquipment.bootCharge(p)==100&&p.getWalkingTick()==344,"full boots, 1.25x cadence");
            int before=xp(p);p.incExp(Skill.AGILITY.id(),100,false);check(xp(p)-before==110,"boots XP");
            steps(p,49);check(AgilityUtilityEquipment.bootCharge(p)==100,"49 tiles no full percent");steps(p,1);check(AgilityUtilityEquipment.bootCharge(p)==99,"50 tiles drain1");
            equip(p,G);resetBoot(p);steps(p,69);check(AgilityUtilityEquipment.bootCharge(p)==100,"69 with gloves");steps(p,1);check(AgilityUtilityEquipment.bootCharge(p)==99,"70 with gloves");
            equip(p,C);resetBoot(p);steps(p,99);check(AgilityUtilityEquipment.bootCharge(p)==100,"99 full set");steps(p,1);check(AgilityUtilityEquipment.bootCharge(p)==99,"100 full set");
            check(AgilityUtilityEquipment.xpPercent(p)==30,"additive30");
            p.getCache().set(AgilityUtilityEquipment.XP_REMAINDER,0);before=xp(p);steps(p,10);check(xp(p)-before==52,"10 walking XP boosted30 with quarter-XP remainder");
            remove(p,Equipment.EquipmentSlot.SLOT_GLOVES);check(AgilityUtilityEquipment.tilesPerPercent(p)==80,"cape alone adds30");
            equip(p,G);p.getCache().set(AgilityUtilityEquipment.BOOT_CHARGE,0);before=xp(p);p.incExp(Skill.AGILITY.id(),100,false);
            check(xp(p)-before==130&&p.getWalkingTick()==430,"empty boots keep XP but no speed");before=xp(p);steps(p,20);check(xp(p)==before,"empty boots no cape walking XP");
            for(int tier=1;tier<=3;tier++){p.getCache().set(AgilityUtilityEquipment.BOOT_CHARGE,0);AgilityUtilityEquipment.completedLap(p,tier);check(AgilityUtilityEquipment.bootCharge(p)==tier,"lap recharge "+tier);}
            p.getCache().set(AgilityUtilityEquipment.BOOT_CHARGE,99);AgilityUtilityEquipment.completedLap(p,3);check(AgilityUtilityEquipment.bootCharge(p)==100,"cap100");
            p.setCombatTimer();int charge=AgilityUtilityEquipment.bootCharge(p),progress=p.getCache().getInt(AgilityUtilityEquipment.BOOT_PROGRESS);
            check(!AgilityUtilityEquipment.speedActive(p),"combat timer blocks");steps(p,100);
            check(AgilityUtilityEquipment.bootCharge(p)==charge&&p.getCache().getInt(AgilityUtilityEquipment.BOOT_PROGRESS)==progress,"inactive movement no drain");
            h.clock().advanceMillis(9999);check(!AgilityUtilityEquipment.speedActive(p),"9999ms blocked");h.clock().advanceMillis(1);check(AgilityUtilityEquipment.speedActive(p),"10000ms restores");
            Npc attacker=h.npc(NpcId.MOSS_GIANT.id(),451,450);attacker.getCombatEngagementAuthority().beginOutgoing(p,CombatStyle.MELEE,false);
            check(!AgilityUtilityEquipment.speedActive(p),"incoming decoupled combat blocks speed");
            attacker.getCombatEngagementAuthority().closeOutgoing(CombatEngagementTerminalReason.EVENT_ENDED);
            check(!AgilityUtilityEquipment.speedActive(p),"end starts full cooldown");h.clock().advanceMillis(10000);check(AgilityUtilityEquipment.speedActive(p),"end cooldown completes");
            // A genuine queued displacement counts once; teleports and a blocked/empty path do not count.
            resetBoot(p);int start=xp(p);p.walk(451,450);p.updatePosition();check(p.getX()==451&&xp(p)>start,"real queued step grants walking XP");
            progress=p.getCache().getInt(AgilityUtilityEquipment.BOOT_PROGRESS);p.teleport(452,450,0,false);check(p.getCache().getInt(AgilityUtilityEquipment.BOOT_PROGRESS)==progress,"teleport not charged");
            p.getWalkingQueue().reset();p.updatePosition();check(p.getCache().getInt(AgilityUtilityEquipment.BOOT_PROGRESS)==progress,"idle not charged");
            // Snapshot/reload exactly the same string/number cache contract used by player saves.
            Map<String,Object> snapshot=new HashMap<>(p.getCache().getCacheMap());Player reloaded=player(h);
            for(Map.Entry<String,Object> e:snapshot.entrySet()){
                Object v=e.getValue();if(v instanceof Integer)reloaded.getCache().set(e.getKey(),(Integer)v);else if(v instanceof Long)reloaded.getCache().store(e.getKey(),(Long)v);
            }
            equip(reloaded,B);equip(reloaded,C);check(AgilityUtilityEquipment.bootCharge(reloaded)==AgilityUtilityEquipment.bootCharge(p),"charge reload");
            check(reloaded.getCache().getInt(AgilityUtilityEquipment.BOOT_PROGRESS)==progress,"partial drain reload");
            remove(p,Equipment.EquipmentSlot.SLOT_BOOTS);AgilityUtilityEquipment.completedLap(p,3);check(AgilityUtilityEquipment.bootCharge(p)==100,"unworn lap no recharge");
            h.world().getRegionManager().preflightNativeLayeredPlacements();h.world().getRegionManager().populateNativeLayeredPlacements();
            for(int[] at:AgilityUtilityEquipment.COURSE_LANDINGS){
                com.openrsc.server.model.world.coordinate.WorldLocation location=com.openrsc.server.model.world.coordinate.WorldLocation.global(new com.openrsc.server.model.world.coordinate.WorldCoordinate(at[0],at[1],at[2]));
                check(com.openrsc.server.io.AgilityShortcutLanding.collisionMaskClear(h.world().getTile(location).traversalMask),"installed landing walkable "+Arrays.toString(at)+" mask="+h.world().getTile(location).traversalMask);
            }
            check(AgilityUtilityEquipment.COURSE_NAMES.length==3&&AgilityUtilityEquipment.COURSE_LANDINGS.length==3,"only the three traditional courses");
            Player cape=player(h);equip(cape,C);check(AgilityUtilityEquipment.capeCharge(cape)==3,"cape starts3");steps(cape,3000);check(!cape.getCache().hasKey(AgilityUtilityEquipment.CAPE_PROGRESS),"full cape banks no extra charge");
            cape.getCache().set(AgilityUtilityEquipment.CAPE_CHARGE,0);steps(cape,1999);check(AgilityUtilityEquipment.capeCharge(cape)==0,"1999 no charge");steps(cape,1);check(AgilityUtilityEquipment.capeCharge(cape)==1,"2000 charge");
            steps(cape,4000);check(AgilityUtilityEquipment.capeCharge(cape)==3&&cape.getCache().getInt(AgilityUtilityEquipment.CAPE_PROGRESS)==0,"cape cap3");
            check(!AgilityUtilityEquipment.teleport(cape,99)&&AgilityUtilityEquipment.capeCharge(cape)==3,"cancel costs no charge");
            check(!AgilityUtilityEquipment.teleport(cape,3)&&AgilityUtilityEquipment.capeCharge(cape)==3,"retired Yanille index refuses without charge");
            int capeXp=xp(cape);
            check(AgilityUtilityEquipment.teleport(cape,0)&&cape.getX()==692&&cape.getY()==493&&AgilityUtilityEquipment.capeCharge(cape)==2,"actual cape teleport spends exactly1");
            check(xp(cape)==capeXp,"teleport no walking XP");
            for(int course=1;course<AgilityUtilityEquipment.COURSE_LANDINGS.length;course++){
                cape.teleport(450,450,0,false);cape.getCache().set(AgilityUtilityEquipment.CAPE_CHARGE,3);
                check(AgilityUtilityEquipment.teleport(cape,course),"course teleport "+course);
            }
            cape.getCache().set(AgilityUtilityEquipment.CAPE_CHARGE,0);cape.teleport(450,450,0,false);
            check(!AgilityUtilityEquipment.teleport(cape,0),"depleted cape refuses teleport");
            cape.getCache().set(AgilityUtilityEquipment.CAPE_CHARGE,3);cape.teleport(298,131,0,false);
            check(!AgilityUtilityEquipment.teleport(cape,0)&&AgilityUtilityEquipment.capeCharge(cape)==3,"deep Wilderness refuses without spending");
            // Exact scripted thresholds, cross-tier eligibility and replacements.
            Player loot=player(h);
            check(uniques(h,loot,1,999).isEmpty(),"tier1 losing endpoint");check(h.random().describeState().contains("int(1000)=999"),"exact T1 denominator");check(uniques(h,loot,1,0).get(0).getCatalogId()==B,"tier1 boots");
            check(uniques(h,loot,2,499,999).isEmpty(),"tier2 losing endpoints");check(h.random().describeState().contains("int(500)=499, int(1000)=999"),"exact T2 denominators");List<Item> won=uniques(h,loot,2,0,0);
            check(won.size()==2&&won.get(0).getCatalogId()==B&&won.get(1).getCatalogId()==G,"tier2 boots and gloves");
            check(uniques(h,loot,3,99,499,999).isEmpty(),"tier3 losing endpoints");check(h.random().describeState().contains("int(100)=99, int(500)=499, int(1000)=999"),"exact T3 denominators");won=uniques(h,loot,3,0,0,0);check(won.size()==3&&won.get(2).getCatalogId()==C,"tier3 all eligible");
            equip(loot,B);equip(loot,G);equip(loot,C);loot.getCache().set(AgilityUtilityEquipment.BOOT_CHARGE,12);loot.getCache().set(AgilityUtilityEquipment.CAPE_CHARGE,0);
            won=uniques(h,loot,3,0,0,0,0);check(won.get(0).getCatalogId()==ItemId.RUNITE_GREAVES.id()&&won.get(1).getCatalogId()==ItemId.RUNITE_GAUNTLETS.id()&&won.get(2).getCatalogId()==ItemId.TOOTH_KEY_HALF.id(),"duplicate replacements");
            check(AgilityUtilityEquipment.bootCharge(loot)==12&&AgilityUtilityEquipment.capeCharge(loot)==0,"duplicates no free recharge");
            won=uniques(h,loot,3,1,1,0,1);check(won.size()==1&&won.get(0).getCatalogId()==ItemId.LOOP_KEY_HALF.id(),"other key half");
            for(int chance=0;chance<=10000;chance++)check(AgilityUtilityEquipment.adjustedSuccess(loot,chance)==chance+(10000-chance)/4,"proportional failure reduction");
            h.random().reset(1);h.random().scriptInts(0,1,2,3);int rescued=0;for(int i=0;i<4;i++)if(AgilityUtilityEquipment.obstacleSuccess(loot,false))rescued++;
            check(rescued==1,"exact25% failure rescue");
            for(int total=1;total<=100;total++){
                AgilityUtilityEquipment.beginObstacle(loot);int waited=0;for(int tick=0;tick<total;tick++)waited+=AgilityUtilityEquipment.obstacleTicks(loot,1);
                check(waited==(total*3+3)/4,"cumulative quarter-tick timing");
            }
            Class<?> type=Class.forName("com.openrsc.server.plugins.custom.myworld.skills.agility.AgilityRewardPouches");
            Method roll=type.getDeclaredMethod("rollRewardItems",Player.class,int.class);roll.setAccessible(true);
            h.random().reset(1);h.random().scriptInts(999);List<?> rewards=(List<?>)roll.invoke(null,loot,2328);
            check(rewards.size()>=2&&rewards.size()<=4,"actual pouch retains normal rewards");
            for(Object r:rewards)check(((Item)r).getCatalogId()!=ItemId.LEFT_HALF_DRAGON_SQUARE_SHIELD.id(),"no shared rare table");
            Player visual=player(h);visual.setClientVersion(10059);
            com.openrsc.server.net.rsc.ActionSender.sendPlayerMovementRate(visual,loot);
            check(h.countOutgoingPackets(visual,com.openrsc.server.net.rsc.enums.OpcodeOut.SEND_PLAYER_MOVEMENT_RATE)==0,"old clients never receive new rate opcode");
            visual.setClientVersion(10060);com.openrsc.server.net.rsc.ActionSender.sendPlayerMovementRate(visual,loot);
            com.openrsc.server.net.rsc.ActionSender.sendPlayerMovementRate(visual,loot);
            check(h.countOutgoingPackets(visual,com.openrsc.server.net.rsc.enums.OpcodeOut.SEND_PLAYER_MOVEMENT_RATE)==1,"send-on-change rate receipt");
            com.openrsc.server.net.rsc.struct.outgoing.PlayerMovementRateStruct rate=new com.openrsc.server.net.rsc.struct.outgoing.PlayerMovementRateStruct();
            rate.serverIndex=77;rate.percent=125;rate.setOpcode(com.openrsc.server.net.rsc.enums.OpcodeOut.SEND_PLAYER_MOVEMENT_RATE);
            com.openrsc.server.net.Packet packet=new com.openrsc.server.net.rsc.generators.impl.PayloadCustomGenerator().generate(rate,visual);
            check(packet.getID()==161&&packet.getBuffer().readUnsignedShort()==77&&packet.getBuffer().readUnsignedShort()==125&&!packet.getBuffer().isReadable(),"real fixed four-byte rate payload");packet.getBuffer().release();
            Player glovesOnly=player(h);equip(glovesOnly,G);before=xp(glovesOnly);glovesOnly.incExp(Skill.AGILITY.id(),100,false);
            check(xp(glovesOnly)-before==110&&glovesOnly.getWalkingTick()==430,"gloves work independently without charges");
            h.server().getConfig().WANT_MYWORLD=false;check(AgilityUtilityEquipment.xpPercent(loot)==0&&loot.getWalkingTick()==430,"other worlds unchanged");
        }
        System.out.println("PASS "+checks+" agility utility equipment, XP, movement, persistence, charges, odds and timing checks");
    }
}
