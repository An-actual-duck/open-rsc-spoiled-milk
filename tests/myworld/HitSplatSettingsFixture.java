package orsc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import orsc.graphics.gui.Panel;
import orsc.graphics.two.MudClientGraphics;

/** Actual Options row and click dispatch, with settings I/O confined to a disposable working directory. */
public final class HitSplatSettingsFixture {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static Object field(Object instance, String name) throws Exception {
        Field f = instance.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(instance);
    }
    private static void set(Object instance, String name, Object value) throws Exception {
        Field f = instance.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(instance, value);
    }
    private static Object invoke(Object instance, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = mudclient.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(instance, args);
    }
    private static Properties disk() throws Exception {
        return (Properties) invoke(null, "loadClientSettings", new Class<?>[0]);
    }
    private static int optionsRow(mudclient mc, Panel panel, int control) throws Exception {
        invoke(mc, "drawGeneralSettingsOptions", new Class<?>[] {int.class, short.class, int.class, int.class},
            10, (short) 196, 10, 30);
        String[][] labels = (String[][]) field(panel, "controlListEntryString");
        int found = -1, count = 0;
        for (int i = 0; i < panel.controlListCurrentSize[control]; i++) {
            if (panel.getControlSelectedListInt(control, i) == HitSplatDisplaySettings.OPTION_ACTION) {
                found = i; count++;
                check(HitSplatDisplaySettings.optionLabel().equals(labels[control][i]), "Options label reflects current style");
                check(mc.getSurface().stringWidth(1, labels[control][i]) <= 180, "label fits normal Options width");
            }
        }
        check(count == 1, "exactly one hit-number option in General");
        return found;
    }
    private static int[] pixels(FloatingHitSplatRenderer renderer, ORSCharacter owner, MudClientGraphics surface) {
        Arrays.fill(surface.pixelData, 0x222B2C);
        renderer.beginFrame(); renderer.collect(owner, 100, 120, 0, surface); renderer.draw(surface);
        return surface.pixelData.clone();
    }
    public static void main(String[] args) throws Exception {
        check(HitSplatDisplaySettings.getStyle() == FloatingHitSplatRenderer.Style.BACKGROUND, "fresh-process background default");
        if ("reload".equals(args[1])) {
            HitSplatDisplaySettings.loadFromClientSettings(disk());
            check(new FloatingHitSplatRenderer().getStyle() == FloatingHitSplatRenderer.Style.TEXT, "new process reloads text preference");
            check("kept".equals(disk().getProperty("unrelated")), "reload preserves unrelated setting");
            System.out.println("PASS hit-number settings fresh-JVM reload: " + checks);
            return;
        }
        Method fonts = FloatingHitSplatFixture.class.getDeclaredMethod("fonts", java.nio.file.Path.class);
        fonts.setAccessible(true); fonts.invoke(null, Paths.get(args[0]));
        Properties invalid = new Properties(); invalid.setProperty(HitSplatDisplaySettings.PROPERTY_KEY, "unknown");
        HitSplatDisplaySettings.loadFromClientSettings(invalid);
        check(HitSplatDisplaySettings.getStyle() == FloatingHitSplatRenderer.Style.BACKGROUND, "invalid setting uses background");
        Properties text = new Properties(); text.setProperty(HitSplatDisplaySettings.PROPERTY_KEY, " TeXt ");
        HitSplatDisplaySettings.loadFromClientSettings(text);
        check(HitSplatDisplaySettings.getStyle() == FloatingHitSplatRenderer.Style.TEXT, "case-insensitive saved text");
        HitSplatDisplaySettings.loadFromClientSettings(new Properties());
        check(HitSplatDisplaySettings.getStyle() == FloatingHitSplatRenderer.Style.BACKGROUND, "older settings file uses default");
        HitSplatDisplaySettings.setStyle(null);
        HitSplatDisplaySettings.loadFromClientSettings(null);
        check(HitSplatDisplaySettings.getStyle() == FloatingHitSplatRenderer.Style.BACKGROUND, "null fallback");

        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        mudclient mc = (mudclient) unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, mudclient.class);
        Config.C_CUSTOM_UI = false;
        MudClientGraphics surface = new MudClientGraphics(400, 320, 1); mc.setSurface(surface);
        Panel panel = new Panel(surface, 1);
        int control = panel.addScrollingList3(10, 30, 195, 184, 100, 1, true, 1, 2);
        set(mc, "panelSettings", panel); set(mc, "controlSettingPanel", control);
        set(mc, "settingTab", 1); set(mc, "gameHeight", 320);
        set(mc, "mouseX", -1000); set(mc, "mouseY", -1000);
        int row = optionsRow(mc, panel, control);
        ((int[]) field(panel, "controlSelectedListIndex"))[control] = row;

        Files.write(Paths.get("clientSettings.conf"), "unrelated=kept\n".getBytes("UTF-8"));
        FloatingHitSplatRenderer renderer = new FloatingHitSplatRenderer();
        ORSCharacter owner = new ORSCharacter(); owner.floatingHitSplats.offer(0, 28, 0);
        int[] background = pixels(renderer, owner, surface);
        for (int button : new int[] {0, 2}) {
            set(mc, "mouseButtonClick", button);
            invoke(mc, "handleGeneralSettingsClicks", new Class<?>[] {short.class, int.class, int.class}, (short) 196, 10, 0);
            check(renderer.getStyle() == FloatingHitSplatRenderer.Style.BACKGROUND, "hover/right click leaves style unchanged");
        }
        for (int i = 0; i < 5; i++) {
            Config.C_CUSTOM_UI = i % 2 != 0;
            set(mc, "mouseButtonClick", 1);
            invoke(mc, "handleGeneralSettingsClicks", new Class<?>[] {short.class, int.class, int.class}, (short) 196, 10, 0);
            boolean isText = i % 2 == 0;
            check(renderer.getStyle() == (isText ? FloatingHitSplatRenderer.Style.TEXT : FloatingHitSplatRenderer.Style.BACKGROUND),
                "existing renderer follows Options click immediately " + i);
            check((isText ? "text" : "background").equals(disk().getProperty(HitSplatDisplaySettings.PROPERTY_KEY)), "style saved to disk " + i);
            check("kept".equals(disk().getProperty("unrelated")), "read-modify-save preserves unrelated settings " + i);
            int[] changed = pixels(renderer, owner, surface);
            check(isText != Arrays.equals(background, changed), "active number changes appearance without a new event " + i);
            check(owner.floatingHitSplats.advance(0).size() == 1 && owner.floatingHitSplats.pendingCount() == 0,
                "switch does not clear or duplicate active events " + i);
            row = optionsRow(mc, panel, control);
            ((int[]) field(panel, "controlSelectedListIndex"))[control] = row;
        }
        check(field(mc, "packetHandler") == null, "style toggle needs no server connection");
        System.out.println("PASS hit-number Options / immediate switching / persistence: " + checks);
    }
}
