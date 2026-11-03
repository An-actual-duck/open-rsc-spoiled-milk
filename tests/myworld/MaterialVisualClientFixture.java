package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.ItemVisualAppearances;
import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import orsc.graphics.two.MudClientGraphics;
import orsc.graphics.two.SpriteArchive.Entry;
import orsc.graphics.two.SpriteArchive.Frame;

/** Exercises actual packaged sprites and preserves native frame geometry. */
public final class MaterialVisualClientFixture {
    public static void main(String[] args) throws Exception {
        Config.S_WANT_CUSTOM_SPRITES = true;
        Config.F_CACHE_DIR = args[0] + "/Client_Base/Cache";
        EntityHandler.load(true);
        check(EntityHandler.animationCount() == 1170, "stable catalog length");
        for (ItemVisualAppearances.Mapping mapping : ItemVisualAppearances.MAPPINGS) {
            AnimationDef animation = EntityHandler.getAnimationDef(mapping.appearanceId - 1);
            check(mapping.family.equals(animation.getName()), "correct family " + mapping.itemId);
            check(mapping.mask == animation.getCharColour(), "correct palette " + mapping.itemId);
        }
        for (int i = 0; i < 15; i++)
            check("viscousbattleaxe".equals(EntityHandler.getAnimationDef(1097 + i).getName()), "coating family");
        Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        mudclient client = (mudclient) ((sun.misc.Unsafe) unsafe.get(null)).allocateInstance(mudclient.class);
        MudClientGraphics graphics = new MudClientGraphics(160,160,5000);
        client.setSurface(graphics);
        check(graphics.fillSpriteTree(), "native archive");
        Map<String, Entry> equipment = graphics.spriteTree.get("equipment");
        Map<String, Entry> originals = new HashMap<>();
        for (String name : new String[]{"staff", "elementalstaff"}) originals.put(name, equipment.get(name).clone());
        Field loader = mudclient.class.getDeclaredField("externalAssetLoader");
        loader.setAccessible(true);
        loader.set(client, new ClientExternalAssetLoader(Paths.get("/nonexistent/material-packaged-only"), mudclient.class));
        Method load = mudclient.class.getDeclaredMethod("loadExternalEquipmentSprites");
        load.setAccessible(true);
        load.invoke(client);
        for (String name : originals.keySet()) {
            Entry original = originals.get(name), masked = equipment.get("material" + name);
            check(original.equals(equipment.get(name)), "native original unchanged");
            check(masked.getType() == original.getType() && masked.getLayer() == original.getLayer(), "native type/layer");
            check(original.getFrames().length == masked.getFrames().length, "all native frames");
            int changed = 0;
            for (int index = 0; index < original.getFrames().length; index++) {
                Frame a = original.getFrames()[index], b = masked.getFrames()[index];
                check(a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight()
                    && a.getUseShift() == b.getUseShift() && a.getOffsetX() == b.getOffsetX()
                    && a.getOffsetY() == b.getOffsetY() && a.getBoundWidth() == b.getBoundWidth()
                    && a.getBoundHeight() == b.getBoundHeight(), "native hand anchors");
                check(Arrays.equals(b.getPixels(), b.getSprite().getPixels()), "live sprite pixel binding");
                for (int pixel = 0; pixel < a.getPixels().length; pixel++) {
                    int before = a.getPixels()[pixel], after = b.getPixels()[pixel];
                    if (before == 0x7d2b13 || before == 0xc35533) {
                        check(before != after, "wood converted"); changed++;
                    } else check(before == after, "orb/transparency retained");
                }
            }
            check(changed > 5000, "all wood frames converted");
        }
        int staves = 0, icons = 0;
        for (int id = 0; id < EntityHandler.itemCount(); id++) {
            ItemDef item = EntityHandler.getItemDef(id);
            if (!item.getSpriteLocation().startsWith("external-png:staff-")) continue;
            check(client.spriteSelect(item) != null, "packaged staff icon " + id); icons++;
        }
        for (int i = 0; i < EntityHandler.animationCount(); i++) {
            AnimationDef animation = EntityHandler.getAnimationDef(i);
            if (!animation.getName().startsWith("material")) continue;
            for (int frame = 0; frame < 18; frame++)
                check(client.spriteSelect(animation,frame) != null, "actual equipped staff frame");
            staves++;
        }
        check(staves == 180 && icons == 180, "all wood/enchantment combinations");
        check("external-png:moonsilver-bar@39x16".equals(EntityHandler.getItemDef(3430).getSpriteLocation()), "bar native size");
        System.out.println("PASS 328 paired families/palettes; 180 packaged staff icons and equipped variants, all 18 frames, native originals/hand anchors retained");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
