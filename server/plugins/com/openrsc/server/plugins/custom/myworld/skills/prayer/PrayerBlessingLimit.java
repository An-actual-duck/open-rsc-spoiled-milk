package com.openrsc.server.plugins.custom.myworld.skills.prayer;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;

import java.util.function.BooleanSupplier;

public final class PrayerBlessingLimit {
	private static final int BLESSINGS_PER_HOUR = 10;
	private static final long BLESSING_WINDOW_MS = 60L * 60L * 1000L;
	private static final String WINDOW_START_KEY = "myworld_prayer_blessing_window_start";
	private static final String WINDOW_COUNT_KEY = "myworld_prayer_blessing_window_count";

	private PrayerBlessingLimit() {
	}

	/**
	 * Runs one blessing conversion and records it only when the conversion
	 * reports success. The availability check, conversion, and bounded counter
	 * update share the player's monitor so two callers cannot consume the final
	 * slot concurrently.
	 */
	public static boolean completeSuccessfulBlessing(
		final Player player,
		final PrayerCatalog.GodLine godLine,
		final BooleanSupplier conversion
	) {
		if (player == null || godLine == null || conversion == null) {
			return false;
		}
		synchronized (player) {
			final long now = System.currentTimeMillis();
			final long windowStart = getWindowStart(player);
			final int count = getWindowCount(player);
			final boolean activeWindow = isWindowActive(windowStart, now);
			if (activeWindow && count >= BLESSINGS_PER_HOUR) {
				player.message("You hear a low rumbling voice...");
				player.message(getLimitMessage(godLine));
				return false;
			}
			if (!conversion.getAsBoolean()) {
				return false;
			}
			if (!activeWindow) {
				player.getCache().store(WINDOW_START_KEY, now);
				player.getCache().set(WINDOW_COUNT_KEY, 1);
			} else {
				player.getCache().set(WINDOW_COUNT_KEY, Math.min(BLESSINGS_PER_HOUR, count + 1));
			}
			return true;
		}
	}

	private static boolean isWindowActive(final long windowStart, final long now) {
		return windowStart > 0L && now - windowStart < BLESSING_WINDOW_MS;
	}

	private static long getWindowStart(final Player player) {
		return player.getCache().hasKey(WINDOW_START_KEY)
			? player.getCache().getLong(WINDOW_START_KEY)
			: 0L;
	}

	private static int getWindowCount(final Player player) {
		return player.getCache().hasKey(WINDOW_COUNT_KEY)
			? player.getCache().getInt(WINDOW_COUNT_KEY)
			: 0;
	}

	private static String getLimitMessage(final PrayerCatalog.GodLine godLine) {
		if (godLine == PrayerCatalog.GodLine.GUTHIX) {
			return "That is quite enough for now";
		}
		if (godLine == PrayerCatalog.GodLine.ZAMORAK) {
			return "Leave. Me. ALONE!";
		}
		return "You must learn Patience";
	}
}
