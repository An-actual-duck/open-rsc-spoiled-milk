package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.DialoguePresentation;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.NoPayloadStruct;

public final class DialoguePresentationReady implements PayloadProcessor<NoPayloadStruct, OpcodeIn> {
	public void process(NoPayloadStruct payload, Player player) {
		if (DialoguePresentation.enabled() && player.isUsingCustomClient()) {
			player.setDialoguePresentationReady();
		}
	}
}
