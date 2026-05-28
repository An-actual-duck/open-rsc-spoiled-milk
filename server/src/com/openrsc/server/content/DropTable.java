package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.database.impl.mysql.queries.logging.LiveFeedLog;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;
import com.openrsc.server.util.rsc.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class DropTable {

	private static final Logger LOGGER = LogManager.getLogger();

	ArrayList<Drop> drops;
	ArrayList<Accessor> accessors;
	int totalWeight;
	String description;
	/*
	 * A unique ID to be specified at drop table creation. Do not change this if one is defined
	 */
	String dropTableId;
	boolean rare;

	private static int RING_OF_WEALTH_BOOST_NUMERATOR = 1;
	private static int RING_OF_WEALTH_BOOST_DENOMINATOR = 128;
	private static final int RARE_NORMAL_DROP_MAX_WEIGHT = 2;
	private static final double MINIMUM_RARE_CONTRIBUTION_SCALE = 0.05D;
	private static final Set<Integer> RARE_NORMAL_DROP_IDS = createRareNormalDropIds();

	public DropTable() {
		this("", "", false);
	}

	public DropTable(String description) {
		this(description, "", false);
	}

	public DropTable(String description, String dropTableId) {
		this(description, dropTableId, false);
	}

	public DropTable(String description, boolean rare) {
		this(description, "", rare);
	}

	public DropTable(String description, String dropTableId, boolean rare) {
		drops = new ArrayList<>();
		accessors = new ArrayList<>();
		totalWeight = 0;
		this.rare = rare;

		this.description = description;
		this.dropTableId = dropTableId;
	}

	@Override
	public String toString() {
		return "DropTable{" +
			"drops=" + drops +
			", totalWeight=" + totalWeight +
			", description='" + description + '\'' +
			'}';
	}

	public DropTable clone() {
		return clone("");
	}

	public DropTable clone(String description) {
		DropTable clonedDropTable = new DropTable(description, this.dropTableId, this.rare);
		for (Drop drop : drops) {
			if (drop.type == dropType.NOTHING) {
				clonedDropTable.addEmptyDrop(drop.weight);
			}
			else if (drop.type == dropType.ITEM) {
				clonedDropTable.addItemDrop(drop.id, drop.amount, drop.weight, drop.noted);
			}
			else if (drop.type == dropType.TABLE) {
				clonedDropTable.addTableDrop(drop.table, drop.weight);
			}
		}
		return clonedDropTable;
	}

	public int getTotalWeight() {
		return totalWeight;
	}

	public String getDescription() {
		return description;
	}

	public String getDropTableId() {
		return dropTableId;
	}

	public boolean isRare() {
		return rare;
	}

	public void addEmptyDrop(int weight) {
		if (weight < 0) {
			LOGGER.error("The drop table for \"" + this.description + "\" doesn't add up as expected!!!");
			System.exit(0);
		}
		drops.add(new Drop(ItemId.NOTHING.id(), 0, weight, false, dropType.NOTHING));
		this.totalWeight += weight;
	}

	public void addItemDrop(int itemID, int amount, int weight) {
		addItemDrop(itemID, amount, weight, false);
	}

	public void addItemDrop(int itemID, int amount, int weight, boolean noted) {
		drops.add(new Drop(itemID, amount, weight, noted, dropType.ITEM));
		this.totalWeight += weight;
	}

	public void addTableDrop(DropTable table, int weight) {
		drops.add(new Drop(table, weight));
		this.totalWeight += weight;
	}

	public void addAccessor(int id, int numerator, int denominator) {
		accessors.add(new Accessor(id, numerator, denominator));
	}

	public void removeItemDrop(Item item) {
		Iterator<Drop> iter = drops.iterator();
		while (iter.hasNext()) {
			Drop drop = iter.next();
			if (drop.id == item.getCatalogId() && drop.amount == item.getAmount()) {
				iter.remove();
			}
		}
	}

	public void removeAllItemDrops(int itemId) {
		Iterator<Drop> iter = drops.iterator();
		while (iter.hasNext()) {
			Drop drop = iter.next();
			if (drop.type == dropType.ITEM && drop.id == itemId) {
				totalWeight -= drop.weight;
				iter.remove();
			}
		}
	}

	public boolean hasItemDrop(int itemId, int amount, int weight, boolean noted) {
		for (Drop drop : drops) {
			if (drop.type == dropType.ITEM
				&& drop.id == itemId
				&& drop.amount == amount
				&& drop.weight == weight
				&& drop.noted == noted) {
				return true;
			}
		}
		return false;
	}

	public ArrayList<Item> rollItem(boolean ringOfWealth, Player owner) {
		return rollItem(ringOfWealth ? 0.25D : 0.0D, owner, true);
	}

	public ArrayList<Item> rollItem(Player owner) {
		return rollItem(getWealthChance(owner), owner, true, 1.0D, false, false);
	}

	public ArrayList<Item> rollPersonalLoot(Player owner, double contributionScale) {
		return rollItem(getWealthChance(owner), owner, true, contributionScale, true, false);
	}

	private ArrayList<Item> rollItem(double wealthChance, Player owner, boolean allowExtraRoll) {
		return rollItem(wealthChance, owner, allowExtraRoll, 1.0D, false, false);
	}

	private ArrayList<Item> rollItem(double wealthChance, Player owner, boolean allowExtraRoll, double contributionScale, boolean scaleRareNormalDrops, boolean suppressRareTables) {
		DropTable rollTable = this;
		int rollWeightTotal = 0;
		for (Drop drop : rollTable.drops) {
			rollWeightTotal += getRollWeight(drop, owner);
		}
		if (rollWeightTotal <= 0) {
			return new ArrayList<>();
		}
		int hit = DataConversions.random(0, rollWeightTotal - 1);
		int sum = 0;
		ArrayList<Item> items = new ArrayList<>();
		for (Drop drop : rollTable.drops) {
			int rollWeight = getRollWeight(drop, owner);
			sum += rollWeight;
			int threshold = sum;
			if (owner.getConfig().WANT_CUSTOM_QUESTS) { // must check this or else BadLuckMitigation might not be initialized & cause NPE
				if (drop.type == dropType.ITEM && owner.getWorld().getNpcDrops().getBadLuckMitigation().shouldMitigateBadLuck(getDropTableId(), drop.id)) {
					threshold += owner.getWorld().getNpcDrops().getBadLuckMitigation().getRollModifier(owner, getDropTableId(), drop.id);
				}
			}
			if (threshold > hit) {
				if (drop.type == dropType.NOTHING) {
					break;
				}
				else if (drop.type == dropType.ITEM) {
					if (drop.weight == 0) continue;
					if (scaleRareNormalDrops && isRareNormalDrop(drop) && !passesContributionGate(contributionScale)) {
						break;
					}
					if (owner.getWorld().getServer().getEntityHandler().getItemDef(drop.id).isMembersOnly()
						&& !owner.getWorld().getServer().getConfig().MEMBER_WORLD) {
						continue; // Members only item on a free world
					}
					if (drop.id == ItemId.UNHOLY_SYMBOL_MOULD.id()) {
						if (owner.wantUnholySymbols()) {
							continue;
						}
					}
					if (owner.getWorld().getServer().getConfig().VALUABLE_DROP_MESSAGES) {
						checkValuableDrop(drop.id, drop.amount, drop.weight, rollTable.totalWeight, owner);
					}
					items.add(new Item(drop.id, drop.amount, drop.noted));
					break;
				} else if (drop.type == dropType.TABLE) {
					if (drop.table.isRare() && (suppressRareTables || !passesContributionGate(contributionScale))) {
						break;
					}
					DropTable newTable = drop.table.clone();

					ArrayList<Item> invariableItemsToAdd = newTable.invariableItems(owner);
					items.addAll(invariableItemsToAdd);

					// We need to check if no "always drop" items were added.
					// If there weren't, and the totalWeight is 0, that means
					// that the new drop table ONLY contains additional drop tables.
					// This is probably only a special case for Chaos Druids.
					boolean onlyTables = invariableItemsToAdd.isEmpty() && newTable.getTotalWeight() == 0;

					if (newTable.getTotalWeight() > 0) {
						ArrayList<Item> itemsToAdd = newTable.rollItem(0.0D, owner, false, contributionScale, false, suppressRareTables);
						items.addAll(itemsToAdd);
					} else if (onlyTables) {
						for (Drop table : newTable.drops) {
							if (table.type == dropType.TABLE)
							{
								if (table.table.isRare() && (suppressRareTables || !passesContributionGate(contributionScale))) {
									continue;
								}
								items.addAll(table.table.rollItem(0.0D, owner, false, contributionScale, false, suppressRareTables));
							}
						}
					}

					break;
				}
			}
		}
		if (allowExtraRoll && wealthChance > 0.0D && DataConversions.getRandom().nextDouble() < wealthChance) {
			owner.playerServerMessage(MessageType.QUEST, "@ora@Your ring of wealth shines brightly!");
			owner.playSound("foundgem");
			items.addAll(rollItem(0.0D, owner, false, contributionScale, scaleRareNormalDrops, false));
		}
		final double standardDropChance = getCosmicNecklaceStandardDropChance(owner);
		if (allowExtraRoll && standardDropChance > 0.0D && DataConversions.getRandom().nextDouble() < standardDropChance) {
			owner.playerServerMessage(MessageType.QUEST, "@ora@Your cosmic necklace gleams.");
			items.addAll(rollItem(0.0D, owner, false, contributionScale, scaleRareNormalDrops, true));
		}
		return items;
	}

	private int getRollWeight(final Drop drop, final Player owner) {
		if (drop.weight <= 0) {
			return drop.weight;
		}
		if (owner != null && drop.type == dropType.TABLE && drop.table.isRare()) {
			return Math.max(1, (int) Math.round(drop.weight * owner.getRareTableWeightMultiplier()));
		}
		return drop.weight;
	}

	private static boolean passesContributionGate(double contributionScale) {
		double scaledChance = Math.max(MINIMUM_RARE_CONTRIBUTION_SCALE, Math.min(1.0D, contributionScale));
		return DataConversions.getRandom().nextDouble() < scaledChance;
	}

	private static boolean isRareNormalDrop(Drop drop) {
		return drop.weight > 0
			&& (drop.weight <= RARE_NORMAL_DROP_MAX_WEIGHT || RARE_NORMAL_DROP_IDS.contains(drop.id));
	}

	private static Set<Integer> createRareNormalDropIds() {
		HashSet<Integer> itemIds = new HashSet<>();
		itemIds.add(ItemId.LARGE_RUNE_HELMET.id());
		itemIds.add(ItemId.RUNE_LONG_SWORD.id());
		itemIds.add(ItemId.RUNE_AXE.id());
		itemIds.add(ItemId.RUNE_BATTLE_AXE.id());
		itemIds.add(ItemId.RUNE_KITE_SHIELD.id());
		itemIds.add(ItemId.RUNE_2_HANDED_SWORD.id());
		itemIds.add(ItemId.RUNE_PLATE_MAIL_BODY.id());
		itemIds.add(ItemId.RUNE_PLATE_MAIL_LEGS.id());
		itemIds.add(ItemId.RUNE_DAGGER.id());
		itemIds.add(ItemId.RUNE_SCIMITAR.id());
		itemIds.add(ItemId.RUNE_SPEAR.id());
		itemIds.add(ItemId.RUNITE_BAR.id());
		itemIds.add(ItemId.DRAGON_SWORD.id());
		itemIds.add(ItemId.DRAGON_AXE.id());
		itemIds.add(ItemId.DRAGON_SQUARE_SHIELD.id());
		itemIds.add(ItemId.DRAGON_MEDIUM_HELMET.id());
		itemIds.add(ItemId.LEFT_HALF_DRAGON_SQUARE_SHIELD.id());
		itemIds.add(ItemId.TOOTH_KEY_HALF.id());
		itemIds.add(ItemId.LOOP_KEY_HALF.id());
		itemIds.add(ItemId.DRAGONSTONE.id());
		return itemIds;
	}

	private double getWealthChance(Player owner) {
		if (owner == null) {
			return 0.0D;
		}
		if (owner.getConfig().WANT_EQUIPMENT_TAB) {
			final Item ring = owner.getCarriedItems().getEquipment().getRingItem();
			return ring == null ? 0.0D : EnchantingItemEffects.getWealthAdditionalRollChance(ring.getCatalogId());
		}
		for (Item item : owner.getCarriedItems().getInventory().getItems()) {
			if (item != null && item.isWielded()
				&& item.getDef(owner.getWorld()).getWieldPosition() == Equipment.EquipmentSlot.SLOT_RING.getIndex()) {
				return EnchantingItemEffects.getWealthAdditionalRollChance(item.getCatalogId());
			}
		}
		return 0.0D;
	}

	private double getCosmicNecklaceStandardDropChance(Player owner) {
		if (owner == null) {
			return 0.0D;
		}
		return owner.getCarriedItems().getEquipment().getCosmicNecklaceStandardDropChance();
	}

	public ArrayList<Item> invariableItems(Player owner) {
		int total = 0;
		ArrayList<Item> items = new ArrayList<>();
		Iterator<Drop> it = drops.iterator();
		while (it.hasNext()) {
			Drop drop = it.next();
			total = total + drop.weight;
			if (drop.weight == 0 && drop.id != ItemId.NOTHING.id()) {

				Item item;
				if (owner.getConfig().BASED_CONFIG_DATA < 50 && Formulae.isGeneralMeat(new Item(drop.id))) {
					item = new Item(ItemId.RAW_CHICKEN.id(), drop.amount, drop.noted);
				} else {
					item = new Item(drop.id, drop.amount, drop.noted);
				}

				// Remove from the table once it's dropped.
				it.remove();

				// If Ring of Avarice (custom) is equipped, and the item is a stack,
				// we will award the item with slightly different logic.
				if (handleRingOfAvarice(owner, item)) continue;

				items.add(item);
			}
		}
		return items;
	}

	public static boolean handleRingOfAvarice(final Player player, final Item item) {
		int slot = -1;
		if (player.getCarriedItems().getEquipment().hasEquipped(ItemId.RING_OF_AVARICE.id())) {
			ItemDefinition itemDef = player.getWorld().getServer().getEntityHandler().getItemDef(item.getCatalogId());
			if (itemDef != null && itemDef.isStackable()) {
				if (player.getCarriedItems().getInventory().hasInInventory(item.getCatalogId())) {
					player.getCarriedItems().getInventory().add(item);
					return true;
				} else if (player.getConfig().WANT_EQUIPMENT_TAB && (slot = player.getCarriedItems().getEquipment().searchEquipmentForItem(item.getCatalogId())) != -1) {
					Item equipped = player.getCarriedItems().getEquipment().get(slot);
					equipped.changeAmount(item.getAmount());
					return true;
				} else {
					if (player.getCarriedItems().getInventory().getFreeSlots() > 0) {
						player.getCarriedItems().getInventory().add(item);
						return true;
					} else {
						player.message("Your ring of Avarice tried to activate, but your inventory was full.");
					}
				}
			}
		}
		return false;
	}

	private void checkValuableDrop(int dropID, int amount, int weight, int weightTotal, Player owner) {
		// Check if we have a "valuable drop" (configurable)
		Item temp = new Item(dropID);
		double currentRatio = (double) weight / (double) weightTotal;
		if (dropID != com.openrsc.server.constants.ItemId.NOTHING.id() &&
			amount > 0 &&
			(
				currentRatio > owner.getWorld().getServer().getConfig().VALUABLE_DROP_RATIO ||
					(
						owner.getWorld().getServer().getConfig().VALUABLE_DROP_EXTRAS &&
							owner.getWorld().getServer().getConfig().valuableDrops.contains(temp.getDef(owner.getWorld()).getName())
					)
			)
		) {
			if (amount > 1) {
				owner.message("@red@Valuable drop: " + amount + " x " + temp.getDef(owner.getWorld()).getName() + " (" +
					(temp.getDef(owner.getWorld()).getDefaultPrice() * amount) + " coins)");
				owner.getWorld().getServer().getGameLogger().addQuery(new LiveFeedLog(owner, "has obtained " + amount
					+ " x " + temp.getDef(owner.getWorld()).getName() + "!"));
			} else {
				owner.message("@red@Valuable drop: " + temp.getDef(owner.getWorld()).getName() + " (" +
					(temp.getDef(owner.getWorld()).getDefaultPrice()) + " coins)");
				owner.getWorld().getServer().getGameLogger().addQuery(new LiveFeedLog(owner, "has obtained a "
					+ temp.getDef(owner.getWorld()).getName() + "!"));
			}
		}
	}

	public enum dropType {
		NOTHING,
		ITEM,
		TABLE;
	}

	public static class Accessor {
		int id;
		int numerator;
		int denominator;

		public Accessor(int id, int numerator, int denominator) {
			this.id = id;
			this.numerator = numerator;
			this.denominator = denominator;
		}
	}

	public boolean rollAccess(int id, boolean ringOfWealth) {
		return rollAccess(id);
	}

	public boolean rollAccess(int id) {
		int numerator, denominator;
		for (Accessor mob : accessors) {
			if (mob.id == id) {
				numerator = mob.numerator;
				denominator = mob.denominator;
				int hit = DataConversions.random(1, denominator);
				return hit <= numerator;
			}
		}
		return false;
	}

	private static class Drop {
		DropTable table = null;
		dropType type;
		int id = -1;
		int amount = 0;
		int weight;
		boolean noted;

		private Drop(int itemID, int amount, int weight, boolean noted, dropType type) {
			this.id = itemID;
			this.amount = amount;
			this.weight = weight;
			this.noted = noted;
			this.type = type;
		}

		private Drop(DropTable table, int weight) {
			this.type = dropType.TABLE;
			this.weight = weight;
			this.table = table;
		}

		@Override
		public String toString() {
			return "Drop{" +
				"table=" + table +
				", type=" + type +
				", id=" + id +
				", amount=" + amount +
				", weight=" + weight +
				", noted=" + noted +
				'}';
		}
	}
}
