package com.openrsc.client.entityhandling.defs.extras;

public class AnimationDef {
	public String name;
	public String category;
	private int charColour;
	private int blueMask;
	private int genderModel;
	private boolean hasA; // Has fighting animation
	private boolean hasF; // Has special fighting animation for the weapon; used authentically on zombweapon, skelweapon, gobweapon
	public int number;


	public AnimationDef(String name, String category, int charColour, int blueMask, int genderModel, boolean hasA, boolean hasF, int number) {
		this.name = name;
		this.category = category;
		this.charColour = charColour;
		this.genderModel = genderModel;
		this.blueMask = blueMask;
		this.hasA = hasA;
		this.hasF = hasF;
		this.number = number;
	}
	public AnimationDef(String name, String category, int charColour, int genderModel, boolean hasA, boolean hasF, int number) {
		this.name = name;
		this.category = category;
		this.charColour = charColour;
		this.blueMask = 0;
		this.genderModel = genderModel;
		this.hasA = hasA;
		this.hasF = hasF;
		this.number = number;
	}

	public String getName() {
		return name;
	}

	public int getCharColour() {
		return charColour;
	}

	public int getBlueMask() {
		return this.blueMask;
	}

	public int getGenderModel() {
		return genderModel;
	}

	public boolean hasA() {
		return hasA;
	} // Has fighting animation; false for sheep and ranged weapons

	public boolean hasF() { // Has special fighting animation for the weapon; used authentically on zombweapon, skelweapon, gobweapon
		return hasF;
	}

	public int getNumber() {
		return number;
	}
}
