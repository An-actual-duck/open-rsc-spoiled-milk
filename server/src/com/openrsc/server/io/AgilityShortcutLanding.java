package com.openrsc.server.io;

import com.openrsc.server.external.TileDef;
import com.openrsc.server.model.world.coordinate.LayeredCompatibilityPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.util.rsc.CollisionFlag;

/** Conservative authored full-floor proof, independent from walkability in upper-level void. */
public final class AgilityShortcutLanding {
	public interface Terrain {
		NativeLayeredTerrainTile tile(WorldLocation location);
		TileDef definition(int id);
		boolean collisionClear(WorldLocation location);
	}
	private AgilityShortcutLanding() {}
	/** Cardinal edge flags bound a tile; they do not occupy its interior. */
	public static boolean collisionMaskClear(int mask) {
		return (mask & (CollisionFlag.FULL_BLOCK | CollisionFlag.OBJECT)) == 0;
	}
	public static void requireSafe(WorldLocation location, Terrain terrain) {
		LayeredCompatibilityPointAdapter.toCompatibilityPoint(location, false, true);
		int x = location.getCoordinate().getX(), y = location.getCoordinate().getY(), level = location.getCoordinate().getLevel();
		if (!"global".equals(location.getWorldSpace().getValue()) || x < 1 || y < 1 || x > 32766 || y > 32766)
			refuse(location, "outside supported authored coordinates");
		NativeLayeredTerrainTile center = terrain.tile(location);
		if (center == null) refuse(location, "missing authored floor");
		int overlay = center.getOverlay();
		if (overlay == 255 || overlay == 250 || (overlay == 0 && (level == 1 || level == 2)))
			refuse(location, "void or incomplete floor");
		if (overlay > 0) {
			TileDef definition = terrain.definition(overlay - 1);
			if (definition == null || definition.getObjectType() != 0 || definition.getColour() == 12345678
				|| definition.getUnknown() < 0 || definition.getUnknown() > 2)
				refuse(location, "blocked, invisible, bridge or unsupported floor");
		}
		for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
			NativeLayeredTerrainTile tile = terrain.tile(at(location, dx, dy));
			if (tile == null || tile.getOverlay() != overlay || tile.getTexture() != center.getTexture())
				refuse(location, "landing needs a complete matching 3 by 3 floor area");
		}
		// A diagonal/embedded field crosses the actual tile. Neighbor walls and
		// cardinal edges are safe beside a pipe exit; the floor proof above is
		// intentionally still a homogeneous 3x3 area, not merely walkability.
		if (center.getDiagonalWall() != 0 || !terrain.collisionClear(location))
			refuse(location, "landing is obstructed");
	}
	private static WorldLocation at(WorldLocation location, int dx, int dy) {
		return new WorldLocation(location.getWorldSpace(), new WorldCoordinate(location.getCoordinate().getX() + dx,
			location.getCoordinate().getY() + dy, location.getCoordinate().getLevel()));
	}
	private static void refuse(WorldLocation location, String reason) {
		throw new IllegalStateException("Unsafe agility shortcut landing at " + location + ": " + reason);
	}
}
