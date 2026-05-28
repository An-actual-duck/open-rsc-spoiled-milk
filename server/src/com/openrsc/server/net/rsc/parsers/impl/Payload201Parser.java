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
 * RSC Protocol-201 Parser of Incoming Packets to respective Protocol Independent Structs
 *
 * Uses different opcodes201 to 202+, and is the final version of the protocol
 * without the anti-bot "banking trap". This was the last protocol used by
 * free-to-play servers, and many bots were written for it thanks to a
 * popular deobufuscated version released by saevion.
 *
 * mudclient201.jar was released on 2004-12-13 and was in active use for 9 months.
 * **/
public class Payload201Parser implements PayloadParser<OpcodeIn> {

	private static final Map<Integer, OpcodeIn> opcodes201 = new HashMap<Integer, OpcodeIn>();
	private final Map<Integer, OpcodeIn> opcodes;
	private final Payload203Parser parser;

	public Payload201Parser() {
		this(opcodes201);
	}

	public Payload201Parser(Map<Integer, OpcodeIn> opcodes) {
		this.opcodes = opcodes;
		parser = new Payload203Parser(opcodes);
	}

	@Override
	public OpcodeIn toOpcodeEnum(Packet packet, Player player) {
		return opcodes.get(packet.getID());
	}

	@Override
	public AbstractStruct<OpcodeIn> parse(Packet packet, Player player) {
		OpcodeIn opcode = toOpcodeEnum(packet, player);
		AbstractStruct<OpcodeIn> result = null;
		try {
			switch (opcode) {
				case BANK_WITHDRAW:
				case BANK_DEPOSIT:
					BankStruct b1 = new BankStruct();
					b1.catalogID = packet.readShort();
					b1.amount = packet.readShort();
					result = b1;
					break;
			}
		} catch (Exception e) {
		}
		if (result != null) {
			result.setOpcode(opcode);
			return result;
		}
		return parser.parse(packet, player);

	}

	public static final boolean isPossiblyValid(Map<Integer, OpcodeIn> opcodes, int opcode, int length) {
		int payloadLength = length - 1; // subtract off opcode length.
		OpcodeIn op = opcodes.get(opcode);
		if (op == null) {
			System.out.println(String.format("Received unknown opcode %d from authentic claiming client", opcode));
			return false;
		}
		switch (op) {
			case BANK_DEPOSIT:
			case BANK_WITHDRAW:
				return payloadLength == 4;
		}
		return Payload203Parser.isPossiblyValid(opcodes, opcode, length);
	}

	public static final boolean isPossiblyValid(int opcode, int length) {
		return isPossiblyValid(opcodes201, opcode, length);
	}

