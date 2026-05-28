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
 * RSC Protocol-203 Parser of Incoming Packets to respective Protocol Independent Structs
 *
 * This is the final version of the protocol before "Retro Revival" in 2009.
 * Contemporary open source clients for 203 include STS203C and STShell by Reines.
 *
 * mudclient203.jar was released on 2005-11-08.
 * mudclient204.jar (same protocol version) was released on 2006-05-25.
 * **/
public class Payload203Parser implements PayloadParser<OpcodeIn> {

	private static final Map<Integer, OpcodeIn> opcodes203 = new HashMap<Integer, OpcodeIn>();

	private final Map<Integer, OpcodeIn> opcodes;

	public Payload203Parser(Map<Integer, OpcodeIn> opcodes) {
		this.opcodes = opcodes;
	}

	public Payload203Parser() {
		this.opcodes = opcodes203;
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
			case COMBAT_STYLE_CHANGED:
				CombatStyleStruct c = new CombatStyleStruct();
				c.style = packet.readByte();
				result = c;
				break;

			case PLAYER_APPEARANCE_CHANGE:
				PlayerAppearanceStruct pl = new PlayerAppearanceStruct();
				pl.headRestrictions = packet.readByte();
				pl.headType = packet.readByte();
				pl.bodyType = packet.readByte();
				pl.mustEqual2 = packet.readByte();
				pl.hairColour = packet.readByte();
				pl.topColour = packet.readByte();
				pl.trouserColour = packet.readByte();
				pl.skinColour = packet.readByte();
				result = pl;
				break;

			case QUESTION_DIALOG_ANSWER:
				MenuOptionStruct m = new MenuOptionStruct();
				m.option = packet.readByte();
				result = m;
				break;

			case CHAT_MESSAGE:
				ChatStruct cs = new ChatStruct();
				cs.message = StringUtil.decompressMessage(packet.readBytes(packet.getReadableBytes()));
				result = cs;
				break;
			case COMMAND:
				CommandStruct co = new CommandStruct();
				co.command = packet.readString();
				result = co;
				break;
			case SOCIAL_ADD_FRIEND:
			case SOCIAL_REMOVE_FRIEND:
			case SOCIAL_ADD_IGNORE:
			case SOCIAL_REMOVE_IGNORE:
			case SOCIAL_SEND_PRIVATE_MESSAGE:
				FriendStruct fs = new FriendStruct();
				fs.player = DataConversions.hashToUsername(packet.readLong());
				if (opcode == OpcodeIn.SOCIAL_SEND_PRIVATE_MESSAGE) {
					fs.message = StringUtil.decompressMessage(packet.readBytes(packet.getReadableBytes()));
				}
				result = fs;
				break;

			case BANK_CLOSE:
				BankStruct b = new BankStruct();
				result = b;
				break;
			case BANK_WITHDRAW:
			case BANK_DEPOSIT:
				BankStruct b1 = new BankStruct();
				b1.catalogID = packet.readShort();
				b1.amount = packet.readShort();
				b1.magicNumber = packet.readInt();
				result = b1;
				break;

			case SHOP_CLOSE:
				ShopStruct s = new ShopStruct();
				result = s;
				break;
			case SHOP_BUY:
			case SHOP_SELL:
				ShopStruct s1 = new ShopStruct();
				s1.catalogID = packet.readShort();
				s1.stockAmount = packet.readInt();
				s1.amount = 1;
				result = s1;
				break;

			case ITEM_UNEQUIP_FROM_INVENTORY:
			case ITEM_EQUIP_FROM_INVENTORY:
				EquipStruct e = new EquipStruct();
				e.slotIndex = packet.readShort();
				result = e;
				break;

			case GROUND_ITEM_USE_ITEM:
				ItemOnGroundItemStruct iog = new ItemOnGroundItemStruct();
				iog.groundItemCoord = new Point(packet.readShort(), packet.readShort());
				iog.groundItemId = packet.readShort();
				iog.slotIndex = packet.readShort();
				result = iog;
				break;

			case ITEM_USE_ITEM:
				ItemOnItemStruct ioi = new ItemOnItemStruct();
				ioi.slotIndex1 = packet.readShort();
				ioi.slotIndex2 = packet.readShort();
				result = ioi;
				break;

			case USE_WITH_BOUNDARY:
			case USE_ITEM_ON_SCENERY:
				ItemOnObjectStruct ioo = new ItemOnObjectStruct();
				ioo.coordObject = new Point(packet.readShort(), packet.readShort());
				if (opcode == OpcodeIn.USE_WITH_BOUNDARY) {
					ioo.direction = packet.readByte();
				}
				ioo.slotID = packet.readShort();
				result = ioo;
				break;

			case NPC_USE_ITEM:
			case PLAYER_USE_ITEM:
				ItemOnMobStruct it = new ItemOnMobStruct();
				it.serverIndex = packet.readShort();
				it.slotIndex = packet.readShort();
				result = it;
				break;

			case BLINK:
			case GROUND_ITEM_TAKE:
				TargetPositionStruct tp = new TargetPositionStruct();
				tp.coordinate = new Point(packet.readShort(), packet.readShort());
				if (opcode == OpcodeIn.GROUND_ITEM_TAKE) {
					tp.itemId = packet.readShort();
				}
				result = tp;
				break;

			case ITEM_COMMAND:
			case ITEM_DROP:
				ItemCommandStruct ic = new ItemCommandStruct();
				ic.index = packet.readShort();
				result = ic;
				break;

			case OBJECT_COMMAND:
			case OBJECT_COMMAND2:
			case INTERACT_WITH_BOUNDARY:
			case INTERACT_WITH_BOUNDARY2:
				TargetObjectStruct to = new TargetObjectStruct();
				to.coordObject = new Point(packet.readShort(), packet.readShort());
				if (opcode == OpcodeIn.INTERACT_WITH_BOUNDARY || opcode == OpcodeIn.INTERACT_WITH_BOUNDARY2) {
					to.direction = packet.readByte();
				}
				result = to;
				break;

			case NPC_ATTACK:
			case NPC_COMMAND:
			case NPC_TALK_TO:
			case PLAYER_ATTACK:
			case PLAYER_FOLLOW:
				TargetMobStruct t = new TargetMobStruct();
				t.serverIndex = packet.readShort();
				result = t;
				break;

			case CAST_ON_SELF:
			case PLAYER_CAST_PVP:
			case CAST_ON_NPC:
			case CAST_ON_INVENTORY_ITEM:
			case CAST_ON_BOUNDARY:
			case CAST_ON_SCENERY:
			case CAST_ON_GROUND_ITEM:
			case CAST_ON_LAND:
				SpellStruct sp = new SpellStruct();
				if (opcode == OpcodeIn.PLAYER_CAST_PVP || opcode == OpcodeIn.CAST_ON_NPC
					|| opcode == OpcodeIn.CAST_ON_INVENTORY_ITEM) {
					sp.targetIndex = packet.readShort();
				} else if (opcode == OpcodeIn.CAST_ON_BOUNDARY || opcode == OpcodeIn.CAST_ON_SCENERY
					|| opcode == OpcodeIn.CAST_ON_GROUND_ITEM || opcode == OpcodeIn.CAST_ON_LAND) {
					sp.targetCoord = new Point(packet.readShort(), packet.readShort());
					if (opcode == OpcodeIn.CAST_ON_BOUNDARY) {
						sp.direction = packet.readByte();
					} else if (opcode == OpcodeIn.CAST_ON_GROUND_ITEM) {
						sp.targetIndex = packet.readShort();
					}
				}
				sp.spell = Constants.spellToEnum(packet.readShort());
				result = sp;
				break;

			case PLAYER_DUEL:
			case DUEL_FIRST_SETTINGS_CHANGED:
			case DUEL_FIRST_ACCEPTED:
			case DUEL_DECLINED:
			case DUEL_OFFER_ITEM:
			case DUEL_SECOND_ACCEPTED:
				PlayerDuelStruct pd = new PlayerDuelStruct();
				if (opcode == OpcodeIn.PLAYER_DUEL) {
					pd.targetPlayerID = packet.readShort();
				} else if (opcode == OpcodeIn.DUEL_OFFER_ITEM) {
					pd.duelCount = packet.readByte();
					pd.duelCatalogIDs = new int[pd.duelCount];
					pd.duelAmounts = new int[pd.duelCount];
					pd.duelNoted = new boolean[pd.duelCount];
					for (int slot = 0; slot < pd.duelCount; slot++) {
						pd.duelCatalogIDs[slot] = packet.readShort();
						pd.duelAmounts[slot] = packet.readInt();
						pd.duelNoted[slot] = false;
					}
				} else if (opcode == OpcodeIn.DUEL_FIRST_SETTINGS_CHANGED) {
					pd.disallowRetreat = packet.readByte();
					pd.disallowMagic = packet.readByte();
					pd.disallowPrayer = packet.readByte();
					pd.disallowWeapons = packet.readByte();
				}
				result = pd;
				break;

			case PLAYER_INIT_TRADE_REQUEST:
			case PLAYER_ACCEPTED_INIT_TRADE_REQUEST:
			case PLAYER_ACCEPTED_TRADE:
			case PLAYER_DECLINED_TRADE:
			case PLAYER_ADDED_ITEMS_TO_TRADE_OFFER:
				PlayerTradeStruct pt = new PlayerTradeStruct();
				if (opcode == OpcodeIn.PLAYER_INIT_TRADE_REQUEST) {
					pt.targetPlayerID = packet.readShort();
				} else if (opcode == OpcodeIn.PLAYER_ADDED_ITEMS_TO_TRADE_OFFER) {
					pt.tradeCount = packet.readByte();
					pt.tradeCatalogIDs = new int[pt.tradeCount];
					pt.tradeAmounts = new int[pt.tradeCount];
					pt.tradeNoted = new boolean[pt.tradeCount];
					for (int slot = 0; slot < pt.tradeCount; slot++) {
						pt.tradeCatalogIDs[slot] = packet.readShort();
						pt.tradeAmounts[slot] = packet.readInt();
						pt.tradeNoted[slot] = false;
					}
				}
				result = pt;
				break;

			case PRAYER_ACTIVATED:
			case PRAYER_DEACTIVATED:
				PrayerStruct p = new PrayerStruct();
				p.prayerID = packet.readByte();
				result = p;
				break;

			case GAME_SETTINGS_CHANGED:
				GameSettingStruct gs = new GameSettingStruct();
				int setting = gs.index = packet.readByte();
				int value = gs.value = packet.readByte();
				if (setting == 0) {
					gs.cameraModeAuto = value;
				} else if (setting == 2) {
					gs.mouseButtonOne = value;
				} else if (setting == 3) {
					gs.soundDisabled = value;
				}
				result = gs;
				break;

			case PRIVACY_SETTINGS_CHANGED:
				PrivacySettingsStruct pr = new PrivacySettingsStruct();
				pr.blockChat = packet.readByte();
				pr.blockPrivate = packet.readByte();
				pr.blockTrade = packet.readByte();
				pr.blockDuel = packet.readByte();
				result = pr;
				break;

			case CHANGE_PASS:
			case CANCEL_RECOVERY_REQUEST:
			case CHANGE_RECOVERY_REQUEST:
			case CHANGE_DETAILS_REQUEST:
			case SET_RECOVERY:
			case SET_DETAILS:
				SecuritySettingsStruct sec = new SecuritySettingsStruct();
				if (opcode == OpcodeIn.CHANGE_PASS) {
					// Get encrypted block
					// old + new password is always 40 characters long, with spaces at the end.
					// each blocks having encrypted 7 chars of password
					int blockLen;
					byte[] decBlock; // current decrypted block
					int session = -1; // TODO: should be players stored TCP session to check if request should be processed
					int receivedSession;
					boolean errored = false;
					byte[] concatPassData = new byte[42];
					for (int i = 0; i < 6; i++) {
						blockLen = packet.readUnsignedByte();
						decBlock = Crypto.decryptRSA(packet.readBytes(blockLen), 0, blockLen);
						// TODO: there are ignored nonces at the beginning of the decrypted block
						receivedSession = ByteBuffer.wrap(Arrays.copyOfRange(decBlock, 4, 8)).getInt();
						// decrypted packet must be of length 15
						if (session == -1 && decBlock.length == 15) {
							session = receivedSession;
						} else if (session != receivedSession || decBlock.length != 15) {
							errored = true; // decryption error occurred
						}

						if (!errored) {
							System.arraycopy(decBlock, 8, concatPassData, i * 7, 7);
						}
					}

					String oldPassword = "";
					String newPassword = "";
					try {
						oldPassword = new String(Arrays.copyOfRange(concatPassData, 0, 20), "UTF8").trim();
						newPassword = new String(Arrays.copyOfRange(concatPassData, 20, 42), "UTF8").trim();
					} catch (Exception ex1) {
						//LOGGER.info("error parsing passwords in change password block");
						errored = true;
						ex1.printStackTrace();
					}

					if (!errored) {
						sec.passwords = new String[]{ oldPassword, newPassword };
					}
				} else if (opcode == OpcodeIn.SET_RECOVERY) {
					// Get the 5 recovery answers
					int blockLen;
					byte[] decBlock; // current decrypted block
					int session = -1; // TODO: should be players stored TCP session to check if request should be processed
					int receivedSession;
					boolean errored = false;
					int questLen = 0;
					int answerLen = 0;
					int expBlocks = 0;
					byte[] answerData;
					String questions[] = new String[5];
					String answers[] = new String[5];
					for (int i = 0; i < 5; i++) {
						questLen = packet.readUnsignedByte();
						questions[i] = new String(packet.readBytes(questLen));
						answerLen = packet.readUnsignedByte();
						// Get encrypted block for answers
						expBlocks = (int)Math.ceil(answerLen / 7.0);
						answerData = new byte[expBlocks * 7];
						for (int j = 0; j < expBlocks; j++) {
							blockLen = packet.readUnsignedByte();
							decBlock = Crypto.decryptRSA(packet.readBytes(blockLen), 0, blockLen);
							// TODO: there are ignored nonces at the beginning of the decrypted block
							receivedSession = ByteBuffer.wrap(Arrays.copyOfRange(decBlock, 4, 8)).getInt();
							// decrypted packet must be of length 15
							if (session == -1 && decBlock.length == 15) {
								session = receivedSession;
							} else if (session != receivedSession || decBlock.length != 15) {
								errored = true; // decryption error occurred
							}

							if (!errored) {
								System.arraycopy(decBlock, 8, answerData, j * 7, 7);
							}
						}

						try {
							answers[i] = new String(answerData, "UTF8").trim();
						} catch (Exception ex) {
							//LOGGER.info("error parsing answer " + i + " in change recovery block");
							errored = true;
							ex.printStackTrace();
						}
					}

					if (!errored) {
						sec.questions = questions.clone();
						sec.answers = answers.clone();
					}
				} else if (opcode == OpcodeIn.SET_DETAILS) {
					boolean errored = false;
					int expLen = 0;
					String details[] = new String[4];
					for (int i = 0; i < 4; i++) {
						expLen = packet.readUnsignedByte();
						details[i] = new String(packet.readBytes(expLen));
						if (details[i].length() != expLen) errored = true;
					}

					if (!errored) {
						sec.details = details.clone();
					}
				}
				result = sec;
				break;

			case REPORT_ABUSE:
				ReportStruct r = new ReportStruct();
				r.targetPlayerName = DataConversions.hashToUsername(packet.readLong());
				r.reason = (byte)(packet.readByte() | 32); // adds 32 to separate from 181- and 205+ reasons
				r.suggestsOrMutes = packet.readByte();
				result = r;
				break;

			case SLEEPWORD_ENTERED:
				SleepStruct sl = new SleepStruct();
				sl.sleepDelay = 0;
				sl.sleepWord = packet.readString();
				result = sl;
				break;

			case HEARTBEAT:
			case LOGOUT:
			case CONFIRM_LOGOUT:
				NoPayloadStruct n = new NoPayloadStruct();
				result = n;
				break;

			case WALK_TO_POINT:
			case WALK_TO_ENTITY:
				WalkStruct w = new WalkStruct();
				w.firstStep = new Point(packet.readShort(), packet.readShort());

				int numWaypoints = packet.getReadableBytes() / 2;
				for (int stepCount = 0; stepCount < numWaypoints; stepCount++) {
					w.steps.add(new Point(packet.readByte(), packet.readByte()));
				}
				result = w;
				break;

			case KNOWN_PLAYERS:
				KnownPlayersStruct kp = new KnownPlayersStruct();
				kp.playerCount = packet.readShort();
				kp.playerServerIndex = new int[kp.playerCount];
				kp.playerServerAppearanceId = new int[kp.playerCount];
				for (int i = 0; i < kp.playerCount; i++) {
					kp.playerServerIndex[i] = packet.readShort();
					kp.playerServerAppearanceId[i] = packet.readShort();
				}
				result = kp;
				break;
		}
		} catch (Exception e) {
		}

		if (result != null) {
			result.setOpcode(opcode);
		}

		return result;

	}

	public static final boolean isPossiblyValid(int opcode, int length) {
		return isPossiblyValid(opcodes203, opcode, length);
	}

	// a basic check is done on authentic opcodes against their possible lengths
	public static final boolean isPossiblyValid(Map<Integer, OpcodeIn> opcodes, int opcode, int length) {
		int payloadLength = length - 1; // subtract off opcode length.
		OpcodeIn op = opcodes.get(opcode);
		if (op == null) {
			System.out.println(String.format("Received unknown opcode %d from authentic claiming client", opcode));
			return false;
		}
		switch (op) {
			case HEARTBEAT:
				return payloadLength == 0;
			case WALK_TO_ENTITY:
				return payloadLength >= 4;
			case WALK_TO_POINT:
				return payloadLength >= 4;
			case CONFIRM_LOGOUT:
				return payloadLength == 0;
			case LOGOUT:
				return payloadLength == 0;
			case COMBAT_STYLE_CHANGED:
				return payloadLength == 1;
			case QUESTION_DIALOG_ANSWER:
				return payloadLength == 1;
			case PLAYER_APPEARANCE_CHANGE:
				return payloadLength == 8;
			case SOCIAL_ADD_IGNORE:
				return payloadLength >= 8;
			case SOCIAL_ADD_FRIEND:
				return payloadLength >= 8;
			case SOCIAL_SEND_PRIVATE_MESSAGE:
				return payloadLength >= 8;
			case SOCIAL_REMOVE_FRIEND:
				return payloadLength >= 8;
			case SOCIAL_REMOVE_IGNORE:
				return payloadLength >= 8;

			case DUEL_FIRST_SETTINGS_CHANGED:
				return payloadLength == 4;
			case DUEL_FIRST_ACCEPTED:
				return payloadLength == 0;
			case DUEL_DECLINED:
				return payloadLength == 0;
			case DUEL_OFFER_ITEM:
				return payloadLength >= 1;
			case DUEL_SECOND_ACCEPTED:
				return payloadLength == 0;

			case INTERACT_WITH_BOUNDARY:
				return payloadLength == 5;
			case INTERACT_WITH_BOUNDARY2:
				return payloadLength == 5;
			case CAST_ON_BOUNDARY:
				return payloadLength == 7;
			case USE_WITH_BOUNDARY:
				return payloadLength == 7;

			case NPC_TALK_TO:
				return payloadLength == 2;
			case NPC_COMMAND:
				return payloadLength == 2;
			case NPC_ATTACK:
				return payloadLength == 2;
			case CAST_ON_NPC:
				return payloadLength == 4;
			case NPC_USE_ITEM:
				return payloadLength == 4;

			case PLAYER_CAST_PVP:
				return payloadLength == 4;
			case PLAYER_USE_ITEM:
				return payloadLength == 4;
			case PLAYER_ATTACK:
				return payloadLength == 2;
			case PLAYER_DUEL:
				return payloadLength == 2;
			case PLAYER_INIT_TRADE_REQUEST:
				return payloadLength == 2;
			case PLAYER_FOLLOW:
				return payloadLength == 2;

			case CAST_ON_GROUND_ITEM:
				return payloadLength == 8;
			case GROUND_ITEM_USE_ITEM:
				return payloadLength == 8;
			case GROUND_ITEM_TAKE:
				// Appears to be a genuine client bug (or anti-bot trap) introduced
				// somewhere between 179-183 that garbage data is appended to the
				// end, so it's more than 6 bytes even though it doesn't need to be.
				// Fixed after 204.
				return payloadLength >= 6;

			case CAST_ON_INVENTORY_ITEM:
				return payloadLength == 4;
			case ITEM_USE_ITEM:
				return payloadLength == 4;
			case ITEM_UNEQUIP_FROM_INVENTORY:
				return payloadLength == 2;
			case ITEM_EQUIP_FROM_INVENTORY:
				return payloadLength == 2;
			case ITEM_COMMAND:
				return payloadLength == 2;
			case ITEM_DROP:
				return payloadLength == 2;

			case CAST_ON_SELF:
				return payloadLength == 2;
			case CAST_ON_LAND:
				return payloadLength == 6;

			case OBJECT_COMMAND:
				return payloadLength == 4;
			case OBJECT_COMMAND2:
				return payloadLength == 4;
			case CAST_ON_SCENERY:
				return payloadLength == 6;
			case USE_ITEM_ON_SCENERY:
				return payloadLength == 6;

			case SHOP_CLOSE:
				return payloadLength == 0;
			case SHOP_BUY:
				return payloadLength == 6;
			case SHOP_SELL:
				return payloadLength == 6;

			case PLAYER_ACCEPTED_INIT_TRADE_REQUEST:
				return payloadLength == 0;
			case PLAYER_DECLINED_TRADE:
				return payloadLength == 0;
			case PLAYER_ADDED_ITEMS_TO_TRADE_OFFER:
				return payloadLength >= 1;
			case PLAYER_ACCEPTED_TRADE:
				return payloadLength == 0;

			case PRAYER_ACTIVATED:
				return payloadLength == 1;
			case PRAYER_DEACTIVATED:
				return payloadLength == 1;

			case GAME_SETTINGS_CHANGED:
				return payloadLength == 2;
			case CHAT_MESSAGE:
				return payloadLength >= 1;
			case COMMAND:
				return payloadLength >= 1;
			case PRIVACY_SETTINGS_CHANGED:
				return payloadLength == 4;
			case REPORT_ABUSE:
				return payloadLength == 10;
			case BANK_CLOSE:
				return payloadLength == 0;
			case BANK_WITHDRAW:
				return payloadLength >= 8;
			case BANK_DEPOSIT:
				return payloadLength >= 8;

			case SLEEPWORD_ENTERED:
				return payloadLength >= 1;

			case KNOWN_PLAYERS:
				return payloadLength >= 2;
		}
		System.out.println(String.format("Received UNHANDLED opcode %d from authentic claiming client", opcode));
		return false;
	}

	static {
		opcodes203.put(67, OpcodeIn.HEARTBEAT);
		opcodes203.put(16, OpcodeIn.WALK_TO_ENTITY);
		opcodes203.put(187, OpcodeIn.WALK_TO_POINT);
		opcodes203.put(31, OpcodeIn.CONFIRM_LOGOUT);
		opcodes203.put(102, OpcodeIn.LOGOUT);
		opcodes203.put(59, OpcodeIn.BLINK);
		opcodes203.put(29, OpcodeIn.COMBAT_STYLE_CHANGED);
		opcodes203.put(116, OpcodeIn.QUESTION_DIALOG_ANSWER);
		opcodes203.put(235, OpcodeIn.PLAYER_APPEARANCE_CHANGE);
		opcodes203.put(132, OpcodeIn.SOCIAL_ADD_IGNORE);
		opcodes203.put(195, OpcodeIn.SOCIAL_ADD_FRIEND);
		opcodes203.put(218, OpcodeIn.SOCIAL_SEND_PRIVATE_MESSAGE);
		opcodes203.put(167, OpcodeIn.SOCIAL_REMOVE_FRIEND);
		opcodes203.put(241, OpcodeIn.SOCIAL_REMOVE_IGNORE);
		opcodes203.put(176, OpcodeIn.DUEL_FIRST_ACCEPTED);
		opcodes203.put(33, OpcodeIn.DUEL_OFFER_ITEM);
		opcodes203.put(77, OpcodeIn.DUEL_SECOND_ACCEPTED);
		opcodes203.put(14, OpcodeIn.INTERACT_WITH_BOUNDARY);
		opcodes203.put(127, OpcodeIn.INTERACT_WITH_BOUNDARY2);
		opcodes203.put(180, OpcodeIn.CAST_ON_BOUNDARY);
		opcodes203.put(161, OpcodeIn.USE_WITH_BOUNDARY);
		opcodes203.put(153, OpcodeIn.NPC_TALK_TO);
		opcodes203.put(202, OpcodeIn.NPC_COMMAND);
		opcodes203.put(190, OpcodeIn.NPC_ATTACK);
		opcodes203.put(50, OpcodeIn.CAST_ON_NPC);
		opcodes203.put(135, OpcodeIn.NPC_USE_ITEM);
		opcodes203.put(229, OpcodeIn.PLAYER_CAST_PVP);
		opcodes203.put(113, OpcodeIn.PLAYER_USE_ITEM);
		opcodes203.put(171, OpcodeIn.PLAYER_ATTACK);
		opcodes203.put(103, OpcodeIn.PLAYER_DUEL);
		opcodes203.put(142, OpcodeIn.PLAYER_INIT_TRADE_REQUEST);
		opcodes203.put(165, OpcodeIn.PLAYER_FOLLOW);
		opcodes203.put(249, OpcodeIn.CAST_ON_GROUND_ITEM);
		opcodes203.put(53, OpcodeIn.GROUND_ITEM_USE_ITEM);
		opcodes203.put(91, OpcodeIn.ITEM_USE_ITEM);
		opcodes203.put(170, OpcodeIn.ITEM_UNEQUIP_FROM_INVENTORY);
		opcodes203.put(169, OpcodeIn.ITEM_EQUIP_FROM_INVENTORY);
		opcodes203.put(90, OpcodeIn.ITEM_COMMAND);
		opcodes203.put(246, OpcodeIn.ITEM_DROP);
		opcodes203.put(137, OpcodeIn.CAST_ON_SELF);
		opcodes203.put(158, OpcodeIn.CAST_ON_LAND);
		opcodes203.put(136, OpcodeIn.OBJECT_COMMAND);
		opcodes203.put(79, OpcodeIn.OBJECT_COMMAND2);
		opcodes203.put(99, OpcodeIn.CAST_ON_SCENERY);
		opcodes203.put(115, OpcodeIn.USE_ITEM_ON_SCENERY);
		opcodes203.put(166, OpcodeIn.SHOP_CLOSE);
		opcodes203.put(236, OpcodeIn.SHOP_BUY);
		opcodes203.put(221, OpcodeIn.SHOP_SELL);
		opcodes203.put(55, OpcodeIn.PLAYER_ACCEPTED_INIT_TRADE_REQUEST);
		opcodes203.put(230, OpcodeIn.PLAYER_DECLINED_TRADE);
		opcodes203.put(46, OpcodeIn.PLAYER_ADDED_ITEMS_TO_TRADE_OFFER);
		opcodes203.put(104, OpcodeIn.PLAYER_ACCEPTED_TRADE);
		opcodes203.put(60, OpcodeIn.PRAYER_ACTIVATED);
		opcodes203.put(254, OpcodeIn.PRAYER_DEACTIVATED);
		opcodes203.put(111, OpcodeIn.GAME_SETTINGS_CHANGED);
		opcodes203.put(216, OpcodeIn.CHAT_MESSAGE);
		opcodes203.put(38, OpcodeIn.COMMAND);
		opcodes203.put(64, OpcodeIn.PRIVACY_SETTINGS_CHANGED);
		opcodes203.put(206, OpcodeIn.REPORT_ABUSE);
		opcodes203.put(212, OpcodeIn.BANK_CLOSE);
		opcodes203.put(22, OpcodeIn.BANK_WITHDRAW);
		opcodes203.put(23, OpcodeIn.BANK_DEPOSIT);
		opcodes203.put(45, OpcodeIn.SLEEPWORD_ENTERED);
		opcodes203.put(0, OpcodeIn.LOGIN);
		opcodes203.put(1, OpcodeIn.LOGIN);
		opcodes203.put(4, OpcodeIn.CAST_ON_INVENTORY_ITEM);
		opcodes203.put(8, OpcodeIn.DUEL_FIRST_SETTINGS_CHANGED);
		opcodes203.put(197, OpcodeIn.DUEL_DECLINED);
		opcodes203.put(247, OpcodeIn.GROUND_ITEM_TAKE);
		opcodes203.put(163, OpcodeIn.KNOWN_PLAYERS);
	}
}
