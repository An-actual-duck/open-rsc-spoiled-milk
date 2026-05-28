package com.openrsc.server.net.rsc.generators.impl;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.external.GameObjectLoc;
import com.openrsc.server.external.ItemLoc;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.RSCString;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.PacketBuilder;
import com.openrsc.server.net.rsc.GameNetworkException;
import com.openrsc.server.net.rsc.PayloadValidator;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.generators.PayloadGenerator;
import com.openrsc.server.net.rsc.struct.AbstractStruct;
import com.openrsc.server.net.rsc.struct.outgoing.*;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.MathUtil;
import com.openrsc.server.util.rsc.StringUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * RSC Protocol-202 Generator for Outgoing Packets from respective Protocol Independent Structs
 *
 * This was the original protocol version used by RSCDaemon.
 *
 * https://github.com/moparisthebest/rswiki-book/blob/master/src/202-Protocol.md
 *
 * mudclient202.jar was released on 2005-08-03 and was in active use for 3 months.
 * **/
public class Payload202Generator implements PayloadGenerator<OpcodeOut> {
	private static final Map<OpcodeOut, Integer> opcodes202 = new HashMap<OpcodeOut, Integer>() {{
		put(OpcodeOut.SEND_LOGOUT_REQUEST_CONFIRM, 222); // done
		put(OpcodeOut.SEND_QUESTS, 224); // done
		put(OpcodeOut.SEND_DUEL_OPPONENTS_ITEMS, 63); // done
		put(OpcodeOut.SEND_TRADE_ACCEPTED, 18); // done
		put(OpcodeOut.SEND_TRADE_OPEN_CONFIRM, 251); // done
		put(OpcodeOut.SEND_WORLD_INFO, 131); // done
		put(OpcodeOut.SEND_DUEL_SETTINGS, 198); // done
		put(OpcodeOut.SEND_EXPERIENCE, 211); // done
		put(OpcodeOut.SEND_BUBBLE, 23); // done
		put(OpcodeOut.SEND_BANK_OPEN, 93); // done
		put(OpcodeOut.SEND_SCENERY_HANDLER, 27); // done
		put(OpcodeOut.SEND_PRIVACY_SETTINGS, 158); // done
		put(OpcodeOut.SEND_SYSTEM_UPDATE, 72); // done
		put(OpcodeOut.SEND_INVENTORY, 114); // done
		put(OpcodeOut.SEND_APPEARANCE_SCREEN, 207); // done
		put(OpcodeOut.SEND_NPC_COORDS, 77); // done
		put(OpcodeOut.SEND_DEATH, 165); // done
		put(OpcodeOut.SEND_STOPSLEEP, 103); // done
		put(OpcodeOut.SEND_BOX2, 148); // done
		put(OpcodeOut.SEND_INVENTORY_UPDATEITEM, 228); // done
		put(OpcodeOut.SEND_BOUNDARY_HANDLER, 95); // done
		put(OpcodeOut.SEND_TRADE_WINDOW, 4); // done
		put(OpcodeOut.SEND_TRADE_OTHER_ITEMS, 250); // done
		put(OpcodeOut.SEND_GROUND_ITEM_HANDLER, 109); // done
		put(OpcodeOut.SEND_SHOP_OPEN, 253); // done
		put(OpcodeOut.SEND_UPDATE_NPC, 190); // done
		put(OpcodeOut.SEND_IGNORE_LIST, 2); // done
		put(OpcodeOut.SEND_FATIGUE, 126); // done
		put(OpcodeOut.SEND_SLEEPSCREEN, 219); // done
		put(OpcodeOut.SEND_PRIVATE_MESSAGE, 170); // done
		put(OpcodeOut.SEND_INVENTORY_REMOVE_ITEM, 191); // done
		put(OpcodeOut.SEND_TRADE_CLOSE, 187); // done
		put(OpcodeOut.SEND_SERVER_MESSAGE, 48); // done
		put(OpcodeOut.SEND_SHOP_CLOSE, 220); // done
		put(OpcodeOut.SEND_FRIEND_LIST, 249); // done
		put(OpcodeOut.SEND_FRIEND_UPDATE, 25); // done
		put(OpcodeOut.SEND_EQUIPMENT_STATS, 177); // done
		put(OpcodeOut.SEND_STATS, 180); // done
		put(OpcodeOut.SEND_STAT, 208); // done
		put(OpcodeOut.SEND_TRADE_OTHER_ACCEPTED, 92); // done
		put(OpcodeOut.SEND_DUEL_CONFIRMWINDOW, 147); // done
		put(OpcodeOut.SEND_DUEL_WINDOW, 229); // done
		put(OpcodeOut.SEND_WELCOME_INFO, 248); // done
		put(OpcodeOut.SEND_CANT_LOGOUT, 136); // done
		put(OpcodeOut.SEND_PLAYER_COORDS, 145); // done
		put(OpcodeOut.SEND_SLEEPWORD_INCORRECT, 15); // done
		put(OpcodeOut.SEND_BANK_CLOSE, 171); // done
		put(OpcodeOut.SEND_PLAY_SOUND, 11); // done
		put(OpcodeOut.SEND_PRAYERS_ACTIVE, 209); // done
		put(OpcodeOut.SEND_DUEL_ACCEPTED, 197); // done
		put(OpcodeOut.SEND_REMOVE_WORLD_ENTITY, 115); // done
		put(OpcodeOut.SEND_BOX, 64); // done
		put(OpcodeOut.SEND_DUEL_CLOSE, 160); // done
		put(OpcodeOut.SEND_UPDATE_PLAYERS, 53); // done
		put(OpcodeOut.SEND_GAME_SETTINGS, 152); // done
		put(OpcodeOut.SEND_SLEEP_FATIGUE, 168); // done
		put(OpcodeOut.SEND_OPTIONS_MENU_OPEN, 223); // done
		put(OpcodeOut.SEND_BANK_UPDATE, 139); // done
		put(OpcodeOut.SEND_OPTIONS_MENU_CLOSE, 127); // done
		put(OpcodeOut.SEND_DUEL_OTHER_ACCEPTED, 65); // done
	}};
	private final Map<OpcodeOut, Integer> opcodes;
	private final Payload203Generator gen;

