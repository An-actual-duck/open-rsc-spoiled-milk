package com.openrsc.server.net.rsc;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.PacketBuilder;

/**
 * Sends the custom client's negotiated inventory-capacity receipt without
 * depending on a particular World Builder runtime snapshot's opcode enum.
 */
public final class InventoryCapacityPackets {
	private static final int FIRST_SUPPORTED_CLIENT_VERSION = 10052;
	private static final int CUSTOM_CLIENT_VERSION_LIMIT = 20000;
	private static final int INVENTORY_CAPACITY_OPCODE = 160;

	private InventoryCapacityPackets() { }

	public static boolean isSupported(Player player) {
		if (player == null) return false;
		int version = player.getClientVersion();
		return version >= FIRST_SUPPORTED_CLIENT_VERSION
			&& version < CUSTOM_CLIENT_VERSION_LIMIT;
	}

	public static boolean send(Player player) {
		if (!isSupported(player)) return false;
		int capacity = player.getCarriedItems().getInventory().getCapacity();
		player.write(build(capacity));
		return true;
	}

	static Packet build(int capacity) {
		return new PacketBuilder(INVENTORY_CAPACITY_OPCODE)
			.writeByte(capacity)
			.toPacket();
	}
}
