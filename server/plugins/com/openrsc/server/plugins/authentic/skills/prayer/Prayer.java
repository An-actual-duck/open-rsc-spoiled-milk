package com.openrsc.server.plugins.authentic.skills.prayer;

import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.util.rsc.MessageType;

import static com.openrsc.server.plugins.Functions.*;

public class Prayer implements OpLocTrigger {

	@Override
	public void onOpLoc(Player player, final GameObject object, String command) {
		boolean wantsWorship = command.equalsIgnoreCase("worship") || command.equalsIgnoreCase("recharge at");
		boolean allowPrayerAltars = !player.getConfig().LACKS_PRAYERS;
		if (wantsWorship && !allowPrayerAltars) {
			player.message("World does not feature prayers!");
		} else if (wantsWorship && allowPrayerAltars) {
			PrayerCatalog.GodLine godLine = PrayerCatalog.getGodLineForAltar(object.getID(), object.getX(), object.getY());
			if (godLine == null) {
				player.playerServerMessage(MessageType.QUEST, "This altar does not answer your prayers");
			} else {
				player.setPrayerBook(godLine);
				player.playerServerMessage(MessageType.QUEST, "You align your prayers with " + formatGodLine(godLine));
				player.playSound("recharge");
			}
		}
		// chaos altar in Yanille dungeon is a trapdoor
		if (wantsWorship && (object.getID() == 625 && object.getY() == 3573)) {
			delay();
			mes("Suddenly a trapdoor opens beneath you");
			delay(3);
			player.teleport(608, 3525);
		}
	}

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return command.equalsIgnoreCase("recharge at") || command.equalsIgnoreCase("worship");
	}

	private String formatGodLine(final PrayerCatalog.GodLine godLine) {
		final String lower = godLine.name().toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
