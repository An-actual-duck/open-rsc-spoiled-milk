package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.DropTable;
import com.openrsc.server.content.gemdragons.GemDragons;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.external.ObjectRunecraftDef;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Production ticket selection, rare boundaries and complete normal/personal delivery. */
public final class GemDragonStandardLootFixture {
    private static int assertions, deliveries;
    private static final int[] BOLTS={2186,2188,2190,2192,2194};
    private static final int[] ARROWS={642,2041,644,2042,646};
    private static final int[] RINGS={284,285,286,287,543};
    private static final int[] RUNES={41,40,42,38,619};
    private static final int[] ALTARS={1205,1207,1209,1211,1213};
    private static final int[] RUNE_LEVELS={22,38,46,54,70};
    private static final int[] PRICES={320,720,1520,2600,4000};
    private static final int[] COINS={300,450,650,900,1200};
    private static final int[] SCRAP_DENOMINATORS={1024,768,512,256,128};
    private static final int[] UNCUT={ItemId.UNCUT_SAPPHIRE.id(),ItemId.UNCUT_EMERALD.id(),
        ItemId.UNCUT_RUBY.id(),ItemId.UNCUT_DIAMOND.id(),ItemId.UNCUT_DRAGONSTONE.id()};
    private static final int SCRAP=ItemId.RAW_DRAGON_METAL.id();
    private static void check(boolean value,String label) {assertions++;if(!value)throw new AssertionError(label);}
    private static final class Outcome {
        final int id, quantity; final boolean noted;
        Outcome(int id,int quantity,boolean noted) {this.id=id;this.quantity=quantity;this.noted=noted;}
    }
    private static Outcome expected(int tier,int ticket,int child) {
        if(ticket<28)return new Outcome(ItemId.GOLD_BAR.id(),2+tier,true);
        if(ticket<36)return new Outcome(ItemId.GOLD_RING.id(),1,false);
        if(ticket<44)return new Outcome(RINGS[tier],1,false);
        if(ticket<60)return new Outcome(BOLTS[tier],2+tier,false);
        if(ticket<64)return new Outcome(ARROWS[tier],1+tier,false);
        if(ticket<84)return new Outcome(tier==0?RUNES[0]:RUNES[child<8?tier-1:tier],10+5*tier,false);
        if(ticket<120)return new Outcome(ItemId.COINS.id(),COINS[tier],false);
        return null;
    }
    public static void main(String[] args)throws Exception {
        try(CurrentCombatHarness h=new CurrentCombatHarness()) {
            h.server().getConfig().VALUABLE_DROP_MESSAGES=false;
            h.server().getConfig().WANT_CUSTOM_QUESTS=false;
            h.server().getConfig().MEMBER_WORLD=true;
            Player player=h.player("gemstandard",449,450);player.getClientLimitations().maxItemId=Integer.MAX_VALUE;h.recordOutgoingPackets(player);
            Method personal=Npc.class.getDeclaredMethod("dropItems",Player.class,double.class,boolean.class);personal.setAccessible(true);
            int previousBoltOffense=0,previousArrowOffense=0,previousRuneLevel=0;
            for(int tier=0;tier<5;tier++) {
                Npc npc=h.npc(877+tier,450,450);npc.updateRegion();
                ItemDefinition bolt=h.server().getEntityHandler().getItemDef(BOLTS[tier]);
                ItemDefinition arrow=h.server().getEntityHandler().getItemDef(ARROWS[tier]);
                ObjectRunecraftDef rune=h.server().getEntityHandler().getObjectRunecraftDef(ALTARS[tier]);
                check(bolt.getRangedOffense()>previousBoltOffense&&arrow.getRangedOffense()>previousArrowOffense,"actual ammunition offense rises with each material tier");
                previousBoltOffense=bolt.getRangedOffense();previousArrowOffense=arrow.getRangedOffense();
                check(bolt.getDefaultPrice()==PRICES[tier]&&arrow.getDefaultPrice()==PRICES[tier],"enhanced ammo prices used for balance");
                check(rune.getRuneId()==RUNES[tier]&&rune.getRequiredLvl()==RUNE_LEVELS[tier]&&rune.getRequiredLvl()>previousRuneLevel,"actual Runecraft progression supplies higher rune choices");
                previousRuneLevel=rune.getRequiredLvl();
                DropTable table=h.world().getNpcDrops().getDropTable(npc.getID());
                check(table.getTotalWeight()==128,"normal table total remains128");
                int[] categories=new int[8];
                for(int ticket=0;ticket<128;ticket++)for(int child=0;child<16;child++) {
                    h.random().reset(1);h.random().scriptInts(ticket,child);
                    ArrayList<Item> rolled=table.clone().rollItem(player);
                    Outcome outcome=expected(tier,ticket,child);
                    if(outcome==null) {check(rolled.isEmpty(),"eight empty tickets");categories[7]++;continue;}
                    check(rolled.size()==1,"one ordinary outcome per root roll");Item item=rolled.get(0);
                    check(item.getCatalogId()==outcome.id&&item.getAmount()==outcome.quantity&&item.getNoted()==outcome.noted,"every normal and child ticket matches matrix");
                    for(int gem:UNCUT)check(item.getCatalogId()!=gem,"normal roll cannot add an uncut gem");
                    check(item.getCatalogId()!=SCRAP,"scrap remains a separate rare roll");
                    int category=ticket<28?0:ticket<36?1:ticket<44?2:ticket<60?3:ticket<64?4:ticket<84?5:6;categories[category]++;
                }
                int[] weights={28,8,8,16,4,20,36,8};
                for(int category=0;category<weights.length;category++)check(categories[category]==weights[category]*16,"exact ordinary category weight");
                check(categories[3]==4*categories[4],"bolts dominate ammo four to one");
                verifyScrapBoundaries(h,npc,tier);
                // Each root outcome, and both rune children where distinct, through death delivery.
                int[][] roots=tier==0?new int[][]{{0,0},{28,0},{36,0},{44,0},{60,0},{64,0},{84,0},{127,0}}
                    :new int[][]{{0,0},{28,0},{36,0},{44,0},{60,0},{64,0},{64,8},{84,0},{127,0}};
                for(int necklace:new int[]{0,1647,1667}) {
                    clearEquipment(player);if(necklace!=0)h.equip(player,necklace,1);
                    for(boolean privateLoot:new boolean[]{false,true})for(int[] root:roots) {
                        clearGround(h,player);int ticket=root[0],child=root[1];
                        h.random().reset(1);
                        if(ticket>=64&&ticket<84)h.random().scriptInts(0,ticket,child,ticket,child);
                        else h.random().scriptInts(0,ticket,ticket);
                        h.random().scriptDoubles(.999,.999,0.0,0.0);
                        if(privateLoot)personal.invoke(npc,player,.01,true);else npc.dropItems(player);deliveries++;
                        Outcome outcome=expected(tier,ticket,child);
                        int multiplier=necklace==1647?2:1;
                        if(outcome!=null) {
                            check(count(player,npc,outcome.id)==outcome.quantity*multiplier,"complete death preserves full ordinary quantity and Cosmic reroll");
                            for(GroundItem item:ground(player,npc))if(item.getID()==outcome.id) {
                                check(item.getNoted()==outcome.noted,"ordinary noting/stackability retained");
                                check(item.getOwnerUsernameHash()==player.getUsernameHash()&&item.getAttribute("personalNpcDrop",false)==privateLoot,"ordinary ownership/personal metadata retained");
                            }
                        } else {
                            for(GroundItem item:ground(player,npc))check(item.getID()==SCRAP||item.getID()==ItemId.DRAGON_BONES.id()||item.getID()==UNCUT[0],"empty normal outcome has no hidden material reward");
                        }
                        check(count(player,npc,UNCUT[0])==1,"guaranteed gem stays independent of normal/Cosmic/Death outcomes");
                        for(int gem=1;gem<UNCUT.length;gem++)check(count(player,npc,UNCUT[gem])==0,"no other gem from normal or rare paths");
                        check(count(player,npc,SCRAP)==1,"rare scrap delivered once without necklace duplication");
                        check(count(player,npc,3408)==0&&count(player,npc,3409)==0,"scrap roll does not force either unique");
                        check(count(player,npc,ItemId.DRAGON_BONES.id())>=(necklace==1667?2:1),"ordinary bones/Death bonus retained");
                    }
                }
                clearEquipment(player);clearGround(h,player);
                h.random().reset(1);h.random().scriptInts(0,127).scriptDoubles(0.0,0.0,0.0);
                npc.dropItems(player);
                check(count(player,npc,3408)==1&&count(player,npc,3409)==1&&count(player,npc,SCRAP)==1&&count(player,npc,UNCUT[0])==1,"all independent rare wins and guaranteed gem coexist");
                clearGround(h,player);
            }
        }
        System.out.println("Gem dragon standard loot: PASS "+assertions+" assertions; 10240 root/child tickets and "+deliveries+" complete necklace/personal deliveries");
    }
    private static void verifyScrapBoundaries(CurrentCombatHarness h,Npc npc,int tier) {
        check(GemDragons.SCRAP_DENOMINATORS[tier]==SCRAP_DENOMINATORS[tier],"approved scrap denominator");
        if(tier>0)check(SCRAP_DENOMINATORS[tier]<SCRAP_DENOMINATORS[tier-1],"scrap chance strictly rises");
        for(double contribution:new double[]{1.0,.25}) {
            double chance=contribution/SCRAP_DENOMINATORS[tier];
            h.random().reset(1);h.random().scriptDoubles(.999,.999,Math.nextDown(chance));
            ArrayList<Item> win=h.world().getNpcDrops().rollHiddenUniqueDrops(npc.getID(),contribution);
            check(win.size()==1&&win.get(0).getCatalogId()==SCRAP&&win.get(0).getAmount()==1&&!win.get(0).getNoted(),"scrap wins independently just below boundary");
            h.random().reset(1);h.random().scriptDoubles(.999,.999,chance);
            check(h.world().getNpcDrops().rollHiddenUniqueDrops(npc.getID(),contribution).isEmpty(),"scrap fails at exclusive boundary");
        }
        check(h.server().getEntityHandler().getItemDef(SCRAP).getName().equals("Dragon Metal Scrap")&&h.server().getEntityHandler().getItemDef(SCRAP).getDefaultPrice()==100000,"reuse existing scrap identity/value");
    }
    private static void clearEquipment(Player player)throws Exception {Field field=Equipment.class.getDeclaredField("list");field.setAccessible(true);Arrays.fill((Item[])field.get(player.getCarriedItems().getEquipment()),null);}
    private static List<GroundItem> ground(Player player,Npc npc) {List<GroundItem> result=new ArrayList<>();for(GroundItem item:player.getViewArea().getItemsInView())if(item.getLocation().equals(npc.getLocation()))result.add(item);return result;}
    private static int count(Player player,Npc npc,int id) {int count=0;for(GroundItem item:ground(player,npc))if(item.getID()==id)count+=item.getAmount();return count;}
    private static void clearGround(CurrentCombatHarness h,Player player) {for(GroundItem item:new ArrayList<GroundItem>(player.getViewArea().getItemsInView()))h.world().unregisterItem(item);}
}
