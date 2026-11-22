package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;
import java.util.ArrayList;
import java.util.List;

/** Complete presentation-only scenery for one cell of a private terrain prediction. */
public final class PredictedSceneryCellStruct extends AbstractStruct<OpcodeOut> {
	public int terrainStageSequence;
	public final List<SceneBaselineStruct.ObjectRecord> records = new ArrayList<>();
}
