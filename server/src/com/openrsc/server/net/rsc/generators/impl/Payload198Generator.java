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
 * RSC Protocol-198 Generator for Outgoing Packets from respective Protocol Independent Structs
 *
 * mudclient198.jar was released on 2004-08-24 and was in active use for 2 months.
 *
 * Contemporary open source clients for 198 include TheLoveMachine by SeanWT and saevion.
 * **/
public class Payload198Generator implements PayloadGenerator<OpcodeOut> {
	private static final Map<OpcodeOut, Integer> opcodes = new HashMap<OpcodeOut, Integer>() {{
		put(OpcodeOut.SEND_SERVER_MESSAGE, 202);
		put(OpcodeOut.SEND_LOGOUT_REQUEST_CONFIRM, 178);
		put(OpcodeOut.SEND_CANT_LOGOUT, 11);
		put(OpcodeOut.SEND_FRIEND_LIST, 174);
		put(OpcodeOut.SEND_FRIEND_UPDATE, 101);
		put(OpcodeOut.SEND_IGNORE_LIST, 144);
		put(OpcodeOut.SEND_PRIVACY_SETTINGS, 148);
		put(OpcodeOut.SEND_PRIVATE_MESSAGE, 193);
		put(OpcodeOut.SEND_PLAYER_COORDS, 18);
		put(OpcodeOut.SEND_GROUND_ITEM_HANDLER, 181);
		put(OpcodeOut.SEND_SCENERY_HANDLER, 238);
		put(OpcodeOut.SEND_INVENTORY, 171);
		put(OpcodeOut.SEND_UPDATE_PLAYERS, 139);
		put(OpcodeOut.SEND_BOUNDARY_HANDLER, 29);
		put(OpcodeOut.SEND_NPC_COORDS, 152);
		put(OpcodeOut.SEND_UPDATE_NPC, 159);
		put(OpcodeOut.SEND_OPTIONS_MENU_OPEN, 55);
		put(OpcodeOut.SEND_OPTIONS_MENU_CLOSE, 93);
		put(OpcodeOut.SEND_WORLD_INFO, 167);
		put(OpcodeOut.SEND_STATS, 205);
		put(OpcodeOut.SEND_EQUIPMENT_STATS, 79);
		put(OpcodeOut.SEND_DEATH, 72);
		put(OpcodeOut.SEND_REMOVE_WORLD_ENTITY, 246);
		put(OpcodeOut.SEND_APPEARANCE_SCREEN, 237);
		put(OpcodeOut.SEND_TRADE_WINDOW, 240);
		put(OpcodeOut.SEND_TRADE_CLOSE, 3);
		put(OpcodeOut.SEND_TRADE_OTHER_ITEMS, 245);
		put(OpcodeOut.SEND_TRADE_OTHER_ACCEPTED, 207);
		put(OpcodeOut.SEND_SHOP_OPEN, 153);
		put(OpcodeOut.SEND_SHOP_CLOSE, 243);
		put(OpcodeOut.SEND_TRADE_ACCEPTED, 118);
		put(OpcodeOut.SEND_GAME_SETTINGS, 105);
		put(OpcodeOut.SEND_PRAYERS_ACTIVE, 140);
		put(OpcodeOut.SEND_QUESTS, 164);
		put(OpcodeOut.SEND_BANK_OPEN, 120);
		put(OpcodeOut.SEND_BANK_CLOSE, 226);
		put(OpcodeOut.SEND_EXPERIENCE, 108);
		put(OpcodeOut.SEND_DUEL_WINDOW, 23);
		put(OpcodeOut.SEND_DUEL_CLOSE, 10);
		put(OpcodeOut.SEND_TRADE_OPEN_CONFIRM, 13);
		put(OpcodeOut.SEND_DUEL_OPPONENTS_ITEMS, 75);
		put(OpcodeOut.SEND_DUEL_SETTINGS, 119);
		put(OpcodeOut.SEND_BANK_UPDATE, 172);
		put(OpcodeOut.SEND_INVENTORY_UPDATEITEM, 242);
		put(OpcodeOut.SEND_INVENTORY_REMOVE_ITEM, 104);
		put(OpcodeOut.SEND_STAT, 99);
		put(OpcodeOut.SEND_DUEL_OTHER_ACCEPTED, 210);
		put(OpcodeOut.SEND_DUEL_ACCEPTED, 235);
		put(OpcodeOut.SEND_DUEL_CONFIRMWINDOW, 255);
		put(OpcodeOut.SEND_PLAY_SOUND, 250);
		put(OpcodeOut.SEND_BUBBLE, 252);
		put(OpcodeOut.SEND_WELCOME_INFO, 176);
		put(OpcodeOut.SEND_BOX2, 146);
		put(OpcodeOut.SEND_BOX, 138);
		put(OpcodeOut.SEND_FATIGUE, 84);
		put(OpcodeOut.SEND_SLEEPSCREEN, 51);
		put(OpcodeOut.SEND_SLEEP_FATIGUE, 21);
		put(OpcodeOut.SEND_STOPSLEEP, 125);
		put(OpcodeOut.SEND_SLEEPWORD_INCORRECT, 127);
		put(OpcodeOut.SEND_SYSTEM_UPDATE, 113);
	}};
	private final Payload202Generator gen;

	public Payload198Generator() {
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
