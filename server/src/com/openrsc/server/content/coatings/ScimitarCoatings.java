package com.openrsc.server.content.coatings;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;

/** Harralander-based anti-coagulant; one dose coats one scimitar permanently. */
public final class ScimitarCoatings {
    private ScimitarCoatings() { }
    public static final int UNFINISHED=457, GLAND=MyWorldItemId.SANGUINARY_GLAND,
        SCALES=MyWorldItemId.GROUND_NAGA_SCALE, SCALE_COUNT=1, LEVEL=33, EXPERIENCE=275;

    public static boolean recipePair(int a,int b) {
        return a!=b && recipeIngredient(a) && recipeIngredient(b);
    }
    private static boolean recipeIngredient(int id) {
        return id==UNFINISHED || id==GLAND || id==SCALES;
    }
    private static Item ingredient(Player p,int id,int amount) {
        for(Item item:p.getCarriedItems().getInventory().getItems())
            if(item.getCatalogId()==id && !item.getNoted() && !item.isWielded()
                    && item.getAmount()==amount)return item;
        return null;
    }
    private static boolean present(Player p,Item item) {
        for(Item held:p.getCarriedItems().getInventory().getItems())if(held==item)return true;
        return false;
    }
    public static boolean mix(Player p,Item used,Item with) {
        if(used==null || with==null || used.getNoted() || with.getNoted()
                || used.isWielded() || with.isWielded())return false;
        synchronized(p.getCarriedItems().getInventory().getItems()) {
            if(!p.getConfig().WANT_MYWORLD || !p.getConfig().MEMBER_WORLD
                    || !present(p,used) || !present(p,with)
                    || !recipePair(used.getCatalogId(),with.getCatalogId()))return false;
            if(p.getConfig().WANT_FATIGUE && p.getConfig().STOP_SKILLING_FATIGUED>=2
                    && p.getFatigue()>=p.MAX_FATIGUE)return false;
            if(p.getSkills().getLevel(Skill.HERBLAW.id())<LEVEL) {
                p.message("You need Herblaw level 33 to mix anti-coagulant.");return false;
            }
            if(p.getQuestStage(Quests.DRUIDIC_RITUAL)!=-1) {
                p.message("You need to complete Druidic ritual first.");return false;
            }
            Item base=ingredient(p,UNFINISHED,1), gland=ingredient(p,GLAND,1),
                scales=ingredient(p,SCALES,SCALE_COUNT);
            if(base==null || gland==null || scales==null) {
                p.message("You need an unfinished harralander potion, one Sanguinary gland and one ground naga scale.");return false;
            }
            if(!BattleaxeCoatings.exchange(p,new Item[]{base,gland,scales},ScimitarCoatingCatalog.COATING))return false;
            p.incExp(Skill.HERBLAW.id(),EXPERIENCE,true);
            p.message("You mix one single-use dose of anti-coagulant.");return true;
        }
    }

    public static boolean cleaningPair(int a,int b) {
        return ScimitarCoatingCatalog.isCoated(a)&&b==ItemId.WOOL.id()
            || ScimitarCoatingCatalog.isCoated(b)&&a==ItemId.WOOL.id();
    }
    public static boolean apply(Player p,Item coating,Item weapon) {
        if(coating==null||weapon==null||coating.getCatalogId()!=ScimitarCoatingCatalog.COATING)return false;
        int id=ScimitarCoatingCatalog.coated(weapon.getCatalogId());
        if(id<0) {p.message("Use this on an uncoated scimitar.");return false;}
        boolean changed=BattleaxeCoatings.exchange(p,new Item[]{weapon,coating},id,ItemId.EMPTY_VIAL.id());
        if(changed)p.message("You coat your scimitar. The empty vial remains.");
        return changed;
    }
    public static boolean wipe(Player p,Item weapon,Item wool) {
        if(weapon==null||wool==null||!ScimitarCoatingCatalog.isCoated(weapon.getCatalogId())
            ||wool.getCatalogId()!=ItemId.WOOL.id())return false;
        boolean changed=BattleaxeCoatings.exchange(p,new Item[]{weapon,wool},ScimitarCoatingCatalog.base(weapon.getCatalogId()));
        if(changed)p.message("You wipe the coating away with the wool. Neither can be recovered.");
        return changed;
    }
}
