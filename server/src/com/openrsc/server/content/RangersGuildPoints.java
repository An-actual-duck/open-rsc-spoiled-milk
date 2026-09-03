package com.openrsc.server.content;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

public final class RangersGuildPoints {
	public static final String POINTS_CACHE_KEY = "rangers_guild_points";
	public static final int GIANT_POINTS = 7;
	public static final int SKELETON_POINTS = 12;
	public static final int LESSER_DEMON_POINTS = 16;
	public static final int GREEN_DRAGON_POINTS = 22;

	private RangersGuildPoints() {
	}

	public static boolean isInBasement(Player player) {
		return player != null
			&& player.getConfig().WANT_MYWORLD
			&& RangersGuildArea.containsBasement(player.getWorldLocation());
	}

	public static int pointsForNpc(final int npcId) {
		switch (npcId) {
			case 61:
				return GIANT_POINTS;
			case 195:
				return SKELETON_POINTS;
			case 22:
				return LESSER_DEMON_POINTS;
			default:
				return npcId == NpcId.GREEN_DRAGON.id() ? GREEN_DRAGON_POINTS : 0;
		}
	}

	public static boolean isEligibleBasementEnemy(final Player player, final Npc npc) {
		return player != null
			&& npc != null
			&& player.getConfig().WANT_MYWORLD
			&& pointsForNpc(npc.getID()) > 0
			&& npc.getAuthoredPlacementIdentity() != null
			&& RangersGuildArea.containsBasement(npc.getLoc().toWorldStartLocation());
	}

	public static void awardEligibleRangedKill(final Player player, final Npc npc) {
		if (!isEligibleBasementEnemy(player, npc) || !npc.hasRangedDamageBy(player)) {
			return;
		}
		addPoints(player, pointsForNpc(npc.getID()));
	}

	/**
	 * Binary-compatibility bridge for the installed World Builder runtime.
	 *
	 * <p>The provider's shadow copy of {@code Skills} predates the current
	 * Rangers Guild design and still invokes this signature after every
	 * experience award. Current gameplay awards points for eligible ranged
	 * kills instead, so the compatible behavior is intentionally a no-op. Keep
	 * this method until the provider no longer shadows target-owned server
	 * classes.</p>
	 */
	@Deprecated
	public static void awardFromExperience(Player player, int skill, int experience) {
		// Intentionally empty: current Rangers Guild points are kill-based.
	}

	public static int getPoints(Player player) {
		return getCacheInt(player, POINTS_CACHE_KEY);
	}

	public static void addPoints(Player player, int points) {
		if (player == null || points <= 0) {
			return;
		}
		long updatedPoints = (long) getPoints(player) + points;
		setPoints(player, (int) Math.min(Integer.MAX_VALUE, updatedPoints));
	}

	public static boolean spendPoints(Player player, int points) {
		if (player == null || points <= 0) {
			return false;
		}

		int currentPoints = getPoints(player);
		if (currentPoints < points) {
			return false;
		}

		setPoints(player, currentPoints - points);
		return true;
	}

	private static void setPoints(Player player, int points) {
		player.getCache().set(POINTS_CACHE_KEY, Math.max(0, points));
	}

	private static int getCacheInt(Player player, String key) {
		if (player == null || !player.getCache().hasKey(key)) {
			return 0;
		}
		return Math.max(0, player.getCache().getInt(key));
	}
}
