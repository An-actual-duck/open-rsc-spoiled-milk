package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Versioned custom-client spatial scope for the unchanged legacy scene packet
 * group that follows it.
 */
public final class LayeredSceneContextStruct extends AbstractStruct<OpcodeOut> {
	public int protocolVersion;
	public int sequence;
	public int serverTick;
	public String worldSpace;
	public String projectionId;
	public int logicalX;
	public int logicalY;
	public int logicalLevel;
	public int legacyX;
	public int legacyY;
	public String nativePackageId;
	public String nativePackageVersion;
	public String nativeManifestSha256;
	public int nativePresentationChunkSize;
	public int nativeSectorX;
	public int nativeSectorY;
	public String nativeEncoding;
	public String nativePayloadSha256;
	public int nativeElevation;
	public int nativeTexture;
	public int nativeOverlay;
	public int nativeRoof;
	public int nativeVerticalWall;
	public int nativeHorizontalWall;
	public int nativeDiagonalWall;
	public int nativeCurrentChunkX;
	public int nativeCurrentChunkY;
	public int nativeChunkRadius;
	public final List<LayeredSceneTerrainChunkStruct> nativeChunks =
		new ArrayList<LayeredSceneTerrainChunkStruct>();
}
