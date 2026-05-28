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
 * RSC Protocol-199 Generator for Outgoing Packets from respective Protocol Independent Structs
 *
 * mudclient199.jar was released on 2004-10-28 and was in active use for 2 months.
 *
 * Contemporary open source clients for 199 include TBoT by RichyT.
 * **/
public class Payload199Generator implements PayloadGenerator<OpcodeOut> {
	private static final Map<OpcodeOut, Integer> opcodes = new HashMap<OpcodeOut, Integer>() {{
		put(OpcodeOut.SEND_SERVER_MESSAGE, 3);
		put(OpcodeOut.SEND_LOGOUT_REQUEST_CONFIRM, 208);
		put(OpcodeOut.SEND_CANT_LOGOUT, 102);
		put(OpcodeOut.SEND_FRIEND_LIST, 45);
		put(OpcodeOut.SEND_FRIEND_UPDATE, 18);
		put(OpcodeOut.SEND_PRIVACY_SETTINGS, 72);
		put(OpcodeOut.SEND_IGNORE_LIST, 53);
		put(OpcodeOut.SEND_PRIVATE_MESSAGE, 249);
		put(OpcodeOut.SEND_PLAYER_COORDS, 116);
		put(OpcodeOut.SEND_GROUND_ITEM_HANDLER, 219);
		put(OpcodeOut.SEND_SCENERY_HANDLER, 71);
		put(OpcodeOut.SEND_INVENTORY, 178);
		put(OpcodeOut.SEND_UPDATE_PLAYERS, 65);
		put(OpcodeOut.SEND_BOUNDARY_HANDLER, 96);
		put(OpcodeOut.SEND_NPC_COORDS, 183);
		put(OpcodeOut.SEND_UPDATE_NPC, 206);
		put(OpcodeOut.SEND_OPTIONS_MENU_OPEN, 153);
		put(OpcodeOut.SEND_OPTIONS_MENU_CLOSE, 91);
		put(OpcodeOut.SEND_WORLD_INFO, 133);
		put(OpcodeOut.SEND_STATS, 32);
		put(OpcodeOut.SEND_EQUIPMENT_STATS, 145);
		put(OpcodeOut.SEND_DEATH, 144);
		put(OpcodeOut.SEND_REMOVE_WORLD_ENTITY, 31);
		put(OpcodeOut.SEND_APPEARANCE_SCREEN, 123);
		put(OpcodeOut.SEND_TRADE_WINDOW, 186);
		put(OpcodeOut.SEND_TRADE_CLOSE, 207);
		put(OpcodeOut.SEND_TRADE_OTHER_ITEMS, 231);
		put(OpcodeOut.SEND_TRADE_OTHER_ACCEPTED, 60);
		put(OpcodeOut.SEND_SHOP_OPEN, 113);
		put(OpcodeOut.SEND_SHOP_CLOSE, 135);
		put(OpcodeOut.SEND_TRADE_ACCEPTED, 148);
		put(OpcodeOut.SEND_GAME_SETTINGS, 101);
		put(OpcodeOut.SEND_PRAYERS_ACTIVE, 204);
		put(OpcodeOut.SEND_QUESTS, 170);
		put(OpcodeOut.SEND_BANK_OPEN, 47);
		put(OpcodeOut.SEND_BANK_CLOSE, 254);
		put(OpcodeOut.SEND_EXPERIENCE, 255);
		put(OpcodeOut.SEND_DUEL_WINDOW, 185);
		put(OpcodeOut.SEND_DUEL_CLOSE, 42);
		put(OpcodeOut.SEND_TRADE_OPEN_CONFIRM, 37);
		put(OpcodeOut.SEND_DUEL_OPPONENTS_ITEMS, 33);
		put(OpcodeOut.SEND_DUEL_SETTINGS, 122);
		put(OpcodeOut.SEND_BANK_UPDATE, 139);
		put(OpcodeOut.SEND_INVENTORY_UPDATEITEM, 156);
		put(OpcodeOut.SEND_INVENTORY_REMOVE_ITEM, 212);
		put(OpcodeOut.SEND_STAT, 244);
		put(OpcodeOut.SEND_DUEL_OTHER_ACCEPTED, 202);
		put(OpcodeOut.SEND_DUEL_ACCEPTED, 140);
		put(OpcodeOut.SEND_DUEL_CONFIRMWINDOW, 80);
		put(OpcodeOut.SEND_PLAY_SOUND, 6);
		put(OpcodeOut.SEND_BUBBLE, 92);
		put(OpcodeOut.SEND_WELCOME_INFO, 43);
		put(OpcodeOut.SEND_BOX2, 127);
		put(OpcodeOut.SEND_BOX, 177);
		put(OpcodeOut.SEND_FATIGUE, 201);
		put(OpcodeOut.SEND_SLEEPSCREEN, 224);
		put(OpcodeOut.SEND_SLEEP_FATIGUE, 162);
		put(OpcodeOut.SEND_STOPSLEEP, 63);
		put(OpcodeOut.SEND_SLEEPWORD_INCORRECT, 79);
		put(OpcodeOut.SEND_SYSTEM_UPDATE, 246);
	}};
	private final Payload202Generator gen;

	public Payload199Generator() {
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
