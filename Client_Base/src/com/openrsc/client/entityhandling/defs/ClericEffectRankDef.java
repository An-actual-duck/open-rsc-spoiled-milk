package com.openrsc.client.entityhandling.defs;

/** Immutable server-fed presentation metadata for one timed Cleric rank. */
public final class ClericEffectRankDef {
	private final int rank;
	private final int durationMilliseconds;
	private final int presentationKind;
	private final int counterKind;
	private final int initialCounter;
	private final int primaryMagnitude;
	private final int secondaryMagnitude;

	public ClericEffectRankDef(int rank, int durationMilliseconds,
			int presentationKind, int counterKind, int initialCounter,
			int primaryMagnitude, int secondaryMagnitude) {
		if (rank <= 0 || rank > 255 || durationMilliseconds <= 0
				|| presentationKind < 1 || presentationKind > 7
				|| counterKind < 0 || counterKind > 2
				|| (counterKind == 0 && initialCounter != 0)
				|| (counterKind != 0 && (initialCounter <= 0 || initialCounter > 65_535))
				|| primaryMagnitude <= 0 || primaryMagnitude > 65_535
				|| secondaryMagnitude < 0 || secondaryMagnitude > 65_535) {
			throw new IllegalArgumentException("Invalid Cleric effect-rank presentation metadata");
		}
		boolean validShape = presentationKind == 1
			? counterKind == 2 && secondaryMagnitude == 0
			: presentationKind == 2
				? counterKind == 0 && secondaryMagnitude > 0
				: presentationKind == 3
					? counterKind == 1 && secondaryMagnitude == 0
					: presentationKind == 6
						? counterKind == 0 && secondaryMagnitude > 0
						: counterKind == 0 && secondaryMagnitude == 0;
		if (!validShape) {
			throw new IllegalArgumentException("Mismatched Cleric effect presentation shape");
		}
		this.rank = rank;
		this.durationMilliseconds = durationMilliseconds;
		this.presentationKind = presentationKind;
		this.counterKind = counterKind;
		this.initialCounter = initialCounter;
		this.primaryMagnitude = primaryMagnitude;
		this.secondaryMagnitude = secondaryMagnitude;
	}

	public int getRank() { return rank; }
	public int getDurationMilliseconds() { return durationMilliseconds; }
	public int getPresentationKind() { return presentationKind; }
	public int getCounterKind() { return counterKind; }
	public int getInitialCounter() { return initialCounter; }
	public int getPrimaryMagnitude() { return primaryMagnitude; }
	public int getSecondaryMagnitude() { return secondaryMagnitude; }
}
