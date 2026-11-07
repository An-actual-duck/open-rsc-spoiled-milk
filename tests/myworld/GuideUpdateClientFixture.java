package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ClericSpellDef;
import com.openrsc.interfaces.misc.ClericSpellGuideCatalog;
import com.openrsc.interfaces.misc.QuestGuideInterface;
import com.openrsc.interfaces.misc.SkillGuideInterface;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.two.MudClientGraphics;

/** Real native-font row bounds, final scroll windows, content identities and filter input. */
public final class GuideUpdateClientFixture {
    private static int checks, windows, tabs;
    private static final String[] SKILLS = {"Melee", "Defense", "Strength", "Hits", "Ranged", "Magic",
        "Worship", "Blessing", "Summoning", "Agility", "Crafting", "Smithing", "Mining", "Woodcutting",
        "Fishing", "Harvest", "Herblaw", "Cooking", "Thieving", "Fletching", "Enchanting", "Magecraft", "Runecraft"};
    private static final String[] FILTERS = {"all", "saradomin", "guthix", "zamorak", "neutral"};

    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> rows(SkillGuideInterface guide) throws Exception {
        return (List<Object>) field(guide, "skillMenuEntries");
    }

    private static int rowHeight(SkillGuideInterface guide, Object entry) throws Exception {
        return (Integer) call(guide, "guideRowHeight",
            new Class<?>[]{Class.forName("com.openrsc.interfaces.misc.SkillMenuEntry")}, entry);
    }

    private static String detail(Object entry) throws Exception {
        Method method = Class.forName("com.openrsc.interfaces.misc.SkillMenuEntry").getDeclaredMethod("getSkillDetail");
        method.setAccessible(true);
        return (String) method.invoke(entry);
    }

    private static void bounds(Renderer2DFrame frame, int left, int top, int width, int bottom, String context) {
        check(frame.getTextCommands().length > 0, "native text emitted: " + context);
        for (Renderer2DFrame.TextCommand command : frame.getTextCommands())
            for (Renderer2DFrame.TextCommand.GlyphCommand glyph : command.getGlyphs()) {
                check(glyph.getX() >= left && glyph.getX() + glyph.getWidth() <= left + width,
                    "horizontal glyph bounds: " + context + " x=" + glyph.getX());
                check(glyph.getY() >= top && glyph.getY() + glyph.getHeight() <= bottom,
                    "vertical glyph bounds: " + context + " y=" + glyph.getY());
            }
    }

    private static int expectedLastStart(SkillGuideInterface guide) throws Exception {
        List<Object> rows = rows(guide);
        int height = 0, visible = 0;
        for (int i = rows.size() - 1; i >= 0; i--) {
            int next = rowHeight(guide, rows.get(i));
            check(next <= 222, "a single guide entry fits a whole page");
            if (height + next > 222) break;
            height += next;
            visible++;
        }
        return Math.max(0, rows.size() - visible);
    }

    private static void renderWindow(mudclient mc, SkillGuideInterface guide, int scroll, String context) throws Exception {
        guide.skillGuide.resetScrollIndex(guide.skillGuideScroll);
        guide.drawSkillItems();
        guide.skillGuide.scrollMethodList(guide.skillGuideScroll, scroll);
        MudClientGraphics surface = (MudClientGraphics) mc.getSurface();
        Arrays.fill(surface.pixelData, 0x22262b);
        surface.beginRenderer2DFrame();
        guide.onRender(surface);
        bounds(surface.consumeRenderer2DFrame(), (mc.getGameWidth() - 430) / 2,
            (mc.getGameHeight() - 320) / 2, 430, (mc.getGameHeight() - 320) / 2 + 320, context);
        windows++;
    }

