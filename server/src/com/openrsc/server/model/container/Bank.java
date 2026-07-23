package com.openrsc.server.model.container;

import com.openrsc.server.constants.IronmanMode;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.struct.UnequipRequest;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

import static com.openrsc.server.plugins.Functions.validatebankpin;


public class Bank {
	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String ITEM_PINS_CACHE_KEY = "bank_item_pins";

	// TODO: Use an ItemContainer rather than a list here.
	private List<Item> list = Collections.synchronizedList(new ArrayList<>());
	private final NavigableMap<Integer, Integer> pinnedSlots = new TreeMap<>();
	private final Player player;
	private final BankPreset[] bankPresets;

	public Bank(final Player player) {
		this.player = player;
		this.bankPresets = new BankPreset[BankPreset.PRESET_COUNT];
		for (int i = 0; i < bankPresets.length; ++i)
			bankPresets[i] = new BankPreset(player);
	}

	/**
	 * Loads the metadata-only pinned slot map after both bank rows and the
	 * persistent player cache have been loaded.
	 */
	public void loadPinnedSlotsFromCache() {
		synchronized (list) {
			pinnedSlots.clear();
			if (player.getCache().hasKey(ITEM_PINS_CACHE_KEY)) {
				try {
					pinnedSlots.putAll(BankPinLayout.parse(
						player.getCache().getString(ITEM_PINS_CACHE_KEY)));
				} catch (RuntimeException ex) {
					LOGGER.warn("Ignoring invalid bank item pin metadata for player {}", player.getDatabaseID());
				}
			}
			normalizePinnedSlots();
			persistPinnedSlots();
		}
	}

	/**
	 * Returns the logical custom-bank view. Empty pins have a null item and a
	 * positive catalog ID; no zero-quantity Item enters server ownership.
	 */
	public List<DisplaySlot> getDisplaySlots() {
		synchronized (list) {
			return getDisplaySlotsInternal();
		}
	}

	public DisplaySlot getDisplaySlot(int slot) {
		synchronized (list) {
			final List<DisplaySlot> displaySlots = getDisplaySlotsInternal();
			return slot >= 0 && slot < displaySlots.size() ? displaySlots.get(slot) : null;
		}
	}

	public Item getDisplayItem(int slot) {
		final DisplaySlot displaySlot = getDisplaySlot(slot);
		return displaySlot == null ? null : displaySlot.getItem();
	}

	public int getUsedSlotCount() {
		synchronized (list) {
			return getDisplaySlotsInternal().size();
		}
	}

	public boolean pinDisplaySlot(int slot, int expectedCatalogId) {
		synchronized (list) {
			final DisplaySlot displaySlot = getDisplaySlotInternal(slot);
			if (displaySlot == null || displaySlot.getItem() == null || displaySlot.isPinned()
				|| displaySlot.getCatalogId() != expectedCatalogId
				|| pinnedSlots.containsValue(expectedCatalogId)) {
				return false;
			}
			pinnedSlots.put(slot, expectedCatalogId);
			normalizePinnedSlots();
			persistPinnedSlots();
			return true;
		}
	}

	public boolean unpinDisplaySlot(int slot, int expectedCatalogId) {
		synchronized (list) {
			final DisplaySlot displaySlot = getDisplaySlotInternal(slot);
			if (displaySlot == null || !displaySlot.isPinned()
				|| displaySlot.getCatalogId() != expectedCatalogId) {
				return false;
			}
			final boolean wasEmpty = displaySlot.getItem() == null;
			pinnedSlots.remove(slot);
			if (wasEmpty) {
				shiftPinsAfterRemovedDisplaySlot(slot);
			}
			normalizePinnedSlots();
			persistPinnedSlots();
			return true;
		}
	}

