package com.openrsc.server.plugins.custom.itemactions;

import com.openrsc.server.content.coatings.BattleaxeCoatings;
import com.openrsc.server.content.coatings.BattleaxeCoatingCatalog;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import com.openrsc.server.plugins.triggers.UseInvTrigger;

public final class BattleaxeCoatingActions implements UseInvTrigger, OpInvTrigger {
    public boolean blockUseInv(Player p,Integer index,Item a,Item b) {
        return p.getConfig().WANT_MYWORLD&&(a.getCatalogId()==BattleaxeCoatingCatalog.COATING
            ||b.getCatalogId()==BattleaxeCoatingCatalog.COATING||BattleaxeCoatings.recipePair(a.getCatalogId(),b.getCatalogId()));
    }
    public void onUseInv(Player p,Integer index,Item a,Item b) {
        if(!blockUseInv(p,index,a,b))return;
        if(a.getCatalogId()==BattleaxeCoatingCatalog.COATING)BattleaxeCoatings.apply(p,a,b);
        else if(b.getCatalogId()==BattleaxeCoatingCatalog.COATING)BattleaxeCoatings.apply(p,b,a);
        else BattleaxeCoatings.mix(p,a,b);
    }
    public boolean blockOpInv(Player p,Integer index,Item item,String command) {
        return p.getConfig().WANT_MYWORLD&&BattleaxeCoatingCatalog.isCoated(item.getCatalogId())&&"Wipe clean".equalsIgnoreCase(command);
    }
    public void onOpInv(Player p,Integer index,Item item,String command) {
        if(blockOpInv(p,index,item,command))BattleaxeCoatings.wipe(p,item);
    }
}
