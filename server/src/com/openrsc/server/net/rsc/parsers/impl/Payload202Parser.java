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
 * RSC Protocol-202 Parser of Incoming Packets to respective Protocol Independent Structs
 *
 * Uses different opcodes to 203, but the same structure. This version added the so-called
 * "banking trap" (magic numbers in the deposit and withdraw packets),
 * later used to identify modified clients in a mass ban, which was never removed.
 *
 * This was the original protocol version used by RSCDaemon.
 *
 * https://github.com/moparisthebest/rswiki-book/blob/master/src/202-Protocol.md
 *
 * mudclient202.jar was released on 2005-08-03 and was in active use for 3 months.
 * **/
public class Payload202Parser implements PayloadParser<OpcodeIn> {

	private static final Map<Integer, OpcodeIn> opcodes = new HashMap<Integer, OpcodeIn>();
	private final Payload203Parser parser;

	public Payload202Parser() {
		parser = new Payload203Parser(opcodes);
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
		return Payload203Parser.isPossiblyValid(opcodes, opcode, length);
	}

	static {
		opcodes.put(153, OpcodeIn.HEARTBEAT);
		opcodes.put(246, OpcodeIn.WALK_TO_ENTITY);
		opcodes.put(132, OpcodeIn.WALK_TO_POINT);
		opcodes.put(39, OpcodeIn.CONFIRM_LOGOUT);
		opcodes.put(129, OpcodeIn.LOGOUT);
		opcodes.put(41, OpcodeIn.COMBAT_STYLE_CHANGED);
		opcodes.put(154, OpcodeIn.QUESTION_DIALOG_ANSWER);
		opcodes.put(218, OpcodeIn.PLAYER_APPEARANCE_CHANGE);
		opcodes.put(25, OpcodeIn.SOCIAL_ADD_IGNORE);
		opcodes.put(168, OpcodeIn.SOCIAL_ADD_FRIEND);
		opcodes.put(254, OpcodeIn.SOCIAL_SEND_PRIVATE_MESSAGE);
		opcodes.put(52, OpcodeIn.SOCIAL_REMOVE_FRIEND);
		opcodes.put(108, OpcodeIn.SOCIAL_REMOVE_IGNORE);
		opcodes.put(252, OpcodeIn.DUEL_FIRST_ACCEPTED);
		opcodes.put(123, OpcodeIn.DUEL_OFFER_ITEM);
		opcodes.put(87, OpcodeIn.DUEL_SECOND_ACCEPTED);
		opcodes.put(126, OpcodeIn.INTERACT_WITH_BOUNDARY);
		opcodes.put(235, OpcodeIn.INTERACT_WITH_BOUNDARY2);
		opcodes.put(67, OpcodeIn.CAST_ON_BOUNDARY);
		opcodes.put(36, OpcodeIn.USE_WITH_BOUNDARY);
		opcodes.put(177, OpcodeIn.NPC_TALK_TO);
		opcodes.put(74, OpcodeIn.NPC_COMMAND);
		opcodes.put(73, OpcodeIn.NPC_ATTACK);
		opcodes.put(71, OpcodeIn.CAST_ON_NPC);
		opcodes.put(142, OpcodeIn.NPC_USE_ITEM);
		opcodes.put(55, OpcodeIn.PLAYER_CAST_PVP);
		opcodes.put(16, OpcodeIn.PLAYER_USE_ITEM);
		opcodes.put(57, OpcodeIn.PLAYER_ATTACK);
		opcodes.put(222, OpcodeIn.PLAYER_DUEL);
		opcodes.put(166, OpcodeIn.PLAYER_INIT_TRADE_REQUEST);
		opcodes.put(68, OpcodeIn.PLAYER_FOLLOW);
		opcodes.put(104, OpcodeIn.CAST_ON_GROUND_ITEM);
		opcodes.put(34, OpcodeIn.GROUND_ITEM_USE_ITEM);
		opcodes.put(27, OpcodeIn.ITEM_USE_ITEM);
		opcodes.put(92, OpcodeIn.ITEM_UNEQUIP_FROM_INVENTORY);
		opcodes.put(181, OpcodeIn.ITEM_EQUIP_FROM_INVENTORY);
		opcodes.put(89, OpcodeIn.ITEM_COMMAND);
		opcodes.put(147, OpcodeIn.ITEM_DROP);
		opcodes.put(206, OpcodeIn.CAST_ON_SELF);
		opcodes.put(232, OpcodeIn.CAST_ON_LAND);
		opcodes.put(51, OpcodeIn.OBJECT_COMMAND);
		opcodes.put(40, OpcodeIn.OBJECT_COMMAND2);
		opcodes.put(17, OpcodeIn.CAST_ON_SCENERY);
		opcodes.put(94, OpcodeIn.USE_ITEM_ON_SCENERY);
		opcodes.put(253, OpcodeIn.SHOP_CLOSE);
		opcodes.put(128, OpcodeIn.SHOP_BUY);
		opcodes.put(255, OpcodeIn.SHOP_SELL);
		opcodes.put(211, OpcodeIn.PLAYER_ACCEPTED_INIT_TRADE_REQUEST);
		opcodes.put(216, OpcodeIn.PLAYER_DECLINED_TRADE);
		opcodes.put(70, OpcodeIn.PLAYER_ADDED_ITEMS_TO_TRADE_OFFER);
		opcodes.put(53, OpcodeIn.PLAYER_ACCEPTED_TRADE);
		opcodes.put(56, OpcodeIn.PRAYER_ACTIVATED);
		opcodes.put(248, OpcodeIn.PRAYER_DEACTIVATED);
		opcodes.put(157, OpcodeIn.GAME_SETTINGS_CHANGED);
		opcodes.put(145, OpcodeIn.CHAT_MESSAGE);
		opcodes.put(90, OpcodeIn.COMMAND);
		opcodes.put(176, OpcodeIn.PRIVACY_SETTINGS_CHANGED);
		opcodes.put(7, OpcodeIn.REPORT_ABUSE);
		opcodes.put(48, OpcodeIn.BANK_CLOSE);
		opcodes.put(183, OpcodeIn.BANK_WITHDRAW);
		opcodes.put(198, OpcodeIn.BANK_DEPOSIT);
		opcodes.put(72, OpcodeIn.SLEEPWORD_ENTERED);
		opcodes.put(0, OpcodeIn.LOGIN);
		opcodes.put(1, OpcodeIn.LOGIN);
		opcodes.put(49, OpcodeIn.CAST_ON_INVENTORY_ITEM);
		opcodes.put(225, OpcodeIn.DUEL_FIRST_SETTINGS_CHANGED);
		opcodes.put(35, OpcodeIn.DUEL_DECLINED);
		opcodes.put(245, OpcodeIn.GROUND_ITEM_TAKE);
		opcodes.put(83, OpcodeIn.KNOWN_PLAYERS);
	}
}
