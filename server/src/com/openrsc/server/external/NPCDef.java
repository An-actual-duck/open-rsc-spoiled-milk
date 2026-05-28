package com.openrsc.server.external;

/**
 * The definition wrapper for npcs
 */
public class NPCDef extends EntityDef {
	/**
	 * Whether the npc is aggressive
	 */
	public Boolean aggressive;
	/**
	 * The attack lvl
	 */
	public int attack;
	/**
	 * Whether the npc is attackable
	 */
	public Boolean attackable;
	public Boolean members;
	/**
	 * Colour of our legs
	 */
	public int bottomColour;
	/**
	 * Something to do with the camera
	 */
	public int camera1, camera2;
	/**
	 * The primary command
	 */
	public String command1, command2;
	/**
	 * The def lvl
	 */
	public int defense;
	public int ranged;
	public int meleeDefense;
	public int rangedDefense;
	public int magicDefense;
	public double meleeDefenseMultiplier;
	public double rangedDefenseMultiplier;
	public double magicDefenseMultiplier;
	public double meleeDefenseDivisor;
	public double rangedDefenseDivisor;
	public double magicDefenseDivisor;
	/**
	 * Possible drops
	 */
	public ItemDropDef[] drops;
	/**
	 * Colour of our hair
	 */
	public int hairColour;
	/**
	 * The hit points
	 */
	public int hits;
	/**
	 * How long the npc takes to respawn
	 */
	public int respawnTime;
	/**
	 * Skin colour
	 */
	public int skinColour;
	/**
	 * Sprites used to make up this npc
	 */
	public int[] sprites = new int[12];
	/**
	 * The strength lvl
	 */
	public int strength;

	/**
	 * combat level because why not,
	 * calculation of strength, def, attack and hits -
	 * is wrong compared to npcs combat level on a few monsters due to RSC set stats on mobs.
	 */
	public int combatLevel;
	/**
	 * Colour of our top
	 */
	public int topColour;
	/**
	 * Something to do with models
	 */
	public int walkModel, combatModel, combatSprite;

	/**
	 * Round Mode of xp given from mob (Xp = 2 * RoundMode(cb lvl) + 20)
	 * -1: Floor
	 * 0: Natural round
	 * 1: Ceil
	 * Default: Cast to int without any Math function
	 */
	public int roundMode;

	private int id;

	public NPCDef(NPCDef.NPCDefinitionBuilder builder) {
		this.id = builder.id;
		super.name = builder.name;
		super.description = builder.description;
		this.command1 = builder.command1;
		this.attack = builder.attack;
		this.strength = builder.strength;
		this.hits = builder.hits;
			this.defense = builder.defense;
			this.ranged = builder.ranged;
			this.meleeDefense = builder.meleeDefense;
			this.rangedDefense = builder.rangedDefense;
			this.magicDefense = builder.magicDefense;
			this.combatLevel = builder.combatLevel;
			this.meleeDefenseMultiplier = builder.meleeDefenseMultiplier;
			this.rangedDefenseMultiplier = builder.rangedDefenseMultiplier;
			this.magicDefenseMultiplier = builder.magicDefenseMultiplier;
			this.meleeDefenseDivisor = builder.meleeDefenseDivisor;
			this.rangedDefenseDivisor = builder.rangedDefenseDivisor;
			this.magicDefenseDivisor = builder.magicDefenseDivisor;
		this.members = builder.members;
		this.attackable = builder.attackable;
		this.aggressive = builder.aggressive;
	}

	public NPCDef() { }

	public int getAtt() {
		return attack;
	}

	public int getBottomColour() {
		return bottomColour;
	}

	public int getCamera1() {
		return camera1;
	}

	public int getCamera2() {
		return camera2;
	}

	public int getCombatModel() {
		return combatModel;
	}

	public int getCombatSprite() {
		return combatSprite;
	}

	public String getCommand1() {
		return command1;
	}
	public void setCommand1(String command) {
		command1 = command;
	}

	public String getCommand2() {
		return command2;
	}
	public void setCommand2(String command) {
		command2 = command;
	}

	public int getDef() {
		return defense;
	}
	public int getRanged() {
		return ranged;
	}
	public int getMeleeDefense() {
		return meleeDefense;
	}
	public int getRangedDefense() {
		return rangedDefense;
	}
	public int getMagicDefense() {
		return magicDefense;
	}
	public double getMeleeDefenseDivisor() {
		return meleeDefenseDivisor;
	}
	public double getRangedDefenseDivisor() {
		return rangedDefenseDivisor;
	}
	public double getMagicDefenseDivisor() {
		return magicDefenseDivisor;
	}
	public double getMeleeDefenseMultiplier() {
		return meleeDefenseMultiplier;
	}
	public double getRangedDefenseMultiplier() {
		return rangedDefenseMultiplier;
	}
	public double getMagicDefenseMultiplier() {
		return magicDefenseMultiplier;
	}

