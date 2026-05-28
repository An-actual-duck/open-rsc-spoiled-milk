package com.openrsc.server.net.rsc.parsers.impl;

import com.openrsc.server.constants.Classes;
import com.openrsc.server.constants.Constants;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.Crypto;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.parsers.PayloadParser;
import com.openrsc.server.net.rsc.struct.AbstractStruct;
import com.openrsc.server.net.rsc.struct.incoming.*;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.StringUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * RSC Protocol-115 Parser of Incoming Packets to respective Protocol Independent Structs
 * **/
public class Payload115Parser implements PayloadParser<OpcodeIn> {
	@Override
	public OpcodeIn toOpcodeEnum(Packet packet, Player player) {
		OpcodeIn opcode = null;

		switch (packet.getID()) {
			case 5:
				opcode = OpcodeIn.HEARTBEAT;
				break;
			case 215:
				opcode = OpcodeIn.WALK_TO_ENTITY;
				break;
			case 255:
				opcode = OpcodeIn.WALK_TO_POINT;
				break;
			case 1:
				opcode = OpcodeIn.CONFIRM_LOGOUT;
				break;
			case 6:
				opcode = OpcodeIn.LOGOUT;
				break;

			case 231:
				opcode = OpcodeIn.COMBAT_STYLE_CHANGED;
				break;
			case 237:
				opcode = OpcodeIn.QUESTION_DIALOG_ANSWER;
				break;
			case 236:
				opcode = OpcodeIn.PLAYER_APPEARANCE_CHANGE;
				break;
			case 29:
				opcode = OpcodeIn.SOCIAL_ADD_IGNORE;
				break;
			case 26:
				opcode = OpcodeIn.SOCIAL_ADD_FRIEND;
				break;
			case 28:
				opcode = OpcodeIn.SOCIAL_SEND_PRIVATE_MESSAGE;
				break;
			case 27:
				opcode = OpcodeIn.SOCIAL_REMOVE_FRIEND;
				break;
			case 30:
				opcode = OpcodeIn.SOCIAL_REMOVE_IGNORE;
				break;
			case 199:
				opcode = OpcodeIn.DUEL_FIRST_ACCEPTED;
				break;
			case 201:
				opcode = OpcodeIn.DUEL_OFFER_ITEM;
				break;
			case 200:
				opcode = OpcodeIn.DUEL_FIRST_SETTINGS_CHANGED;
				break;
			case 203:
				opcode = OpcodeIn.DUEL_DECLINED;
				break;
			case 198:
				opcode = OpcodeIn.DUEL_SECOND_ACCEPTED;
				break;
			case 238:
				opcode = OpcodeIn.INTERACT_WITH_BOUNDARY;
				break;
			case 229:
				opcode = OpcodeIn.INTERACT_WITH_BOUNDARY2;
				break;
			case 252:
				opcode = OpcodeIn.GROUND_ITEM_TAKE;
				break;
			case 223:
				opcode = OpcodeIn.CAST_ON_BOUNDARY;
				break;
			case 239:
				opcode = OpcodeIn.USE_WITH_BOUNDARY;
				break;
			case 245:
				opcode = OpcodeIn.NPC_TALK_TO;
				break;
			case 244:
				opcode = OpcodeIn.NPC_ATTACK;
				break;
			case 225:
				opcode = OpcodeIn.CAST_ON_NPC;
				break;
			case 243:
				opcode = OpcodeIn.NPC_USE_ITEM;
				break;
			case 226:
				opcode = OpcodeIn.PLAYER_CAST_PVP;
				break;
			case 219:
				opcode = OpcodeIn.PLAYER_USE_ITEM;
				break;
			case 228:
				opcode = OpcodeIn.PLAYER_ATTACK;
				break;
			case 204:
				opcode = OpcodeIn.PLAYER_DUEL;
				break;
			case 235:
				opcode = OpcodeIn.PLAYER_INIT_TRADE_REQUEST;
				break;
			case 214:
				opcode = OpcodeIn.PLAYER_FOLLOW;
				break;
			case 224:
				opcode = OpcodeIn.CAST_ON_GROUND_ITEM;
				break;
			case 250:
				opcode = OpcodeIn.GROUND_ITEM_USE_ITEM;
				break;
			case 220:
				opcode = OpcodeIn.CAST_ON_INVENTORY_ITEM;
				break;
			case 240:
				opcode = OpcodeIn.ITEM_USE_ITEM;
				break;
			case 248:
				opcode = OpcodeIn.ITEM_UNEQUIP_FROM_INVENTORY;
				break;
			case 249:
				opcode = OpcodeIn.ITEM_EQUIP_FROM_INVENTORY;
				break;
			case 246:
				opcode = OpcodeIn.ITEM_COMMAND;
				break;
			case 251:
				opcode = OpcodeIn.ITEM_DROP;
				break;
			case 227:
				opcode = OpcodeIn.CAST_ON_SELF;
				break;
			case 221:
				opcode = OpcodeIn.CAST_ON_LAND;
				break;
			case 242:
				opcode = OpcodeIn.OBJECT_COMMAND;
				break;
			case 230:
				opcode = OpcodeIn.OBJECT_COMMAND2;
				break;
			case 222:
				opcode = OpcodeIn.CAST_ON_SCENERY;
				break;
			case 241:
				opcode = OpcodeIn.USE_ITEM_ON_SCENERY;
				break;
			case 218:
				opcode = OpcodeIn.SHOP_CLOSE;
				break;
			case 217:
				opcode = OpcodeIn.SHOP_BUY;
				break;
			case 216:
				opcode = OpcodeIn.SHOP_SELL;
				break;
			case 232:
				opcode = OpcodeIn.PLAYER_ACCEPTED_INIT_TRADE_REQUEST;
				break;
			case 233:
				opcode = OpcodeIn.PLAYER_DECLINED_TRADE;
				break;
			case 234:
				opcode = OpcodeIn.PLAYER_ADDED_ITEMS_TO_TRADE_OFFER;
				break;
			case 202:
				opcode = OpcodeIn.PLAYER_ACCEPTED_TRADE;
				break;
			case 212:
				opcode = OpcodeIn.PRAYER_ACTIVATED;
				break;
			case 211:
				opcode = OpcodeIn.PRAYER_DEACTIVATED;
				break;
			case 213:
				opcode = OpcodeIn.GAME_SETTINGS_CHANGED;
				break;
			case 3:
				opcode = OpcodeIn.CHAT_MESSAGE;
				break;
			case 7:
				opcode = OpcodeIn.COMMAND;
				break;
			case 31:
				opcode = OpcodeIn.PRIVACY_SETTINGS_CHANGED;
				break;
			case 207:
				opcode = OpcodeIn.BANK_CLOSE;
				break;
			case 206:
				opcode = OpcodeIn.BANK_WITHDRAW;
				break;
			case 205:
				opcode = OpcodeIn.BANK_DEPOSIT;
				break;

			case 0:
			case 19: //relogin
				opcode = OpcodeIn.LOGIN;
				break;
			case 2:
				opcode = OpcodeIn.REGISTER_ACCOUNT;
				break;
			case 4:
				opcode = OpcodeIn.FORGOT_PASSWORD;
				break;
			case 8:
				opcode = OpcodeIn.RECOVERY_ATTEMPT;
				break;
			case 25:
				opcode = OpcodeIn.CHANGE_PASS;
				break;
			case 208:
				opcode = OpcodeIn.SET_RECOVERY;
				break;

			case 17:
				opcode = OpcodeIn.SEND_DEBUG_INFO;
				break;
			case 254:
				opcode = OpcodeIn.KNOWN_PLAYERS;
				break;
			default:
				break;
		}

		return opcode;
	}

