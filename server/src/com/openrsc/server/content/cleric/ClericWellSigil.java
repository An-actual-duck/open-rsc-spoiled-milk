package com.openrsc.server.content.cleric;

/** Purpose resources, separate from god alignment and the legacy spellbook's stable neutral identities. */
public enum ClericWellSigil {
    STONE_GRACE(ClericSigilMaterial.STONE, Purpose.GRACE, 3299, 3300),
    SILVER_GRACE(ClericSigilMaterial.SILVER, Purpose.GRACE, 3307, 3308),
    STONE_BLIGHT(ClericSigilMaterial.STONE, Purpose.BLIGHT, 3426, 3427),
    SILVER_BLIGHT(ClericSigilMaterial.SILVER, Purpose.BLIGHT, 3428, 3429);

    public enum Purpose {
        GRACE("Grace", 1355, "a Divine wellspring"),
        BLIGHT("Blight", 1354, "an Ichor well");
        private final String name;
        private final int wellId;
        private final String destination;
        Purpose(String name, int wellId, String destination) { this.name=name;this.wellId=wellId;this.destination=destination; }
        public String getName() { return name; }
        public int getWellId() { return wellId; }
        public String getDestination() { return destination; }
        public static Purpose forWell(int id) { for(Purpose purpose:values())if(purpose.wellId==id)return purpose;return null; }
    }
    private final ClericSigilMaterial material;
    private final Purpose purpose;
    private final int rawId, infusedId;
    ClericWellSigil(ClericSigilMaterial material, Purpose purpose, int rawId, int infusedId) {
        this.material=material;this.purpose=purpose;this.rawId=rawId;this.infusedId=infusedId;
    }
    public ClericSigilMaterial getMaterial() { return material; }
    public Purpose getPurpose() { return purpose; }
    public int getRawItemId() { return rawId; }
    public int getInfusedItemId() { return infusedId; }
    public boolean acceptsWell(int id) { return purpose.getWellId()==id; }
    public static ClericWellSigil fromRaw(int id) { for(ClericWellSigil sigil:values())if(sigil.rawId==id)return sigil;return null; }
    public static ClericWellSigil fromAny(int id) { for(ClericWellSigil sigil:values())if(sigil.rawId==id||sigil.infusedId==id)return sigil;return null; }
}
