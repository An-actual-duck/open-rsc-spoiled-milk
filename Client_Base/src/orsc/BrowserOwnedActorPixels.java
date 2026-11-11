package orsc;

import java.util.Map;
import java.util.IdentityHashMap;
import orsc.graphics.three.Renderer3DSettings;
import orsc.graphics.two.SpriteArchive.Entry;
import orsc.graphics.two.SpriteArchive.Frame;

/** Private publication policy: native/software defaults never copy or trust actor data. */
public final class BrowserOwnedActorPixels {
    private BrowserOwnedActorPixels() { }
    public static boolean isEnabled() {
        return Boolean.getBoolean("spoiledmilk.browserOwnedActorPixels")
            && Renderer3DSettings.isBrowserPresentationAvailable();
    }
    public static void publish(Map<String, Map<String, Entry>> tree) {
        if (!isEnabled() || tree == null) return;
        IdentityHashMap<Frame, Frame> copies = new IdentityHashMap<Frame, Frame>();
        // Type, not a mutable name, defines the audited decoded actor families.
        for (Map<String, Entry> space : tree.values()) {
            if (space == null) continue;
            for (Entry entry : space.values()) {
                if (entry == null || entry.getClass() != Entry.class || !actorType(entry.getType())) continue;
                Frame[] frames = entry.getFrames();
                for (int i = 0; i < frames.length; i++) {
                    if (frames[i] != null) {
                        Frame source = frames[i];
                        Frame copy = copies.get(source);
                        if (copy == null) { copy = source.copyForOwnedActor(); copies.put(source, copy); }
                        frames[i] = copy;
                    }
                }
            }
        }
    }
    private static boolean actorType(Entry.TYPE type) {
        return type == Entry.TYPE.PLAYER_PART || type == Entry.TYPE.NPC
            || type == Entry.TYPE.PLAYER_EQUIPPABLE_HASCOMBAT
            || type == Entry.TYPE.PLAYER_EQUIPPABLE_NOCOMBAT;
    }
}
