package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

import java.util.ArrayList;
import java.util.List;

public class MovementUpdateStruct extends AbstractStruct<OpcodeOut> {
	public int localX;
	public int localY;
	public int localSprite;
	public List<MobMovement> players = new ArrayList<>();
	public List<MobMovement> npcs = new ArrayList<>();

	public static class MobMovement {
		public final int serverIndex;
		public final int x;
		public final int y;
		public final int sprite;

		public MobMovement(int serverIndex, int x, int y, int sprite) {
			this.serverIndex = serverIndex;
			this.x = x;
			this.y = y;
			this.sprite = sprite;
		}
	}
}
