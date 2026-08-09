package com.openrsc.server.plugins.authentic.quests.members.undergroundpass.mechanism;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.plugins.triggers.UseInvTrigger;
import com.openrsc.server.plugins.triggers.UseLocTrigger;
import com.openrsc.server.util.rsc.DataConversions;

import static com.openrsc.server.plugins.Functions.*;

public class UndergroundPassMechanismMap1 implements UseInvTrigger, UseLocTrigger {

	/**
	 * OBJECT IDs
	 **/
	private static int OLD_BRIDGE = 726;
	private static int STALACTITE_1 = 771;
	private static int STALACTITE_2 = 798;
	private static int SWAMP_CROSS = 754;
	private static int BLESSED_SPIDER_SWAMP_CROSS = 795;
	private static final int UNDERGROUND_PASS_LEVEL = -1;
	private static final int BLESSED_SPIDER_SWAMP_CROSSING_Y = 586;


	@Override
	public boolean blockUseInv(Player player, Integer invIndex, Item item1, Item item2) {
		String itemArrow1 = item1.getDef(player.getWorld()).getName().toLowerCase();
		String itemArrow2 = item2.getDef(player.getWorld()).getName().toLowerCase();
		return (item1.getCatalogId() == ItemId.DAMP_CLOTH.id() && itemArrow2.contains("arrows"))
				|| (itemArrow1.contains("arrows") && item2.getCatalogId() == ItemId.DAMP_CLOTH.id());
	}

	@Override
	public void onUseInv(Player player, Integer invIndex, Item item1, Item item2) {
		String itemArrow1 = item1.getDef(player.getWorld()).getName().toLowerCase();
		String itemArrow2 = item2.getDef(player.getWorld()).getName().toLowerCase();
		if ((item1.getCatalogId() == ItemId.DAMP_CLOTH.id() && itemArrow2.contains("arrows"))
				|| (itemArrow1.contains("arrows") && item2.getCatalogId() == ItemId.DAMP_CLOTH.id())) {
			int idArrow = itemArrow2.contains("arrows") ? item2.getCatalogId() : item1.getCatalogId();
			player.message("you wrap the damp cloth around the arrow head");
			player.getCarriedItems().remove(new Item(ItemId.DAMP_CLOTH.id()));
			player.getCarriedItems().remove(new Item(idArrow));
			give(player, ItemId.ARROW.id(), 1);
		}
	}

	@Override
	public boolean blockUseLoc(Player player, GameObject obj, Item item) {
		return (item.getCatalogId() == ItemId.ARROW.id() && obj.getID() == 97)
				|| (item.getCatalogId() == ItemId.LIT_ARROW.id() && obj.getID() == OLD_BRIDGE)
				|| (item.getCatalogId() == ItemId.ROPE.id() && (obj.getID() == STALACTITE_1 || obj.getID() == STALACTITE_2 || obj.getID() == STALACTITE_2 + 1))
				|| (item.getCatalogId() == ItemId.ROCKS.id() && (obj.getID() == SWAMP_CROSS || isBlessedSpiderSwampCrossing(obj)));
	}

