package com.openrsc.server.net.rsc.struct.outgoing;

/** One explicit slot in a native layered scene-context readiness window. */
public final class LayeredSceneTerrainChunkStruct {
	public int chunkX;
	public int chunkY;
	public boolean available;
	public int sourceSectorX;
	public int sourceSectorY;
	public String sourceEncoding;
	public String sourcePayloadSha256;
	public byte[] tileBytes;
}