	private List<DisplaySlot> getDisplaySlotsInternal() {
		final List<Integer> catalogIds = new ArrayList<>(list.size());
		for (Item item : list) {
			catalogIds.add(item.getCatalogId());
		}
		final BankPinLayout.Layout layout = BankPinLayout.build(
			catalogIds, pinnedSlots, player.getWorld().getMaxBankSize());
		if (!layout.getPins().equals(pinnedSlots)) {
			pinnedSlots.clear();
			pinnedSlots.putAll(layout.getPins());
		}
		final List<DisplaySlot> displaySlots = new ArrayList<>(layout.getSlots().size());
		for (BankPinLayout.Slot slot : layout.getSlots()) {
			final Item item = slot.getSourceIndex() >= 0 ? list.get(slot.getSourceIndex()) : null;
			displaySlots.add(new DisplaySlot(item, slot.getCatalogId(), slot.isPinned()));
		}
		return displaySlots;
	}

	private DisplaySlot getDisplaySlotInternal(int slot) {
		final List<DisplaySlot> displaySlots = getDisplaySlotsInternal();
		return slot >= 0 && slot < displaySlots.size() ? displaySlots.get(slot) : null;
	}

	private int findDisplaySlotByIdentity(Item item) {
		final List<DisplaySlot> displaySlots = getDisplaySlotsInternal();
		for (int slot = 0; slot < displaySlots.size(); slot++) {
			if (displaySlots.get(slot).getItem() == item) {
				return slot;
			}
		}
		return -1;
	}

	private boolean hasEmptyPin(int catalogId) {
		for (DisplaySlot slot : getDisplaySlotsInternal()) {
			if (slot.isPinned() && slot.getItem() == null && slot.getCatalogId() == catalogId) {
				return true;
			}
		}
		return false;
	}

	private void normalizePinnedSlots() {
		pinnedSlots.entrySet().removeIf(entry ->
			player.getWorld().getServer().getEntityHandler().getItemDef(entry.getValue()) == null);
		final List<Integer> catalogIds = new ArrayList<>(list.size());
		for (Item item : list) {
			catalogIds.add(item.getCatalogId());
		}
		final NavigableMap<Integer, Integer> normalized = BankPinLayout.build(
			catalogIds, pinnedSlots, player.getWorld().getMaxBankSize()).getPins();
		pinnedSlots.clear();
		pinnedSlots.putAll(normalized);
	}

	private void persistPinnedSlots() {
		if (pinnedSlots.isEmpty()) {
			player.getCache().remove(ITEM_PINS_CACHE_KEY);
		} else {
			player.getCache().store(ITEM_PINS_CACHE_KEY, BankPinLayout.serialize(pinnedSlots));
		}
	}

	private void shiftPinsAfterRemovedDisplaySlot(int removedSlot) {
		final NavigableMap<Integer, Integer> shifted =
			BankPinLayout.shiftAfterRemoval(pinnedSlots, removedSlot);
		pinnedSlots.clear();
		pinnedSlots.putAll(shifted);
	}

	private boolean supportsPinnedBankDisplay() {
		return player.isUsingCustomClient()
			&& player.getConfig().WANT_CUSTOM_BANKS
			&& player.getClientLimitations().supportsBankItemPinning;
	}

	private void updateCustomBankOrSlot(boolean updateClient, int slot, Item item, int amount) {
		if (!updateClient) {
			return;
		}
		if (supportsPinnedBankDisplay() && !pinnedSlots.isEmpty()) {
			ActionSender.showBank(player);
		} else {
			ActionSender.updateBankItem(player, slot, item, amount);
		}
	}

	public static final class DisplaySlot {
		private final Item item;
		private final int catalogId;
		private final boolean pinned;

		private DisplaySlot(Item item, int catalogId, boolean pinned) {
			this.item = item;
			this.catalogId = catalogId;
			this.pinned = pinned;
		}

		public Item getItem() {
			return item;
		}

		public int getCatalogId() {
			return catalogId;
		}

		public int getAmount() {
			return item == null ? 0 : item.getAmount();
		}

		public boolean isPinned() {
			return pinned;
		}
	}

