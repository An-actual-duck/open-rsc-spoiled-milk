package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Cache-only native terrain window predicted from authoritative queued
 * movement. Receiving this envelope must not change the active scene scope.
 */
public final class LayeredTerrainStageStruct extends AbstractStruct<OpcodeOut> {
	public int protocolVersion;
	public int sequence;
	public int contextSequence;
	public int serverTick;
	public String worldSpace;
	public int logicalLevel;
	public String nativePackageId;
	public String nativePackageVersion;
	public String nativeManifestSha256;
	public int nativePresentationChunkSize;
	public int nativeCurrentChunkX;
	public int nativeCurrentChunkY;
	public int nativeChunkRadius;
	public final List<LayeredSceneTerrainChunkStruct> nativeChunks =
		new ArrayList<LayeredSceneTerrainChunkStruct>();
}
