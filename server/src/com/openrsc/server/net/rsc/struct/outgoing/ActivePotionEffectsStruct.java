package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

public final class ActivePotionEffectsStruct extends AbstractStruct<OpcodeOut> {
	public int count;
	public int totalCount;
	public int[] itemIds = new int[0];
	public int[] remainingSeconds = new int[0];
}
