package com.openrsc.server.net.rsc.parsers.impl;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.Crypto;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.parsers.PayloadParser;
import com.openrsc.server.net.rsc.struct.*;
import com.openrsc.server.net.rsc.struct.incoming.*;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.StringUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * RSC Protocol-198 Parser of Incoming Packets to respective Protocol Independent Structs
 *
 * mudclient198.jar was released on 2004-08-24 and was in active use for 2 months.
 *
 * Open source clients for 198 include TheLoveMachine by SeanWT and saevion.
 * **/
public class Payload198Parser implements PayloadParser<OpcodeIn> {

	private static final Map<Integer, OpcodeIn> opcodes = new HashMap<Integer, OpcodeIn>();
	private final Payload201Parser parser;

	public Payload198Parser() {
		parser = new Payload201Parser(opcodes);
	}

	@Override
	public OpcodeIn toOpcodeEnum(Packet packet, Player player) {
		return opcodes.get(packet.getID());
	}

	@Override
	public AbstractStruct<OpcodeIn> parse(Packet packet, Player player) {
		return parser.parse(packet, player);

	}

	public static final boolean isPossiblyValid(Map<Integer, OpcodeIn> opcodes, int opcode, int length) {
		return Payload201Parser.isPossiblyValid(opcodes, opcode, length);
	}

	public static final boolean isPossiblyValid(int opcode, int length) {
		return isPossiblyValid(opcodes, opcode, length);
	}

	static {
		opcodes.put(76, OpcodeIn.HEARTBEAT);
		opcodes.put(230, OpcodeIn.WALK_TO_ENTITY);
		opcodes.put(21, OpcodeIn.WALK_TO_POINT);
		opcodes.put(190, OpcodeIn.CONFIRM_LOGOUT);
		opcodes.put(168, OpcodeIn.LOGOUT);
		opcodes.put(119, OpcodeIn.COMBAT_STYLE_CHANGED);
		opcodes.put(203, OpcodeIn.QUESTION_DIALOG_ANSWER);
		opcodes.put(16, OpcodeIn.PLAYER_APPEARANCE_CHANGE);
		opcodes.put(241, OpcodeIn.SOCIAL_ADD_IGNORE);
		opcodes.put(204, OpcodeIn.SOCIAL_ADD_FRIEND);
		opcodes.put(225, OpcodeIn.SOCIAL_SEND_PRIVATE_MESSAGE);
		opcodes.put(179, OpcodeIn.SOCIAL_REMOVE_FRIEND);
		opcodes.put(30, OpcodeIn.SOCIAL_REMOVE_IGNORE);
		opcodes.put(154, OpcodeIn.DUEL_OFFER_ITEM);
		opcodes.put(24, OpcodeIn.DUEL_SECOND_ACCEPTED);
		opcodes.put(10, OpcodeIn.DUEL_FIRST_SETTINGS_CHANGED);
		opcodes.put(245, OpcodeIn.DUEL_FIRST_ACCEPTED);
		opcodes.put(82, OpcodeIn.DUEL_DECLINED);
		opcodes.put(157, OpcodeIn.SHOP_BUY);
		opcodes.put(211, OpcodeIn.SHOP_SELL);
		opcodes.put(56, OpcodeIn.SHOP_CLOSE);
		opcodes.put(38, OpcodeIn.PLAYER_ACCEPTED_INIT_TRADE_REQUEST);
		opcodes.put(178, OpcodeIn.PLAYER_DECLINED_TRADE);
		opcodes.put(116, OpcodeIn.PLAYER_ADDED_ITEMS_TO_TRADE_OFFER);
		opcodes.put(65, OpcodeIn.PLAYER_ACCEPTED_TRADE);
		opcodes.put(164, OpcodeIn.PRAYER_DEACTIVATED);
		opcodes.put(17, OpcodeIn.PRAYER_ACTIVATED);
		opcodes.put(66, OpcodeIn.GAME_SETTINGS_CHANGED);
		opcodes.put(40, OpcodeIn.CHAT_MESSAGE);
		opcodes.put(195, OpcodeIn.COMMAND);
		opcodes.put(191, OpcodeIn.PRIVACY_SETTINGS_CHANGED);
		opcodes.put(180, OpcodeIn.REPORT_ABUSE);
		opcodes.put(83, OpcodeIn.BANK_WITHDRAW);
		opcodes.put(145, OpcodeIn.BANK_DEPOSIT);
		opcodes.put(50, OpcodeIn.BANK_CLOSE);
		opcodes.put(60, OpcodeIn.SLEEPWORD_ENTERED);
		opcodes.put(0, OpcodeIn.LOGIN);
		opcodes.put(1, OpcodeIn.LOGIN);
		opcodes.put(118, OpcodeIn.KNOWN_PLAYERS);
		opcodes.put(31, OpcodeIn.CAST_ON_GROUND_ITEM);
		opcodes.put(70, OpcodeIn.GROUND_ITEM_USE_ITEM);
		opcodes.put(104, OpcodeIn.GROUND_ITEM_TAKE);
		opcodes.put(48, OpcodeIn.CAST_ON_BOUNDARY);
		opcodes.put(167, OpcodeIn.USE_WITH_BOUNDARY);
		opcodes.put(114, OpcodeIn.INTERACT_WITH_BOUNDARY);
		opcodes.put(163, OpcodeIn.INTERACT_WITH_BOUNDARY2);
		opcodes.put(14, OpcodeIn.CAST_ON_SCENERY);
		opcodes.put(153, OpcodeIn.USE_ITEM_ON_SCENERY);
		opcodes.put(90, OpcodeIn.OBJECT_COMMAND);
		opcodes.put(227, OpcodeIn.OBJECT_COMMAND2);
		opcodes.put(159, OpcodeIn.CAST_ON_INVENTORY_ITEM);
		opcodes.put(172, OpcodeIn.ITEM_USE_ITEM);
		opcodes.put(170, OpcodeIn.ITEM_UNEQUIP_FROM_INVENTORY);
		opcodes.put(12, OpcodeIn.ITEM_EQUIP_FROM_INVENTORY);
		opcodes.put(219, OpcodeIn.ITEM_COMMAND);
		opcodes.put(196, OpcodeIn.ITEM_DROP);
		opcodes.put(49, OpcodeIn.CAST_ON_NPC);
		opcodes.put(160, OpcodeIn.NPC_USE_ITEM);
		opcodes.put(7, OpcodeIn.NPC_TALK_TO);
		opcodes.put(151, OpcodeIn.NPC_COMMAND);
		opcodes.put(47, OpcodeIn.NPC_ATTACK);
		opcodes.put(169, OpcodeIn.PLAYER_CAST_PVP);
		opcodes.put(177, OpcodeIn.PLAYER_USE_ITEM);
		opcodes.put(210, OpcodeIn.PLAYER_ATTACK);
		opcodes.put(89, OpcodeIn.PLAYER_DUEL);
		opcodes.put(250, OpcodeIn.PLAYER_INIT_TRADE_REQUEST);
		opcodes.put(36, OpcodeIn.PLAYER_FOLLOW);
		opcodes.put(220, OpcodeIn.CAST_ON_LAND);
		opcodes.put(156, OpcodeIn.CAST_ON_SELF);
	}
}
