package com.openrsc.server.content.cleric;

import java.util.Arrays;

/** Pure, bounded recovery planning for the instant Restore support spell. */
public final class ClericRestoreEffect {
	private static final int[] RECOVERY_PERCENT = {10, 25, 40, 60};

	private ClericRestoreEffect() {
	}

	public static int recoveryPercentForRank(final int effectRank) {
		if (effectRank <= 0 || effectRank > RECOVERY_PERCENT.length) {
			throw new IllegalArgumentException("Unknown Restore effect rank: " + effectRank);
		}
		return RECOVERY_PERCENT[effectRank - 1];
	}

	public static Plan plan(final int[] currentLevels, final int[] validMaximums,
			final int hitsSkillIndex, final int effectRank) {
		if (currentLevels == null || validMaximums == null
				|| currentLevels.length != validMaximums.length
				|| hitsSkillIndex < 0 || hitsSkillIndex >= currentLevels.length) {
			throw new IllegalArgumentException("Restore requires matching configured-skill snapshots");
		}
		final int recoveryPercent = recoveryPercentForRank(effectRank);
		final int[] restoredLevels = Arrays.copyOf(currentLevels, currentLevels.length);
		int restoredSkillCount = 0;
		for (int skill = 0; skill < currentLevels.length; skill++) {
			final int current = currentLevels[skill];
			final int validMaximum = validMaximums[skill];
			if (current < 0 || validMaximum < 0) {
				throw new IllegalArgumentException("Restore skill levels cannot be negative");
			}
			if (skill == hitsSkillIndex || current >= validMaximum) {
				continue;
			}
			final long scaled = (long) validMaximum * recoveryPercent;
			final int recovery = (int) Math.min(Integer.MAX_VALUE,
				(scaled + 99L) / 100L);
			final int restored = (int) Math.min((long) validMaximum,
				(long) current + recovery);
			if (restored > current) {
				restoredLevels[skill] = restored;
				restoredSkillCount++;
			}
		}
		return new Plan(restoredLevels, restoredSkillCount);
	}

	public static final class Plan {
		private final int[] restoredLevels;
		private final int restoredSkillCount;

		private Plan(final int[] restoredLevels, final int restoredSkillCount) {
			this.restoredLevels = restoredLevels;
			this.restoredSkillCount = restoredSkillCount;
		}

		public boolean isUseful() {
			return restoredSkillCount > 0;
		}

		public int getRestoredSkillCount() {
			return restoredSkillCount;
		}

		public int[] getRestoredLevels() {
			return Arrays.copyOf(restoredLevels, restoredLevels.length);
		}
	}
}
