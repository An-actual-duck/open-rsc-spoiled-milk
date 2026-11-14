package com.openrsc.server.content.coatings;

import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.entity.player.Player;

/** Reviewed roster: config/scimitar-coatings.json; coated variants retain all base equipment stats. */
public final class ScimitarCoatingCatalog {
    public static final int COATING = 3447;
    private static final int[][] ROWS = {
        {1999,3448,1,1},
        {2010,3449,2,1},
        {82,3450,3,1},
        {83,3451,4,1},
        {84,3452,5,2},
        {427,3453,5,2},
        {2155,3454,5,2},
        {3117,3455,5,2},
        {85,3456,6,2},
        {2021,3457,7,2},
        {86,3458,8,2},
        {2032,3459,9,3},
        {398,3460,10,3},
        {3265,3461,12,3},
    };
    private ScimitarCoatingCatalog() { }
    public static int[] coatedIds() { int[] ids=new int[ROWS.length]; for(int n=0;n<ids.length;n++)ids[n]=ROWS[n][1]; return ids; }
    public static int coated(int id) { for(int[] r:ROWS) if(r[0]==id)return r[1]; return -1; }
    public static int base(int id) { for(int[] r:ROWS) if(r[1]==id)return r[0]; return id; }
    public static boolean isCoated(int id) { return base(id)!=id; }
    public static int tier(int id) { for(int[] r:ROWS) if(r[0]==id||r[1]==id)return r[2]; return 0; }
    public static int severity(int id) { for(int[] r:ROWS) if(r[1]==id)return r[3]; return 0; }
    public static int equipped(Player p) {
        if(p.getConfig().WANT_EQUIPMENT_TAB) {
            Item item=p.getCarriedItems().getEquipment().get(Equipment.EquipmentSlot.SLOT_MAINHAND.getIndex());
            return item==null?-1:item.getCatalogId();
        }
        for(Item item:p.getCarriedItems().getInventory().getItems())
            if(item.isWielded()&&tier(item.getCatalogId())>0)return item.getCatalogId();
        return -1;
    }
}
