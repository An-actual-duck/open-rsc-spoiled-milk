package com.openrsc.client.entityhandling.defs;

/** Immutable server-fed presentation metadata for one timed Cleric rank. */
public final class ClericEffectRankDef {
	private final java.util.List<ClericComponentDef> components;
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
		this(rank,durationMilliseconds,presentationKind,counterKind,initialCounter,primaryMagnitude,secondaryMagnitude,java.util.Collections.<ClericComponentDef>emptyList());
	}

	public ClericEffectRankDef(int rank,int durationMilliseconds,int presentationKind,int counterKind,int initialCounter,int primaryMagnitude,int secondaryMagnitude,java.util.List<ClericComponentDef> components) {
		if (rank <= 0 || rank > 255 || durationMilliseconds <= 0
				|| presentationKind < 1 || presentationKind > 8
				|| counterKind < 0 || counterKind > 2
				|| (counterKind == 0 && initialCounter != 0)
				|| (counterKind != 0 && (initialCounter <= 0 || initialCounter > 65_535))
				|| primaryMagnitude <= 0 || primaryMagnitude > 65_535
				|| secondaryMagnitude < 0 || secondaryMagnitude > 65_535) {
			throw new IllegalArgumentException("Invalid Cleric effect-rank presentation metadata");
		}
		boolean validShape = presentationKind == 8
            ? counterKind == 0 && initialCounter == 0 && rank <= 6 && primaryMagnitude == rank && secondaryMagnitude >= 1 && secondaryMagnitude <= 3
            : presentationKind == 1
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
        if(components==null||components.size()>orsc.ClericBlessingContract.MAX_COMPONENTS
                ||(presentationKind==8?components.isEmpty():!components.isEmpty()))throw new IllegalArgumentException("Invalid blessing components");
        java.util.Set<Integer> kinds=new java.util.HashSet<Integer>();
        for(ClericComponentDef component:components)if(component==null||!kinds.add(component.getKind()))throw new IllegalArgumentException("Duplicate blessing component");
        this.components=java.util.Collections.unmodifiableList(new java.util.ArrayList<ClericComponentDef>(components));
		this.rank = rank;
		this.durationMilliseconds = durationMilliseconds;
		this.presentationKind = presentationKind;
		this.counterKind = counterKind;
		this.initialCounter = initialCounter;
		this.primaryMagnitude = primaryMagnitude;
		this.secondaryMagnitude = secondaryMagnitude;
	}

	public java.util.List<ClericComponentDef> getComponents(){return components;}
	public String describeComponents(){StringBuilder text=new StringBuilder();for(ClericComponentDef c:components){if(text.length()>0)text.append("; ");text.append(c.describe());}return text.toString();}
	public int getRank() { return rank; }
	public int getDurationMilliseconds() { return durationMilliseconds; }
	public int getPresentationKind() { return presentationKind; }
	public int getCounterKind() { return counterKind; }
	public int getInitialCounter() { return initialCounter; }
	public int getPrimaryMagnitude() { return primaryMagnitude; }
	public int getSecondaryMagnitude() { return secondaryMagnitude; }
}
