package com.openrsc.server.plugins.custom.myworld.skills.enchanting;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import com.openrsc.server.plugins.triggers.UseLocTrigger;

import java.util.Optional;

import static com.openrsc.server.plugins.Functions.delay;
import static com.openrsc.server.plugins.Functions.mes;

public final class NatureAlchemyAmulet implements OpInvTrigger, UseLocTrigger {

	@Override
	public boolean blockOpInv(final Player player, final Integer invIndex, final Item item, final String command) {
		return item != null
			&& EnchantingItemEffects.isNatureAmulet(item.getCatalogId())
			&& "check".equalsIgnoreCase(command);
	}

	@Override
	public void onOpInv(final Player player, final Integer invIndex, final Item item, final String command) {
		if (item == null || !EnchantingItemEffects.isNatureAmulet(item.getCatalogId())) {
			return;
		}
		showRemainingCharges(player, item);
	}

	@Override
	public boolean blockUseLoc(final Player player, final GameObject obj, final Item item) {
		return item != null
			&& !item.getNoted()
			&& EnchantingItemEffects.isNatureAmulet(item.getCatalogId())
			&& normalizeNatureAltarId(obj.getID()) == EnchantingItemEffects.NATURE_ALTAR;
	}

	@Override
	public void onUseLoc(final Player player, final GameObject obj, final Item item) {
		if (item == null || item.getNoted() || normalizeNatureAltarId(obj.getID()) != EnchantingItemEffects.NATURE_ALTAR) {
			return;
		}
		final int maxCharges = EnchantingItemEffects.getNatureAlchemyAmuletMaxCharges(item.getCatalogId());
		if (maxCharges <= 0) {
			return;
		}
		if (EnchantingItemEffects.getNatureAlchemyAmuletCharges(player, item) >= maxCharges) {
			player.message("That Bangel is already fully charged.");
			return;
		}

		final int requiredRunes = getRechargeRuneCost(maxCharges);
		if (player.getCarriedItems().getInventory().countId(ItemId.NATURE_RUNE.id(), Optional.of(false)) < requiredRunes) {
			player.message("You need " + requiredRunes + " nature runes to recharge this Bangel.");
			return;
		}
		if (player.getCarriedItems().remove(new Item(ItemId.NATURE_RUNE.id(), requiredRunes)) == -1) {
			return;
		}

		EnchantingItemEffects.setNatureAlchemyAmuletCharges(player, item, maxCharges);
		mes("You hold the Bangel against the altar.");
		delay();
		mes("Nature energy flows back into it.");
		delay();
		player.message("It is fully recharged.");
	}

	private int getRechargeRuneCost(final int maxCharges) {
		if (maxCharges <= 0) {
			return 0;
		}
		return Math.max(10, ((maxCharges + 99) / 100) * 10);
	}

	private void showRemainingCharges(final Player player, final Item item) {
		final int charges = EnchantingItemEffects.getNatureAlchemyAmuletCharges(player, item);
		final int maxCharges = EnchantingItemEffects.getNatureAlchemyAmuletMaxCharges(item.getCatalogId());
		player.message("It has " + formatCharges(charges) + " remaining.");
		player.message("It can hold " + formatCharges(maxCharges) + ".");
	}

	private String formatCharges(final int charges) {
		return charges + " charge" + (charges == 1 ? "" : "s");
	}

	private int normalizeNatureAltarId(final int objectId) {
		if (objectId == EnchantingItemEffects.NATURE_ALTAR) {
			return objectId;
		}
		if (objectId == EnchantingItemEffects.NATURE_ALTAR + 1) {
			return EnchantingItemEffects.NATURE_ALTAR;
		}
		return -1;
	}
}
