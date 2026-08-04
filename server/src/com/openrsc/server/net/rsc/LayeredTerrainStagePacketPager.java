package com.openrsc.server.net.rsc;

import com.openrsc.server.net.Packet;
import com.openrsc.server.net.PacketBuilder;
import com.openrsc.server.net.PacketFrameLengthGuard;
import com.openrsc.server.net.rsc.LayeredTerrainStagePaging.Page;
import com.openrsc.server.net.rsc.struct.outgoing.LayeredTerrainStageStruct;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Converts an oversized opcode-154 stage into independently frame-safe pages. */
public final class LayeredTerrainStagePacketPager {
	private LayeredTerrainStagePacketPager() {
	}

	public static List<Packet> pageIfRequired(
			final Packet packet,
			final LayeredTerrainStageStruct stage) {
		if (packet == null || stage == null) {
			throw new IllegalArgumentException(
				"Layered terrain packet and stage are required");
		}
		final ByteBuf source = packet.getBuffer().duplicate();
		final byte[] serialized = new byte[source.readableBytes()];
		source.readBytes(serialized);
		if (serialized.length == 0
			|| (serialized[0] & 0xFF) != stage.protocolVersion) {
			throw new IllegalArgumentException(
				"Layered terrain packet identity is invalid");
		}
		if (!LayeredTerrainStagePaging.requiresPaging(serialized.length)) {
			return Collections.singletonList(packet);
		}

		final List<Page> pageData = LayeredTerrainStagePaging.split(
			serialized, stage.sequence, stage.contextSequence);
		final List<Packet> packets = new ArrayList<Packet>(pageData.size());
		for (Page page : pageData) {
			final byte[] wirePayload = page.toWirePayload();
			final PacketBuilder builder = new PacketBuilder()
				.setID(packet.getID())
				.write(wirePayload);
			final Packet paged = builder.toPacket();
			PacketFrameLengthGuard.requireSimplifiedPayloadLength(
				paged.getReadableBytes());
			PacketFrameLengthGuard.requireAuthenticPacketLength(
				Math.addExact(paged.getReadableBytes(), 1));
			packets.add(paged);
		}
		return Collections.unmodifiableList(packets);
	}
}
