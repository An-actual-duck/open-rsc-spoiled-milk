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
 * RSC Protocol-196 Parser of Incoming Packets to respective Protocol Independent Structs
 *
 * mudclient196.jar was released on 2004-06-28 and was in active use for 2 months.
 *
 * It was the first client to support up to 200 friends and the first to use a
 * username hash to select a login server.
 * **/
public class Payload196Parser implements PayloadParser<OpcodeIn> {

	private static final Map<Integer, OpcodeIn> opcodes = new HashMap<Integer, OpcodeIn>();
	private final Payload201Parser parser;

	public Payload196Parser() {
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

	public static final boolean isPossiblyValid(int opcode, int length) {
		return  Payload201Parser.isPossiblyValid(opcodes, opcode, length);
	}

	static {
		opcodes.put(14, OpcodeIn.HEARTBEAT);
		opcodes.put(53, OpcodeIn.WALK_TO_ENTITY);
		opcodes.put(39, OpcodeIn.WALK_TO_POINT);
		opcodes.put(177, OpcodeIn.CONFIRM_LOGOUT);
		opcodes.put(160, OpcodeIn.LOGOUT);
		opcodes.put(131, OpcodeIn.COMBAT_STYLE_CHANGED);
		opcodes.put(223, OpcodeIn.QUESTION_DIALOG_ANSWER);
		opcodes.put(141, OpcodeIn.PLAYER_APPEARANCE_CHANGE);
		opcodes.put(121, OpcodeIn.SOCIAL_ADD_IGNORE);
		opcodes.put(42, OpcodeIn.SOCIAL_ADD_FRIEND);
		opcodes.put(103, OpcodeIn.SOCIAL_SEND_PRIVATE_MESSAGE);
		opcodes.put(7, OpcodeIn.SOCIAL_REMOVE_FRIEND);
		opcodes.put(202, OpcodeIn.SOCIAL_REMOVE_IGNORE);
		opcodes.put(156, OpcodeIn.DUEL_OFFER_ITEM);
		opcodes.put(175, OpcodeIn.DUEL_SECOND_ACCEPTED);
		opcodes.put(100, OpcodeIn.DUEL_FIRST_SETTINGS_CHANGED);
		opcodes.put(159, OpcodeIn.DUEL_FIRST_ACCEPTED);
		opcodes.put(63, OpcodeIn.DUEL_DECLINED);
		opcodes.put(91, OpcodeIn.SHOP_BUY);
		opcodes.put(104, OpcodeIn.SHOP_SELL);
		opcodes.put(93, OpcodeIn.SHOP_CLOSE);
		opcodes.put(224, OpcodeIn.PLAYER_ACCEPTED_INIT_TRADE_REQUEST);
		opcodes.put(40, OpcodeIn.PLAYER_DECLINED_TRADE);
		opcodes.put(119, OpcodeIn.PLAYER_ADDED_ITEMS_TO_TRADE_OFFER);
		opcodes.put(116, OpcodeIn.PLAYER_ACCEPTED_TRADE);
		opcodes.put(212, OpcodeIn.PRAYER_DEACTIVATED);
		opcodes.put(72, OpcodeIn.PRAYER_ACTIVATED);
		opcodes.put(8, OpcodeIn.GAME_SETTINGS_CHANGED);
		opcodes.put(142, OpcodeIn.CHAT_MESSAGE);
		opcodes.put(171, OpcodeIn.COMMAND);
		opcodes.put(145, OpcodeIn.PRIVACY_SETTINGS_CHANGED);
		opcodes.put(231, OpcodeIn.REPORT_ABUSE);
		opcodes.put(239, OpcodeIn.BANK_WITHDRAW);
		opcodes.put(129, OpcodeIn.BANK_DEPOSIT);
		opcodes.put(215, OpcodeIn.BANK_CLOSE);
		opcodes.put(110, OpcodeIn.SLEEPWORD_ENTERED);
		opcodes.put(0, OpcodeIn.LOGIN);
		opcodes.put(1, OpcodeIn.LOGIN);
		opcodes.put(238, OpcodeIn.KNOWN_PLAYERS);
		opcodes.put(227, OpcodeIn.CAST_ON_GROUND_ITEM);
		opcodes.put(86, OpcodeIn.GROUND_ITEM_USE_ITEM);
		opcodes.put(26, OpcodeIn.GROUND_ITEM_TAKE);
		opcodes.put(243, OpcodeIn.CAST_ON_BOUNDARY);
		opcodes.put(47, OpcodeIn.USE_WITH_BOUNDARY);
		opcodes.put(71, OpcodeIn.INTERACT_WITH_BOUNDARY);
		opcodes.put(144, OpcodeIn.INTERACT_WITH_BOUNDARY2);
		opcodes.put(197, OpcodeIn.CAST_ON_SCENERY);
		opcodes.put(183, OpcodeIn.USE_ITEM_ON_SCENERY);
		opcodes.put(49, OpcodeIn.OBJECT_COMMAND);
		opcodes.put(61, OpcodeIn.OBJECT_COMMAND2);
		opcodes.put(78, OpcodeIn.CAST_ON_INVENTORY_ITEM);
		opcodes.put(225, OpcodeIn.ITEM_USE_ITEM);
		opcodes.put(143, OpcodeIn.ITEM_UNEQUIP_FROM_INVENTORY);
		opcodes.put(186, OpcodeIn.ITEM_EQUIP_FROM_INVENTORY);
		opcodes.put(70, OpcodeIn.ITEM_COMMAND);
		opcodes.put(60, OpcodeIn.ITEM_DROP);
		opcodes.put(185, OpcodeIn.CAST_ON_NPC);
		opcodes.put(22, OpcodeIn.NPC_USE_ITEM);
		opcodes.put(27, OpcodeIn.NPC_TALK_TO);
		opcodes.put(200, OpcodeIn.NPC_COMMAND);
		opcodes.put(201, OpcodeIn.NPC_ATTACK);
		opcodes.put(152, OpcodeIn.PLAYER_CAST_PVP);
		opcodes.put(34, OpcodeIn.PLAYER_USE_ITEM);
		opcodes.put(232, OpcodeIn.PLAYER_ATTACK);
		opcodes.put(242, OpcodeIn.PLAYER_DUEL);
		opcodes.put(112, OpcodeIn.PLAYER_INIT_TRADE_REQUEST);
		opcodes.put(244, OpcodeIn.PLAYER_FOLLOW);
		opcodes.put(44, OpcodeIn.CAST_ON_LAND);
		opcodes.put(209, OpcodeIn.CAST_ON_SELF);
	}
}