	/**
	 * Attempts to add the item to the player's Bank.
	 * Updates the database.
	 */
	public boolean add(Item itemToAdd) { return add(itemToAdd, true); }

	public boolean add(Item itemToAdd, boolean updateClient) {
		synchronized(list) {
			// Check bounds of amount
			if (itemToAdd.getAmount() <= 0) {
				return false;
			}

			// Determine if there's already a spot in the bank for this item
			Item existingStack = null;
			int index = -1;

			for (Item bankItem : list) {
				++index;
				// Check for matching catalog ID's
				if (bankItem.getCatalogId() != itemToAdd.getCatalogId())
					continue;

				// Make sure the existing stack has room for more
				if (bankItem.getAmount() == Integer.MAX_VALUE)
					continue;

				// An existing stack has been found, exit the loop
				existingStack = bankItem;
				break;
			}

			if (player.getWorld().getPlayer(DataConversions.usernameToHash(player.getUsername())) == null) {
				return false;
			}

			// There is none of this item in the bank yet - create a new stack.
			if (existingStack == null) {
				// Make sure they have room in the bank
				if (getUsedSlotCount() >= player.getWorld().getMaxBankSize()
					&& !hasEmptyPin(itemToAdd.getCatalogId()))
					return false;

				long itemID = player.getWorld().getServer().getDatabase().incrementMaxItemId(player);
				itemToAdd = copyItemForBank(itemToAdd, itemToAdd.getAmount(), itemID);

				// Update the server bank
				list.add(itemToAdd);

				// Update the client bank
				updateCustomBankOrSlot(updateClient, list.size() - 1, itemToAdd, itemToAdd.getAmount());

			// A stack exists of this item in the bank already.
			} else {

				// We will always update the existing stack, but if it overflows we need a second stack.
				int remainingSize = Integer.MAX_VALUE - existingStack.getAmount();
				if (remainingSize < itemToAdd.getAmount()
					&& getUsedSlotCount() >= player.getWorld().getMaxBankSize()) {
					return false;
				}

				// In the first case, we have enough space to fit what we are depositing.
				if (remainingSize >= itemToAdd.getAmount()) {

					// Update the database and server bank
					existingStack.changeAmount(itemToAdd.getAmount());
					rememberLastItemDurability(existingStack, itemToAdd);

					// Update the client bank
					updateCustomBankOrSlot(updateClient, index, existingStack, existingStack.getAmount());

				// In the second case, we must made a new stack as well as updating the old one. (First is full.)
				} else {

					// Update the database - first (existing) stack amount to max value
					existingStack.setAmount(Integer.MAX_VALUE);
					rememberLastItemDurability(existingStack, itemToAdd);

					// Adjust quantity of second stack to reflect that which was added to the first stack.
					long itemID = player.getWorld().getServer().getDatabase().incrementMaxItemId(player);
					itemToAdd = copyItemForBank(itemToAdd, itemToAdd.getAmount() - remainingSize, itemID);

					// Update the server bank - second stack
					list.add(itemToAdd);

					// Update the client - both stacks
					if (updateClient) {
						if (supportsPinnedBankDisplay()) {
							ActionSender.showBank(player);
						} else {
							ActionSender.updateBankItem(player, index, existingStack, Integer.MAX_VALUE);
							ActionSender.updateBankItem(player, list.size() - 1, itemToAdd, itemToAdd.getAmount());
						}
					}
				}
			}
			return true;
		}
	}

	public boolean remove(Item item) {
		return remove(item, true);
	}

	public boolean remove(final Item item, final boolean updateClient) {
		return this.remove(item.getCatalogId(), item.getAmount(), updateClient);
	}

	public boolean remove(int catalogID, int amount) {
		return remove(catalogID, amount, true);
	}

