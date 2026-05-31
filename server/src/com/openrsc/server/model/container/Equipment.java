package com.openrsc.server.model.container;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.Devotion;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.event.rsc.impl.projectile.RangeUtils;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.model.struct.EquipRequest;
import com.openrsc.server.model.struct.UnequipRequest;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Optional;

public class Equipment {

	private static final Logger LOGGER = LogManager.getLogger();
	public static final int SLOT_COUNT = 14;
	private static final int ARMOR_POWER_PENALTY_PER_MAJOR_SLOT = 8;
	private final Item[] list = new Item[SLOT_COUNT];
	private final Player player;

	// These family buckets still back live compatibility behavior: chainmail sprite/body swaps,
	// plate normalization, and legacy-family combat bonuses for items that remain equipable.
	private static final int[] chainBodyIds = {
		ItemId.BRONZE_CHAIN_MAIL_BODY.id(),
		ItemId.IRON_CHAIN_MAIL_BODY.id(),
		ItemId.STEEL_CHAIN_MAIL_BODY.id(),
		ItemId.BLACK_CHAIN_MAIL_BODY.id(),
		ItemId.WHITE_CHAIN_MAIL_BODY.id(),
		ItemId.MITHRIL_CHAIN_MAIL_BODY.id(),
		ItemId.ADAMANTITE_CHAIN_MAIL_BODY.id(),
		ItemId.RUNE_CHAIN_MAIL_BODY.id(),
		ItemId.DRAGON_SCALE_MAIL.id()
	};
	private static final int[] chainTopIds = {
		ItemId.BRONZE_CHAIN_MAIL_TOP.id(),
		ItemId.IRON_CHAIN_MAIL_TOP.id(),
		ItemId.STEEL_CHAIN_MAIL_TOP.id(),
		ItemId.BLACK_CHAIN_MAIL_TOP.id(),
		ItemId.WHITE_CHAIN_MAIL_TOP.id(),
		ItemId.MITHRIL_CHAIN_MAIL_TOP.id(),
		ItemId.ADAMANTITE_CHAIN_MAIL_TOP.id(),
		ItemId.RUNE_CHAIN_MAIL_TOP.id(),
		ItemId.DRAGON_SCALE_MAIL_TOP.id()
	};
	private static final int[] plateBodyIds = {
		ItemId.BRONZE_PLATE_MAIL_BODY.id(),
		ItemId.IRON_PLATE_MAIL_BODY.id(),
		ItemId.STEEL_PLATE_MAIL_BODY.id(),
		ItemId.BLACK_PLATE_MAIL_BODY.id(),
		ItemId.WHITE_PLATE_MAIL_BODY.id(),
		ItemId.MITHRIL_PLATE_MAIL_BODY.id(),
		ItemId.ADAMANTITE_PLATE_MAIL_BODY.id(),
		ItemId.RUNE_PLATE_MAIL_BODY.id(),
		ItemId.DRAGON_PLATE_MAIL_BODY.id(),
		ItemId.IRONMAN_PLATEBODY.id(),
		ItemId.ULTIMATE_IRONMAN_PLATEBODY.id(),
		ItemId.HARDCORE_IRONMAN_PLATEBODY.id()
	};
	private static final int[] plateTopIds = {
		ItemId.BRONZE_PLATE_MAIL_TOP.id(),
		ItemId.IRON_PLATE_MAIL_TOP.id(),
		ItemId.STEEL_PLATE_MAIL_TOP.id(),
		ItemId.BLACK_PLATE_MAIL_TOP.id(),
		ItemId.WHITE_PLATE_MAIL_TOP.id(),
		ItemId.MITHRIL_PLATE_MAIL_TOP.id(),
		ItemId.ADAMANTITE_PLATE_MAIL_TOP.id(),
		ItemId.RUNE_PLATE_MAIL_TOP.id(),
		ItemId.DRAGON_PLATE_MAIL_TOP.id(),
		ItemId.IRONMAN_PLATE_TOP.id(),
		ItemId.ULTIMATE_IRONMAN_PLATE_TOP.id(),
		ItemId.HARDCORE_IRONMAN_PLATE_TOP.id()
	};
	private static final int[] plateLegIds = {
		ItemId.BRONZE_PLATE_MAIL_LEGS.id(),
		ItemId.IRON_PLATE_MAIL_LEGS.id(),
		ItemId.STEEL_PLATE_MAIL_LEGS.id(),
		ItemId.BLACK_PLATE_MAIL_LEGS.id(),
		ItemId.WHITE_PLATE_MAIL_LEGS.id(),
		ItemId.MITHRIL_PLATE_MAIL_LEGS.id(),
		ItemId.ADAMANTITE_PLATE_MAIL_LEGS.id(),
		ItemId.RUNE_PLATE_MAIL_LEGS.id(),
		ItemId.DRAGON_PLATE_MAIL_LEGS.id(),
		ItemId.IRONMAN_PLATELEGS.id(),
		ItemId.ULTIMATE_IRONMAN_PLATELEGS.id(),
		ItemId.HARDCORE_IRONMAN_PLATELEGS.id()
	};
	private static final int[] platedSkirtIds = {
		ItemId.BRONZE_PLATED_SKIRT.id(),
		ItemId.IRON_PLATED_SKIRT.id(),
		ItemId.STEEL_PLATED_SKIRT.id(),
		ItemId.BLACK_PLATED_SKIRT.id(),
		ItemId.WHITE_PLATED_SKIRT.id(),
		ItemId.MITHRIL_PLATED_SKIRT.id(),
		ItemId.ADAMANTITE_PLATED_SKIRT.id(),
		ItemId.RUNE_SKIRT.id(),
		ItemId.DRAGON_PLATED_SKIRT.id(),
		ItemId.IRONMAN_PLATED_SKIRT.id(),
		ItemId.ULTIMATE_IRONMAN_PLATED_SKIRT.id(),
		ItemId.HARDCORE_IRONMAN_PLATED_SKIRT.id()
	};
	private static final int[] mediumHelmetIds = {
		ItemId.MEDIUM_BRONZE_HELMET.id(),
		ItemId.MEDIUM_IRON_HELMET.id(),
		ItemId.MEDIUM_STEEL_HELMET.id(),
		ItemId.MEDIUM_BLACK_HELMET.id(),
		ItemId.MEDIUM_WHITE_HELMET.id(),
		ItemId.MEDIUM_MITHRIL_HELMET.id(),
		ItemId.MEDIUM_ADAMANTITE_HELMET.id(),
		ItemId.MEDIUM_RUNE_HELMET.id()
	};
	private static final int[] scimitarIds = {
		ItemId.TIN_SCIMITAR.id(), ItemId.COPPER_SCIMITAR.id(), ItemId.BRONZE_SCIMITAR.id(), ItemId.IRON_SCIMITAR.id(), ItemId.STEEL_SCIMITAR.id(),
		ItemId.BLACK_SCIMITAR.id(), ItemId.WHITE_SCIMITAR.id(), ItemId.MITHRIL_SCIMITAR.id(), ItemId.TITAN_STEEL_SCIMITAR.id(), ItemId.ADAMANTITE_SCIMITAR.id(), ItemId.ORICHALCUM_SCIMITAR.id(), ItemId.RUNE_SCIMITAR.id()
	};
	private static final int[] battleaxeIds = {
		ItemId.TIN_BATTLE_AXE.id(), ItemId.COPPER_BATTLE_AXE.id(), ItemId.BRONZE_BATTLE_AXE.id(), ItemId.IRON_BATTLE_AXE.id(), ItemId.STEEL_BATTLE_AXE.id(),
		ItemId.BLACK_BATTLE_AXE.id(), ItemId.WHITE_BATTLE_AXE.id(), ItemId.MITHRIL_BATTLE_AXE.id(), ItemId.TITAN_STEEL_BATTLE_AXE.id(), ItemId.ADAMANTITE_BATTLE_AXE.id(), ItemId.ORICHALCUM_BATTLE_AXE.id(), ItemId.RUNE_BATTLE_AXE.id(), ItemId.DRAGON_BATTLE_AXE.id()
	};
	private static final int[] cowHideSetIds = {
		ItemId.COW_HIDE_COIF.id(),
		ItemId.COW_HIDE_GLOVES.id(),
		ItemId.COW_HIDE_BOOTS.id(),
		ItemId.COW_HIDE_CHAPS.id(),
		ItemId.COW_HIDE_CUIRASS.id()
	};
	private static final int[] unicornHideSetIds = {
		ItemId.UNICORN_HIDE_COIF.id(),
		ItemId.UNICORN_HIDE_GLOVES.id(),
		ItemId.UNICORN_HIDE_BOOTS.id(),
		ItemId.UNICORN_HIDE_CHAPS.id(),
		ItemId.UNICORN_HIDE_CUIRASS.id()
	};
	private static final int[] blackUnicornHideSetIds = {
		ItemId.BLACK_UNICORN_HIDE_COIF.id(),
		ItemId.BLACK_UNICORN_HIDE_GLOVES.id(),
		ItemId.BLACK_UNICORN_HIDE_BOOTS.id(),
		ItemId.BLACK_UNICORN_HIDE_CHAPS.id(),
		ItemId.BLACK_UNICORN_HIDE_CUIRASS.id()
	};
	private static final int[] bearHideSetIds = {
		ItemId.BEAR_HIDE_COIF.id(),
		ItemId.BEAR_HIDE_GLOVES.id(),
		ItemId.BEAR_HIDE_BOOTS.id(),
		ItemId.BEAR_HIDE_CHAPS.id(),
		ItemId.BEAR_HIDE_CUIRASS.id()
	};
	private static final int[] goblinHideSetIds = {
		ItemId.GOBLIN_HIDE_COIF.id(),
		ItemId.GOBLIN_HIDE_GLOVES.id(),
		ItemId.GOBLIN_HIDE_BOOTS.id(),
		ItemId.GOBLIN_HIDE_CHAPS.id(),
		ItemId.GOBLIN_HIDE_CUIRASS.id()
	};
	private static final int[] wolfHideSetIds = {
		ItemId.WOLF_COIF.id(),
		ItemId.WOLF_GLOVES.id(),
		ItemId.WOLF_BOOTS.id(),
		ItemId.WOLF_CHAPS.id(),
		ItemId.WOLF_CUIRASS.id()
	};
	private static final int[] scorpionCarapaceSetIds = {
		ItemId.SCORPION_CARAPACE_COIF.id(),
		ItemId.SCORPION_CARAPACE_GLOVES.id(),
		ItemId.SCORPION_CARAPACE_BOOTS.id(),
		ItemId.SCORPION_CARAPACE_CHAPS.id(),
		ItemId.SCORPION_CARAPACE_CUIRASS.id()
	};
	private static final int[] spiderCarapaceSetIds = {
		ItemId.SPIDER_COIF.id(),
		ItemId.SPIDER_GLOVES.id(),
		ItemId.SPIDER_BOOTS.id(),
		ItemId.SPIDER_CHAPS.id(),
		ItemId.SPIDER_CUIRASS.id()
	};
	private static final int[] magicSpiderCarapaceSetIds = {
		ItemId.MAGIC_SPIDER_COIF.id(),
		ItemId.MAGIC_SPIDER_GLOVES.id(),
		ItemId.MAGIC_SPIDER_BOOTS.id(),
		ItemId.MAGIC_SPIDER_CHAPS.id(),
		ItemId.MAGIC_SPIDER_CUIRASS.id()
	};
	private static final int[] giantSetIds = {
		ItemId.GIANT_COIF.id(),
		ItemId.GIANT_GLOVES.id(),
		ItemId.GIANT_BOOTS.id(),
		ItemId.GIANT_CHAPS.id(),
		ItemId.GIANT_CUIRASS.id()
	};
	private static final int[] ogreSetIds = {
		ItemId.OGRE_COIF.id(),
		ItemId.OGRE_GLOVES.id(),
		ItemId.OGRE_BOOTS.id(),
		ItemId.OGRE_CHAPS.id(),
		ItemId.OGRE_CUIRASS.id()
	};
	private static final int[] babyDragonSetIds = {
		ItemId.BABY_DRAGON_COIF.id(),
		ItemId.BABY_DRAGON_GLOVES.id(),
		ItemId.BABY_DRAGON_BOOTS.id(),
		ItemId.BABY_DRAGON_CHAPS.id(),
		ItemId.BABY_DRAGON_CUIRASS.id()
	};
	private static final int[] demonSetIds = {
		ItemId.DEMON_COIF.id(),
		ItemId.DEMON_GLOVES.id(),
		ItemId.DEMON_BOOTS.id(),
		ItemId.DEMON_CHAPS.id(),
		ItemId.DEMON_CUIRASS.id()
	};
	private static final int[] mossGiantSetIds = {
		ItemId.MOSS_GIANT_COIF.id(),
		ItemId.MOSS_GIANT_GLOVES.id(),
		ItemId.MOSS_GIANT_BOOTS.id(),
		ItemId.MOSS_GIANT_CHAPS.id(),
		ItemId.MOSS_GIANT_CUIRASS.id()
	};
	private static final int[] iceGiantSetIds = {
		ItemId.ICE_GIANT_COIF.id(),
		ItemId.ICE_GIANT_GLOVES.id(),
		ItemId.ICE_GIANT_BOOTS.id(),
		ItemId.ICE_GIANT_CHAPS.id(),
		ItemId.ICE_GIANT_CUIRASS.id()
	};
	private static final int[] blackDemonSetIds = {
		ItemId.BLACK_DEMON_COIF.id(),
		ItemId.BLACK_DEMON_GLOVES.id(),
		ItemId.BLACK_DEMON_BOOTS.id(),
		ItemId.BLACK_DEMON_CHAPS.id(),
		ItemId.BLACK_DEMON_CUIRASS.id()
	};
	private static final int[] balrogSetIds = {
		ItemId.BALROG_COIF.id(),
		ItemId.BALROG_GLOVES.id(),
		ItemId.BALROG_BOOTS.id(),
		ItemId.BALROG_CHAPS.id(),
		ItemId.BALROG_CUIRASS.id()
	};
	private static final int[] blueDragonSetIds = {
		ItemId.BLUE_DRAGON_COIF.id(),
		ItemId.BLUE_DRAGON_GLOVES.id(),
		ItemId.BLUE_DRAGON_BOOTS.id(),
		ItemId.BLUE_DRAGON_CHAPS.id(),
		ItemId.BLUE_DRAGON_CUIRASS.id()
	};
	private static final int[] earthDragonSetIds = {
		ItemId.DRAGON_COIF.id(),
		ItemId.DRAGON_GLOVES.id(),
		ItemId.DRAGON_BOOTS.id(),
		ItemId.DRAGON_CHAPS.id(),
		ItemId.DRAGON_CUIRASS.id()
	};
	private static final int[] redDragonSetIds = {
		ItemId.RED_DRAGON_COIF.id(),
		ItemId.RED_DRAGON_GLOVES.id(),
		ItemId.RED_DRAGON_BOOTS.id(),
		ItemId.RED_DRAGON_CHAPS.id(),
		ItemId.RED_DRAGON_CUIRASS.id()
	};
	private static final int[] blackDragonSetIds = {
		ItemId.BLACK_DRAGON_COIF.id(),
		ItemId.BLACK_DRAGON_GLOVES.id(),
		ItemId.BLACK_DRAGON_BOOTS.id(),
		ItemId.BLACK_DRAGON_CHAPS.id(),
		ItemId.BLACK_DRAGON_CUIRASS.id()
	};
	private static final int[] kingBlackDragonSetIds = {
		ItemId.KING_BLACK_DRAGON_COIF.id(),
		ItemId.KING_BLACK_DRAGON_GLOVES.id(),
		ItemId.KING_BLACK_DRAGON_BOOTS.id(),
		ItemId.KING_BLACK_DRAGON_CHAPS.id(),
		ItemId.KING_BLACK_DRAGON_CUIRASS.id()
	};
	private static final int[] fireGiantSetIds = {
		ItemId.FIRE_GIANT_COIF.id(),
		ItemId.FIRE_GIANT_GLOVES.id(),
		ItemId.FIRE_GIANT_BOOTS.id(),
		ItemId.FIRE_GIANT_CHAPS.id(),
		ItemId.FIRE_GIANT_CUIRASS.id()
	};
	private static final int[] hellhoundSetIds = {
		ItemId.HELLHOUND_COIF.id(),
		ItemId.HELLHOUND_GLOVES.id(),
		ItemId.HELLHOUND_BOOTS.id(),
		ItemId.HELLHOUND_CHAPS.id(),
		ItemId.HELLHOUND_CUIRASS.id()
	};

