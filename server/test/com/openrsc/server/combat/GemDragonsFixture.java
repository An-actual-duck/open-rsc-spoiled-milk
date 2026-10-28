package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.DropTable;
import com.openrsc.server.content.gemdragons.GemDragons;
import com.openrsc.server.content.gemdragons.GemCuttingEquipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;

/** Offline production definitions, complete NPC loot path and actual equipment/inventory behavior. */
public final class GemDragonsFixture {
    static int assertions;
    static final int[] GEMS={ItemId.UNCUT_OPAL.id(),ItemId.UNCUT_JADE.id(),ItemId.UNCUT_RED_TOPAZ.id(),
        ItemId.UNCUT_SAPPHIRE.id(),ItemId.UNCUT_EMERALD.id(),ItemId.UNCUT_RUBY.id(),ItemId.UNCUT_DIAMOND.id(),ItemId.UNCUT_DRAGONSTONE.id()};
    static void check(boolean value,String message) { assertions++; if(!value)throw new AssertionError(message); }
    public static void main(String[] args)throws Exception {
        try(CurrentCombatHarness h=new CurrentCombatHarness()) {
            h.server().getConfig().VALUABLE_DROP_MESSAGES=false;
            h.server().getConfig().WANT_CUSTOM_QUESTS=false;
            h.server().getConfig().MEMBER_WORLD=true;
            Player p=h.player("gem fixture",449,450);p.getClientLimitations().maxItemId=Integer.MAX_VALUE;
            h.recordOutgoingPackets(p);
            h.openCombatProjectileRectangle(448,460,449,451);
            int previousAttack=0,previousHits=0,previousMelee=0;
            for(int tier=0;tier<5;tier++) {
                Npc npc=h.npc(877+tier,450+tier*2,450);npc.updateRegion();
                check(npc.getDef().getAtt()>previousAttack&&npc.getDef().getHits()>previousHits&&npc.getMeleeDefense()>previousMelee,"strict gem hierarchy");
                previousAttack=npc.getDef().getAtt();previousHits=npc.getDef().getHits();previousMelee=npc.getMeleeDefense();
                check(npc.getMeleeDefense()==(int)Math.floor(npc.getDef().getDef()*1.25),"physical melee bonus");
                check(npc.getRangedDefense()==(int)Math.floor(npc.getDef().getDef()*.9375),"physical ranged bonus");
                check(npc.getMagicDefense()==npc.getDef().getDef(),"no magic crystallization bonus");
                check(h.world().getNpcDrops().isDragon(npc.getID()),"dragon bones classification");
                // Keep all fire checks on the same verified open authored tiles.
                Npc fireSource=h.npc(npc.getID(),450,450);
                Player protectedPlayer=h.player("gemfire"+tier,449,450);h.recordOutgoingPackets(protectedPlayer);
                protectedPlayer.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),99,99,false);
                h.equip(protectedPlayer,ItemId.ANTI_DRAGON_BREATH_SHIELD.id(),1);
                com.openrsc.server.event.rsc.impl.combat.scripts.all.DragonFireBreath breath=new com.openrsc.server.event.rsc.impl.combat.scripts.all.DragonFireBreath();
                check(breath.shouldExecute(fireSource,protectedPlayer),"normal dragon fire handler recognizes each variant");
                breath.executeScript(fireSource,protectedPlayer);
                check(protectedPlayer.getSkills().getLevel(Skill.HITS.id())==95,"anti-dragon shield routes each variant to protected HP-based fire damage: tier="+tier+" HP="+protectedPlayer.getSkills().getLevel(Skill.HITS.id()));
                check(com.openrsc.server.model.entity.npc.NpcAttackStyleProfile.forNpc(npc)==com.openrsc.server.model.entity.npc.NpcAttackStyleProfile.MELEE_MAGIC,"regular dragon attack profile retained");
                DropTable table=h.world().getNpcDrops().getDropTable(npc.getID());
                check(table.getTotalWeight()==128,"normal table budget");
                // Both independent shared-unique rolls win; exact upper boundary loses.
                double chance=1.0/GemDragons.UNIQUE_DENOMINATORS[tier];
                h.random().reset(1);h.random().scriptDoubles(Math.nextDown(chance),Math.nextDown(chance));
                ArrayList<Item> uniques=h.world().getNpcDrops().rollHiddenUniqueDrops(npc.getID(),1.0);
                check(uniques.size()==2&&uniques.get(0).getCatalogId()==3408&&uniques.get(1).getCatalogId()==3409,"both shared uniques reachable");
                h.random().reset(1);h.random().scriptDoubles(chance,chance);
                check(h.world().getNpcDrops().rollHiddenUniqueDrops(npc.getID(),1.0).isEmpty(),"unique probability exclusive boundary");
                clearEquipment(p);
            }
            // Reserved antidote holes must stay absent server-side.
            for(int id=3400;id<=3407;id++)check(h.server().getEntityHandler().getItemDef(id)==null,"reserved item hole preserved");
            check(!GemCuttingEquipment.preventsFailure(p),"no glove effect when unequipped");
            h.equip(p,3408,1);check(GemCuttingEquipment.preventsFailure(p),"equipped gloves prevent failures");
            clearEquipment(p);check(!GemCuttingEquipment.preventsFailure(p),"unequipping immediately removes gloves effect");
            h.equip(p,3409,1);
            for(int draw=0;draw<100;draw++) {
                h.random().reset(1);h.random().scriptInts(draw);
                check(GemCuttingEquipment.duplicates(p)==(draw<5),"exact 5/100 boundary");
            }
            clearEquipment(p);h.random().reset(1);h.random().scriptInts(0);check(!GemCuttingEquipment.duplicates(p),"no monocle bonus unequipped");
            // Run the actual crafting batch resolver with its supported non-script batch context.
            Class<?> craftingType=Class.forName("com.openrsc.server.plugins.authentic.skills.crafting.Crafting");
            Object crafting=craftingType.getConstructor().newInstance();
            java.lang.reflect.Method cut=craftingType.getDeclaredMethod("batchGemCutting",Player.class,Item.class,com.openrsc.server.external.ItemGemDef.class);
            cut.setAccessible(true);
            for(int index=0;index<GEMS.length;index++) {
                Player cutter=h.player("gemcut"+index,430+index,455);cutter.getClientLimitations().maxItemId=Integer.MAX_VALUE;h.recordOutgoingPackets(cutter);
                com.openrsc.server.external.ItemGemDef definition=h.server().getEntityHandler().getItemGemDef(GEMS[index]);
                check(definition!=null,"all gem crafting definitions present");
                cutter.getSkills().setTemporaryLevelAndMaxStat(Skill.CRAFTING.id(),99,99,false);
                h.equip(cutter,3408,1);h.equip(cutter,3409,1);
                cutter.getCarriedItems().getInventory().add(new Item(GEMS[index],1));
                int beforeXP=cutter.getSkills().getExperience(Skill.CRAFTING.id());
                h.random().reset(1);h.random().scriptInts(0);
                com.openrsc.server.plugins.Functions.startbatch(cutter,1);
                cut.invoke(crafting,cutter,new Item(GEMS[index]),definition);
                check(cutter.getCarriedItems().getInventory().countId(GEMS[index])==0,"one input consumed by actual cutting");
                check(cutter.getCarriedItems().getInventory().countId(definition.getGemID())==2,"actual glove+monocle cutting produces two");
                check(cutter.getCarriedItems().getInventory().countId(ItemId.CRUSHED_GEMSTONE.id())==0,"no dust for any gem with gloves");
                int bonusXP=cutter.getSkills().getExperience(Skill.CRAFTING.id())-beforeXP;
                Player ordinary=h.player("plaincut"+index,430+index,456);ordinary.getClientLimitations().maxItemId=Integer.MAX_VALUE;h.recordOutgoingPackets(ordinary);
                ordinary.getSkills().setTemporaryLevelAndMaxStat(Skill.CRAFTING.id(),99,99,false);h.equip(ordinary,3408,1);
                ordinary.getCarriedItems().getInventory().add(new Item(GEMS[index],1));
                beforeXP=ordinary.getSkills().getExperience(Skill.CRAFTING.id());
                com.openrsc.server.plugins.Functions.startbatch(ordinary,1);cut.invoke(crafting,ordinary,new Item(GEMS[index]),definition);
                check(ordinary.getCarriedItems().getInventory().countId(definition.getGemID())==1,"ordinary successful output remains one");
                check(ordinary.getSkills().getExperience(Skill.CRAFTING.id())-beforeXP==bonusXP,"actual duplicate output awards normal single-cut XP");
                // The glove/monocle does not bypass the existing batch level requirement.
                Player low=h.player("lowcut"+index,430+index,457);low.getClientLimitations().maxItemId=Integer.MAX_VALUE;h.recordOutgoingPackets(low);
                low.getSkills().setTemporaryLevelAndMaxStat(Skill.CRAFTING.id(),definition.getReqLevel()-1,definition.getReqLevel()-1,false);h.equip(low,3408,1);h.equip(low,3409,1);
                low.getCarriedItems().getInventory().add(new Item(GEMS[index],1));
                com.openrsc.server.plugins.Functions.startbatch(low,1);cut.invoke(crafting,low,new Item(GEMS[index]),definition);
                check(low.getCarriedItems().getInventory().countId(GEMS[index])==1,"level requirement preserved");
            }
            Object superchisel=Class.forName("com.openrsc.server.plugins.custom.misc.Superchisel").getConstructor().newInstance();
            java.lang.reflect.Method specialCut=superchisel.getClass().getMethod("onUseInv",Player.class,Integer.class,Item.class,Item.class);
            for(int index=3;index<GEMS.length;index++) {
                Player dev=h.player("devgem"+index,430+index,458);dev.getClientLimitations().maxItemId=Integer.MAX_VALUE;h.recordOutgoingPackets(dev);h.equip(dev,3409,1);dev.getSkills().setTemporaryLevelAndMaxStat(Skill.CRAFTING.id(),99,99,false);
                dev.getCarriedItems().getInventory().add(new Item(GEMS[index],1));
                h.random().reset(1);h.random().scriptInts(0);
                specialCut.invoke(superchisel,dev,0,new Item(ItemId.SUPERCHISEL.id()),new Item(GEMS[index]));
                int cutId=h.server().getEntityHandler().getItemGemDef(GEMS[index]).getGemID();
                check(dev.getCarriedItems().getInventory().countId(GEMS[index])==0&&dev.getCarriedItems().getInventory().countId(cutId)==2,"alternate dev chisel path uses same one-input bonus");
            }
            Player full=h.player("full gem fixture",448,451);full.getClientLimitations().maxItemId=Integer.MAX_VALUE;h.recordOutgoingPackets(full);h.equip(full,3409,1);
            for(int i=0;i<full.getCarriedItems().getInventory().getCapacity()-1;i++)full.getCarriedItems().getInventory().add(new Item(ItemId.CHISEL.id()));
            int xp=full.getSkills().getExperience(Skill.CRAFTING.id());
            h.random().reset(1);h.random().scriptInts(0);
            check(GemCuttingEquipment.awardSuccessfulCut(full,ItemId.SAPPHIRE.id()),"monocle procs with only one free slot");
            check(full.getCarriedItems().getInventory().countId(ItemId.SAPPHIRE.id())==1,"first cut gem fills freed slot");
            GroundItem bonus=h.world().getRegionManager().getRegion(full.getLocation()).getItem(ItemId.SAPPHIRE.id(),full.getLocation(),full);
            check(bonus!=null&&bonus.getAmount()==1,"bonus overflow preserved on owner ground");
            check(full.getSkills().getExperience(Skill.CRAFTING.id())==xp,"bonus itself grants no XP");
        }
        System.out.println("Gem dragons: PASS "+assertions+" assertions");
    }
    static void clearEquipment(Player p)throws Exception {
        Field f=com.openrsc.server.model.container.Equipment.class.getDeclaredField("list");f.setAccessible(true);
        Arrays.fill((Item[])f.get(p.getCarriedItems().getEquipment()),null);
    }
}
