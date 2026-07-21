package com.openrsc.server.plugins.custom.myworld.skills.agility;

import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.util.rsc.DataConversions;

import static com.openrsc.server.plugins.Functions.delay;
import static com.openrsc.server.plugins.Functions.teleport;

public final class LumbridgeAlKharidSteppingStones implements OpLocTrigger {
	private static final int STONE_Y = 686;
	private static final Point AL_KHARID_BANK = Point.location(102, STONE_Y);
	private static final Point WEST_STONE = Point.location(103, STONE_Y);
	private static final Point CENTRE_STONE = Point.location(104, STONE_Y);
	private static final Point EAST_STONE = Point.location(105, STONE_Y);
	private static final Point LUMBRIDGE_BANK = Point.location(106, STONE_Y);
	private static final int SLIP_CHANCE_PERCENT = 10;

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return isAlKharidEndpoint(obj) || isLumbridgeEndpoint(obj);
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (isAlKharidEndpoint(obj)) {
			cross(player, AL_KHARID_BANK, LUMBRIDGE_BANK, WEST_STONE, EAST_STONE);
		} else if (isLumbridgeEndpoint(obj)) {
			cross(player, LUMBRIDGE_BANK, AL_KHARID_BANK, EAST_STONE, WEST_STONE);
		}
	}

	private static boolean isAlKharidEndpoint(GameObject obj) {
		return obj.getID() == SceneryId.STEPPING_STONE_AL_KHARID_TO_LUMBRIDGE.id()
			&& obj.getX() == WEST_STONE.getX() && obj.getY() == WEST_STONE.getY();
	}

	private static boolean isLumbridgeEndpoint(GameObject obj) {
		return obj.getID() == SceneryId.STEPPING_STONE_LUMBRIDGE_TO_AL_KHARID.id()
			&& obj.getX() == EAST_STONE.getX() && obj.getY() == EAST_STONE.getY();
	}

	private static void cross(Player player, Point departureBank, Point destinationBank,
		Point firstStone, Point lastStone) {
		player.message("You jump onto the stepping stones.");
		teleport(player, firstStone.getX(), firstStone.getY());
		delay();
		teleport(player, CENTRE_STONE.getX(), CENTRE_STONE.getY());
		delay();

		if (DataConversions.random(1, 100) <= SLIP_CHANCE_PERCENT) {
			player.message("You slip and splash into the river.");
			delay();
			teleport(player, departureBank.getX(), departureBank.getY());
			player.message("You scramble back onto the bank.");
			return;
		}

		teleport(player, lastStone.getX(), lastStone.getY());
		delay();
		teleport(player, destinationBank.getX(), destinationBank.getY());
		player.message("You make it safely to the other bank.");
	}
}