	public ItemDropDef[] getDrops() {
		return drops;
	}

	public int getHairColour() {
		return hairColour;
	}

	public int getHits() {
		return hits;
	}

	public int getSkinColour() {
		return skinColour;
	}

	public int getSprite(int index) {
		return sprites[index];
	}

	public int[] getStats() {
		return new int[]{attack, defense, strength};
	}

	public int getStr() {
		return strength;
	}

	public int getTopColour() {
		return topColour;
	}

	public int getWalkModel() {
		return walkModel;
	}

	public boolean isAggressive() {
		return attackable && aggressive;
	}

	public boolean isAttackable() {
		return attackable;
	}

	public int respawnTime() {
		return respawnTime;
	}

	public boolean isMembers() {
		return members;
	}

	public int roundMode() { return roundMode; }

	public static class NPCDefinitionBuilder
	{
		private String command1;
		private String description;
		private String name;
		private int attack;
		private int strength;
		private int hits;
			private int defense;
			private int ranged;
			private int meleeDefense;
			private int rangedDefense;
			private int magicDefense;
			private double meleeDefenseMultiplier;
			private double rangedDefenseMultiplier;
			private double magicDefenseMultiplier;
			private double meleeDefenseDivisor;
			private double rangedDefenseDivisor;
			private double magicDefenseDivisor;
			private int combatLevel;
		private Boolean members;
		private Boolean attackable;
		private Boolean aggressive;
		private int id;

		public NPCDefinitionBuilder(int id, String name) {
			this.id = id;
			this.name = name;
		}

		public NPCDef.NPCDefinitionBuilder description(String description) {
			this.description = description;
			return this;
		}

		public NPCDef.NPCDefinitionBuilder command(String command) {
			this.command1 = command;
			return this;
		}

		public NPCDef.NPCDefinitionBuilder attack(int attack) {
			this.attack = attack;
			return this;
		}

		public NPCDef.NPCDefinitionBuilder strength(int strength) {
			this.strength = strength;
			return this;
		}

		public NPCDef.NPCDefinitionBuilder hits(int hits) {
			this.hits = hits;
			return this;
		}

		public NPCDef.NPCDefinitionBuilder defense(int defense) {
			this.defense = defense;
			return this;
		}

			public NPCDef.NPCDefinitionBuilder ranged(int ranged) {
				this.ranged = ranged;
				return this;
			}

			public NPCDef.NPCDefinitionBuilder meleeDefense(int meleeDefense) {
				this.meleeDefense = meleeDefense;
				return this;
			}

			public NPCDef.NPCDefinitionBuilder rangedDefense(int rangedDefense) {
				this.rangedDefense = rangedDefense;
				return this;
			}

		public NPCDef.NPCDefinitionBuilder magicDefense(int magicDefense) {
			this.magicDefense = magicDefense;
			return this;
		}

		public NPCDef.NPCDefinitionBuilder meleeDefenseMultiplier(double meleeDefenseMultiplier) {
			this.meleeDefenseMultiplier = meleeDefenseMultiplier;
			return this;
		}

		public NPCDef.NPCDefinitionBuilder rangedDefenseMultiplier(double rangedDefenseMultiplier) {
			this.rangedDefenseMultiplier = rangedDefenseMultiplier;
			return this;
		}

		public NPCDef.NPCDefinitionBuilder magicDefenseMultiplier(double magicDefenseMultiplier) {
			this.magicDefenseMultiplier = magicDefenseMultiplier;
			return this;
		}

		public NPCDef.NPCDefinitionBuilder meleeDefenseDivisor(double meleeDefenseDivisor) {
			this.meleeDefenseDivisor = meleeDefenseDivisor;
			return this;
		}

		public NPCDef.NPCDefinitionBuilder rangedDefenseDivisor(double rangedDefenseDivisor) {
			this.rangedDefenseDivisor = rangedDefenseDivisor;
			return this;
		}

		public NPCDef.NPCDefinitionBuilder magicDefenseDivisor(double magicDefenseDivisor) {
			this.magicDefenseDivisor = magicDefenseDivisor;
			return this;
		}

		public NPCDef.NPCDefinitionBuilder combatLevel(int combatLevel) {
			this.combatLevel = combatLevel;
			return this;
		}

		public NPCDef.NPCDefinitionBuilder members(Boolean members) {
			this.members = members;
			return this;
		}

		public NPCDef.NPCDefinitionBuilder attackable(Boolean attackable) {
			this.attackable = attackable;
			return this;
		}

		public NPCDef.NPCDefinitionBuilder aggressive(Boolean aggressive) {
			this.aggressive = aggressive;
			return this;
		}

		public NPCDef build() {
			NPCDef definition =  new NPCDef(this);
			return definition;
		}
	}
}
