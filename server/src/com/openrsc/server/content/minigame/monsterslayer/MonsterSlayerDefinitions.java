package com.openrsc.server.content.minigame.monsterslayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable JSON definition records. Player state stores their stable keys, never positions. */
public final class MonsterSlayerDefinitions {
	private MonsterSlayerDefinitions() {
	}

	public static final class Family {
		private final String key;
		private final String displayName;
		private final List<Integer> npcIds;

		Family(String key, String displayName, List<Integer> npcIds) {
			this.key = key;
			this.displayName = displayName;
			this.npcIds = immutableCopy(npcIds);
		}

		public String getKey() {
			return key;
		}

		public String getDisplayName() {
			return displayName;
		}

		public List<Integer> getNpcIds() {
			return npcIds;
		}
	}

	public static final class Task {
		private final String key;
		private final String familyKey;
		private final int requiredKills;
		private final long pointReward;
		private final int weight;
		private final boolean repeatable;
		private final List<MonsterSlayerHazard> hazards;

		Task(String key, String familyKey, int requiredKills, long pointReward,
			 int weight, boolean repeatable, List<MonsterSlayerHazard> hazards) {
			this.key = key;
			this.familyKey = familyKey;
			this.requiredKills = requiredKills;
			this.pointReward = pointReward;
			this.weight = weight;
			this.repeatable = repeatable;
			this.hazards = immutableCopy(hazards);
		}

		public String getKey() {
			return key;
		}

		public String getFamilyKey() {
			return familyKey;
		}

		public int getRequiredKills() {
			return requiredKills;
		}

		public long getPointReward() {
			return pointReward;
		}

		public int getWeight() {
			return weight;
		}

		public boolean isRepeatable() {
			return repeatable;
		}

		public List<MonsterSlayerHazard> getHazards() { return hazards; }
	}

	public static final class Contact {
		private final String key;
		private final int npcId;
		private final MonsterSlayerChallenge challenge;
		private final MonsterSlayerRank requiredRank;
		private final MonsterSlayerRank awardedRank;
		private final List<Task> mandatoryTasks;
		private final List<Task> repeatableTasks;

		Contact(String key, int npcId, MonsterSlayerChallenge challenge,
				MonsterSlayerRank requiredRank, MonsterSlayerRank awardedRank,
				List<Task> mandatoryTasks, List<Task> repeatableTasks) {
			this.key = key;
			this.npcId = npcId;
			this.challenge = challenge;
			this.requiredRank = requiredRank;
			this.awardedRank = awardedRank;
			this.mandatoryTasks = immutableCopy(mandatoryTasks);
			this.repeatableTasks = immutableCopy(repeatableTasks);
		}

		public String getKey() {
			return key;
		}

		public int getNpcId() {
			return npcId;
		}

		public MonsterSlayerChallenge getChallenge() {
			return challenge;
		}

		public MonsterSlayerRank getRequiredRank() {
			return requiredRank;
		}

		public MonsterSlayerRank getAwardedRank() {
			return awardedRank;
		}

		public List<Task> getMandatoryTasks() {
			return mandatoryTasks;
		}

		public List<Task> getRepeatableTasks() {
			return repeatableTasks;
		}
	}

	public static final class Shop {
		private final String key;
		private final MonsterSlayerChallenge challenge;
		private final List<Category> categories;
		private final CapacityUpgrade capacityUpgrade;

		Shop(String key, MonsterSlayerChallenge challenge, List<Category> categories, CapacityUpgrade capacityUpgrade) {
			this.key = key;
			this.challenge = challenge;
			this.categories = immutableCopy(categories);
			this.capacityUpgrade = capacityUpgrade;
		}

		public String getKey() {
			return key;
		}

		public MonsterSlayerChallenge getChallenge() {
			return challenge;
		}

		public List<Category> getCategories() {
			return categories;
		}
		public CapacityUpgrade getCapacityUpgrade() { return capacityUpgrade; }
	}

	public static final class Category {
		private final String key;
		private final String label;
		private final int iconItemId;
		private final List<Reward> rewards;

		Category(String key, String label, int iconItemId, List<Reward> rewards) {
			this.key = key;
			this.label = label;
			this.iconItemId = iconItemId;
			this.rewards = immutableCopy(rewards);
		}

		public String getKey() {
			return key;
		}

		public String getLabel() {
			return label;
		}

		public int getIconItemId() {
			return iconItemId;
		}

		public List<Reward> getRewards() {
			return rewards;
		}
	}

	public static final class Reward {
		private final String key;
		private final int itemId;
		private final int amount;
		private final MonsterSlayerCost cost;
		private final int stock;
		private final int restockAmount;

		Reward(String key, int itemId, int amount, MonsterSlayerCost cost, int stock, int restockAmount) {
			this.key = key;
			this.itemId = itemId;
			this.amount = amount;
			this.cost = cost;
			this.stock = stock;
			this.restockAmount = restockAmount;
		}

		public String getKey() {
			return key;
		}

		public int getItemId() {
			return itemId;
		}

		public int getAmount() {
			return amount;
		}

		public MonsterSlayerCost getCost() {
			return cost;
		}
		public int getStock() { return stock; }
		public int getRestockAmount() { return restockAmount; }

		public MonsterSlayerCost costFor(long quantity) {
			return cost.multiply(quantity);
		}

		public long outputAmountFor(long quantity) {
			if (quantity <= 0L) {
				throw new IllegalArgumentException("Reward quantity must be positive");
			}
			try {
				return Math.multiplyExact((long) amount, quantity);
			} catch (ArithmeticException ex) {
				throw new IllegalArgumentException("Monster Slayer reward output overflow", ex);
			}
		}
	}

	public static final class CapacityUpgrade {
		private final String key;
		private final MonsterSlayerCost cost;
		CapacityUpgrade(String key, MonsterSlayerCost cost) { this.key = key; this.cost = cost; }
		public String getKey() { return key; }
		public MonsterSlayerCost getCost() { return cost; }
	}

	private static <T> List<T> immutableCopy(List<T> values) {
		return Collections.unmodifiableList(new ArrayList<T>(values));
	}
}
