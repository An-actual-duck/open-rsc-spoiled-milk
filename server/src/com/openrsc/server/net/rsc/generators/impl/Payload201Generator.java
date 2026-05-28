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
 * RSC Protocol-201 Generator for Outgoing Packets from respective Protocol Independent Structs
 *
 * Uses different opcodes to 202+, and is the final version of the protocol
 * without the anti-bot "banking trap". This was the last protocol used by
 * free-to-play servers, and many bots were written for it thanks to a
 * popular deobufuscated version released by saevion.
 *
 * mudclient201.jar was released on 2004-12-13 and was in active use for 9 months.
 * **/
public class Payload201Generator implements PayloadGenerator<OpcodeOut> {
	private static final Map<OpcodeOut, Integer> opcodes = new HashMap<OpcodeOut, Integer>() {{
		put(OpcodeOut.SEND_SERVER_MESSAGE, 168); // done
		put(OpcodeOut.SEND_CANT_LOGOUT, 67); // done
		put(OpcodeOut.SEND_LOGOUT_REQUEST_CONFIRM, 125); // done
		put(OpcodeOut.SEND_FRIEND_LIST, 54); // done
		put(OpcodeOut.SEND_FRIEND_UPDATE, 248); // done
		put(OpcodeOut.SEND_PRIVACY_SETTINGS, 137); // done
		put(OpcodeOut.SEND_IGNORE_LIST, 154); // done
		put(OpcodeOut.SEND_PRIVATE_MESSAGE, 230); // done
		put(OpcodeOut.SEND_PLAYER_COORDS, 141); // done
		put(OpcodeOut.SEND_GROUND_ITEM_HANDLER, 172); // done
		put(OpcodeOut.SEND_SCENERY_HANDLER, 158); // done
		put(OpcodeOut.SEND_INVENTORY, 37); // done
		put(OpcodeOut.SEND_UPDATE_PLAYERS, 183); // done
		put(OpcodeOut.SEND_BOUNDARY_HANDLER, 29); // done
		put(OpcodeOut.SEND_NPC_COORDS, 41); // done
		put(OpcodeOut.SEND_UPDATE_NPC, 47); // done
		put(OpcodeOut.SEND_OPTIONS_MENU_OPEN, 117); // done
		put(OpcodeOut.SEND_OPTIONS_MENU_CLOSE, 208); // done
		put(OpcodeOut.SEND_WORLD_INFO, 249); // done
		put(OpcodeOut.SEND_STATS, 16); // done
		put(OpcodeOut.SEND_EQUIPMENT_STATS, 33); // done
		put(OpcodeOut.SEND_DEATH, 109); // done
		put(OpcodeOut.SEND_REMOVE_WORLD_ENTITY, 76); // done
		put(OpcodeOut.SEND_APPEARANCE_SCREEN, 7); // done
		put(OpcodeOut.SEND_TRADE_WINDOW, 108); // done
		put(OpcodeOut.SEND_TRADE_CLOSE, 113); // done
		put(OpcodeOut.SEND_TRADE_OTHER_ITEMS, 155); // done
		put(OpcodeOut.SEND_TRADE_OTHER_ACCEPTED, 185); // done
		put(OpcodeOut.SEND_SHOP_OPEN, 24); // done
		put(OpcodeOut.SEND_SHOP_CLOSE, 42); // done
		put(OpcodeOut.SEND_TRADE_ACCEPTED, 170); // done
		put(OpcodeOut.SEND_GAME_SETTINGS, 161); // done
		put(OpcodeOut.SEND_PRAYERS_ACTIVE, 222); // done
		put(OpcodeOut.SEND_QUESTS, 26); // done
		put(OpcodeOut.SEND_BANK_OPEN, 188); // done
		put(OpcodeOut.SEND_BANK_CLOSE, 130); // done
		put(OpcodeOut.SEND_EXPERIENCE, 69); // done
		put(OpcodeOut.SEND_DUEL_WINDOW, 240); // done
		put(OpcodeOut.SEND_DUEL_CLOSE, 198); // done
		put(OpcodeOut.SEND_TRADE_OPEN_CONFIRM, 128); // done
		put(OpcodeOut.SEND_DUEL_OPPONENTS_ITEMS, 229); // done
		put(OpcodeOut.SEND_DUEL_SETTINGS, 211); // done
		put(OpcodeOut.SEND_BANK_UPDATE, 81); // done
		put(OpcodeOut.SEND_INVENTORY_UPDATEITEM, 210); // done
		put(OpcodeOut.SEND_INVENTORY_REMOVE_ITEM, 44); // done
		put(OpcodeOut.SEND_STAT, 23); // done
		put(OpcodeOut.SEND_DUEL_OTHER_ACCEPTED, 73); // done
		put(OpcodeOut.SEND_DUEL_ACCEPTED, 131); // done
		put(OpcodeOut.SEND_DUEL_CONFIRMWINDOW, 10); // done
		put(OpcodeOut.SEND_PLAY_SOUND, 238); // done
		put(OpcodeOut.SEND_BUBBLE, 253); // done
		put(OpcodeOut.SEND_WELCOME_INFO, 126); // done
		put(OpcodeOut.SEND_BOX2, 112); // done
		put(OpcodeOut.SEND_BOX, 49); // done
		put(OpcodeOut.SEND_FATIGUE, 60); // done
		put(OpcodeOut.SEND_SLEEPSCREEN, 15); // done
		put(OpcodeOut.SEND_SLEEP_FATIGUE, 174); // done
		put(OpcodeOut.SEND_STOPSLEEP, 206); // done
		put(OpcodeOut.SEND_SLEEPWORD_INCORRECT, 20); // done
		put(OpcodeOut.SEND_SYSTEM_UPDATE, 28); // done
	}};
	private final Payload202Generator gen;

	public Payload201Generator() {
		gen = new Payload202Generator(opcodes);
	}

	@Override
	public PacketBuilder fromOpcodeEnum(OpcodeOut opcode, Player player) {
		return gen.fromOpcodeEnum(opcode, player);
	}

	@Override
	public Packet generate(AbstractStruct<OpcodeOut> payload, Player player) {
		return gen.generate(payload, player);
	}
}
