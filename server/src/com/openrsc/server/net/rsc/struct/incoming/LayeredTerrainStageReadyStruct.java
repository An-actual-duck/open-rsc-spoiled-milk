package com.openrsc.server.net.rsc.struct.incoming;

import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

/** Exact client acknowledgement for one cache-only terrain stage. */
public final class LayeredTerrainStageReadyStruct
	extends AbstractStruct<OpcodeIn> {

	public int protocolVersion;
	public int stageSequence;
	public int contextSequence;
	public String worldSpace;
	public int logicalLevel;
	public int centerSectorX;
	public int centerSectorY;
	public String manifestSha256;
}
