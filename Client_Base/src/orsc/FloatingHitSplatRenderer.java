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
    private final Style style;
    public FloatingHitSplatRenderer() {
        this("background".equalsIgnoreCase(System.getProperty("spoiledmilk.hitSplatStyle", "text"))
            ? Style.BACKGROUND : Style.TEXT);
    }
    public FloatingHitSplatRenderer(Style style) { this.style = style; }
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
        surface.setRenderer2DPhase(Renderer2DFrame.Phase.WORLD_OVERLAY);
        try {
            for (Placement p : layout(surface.width2, surface.height2)) {
                surface.drawSprite(p.sprite, p.x, p.y, p.width, p.height, 5924, p.opacity);
            }
        } finally { surface.setRenderer2DPhase(Renderer2DFrame.Phase.SCENE); }
    }

    private Sprite sprite(int type, int amount, MudClientGraphics surface) {
        String digits = Integer.toString(amount);
        String key = style + ":" + type + ":" + digits;
        Sprite cached = sprites.get(key);
        if (cached != null) return cached;
        int width = surface.stringWidth(0, digits) + 4;
        int height = surface.fontHeight(0) + 2;
        MudClientGraphics scratch = new MudClientGraphics(width, height, 1);
        // This scratch raster owns its pixels even when the main window uses native UI replacement.
        scratch.beginSoftwareOwnedRenderer2DFrame();
        if (style == Style.BACKGROUND) {
            scratch.drawBoxAlpha(0, 0, width, height, backgroundColor(type), 256);
        }
        int textColor = style == Style.BACKGROUND ? 0xFFFFFF : textColor(type);
        scratch.drawShadowText(digits, 2, height - 3, textColor, 0, false);
        cached = new Sprite(scratch.pixelData, width, height);
        sprites.put(key, cached);
        return cached;
    }

    public static int textColor(int type) {
        switch (type) {
            case ORSCharacter.HIT_SPLAT_STANDARD: return 0xFF6860;
            case ORSCharacter.HIT_SPLAT_POISON: return 0x65DF80;
            case ORSCharacter.HIT_SPLAT_HEAL: return 0x69B8FF;
            case ORSCharacter.HIT_SPLAT_SUMMON: return 0xFFE263;
            case ORSCharacter.HIT_SPLAT_RECOIL: return 0xCD96FF;
            default: return 0xFFB575;
        }
    }
    public static int backgroundColor(int type) {
        switch (type) {
            case ORSCharacter.HIT_SPLAT_STANDARD: return 0xA62B28;
            case ORSCharacter.HIT_SPLAT_POISON: return 0x256938;
            case ORSCharacter.HIT_SPLAT_HEAL: return 0x245B9B;
            case ORSCharacter.HIT_SPLAT_SUMMON: return 0x876611;
            case ORSCharacter.HIT_SPLAT_RECOIL: return 0x663897;
            default: return 0x965126;
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
