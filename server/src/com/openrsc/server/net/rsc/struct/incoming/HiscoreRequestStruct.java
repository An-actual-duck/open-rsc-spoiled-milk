package com.openrsc.server.net.rsc.struct.incoming;

import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

public class HiscoreRequestStruct extends AbstractStruct<OpcodeIn> {

	// Skill id to rank by, or 255 for the overall (total level) hiscores
	public int skillId;

}
