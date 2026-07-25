package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

/**
 * Versioned custom-client spatial scope for the unchanged legacy scene packet
 * group that follows it.
 */
public final class LayeredSceneContextStruct extends AbstractStruct<OpcodeOut> {
	public int protocolVersion;
	public int sequence;
	public int serverTick;
	public String worldSpace;
	public int logicalX;
	public int logicalY;
	public int logicalLevel;
	public int legacyX;
	public int legacyY;
}
