package com.openrsc.server.content.cleric;

/** One immutable, unnoted item contribution to a flat per-cast cost. */
public final class ClericResourceCost {
    private final int itemId, count;
    public ClericResourceCost(int itemId, int count) {
        if (itemId < 0 || itemId > 65535 || count < 1 || count > 255)
            throw new IllegalArgumentException("Invalid Cleric resource cost");
        this.itemId=itemId; this.count=count;
    }
    public int getItemId(){return itemId;}
    public int getCount(){return count;}
}
