package com.openrsc.server.plugins.authentic.defaults;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.worldedit.WorldEditorSessionManager;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.TelePoint;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.LavaForgeLocation;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.ZanarisLocation;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.custom.minigames.ALumbridgeCarol;
import com.openrsc.server.plugins.custom.minigames.CombatOdyssey;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;
import com.openrsc.server.util.rsc.MessageType;

import java.util.Optional;

import static com.openrsc.server.plugins.Functions.*;


public class Ladders {

	public boolean blockObjectAction(GameObject obj, String command,
									 Player player) {
		return (command.equals("climb-down") || command.equals("go down") || command
			.equals("climb down"))
			|| command.equals("climb-up")
			|| command.equals("go up")
			|| command.equals("pull");
	}

	public void onObjectAction(GameObject obj, String command, Player player) {
		if (tryWorldBuilderVerticalPair(obj, command, player)) {
			return;
		}
		if (obj.getID() == 487 && !config().MEMBER_WORLD) {
			player.message(player.MEMBER_MESSAGE);
			return;
		} else if (obj.getID() == 79
			&& matchesLegacyPackedLocation(obj, 243, 95)) {
			player.message("Are you sure you want to go down to this lair?");
			int menu = multi(player, "Yes I take the risk!", "No stay up here.");
			if (menu == 0) {
				player.message("You climb down the manhole and land in a water lair");
				player.teleportLegacy(98, 2931, false);
			} else if (menu == 1) {
				player.message("You decide to stay.");
			}
			//player.message("The new dungeon is available in a couple of minutes");
			//player.message("We are doing the decoration, please stay tuned.");
			return;
		} else if (obj.getID() == 5
			&& (matchesLegacyPackedLocation(obj, 98, 2930)
				|| matchesLegacyPackedLocation(obj, 137, 2932))) {
			player.teleportLegacy(243, 96, false);
			player.message("You climb up the ladder");
			return;
		} else if (obj.getID() == 629) {
			player.teleportLegacy(576, 3580, false);
			player.message("You go up the stairs");
			return;
		} else if (obj.getID() == 621) {
			player.teleportLegacy(606, 3556, false);
			player.message("You go up the stairs");
			return;
		} else if (obj.getID() == 223
			&& (LavaForgeLocation.isDwarvenMineDownLadder(
					obj.getWorldLocation())
				|| matchesLegacyPackedLocation(obj, 271, 3340))) {
			//Ladder from dwarven mine to lava forge
			if (player.getCache().hasKey("miniquest_dwarf_youth_rescue")) {
				if (player.getWorld().getRegionManager()
					.hasNativeLayeredTerrain(
						LavaForgeLocation.entrance())) {
					player.teleportLayered(
						LavaForgeLocation.entrance(), false);
				} else {
					player.teleportLegacy(329, 3419, false);
				}
			} else
				player.message("you don't have access to this area");
			return;
		} else if (obj.getID() == 5
			&& (matchesLegacyPackedLocation(obj, 329, 3418)
				|| LavaForgeLocation.isExitLadder(
					obj.getWorldLocation()))) {
			//Ladder from lava forge to dwarven mine
			if (LavaForgeLocation.isExitLadder(
					obj.getWorldLocation())) {
				player.teleportLayered(
					LavaForgeLocation.dwarvenMineReturn(), false);
			} else {
				player.teleportLegacy(271, 3339, false);
			}
			return;
		} else if (obj.getID() == 42
			&& matchesLegacyPackedLocation(obj, 368, 438)) {
			player.message("You go down the stairs");
			player.teleportLegacy(371, 3266, false);
			return;
		} else if (obj.getID() == 41) {
			if (matchesLegacyPackedLocation(obj, 370, 3264)) {
				player.message("You go up the stairs");
				player.teleportLegacy(369, 437, false);
				return;
			} else if (matchesLegacyPackedLocation(obj, 516, 1479)) {
				// Legend's Guild second floor stairs up
				player.message("You go up the stairs");
				player.teleportLegacy(516, 2426, false);
				if (player.getConfig().WANT_COMBAT_ODYSSEY) {
					if (CombatOdyssey.getIntroStage(player) == CombatOdyssey.TALKED_TO_RADIMUS) {
						CombatOdyssey.meetBiggum(player);
					} else if (CombatOdyssey.getIntroStage(player) != CombatOdyssey.NOT_STARTED
						&& CombatOdyssey.getPrestige(player) < 1
						&& !player.getCarriedItems().hasCatalogID(ItemId.BIGGUM_FLODROT.id())
						&& !player.getBank().hasItemId(ItemId.BIGGUM_FLODROT.id())) {
						CombatOdyssey.recoverBiggum(player);
					}
				}
				return;
			} else if (matchesLegacyPackedLocation(obj, 316, 546)) {
				// Rising Sun Inn (Falador) stairs up
				if (player.getConfig().A_LUMBRIDGE_CAROL) {
					int stage = ALumbridgeCarol.getStage(player);
					if (stage < ALumbridgeCarol.PARTY_TIME && stage != ALumbridgeCarol.COMPLETED) {
						Npc barmaid = ifnearvisnpc(player, NpcId.BARMAID.id(), 8);
						if (barmaid != null) {
							npcsay(player, barmaid, "You can't go up there right now",
								"We're preparing for a private event");
						} else {
							player.message("There is a sign that reads:");
							player.message("\"No entry. Preparing for a private event\"");
						}
						return;
					}
				}
				player.message("You go up the stairs");
				player.teleportLegacy(316, 1493, false);
				return;
			}
		}

		TelePoint telePoint = player.getWorld().getServer().getEntityHandler()
			.getObjectTelePoint(obj.getWorldLocation(), command);
		if (telePoint != null) {
			player.teleport(telePoint.getX(), telePoint.getY(), false);
		} else if (obj.getID() == 487) {
			player.message("You pull the lever");
			player.teleportLegacy(567, 3330, false);
			delay();
			if (player.getX() == 567 && player.getY() == 3330) {
				displayTeleportBubble(player, player.getX(), player.getY(), false);
			}
		} else if (obj.getID() == 488) {
			player.message("You pull the lever");
			player.teleportLegacy(282, 3019, false);
			delay();
			if (player.getX() == 282 && player.getY() == 3019) {
				displayTeleportBubble(player, player.getX(), player.getY(), false);
			}
		} else if (obj.getID() == 349) {
			player.playerServerMessage(MessageType.QUEST, "You pull the lever");
			player.teleportLegacy(621, 596, false);
			delay();
			if (player.getX() == 621 && player.getY() == 596) {
				displayTeleportBubble(player, player.getX(), player.getY(), false);
			}
		} else if (obj.getID() == 348) {
			boolean skip = player.getCache().hasKey("hide_wild_lever_warn")
					&& player.getCache().getBoolean("hide_wild_lever_warn");
			boolean teleport = false;
			if (!skip) {
				player.playerServerMessage(MessageType.QUEST, "warning pulling this lever will teleport you deep into the wilderness");
				player.playerServerMessage(MessageType.QUEST, "Are you sure you wish to pull it?");
				int menu = multi(player, "Yes I'm brave", "Eeep the wilderness no thankyou", "Yes please, don't show this message again");
				if (menu == 0 || menu == 2) {
					if (menu == 2) player.getCache().store("hide_wild_lever_warn", true);
					teleport = true;
				}
			}
			if (skip || teleport) {
				player.message("you pull the lever");
				player.teleportLegacy(180, 128, false);
				displayTeleportBubble(player, player.getX(), player.getY(), false);
				delay();
				if (player.getX() == 180 && player.getY() == 128) {
					displayTeleportBubble(player, player.getX(), player.getY(), false);
				}
			}
		} else if (obj.getID() == 776) {
			if (player.getCarriedItems().hasCatalogID(ItemId.PARAMAYA_REST_TICKET.id(), Optional.of(false))) {
				player.getCarriedItems().remove(new Item(ItemId.PARAMAYA_REST_TICKET.id()));
				player.message("The barman takes your ticket and allows you up to");
				player.message("the dormitory.");
				player.teleportLegacy(395, 2713, false);
				player.message("You climb up the ladder");
			} else {
				Npc kaleb = ifnearvisnpc(player, NpcId.KALEB.id(), 10);
				if (kaleb != null) {
					player.message("You need a ticket to access the dormitory");
					npcsay(player, kaleb, "You can buy a ticket to the dormitory from me.",
						"And have a lovely nights rest.");
				} else {
					player.message("Kaleb is busy at the moment.");
				}
			}
		} else if (obj.getID() == 198
			&& matchesLegacyPackedLocation(obj, 251, 468)) { // Prayer
			// Guild
			// Ladder
			if (!player.getCache().hasKey("prayer_guild")) {
				Npc abbot = player.getWorld().getNpc(NpcId.ABBOT_LANGLEY.id(), 249, 252, 458, 468);
				if (abbot != null) {
					npcsay(player, abbot, "Only members of our order can go up there");
					int op = multi(player, abbot, false, "Well can i join your order?",
						"Oh sorry");
					if (op == 0) {
						say(player, abbot, "Well can I join your order?");
						if (getCurrentLevel(player, Skill.PRAYER.id()) >= 31) {
							npcsay(player, abbot, "Ok I see you are someone suitable for our order",
								"You may join");
							player.getCache().set("prayer_guild", 1);
							player.teleportLegacy(251, 1411, false);
							player.message("You climb up the ladder");
						} else {
							npcsay(player, abbot, "No I feel you are not devout enough");
							delay(2);
							player.message("You need a Worship level of 31");
						}
					} else if (op == 1) {
						say(player, abbot, "Oh Sorry");
					}
				} else {
					player.message("Abbot Langley is busy at the moment.");
				}
			} else {
				player.teleportLegacy(251, 1411, false);
				player.message("You climb up the ladder");
			}
		} else if (obj.getID() == 223
			&& matchesLegacyPackedLocation(obj, 274, 566)) { // Mining
			// Guild
			// Ladder
			if (getCurrentLevel(player, Skill.MINING.id()) < 60) {
				Npc dwarf = player.getWorld().getNpc(NpcId.DWARF_MINING_GUILD.id(), 272, 277, 563, 567);
				if (dwarf != null) {
					npcYell(player, dwarf,
						"Sorry only the top miners are allowed in there");
				}
				delay(2);
				player.message("You need a mining level of 60 to enter");
			} else {
				player.teleportLegacy(274, 3397, false);
			}
		} else if (obj.getID() == 199) { // ladder to black hole
			if (!player.getCarriedItems().hasCatalogID(ItemId.DISK_OF_RETURNING.id(), Optional.of(false))) {
				mes("you seem to be missing a disk to use the ladder");
				delay(3);
			} else {
				mes("You climb down the ladder");
				delay(2);
				int offX = DataConversions.random(0,4) - 2;
				int offY = DataConversions.random(0,4) - 2;
				player.teleportLegacy(305 + offX, 3300 + offY, false);
				ActionSender.sendPlayerOnBlackHole(player);
			}
		} else if (obj.getID() == 342
			&& matchesLegacyPackedLocation(obj, 611, 601)) {
			Npc paladinGuard = ifnearvisnpc(player, NpcId.PALADIN.id(), 4);
			if (paladinGuard != null) {
				npcYell(player, paladinGuard, "Stop right there");
				paladinGuard.setChasing(player);
				delay(2);
				if (player.inCombat()) {
					return;
				}
			}
			teleportVertical(player, true, obj);
			player.message(
				"You " + command.replace("-", " ") + " the "
					+ obj.getGameObjectDef().getName().toLowerCase());
		} else if (isZanarisExitLadder(obj)) {
			boolean relocated =
				ZanarisLocation.isAt(
					obj.getWorldLocation(),
					ZanarisLocation.EXIT_LADDER_X,
					ZanarisLocation.EXIT_LADDER_Y);
			int attendantY = relocated
				? ZanarisLocation.EXIT_LADDER_Y
				: 3537;
			Npc ladderAttendant =
				findNpcInPlayerDomain(
					player,
					NpcId.FAIRY_LADDER_ATTENDANT.id(),
					99,
					99,
					attendantY,
					attendantY);
			if (ladderAttendant != null) {
				npcsay(player, ladderAttendant, "This ladder leaves Zanaris",
					"It leads to near Al Kharid in your mortal realm",
					"You won't be able to return this way",
					"Are you sure you have sampled your fill of delights from our market?");
				int m = multi(player, ladderAttendant, "I think I'll stay down here a bit longer", "Yes, I'm ready to leave");
				if (m == 1) {
					player.message("You climb up the ladder");
					if (relocated
						&& player.isLayeredLocationAuthorityEnabled()) {
						player.teleportLayered(
							ZanarisLocation.surfaceExit(),
							false);
					} else {
						player.teleportLegacy(98, 706, false);
					}
				}
			}
		} else if (obj.getID() == 1187
			&& matchesLegacyPackedLocation(obj, 446, 3367)) {
			player.teleportLegacy(222, 110, false);
		} else if (obj.getID() == 331
			&& matchesLegacyPackedLocation(obj, 150, 558)) {
			player.teleportLegacy(151, 1505, false);
		} else if (obj.getID() == 6
			&& matchesLegacyPackedLocation(obj, 282, 185)
			&& !config().MEMBER_WORLD) {
			player.message(player.MEMBER_MESSAGE);
		} else if (obj.getID() == 6
			&& matchesLegacyPackedLocation(obj, 148, 1507)) {
			player.teleportLegacy(148, 563, false);
		} else if (command.equals("climb-up") || command.equals("climb up")
			|| command.equals("go up")) {
			teleportVertical(player, true, obj);
			player.message(
				"You " + command.replace("-", " ") + " the "
					+ obj.getGameObjectDef().getName().toLowerCase());
		} else if (command.equals("climb-down") || command.equals("climb down")
			|| command.equals("go down")) {
			teleportVertical(player, false, obj);
			player.message(
				"You " + command.replace("-", " ") + " the "
					+ obj.getGameObjectDef().getName().toLowerCase());
		}
	}

