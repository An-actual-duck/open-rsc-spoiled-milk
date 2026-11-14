package com.openrsc.server.content.coatings;

import com.openrsc.server.runtime.GameRandom;

/** Monotone, mean-preserving compression of positive ordinary melee rolls. */
public final class ScimitarDamage {
    private ScimitarDamage() { }

    public static int maximum(int original) {
        return original <= 0 ? 0 : (int)(((long)original * 9 + 9) / 10);
    }

    /**
     * Keep the original zero/miss and defense roll. Conditional on that defense,
     * positives formerly span 1..remainingMaximum uniformly. Mix a uniform and
     * ascending triangular distribution on the smaller support to keep its mean.
     * Mapping the original quantile monotonically preserves existing luck/bias
     * advantages; no guaranteed minimum positive hit is introduced.
     */
    public static int reshape(int positive, int remainingMaximum, GameRandom random) {
        if (positive <= 0 || remainingMaximum <= 1) return Math.max(0, positive);
        int cap = maximum(remainingMaximum);
        if (cap == remainingMaximum) return positive;
        double q = 3.0 * (remainingMaximum - cap) / (cap - 1);
        double quantile = (positive - 1.0 + random.nextDouble()) / remainingMaximum;
        int lo = 1, hi = cap;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            double cdf = (1-q) * mid / cap + q * mid * (mid+1.0) / (cap * (cap+1.0));
            if (quantile < cdf) hi = mid; else lo = mid+1;
        }
        return lo;
    }
}
