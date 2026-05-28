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
 * RSC Protocol-196 Generator for Outgoing Packets from respective Protocol Independent Structs
 *
 * mudclient196.jar was released on 2004-06-28 and was in active use for 2 months.
 *
 * It was the first client to support up to 200 friends and the first to use a
 * username hash to select a login server.
 * **/
public class Payload196Generator implements PayloadGenerator<OpcodeOut> {
	private static final Map<OpcodeOut, Integer> opcodes = new HashMap<OpcodeOut, Integer>() {{
		put(OpcodeOut.SEND_SERVER_MESSAGE, 53);
		put(OpcodeOut.SEND_LOGOUT_REQUEST_CONFIRM, 183);
		put(OpcodeOut.SEND_CANT_LOGOUT, 36);
		put(OpcodeOut.SEND_FRIEND_LIST, 30);
		put(OpcodeOut.SEND_FRIEND_UPDATE, 108);
		put(OpcodeOut.SEND_IGNORE_LIST, 84);
		put(OpcodeOut.SEND_PRIVACY_SETTINGS, 50);
		put(OpcodeOut.SEND_PRIVATE_MESSAGE, 29);
		put(OpcodeOut.SEND_PLAYER_COORDS, 196);
		put(OpcodeOut.SEND_GROUND_ITEM_HANDLER, 180);
		put(OpcodeOut.SEND_SCENERY_HANDLER, 120);
		put(OpcodeOut.SEND_INVENTORY, 232);
		put(OpcodeOut.SEND_UPDATE_PLAYERS, 240);
		put(OpcodeOut.SEND_BOUNDARY_HANDLER, 94);
		put(OpcodeOut.SEND_NPC_COORDS, 135);
		put(OpcodeOut.SEND_UPDATE_NPC, 215);
		put(OpcodeOut.SEND_OPTIONS_MENU_OPEN, 80);
		put(OpcodeOut.SEND_OPTIONS_MENU_CLOSE, 125);
		put(OpcodeOut.SEND_WORLD_INFO, 79);
		put(OpcodeOut.SEND_STATS, 184);
		put(OpcodeOut.SEND_EQUIPMENT_STATS, 207);
		put(OpcodeOut.SEND_DEATH, 73);
		put(OpcodeOut.SEND_REMOVE_WORLD_ENTITY, 74);
		put(OpcodeOut.SEND_APPEARANCE_SCREEN, 255);
		put(OpcodeOut.SEND_TRADE_WINDOW, 119);
		put(OpcodeOut.SEND_TRADE_CLOSE, 161);
		put(OpcodeOut.SEND_TRADE_OTHER_ITEMS, 197);
		put(OpcodeOut.SEND_SHOP_OPEN, 24);
		put(OpcodeOut.SEND_SHOP_CLOSE, 115);
		put(OpcodeOut.SEND_TRADE_ACCEPTED, 21);
		put(OpcodeOut.SEND_GAME_SETTINGS, 51);
		put(OpcodeOut.SEND_PRAYERS_ACTIVE, 133);
		put(OpcodeOut.SEND_QUESTS, 253);
		put(OpcodeOut.SEND_BANK_OPEN, 6);
		put(OpcodeOut.SEND_BANK_CLOSE, 75);
		put(OpcodeOut.SEND_EXPERIENCE, 158);
		put(OpcodeOut.SEND_DUEL_WINDOW, 209);
		put(OpcodeOut.SEND_DUEL_CLOSE, 64);
		put(OpcodeOut.SEND_TRADE_OPEN_CONFIRM, 185);
		put(OpcodeOut.SEND_DUEL_OPPONENTS_ITEMS, 160);
		put(OpcodeOut.SEND_DUEL_SETTINGS, 228);
		put(OpcodeOut.SEND_BANK_UPDATE, 175);
		put(OpcodeOut.SEND_INVENTORY_UPDATEITEM, 28);
		put(OpcodeOut.SEND_INVENTORY_REMOVE_ITEM, 122);
		put(OpcodeOut.SEND_STAT, 128);
		put(OpcodeOut.SEND_DUEL_OTHER_ACCEPTED, 54);
		put(OpcodeOut.SEND_DUEL_ACCEPTED, 229);
		put(OpcodeOut.SEND_DUEL_CONFIRMWINDOW, 44);
		put(OpcodeOut.SEND_PLAY_SOUND, 123);
		put(OpcodeOut.SEND_BUBBLE, 142);
		put(OpcodeOut.SEND_WELCOME_INFO, 114);
		put(OpcodeOut.SEND_BOX2, 37);
		put(OpcodeOut.SEND_BOX, 152);
		put(OpcodeOut.SEND_FATIGUE, 82);
		put(OpcodeOut.SEND_SLEEPSCREEN, 155);
		put(OpcodeOut.SEND_SLEEP_FATIGUE, 32);
		put(OpcodeOut.SEND_STOPSLEEP, 138);
		put(OpcodeOut.SEND_SLEEPWORD_INCORRECT, 116);
		put(OpcodeOut.SEND_SYSTEM_UPDATE, 7);
	}};
	private final Payload202Generator gen;

	public Payload196Generator() {
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
