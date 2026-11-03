package com.openrsc.client.entityhandling;

import com.openrsc.client.entityhandling.defs.ItemDef;
import java.util.List;

/** Shared mask bases keep wood, metal and enchantment colours independent. */
final class MaterialItemVisuals {
    private static final int[] CROSSBOWS = {60,59,2169,2170,2171,2172,2173,2174,2175,2176};
    private static final int[] BOLTS = {190,2178,2180,2182,2184,2186,2188,2190,2192,2194};
    private static final int[] POISON_BOLTS = {592,2179,2181,2183,2185,2187,2189,2191,2193,2195};
    private static final int[] LOGS = {2111,2112,2113,2114};
    private static final int[] LOG_WOODS = {1,4,7,9};
    // Native log body colours; decorative end-grain colours are not used as wood.
    private static final int[] CROSSBOW_WOODS = {
        0xB87030,0xCCAA33,0xBD7F60,0x9C8253,0xC78C4A,
        0xA82000,0x824222,0x333333,0xAC8F61,0xCC2222
    };

    private MaterialItemVisuals() { }

    static void apply(List<ItemDef> items, int[] staffs, int[][] enchanted,
                      int[] woods, int[] runes) {
        for (int wood = 0; wood < staffs.length; wood++) {
            replace(items, staffs[wood], "staff-wood-base@36x28", woods[wood], 0);
            for (int rune = 0; rune < enchanted.length; rune++) {
                replace(items, enchanted[rune][wood], "staff-orb-base@37x29", runes[rune], woods[wood]);
            }
            replace(items, CROSSBOWS[wood], "crossbow-wood-base@40x27", 0, CROSSBOW_WOODS[wood]);
        }
        for (int i = 0; i < LOGS.length; i++) {
            replace(items, LOGS[i], "log-wood-base@44x30", woods[LOG_WOODS[i]], 0);
        }
        for (int metal = 0; metal < BOLTS.length; metal++) {
            int colour = items.get(BOLTS[metal]).getPictureMask();
            replace(items, BOLTS[metal], "metal-bolts-base@25x22", colour, 0);
            replace(items, POISON_BOLTS[metal], "metal-bolts-poison-base@25x22", colour, 0);
        }
    }

    private static void replace(List<ItemDef> items, int id, String image, int mask, int blueMask) {
        ItemDef item = items.get(id);
        String commands = item.command == null ? "" : String.join(",", item.command);
        items.set(id, new ItemDef(item.getName(), item.getDescription(), commands, item.basePrice,
            -1, "external-png:" + image, item.stackable, item.wieldable, item.wearableID,
            mask, blueMask, item.membersItem, item.untradeable, item.noteable, id));
    }
}
