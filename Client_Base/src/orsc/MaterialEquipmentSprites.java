package orsc;

import java.util.Map;
import orsc.graphics.two.SpriteArchive.Entry;
import orsc.graphics.two.SpriteArchive.Frame;

/** Colour-mask copies of the native staff frames; geometry and originals stay intact. */
public final class MaterialEquipmentSprites {
    private MaterialEquipmentSprites() { }

    public static void install(Map<String, Entry> equipment) {
        if (equipment == null) return;
        install(equipment, "staff", "materialstaff", false);
        install(equipment, "elementalstaff", "materialelementalstaff", true);
    }

    private static void install(Map<String, Entry> equipment, String source,
                                String destination, boolean enchanted) {
        Entry original = equipment.get(source);
        if (original == null) throw new IllegalStateException("Missing native staff: " + source);
        Entry masked = original.clone();
        masked.changeID(destination);
        for (Frame frame : masked.getFrames()) {
            int[] pixels = frame.getPixels();
            for (int i = 0; i < pixels.length; i++) {
                // These are the two native wood tones. Orb pixels remain unchanged.
                if (pixels[i] == 0x7d2b13 || pixels[i] == 0xc35533) {
                    int shade = pixels[i] == 0x7d2b13 ? 208 : 254;
                    pixels[i] = (shade << 16) | (shade << 8) | (enchanted ? 255 : shade);
                }
            }
        }
        equipment.put(destination, masked);
    }
}
