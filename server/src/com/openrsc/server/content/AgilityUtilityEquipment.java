package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.PluginTask;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.util.rsc.TeleportRestrictions;
import com.openrsc.server.runtime.GameRandom;
import java.util.ArrayList;
import java.util.List;

/** Player-owned charges and utility benefits. Item metadata is never a charge authority. */
public final class AgilityUtilityEquipment {
    public static final int BOOTS=3472, GLOVES=3473, CAPE=3474, FIRST_MOVEMENT_RATE_CLIENT_VERSION=10060;
    public static final String BOOT_CHARGE="agility_swiftness_charge", BOOT_PROGRESS="agility_swiftness_progress",
        CAPE_CHARGE="agility_traversal_charge", CAPE_PROGRESS="agility_traversal_progress", XP_REMAINDER="agility_utility_xp_remainder";
    private static final int ENERGY_PER_PERCENT=2800; // LCM of 50,70,80,100; swapping equipment cannot reset partial drain.
    private static final String TIMING="agility_suregrip_timing", LAST_SPEED="agility_last_step_speed";
    public static final String[] COURSE_NAMES={"Gnome Stronghold","Barbarian Outpost","Wilderness"};
    public static final int[][] COURSE_LANDINGS={{692,493,0},{487,555,0},{298,131,0}};
    private AgilityUtilityEquipment() {}
    public static boolean worn(Player p,int id){return p!=null&&p.getConfig().WANT_MYWORLD&&p.getCarriedItems().getEquipment().hasEquipped(id);}
    private static int stored(Player p,String key,int initial,int maximum){
        int value=p.getCache().hasKey(key)?p.getCache().getInt(key):initial;
        value=Math.max(0,Math.min(maximum,value));p.getCache().set(key,value);return value;
    }
    public static int bootCharge(Player p){return stored(p,BOOT_CHARGE,100,100);}
    public static int capeCharge(Player p){return stored(p,CAPE_CHARGE,3,3);}
    public static boolean owns(Player p,int id){return worn(p,id)||p.getCarriedItems().getInventory().countId(id)>0||(p.getBank()!=null&&p.getBank().hasItemId(id));}
    public static void initialize(Player p,int id){if(id==BOOTS)bootCharge(p);else if(id==CAPE)capeCharge(p);}
    public static int xpPercent(Player p){return (worn(p,BOOTS)?10:0)+(worn(p,GLOVES)?10:0)+(worn(p,CAPE)?10:0);}
    /** Internal XP uses quarter-XP units. Retain the percentage remainder, including walking awards. */
    public static int boostExperience(Player p,int amount){
        int percent=xpPercent(p);if(percent==0||amount<=0)return amount;
        long scaled=(long)amount*(100+percent)+stored(p,XP_REMAINDER,0,99);
        p.getCache().set(XP_REMAINDER,(int)(scaled%100));return (int)Math.min(Integer.MAX_VALUE,scaled/100);
    }
    public static boolean speedActive(Player p){
        return worn(p,BOOTS)&&bootCharge(p)>0&&!p.inCombat()&&!p.hasIncomingAttackers()
            &&p.getOutgoingCombatTarget()==null
            &&(!p.getCache().hasKey("agility_combat_until")||p.getWorld().getServer().getGameClock().currentTimeMillis()>=p.getCache().getLong("agility_combat_until"))
            &&(p.getCombatTimer()==0||p.getWorld().getServer().getGameClock().currentTimeMillis()-p.getCombatTimer()>=10_000L);
    }
    public static void combatEnded(com.openrsc.server.model.entity.Mob actor){
        if(actor instanceof Player&&actor.getConfig().WANT_MYWORLD)((Player)actor).getCache().store("agility_combat_until",actor.getWorld().getServer().getGameClock().currentTimeMillis()+10_000L);
    }
    public static int walkingInterval(Player p,int base){return speedActive(p)?Math.max(1,(int)Math.round(base/1.25D)):base;}
    public static int tilesPerPercent(Player p){return 50+(worn(p,GLOVES)?20:0)+(worn(p,CAPE)?30:0);}
    /** Called exactly once for a successfully validated ordinary walking-queue step, never teleport/forced movement. */
    public static void walkedTile(Player p){
        if(!p.getConfig().WANT_MYWORLD)return;
        boolean charged=worn(p,BOOTS)&&bootCharge(p)>0;
        boolean fast=speedActive(p);p.setAttribute(LAST_SPEED,fast?125:100);
        if(worn(p,CAPE)){
            int charges=capeCharge(p);
            if(charges<3){
                int progress=stored(p,CAPE_PROGRESS,0,1999)+1;
                if(progress>=2000){p.getCache().set(CAPE_CHARGE,charges+1);progress=0;p.message("Your Cape of Traversal regains a teleport charge.");}
                p.getCache().set(CAPE_PROGRESS,progress);
            }
            if(charged)p.incExp(Skill.AGILITY.id(),4,true); // One displayed XP, through ordinary boosts/fatigue/freeze rules.
        }
        if(fast){
            int progress=stored(p,BOOT_PROGRESS,0,ENERGY_PER_PERCENT-1)+ENERGY_PER_PERCENT/tilesPerPercent(p);
            if(progress>=ENERGY_PER_PERCENT){p.getCache().set(BOOT_CHARGE,bootCharge(p)-1);progress-=ENERGY_PER_PERCENT;}
            p.getCache().set(BOOT_PROGRESS,progress);
        }
    }
    public static int lastStepSpeed(Player p){return p.getAttribute(LAST_SPEED,100);}
    public static void completedLap(Player p,int tier){
        if(worn(p,BOOTS)&&tier>=1&&tier<=3)p.getCache().set(BOOT_CHARGE,Math.min(100,bootCharge(p)+tier));
    }
    public static int pouchTier(int id){return id==ItemId.TIER_1_AGILITY_POUCH.id()?1:id==ItemId.TIER_2_AGILITY_POUCH.id()?2:id==ItemId.TIER_3_AGILITY_POUCH.id()?3:0;}
    /** Independent, exact per-pouch unique chances. Duplicate equipment never resets character charges. */
    public static List<Item> rollUniques(Player p,int tier,GameRandom random){
        List<Item> items=new ArrayList<>();if(!p.getConfig().WANT_MYWORLD||tier<1||tier>3)return items;
        int[] denominators=tier==1?new int[]{1000,0,0}:tier==2?new int[]{500,1000,0}:new int[]{100,500,1000};
        for(int i=0;i<3;i++)if(denominators[i]>0&&random.nextInt(denominators[i])==0){
            int id=BOOTS+i;
            if(owns(p,id))id=i==0?ItemId.RUNITE_GREAVES.id():i==1?ItemId.RUNITE_GAUNTLETS.id():random.nextInt(2)==0?ItemId.TOOTH_KEY_HALF.id():ItemId.LOOP_KEY_HALF.id();
            else initialize(p,id);
            items.add(new Item(id));
        }
        return items;
    }
    /** A 25% rescue of failures is exactly a 25% proportional reduction, with no change to success rolls. */
    public static boolean obstacleSuccess(Player p,boolean success){return success||(worn(p,GLOVES)&&p.getWorld().getServer().getCombatRandom().nextInt(4)==0);}
    public static boolean productionSuccess(Player p,int required,int effective,boolean stops,int stopLevel){return productionSuccess(p,required,effective,stops,stopLevel,1);}
    public static boolean productionSuccess(Player p,int required,int effective,boolean stops,int stopLevel,int minimumFail){
        boolean success=com.openrsc.server.util.rsc.Formulae.calcProductionSuccessfulLegacy(required,effective,stops,stopLevel,minimumFail);
        return effective>=required&&obstacleSuccess(p,success);
    }
    public static void beginObstacle(Player p){p.setAttribute(TIMING,0);}
    public static int obstacleTicks(Player p,int ticks){
        if(ticks<=0||!worn(p,GLOVES))return ticks;
        int prior=p.getAttribute(TIMING,0),next=prior+ticks*3;p.setAttribute(TIMING,next);
        return (next+3)/4-(prior+3)/4;
    }
    public static void obstacleDelay(Player p,int ticks){int wait=obstacleTicks(p,ticks);PluginTask task=PluginTask.getContextPluginTask();if(task!=null&&wait>0)task.pause(wait);}
    public static int configuredTicks(Player p,int ticks){return worn(p,GLOVES)?Math.max(1,(ticks*3+3)/4):ticks;}
    public static int adjustedSuccess(Player p,int chance){return worn(p,GLOVES)&&chance>=0?chance+(10000-chance)/4:chance;}
    public static boolean teleport(Player p,int destination){
        if(destination<0||destination>=COURSE_LANDINGS.length||!p.getConfig().WANT_MYWORLD||!(worn(p,CAPE)||p.getCarriedItems().getInventory().countId(CAPE)>0))return false;
        if(TeleportRestrictions.isBlocked(p)){p.message("A mysterious force blocks your teleport.");return false;}
        if(capeCharge(p)==0){p.message("Your cape is out of charges. Walk 2,000 tiles while wearing it to restore one.");return false;}
        int[] at=COURSE_LANDINGS[destination];
        com.openrsc.server.model.world.coordinate.WorldLocation landing=com.openrsc.server.model.world.coordinate.WorldLocation.global(new com.openrsc.server.model.world.coordinate.WorldCoordinate(at[0],at[1],at[2]));
        com.openrsc.server.model.world.region.TileValue tile=p.getWorld().getTile(landing);
        if(tile==null||!com.openrsc.server.io.AgilityShortcutLanding.collisionMaskClear(tile.traversalMask)){
            p.message("That course does not have a safe arrival spot right now.");return false;
        }
        p.teleport(at[0],at[1],at[2],true);p.getCache().set(CAPE_CHARGE,capeCharge(p)-1);
        p.message("Your Cape of Traversal has "+capeCharge(p)+" teleport charges remaining.");return true;
    }
    public static void check(Player p,int id){
        if(id==BOOTS)p.message("Your Boots of Swiftness have "+bootCharge(p)+"% charge remaining.");
        if(id==CAPE){if(capeCharge(p)==3)p.message("Your Cape of Traversal is fully charged (3/3).");else p.message("Your Cape of Traversal has "+capeCharge(p)+" charges; "+stored(p,CAPE_PROGRESS,0,1999)+"/2,000 tiles toward the next charge.");}
    }
}
