package com.openrsc.server.net.rsc.struct.outgoing;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;
public final class PlayerMovementRateStruct extends AbstractStruct<OpcodeOut> {
    public int serverIndex, percent;
}
