package com.openrsc.server.content.gemdragons;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.DropTable;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.runtime.GameRandom;
import java.util.Map;

/** The game-owned gem dragon family; no editor-specific content registration. */
public final class GemDragons {
    public static final int FIRST_ID = 877;
    public static final int LAST_ID = 881;
    public static final int[] UNIQUE_DENOMINATORS = {2048, 1536, 1024, 768, 512};
    private static final int[] GEMS = {ItemId.UNCUT_SAPPHIRE.id(), ItemId.UNCUT_EMERALD.id(),
        ItemId.UNCUT_RUBY.id(), ItemId.UNCUT_DIAMOND.id(), ItemId.UNCUT_DRAGONSTONE.id()};
    private static final int[] RINGS = {ItemId.SAPPHIRE_RING.id(), ItemId.EMERALD_RING.id(),
        ItemId.RUBY_RING.id(), ItemId.DIAMOND_RING.id(), ItemId.DRAGONSTONE_RING.id()};
    private static final int[] QUANTITIES = {1, 3, 5};
    private static final int[] RANK_WEIGHTS = {64, 32, 16, 8, 4, 2};

    private GemDragons() { }
    public static boolean contains(int id) { return id >= FIRST_ID && id <= LAST_ID; }
    public static int tier(int id) {
        if (!contains(id)) throw new IllegalArgumentException("Not a gem dragon: " + id);
        return id - FIRST_ID;
    }
    public static int fireMaxHit(int id) { return 45 + tier(id) * 3; }

    /** One independent guaranteed roll; rank = gem tier + quantity rank. */
    public static int guaranteedGemWeightTotal(int id) {
        int dragonTier = tier(id), total = 0;
        for (int gemTier = 0; gemTier <= Math.min(dragonTier, 3); gemTier++) {
            for (int quantityRank = 0; quantityRank < QUANTITIES.length; quantityRank++) {
                total += RANK_WEIGHTS[gemTier + quantityRank];
            }
        }
        // Dragonstone is a separate final rank: no multiples and never on lower dragons.
        return total + (dragonTier == 4 ? 1 : 0);
    }

    public static Item rollGuaranteedGem(int id, GameRandom random) {
        if (!contains(id)) return null;
        int dragonTier = tier(id);
        int ticket = random.nextInt(guaranteedGemWeightTotal(id));
        for (int gemTier = 0; gemTier <= Math.min(dragonTier, 3); gemTier++) {
            for (int quantityRank = 0; quantityRank < QUANTITIES.length; quantityRank++) {
                int weight = RANK_WEIGHTS[gemTier + quantityRank];
                if (ticket < weight) {
                    int quantity = QUANTITIES[quantityRank];
                    return new Item(GEMS[gemTier], quantity, quantity > 1);
                }
                ticket -= weight;
            }
        }
        if (dragonTier == 4 && ticket == 0) {
            return new Item(ItemId.UNCUT_DRAGONSTONE.id(), 1);
        }
        throw new IllegalStateException("Guaranteed gem ticket escaped its table: " + id);
    }

    /** Ordinary crafting materials remain separate from the guaranteed gem roll. */
    public static DropTable craftingTable(int id) {
        int tier = tier(id);
        DropTable table = new DropTable("Gem dragon crafting (" + id + ")");
        table.addItemDrop(ItemId.GOLD_BAR.id(), 2 + tier, 28, true);
        table.addItemDrop(ItemId.GOLD_RING.id(), 1, 8);
        table.addItemDrop(RINGS[tier], 1, 8);
        table.addEmptyDrop(84);
        return table;
    }

    public static void register(Map<Integer,DropTable> tables) {
        for (int id=FIRST_ID; id<=LAST_ID; id++) tables.put(id, craftingTable(id));
    }
}
