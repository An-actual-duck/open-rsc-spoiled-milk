package com.openrsc.server.content;

/** Pure defense-budget policy for crafted wool armor. */
public final class WoolRobeDefense {
	private WoolRobeDefense() {
	}

	public static int budget(final int tier, final int resourceCost) {
		final long scaled = (long) Math.max(0, tier) * Math.max(0, resourceCost);
		return scaled > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) scaled;
	}
}
