package com.openrsc.server.plugins.custom.itemactions;

import com.openrsc.server.content.coatings.ScimitarCoatings;
import com.openrsc.server.content.coatings.ScimitarCoatingCatalog;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.UseInvTrigger;

public final class ScimitarCoatingActions implements UseInvTrigger {
    public boolean blockUseInv(Player p,Integer index,Item a,Item b) {
        return p.getConfig().WANT_MYWORLD&&(a.getCatalogId()==ScimitarCoatingCatalog.COATING
            ||b.getCatalogId()==ScimitarCoatingCatalog.COATING||ScimitarCoatings.cleaningPair(a.getCatalogId(),b.getCatalogId()));
    }
    public void onUseInv(Player p,Integer index,Item a,Item b) {
        if(!blockUseInv(p,index,a,b))return;
        if(a.getCatalogId()==ScimitarCoatingCatalog.COATING)ScimitarCoatings.apply(p,a,b);
        else if(b.getCatalogId()==ScimitarCoatingCatalog.COATING)ScimitarCoatings.apply(p,b,a);
        else if(ScimitarCoatingCatalog.isCoated(a.getCatalogId()))ScimitarCoatings.wipe(p,a,b);
        else ScimitarCoatings.wipe(p,b,a);
    }
}
