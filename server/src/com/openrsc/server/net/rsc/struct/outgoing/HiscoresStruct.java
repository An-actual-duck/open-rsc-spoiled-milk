package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

public class HiscoresStruct extends AbstractStruct<OpcodeOut> {

	// Skill id the rankings are for, or 255 for the overall (total level) hiscores
	public int skillId;
	// Requesting player's rank among eligible players (1-based)
	public int ownRank;
	// Requesting player's level (or total level for overall)
	public int ownLevel;
	// Requesting player's raw experience (x4 fixed point; total for overall)
	public long ownExperience;
	// Index of the requesting player within the rows below, or 255 if absent
	public int ownListIndex;
	public int count;
	public String[] names;
	public int[] levels;
	public long[] experiences;
}