	public boolean remove(final int catalogID, final int amount, final boolean updateClient) {
		synchronized(this.list) {
			if (this.player.getWorld().getPlayer(DataConversions.usernameToHash(this.player.getUsername())) == null)
				return false;

			int bankItemIndex;
			Item bankItem = null;

			for (bankItemIndex = this.list.size() - 1; bankItemIndex >= 0; bankItemIndex--) {
				final Item item = this.list.get(bankItemIndex);

				if (item.getCatalogId() == catalogID) {
					bankItem = item;
					break;
				}
			}

			if (bankItem == null) return false;

			final int amountToRemove = Math.min(amount, bankItem.getAmount());
			final int displaySlot = findDisplaySlotByIdentity(bankItem);
			final boolean removingPinnedItem = displaySlot >= 0
				&& getDisplaySlotInternal(displaySlot).isPinned();

			if (amountToRemove == bankItem.getAmount()) {
				this.list.remove(bankItemIndex);
				if (!removingPinnedItem && displaySlot >= 0) {
					shiftPinsAfterRemovedDisplaySlot(displaySlot);
				}
				normalizePinnedSlots();
				persistPinnedSlots();
				updateCustomBankOrSlot(updateClient, bankItemIndex, bankItem, 0);
			} else {
				bankItem.setAmount(bankItem.getAmount() - amountToRemove);

				updateCustomBankOrSlot(updateClient, bankItemIndex, bankItem, bankItem.getAmount());
			}

			return true;
		}
	}

	public boolean canRemoveAtLeast1(int catalogID) {
		int bankIndex = getFirstIndexById(catalogID);
		Item bankItem = get(bankIndex);

		// Continue until a matching catalogID is found.
		if (bankItem == null) return false;

		if (player.getWorld().getPlayer(DataConversions.usernameToHash(player.getUsername())) == null) {
			return false;
		}

		return true;
	}

	public boolean canHold(Item item) {
		synchronized(list) {
			return (getPlayer().getWorld().getMaxBankSize() - getUsedSlotCount()) >= getRequiredSlots(item);
		}
	}

	public boolean contains(Item i) {
		synchronized(list) {
			return list.contains(i);
		}
	}

	public int countId(int catalogID) {
		synchronized(list) {
			int ret = 0;
			for (Item i : list) {
				if (i.getCatalogId() == catalogID) {
					final int amount = i.getAmount();
					if (amount > Integer.MAX_VALUE - ret)
						return Integer.MAX_VALUE;
					ret += amount;
				}
			}
			return ret;
		}
	}

	public boolean full() {
		synchronized(list) {
			return getUsedSlotCount() >= getPlayer().getWorld().getMaxBankSize();
		}
	}

	public Item get(int index) {
		synchronized(list) {
			if (index < 0 || index >= list.size()) {
				return null;
			}
			return list.get(index);
		}
	}

	public Item get(Item item) {
		synchronized(list) {
			for (Item i : list) {
				if (item.equals(i)) {
					return i;
				}
			}
			return null;
		}
	}

	public int getFirstIndexById(int id) {
		synchronized(list) {
			for (int index = 0; index < list.size(); index++) {
				if (list.get(index).getCatalogId() == id) {
					return index;
				}
			}
			return -1;
		}
	}

	public List<Item> getItems() {
		// TODO: This should be made private and all calls converted to use API on ItemContainer. This could stay public, IF we copy the list to a new list before returning.
		synchronized(list) {
			return list;
		}
	}

	public int getRequiredSlots(Item item) {
		synchronized(list) {
			//Check if there's a stack that can be added to
			for (Item bankItem : list) {
				//Check for matching catalogID
				if (bankItem.getCatalogId() != item.getCatalogId())
					continue;

				//Make sure there's room in the stack
				if (bankItem.getAmount() == Integer.MAX_VALUE)
					continue;

				//Check if all of the stack can fit in the existing stack
				int remainingSize = Integer.MAX_VALUE - bankItem.getAmount();
				return remainingSize < item.getAmount() ? 1 : 0;
			}

			if (hasEmptyPin(item.getCatalogId())) {
				return 0;
			}

			//No existing stack was found
			return 1;
		}
	}

	public boolean hasItemId(int id) {
		synchronized(list) {
			for (Item i : list) {
				if (i.getCatalogId() == id)
					return true;
			}

			return false;
		}
	}