    private static void ordinaryPages(mudclient mc, Path out) throws Exception {
        SkillGuideInterface guide = new SkillGuideInterface(mc);
        for (String skill : SKILLS) {
            call(mc, "setSkillGuideChosen", new Class<?>[]{String.class}, skill);
            for (int tab = 0; tab < mc.skillGuideChosenTabs.size(); tab++) {
                guide.changeTab(tab);
                String context = skill + "/" + mc.skillGuideChosenTabs.get(tab) + "/" + mc.getGameWidth();
                int count = rows(guide).size();
                for (Object row : rows(guide)) check(rowHeight(guide, row) <= 222,
                    "every populated row fits the body: " + context + " " + detail(row));
                for (int start = 0; start <= count; start++) renderWindow(mc, guide, start, context + "/" + start);
                renderWindow(mc, guide, 10000, context + "/end");
                check(guide.skillGuide.getScrollPosition(guide.skillGuideScroll) == expectedLastStart(guide),
                    "final complete window reaches last entry: " + context);
                if (skill.equals("Herblaw") && tab == 1 || skill.equals("Magic") && tab == 2
                    || skill.equals("Blessing") || skill.equals("Summoning") && tab == 2) {
                    int preview = 0;
                    String target = skill.equals("Herblaw") ? "Viscous coating" : skill.equals("Magic") ? "Thunder"
                        : skill.equals("Summoning") ? "Reverent" : "";
                    for (int i = 0; i < rows(guide).size(); i++)
                        if (!target.isEmpty() && detail(rows(guide).get(i)).contains(target)) { preview = i;break; }
                    renderWindow(mc, guide, preview, context + "/preview");
                    ClericThreeTierClientRenderFixture.png((MudClientGraphics) mc.getSurface(),
                        out.resolve(skill.toLowerCase() + "-" + tab + "-" + mc.getGameWidth() + ".png"));
                }
                tabs++;
            }
        }
        call(mc, "setSkillGuideChosen", new Class<?>[]{String.class}, "Crafting");
        guide.changeTab(0);
        check(rows(guide).size() == 26, "all 26 implemented hide families listed");
        Set<Integer> hideIds = new HashSet<Integer>();
        for (Object row : rows(guide)) {
            Method item = row.getClass().getDeclaredMethod("getItemID");item.setAccessible(true);
            hideIds.add((Integer) item.invoke(row));
            check(detail(row).contains("from level"), "hide families clearly list their starting requirement");
        }
        check(hideIds.containsAll(Arrays.asList(3357, 3394, 3369, 3375, 3381, 3387)), "six missing hide icons restored");
        call(mc, "setSkillGuideChosen", new Class<?>[]{String.class}, "Smithing");
        for (int tab = 1; tab <= 6; tab++) {
            guide.changeTab(tab);
            for (Object row : rows(guide)) if (detail(row).contains(" scythe -")) {
                Method item = row.getClass().getDeclaredMethod("getItemID");item.setAccessible(true);
                int id = (Integer) item.invoke(row);
                check(EntityHandler.getItemDef(id).getName().toLowerCase().contains("scythe"), "scythe row has actual scythe icon");
            }
        }
    }

    private static void clericFilters(mudclient mc, List<ClericSpellDef> definitions, Path out) throws Exception {
        call(mc, "setSkillGuideChosen", new Class<?>[]{String.class}, "Worship");
        SkillGuideInterface guide = new SkillGuideInterface(mc);
        guide.changeTab(mc.skillGuideChosenTabs.indexOf("Spells"));
        Set<Integer> all = new HashSet<Integer>(), filtered = new HashSet<Integer>();
        for (ClericSpellDef d : definitions) all.add(d.getStableCode());
        for (String key : FILTERS) {
            guide.skillGuide.scrollMethodList(guide.skillGuideScroll, 10000);
            guide.setClericGuideFilter(key);
            check(guide.skillGuide.getScrollPosition(guide.skillGuideScroll) == 0, "filter resets old scroll");
            List<ClericSpellGuideCatalog.Entry> pages = ClericSpellGuideCatalog.build(definitions,
                id -> EntityHandler.getItemDef(id).getName(), key);
            for (ClericSpellGuideCatalog.Entry page : pages) {
                if (page.getStableCode() >= 0 && !key.equals("all")) {
                    ClericSpellDef spell = definitions.stream().filter(d -> d.getStableCode() == page.getStableCode()).findFirst().get();
                    check(spell.getAlignment().equals(key), "filter contains only matching god line");
                    filtered.add(spell.getStableCode());
                }
            }
            for (int i = 0; i < pages.size(); i++) renderWindow(mc, guide, i, "Cleric/" + key + "/" + i);
            renderWindow(mc, guide, 10000, "Cleric/" + key + "/end");
            check(guide.skillGuide.getScrollPosition(guide.skillGuideScroll) == pages.size() - 1,
                "filter's last spell page is reachable");
        }
        check(filtered.equals(all), "all twenty spells remain accessible across line filters");
        int x = (mc.getGameWidth() - 430) / 2, y = (mc.getGameHeight() - 320) / 2;
        mc.mouseX = x + 5 + 2 * 83 + 10;mc.mouseY = y + 90;mc.setMouseClick(1);
        guide.onRender(mc.getSurface());
        check(field(guide, "clericGuideFilter").equals("guthix"), "actual Guthix filter button handles click");
        check(mc.getMouseClick() == 0, "filter consumes the click");
        Arrays.fill(mc.getSurface().pixelData, 0x22262b);
        mc.getSurface().beginRenderer2DFrame();
        guide.onRender(mc.getSurface());
        ClericThreeTierClientRenderFixture.png((MudClientGraphics) mc.getSurface(),
            out.resolve("cleric-guthix-" + mc.getGameWidth() + ".png"));
    }