	private static boolean isZanarisExitLadder(GameObject object) {
		return object.getID() == 249
			&& (matchesLegacyPackedLocation(object, 98, 3537)
				|| ZanarisLocation.isAt(
					object.getWorldLocation(),
					ZanarisLocation.EXIT_LADDER_X,
					ZanarisLocation.EXIT_LADDER_Y));
	}

	/**
	 * Compares a classic script coordinate without treating a native layered
	 * object's unpacked Y value as the old packed-Y carrier.
	 */
	private static boolean matchesLegacyPackedLocation(
		final GameObject object,
		final int x,
		final int packedY) {
		try {
			Point legacyLocation = LegacyPackedPointAdapter.toLegacyPoint(
				object.getWorldLocation());
			return legacyLocation.getX() == x
				&& legacyLocation.getY() == packedY;
		} catch (IllegalArgumentException | IllegalStateException
			unrepresentableLocation) {
			return false;
		}
	}

	private static Npc findNpcInPlayerDomain(
		Player player,
		int npcId,
		int minimumX,
		int maximumX,
		int minimumY,
		int maximumY) {
		for (Npc npc : player.getWorld().getNpcs()) {
			if (!npc.isRemoved()
				&& !npc.isRespawning()
				&& npc.getID() == npcId
				&& npc.getX() >= minimumX
				&& npc.getX() <= maximumX
				&& npc.getY() >= minimumY
				&& npc.getY() <= maximumY
				&& npc.sharesSpatialDomain(player)) {
				return npc;
			}
		}
		return null;
	}