	@Override
	public void onUseLoc(Player player, GameObject obj, Item item) {
		if (item.getCatalogId() == ItemId.ARROW.id() && obj.getID() == 97) {
			player.message("you light the cloth wrapped arrow head");
			player.getCarriedItems().remove(new Item(ItemId.ARROW.id()));
			give(player, ItemId.LIT_ARROW.id(), 1);
		}
		else if (item.getCatalogId() == ItemId.LIT_ARROW.id() && obj.getID() == OLD_BRIDGE) {
			if (hasABow(player)) {
				player.getCarriedItems().remove(new Item(ItemId.LIT_ARROW.id()));
				if ((getCurrentLevel(player, Skill.RANGED.id()) < 25) || (player.getY() != 3417 && player.getX() < 701)) {
					mes("you fire the lit arrow at the bridge");
					delay(3);
					mes("it burns out and has little effect");
					delay(3);
				} else {
					mes("you fire your arrow at the rope supporting the bridge");
					delay(3);
					if (DataConversions.getRandom().nextInt(5) == 1) {
						player.message("the arrow just misses the rope");
					} else {
						if (player.getQuestStage(Quests.UNDERGROUND_PASS) == 2) {
							player.updateQuestStage(Quests.UNDERGROUND_PASS, 3);
						}
						mes("the arrow impales the wooden bridge, just below the rope support");
						delay(3);
						mes("the rope catches alight and begins to burn");
						delay(3);
						mes("the bridge swings down creating a walkway");
						delay(3);
						player.getWorld().replaceGameObject(obj,
							new GameObject(obj.getWorld(), obj.getLocation(), 727, obj.getDirection(), obj
								.getType()));
						player.getWorld().delayedSpawnObject(obj.getLoc(), 10000);
						player.teleport(702, 3420);
						delay(2);
						player.teleport(706, 3420);
						delay();
						player.teleport(709, 3420);
						player.message("you rush across the bridge");
					}
				}
			} else {
				player.message("first you'll need a bow");
			}
		}
		else if (item.getCatalogId() == ItemId.ROPE.id() && (obj.getID() == STALACTITE_1 || obj.getID() == STALACTITE_2 || obj.getID() == STALACTITE_2 + 1)) {
			mes("you lasso the rope around the stalactite");
			delay(3);
			mes("and pull yourself up");
			delay(3);
			if (obj.getID() == STALACTITE_1) {
				player.teleport(695, 3435);
			} else if (obj.getID() == STALACTITE_2) {
				player.teleport(677, 3435);
			} else if (obj.getID() == STALACTITE_2 + 1) {
				player.teleport(682, 3436);
			}
			player.message("you climb from stalactite to stalactite and over the rocks");
		}
		else if (item.getCatalogId() == ItemId.ROCKS.id() && obj.getID() == SWAMP_CROSS) {
			mes("you throw the rocks onto the swamp");
			delay(3);
			player.message("and carefully tread from one to another");
			player.getCarriedItems().remove(new Item(ItemId.ROCKS.id()));
			registerLegacySteppingStone(player, Point.location(697, 3441), 2);
			if (player.getX() <= 695) {
				teleportUnderground(player, 698, 609);
				delay(2);
				teleportUnderground(player, 700, 609);
			} else {
				teleportUnderground(player, 698, 609);
				delay(2);
				teleportUnderground(player, 695, 609);
			}
		}
		else if (item.getCatalogId() == ItemId.ROCKS.id() && isBlessedSpiderSwampCrossing(obj)) {
			mes("you throw the rocks onto the swamp");
			delay(3);
			player.message("and carefully tread from one to another");
			player.getCarriedItems().remove(new Item(ItemId.ROCKS.id()));
			registerLegacySteppingStone(player, Point.location(714, 3418), 0);
			if (player.getWorldLocation().getCoordinate().getY() >= BLESSED_SPIDER_SWAMP_CROSSING_Y) {
				teleportUnderground(player, 715, 584);
			} else {
				teleportUnderground(player, 713, 588);
			}
		}
	}

	private static boolean isBlessedSpiderSwampCrossing(final GameObject obj) {
		if (obj.getID() != BLESSED_SPIDER_SWAMP_CROSS) {
			return false;
		}
		final WorldCoordinate coordinate = obj.getWorldLocation().getCoordinate();
		return coordinate.getLevel() == UNDERGROUND_PASS_LEVEL
			&& coordinate.getY() == BLESSED_SPIDER_SWAMP_CROSSING_Y
			&& (coordinate.getX() == 714 || coordinate.getX() == 715);
	}

	private static void teleportUnderground(final Player player, final int x, final int y) {
		player.teleport(x, y, UNDERGROUND_PASS_LEVEL, false);
	}

	/**
	 * The temporary stepping stone has only ever been a legacy visual. Native
	 * layered packages must not register a packed-Y object into a separate
	 * legacy region; the traversal itself uses explicit layered destinations.
	 */
	private static void registerLegacySteppingStone(final Player player, final Point location, final int direction) {
		if (player.getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY) {
			return;
		}
		final GameObject object = new GameObject(player.getWorld(), location, 774, direction, 0);
		player.getWorld().registerGameObject(object);
		player.getWorld().delayedRemoveObject(object, 10000);
	}

	private boolean hasABow(Player player) {
		synchronized(player.getCarriedItems().getInventory().getItems()) {
			for (Item bow : player.getCarriedItems().getInventory().getItems()) {
				String bowName = bow.getDef(player.getWorld()).getName().toLowerCase();
				if (bowName.contains("bow")) {
					return true;
				}
			}
			return false;
		}
	}
}
