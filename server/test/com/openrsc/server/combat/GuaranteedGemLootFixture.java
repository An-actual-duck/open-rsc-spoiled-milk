package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.DropTable;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.content.gemdragons.GemDragons;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.runtime.GameRandom;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Exact approved tickets and complete normal/personal NPC death delivery. No listener/accounts. */
public final class GuaranteedGemLootFixture {
    private static int assertions;
    private static final int S=ItemId.UNCUT_SAPPHIRE.id(), E=ItemId.UNCUT_EMERALD.id(),
        R=ItemId.UNCUT_RUBY.id(), D=ItemId.UNCUT_DIAMOND.id(), T=ItemId.UNCUT_DRAGONSTONE.id();
    private static final int[] GEMS={S,E,R,D,T};
    private static final int[] FORBIDDEN={ItemId.UNCUT_OPAL.id(),ItemId.UNCUT_JADE.id(),ItemId.UNCUT_RED_TOPAZ.id()};
    // Prefixes select the approved matrix. Rows: gem, quantity, exact outcome weight.
    private static final int[][] MATRIX={
        {S,1,64},{S,3,32},{S,5,16},
        {E,1,32},{E,3,16},{E,5,8},
        {R,1,16},{R,3,8},{R,5,4},
        {D,1,8},{D,3,4},{D,5,2},{T,1,1}
    };
    private static final int[] OUTCOMES={3,6,9,12,13}, TOTALS={112,168,196,210,211};
    private static final int[] RINGS={ItemId.SAPPHIRE_RING.id(),ItemId.EMERALD_RING.id(),
        ItemId.RUBY_RING.id(),ItemId.DIAMOND_RING.id(),ItemId.DRAGONSTONE_RING.id()};
    private static void check(boolean condition,String label) {
        assertions++;
        if(!condition) throw new AssertionError(label);
    }
    private static final class Ticket implements GameRandom {
        final int ticket, expectedBound; int calls;
        Ticket(int ticket,int expectedBound) {this.ticket=ticket;this.expectedBound=expectedBound;}
        public int nextInt(int bound) {check(bound==expectedBound,"correct whole-table bound");calls++;return ticket;}
        public double nextDouble() {throw new AssertionError("Guaranteed gem must make one integer draw only");}
        public String describeState() {return "ticket="+ticket;}
    }
    public static void main(String[] args)throws Exception {
        try(CurrentCombatHarness h=new CurrentCombatHarness()) {
            verifyEveryTicket();
            h.server().getConfig().VALUABLE_DROP_MESSAGES=false;
            h.server().getConfig().WANT_CUSTOM_QUESTS=false;
            h.server().getConfig().MEMBER_WORLD=true;
            Player player=h.player("gemloot",449,450);player.getClientLimitations().maxItemId=Integer.MAX_VALUE;h.recordOutgoingPackets(player);player.setBank(new com.openrsc.server.model.container.Bank(player));
            Player stranger=h.player("gemother",449,451);stranger.getClientLimitations().maxItemId=Integer.MAX_VALUE;h.recordOutgoingPackets(stranger);
            Method personal=Npc.class.getDeclaredMethod("dropItems",Player.class,double.class,boolean.class);personal.setAccessible(true);
            for(int tier=0;tier<5;tier++) {
                Npc npc=h.npc(877+tier,450,450);npc.updateRegion();
                // Neither necklace can reroll/multiply this separate guaranteed roll.
                // They occupy the same slot, so exercise them independently.
                for(int necklace:new int[]{0,1647,1667}) {
                    clearEquipment(player);if(necklace!=0)h.equip(player,necklace,1);
                    for(boolean personalLoot:new boolean[]{false,true}) {
                        int start=0;
                        for(int outcome=0;outcome<OUTCOMES[tier];outcome++) {
                            int[] row=MATRIX[outcome];
                            clearGround(h,player);
                            h.random().reset(1);h.random().scriptInts(start,127,127).scriptDoubles(.999,.999,.999,0.0);
                            if(personalLoot)personal.invoke(npc,player,.01,true);else npc.dropItems(player);
                            List<GroundItem> gems=gemDrops(player,npc);
                            check(gems.size()==1,"one guaranteed gem outcome, even when normal table rolls nothing");
                            GroundItem gem=gems.get(0);
                            check(gem.getID()==row[0]&&gem.getAmount()==row[1]&&gem.getNoted()==(row[1]>1),"complete death delivers exact chosen outcome");
                            check(gem.getOwnerUsernameHash()==player.getUsernameHash(),"recipient ownership retained");
                            check(gem.getAttribute("personalNpcDrop",false)==personalLoot,"personal loot metadata retained");
                            if(personalLoot)check(!gem.belongsTo(stranger),"personal gem hidden from another recipient");
                            int bones=count(player,npc,ItemId.DRAGON_BONES.id());
                            check(bones>=(necklace==1667?2:1),"ordinary bones and Death-necklace bonuses remain independent");
                            check(count(player,npc,3408)==0&&count(player,npc,3409)==0,"guaranteed gem does not guarantee unique equipment");
                            check(count(player,npc,ItemId.GOLD_BAR.id())==0&&count(player,npc,ItemId.GOLD_RING.id())==0&&count(player,npc,RINGS[tier])==0,"normal nothing outcome really delivers no extra material");
                            for(int forbidden:FORBIDDEN)check(count(player,npc,forbidden)==0,"no opal/jade/topaz drops");
                            if(necklace==1647) {
                                String trace=h.random().describeState();
                                check(occurrences(trace,"int(128)=127")==2,"Cosmic necklace really rerolls normal loot but not gems");
                            }
                            start+=row[2];
                        }
                    }
                }
                // Winning both separate uniques still gives exactly one gem outcome.
                clearEquipment(player);clearGround(h,player);
                h.random().reset(1);h.random().scriptInts(0,127).scriptDoubles(0.0,0.0,.999);
                npc.dropItems(player);
                check(gemDrops(player,npc).size()==1,"independent unique wins do not add another gem roll");
                check(count(player,npc,3408)==1&&count(player,npc,3409)==1,"both unique rolls still deliver independently");
                verifyBanking(h,player,npc,personal);
                verifyWorldEligibility(h,player,npc);
            }
        }
        System.out.println("Guaranteed gem loot: PASS "+assertions+" assertions; all 897 tickets and 258 normal/personal necklace deliveries");
    }
    private static void verifyEveryTicket() {
        for(int tier=0;tier<5;tier++) {
            check(GemDragons.guaranteedGemWeightTotal(877+tier)==TOTALS[tier],"approved table total");
            int start=0;Map<String,Integer> counts=new HashMap<>();
            for(int outcome=0;outcome<OUTCOMES[tier];outcome++) {
                int[] row=MATRIX[outcome];
                for(int ticket=start;ticket<start+row[2];ticket++) {
                    Ticket random=new Ticket(ticket,TOTALS[tier]);
                    Item gem=GemDragons.rollGuaranteedGem(877+tier,random);
                    check(random.calls==1,"one RNG draw per guaranteed gem");
                    check(gem.getCatalogId()==row[0]&&gem.getAmount()==row[1]&&gem.getNoted()==(row[1]>1),"every ticket resolves to approved matrix row");
                    check(gem.getAmount()==1||gem.getAmount()==3||gem.getAmount()==5,"quantity must be 1/3/5");
                    check(gem.getCatalogId()!=T||tier==4&&gem.getAmount()==1,"Dragonstone only on highest dragon and only one");
                    String key=gem.getCatalogId()+":"+gem.getAmount();counts.put(key,counts.getOrDefault(key,0)+1);
                }
                start+=row[2];
            }
            check(start==TOTALS[tier],"entire table covered without gaps");
            for(int outcome=0;outcome<OUTCOMES[tier];outcome++) {
                int[] row=MATRIX[outcome];check(counts.get(row[0]+":"+row[1])==row[2],"exact per-outcome probability weight");
            }
        }
        check(MATRIX[1][2]==MATRIX[3][2],"Sapphire3 equals Emerald1");
        check(MATRIX[2][2]==MATRIX[4][2]&&MATRIX[4][2]==MATRIX[6][2],"Sapphire5 equals Emerald3 equals Ruby1");
        check(MATRIX[12][2]<MATRIX[11][2],"Dragonstone1 is individually rarer than Diamond5");
        for(int tier=0;tier<4;tier++) {
            boolean rejected=false;
            try { GemDragons.rollGuaranteedGem(877+tier,new Ticket(TOTALS[tier],TOTALS[tier])); }
            catch(IllegalStateException invalidTicket) { rejected=true; }
            check(rejected,"invalid escaped lower-tier ticket cannot silently become Dragonstone");
        }
        for(int id:new int[]{0,196,291,876,882}) {
            Ticket random=new Ticket(0,1);check(GemDragons.rollGuaranteedGem(id,random)==null&&random.calls==0,"unrelated NPCs receive no gem or extra RNG draw");
        }
    }
    private static void verifyBanking(CurrentCombatHarness h,Player player,Npc npc,Method personal)throws Exception {
        clearEquipment(player);h.equip(player,1662,1);
        Item neck=equipment(player)[10];EnchantingItemEffects.setLawBankingItemCharges(player,neck,100);
        int start=0;
        for(int outcome=0;outcome<3;outcome++) {
            clearGround(h,player);int[] row=MATRIX[outcome];int before=player.getBank().countId(S);
            h.random().reset(1);h.random().scriptInts(start,127).scriptDoubles(.999,.999,.999);
            personal.invoke(npc,player,.05,true);
            if(row[1]==1) {
                check(player.getBank().countId(S)==before+1&&gemDrops(player,npc).isEmpty(),"single unnoted gem uses existing Law banking");
            } else {
                List<GroundItem> gems=gemDrops(player,npc);
                check(player.getBank().countId(S)==before&&gems.size()==1&&gems.get(0).getNoted()&&gems.get(0).getAmount()==row[1],"noted 3/5 retain full quantity under ordinary Law helper rules");
            }
            start+=row[2];
        }
        clearEquipment(player);
    }
    private static void verifyWorldEligibility(CurrentCombatHarness h,Player player,Npc npc)throws Exception {
        clearEquipment(player);clearGround(h,player);
        int previous=h.server().getConfig().RESTRICT_ITEM_ID;h.server().getConfig().RESTRICT_ITEM_ID=S-1;
        try {
            h.random().reset(1);h.random().scriptInts(0,127).scriptDoubles(.999,.999,.999);
            npc.dropItems(player);check(gemDrops(player,npc).isEmpty(),"world item restriction still takes precedence over reward delivery");
        } finally {h.server().getConfig().RESTRICT_ITEM_ID=previous;}
    }
    private static Item[] equipment(Player player)throws Exception {Field field=Equipment.class.getDeclaredField("list");field.setAccessible(true);return(Item[])field.get(player.getCarriedItems().getEquipment());}
    private static void clearEquipment(Player player)throws Exception {Arrays.fill(equipment(player),null);}
    private static void clearGround(CurrentCombatHarness h,Player player) {for(GroundItem item:new ArrayList<GroundItem>(player.getViewArea().getItemsInView()))h.world().unregisterItem(item);}
    private static List<GroundItem> gemDrops(Player player,Npc npc) {
        List<GroundItem> result=new ArrayList<>();
        for(GroundItem item:player.getViewArea().getItemsInView())if(item.getLocation().equals(npc.getLocation()))for(int gem:GEMS)if(item.getID()==gem)result.add(item);
        return result;
    }
    private static int count(Player player,Npc npc,int id) {int amount=0;for(GroundItem item:player.getViewArea().getItemsInView())if(item.getLocation().equals(npc.getLocation())&&item.getID()==id)amount+=item.getAmount();return amount;}
    private static int occurrences(String value,String needle) {int count=0,index=0;while((index=value.indexOf(needle,index))>=0){count++;index+=needle.length();}return count;}
}
