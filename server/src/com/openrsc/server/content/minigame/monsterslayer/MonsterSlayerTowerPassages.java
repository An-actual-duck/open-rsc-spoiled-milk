package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;

/** Game-owned bindings for the authored tower. These are not editor content rules. */
public final class MonsterSlayerTowerPassages {
	private MonsterSlayerTowerPassages() { }

	/** Only the exact upward stairs are gated. Existing transport handles successful use. */
	public static MonsterSlayerTowerAccess stairs(WorldLocation location, int objectId,
		int direction, int type, String command) {
		if (location == null || !WorldSpaceId.GLOBAL.equals(location.getWorldSpace())
			|| objectId != 43 || direction != 0 || type != 0 || !isUp(command)) return null;
		WorldCoordinate point = location.getCoordinate();
		if (point.getY() != 472) return null;
		switch (point.getLevel()) {
			case 0: return point.getX() == 6 ? MonsterSlayerTowerAccess.ADEPT : null;
			case 1: return point.getX() == 37 ? MonsterSlayerTowerAccess.VETERAN : null;
			case 2: return point.getX() == 6 ? MonsterSlayerTowerAccess.ELITE : null;
			case 3: return point.getX() == 37 ? MonsterSlayerTowerAccess.CHAMPION : null;
			case 4: return point.getX() == 6 ? MonsterSlayerTowerAccess.HERO : null;
			default: return null;
		}
	}

	/** The south-to-north entrance has two lanes, and never restricts leaving. */
	public static MonsterSlayerTowerAccess entrance(WorldLocation location, int objectId,
		int direction, int type, WorldLocation player) {
		if (location == null || player == null || !WorldSpaceId.GLOBAL.equals(location.getWorldSpace())
			|| !location.getWorldSpace().equals(player.getWorldSpace())
			|| location.getCoordinate().getLevel() != 0 || player.getCoordinate().getLevel() != 0
			|| location.getCoordinate().getX() != 22 || location.getCoordinate().getY() != 474
			|| (objectId != 1337 && objectId != 1338) || direction != 6 || type != 0) return null;
		WorldCoordinate from = player.getCoordinate();
		return from.getY() >= 474 ? MonsterSlayerTowerAccess.FLEDGLING : null;
	}

	public static MonsterSlayerTowerAccess passage(Player player, GameObject object, String command) {
		if (!eligibleProfile(player, object)) return null;
		MonsterSlayerTowerAccess entry = "open".equalsIgnoreCase(command)
			? entrance(object.getWorldLocation(), object.getID(), object.getDirection(),
				object.getType(), player.getWorldLocation()) : null;
		return entry != null ? entry : stairs(player, object, command);
	}

	public static MonsterSlayerTowerAccess stairs(Player player, GameObject object, String command) {
		if (!eligibleProfile(player, object)) return null;
		return stairs(object.getWorldLocation(), object.getID(), object.getDirection(), object.getType(), command);
	}

	private static boolean eligibleProfile(Player player, GameObject object) {
		return player != null && object != null && player.getConfig().WANT_MYWORLD
			&& player.getWorld() == object.getWorld()
			&& player.getWorldLocation().getWorldSpace().equals(object.getWorldLocation().getWorldSpace())
			&& player.getWorldLocation().getCoordinate().getLevel() == object.getWorldLocation().getCoordinate().getLevel();
	}

	private static boolean isUp(String command) {
		return "climb-up".equalsIgnoreCase(command) || "climb up".equalsIgnoreCase(command)
			|| "go up".equalsIgnoreCase(command);
	}
}
