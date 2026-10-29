package com.openrsc.client.entityhandling.defs;

public class GameObjectDef extends EntityDef {
	private String command1;
	private String command2;
	public int type;
	public int width;
	public int height;
	private int groundItemVar;
	private String objectModel;
	public int modelID;
	private final boolean interactionOnly;

	public GameObjectDef(String name, String description, String command1, String command2, int type, int width, int height, int groundItemVar, String objectModel, int id) {
		this(name, description, command1, command2, type, width, height, groundItemVar, objectModel, id, false);
	}

	/** Invisible scenery whose native footprint is still available for interaction. */
	public GameObjectDef(String name, String description, String command1, String command2, int type, int width, int height, int groundItemVar, String objectModel, int id, boolean interactionOnly) {
		super(name, description, id);
		this.interactionOnly = interactionOnly;
		this.command1 = command1;
		this.command2 = command2;
		this.type = type;
		this.width = width;
		this.height = height;
		this.groundItemVar = groundItemVar;
		this.objectModel = objectModel;
	}

	public boolean isInteractionOnly() { return interactionOnly; }

	public String getObjectModel() {
		return objectModel;
	}

	public String getCommand1() {
		return command1;
	}

	public String getCommand2() {
		return command2;
	}

	public int getType() {
		return type;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public int getGroundItemVar() {
		return groundItemVar;
	}
}
