package com.openrsc.server.net;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ISAACContainer;

import java.util.concurrent.atomic.AtomicReference;

public class ConnectionAttachment {

	public AtomicReference<Player> player = new AtomicReference<Player>();

    public final AtomicReference<com.openrsc.server.login.StagedLoginSession> stagedLogin=new AtomicReference<com.openrsc.server.login.StagedLoginSession>();
    public final java.util.concurrent.atomic.AtomicBoolean stagedLoginAdvertised=new java.util.concurrent.atomic.AtomicBoolean();

	public AtomicReference<ISAACContainer> ISAAC = new AtomicReference<ISAACContainer>();

	public AtomicReference<Short> authenticClient = new AtomicReference<Short>();

	public AtomicReference<PcapLogger> pcapLogger = new AtomicReference<PcapLogger>();

	public AtomicReference<Integer> sessionId = new AtomicReference<Integer>();
	public AtomicReference<Boolean> canSendSessionId = new AtomicReference<Boolean>();
	public AtomicReference<Boolean> isLongSessionId = new AtomicReference<Boolean>();
	public AtomicReference<Boolean> isWebSocket = new AtomicReference<Boolean>(false);

}
