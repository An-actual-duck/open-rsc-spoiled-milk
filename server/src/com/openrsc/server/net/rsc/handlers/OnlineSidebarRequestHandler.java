package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.content.OnlinePlayerList;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.NoPayloadStruct;

public final class OnlineSidebarRequestHandler implements PayloadProcessor<NoPayloadStruct, OpcodeIn> {
	private static final long MIN_REFRESH_INTERVAL_MS = 5000;
	private static final String LAST_REQUEST = "last_online_sidebar_request";

	@Override
	public void process(NoPayloadStruct payload, Player player) {
		if (player == null || !player.isLoggedIn() || !player.isUsingCustomClient()
			|| !player.getConfig().PLAYER_COMMANDS) return;

		long now = System.currentTimeMillis();
		long last = player.getAttribute(LAST_REQUEST, 0L);
		if (now - last < MIN_REFRESH_INTERVAL_MS) return;
		player.setAttribute(LAST_REQUEST, now);
		OnlinePlayerList.send(player, false, false, true);
	}
}