	private boolean tryWorldBuilderVerticalPair(
		GameObject object,
		String command,
		Player player) {
		boolean up = command.equals("climb-up")
			|| command.equals("climb up")
			|| command.equals("go up");
		boolean down = command.equals("climb-down")
			|| command.equals("climb down")
			|| command.equals("go down");
		if (!up && !down) {
			return false;
		}
		int[] coordinates = coordModifier(
			player, up, object, false);
		try {
			WorldEditorSessionManager.NativeVerticalPairResult result =
				player.getWorld().getServer().getWorldEditorSessions()
					.prepareNativeVerticalPair(
						player,
						object,
						coordinates[0],
						coordinates[1],
						up ? 1 : -1);
			if (!result.applicable) {
				return false;
			}
			player.teleportLayered(result.destination, false);
			player.message(
				"You " + command.replace("-", " ") + " the "
					+ object.getGameObjectDef().getName().toLowerCase());
			if (result.createdInverse) {
				player.message(
					"Builder: created the paired "
						+ (up ? "down" : "up")
						+ " object on level "
						+ result.destination.getCoordinate().getLevel()
						+ ".");
			}
			return true;
		} catch (IllegalArgumentException | IllegalStateException failure) {
			player.message(
				"Builder vertical pairing: " + failure.getMessage());
			return true;
		}
	}

