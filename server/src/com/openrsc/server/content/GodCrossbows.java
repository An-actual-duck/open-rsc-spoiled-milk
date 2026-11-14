package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;

/** God crossbow equipment and reserved, virtual ammunition. Never creates physical bolts. */
public final class GodCrossbows {
    public static final int PRAYER_SLOT = 16;
    public static final int RESERVATION = 80;
    public static final int CAPE_RESERVATION = 60;
    private GodCrossbows() { }

    public static PrayerCatalog.GodLine god(int itemId) {
        if (itemId == ItemId.SARADOMIN_CROSSBOW.id()) return PrayerCatalog.GodLine.SARADOMIN;
        if (itemId == ItemId.ZAMORAK_CROSSBOW.id()) return PrayerCatalog.GodLine.ZAMORAK;
        if (itemId == ItemId.GUTHIX_CROSSBOW.id()) return PrayerCatalog.GodLine.GUTHIX;
        return null;
    }

    public static boolean isGodCrossbow(int id) { return god(id) != null; }

    public static int equippedId(Player player) {
        if (player.getConfig().WANT_EQUIPMENT_TAB) {
            Item item = player.getCarriedItems().getEquipment().get(Equipment.EquipmentSlot.SLOT_MAINHAND.getIndex());
            return item != null && isGodCrossbow(item.getCatalogId()) ? item.getCatalogId() : -1;
        }
        for (Item item : player.getCarriedItems().getInventory().getItems())
            if (item != null && item.isWielded() && isGodCrossbow(item.getCatalogId())) return item.getCatalogId();
        return -1;
    }

    public static boolean canUse(Player player, PrayerCatalog.GodLine book) {
        return player != null && book != null && player.getConfig().WANT_MYWORLD && god(equippedId(player)) == book;
    }

    public static int cape(PrayerCatalog.GodLine god) {
        if (god == PrayerCatalog.GodLine.SARADOMIN) return ItemId.SARADOMIN_CAPE.id();
        if (god == PrayerCatalog.GodLine.ZAMORAK) return ItemId.ZAMORAK_CAPE.id();
        if (god == PrayerCatalog.GodLine.GUTHIX) return ItemId.GUTHIX_CAPE.id();
        return -1;
    }

    public static boolean hasMatchingCape(Player player) {
        PrayerCatalog.GodLine god = god(equippedId(player));
        return god != null && player.getCarriedItems().getEquipment().hasEquipped(cape(god));
    }

    public static int reservation(Player player) {
        return hasMatchingCape(player) ? CAPE_RESERVATION : RESERVATION;
    }

    public static boolean usesHolyBolts(Player player) {
        return player != null && canUse(player, player.getPrayerBook())
            && player.getPrayers().isPrayerActivated(PRAYER_SLOT)
            && player.getPrayers().getAllocatedPoints() <= player.getPrayerAllocationPoints();
    }

    public static int holyAmmoOffense(Player player) {
        return usesHolyBolts(player) ? (hasMatchingCape(player) ? 16 : 12) : 0;
    }

    /** Definition identity for damage formula compatibility only, never a resource or recovery. */
    public static int holyAmmoDefinition(Player player) {
        return hasMatchingCape(player) ? ItemId.ADAMANTITE_BOLTS.id() : ItemId.MITHRIL_BOLTS.id();
    }
}