    @SuppressWarnings("unchecked")
    private static void questPages(mudclient mc, Path out) throws Exception {
        ClericThreeTierClientFixture.set(mc, "questGuideChosen", "Tourist Trap");
        ClericThreeTierClientFixture.set(mc, "questGuideStartWho", "Irena");
        ClericThreeTierClientFixture.set(mc, "questGuideStartWhere", "outside Shantay Pass");
        String[] requirements = new String[30];Arrays.fill(requirements, "10 Crafting; 20 Smithing; ability to defeat a level 47 enemy");
        String[] rewards = {"2 Quest Points", "Choose two experience rewards from Agility, Crafting, Smithing and Thieving"};
        ClericThreeTierClientFixture.set(mc, "questGuideRequirement", requirements);
        ClericThreeTierClientFixture.set(mc, "questGuideReward", rewards);
        QuestGuideInterface guide = new QuestGuideInterface(mc);
        for (int state : new int[]{0, 3, -1}) {
            ClericThreeTierClientFixture.set(mc, "questGuideProgress", state);
            guide.questGuide.resetScrollIndex(guide.questGuideScroll);
            guide.onRender(mc.getSurface());
            List<Object> entries = (List<Object>) field(guide, "questItems");
            StringBuilder text = new StringBuilder();
            for (Object entry : entries) text.append(call(entry, "getText", new Class<?>[]{})).append(' ');
            check(!text.toString().contains("coming soon"), "no false active walkthrough promise");
            if (state >= 0) check(text.toString().contains("Irena") && text.toString().contains("Requirements:"), "start contact and requirements retained");
            if (state > 0) check(text.toString().contains("Start contact:") && text.toString().contains("Detailed walkthrough unavailable"), "active quest has truthful help");
            for (int scroll : new int[]{0, 10, 10000}) {
                guide.questGuide.resetScrollIndex(guide.questGuideScroll);
                guide.onRender(mc.getSurface());
                guide.questGuide.scrollMethodList(guide.questGuideScroll, scroll);
                MudClientGraphics surface = (MudClientGraphics) mc.getSurface();
                Arrays.fill(surface.pixelData, 0x22262b);surface.beginRenderer2DFrame();
                guide.onRender(surface);
                bounds(surface.consumeRenderer2DFrame(), (mc.getGameWidth() - 430) / 2,
                    (mc.getGameHeight() - 320) / 2, 430, (mc.getGameHeight() - 320) / 2 + 320, "Quest/" + state);
                if (scroll == 10000) check(guide.questGuide.getScrollPosition(guide.questGuideScroll) == Math.max(0, entries.size() - 17), "quest rewards remain reachable");
            }
            if (state == 3) ClericThreeTierClientRenderFixture.png((MudClientGraphics) mc.getSurface(),
                out.resolve("quest-active-last-" + mc.getGameWidth() + ".png"));
        }
        ClericThreeTierClientFixture.set(mc, "questGuideRequirement", new String[]{"10 Crafting", "20 Smithing", "Ability to defeat a level 47 enemy"});
        ClericThreeTierClientFixture.set(mc, "questGuideProgress", 3);
        guide.questGuide.resetScrollIndex(guide.questGuideScroll);
        // The compact quest backdrop uses the previous frame's measured height.
        // Settle it before taking a standalone screenshot with no underlying game frame.
        guide.onRender(mc.getSurface());
        Arrays.fill(mc.getSurface().pixelData, 0x22262b);mc.getSurface().beginRenderer2DFrame();
        guide.onRender(mc.getSurface());
        ClericThreeTierClientRenderFixture.png((MudClientGraphics) mc.getSurface(),
            out.resolve("quest-active-summary-" + mc.getGameWidth() + ".png"));
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args[0]), out = Paths.get(args[2]);Files.createDirectories(out);
        Config.F_CACHE_DIR = root.resolve("Client_Base/Cache").toString();
        Config.S_WANT_CUSTOM_SPRITES = true;Config.S_WANT_HISCORES = false;
        EntityHandler.load(true);ClericThreeTierClientRenderFixture.fonts(root);
        List<ClericSpellDef> definitions = ClericSpellbookPacketDecoder.decode(Files.readAllBytes(Paths.get(args[1])));
        for (int[] size : new int[][]{{512, 334}, {960, 528}}) {
            mudclient mc = ClericSpellbookUiFixture.client(root, definitions, size[0], size[1]);
            // Same fixed native layer order as the normal client constructor (test-only allocation skips it).
            ClericThreeTierClientFixture.set(mc, "animDirLayer_To_CharLayer", new int[][]{
                {11,2,9,7,1,6,10,0,5,8,3,4},{11,2,9,7,1,6,10,0,5,8,3,4},{11,3,2,9,7,1,6,10,0,5,8,4},
                {3,4,2,9,7,1,6,10,8,11,0,5},{3,4,2,9,7,1,6,10,8,11,0,5},{4,3,2,9,7,1,6,10,8,11,0,5},
                {11,4,2,9,7,1,6,10,0,5,8,3},{11,2,9,7,1,6,10,0,5,8,4,3}});
            ordinaryPages(mc, out);
            clericFilters(mc, definitions, out);
            questPages(mc, out);
        }
        System.out.println("PASS guide updates: " + checks + " assertions; " + tabs + " tab/viewport combinations, " + windows
            + " native scroll windows, all filters and quest states at 512x334 and 960x528");
    }
}
