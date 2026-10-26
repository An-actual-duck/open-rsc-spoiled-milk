package orsc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Visual tile grouping; ranks count strictly smaller server indexes, including ties. */
final class NpcOverlapLayout {
    static void calculate(ORSCharacter[] npcs, int count, int tileSize, int[] ranks, int[] counts) {
        Arrays.fill(ranks, 0, count, 0);
        Arrays.fill(counts, 0, count, 0);
        // Measurements put sparse small views below the grouping crossover.
        if (count <= 32) {
            for (int i = 0; i < count; i++) if (npcs[i] != null) {
                int x = Math.floorDiv(npcs[i].currentX - 64, tileSize);
                int z = Math.floorDiv(npcs[i].currentZ - 64, tileSize);
                for (int j = 0; j < count; j++) if (npcs[j] != null
                        && x == Math.floorDiv(npcs[j].currentX - 64, tileSize)
                        && z == Math.floorDiv(npcs[j].currentZ - 64, tileSize)) {
                    counts[i]++;
                    if (npcs[j].serverIndex < npcs[i].serverIndex) ranks[i]++;
                }
            }
            return;
        }
        Map<Long, ArrayList<Integer>> groups = new HashMap<>();
        for (int i = 0; i < count; i++) {
            ORSCharacter npc = npcs[i];
            if (npc == null) continue;
            int x = Math.floorDiv(npc.currentX - 64, tileSize);
            int z = Math.floorDiv(npc.currentZ - 64, tileSize);
            long tile = ((long) x << 32) | (z & 0xffffffffL);
            groups.computeIfAbsent(tile, ignored -> new ArrayList<>(1)).add(i);
        }
        for (ArrayList<Integer> group : groups.values()) {
            group.sort((a, b) -> Integer.compare(npcs[a].serverIndex, npcs[b].serverIndex));
            int rank = 0;
            for (int i = 0; i < group.size(); i++) {
                int slot = group.get(i);
                if (i > 0 && npcs[slot].serverIndex != npcs[group.get(i - 1)].serverIndex) rank = i;
                ranks[slot] = rank;
                counts[slot] = group.size();
            }
        }
    }
}
