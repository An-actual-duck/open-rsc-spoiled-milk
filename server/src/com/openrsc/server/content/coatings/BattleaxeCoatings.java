package com.openrsc.server.content.coatings;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Slow;
import com.openrsc.server.model.container.Equipment.EquipmentSlot;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.List;

/** Permanent item variants; combat uses the maintained shared Slow/poison engines. */
public final class BattleaxeCoatings {
    public static final int UNFINISHED=460, EYE=3342, GLAND=3341;
    public static final int LEVEL=46, EXPERIENCE=300;
    private BattleaxeCoatings() { }

    public static int equipped(Player player) {
        Item item=player.getCarriedItems().getEquipment().get(EquipmentSlot.SLOT_MAINHAND.getIndex());
        return item==null?-1:item.getCatalogId();
    }

    /** Item ID is snapshotted at primary-hit commitment, before damage callbacks. */
    public static void primaryHit(Mob source, Mob target, int committedWeaponId, int actualDamage) {
        if (!(source instanceof Player) || !(target instanceof Npc) || actualDamage<=0
                || !source.getConfig().WANT_MYWORLD || !BattleaxeCoatingCatalog.isCoated(committedWeaponId)
                || !((Player)source).loggedIn() || source.isRemoved() || source.killed || source.getLevel(Skill.HITS.id())<=0
                || target.isRemoved() || target.killed || target.getLevel(Skill.HITS.id())<=0
                || ((Npc)target).isRespawning()) return;
        Slow.apply(target,4,Slow.CAP_ONE);
    }

    public static boolean recipePair(int a,int b) {
        return (a==UNFINISHED&&(b==EYE||b==GLAND)) || (b==UNFINISHED&&(a==EYE||a==GLAND))
            || (a==EYE&&b==GLAND) || (a==GLAND&&b==EYE);
    }

    private static Item ingredient(Player player,int id) {
        for(Item item:player.getCarriedItems().getInventory().getItems())
            if(item.getCatalogId()==id&&!item.getNoted()&&!item.isWielded()&&item.getAmount()==1)return item;
        return null;
    }

    private static int identityIndex(List<Item> items,Item wanted) {
        for(int n=0;n<items.size();n++)if(items.get(n)==wanted)return n;
        return -1;
    }

    /** A fixed-size inventory exchange: validate all identities/outputs before any mutation. */
    private static boolean exchange(Player player,Item[] sources,int... outputs) {
        Inventory inv=player.getCarriedItems().getInventory();List<Item> items=inv.getItems();
        synchronized(items) {
            if(outputs.length>sources.length || !player.getConfig().WANT_MYWORLD)return false;
            for(int n=0;n<sources.length;n++) {
                Item item=sources[n];
                if(item==null||item.getItemId()==Item.ITEM_ID_UNASSIGNED||item.getAmount()!=1
                        ||item.getNoted()||item.isWielded()||identityIndex(items,item)<0)return false;
                for(int prior=0;prior<n;prior++)if(sources[prior]==item)return false;
                if(n<outputs.length&&(player.getWorld().getServer().getEntityHandler().getItemDef(outputs[n])==null
                        ||player.getClientLimitations().maxItemId<outputs[n]
                        ||(player.getConfig().RESTRICT_ITEM_ID>=0&&player.getConfig().RESTRICT_ITEM_ID<outputs[n])))return false;
            }
            // No operation below can fail after identity validation under the inventory list lock.
            for(int n=0;n<outputs.length;n++) {
                Item replacement=new Item(outputs[n],1,false,sources[n].getItemId());
                replacement.getItemStatus().setDurability(sources[n].getItemStatus().getDurability());
                items.set(identityIndex(items,sources[n]),replacement);
            }
            for(int n=outputs.length;n<sources.length;n++)items.remove(identityIndex(items,sources[n]));
            ActionSender.sendInventory(player);
            return true;
        }
    }

    public static boolean mix(Player player,Item used,Item with) {
        if(used==null||with==null||used.getNoted()||with.getNoted()||used.isWielded()||with.isWielded())return false;
        synchronized(player.getCarriedItems().getInventory().getItems()) {
            if(identityIndex(player.getCarriedItems().getInventory().getItems(),used)<0
                    ||identityIndex(player.getCarriedItems().getInventory().getItems(),with)<0)return false;
            if(!recipePair(used.getCatalogId(),with.getCatalogId()))return false;
            if(!player.getConfig().MEMBER_WORLD)return false;
            if(player.getConfig().WANT_FATIGUE&&player.getConfig().STOP_SKILLING_FATIGUED>=2&&player.getFatigue()>=player.MAX_FATIGUE)return false;
            if(player.getSkills().getLevel(Skill.HERBLAW.id())<LEVEL) {
                player.message("You need Herblaw level 46 to mix this coating.");return false;
            }
            if(player.getQuestStage(com.openrsc.server.constants.Quests.DRUIDIC_RITUAL)!=-1) {
                player.message("You need to complete Druidic ritual first.");return false;
            }
            if(!exchange(player,new Item[]{ingredient(player,UNFINISHED),ingredient(player,EYE),ingredient(player,GLAND)},BattleaxeCoatingCatalog.COATING)) {
                player.message("You need an unfinished avantoe potion, one Cockatrice Eye and one Sticky Saliva Gland.");return false;
            }
            player.incExp(Skill.HERBLAW.id(),EXPERIENCE,true);
            player.message("You mix one single-use dose of viscous weapon coating.");return true;
        }
    }

    public static boolean apply(Player player,Item coating,Item weapon) {
        if(coating==null||weapon==null||coating.getCatalogId()!=BattleaxeCoatingCatalog.COATING)return false;
        int coated=BattleaxeCoatingCatalog.coated(weapon.getCatalogId());
        if(coated<0) {player.message("Use this on an uncoated battleaxe.");return false;}
        boolean changed=exchange(player,new Item[]{weapon,coating},coated,ItemId.EMPTY_VIAL.id());
        if(changed)player.message("You coat your battleaxe. The empty vial remains.");
        return changed;
    }

    public static boolean wipe(Player player,Item weapon) {
        if(weapon==null||!BattleaxeCoatingCatalog.isCoated(weapon.getCatalogId()))return false;
        boolean changed=exchange(player,new Item[]{weapon},BattleaxeCoatingCatalog.base(weapon.getCatalogId()));
        if(changed)player.message("You wipe the coating away. It cannot be recovered.");
        return changed;
    }
}
