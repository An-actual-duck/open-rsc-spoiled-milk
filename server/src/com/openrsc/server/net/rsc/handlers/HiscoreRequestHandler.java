package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.login.HiscoreLookupRequest;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.HiscoreRequestStruct;

public class HiscoreRequestHandler implements PayloadProcessor<HiscoreRequestStruct, OpcodeIn> {

	private static final long REQUEST_COOLDOWN_MS = 500;
	private static final String LAST_REQUEST_ATTRIBUTE = "last_hiscore_request";

	@Override
	public void process(HiscoreRequestStruct payload, Player player) throws Exception {
		if (player == null || !player.getWorld().getServer().getConfig().WANT_HISCORES
			|| !player.isUsingCustomClient()) {
			return;
		}

		final int skillId = payload.skillId;
		if (skillId != HiscoreLookupRequest.OVERALL_ID
			&& (skillId < 0 || skillId >= player.getWorld().getServer().getConstants().getSkills().getSkillsCount())) {
			return;
		}

		// Light throttle so a misbehaving client cannot spam ranking queries
		final long now = System.currentTimeMillis();
		final long lastRequest = player.getAttribute(LAST_REQUEST_ATTRIBUTE, 0L);
		if (now - lastRequest < REQUEST_COOLDOWN_MS) {
			return;
		}
		player.setAttribute(LAST_REQUEST_ATTRIBUTE, now);

		player.getWorld().getServer().getLoginExecutor().add(
			new HiscoreLookupRequest(player.getWorld().getServer(), player, skillId));
	}
}
