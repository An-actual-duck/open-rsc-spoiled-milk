package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

import java.util.ArrayList;
import java.util.List;

public class SceneBaselineStruct extends AbstractStruct<OpcodeOut> {
	public int protocolVersion;
	public int serverTick;
	public int localX;
	public int localY;
	public int locationContextSequence;
	public int players;
	public int npcs;
	public int scenery;
	public int walls;
	public int groundItems;
	public int objectViewDistance;
	public int playersHash;
	public int npcsHash;
	public int sceneryHash;
	public int wallsHash;
	public int groundItemsHash;
	public int presentationCenterSectorX;
	public int presentationCenterSectorY;
	public int presentationOuterRadius;
	public int presentationInnerRadius;
	public int presentationScenery;
	public int presentationWalls;
	public int presentationSceneryHash;
	public int presentationWallsHash;
	public int pageCategory;
	public int pageIndex;
	public int pageTotal;
	public List<ObjectRecord> objectRecords = new ArrayList<>();

	public static class ObjectRecord {
		public final int id;
		public final int x;
		public final int y;
		public final int direction;
		public final int type;

		public ObjectRecord(final int id, final int x, final int y, final int direction, final int type) {
			this.id = id;
			this.x = x;
			this.y = y;
			this.direction = direction;
			this.type = type;
		}
	}
}