	static {
		opcodes201.put(186, OpcodeIn.HEARTBEAT);
		opcodes201.put(226, OpcodeIn.WALK_TO_ENTITY);
		opcodes201.put(211, OpcodeIn.WALK_TO_POINT);
		opcodes201.put(104, OpcodeIn.CONFIRM_LOGOUT);
		opcodes201.put(3, OpcodeIn.LOGOUT);
		opcodes201.put(74, OpcodeIn.COMBAT_STYLE_CHANGED);
		opcodes201.put(189, OpcodeIn.QUESTION_DIALOG_ANSWER);
		opcodes201.put(238, OpcodeIn.PLAYER_APPEARANCE_CHANGE);
		opcodes201.put(254, OpcodeIn.SOCIAL_ADD_IGNORE);
		opcodes201.put(232, OpcodeIn.SOCIAL_ADD_FRIEND);
		opcodes201.put(59, OpcodeIn.SOCIAL_SEND_PRIVATE_MESSAGE);
		opcodes201.put(52, OpcodeIn.SOCIAL_REMOVE_FRIEND);
		opcodes201.put(244, OpcodeIn.SOCIAL_REMOVE_IGNORE);
		opcodes201.put(125, OpcodeIn.DUEL_FIRST_ACCEPTED);
		opcodes201.put(229, OpcodeIn.DUEL_OFFER_ITEM);
		opcodes201.put(175, OpcodeIn.DUEL_SECOND_ACCEPTED);
		opcodes201.put(100, OpcodeIn.INTERACT_WITH_BOUNDARY);
		opcodes201.put(121, OpcodeIn.INTERACT_WITH_BOUNDARY2);
		opcodes201.put(76, OpcodeIn.CAST_ON_BOUNDARY);
		opcodes201.put(71, OpcodeIn.USE_WITH_BOUNDARY);
		opcodes201.put(159, OpcodeIn.NPC_TALK_TO);
		opcodes201.put(89, OpcodeIn.NPC_COMMAND);
		opcodes201.put(118, OpcodeIn.NPC_ATTACK);
		opcodes201.put(10, OpcodeIn.CAST_ON_NPC);
		opcodes201.put(143, OpcodeIn.NPC_USE_ITEM);
		opcodes201.put(56, OpcodeIn.PLAYER_CAST_PVP);
		opcodes201.put(11, OpcodeIn.PLAYER_USE_ITEM);
		opcodes201.put(124, OpcodeIn.PLAYER_ATTACK);
		opcodes201.put(217, OpcodeIn.PLAYER_DUEL);
		opcodes201.put(62, OpcodeIn.PLAYER_INIT_TRADE_REQUEST);
		opcodes201.put(91, OpcodeIn.PLAYER_FOLLOW);
		opcodes201.put(18, OpcodeIn.CAST_ON_GROUND_ITEM);
		opcodes201.put(255, OpcodeIn.GROUND_ITEM_USE_ITEM);
		opcodes201.put(235, OpcodeIn.ITEM_USE_ITEM);
		opcodes201.put(40, OpcodeIn.ITEM_UNEQUIP_FROM_INVENTORY);
		opcodes201.put(199, OpcodeIn.ITEM_EQUIP_FROM_INVENTORY);
		opcodes201.put(24, OpcodeIn.ITEM_COMMAND);
		opcodes201.put(123, OpcodeIn.ITEM_DROP);
		opcodes201.put(44, OpcodeIn.CAST_ON_SELF);
		opcodes201.put(201, OpcodeIn.CAST_ON_LAND);
		opcodes201.put(38, OpcodeIn.OBJECT_COMMAND);
		opcodes201.put(172, OpcodeIn.OBJECT_COMMAND2);
		opcodes201.put(237, OpcodeIn.CAST_ON_SCENERY);
		opcodes201.put(127, OpcodeIn.USE_ITEM_ON_SCENERY);
		opcodes201.put(92, OpcodeIn.SHOP_CLOSE);
		opcodes201.put(67, OpcodeIn.SHOP_BUY);
		opcodes201.put(177, OpcodeIn.SHOP_SELL);
		opcodes201.put(94, OpcodeIn.PLAYER_ACCEPTED_INIT_TRADE_REQUEST);
		opcodes201.put(27, OpcodeIn.PLAYER_DECLINED_TRADE);
		opcodes201.put(144, OpcodeIn.PLAYER_ADDED_ITEMS_TO_TRADE_OFFER);
		opcodes201.put(102, OpcodeIn.PLAYER_ACCEPTED_TRADE);
		opcodes201.put(202, OpcodeIn.PRAYER_ACTIVATED);
		opcodes201.put(162, OpcodeIn.PRAYER_DEACTIVATED);
		opcodes201.put(165, OpcodeIn.GAME_SETTINGS_CHANGED);
		opcodes201.put(249, OpcodeIn.CHAT_MESSAGE);
		opcodes201.put(32, OpcodeIn.COMMAND);
		opcodes201.put(247, OpcodeIn.PRIVACY_SETTINGS_CHANGED);
		opcodes201.put(215, OpcodeIn.REPORT_ABUSE);
		opcodes201.put(78, OpcodeIn.BANK_CLOSE);
		opcodes201.put(131, OpcodeIn.BANK_WITHDRAW);
		opcodes201.put(190, OpcodeIn.BANK_DEPOSIT);
		opcodes201.put(142, OpcodeIn.SLEEPWORD_ENTERED);
		opcodes201.put(0, OpcodeIn.LOGIN);
		opcodes201.put(1, OpcodeIn.LOGIN);
		opcodes201.put(166, OpcodeIn.CAST_ON_INVENTORY_ITEM);
		opcodes201.put(138, OpcodeIn.DUEL_FIRST_SETTINGS_CHANGED);
		opcodes201.put(43, OpcodeIn.DUEL_DECLINED);
		opcodes201.put(253, OpcodeIn.GROUND_ITEM_TAKE);
		opcodes201.put(241, OpcodeIn.KNOWN_PLAYERS);
	}
}
