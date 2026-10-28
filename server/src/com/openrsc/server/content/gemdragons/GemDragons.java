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
    public static final int[] SCRAP_DENOMINATORS = {1024, 768, 512, 256, 128};
    private static final int[] GEMS = {ItemId.UNCUT_SAPPHIRE.id(), ItemId.UNCUT_EMERALD.id(),
        ItemId.UNCUT_RUBY.id(), ItemId.UNCUT_DIAMOND.id(), ItemId.UNCUT_DRAGONSTONE.id()};
    private static final int[] RINGS = {ItemId.SAPPHIRE_RING.id(), ItemId.EMERALD_RING.id(),
        ItemId.RUBY_RING.id(), ItemId.DIAMOND_RING.id(), ItemId.DRAGONSTONE_RING.id()};
    private static final int[] BOLTS = {ItemId.MITHRIL_BOLTS.id(), ItemId.TITAN_STEEL_BOLTS.id(),
        ItemId.ADAMANTITE_BOLTS.id(), ItemId.ORICHALCUM_BOLTS.id(), ItemId.RUNE_BOLTS.id()};
    private static final int[] ARROWS = {ItemId.MITHRIL_ARROWS.id(), ItemId.TITAN_STEEL_ARROWS.id(),
        ItemId.ADAMANTITE_ARROWS.id(), ItemId.ORICHALCUM_ARROWS.id(), ItemId.RUNE_ARROWS.id()};
    // Actual Runecraft requirements: Chaos22, Nature38, Law46, Death54, Blood70.
    private static final int[] PRIMARY_RUNES = {ItemId.CHAOS_RUNE.id(), ItemId.NATURE_RUNE.id(),
        ItemId.LAW_RUNE.id(), ItemId.DEATH_RUNE.id(), ItemId.BLOOD_RUNE.id()};
    private static final int[] COIN_AMOUNTS = {300, 450, 650, 900, 1200};
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
        // Enhanced ammunition is expensive; use small, steadily rising stacks.
        table.addItemDrop(BOLTS[tier], 2 + tier, 16);
        table.addItemDrop(ARROWS[tier], 1 + tier, 4);
        DropTable runes = new DropTable("Gem dragon runes (" + id + ")");
        if (tier == 0) {
            runes.addItemDrop(PRIMARY_RUNES[0], 10, 16);
        } else {
            runes.addItemDrop(PRIMARY_RUNES[tier - 1], 10 + 5 * tier, 8);
            runes.addItemDrop(PRIMARY_RUNES[tier], 10 + 5 * tier, 8);
        }
        table.addTableDrop(runes, 20);
        table.addItemDrop(ItemId.COINS.id(), COIN_AMOUNTS[tier], 36);
        table.addEmptyDrop(8);
        DropTable scrap = new DropTable("Gem dragon metal scrap (" + id + ")", true);
        scrap.addItemDrop(ItemId.RAW_DRAGON_METAL.id(), 1, 1);
        DropTable root = new DropTable("Gem dragon normal loot (" + id + ")");
        root.addTableDrop(table, SCRAP_DENOMINATORS[tier] - 1);
        root.addTableDrop(scrap, 1);
        return root;
    }

    public static void register(Map<Integer,DropTable> tables) {
        for (int id=FIRST_ID; id<=LAST_ID; id++) tables.put(id, craftingTable(id));
    }
}
