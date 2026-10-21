package com.openrsc.server.model.action;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.SingleTickEvent;
import com.openrsc.server.external.GameObjectDef;
import com.openrsc.server.external.TileDef;
import com.openrsc.server.io.NativeLayeredTerrainTile;
import com.openrsc.server.io.NativeLayeredWorldPackage;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GameObjectCollisionRegistrationState;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.region.RegionManager;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.util.rsc.CollisionFlag;

/** The existing open/pass/close interaction for the explicitly generic double-door pair.
 * No persistent door state, access rules, timers on saved placements or quest interception.
 */
public final class DoubleDoorPassage {
	private DoubleDoorPassage() {}

	public static void begin(final Player player, final GameObject closed, final int openId) {
		if (player.getWorld() != closed.getWorld() || !player.isLoggedIn() || player.isRemoved() || player.isBusy()
			|| closed.isRemoved() || !current(closed)) return;
		GameObjectDef original = closed.getGameObjectDef();
		GameObjectDef opened = player.getWorld().getServer().getEntityHandler().getGameObjectDef(openId);
		// The temporary visual remains blocking, just like a guild door's open frame.
		// A second player must activate their own access-checked passage, not follow through.
		if (original.getType() != 2 || opened == null || opened.getType() != 2
			|| original.getWidth() != 1 || original.getHeight() != 2
			|| opened.getWidth() != 1 || opened.getHeight() != 2) return;
		final WorldLocation destination = destination(closed.getWorldLocation(), closed.getDirection(), player.getWorldLocation());
		if (destination == null) return;
		try {
			requireSafe(player, closed, player.getWorldLocation());
			requireSafe(player, closed, destination);
		} catch (IllegalStateException unavailable) {
			player.message("There isn't a clear, safe path through these doors."); return;
		}
		final GameObject open = new GameObject(closed.getWorld(), closed.getLocation(), openId, closed.getDirection(), closed.getType());
		final SingleTickEvent close = new SingleTickEvent(closed.getWorld(), null, 2,
			"Generic double door close", DuplicationStrategy.ALLOW_MULTIPLE) {
			public void action() {
				// Exact instance/generation check: never resurrect a removed/replaced door.
				if (current(open)) open.getWorld().replaceGameObject(open, new GameObject(closed.getWorld(), closed.getLoc()));
			}
		};
		boolean scheduled = player.getWorld().getServer().getGameEventHandler().add(close);
		if (!scheduled) return;
		try {
			closed.getWorld().replaceGameObject(closed, open);
			player.playSound("opendoor");
			// Same immediate passage as doGate; retain the lane and explicit signed level.
			int sprite = player.getSprite();
			player.teleport(destination.getCoordinate().getX(), destination.getCoordinate().getY(), destination.getCoordinate().getLevel(), false);
			player.setSprite(sprite);
		} catch (RuntimeException failure) {
			close.stop();
			if (current(open)) open.getWorld().replaceGameObject(open, new GameObject(closed.getWorld(), closed.getLoc()));
			throw failure;
		}
		// No player-owned delayed callback or busy token: logout/reset cannot strand the door open.
	}

	/** Two lanes, either side, only cardinal directions; never snap an end-on click sideways. */
	public static WorldLocation destination(WorldLocation door, int direction, WorldLocation player) {
		if (!door.getWorldSpace().equals(player.getWorldSpace())
			|| door.getCoordinate().getLevel() != player.getCoordinate().getLevel()) return null;
		int x = door.getCoordinate().getX(), y = door.getCoordinate().getY();
		int px = player.getCoordinate().getX(), py = player.getCoordinate().getY();
		if (direction == 0 && py >= y && py <= y + 1 && (px == x || px == x - 1)) return at(door, px == x ? x - 1 : x, py);
		if (direction == 4 && py >= y && py <= y + 1 && (px == x || px == x + 1)) return at(door, px == x ? x + 1 : x, py);
		if (direction == 2 && px >= x && px <= x + 1 && (py == y || py == y + 1)) return at(door, px, py == y ? y + 1 : y);
		if (direction == 6 && px >= x && px <= x + 1 && (py == y || py == y - 1)) return at(door, px, py == y ? y - 1 : y);
		return null;
	}

	private static boolean current(GameObject object) {
		if (object.isRemoved() || object.getLocation() == null) return false;
		RegionManager regions = object.getWorld().getRegionManager();
		return regions.hasNativeLayeredGameObjectIdentity(object)
			? regions.isNativeLayeredGameObject(object) && regions.findNativeLayeredGameObject(object) == object
			: regions.findInteractionScenery(object.getLocation(), object) == object;
	}

