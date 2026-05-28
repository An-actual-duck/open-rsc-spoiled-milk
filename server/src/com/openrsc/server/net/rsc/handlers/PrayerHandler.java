package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.PrayerStruct;

public class PrayerHandler implements PayloadProcessor<PrayerStruct, OpcodeIn> {

	public void process(final PrayerStruct payload, final Player player) throws Exception {
		final int prayerID = payload.prayerID;

		if (prayerID < 0 || prayerID >= PrayerCatalog.PRAYERS_PER_BOOK) {
			player.setSuspiciousPlayer(true,
				String.format("prayerID < 0 or prayerID >= %d", PrayerCatalog.PRAYERS_PER_BOOK));
			return;
		}

		if (player.getConfig().LACKS_PRAYERS) {
			player.message("World does not feature prayers!");
			return;
		}

		if (player.getDuel().isDuelActive() && player.getDuel().getDuelSetting(2)) {
			player.message("Prayers cannot be used during this duel!");
			return;
		}

		final Prayers prayers = player.getPrayers();
		final OpcodeIn opcode = payload.getOpcode();

		if (opcode == OpcodeIn.PRAYER_ACTIVATED) {
			if (prayers.isPrayerActivated(prayerID)) {
				return;
			}
			if (!prayers.canActivate(prayerID)) {
				final PrayerCatalog.PrayerDefinition definition = PrayerCatalog.getDefinition(player.getPrayerBook(), prayerID);
				final int pointCost = definition != null ? definition.getPointCost() : 0;
				player.message("You need " + pointCost + " free prayer points to activate this prayer");
				return;
			}
			prayers.setPrayer(prayerID, true);
		} else if (opcode == OpcodeIn.PRAYER_DEACTIVATED) {
			if (prayers.isPrayerActivated(prayerID)) {
				prayers.setPrayer(prayerID, false);
			}
		}
	}
}
