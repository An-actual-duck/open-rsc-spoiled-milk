package com.openrsc.server.content.market;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Auction messages use the existing game chat and message-box packets. */
public final class AuctionFeedback {
    private static final int MAX_RECEIPT_ROWS = 24;

    private AuctionFeedback() {}

    public static void status(Player player, String message) {
        player.message("@gre@[Auction House]@whi@ " + message);
    }

    public static void warning(Player player, String message) {
        ActionSender.sendBox(player, "Auction House%" + message, false);
    }

    public static void collection(Player player, String message) {
        ActionSender.sendBox(player, "Auction collections%" + message, false);
    }

    public static void collection(Player player, List<CollectibleItem> items, boolean received, int remaining) {
        StringBuilder body = new StringBuilder(received
            ? "Deposited in your bank:" : "Ready to collect from a banker:");
        int shown = Math.min(items.size(), MAX_RECEIPT_ROWS);
        for (int index = 0; index < shown; index++) {
            CollectibleItem item = items.get(index);
            body.append('%').append(describe(player, item.item_id, item.item_amount));
            if (item.explanation != null && !item.explanation.isEmpty()) {
                body.append(" - ").append(item.explanation);
            }
        }
        if (items.size() > shown) body.append("%And ").append(items.size() - shown).append(" more entries.");
        if (remaining > 0) {
            body.append("%Make room in your bank and speak to a banker again to collect the rest.");
        }
        ActionSender.sendBox(player, "Auction collections%" + body, true);
    }

    static String describe(Player player, int catalog, int amount) {
        if (catalog == ItemId.COINS.id()) return number(amount) + " gp";
        ItemDefinition item = player.getWorld().getServer().getEntityHandler().getItemDef(catalog);
        return (item == null ? "Item " + catalog : item.getName()) + " x " + number(amount);
    }

    static String number(int value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }
}
