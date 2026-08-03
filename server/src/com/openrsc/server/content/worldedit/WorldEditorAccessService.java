package com.openrsc.server.content.worldedit;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.struct.outgoing.WorldEditorStruct;

/** Shared authoritative entry point for command and automatic editor sessions. */
public final class WorldEditorAccessService {
	private WorldEditorAccessService() {
	}

	public static boolean open(Player player) {
		if (!WorldBuilderPlayerSession.mayOpenEditor(player)) {
			player.message(player.getConfig().MESSAGE_PREFIX
				+ "Adaptive Builder client/server binding is not complete.");
			return false;
		}
		WorldEditorSessionManager.OpenResult result = player.getWorld().getServer().getWorldEditorSessions()
			.open(player, player.getConfig().ALLOW_IN_GAME_WORLD_EDITOR && player.getClientVersion() > 10000);
		if (!result.opened) {
			player.message(player.getConfig().MESSAGE_PREFIX + result.message);
			return false;
		}
		WorldEditorStruct out = new WorldEditorStruct();
		out.type = 1;
		out.sessionId = result.sessionId;
		out.sequence = result.nextSequence;
		ActionSender.sendWorldEditor(player, out);
		player.message(player.getConfig().MESSAGE_PREFIX
			+ "World editor session opened. Terrain painting uses an unsaved server draft.");
		return true;
	}
}
