package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming
	.LayeredTerrainStageReadyStruct;

/** Applies an exact cache-only native terrain stage receipt. */
public final class LayeredTerrainStageReadyHandler
	implements PayloadProcessor<LayeredTerrainStageReadyStruct, OpcodeIn> {

	@Override
	public void process(
		final LayeredTerrainStageReadyStruct payload,
		final Player player) {
		player.getWorld().getServer().getGameUpdater()
			.acceptLayeredTerrainStageReady(player, payload);
	}
}