	public Payload202Generator(Map<OpcodeOut, Integer> opcodes) {
		this.opcodes = opcodes;
		gen = new Payload203Generator(opcodes);
	}

	public Payload202Generator() {
		this(opcodes202);
	}

	@Override
	public PacketBuilder fromOpcodeEnum(OpcodeOut opcode, Player player) {
		return gen.fromOpcodeEnum(opcode, player);
	}

	@Override
	public Packet generate(AbstractStruct<OpcodeOut> payload, Player player) {
		PacketBuilder builder = fromOpcodeEnum(payload.getOpcode(), player);
		boolean possiblyValid = PayloadValidator.isPayloadCorrectInstance(payload, payload.getOpcode());
		if (builder != null && possiblyValid) {
			switch (payload.getOpcode()) {
				case SEND_FRIEND_LIST:
					FriendListStruct fl = (FriendListStruct) payload;
					int friendSize = fl.listSize;
					builder.writeByte((byte) friendSize);
					for (int i = 0; i < friendSize; i++) {
						builder.writeLong(DataConversions.usernameToHash(fl.name[i]));
						builder.writeByte((byte) fl.worldNumber[i]);
					}
					return builder.toPacket();

				case SEND_FRIEND_UPDATE:
					FriendUpdateStruct fr = (FriendUpdateStruct) payload;
					builder.writeLong(DataConversions.usernameToHash(fr.name));
					builder.writeByte((byte) fr.worldNumber);
					return builder.toPacket();
			}
		}
		return gen.generate(payload, player);
	}
}