	private static void requireSafe(Player player, GameObject door, WorldLocation destination) {
		int x = destination.getCoordinate().getX(), y = destination.getCoordinate().getY();
		if (!"global".equals(destination.getWorldSpace().getValue()) || x < 1 || y < 1 || x > 32766 || y > 32766)
			throw new IllegalStateException("Outside supported authored terrain");
		RegionManager regions = player.getWorld().getRegionManager();
		NativeLayeredWorldPackage owner = regions.findNativeLayeredWorldPackage(door.getWorldLocation()).orElse(null);
		if (owner == null || !regions.hasNativeLayeredGameObjectIdentity(door))
			throw new IllegalStateException("Generic passage requires authored package terrain");
		// Match the renderer's floor support, not a walkability test on upper-level void.
		// Surrounding void is legitimate outside a narrow upstairs hallway; only the
		// occupied tile must be fully visible. Known neighbors determine split edges.
		int[][] classes = new int[3][3];
		int centerType = -1, centerDiagonal = 0;
		for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
			WorldLocation sample = at(destination, x + dx, y + dy);
			int overlay;
			int diagonal;
			if (regions.findNativeLayeredWorldPackage(sample).orElse(null) != owner)
				throw new IllegalStateException("Missing authored terrain");
			NativeLayeredTerrainTile tile = owner.findTile(sample).orElse(null);
			if (tile == null) throw new IllegalStateException("Missing authored terrain");
			overlay = tile.getOverlay();
			diagonal = tile.getDiagonalWall();
			boolean center = dx == 0 && dy == 0;
			if (overlay == 250) throw new IllegalStateException("Unresolved floor");
			int type = -1;
			if (overlay > 0 && overlay != 255) {
				TileDef definition = player.getWorld().getServer().getEntityHandler().getTileDef(overlay - 1);
				if (definition == null || definition.getUnknown() < 0 || definition.getUnknown() > 5)
					throw new IllegalStateException("Unsupported floor");
				type = definition.getUnknown();
				if (center && (definition.getObjectType() != 0 || definition.getColour() == 12345678 || type > 2))
					throw new IllegalStateException("Blocked, invisible or partial floor");
			}
			if (center && (overlay == 255 || (overlay == 0 && (sample.getCoordinate().getLevel() == 1 || sample.getCoordinate().getLevel() == 2))))
				throw new IllegalStateException("Void floor");
			classes[dx + 1][dy + 1] = overlay == 0 ? -1 : type == 2 ? 1 : 0;
			if (center) { centerType = type; centerDiagonal = diagonal; }
		}
		if (centerDiagonal > 0 && centerDiagonal < 24000) throw new IllegalStateException("Diagonal passage is unsupported");
		if ((destination.getCoordinate().getLevel() == 1 || destination.getCoordinate().getLevel() == 2) && centerType != 2) {
			int center = classes[1][1];
			boolean left = classes[0][1] != center, right = classes[2][1] != center;
			boolean up = classes[1][0] != center, down = classes[1][2] != center;
			if ((left && up) || (right && down) || (right && up) || (left && down))
				throw new IllegalStateException("Floor has an empty rendered half");
		}
		TileValue actual = regions.getTile(destination);
		if (actual == null) throw new IllegalStateException("Missing runtime landing");
		TileValue clear = actual.copy();
		GameObjectCollisionRegistrationState own = door.getCollisionRegistrationState();
		if (own == null) throw new IllegalStateException("Door has no exact collision footprint");
		for (GameObjectCollisionRegistrationState.CollisionContribution contribution : own.getContributions()) {
			if (contribution.getX() != x || contribution.getY() != y) continue;
			for (int count = 0; count < contribution.getBlockingSceneryCount(); count++) clear.removeBlockingScenery();
			for (int bit = 0; bit < 6; bit++) for (int count = 0; count < contribution.getDynamicCollisionCount(bit); count++) clear.removeDynamicCollision(1 << bit);
		}
		WorldCoordinate origin = door.getWorldLocation().getCoordinate();
		int passageEdge;
		switch (door.getDirection()) {
			case 0: passageEdge = x == origin.getX() ? CollisionFlag.WALL_EAST : CollisionFlag.WALL_WEST; break;
			case 4: passageEdge = x == origin.getX() ? CollisionFlag.WALL_WEST : CollisionFlag.WALL_EAST; break;
			case 2: passageEdge = y == origin.getY() ? CollisionFlag.WALL_SOUTH : CollisionFlag.WALL_NORTH; break;
			case 6: passageEdge = y == origin.getY() ? CollisionFlag.WALL_NORTH : CollisionFlag.WALL_SOUTH; break;
			default: throw new IllegalStateException("Unsupported direction");
		}
		if ((clear.traversalMask & (CollisionFlag.FULL_BLOCK | passageEdge)) != 0)
			throw new IllegalStateException("Landing or passage is blocked by another object or wall");
	}
	private static WorldLocation at(WorldLocation source, int x, int y) {
		return new WorldLocation(source.getWorldSpace(), new WorldCoordinate(x, y, source.getCoordinate().getLevel()));
	}
}
