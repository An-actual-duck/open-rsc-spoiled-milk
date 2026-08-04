package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.content.cleric.ClericSpellDefinition;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

public final class ClericSpellbookStruct extends AbstractStruct<OpcodeOut> {
	public int schemaVersion;
	public int gameTickMilliseconds;
	public ClericSpellDefinition[] definitions = new ClericSpellDefinition[0];
}
