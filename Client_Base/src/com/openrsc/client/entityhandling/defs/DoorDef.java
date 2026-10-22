package com.openrsc.client.entityhandling.defs;

public class DoorDef extends EntityDef {

	private String command1;
	private String command2;
	private int doorType;
	public int unknown;
	private int wallObjectHeight;
	private int modelVar2;
	private int modelVar3;
	private int doorOpeningHeight;
	private int doorSurroundFor;

	public DoorDef(String name, String description, String command1, String command2, int doorType, int unknown, int wallObjectHeight, int modelVar2, int modelVar3, int id) {
		this(name, description, command1, command2, doorType, unknown, wallObjectHeight,
			modelVar2, modelVar3, id, 0, 0);
	}

	public DoorDef(String name, String description, String command1, String command2, int doorType, int unknown, int wallObjectHeight, int modelVar2, int modelVar3, int id, int doorOpeningHeight, int doorSurroundFor) {
		super(name, description, id);
		if (doorOpeningHeight < 0 || (doorOpeningHeight > 0
			&& (doorOpeningHeight >= wallObjectHeight || doorSurroundFor < 0 || doorType != 0 || unknown != 0))) {
			throw new IllegalArgumentException("Invalid passable door surround");
		}
		this.command1 = command1;
		this.command2 = command2;
		this.doorType = doorType;
		this.unknown = unknown;
		this.wallObjectHeight = wallObjectHeight;
		this.modelVar2 = modelVar2;
		this.modelVar3 = modelVar3;
		this.doorOpeningHeight = doorOpeningHeight;
		this.doorSurroundFor = doorSurroundFor;
	}

	public int getDoorOpeningHeight() {
		return doorOpeningHeight;
	}

	public int getDoorSurroundFor() {
		return doorSurroundFor;
	}

	public String getCommand1() {
		return command1;
	}

	public String getCommand2() {
		return command2;
	}

	public int getDoorType() {
		return doorType;
	}

	public int getUnknown() {
		return unknown;
	}

	public int getWallObjectHeight() {
		return wallObjectHeight;
	}

	public int getModelVar2() {
		return modelVar2;
	}

	public int getModelVar3() {
		return modelVar3;
	}
}
