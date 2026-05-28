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
 * RSC Protocol-199 Parser of Incoming Packets to respective Protocol Independent Structs
 *
 * mudclient199.jar was released on 2004-10-28 and was in active use for 2 months.
 *
 * This client has a notable bug where the "login" and "shop close" packets have
 * the same opcode.
 *
 * Contemporary open source clients for 199 include TBoT by RichyT.
 * **/
public class Payload199Parser implements PayloadParser<OpcodeIn> {

	private static final Map<Integer, OpcodeIn> opcodes = new HashMap<Integer, OpcodeIn>();
	private final Payload201Parser parser;

	public Payload199Parser() {
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
		return Payload201Parser.isPossiblyValid(opcodes, opcode, length);
	}

	static {
		opcodes.put(1, OpcodeIn.SHOP_CLOSE);
		opcodes.put(76, OpcodeIn.SHOP_BUY);
		opcodes.put(139, OpcodeIn.SHOP_SELL);
		opcodes.put(64, OpcodeIn.BANK_CLOSE);
		opcodes.put(152, OpcodeIn.BANK_WITHDRAW);
		opcodes.put(179, OpcodeIn.BANK_DEPOSIT);
		opcodes.put(79, OpcodeIn.CAST_ON_GROUND_ITEM);
		opcodes.put(199, OpcodeIn.GROUND_ITEM_USE_ITEM);
		opcodes.put(43, OpcodeIn.GROUND_ITEM_TAKE);
		opcodes.put(151, OpcodeIn.INTERACT_WITH_BOUNDARY);
		opcodes.put(50, OpcodeIn.INTERACT_WITH_BOUNDARY2);
		opcodes.put(185, OpcodeIn.CAST_ON_BOUNDARY);
		opcodes.put(157, OpcodeIn.USE_WITH_BOUNDARY);
		opcodes.put(100, OpcodeIn.CAST_ON_SCENERY);
		opcodes.put(150, OpcodeIn.USE_ITEM_ON_SCENERY);
		opcodes.put(171, OpcodeIn.OBJECT_COMMAND);
		opcodes.put(67, OpcodeIn.OBJECT_COMMAND2);
		opcodes.put(192, OpcodeIn.CAST_ON_INVENTORY_ITEM);
		opcodes.put(60, OpcodeIn.ITEM_USE_ITEM);
		opcodes.put(135, OpcodeIn.ITEM_UNEQUIP_FROM_INVENTORY);
		opcodes.put(105, OpcodeIn.ITEM_EQUIP_FROM_INVENTORY);
		opcodes.put(137, OpcodeIn.ITEM_COMMAND);
		opcodes.put(45, OpcodeIn.ITEM_DROP);
		opcodes.put(44, OpcodeIn.CAST_ON_NPC);
		opcodes.put(31, OpcodeIn.NPC_USE_ITEM);
		opcodes.put(207, OpcodeIn.NPC_TALK_TO);
		opcodes.put(83, OpcodeIn.NPC_COMMAND);
		opcodes.put(142, OpcodeIn.NPC_ATTACK);
		opcodes.put(243, OpcodeIn.PLAYER_CAST_PVP);
		opcodes.put(234, OpcodeIn.PLAYER_USE_ITEM);
		opcodes.put(134, OpcodeIn.PLAYER_ATTACK);
		opcodes.put(170, OpcodeIn.PLAYER_DUEL);
		opcodes.put(39, OpcodeIn.PLAYER_INIT_TRADE_REQUEST);
		opcodes.put(214, OpcodeIn.PLAYER_FOLLOW);
		opcodes.put(163, OpcodeIn.CAST_ON_LAND);
		opcodes.put(33, OpcodeIn.CAST_ON_SELF);
		opcodes.put(251, OpcodeIn.HEARTBEAT);
		opcodes.put(7, OpcodeIn.WALK_TO_ENTITY);
		opcodes.put(227, OpcodeIn.WALK_TO_POINT);
		opcodes.put(29, OpcodeIn.CONFIRM_LOGOUT);
		opcodes.put(235, OpcodeIn.LOGOUT);
		opcodes.put(148, OpcodeIn.COMBAT_STYLE_CHANGED);
		opcodes.put(68, OpcodeIn.QUESTION_DIALOG_ANSWER);
		opcodes.put(65, OpcodeIn.PLAYER_APPEARANCE_CHANGE);
		opcodes.put(232, OpcodeIn.SOCIAL_ADD_IGNORE);
		opcodes.put(133, OpcodeIn.SOCIAL_ADD_FRIEND);
		opcodes.put(198, OpcodeIn.SOCIAL_SEND_PRIVATE_MESSAGE);
		opcodes.put(247, OpcodeIn.SOCIAL_REMOVE_FRIEND);
		opcodes.put(136, OpcodeIn.SOCIAL_REMOVE_IGNORE);
		opcodes.put(125, OpcodeIn.DUEL_SECOND_ACCEPTED);
		opcodes.put(177, OpcodeIn.PLAYER_ACCEPTED_INIT_TRADE_REQUEST);
		opcodes.put(24, OpcodeIn.PLAYER_ADDED_ITEMS_TO_TRADE_OFFER);
		opcodes.put(145, OpcodeIn.PLAYER_ACCEPTED_TRADE);
		opcodes.put(216, OpcodeIn.PLAYER_DECLINED_TRADE);
		opcodes.put(13, OpcodeIn.PRAYER_ACTIVATED);
		opcodes.put(122, OpcodeIn.PRAYER_DEACTIVATED);
		opcodes.put(91, OpcodeIn.GAME_SETTINGS_CHANGED);
		opcodes.put(204, OpcodeIn.CHAT_MESSAGE);
		opcodes.put(6, OpcodeIn.COMMAND);
		opcodes.put(22, OpcodeIn.PRIVACY_SETTINGS_CHANGED);
		opcodes.put(10, OpcodeIn.REPORT_ABUSE);
		opcodes.put(18, OpcodeIn.SLEEPWORD_ENTERED);
		opcodes.put(0, OpcodeIn.LOGIN);
		/*
		 * commented out due to bug in the client, this
		 * is handled elsewhere anyway...
		 */
		//opcodes.put(1, OpcodeIn.LOGIN);
		opcodes.put(153, OpcodeIn.DUEL_OFFER_ITEM);
		opcodes.put(128, OpcodeIn.DUEL_FIRST_SETTINGS_CHANGED);
		opcodes.put(208, OpcodeIn.DUEL_FIRST_ACCEPTED);
		opcodes.put(92, OpcodeIn.DUEL_DECLINED);
		opcodes.put(37, OpcodeIn.KNOWN_PLAYERS);
	}
}