	public ListIterator<Item> iterator() {
		synchronized(list) {
			return list.listIterator();
		}
	}

	public int size() {
		synchronized(list) {
			return list.size();
		}
	}

	public boolean swap(int slot, int to) {
		synchronized(list) {
			return rearrangeDisplaySlots(slot, to, false);
		}
	}

	public boolean insert(int slot, int to) {
		synchronized(list) {
			return rearrangeDisplaySlots(slot, to, true);
		}
	}

	private boolean rearrangeDisplaySlots(int from, int to, boolean insert) {
		final List<Item> sourceItems = new ArrayList<>(list);
		final List<Integer> catalogIds = new ArrayList<>(sourceItems.size());
		for (Item item : sourceItems) {
			catalogIds.add(item.getCatalogId());
		}
		final BankPinLayout.Rearrangement rearrangement = BankPinLayout.rearrange(
			catalogIds, pinnedSlots, player.getWorld().getMaxBankSize(), from, to, insert);
		if (rearrangement == null) {
			return false;
		}
		list.clear();
		for (Integer sourceIndex : rearrangement.getSourceOrder()) {
			list.add(sourceItems.get(sourceIndex));
		}
		pinnedSlots.clear();
		pinnedSlots.putAll(rearrangement.getPins());
		normalizePinnedSlots();
		persistPinnedSlots();
		return true;
	}

	/**
	 * Retained compatibility hook for callers that model bank tabs. Server-side
	 * bank storage has no tab state; the active client owns tab presentation.
	 */
	public void setTab(int tab) {
		// Intentionally no-op.
	}


