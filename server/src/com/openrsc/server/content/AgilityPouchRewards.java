package com.openrsc.server.content;

import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.runtime.GameRandom;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit ordinary rewards only. Unique agility equipment remains a separate roll. */
public final class AgilityPouchRewards {
    private static final Map<Path,Catalog> CACHE=new ConcurrentHashMap<>();
    private AgilityPouchRewards() {}

    public static Catalog forPlayer(Player player) {
        Path path=Paths.get(player.getConfig().CONFIG_DIR,"defs","extras","AgilityPouchRewards.json").toAbsolutePath().normalize();
        Catalog current=CACHE.get(path);if(current!=null)return current;
        Catalog loaded=load(path);
        for(Tier tier:loaded.tiers.values())for(Node category:tier.categories)validateItems(player,category);
        Catalog prior=CACHE.putIfAbsent(path,loaded);return prior==null?loaded:prior;
    }
    private static void validateItems(Player player,Node node) {
        if(node.itemId>0&&player.getWorld().getServer().getEntityHandler().getItemDef(node.itemId)==null)
            throw new IllegalStateException("Unknown agility reward item "+node.itemId);
        for(Node child:node.options)validateItems(player,child);
    }
    public static Catalog load(Path path) {
        try {
            JSONObject document=new JSONObject(new String(Files.readAllBytes(path),StandardCharsets.UTF_8));
            if(document.getInt("schemaVersion")!=1)throw new IllegalArgumentException("Unsupported agility reward schema");
            Map<Integer,Tier> tiers=new LinkedHashMap<>();JSONArray data=document.getJSONArray("tiers");
            for(int i=0;i<data.length();i++){
                JSONObject row=data.getJSONObject(i);int tier=row.getInt("tier"),pouch=row.getInt("pouchId");
                List<Node> categories=nodes(row.getJSONArray("categories"),0);Set<String> names=new HashSet<>();
                for(Node category:categories)if(category.itemId>0||!names.add(category.label))throw new IllegalArgumentException("Duplicate/leaf reward category");
                int min=row.getInt("minRewards"),max=row.getInt("maxRewards");
                if(tier<1||tier>3||pouch!=2327+tier||min<1||max<min||max>categories.size()||tiers.containsKey(tier))
                    throw new IllegalArgumentException("Invalid agility tier/count/identity");
                tiers.put(tier,new Tier(tier,pouch,min,max,categories));
            }
            if(tiers.size()!=3)throw new IllegalArgumentException("All three agility reward tiers are required");
            return new Catalog(tiers);
        }catch(IOException|RuntimeException failure){throw new IllegalStateException("Cannot load agility pouch rewards from "+path,failure);}
    }
    private static List<Node> nodes(JSONArray rows,int depth) {
        if(depth>8||rows.length()==0)throw new IllegalArgumentException("Invalid empty/deep reward group");
        List<Node> result=new ArrayList<>();int total=0;
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);int weight=row.getInt("weight");String label=row.getString("label");
            if(weight<=0||label.trim().isEmpty())throw new IllegalArgumentException("Invalid reward weight/label");
            total=Math.addExact(total,weight);
            if(row.has("options")){
                if(row.has("itemId"))throw new IllegalArgumentException("Reward cannot be both item and group");
                result.add(new Node(label,weight,0,0,0,false,nodes(row.getJSONArray("options"),depth+1)));
            }else{
                int id=row.getInt("itemId"),min=row.getInt("min"),max=row.getInt("max");
                if(id<=0||min<=0||max<min||max>100000)throw new IllegalArgumentException("Invalid reward quantity/item");
                result.add(new Node(label,weight,id,min,max,row.getBoolean("preferNoted"),Collections.emptyList()));
            }
        }
        return Collections.unmodifiableList(result);
    }
    private static Node select(List<Node> choices,GameRandom random) {
        int total=0;for(Node node:choices)total=Math.addExact(total,node.weight);
        int roll=random.nextInt(total);for(Node node:choices){if(roll<node.weight)return node;roll-=node.weight;}
        throw new IllegalStateException("Reward selection exceeded validated weights");
    }
    public static final class Node {
        public final String label;
        public final int weight,itemId,min,max;
        public final boolean preferNoted;
        public final List<Node> options;
        private Node(String label,int weight,int id,int min,int max,boolean noted,List<Node> options){
            this.label=label;this.weight=weight;itemId=id;this.min=min;this.max=max;preferNoted=noted;this.options=options;
        }
        public Item roll(Player player,GameRandom random){
            if(itemId==0)return select(options,random).roll(player,random);
            int amount=min+random.nextInt(max-min+1);
            boolean noted=preferNoted&&player.getWorld().getServer().getEntityHandler().getItemDef(itemId).isNoteable();
            return new Item(itemId,amount,noted);
        }
    }
    public static final class Tier {
        public final int tier,pouchId,minRewards,maxRewards;
        public final List<Node> categories;
        private Tier(int tier,int pouch,int min,int max,List<Node> categories){this.tier=tier;pouchId=pouch;minRewards=min;maxRewards=max;this.categories=categories;}
    }
    public static final class Catalog {
        public final Map<Integer,Tier> tiers;
        private Catalog(Map<Integer,Tier> values){tiers=Collections.unmodifiableMap(new LinkedHashMap<>(values));}
        public ArrayList<Item> rollNormal(Player player,int pouchId,GameRandom random){
            Tier tier=tiers.get(AgilityUtilityEquipment.pouchTier(pouchId));
            if(tier==null)throw new IllegalArgumentException("Not an agility reward pouch");
            List<Node> remaining=new ArrayList<>(tier.categories);ArrayList<Item> result=new ArrayList<>();
            int count=tier.minRewards+random.nextInt(tier.maxRewards-tier.minRewards+1);
            for(int i=0;i<count;i++){Node category=select(remaining,random);remaining.remove(category);result.add(category.roll(player,random));}
            return result;
        }
    }
}
