package com.openrsc.server.net.rsc.struct.incoming;

import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

/**
 * Exact client acknowledgement for an installed native terrain generation.
 */
public final class LayeredTerrainReadyStruct extends AbstractStruct<OpcodeIn> {
	public int protocolVersion;
	public int contextSequence;
	public String worldSpace;
	public int logicalLevel;
	public int centerSectorX;
	public int centerSectorY;
	public String manifestSha256;
}
