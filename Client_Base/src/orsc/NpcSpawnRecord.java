package orsc;

import orsc.buffers.RSBuffer_Bits;

/** One version-10053 NPC addition; local counts and definition IDs are independent. */
public final class NpcSpawnRecord {
    public static final int BITS = NpcCapacityContract.INDEX_BITS + 8 + 8 + 4
        + NpcCapacityContract.DEFINITION_BITS;
    public final int index, offsetX, offsetZ, sprite, definition;

    private NpcSpawnRecord(int index, int x, int z, int sprite, int definition) {
        this.index = index;
        this.offsetX = x;
        this.offsetZ = z;
        this.sprite = sprite;
        this.definition = definition;
    }

    public static NpcSpawnRecord read(RSBuffer_Bits buffer, int packetBytes) {
        if (packetBytes < 0 || (long) buffer.getBitHead() + BITS > (long) packetBytes * 8) {
            throw new IllegalArgumentException("Truncated NPC addition");
        }
        int index = buffer.getBitMask(NpcCapacityContract.INDEX_BITS);
        int x = buffer.getBitMask(8), z = buffer.getBitMask(8);
        int sprite = buffer.getBitMask(4);
        int definition = buffer.getBitMask(NpcCapacityContract.DEFINITION_BITS);
        if (index >= NpcCapacityContract.TARGET_CAPACITY) {
            throw new IllegalArgumentException("NPC index exceeds supported registry: " + index);
        }
        return new NpcSpawnRecord(index, x >= 128 ? x - 256 : x,
            z >= 128 ? z - 256 : z, sprite, definition);
    }

    public static void requirePadding(RSBuffer_Bits buffer, int packetBytes) {
        int remaining = packetBytes * 8 - buffer.getBitHead();
        if (remaining < 0 || remaining > 7
                || remaining > 0 && buffer.getBitMask(remaining) != 0) {
            throw new IllegalArgumentException("Malformed NPC addition tail");
        }
    }
}
