package com.openrsc.server.content.coatings;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;

/** One dose coats one scimitar permanently; acquisition is deliberately not assigned. */
public final class ScimitarCoatings {
    private ScimitarCoatings() { }
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
