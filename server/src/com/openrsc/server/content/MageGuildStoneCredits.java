package com.openrsc.server.content;

import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldTileBounds;

/** Durable Stone-conversion credits earned from the authored Mage Guild basement. */
public final class MageGuildStoneCredits {
	public static final String CREDITS_CACHE_KEY = "mage_guild_magic_zombie_credits";
	private static final int MAGIC_ZOMBIE_ID = 516;
	private static final WorldTileBounds ELIGIBLE_SPAWN_AREA = new WorldTileBounds(
		WorldLocation.global(new WorldCoordinate(604, 751, -1)),
		WorldLocation.global(new WorldCoordinate(620, 753, -1)));

	private MageGuildStoneCredits() {
	}

	public static boolean isEligibleMagicZombie(final Player player, final Npc npc) {
		return player != null
			&& npc != null
			&& player.getConfig().WANT_MYWORLD
			&& npc.getID() == MAGIC_ZOMBIE_ID
			&& npc.getAuthoredPlacementIdentity() != null
			&& ELIGIBLE_SPAWN_AREA.contains(npc.getLoc().toWorldStartLocation());
	}

	public static void awardEligibleKill(final Player player, final Npc npc) {
		if (!isEligibleMagicZombie(player, npc)) {
			return;
		}
		long updated = (long) getCredits(player) + 1L;
		player.getCache().set(CREDITS_CACHE_KEY,
			(int) Math.min(Integer.MAX_VALUE, updated));
	}

	public static int getCredits(final Player player) {
		if (player == null || !player.getCache().hasKey(CREDITS_CACHE_KEY)) {
			return 0;
		}
		return Math.max(0, player.getCache().getInt(CREDITS_CACHE_KEY));
	}

	public static boolean spendCredits(final Player player, final int amount) {
		if (player == null || amount <= 0) {
			return false;
		}
		int credits = getCredits(player);
		if (credits < amount) {
			return false;
		}
		player.getCache().set(CREDITS_CACHE_KEY, credits - amount);
		return true;
	}
}