	public Equipment(Player player) {
		synchronized (list) {
			this.player = player;
			for (int slotID = 0; slotID < SLOT_COUNT; slotID++)
				list[slotID] = null;
		}
	}

	/** Getters and Setters */

	public Item[] getList() {
		synchronized (list) {
			return this.list;
		}
	}

	public Item getAmmoItem() {
		synchronized (list) {
			return list[EquipmentSlot.SLOT_AMMO.getIndex()];
		}
	}

	public Item getNeckItem() {
		synchronized (list) {
			return list[EquipmentSlot.SLOT_NECK.getIndex()];
		}
	}

	public Item getRingItem() {
		synchronized (list) {
			return list[EquipmentSlot.SLOT_RING.getIndex()];
		}
	}

	public boolean hasEquippedGatheringToolInMainhand() {
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				return isGatheringToolInMainhand(list[EquipmentSlot.SLOT_MAINHAND.getIndex()]);
			}
		}
		for (Item item : player.getCarriedItems().getInventory().getItems()) {
			if (item != null && item.isWielded() && isGatheringToolInMainhand(item)) {
				return true;
			}
		}
		return false;
	}

	private boolean isGatheringToolInMainhand(Item item) {
		if (item == null || item.getDef(player.getWorld()) == null) {
			return false;
		}
		ItemDefinition def = item.getDef(player.getWorld());
		return def.getWieldPosition() == EquipmentSlot.SLOT_MAINHAND.getIndex()
			&& Formulae.isGatheringTool(item.getCatalogId());
	}

	/** Primary Method Definitions */

	// Equipment::add(Item)
	// Adds an item to the equipment container.
	public int add(Item item) {
		synchronized (list) {
			ItemDefinition itemDef = item.getDef(player.getWorld());
			if (itemDef == null || !itemDef.isWieldable())
				return -1;

			int slotID = itemDef.getWieldPosition();

			if (slotID < 0 || slotID >= Equipment.SLOT_COUNT)
				return -1;

			if (list[slotID] == null) {
				long itemID = player.getWorld().getServer().getDatabase().incrementMaxItemId(player);
				Item toEquip = item.copyWithItemId(itemID);
				list[slotID] = toEquip;
				return slotID;
			} else {
				if (itemDef.isStackable()
					&& list[slotID].getCatalogId() == item.getCatalogId()) {
					list[slotID].changeAmount(item.getAmount());
					return slotID;
				}
			}
		}
		return -1;
	}

	// Equipment::remove(Item, int)
	// Removes an item from the equipment container. Doesn't put it anywhere else, just removes.
	public int remove(Item item, int amount) {
		return remove(item, amount, true);
	}

	public int remove(Item item, int amount, boolean updateClient) {
		synchronized (list) {
			long itemId = item.getItemId();
			for (int slotID = 0; slotID < SLOT_COUNT; slotID++) {
				Item curEquip = list[slotID];
				if (curEquip == null || curEquip.getDef(player.getWorld()) == null)
					continue;
				ItemDefinition curEquipDef = curEquip.getDef(player.getWorld());

				if (curEquip.getItemId() == itemId) {
					int curAmount = curEquip.getAmount();
					if (!curEquipDef.isStackable() && amount > 1)
						return -1;

					if (curAmount > amount) {
						list[slotID].changeAmount(-amount);
					} else if (curAmount < amount) {
						return -1;
					} else {
						list[slotID] = null;
						int appearanceId = player.getSettings().getAppearance().getSprite(curEquipDef.getWieldPosition());
						int wieldPosition = curEquipDef.getWieldPosition();
						if (wieldPosition > 4) {
							appearanceId = 0;
						}
						player.updateWornItems(wieldPosition,
							appearanceId,
							curEquipDef.getWearableId(), false);
					}
					if (updateClient) {
						ActionSender.sendEquipmentStats(player);
					}
					return slotID;
				}
			}
			return -1;
		}
	}

	// Equipment::unequipItem(UnequipRequest)
	// Attempts to unequip an item.
	public boolean unequipItem(UnequipRequest request) {
		return unequipItem(request, true);
	}

	public boolean unequipItem(UnequipRequest request, boolean updateClient) {
		if (request.item == null || !request.item.isWieldable(player.getWorld())) {
			return false;
		}

		// Make sure they have the item equipped
		if (!hasEquipped(request.item.getCatalogId())) {
			player.setSuspiciousPlayer(true, "tried to unequip something they don't have equipped");
			return false;
		}

		// Check legitimacy of packet
		if ((request.requestType == UnequipRequest.RequestType.FROM_EQUIPMENT
			|| request.requestType == UnequipRequest.RequestType.FROM_BANK)
			&& !player.getConfig().WANT_EQUIPMENT_TAB) {
			player.setSuspiciousPlayer(true, "tried to unequip from a container they can't");
			return false;
		}

		switch (request.requestType) {
			case FROM_INVENTORY:
				request.item.setWielded(false);
				ItemDefinition curEquipDef = request.item.getDef(player.getWorld());
				// The item was no longer equipped once we removed it, so we pass isEquipped as false
				player.updateWornItems(
					curEquipDef.getWieldPosition(),
					player.getSettings().getAppearance().getSprite(curEquipDef.getWieldPosition()),
					curEquipDef.getWearableId(),
					false
				);

				if (player.getConfig().WANT_CUSTOM_SPRITES && player.getConfig().FORM_FITTING_CHAINMAIL) {
					for (int i = 0; i < chainTopIds.length; ++i) {
						if (request.item.getCatalogId() == chainTopIds[i]) {
							// If we can't remove the item, just worry about the swap
							if (player.getCarriedItems().remove(request.item) == -1) {
								break;
							}
							player.getCarriedItems().getInventory().add(new Item(chainBodyIds[i]));
							break;
						}
					}
				}

				break;
			case FROM_EQUIPMENT:
				synchronized (list) {
					synchronized (player) {
						// Can't unequip something if inventory is full
						if (player.getCarriedItems().getInventory().full()) {
							player.message("You need more inventory space to unequip that.");
							return false;
						}
						if (remove(request.item, request.item.getAmount()) == -1)
							return false;
						request.item.setWielded(false);

						Item itemToAdd = request.item;
						if (player.getConfig().WANT_CUSTOM_SPRITES && player.getConfig().FORM_FITTING_CHAINMAIL) {
							for (int i = 0; i < chainTopIds.length; ++i) {
								if (request.item.getCatalogId() == chainTopIds[i]) {
									itemToAdd = new Item(chainBodyIds[i]);
									break;
								}
							}
						}

						player.getCarriedItems().getInventory().add(itemToAdd, updateClient);

					}
				}
				break;
			case FROM_BANK:
				synchronized (list) {
					synchronized (player.getBank().getItems()) {
						// Can't unequip something if bank is full
						if (player.getBank().full()) {
							player.message("You need more bank space to unequip that.");
							return false;
						}
						if (remove(request.item, request.item.getAmount()) == -1)
							return false;
						request.item.setWielded(false);

						Item itemToAdd = request.item;
						if (player.getConfig().WANT_CUSTOM_SPRITES && player.getConfig().FORM_FITTING_CHAINMAIL) {
							for (int i = 0; i < chainTopIds.length; ++i) {
								if (request.item.getCatalogId() == chainTopIds[i]) {
									itemToAdd = new Item(chainBodyIds[i]);
									break;
								}
							}
						}

						player.getBank().add(itemToAdd, updateClient);
						if (updateClient) {
							ActionSender.showBank(player);
						}
					}
				}
				break;
			case CHECK_IF_EQUIPMENT_TAB:
				if (player.getConfig().WANT_EQUIPMENT_TAB) {
					request.requestType = UnequipRequest.RequestType.FROM_EQUIPMENT;
				} else {
					request.requestType = UnequipRequest.RequestType.FROM_INVENTORY;
				}
				return unequipItem(request);
		}

		// unequipped morphing ring
		AppearanceId appearance = AppearanceId.getById(request.item.getDef(player.getWorld()).getAppearanceId());
		if (appearance.getSuggestedWieldPosition() == AppearanceId.SLOT_MORPHING_RING) {
			player.exitMorph();
		}

		if (request.sound) {
			player.playSound("click");
		}

		if (updateClient) {
			ActionSender.sendEquipmentStats(player, request.item.getDef(player.getWorld()).getWieldPosition());
			player.getUpdateFlags().setAppearanceChanged(true);
			ActionSender.sendInventory(player);
		}
		Summoning.syncArmorSummon(player);
		return true;
	}

	// Equipment::equipItem(EquipRequest)
	// Attempts to equip an item.
	public boolean equipItem(EquipRequest request) {
		return equipItem(request, true);
	}
	public boolean equipItem(EquipRequest request, boolean updateClient) {
		// Make sure the item isn't a note
		if (request.item.getNoted())
			return false;

		// Turn chain tops into chain bodies and vice-versa
		if (player.getConfig().WANT_CUSTOM_SPRITES && player.getConfig().FORM_FITTING_CHAINMAIL) {
			Item newItem = null;
			if (player.isMale()) {
				for (int i = 0; i < chainTopIds.length; ++i) {
					if (chainTopIds[i] == request.item.getCatalogId()) {
						newItem = new Item(chainBodyIds[i]);
					}
				}
			} else {
				for (int i = 0; i < chainBodyIds.length; ++i) {
					if (chainBodyIds[i] == request.item.getCatalogId()) {
						newItem = new Item(chainTopIds[i]);
					}
				}
			}
			if (newItem != null) {
				if (request.requestType == EquipRequest.RequestType.FROM_BANK && player.getBank().remove(request.item.getCatalogId(), 1)) {
					player.getBank().add(newItem);
				} else if (request.requestType == EquipRequest.RequestType.FROM_INVENTORY && player.getCarriedItems().remove(request.item) != -1) {
					player.getCarriedItems().getInventory().add(newItem);
				} else {
					return false;
				}
				request.item = newItem;
			}
		}

		// Normalize plate tops into plate bodies and plated skirts into plate legs.
		if (player.getConfig().WANT_CUSTOM_SPRITES) {
			Item normalizedPlateItem = normalizePlateItem(request);
			if (normalizedPlateItem != null) {
				request.item = normalizedPlateItem;
			}
		}

		// Check that they are eligible to equip the item
		if (!ableToEquip(request.item))
			return false;

		debugJewelryEquip("request", request.item,
			"type=" + request.requestType
				+ " equipTab=" + player.getConfig().WANT_EQUIPMENT_TAB
				+ " currentRing=" + describeEquipmentSlot(EquipmentSlot.SLOT_RING.getIndex())
				+ " currentNeck=" + describeEquipmentSlot(EquipmentSlot.SLOT_NECK.getIndex())
				+ " wornRing=" + describeWieldedInventorySlot(EquipmentSlot.SLOT_RING.getIndex()));

		// Logic changes depending on where the item is being equipped from
		switch (request.requestType) {
			case FROM_INVENTORY:
				if (!equipItemFromInventory(request, updateClient))
					return false;
				break;
			case FROM_BANK:
				if (!equipItemFromBank(request, updateClient))
					return false;
				break;
			default:
				LOGGER.error("Unknown Equip request by " + request.player);
				return false;
		}

		if (request.sound)
			player.playSound("click");

		ItemDefinition itemDef = request.item.getDef(player.getWorld());
		if (morphAllowsUpdate(itemDef)) {
			player.updateWornItems(itemDef.getWieldPosition(), itemDef.getAppearanceId(), itemDef.getWearableId(), true);
		}

		if (updateClient) {
			ActionSender.sendEquipmentStats(player, request.item.getDef(player.getWorld()).getWieldPosition());
			player.getUpdateFlags().setAppearanceChanged(true);
		}
		Summoning.syncArmorSummon(player);
		return true;
	}

	private Item normalizePlateItem(EquipRequest request) {
		for (int i = 0; i < plateTopIds.length; ++i) {
			if (plateTopIds[i] == request.item.getCatalogId()) {
				return replaceRequestedItem(request, plateBodyIds[i]);
			}
		}
		for (int i = 0; i < platedSkirtIds.length; ++i) {
			if (platedSkirtIds[i] == request.item.getCatalogId()) {
				return replaceRequestedItem(request, plateLegIds[i]);
			}
		}
		return null;
	}

	private Item replaceRequestedItem(EquipRequest request, int replacementItemId) {
		Item replacement = new Item(replacementItemId);
		if (request.requestType == EquipRequest.RequestType.FROM_BANK && player.getBank().remove(request.item.getCatalogId(), 1)) {
			player.getBank().add(replacement);
			return replacement;
		}
		if (request.requestType == EquipRequest.RequestType.FROM_INVENTORY && player.getCarriedItems().remove(request.item) != -1) {
			player.getCarriedItems().getInventory().add(replacement);
			return replacement;
		}
		return null;
	}

	private boolean morphAllowsUpdate(ItemDefinition newlyWieldedItem) {
		if (newlyWieldedItem.getWieldPosition() == AppearanceId.SLOT_MORPHING_RING) {
			return true;
		} else {
			if (player.getCarriedItems().getEquipment() == null) {
				return true;
			}
			if (player.getCarriedItems().getEquipment().getRingItem() == null) {
				return true;
			}
			final ItemDefinition wornRingDef = player.getCarriedItems().getEquipment().getRingItem().getDef(player.getWorld());
			if (wornRingDef.getAppearanceId() == AppearanceId.NOTHING.id()) {
				return true;
			}
		}
		return false;
	}

	// Equipment::equipItemFromInventory(EquipRequest)
	// Attempts to equip the item from the inventory tab.
	private boolean equipItemFromInventory(EquipRequest request, boolean updateClient) {
		synchronized (player.getCarriedItems()) {
			if (player.getConfig().WANT_EQUIPMENT_TAB) { //on a world with equipment tab

				ItemDefinition itemDef = request.item.getDef(player.getWorld());
				if (itemDef == null)
					return false;

				ArrayList<Item> items = gatherConflictingItems(request);
				debugJewelryEquip("inventory_conflicts", request.item,
					"slot=" + itemDef.getWieldPosition()
						+ " wearable=" + itemDef.getWearableId()
						+ " invCount=" + player.getCarriedItems().getInventory().countId(request.item.getCatalogId(), Optional.of(request.item.getNoted()))
						+ " conflicts=" + describeItems(items));

				// We don't have enough space, even with the item we
				// will equip removed from the inventory.
				if (player.getCarriedItems().getInventory().getFreeSlots() + 1 < items.size()) {
					player.message("You need more inventory space to equip that.");
					return false;
				}

				// Grab the item we will be removing from the inventory
				Item toEquip = player.getCarriedItems().getInventory().get(
					player.getCarriedItems().getInventory().getLastIndexById(
						request.item.getCatalogId(), Optional.of(false)
					)
				);
				if (toEquip == null)
					return false;

				if (player.getWorld().getPlayer(DataConversions.usernameToHash(player.getUsername())) == null) {
					return false;
				}

				// Remove the items from their containers.
				for (Item item : items) {
					debugJewelryEquip("remove_conflict", request.item, describeItem(item));
					remove(item, item.getAmount(), updateClient); // Remove from equipment
				}
				player.getCarriedItems().getInventory().remove(toEquip, updateClient); // Remove from inventory
				debugJewelryEquip("inventory_removed", request.item,
					"removed=" + describeItem(toEquip)
						+ " invCountNow=" + player.getCarriedItems().getInventory().countId(toEquip.getCatalogId(), Optional.of(toEquip.getNoted())));

				for (Item item : items) {
					int id = item.getCatalogId();
					if (player.getConfig().WANT_CUSTOM_SPRITES && player.getConfig().FORM_FITTING_CHAINMAIL) {
						for (int i = 0; i < chainTopIds.length; ++i) {
							if (chainTopIds[i] == item.getCatalogId()) {
								id = chainBodyIds[i];
								break;
							}
						}
					}
					player.getCarriedItems().getInventory().add( // Add to inventory
						new Item(id, item.getAmount()), updateClient);
				}
				int addedSlot = add(new Item(toEquip.getCatalogId(), toEquip.getAmount()));
				debugJewelryEquip("equipment_add", request.item,
					"addedSlot=" + addedSlot
						+ " ringNow=" + describeEquipmentSlot(EquipmentSlot.SLOT_RING.getIndex())
						+ " neckNow=" + describeEquipmentSlot(EquipmentSlot.SLOT_NECK.getIndex()));
				if (addedSlot == -1) { // Add to equipment
					player.getCarriedItems().getInventory().add(toEquip, updateClient);
					return false;
				}

			} else { //On a world without equipment tab
				debugJewelryEquip("legacy_before", request.item,
					"wornRing=" + describeWieldedInventorySlot(EquipmentSlot.SLOT_RING.getIndex())
						+ " wornNeck=" + describeWieldedInventorySlot(EquipmentSlot.SLOT_NECK.getIndex()));
				unequipConflictingItems(request);
				request.item.setWielded(true);
				debugJewelryEquip("legacy_after", request.item,
					"wornRing=" + describeWieldedInventorySlot(EquipmentSlot.SLOT_RING.getIndex())
						+ " wornNeck=" + describeWieldedInventorySlot(EquipmentSlot.SLOT_NECK.getIndex()));
			}

		}
		// Update the inventory
		if (updateClient) {
			ActionSender.sendInventory(player);
		}
		return true;
	}

	// Equipment::equipItemFromBank(EquipRequest)
	// Attempts to equip the item from the bank screen.
	private boolean equipItemFromBank(EquipRequest request, boolean updateClient) {
		synchronized (list) {
			synchronized (player.getBank().getItems()) {
				if (!request.player.getConfig().WANT_EQUIPMENT_TAB) {
					request.player.setSuspiciousPlayer(true, "Tried to equip from bank on a world without equipment tab");
					return false;
				}

				ItemDefinition itemDef = request.item.getDef(player.getWorld());
				if (itemDef == null)
					return false;

				ArrayList<Item> itemsToUnequip = gatherConflictingItems(request);

				// We don't have enough space, even with the item we
				// will equip removed from the inventory.
				if (player.getFreeBankSlots() < itemsToUnequip.size()) {
					player.message("You need more bank space to equip that.");
					return false;
				}

				Item toEquip = player.getBank().get(
					player.getBank().getFirstIndexById(request.item.getCatalogId())
				);
				if (toEquip == null) {
					return false;
				}

				if (player.getWorld().getPlayer(DataConversions.usernameToHash(player.getUsername())) == null) {
					return false;
				}

				// Remove items from equipment (added to bank later)
				for (Item item : itemsToUnequip) {
					remove(item, item.getAmount(), updateClient);
				}

				if (!itemDef.isStackable()) {
					player.getBank().remove(toEquip.getCatalogId(), 1, updateClient);
					for (Item item : itemsToUnequip) {
						int id = item.getCatalogId();
						if (player.getConfig().WANT_CUSTOM_SPRITES && player.getConfig().FORM_FITTING_CHAINMAIL) {
							for (int i = 0; i < chainTopIds.length; ++i) {
								if (chainTopIds[i] == item.getCatalogId()) {
									id = chainBodyIds[i];
									break;
								}
							}
						}

						player.getBank().add(new Item(id, item.getAmount()), updateClient);
					}

					if (toEquip.getAmount() > 1) {
						if (add(new Item(toEquip.getCatalogId(), 1)) == -1) {
							player.getBank().add(new Item(toEquip.getCatalogId(), 1), updateClient);
							return false;
						}
					} else {
						if (add(request.item) == -1) {
							player.getBank().add(request.item, updateClient);
							return false;
						}
					}
				} else {
					int amountToRemoveAndEquip = Math.min(toEquip.getAmount(), request.item.getAmount());

					player.getBank().remove(toEquip.getCatalogId(), amountToRemoveAndEquip, updateClient);
					for (Item item : itemsToUnequip) {
						player.getBank().add(new Item(item.getCatalogId(), item.getAmount()), updateClient);
					}

					if (amountToRemoveAndEquip != request.item.getAmount()) {
						if (add(new Item(request.item.getCatalogId(), amountToRemoveAndEquip)) == -1) {
							player.getBank().add(new Item(request.item.getCatalogId(), amountToRemoveAndEquip), updateClient);
							return false;
						}
					} else {
						if (add(request.item) == -1) {
							player.getBank().add(request.item, updateClient);
							return false;
						}
					}
				}
			}
		}

		// Send client update
		if (updateClient) {
			ActionSender.showBank(player);
		}
		return true;
	}

	private ArrayList<Item> gatherConflictingItems(EquipRequest request) {
		// Gather conflicting equipment
		ArrayList<Item> items = new ArrayList<>();
		ItemDefinition requestedDef = request.item.getDef(player.getWorld());
		if (requestedDef == null) {
			return items;
		}
		for (int slotID = 0; slotID < Equipment.SLOT_COUNT; slotID++) {
			Item item = list[slotID];
			if (item == null || item.getDef(player.getWorld()) == null) {
				continue;
			}
			boolean conflictsBySlot = requestedDef.getWieldPosition() == item.getDef(player.getWorld()).getWieldPosition();
			boolean conflictsByWearableType = request.item.wieldingAffectsItem(player.getWorld(), item)
				&& !allowsHandFootArmorOverlap(request.item, item);
			boolean conflictsByBowOffhand = bowConflictsWithOffhand(request.item, item);
			if (conflictsBySlot || conflictsByWearableType || conflictsByBowOffhand) {
				if (request.item.getDef(player.getWorld()).isStackable()) {
					if (request.item.getCatalogId() == item.getCatalogId())
						continue;
				}
				items.add(item);
			}
		}
		return items;
	}

	// Equipment::unequipConflictingItems(EquipRequest)
	// Removes equipment that conflicts with an equipItem request.
	private boolean unequipConflictingItems(EquipRequest request) {
		synchronized (player.getCarriedItems().getInventory()) {
			ItemDefinition requestedDef = request.item.getDef(player.getWorld());
			for (Item item : player.getCarriedItems().getInventory().getItems()) {
				if (item == null || item.getDef(player.getWorld()) == null || !item.isWielded()) {
					continue;
				}
				boolean conflictsBySlot = requestedDef != null
					&& requestedDef.getWieldPosition() == item.getDef(player.getWorld()).getWieldPosition();
				boolean conflictsByWearableType = request.item.wieldingAffectsItem(player.getWorld(), item)
					&& !allowsHandFootArmorOverlap(request.item, item);
				boolean conflictsByBowOffhand = bowConflictsWithOffhand(request.item, item);
				if (conflictsBySlot || conflictsByWearableType || conflictsByBowOffhand) {
					debugJewelryEquip("legacy_remove_conflict", request.item,
						describeItem(item)
							+ " bySlot=" + conflictsBySlot
							+ " byWearable=" + conflictsByWearableType);
					if (!player.getCarriedItems().getEquipment().unequipItem(new UnequipRequest(player, item, UnequipRequest.RequestType.FROM_INVENTORY, false), false))
						return false;
				}
			}
		}
		return true;
	}

	private void debugJewelryEquip(final String stage, final Item item, final String detail) {
	}

	private boolean isJewelryDebugItem(final Item item) {
		if (item == null || item.getDef(player.getWorld()) == null) {
			return false;
		}
		final ItemDefinition def = item.getDef(player.getWorld());
		final int slot = def.getWieldPosition();
		final String name = def.getName().toLowerCase();
		return slot == EquipmentSlot.SLOT_RING.getIndex()
			|| slot == EquipmentSlot.SLOT_NECK.getIndex()
			|| name.contains("ring")
			|| name.contains("necklace")
			|| name.contains("amulet");
	}

	private String describeItems(final ArrayList<Item> items) {
		if (items == null || items.isEmpty()) {
			return "none";
		}
		StringBuilder builder = new StringBuilder();
		for (Item item : items) {
			if (builder.length() > 0) {
				builder.append(",");
			}
			builder.append(describeItem(item));
		}
		return builder.toString();
	}

	private String describeEquipmentSlot(final int slot) {
		if (slot < 0 || slot >= Equipment.SLOT_COUNT) {
			return "invalid";
		}
		Item item = list[slot];
		return item == null ? "empty" : describeItem(item);
	}

	private String describeWieldedInventorySlot(final int slot) {
		synchronized (player.getCarriedItems().getInventory().getItems()) {
			for (Item item : player.getCarriedItems().getInventory().getItems()) {
				if (item != null
					&& item.isWielded()
					&& item.getDef(player.getWorld()) != null
					&& item.getDef(player.getWorld()).getWieldPosition() == slot) {
					return describeItem(item);
				}
			}
		}
		return "empty";
	}

	private String describeItem(final Item item) {
		if (item == null) {
			return "null";
		}
		ItemDefinition def = item.getDef(player.getWorld());
		if (def == null) {
			return "id=" + item.getCatalogId() + " missingDef";
		}
		return "id=" + item.getCatalogId()
			+ " name=\"" + def.getName() + "\""
			+ " slot=" + def.getWieldPosition()
			+ " wearable=" + def.getWearableId()
			+ " amount=" + item.getAmount()
			+ " wielded=" + item.isWielded();
	}

	/** Equipment helper functions */

	// Equipment::searchEquipmentForItem(int)
	// Returns the equipment slot of specified catalogId.
	// Use only when you need the slotID.
	// Use only with custom Equipment inventory.
	public int searchEquipmentForItem(int id) {
		synchronized (list) {
			Item item;
			for (int slotID = 0; slotID < SLOT_COUNT; slotID++) {
				item = list[slotID];
				if (item != null && item.getCatalogId() == id)
					return slotID;
			}
			return -1;
		}
	}

	// Equipment::hasCatalogID(int)
	// Returns true if equipment list contains catalogID.
	// Use when you need an item, but not its slotID.
	// Use only with custom Equipment inventory.
	public boolean hasCatalogID(int catalogID) {
		return searchEquipmentForItem(catalogID) != -1;
	}

	// Equipment::get(int)
	// Returns the Item object held in a specified slotID.
	// Use only with custom Equipment inventory.
	public Item get(int slotID) {
		synchronized (list) {
			if (slotID < 0 || slotID >= SLOT_COUNT) {
				return null;
			}
			return list[slotID];
		}
	}

	// Equipment::hasEquipped(int)
	// Returns true if an item is equipped (marked wielded or in Equipment inventory).
	public boolean hasEquipped(int id) {
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			return searchEquipmentForItem(id) != -1;
		} else {
			for (Item i : player.getCarriedItems().getInventory().getItems()) {
				if (i.getCatalogId() == id && i.isWielded()) {
					return true;
				}
			}
		}
		return false;
	}

	// Equipment::ableToEquip(Item)
	// Returns true if the item may be equipped to the player.
	public boolean ableToEquip(Item item) {
		// Retro RSC mechanic - did not use to require level to wield
		boolean hasRequirement = !player.getConfig().NO_LEVEL_REQUIREMENT_WIELD;

		int requiredLevel = hasRequirement ? item.getDef(player.getWorld()).getRequiredLevel() : 1;
		int requiredSkillIndex = remapLegacyMeleeRequirementSkill(item.getDef(player.getWorld()).getRequiredSkillIndex());
		if (isArmorItem(item)) {
			requiredLevel = 1;
			requiredSkillIndex = -1;
		}
		String itemLower = item.getDef(player.getWorld()).getName().toLowerCase();
		Optional<Integer> optionalLevel = Optional.empty();
		Optional<Integer> optionalSkillIndex = Optional.empty();
		boolean ableToWield = true;
		boolean bypass = !player.getConfig().STRICT_CHECK_ALL &&
			(itemLower.startsWith("poisoned") &&
				((itemLower.endsWith("throwing dart") && !player.getConfig().STRICT_PDART_CHECK) ||
					(itemLower.endsWith("throwing knife") && !player.getConfig().STRICT_PKNIFE_CHECK) ||
					(itemLower.endsWith("spear") && !player.getConfig().STRICT_PSPEAR_CHECK))
			);

		// Spears are melee weapons.
		if (itemLower.endsWith("spear")) {
			optionalLevel = Optional.of(requiredLevel <= 10 ? requiredLevel : requiredLevel + 5);
			optionalSkillIndex = Optional.of(Skill.MELEE.id());
		}
		// Throwing knives are ranged-only weapons in MyWorld.
		if (itemLower.endsWith("throwing knife")) {
			optionalLevel = Optional.of(requiredLevel);
			optionalSkillIndex = Optional.of(Skill.RANGED.id());
		}
		// Staff of iban (usable)
		if (item.getCatalogId() == ItemId.STAFF_OF_IBAN.id()) {
			optionalLevel = Optional.of(requiredLevel);
			optionalSkillIndex = Optional.of(Skill.MELEE.id());
		}

		// Battlestaves (incl. enchanted version)
		if (itemLower.contains("battlestaff")) {
			optionalLevel = Optional.of(requiredLevel);
			optionalSkillIndex = Optional.of(Skill.MELEE.id());
		}
		if (isBlessedStaff(item.getCatalogId())) {
			optionalLevel = Optional.of(requiredLevel);
			optionalSkillIndex = Optional.of(Skill.MAGIC.id());
		}
		if (isGodStaff(item.getCatalogId())) {
			optionalLevel = Optional.of(requiredLevel);
			optionalSkillIndex = Optional.of(Skill.PRAYER.id());
		}

		if (optionalLevel.isPresent() && !hasRequirement) {
			optionalLevel = Optional.of(1);
		}

		// Check if the skill is a high enough level
		if (requiredSkillIndex >= 0 && player.getSkills().getMaxStat(requiredSkillIndex) < requiredLevel) {
			if (!bypass) {
				player.message("You are not a high enough level to use this item");
				player.message("You need to have a " + player.getWorld().getServer().getConstants().getSkills().getSkillName(requiredSkillIndex) + " level of " + requiredLevel);
				ableToWield = false;
			}
		}
		if (optionalSkillIndex.isPresent() && player.getSkills().getMaxStat(optionalSkillIndex.get()) < optionalLevel.get()) {
			if (!bypass) {
				player.message("You are not a high enough level to use this item");
				player.message("You need to have a " + player.getWorld().getServer().getConstants().getSkills().getSkillName(optionalSkillIndex.get()) + " level of " + optionalLevel.get());
				ableToWield = false;
			}
		}

		// Incorrect sex for armour type
		if (item.getDef(player.getWorld()).isFemaleOnly() && player.isMale()) {
			player.message("It doesn't fit!");
			player.message("Perhaps I should get someone to adjust it for me");
			ableToWield = false;
		}

		// Rune plate mail body and top
		if (!player.getConfig().EQUIP_QUEST_ITEMS_WITHOUT_QUESTS && (item.getCatalogId() == ItemId.RUNE_PLATE_MAIL_BODY.id() || item.getCatalogId() == ItemId.RUNE_PLATE_MAIL_TOP.id())
			&& (player.getQuestStage(Quests.DRAGON_SLAYER) != -1)) {
			player.message("you have not earned the right to wear this yet");
			player.message("you need to complete the dragon slayer quest");
			return false;
		}

		// Dragon sword
		else if (!player.getConfig().EQUIP_QUEST_ITEMS_WITHOUT_QUESTS && item.getCatalogId() == ItemId.DRAGON_SWORD.id() && player.getQuestStage(Quests.LOST_CITY) != -1) {
			player.message("you have not earned the right to wear this yet");
			player.message("you need to complete the Lost city of zanaris quest");
			return false;
		}

		// Dragon battle axe
		else if (!player.getConfig().EQUIP_QUEST_ITEMS_WITHOUT_QUESTS && item.getCatalogId() == ItemId.DRAGON_AXE.id() && player.getQuestStage(Quests.HEROS_QUEST) != -1) {
			player.message("you have not earned the right to wear this yet");
			player.message("you need to complete the Hero's guild entry quest");
			return false;
		}

		// Dragon square shield
		else if (!player.getConfig().EQUIP_QUEST_ITEMS_WITHOUT_QUESTS && item.getCatalogId() == ItemId.DRAGON_SQUARE_SHIELD.id() && player.getQuestStage(Quests.LEGENDS_QUEST) != -1) {
			player.message("you have not earned the right to wear this yet");
			player.message("you need to complete the legend's guild quest");
			return false;
		}

		// Note: It is authentic to NOT have a check for Cape of Legends!
		// They only relied on its untradability as the check. Proof of this is in the
		// behaviour seen in 2004, when transferring the cape from RS2 back to RS1.

		// God capes and staves.
		else if (!matchesCurrentPrayerBook(item.getCatalogId())) {
			player.message("you must worship " + formatGodLine(getRequiredGodLine(item.getCatalogId())) + " to equip this");
			return false;
		} else if (item.getCatalogId() == ItemId.STAFF_OF_GUTHIX.id() && (hasEquipped(ItemId.ZAMORAK_CAPE.id()) || hasEquipped(ItemId.SARADOMIN_CAPE.id()))) { // try to wear guthix staff
			player.message("you may not wield this staff while wearing a cape of another god");
			return false;
		} else if (item.getCatalogId() == ItemId.STAFF_OF_SARADOMIN.id() && (hasEquipped(ItemId.ZAMORAK_CAPE.id()) || hasEquipped(ItemId.GUTHIX_CAPE.id()))) { // try to wear sara staff
			player.message("you may not wield this staff while wearing a cape of another god");
			return false;
		} else if (item.getCatalogId() == ItemId.STAFF_OF_ZAMORAK.id() && (hasEquipped(ItemId.SARADOMIN_CAPE.id()) || hasEquipped(ItemId.GUTHIX_CAPE.id()))) { // try to wear zamorak staff
			player.message("you may not wield this staff while wearing a cape of another god");
			return false;
		} else if (item.getCatalogId() == ItemId.GUTHIX_CAPE.id() && (hasEquipped(ItemId.STAFF_OF_ZAMORAK.id()) || hasEquipped(ItemId.STAFF_OF_SARADOMIN.id()))) { // try to wear guthix cape
			player.message("you may not wear this cape while wielding staffs of the other gods");
			return false;
		} else if (item.getCatalogId() == ItemId.SARADOMIN_CAPE.id() && (hasEquipped(ItemId.STAFF_OF_ZAMORAK.id()) || hasEquipped(ItemId.STAFF_OF_GUTHIX.id()))) { // try to wear sara cape
			player.message("you may not wear this cape while wielding staffs of the other gods");
			return false;
		} else if (item.getCatalogId() == ItemId.ZAMORAK_CAPE.id() && (hasEquipped(ItemId.STAFF_OF_GUTHIX.id()) || hasEquipped(ItemId.STAFF_OF_SARADOMIN.id()))) { // try to wear zamorak cape
			player.message("you may not wear this cape while wielding staffs of the other gods");
			return false;
		}

		// Quest cape 112QP. TODO item id
		/*
		else if (item.getID() == 2145 && player.getQuestPoints() < 112) {
			player.message("you have not earned the right to wear this yet");
			player.message("you need to complete all the available quests");
			return;
		}
		*/

		// Max skill total cape. TODO item id
		/*
		else if (item.getID() == 2146 && player.getSkills().getTotalLevel() < 1782) {
			player.message("you have not earned the right to wear this yet");
			player.message("you need to be level 99 in all skills");
			return;
		}
		*/

		// Ironman armour.
		else if ((item.getCatalogId() == ItemId.IRONMAN_HELM.id() || item.getCatalogId() == ItemId.IRONMAN_PLATEBODY.id() || item.getCatalogId() == ItemId.IRONMAN_PLATE_TOP.id()
			|| item.getCatalogId() == ItemId.IRONMAN_PLATELEGS.id() || item.getCatalogId() == ItemId.IRONMAN_PLATED_SKIRT.id()) && !player.isIronMan(IronmanMode.Ironman.id())) {
			player.message("You need to be an Ironman to wear this");
			return false;
		} else if ((item.getCatalogId() == ItemId.ULTIMATE_IRONMAN_HELM.id() || item.getCatalogId() == ItemId.ULTIMATE_IRONMAN_PLATEBODY.id() || item.getCatalogId() == ItemId.ULTIMATE_IRONMAN_PLATE_TOP.id()
			|| item.getCatalogId() == ItemId.ULTIMATE_IRONMAN_PLATELEGS.id() || item.getCatalogId() == ItemId.ULTIMATE_IRONMAN_PLATED_SKIRT.id()) && !player.isIronMan(IronmanMode.Ultimate.id())) {
			player.message("You need to be an Ultimate Ironman to wear this");
			return false;
		} else if ((item.getCatalogId() == ItemId.HARDCORE_IRONMAN_HELM.id() || item.getCatalogId() == ItemId.HARDCORE_IRONMAN_PLATEBODY.id() || item.getCatalogId() == ItemId.HARDCORE_IRONMAN_PLATE_TOP.id()
			|| item.getCatalogId() == ItemId.HARDCORE_IRONMAN_PLATELEGS.id() || item.getCatalogId() == ItemId.HARDCORE_IRONMAN_PLATED_SKIRT.id()) && !player.isIronMan(IronmanMode.Hardcore.id())) {
			player.message("You need to be a Hardcore Ironman to wear this");
			return false;
		} else if (item.getCatalogId() == 2254 && player.getQuestStage(Quests.LEGENDS_QUEST) != -1) {
			player.message("you have not earned the right to wear this yet");
			player.message("you need to complete the Legends Quest");
			return false;
		}

		return ableToWield;
	}

	private boolean isBlessedStaff(int itemId) {
		return isZamorakBlessedStaff(itemId)
			|| isSaradominBlessedStaff(itemId)
			|| isGuthixBlessedStaff(itemId);
	}

	private boolean isGodStaff(int itemId) {
		return itemId == ItemId.STAFF_OF_ZAMORAK.id()
			|| itemId == ItemId.STAFF_OF_GUTHIX.id()
			|| itemId == ItemId.STAFF_OF_SARADOMIN.id();
	}

	private int remapLegacyMeleeRequirementSkill(int skillIndex) {
		if (skillIndex == Skill.ATTACK.id() || skillIndex == Skill.DEFENSE.id() || skillIndex == Skill.STRENGTH.id()) {
			return Skill.MELEE.id();
		}
		return skillIndex;
	}

	public void unequipItemsThatDoNotMatchPrayerBook(final PrayerCatalog.GodLine prayerBook) {
		if (!player.getConfig().WANT_EQUIPMENT_TAB) {
			return;
		}
		final PrayerCatalog.GodLine safePrayerBook = prayerBook == null ? PrayerCatalog.getDefaultGodLine() : prayerBook;
		final ArrayList<Item> toUnequip = new ArrayList<Item>();
		synchronized (list) {
			for (Item item : list) {
				if (item == null || item.getDef(player.getWorld()) == null) {
					continue;
				}
				if (!matchesPrayerBook(item.getCatalogId(), safePrayerBook)) {
					toUnequip.add(item);
				}
			}
		}
		for (Item item : toUnequip) {
			boolean unequipped = unequipItem(new UnequipRequest(player, item, UnequipRequest.RequestType.FROM_EQUIPMENT, false), true);
			if (!unequipped) {
				unequipped = unequipItem(new UnequipRequest(player, item, UnequipRequest.RequestType.FROM_BANK, false), true);
			}
			if (!unequipped) {
				unequipped = unequipToGround(item);
			}
			if (unequipped) {
				player.message(item.getDef(player.getWorld()).getName() + " is removed because you now worship " + formatGodLine(safePrayerBook));
			}
		}
	}

	private boolean unequipToGround(final Item item) {
		if (item == null || item.getDef(player.getWorld()) == null) {
			return false;
		}
		final int catalogId = item.getCatalogId();
		final int amount = item.getAmount();
		final boolean noted = item.getNoted();
		if (remove(item, amount, true) == -1) {
			return false;
		}
		item.setWielded(false);
		player.getWorld().registerItem(
			new GroundItem(player.getWorld(), catalogId, player.getX(), player.getY(), amount, player, noted),
			player.getConfig().GAME_TICK * 300
		);
		player.getUpdateFlags().setAppearanceChanged(true);
		ActionSender.sendInventory(player);
		return true;
	}

	private boolean matchesCurrentPrayerBook(final int itemId) {
		final PrayerCatalog.GodLine prayerBook = player.getPrayerBook() == null ? PrayerCatalog.getDefaultGodLine() : player.getPrayerBook();
		return matchesPrayerBook(itemId, prayerBook);
	}

	private boolean matchesPrayerBook(final int itemId, final PrayerCatalog.GodLine prayerBook) {
		final PrayerCatalog.GodLine requiredGodLine = getRequiredGodLine(itemId);
		return requiredGodLine == null || requiredGodLine == prayerBook;
	}

	private PrayerCatalog.GodLine getRequiredGodLine(final int itemId) {
		if (itemId == ItemId.SARADOMIN_CAPE.id() || itemId == ItemId.STAFF_OF_SARADOMIN.id()) {
			return PrayerCatalog.GodLine.SARADOMIN;
		}
		if (itemId == ItemId.ZAMORAK_CAPE.id() || itemId == ItemId.STAFF_OF_ZAMORAK.id()) {
			return PrayerCatalog.GodLine.ZAMORAK;
		}
		if (itemId == ItemId.GUTHIX_CAPE.id() || itemId == ItemId.STAFF_OF_GUTHIX.id()) {
			return PrayerCatalog.GodLine.GUTHIX;
		}
		if (isZamorakBlessedStaff(itemId)) {
			return PrayerCatalog.GodLine.ZAMORAK;
		}
		if (isSaradominBlessedStaff(itemId)) {
			return PrayerCatalog.GodLine.SARADOMIN;
		}
		if (isGuthixBlessedStaff(itemId)) {
			return PrayerCatalog.GodLine.GUTHIX;
		}
		if (isZamorakBlessedWool(itemId)) {
			return PrayerCatalog.GodLine.ZAMORAK;
		}
		if (isSaradominBlessedWool(itemId)) {
			return PrayerCatalog.GodLine.SARADOMIN;
		}
		if (isGuthixBlessedWool(itemId)) {
			return PrayerCatalog.GodLine.GUTHIX;
		}
		if (isWhiteKnightEquipment(itemId)) {
			return PrayerCatalog.GodLine.SARADOMIN;
		}
		if (isBlackKnightEquipment(itemId)) {
			return PrayerCatalog.GodLine.ZAMORAK;
		}
		if (isGreyKnightEquipment(itemId)) {
			return PrayerCatalog.GodLine.GUTHIX;
		}
		return null;
	}

	private boolean isWhiteKnightEquipment(final int itemId) {
		switch (itemId) {
			case 2151: // WHITE_DAGGER
			case 2152: // WHITE_SHORT_SWORD
			case 2153: // WHITE_LONG_SWORD
			case 2154: // WHITE_2_HANDED_SWORD
			case 2155: // WHITE_SCIMITAR
			case 2156: // WHITE_BATTLE_AXE
			case 2157: // WHITE_MACE
			case 2158: // LARGE_WHITE_HELMET
			case 2159: // MEDIUM_WHITE_HELMET
			case 2160: // WHITE_CHAIN_MAIL_BODY
			case 2161: // WHITE_SQUARE_SHIELD
			case 2162: // WHITE_KITE_SHIELD
			case 2163: // WHITE_PLATE_MAIL_BODY
			case 2164: // WHITE_PLATE_MAIL_LEGS
			case 2165: // WHITE_PLATE_MAIL_TOP
			case 2166: // WHITE_PLATED_SKIRT
			case 2167: // WHITE_CHAIN_MAIL_LEGS
			case 2168: // WHITE_CHAIN_MAIL_TOP
			case 3133: // WHITE_GAUNTLETS
			case 3134: // WHITE_GREAVES
				return true;
			default:
				return false;
		}
	}

	private boolean isBlackKnightEquipment(final int itemId) {
		switch (itemId) {
			case 423: // BLACK_DAGGER
			case 424: // BLACK_SHORT_SWORD
			case 425: // BLACK_LONG_SWORD
			case 426: // BLACK_2_HANDED_SWORD
			case 427: // BLACK_SCIMITAR
			case 429: // BLACK_BATTLE_AXE
			case 430: // BLACK_MACE
			case 230: // LARGE_BLACK_HELMET
			case 470: // MEDIUM_BLACK_HELMET
			case 431: // BLACK_CHAIN_MAIL_BODY
			case 432: // BLACK_SQUARE_SHIELD
			case 433: // BLACK_KITE_SHIELD
			case 196: // BLACK_PLATE_MAIL_BODY
			case 248: // BLACK_PLATE_MAIL_LEGS
			case 313: // BLACK_PLATE_MAIL_TOP
			case 434: // BLACK_PLATED_SKIRT
			case 1424: // BLACK_CHAIN_MAIL_LEGS
			case 1533: // BLACK_CHAIN_MAIL_TOP
			case 3131: // BLACK_GAUNTLETS
			case 3132: // BLACK_GREAVES
				return true;
			default:
				return false;
		}
	}

	private boolean isGreyKnightEquipment(final int itemId) {
		switch (itemId) {
			case 3113: // GREY_DAGGER
			case 3114: // GREY_SHORT_SWORD
			case 3115: // GREY_LONG_SWORD
			case 3116: // GREY_2_HANDED_SWORD
			case 3117: // GREY_SCIMITAR
			case 3118: // GREY_BATTLE_AXE
			case 3119: // GREY_MACE
			case 3120: // LARGE_GREY_HELMET
			case 3121: // MEDIUM_GREY_HELMET
			case 3122: // GREY_CHAIN_MAIL_BODY
			case 3123: // GREY_SQUARE_SHIELD
			case 3124: // GREY_KITE_SHIELD
			case 3125: // GREY_PLATE_MAIL_BODY
			case 3126: // GREY_PLATE_MAIL_LEGS
			case 3127: // GREY_PLATE_MAIL_TOP
			case 3128: // GREY_PLATED_SKIRT
			case 3129: // GREY_CHAIN_MAIL_LEGS
			case 3130: // GREY_CHAIN_MAIL_TOP
			case 3135: // GREY_GAUNTLETS
			case 3136: // GREY_GREAVES
				return true;
			default:
				return false;
		}
	}

	private boolean isZamorakBlessedWool(final int itemId) {
		return itemId >= ItemId.ZAMORAK_WOOL_HAT.id() && itemId <= ItemId.ZAMORAK_WOOL_BOOTS.id();
	}

	private boolean isZamorakBlessedStaff(final int itemId) {
		return itemId >= ItemId.BLESSED_STAFF.id() && itemId <= ItemId.BLESSED_BLOOD_STAFF.id();
	}

	private boolean isSaradominBlessedStaff(final int itemId) {
		return itemId >= ItemId.SARADOMIN_BLESSED_STAFF.id() && itemId <= ItemId.SARADOMIN_BLESSED_BLOOD_STAFF.id();
	}

	private boolean isGuthixBlessedStaff(final int itemId) {
		return itemId >= ItemId.GUTHIX_BLESSED_STAFF.id() && itemId <= ItemId.GUTHIX_BLESSED_BLOOD_STAFF.id();
	}

	private boolean isSaradominBlessedWool(final int itemId) {
		return itemId >= ItemId.SARADOMIN_WOOL_HAT.id() && itemId <= ItemId.SARADOMIN_WOOL_BOOTS.id();
	}

	private boolean isGuthixBlessedWool(final int itemId) {
		return itemId >= ItemId.GUTHIX_WOOL_HAT.id() && itemId <= ItemId.GUTHIX_WOOL_BOOTS.id();
	}

	private boolean isBlessedWoolArmor(final int itemId) {
		return isZamorakBlessedWool(itemId)
			|| isSaradominBlessedWool(itemId)
			|| isGuthixBlessedWool(itemId);
	}

	private String formatGodLine(final PrayerCatalog.GodLine godLine) {
		if (godLine == null) {
			return "your current god";
		}
		final String lower = godLine.name().toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	/** Methods that report equipment statistics. */

	// Equipment::getWeaponAim()
	// Returns the total weapon aim from all equipment (+1 base).
	public int getWeaponAim() {
		int total = 1;
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list)
					total += getScaledWeaponAimBonus(item);
			}
		} else {
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					if (item.isWielded()) {
						total += item.getDef(player.getWorld()).getWeaponAimBonus();
						total += getDevotionScaledCombatBonus(item, item.getDef(player.getWorld()).getWeaponAimBonus(), getGodEquipmentTargetWeaponAim(item.getCatalogId()));
					}
				}
			}
		}
		return total;
	}

	// Equipment::getWeaponPower()
	// Returns the total weapon power from all equipment (+1 base).
	public int getWeaponPower() {
		int total = 1;
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list)
					total += getScaledWeaponPowerBonus(item);
			}
		} else {
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					if (item.isWielded()) {
						total += item.getDef(player.getWorld()).getWeaponPowerBonus();
						total += getDevotionScaledCombatBonus(item, item.getDef(player.getWorld()).getWeaponPowerBonus(), getGodEquipmentTargetWeaponPower(item.getCatalogId()));
					}
				}
			}
		}
		return total;
	}

	// Equipment::getArmour()
	// Returns the total armour value from all equipment (+1 base).
	public int getArmour() {
		int total = 1;
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list)
					total += getScaledArmourBonus(item);
			}
		} else {
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					if (item.isWielded()) {
						final int armourBonus = (int) item.getDef(player.getWorld()).getArmourBonus();
						total += armourBonus;
						total += getDevotionScaledCombatBonus(item, armourBonus, getGodEquipmentTargetArmour(item.getCatalogId()));
					}
				}
			}
		}
		return total;
	}

	// Equipment::getMagic()
	// Returns the total magic power from all equipment (+1 base).
	public int getMagic() {
		int total = 1;
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list)
					total += getScaledMagicBonus(item);
			}
		} else {
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					if (item.isWielded()) {
						total += item.getDef(player.getWorld()).getMagicBonus();
						total += getDevotionScaledCombatBonus(item, item.getDef(player.getWorld()).getMagicBonus(), getGodEquipmentTargetMagic(item.getCatalogId()));
					}
				}
			}
		}
		return total;
	}

	// Equipment::getPrayer()
	// Returns the total prayer bonus from all equipment (+1 base).
	public int getPrayer() {
		int total = 1;
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list)
					total += getActivePrayerBonus(item);
			}
		} else {
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					if (item.isWielded()) {
						total += getActivePrayerBonus(item);
					}
				}
			}
		}
		total += getUnicornHidePrayerBonus();
		total += getBlackUnicornHidePrayerBonus();
		return total;
	}

	public int getCowHideHitsBonus() {
		return hasFullCowHideSet() ? 5 : 0;
	}

	public int getUnicornHidePrayerBonus() {
		return hasFullUnicornHideSet() && player.getPrayerBook() == PrayerCatalog.GodLine.SARADOMIN ? 10 : 0;
	}

	public int getBlackUnicornHidePrayerBonus() {
		return hasFullBlackUnicornHideSet() && player.getPrayerBook() == PrayerCatalog.GodLine.ZAMORAK ? 10 : 0;
	}

	private int getActivePrayerBonus(final Item item) {
		if (item == null || item.getDef(player.getWorld()) == null) {
			return 0;
		}
		if (!matchesCurrentPrayerBook(item.getCatalogId())) {
			return 0;
		}
		final int godEquipmentPrayerBonus = getGodEquipmentPrayerBonus(item.getCatalogId());
		if (godEquipmentPrayerBonus >= 0) {
			return godEquipmentPrayerBonus;
		}
		return item.getDef(player.getWorld()).getPrayerBonus();
	}

	private int getScaledWeaponAimBonus(final Item item) {
		if (item == null || item.getDef(player.getWorld()) == null) {
			return 0;
		}
		final int base = item.getDef(player.getWorld()).getWeaponAimBonus();
		return base + getDevotionScaledCombatBonus(item, base, getGodEquipmentTargetWeaponAim(item.getCatalogId()));
	}

	private int getScaledWeaponPowerBonus(final Item item) {
		if (item == null || item.getDef(player.getWorld()) == null) {
			return 0;
		}
		final int base = item.getDef(player.getWorld()).getWeaponPowerBonus();
		return base + getDevotionScaledCombatBonus(item, base, getGodEquipmentTargetWeaponPower(item.getCatalogId()));
	}

	private int getScaledArmourBonus(final Item item) {
		if (item == null || item.getDef(player.getWorld()) == null) {
			return 0;
		}
		final int base = (int) item.getDef(player.getWorld()).getArmourBonus();
		return base + getDevotionScaledCombatBonus(item, base, getGodEquipmentTargetArmour(item.getCatalogId()));
	}

	private int getScaledMagicBonus(final Item item) {
		if (item == null || item.getDef(player.getWorld()) == null) {
			return 0;
		}
		final int base = item.getDef(player.getWorld()).getMagicBonus();
		return base + getDevotionScaledCombatBonus(item, base, getGodEquipmentTargetMagic(item.getCatalogId()));
	}

	private int getDevotionScaledCombatBonus(final Item item, final int baseValue, final int targetValue) {
		if (item == null || targetValue <= baseValue || !matchesCurrentPrayerBook(item.getCatalogId())) {
			return 0;
		}
		final PrayerCatalog.GodLine godLine = getRequiredGodLine(item.getCatalogId());
		return Devotion.getDevotionGrowthBonus(player, godLine, targetValue - baseValue);
	}

	private int getGodEquipmentPrayerBonus(final int itemId) {
		final int resourceCost = getGodEquipmentResourceCost(itemId);
		if (resourceCost <= 0) {
			return -1;
		}
		final PrayerCatalog.GodLine godLine = getRequiredGodLine(itemId);
		return getGodEquipmentNaturalPrayerBonus(itemId)
			+ resourceCost
			+ Devotion.getPrayerBonusGrowth(player, godLine);
	}

	private int getGodEquipmentNaturalPrayerBonus(final int itemId) {
		switch (itemId) {
			case 430: // BLACK_MACE
			case 2157: // WHITE_MACE
			case 3119: // GREY_MACE
			case 433: // BLACK_KITE_SHIELD
			case 2162: // WHITE_KITE_SHIELD
			case 3124: // GREY_KITE_SHIELD
				return 5;
			default:
				return 0;
		}
	}

	private int getGodEquipmentResourceCost(final int itemId) {
		switch (itemId) {
			case 423: // BLACK_DAGGER
			case 424: // BLACK_SHORT_SWORD
			case 430: // BLACK_MACE
			case 2151: // WHITE_DAGGER
			case 2152: // WHITE_SHORT_SWORD
			case 2157: // WHITE_MACE
			case 3113: // GREY_DAGGER
			case 3114: // GREY_SHORT_SWORD
			case 3119: // GREY_MACE
			case 3137: // ZAMORAK_WOOL_HAT
			case 3142: // SARADOMIN_WOOL_HAT
			case 3147: // GUTHIX_WOOL_HAT
				return 1;
			case 425: // BLACK_LONG_SWORD
			case 427: // BLACK_SCIMITAR
			case 230: // LARGE_BLACK_HELMET
			case 3131: // BLACK_GAUNTLETS
			case 3132: // BLACK_GREAVES
			case 2153: // WHITE_LONG_SWORD
			case 2155: // WHITE_SCIMITAR
			case 2158: // LARGE_WHITE_HELMET
			case 3133: // WHITE_GAUNTLETS
			case 3134: // WHITE_GREAVES
			case 3115: // GREY_LONG_SWORD
			case 3117: // GREY_SCIMITAR
			case 3120: // LARGE_GREY_HELMET
			case 3135: // GREY_GAUNTLETS
			case 3136: // GREY_GREAVES
			case 3140: // ZAMORAK_WOOL_GLOVES
			case 3141: // ZAMORAK_WOOL_BOOTS
			case 3145: // SARADOMIN_WOOL_GLOVES
			case 3146: // SARADOMIN_WOOL_BOOTS
			case 3150: // GUTHIX_WOOL_GLOVES
			case 3151: // GUTHIX_WOOL_BOOTS
				return 2;
			case 426: // BLACK_2_HANDED_SWORD
			case 429: // BLACK_BATTLE_AXE
			case 433: // BLACK_KITE_SHIELD
			case 248: // BLACK_PLATE_MAIL_LEGS
			case 2154: // WHITE_2_HANDED_SWORD
			case 2156: // WHITE_BATTLE_AXE
			case 2162: // WHITE_KITE_SHIELD
			case 2164: // WHITE_PLATE_MAIL_LEGS
			case 3116: // GREY_2_HANDED_SWORD
			case 3118: // GREY_BATTLE_AXE
			case 3124: // GREY_KITE_SHIELD
			case 3126: // GREY_PLATE_MAIL_LEGS
			case 3139: // ZAMORAK_WOOL_ROBE_BOTTOM
			case 3144: // SARADOMIN_WOOL_ROBE_BOTTOM
			case 3149: // GUTHIX_WOOL_ROBE_BOTTOM
				return 3;
			case 196: // BLACK_PLATE_MAIL_BODY
			case 2163: // WHITE_PLATE_MAIL_BODY
			case 3125: // GREY_PLATE_MAIL_BODY
			case 3138: // ZAMORAK_WOOL_ROBE_TOP
			case 3143: // SARADOMIN_WOOL_ROBE_TOP
			case 3148: // GUTHIX_WOOL_ROBE_TOP
				return 4;
			default:
				return 0;
		}
	}

	private int getGodEquipmentTargetWeaponAim(final int itemId) {
		switch (itemId) {
			case 423: // BLACK_DAGGER
			case 2151: // WHITE_DAGGER
			case 3113: // GREY_DAGGER
				return 25;
			case 424: // BLACK_SHORT_SWORD
			case 2152: // WHITE_SHORT_SWORD
			case 3114: // GREY_SHORT_SWORD
				return 40;
			case 425: // BLACK_LONG_SWORD
			case 2153: // WHITE_LONG_SWORD
			case 3115: // GREY_LONG_SWORD
				return 49;
			case 426: // BLACK_2_HANDED_SWORD
			case 2154: // WHITE_2_HANDED_SWORD
			case 3116: // GREY_2_HANDED_SWORD
				return 70;
			case 427: // BLACK_SCIMITAR
			case 2155: // WHITE_SCIMITAR
			case 3117: // GREY_SCIMITAR
				return 44;
			case 429: // BLACK_BATTLE_AXE
			case 2156: // WHITE_BATTLE_AXE
			case 3118: // GREY_BATTLE_AXE
				return 47;
			case 430: // BLACK_MACE
			case 2157: // WHITE_MACE
			case 3119: // GREY_MACE
				return 38;
			default:
				return 0;
		}
	}

	private int getGodEquipmentTargetWeaponPower(final int itemId) {
		switch (itemId) {
			case 423: // BLACK_DAGGER
			case 2151: // WHITE_DAGGER
			case 3113: // GREY_DAGGER
				return 25;
			case 424: // BLACK_SHORT_SWORD
			case 2152: // WHITE_SHORT_SWORD
			case 3114: // GREY_SHORT_SWORD
				return 40;
			case 425: // BLACK_LONG_SWORD
			case 2153: // WHITE_LONG_SWORD
			case 3115: // GREY_LONG_SWORD
				return 49;
			case 426: // BLACK_2_HANDED_SWORD
			case 2154: // WHITE_2_HANDED_SWORD
			case 3116: // GREY_2_HANDED_SWORD
				return 70;
			case 427: // BLACK_SCIMITAR
			case 2155: // WHITE_SCIMITAR
			case 3117: // GREY_SCIMITAR
				return 44;
			case 429: // BLACK_BATTLE_AXE
			case 2156: // WHITE_BATTLE_AXE
			case 3118: // GREY_BATTLE_AXE
				return 64;
			case 430: // BLACK_MACE
			case 2157: // WHITE_MACE
			case 3119: // GREY_MACE
				return 28;
			default:
				return 0;
		}
	}

	private int getGodEquipmentTargetArmour(final int itemId) {
		switch (itemId) {
			case 230: // LARGE_BLACK_HELMET
			case 2158: // LARGE_WHITE_HELMET
			case 3120: // LARGE_GREY_HELMET
				return 30;
			case 196: // BLACK_PLATE_MAIL_BODY
			case 2163: // WHITE_PLATE_MAIL_BODY
			case 3125: // GREY_PLATE_MAIL_BODY
				return 80;
			case 248: // BLACK_PLATE_MAIL_LEGS
			case 2164: // WHITE_PLATE_MAIL_LEGS
			case 3126: // GREY_PLATE_MAIL_LEGS
				return 49;
			case 433: // BLACK_KITE_SHIELD
			case 2162: // WHITE_KITE_SHIELD
			case 3124: // GREY_KITE_SHIELD
				return 38;
			case 3131: // BLACK_GAUNTLETS
			case 3133: // WHITE_GAUNTLETS
			case 3135: // GREY_GAUNTLETS
			case 3132: // BLACK_GREAVES
			case 3134: // WHITE_GREAVES
			case 3136: // GREY_GREAVES
				return 22;
			default:
				return 0;
		}
	}

	private int getGodEquipmentTargetMagic(final int itemId) {
		return 0;
	}

	public int getBearHideIntimidatePercent() {
		return hasFullBearHideSet() ? 10 : 0;
	}

	public double getBearHideIntimidateProcChance() {
		return hasFullBearHideSet() ? 0.20D : 0.0D;
	}

	public double getGoblinEnragedProcChance() {
		return hasFullGoblinHideSet() ? 0.20D : 0.0D;
	}

	public double getGiantBruteForceProcChance() {
		return hasFullGiantSet() ? 0.20D : 0.0D;
	}

	public double getMossGiantBruteForceProcChance() {
		return hasFullMossGiantSet() ? 0.20D : 0.0D;
	}

	public double getIceGiantBruteForceProcChance() {
		return hasFullIceGiantSet() ? 0.20D : 0.0D;
	}

	public double getFireGiantBruteForceProcChance() {
		return hasFullFireGiantSet() ? 0.20D : 0.0D;
	}

	public double getOgreStaggeringBlowProcChance() {
		return hasFullOgreSet() ? 0.20D : 0.0D;
	}

	public int getMeleePoisonArmorMaxPower() {
		return hasFullScorpionCarapaceSet() ? 10 : 0;
	}

	public int getMeleePoisonArmorAppliedPower() {
		return hasFullScorpionCarapaceSet() ? 5 : 0;
	}

	public double getMeleePoisonArmorProcChance() {
		return hasFullScorpionCarapaceSet() ? 0.20D : 0.0D;
	}

	public int getRangedPoisonArmorMaxPower() {
		return hasFullSpiderCarapaceSet() ? 10 : 0;
	}

	public int getRangedPoisonArmorAppliedPower() {
		return hasFullSpiderCarapaceSet() ? 5 : 0;
	}

	public double getRangedPoisonArmorProcChance() {
		return hasFullSpiderCarapaceSet() ? 0.20D : 0.0D;
	}

	public int getMagicPoisonArmorMaxPower() {
		return hasFullMagicSpiderCarapaceSet() ? 20 : 0;
	}

	public int getMagicPoisonArmorAppliedPower() {
		return hasFullMagicSpiderCarapaceSet() ? 10 : 0;
	}

	public double getMagicPoisonArmorProcChance() {
		return hasFullMagicSpiderCarapaceSet() ? 0.20D : 0.0D;
	}

	public int getBabyDragonSmokeAccuracyDebuffPercent() {
		return hasFullBabyDragonSet() ? 10 : 0;
	}

	public double getBabyDragonSmokeProcChance() {
		return hasFullBabyDragonSet() ? 0.20D : 0.0D;
	}

	public int getInfernalFireProcMaxHit() {
		if (hasFullBalrogSet()) {
			return 18;
		}
		if (hasFullBlackDemonSet()) {
			return 12;
		}
		if (hasFullDemonSet()) {
			return 8;
		}
		return 0;
	}

	public int getInfernalFireDefenseDebuffPercent() {
		if (hasFullBalrogSet()) {
			return 12;
		}
		if (hasFullBlackDemonSet()) {
			return 9;
		}
		if (hasFullDemonSet()) {
			return 6;
		}
		return 0;
	}

	public double getInfernalFireProcChance() {
		return getInfernalFireProcMaxHit() > 0 ? 0.20D : 0.0D;
	}

	public int getInfernalArmorPieceCount() {
		return countMatchingEquippedPieces(demonSetIds)
			+ countMatchingEquippedPieces(blackDemonSetIds)
			+ countMatchingEquippedPieces(balrogSetIds);
	}

	public boolean hasFullBlueDragonSet() {
		return hasFullSet(blueDragonSetIds);
	}

	public boolean hasFullEarthDragonSet() {
		return hasFullSet(earthDragonSetIds);
	}

	public boolean hasFullRedDragonSet() {
		return hasFullSet(redDragonSetIds);
	}

	public boolean hasFullBlackDragonSet() {
		return hasFullSet(blackDragonSetIds);
	}

	public boolean hasFullKingBlackDragonSet() {
		return hasFullSet(kingBlackDragonSetIds);
	}

	public int getMeleeOffense() {
		return getModifiedOffense(PrayerCatalog.CombatStyle.MELEE);
	}

	public int getRangedOffense() {
		return getModifiedOffense(PrayerCatalog.CombatStyle.RANGED);
	}

	public int getMagicOffense() {
		return getModifiedOffense(PrayerCatalog.CombatStyle.MAGIC);
	}

	public int getDisplayedMeleeOffense() {
		return getDisplayedModifiedOffense(PrayerCatalog.CombatStyle.MELEE);
	}

	public int getDisplayedRangedOffense() {
		return getDisplayedModifiedOffense(PrayerCatalog.CombatStyle.RANGED);
	}

	public int getDisplayedMagicOffense() {
		return getDisplayedModifiedOffense(PrayerCatalog.CombatStyle.MAGIC);
	}

	public String getOffenseDebugSummary() {
		return getOffenseDebugSummary(PrayerCatalog.CombatStyle.MELEE, "melee")
			+ getOffenseDebugSummary(PrayerCatalog.CombatStyle.RANGED, " ranged")
			+ getOffenseDebugSummary(PrayerCatalog.CombatStyle.MAGIC, " magic");
	}

	private String getOffenseDebugSummary(final PrayerCatalog.CombatStyle combatStyle, final String label) {
		final int base = getRawOffense(combatStyle);
		final int jewelryBonus = getEquippedElementalPowerBonus(combatStyle) + getDeathRingLowHealthPowerBonus();
		final int penalty = getArmorPowerPenalty(combatStyle);
		final int displayValue = getDisplayedModifiedOffense(combatStyle);
		final int combatValue = Math.max(0, displayValue);
		return " " + label + "Base=" + base
			+ " " + label + "Bonus=" + jewelryBonus
			+ " " + label + "Penalty=" + penalty
			+ " " + label + "Display=" + displayValue
			+ " " + label + "Combat=" + combatValue;
	}

	private int getModifiedOffense(final PrayerCatalog.CombatStyle combatStyle) {
		return Math.max(0, getDisplayedModifiedOffense(combatStyle));
	}

	private int getDisplayedModifiedOffense(final PrayerCatalog.CombatStyle combatStyle) {
		return applyOffenseBonus(combatStyle, getRawOffense(combatStyle)) - getArmorPowerPenalty(combatStyle);
	}

	private int getRawOffense(final PrayerCatalog.CombatStyle combatStyle) {
		int total = 0;
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list) {
					if (item != null && isOffenseItem(item, combatStyle)) {
						total += getDerivedOffense(item, combatStyle);
					}
				}
			}
		} else {
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					if (item.isWielded() && isOffenseItem(item, combatStyle)) {
						total += getDerivedOffense(item, combatStyle);
					}
				}
			}
		}
		return total;
	}

	private boolean isOffenseItem(final Item item, final PrayerCatalog.CombatStyle combatStyle) {
		switch (combatStyle) {
			case MELEE:
			case MAGIC:
				return isOffenseSlot(item, EquipmentSlot.SLOT_MAINHAND, EquipmentSlot.SLOT_OFFHAND);
			case RANGED:
				return isOffenseSlot(item, EquipmentSlot.SLOT_MAINHAND, EquipmentSlot.SLOT_OFFHAND, EquipmentSlot.SLOT_AMMO);
			default:
				return false;
		}
	}

	private int getDerivedOffense(final Item item, final PrayerCatalog.CombatStyle combatStyle) {
		switch (combatStyle) {
			case MELEE:
				return getDerivedMeleeOffense(item);
			case RANGED:
				return getDerivedRangedOffense(item);
			case MAGIC:
				return getDerivedMagicOffense(item);
			default:
				return 0;
		}
	}

	public int getWeaponSpeed() {
		int speed = 0;
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list) {
					if (item != null && isOffenseSlot(item, EquipmentSlot.SLOT_MAINHAND, EquipmentSlot.SLOT_OFFHAND)) {
						speed = Math.max(speed, getDerivedWeaponSpeed(item));
					}
				}
			}
		} else {
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					if (item.isWielded() && isOffenseSlot(item, EquipmentSlot.SLOT_MAINHAND, EquipmentSlot.SLOT_OFFHAND)) {
						speed = Math.max(speed, getDerivedWeaponSpeed(item));
					}
				}
			}
		}
		return speed;
	}

	public double getDamageRollHighBiasChance() {
		double bonus = getEquippedWeaponFamilyHighRollBias();
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list) {
					if (item != null && isMediumHelmet(item)) {
						return 0.10D + bonus;
					}
				}
			}
		} else {
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					if (item.isWielded() && isMediumHelmet(item)) {
						return 0.10D + bonus;
					}
				}
			}
		}
		return bonus;
	}

	public double getArmorSpeedMultiplier() {
		double multiplier = 1.0D;
		int dragonSpeedPieces = 0;
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list) {
					if (item == null) {
						continue;
					}
					if (isDragonSpeedArmor(item)) {
						dragonSpeedPieces++;
						continue;
					}
					if (isChainArmor(item)) {
						return 1.05D * multiplier;
					}
				}
			}
		} else {
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					if (!item.isWielded()) {
						continue;
					}
					if (isDragonSpeedArmor(item)) {
						dragonSpeedPieces++;
						continue;
					}
					if (isChainArmor(item)) {
						return 1.05D * multiplier;
					}
				}
			}
		}
		if (dragonSpeedPieces > 0) {
			return multiplier * (1.0D + (dragonSpeedPieces * 0.05D));
		}
		return multiplier;
	}

	public double getChaosAmuletSecondHitChance() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0.0D : EnchantingItemEffects.getChaosAmuletSecondHitChance(neckItem.getCatalogId());
	}

	public double getChaosRecoilChance() {
		Item neckItem = getEquippedNeckItem();
		Item ringItem = getEquippedRingItem();
		return Math.min(1.0D,
			(neckItem == null ? 0.0D : EnchantingItemEffects.getChaosRingRecoilChance(neckItem.getCatalogId()))
				+ (ringItem == null ? 0.0D : EnchantingItemEffects.getChaosRingRecoilChance(ringItem.getCatalogId())));
	}

	public int getChaosRecoilDamageDivisor() {
		Item neckItem = getEquippedNeckItem();
		Item ringItem = getEquippedRingItem();
		final boolean hasNeck = neckItem != null && EnchantingItemEffects.getChaosRingRecoilChance(neckItem.getCatalogId()) > 0.0D;
		final boolean hasRing = ringItem != null && EnchantingItemEffects.getChaosRingRecoilChance(ringItem.getCatalogId()) > 0.0D;
		return hasNeck && hasRing ? 5 : 10;
	}

	public double getDeathAmuletDamagePerKillBonus() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0.0D : EnchantingItemEffects.getDeathAmuletDamagePerKillBonus(neckItem.getCatalogId());
	}

	public double getBloodAmuletLifestealChance() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0.0D : EnchantingItemEffects.getBloodAmuletLifestealChance(neckItem.getCatalogId());
	}

	public int getDeathRingLowHealthPowerBonus() {
		Item ringItem = getEquippedRingItem();
		if (ringItem == null) {
			return 0;
		}
		return EnchantingItemEffects.getDeathRingLowHealthPowerBonus(ringItem.getCatalogId(),
			player.getSkills().getLevel(Skill.HITS.id()), player.getSkills().getMaxStat(Skill.HITS.id()));
	}

	public int getDeathNecklaceLowHealthDefenseBonus() {
		Item neckItem = getEquippedNeckItem();
		if (neckItem == null) {
			return 0;
		}
		return EnchantingItemEffects.getDeathNecklaceLowHealthDefenseBonus(neckItem.getCatalogId(),
			player.getSkills().getLevel(Skill.HITS.id()), player.getSkills().getMaxStat(Skill.HITS.id()));
	}

	public int getDeathAmuletBurstRadius() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0 : EnchantingItemEffects.getDeathAmuletBurstRadius(neckItem.getCatalogId());
	}

	public double getDeathAmuletBurstPercent() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0.0D : EnchantingItemEffects.getDeathAmuletBurstPercent(neckItem.getCatalogId());
	}

	public int getLifeNecklaceSummonHealthPercent() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0 : EnchantingItemEffects.getLifeNecklaceSummonHealthPercent(neckItem.getCatalogId());
	}

	public int getLifeRingSupportDurationPercent() {
		Item ringItem = getEquippedRingItem();
		return ringItem == null ? 0 : EnchantingItemEffects.getLifeRingSupportDurationPercent(ringItem.getCatalogId());
	}

	public int getLifeAmuletSummonMaxDamageBonus() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0 : EnchantingItemEffects.getLifeAmuletSummonMaxDamageBonus(neckItem.getCatalogId());
	}

	public int getBloodAmuletHitsBonus(final int baseHits) {
		Item neckItem = getEquippedNeckItem();
		Item ringItem = getEquippedRingItem();
		return (neckItem == null ? 0 : EnchantingItemEffects.getBloodJewelryHitsBonus(neckItem.getCatalogId()))
			+ (ringItem == null ? 0 : EnchantingItemEffects.getBloodJewelryHitsBonus(ringItem.getCatalogId()));
	}

	public boolean hasFullCowHideSet() {
		return hasFullSet(cowHideSetIds);
	}

	public boolean hasFullUnicornHideSet() {
		return hasFullSet(unicornHideSetIds);
	}

	public boolean hasFullBlackUnicornHideSet() {
		return hasFullSet(blackUnicornHideSetIds);
	}

	public boolean hasFullBearHideSet() {
		return hasFullSet(bearHideSetIds);
	}

	public boolean hasFullGoblinHideSet() {
		return hasFullSet(goblinHideSetIds);
	}

	public boolean hasFullWolfSet() {
		return hasFullSet(wolfHideSetIds);
	}

	public boolean hasFullScorpionCarapaceSet() {
		return hasFullSet(scorpionCarapaceSetIds);
	}

	public boolean hasFullSpiderCarapaceSet() {
		return hasFullSet(spiderCarapaceSetIds);
	}

	public boolean hasFullMagicSpiderCarapaceSet() {
		return hasFullSet(magicSpiderCarapaceSetIds);
	}

	public boolean hasFullGiantSet() {
		return hasFullSet(giantSetIds);
	}

	public boolean hasFullOgreSet() {
		return hasFullSet(ogreSetIds);
	}

	public boolean hasFullBabyDragonSet() {
		return hasFullSet(babyDragonSetIds);
	}

	public boolean hasFullMossGiantSet() {
		return hasFullSet(mossGiantSetIds);
	}

	public boolean hasFullIceGiantSet() {
		return hasFullSet(iceGiantSetIds);
	}

	public boolean hasFullDemonSet() {
		return hasFullSet(demonSetIds);
	}

	public boolean hasFullBlackDemonSet() {
		return hasFullSet(blackDemonSetIds);
	}

	public boolean hasFullBalrogSet() {
		return hasFullSet(balrogSetIds);
	}

	public boolean hasFullFireGiantSet() {
		return hasFullSet(fireGiantSetIds);
	}

	public boolean hasFullHellhoundSet() {
		return hasFullSet(hellhoundSetIds);
	}

	private boolean hasFullSet(final int[] itemIds) {
		return countMatchingEquippedPieces(itemIds) == itemIds.length;
	}

	private int countMatchingEquippedPieces(final int[] itemIds) {
		int count = 0;
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list) {
					if (item != null && DataConversions.inArray(itemIds, item.getCatalogId())) {
						count++;
					}
				}
			}
		} else {
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					if (item != null && item.isWielded() && DataConversions.inArray(itemIds, item.getCatalogId())) {
						count++;
					}
				}
			}
		}
		return count;
	}

	public double getMindAmuletXpBonus() {
		Item neckItem = getEquippedNeckItem();
		Item ringItem = getEquippedRingItem();
		return (neckItem == null ? 0.0D : EnchantingItemEffects.getMindAmuletXpBonus(neckItem.getCatalogId()))
			+ (ringItem == null ? 0.0D : EnchantingItemEffects.getMindAmuletXpBonus(ringItem.getCatalogId()));
	}

	public double getBodyAmuletXpBonus() {
		Item neckItem = getEquippedNeckItem();
		Item ringItem = getEquippedRingItem();
		return (neckItem == null ? 0.0D : EnchantingItemEffects.getBodyAmuletXpBonus(neckItem.getCatalogId()))
			+ (ringItem == null ? 0.0D : EnchantingItemEffects.getBodyAmuletXpBonus(ringItem.getCatalogId()));
	}

	public double getMindAmuletPotionDurationBonus() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0.0D : EnchantingItemEffects.getMindAmuletPotionDurationBonus(neckItem.getCatalogId());
	}

	public double getBodyAmuletRegenSpeedBonus() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0.0D : EnchantingItemEffects.getBodyAmuletRegenSpeedBonus(neckItem.getCatalogId());
	}

	public double getMindCombatAmuletXpBonus() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0.0D : EnchantingItemEffects.getMindCombatAmuletXpBonus(neckItem.getCatalogId());
	}

	public double getBodyDisciplineAmuletXpBonus() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0.0D : EnchantingItemEffects.getBodyDisciplineAmuletXpBonus(neckItem.getCatalogId());
	}

	public double getNatureAmuletFoodBonus() {
		Item neckItem = getEquippedNeckItem();
		Item ringItem = getEquippedRingItem();
		return (neckItem == null ? 0.0D : EnchantingItemEffects.getNatureAmuletFoodBonus(neckItem.getCatalogId()))
			+ (ringItem == null ? 0.0D : EnchantingItemEffects.getNatureAmuletFoodBonus(ringItem.getCatalogId()));
	}

	public int getNatureAmuletPoisonDecayBonus() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0 : EnchantingItemEffects.getNatureAmuletPoisonDecayBonus(neckItem.getCatalogId());
	}

	public double getCosmicAmuletExtraResourceChance() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0.0D : EnchantingItemEffects.getCosmicAmuletExtraResourceChance(neckItem.getCatalogId());
	}

	public double getCosmicAmuletRareGatheringDoubleChance() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0.0D : EnchantingItemEffects.getCosmicAmuletRareGatheringDoubleChance(neckItem.getCatalogId());
	}

	public double getCosmicNecklaceStandardDropChance() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0.0D : EnchantingItemEffects.getCosmicNecklaceStandardDropChance(neckItem.getCatalogId());
	}

	public boolean tryBankMonsterLootWithLawNecklace(final Item item) {
		if (item == null || item.getAmount() <= 0) {
			return false;
		}
		final Item neckItem = getEquippedNeckItem();
		if (neckItem == null || EnchantingItemEffects.isLawAmulet(neckItem.getCatalogId())) {
			return false;
		}
		final int maxCharges = EnchantingItemEffects.getLawItemMaxCharges(neckItem.getCatalogId());
		if (maxCharges <= 3) {
			return false;
		}
		int charges = neckItem.getItemStatus().getDurability();
		charges = Math.min(maxCharges, charges);
		if (charges <= 0) {
			return false;
		}

		final Item bankItem = new Item(item.getCatalogId(), item.getAmount(), item.getNoted());
		if (!player.getBank().canHold(bankItem)) {
			return false;
		}
		if (!player.getBank().add(bankItem, false)) {
			return false;
		}

		final int chargeCost = Math.max(1, item.getAmount());
		charges -= chargeCost;
		if (charges <= 0) {
			remove(neckItem, 1, true);
			player.message("@ora@Your law necklace sends the loot to your bank and crumbles.");
		} else {
			neckItem.getItemStatus().setDurability(charges);
			player.message("@ora@Your law necklace sends the loot to your bank. " + formatLawCharges(charges));
		}
		return true;
	}

	public int bankSkillingDropWithLawRing(final Item item) {
		if (item == null || item.getAmount() <= 0) {
			return 0;
		}
		final Item ringItem = getEquippedRingItem();
		if (ringItem == null || !EnchantingItemEffects.isLawRing(ringItem.getCatalogId())) {
			return 0;
		}
		if (item.getNoted()) {
			return 0;
		}
		if (item.getDef(player.getWorld()).isStackable()) {
			return 0;
		}
		final int maxCharges = EnchantingItemEffects.getLawItemMaxCharges(ringItem.getCatalogId());
		if (maxCharges <= 0) {
			return 0;
		}
		int charges = ringItem.getItemStatus().getDurability();
		charges = Math.min(maxCharges, charges);
		if (charges <= 0) {
			return 0;
		}
		int banked = 0;
		for (int count = 0; count < item.getAmount() && charges > 0; count++) {
			final Item bankItem = new Item(item.getCatalogId(), 1, false);
			if (!player.getBank().canHold(bankItem) || !player.getBank().add(bankItem, false)) {
				break;
			}
			banked++;
			charges--;
		}
		if (banked <= 0) {
			return 0;
		}
		if (charges <= 0) {
			remove(ringItem, 1, true);
			player.message("@ora@Your law ring sends your resources to your bank and crumbles.");
		} else {
			ringItem.getItemStatus().setDurability(charges);
			player.message("@ora@Your law ring sends your resources to your bank. " + formatLawCharges(charges));
		}
		return banked;
	}

	private String formatLawCharges(final int charges) {
		return "It has " + charges + " charge" + (charges == 1 ? "" : "s") + " remaining.";
	}

	public double getCosmicAmuletGemChanceMultiplier() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 1.0D : EnchantingItemEffects.getCosmicAmuletGemChanceMultiplier(neckItem.getCatalogId());
	}

	public double getCosmicAmuletHerbQualityChance() {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0.0D : EnchantingItemEffects.getCosmicAmuletHerbQualityChance(neckItem.getCatalogId());
	}

	public double getWoolRobeRunePreservationChance(final int runeId) {
		double chance = 0.0D;
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list) {
					if (item != null) {
						chance += EnchantingItemEffects.getWoolRobeRunePreservationChance(item.getCatalogId(), runeId);
					}
				}
			}
			return Math.min(1.0D, chance);
		}
		synchronized (player.getCarriedItems().getInventory().getItems()) {
			for (Item item : player.getCarriedItems().getInventory().getItems()) {
				if (item != null && item.isWielded()) {
					chance += EnchantingItemEffects.getWoolRobeRunePreservationChance(item.getCatalogId(), runeId);
				}
			}
		}
		return Math.min(1.0D, chance);
	}

	public int getSoulAmuletExtraKeptItems() {
		Item neckItem = getEquippedNeckItem();
		Item ringItem = getEquippedRingItem();
		return (neckItem == null ? 0 : EnchantingItemEffects.getSoulAmuletExtraKeptItems(neckItem.getCatalogId()))
			+ (ringItem == null ? 0 : EnchantingItemEffects.getSoulAmuletExtraKeptItems(ringItem.getCatalogId()));
	}

	public int getMeleeDefense() {
		int total = 0;
		final RobeEffects robeEffects = getEquippedRobeEffects();
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list) {
					if (item == null) {
						continue;
					}
					total += getDerivedMeleeDefense(item);
					total += robeEffects.getAdditionalMeleeDefense(item);
				}
			}
		} else {
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					if (!item.isWielded()) {
						continue;
					}
					total += getDerivedMeleeDefense(item);
					total += robeEffects.getAdditionalMeleeDefense(item);
				}
			}
		}
		total = applyDefenseBonus(PrayerCatalog.CombatStyle.MELEE, total);
		return robeEffects.applyMissingHealthDefenseBonus(total);
	}

	public int getRangedDefense() {
		int total = 0;
		final RobeEffects robeEffects = getEquippedRobeEffects();
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list) {
					if (item == null) {
						continue;
					}
					total += getDerivedRangedDefense(item);
					total += robeEffects.getAdditionalRangedDefense(item);
				}
			}
		} else {
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					if (!item.isWielded()) {
						continue;
					}
					total += getDerivedRangedDefense(item);
					total += robeEffects.getAdditionalRangedDefense(item);
				}
			}
		}
		total = applyDefenseBonus(PrayerCatalog.CombatStyle.RANGED, total);
		return robeEffects.applyMissingHealthDefenseBonus(total);
	}

	public int getMagicDefense() {
		int total = 0;
		final RobeEffects robeEffects = getEquippedRobeEffects();
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list) {
					if (item == null) {
						continue;
					}
					total += getDerivedMagicDefense(item);
				}
			}
		} else {
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					if (!item.isWielded()) {
						continue;
					}
					total += getDerivedMagicDefense(item);
				}
			}
		}
		total = applyDefenseBonus(PrayerCatalog.CombatStyle.MAGIC, total);
		total = robeEffects.applyFireMagicDefenseBonus(total);
		return robeEffects.applyMissingHealthDefenseBonus(total);
	}

	private int applyOffenseBonus(final PrayerCatalog.CombatStyle combatStyle, final int total) {
		final int bonus = getEquippedElementalPowerBonus(combatStyle) + getDeathRingLowHealthPowerBonus();
		if (bonus <= 0) {
			return total;
		}
		return total + bonus;
	}

	private int getArmorPowerPenalty(final PrayerCatalog.CombatStyle combatStyle) {
		int pieces = 0;
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list) {
					if (item != null && appliesArmorPowerPenalty(item, combatStyle)) {
						pieces++;
					}
				}
			}
		} else {
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					if (item != null && item.isWielded() && appliesArmorPowerPenalty(item, combatStyle)) {
						pieces++;
					}
				}
			}
		}
		return pieces * ARMOR_POWER_PENALTY_PER_MAJOR_SLOT;
	}

	private boolean appliesArmorPowerPenalty(final Item item, final PrayerCatalog.CombatStyle combatStyle) {
		if (!isMajorArmorPenaltySlot(item)) {
			return false;
		}
		switch (combatStyle) {
			case MELEE:
				return isClothArmorPenaltyItem(item);
			case RANGED:
				return isMetalArmorPenaltyItem(item);
			case MAGIC:
				return isLeatherArmorPenaltyItem(item);
			default:
				return false;
		}
	}

	private int applyDefenseBonus(final PrayerCatalog.CombatStyle combatStyle, final int total) {
		final int bonus = getEquippedElementalDefenseBonus(combatStyle) + getDeathNecklaceLowHealthDefenseBonus();
		if (bonus <= 0) {
			return total;
		}
		return total + bonus;
	}

	private int getEquippedElementalPowerBonus(final PrayerCatalog.CombatStyle combatStyle) {
		Item neckItem = getEquippedNeckItem();
		Item ringItem = getEquippedRingItem();
		return (neckItem == null ? 0 : EnchantingItemEffects.getElementalPowerBonus(neckItem.getCatalogId(), combatStyle))
			+ (ringItem == null ? 0 : EnchantingItemEffects.getElementalPowerBonus(ringItem.getCatalogId(), combatStyle));
	}

	private int getEquippedElementalDefenseBonus(final PrayerCatalog.CombatStyle combatStyle) {
		Item neckItem = getEquippedNeckItem();
		return neckItem == null ? 0 : EnchantingItemEffects.getElementalDefenseBonus(neckItem.getCatalogId(), combatStyle);
	}

	public boolean hasEquippedMeleeWeapon() {
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list) {
					if (item != null
						&& isOffenseSlot(item, EquipmentSlot.SLOT_MAINHAND, EquipmentSlot.SLOT_OFFHAND)
						&& getDerivedMeleeOffense(item) > 0) {
						return true;
					}
				}
			}
			return false;
		}
		synchronized (player.getCarriedItems().getInventory().getItems()) {
			for (Item item : player.getCarriedItems().getInventory().getItems()) {
				if (item.isWielded()
					&& isOffenseSlot(item, EquipmentSlot.SLOT_MAINHAND, EquipmentSlot.SLOT_OFFHAND)
					&& getDerivedMeleeOffense(item) > 0) {
					return true;
				}
			}
		}
		return false;
	}

	public int getMindRobePieces() {
		return getEquippedRobeEffects().mindPieces;
	}

	public int getBodyRobePieces() {
		return getEquippedRobeEffects().bodyPieces;
	}

	public int getNatureRobePieces() {
		return getEquippedRobeEffects().naturePieces;
	}

	public int getCosmicRobePieces() {
		return getEquippedRobeEffects().cosmicPieces;
	}

	public int getChaosRobePieces() {
		return getEquippedRobeEffects().chaosPieces;
	}

	public int getSoulRobePieces() {
		return getEquippedRobeEffects().soulPieces;
	}

	public int getBloodRobePieces() {
		return getEquippedRobeEffects().bloodPieces;
	}

	public int getLawRobePieces() {
		return getEquippedRobeEffects().lawPieces;
	}

	private Item getEquippedNeckItem() {
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				return list[EquipmentSlot.SLOT_NECK.getIndex()];
			}
		}
		synchronized (player.getCarriedItems().getInventory().getItems()) {
			for (Item item : player.getCarriedItems().getInventory().getItems()) {
				if (item.isWielded() && item.getDef(player.getWorld()).getWieldPosition() == EquipmentSlot.SLOT_NECK.getIndex()) {
					return item;
				}
			}
		}
		return null;
	}

	private Item getEquippedRingItem() {
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				return list[EquipmentSlot.SLOT_RING.getIndex()];
			}
		}
		synchronized (player.getCarriedItems().getInventory().getItems()) {
			for (Item item : player.getCarriedItems().getInventory().getItems()) {
				if (item.isWielded() && item.getDef(player.getWorld()).getWieldPosition() == EquipmentSlot.SLOT_RING.getIndex()) {
					return item;
				}
			}
		}
		return null;
	}

	private int getDerivedMeleeOffense(Item item) {
		int explicit = item.getDef(player.getWorld()).getMeleeOffense();
		if (explicit != 0) {
			return explicit;
		}
		String lowerName = item.getDef(player.getWorld()).getName().toLowerCase();
		if (lowerName.contains("bow") || lowerName.contains("crossbow") || lowerName.contains("dart")) {
			return 0;
		}
		if (lowerName.contains("throwing knife")) {
			return 0;
		}
			if (lowerName.contains("staff")
				&& (getScaledMagicBonus(item) > 0
					|| item.getDef(player.getWorld()).getMagicOffense() > 0)) {
				return 0;
			}
			return (int) Math.ceil(getScaledWeaponPowerBonus(item) / 7.0D);
	}

	private int getDerivedRangedOffense(Item item) {
		int explicit = item.getDef(player.getWorld()).getRangedOffense();
		if (explicit != 0) {
			return explicit;
		}
		return getLegacyRangedOffense(item);
	}

	private int getDerivedMagicOffense(Item item) {
		if (!isStaffMagicWeapon(item)) {
			return 0;
		}
		int explicit = item.getDef(player.getWorld()).getMagicOffense();
		if (explicit != 0) {
			return explicit;
		}
			return (int) Math.ceil(getScaledMagicBonus(item) / 4.0D);
	}

	private boolean isStaffMagicWeapon(Item item) {
		if (item == null) {
			return false;
		}
		if (item.getDef(player.getWorld()).getWieldPosition() != EquipmentSlot.SLOT_MAINHAND.getIndex()) {
			return false;
		}
		return item.getDef(player.getWorld()).getName().toLowerCase().contains("staff");
	}

	private int getDerivedWeaponSpeed(Item item) {
		int explicit = item.getDef(player.getWorld()).getWeaponSpeed();
		if (explicit != 0) {
			return explicit;
		}
		return 0;
	}

	private int getDerivedMeleeDefense(Item item) {
		if (EnchantingItemEffects.isBaseWoolRobePiece(item.getCatalogId())
			|| EnchantingItemEffects.isEnchantedWoolRobePiece(item.getCatalogId())) {
			return EnchantingItemEffects.getWoolRobeMeleeDefense(item);
		}
		int explicit = item.getDef(player.getWorld()).getMeleeDefense();
		if (explicit != 0) {
			return explicit;
		}
		if (isMagicArmor(item) || isRangedArmor(item)) {
			return 0;
		}
			return (int) Math.ceil(getScaledArmourBonus(item) / 14.0D);
	}

	private int getDerivedRangedDefense(Item item) {
		if (EnchantingItemEffects.isBaseWoolRobePiece(item.getCatalogId())
			|| EnchantingItemEffects.isEnchantedWoolRobePiece(item.getCatalogId())) {
			return EnchantingItemEffects.getWoolRobeRangedDefense(item);
		}
		int explicit = item.getDef(player.getWorld()).getRangedDefense();
		if (explicit != 0) {
			return explicit;
		}
		if (!isRangedArmor(item)) {
			return 0;
		}
			return (int) Math.ceil(getScaledArmourBonus(item) / 14.0D);
	}

	private int getDerivedMagicDefense(Item item) {
		if (isBlessedWoolArmor(item.getCatalogId())) {
			return getBlessedWoolMagicDefense(item);
		}
		if (EnchantingItemEffects.isBaseWoolRobePiece(item.getCatalogId())
			|| EnchantingItemEffects.isEnchantedWoolRobePiece(item.getCatalogId())) {
			return EnchantingItemEffects.getWoolRobeMagicDefense(item);
		}
		int explicit = item.getDef(player.getWorld()).getMagicDefense();
		if (explicit != 0) {
			return explicit;
		}
		if (!isMagicArmor(item)) {
			return 0;
		}
		return (int) Math.ceil(getScaledArmourBonus(item) / 14.0D);
	}

	private int getBlessedWoolMagicDefense(final Item item) {
		final int itemId = item.getCatalogId();
		final int baseValue = getBlessedWoolBaseMagicDefense(itemId);
		final int targetValue = getBlessedWoolTargetMagicDefense(itemId);
		return baseValue + getDevotionScaledCombatBonus(item, baseValue, targetValue);
	}

	private int getBlessedWoolBaseMagicDefense(final int itemId) {
		switch (itemId) {
			case 3137: // ZAMORAK_WOOL_HAT
			case 3142: // SARADOMIN_WOOL_HAT
			case 3147: // GUTHIX_WOOL_HAT
				return 1;
			case 3138: // ZAMORAK_WOOL_ROBE_TOP
			case 3143: // SARADOMIN_WOOL_ROBE_TOP
			case 3148: // GUTHIX_WOOL_ROBE_TOP
				return 4;
			case 3139: // ZAMORAK_WOOL_ROBE_BOTTOM
			case 3144: // SARADOMIN_WOOL_ROBE_BOTTOM
			case 3149: // GUTHIX_WOOL_ROBE_BOTTOM
				return 3;
			case 3140: // ZAMORAK_WOOL_GLOVES
			case 3141: // ZAMORAK_WOOL_BOOTS
			case 3145: // SARADOMIN_WOOL_GLOVES
			case 3146: // SARADOMIN_WOOL_BOOTS
			case 3150: // GUTHIX_WOOL_GLOVES
			case 3151: // GUTHIX_WOOL_BOOTS
				return 2;
			default:
				return 0;
		}
	}

	private int getBlessedWoolTargetMagicDefense(final int itemId) {
		final int resourceCost = getGodEquipmentResourceCost(itemId);
		return resourceCost > 0 ? Math.max(getBlessedWoolBaseMagicDefense(itemId), (int) Math.ceil(9 * resourceCost * 0.6D)) : 0;
	}

	private RobeEffects getEquippedRobeEffects() {
		final RobeEffects robeEffects = new RobeEffects();
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list) {
					robeEffects.add(item);
				}
			}
		} else {
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					if (item.isWielded()) {
						robeEffects.add(item);
					}
				}
			}
		}
		return robeEffects;
	}

	private final class RobeEffects {
		private int earthPieces;
		private int airPieces;
		private int waterPieces;
		private int firePieces;
		private int bodyPieces;
		private int mindPieces;
		private int naturePieces;
		private int cosmicPieces;
		private int chaosPieces;
		private int lawPieces;
		private int deathPieces;
		private int soulPieces;
		private int bloodPieces;

		private void add(final Item item) {
			if (item == null) {
				return;
			}
			switch (EnchantingItemEffects.getAltarIdForWoolRobeItem(item.getCatalogId())) {
				case EnchantingItemEffects.EARTH_ALTAR:
					earthPieces++;
					break;
				case EnchantingItemEffects.AIR_ALTAR:
					airPieces++;
					break;
				case EnchantingItemEffects.WATER_ALTAR:
					waterPieces++;
					break;
				case EnchantingItemEffects.FIRE_ALTAR:
					firePieces++;
					break;
				case EnchantingItemEffects.BODY_ALTAR:
					bodyPieces++;
					break;
				case EnchantingItemEffects.MIND_ALTAR:
					mindPieces++;
					break;
				case EnchantingItemEffects.NATURE_ALTAR:
					naturePieces++;
					break;
				case EnchantingItemEffects.COSMIC_ALTAR:
					cosmicPieces++;
					break;
				case EnchantingItemEffects.CHAOS_ALTAR:
					chaosPieces++;
					break;
				case EnchantingItemEffects.LAW_ALTAR:
					lawPieces++;
					break;
				case EnchantingItemEffects.DEATH_ALTAR:
					deathPieces++;
					break;
				case EnchantingItemEffects.SOUL_ALTAR:
					soulPieces++;
					break;
				case EnchantingItemEffects.BLOOD_ALTAR:
					bloodPieces++;
					break;
				default:
					break;
			}
		}

		private int getAdditionalMeleeDefense(final Item item) {
			return 0;
		}

		private int getAdditionalRangedDefense(final Item item) {
			return 0;
		}

		private int applyFireMagicDefenseBonus(final int totalMagicDefense) {
			return totalMagicDefense;
		}

		private int applyMissingHealthDefenseBonus(final int totalDefense) {
			return totalDefense;
		}
	}

	private boolean isMediumHelmet(Item item) {
		int catalogId = item.getCatalogId();
		for (int mediumHelmetId : mediumHelmetIds) {
			if (catalogId == mediumHelmetId) {
				return true;
			}
		}
		return false;
	}

	private boolean isChainArmor(Item item) {
		int catalogId = item.getCatalogId();
		for (int chainBodyId : chainBodyIds) {
			if (catalogId == chainBodyId) {
				return true;
			}
		}
		for (int chainTopId : chainTopIds) {
			if (catalogId == chainTopId) {
				return true;
			}
		}
		return false;
	}

	private boolean isDragonSpeedArmor(Item item) {
		int catalogId = item.getCatalogId();
		return catalogId == ItemId.DRAGON_MEDIUM_HELMET.id()
			|| catalogId == ItemId.DRAGON_SCALE_MAIL.id()
			|| catalogId == ItemId.DRAGON_SCALE_MAIL_TOP.id()
			|| catalogId == ItemId.DRAGON_KITE_SHIELD.id();
	}

	private double getEquippedWeaponFamilyHighRollBias() {
		Item weaponItem = getEquippedWeaponItem();
		if (weaponItem == null) {
			return 0.0D;
		}
		if (isScimitar(weaponItem) || isBattleaxe(weaponItem)) {
			return 0.20D;
		}
		return 0.0D;
	}

	private Item getEquippedWeaponItem() {
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			synchronized (list) {
				for (Item item : list) {
					if (item != null
						&& isOffenseSlot(item, EquipmentSlot.SLOT_MAINHAND, EquipmentSlot.SLOT_OFFHAND)
						&& getDerivedMeleeOffense(item) > 0) {
						return item;
					}
				}
			}
			return null;
		}
		synchronized (player.getCarriedItems().getInventory().getItems()) {
			for (Item item : player.getCarriedItems().getInventory().getItems()) {
				if (item.isWielded()
					&& isOffenseSlot(item, EquipmentSlot.SLOT_MAINHAND, EquipmentSlot.SLOT_OFFHAND)
					&& getDerivedMeleeOffense(item) > 0) {
					return item;
				}
			}
		}
		return null;
	}

	private boolean isScimitar(Item item) {
		int catalogId = item.getCatalogId();
		for (int scimitarId : scimitarIds) {
			if (catalogId == scimitarId) {
				return true;
			}
		}
		return false;
	}

	private boolean isBattleaxe(Item item) {
		int catalogId = item.getCatalogId();
		for (int battleaxeId : battleaxeIds) {
			if (catalogId == battleaxeId) {
				return true;
			}
		}
		return false;
	}

	private boolean isOffenseSlot(Item item, EquipmentSlot... allowedSlots) {
		EquipmentSlot itemSlot = EquipmentSlot.get(item.getDef(player.getWorld()).getWieldPosition());
		if (itemSlot == null) {
			return false;
		}
		for (EquipmentSlot allowedSlot : allowedSlots) {
			if (itemSlot == allowedSlot) {
				return true;
			}
		}
		return false;
	}

	private boolean bowConflictsWithOffhand(Item requestedItem, Item equippedItem) {
		if (requestedItem == null || equippedItem == null) {
			return false;
		}
		ItemDefinition requestedDef = requestedItem.getDef(player.getWorld());
		ItemDefinition equippedDef = equippedItem.getDef(player.getWorld());
		if (requestedDef == null || equippedDef == null) {
			return false;
		}
		return (RangeUtils.isBow(requestedItem.getCatalogId())
				&& equippedDef.getWieldPosition() == EquipmentSlot.SLOT_OFFHAND.getIndex())
			|| (requestedDef.getWieldPosition() == EquipmentSlot.SLOT_OFFHAND.getIndex()
				&& RangeUtils.isBow(equippedItem.getCatalogId()));
	}

	private boolean allowsHandFootArmorOverlap(Item requestedItem, Item equippedItem) {
		ItemDefinition requestedDef = requestedItem.getDef(player.getWorld());
		ItemDefinition equippedDef = equippedItem.getDef(player.getWorld());
		if (requestedDef == null || equippedDef == null) {
			return false;
		}
		EquipmentSlot requestedSlot = EquipmentSlot.get(requestedDef.getWieldPosition());
		EquipmentSlot equippedSlot = EquipmentSlot.get(equippedDef.getWieldPosition());
		if (requestedSlot == null || equippedSlot == null) {
			return false;
		}
		return isHandFootArmorSlot(requestedSlot) && isBodyLegArmorSlot(equippedSlot)
			|| isHandFootArmorSlot(equippedSlot) && isBodyLegArmorSlot(requestedSlot);
	}

	private boolean isHandFootArmorSlot(EquipmentSlot slot) {
		return slot == EquipmentSlot.SLOT_GLOVES || slot == EquipmentSlot.SLOT_BOOTS;
	}

	private boolean isBodyLegArmorSlot(EquipmentSlot slot) {
		return slot == EquipmentSlot.SLOT_CHAIN_BODY
			|| slot == EquipmentSlot.SLOT_PLATE_BODY
			|| slot == EquipmentSlot.SLOT_PLATE_LEGS
			|| slot == EquipmentSlot.SLOT_SKIRT;
	}

	private boolean isRangedArmor(Item item) {
		String lowerName = item.getDef(player.getWorld()).getName().toLowerCase();
		return lowerName.contains("dragonhide")
			|| lowerName.contains("d'hide")
			|| lowerName.contains("coif")
			|| lowerName.contains("vambrace")
			|| lowerName.contains("chaps")
			|| lowerName.contains("cowl")
			|| lowerName.contains("ranger");
	}

	private boolean isMagicArmor(Item item) {
		String lowerName = item.getDef(player.getWorld()).getName().toLowerCase();
		return lowerName.contains("robe")
			|| lowerName.contains("wizard")
			|| lowerName.contains("mage")
			|| lowerName.contains("druid")
			|| lowerName.contains("shaman");
	}

	private boolean isMajorArmorPenaltySlot(Item item) {
		EquipmentSlot itemSlot = EquipmentSlot.get(item.getDef(player.getWorld()).getWieldPosition());
		if (itemSlot == null) {
			return false;
		}
		switch (itemSlot) {
			case SLOT_LARGE_HELMET:
			case SLOT_MEDIUM_HELMET:
			case SLOT_CHAIN_BODY:
			case SLOT_PLATE_BODY:
			case SLOT_PLATE_LEGS:
			case SLOT_SKIRT:
				return true;
			default:
				return false;
		}
	}

	private boolean isLeatherArmorPenaltyItem(Item item) {
		String lowerName = item.getDef(player.getWorld()).getName().toLowerCase();
		return lowerName.contains("leather")
			|| lowerName.contains("hide")
			|| lowerName.contains("carapace")
			|| lowerName.contains("coif")
			|| lowerName.contains("chaps")
			|| lowerName.contains("cuirass");
	}

	private boolean isClothArmorPenaltyItem(Item item) {
		return EnchantingItemEffects.isBaseWoolRobePiece(item.getCatalogId())
			|| EnchantingItemEffects.isEnchantedWoolRobePiece(item.getCatalogId())
			|| isMagicArmor(item);
	}

	private boolean isMetalArmorPenaltyItem(Item item) {
		return !isLeatherArmorPenaltyItem(item) && !isClothArmorPenaltyItem(item);
	}

	private boolean isArmorItem(Item item) {
		EquipmentSlot itemSlot = EquipmentSlot.get(item.getDef(player.getWorld()).getWieldPosition());
		if (itemSlot == null) {
			return false;
		}
		switch (itemSlot) {
			case SLOT_LARGE_HELMET:
			case SLOT_MEDIUM_HELMET:
			case SLOT_CHAIN_BODY:
			case SLOT_PLATE_BODY:
			case SLOT_PLATE_LEGS:
			case SLOT_SKIRT:
			case SLOT_GLOVES:
			case SLOT_BOOTS:
			case SLOT_OFFHAND:
				return true;
			default:
				return false;
		}
	}

	private int getLegacyRangedOffense(Item item) {
		switch (ItemId.getById(item.getCatalogId())) {
			case BRONZE_THROWING_DART:
			case POISONED_BRONZE_THROWING_DART:
			case BRONZE_ARROWS:
			case POISON_BRONZE_ARROWS:
				return 15;
			case IRON_THROWING_DART:
			case POISONED_IRON_THROWING_DART:
				return 17;
			case IRON_ARROWS:
			case POISON_IRON_ARROWS:
			case CROSSBOW_BOLTS:
			case POISON_CROSSBOW_BOLTS:
			case OYSTER_PEARL_BOLTS:
				return 20;
			case STEEL_THROWING_DART:
			case POISONED_STEEL_THROWING_DART:
				return 22;
			case STEEL_ARROWS:
			case POISON_STEEL_ARROWS:
			case MITHRIL_THROWING_DART:
			case POISONED_MITHRIL_THROWING_DART:
			case BRONZE_THROWING_KNIFE:
			case POISONED_BRONZE_THROWING_KNIFE:
				return 25;
			case ADAMANTITE_THROWING_DART:
			case POISONED_ADAMANTITE_THROWING_DART:
				return 27;
			case RUNE_THROWING_DART:
			case POISONED_RUNE_THROWING_DART:
			case MITHRIL_ARROWS:
			case POISON_MITHRIL_ARROWS:
			case IRON_THROWING_KNIFE:
			case POISONED_IRON_THROWING_KNIFE:
				return 30;
			case ADAMANTITE_ARROWS:
			case POISON_ADAMANTITE_ARROWS:
			case STEEL_THROWING_KNIFE:
			case POISONED_STEEL_THROWING_KNIFE:
			case BLACK_THROWING_KNIFE:
			case POISONED_BLACK_THROWING_KNIFE:
				return 35;
			case RUNE_ARROWS:
			case POISON_RUNE_ARROWS:
			case MITHRIL_THROWING_KNIFE:
			case POISONED_MITHRIL_THROWING_KNIFE:
				return 40;
			case ADAMANTITE_THROWING_KNIFE:
			case POISONED_ADAMANTITE_THROWING_KNIFE:
				return 45;
			case RUNE_THROWING_KNIFE:
			case POISONED_RUNE_THROWING_KNIFE:
			case DRAGON_ARROWS:
			case POISON_DRAGON_ARROWS:
			case DRAGON_BOLTS:
			case POISON_DRAGON_BOLTS:
				return 50;
			case SHORTBOW:
				return 14;
			case LONGBOW:
				return 20;
			case CROSSBOW:
			case PHOENIX_CROSSBOW:
				return 22;
			default:
				return 0;
		}
	}

	// Equipment::equipCount()
	// Returns the total count of items equipped.
	// Does not account for the quantity in the case of stacks.
	public int equipCount() {
		synchronized (list) {
			int total = 0;
			for (Item item : list) {
				if (item != null)
					total++;
			}
			return total;
		}
	}

	/** Equipment::EquipmentSlot
	 *  Enumerated list that names the equipment slots.
	 *  Can be used to rename front-end equipment slotIDs to server-recognized IDs.
	 */
	public enum EquipmentSlot {
		SLOT_LARGE_HELMET(0),
		SLOT_PLATE_BODY(1),
		SLOT_PLATE_LEGS(2),
		SLOT_OFFHAND(3),
		SLOT_MAINHAND(4),
		SLOT_MEDIUM_HELMET(5),
		SLOT_CHAIN_BODY(6),
		SLOT_SKIRT(7),
		SLOT_GLOVES(8),
		SLOT_BOOTS(9),
		SLOT_NECK(10),
		SLOT_CAPE(11),
		SLOT_AMMO(12),
		SLOT_RING(13);
		int index;

		EquipmentSlot(int index) {
			this.index = index;
		}

		public int getIndex() {
			return this.index;
		}

		public static EquipmentSlot get(int index) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (slot.getIndex() == index)
					return slot;
			}
			return null;
		}
	}

	public static void correctIndex(UnequipRequest request) {
		if (request.equipmentSlot == EquipmentSlot.SLOT_LARGE_HELMET) {
			if (request.player.getCarriedItems().getEquipment().get(EquipmentSlot.SLOT_LARGE_HELMET.getIndex()) != null) {
				request.item = request.player.getCarriedItems().getEquipment().get(EquipmentSlot.SLOT_LARGE_HELMET.getIndex());
				request.equipmentSlot = EquipmentSlot.SLOT_LARGE_HELMET;
			} else if (request.player.getCarriedItems().getEquipment().get(EquipmentSlot.SLOT_MEDIUM_HELMET.getIndex()) != null) {
				request.item = request.player.getCarriedItems().getEquipment().get(EquipmentSlot.SLOT_MEDIUM_HELMET.getIndex());
				request.equipmentSlot = EquipmentSlot.SLOT_MEDIUM_HELMET;
			}
		} else if (request.equipmentSlot == EquipmentSlot.SLOT_PLATE_BODY) {
			if (request.player.getCarriedItems().getEquipment().get(EquipmentSlot.SLOT_PLATE_BODY.getIndex()) != null) {
				request.item = request.player.getCarriedItems().getEquipment().get(EquipmentSlot.SLOT_PLATE_BODY.getIndex());
				request.equipmentSlot = EquipmentSlot.SLOT_PLATE_BODY;
			} else if (request.player.getCarriedItems().getEquipment().get(EquipmentSlot.SLOT_CHAIN_BODY.getIndex()) != null) {
				request.item = request.player.getCarriedItems().getEquipment().get(EquipmentSlot.SLOT_CHAIN_BODY.getIndex());
				request.equipmentSlot = EquipmentSlot.SLOT_CHAIN_BODY;
			}
		} else if (request.equipmentSlot == Equipment.EquipmentSlot.SLOT_PLATE_LEGS) {
			if (request.player.getCarriedItems().getEquipment().get(EquipmentSlot.SLOT_PLATE_LEGS.getIndex()) != null) {
				request.item = request.player.getCarriedItems().getEquipment().get(EquipmentSlot.SLOT_PLATE_LEGS.getIndex());
				request.equipmentSlot = EquipmentSlot.SLOT_PLATE_LEGS;
			} else if (request.player.getCarriedItems().getEquipment().get(EquipmentSlot.SLOT_SKIRT.getIndex()) != null) {
				request.item = request.player.getCarriedItems().getEquipment().get(EquipmentSlot.SLOT_SKIRT.getIndex());
				request.equipmentSlot = EquipmentSlot.SLOT_SKIRT;
			}
		} else if (request.equipmentSlot.getIndex() > 4) {
			request.item = request.player.getCarriedItems().getEquipment().get(request.equipmentSlot.getIndex() + 3);
			request.equipmentSlot = EquipmentSlot.get(request.equipmentSlot.getIndex() + 3);
		} else {
			request.item = request.player.getCarriedItems().getEquipment().get(request.equipmentSlot.getIndex());
			request.equipmentSlot = EquipmentSlot.get(request.equipmentSlot.getIndex());
		}
	}
}
