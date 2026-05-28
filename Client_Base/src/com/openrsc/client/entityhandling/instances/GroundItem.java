package com.openrsc.client.entityhandling.instances;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.model.Sprite;

import java.util.Comparator;

public class GroundItem {
    private int id;
    private int x;
    private int y;
    private int width;
    private int height;
    private Sprite sprite;
    private ItemDef itemDef;

    public GroundItem(int id, int x, int y, int width, int height, ItemDef itemDef, Sprite sprite) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.itemDef = itemDef;
        this.sprite = sprite;
    }

    public GroundItem(int id, int x, int y, int width, int height) {
        this(id, x, y, width, height, EntityHandler.getItemDef(id), null);
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof GroundItem)) 
        { 
            return false; 
        }

        GroundItem g = (GroundItem)o;
        return this.getName().equals(g.getName()) && this.getX() == g.getX() && this.getY() == g.getY();
    }

    public int getId() {
        return id;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Sprite getSprite() {
        return sprite;
    }

    public void setSprite(Sprite sprite) {
        this.sprite = sprite;
    }

    public ItemDef getItemDef() {
        return itemDef;
    }

    public String getName() {
        return itemDef.getName();
    }

    public static class GroundItemComparator implements Comparator<GroundItem> {
        @Override
        public int compare(GroundItem a, GroundItem b) {
            // Source: https://github.com/15rtrujillo/rscplus/blob/master/src/Game/Renderer.java
            // this is reverse alphabetical order b/c we display them/in reverse order (y-=12 ea item)
            int offset = a.getName().compareToIgnoreCase(b.getName()) * -1;
            if (offset > 0) { // item a is alphabetically before item b
                offset = 10;
            } else if (offset < 0) { // item b is alphabetically before item a
                offset = -10;
                // items have the same name we would like to group items that are on the same tile as well,
                // not just having
                // the same name, so that we can use "last_item" in a useful way
            } else {
                if (a.getX() == b.getX() && a.getY() == b.getY()) {
                    offset = 0; // name is the same and so is location, items are considered equal
                } else {
                    if (a.getX() < b.getX()) {
                        offset = -5;
                    } else {
                        offset = 5;
                    }
                }
            }
            return offset;
        }
    }
}
