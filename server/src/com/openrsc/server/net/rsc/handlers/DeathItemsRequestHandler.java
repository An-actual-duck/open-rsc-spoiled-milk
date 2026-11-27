package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.PacketBuilder;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.DeathItemsRequestStruct;

/** Read-only preview of the normal death-drop rules for the requesting player. */
public final class DeathItemsRequestHandler implements PayloadProcessor<DeathItemsRequestStruct, OpcodeIn> {
	public static final String CAPABILITY = "death-items/1";
	private static final int RESPONSE_OPCODE = 168;

	@Override
	public void process(DeathItemsRequestStruct payload, Player player) {
		if (player == null || !player.isLoggedIn() || !player.isUsingCustomClient()
			|| !player.getConfig().ITEMS_ON_DEATH_MENU || payload.requestId <= 0) return;

		PacketBuilder response = new PacketBuilder(RESPONSE_OPCODE).writeInt(payload.requestId);
		if (player.getDuel().isDuelActive()) {
			player.write(response.writeByte(1).writeByte(0).writeByte(0).toPacket());
			return;
		}
		Inventory.DeathItems preview = player.getCarriedItems().getInventory().previewOnDeath();
		response.writeByte(0).writeByte(preview.keptCount).writeByte(preview.items.size());
		for (Item item : preview.items) {
			response.writeShort(item.getCatalogId()).writeInt(item.getAmount()).writeByte(item.getNoted() ? 1 : 0);
			response.writeInt(Math.max(0, item.getDef(player.getWorld()).getDefaultPrice()));
		}
		player.write(response.toPacket());
	}
}
