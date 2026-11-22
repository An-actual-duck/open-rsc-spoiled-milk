package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

import java.util.List;

/** One ordered page of a viewer's authoritative surface scenery cell. */
public final class SurfaceSceneryCellStruct extends AbstractStruct<OpcodeOut> {
	public static final int VERSION = 1;
	public static final int PAGE_SIZE = 256;
	public int generation, context, terrainStage, cellX, cellY, revision, page, pages;
	public List<SceneBaselineStruct.ObjectRecord> records;
}
