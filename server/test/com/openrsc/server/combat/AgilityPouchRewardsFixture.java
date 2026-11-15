package com.openrsc.server.combat;

import com.openrsc.server.content.AgilityPouchRewards;
import com.openrsc.server.content.AgilityPouchRewards.*;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.event.rsc.PluginTask;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import org.json.*;

/** Real loaded IDs, weighted hierarchy endpoints and actual chest opening/delivery. No accounts/listeners. */
public final class AgilityPouchRewardsFixture {
    private static int checks;
    private static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    private static void endpoints(CurrentCombatHarness h,Player player,Node category,Node node,List<Integer> prefix,Map<Integer,String> membership,String label){
        if(node.itemId>0){
            check(membership.put(node.itemId,label)==null,"one ordinary category per item within tier");
            for(boolean high:new boolean[]{false,true}){
                List<Integer> script=new ArrayList<>(prefix);script.add(high?node.max-node.min:0);
                h.random().reset(1);h.random().scriptInts(script.toArray(new Integer[0]));Item rolled=category.roll(player,h.random());
                check(rolled.getCatalogId()==node.itemId&&rolled.getAmount()==(high?node.max:node.min),"reachable exact leaf and quantity "+node.label);
                check(rolled.getNoted()==(node.preferNoted&&rolled.getDef(h.world()).isNoteable()),"actual noteability "+node.label);
                check(rolled.getDef(h.world())!=null&&!rolled.getDef(h.world()).getName().equals("Unobtanium"),"real game definition "+node.label);
            }
            return;
        }
        int offset=0;
        for(Node child:node.options){List<Integer> next=new ArrayList<>(prefix);next.add(offset);endpoints(h,player,category,child,next,membership,label);offset+=child.weight;}
    }
    public static void main(String[] args)throws Exception{
        try(CurrentCombatHarness h=new CurrentCombatHarness()){
            h.server().getConfig().WANT_MYWORLD=true;h.server().getConfig().WANT_FATIGUE=false;
            Player player=h.player("pouch review",450,450);player.getClientLimitations().maxItemId=65535;h.recordOutgoingPackets(player);
            Catalog catalog=AgilityPouchRewards.forPlayer(player);
            Set<Integer> forbidden=new HashSet<>(Arrays.asList(3343,3344,3345,3346,3347,3348));Set<Integer> coating=new HashSet<>(Arrays.asList(3341,3342,3466,3470));
            for(Tier tier:catalog.tiers.values()){
                check(tier.categories.size()==10,"ten normal categories");int sum=0;Map<Integer,String> membership=new HashMap<>();Set<Integer> foundCoating=new HashSet<>();
                for(Node category:tier.categories){sum+=category.weight;endpoints(h,player,category,category,new ArrayList<>(),membership,category.label);}
                check(sum==100,"category weights total100");
                for(Integer id:membership.keySet()){check(!forbidden.contains(id),"major Slayer component excluded");if(coating.contains(id))foundCoating.add(id);}
                check(foundCoating.equals(tier.tier==3?coating:Collections.emptySet()),"four ingredients only tier3");
                h.random().reset(0xACE);
                Set<String> seen=new HashSet<>();
                for(int sample=0;sample<1000;sample++){
                    List<Item> rewards=catalog.rollNormal(player,tier.pouchId,h.random());Set<String> selected=new HashSet<>();
                    check(rewards.size()>=tier.minRewards&&rewards.size()<=tier.maxRewards,"normal stack count");
                    for(Item reward:rewards){String category=membership.get(reward.getCatalogId());check(category!=null&&selected.add(category),"no repeated categories");seen.add(category);}
                }
                check(seen.size()==10,"all categories reached");
            }
            // Opening the actual plugin consumes exactly one chest, retains resource rewards and may add uniques.
            Class<?> type=Class.forName("com.openrsc.server.plugins.custom.myworld.skills.agility.AgilityRewardPouches");
            com.openrsc.server.plugins.triggers.OpInvTrigger plugin=(com.openrsc.server.plugins.triggers.OpInvTrigger)type.getConstructor().newInstance();
            Field contextField=PluginTask.class.getDeclaredField("tasksMap");contextField.setAccessible(true);
            @SuppressWarnings("unchecked") Map<String,PluginTask> contexts=(Map<String,PluginTask>)contextField.get(null);
            player.getCarriedItems().getInventory().add(new Item(2330,2));Item chest=player.getCarriedItems().getInventory().get(0);
            contexts.put(Thread.currentThread().getName(),new PluginTask(h.world(),player,"OpInv",new Object[0]){public int action(){return 0;}public synchronized void pause(int ticks){}});
            try{h.random().reset(1);check(plugin.blockOpInv(player,0,chest,"Open"),"actual item trigger");plugin.onOpInv(player,0,chest,"Open");}
            finally{contexts.remove(Thread.currentThread().getName());}
            check(player.getCarriedItems().getInventory().countId(2330)==1,"one chest consumed");
            check(player.getCarriedItems().getInventory().size()>=4,"real delivered resources");
            // A bad table is refused before the item can be consumed.
            Path bad=Files.createTempDirectory(Paths.get("."),"bad-pouch-table-");Files.createDirectories(bad.resolve("defs/extras"));
            Files.write(bad.resolve("defs/extras/AgilityPouchRewards.json"),"{\"schemaVersion\":99}".getBytes(StandardCharsets.UTF_8));
            String original=h.server().getConfig().CONFIG_DIR;h.server().getConfig().CONFIG_DIR=bad.toAbsolutePath().toString();
            boolean rejected=false;try{plugin.onOpInv(player,0,chest,"Open");}catch(IllegalStateException expected){rejected=true;}finally{h.server().getConfig().CONFIG_DIR=original;}
            check(rejected&&player.getCarriedItems().getInventory().countId(2330)==1,"invalid configuration preserves chest");
        }
        System.out.println("PASS "+checks+" pouch data, exact selection paths, quantities, rarity exclusions and actual delivery checks");
    }
}
