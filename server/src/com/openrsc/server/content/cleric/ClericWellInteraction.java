package com.openrsc.server.content.cleric;

import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.util.rsc.CollisionFlag;

/** Game-owned well identities and land-side admission for sigil infusion. */
public final class ClericWellInteraction {
    public static final int ICHOR_WELL = 1354;
    public static final int DIVINE_WELLSPRING = 1355;
    private ClericWellInteraction() { }
    public static boolean isWell(int id) { return id == ICHOR_WELL || id == DIVINE_WELLSPRING; }

    public static boolean canInteract(Player player, GameObject object) {
        if (player == null || object == null || !isWell(object.getID()) || player.isRemoved() || object.isRemoved()
                || player.getWorld() != object.getWorld()) return false;
        try {
            WorldLocation here = player.getWorldLocation(), there = object.getWorldLocation();
            if (!here.getWorldSpace().equals(there.getWorldSpace())
                    || here.getCoordinate().getLevel() != there.getCoordinate().getLevel()) return false;
            if (player.getViewArea().getGameObject(object.getLocation()) != object) return false;
            TileValue standing = player.getTileAtCurrentLevel(player.getX(), player.getY());
            return standing != null && (standing.traversalMask & (CollisionFlag.FULL_BLOCK | CollisionFlag.OBJECT)) == 0
                    && player.atObject(object);
        } catch (IllegalArgumentException | IllegalStateException unavailableScope) {
            return false;
        }
    }

    public static String guidance(int id) {
        if (id == ICHOR_WELL) return "Dip uninfused Blight sigils into this dark ichor.";
        if (id == DIVINE_WELLSPRING) return "Dip uninfused Grace sigils into this pale-blue divine water.";
        throw new IllegalArgumentException("Not a sigil well");
    }
}