	@Override
	public AbstractStruct<OpcodeIn> parse(Packet packet, Player player) {

		OpcodeIn opcode = toOpcodeEnum(packet, player);
		AbstractStruct<OpcodeIn> result = null;

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
				Classes characterClass = null;
				int classIndex = packet.readByte();
				switch (classIndex) {
					case 0:
						characterClass = Classes.ADVENTURER;
						break;
					case 1:
						characterClass = Classes.WARRIOR;
						break;
					case 2:
						characterClass = Classes.WIZARD;
						break;
					case 3:
						characterClass = Classes.RANGER;
						break;
					case 4:
						characterClass = Classes.MINER;
						break;
				}
				pl.chosenClass = characterClass;
				result = pl;
				break;

			case QUESTION_DIALOG_ANSWER:
				MenuOptionStruct m = new MenuOptionStruct();
				m.option = packet.readByte();
				result = m;
				break;

			case CHAT_MESSAGE:
				ChatStruct cs = new ChatStruct();
				cs.message = read115RSCString(packet.readBytes(packet.getReadableBytes()));
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
					fs.message = read115RSCString(packet.readBytes(packet.getReadableBytes()));
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
				s1.stockAmount = packet.readShort();
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
			case SET_RECOVERY:
			case SET_DETAILS:
				SecuritySettingsStruct sec = new SecuritySettingsStruct();
				if (opcode == OpcodeIn.CHANGE_PASS) {
					// Get encrypted block
					// old + new password is always 40 characters long, with spaces at the end.
					// each blocks having encrypted 7 chars of password
					int blockLen;
					byte[] decBlock; // current decrypted block
					int session =  player.sessionId;
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
					int session =  player.sessionId;
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

		if (result != null) {
			result.setOpcode(opcode);
		}

		return result;

	}

	private String read115RSCString(byte[] data) {
		// TODO: refactor
		// good words
		String[] ygb = StringUtil.getGoodWords();
		char[] bhb = new char[]{'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ' ', '!', '?', '.', ',', ':', ';', '(', ')', '-', '&', '*', '\\', '\''};

		try {
			String var4 = "";
			String var5 = "";

			int var7;
			for(int var6 = 0; var6 < data.length; ++var6) {
				var7 = data[var6] & 255;
				if(var7 < 50) {
					var4 = var4 + bhb[var7];
				} else if(var7 < 70) {
					++var6;
					var4 = var4 + ygb[(var7 - 50) * 256 + (data[var6] & 255)] + " ";
				} else if(var7 < 90) {
					++var6;
					var4 = var4 + ygb[(var7 - 70) * 256 + (data[var6] & 255)];
				} else if(var7 < 255) {
					var4 = var4 + ygb[var7 - 90] + " ";
				} else {
					++var6;
					var7 = data[var6] & 255;
					if(var7 == 0) {
						var5 = "@red@";
					}

					if(var7 == 1) {
						var5 = "@gre@";
					}

					if(var7 == 2) {
						var5 = "@blu@";
					}

					if(var7 == 3) {
						var5 = "@cya@";
					}

					if(var7 == 4) {
						var5 = "@ran@";
					}

					if(var7 == 5) {
						var5 = "@whi@";
					}

					if(var7 == 6) {
						var5 = "@bla@";
					}

					if(var7 == 7) {
						var5 = "@ora@";
					}

					if(var7 == 8) {
						var5 = "@yel@";
					}

					if(var7 == 9) {
						var5 = "@mag@";
					}
				}
			}

			if(true) {
				for(var7 = 0; var7 < 2; ++var7) {
					String var8 = var4;
					var4 = hn(var4);
					if(var4.equals(var8)) {
						break;
					}
				}
			}

			if(var4.length() > 80) {
				var4 = var4.substring(0, 80);
			}

			var4 = var4.toLowerCase();
			String var12 = var5;
			boolean var13 = true;

			for(int var9 = 0; var9 < var4.length(); ++var9) {
				char var10 = var4.charAt(var9);
				if(var10 >= 97 && var10 <= 122 && var13) {
					var13 = false;
					var10 = (char)(var10 + 65 - 97);
				}

				if(var10 == 46 || var10 == 33 || var10 == 63) {
					var13 = true;
				}

				var12 = var12 + var10;
			}

			return var12;
		} catch (Exception var11) {
			var11.printStackTrace();
			return "eep!";
		}
	}

	private static String hn(String var0) {
		// TODO: refactor
		char[] ahb = new char[1000];
		// badwords
		String[] wgb = StringUtil.getBadWords();
		int vgb = 200;

		try {
			int var1 = var0.length();
			var0.toLowerCase().getChars(0, var1, ahb, 0);

			for(int var2 = 0; var2 < var1; ++var2) {
				char var3 = ahb[var2];

				for(int var4 = 0; var4 < vgb; ++var4) {
					String var5 = wgb[var4];
					char var6 = var5.charAt(0);
					if(dn(var6, var3, 0)) {
						int var7 = 1;
						int var8 = var5.length();
						char var9 = var5.charAt(1);
						int var10 = 0;
						if(var8 >= 6) {
							var10 = 1;
						}

						for(int var11 = var2 + 1; var11 < var1; ++var11) {
							char var12 = ahb[var11];
							if(dn(var9, var12, var8)) {
								++var7;
								if(var7 >= var8) {
									boolean var13 = false;

									for(int var14 = var2; var14 <= var11; ++var14) {
										if(var0.charAt(var14) >= 65 && var0.charAt(var14) <= 90) {
											var13 = true;
											break;
										}
									}

									if(!var13) {
										break;
									}

									String var15 = "";

									for(int var16 = 0; var16 < var0.length(); ++var16) {
										char var17 = var0.charAt(var16);
										if(var16 < var2 || var16 > var11 || var17 == 32 || var17 >= 97 && var17 <= 122) {
											var15 = var15 + var17;
										} else {
											var15 = var15 + "*";
										}
									}

									var0 = var15;
									break;
								}

								var6 = var9;
								var9 = var5.charAt(var7);
							} else if(!qn(var6, var12, var8)) {
								--var10;
								if(var10 < 0) {
									break;
								}
							}
						}
					}
				}
			}

			return var0;
		} catch (Exception var18) {
			return "wibble!";
		}
	}

	private static boolean dn(char paramChar1, char paramChar2, int paramInt)
	{
		if (paramChar1 == paramChar2) {
			return true;
		}
		if ((paramChar1 == 'i') && ((paramChar2 == 'y') || (paramChar2 == '1') || (paramChar2 == '!') || (paramChar2 == ':') || (paramChar2 == ';'))) {
			return true;
		}
		if ((paramChar1 == 's') && ((paramChar2 == '5') || (paramChar2 == 'z'))) {
			return true;
		}
		if ((paramChar1 == 'e') && (paramChar2 == '3')) {
			return true;
		}
		if ((paramChar1 == 'a') && (paramChar2 == '4')) {
			return true;
		}
		if ((paramChar1 == 'o') && ((paramChar2 == '0') || (paramChar2 == '*'))) {
			return true;
		}
		if ((paramChar1 == 'u') && (paramChar2 == 'v')) {
			return true;
		}
		if ((paramChar1 == 'c') && ((paramChar2 == '(') || (paramChar2 == 'k'))) {
			return true;
		}
		if ((paramChar1 == 'k') && ((paramChar2 == '(') || (paramChar2 == 'c'))) {
			return true;
		}
		if ((paramChar1 == 'w') && (paramChar2 == 'v')) {
			return true;
		}
		return (paramInt >= 4) && (paramChar1 == 'i') && (paramChar2 == 'l');
	}

	private static boolean qn(char paramChar1, char paramChar2, int paramInt)
	{
		if (paramChar1 == paramChar2) {
			return true;
		}
		if ((paramChar2 < 'a') || ((paramChar2 > 'u') && (paramChar2 != 'y'))) {
			return true;
		}
		if ((paramChar1 == 'i') && (paramChar2 == 'y')) {
			return true;
		}
		if ((paramChar1 == 'c') && (paramChar2 == 'k')) {
			return true;
		}
		if ((paramChar1 == 'k') && (paramChar2 == 'c')) {
			return true;
		}
		return (paramInt >= 5) && ((paramChar1 == 'a') || (paramChar1 == 'e') || (paramChar1 == 'i') || (paramChar1 == 'o') || (paramChar1 == 'u') || (paramChar1 == 'y')) && ((paramChar2 == 'a') || (paramChar2 == 'e') || (paramChar2 == 'i') || (paramChar2 == 'o') || (paramChar2 == 'u') || (paramChar2 == 'y'));
	}


	// a basic check is done on authentic opcodes against their possible lengths
	public static boolean isPossiblyValid(int opcode, int length, int protocolVer) {
		// no ISAAC in this version, don't need this
		return true;
	}
}
