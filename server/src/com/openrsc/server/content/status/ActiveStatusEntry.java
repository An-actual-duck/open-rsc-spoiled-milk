package com.openrsc.server.content.status;

/** Immutable server-authoritative status presentation entry. */
public final class ActiveStatusEntry {
	public enum IdentityKind {
		ITEM(0),
		CLERIC(1);

		private final int code;

		IdentityKind(int code) {
			this.code = code;
		}

		public int getCode() {
			return code;
		}
	}

	public enum CounterKind {
		NONE(0),
		CHARGES(1),
		PULSES(2);

		private final int code;

		CounterKind(int code) {
			this.code = code;
		}

		public int getCode() {
			return code;
		}
	}

	private final String stableKey;
	private final IdentityKind identityKind;
	private final int stableIdentity;
	private final int iconItemId;
	private final int remainingSeconds;
	private final int rank;
	private final CounterKind counterKind;
	private final int remainingCounter;

	private ActiveStatusEntry(String stableKey, IdentityKind identityKind,
			int stableIdentity, int iconItemId, int remainingSeconds, int rank,
			CounterKind counterKind, int remainingCounter) {
		if (stableKey == null || stableKey.trim().isEmpty() || identityKind == null
				|| counterKind == null || stableIdentity < 0 || stableIdentity > 65_535
				|| iconItemId < 0 || iconItemId > 65_535 || remainingSeconds <= 0) {
			throw new IllegalArgumentException("Invalid active-status identity or lifetime");
		}
		if (identityKind == IdentityKind.ITEM
				&& (stableIdentity != iconItemId || rank != 0
					|| counterKind != CounterKind.NONE || remainingCounter != 0)) {
			throw new IllegalArgumentException("Item statuses cannot carry Cleric rank or counters");
		}
		if (identityKind == IdentityKind.CLERIC
				&& (rank <= 0 || rank > 255
					|| counterKind == CounterKind.NONE && remainingCounter != 0
					|| counterKind != CounterKind.NONE
						&& (remainingCounter <= 0 || remainingCounter > 65_535))) {
			throw new IllegalArgumentException("Invalid Cleric status rank or counter");
		}
		this.stableKey = stableKey;
		this.identityKind = identityKind;
		this.stableIdentity = stableIdentity;
		this.iconItemId = iconItemId;
		this.remainingSeconds = remainingSeconds;
		this.rank = rank;
		this.counterKind = counterKind;
		this.remainingCounter = remainingCounter;
	}

	public static ActiveStatusEntry item(String stableKey, int itemId,
			int remainingSeconds) {
		return new ActiveStatusEntry(stableKey, IdentityKind.ITEM, itemId, itemId,
			remainingSeconds, 0, CounterKind.NONE, 0);
	}

	public static ActiveStatusEntry cleric(String stableKey, int spellCode,
			int fallbackIconItemId, int remainingSeconds, int rank,
			CounterKind counterKind, int remainingCounter) {
		return new ActiveStatusEntry(stableKey, IdentityKind.CLERIC, spellCode,
			fallbackIconItemId, remainingSeconds, rank, counterKind, remainingCounter);
	}

	public String getStableKey() {
		return stableKey;
	}

	public IdentityKind getIdentityKind() {
		return identityKind;
	}

	public int getStableIdentity() {
		return stableIdentity;
	}

	public int getIconItemId() {
		return iconItemId;
	}

	public int getRemainingSeconds() {
		return remainingSeconds;
	}

	public int getRank() {
		return rank;
	}

	public CounterKind getCounterKind() {
		return counterKind;
	}

	public int getRemainingCounter() {
		return remainingCounter;
	}
}
