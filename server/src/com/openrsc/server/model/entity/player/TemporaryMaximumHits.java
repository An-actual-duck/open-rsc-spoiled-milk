package com.openrsc.server.model.entity.player;

/**
 * Arithmetic policy for a temporary maximum-Hits bonus.
 *
 * <p>The ordinary maximum remains authoritative and persistent. Increasing a
 * temporary bonus grants the added Hits, while reducing or expiring it only
 * clamps health that is above the new ceiling. Damage taken while the bonus is
 * active must not be applied a second time when the ceiling returns to normal.</p>
 */
public final class TemporaryMaximumHits {
	private TemporaryMaximumHits() {
	}

	public static int healingCeiling(final int ordinaryMaximum, final int temporaryBonus) {
		return boundedAdd(Math.max(0, ordinaryMaximum), Math.max(0, temporaryBonus));
	}

	public static int reconcileBonus(
		final int currentHits,
		final int ordinaryMaximum,
		final int previousBonus,
		final int nextBonus) {
		final int current = Math.max(0, currentHits);
		if (current == 0) {
			return 0;
		}
		final int previous = Math.max(0, previousBonus);
		final int next = Math.max(0, nextBonus);
		final int nextCeiling = healingCeiling(ordinaryMaximum, next);
		if (next > previous) {
			return Math.min(nextCeiling, boundedAdd(current, next - previous));
		}
		return Math.min(current, nextCeiling);
	}

	public static int persistedHits(final int currentHits, final int ordinaryMaximum) {
		return Math.min(Math.max(0, currentHits), Math.max(0, ordinaryMaximum));
	}

	private static int boundedAdd(final int left, final int right) {
		final long sum = (long) left + right;
		return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
	}
}
