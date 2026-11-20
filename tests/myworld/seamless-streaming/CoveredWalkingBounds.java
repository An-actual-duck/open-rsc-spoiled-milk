package com.openrsc.server;

/** Checks when the private preview must release its existing scene center. */
public final class CoveredWalkingBounds {
	public static void main(String[] args) {
		int samples = 0;
		for (int x = -50; x <= 100; x++) {
			for (int y = -50; y <= 100; y++) {
				boolean expected = x >= -24 && x < 72 && y >= -24 && y < 72;
				if (GameStateUpdater.canRetainCoveredWalkingCenter(96 + x, 624 + y, 96, 624) != expected) {
					throw new AssertionError("Wrong coverage decision for " + x + "," + y);
				}
				samples++;
			}
		}
		if (GameStateUpdater.canRetainCoveredWalkingCenter(Integer.MAX_VALUE, 624, Integer.MIN_VALUE, 624)) {
			throw new AssertionError("Wrapped coordinate admitted to coverage");
		}
		System.out.println("PASS " + (samples + 1) + " covered walking bounds checks");
	}
}
