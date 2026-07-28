package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.LayeredTerrainReadyStruct;

/** Applies an exact native-terrain readiness receipt to its pending context. */
public final class LayeredTerrainReadyHandler
	implements PayloadProcessor<LayeredTerrainReadyStruct, OpcodeIn> {

	@Override
	public void process(
		final LayeredTerrainReadyStruct payload,
		final Player player) {
		player.getWorld().getServer().getGameUpdater()
			.acceptLayeredTerrainReady(player, payload);
	}
}
