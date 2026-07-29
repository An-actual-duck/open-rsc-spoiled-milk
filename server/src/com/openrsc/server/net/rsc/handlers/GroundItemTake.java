package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.action.WalkToPointAction;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.TargetPositionStruct;
import com.openrsc.server.plugins.triggers.TakeObjTrigger;

public class GroundItemTake implements PayloadProcessor<TargetPositionStruct, OpcodeIn> {
	public void process(TargetPositionStruct payload, Player player) throws Exception {
		if (!player.getConfig().WANT_MYWORLD && player.inCombat()) {
			player.message("You can't do that whilst you are fighting");
			return;
		}

		if (player.getDuel().isDueling()) {
			return;
		}

		if (player.isBusy()) {
			player.resetPath();
			return;
		}
		player.resetAll();

		final int x = payload.coordinate.getX();
		final int y = payload.coordinate.getY();
		if (x < 0 || y < 0) return;

		final Point location = Point.location(x, y);

		final int itemId = payload.itemId;
		if (itemId < 0 || itemId >= player.getWorld().getServer().getEntityHandler().getItemCount()) {
			return;
		}
		final int takeCount = Math.max(1, Math.min(Player.MAX_BULK_GROUND_ITEM_TAKE_COUNT, payload.takeCount));

		final GroundItem item = player.getViewArea().getVisibleGroundItem(itemId, location, player);

		if (item == null) {
			player.resetPath();
			return;
		}

		int distance = player.getWorld().getRegionManager()
			.findInteractionScenery(location, player) != null ? 1 : 0;
		Player onTile = player.getWorld().getRegionManager()
			.findInteractionPlayer(
				location.getX(), location.getY(), player, true);
		if (onTile != null && onTile.inCombat()) {
			distance = 1;
		}
		if (PathValidation.isMobBlocking(player, location.getX(), location.getY())) {
			distance = 1;
		}
		player.setWalkToAction(new WalkToPointAction(player, item.getLocation(), distance) {
			public void executeInternal() {
				if (!getPlayer().canTakeVisibleGroundItem(item)) {
					return;
				}

				getPlayer().resetAll();
				if (takeCount > 1) {
					item.setAttribute(Player.BULK_GROUND_ITEM_TAKE_COUNT_ATTRIBUTE, takeCount);
				}

				boolean blockedDefault = getPlayer().getWorld().getServer().getPluginHandler()
					.handlePlugin(TakeObjTrigger.class, getPlayer(), new Object[]{getPlayer(), item}, this);
				if (blockedDefault) {
					item.removeAttribute(Player.BULK_GROUND_ITEM_TAKE_COUNT_ATTRIBUTE);
				}
			}
		});
	}
}
