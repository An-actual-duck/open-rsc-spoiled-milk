package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

public class OnlineListStruct extends AbstractStruct<OpcodeOut> {

	public int numberOnline;
	public int playerCount;
	public boolean sidebar;
	public String[] name;
	public int[] icon;
	public String[] location;
}
