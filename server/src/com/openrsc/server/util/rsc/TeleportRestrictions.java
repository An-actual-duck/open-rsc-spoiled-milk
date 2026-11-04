package com.openrsc.server.util.rsc;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.player.Player;

/** Eligibility for player teleports, not forced travel or respawn movement. */
public final class TeleportRestrictions {
    public static final int WILDERNESS_LIMIT = 20;

    private TeleportRestrictions() { }

    public static boolean isDuelBlocked(Player player) {
        return player.getDuel().isDuelActive();
    }

    /** Checked before item-action reset code can clear the duel state. */
    public static boolean isDuelItemTeleportBlocked(Player player, String command) {
        return isDuelBlocked(player) && "teleport".equalsIgnoreCase(command);
    }

    public static boolean isLocationBlocked(Point location) {
        return location.wildernessLevel() >= WILDERNESS_LIMIT
            || location.isInFisherKingRealm()
            || location.isInsideGrandTreeGround()
            || location.inModRoom();
    }

    public static boolean isBlocked(Player player) {
        return isDuelBlocked(player) || isLocationBlocked(player.getLocation())
            || player.getCarriedItems().getInventory().countId(ItemId.ANA_IN_A_BARREL.id()) > 0;
    }
}
