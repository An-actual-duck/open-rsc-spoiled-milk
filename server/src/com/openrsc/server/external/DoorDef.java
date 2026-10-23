package com.openrsc.server.external;

/**
 * The definition wrapper for doors
 */
public class DoorDef extends EntityDef {

	/**
	 * The first command of the door
	 */
	public String command1;
	/**
	 * The second command of the door
	 */
	public String command2;
	/**
	 * The doors type.
	 */
	public int doorType;
	public int modelVar1;

	public int modelVar2;
	public int modelVar3;
	/**
	 * Unknown
	 */
	public int unknown;
	/** Optional passable header: visible masonry starts this far above its base. */
	public int doorOpeningHeight;
	/** Source wall lookup ID matched by this header, meaningful only with an opening. */
	public int doorSurroundFor;
	/** Optional two-band static wall: original material below, alternate above. */
	public int wallLowerHeight;
	public int wallUpperFront;
	public int wallUpperBack;

	public int getWallLowerHeight() { return wallLowerHeight; }
	public int getWallUpperFront() { return wallUpperFront; }
	public int getWallUpperBack() { return wallUpperBack; }

	public void validateWallBands() {
		if (wallLowerHeight == 0 && wallUpperFront == 0 && wallUpperBack == 0) return;
		if (wallLowerHeight <= 0 || wallLowerHeight >= modelVar1
			|| wallUpperFront < -32768 || wallUpperFront >= 32767
			|| wallUpperBack < -32768 || wallUpperBack >= 32767
			|| doorOpeningHeight != 0 || doorSurroundFor != 0
			|| doorType != 1 || unknown != 0
			|| !"WalkTo".equalsIgnoreCase(command1) || !"Examine".equalsIgnoreCase(command2)) {
			throw new IllegalArgumentException("Invalid static wall material bands: " + getName());
		}
	}

	public int getDoorOpeningHeight() {
		return doorOpeningHeight;
	}

	public int getDoorSurroundFor() {
		return doorSurroundFor;
	}

	/** Reject malformed optional content before the map can reference it. */
	public void validateDoorSurround(DoorDef[] definitions) {
		validateWallBands();
		if (doorOpeningHeight == 0) return;
		if (doorOpeningHeight < 0 || doorOpeningHeight >= modelVar1
			|| doorSurroundFor < 0 || doorSurroundFor >= definitions.length
			|| doorType != 0 || unknown != 0
			|| !"walkto".equals(getCommand1()) || !"examine".equals(getCommand2())) {
			throw new IllegalArgumentException("Invalid passable door surround: " + getName());
		}
		DoorDef source = definitions[doorSurroundFor];
		if (source == null || source.doorOpeningHeight != 0 || source.wallLowerHeight != 0 || source.modelVar1 != modelVar1
			|| source.modelVar2 != modelVar2 || source.modelVar3 != modelVar3) {
			throw new IllegalArgumentException("Door surround does not match its source wall: " + getName());
		}
	}

	public String getCommand1() {
		return command1.toLowerCase();
	}

	public String getCommand2() {
		return command2.toLowerCase();
	}

	public int getDoorType() {
		return doorType;
	}

	public int getModelVar1() {
		return modelVar1;
	}

	public int getModelVar2() {
		return modelVar2;
	}

	public int getModelVar3() {
		return modelVar3;
	}

	public int getUnknown() {
		return unknown;
	}
}
