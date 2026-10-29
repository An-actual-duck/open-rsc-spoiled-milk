package com.openrsc.server.plugins.custom.myworld.skills.blessing;

import com.openrsc.server.content.cleric.ClericWellInteraction;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpLocTrigger;

/** Owner-approved building stage: real location interaction, charging added with the Cleric overhaul. */
public final class SigilWells implements OpLocTrigger {
    @Override public boolean blockOpLoc(Player player, GameObject object, String command) {
        return player != null && player.getConfig().WANT_MYWORLD && object != null
                && ClericWellInteraction.isWell(object.getID()) && "Dip sigils".equalsIgnoreCase(command);
    }
    @Override public void onOpLoc(Player player, GameObject object, String command) {
        if (!blockOpLoc(player, object, command) || !ClericWellInteraction.canInteract(player, object)) return;
        player.message(ClericWellInteraction.guidance(object.getID()));
    }
}
