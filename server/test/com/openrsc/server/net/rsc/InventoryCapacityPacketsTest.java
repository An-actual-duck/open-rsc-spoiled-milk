package com.openrsc.server.net.rsc;

import com.openrsc.server.net.Packet;

/** Executable wire guard for the custom 30..40 inventory capacity receipt. */
public final class InventoryCapacityPacketsTest {
	private InventoryCapacityPacketsTest() { }

	public static void main(String[] arguments) {
		assertPacket(31);
		assertPacket(40);
		System.out.println("PASS: inventory capacity receipts retain opcode 160 and one-byte capacity");
	}

	private static void assertPacket(int capacity) {
		Packet packet = InventoryCapacityPackets.build(capacity);
		if (packet.getID() != 160) {
			throw new AssertionError("inventory capacity opcode: " + packet.getID());
		}
		if (packet.getLength() != 1) {
			throw new AssertionError("inventory capacity payload length: " + packet.getLength());
		}
		if (packet.readUnsignedByte() != capacity) {
			throw new AssertionError("inventory capacity payload did not preserve " + capacity);
		}
	}
}
