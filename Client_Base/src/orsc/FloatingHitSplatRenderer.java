package orsc;

import com.openrsc.client.model.Sprite;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.two.MudClientGraphics;

/** Shared player/NPC overlay pass, after lifebars. Uses native font pixels in alpha sprites. */
public final class FloatingHitSplatRenderer {
    public enum Style { TEXT, BACKGROUND }
    public static final int MAX_HORIZONTAL_SHIFT = 24, HEAD_GAP = 8, MAX_FRAME_VALUES = 4096;
    private final List<Placement> frame = new ArrayList<Placement>();
    private final IdentityHashMap<ORSCharacter, Boolean> collected = new IdentityHashMap<ORSCharacter, Boolean>();
    private final Map<String, Sprite> sprites = new LinkedHashMap<String, Sprite>(128, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, Sprite> eldest) { return size() > 256; }
    };
    private final Style fixedStyle;
    public FloatingHitSplatRenderer() {
        this.fixedStyle = null;
    }
    public FloatingHitSplatRenderer(Style style) { this.fixedStyle = style; }
    public Style getStyle() { return fixedStyle == null ? HitSplatDisplaySettings.getStyle() : fixedStyle; }
    public void beginFrame() { frame.clear(); collected.clear(); }

    public void collect(ORSCharacter owner, int centerX, int headY, long now, MudClientGraphics surface) {
        if (collected.put(owner, Boolean.TRUE) != null) return;
        for (FloatingHitSplatState.Entry e : owner.floatingHitSplats.advance(now)) {
            if (frame.size() >= MAX_FRAME_VALUES) break;
            Sprite sprite = sprite(e.type, e.amount, surface);
            int y = headY - HEAD_GAP - sprite.getHeight() - (int) Math.round(e.rise);
            frame.add(new Placement(owner, e, centerX, y, sprite, e.opacity(now)));
        }
    }

    public List<Placement> layout(int screenWidth, int screenHeight) {
        frame.sort(Comparator.comparingInt((Placement p) -> p.anchorX)
            .thenComparingInt(p -> p.owner.serverIndex).thenComparingInt(p -> p.owner.npcId)
            .thenComparingLong(p -> p.entry.sequence));
        List<Placement> placed = new ArrayList<Placement>();
        for (Placement p : frame) {
            int desired = p.anchorX - p.width / 2;
            p.x = desired;
            int bestCollisions = Integer.MAX_VALUE;
            // Prefer nearby horizontal space; never detach far from the owner or stack above the height cap.
            for (int delta = 0; delta <= MAX_HORIZONTAL_SHIFT; delta += 2) {
                for (int sign : new int[] {1, -1}) {
                    int candidate = desired + sign * delta;
                    if (candidate < 0 || candidate + p.width > screenWidth) continue;
                    int collisions = 0;
                    for (Placement q : placed) {
                        if (candidate < q.x + q.width + 2 && candidate + p.width + 2 > q.x
                            && p.y < q.y + q.height + 2 && p.y + p.height + 2 > q.y) collisions++;
                    }
                    if (collisions < bestCollisions) { p.x = candidate; bestCollisions = collisions; }
                    if (collisions == 0) break;
                }
                if (bestCollisions == 0) break;
            }
            // Clip at the viewport rather than moving numbers down into lifebars.
            if (p.y + p.height > 0 && p.y < screenHeight) placed.add(p);
        }
        return placed;
    }

    public void draw(MudClientGraphics surface) {
        Renderer2DFrame.Phase previousPhase = surface.getRenderer2DPhase();
        surface.setRenderer2DPhase(Renderer2DFrame.Phase.WORLD_OVERLAY);
        try {
            for (Placement p : layout(surface.width2, surface.height2)) {
                surface.drawSprite(p.sprite, p.x, p.y, p.width, p.height, 5924, p.opacity);
            }
        } finally { surface.setRenderer2DPhase(previousPhase); }
    }

    private Sprite sprite(int type, int amount, MudClientGraphics surface) {
        Style style = getStyle();
        String digits = Integer.toString(amount);
        String key = style + ":" + type + ":" + digits;
        Sprite cached = sprites.get(key);
        if (cached != null) return cached;
        if (style == Style.BACKGROUND) {
            cached = backgroundSprite(type, digits, surface);
        } else {
            // Keep the accepted text-only placement and appearance unchanged.
            int width = surface.stringWidth(0, digits) + 4;
            int height = surface.fontHeight(0) + 2;
            MudClientGraphics scratch = new MudClientGraphics(width, height, 1);
            scratch.beginSoftwareOwnedRenderer2DFrame();
            scratch.drawShadowText(digits, 2, height - 3, textColor(type), 0, false);
            cached = new Sprite(scratch.pixelData, width, height);
        }
        sprites.put(key, cached);
        return cached;
    }


    private Sprite backgroundSprite(int type, String digits, MudClientGraphics surface) {
        // Advance width includes bearings/trailing space, and line height includes baseline
        // space. Measure the native foreground raster instead so 1, 28 and 100 all
        // have identical visible padding, without clipping their actual glyphs.
        int measureWidth = surface.stringWidth(0, digits) + 12;
        int measureHeight = surface.fontHeight(0) + 12;
        int originX = 6, baseline = measureHeight - 6;
        MudClientGraphics measure = new MudClientGraphics(measureWidth, measureHeight, 1);
        measure.beginSoftwareOwnedRenderer2DFrame();
        measure.drawString(digits, originX, baseline, 0xFFFFFF, 0);
        int left = measureWidth, top = measureHeight, right = -1, bottom = -1;
        for (int y = 0; y < measureHeight; y++) {
            for (int x = 0; x < measureWidth; x++) {
                if (measure.pixelData[y * measureWidth + x] != 0) {
                    left = Math.min(left, x); right = Math.max(right, x);
                    top = Math.min(top, y); bottom = Math.max(bottom, y);
                }
            }
        }
        if (right < left || bottom < top) throw new IllegalStateException("Missing native hit-number glyphs");
        int padding = 2;
        int width = right - left + 1 + padding * 2;
        int height = bottom - top + 1 + padding * 2;
        MudClientGraphics scratch = new MudClientGraphics(width, height, 1);
        scratch.beginSoftwareOwnedRenderer2DFrame();
        scratch.drawBoxAlpha(0, 0, width, height, backgroundColor(type), 256);
        scratch.drawShadowText(digits, originX + padding - left, baseline + padding - top, 0xFFFFFF, 0, false);
        return new Sprite(scratch.pixelData, width, height);
    }

    public static int textColor(int type) {
        switch (type) {
            case ORSCharacter.HIT_SPLAT_STANDARD: return 0xFF6860;
            case ORSCharacter.HIT_SPLAT_POISON: return 0x65DF80;
            case ORSCharacter.HIT_SPLAT_HEAL: return 0x69B8FF;
            case ORSCharacter.HIT_SPLAT_SUMMON: return 0xFFE263;
            case ORSCharacter.HIT_SPLAT_ARMOR_PROC:
            case ORSCharacter.HIT_SPLAT_RECOIL: return 0xCD96FF;
            case ORSCharacter.HIT_SPLAT_BURN: return 0xFFB575;
            default: return 0xFF6860;
        }
    }
    public static int backgroundColor(int type) {
        switch (type) {
            case ORSCharacter.HIT_SPLAT_STANDARD: return 0xA62B28;
            case ORSCharacter.HIT_SPLAT_POISON: return 0x256938;
            case ORSCharacter.HIT_SPLAT_HEAL: return 0x245B9B;
            case ORSCharacter.HIT_SPLAT_SUMMON: return 0x876611;
            case ORSCharacter.HIT_SPLAT_ARMOR_PROC:
            case ORSCharacter.HIT_SPLAT_RECOIL: return 0x663897;
            case ORSCharacter.HIT_SPLAT_BURN: return 0x965126;
            default: return 0xA62B28;
        }
    }

    public static final class Placement {
        public final ORSCharacter owner;
        public final FloatingHitSplatState.Entry entry;
        public final int anchorX, y, width, height, opacity;
        public int x;
        private final Sprite sprite;
        private Placement(ORSCharacter owner, FloatingHitSplatState.Entry entry, int anchorX, int y, Sprite sprite, int opacity) {
            this.owner = owner; this.entry = entry; this.anchorX = anchorX; this.y = y; this.sprite = sprite;
            this.width = sprite.getWidth(); this.height = sprite.getHeight(); this.opacity = opacity;
        }
    }
}