	public void depositAllFromInventory() {
		synchronized (list) {
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				try {
					for (int i = player.getCarriedItems().getInventory().getItems().size(); i-- > 0;) {
						Item item = player.getCarriedItems().getInventory().getItems().get(i);
						depositItemFromInventory(item.getCatalogId(), item.getAmount(), true);
					}
				} catch (Exception ex) {
					LOGGER.error(ex.getMessage());
				}
			}
		}
	}

	public void depositAllFromEquipment() {
		synchronized (list) {
			synchronized (player.getCarriedItems().getEquipment().getList()) {
				try {
					for (int slot = 0; slot < Equipment.SLOT_COUNT; slot++) {
						Item item = player.getCarriedItems().getEquipment().get(slot);
						if (item == null || item.getCatalogId() == ItemId.NOTHING.id()) continue;
						UnequipRequest uer = new UnequipRequest(player, item, UnequipRequest.RequestType.FROM_BANK, false);
						uer.equipmentSlot = Equipment.EquipmentSlot.get(slot);
						//Equipment.correctIndex(uer);
						if (!player.getCarriedItems().getEquipment().unequipItem(uer)) {
							player.message("Failed to bank: " + item.getDef(player.getWorld()).getName());
							return;
						}
					}
				}
				catch (Exception ex) {
					LOGGER.error(ex.getMessage());
				}
			}
		}
	}

	/**
	** The bank works in slots.
	 * When you withdraw non-stack items, the bank will update with the value all at once, and the inventory
	 * will "trickle" the items into it slot-by-slot.
	 * When you withdraw stack items, it will withdraw the full quantity at once, updating the inventory AND bank stack
	 * only one time.
	 */
	public void withdrawItemToInventory(final Integer catalogID, final Integer requestedAmount,
										final Boolean wantsNotes) {
		this.withdrawItemToInventory(catalogID, requestedAmount, wantsNotes, true);
	}

	public void withdrawItemToInventory(final Integer catalogID, final Integer requestedAmount,
										final Boolean wantsNotes, final boolean updateClient) {
		synchronized (this.list) {
			synchronized (this.player.getCarriedItems().getInventory().getItems()) {
				if (this.list.isEmpty()) return;

				Item bankItem = null;

				for (int i = this.list.size() - 1; i >= 0; i--) {
					final Item item = this.list.get(i);

					if (item.getCatalogId() == catalogID) {
						bankItem = item;
						break;
					}
				}

				if (bankItem == null) return;

				int amountToWithdraw = Math.min(requestedAmount, bankItem.getAmount());

				final ItemDefinition itemDef = bankItem.getDef(this.player.getWorld());
				if (itemDef == null) return;

				final boolean withdrawNoted = wantsNotes && itemDef.isNoteable();

				final int requiredInventorySlots = this.player.getCarriedItems()
					.getInventory()
					.getRequiredSlots(bankItem.getCatalogId(), amountToWithdraw, withdrawNoted);

				final int freeInventorySlots = this.player.getCarriedItems().getInventory().getFreeSlots();

				boolean limitedSlots = false;

				if (requiredInventorySlots > freeInventorySlots) {
					if (itemDef.isStackable() || withdrawNoted) {
						this.player.message("You don't have room to hold everything!");
						return;
					}

					amountToWithdraw = freeInventorySlots;
					limitedSlots = true;
				}

				final Item item = new Item(bankItem.getCatalogId(), amountToWithdraw, withdrawNoted,
					bankItem.getItemId());
				item.getItemStatus().setDurability(bankItem.getItemStatus().getDurability());

				if (this.player.isUsingCustomClient()) {
					if (!this.remove(item, updateClient)) return;

					this.addToInventory(item, itemDef, amountToWithdraw, updateClient);

					if (limitedSlots && requestedAmount > amountToWithdraw)
						this.player.message("You don't have room to hold everything!");

					return;
				}

				// Authentic client requires bank update to happen after adding to inventory,
				// in order to display properly
				this.addToInventory(item, itemDef, amountToWithdraw, updateClient);

				if (limitedSlots && requestedAmount > amountToWithdraw)
					this.player.message("You don't have room to hold everything!");

				// TODO: there are safeguards here which might be fine, but it may be better to
				// implement a way to sort the packets in Player.outgoingPackets instead?
				// Not sure how Jagex would have done it.
				try {
					if (!this.remove(item, updateClient))
						this.removeFromInventory(item, itemDef, amountToWithdraw, updateClient);
				} catch (final Exception e) {
					// Possibly the database is unavailable?
					// Not sure, but it's important to not halt execution mid-remove() if an exception happens.
					LOGGER.error(e.getMessage(), e);
					this.removeFromInventory(item, itemDef, amountToWithdraw, updateClient);
				}
			}
		}
	}

	public void depositItemFromInventory(final int catalogID, int requestedAmount, final Boolean updateClient) {
		synchronized (list) {
			List<Item> items = player.getCarriedItems().getInventory().getItems();
			synchronized (items) {

				// Ensure they have the item in their inventory.
				requestedAmount = Math.min(requestedAmount, player.getCarriedItems().getInventory().countId(catalogID, Optional.empty()));
				if (requestedAmount <= 0) {
					if (!player.isUsingCustomClient() && catalogID == 1030) { //shantay pass placeholder item
						player.playerServerMessage(MessageType.QUEST, "Try using the note on the Banker instead.");
					}
					return;
				}

				Item depositItem = player.getCarriedItems().getInventory().get(
					player.getCarriedItems().getInventory().getLastIndexById(catalogID)
				);
				if (depositItem == null) return;

				// To deal with uncerting from the bank we must
				// check for an uncerted id IFF the swap_cert flag
				// has been toggled.
				int itemToAddCatalogId = depositItem.getCatalogId();
				int itemToAddAmount = requestedAmount;

				if (player.getConfig().WANT_CERT_DEPOSIT && player.getAttribute("swap_cert", false)) {
					itemToAddCatalogId = uncertedID(itemToAddCatalogId);

					if (itemToAddCatalogId != depositItem.getCatalogId()) {
						itemToAddAmount *= 5;
					}
				}

				Item itemToAdd = depositItem.copy();
				itemToAdd.setCatalogId(itemToAddCatalogId);
				itemToAdd.setAmount(itemToAddAmount);
				itemToAdd.setNoted(false);
				itemToAdd.setWielded(false);

				// Make sure they have enough space in their bank to deposit it
				if (!canHold(itemToAdd)) {
					player.message("You don't have room for that in your bank");
					return;
				}

				// Attempt to add the item to the bank (or fail out).
				if (!add(itemToAdd, updateClient)) return;

				// TODO: technically, similar to withdrawItemFromInventory, the authentic client
				// should have the bank_update & inventory_update packets reversed here
				// but it actually shouldn't visually matter, so it's a TODO.

				// Check the item definition
				ItemDefinition depositDef = depositItem.getDef(player.getWorld());
				if (depositDef == null) return;
				removeFromInventory(depositItem, depositDef, requestedAmount, updateClient);
			}
		}
	}

	// Add the items to the inventory one slot at a time or all at once if stackable/noted.
	private void addToInventory(Item item, ItemDefinition def, int requestedAmount, boolean updateClient) {
		final boolean sendSlotUpdates = updateClient && !player.isUsingCustomClient();
		if (def.isStackable() || item.getNoted()) {
			item = copyItemForTransfer(item, requestedAmount, item.getNoted());

			if (!player.getCarriedItems().getInventory().canHold(item)) {
				player.message("You don't have room to hold everything!");
				return;
			}

			// Add the item to the inventory (or fail and place it back into the bank).
			if (!player.getCarriedItems().getInventory().add(item, sendSlotUpdates)) {
				// If the client doesn't support the item, DON'T ADD IT BACK because it dropped on the ground.
				if (player.getClientLimitations().maxItemId < item.getCatalogId()) {
					return;
				}
				add(item);
			}
		} else {
			for (int i = 1; i <= requestedAmount; i++) {
				item = copyItemForTransfer(item, 1, item.getNoted());

				if (!player.getCarriedItems().getInventory().canHold(item)) {
					player.message("You don't have room to hold everything!");
					return;
				}

				// Add the item to the inventory (or fail and place it back into the bank).
				if (!player.getCarriedItems().getInventory().add(item, sendSlotUpdates)) {
					// If the client doesn't support the item, DON'T ADD IT BACK because it dropped on the ground.
					if (player.getClientLimitations().maxItemId < item.getCatalogId()) {
						break;
					}
					add(item);
					break;
				}
			}
		}
		if (updateClient && player.isUsingCustomClient()) {
			ActionSender.sendInventory(player);
		}
	}

	// Remove the items from the inventory one slot at a time.
	private void removeFromInventory(Item item, ItemDefinition def, int requestedAmount, boolean updateClient) {
		int slotAmount = 1;
		if (def.isStackable() || item.getNoted()) {
			slotAmount = Math.min(requestedAmount, item.getAmount());
		}

		// Always remove the last slot first.
		item = copyItemForTransfer(item, slotAmount, item.getNoted());
		if (player.getCarriedItems().getInventory().remove(item, updateClient) == -1) return;

		if (slotAmount < requestedAmount) {
			// Get next item
			item = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(item.getCatalogId())
			);
			removeFromInventory(item, def, requestedAmount - slotAmount, updateClient);
		}
	}

	private static int uncertedID(int itemID) {
		if (itemID == ItemId.IRON_ORE_CERTIFICATE.id()) {
			return ItemId.IRON_ORE.id();
		} else if (itemID == ItemId.COAL_CERTIFICATE.id()) {
			return ItemId.COAL.id();
		} else if (itemID == ItemId.MITHRIL_ORE_CERTIFICATE.id()) {
			return ItemId.MITHRIL_ORE.id();
		} else if (itemID == ItemId.SILVER_CERTIFICATE.id()) {
			return ItemId.SILVER.id();
		} else if (itemID == ItemId.GOLD_CERTIFICATE.id()) {
			return ItemId.GOLD.id();
		} else if (itemID == ItemId.IRON_BAR_CERTIFICATE.id()) {
			return ItemId.IRON_BAR.id();
		} else if (itemID == ItemId.STEEL_BAR_CERTIFICATE.id()) {
			return ItemId.STEEL_BAR.id();
		} else if (itemID == ItemId.MITHRIL_BAR_CERTIFICATE.id()) {
			return ItemId.MITHRIL_BAR.id();
		} else if (itemID == ItemId.SILVER_BAR_CERTIFICATE.id()) {
			return ItemId.SILVER_BAR.id();
		} else if (itemID == ItemId.GOLD_BAR_CERTIFICATE.id()) {
			return ItemId.GOLD_BAR.id();
		} else if (itemID == ItemId.LOBSTER_CERTIFICATE.id()) {
			return ItemId.LOBSTER.id();
		} else if (itemID == ItemId.RAW_LOBSTER_CERTIFICATE.id()) {
			return ItemId.RAW_LOBSTER.id();
		} else if (itemID == ItemId.SWORDFISH_CERTIFICATE.id()) {
			return ItemId.SWORDFISH.id();
		} else if (itemID == ItemId.RAW_SWORDFISH_CERTIFICATE.id()) {
			return ItemId.RAW_SWORDFISH.id();
		} else if (itemID == ItemId.BASS_CERTIFICATE.id()) {
			return ItemId.BASS.id();
		} else if (itemID == ItemId.RAW_BASS_CERTIFICATE.id()) {
			return ItemId.RAW_BASS.id();
		} else if (itemID == ItemId.SHARK_CERTIFICATE.id()) {
			return ItemId.SHARK.id();
		} else if (itemID == ItemId.RAW_SHARK_CERTIFICATE.id()) {
			return ItemId.RAW_SHARK.id();
		} else if (itemID == ItemId.YEW_LOGS_CERTIFICATE.id()) {
			return ItemId.YEW_LOGS.id();
		} else if (itemID == ItemId.MAPLE_LOGS_CERTIFICATE.id()) {
			return ItemId.MAPLE_LOGS.id();
		} else if (itemID == ItemId.WILLOW_LOGS_CERTIFICATE.id()) {
			return ItemId.WILLOW_LOGS.id();
		} else if (itemID == ItemId.DRAGON_BONE_CERTIFICATE.id()) {
			return ItemId.DRAGON_BONES.id();
		} else if (itemID == ItemId.LIMPWURT_ROOT_CERTIFICATE.id()) {
			return ItemId.LIMPWURT_ROOT.id();
		} else {
			return itemID;
		}
	}

	private static Item copyItemForBank(final Item source, final int amount, final long itemId) {
		final Item copy = source.copyWithItemId(itemId);
		copy.setAmount(amount);
		copy.setNoted(false);
		copy.setWielded(false);
		return copy;
	}

	private static Item copyItemForTransfer(final Item source, final int amount, final boolean noted) {
		final Item copy = source.copy();
		copy.setAmount(amount);
		copy.setNoted(noted);
		copy.setWielded(false);
		return copy;
	}

	private static void rememberLastItemDurability(final Item bankItem, final Item depositedItem) {
		bankItem.getItemStatus().setDurability(depositedItem.getItemStatus().getDurability());
	}

	public Player getPlayer() {
		return player;
	}

	public BankPreset getBankPreset(int slot) { return this.bankPresets[slot]; }

	public void quickFeature(Npc npc, Player player, boolean auction) {
		if (player.isIronMan(IronmanMode.Ultimate.id())) {
			player.message("As an Ultimate Ironman, you cannot use the bank.");
			return;
		}

		if(validatebankpin(player, npc)) {
			if (auction) {
				player.getWorld().getMarket().addPlayerCollectItemsTask(player);
			} else {
				if (player.getConfig().BATCH_PROGRESSION && player.hasActiveBatch()) {
					player.message("Please finish what you're doing to access your bank.");
					return;
				}
				player.setAccessingBank(true);
				ActionSender.showBank(player);
			}
		}
	}
}
