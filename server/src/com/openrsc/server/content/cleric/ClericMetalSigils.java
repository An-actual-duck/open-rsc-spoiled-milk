package com.openrsc.server.content.cleric;

/** Game-owned metal resources; identities are independent of legacy ItemId ordinals. */
public final class ClericMetalSigils {
    public static final int MOONSILVER_BAR = 3430;
    public static final int BLANK_SILVER_SIGIL = 3431;
    public static final int BLANK_MOONSILVER_SIGIL = 3432;
    public static final int SIGIL_MOULD = 3433;
    public static final int BLANKS_PER_BAR = 3;
    public static final int MOONSILVER_SMITHING_LEVEL = 38;
    private ClericMetalSigils() {}

    public static int barFor(ClericSigilMaterial material) {
        if (material == ClericSigilMaterial.SILVER) return 384;
        if (material == ClericSigilMaterial.MOONSILVER) return MOONSILVER_BAR;
        throw new IllegalArgumentException("Stone sigils are carved directly, not moulded");
    }
}
