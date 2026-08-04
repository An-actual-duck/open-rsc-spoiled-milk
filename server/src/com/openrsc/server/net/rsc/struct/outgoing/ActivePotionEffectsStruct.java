package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

public final class ActivePotionEffectsStruct extends AbstractStruct<OpcodeOut> {
	public static final int EXTENSION_VERSION = 1;

	public int count;
	public int totalCount;
	public int[] itemIds = new int[0];
	public int[] remainingSeconds = new int[0];
	public int[] identityKinds = new int[0];
	public int[] stableIdentities = new int[0];
	public int[] ranks = new int[0];
	public int[] counterKinds = new int[0];
	public int[] remainingCounters = new int[0];
}
