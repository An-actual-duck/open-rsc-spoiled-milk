package com.openrsc.server.plugins.custom.misc;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpLocTrigger;

import static com.openrsc.server.plugins.Functions.*;

public final class RangersGuildDoor implements OpLocTrigger {
	private static final int CLOSED_DOUBLE_DOORS = 64;
	private static final int OPEN_DOUBLE_DOORS = 63;
	private static final int DOOR_X = 495;
	private static final int DOOR_Y = 463;
	private static final int DOOR_DIRECTION = 2;

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return isRangersGuildDoor(obj) && command.equalsIgnoreCase("open");
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (!isRangersGuildDoor(obj)) {
			return;
		}

		if (getCurrentLevel(player, Skill.RANGED.id()) < 66) {
			Npc ranger = player.getWorld().getNpc(NpcId.RANGERS_GUILD_RANGER.id(), 493, 499, 461, 464);
			if (ranger != null) {
				npcsay(player, ranger, "Sorry, only skilled rangers are allowed in here");
			}
			player.message("You need a ranged level of 66 to enter the guild");
			return;
		}

		boolean entering = player.getY() <= DOOR_Y;
		int targetX = player.getX() <= DOOR_X ? DOOR_X : DOOR_X + 1;
		doDoor(obj, player, OPEN_DOUBLE_DOORS);
		player.teleport(targetX, entering ? DOOR_Y + 1 : DOOR_Y - 1);
	}

	private boolean isRangersGuildDoor(GameObject obj) {
		return obj.getID() == CLOSED_DOUBLE_DOORS
			&& obj.getX() == DOOR_X
			&& obj.getY() == DOOR_Y
			&& obj.getDirection() == DOOR_DIRECTION;
	}
}
