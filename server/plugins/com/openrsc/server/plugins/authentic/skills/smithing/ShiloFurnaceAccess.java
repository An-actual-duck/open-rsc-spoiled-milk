package com.openrsc.server.plugins.authentic.skills.smithing;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

import java.util.Optional;

import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;

public final class ShiloFurnaceAccess {
	public static final Point FURNACE_LOCATION = Point.location(399, 840);
	public static final String UNLOCK_CACHE_KEY = "myworld_shilo_furnace_unlocked";

	private ShiloFurnaceAccess() {
	}

	public static boolean isShiloFurnace(GameObject object) {
		return object != null && FURNACE_LOCATION.equals(object.getLocation());
	}

	public static boolean isUnlocked(Player player) {
		return player.getCache().hasKey(UNLOCK_CACHE_KEY)
			&& player.getCache().getBoolean(UNLOCK_CACHE_KEY);
	}

	public static boolean ensureUnlocked(Player player) {
		if (isUnlocked(player)) {
			return true;
		}
		player.message("Yohnus asks for one cut red topaz to unlock permanent furnace access.");
		if (!hasCutTopaz(player)) {
			player.message("You need an unnoted cut red topaz.");
			return false;
		}
		int option = multi(player, "Give Yohnus one cut red topaz", "Not now");
		if (option != 0) {
			return false;
		}
		return consumeTopazAndUnlock(player);
	}

	public static void explainAndOffer(Player player, Npc yohnus) {
		if (isUnlocked(player)) {
			npcsay(player, yohnus, "You have permanent access to the furnace, Bwana.");
			return;
		}
		npcsay(player, yohnus,
			"The furnace is available for a one-time contribution.",
			"Bring me one cut red topaz and you may use it permanently.");
		if (!hasCutTopaz(player)) {
			npcsay(player, yohnus, "Come back when you have an unnoted cut red topaz.");
			return;
		}
		int option = multi(player, yohnus, false,
			"Give him one cut red topaz",
			"No thanks");
		if (option == 0 && consumeTopazAndUnlock(player)) {
			npcsay(player, yohnus, "Thanks Bwana!", "You may use the furnace whenever you like.");
		}
	}

	private static boolean hasCutTopaz(Player player) {
		return player.getCarriedItems().getInventory()
			.countId(ItemId.RED_TOPAZ.id(), Optional.of(false)) > 0;
	}

	private static boolean consumeTopazAndUnlock(Player player) {
		if (player.getCarriedItems().remove(new Item(ItemId.RED_TOPAZ.id(), 1)) < 0) {
			player.message("You no longer have an unnoted cut red topaz.");
			return false;
		}
		player.getCache().store(UNLOCK_CACHE_KEY, true);
		player.message("The cut red topaz is accepted. You now have permanent furnace access.");
		return true;
	}
}
