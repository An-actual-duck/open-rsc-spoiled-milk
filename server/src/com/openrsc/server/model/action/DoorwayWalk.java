package com.openrsc.server.model.action;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.PluginTask;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.plugins.PluginInterruptedException;

/** Short, already-authorized scripted crossings through a door's collision boundary. */
public final class DoorwayWalk {
    private static final int MAX_STEPS = 16;
    private DoorwayWalk() { }

    public static void cross(Player player, Point destination) {
        WorldLocation expected = player.getWorldLocation();
        WorldLocation target = player.getWorld().getRegionManager()
            .fromRuntimeCompatibilityPoint(destination, expected, false);
        if (!expected.getWorldSpace().equals(target.getWorldSpace())
            || expected.getCoordinate().getLevel() != target.getCoordinate().getLevel()) {
            throw new IllegalArgumentException("A doorway cannot change world space or level");
        }
        long distance = Math.max(Math.abs((long)target.getCoordinate().getX()-expected.getCoordinate().getX()),
            Math.abs((long)target.getCoordinate().getY()-expected.getCoordinate().getY()));
        if (distance > MAX_STEPS) throw new IllegalArgumentException("Doorway crossing is too long");
        PluginTask task = PluginTask.getContextPluginTask();
        if (distance > 1 && task == null) throw new IllegalStateException("Multi-step doorway crossing requires a plugin context");
        player.resetPath();
        // An approach may have arrived earlier in this same tick. Preserve its
        // movement update before beginning the first step through the doorway.
        if (task != null && player.hasMoved()) task.pause(1);
        for (;;) {
            if (!player.loggedIn() || player.isRemoved() || player.getLevel(Skill.HITS.id()) <= 0
                || !player.getWorldLocation().equals(expected)) {
                throw new PluginInterruptedException("Doorway crossing was cancelled");
            }
            if (expected.equals(target)) return;
            WorldCoordinate current = expected.getCoordinate(), end = target.getCoordinate();
            WorldLocation next = new WorldLocation(expected.getWorldSpace(), new WorldCoordinate(
                current.getX()+Integer.compare(end.getX(),current.getX()),
                current.getY()+Integer.compare(end.getY(),current.getY()), current.getLevel()));
            Point step = player.getWorld().getRegionManager().toRuntimeCompatibilityPoint(next);
            player.face(step);
            if (player.isLayeredLocationAuthorityEnabled()) player.setLayeredLocation(next,false);
            else player.setLocation(step,false);
            expected = next;
            // Yield after the final step too: a caller may continue another short
            // crossing, and those steps must not collapse into one network update.
            if (task != null) task.pause(1);
        }
    }
}
