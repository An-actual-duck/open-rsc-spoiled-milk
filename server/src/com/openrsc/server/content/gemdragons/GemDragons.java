package com.openrsc.server.content.gemdragons;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.DropTable;
import java.util.Map;
import java.util.ArrayList;
import java.util.Iterator;
import com.openrsc.server.model.container.Item;

/** The game-owned gem dragon family; no editor-specific content registration. */
public final class GemDragons {
    public static final int FIRST_ID = 877;
    public static final int LAST_ID = 881;
    public static final int[] UNIQUE_DENOMINATORS = {2048, 1536, 1024, 768, 512};
    private static final int[] GEMS = {ItemId.UNCUT_OPAL.id(), ItemId.UNCUT_JADE.id(),
        ItemId.UNCUT_RED_TOPAZ.id(), ItemId.UNCUT_SAPPHIRE.id(), ItemId.UNCUT_EMERALD.id(),
        ItemId.UNCUT_RUBY.id(), ItemId.UNCUT_DIAMOND.id(), ItemId.UNCUT_DRAGONSTONE.id()};
    private static final int[] RINGS = {ItemId.SAPPHIRE_RING.id(), ItemId.EMERALD_RING.id(),
        ItemId.RUBY_RING.id(), ItemId.DIAMOND_RING.id(), ItemId.DRAGONSTONE_RING.id()};

    private GemDragons() { }
    public static boolean contains(int id) { return id >= FIRST_ID && id <= LAST_ID; }
    public static int tier(int id) {
        if (!contains(id)) throw new IllegalArgumentException("Not a gem dragon: " + id);
        return id - FIRST_ID;
    }
    public static int fireMaxHit(int id) { return 45 + tier(id) * 3; }

    /** Exactly one normal crafting roll; no generic gem/RDT table can leak a higher gem. */
    public static DropTable craftingTable(int id) {
        int tier = tier(id), own = tier + 3;
        DropTable lower = new DropTable("Gem dragon lower gems");
        DropTable clusters = new DropTable("Gem dragon lower gem clusters");
        for (int gem = 0; gem < own; gem++) {
            int distance = own - gem;
            lower.addItemDrop(GEMS[gem], 1 + distance / 2, distance, true);
            clusters.addItemDrop(GEMS[gem], 3 + distance * 2, distance, true);
        }
        DropTable table = new DropTable("Gem dragon crafting (" + id + ")");
        table.addItemDrop(GEMS[own], 1, 16); // Own tier exists only here: never more than one.
        table.addTableDrop(lower, 50);
        table.addTableDrop(clusters, 16);
        table.addItemDrop(ItemId.GOLD_BAR.id(), 2 + tier, 28, true);
        table.addItemDrop(ItemId.GOLD_RING.id(), 1, 8);
        table.addItemDrop(RINGS[tier], 1, 8);
        table.addEmptyDrop(2);
        return table;
    }
    /** Final normal-loot boundary, including cosmic-necklace standard rerolls. */
    public static void enforceOwnGemCap(int id, ArrayList<Item> items) {
        if (!contains(id)) return;
        int own = GEMS[tier(id)+3];
        boolean received = false;
        for (Iterator<Item> it=items.iterator(); it.hasNext();) {
            Item item=it.next();
            if (item == null || item.getCatalogId()!=own) continue;
            if (received) it.remove();
            else { item.setAmount(1); received=true; }
        }
    }
    public static void register(Map<Integer,DropTable> tables) {
        for (int id=FIRST_ID; id<=LAST_ID; id++) tables.put(id, craftingTable(id));
    }
}