	private void teleportVertical(
		Player player,
		boolean up,
		GameObject object) {
		boolean nativeLayered = player.getConfig()
				.WANT_LAYERED_PLAYER_LOCATION_AUTHORITY
			&& player.getWorld().getRegionManager()
				.hasNativeLayeredTerrain(player.getWorldLocation());
		int[] coords = coordModifier(player, up, object, !nativeLayered);
		if (nativeLayered) {
			player.teleportRelativeLayer(
				coords[0], coords[1], up ? 1 : -1, false);
		} else {
			player.teleport(coords[0], coords[1], false);
		}
	}

	private int[] coordModifier(Player player, boolean up, GameObject object) {
		return coordModifier(player, up, object, true);
	}

	private int[] coordModifier(
		Player player,
		boolean up,
		GameObject object,
		boolean encodeLegacyPlane) {
		if (object.getGameObjectDef().getHeight() <= 1) {
			return new int[]{player.getX(),
				encodeLegacyPlane
					? Formulae.getNewY(player.getY(), up)
					: player.getY()};
		}
		int[] coords = {
			object.getX(),
			encodeLegacyPlane
				? Formulae.getNewY(object.getY(), up)
				: object.getY()};
		switch (object.getDirection()) {
			case 0:
				coords[1] -= (up ? -object.getGameObjectDef().getHeight() : 1);
				break;
			case 2:
				coords[0] -= (up ? -object.getGameObjectDef().getHeight() : 1);
				break;
			case 4:
				coords[1] += (up ? -1 : object.getGameObjectDef().getHeight());
				break;
			case 6:
				coords[0] += (up ? -1 : object.getGameObjectDef().getHeight());
				break;
		}
		return coords;
	}

}
