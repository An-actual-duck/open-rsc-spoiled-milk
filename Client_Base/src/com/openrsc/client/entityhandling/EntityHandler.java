package com.openrsc.client.entityhandling;

import com.openrsc.client.entityhandling.defs.*;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import com.openrsc.client.entityhandling.defs.extras.TextureDef;
import orsc.Config;
import orsc.mudclient;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;

public class EntityHandler {

	public static ArrayList<NPCDef> npcs = new ArrayList<>();
	private static final ArrayList<ItemDef> items = new ArrayList<>();
	private static final ArrayList<TextureDef> textures = new ArrayList<>();
	private static final ArrayList<AnimationDef> animations = new ArrayList<>();
	public static ArrayList<SpriteDef> projectiles = new ArrayList<>();
	public static ArrayList<SpriteDef> GUIparts = new ArrayList<>();
	public static ArrayList<SpriteDef> crowns = new ArrayList<>();
	private static final ArrayList<SpellDef> spells = new ArrayList<>();
	private static final ArrayList<PrayerDef> prayers = new ArrayList<>();
	private static String activePrayerBook = "SARADOMIN";
	private static final ArrayList<TileDef> tiles = new ArrayList<>();
	private static final ArrayList<DoorDef> doors = new ArrayList<>();
	private static final ArrayList<ElevationDef> elevation = new ArrayList<>();
	private static final ArrayList<GameObjectDef> objects = new ArrayList<>();
	private static final ArrayList<String> models = new ArrayList<>();
	public static ItemDef noteDef, certificateDef;

	private static int invPictureCount = 0;
	private static final int[] MYWORLD_STAFF_BASE_IDS = {
		100, 2131, 1764, 1769, 2136, 1774, 1779, 2141, 1784, 2146
	};
	private static final int[] MYWORLD_STAFF_WOOD_COLORS = {
		0x8B5A2B, 0xCCAA33, 0xB97A57, 0x7D9B5A, 0xC78C4A,
		0xA0522D, 0x6B4F2A, 0x333333, 0x4B2E83, 0xCC2222
	};
	private static final int[] MYWORLD_STAFF_RUNE_COLORS = {
		0x0AE5E4, 0x0401DC, 0x642E01, 0xD40203, 0xF0E68C,
		0x9A4A3A, 0xC88A54, 0xF2D13D, 0xFF8800, 0x3FAF5A,
		0x1B8A8F, 0x222222, 0x7F1D1D, 0x8FA8C9, 0xFFD95A
	};
	private static final int[][] MYWORLD_RUNE_STAFF_IDS = {
		{101, 2132, 1765, 1770, 2137, 1775, 1780, 2142, 1785, 2147},
		{102, 2133, 1766, 1771, 2138, 1776, 1781, 2143, 1786, 2148},
		{103, 2134, 1767, 1772, 2139, 1777, 1782, 2144, 1787, 2149},
		{197, 2135, 1768, 1773, 2140, 1778, 1783, 2145, 1788, 2150},
		{2228, 2229, 2230, 2231, 2232, 2233, 2234, 2235, 2236, 2237},
		{2238, 2239, 2240, 2241, 2242, 2243, 2244, 2245, 2246, 2247},
		{2248, 2249, 2250, 2251, 2252, 2253, 2254, 2255, 2256, 2257},
		{2258, 2259, 2260, 2261, 2262, 2263, 2264, 2265, 2266, 2267},
		{2268, 2269, 2270, 2271, 2272, 2273, 2274, 2275, 2276, 2277},
		{2278, 2279, 2280, 2281, 2282, 2283, 2284, 2285, 2286, 2287},
		{2288, 2289, 2290, 2291, 2292, 2293, 2294, 2295, 2296, 2297},
		{2298, 2299, 2300, 2301, 2302, 2303, 2304, 2305, 2306, 2307},
		{2308, 2309, 2310, 2311, 2312, 2313, 2314, 2315, 2316, 2317},
		{2318, 2319, 2320, 2321, 2322, 2323, 2324, 2325, 2326, 2327},
		{2754, 2755, 2756, 2757, 2758, 2759, 2760, 2761, 2762, 2763}
	};
	private static final int[] MYWORLD_RUNE_CLOTH_COLORS = {
		0x87CEEB, 0x8B4A3B, 0x1F4E8C, 0x7A5230, 0xC62828,
		0xC2A57A, 0xE0C341, 0xF57C00, 0x2E7D32, 0x1B8A8F,
		0x222222, 0x7F1D1D, 0x8FA8C9, 0xF2D75A
	};
	private static final int[] MYWORLD_PICKAXE_COLORS = {
		0xB7C9D9, 0xC86A2B, 16737817, 15654365, 15658734,
		10072780, 0x8EA6BB, 11717785, 0x5A3F7D, 65535
	};

	public static int getModelCount() {
		return models.size();
	}

	public static String getModelName(int id) {
		if (id < 0 || id >= models.size()) {
			return null;
		}
		return models.get(id);
	}

	public static int invPictureCount() {
		return invPictureCount;
	}

	public static int npcCount() {
		return npcs.size();
	}

	public static NPCDef getNpcDef(int id) {
		if (id < 0 || id >= npcs.size()) {
			return npcs.get(825); //Default NPC is Ana (not in a barrel)
		}
		return npcs.get(id);
	}

	public static int itemCount() {
		return items.size();
	}

	public static ItemDef getItemDef(int id) {
		int newId = id;
		boolean noted = false;
		if (id < 0) {
			newId = (newId + 1) * -1;
			noted = true;
		}
		if (newId < 0 || newId >= items.size()) {
			return items.get(1544); //Default Item is Unobtanium
		}
		ItemDef item = findItem(newId, noted);
		if (item == null) {
			return items.get(1544); //Default Item is Unobtanium
		}
		return item;
	}

	public static ItemDef getItemDef(int id, boolean isNote) {
		if (id < 0 || id >= items.size()) {
			return items.get(1544); //Default Item is Unobtanium
		}
		ItemDef item = findItem(id, isNote);
		if (item == null) {
			return items.get(1544); //Default Item is Unobtanium
		}
		return item;
	}

	public static ItemDef findItem(int id, boolean isNote) {
		ItemDef res = null;
		for (Iterator<ItemDef> iter = items.iterator(); iter.hasNext(); ) {
			ItemDef it = iter.next();
			if (it.id != id) continue;
			if (isUnobtaniumPlaceholder(it)) {
				if (res == null) {
					res = it;
				}
				continue;
			}
			if (!isNote) {
				return it;
			} else {
				return ItemDef.asNote(it);
			}
		}
		if (res == null) {
			return null;
		}
		return isNote ? ItemDef.asNote(res) : res;
	}

	public static int textureCount() {
		return textures.size();
	}

	public static int animationCount() {
		return animations.size();
	}

	public static AnimationDef getAnimationDef(int id) {
		if (id < 0 || id >= animations.size()) {
			return animations.get(0);
		}
		return animations.get(id);
	}

	public static int spellCount() {
		return spells.size();
	}

	public static SpellDef getSpellDef(int id) {
		if (id < 0 || id >= spells.size()) {
			return null;
		}
		return spells.get(id);
	}

	public static int prayerCount() {
		return prayers.size();
	}

	public static PrayerDef getPrayerDef(int id) {
		if (id < 0 || id >= prayers.size()) {
			return null;
		}
		return prayers.get(id);
	}

	public static String getActivePrayerBook() {
		return activePrayerBook;
	}

	public static void setPrayerBook(String prayerBook) {
		if (prayerBook == null) {
			return;
		}
		String normalizedPrayerBook = prayerBook.toUpperCase();
		prayers.clear();
		if (normalizedPrayerBook.equals("ZAMORAK")) {
			activePrayerBook = "ZAMORAK";
			loadZamorakPrayerDefinitions();
		} else if (normalizedPrayerBook.equals("GUTHIX")) {
			activePrayerBook = "GUTHIX";
			loadGuthixPrayerDefinitions();
		} else {
			activePrayerBook = "SARADOMIN";
			loadSaradominPrayerDefinitions();
		}
	}

	public static int tileCount() {
		return tiles.size();
	}

	public static TileDef getTileDef(int id) {
		if (id < 0 || id >= tiles.size()) {
			return null;
		}
		return tiles.get(id);
	}

	public static int doorCount() {
		return doors.size();
	}

	public static DoorDef getDoorDef(int id) {
		if (id < 0 || id >= doors.size()) {
			return null;
		}
		return doors.get(id);
	}

	public static int elevationCount() {
		return elevation.size();
	}

	public static ElevationDef getElevationDef(int id) {
		if (id < 0 || id >= elevation.size()) {
			return null;
		}
		return elevation.get(id);
	}

	public static int objectCount() {
		return objects.size();
	}

	public static GameObjectDef getObjectDef(int id) {
		if (id < 0 || id >= objects.size() || (objects.get(id) != null && objects.get(id).id != id)) {
			//There may be a gap in the object definitions that causes this. Check for that.
			for (int i = objects.size() - 1; i >= 0; i--) {
				if (objects.get(i).id == id)
					return objects.get(i);
			}
			return objects.get(4); // Default Object is tree stump
		}
		return objects.get(id);
	}

	private static void loadPrayerDefinitions() {
		setPrayerBook(activePrayerBook);
	}

	private static void loadSaradominPrayerDefinitions() {
		prayers.add(new PrayerDef(0, 3, "Weak Magic Power",
			"Reserve 3 prayer points. Magic damage +5%."));
		prayers.add(new PrayerDef(0, 6, "Lesser Magic Power",
			"Reserve 6 prayer points. Magic damage +10%."));
		prayers.add(new PrayerDef(0, 10, "Magic Power",
			"Reserve 10 prayer points. Magic damage +15%."));
		prayers.add(new PrayerDef(0, 15, "Strong Magic Power",
			"Reserve 15 prayer points. Magic damage +20%."));
		prayers.add(new PrayerDef(0, 21, "Greater Magic Power",
			"Reserve 21 prayer points. Magic damage +25%."));
		prayers.add(new PrayerDef(0, 3, "Weak Melee Protection",
			"Reserve 3 prayer points. Melee damage taken -5%."));
		prayers.add(new PrayerDef(0, 6, "Lesser Melee Protection",
			"Reserve 6 prayer points. Melee damage taken -10%."));
		prayers.add(new PrayerDef(0, 10, "Melee Protection",
			"Reserve 10 prayer points. Melee damage taken -15%."));
		prayers.add(new PrayerDef(0, 15, "Strong Melee Protection",
			"Reserve 15 prayer points. Melee damage taken -20%."));
		prayers.add(new PrayerDef(0, 21, "Greater Melee Protection",
			"Reserve 21 prayer points. Melee damage taken -25%."));
		prayers.add(new PrayerDef(0, 2, "Weak Enchanting Favor",
			"Reserve 2 prayer points. Enchanting XP +10%."));
		prayers.add(new PrayerDef(0, 8, "Lesser Enchanting Favor",
			"Reserve 8 prayer points. Enchanting XP +15%."));
		prayers.add(new PrayerDef(0, 20, "Enchanting Favor",
			"Reserve 20 prayer points. Enchanting XP +20%."));
		prayers.add(new PrayerDef(0, 35, "Strong Enchanting Favor",
			"Reserve 35 prayer points. Enchanting XP +25%."));
		prayers.add(new PrayerDef(0, 55, "Greater Enchanting Favor",
			"Reserve 55 prayer points. Enchanting XP +30%."));
	}

	private static void loadZamorakPrayerDefinitions() {
		prayers.add(new PrayerDef(0, 3, "Weak Melee Power",
			"Reserve 3 prayer points. Melee damage +5%."));
		prayers.add(new PrayerDef(0, 6, "Lesser Melee Power",
			"Reserve 6 prayer points. Melee damage +10%."));
		prayers.add(new PrayerDef(0, 10, "Melee Power",
			"Reserve 10 prayer points. Melee damage +15%."));
		prayers.add(new PrayerDef(0, 15, "Strong Melee Power",
			"Reserve 15 prayer points. Melee damage +20%."));
		prayers.add(new PrayerDef(0, 21, "Greater Melee Power",
			"Reserve 21 prayer points. Melee damage +25%."));
		prayers.add(new PrayerDef(0, 3, "Weak Ranged Protection",
			"Reserve 3 prayer points. Ranged damage taken -5%."));
		prayers.add(new PrayerDef(0, 6, "Lesser Ranged Protection",
			"Reserve 6 prayer points. Ranged damage taken -10%."));
		prayers.add(new PrayerDef(0, 10, "Ranged Protection",
			"Reserve 10 prayer points. Ranged damage taken -15%."));
		prayers.add(new PrayerDef(0, 15, "Strong Ranged Protection",
			"Reserve 15 prayer points. Ranged damage taken -20%."));
		prayers.add(new PrayerDef(0, 21, "Greater Ranged Protection",
			"Reserve 21 prayer points. Ranged damage taken -25%."));
		prayers.add(new PrayerDef(0, 2, "Weak Smithing Favor",
			"Reserve 2 prayer points. Smithing XP +10%."));
		prayers.add(new PrayerDef(0, 8, "Lesser Smithing Favor",
			"Reserve 8 prayer points. Smithing XP +15%."));
		prayers.add(new PrayerDef(0, 20, "Smithing Favor",
			"Reserve 20 prayer points. Smithing XP +20%."));
		prayers.add(new PrayerDef(0, 35, "Strong Smithing Favor",
			"Reserve 35 prayer points. Smithing XP +25%."));
		prayers.add(new PrayerDef(0, 55, "Greater Smithing Favor",
			"Reserve 55 prayer points. Smithing XP +30%."));
	}

	private static void loadGuthixPrayerDefinitions() {
		prayers.add(new PrayerDef(0, 3, "Weak Ranged Power",
			"Reserve 3 prayer points. Ranged damage +5%."));
		prayers.add(new PrayerDef(0, 6, "Lesser Ranged Power",
			"Reserve 6 prayer points. Ranged damage +10%."));
		prayers.add(new PrayerDef(0, 10, "Ranged Power",
			"Reserve 10 prayer points. Ranged damage +15%."));
		prayers.add(new PrayerDef(0, 15, "Strong Ranged Power",
			"Reserve 15 prayer points. Ranged damage +20%."));
		prayers.add(new PrayerDef(0, 21, "Greater Ranged Power",
			"Reserve 21 prayer points. Ranged damage +25%."));
		prayers.add(new PrayerDef(0, 3, "Weak Magic Protection",
			"Reserve 3 prayer points. Magic damage taken -5%."));
		prayers.add(new PrayerDef(0, 6, "Lesser Magic Protection",
			"Reserve 6 prayer points. Magic damage taken -10%."));
		prayers.add(new PrayerDef(0, 10, "Magic Protection",
			"Reserve 10 prayer points. Magic damage taken -15%."));
		prayers.add(new PrayerDef(0, 15, "Strong Magic Protection",
			"Reserve 15 prayer points. Magic damage taken -20%."));
		prayers.add(new PrayerDef(0, 21, "Greater Magic Protection",
			"Reserve 21 prayer points. Magic damage taken -25%."));
		prayers.add(new PrayerDef(0, 2, "Weak Crafting Favor",
			"Reserve 2 prayer points. Crafting XP +10%."));
		prayers.add(new PrayerDef(0, 8, "Lesser Crafting Favor",
			"Reserve 8 prayer points. Crafting XP +15%."));
		prayers.add(new PrayerDef(0, 20, "Crafting Favor",
			"Reserve 20 prayer points. Crafting XP +20%."));
		prayers.add(new PrayerDef(0, 35, "Strong Crafting Favor",
			"Reserve 35 prayer points. Crafting XP +25%."));
		prayers.add(new PrayerDef(0, 55, "Greater Crafting Favor",
			"Reserve 55 prayer points. Crafting XP +30%."));
	}

	private static void loadTileDefinitions() {
		tiles.add(new TileDef(-16913, 1, 0));
		tiles.add(new TileDef(1, 3, 1));
		tiles.add(new TileDef(3, 2, 0));
		tiles.add(new TileDef(3, 4, 0));
		tiles.add(new TileDef(-16913, 2, 0));
		tiles.add(new TileDef(-27685, 2, 0));
		tiles.add(new TileDef(25, 3, 1));
		tiles.add(new TileDef(12345678, 5, 1));
		tiles.add(new TileDef(-26426, 1, 1));
		tiles.add(new TileDef(-1, 5, 1));
		tiles.add(new TileDef(31, 3, 1));
		tiles.add(new TileDef(3, 4, 0));
		tiles.add(new TileDef(-4534, 2, 0));
		tiles.add(new TileDef(32, 2, 0));
		tiles.add(new TileDef(-9225, 2, 0));
		tiles.add(new TileDef(-3172, 2, 0));
		tiles.add(new TileDef(15, 2, 0));
		tiles.add(new TileDef(-2, 2, 0));
		tiles.add(new TileDef(-1, 3, 1));
		tiles.add(new TileDef(-2, 4, 0));
		tiles.add(new TileDef(-2, 4, 1));
		tiles.add(new TileDef(-2, 0, 0));
		tiles.add(new TileDef(-17793, 2, 0));
		tiles.add(new TileDef(-14594, 1, 1));
		tiles.add(new TileDef(1, 3, 0));
	}

	private static void loadElevationDefinitions() {
		elevation.add(new ElevationDef(64, 6));
		elevation.add(new ElevationDef(64, 3));
		elevation.add(new ElevationDef(96, 2));
		elevation.add(new ElevationDef(80, 33));
		elevation.add(new ElevationDef(80, 15));
		elevation.add(new ElevationDef(90, 49));
	}

	private static void loadTextureDefinitions() {
		textures.add(new TextureDef("wall", "door"));
		textures.add(new TextureDef("water", ""));
		textures.add(new TextureDef("wall", ""));
		textures.add(new TextureDef("planks", ""));
		textures.add(new TextureDef("wall", "doorway"));
		textures.add(new TextureDef("wall", "window"));
		textures.add(new TextureDef("roof", ""));
		textures.add(new TextureDef("wall", "arrowslit"));
		textures.add(new TextureDef("leafytree", ""));
		textures.add(new TextureDef("treestump", ""));
		textures.add(new TextureDef("fence", ""));
		textures.add(new TextureDef("mossy", ""));
		textures.add(new TextureDef("railings", ""));
		textures.add(new TextureDef("painting1", ""));
		textures.add(new TextureDef("painting2", ""));
		textures.add(new TextureDef("marble", ""));
		textures.add(new TextureDef("deadtree", ""));
		textures.add(new TextureDef("fountain", ""));
		textures.add(new TextureDef("wall", "stainedglass"));
		textures.add(new TextureDef("target", ""));
		textures.add(new TextureDef("books", ""));
		textures.add(new TextureDef("timbered", ""));
		textures.add(new TextureDef("timbered", "timberwindow"));
		textures.add(new TextureDef("mossybricks", ""));
		textures.add(new TextureDef("growingwheat", ""));
		textures.add(new TextureDef("gungywater", ""));
		textures.add(new TextureDef("web", ""));
		textures.add(new TextureDef("wall", "desertwindow"));
		textures.add(new TextureDef("wall", "crumbled"));
		textures.add(new TextureDef("cavern", ""));
		textures.add(new TextureDef("cavern2", ""));
		textures.add(new TextureDef("lava", ""));
		textures.add(new TextureDef("pentagram", ""));
		textures.add(new TextureDef("mapletree", ""));
		textures.add(new TextureDef("yewtree", ""));
		textures.add(new TextureDef("helmet", ""));
		textures.add(new TextureDef("canvas", "tentbottom"));
		textures.add(new TextureDef("Chainmail2", ""));
		textures.add(new TextureDef("mummy", ""));
		textures.add(new TextureDef("jungleleaf", ""));
		textures.add(new TextureDef("jungleleaf3", ""));
		textures.add(new TextureDef("jungleleaf4", ""));
		textures.add(new TextureDef("jungleleaf5", ""));
		textures.add(new TextureDef("jungleleaf6", ""));
		textures.add(new TextureDef("mossybricks", "arrowslit"));
		textures.add(new TextureDef("planks", "window"));
		textures.add(new TextureDef("planks", "junglewindow"));
		textures.add(new TextureDef("cargonet", ""));
		textures.add(new TextureDef("bark", ""));
		textures.add(new TextureDef("canvas", ""));
		textures.add(new TextureDef("canvas", "tentdoor"));
		textures.add(new TextureDef("wall", "lowcrumbled"));
		textures.add(new TextureDef("cavern", "crumbled"));
		textures.add(new TextureDef("cavern2", "crumbled"));
		textures.add(new TextureDef("lava", "flames"));

		if (Config.S_WANT_CUSTOM_SPRITES) {
			loadCustomTextureDefinitions();
		}
	}

	private static void loadCustomTextureDefinitions() {
		/*textures.add(new TextureDef("sapgift", ""));
		textures.add(new TextureDef("emegift", ""));
		textures.add(new TextureDef("rubygift", ""));
		textures.add(new TextureDef("diagift", ""));
		textures.add(new TextureDef("ornamenttree", ""));*/
	}

	public enum PROJECTILE_TYPES {
		ORB(0),
		MAGIC(1),
		RANGED(2),
		GNOMEBALL(3),
		SKULL(4),
		SPIKEBALL(5),
		BLANK(6), //not sure if this is even used for anything
		BLOW_SMOKE(7),
		FIREBALL(8),
		WIND_ARROW(9),
		ROCK_THROW(10),
		WATER_BALL(11),
		THROWING_KNIFE(12),
		ARROW(13),
		THROWING_DART(14),
		CLAWS_OF_GUTHIX(15),
		THUNDER_BALL(16),
		ICICLE_SHOT(17),
		ACID_DROP(18),
		BRANCH_SPORE(19);

		private final int value;

		PROJECTILE_TYPES(int value) {
			this.value = value;
		}

		public int id() {
			return value;
		}
	}

	public static int projectilesCount() {
		return projectiles.size();
	}

	private static void loadProjectiles() {
		projectiles.add(new SpriteDef("orb projectile", mudclient.spriteProjectile, "projectiles:0", 0));
		projectiles.add(new SpriteDef("magic projectile", mudclient.spriteProjectile + 1, "projectiles:1", 1));
		projectiles.add(new SpriteDef("ranged projectile", mudclient.spriteProjectile + 2, "projectiles:2", 2));
		projectiles.add(new SpriteDef("gnomeball projectile", mudclient.spriteProjectile + 3, "projectiles:3", 3));
		projectiles.add(new SpriteDef("skull projectile", mudclient.spriteProjectile + 4, "projectiles:4", 4));
		projectiles.add(new SpriteDef("spiked ball projectile", mudclient.spriteProjectile + 5, "projectiles:5", 5));
		projectiles.add(new SpriteDef("blank projectile", mudclient.spriteProjectile + 6, "projectiles:6", 6));
		projectiles.add(new SpriteDef("blow smoke projectile", mudclient.spriteProjectile + 1, "projectiles:1", 7));
		projectiles.add(new SpriteDef("fireball projectile", mudclient.spriteProjectile + 1, "projectiles:1", 8));
		projectiles.add(new SpriteDef("wind arrow projectile", mudclient.spriteProjectile + 1, "projectiles:1", 9));
		projectiles.add(new SpriteDef("rock throw projectile", mudclient.spriteProjectile + 1, "projectiles:1", 10));
		projectiles.add(new SpriteDef("water ball projectile", mudclient.spriteProjectile + 1, "projectiles:1", 11));
		projectiles.add(new SpriteDef("throwing knife projectile", mudclient.spriteProjectile + 6, "projectiles:6", 12));
		projectiles.add(new SpriteDef("arrow projectile", mudclient.spriteProjectile + 6, "projectiles:6", 13));
		projectiles.add(new SpriteDef("throwing dart projectile", mudclient.spriteProjectile + 6, "projectiles:6", 14));
		projectiles.add(new SpriteDef("claws of guthix projectile", mudclient.spriteProjectile + 1, "projectiles:1", 15));
		projectiles.add(new SpriteDef("thunder ball projectile", mudclient.spriteProjectile + 1, "projectiles:1", 16));
		projectiles.add(new SpriteDef("icicle shot projectile", mudclient.spriteProjectile + 1, "projectiles:1", 17));
		projectiles.add(new SpriteDef("acid drop projectile", mudclient.spriteProjectile + 1, "projectiles:1", 18));
		projectiles.add(new SpriteDef("spore projectile", mudclient.spriteProjectile + 1, "projectiles:1", 19));
	}

	public enum GUIPARTS {
		MAINLOGO(0),
		BLUEBAR(1),
		ACCEPTBUTTON(2),
		DECLINEBUTTON(3),
		SKULL(4),
		DAMAGETAKEN(5),
		DAMAGEGIVEN(6),
		MENUBAR(7),
		MENUSOCIAL(8),
		MENUSPELLS(9),
		MINIMAPTAB(10),
		SETTINGSTAB(11),
		SKILLSTAB(12),
		BAGTAB(13),
		CLIPPING(14),
		CHECKMARK(15),
		XMARK(16),
		CHATTABS(17),
		CHATTABSCLAN(18),
		COMPASS(19),
		UPARROW(20),
		DOWNARROW(21),
		RIGHTARROW(22),
		LEFTARROW(23),
		MINIARROWUP(24),
		MINIARROWDOWN(25),
		DECORATEDBOXUL(26),
		DECORATEDBOXUR(27),
		DECORATEDBOXLL(28),
		DECORATEDBOXLR(29),
		YELLOWX1(30),
		YELLOWX2(31),
		YELLOWX3(32),
		YELLOWX4(33),
		REDX1(34),
		REDX2(35),
		REDX3(36),
		REDX4(37),
		EQUIPSLOT_HELM(38),
		EQUIPSLOT_BODY(39),
		EQUIPSLOT_LEGS(40),
		EQUIPSLOT_SHIELD(41),
		EQUIPSLOT_SWORD(42),
		EQUIPSLOT_GLOVES(43),
		EQUIPSLOT_BOOTS(44),
		EQUIPSLOT_NECK(45),
		EQUIPSLOT_CAPE(46),
		EQUIPSLOT_AMMO(47),
		EQUIPSLOT_RING(48),
		EQUIPSLOT_HIGHLIGHT(49),
		BANK_EQUIP_BAG(50),
		BANK_EQUIP_HELM(51),
		BANK_PRESET_OPTIONS(52),
		KEPT_ON_DEATH(53);

		private final int value;

		GUIPARTS(int value) {
			this.value = value;
		}

		public int id() {
			return value;
		}

		public SpriteDef getDef() {
			return GUIparts.get(this.value);
		}
	}

	private static void loadGUIParts() {
		GUIparts.add(new SpriteDef("main logo", mudclient.spriteMedia + 10, "GUI:7", 0));
		GUIparts.add(new SpriteDef("bluebar", mudclient.spriteMedia + 22, "GUI:19", 1));
		GUIparts.add(new SpriteDef("accept button", mudclient.spriteMedia + 25, "GUI:22", 2));
		GUIparts.add(new SpriteDef("decline button", mudclient.spriteMedia + 26, "GUI:23", 3));
		GUIparts.add(new SpriteDef("skull", mudclient.spriteMedia + 13, "GUI:10", 4));
		GUIparts.add(new SpriteDef("blue damage taken bubble", mudclient.spriteMedia + 12, "GUI:9", 5));
		GUIparts.add(new SpriteDef("red damage taken bubble", mudclient.spriteMedia + 11, "GUI:8", 6));
		GUIparts.add(new SpriteDef("menu bar", mudclient.spriteMedia, "GUI:0", 7));
		GUIparts.add(new SpriteDef("social tab", mudclient.spriteMedia + 5, "GUI:5", 8));
		GUIparts.add(new SpriteDef("spell tab", mudclient.spriteMedia + 4, "GUI:4", 9));
		GUIparts.add(new SpriteDef("minimap tab", mudclient.spriteMedia + 2, "GUI:2", 10));
		GUIparts.add(new SpriteDef("settings tab", mudclient.spriteMedia + 6, "GUI:6", 11));
		GUIparts.add(new SpriteDef("skills tab", mudclient.spriteMedia + 3, "GUI:3", 12));
		GUIparts.add(new SpriteDef("bag tab", mudclient.spriteMedia + 1, "GUI:1", 13));
		GUIparts.add(new SpriteDef("clipping sprite", mudclient.spriteMedia + 9, "clipping:0", 14));
		GUIparts.add(new SpriteDef("check mark", mudclient.spriteMedia + 27, "GUI:24", 15));
		GUIparts.add(new SpriteDef("x mark", mudclient.spriteMedia + 28, "GUI:25", 16));
		GUIparts.add(new SpriteDef("chat tabs", mudclient.spriteMedia + 30, "GUI:27", 17));
		GUIparts.add(new SpriteDef("chat tabs clan", mudclient.spriteMedia + 23, "GUI:20", 18));
		GUIparts.add(new SpriteDef("compass", mudclient.spriteMedia + 24, "GUI:21", 19));
		GUIparts.add(new SpriteDef("up arrow", mudclient.spriteUtil + 8, "GUIutil:8", 20));
		GUIparts.add(new SpriteDef("down arrow", mudclient.spriteUtil + 9, "GUIutil:9", 21));
		GUIparts.add(new SpriteDef("right arrow", mudclient.spriteUtil + 6, "GUIutil:6", 22));
		GUIparts.add(new SpriteDef("left arrow", mudclient.spriteUtil + 7, "GUIutil:7", 23));
		GUIparts.add(new SpriteDef("mini up arrow", mudclient.spriteUtil, "GUIutil:0", 24));
		GUIparts.add(new SpriteDef("mini down arrow", mudclient.spriteUtil + 1, "GUIutil:1", 25));
		GUIparts.add(new SpriteDef("decorated box upper left", mudclient.spriteUtil + 2, "GUIutil:2", 26));
		GUIparts.add(new SpriteDef("decorated box upper right", mudclient.spriteUtil + 3, "GUIutil:3", 27));
		GUIparts.add(new SpriteDef("decorated box lower left", mudclient.spriteUtil + 4, "GUIutil:4", 28));
		GUIparts.add(new SpriteDef("decorated box lower right", mudclient.spriteUtil + 5, "GUIutil:5", 29));
		GUIparts.add(new SpriteDef("yellow cross 1", mudclient.spriteMedia + 14, "GUI:11", 30));
		GUIparts.add(new SpriteDef("yellow cross 2", mudclient.spriteMedia + 15, "GUI:12", 31));
		GUIparts.add(new SpriteDef("yellow cross 3", mudclient.spriteMedia + 16, "GUI:13", 32));
		GUIparts.add(new SpriteDef("yellow cross 4", mudclient.spriteMedia + 17, "GUI:14", 33));
		GUIparts.add(new SpriteDef("red cross 1", mudclient.spriteMedia + 18, "GUI:15", 34));
		GUIparts.add(new SpriteDef("red cross 2", mudclient.spriteMedia + 19, "GUI:16", 35));
		GUIparts.add(new SpriteDef("red cross 3", mudclient.spriteMedia + 20, "GUI:17", 36));
		GUIparts.add(new SpriteDef("red cross 4", mudclient.spriteMedia + 21, "GUI:18", 37));
		GUIparts.add(new SpriteDef("equipment slot head", -1, "GUI:28", 38));
		GUIparts.add(new SpriteDef("equipment slot body", -1, "GUI:29", 39));
		GUIparts.add(new SpriteDef("equipment slot legs", -1, "GUI:30", 40));
		GUIparts.add(new SpriteDef("equipment slot shield", -1, "GUI:31", 41));
		GUIparts.add(new SpriteDef("equipment slot sword", -1, "GUI:32", 42));
		GUIparts.add(new SpriteDef("equipment slot neck", -1, "GUI:33", 43));
		GUIparts.add(new SpriteDef("equipment slot gloves", -1, "GUI:34", 44));
		GUIparts.add(new SpriteDef("equipment slot boots", -1, "GUI:35", 45));
		GUIparts.add(new SpriteDef("equipment slot cape", -1, "GUI:36", 46));
		GUIparts.add(new SpriteDef("equipment slot ammo", -1, "GUI:37", 47));
		GUIparts.add(new SpriteDef("equipment slot ring", -1, "GUI:38", 48));
		GUIparts.add(new SpriteDef("equipment blue highlight+", -1, "GUI:39", 49));
		GUIparts.add(new SpriteDef("bank toggle for inventory mode", -1, "GUI:40", 50));
		GUIparts.add(new SpriteDef("bank toggle for equipment mode", -1, "GUI:41", 51));
		GUIparts.add(new SpriteDef("bank preset options gear", -1, "GUI:42", 52));
		GUIparts.add(new SpriteDef("items kept on death", -1, "GUI:43", 53));
	}

	public enum CROWN_TYPES {
		GREY_MOD(0),
		GOLD_MOD(1),
		DARKGREY_MOD(2),
		STAR(3),
		KEY(4);

		private final int value;

		CROWN_TYPES(int value) {
			this.value = value;
		}

		public int id() {
			return value;
		}
	}

	public static int crownCount() {
		return crowns.size();
	}

	private static void loadCrowns() {
		crowns.add(new SpriteDef("grey mod crown", 3284, "crowns:0", 0));
		crowns.add(new SpriteDef("gold mod crown", 3285, "crowns:1", 1));
		crowns.add(new SpriteDef("dark grey mod crown", 3286, "crowns:2", 2));
		crowns.add(new SpriteDef("star", 3287, "crowns:3", 3));
		crowns.add(new SpriteDef("key", 3288, "crowns:4", 4));

	}

	private static void loadNpcDefinitions1() {
		int i = 0;
		// SPRITE ARRAY ORDER
		// head, shirt, pants, shield, weapon, hat, body, legs, gloves, boots, amulet, cape
		int[] sprites;

		/* Configurable NPC Data */
		String bankerOption1 = Config.S_RIGHT_CLICK_BANK ? "Bank" : ""; // Banker right click bank
		String bankerOption2 = Config.S_SPAWN_AUCTION_NPCS ? "Collect" : null; // Banker collect for auctions
		String shopOption = Config.S_RIGHT_CLICK_TRADE ? "Trade" : ""; // Shop right click trade

		sprites = new int[]{130, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Unicorn", "It's a unicorn", "", 21, 23, 19, 23, true, sprites, 0, 0, 0, 0, 201, 230, 6, 6, 7, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, 518};
		} else {
			sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		}
		npcs.add(new NPCDef("Bob", "An axe seller", shopOption, 2, 2, 3, 2, false, sprites, 3158064, 16711680, 16711680, 9461792, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{129, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Sheep", "A very wooly sheep", "", 0, 0, 3, 0, false, sprites, 0, 0, 0, 0, 170, 124, 6, 6, 5, i++));
		sprites = new int[]{132, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Chicken", "Yep definitely a chicken", "", 3, 4, 3, 4, true, sprites, 0, 0, 0, 0, 70, 62, 6, 6, 5, i++));
		sprites = new int[]{142, 139, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Goblin", "An ugly green creature", "", 16, 14, 12, 13, true, sprites, 0, 0, 0, 0, 219, 206, 9, 8, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Hans", "A castle servant", "", 3, 3, 3, 3, true, sprites, 1, 16711680, 65280, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{128, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("cow", "It's a multi purpose cow", "", 9, 8, 8, 9, true, sprites, 0, 0, 0, 0, 327, 240, 6, 6, 45, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 8, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("cook", "The head cook of Lumbridge castle", "", 20, 20, 3, 20, false, sprites, 1, 16777215, 16711680, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{131, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bear", "Eek! A bear!", "", 25, 23, 25, 26, true, sprites, 0, 0, 0, 0, 262, 247, 6, 9, 30, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Priest", "A priest of Saradomin", "", 0, 0, 3, 0, false, sprites, 1, 2105376, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Urhney", "He looks a little grumpy", "", 10, 10, 3, 10, false, sprites, 1, 2105376, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Man", "One of runescapes many citizens", "pickpocket", 11, 8, 7, 11, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bartender", "I could get a beer off him", "", 0, 0, 3, 0, false, sprites, 1, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{127, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Camel", "Oh its a camel", "", 0, 0, 3, 0, false, sprites, 0, 0, 0, 0, 208, 208, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Gypsy", "An old gypsy lady", "", 0, 0, 3, 0, false, sprites, 15921906, 255, 65280, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{137, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ghost", "Ooh spooky", "", 15, 15, 5, 15, false, sprites, 0, 0, 0, 0, 201, 243, 9, 9, 5, i++));
		sprites = new int[]{13, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Sir Prysin", "One of the king's knights", "", 30, 60, 50, 20, false, sprites, 1, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 77, 76, 81, -1, -1, -1, -1};
		npcs.add(new NPCDef("Traiborn the wizard", "An old wizard", "", 20, 15, 3, 10, false, sprites, 16777215, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{0, 28, 2, -1, -1, -1, -1, -1, -1, -1, -1, 314};
		} else {
			sprites = new int[]{0, 28, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		}
		npcs.add(new NPCDef("Captain Rovin", "The head of the palace guard", "", 40, 70, 65, 30, false, sprites, 11167296, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{123, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Rat", "Overgrown vermin", "", 10, 10, 5, 10, true, sprites, 0, 0, 0, 0, 346, 136, 7, 7, 45, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Reldo", "I think he's the librarian", "", 20, 15, 3, 10, false, sprites, 1, 2, 65280, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("mugger", "He jumps out and attacks people", "", 15, 10, 8, 8, true, sprites, 1, 16711680, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{124, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Lesser Demon", "Lesser but still pretty big", "", 78, 79, 79, 80, true, sprites, 0, 0, 0, 0, 275, 262, 11, 11, 30, i++));
		sprites = new int[]{125, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Giant Spider", "I think this spider has been genetically modified", "", 10, 10, 5, 10, true, sprites, 0, 0, 0, 0, 120, 104, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Man", "A shifty looking man", "", 30, 30, 30, 30, false, sprites, 1, 16711680, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Jonny the beard", "I can see why he's called the beard", "", 10, 20, 8, 5, true, sprites, 1, 2, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Baraek", "A fur trader", "", 30, 30, 30, 30, false, sprites, 1, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Katrine", "She doesn't look to friendly", "", 35, 25, 10, 30, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Tramp", "A scruffy looking chap", "", 9, 8, 5, 7, false, sprites, 16711680, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{123, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Rat", "A small muddy rat", "", 3, 4, 2, 2, true, sprites, 0, 0, 0, 0, 115, 45, 7, 7, 10, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Romeo", "He looks mildly confused", "", 20, 60, 60, 40, false, sprites, 16761440, 255, 8409120, 15523536, 125, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 89, -1, -1, -1, -1};
		npcs.add(new NPCDef("Juliet", "She looks a little stressed", "", 2, 4, 3, 2, false, sprites, 15645552, 16036851, 16036851, 15523536, 125, 225, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Father Lawrence", "A kindly looking priest", "", 0, 0, 3, 0, false, sprites, 11167296, 2105376, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Apothecary", "I wonder if he has any good potions", "", 10, 5, 7, 5, false, sprites, 11167296, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{125, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("spider", "Incey wincey", "", 5, 2, 2, 1, true, sprites, 0, 0, 0, 0, 40, 35, 6, 6, 5, i++));
		sprites = new int[]{124, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Delrith", "A freshly summoned demon", "", 42, 35, 7, 37, true, sprites, 0, 0, 0, 0, 275, 262, 11, 11, 30, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 88, -1, -1, -1, -1};
		npcs.add(new NPCDef("Veronica", "She doesn't look too happy", "", 1, 1, 5, 1, false, sprites, 15643488, 255, 3, 15523536, 140, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, 48, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Weaponsmaster", "The phoenix gang quartermaster", "", 35, 20, 20, 28, true, sprites, 1, 16711680, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Professor Oddenstein", "A mad scientist if I ever saw one", "", 3, 3, 7, 3, false, sprites, 16777215, 16777215, 16777215, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Curator", "He looks like he's daydreaming", "", 3, 2, 3, 2, false, sprites, 16777215, 2, 8409120, 15523536, 145, 200, 6, 6, 5, i++));
		sprites = new int[]{133, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("skeleton", "It rattles as it walks", "", 24, 20, 17, 23, true, sprites, 0, 0, 0, 0, 216, 234, 11, 11, 5, i++));
		sprites = new int[]{135, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("zombie", "The living dead", "", 23, 28, 24, 23, true, sprites, 0, 0, 0, 0, 174, 259, 12, 12, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 80, 62};
		npcs.add(new NPCDef("king", "King Roald the VIII", "", 15, 60, 30, 15, false, sprites, 1, 16711680, 16777215, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{138, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Giant bat", "An angry flying rodent", "", 32, 32, 32, 32, true, sprites, 0, 0, 0, 0, 225, 195, 5, 3, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bartender", "A friendly barman", "", 0, 0, 3, 0, false, sprites, 1, 8409120, 8409120, 15523536, 155, 220, 6, 6, 5, i++));
		sprites = new int[]{134, 133, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("skeleton", "It rattles as it walks", "", 32, 30, 29, 35, true, sprites, 0, 0, 0, 0, 216, 234, 11, 11, 5, i++));
		sprites = new int[]{133, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("skeleton", "It rattles as it walks", "", 27, 24, 24, 28, true, sprites, 0, 0, 0, 0, 216, 234, 11, 11, 5, i++));
		sprites = new int[]{123, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Rat", "overgrown vermin", "", 16, 12, 10, 15, true, sprites, 0, 0, 0, 0, 346, 136, 7, 7, 45, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Horvik the Armourer", "He looks strong", shopOption, 15, 22, 22, 6, false, sprites, 16761440, 2, 8409120, 15523536, 155, 230, 6, 6, 5, i++));
		sprites = new int[]{131, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bear", "A  bear", "", 0, 0, 3, 0, false, sprites, 0, 0, 0, 0, 262, 247, 6, 9, 30, i++));
		sprites = new int[]{133, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("skeleton", "It rattles when it walks", "", 20, 18, 18, 21, true, sprites, 0, 0, 0, 0, 216, 234, 11, 11, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Shopkeeper", "Maybe he'd like to buy some of my junk", shopOption, 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{135, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("zombie", "The living dead", "", 18, 20, 22, 19, true, sprites, 0, 0, 0, 0, 174, 259, 12, 12, 5, i++));
		sprites = new int[]{137, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ghost", "Ooh spooky", "", 23, 30, 25, 23, true, sprites, 0, 0, 0, 0, 201, 243, 9, 9, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Aubury", "I think he might be a shop keeper", (Config.S_RIGHT_CLICK_TRADE ? "Trade" : ""), 0, 0, 3, 0, false, sprites, 1, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Shopkeeper", "I wonder what he's got for sale", shopOption, 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("shopkeeper", "I can buy swords off him", shopOption, 0, 0, 3, 0, false, sprites, 16761440, 2, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 82, 88, -1, -1, -1, -1};
		npcs.add(new NPCDef("Darkwizard", "He works evil magic", "", 15, 15, 12, 12, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{0, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, 517};
		} else {
			sprites = new int[]{0, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		}
		npcs.add(new NPCDef("lowe", "The owner of the archery store", shopOption, 0, 0, 3, 0, false, sprites, 16761440, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Thessalia", "A young shop assistant", shopOption, 0, 0, 3, 0, false, sprites, 1, 16036851, 3, 15523536, 130, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 82, 88, -1, -1, -1, -1};
		npcs.add(new NPCDef("Darkwizard", "He works evil magic", "", 27, 24, 24, 27, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Giant", "A very large foe", "", 37, 36, 35, 40, true, sprites, 1, 2, 8409120, 15523536, 218, 330, 6, 6, 5, i++));
		sprites = new int[]{139, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Goblin", "An ugly green creature", "", 8, 9, 5, 9, true, sprites, 0, 0, 0, 0, 219, 206, 9, 8, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("farmer", "He grows the crops in this area", "pickpocket", 15, 16, 12, 18, true, sprites, 11167296, 8409120, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 45, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Thief", "He'll take anything that isn't nailed down", "", 24, 22, 17, 23, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 69, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guard", "He tries to keep order around here", "pickpocket", 31, 30, 22, 31, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{18, 32, 42, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Black Knight", "A sinister looking knight", "", 45, 50, 42, 48, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{139, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Hobgoblin", "A large ugly green creature", "", 32, 34, 29, 34, true, sprites, 0, 0, 0, 0, 285, 268, 9, 8, 7, i++));
		sprites = new int[]{136, 135, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("zombie", "The living dead", "", 32, 31, 30, 35, true, sprites, 0, 0, 0, 0, 174, 259, 12, 12, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 76, 81, -1, -1, -1, -1};
		npcs.add(new NPCDef("Zaff", "He trades in staffs", shopOption, 0, 0, 3, 0, false, sprites, 3158064, 2, 3, 10056486, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{143, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Scorpion", "An extremely vicious scorpion", "", 21, 24, 17, 22, true, sprites, 0, 0, 0, 0, 362, 208, 7, 7, 45, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("silk trader", "He sells silk", "", 0, 0, 3, 0, false, sprites, 3158064, 16724172, 16724172, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Man", "One of Runescapes many citizens", "pickpocket", 11, 8, 7, 11, true, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guide", "He gives hints to new adventurers", "", 0, 0, 7, 0, false, sprites, 1, 32768, 8388863, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{125, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Giant Spider", "I think this spider has been genetically modified", "", 30, 31, 32, 34, true, sprites, 0, 0, 0, 0, 180, 156, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 70, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Peksa", "A helmet salesman", shopOption, 11, 8, 7, 11, false, sprites, 15645552, 2, 3, 15523536, 160, 230, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Barbarian", "Not civilised looking", "", 18, 15, 14, 18, true, sprites, 15645552, 8409120, 8409120, 15523536, 160, 230, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Fred the farmer", "An old farmer", "", 11, 8, 7, 11, false, sprites, 15921906, 8409120, 8409136, 15523536, 160, 230, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Gunthor the Brave", "The barbarians fearless leader", "", 37, 40, 35, 38, true, sprites, 15645552, 16732192, 8409120, 15523536, 165, 245, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 78, -1, 88, -1, -1, -1, 63};
		npcs.add(new NPCDef("Witch", "She's got warts", "", 35, 25, 10, 30, true, sprites, 1, 2, 3, 15523536, 155, 220, 6, 6, 5, i++));
		sprites = new int[]{137, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ghost", "Ooh spooky", "", 23, 30, 25, 23, true, sprites, 0, 0, 0, 0, 201, 243, 9, 9, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 77, 76, 81, -1, -1, -1, -1};
		npcs.add(new NPCDef("Wizard", "An old wizard", "", 18, 15, 14, 18, true, sprites, 16777215, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Shop Assistant", "Maybe he'd like to buy some of my junk", shopOption, 0, 0, 3, 0, false, sprites, 6307872, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Shop Assistant", "Maybe he'd like to buy some of my junk", shopOption, 0, 0, 3, 0, false, sprites, 6307872, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 70, -1, -1, -1, -1, -1, 63};
		npcs.add(new NPCDef("Zeke", "He sells Scimitars", shopOption, 0, 0, 3, 0, false, sprites, 3158064, 16763952, 15609986, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 37, -1, -1, -1, -1, -1, -1, -1, -1, 63};
		npcs.add(new NPCDef("Louie Legs", "He might want to sell something", shopOption, 0, 0, 3, 0, false, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, 98, 48, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Warrior", "A member of Al Kharid's military", "pickpocket", 20, 17, 19, 18, true, sprites, 1, 13385932, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Shopkeeper", "I wonder what he's got for sale", shopOption, 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 13415270, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Shop Assistant", "Maybe she'd like to buy some of my junk", shopOption, 0, 0, 3, 0, false, sprites, 6307872, 8409120, 8409120, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, 47, -1, -1, -1, -1, -1, -1, -1, 63};
		npcs.add(new NPCDef("Highwayman", "He holds up passers by", "", 14, 15, 13, 13, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Kebab Seller", "A seller of strange food", "", 0, 0, 3, 0, false, sprites, 1, 8409120, 8409120, 13415270, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{132, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Chicken", "Yep definitely a chicken", "", 3, 4, 3, 4, false, sprites, 0, 0, 0, 0, 70, 62, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ernest", "A former chicken", "", 3, 3, 3, 3, false, sprites, 16761440, 65535, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, 83, 87, -1, -1, -1, -1};
		npcs.add(new NPCDef("Monk", "A Peaceful monk", "", 12, 13, 15, 12, true, sprites, 16761440, 65535, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Dwarf", "A short angry guy", "", 20, 17, 16, 20, true, sprites, 7360576, 8409120, 8409120, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Banker", "He can look after my money", bankerOption1, bankerOption2, 11, 8, 7, 11, false, sprites, 15921906, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, 63};
		npcs.add(new NPCDef("Count Draynor", "A vicious vampire", "", 40, 65, 35, 35, true, sprites, 1, 2, 3, 16576224, 140, 230, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Morgan", "A frigtened villager", "", 11, 8, 7, 11, false, sprites, 1, 16267666, 10464124, 15523536, 155, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Dr Harlow", "His nose is very red", "", 11, 8, 7, 11, false, sprites, 15921906, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{126, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Deadly Red spider", "I think this spider has been genetically modified", "", 40, 36, 35, 35, true, sprites, 0, 0, 0, 0, 120, 104, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 69, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guard", "He's here to guard this fortress", "pickpocket", 31, 30, 22, 31, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Cassie", "She sells shields", shopOption, 35, 25, 10, 30, false, sprites, 16753488, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{19, 34, 43, -1, 49, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("White Knight", "A chivalrous knight", "", 55, 60, 52, 58, true, sprites, 1, 0xE7E2D6, 0xE7E2D6, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 93, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ranael", "A shopkeeper of some sort", shopOption, 35, 25, 10, 30, false, sprites, 16753488, 14518442, 3, 13415270, 145, 235, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 122, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Moss Giant", "his beard seems to have a life of its own", "", 62, 61, 60, 65, true, sprites, 7838054, 8409120, 8409120, 14483408, 261, 396, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Shopkeeper", "I wonder what he's got for sale", shopOption, 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Shop Assistant", "Maybe he'd like to buy some of my junk", shopOption, 0, 0, 3, 0, false, sprites, 6307872, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 78, -1, 88, -1, -1, -1, 63};
		npcs.add(new NPCDef("Witch", "She's got warts", "", 35, 25, 10, 30, false, sprites, 1, 2, 3, 15523536, 155, 220, 6, 6, 5, i++));
		sprites = new int[]{18, 32, 42, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Black Knight", "A sinister looking knight", "", 45, 50, 42, 48, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{139, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Greldo", "A small green warty creature", "", 8, 9, 5, 9, true, sprites, 0, 0, 0, 0, 219, 206, 9, 8, 5, i++));
		sprites = new int[]{19, 34, 43, -1, 49, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Sir Amik Varze", "The leader of the white knights", "", 55, 60, 52, 58, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 29, 38, 48, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guildmaster", "He's in charge of this place", "", 40, 40, 40, 40, false, sprites, 1, 13385932, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Valaine", "She runs the champion's store", shopOption, 35, 25, 10, 30, false, sprites, 16753488, 3211263, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Drogo", "He runs a mining store", shopOption, 20, 17, 16, 20, false, sprites, 7360576, 9465888, 9465888, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{124, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Imp", "A cheeky little imp", "", 4, 4, 8, 5, true, sprites, 0, 0, 0, 0, 74, 70, 11, 11, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Flynn", "The mace salesman", shopOption, 15, 22, 22, 6, false, sprites, 16761440, 2, 8409120, 15523536, 155, 230, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Wyson the gardener", "An old gardener", "", 10, 8, 7, 8, false, sprites, 16777215, 8947848, 8409120, 15523536, 155, 230, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 77, 76, 81, -1, -1, -1, -1};
		npcs.add(new NPCDef("Wizard Mizgog", "An old wizard", "", 20, 15, 3, 10, false, sprites, 14535816, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("Prince Ali", "A young prince", "", 20, 20, 20, 20, false, sprites, 1, 15618286, 15658576, 13415270, 140, 215, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, -1, -1, -1, -1, 79, 62};
		npcs.add(new NPCDef("Hassan", "the Chancellor to the emir", "", 20, 20, 20, 20, false, sprites, 1, 16777215, 16777215, 13415270, 150, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 82, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Osman", "He looks a little shifty", "", 20, 20, 20, 20, false, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, 116, -1, 22, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Joe", "Lady Keli's head guard", "", 40, 40, 40, 40, false, sprites, 1, 2, 3, 15523536, 155, 230, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 88, -1, -1, -1, -1};
		npcs.add(new NPCDef("Leela", "She comes from Al Kharid", "", 20, 20, 20, 20, false, sprites, 1, 12285781, 3, 13415270, 140, 215, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 89, -1, -1, -1, -1};
		npcs.add(new NPCDef("Lady Keli", "An Infamous bandit", "", 20, 20, 20, 20, false, sprites, 16763992, 15618286, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ned", "An old sailor", "", 20, 20, 20, 20, false, sprites, 16777215, 255, 3, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 78, -1, 88, -1, -1, -1, 63};
		npcs.add(new NPCDef("Aggie", "A witch", "", 35, 25, 10, 30, false, sprites, 1, 16711680, 3, 15523536, 155, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 89, -1, -1, -1, -1};
		npcs.add(new NPCDef("Prince Ali", "That is an effective disguise", "", 10, 10, 10, 10, false, sprites, 16763992, 15618286, 3, 15523536, 140, 215, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, 45, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Jailguard", "I wonder what he's guarding", "", 34, 34, 32, 36, true, sprites, 16763992, 2, 3, 15523536, 155, 230, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 48, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Redbeard Frank", "A pirate", "", 35, 25, 10, 30, false, sprites, 15630384, 2, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Wydin", "A grocer", shopOption, 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("shop assistant", "I can buy swords off him", shopOption, 0, 0, 3, 0, false, sprites, 16761440, 2, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Brian", "An axe seller", shopOption, 0, 0, 3, 0, false, sprites, 16761440, 2, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 45, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("squire", "A young squire", "", 0, 0, 3, 0, false, sprites, 14535800, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{6, 1, 2, -1, -1, 8, -1, -1, -1, -1, 9, 310};
		} else {
			sprites = new int[]{6, 1, 2, -1, -1, 8, -1, -1, -1, -1, 9, -1};
		}
		npcs.add(new NPCDef("Head chef", "He looks after the chef's guild", "", 20, 20, 3, 20, false, sprites, 1, 16777215, 16711680, 15523536, 150, 220, 6, 6, 5, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, 383};
		} else {
			sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		}
		npcs.add(new NPCDef("Thurgo", "A short angry guy", "", 20, 17, 16, 20, false, sprites, 15658734, 8409200, 8409120, 13415270, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 122, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ice Giant", "He's got icicles in his beard", "", 67, 66, 70, 70, true, sprites, 6724027, 8425710, 8409120, 5623807, 261, 396, 6, 6, 5, i++));
		sprites = new int[]{143, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("King Scorpion", "Wow scorpions shouldn't grow that big", "", 40, 38, 30, 39, true, sprites, 0, 0, 0, 0, 543, 312, 7, 7, 45, i++));
		sprites = new int[]{6, 1, 2, -1, 48, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Pirate", "A vicious pirate", "", 35, 25, 20, 30, true, sprites, 1, 15658615, 14483456, 15523536, 145, 220, 6, 6, 5, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{19, 34, 43, -1, 49, -1, -1, -1, -1, -1, -1, 512};
		} else {
			sprites = new int[]{19, 34, 43, -1, 49, -1, -1, -1, -1, -1, -1, -1};
		}
		npcs.add(new NPCDef("Sir Vyvin", "One of the white knights of Falador", "", 55, 60, 52, 58, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 116, -1, 84, 90, -1, -1, -1, -1};
		npcs.add(new NPCDef("Monk of Zamorak", "An evil cleric", "", 28, 32, 30, 28, true, sprites, 16761440, 65535, 255, 15392466, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, 84, 90, -1, -1, -1, -1};
		npcs.add(new NPCDef("Monk of Zamorak", "An evil cleric", "", 18, 22, 20, 18, true, sprites, 16761440, 65535, 255, 15392466, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Wayne", "An armourer", shopOption, 15, 22, 22, 6, false, sprites, 16761440, 2, 8409120, 15523536, 140, 210, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Barmaid", "a pretty barmaid", "", 35, 25, 10, 30, false, sprites, 16753488, 16777008, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Dwarven shopkeeper", "I wonder if he wants to buy any of my junk", shopOption, 20, 17, 16, 20, false, sprites, 7360576, 9465888, 9465888, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Doric", "A dwarven smith", "", 20, 17, 16, 20, false, sprites, 16753488, 9465888, 9465888, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Shopkeeper", "I wonder what he's got for sale", shopOption, 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Shop Assistant", "Maybe he'd like to buy some of my junk", shopOption, 0, 0, 3, 0, false, sprites, 6307872, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guide", "She gives hints to new adventurers", "", 0, 0, 7, 0, false, sprites, 1, 32768, 8388863, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 78, -1, 88, -1, -1, -1, 63};
		npcs.add(new NPCDef("Hetty", "A witch", "", 35, 25, 10, 30, false, sprites, 3182640, 16711680, 3, 15531728, 155, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 78, -1, 88, -1, -1, -1, -1};
		npcs.add(new NPCDef("Betty", "A witch", shopOption, 35, 25, 10, 30, false, sprites, 1, 16711680, 3, 15523536, 155, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bartender", "I could get a beer off him", "", 0, 0, 3, 0, false, sprites, 1, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{142, 141, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("General wartface", "An ugly green creature", "", 16, 14, 12, 13, false, sprites, 0, 0, 0, 0, 264, 250, 9, 8, 5, i++));
		sprites = new int[]{142, 140, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("General Bentnoze", "An ugly green creature", "", 16, 14, 12, 13, false, sprites, 0, 0, 0, 0, 264, 250, 9, 8, 5, i++));
		sprites = new int[]{142, 140, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Goblin", "An ugly green creature", "", 16, 14, 12, 13, true, sprites, 0, 0, 0, 0, 219, 206, 9, 8, 5, i++));
		sprites = new int[]{142, 141, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Goblin", "An ugly green creature", "", 16, 14, 12, 13, true, sprites, 0, 0, 0, 0, 219, 206, 9, 8, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Herquin", "A gem merchant", shopOption, 0, 0, 3, 0, false, sprites, 16753488, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Rommik", "The owner of the crafting shop", shopOption, 0, 0, 3, 0, false, sprites, 16753488, 16732192, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Grum", "Grum the goldsmith", shopOption, 0, 0, 3, 0, false, sprites, 16753488, 7368816, 7368816, 15523536, 130, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 44, 100, 118, -1, 35, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ice warrior", "A strange inhuman warrior", "", 57, 56, 59, 59, true, sprites, 6724027, 8425710, 8425710, 5623807, 150, 250, 6, 6, 5, i++));
		sprites = new int[]{3, 56, 38, -1, 109, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Warrior", "A skilled fighter", "pickpocket", 35, 25, 20, 30, true, sprites, 16753488, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Thrander", "A smith of some sort", "", 15, 22, 22, 6, false, sprites, 16761440, 2, 8409120, 15523536, 155, 230, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, 48, -1, 70, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Border Guard", "a guard from Al Kharid", "", 20, 17, 19, 18, false, sprites, 1, 13385881, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, 48, -1, 70, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Border Guard", "a guard from Al Kharid", "", 20, 17, 19, 18, false, sprites, 1, 13385881, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 4, 38, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Customs Officer", "She is here to stop smugglers", "", 23, 12, 15, 14, false, sprites, 1, 2, 3, 9461792, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Luthas", "The owner of the banana plantation", "", 23, 12, 15, 14, false, sprites, 1, 2, 3, 9461792, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Zambo", "He will sell me exotic rum", shopOption, 23, 12, 15, 14, false, sprites, 13398064, 3198139, 3, 9461792, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Captain Tobias", "An old sailor", "", 20, 20, 20, 20, false, sprites, 16777215, 255, 255, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Gerrant", "I wonder what he's got for sale", shopOption, 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Shopkeeper", "I wonder what he's got for sale", shopOption, 0, 0, 3, 0, false, sprites, 1, 8409120, 8409120, 9461792, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Shop Assistant", "Maybe he'd like to buy some of my junk", shopOption, 0, 0, 3, 0, false, sprites, 1, 8409120, 8409120, 9461792, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Seaman Lorris", "A young sailor", "", 20, 20, 20, 20, false, sprites, 16752704, 255, 255, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Seaman Thresnor", "A young sailor", "", 20, 20, 20, 20, false, sprites, 16752704, 255, 255, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Tanner", "He makes leather", "", 20, 60, 60, 40, false, sprites, 16761440, 8409120, 8409120, 13415270, 125, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Dommik", "The owner of the crafting shop", shopOption, 0, 0, 3, 0, false, sprites, 16753488, 16732192, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, 83, 87, -1, -1, -1, -1};
		npcs.add(new NPCDef("Abbot Langley", "A Peaceful monk", "", 12, 13, 15, 12, false, sprites, 16761440, 65535, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Thordur", "He runs a a tourist attraction", "", 20, 17, 16, 20, false, sprites, 7360576, 9465888, 9465888, 15523536, 121, 176, 6, 6, 5, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{7, 1, 2, -1, -1, -1, 83, 87, -1, -1, -1, 516};
		} else {
			sprites = new int[]{7, 1, 2, -1, -1, -1, 83, 87, -1, -1, -1, -1};
		}
		npcs.add(new NPCDef("Brother Jered", "human", "", 12, 13, 15, 12, false, sprites, 16761440, 65535, 255, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{123, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Rat", "Overgrown vermin", "", 16, 12, 10, 15, true, sprites, 0, 0, 0, 0, 346, 136, 7, 7, 45, i++));
		sprites = new int[]{137, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ghost", "Ooh spooky", "", 23, 30, 25, 23, true, sprites, 0, 0, 0, 0, 201, 243, 9, 9, 5, i++));
		sprites = new int[]{134, 133, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("skeleton", "it rattles when it walks", "", 32, 30, 29, 35, true, sprites, 0, 0, 0, 0, 216, 234, 11, 11, 5, i++));
		sprites = new int[]{136, 135, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("zombie", "the living dead", "", 32, 31, 30, 35, true, sprites, 0, 0, 0, 0, 174, 259, 12, 12, 5, i++));
		sprites = new int[]{124, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Lesser Demon", "Lesser but still very big", "", 78, 79, 79, 80, true, sprites, 0, 0, 0, 0, 275, 262, 11, 11, 30, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 82, 88, -1, -1, -1, -1};
		npcs.add(new NPCDef("Melzar the mad", "He looks totally insane", "", 47, 44, 44, 47, true, sprites, 1, 2, 3, 16776944, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Scavvo", "He has lopsided eyes", shopOption, 10, 10, 10, 10, false, sprites, 15921906, 7356480, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{124, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Greater Demon", "big red and incredibly evil", "", 86, 87, 87, 88, true, sprites, 0, 0, 0, 0, 358, 341, 11, 11, 30, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		if (Config.S_WANT_OPENPK_POINTS) {
			npcs.add(new NPCDef("Points Shopkeeper", "She will buy my points and sell me stuff", shopOption, 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 13415270, 120, 220, 6, 6, 5, i++));
		} else {
			npcs.add(new NPCDef("Shopkeeper", "I wonder what he's got for sale", shopOption, 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		}
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		if (Config.S_WANT_OPENPK_POINTS) {
			npcs.add(new NPCDef("Points Shopkeeper", "He will buy my points and sell me stuff", shopOption, 0, 0, 3, 0, false, sprites, 6307872, 8409120, 8409120, 13415270, 145, 220, 6, 6, 5, i++));
		} else {
			npcs.add(new NPCDef("Shop Assistant", "Maybe he'd like to buy some of my junk", shopOption, 0, 0, 3, 0, false, sprites, 6307872, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		}
		sprites = new int[]{3, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Oziach", "A strange little man", shopOption, 0, 0, 3, 0, false, sprites, 6307872, 8440864, 8440864, 15523536, 145, 205, 6, 6, 5, i++));
		sprites = new int[]{131, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bear", "Eek! A bear!", "", 27, 25, 27, 28, true, sprites, 0, 0, 0, 0, 262, 247, 6, 9, 30, i++));
		sprites = new int[]{18, 32, 42, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Black Knight", "An armoured follower of Zamorak", "", 45, 50, 42, 48, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 110, 71, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("chaos Dwarf", "a dwarf gone bad", "", 58, 59, 61, 60, true, sprites, 14495808, 14495808, 14495808, 14495808, 135, 185, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("dwarf", "A dwarf who looks after the mining guild", "", 20, 17, 16, 20, false, sprites, 7360576, 9465888, 9465888, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{139, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Wormbrain", "Dumb even by goblin standards", "", 8, 9, 5, 9, true, sprites, 0, 0, 0, 0, 219, 206, 9, 8, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, 46, -1, -1, -1, -1};
		npcs.add(new NPCDef("Klarense", "A young sailor", "", 20, 20, 20, 20, false, sprites, 16752704, 221, 4, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ned", "An old sailor", "", 20, 20, 20, 20, false, sprites, 16777215, 255, 3, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{134, 133, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("skeleton", "A Taller than normal skeleton", "", 52, 50, 59, 55, true, sprites, 0, 0, 0, 0, 259, 281, 11, 11, 12, i++));
		sprites = new int[]{144, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Dragon", "A powerful and ancient dragon", "", 110, 110, 110, 110, true, sprites, 0, 0, 0, 0, 452, 326, 10, 7, 70, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Oracle", "A mystic of unknown race", "", 57, 56, 59, 59, false, sprites, 6724027, 14544622, 14544622, 5619694, 110, 180, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, 80, 64};
		npcs.add(new NPCDef("Duke of Lumbridge", "Duke Horacio of Lumbridge", "", 15, 60, 30, 15, false, sprites, 1, 16711680, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 32, 42, -1, 114, 75, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Dark Warrior", "A warrior touched by chaos", "", 20, 25, 17, 23, true, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 85, 86, -1, -1, -1, -1};
		npcs.add(new NPCDef("Druid", "A worshipper of Guthix", "", 28, 32, 30, 28, true, sprites, 16777215, 65535, 255, 15392466, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{145, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Red Dragon", "A big powerful dragon", "", 140, 140, 140, 140, true, sprites, 0, 0, 0, 0, 452, 326, 10, 7, 70, i++));
		sprites = new int[]{146, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Blue Dragon", "A mother dragon", "", 105, 105, 105, 105, true, sprites, 0, 0, 0, 0, 452, 326, 10, 7, 70, i++));
		sprites = new int[]{146, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Baby Blue Dragon", "Young but still dangerous", "", 50, 50, 50, 50, true, sprites, 0, 0, 0, 0, 226, 163, 10, 7, 30, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{6, 1, 2, -1, -1, -1, 85, 86, -1, -1, -1, 514};
		} else {
			sprites = new int[]{6, 1, 2, -1, -1, -1, 85, 86, -1, -1, -1, -1};
		}
		npcs.add(new NPCDef("Kaqemeex", "A wise druid", "", 28, 32, 30, 28, false, sprites, 14540253, 65535, 255, 15392466, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 85, 86, -1, -1, -1, -1};
		npcs.add(new NPCDef("Sanfew", "An old druid", "", 28, 32, 30, 28, false, sprites, 16777215, 65535, 255, 15392466, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{-1, 28, 37, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Suit of armour", "A dusty old suit of armour", "", 30, 30, 29, 28, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 83, 87, -1, -1, -1, -1};
		npcs.add(new NPCDef("Adventurer", "A cleric", "", 12, 13, 15, 12, false, sprites, 16753248, 65535, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, 122, -1, 77, 76, 81, -1, -1, -1, -1};
		npcs.add(new NPCDef("Adventurer", "A wizard", "", 20, 15, 3, 10, false, sprites, 1, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 28, 37, -1, 110, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Adventurer", "A Warrior", "", 55, 60, 52, 58, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, 107, -1, -1, -1, 46, -1, 80, -1};
		npcs.add(new NPCDef("Adventurer", "An archer", "", 39, 39, 39, 39, false, sprites, 16753488, 15645504, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Leprechaun", "A funny little man who lives in a tree", "", 20, 17, 16, 20, false, sprites, 5271616, 5286432, 5286432, 15523536, 103, 141, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 83, 87, -1, -1, -1, -1};
		npcs.add(new NPCDef("Monk of entrana", "A Peaceful monk", "", 12, 13, 15, 12, false, sprites, 16761440, 65535, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 83, 87, -1, -1, -1, -1};
		npcs.add(new NPCDef("Monk of entrana", "A Peaceful monk", "", 12, 13, 15, 12, false, sprites, 16761440, 65535, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{136, 135, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("zombie", "The living dead", "", 32, 31, 30, 35, true, sprites, 0, 0, 0, 0, 174, 259, 12, 12, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 83, 87, -1, -1, -1, -1};
		npcs.add(new NPCDef("Monk of entrana", "A Peaceful monk", "", 12, 13, 15, 12, false, sprites, 16761440, 65535, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{137, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("tree spirit", "Ooh spooky", "", 100, 90, 85, 105, true, sprites, 0, 0, 0, 0, 241, 292, 9, 9, 5, i++));
		sprites = new int[]{128, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("cow", "It's a dairy cow", "", 9, 8, 8, 9, false, sprites, 0, 0, 0, 0, 327, 240, 6, 6, 45, i++));
		sprites = new int[]{78, 82, 88, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Irksol", "Is he invisible or just a set of floating clothes?", shopOption, 2, 2, 3, 2, false, sprites, 3158064, 16711680, 16711680, 9461792, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Fairy Lunderwin", "A fairy merchant", "", 2, 2, 3, 2, false, sprites, 3158064, 16711680, 16711680, 9461792, 94, 143, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Jakut", "An unusual looking merchant", shopOption, 2, 2, 3, 2, false, sprites, 3180748, 65280, 65280, 9461792, 145, 260, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 37, -1, 110, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Doorman", "He guards the entrance to the faerie market", "", 55, 60, 52, 58, false, sprites, 3189418, 3170508, 3206894, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Fairy Shopkeeper", "I wonder what he's got for sale", shopOption, 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 15523536, 94, 143, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Fairy Shop Assistant", "Maybe he'd like to buy some of my junk", shopOption, 0, 0, 3, 0, false, sprites, 6307872, 8409120, 8409120, 15523536, 94, 143, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Fairy banker", "He can look after my money", bankerOption1, bankerOption2, 11, 8, 7, 11, false, sprites, 15921906, 2, 3, 15523536, 94, 143, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Giles", "He runs an ore exchange store", "", 30, 30, 30, 30, false, sprites, 1, 255, 14508096, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Miles", "He runs a bar exchange store", "", 30, 30, 30, 30, false, sprites, 16772761, 255, 14508096, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Niles", "He runs a fish exchange store", "", 30, 30, 30, 30, false, sprites, 15921906, 255, 14508096, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Gaius", "he sells very big swords", shopOption, 15, 22, 22, 6, false, sprites, 16761440, 2, 8409120, 15523536, 155, 230, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Fairy Ladder attendant", "A worker in the faerie market", "", 0, 0, 3, 0, false, sprites, 16761440, 8409120, 8409120, 15523536, 94, 143, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 85, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Jatix", "A hard working druid", shopOption, 28, 32, 30, 28, false, sprites, 11184810, 65535, 16777215, 15392466, 145, 220, 6, 6, 5, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, 440};
		} else {
			sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		}
		npcs.add(new NPCDef("Master Crafter", "The man in charge of the crafter's guild", "", 0, 0, 3, 0, false, sprites, 16753488, 16732192, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, 49, -1, -1, -1, -1, -1, -1, -1, 63};
		npcs.add(new NPCDef("Bandit", "He's ready for a fight", "", 32, 33, 27, 26, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, 117, -1, -1, -1, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("Noterazzo", "A bandit shopkeeper", shopOption, 32, 33, 27, 26, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, 49, -1, -1, -1, -1, -1, -1, -1, 63};
		npcs.add(new NPCDef("Bandit", "A wilderness outlaw", "", 32, 33, 27, 26, true, sprites, 1, 221, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 8, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Fat Tony", "A Gourmet Pizza chef", shopOption, 20, 20, 3, 20, false, sprites, 1, 16777215, 16711680, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, 49, 98, -1, -1, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("Donny the lad", "A bandit leader", "", 42, 43, 37, 36, true, sprites, 16752704, 8060928, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, 109, 98, -1, -1, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("Black Heather", "A bandit leader", "", 42, 43, 37, 36, true, sprites, 1, 8060928, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, 49, -1, -1, 22, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("Speedy Keith", "A bandit leader", "", 42, 43, 37, 36, true, sprites, 16752704, 8060928, 3, 15523536, 150, 230, 6, 6, 5, i++));
		sprites = new int[]{147, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("White wolf sentry", "A vicious mountain wolf", "", 30, 32, 34, 31, true, sprites, 0, 0, 0, 0, 260, 198, 6, 10, 30, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Boy", "He doesn't seem very happy", "", 42, 43, 37, 36, false, sprites, 16752704, 8060928, 3, 15523536, 100, 147, 6, 6, 5, i++));
		sprites = new int[]{123, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Rat", "He seems to live here", "", 3, 4, 2, 2, false, sprites, 0, 0, 0, 0, 115, 45, 7, 7, 10, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 78, -1, 88, -1, -1, -1, 63};
		npcs.add(new NPCDef("Nora T Hag", "She's got warts", "", 35, 25, 10, 30, false, sprites, 1, 2, 3, 15527632, 155, 220, 6, 6, 5, i++));
		sprites = new int[]{148, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Grey wolf", "A sinister looking wolf", "", 60, 62, 69, 65, true, sprites, 0, 0, 0, 0, 260, 198, 6, 10, 30, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, 84, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("shapeshifter", "I've not seen anyone like this before", "", 28, 29, 21, 20, true, sprites, 14495808, 14495808, 14495808, 14495808, 150, 185, 6, 6, 5, i++));
		sprites = new int[]{125, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("shapeshifter", "I think this spider has been genetically modified", "", 38, 39, 31, 30, false, sprites, 0, 0, 0, 0, 120, 104, 6, 6, 5, i++));
		sprites = new int[]{131, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("shapeshifter", "Eek! A bear!", "", 48, 49, 41, 40, false, sprites, 0, 0, 0, 0, 262, 247, 6, 9, 30, i++));
		sprites = new int[]{148, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("shapeshifter", "A sinister looking wolf", "", 58, 59, 51, 50, false, sprites, 0, 0, 0, 0, 260, 198, 6, 10, 30, i++));
		sprites = new int[]{147, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("White wolf", "A vicious mountain wolf", "", 40, 42, 44, 41, true, sprites, 0, 0, 0, 0, 260, 198, 6, 10, 30, i++));
		sprites = new int[]{147, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Pack leader", "A vicious mountain wolf", "", 70, 72, 74, 71, true, sprites, 0, 0, 0, 0, 312, 238, 6, 10, 30, i++));

		//loadNpcDefinitionsB();
		//`sprites1`='" + npc.sprites[0] + "',`sprites2`='" + npc.sprites[1] + "',`sprites3`='" + npc.sprites[2] + "',`sprites4`='" + npc.sprites[3] + "',`sprites5`='" + npc.sprites[4] + "',`sprites6`='" + npc.sprites[5] + "',`sprites7`='" + npc.sprites[6] + "',`sprites8`='" + npc.sprites[7] + "',`sprites9`='" + npc.sprites[8] + "',`sprites10`='" + npc.sprites[9] + "',`sprites11`='" + npc.sprites[10] + "',`sprites12`='" + npc.sprites[11] + "'
		/*try {
			PrintWriter printWriter = new PrintWriter(new FileOutputStream("newNpcDef.txt"), true);
			int count = 0;
			for(NPCDef npc : npcs) {
				int npcCombat = (npc.getStr()
						+ npc.getAtt()
						+ npc.getDef()
						+ npc.getHits()) / 4;
				printWriter.println("UPDATE `npcdef` SET `name`='" + npc.getName().replace("'", "''") + "',`description`='" + npc.getDescription().replace("'", "''") + "', " + (npc.getCommand().isEmpty() ? "" : "`command`='" + npc.getCommand() + "',") + "`attack`='" + npc.getAtt() + "',`strength`='" + npc.getStr() + "',`hits`='" + npc.getHits() + "',`defense`='" + npc.getDef() + "',`combatlvl`='" + npcCombat + "',`attackable`=" + (npc.isAttackable() ? "'1'" : "'0'") + ", `sprites1`='" + npc.sprites[0] + "',`sprites2`='" + npc.sprites[1] + "',`sprites3`='" + npc.sprites[2] + "',`sprites4`='" + npc.sprites[3] + "',`sprites5`='" + npc.sprites[4] + "',`sprites6`='" + npc.sprites[5] + "',`sprites7`='" + npc.sprites[6] + "',`sprites8`='" + npc.sprites[7] + "',`sprites9`='" + npc.sprites[8] + "',`sprites10`='" + npc.sprites[9] + "',`sprites11`='" + npc.sprites[10] + "',`sprites12`='" + npc.sprites[11] + "', `hairColour`='" + npc.getHairColour() + "',`topColour`='" + npc.getTopColour() + "', `bottomColour`='" + npc.bottomColour + "',`skinColour`='" + npc.getSkinColour() + "',`camera1`='" + npc.getCamera1() + "',`camera2`='" + npc.getCamera2() + "',`walkModel`='" + npc.getWalkModel() + "',`combatModel`='" + npc.getCombatModel() + "',`combatSprite`='" + npc.getCombatSprite() + "' WHERE `id`='" + npc.id + "';");

				printWriter.flush();
				count++;
			}
			printWriter.close();
			System.out.println("NPCS TOTAL: " + count);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}*/
	}


	private static void loadNpcDefinitions2() {
		int[] sprites;
		int i = npcs.size() - 1;

		/* Configurable NPC Data */
		String bankerOption1 = Config.S_RIGHT_CLICK_BANK ? "Bank" : ""; // Banker right click bank
		String bankerOption2 = Config.S_SPAWN_AUCTION_NPCS ? "Collect" : null; // Auction collect banker
		String shopOption = Config.S_RIGHT_CLICK_TRADE ? "Trade" : ""; // Shop right click trade

		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Harry", "I wonder what he's got for sale", shopOption, 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Thug", "He likes hitting things", "", 19, 20, 18, 17, true, sprites, 1, 2, 255, 15523536, 155, 230, 6, 6, 5, i++));
		sprites = new int[]{156, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Firebird", "Probably not a chicken", "", 6, 7, 5, 7, true, sprites, 0, 0, 0, 0, 70, 62, 6, 6, 5, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{3, 59, 41, 102, 113, 74, -1, -1, -1, -1, -1, 384};
		} else {
			sprites = new int[]{3, 59, 41, 102, 113, 74, -1, -1, -1, -1, -1, -1};
		}
		npcs.add(new NPCDef("Achetties", "One of Asgarnia's greatest heros", "", 45, 50, 42, 48, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 4, 44, 100, 118, -1, -1, -1, 155, -1, -1, 64};
		npcs.add(new NPCDef("Ice queen", "The leader of the ice warriors", "", 105, 101, 104, 104, true, sprites, 6724027, 8425710, 8425710, 5623807, 150, 250, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Grubor", "A rough looking thief", "", 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 15523536, 150, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, 51, -1, -1, -1, -1, -1, -1, 79, 67};
		npcs.add(new NPCDef("Trobert", "A well dressed thief", "", 14, 15, 13, 13, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, 110, -1, 75, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Garv", "A diligent guard", "", 31, 30, 22, 31, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 48, 163, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("guard", "A vicious pirate", "", 35, 25, 20, 30, false, sprites, 1, 15658615, 14483456, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 42, 110, -1, 75, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Grip", "Scar face petes head guard", "", 31, 60, 62, 31, true, sprites, 1, 2, 3, 15523536, 152, 231, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Alfonse the waiter", "He should get a clean apron", shopOption, 11, 8, 7, 11, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 8, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Charlie the cook", "Head cook of the Shrimp and parrot", "", 20, 20, 3, 20, false, sprites, 1, 16777215, 15641122, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{159, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guard Dog", "He doesn't seem pleased to see me", "", 45, 47, 49, 46, true, sprites, 0, 0, 0, 0, 247, 188, 6, 10, 30, i++));
		sprites = new int[]{160, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ice spider", "I think this spider has been genetically modified", "", 60, 66, 65, 65, true, sprites, 0, 0, 0, 0, 132, 114, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 48, 163, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Pirate", "A vicious pirate", "", 38, 28, 23, 33, true, sprites, 1, 7829248, 6684672, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 32, 42, -1, 114, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Jailer", "Guards prisoners for the black knights", "", 50, 55, 47, 53, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 32, 42, 103, 53, -1, -1, -1, -1, -1, -1, 63};
		npcs.add(new NPCDef("Lord Darquarius", "A black knight commander", "", 75, 80, 72, 78, true, sprites, 1, 2, 3, 9461792, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Seth", "He runs a fish exchange store", "", 30, 30, 30, 30, false, sprites, 15921906, 255, 14508096, 9461792, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Banker", "He can look after my money", bankerOption1, bankerOption2, 11, 8, 7, 11, false, sprites, 15921906, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 33, 41, 102, 52, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Helemos", "A retired hero", shopOption, 45, 50, 42, 48, false, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 84, 90, -1, -1, -1, -1};
		npcs.add(new NPCDef("Chaos Druid", "A crazy evil druid", "", 18, 22, 20, 18, true, sprites, 16777215, 65535, 255, 15392466, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{143, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Poison Scorpion", "It has a very vicious looking tail", "", 26, 29, 23, 27, true, sprites, 0, 0, 0, 0, 362, 208, 7, 7, 45, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Velrak the explorer", "he looks cold and hungry", "", 3, 3, 3, 3, false, sprites, 1, 16711680, 8952166, 9461792, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 34, 43, 101, 49, -1, -1, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("Sir Lancelot", "A knight of the round table", "", 55, 60, 52, 58, false, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 34, 43, 100, 49, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Sir Gawain", "A knight of the round table", "", 55, 60, 52, 58, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 34, 43, -1, 49, 150, -1, -1, -1, -1, -1, 68};
		npcs.add(new NPCDef("King Arthur", "A wise old king", "", 55, 60, 52, 58, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{18, 32, 42, 103, 53, -1, -1, -1, -1, -1, -1, 64};
		npcs.add(new NPCDef("Sir Mordred", "An evil knight", "", 57, 62, 54, 60, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{18, 32, 42, -1, 53, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Renegade knight", "He isn't very friendly", "", 50, 55, 48, 53, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 48, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Davon", "An amulet trader", shopOption, 35, 25, 20, 30, false, sprites, 1, 15658615, 10289152, 11312784, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, 163, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bartender", "I could get some grog off him", "", 0, 0, 3, 0, false, sprites, 1, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Arhein", "A merchant", shopOption, 0, 0, 3, 0, false, sprites, 3158064, 13381836, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, 122, -1, -1, 88, -1, -1, -1, 63};
		npcs.add(new NPCDef("Morgan le faye", "An evil sorceress", "", 35, 25, 10, 30, false, sprites, 1, 2, 3, 15527632, 155, 220, 6, 6, 5, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, 513};
		} else {
			sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		}
		npcs.add(new NPCDef("Candlemaker", "He makes and sells candles", shopOption, 15, 22, 22, 6, false, sprites, 16761440, 2, 8409120, 15523536, 155, 230, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("lady", "She has a hint of magic about her", "", 0, 0, 3, 0, false, sprites, 15921906, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("lady", "She has a hint of magic about her", "", 0, 0, 3, 0, false, sprites, 15921906, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("lady", "She has a hint of magic about her", "", 0, 0, 3, 0, false, sprites, 15921906, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Beggar", "A scruffy looking chap", "", 9, 8, 5, 7, false, sprites, 16768256, 2, 3, 15523536, 135, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 122, 77, 76, 81, -1, -1, -1, -1};
		npcs.add(new NPCDef("Merlin", "An old wizard", "", 20, 15, 3, 10, false, sprites, 16777215, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{124, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Thrantax", "A freshly summoned demon", "", 90, 90, 90, 90, false, sprites, 0, 0, 0, 0, 358, 341, 11, 11, 30, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, 429};
		} else {
			sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		}
		npcs.add(new NPCDef("Hickton", "The owner of the archery store", shopOption, 0, 0, 3, 0, false, sprites, 8409136, 14483456, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{164, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Black Demon", "A big scary jet black demon", "", 155, 157, 157, 158, true, sprites, 0, 0, 0, 0, 358, 341, 11, 11, 30, i++));
		sprites = new int[]{165, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Black Dragon", "A fierce dragon with black scales!", "", 210, 190, 190, 210, true, sprites, 0, 0, 0, 0, 542, 391, 10, 7, 84, i++));
		sprites = new int[]{166, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Poison Spider", "I think this spider has been genetically modified", "", 60, 62, 64, 68, true, sprites, 0, 0, 0, 0, 180, 156, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 122, -1, 84, 90, -1, -1, -1, -1};
		npcs.add(new NPCDef("Monk of Zamorak", "An evil cleric", "", 48, 52, 40, 48, true, sprites, 16761440, 65535, 255, 15392466, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{167, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Hellhound", "Hello nice doggy", "", 115, 112, 116, 114, true, sprites, 0, 0, 0, 0, 312, 237, 6, 10, 36, i++));
		sprites = new int[]{-1, -1, -1, -1, 109, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Animated axe", "a magic axe with a mind of it's own", "", 48, 45, 44, 48, true, sprites, 15645552, 8409120, 8409120, 15523536, 160, 230, 6, 6, 5, i++));
		sprites = new int[]{168, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Black Unicorn", "It's a sort of unicorn", "", 31, 33, 29, 33, true, sprites, 0, 0, 0, 0, 201, 230, 6, 6, 7, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 83, 87, -1, -1, -1, -1};
		npcs.add(new NPCDef("Frincos", "A Peaceful monk", shopOption, 12, 13, 15, 12, false, sprites, 16761440, 65535, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{78, 82, 88, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Otherworldly being", "Is he invisible or just a set of floating clothes?", "", 66, 66, 66, 66, true, sprites, 3158064, 16711680, 16711680, 9461792, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Owen", "He runs a fish exchange store", "", 30, 30, 30, 30, false, sprites, 16772761, 255, 14508096, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 82, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Thormac the sorceror", "A powerful sorcerrer", "", 27, 24, 24, 27, false, sprites, 1, 2, 3, 10056486, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 76, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Seer", "An old wizard", "", 18, 15, 14, 18, false, sprites, 16777215, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{143, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Kharid Scorpion", "a smaller less dangerous scorpion", "", 21, 24, 17, 22, false, sprites, 0, 0, 0, 0, 121, 69, 7, 7, 45, i++));
		sprites = new int[]{143, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Kharid Scorpion", "a smaller less dangerous scorpion", "", 21, 24, 17, 22, false, sprites, 0, 0, 0, 0, 121, 69, 7, 7, 45, i++));
		sprites = new int[]{143, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Kharid Scorpion", "a smaller less dangerous scorpion", "", 21, 24, 17, 22, false, sprites, 0, 0, 0, 0, 121, 69, 7, 7, 45, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 28, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Barbarian guard", "Not very civilised", "", 18, 15, 14, 18, false, sprites, 15645552, 8409120, 8409120, 15523536, 160, 230, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bartender", "I could get a beer off him", "", 0, 0, 3, 0, false, sprites, 1, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 79, 66};
		npcs.add(new NPCDef("man", "A well dressed nobleman", "", 11, 8, 7, 11, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("gem trader", "He sells gems", shopOption, 0, 0, 3, 0, false, sprites, 3158064, 3211212, 3211212, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, 79, 62};
		npcs.add(new NPCDef("Dimintheis", "A well dressed nobleman", "", 11, 8, 7, 11, false, sprites, 1, 7811157, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 8, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("chef", "A busy looking chef", "", 20, 20, 3, 20, false, sprites, 1, 16777215, 14540032, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{142, 140, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Hobgoblin", "An ugly green creature", "", 49, 47, 49, 48, true, sprites, 0, 0, 0, 0, 314, 295, 9, 8, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 117, -1, 45, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Ogre", "A large dim looking humanoid", "", 72, 33, 60, 70, true, sprites, 11550752, 6299664, 6299664, 10056486, 212, 280, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 20, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Boot the Dwarf", "A short angry guy", "", 20, 17, 16, 20, false, sprites, 7360576, 10502176, 8409120, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{3, 1, 2, -1, 122, -1, 76, 81, -1, -1, -1, -1};
		npcs.add(new NPCDef("Wizard", "A young wizard", "", 18, 15, 14, 18, false, sprites, 16777215, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{169, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Chronozon", "Chronozon the blood demon", "", 183, 60, 60, 182, true, sprites, 0, 0, 0, 0, 358, 341, 11, 11, 30, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Captain Barnaby", "An old sailor", "", 20, 20, 20, 20, false, sprites, 16777215, 255, 255, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 4, 38, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Customs Official", "She's here to stop smugglers", "", 23, 12, 15, 14, false, sprites, 1, 2, 3, 9461792, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Man", "One of Runescape's citizens", "pickpocket", 11, 8, 7, 11, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("farmer", "An humble peasant", "pickpocket", 15, 16, 12, 18, true, sprites, 11167296, 8409120, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 56, 38, -1, 109, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Warrior", "A skilled fighter", "pickpocket", 35, 25, 20, 30, true, sprites, 16753488, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 69, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guard", "He tries to keep the law and order around here", "pickpocket", 31, 30, 22, 31, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{19, 34, 43, -1, 49, -1, -1, -1, -1, -1, -1, 64};
		npcs.add(new NPCDef("Knight", "A knight of Ardougne", "pickpocket", 55, 60, 52, 58, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 34, 43, 101, 119, -1, -1, -1, -1, -1, -1, 66};
		npcs.add(new NPCDef("Paladin", "A paladin of Ardougne", "pickpocket", 85, 55, 57, 88, true, sprites, 16760880, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 33, 41, 102, 52, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Hero", "A Hero of Ardougne", "pickpocket", 85, 80, 82, 88, true, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Baker", "He sells hot baked bread", shopOption, 20, 20, 3, 20, false, sprites, 1, 16777215, 8912896, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("silk merchant", "He buys silk", "", 0, 0, 3, 0, false, sprites, 3158064, 16724172, 16724172, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, 67};
		npcs.add(new NPCDef("Fur trader", "A buyer and seller of animal furs", shopOption, 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, 66};
		npcs.add(new NPCDef("silver merchant", "He deals in silver", shopOption, 0, 0, 3, 0, false, sprites, 3158064, 16764108, 16764108, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("spice merchant", "He sells exotic spices", shopOption, 20, 20, 3, 20, false, sprites, 1, 16777215, 8912896, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("gem merchant", "He sells gems", shopOption, 0, 0, 3, 0, false, sprites, 3158064, 3211212, 3211212, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, 56, 93, -1, -1, -1, -1};
		npcs.add(new NPCDef("Zenesha", "A shopkeeper of some sort", shopOption, 35, 25, 10, 30, false, sprites, 16753488, 14518442, 3, 15523536, 145, 235, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Kangai Mau", "A tribesman", "", 0, 0, 3, 0, false, sprites, 1, 9461792, 16724016, 9461792, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, 122, -1, 77, 76, 81, -1, -1, -1, -1};
		npcs.add(new NPCDef("Wizard Cromperty", "An old wizard", "", 20, 15, 3, 10, false, sprites, 16777215, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("RPDT employee", "A delivery man", "", 12, 12, 13, 12, false, sprites, 3158064, 170, 170, 9461792, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Horacio", "An old gardener", "", 10, 8, 7, 8, false, sprites, 16777215, 8947848, 8409120, 15523536, 155, 230, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Aemad", "He helps run the adventurers store", shopOption, 15, 22, 22, 6, false, sprites, 16761440, 2, 8409120, 15523536, 155, 230, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Kortan", "He helps run the adventurers store", shopOption, 15, 22, 22, 6, false, sprites, 16761440, 2, 8409120, 15523536, 155, 230, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("zoo keeper", "He looks after Ardougne city zoo", "", 20, 20, 20, 20, true, sprites, 16752704, 187, 187, 15523536, 160, 220, 6, 6, 5, i++));
		if (Config.S_PRIDE_MONTH) {
			sprites = new int[]{6, 1, 2, -1, 122, -1, 84, -1, -1, -1, -1, 509};
		} else {
			sprites = new int[]{6, 1, 2, -1, 122, -1, 84, -1, -1, -1, -1, 68};
		}
		npcs.add(new NPCDef("Make over mage", "He can change how I look", "", 0, 0, 3, 0, false, sprites, 3158064, 16763952, 15609986, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bartender", "I could get a beer off him", "", 0, 0, 3, 0, false, sprites, 1, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("chuck", "A wood merchant", "", 0, 0, 3, 0, false, sprites, 16753488, 16732192, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Rogue", "He needs a shave", "pickpocket", 24, 22, 17, 23, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{170, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Shadow spider", "Is it a spider or is it a shadow", "", 54, 51, 55, 52, true, sprites, 0, 0, 0, 0, 132, 114, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 122, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Fire Giant", "A big guy with red glowing skin", "", 110, 112, 111, 105, true, sprites, 12255232, 16742195, 16742195, 16724787, 261, 396, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 122, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Grandpa Jack", "A wistful old man", "", 20, 20, 20, 20, false, sprites, 15658734, 12277060, 3, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, 63};
		npcs.add(new NPCDef("Sinister stranger", "not your average fisherman", "", 40, 65, 35, 35, false, sprites, 1, 2, 3, 16576224, 140, 240, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bonzo", "Fishing competition organiser", "", 30, 30, 30, 30, false, sprites, 1, 16711680, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 107, -1, 45, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Forester", "He looks after McGrubor's wood", "", 24, 22, 17, 23, true, sprites, 1, 56576, 43520, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Morris", "Fishing competition organiser", "", 30, 30, 30, 30, false, sprites, 1, 16711680, 3, 15523536, 150, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, 83, 87, -1, -1, -1, -1};
		npcs.add(new NPCDef("Brother Omad", "A Peaceful monk", "", 12, 13, 15, 12, false, sprites, 16761440, 65535, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, 48, -1, 45, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Thief", "A dastardly blanket thief", "", 24, 22, 17, 23, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 50, -1, 45, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Head Thief", "A dastardly blanket thief", "", 34, 32, 37, 33, true, sprites, 1, 2, 3, 15523536, 150, 230, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Big Dave", "A well built fisherman", "", 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 15523536, 165, 242, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Joshua", "A grumpy fisherman", "", 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 15523536, 145, 230, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Mountain Dwarf", "A short angry guy", "", 20, 17, 16, 20, false, sprites, 1, 7360544, 7360544, 15523536, 130, 180, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Mountain Dwarf", "A short angry guy", "", 30, 27, 26, 30, true, sprites, 1, 7360544, 7360544, 15523536, 130, 180, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, 83, 87, -1, -1, -1, -1};
		npcs.add(new NPCDef("Brother Cedric", "A Peaceful monk", "", 12, 13, 15, 12, false, sprites, 16761440, 65535, 255, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 84, 90, -1, -1, -1, -1};
		npcs.add(new NPCDef("Necromancer", "A crazy evil necromancer", "", 28, 42, 40, 28, true, sprites, 16777215, 65535, 255, 16768722, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{135, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("zombie", "The living dead", "", 23, 28, 24, 23, true, sprites, 0, 0, 0, 0, 174, 259, 12, 12, 5, i++));
		sprites = new int[]{18, 1, 2, -1, -1, -1, 82, 88, 46, 11, -1, -1};
		npcs.add(new NPCDef("Lucien", "He walks with a slight limp", "", 24, 22, 17, 23, false, sprites, 1, 2, 3, 4, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 36, 97, 108, -1, 20, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("The Fire warrior of lesarkus", "A strange red humanoid", "", 72, 50, 59, 72, true, sprites, 16750950, 15634560, 15634560, 16752469, 150, 250, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 41, -1, -1, -1, 85, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("guardian of Armadyl", "A worshipper of Armadyl", "", 58, 52, 50, 58, false, sprites, 16772778, 65535, 255, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 41, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("guardian of Armadyl", "A worshipper of Armadyl", "", 58, 52, 50, 58, false, sprites, 16772778, 16777215, 255, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{18, 1, 2, -1, -1, -1, 82, 88, 46, 11, -1, -1};
		npcs.add(new NPCDef("Lucien", "He walks with a limp", "", 24, 22, 17, 23, true, sprites, 1, 2, 3, 4, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 78, -1, 88, -1, -1, -1, 63};
		npcs.add(new NPCDef("winelda", "A witch", "", 35, 25, 10, 30, false, sprites, 1, 16711680, 3, 15531984, 155, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, 83, 87, -1, -1, -1, -1};
		npcs.add(new NPCDef("Brother Kojo", "A Peaceful monk", "", 12, 13, 15, 12, false, sprites, 16761440, 65535, 255, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{172, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Dungeon Rat", "Overgrown vermin", "", 20, 10, 12, 22, true, sprites, 0, 0, 0, 0, 346, 136, 7, 7, 45, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, 46, -1, -1, 309};
		} else {
			sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, 46, -1, -1, -1};
		}
		npcs.add(new NPCDef("Master fisher", "The man in charge of the fishing guild", "", 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 13415270, 145, 230, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Orven", "He runs a fish exchange store", "", 30, 30, 30, 30, false, sprites, 16772761, 16711680, 14508096, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Padik", "He runs a fish exchange store", "", 30, 30, 30, 30, false, sprites, 16772761, 16711680, 14508096, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Shopkeeper", "He smells of fish", shopOption, 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 88, -1, -1, -1, -1};
		npcs.add(new NPCDef("Lady servil", "She look's wealthy", "", 1, 1, 5, 1, false, sprites, 15643488, 255, 3, 15523536, 140, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 69, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guard", "It's one of General Khazard's guard's", "", 31, 30, 22, 31, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 173, 174, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guard", "It's one of General Khazard's guard's", "", 31, 30, 22, 31, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 173, 174, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guard", "It's one of General Khazard's guard's", "pickpocket", 31, 30, 22, 31, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, 117, 173, 174, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guard", "It's one of General Khazard's guard's", "pickpocket", 31, 30, 22, 31, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 45, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Jeremy Servil", "A young squire", "", 0, 0, 3, 0, false, sprites, 14535800, 2, 3, 15523536, 120, 140, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 45, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Justin Servil", "Jeremy servil's father", "", 0, 0, 3, 0, false, sprites, 12307576, 2, 3, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("fightslave joe", "He look's mistreated and weak", "", 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("fightslave kelvin", "He look's mistreated and weak", "", 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("local", "A scruffy looking chap", "", 9, 8, 5, 7, false, sprites, 16768256, 2, 3, 15523536, 135, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Khazard Bartender", "A tough looking barman", "", 0, 0, 3, 0, false, sprites, 1, 8409120, 8409120, 15523536, 155, 220, 6, 6, 5, i++));
		sprites = new int[]{18, 32, 42, 103, 53, -1, -1, -1, -1, -1, -1, 63};
		npcs.add(new NPCDef("General Khazard", "He look's real nasty", "", 75, 80, 170, 78, true, sprites, 1, 2, 3, 9461792, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 117, -1, 45, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Khazard Ogre", "Khazard's strongest ogre warrior", "", 72, 33, 60, 70, true, sprites, 11550752, 6299664, 6299664, 10056486, 212, 280, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, 117, 173, 174, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guard", "It's one of General Khazard's guard's", "", 31, 30, 22, 31, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{143, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Khazard Scorpion", "A large angry scorpion", "", 50, 48, 40, 49, true, sprites, 0, 0, 0, 0, 543, 312, 7, 7, 45, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("hengrad", "He look's mistreated and weak", "", 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{167, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bouncer", "Hello nice doggy", "", 130, 112, 116, 130, true, sprites, 0, 0, 0, 0, 312, 237, 6, 10, 36, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Stankers", "A cheerful looking fellow", "", 0, 0, 3, 0, false, sprites, 1, 8453920, 8409120, 14467993, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Docky", "An old sailor", "", 20, 20, 20, 20, false, sprites, 16777215, 255, 3, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Shopkeeper", "Maybe he'd like to buy some of my junk", shopOption, 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 150, -1, 86, -1, -1, -1, 66};
		npcs.add(new NPCDef("Fairy queen", "A very little queen", "", 2, 2, 3, 2, false, sprites, 16765040, 16777215, 16777215, 9461792, 94, 143, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 122, 77, 76, 81, -1, -1, -1, -1};
		npcs.add(new NPCDef("Merlin", "An old wizard", "", 20, 15, 3, 10, false, sprites, 16777215, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, 122, -1, 82, 88, -1, -1, -1, 63};
		npcs.add(new NPCDef("Crone", "A strange old lady", "", 35, 25, 10, 30, false, sprites, 5255248, 2, 3, 15523536, 155, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 83, 87, -1, -1, 79, -1};
		npcs.add(new NPCDef("High priest of entrana", "A Peaceful monk", "", 12, 13, 15, 12, false, sprites, 3158064, 65535, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 175, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("elkoy", "It's a tree gnome", "", 3, 3, 3, 3, false, sprites, 1, 16711680, 14535850, 36864, 90, 130, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 175, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("remsai", "It's a tree gnome", "", 3, 3, 3, 3, false, sprites, 1, 16711680, 14535850, 36864, 90, 130, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 175, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("bolkoy", "It's a tree gnome", shopOption, 3, 3, 3, 3, false, sprites, 1, 16711680, 8965256, 36864, 90, 130, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 175, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("local gnome", "It's a young tree gnome", "", 3, 3, 3, 3, true, sprites, 1, 16711680, 8973960, 36864, 90, 110, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 150, -1, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("bolren", "It's a gnome he look's important", "", 3, 3, 3, 3, false, sprites, 1, 16776960, 2280584, 36864, 100, 150, 6, 6, 5, i++));
		sprites = new int[]{18, 32, 42, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Black Knight titan", "He is blocking the way", "", 145, 150, 142, 148, true, sprites, 1, 2, 3, 15523536, 209, 314, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 175, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("kalron", "he look's lost", "", 3, 3, 3, 3, true, sprites, 1, 16711680, 8973824, 36864, 90, 110, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 83, 87, -1, -1, -1, 62};
		npcs.add(new NPCDef("brother Galahad", "A Peaceful monk", "", 12, 13, 15, 12, false, sprites, 16761440, 65535, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 175, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("tracker 1", "It's a tree gnome", "", 3, 3, 3, 3, false, sprites, 1, 16711680, 8965256, 36864, 90, 130, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 175, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("tracker 2", "It's a tree gnome", "", 3, 3, 3, 3, false, sprites, 1, 16711680, 8965256, 36864, 90, 130, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 175, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("tracker 3", "It's a tree gnome", "", 3, 3, 3, 3, false, sprites, 1, 16711680, 8965256, 36864, 90, 130, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, 98, 117, 173, 174, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Khazard troop", "It's one of General Khazard's warrior's", "", 31, 30, 22, 31, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, 108, 175, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("commander montai", "It's a tree gnome", "", 3, 3, 3, 3, false, sprites, 1, 255, 8965256, 36864, 90, 130, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, 107, 175, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("gnome troop", "It's a tree gnome trooper", "", 3, 3, 3, 3, true, sprites, 1, 16711680, 4508808, 36864, 90, 130, 6, 6, 5, i++));
		sprites = new int[]{0, 32, 42, 103, 53, -1, -1, -1, -1, -1, -1, 63};
		npcs.add(new NPCDef("khazard warlord", "He look's real nasty", "", 75, 80, 170, 78, true, sprites, 1, 2, 3, 9461792, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{19, 34, 43, 101, 49, -1, -1, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("Sir Percival", "He's covered in pieces of straw", "", 55, 60, 52, 58, false, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 122, 154, -1, -1, -1, -1, 80, 62};
		npcs.add(new NPCDef("Fisher king", "an old king", "", 15, 60, 30, 15, false, sprites, 15658734, 16711680, 16777215, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 86, -1, -1, -1, -1};
		npcs.add(new NPCDef("maiden", "She has a far away look in her eyes", "", 2, 4, 3, 2, false, sprites, 15645552, 16777215, 16777215, 15523536, 125, 225, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Fisherman", "an old fisherman", "", 15, 60, 30, 15, false, sprites, 15658734, 15636787, 11184810, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 34, 43, 101, 49, 154, -1, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("King Percival", "The new fisher king", "", 55, 60, 52, 58, false, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("unhappy peasant", "He looks tired and hungry", "", 25, 26, 22, 28, true, sprites, 11167296, 8409120, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("happy peasant", "He looks well fed and full of energy", "", 25, 26, 22, 28, true, sprites, 11167296, 8409120, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("ceril", "It's Sir ceril carnillean a local noblemen", "", 11, 8, 7, 11, false, sprites, 16777215, 255, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("butler", "It's the carnillean family butler", "", 11, 8, 7, 11, false, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, 117, 70, 21, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("carnillean guard", "It's a carnillean family guard", "", 31, 30, 22, 31, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, 97, 48, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Tribesman", "A primative warrior", "", 38, 39, 39, 40, true, sprites, 1, 9461792, 9461792, 7360528, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 86, -1, -1, -1, -1};
		npcs.add(new NPCDef("henryeta", "It's a wealthy looking woman", "", 2, 4, 3, 2, false, sprites, 15645552, 16777215, 16777215, 15523536, 125, 225, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 45, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("philipe", "It's a young well dressed boy", "", 0, 0, 3, 0, false, sprites, 14535800, 2, 3, 15523536, 120, 140, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("clivet", "A strange looking man in black ", "", 20, 20, 20, 20, false, sprites, 1, 2, 3, 13415270, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("cult member", "An suspicous looking man in black ", "", 20, 20, 20, 20, true, sprites, 1, 2, 3, 13415270, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{18, 32, 42, 103, 53, -1, -1, -1, -1, -1, -1, 63};
		npcs.add(new NPCDef("Lord hazeel", "He could do with some sun", "", 75, 80, 170, 78, true, sprites, 1, 2, 3, 9461792, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, 110, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("alomone", "A musculer looking man in black ", "", 48, 46, 20, 56, false, sprites, 3, 2, 3, 13415270, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, 98, 110, 173, 174, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Khazard commander", "It's one of General Khazard's commander's", "", 50, 50, 22, 45, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 8, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("claus", "the carnillean family cook", "", 20, 20, 3, 20, false, sprites, 1, 16777215, 16711680, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{129, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("1st plague sheep", "The sheep has the plague", "", 0, 0, 3, 0, false, sprites, 0, 0, 0, 0, 170, 124, 6, 6, 5, i++));
		sprites = new int[]{129, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("2nd plague sheep", "The sheep has the plague", "", 0, 0, 3, 0, false, sprites, 0, 0, 0, 0, 170, 124, 6, 6, 5, i++));
		sprites = new int[]{129, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("3rd plague sheep", "The sheep has the plague", "", 0, 0, 3, 0, false, sprites, 0, 0, 0, 0, 170, 124, 6, 6, 5, i++));
		sprites = new int[]{129, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("4th plague sheep", "The sheep has the plague", "", 0, 0, 3, 0, false, sprites, 0, 0, 0, 0, 170, 124, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Farmer brumty", "He looks after livestock in this area", "", 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 15523536, 145, 220, 6, 6, 5, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, 385};
		} else {
			sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		}
		npcs.add(new NPCDef("Doctor orbon", "A local doctor", "", 20, 20, 3, 20, false, sprites, 1, 16777215, 16711680, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Councillor Halgrive", "A town counceller", "", 20, 20, 20, 20, false, sprites, 16777215, 255, 255, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Edmond", "A local civilian", "", 20, 20, 20, 20, false, sprites, 1, 12255487, 16777215, 9461792, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Citizen", "He look's tired", "", 12, 11, 13, 10, true, sprites, 16760880, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Citizen", "He look's frightened", "", 10, 10, 13, 8, true, sprites, 16760880, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Citizen", "She look's frustrated", "", 11, 10, 13, 14, true, sprites, 1, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Citizen", "He look's angry", "", 20, 20, 23, 18, true, sprites, 16728064, 12603424, 5263392, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Citizen", "He look's disillusioned", "", 18, 12, 10, 20, true, sprites, 6307872, 10506320, 3174432, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Jethick", "A cynical old man", "", 18, 12, 10, 20, false, sprites, 16777215, 5263488, 3158048, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 177, 83, 176, 46, 11, 45, -1};
		npcs.add(new NPCDef("Mourner", "A mourner or plague healer", "", 2, 2, 3, 2, false, sprites, 3158064, 16711680, 16711680, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 1, 2, -1, -1, 177, 83, 176, 46, 11, 45, -1};
		npcs.add(new NPCDef("Mourner", "A mourner or plague healer", "", 2, 2, 3, 2, false, sprites, 3158064, 16711680, 16711680, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ted Rehnison", "The head of the Rehnison family", "", 11, 8, 7, 11, false, sprites, 15921906, 8409120, 8409136, 15523536, 160, 230, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Martha Rehnison", "A fairly poor looking woman", "", 11, 10, 13, 14, false, sprites, 1, 8409120, 8409120, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Billy Rehnison", "The Rehnisons eldest son", "", 20, 60, 60, 40, false, sprites, 16761440, 8388688, 8409120, 15523536, 125, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 87, -1, -1, -1, -1};
		npcs.add(new NPCDef("Milli Rehnison", "She doesn't seem very happy", "", 42, 43, 37, 36, false, sprites, 16752704, 8060928, 3, 15523536, 112, 198, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 88, -1, -1, -1, -1};
		npcs.add(new NPCDef("Alrena", "She look's concerned", "", 1, 1, 5, 1, false, sprites, 15643488, 255, 3, 15523536, 140, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 177, 83, 176, 46, 11, 45, -1};
		npcs.add(new NPCDef("Mourner", "A mourner or plague healer", "", 2, 2, 3, 2, false, sprites, 3158064, 16711680, 16711680, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Clerk", "A bueracratic administrator", "", 2, 4, 3, 2, false, sprites, 16759632, 16021427, 12303291, 14470816, 138, 205, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 88, -1, -1, -1, -1};
		npcs.add(new NPCDef("Carla", "She look's upset", "", 1, 1, 5, 1, false, sprites, 15643488, 255, 3, 15523536, 140, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, 79, 64};
		npcs.add(new NPCDef("Bravek", "The city warder of West Ardougne", "", 15, 60, 30, 15, false, sprites, 1, 16711680, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 88, -1, -1, -1, -1};
		npcs.add(new NPCDef("Caroline", "A well dressed middle aged lady", "", 1, 1, 5, 1, false, sprites, 15643488, 255, 3, 15523536, 140, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Holgart", "An old sailor", "", 20, 20, 20, 20, false, sprites, 16777215, 255, 3, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Holgart", "An old sailor", "", 20, 20, 20, 20, false, sprites, 16777215, 255, 3, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Holgart", "An old sailor", "", 20, 20, 20, 20, false, sprites, 16777215, 255, 3, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("kent", "caroline's husband", "", 20, 60, 60, 40, false, sprites, 16761440, 8388688, 8409120, 15523536, 125, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 8, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("bailey", "the fishing platform cook", "", 20, 20, 3, 20, false, sprites, 1, 16777215, 16711680, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("kennith", "A young scared looking boy", "", 0, 0, 3, 0, false, sprites, 14535800, 11193464, 3, 15523536, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Platform Fisherman", "an emotionless fisherman", "", 15, 60, 30, 15, true, sprites, 15658734, 16763904, 16711680, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Platform Fisherman", "an emotionless fisherman", "", 15, 60, 30, 15, true, sprites, 15658734, 12255453, 14531532, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Platform Fisherman", "an emotionless fisherman", "", 15, 60, 30, 15, true, sprites, 15658734, 13426124, 15641275, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 86, -1, -1, -1, -1};
		npcs.add(new NPCDef("Elena", "She doesn't look too happy", "", 1, 1, 5, 1, false, sprites, 13542224, 13408767, 3, 15523536, 140, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("jinno", "He doesn't seem to mind his lack of legs", "", 30, 30, 30, 30, false, sprites, 1, 255, 14508096, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Watto", "He doesn't seem to mind his lack of legs", "", 30, 30, 30, 30, false, sprites, 16772761, 255, 14508096, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 28, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Recruiter", "A member of the Ardougne royal army", "", 40, 70, 65, 30, false, sprites, 15643488, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 1, 2, -1, -1, 177, 83, 176, 46, 11, 45, 63};
		npcs.add(new NPCDef("Head mourner", "In charge of people with silly outfits", "", 2, 2, 3, 2, false, sprites, 3158064, 16711680, 16711680, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 88, -1, -1, -1, -1};
		npcs.add(new NPCDef("Almera", "A woman of the wilderness", "", 1, 1, 5, 1, false, sprites, 15643488, 255, 3, 15523536, 140, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("hudon", "A young boisterous looking lad", "", 0, 0, 3, 0, false, sprites, 14535800, 11193464, 3, 15523536, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("hadley", "A happy looking fellow", "", 15, 60, 30, 15, false, sprites, 15658734, 13426124, 15641275, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{123, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Rat", "Overgrown vermin", "", 15, 2, 3, 8, true, sprites, 0, 0, 0, 0, 346, 136, 7, 7, 45, i++));
		sprites = new int[]{0, 28, 2, -1, -1, -1, -1, -1, -1, -1, -1, 64};
		npcs.add(new NPCDef("Combat instructor", "He will tell me how to fight", "", 40, 70, 65, 30, false, sprites, 11167296, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 175, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("golrie", "It's a tree gnome", "", 3, 3, 3, 3, false, sprites, 1, 13417420, 14535850, 36864, 90, 130, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, 122, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guide", "She gives hints to new adventurers", "", 0, 0, 7, 0, false, sprites, 1, 32768, 8388863, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{165, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("King Black Dragon", "The biggest meanest dragon around", "", 250, 240, 240, 250, true, sprites, 0, 0, 0, 0, 542, 391, 10, 7, 84, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 8, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("cooking instructor", "Talk to him to learn about runescape food", "", 20, 20, 3, 20, false, sprites, 1, 16777215, 16777215, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("fishing instructor", "He smells of fish", "", 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 13415270, 145, 230, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("financial advisor", "He knows about money", "", 0, 0, 3, 0, false, sprites, 16753488, 7368816, 7368816, 15523536, 130, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("gerald", "An old fisherman", "", 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 13415270, 145, 230, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 69, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("mining instructor", "A short angry guy", "", 20, 17, 16, 20, false, sprites, 7360576, 8409120, 8409120, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 86, -1, -1, -1, -1};
		npcs.add(new NPCDef("Elena", "She looks concerned", "", 1, 1, 5, 1, false, sprites, 13542224, 13408767, 3, 15523536, 140, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Omart", "A nervous looking fellow", "", 15, 60, 30, 15, false, sprites, 15658734, 13426124, 15641275, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bank assistant", "She can look after my stuff", bankerOption1, bankerOption2, 11, 8, 7, 11, false, sprites, 15921906, 7368816, 7368816, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Jerico", "He looks friendly enough", "", 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 13415270, 145, 230, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Kilron", "He looks shifty", "", 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 13415270, 145, 230, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 88, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guidor's wife", "She looks rather concerned", "", 1, 1, 5, 1, false, sprites, 15643488, 255, 3, 15523536, 140, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 28, 37, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Quest advisor", "I wonder what advise he has to impart", "", 40, 70, 65, 30, false, sprites, 11167296, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("chemist", "human", "", 3, 3, 7, 3, false, sprites, 16777215, 16777215, 16777215, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 177, 83, 176, 46, 11, 45, -1};
		npcs.add(new NPCDef("Mourner", "A mourner or plague healer", "", 2, 2, 3, 2, false, sprites, 3158064, 16711680, 16711680, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 177, 83, 176, 46, 11, 45, -1};
		npcs.add(new NPCDef("Mourner", "A mourner or plague healer", "", 2, 2, 3, 2, false, sprites, 3158064, 16711680, 16711680, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, 49, -1, -1, -1, -1, -1, -1, -1, 64};
		npcs.add(new NPCDef("Wilderness guide", "He's ready for a fight", "", 32, 33, 27, 26, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 77, 76, 81, -1, -1, -1, -1};
		npcs.add(new NPCDef("Magic Instructor", "An old wizard", "", 20, 15, 3, 10, false, sprites, 16777215, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 177, 83, 176, 46, 11, 45, -1};
		npcs.add(new NPCDef("Mourner", "A mourner or plague healer", "", 20, 20, 19, 30, true, sprites, 3158064, 16711680, 16711680, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 86, -1, -1, -1, -1};
		npcs.add(new NPCDef("Community instructor", "This is the last advisor - honest", "", 2, 4, 3, 2, false, sprites, 16746544, 16021427, 12303291, 14470816, 138, 205, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, 46, -1, 45, -1};
		npcs.add(new NPCDef("boatman", "An old sailor", "", 20, 20, 20, 20, false, sprites, 6710886, 255, 3, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{133, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("skeleton mage", "It rattles as it walks", "", 24, 20, 17, 23, true, sprites, 0, 0, 0, 0, 216, 234, 11, 11, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("controls guide", "He's ready for a fight", "", 32, 33, 27, 26, false, sprites, 16746544, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
	}

	private static void loadNPCDefinitions3() {
		int[] sprites;
		int i = npcs.size() - 1;

		/* Configurable NPC Data */
		String bankerOption1 = Config.S_RIGHT_CLICK_BANK ? "Bank" : ""; // Banker right click bank
		String bankerOption2 = Config.S_SPAWN_AUCTION_NPCS ? "Collect" : null; // Auction collect banker
		String shopOption = Config.S_RIGHT_CLICK_TRADE ? "Trade" : ""; // Shop right click trade

		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 86, -1, -1, -1, -1};
		npcs.add(new NPCDef("nurse sarah", "She's quite a looker", "", 1, 1, 5, 1, false, sprites, 15643488, 16777215, 16777215, 15523536, 140, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("Tailor", "He's ready for a party", shopOption, 32, 33, 27, 26, false, sprites, 16746544, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 177, 83, 176, 46, 11, 45, -1};
		npcs.add(new NPCDef("Mourner", "A mourner or plague healer", "", 30, 20, 25, 25, true, sprites, 3158064, 16711680, 16711680, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 69, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guard", "He tries to keep order around here", "", 31, 30, 22, 31, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Chemist", "He looks clever enough", "", 32, 33, 27, 26, false, sprites, 16746544, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, 83, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Chancy", "He's ready for a bet", "", 32, 33, 27, 26, false, sprites, 16746544, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Hops", "He's drunk", "", 32, 33, 27, 26, false, sprites, 16746544, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 83, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("DeVinci", "He has a colourful personality", "", 32, 33, 27, 26, false, sprites, 16746544, 11189213, 11189213, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 83, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("Guidor", "He's not that ill", "", 32, 33, 27, 26, false, sprites, 16746544, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, 83, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Chancy", "He's ready for a bet", "", 32, 33, 27, 26, false, sprites, 16746544, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Hops", "He's drunk", "", 32, 33, 27, 26, false, sprites, 16746544, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 83, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("DeVinci", "He has a colourful personality", "", 32, 33, 27, 26, false, sprites, 16746544, 11189213, 11189213, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 80, 62};
		npcs.add(new NPCDef("king Lathas", "King Lanthas of east ardounge", "", 15, 60, 30, 15, false, sprites, 1, 16711680, 16777215, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 77, 76, 81, -1, -1, -1, -1};
		npcs.add(new NPCDef("Head wizard", "He runs the wizards guild", "", 20, 15, 3, 10, false, sprites, 1, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, 77, 76, 81, -1, -1, -1, -1};
		npcs.add(new NPCDef("Magic store owner", "An old wizard", shopOption, 20, 15, 3, 10, false, sprites, 16777215, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{6, 1, 2, -1, -1, 77, 76, 81, -1, -1, -1, 439};
		} else {
			sprites = new int[]{6, 1, 2, -1, -1, 77, 76, 81, -1, -1, -1, -1};
		}
		npcs.add(new NPCDef("Wizard Frumscone", "A confused looking wizard", "", 20, 15, 3, 10, false, sprites, 1, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{135, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("target practice zombie", "The living dead", "", 23, 28, 24, 23, true, sprites, 0, 0, 0, 0, 174, 259, 12, 12, 5, i++));
		sprites = new int[]{6, 1, 2, 122, -1, -1, 83, 87, -1, -1, -1, -1};
		npcs.add(new NPCDef("Trufitus", "A wise old witch doctor", "", 10, 5, 7, 5, false, sprites, 12632256, 7296823, 7296823, 7296823, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 28, 37, -1, 109, -1, -1, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("Colonel Radick", "A soldier of the town of Yanille", "", 40, 70, 65, 30, true, sprites, 11167296, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, 48, 70, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Soldier", "A soldier of the town of Yanille", "", 31, 30, 22, 31, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bartender", "I could get a beer off him", "", 0, 0, 3, 0, false, sprites, 1, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{179, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Jungle Spider", "A venomous deadly spider", "", 45, 46, 50, 47, true, sprites, 0, 0, 0, 0, 120, 104, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, 83, 87, -1, -1, -1, 10};
		npcs.add(new NPCDef("Jiminua", "She looks very interested in selling some of her wares.", shopOption, 0, 0, 3, 0, false, sprites, 10, 8409136, 7296823, 7296823, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 180, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Jogre", "An aggressive humanoid", "", 72, 33, 60, 70, true, sprites, 3852326, 3329330, 37633, 3978097, 212, 280, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 69, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guard", "He tries to keep order around here", "", 31, 30, 22, 31, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 117, -1, 45, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Ogre", "Useful for ranged training", "", 72, 33, 60, 70, true, sprites, 11550752, 6299664, 6299664, 10056486, 212, 280, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 69, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guard", "He tries to keep order around here", "", 31, 30, 22, 31, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 69, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guard", "He tries to keep order around here", "", 31, 30, 22, 31, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("shop keeper", "he sells weapons", shopOption, 0, 0, 3, 0, false, sprites, 1, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bartender", "I could get a beer off him", "", 0, 0, 3, 0, false, sprites, 1, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Frenita", "runs a cookery shop", shopOption, 0, 0, 3, 0, false, sprites, 16752704, 8409120, 8409120, 15523536, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 119, -1, 22, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Ogre chieftan", "A slightly bigger uglier ogre", "", 92, 53, 80, 90, true, sprites, 11550752, 6299664, 6299664, 10056486, 222, 294, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 192, 197, 187, -1, 204, -1, -1};
		npcs.add(new NPCDef("rometti", "It's a well dressed tree gnome", shopOption, 3, 3, 3, 3, false, sprites, 1, 16711680, 14535850, 36864, 90, 130, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, 201, 202, -1, -1, -1, 65};
		npcs.add(new NPCDef("Rashiliyia", "A willowy ethereal being who floats above the ground", "", 80, 80, 80, 80, false, sprites, 1, 2, 3, 3978097, 155, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, 193, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Blurberry", "It's a red faced tree gnome", "", 3, 3, 3, 3, false, sprites, 1, 11184895, 14535850, 36864, 110, 140, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, 195, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Heckel funch", "It's another jolly tree gnome", shopOption, 3, 3, 3, 3, false, sprites, 1, 16711680, 14535850, 36864, 90, 130, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, 8, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Aluft Gianne", "It's a tree gnome chef", "", 3, 3, 3, 3, false, sprites, 1, 13434879, 14535901, 36864, 90, 130, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, 195, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Hudo glenfad", "It's another jolly tree gnome", shopOption, 3, 3, 3, 3, false, sprites, 1, 16711680, 14535850, 36864, 90, 130, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 88, -1, -1, -1, -1};
		npcs.add(new NPCDef("Irena", "human", "", 0, 0, 0, 0, false, sprites, 1, 12285781, 3, 13415270, 140, 215, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, 180, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Mosol", "A jungle warrior", "", 0, 0, 3, 0, false, sprites, 1, 9461792, 9461792, 9461792, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Gnome banker", "It's tree gnome banker", bankerOption1, bankerOption2, 3, 3, 3, 3, false, sprites, 16777215, 1052688, 1052688, 36864, 90, 130, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 150, -1, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("King Narnode Shareen", "It's a gnome he look's important", "", 3, 3, 3, 3, false, sprites, 1, 16776960, 16711424, 36864, 100, 150, 6, 6, 5, i++));
		sprites = new int[]{135, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("UndeadOne", "One of Rashaliyas Minions", "", 80, 59, 59, 50, true, sprites, 0, 0, 0, 0, 174, 259, 12, 12, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 82, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Drucas", "engraver", "", 20, 20, 20, 20, false, sprites, 1, 2, 3, 11766848, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("tourist", "human", "", 32, 33, 27, 26, false, sprites, 16746544, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 150, -1, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("King Narnode Shareen", "It's a gnome he look's important", "", 3, 3, 3, 3, false, sprites, 1, 16776960, 16711424, 36864, 100, 150, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 192, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Hazelmere", "An ancient looking gnome", "", 3, 3, 3, 3, false, sprites, 1, 16776960, 2280584, 36864, 100, 150, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 116, 69, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Glough", "An rough looking gnome", "", 3, 3, 3, 3, false, sprites, 1, 3158064, 3158064, 36864, 100, 150, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Shar", "Concerned about the economy", "b38c40", 0, 0, 3, 0, false, sprites, 16761440, 2, 8409120, 11766848, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Shantay", "human", "", 0, 0, 3, 0, false, sprites, 1, 8409120, 8409120, 11766848, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("charlie", "Poor guy?", "", 0, 0, 3, 0, true, sprites, 1, 8409120, 8409120, 11766848, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, 98, 180, 175, 22, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome guard", "A tree gnome guard", "", 31, 31, 31, 31, true, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 193, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome pilot", "He can fly the glider", "", 3, 3, 3, 3, false, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Mehman", "local", "805030", 32, 33, 27, 26, false, sprites, 16746544, 2, 3, 11766848, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 1, 2, -1, -1, -1, 213, 214, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ana", "This lady doesn't look as if she belongs here.", "", 17, 15, 16, 18, false, sprites, 16760880, 8409120, 8409120, 10056486, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 122, -1, 84, 42, -1, -1, -1, -1};
		npcs.add(new NPCDef("Chaos Druid warrior", "A crazy evil druid", "", 48, 42, 40, 48, true, sprites, 1, 65535, 255, 15392466, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 193, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome pilot", "He can fly the glider", "", 3, 3, 3, 3, false, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Shipyard worker", "He look's busy", "", 48, 42, 40, 48, true, sprites, 3158064, 8409120, 8409120, 11766848, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Shipyard worker", "He look's busy", "", 48, 42, 40, 48, true, sprites, 3158064, 8409120, 8409120, 11766848, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Shipyard worker", "He look's busy", "", 48, 42, 40, 48, true, sprites, 3158064, 8409120, 8409120, 7360528, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 22, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Shipyard foreman", "He look's busy", "", 60, 60, 59, 69, false, sprites, 3158064, 8409120, 8409120, 7360528, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 22, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Shipyard foreman", "He look's busy", "", 60, 60, 59, 69, false, sprites, 3158064, 8409120, 8409120, 7360528, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, 98, 180, 175, 22, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome guard", "A tree gnome guard", "", 23, 23, 23, 23, true, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 175, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Femi", "It's a little tree gnome", "", 3, 3, 3, 3, false, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 175, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Femi", "It's a little tree gnome", "", 3, 3, 3, 3, false, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 193, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Anita", "It's a little tree gnome", "", 3, 3, 3, 3, false, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 116, 69, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Glough", "An rough looking gnome", "", 3, 3, 3, 3, false, sprites, 1, 3158064, 3158064, 36864, 100, 150, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 84, 90, -1, -1, -1, -1};
		npcs.add(new NPCDef("Salarin the twisted", "A crazy evil druid", "", 68, 72, 70, 68, true, sprites, 14483456, 65535, 255, 15392466, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{164, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Black Demon", "A big scary jet black demon", "", 195, 168, 160, 178, true, sprites, 0, 0, 0, 0, 398, 401, 11, 11, 30, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 193, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome pilot", "He can fly the glider", "", 3, 3, 3, 3, false, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 193, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome pilot", "He can fly the glider", "", 3, 3, 3, 3, false, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 193, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome pilot", "He can fly the glider", "", 3, 3, 3, 3, false, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 193, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome pilot", "He can fly the glider", "", 3, 3, 3, 3, false, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{5, 28, 37, -1, 110, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Sigbert the Adventurer", "A Warrior", "", 55, 60, 52, 58, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 70, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Yanille Watchman", "He watches out for invading ogres", "pickpocket", 41, 30, 22, 41, true, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 70, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Tower guard", "He stops people going up the tower", "", 41, 30, 22, 41, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 175, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Trainer", "He can advise on training", "", 11, 11, 11, 11, false, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 175, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Trainer", "He can advise on training", "", 11, 11, 11, 11, false, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 175, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Trainer", "He can advise on training", "", 11, 11, 11, 11, false, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 175, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Trainer", "He can advise on training", "pickpocket", 11, 11, 11, 11, false, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 175, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Blurberry barman", "He serves cocktails", "pickpocket", shopOption, 3, 3, 3, 3, false, sprites, 1, 16776960, 16711424, 36864, 90, 120, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 194, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Gnome waiter", "He can serve you gnome food", "pickpocket", shopOption, 3, 3, 3, 3, false, sprites, 1, 16777164, 3158064, 36864, 90, 120, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, 98, 106, 175, 22, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome guard", "A tree gnome guard", "pickpocket", 31, 31, 31, 17, true, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 192, 198, 189, -1, 204, -1, -1};
		npcs.add(new NPCDef("Gnome child", "that's a little gnome", "pickpocket", 3, 3, 3, 3, true, sprites, 1, 16776960, 16711680, 36864, 80, 100, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, 97, 47, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Earth warrior", "A strange inhuman warrior", "", 52, 51, 54, 54, true, sprites, 6724027, 7356448, 7356448, 9461792, 150, 250, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 191, 200, 186, -1, 207, -1, -1};
		npcs.add(new NPCDef("Gnome child", "He's a little fellow", "pickpocket", 3, 3, 3, 3, true, sprites, 1, 16711935, 65280, 36864, 80, 100, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 193, 196, 188, -1, 203, -1, -1};
		npcs.add(new NPCDef("Gnome child", "hello little gnome", "pickpocket", 3, 3, 3, 3, true, sprites, 1, 16711884, 255, 36864, 80, 100, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, 116, 116, 69, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Gulluck", "He sells weapons", shopOption, 10, 11, 11, 11, false, sprites, 1, 3158064, 3158064, 36864, 100, 150, 6, 6, 5, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, 46, -1, -1, 511};
		} else {
			sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, 46, -1, -1, -1};
		}
		npcs.add(new NPCDef("Gunnjorn", "Not civilised looking", "", 18, 15, 14, 18, false, sprites, 16768384, 8409120, 8409120, 15523536, 160, 230, 6, 6, 5, i++));
		sprites = new int[]{137, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Zadimus", "Ghostly Visage of the dead Zadimus", "", 0, 0, 0, 0, false, sprites, 0, 0, 0, 0, 201, 243, 9, 9, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 192, -1, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("Brimstail", "An ancient looking gnome", "", 3, 3, 3, 3, false, sprites, 1, 16776960, 2280584, 36864, 100, 150, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 195, 198, 190, -1, 205, -1, -1};
		npcs.add(new NPCDef("Gnome child", "He's a little fellow", "pickpocket", 3, 3, 3, 3, false, sprites, 1, 16711884, 255, 36864, 80, 100, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 175, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome local", "A tree gnome villager", "pickpocket", 9, 9, 9, 9, true, sprites, 1, 8409120, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 193, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome local", "A tree gnome villager", "pickpocket", 3, 3, 3, 3, true, sprites, 1, 8409120, 8409120, 36864, 90, 120, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 122, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Moss Giant", "his beard seems to have a life of its own", "", 62, 61, 60, 65, true, sprites, 7838054, 8409120, 8409120, 14483408, 261, 396, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 175, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Baller", "A tree gnome ball player", "tackle", 70, 70, 70, 70, true, sprites, 1, 7798801, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 191, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Goalie", "A gnome ball goal catcher", "", 70, 70, 70, 70, false, sprites, 1, 16728064, 16728064, 36864, 100, 120, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 175, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Baller", "A tree gnome ball player", "tackle", 70, 70, 70, 70, true, sprites, 1, 10027110, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 191, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Baller", "A tree gnome ball player", "tackle", 70, 70, 70, 70, true, sprites, 1, 10027110, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 192, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Baller", "A tree gnome ball player", "tackle", 70, 70, 70, 70, true, sprites, 1, 10027110, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 193, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Baller", "A tree gnome ball player", "tackle", 70, 70, 70, 70, true, sprites, 1, 10027025, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 116, 69, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Referee", "He controls the game", "", 3, 3, 3, 3, false, sprites, 1, 3158064, 3158064, 36864, 100, 150, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 194, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Baller", "A tree gnome ball player", "tackle", 70, 70, 70, 70, true, sprites, 1, 10027025, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 194, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Baller", "A tree gnome ball player", "tackle", 70, 70, 70, 70, true, sprites, 1, 16752704, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 194, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Baller", "A tree gnome ball player", "tackle", 70, 70, 70, 70, true, sprites, 1, 16752704, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 194, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Baller", "A tree gnome ball player", "tackle", 70, 70, 70, 70, true, sprites, 1, 16760880, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 194, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Baller", "A tree gnome ball player", "tackle", 70, 70, 70, 70, true, sprites, 1, 16760880, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 194, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Baller", "A tree gnome ball player", "tackle", 70, 70, 70, 70, true, sprites, 1, 16760880, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 194, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Baller", "A tree gnome ball player", "tackle", 70, 70, 70, 70, true, sprites, 1, 16760880, 8409120, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 194, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Baller", "He's on your team", "pass to", 70, 70, 70, 70, false, sprites, 1, 16728064, 16728064, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 194, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gnome Baller", "He's on your team", "pass to", 70, 70, 70, 70, false, sprites, 1, 16728064, 16728064, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 175, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Cheerleader", "It's a little tree gnome", "", 3, 3, 3, 3, false, sprites, 1, 16728064, 16728064, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 175, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Cheerleader", "It's a little tree gnome", "", 3, 3, 3, 3, false, sprites, 1, 16728064, 16728064, 36864, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{135, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Nazastarool Zombie", "One of Rashaliyas Minions", "", 95, 70, 80, 90, true, sprites, 0, 0, 0, 0, 261, 388, 12, 12, 5, i++));
		sprites = new int[]{134, 133, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Nazastarool Skeleton", "One of Rashaliyas Minions", "", 95, 70, 80, 90, true, sprites, 0, 0, 0, 0, 259, 281, 11, 11, 12, i++));
		sprites = new int[]{137, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Nazastarool Ghost", "One of Rashaliyas Minions", "", 95, 70, 80, 90, true, sprites, 0, 0, 0, 0, 302, 365, 9, 9, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, 46, -1, 10, -1};
		npcs.add(new NPCDef("Fernahei", "An enthusiastic fishing shop owner", shopOption, 10, 5, 7, 5, false, sprites, 12632256, 7296823, 7296823, 7296823, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 201, -1, -1, -1, 10, -1};
		npcs.add(new NPCDef("Jungle Banker", "He can look after my money", bankerOption1, bankerOption2, 11, 8, 7, 11, false, sprites, 12632256, 7296823, 7296823, 7296823, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Cart Driver", "He drives the cart", "", 15, 16, 12, 18, false, sprites, 16760880, 16777215, 16777215, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Cart Driver", "He drives the cart", "", 15, 16, 12, 18, false, sprites, 3158064, 16777215, 16777215, 7296823, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 83, 87, -1, -1, -1, 10};
		npcs.add(new NPCDef("Obli", "An intelligent looking shop owner", shopOption, 0, 0, 3, 0, false, sprites, 10, 3158064, 7296823, 7296823, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 83, -1, -1, -1, -1, 10};
		npcs.add(new NPCDef("Kaleb", "This is Kaleb Paramaya - a warm and friendly inn owner", "", 0, 0, 3, 0, false, sprites, 10, 16752704, 16777215, 7296823, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 83, -1, -1, -1, -1, 10};
		npcs.add(new NPCDef("Yohnus", "This is Yohnus - he runs the local blacksmiths", "", 0, 0, 3, 0, false, sprites, 10, 16752704, 16777215, 7296823, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Serevel", "This is Serevel - he sells tickets for the 'Lady of the Waves'", "", 0, 0, 3, 0, false, sprites, 10, 16752704, 16777215, 11766848, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, 83, -1, -1, -1, -1, 10};
		npcs.add(new NPCDef("Yanni", "Yanni Salika - He buys and sells antiques.", "", 0, 0, 3, 0, false, sprites, 10, 16777215, 16711680, 11766848, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Official", "He helps the referee", "", 3, 3, 3, 3, false, sprites, 10, 1, 0, 0, 100, 130, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Koftik", "The kings top tracker", "", 18, 15, 14, 18, false, sprites, 16768384, 8409120, 8409120, 15523536, 160, 230, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Koftik", "The kings top tracker", "", 18, 15, 14, 18, false, sprites, 16768384, 8409120, 8409120, 15523536, 160, 230, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Koftik", "The kings top tracker", "", 18, 15, 14, 18, false, sprites, 16768384, 8409120, 8409120, 15523536, 160, 230, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Koftik", "The kings top tracker", "", 18, 15, 14, 18, false, sprites, 16768384, 8409120, 8409120, 15523536, 160, 230, 6, 6, 5, i++));
		sprites = new int[]{123, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Blessed Vermen", "A undead servent of iban", "", 15, 7, 30, 7, true, sprites, 0, 0, 0, 0, 115, 45, 7, 7, 10, i++));
		sprites = new int[]{125, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Blessed Spider", "One of iban's eight legged friends", "", 45, 31, 32, 34, true, sprites, 0, 0, 0, 0, 180, 156, 6, 6, 5, i++));
		sprites = new int[]{6, 34, 43, 101, 119, -1, -1, -1, -1, -1, -1, 66};
		npcs.add(new NPCDef("Paladin", "A paladin of Ardougne", "", 85, 55, 57, 88, true, sprites, 16760880, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 34, 43, 101, 119, -1, -1, -1, -1, -1, -1, 66};
		npcs.add(new NPCDef("Paladin", "A paladin of Ardougne", "", 85, 55, 57, 88, true, sprites, 16760880, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("slave", "He seems possessed", "", 17, 15, 16, 18, true, sprites, 16777215, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("slave", "He seems possessed", "", 17, 15, 16, 18, true, sprites, 16777215, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("slave", "He seems to have been here a while", "", 17, 15, 16, 18, true, sprites, 16777215, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("slave", "He seems possessed", "", 17, 15, 16, 18, true, sprites, 16760880, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("slave", "He seems to have been here a while", "", 17, 15, 16, 18, true, sprites, 16752704, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("slave", "He seems possessed", "", 17, 15, 16, 18, true, sprites, 16760880, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("slave", "He seems to have been here a while", "", 17, 15, 16, 18, true, sprites, 16777130, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{125, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Kalrag", "I think this is one of Ibans pets", "", 88, 69, 78, 78, true, sprites, 0, 0, 0, 0, 420, 404, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Niloof", "A short angry guy", "", 20, 17, 16, 20, false, sprites, 7360576, 8409120, 8409120, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 78, -1, 88, -1, -1, -1, 63};
		npcs.add(new NPCDef("Kardia the Witch", "She's got warts", "", 35, 25, 10, 30, false, sprites, 1, 2, 3, 15523536, 155, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Souless", "He seems an empty shell", "", 17, 15, 16, 18, true, sprites, 16777215, 8409120, 8409120, 16777215, 100, 200, 6, 6, 5, i++));
		sprites = new int[]{124, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Othainian", "big red and incredibly evil", "", 78, 78, 78, 78, true, sprites, 0, 0, 0, 0, 358, 341, 11, 11, 30, i++));
		sprites = new int[]{164, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Doomion", "A big scary jet black demon", "", 98, 98, 98, 98, true, sprites, 0, 0, 0, 0, 358, 341, 11, 11, 30, i++));
		sprites = new int[]{124, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Holthion", "big red and incredibly evil", "", 78, 78, 78, 78, true, sprites, 0, 0, 0, 0, 358, 341, 11, 11, 30, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Klank", "A short angry guy", "", 20, 17, 16, 20, false, sprites, 7360576, 8409120, 8409120, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{18, 1, 2, -1, -1, -1, 82, 88, 46, 11, -1, -1};
		npcs.add(new NPCDef("Iban", "You feel terror just looking at him", "", 24, 22, 17, 23, false, sprites, 1, 2, 3, 4, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Koftik", "The kings top tracker", "", 18, 15, 14, 18, false, sprites, 16768384, 8409120, 8409120, 15523536, 160, 230, 6, 6, 5, i++));
		sprites = new int[]{142, 139, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Goblin guard", "An imposing green creature", "", 48, 51, 43, 51, true, sprites, 0, 0, 0, 0, 285, 268, 9, 8, 7, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, -1, -1, 46, -1, 9, -1};
		npcs.add(new NPCDef("Observatory Professor", "He works in the observatory", "", 3, 3, 7, 3, false, sprites, 16777215, 16777215, 16777215, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{127, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ugthanki", "A dangerous type of spitting camel that can temporarily blind an opponent.", "", 45, 45, 45, 45, true, sprites, 0, 0, 0, 0, 208, 208, 6, 6, 25, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Observatory assistant", "The Professor's assistant", "", 3, 3, 7, 3, false, sprites, 16777215, 16777215, 16777215, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{210, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Souless", "A servent to zamorak", "", 23, 28, 24, 23, true, sprites, 0, 0, 0, 0, 174, 259, 12, 12, 5, i++));
		sprites = new int[]{166, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Dungeon spider", "A nasty poisonous arachnid", "", 25, 20, 35, 10, true, sprites, 0, 0, 0, 0, 90, 78, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Kamen", "A short angry guy", "", 20, 17, 16, 20, false, sprites, 7360576, 8409120, 8409120, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, 84, 90, -1, -1, -1, -1};
		npcs.add(new NPCDef("Iban disciple", "An evil follower of Iban", "", 18, 22, 20, 18, true, sprites, 16761440, 65535, 255, 15392466, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Koftik", "The kings top tracker", "", 18, 15, 14, 18, false, sprites, 16768384, 8409120, 8409120, 15523536, 160, 230, 6, 6, 5, i++));
		sprites = new int[]{142, 139, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Goblin", "These goblins have grown strong", "", 24, 20, 16, 18, true, sprites, 0, 0, 0, 0, 219, 206, 9, 8, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, 46, -1, 10, -1};
		npcs.add(new NPCDef("Chadwell", "A sturdy looking gent", shopOption, 18, 15, 14, 18, false, sprites, 16768384, 8409120, 8409120, 15523536, 160, 230, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, -1, -1, 46, -1, 9, -1};
		npcs.add(new NPCDef("Professor", "The owner of the observatory", "", 3, 3, 7, 3, false, sprites, 16777215, 16777215, 16777215, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{-1, -1, -1, 99, 49, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("San Tojalon", "The animated spirit of San Tojalon", "", 120, 120, 120, 120, true, sprites, 16760880, 8409120, 8409120, 10056486, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{137, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ghost", "A doomed victim of zamorak", "", 33, 33, 20, 30, true, sprites, 0, 0, 0, 0, 201, 243, 9, 9, 5, i++));
		sprites = new int[]{137, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Spirit of Scorpius", "The undead spirit of the follower of Zamorak", "", 100, 100, 100, 100, false, sprites, 0, 0, 0, 0, 201, 243, 9, 9, 5, i++));
		sprites = new int[]{143, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Scorpion", "There are nasty scorpions around this grave", "", 21, 24, 17, 22, false, sprites, 0, 0, 0, 0, 121, 69, 7, 7, 45, i++));
		sprites = new int[]{6, 1, 2, -1, 122, -1, 84, -1, -1, -1, -1, 68};
		npcs.add(new NPCDef("Dark Mage", "He works in the ways of dark magic", "", 0, 0, 3, 0, false, sprites, 3158064, 16763952, 15609986, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, 49, 70, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Mercenary", "He seems to be guarding an area", "", 39, 39, 39, 39, true, sprites, 1, 11379585, 14858776, 11766848, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 51, 71, 22, -1, -1, -1, -1, 62};
		npcs.add(new NPCDef("Mercenary Captain", "He's in control of the local guards.", "watch", 48, 80, 80, 48, true, sprites, 1, 11379585, 14858776, 11766848, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, 49, 70, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Mercenary", "He seems to be guarding an area", "", 48, 30, 48, 32, true, sprites, 1, 11379585, 14858776, 11766848, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, 213, 214, -1, -1, -1, -1};
		npcs.add(new NPCDef("Mining Slave", "A chained slave forced to mine rocks.", "", 17, 15, 16, 18, true, sprites, 16777130, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 77, 76, 81, -1, -1, -1, -1};
		npcs.add(new NPCDef("Watchtower wizard", "A learned man", "", 20, 15, 3, 10, false, sprites, 16777215, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 122, 75, 21, -1, -1, 11, 79, 83};
		npcs.add(new NPCDef("Ogre Shaman", "An intelligent form of ogre", "", 100, 100, 100, 100, false, sprites, 3381504, 10027263, 26367, 26367, 222, 294, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 117, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Skavid", "Servant race to the ogres", "", 3, 3, 3, 3, false, sprites, 629145, 10079385, 10066329, 10066329, 96, 176, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 114, -1, 22, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Ogre guard", "These ogres protect the city", "", 92, 53, 80, 90, false, sprites, 11550752, 6299664, 6299664, 10056486, 222, 294, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 114, -1, 22, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Ogre guard", "These ogres protect the city", "", 92, 53, 80, 90, false, sprites, 11550752, 6299664, 6299664, 10056486, 222, 294, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 119, -1, 22, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Ogre guard", "These ogres protect the city", "", 92, 53, 80, 90, false, sprites, 11550752, 6299664, 6299664, 10056486, 222, 294, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 117, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Skavid", "Servant race to the ogres", "", 3, 3, 3, 3, false, sprites, 629145, 10066329, 10066329, 10066329, 96, 176, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 117, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Skavid", "Servant race to the ogres", "", 3, 3, 3, 3, false, sprites, 629145, 10066329, 10066329, 10066329, 96, 176, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 119, -1, 22, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Og", "The chieftan of this ogre tribe", "", 92, 53, 80, 90, false, sprites, 11550752, 6299664, 6299664, 10056486, 222, 294, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 119, -1, 22, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Grew", "The chieftan of this ogre tribe", "", 92, 53, 80, 90, false, sprites, 11550752, 6299664, 6299664, 10056486, 222, 294, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 119, -1, 22, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Toban", "The chieftan of this ogre tribe", "", 92, 53, 80, 90, false, sprites, 11550752, 6299664, 6299664, 10056486, 222, 294, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 119, 70, 22, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Gorad", "A high ranking ogre official", "", 92, 53, 80, 90, true, sprites, 11550752, 6299664, 6299664, 10056486, 222, 294, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 114, -1, 24, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Ogre guard", "this creature looks very tough", "", 98, 99, 99, 90, true, sprites, 11550752, 6299664, 6299664, 10056486, 233, 320, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 70, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Yanille Watchman", "A captured guard of Yanille", "", 41, 30, 22, 41, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, 45, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Ogre merchant", "He sells ogre-inspired items", shopOption, 72, 33, 60, 70, false, sprites, 11550752, 6299664, 6299664, 10056486, 212, 280, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, 45, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Ogre trader", "He trades in metals", shopOption, 72, 33, 60, 70, false, sprites, 11550752, 6299664, 6299664, 10056486, 212, 280, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, 45, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Ogre trader", "He trades in food", shopOption, 72, 33, 60, 70, false, sprites, 11550752, 6299664, 6299664, 10056486, 212, 280, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, 45, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Ogre trader", "He trades in food", shopOption, 72, 33, 60, 70, false, sprites, 11550752, 6299664, 6299664, 10056486, 212, 280, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, 49, 70, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Mercenary", "He seems to be guarding an area", "", 48, 30, 48, 32, true, sprites, 1, 11379585, 14858776, 11766848, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 119, 70, 22, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("City Guard", "high ranking ogre guards", "", 92, 53, 80, 90, false, sprites, 11550752, 6299664, 6299664, 10056486, 222, 294, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, 49, 70, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Mercenary", "He seems to be guarding this area", "", 48, 30, 48, 32, true, sprites, 1, 11379585, 14858776, 11766848, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, 63};
		npcs.add(new NPCDef("Lawgof", "He guards the mines", "", 20, 17, 16, 20, false, sprites, 7360576, 3158064, 3158064, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Dwarf", "A short angry guy", "", 20, 17, 16, 20, true, sprites, 7360576, 3158064, 3158064, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("lollk", "He looks scared", "", 20, 17, 16, 20, false, sprites, 7360576, 3158064, 3158064, 15523536, 90, 130, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 117, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Skavid", "Servant race to the ogres", "", 3, 3, 3, 3, false, sprites, 629145, 10066329, 10066329, 10066329, 96, 176, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 119, -1, 22, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Ogre guard", "These ogres protect the city", "", 92, 53, 80, 90, true, sprites, 11550752, 6299664, 6299664, 10056486, 222, 294, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, 63};
		npcs.add(new NPCDef("Nulodion", "He's the head of black guard weapon development", "", 20, 17, 16, 20, false, sprites, 7360576, 3158064, 3158064, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 109, 70, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Dwarf", "A short angry guy", "", 20, 17, 16, 20, true, sprites, 7360576, 3158064, 3158064, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 122, 154, 85, 86, 155, 211, 80, 215};
		npcs.add(new NPCDef("Al Shabim", "The leader of a nomadic Bedabin desert people - sometimes referred to as the 'Tenti's'", "", 0, 0, 3, 0, false, sprites, 1, 2105376, 3, 6307872, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, 85, 86, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bedabin Nomad", "A Bedabin nomad - they live in the harshest extremes in the desert", "", 0, 0, 3, 0, false, sprites, 1, 2105376, 3, 6307872, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 51, 150, 22, -1, -1, -1, -1, 68};
		npcs.add(new NPCDef("Captain Siad", "He's in control of the whole mining camp.", "", 48, 48, 48, 48, true, sprites, 1, 16777215, 14858776, 11766848, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, 48, -1, 85, 86, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bedabin Nomad Guard", "A Bedabin nomad guard - he's protecting something important", "", 70, 70, 70, 70, true, sprites, 1, 2105376, 3, 6307872, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 117, -1, 45, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Ogre citizen", "A denizen of Gu'Tanoth", "", 72, 33, 60, 70, true, sprites, 11550752, 6299664, 6299664, 10056486, 212, 280, 6, 6, 5, i++));
		sprites = new int[]{124, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Rock of ages", "A huge boulder", "", 150, 150, 150, 150, true, sprites, 0, 0, 0, 0, 74, 70, 11, 11, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 117, -1, 45, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Ogre", "A large dim looking humanoid", "", 72, 33, 60, 70, true, sprites, 11550752, 6299664, 6299664, 10056486, 212, 280, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 117, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Skavid", "Servant race to the ogres", "", 3, 3, 3, 3, false, sprites, 629145, 10066329, 10066329, 10066329, 96, 176, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 117, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Skavid", "Servant race to the ogres", "", 3, 3, 3, 3, false, sprites, 629145, 10066329, 10066329, 10066329, 96, 176, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, 117, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Skavid", "Servant race to the ogres", "", 3, 3, 3, 3, false, sprites, 629145, 10066329, 10066329, 10066329, 96, 176, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, 49, 70, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Draft Mercenary Guard", "He's quickly drafted in to deal with trouble makers", "", 48, 60, 60, 32, true, sprites, 1, 11379585, 14858776, 11766848, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, 85, 86, -1, -1, -1, -1};
		npcs.add(new NPCDef("Mining Cart Driver", "He drives the mining cart", "", 15, 16, 12, 18, false, sprites, 16760880, 16777215, 16777215, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 122, 70, 76, 81, -1, -1, -1, -1};
		npcs.add(new NPCDef("kolodion", "He runs the mage arena", "", 20, 15, 3, 10, false, sprites, 1, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 122, 70, 76, 81, -1, -1, -1, -1};
		npcs.add(new NPCDef("kolodion", "He runs the mage arena", "", 20, 15, 3, 10, true, sprites, 1, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 89, -1, -1, -1, -1};
		npcs.add(new NPCDef("Gertrude", "A busy housewife", "", 20, 20, 20, 20, false, sprites, 16763992, 15618286, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Shilop", "A young boisterous looking lad", "", 0, 0, 3, 0, false, sprites, 14535800, 11193464, 3, 15523536, 100, 140, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, 49, 70, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Rowdy Guard", "He looks as if he's spoiling for trouble", "", 48, 60, 60, 32, true, sprites, 1, 11379585, 14858776, 11766848, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, 49, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Shantay Pass Guard", "He seems to be guarding the Shantay Pass", "", 32, 32, 32, 32, true, sprites, 1, 11379585, 8421376, 11766848, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, 213, 214, -1, -1, -1, -1};
		npcs.add(new NPCDef("Rowdy Slave", "A slave who's looking for trouble.", "", 17, 15, 16, 18, true, sprites, 16777130, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, 49, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Shantay Pass Guard", "He seems to be guarding the Shantay Pass", "", 32, 32, 32, 32, false, sprites, 1, 11379585, 8421376, 11766848, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Assistant", "He is an assistant to Shantay and helps him to run the pass.", shopOption, 0, 0, 3, 0, false, sprites, 1, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{216, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Desert Wolf", "A vicious Desert wolf", "", 30, 32, 34, 31, true, sprites, 0, 0, 0, 0, 260, 198, 6, 10, 30, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, 46, 11, 10, -1};
		npcs.add(new NPCDef("Workman", "This person is working on the site", "pickpocket", 11, 8, 7, 11, false, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Examiner", "As you examine the examiner you examine that she is indeed an examiner!!", "", 1, 1, 5, 1, false, sprites, 15643488, 255, 3, 15523536, 140, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 88, 46, 11, -1, -1};
		npcs.add(new NPCDef("Student", "A student busily digging!", "", 0, 0, 3, 0, false, sprites, 1, 16036851, 3, 15523536, 130, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, 46, 11, -1, -1};
		npcs.add(new NPCDef("Student", "A student busily digging!", "", 20, 20, 20, 20, false, sprites, 1, 52224, 15658576, 13415270, 140, 215, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, 46, 11, -1, -1};
		npcs.add(new NPCDef("Guide", "This person specialises in panning for gold", "", 20, 15, 3, 10, false, sprites, 1, 10053120, 6697728, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, 46, 11, -1, -1};
		npcs.add(new NPCDef("Student", "A student busily digging!", "", 20, 17, 19, 18, false, sprites, 1, 16737792, 3, 10053171, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Archaeological expert", "An expert on archaeology!", "", 20, 20, 3, 20, false, sprites, 1, 16777215, 16711680, 6697728, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, 46, 11, -1, -1};
		npcs.add(new NPCDef("civillian", "He looks aggitated!", "", 20, 17, 19, 18, true, sprites, 1, 16737792, 3, 10053171, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 88, 46, 11, -1, -1};
		npcs.add(new NPCDef("civillian", "She looks aggitated!", "", 0, 0, 3, 0, false, sprites, 1, 16036851, 3, 15523536, 130, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("civillian", "She looks aggitated!", "", 0, 0, 3, 0, false, sprites, 16777215, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("civillian", "He looks aggitated!", "pickpocket", 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Murphy", "The man in charge of the fishing trawler", "", 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 13415270, 145, 230, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Murphy", "The man in charge of the fishing trawler", "", 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 13415270, 145, 230, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 41, -1, 52, 74, 25, -1, -1, -1, 80, 68};
		npcs.add(new NPCDef("Sir Radimus Erkle", "A huge muscular man in charge of the Legends Guild", "", 10, 20, 8, 5, false, sprites, 16777215, 13415270, 13415270, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, 180, 72, 23, 94, -1, -1, -1, 215};
		npcs.add(new NPCDef("Legends Guild Guard", "This guard is protecting the entrance to the Legends Guild.", "", 50, 50, 50, 50, false, sprites, 6307872, 13415270, 13415270, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, 85, 86, -1, 211, -1, -1};
		npcs.add(new NPCDef("Escaping Mining Slave", "An emancipated slave with cool Desert Clothes.", "", 17, 15, 16, 18, false, sprites, 16777130, 8409120, 8409120, 15523536, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, 46, 11, 10, -1};
		npcs.add(new NPCDef("Workman", "This person is working in the mine", "pickpocket", 11, 8, 7, 11, false, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Murphy", "The man in charge of the fishing trawler", "", 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 13415270, 145, 230, 6, 6, 5, i++));
		sprites = new int[]{137, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Echned Zekin", "An evil spirit of the underworld.", "", 50, 50, 50, 50, false, sprites, 0, 0, 0, 0, 201, 243, 9, 9, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Donovan the Handyman", "It's the family odd jobs man", "", 11, 8, 7, 11, false, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Pierre the Dog Handler", "It's the guy who looks after the family guard dog", "", 11, 8, 7, 11, false, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Hobbes the Butler", "It's the family butler", "", 11, 8, 7, 11, false, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 8, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Louisa The Cook", "It's the family cook", "", 0, 0, 3, 0, false, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Mary The Maid", "The family maid", "", 35, 25, 10, 30, false, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Stanford The Gardener", "It's the family Gardener", "", 10, 8, 7, 8, false, sprites, 1, 2, 3, 13415270, 155, 230, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 71, 22, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guard", "He looks like he's in over his head here", "", 31, 30, 22, 31, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{159, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guard Dog", "He doesn't seem pleased to see me", "", 45, 47, 49, 46, false, sprites, 0, 0, 0, 0, 247, 188, 6, 10, 30, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Guard", "***EMPTY PLEASE USE OR REPLACE***", "", 10, 8, 7, 8, false, sprites, 1, 2, 3, 13415270, 155, 230, 6, 6, 5, i++));
	}

	private static void loadNpcDefinitions4() {
		int[] sprites;
		int i = npcs.size() - 1;

		/* Configurable NPC Data */
		String bankerOption1 = Config.S_RIGHT_CLICK_BANK ? "Bank" : ""; // Banker right click bank
		String bankerOption2 = Config.S_SPAWN_AUCTION_NPCS ? "Collect" : null; // Auction collect banker
		String shopOption = Config.S_RIGHT_CLICK_TRADE ? "Trade" : ""; // Shop right click trade

		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Man", "A thirsty looking man", "", 11, 8, 7, 11, false, sprites, 1, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Anna Sinclair", "The first child of the late Lord Sinclair", "", 11, 8, 7, 11, false, sprites, 1, 16711680, 65280, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bob Sinclair", "The second child of the late Lord Sinclair", "", 11, 8, 7, 11, false, sprites, 1, 16711680, 16711680, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Carol Sinclair", "The third child of the late Lord Sinclair", "", 11, 8, 7, 11, false, sprites, 1, 255, 16711680, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("David Sinclair", "The fourth child of the late Lord Sinclair", "", 11, 8, 7, 11, false, sprites, 1, 65280, 65280, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Elizabeth Sinclair", "The fifth child of the late Lord Sinclair", "", 11, 8, 7, 11, false, sprites, 1, 65280, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Frank Sinclair", "The sixth child of the late Lord Sinclair", "", 11, 8, 7, 11, false, sprites, 1, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, -1, -1, -1, 45, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("kolodion", "He's a shape shifter", "", 72, 55, 65, 70, true, sprites, 11550752, 6299664, 6299664, 10056486, 212, 280, 6, 6, 5, i++));
		sprites = new int[]{125, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("kolodion", "He's a shape shifter", "", 47, 69, 78, 78, true, sprites, 0, 0, 0, 0, 420, 404, 6, 6, 5, i++));
		sprites = new int[]{210, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("kolodion", "He's a shape shifter", "", 58, 28, 78, 23, true, sprites, 0, 0, 0, 0, 270, 390, 12, 12, 5, i++));
		sprites = new int[]{164, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("kolodion", "He's a shape shifter", "", 105, 85, 107, 98, true, sprites, 0, 0, 0, 0, 358, 341, 11, 11, 30, i++));
		sprites = new int[]{-1, -1, -1, 100, 50, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Irvig Senay", "The animated spirit of Irvig Senay", "", 125, 125, 125, 125, true, sprites, 16760880, 8409120, 8409120, 10056486, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{-1, -1, -1, 102, 52, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ranalph Devere", "The animated spirit of Ranalph Devere", "", 130, 130, 130, 130, true, sprites, 16760880, 8409120, 8409120, 10056486, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Poison Salesman", "Peter Potter - Poison Purveyor", "", 9, 8, 5, 7, false, sprites, 16711680, 2, 3, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{7, 1, 2, 97, 219, -1, 221, 220, -1, -1, -1, -1};
		npcs.add(new NPCDef("Gujuo", "A tall charismatic looking jungle native - he approaches with confidence", "", 60, 60, 60, 60, false, sprites, 0, 9461792, 9461792, 9461792, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, 108, -1, -1, -1, 46, -1, -1, 65};
		npcs.add(new NPCDef("Jungle Forester", "A woodsman who specialises in large and exotic timber", "", 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 13415270, 145, 230, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, 122, -1, -1, 85, 86, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ungadulu", "An ancient looking Shaman", "", 75, 75, 75, 75, true, sprites, 12632256, 7296823, 7296823, 7296823, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, 180, -1, -1, 85, 86, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ungadulu", "An ancient looking Shaman - he looks very strange with glowing red eyes...", "", 75, 75, 75, 75, true, sprites, 16728064, 7296823, 7296823, 7296823, 148, 224, 6, 6, 5, i++));
		sprites = new int[]{138, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Death Wing", "A supernatural creature of the underworld", "", 80, 80, 80, 80, true, sprites, 0, 0, 0, 0, 225, 195, 5, 3, 5, i++));
		sprites = new int[]{164, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Nezikchened", "An ancient powerful Demon of the Underworld...", "", 175, 177, 160, 178, true, sprites, 0, 0, 0, 0, 358, 341, 11, 11, 30, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, 63};
		npcs.add(new NPCDef("Dwarf Cannon engineer", "He's the head of black guard weapon development", "", 20, 17, 16, 20, false, sprites, 7360576, 3158064, 3158064, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, 63};
		npcs.add(new NPCDef("Dwarf commander", "He guards the mines", "", 20, 17, 16, 20, false, sprites, 7360576, 3158064, 3158064, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{-1, 1, 2, 122, -1, 77, 76, 81, 155, -1, -1, 64};
		npcs.add(new NPCDef("Viyeldi", "The spirit of a dead sorcerer", "", 80, 80, 80, 80, true, sprites, 1, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, 430};
		} else {
			sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		}
		npcs.add(new NPCDef("Nurmof", "He sells pickaxes", shopOption, 20, 17, 16, 20, false, sprites, 7360576, 9465888, 13393952, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Fatigue expert", "He looks wide awake", "", 10, 10, 13, 8, false, sprites, 16760880, 8409120, 8409120, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{222, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Karamja Wolf", "A hungry", "", 61, 61, 61, 61, true, sprites, 0, 0, 0, 0, 260, 198, 6, 10, 30, i++));
		sprites = new int[]{5, 1, 2, -1, 180, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Jungle Savage", "A savage and fearless Jungle warrior", "", 100, 60, 90, 100, true, sprites, 1, 9461792, 9461792, 7360528, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{223, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Oomlie Bird", "A variety of flightless jungle fowl - it has a sharp beak and a bad temper.", "", 50, 20, 40, 20, true, sprites, 0, 0, 0, 0, 70, 62, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Sidney Smith", "Sidney Smith - Certification clerk", "", 30, 30, 30, 30, false, sprites, 0, 16711935, 16744703, 8404992, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Siegfried Erkle", "An eccentric shop keeper - related to the Grand Vizier of the Legends Guild", shopOption, 35, 25, 10, 30, false, sprites, 16753488, 14518442, 3, 13415270, 145, 235, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Tea seller", "He has delicious tea to buy", shopOption, 11, 8, 7, 11, false, sprites, 1, 2, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Wilough", "A young son of gertrudes", "", 0, 0, 3, 0, false, sprites, 14535800, 11193464, 3, 15523536, 100, 120, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Philop", "Gertrudes youngest", "", 0, 0, 3, 0, false, sprites, 14535800, 11193464, 3, 15523536, 80, 100, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Kanel", "Gertrudes youngest's twin brother", "", 0, 0, 3, 0, false, sprites, 14535800, 11193464, 3, 15523536, 80, 100, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("chamber guardian", "He hasn't seen much sun latley", "", 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 13415270, 145, 230, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 41, -1, 52, 74, 25, -1, -1, -1, 80, 68};
		npcs.add(new NPCDef("Sir Radimus Erkle", "A huge muscular man in charge of the Legends Guild", "", 10, 20, 8, 5, false, sprites, 16777215, 13415270, 13415270, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{143, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Pit Scorpion", "Very vicious little scorpions", "", 33, 30, 32, 48, true, sprites, 0, 0, 0, 0, 121, 69, 7, 7, 45, i++));
		sprites = new int[]{3, 1, 2, 53, -1, -1, 227, -1, -1, 226, -1, -1};
		npcs.add(new NPCDef("Shadow Warrior", "A sinsistar shadowy figure", "", 61, 68, 67, 61, true, sprites, 1, 2, 3, 4, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 186, -1, 207, -1, -1};
		npcs.add(new NPCDef("Fionella", "She runs the legend's general store", shopOption, 35, 25, 10, 30, false, sprites, 16752704, 3211263, 3, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 122, 192, -1, -1, -1, -1, -1, 65};
		npcs.add(new NPCDef("Battle mage", "He kills in the name of guthix", "", 0, 90, 120, 0, true, sprites, 1, 8413216, 8409120, 36864, 100, 150, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 209, -1, 82, 88, -1, -1, -1, 62};
		npcs.add(new NPCDef("Battle mage", "He kills in the name of zamarok", "", 0, 90, 120, 0, true, sprites, 1, 2, 3, 16776944, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 218, -1, 85, 86, -1, -1, -1, 64};
		npcs.add(new NPCDef("Battle mage", "He kills in the name of Saradomin", "", 0, 90, 120, 0, true, sprites, 3158064, 16763952, 15609986, 9461792, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Gundai", "He must get lonely out here", bankerOption1, bankerOption2, 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 13415270, 145, 230, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Lundail", "He sells rune stones", shopOption, 15, 16, 12, 18, false, sprites, 11167296, 8409120, 3, 13415270, 145, 230, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, 46, -1, -1, -1};
		npcs.add(new NPCDef("Auctioneer", "He gives access to auction house", "Auction", 0, 0, 3, 0, false, sprites, 16761440, 2, 8409120, 13415270, 145, 230, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Auction Clerk", "There to help me make my auctions", "Auction", "Teleport", 15, 16, 12, 18, false, sprites, 11167296, 11141375, 11141375, 14415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, 77, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Subscription Vendor", "Exchange your subscription token to subscription time", "", 0, 0, 3, 0, false, sprites, 16711680, 143190, 143190, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, 77, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Subscription Vendor", "Exchange your subscription token to subscription time", "", 0, 0, 3, 0, false, sprites, 16761440, 143190, 143190, 15523536, 145, 230, 6, 6, 5, i++));
		sprites = new int[]{241, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Gaia", "The earth queen with a rotten heart", "", 78, 79, 79, 80, true, sprites, 0, 0, 0, 0, 275, 262, 11, 11, 30, i++));
		sprites = new int[]{0, -1, -1, -1, -1, -1, 318, 319, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ironman", "An Ironman", "Armour", 0, 0, 0, 0, false, sprites, 6751590, 0, 14, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, -1, -1, -1, -1, -1, 535, 538, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ultimate Ironman", "An Ultimate Ironman", "Armour", 0, 0, 0, 0, false, sprites, 11167296, 8, 14, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, -1, -1, -1, -1, 323, 324, 325, -1, -1, -1, -1};
		npcs.add(new NPCDef("Hardcore Ironman", "A Hardcore Ironman", "Armour", 0, 0, 0, 0, false, sprites, 11167296, 8, 14, 13415270, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{309, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Greatwood", "A scary hard slamming tree", "", 255, 245, 400, 300, true, sprites, 0, 0, 0, 0, 345, 410, 11, 11, 30, i++));
		sprites = new int[]{6, 1, 2, -1, -1, 77, 76, 81, -1, -1, -1, -1};
		npcs.add(new NPCDef("Wizard Sedridor", "An old wizard", "", 0, 0, 0, 0, false, sprites, 16777215, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, 63};
		npcs.add(new NPCDef("Scot Ruth", "A smelly, dirty dwarf", "", 20, 17, 16, 20, false, sprites, 7360576, 3158064, 3158064, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, 87, -1, -1, -1, -1};
		npcs.add(new NPCDef("Gardener", "She takes care of the plants around", shopOption, 25, 25, 10, 20, false, sprites, 16753488, 5286432, 10510400, 13415270, 125, 225, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Gramat", "He looks worried", "", 20, 17, 16, 20, false, sprites, 7360576, 9465888, 13393952, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Dwarven Smithy", "A master of metals", "", 20, 17, 16, 20, false, sprites, 7360576, 9465888, 13393952, 15523536, 121, 176, 6, 6, 5, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 45, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Dwarven Youth", "He is upset", "", 20, 17, 16, 20, false, sprites, 7360576, 9465888, 13393952, 15523536, 90, 130, 6, 6, 5, i++));
		sprites = new int[]{164, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Balrog", "A massive black demon", "", 999, 250, 80, 200, true, sprites, 0, 0, 0, 0, 450, 480, 11, 11, 30, i++));
		sprites = new int[]{6, 1, 2, -1, -1, -1, 83, 87, -1, -1, -1, -1};
		npcs.add(new NPCDef("Silicius", "A Peaceful monk", "", 12, 13, 15, 12, false, sprites, 16761440, 65535, 255, 15523536, 145, 220, 6, 6, 5, i++));
		if (Config.S_WANT_CUSTOM_SPRITES) {
			sprites = new int[]{3, 4, 2, -1, -1, -1, 426, -1, 46, -1, -1, 428};
		} else {
			sprites = new int[]{3, 4, 2, -1, -1, -1, 426, -1, 46, -1, -1, -1};
		}
		npcs.add(new NPCDef("Robin Banks", "A master thief", "", 34, 32, 37, 33, false, sprites, 1, 2, 3, 15523536, 150, 230, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, 9, -1};
		npcs.add(new NPCDef("Mum", "The greatest woman in the world", "", 1, 99, 3, 1, false, sprites, 16752704, 3211263, 14540032, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, 122, 191, 197, 189, -1, -1, -1, 68};
		npcs.add(new NPCDef("Ester", "She looks quite frazzled", "", 1, 99, 3, 1, false, sprites, 16763992, 3211263, 14540032, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{472, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Bunny", "A fluffy bunny", "", 1, 1, 10, 1, false, sprites, 0, 0, 0, 0, 95, 85, 7, 7, 10, i++));
		sprites = new int[]{473, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Duck", "Definitely not the ugly one", "", 1, 1, 10, 1, false, sprites, 1, 2, 3, 4, 85, 95, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, 52, 8, 83, 87, -1, -1, -1, -1};
		npcs.add(new NPCDef("PKBOT", "He looks scary.", "", 41, 99, 87, 1, true, sprites, 16761440, 8409120, 33415270, 15523536, 145, 220, 6, 6, 5, i++));
		// head, shirt, pants, shield, weapon, hat, body, legs, gloves, boots, amulet, cape
		sprites = new int[]{3, 1, 2, -1, 228, 483, 82, 88, 155, -1, -1, -1};
		npcs.add(new NPCDef("Death", "He sure could do with gaining some weight", "", 15, 15, 12, 12, false, sprites, 1, 2, 3, 16777215, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Loan Officer", "He can lend me some money", "", 11, 8, 7, 11, false, sprites, 15921906, 2, 3, 7296823, 145, 220, 6, 6, 5, i++));
		// head, shirt, pants, shield, weapon, hat, body, legs, gloves, boots, amulet, cape
		sprites = new int[]{6, 1, 2, -1, -1, 208, 395, 396, 46, -1, -1, -1};
		npcs.add(new NPCDef("Santa", "He sure could do with gaining some weight", "", 123, 123, 123, 123, false, sprites, 16777215, 0xFF0000, 0xFF0000, 15523536, 160, 220, 6, 6, 5, i++));
		// head, shirt, pants, shield, weapon, hat, body, legs, gloves, boots, amulet, cape
		sprites = new int[]{7, 1, 2, -1, -1, 500, 501, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Kresh", "He's kind of like an onion", "", 123, 123, 123, 123, false, sprites, 0, 0xFFFFFF, 0x802415, 0xb5ff1d, 160, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, -1, -1, 46, -1, 10, 519};
		npcs.add(new NPCDef("Lily", "She has a green thumb", "", 1, 1, 10, 1, false, sprites, 0xEEBB70, 0x006600, 0x663300, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{5, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Peter Skippin", "Shut up, Meg", "", 20, 20, 20, 20, false, sprites, 11167296, 0xFFFFFF, 0x014E00, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, -1, 2, -1, -1, -1, 531, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Mortimer", "A not-so-wealthy tradesman", "", 11, 8, 7, 11, false, sprites, 15921906, 2, 0x5C5C5C, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, -1, 2, -1, -1, -1, 532, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Randolph", "A not-so-wealthy tradesman", "", 11, 8, 7, 11, false, sprites, 15921906, 2, 0x303030, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{3, 4, 2, -1, -1, -1, 213, 214, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ana (not in a barrel)", "I should update my client.", "", 17, 15, 16, 18, false, sprites, 16760880, 8409120, 8409120, 10056486, 120, 220, 6, 6, 5, i++));
		sprites = new int[]{533, 139, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Biggum Flodrot", "Biggum Flodrot, goblin hero", "", 99, 99, 99, 99, false, sprites, 0, 0, 0, 0, 219, 206, 9, 8, 5, i++));
		sprites = new int[]{133, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Spookie", "A spooky, scary skeleton!", "", 0, 0, 10, 1, false, sprites, 0, 0, 0, 0, 216, 234, 11, 11, 5, i++));
		npcs.add(new NPCDef("Scarie", "A spooky, scary skeleton!", "", 0, 0, 10, 1, false, sprites, 0, 0, 0, 0, 216, 234, 11, 11, 5, i++));
		// head, shirt, pants, shield, weapon, hat, body, legs, gloves, boots, amulet, cape
		sprites = new int[]{0, 1, 2, -1, 109, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Todd Sandyman", "Some of the children call him \"The White Ogre\"", "", 0, 0, 3, 0, false, sprites, 16753488, 0xFFFFFF, 0x663300, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{137, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Praeteritum", "The ghost of Christmas past", "", 15, 15, 5, 15, false, sprites, 0, 0, 0, 0, 201, 243, 9, 9, 5, i++));
		sprites = new int[]{137, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Praesens", "The ghost of Christmas present", "", 15, 15, 5, 15, false, sprites, 0, 0, 0, 0, 201, 243, 9, 9, 5, i++));
		sprites = new int[]{137, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Futurum", "The ghost of Christmas future", "", 15, 15, 5, 15, false, sprites, 0, 0, 0, 0, 201, 243, 9, 9, 5, i++));
		sprites = new int[]{6, 1, 2, -1, 107, -1, 45, -1, -1, 11, -1, -1};
		npcs.add(new NPCDef("Forester", "He looks after McGrubor's wood", "", 24, 22, 17, 23, false, sprites, 1, 56576, 43520, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{19, 34, 43, -1, 49, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Grey Knight", "An armoured follower of Guthix", "", 55, 58, 52, 60, true, sprites, 1, 8421504, 8421504, 15523536, 145, 220, 6, 6, 5, i++));
		sprites = new int[]{0, 557, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1};
		npcs.add(new NPCDef("Ash", "Groovy", "", 20, 20, 20, 20, false, sprites, 0x50321E, 0x00137F, 0x794B1E, 15523536, 160, 220, 6, 6, 5, i++));

		if (Config.S_WANT_CUSTOM_SPRITES) {
			// Ranael
			npcs.get(103).sprites = new int[]{3, 4, -1, -1, -1, -1, -1, 247, -1, -1, -1, -1};
			// Zenesha
			npcs.get(331).sprites = new int[]{3, 4, -1, -1, -1, -1, 56, 247, -1, -1, -1, -1};
		}
	}

	private static void loadItemDefinitions() {
		//Setup the note definition
		noteDef = new ItemDef("", "", "", 0, 438, "items:438", true, false, 0, 0, false, false, false, 0);
		certificateDef = new ItemDef("", "", "", 0, 180, "items:180", true, false, 0, 0, false, false, false, 0);

		items.add(new ItemDef("Iron Mace", "A spiky mace", "", 63, 0, "items:0", false, true, 16, 15654365, false, false, true, 0));
		items.add(new ItemDef("Iron Short Sword", "A razor sharp sword", "", 91, 1, "items:1", false, true, 16, 15654365, false, false, true, 1));
		items.add(new ItemDef("Iron Kite Shield", "A large metal shield", "", 238, 2, "items:2", false, true, 8, 15654365, false, false, true, 2));
		items.add(new ItemDef("Iron Square Shield", "A medium metal shield", "", 168, 3, "items:3", false, true, 8, 15654365, false, false, true, 3));
		items.add(new ItemDef("Wooden Shield", "A solid wooden shield", "", 20, 4, "items:4", false, true, 8, 0, false, false, true, 4));
		items.add(new ItemDef("Medium Iron Helmet", "A medium sized helmet", "", 84, 5, "items:5", false, true, 32, 15654365, false, false, true, 5));
		items.add(new ItemDef("Large Iron Helmet", "A full face helmet", "", 154, 6, "items:6", false, true, 33, 15654365, false, false, true, 6));
		items.add(new ItemDef("Iron Chain Mail Body", "A series of connected metal rings", "", 210, 7, "items:7", false, true, 64, 15654365, false, false, true, 7));
		items.add(new ItemDef("Iron Plate Mail Body", "Provides excellent protection", "", 560, 8, "items:8", false, true, 322, 15654365, false, false, true, 8));
		items.add(new ItemDef("Iron Plate Mail Legs", "These look pretty heavy", "", 280, 9, "items:9", false, true, 644, 15654365, false, false, true, 9));
		items.add(new ItemDef("Coins", "Lovely money!", "", 1, 10, "items:10", true, false, 0, 0, false, false, false, 10));
		items.add(new ItemDef("Bronze Arrows", "Arrows with bronze heads", "", 2, 11, "items:11", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 16737817, false, false, false, 11));
		items.add(new ItemDef("Iron Axe", "A woodcutters axe", "", 56, 12, "items:12", false, false, 0, 15654365, false, false, true, 12));

		items.add(new ItemDef("Knife", "A dangerous looking knife", "", 6, 13, "items:13", false, false, 0, 0, false, false, true, 13));
		items.add(new ItemDef("Logs", "A number of wooden logs", "", 4, 14, "items:14", false, false, 0, 0, false, false, true, 14));
		items.add(new ItemDef("Leather Armour", "Better than no armour!", "", 21, 15, "items:15", false, true, 64, 0, false, false, true, 15));
		items.add(new ItemDef("Leather Gloves", "These will keep my hands warm!", "", 6, 17, "items:17", false, true, 256, 0, false, false, true, 16));
		items.add(new ItemDef("Boots", "Comfortable leather boots", "", 6, 16, "items:16", false, true, 512, 0, false, false, true, 17));
		items.add(new ItemDef("Cabbage", "Yuck I don't like cabbage", "Eat", 1, 18, "items:18", false, false, 0, 0, false, false, true, 18));
		items.add(new ItemDef("Egg", "A nice fresh egg", "", 4, 19, "items:19", false, false, 0, 0, false, false, true, 19));
		items.add(new ItemDef("Bones", "Ew it's a pile of bones", "Bury", 1, 20, "items:20", false, false, 0, 0, false, false, true, 20));
		items.add(new ItemDef("Bucket", "It's a wooden bucket", "", 2, 22, "items:22", false, false, 0, 1052688, false, false, true, 21));
		items.add(new ItemDef("Milk", "It's a bucket of milk", "", 6, 22, "items:22", false, false, 0, 0, false, false, true, 22));
		items.add(new ItemDef("Flour", "A little heap of flour", "", 2, 23, "items:23", false, false, 0, 0, false, true, true, 23));
		items.add(new ItemDef("Amulet of GhostSpeak", "It lets me talk to ghosts", "", 35, 24, "items:24", false, true, 1024, 0, false, true, false, 24));
		items.add(new ItemDef("Silverlight key 1", "A key given to me by Wizard Traiborn", "", 1, 25, "items:25", false, false, 0, 14540253, false, true, false, 25));
		items.add(new ItemDef("Silverlight key 2", "A key given to me by Captain Rovin", "", 1, 25, "items:25", false, false, 0, 14540253, false, true, false, 26));
		items.add(new ItemDef("skull", "A spooky looking skull", "", 1, 26, "items:26", false, false, 0, 0, false, true, true, 27));
		items.add(new ItemDef("Iron dagger", "Short but pointy", "", 35, 80, "items:80", false, true, 16, 15654365, false, false, true, 28));
		items.add(new ItemDef("grain", "Some wheat heads", "", 2, 27, "items:27", false, false, 0, 0, false, false, true, 29));
		items.add(new ItemDef("Book", "", "read", 1, 28, "items:28", false, false, 0, 16755370, false, true, false, 30));
		items.add(new ItemDef("Fire-Rune", "One of the 4 basic elemental runes", "", 4, 30, "items:30", true, false, 0, 0, false, false, false, 31));
		items.add(new ItemDef("Water-Rune", "One of the 4 basic elemental runes", "", 4, 31, "items:31", true, false, 0, 0, false, false, false, 32));
		items.add(new ItemDef("Air-Rune", "One of the 4 basic elemental runes", "", 4, 32, "items:32", true, false, 0, 0, false, false, false, 33));
		items.add(new ItemDef("Earth-Rune", "One of the 4 basic elemental runes", "", 4, 33, "items:33", true, false, 0, 0, false, false, false, 34));
		items.add(new ItemDef("Mind-Rune", "Used for low level missile spells", "", 3, 34, "items:34", true, false, 0, 0, false, false, false, 35));
		items.add(new ItemDef("Body-Rune", "Used for curse spells", "", 3, 35, "items:35", true, false, 0, 0, false, false, false, 36));
		items.add(new ItemDef("Life-Rune", "Used for summon spells", "", 1, 36, "items:36", true, false, 0, 0, true, false, false, 37));
		items.add(new ItemDef("Death-Rune", "Used for high level missile spells", "", 20, 37, "items:37", true, false, 0, 0, false, false, false, 38));
		items.add(new ItemDef("Needle", "Used with a thread to make clothes", "", 1, 38, "items:38", true, false, 0, 0, false, false, false, 39));
		items.add(new ItemDef("Nature-Rune", "Used for alchemy spells", "", 7, 39, "items:39", true, false, 0, 0, false, false, false, 40));
		items.add(new ItemDef("Chaos-Rune", "Used for mid level missile spells", "", 10, 40, "items:40", true, false, 0, 0, false, false, false, 41));
		items.add(new ItemDef("Law-Rune", "Used for teleport spells", "", 12, 41, "items:41", true, false, 0, 0, false, false, false, 42));
		items.add(new ItemDef("Thread", "Used with a needle to make clothes", "", 1, 42, "items:42", true, false, 0, 0, false, false, false, 43));
		items.add(new ItemDef("Holy Symbol of saradomin", "This needs a string putting on it", "", 200, 43, "items:43", false, false, 0, 0, false, false, true, 44));
		items.add(new ItemDef("Unblessed Holy Symbol", "This needs blessing", "", 200, 44, "items:44", false, true, 1024, 0, false, false, true, 45));
		items.add(new ItemDef("Cosmic-Rune", "Used for enchant spells", "", 15, 45, "items:45", true, false, 0, 0, false, false, false, 46));
		items.add(new ItemDef("key", "The key to get into the phoenix gang", "", 1, 25, "items:25", false, false, 0, 15636736, false, true, false, 47));
		items.add(new ItemDef("key", "The key to the phoenix gang's weapons store", "", 1, 25, "items:25", false, false, 0, 15636736, false, false, true, 48));
		items.add(new ItemDef("scroll", "An intelligence Report", "", 5, 29, "items:29", false, false, 0, 0, false, false, true, 49));
		items.add(new ItemDef("Water", "It's a bucket of water", "", 6, 22, "items:22", false, false, 0, 5724145, false, false, true, 50));
		items.add(new ItemDef("Silverlight key 3", "A key I found in a drain", "", 1, 25, "items:25", false, false, 0, 14540253, false, true, false, 51));
		items.add(new ItemDef("Silverlight", "A magic sword", "", 50, 81, "items:81", false, true, 16, 0, false, false, true, 52));
		items.add(new ItemDef("Broken shield", "Half of the shield of Arrav", "", 1, 46, "items:46", false, false, 0, 0, false, false, true, 53));
		items.add(new ItemDef("Broken shield", "Half of the shield of Arrav", "", 1, 47, "items:47", false, false, 0, 0, false, false, true, 54));
		items.add(new ItemDef("Cadavaberries", "Poisonous berries", "", 1, 21, "items:21", false, false, 0, 15161265, false, false, true, 55));
		items.add(new ItemDef("message", "A message from Juliet to Romeo", "", 1, 29, "items:29", false, false, 0, 0, false, false, true, 56));
		items.add(new ItemDef("Cadava", "I'm meant to give this to Juliet", "", 1, 48, "items:48", false, false, 0, 11620466, false, true, true, 57));
		items.add(new ItemDef("potion", "this is meant to be good for spots", "", 1, 48, "items:48", false, false, 0, 5289585, false, false, true, 58));
		items.add(new ItemDef("Phoenix Crossbow", "Former property of the phoenix gang", "", 4, 49, "items:49", false, true, 16, 0, false, false, true, 59));
		items.add(new ItemDef("Crossbow", "This fires crossbow bolts", "", 70, 49, "items:49", false, true, 16, 0, false, false, true, 60));
		items.add(new ItemDef("Certificate", "I can use this to claim a reward from the king", "", 1, 29, "items:29", false, false, 0, 0, false, false, true, 61));
		items.add(new ItemDef("bronze dagger", "Short but pointy", "", 10, 80, "items:80", false, true, 16, 16737817, false, false, true, 62));
		items.add(new ItemDef("Steel dagger", "Short but pointy", "", 125, 80, "items:80", false, true, 16, 15658734, false, false, true, 63));
		items.add(new ItemDef("Mithril dagger", "Short but pointy", "", 325, 80, "items:80", false, true, 16, 10072780, false, false, true, 64));
		items.add(new ItemDef("Adamantite dagger", "Short but pointy", "", 800, 80, "items:80", false, true, 16, 11717785, false, false, true, 65));
		items.add(new ItemDef("Bronze Short Sword", "A razor sharp sword", "", 26, 1, "items:1", false, true, 16, 16737817, false, false, true, 66));
		items.add(new ItemDef("Steel Short Sword", "A razor sharp sword", "", 325, 1, "items:1", false, true, 16, 15658734, false, false, true, 67));
		items.add(new ItemDef("Mithril Short Sword", "A razor sharp sword", "", 845, 1, "items:1", false, true, 16, 10072780, false, false, true, 68));
		items.add(new ItemDef("Adamantite Short Sword", "A razor sharp sword", "", 2080, 1, "items:1", false, true, 16, 11717785, false, false, true, 69));
		items.add(new ItemDef("Bronze Long Sword", "A razor sharp sword", "", 40, 81, "items:81", false, true, 16, 16737817, false, false, true, 70));
		items.add(new ItemDef("Iron Long Sword", "A razor sharp sword", "", 140, 81, "items:81", false, true, 16, 15654365, false, false, true, 71));
		items.add(new ItemDef("Steel Long Sword", "A razor sharp sword", "", 500, 81, "items:81", false, true, 16, 15658734, false, false, true, 72));
		items.add(new ItemDef("Mithril Long Sword", "A razor sharp sword", "", 1300, 81, "items:81", false, true, 16, 10072780, false, false, true, 73));
		items.add(new ItemDef("Adamantite Long Sword", "A razor sharp sword", "", 3200, 81, "items:81", false, true, 16, 11717785, false, false, true, 74));
		items.add(new ItemDef("Rune long sword", "A razor sharp sword", "", 32000, 81, "items:81", false, true, 16, 65535, false, false, true, 75));
		items.add(new ItemDef("Bronze 2-handed Sword", "A very large sword", "", 80, 82, "items:82", false, true, 8216, 16737817, false, false, true, 76));
		items.add(new ItemDef("Iron 2-handed Sword", "A very large sword", "", 280, 82, "items:82", false, true, 8216, 15654365, false, false, true, 77));
		items.add(new ItemDef("Steel 2-handed Sword", "A very large sword", "", 1000, 82, "items:82", false, true, 8216, 15658734, false, false, true, 78));
		items.add(new ItemDef("Mithril 2-handed Sword", "A very large sword", "", 2600, 82, "items:82", false, true, 8216, 10072780, false, false, true, 79));
		items.add(new ItemDef("Adamantite 2-handed Sword", "A very large sword", "", 6400, 82, "items:82", false, true, 8216, 11717785, false, false, true, 80));
		items.add(new ItemDef("rune 2-handed Sword", "A very large sword", "", 64000, 82, "items:82", false, true, 8216, 65535, false, false, true, 81));
		items.add(new ItemDef("Bronze Scimitar", "A vicious curved sword", "", 32, 83, "items:83", false, true, 16, 16737817, false, false, true, 82));
		items.add(new ItemDef("Iron Scimitar", "A vicious curved sword", "", 112, 83, "items:83", false, true, 16, 15654365, false, false, true, 83));
		items.add(new ItemDef("Steel Scimitar", "A vicious curved sword", "", 400, 83, "items:83", false, true, 16, 15658734, false, false, true, 84));
		items.add(new ItemDef("Mithril Scimitar", "A vicious curved sword", "", 1040, 83, "items:83", false, true, 16, 10072780, false, false, true, 85));
		items.add(new ItemDef("Adamantite Scimitar", "A vicious curved sword", "", 2560, 83, "items:83", false, true, 16, 11717785, false, false, true, 86));
		items.add(new ItemDef("bronze Axe", "A woodcutters axe", "", 16, 12, "items:12", false, false, 0, 16737817, false, false, true, 87));
		items.add(new ItemDef("Steel Axe", "A woodcutters axe", "", 200, 12, "items:12", false, false, 0, 15658734, false, false, true, 88));
		items.add(new ItemDef("Iron battle Axe", "A vicious looking axe", "", 182, 84, "items:84", false, true, 16, 15654365, false, false, true, 89));
		items.add(new ItemDef("Steel battle Axe", "A vicious looking axe", "", 650, 84, "items:84", false, true, 16, 15658734, false, false, true, 90));
		items.add(new ItemDef("Mithril battle Axe", "A vicious looking axe", "", 1690, 84, "items:84", false, true, 16, 10072780, false, false, true, 91));
		items.add(new ItemDef("Adamantite battle Axe", "A vicious looking axe", "", 4160, 84, "items:84", false, true, 16, 11717785, false, false, true, 92));
		items.add(new ItemDef("Rune battle Axe", "A vicious looking axe", "", 41600, 84, "items:84", false, true, 16, 65535, false, false, true, 93));
		items.add(new ItemDef("Bronze Mace", "A spiky mace", "", 18, 0, "items:0", false, true, 16, 16737817, false, false, true, 94));
		items.add(new ItemDef("Steel Mace", "A spiky mace", "", 225, 0, "items:0", false, true, 16, 15658734, false, false, true, 95));
		items.add(new ItemDef("Mithril Mace", "A spiky mace", "", 585, 0, "items:0", false, true, 16, 10072780, false, false, true, 96));
		items.add(new ItemDef("Adamantite Mace", "A spiky mace", "", 1440, 0, "items:0", false, true, 16, 11717785, false, false, true, 97));
		items.add(new ItemDef("Rune Mace", "A spiky mace", "", 14400, 0, "items:0", false, true, 16, 65535, false, false, true, 98));
		items.add(new ItemDef("Brass key", "I wonder what this is the key to", "", 1, 25, "items:25", false, false, 0, 16750848, false, false, true, 99));
		items.add(new ItemDef("staff", "It's a slightly magical stick", "", 15, 85, "items:85", false, true, 16, 10072780, false, false, true, 100));
		items.add(new ItemDef("Air Staff", "A staff attuned to air magic", "", 750, 422, "items:422", false, true, 16, 10072780, false, false, true, 101));
		items.add(new ItemDef("Water Staff", "A staff attuned to water magic", "", 750, 423, "items:423", false, true, 16, 10072780, false, false, true, 102));
		items.add(new ItemDef("Earth Staff", "A staff attuned to earth magic", "", 750, 424, "items:424", false, true, 16, 10072780, false, false, true, 103));
		items.add(new ItemDef("Medium Bronze Helmet", "A medium sized helmet", "", 24, 5, "items:5", false, true, 32, 16737817, false, false, true, 104));
		items.add(new ItemDef("Medium Steel Helmet", "A medium sized helmet", "", 300, 5, "items:5", false, true, 32, 15658734, false, false, true, 105));
		items.add(new ItemDef("Medium Mithril Helmet", "A medium sized helmet", "", 780, 5, "items:5", false, true, 32, 10072780, false, false, true, 106));
		items.add(new ItemDef("Medium Adamantite Helmet", "A medium sized helmet", "", 1920, 5, "items:5", false, true, 32, 11717785, false, false, true, 107));
		items.add(new ItemDef("Large Bronze Helmet", "A full face helmet", "", 44, 6, "items:6", false, true, 33, 16737817, false, false, true, 108));
		items.add(new ItemDef("Large Steel Helmet", "A full face helmet", "", 550, 6, "items:6", false, true, 33, 15658734, false, false, true, 109));
		items.add(new ItemDef("Large Mithril Helmet", "A full face helmet", "", 1430, 6, "items:6", false, true, 33, 10072780, false, false, true, 110));
		items.add(new ItemDef("Large Adamantite Helmet", "A full face helmet", "", 3520, 6, "items:6", false, true, 33, 11717785, false, false, true, 111));
		items.add(new ItemDef("Large Rune Helmet", "A full face helmet", "", 35200, 6, "items:6", false, true, 33, 65535, false, false, true, 112));
		items.add(new ItemDef("Bronze Chain Mail Body", "A series of connected metal rings", "", 60, 7, "items:7", false, true, 64, 16737817, false, false, true, 113));
		items.add(new ItemDef("Steel Chain Mail Body", "A series of connected metal rings", "", 750, 7, "items:7", false, true, 64, 15658734, false, false, true, 114));
		items.add(new ItemDef("Mithril Chain Mail Body", "A series of connected metal rings", "", 1950, 7, "items:7", false, true, 64, 10072780, false, false, true, 115));
		items.add(new ItemDef("Adamantite Chain Mail Body", "A series of connected metal rings", "", 4800, 7, "items:7", false, true, 64, 11717785, false, false, true, 116));
		items.add(new ItemDef("Bronze Plate Mail Body", "Provides excellent protection", "", 160, 8, "items:8", false, true, 322, 16737817, false, false, true, 117));
		items.add(new ItemDef("Steel Plate Mail Body", "Provides excellent protection", "", 2000, 8, "items:8", false, true, 322, 15658734, false, false, true, 118));
		items.add(new ItemDef("Mithril Plate Mail Body", "Provides excellent protection", "", 5200, 8, "items:8", false, true, 322, 10072780, false, false, true, 119));
		items.add(new ItemDef("Adamantite Plate Mail Body", "Provides excellent protection", "", 12800, 8, "items:8", false, true, 322, 11717785, false, false, true, 120));
		items.add(new ItemDef("Steel Plate Mail Legs", "These look pretty heavy", "", 1000, 9, "items:9", false, true, 644, 15658734, false, false, true, 121));
		items.add(new ItemDef("Mithril Plate Mail Legs", "These look pretty heavy", "", 2600, 9, "items:9", false, true, 644, 10072780, false, false, true, 122));
		items.add(new ItemDef("Adamantite Plate Mail Legs", "These look pretty heavy", "", 6400, 9, "items:9", false, true, 644, 11717785, false, false, true, 123));
		items.add(new ItemDef("Bronze Square Shield", "A medium metal shield", "", 48, 3, "items:3", false, true, 8, 16737817, false, false, true, 124));
		items.add(new ItemDef("Steel Square Shield", "A medium metal shield", "", 600, 3, "items:3", false, true, 8, 15658734, false, false, true, 125));
		items.add(new ItemDef("Mithril Square Shield", "A medium metal shield", "", 1560, 3, "items:3", false, true, 8, 10072780, false, false, true, 126));
		items.add(new ItemDef("Adamantite Square Shield", "A medium metal shield", "", 3840, 3, "items:3", false, true, 8, 11717785, false, false, true, 127));
		items.add(new ItemDef("Bronze Kite Shield", "A large metal shield", "", 68, 2, "items:2", false, true, 8, 16737817, false, false, true, 128));
		items.add(new ItemDef("Steel Kite Shield", "A large metal shield", "", 850, 2, "items:2", false, true, 8, 15658734, false, false, true, 129));
		items.add(new ItemDef("Mithril Kite Shield", "A large metal shield", "", 2210, 2, "items:2", false, true, 8, 10072780, false, false, true, 130));
		items.add(new ItemDef("Adamantite Kite Shield", "A large metal shield", "", 5440, 2, "items:2", false, true, 8, 11717785, false, false, true, 131));
		items.add(new ItemDef("cookedmeat", "Mmm this looks tasty", "Eat", 4, 60, "items:60", false, false, 0, 13395507, false, false, true, 132));
		items.add(new ItemDef("raw chicken", "I need to cook this first", "", 1, 60, "items:60", false, false, 0, 16747571, false, false, true, 133));
		items.add(new ItemDef("burntmeat", "Oh dear", "", 1, 60, "items:60", false, false, 0, 5000268, false, false, true, 134));
		items.add(new ItemDef("pot", "This pot is empty", "", 1, 61, "items:61", false, false, 0, 16748885, false, false, true, 135));
		items.add(new ItemDef("flour", "There is flour in this pot", "", 10, 62, "items:62", false, false, 0, 0, false, false, true, 136));
		items.add(new ItemDef("bread dough", "Some uncooked dough", "", 1, 63, "items:63", false, false, 0, 0, false, false, true, 137));
		items.add(new ItemDef("bread", "Nice crispy bread", "Eat", 12, 64, "items:64", false, false, 0, 16739379, false, false, true, 138));
		items.add(new ItemDef("burntbread", "This bread is ruined!", "", 1, 64, "items:64", false, false, 0, 5000268, false, false, true, 139));
		items.add(new ItemDef("jug", "This jug is empty", "", 1, 65, "items:65", false, false, 0, 65856, false, false, true, 140));
		items.add(new ItemDef("water", "It's full of water", "", 1, 65, "items:65", false, false, 0, 12632319, false, false, true, 141));
		items.add(new ItemDef("wine", "It's full of wine", "Drink", 1, 65, "items:65", false, false, 0, 12851224, false, false, true, 142));
		items.add(new ItemDef("grapes", "Good grapes for wine making", "", 1, 21, "items:21", false, false, 0, 9386967, false, false, true, 143));
		items.add(new ItemDef("Tin shears", "Tin shears for harvesting", "", 1, 66, "items:66", false, false, 0, 0xB7C9D9, false, false, true, 144));
		items.add(new ItemDef("wool", "I think this came from a sheep", "", 1, 67, "items:67", false, false, 0, 0, false, false, true, 145));
		items.add(new ItemDef("fur", "This would make warm clothing", "", 10, 68, "items:68", false, false, 0, 12288534, false, false, true, 146));
		items.add(new ItemDef("cow hide", "I should take this to the tannery", "", 1, 69, "items:69", false, false, 0, 0, false, false, true, 147));
		items.add(new ItemDef("leather", "It's a piece of leather", "", 1, 69, "items:69", false, false, 0, 16757299, false, false, true, 148));
		items.add(new ItemDef("clay", "Some hard dry clay", "", 1, 70, "items:70", false, false, 0, 15046937, false, false, true, 149));
		items.add(new ItemDef("copper ore", "this needs refining", "", 3, 70, "items:70", false, false, 0, 16737817, false, false, true, 150));
		items.add(new ItemDef("iron ore", "this needs refining", "", 17, 70, "items:70", false, false, 0, 11704729, false, false, true, 151));
		items.add(new ItemDef("gold", "this needs refining", "", 150, 73, "items:73", false, false, 0, 16763980, false, false, true, 152));
		items.add(new ItemDef("mithril ore", "this needs refining", "", 162, 70, "items:70", false, false, 0, 10072780, false, false, true, 153));
		items.add(new ItemDef("adamantite ore", "this needs refining", "", 400, 70, "items:70", false, false, 0, 11717785, false, false, true, 154));
		items.add(new ItemDef("coal", "hmm a non-renewable energy source!", "", 45, 71, "items:71", false, false, 0, 0, false, false, true, 155));
		items.add(new ItemDef("Bronze Pickaxe", "Used for mining", "", 1, 72, "items:72", false, false, 0, 16737817, false, false, true, 156));
		items.add(new ItemDef("uncut diamond", "this would be worth more cut", "", 200, 73, "items:73", false, false, 0, 0, false, false, true, 157));
		items.add(new ItemDef("uncut ruby", "this would be worth more cut", "", 100, 73, "items:73", false, false, 0, 16724736, false, false, true, 158));
		items.add(new ItemDef("uncut emerald", "this would be worth more cut", "", 50, 73, "items:73", false, false, 0, 3394611, false, false, true, 159));
		items.add(new ItemDef("uncut sapphire", "this would be worth more cut", "", 25, 73, "items:73", false, false, 0, 19711, false, false, true, 160));
		items.add(new ItemDef("diamond", "this looks valuable", "", 2000, 74, "items:74", false, false, 0, 0, false, false, true, 161));
		items.add(new ItemDef("ruby", "this looks valuable", "", 1000, 74, "items:74", false, false, 0, 16724736, false, false, true, 162));
		items.add(new ItemDef("emerald", "this looks valuable", "", 500, 74, "items:74", false, false, 0, 3394611, false, false, true, 163));
		items.add(new ItemDef("sapphire", "this looks valuable", "", 250, 74, "items:74", false, false, 0, 19711, false, false, true, 164));
		items.add(new ItemDef("Herb", "I need a closer look to identify this", "Identify", 1, 75, "items:75", false, false, 0, 0, true, false, true, 165));
		items.add(new ItemDef("tinderbox", "useful for lighting a fire", "", 1, 76, "items:76", false, false, 0, 0, false, false, true, 166));
		items.add(new ItemDef("chisel", "good for detailed crafting", "", 1, 77, "items:77", false, false, 0, 0, false, false, true, 167));
		items.add(new ItemDef("hammer", "good for hitting things!", "", 1, 78, "items:78", false, false, 0, 0, false, false, true, 168));
		items.add(new ItemDef("bronze bar", "it's a bar of bronze", "", 8, 79, "items:79", false, false, 0, 16737817, false, false, true, 169));
		items.add(new ItemDef("iron bar", "it's a bar of iron", "", 28, 79, "items:79", false, false, 0, 15654365, false, false, true, 170));
		items.add(new ItemDef("steel bar", "it's a bar of steel", "", 100, 79, "items:79", false, false, 0, 15658734, false, false, true, 171));
		items.add(new ItemDef("gold bar", "this looks valuable", "", 300, 79, "items:79", false, false, 0, 16763980, false, false, true, 172));
		items.add(new ItemDef("mithril bar", "it's a bar of mithril", "", 300, 79, "items:79", false, false, 0, 10072780, false, false, true, 173));
		items.add(new ItemDef("adamantite bar", "it's a bar of adamantite", "", 640, 79, "items:79", false, false, 0, 11717785, false, false, true, 174));
		items.add(new ItemDef("Pressure gauge", "It looks like part of a machine", "", 1, 50, "items:50", false, false, 0, 0, false, true, false, 175));
		items.add(new ItemDef("Fish Food", "Keeps  your pet fish strong and healthy", "", 1, 51, "items:51", false, false, 0, 0, false, false, true, 176));
		items.add(new ItemDef("Poison", "This stuff looks nasty", "", 1, 52, "items:52", false, false, 0, 0, false, false, true, 177));
		items.add(new ItemDef("Poisoned fish food", "Doesn't seem very nice to the poor fishes", "", 1, 51, "items:51", false, false, 0, 0, false, true, false, 178));
		items.add(new ItemDef("spinach roll", "A home made spinach thing", "Eat", 1, 53, "items:53", false, false, 0, 0, false, false, true, 179));
		items.add(new ItemDef("Bad wine", "Oh dear", "Drink", 1, 65, "items:65", false, false, 0, 12851224, false, false, true, 180));
		items.add(new ItemDef("Ashes", "A heap of ashes", "", 2, 23, "items:23", false, false, 0, 11184810, false, false, true, 181));
		items.add(new ItemDef("Apron", "A mostly clean apron", "", 2, 58, "items:58", false, true, 1024, 0, false, false, true, 182));
		items.add(new ItemDef("Cape", "A bright red cape", "", 2, 59, "items:59", false, true, 2048, 16711680, false, false, true, 183));
		items.add(new ItemDef("Wizards robe", "I can do magic better in this", "", 15, 87, "items:87", false, true, 64, 255, false, false, true, 184));
		items.add(new ItemDef("wizardshat", "A silly pointed hat", "", 2, 86, "items:86", false, true, 32, 255, false, false, true, 185));
		items.add(new ItemDef("Brass necklace", "I'd prefer a gold one", "", 30, 57, "items:57", false, true, 1024, 0, false, false, true, 186));
		items.add(new ItemDef("skirt", "A ladies skirt", "", 2, 88, "items:88", false, true, 128, 255, false, false, true, 187));
		items.add(new ItemDef("Longbow", "A Nice sturdy bow", "", 80, 54, "items:54", false, true, 24, 65280, 8537122, false, false, true, 188));
		items.add(new ItemDef("Shortbow", "Short but effective", "", 50, 55, "items:55", false, true, 24, 65280, 8537122, false, false, true, 189));
		items.add(new ItemDef("Crossbow bolts", "Good if you have a crossbow!", "", 3, 56, "items:56", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1001 : 0, 0, false, false, false, 190));
		items.add(new ItemDef("Brown apron", "this will help keep my clothes clean", "", 2, 58, "items:58", false, true, 1024, 9789488, false, false, true, 191));
		items.add(new ItemDef("Chef's hat", "What a silly hat", "", 2, 89, "items:89", false, true, 32, 0, false, false, true, 192));
		items.add(new ItemDef("Beer", "A glass of frothy ale", "drink", 2, 90, "items:90", false, false, 0, 0, false, false, true, 193));
		items.add(new ItemDef("skirt", "A ladies skirt", "", 2, 88, "items:88", false, true, 128, 16036851, false, false, true, 194));
		items.add(new ItemDef("skirt", "A ladies skirt", "", 2, 88, "items:88", false, true, 128, 4210752, false, false, true, 195));
		items.add(new ItemDef("Black Plate Mail Body", "Provides excellent protection", "", 3840, 8, "items:8", false, true, 322, 3158064, false, false, true, 196));
		items.add(new ItemDef("Fire Staff", "A staff attuned to fire magic", "", 750, 425, "items:425", false, true, 16, 10072780, false, false, true, 197));
		items.add(new ItemDef("Magic Staff", "A Magical staff", "", 200, 91, "items:91", false, true, 16, 16777215, false, false, true, 198));
		items.add(new ItemDef("wizardshat", "A silly pointed hat", "", 2, 86, "items:86", false, true, 32, 4210752, false, false, true, 199));
		items.add(new ItemDef("silk", "It's a sheet of silk", "", 30, 92, "items:92", false, false, 0, 16724172, false, false, true, 200));
		items.add(new ItemDef("flier", "Get your axes from Bob's axes", "", 1, 29, "items:29", false, false, 0, 0, false, false, true, 201));
		items.add(new ItemDef("tin ore", "this needs refining", "", 3, 70, "items:70", false, false, 0, 13810105, false, false, true, 202));
		items.add(new ItemDef("Mithril Axe", "A powerful axe", "", 520, 12, "items:12", false, false, 0, 10072780, false, false, true, 203));
		items.add(new ItemDef("Adamantite Axe", "A powerful axe", "", 1280, 12, "items:12", false, false, 0, 11717785, false, false, true, 204));
		items.add(new ItemDef("bronze battle Axe", "A vicious looking axe", "", 52, 84, "items:84", false, true, 16, 16737817, false, false, true, 205));
		items.add(new ItemDef("Bronze Plate Mail Legs", "These look pretty heavy", "", 80, 9, "items:9", false, true, 644, 16737817, false, false, true, 206));
		items.add(new ItemDef("Ball of wool", "Spun from sheeps wool", "", 2, 93, "items:93", false, false, 0, 0, false, false, true, 207));
		items.add(new ItemDef("Oil can", "Its pretty full", "", 3, 94, "items:94", false, false, 0, 0, false, true, false, 208));
		items.add(new ItemDef("Cape", "A warm black cape", "", 7, 59, "items:59", false, true, 2048, 2434341, false, false, true, 209));
		items.add(new ItemDef("Kebab", "A meaty Kebab", "eat", 3, 95, "items:95", false, false, 0, 0, false, false, true, 210));
		items.add(new ItemDef("Spade", "A fairly small spade", "Dig", 3, 96, "items:96", false, false, 0, 0, false, false, true, 211));
		items.add(new ItemDef("Closet Key", "A slightly smelly key", "", 1, 25, "items:25", false, false, 0, 16772608, false, true, false, 212));
		items.add(new ItemDef("rubber tube", "Its slightly charred", "", 3, 97, "items:97", false, false, 0, 0, false, true, false, 213));
		items.add(new ItemDef("Bronze Plate Mail Legs", "These look pretty heavy", "", 80, 9, "items:9", false, true, 640, 8400921, false, false, true, 214));
		items.add(new ItemDef("Iron Plate Mail Legs", "These look pretty heavy", "", 280, 9, "items:9", false, true, 640, 7824998, false, false, true, 215));
		items.add(new ItemDef("Black robe", "I can do magic better in this", "", 13, 87, "items:87", false, true, 64, 4210752, false, false, true, 216));
		items.add(new ItemDef("stake", "A very pointy stick", "", 8, 98, "items:98", false, true, 16, 16737817, false, true, false, 217));
		items.add(new ItemDef("Garlic", "A clove of garlic", "", 3, 99, "items:99", false, false, 0, 0, false, false, true, 218));
		items.add(new ItemDef("Red spiders eggs", "eewww", "", 7, 100, "items:100", false, false, 0, 0, false, false, true, 219));
		items.add(new ItemDef("Limpwurt root", "the root of a limpwurt plant", "", 7, 101, "items:101", false, false, 0, 0, false, false, true, 220));
		items.add(new ItemDef("Strength Potion", "4 doses of strength potion", "Drink", 14, 48, "items:48", false, false, 0, 15658544, false, false, true, 221));
		items.add(new ItemDef("Strength Potion", "3 doses of strength potion", "Drink", 13, 48, "items:48", false, false, 0, 15658544, false, false, true, 222));
		items.add(new ItemDef("Strength Potion", "2 doses of strength potion", "Drink", 13, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 15658544, false, false, true, 223));
		items.add(new ItemDef("Strength Potion", "1 dose of strength potion", "Drink", 11, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 15658544, false, false, true, 224));
		items.add(new ItemDef("Steel Plate Mail Legs", "These look pretty heavy", "", 1000, 9, "items:9", false, true, 640, 7829367, false, false, true, 225));
		items.add(new ItemDef("Mithril Plate Mail Legs", "These look pretty heavy", "", 2600, 9, "items:9", false, true, 640, 2245205, false, false, true, 226));
		items.add(new ItemDef("Adamantite Plate Mail Legs", "These look pretty heavy", "", 6400, 9, "items:9", false, true, 640, 4347170, false, false, true, 227));
		items.add(new ItemDef("Cabbage", "Yuck I don't like cabbage", "Eat", 1, 18, "items:18", false, false, 0, 0, false, false, true, 228));
		items.add(new ItemDef("Cape", "A thick blue cape", "", 32, 59, "items:59", false, true, 2048, 4210926, false, false, true, 229));
		items.add(new ItemDef("Large Black Helmet", "A full face helmet", "", 1056, 6, "items:6", false, true, 33, 4210752, false, false, true, 230));
		items.add(new ItemDef("Red Bead", "A small round red bead", "", 4, 102, "items:102", false, false, 0, 16711680, false, false, true, 231));
		items.add(new ItemDef("Yellow Bead", "A small round yellow bead", "", 4, 102, "items:102", false, false, 0, 16776960, false, false, true, 232));
		items.add(new ItemDef("Black Bead", "A small round black bead", "", 4, 102, "items:102", false, false, 0, 4210752, false, false, true, 233));
		items.add(new ItemDef("White Bead", "A small round white bead", "", 4, 102, "items:102", false, false, 0, 16777215, false, false, true, 234));
		items.add(new ItemDef("Amulet of accuracy", "Makes your attacks roll higher 3% of the time.", "", 100, 24, "items:24", false, true, 1024, 0, false, false, true, 235));
		items.add(new ItemDef("Redberries", "Very bright red berries", "", 3, 21, "items:21", false, false, 0, 16711680, false, false, true, 236));
		items.add(new ItemDef("Rope", "A Coil of rope", "", 18, 103, "items:103", false, false, 0, 0, false, false, true, 237));
		items.add(new ItemDef("Reddye", "A little bottle of dye", "", 5, 104, "items:104", false, false, 0, 16711680, false, false, true, 238));
		items.add(new ItemDef("Yellowdye", "A little bottle of dye", "", 5, 104, "items:104", false, false, 0, 16776960, false, false, true, 239));
		items.add(new ItemDef("Paste", "A bottle off skin coloured paste", "", 5, 104, "items:104", false, false, 0, 15523008, false, true, false, 240));
		items.add(new ItemDef("Onion", "A strong smelling onion", "", 3, 99, "items:99", false, false, 0, 15641190, false, false, true, 241));
		items.add(new ItemDef("Bronze key", "A heavy key", "", 1, 25, "items:25", false, false, 0, 16737817, false, true, false, 242));
		items.add(new ItemDef("Soft Clay", "Clay that's ready to be used", "", 2, 105, "items:105", false, false, 0, 0, false, false, true, 243));
		items.add(new ItemDef("wig", "A blonde wig", "", 2, 106, "items:106", false, false, 0, 16763992, false, true, false, 244));
		items.add(new ItemDef("wig", "A wig made from wool", "", 2, 106, "items:106", false, false, 0, 0, false, true, false, 245));
		items.add(new ItemDef("Half full wine jug", "It's half full of wine", "Drink", 1, 65, "items:65", false, false, 0, 12851224, false, false, true, 246));
		items.add(new ItemDef("Keyprint", "An imprint of a key in a lump of clay", "", 2, 107, "items:107", false, false, 0, 0, false, true, false, 247));
		items.add(new ItemDef("Black Plate Mail Legs", "These look pretty heavy", "", 1920, 9, "items:9", false, true, 644, 4210752, false, false, true, 248));
		items.add(new ItemDef("banana", "Mmm this looks tasty", "Eat", 2, 108, "items:108", false, false, 0, 0, false, false, true, 249));
		items.add(new ItemDef("pastry dough", "Some uncooked dough", "", 1, 63, "items:63", false, false, 0, 0, false, false, true, 250));
		items.add(new ItemDef("Pie dish", "For making pies in", "", 3, 110, "items:110", false, false, 0, 15634261, false, false, true, 251));
		items.add(new ItemDef("cooking apple", "I wonder what i can make with this", "", 1, 109, "items:109", false, false, 0, 0, false, false, true, 252));
		items.add(new ItemDef("pie shell", "I need to find a filling for this pie", "", 1, 111, "items:111", false, false, 0, 0, false, false, true, 253));
		items.add(new ItemDef("Uncooked apple pie", "I need to cook this first", "", 1, 112, "items:112", false, false, 0, 16633518, false, false, true, 254));
		items.add(new ItemDef("Uncooked meat pie", "I need to cook this first", "", 1, 112, "items:112", false, false, 0, 16633518, false, false, true, 255));
		items.add(new ItemDef("Uncooked redberry pie", "I need to cook this first", "", 1, 112, "items:112", false, false, 0, 16633518, false, false, true, 256));
		items.add(new ItemDef("apple pie", "Mmm Apple pie", "eat", 30, 112, "items:112", false, false, 0, 11168819, false, false, true, 257));
		items.add(new ItemDef("Redberry pie", "Looks tasty", "eat", 12, 112, "items:112", false, false, 0, 11168819, false, false, true, 258));
		items.add(new ItemDef("meat pie", "Mighty and meaty", "eat", 15, 112, "items:112", false, false, 0, 11168819, false, false, true, 259));
		items.add(new ItemDef("burntpie", "Oops", "empty dish", 1, 112, "items:112", false, false, 0, 5000268, false, false, true, 260));
		items.add(new ItemDef("Half a meat pie", "Mighty and meaty", "eat", 10, 113, "items:113", false, false, 0, 11168819, false, false, true, 261));
		items.add(new ItemDef("Half a Redberry pie", "Looks tasty", "eat", 4, 113, "items:113", false, false, 0, 11168819, false, false, true, 262));
		items.add(new ItemDef("Half an apple pie", "Mmm Apple pie", "eat", 5, 113, "items:113", false, false, 0, 11168819, false, false, true, 263));
		items.add(new ItemDef("Portrait", "It's a picture of a knight", "", 3, 114, "items:114", false, false, 0, 0, false, true, false, 264));
		items.add(new ItemDef("Faladian Knight's sword", "A razor sharp sword", "", 200, 115, "items:115", false, true, 16, 15654365, false, true, false, 265));
		items.add(new ItemDef("blurite ore", "What Strange stuff", "", 3, 70, "items:70", false, false, 0, 5263598, false, true, true, 266));
		items.add(new ItemDef("Asgarnian Ale", "A glass of frothy ale", "drink", 2, 90, "items:90", false, false, 0, 0, false, false, true, 267));
		items.add(new ItemDef("Wizard's Mind Bomb", "It's got strange bubbles in it", "drink", 2, 90, "items:90", false, false, 0, 0, false, false, true, 268));
		items.add(new ItemDef("Dwarven Stout", "A Pint of thick dark beer", "drink", 2, 90, "items:90", false, false, 0, 0, false, false, true, 269));
		items.add(new ItemDef("Eye of newt", "It seems to be looking at me", "", 3, 116, "items:116", false, false, 0, 0, false, false, true, 270));
		items.add(new ItemDef("Rat's tail", "A bit of rat", "", 3, 117, "items:117", false, false, 0, 0, false, true, false, 271));
		items.add(new ItemDef("Bluedye", "A little bottle of dye", "", 5, 104, "items:104", false, false, 0, 255, false, false, true, 272));
		items.add(new ItemDef("Goblin Armour", "Armour Designed to fit Goblins", "", 40, 118, "items:118", false, false, 0, 65535, false, false, true, 273));
		items.add(new ItemDef("Goblin Armour", "Armour Designed to fit Goblins", "", 40, 118, "items:118", false, false, 0, 16750912, false, true, true, 274));
		items.add(new ItemDef("Goblin Armour", "Armour Designed to fit Goblins", "", 40, 118, "items:118", false, false, 0, 255, false, true, true, 275));
		items.add(new ItemDef("unstrung Longbow", "I need to find a string for this", "", 60, 119, "items:119", false, false, 0, 65280, 8537122, true, false, true, 276));
		items.add(new ItemDef("unstrung shortbow", "I need to find a string for this", "", 23, 120, "items:120", false, false, 0, 65280, 8537122, true, false, true, 277));
		items.add(new ItemDef("Unfired Pie dish", "I need to put this in a pottery oven", "", 3, 110, "items:110", false, false, 0, 15632503, false, false, true, 278));
		items.add(new ItemDef("unfired pot", "I need to put this in a pottery oven", "", 1, 61, "items:61", false, false, 0, 15632503, false, false, true, 279));
		items.add(new ItemDef("arrow shafts", "I need to attach feathers to these", "", 1, 121, "items:121", true, false, 0, 0, true, false, false, 280));
		items.add(new ItemDef("Woad Leaf", "slightly bluish leaves", "", 1, 122, "items:122", true, false, 0, 0, false, false, false, 281));
		items.add(new ItemDef("Orangedye", "A little bottle of dye", "", 5, 104, "items:104", false, false, 0, 16755200, false, false, true, 282));
		items.add(new ItemDef("Gold ring", "A valuable ring", "", 350, 123, "items:123", false, Config.S_WANT_CUSTOM_SPRITES, Config.S_WANT_CUSTOM_SPRITES ? 1200 : 0, 16763980, false, false, true, 283));
		items.add(new ItemDef("Sapphire ring", "A valuable ring", "", 900, 123, "items:123", false, Config.S_WANT_CUSTOM_SPRITES, Config.S_WANT_CUSTOM_SPRITES ? 1200 : 0, 19711, false, false, true, 284));
		items.add(new ItemDef("Emerald ring", "A valuable ring", "", 1275, 123, "items:123", false, Config.S_WANT_CUSTOM_SPRITES, Config.S_WANT_CUSTOM_SPRITES ? 1200 : 0, 3394611, false, false, true, 285));
		items.add(new ItemDef("Ruby ring", "A valuable ring", "", 2025, 123, "items:123", false, Config.S_WANT_CUSTOM_SPRITES, Config.S_WANT_CUSTOM_SPRITES ? 1200 : 0, 16724736, false, false, true, 286));
		items.add(new ItemDef("Diamond ring", "A valuable ring", "", 3525, 123, "items:123", false, Config.S_WANT_CUSTOM_SPRITES, Config.S_WANT_CUSTOM_SPRITES ? 1200 : 0, 0, false, false, true, 287));
		items.add(new ItemDef("Gold necklace", "I wonder if this is valuable", "", 450, 57, "items:57", false, true, 1024, 16763980, false, false, true, 288));
		items.add(new ItemDef("Sapphire necklace", "I wonder if this is valuable", "", 1050, 57, "items:57", false, true, 1024, 19711, false, false, true, 289));
		items.add(new ItemDef("Emerald necklace", "I wonder if this is valuable", "", 1425, 57, "items:57", false, true, 1024, 3394611, false, false, true, 290));
		items.add(new ItemDef("Ruby necklace", "I wonder if this is valuable", "", 2175, 57, "items:57", false, true, 1024, 16724736, false, false, true, 291));
		items.add(new ItemDef("Diamond necklace", "I wonder if this is valuable", "", 3675, 57, "items:57", false, true, 1024, 0, false, false, true, 292));
		items.add(new ItemDef("ring mould", "Used to make gold rings", "", 5, 127, "items:127", false, false, 0, 0, false, false, true, 293));
		items.add(new ItemDef("Amulet mould", "Used to make gold amulets", "", 5, 128, "items:128", false, false, 0, 0, false, false, true, 294));
		items.add(new ItemDef("Necklace mould", "Used to make gold necklaces", "", 5, 129, "items:129", false, false, 0, 0, false, false, true, 295));
		items.add(new ItemDef("Gold Amulet", "It needs a string so I can wear it", "", 350, 126, "items:126", false, false, 0, 16763980, false, false, true, 296));
		items.add(new ItemDef("Sapphire Amulet", "It needs a string so I can wear it", "", 900, 126, "items:126", false, false, 0, 19711, false, false, true, 297));
		items.add(new ItemDef("Emerald Amulet", "It needs a string so I can wear it", "", 1275, 126, "items:126", false, false, 0, 3394611, false, false, true, 298));
		items.add(new ItemDef("Ruby Amulet", "It needs a string so I can make wear it", "", 2025, 126, "items:126", false, false, 0, 16724736, false, false, true, 299));
		items.add(new ItemDef("Diamond Amulet", "It needs a string so I can wear it", "", 3525, 126, "items:126", false, false, 0, 0, false, false, true, 300));
		items.add(new ItemDef("Gold Amulet", "I wonder if I can get this enchanted", "", 350, 125, "items:125", false, true, 1024, 16763980, false, false, true, 301));
		items.add(new ItemDef("Sapphire Amulet", "I wonder if I can get this enchanted", "", 900, 125, "items:125", false, true, 1024, 19711, false, false, true, 302));
		items.add(new ItemDef("Emerald Amulet", "I wonder if I can get this enchanted", "", 1275, 125, "items:125", false, true, 1024, 3394611, false, false, true, 303));
		items.add(new ItemDef("Ruby Amulet", "I wonder if I can get this enchanted", "", 2025, 125, "items:125", false, true, 1024, 16724736, false, false, true, 304));
		items.add(new ItemDef("Diamond Amulet", "I wonder if I can get this enchanted", "", 3525, 125, "items:125", false, true, 1024, 0, false, false, true, 305));
		items.add(new ItemDef("superchisel", "I wonder if I can get this enchanted", "twiddle", 3525, 126, "items:126", false, false, 0, 0, true, false, true, 306));
		items.add(new ItemDef("Mace of Zamorak", "This mace gives me the creeps", "", 4500, 0, "items:0", false, true, 16, 13408690, false, false, true, 307));
		items.add(new ItemDef("Bronze Plate Mail Body", "Provides excellent protection", "", 160, 8, "items:8", false, true, 322, 16737817, false, false, true, 308));
		items.add(new ItemDef("Steel Plate Mail Body", "Provides excellent protection", "", 2000, 8, "items:8", false, true, 322, 15658734, false, false, true, 309));
		items.add(new ItemDef("Mithril Plate Mail Body", "Provides excellent protection", "", 5200, 8, "items:8", false, true, 322, 10072780, false, false, true, 310));
		items.add(new ItemDef("Adamantite Plate Mail Body", "Provides excellent protection", "", 12800, 8, "items:8", false, true, 322, 11717785, false, false, true, 311));
		items.add(new ItemDef("Iron Plate Mail Body", "Provides excellent protection", "", 560, 8, "items:8", false, true, 322, 15654365, false, false, true, 312));
		items.add(new ItemDef("Black Plate Mail Body", "Provides excellent protection", "", 3840, 8, "items:8", false, true, 322, 3158064, false, false, true, 313));
		items.add(new ItemDef("Sapphire Amulet of magic", "It improves my magic", "", 900, 125, "items:125", false, true, 1024, 19711, false, false, true, 314));
		items.add(new ItemDef("Emerald Amulet of protection", "It improves my defense", "", 1275, 125, "items:125", false, true, 1024, 3394611, false, false, true, 315));
		items.add(new ItemDef("Ruby Amulet of strength", "It improves my damage", "", 2025, 125, "items:125", false, true, 1024, 16724736, false, false, true, 316));
		items.add(new ItemDef("Diamond Amulet of power", "A powerful amulet", "", 3525, 125, "items:125", false, true, 1024, 0, false, false, true, 317));
		items.add(new ItemDef("Karamja Rum", "A very strong spirit brewed in Karamja", "", 30, 131, "items:131", false, false, 0, 0, false, true, false, 318));
		items.add(new ItemDef("Cheese", "It's got holes in it", "Eat", 4, 150, "items:150", false, false, 0, 0, false, false, true, 319));
		items.add(new ItemDef("Tomato", "This would make good ketchup", "Eat", 4, 151, "items:151", false, false, 0, 0, false, false, true, 320));
		items.add(new ItemDef("Pizza Base", "I need to add some tomato next", "", 4, 152, "items:152", false, false, 0, 16768184, false, false, true, 321));
		items.add(new ItemDef("Burnt Pizza", "Oh dear!", "", 1, 152, "items:152", false, false, 0, 4210752, false, false, true, 322));
		items.add(new ItemDef("Incomplete Pizza", "I need to add some cheese next", "", 10, 153, "items:153", false, false, 0, 0, false, false, true, 323));
		items.add(new ItemDef("Uncooked Pizza", "This needs cooking", "", 25, 154, "items:154", false, false, 0, 0, false, false, true, 324));
		items.add(new ItemDef("Plain Pizza", "A cheese and tomato pizza", "Eat", 40, 154, "items:154", false, false, 0, 0, false, false, true, 325));
		items.add(new ItemDef("Meat Pizza", "A pizza with bits of meat on it", "Eat", 50, 155, "items:155", false, false, 0, 16756316, false, false, true, 326));
		items.add(new ItemDef("Anchovie Pizza", "A Pizza with Anchovies", "Eat", 60, 155, "items:155", false, false, 0, 11447982, false, false, true, 327));
		items.add(new ItemDef("Half Meat Pizza", "Half of this pizza has been eaten", "Eat", 25, 156, "items:156", false, false, 0, 16756316, false, false, true, 328));
		items.add(new ItemDef("Half Anchovie Pizza", "Half of this pizza has been eaten", "Eat", 30, 156, "items:156", false, false, 0, 11447982, false, false, true, 329));
		items.add(new ItemDef("Cake", "A plain sponge cake", "Eat", 50, 157, "items:157", false, false, 0, 16763289, false, false, true, 330));
		items.add(new ItemDef("Burnt Cake", "Argh what a mess!", "", 1, 157, "items:157", false, false, 0, 4210752, false, false, true, 331));
		items.add(new ItemDef("Chocolate Cake", "This looks very tasty!", "Eat", 70, 157, "items:157", false, false, 0, 16744524, false, false, true, 332));
		items.add(new ItemDef("Partial Cake", "Someone has eaten a big chunk of this cake", "Eat", 30, 158, "items:158", false, false, 0, 16763289, false, false, true, 333));
		items.add(new ItemDef("Partial Chocolate Cake", "Someone has eaten a big chunk of this cake", "Eat", 50, 158, "items:158", false, false, 0, 16744524, false, false, true, 334));
		items.add(new ItemDef("Slice of Cake", "I'd rather have a whole cake!", "Eat", 10, 159, "items:159", false, false, 0, 16763289, false, false, true, 335));
		items.add(new ItemDef("Chocolate Slice", "A slice of chocolate cake", "Eat", 30, 159, "items:159", false, false, 0, 16744524, false, false, true, 336));
		items.add(new ItemDef("Chocolate Bar", "It's a bar of chocolate", "Eat", 10, 160, "items:160", false, false, 0, 0, false, false, true, 337));
		items.add(new ItemDef("Cake Tin", "Useful for baking cakes", "", 10, 177, "items:177", false, false, 0, 0, false, false, true, 338));
		items.add(new ItemDef("Uncooked cake", "Now all I need to do is cook it", "", 20, 178, "items:178", false, false, 0, 16769248, false, false, true, 339));
		items.add(new ItemDef("Unfired bowl", "I need to put this in a pottery oven", "", 2, 161, "items:161", false, false, 0, 15632503, false, false, true, 340));
		items.add(new ItemDef("Bowl", "Useful for mixing things", "", 4, 161, "items:161", false, false, 0, 16757606, false, false, true, 341));
		items.add(new ItemDef("Bowl of water", "It's a bowl of water", "", 3, 162, "items:162", false, false, 0, 255, false, false, true, 342));
		items.add(new ItemDef("Incomplete stew", "I need to add some meat too", "", 4, 162, "items:162", false, false, 0, 10066355, false, false, true, 343));
		items.add(new ItemDef("Incomplete stew", "I need to add some potato too", "", 4, 162, "items:162", false, false, 0, 10066355, false, false, true, 344));
		items.add(new ItemDef("Uncooked stew", "I need to cook this", "", 10, 162, "items:162", false, false, 0, 13415270, false, false, true, 345));
		items.add(new ItemDef("Stew", "It's a meat and potato stew", "Eat", 20, 162, "items:162", false, false, 0, 10046464, false, false, true, 346));
		items.add(new ItemDef("Burnt Stew", "Eew it's horribly burnt", "Empty", 1, 162, "items:162", false, false, 0, 3158064, false, false, true, 347));
		items.add(new ItemDef("Potato", "Can be used to make stew", "", 1, 163, "items:163", false, false, 0, 0, false, false, true, 348));
		items.add(new ItemDef("Raw Shrimp", "I should try cooking this", "", 5, 164, "items:164", false, false, 0, 16752800, false, false, true, 349));
		items.add(new ItemDef("Shrimp", "Some nicely cooked fish", "Eat", 5, 164, "items:164", false, false, 0, 16740464, false, false, true, 350));
		items.add(new ItemDef("Raw Anchovies", "I should try cooking this", "", 15, 164, "items:164", false, false, 0, 10526975, false, false, true, 351));
		items.add(new ItemDef("Anchovies", "Some nicely cooked fish", "Eat", 15, 164, "items:164", false, false, 0, 7368959, false, false, true, 352));
		items.add(new ItemDef("Burnt fish", "Oops!", "", 1, 164, "items:164", false, false, 0, 4210752, false, false, true, 353));
		items.add(new ItemDef("Raw Sardine", "I should try cooking this", "", 10, 165, "items:165", false, false, 0, 10551200, false, false, true, 354));
		items.add(new ItemDef("Sardine", "Some nicely cooked fish", "Eat", 10, 165, "items:165", false, false, 0, 7405424, false, false, true, 355));
		items.add(new ItemDef("Raw Salmon", "I should try cooking this", "", 50, 165, "items:165", false, false, 0, 0, false, false, true, 356));
		items.add(new ItemDef("Salmon", "Some nicely cooked fish", "Eat", 50, 165, "items:165", false, false, 0, 12619920, false, false, true, 357));
		items.add(new ItemDef("Raw Trout", "I should try cooking this", "", 20, 165, "items:165", false, false, 0, 16752800, false, false, true, 358));
		items.add(new ItemDef("Trout", "Some nicely cooked fish", "Eat", 20, 165, "items:165", false, false, 0, 16740464, false, false, true, 359));
		items.add(new ItemDef("Burnt fish", "Oops!", "", 1, 165, "items:165", false, false, 0, 4210752, false, false, true, 360));
		items.add(new ItemDef("Raw Herring", "I should try cooking this", "", 15, 166, "items:166", false, false, 0, 0, false, false, true, 361));
		items.add(new ItemDef("Herring", "Some nicely cooked fish", "Eat", 15, 166, "items:166", false, false, 0, 12619920, false, false, true, 362));
		items.add(new ItemDef("Raw Pike", "I should try cooking this", "", 25, 166, "items:166", false, false, 0, 10526975, false, false, true, 363));
		items.add(new ItemDef("Pike", "Some nicely cooked fish", "Eat", 25, 166, "items:166", false, false, 0, 7368959, false, false, true, 364));
		items.add(new ItemDef("Burnt fish", "Oops!", "", 1, 166, "items:166", false, false, 0, 4210752, false, false, true, 365));
		items.add(new ItemDef("Raw Tuna", "I should try cooking this", "", 100, 167, "items:167", false, false, 0, 0, false, false, true, 366));
		items.add(new ItemDef("Tuna", "Wow this is a big fish", "Eat", 100, 167, "items:167", false, false, 0, 12619920, false, false, true, 367));
		items.add(new ItemDef("Burnt fish", "Oops!", "", 1, 167, "items:167", false, false, 0, 4210752, false, false, true, 368));
		items.add(new ItemDef("Raw Swordfish", "I should try cooking this", "", 200, 168, "items:168", false, false, 0, 16752895, false, false, true, 369));
		items.add(new ItemDef("Swordfish", "I'd better be careful eating this!", "Eat", 200, 168, "items:168", false, false, 0, 12611776, false, false, true, 370));
		items.add(new ItemDef("Burnt Swordfish", "Oops!", "", 1, 168, "items:168", false, false, 0, 4210752, false, false, true, 371));
		items.add(new ItemDef("Raw Lobster", "I should try cooking this", "", 150, 169, "items:169", false, false, 0, 16711680, false, false, true, 372));
		items.add(new ItemDef("Lobster", "This looks tricky to eat", "Eat", 150, 169, "items:169", false, false, 0, 11558912, false, false, true, 373));
		items.add(new ItemDef("Burnt Lobster", "Oops!", "", 1, 169, "items:169", false, false, 0, 4210752, false, false, true, 374));
		items.add(new ItemDef("Lobster Pot", "Useful for catching lobsters", "", 20, 170, "items:170", false, false, 0, 0, false, false, true, 375));
		items.add(new ItemDef("Net", "Useful for catching small fish", "", 5, 171, "items:171", false, false, 0, 0, false, false, true, 376));
		items.add(new ItemDef("Fishing Rod", "Useful for catching sardine or herring", "", 5, 172, "items:172", false, true, 16, 0, false, false, true, 377));
		items.add(new ItemDef("Fly Fishing Rod", "Useful for catching salmon or trout", "", 5, 173, "items:173", false, false, 0, 0, false, false, true, 378));
		items.add(new ItemDef("Harpoon", "Useful for catching really big fish", "", 5, 174, "items:174", false, false, 0, 0, false, false, true, 379));
		items.add(new ItemDef("Fishing Bait", "For use with a fishing rod", "", 3, 175, "items:175", true, false, 0, 0, false, false, false, 380));
		items.add(new ItemDef("Feather", "Used for fly-fishing", "", 2, 176, "items:176", true, false, 0, 0, false, false, false, 381));
		items.add(new ItemDef("Chest key", "A key to One eyed Hector's chest", "", 1, 25, "items:25", false, false, 0, 14540253, false, true, false, 382));
		items.add(new ItemDef("Silver", "this needs refining", "", 75, 134, "items:134", false, false, 0, 0, false, false, true, 383));
		items.add(new ItemDef("silver bar", "this looks valuable", "", 150, 79, "items:79", false, false, 0, 0, false, false, true, 384));
		items.add(new ItemDef("Holy Symbol of saradomin", "This improves my prayer", "", 300, 44, "items:44", false, true, 1024, 0, false, false, true, 385));
		items.add(new ItemDef("Holy symbol mould", "Used to make Holy Symbols", "", 5, 132, "items:132", false, false, 0, 0, false, false, true, 386));
		items.add(new ItemDef("Disk of Returning", "Used to get out of Thordur's blackhole", "spin", 12, 133, "items:133", false, false, 0, 0, false, false, true, 387));
		items.add(new ItemDef("Monks robe", "I feel closer to the God's when I am wearing this", "", 40, 87, "items:87", false, true, 64, 10510400, false, false, true, 388));
		items.add(new ItemDef("Monks robe", "Keeps a monk's legs nice and warm", "", 30, 88, "items:88", false, true, 128, 10510400, false, false, true, 389));
		items.add(new ItemDef("Red key", "A painted key", "", 1, 25, "items:25", false, false, 0, 16711680, false, true, false, 390));
		items.add(new ItemDef("Orange Key", "A painted key", "", 1, 25, "items:25", false, false, 0, 16755200, false, true, false, 391));
		items.add(new ItemDef("yellow key", "A painted key", "", 1, 25, "items:25", false, false, 0, 16777045, false, true, false, 392));
		items.add(new ItemDef("Blue key", "A painted key", "", 1, 25, "items:25", false, false, 0, 255, false, true, false, 393));
		items.add(new ItemDef("Magenta key", "A painted key", "", 1, 25, "items:25", false, false, 0, 16711935, false, true, false, 394));
		items.add(new ItemDef("black key", "A painted key", "", 1, 25, "items:25", false, false, 0, 4210752, false, true, false, 395));
		items.add(new ItemDef("rune dagger", "Short but pointy", "", 8000, 80, "items:80", false, true, 16, 65535, false, false, true, 396));
		items.add(new ItemDef("Rune short sword", "A razor sharp sword", "", 20800, 1, "items:1", false, true, 16, 65535, false, false, true, 397));
		items.add(new ItemDef("rune Scimitar", "A vicious curved sword", "", 25600, 83, "items:83", false, true, 16, 65535, false, false, true, 398));
		items.add(new ItemDef("Medium Rune Helmet", "A medium sized helmet", "", 19200, 5, "items:5", false, true, 32, 65535, false, false, true, 399));
		items.add(new ItemDef("Rune Chain Mail Body", "A series of connected metal rings", "", 50000, 7, "items:7", false, true, 64, 65535, false, false, true, 400));
		items.add(new ItemDef("Rune Plate Mail Body", "Provides excellent protection", "", 65000, 8, "items:8", false, true, 322, 65535, false, false, true, 401));
		items.add(new ItemDef("Rune Plate Mail Legs", "These look pretty heavy", "", 64000, 9, "items:9", false, true, 644, 65535, false, false, true, 402));
		items.add(new ItemDef("Rune Square Shield", "A medium metal shield", "", 38400, 3, "items:3", false, true, 8, 56797, false, false, true, 403));
		items.add(new ItemDef("Rune Kite Shield", "A large metal shield", "", 54400, 2, "items:2", false, true, 8, 56797, false, false, true, 404));
		items.add(new ItemDef("rune Axe", "A powerful axe", "", 12800, 12, "items:12", false, false, 0, 65535, false, false, true, 405));
		items.add(new ItemDef("Rune Plate Mail Legs", "These look pretty heavy", "", 64000, 9, "items:9", false, true, 640, 26214, false, false, true, 406));
		items.add(new ItemDef("Rune Plate Mail Body", "Provides excellent protection", "", 65000, 8, "items:8", false, true, 322, 65535, false, false, true, 407));
		items.add(new ItemDef("Runite bar", "it's a bar of runite", "", 5000, 79, "items:79", false, false, 0, 56797, false, false, true, 408));
		items.add(new ItemDef("runite ore", "this needs refining", "", 3200, 70, "items:70", false, false, 0, 56797, false, false, true, 409));
		items.add(new ItemDef("Plank", "This doesn't look very useful", "", 1, 135, "items:135", false, false, 0, 0, false, false, true, 410));
		items.add(new ItemDef("Tile", "This doesn't look very useful", "", 1, 136, "items:136", false, false, 0, 0, false, false, true, 411));
		items.add(new ItemDef("skull", "A spooky looking skull", "", 1, 26, "items:26", false, false, 0, 0, false, false, true, 412));
		items.add(new ItemDef("Big Bones", "Ew it's a pile of bones", "Bury", 1, 137, "items:137", false, false, 0, 0, false, false, true, 413));
		items.add(new ItemDef("Muddy key", "It looks like a key to a chest", "", 1, 25, "items:25", false, false, 0, 15636736, false, false, true, 414));
		items.add(new ItemDef("Map", "A map showing the way to the Isle of Crandor", "", 1, 138, "items:138", false, false, 0, 0, false, true, false, 415));
		items.add(new ItemDef("Map Piece", "I need some more of the map for this to be useful", "", 1, 139, "items:139", false, false, 0, 0, false, true, false, 416));
		items.add(new ItemDef("Map Piece", "I need some more of the map for this to be useful", "", 1, 140, "items:140", false, false, 0, 0, false, true, false, 417));
		items.add(new ItemDef("Map Piece", "I need some more of the map for this to be useful", "", 1, 141, "items:141", false, false, 0, 0, false, true, false, 418));
		items.add(new ItemDef("Nails", "Nails made from steel", "", 3, 142, "items:142", true, false, 0, 0, false, false, false, 419));
		items.add(new ItemDef("Anti dragon breath Shield", "Helps prevent damage from dragons", "", 20, 143, "items:143", false, true, 8, 0, false, false, true, 420));
		items.add(new ItemDef("Maze key", "The key to the entrance of Melzar's maze", "", 1, 25, "items:25", false, false, 0, 14540253, false, false, true, 421));
		items.add(new ItemDef("Pumpkin", "Happy halloween", "eat", 30, 149, "items:149", false, false, 0, 0, false, false, true, 422));
		items.add(new ItemDef("Black dagger", "Short but pointy", "", 240, 80, "items:80", false, true, 16, 3158064, false, false, true, 423));
		items.add(new ItemDef("Black Short Sword", "A razor sharp sword", "", 624, 1, "items:1", false, true, 16, 3158064, false, false, true, 424));
		items.add(new ItemDef("Black Long Sword", "A razor sharp sword", "", 960, 81, "items:81", false, true, 16, 3158064, false, false, true, 425));
		items.add(new ItemDef("Black 2-handed Sword", "A very large sword", "", 1920, 82, "items:82", false, true, 8216, 3158064, false, false, true, 426));
		items.add(new ItemDef("Black Scimitar", "A vicious curved sword", "", 768, 83, "items:83", false, true, 16, 3158064, false, false, true, 427));
		items.add(new ItemDef("Black Axe", "A sinister looking axe", "", 384, 12, "items:12", false, false, 0, 3158064, false, false, true, 428));
		items.add(new ItemDef("Black battle Axe", "A vicious looking axe", "", 1248, 84, "items:84", false, true, 16, 3158064, false, false, true, 429));
		items.add(new ItemDef("Black Mace", "A spikey mace", "", 432, 0, "items:0", false, true, 16, 3158064, false, false, true, 430));
		items.add(new ItemDef("Black Chain Mail Body", "A series of connected metal rings", "", 1440, 7, "items:7", false, true, 64, 3158064, false, false, true, 431));
		items.add(new ItemDef("Black Square Shield", "A medium metal shield", "", 1152, 3, "items:3", false, true, 8, 3158064, false, false, true, 432));
		items.add(new ItemDef("Black Kite Shield", "A large metal shield", "", 1632, 2, "items:2", false, true, 8, 3158064, false, false, true, 433));
		items.add(new ItemDef("Black Plate Mail Legs", "These look pretty heavy", "", 1920, 9, "items:9", false, true, 640, 1118481, false, false, true, 434));
		items.add(new ItemDef("Herb", "I need a closer look to identify this", "Identify", 1, 75, "items:75", false, false, 0, 0, true, false, true, 435));
		items.add(new ItemDef("Herb", "I need a closer look to identify this", "Identify", 1, 75, "items:75", false, false, 0, 0, true, false, true, 436));
		items.add(new ItemDef("Herb", "I need a closer look to identify this", "Identify", 1, 75, "items:75", false, false, 0, 0, true, false, true, 437));
		items.add(new ItemDef("Herb", "I need a closer look to identify this", "Identify", 1, 75, "items:75", false, false, 0, 0, true, false, true, 438));
		items.add(new ItemDef("Herb", "I need a closer look to identify this", "Identify", 1, 75, "items:75", false, false, 0, 0, true, false, true, 439));
		items.add(new ItemDef("Herb", "I need a closer look to identify this", "Identify", 1, 75, "items:75", false, false, 0, 0, true, false, true, 440));
		items.add(new ItemDef("Herb", "I need a closer look to identify this", "Identify", 1, 75, "items:75", false, false, 0, 0, true, false, true, 441));
		items.add(new ItemDef("Herb", "I need a closer look to identify this", "Identify", 1, 75, "items:75", false, false, 0, 0, true, false, true, 442));
		items.add(new ItemDef("Herb", "I need a closer look to identify this", "Identify", 1, 75, "items:75", false, false, 0, 0, true, false, true, 443));
		items.add(new ItemDef("Guam leaf", "A herb used in attack potion making", "", 3, 75, "items:75", false, false, 0, 0, true, false, true, 444));
		items.add(new ItemDef("Marrentill", "A herb used in poison cures", "", 5, 75, "items:75", false, false, 0, 0, true, false, true, 445));
		items.add(new ItemDef("Tarromin", "A useful herb", "", 11, 75, "items:75", false, false, 0, 0, true, false, true, 446));
		items.add(new ItemDef("Harralander", "A useful herb", "", 20, 75, "items:75", false, false, 0, 0, true, false, true, 447));
		items.add(new ItemDef("Ranarr Weed", "A useful herb", "", 25, 75, "items:75", false, false, 0, 0, true, false, true, 448));
		items.add(new ItemDef("Irit Leaf", "A useful herb", "", 40, 75, "items:75", false, false, 0, 0, true, false, true, 449));
		items.add(new ItemDef("Avantoe", "A useful herb", "", 48, 75, "items:75", false, false, 0, 0, true, false, true, 450));
		items.add(new ItemDef("Kwuarm", "A powerful herb", "", 54, 75, "items:75", false, false, 0, 0, true, false, true, 451));
		items.add(new ItemDef("Cadantine", "A powerful herb", "", 65, 75, "items:75", false, false, 0, 0, true, false, true, 452));
		items.add(new ItemDef("Dwarf Weed", "A powerful herb", "", 70, 75, "items:75", false, false, 0, 0, true, false, true, 453));
		items.add(new ItemDef("Unfinished potion", "I need another ingredient to finish this Guam potion", "", 3, 48, "items:48", false, false, 0, 10073782, true, false, true, 454));
		items.add(new ItemDef("Unfinished potion", "I need another ingredient to finish this Marrentill potion", "", 5, 48, "items:48", false, false, 0, 11966902, true, false, true, 455));
		items.add(new ItemDef("Unfinished potion", "I need another ingredient to finish this Tarromin potion", "", 11, 48, "items:48", false, false, 0, 11974297, true, false, true, 456));
		items.add(new ItemDef("Unfinished potion", "I need another ingredient to finish this Harralander potion", "", 20, 48, "items:48", false, false, 0, 11966873, true, false, true, 457));
		items.add(new ItemDef("Unfinished potion", "I need another ingredient to finish this Ranarr potion", "", 25, 48, "items:48", false, false, 0, 10073753, true, false, true, 458));
		items.add(new ItemDef("Unfinished potion", "I need another ingredient to finish this Irit potion", "", 40, 48, "items:48", false, false, 0, 10066358, true, false, true, 459));
		items.add(new ItemDef("Unfinished potion", "I need another ingredient to finish this Avantoe potion", "", 48, 48, "items:48", false, false, 0, 10066329, true, false, true, 460));
		items.add(new ItemDef("Unfinished potion", "I need another ingredient to finish this Kwuarm potion", "", 54, 48, "items:48", false, false, 0, 11974326, true, false, true, 461));
		items.add(new ItemDef("Unfinished potion", "I need another ingredient to finish this Cadantine potion", "", 65, 48, "items:48", false, false, 0, 13743769, true, false, true, 462));
		items.add(new ItemDef("Unfinished potion", "I need another ingredient to finish this Dwarfweed potion", "", 70, 48, "items:48", false, false, 0, 10073809, true, false, true, 463));
		items.add(new ItemDef(Config.S_IMPROVED_ITEM_OBJECT_NAMES ? "Vial of Water" : "Vial", "It's full of water", "", 2, 48, "items:48", false, false, 0, 11197951, false, false, true, 464));
		items.add(new ItemDef("Vial", "This vial is empty", "", 2, 144, "items:144", false, false, 0, 0, false, false, true, 465));
		items.add(new ItemDef("Unicorn horn", "Poor unicorn", "", 20, 145, "items:145", false, false, 0, 0, true, false, true, 466));
		items.add(new ItemDef("Blue dragon scale", "A large shiny scale", "", 50, 146, "items:146", false, false, 0, 0, true, false, true, 467));
		items.add(new ItemDef("Pestle and mortar", "I can grind things for potions in this", "", 4, 147, "items:147", false, false, 0, 0, true, false, true, 468));
		items.add(new ItemDef("Snape grass", "Strange spikey grass", "", 10, 148, "items:148", false, false, 0, 0, true, false, true, 469));
		items.add(new ItemDef("Medium black Helmet", "A medium sized helmet", "", 576, 5, "items:5", false, true, 32, 3158064, false, false, true, 470));
		items.add(new ItemDef("White berries", "Poisonous berries", "", 10, 21, "items:21", false, false, 0, 0, true, false, true, 471));
		items.add(new ItemDef("Ground blue dragon scale", "This stuff isn't good for you", "", 40, 23, "items:23", false, false, 0, 35071, true, false, true, 472));
		items.add(new ItemDef("Ground unicorn horn", "A useful potion ingredient", "", 20, 23, "items:23", false, false, 0, 15645520, true, false, true, 473));
		items.add(new ItemDef("attack Potion", "3 doses of attack potion", "Drink", 12, 48, "items:48", false, false, 0, 3206894, true, false, true, 474));
		items.add(new ItemDef("attack Potion", "2 doses of attack potion", "Drink", 9, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 3206894, true, false, true, 475));
		items.add(new ItemDef("attack Potion", "1 dose of attack potion", "Drink", 6, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 3206894, true, false, true, 476));
		items.add(new ItemDef("stat restoration Potion", "3 doses of stat restoration potion", "Drink", 88, 48, "items:48", false, false, 0, 15609904, true, false, true, 477));
		items.add(new ItemDef("stat restoration Potion", "2 doses of stat restoration potion", "Drink", 66, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 15609904, true, false, true, 478));
		items.add(new ItemDef("stat restoration Potion", "1 dose of stat restoration potion", "Drink", 44, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 15609904, true, false, true, 479));
		items.add(new ItemDef("defense Potion", "3 doses of defense potion", "Drink", 120, 48, "items:48", false, false, 0, 3206704, true, false, true, 480));
		items.add(new ItemDef("defense Potion", "2 doses of defense potion", "Drink", 90, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 3206704, true, false, true, 481));
		items.add(new ItemDef("defense Potion", "1 dose of defense potion", "Drink", 60, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 3206704, true, false, true, 482));
		items.add(new ItemDef("restore prayer Potion", "3 doses of restore prayer potion", "Drink", 152, 48, "items:48", false, false, 0, 3206809, true, false, true, 483));
		items.add(new ItemDef("restore prayer Potion", "2 doses of restore prayer potion", "Drink", 114, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 3206809, true, false, true, 484));
		items.add(new ItemDef("restore prayer Potion", "1 dose of restore prayer potion", "Drink", 76, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 3206809, true, false, true, 485));
		items.add(new ItemDef("Super attack Potion", "3 doses of attack potion", "Drink", 180, 48, "items:48", false, false, 0, 3158254, true, false, true, 486));
		items.add(new ItemDef("Super attack Potion", "2 doses of attack potion", "Drink", 135, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 3158254, true, false, true, 487));
		items.add(new ItemDef("Super attack Potion", "1 dose of attack potion", "Drink", 90, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 3158254, true, false, true, 488));
		items.add(new ItemDef("fishing Potion", "3 doses of fishing potion", "Drink", 200, 48, "items:48", false, false, 0, 3158064, true, false, true, 489));
		items.add(new ItemDef("fishing Potion", "2 doses of fishing potion", "Drink", 150, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 3158064, true, false, true, 490));
		items.add(new ItemDef("fishing Potion", "1 dose of fishing potion", "Drink", 100, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 3158064, true, false, true, 491));
		items.add(new ItemDef("Super strength Potion", "3 doses of strength potion", "Drink", 220, 48, "items:48", false, false, 0, 15658734, true, false, true, 492));
		items.add(new ItemDef("Super strength Potion", "2 doses of strength potion", "Drink", 165, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 15658734, true, false, true, 493));
		items.add(new ItemDef("Super strength Potion", "1 dose of strength potion", "Drink", 110, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 15658734, true, false, true, 494));
		items.add(new ItemDef("Super defense Potion", "3 doses of defense potion", "Drink", 264, 48, "items:48", false, false, 0, 15644208, true, false, true, 495));
		items.add(new ItemDef("Super defense Potion", "2 doses of defense potion", "Drink", 198, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 15644208, true, false, true, 496));
		items.add(new ItemDef("Super defense Potion", "1 dose of defense potion", "Drink", 132, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 15644208, true, false, true, 497));
		items.add(new ItemDef("ranging Potion", "3 doses of ranging potion", "Drink", 288, 48, "items:48", false, false, 0, 3192558, true, false, true, 498));
		items.add(new ItemDef("ranging Potion", "2 doses of ranging potion", "Drink", 216, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 3192558, true, false, true, 499));
		items.add(new ItemDef("ranging Potion", "1 dose of ranging potion", "Drink", 144, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 3192558, true, false, true, 500));
		items.add(new ItemDef("wine of Zamorak", "It's full of wine", "Drink", 1, 65, "items:65", false, false, 0, 12851224, false, false, true, 501));
		items.add(new ItemDef("raw bear meat", "I need to cook this first", "", 1, 60, "items:60", false, false, 0, 16747571, false, false, true, 502));
		items.add(new ItemDef("raw rat meat", "I need to cook this first", "", 1, 60, "items:60", false, false, 0, 16747571, false, false, true, 503));
		items.add(new ItemDef("raw beef", "I need to cook this first", "", 1, 60, "items:60", false, false, 0, 16747571, false, false, true, 504));
		items.add(new ItemDef("enchanted bear meat", "I don't fancy eating this now", "", 1, 60, "items:60", false, false, 0, 13495347, true, true, false, 505));
		items.add(new ItemDef("enchanted rat meat", "I don't fancy eating this now", "", 1, 60, "items:60", false, false, 0, 13495347, true, true, false, 506));
		items.add(new ItemDef("enchanted beef", "I don't fancy eating this now", "", 1, 60, "items:60", false, false, 0, 13495347, true, true, false, 507));
		items.add(new ItemDef("enchanted chicken meat", "I don't fancy eating this now", "", 1, 60, "items:60", false, false, 0, 13495347, true, true, false, 508));
		items.add(new ItemDef("Dramen Staff", "A magical staff cut from the dramen tree", "", 15, 85, "items:85", false, true, 16, 10072780, true, true, true, 509));
		items.add(new ItemDef("Dramen Branch", "I need to make this into a staff", "", 15, 179, "items:179", false, false, 0, 10072780, true, true, true, 510));
		items.add(new ItemDef("Cape", "A thick Green cape", "", 32, 59, "items:59", false, true, 2048, 4246592, false, false, true, 511));
		items.add(new ItemDef("Cape", "A thick yellow cape", "", 32, 59, "items:59", false, true, 2048, 15658560, false, false, true, 512));
		items.add(new ItemDef("Cape", "A thick Orange cape", "", 32, 59, "items:59", false, true, 2048, 15636736, false, false, true, 513));
		items.add(new ItemDef("Cape", "A thick purple cape", "", 32, 59, "items:59", false, true, 2048, 11141341, false, false, true, 514));
		items.add(new ItemDef("Greendye", "A little bottle of dye", "", 5, 104, "items:104", false, false, 0, 65280, false, false, true, 515));
		items.add(new ItemDef("Purpledye", "A little bottle of dye", "", 5, 104, "items:104", false, false, 0, 11141375, false, false, true, 516));
		items.add(new ItemDef("Iron ore certificate", "Each certificate exchangable at draynor market for 5 iron ore", "", 10, 180, "items:180", true, false, 0, 0, false, false, false, 517));
		items.add(new ItemDef("Coal certificate", "Each certificate exchangable at draynor market for 5 coal", "", 20, 180, "items:180", true, false, 0, 0, false, false, false, 518));
		items.add(new ItemDef("Mithril ore certificate", "Each certificate exchangable at draynor market for 5 mithril ore", "", 30, 180, "items:180", true, false, 0, 0, false, false, false, 519));
		items.add(new ItemDef("silver certificate", "Each certificate exchangable at draynor market for 5 silver nuggets", "", 15, 180, "items:180", true, false, 0, 0, false, false, false, 520));
		items.add(new ItemDef("Gold certificate", "Each certificate exchangable at draynor market for 5 gold nuggets", "", 25, 180, "items:180", true, false, 0, 0, false, false, false, 521));
		items.add(new ItemDef("Dragonstone Amulet", "A very powerful amulet", "", 17625, 125, "items:125", false, true, 1024, 12255487, true, false, true, 522));
		items.add(new ItemDef("Dragonstone", "This looks very valuable", "", 10000, 74, "items:74", false, false, 0, 12255487, true, false, true, 523));
		items.add(new ItemDef("Dragonstone Amulet", "It needs a string so I can wear it", "", 17625, 126, "items:126", false, false, 0, 12255487, true, false, true, 524));
		items.add(new ItemDef("Crystal key", "A very shiny key", "", 1, 25, "items:25", false, false, 0, 15663103, true, false, true, 525));
		items.add(new ItemDef("Half of a key", "A very shiny key", "", 1, 181, "items:181", false, false, 0, 15663103, true, false, true, 526));
		items.add(new ItemDef("Half of a key", "A very shiny key", "", 1, 182, "items:182", false, false, 0, 15663103, true, false, true, 527));
		items.add(new ItemDef("Iron bar certificate", "Each certificate exchangable at draynor market for 5 iron bars", "", 10, 180, "items:180", true, false, 0, 0, false, false, false, 528));
		items.add(new ItemDef("steel bar certificate", "Each certificate exchangable at draynor market for 5 steel bars", "", 20, 180, "items:180", true, false, 0, 0, false, false, false, 529));
		items.add(new ItemDef("Mithril bar certificate", "Each certificate exchangable at draynor market for 5 mithril bars", "", 30, 180, "items:180", true, false, 0, 0, false, false, false, 530));
		items.add(new ItemDef("silver bar certificate", "Each certificate exchangable at draynor market for 5 silver bars", "", 15, 180, "items:180", true, false, 0, 0, false, false, false, 531));
		items.add(new ItemDef("Gold bar certificate", "Each certificate exchangable at draynor market for 5 gold bars", "", 25, 180, "items:180", true, false, 0, 0, false, false, false, 532));
		items.add(new ItemDef("Lobster certificate", "Each certificate exchangable at draynor market for 5 lobsters", "", 10, 180, "items:180", true, false, 0, 0, false, false, false, 533));
		items.add(new ItemDef("Raw lobster certificate", "Each certificate exchangable at draynor market for 5 raw lobsters", "", 10, 180, "items:180", true, false, 0, 0, false, false, false, 534));
		items.add(new ItemDef("Swordfish certificate", "Each certificate exchangable at draynor market for 5 swordfish", "", 10, 180, "items:180", true, false, 0, 0, false, false, false, 535));
		items.add(new ItemDef("Raw swordfish certificate", "Each certificate exchangable at draynor market for 5 raw swordfish", "", 10, 180, "items:180", true, false, 0, 0, false, false, false, 536));
		items.add(new ItemDef("Diary", "Property of Nora.T.Hag", "read", 1, 28, "items:28", false, false, 0, 11206570, true, false, true, 537));
		items.add(new ItemDef("Front door key", "A house key", "", 1, 25, "items:25", false, false, 0, 15636736, true, true, false, 538));
		items.add(new ItemDef("Ball", "A child's ball", "", 1, 183, "items:183", false, false, 0, 0, true, true, false, 539));
		items.add(new ItemDef("magnet", "A very attractive magnet", "", 3, 184, "items:184", false, false, 0, 0, true, true, false, 540));
		items.add(new ItemDef("Grey wolf fur", "This would make warm clothing", "", 50, 68, "items:68", false, false, 0, 15658734, true, false, true, 541));
		items.add(new ItemDef("uncut dragonstone", "this would be worth more cut", "", 1000, 73, "items:73", false, false, 0, 12255487, true, false, true, 542));
		items.add(new ItemDef("Dragonstone ring", "A valuable ring", "", 17625, 123, "items:123", false, Config.S_WANT_CUSTOM_SPRITES, Config.S_WANT_CUSTOM_SPRITES ? 1200 : 0, 12255487, true, false, true, 543));
		items.add(new ItemDef("Dragonstone necklace", "I wonder if this is valuable", "", 18375, 57, "items:57", false, true, 1024, 12255487, true, false, true, 544));
		items.add(new ItemDef("Raw Shark", "I should try cooking this", "", 300, 185, "items:185", false, false, 0, 5263488, true, false, true, 545));
		items.add(new ItemDef("Shark", "I'd better be careful eating this!", "Eat", 300, 185, "items:185", false, false, 0, 11558912, true, false, true, 546));
		items.add(new ItemDef("Burnt Shark", "Oops!", "", 1, 185, "items:185", false, false, 0, 4210752, true, false, true, 547));
		items.add(new ItemDef("Big Net", "Useful for catching lots of fish", "", 20, 186, "items:186", false, false, 0, 0, true, false, true, 548));
		items.add(new ItemDef("Casket", "I hope there is treasure in it", "open", 50, 187, "items:187", false, false, 0, 0, true, false, true, 549));
		items.add(new ItemDef("Raw cod", "I should try cooking this", "", 25, 165, "items:165", false, false, 0, 10526924, true, false, true, 550));
		items.add(new ItemDef("Cod", "Some nicely cooked fish", "Eat", 25, 165, "items:165", false, false, 0, 7368908, true, false, true, 551));
		items.add(new ItemDef("Raw Mackerel", "I should try cooking this", "", 17, 166, "items:166", false, false, 0, 13421728, true, false, true, 552));
		items.add(new ItemDef("Mackerel", "Some nicely cooked fish", "Eat", 17, 166, "items:166", false, false, 0, 13421680, true, false, true, 553));
		items.add(new ItemDef("Raw Bass", "I should try cooking this", "", 120, 167, "items:167", false, false, 0, 16752800, true, false, true, 554));
		items.add(new ItemDef("Bass", "Wow this is a big fish", "Eat", 120, 167, "items:167", false, false, 0, 16740464, true, false, true, 555));
		items.add(new ItemDef("Ice Gloves", "These will keep my hands cold!", "", 6, 17, "items:17", false, true, 256, 11202303, true, true, false, 556));
		items.add(new ItemDef("Firebird Feather", "A red hot feather", "", 2, 176, "items:176", false, false, 0, 16711680, true, true, false, 557));
		items.add(new ItemDef("Firebird Feather", "This is cool enough to hold now", "", 2, 176, "items:176", false, false, 0, 16768256, true, true, false, 558));
		items.add(new ItemDef("Poisoned Iron dagger", "Short but pointy", "", 35, 80, "items:514", false, true, 16, 15654365, true, false, true, 559));
		items.add(new ItemDef("Poisoned bronze dagger", "Short but pointy", "", 10, 80, "items:514", false, true, 16, 16737817, true, false, true, 560));
		items.add(new ItemDef("Poisoned Steel dagger", "Short but pointy", "", 125, 80, "items:514", false, true, 16, 15658734, true, false, true, 561));
		items.add(new ItemDef("Poisoned Mithril dagger", "Short but pointy", "", 325, 80, "items:514", false, true, 16, 10072780, true, false, true, 562));
		items.add(new ItemDef("Poisoned Rune dagger", "Short but pointy", "", 8000, 80, "items:514", false, true, 16, 65535, true, false, true, 563));
		items.add(new ItemDef("Poisoned Adamantite dagger", "Short but pointy", "", 800, 80, "items:514", false, true, 16, 11717785, true, false, true, 564));
		items.add(new ItemDef("Poisoned Black dagger", "Short but pointy", "", 240, 80, "items:514", false, true, 16, 3158064, true, false, true, 565));
		items.add(new ItemDef("Cure poison Potion", "3 doses of cure poison potion", "Drink", 288, 48, "items:48", false, false, 0, 6749969, true, false, true, 566));
		items.add(new ItemDef("Cure poison Potion", "2 doses of cure poison potion", "Drink", 216, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 6749969, true, false, true, 567));
		items.add(new ItemDef("Cure poison Potion", "1 dose of cure poison potion", "Drink", 144, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 6749969, true, false, true, 568));
		items.add(new ItemDef("Poison antidote", "3 doses of anti poison potion", "Drink", 288, 48, "items:48", false, false, 0, 16716134, true, false, true, 569));
		items.add(new ItemDef("Poison antidote", "2 doses of anti poison potion", "Drink", 216, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 16716134, true, false, true, 570));
		items.add(new ItemDef("Poison antidote", "1 dose of anti poison potion", "Drink", 144, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 16716134, true, false, true, 571));
		items.add(new ItemDef("weapon poison", "For use on daggers and arrows", "", 144, 48, "items:48", false, false, 0, 1140479, true, false, true, 572));
		items.add(new ItemDef("ID Paper", "ID of Hartigen the black knight", "", 1, 29, "items:29", false, false, 0, 0, true, false, true, 573));
		items.add(new ItemDef("Poison Bronze Arrows", "Venomous looking arrows", "", 2, 206, "items:206", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 16737817, true, false, false, 574));
		items.add(new ItemDef("Christmas cracker", "Use on another player to pull it", "", 1, 188, "items:188", false, false, 0, 16711680, false, false, true, 575));
		items.add(new ItemDef("Party Hat", "Party!!!", "", 2, 189, "items:189", false, true, 32, 16711680, false, false, true, 576));
		items.add(new ItemDef("Party Hat", "Party!!!", "", 2, 189, "items:189", false, true, 32, 16776960, false, false, true, 577));
		items.add(new ItemDef("Party Hat", "Party!!!", "", 2, 189, "items:189", false, true, 32, 255, false, false, true, 578));
		items.add(new ItemDef("Party Hat", "Party!!!", "", 2, 189, "items:189", false, true, 32, 65280, false, false, true, 579));
		items.add(new ItemDef("Party Hat", "Party!!!", "", 2, 189, "items:189", false, true, 32, 16711935, false, false, true, 580));
		items.add(new ItemDef("Party Hat", "Party!!!", "", 2, 189, "items:189", false, true, 32, 0, false, false, true, 581));
		items.add(new ItemDef("Miscellaneous key", "I wonder what this unlocks", "", 1, 25, "items:25", false, false, 0, 14509670, true, false, true, 582));
		items.add(new ItemDef("Bunch of keys", "Some keys on a keyring", "", 2, 190, "items:190", false, false, 0, 0, true, false, true, 583));
		items.add(new ItemDef("Whisky", "A bottle of Draynor Malt", "drink", 5, 191, "items:191", false, false, 0, 16755200, true, false, true, 584));
		items.add(new ItemDef("Candlestick", "A valuable candlestick", "", 5, 192, "items:192", false, false, 0, 0, true, false, true, 585));
		items.add(new ItemDef("Master thief armband", "This denotes a great act of thievery", "", 2, 193, "items:193", false, false, 0, 0, true, true, false, 586));
		items.add(new ItemDef("Blamish snail slime", "Yuck", "", 5, 104, "items:104", false, false, 0, 15663086, true, true, true, 587));
		items.add(new ItemDef("Blamish oil", "made from the finest snail slime", "", 10, 48, "items:48", false, false, 0, 15663086, true, true, true, 588));
		items.add(new ItemDef("Oily Fishing Rod", "A rod covered in Blamish oil", "", 15, 172, "items:172", false, false, 0, 0, true, true, true, 589));
		items.add(new ItemDef("lava eel", "Strange it looks cooler now it's been cooked", "eat", 150, 194, "items:194", false, false, 0, 11558912, true, true, true, 590));
		items.add(new ItemDef("Raw lava eel", "A very strange eel", "", 150, 194, "items:194", false, false, 0, 16711680, true, true, true, 591));
		items.add(new ItemDef("Poison Crossbow bolts", "Good if you have a crossbow!", "", 3, 56, "items:56", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1001 : 0, 0, true, false, false, 592));
		items.add(new ItemDef("Dragon sword", "A Razor sharp sword", "", 100000, 273, "items:273", false, true, 16, 16711748, true, false, true, 593));
		items.add(new ItemDef("Dragon axe", "A vicious looking axe", "", 200000, 272, "items:272", false, false, 0, 16711748, true, false, true, 594));
		items.add(new ItemDef("Jail keys", "Keys to the black knight jail", "", 2, 190, "items:190", false, false, 0, 0, true, true, false, 595));
		items.add(new ItemDef("Dusty Key", "A key given to me by Velrak", "", 1, 25, "items:25", false, false, 0, 12303291, true, true, false, 596));
		items.add(new ItemDef("Charged Dragonstone Amulet", "A very powerful amulet", "rub", 17625, 125, "items:125", false, true, 1024, 12255487, true, false, true, 597));
		items.add(new ItemDef("Grog", "A murky glass of some sort of drink", "drink", 3, 90, "items:90", false, false, 0, 0, true, false, true, 598));
		items.add(new ItemDef("Candle", "An unlit candle", "", 3, 195, "items:195", false, false, 0, 0, true, true, true, 599));
		items.add(new ItemDef("black Candle", "A spooky but unlit candle", "", 3, 195, "items:195", false, false, 0, 2105376, true, true, true, 600));
		items.add(new ItemDef("Candle", "A small slowly burning candle", "", 3, 196, "items:196", false, false, 0, 0, true, true, true, 601));
		items.add(new ItemDef("black Candle", "A spooky candle", "", 3, 196, "items:196", false, false, 0, 2105376, true, true, true, 602));
		items.add(new ItemDef("insect repellant", "Drives away all known 6 legged creatures", "", 3, 197, "items:197", false, false, 0, 0, true, true, true, 603));
		items.add(new ItemDef("Bat bones", "Ew it's a pile of bones", "Bury", 1, 20, Config.S_WANT_CUSTOM_SPRITES ? "items:bat_bones" : "items:20", false, false, 0, 0, true, false, true, 604));
		items.add(new ItemDef("wax Bucket", "It's a wooden bucket", "", 2, 22, "items:22", false, false, 0, 16777181, true, true, true, 605));
		items.add(new ItemDef("Excalibur", "This used to belong to king Arthur", "", 200, 115, "items:115", false, true, 16, 10072780, true, true, false, 606));
		items.add(new ItemDef("Druids robe", "I feel closer to the Gods when I am wearing this", "", 40, 87, "items:87", false, true, 64, 16777215, true, false, true, 607));
		items.add(new ItemDef("Druids robe", "Keeps a druids's knees nice and warm", "", 30, 88, "items:88", false, true, 128, 16777215, true, false, true, 608));
		items.add(new ItemDef("Eye patch", "It makes me look very piratical", "", 2, 198, "items:198", false, true, 32, 0, true, false, true, 609));
		items.add(new ItemDef("Unenchanted Dragonstone Amulet", "I wonder if I can get this enchanted", "", 17625, 125, "items:125", false, true, 1024, 12255487, true, false, true, 610));
		items.add(new ItemDef("Retired item", "This item has been retired.", "", 0, 0, "items:0", false, false, 0, 0, true, false, true, 611));
		items.add(new ItemDef("Retired item", "This item has been retired.", "", 0, 0, "items:0", false, false, 0, 0, true, false, true, 612));
		items.add(new ItemDef("Retired item", "This item has been retired.", "", 0, 0, "items:0", false, false, 0, 0, true, false, true, 613));
		items.add(new ItemDef("Retired item", "This item has been retired.", "", 0, 0, "items:0", false, false, 0, 0, true, false, true, 614));
		items.add(new ItemDef("Retired item", "This item has been retired.", "", 0, 0, "items:0", false, false, 0, 0, true, false, true, 615));
		items.add(new ItemDef("Retired item", "This item has been retired.", "", 0, 0, "items:0", false, false, 0, 0, true, false, true, 616));
		items.add(new ItemDef("Retired item", "This item has been retired.", "", 0, 0, "items:0", false, false, 0, 0, true, false, true, 617));
		items.add(new ItemDef("Retired item", "This item has been retired.", "", 0, 0, "items:0", false, false, 0, 0, true, false, true, 618));
		items.add(new ItemDef("Blood-Rune", "Used for high level missile spells", "", 25, 200, "items:200", true, false, 0, 0, true, false, false, 619));
		items.add(new ItemDef("Beer glass", "I need to fill this with beer", "", 2, 201, "items:201", false, false, 0, 0, false, false, true, 620));
		items.add(new ItemDef("glassblowing pipe", "Use on molten glass to make things", "", 2, 202, "items:202", false, false, 0, 0, true, false, true, 621));
		items.add(new ItemDef("seaweed", "slightly damp seaweed", "", 2, 203, "items:203", false, false, 0, 0, true, false, true, 622));
		items.add(new ItemDef("molten glass", "hot glass ready to be blown", "", 2, 204, "items:204", false, false, 0, 0, true, false, true, 623));
		items.add(new ItemDef("soda ash", "one of the ingredients for making glass", "", 2, 23, "items:23", false, false, 0, 0, true, false, true, 624));
		items.add(new ItemDef("sand", "one of the ingredients for making glass", "", 2, 22, "items:22", false, false, 0, 16763904, true, false, true, 625));
		items.add(new ItemDef("Retired item", "This item has been retired.", "", 0, 0, "items:0", false, false, 0, 0, true, false, true, 626));
		items.add(new ItemDef("Retired item", "This item has been retired.", "", 0, 0, "items:0", false, false, 0, 0, true, false, true, 627));
		items.add(new ItemDef("bass certificate", "Each certificate exchangable at Catherby for 5 bass", "", 10, 180, "items:180", true, false, 0, 0, true, false, false, 628));
		items.add(new ItemDef("Raw bass certificate", "Each certificate exchangable at Catherby for 5 raw bass", "", 10, 180, "items:180", true, false, 0, 0, true, false, false, 629));
		items.add(new ItemDef("shark certificate", "Each certificate exchangable at Catherby for 5 shark", "", 10, 180, "items:180", true, false, 0, 0, true, false, false, 630));
		items.add(new ItemDef("Raw shark certificate", "Each certificate exchangable at Catherby for 5 raw shark", "", 10, 180, "items:180", true, false, 0, 0, true, false, false, 631));
		items.add(new ItemDef("Oak Logs", "Logs cut from an oak tree", "", 20, 14, "items:14", false, false, 0, 0, true, false, true, 632));
		items.add(new ItemDef("Willow Logs", "Logs cut from a willow tree", "", 40, 14, "items:14", false, false, 0, 0, true, false, true, 633));
		items.add(new ItemDef("Maple Logs", "Logs cut from a maple tree", "", 80, 14, "items:14", false, false, 0, 0, true, false, true, 634));
		items.add(new ItemDef("Yew Logs", "Logs cut from a yew tree", "", 160, 14, "items:14", false, false, 0, 0, true, false, true, 635));
		items.add(new ItemDef("Magic Logs", "Logs made from magical wood", "", 320, 14, "items:14", false, false, 0, 0, true, false, true, 636));
		items.add(new ItemDef("Pine Logs", "Logs cut from a pine tree", "", 10, 14, "items:14", false, false, 0, 0xCCAA33, true, false, true, 2111));
		items.add(new ItemDef("Palm Logs", "Logs cut from a dense palm tree", "", 60, 14, "items:14", false, false, 0, 0xC78C4A, true, false, true, 2112));
		items.add(new ItemDef("Ebony Logs", "Logs cut from a dead tree", "", 400, 14, "items:14", false, false, 0, 0x333333, true, false, true, 2113));
		items.add(new ItemDef("Blood Logs", "Logs cut from a blood tree", "", 800, 14, "items:14", false, false, 0, 0xCC2222, true, false, true, 2114));
		items.add(new ItemDef("Headless Arrows", "I need to attach arrow heads to these", "", 1, 205, "items:205", true, false, 0, 0, true, false, false, 637));
		items.add(new ItemDef("Iron Arrows", "Arrows with iron heads", "", 6, 11, "items:11", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 15654365, true, false, false, 638));
		items.add(new ItemDef("Poison Iron Arrows", "Venomous looking arrows", "", 6, 206, "items:206", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 15654365, true, false, false, 639));
		items.add(new ItemDef("Steel Arrows", "Arrows with steel heads", "", 24, 11, "items:11", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 15658734, true, false, false, 640));
		items.add(new ItemDef("Poison Steel Arrows", "Venomous looking arrows", "", 24, 206, "items:206", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 15658734, true, false, false, 641));
		items.add(new ItemDef("Mithril Arrows", "Arrows with mithril heads", "", 64, 11, "items:11", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 9614028, true, false, false, 642));
		items.add(new ItemDef("Poison Mithril Arrows", "Venomous looking arrows", "", 64, 206, "items:206", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 9614028, true, false, false, 643));
		items.add(new ItemDef("Adamantite Arrows", "Arrows with adamantite heads", "", 160, 11, "items:11", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 11717785, true, false, false, 644));
		items.add(new ItemDef("Poison Adamantite Arrows", "Venomous looking arrows", "", 160, 206, "items:206", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 11717785, true, false, false, 645));
		items.add(new ItemDef("Rune Arrows", "Arrows with rune heads", "", 800, 11, "items:11", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 65535, true, false, false, 646));
		items.add(new ItemDef("Poison Rune Arrows", "Venomous looking arrows", "", 800, 206, "items:206", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 65535, true, false, false, 647));
		items.add(new ItemDef("Oak Longbow", "A Nice sturdy bow", "", 160, 54, "items:54", false, true, 24, 255, 11300689, true, false, true, 648));
		items.add(new ItemDef("Oak Shortbow", "Short but effective", "", 100, 55, "items:55", false, true, 24, 255, 11300689, true, false, true, 649));
		items.add(new ItemDef("Willow Longbow", "A Nice sturdy bow", "", 320, 54, "items:54", false, true, 24, 16776960, 8941897, true, false, true, 650));
		items.add(new ItemDef("Willow Shortbow", "Short but effective", "", 200, 55, "items:55", false, true, 24, 16776960, 8941897, true, false, true, 651));
		items.add(new ItemDef("Maple Longbow", "A Nice sturdy bow", "", 640, 54, "items:54", false, true, 24, 16746496, 9132849, true, false, true, 652));
		items.add(new ItemDef("Maple Shortbow", "Short but effective", "", 400, 55, "items:55", false, true, 24, 16746496, 9132849, true, false, true, 653));
		items.add(new ItemDef("Yew Longbow", "A Nice sturdy bow", "", 1280, 54, "items:54", false, true, 24, 16711680, 10310656, true, false, true, 654));
		items.add(new ItemDef("Yew Shortbow", "Short but effective", "", 800, 55, "items:55", false, true, 24, 16711680, 10310656, true, false, true, 655));
		items.add(new ItemDef("Magic Longbow", "A Nice sturdy bow", "", 2560, 54, "items:54", false, true, 24, 4210752, 44737, true, false, true, 656));
		items.add(new ItemDef("Magic Shortbow", "Short but effective", "", 1600, 55, "items:55", false, true, 24, 4210752, 37281, true, false, true, 657));
		items.add(new ItemDef("unstrung Oak Longbow", "I need to find a string for this", "", 80, 119, "items:119", false, false, 0, 255, 11300689, true, false, true, 658));
		items.add(new ItemDef("unstrung Oak Shortbow", "I need to find a string for this", "", 50, 120, "items:120", false, false, 0, 255, 11300689, true, false, true, 659));
		items.add(new ItemDef("unstrung Willow Longbow", "I need to find a string for this", "", 160, 119, "items:119", false, false, 0, 16776960, 8941897, true, false, true, 660));
		items.add(new ItemDef("unstrung Willow Shortbow", "I need to find a string for this", "", 100, 120, "items:120", false, false, 0, 16776960, 8941897, true, false, true, 661));
		items.add(new ItemDef("unstrung Maple Longbow", "I need to find a string for this", "", 320, 119, "items:119", false, false, 0, 16744448, 9132849, true, false, true, 662));
		items.add(new ItemDef("unstrung Maple Shortbow", "I need to find a string for this", "", 200, 120, "items:120", false, false, 0, 16744448, 9132849, true, false, true, 663));
		items.add(new ItemDef("unstrung Yew Longbow", "I need to find a string for this", "", 640, 119, "items:119", false, false, 0, 16711680, 10310656, true, false, true, 664));
		items.add(new ItemDef("unstrung Yew Shortbow", "I need to find a string for this", "", 400, 120, "items:120", false, false, 0, 16711680, 10310656, true, false, true, 665));
		items.add(new ItemDef("unstrung Magic Longbow", "I need to find a string for this", "", 1280, 119, "items:119", false, false, 0, 4210752, 37281, true, false, true, 666));
		items.add(new ItemDef("unstrung Magic Shortbow", "I need to find a string for this", "", 800, 120, "items:120", false, false, 0, 4210752, 37281, true, false, true, 667));
		items.add(new ItemDef("barcrawl card", "The official Alfred Grimhand barcrawl", "read", 10, 180, "items:180", false, false, 0, 0, true, true, false, 668));
		items.add(new ItemDef("bronze arrow heads", "Not much use without the rest of the arrow!", "", 1, 207, "items:207", true, false, 0, 16737817, true, false, false, 669));
		items.add(new ItemDef("iron arrow heads", "Not much use without the rest of the arrow!", "", 3, 207, "items:207", true, false, 0, 15658717, true, false, false, 670));
		items.add(new ItemDef("steel arrow heads", "Not much use without the rest of the arrow!", "", 12, 207, "items:207", true, false, 0, 15658734, true, false, false, 671));
		items.add(new ItemDef("mithril arrow heads", "Not much use without the rest of the arrow!", "", 32, 207, "items:207", true, false, 0, 10072780, true, false, false, 672));
		items.add(new ItemDef("adamantite arrow heads", "Not much use without the rest of the arrow!", "", 80, 207, "items:207", true, false, 0, 11717785, true, false, false, 673));
		items.add(new ItemDef("rune arrow heads", "Not much use without the rest of the arrow!", "", 400, 207, "items:207", true, false, 0, 65535, true, false, false, 674));
		items.add(new ItemDef("flax", "I should use this with a spinning wheel", "", 5, 209, "items:209", false, false, 0, 0, true, false, true, 675));
		items.add(new ItemDef("bow string", "I need a bow handle to attach this too", "", 10, 208, "items:208", false, false, 0, 0, true, false, true, 676));
		items.add(new ItemDef("Easter egg", "Happy Easter", "eat", 10, 210, "items:210", false, false, 0, 0, false, false, true, 677));
		items.add(new ItemDef("scorpion cage", "I need to catch some scorpions in this", "", 10, 211, "items:211", false, false, 0, 0, true, true, false, 678));
		items.add(new ItemDef("scorpion cage", "It has 1 scorpion in it", "", 10, 212, "items:212", false, false, 0, 0, true, true, false, 679));
		items.add(new ItemDef("scorpion cage", "It has 2 scorpions in it", "", 10, 212, "items:212", false, false, 0, 0, true, true, false, 680));
		items.add(new ItemDef("scorpion cage", "It has 3 scorpions in it", "", 10, 212, "items:212", false, false, 0, 0, true, true, false, 681));
		items.add(new ItemDef("Retired item", "This item has been retired.", "", 0, 0, "items:0", false, false, 0, 0, true, false, true, 682));
		items.add(new ItemDef("Retired item", "This item has been retired.", "", 0, 0, "items:0", false, false, 0, 0, true, false, true, 683));
		items.add(new ItemDef("Retired item", "This item has been retired.", "", 0, 0, "items:0", false, false, 0, 0, true, false, true, 684));
		items.add(new ItemDef("Retired item", "This item has been retired.", "", 0, 0, "items:0", false, false, 0, 0, true, false, true, 685));
		items.add(new ItemDef("scorpion cage", "It has 1 scorpion in it", "", 10, 212, "items:212", false, false, 0, 0, true, true, false, 686));
		items.add(new ItemDef("scorpion cage", "It has 1 scorpion in it", "", 10, 212, "items:212", false, false, 0, 0, true, true, false, 687));
		items.add(new ItemDef("scorpion cage", "It has 2 scorpions in it", "", 10, 212, "items:212", false, false, 0, 0, true, true, false, 688));
		items.add(new ItemDef("scorpion cage", "It has 2 scorpions in it", "", 10, 212, "items:212", false, false, 0, 0, true, true, false, 689));
		items.add(new ItemDef((Config.S_IMPROVED_ITEM_OBJECT_NAMES ? "Perfect " : "") + "gold", "this needs refining", "", 150, 73, "items:73", false, false, 0, 16763980, true, true, true, 690));
		items.add(new ItemDef((Config.S_IMPROVED_ITEM_OBJECT_NAMES ? "Perfect " : "") + "gold bar", "this looks valuable", "", 300, 79, "items:79", false, false, 0, 16763980, true, true, true, 691));
		items.add(new ItemDef((Config.S_IMPROVED_ITEM_OBJECT_NAMES ? "Perfect " : "") + "Ruby ring", "A valuable ring", "", 2025, 123, "items:123", false, false, 0, 16724736, true, true, true, 692));
		items.add(new ItemDef((Config.S_IMPROVED_ITEM_OBJECT_NAMES ? "Perfect " : "") + "Ruby necklace", "I wonder if this is valuable", "", 2175, 57, "items:57", false, true, 1024, 16724736, true, true, true, 693));
		items.add(new ItemDef("Family crest", "The crest of a varrocian noble family", "", 10, 213, "items:213", false, false, 0, 0, true, true, false, 694));
		items.add(new ItemDef("Crest fragment", "Part of the Fitzharmon family crest", "", 10, 214, "items:214", false, false, 0, 0, true, true, false, 695));
		items.add(new ItemDef("Crest fragment", "Part of the Fitzharmon family crest", "", 10, 215, "items:215", false, false, 0, 0, true, true, false, 696));
		items.add(new ItemDef("Crest fragment", "Part of the Fitzharmon family crest", "", 10, 216, "items:216", false, false, 0, 0, true, true, false, 697));
		items.add(new ItemDef("Steel gauntlets", "Very handy armour", "", 6, 217, "items:217", false, true, 256, 12303291, true, true, false, 698));
		items.add(new ItemDef("gauntlets of goldsmithing", "metal gloves for gold making", "", 6, 217, "items:217", false, true, 256, 16777130, true, true, false, 699));
		items.add(new ItemDef("gauntlets of cooking", "Used for cooking fish", "", 6, 217, "items:217", false, true, 256, 14540253, true, true, false, 700));
		items.add(new ItemDef("gauntlets of chaos", "Mind spells hit like chaos spells", "", 6, 217, "items:217", false, true, 256, 16755370, true, true, false, 701));
		items.add(new ItemDef("robe of Zamorak", "A robe worn by worshippers of Zamorak", "", 40, 87, "items:87", false, true, 64, 16711680, true, false, true, 702));
		items.add(new ItemDef("robe of Zamorak", "A robe worn by worshippers of Zamorak", "", 30, 88, "items:88", false, true, 128, 16711680, true, false, true, 703));
		items.add(new ItemDef("Address Label", "To lord Handelmort- Handelmort mansion", "", 10, 218, "items:218", false, false, 0, 0, true, true, false, 704));
		items.add(new ItemDef("Tribal totem", "It represents some sort of tribal god", "", 10, 219, "items:219", false, false, 0, 0, true, true, false, 705));
		items.add(new ItemDef("tourist guide", "Your definitive guide to Ardougne", "read", 1, 28, "items:28", false, false, 0, 11184895, true, false, true, 706));
		items.add(new ItemDef("spice", "Put it in uncooked stew to make curry", "", 230, 62, "items:62", false, false, 0, 16711680, true, false, true, 707));
		items.add(new ItemDef("Uncooked curry", "I need to cook this", "", 10, 162, "items:162", false, false, 0, 15643494, true, false, true, 708));
		items.add(new ItemDef("curry", "It's a spicey hot curry", "Eat", 20, 162, "items:162", false, false, 0, 12274688, true, false, true, 709));
		items.add(new ItemDef("Burnt curry", "Eew it's horribly burnt", "Empty", 1, 162, "items:162", false, false, 0, 5255216, true, false, true, 710));
		items.add(new ItemDef("yew logs certificate", "Each certificate exchangable at Ardougne for 5 yew logs", "", 10, 180, "items:180", true, false, 0, 0, true, false, false, 711));
		items.add(new ItemDef("maple logs certificate", "Each certificate exchangable at Ardougne for 5 maple logs", "", 20, 180, "items:180", true, false, 0, 0, true, false, false, 712));
		items.add(new ItemDef("willow logs certificate", "Each certificate exchangable at Ardougne for 5 willow logs", "", 30, 180, "items:180", true, false, 0, 0, true, false, false, 713));
		items.add(new ItemDef("lockpick", "It makes picking some locks easier", "", 20, 220, "items:220", false, false, 0, 0, true, false, true, 714));
		items.add(new ItemDef("Red vine worms", "Strange little red worms", "", 3, 175, "items:175", true, false, 0, 16711680, true, true, false, 715));
		items.add(new ItemDef("Blanket", "A child's blanket", "", 5, 92, "items:92", false, false, 0, 56831, true, true, false, 716));
		items.add(new ItemDef("Raw giant carp", "I should try cooking this", "", 50, 165, "items:165", false, false, 0, 80, true, true, true, 717));
		items.add(new ItemDef("giant Carp", "Some nicely cooked fish", "Eat", 50, 165, "items:165", false, false, 0, 12619984, true, true, true, 718));
		items.add(new ItemDef("Fishing competition Pass", "Admits one to the Hemenster fishing competition", "", 10, 218, "items:218", false, false, 0, 0, true, true, false, 719));
		items.add(new ItemDef("Hemenster fishing trophy", "Hurrah you won a fishing competition", "", 20, 221, "items:221", false, false, 0, 16763980, true, true, false, 720));
		items.add(new ItemDef("Pendant of Lucien", "Gets me through the chamber of fear", "", 12, 222, "items:222", false, true, 1024, 3158064, true, true, false, 721));
		items.add(new ItemDef("Boots of lightfootedness", "Wearing these makes me feel like I am floating", "", 6, 223, "items:223", false, true, 512, 16742144, true, true, true, 722));
		items.add(new ItemDef("Ice Arrows", "Can only be fired with yew or magic bows", "", 2, 11, "items:11", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 11206655, true, true, false, 723));
		items.add(new ItemDef("Lever", "This was once attached to something", "", 20, 224, "items:224", false, false, 0, 0, true, true, false, 724));
		items.add(new ItemDef("Staff of Armadyl", "A Magical staff", "", 15, 91, "items:91", false, true, 16, 16776960, true, true, true, 725));
		items.add(new ItemDef("Pendant of Armadyl", "Allows me to fight Lucien", "", 12, 222, "items:222", false, true, 1024, 0, true, true, false, 726));
		items.add(new ItemDef("Large cog", " A large old cog", "", 10, 241, "items:241", false, false, 0, 255, true, true, false, 727));
		items.add(new ItemDef("Large cog", " A large old cog", "", 10, 240, "items:240", false, false, 0, 0, true, true, false, 728));
		items.add(new ItemDef("Large cog", " A large old cog", "", 10, 241, "items:241", false, false, 0, 16711680, true, true, false, 729));
		items.add(new ItemDef("Large cog", " A large old cog", "", 10, 241, "items:241", false, false, 0, 13369548, true, true, false, 730));
		items.add(new ItemDef("Rat Poison", "This stuff looks nasty", "", 1, 52, "items:52", false, false, 0, 0, true, false, true, 731));
		items.add(new ItemDef("shiny Key", "Quite a small key", "", 1, 25, "items:25", false, false, 0, 12303291, true, true, false, 732));
		items.add(new ItemDef("khazard Helmet", "A medium sized helmet", "", 10, 5, "items:5", false, true, 32, 11250603, true, true, false, 733));
		items.add(new ItemDef("khazard chainmail", "A series of connected metal rings", "", 10, 7, "items:7", false, true, 64, 11250603, true, true, false, 734));
		items.add(new ItemDef("khali brew", "A bottle of khazard's worst brew", "drink", 5, 191, "items:191", false, false, 0, 16755200, true, false, true, 735));
		items.add(new ItemDef("khazard cell keys", "Keys for General Khazard's cells", "", 1, 25, "items:25", false, false, 0, 14540253, true, true, false, 736));
		items.add(new ItemDef("Poison chalice", "A strange looking drink", "drink", 20, 225, "items:225", false, false, 0, 11206400, true, true, true, 737));
		items.add(new ItemDef("magic whistle", "A small tin whistle", "blow", 10, 226, "items:226", false, false, 0, 0, true, true, false, 738));
		items.add(new ItemDef("Cup of tea", "A nice cup of tea", "drink", 10, 227, "items:227", false, false, 0, 0, true, false, true, 739));
		items.add(new ItemDef("orb of protection", "a strange glowing green orb", "", 1, 242, "items:242", false, false, 0, 14540253, true, true, false, 740));
		items.add(new ItemDef("orbs of protection", "two strange glowing green orbs", "", 1, 243, "items:243", false, false, 0, 14540253, true, true, false, 741));
		items.add(new ItemDef("Holy table napkin", "a cloth given to me by sir Galahad", "", 10, 92, "items:92", false, false, 0, 0, true, true, false, 742));
		items.add(new ItemDef("bell", "I wonder what happens when i ring it", "ring", 1, 228, "items:228", false, false, 0, 0, true, true, false, 743));
		items.add(new ItemDef("Gnome Emerald Amulet of protection", "It improves my defense", "", 0, 125, "items:125", false, true, 1024, 3394611, true, true, false, 744));
		items.add(new ItemDef("magic golden feather", "It will point the way for me", "blow on", 2, 176, "items:176", false, false, 0, 16776960, true, true, false, 745));
		items.add(new ItemDef("Holy grail", "A holy and powerful artifact", "", 1, 229, "items:229", false, false, 0, 0, true, true, false, 746));
		items.add(new ItemDef("Script of Hazeel", "An old scroll with strange ancient text", "", 1, 244, "items:244", false, false, 0, 14540253, true, true, false, 747));
		items.add(new ItemDef("Pineapple", "It can be cut up with a knife", "", 1, 124, "items:124", false, false, 0, 0, true, false, true, 748));
		items.add(new ItemDef("Pineapple ring", "Exotic fruit", "eat", 1, 230, "items:230", false, false, 0, 0, true, false, true, 749));
		items.add(new ItemDef("Pineapple Pizza", "A tropicana pizza", "Eat", 100, 155, "items:155", false, false, 0, 16777079, true, false, true, 750));
		items.add(new ItemDef("Half pineapple Pizza", "Half of this pizza has been eaten", "Eat", 50, 156, "items:156", false, false, 0, 16777079, true, false, true, 751));
		items.add(new ItemDef("Magic scroll", "Maybe I should read it", "read", 1, 244, "items:244", false, false, 0, 0, true, true, false, 752));
		items.add(new ItemDef("Mark of Hazeel", "A large metal amulet", "", 0, 245, "items:245", false, false, 0, 14540253, true, true, false, 753));
		items.add(new ItemDef("bloody axe of zamorak", "A vicious looking axe", "", 5000, 246, "items:246", false, true, 16, 15658734, true, true, true, 754));
		items.add(new ItemDef("carnillean armour", "the carnillean family armour", "", 65, 247, "items:247", false, false, 0, 15658734, true, true, false, 755));
		items.add(new ItemDef("Carnillean Key", "An old rusty key", "", 1, 25, "items:25", false, false, 0, 16772608, true, true, false, 756));
		items.add(new ItemDef("Cattle prod", "An old cattle prod", "", 15, 248, "items:248", false, false, 0, 16772608, true, true, true, 757));
		items.add(new ItemDef("Plagued sheep remains", "These sheep remains are infected", "", 0, 20, "items:20", false, false, 0, 14540253, true, true, false, 758));
		items.add(new ItemDef("Poisoned animal feed", "This looks nasty", "", 0, 250, "items:250", false, false, 0, 14540253, true, true, false, 759));
		items.add(new ItemDef("Protective jacket", "A thick heavy leather top", "", 50, 251, "items:251", false, true, 64, 14540253, true, true, true, 760));
		items.add(new ItemDef("Protective trousers", "A thick pair of leather trousers", "", 50, 252, "items:252", false, true, 644, 15654365, true, true, true, 761));
		items.add(new ItemDef("Plagued sheep remains", "These sheep remains are infected", "", 0, 20, "items:20", false, false, 0, 14540253, true, true, false, 762));
		items.add(new ItemDef("Plagued sheep remains", "These sheep remains are infected", "", 0, 20, "items:20", false, false, 0, 14540253, true, true, false, 763));
		items.add(new ItemDef("Plagued sheep remains", "These sheep remains are infected", "", 0, 20, "items:20", false, false, 0, 14540253, true, true, false, 764));
		items.add(new ItemDef("dwellberries", "some rather pretty blue berries", "eat", 4, 253, "items:253", false, false, 0, 0, true, false, true, 765));
		items.add(new ItemDef("Gasmask", "Stops me breathing nasty stuff", "", 2, 232, "items:232", false, true, 32, 0, true, true, true, 766));
		items.add(new ItemDef("picture", "A picture of a lady called Elena", "", 2, 233, "items:233", false, false, 0, 0, true, true, false, 767));
		items.add(new ItemDef("Book", "Turnip growing for beginners", "read", 1, 28, "items:28", false, false, 0, 16755455, true, true, false, 768));
		items.add(new ItemDef("Seaslug", "a rather nasty looking crustacean", "", 4, 254, "items:254", false, false, 0, 0, true, true, false, 769));
		items.add(new ItemDef("chocolaty milk", "Milk with chocolate in it", "drink", 2, 22, "items:22", false, false, 0, 9785408, true, true, true, 770));
		items.add(new ItemDef("Hangover cure", "It doesn't look very tasty", "", 2, 22, "items:22", false, false, 0, 8757312, true, true, false, 771));
		items.add(new ItemDef("Chocolate dust", "I prefer it in a bar shape", "", 2, 23, "items:23", false, false, 0, 9461792, true, false, true, 772));
		items.add(new ItemDef("Torch", "A unlit home made torch", "", 4, 255, "items:255", false, false, 0, 0, true, true, true, 773));
		items.add(new ItemDef("Torch", "A lit home made torch", "", 4, 256, "items:256", false, false, 0, 0, true, true, true, 774));
		items.add(new ItemDef("warrant", "A search warrant for a house in Ardougne", "", 5, 29, "items:29", false, false, 0, 0, true, true, false, 775));
		items.add(new ItemDef("Damp sticks", "Some damp wooden sticks", "", 0, 257, "items:257", false, false, 0, 0, true, true, false, 776));
		items.add(new ItemDef("Dry sticks", "Some dry wooden sticks", "rub together", 0, 258, "items:258", false, false, 0, 0, true, true, false, 777));
		items.add(new ItemDef("Broken glass", "Glass from a broken window pane", "", 0, 259, "items:259", false, false, 0, 0, true, true, false, 778));
		items.add(new ItemDef("oyster pearls", "I could work wonders with these and a chisel", "", 1400, 260, "items:260", false, false, 0, 0, true, false, true, 779));
		items.add(new ItemDef("little key", "Quite a small key", "", 1, 25, "items:25", false, false, 0, 12303291, true, true, false, 780));
		items.add(new ItemDef("Scruffy note", "It seems to say hongorer lure", "read", 2, 234, "items:234", false, false, 0, 0, true, false, false, 781));
		items.add(new ItemDef("Glarial's amulet", "A bright green gem set in a necklace", "", 1, 261, "items:261", false, true, 1024, 12303291, true, true, false, 782));
		items.add(new ItemDef("Swamp tar", "A foul smelling thick tar like substance", "", 1, 262, "items:262", true, false, 0, 12303291, true, false, false, 783));
		items.add(new ItemDef("Uncooked Swamp paste", "A thick tar like substance mixed with flour", "", 1, 263, "items:263", true, false, 0, 12303291, true, false, false, 784));
		items.add(new ItemDef("Swamp paste", "A tar like substance mixed with flour and warmed", "", 30, 263, "items:263", true, false, 0, 12303291, true, false, false, 785));
		items.add(new ItemDef("Oyster pearl bolts", "Great if you have a crossbow!", "", 110, 266, "items:266", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1001 : 0, 0, true, false, false, 786));
		items.add(new ItemDef("Glarials pebble", "A small pebble with elven inscription", "", 1, 264, "items:264", false, false, 0, 12303291, true, true, false, 787));
		items.add(new ItemDef("book on baxtorian", "A book on elven history in north runescape", "read", 2, 28, "items:28", false, false, 0, 0, true, true, false, 788));
		items.add(new ItemDef("large key", "I wonder what this is the key to", "", 1, 25, "items:25", false, false, 0, 16750848, true, true, false, 789));
		items.add(new ItemDef("Oyster pearl bolt tips", "Can be used to improve crossbow bolts", "", 56, 265, "items:265", true, false, 0, 12303291, true, false, false, 790));
		items.add(new ItemDef("oyster", "It's empty", "", 5, 267, "items:267", false, false, 0, 0, true, false, true, 791));
		items.add(new ItemDef("oyster pearls", "I could work wonders with these and a chisel", "", 112, 268, "items:268", false, false, 0, 0, true, false, true, 792));
		items.add(new ItemDef("oyster", "It's a rare oyster", "open", 200, 269, "items:269", false, false, 0, 0, true, false, true, 793));
		items.add(new ItemDef("Soil", "It's a bucket of fine soil", "", 2, 22, "items:22", false, false, 0, 12285815, true, true, true, 794));
		items.add(new ItemDef("Dragon medium Helmet", "A medium sized helmet", "", 100000, 271, "items:271", false, true, 32, 16711748, true, false, true, 795));
		items.add(new ItemDef("Mithril seed", "Magical seeds in a mithril case", "open", 5000, 270, "items:270", true, false, 0, 0, true, true, false, 796));
		items.add(new ItemDef("An old key", "A door key", "", 1, 25, "items:25", false, false, 0, 15636736, true, true, false, 797));
		items.add(new ItemDef("pigeon cage", "It's for holding pigeons", "", 1, 274, "items:274", false, false, 0, 15636736, true, true, false, 798));
		items.add(new ItemDef("Messenger pigeons", "some very plump birds", "release", 1, 275, "items:275", false, false, 0, 15636736, true, true, false, 799));
		items.add(new ItemDef("Bird feed", "A selection of mixed seeds", "", 1, 276, "items:276", false, false, 0, 15636736, true, true, false, 800));
		items.add(new ItemDef("Rotten apples", "Yuck!", "eat", 1, 277, "items:277", false, false, 0, 15636736, true, true, true, 801));
		items.add(new ItemDef("Doctors gown", "I do feel clever wearing this", "", 40, 87, "items:87", false, true, 64, 16777215, true, true, false, 802));
		items.add(new ItemDef("Bronze key", "A heavy key", "", 1, 25, "items:25", false, false, 0, 16737817, true, true, false, 803));
		items.add(new ItemDef("Distillator", "It's for seperating compounds", "", 1, 278, "items:278", false, false, 0, 16737817, true, true, false, 804));
		items.add(new ItemDef("Glarial's urn", "An urn containing glarials ashes", "", 1, 279, "items:279", false, false, 0, 0, false, true, false, 805));
		items.add(new ItemDef("Glarial's urn", "An empty metal urn", "", 1, 280, "items:280", false, false, 0, 0, false, true, false, 806));
		items.add(new ItemDef("Priest robe", "I feel closer to saradomin in this", "", 5, 87, "items:87", false, true, 64, 255, false, false, true, 807));
		items.add(new ItemDef("Priest gown", "I feel closer to saradomin in this", "", 5, 88, "items:88", false, true, 128, 255, false, false, true, 808));
		items.add(new ItemDef("Liquid Honey", "This isn't worth much", "", 0, 48, "items:48", false, false, 0, 16776960, true, true, false, 809));
		items.add(new ItemDef("Ethenea", "An expensive colourless liquid", "", 10, 48, "items:48", false, false, 0, 11184827, true, true, false, 810));
		items.add(new ItemDef("Sulphuric Broline", "it's highly poisonous", "", 1, 48, "items:48", false, false, 0, 11966902, true, true, false, 811));
		items.add(new ItemDef("Plague sample", "An air tight tin container", "", 1, 281, "items:281", false, false, 0, 0, true, true, false, 812));
		items.add(new ItemDef("Touch paper", "For scientific testing", "", 1, 282, "items:282", false, false, 0, 0, true, true, false, 813));
		items.add(new ItemDef("Dragon Bones", "Ew it's a pile of bones", "Bury", 1, 137, Config.S_WANT_CUSTOM_SPRITES ? "items:dragon_bones" : "items:137", false, false, 0, 0, true, false, true, 814));
		items.add(new ItemDef("Herb", "I need a closer look to identify this", "Identify", 1, 75, "items:75", false, false, 0, 0, true, true, true, 815));
		items.add(new ItemDef("Snake Weed", "A very rare jungle herb", "", 5, 75, "items:75", false, false, 0, 0, true, true, true, 816));
		items.add(new ItemDef("Herb", "I need a closer look to identify this", "Identify", 1, 75, "items:75", false, false, 0, 0, true, true, true, 817));
		items.add(new ItemDef("Ardrigal", "An interesting", "", 5, 75, "items:75", false, false, 0, 0, true, true, true, 818));
		items.add(new ItemDef("Herb", "I need a closer look to identify this", "Identify", 1, 75, "items:75", false, false, 0, 0, true, true, true, 819));
		items.add(new ItemDef("Sito Foil", "An rare species of jungle herb", "", 5, 75, "items:75", false, false, 0, 0, true, true, true, 820));
		items.add(new ItemDef("Herb", "I need a closer look to identify this", "Identify", 1, 75, "items:75", false, false, 0, 0, true, true, true, 821));
		items.add(new ItemDef("Volencia Moss", "A very rare species of jungle herb", "", 5, 75, "items:75", false, false, 0, 0, true, true, true, 822));
		items.add(new ItemDef("Herb", "I need a closer look to identify this", "Identify", 1, 75, "items:75", false, false, 0, 0, true, true, true, 823));
		items.add(new ItemDef("Rogues Purse", " A rare species of jungle herb", "", 5, 75, "items:75", false, false, 0, 0, true, true, true, 824));
		items.add(new ItemDef("Soul-Rune", "Used for high level curse spells", "", 2500, 235, "items:235", true, false, 0, 0, true, false, false, 825));
		items.add(new ItemDef("king lathas Amulet", "The amulet is red", "", 10, 125, "items:125", false, true, 1024, 13382451, true, true, false, 826));
		items.add(new ItemDef("Bronze Spear", "A bronze tipped spear", "", 4, 283, "items:283", false, true, 16, 16737817, true, false, true, 827));
		items.add(new ItemDef("halloween mask", "aaaarrrghhh ... i'm a monster", "", 15, 284, "items:284", false, true, 32, 65280, false, false, true, 828));
		items.add(new ItemDef("Dragon bitter", "A glass of frothy ale", "drink", 2, 90, "items:90", false, false, 0, 0, true, false, true, 829));
		items.add(new ItemDef("Greenmans ale", "A glass of frothy ale", "drink", 2, 90, "items:90", false, false, 0, 0, true, false, true, 830));
		items.add(new ItemDef("halloween mask", "aaaarrrghhh ... i'm a monster", "", 15, 284, "items:284", false, true, 32, 16711680, false, false, true, 831));
		items.add(new ItemDef("halloween mask", "aaaarrrghhh ... i'm a monster", "", 15, 284, "items:284", false, true, 32, 255, false, false, true, 832));
		items.add(new ItemDef("cocktail glass", "For sipping cocktails", "", 0, 285, "items:285", false, false, 0, 0, true, false, true, 833));
		items.add(new ItemDef("cocktail shaker", "For mixing cocktails", "pour", 2, 286, "items:286", false, false, 0, 0, true, false, true, 834));
		items.add(new ItemDef("Bone Key", "A key delicately carved key made from a single piece of bone", "Look", 1, 25, "items:25", false, false, 0, 16777215, true, true, false, 835));
		items.add(new ItemDef("gnome robe", "A high fashion robe", "", 180, 88, "items:88", false, true, 128, 16755370, true, false, true, 836));
		items.add(new ItemDef("gnome robe", "A high fashion robe", "", 180, 88, "items:88", false, true, 128, 11206570, true, false, true, 837));
		items.add(new ItemDef("gnome robe", "A high fashion robe", "", 180, 88, "items:88", false, true, 128, 11184895, true, false, true, 838));
		items.add(new ItemDef("gnome robe", "A high fashion robe", "", 180, 88, "items:88", false, true, 128, 16777164, true, false, true, 839));
		items.add(new ItemDef("gnome robe", "A high fashion robe", "", 180, 88, "items:88", false, true, 128, 13434879, true, false, true, 840));
		items.add(new ItemDef("gnomeshat", "A silly pointed hat", "", 160, 86, "items:86", false, true, 32, 16755370, true, false, true, 841));
		items.add(new ItemDef("gnomeshat", "A silly pointed hat", "", 160, 86, "items:86", false, true, 32, 11206570, true, false, true, 842));
		items.add(new ItemDef("gnomeshat", "A silly pointed hat", "", 160, 86, "items:86", false, true, 32, 11184895, true, false, true, 843));
		items.add(new ItemDef("gnomeshat", "A silly pointed hat", "", 160, 86, "items:86", false, true, 32, 16777164, true, false, true, 844));
		items.add(new ItemDef("gnomeshat", "A silly pointed hat", "", 160, 86, "items:86", false, true, 32, 13434879, true, false, true, 845));
		items.add(new ItemDef("gnome top", "rometti - the ultimate in gnome design", "", 180, 87, "items:87", false, true, 64, 16755370, true, false, true, 846));
		items.add(new ItemDef("gnome top", "rometti - the only name in gnome fashion!", "", 180, 87, "items:87", false, true, 64, 11206570, true, false, true, 847));
		items.add(new ItemDef("gnome top", "rometti - the only name in gnome fashion!", "", 180, 87, "items:87", false, true, 64, 11184895, true, false, true, 848));
		items.add(new ItemDef("gnome top", "rometti - the only name in gnome fashion!", "", 180, 87, "items:87", false, true, 64, 16777164, true, false, true, 849));
		items.add(new ItemDef("gnome top", "rometti - the only name in gnome fashion!", "", 180, 87, "items:87", false, true, 64, 13434879, true, false, true, 850));
		items.add(new ItemDef("gnome cocktail guide", "A book on tree gnome cocktails", "read", 2, 299, "items:299", false, false, 0, 0, true, false, true, 851));
		items.add(new ItemDef("Beads of the dead", "A curious looking neck ornament", "", 35, 24, "items:24", false, true, 1024, 16737817, true, true, false, 852));
		items.add(new ItemDef("cocktail glass", "For sipping cocktails", "drink", 2, 288, "items:288", false, false, 0, 0, true, false, true, 853));
		items.add(new ItemDef("cocktail glass", "For sipping cocktails", "drink", 2, 289, "items:289", false, false, 0, 0, true, false, true, 854));
		items.add(new ItemDef("lemon", "It's very fresh", "eat", 2, 290, "items:290", false, false, 0, 0, true, false, true, 855));
		items.add(new ItemDef("lemon slices", "It's very fresh", "eat", 2, 291, "items:291", false, false, 0, 16763952, true, false, true, 856));
		items.add(new ItemDef("orange", "It's very fresh", "eat", 2, 292, "items:292", false, false, 0, 0, true, false, true, 857));
		items.add(new ItemDef("orange slices", "It's very fresh", "eat", 2, 291, "items:291", false, false, 0, 16760880, true, false, true, 858));
		items.add(new ItemDef("Diced orange", "Fresh chunks of orange", "eat", 2, 293, "items:293", false, false, 0, 16760880, true, false, true, 859));
		items.add(new ItemDef("Diced lemon", "Fresh chunks of lemon", "eat", 2, 293, "items:293", false, false, 0, 16763952, true, false, true, 860));
		items.add(new ItemDef("Fresh Pineapple", "It can be cut up with a knife", "eat", 1, 124, "items:124", false, false, 0, 0, true, false, true, 861));
		items.add(new ItemDef("Pineapple chunks", "Fresh chunks of pineapple", "eat", 1, 293, "items:293", false, false, 0, 16760880, true, false, true, 862));
		items.add(new ItemDef("lime", "It's very fresh", "eat", 2, 294, "items:294", false, false, 0, 0, true, false, true, 863));
		items.add(new ItemDef("lime chunks", "Fresh chunks of lime", "eat", 1, 293, "items:293", false, false, 0, 65280, true, false, true, 864));
		items.add(new ItemDef("lime slices", "It's very fresh", "eat", 2, 291, "items:291", false, false, 0, 65280, true, false, true, 865));
		items.add(new ItemDef("fruit blast", "A cool refreshing fruit mix", "drink", 2, 295, "items:295", false, false, 0, 0, true, false, true, 866));
		items.add(new ItemDef("odd looking cocktail", "A cool refreshing mix", "drink", 2, 289, "items:289", false, false, 0, 0, true, false, true, 867));
		items.add(new ItemDef("Whisky", "A locally brewed Malt", "drink", 5, 191, "items:191", false, false, 0, 16755200, true, false, true, 868));
		items.add(new ItemDef("vodka", "A strong spirit", "drink", 5, 191, "items:191", false, false, 0, 16755200, true, false, true, 869));
		items.add(new ItemDef("gin", "A strong spirit", "drink", 5, 191, "items:191", false, false, 0, 16755200, true, false, true, 870));
		items.add(new ItemDef("cream", "Fresh cream", "eat", 2, 296, "items:296", false, false, 0, 0, true, false, true, 871));
		items.add(new ItemDef(Config.S_IMPROVED_ITEM_OBJECT_NAMES ? "Drunk dragon (Player-mixed)" : "Drunk dragon", "A warm creamy alcoholic beverage", "drink", 2, 297, "items:297", false, false, 0, 0, true, false, true, 872));
		items.add(new ItemDef("Equa leaves", "Small sweet smelling leaves", "eat", 2, 298, "items:298", false, false, 0, 0, true, false, true, 873));
		items.add(new ItemDef("SGG", "A short green guy..looks good", "drink", 2, 295, "items:295", false, false, 0, 0, true, false, true, 874));
		items.add(new ItemDef("Chocolate saturday", "A warm creamy alcoholic beverage", "drink", 2, 297, "items:297", false, false, 0, 0, true, false, true, 875));
		items.add(new ItemDef("brandy", "A strong spirit", "drink", 5, 191, "items:191", false, false, 0, 16755200, true, false, true, 876));
		items.add(new ItemDef("blurberry special", "Looks good..smells strong", "drink", 2, 295, "items:295", false, false, 0, 0, true, false, true, 877));
		items.add(new ItemDef("wizard blizzard", "Looks like a strange mix", "drink", 2, 295, "items:295", false, false, 0, 0, true, false, true, 878));
		items.add(new ItemDef("pineapple punch", "A fresh healthy fruit mix", "drink", 2, 295, "items:295", false, false, 0, 0, true, false, true, 879));
		items.add(new ItemDef("gnomebatta dough", "Dough formed into a base", "", 2, 300, "items:300", false, false, 0, 0, true, false, true, 880));
		items.add(new ItemDef("gianne dough", "It's made from a secret recipe", "mould", 2, 301, "items:301", false, false, 0, 0, true, false, true, 881));
		items.add(new ItemDef("gnomebowl dough", "Dough formed into a bowl shape", "", 2, 302, "items:302", false, false, 0, 0, true, false, true, 882));
		items.add(new ItemDef("gnomecrunchie dough", "Dough formed into cookie shapes", "", 2, 303, "items:303", false, false, 0, 0, true, false, true, 883));
		items.add(new ItemDef("gnomebatta", "A baked dough base", "", 2, 300, "items:300", false, false, 0, 0, true, false, true, 884));
		items.add(new ItemDef("gnomebowl", "A baked dough bowl", "eat", 2, 302, "items:302", false, false, 0, 0, true, false, true, 885));
		items.add(new ItemDef("gnomebatta", "It's burnt to a sinder", "", 2, 304, "items:304", false, false, 0, 0, true, false, true, 886));
		items.add(new ItemDef("gnomecrunchie", "They're burnt to a sinder", "", 2, 306, "items:306", false, false, 0, 0, true, false, true, 887));
		items.add(new ItemDef("gnomebowl", "It's burnt to a sinder", "", 2, 305, "items:305", false, false, 0, 0, true, false, true, 888));
		items.add(new ItemDef("Uncut Red Topaz", "A semi precious stone", "", 40, 73, "items:73", false, false, 0, 16525133, true, false, true, 889));
		items.add(new ItemDef("Uncut Jade", "A semi precious stone", "", 30, 73, "items:73", false, false, 0, 10025880, true, false, true, 890));
		items.add(new ItemDef("Uncut Opal", "A semi precious stone", "", 20, 73, "items:73", false, false, 0, 16777124, true, false, true, 891));
		items.add(new ItemDef("Red Topaz", "A semi precious stone", "", 200, 74, "items:74", false, false, 0, 16525133, true, false, true, 892));
		items.add(new ItemDef("Jade", "A semi precious stone", "", 150, 74, "items:74", false, false, 0, 10025880, true, false, true, 893));
		items.add(new ItemDef("Opal", "A semi precious stone", "", 100, 74, "items:74", false, false, 0, 16777124, true, false, true, 894));
		items.add(new ItemDef("Swamp Toad", "Slippery little blighters", "remove legs", 2, 307, "items:307", false, false, 0, 0, true, false, true, 895));
		items.add(new ItemDef("Toad legs", "Gnome delicacy apparently", "eat", 2, 308, "items:308", false, false, 0, 0, true, false, true, 896));
		items.add(new ItemDef("King worm", "Gnome delicacy apparently", "eat", 2, 309, "items:309", false, false, 0, 0, true, false, true, 897));
		items.add(new ItemDef("Gnome spice", "Aluft Giannes secret reciepe", "", 2, 310, "items:310", false, false, 0, 0, true, false, true, 898));
		items.add(new ItemDef("gianne cook book", "Aluft Giannes favorite dishes", "read", 2, 299, "items:299", false, false, 0, 0, true, false, true, 899));
		items.add(new ItemDef("gnomecrunchie", "yum ... smells good", "eat", 2, 311, "items:311", false, false, 0, 0, true, false, true, 900));
		items.add(new ItemDef("cheese and tomato batta", "Smells really good", "eat", 2, 312, "items:312", false, false, 0, 0, true, false, true, 901));
		items.add(new ItemDef("toad batta", "actually smells quite good", "eat", 2, 312, "items:312", false, false, 0, 0, true, false, true, 902));
		items.add(new ItemDef("gnome batta", "smells like pants", "eat", 2, 312, "items:312", false, false, 0, 0, true, false, true, 903));
		items.add(new ItemDef("worm batta", "actually smells quite good", "eat", 2, 312, "items:312", false, false, 0, 0, true, false, true, 904));
		items.add(new ItemDef("fruit batta", "actually smells quite good", "eat", 2, 312, "items:312", false, false, 0, 0, true, false, true, 905));
		items.add(new ItemDef("Veg batta", "well..it looks healthy", "eat", 2, 312, "items:312", false, false, 0, 0, true, false, true, 906));
		items.add(new ItemDef("Chocolate bomb", "Looks great", "eat", 2, 313, "items:313", false, false, 0, 0, true, false, true, 907));
		items.add(new ItemDef("Vegball", "Looks pretty healthy", "eat", 2, 314, "items:314", false, false, 0, 0, true, false, true, 908));
		items.add(new ItemDef("worm hole", "actually smells quite good", "eat", 2, 315, "items:315", false, false, 0, 0, true, false, true, 909));
		items.add(new ItemDef("Tangled toads legs", "actually smells quite good", "eat", 2, 316, "items:316", false, false, 0, 0, true, false, true, 910));
		items.add(new ItemDef("Choc crunchies", "yum ... smells good", "eat", 2, 311, "items:311", false, false, 0, 0, true, false, true, 911));
		items.add(new ItemDef("Worm crunchies", "actually smells quite good", "eat", 2, 311, "items:311", false, false, 0, 0, true, false, true, 912));
		items.add(new ItemDef("Toad crunchies", "actually smells quite good", "eat", 2, 311, "items:311", false, false, 0, 0, true, false, true, 913));
		items.add(new ItemDef("Spice crunchies", "yum ... smells good", "eat", 2, 311, "items:311", false, false, 0, 0, true, false, true, 914));
		items.add(new ItemDef("Crushed Gemstone", "A gemstone that has been smashed", "", 2, 23, "items:23", false, false, 0, 16777215, true, false, true, 915));
		items.add(new ItemDef("Blurberry badge", "an official cocktail maker", "", 2, 317, "items:317", false, false, 0, 16711680, true, false, true, 916));
		items.add(new ItemDef("Gianne badge", "an official gianne chef", "", 2, 317, "items:317", false, false, 0, 65280, true, false, true, 917));
		items.add(new ItemDef("tree gnome translation", "Translate the old gnome tounge", "read", 2, 299, "items:299", false, false, 0, 0, true, false, true, 918));
		items.add(new ItemDef("Bark sample", "A sample from the grand tree", "", 2, 318, "items:318", false, false, 0, 0, true, true, false, 919));
		items.add(new ItemDef("War ship", "A model of a karamja warship", "play with", 2, 319, "items:319", false, false, 0, 0, true, false, true, 920));
		items.add(new ItemDef("gloughs journal", "Glough's private notes", "read", 2, 299, "items:299", false, false, 0, 0, true, true, false, 921));
		items.add(new ItemDef("invoice", "A note with foreman's timber order", "read", 2, 234, "items:234", false, false, 0, 0, true, true, false, 922));
		items.add(new ItemDef("Ugthanki Kebab", "A strange smelling Kebab made from Ugthanki meat - it doesn't look too good", "eat", 20, 95, "items:95", false, false, 0, 0, true, false, true, 923));
		items.add(new ItemDef("special curry", "It's a spicy hot curry", "Eat", 20, 162, "items:162", false, false, 0, 12274688, true, false, true, 924));
		items.add(new ItemDef("glough's key", "Glough left this at anita's", "", 1, 25, "items:25", false, false, 0, 14540253, true, true, false, 925));
		items.add(new ItemDef("glough's notes", "Scribbled notes and diagrams", "read", 2, 234, "items:234", false, false, 0, 0, true, true, false, 926));
		items.add(new ItemDef("Pebble", "The pebble has an inscription", "", 2, 321, "items:321", false, false, 0, 0, true, true, false, 927));
		items.add(new ItemDef("Pebble", "The pebble has an inscription", "", 2, 322, "items:322", false, false, 0, 0, true, true, false, 928));
		items.add(new ItemDef("Pebble", "The pebble has an inscription", "", 2, 323, "items:323", false, false, 0, 0, true, true, false, 929));
		items.add(new ItemDef("Pebble", "The pebble has an inscription", "", 2, 324, "items:324", false, false, 0, 0, true, true, false, 930));
		items.add(new ItemDef("Daconia rock", "A magicaly crafted stone", "", 40, 73, "items:73", false, false, 0, 14540253, true, true, false, 931));
		items.add(new ItemDef("Sinister key", "You get a sense of dread from this key", "", 1, 25, "items:25", false, false, 0, 1118481, true, false, true, 932));
		items.add(new ItemDef("Herb", "I need a closer look to identify this", "Identify", 1, 75, "items:75", false, false, 0, 0, true, false, true, 933));
		items.add(new ItemDef("Torstol", "A useful herb", "", 25, 75, "items:75", false, false, 0, 0, true, false, true, 934));
		items.add(new ItemDef("Unfinished potion", "I need Jangerberries to finish this Torstol potion", "", 25, 48, "items:48", false, false, 0, 12285696, true, false, true, 935));
		items.add(new ItemDef("Jangerberries", "They don't look very ripe", "eat", 1, 21, "items:21", false, false, 0, 4497408, true, false, true, 936));
		items.add(new ItemDef("fruit blast", "A cool refreshing fruit mix", "drink", 30, 295, "items:295", false, false, 0, 0, true, false, true, 937));
		items.add(new ItemDef("blurberry special", "Looks good..smells strong", "drink", 30, 295, "items:295", false, false, 0, 0, true, false, true, 938));
		items.add(new ItemDef("wizard blizzard", "Looks like a strange mix", "drink", 30, 295, "items:295", false, false, 0, 0, true, false, true, 939));
		items.add(new ItemDef("pineapple punch", "A fresh healthy fruit mix", "drink", 30, 295, "items:295", false, false, 0, 0, true, false, true, 940));
		items.add(new ItemDef("SGG", "A short green guy..looks good", "drink", 30, 295, "items:295", false, false, 0, 0, true, false, true, 941));
		items.add(new ItemDef("Chocolate saturday", "A warm creamy alcoholic beverage", "drink", 30, 297, "items:297", false, false, 0, 0, true, false, true, 942));
		items.add(new ItemDef("Drunk dragon", "A warm creamy alcoholic beverage", "drink", 30, 297, "items:297", false, false, 0, 0, true, false, true, 943));
		items.add(new ItemDef("cheese and tomato batta", "Smells really good", "eat", 120, 312, "items:312", false, false, 0, 0, true, false, true, 944));
		items.add(new ItemDef("toad batta", "actually smells quite good", "eat", 120, 312, "items:312", false, false, 0, 0, true, false, true, 945));
		items.add(new ItemDef("gnome batta", "smells like pants", "eat", 120, 312, "items:312", false, false, 0, 0, true, false, true, 946));
		items.add(new ItemDef("worm batta", "actually smells quite good", "eat", 120, 312, "items:312", false, false, 0, 0, true, false, true, 947));
		items.add(new ItemDef("fruit batta", "actually smells quite good", "eat", 120, 312, "items:312", false, false, 0, 0, true, false, true, 948));
		items.add(new ItemDef("Veg batta", "well..it looks healthy", "eat", 120, 312, "items:312", false, false, 0, 0, true, false, true, 949));
		items.add(new ItemDef("Chocolate bomb", "Looks great", "eat", 160, 313, "items:313", false, false, 0, 0, true, false, true, 950));
		items.add(new ItemDef("Vegball", "Looks pretty healthy", "eat", 150, 314, "items:314", false, false, 0, 0, true, false, true, 951));
		items.add(new ItemDef("worm hole", "actually smells quite good", "eat", 150, 315, "items:315", false, false, 0, 0, true, false, true, 952));
		items.add(new ItemDef("Tangled toads legs", "actually smells quite good", "eat", 160, 316, "items:316", false, false, 0, 0, true, false, true, 953));
		items.add(new ItemDef("Choc crunchies", "yum ... smells good", "eat", 85, 311, "items:311", false, false, 0, 0, true, false, true, 954));
		items.add(new ItemDef("Worm crunchies", "actually smells quite good", "eat", 85, 311, "items:311", false, false, 0, 0, true, false, true, 955));
		items.add(new ItemDef("Toad crunchies", "actually smells quite good", "eat", 85, 311, "items:311", false, false, 0, 0, true, false, true, 956));
		items.add(new ItemDef("Spice crunchies", "yum ... smells good", "eat", 85, 311, "items:311", false, false, 0, 0, true, false, true, 957));
		items.add(new ItemDef("Stone-Plaque", "A stone plaque with carved letters in it", "Read", 5, 236, "items:236", false, false, 0, 0, true, true, false, 958));
		items.add(new ItemDef("Tattered Scroll", "An ancient tattered scroll", "Read", 5, 29, "items:29", false, false, 0, 255, true, true, false, 959));
		items.add(new ItemDef("Crumpled Scroll", "An ancient crumpled scroll", "Read", 5, 29, "items:29", false, false, 0, 12648448, true, true, false, 960));
		items.add(new ItemDef("Bervirius Tomb Notes", "Notes taken from the tomb of Bervirius", "Read", 5, 29, "items:29", false, false, 0, 16776960, true, true, false, 961));
		items.add(new ItemDef("Zadimus Corpse", "The remains of Zadimus", "Bury", 1, 237, "items:237", false, false, 0, 15400622, true, true, false, 962));
		items.add(new ItemDef("Potion of Zamorak", "It looks scary", "drink", 25, 48, "items:48", false, false, 0, 15636736, true, false, true, 963));
		items.add(new ItemDef("Potion of Zamorak", "It looks scary", "drink", 25, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 15636736, true, false, true, 964));
		items.add(new ItemDef("Potion of Zamorak", "It looks scary", "drink", 25, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 15636736, true, false, true, 965));
		items.add(new ItemDef("Boots", "They're soft and silky", "", 200, 223, "items:223", false, true, 512, 16755370, true, false, true, 966));
		items.add(new ItemDef("Boots", "They're soft and silky", "", 200, 223, "items:223", false, true, 512, 11206570, true, false, true, 967));
		items.add(new ItemDef("Boots", "They're soft and silky", "", 200, 223, "items:223", false, true, 512, 11184895, true, false, true, 968));
		items.add(new ItemDef("Boots", "They're soft and silky", "", 200, 223, "items:223", false, true, 512, 16777164, true, false, true, 969));
		items.add(new ItemDef("Boots", "They're soft and silky", "", 200, 223, "items:223", false, true, 512, 13434879, true, false, true, 970));
		items.add(new ItemDef("Santa's hat", "It's a santa claus' hat", "", 160, 325, "items:325", false, true, 32, 0, false, false, true, 971));
		items.add(new ItemDef("Locating Crystal", "A magical crystal sphere", "Activate", 100, 199, "items:199", false, false, 0, 12648447, true, true, false, 972));
		items.add(new ItemDef("Sword Pommel", "An ivory sword pommel", "", 100, 334, "items:334", false, false, 0, 16777088, true, true, false, 973));
		items.add(new ItemDef("Bone Shard", "A slender piece of bone", "Look", 1, 238, "items:238", false, false, 0, 0, true, true, false, 974));
		items.add(new ItemDef("Steel Wire", "Useful for crafting items", "", 200, 326, "items:326", false, false, 0, 0, true, false, true, 975));
		items.add(new ItemDef("Bone Beads", "Beads carved out of bone", "", 1, 239, "items:239", false, false, 0, 16777152, true, true, false, 976));
		items.add(new ItemDef("Rashiliya Corpse", "The remains of the Zombie Queen", "Bury", 1, 237, "items:237", false, false, 0, 16744576, true, true, false, 977));
		items.add(new ItemDef("ResetCrystal", "Helps reset things in game", "Activate", 100, 199, "items:199", false, false, 0, 182474, true, false, true, 978));
		items.add(new ItemDef("Bronze Wire", "Useful for crafting items", "", 20, 326, "items:326", false, false, 0, 16737817, true, false, true, 979));
		items.add(new ItemDef("Present", "Click to use this on a friend", "open", 160, 330, "items:330", false, false, 0, 0, false, false, true, 980));
		items.add(new ItemDef("Gnome Ball", "Lets play", "shoot", 10, 327, "items:327", false, false, 0, 0, true, true, true, 981));
		items.add(new ItemDef("Papyrus", "Used for making notes", "", 9, 282, "items:282", false, false, 0, 0, true, false, true, 982));
		items.add(new ItemDef("A lump of Charcoal", "a lump of cooked coal good for making marks.", "", 45, 73, "items:73", false, false, 0, 2105376, true, false, true, 983));
		items.add(new ItemDef("Arrow", "linen wrapped around an arrow head", "", 10, 328, "items:328", false, false, 0, 0, true, true, false, 984));
		items.add(new ItemDef("Lit Arrow", "A flamming arrow", "", 10, 329, "items:329", true, false, 0, 0, true, true, false, 985));
		items.add(new ItemDef("Rocks", "A few Large rocks", "", 10, 331, "items:331", false, false, 0, 0, true, true, false, 986));
		items.add(new ItemDef("Paramaya Rest Ticket", "Allows you to rest in the luxurius Paramaya Inn", "", 5, 218, "items:218", false, false, 0, 0, true, true, false, 987));
		items.add(new ItemDef("Ship Ticket", "Allows you passage on the 'Lady of the Waves' ship.", "", 5, 218, "items:218", false, false, 0, 8454143, true, true, false, 988));
		items.add(new ItemDef("Damp cloth", "It smells as if it's been doused in alcohol", "", 10, 92, "items:92", false, false, 0, 0, true, true, false, 989));
		items.add(new ItemDef("Desert Boots", "Boots made specially for the desert", "", 20, 223, "items:223", false, true, 512, 16777215, true, false, true, 990));
		items.add(new ItemDef("Orb of light", "The orb gives you a safe peaceful feeling", "", 10, 333, "items:333", false, false, 0, 0, true, true, false, 991));
		items.add(new ItemDef("Orb of light", "The orb gives you a safe peaceful feeling", "", 10, 345, "items:345", false, false, 0, 0, true, true, false, 992));
		items.add(new ItemDef("Orb of light", "The orb gives you a safe peaceful feeling", "", 10, 346, "items:346", false, false, 0, 0, true, true, false, 993));
		items.add(new ItemDef("Orb of light", "The orb gives you a safe peaceful feeling", "", 10, 347, "items:347", false, false, 0, 0, true, true, false, 994));
		items.add(new ItemDef("Railing", "A broken metal rod", "", 10, 335, "items:335", false, false, 0, 0, true, true, false, 995));
		items.add(new ItemDef("Randas's journal", "An old journal with several pages missing", "read", 1, 28, "items:28", false, false, 0, 15641258, true, true, false, 996));
		items.add(new ItemDef("Unicorn horn", "Poor unicorn went splat!", "", 20, 145, "items:145", false, false, 0, 0, true, true, true, 997));
		items.add(new ItemDef("Coat of Arms", "A symbol of truth and all that is good", "", 10, 348, "items:348", false, false, 0, 0, true, true, false, 998));
		items.add(new ItemDef("Coat of Arms", "A symbol of truth and all that is good", "", 10, 336, "items:336", false, false, 0, 0, true, true, false, 999));
		items.add(new ItemDef("Staff of Iban", "It's a slightly magical stick", "", 15, 337, "items:337", false, true, 8216, 0, true, true, true, 1000));
		items.add(new ItemDef("Dwarf brew", "It's a bucket of home made brew", "", 2, 22, "items:22", false, false, 0, 12285815, true, true, false, 1001));
		items.add(new ItemDef("Ibans Ashes", "A heap of ashes", "", 2, 23, "items:23", false, false, 0, 11184810, true, true, false, 1002));
		items.add(new ItemDef("Cat", "She's sleeping..i think!", "", 2, 338, "items:338", false, false, 0, 11184810, true, true, false, 1003));
		items.add(new ItemDef("A Doll of Iban", "A strange doll made from sticks and cloth", "search", 2, 339, "items:339", false, false, 0, 11184810, true, true, false, 1004));
		items.add(new ItemDef("Old Journal", "I wonder who wrote this!", "read", 1, 28, "items:28", false, false, 0, 16755370, true, true, false, 1005));
		items.add(new ItemDef("Klank's gauntlets", "Heavy hand protection", "", 6, 217, "items:217", false, true, 256, 12303291, true, true, false, 1006));
		items.add(new ItemDef("Iban's shadow", "A dark mystical liquid", "", 2, 340, "items:340", false, false, 0, 11184810, true, true, false, 1007));
		items.add(new ItemDef("Iban's conscience", "The remains of a dove that died long ago", "", 2, 341, "items:341", false, false, 0, 11184810, true, true, false, 1008));
		items.add(new ItemDef("Amulet of Othainian", "A strange looking amulet", "", 0, 125, "items:125", false, true, 1024, 12255487, true, true, false, 1009));
		items.add(new ItemDef("Amulet of Doomion", "A strange looking amulet", "", 0, 125, "items:125", false, true, 1024, 12255487, true, true, false, 1010));
		items.add(new ItemDef("Amulet of Holthion", "A strange looking amulet", "", 0, 125, "items:125", false, true, 1024, 12255487, true, true, false, 1011));
		items.add(new ItemDef("keep key", "A small prison key", "", 1, 25, "items:25", false, false, 0, 12303291, true, true, false, 1012));
		items.add(new ItemDef("Bronze Throwing Dart", "A deadly throwing dart with a bronze tip.", "", 2, 231, "items:231", true, true, 16, 16737817, true, false, false, 1013));
		items.add(new ItemDef("Prototype Throwing Dart", "A proto type of a deadly throwing dart.", "", 70, 231, "items:231", true, false, 0, 16737817, true, true, false, 1014));
		items.add(new ItemDef("Iron Throwing Dart", "A deadly throwing dart with an iron tip.", "", 5, 231, "items:231", true, true, 16, 15654365, true, false, false, 1015));
		items.add(new ItemDef("Full Water Skin", "A skinful of water", "", 30, 343, "items:343", false, false, 0, 8404992, true, false, true, 1016));
		items.add(new ItemDef("Lens mould", "A peculiar mould in the shape of a disc", "", 10, 342, "items:342", false, false, 0, 0, true, true, true, 1017));
		items.add(new ItemDef("Lens", "A perfectly formed glass disc", "", 10, 344, "items:344", false, false, 0, 0, true, true, true, 1018));
		items.add(new ItemDef("Desert Robe", "Cool light robe to wear in the desert", "", 40, 88, "items:88", false, true, 128, 16777215, true, false, true, 1019));
		items.add(new ItemDef("Desert Shirt", "A light cool shirt to wear in the desert", "", 40, 87, "items:87", false, true, 64, 16777215, true, false, true, 1020));
		items.add(new ItemDef("Metal Key", "A large metalic key.", "", 1, 25, "items:25", false, false, 0, 12632256, true, true, false, 1021));
		items.add(new ItemDef("Slaves Robe Bottom", "A dirty desert skirt", "", 40, 88, "items:88", false, true, 128, 8421376, true, false, true, 1022));
		items.add(new ItemDef("Slaves Robe Top", "A dirty desert shirt", "", 40, 87, "items:87", false, true, 64, 8421376, true, false, true, 1023));
		items.add(new ItemDef("Steel Throwing Dart", "A deadly throwing dart with a steel tip.", "", 20, 231, "items:231", true, true, 16, 15658734, true, false, false, 1024));
		items.add(new ItemDef("Astrology Book", "A book on Astrology in runescape", "Read", 2, 28, "items:28", false, false, 0, 0, true, true, false, 1025));
		items.add(new ItemDef("Unholy Symbol mould", "use this with silver in a furnace", "", 200, 349, "items:349", false, false, 0, 0, true, true, true, 1026));
		items.add(new ItemDef("Unholy Symbol of Zamorak", "this needs stringing", "", 200, 350, "items:350", false, false, 0, 0, true, true, true, 1027));
		items.add(new ItemDef("Unblessed Unholy Symbol of Zamorak", "this needs blessing", "", 200, 351, "items:351", false, true, 1024, 0, true, true, true, 1028));
		items.add(new ItemDef("Unholy Symbol of Zamorak", "a symbol indicating allegiance to Zamorak", "", 200, 351, "items:351", false, true, 1024, 0, true, true, true, 1029));
		items.add(new ItemDef("Shantay Desert Pass", "Allows you into the desert through the Shantay pass worth 5 gold.", "", 5, 218, "items:218", true, false, 0, 13083169, true, true, false, 1030));
		items.add(new ItemDef("Staff of Iban", "The staff is damaged", "wield", 15, 337, "items:337", false, false, 0, 0, true, true, true, 1031));
		items.add(new ItemDef("Dwarf cannon base", "bang", "set down", 200000, 352, "items:352", false, false, 0, 0, true, false, true, 1032));
		items.add(new ItemDef("Dwarf cannon stand", "bang", "", 200000, 353, "items:353", false, false, 0, 0, true, false, true, 1033));
		items.add(new ItemDef("Dwarf cannon barrels", "bang", "", 200000, 354, "items:354", false, false, 0, 0, true, false, true, 1034));
		items.add(new ItemDef("Dwarf cannon furnace", "bang", "", 200000, 355, "items:355", false, false, 0, 0, true, false, true, 1035));
		items.add(new ItemDef("Fingernails", "Ugh gross!", "", 0, 356, "items:356", false, false, 0, 0, true, true, false, 1036));
		items.add(new ItemDef("Powering crystal1", "An intricately cut gemstone", "", 0, 357, "items:357", false, false, 0, 16777011, true, true, false, 1037));
		items.add(new ItemDef("Mining Barrel", "A roughly constructed barrel for carrying rock.", "", 100, 358, "items:358", false, false, 0, 65280, true, true, false, 1038));
		items.add(new ItemDef("Ana in a Barrel", "A roughly constructed barrel with an Ana in it!", "Look", 100, 359, "items:359", false, false, 0, 16711680, true, true, false, 1039));
		items.add(new ItemDef("Stolen gold", "I wish I could spend it", "", 300, 79, "items:79", false, false, 0, 16763980, true, true, false, 1040));
		items.add(new ItemDef("multi cannon ball", "A heavy metal spiked ball", "", 10, 332, "items:332", true, false, 0, 0, true, false, false, 1041));
		items.add(new ItemDef("Railing", "A metal railing replacement", "", 10, 335, "items:335", false, false, 0, 0, true, true, false, 1042));
		items.add(new ItemDef("Ogre tooth", "big sharp and nasty", "", 0, 360, "items:360", false, false, 0, 0, true, true, false, 1043));
		items.add(new ItemDef("Ogre relic", "A grotesque symbol of the ogres", "", 0, 361, "items:361", false, false, 0, 0, true, true, false, 1044));
		items.add(new ItemDef("Skavid map", "A map of cave locations", "", 0, 362, "items:362", false, false, 0, 0, true, true, false, 1045));
		items.add(new ItemDef("dwarf remains", "The remains of a dwarf savaged by goblins", "", 1, 237, "items:237", false, false, 0, 16744576, true, true, false, 1046));
		items.add(new ItemDef("Key", "A key for a chest", "", 1, 25, "items:25", false, false, 0, 16750848, true, true, false, 1047));
		items.add(new ItemDef("Ogre relic part", "A piece of a statue", "", 0, 363, "items:363", false, false, 0, 0, true, true, false, 1048));
		items.add(new ItemDef("Ogre relic part", "A piece of a statue", "", 0, 364, "items:364", false, false, 0, 0, true, true, false, 1049));
		items.add(new ItemDef("Ogre relic part", "A piece of a statue", "", 0, 365, "items:365", false, false, 0, 0, true, true, false, 1050));
		items.add(new ItemDef("Ground bat bones", "The ground bones of a bat", "", 20, 23, "items:23", false, false, 0, 15645520, true, true, true, 1051));
		items.add(new ItemDef("Unfinished potion", "I need another ingredient to finish the shaman potion", "", 3, 48, "items:48", false, false, 0, 56576, true, true, true, 1052));
		items.add(new ItemDef("Ogre potion", "A strange liquid", "", 120, 48, "items:48", false, false, 0, 13434726, true, true, true, 1053));
		items.add(new ItemDef("Magic ogre potion", "A strange liquid that bubbles with power", "", 120, 48, "items:48", false, false, 0, 6750156, true, true, true, 1054));
		items.add(new ItemDef("Tool kit", "These could be handy!", "", 120, 366, "items:366", false, false, 0, 15654365, true, true, false, 1055));
		items.add(new ItemDef("Nulodion's notes", "Construction notes for dwarf cannon ammo", "read", 1, 234, "items:234", false, false, 0, 0, true, true, false, 1056));
		items.add(new ItemDef("cannon ammo mould", "Used to make cannon ammo", "", 5, 367, "items:367", false, false, 0, 0, true, false, true, 1057));
		items.add(new ItemDef("Tenti Pineapple", "The most delicious in the whole of Kharid", "", 1, 124, "items:124", false, false, 0, 0, true, true, true, 1058));
		items.add(new ItemDef("Bedobin Copy Key", "A copy of a key for the captains of the mining camps chest", "", 20, 25, "items:25", false, false, 0, 4194304, true, true, false, 1059));
		items.add(new ItemDef("Technical Plans", "Very technical looking plans for making a thrown weapon of some sort", "Read", 500, 218, "items:218", false, false, 0, 12632256, true, true, false, 1060));
		items.add(new ItemDef("Rock cake", "Yum... I think!", "eat", 0, 368, "items:368", false, false, 0, 0, true, true, true, 1061));
		items.add(new ItemDef("Bronze dart tips", "Dangerous looking dart tips - need feathers for flight", "", 1, 369, "items:369", true, false, 0, 16737817, true, false, false, 1062));
		items.add(new ItemDef("Iron dart tips", "Dangerous looking dart tips - need feathers for flight", "", 3, 369, "items:369", true, false, 0, 15654365, true, false, false, 1063));
		items.add(new ItemDef("Steel dart tips", "Dangerous looking dart tips - need feathers for flight", "", 9, 369, "items:369", true, false, 0, 15658734, true, false, false, 1064));
		items.add(new ItemDef("Mithril dart tips", "Dangerous looking dart tips - need feathers for flight", "", 25, 369, "items:369", true, false, 0, 10072780, true, false, false, 1065));
		items.add(new ItemDef("Adamantite dart tips", "Dangerous looking dart tips - need feathers for flight", "", 65, 369, "items:369", true, false, 0, 11717785, true, false, false, 1066));
		items.add(new ItemDef("Rune dart tips", "Dangerous looking dart tips - need feathers for flight", "", 350, 369, "items:369", true, false, 0, 65535, true, false, false, 1067));
		items.add(new ItemDef("Mithril Throwing Dart", "A deadly throwing dart with a mithril tip.", "", 50, 231, "items:231", true, true, 16, 10072780, true, false, false, 1068));
		items.add(new ItemDef("Adamantite Throwing Dart", "A deadly throwing dart with an adamantite tip.", "", 130, 231, "items:231", true, true, 16, 11717785, true, false, false, 1069));
		items.add(new ItemDef("Rune Throwing Dart", "A deadly throwing dart with a runite tip.", "", 700, 231, "items:231", true, true, 16, 65535, true, false, false, 1070));
		items.add(new ItemDef("Prototype dart tip", "Dangerous looking dart tip - needs feathers for flight", "", 1, 207, "items:207", true, false, 0, 16737817, true, true, false, 1071));
		items.add(new ItemDef("info document", "read to access variable choices", "read", 2, 234, "items:234", false, false, 0, 0, true, true, false, 1072));
		items.add(new ItemDef("Instruction manual", "An old note book", "read", 1, 28, "items:28", false, false, 0, 16755370, true, true, false, 1073));
		items.add(new ItemDef("Unfinished potion", "I need another ingredient to finish this potion", "", 3, 48, "items:48", false, false, 0, 6750054, true, true, true, 1074));
		items.add(new ItemDef("Iron throwing knife", "A finely balanced knife", "", 6, 80, "items:80", true, true, 16, 15654365, true, false, false, 1075));
		items.add(new ItemDef("Bronze throwing knife", "A finely balanced knife", "", 2, 80, "items:80", true, true, 16, 16737817, true, false, false, 1076));
		items.add(new ItemDef("Steel throwing knife", "A finely balanced knife", "", 21, 80, "items:80", true, true, 16, 15658734, true, false, false, 1077));
		items.add(new ItemDef("Mithril throwing knife", "A finely balanced knife", "", 54, 80, "items:80", true, true, 16, 10072780, true, false, false, 1078));
		items.add(new ItemDef("Adamantite throwing knife", "A finely balanced knife", "", 133, 80, "items:80", true, true, 16, 11717785, true, false, false, 1079));
		items.add(new ItemDef("Rune throwing knife", "A finely balanced knife", "", 333, 80, "items:80", true, true, 16, 65535, true, false, false, 1080));
		items.add(new ItemDef("Black throwing knife", "A finely balanced knife", "", 37, 80, "items:80", true, true, 16, 3158064, true, false, false, 1081));
		items.add(new ItemDef("Water Skin mostly full", "A half full skin of water", "", 27, 343, "items:343", false, false, 0, 8404992, true, false, true, 1082));
		items.add(new ItemDef("Water Skin mostly empty", "A half empty skin of water", "", 24, 343, "items:343", false, false, 0, 8404992, true, false, true, 1083));
		items.add(new ItemDef("Water Skin mouthful left", "A waterskin with a mouthful of water left", "", 18, 343, "items:343", false, false, 0, 8404992, true, false, true, 1084));
		items.add(new ItemDef("Empty Water Skin", "A completely empty waterskin", "", 15, 343, "items:343", false, false, 0, 8404992, true, false, true, 1085));
		items.add(new ItemDef("nightshade", "Deadly!", "eat", 30, 370, "items:370", false, false, 0, 0, true, true, true, 1086));
		items.add(new ItemDef("Shaman robe", "This has been left by one of the dead ogre shaman", "search", 40, 87, "items:87", false, false, 0, 10510400, true, true, false, 1087));
		items.add(new ItemDef("Iron Spear", "An iron tipped spear", "", 13, 283, "items:283", false, true, 16, 15654365, true, false, true, 1088));
		items.add(new ItemDef("Steel Spear", "A steel tipped spear", "", 46, 283, "items:283", false, true, 16, 15658734, true, false, true, 1089));
		items.add(new ItemDef("Mithril Spear", "A mithril tipped spear", "", 119, 283, "items:283", false, true, 16, 10072780, true, false, true, 1090));
		items.add(new ItemDef("Adamantite Spear", "An adamantite tipped spear", "", 293, 283, "items:283", false, true, 16, 11717785, true, false, true, 1091));
		items.add(new ItemDef("Rune Spear", "A rune tipped spear", "", 1000, 283, "items:283", false, true, 16, 56797, true, false, true, 1092));
		items.add(new ItemDef("Cat", "it's fluffs", "Stroke", 2, 338, "items:338", false, false, 0, 11184810, true, true, false, 1093));
		items.add(new ItemDef("Seasoned Sardine", "They don't smell any better", "", 10, 165, "items:165", false, false, 0, 10551200, true, false, true, 1094));
		items.add(new ItemDef("Kittens", "purrr", "", 2, 372, "items:372", false, false, 0, 11184810, true, true, false, 1095));
		items.add(new ItemDef("Kitten", "purrr", "stroke", 2, 371, "items:371", false, false, 0, 11184810, true, true, false, 1096));
		items.add(new ItemDef("Wrought iron key", "This key clears unlocks a very sturdy gate of some sort.", "", 1, 25, "items:25", false, false, 0, 14540253, true, true, false, 1097));
		items.add(new ItemDef("Cell Door Key", "A roughly hewn key", "", 1, 25, "items:25", false, false, 0, 16384, true, true, false, 1098));
		items.add(new ItemDef("A free Shantay Disclaimer", "Very important information.", "Read", 1, 218, "items:218", false, false, 0, 16711680, true, false, false, 1099));
		items.add(new ItemDef("Doogle leaves", "Small sweet smelling leaves", "", 2, 298, "items:298", false, false, 0, 0, true, false, true, 1100));
		items.add(new ItemDef("Raw Ugthanki Meat", "I need to cook this first", "", 2, 60, "items:60", false, false, 0, 16744640, true, false, true, 1101));
		items.add(new ItemDef("Tasty Ugthanki Kebab", "A fresh Kebab made from Ugthanki meat", "eat", 20, 320, "items:320", false, false, 0, 0, true, false, true, 1102));
		items.add(new ItemDef("Cooked Ugthanki Meat", "Freshly cooked Ugthanki meat", "Eat", 5, 60, "items:60", false, false, 0, 13395507, true, false, true, 1103));
		items.add(new ItemDef("Uncooked Pitta Bread", "I need to cook this.", "", 4, 152, "items:152", false, false, 0, 0, true, false, true, 1104));
		items.add(new ItemDef("Pitta Bread", "Mmmm I need to add some other ingredients yet.", "", 10, 152, "items:152", false, false, 0, 16768184, true, false, true, 1105));
		items.add(new ItemDef("Tomato Mixture", "A mixture of tomatoes in a bowl", "", 3, 162, "items:162", false, false, 0, 16711680, true, false, true, 1106));
		items.add(new ItemDef("Onion Mixture", "A mixture of onions in a bowl", "", 3, 162, "items:162", false, false, 0, 16776960, true, false, true, 1107));
		items.add(new ItemDef("Onion and Tomato Mixture", "A mixture of onions and tomatoes in a bowl", "", 3, 162, "items:162", false, false, 0, 16744512, true, false, true, 1108));
		items.add(new ItemDef("Onion and Tomato and Ugthanki Mix", "A mixture of onions and tomatoes and Ugthanki meat in a bowl", "", 3, 162, "items:162", false, false, 0, 5977890, true, false, true, 1109));
		items.add(new ItemDef("Burnt Pitta Bread", "Urgh - it's all burnt", "", 1, 152, "items:152", false, false, 0, 4194304, true, false, true, 1110));
		items.add(new ItemDef("Panning tray", "used for panning gold", "search", 1, 373, "items:373", false, false, 0, 4194304, true, true, false, 1111));
		items.add(new ItemDef("Panning tray", "this tray contains gold nuggets", "take gold", 1, 374, "items:374", false, false, 0, 4194304, true, true, false, 1112));
		items.add(new ItemDef("Panning tray", "this tray contains mud", "search", 1, 375, "items:375", false, false, 0, 4194304, true, true, false, 1113));
		items.add(new ItemDef("Rock pick", "a sharp pick for cracking rocks", "", 1, 376, "items:376", false, false, 0, 4194304, true, true, true, 1114));
		items.add(new ItemDef("Specimen brush", "stiff brush for cleaning specimens", "", 1, 377, "items:377", false, false, 0, 4194304, true, true, true, 1115));
		items.add(new ItemDef("Specimen jar", "a jar for holding soil samples", "", 1, 378, "items:378", false, false, 0, 4194304, true, true, true, 1116));
		items.add(new ItemDef("Rock Sample", "A rock sample", "", 1, 379, "items:379", false, false, 0, 4194304, true, true, false, 1117));
		items.add(new ItemDef("gold Nuggets", "Real gold pieces!", "", 1, 380, "items:380", true, false, 0, 4194304, true, true, false, 1118));
		items.add(new ItemDef("cat", "looks like a healthy one", (Config.S_WANT_EXTENDED_CATS_BEHAVIOR ? "stroke" : ""), 1, 381, "items:381", false, false, 0, 4194304, true, false, false, 1119));
		items.add(new ItemDef("Scrumpled piece of paper", "A piece of paper with barely legible writing - looks like a recipe!", "Read", 10, 218, "items:218", false, false, 0, 16317080, true, false, true, 1120));
		items.add(new ItemDef("Digsite info", "IAN ONLY", "read", 63, 382, "items:382", false, false, 0, 0, true, true, false, 1121));
		items.add(new ItemDef("Poisoned Bronze Throwing Dart", "A venomous throwing dart with a bronze tip.", "", 2, 384, "items:384", true, true, 16, 16737817, true, false, false, 1122));
		items.add(new ItemDef("Poisoned Iron Throwing Dart", "A venomous throwing dart with an iron tip.", "", 5, 384, "items:384", true, true, 16, 16737817, true, false, false, 1123));
		items.add(new ItemDef("Poisoned Steel Throwing Dart", "A venomous throwing dart with a steel tip.", "", 20, 384, "items:384", true, true, 16, 15658734, true, false, false, 1124));
		items.add(new ItemDef("Poisoned Mithril Throwing Dart", "A venomous throwing dart with a mithril tip.", "", 50, 384, "items:384", true, true, 16, 10072780, true, false, false, 1125));
		items.add(new ItemDef("Poisoned Adamantite Throwing Dart", "A venomous throwing dart with an adamantite tip.", "", 130, 384, "items:384", true, true, 16, 11717785, true, false, false, 1126));
		items.add(new ItemDef("Poisoned Rune Throwing Dart", "A deadly venomous dart with a runite tip.", "", 700, 384, "items:384", true, true, 16, 65535, true, false, false, 1127));
		items.add(new ItemDef("Poisoned Bronze throwing knife", "A finely balanced knife with a coating of venom", "", 2, 385, "items:385", true, true, 16, 16737817, true, false, false, 1128));
		items.add(new ItemDef("Poisoned Iron throwing knife", "A finely balanced knife with a coating of venom", "", 6, 385, "items:385", true, true, 16, 15654365, true, false, false, 1129));
		items.add(new ItemDef("Poisoned Steel throwing knife", "A finely balanced knife with a coating of venom", "", 21, 385, "items:385", true, true, 16, 15658734, true, false, false, 1130));
		items.add(new ItemDef("Poisoned Mithril throwing knife", "A finely balanced knife with a coating of venom", "", 54, 385, "items:385", true, true, 16, 10072780, true, false, false, 1131));
		items.add(new ItemDef("Poisoned Black throwing knife", "A finely balanced knife with a coating of venom", "", 37, 385, "items:385", true, true, 16, 3158064, true, false, false, 1132));
		items.add(new ItemDef("Poisoned Adamantite throwing knife", "A finely balanced knife with a coating of venom", "", 133, 385, "items:385", true, true, 16, 11717785, true, false, false, 1133));
		items.add(new ItemDef("Poisoned Rune throwing knife", "A finely balanced knife with a coating of venom", "", 333, 385, "items:385", true, true, 16, 65535, true, false, false, 1134));
		items.add(new ItemDef("Poisoned Bronze Spear", "A bronze tipped spear with added venom ", "", 4, 383, "items:383", false, true, 16, 16737817, true, false, true, 1135));
		items.add(new ItemDef("Poisoned Iron Spear", "An iron tipped spear with added venom", "", 13, 383, "items:383", false, true, 16, 15654365, true, false, true, 1136));
		items.add(new ItemDef("Poisoned Steel Spear", "A steel tipped spear with added venom", "", 46, 383, "items:383", false, true, 16, 15658734, true, false, true, 1137));
		items.add(new ItemDef("Poisoned Mithril Spear", "A mithril tipped spear with added venom", "", 119, 383, "items:383", false, true, 16, 10072780, true, false, true, 1138));
		items.add(new ItemDef("Poisoned Adamantite Spear", "An adamantite tipped spear with added venom", "", 293, 383, "items:383", false, true, 16, 11717785, true, false, true, 1139));
		items.add(new ItemDef("Poisoned Rune Spear", "A rune tipped spear with added venom", "", 1000, 383, "items:383", false, true, 16, 56797, true, false, true, 1140));
		items.add(new ItemDef("Book of experimental chemistry", "A book on experiments with volatile chemicals", "read", 1, 28, "items:28", false, false, 0, 16755370, true, true, false, 1141));
		items.add(new ItemDef("Level 1 Certificate", "A Certificate of education", "read", 1, 29, "items:29", false, false, 0, 0, true, true, false, 1142));
		items.add(new ItemDef("Level 2 Certificate", "A Certificate of education", "read", 1, 29, "items:29", false, false, 0, 0, true, true, false, 1143));
		items.add(new ItemDef("Level 3 Certificate", "A Certificate of education", "read", 1, 29, "items:29", false, false, 0, 0, true, true, false, 1144));
		items.add(new ItemDef("Trowel", "A small device for digging", "", 1, 386, "items:386", false, false, 0, 0, true, true, true, 1145));
		items.add(new ItemDef("Stamped letter of recommendation", "A stamped scroll with a recommendation on it", "", 1, 402, "items:402", false, false, 0, 0, true, true, false, 1146));
		items.add(new ItemDef("Unstamped letter of recommendation", "I hereby recommend this student to undertake the Varrock City earth sciences exams", "", 5, 29, "items:29", false, false, 0, 0, true, true, false, 1147));
		items.add(new ItemDef("Rock Sample", "A rock sample", "", 1, 388, "items:388", false, false, 0, 4194304, true, true, false, 1148));
		items.add(new ItemDef("Rock Sample", "A rock sample", "", 1, 389, "items:389", false, false, 0, 4194304, true, true, false, 1149));
		items.add(new ItemDef("Cracked rock Sample", "It's been cracked open", "", 1, 387, "items:387", false, false, 0, 4194304, true, true, false, 1150));
		items.add(new ItemDef("Belt buckle", "been here some time", "", 1, 390, "items:390", false, false, 0, 4194304, true, true, false, 1151));
		items.add(new ItemDef("Powering crystal2", "An intricately cut gemstone", "", 0, 357, "items:357", false, false, 0, 16738047, true, true, false, 1152));
		items.add(new ItemDef("Powering crystal3", "An intricately cut gemstone", "", 0, 357, "items:357", false, false, 0, 6750207, true, true, false, 1153));
		items.add(new ItemDef("Powering crystal4", "An intricately cut gemstone", "", 0, 357, "items:357", false, false, 0, 3407667, true, true, false, 1154));
		items.add(new ItemDef("Old boot", "that's been here some time", "", 1, 391, "items:391", false, false, 0, 4194304, true, true, false, 1155));
		items.add(new ItemDef("Bunny ears", "Get another from the clothes shop if you die", "", 1, 392, "items:392", false, true, 32, 4194304, false, true, true, 1156));
		items.add(new ItemDef("Damaged armour", "that's been here some time", "", 1, 393, "items:393", false, false, 0, 4194304, true, true, false, 1157));
		items.add(new ItemDef("Damaged armour", "that's been here some time", "", 1, 394, "items:394", false, false, 0, 4194304, true, true, false, 1158));
		items.add(new ItemDef("Rusty sword", "that's been here some time", "", 1, 395, "items:395", false, false, 0, 4194304, true, true, false, 1159));
		items.add(new ItemDef("Ammonium Nitrate", "An acrid chemical", "", 20, 23, "items:23", false, false, 0, 16777164, true, true, false, 1160));
		items.add(new ItemDef("Nitroglycerin", "A strong acidic formula", "", 2, 48, "items:48", false, false, 0, 16750848, true, true, false, 1161));
		items.add(new ItemDef("Old tooth", "a large single tooth", "", 0, 360, "items:360", false, false, 0, 0, true, true, false, 1162));
		items.add(new ItemDef("Radimus Scrolls", "Scrolls that Radimus gave you", "Read Scrolls", 5, 29, "items:29", false, false, 0, 8421504, true, true, false, 1163));
		items.add(new ItemDef("chest key", "A small key for a chest", "", 1, 25, "items:25", false, false, 0, 16763904, true, true, false, 1164));
		items.add(new ItemDef("broken arrow", "that's been here some time", "", 1, 396, "items:396", false, false, 0, 4194304, true, true, false, 1165));
		items.add(new ItemDef("buttons", "they've been here some time", "", 1, 397, "items:397", false, false, 0, 4194304, true, true, false, 1166));
		items.add(new ItemDef("broken staff", "that's been here some time", "", 1, 398, "items:398", false, false, 0, 4194304, true, true, false, 1167));
		items.add(new ItemDef("vase", "An old vase", "", 1, 279, "items:279", false, false, 0, 0, true, true, false, 1168));
		items.add(new ItemDef("ceramic remains", "some ancient pottery", "", 1, 399, "items:399", false, false, 0, 4194304, true, true, false, 1169));
		items.add(new ItemDef("Broken glass", "smashed glass", "", 0, 259, "items:259", false, false, 0, 0, true, true, false, 1170));
		items.add(new ItemDef("Unidentified powder", "who knows what this is for?", "", 20, 23, "items:23", false, false, 0, 16777164, true, true, false, 1171));
		items.add(new ItemDef("Machette", "A purpose built tool for cutting through thick jungle.", "", 40, 432, "items:432", false, true, 16, 8421504, true, false, true, 1172));
		items.add(new ItemDef("Scroll", "A letter written by the expert", "read", 5, 29, "items:29", false, false, 0, 0, true, true, false, 1173));
		items.add(new ItemDef("stone tablet", "some ancient script is engraved on here", "read", 1, 400, "items:400", false, false, 0, 4194304, true, true, false, 1174));
		items.add(new ItemDef("Talisman of Zaros", "an ancient item", "", 1, 401, "items:401", false, false, 0, 4194304, true, true, false, 1175));
		items.add(new ItemDef("Explosive compound", "A dark mystical powder", "", 2, 48, "items:48", false, false, 0, 51, true, true, false, 1176));
		items.add(new ItemDef("Bull Roarer", "A sound producing instrument - it may attract attention", "Swing", 1, 418, "items:418", false, false, 0, 7552262, true, true, false, 1177));
		items.add(new ItemDef("Mixed chemicals", "A pungent mix of 2 chemicals", "", 2, 48, "items:48", false, false, 0, 16777113, true, true, false, 1178));
		items.add(new ItemDef("Ground charcoal", "Powdered charcoal!", "", 20, 23, "items:23", false, false, 0, 2236962, true, false, true, 1179));
		items.add(new ItemDef("Mixed chemicals", "A pungent mix of 3 chemicals", "", 2, 48, "items:48", false, false, 0, 13408512, true, true, false, 1180));
		items.add(new ItemDef("Spell scroll", "A magical scroll", "read", 5, 29, "items:29", false, false, 0, 0, true, true, false, 1181));
		items.add(new ItemDef("Yommi tree seed", "A magical seed that grows into a Yommi tree - these need to be germinated", "Inspect", 200, 270, "items:270", true, false, 0, 65280, true, true, false, 1182));
		items.add(new ItemDef("Totem Pole", "A well crafted totem pole", "", 500, 403, "items:403", false, false, 0, 65280, true, true, false, 1183));
		items.add(new ItemDef("Dwarf cannon base", "bang", "set down", 200000, 352, "items:352", false, false, 0, 0, true, false, true, 1184));
		items.add(new ItemDef("Dwarf cannon stand", "bang", "", 200000, 353, "items:353", false, false, 0, 0, true, false, true, 1185));
		items.add(new ItemDef("Dwarf cannon barrels", "bang", "", 200000, 354, "items:354", false, false, 0, 0, true, false, true, 1186));
		items.add(new ItemDef("Dwarf cannon furnace", "bang", "", 150000, 355, "items:355", false, false, 0, 0, true, false, true, 1187));
		items.add(new ItemDef("Golden Bowl", "A specially made bowl constructed out of pure gold", "", 1000, 404, "items:404", false, false, 0, 0, true, true, true, 1188));
		items.add(new ItemDef("Golden Bowl with pure water", "A golden bowl filled with pure water", "", 1000, 405, "items:405", false, false, 0, 8454143, true, true, true, 1189));
		items.add(new ItemDef("Raw Manta ray", "A rare catch!", "", 500, 406, "items:406", false, false, 0, 255, true, false, true, 1190));
		items.add(new ItemDef("Manta ray", "A rare catch!", "eat", 500, 407, "items:407", false, false, 0, 255, true, false, true, 1191));
		items.add(new ItemDef("Raw Sea turtle", "A rare catch!", "", 500, 408, "items:408", false, false, 0, 255, true, false, true, 1192));
		items.add(new ItemDef("Sea turtle", "Tasty!", "eat", 500, 409, "items:409", false, false, 0, 255, true, false, true, 1193));
		items.add(new ItemDef("Annas Silver Necklace", "A necklace coated with silver", "", 1, 24, "items:24", false, true, 1024, 0, true, true, false, 1194));
		items.add(new ItemDef("Bobs Silver Teacup", "A tea cup coated with silver", "", 1, 227, "items:227", false, false, 0, 0, true, true, false, 1195));
		items.add(new ItemDef("Carols Silver Bottle", "A little bottle coated with silver", "", 1, 104, "items:104", false, false, 0, 0, true, true, false, 1196));
		items.add(new ItemDef("Davids Silver Book", "An ornamental book coated with silver", "", 1, 28, "items:28", false, false, 0, 0, true, true, false, 1197));
		items.add(new ItemDef("Elizabeths Silver Needle", "An ornamental needle coated with silver", "", 1, 38, "items:38", false, false, 0, 0, true, true, false, 1198));
		items.add(new ItemDef("Franks Silver Pot", "A small pot coated with silver", "", 1, 61, "items:61", false, false, 0, 0, true, true, false, 1199));
		items.add(new ItemDef("Thread", "A piece of red thread discovered at the scene of the crime", "", 1, 208, "items:208", false, false, 0, 16711680, true, true, false, 1200));
		items.add(new ItemDef("Thread", "A piece of green thread discovered at the scene of the crime", "", 1, 208, "items:208", false, false, 0, 65280, true, true, false, 1201));
		items.add(new ItemDef("Thread", "A piece of blue thread discovered at the scene of the crime", "", 1, 208, "items:208", false, false, 0, 255, true, true, false, 1202));
		items.add(new ItemDef("Flypaper", "Sticky paper for catching flies", "", 1, 415, "items:415", false, false, 0, 14540253, true, true, false, 1203));
		items.add(new ItemDef("Murder Scene Pot", "The pot has a sickly smell of poison mixed with wine", "", 1, 61, "items:61", false, false, 0, 16711680, true, true, false, 1204));
		items.add(new ItemDef("A Silver Dagger", "Dagger Found at crime scene", "", 1, 80, "items:80", false, true, 16, 0, true, true, false, 1205));
		items.add(new ItemDef("Murderers fingerprint", "An impression of the murderers fingerprint", "", 1, 416, "items:416", false, false, 0, 14540253, true, true, false, 1206));
		items.add(new ItemDef("Annas fingerprint", "An impression of Annas fingerprint", "", 1, 416, "items:416", false, false, 0, 14540253, true, true, false, 1207));
		items.add(new ItemDef("Bobs fingerprint", "An impression of Bobs fingerprint", "", 1, 416, "items:416", false, false, 0, 14540253, true, true, false, 1208));
		items.add(new ItemDef("Carols fingerprint", "An impression of Carols fingerprint", "", 1, 416, "items:416", false, false, 0, 14540253, true, true, false, 1209));
		items.add(new ItemDef("Davids fingerprint", "An impression of Davids fingerprint", "", 1, 416, "items:416", false, false, 0, 14540253, true, true, false, 1210));
		items.add(new ItemDef("Elizabeths fingerprint", "An impression of Elizabeths fingerprint", "", 1, 416, "items:416", false, false, 0, 14540253, true, true, false, 1211));
		items.add(new ItemDef("Franks fingerprint", "An impression of Franks fingerprint", "", 1, 416, "items:416", false, false, 0, 14540253, true, true, false, 1212));
		items.add(new ItemDef("Zamorak Cape", "A cape from the almighty zamorak", "", 100, Config.S_WANT_CUSTOM_SPRITES ? 553 : 59, Config.S_WANT_CUSTOM_SPRITES ? "items:553" : "items:59", false, true, 2048, 16711680, true, true, true, 1213));
		items.add(new ItemDef("Saradomin Cape", "A cape from the almighty saradomin", "", 100, Config.S_WANT_CUSTOM_SPRITES ? 552 : 59, Config.S_WANT_CUSTOM_SPRITES ? "items:552" : "items:59", false, true, 2048, 4210926, true, true, true, 1214));
		items.add(new ItemDef("Guthix Cape", "A cape from the almighty guthix", "", 100, Config.S_WANT_CUSTOM_SPRITES ? 551 : 59, Config.S_WANT_CUSTOM_SPRITES ? "items:551" : "items:59", false, true, 2048, 4246592, true, true, true, 1215));
		items.add(new ItemDef("Staff of zamorak", "It's a stick of the gods", "", 80000, 337, "items:337", false, true, 16, 0, true, true, true, 1216));
		items.add(new ItemDef("Staff of guthix", "It's a stick of the gods", "", 80000, 85, "items:85", false, true, 16, 10072780, true, true, true, 1217));
		items.add(new ItemDef("Staff of Saradomin", "It's a stick of the gods", "", 80000, 414, "items:414", false, true, 16, 10072780, true, true, true, 1218));
		items.add(new ItemDef("A chunk of crystal", "A reddish crystal fragment - it looks like it formed a shape at one time.", "", 2000, 410, "items:410", false, false, 0, 0, true, true, false, 1219));
		items.add(new ItemDef("A lump of crystal", "A reddish crystal fragment - it looks like it formed a shape at one time.", "", 2000, 411, "items:411", false, false, 0, 0, true, true, false, 1220));
		items.add(new ItemDef("A hunk of crystal", "A reddish crystal fragment - it looks like it formed a shape at one time.", "", 2000, 412, "items:412", false, false, 0, 0, true, true, false, 1221));
		items.add(new ItemDef("A red crystal", "A heart shaped red crystal ", "Inspect", 2000, 413, "items:413", false, false, 0, 0, true, true, false, 1222));
		items.add(new ItemDef("Unidentified fingerprint", "An impression of the murderers fingerprint", "", 1, 416, "items:416", false, false, 0, 14540253, true, true, false, 1223));
		items.add(new ItemDef("Annas Silver Necklace", "A silver necklace coated with flour", "", 1, 24, "items:24", false, true, 1024, 0, true, true, false, 1224));
		items.add(new ItemDef("Bobs Silver Teacup", "A silver tea cup coated with flour", "", 1, 227, "items:227", false, false, 0, 0, true, true, false, 1225));
		items.add(new ItemDef("Carols Silver Bottle", "A little silver bottle coated with flour", "", 1, 104, "items:104", false, false, 0, 0, true, true, false, 1226));
		items.add(new ItemDef("Davids Silver Book", "An ornamental silver book coated with flour", "", 1, 28, "items:28", false, false, 0, 0, true, true, false, 1227));
		items.add(new ItemDef("Elizabeths Silver Needle", "An ornamental silver needle coated with flour", "", 1, 38, "items:38", false, false, 0, 0, true, true, false, 1228));
		items.add(new ItemDef("Franks Silver Pot", "A small silver pot coated with flour", "", 1, 61, "items:61", false, false, 0, 0, true, true, false, 1229));
		items.add(new ItemDef("A Silver Dagger", "Dagger Found at crime scene coated with flour", "", 1, 80, "items:80", false, true, 16, 0, true, true, false, 1230));
		items.add(new ItemDef("A glowing red crystal", "A glowing heart shaped red crystal - great magic must be present in this item", "", 2000, 419, "items:419", false, false, 0, 0, true, true, false, 1231));
		items.add(new ItemDef("Unidentified liquid", "A strong acidic formula", "", 2, 48, "items:48", false, false, 0, 16750848, true, true, false, 1232));
		items.add(new ItemDef("Radimus Scrolls", "Mission briefing and the completed map of Karamja - Sir Radimus will be pleased...", "Read Scrolls", 5, 29, "items:29", false, false, 0, 8421504, true, true, false, 1233));
		items.add(new ItemDef("Robe", "A worn robe", "", 15, 87, "items:87", false, true, 64, 255, true, true, false, 1234));
		items.add(new ItemDef("Armour", "An unusually red armour", "", 40, 118, "items:118", false, false, 0, 13369344, true, true, false, 1235));
		items.add(new ItemDef("Dagger", "Short but pointy", "", 35, 80, "items:80", false, true, 16, 15654365, true, true, false, 1236));
		items.add(new ItemDef("eye patch", "It makes me look very piratical", "", 2, 198, "items:198", false, true, 32, 0, true, true, false, 1237));
		items.add(new ItemDef("Booking of Binding", "An ancient tome on Demonology", "read", 1, 28, "items:28", false, false, 0, 15641258, true, true, false, 1238));
		items.add(new ItemDef("Holy Water Vial", "A deadly potion against evil kin", "Throw", 3, 48, "items:48", false, true, 16, 10073782, true, true, true, 1239));
		items.add(new ItemDef("Enchanted Vial", "This enchanted vial is empty - but is ready for magical liquids.", "", 200, 144, "items:144", false, false, 0, 16646109, true, true, true, 1240));
		items.add(new ItemDef("Scribbled notes", "It looks like a page ripped from a book", "Read", 20, 427, "items:427", false, false, 0, 8421376, true, true, false, 1241));
		items.add(new ItemDef("Scrawled notes", "It looks like a page ripped from a book", "Read", 20, 427, "items:427", false, false, 0, 14066524, true, true, false, 1242));
		items.add(new ItemDef("Scatched notes", "It looks like a page ripped from a book", "Read", 20, 427, "items:427", false, false, 0, 11909701, true, true, false, 1243));
		items.add(new ItemDef("Shamans Tome", "An ancient tome on various subjects...", "read", 1, 299, "items:299", false, false, 0, 15641258, true, true, false, 1244));
		items.add(new ItemDef("Edible seaweed", "slightly damp seaweed", "eat", 2, 203, "items:203", false, false, 0, 0, true, false, true, 1245));
		items.add(new ItemDef("Rough Sketch of a bowl", "A roughly sketched picture of a bowl made from metal", "Read", 5, 29, "items:29", false, false, 0, 0, true, true, false, 1246));
		items.add(new ItemDef("Burnt Manta ray", "oops!", "", 500, 430, "items:430", false, false, 0, 255, true, false, true, 1247));
		items.add(new ItemDef("Burnt Sea turtle", "oops!", "", 500, 431, "items:431", false, false, 0, 255, true, false, true, 1248));
		items.add(new ItemDef("Cut reed plant", "A narrow long tube - it might be useful for something", "", 2, 202, "items:202", false, false, 0, 65280, true, false, true, 1249));
		items.add(new ItemDef("Magical Fire Pass", "A pass which allows you to cross the flaming walls into the Flaming Octagon", "", 1, 29, "items:29", false, false, 0, 16711680, true, true, false, 1250));
		items.add(new ItemDef("Snakes Weed Solution", "Snakes weed in water - part of a potion", "", 1, 48, "items:48", false, false, 0, 8454016, true, true, true, 1251));
		items.add(new ItemDef("Ardrigal Solution", "Ardrigal herb in water - part of a potion", "", 1, 48, "items:48", false, false, 0, 8388608, true, true, true, 1252));
		items.add(new ItemDef("Gujuo Potion", "A potion to help against fear of the supernatural", "Drink", 1, 48, "items:48", false, false, 0, 8405056, true, true, true, 1253));
		items.add(new ItemDef("Germinated Yommi tree seed", "A magical seed that grows into a Yommi tree - these have been germinated.", "Inspect", 200, 270, "items:270", true, false, 0, 65280, true, true, false, 1254));
		items.add(new ItemDef("Dark Dagger", "An unusual looking dagger made of dark shiny obsidian", "", 91, 420, "items:420", false, true, 16, 0, true, true, false, 1255));
		items.add(new ItemDef("Glowing Dark Dagger", "An unusual looking dagger made of dark shiny obsidian - it has an unnatural glow .", "", 91, 421, "items:421", false, true, 16, 0, true, true, false, 1256));
		items.add(new ItemDef("Holy Force Spell", "A powerful incantation - it affects spirits of the underworld", "Cast", 1, 423, "items:423", false, false, 0, 0, true, true, false, 1257));
		items.add(new ItemDef("Iron Pickaxe", "Used for mining", "", 140, 72, "items:72", false, false, 0, 15654365, false, false, true, 1258));
		items.add(new ItemDef("Steel Pickaxe", "Requires level 6 mining to use", "", 500, 72, "items:72", false, false, 0, 15658734, false, false, true, 1259));
		items.add(new ItemDef("Mithril Pickaxe", "Requires level 21 mining to use", "", 1300, 72, "items:72", false, false, 0, 10072780, false, false, true, 1260));
		items.add(new ItemDef("Adamantite Pickaxe", "Requires level 31 mining to use", "", 3200, 72, "items:72", false, false, 0, 11717785, false, false, true, 1261));
		items.add(new ItemDef("Rune Pickaxe", "Requires level 41 mining to use", "", 32000, 72, "items:72", false, false, 0, 65535, false, false, true, 1262));
		items.add(new ItemDef("Sleeping Bag", "Not as comfy as a bed but better than nothing", "sleep", 30, 422, "items:422", false, false, 0, 0, false, false, true, 1263));
		items.add(new ItemDef("Stone", "A mined stone used to craft runes at altars.", "", 4, 443, "items:443", false, false, 0, 0, false, false, true, 1264));
		items.add(new ItemDef("Gilded Totem Pole", "A well crafted totem pole - given to you as a gift from Gujuo", "Inspect", 20, 403, "items:403", false, false, 0, 65280, true, true, false, 1265));
		items.add(new ItemDef("Blessed Golden Bowl", "A specially made bowl constructed out of pure gold - it looks magical somehow", "", 1000, 404, "items:404", false, false, 0, 0, true, true, true, 1266));
		items.add(new ItemDef("Blessed Golden Bowl with Pure Water", "A golden bowl filled with pure water - it looks magical somehow", "", 1000, 405, "items:405", false, false, 0, 8454143, true, true, true, 1267));
		items.add(new ItemDef("Raw Oomlie Meat", "Raw meat from the Oomlie bird", "", 10, 60, "items:60", false, false, 0, 16747571, true, false, true, 1268));
		items.add(new ItemDef("Cooked Oomlie meat Parcel", "Deliciously cooked Oomlie meat in a palm leaf pouch.", "eat", 35, 433, "items:433", false, false, 0, 13395507, true, false, true, 1269));
		items.add(new ItemDef("Dragon Bone Certificate", "Each certificate exchangable at Yanille for 5 Dragon Bones", "", 10, 180, "items:180", true, false, 0, 0, true, false, false, 1270));
		items.add(new ItemDef("Limpwurt Root Certificate", "Each certificate exchangable at Yanille for 5 Limpwort roots", "", 10, 180, "items:180", true, false, 0, 16384, true, false, false, 1271));
		items.add(new ItemDef("Prayer Potion Certificate", "Each certificate exchangable at Yanille for 5 prayer potions", "", 10, 180, "items:180", true, false, 0, 3206809, true, false, false, 1272));
		items.add(new ItemDef("Super Attack Potion Certificate", "Exchangable at Yanille for 5", "", 10, 180, "items:180", true, false, 0, 3158254, true, false, false, 1273));
		items.add(new ItemDef("Super Defense Potion Certificate", "Exchangable at Yanille for 5", "", 10, 180, "items:180", true, false, 0, 15644208, true, false, false, 1274));
		items.add(new ItemDef("Super Strength Potion Certificate", "Exchangable at Yanille for 5", "", 10, 180, "items:180", true, false, 0, 15658734, true, false, false, 1275));
		items.add(new ItemDef("Half Dragon Square Shield", "The Right Half of an ancient and powerful looking Dragon Square shield.", "", 500000, 425, "items:425", false, false, 0, 15654365, true, false, true, 1276));
		items.add(new ItemDef("Half Dragon Square Shield", "Left Half of an ancient and powerful looking Dragon Square shield.", "", 110000, 424, "items:424", false, false, 0, 15654365, true, false, true, 1277));
		items.add(new ItemDef("Dragon Square Shield", "An ancient and powerful looking Dragon Square shield.", "", 500000, 426, "items:426", false, true, 8, 13500416, true, false, true, 1278));
		items.add(new ItemDef("Palm tree leaf", "A thick green plam leaf - natives use this to cook meat in", "", 5, 428, "items:428", false, false, 0, 0, true, false, true, 1279));
		items.add(new ItemDef("Raw Oomlie Meat Parcel", "Oomlie meat in a palm leaf pouch - just needs to be cooked.", "", 16, 429, "items:429", false, false, 0, 16747571, true, false, true, 1280));
		items.add(new ItemDef("Burnt Oomlie Meat parcel", "Oomlie meat in a palm leaf pouch - it's burnt.", "", 1, 429, "items:429", false, false, 0, 4194304, true, false, true, 1281));
		items.add(new ItemDef("Bailing Bucket", "It's a water tight bucket", "bail with ", 10, 22, "items:22", false, false, 0, 1052688, true, false, true, 1282));
		items.add(new ItemDef("Plank", "Damaged remains of the ship", "", 1, 135, "items:135", false, false, 0, 0, true, false, true, 1283));
		items.add(new ItemDef("Arcenia root", "the root of an arcenia plant", "", 7, 101, "items:101", false, false, 0, 0, true, true, false, 1284));
		items.add(new ItemDef("display tea", "A nice cup of tea - for display only", "", 10, 227, "items:227", false, false, 0, 0, true, false, true, 1285));
		items.add(new ItemDef("Blessed Golden Bowl with plain water", "A golden bowl filled with plain water", "Empty", 1000, 405, "items:405", false, false, 0, 8454143, true, true, true, 1286));
		items.add(new ItemDef("Golden Bowl with plain water", "A golden bowl filled with plain water", "Empty", 1000, 405, "items:405", false, false, 0, 8454143, true, true, true, 1287));
		items.add(new ItemDef("Cape of legends", "Shows I am a member of the legends guild", "", 450, 59, "items:59", false, true, 2048, 16777215, true, true, true, 1288));
		items.add(new ItemDef("Scythe", "Get another from the clothes shop if you die", "", 15, 434, "items:434", false, true, 8216, 0, false, true, true, 1289));
		//loadNoteDefinitions();

		//Load custom sprites
		//if (Config.S_WANT_CUSTOM_SPRITES)
		//TODO: Maybe it should only load if WANT_CUSTOM_SPRITES but client needs to handle item ID out of bounds better if that's enabled.
		loadCustomItemDefinitions();

		if (Config.S_SHOW_UNIDENTIFIED_HERB_NAMES) {
			items.get(165).name = "Unidentified Guam";
			items.get(435).name = "Unidentified Marrentill";
			items.get(436).name = "Unidentified Tarromin";
			items.get(437).name = "Unidentified Harralander";
			items.get(438).name = "Unidentified Ranarr Weed";
			items.get(439).name = "Unidentified Irit Leaf";
			items.get(440).name = "Unidentified Avantoe";
			items.get(441).name = "Unidentified Kwuarm";
			items.get(442).name = "Unidentified Cadantine";
			items.get(443).name = "Unidentified Dwarf Weed";
			items.get(815).name = "Unidentified Snake Weed";
			items.get(817).name = "Unidentified Ardrigal";
			items.get(819).name = "Unidentified Sito Foil";
			items.get(821).name = "Unidentified Volencia Moss";
			items.get(823).name = "Unidentified Rogues Purse";
			items.get(933).name = "Unidentified Torstol";

			// apply also for potions
			items.get(454).name = "Unfinished Guam potion";
			items.get(455).name = "Unfinished Marrentill potion";
			items.get(456).name = "Unfinished Tarromin potion";
			items.get(457).name = "Unfinished Harralander potion";
			items.get(458).name = "Unfinished Ranarr potion";
			items.get(459).name = "Unfinished Irit potion";
			items.get(460).name = "Unfinished Avantoe potion";
			items.get(461).name = "Unfinished Kwuarm potion";
			items.get(462).name = "Unfinished Cadantine potion";
			items.get(463).name = "Unfinished Dwarf Weed potion";
			items.get(935).name = "Unfinished Torstol potion";
			items.get(1052).name = "Unfinished Ogre potion";
			items.get(1074).name = "Unfinished Jangerberries potion";
		}

		if (Config.S_WANT_BANK_NOTES && !Config.S_WANT_CERT_AS_NOTES) {
			// notes themed as certificates, "old" certs change name
			int oldCertids[] = { 517, 518, 519, 520, 521, 528, 529, 530, 531, 532, 533, 534, 535, 536, 628, 629, 630, 631, 711, 712, 713, 1270, 1271, 1272, 1273, 1274, 1275 };
			for (int certId : oldCertids) {
				items.get(certId).name = items.get(certId).name + " (market)";
			}
		}

		/*try {
		PrintWriter printWriter = new PrintWriter(new FileOutputStream("newItemDef.txt"), true);


		/*for(ItemDef item : items) {
			if(item.id <= 1289) {
				printWriter.println("UPDATE `itemdef` SET `bankNoteID`=" + item.getNotedForm() + ",`originalItemID`='-1' WHERE id='" + item.id + "';");
				printWriter.flush();
			}
			else if(item.id >= 0) {
				printWriter.println("UPDATE `itemdef` SET `name`='" + item.getName().replace("'", "''") + "',`description`='" + item.getDescription().replace("'", "''") + "', " + (item.getCommand().isEmpty() ? "" : "`command`='" + item.getCommand() + "',") + "`isStackable`=" + (item.isStackable() ? "'1'" : "'0'") + ",`isUntradable`=" + (item.untradeable ? "'1'" : "'0'") + ",`isWearable`=" + (item.isWieldable() ? "'1'" : "'0'") + ",`wearableID`='" + item.wearableID + "',`basePrice`='" + item.getBasePrice() + "',`isMembersOnly`=" + (item.membersItem ? "'1'" : "'0'") + " WHERE id='" + item.id + "';");
				printWriter.flush();
			}
			else {
				printWriter.println("INSERT INTO `itemdef`(`id`, `bankNoteID`, `originalItemID`, `name`, `description`, `command`, `isFemaleOnly`, `isMembersOnly`, `isStackable`, `isUntradable`, `isWearable`, `appearanceID`, `wearableID`, `wearSlot`, `requiredLevel`, `requiredSkillID`, `armourBonus`, `weaponAimBonus`, `weaponPowerBonus`, `magicBonus`, `prayerBonus`, `basePrice`)"
						+ " VALUES (\""+ item.id +"\", \"-1\", \"" + item.getNotedFormOf() + "\", \"" + item.getName() + "\" ,\""+ item.getDescription()+"\",\""+ item.getCommand() +"\",\"0\",\"" + (item.membersItem ? 1 : 0) + "\",\""+ (item.isStackable() ? 1 : 0) +"\", \"0\", \"0\",\"0\",\"0\",\"0\",\"0\",\"0\",\"0\",\"0\",\"0\",\"0\",\"0\",\"" + item.basePrice + "\");");
				printWriter.flush();
			}
		}

		for(ItemDef item : (ArrayList<ItemDef>) items.clone()) {
			if(item.isStackable() || item.quest) {
				continue;
			}
			items.add(new ItemDef(itemCount(), item));
		}
		for(ItemDef item : items) {
			if(item.getNotedFormOf() >= 0) {
				ItemDef original = getItemDef(item.getNotedFormOf());
				original.setNotedForm(item.id);
			}
		}
			for(ItemDef item : items) {
				printWriter.println("items.add(new ItemDef(\""+ item.getName() +"\",\"" + item.getDescription() + "\", \"" + item.command + "\", " + item.getBasePrice() + ", " + item.getSprite() + ", " + item.stackable + ", "+ item.wieldable +", "+ item.wearableID +", "+ item.getPictureMask()+", "+ item.membersItem+", "+item.quest+", " + item.getNotedForm() +", " + item.getNotedFormOf() + ", " + item.id +"));");
				printWriter.flush();
			}
		printWriter.close();
	} catch (FileNotFoundException e) {
		e.printStackTrace();
	}*/

	}

	private static void loadCustomItemDefinitions() {
		/**
		 * These are the custom item definitions.
		 * ------------------------------------------------------------------------------
		 * After id 1289 - Scythe from OG RSC.
		 */

		addCustomWoodItemDefinitions();

		// Ironman
		items.add(new ItemDef("Ironman helm", "For just a rather very independent scaper.", "", 154, -1, "items:6", false, true, 33, 11189164, false, true, true, 1290));
		items.add(new ItemDef("Ironman platebody", "Take it off and what are you?", "", 560, -1, "items:8", false, true, 322, 11189164, false, true, true, 1291));
		items.add(new ItemDef("Ironman platelegs", "Take it off and what are you?", "", 280, -1, "items:9", false, true, 644, 11189164, false, true, true, 1292));

		// Ultimate ironman
		items.add(new ItemDef("Ultimate ironman helm", "For Just A Rather Very Independent Scaper.", "", 154, -1, "items:6", false, true, 33, 16768685, false, true, true, 1293));
		items.add(new ItemDef("Ultimate ironman platebody", "Take it off and what are you?", "", 560, -1, "items:8", false, true, 322, 16768685, false, true, true, 1294));
		items.add(new ItemDef("Ultimate ironman platelegs", "Take it off and what are you?", "", 280, -1, "items:9", false, true, 644, 16768685, false, true, true, 1295));

		// Hardcore ironman
		items.add(new ItemDef("Hardcore ironman helm", "For those who stand alone.", "", 154, -1, "items:6", false, true, 33, 10027084, false, true, true, 1296));
		items.add(new ItemDef("Hardcore ironman platebody", "Take it off and what are you?", "", 560, -1, "items:8", false, true, 322, 10027084, false, true, true, 1297));
		items.add(new ItemDef("Hardcore ironman platelegs", "Take it off and what are you?", "", 280, -1, "items:9", false, true, 644, 10027084, false, true, true, 1298));



		// Runecraft
		items.add(new ItemDef("Rune stone", "An uncharged runestone", "", 4, -1, "items:443", false, false, 0, 0, false, false, true, 1299));
		items.add(new ItemDef("Air talisman", "A mysterious power emanates from the talisman...", "Locate", 40, -1, "items:445", false, false, 0, 0, false, false, true, 1300));
		items.add(new ItemDef("Mind talisman", "A mysterious power emanates from the talisman...", "Locate", 30, -1, "items:464", false, false, 0, 0, false, false, true, 1301));
		items.add(new ItemDef("Water talisman", "A mysterious power emanates from the talisman...", "Locate", 40, -1, "items:446", false, false, 0, 0, false, false, true, 1302));
		items.add(new ItemDef("Earth talisman", "A mysterious power emanates from the talisman...", "Locate", 40, -1, "items:448", false, false, 0, 0, false, false, true, 1303));
		items.add(new ItemDef("Fire talisman", "A mysterious power emanates from the talisman...", "Locate", 40, -1, "items:447", false, false, 0, 0, false, false, true, 1304));
		items.add(new ItemDef("Body talisman", "A mysterious power emanates from the talisman...", "Locate", 30, -1, "items:444", false, false, 0, 0, false, false, true, 1305));
		items.add(new ItemDef("Cosmic talisman", "A mysterious power emanates from the talisman...", "Locate", 150, -1, "items:451", false, false, 0, 0, false, false, true, 1306));
		items.add(new ItemDef("Chaos talisman", "A mysterious power emanates from the talisman...", "Locate", 100, -1, "items:452", false, false, 0, 0, false, false, true, 1307));
		items.add(new ItemDef("Nature talisman", "A mysterious power emanates from the talisman...", "Locate", 70, -1, "items:449", false, false, 0, 0, false, false, true, 1308));
		items.add(new ItemDef("Law talisman", "A mysterious power emanates from the talisman...", "Locate", 120, -1, "items:450", false, false, 0, 0, false, false, true, 1309));
		items.add(new ItemDef("Death talisman", "A mysterious power emanates from the talisman...", "Locate", 200, -1, "items:453", false, false, 0, 0, false, false, true, 1310));
		items.add(new ItemDef("Blood talisman", "A mysterious power emanates from the talisman...", "Locate", 250, -1, "items:454", false, false, 0, 0, false, false, true, 1311));
		items.add(new ItemDef("Research package", "This contains some vital research results.", "", 0, -1, "items:330", false, false, 0, 0, true, true, false, 1312));
		items.add(new ItemDef("Research notes", "These make no sense at all.", "", 0, -1, "items:427", false, false, 0, 0, true, true, false, 1313));


		// Rings
		items.add(new ItemDef("Sapphire Ring of Recoil", "Has an 8% chance to recoil attacker damage.", "", 900, -1, "items:502", false, true, 1200, 19711, true, false, true, 1314));
		items.add(new ItemDef("Ring of splendor", "An enchanted ring.", "", 1275, -1, "items:502", false, true, 1200, 3394611, true, false, true, 1315));
		items.add(new ItemDef("Sapphire Ring of Nourishment", "Boosts food healing by 10%.", "", 2025, -1, "items:502", false, true, 1200, 16724736, true, false, true, 1316));
		items.add(new ItemDef("Diamond Ring of Preservation", "Lets you keep 4 extra items on death.", "", 3525, -1, "items:502", false, true, 1200, 0xFFFFFF, true, false, true, 1317));
		items.add(new ItemDef("Ring of Wealth", "A legacy ring without a current effect.", "", 17625, -1, "items:502", false, true, 1200, 12255487, true, false, true, 1318));
		items.add(new ItemDef("Ring of avarice", "An enchanted ring.", "", 17625, -1, "items:503", false, true, 1200, 12255487, true, false, true, 1319));
		items.add(new ItemDef("Dwarven ring", "An uncharged ring.", "", 400, -1, "items:503", false, true, 1200, 16777124, true, false, true, 1320));
		items.add(new ItemDef("Opal ring", "A valuable ring", "", 1050, -1, "items:123", false, Config.S_WANT_CUSTOM_SPRITES, Config.S_WANT_CUSTOM_SPRITES ? 1200 : 0, 16777124, false, false, true, 1321));
		addEnchantedJewelryDefinitions();

		// Wolf masks
		items.add(new ItemDef("White wolf mask", "Awoooo", "", 1, 86, "items:509", false, true, 32, 16777215, 16777215, false, false, true, 1322));
		items.add(new ItemDef("Blood wolf mask", "Awoooo", "", 1, 86, "items:509", false, true, 32, 10878976, 1513239, false, false, true, 1323));
		items.add(new ItemDef("Black wolf mask", "Awoooo", "", 1, 86, "items:509", false, true, 32, 1513239, 10878976, false, false, true, 1324));
		items.add(new ItemDef("Pink wolf mask", "Awoooo", "", 1, 86, "items:509", false, true, 32, 16759039, 16777215, false, false, true, 1325));

		// Unicorn masks
		items.add(new ItemDef("White unicorn mask", "I'm so fluffy I'm gonne die!!", "", 1, 86, "items:510", false, true, 32, 16777215, 16777215, false, false, true, 1326));
		items.add(new ItemDef("Blood unicorn mask", "I'm so fluffy I'm gonne die!!", "", 1, 86, "items:510", false, true, 32, 10878976, 1513239, false, false, true, 1327));
		items.add(new ItemDef("Black unicorn mask", "I'm so fluffy I'm gonne die!!", "", 1, 86, "items:510", false, true, 32, 1513239, 10878976, false, false, true, 1328));
		items.add(new ItemDef("Pink unicorn mask", "I'm so fluffy I'm gonne die!!", "", 1, 86, "items:510", false, true, 32, 16759039, 16777215, false, false, true, 1329));

		// Halloween cracker
		items.add(new ItemDef("Trick or treat cracker", "Use on another player to pull it", "", 0, 515, "items:515", false, false, 0, 0, false, false, true, 1330));

		// Fox mask
		items.add(new ItemDef("Fox mask", "Struttin' like a fox", "", 1, 86, "items:509", false, true, 32, 16730368, 16446686, false, false, true, 1331));

		// Xmas items
		items.add(new ItemDef("Christmas cape", "A cape worn on the holidays", "", 3, -1, "items:516", false, true, 2048, 16711680, false, false, true, 1332));
		items.add(new ItemDef("Santa's hat with beard", "It's a santa claus' hat with a beard!", "", 160, -1, "items:517", false, true, 32, 0, false, false, true, 1333));
		items.add(new ItemDef("Christmas Apron", "An apron for the festivities", "", 2, -1, "items:518", false, true, 1024, 0, false, false, true, 1334));
		items.add(new ItemDef("Glass of milk", "A glass of tasty milk", "drink", 2, -1, "items:519", false, false, 0, 0, false, false, true, 1335));
		items.add(new ItemDef("Cane cookie", "A tasty holiday cookie", "eat", 2, -1, "items:520", false, false, 0, 0, false, false, true, 1336));
		items.add(new ItemDef("Star cookie", "A tasty holiday cookie", "eat", 2, -1, "items:521", false, false, 0, 0, false, false, true, 1337));
		items.add(new ItemDef("Tree cookie", "A tasty holiday cookie", "eat", 2, -1, "items:522", false, false, 0, 0, false, false, true, 1338));
		items.add(new ItemDef("Santa's Gloves", "These keep Santa's hands warm", "", 6, -1, "items:523", false, true, 256, 0, false, false, true, 1339));
		items.add(new ItemDef("Santa's Mittens", "Santa's favorite mittens", "", 6, -1, "items:524", false, true, 256, 0, false, false, true, 1340));
		items.add(new ItemDef("Santa's suit", "A suit full of joy", "", 8, -1, "items:466", false, true, 64, 1052688, false, false, true, 1341)); // this is the top
		items.add(new ItemDef("Santa's suit", "A suit full of joy", "", 8, -1, "items:465", false, true, 128, 1052688, false, false, true, 1342)); // these are the legs
		items.add(new ItemDef("Santa's hat", "It's a santa claus' hat", "", 160, -1, "items:467", false, true, 32, 0, false, false, true, 1343)); // green version
		items.add(new ItemDef("Antlers with red-nose", "Im Rudolph the reindeer!!!", "", 3, -1, "items:468", false, true, 32, 0, false, false, true, 1344));
		items.add(new ItemDef("Beverage glass", "A glass left after a tasty drink", "", 1, -1, "items:525", false, false, 0, 0, false, false, true, 1345));

		items.add(new ItemDef("Dragon 2-handed Sword", "A massive sword", "", 5000000, -1, "items:dragon2hander", false, true, 8216, 0, false, false, true, 1346));
		items.add(new ItemDef("King Black Dragon scale", "Taken from a monstrous beast", "", 2500, 146, "items:kbdscale", false, false, 0, 0, true, false, true, 1347));


		//Harvesting
		items.add(new ItemDef("red apple", "Seems tasty!", "eat", 1, -1, "items:534", false, false, 0, 0, false, false, true, 1348));
		items.add(new ItemDef("grapefruit", "It's very fresh", "eat", 2, -1, "items:526", false, false, 0, 0, true, false, true, 1349));
		items.add(new ItemDef("papaya", "Seems very tasty!", "eat", 2, -1, "items:527", false, false, 0, 0, true, false, true, 1350));
		items.add(new ItemDef("coconut", "It can be cut up with a machette", "", 2, -1, "items:535", false, false, 0, 0, true, false, true, 1351));
		items.add(new ItemDef("Red Cabbage", "Yuck I don't like cabbage", "Eat", 1, -1, "items:529", false, false, 0, 0, false, false, true, 1352));
		items.add(new ItemDef("Corn", "Some fresh picked corn", "eat", 2, -1, "items:528", false, false, 0, 0, false, false, true, 1353));
		items.add(new ItemDef("White Pumpkin", "Wonder how it tastes", "eat", 2, -1, "items:536", false, false, 0, 0, true, false, true, 1354));
		items.add(new ItemDef("Fruit Picker", "Useful for picking trees better", "", 10, -1, "items:530", false, false, 0, 0, false, false, true, 1355));
		items.add(new ItemDef("Hand Shovel", "This will help get yield from bushes and allotments", "", 15, -1, "items:532", false, false, 0, 0, false, false, true, 1356));
		items.add(new ItemDef("Herb Clippers", "Useful for picking up herbs out there", "", 25, -1, "items:531", false, false, 0, 0, true, false, true, 1357));
		items.add(new ItemDef("Watering Can", "It's a watering can", "", 20, -1, "items:557", false, false, 0, 0, false, false, true, 1358));
		items.add(new ItemDef("grapefruit slices", "It's very fresh", "eat", 2, 291, "items:291", false, false, 0, 15435583, true, false, true, 1359));
		items.add(new ItemDef("Diced grapefruit", "Fresh chunks of grapefruit", "eat", 2, 293, "items:293", false, false, 0, 15435583, true, false, true, 1360));
		items.add(new ItemDef("Half coconut", "Looks like some great coconut", "", 2, -1, "items:538", false, false, 0, 0, true, false, true, 1361));


		//Dwarf miniquest & Dragon Scale Mail
		items.add(new ItemDef("Teddy body", "A fluffy teddy body", "", 1, -1, "items:543", false, false, 0, 16777124, false, true, false, 1362));
		items.add(new ItemDef("Teddy head", "A fluffy teddy head", "", 1, -1, "items:544", false, false, 0, 16777124, false, true, false, 1363));
		items.add(new ItemDef("Teddy", "A fluffy teddy", "", 1, -1, "items:542", false, false, 0, 16777124, false, true, false, 1364));
		items.add(new ItemDef("Dragon bar", "it's a bar of dragon metal", "", 100000, -1, "items:79", false, false, 0, 16711748, true, false, true, 1365));
		items.add(new ItemDef("Chipped Dragon Scale", "A piece of dragon scale", "", 50, -1, "items:546", true, false, 0, 0, true, false, false, 1366));
		items.add(new ItemDef("Dragon Metal Chain", "Linked dragon loops", "", 2000, -1, "items:547", true, false, 0, 0, true, false, false, 1367));
		items.add(new ItemDef("Dragon Scale Mail Body", "A dragon chain mail reinforced with dragon scales", "", 1500000, -1, "items:537", false, true, 64, 15654365, false, false, true, 1368));
		items.add(new ItemDef("Dwarf Smithy Note", "Details how to make the Dragon Scale Mail", "read", 1, 234, "items:234", false, false, 0, 0, true, true, false, 1369));

		//New leather items
		items.add(new ItemDef("Leather chaps", "They seem like decent protection", "", 14, -1, "items:512", false, true, 128, 0, false, false, true, 1370));
		items.add(new ItemDef("Leather top", "Stylish leather top", "", 21, -1, "items:511", false, true, 64, 0, false, false, true, 1371));
		items.add(new ItemDef("Leather skirt", "A ladies skirt made of leather", "", 14, -1, "items:513", false, true, 128, 0, false, false, true, 1372));

		//Skill Cape Batch One
		items.add(new ItemDef("Cooking cape", "The cape worn by the world's best chefs", "", 99000, -1, "items:481", false, true, 2048, 0, false, true, false, 1373));
		items.add(new ItemDef("Melee cape", "The cape worn by masters of melee", "", 99000, -1, "items:480", false, true, 2048, 0, false, true, false, 1374));
		items.add(new ItemDef("Thieving cape", "The cape worn by masters of thieving", "", 99000, -1, "items:496", false, true, 2048, 0, false, false, false, 1375));
		items.add(new ItemDef("Fletching cape", "The cape worn by masters of fletching", "", 99000, -1, "items:486", false, true, 2048, 0, false, false, false, 1376));
		items.add(new ItemDef("Mining cape", "The cape worn by masters of mining", "", 99000, -1, "items:490", false, true, 2048, 0, false, false, false, 1377));

		// 2020 April Fools Items
		items.add(new ItemDef("Pestilence Mask", "You wouldn't want to be seen in this! Stay the cabbage home!", "", 1, -1, "items:556", false, true, 32, 0, false, false, true, 1378));
		items.add(new ItemDef("Rubber Chicken Cap", "Wow. That was some very in-depth research on the 'chicken or the egg' question.", "", 1, -1, "items:548", false, true, 32, 0, false, false, true, 1379));

		// Skill Cape Batch Two
		items.add(new ItemDef("Fishing cape", "The cape worn by the best fishermen", "Teleport", 99000, -1, "items:485", false, true, 2048, 0, false, false, false, 1380));
		items.add(new ItemDef("Strength cape", "The cape worn by only the strongest people", "", 99000, -1, "items:495", false, true, 2048, 0, false, false, false, 1381));
		items.add(new ItemDef("Magic cape", "The cape worn by the most powerful mages", "", 99000, -1, "items:489", false, true, 2048, 0, false, false, false, 1382));
		items.add(new ItemDef("Smithing cape", "The cape worn by master smiths", "", 99000, -1, "items:494", false, true, 2048, 0, false, false, false, 1383));
		items.add(new ItemDef("Crafting cape", "The cape worn by master craftworkers", "Teleport", 99000, -1, "items:482", false, true, 2048, 0, false, false, false, 1384));


		// Runecraft Update Items
		items.add(new ItemDef("Uncharged talisman", "This needs charging to work properly...", "", 4, -1, "items:558", false, false, 0, 0, false, false, true, 1385));
		items.add(new ItemDef("Cursed air talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:559", false, false, 0, 0, false, true, false, 1386));
		items.add(new ItemDef("Cursed mind talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:560", false, false, 0, 0, false, true, false, 1387));
		items.add(new ItemDef("Cursed water talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:561", false, false, 0, 0, false, true, false, 1388));
		items.add(new ItemDef("Cursed earth talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:562", false, false, 0, 0, false, true, false, 1389));
		items.add(new ItemDef("Cursed fire talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:563", false, false, 0, 0, false, true, false, 1390));
		items.add(new ItemDef("Cursed body talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:564", false, false, 0, 0, false, true, false, 1391));
		items.add(new ItemDef("Cursed cosmic talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:565", false, false, 0, 0, false, true, false, 1392));
		items.add(new ItemDef("Cursed chaos talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:566", false, false, 0, 0, false, true, false, 1393));
		items.add(new ItemDef("Cursed nature talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:567", false, false, 0, 0, false, true, false, 1394));
		items.add(new ItemDef("Cursed law talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:568", false, false, 0, 0, false, true, false, 1395));
		items.add(new ItemDef("Cursed death talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:569", false, false, 0, 0, false, true, false, 1396));
		items.add(new ItemDef("Cursed blood talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:570", false, false, 0, 0, false, true, false, 1397));
		items.add(new ItemDef("Enfeebled air talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:573", false, false, 0, 0, false, true, false, 1398));
		items.add(new ItemDef("Enfeebled mind talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:574", false, false, 0, 0, false, true, false, 1399));
		items.add(new ItemDef("Enfeebled water talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:575", false, false, 0, 0, false, true, false, 1400));
		items.add(new ItemDef("Enfeebled earth talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:576", false, false, 0, 0, false, true, false, 1401));
		items.add(new ItemDef("Enfeebled fire talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:577", false, false, 0, 0, false, true, false, 1402));
		items.add(new ItemDef("Enfeebled body talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:578", false, false, 0, 0, false, true, false, 1403));
		items.add(new ItemDef("Enfeebled cosmic talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:579", false, false, 0, 0, false, true, false, 1404));
		items.add(new ItemDef("Enfeebled chaos talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:580", false, false, 0, 0, false, true, false, 1405));
		items.add(new ItemDef("Enfeebled nature talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:581", false, false, 0, 0, false, true, false, 1406));
		items.add(new ItemDef("Enfeebled law talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:582", false, false, 0, 0, false, true, false, 1407));
		items.add(new ItemDef("Enfeebled death talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:583", false, false, 0, 0, false, true, false, 1408));
		items.add(new ItemDef("Enfeebled blood talisman", "A mysterious power emanates from the talisman...", "Locate", 0, -1, "items:584", false, false, 0, 0, false, true, false, 1409));
		items.add(new ItemDef("Fish oil", "Good for my heart", "Eat", 1, -1, "items:587", true, false, 0, 0, false, false, false, 1410));
		items.add(new ItemDef("Runecraft Potion", "3 doses of runecraft potion", "Drink", 200, -1, "items:48", false, false, 0, 7547378, true, false, true, 1411));
		items.add(new ItemDef("Runecraft Potion", "2 doses of runecraft potion", "Drink", 150, -1, "items:436", false, false, 0, 7547378, true, false, true, 1412));
		items.add(new ItemDef("Runecraft Potion", "1 dose of runecraft potion", "Drink", 100, -1, "items:437", false, false, 0, 7547378, true, false, true, 1413));
		items.add(new ItemDef("Super Runecraft Potion", "3 doses of super runecraft potion", "Drink", 400, -1, "items:48", false, false, 0, 10710783, true, false, true, 1414));
		items.add(new ItemDef("Super Runecraft Potion", "2 doses of super runecraft potion", "Drink", 300, -1, "items:436", false, false, 0, 10710783, true, false, true, 1415));
		items.add(new ItemDef("Super Runecraft Potion", "1 dose of super runecraft potion", "Drink", 200, -1, "items:437", false, false, 0, 10710783, true, false, true, 1416));


		items.add(new ItemDef("Pizza Bagel", "I sure wish I could make these on my own", "Eat", 50, -1, "items:589", false, false, 0, 0, false, false, true, 1417));

		items.add(new ItemDef("Bronze Chain Mail Legs", "A series of connected metal rings", "", 30, -1, "items:590", false, true, 128, 16737817, false, false, true, 1418));
		items.add(new ItemDef("Iron Chain Mail Legs", "A series of connected metal rings", "", 105, -1, "items:590", false, true, 128, 15654365, false, false, true, 1419));
		items.add(new ItemDef("Steel Chain Mail Legs", "A series of connected metal rings", "", 375, -1, "items:590", false, true, 128, 15658734, false, false, true, 1420));
		items.add(new ItemDef("Mithril Chain Mail Legs", "A series of connected metal rings", "", 975, -1, "items:590", false, true, 128, 10072780, false, false, true, 1421));
		items.add(new ItemDef("Adamantite Chain Mail Legs", "A series of connected metal rings", "", 2400, -1, "items:590", false, true, 128, 11717785, false, false, true, 1422));
		items.add(new ItemDef("Rune Chain Mail Legs", "A series of connected metal rings", "", 37500, -1, "items:590", false, true, 128, 65535, false, false, true, 1423));
		items.add(new ItemDef("Black Chain Mail Legs", "A series of connected metal rings", "", 720, -1, "items:590", false, true, 128, 3158064, false, false, true, 1424));

		items.add(new ItemDef("Large Dragon Helmet", "A full face helmet", "", 5000000, -1, "items:501", false, true, 33, 0, false, false, true, 1425));
		items.add(new ItemDef("Dragon Kite Shield", "An ancient and powerful looking Dragon Kite shield", "", 5000000, -1, "items:dragonkite", false, true, 8, 0, false, false, true, 1426));
		items.add(new ItemDef("Dragon Plate Mail Body", "Provides excellent protection", "", 5000000, -1, "items:498", false, true, 322, 0, false, false, true, 1427));
		items.add(new ItemDef("Dragon Plate Mail Body", "Provides excellent protection", "", 5000000, 498, "items:498", false, true, 322, 0, false, false, true, 1428));
		items.add(new ItemDef("Dragon Plate Mail Legs", "These look pretty heavy", "", 5000000, -1, "items:499", false, true, 644, 0, false, false, true, 1429));
		items.add(new ItemDef("Dragon Plate Mail Legs", "These look pretty heavy", "", 5000000, 499, "items:499", false, true, 640, 0x960018, false, false, true, 1430));

		items.add(new ItemDef("White CTF Flag", "White Capture the flag banner", "", 1, -1, "items:554", false, true, 16, 0, false, false, true, 1431));
		items.add(new ItemDef("Guthix CTF Flag", "Guthix capture the flag banner", "", 1, -1, "items:554", false, true, 16, 4246592, false, false, true, 1432));
		items.add(new ItemDef("Saradomin CTF Flag", "Saradomin capture the flag banner", "", 1, -1, "items:554", false, true, 16, 4210926, false, false, true, 1433));
		items.add(new ItemDef("Zamorak CTF Flag", "Zamorak capture the flag banner", "", 1, -1, "items:554", false, true, 16, 16711680, false, false, true, 1434));
		items.add(new ItemDef("White Wings", "White Wings", "", 0, -1, "items:whitewings", false, true, 2048, 0, false, false, true, 1435));
		items.add(new ItemDef("Medium Valkyrie Helmet", "A medium sized Valkyrie helmet", "", 1, -1, "items:501", false, true, 32, 0, false, false, true, 1436));
		items.add(new ItemDef("Medium Guthix Valkyrie Helmet", "A medium sized Guthix Valkyrie helmet", "", 0, -1, "items:501", false, true, 32, 0, false, false, true, 1437));
		items.add(new ItemDef("Medium Saradomin Valkyrie Helmet", "A medium sized Saradomin Valkyrie helmet", "", 0, -1, "items:501", false, true, 32, 0, false, false, true, 1438));
		items.add(new ItemDef("Medium Zamorak Valkyrie Helmet", "A medium sized Zamorak Valkyrie helmet", "", 0, -1, "items:501", false, true, 32, 0, false, false, true, 1439));
		items.add(new ItemDef("Large Valkyrie Helmet", "A large sized Valkyrie helmet", "", 0, -1, "items:501", false, true, 32, 0, false, false, true, 1440));
		items.add(new ItemDef("Large Guthix Valkyrie Helmet", "A large sized Guthix Valkyrie helmet", "", 0, -1, "items:501", false, true, 32, 0, false, false, true, 1441));
		items.add(new ItemDef("Large Saradomin Valkyrie Helmet", "A large sized Saradomin Valkyrie helmet", "", 0, -1, "items:501", false, true, 32, 0, false, false, true, 1442));
		items.add(new ItemDef("Large Zamorak Valkyrie Helmet", "A large sized Zamorak Valkyrie helmet", "", 0, -1, "items:501", false, true, 32, 0, false, false, true, 1443));
		items.add(new ItemDef("Guthix Wings", "Guthix Wings", "", 0, -1, "items:guthixwings", false, true, 2048, 0, false, false, true, 1444));
		items.add(new ItemDef("Saradomin Wings", "Saradomin Wings", "", 0, -1, "items:sarawings", false, true, 2048, 0, false, false, true, 1445));
		items.add(new ItemDef("Zamorak Wings", "Zamorak Wings", "", 0, -1, "items:zammywings", false, true, 2048, 0, false, false, true, 1446));

		items.add(new ItemDef("Dragon dagger", "Short but pointy", "", 200000, 80, "items:80", false, true, 16, 16711748, false, false, true, 1447));
		items.add(new ItemDef("Poisoned dragon dagger", "Short but pointy", "", 300000, 80, "items:514", false, true, 16, 16711748, true, false, true, 1448));
		items.add(new ItemDef("Dragon arrows", "Large arrows for the dragon longbow", "", 3000, 11, "items:11", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 16711748, true, false, false, 1449));
		items.add(new ItemDef("Poison dragon arrows", "Venomous large arrows for the dragon longbow", "", 3000, 206, "items:206", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 16711748, true, false, false, 1450));
		items.add(new ItemDef("Dragon bolts", "Great if you have a dragon crossbow!", "", 3000, -1, "items:dragonbolts", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1001 : 0, 16711748, 8257536, true, false, true, 1451));
		items.add(new ItemDef("Poison dragon bolts", "Good if you have a dragon crossbow!", "", 3000, -1, "items:poisondragonbolts", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1001 : 0, 16711748, 8257536, true, false, true, 1452));
		items.add(new ItemDef("Dragon crossbow", "This fires crossbow bolts", "", 300000, -1, "items:dragoncrossbow", false, true, 16, 16711748, 8257536, true, false, true, 1453));
		items.add(new ItemDef("Dragon longbow", "A nice sturdy bow", "", 300000, 54, "items:54", false, true, 24, 8257536, 16711748, true, false, true, 1454));


		items.add(new ItemDef("Watering Can", "It's an empty watering can", "", 20, -1, "items:533", false, false, 0, 0, false, false, true, 1455));
		items.add(new ItemDef("sugar cane", "These can sweeten things up", "", 2, -1, "items:541", false, false, 0, 0, true, false, true, 1456));
		items.add(new ItemDef("dragonfruit", "A powerful fruit", "", 3, -1, "items:539", false, false, 0, 0, true, false, true, 1457));
		items.add(new ItemDef("sliced dragonfruit", "Some great dragonfruit ready to be used", "", 3, -1, "items:540", false, false, 0, 0, true, false, true, 1458));
		items.add(new ItemDef("Sweetened Slices", "Slices of fruit both sweet and sour", "eat", 2, 291, "items:291", true, false, 0, 15106125, true, false, false, 1459));
		items.add(new ItemDef("Sweetened Chunks", "Chunks of fruit both sweet and sour", "eat", 2, 293, "items:293", true, false, 0, 15106125, true, false, false, 1460));
		items.add(new ItemDef("Mixing bowl", "For mixing advanced cooking ingredients", "pour", 2, 161, "items:161", false, false, 0, 13553358, true, false, true, 1461));
		items.add(new ItemDef("Uncooked seaweed soup", "I need to cook this", "", 15, 162, "items:162", false, false, 0, 10066227, true, false, true, 1462));
		items.add(new ItemDef("Seaweed soup", "It's a seaweed soup", "Eat", 25, 162, "items:162", false, false, 0, 3368499, true, false, true, 1463));
		items.add(new ItemDef("Burnt seaweed soup", "Eew it's horribly burnt", "", 1, 162, "items:162", false, false, 0, 3158064, true, false, true, 1464));
		items.add(new ItemDef("grapes of Saradomin", "Strong grapes for a powerful wine", "", 1, 21, "items:21", false, false, 0, 4210783, true, false, true, 1465));
		items.add(new ItemDef("grapes of Zamorak", "Strong grapes for a powerful wine", "", 1, 21, "items:21", false, false, 0, 12981081, true, false, true, 1466));
		items.add(new ItemDef("wine of Saradomin", "It's full of wine", "Drink", 1, 65, "items:65", false, false, 0, 12851224, true, false, true, 1467));
		items.add(new ItemDef("magic Potion", "3 doses of magic potion", "Drink", 288, 48, "items:48", false, false, 0, 11959655, true, false, true, 1468));
		items.add(new ItemDef("magic Potion", "2 doses of magic potion", "Drink", 216, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 11959655, true, false, true, 1469));
		items.add(new ItemDef("magic Potion", "1 dose of magic potion", "Drink", 144, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 11959655, true, false, true, 1470));
		items.add(new ItemDef("Potion of Saradomin", "It looks dauntless", "drink", 25, 48, "items:48", false, false, 0, 14868319, true, false, true, 1471));
		items.add(new ItemDef("Potion of Saradomin", "It looks dauntless", "drink", 25, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 14868319, true, false, true, 1472));
		items.add(new ItemDef("Potion of Saradomin", "It looks dauntless", "drink", 25, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 14868319, true, false, true, 1473));
		items.add(new ItemDef("Super ranging Potion", "3 doses of ranging potion", "Drink", 288, 48, "items:48", false, false, 0, 3192558, true, false, true, 1474));
		items.add(new ItemDef("Super ranging Potion", "2 doses of ranging potion", "Drink", 216, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 3192558, true, false, true, 1475));
		items.add(new ItemDef("Super ranging Potion", "1 dose of ranging potion", "Drink", 144, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 3192558, true, false, true, 1476));
		items.add(new ItemDef("Super magic Potion", "3 doses of magic potion", "Drink", 288, 48, "items:48", false, false, 0, 6130854, true, false, true, 1477));
		items.add(new ItemDef("Super magic Potion", "2 doses of magic potion", "Drink", 216, Config.S_WANT_CUSTOM_SPRITES ? 436 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:436" : "items:48", false, false, 0, 6130854, true, false, true, 1478));
		items.add(new ItemDef("Super magic Potion", "1 dose of magic potion", "Drink", 144, Config.S_WANT_CUSTOM_SPRITES ? 437 : 48, Config.S_WANT_CUSTOM_SPRITES ? "items:437" : "items:48", false, false, 0, 6130854, true, false, true, 1479));


		// Item 1480 is reserved for the Dragon Woodcutting axe.
		items.add(new ItemDef("Dragon Axe", "A powerful axe", "", 1, -1, "items:12", false, false, 0, 10072780, true, false, true, 1480));

		// Easter 2021
		items.add(new ItemDef("Rabbit's Foot", "I do feel lucky, punk", "", 0, -1, "items:rabbitsfoot", false, false, 0, 16777215, false, true, false, 1481));
		items.add(new ItemDef("Rabbit's Foot", "I do feel lucky, punk", "", 0, -1, "items:rabbitsfoot", false, false, 0, 16777215, false, true, false, 1482));
		items.add(new ItemDef("Rabbit's Foot", "I do feel lucky, punk", "", 0, -1, "items:rabbitsfoot", false, false, 0, 16777215, false, true, false, 1483));
		items.add(new ItemDef("Rabbit's Foot", "I do feel lucky, punk", "", 0, -1, "items:rabbitsfoot", false, false, 0, 16777215, false, true, false, 1484));
		items.add(new ItemDef("Rabbit's Foot", "I do feel lucky, punk", "", 0, -1, "items:rabbitsfoot", false, false, 0, 16777215, false, true, false, 1485));
		items.add(new ItemDef("Ring of Bunny", "Imbued with the power of cuteness", "", 0, -1, "items:bunnyring", false, true, 1200, 0, false, true, false, 1486));
		items.add(new ItemDef("Ring of Egg", "Imbued with egg-streme power", "", 0, -1, "items:eggring", false, true, 1200, 0, false, true, false, 1487));

		// Halloween 2021
		items.add(new ItemDef("Unused", "Do Not Use", "", 1, 112, "items:112", false, false, 0, 16633518, false, false, true, 1488));
		items.add(new ItemDef("Uncooked pumpkin pie", "I need to cook this first", "", 1, 112, "items:uncookedpumpkinpie", false, false, 0, 16633518, false, false, true, 1489));
		items.add(new ItemDef("Pumpkin pie", "A festive autumn pie. It's rare to have a pie this nice.", "eat", 30, 112, "items:pumpkinpie", false, false, 0, 11168819, false, false, true, 1490));
		items.add(new ItemDef("Half a pumpkin pie", "A festive autumn pie. It's rare to have a pie this nice.", "eat", 10, 113, "items:halfpumpkinpie", false, false, 0, 11168819, false, false, true, 1491));
		items.add(new ItemDef("Uncooked white pumpkin pie", "I need to cook this first", "", 1, 112, "items:uncookedwhitepumpkinpie", false, false, 0, 16633518, false, false, true, 1492));
		items.add(new ItemDef("White pumpkin pie", "A festive autumn pie. It's weird that it's white.", "eat", 30, 112, "items:whitepumpkinpie", false, false, 0, 11168819, false, false, true, 1493));
		items.add(new ItemDef("Half a white pumpkin pie", "A festive autumn pie. It's weird that it's white.", "eat", 10, 113, "items:halfwhitepumpkinpie", false, false, 0, 11168819, false, false, true, 1494));
		items.add(new ItemDef("Eak the Mouse", "A cute mouse", "Talk", 1, 112, "items:eakthemouse", false, false, 0, 16633518, false, true, false, 1495));

		// Christmas 2021
		items.add(new ItemDef("Yoyo", "This technology shouldn't be possible!", "Play", 100, -1, "items:yoyo", false, true, 16, 0xFFFFFF,false, true, false, 1496));

		// Easter 2022 (Peeling the Onion)
		items.add(new ItemDef("Ogre Ears", "The ogres in Gu'Tannoth don't have ears like this...", "", 100, -1, "items:ogreears", false, true, 32, 0xFFFFFF, false, true, false, 1497));
		items.add(new ItemDef("Leather vest", "It's kind of fashionable?", "", 15, -1, "items:leathervest", false, true, 64, 0xFFFFFF, false, false, true, 1498));
		items.add(new ItemDef("Makeover Waiver", "yada yada yada...", "Read", 15, -1, "items:427", false, false, 0, 0xFFFFFF, false, true, false, 1499));
		items.add(new ItemDef("Soft Yellowgreen Clay", "I hope this colour doesn't get on my clothes", "Shape", 2, 105, "items:yellowgreenclay", false, false, 0, 0xFFFFFF, false, true, false, 1500));
		items.add(new ItemDef("Ogre recipes", "Just like grandma used to make", "read", 1, 234, "items:234", false, false, 0, 0, false, true, false, 1501));

		// Crowns
		items.add(new ItemDef("Crown mould", "Used to make gold crowns", "", 5, 594, "items:594", false, false, 0, 0, false, false, true, 1502));
		items.add(new ItemDef("Gold Crown", "I wonder what an enchantment would do on this valuable", "", 550, 545, "items:545", false, true, 32, 16763980, false, false, true, 1503));
		items.add(new ItemDef("Sapphire Crown", "I wonder what an enchantment would do on this valuable", "", 1200, 545, "items:545", false, true, 32, 19711, false, false, true, 1504));
		items.add(new ItemDef("Emerald Crown", "I wonder what an enchantment would do on this valuable", "", 1575, 545, "items:545", false, true, 32, 3394611, false, false, true, 1505));
		items.add(new ItemDef("Ruby Crown", "I wonder what an enchantment would do on this valuable", "", 2325, 545, "items:545", false, true, 32, 16724736, false, false, true, 1506));
		items.add(new ItemDef("Diamond Crown", "I wonder what an enchantment would do on this valuable", "", 3825, 545, "items:545", false, true, 32, 0, false, false, true, 1507));
		items.add(new ItemDef("Dragonstone Crown", "I wonder what an enchantment would do on this valuable", "", 19125, 545, "items:545", false, true, 32, 12255487, true, false, true, 1508));
		items.add(new ItemDef("Crown of dew", "It gives me a humidifier sense", "Check,Break,Configure", 1200, 545, "items:545", false, true, 32, 19711, false, false, true, 1509));
		items.add(new ItemDef("Crown of mimicry", "It helps me avoid monsters when skilling", "Check,Break", 1575, 545, "items:545", false, true, 32, 3394611, false, false, true, 1510));
		items.add(new ItemDef("Crown of the artisan", "It assists my skilling experience", "Check,Break", 2325, 545, "items:545", false, true, 32, 16724736, false, false, true, 1511));
		items.add(new ItemDef("Crown of the items", "It brings forth an item on the ground when skilling", "Check,Break", 3825, 545, "items:545", false, true, 32, 0, false, false, true, 1512));
		items.add(new ItemDef("Crown of the herbalist", "It gives me a sense to be one with herbs", "Check,Break,Configure", 19125, 545, "items:545", false, true, 32, 12255487, true, false, true, 1513));
		items.add(new ItemDef("Crown of the occult", "It gives me a sense to be one with bones", "Check,Break,Configure", 19125, 545, "items:545", false, true, 32, 12255487, true, false, true, 1514));

		// Halloween 2022
		items.add(new ItemDef("halloween mask", "aaaarrrghhh ... i'm a monster", "", 15, 284, "items:284", false, true, 32, 16711935, false, false, true, 1515));
		items.add(new ItemDef("Cape of Inclusion", "A colourful cape made from many different pieces of cloth.", "", 3, -1, "items:pridecape", false, true, 2048, 0xFFFFFF, false, true, false, 1516));
		items.add(new ItemDef("halloween mask", "aaaarrrghhh ... i'm a monster", "", 15, 284, "items:284", false, true, 32, 4, false, false, true, 1517));

		// Rest of the skillcapes
		items.add(new ItemDef("Agility cape", "The cape worn by the most agile", "Teleport", 99000, -1, "items:479", false, true, 2048, 0, false, false, false, 1518));
		items.add(new ItemDef("Defense cape", "The cape worn by the most formidable", "", 99000, -1, "items:483", false, true, 2048, 0, false, false, false, 1519));
		items.add(new ItemDef("Firemaking cape", "The cape worn by pyro enthusiasts", "Combust", 99000, -1, "items:484", false, true, 2048, 0, false, false, false, 1520));
		items.add(new ItemDef("Herblaw cape", "The cape worn by master herblawists", "", 99000, -1, "items:487", false, true, 2048, 0, false, false, false, 1521));
		items.add(new ItemDef("Hits cape", "The cape worn by the most sturdy", "", 99000, -1, "items:488", false, true, 2048, 0, false, false, false, 1522));
		items.add(new ItemDef("Prayer cape", "The cape worn by the most pious", "", 99000, -1, "items:491", false, true, 2048, 0, false, false, false, 1523));
		items.add(new ItemDef("Ranged cape", "The cape worn by the best archers", "", 99000, -1, "items:493", false, true, 2048, 0, false, false, false, 1524));
		items.add(new ItemDef("Woodcutting cape", "The cape worn by the best loggers", "", 99000, -1, "items:497", false, true, 2048, 0, false, false, false, 1525));
		items.add(new ItemDef("Harvesting cape", "The cape worn by agronomists", "", 99000, -1, "items:549", false, true, 2048, 0, false, false, false, 1526));
		items.add(new ItemDef("Enchanting cape", "The cape worn by masters of enchanting", "", 99000, -1, "items:550", false, true, 2048, 0, false, false, false, 1527));
		items.add(new ItemDef("Quest cape", "The cape worn by the most seasoned adventurers", "", 99000, -1, "items:492", false, true, 2048, 0, false, false, false, 1528));
		// We don't have a sprite for this cape, but I want to reserve the ID.
		items.add(new ItemDef("Max cape", "The cape worn by ???", "", 99000, -1, "items:485", false, true, 2048, 0, false, false, false, 1529));

		// Female chainmail tops
		items.add(new ItemDef("Bronze Chain Mail Top", "A series of connected metal rings", "", 60, -1, "items:595", false, true, 64, 16737817, false, false, true, 1530));
		items.add(new ItemDef("Iron Chain Mail Top", "A series of connected metal rings", "", 210, -1, "items:595", false, true, 64, 15654365, false, false, true, 1531));
		items.add(new ItemDef("Steel Chain Mail Top", "A series of connected metal rings", "", 750, -1, "items:595", false, true, 64, 15658734, false, false, true, 1532));
		items.add(new ItemDef("Black Chain Mail Top", "A series of connected metal rings", "", 1440, -1, "items:595", false, true, 64, 3158064, false, false, true, 1533));
		items.add(new ItemDef("Mithril Chain Mail Top", "A series of connected metal rings", "", 1950, -1, "items:595", false, true, 64, 10072780, false, false, true, 1534));
		items.add(new ItemDef("Adamantite Chain Mail Top", "A series of connected metal rings", "", 4800, -1, "items:595", false, true, 64, 11717785, false, false, true, 1535));
		items.add(new ItemDef("Rune Chain Mail Top", "A series of connected metal rings", "", 50000, -1, "items:595", false, true, 64, 65535, false, false, true, 1536));
		items.add(new ItemDef("Dragon Scale Mail Top", "A dragon chain mail reinforced with dragon scales", "", 1500000, -1, "items:596", false, true, 64, 0x0000FF, true, false, true, 1537));

		// Custom leather making
		items.add(new ItemDef("Animal fat", "Thick and gelatinous", "", 0, -1, "items:animalfat", false, false, 0, 0, false, false, true, 1538));
		items.add(new ItemDef("Treated hide", "I should use this on a fire to dry it", "", 1, -1, "items:treatedhide", false, false, 0, 0, false, false, true, 1539));
		items.add(new ItemDef("lean bear meat", "I need to cook this first", "", 1, 60, "items:60", false, false, 0, 0xE57C2B, false, false, true, 1540));
		items.add(new ItemDef("lean rat meat", "I need to cook this first", "", 1, 60, "items:60", false, false, 0, 0xE57C2B, false, false, true, 1541));
		items.add(new ItemDef("lean beef", "I need to cook this first", "", 1, 60, "items:60", false, false, 0, 0xE57C2B, false, false, true, 1542));

		items.add(new ItemDef("Rune stone certificate", "Each certificate exchangable at Varrock for 5 rune stone", "", 10, 180, "items:180", true, false, 0, 0, false, false, false, 1543));

		items.add(new ItemDef("Unobtanium", "I should update my client.", "", 17, 70, "items:70", false, false, 0, 0xCC4CFF, false, false, true, 1544));
		items.add(new ItemDef("Unobtanium", "I should update my client.", "", 17, 70, "items:70", true, false, 0, 0xCC4CFF, false, false, false, 1545));

		items.add(new ItemDef("stat restoration Potion certificate", "Each certificate exchangable at Varrock for 5 stat restore potions", "", 10, 180, "items:180", true, false, 0, 0, false, false, false, 1546));
		items.add(new ItemDef("giant carp certificate", "Each certificate exchangable at Varrock for 5 giant carp", "", 10, 180, "items:180", true, false, 0, 0, false, false, false, 1547));
		items.add(new ItemDef("Lava eel certificate", "Each certificate exchangable at Varrock for 5 lava eels", "", 10, 180, "items:180", true, false, 0, 0, false, false, false, 1548));
		items.add(new ItemDef("Poison antidote certificate", "Each certificate exchangable at Varrock for 5 poison antidote potions", "", 10, 180, "items:180", true, false, 0, 0, false, false, false, 1549));
		items.add(new ItemDef("Manta ray certificate", "Each certificate exchangable at Varrock for 5 manta rays", "", 10, 180, "items:180", true, false, 0, 0, false, false, false, 1550));
		items.add(new ItemDef("Sea turtle certificate", "Each certificate exchangable at Varrock for 5 sea turtles", "", 10, 180, "items:180", true, false, 0, 0, false, false, false, 1551));
		items.add(new ItemDef("Cure poison Potion certificate", "Each certificate exchangable at Varrock for 5 cure poison potions", "", 10, 180, "items:180", true, false, 0, 0, false, false, false, 1552));

		items.add(new ItemDef("Biggum Flodrot", "Biggum Flodrot, goblin hero", "Talk", 0, -1, "items:597", false, false, 0, 0, true, true, false, 1553));

		// Ironman plate tops
		items.add(new ItemDef("Ironman platebody", "Take it off and what are you?", "", 560, -1, "items:8", false, true, 322, 11189164, false, true, true, 1554));
		items.add(new ItemDef("Ultimate ironman platebody", "Take it off and what are you?", "", 560, -1, "items:8", false, true, 322, 16768685, false, true, true, 1555));
		items.add(new ItemDef("Hardcore ironman platebody", "Take it off and what are you?", "", 560, -1, "items:8", false, true, 322, 10027084, false, true, true, 1556));

		// Ironman plated skirts
		items.add(new ItemDef("Ironman platelegs", "Take it off and what are you?", "", 280, -1, "items:9", false, true, 644, 0x6F7A70, false, true, true, 1557));
		items.add(new ItemDef("Ultimate ironman platelegs", "Take it off and what are you?", "", 280, -1, "items:9", false, true, 644, 0xA69070, false, true, true, 1558));
		items.add(new ItemDef("Hardcore ironman platelegs", "Take it off and what are you?", "", 280, -1, "items:9", false, true, 644, 0x640031, false, true, true, 1559));

		// Halloween 2023
		items.add(new ItemDef("Bonecrusher", "A contraption that crushes bones to dust", "", 0, -1, "items:598", false, false, 0, 0, false, true, false, 1560));
		items.add(new ItemDef("Chipped pestle and mortar", "The apothecary's old pestle & mortar", "", 4, 147, "items:147", false, false, 0, 0, false, true, false, 1561));
		items.add(new ItemDef("aluminium bar", "this looks malleable", "", 150, 79, "items:79", false, false, 0, 0xFFFFFF, false, true, true, 1562));
		items.add(new ItemDef("aluminium cog", "A piece of machinery", "", 150, -1, "items:599", false, false, 0, 0, false, true, true, 1563));
		items.add(new ItemDef("Wooden box", "A box made of wood", "", 25, -1, "items:600", false, false, 0, 0xFFFFFF, false, true, true, 1564));
		items.add(new ItemDef("Ring of Skull", "Imbued with the powers of a bonafide skeleton", "", 0, -1, "items:601", false, true, 1200, 0, false, true, false, 1565));
		items.add(new ItemDef("halloween mask", "aaaarrrghhh ... i'm a monster", "", 15, 284, "items:602", false, true, 32, 65280, false, false, true, 1566));
		items.add(new ItemDef("Spookie's Bones", "Better do something about these", "", 1, 20, "items:20", false, false, 0, 0, false, true, true, 1567));
		items.add(new ItemDef("Scarie's Bones", "Better do something about these", "", 1, 20, "items:20", false, false, 0, 0, false, true, true, 1568));
		items.add(new ItemDef("Lily's Pumpkin", "A pumpkin harvested from Lily's field", "eat", 30, 149, "items:149", false, false, 0, 0, false, false, true, 1569));
		items.add(new ItemDef("Uncooked Lily's pumpkin pie", "I need to cook this first", "", 1, 112, "items:603", false, false, 0, 16633518, false, false, true, 1570));
		items.add(new ItemDef("Lily's pumpkin pie", "Mmm a pie made with Lily's pumpkins", "eat", 30, 112, "items:604", false, false, 0, 16633518, false, false, true, 1571));
		items.add(new ItemDef("Half a Lily's pumpkin pie", "Mmm a pie made with Lily's pumpkins", "eat", 5, 113, "items:605", false, false, 0, 16633518, false, false, true, 1572));

		// Christmas 2023
		items.add(new ItemDef("Duke Horacio's Journal", "This is a journal not a diary", "read", 1, 28, "items:28", false, false, 0, 0xFF0000, false, true, false, 1573));
		items.add(new ItemDef("parchment", "I can write on this", "write", 1, 244, "items:244", false, false, 0, 14540253, false, true, false, 1574));
		items.add(new ItemDef("Apology letter", "A heartfelt apology letter", "read", 1, 244, "items:244", false, false, 0, 14540253, false, true, false, 1575));
		items.add(new ItemDef("Christmas sweater", "Knitted with love!", "", 5, 87, "items:christmassweater", false, true, 64, 0x820101, false, true, false, 1576));
		items.add(new ItemDef("Christmas sweater", "Knitted with love!", "", 5, 87, "items:christmassweater", false, true, 64, 0xc3c90e, false, true, false, 1577));
		items.add(new ItemDef("Christmas sweater", "Knitted with love!", "", 5, 87, "items:christmassweater", false, true, 64, 0x012c82, false, true, false, 1578));
		items.add(new ItemDef("Christmas sweater", "Knitted with love!", "", 5, 87, "items:christmassweater", false, true, 64, 0x6804b5, false, true, false, 1579));
		items.add(new ItemDef("Christmas sweater", "Knitted with love!", "", 5, 87, "items:christmassweater", false, true, 64, 0xcf7602, false, true, false, 1580));
		items.add(new ItemDef("Christmas sweater", "Knitted with love!", "", 5, 87, "items:christmassweater", false, true, 64, 0x106105, false, true, false, 1581));
		items.add(new ItemDef("Party Hat", "Party!!!", "", 2, 189, "items:189", false, true, 32, 1, false, false, true, 1582));
		items.add(new ItemDef("Santa's hat", "It's a santa claus' hat", "", 160, -1, "items:pinksantahat", false, true, 32, 0, false, false, true, 1583));
		items.add(new ItemDef("Christmas sweater", "Knitted with love!", "", 5, 87, "items:christmassweater", false, true, 64, 0x820101, false, true, false, 1584));
		items.add(new ItemDef("Christmas sweater", "Knitted with love!", "", 5, 87, "items:christmassweater", false, true, 64, 0xc3c90e, false, true, false, 1585));
		items.add(new ItemDef("Christmas sweater", "Knitted with love!", "", 5, 87, "items:christmassweater", false, true, 64, 0x012c82, false, true, false, 1586));
		items.add(new ItemDef("Christmas sweater", "Knitted with love!", "", 5, 87, "items:christmassweater", false, true, 64, 0x6804b5, false, true, false, 1587));
		items.add(new ItemDef("Christmas sweater", "Knitted with love!", "", 5, 87, "items:christmassweater", false, true, 64, 0xcf7602, false, true, false, 1588));
		items.add(new ItemDef("Christmas sweater", "Knitted with love!", "", 5, 87, "items:christmassweater", false, true, 64, 0x106105, false, true, false, 1589));

		// Halloween 2024
		items.add(new ItemDef("Necronomicon Ex Mortis", "This looks very homemade", "read", 1, 28, "items:28", false, false, 0, 0x777777, false, true, false, 1590));
		items.add(new ItemDef("Ancient amulet", "A sinister looking amulet", "", 1, 125, "items:125", false, true, 1024, 0x010101, false, true, false, 1591));
		items.add(new ItemDef("Boomstick", "A 12-Gauge, Double-Barreled Remington", "", 1, 606, "items:606", false, true, 16, 16737817, false, true, false, 1592));

		// Placeholder support so custom material ids are client-valid up to the MyWorld range.
		for (int customId = 1593; customId < 1789; customId++) {
			items.add(new ItemDef("Unknown item", "A custom item placeholder", "", 1, 69, "items:69", false, false, 0, 0x777777, false, false, true, customId));
		}

		// MyWorld hide / leather materials
		items.add(new ItemDef("Goblin hide", "A muted green goblin hide", "", 4, 69, "items:69", false, false, 0, 0x7c9a29, false, false, true, 1789));
		items.add(new ItemDef("Goblin leather", "Processed goblin hide", "", 8, 69, "items:69", false, false, 0, 0x5f7d20, false, false, true, 1790));
		items.add(new ItemDef("Unicorn hide", "A pale unicorn hide", "", 5, 69, "items:69", false, false, 0, 0xe7dfc8, false, false, true, 1791));
		items.add(new ItemDef("Unicorn leather", "Processed unicorn hide", "", 10, 69, "items:69", false, false, 0, 0xcfc2a2, false, false, true, 1792));
		items.add(new ItemDef("Bear hide", "A thick brown bear hide", "", 5, 69, "items:69", false, false, 0, 0x6e4b2e, false, false, true, 1793));
		items.add(new ItemDef("Bear leather", "Processed bear hide", "", 10, 69, "items:69", false, false, 0, 0x57381f, false, false, true, 1794));
		items.add(new ItemDef("Black unicorn hide", "A dark hide with a cool purple cast", "", 6, 69, "items:69", false, false, 0, 0x312c45, false, false, true, 1795));
		items.add(new ItemDef("Black unicorn leather", "Processed black unicorn hide", "", 12, 69, "items:69", false, false, 0, 0x262038, false, false, true, 1796));
		items.add(new ItemDef("Wolf hide", "A cool grey wolf hide", "", 6, 69, "items:69", false, false, 0, 0x7c838d, false, false, true, 1797));
		items.add(new ItemDef("Wolf leather", "Processed wolf hide", "", 12, 69, "items:69", false, false, 0, 0x5d6570, false, false, true, 1798));
		items.add(new ItemDef("Hellhound hide", "An ash-grey infernal hide", "", 10, 69, "items:69", false, false, 0, 0x605854, false, false, true, 1799));
		items.add(new ItemDef("Hellhound leather", "Processed hellhound hide", "", 20, 69, "items:69", false, false, 0, 0x4b403d, false, false, true, 1800));
		items.add(new ItemDef("Spider carapace", "A hard spider shell", "", 6, 69, "items:69", false, false, 0, 0x6a342a, false, false, true, 1801));
		items.add(new ItemDef("Cured spider carapace", "A polished spider shell", "", 12, 69, "items:69", false, false, 0, 0x55271f, false, false, true, 1802));
		items.add(new ItemDef("Magic spider carapace", "A shell humming with strange energy", "", 10, 69, "items:69", false, false, 0, 0x4c4498, false, false, true, 1803));
		items.add(new ItemDef("Cured magic spider carapace", "A polished arcane shell", "", 20, 69, "items:69", false, false, 0, 0x3d377a, false, false, true, 1804));
		items.add(new ItemDef("Scorpion carapace", "A sandy scorpion shell", "", 5, 69, "items:69", false, false, 0, 0xb08a45, false, false, true, 1805));
		items.add(new ItemDef("Cured scorpion carapace", "A polished scorpion shell", "", 10, 69, "items:69", false, false, 0, 0x8f6d33, false, false, true, 1806));
		items.add(new ItemDef("Giant hide", "A broad giant hide", "", 7, 69, "items:69", false, false, 0, 0x9d8b73, false, false, true, 1807));
		items.add(new ItemDef("Giant leather", "Processed giant hide", "", 14, 69, "items:69", false, false, 0, 0x7e6e59, false, false, true, 1808));
		items.add(new ItemDef("Moss giant hide", "A hide streaked with mossy green", "", 10, 69, "items:69", false, false, 0, 0x64703a, false, false, true, 1809));
		items.add(new ItemDef("Moss giant leather", "Processed moss giant hide", "", 20, 69, "items:69", false, false, 0, 0x50582d, false, false, true, 1810));
		items.add(new ItemDef("Ice giant hide", "A pale icy hide", "", 10, 69, "items:69", false, false, 0, 0xc8d9e6, false, false, true, 1811));
		items.add(new ItemDef("Ice giant leather", "Processed ice giant hide", "", 20, 69, "items:69", false, false, 0, 0xa6bdcf, false, false, true, 1812));
		items.add(new ItemDef("Fire giant hide", "A hide warm with ember tones", "", 12, 69, "items:69", false, false, 0, 0xbd5f23, false, false, true, 1813));
		items.add(new ItemDef("Fire giant leather", "Processed fire giant hide", "", 24, 69, "items:69", false, false, 0, 0x9a4916, false, false, true, 1814));
		items.add(new ItemDef("Ogre hide", "A heavy ogre hide", "", 8, 69, "items:69", false, false, 0, 0x657245, false, false, true, 1815));
		items.add(new ItemDef("Ogre leather", "Processed ogre hide", "", 16, 69, "items:69", false, false, 0, 0x4f5933, false, false, true, 1816));
		items.add(new ItemDef("Baby dragon hide", "A supple dragon hide", "", 6, 69, "items:69", false, false, 0, 0x6a9d8e, false, false, true, 1817));
		items.add(new ItemDef("Baby dragon leather", "Processed baby dragon hide", "", 12, 69, "items:69", false, false, 0, 0x4f7d70, false, false, true, 1818));
		items.add(new ItemDef("Demon hide", "A dark crimson demon hide", "", 16, 69, "items:69", false, false, 0, 0x7d2026, false, false, true, 1819));
		items.add(new ItemDef("Demon leather", "Processed demon hide", "", 32, 69, "items:69", false, false, 0, 0x63171c, false, false, true, 1820));
		items.add(new ItemDef("Blue dragon hide", "A strong cobalt-blue dragon hide", "", 20, 69, "items:69", false, false, 0, 0x295eb8, false, false, true, 1821));
		items.add(new ItemDef("Blue dragon leather", "Processed blue dragon hide", "", 40, 69, "items:69", false, false, 0, 0x20478d, false, false, true, 1822));
		items.add(new ItemDef("Dragon hide", "A sturdy green dragon hide", "", 18, 69, "items:69", false, false, 0, 0x3f8b43, false, false, true, 1823));
		items.add(new ItemDef("Dragon leather", "Processed dragon hide", "", 36, 69, "items:69", false, false, 0, 0x306c33, false, false, true, 1824));
		items.add(new ItemDef("Red dragon hide", "A rich red dragon hide", "", 24, 69, "items:69", false, false, 0, 0xb3362e, false, false, true, 1825));
		items.add(new ItemDef("Red dragon leather", "Processed red dragon hide", "", 48, 69, "items:69", false, false, 0, 0x8c2620, false, false, true, 1826));
		items.add(new ItemDef("Black demon hide", "A dark hide with muted red undertones", "", 28, 69, "items:69", false, false, 0, 0x302325, false, false, true, 1827));
		items.add(new ItemDef("Black demon leather", "Processed black demon hide", "", 56, 69, "items:69", false, false, 0, 0x24191b, false, false, true, 1828));
		items.add(new ItemDef("Black dragon hide", "A near-black hide with a cool blue cast", "", 30, 69, "items:69", false, false, 0, 0x28303d, false, false, true, 1829));
		items.add(new ItemDef("Black dragon leather", "Processed black dragon hide", "", 60, 69, "items:69", false, false, 0, 0x1f2530, false, false, true, 1830));
		items.add(new ItemDef("Balrog hide", "A lava-dark infernal hide", "", 36, 69, "items:69", false, false, 0, 0x44312a, false, false, true, 1831));
		items.add(new ItemDef("Balrog leather", "Processed balrog hide", "", 72, 69, "items:69", false, false, 0, 0x35241f, false, false, true, 1832));
		items.add(new ItemDef("King black dragon hide", "A legendary dark dragon hide", "", 40, 69, "items:69", false, false, 0, 0x20252a, false, false, true, 1833));
		items.add(new ItemDef("King black dragon leather", "Processed king black dragon hide", "", 80, 69, "items:69", false, false, 0, 0x171b20, false, false, true, 1834));
		items.add(new ItemDef("Cow-hide coif", "A simple coif made from cow hide", "", 8, 5, "items:5", false, true, 32, 0x7a5a38, false, false, true, 1835));
		items.add(new ItemDef("Cow-hide gloves", "Gloves worked from cow hide", "", 12, 17, "items:17", false, true, 256, 0x7a5a38, false, false, true, 1836));
		items.add(new ItemDef("Cow-hide boots", "Boots worked from cow hide", "", 12, 223, "items:223", false, true, 512, 0x7a5a38, false, false, true, 1837));
		items.add(new ItemDef("Cow-hide chaps", "Leg protection stitched from cow hide", "", 18, 590, "items:590", false, true, 128, 0x7a5a38, false, false, true, 1838));
		items.add(new ItemDef("Cow-hide cuirass", "A cuirass stitched from cow hide", "", 24, 7, "items:7", false, true, 64, 0x7a5a38, false, false, true, 1839));
		items.add(new ItemDef("Goblin-hide coif", "A rough coif made from goblin hide", "", 8, 5, "items:5", false, true, 32, 0x7b8f2f, false, false, true, 1840));
		items.add(new ItemDef("Goblin-hide gloves", "Gloves worked from goblin hide", "", 12, 17, "items:17", false, true, 256, 0x7b8f2f, false, false, true, 1841));
		items.add(new ItemDef("Goblin-hide boots", "Boots worked from goblin hide", "", 12, 223, "items:223", false, true, 512, 0x7b8f2f, false, false, true, 1842));
		items.add(new ItemDef("Goblin-hide chaps", "Leg protection stitched from goblin hide", "", 18, 590, "items:590", false, true, 128, 0x7b8f2f, false, false, true, 1843));
		items.add(new ItemDef("Goblin-hide cuirass", "A cuirass stitched from goblin hide", "", 24, 7, "items:7", false, true, 64, 0x7b8f2f, false, false, true, 1844));
		items.add(new ItemDef("Unicorn-hide coif", "A pale coif made from unicorn hide", "", 16, 5, "items:5", false, true, 32, 0xcfc7a4, false, false, true, 1845));
		items.add(new ItemDef("Unicorn-hide gloves", "Gloves worked from unicorn hide", "", 24, 17, "items:17", false, true, 256, 0xcfc7a4, false, false, true, 1846));
		items.add(new ItemDef("Unicorn-hide boots", "Boots worked from unicorn hide", "", 24, 223, "items:223", false, true, 512, 0xcfc7a4, false, false, true, 1847));
		items.add(new ItemDef("Unicorn-hide chaps", "Leg protection stitched from unicorn hide", "", 36, 590, "items:590", false, true, 128, 0xcfc7a4, false, false, true, 1848));
		items.add(new ItemDef("Unicorn-hide cuirass", "A cuirass stitched from unicorn hide", "", 48, 7, "items:7", false, true, 64, 0xcfc7a4, false, false, true, 1849));
		items.add(new ItemDef("Bear-hide coif", "A heavy coif made from bear hide", "", 16, 5, "items:5", false, true, 32, 0x5a3b24, false, false, true, 1850));
		items.add(new ItemDef("Bear-hide gloves", "Gloves worked from bear hide", "", 24, 17, "items:17", false, true, 256, 0x5a3b24, false, false, true, 1851));
		items.add(new ItemDef("Bear-hide boots", "Boots worked from bear hide", "", 24, 223, "items:223", false, true, 512, 0x5a3b24, false, false, true, 1852));
		items.add(new ItemDef("Bear-hide chaps", "Leg protection stitched from bear hide", "", 36, 590, "items:590", false, true, 128, 0x5a3b24, false, false, true, 1853));
		items.add(new ItemDef("Bear-hide cuirass", "A cuirass stitched from bear hide", "", 48, 7, "items:7", false, true, 64, 0x5a3b24, false, false, true, 1854));
		items.add(new ItemDef("Black unicorn-hide coif", "A dark coif made from black unicorn hide", "", 16, 5, "items:5", false, true, 32, 0x2e2a38, false, false, true, 1855));
		items.add(new ItemDef("Black unicorn-hide gloves", "Gloves worked from black unicorn hide", "", 24, 17, "items:17", false, true, 256, 0x2e2a38, false, false, true, 1856));
		items.add(new ItemDef("Black unicorn-hide boots", "Boots worked from black unicorn hide", "", 24, 223, "items:223", false, true, 512, 0x2e2a38, false, false, true, 1857));
		items.add(new ItemDef("Black unicorn-hide chaps", "Leg protection stitched from black unicorn hide", "", 36, 590, "items:590", false, true, 128, 0x2e2a38, false, false, true, 1858));
		items.add(new ItemDef("Black unicorn-hide cuirass", "A cuirass stitched from black unicorn hide", "", 48, 7, "items:7", false, true, 64, 0x2e2a38, false, false, true, 1859));
		items.add(new ItemDef("Scorpion-carapace coif", "A coif formed from cured scorpion carapace", "", 16, 5, "items:5", false, true, 32, 0xb08b45, false, false, true, 1860));
		items.add(new ItemDef("Scorpion-carapace gloves", "Gloves formed from cured scorpion carapace", "", 24, 17, "items:17", false, true, 256, 0xb08b45, false, false, true, 1861));
		items.add(new ItemDef("Scorpion-carapace boots", "Boots formed from cured scorpion carapace", "", 24, 223, "items:223", false, true, 512, 0xb08b45, false, false, true, 1862));
		items.add(new ItemDef("Scorpion-carapace chaps", "Leg protection formed from cured scorpion carapace", "", 36, 590, "items:590", false, true, 128, 0xb08b45, false, false, true, 1863));
		items.add(new ItemDef("Scorpion-carapace cuirass", "A cuirass formed from cured scorpion carapace", "", 48, 7, "items:7", false, true, 64, 0xb08b45, false, false, true, 1864));
		items.add(new ItemDef("Wolf-hide coif", "A coif made from wolf hide", "", 24, 5, "items:5", false, true, 32, 0x5d6570, false, false, true, 1865));
		items.add(new ItemDef("Wolf-hide gloves", "A gloves made from wolf hide", "", 48, 17, "items:17", false, true, 256, 0x5d6570, false, false, true, 1866));
		items.add(new ItemDef("Wolf-hide boots", "A boots made from wolf hide", "", 48, 223, "items:223", false, true, 512, 0x5d6570, false, false, true, 1867));
		items.add(new ItemDef("Wolf-hide chaps", "A chaps made from wolf hide", "", 72, 590, "items:590", false, true, 128, 0x5d6570, false, false, true, 1868));
		items.add(new ItemDef("Wolf-hide cuirass", "A cuirass made from wolf hide", "", 96, 7, "items:7", false, true, 64, 0x5d6570, false, false, true, 1869));
		items.add(new ItemDef("Spider-carapace coif", "A coif made from cured spider carapace", "", 24, 5, "items:5", false, true, 32, 0x55271f, false, false, true, 1870));
		items.add(new ItemDef("Spider-carapace gloves", "A gloves made from cured spider carapace", "", 48, 17, "items:17", false, true, 256, 0x55271f, false, false, true, 1871));
		items.add(new ItemDef("Spider-carapace boots", "A boots made from cured spider carapace", "", 48, 223, "items:223", false, true, 512, 0x55271f, false, false, true, 1872));
		items.add(new ItemDef("Spider-carapace chaps", "A chaps made from cured spider carapace", "", 72, 590, "items:590", false, true, 128, 0x55271f, false, false, true, 1873));
		items.add(new ItemDef("Spider-carapace cuirass", "A cuirass made from cured spider carapace", "", 96, 7, "items:7", false, true, 64, 0x55271f, false, false, true, 1874));
		items.add(new ItemDef("Giant-hide coif", "A coif made from giant hide", "", 24, 5, "items:5", false, true, 32, 0x7e6e59, false, false, true, 1875));
		items.add(new ItemDef("Giant-hide gloves", "A gloves made from giant hide", "", 48, 17, "items:17", false, true, 256, 0x7e6e59, false, false, true, 1876));
		items.add(new ItemDef("Giant-hide boots", "A boots made from giant hide", "", 48, 223, "items:223", false, true, 512, 0x7e6e59, false, false, true, 1877));
		items.add(new ItemDef("Giant-hide chaps", "A chaps made from giant hide", "", 72, 590, "items:590", false, true, 128, 0x7e6e59, false, false, true, 1878));
		items.add(new ItemDef("Giant-hide cuirass", "A cuirass made from giant hide", "", 96, 7, "items:7", false, true, 64, 0x7e6e59, false, false, true, 1879));
		items.add(new ItemDef("Ogre-hide coif", "A coif made from ogre hide", "", 32, 5, "items:5", false, true, 32, 0x4f5933, false, false, true, 1880));
		items.add(new ItemDef("Ogre-hide gloves", "A gloves made from ogre hide", "", 64, 17, "items:17", false, true, 256, 0x4f5933, false, false, true, 1881));
		items.add(new ItemDef("Ogre-hide boots", "A boots made from ogre hide", "", 64, 223, "items:223", false, true, 512, 0x4f5933, false, false, true, 1882));
		items.add(new ItemDef("Ogre-hide chaps", "A chaps made from ogre hide", "", 96, 590, "items:590", false, true, 128, 0x4f5933, false, false, true, 1883));
		items.add(new ItemDef("Ogre-hide cuirass", "A cuirass made from ogre hide", "", 128, 7, "items:7", false, true, 64, 0x4f5933, false, false, true, 1884));
		items.add(new ItemDef("Baby-dragon-hide coif", "A coif made from baby dragon hide", "", 32, 5, "items:5", false, true, 32, 0x4f7d70, false, false, true, 1885));
		items.add(new ItemDef("Baby-dragon-hide gloves", "A gloves made from baby dragon hide", "", 64, 17, "items:17", false, true, 256, 0x4f7d70, false, false, true, 1886));
		items.add(new ItemDef("Baby-dragon-hide boots", "A boots made from baby dragon hide", "", 64, 223, "items:223", false, true, 512, 0x4f7d70, false, false, true, 1887));
		items.add(new ItemDef("Baby-dragon-hide chaps", "A chaps made from baby dragon hide", "", 96, 590, "items:590", false, true, 128, 0x4f7d70, false, false, true, 1888));
		items.add(new ItemDef("Baby-dragon-hide cuirass", "A cuirass made from baby dragon hide", "", 128, 7, "items:7", false, true, 64, 0x4f7d70, false, false, true, 1889));
		items.add(new ItemDef("Magic-spider-carapace coif", "A coif made from cured magic spider carapace", "", 40, 5, "items:5", false, true, 32, 0x3d377a, false, false, true, 1890));
		items.add(new ItemDef("Magic-spider-carapace gloves", "A gloves made from cured magic spider carapace", "", 80, 17, "items:17", false, true, 256, 0x3d377a, false, false, true, 1891));
		items.add(new ItemDef("Magic-spider-carapace boots", "A boots made from cured magic spider carapace", "", 80, 223, "items:223", false, true, 512, 0x3d377a, false, false, true, 1892));
		items.add(new ItemDef("Magic-spider-carapace chaps", "A chaps made from cured magic spider carapace", "", 120, 590, "items:590", false, true, 128, 0x3d377a, false, false, true, 1893));
		items.add(new ItemDef("Magic-spider-carapace cuirass", "A cuirass made from cured magic spider carapace", "", 160, 7, "items:7", false, true, 64, 0x3d377a, false, false, true, 1894));
		items.add(new ItemDef("Moss-giant-hide coif", "A coif made from moss giant hide", "", 40, 5, "items:5", false, true, 32, 0x50582d, false, false, true, 1895));
		items.add(new ItemDef("Moss-giant-hide gloves", "A gloves made from moss giant hide", "", 80, 17, "items:17", false, true, 256, 0x50582d, false, false, true, 1896));
		items.add(new ItemDef("Moss-giant-hide boots", "A boots made from moss giant hide", "", 80, 223, "items:223", false, true, 512, 0x50582d, false, false, true, 1897));
		items.add(new ItemDef("Moss-giant-hide chaps", "A chaps made from moss giant hide", "", 120, 590, "items:590", false, true, 128, 0x50582d, false, false, true, 1898));
		items.add(new ItemDef("Moss-giant-hide cuirass", "A cuirass made from moss giant hide", "", 160, 7, "items:7", false, true, 64, 0x50582d, false, false, true, 1899));
		items.add(new ItemDef("Ice-giant-hide coif", "A coif made from ice giant hide", "", 40, 5, "items:5", false, true, 32, 0xa6bdcf, false, false, true, 1900));
		items.add(new ItemDef("Ice-giant-hide gloves", "A gloves made from ice giant hide", "", 80, 17, "items:17", false, true, 256, 0xa6bdcf, false, false, true, 1901));
		items.add(new ItemDef("Ice-giant-hide boots", "A boots made from ice giant hide", "", 80, 223, "items:223", false, true, 512, 0xa6bdcf, false, false, true, 1902));
		items.add(new ItemDef("Ice-giant-hide chaps", "A chaps made from ice giant hide", "", 120, 590, "items:590", false, true, 128, 0xa6bdcf, false, false, true, 1903));
		items.add(new ItemDef("Ice-giant-hide cuirass", "A cuirass made from ice giant hide", "", 160, 7, "items:7", false, true, 64, 0xa6bdcf, false, false, true, 1904));
		items.add(new ItemDef("Demon-hide coif", "A coif made from demon hide", "", 48, 5, "items:5", false, true, 32, 0x63171c, false, false, true, 1905));
		items.add(new ItemDef("Demon-hide gloves", "A gloves made from demon hide", "", 96, 17, "items:17", false, true, 256, 0x63171c, false, false, true, 1906));
		items.add(new ItemDef("Demon-hide boots", "A boots made from demon hide", "", 96, 223, "items:223", false, true, 512, 0x63171c, false, false, true, 1907));
		items.add(new ItemDef("Demon-hide chaps", "A chaps made from demon hide", "", 144, 590, "items:590", false, true, 128, 0x63171c, false, false, true, 1908));
		items.add(new ItemDef("Demon-hide cuirass", "A cuirass made from demon hide", "", 192, 7, "items:7", false, true, 64, 0x63171c, false, false, true, 1909));
		items.add(new ItemDef("Hellhound-hide coif", "A coif made from hellhound hide", "", 56, 5, "items:5", false, true, 32, 0x4b403d, false, false, true, 1910));
		items.add(new ItemDef("Hellhound-hide gloves", "A gloves made from hellhound hide", "", 112, 17, "items:17", false, true, 256, 0x4b403d, false, false, true, 1911));
		items.add(new ItemDef("Hellhound-hide boots", "A boots made from hellhound hide", "", 112, 223, "items:223", false, true, 512, 0x4b403d, false, false, true, 1912));
		items.add(new ItemDef("Hellhound-hide chaps", "A chaps made from hellhound hide", "", 168, 590, "items:590", false, true, 128, 0x4b403d, false, false, true, 1913));
		items.add(new ItemDef("Hellhound-hide cuirass", "A cuirass made from hellhound hide", "", 224, 7, "items:7", false, true, 64, 0x4b403d, false, false, true, 1914));
		items.add(new ItemDef("Fire-giant-hide coif", "A coif made from fire giant hide", "", 56, 5, "items:5", false, true, 32, 0x9a4916, false, false, true, 1915));
		items.add(new ItemDef("Fire-giant-hide gloves", "A gloves made from fire giant hide", "", 112, 17, "items:17", false, true, 256, 0x9a4916, false, false, true, 1916));
		items.add(new ItemDef("Fire-giant-hide boots", "A boots made from fire giant hide", "", 112, 223, "items:223", false, true, 512, 0x9a4916, false, false, true, 1917));
		items.add(new ItemDef("Fire-giant-hide chaps", "A chaps made from fire giant hide", "", 168, 590, "items:590", false, true, 128, 0x9a4916, false, false, true, 1918));
		items.add(new ItemDef("Fire-giant-hide cuirass", "A cuirass made from fire giant hide", "", 224, 7, "items:7", false, true, 64, 0x9a4916, false, false, true, 1919));
		items.add(new ItemDef("Blue-dragon-hide coif", "A coif made from blue dragon hide", "", 56, 5, "items:5", false, true, 32, 0x20478d, false, false, true, 1920));
		items.add(new ItemDef("Blue-dragon-hide gloves", "A gloves made from blue dragon hide", "", 112, 17, "items:17", false, true, 256, 0x20478d, false, false, true, 1921));
		items.add(new ItemDef("Blue-dragon-hide boots", "A boots made from blue dragon hide", "", 112, 223, "items:223", false, true, 512, 0x20478d, false, false, true, 1922));
		items.add(new ItemDef("Blue-dragon-hide chaps", "A chaps made from blue dragon hide", "", 168, 590, "items:590", false, true, 128, 0x20478d, false, false, true, 1923));
		items.add(new ItemDef("Blue-dragon-hide cuirass", "A cuirass made from blue dragon hide", "", 224, 7, "items:7", false, true, 64, 0x20478d, false, false, true, 1924));
		items.add(new ItemDef("Dragon-hide coif", "A coif made from dragon hide", "", 56, 5, "items:5", false, true, 32, 0x306c33, false, false, true, 1925));
		items.add(new ItemDef("Dragon-hide gloves", "A gloves made from dragon hide", "", 112, 17, "items:17", false, true, 256, 0x306c33, false, false, true, 1926));
		items.add(new ItemDef("Dragon-hide boots", "A boots made from dragon hide", "", 112, 223, "items:223", false, true, 512, 0x306c33, false, false, true, 1927));
		items.add(new ItemDef("Dragon-hide chaps", "A chaps made from dragon hide", "", 168, 590, "items:590", false, true, 128, 0x306c33, false, false, true, 1928));
		items.add(new ItemDef("Dragon-hide cuirass", "A cuirass made from dragon hide", "", 224, 7, "items:7", false, true, 64, 0x306c33, false, false, true, 1929));
		items.add(new ItemDef("Red-dragon-hide coif", "A coif made from red dragon hide", "", 64, 5, "items:5", false, true, 32, 0x8c2620, false, false, true, 1930));
		items.add(new ItemDef("Red-dragon-hide gloves", "A gloves made from red dragon hide", "", 128, 17, "items:17", false, true, 256, 0x8c2620, false, false, true, 1931));
		items.add(new ItemDef("Red-dragon-hide boots", "A boots made from red dragon hide", "", 128, 223, "items:223", false, true, 512, 0x8c2620, false, false, true, 1932));
		items.add(new ItemDef("Red-dragon-hide chaps", "A chaps made from red dragon hide", "", 192, 590, "items:590", false, true, 128, 0x8c2620, false, false, true, 1933));
		items.add(new ItemDef("Red-dragon-hide cuirass", "A cuirass made from red dragon hide", "", 256, 7, "items:7", false, true, 64, 0x8c2620, false, false, true, 1934));
		items.add(new ItemDef("Black-demon-hide coif", "A coif made from black demon hide", "", 64, 5, "items:5", false, true, 32, 0x24191b, false, false, true, 1935));
		items.add(new ItemDef("Black-demon-hide gloves", "A gloves made from black demon hide", "", 128, 17, "items:17", false, true, 256, 0x24191b, false, false, true, 1936));
		items.add(new ItemDef("Black-demon-hide boots", "A boots made from black demon hide", "", 128, 223, "items:223", false, true, 512, 0x24191b, false, false, true, 1937));
		items.add(new ItemDef("Black-demon-hide chaps", "A chaps made from black demon hide", "", 192, 590, "items:590", false, true, 128, 0x24191b, false, false, true, 1938));
		items.add(new ItemDef("Black-demon-hide cuirass", "A cuirass made from black demon hide", "", 256, 7, "items:7", false, true, 64, 0x24191b, false, false, true, 1939));
		items.add(new ItemDef("Black-dragon-hide coif", "A coif made from black dragon hide", "", 72, 5, "items:5", false, true, 32, 0x1f2530, false, false, true, 1940));
		items.add(new ItemDef("Black-dragon-hide gloves", "A gloves made from black dragon hide", "", 144, 17, "items:17", false, true, 256, 0x1f2530, false, false, true, 1941));
		items.add(new ItemDef("Black-dragon-hide boots", "A boots made from black dragon hide", "", 144, 223, "items:223", false, true, 512, 0x1f2530, false, false, true, 1942));
		items.add(new ItemDef("Black-dragon-hide chaps", "A chaps made from black dragon hide", "", 216, 590, "items:590", false, true, 128, 0x1f2530, false, false, true, 1943));
		items.add(new ItemDef("Black-dragon-hide cuirass", "A cuirass made from black dragon hide", "", 288, 7, "items:7", false, true, 64, 0x1f2530, false, false, true, 1944));
		items.add(new ItemDef("Balrog-hide coif", "A coif made from balrog hide", "", 72, 5, "items:5", false, true, 32, 0x35241f, false, false, true, 1945));
		items.add(new ItemDef("Balrog-hide gloves", "A gloves made from balrog hide", "", 144, 17, "items:17", false, true, 256, 0x35241f, false, false, true, 1946));
		items.add(new ItemDef("Balrog-hide boots", "A boots made from balrog hide", "", 144, 223, "items:223", false, true, 512, 0x35241f, false, false, true, 1947));
		items.add(new ItemDef("Balrog-hide chaps", "A chaps made from balrog hide", "", 216, 590, "items:590", false, true, 128, 0x35241f, false, false, true, 1948));
		items.add(new ItemDef("Balrog-hide cuirass", "A cuirass made from balrog hide", "", 288, 7, "items:7", false, true, 64, 0x35241f, false, false, true, 1949));
		items.add(new ItemDef("King-black-dragon-hide coif", "A coif made from king black dragon hide", "", 80, 5, "items:5", false, true, 32, 0x171b20, false, false, true, 1950));
		items.add(new ItemDef("King-black-dragon-hide gloves", "A gloves made from king black dragon hide", "", 160, 17, "items:17", false, true, 256, 0x171b20, false, false, true, 1951));
		items.add(new ItemDef("King-black-dragon-hide boots", "A boots made from king black dragon hide", "", 160, 223, "items:223", false, true, 512, 0x171b20, false, false, true, 1952));
		items.add(new ItemDef("King-black-dragon-hide chaps", "A chaps made from king black dragon hide", "", 240, 590, "items:590", false, true, 128, 0x171b20, false, false, true, 1953));
		items.add(new ItemDef("King-black-dragon-hide cuirass", "A cuirass made from king black dragon hide", "", 320, 7, "items:7", false, true, 64, 0x171b20, false, false, true, 1954));
		applyMyWorldLeatherArmorDescriptions();
		items.add(new ItemDef("Tin bar", "A soft light metal bar", "", 3, 79, "items:79", false, false, 0, 0xb7c9d9, false, false, true, 1955));
		items.add(new ItemDef("Copper bar", "A bright orange metal bar", "", 4, 79, "items:79", false, false, 0, 0xc86a2b, false, false, true, 1956));
		items.add(new ItemDef("Titan Steel bar", "A pale blue steel-alloy bar", "", 70, 79, "items:79", false, false, 0, 0x8ea6bb, false, false, true, 1957));
		items.add(new ItemDef("Orichalcum bar", "A dark violet alloy bar", "", 110, 79, "items:79", false, false, 0, 0x5a3f7d, false, false, true, 1958));
		items.add(new ItemDef("Tin helmet", "A full face helmet", "", 24, 6, "items:6", false, true, 33, 0xb7c9d9, false, false, true, 1959));
		items.add(new ItemDef("Tin gauntlets", "Heavy hand protection", "", 48, 217, "items:217", false, true, 256, 0xb7c9d9, false, false, true, 1960));
		items.add(new ItemDef("Tin greaves", "Heavy foot protection", "", 48, 223, "items:223", false, true, 512, 0xb7c9d9, false, false, true, 1961));
		items.add(new ItemDef("Tin kite shield", "A large metal shield", "", 72, 2, "items:2", false, true, 8, 0xb7c9d9, false, false, true, 1962));
		items.add(new ItemDef("Tin plate mail legs", "These look pretty heavy", "", 72, 9, "items:9", false, true, 644, 0xb7c9d9, false, false, true, 1963));
		items.add(new ItemDef("Tin plate mail body", "Provides excellent protection", "", 96, 8, "items:8", false, true, 322, 0xb7c9d9, false, false, true, 1964));
		items.add(new ItemDef("Copper helmet", "A full face helmet", "", 36, 6, "items:6", false, true, 33, 0xc86a2b, false, false, true, 1965));
		items.add(new ItemDef("Copper gauntlets", "Heavy hand protection", "", 72, 217, "items:217", false, true, 256, 0xc86a2b, false, false, true, 1966));
		items.add(new ItemDef("Copper greaves", "Heavy foot protection", "", 72, 223, "items:223", false, true, 512, 0xc86a2b, false, false, true, 1967));
		items.add(new ItemDef("Copper kite shield", "A large metal shield", "", 108, 2, "items:2", false, true, 8, 0xc86a2b, false, false, true, 1968));
		items.add(new ItemDef("Copper plate mail legs", "These look pretty heavy", "", 108, 9, "items:9", false, true, 644, 0xc86a2b, false, false, true, 1969));
		items.add(new ItemDef("Copper plate mail body", "Provides excellent protection", "", 144, 8, "items:8", false, true, 322, 0xc86a2b, false, false, true, 1970));
		items.add(new ItemDef("Titan Steel helmet", "A full face helmet", "", 64, 6, "items:6", false, true, 33, 0x8ea6bb, false, false, true, 1971));
		items.add(new ItemDef("Titan Steel gauntlets", "Heavy hand protection", "", 128, 217, "items:217", false, true, 256, 0x8ea6bb, false, false, true, 1972));
		items.add(new ItemDef("Titan Steel greaves", "Heavy foot protection", "", 128, 223, "items:223", false, true, 512, 0x8ea6bb, false, false, true, 1973));
		items.add(new ItemDef("Titan Steel kite shield", "A large metal shield", "", 192, 2, "items:2", false, true, 8, 0x8ea6bb, false, false, true, 1974));
		items.add(new ItemDef("Titan Steel plate mail legs", "These look pretty heavy", "", 192, 9, "items:9", false, true, 644, 0x8ea6bb, false, false, true, 1975));
		items.add(new ItemDef("Titan Steel plate mail body", "Provides excellent protection", "", 256, 8, "items:8", false, true, 322, 0x8ea6bb, false, false, true, 1976));
		items.add(new ItemDef("Orichalcum helmet", "A full face helmet", "", 80, 6, "items:6", false, true, 33, 0x5a3f7d, false, false, true, 1977));
		items.add(new ItemDef("Orichalcum gauntlets", "Heavy hand protection", "", 160, 217, "items:217", false, true, 256, 0x5a3f7d, false, false, true, 1978));
		items.add(new ItemDef("Orichalcum greaves", "Heavy foot protection", "", 160, 223, "items:223", false, true, 512, 0x5a3f7d, false, false, true, 1979));
		items.add(new ItemDef("Orichalcum kite shield", "A large metal shield", "", 240, 2, "items:2", false, true, 8, 0x5a3f7d, false, false, true, 1980));
		items.add(new ItemDef("Orichalcum plate mail legs", "These look pretty heavy", "", 240, 9, "items:9", false, true, 644, 0x5a3f7d, false, false, true, 1981));
		items.add(new ItemDef("Orichalcum plate mail body", "Provides excellent protection", "", 320, 8, "items:8", false, true, 322, 0x5a3f7d, false, false, true, 1982));
		items.add(new ItemDef("Bronze gauntlets", "Heavy hand protection", "", 64, 217, "items:217", false, true, 256, 16737817, false, false, true, 1983));
		items.add(new ItemDef("Bronze greaves", "Heavy foot protection", "", 64, 223, "items:223", false, true, 512, 16737817, false, false, true, 1984));
		items.add(new ItemDef("Iron gauntlets", "Heavy hand protection", "", 80, 217, "items:217", false, true, 256, 15654365, false, false, true, 1985));
		items.add(new ItemDef("Iron greaves", "Heavy foot protection", "", 80, 223, "items:223", false, true, 512, 15654365, false, false, true, 1986));
		items.add(new ItemDef("Tin Pickaxe", "Used for mining", "", 10, 72, "items:72", false, false, 0, 0xb7c9d9, false, false, true, 1987));
		items.add(new ItemDef("Steel greaves", "Heavy foot protection", "", 96, 223, "items:223", false, true, 512, 15658734, false, false, true, 1988));
		items.add(new ItemDef("Mithril gauntlets", "Heavy hand protection", "", 112, 217, "items:217", false, true, 256, 10072780, false, false, true, 1989));
		items.add(new ItemDef("Mithril greaves", "Heavy foot protection", "", 112, 223, "items:223", false, true, 512, 10072780, false, false, true, 1990));
		items.add(new ItemDef("Adamantite gauntlets", "Heavy hand protection", "", 144, 217, "items:217", false, true, 256, 11717785, false, false, true, 1991));
		items.add(new ItemDef("Adamantite greaves", "Heavy foot protection", "", 144, 223, "items:223", false, true, 512, 11717785, false, false, true, 1992));
		items.add(new ItemDef("Rune gauntlets", "Heavy hand protection", "", 176, 217, "items:217", false, true, 256, 65535, false, false, true, 1993));
		items.add(new ItemDef("Rune greaves", "Heavy foot protection", "", 176, 223, "items:223", false, true, 512, 65535, false, false, true, 1994));
		items.add(new ItemDef("Tin dagger", "Short but pointy", "", 6, 80, "items:80", false, true, 16, 0xb7c9d9, false, false, true, 1995));
		items.add(new ItemDef("Tin throwing knife", "A finely balanced knife", "", 2, 80, "items:80", true, true, 16, 0xb7c9d9, false, false, false, 1996));
		items.add(new ItemDef("Tin Short Sword", "A razor sharp sword", "", 14, 1, "items:1", false, true, 16, 0xb7c9d9, false, false, true, 1997));
		items.add(new ItemDef("Tin Long Sword", "A razor sharp sword", "", 20, 81, "items:81", false, true, 16, 0xb7c9d9, false, false, true, 1998));
		items.add(new ItemDef("Tin Scimitar", "A vicious curved sword", "", 16, 83, "items:83", false, true, 16, 0xb7c9d9, false, false, true, 1999));
		items.add(new ItemDef("Tin 2-handed Sword", "A very large sword", "", 40, 82, "items:82", false, true, 8216, 0xb7c9d9, false, false, true, 2000));
		items.add(new ItemDef("Tin Hatchet", "A woodcutters axe", "", 10, 12, "items:12", false, false, 0, 0xb7c9d9, false, false, true, 2001));
		items.add(new ItemDef("Tin battle Axe", "A vicious looking axe", "", 28, 84, "items:84", false, true, 16, 0xb7c9d9, false, false, true, 2002));
		items.add(new ItemDef("Tin Mace", "A spiky mace", "", 12, 0, "items:0", false, true, 16, 0xb7c9d9, false, false, true, 2003));
		items.add(new ItemDef("Tin arrow heads", "Dangerous looking arrow heads - need shafts for flight", "", 1, 207, "items:207", true, false, 0, 0xb7c9d9, false, false, false, 2004));
		items.add(new ItemDef("Tin dart tips", "Dangerous looking dart tips - need feathers for flight", "", 1, 369, "items:369", true, false, 0, 0xb7c9d9, false, false, false, 2005));
		items.add(new ItemDef("Copper dagger", "Short but pointy", "", 12, 80, "items:80", false, true, 16, 0xc86a2b, false, false, true, 2006));
		items.add(new ItemDef("Copper throwing knife", "A finely balanced knife", "", 4, 80, "items:80", true, true, 16, 0xc86a2b, false, false, false, 2007));
		items.add(new ItemDef("Copper Short Sword", "A razor sharp sword", "", 24, 1, "items:1", false, true, 16, 0xc86a2b, false, false, true, 2008));
		items.add(new ItemDef("Copper Long Sword", "A razor sharp sword", "", 36, 81, "items:81", false, true, 16, 0xc86a2b, false, false, true, 2009));
		items.add(new ItemDef("Copper Scimitar", "A vicious curved sword", "", 30, 83, "items:83", false, true, 16, 0xc86a2b, false, false, true, 2010));
		items.add(new ItemDef("Copper 2-handed Sword", "A very large sword", "", 72, 82, "items:82", false, true, 8216, 0xc86a2b, false, false, true, 2011));
		items.add(new ItemDef("Copper Hatchet", "A woodcutters axe", "", 18, 12, "items:12", false, false, 0, 0xc86a2b, false, false, true, 2012));
		items.add(new ItemDef("Copper battle Axe", "A vicious looking axe", "", 50, 84, "items:84", false, true, 16, 0xc86a2b, false, false, true, 2013));
		items.add(new ItemDef("Copper Mace", "A spiky mace", "", 20, 0, "items:0", false, true, 16, 0xc86a2b, false, false, true, 2014));
		items.add(new ItemDef("Copper arrow heads", "Dangerous looking arrow heads - need shafts for flight", "", 2, 207, "items:207", true, false, 0, 0xc86a2b, false, false, false, 2015));
		items.add(new ItemDef("Copper dart tips", "Dangerous looking dart tips - need feathers for flight", "", 2, 369, "items:369", true, false, 0, 0xc86a2b, false, false, false, 2016));
		items.add(new ItemDef("Titan Steel dagger", "Short but pointy", "", 55, 80, "items:80", false, true, 16, 0x8ea6bb, false, false, true, 2017));
		items.add(new ItemDef("Titan Steel throwing knife", "A finely balanced knife", "", 18, 80, "items:80", true, true, 16, 0x8ea6bb, false, false, false, 2018));
		items.add(new ItemDef("Titan Steel Short Sword", "A razor sharp sword", "", 120, 1, "items:1", false, true, 16, 0x8ea6bb, false, false, true, 2019));
		items.add(new ItemDef("Titan Steel Long Sword", "A razor sharp sword", "", 180, 81, "items:81", false, true, 16, 0x8ea6bb, false, false, true, 2020));
		items.add(new ItemDef("Titan Steel Scimitar", "A vicious curved sword", "", 150, 83, "items:83", false, true, 16, 0x8ea6bb, false, false, true, 2021));
		items.add(new ItemDef("Titan Steel 2-handed Sword", "A very large sword", "", 360, 82, "items:82", false, true, 8216, 0x8ea6bb, false, false, true, 2022));
		items.add(new ItemDef("Titan Steel Hatchet", "A woodcutters axe", "", 90, 12, "items:12", false, false, 0, 0x8ea6bb, false, false, true, 2023));
		items.add(new ItemDef("Titan Steel battle Axe", "A vicious looking axe", "", 250, 84, "items:84", false, true, 16, 0x8ea6bb, false, false, true, 2024));
		items.add(new ItemDef("Titan Steel Mace", "A spiky mace", "", 100, 0, "items:0", false, true, 16, 0x8ea6bb, false, false, true, 2025));
		items.add(new ItemDef("Titan Steel arrow heads", "Dangerous looking arrow heads - need shafts for flight", "", 12, 207, "items:207", true, false, 0, 0x8ea6bb, false, false, false, 2026));
		items.add(new ItemDef("Titan Steel dart tips", "Dangerous looking dart tips - need feathers for flight", "", 12, 369, "items:369", true, false, 0, 0x8ea6bb, false, false, false, 2027));
		items.add(new ItemDef("Orichalcum dagger", "Short but pointy", "", 170, 80, "items:80", false, true, 16, 0x5a3f7d, false, false, true, 2028));
		items.add(new ItemDef("Orichalcum throwing knife", "A finely balanced knife", "", 54, 80, "items:80", true, true, 16, 0x5a3f7d, false, false, false, 2029));
		items.add(new ItemDef("Orichalcum Short Sword", "A razor sharp sword", "", 360, 1, "items:1", false, true, 16, 0x5a3f7d, false, false, true, 2030));
		items.add(new ItemDef("Orichalcum Long Sword", "A razor sharp sword", "", 540, 81, "items:81", false, true, 16, 0x5a3f7d, false, false, true, 2031));
		items.add(new ItemDef("Orichalcum Scimitar", "A vicious curved sword", "", 450, 83, "items:83", false, true, 16, 0x5a3f7d, false, false, true, 2032));
		items.add(new ItemDef("Orichalcum 2-handed Sword", "A very large sword", "", 1080, 82, "items:82", false, true, 8216, 0x5a3f7d, false, false, true, 2033));
		items.add(new ItemDef("Orichalcum Hatchet", "A woodcutters axe", "", 270, 12, "items:12", false, false, 0, 0x5a3f7d, false, false, true, 2034));
		items.add(new ItemDef("Orichalcum battle Axe", "A vicious looking axe", "", 750, 84, "items:84", false, true, 16, 0x5a3f7d, false, false, true, 2035));
		items.add(new ItemDef("Orichalcum Mace", "A spiky mace", "", 300, 0, "items:0", false, true, 16, 0x5a3f7d, false, false, true, 2036));
		items.add(new ItemDef("Orichalcum arrow heads", "Dangerous looking arrow heads - need shafts for flight", "", 36, 207, "items:207", true, false, 0, 0x5a3f7d, false, false, false, 2037));
		items.add(new ItemDef("Orichalcum dart tips", "Dangerous looking dart tips - need feathers for flight", "", 36, 369, "items:369", true, false, 0, 0x5a3f7d, false, false, false, 2038));
		items.add(new ItemDef("Tin Arrows", "Arrows with newly forged heads", "", 1, 11, "items:11", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 0xb7c9d9, false, false, false, 2039));
		items.add(new ItemDef("Copper Arrows", "Arrows with newly forged heads", "", 3, 11, "items:11", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 0xc86a2b, false, false, false, 2040));
		items.add(new ItemDef("Titan Steel Arrows", "Arrows with newly forged heads", "", 96, 11, "items:11", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 0x8ea6bb, false, false, false, 2041));
		items.add(new ItemDef("Orichalcum Arrows", "Arrows with newly forged heads", "", 288, 11, "items:11", true, Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1000 : 0, 0x5a3f7d, false, false, false, 2042));
		items.add(new ItemDef("Tin Throwing Dart", "A deadly throwing dart", "", 1, 231, "items:231", true, true, 16, 0xb7c9d9, false, false, false, 2043));
		items.add(new ItemDef("Copper Throwing Dart", "A deadly throwing dart", "", 3, 231, "items:231", true, true, 16, 0xc86a2b, false, false, false, 2044));
		items.add(new ItemDef("Titan Steel Throwing Dart", "A deadly throwing dart", "", 96, 231, "items:231", true, true, 16, 0x8ea6bb, false, false, false, 2045));
		items.add(new ItemDef("Orichalcum Throwing Dart", "A deadly throwing dart", "", 288, 231, "items:231", true, true, 16, 0x5a3f7d, false, false, false, 2046));
		items.add(new ItemDef("Copper Pickaxe", "Used for mining", "", 18, 72, "items:72", false, false, 0, 0xc86a2b, false, false, true, 2047));
		items.add(new ItemDef("Titan Steel Pickaxe", "Used for mining", "", 90, 72, "items:72", false, false, 0, 0x8ea6bb, false, false, true, 2048));
		items.add(new ItemDef("Orichalcum Pickaxe", "Used for mining", "", 270, 72, "items:72", false, false, 0, 0x5a3f7d, false, false, true, 2049));
		items.add(new ItemDef("White dagger", "Short but pointy", "", 240, 80, "items:80", false, true, 16, 0xE7E2D6, false, false, true, 2151));
		items.add(new ItemDef("White Short Sword", "A razor sharp sword", "", 624, 1, "items:1", false, true, 16, 0xE7E2D6, false, false, true, 2152));
		items.add(new ItemDef("White Long Sword", "A razor sharp sword", "", 960, 81, "items:81", false, true, 16, 0xE7E2D6, false, false, true, 2153));
		items.add(new ItemDef("White 2-handed Sword", "A very large sword", "", 1920, 82, "items:82", false, true, 8216, 0xE7E2D6, false, false, true, 2154));
		items.add(new ItemDef("White Scimitar", "A vicious curved sword", "", 768, 83, "items:83", false, true, 16, 0xE7E2D6, false, false, true, 2155));
		items.add(new ItemDef("White battle Axe", "A vicious looking axe", "", 1248, 84, "items:84", false, true, 16, 0xE7E2D6, false, false, true, 2156));
		items.add(new ItemDef("White Mace", "A spiky mace", "", 432, 0, "items:0", false, true, 16, 0xE7E2D6, false, false, true, 2157));
		items.add(new ItemDef("Large White Helmet", "A full face helmet", "", 1056, 6, "items:6", false, true, 33, 0xE7E2D6, false, false, true, 2158));
		items.add(new ItemDef("Medium White Helmet", "A medium sized helmet", "", 576, 5, "items:5", false, true, 32, 0xE7E2D6, false, false, true, 2159));
		items.add(new ItemDef("White Chain Mail Body", "A series of connected metal rings", "", 1440, 7, "items:7", false, true, 64, 0xE7E2D6, false, false, true, 2160));
		items.add(new ItemDef("White Square Shield", "A medium metal shield", "", 1152, 3, "items:3", false, true, 8, 0xE7E2D6, false, false, true, 2161));
		items.add(new ItemDef("White Kite Shield", "A large metal shield", "", 1632, 2, "items:2", false, true, 8, 0xE7E2D6, false, false, true, 2162));
		items.add(new ItemDef("White Plate Mail Body", "Provides excellent protection", "", 3840, 8, "items:8", false, true, 322, 0xE7E2D6, false, false, true, 2163));
		items.add(new ItemDef("White Plate Mail Legs", "These look pretty heavy", "", 1920, 9, "items:9", false, true, 644, 0xE7E2D6, false, false, true, 2164));
		items.add(new ItemDef("White Plate Mail Body", "Provides excellent protection", "", 3840, 8, "items:8", false, true, 322, 0xE7E2D6, false, false, true, 2165));
		items.add(new ItemDef("White Plate Mail Legs", "These look pretty heavy", "", 1920, 9, "items:9", false, true, 644, 0xE7E2D6, false, false, true, 2166));
		items.add(new ItemDef("White Chain Mail Legs", "A series of connected metal rings", "", 720, 590, "items:590", false, true, 128, 0xE7E2D6, false, false, true, 2167));
		items.add(new ItemDef("White Chain Mail Top", "A series of connected metal rings", "", 1440, 595, "items:595", false, true, 64, 0xE7E2D6, false, false, true, 2168));

		// Custom certificate names
		if (Config.S_WANT_BANK_NOTES && !Config.S_WANT_CERT_AS_NOTES) {
			for (int i : new int[]{1543, 1546, 1547, 1548, 1549, 1550, 1551, 1552}) {
				items.get(i).name = items.get(i).getName() + " (market)";
			}
		}

		// Add muddy herb sprite
		items.get(165).spriteLocation = "items:588";
		items.get(435).spriteLocation = "items:588";
		items.get(436).spriteLocation = "items:588";
		items.get(437).spriteLocation = "items:588";
		items.get(438).spriteLocation = "items:588";
		items.get(439).spriteLocation = "items:588";
		items.get(440).spriteLocation = "items:588";
		items.get(441).spriteLocation = "items:588";
		items.get(442).spriteLocation = "items:588";
		items.get(443).spriteLocation = "items:588";
		items.get(815).spriteLocation = "items:588";
		items.get(817).spriteLocation = "items:588";
		items.get(819).spriteLocation = "items:588";
		items.get(821).spriteLocation = "items:588";
		items.get(823).spriteLocation = "items:588";
		items.get(933).spriteLocation = "items:588";

		// Custom logs sprite
		items.get(632).spriteLocation = "items:506";
		items.get(633).spriteLocation = "items:507";
		items.get(634).spriteLocation = "items:505";
		items.get(635).spriteLocation = "items:508";
		items.get(636).spriteLocation = "items:504";
	}

	private static void addCustomWoodItemDefinitions() {
		addWoodCrossbowDefinition("Crossbow", 60, 70, 0x00FF00, 0x824A22);
		addWoodCrossbowDefinition("Pine Crossbow", 59, 120, 0xCCAA33, 0xAC6F51);
		addWoodCrossbowDefinition("Oak Crossbow", 2169, 240, 0x0000FF, 0xAC6F51);
		addWoodCrossbowDefinition("Willow Crossbow", 2170, 480, 0xFFFF00, 0x886B29);
		addWoodCrossbowDefinition("Palm Crossbow", 2171, 960, 0xC78C4A, 0x8B5B31);
		addWoodCrossbowDefinition("Maple Crossbow", 2172, 1920, 0xFF8800, 0x8B5B31);
		addWoodCrossbowDefinition("Yew Crossbow", 2173, 3840, 0xFF0000, 0x9D5300);
		addWoodCrossbowDefinition("Ebony Crossbow", 2174, 5760, 0x333333, 0x6B4A2D);
		addWoodCrossbowDefinition("Magic Crossbow", 2175, 7680, 0x404040, 0x91A1);
		addWoodCrossbowDefinition("Blood Crossbow", 2176, 11520, 0xCC2222, 0x5A0F0F);

		addCustomWoodBowDefinitions("Pine", 2115, 2116, 2117, 2118, 70, 45, 120, 75, 0xCCAA33, 0xAC6F51);
		addCustomWoodBowDefinitions("Palm", 2119, 2120, 2121, 2122, 240, 150, 480, 300, 0xC78C4A, 0x8B5B31);
		addCustomWoodBowDefinitions("Ebony", 2123, 2124, 2125, 2126, 960, 600, 1920, 1200, 0x333333, 0x6B4A2D);
		addCustomWoodBowDefinitions("Blood", 2127, 2128, 2129, 2130, 1920, 1200, 3840, 2400, 0xCC2222, 0x5A0F0F);

		addCustomWoodStaffDefinitions("Oak", "oak", 1764, 1765, 1766, 1767, 1768, 250, 1750, 0xB97A57);
		addCustomWoodStaffDefinitions("Willow", "willow", 1769, 1770, 1771, 1772, 1773, 500, 2000, 0x7D9B5A);
		addCustomWoodStaffDefinitions("Maple", "maple", 1774, 1775, 1776, 1777, 1778, 1000, 2500, 0xA0522D);
		addCustomWoodStaffDefinitions("Yew", "yew", 1779, 1780, 1781, 1782, 1783, 2500, 4000, 0x6B4F2A);
		addCustomWoodStaffDefinitions("Magic", "magic-wood", 1784, 1785, 1786, 1787, 1788, 5000, 6500, 0x4B2E83);
		addCustomWoodStaffDefinitions("Pine", "pine", 2131, 2132, 2133, 2134, 2135, 125, 875, 0xCCAA33);
		addCustomWoodStaffDefinitions("Palm", "palm", 2136, 2137, 2138, 2139, 2140, 750, 5250, 0xC78C4A);
		addCustomWoodStaffDefinitions("Ebony", "ebony", 2141, 2142, 2143, 2144, 2145, 3750, 26250, 0x333333);
		addCustomWoodStaffDefinitions("Blood", "blood-wood", 2146, 2147, 2148, 2149, 2150, 7500, 52500, 0xCC2222);

		addFishingRodDefinition("Pine Fishing Rod", "A fishing rod shaped from pine logs.", 2682, 25, 0xCCAA33, 0xAC6F51);
		addFishingRodDefinition("Oak Fishing Rod", "A fishing rod shaped from oak logs.", 2683, 50, 0xB97A57, 0xAC6F51);
		addFishingRodDefinition("Willow Fishing Rod", "A fishing rod shaped from willow logs.", 2684, 100, 0x7D9B5A, 0x886B29);
		addFishingRodDefinition("Palm Fishing Rod", "A fishing rod shaped from palm logs.", 2685, 180, 0xC78C4A, 0x8B5B31);
		addFishingRodDefinition("Maple Fishing Rod", "A fishing rod shaped from maple logs.", 2686, 300, 0xA0522D, 0x8B5B31);
		addFishingRodDefinition("Yew Fishing Rod", "A fishing rod shaped from yew logs.", 2687, 500, 0x6B4F2A, 0x9D5300);
		addFishingRodDefinition("Ebony Fishing Rod", "A fishing rod shaped from ebony logs.", 2688, 850, 0x333333, 0x6B4A2D);
		addFishingRodDefinition("Magic Fishing Rod", "A fishing rod shaped from magic logs.", 2689, 1400, 0x4B2E83, 0x000000);
		addFishingRodDefinition("Blood Fishing Rod", "A fishing rod shaped from blood logs.", 2690, 2200, 0xCC2222, 0x5A0F0F);

		setCustomItemDefinition(2177, new ItemDef("Bolt mould", "Use with bars to cast bolts", "", 5, 128, "items:128", false, false, 0, 0, false, false, true, 2177));
		setCustomItemDefinition(2204, new ItemDef("Dart mould", "Use with bars to cast dart tips", "", 5, 129, "items:129", false, false, 0, 0, false, false, true, 2204));
		setCustomItemDefinition(2205, new ItemDef("Throwing knife mould", "Use with bars to cast throwing knives", "", 5, 127, "items:127", false, false, 0, 0, false, false, true, 2205));
		setCustomItemDefinition(2206, new ItemDef("Arrowhead mould", "Use with bars to cast arrow heads", "", 5, 132, "items:132", false, false, 0, 0, false, false, true, 2206));
		addMetalBoltDefinition("Tin bolts", 190, 3, 0xB7C9D9);
		addMetalBoltDefinition("Poison Tin bolts", 592, 3, 0xB7C9D9);
		addMetalBoltDefinition("Copper bolts", 2178, 4, 0xC86A2B);
		addMetalBoltDefinition("Poison Copper bolts", 2179, 4, 0xC86A2B);
		addMetalBoltDefinition("Bronze bolts", 2180, 6, 0xFF6C29);
		addMetalBoltDefinition("Poison Bronze bolts", 2181, 6, 0xFF6C29);
		addMetalBoltDefinition("Iron bolts", 2182, 8, 0xEEEEED);
		addMetalBoltDefinition("Poison Iron bolts", 2183, 8, 0xEEEEED);
		addMetalBoltDefinition("Steel bolts", 2184, 12, 0xEEEEEE);
		addMetalBoltDefinition("Poison Steel bolts", 2185, 12, 0xEEEEEE);
		addMetalBoltDefinition("Mithril bolts", 2186, 16, 0x92B5D8);
		addMetalBoltDefinition("Poison Mithril bolts", 2187, 16, 0x92B5D8);
		addMetalBoltDefinition("Titan Steel bolts", 2188, 18, 0x8EA6BB);
		addMetalBoltDefinition("Poison Titan Steel bolts", 2189, 18, 0x8EA6BB);
		addMetalBoltDefinition("Adamantite bolts", 2190, 20, 0xB2CD89);
		addMetalBoltDefinition("Poison Adamantite bolts", 2191, 20, 0xB2CD89);
		addMetalBoltDefinition("Orichalcum bolts", 2192, 22, 0x5A3F7D);
		addMetalBoltDefinition("Poison Orichalcum bolts", 2193, 22, 0x5A3F7D);
		addMetalBoltDefinition("Rune bolts", 2194, 24, 0x00FFFF);
		addMetalBoltDefinition("Poison Rune bolts", 2195, 24, 0x00FFFF);
		items.add(new ItemDef("Poisoned Tin Throwing Dart", "A venomous throwing dart.", "", 1, 384, "items:384", true, true, 16, 0xb7c9d9, false, false, false, 2196));
		items.add(new ItemDef("Poisoned Copper Throwing Dart", "A venomous throwing dart.", "", 3, 384, "items:384", true, true, 16, 0xc86a2b, false, false, false, 2197));
		items.add(new ItemDef("Poisoned Titan Steel Throwing Dart", "A venomous throwing dart.", "", 96, 384, "items:384", true, true, 16, 0x8ea6bb, false, false, false, 2198));
		items.add(new ItemDef("Poisoned Orichalcum Throwing Dart", "A venomous throwing dart.", "", 288, 384, "items:384", true, true, 16, 0x5a3f7d, false, false, false, 2199));
		items.add(new ItemDef("Poisoned Tin throwing knife", "A finely balanced knife with a coating of venom", "", 2, 385, "items:385", true, true, 16, 0xb7c9d9, false, false, false, 2200));
		items.add(new ItemDef("Poisoned Copper throwing knife", "A finely balanced knife with a coating of venom", "", 4, 385, "items:385", true, true, 16, 0xc86a2b, false, false, false, 2201));
		items.add(new ItemDef("Poisoned Titan Steel throwing knife", "A finely balanced knife with a coating of venom", "", 18, 385, "items:385", true, true, 16, 0x8ea6bb, false, false, false, 2202));
		items.add(new ItemDef("Poisoned Orichalcum throwing knife", "A finely balanced knife with a coating of venom", "", 54, 385, "items:385", true, true, 16, 0x5a3f7d, false, false, false, 2203));
		setCustomItemDefinition(2050, new ItemDef("Wool Hat", "A plain pointed hat stitched from wool", "", 6, 86, "items:86", false, true, 32, 0xFFFFFF, false, false, true, 2050));
		setCustomItemDefinition(2051, new ItemDef("Wool Robe Top", "A simple robe top stitched from wool", "", 16, 87, "items:87", false, true, 64, 0xFFFFFF, false, false, true, 2051));
		setCustomItemDefinition(2052, new ItemDef("Wool Robe Bottom", "A simple robe bottom stitched from wool", "", 12, 88, "items:88", false, true, 128, 0xFFFFFF, false, false, true, 2052));
		setCustomItemDefinition(2053, new ItemDef("White Cape", "A plain cape ready for dye", "", 8, 59, "items:59", false, true, 2048, 0xFFFFFF, false, false, true, 2053));
		setCustomItemDefinition(2054, new ItemDef("Red wool wizard hat", "A red pointed hat stitched from wool", "", 6, 86, "items:86", false, true, 32, 0xFF0000, false, false, true, 2054));
		setCustomItemDefinition(2055, new ItemDef("Yellow wool wizard hat", "A yellow pointed hat stitched from wool", "", 6, 86, "items:86", false, true, 32, 0xFFFF00, false, false, true, 2055));
		setCustomItemDefinition(2056, new ItemDef("Blue wool wizard hat", "A blue pointed hat stitched from wool", "", 6, 86, "items:86", false, true, 32, 0x0000FF, false, false, true, 2056));
		setCustomItemDefinition(2057, new ItemDef("Orange wool wizard hat", "An orange pointed hat stitched from wool", "", 6, 86, "items:86", false, true, 32, 0xFF8800, false, false, true, 2057));
		setCustomItemDefinition(2058, new ItemDef("Green wool wizard hat", "A green pointed hat stitched from wool", "", 6, 86, "items:86", false, true, 32, 0x00AA00, false, false, true, 2058));
		setCustomItemDefinition(2059, new ItemDef("Purple wool wizard hat", "A purple pointed hat stitched from wool", "", 6, 86, "items:86", false, true, 32, 0xAA00AA, false, false, true, 2059));
		setCustomItemDefinition(2060, new ItemDef("Red wool robe top", "A red robe top stitched from wool", "", 16, 87, "items:87", false, true, 64, 0xFF0000, false, false, true, 2060));
		setCustomItemDefinition(2061, new ItemDef("Yellow wool robe top", "A yellow robe top stitched from wool", "", 16, 87, "items:87", false, true, 64, 0xFFFF00, false, false, true, 2061));
		setCustomItemDefinition(2062, new ItemDef("Blue wool robe top", "A blue robe top stitched from wool", "", 16, 87, "items:87", false, true, 64, 0x0000FF, false, false, true, 2062));
		setCustomItemDefinition(2063, new ItemDef("Orange wool robe top", "An orange robe top stitched from wool", "", 16, 87, "items:87", false, true, 64, 0xFF8800, false, false, true, 2063));
		setCustomItemDefinition(2064, new ItemDef("Green wool robe top", "A green robe top stitched from wool", "", 16, 87, "items:87", false, true, 64, 0x00AA00, false, false, true, 2064));
		setCustomItemDefinition(2065, new ItemDef("Purple wool robe top", "A purple robe top stitched from wool", "", 16, 87, "items:87", false, true, 64, 0xAA00AA, false, false, true, 2065));
		setCustomItemDefinition(2066, new ItemDef("Red wool robe skirt", "A red robe skirt stitched from wool", "", 12, 88, "items:88", false, true, 128, 0xFF0000, false, false, true, 2066));
		setCustomItemDefinition(2067, new ItemDef("Yellow wool robe skirt", "A yellow robe skirt stitched from wool", "", 12, 88, "items:88", false, true, 128, 0xFFFF00, false, false, true, 2067));
		setCustomItemDefinition(2068, new ItemDef("Blue wool robe skirt", "A blue robe skirt stitched from wool", "", 12, 88, "items:88", false, true, 128, 0x0000FF, false, false, true, 2068));
		setCustomItemDefinition(2069, new ItemDef("Orange wool robe skirt", "An orange robe skirt stitched from wool", "", 12, 88, "items:88", false, true, 128, 0xFF8800, false, false, true, 2069));
		setCustomItemDefinition(2070, new ItemDef("Green wool robe skirt", "A green robe skirt stitched from wool", "", 12, 88, "items:88", false, true, 128, 0x00AA00, false, false, true, 2070));
		setCustomItemDefinition(2071, new ItemDef("Purple wool robe skirt", "A purple robe skirt stitched from wool", "", 12, 88, "items:88", false, true, 128, 0xAA00AA, false, false, true, 2071));
		setCustomItemDefinition(2072, new ItemDef("Beginner's Air Wizard Hat", "A sky blue wizard hat attuned to the air altar", "", 6, 86, "items:86", false, true, 32, 0x87CEEB, false, false, true, 2072));
		setCustomItemDefinition(2331, new ItemDef("Novice Air Wizard Hat", "A sky blue wizard hat attuned to the air altar", "", 6, 86, "items:86", false, true, 32, 0x87CEEB, false, false, true, 2331));
		setCustomItemDefinition(2332, new ItemDef("Apprentice Air Wizard Hat", "A sky blue wizard hat attuned to the air altar", "", 6, 86, "items:86", false, true, 32, 0x87CEEB, false, false, true, 2332));
		setCustomItemDefinition(2333, new ItemDef("Adept Air Wizard Hat", "A sky blue wizard hat attuned to the air altar", "", 6, 86, "items:86", false, true, 32, 0x87CEEB, false, false, true, 2333));
		setCustomItemDefinition(2334, new ItemDef("Expert Air Wizard Hat", "A sky blue wizard hat attuned to the air altar", "", 6, 86, "items:86", false, true, 32, 0x87CEEB, false, false, true, 2334));
		setCustomItemDefinition(2335, new ItemDef("Master Air Wizard Hat", "A sky blue wizard hat attuned to the air altar", "", 6, 86, "items:86", false, true, 32, 0x87CEEB, false, false, true, 2335));
		setCustomItemDefinition(2336, new ItemDef("Mystic Air Wizard Hat", "A sky blue wizard hat attuned to the air altar", "", 6, 86, "items:86", false, true, 32, 0x87CEEB, false, false, true, 2336));
		setCustomItemDefinition(2337, new ItemDef("Arcane Air Wizard Hat", "A sky blue wizard hat attuned to the air altar", "", 6, 86, "items:86", false, true, 32, 0x87CEEB, false, false, true, 2337));
		setCustomItemDefinition(2338, new ItemDef("Elder Air Wizard Hat", "A sky blue wizard hat attuned to the air altar", "", 6, 86, "items:86", false, true, 32, 0x87CEEB, false, false, true, 2338));
		setCustomItemDefinition(2339, new ItemDef("Mythic Air Wizard Hat", "A sky blue wizard hat attuned to the air altar", "", 6, 86, "items:86", false, true, 32, 0x87CEEB, false, false, true, 2339));
		setCustomItemDefinition(2076, new ItemDef("Beginner's Mind Wizard Hat", "A brown-red wizard hat attuned to the mind altar", "", 6, 86, "items:86", false, true, 32, 0x8B4A3B, false, false, true, 2076));
		setCustomItemDefinition(2340, new ItemDef("Novice Mind Wizard Hat", "A brown-red wizard hat attuned to the mind altar", "", 6, 86, "items:86", false, true, 32, 0x8B4A3B, false, false, true, 2340));
		setCustomItemDefinition(2341, new ItemDef("Apprentice Mind Wizard Hat", "A brown-red wizard hat attuned to the mind altar", "", 6, 86, "items:86", false, true, 32, 0x8B4A3B, false, false, true, 2341));
		setCustomItemDefinition(2342, new ItemDef("Adept Mind Wizard Hat", "A brown-red wizard hat attuned to the mind altar", "", 6, 86, "items:86", false, true, 32, 0x8B4A3B, false, false, true, 2342));
		setCustomItemDefinition(2343, new ItemDef("Expert Mind Wizard Hat", "A brown-red wizard hat attuned to the mind altar", "", 6, 86, "items:86", false, true, 32, 0x8B4A3B, false, false, true, 2343));
		setCustomItemDefinition(2344, new ItemDef("Master Mind Wizard Hat", "A brown-red wizard hat attuned to the mind altar", "", 6, 86, "items:86", false, true, 32, 0x8B4A3B, false, false, true, 2344));
		setCustomItemDefinition(2345, new ItemDef("Mystic Mind Wizard Hat", "A brown-red wizard hat attuned to the mind altar", "", 6, 86, "items:86", false, true, 32, 0x8B4A3B, false, false, true, 2345));
		setCustomItemDefinition(2346, new ItemDef("Arcane Mind Wizard Hat", "A brown-red wizard hat attuned to the mind altar", "", 6, 86, "items:86", false, true, 32, 0x8B4A3B, false, false, true, 2346));
		setCustomItemDefinition(2347, new ItemDef("Elder Mind Wizard Hat", "A brown-red wizard hat attuned to the mind altar", "", 6, 86, "items:86", false, true, 32, 0x8B4A3B, false, false, true, 2347));
		setCustomItemDefinition(2348, new ItemDef("Mythic Mind Wizard Hat", "A brown-red wizard hat attuned to the mind altar", "", 6, 86, "items:86", false, true, 32, 0x8B4A3B, false, false, true, 2348));
		setCustomItemDefinition(2073, new ItemDef("Beginner's Water Wizard Hat", "A deep blue wizard hat attuned to the water altar", "", 6, 86, "items:86", false, true, 32, 0x1F4E8C, false, false, true, 2073));
		setCustomItemDefinition(2349, new ItemDef("Novice Water Wizard Hat", "A deep blue wizard hat attuned to the water altar", "", 6, 86, "items:86", false, true, 32, 0x1F4E8C, false, false, true, 2349));
		setCustomItemDefinition(2350, new ItemDef("Apprentice Water Wizard Hat", "A deep blue wizard hat attuned to the water altar", "", 6, 86, "items:86", false, true, 32, 0x1F4E8C, false, false, true, 2350));
		setCustomItemDefinition(2351, new ItemDef("Adept Water Wizard Hat", "A deep blue wizard hat attuned to the water altar", "", 6, 86, "items:86", false, true, 32, 0x1F4E8C, false, false, true, 2351));
		setCustomItemDefinition(2352, new ItemDef("Expert Water Wizard Hat", "A deep blue wizard hat attuned to the water altar", "", 6, 86, "items:86", false, true, 32, 0x1F4E8C, false, false, true, 2352));
		setCustomItemDefinition(2353, new ItemDef("Master Water Wizard Hat", "A deep blue wizard hat attuned to the water altar", "", 6, 86, "items:86", false, true, 32, 0x1F4E8C, false, false, true, 2353));
		setCustomItemDefinition(2354, new ItemDef("Mystic Water Wizard Hat", "A deep blue wizard hat attuned to the water altar", "", 6, 86, "items:86", false, true, 32, 0x1F4E8C, false, false, true, 2354));
		setCustomItemDefinition(2355, new ItemDef("Arcane Water Wizard Hat", "A deep blue wizard hat attuned to the water altar", "", 6, 86, "items:86", false, true, 32, 0x1F4E8C, false, false, true, 2355));
		setCustomItemDefinition(2356, new ItemDef("Elder Water Wizard Hat", "A deep blue wizard hat attuned to the water altar", "", 6, 86, "items:86", false, true, 32, 0x1F4E8C, false, false, true, 2356));
		setCustomItemDefinition(2357, new ItemDef("Mythic Water Wizard Hat", "A deep blue wizard hat attuned to the water altar", "", 6, 86, "items:86", false, true, 32, 0x1F4E8C, false, false, true, 2357));
		setCustomItemDefinition(2074, new ItemDef("Beginner's Earth Wizard Hat", "A brown wizard hat attuned to the earth altar", "", 6, 86, "items:86", false, true, 32, 0x7A5230, false, false, true, 2074));
		setCustomItemDefinition(2358, new ItemDef("Novice Earth Wizard Hat", "A brown wizard hat attuned to the earth altar", "", 6, 86, "items:86", false, true, 32, 0x7A5230, false, false, true, 2358));
		setCustomItemDefinition(2359, new ItemDef("Apprentice Earth Wizard Hat", "A brown wizard hat attuned to the earth altar", "", 6, 86, "items:86", false, true, 32, 0x7A5230, false, false, true, 2359));
		setCustomItemDefinition(2360, new ItemDef("Adept Earth Wizard Hat", "A brown wizard hat attuned to the earth altar", "", 6, 86, "items:86", false, true, 32, 0x7A5230, false, false, true, 2360));
		setCustomItemDefinition(2361, new ItemDef("Expert Earth Wizard Hat", "A brown wizard hat attuned to the earth altar", "", 6, 86, "items:86", false, true, 32, 0x7A5230, false, false, true, 2361));
		setCustomItemDefinition(2362, new ItemDef("Master Earth Wizard Hat", "A brown wizard hat attuned to the earth altar", "", 6, 86, "items:86", false, true, 32, 0x7A5230, false, false, true, 2362));
		setCustomItemDefinition(2363, new ItemDef("Mystic Earth Wizard Hat", "A brown wizard hat attuned to the earth altar", "", 6, 86, "items:86", false, true, 32, 0x7A5230, false, false, true, 2363));
		setCustomItemDefinition(2364, new ItemDef("Arcane Earth Wizard Hat", "A brown wizard hat attuned to the earth altar", "", 6, 86, "items:86", false, true, 32, 0x7A5230, false, false, true, 2364));
		setCustomItemDefinition(2365, new ItemDef("Elder Earth Wizard Hat", "A brown wizard hat attuned to the earth altar", "", 6, 86, "items:86", false, true, 32, 0x7A5230, false, false, true, 2365));
		setCustomItemDefinition(2366, new ItemDef("Mythic Earth Wizard Hat", "A brown wizard hat attuned to the earth altar", "", 6, 86, "items:86", false, true, 32, 0x7A5230, false, false, true, 2366));
		setCustomItemDefinition(2075, new ItemDef("Beginner's Fire Wizard Hat", "A red wizard hat attuned to the fire altar", "", 6, 86, "items:86", false, true, 32, 0xC62828, false, false, true, 2075));
		setCustomItemDefinition(2367, new ItemDef("Novice Fire Wizard Hat", "A red wizard hat attuned to the fire altar", "", 6, 86, "items:86", false, true, 32, 0xC62828, false, false, true, 2367));
		setCustomItemDefinition(2368, new ItemDef("Apprentice Fire Wizard Hat", "A red wizard hat attuned to the fire altar", "", 6, 86, "items:86", false, true, 32, 0xC62828, false, false, true, 2368));
		setCustomItemDefinition(2369, new ItemDef("Adept Fire Wizard Hat", "A red wizard hat attuned to the fire altar", "", 6, 86, "items:86", false, true, 32, 0xC62828, false, false, true, 2369));
		setCustomItemDefinition(2370, new ItemDef("Expert Fire Wizard Hat", "A red wizard hat attuned to the fire altar", "", 6, 86, "items:86", false, true, 32, 0xC62828, false, false, true, 2370));
		setCustomItemDefinition(2371, new ItemDef("Master Fire Wizard Hat", "A red wizard hat attuned to the fire altar", "", 6, 86, "items:86", false, true, 32, 0xC62828, false, false, true, 2371));
		setCustomItemDefinition(2372, new ItemDef("Mystic Fire Wizard Hat", "A red wizard hat attuned to the fire altar", "", 6, 86, "items:86", false, true, 32, 0xC62828, false, false, true, 2372));
		setCustomItemDefinition(2373, new ItemDef("Arcane Fire Wizard Hat", "A red wizard hat attuned to the fire altar", "", 6, 86, "items:86", false, true, 32, 0xC62828, false, false, true, 2373));
		setCustomItemDefinition(2374, new ItemDef("Elder Fire Wizard Hat", "A red wizard hat attuned to the fire altar", "", 6, 86, "items:86", false, true, 32, 0xC62828, false, false, true, 2374));
		setCustomItemDefinition(2375, new ItemDef("Mythic Fire Wizard Hat", "A red wizard hat attuned to the fire altar", "", 6, 86, "items:86", false, true, 32, 0xC62828, false, false, true, 2375));
		setCustomItemDefinition(2077, new ItemDef("Beginner's Body Wizard Hat", "A tan wizard hat attuned to the body altar", "", 6, 86, "items:86", false, true, 32, 0xC2A57A, false, false, true, 2077));
		setCustomItemDefinition(2376, new ItemDef("Novice Body Wizard Hat", "A tan wizard hat attuned to the body altar", "", 6, 86, "items:86", false, true, 32, 0xC2A57A, false, false, true, 2376));
		setCustomItemDefinition(2377, new ItemDef("Apprentice Body Wizard Hat", "A tan wizard hat attuned to the body altar", "", 6, 86, "items:86", false, true, 32, 0xC2A57A, false, false, true, 2377));
		setCustomItemDefinition(2378, new ItemDef("Adept Body Wizard Hat", "A tan wizard hat attuned to the body altar", "", 6, 86, "items:86", false, true, 32, 0xC2A57A, false, false, true, 2378));
		setCustomItemDefinition(2379, new ItemDef("Expert Body Wizard Hat", "A tan wizard hat attuned to the body altar", "", 6, 86, "items:86", false, true, 32, 0xC2A57A, false, false, true, 2379));
		setCustomItemDefinition(2380, new ItemDef("Master Body Wizard Hat", "A tan wizard hat attuned to the body altar", "", 6, 86, "items:86", false, true, 32, 0xC2A57A, false, false, true, 2380));
		setCustomItemDefinition(2381, new ItemDef("Mystic Body Wizard Hat", "A tan wizard hat attuned to the body altar", "", 6, 86, "items:86", false, true, 32, 0xC2A57A, false, false, true, 2381));
		setCustomItemDefinition(2382, new ItemDef("Arcane Body Wizard Hat", "A tan wizard hat attuned to the body altar", "", 6, 86, "items:86", false, true, 32, 0xC2A57A, false, false, true, 2382));
		setCustomItemDefinition(2383, new ItemDef("Elder Body Wizard Hat", "A tan wizard hat attuned to the body altar", "", 6, 86, "items:86", false, true, 32, 0xC2A57A, false, false, true, 2383));
		setCustomItemDefinition(2384, new ItemDef("Mythic Body Wizard Hat", "A tan wizard hat attuned to the body altar", "", 6, 86, "items:86", false, true, 32, 0xC2A57A, false, false, true, 2384));
		setCustomItemDefinition(2079, new ItemDef("Beginner's Cosmic Wizard Hat", "A yellow wizard hat attuned to the cosmic altar", "", 6, 86, "items:86", false, true, 32, 0xE0C341, false, false, true, 2079));
		setCustomItemDefinition(2385, new ItemDef("Novice Cosmic Wizard Hat", "A yellow wizard hat attuned to the cosmic altar", "", 6, 86, "items:86", false, true, 32, 0xE0C341, false, false, true, 2385));
		setCustomItemDefinition(2386, new ItemDef("Apprentice Cosmic Wizard Hat", "A yellow wizard hat attuned to the cosmic altar", "", 6, 86, "items:86", false, true, 32, 0xE0C341, false, false, true, 2386));
		setCustomItemDefinition(2387, new ItemDef("Adept Cosmic Wizard Hat", "A yellow wizard hat attuned to the cosmic altar", "", 6, 86, "items:86", false, true, 32, 0xE0C341, false, false, true, 2387));
		setCustomItemDefinition(2388, new ItemDef("Expert Cosmic Wizard Hat", "A yellow wizard hat attuned to the cosmic altar", "", 6, 86, "items:86", false, true, 32, 0xE0C341, false, false, true, 2388));
		setCustomItemDefinition(2389, new ItemDef("Master Cosmic Wizard Hat", "A yellow wizard hat attuned to the cosmic altar", "", 6, 86, "items:86", false, true, 32, 0xE0C341, false, false, true, 2389));
		setCustomItemDefinition(2390, new ItemDef("Mystic Cosmic Wizard Hat", "A yellow wizard hat attuned to the cosmic altar", "", 6, 86, "items:86", false, true, 32, 0xE0C341, false, false, true, 2390));
		setCustomItemDefinition(2391, new ItemDef("Arcane Cosmic Wizard Hat", "A yellow wizard hat attuned to the cosmic altar", "", 6, 86, "items:86", false, true, 32, 0xE0C341, false, false, true, 2391));
		setCustomItemDefinition(2392, new ItemDef("Elder Cosmic Wizard Hat", "A yellow wizard hat attuned to the cosmic altar", "", 6, 86, "items:86", false, true, 32, 0xE0C341, false, false, true, 2392));
		setCustomItemDefinition(2393, new ItemDef("Mythic Cosmic Wizard Hat", "A yellow wizard hat attuned to the cosmic altar", "", 6, 86, "items:86", false, true, 32, 0xE0C341, false, false, true, 2393));
		setCustomItemDefinition(2078, new ItemDef("Beginner's Chaos Wizard Hat", "A orange wizard hat attuned to the chaos altar", "", 6, 86, "items:86", false, true, 32, 0xF57C00, false, false, true, 2078));
		setCustomItemDefinition(2394, new ItemDef("Novice Chaos Wizard Hat", "A orange wizard hat attuned to the chaos altar", "", 6, 86, "items:86", false, true, 32, 0xF57C00, false, false, true, 2394));
		setCustomItemDefinition(2395, new ItemDef("Apprentice Chaos Wizard Hat", "A orange wizard hat attuned to the chaos altar", "", 6, 86, "items:86", false, true, 32, 0xF57C00, false, false, true, 2395));
		setCustomItemDefinition(2396, new ItemDef("Adept Chaos Wizard Hat", "A orange wizard hat attuned to the chaos altar", "", 6, 86, "items:86", false, true, 32, 0xF57C00, false, false, true, 2396));
		setCustomItemDefinition(2397, new ItemDef("Expert Chaos Wizard Hat", "A orange wizard hat attuned to the chaos altar", "", 6, 86, "items:86", false, true, 32, 0xF57C00, false, false, true, 2397));
		setCustomItemDefinition(2398, new ItemDef("Master Chaos Wizard Hat", "A orange wizard hat attuned to the chaos altar", "", 6, 86, "items:86", false, true, 32, 0xF57C00, false, false, true, 2398));
		setCustomItemDefinition(2399, new ItemDef("Mystic Chaos Wizard Hat", "A orange wizard hat attuned to the chaos altar", "", 6, 86, "items:86", false, true, 32, 0xF57C00, false, false, true, 2399));
		setCustomItemDefinition(2400, new ItemDef("Arcane Chaos Wizard Hat", "A orange wizard hat attuned to the chaos altar", "", 6, 86, "items:86", false, true, 32, 0xF57C00, false, false, true, 2400));
		setCustomItemDefinition(2401, new ItemDef("Elder Chaos Wizard Hat", "A orange wizard hat attuned to the chaos altar", "", 6, 86, "items:86", false, true, 32, 0xF57C00, false, false, true, 2401));
		setCustomItemDefinition(2402, new ItemDef("Mythic Chaos Wizard Hat", "A orange wizard hat attuned to the chaos altar", "", 6, 86, "items:86", false, true, 32, 0xF57C00, false, false, true, 2402));
		setCustomItemDefinition(2080, new ItemDef("Beginner's Nature Wizard Hat", "A green wizard hat attuned to the nature altar", "", 6, 86, "items:86", false, true, 32, 0x2E7D32, false, false, true, 2080));
		setCustomItemDefinition(2403, new ItemDef("Novice Nature Wizard Hat", "A green wizard hat attuned to the nature altar", "", 6, 86, "items:86", false, true, 32, 0x2E7D32, false, false, true, 2403));
		setCustomItemDefinition(2404, new ItemDef("Apprentice Nature Wizard Hat", "A green wizard hat attuned to the nature altar", "", 6, 86, "items:86", false, true, 32, 0x2E7D32, false, false, true, 2404));
		setCustomItemDefinition(2405, new ItemDef("Adept Nature Wizard Hat", "A green wizard hat attuned to the nature altar", "", 6, 86, "items:86", false, true, 32, 0x2E7D32, false, false, true, 2405));
		setCustomItemDefinition(2406, new ItemDef("Expert Nature Wizard Hat", "A green wizard hat attuned to the nature altar", "", 6, 86, "items:86", false, true, 32, 0x2E7D32, false, false, true, 2406));
		setCustomItemDefinition(2407, new ItemDef("Master Nature Wizard Hat", "A green wizard hat attuned to the nature altar", "", 6, 86, "items:86", false, true, 32, 0x2E7D32, false, false, true, 2407));
		setCustomItemDefinition(2408, new ItemDef("Mystic Nature Wizard Hat", "A green wizard hat attuned to the nature altar", "", 6, 86, "items:86", false, true, 32, 0x2E7D32, false, false, true, 2408));
		setCustomItemDefinition(2409, new ItemDef("Arcane Nature Wizard Hat", "A green wizard hat attuned to the nature altar", "", 6, 86, "items:86", false, true, 32, 0x2E7D32, false, false, true, 2409));
		setCustomItemDefinition(2410, new ItemDef("Elder Nature Wizard Hat", "A green wizard hat attuned to the nature altar", "", 6, 86, "items:86", false, true, 32, 0x2E7D32, false, false, true, 2410));
		setCustomItemDefinition(2411, new ItemDef("Mythic Nature Wizard Hat", "A green wizard hat attuned to the nature altar", "", 6, 86, "items:86", false, true, 32, 0x2E7D32, false, false, true, 2411));
		setCustomItemDefinition(2081, new ItemDef("Beginner's Law Wizard Hat", "A teal wizard hat attuned to the law altar", "", 6, 86, "items:86", false, true, 32, 0x1B8A8F, false, false, true, 2081));
		setCustomItemDefinition(2412, new ItemDef("Novice Law Wizard Hat", "A teal wizard hat attuned to the law altar", "", 6, 86, "items:86", false, true, 32, 0x1B8A8F, false, false, true, 2412));
		setCustomItemDefinition(2413, new ItemDef("Apprentice Law Wizard Hat", "A teal wizard hat attuned to the law altar", "", 6, 86, "items:86", false, true, 32, 0x1B8A8F, false, false, true, 2413));
		setCustomItemDefinition(2414, new ItemDef("Adept Law Wizard Hat", "A teal wizard hat attuned to the law altar", "", 6, 86, "items:86", false, true, 32, 0x1B8A8F, false, false, true, 2414));
		setCustomItemDefinition(2415, new ItemDef("Expert Law Wizard Hat", "A teal wizard hat attuned to the law altar", "", 6, 86, "items:86", false, true, 32, 0x1B8A8F, false, false, true, 2415));
		setCustomItemDefinition(2416, new ItemDef("Master Law Wizard Hat", "A teal wizard hat attuned to the law altar", "", 6, 86, "items:86", false, true, 32, 0x1B8A8F, false, false, true, 2416));
		setCustomItemDefinition(2417, new ItemDef("Mystic Law Wizard Hat", "A teal wizard hat attuned to the law altar", "", 6, 86, "items:86", false, true, 32, 0x1B8A8F, false, false, true, 2417));
		setCustomItemDefinition(2418, new ItemDef("Arcane Law Wizard Hat", "A teal wizard hat attuned to the law altar", "", 6, 86, "items:86", false, true, 32, 0x1B8A8F, false, false, true, 2418));
		setCustomItemDefinition(2419, new ItemDef("Elder Law Wizard Hat", "A teal wizard hat attuned to the law altar", "", 6, 86, "items:86", false, true, 32, 0x1B8A8F, false, false, true, 2419));
		setCustomItemDefinition(2420, new ItemDef("Mythic Law Wizard Hat", "A teal wizard hat attuned to the law altar", "", 6, 86, "items:86", false, true, 32, 0x1B8A8F, false, false, true, 2420));
		setCustomItemDefinition(2082, new ItemDef("Beginner's Death Wizard Hat", "A black wizard hat attuned to the death altar", "", 6, 86, "items:86", false, true, 32, 0x222222, false, false, true, 2082));
		setCustomItemDefinition(2421, new ItemDef("Novice Death Wizard Hat", "A black wizard hat attuned to the death altar", "", 6, 86, "items:86", false, true, 32, 0x222222, false, false, true, 2421));
		setCustomItemDefinition(2422, new ItemDef("Apprentice Death Wizard Hat", "A black wizard hat attuned to the death altar", "", 6, 86, "items:86", false, true, 32, 0x222222, false, false, true, 2422));
		setCustomItemDefinition(2423, new ItemDef("Adept Death Wizard Hat", "A black wizard hat attuned to the death altar", "", 6, 86, "items:86", false, true, 32, 0x222222, false, false, true, 2423));
		setCustomItemDefinition(2424, new ItemDef("Expert Death Wizard Hat", "A black wizard hat attuned to the death altar", "", 6, 86, "items:86", false, true, 32, 0x222222, false, false, true, 2424));
		setCustomItemDefinition(2425, new ItemDef("Master Death Wizard Hat", "A black wizard hat attuned to the death altar", "", 6, 86, "items:86", false, true, 32, 0x222222, false, false, true, 2425));
		setCustomItemDefinition(2426, new ItemDef("Mystic Death Wizard Hat", "A black wizard hat attuned to the death altar", "", 6, 86, "items:86", false, true, 32, 0x222222, false, false, true, 2426));
		setCustomItemDefinition(2427, new ItemDef("Arcane Death Wizard Hat", "A black wizard hat attuned to the death altar", "", 6, 86, "items:86", false, true, 32, 0x222222, false, false, true, 2427));
		setCustomItemDefinition(2428, new ItemDef("Elder Death Wizard Hat", "A black wizard hat attuned to the death altar", "", 6, 86, "items:86", false, true, 32, 0x222222, false, false, true, 2428));
		setCustomItemDefinition(2429, new ItemDef("Mythic Death Wizard Hat", "A black wizard hat attuned to the death altar", "", 6, 86, "items:86", false, true, 32, 0x222222, false, false, true, 2429));
		setCustomItemDefinition(2084, new ItemDef("Beginner's Blood Wizard Hat", "A dark red wizard hat attuned to the blood altar", "", 6, 86, "items:86", false, true, 32, 0x7F1D1D, false, false, true, 2084));
		setCustomItemDefinition(2430, new ItemDef("Novice Blood Wizard Hat", "A dark red wizard hat attuned to the blood altar", "", 6, 86, "items:86", false, true, 32, 0x7F1D1D, false, false, true, 2430));
		setCustomItemDefinition(2431, new ItemDef("Apprentice Blood Wizard Hat", "A dark red wizard hat attuned to the blood altar", "", 6, 86, "items:86", false, true, 32, 0x7F1D1D, false, false, true, 2431));
		setCustomItemDefinition(2432, new ItemDef("Adept Blood Wizard Hat", "A dark red wizard hat attuned to the blood altar", "", 6, 86, "items:86", false, true, 32, 0x7F1D1D, false, false, true, 2432));
		setCustomItemDefinition(2433, new ItemDef("Expert Blood Wizard Hat", "A dark red wizard hat attuned to the blood altar", "", 6, 86, "items:86", false, true, 32, 0x7F1D1D, false, false, true, 2433));
		setCustomItemDefinition(2434, new ItemDef("Master Blood Wizard Hat", "A dark red wizard hat attuned to the blood altar", "", 6, 86, "items:86", false, true, 32, 0x7F1D1D, false, false, true, 2434));
		setCustomItemDefinition(2435, new ItemDef("Mystic Blood Wizard Hat", "A dark red wizard hat attuned to the blood altar", "", 6, 86, "items:86", false, true, 32, 0x7F1D1D, false, false, true, 2435));
		setCustomItemDefinition(2436, new ItemDef("Arcane Blood Wizard Hat", "A dark red wizard hat attuned to the blood altar", "", 6, 86, "items:86", false, true, 32, 0x7F1D1D, false, false, true, 2436));
		setCustomItemDefinition(2437, new ItemDef("Elder Blood Wizard Hat", "A dark red wizard hat attuned to the blood altar", "", 6, 86, "items:86", false, true, 32, 0x7F1D1D, false, false, true, 2437));
		setCustomItemDefinition(2438, new ItemDef("Mythic Blood Wizard Hat", "A dark red wizard hat attuned to the blood altar", "", 6, 86, "items:86", false, true, 32, 0x7F1D1D, false, false, true, 2438));
		setCustomItemDefinition(2083, new ItemDef("Beginner's Soul Wizard Hat", "A silver-blue wizard hat attuned to the soul altar", "", 6, 86, "items:86", false, true, 32, 0x8FA8C9, false, false, true, 2083));
		setCustomItemDefinition(2439, new ItemDef("Novice Soul Wizard Hat", "A silver-blue wizard hat attuned to the soul altar", "", 6, 86, "items:86", false, true, 32, 0x8FA8C9, false, false, true, 2439));
		setCustomItemDefinition(2440, new ItemDef("Apprentice Soul Wizard Hat", "A silver-blue wizard hat attuned to the soul altar", "", 6, 86, "items:86", false, true, 32, 0x8FA8C9, false, false, true, 2440));
		setCustomItemDefinition(2441, new ItemDef("Adept Soul Wizard Hat", "A silver-blue wizard hat attuned to the soul altar", "", 6, 86, "items:86", false, true, 32, 0x8FA8C9, false, false, true, 2441));
		setCustomItemDefinition(2442, new ItemDef("Expert Soul Wizard Hat", "A silver-blue wizard hat attuned to the soul altar", "", 6, 86, "items:86", false, true, 32, 0x8FA8C9, false, false, true, 2442));
		setCustomItemDefinition(2443, new ItemDef("Master Soul Wizard Hat", "A silver-blue wizard hat attuned to the soul altar", "", 6, 86, "items:86", false, true, 32, 0x8FA8C9, false, false, true, 2443));
		setCustomItemDefinition(2444, new ItemDef("Mystic Soul Wizard Hat", "A silver-blue wizard hat attuned to the soul altar", "", 6, 86, "items:86", false, true, 32, 0x8FA8C9, false, false, true, 2444));
		setCustomItemDefinition(2445, new ItemDef("Arcane Soul Wizard Hat", "A silver-blue wizard hat attuned to the soul altar", "", 6, 86, "items:86", false, true, 32, 0x8FA8C9, false, false, true, 2445));
		setCustomItemDefinition(2446, new ItemDef("Elder Soul Wizard Hat", "A silver-blue wizard hat attuned to the soul altar", "", 6, 86, "items:86", false, true, 32, 0x8FA8C9, false, false, true, 2446));
		setCustomItemDefinition(2447, new ItemDef("Mythic Soul Wizard Hat", "A silver-blue wizard hat attuned to the soul altar", "", 6, 86, "items:86", false, true, 32, 0x8FA8C9, false, false, true, 2447));
		setCustomItemDefinition(2085, new ItemDef("Beginner's Air Robe Top", "A sky blue robe top attuned to the air altar", "", 16, 87, "items:87", false, true, 64, 0x87CEEB, false, false, true, 2085));
		setCustomItemDefinition(2448, new ItemDef("Novice Air Robe Top", "A sky blue robe top attuned to the air altar", "", 16, 87, "items:87", false, true, 64, 0x87CEEB, false, false, true, 2448));
		setCustomItemDefinition(2449, new ItemDef("Apprentice Air Robe Top", "A sky blue robe top attuned to the air altar", "", 16, 87, "items:87", false, true, 64, 0x87CEEB, false, false, true, 2449));
		setCustomItemDefinition(2450, new ItemDef("Adept Air Robe Top", "A sky blue robe top attuned to the air altar", "", 16, 87, "items:87", false, true, 64, 0x87CEEB, false, false, true, 2450));
		setCustomItemDefinition(2451, new ItemDef("Expert Air Robe Top", "A sky blue robe top attuned to the air altar", "", 16, 87, "items:87", false, true, 64, 0x87CEEB, false, false, true, 2451));
		setCustomItemDefinition(2452, new ItemDef("Master Air Robe Top", "A sky blue robe top attuned to the air altar", "", 16, 87, "items:87", false, true, 64, 0x87CEEB, false, false, true, 2452));
		setCustomItemDefinition(2453, new ItemDef("Mystic Air Robe Top", "A sky blue robe top attuned to the air altar", "", 16, 87, "items:87", false, true, 64, 0x87CEEB, false, false, true, 2453));
		setCustomItemDefinition(2454, new ItemDef("Arcane Air Robe Top", "A sky blue robe top attuned to the air altar", "", 16, 87, "items:87", false, true, 64, 0x87CEEB, false, false, true, 2454));
		setCustomItemDefinition(2455, new ItemDef("Elder Air Robe Top", "A sky blue robe top attuned to the air altar", "", 16, 87, "items:87", false, true, 64, 0x87CEEB, false, false, true, 2455));
		setCustomItemDefinition(2456, new ItemDef("Mythic Air Robe Top", "A sky blue robe top attuned to the air altar", "", 16, 87, "items:87", false, true, 64, 0x87CEEB, false, false, true, 2456));
		setCustomItemDefinition(2089, new ItemDef("Beginner's Mind Robe Top", "A brown-red robe top attuned to the mind altar", "", 16, 87, "items:87", false, true, 64, 0x8B4A3B, false, false, true, 2089));
		setCustomItemDefinition(2457, new ItemDef("Novice Mind Robe Top", "A brown-red robe top attuned to the mind altar", "", 16, 87, "items:87", false, true, 64, 0x8B4A3B, false, false, true, 2457));
		setCustomItemDefinition(2458, new ItemDef("Apprentice Mind Robe Top", "A brown-red robe top attuned to the mind altar", "", 16, 87, "items:87", false, true, 64, 0x8B4A3B, false, false, true, 2458));
		setCustomItemDefinition(2459, new ItemDef("Adept Mind Robe Top", "A brown-red robe top attuned to the mind altar", "", 16, 87, "items:87", false, true, 64, 0x8B4A3B, false, false, true, 2459));
		setCustomItemDefinition(2460, new ItemDef("Expert Mind Robe Top", "A brown-red robe top attuned to the mind altar", "", 16, 87, "items:87", false, true, 64, 0x8B4A3B, false, false, true, 2460));
		setCustomItemDefinition(2461, new ItemDef("Master Mind Robe Top", "A brown-red robe top attuned to the mind altar", "", 16, 87, "items:87", false, true, 64, 0x8B4A3B, false, false, true, 2461));
		setCustomItemDefinition(2462, new ItemDef("Mystic Mind Robe Top", "A brown-red robe top attuned to the mind altar", "", 16, 87, "items:87", false, true, 64, 0x8B4A3B, false, false, true, 2462));
		setCustomItemDefinition(2463, new ItemDef("Arcane Mind Robe Top", "A brown-red robe top attuned to the mind altar", "", 16, 87, "items:87", false, true, 64, 0x8B4A3B, false, false, true, 2463));
		setCustomItemDefinition(2464, new ItemDef("Elder Mind Robe Top", "A brown-red robe top attuned to the mind altar", "", 16, 87, "items:87", false, true, 64, 0x8B4A3B, false, false, true, 2464));
		setCustomItemDefinition(2465, new ItemDef("Mythic Mind Robe Top", "A brown-red robe top attuned to the mind altar", "", 16, 87, "items:87", false, true, 64, 0x8B4A3B, false, false, true, 2465));
		setCustomItemDefinition(2086, new ItemDef("Beginner's Water Robe Top", "A deep blue robe top attuned to the water altar", "", 16, 87, "items:87", false, true, 64, 0x1F4E8C, false, false, true, 2086));
		setCustomItemDefinition(2466, new ItemDef("Novice Water Robe Top", "A deep blue robe top attuned to the water altar", "", 16, 87, "items:87", false, true, 64, 0x1F4E8C, false, false, true, 2466));
		setCustomItemDefinition(2467, new ItemDef("Apprentice Water Robe Top", "A deep blue robe top attuned to the water altar", "", 16, 87, "items:87", false, true, 64, 0x1F4E8C, false, false, true, 2467));
		setCustomItemDefinition(2468, new ItemDef("Adept Water Robe Top", "A deep blue robe top attuned to the water altar", "", 16, 87, "items:87", false, true, 64, 0x1F4E8C, false, false, true, 2468));
		setCustomItemDefinition(2469, new ItemDef("Expert Water Robe Top", "A deep blue robe top attuned to the water altar", "", 16, 87, "items:87", false, true, 64, 0x1F4E8C, false, false, true, 2469));
		setCustomItemDefinition(2470, new ItemDef("Master Water Robe Top", "A deep blue robe top attuned to the water altar", "", 16, 87, "items:87", false, true, 64, 0x1F4E8C, false, false, true, 2470));
		setCustomItemDefinition(2471, new ItemDef("Mystic Water Robe Top", "A deep blue robe top attuned to the water altar", "", 16, 87, "items:87", false, true, 64, 0x1F4E8C, false, false, true, 2471));
		setCustomItemDefinition(2472, new ItemDef("Arcane Water Robe Top", "A deep blue robe top attuned to the water altar", "", 16, 87, "items:87", false, true, 64, 0x1F4E8C, false, false, true, 2472));
		setCustomItemDefinition(2473, new ItemDef("Elder Water Robe Top", "A deep blue robe top attuned to the water altar", "", 16, 87, "items:87", false, true, 64, 0x1F4E8C, false, false, true, 2473));
		setCustomItemDefinition(2474, new ItemDef("Mythic Water Robe Top", "A deep blue robe top attuned to the water altar", "", 16, 87, "items:87", false, true, 64, 0x1F4E8C, false, false, true, 2474));
		setCustomItemDefinition(2087, new ItemDef("Beginner's Earth Robe Top", "A brown robe top attuned to the earth altar", "", 16, 87, "items:87", false, true, 64, 0x7A5230, false, false, true, 2087));
		setCustomItemDefinition(2475, new ItemDef("Novice Earth Robe Top", "A brown robe top attuned to the earth altar", "", 16, 87, "items:87", false, true, 64, 0x7A5230, false, false, true, 2475));
		setCustomItemDefinition(2476, new ItemDef("Apprentice Earth Robe Top", "A brown robe top attuned to the earth altar", "", 16, 87, "items:87", false, true, 64, 0x7A5230, false, false, true, 2476));
		setCustomItemDefinition(2477, new ItemDef("Adept Earth Robe Top", "A brown robe top attuned to the earth altar", "", 16, 87, "items:87", false, true, 64, 0x7A5230, false, false, true, 2477));
		setCustomItemDefinition(2478, new ItemDef("Expert Earth Robe Top", "A brown robe top attuned to the earth altar", "", 16, 87, "items:87", false, true, 64, 0x7A5230, false, false, true, 2478));
		setCustomItemDefinition(2479, new ItemDef("Master Earth Robe Top", "A brown robe top attuned to the earth altar", "", 16, 87, "items:87", false, true, 64, 0x7A5230, false, false, true, 2479));
		setCustomItemDefinition(2480, new ItemDef("Mystic Earth Robe Top", "A brown robe top attuned to the earth altar", "", 16, 87, "items:87", false, true, 64, 0x7A5230, false, false, true, 2480));
		setCustomItemDefinition(2481, new ItemDef("Arcane Earth Robe Top", "A brown robe top attuned to the earth altar", "", 16, 87, "items:87", false, true, 64, 0x7A5230, false, false, true, 2481));
		setCustomItemDefinition(2482, new ItemDef("Elder Earth Robe Top", "A brown robe top attuned to the earth altar", "", 16, 87, "items:87", false, true, 64, 0x7A5230, false, false, true, 2482));
		setCustomItemDefinition(2483, new ItemDef("Mythic Earth Robe Top", "A brown robe top attuned to the earth altar", "", 16, 87, "items:87", false, true, 64, 0x7A5230, false, false, true, 2483));
		setCustomItemDefinition(2088, new ItemDef("Beginner's Fire Robe Top", "A red robe top attuned to the fire altar", "", 16, 87, "items:87", false, true, 64, 0xC62828, false, false, true, 2088));
		setCustomItemDefinition(2484, new ItemDef("Novice Fire Robe Top", "A red robe top attuned to the fire altar", "", 16, 87, "items:87", false, true, 64, 0xC62828, false, false, true, 2484));
		setCustomItemDefinition(2485, new ItemDef("Apprentice Fire Robe Top", "A red robe top attuned to the fire altar", "", 16, 87, "items:87", false, true, 64, 0xC62828, false, false, true, 2485));
		setCustomItemDefinition(2486, new ItemDef("Adept Fire Robe Top", "A red robe top attuned to the fire altar", "", 16, 87, "items:87", false, true, 64, 0xC62828, false, false, true, 2486));
		setCustomItemDefinition(2487, new ItemDef("Expert Fire Robe Top", "A red robe top attuned to the fire altar", "", 16, 87, "items:87", false, true, 64, 0xC62828, false, false, true, 2487));
		setCustomItemDefinition(2488, new ItemDef("Master Fire Robe Top", "A red robe top attuned to the fire altar", "", 16, 87, "items:87", false, true, 64, 0xC62828, false, false, true, 2488));
		setCustomItemDefinition(2489, new ItemDef("Mystic Fire Robe Top", "A red robe top attuned to the fire altar", "", 16, 87, "items:87", false, true, 64, 0xC62828, false, false, true, 2489));
		setCustomItemDefinition(2490, new ItemDef("Arcane Fire Robe Top", "A red robe top attuned to the fire altar", "", 16, 87, "items:87", false, true, 64, 0xC62828, false, false, true, 2490));
		setCustomItemDefinition(2491, new ItemDef("Elder Fire Robe Top", "A red robe top attuned to the fire altar", "", 16, 87, "items:87", false, true, 64, 0xC62828, false, false, true, 2491));
		setCustomItemDefinition(2492, new ItemDef("Mythic Fire Robe Top", "A red robe top attuned to the fire altar", "", 16, 87, "items:87", false, true, 64, 0xC62828, false, false, true, 2492));
		setCustomItemDefinition(2090, new ItemDef("Beginner's Body Robe Top", "A tan robe top attuned to the body altar", "", 16, 87, "items:87", false, true, 64, 0xC2A57A, false, false, true, 2090));
		setCustomItemDefinition(2493, new ItemDef("Novice Body Robe Top", "A tan robe top attuned to the body altar", "", 16, 87, "items:87", false, true, 64, 0xC2A57A, false, false, true, 2493));
		setCustomItemDefinition(2494, new ItemDef("Apprentice Body Robe Top", "A tan robe top attuned to the body altar", "", 16, 87, "items:87", false, true, 64, 0xC2A57A, false, false, true, 2494));
		setCustomItemDefinition(2495, new ItemDef("Adept Body Robe Top", "A tan robe top attuned to the body altar", "", 16, 87, "items:87", false, true, 64, 0xC2A57A, false, false, true, 2495));
		setCustomItemDefinition(2496, new ItemDef("Expert Body Robe Top", "A tan robe top attuned to the body altar", "", 16, 87, "items:87", false, true, 64, 0xC2A57A, false, false, true, 2496));
		setCustomItemDefinition(2497, new ItemDef("Master Body Robe Top", "A tan robe top attuned to the body altar", "", 16, 87, "items:87", false, true, 64, 0xC2A57A, false, false, true, 2497));
		setCustomItemDefinition(2498, new ItemDef("Mystic Body Robe Top", "A tan robe top attuned to the body altar", "", 16, 87, "items:87", false, true, 64, 0xC2A57A, false, false, true, 2498));
		setCustomItemDefinition(2499, new ItemDef("Arcane Body Robe Top", "A tan robe top attuned to the body altar", "", 16, 87, "items:87", false, true, 64, 0xC2A57A, false, false, true, 2499));
		setCustomItemDefinition(2500, new ItemDef("Elder Body Robe Top", "A tan robe top attuned to the body altar", "", 16, 87, "items:87", false, true, 64, 0xC2A57A, false, false, true, 2500));
		setCustomItemDefinition(2501, new ItemDef("Mythic Body Robe Top", "A tan robe top attuned to the body altar", "", 16, 87, "items:87", false, true, 64, 0xC2A57A, false, false, true, 2501));
		setCustomItemDefinition(2092, new ItemDef("Beginner's Cosmic Robe Top", "A yellow robe top attuned to the cosmic altar", "", 16, 87, "items:87", false, true, 64, 0xE0C341, false, false, true, 2092));
		setCustomItemDefinition(2502, new ItemDef("Novice Cosmic Robe Top", "A yellow robe top attuned to the cosmic altar", "", 16, 87, "items:87", false, true, 64, 0xE0C341, false, false, true, 2502));
		setCustomItemDefinition(2503, new ItemDef("Apprentice Cosmic Robe Top", "A yellow robe top attuned to the cosmic altar", "", 16, 87, "items:87", false, true, 64, 0xE0C341, false, false, true, 2503));
		setCustomItemDefinition(2504, new ItemDef("Adept Cosmic Robe Top", "A yellow robe top attuned to the cosmic altar", "", 16, 87, "items:87", false, true, 64, 0xE0C341, false, false, true, 2504));
		setCustomItemDefinition(2505, new ItemDef("Expert Cosmic Robe Top", "A yellow robe top attuned to the cosmic altar", "", 16, 87, "items:87", false, true, 64, 0xE0C341, false, false, true, 2505));
		setCustomItemDefinition(2506, new ItemDef("Master Cosmic Robe Top", "A yellow robe top attuned to the cosmic altar", "", 16, 87, "items:87", false, true, 64, 0xE0C341, false, false, true, 2506));
		setCustomItemDefinition(2507, new ItemDef("Mystic Cosmic Robe Top", "A yellow robe top attuned to the cosmic altar", "", 16, 87, "items:87", false, true, 64, 0xE0C341, false, false, true, 2507));
		setCustomItemDefinition(2508, new ItemDef("Arcane Cosmic Robe Top", "A yellow robe top attuned to the cosmic altar", "", 16, 87, "items:87", false, true, 64, 0xE0C341, false, false, true, 2508));
		setCustomItemDefinition(2509, new ItemDef("Elder Cosmic Robe Top", "A yellow robe top attuned to the cosmic altar", "", 16, 87, "items:87", false, true, 64, 0xE0C341, false, false, true, 2509));
		setCustomItemDefinition(2510, new ItemDef("Mythic Cosmic Robe Top", "A yellow robe top attuned to the cosmic altar", "", 16, 87, "items:87", false, true, 64, 0xE0C341, false, false, true, 2510));
		setCustomItemDefinition(2091, new ItemDef("Beginner's Chaos Robe Top", "A orange robe top attuned to the chaos altar", "", 16, 87, "items:87", false, true, 64, 0xF57C00, false, false, true, 2091));
		setCustomItemDefinition(2511, new ItemDef("Novice Chaos Robe Top", "A orange robe top attuned to the chaos altar", "", 16, 87, "items:87", false, true, 64, 0xF57C00, false, false, true, 2511));
		setCustomItemDefinition(2512, new ItemDef("Apprentice Chaos Robe Top", "A orange robe top attuned to the chaos altar", "", 16, 87, "items:87", false, true, 64, 0xF57C00, false, false, true, 2512));
		setCustomItemDefinition(2513, new ItemDef("Adept Chaos Robe Top", "A orange robe top attuned to the chaos altar", "", 16, 87, "items:87", false, true, 64, 0xF57C00, false, false, true, 2513));
		setCustomItemDefinition(2514, new ItemDef("Expert Chaos Robe Top", "A orange robe top attuned to the chaos altar", "", 16, 87, "items:87", false, true, 64, 0xF57C00, false, false, true, 2514));
		setCustomItemDefinition(2515, new ItemDef("Master Chaos Robe Top", "A orange robe top attuned to the chaos altar", "", 16, 87, "items:87", false, true, 64, 0xF57C00, false, false, true, 2515));
		setCustomItemDefinition(2516, new ItemDef("Mystic Chaos Robe Top", "A orange robe top attuned to the chaos altar", "", 16, 87, "items:87", false, true, 64, 0xF57C00, false, false, true, 2516));
		setCustomItemDefinition(2517, new ItemDef("Arcane Chaos Robe Top", "A orange robe top attuned to the chaos altar", "", 16, 87, "items:87", false, true, 64, 0xF57C00, false, false, true, 2517));
		setCustomItemDefinition(2518, new ItemDef("Elder Chaos Robe Top", "A orange robe top attuned to the chaos altar", "", 16, 87, "items:87", false, true, 64, 0xF57C00, false, false, true, 2518));
		setCustomItemDefinition(2519, new ItemDef("Mythic Chaos Robe Top", "A orange robe top attuned to the chaos altar", "", 16, 87, "items:87", false, true, 64, 0xF57C00, false, false, true, 2519));
		setCustomItemDefinition(2093, new ItemDef("Beginner's Nature Robe Top", "A green robe top attuned to the nature altar", "", 16, 87, "items:87", false, true, 64, 0x2E7D32, false, false, true, 2093));
		setCustomItemDefinition(2520, new ItemDef("Novice Nature Robe Top", "A green robe top attuned to the nature altar", "", 16, 87, "items:87", false, true, 64, 0x2E7D32, false, false, true, 2520));
		setCustomItemDefinition(2521, new ItemDef("Apprentice Nature Robe Top", "A green robe top attuned to the nature altar", "", 16, 87, "items:87", false, true, 64, 0x2E7D32, false, false, true, 2521));
		setCustomItemDefinition(2522, new ItemDef("Adept Nature Robe Top", "A green robe top attuned to the nature altar", "", 16, 87, "items:87", false, true, 64, 0x2E7D32, false, false, true, 2522));
		setCustomItemDefinition(2523, new ItemDef("Expert Nature Robe Top", "A green robe top attuned to the nature altar", "", 16, 87, "items:87", false, true, 64, 0x2E7D32, false, false, true, 2523));
		setCustomItemDefinition(2524, new ItemDef("Master Nature Robe Top", "A green robe top attuned to the nature altar", "", 16, 87, "items:87", false, true, 64, 0x2E7D32, false, false, true, 2524));
		setCustomItemDefinition(2525, new ItemDef("Mystic Nature Robe Top", "A green robe top attuned to the nature altar", "", 16, 87, "items:87", false, true, 64, 0x2E7D32, false, false, true, 2525));
		setCustomItemDefinition(2526, new ItemDef("Arcane Nature Robe Top", "A green robe top attuned to the nature altar", "", 16, 87, "items:87", false, true, 64, 0x2E7D32, false, false, true, 2526));
		setCustomItemDefinition(2527, new ItemDef("Elder Nature Robe Top", "A green robe top attuned to the nature altar", "", 16, 87, "items:87", false, true, 64, 0x2E7D32, false, false, true, 2527));
		setCustomItemDefinition(2528, new ItemDef("Mythic Nature Robe Top", "A green robe top attuned to the nature altar", "", 16, 87, "items:87", false, true, 64, 0x2E7D32, false, false, true, 2528));
		setCustomItemDefinition(2094, new ItemDef("Beginner's Law Robe Top", "A teal robe top attuned to the law altar", "", 16, 87, "items:87", false, true, 64, 0x1B8A8F, false, false, true, 2094));
		setCustomItemDefinition(2529, new ItemDef("Novice Law Robe Top", "A teal robe top attuned to the law altar", "", 16, 87, "items:87", false, true, 64, 0x1B8A8F, false, false, true, 2529));
		setCustomItemDefinition(2530, new ItemDef("Apprentice Law Robe Top", "A teal robe top attuned to the law altar", "", 16, 87, "items:87", false, true, 64, 0x1B8A8F, false, false, true, 2530));
		setCustomItemDefinition(2531, new ItemDef("Adept Law Robe Top", "A teal robe top attuned to the law altar", "", 16, 87, "items:87", false, true, 64, 0x1B8A8F, false, false, true, 2531));
		setCustomItemDefinition(2532, new ItemDef("Expert Law Robe Top", "A teal robe top attuned to the law altar", "", 16, 87, "items:87", false, true, 64, 0x1B8A8F, false, false, true, 2532));
		setCustomItemDefinition(2533, new ItemDef("Master Law Robe Top", "A teal robe top attuned to the law altar", "", 16, 87, "items:87", false, true, 64, 0x1B8A8F, false, false, true, 2533));
		setCustomItemDefinition(2534, new ItemDef("Mystic Law Robe Top", "A teal robe top attuned to the law altar", "", 16, 87, "items:87", false, true, 64, 0x1B8A8F, false, false, true, 2534));
		setCustomItemDefinition(2535, new ItemDef("Arcane Law Robe Top", "A teal robe top attuned to the law altar", "", 16, 87, "items:87", false, true, 64, 0x1B8A8F, false, false, true, 2535));
		setCustomItemDefinition(2536, new ItemDef("Elder Law Robe Top", "A teal robe top attuned to the law altar", "", 16, 87, "items:87", false, true, 64, 0x1B8A8F, false, false, true, 2536));
		setCustomItemDefinition(2537, new ItemDef("Mythic Law Robe Top", "A teal robe top attuned to the law altar", "", 16, 87, "items:87", false, true, 64, 0x1B8A8F, false, false, true, 2537));
		setCustomItemDefinition(2095, new ItemDef("Beginner's Death Robe Top", "A black robe top attuned to the death altar", "", 16, 87, "items:87", false, true, 64, 0x222222, false, false, true, 2095));
		setCustomItemDefinition(2538, new ItemDef("Novice Death Robe Top", "A black robe top attuned to the death altar", "", 16, 87, "items:87", false, true, 64, 0x222222, false, false, true, 2538));
		setCustomItemDefinition(2539, new ItemDef("Apprentice Death Robe Top", "A black robe top attuned to the death altar", "", 16, 87, "items:87", false, true, 64, 0x222222, false, false, true, 2539));
		setCustomItemDefinition(2540, new ItemDef("Adept Death Robe Top", "A black robe top attuned to the death altar", "", 16, 87, "items:87", false, true, 64, 0x222222, false, false, true, 2540));
		setCustomItemDefinition(2541, new ItemDef("Expert Death Robe Top", "A black robe top attuned to the death altar", "", 16, 87, "items:87", false, true, 64, 0x222222, false, false, true, 2541));
		setCustomItemDefinition(2542, new ItemDef("Master Death Robe Top", "A black robe top attuned to the death altar", "", 16, 87, "items:87", false, true, 64, 0x222222, false, false, true, 2542));
		setCustomItemDefinition(2543, new ItemDef("Mystic Death Robe Top", "A black robe top attuned to the death altar", "", 16, 87, "items:87", false, true, 64, 0x222222, false, false, true, 2543));
		setCustomItemDefinition(2544, new ItemDef("Arcane Death Robe Top", "A black robe top attuned to the death altar", "", 16, 87, "items:87", false, true, 64, 0x222222, false, false, true, 2544));
		setCustomItemDefinition(2545, new ItemDef("Elder Death Robe Top", "A black robe top attuned to the death altar", "", 16, 87, "items:87", false, true, 64, 0x222222, false, false, true, 2545));
		setCustomItemDefinition(2546, new ItemDef("Mythic Death Robe Top", "A black robe top attuned to the death altar", "", 16, 87, "items:87", false, true, 64, 0x222222, false, false, true, 2546));
		setCustomItemDefinition(2097, new ItemDef("Beginner's Blood Robe Top", "A dark red robe top attuned to the blood altar", "", 16, 87, "items:87", false, true, 64, 0x7F1D1D, false, false, true, 2097));
		setCustomItemDefinition(2547, new ItemDef("Novice Blood Robe Top", "A dark red robe top attuned to the blood altar", "", 16, 87, "items:87", false, true, 64, 0x7F1D1D, false, false, true, 2547));
		setCustomItemDefinition(2548, new ItemDef("Apprentice Blood Robe Top", "A dark red robe top attuned to the blood altar", "", 16, 87, "items:87", false, true, 64, 0x7F1D1D, false, false, true, 2548));
		setCustomItemDefinition(2549, new ItemDef("Adept Blood Robe Top", "A dark red robe top attuned to the blood altar", "", 16, 87, "items:87", false, true, 64, 0x7F1D1D, false, false, true, 2549));
		setCustomItemDefinition(2550, new ItemDef("Expert Blood Robe Top", "A dark red robe top attuned to the blood altar", "", 16, 87, "items:87", false, true, 64, 0x7F1D1D, false, false, true, 2550));
		setCustomItemDefinition(2551, new ItemDef("Master Blood Robe Top", "A dark red robe top attuned to the blood altar", "", 16, 87, "items:87", false, true, 64, 0x7F1D1D, false, false, true, 2551));
		setCustomItemDefinition(2552, new ItemDef("Mystic Blood Robe Top", "A dark red robe top attuned to the blood altar", "", 16, 87, "items:87", false, true, 64, 0x7F1D1D, false, false, true, 2552));
		setCustomItemDefinition(2553, new ItemDef("Arcane Blood Robe Top", "A dark red robe top attuned to the blood altar", "", 16, 87, "items:87", false, true, 64, 0x7F1D1D, false, false, true, 2553));
		setCustomItemDefinition(2554, new ItemDef("Elder Blood Robe Top", "A dark red robe top attuned to the blood altar", "", 16, 87, "items:87", false, true, 64, 0x7F1D1D, false, false, true, 2554));
		setCustomItemDefinition(2555, new ItemDef("Mythic Blood Robe Top", "A dark red robe top attuned to the blood altar", "", 16, 87, "items:87", false, true, 64, 0x7F1D1D, false, false, true, 2555));
		setCustomItemDefinition(2096, new ItemDef("Beginner's Soul Robe Top", "A silver-blue robe top attuned to the soul altar", "", 16, 87, "items:87", false, true, 64, 0x8FA8C9, false, false, true, 2096));
		setCustomItemDefinition(2556, new ItemDef("Novice Soul Robe Top", "A silver-blue robe top attuned to the soul altar", "", 16, 87, "items:87", false, true, 64, 0x8FA8C9, false, false, true, 2556));
		setCustomItemDefinition(2557, new ItemDef("Apprentice Soul Robe Top", "A silver-blue robe top attuned to the soul altar", "", 16, 87, "items:87", false, true, 64, 0x8FA8C9, false, false, true, 2557));
		setCustomItemDefinition(2558, new ItemDef("Adept Soul Robe Top", "A silver-blue robe top attuned to the soul altar", "", 16, 87, "items:87", false, true, 64, 0x8FA8C9, false, false, true, 2558));
		setCustomItemDefinition(2559, new ItemDef("Expert Soul Robe Top", "A silver-blue robe top attuned to the soul altar", "", 16, 87, "items:87", false, true, 64, 0x8FA8C9, false, false, true, 2559));
		setCustomItemDefinition(2560, new ItemDef("Master Soul Robe Top", "A silver-blue robe top attuned to the soul altar", "", 16, 87, "items:87", false, true, 64, 0x8FA8C9, false, false, true, 2560));
		setCustomItemDefinition(2561, new ItemDef("Mystic Soul Robe Top", "A silver-blue robe top attuned to the soul altar", "", 16, 87, "items:87", false, true, 64, 0x8FA8C9, false, false, true, 2561));
		setCustomItemDefinition(2562, new ItemDef("Arcane Soul Robe Top", "A silver-blue robe top attuned to the soul altar", "", 16, 87, "items:87", false, true, 64, 0x8FA8C9, false, false, true, 2562));
		setCustomItemDefinition(2563, new ItemDef("Elder Soul Robe Top", "A silver-blue robe top attuned to the soul altar", "", 16, 87, "items:87", false, true, 64, 0x8FA8C9, false, false, true, 2563));
		setCustomItemDefinition(2564, new ItemDef("Mythic Soul Robe Top", "A silver-blue robe top attuned to the soul altar", "", 16, 87, "items:87", false, true, 64, 0x8FA8C9, false, false, true, 2564));
		setCustomItemDefinition(2098, new ItemDef("Beginner's Air Robe Skirt", "A sky blue robe skirt attuned to the air altar", "", 12, 88, "items:88", false, true, 128, 0x87CEEB, false, false, true, 2098));
		setCustomItemDefinition(2565, new ItemDef("Novice Air Robe Skirt", "A sky blue robe skirt attuned to the air altar", "", 12, 88, "items:88", false, true, 128, 0x87CEEB, false, false, true, 2565));
		setCustomItemDefinition(2566, new ItemDef("Apprentice Air Robe Skirt", "A sky blue robe skirt attuned to the air altar", "", 12, 88, "items:88", false, true, 128, 0x87CEEB, false, false, true, 2566));
		setCustomItemDefinition(2567, new ItemDef("Adept Air Robe Skirt", "A sky blue robe skirt attuned to the air altar", "", 12, 88, "items:88", false, true, 128, 0x87CEEB, false, false, true, 2567));
		setCustomItemDefinition(2568, new ItemDef("Expert Air Robe Skirt", "A sky blue robe skirt attuned to the air altar", "", 12, 88, "items:88", false, true, 128, 0x87CEEB, false, false, true, 2568));
		setCustomItemDefinition(2569, new ItemDef("Master Air Robe Skirt", "A sky blue robe skirt attuned to the air altar", "", 12, 88, "items:88", false, true, 128, 0x87CEEB, false, false, true, 2569));
		setCustomItemDefinition(2570, new ItemDef("Mystic Air Robe Skirt", "A sky blue robe skirt attuned to the air altar", "", 12, 88, "items:88", false, true, 128, 0x87CEEB, false, false, true, 2570));
		setCustomItemDefinition(2571, new ItemDef("Arcane Air Robe Skirt", "A sky blue robe skirt attuned to the air altar", "", 12, 88, "items:88", false, true, 128, 0x87CEEB, false, false, true, 2571));
		setCustomItemDefinition(2572, new ItemDef("Elder Air Robe Skirt", "A sky blue robe skirt attuned to the air altar", "", 12, 88, "items:88", false, true, 128, 0x87CEEB, false, false, true, 2572));
		setCustomItemDefinition(2573, new ItemDef("Mythic Air Robe Skirt", "A sky blue robe skirt attuned to the air altar", "", 12, 88, "items:88", false, true, 128, 0x87CEEB, false, false, true, 2573));
		setCustomItemDefinition(2102, new ItemDef("Beginner's Mind Robe Skirt", "A brown-red robe skirt attuned to the mind altar", "", 12, 88, "items:88", false, true, 128, 0x8B4A3B, false, false, true, 2102));
		setCustomItemDefinition(2574, new ItemDef("Novice Mind Robe Skirt", "A brown-red robe skirt attuned to the mind altar", "", 12, 88, "items:88", false, true, 128, 0x8B4A3B, false, false, true, 2574));
		setCustomItemDefinition(2575, new ItemDef("Apprentice Mind Robe Skirt", "A brown-red robe skirt attuned to the mind altar", "", 12, 88, "items:88", false, true, 128, 0x8B4A3B, false, false, true, 2575));
		setCustomItemDefinition(2576, new ItemDef("Adept Mind Robe Skirt", "A brown-red robe skirt attuned to the mind altar", "", 12, 88, "items:88", false, true, 128, 0x8B4A3B, false, false, true, 2576));
		setCustomItemDefinition(2577, new ItemDef("Expert Mind Robe Skirt", "A brown-red robe skirt attuned to the mind altar", "", 12, 88, "items:88", false, true, 128, 0x8B4A3B, false, false, true, 2577));
		setCustomItemDefinition(2578, new ItemDef("Master Mind Robe Skirt", "A brown-red robe skirt attuned to the mind altar", "", 12, 88, "items:88", false, true, 128, 0x8B4A3B, false, false, true, 2578));
		setCustomItemDefinition(2579, new ItemDef("Mystic Mind Robe Skirt", "A brown-red robe skirt attuned to the mind altar", "", 12, 88, "items:88", false, true, 128, 0x8B4A3B, false, false, true, 2579));
		setCustomItemDefinition(2580, new ItemDef("Arcane Mind Robe Skirt", "A brown-red robe skirt attuned to the mind altar", "", 12, 88, "items:88", false, true, 128, 0x8B4A3B, false, false, true, 2580));
		setCustomItemDefinition(2581, new ItemDef("Elder Mind Robe Skirt", "A brown-red robe skirt attuned to the mind altar", "", 12, 88, "items:88", false, true, 128, 0x8B4A3B, false, false, true, 2581));
		setCustomItemDefinition(2582, new ItemDef("Mythic Mind Robe Skirt", "A brown-red robe skirt attuned to the mind altar", "", 12, 88, "items:88", false, true, 128, 0x8B4A3B, false, false, true, 2582));
		setCustomItemDefinition(2099, new ItemDef("Beginner's Water Robe Skirt", "A deep blue robe skirt attuned to the water altar", "", 12, 88, "items:88", false, true, 128, 0x1F4E8C, false, false, true, 2099));
		setCustomItemDefinition(2583, new ItemDef("Novice Water Robe Skirt", "A deep blue robe skirt attuned to the water altar", "", 12, 88, "items:88", false, true, 128, 0x1F4E8C, false, false, true, 2583));
		setCustomItemDefinition(2584, new ItemDef("Apprentice Water Robe Skirt", "A deep blue robe skirt attuned to the water altar", "", 12, 88, "items:88", false, true, 128, 0x1F4E8C, false, false, true, 2584));
		setCustomItemDefinition(2585, new ItemDef("Adept Water Robe Skirt", "A deep blue robe skirt attuned to the water altar", "", 12, 88, "items:88", false, true, 128, 0x1F4E8C, false, false, true, 2585));
		setCustomItemDefinition(2586, new ItemDef("Expert Water Robe Skirt", "A deep blue robe skirt attuned to the water altar", "", 12, 88, "items:88", false, true, 128, 0x1F4E8C, false, false, true, 2586));
		setCustomItemDefinition(2587, new ItemDef("Master Water Robe Skirt", "A deep blue robe skirt attuned to the water altar", "", 12, 88, "items:88", false, true, 128, 0x1F4E8C, false, false, true, 2587));
		setCustomItemDefinition(2588, new ItemDef("Mystic Water Robe Skirt", "A deep blue robe skirt attuned to the water altar", "", 12, 88, "items:88", false, true, 128, 0x1F4E8C, false, false, true, 2588));
		setCustomItemDefinition(2589, new ItemDef("Arcane Water Robe Skirt", "A deep blue robe skirt attuned to the water altar", "", 12, 88, "items:88", false, true, 128, 0x1F4E8C, false, false, true, 2589));
		setCustomItemDefinition(2590, new ItemDef("Elder Water Robe Skirt", "A deep blue robe skirt attuned to the water altar", "", 12, 88, "items:88", false, true, 128, 0x1F4E8C, false, false, true, 2590));
		setCustomItemDefinition(2591, new ItemDef("Mythic Water Robe Skirt", "A deep blue robe skirt attuned to the water altar", "", 12, 88, "items:88", false, true, 128, 0x1F4E8C, false, false, true, 2591));
		setCustomItemDefinition(2100, new ItemDef("Beginner's Earth Robe Skirt", "A brown robe skirt attuned to the earth altar", "", 12, 88, "items:88", false, true, 128, 0x7A5230, false, false, true, 2100));
		setCustomItemDefinition(2592, new ItemDef("Novice Earth Robe Skirt", "A brown robe skirt attuned to the earth altar", "", 12, 88, "items:88", false, true, 128, 0x7A5230, false, false, true, 2592));
		setCustomItemDefinition(2593, new ItemDef("Apprentice Earth Robe Skirt", "A brown robe skirt attuned to the earth altar", "", 12, 88, "items:88", false, true, 128, 0x7A5230, false, false, true, 2593));
		setCustomItemDefinition(2594, new ItemDef("Adept Earth Robe Skirt", "A brown robe skirt attuned to the earth altar", "", 12, 88, "items:88", false, true, 128, 0x7A5230, false, false, true, 2594));
		setCustomItemDefinition(2595, new ItemDef("Expert Earth Robe Skirt", "A brown robe skirt attuned to the earth altar", "", 12, 88, "items:88", false, true, 128, 0x7A5230, false, false, true, 2595));
		setCustomItemDefinition(2596, new ItemDef("Master Earth Robe Skirt", "A brown robe skirt attuned to the earth altar", "", 12, 88, "items:88", false, true, 128, 0x7A5230, false, false, true, 2596));
		setCustomItemDefinition(2597, new ItemDef("Mystic Earth Robe Skirt", "A brown robe skirt attuned to the earth altar", "", 12, 88, "items:88", false, true, 128, 0x7A5230, false, false, true, 2597));
		setCustomItemDefinition(2598, new ItemDef("Arcane Earth Robe Skirt", "A brown robe skirt attuned to the earth altar", "", 12, 88, "items:88", false, true, 128, 0x7A5230, false, false, true, 2598));
		setCustomItemDefinition(2599, new ItemDef("Elder Earth Robe Skirt", "A brown robe skirt attuned to the earth altar", "", 12, 88, "items:88", false, true, 128, 0x7A5230, false, false, true, 2599));
		setCustomItemDefinition(2600, new ItemDef("Mythic Earth Robe Skirt", "A brown robe skirt attuned to the earth altar", "", 12, 88, "items:88", false, true, 128, 0x7A5230, false, false, true, 2600));
		setCustomItemDefinition(2101, new ItemDef("Beginner's Fire Robe Skirt", "A red robe skirt attuned to the fire altar", "", 12, 88, "items:88", false, true, 128, 0xC62828, false, false, true, 2101));
		setCustomItemDefinition(2601, new ItemDef("Novice Fire Robe Skirt", "A red robe skirt attuned to the fire altar", "", 12, 88, "items:88", false, true, 128, 0xC62828, false, false, true, 2601));
		setCustomItemDefinition(2602, new ItemDef("Apprentice Fire Robe Skirt", "A red robe skirt attuned to the fire altar", "", 12, 88, "items:88", false, true, 128, 0xC62828, false, false, true, 2602));
		setCustomItemDefinition(2603, new ItemDef("Adept Fire Robe Skirt", "A red robe skirt attuned to the fire altar", "", 12, 88, "items:88", false, true, 128, 0xC62828, false, false, true, 2603));
		setCustomItemDefinition(2604, new ItemDef("Expert Fire Robe Skirt", "A red robe skirt attuned to the fire altar", "", 12, 88, "items:88", false, true, 128, 0xC62828, false, false, true, 2604));
		setCustomItemDefinition(2605, new ItemDef("Master Fire Robe Skirt", "A red robe skirt attuned to the fire altar", "", 12, 88, "items:88", false, true, 128, 0xC62828, false, false, true, 2605));
		setCustomItemDefinition(2606, new ItemDef("Mystic Fire Robe Skirt", "A red robe skirt attuned to the fire altar", "", 12, 88, "items:88", false, true, 128, 0xC62828, false, false, true, 2606));
		setCustomItemDefinition(2607, new ItemDef("Arcane Fire Robe Skirt", "A red robe skirt attuned to the fire altar", "", 12, 88, "items:88", false, true, 128, 0xC62828, false, false, true, 2607));
		setCustomItemDefinition(2608, new ItemDef("Elder Fire Robe Skirt", "A red robe skirt attuned to the fire altar", "", 12, 88, "items:88", false, true, 128, 0xC62828, false, false, true, 2608));
		setCustomItemDefinition(2609, new ItemDef("Mythic Fire Robe Skirt", "A red robe skirt attuned to the fire altar", "", 12, 88, "items:88", false, true, 128, 0xC62828, false, false, true, 2609));
		setCustomItemDefinition(2103, new ItemDef("Beginner's Body Robe Skirt", "A tan robe skirt attuned to the body altar", "", 12, 88, "items:88", false, true, 128, 0xC2A57A, false, false, true, 2103));
		setCustomItemDefinition(2610, new ItemDef("Novice Body Robe Skirt", "A tan robe skirt attuned to the body altar", "", 12, 88, "items:88", false, true, 128, 0xC2A57A, false, false, true, 2610));
		setCustomItemDefinition(2611, new ItemDef("Apprentice Body Robe Skirt", "A tan robe skirt attuned to the body altar", "", 12, 88, "items:88", false, true, 128, 0xC2A57A, false, false, true, 2611));
		setCustomItemDefinition(2612, new ItemDef("Adept Body Robe Skirt", "A tan robe skirt attuned to the body altar", "", 12, 88, "items:88", false, true, 128, 0xC2A57A, false, false, true, 2612));
		setCustomItemDefinition(2613, new ItemDef("Expert Body Robe Skirt", "A tan robe skirt attuned to the body altar", "", 12, 88, "items:88", false, true, 128, 0xC2A57A, false, false, true, 2613));
		setCustomItemDefinition(2614, new ItemDef("Master Body Robe Skirt", "A tan robe skirt attuned to the body altar", "", 12, 88, "items:88", false, true, 128, 0xC2A57A, false, false, true, 2614));
		setCustomItemDefinition(2615, new ItemDef("Mystic Body Robe Skirt", "A tan robe skirt attuned to the body altar", "", 12, 88, "items:88", false, true, 128, 0xC2A57A, false, false, true, 2615));
		setCustomItemDefinition(2616, new ItemDef("Arcane Body Robe Skirt", "A tan robe skirt attuned to the body altar", "", 12, 88, "items:88", false, true, 128, 0xC2A57A, false, false, true, 2616));
		setCustomItemDefinition(2617, new ItemDef("Elder Body Robe Skirt", "A tan robe skirt attuned to the body altar", "", 12, 88, "items:88", false, true, 128, 0xC2A57A, false, false, true, 2617));
		setCustomItemDefinition(2618, new ItemDef("Mythic Body Robe Skirt", "A tan robe skirt attuned to the body altar", "", 12, 88, "items:88", false, true, 128, 0xC2A57A, false, false, true, 2618));
		setCustomItemDefinition(2105, new ItemDef("Beginner's Cosmic Robe Skirt", "A yellow robe skirt attuned to the cosmic altar", "", 12, 88, "items:88", false, true, 128, 0xE0C341, false, false, true, 2105));
		setCustomItemDefinition(2619, new ItemDef("Novice Cosmic Robe Skirt", "A yellow robe skirt attuned to the cosmic altar", "", 12, 88, "items:88", false, true, 128, 0xE0C341, false, false, true, 2619));
		setCustomItemDefinition(2620, new ItemDef("Apprentice Cosmic Robe Skirt", "A yellow robe skirt attuned to the cosmic altar", "", 12, 88, "items:88", false, true, 128, 0xE0C341, false, false, true, 2620));
		setCustomItemDefinition(2621, new ItemDef("Adept Cosmic Robe Skirt", "A yellow robe skirt attuned to the cosmic altar", "", 12, 88, "items:88", false, true, 128, 0xE0C341, false, false, true, 2621));
		setCustomItemDefinition(2622, new ItemDef("Expert Cosmic Robe Skirt", "A yellow robe skirt attuned to the cosmic altar", "", 12, 88, "items:88", false, true, 128, 0xE0C341, false, false, true, 2622));
		setCustomItemDefinition(2623, new ItemDef("Master Cosmic Robe Skirt", "A yellow robe skirt attuned to the cosmic altar", "", 12, 88, "items:88", false, true, 128, 0xE0C341, false, false, true, 2623));
		setCustomItemDefinition(2624, new ItemDef("Mystic Cosmic Robe Skirt", "A yellow robe skirt attuned to the cosmic altar", "", 12, 88, "items:88", false, true, 128, 0xE0C341, false, false, true, 2624));
		setCustomItemDefinition(2625, new ItemDef("Arcane Cosmic Robe Skirt", "A yellow robe skirt attuned to the cosmic altar", "", 12, 88, "items:88", false, true, 128, 0xE0C341, false, false, true, 2625));
		setCustomItemDefinition(2626, new ItemDef("Elder Cosmic Robe Skirt", "A yellow robe skirt attuned to the cosmic altar", "", 12, 88, "items:88", false, true, 128, 0xE0C341, false, false, true, 2626));
		setCustomItemDefinition(2627, new ItemDef("Mythic Cosmic Robe Skirt", "A yellow robe skirt attuned to the cosmic altar", "", 12, 88, "items:88", false, true, 128, 0xE0C341, false, false, true, 2627));
		setCustomItemDefinition(2104, new ItemDef("Beginner's Chaos Robe Skirt", "A orange robe skirt attuned to the chaos altar", "", 12, 88, "items:88", false, true, 128, 0xF57C00, false, false, true, 2104));
		setCustomItemDefinition(2628, new ItemDef("Novice Chaos Robe Skirt", "A orange robe skirt attuned to the chaos altar", "", 12, 88, "items:88", false, true, 128, 0xF57C00, false, false, true, 2628));
		setCustomItemDefinition(2629, new ItemDef("Apprentice Chaos Robe Skirt", "A orange robe skirt attuned to the chaos altar", "", 12, 88, "items:88", false, true, 128, 0xF57C00, false, false, true, 2629));
		setCustomItemDefinition(2630, new ItemDef("Adept Chaos Robe Skirt", "A orange robe skirt attuned to the chaos altar", "", 12, 88, "items:88", false, true, 128, 0xF57C00, false, false, true, 2630));
		setCustomItemDefinition(2631, new ItemDef("Expert Chaos Robe Skirt", "A orange robe skirt attuned to the chaos altar", "", 12, 88, "items:88", false, true, 128, 0xF57C00, false, false, true, 2631));
		setCustomItemDefinition(2632, new ItemDef("Master Chaos Robe Skirt", "A orange robe skirt attuned to the chaos altar", "", 12, 88, "items:88", false, true, 128, 0xF57C00, false, false, true, 2632));
		setCustomItemDefinition(2633, new ItemDef("Mystic Chaos Robe Skirt", "A orange robe skirt attuned to the chaos altar", "", 12, 88, "items:88", false, true, 128, 0xF57C00, false, false, true, 2633));
		setCustomItemDefinition(2634, new ItemDef("Arcane Chaos Robe Skirt", "A orange robe skirt attuned to the chaos altar", "", 12, 88, "items:88", false, true, 128, 0xF57C00, false, false, true, 2634));
		setCustomItemDefinition(2635, new ItemDef("Elder Chaos Robe Skirt", "A orange robe skirt attuned to the chaos altar", "", 12, 88, "items:88", false, true, 128, 0xF57C00, false, false, true, 2635));
		setCustomItemDefinition(2636, new ItemDef("Mythic Chaos Robe Skirt", "A orange robe skirt attuned to the chaos altar", "", 12, 88, "items:88", false, true, 128, 0xF57C00, false, false, true, 2636));
		setCustomItemDefinition(2106, new ItemDef("Beginner's Nature Robe Skirt", "A green robe skirt attuned to the nature altar", "", 12, 88, "items:88", false, true, 128, 0x2E7D32, false, false, true, 2106));
		setCustomItemDefinition(2637, new ItemDef("Novice Nature Robe Skirt", "A green robe skirt attuned to the nature altar", "", 12, 88, "items:88", false, true, 128, 0x2E7D32, false, false, true, 2637));
		setCustomItemDefinition(2638, new ItemDef("Apprentice Nature Robe Skirt", "A green robe skirt attuned to the nature altar", "", 12, 88, "items:88", false, true, 128, 0x2E7D32, false, false, true, 2638));
		setCustomItemDefinition(2639, new ItemDef("Adept Nature Robe Skirt", "A green robe skirt attuned to the nature altar", "", 12, 88, "items:88", false, true, 128, 0x2E7D32, false, false, true, 2639));
		setCustomItemDefinition(2640, new ItemDef("Expert Nature Robe Skirt", "A green robe skirt attuned to the nature altar", "", 12, 88, "items:88", false, true, 128, 0x2E7D32, false, false, true, 2640));
		setCustomItemDefinition(2641, new ItemDef("Master Nature Robe Skirt", "A green robe skirt attuned to the nature altar", "", 12, 88, "items:88", false, true, 128, 0x2E7D32, false, false, true, 2641));
		setCustomItemDefinition(2642, new ItemDef("Mystic Nature Robe Skirt", "A green robe skirt attuned to the nature altar", "", 12, 88, "items:88", false, true, 128, 0x2E7D32, false, false, true, 2642));
		setCustomItemDefinition(2643, new ItemDef("Arcane Nature Robe Skirt", "A green robe skirt attuned to the nature altar", "", 12, 88, "items:88", false, true, 128, 0x2E7D32, false, false, true, 2643));
		setCustomItemDefinition(2644, new ItemDef("Elder Nature Robe Skirt", "A green robe skirt attuned to the nature altar", "", 12, 88, "items:88", false, true, 128, 0x2E7D32, false, false, true, 2644));
		setCustomItemDefinition(2645, new ItemDef("Mythic Nature Robe Skirt", "A green robe skirt attuned to the nature altar", "", 12, 88, "items:88", false, true, 128, 0x2E7D32, false, false, true, 2645));
		setCustomItemDefinition(2107, new ItemDef("Beginner's Law Robe Skirt", "A teal robe skirt attuned to the law altar", "", 12, 88, "items:88", false, true, 128, 0x1B8A8F, false, false, true, 2107));
		setCustomItemDefinition(2646, new ItemDef("Novice Law Robe Skirt", "A teal robe skirt attuned to the law altar", "", 12, 88, "items:88", false, true, 128, 0x1B8A8F, false, false, true, 2646));
		setCustomItemDefinition(2647, new ItemDef("Apprentice Law Robe Skirt", "A teal robe skirt attuned to the law altar", "", 12, 88, "items:88", false, true, 128, 0x1B8A8F, false, false, true, 2647));
		setCustomItemDefinition(2648, new ItemDef("Adept Law Robe Skirt", "A teal robe skirt attuned to the law altar", "", 12, 88, "items:88", false, true, 128, 0x1B8A8F, false, false, true, 2648));
		setCustomItemDefinition(2649, new ItemDef("Expert Law Robe Skirt", "A teal robe skirt attuned to the law altar", "", 12, 88, "items:88", false, true, 128, 0x1B8A8F, false, false, true, 2649));
		setCustomItemDefinition(2650, new ItemDef("Master Law Robe Skirt", "A teal robe skirt attuned to the law altar", "", 12, 88, "items:88", false, true, 128, 0x1B8A8F, false, false, true, 2650));
		setCustomItemDefinition(2651, new ItemDef("Mystic Law Robe Skirt", "A teal robe skirt attuned to the law altar", "", 12, 88, "items:88", false, true, 128, 0x1B8A8F, false, false, true, 2651));
		setCustomItemDefinition(2652, new ItemDef("Arcane Law Robe Skirt", "A teal robe skirt attuned to the law altar", "", 12, 88, "items:88", false, true, 128, 0x1B8A8F, false, false, true, 2652));
		setCustomItemDefinition(2653, new ItemDef("Elder Law Robe Skirt", "A teal robe skirt attuned to the law altar", "", 12, 88, "items:88", false, true, 128, 0x1B8A8F, false, false, true, 2653));
		setCustomItemDefinition(2654, new ItemDef("Mythic Law Robe Skirt", "A teal robe skirt attuned to the law altar", "", 12, 88, "items:88", false, true, 128, 0x1B8A8F, false, false, true, 2654));
		setCustomItemDefinition(2108, new ItemDef("Beginner's Death Robe Skirt", "A black robe skirt attuned to the death altar", "", 12, 88, "items:88", false, true, 128, 0x222222, false, false, true, 2108));
		setCustomItemDefinition(2655, new ItemDef("Novice Death Robe Skirt", "A black robe skirt attuned to the death altar", "", 12, 88, "items:88", false, true, 128, 0x222222, false, false, true, 2655));
		setCustomItemDefinition(2656, new ItemDef("Apprentice Death Robe Skirt", "A black robe skirt attuned to the death altar", "", 12, 88, "items:88", false, true, 128, 0x222222, false, false, true, 2656));
		setCustomItemDefinition(2657, new ItemDef("Adept Death Robe Skirt", "A black robe skirt attuned to the death altar", "", 12, 88, "items:88", false, true, 128, 0x222222, false, false, true, 2657));
		setCustomItemDefinition(2658, new ItemDef("Expert Death Robe Skirt", "A black robe skirt attuned to the death altar", "", 12, 88, "items:88", false, true, 128, 0x222222, false, false, true, 2658));
		setCustomItemDefinition(2659, new ItemDef("Master Death Robe Skirt", "A black robe skirt attuned to the death altar", "", 12, 88, "items:88", false, true, 128, 0x222222, false, false, true, 2659));
		setCustomItemDefinition(2660, new ItemDef("Mystic Death Robe Skirt", "A black robe skirt attuned to the death altar", "", 12, 88, "items:88", false, true, 128, 0x222222, false, false, true, 2660));
		setCustomItemDefinition(2661, new ItemDef("Arcane Death Robe Skirt", "A black robe skirt attuned to the death altar", "", 12, 88, "items:88", false, true, 128, 0x222222, false, false, true, 2661));
		setCustomItemDefinition(2662, new ItemDef("Elder Death Robe Skirt", "A black robe skirt attuned to the death altar", "", 12, 88, "items:88", false, true, 128, 0x222222, false, false, true, 2662));
		setCustomItemDefinition(2663, new ItemDef("Mythic Death Robe Skirt", "A black robe skirt attuned to the death altar", "", 12, 88, "items:88", false, true, 128, 0x222222, false, false, true, 2663));
		setCustomItemDefinition(2110, new ItemDef("Beginner's Blood Robe Skirt", "A dark red robe skirt attuned to the blood altar", "", 12, 88, "items:88", false, true, 128, 0x7F1D1D, false, false, true, 2110));
		setCustomItemDefinition(2664, new ItemDef("Novice Blood Robe Skirt", "A dark red robe skirt attuned to the blood altar", "", 12, 88, "items:88", false, true, 128, 0x7F1D1D, false, false, true, 2664));
		setCustomItemDefinition(2665, new ItemDef("Apprentice Blood Robe Skirt", "A dark red robe skirt attuned to the blood altar", "", 12, 88, "items:88", false, true, 128, 0x7F1D1D, false, false, true, 2665));
		setCustomItemDefinition(2666, new ItemDef("Adept Blood Robe Skirt", "A dark red robe skirt attuned to the blood altar", "", 12, 88, "items:88", false, true, 128, 0x7F1D1D, false, false, true, 2666));
		setCustomItemDefinition(2667, new ItemDef("Expert Blood Robe Skirt", "A dark red robe skirt attuned to the blood altar", "", 12, 88, "items:88", false, true, 128, 0x7F1D1D, false, false, true, 2667));
		setCustomItemDefinition(2668, new ItemDef("Master Blood Robe Skirt", "A dark red robe skirt attuned to the blood altar", "", 12, 88, "items:88", false, true, 128, 0x7F1D1D, false, false, true, 2668));
		setCustomItemDefinition(2669, new ItemDef("Mystic Blood Robe Skirt", "A dark red robe skirt attuned to the blood altar", "", 12, 88, "items:88", false, true, 128, 0x7F1D1D, false, false, true, 2669));
		setCustomItemDefinition(2670, new ItemDef("Arcane Blood Robe Skirt", "A dark red robe skirt attuned to the blood altar", "", 12, 88, "items:88", false, true, 128, 0x7F1D1D, false, false, true, 2670));
		setCustomItemDefinition(2671, new ItemDef("Elder Blood Robe Skirt", "A dark red robe skirt attuned to the blood altar", "", 12, 88, "items:88", false, true, 128, 0x7F1D1D, false, false, true, 2671));
		setCustomItemDefinition(2672, new ItemDef("Mythic Blood Robe Skirt", "A dark red robe skirt attuned to the blood altar", "", 12, 88, "items:88", false, true, 128, 0x7F1D1D, false, false, true, 2672));
		setCustomItemDefinition(2109, new ItemDef("Beginner's Soul Robe Skirt", "A silver-blue robe skirt attuned to the soul altar", "", 12, 88, "items:88", false, true, 128, 0x8FA8C9, false, false, true, 2109));
		setCustomItemDefinition(2673, new ItemDef("Novice Soul Robe Skirt", "A silver-blue robe skirt attuned to the soul altar", "", 12, 88, "items:88", false, true, 128, 0x8FA8C9, false, false, true, 2673));
		setCustomItemDefinition(2674, new ItemDef("Apprentice Soul Robe Skirt", "A silver-blue robe skirt attuned to the soul altar", "", 12, 88, "items:88", false, true, 128, 0x8FA8C9, false, false, true, 2674));
		setCustomItemDefinition(2675, new ItemDef("Adept Soul Robe Skirt", "A silver-blue robe skirt attuned to the soul altar", "", 12, 88, "items:88", false, true, 128, 0x8FA8C9, false, false, true, 2675));
		setCustomItemDefinition(2676, new ItemDef("Expert Soul Robe Skirt", "A silver-blue robe skirt attuned to the soul altar", "", 12, 88, "items:88", false, true, 128, 0x8FA8C9, false, false, true, 2676));
		setCustomItemDefinition(2677, new ItemDef("Master Soul Robe Skirt", "A silver-blue robe skirt attuned to the soul altar", "", 12, 88, "items:88", false, true, 128, 0x8FA8C9, false, false, true, 2677));
		setCustomItemDefinition(2678, new ItemDef("Mystic Soul Robe Skirt", "A silver-blue robe skirt attuned to the soul altar", "", 12, 88, "items:88", false, true, 128, 0x8FA8C9, false, false, true, 2678));
		setCustomItemDefinition(2679, new ItemDef("Arcane Soul Robe Skirt", "A silver-blue robe skirt attuned to the soul altar", "", 12, 88, "items:88", false, true, 128, 0x8FA8C9, false, false, true, 2679));
		setCustomItemDefinition(2680, new ItemDef("Elder Soul Robe Skirt", "A silver-blue robe skirt attuned to the soul altar", "", 12, 88, "items:88", false, true, 128, 0x8FA8C9, false, false, true, 2680));
		setCustomItemDefinition(2681, new ItemDef("Mythic Soul Robe Skirt", "A silver-blue robe skirt attuned to the soul altar", "", 12, 88, "items:88", false, true, 128, 0x8FA8C9, false, false, true, 2681));
		setCustomItemDefinition(2764, new ItemDef("Beginner's Life Wizard Hat", "A golden yellow wizard hat attuned to the life altar", "", 6, 86, "items:86", false, true, 32, 0xF2D75A, false, false, true, 2764));
		setCustomItemDefinition(2765, new ItemDef("Novice Life Wizard Hat", "A golden yellow wizard hat attuned to the life altar", "", 6, 86, "items:86", false, true, 32, 0xF2D75A, false, false, true, 2765));
		setCustomItemDefinition(2766, new ItemDef("Apprentice Life Wizard Hat", "A golden yellow wizard hat attuned to the life altar", "", 6, 86, "items:86", false, true, 32, 0xF2D75A, false, false, true, 2766));
		setCustomItemDefinition(2767, new ItemDef("Adept Life Wizard Hat", "A golden yellow wizard hat attuned to the life altar", "", 6, 86, "items:86", false, true, 32, 0xF2D75A, false, false, true, 2767));
		setCustomItemDefinition(2768, new ItemDef("Expert Life Wizard Hat", "A golden yellow wizard hat attuned to the life altar", "", 6, 86, "items:86", false, true, 32, 0xF2D75A, false, false, true, 2768));
		setCustomItemDefinition(2769, new ItemDef("Master Life Wizard Hat", "A golden yellow wizard hat attuned to the life altar", "", 6, 86, "items:86", false, true, 32, 0xF2D75A, false, false, true, 2769));
		setCustomItemDefinition(2770, new ItemDef("Mystic Life Wizard Hat", "A golden yellow wizard hat attuned to the life altar", "", 6, 86, "items:86", false, true, 32, 0xF2D75A, false, false, true, 2770));
		setCustomItemDefinition(2771, new ItemDef("Arcane Life Wizard Hat", "A golden yellow wizard hat attuned to the life altar", "", 6, 86, "items:86", false, true, 32, 0xF2D75A, false, false, true, 2771));
		setCustomItemDefinition(2772, new ItemDef("Elder Life Wizard Hat", "A golden yellow wizard hat attuned to the life altar", "", 6, 86, "items:86", false, true, 32, 0xF2D75A, false, false, true, 2772));
		setCustomItemDefinition(2773, new ItemDef("Mythic Life Wizard Hat", "A golden yellow wizard hat attuned to the life altar", "", 6, 86, "items:86", false, true, 32, 0xF2D75A, false, false, true, 2773));
		setCustomItemDefinition(2774, new ItemDef("Beginner's Life Robe Top", "A golden yellow robe top attuned to the life altar", "", 16, 87, "items:87", false, true, 64, 0xF2D75A, false, false, true, 2774));
		setCustomItemDefinition(2775, new ItemDef("Novice Life Robe Top", "A golden yellow robe top attuned to the life altar", "", 16, 87, "items:87", false, true, 64, 0xF2D75A, false, false, true, 2775));
		setCustomItemDefinition(2776, new ItemDef("Apprentice Life Robe Top", "A golden yellow robe top attuned to the life altar", "", 16, 87, "items:87", false, true, 64, 0xF2D75A, false, false, true, 2776));
		setCustomItemDefinition(2777, new ItemDef("Adept Life Robe Top", "A golden yellow robe top attuned to the life altar", "", 16, 87, "items:87", false, true, 64, 0xF2D75A, false, false, true, 2777));
		setCustomItemDefinition(2778, new ItemDef("Expert Life Robe Top", "A golden yellow robe top attuned to the life altar", "", 16, 87, "items:87", false, true, 64, 0xF2D75A, false, false, true, 2778));
		setCustomItemDefinition(2779, new ItemDef("Master Life Robe Top", "A golden yellow robe top attuned to the life altar", "", 16, 87, "items:87", false, true, 64, 0xF2D75A, false, false, true, 2779));
		setCustomItemDefinition(2780, new ItemDef("Mystic Life Robe Top", "A golden yellow robe top attuned to the life altar", "", 16, 87, "items:87", false, true, 64, 0xF2D75A, false, false, true, 2780));
		setCustomItemDefinition(2781, new ItemDef("Arcane Life Robe Top", "A golden yellow robe top attuned to the life altar", "", 16, 87, "items:87", false, true, 64, 0xF2D75A, false, false, true, 2781));
		setCustomItemDefinition(2782, new ItemDef("Elder Life Robe Top", "A golden yellow robe top attuned to the life altar", "", 16, 87, "items:87", false, true, 64, 0xF2D75A, false, false, true, 2782));
		setCustomItemDefinition(2783, new ItemDef("Mythic Life Robe Top", "A golden yellow robe top attuned to the life altar", "", 16, 87, "items:87", false, true, 64, 0xF2D75A, false, false, true, 2783));
		setCustomItemDefinition(2784, new ItemDef("Beginner's Life Robe Skirt", "A golden yellow robe skirt attuned to the life altar", "", 12, 88, "items:88", false, true, 128, 0xF2D75A, false, false, true, 2784));
		setCustomItemDefinition(2785, new ItemDef("Novice Life Robe Skirt", "A golden yellow robe skirt attuned to the life altar", "", 12, 88, "items:88", false, true, 128, 0xF2D75A, false, false, true, 2785));
		setCustomItemDefinition(2786, new ItemDef("Apprentice Life Robe Skirt", "A golden yellow robe skirt attuned to the life altar", "", 12, 88, "items:88", false, true, 128, 0xF2D75A, false, false, true, 2786));
		setCustomItemDefinition(2787, new ItemDef("Adept Life Robe Skirt", "A golden yellow robe skirt attuned to the life altar", "", 12, 88, "items:88", false, true, 128, 0xF2D75A, false, false, true, 2787));
		setCustomItemDefinition(2788, new ItemDef("Expert Life Robe Skirt", "A golden yellow robe skirt attuned to the life altar", "", 12, 88, "items:88", false, true, 128, 0xF2D75A, false, false, true, 2788));
		setCustomItemDefinition(2789, new ItemDef("Master Life Robe Skirt", "A golden yellow robe skirt attuned to the life altar", "", 12, 88, "items:88", false, true, 128, 0xF2D75A, false, false, true, 2789));
		setCustomItemDefinition(2790, new ItemDef("Mystic Life Robe Skirt", "A golden yellow robe skirt attuned to the life altar", "", 12, 88, "items:88", false, true, 128, 0xF2D75A, false, false, true, 2790));
		setCustomItemDefinition(2791, new ItemDef("Arcane Life Robe Skirt", "A golden yellow robe skirt attuned to the life altar", "", 12, 88, "items:88", false, true, 128, 0xF2D75A, false, false, true, 2791));
		setCustomItemDefinition(2792, new ItemDef("Elder Life Robe Skirt", "A golden yellow robe skirt attuned to the life altar", "", 12, 88, "items:88", false, true, 128, 0xF2D75A, false, false, true, 2792));
		setCustomItemDefinition(2793, new ItemDef("Mythic Life Robe Skirt", "A golden yellow robe skirt attuned to the life altar", "", 12, 88, "items:88", false, true, 128, 0xF2D75A, false, false, true, 2793));
		setCustomItemDefinition(2207, new ItemDef("Tin Spear", "A tin tipped spear", "", 2, 283, "items:283", false, true, 16, 0xB7C9D9, true, false, true, 2207));
		setCustomItemDefinition(2208, new ItemDef("Copper Spear", "A copper tipped spear", "", 3, 283, "items:283", false, true, 16, 0xC86A2B, true, false, true, 2208));
		setCustomItemDefinition(2209, new ItemDef("Titan Steel Spear", "A titan steel tipped spear", "", 180, 283, "items:283", false, true, 16, 0x8EA6BB, true, false, true, 2209));
		setCustomItemDefinition(2210, new ItemDef("Orichalcum Spear", "An orichalcum tipped spear", "", 500, 283, "items:283", false, true, 16, 0x5A3F7D, true, false, true, 2210));
		setCustomItemDefinition(2211, new ItemDef("Poisoned Tin Spear", "A tin tipped spear with added venom", "", 2, 383, "items:383", false, true, 16, 0xB7C9D9, true, false, true, 2211));
		setCustomItemDefinition(2212, new ItemDef("Poisoned Copper Spear", "A copper tipped spear with added venom", "", 3, 383, "items:383", false, true, 16, 0xC86A2B, true, false, true, 2212));
		setCustomItemDefinition(2213, new ItemDef("Poisoned Titan Steel Spear", "A titan steel tipped spear with added venom", "", 180, 383, "items:383", false, true, 16, 0x8EA6BB, true, false, true, 2213));
		setCustomItemDefinition(2214, new ItemDef("Poisoned Orichalcum Spear", "An orichalcum tipped spear with added venom", "", 500, 383, "items:383", false, true, 16, 0x5A3F7D, true, false, true, 2214));
		addMetalShearsDefinition("Copper shears", 2215, 4, 0xC86A2B);
		addMetalShearsDefinition("Bronze shears", 2216, 8, 0xFF6C29);
		addMetalShearsDefinition("Iron shears", 2217, 26, 0xEEEEED);
		addMetalShearsDefinition("Steel shears", 2218, 80, 0xEEEEEE);
		addMetalShearsDefinition("Mithril shears", 2219, 260, 0x92B5D8);
		addMetalShearsDefinition("Titan Steel shears", 2220, 480, 0x8EA6BB);
		addMetalShearsDefinition("Adamantite shears", 2221, 1600, 0xB2CD89);
		addMetalShearsDefinition("Orichalcum shears", 2222, 3200, 0x5A3F7D);
		addMetalShearsDefinition("Rune shears", 2223, 32000, 0x00FFFF);
		setCustomItemDefinition(2224, new ItemDef("Tin Square Shield", "A sturdy metal shield", "", 72, 3, "items:3", false, true, 8, 0xB7C9D9, false, false, true, 2224));
		setCustomItemDefinition(2225, new ItemDef("Copper Square Shield", "A sturdy metal shield", "", 108, 3, "items:3", false, true, 8, 0xC86A2B, false, false, true, 2225));
		setCustomItemDefinition(2226, new ItemDef("Titan Steel Square Shield", "A sturdy metal shield", "", 192, 3, "items:3", false, true, 8, 0x8EA6BB, false, false, true, 2226));
		setCustomItemDefinition(2227, new ItemDef("Orichalcum Square Shield", "A sturdy metal shield", "", 240, 3, "items:3", false, true, 8, 0x5A3F7D, false, false, true, 2227));
		setCustomItemDefinition(2228, new ItemDef("Staff blessed by Zamorak", "A staff blessed by Zamorak", "", 24, 123, "items:123", false, true, 16, 0xF0E68C, false, false, true, 2228));
		setCustomItemDefinition(2229, new ItemDef("Pine staff blessed by Zamorak", "A pine staff blessed by Zamorak", "", 30, 123, "items:123", false, true, 16, 0xD8B25C, false, false, true, 2229));
		setCustomItemDefinition(2230, new ItemDef("Oak staff blessed by Zamorak", "An oak staff blessed by Zamorak", "", 45, 123, "items:123", false, true, 16, 0xB97A57, false, false, true, 2230));
		setCustomItemDefinition(2231, new ItemDef("Willow staff blessed by Zamorak", "A willow staff blessed by Zamorak", "", 54, 123, "items:123", false, true, 16, 0x7D9B5A, false, false, true, 2231));
		setCustomItemDefinition(2232, new ItemDef("Palm staff blessed by Zamorak", "A palm staff blessed by Zamorak", "", 120, 123, "items:123", false, true, 16, 0xC69C6D, false, false, true, 2232));
		setCustomItemDefinition(2233, new ItemDef("Maple staff blessed by Zamorak", "A maple staff blessed by Zamorak", "", 180, 123, "items:123", false, true, 16, 0xA0522D, false, false, true, 2233));
		setCustomItemDefinition(2234, new ItemDef("Yew staff blessed by Zamorak", "A yew staff blessed by Zamorak", "", 270, 123, "items:123", false, true, 16, 0x6B4F2A, false, false, true, 2234));
		setCustomItemDefinition(2235, new ItemDef("Ebony staff blessed by Zamorak", "An ebony staff blessed by Zamorak", "", 360, 123, "items:123", false, true, 16, 0x333333, false, false, true, 2235));
		setCustomItemDefinition(2236, new ItemDef("Magic staff blessed by Zamorak", "A magic staff blessed by Zamorak", "", 480, 123, "items:123", false, true, 16, 0x4B2E83, false, false, true, 2236));
		setCustomItemDefinition(2237, new ItemDef("Blood staff blessed by Zamorak", "A blood staff blessed by Zamorak", "", 720, 123, "items:123", false, true, 16, 0xCC2222, false, false, true, 2237));
		setCustomItemDefinition(2238, new ItemDef("Mind Staff", "A staff attuned to mind magic", "", 750, 123, "items:123", false, true, 16, 0xE0D28B, false, false, true, 2238));
		setCustomItemDefinition(2239, new ItemDef("Mind Pine Staff", "A pine staff attuned to mind magic", "", 875, 123, "items:123", false, true, 16, 0xE0D28B, false, false, true, 2239));
		setCustomItemDefinition(2240, new ItemDef("Mind Oak Staff", "An oak staff attuned to mind magic", "", 1750, 123, "items:123", false, true, 16, 0xE0D28B, false, false, true, 2240));
		setCustomItemDefinition(2241, new ItemDef("Mind Willow Staff", "A willow staff attuned to mind magic", "", 2000, 123, "items:123", false, true, 16, 0xE0D28B, false, false, true, 2241));
		setCustomItemDefinition(2242, new ItemDef("Mind Palm Staff", "A palm staff attuned to mind magic", "", 5250, 123, "items:123", false, true, 16, 0xE0D28B, false, false, true, 2242));
		setCustomItemDefinition(2243, new ItemDef("Mind Maple Staff", "A maple staff attuned to mind magic", "", 2500, 123, "items:123", false, true, 16, 0xE0D28B, false, false, true, 2243));
		setCustomItemDefinition(2244, new ItemDef("Mind Yew Staff", "A yew staff attuned to mind magic", "", 4000, 123, "items:123", false, true, 16, 0xE0D28B, false, false, true, 2244));
		setCustomItemDefinition(2245, new ItemDef("Mind Ebony Staff", "An ebony staff attuned to mind magic", "", 26250, 123, "items:123", false, true, 16, 0xE0D28B, false, false, true, 2245));
		setCustomItemDefinition(2246, new ItemDef("Mind Magic Staff", "A magic staff attuned to mind magic", "", 6500, 123, "items:123", false, true, 16, 0xE0D28B, false, false, true, 2246));
		setCustomItemDefinition(2247, new ItemDef("Mind Blood Staff", "A blood staff attuned to mind magic", "", 52500, 123, "items:123", false, true, 16, 0xE0D28B, false, false, true, 2247));
		setCustomItemDefinition(2248, new ItemDef("Body Staff", "A staff attuned to body magic", "", 750, 123, "items:123", false, true, 16, 0xC88A54, false, false, true, 2248));
		setCustomItemDefinition(2249, new ItemDef("Body Pine Staff", "A pine staff attuned to body magic", "", 875, 123, "items:123", false, true, 16, 0xC88A54, false, false, true, 2249));
		setCustomItemDefinition(2250, new ItemDef("Body Oak Staff", "An oak staff attuned to body magic", "", 1750, 123, "items:123", false, true, 16, 0xC88A54, false, false, true, 2250));
		setCustomItemDefinition(2251, new ItemDef("Body Willow Staff", "A willow staff attuned to body magic", "", 2000, 123, "items:123", false, true, 16, 0xC88A54, false, false, true, 2251));
		setCustomItemDefinition(2252, new ItemDef("Body Palm Staff", "A palm staff attuned to body magic", "", 5250, 123, "items:123", false, true, 16, 0xC88A54, false, false, true, 2252));
		setCustomItemDefinition(2253, new ItemDef("Body Maple Staff", "A maple staff attuned to body magic", "", 2500, 123, "items:123", false, true, 16, 0xC88A54, false, false, true, 2253));
		setCustomItemDefinition(2254, new ItemDef("Body Yew Staff", "A yew staff attuned to body magic", "", 4000, 123, "items:123", false, true, 16, 0xC88A54, false, false, true, 2254));
		setCustomItemDefinition(2255, new ItemDef("Body Ebony Staff", "An ebony staff attuned to body magic", "", 26250, 123, "items:123", false, true, 16, 0xC88A54, false, false, true, 2255));
		setCustomItemDefinition(2256, new ItemDef("Body Magic Staff", "A magic staff attuned to body magic", "", 6500, 123, "items:123", false, true, 16, 0xC88A54, false, false, true, 2256));
		setCustomItemDefinition(2257, new ItemDef("Body Blood Staff", "A blood staff attuned to body magic", "", 52500, 123, "items:123", false, true, 16, 0xC88A54, false, false, true, 2257));
		setCustomItemDefinition(2258, new ItemDef("Cosmic Staff", "A staff attuned to cosmic magic", "", 750, 123, "items:123", false, true, 16, 0x8A6CFF, false, false, true, 2258));
		setCustomItemDefinition(2259, new ItemDef("Cosmic Pine Staff", "A pine staff attuned to cosmic magic", "", 875, 123, "items:123", false, true, 16, 0x8A6CFF, false, false, true, 2259));
		setCustomItemDefinition(2260, new ItemDef("Cosmic Oak Staff", "An oak staff attuned to cosmic magic", "", 1750, 123, "items:123", false, true, 16, 0x8A6CFF, false, false, true, 2260));
		setCustomItemDefinition(2261, new ItemDef("Cosmic Willow Staff", "A willow staff attuned to cosmic magic", "", 2000, 123, "items:123", false, true, 16, 0x8A6CFF, false, false, true, 2261));
		setCustomItemDefinition(2262, new ItemDef("Cosmic Palm Staff", "A palm staff attuned to cosmic magic", "", 5250, 123, "items:123", false, true, 16, 0x8A6CFF, false, false, true, 2262));
		setCustomItemDefinition(2263, new ItemDef("Cosmic Maple Staff", "A maple staff attuned to cosmic magic", "", 2500, 123, "items:123", false, true, 16, 0x8A6CFF, false, false, true, 2263));
		setCustomItemDefinition(2264, new ItemDef("Cosmic Yew Staff", "A yew staff attuned to cosmic magic", "", 4000, 123, "items:123", false, true, 16, 0x8A6CFF, false, false, true, 2264));
		setCustomItemDefinition(2265, new ItemDef("Cosmic Ebony Staff", "An ebony staff attuned to cosmic magic", "", 26250, 123, "items:123", false, true, 16, 0x8A6CFF, false, false, true, 2265));
		setCustomItemDefinition(2266, new ItemDef("Cosmic Magic Staff", "A magic staff attuned to cosmic magic", "", 6500, 123, "items:123", false, true, 16, 0x8A6CFF, false, false, true, 2266));
		setCustomItemDefinition(2267, new ItemDef("Cosmic Blood Staff", "A blood staff attuned to cosmic magic", "", 52500, 123, "items:123", false, true, 16, 0x8A6CFF, false, false, true, 2267));
		setCustomItemDefinition(2268, new ItemDef("Chaos Staff", "A staff attuned to chaos magic", "", 750, 123, "items:123", false, true, 16, 0xFF4FB3, false, false, true, 2268));
		setCustomItemDefinition(2269, new ItemDef("Chaos Pine Staff", "A pine staff attuned to chaos magic", "", 875, 123, "items:123", false, true, 16, 0xFF4FB3, false, false, true, 2269));
		setCustomItemDefinition(2270, new ItemDef("Chaos Oak Staff", "An oak staff attuned to chaos magic", "", 1750, 123, "items:123", false, true, 16, 0xFF4FB3, false, false, true, 2270));
		setCustomItemDefinition(2271, new ItemDef("Chaos Willow Staff", "A willow staff attuned to chaos magic", "", 2000, 123, "items:123", false, true, 16, 0xFF4FB3, false, false, true, 2271));
		setCustomItemDefinition(2272, new ItemDef("Chaos Palm Staff", "A palm staff attuned to chaos magic", "", 5250, 123, "items:123", false, true, 16, 0xFF4FB3, false, false, true, 2272));
		setCustomItemDefinition(2273, new ItemDef("Chaos Maple Staff", "A maple staff attuned to chaos magic", "", 2500, 123, "items:123", false, true, 16, 0xFF4FB3, false, false, true, 2273));
		setCustomItemDefinition(2274, new ItemDef("Chaos Yew Staff", "A yew staff attuned to chaos magic", "", 4000, 123, "items:123", false, true, 16, 0xFF4FB3, false, false, true, 2274));
		setCustomItemDefinition(2275, new ItemDef("Chaos Ebony Staff", "An ebony staff attuned to chaos magic", "", 26250, 123, "items:123", false, true, 16, 0xFF4FB3, false, false, true, 2275));
		setCustomItemDefinition(2276, new ItemDef("Chaos Magic Staff", "A magic staff attuned to chaos magic", "", 6500, 123, "items:123", false, true, 16, 0xFF4FB3, false, false, true, 2276));
		setCustomItemDefinition(2277, new ItemDef("Chaos Blood Staff", "A blood staff attuned to chaos magic", "", 52500, 123, "items:123", false, true, 16, 0xFF4FB3, false, false, true, 2277));
		setCustomItemDefinition(2278, new ItemDef("Nature Staff", "A staff attuned to nature magic", "", 750, 123, "items:123", false, true, 16, 0x3FAF5A, false, false, true, 2278));
		setCustomItemDefinition(2279, new ItemDef("Nature Pine Staff", "A pine staff attuned to nature magic", "", 875, 123, "items:123", false, true, 16, 0x3FAF5A, false, false, true, 2279));
		setCustomItemDefinition(2280, new ItemDef("Nature Oak Staff", "An oak staff attuned to nature magic", "", 1750, 123, "items:123", false, true, 16, 0x3FAF5A, false, false, true, 2280));
		setCustomItemDefinition(2281, new ItemDef("Nature Willow Staff", "A willow staff attuned to nature magic", "", 2000, 123, "items:123", false, true, 16, 0x3FAF5A, false, false, true, 2281));
		setCustomItemDefinition(2282, new ItemDef("Nature Palm Staff", "A palm staff attuned to nature magic", "", 5250, 123, "items:123", false, true, 16, 0x3FAF5A, false, false, true, 2282));
		setCustomItemDefinition(2283, new ItemDef("Nature Maple Staff", "A maple staff attuned to nature magic", "", 2500, 123, "items:123", false, true, 16, 0x3FAF5A, false, false, true, 2283));
		setCustomItemDefinition(2284, new ItemDef("Nature Yew Staff", "A yew staff attuned to nature magic", "", 4000, 123, "items:123", false, true, 16, 0x3FAF5A, false, false, true, 2284));
		setCustomItemDefinition(2285, new ItemDef("Nature Ebony Staff", "An ebony staff attuned to nature magic", "", 26250, 123, "items:123", false, true, 16, 0x3FAF5A, false, false, true, 2285));
		setCustomItemDefinition(2286, new ItemDef("Nature Magic Staff", "A magic staff attuned to nature magic", "", 6500, 123, "items:123", false, true, 16, 0x3FAF5A, false, false, true, 2286));
		setCustomItemDefinition(2287, new ItemDef("Nature Blood Staff", "A blood staff attuned to nature magic", "", 52500, 123, "items:123", false, true, 16, 0x3FAF5A, false, false, true, 2287));
		setCustomItemDefinition(2288, new ItemDef("Law Staff", "A staff attuned to law magic", "", 750, 123, "items:123", false, true, 16, 0xE6E6E6, false, false, true, 2288));
		setCustomItemDefinition(2289, new ItemDef("Law Pine Staff", "A pine staff attuned to law magic", "", 875, 123, "items:123", false, true, 16, 0xE6E6E6, false, false, true, 2289));
		setCustomItemDefinition(2290, new ItemDef("Law Oak Staff", "An oak staff attuned to law magic", "", 1750, 123, "items:123", false, true, 16, 0xE6E6E6, false, false, true, 2290));
		setCustomItemDefinition(2291, new ItemDef("Law Willow Staff", "A willow staff attuned to law magic", "", 2000, 123, "items:123", false, true, 16, 0xE6E6E6, false, false, true, 2291));
		setCustomItemDefinition(2292, new ItemDef("Law Palm Staff", "A palm staff attuned to law magic", "", 5250, 123, "items:123", false, true, 16, 0xE6E6E6, false, false, true, 2292));
		setCustomItemDefinition(2293, new ItemDef("Law Maple Staff", "A maple staff attuned to law magic", "", 2500, 123, "items:123", false, true, 16, 0xE6E6E6, false, false, true, 2293));
		setCustomItemDefinition(2294, new ItemDef("Law Yew Staff", "A yew staff attuned to law magic", "", 4000, 123, "items:123", false, true, 16, 0xE6E6E6, false, false, true, 2294));
		setCustomItemDefinition(2295, new ItemDef("Law Ebony Staff", "An ebony staff attuned to law magic", "", 26250, 123, "items:123", false, true, 16, 0xE6E6E6, false, false, true, 2295));
		setCustomItemDefinition(2296, new ItemDef("Law Magic Staff", "A magic staff attuned to law magic", "", 6500, 123, "items:123", false, true, 16, 0xE6E6E6, false, false, true, 2296));
		setCustomItemDefinition(2297, new ItemDef("Law Blood Staff", "A blood staff attuned to law magic", "", 52500, 123, "items:123", false, true, 16, 0xE6E6E6, false, false, true, 2297));
		setCustomItemDefinition(2298, new ItemDef("Death Staff", "A staff attuned to death magic", "", 750, 123, "items:123", false, true, 16, 0x5E5E73, false, false, true, 2298));
		setCustomItemDefinition(2299, new ItemDef("Death Pine Staff", "A pine staff attuned to death magic", "", 875, 123, "items:123", false, true, 16, 0x5E5E73, false, false, true, 2299));
		setCustomItemDefinition(2300, new ItemDef("Death Oak Staff", "An oak staff attuned to death magic", "", 1750, 123, "items:123", false, true, 16, 0x5E5E73, false, false, true, 2300));
		setCustomItemDefinition(2301, new ItemDef("Death Willow Staff", "A willow staff attuned to death magic", "", 2000, 123, "items:123", false, true, 16, 0x5E5E73, false, false, true, 2301));
		setCustomItemDefinition(2302, new ItemDef("Death Palm Staff", "A palm staff attuned to death magic", "", 5250, 123, "items:123", false, true, 16, 0x5E5E73, false, false, true, 2302));
		setCustomItemDefinition(2303, new ItemDef("Death Maple Staff", "A maple staff attuned to death magic", "", 2500, 123, "items:123", false, true, 16, 0x5E5E73, false, false, true, 2303));
		setCustomItemDefinition(2304, new ItemDef("Death Yew Staff", "A yew staff attuned to death magic", "", 4000, 123, "items:123", false, true, 16, 0x5E5E73, false, false, true, 2304));
		setCustomItemDefinition(2305, new ItemDef("Death Ebony Staff", "An ebony staff attuned to death magic", "", 26250, 123, "items:123", false, true, 16, 0x5E5E73, false, false, true, 2305));
		setCustomItemDefinition(2306, new ItemDef("Death Magic Staff", "A magic staff attuned to death magic", "", 6500, 123, "items:123", false, true, 16, 0x5E5E73, false, false, true, 2306));
		setCustomItemDefinition(2307, new ItemDef("Death Blood Staff", "A blood staff attuned to death magic", "", 52500, 123, "items:123", false, true, 16, 0x5E5E73, false, false, true, 2307));
		setCustomItemDefinition(2308, new ItemDef("Blood Rune Staff", "A staff attuned to blood magic", "", 750, 123, "items:123", false, true, 16, 0xC01818, false, false, true, 2308));
		setCustomItemDefinition(2309, new ItemDef("Blood Rune Pine Staff", "A pine staff attuned to blood magic", "", 875, 123, "items:123", false, true, 16, 0xC01818, false, false, true, 2309));
		setCustomItemDefinition(2310, new ItemDef("Blood Rune Oak Staff", "An oak staff attuned to blood magic", "", 1750, 123, "items:123", false, true, 16, 0xC01818, false, false, true, 2310));
		setCustomItemDefinition(2311, new ItemDef("Blood Rune Willow Staff", "A willow staff attuned to blood magic", "", 2000, 123, "items:123", false, true, 16, 0xC01818, false, false, true, 2311));
		setCustomItemDefinition(2312, new ItemDef("Blood Rune Palm Staff", "A palm staff attuned to blood magic", "", 5250, 123, "items:123", false, true, 16, 0xC01818, false, false, true, 2312));
		setCustomItemDefinition(2313, new ItemDef("Blood Rune Maple Staff", "A maple staff attuned to blood magic", "", 2500, 123, "items:123", false, true, 16, 0xC01818, false, false, true, 2313));
		setCustomItemDefinition(2314, new ItemDef("Blood Rune Yew Staff", "A yew staff attuned to blood magic", "", 4000, 123, "items:123", false, true, 16, 0xC01818, false, false, true, 2314));
		setCustomItemDefinition(2315, new ItemDef("Blood Rune Ebony Staff", "An ebony staff attuned to blood magic", "", 26250, 123, "items:123", false, true, 16, 0xC01818, false, false, true, 2315));
		setCustomItemDefinition(2316, new ItemDef("Blood Rune Magic Staff", "A magic staff attuned to blood magic", "", 6500, 123, "items:123", false, true, 16, 0xC01818, false, false, true, 2316));
		setCustomItemDefinition(2317, new ItemDef("Blood Rune Blood Staff", "A blood staff attuned to blood magic", "", 52500, 123, "items:123", false, true, 16, 0xC01818, false, false, true, 2317));
		setCustomItemDefinition(2318, new ItemDef("Soul Staff", "A staff attuned to soul magic", "", 750, 123, "items:123", false, true, 16, 0x86D7FF, false, false, true, 2318));
		setCustomItemDefinition(2319, new ItemDef("Soul Pine Staff", "A pine staff attuned to soul magic", "", 875, 123, "items:123", false, true, 16, 0x86D7FF, false, false, true, 2319));
		setCustomItemDefinition(2320, new ItemDef("Soul Oak Staff", "An oak staff attuned to soul magic", "", 1750, 123, "items:123", false, true, 16, 0x86D7FF, false, false, true, 2320));
		setCustomItemDefinition(2321, new ItemDef("Soul Willow Staff", "A willow staff attuned to soul magic", "", 2000, 123, "items:123", false, true, 16, 0x86D7FF, false, false, true, 2321));
		setCustomItemDefinition(2322, new ItemDef("Soul Palm Staff", "A palm staff attuned to soul magic", "", 5250, 123, "items:123", false, true, 16, 0x86D7FF, false, false, true, 2322));
		setCustomItemDefinition(2323, new ItemDef("Soul Maple Staff", "A maple staff attuned to soul magic", "", 2500, 123, "items:123", false, true, 16, 0x86D7FF, false, false, true, 2323));
		setCustomItemDefinition(2324, new ItemDef("Soul Yew Staff", "A yew staff attuned to soul magic", "", 4000, 123, "items:123", false, true, 16, 0x86D7FF, false, false, true, 2324));
		setCustomItemDefinition(2325, new ItemDef("Soul Ebony Staff", "An ebony staff attuned to soul magic", "", 26250, 123, "items:123", false, true, 16, 0x86D7FF, false, false, true, 2325));
		setCustomItemDefinition(2326, new ItemDef("Soul Magic Staff", "A magic staff attuned to soul magic", "", 6500, 123, "items:123", false, true, 16, 0x86D7FF, false, false, true, 2326));
		setCustomItemDefinition(2327, new ItemDef("Soul Blood Staff", "A blood staff attuned to soul magic", "", 52500, 123, "items:123", false, true, 16, 0x86D7FF, false, false, true, 2327));
		setCustomItemDefinition(2754, new ItemDef("Life Staff", "A staff attuned to life magic", "", 750, 123, "items:123", false, true, 16, 0xF2D75A, false, false, true, 2754));
		setCustomItemDefinition(2755, new ItemDef("Life Pine Staff", "A pine staff attuned to life magic", "", 875, 123, "items:123", false, true, 16, 0xF2D75A, false, false, true, 2755));
		setCustomItemDefinition(2756, new ItemDef("Life Oak Staff", "An oak staff attuned to life magic", "", 1750, 123, "items:123", false, true, 16, 0xF2D75A, false, false, true, 2756));
		setCustomItemDefinition(2757, new ItemDef("Life Willow Staff", "A willow staff attuned to life magic", "", 2000, 123, "items:123", false, true, 16, 0xF2D75A, false, false, true, 2757));
		setCustomItemDefinition(2758, new ItemDef("Life Palm Staff", "A palm staff attuned to life magic", "", 5250, 123, "items:123", false, true, 16, 0xF2D75A, false, false, true, 2758));
		setCustomItemDefinition(2759, new ItemDef("Life Maple Staff", "A maple staff attuned to life magic", "", 2500, 123, "items:123", false, true, 16, 0xF2D75A, false, false, true, 2759));
		setCustomItemDefinition(2760, new ItemDef("Life Yew Staff", "A yew staff attuned to life magic", "", 4000, 123, "items:123", false, true, 16, 0xF2D75A, false, false, true, 2760));
		setCustomItemDefinition(2761, new ItemDef("Life Ebony Staff", "An ebony staff attuned to life magic", "", 26250, 123, "items:123", false, true, 16, 0xF2D75A, false, false, true, 2761));
		setCustomItemDefinition(2762, new ItemDef("Life Magic Staff", "A magic staff attuned to life magic", "", 6500, 123, "items:123", false, true, 16, 0xF2D75A, false, false, true, 2762));
		setCustomItemDefinition(2763, new ItemDef("Life Blood Staff", "A blood staff attuned to life magic", "", 52500, 123, "items:123", false, true, 16, 0xF2D75A, false, false, true, 2763));
		applyMyWorldStaffItemVisuals();
		setCustomItemDefinition(1299, new ItemDef("Stone", "A mined stone used to craft runes at altars.", "", 4, 443, "items:443", false, false, 0, 0, false, false, true, 1299));
		setCustomItemDefinition(1987, new ItemDef("Tin Pickaxe", "Used for mining", "", 10, 72, "items:72", false, true, 16, 0xb7c9d9, false, false, true, 1987));
		setCustomItemDefinition(2001, new ItemDef("Tin Hatchet", "A woodcutters axe", "", 10, 12, "items:12", false, true, 16, 0xb7c9d9, false, false, true, 2001));
		setCustomItemDefinition(2012, new ItemDef("Copper Hatchet", "A woodcutters axe", "", 18, 12, "items:12", false, true, 16, 0xc86a2b, false, false, true, 2012));
		setCustomItemDefinition(2023, new ItemDef("Titan Steel Hatchet", "A woodcutters axe", "", 90, 12, "items:12", false, true, 16, 0x8ea6bb, false, false, true, 2023));
		setCustomItemDefinition(2034, new ItemDef("Orichalcum Hatchet", "A woodcutters axe", "", 270, 12, "items:12", false, true, 16, 0x5a3f7d, false, false, true, 2034));
		setCustomItemDefinition(1260, new ItemDef("Mithril Pickaxe", "Used for mining", "", 1300, 72, "items:72", false, true, 16, 10072780, false, false, true, 1260));
		setCustomItemDefinition(2047, new ItemDef("Copper Pickaxe", "Used for mining", "", 18, 72, "items:72", false, true, 16, 0xc86a2b, false, false, true, 2047));
		setCustomItemDefinition(2048, new ItemDef("Titan Steel Pickaxe", "Used for mining", "", 90, 72, "items:72", false, true, 16, 0x8ea6bb, false, false, true, 2048));
		setCustomItemDefinition(2049, new ItemDef("Orichalcum Pickaxe", "Used for mining", "", 270, 72, "items:72", false, true, 16, 0x5a3f7d, false, false, true, 2049));
		setCustomItemDefinition(2328, new ItemDef("Tier 1 Agility Pouch", "A reward pouch earned from lower-tier agility courses.", "open", 300, 187, "items:187", true, false, 0, 0, false, false, false, 2328));
		setCustomItemDefinition(2329, new ItemDef("Tier 2 Agility Pouch", "A reward pouch earned from mid-tier agility courses.", "open", 900, 187, "items:187", true, false, 0, 0, false, false, false, 2329));
		setCustomItemDefinition(2330, new ItemDef("Tier 3 Agility Pouch", "A reward pouch earned from high-tier agility courses.", "open", 1800, 187, "items:187", true, false, 0, 0, false, false, false, 2330));
		setCustomItemDefinition(2753, new ItemDef("Pig Iron bar", "An impure pink iron bar", "", 12, 79, "items:79", false, false, 0, 0xFF8FB8, false, false, true, 2753));
		addWoolAccessoryDefinitions();
		addResourceSeedDefinition("Tin seed", "A magical seed that grows into a tin tree.", 2691, 250, 0xB7C9D9);
		addResourceSeedDefinition("Copper seed", "A magical seed that grows into a copper tree.", 2692, 250, 0xC86A2B);
		addResourceSeedDefinition("Iron seed", "A magical seed that grows into an iron tree.", 2693, 500, 0xEEEEED);
		addResourceSeedDefinition("Coal seed", "A magical seed that grows into a coal tree.", 2694, 750, 0x222222);
		addResourceSeedDefinition("Silver seed", "A magical seed that grows into a silver tree.", 2695, 750, 0xDDDDDD);
		addResourceSeedDefinition("Gold seed", "A magical seed that grows into a gold tree.", 2696, 1000, 0xFFD700);
		addResourceSeedDefinition("Adamantite seed", "A magical seed that grows into an adamantite tree.", 2697, 7500, 0x3FAF5A);
		addResourceSeedDefinition("Runite seed", "A magical seed that grows into a runite tree.", 2698, 12500, 0x86D7FF);
		addResourceSeedDefinition("Potato seed", "A magical seed that grows into a resource plant.", 2699, 100, 0xB68A3A);
		addResourceSeedDefinition("Onion seed", "A magical seed that grows into a resource plant.", 2700, 125, 0xD6C080);
		addResourceSeedDefinition("Garlic seed", "A magical seed that grows into a resource plant.", 2701, 175, 0xF3EBC5);
		addResourceSeedDefinition("Redberry seed", "A magical seed that grows into a resource plant.", 2702, 250, 0xCC2222);
		addResourceSeedDefinition("Limpwurt seed", "A magical seed that grows into a resource plant.", 2703, 500, 0x8A5A2B);
		addResourceSeedDefinition("Snape grass seed", "A magical seed that grows into a resource plant.", 2704, 750, 0x68A85F);
		addResourceSeedDefinition("Guam seed", "A magical seed that grows into a resource plant.", 2705, 350, 0x3FAF5A);
		addResourceSeedDefinition("Ranarr seed", "A magical seed that grows into a resource plant.", 2706, 1500, 0x77C66E);
		addResourceSeedDefinition("Woodcutting knowledge seed", "A magical seed that grows into a knowledge tree.", 2707, 5000, 0x86D7FF);
		addResourceSeedDefinition("Woodcutting money seed", "A magical seed that grows into a money tree.", 2708, 10000, 0xFFD700);
		addResourceSeedDefinition("Harvesting knowledge seed", "A magical seed that grows into a knowledge plant.", 2709, 5000, 0x86D7FF);
		addResourceSeedDefinition("Harvesting money seed", "A magical seed that grows into a money plant.", 2710, 10000, 0xFFD700);
		addResourceSeedDefinition("Oak log seed", "A magical seed that grows into a resource tree.", 2711, 200, 0xB97A57);
		addResourceSeedDefinition("Willow log seed", "A magical seed that grows into a resource tree.", 2712, 300, 0x7D9B5A);
		addResourceSeedDefinition("Palm log seed", "A magical seed that grows into a resource tree.", 2713, 450, 0xC78C4A);
		addResourceSeedDefinition("Maple log seed", "A magical seed that grows into a resource tree.", 2714, 700, 0xA0522D);
		addResourceSeedDefinition("Yew log seed", "A magical seed that grows into a resource tree.", 2715, 1100, 0x6B4F2A);
		addResourceSeedDefinition("Ebony log seed", "A magical seed that grows into a resource tree.", 2716, 1600, 0x333333);
		addResourceSeedDefinition("Magic log seed", "A magical seed that grows into a resource tree.", 2717, 2200, 0x4B2E83);
		addResourceSeedDefinition("Blood log seed", "A magical seed that grows into a resource tree.", 2718, 3000, 0xCC2222);
		addResourceSeedDefinition("Sapphire seed", "A magical seed that grows into a gem tree.", 2719, 1500, 0x3C8DFF);
		addResourceSeedDefinition("Emerald seed", "A magical seed that grows into a gem tree.", 2720, 2500, 0x26B34A);
		addResourceSeedDefinition("Ruby seed", "A magical seed that grows into a gem tree.", 2721, 4000, 0xD02030);
		addResourceSeedDefinition("Diamond seed", "A magical seed that grows into a gem tree.", 2722, 6000, 0xE6F7FF);
		addResourceSeedDefinition("Dragonstone seed", "A magical seed that grows into a gem tree.", 2723, 10000, 0xB05CFF);
		addResourceSeedDefinition("Salmon food seed", "A magical seed that grows into a food plant.", 2724, 1200, 0xE28C78);
		addResourceSeedDefinition("Tuna food seed", "A magical seed that grows into a food plant.", 2725, 1600, 0xB7C9D9);
		addResourceSeedDefinition("Lobster food seed", "A magical seed that grows into a food plant.", 2726, 2200, 0xD05A3A);
		addResourceSeedDefinition("Swordfish food seed", "A magical seed that grows into a food plant.", 2727, 3000, 0xD8D8C8);
		addResourceSeedDefinition("Shark food seed", "A magical seed that grows into a food plant.", 2728, 5000, 0x7FA8B8);
		addResourceSeedDefinition("Stew food seed", "A magical seed that grows into a food plant.", 2729, 1200, 0x9B6A3F);
		addResourceSeedDefinition("Meat pie food seed", "A magical seed that grows into a food plant.", 2730, 1400, 0xB97A57);
		addResourceSeedDefinition("Apple pie food seed", "A magical seed that grows into a food plant.", 2731, 1600, 0xD6C080);
		addResourceSeedDefinition("Plain pizza food seed", "A magical seed that grows into a food plant.", 2732, 1800, 0xE0B060);
		addResourceSeedDefinition("Meat pizza food seed", "A magical seed that grows into a food plant.", 2733, 2400, 0xC86A2B);
		addResourceSeedDefinition("Chocolate cake food seed", "A magical seed that grows into a food plant.", 2734, 2600, 0x6A3A24);
		addResourceSeedDefinition("Anchovie pizza food seed", "A magical seed that grows into a food plant.", 2735, 3200, 0x9A8A6A);
		addResourceSeedDefinition("Pineapple pizza food seed", "A magical seed that grows into a food plant.", 2736, 4200, 0xFFD95A);
		addResourceSeedDefinition("Marrentill seed", "A magical seed that grows into a herb plant.", 2737, 500, 0x5FA85F);
		addResourceSeedDefinition("Tarromin seed", "A magical seed that grows into a herb plant.", 2738, 650, 0x6FB45F);
		addResourceSeedDefinition("Harralander seed", "A magical seed that grows into a herb plant.", 2739, 900, 0x77C66E);
		addResourceSeedDefinition("Irit seed", "A magical seed that grows into a herb plant.", 2740, 1200, 0x8ACB6A);
		addResourceSeedDefinition("Avantoe seed", "A magical seed that grows into a herb plant.", 2741, 1600, 0x4E9E5A);
		addResourceSeedDefinition("Kwuarm seed", "A magical seed that grows into a herb plant.", 2742, 2200, 0x3FAF5A);
		addResourceSeedDefinition("Cadantine seed", "A magical seed that grows into a herb plant.", 2743, 3000, 0x2E8F4A);
		addResourceSeedDefinition("Dwarf weed seed", "A magical seed that grows into a herb plant.", 2744, 4000, 0x687A3F);
		addResourceSeedDefinition("Eye of newt seed", "A magical seed that grows into an ingredient plant.", 2745, 300, 0xDDDDDD);
		addResourceSeedDefinition("Ground unicorn horn seed", "A magical seed that grows into an ingredient plant.", 2746, 700, 0xF3EBC5);
		addResourceSeedDefinition("Ground blue dragon scale seed", "A magical seed that grows into an ingredient plant.", 2747, 1200, 0x3C8DFF);
		addResourceSeedDefinition("White berries seed", "A magical seed that grows into an ingredient plant.", 2748, 1500, 0xF2F2E8);
		addResourceSeedDefinition("Red spiders eggs seed", "A magical seed that grows into an ingredient plant.", 2749, 2200, 0xD02030);
		addResourceSeedDefinition("Jangerberries seed", "A magical seed that grows into an ingredient plant.", 2750, 1600, 0xC050B0);
		addResourceSeedDefinition("Wine of zamorak seed", "A magical seed that grows into an ingredient plant.", 2751, 4500, 0x8B1A1A);
		items.add(new ItemDef("Dragon battle Axe", "A vicious looking axe", "", 200000, 272, "items:272", false, true, 16, 16711748, true, false, true, 2752));
		normalizeItemDefinitions();
	}

	private static void addCustomWoodBowDefinitions(String woodName, int unstrungLongId, int unstrungShortId, int longbowId,
		int shortbowId, int unstrungLongPrice, int unstrungShortPrice, int longbowPrice, int shortbowPrice,
		int pictureMask, int blueMask) {
		setCustomItemDefinition(unstrungLongId, new ItemDef("unstrung " + woodName + " Longbow", "I need to find a string for this", "", unstrungLongPrice,
			119, "items:119", false, false, 0, pictureMask, blueMask, true, false, true, unstrungLongId));
		setCustomItemDefinition(unstrungShortId, new ItemDef("unstrung " + woodName + " Shortbow", "I need to find a string for this", "", unstrungShortPrice,
			120, "items:120", false, false, 0, pictureMask, blueMask, true, false, true, unstrungShortId));
		setCustomItemDefinition(longbowId, new ItemDef(woodName + " Longbow", "A nice sturdy bow", "", longbowPrice,
			54, "items:54", false, true, 24, pictureMask, blueMask, true, false, true, longbowId));
		setCustomItemDefinition(shortbowId, new ItemDef(woodName + " Shortbow", "Short but effective", "", shortbowPrice,
			55, "items:55", false, true, 24, pictureMask, blueMask, true, false, true, shortbowId));
	}

	private static void addWoodCrossbowDefinition(String name, int id, int price, int pictureMask, int blueMask) {
		ItemDef item = new ItemDef(name, "This fires crossbow bolts", "", price, 49, "items:49", false, true, 16, pictureMask, blueMask, true, false, true, id);
		setCustomItemDefinition(id, item);
	}

	private static void addFishingRodDefinition(String name, String description, int id, int price, int pictureMask, int blueMask) {
		setCustomItemDefinition(id, new ItemDef(name, description, "", price, 172, "items:172",
			false, true, 16, pictureMask, blueMask, false, false, true, id));
	}

	private static void addResourceSeedDefinition(String name, String description, int id, int price, int pictureMask) {
		setCustomItemDefinition(id, new ItemDef(name, description, "open", price, 270, "items:270",
			true, false, 0, pictureMask, false, false, false, id));
	}

	private static void addMetalBoltDefinition(String name, int id, int price, int pictureMask) {
		ItemDef item = new ItemDef(name, "Good if you have a crossbow!", "", price, 56, "items:56", true,
			Config.S_WANT_EQUIPMENT_TAB, Config.S_WANT_EQUIPMENT_TAB ? 1001 : 0, pictureMask, false, false, false, id);
		if (id < items.size()) {
			items.set(id, item);
		} else {
			items.add(item);
		}
	}

	private static void addMetalShearsDefinition(String name, int id, int price, int pictureMask) {
		ItemDef item = new ItemDef(name, name + " for harvesting", "", price, 66, "items:66", false,
			false, 0, pictureMask, false, false, true, id);
		setCustomItemDefinition(id, item);
	}

	private static void addWoolAccessoryDefinitions() {
		setCustomItemDefinition(2794, new ItemDef("Wool Gloves", "Simple gloves stitched from wool", "", 12, 17, "items:17",
			false, true, 256, 0xFFFFFF, false, false, true, 2794));
		setCustomItemDefinition(2795, new ItemDef("Wool Boots", "Simple boots stitched from wool", "", 12, 16, "items:16",
			false, true, 512, 0xFFFFFF, false, false, true, 2795));

		addWoolAccessoryLine(2796, "Gloves", 17, 256);
		addWoolAccessoryLine(2936, "Boots", 16, 512);
	}

	private static void addWoolAccessoryLine(int startId, String itemType, int spriteId, int wearableId) {
		final String[] tiers = {"Beginner's", "Novice", "Apprentice", "Adept", "Expert", "Master", "Mystic", "Arcane", "Elder", "Mythic"};
		final String[] altars = {"Air", "Mind", "Water", "Earth", "Fire", "Body", "Cosmic", "Chaos", "Nature", "Law", "Death", "Blood", "Soul", "Life"};
		final String[] colorNames = {"sky blue", "brown-red", "deep blue", "brown", "red", "tan", "yellow", "orange", "green", "teal", "black", "dark red", "silver-blue", "yellow"};
		final int[] colorMasks = {0x87CEEB, 0x8B4A3B, 0x1F4E8C, 0x6B4F2A, 0xD32F2F, 0xC2A57A, 0xE0C341, 0xF57C00, 0x2E7D32, 0x1B8A8F, 0x222222, 0x7F1D1D, 0x8FA8C9, 0xF2D75A};

		int id = startId;
		for (int altarIndex = 0; altarIndex < altars.length; altarIndex++) {
			for (int tierIndex = 0; tierIndex < tiers.length; tierIndex++) {
				setCustomItemDefinition(id, new ItemDef(
					tiers[tierIndex] + " " + altars[altarIndex] + " Wizard " + itemType,
					colorNames[altarIndex] + " wizard " + itemType.toLowerCase() + " attuned to the " + altars[altarIndex].toLowerCase() + " altar",
					"",
					12 * (tierIndex + 1),
					spriteId,
					"items:" + spriteId,
					false,
					true,
					wearableId,
					colorMasks[altarIndex],
					false,
					false,
					true,
					id));
				id++;
			}
		}
	}

	private static void addEnchantedJewelryDefinitions() {
		final String[] tiers = {"Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone"};
		final int[] amuletPrices = {900, 1275, 2025, 3525, 17625};
		final int[] necklacePrices = {1050, 1425, 2175, 3675, 18375};
		final int[] ringPrices = {900, 1275, 2025, 3525, 17625};
		final int[] lawAmuletPrices = {1050, 1425, 2175, 3675, 17775};
		final int[] soulNecklacePrices = {900, 1275, 2025, 3525, 17625};
		final int[] gemMasks = {19711, 3394611, 16724736, 0, 12255487};
		final int[] lawBankCharges = {50, 150, 300, 500, 750};

		addAmuletLine(1593, tiers, "Evasion", "Adds +%d ranged defense.", 3, amuletPrices, gemMasks, "");
		addAmuletLine(1598, tiers, "Balance", "Adds +%d melee, ranged, and magic defense.", 2, amuletPrices, gemMasks, "");
		addAmuletLine(1603, tiers, "Guarding", "Adds +%d melee defense.", 3, amuletPrices, gemMasks, "");
		addAmuletLine(1608, tiers, "Warding", "Adds +%d magic defense.", 3, amuletPrices, gemMasks, "");

		addNecklaceLine(1613, tiers, "Archery", "Adds +%d ranged power.", 3, necklacePrices, gemMasks);
		addNecklaceLine(1618, tiers, "Craftsmanship", "Boosts crafting-style skill XP by %d%%.", 5, necklacePrices, gemMasks);
		addNecklaceLine(1623, tiers, "Balance", "Adds +%d melee, ranged, and magic power.", 2, necklacePrices, gemMasks);
		addNecklaceLine(1628, tiers, "Force", "Adds +%d melee power.", 3, necklacePrices, gemMasks);
		addNecklaceLine(1633, tiers, "Sorcery", "Adds +%d magic power.", 3, necklacePrices, gemMasks);
		addNecklaceLine(1638, tiers, "Discipline", "Boosts gathering-style skill XP by %d%%.", 5, necklacePrices, gemMasks);
		addNecklaceLine(1643, tiers, "Fortune", "Has a %d%% chance to roll extra standard monster loot.", 10, necklacePrices, gemMasks);
		addNecklaceLine(1648, tiers, "Recoil", "Has %d%% chance to recoil attacker damage.", 8, necklacePrices, gemMasks);
		addNecklaceLine(1653, tiers, "Nourishment", "Boosts food healing by %d%%.", 10, necklacePrices, gemMasks);
		addLawBankingNecklaceLine(1658, tiers, necklacePrices, gemMasks, lawBankCharges);
		addNecklaceLine(1663, tiers, "Desperation", "Raises defenses as health falls. Tier %d.", 1, necklacePrices, gemMasks);
		addNecklaceLine(1668, tiers, "Vitality", "Adds +%d max Hits.", 2, necklacePrices, gemMasks);

		addRingLine(1673, tiers, "Archery", "Adds +%d ranged power.", 3, ringPrices, gemMasks);
		addRingLine(1678, tiers, "Balance", "Adds +%d melee, ranged, and magic power.", 2, ringPrices, gemMasks);
		addRingLine(1683, tiers, "Force", "Adds +%d melee power.", 3, ringPrices, gemMasks);
		addRingLine(1688, tiers, "Sorcery", "Adds +%d magic power.", 3, ringPrices, gemMasks);

		addOffsetRingLine(1693, new String[] {"Emerald", "Ruby", "Diamond", "Dragonstone"},
			"Recoil", "Has %d%% chance to recoil attacker damage.", 8,
			2,
			new int[] {1275, 2025, 3525, 17625}, new int[] {3394611, 16724736, 0, 12255487});
		addOffsetRingLine(1697, new String[] {"Emerald", "Ruby", "Diamond", "Dragonstone"},
			"Nourishment", "Boosts food healing by %d%%.", 10,
			2,
			new int[] {1275, 2025, 3525, 17625}, new int[] {3394611, 16724736, 0, 12255487});
		addRingLine(1701, new String[] {"Sapphire", "Emerald", "Ruby", "Diamond"},
			"Fortune", "Has a %d%% chance for an extra full monster drop.", 5,
			new int[] {900, 1275, 2025, 3525}, new int[] {19711, 3394611, 16724736, 0});
		setCustomItemDefinition(3111, new ItemDef("Dragonstone Ring of Fortune",
			"Has a 25% chance for an extra full monster drop.", "", 17625, 123, "items:123",
			false, true, 1200, 12255487, true, false, true, 3111));
		setCustomItemDefinition(3112, new ItemDef("Demon ash",
			"Ashes from a demon", "scatter", 80, 23, "items:23",
			false, false, 0, 11184810, false, false, true, 3112));
		setCustomItemDefinition(3113, new ItemDef("Grey dagger", "Short but pointy", "", 240, 80, "items:80", false, true, 16, 0x9EA59F, false, false, true, 3113));
		setCustomItemDefinition(3114, new ItemDef("Grey Short Sword", "A razor sharp sword", "", 624, 1, "items:1", false, true, 16, 0x9EA59F, false, false, true, 3114));
		setCustomItemDefinition(3115, new ItemDef("Grey Long Sword", "A razor sharp sword", "", 960, 81, "items:81", false, true, 16, 0x9EA59F, false, false, true, 3115));
		setCustomItemDefinition(3116, new ItemDef("Grey 2-handed Sword", "A very large sword", "", 1920, 82, "items:82", false, true, 8216, 0x9EA59F, false, false, true, 3116));
		setCustomItemDefinition(3117, new ItemDef("Grey Scimitar", "A vicious curved sword", "", 768, 83, "items:83", false, true, 16, 0x9EA59F, false, false, true, 3117));
		setCustomItemDefinition(3118, new ItemDef("Grey battle Axe", "A vicious looking axe", "", 1248, 84, "items:84", false, true, 16, 0x9EA59F, false, false, true, 3118));
		setCustomItemDefinition(3119, new ItemDef("Grey Mace", "A spiky mace", "", 432, 0, "items:0", false, true, 16, 0x9EA59F, false, false, true, 3119));
		setCustomItemDefinition(3120, new ItemDef("Large Grey Helmet", "A full face helmet", "", 1056, 6, "items:6", false, true, 33, 0x9EA59F, false, false, true, 3120));
		setCustomItemDefinition(3121, new ItemDef("Medium Grey Helmet", "A medium sized helmet", "", 576, 5, "items:5", false, true, 32, 0x9EA59F, false, false, true, 3121));
		setCustomItemDefinition(3122, new ItemDef("Grey Chain Mail Body", "A series of connected metal rings", "", 1440, 7, "items:7", false, true, 64, 0x9EA59F, false, false, true, 3122));
		setCustomItemDefinition(3123, new ItemDef("Grey Square Shield", "A medium metal shield", "", 1152, 3, "items:3", false, true, 8, 0x9EA59F, false, false, true, 3123));
		setCustomItemDefinition(3124, new ItemDef("Grey Paladin Shield", "A heavy shield favoured by balanced warriors", "", 1632, 2, "items:2", false, true, 8, 0x9EA59F, false, false, true, 3124));
		setCustomItemDefinition(3125, new ItemDef("Grey Plate Mail Body", "Provides excellent protection", "", 3840, 8, "items:8", false, true, 322, 0x9EA59F, false, false, true, 3125));
		setCustomItemDefinition(3126, new ItemDef("Grey Plate Mail Legs", "These look pretty heavy", "", 1920, 9, "items:9", false, true, 644, 0x9EA59F, false, false, true, 3126));
		setCustomItemDefinition(3127, new ItemDef("Grey Plate Mail top", "Armour designed for females", "", 3840, 8, "items:8", false, true, 322, 0x9EA59F, false, false, true, 3127));
		setCustomItemDefinition(3128, new ItemDef("Grey Plated skirt", "Armour designed for females", "", 1920, 9, "items:9", false, true, 644, 0x9EA59F, false, false, true, 3128));
		setCustomItemDefinition(3129, new ItemDef("Grey Chain Mail Legs", "A series of connected metal rings", "", 720, 590, "items:590", false, true, 128, 0x9EA59F, false, false, true, 3129));
		setCustomItemDefinition(3130, new ItemDef("Grey Chain Mail Top", "A series of connected metal rings", "", 1440, 595, "items:595", false, true, 64, 0x9EA59F, false, false, true, 3130));
		setCustomItemDefinition(3131, new ItemDef("Black gauntlets", "Heavy hand protection blessed by Zamorak", "", 384, 156, "items:156", false, true, 256, 0x222222, false, false, true, 3131));
		setCustomItemDefinition(3132, new ItemDef("Black greaves", "Heavy foot protection blessed by Zamorak", "", 384, 16, "items:16", false, true, 512, 0x222222, false, false, true, 3132));
		setCustomItemDefinition(3133, new ItemDef("White gauntlets", "Heavy hand protection blessed by Saradomin", "", 384, 156, "items:156", false, true, 256, 0xF0F0F0, false, false, true, 3133));
		setCustomItemDefinition(3134, new ItemDef("White greaves", "Heavy foot protection blessed by Saradomin", "", 384, 16, "items:16", false, true, 512, 0xF0F0F0, false, false, true, 3134));
		setCustomItemDefinition(3135, new ItemDef("Grey gauntlets", "Heavy hand protection blessed by Guthix", "", 384, 156, "items:156", false, true, 256, 0x9EA59F, false, false, true, 3135));
		setCustomItemDefinition(3136, new ItemDef("Grey greaves", "Heavy foot protection blessed by Guthix", "", 384, 16, "items:16", false, true, 512, 0x9EA59F, false, false, true, 3136));
		addBlessedWoolArmorDefinitions();
		addAdditionalBlessedStaffDefinitions();
		addSoulRingLine(1705, new String[] {"Sapphire", "Emerald", "Ruby", "Dragonstone"},
			new int[] {900, 1275, 2025, 17625}, new int[] {19711, 3394611, 16724736, 12255487});

		addLawAmuletLine(1709, tiers, lawAmuletPrices, gemMasks);
		addLawBankingRingLine(1714, tiers, ringPrices, gemMasks, lawBankCharges);

		addAmuletLine(1719, tiers, "Echoes", "Has a %d%% chance to echo half damage nearby.", 15, amuletPrices, gemMasks, "");
		addDeathAmuletLine(1724, tiers, amuletPrices, gemMasks);
		addAmuletLine(1729, tiers, "Siphoning", "Steals %d%% of damage dealt as healing.", 5, amuletPrices, gemMasks, "");
		addAmuletLine(1734, tiers, "Combat", "Boosts combat XP by %d%%.", 5, amuletPrices, gemMasks, "");
		addAmuletLine(1739, tiers, "Discipline", "Boosts hits, agility, prayer, and thieving XP by %d%%.", 5, amuletPrices, gemMasks, "");
		addAmuletLine(1744, tiers, "Cleansing", "Adds +%d poison decay per tick.", 1, amuletPrices, gemMasks, "");
		addCosmicAmuletLine(1749, tiers, amuletPrices, gemMasks);
		addSoulAmuletLine(1754, tiers, amuletPrices, gemMasks);
		addSoulNecklaceLine(1759, tiers, soulNecklacePrices, gemMasks);
		addAttunedRingLine(3076, tiers, "Craftsmanship", "Boosts crafting-style skill XP by %d%%.", 5, ringPrices, gemMasks);
		addAttunedRingLine(3081, tiers, "Discipline", "Boosts gathering-style skill XP by %d%%.", 5, ringPrices, gemMasks);
		addAttunedRingLine(3086, tiers, "Desperation", "Raises weapon power as health falls. Tier %d.", 1, ringPrices, gemMasks);
		addAttunedRingLine(3091, tiers, "Vitality", "Adds +%d max Hits.", 2, ringPrices, gemMasks);
		addLifeRingLine(3096, tiers, ringPrices, gemMasks);
		addLifeNecklaceLine(3101, tiers, necklacePrices, gemMasks);
		addLifeAmuletLine(3106, tiers, lawAmuletPrices, gemMasks);
	}

	private static void addBlessedWoolArmorDefinitions() {
		final String[] gods = {"Zamorak", "Saradomin", "Guthix"};
		final int[] colors = {0x222222, 0xF0F0F0, 0x9EA59F};
		int id = 3137;
		for (int index = 0; index < gods.length; index++) {
			final String god = gods[index];
			final int color = colors[index];
			setCustomItemDefinition(id++, new ItemDef("Wool hat blessed by " + god, "A wool hat blessed by " + god, "", 60, 86, "items:86", false, true, 32, color, false, false, true, id - 1));
			setCustomItemDefinition(id++, new ItemDef("Wool robe top blessed by " + god, "A wool robe top blessed by " + god, "", 160, 87, "items:87", false, true, 64, color, false, false, true, id - 1));
			setCustomItemDefinition(id++, new ItemDef("Wool robe bottom blessed by " + god, "A wool robe bottom blessed by " + god, "", 120, 88, "items:88", false, true, 128, color, false, false, true, id - 1));
			setCustomItemDefinition(id++, new ItemDef("Wool gloves blessed by " + god, "Wool gloves blessed by " + god, "", 120, 17, "items:17", false, true, 256, color, false, false, true, id - 1));
			setCustomItemDefinition(id++, new ItemDef("Wool boots blessed by " + god, "Wool boots blessed by " + god, "", 120, 16, "items:16", false, true, 512, color, false, false, true, id - 1));
		}
	}

	private static void addAdditionalBlessedStaffDefinitions() {
		addBlessedStaffLine(3152, "Saradomin");
		addBlessedStaffLine(3162, "Guthix");
	}

	private static void addBlessedStaffLine(final int startId, final String god) {
		final String[] woods = {"", "Pine ", "Oak ", "Willow ", "Palm ", "Maple ", "Yew ", "Ebony ", "Magic ", "Blood "};
		final String[] descriptions = {"A staff", "A pine staff", "An oak staff", "A willow staff", "A palm staff", "A maple staff", "A yew staff", "An ebony staff", "A magic staff", "A blood staff"};
		final int[] prices = {24, 30, 45, 54, 120, 180, 270, 360, 480, 720};
		final int[] colors = {0xF0E68C, 0xD8B25C, 0xB97A57, 0x7D9B5A, 0xC69C6D, 0xA0522D, 0x6B4F2A, 0x333333, 0x4B2E83, 0xCC2222};
		for (int index = 0; index < woods.length; index++) {
			final int id = startId + index;
			final String name = index == 0 ? "Staff" : woods[index] + "staff";
			setCustomItemDefinition(id, new ItemDef(
				name + " blessed by " + god,
				descriptions[index] + " blessed by " + god,
				"",
				prices[index],
				123,
				"items:123",
				false,
				true,
				16,
				colors[index],
				false,
				false,
				true,
				id));
		}
	}

	private static void addAmuletLine(int startId, String[] tiers, String altarName, String descriptionFormat,
		int step, int[] prices, int[] pictureMasks, String command) {
		for (int i = 0; i < tiers.length; i++) {
			setCustomItemDefinition(startId + i,
				new ItemDef(tiers[i] + " Amulet of " + altarName,
					String.format(descriptionFormat, step * (i + 1)),
					command,
					prices[i],
					125,
					"items:125",
					false,
					true,
					1024,
					pictureMasks[i],
					true,
					false,
					true,
					startId + i));
		}
	}

	private static void addNecklaceLine(int startId, String[] tiers, String altarName, String descriptionFormat,
		int step, int[] prices, int[] pictureMasks) {
		for (int i = 0; i < tiers.length; i++) {
			setCustomItemDefinition(startId + i,
				new ItemDef(tiers[i] + " Necklace of " + altarName,
					String.format(descriptionFormat, step * (i + 1)),
					"",
					prices[i],
					57,
					"items:57",
					false,
					true,
					1024,
					pictureMasks[i],
					true,
					false,
					true,
					startId + i));
		}
	}

	private static void addLawBankingNecklaceLine(int startId, String[] tiers, int[] prices, int[] pictureMasks,
		int[] charges) {
		for (int i = 0; i < tiers.length; i++) {
			setCustomItemDefinition(startId + i,
				new ItemDef(tiers[i] + " Necklace of Banking",
					"Banks non-stack monster loot. " + charges[i] + " charges.",
					"Check",
					prices[i],
					57,
					"items:57",
					false,
					true,
					1024,
					pictureMasks[i],
					true,
					false,
					true,
					startId + i));
		}
	}

	private static void addRingLine(int startId, String[] tiers, String altarName, String descriptionFormat,
		int step, int[] prices, int[] pictureMasks) {
		for (int i = 0; i < tiers.length; i++) {
			setCustomItemDefinition(startId + i,
				new ItemDef(tiers[i] + " Ring of " + altarName,
					String.format(descriptionFormat, step * (i + 1)),
					"",
					prices[i],
					123,
					"items:123",
					false,
					true,
					1200,
					pictureMasks[i],
					true,
					false,
					true,
					startId + i));
		}
	}

	private static void addOffsetRingLine(int startId, String[] tiers, String altarName, String descriptionFormat,
		int step, int startingTier, int[] prices, int[] pictureMasks) {
		for (int i = 0; i < tiers.length; i++) {
			setCustomItemDefinition(startId + i,
				new ItemDef(tiers[i] + " Ring of " + altarName,
					String.format(descriptionFormat, step * (startingTier + i)),
					"",
					prices[i],
					123,
					"items:123",
					false,
					true,
					1200,
					pictureMasks[i],
					true,
					false,
					true,
					startId + i));
		}
	}

	private static void addSoulRingLine(int startId, String[] tiers, int[] prices, int[] pictureMasks) {
		final int[] keptItems = {1, 2, 3, 5};
		for (int i = 0; i < tiers.length; i++) {
			setCustomItemDefinition(startId + i,
				new ItemDef(tiers[i] + " Ring of Preservation",
					"Lets you keep " + keptItems[i] + " extra item" + (keptItems[i] == 1 ? "" : "s") + " on death.",
					"",
					prices[i],
					123,
					"items:123",
					false,
					true,
					1200,
					pictureMasks[i],
					true,
					false,
					true,
					startId + i));
		}
	}

	private static void addAttunedRingLine(int startId, String[] tiers, String altarName, String descriptionFormat,
		int step, int[] prices, int[] pictureMasks) {
		for (int i = 0; i < tiers.length; i++) {
			setCustomItemDefinition(startId + i,
				new ItemDef(tiers[i] + " Ring of " + altarName,
					String.format(descriptionFormat, step * (i + 1)),
					"",
					prices[i],
					123,
					"items:123",
					false,
					true,
					1200,
					pictureMasks[i],
					true,
					false,
					true,
					startId + i));
		}
	}

	private static void addLawAmuletLine(int startId, String[] tiers, int[] prices, int[] pictureMasks) {
		for (int i = 0; i < tiers.length; i++) {
			setCustomItemDefinition(startId + i,
				new ItemDef(tiers[i] + " Amulet of Teleportation",
					"Stores 3 guild teleports.",
					"Use,Check",
					prices[i],
					125,
					"items:125",
					false,
					true,
					1024,
					pictureMasks[i],
					true,
					false,
					true,
					startId + i));
		}
	}

	private static void addLawBankingRingLine(int startId, String[] tiers, int[] prices, int[] pictureMasks,
		int[] charges) {
		for (int i = 0; i < tiers.length; i++) {
			setCustomItemDefinition(startId + i,
				new ItemDef(tiers[i] + " Ring of Banking",
					"Banks non-stack skilling drops. " + charges[i] + " charges.",
					"Check",
					prices[i],
					123,
					"items:123",
					false,
					true,
					1200,
					pictureMasks[i],
					true,
					false,
					true,
					startId + i));
		}
	}

	private static void addCosmicAmuletLine(int startId, String[] tiers, int[] prices, int[] pictureMasks) {
		for (int i = 0; i < tiers.length; i++) {
			int amount = (i + 1) * 10;
			setCustomItemDefinition(startId + i,
				new ItemDef(tiers[i] + " Amulet of Bounty",
					"Has a " + amount + "% chance to double rare gathering rewards.",
					"",
					prices[i],
					125,
					"items:125",
					false,
					true,
					1024,
					pictureMasks[i],
					true,
					false,
					true,
					startId + i));
		}
	}

	private static void addDeathAmuletLine(int startId, String[] tiers, int[] prices, int[] pictureMasks) {
		final int[] radius = {1, 1, 2, 2, 3};
		final int[] percent = {5, 10, 10, 15, 15};
		for (int i = 0; i < tiers.length; i++) {
			setCustomItemDefinition(startId + i,
				new ItemDef(tiers[i] + " Amulet of Ruin",
					"Enemy deaths hit foes within " + radius[i] + " tile" + (radius[i] == 1 ? "" : "s")
						+ " for " + percent[i] + "% max Hits.",
					"",
					prices[i],
					125,
					"items:125",
					false,
					true,
					1024,
					pictureMasks[i],
					true,
					false,
					true,
					startId + i));
		}
	}

	private static void addSoulAmuletLine(int startId, String[] tiers, int[] prices, int[] pictureMasks) {
		for (int i = 0; i < tiers.length; i++) {
			setCustomItemDefinition(startId + i,
				new ItemDef(tiers[i] + " Amulet of Lifesaving",
					"Has a " + ((i + 1) * 10) + "% chance not to break after saving you.",
					"",
					prices[i],
					125,
					"items:125",
					false,
					true,
					1024,
					pictureMasks[i],
					true,
					false,
					true,
					startId + i));
		}
	}

	private static void addSoulNecklaceLine(int startId, String[] tiers, int[] prices, int[] pictureMasks) {
		for (int i = 0; i < tiers.length; i++) {
			setCustomItemDefinition(startId + i,
				new ItemDef(tiers[i] + " Necklace of Preservation",
					"Lets you keep " + (i + 1) + " extra item" + ((i + 1) == 1 ? "" : "s") + " on death.",
					"",
					prices[i],
					57,
					"items:57",
					false,
					true,
					1024,
					pictureMasks[i],
					true,
					false,
					true,
					startId + i));
		}
	}

	private static void addLifeRingLine(int startId, String[] tiers, int[] prices, int[] pictureMasks) {
		for (int i = 0; i < tiers.length; i++) {
			setCustomItemDefinition(startId + i,
				new ItemDef(tiers[i] + " Ring of Endurance",
					"Extends support summons " + ((i + 1) * 20) + "% longer.",
					"",
					prices[i],
					123,
					"items:123",
					false,
					true,
					1200,
					pictureMasks[i],
					true,
					false,
					true,
					startId + i));
		}
	}

	private static void addLifeNecklaceLine(int startId, String[] tiers, int[] prices, int[] pictureMasks) {
		for (int i = 0; i < tiers.length; i++) {
			setCustomItemDefinition(startId + i,
				new ItemDef(tiers[i] + " Necklace of Vigor",
					"Raises combat summon health by " + ((i + 1) * 10) + "%.",
					"",
					prices[i],
					57,
					"items:57",
					false,
					true,
					1024,
					pictureMasks[i],
					true,
					false,
					true,
					startId + i));
		}
	}

	private static void addLifeAmuletLine(int startId, String[] tiers, int[] prices, int[] pictureMasks) {
		for (int i = 0; i < tiers.length; i++) {
			setCustomItemDefinition(startId + i,
				new ItemDef(tiers[i] + " Amulet of Command",
					"Raises combat summon max damage by +" + (i + 1) + ".",
					"",
					prices[i],
					125,
					"items:125",
					false,
					true,
					1024,
					pictureMasks[i],
					true,
					false,
					true,
					startId + i));
		}
	}

	private static void applyMyWorldLeatherArmorDescriptions() {
		setLeatherSetDescription(1835, 1839, "Full cow-hide set: +5 Hits.");
		setLeatherSetDescription(1840, 1844, "Full goblin-hide set: 20% chance to enrage for +10% attack speed.");
		setLeatherSetDescription(1845, 1849, "Full unicorn-hide set: +10 Prayer while worshipping Saradomin.");
		setLeatherSetDescription(1850, 1854, "Full bear-hide set: 20% chance to intimidate, slowing enemy attacks by 10%.");
		setLeatherSetDescription(1855, 1859, "Full black unicorn-hide set: +10 Prayer while worshipping Zamorak.");
		setLeatherSetDescription(1860, 1864, "Full scorpion-carapace set: 20% melee poison chance, up to 10 poison.");
		setLeatherSetDescription(1865, 1869, "Full wolf-hide set: summons a spirit wolf companion.");
		setLeatherSetDescription(1870, 1874, "Full spider-carapace set: 20% ranged poison chance, up to 10 poison.");
		setLeatherSetDescription(1875, 1879, "Full giant-hide set: 20% chance for +10% melee damage.");
		setLeatherSetDescription(1880, 1884, "Full ogre-hide set: 20% chance to stagger enemy attacks.");
		setLeatherSetDescription(1885, 1889, "Full baby-dragon-hide set: 20% chance to reduce enemy accuracy by 10%.");
		setLeatherSetDescription(1890, 1894, "Full magic-spider-carapace set: 20% magic poison chance, up to 20 poison.");
		setLeatherSetDescription(1895, 1899, "Full moss-giant-hide set: 20% chance for +10% earth and melee damage.");
		setLeatherSetDescription(1900, 1904, "Full ice-giant-hide set: 20% chance for +10% water and melee damage.");
		setLeatherSetDescription(1905, 1909, "Full demon-hide set: 20% chance for infernal fire, max hit 8.");
		setLeatherSetDescription(1910, 1914, "Full hellhound-hide set: summons a spirit hellhound companion.");
		setLeatherSetDescription(1915, 1919, "Full fire-giant-hide set: 20% chance for +10% fire and melee damage.");
		setLeatherSetDescription(1920, 1924, "Full blue-dragon-hide set: 20% chance to dampen enemy max hit by 10%.");
		setLeatherSetDescription(1925, 1929, "Full earth-dragon-hide set: 20% chance to slow enemy attacks by 6%.");
		setLeatherSetDescription(1930, 1934, "Full red-dragon-hide set: 20% chance to weaken enemy defense by 6%.");
		setLeatherSetDescription(1935, 1939, "Full black-demon-hide set: 20% chance for infernal fire, max hit 12.");
		setLeatherSetDescription(1940, 1944, "Full black-dragon-hide set: 20% chance for dragon breath, max hit 30.");
		setLeatherSetDescription(1945, 1949, "Full balrog-hide set: 20% chance for infernal fire, max hit 18.");
		setLeatherSetDescription(1950, 1954, "Full king-black-dragon-hide set: 60% chance for dragon breath, max hit 40.");
	}

	private static void setLeatherSetDescription(int firstItemId, int lastItemId, String description) {
		for (int itemId = firstItemId; itemId <= lastItemId; itemId++) {
			ItemDef item = findItem(itemId, false);
			if (item != null) {
				item.description = description;
			}
		}
	}

	private static void setCustomItemDefinition(int id, ItemDef item) {
		while (items.size() <= id) {
			items.add(createUnobtaniumPlaceholder(items.size()));
		}
		items.set(id, item);
	}

	private static ItemDef createUnobtaniumPlaceholder(int id) {
		return new ItemDef("Unobtanium", "I should update my client.", "", 17, 70, "items:70",
			false, false, 0, 0xCC4CFF, false, false, true, id);
	}

	private static void normalizeItemDefinitions() {
		int maxId = -1;
		for (ItemDef item : items) {
			if (item != null && item.id > maxId) {
				maxId = item.id;
			}
		}
		ArrayList<ItemDef> normalized = new ArrayList<>();
		for (int id = 0; id <= maxId; id++) {
			normalized.add(createUnobtaniumPlaceholder(id));
		}
		for (ItemDef item : items) {
			if (item == null || item.id < 0) {
				continue;
			}
			ItemDef current = normalized.get(item.id);
			if (isUnobtaniumPlaceholder(item) && !isUnobtaniumPlaceholder(current)) {
				continue;
			}
			normalized.set(item.id, item);
		}
		items.clear();
		items.addAll(normalized);
	}

	private static boolean isUnobtaniumPlaceholder(ItemDef item) {
		return item != null && "Unobtanium".equals(item.getName()) && item.getSpriteID() == 70;
	}


	private static void addCustomWoodStaffDefinitions(String woodName, String woodDescriptor, int baseStaffId, int airStaffId,
		int waterStaffId, int earthStaffId, int fireStaffId, int basePrice, int elementalPrice, int woodMask) {
		setCustomItemDefinition(baseStaffId, new ItemDef(woodName + " Staff", "A " + woodDescriptor + " staff prepared for Enchanting", "", basePrice,
			85, "items:85", false, true, 16, woodMask, true, false, true, baseStaffId));
		setCustomItemDefinition(airStaffId, new ItemDef("Air " + woodName + " Staff", "A " + woodDescriptor + " staff attuned to air magic", "", elementalPrice,
			91, "items:91", false, true, 16, 0x00FFFF, woodMask, true, false, true, airStaffId));
		setCustomItemDefinition(waterStaffId, new ItemDef("Water " + woodName + " Staff", "A " + woodDescriptor + " staff attuned to water magic", "", elementalPrice,
			91, "items:91", false, true, 16, 0x0000FF, woodMask, true, false, true, waterStaffId));
		setCustomItemDefinition(earthStaffId, new ItemDef("Earth " + woodName + " Staff", "A " + woodDescriptor + " staff attuned to earth magic", "", elementalPrice,
			91, "items:91", false, true, 16, 0x703800, woodMask, true, false, true, earthStaffId));
		setCustomItemDefinition(fireStaffId, new ItemDef("Fire " + woodName + " Staff", "A " + woodDescriptor + " staff attuned to fire magic", "", elementalPrice,
			91, "items:91", false, true, 16, 0xFF0000, woodMask, true, false, true, fireStaffId));
	}

	private static void applyMyWorldStaffItemVisuals() {
		for (int i = 0; i < MYWORLD_STAFF_BASE_IDS.length; i++) {
			replaceStaffItemVisual(MYWORLD_STAFF_BASE_IDS[i], 85, "items:85", MYWORLD_STAFF_WOOD_COLORS[i], 0);
		}
		for (int runeIndex = 0; runeIndex < MYWORLD_RUNE_STAFF_IDS.length; runeIndex++) {
			for (int woodIndex = 0; woodIndex < MYWORLD_RUNE_STAFF_IDS[runeIndex].length; woodIndex++) {
				replaceStaffItemVisual(MYWORLD_RUNE_STAFF_IDS[runeIndex][woodIndex], 91, "items:91",
					MYWORLD_STAFF_RUNE_COLORS[runeIndex], MYWORLD_STAFF_WOOD_COLORS[woodIndex]);
			}
		}
	}

	private static void replaceStaffItemVisual(int itemId, int spriteId, String spriteLocation, int pictureMask, int blueMask) {
		if (itemId < 0 || itemId >= items.size()) {
			return;
		}
		ItemDef item = items.get(itemId);
		if (item == null) {
			return;
		}
		setCustomItemDefinition(itemId, new ItemDef(item.getName(), item.getDescription(), commandString(item),
			item.basePrice, spriteId, spriteLocation, item.stackable, item.wieldable, item.wearableID,
			pictureMask, blueMask, item.membersItem, item.untradeable, item.noteable, itemId));
	}

	private static String commandString(ItemDef item) {
		if (item.command == null || item.command.length == 0) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < item.command.length; i++) {
			if (i > 0) {
				builder.append(",");
			}
			builder.append(item.command[i]);
		}
		return builder.toString();
	}

	private static void loadAnimationDefinitions() {
		animations.add(new AnimationDef("head1", "player", 1, 13, true, false, 0));//0
		animations.add(new AnimationDef("body1", "player", 2, 6, true, false, 0));//1
		animations.add(new AnimationDef("legs1", "player", 3, 15, true, false, 0));//2
		animations.add(new AnimationDef("fhead1", "player", 1, 13, true, false, 0));//3
		animations.add(new AnimationDef("fbody1", "player", 2, 10, true, false, 0));//4
		animations.add(new AnimationDef("head2", "player", 1, 13, true, false, 0));//5
		if (Config.S_ALLOW_BEARDED_LADIES) {
			animations.add(new AnimationDef("head3", "player", 1, 13, true, false, 0)); //6
		} else {
			animations.add(new AnimationDef("head3", "player", 1, 5, true, false, 0)); //6
		}
		animations.add(new AnimationDef("head4", "player", 1, 13, true, false, 0));//7
		animations.add(new AnimationDef("chefshat", "equipment", 16777215, 0, true, false, 0));//8
		animations.add(new AnimationDef("apron", "equipment", 16777215, 0, true, false, 0));//9
		animations.add(new AnimationDef("apron", "equipment", 9789488, 0, true, false, 0));//10
		animations.add(new AnimationDef("boots", "equipment", 5592405, 0, true, false, 0));//11
		animations.add(new AnimationDef("fullhelm", "equipment", 16737817, 0, true, false, 0));//12
		animations.add(new AnimationDef("fullhelm", "equipment", 15654365, 0, true, false, 0));//13
		animations.add(new AnimationDef("fullhelm", "equipment", 15658734, 0, true, false, 0));//14
		animations.add(new AnimationDef("fullhelm", "equipment", 10072780, 0, true, false, 0));//15
		animations.add(new AnimationDef("fullhelm", "equipment", 11717785, 0, true, false, 0));//16
		animations.add(new AnimationDef("fullhelm", "equipment", 65535, 0, true, false, 0));//17
		animations.add(new AnimationDef("fullhelm", "equipment", 3158064, 0, true, false, 0));//18
		animations.add(new AnimationDef("fullhelm", "equipment", 16777215, 0, true, false, 0));//19
		animations.add(new AnimationDef("chainmail", "equipment", 16737817, 0, true, false, 0));//20
		animations.add(new AnimationDef("chainmail", "equipment", 15654365, 0, true, false, 0));//21
		animations.add(new AnimationDef("chainmail", "equipment", 15658734, 0, true, false, 0));//22
		animations.add(new AnimationDef("chainmail", "equipment", 10072780, 0, true, false, 0));//23
		animations.add(new AnimationDef("chainmail", "equipment", 11717785, 0, true, false, 0));//24
		animations.add(new AnimationDef("chainmail", "equipment", 65535, 0, true, false, 0));//25
		animations.add(new AnimationDef("chainmail", "equipment", 3158064, 0, true, false, 0));//26
		animations.add(new AnimationDef("platemailtop", "equipment", 16737817, 0, true, false, 0));//27
		animations.add(new AnimationDef("platemailtop", "equipment", 15654365, 0, true, false, 0));//28
		animations.add(new AnimationDef("platemailtop", "equipment", 15658734, 0, true, false, 0));//29
		animations.add(new AnimationDef("platemailtop", "equipment", 10072780, 0, true, false, 0));//30
		animations.add(new AnimationDef("platemailtop", "equipment", 11717785, 0, true, false, 0));//31
		animations.add(new AnimationDef("platemailtop", "equipment", 3158064, 0, true, false, 0));//32
		animations.add(new AnimationDef("platemailtop", "equipment", 65535, 0, true, false, 0));//33
		animations.add(new AnimationDef("platemailtop", "equipment", 16777215, 0, true, false, 0));//34
		animations.add(new AnimationDef("platemailtop", "equipment", 10083839, 0, true, false, 0));//35
		animations.add(new AnimationDef("platemaillegs", "equipment", 16737817, 0, true, false, 0));//36
		animations.add(new AnimationDef("platemaillegs", "equipment", 15654365, 0, true, false, 0));//37
		animations.add(new AnimationDef("platemaillegs", "equipment", 15658734, 0, true, false, 0));//38
		animations.add(new AnimationDef("platemaillegs", "equipment", 10072780, 0, true, false, 0));//39
		animations.add(new AnimationDef("platemaillegs", "equipment", 11717785, 0, true, false, 0));//40
		animations.add(new AnimationDef("platemaillegs", "equipment", 65535, 0, true, false, 0));//41
		animations.add(new AnimationDef("platemaillegs", "equipment", 4210752, 0, true, false, 0));//42
		animations.add(new AnimationDef("platemaillegs", "equipment", 16777215, 0, true, false, 0));//43
		animations.add(new AnimationDef("platemaillegs", "equipment", 10083839, 0, true, false, 0));//44
		animations.add(new AnimationDef("leatherarmour", "equipment", 0, 0, true, false, 0));//45
		animations.add(new AnimationDef("leathergloves", "equipment", 0, 0, true, false, 0));//46
		animations.add(new AnimationDef("sword", "equipment", 16737817, 0, true, false, 0));//47
		animations.add(new AnimationDef("sword", "equipment", 15654365, 0, true, false, 0));//48
		animations.add(new AnimationDef("sword", "equipment", 15658734, 0, true, false, 0));//49
		animations.add(new AnimationDef("sword", "equipment", 10072780, 0, true, false, 0));//50
		animations.add(new AnimationDef("sword", "equipment", 11717785, 0, true, false, 0));//51
		animations.add(new AnimationDef("sword", "equipment", 65535, 0, true, false, 0));//52
		animations.add(new AnimationDef("sword", "equipment", 3158064, 0, true, false, 0));//53
		animations.add(new AnimationDef("fplatemailtop", "equipment", 16737817, 0, true, false, 0));//54
		animations.add(new AnimationDef("fplatemailtop", "equipment", 15654365, 0, true, false, 0));//55
		animations.add(new AnimationDef("fplatemailtop", "equipment", 15658734, 0, true, false, 0));//56
		animations.add(new AnimationDef("fplatemailtop", "equipment", 10072780, 0, true, false, 0));//57
		animations.add(new AnimationDef("fplatemailtop", "equipment", 11717785, 0, true, false, 0));//58
		animations.add(new AnimationDef("fplatemailtop", "equipment", 65535, 0, true, false, 0));//59
		animations.add(new AnimationDef("fplatemailtop", "equipment", 3158064, 0, true, false, 0));//60
		animations.add(new AnimationDef("apron", "equipment", 16777215, 0, true, false, 0));//61
		animations.add(new AnimationDef("cape", "equipment", 16711680, 0, true, false, 0));//62
		animations.add(new AnimationDef("cape", "equipment", 2434341, 0, true, false, 0));//63
		animations.add(new AnimationDef("cape", "equipment", 4210926, 0, true, false, 0));//64
		animations.add(new AnimationDef("cape", "equipment", 4246592, 0, true, false, 0));//65
		animations.add(new AnimationDef("cape", "equipment", 15658560, 0, true, false, 0));//66
		animations.add(new AnimationDef("cape", "equipment", 15636736, 0, true, false, 0));//67
		animations.add(new AnimationDef("cape", "equipment", 11141341, 0, true, false, 0));//68
		animations.add(new AnimationDef("mediumhelm", "equipment", 16737817, 0, true, false, 0));//69
		animations.add(new AnimationDef("mediumhelm", "equipment", 15654365, 0, true, false, 0));//70
		animations.add(new AnimationDef("mediumhelm", "equipment", 15658734, 0, true, false, 0));//71
		animations.add(new AnimationDef("mediumhelm", "equipment", 10072780, 0, true, false, 0));//72
		animations.add(new AnimationDef("mediumhelm", "equipment", 11717785, 0, true, false, 0));//73
		animations.add(new AnimationDef("mediumhelm", "equipment", 65535, 0, true, false, 0));//74
		animations.add(new AnimationDef("mediumhelm", "equipment", 3158064, 0, true, false, 0));//75
		animations.add(new AnimationDef("wizardsrobe", "equipment", 255, 0, true, false, 0));//76
		animations.add(new AnimationDef("wizardshat", "equipment", 255, 0, true, false, 0));//77
		animations.add(new AnimationDef("wizardshat", "equipment", 4210752, 0, true, false, 0));//78
		animations.add(new AnimationDef("necklace", "equipment", 15658734, 0, true, false, 0));//79
		animations.add(new AnimationDef("necklace", "equipment", 16763980, 0, true, false, 0));//80
		animations.add(new AnimationDef("skirt", "equipment", 255, 0, true, false, 0));//81
		animations.add(new AnimationDef("wizardsrobe", "equipment", 4210752, 0, true, false, 0));//82
		animations.add(new AnimationDef("wizardsrobe", "equipment", 10510400, 0, true, false, 0));//83
		animations.add(new AnimationDef("wizardsrobe", "equipment", 15609904, 0, true, false, 0));//84
		animations.add(new AnimationDef("wizardsrobe", "equipment", 16777215, 0, true, false, 0));//85
		animations.add(new AnimationDef("skirt", "equipment", 16777215, 0, true, false, 0));//86
		animations.add(new AnimationDef("skirt", "equipment", 10510400, 0, true, false, 0));//87
		animations.add(new AnimationDef("skirt", "equipment", 4210752, 0, true, false, 0));//88
		animations.add(new AnimationDef("skirt", "equipment", 16036851, 0, true, false, 0));//89
		animations.add(new AnimationDef("skirt", "equipment", 15609904, 0, true, false, 0));//90
		animations.add(new AnimationDef("skirt", "equipment", 8400921, 0, true, false, 0));//91
		animations.add(new AnimationDef("skirt", "equipment", 7824998, 0, true, false, 0));//92
		animations.add(new AnimationDef("skirt", "equipment", 7829367, 0, true, false, 0));//93
		animations.add(new AnimationDef("skirt", "equipment", 2245205, 0, true, false, 0));//94
		animations.add(new AnimationDef("skirt", "equipment", 4347170, 0, true, false, 0));//95
		animations.add(new AnimationDef("skirt", "equipment", 26214, 0, true, false, 0));//96
		animations.add(new AnimationDef("squareshield", "equipment", 16737817, 0, true, false, 0));//97
		animations.add(new AnimationDef("squareshield", "equipment", 15654365, 0, true, false, 0));//98
		animations.add(new AnimationDef("squareshield", "equipment", 15658734, 0, true, false, 0));//99
		animations.add(new AnimationDef("squareshield", "equipment", 10072780, 0, true, false, 0));//100
		animations.add(new AnimationDef("squareshield", "equipment", 11717785, 0, true, false, 0));//101
		animations.add(new AnimationDef("squareshield", "equipment", 56797, 0, true, false, 0));//102
		animations.add(new AnimationDef("squareshield", "equipment", 3158064, 0, true, false, 0));//103
		animations.add(new AnimationDef("squareshield", "equipment", 16750896, 0, true, false, 0));//104
		animations.add(new AnimationDef("squareshield", "equipment", 11363121, 0, true, false, 0));//105
		animations.add(new AnimationDef("crossbow", "equipment", 0, 0, false, false, 0));//106
		animations.add(new AnimationDef("longbow", "equipment", 0, 0, false, false, 0));//107
		animations.add(new AnimationDef("battleaxe", "equipment", 16737817, 0, true, false, 0));//108
		animations.add(new AnimationDef("battleaxe", "equipment", 15654365, 0, true, false, 0));//109
		animations.add(new AnimationDef("battleaxe", "equipment", 15658734, 0, true, false, 0));//110
		animations.add(new AnimationDef("battleaxe", "equipment", 10072780, 0, true, false, 0));//111
		animations.add(new AnimationDef("battleaxe", "equipment", 11717785, 0, true, false, 0));//112
		animations.add(new AnimationDef("battleaxe", "equipment", 65535, 0, true, false, 0));//113
		animations.add(new AnimationDef("battleaxe", "equipment", 3158064, 0, true, false, 0));//114
		animations.add(new AnimationDef("mace", "equipment", 16737817, 0, true, false, 0));//115
		animations.add(new AnimationDef("mace", "equipment", 15654365, 0, true, false, 0));//116
		animations.add(new AnimationDef("mace", "equipment", 15658734, 0, true, false, 0));//117
		animations.add(new AnimationDef("mace", "equipment", 10072780, 0, true, false, 0));//118
		animations.add(new AnimationDef("mace", "equipment", 11717785, 0, true, false, 0));//119
		animations.add(new AnimationDef("mace", "equipment", 65535, 0, true, false, 0));//120
		animations.add(new AnimationDef("mace", "equipment", 3158064, 0, true, false, 0));//121
		animations.add(new AnimationDef("staff", "equipment", 0, 0, true, false, 0));//122
		animations.add(new AnimationDef("rat", "npc", 4805259, 0, true, false, 0));//123
		animations.add(new AnimationDef("demon", "npc", 16384000, 0, true, false, 0));//124
		animations.add(new AnimationDef("spider", "npc", 13408576, 0, true, false, 0));//125
		animations.add(new AnimationDef("spider", "npc", 16728144, 0, true, false, 0));//126
		animations.add(new AnimationDef("camel", "npc", 0, 0, true, false, 0));//127
		animations.add(new AnimationDef("cow", "npc", 0, 0, true, false, 0));//128
		animations.add(new AnimationDef("sheep", "npc", 0, 0, false, false, 0));//129
		animations.add(new AnimationDef("unicorn", "npc", 0, 0, true, false, 0));//130
		animations.add(new AnimationDef("bear", "npc", 0, 0, true, false, 0));//131
		animations.add(new AnimationDef("chicken", "npc", 0, 0, true, false, 0));//132
		animations.add(new AnimationDef("skeleton", "npc", 0, 0, true, false, 0));//133
		animations.add(new AnimationDef("skelweap", "npc", 0, 0, true, true, 0));//134
		animations.add(new AnimationDef("zombie", "npc", 0, 0, true, false, 0));//135
		animations.add(new AnimationDef("zombweap", "npc", 0, 0, true, true, 0));//136
		animations.add(new AnimationDef("ghost", "npc", 0, 0, true, false, 0));//137
		animations.add(new AnimationDef("bat", "npc", 0, 0, true, false, 0));//138
		animations.add(new AnimationDef("goblin", "npc", 8969727, 0, true, false, 0));//139
		animations.add(new AnimationDef("goblin", "npc", 16711680, 0, true, false, 0));//140
		animations.add(new AnimationDef("goblin", "npc", 47872, 0, true, false, 0));//141
		animations.add(new AnimationDef("gobweap", "npc", 65535, 0, true, true, 0));//142
		animations.add(new AnimationDef("scorpion", "npc", 0, 0, true, false, 0));//143
		animations.add(new AnimationDef("dragon", "npc", 65280, 0, true, false, 0));//144
		animations.add(new AnimationDef("dragon", "npc", 16711680, 0, true, false, 0));//145
		animations.add(new AnimationDef("dragon", "npc", 21981, 0, true, false, 0));//146
		animations.add(new AnimationDef("wolf", "npc", 0, 0, true, false, 0));//147
		animations.add(new AnimationDef("wolf", "npc", 10066329, 0, true, false, 0));//148
		animations.add(new AnimationDef("partyhat", "equipment", 16711680, 0, true, false, 0));//149
		animations.add(new AnimationDef("partyhat", "equipment", 16776960, 0, true, false, 0));//150
		animations.add(new AnimationDef("partyhat", "equipment", 255, 0, true, false, 0));//151
		animations.add(new AnimationDef("partyhat", "equipment", 65280, 0, true, false, 0));//152
		animations.add(new AnimationDef("partyhat", "equipment", 16711935, 0, true, false, 0));//153
		animations.add(new AnimationDef("partyhat", "equipment", 16777215, 0, true, false, 0));//154
		animations.add(new AnimationDef("leathergloves", "equipment", 11202303, 0, true, false, 0));//155
		animations.add(new AnimationDef("chicken", "npc", 16711680, 0, true, false, 0));//156
		animations.add(new AnimationDef("fplatemailtop", "equipment", 10083839, 0, true, false, 0));//157
		animations.add(new AnimationDef("skirt", "equipment", 1118481, 0, true, false, 0));//158
		animations.add(new AnimationDef("wolf", "npc", 9789488, 0, true, false, 0));//159
		animations.add(new AnimationDef("spider", "npc", 65535, 0, true, false, 0));//160
		animations.add(new AnimationDef("battleaxe", "equipment", 16711748, 0, true, false, 0));//161
		animations.add(new AnimationDef("sword", "equipment", 16711748, 0, true, false, 0));//162
		animations.add(new AnimationDef("eyepatch", "equipment", 0, 0, true, true, 0));//163
		animations.add(new AnimationDef("demon", "npc", 3158064, 0, true, false, 0));//164
		animations.add(new AnimationDef("dragon", "npc", 3158064, 0, true, false, 0));//165
		animations.add(new AnimationDef("spider", "npc", 14535680, 0, true, false, 0));//166
		animations.add(new AnimationDef("wolf", "npc", 2236962, 0, true, false, 0));//167
		animations.add(new AnimationDef("unicorn", "npc", 2236962, 0, true, false, 0));//168
		animations.add(new AnimationDef("demon", "npc", 6291456, 0, true, false, 0));//169
		animations.add(new AnimationDef("spider", "npc", 2236962, 0, true, false, 0));//170
		animations.add(new AnimationDef("necklace", "equipment", 3158064, 0, true, false, 0));//171
		animations.add(new AnimationDef("rat", "npc", 11184810, 0, true, false, 0));//172
		animations.add(new AnimationDef("mediumhelm", "equipment", 11250603, 0, true, false, 0));//173
		animations.add(new AnimationDef("chainmail", "equipment", 11250603, 0, true, false, 0));//174
		animations.add(new AnimationDef("wizardshat", "equipment", 16711680, 0, true, false, 0));//175
		animations.add(new AnimationDef("legs1", "player", 9785408, 0, true, false, 0));//176
		animations.add(new AnimationDef("gasmask", "equipment", 0, 0, true, false, 0));//177
		animations.add(new AnimationDef("mediumhelm", "equipment", 16711748, 0, true, false, 0));//178
		animations.add(new AnimationDef("spider", "npc", 3852326, 0, true, false, 0));//179
		animations.add(new AnimationDef("spear", "equipment", 0, 0, true, false, 0));//180
		animations.add(new AnimationDef("halloweenmask", "equipment", 52224, 0, true, false, 0));//181
		animations.add(new AnimationDef("wizardsrobe", "equipment", 1052688, 0, true, false, 0));//182
		animations.add(new AnimationDef("skirt", "equipment", 1052688, 0, true, false, 0));//183
		animations.add(new AnimationDef("halloweenmask", "equipment", 16711680, 0, true, false, 0));//184
		animations.add(new AnimationDef("halloweenmask", "equipment", 255, 0, true, false, 0));//185
		animations.add(new AnimationDef("skirt", "equipment", 16755370, 15, true, false, 0));//186
		animations.add(new AnimationDef("skirt", "equipment", 11206570, 15, true, false, 0));//187
		animations.add(new AnimationDef("skirt", "equipment", 11184895, 15, true, false, 0));//188
		animations.add(new AnimationDef("skirt", "equipment", 16777164, 15, true, false, 0));//189
		animations.add(new AnimationDef("skirt", "equipment", 13434879, 15, true, false, 0));//190
		animations.add(new AnimationDef("wizardshat", "equipment", 16755370, 0, true, false, 0));//191
		animations.add(new AnimationDef("wizardshat", "equipment", 11206570, 0, true, false, 0));//192
		animations.add(new AnimationDef("wizardshat", "equipment", 11184895, 0, true, false, 0));//193
		animations.add(new AnimationDef("wizardshat", "equipment", 16777164, 0, true, false, 0));//194
		animations.add(new AnimationDef("wizardshat", "equipment", 13434879, 0, true, false, 0));//195
		animations.add(new AnimationDef("wizardsrobe", "equipment", 16755370, 0, true, false, 0));//196
		animations.add(new AnimationDef("wizardsrobe", "equipment", 11206570, 0, true, false, 0));//197
		animations.add(new AnimationDef("wizardsrobe", "equipment", 11184895, 0, true, false, 0));//198
		animations.add(new AnimationDef("wizardsrobe", "equipment", 16777164, 0, true, false, 0));//199
		animations.add(new AnimationDef("wizardsrobe", "equipment", 13434879, 0, true, false, 0));//200
		animations.add(new AnimationDef("wizardsrobe", "equipment", 3978097, 0, true, false, 0));//201
		animations.add(new AnimationDef("skirt", "equipment", 3978097, 0, true, false, 0));//202
		animations.add(new AnimationDef("boots", "equipment", 16755370, 0, true, false, 0));//203
		animations.add(new AnimationDef("boots", "equipment", 11206570, 0, true, false, 0));//204
		animations.add(new AnimationDef("boots", "equipment", 11184895, 0, true, false, 0));//205
		animations.add(new AnimationDef("boots", "equipment", 16777164, 0, true, false, 0));//206
		animations.add(new AnimationDef("boots", "equipment", 13434879, 0, true, false, 0));//207
		animations.add(new AnimationDef("santahat", "equipment", 0, 0, true, false, 0));//208
		animations.add(new AnimationDef("ibanstaff", "equipment", 0, 0, true, false, 0));//209
		animations.add(new AnimationDef("souless", "npc", 0, 0, true, false, 0));//210
		animations.add(new AnimationDef("boots", "equipment", 16777215, 0, true, false, 0));//211
		animations.add(new AnimationDef("legs1", "player", 16777215, 0, true, false, 0));//212
		animations.add(new AnimationDef("wizardsrobe", "equipment", 8421376, 0, true, false, 0));//213
		animations.add(new AnimationDef("skirt", "equipment", 8421376, 0, true, false, 0));//214
		animations.add(new AnimationDef("cape", "equipment", 16777215, 0, true, false, 0));//215
		animations.add(new AnimationDef("wolf", "npc", 13420580, 0, true, false, 0));//216
		animations.add(new AnimationDef("bunnyears", "equipment", 0, 0, true, false, 0));//217
		animations.add(new AnimationDef("saradominstaff", "equipment", 0, 0, true, false, 0));//218
		animations.add(new AnimationDef("spear", "equipment", 56797, 0, true, false, 0));//219
		animations.add(new AnimationDef("skirt", "equipment", 1392384, 0, true, false, 0));//220
		animations.add(new AnimationDef("wizardsrobe", "equipment", 1392384, 0, true, false, 0));//221
		animations.add(new AnimationDef("wolf", "npc", 5585408, 0, true, false, 0));//222
		animations.add(new AnimationDef("chicken", "npc", 6893315, 0, true, false, 0));//223
		animations.add(new AnimationDef("squareshield", "equipment", 13500416, 0, true, false, 0));//224
		animations.add(new AnimationDef("cape", "equipment", 16777215, 0, true, false, 0));//225
		animations.add(new AnimationDef("boots", "equipment", 1118481, 0, true, false, 0));//226
		animations.add(new AnimationDef("wizardsrobe", "equipment", 1118481, 0, true, false, 0));//227
		animations.add(new AnimationDef("scythe", "equipment", 0, 0, true, false, 0));//228
		/*
		  Add custom animation below.
		 */

		if (Config.S_WANT_CUSTOM_SPRITES) {
			// Hatchets
			animations.add(new AnimationDef("hatchet", "equipment", 16737817, 0, true, false, 0)); //229 - bronze hatchet
			animations.add(new AnimationDef("hatchet", "equipment", 15654365, 0, true, false, 0));//230
			animations.add(new AnimationDef("hatchet", "equipment", 15658734, 0, true, false, 0));//231
			animations.add(new AnimationDef("hatchet", "equipment", 10072780, 0, true, false, 0));//232
			animations.add(new AnimationDef("hatchet", "equipment", 11717785, 0, true, false, 0));//233
			animations.add(new AnimationDef("hatchet", "equipment", 65535, 0, true, false, 0));//234
			animations.add(new AnimationDef("hatchet", "equipment", 3158064, 0, true, false, 0)); //235 black

			// Kite shields
			animations.add(new AnimationDef("kiteshield", "equipment", 0xBB4B12, 0, true, false, 0)); //236 - bronze kite
			animations.add(new AnimationDef("kiteshield", "equipment", 0xAFA2A2, 0, true, false, 0)); //237 - iron kite
			animations.add(new AnimationDef("kiteshield", "equipment", 0xAFAFAF, 0, true, false, 0)); //238 - steel kite
			animations.add(new AnimationDef("kiteshield", "equipment", 0x708396, 0, true, false, 0)); //239 - black kite
			animations.add(new AnimationDef("kiteshield", "equipment", 0x839670, 0, true, false, 0)); //240 - mith kite
			animations.add(new AnimationDef("kiteshield", "equipment", 48059, 0, true, false, 0)); //241 - addy kite
			animations.add(new AnimationDef("kiteshield", "equipment", 0x232323, 0, true, false, 0)); //242 - rune kite

			// Dragon items
			animations.add(new AnimationDef("dragonshield", "equipment", 0, 0, true, false, 0)); //243 - dragon square
			animations.add(new AnimationDef("dragonmedhelm", "equipment", 0, 0, true, false, 0)); //244 - dragon med

			// Plate skirts
			animations.add(new AnimationDef("armorskirt", "equipment", 0xBB4B12, 0, true, false, 0)); //245 - bronze plate skirt
			animations.add(new AnimationDef("armorskirt", "equipment", 0xAFA2A2, 0, true, false, 0));//246
			animations.add(new AnimationDef("armorskirt", "equipment", 0xAFAFAF, 0, true, false, 0));//247
			animations.add(new AnimationDef("armorskirt", "equipment", 0x708396, 0, true, false, 0));//248
			animations.add(new AnimationDef("armorskirt", "equipment", 0x839670, 0, true, false, 0));//249
			animations.add(new AnimationDef("armorskirt", "equipment", 48059, 0, true, false, 0));//250
			animations.add(new AnimationDef("armorskirt", "equipment", 0x232323, 0, true, false, 0));//251

			// Longbows
			animations.add(new AnimationDef("longbow", "equipment", 8537122, 0, false, false, 0)); //252 - wooden longbow
			animations.add(new AnimationDef("longbow", "equipment", 11300689, 0, false, false, 0));//253
			animations.add(new AnimationDef("longbow", "equipment", 8941897, 0, false, false, 0));//254
			animations.add(new AnimationDef("longbow", "equipment", 9132849, 0, false, false, 0));//255
			animations.add(new AnimationDef("longbow", "equipment", 10310656, 0, false, false, 0));//256
			animations.add(new AnimationDef("longbow", "equipment", 37281, 0, false, false, 0));//257

			// Short swords
			animations.add(new AnimationDef("shortsword", "equipment", 16737817, 0, true, false, 0)); //258 - bronze short sword
			animations.add(new AnimationDef("shortsword", "equipment", 15654365, 0, true, false, 0));//259
			animations.add(new AnimationDef("shortsword", "equipment", 15658734, 0, true, false, 0));//260
			animations.add(new AnimationDef("shortsword", "equipment", 10072780, 0, true, false, 0));//261
			animations.add(new AnimationDef("shortsword", "equipment", 11717785, 0, true, false, 0));//262
			animations.add(new AnimationDef("shortsword", "equipment", 65535, 0, true, false, 0));//263
			animations.add(new AnimationDef("shortsword", "equipment", 3158064, 0, true, false, 0));//264

			// Daggers
			animations.add(new AnimationDef("dagger", "equipment", 16737817, 0, true, false, 0)); //265 - bronze dagger
			animations.add(new AnimationDef("dagger", "equipment", 15654365, 0, true, false, 0));//266
			animations.add(new AnimationDef("dagger", "equipment", 15658734, 0, true, false, 0));//267
			animations.add(new AnimationDef("dagger", "equipment", 10072780, 0, true, false, 0));//268
			animations.add(new AnimationDef("dagger", "equipment", 11717785, 0, true, false, 0));//269
			animations.add(new AnimationDef("dagger", "equipment", 65535, 0, true, false, 0));//270
			animations.add(new AnimationDef("dagger", "equipment", 3158064, 0, true, false, 0));//271

			// Poison daggers
			animations.add(new AnimationDef("poisoneddagger", "equipment", 16737817, 0, true, false, 0)); //272 - bronze p dagger
			animations.add(new AnimationDef("poisoneddagger", "equipment", 15654365, 0, true, false, 0)); //273 - iron p dagger
			animations.add(new AnimationDef("poisoneddagger", "equipment", 15658734, 0, true, false, 0)); //274 - steel p dagger
			animations.add(new AnimationDef("poisoneddagger", "equipment", 10072780, 0, true, false, 0)); //275 - mith p dagger
			animations.add(new AnimationDef("poisoneddagger", "equipment", 11717785, 0, true, false, 0)); //276 - addy p dagger
			animations.add(new AnimationDef("poisoneddagger", "equipment", 65535, 0, true, false, 0)); //277 - rune p dagger
			animations.add(new AnimationDef("poisoneddagger", "equipment", 3158064, 0, true, false, 0)); //278 - black p dagger

			// 2-handed swords
			animations.add(new AnimationDef("2hander", "equipment", 16737817, 0, true, false, 0)); //279 - bronze 2h
			animations.add(new AnimationDef("2hander", "equipment", 15654365, 0, true, false, 0)); //280 - iron 2h
			animations.add(new AnimationDef("2hander", "equipment", 15658734, 0, true, false, 0)); //281 - steel 2h
			animations.add(new AnimationDef("2hander", "equipment", 10072780, 0, true, false, 0)); //282 - black 2h
			animations.add(new AnimationDef("2hander", "equipment", 11717785, 0, true, false, 0)); //283 - mith 2h
			animations.add(new AnimationDef("2hander", "equipment", 65535, 0, true, false, 0)); //284 - addy 2h
			animations.add(new AnimationDef("2hander", "equipment", 3158064, 0, true, false, 0)); //285 - rune 2h

			// Unicorn masks
			animations.add(new AnimationDef("unicornmask", "equipment", 16777215, 16777215, 0, true, false, 0)); //286 - white unicorn mask
			animations.add(new AnimationDef("unicornmask", "equipment", 10878976, 1513239, 0, true, false, 0)); //287 - blood unicorn mask
			animations.add(new AnimationDef("unicornmask", "equipment", 1513239, 10878976, 0, true, false, 0)); //288 - black unicorn mask
			animations.add(new AnimationDef("unicornmask", "equipment", 16759039, 16777215, 0, true, false, 0)); //289 - pink unicorn mask

			// Wolf masks
			animations.add(new AnimationDef("wolfmask", "equipment", 16777215, 16777215, 0, true, false, 0)); //290 - white wolf mask
			animations.add(new AnimationDef("wolfmask", "equipment", 10878976, 1513239, 0, true, false, 0)); //291 - blood wolf mask
			animations.add(new AnimationDef("wolfmask", "equipment", 1513239, 10878976, 0, true, false, 0)); //292 - black wolf mask
			animations.add(new AnimationDef("wolfmask", "equipment", 16759039, 16777215, 0, true, false, 0)); //293 - pink wolf mask

			// Dragon items
			animations.add(new AnimationDef("dragonfullhelm", "equipment", 11189164, 0, true, false, 0)); //294 - dragon large
			animations.add(new AnimationDef("dragonbody", "equipment", 11189164, 0, true, false, 0)); //295 - dragon plate
			animations.add(new AnimationDef("dragonlegs", "equipment", 11189164, 0, true, false, 0)); //296 - dragon legs
			animations.add(new AnimationDef("fullhelm", "equipment", 16768685, 0, true, false, 0)); //297 - (does not alter)
			animations.add(new AnimationDef("fdragontop", "equipment", 16768685, 0, true, false, 0)); //298 - female dragon top
			animations.add(new AnimationDef("dragonskirt", "equipment", 16768685, 0, true, false, 0)); //299 - dragon skirt
			animations.add(new AnimationDef("fullhelm", "equipment", 10027084, 0, true, false, 0)); //300 - (does not alter)
			animations.add(new AnimationDef("platemailtop", "equipment", 10027084, 0, true, false, 0)); //301 - (does not alter)
			animations.add(new AnimationDef("hatchet", "equipment", 0, 0, true, false, 0)); // 302 - (does not alter)

			// Pumpkin head masks (missing, using wolf instead)
			animations.add(new AnimationDef("wolf", "npc", 2039583, 0, true, false, 0)); //303 - orange pumpkin head (missing, was using charColour 0)
			animations.add(new AnimationDef("wolf", "npc", 2039583, 0, true, false, 0)); //304 - red pumpkin head (missing, was 1513239)
			animations.add(new AnimationDef("wolf", "npc", 2039583, 0, true, false, 0)); //305 - yellow pumpkin head (missing, was 16776960)
			animations.add(new AnimationDef("wolf", "npc", 255, 0, true, false, 0)); //306 - blue pumpkin head (missing)
			animations.add(new AnimationDef("wolf", "npc", 11141375, 0, true, false, 0)); //307 - purple pumpkin head (missing)
			animations.add(new AnimationDef("wolf", "npc", 65280, 0, true, false, 0)); //308 - green pumpkin head (missing)

			// Skill capes and hoods
			animations.add(new AnimationDef("fishingcape", "equipment", 0, 0, true, false, 0)); //309 - fishing cape
			animations.add(new AnimationDef("cookingcape", "equipment", 0, 0, true, false, 0)); //310 - cooking cape
			animations.add(new AnimationDef("hood1", "equipment", 0, 0, true, false, 0)); //311 - fishing hood
			animations.add(new AnimationDef("warriorcape", "equipment", 0, 0, true, false, 0)); //312 - warrior cape
			animations.add(new AnimationDef("spottedcape", "equipment", 7692086, 0, true, false, 0)); //313 - spotted cape
			animations.add(new AnimationDef("attackcape", "equipment", 0, 0, true, false, 0)); //314 - attack cape

			// Easter basket (missing, using peppermintstick instead) and Gaia NPC (missing, using evilhoodie instead)
			animations.add(new AnimationDef("evilhoodie", "equipment", 0, 0, true, false, 0)); //315 - NPC Gaia (missing)
			animations.add(new AnimationDef("peppermintstick", "equipment", 0, 0, true, false, 0)); //316 - easter basket (missing)

			// Ironman items
			animations.add(new AnimationDef("fullhelm", "equipment", 11189164, 0, true, false, 0)); //317 - ironman helm
			animations.add(new AnimationDef("platemailtop", "equipment", 11189164, 0, true, false, 0)); //318 - ironman plate
			animations.add(new AnimationDef("platemaillegs", "equipment", 11189164, 0, true, false, 0)); //319 - ironman legs
			animations.add(new AnimationDef("fullhelm", "equipment", 16768685, 0, true, false, 0)); //320 - ultimate ironman helm
			animations.add(new AnimationDef("platemailtop", "equipment", 16768685, 0, true, false, 0)); //321 - ultimate ironman plate
			animations.add(new AnimationDef("platemaillegs", "equipment", 16768685, 0, true, false, 0)); //322 - ultimate ironman legs
			animations.add(new AnimationDef("fullhelm", "equipment", 10027084, 0, true, false, 0)); //323 - hc ironman helm
			animations.add(new AnimationDef("platemailtop", "equipment", 10027084, 0, true, false, 0)); //324 - hc ironman plate
			animations.add(new AnimationDef("platemaillegs", "equipment", 10027084, 0, true, false, 0)); //325 - hc ironman legs

			// Orange feather helms
			animations.add(new AnimationDef("fullhelmorange", "equipment", 16737817, 0, true, false, 0)); //326 - bronze helm orange
			animations.add(new AnimationDef("fullhelmorange", "equipment", 15654365, 0, true, false, 0)); //327 - iron helm orange
			animations.add(new AnimationDef("fullhelmorange", "equipment", 15658734, 0, true, false, 0)); //328 - steel helm orange
			animations.add(new AnimationDef("fullhelmorange", "equipment", 3158064, 0, true, false, 0)); //329 - black helm orange
			animations.add(new AnimationDef("fullhelmorange", "equipment", 10072780, 0, true, false, 0)); //330 - mith helm orange
			animations.add(new AnimationDef("fullhelmorange", "equipment", 11717785, 0, true, false, 0)); //331 - addy helm orange
			animations.add(new AnimationDef("fullhelmorange", "equipment", 65535, 0, true, false, 0)); //332 - rune helm orange

			// Blue feather helms
			animations.add(new AnimationDef("fullhelmblue", "equipment", 16737817, 0, true, false, 0)); //333 - bronze helm blue
			animations.add(new AnimationDef("fullhelmblue", "equipment", 15654365, 0, true, false, 0)); //334 - iron helm blue
			animations.add(new AnimationDef("fullhelmblue", "equipment", 15658734, 0, true, false, 0)); //335 - steel helm blue
			animations.add(new AnimationDef("fullhelmblue", "equipment", 3158064, 0, true, false, 0)); //336 - black helm blue
			animations.add(new AnimationDef("fullhelmblue", "equipment", 10072780, 0, true, false, 0)); //337 - mith helm blue
			animations.add(new AnimationDef("fullhelmblue", "equipment", 11717785, 0, true, false, 0)); //338 - addy helm blue
			animations.add(new AnimationDef("fullhelmblue", "equipment", 65535, 0, true, false, 0)); //339 - rune helm blue

			// Purple feather helms
			animations.add(new AnimationDef("fullhelmpurple", "equipment", 16737817, 0, true, false, 0)); //340 - bronze helm purple
			animations.add(new AnimationDef("fullhelmpurple", "equipment", 15654365, 0, true, false, 0)); //341 - iron helm purple
			animations.add(new AnimationDef("fullhelmpurple", "equipment", 15658734, 0, true, false, 0)); //342 - steel helm purple
			animations.add(new AnimationDef("fullhelmpurple", "equipment", 3158064, 0, true, false, 0)); //343 - black helm purple
			animations.add(new AnimationDef("fullhelmpurple", "equipment", 10072780, 0, true, false, 0)); //344 - mith helm purple
			animations.add(new AnimationDef("fullhelmpurple", "equipment", 11717785, 0, true, false, 0)); //345 - addy helm purple
			animations.add(new AnimationDef("fullhelmpurple", "equipment", 65535, 0, true, false, 0)); //346 - rune helm purple

			// Yellow feather helms
			animations.add(new AnimationDef("fullhelmyellow", "equipment", 16737817, 0, true, false, 0)); //347 - bronze helm yellow
			animations.add(new AnimationDef("fullhelmyellow", "equipment", 15654365, 0, true, false, 0)); //348 - iron helm yellow
			animations.add(new AnimationDef("fullhelmyellow", "equipment", 15658734, 0, true, false, 0)); //349 - steel helm yellow
			animations.add(new AnimationDef("fullhelmyellow", "equipment", 3158064, 0, true, false, 0)); //350 - black helm yellow
			animations.add(new AnimationDef("fullhelmyellow", "equipment", 10072780, 0, true, false, 0)); //351 - mith helm yellow
			animations.add(new AnimationDef("fullhelmyellow", "equipment", 11717785, 0, true, false, 0)); //352 - addy helm yellow
			animations.add(new AnimationDef("fullhelmyellow", "equipment", 65535, 0, true, false, 0)); //353 - rune helm yellow

			// Green feather helms
			animations.add(new AnimationDef("fullhelmgreen", "equipment", 16737817, 0, true, false, 0)); //354 - bronze helm green
			animations.add(new AnimationDef("fullhelmgreen", "equipment", 15654365, 0, true, false, 0)); //355 - iron helm green
			animations.add(new AnimationDef("fullhelmgreen", "equipment", 15658734, 0, true, false, 0)); //356 - steel helm green
			animations.add(new AnimationDef("fullhelmgreen", "equipment", 3158064, 0, true, false, 0)); //357 - black helm green
			animations.add(new AnimationDef("fullhelmgreen", "equipment", 10072780, 0, true, false, 0)); //358 - mith helm green
			animations.add(new AnimationDef("fullhelmgreen", "equipment", 11717785, 0, true, false, 0)); //359 - addy helm green
			animations.add(new AnimationDef("fullhelmgreen", "equipment", 65535, 0, true, false, 0)); //360 - rune helm green

			// Grey feather helms
			animations.add(new AnimationDef("fullhelmgrey", "equipment", 16737817, 0, true, false, 0)); //361 - bronze helm grey
			animations.add(new AnimationDef("fullhelmgrey", "equipment", 15654365, 0, true, false, 0)); //362 - iron helm grey
			animations.add(new AnimationDef("fullhelmgrey", "equipment", 15658734, 0, true, false, 0)); //363 - steel helm grey
			animations.add(new AnimationDef("fullhelmgrey", "equipment", 3158064, 0, true, false, 0)); //364 - black helm grey
			animations.add(new AnimationDef("fullhelmgrey", "equipment", 10072780, 0, true, false, 0)); //365 - mith helm grey
			animations.add(new AnimationDef("fullhelmgrey", "equipment", 11717785, 0, true, false, 0)); //366 - addy helm grey
			animations.add(new AnimationDef("fullhelmgrey", "equipment", 65535, 0, true, false, 0)); //367 - rune helm grey

			// Black feather helms
			animations.add(new AnimationDef("fullhelmblack", "equipment", 16737817, 0, true, false, 0)); //368 - bronze helm black
			animations.add(new AnimationDef("fullhelmblack", "equipment", 15654365, 0, true, false, 0)); //369 - iron helm black
			animations.add(new AnimationDef("fullhelmblack", "equipment", 15658734, 0, true, false, 0)); //370 - steel helm black
			animations.add(new AnimationDef("fullhelmblack", "equipment", 3158064, 0, true, false, 0)); //371 - black helm black
			animations.add(new AnimationDef("fullhelmblack", "equipment", 10072780, 0, true, false, 0)); //372 - mith helm black
			animations.add(new AnimationDef("fullhelmblack", "equipment", 11717785, 0, true, false, 0)); //373 - addy helm black
			animations.add(new AnimationDef("fullhelmblack", "equipment", 65535, 0, true, false, 0)); //374 - rune helm black

			// White feather helms
			animations.add(new AnimationDef("fullhelmwhite", "equipment", 16737817, 0, true, false, 0)); //375 - bronze helm white
			animations.add(new AnimationDef("fullhelmwhite", "equipment", 15654365, 0, true, false, 0)); //376 - iron helm white
			animations.add(new AnimationDef("fullhelmwhite", "equipment", 15658734, 0, true, false, 0)); //377 - steel helm white
			animations.add(new AnimationDef("fullhelmwhite", "equipment", 3158064, 0, true, false, 0)); //378 - black helm white
			animations.add(new AnimationDef("fullhelmwhite", "equipment", 10072780, 0, true, false, 0)); //379 - mith helm white
			animations.add(new AnimationDef("fullhelmwhite", "equipment", 11717785, 0, true, false, 0)); //380 - addy helm white
			animations.add(new AnimationDef("fullhelmwhite", "equipment", 65535, 0, true, false, 0)); //381 - rune helm white

			// Greatwood NPC (missing, using evilhoodie instead) and skill capes
			animations.add(new AnimationDef("evilhoodie", "equipment", 5453066, 0, true, false, 0)); //382 NPC Greatwood tree boss (missing)
			animations.add(new AnimationDef("smithingcape", "equipment", 0, 0, true, false, 0)); //383 smithing cape
			animations.add(new AnimationDef("strengthcape", "equipment", 0, 0, true, false, 0)); //384 strength cape
			animations.add(new AnimationDef("hitscape", "equipment", 0, 0, true, false, 0)); //385 hits cape

			// Fox mask
			animations.add(new AnimationDef("wolfmask", "equipment", 16730368, 16446686, 0, true, false, 0)); //386 - fox mask

			// Spears
			animations.add(new AnimationDef("spear", "equipment", 0xBB4B12, 0, true, false, 0)); //387 - bronze spear
			animations.add(new AnimationDef("spear", "equipment", 0xAFA2A2, 0, true, false, 0)); //388 - iron spear
			animations.add(new AnimationDef("spear", "equipment", 0xAFAFAF, 0, true, false, 0)); //389 - steel spear
			animations.add(new AnimationDef("spear", "equipment", 0x708396, 0, true, false, 0)); //390 - mith spear
			animations.add(new AnimationDef("spear", "equipment", 0x839670, 0, true, false, 0)); //391 - addy spear
			animations.add(new AnimationDef("spear", "equipment", 48059, 0, true, false, 0)); //392 - rune spear

			// Xmas
			animations.add(new AnimationDef("xmasapron", "equipment", 0, 0, true, false, 0)); //393
			animations.add(new AnimationDef("xmascape", "equipment", 0, 0, true, false, 0)); //394
			animations.add(new AnimationDef("santabody", "equipment", 0, 0, true, false, 0)); //395
			animations.add(new AnimationDef("santalegs", "equipment", 0, 0, true, false, 0)); //396
			animations.add(new AnimationDef("santahat2", "equipment", 0, 0, true, false, 0)); //397
			animations.add(new AnimationDef("santamittens", "equipment", 0, 0, true, false, 0)); //398
			animations.add(new AnimationDef("satansgloveswht", "equipment", 0, 0, true, false, 0)); //399
			animations.add(new AnimationDef("greensantahat", "equipment", 0, 0, true, false, 0)); //400
			animations.add(new AnimationDef("antlers", "equipment", 0, 0, true, false, 0)); //401

			//Dragon 2H
			animations.add(new AnimationDef("2hander", "equipment", 16711748, 0, true, false, 0)); //402 d2h

			//Dragon Scale Mail
			animations.add(new AnimationDef("dragonscalemail", "equipment", 0, 0, true, false, 0));//403

			//Updated Necklaces and Amulets
			//Sapphire
			animations.add(new AnimationDef("necklace2", "equipment", 16763980, 19711, 0, true, false, 0)); //404
			animations.add(new AnimationDef("amulet", "equipment", 16763980, 19711, 0, true, false, 0)); //405
			//Emerald
			animations.add(new AnimationDef("necklace2", "equipment", 16763980, 3394611, 0, true, false, 0)); //406
			animations.add(new AnimationDef("amulet", "equipment", 16763980, 3394611, 0, true, false, 0)); //407
			//Ruby
			animations.add(new AnimationDef("necklace2", "equipment", 16763980, 16724736, 0, true, false, 0)); //408
			animations.add(new AnimationDef("amulet", "equipment", 16763980, 16724736, 0, true, false, 0)); //409
			//Diamond
			animations.add(new AnimationDef("necklace2", "equipment", 16763980, 16184564, 0, true, false, 0)); //410
			animations.add(new AnimationDef("amulet", "equipment", 16763980, 16184564, 0, true, false, 0)); //411
			//Dragonstone
			animations.add(new AnimationDef("necklace2", "equipment", 16763980, 12255487, 0, true, false, 0)); //412
			animations.add(new AnimationDef("amulet", "equipment", 16763980, 12255487, 0, true, false, 0)); //413
			//Annas, Accuracy, Ghostspeak
			animations.add(new AnimationDef("amulet2", "equipment", 0, 0, 0, true, false, 0)); //414
			//Beads of the dead
			animations.add(new AnimationDef("amulet2", "equipment", 16737817, 0, 0, true, false, 0)); //415
			//Lucien / Armadyl
			animations.add(new AnimationDef("lucians", "equipment", 3158064, 12750123, 0, true, false, 0)); //416
			animations.add(new AnimationDef("lucians", "equipment", 0, 12750123, 0, true, false, 0)); //417
			//Glarial
			animations.add(new AnimationDef("necklace2", "equipment", 0, 3394611, 0, true, false, 0)); //418
			//Symbols
			animations.add(new AnimationDef("sarasymbol", "equipment", 0, 0, 0, true, false, 0)); //419
			animations.add(new AnimationDef("zammysymbol", "equipment", 0, 0, 0, true, false, 0)); //420

			//Elemental Staves
			//air
			animations.add(new AnimationDef("elementalstaff", "equipment", 0x0AE5E4, 0, 0, true, false, 0)); //421
			//water
			animations.add(new AnimationDef("elementalstaff", "equipment", 0x0401DC, 0, 0, true, false, 0)); //422
			//earth
			animations.add(new AnimationDef("elementalstaff", "equipment", 0x642E01, 0, 0, true, false, 0)); //423
			//fire
			animations.add(new AnimationDef("elementalstaff", "equipment", 0xD40203, 0, 0, true, false, 0)); //424

			//New Leather Items
			//Chaps
			animations.add(new AnimationDef("leatherchaps", "equipment", 3, 0, 0, true, false, 0)); //425
			//Female Top
			animations.add(new AnimationDef("fleatherbody", "equipment", 0, 0, 0, true, false, 0)); //426
			//Female Skirt
			animations.add(new AnimationDef("leatherskirt", "equipment", 3, 0, 0, true, false, 0)); //427

			//Skill Cape Batch One
			//animations.add(new AnimationDef("attackcape", "equipment", 0, 0, true, false, 0)); //315
			//animations.add(new AnimationDef("cookingcape", "equipment", 0, 0, true, false, 0)); //311
			animations.add(new AnimationDef("thievingcape", "equipment", 0, 0, true, false, 0)); //428
			animations.add(new AnimationDef("fletchingcape", "equipment", 0, 0, true, false, 0)); //429
			animations.add(new AnimationDef("miningcape", "equipment", 0, 0, true, false, 0)); //430

			// April Fools Items
			animations.add(new AnimationDef("plaguemask", "equipment", 0, 0, true, false, 0)); // 431
			animations.add(new AnimationDef("rubberchicken", "equipment", 0, 0, true, false, 0)); // 432

			// Pickaxe
			animations.add(new AnimationDef("pickaxe", "equipment", 16737817, 0, true, false, 0)); // bronze 433
			animations.add(new AnimationDef("pickaxe", "equipment", 15654365, 0, true, false, 0)); // iron 434
			animations.add(new AnimationDef("pickaxe", "equipment", 15658734, 0, true, false, 0)); // steel 435
			animations.add(new AnimationDef("pickaxe", "equipment", 10072780, 0, true, false, 0)); // mithril 436
			animations.add(new AnimationDef("pickaxe", "equipment", 11717785, 0, true, false, 0)); // adamant 437
			animations.add(new AnimationDef("pickaxe", "equipment", 65535, 0, true, false, 0)); // rune 438

			// More skill capes (batch 2)
			// animations.add(new AnimationDef("fishingcape", "equipment", 0, 0, true, false, 0)); //310 - fishing cape
			// animations.add(new AnimationDef("strengthcape", "equipment", 0, 0, true, false, 0)); //385 strength cape
			// animations.add(new AnimationDef("smithingcape", "equipment", 0, 0, true, false, 0)); //384 smithing cape
			animations.add(new AnimationDef("magiccape", "equipment", 0, 0, true, false, 0)); // 439
			animations.add(new AnimationDef("craftingcape", "equipment", 0, 0, true, false, 0)); // 440

			// Chainmail leg
			animations.add(new AnimationDef("chainmaillegs", "equipment", 16737817, 0, true, false, 0)); // bronze 441
			animations.add(new AnimationDef("chainmaillegs", "equipment", 15654365, 0, true, false, 0)); // iron 442
			animations.add(new AnimationDef("chainmaillegs", "equipment", 15658734, 0, true, false, 0)); // steel 443
			animations.add(new AnimationDef("chainmaillegs", "equipment", 10072780, 0, true, false, 0)); // mithril 444
			animations.add(new AnimationDef("chainmaillegs", "equipment", 11717785, 0, true, false, 0)); // adamant 445
			animations.add(new AnimationDef("chainmaillegs", "equipment", 65535, 0, true, false, 0)); // rune 446
			animations.add(new AnimationDef("chainmaillegs", "equipment", 3158064, 0, true, false, 0)); //black 447

			// Additional dragon items
			animations.add(new AnimationDef("dragonkiteshield", "equipment", 0, 0, true, false, 0)); //448 - dragon kite shield

			// CTF
			animations.add(new AnimationDef("ctfflag", "equipment", 0, 0, true, false, 0)); //449 - white ctf flag
			animations.add(new AnimationDef("ctfflag", "equipment", 4246592, 0, true, false, 0)); //450 - guthix ctf flag
			animations.add(new AnimationDef("ctfflag", "equipment", 4210926, 0, true, false, 0)); //451 - saradomin ctf flag
			animations.add(new AnimationDef("ctfflag", "equipment", 16711680, 0, true, false, 0)); //452 - zamorak ctf flag
			animations.add(new AnimationDef("wings", "equipment", 0, 0, true, false, 0)); //453 - white wings
			animations.add(new AnimationDef("mvalkyriehelm", "equipment", 0, 0, true, false, 0)); //454 - medium valkyrie helmet
			animations.add(new AnimationDef("mvalkyriehelm", "equipment", 4246592, 0, true, false, 0)); //455 - medium guthix valkyrie helmet
			animations.add(new AnimationDef("mvalkyriehelm", "equipment", 4210926, 0, true, false, 0)); //456 - medium saradomin valkyrie helmet
			animations.add(new AnimationDef("mvalkyriehelm", "equipment", 16711680, 0, true, false, 0)); //457 - medium zamorak valkyrie helmet
			animations.add(new AnimationDef("valkyriehelm", "equipment", 0, 0, true, false, 0)); //458 - large valkyrie helmet
			animations.add(new AnimationDef("valkyriehelm", "equipment", 4246592, 0, true, false, 0)); //459 - large guthix valkyrie helmet
			animations.add(new AnimationDef("valkyriehelm", "equipment", 4210926, 0, true, false, 0)); //460 - large saradomin valkyrie helmet
			animations.add(new AnimationDef("valkyriehelm", "equipment", 16711680, 0, true, false, 0)); //461 - large zamorak valkyrie helmet
			animations.add(new AnimationDef("guthixcape", "equipment", 0, 0, true, false, 0)); //462 - guthix cape
			animations.add(new AnimationDef("saracape", "equipment", 0, 0, true, false, 0)); //463 - saradomin cape
			animations.add(new AnimationDef("zammycape", "equipment", 0, 0, true, false, 0)); //464 - zamorak cape
			animations.add(new AnimationDef("wings", "equipment", 4246592, 1513239, 0, true, false, 0)); //465 - guthix wings
			animations.add(new AnimationDef("wings", "equipment", 4210926, 1513239, 0, true, false, 0)); //466 - saradomin wings
			animations.add(new AnimationDef("wings", "equipment", 16711680, 1513239, 0, true, false, 0)); //467 - zamorak wings
			animations.add(new AnimationDef("dagger", "equipment", 16711748, 0, true, false, 0)); //468 - dragon dagger
			animations.add(new AnimationDef("poisoneddagger", "equipment", 16711748, 0, true, false, 0)); //469 - poison dragon dagger
			animations.add(new AnimationDef("crossbow", "equipment", 16711748, 0, false, false, 0)); //470 - dragon crossbow
			animations.add(new AnimationDef("longbow", "equipment", 16711748, 0, false, false, 0)); //471 - dragon longbow

			// Easter 2021
			animations.add(new AnimationDef("bunny", "npc", 16777215, 0, true, false, 0)); //472
			animations.add(new AnimationDef("duck", "npc", 16777215, 0, true, false, 0));//473
			animations.add(new AnimationDef("bunnymorph", "npc", 16777215, 0, true, false, 0));//474
			animations.add(new AnimationDef("eggmorph", "npc", 16777215, 0, true, false, 0));//475

			// Scimitar Animation
			animations.add(new AnimationDef("scimitar", "equipment", 16737817, 0, true, false, 0)); // 476 Bronze Scimitar
			animations.add(new AnimationDef("scimitar", "equipment", 15654365, 0, true, false, 0)); // 477 Iron Scimitar
			animations.add(new AnimationDef("scimitar", "equipment", 15658734, 0, true, false, 0)); // 478 Steel Scimitar
			animations.add(new AnimationDef("scimitar", "equipment", 3158064, 0, true, false, 0)); // 479 Black Scimitar
			animations.add(new AnimationDef("scimitar", "equipment", 10072780, 0, true, false, 0)); // 480 Mithril Scimitar
			animations.add(new AnimationDef("scimitar", "equipment", 11717785, 0, true, false, 0)); // 481 Adamantite Scimitar
			animations.add(new AnimationDef("scimitar", "equipment", 65535, 0, true, false, 0)); // 482 Rune Scimitar

			// Halloween 2021
			animations.add(new AnimationDef("deathmask", "equipment", 0, 0, true, false, 0)); // 483 death mask

			// Christmas 2021
			animations.add(new AnimationDef("yoyo", "equipment", 0, 0, true, false, 0)); // 484 yoyo in hand
			animations.add(new AnimationDef("yoyo_anim_1", "equipment", 0, 0, true, false, 0)); // 485 yoyo up-down 1
			animations.add(new AnimationDef("yoyo_anim_2", "equipment", 0, 0, true, false, 0)); // 486 yoyo up-down 2
			animations.add(new AnimationDef("yoyo_anim_3", "equipment", 0, 0, true, false, 0)); // 487 yoyo up-down 3
			animations.add(new AnimationDef("yoyo_anim_4", "equipment", 0, 0, true, false, 0)); // 488 yoyo up-down 4
			animations.add(new AnimationDef("yoyo_anim_crazy_1", "equipment", 0, 0, true, false, 0)); // 489 yoyo crazy 1'oclock
			animations.add(new AnimationDef("yoyo_anim_crazy_2", "equipment", 0, 0, true, false, 0)); // 490 yoyo crazy 2'oclock
			animations.add(new AnimationDef("yoyo_anim_crazy_3", "equipment", 0, 0, true, false, 0)); // 491 yoyo crazy 3'oclock
			animations.add(new AnimationDef("yoyo_anim_crazy_4", "equipment", 0, 0, true, false, 0)); // 492 yoyo crazy 4'oclock
			animations.add(new AnimationDef("yoyo_anim_crazy_5", "equipment", 0, 0, true, false, 0)); // 493 yoyo crazy 5'oclock
			// for 6'oclock, just use yoyo_anim_4
			animations.add(new AnimationDef("yoyo_anim_crazy_7", "equipment", 0, 0, true, false, 0)); // 494 yoyo crazy 7'oclock
			animations.add(new AnimationDef("yoyo_anim_crazy_8", "equipment", 0, 0, true, false, 0)); // 495 yoyo crazy 8'oclock
			animations.add(new AnimationDef("yoyo_anim_crazy_9", "equipment", 0, 0, true, false, 0)); // 496 yoyo crazy 9'oclock
			animations.add(new AnimationDef("yoyo_anim_crazy_10", "equipment", 0, 0, true, false, 0)); // 497 yoyo crazy 10'oclock
			animations.add(new AnimationDef("yoyo_anim_crazy_11", "equipment", 0, 0, true, false, 0)); // 498 yoyo crazy 11'oclock
			animations.add(new AnimationDef("yoyo_anim_crazy_12", "equipment", 0, 0, true, false, 0)); // 499 yoyo crazy 12'oclock

			// Easter 2022 aka Peeling the Onion
			animations.add(new AnimationDef("ogreears", "equipment", 0xb5ff1d, 0, true, false, 0)); // 500 ogre ears
			animations.add(new AnimationDef("leathervest", "equipment", 0, 0, true, false, 0)); // 501 leather vest

			//Crowns
			//Gold
			animations.add(new AnimationDef("crown", "equipment", 16763980,0, true, false, 0));//502
			//Sapphire
			animations.add(new AnimationDef("crown", "equipment", 19711, 0, true, false, 0)); //503
			//Emerald
			animations.add(new AnimationDef("crown", "equipment", 3394611,0, true, false, 0)); //504
			//Ruby
			animations.add(new AnimationDef("crown", "equipment", 16724736,0, true, false, 0)); //505
			//Diamond
			animations.add(new AnimationDef("crown", "equipment", 16184564,0, true, false, 0)); //506
			//Dragonstone
			animations.add(new AnimationDef("crown", "equipment", 12255487,0, true, false, 0)); //507

			// Halloween 2022
			animations.add(new AnimationDef("halloweenmask", "equipment", 16711935, 0, true, false, 0)); // 508
			animations.add(new AnimationDef("pridecape", "equipment", 0, 0, true, false, 0)); // 509
			animations.add(new AnimationDef("halloweenmask", "equipment", 4, 0, true, false, 0)); // 510

			// Rest of the skillcapes
			animations.add(new AnimationDef("agilitycape", "equipment", 0, 0, true, false, 0)); //511
			animations.add(new AnimationDef("defensecape", "equipment", 0, 0, true, false, 0)); //512
			animations.add(new AnimationDef("firemakingcape", "equipment", 0, 0, true, false, 0)); //513
			animations.add(new AnimationDef("herblawcape", "equipment", 0, 0, true, false, 0)); //514
			animations.add(new AnimationDef("hitscape", "equipment", 0, 0, true, false, 0)); //515
			animations.add(new AnimationDef("prayercape", "equipment", 0, 0, true, false, 0)); //516
			animations.add(new AnimationDef("rangingcape", "equipment", 0, 0, true, false, 0)); //517
			animations.add(new AnimationDef("woodcuttingcape", "equipment", 0, 0, true, false, 0)); //518
			animations.add(new AnimationDef("harvestingcape", "equipment", 0, 0, true, false, 0)); //519
			animations.add(new AnimationDef("runecraftingcape", "equipment", 0, 0, true, false, 0)); //520
			animations.add(new AnimationDef("questcape", "equipment", 0, 0, true, false, 0)); //521
			// There isn't an animation for the max cape yet, but I wanted to reserve the ID.
			animations.add(new AnimationDef("fishingcape", "equipment", 0, 0, true, false, 0)); //522

			// Female Chain Mail Tops
			animations.add(new AnimationDef("fchainmail", "equipment", 16737817, 0, true, false, 0));//523 bronze
			animations.add(new AnimationDef("fchainmail", "equipment", 15654365, 0, true, false, 0));//524 iron
			animations.add(new AnimationDef("fchainmail", "equipment", 15658734, 0, true, false, 0));//525 steel
			animations.add(new AnimationDef("fchainmail", "equipment", 10072780, 0, true, false, 0));//526 mithril
			animations.add(new AnimationDef("fchainmail", "equipment", 11717785, 0, true, false, 0));//527 adamant
			animations.add(new AnimationDef("fchainmail", "equipment", 65535, 0, true, false, 0));//528 rune
			animations.add(new AnimationDef("fchainmail", "equipment", 3158064, 0, true, false, 0));//529 black
			animations.add(new AnimationDef("fdragonscalemail", "equipment", 0, 0, true, false, 0));//530
			animations.add(new AnimationDef("mortimertorso", "equipment", 0, 0, true, false, 0));//531
			animations.add(new AnimationDef("randolphtorso", "equipment", 0, 0, true, false, 0));//532
			animations.add(new AnimationDef("biggum", "npc", 0xFFFFFF, 0, true, false, 0));//533

			// Ironman plate tops
			animations.add(new AnimationDef("fplatemailtop", "equipment", 11189164, 0, true, false, 0)); //534 - ironman plate top
			animations.add(new AnimationDef("fplatemailtop", "equipment", 16768685, 0, true, false, 0)); //535 - ultimate ironman plate top
			animations.add(new AnimationDef("fplatemailtop", "equipment", 10027084, 0, true, false, 0)); //536 - hc ironman plate top

			// Ironman plated skirts
			animations.add(new AnimationDef("armorskirt", "equipment", 11189164, 0, true, false, 0));//537 - ironman plated skirt
			animations.add(new AnimationDef("armorskirt", "equipment", 16768685, 0, true, false, 0));//538 - ultimate ironman palted skirt
			animations.add(new AnimationDef("armorskirt", "equipment", 10027084, 0, true, false, 0));//539 - hc ironman plated skirt

			// Halloween 2023
			animations.add(new AnimationDef("halloweenmask_pink", "equipment", 0, 0, true, false, 0));//540 - pink halloween mask
			animations.add(new AnimationDef("skeletonmorph", "npc", 16777215, 0, true, false, 0));//541

			// Christmas 2023
			animations.add(new AnimationDef("christmassweater", "equipment", 0x820101, 0, true, false, 0));//542 - red
			animations.add(new AnimationDef("christmassweater", "equipment", 0xc3c90e, 0, true, false, 0));//543 - yellow
			animations.add(new AnimationDef("christmassweater", "equipment", 0x012c82, 0, true, false, 0));//544 - blue
			animations.add(new AnimationDef("christmassweater", "equipment", 0x6804b5, 0, true, false, 0));//545 - purple
			animations.add(new AnimationDef("christmassweater", "equipment", 0xcf7602, 0, true, false, 0));//546 - orange
			animations.add(new AnimationDef("christmassweater", "equipment", 0x106105, 0, true, false, 0));//547 - green
			animations.add(new AnimationDef("partyhat", "equipment", 0x1a1a1a, 0, true, false, 0));//548 - black party hat
			animations.add(new AnimationDef("pinksantahat", "equipment", 0, 0, true, false, 0));//549 - pink santa hat
			animations.add(new AnimationDef("fchristmassweater", "equipment", 0x820101, 0, true, false, 0));//550 - red
			animations.add(new AnimationDef("fchristmassweater", "equipment", 0xc3c90e, 0, true, false, 0));//551 - yellow
			animations.add(new AnimationDef("fchristmassweater", "equipment", 0x012c82, 0, true, false, 0));//552 - blue
			animations.add(new AnimationDef("fchristmassweater", "equipment", 0x6804b5, 0, true, false, 0));//553 - purple
			animations.add(new AnimationDef("fchristmassweater", "equipment", 0xcf7602, 0, true, false, 0));//554 - orange
			animations.add(new AnimationDef("fchristmassweater", "equipment", 0x106105, 0, true, false, 0));//555 - green

			// Halloween 2024
			animations.add(new AnimationDef("boomstick", "equipment", 0, 0, true, false, 0));//556
			animations.add(new AnimationDef("ashtorso", "equipment", 0, 0, true, false, 0));//557
			animations.add(new AnimationDef("amulet", "equipment", 16763980, 3,0, true, false, 0)); //558
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x7a5a38, 0, true, false, 0));//559 cow
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x7b8f2f, 0, true, false, 0));//560 goblin
			animations.add(new AnimationDef("mediumhelm", "equipment", 0xcfc7a4, 0, true, false, 0));//561 unicorn
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x5a3b24, 0, true, false, 0));//562 bear
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x2e2a38, 0, true, false, 0));//563 black unicorn
			animations.add(new AnimationDef("mediumhelm", "equipment", 0xb08b45, 0, true, false, 0));//564 scorpion
			animations.add(new AnimationDef("chainmail", "equipment", 0x7a5a38, 0, true, false, 0));//565 cow
			animations.add(new AnimationDef("chainmail", "equipment", 0x7b8f2f, 0, true, false, 0));//566 goblin
			animations.add(new AnimationDef("chainmail", "equipment", 0xcfc7a4, 0, true, false, 0));//567 unicorn
			animations.add(new AnimationDef("chainmail", "equipment", 0x5a3b24, 0, true, false, 0));//568 bear
			animations.add(new AnimationDef("chainmail", "equipment", 0x2e2a38, 0, true, false, 0));//569 black unicorn
			animations.add(new AnimationDef("chainmail", "equipment", 0xb08b45, 0, true, false, 0));//570 scorpion
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x7a5a38, 0, true, false, 0));//571 cow
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x7b8f2f, 0, true, false, 0));//572 goblin
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0xcfc7a4, 0, true, false, 0));//573 unicorn
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x5a3b24, 0, true, false, 0));//574 bear
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x2e2a38, 0, true, false, 0));//575 black unicorn
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0xb08b45, 0, true, false, 0));//576 scorpion
			animations.add(new AnimationDef("leathergloves", "equipment", 0x7a5a38, 0, true, false, 0));//577 cow
			animations.add(new AnimationDef("leathergloves", "equipment", 0x7b8f2f, 0, true, false, 0));//578 goblin
			animations.add(new AnimationDef("leathergloves", "equipment", 0xcfc7a4, 0, true, false, 0));//579 unicorn
			animations.add(new AnimationDef("leathergloves", "equipment", 0x5a3b24, 0, true, false, 0));//580 bear
			animations.add(new AnimationDef("leathergloves", "equipment", 0x2e2a38, 0, true, false, 0));//581 black unicorn
			animations.add(new AnimationDef("leathergloves", "equipment", 0xb08b45, 0, true, false, 0));//582 scorpion
			animations.add(new AnimationDef("boots", "equipment", 0x7a5a38, 0, true, false, 0));//583 cow
			animations.add(new AnimationDef("boots", "equipment", 0x7b8f2f, 0, true, false, 0));//584 goblin
			animations.add(new AnimationDef("boots", "equipment", 0xcfc7a4, 0, true, false, 0));//585 unicorn
			animations.add(new AnimationDef("boots", "equipment", 0x5a3b24, 0, true, false, 0));//586 bear
			animations.add(new AnimationDef("boots", "equipment", 0x2e2a38, 0, true, false, 0));//587 black unicorn
			animations.add(new AnimationDef("boots", "equipment", 0xb08b45, 0, true, false, 0));//588 scorpion
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x5d6570, 0, true, false, 0));//589 wolf-hide
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x55271f, 0, true, false, 0));//590 spider-carapace
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x7e6e59, 0, true, false, 0));//591 giant-hide
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x4f5933, 0, true, false, 0));//592 ogre-hide
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x4f7d70, 0, true, false, 0));//593 baby-dragon-hide
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x3d377a, 0, true, false, 0));//594 magic-spider-carapace
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x50582d, 0, true, false, 0));//595 moss-giant-hide
			animations.add(new AnimationDef("mediumhelm", "equipment", 0xa6bdcf, 0, true, false, 0));//596 ice-giant-hide
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x63171c, 0, true, false, 0));//597 demon-hide
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x4b403d, 0, true, false, 0));//598 hellhound-hide
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x9a4916, 0, true, false, 0));//599 fire-giant-hide
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x20478d, 0, true, false, 0));//600 blue-dragon-hide
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x306c33, 0, true, false, 0));//601 dragon-hide
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x8c2620, 0, true, false, 0));//602 red-dragon-hide
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x24191b, 0, true, false, 0));//603 black-demon-hide
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x1f2530, 0, true, false, 0));//604 black-dragon-hide
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x35241f, 0, true, false, 0));//605 balrog-hide
			animations.add(new AnimationDef("mediumhelm", "equipment", 0x171b20, 0, true, false, 0));//606 king-black-dragon-hide
			animations.add(new AnimationDef("chainmail", "equipment", 0x5d6570, 0, true, false, 0));//607 wolf-hide
			animations.add(new AnimationDef("chainmail", "equipment", 0x55271f, 0, true, false, 0));//608 spider-carapace
			animations.add(new AnimationDef("chainmail", "equipment", 0x7e6e59, 0, true, false, 0));//609 giant-hide
			animations.add(new AnimationDef("chainmail", "equipment", 0x4f5933, 0, true, false, 0));//610 ogre-hide
			animations.add(new AnimationDef("chainmail", "equipment", 0x4f7d70, 0, true, false, 0));//611 baby-dragon-hide
			animations.add(new AnimationDef("chainmail", "equipment", 0x3d377a, 0, true, false, 0));//612 magic-spider-carapace
			animations.add(new AnimationDef("chainmail", "equipment", 0x50582d, 0, true, false, 0));//613 moss-giant-hide
			animations.add(new AnimationDef("chainmail", "equipment", 0xa6bdcf, 0, true, false, 0));//614 ice-giant-hide
			animations.add(new AnimationDef("chainmail", "equipment", 0x63171c, 0, true, false, 0));//615 demon-hide
			animations.add(new AnimationDef("chainmail", "equipment", 0x4b403d, 0, true, false, 0));//616 hellhound-hide
			animations.add(new AnimationDef("chainmail", "equipment", 0x9a4916, 0, true, false, 0));//617 fire-giant-hide
			animations.add(new AnimationDef("chainmail", "equipment", 0x20478d, 0, true, false, 0));//618 blue-dragon-hide
			animations.add(new AnimationDef("chainmail", "equipment", 0x306c33, 0, true, false, 0));//619 dragon-hide
			animations.add(new AnimationDef("chainmail", "equipment", 0x8c2620, 0, true, false, 0));//620 red-dragon-hide
			animations.add(new AnimationDef("chainmail", "equipment", 0x24191b, 0, true, false, 0));//621 black-demon-hide
			animations.add(new AnimationDef("chainmail", "equipment", 0x1f2530, 0, true, false, 0));//622 black-dragon-hide
			animations.add(new AnimationDef("chainmail", "equipment", 0x35241f, 0, true, false, 0));//623 balrog-hide
			animations.add(new AnimationDef("chainmail", "equipment", 0x171b20, 0, true, false, 0));//624 king-black-dragon-hide
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x5d6570, 0, true, false, 0));//625 wolf-hide
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x55271f, 0, true, false, 0));//626 spider-carapace
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x7e6e59, 0, true, false, 0));//627 giant-hide
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x4f5933, 0, true, false, 0));//628 ogre-hide
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x4f7d70, 0, true, false, 0));//629 baby-dragon-hide
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x3d377a, 0, true, false, 0));//630 magic-spider-carapace
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x50582d, 0, true, false, 0));//631 moss-giant-hide
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0xa6bdcf, 0, true, false, 0));//632 ice-giant-hide
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x63171c, 0, true, false, 0));//633 demon-hide
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x4b403d, 0, true, false, 0));//634 hellhound-hide
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x9a4916, 0, true, false, 0));//635 fire-giant-hide
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x20478d, 0, true, false, 0));//636 blue-dragon-hide
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x306c33, 0, true, false, 0));//637 dragon-hide
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x8c2620, 0, true, false, 0));//638 red-dragon-hide
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x24191b, 0, true, false, 0));//639 black-demon-hide
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x1f2530, 0, true, false, 0));//640 black-dragon-hide
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x35241f, 0, true, false, 0));//641 balrog-hide
			animations.add(new AnimationDef("chainmaillegs", "equipment", 0x171b20, 0, true, false, 0));//642 king-black-dragon-hide
			animations.add(new AnimationDef("leathergloves", "equipment", 0x5d6570, 0, true, false, 0));//643 wolf-hide
			animations.add(new AnimationDef("leathergloves", "equipment", 0x55271f, 0, true, false, 0));//644 spider-carapace
			animations.add(new AnimationDef("leathergloves", "equipment", 0x7e6e59, 0, true, false, 0));//645 giant-hide
			animations.add(new AnimationDef("leathergloves", "equipment", 0x4f5933, 0, true, false, 0));//646 ogre-hide
			animations.add(new AnimationDef("leathergloves", "equipment", 0x4f7d70, 0, true, false, 0));//647 baby-dragon-hide
			animations.add(new AnimationDef("leathergloves", "equipment", 0x3d377a, 0, true, false, 0));//648 magic-spider-carapace
			animations.add(new AnimationDef("leathergloves", "equipment", 0x50582d, 0, true, false, 0));//649 moss-giant-hide
			animations.add(new AnimationDef("leathergloves", "equipment", 0xa6bdcf, 0, true, false, 0));//650 ice-giant-hide
			animations.add(new AnimationDef("leathergloves", "equipment", 0x63171c, 0, true, false, 0));//651 demon-hide
			animations.add(new AnimationDef("leathergloves", "equipment", 0x4b403d, 0, true, false, 0));//652 hellhound-hide
			animations.add(new AnimationDef("leathergloves", "equipment", 0x9a4916, 0, true, false, 0));//653 fire-giant-hide
			animations.add(new AnimationDef("leathergloves", "equipment", 0x20478d, 0, true, false, 0));//654 blue-dragon-hide
			animations.add(new AnimationDef("leathergloves", "equipment", 0x306c33, 0, true, false, 0));//655 dragon-hide
			animations.add(new AnimationDef("leathergloves", "equipment", 0x8c2620, 0, true, false, 0));//656 red-dragon-hide
			animations.add(new AnimationDef("leathergloves", "equipment", 0x24191b, 0, true, false, 0));//657 black-demon-hide
			animations.add(new AnimationDef("leathergloves", "equipment", 0x1f2530, 0, true, false, 0));//658 black-dragon-hide
			animations.add(new AnimationDef("leathergloves", "equipment", 0x35241f, 0, true, false, 0));//659 balrog-hide
			animations.add(new AnimationDef("leathergloves", "equipment", 0x171b20, 0, true, false, 0));//660 king-black-dragon-hide
			animations.add(new AnimationDef("boots", "equipment", 0x5d6570, 0, true, false, 0));//661 wolf-hide
			animations.add(new AnimationDef("boots", "equipment", 0x55271f, 0, true, false, 0));//662 spider-carapace
			animations.add(new AnimationDef("boots", "equipment", 0x7e6e59, 0, true, false, 0));//663 giant-hide
			animations.add(new AnimationDef("boots", "equipment", 0x4f5933, 0, true, false, 0));//664 ogre-hide
			animations.add(new AnimationDef("boots", "equipment", 0x4f7d70, 0, true, false, 0));//665 baby-dragon-hide
			animations.add(new AnimationDef("boots", "equipment", 0x3d377a, 0, true, false, 0));//666 magic-spider-carapace
			animations.add(new AnimationDef("boots", "equipment", 0x50582d, 0, true, false, 0));//667 moss-giant-hide
			animations.add(new AnimationDef("boots", "equipment", 0xa6bdcf, 0, true, false, 0));//668 ice-giant-hide
			animations.add(new AnimationDef("boots", "equipment", 0x63171c, 0, true, false, 0));//669 demon-hide
			animations.add(new AnimationDef("boots", "equipment", 0x4b403d, 0, true, false, 0));//670 hellhound-hide
			animations.add(new AnimationDef("boots", "equipment", 0x9a4916, 0, true, false, 0));//671 fire-giant-hide
			animations.add(new AnimationDef("boots", "equipment", 0x20478d, 0, true, false, 0));//672 blue-dragon-hide
			animations.add(new AnimationDef("boots", "equipment", 0x306c33, 0, true, false, 0));//673 dragon-hide
			animations.add(new AnimationDef("boots", "equipment", 0x8c2620, 0, true, false, 0));//674 red-dragon-hide
			animations.add(new AnimationDef("boots", "equipment", 0x24191b, 0, true, false, 0));//675 black-demon-hide
			animations.add(new AnimationDef("boots", "equipment", 0x1f2530, 0, true, false, 0));//676 black-dragon-hide
			animations.add(new AnimationDef("boots", "equipment", 0x35241f, 0, true, false, 0));//677 balrog-hide
			animations.add(new AnimationDef("boots", "equipment", 0x171b20, 0, true, false, 0));//678 king-black-dragon-hide
			animations.add(new AnimationDef("boots", "equipment", 16737817, 0, true, false, 0));//679 bronze greaves
			animations.add(new AnimationDef("boots", "equipment", 15654365, 0, true, false, 0));//680 iron greaves
			animations.add(new AnimationDef("boots", "equipment", 15658734, 0, true, false, 0));//681 steel greaves
			animations.add(new AnimationDef("boots", "equipment", 10072780, 0, true, false, 0));//682 mithril greaves
			animations.add(new AnimationDef("boots", "equipment", 11717785, 0, true, false, 0));//683 adamantite greaves
			animations.add(new AnimationDef("boots", "equipment", 65535, 0, true, false, 0));//684 rune greaves
			animations.add(new AnimationDef("fullhelm", "equipment", 0xb7c9d9, 0, true, false, 0));//685 tin
			animations.add(new AnimationDef("fullhelm", "equipment", 0xc86a2b, 0, true, false, 0));//686 copper
			animations.add(new AnimationDef("fullhelm", "equipment", 0x8ea6bb, 0, true, false, 0));//687 titan steel
			animations.add(new AnimationDef("fullhelm", "equipment", 0x5a3f7d, 0, true, false, 0));//688 orichalcum
			animations.add(new AnimationDef("platemailtop", "equipment", 0xb7c9d9, 0, true, false, 0));//689 tin
			animations.add(new AnimationDef("platemailtop", "equipment", 0xc86a2b, 0, true, false, 0));//690 copper
			animations.add(new AnimationDef("platemailtop", "equipment", 0x8ea6bb, 0, true, false, 0));//691 titan steel
			animations.add(new AnimationDef("platemailtop", "equipment", 0x5a3f7d, 0, true, false, 0));//692 orichalcum
			animations.add(new AnimationDef("platemaillegs", "equipment", 0xb7c9d9, 0, true, false, 0));//693 tin
			animations.add(new AnimationDef("platemaillegs", "equipment", 0xc86a2b, 0, true, false, 0));//694 copper
			animations.add(new AnimationDef("platemaillegs", "equipment", 0x8ea6bb, 0, true, false, 0));//695 titan steel
			animations.add(new AnimationDef("platemaillegs", "equipment", 0x5a3f7d, 0, true, false, 0));//696 orichalcum
			animations.add(new AnimationDef("kiteshield", "equipment", 0xb7c9d9, 0, true, false, 0));//697 tin
			animations.add(new AnimationDef("kiteshield", "equipment", 0xc86a2b, 0, true, false, 0));//698 copper
			animations.add(new AnimationDef("kiteshield", "equipment", 0x8ea6bb, 0, true, false, 0));//699 titan steel
			animations.add(new AnimationDef("kiteshield", "equipment", 0x5a3f7d, 0, true, false, 0));//700 orichalcum
			animations.add(new AnimationDef("boots", "equipment", 0xb7c9d9, 0, true, false, 0));//701 tin greaves
			animations.add(new AnimationDef("boots", "equipment", 0xc86a2b, 0, true, false, 0));//702 copper greaves
			animations.add(new AnimationDef("boots", "equipment", 0x8ea6bb, 0, true, false, 0));//703 titan steel greaves
			animations.add(new AnimationDef("boots", "equipment", 0x5a3f7d, 0, true, false, 0));//704 orichalcum greaves
			animations.add(new AnimationDef("sword", "equipment", 0xb7c9d9, 0, true, false, 0));//705 tin
			animations.add(new AnimationDef("sword", "equipment", 0xc86a2b, 0, true, false, 0));//706 copper
			animations.add(new AnimationDef("sword", "equipment", 0x8ea6bb, 0, true, false, 0));//707 titan steel
			animations.add(new AnimationDef("sword", "equipment", 0x5a3f7d, 0, true, false, 0));//708 orichalcum
			animations.add(new AnimationDef("battleaxe", "equipment", 0xb7c9d9, 0, true, false, 0));//709 tin
			animations.add(new AnimationDef("battleaxe", "equipment", 0xc86a2b, 0, true, false, 0));//710 copper
			animations.add(new AnimationDef("battleaxe", "equipment", 0x8ea6bb, 0, true, false, 0));//711 titan steel
			animations.add(new AnimationDef("battleaxe", "equipment", 0x5a3f7d, 0, true, false, 0));//712 orichalcum
			animations.add(new AnimationDef("mace", "equipment", 0xb7c9d9, 0, true, false, 0));//713 tin
			animations.add(new AnimationDef("mace", "equipment", 0xc86a2b, 0, true, false, 0));//714 copper
			animations.add(new AnimationDef("mace", "equipment", 0x8ea6bb, 0, true, false, 0));//715 titan steel
			animations.add(new AnimationDef("mace", "equipment", 0x5a3f7d, 0, true, false, 0));//716 orichalcum

			// MyWorld spears use appearance IDs 718-721; keep 722-723 reserved so wizard robes stay on 724+.
			animations.add(new AnimationDef("spear", "equipment", 0xb7c9d9, 0, true, false, 0));
			animations.add(new AnimationDef("spear", "equipment", 0xc86a2b, 0, true, false, 0));
			animations.add(new AnimationDef("spear", "equipment", 0x8ea6bb, 0, true, false, 0));
			animations.add(new AnimationDef("spear", "equipment", 0x5a3f7d, 0, true, false, 0));
			animations.add(new AnimationDef("nothing", "equipment", 0, 0, true, false, 0));
			animations.add(new AnimationDef("nothing", "equipment", 0, 0, true, false, 0));

			// MyWorld wizard robes
			animations.add(new AnimationDef("wizardshat", "equipment", 0xFFFFFF, 0, true, false, 0));//723 - Wool wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0xFFFFFF, 0, true, false, 0));//724 - Wool robe top
			animations.add(new AnimationDef("skirt", "equipment", 0xFFFFFF, 0, true, false, 0));//725 - Wool robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0xFF0000, 0, true, false, 0));//726 - Red wool wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0xFF0000, 0, true, false, 0));//727 - Red wool robe top
			animations.add(new AnimationDef("skirt", "equipment", 0xFF0000, 0, true, false, 0));//728 - Red wool robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0xFFFF00, 0, true, false, 0));//729 - Yellow wool wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0xFFFF00, 0, true, false, 0));//730 - Yellow wool robe top
			animations.add(new AnimationDef("skirt", "equipment", 0xFFFF00, 0, true, false, 0));//731 - Yellow wool robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0x0000FF, 0, true, false, 0));//732 - Blue wool wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0x0000FF, 0, true, false, 0));//733 - Blue wool robe top
			animations.add(new AnimationDef("skirt", "equipment", 0x0000FF, 0, true, false, 0));//734 - Blue wool robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0xFF8800, 0, true, false, 0));//735 - Orange wool wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0xFF8800, 0, true, false, 0));//736 - Orange wool robe top
			animations.add(new AnimationDef("skirt", "equipment", 0xFF8800, 0, true, false, 0));//737 - Orange wool robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0x00AA00, 0, true, false, 0));//738 - Green wool wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0x00AA00, 0, true, false, 0));//739 - Green wool robe top
			animations.add(new AnimationDef("skirt", "equipment", 0x00AA00, 0, true, false, 0));//740 - Green wool robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0xAA00AA, 0, true, false, 0));//741 - Purple wool wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0xAA00AA, 0, true, false, 0));//742 - Purple wool robe top
			animations.add(new AnimationDef("skirt", "equipment", 0xAA00AA, 0, true, false, 0));//743 - Purple wool robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0x87CEEB, 0, true, false, 0));//744 - Air wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0x87CEEB, 0, true, false, 0));//745 - Air robe top
			animations.add(new AnimationDef("skirt", "equipment", 0x87CEEB, 0, true, false, 0));//746 - Air robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0x8B4A3B, 0, true, false, 0));//747 - Mind wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0x8B4A3B, 0, true, false, 0));//748 - Mind robe top
			animations.add(new AnimationDef("skirt", "equipment", 0x8B4A3B, 0, true, false, 0));//749 - Mind robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0x1F4E8C, 0, true, false, 0));//750 - Water wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0x1F4E8C, 0, true, false, 0));//751 - Water robe top
			animations.add(new AnimationDef("skirt", "equipment", 0x1F4E8C, 0, true, false, 0));//752 - Water robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0x7A5230, 0, true, false, 0));//753 - Earth wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0x7A5230, 0, true, false, 0));//754 - Earth robe top
			animations.add(new AnimationDef("skirt", "equipment", 0x7A5230, 0, true, false, 0));//755 - Earth robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0xC62828, 0, true, false, 0));//756 - Fire wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0xC62828, 0, true, false, 0));//757 - Fire robe top
			animations.add(new AnimationDef("skirt", "equipment", 0xC62828, 0, true, false, 0));//758 - Fire robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0xC2A57A, 0, true, false, 0));//759 - Body wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0xC2A57A, 0, true, false, 0));//760 - Body robe top
			animations.add(new AnimationDef("skirt", "equipment", 0xC2A57A, 0, true, false, 0));//761 - Body robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0xE0C341, 0, true, false, 0));//762 - Cosmic wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0xE0C341, 0, true, false, 0));//763 - Cosmic robe top
			animations.add(new AnimationDef("skirt", "equipment", 0xE0C341, 0, true, false, 0));//764 - Cosmic robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0xF57C00, 0, true, false, 0));//765 - Chaos wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0xF57C00, 0, true, false, 0));//766 - Chaos robe top
			animations.add(new AnimationDef("skirt", "equipment", 0xF57C00, 0, true, false, 0));//767 - Chaos robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0x2E7D32, 0, true, false, 0));//768 - Nature wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0x2E7D32, 0, true, false, 0));//769 - Nature robe top
			animations.add(new AnimationDef("skirt", "equipment", 0x2E7D32, 0, true, false, 0));//770 - Nature robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0x1B8A8F, 0, true, false, 0));//771 - Law wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0x1B8A8F, 0, true, false, 0));//772 - Law robe top
			animations.add(new AnimationDef("skirt", "equipment", 0x1B8A8F, 0, true, false, 0));//773 - Law robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0x222222, 0, true, false, 0));//774 - Death wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0x222222, 0, true, false, 0));//775 - Death robe top
			animations.add(new AnimationDef("skirt", "equipment", 0x222222, 0, true, false, 0));//776 - Death robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0x8FA8C9, 0, true, false, 0));//777 - Soul wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0x8FA8C9, 0, true, false, 0));//778 - Soul robe top
			animations.add(new AnimationDef("skirt", "equipment", 0x8FA8C9, 0, true, false, 0));//779 - Soul robe skirt
			animations.add(new AnimationDef("wizardshat", "equipment", 0x7F1D1D, 0, true, false, 0));//780 - Blood wizard hat
			animations.add(new AnimationDef("wizardsrobe", "equipment", 0x7F1D1D, 0, true, false, 0));//781 - Blood robe top
			animations.add(new AnimationDef("skirt", "equipment", 0x7F1D1D, 0, true, false, 0));//782 - Blood robe skirt
			animations.add(new AnimationDef("fishingpole", "equipment", 0, 0, false, false, 0));//783 - fishing pole
			animations.add(new AnimationDef("longbow", "equipment", 0xCCAA33, 0, false, false, 0));//784 - pine longbow
			animations.add(new AnimationDef("longbow", "equipment", 0xC78C4A, 0, false, false, 0));//785 - palm longbow
			animations.add(new AnimationDef("longbow", "equipment", 0x333333, 0, false, false, 0));//786 - ebony longbow
			animations.add(new AnimationDef("longbow", "equipment", 0xCC2222, 0, false, false, 0));//787 - blood longbow
			for (int woodColor : MYWORLD_STAFF_WOOD_COLORS) {
				animations.add(new AnimationDef("staff", "equipment", woodColor, 0, true, false, 0));
			}
			for (int runeColor : MYWORLD_STAFF_RUNE_COLORS) {
				for (int woodColor : MYWORLD_STAFF_WOOD_COLORS) {
					animations.add(new AnimationDef("elementalstaff", "equipment", runeColor, woodColor, 0, true, false, 0));
				}
			}
			for (int runeColor : MYWORLD_RUNE_CLOTH_COLORS) {
				animations.add(new AnimationDef("leathergloves", "equipment", runeColor, 0, true, false, 0));
			}
			for (int runeColor : MYWORLD_RUNE_CLOTH_COLORS) {
				animations.add(new AnimationDef("boots", "equipment", runeColor, 0, true, false, 0));
			}
			animations.add(new AnimationDef("leathergloves", "equipment", 0xFFFFFF, 0, true, false, 0));
			animations.add(new AnimationDef("boots", "equipment", 0xFFFFFF, 0, true, false, 0));
			for (int pickaxeColor : MYWORLD_PICKAXE_COLORS) {
				animations.add(new AnimationDef("pickaxe", "equipment", pickaxeColor, 0, true, false, 0));
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void loadSpellDefinitions() {
		LinkedHashMap<Integer, Integer> runes = new LinkedHashMap<Integer, Integer>();
		runes.put(33, 1);
		runes.put(35, 1);
		spells.add(new SpellDef("Wind Arrow", "Applies Weaker Unsteady for 5 attacks",
			1, 2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(32, 1);
		runes.put(35, 1);
		spells.add(new SpellDef("Water Ball", "Applies Weaker Dampen for 5 attacks",
			1, 2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(34, 1);
		runes.put(35, 1);
		spells.add(new SpellDef("Rock Throw", "Applies Weaker Slow for 5 attacks",
			1, 2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(31, 1);
		runes.put(35, 1);
		spells.add(new SpellDef("Fireball", "Applies Weaker Scorch for 5 attacks",
			1, 2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(31, 1);
		runes.put(32, 1);
		runes.put(35, 1);
		spells.add(new SpellDef("Thunder Ball", "Dual-element thunder damage",
			10, 2, 3, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(33, 1);
		runes.put(32, 1);
		runes.put(35, 1);
		spells.add(new SpellDef("Icicle Shot", "Dual-element ice damage",
			10, 2, 3, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(31, 1);
		runes.put(34, 1);
		runes.put(35, 1);
		spells.add(new SpellDef("Acid Drop", "Dual-element acid damage",
			10, 2, 3, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(34, 1);
		runes.put(32, 1);
		runes.put(35, 1);
		spells.add(new SpellDef("Spore", "Dual-element wood damage",
			10, 2, 3, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(34, 1);
		runes.put(33, 3);
		runes.put(42, 1);
		spells.add(new SpellDef("Lumbridge teleport",
			"Teleports you to Lumbridge", 3, 0, 3,
			(LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(35, 1);
		runes.put(36, 1);
		spells.add(new SpellDef("Lesser Heal", "Restores a small amount of health",
			5, 0, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(31, 1);
		runes.put(33, 3);
		runes.put(42, 1);
		spells.add(new SpellDef("Varrock teleport", "Teleports you to Varrock",
			6, 0, 3, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(32, 1);
		runes.put(33, 3);
		runes.put(42, 1);
		spells.add(new SpellDef("Falador teleport", "Teleports you to Falador",
			9, 0, 3, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(33, 5);
		runes.put(42, 1);
		spells.add(new SpellDef("Camelot teleport", "Teleports you to Camelot",
			12, 0, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(32, 2);
		runes.put(42, 2);
		spells.add(new SpellDef("Ardougne teleport",
			"Teleports you to Ardougne", 15, 0, 2,
			(LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(34, 2);
		runes.put(42, 2);
		spells.add(new SpellDef("Watchtower teleport",
			"Teleports you to the watchtower", 18, 0, 2,
			(LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(33, 2);
		runes.put(41, 1);
		spells.add(new SpellDef("Wind Slash", "Applies Weak Unsteady for 5 attacks", 20,
			2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(32, 2);
		runes.put(41, 1);
		spells.add(new SpellDef("Water Burst", "Applies Weak Dampen for 5 attacks", 20,
			2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(34, 2);
		runes.put(41, 1);
		spells.add(new SpellDef("Earth Hammer", "Applies Weak Slow for 5 attacks",
			20, 2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(31, 2);
		runes.put(41, 1);
		spells.add(new SpellDef("Fire Claw", "Applies Weak Scorch for 5 attacks", 20,
			2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(31, 2);
		runes.put(32, 2);
		runes.put(41, 1);
		spells.add(new SpellDef("Thunder Splash", "Dual-element thunder damage",
			30, 2, 3, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(33, 2);
		runes.put(32, 2);
		runes.put(41, 1);
		spells.add(new SpellDef("Ice Burst", "Dual-element ice damage",
			30, 2, 3, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(31, 2);
		runes.put(34, 2);
		runes.put(41, 1);
		spells.add(new SpellDef("Acid Frog", "Dual-element acid damage",
			30, 2, 3, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(34, 2);
		runes.put(32, 2);
		runes.put(41, 1);
		spells.add(new SpellDef("Wood Drill", "Dual-element wood damage",
			30, 2, 3, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(31, 5);
		runes.put(40, 1);
		spells.add(new SpellDef("Alchemy",
			"Converts an item into gold", 20, 3, 2,
			(LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(33, 1);
		runes.put(42, 1);
		spells.add(new SpellDef("Telekinetic grab",
			"Take an item you can see but can't reach", 21, 3, 2,
			(LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(36, 2);
		runes.put(40, 1);
		spells.add(new SpellDef("Retired spell", "No longer available",
			25, 0, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(33, 3);
		runes.put(38, 1);
		spells.add(new SpellDef("Tornado", "Applies Strong Unsteady for 5 attacks",
			40, 2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(32, 3);
		runes.put(38, 1);
		spells.add(new SpellDef("Water Eruption", "Applies Strong Dampen for 5 attacks",
			40, 2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(34, 3);
		runes.put(38, 1);
		spells.add(new SpellDef("Earth Burst", "Applies Strong Slow for 5 attacks",
			40, 2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(31, 3);
		runes.put(38, 1);
		spells.add(new SpellDef("Explosion", "Applies Strong Scorch for 5 attacks",
			40, 2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(31, 3);
		runes.put(32, 3);
		runes.put(38, 1);
		spells.add(new SpellDef("Thunder Strike", "Dual-element thunder damage",
			50, 2, 3, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(33, 3);
		runes.put(32, 3);
		runes.put(38, 1);
		spells.add(new SpellDef("Ice Crystal", "Dual-element ice damage",
			50, 2, 3, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(31, 3);
		runes.put(34, 3);
		runes.put(38, 1);
		spells.add(new SpellDef("Acid Gush", "Dual-element acid damage",
			50, 2, 3, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(34, 3);
		runes.put(32, 3);
		runes.put(38, 1);
		spells.add(new SpellDef("Battering Ram", "Dual-element wood damage",
			50, 2, 3, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(31, 5);
		runes.put(38, 1);
		spells.add(new SpellDef("Iban blast", "A strength 25 missile attack!",
			50, 2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(36, 2);
		runes.put(40, 1);
		runes.put(825, 1);
		spells.add(new SpellDef("Greater Heal", "Restores a strong amount of health",
			50, 0, 3, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(33, 4);
		runes.put(619, 1);
		spells.add(new SpellDef("Wind Beam", "Applies Stronger Unsteady for 5 attacks",
			60, 2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(32, 4);
		runes.put(619, 1);
		spells.add(new SpellDef("Water Vortex", "Applies Stronger Dampen for 5 attacks",
			60, 2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(34, 4);
		runes.put(619, 1);
		spells.add(new SpellDef("Earth Impale", "Applies Stronger Slow for 5 attacks",
			60, 2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(31, 4);
		runes.put(619, 1);
		spells.add(new SpellDef("Fire Pillar", "Applies Stronger Scorch for 5 attacks",
			60, 2, 2, (LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(35, 3);
		runes.put(619, 1);
		runes.put(40, 2);
		spells.add(new SpellDef("Eye of Guthix",
			"AoE damage and applies poison", 70, 2, 3,
			(LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(35, 3);
		runes.put(619, 1);
		runes.put(825, 2);
		spells.add(new SpellDef("Saradomin strike",
			"AoE damage and steals health", 70, 2, 3,
			(LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(35, 3);
		runes.put(619, 1);
		runes.put(41, 2);
		spells.add(new SpellDef("Void of Zamorak",
			"AoE damage and applies withering", 70, 2, 3,
			(LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(35, 6);
		runes.put(619, 2);
		runes.put(41, 4);
		spells.add(new SpellDef("Zamorak's Apocolypse",
			"Greater AoE damage and applies stronger Withering", 80, 2, 3,
			(LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(35, 6);
		runes.put(619, 2);
		runes.put(825, 4);
		spells.add(new SpellDef("Saradomin Soul Slash",
			"Greater AoE damage and stronger lifesteal", 80, 2, 3,
			(LinkedHashMap<Integer, Integer>) runes.clone()));
		runes.clear();
		runes.put(35, 6);
		runes.put(619, 2);
		runes.put(40, 4);
		spells.add(new SpellDef("Claw of Guthix",
			"Greater AoE damage and stronger poison", 80, 2, 3,
			(LinkedHashMap<Integer, Integer>) runes.clone()));
		runes = null;
	}

	private static void loadDoorDefinitions() {
		int i = 0;
		doors.add(new DoorDef("Wall", "", "WalkTo", "Examine", 1, 0, 192, 2, 2,
			i++));
		doors.add(new DoorDef("Doorframe", "", "WalkTo", "Close", 0, 1, 192, 4,
			4, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Window", "", "WalkTo", "Examine", 1, 0, 192, 5,
			5, i++));
		doors.add(new DoorDef("Fence", "", "WalkTo", "Examine", 1, 0, 192, 10,
			10, i++));
		doors.add(new DoorDef("railings", "", "WalkTo", "Examine", 1, 0, 192,
			12, 12, i++));
		doors.add(new DoorDef("Stained glass window", "", "WalkTo", "Examine",
			1, 0, 192, 18, 18, i++));
		doors.add(new DoorDef("Highwall", "", "WalkTo", "Examine", 1, 0, 275,
			2, 2, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 275, 0, 0, i++));
		doors.add(new DoorDef("Doorframe", "", "WalkTo", "Close", 0, 1, 275, 4,
			4, i++));
		doors.add(new DoorDef("battlement", "", "WalkTo", "Examine", 1, 0, 70,
			2, 2, i++));
		doors.add(new DoorDef("Doorframe", "", "WalkTo", "Examine", 1, 0, 192,
			4, 4, i++));
		doors.add(new DoorDef("snowwall", "", "WalkTo", "Examine", 1, 0, 192,
			-31711, -31711, i++));
		doors.add(new DoorDef("arrowslit", "", "WalkTo", "Examine", 1, 0, 192,
			7, 7, i++));
		doors.add(new DoorDef("timberwall", "", "WalkTo", "Examine", 1, 0, 192,
			21, 21, i++));
		doors.add(new DoorDef("timberwindow", "", "WalkTo", "Examine", 1, 0,
			192, 22, 22, i++));
		doors.add(new DoorDef("blank", "", "WalkTo", "Examine", 0, 0, 192,
			12345678, 12345678, i++));
		doors.add(new DoorDef("highblank", "", "WalkTo", "Examine", 0, 0, 275,
			12345678, 12345678, i++));
		doors.add(new DoorDef("mossybricks", "", "WalkTo", "Examine", 1, 0,
			192, 23, 23, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Odd looking wall",
			"This wall doesn't look quite right", "Push", "Examine", 1, 1,
			192, 2, 2, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("web", "A spider's web", Config.S_WANT_LEFTCLICK_WEBS ? "Slice" : "WalkTo", Config.S_WANT_LEFTCLICK_WEBS ? "WalkTo" : "Examine", 1,
			1, 192, 26, 26, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Window", "", "WalkTo", "Examine", 1, 0, 192, 27,
			27, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Crumbled", "", "WalkTo", "Examine", 1, 0, 192,
			28, 28, i++));
		doors.add(new DoorDef("Cavern", "", "WalkTo", "Examine", 1, 0, 192, 29,
			29, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("cavern2", "", "WalkTo", "Examine", 1, 0, 192,
			30, 30, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Wall", "", "WalkTo", "Examine", 1, 0, 192, 3, 3,
			i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Strange looking wall",
			"This wall doesn't look quite right", "Push", "Examine", 1, 1,
			192, 29, 29, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("memberrailings", "", "WalkTo", "Examine", 1, 0,
			192, 12, 12, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Magic Door", "The door is shut", "Open",
			"Examine", 1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Strange Panel",
			"This wall doesn't look quite right", "Push", "Examine", 1, 1,
			192, 21, 21, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("blockblank", "", "WalkTo", "Examine", 1, 0, 192,
			12345678, 12345678, i++));
		doors.add(new DoorDef("unusual looking wall",
			"This wall doesn't look quite right", "Push", "Examine", 1, 1,
			192, 2, 2, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Pick lock",
			1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Pick lock",
			1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Pick lock",
			1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Pick lock",
			1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Pick lock",
			1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Pick Lock",
			1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Pick lock",
			1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Fence with loose pannels",
			"I wonder if I could get through this", "push", "Examine", 1,
			1, 192, 10, 10, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("rat cage", "The rat's have damaged the bars",
			"search", "Examine", 1, 1, 192, 12, 12, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("arrowslit", "", "WalkTo", "Examine", 1, 0, 192,
			44, 44, i++));
		doors.add(new DoorDef("solidblank", "", "WalkTo", "Examine", 1, 0, 192,
			12345678, 12345678, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("loose panel", "The panel has worn with age",
			"break", "Examine", 1, 1, 192, 3, 3, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("plankswindow", "", "WalkTo", "Examine", 1, 0,
			192, 45, 45, i++));
		doors.add(new DoorDef("Low Fence", "", "WalkTo", "Examine", 1, 0, 96,
			10, 10, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Cooking pot", "Smells good!", "WalkTo",
			"Examine", 1, 1, 96, 10, 10, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("plankstimber", "", "WalkTo", "Examine", 1, 0,
			192, 46, 46, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("magic portal", "", "enter", "Examine", 1, 1,
			192, 17, 17, i++));
		doors.add(new DoorDef("magic portal", "", "enter", "Examine", 1, 1,
			192, 17, 17, i++));
		doors.add(new DoorDef("magic portal", "", "enter", "Examine", 1, 1,
			192, 17, 17, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Cavern wall",
			"It looks as if it is covered in some fungus.", "WalkTo",
			"search", 1, 1, 192, 29, 29, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "the door is shut", "walk through",
			"Examine", 1, 1, 192, 3, 3, i++));
		doors.add(new DoorDef("Door", "The door is shut", "walk through",
			"Examine", 1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "walk through",
			"Examine", 1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "walk through",
			"Examine", 1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "walk through",
			"Examine", 1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "walk through",
			"Examine", 1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "walk through",
			"Examine", 1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "walk through",
			"Examine", 1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Pick Lock",
			1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Low wall", "a low wall", "jump", "Examine", 1,
			1, 70, 2, 2, i++));
		doors.add(new DoorDef("Low wall", "a low wall", "jump", "Examine", 1,
			1, 70, 2, 2, i++));
		doors.add(new DoorDef("Blacksmiths Door", "The door is shut", "Open",
			"Examine", 1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("railings", "", "pick lock", "Examine", 1, 1,
			192, 12, 12, i++));
		doors.add(new DoorDef("railings", "", "pick lock", "Examine", 1, 1,
			192, 12, 12, i++));
		doors.add(new DoorDef("railings", "", "pick lock", "search", 1, 1, 192,
			12, 12, i++));
		doors.add(new DoorDef("railings", "", "pick lock", "search", 1, 1, 192,
			12, 12, i++));
		doors.add(new DoorDef("railings", "", "pick lock", "search", 1, 1, 192,
			12, 12, i++));
		doors.add(new DoorDef("railings", "", "WalkTo", "search", 1, 1, 192,
			12, 12, i++));
		doors.add(new DoorDef("railings", "", "WalkTo", "look through", 1, 1,
			192, 12, 12, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "knock on",
			1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Doorframe", "", "WalkTo", "Close", 1, 1, 192, 4,
			4, i++));
		doors.add(new DoorDef("Tent", "", "WalkTo", "Examine", 1, 0, 192, 36,
			36, i++));
		doors.add(new DoorDef("Jail Door", "The door is shut", "Open",
			"Examine", 1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Jail Door", "The door is shut", "Open",
			"Examine", 1, 1, 192, 0, 0, i++));
		doors.add(new DoorDef("Window", "A barred window", "WalkTo", "Search",
			1, 1, 192, 27, 27, i++));
		doors.add(new DoorDef("magic portal",
			"A magical barrier shimmers with power", "WalkTo", "Examine",
			1, 1, 192, 17, 17, i++));
		doors.add(new DoorDef("Jail Door", "A solid iron gate", "Open",
			"Examine", 1, 1, 192, 12, 12, i++));
		doors.add(new DoorDef("railings", "", "WalkTo", "search", 1, 1, 192,
			12, 12, i++));
		doors.add(new DoorDef("railings", "", "WalkTo", "search", 1, 1, 192,
			12, 12, i++));
		doors.add(new DoorDef("railings", "", "WalkTo", "search", 1, 1, 192,
			12, 12, i++));
		doors.add(new DoorDef("railings", "", "WalkTo", "search", 1, 1, 192,
			12, 12, i++));
		doors.add(new DoorDef("railings", "", "WalkTo", "search", 1, 1, 192,
			12, 12, i++));
		doors.add(new DoorDef("railings", "", "WalkTo", "search", 1, 1, 192,
			12, 12, i++));
		doors.add(new DoorDef("Cave exit", "The way out", "Leave", "Examine",
			0, 1, 192, 26, 26, i++));
		doors.add(new DoorDef("Cave exit", "The way out", "Leave", "Examine",
			0, 1, 192, 26, 26, i++));
		doors.add(new DoorDef("Cave exit", "The way out", "Leave", "Examine",
			0, 1, 192, 26, 26, i++));
		doors.add(new DoorDef("Cave exit", "The way out", "Leave", "Examine",
			0, 1, 192, 26, 26, i++));
		doors.add(new DoorDef("Cave exit", "The way out", "Leave", "Examine",
			0, 1, 192, 26, 26, i++));
		doors.add(new DoorDef("Cave exit", "The way out", "Leave", "Examine",
			0, 1, 192, 26, 26, i++));
		doors.add(new DoorDef("railings", "", "WalkTo", "search", 1, 1, 192,
			12, 12, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("battlement", "This is blocking your path",
			"Climb-over", "Examine", 1, 1, 70, 2, 2, i++));
		doors.add(new DoorDef("Tent Door", "An entrance into the tent",
			"Go through", "Examine", 1, 1, 192, 50, 50, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
		doors.add(new DoorDef("Tent Door", "An entrance into the tent",
			"Go through", "Examine", 1, 1, 192, 50, 50, i++));
		doors.add(new DoorDef("Low Fence", "A damaged wooden fence", "search",
			"Examine", 1, 1, 96, 10, 10, i++));
		doors.add(new DoorDef("Sturdy Iron Gate", "A solid iron gate", "Open",
			"Examine", 1, 1, 192, 12, 12, i++));
		doors.add(new DoorDef("battlement", "this low wall blocks your path",
			"climb over", "Examine", 1, 1, 70, 2, 2, i++));
		doors.add(new DoorDef("Water", "My waterfall boundary!", "WalkTo",
			"Examine", 1, 0, 192, 25, 25, i++));
		doors.add(new DoorDef("Wheat", "Test Boundary!", "WalkTo", "Examine",
			1, 0, 192, 24, 24, i++));
		doors.add(new DoorDef("Jungle", "Thick inpenetrable jungle", "Chop",
			"Examine", 1, 1, 192, 8, 8, i++));
		doors.add(new DoorDef("Window",
			"you can see a vicious looking guard dog right outside",
			"Investigate", "Examine", 1, 1, 192, 5, 5, i++));
		doors.add(new DoorDef("Rut",
			"Looks like a small rut carved into the ground.", "WalkTo",
			"Search", 1, 0, 96, 51, 51, i++));
		doors.add(new DoorDef("Crumbled Cavern 1", "", "WalkTo", "Examine", 0,
			0, 192, 52, 52, i++));
		doors.add(new DoorDef("Crumbled Cavern 2", "", "WalkTo", "Examine", 0,
			0, 192, 53, 53, i++));
		doors.add(new DoorDef("cavernhole", "", "WalkTo", "Examine", 1, 0, 192,
			54, 54, i++));
		doors.add(new DoorDef("flamewall",
			"A supernatural fire of incredible intensity", "Touch",
			"Investigate", 1, 1, 192, 54, 54, i++));
		doors.add(new DoorDef("Ruined wall",
			"Some ancient wall structure - it doesn't look too high.",
			"WalkTo", "Jump", 1, 1, 192, 28, 28, i++));
		doors.add(new DoorDef(
			"Ancient Wall",
			"An ancient - slightly higher wall with some strange markings on it",
			"Use", "Search", 1, 1, 275, 2, 2, i++));
		doors.add(new DoorDef("Door", "The door is shut", "Open", "Examine", 1,
			1, 192, 0, 0, i++));
	}

	private static void loadGameObjectDefinitionsA() { // GOOD
		int i = 0;
		objects.add(new GameObjectDef("Pine Tree", "A tall pine tree", "Chop", "Examine", 1, 1, 1, 0, "tree2", i++));
		objects.add(new GameObjectDef("Tree", "A leafy tree", "Chop", "Examine", 1, 1, 1, 0, "tree", i++));
		objects.add(new GameObjectDef("Well", "The bucket is missing", "WalkTo", "Examine", 1, 2, 2, 0, "well", i++));
		objects.add(new GameObjectDef("Table", "A mighty fine table", "WalkTo", "Examine", 1, 1, 1, 96, "table", i++));
		objects.add(new GameObjectDef("Treestump", "Someone has chopped this tree down!", "WalkTo", "Examine", 1, 1, 1, 0, "treestump", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading upwards", "Climb-Up", "Examine", 1, 1, 1, 0, "ladder", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading downwards", "Climb-Down", "Examine", 1, 1, 1, 0, "ladderdown", i++));
		objects.add(new GameObjectDef("Chair", "A sturdy looking chair", "WalkTo", "Examine", 1, 1, 1, 0, "chair", i++));
		objects.add(new GameObjectDef("logs", "A pile of logs", "WalkTo", "Examine", 1, 1, 1, 0, "logpile", i++));
		objects.add(new GameObjectDef("Longtable", "It has nice candles", "WalkTo", "Examine", 1, 4, 1, 0, "longtable", i++));
		objects.add(new GameObjectDef("Throne", "It looks fancy and expensive", "WalkTo", "Examine", 1, 1, 1, 0, "throne", i++));//10
		objects.add(new GameObjectDef("Range", "A hot well stoked range", "WalkTo", "Examine", 1, 1, 2, 0, "range", i++));
		objects.add(new GameObjectDef("Gravestone", "R I P", "WalkTo", "Examine", 1, 1, 1, 0, "gravestone1", i++));
		objects.add(new GameObjectDef("Gravestone", "Its covered in moss", "WalkTo", "Examine", 1, 1, 1, 0, "gravestone2", i++));
		objects.add(new GameObjectDef("Bed", "Ooh nice blankets", "rest", "Examine", 1, 2, 3, 0, "Bigbed", i++));
		objects.add(new GameObjectDef("Bed", "Its a bed - wow", "rest", "Examine", 1, 2, 2, 0, "bed", i++));
		objects.add(new GameObjectDef("bar", "Mmm beer", "WalkTo", "Examine", 1, 1, 1, 0, "barpumps", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Altar of Saradomin", "An altar dedicated to Saradomin", "Worship", "Examine", 1, 2, 1, 0, "altar", i++));
		objects.add(new GameObjectDef("Post", "What am I examining posts for", "WalkTo", "Examine", 1, 1, 1, 0, "wallpost", i++));//20
		objects.add(new GameObjectDef("Support", "A wooden pole", "WalkTo", "Examine", 0, 1, 1, 0, "supportnw", i++));
		objects.add(new GameObjectDef("barrel", "Its empty", "WalkTo", "Examine", 1, 1, 1, 0, "barrel", i++));
		objects.add(new GameObjectDef("Bench", "It doesn't look very comfy", "WalkTo", "Examine", 1, 2, 1, 0, "bench", i++));
		objects.add(new GameObjectDef("Portrait", "A painting of our beloved king", "WalkTo", "Examine", 0, 1, 1, 0, "portrait", i++));
		objects.add(new GameObjectDef("candles", "Candles on a fancy candlestick", "WalkTo", "Examine", 1, 1, 1, 0, "candles", i++));
		objects.add(new GameObjectDef("fountain", "The water looks fairly clean", "WalkTo", "Examine", 1, 2, 2, 0, "fountain", i++));
		objects.add(new GameObjectDef("landscape", "An oil painting", "WalkTo", "Examine", 0, 1, 1, 0, "landscape", i++));
		objects.add(new GameObjectDef("Millstones", "You can use these to make flour", "WalkTo", "Examine", 1, 3, 3, 0, "mill", i++));
		objects.add(new GameObjectDef("Counter", "It's the shop counter", "WalkTo", "Examine", 1, 2, 1, 120, "counter", i++));
		objects.add(new GameObjectDef("Stall", "A market stall", "WalkTo", "Examine", 1, 2, 2, 112, "market", i++));//30
		objects.add(new GameObjectDef("Target", "Coming soon archery practice", "Practice", "Examine", 1, 1, 1, 0, "target", i++));
		objects.add(new GameObjectDef("PalmTree", "A nice palm tree", "Chop", "Examine", 1, 1, 1, 0, "palm2", i++));
		objects.add(new GameObjectDef("PalmTree", "A shady palm tree", "Chop", "Examine", 1, 1, 1, 0, "palm", i++));
		objects.add(new GameObjectDef("Fern", "A leafy plant", "WalkTo", "Examine", 0, 1, 1, 0, "fern", i++));//34
		objects.add(new GameObjectDef("Cactus", "It looks very spikey", "WalkTo", "Examine", 1, 1, 1, 0, "cactus", i++));
		objects.add(new GameObjectDef("Bullrushes", "I wonder why it's called a bullrush", "WalkTo", "Examine", 0, 1, 1, 0, "bullrushes", i++));
		objects.add(new GameObjectDef("Flower", "Ooh thats pretty", "WalkTo", "Examine", 0, 1, 1, 0, "flower", i++));
		objects.add(new GameObjectDef("Mushroom", "I think it's a poisonous one", "WalkTo", "Examine", 0, 1, 1, 0, "mushroom", i++));
		objects.add(new GameObjectDef("Coffin", "This coffin is closed", "Open", "Examine", 1, 2, 2, 0, "coffin", i++));
		objects.add(new GameObjectDef("Coffin", "This coffin is open", "Search", "Close", 1, 2, 2, 0, "coffin2", i++));//40
		objects.add(new GameObjectDef("stairs", "These lead upstairs", "Go up", "Examine", 1, 2, 3, 0, "woodenstairs", i++));
		objects.add(new GameObjectDef("stairs", "These lead downstairs", "Go down", "Examine", 1, 2, 3, 0, "woodenstairsdown", i++));
		objects.add(new GameObjectDef("stairs", "These lead upstairs", "Go up", "Examine", 1, 2, 3, 0, "stonestairs", i++));
		objects.add(new GameObjectDef("stairs", "These lead downstairs", "Go down", "Examine", 1, 2, 3, 0, "stonestairsdown", i++));
		objects.add(new GameObjectDef("railing", "nice safety measure", "WalkTo", "Examine", 1, 1, 1, 0, "woodenrailing", i++));
		objects.add(new GameObjectDef("pillar", "An ornate pillar", "WalkTo", "Examine", 1, 1, 1, 0, "marblepillar", i++));
		objects.add(new GameObjectDef("Bookcase", "A large collection of books", "WalkTo", "Examine", 1, 1, 2, 0, "bookcase", i++));
		objects.add(new GameObjectDef("Sink", "Its fairly dirty", "WalkTo", "Examine", 1, 1, 2, 0, "sink", i++));
		objects.add(new GameObjectDef("Dummy", "I can practice my fighting here", "hit", "Examine", 1, 1, 1, 0, "sworddummy", i++));
		objects.add(new GameObjectDef("anvil", "heavy metal", "Smith", "Examine", 1, 1, 1, 0, "anvil", i++));//50
		objects.add(new GameObjectDef("Torch", "It would be very dark without this", "WalkTo", "Examine", 0, 1, 1, 0, "torcha1", i++));
		objects.add(new GameObjectDef("hopper", "You put grain in here", "operate", "Examine", 1, 2, 2, 0, "milltop", i++));
		objects.add(new GameObjectDef("chute", "Flour comes out here", "WalkTo", "Examine", 1, 2, 2, 40, "millbase", i++));
		objects.add(new GameObjectDef("cart", "A farm cart", "WalkTo", "Examine", 1, 2, 3, 0, "cart", i++));
		objects.add(new GameObjectDef("sacks", "Yep they're sacks", "WalkTo", "Examine", 1, 1, 1, 0, "sacks", i++));
		objects.add(new GameObjectDef("cupboard", "The cupboard is shut", "open", "Examine", 1, 1, 2, 0, "cupboard", i++));
		objects.add(new GameObjectDef("Gate", "The gate is closed", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("gate", "The gate is open", "WalkTo", "close", 3, 1, 2, 0, "metalgateopen", i++));
		objects.add(new GameObjectDef("gate", "The gate is open", "WalkTo", "close", 3, 1, 2, 0, "woodengateopen", i++));
		objects.add(new GameObjectDef("gate", "The gate is closed", "open", "Examine", 2, 1, 2, 0, "woodengateclosed", i++));//60
		objects.add(new GameObjectDef("signpost", "To Varrock", "WalkTo", "Examine", 1, 1, 1, 0, "signpost", i++));
		objects.add(new GameObjectDef("signpost", "To the tower of wizards", "WalkTo", "Examine", 1, 1, 1, 0, "signpost", i++));
		objects.add(new GameObjectDef("doors", "The doors are open", "WalkTo", "Close", 3, 1, 2, 0, "doubledoorsopen", i++));
		objects.add(new GameObjectDef("doors", "The doors are shut", "Open", "Examine", 2, 1, 2, 0, "doubledoorsclosed", i++));
		objects.add(new GameObjectDef("signpost", "To player owned houses", "WalkTo", "Examine", 1, 1, 1, 0, "signpost", i++));
		objects.add(new GameObjectDef("signpost", "To Lumbridge Castle", "WalkTo", "Examine", 1, 1, 1, 0, "signpost", i++));
		objects.add(new GameObjectDef("bookcase", "It's a bookcase", "WalkTo", "Search", 1, 1, 2, 0, "bookcase", i++));
		objects.add(new GameObjectDef("henge", "these look impressive", "WalkTo", "Examine", 1, 2, 2, 0, "henge", i++));
		objects.add(new GameObjectDef("Dolmen", "A sort of ancient altar thingy", "WalkTo", "Examine", 1, 2, 2, 0, "dolmen", i++));
		objects.add(new GameObjectDef("Tree", "This tree doesn't look too healthy", "WalkTo", "Chop", 1, 1, 1, 0, "deadtree1", i++));//70
		objects.add(new GameObjectDef("cupboard", "Perhaps I should search it", "Search", "close", 1, 1, 2, 0, "cupboardopen", i++));
		objects.add(new GameObjectDef("Wheat", "nice ripe looking wheat", "WalkTo", "pick", 0, 1, 1, 0, "wheat", i++));
		objects.add(new GameObjectDef("sign", "The blue moon inn", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("sails", "The windmill's sails", "WalkTo", "Examine", 0, 1, 3, 0, "windmillsail", i++));
		objects.add(new GameObjectDef("sign", "estate agent", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("sign", "The Jolly boar inn", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("Drain", "This drainpipe runs from the kitchen to the sewers", "WalkTo", "Search", 0, 1, 1, 0, "pipe&drain", i++));
		objects.add(new GameObjectDef("manhole", "A manhole cover", "open", "Examine", 0, 1, 1, 0, "manholeclosed", i++));
		objects.add(new GameObjectDef("manhole", "How dangerous - this manhole has been left open", "climb down", "close", 0, 1, 1, 0, "manholeopen", i++));
		objects.add(new GameObjectDef("pipe", "a dirty sewer pipe", "WalkTo", "Examine", 1, 1, 1, 0, "wallpipe", i++));//80
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("barrel", "It seems to be full of newt's eyes", "WalkTo", "Examine", 1, 1, 1, 0, "barrel", i++));
		objects.add(new GameObjectDef("cupboard", "The cupboard is shut", "open", "Examine", 1, 1, 2, 0, "cupboard", i++));
		objects.add(new GameObjectDef("cupboard", "Perhaps I should search it", "Search", "close", 1, 1, 2, 0, "cupboardopen", i++));
		objects.add(new GameObjectDef("fountain", "I think I see something in the fountain", "WalkTo", "Search", 1, 2, 2, 0, "fountain", i++));
		objects.add(new GameObjectDef("signpost", "To Draynor Manor", "WalkTo", "Examine", 1, 1, 1, 0, "signpost", i++));
		objects.add(new GameObjectDef("Tree", "This tree doesn't look too healthy", "Approach", "Search", 1, 1, 1, 0, "deadtree1", i++));
		objects.add(new GameObjectDef("sign", "General Store", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("sign", "Lowe's Archery store", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));//90
		objects.add(new GameObjectDef("sign", "The Clothes Shop", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("sign", "Varrock Swords", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("gate", "You can pass through this on the members server", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("gate", "You can pass through this on the members server", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("sign", "Bob's axes", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("sign", "The staff shop", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("fire", "A strongly burning fire", "WalkTo", "Examine", 0, 1, 1, 0, "firea1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks2", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "copperrock1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "copperrock1", i++));//100
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "ironrock1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "ironrock1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "tinrock1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "tinrock1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "mithrilrock1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "mithrilrock1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "adamiterock1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "adamiterock1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "coalrock1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "coalrock1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "goldrock1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "goldrock1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "clayrock1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "clayrock1", i++));
		objects.add(new GameObjectDef("web", "A spider's web", "WalkTo", "Examine", 0, 1, 1, 0, "ceilingweb", i++));
		objects.add(new GameObjectDef("web", "A spider's web", "WalkTo", "Examine", 0, 1, 1, 0, "floorweb", i++));
		objects.add(new GameObjectDef("furnace", "A red hot furnace", "Smelt", "Examine", 1, 2, 2, 0, "furnace", i++));
		objects.add(new GameObjectDef("Cook's Range", "A hot well stoked range", "WalkTo", "Examine", 1, 1, 2, 0, "range", i++));
		objects.add(new GameObjectDef("Machine", "I wonder what it's supposed to do", "WalkTo", "Examine", 1, 2, 2, 0, "madmachine", i++));
		objects.add(new GameObjectDef("Spinning wheel", "I can spin wool on this", "WalkTo", "Examine", 1, 1, 1, 0, "spinningwheel", i++));
		objects.add(new GameObjectDef("Lever", "The lever is up", "WalkTo", "Examine", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("Lever", "The lever is down", "WalkTo", "Examine", 0, 1, 1, 0, "leverdown", i++));
		objects.add(new GameObjectDef("LeverA", "It's a lever", "Pull", "Inspect", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("LeverB", "It's a lever", "Pull", "Inspect", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("LeverC", "It's a lever", "Pull", "Inspect", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("LeverD", "It's a lever", "Pull", "Inspect", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("LeverE", "It's a lever", "Pull", "Inspect", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("LeverF", "It's a lever", "Pull", "Inspect", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading upwards", "Climb-Up", "Examine", 1, 1, 1, 0, "ladder", i++));
		objects.add(new GameObjectDef("signpost", "To the forge", "WalkTo", "Examine", 1, 1, 1, 0, "signpost", i++));
		objects.add(new GameObjectDef("signpost", "To the Barbarian's  Village", "WalkTo", "Examine", 1, 1, 1, 0, "signpost", i++));
		objects.add(new GameObjectDef("signpost", "To Al Kharid", "WalkTo", "Examine", 1, 1, 1, 0, "signpost", i++));
		objects.add(new GameObjectDef("Compost Heap", "A smelly pile of compost", "WalkTo", "Search", 1, 2, 2, 0, "compost", i++));
		objects.add(new GameObjectDef("Coffin", "This coffin is closed", "Open", "Examine", 1, 2, 2, 0, "coffin", i++));
		objects.add(new GameObjectDef("Coffin", "This coffin is open", "Search", "Close", 1, 2, 2, 0, "coffin2", i++));
		objects.add(new GameObjectDef("gate", "You can pass through this on the members server", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("gate", "You can pass through this on the members server", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("sign", "The Bank of runescape", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("cupboard", "The cupboard is shut", "open", "Examine", 1, 1, 2, 0, "cupboard", i++));
		objects.add(new GameObjectDef("cupboard", "Perhaps I should search it", "Search", "close", 1, 1, 2, 0, "cupboardopen", i++));
		objects.add(new GameObjectDef("doors", "The doors are shut", "Open", "Examine", 2, 1, 2, 0, "doubledoorsclosed", i++));
		objects.add(new GameObjectDef("torch", "A scary torch", "WalkTo", "Examine", 0, 1, 1, 0, "skulltorcha1", i++));
		objects.add(new GameObjectDef("Altar of Zamorak", "An altar dedicated to Zamorak", "Worship", "Examine", 1, 2, 1, 0, "chaosaltar", i++));
		objects.add(new GameObjectDef("Shield", "A display shield", "WalkTo", "Examine", 0, 1, 1, 0, "wallshield", i++));
		objects.add(new GameObjectDef("Grill", "some sort of ventilation", "WalkTo", "Examine", 0, 1, 1, 0, "wallgrill", i++));
		objects.add(new GameObjectDef("Cauldron", "A very large pot", "WalkTo", "drink from", 1, 1, 1, 0, "cauldron", i++));
		objects.add(new GameObjectDef("Grill", "some sort of ventilation", "Listen", "Examine", 0, 1, 1, 0, "wallgrill", i++));
		objects.add(new GameObjectDef("Mine Cart", "It's empty", "WalkTo", "Examine", 1, 1, 1, 0, "minecart", i++));
		objects.add(new GameObjectDef("Buffers", "Stop the carts falling off the end", "WalkTo", "Examine", 1, 1, 1, 0, "trackbuffer", i++));
		objects.add(new GameObjectDef("Track", "Train track", "WalkTo", "Examine", 0, 2, 2, 0, "trackcurve", i++));
		objects.add(new GameObjectDef("Track", "Train track", "WalkTo", "Examine", 0, 2, 2, 0, "trackpoints", i++));
		objects.add(new GameObjectDef("Track", "Train track", "WalkTo", "Examine", 0, 1, 1, 0, "trackstraight", i++));
		objects.add(new GameObjectDef("Hole", "I can see a witches cauldron directly below it", "WalkTo", "Examine", 1, 1, 1, 0, "hole", i++));
		objects.add(new GameObjectDef("ship", "A ship to Karamja", "board", "Examine", 0, 5, 3, 0, "Shipfront", i++));
		objects.add(new GameObjectDef("ship", "A ship to Karamja", "board", "Examine", 0, 2, 3, 0, "Shipmiddle", i++));
		objects.add(new GameObjectDef("ship", "A ship to Karamja", "board", "Examine", 0, 5, 3, 0, "Shipback", i++));
		objects.add(new GameObjectDef("Emergency escape ladder", "it's a ladder leading downwards", "Climb-Down", "Examine", 1, 1, 1, 0, "ladderdown", i++));
		objects.add(new GameObjectDef("sign", "Wydin's grocery", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("sign", "The Rusty Anchor", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("ship", "A ship to Port Sarim", "board", "Examine", 0, 5, 3, 0, "Shipfront", i++));
		objects.add(new GameObjectDef("ship", "A ship to Port Sarim", "board", "Examine", 0, 2, 3, 0, "Shipmiddle", i++));
		objects.add(new GameObjectDef("ship", "A ship to Port Sarim", "board", "Examine", 0, 5, 3, 0, "Shipback", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("hopper", "You put grain in here", "operate", "Examine", 1, 2, 2, 0, "milltop", i++));
		objects.add(new GameObjectDef("cupboard", "The cupboard is shut", "open", "Examine", 1, 1, 2, 0, "cupboard", i++));
		objects.add(new GameObjectDef("cupboard", "Perhaps I should search it", "Search", "close", 1, 1, 2, 0, "cupboardopen", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "bluriterock1", i++));
		objects.add(new GameObjectDef("Doric's anvil", "Property of Doric the dwarf", "Smith", "Examine", 1, 1, 1, 0, "anvil", i++));
		objects.add(new GameObjectDef("pottery oven", "I can fire clay pots in this", "WalkTo", "Examine", 1, 2, 2, 0, "potteryoven", i++));
		objects.add(new GameObjectDef("potter's wheel", "I can make clay pots using this", "WalkTo", "Examine", 1, 1, 1, 0, "potterywheel", i++));
		objects.add(new GameObjectDef("gate", "A gate from Lumbridge to Al Kharid", "Open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("gate", "This gate is open", "WalkTo", "Examine", 2, 1, 2, 0, "metalgateopen", i++));
		objects.add(new GameObjectDef("crate", "A crate used for storing bananas", "WalkTo", "Search", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("Banana tree", "A tree with nice ripe bananas growing on it", "WalkTo", "Pick Banana", 1, 1, 1, 0, "palm2", i++));
		objects.add(new GameObjectDef("Banana tree", "There are no bananas left on the tree", "WalkTo", "Pick Banana", 1, 1, 1, 0, "palm2", i++));
		objects.add(new GameObjectDef("crate", "A crate used for storing bananas", "WalkTo", "Search", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("Chest", "A battered old chest", "WalkTo", "Examine", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Flower", "Ooh thats pretty", "WalkTo", "Examine", 0, 1, 1, 0, "flower", i++));
		objects.add(new GameObjectDef("sign", "Fishing Supplies", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("sign", "Jewellers", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("Potato", "A potato plant", "WalkTo", "pick", 0, 1, 1, 0, "potato", i++));
		objects.add(new GameObjectDef("fish", "I can see fish swimming in the water", "Fish", "Examine", 0, 1, 1, 0, "fishing", i++));
		objects.add(new GameObjectDef("fish", "I can see fish swimming in the water", "Fish", "Examine", 0, 1, 1, 0, "fishing", i++));
		objects.add(new GameObjectDef("fish", "I can see fish swimming in the water", "Fish", "Examine", 0, 1, 1, 0, "fishing", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "silverrock1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "silverrock1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading upwards", "Climb-Up", "Examine", 1, 1, 1, 0, "ladder", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading downwards", "Climb-Down", "Examine", 1, 1, 1, 0, "ladderdown", i++));
		objects.add(new GameObjectDef("Altar of Saradomin", "An altar dedicated to Saradomin", "Worship", "Examine", 1, 2, 1, 0, "altar", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading downwards", "Climb-Down", "Examine", 1, 1, 1, 0, "ladderdown", i++));
		objects.add(new GameObjectDef("Coffin", "This coffin is closed", "Open", "Examine", 1, 2, 2, 0, "coffin", i++));
		objects.add(new GameObjectDef("Coffin", "This coffin is open", "Search", "Close", 1, 2, 2, 0, "coffin2", i++));
		objects.add(new GameObjectDef("Smashed table", "This table has seen better days", "WalkTo", "Examine", 1, 1, 1, 0, "smashedtable", i++));
		objects.add(new GameObjectDef("Fungus", "A creepy looking fungus", "WalkTo", "Examine", 0, 1, 1, 0, "nastyfungus", i++));
		objects.add(new GameObjectDef("Smashed chair", "This chair is broken", "WalkTo", "Examine", 1, 1, 1, 0, "smashedchair", i++));
		objects.add(new GameObjectDef("Broken pillar", "The remains of a pillar", "WalkTo", "Examine", 1, 1, 1, 0, "brokenpillar", i++));
		objects.add(new GameObjectDef("Fallen tree", "A fallen tree", "WalkTo", "Examine", 1, 3, 2, 0, "fallentree", i++));
		objects.add(new GameObjectDef("Danger Sign", "Danger!", "WalkTo", "Examine", 1, 1, 1, 0, "dangersign", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "runiterock1", i++));
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "runiteruck1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Gravestone", "A big impressive gravestone", "WalkTo", "Examine", 1, 2, 2, 0, "largegrave", i++));
		objects.add(new GameObjectDef("bone", "Eep!", "WalkTo", "Examine", 1, 1, 1, 0, "curvedbone", i++));
		objects.add(new GameObjectDef("bone", "This would feed a dog for a month", "WalkTo", "Examine", 1, 1, 1, 0, "largebone", i++));
		objects.add(new GameObjectDef("carcass", "I think it's dead", "WalkTo", "Examine", 1, 2, 2, 0, "carcass", i++));
		objects.add(new GameObjectDef("animalskull", "I wouldn't like to meet a live one", "WalkTo", "Examine", 1, 1, 1, 0, "animalskull", i++));
		objects.add(new GameObjectDef("Vine", "A creepy creeper", "WalkTo", "Examine", 0, 1, 1, 0, "vine", i++));
		objects.add(new GameObjectDef("Vine", "A creepy creeper", "WalkTo", "Examine", 0, 1, 1, 0, "vinecorner", i++));
		objects.add(new GameObjectDef("Vine", "A creepy creeper", "WalkTo", "Examine", 0, 1, 1, 0, "vinejunction", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "WalkTo", "Examine", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading downwards", "Climb-Down", "Examine", 1, 1, 1, 0, "ladderdown", i++));
		objects.add(new GameObjectDef("ship", "The Lumbridge Lady", "board", "Examine", 0, 5, 3, 0, "Shipfront", i++));
		objects.add(new GameObjectDef("ship", "The Lumbridge Lady", "board", "Examine", 0, 5, 3, 0, "Shipback", i++));
		objects.add(new GameObjectDef("hole", "This ship isn't much use with that there", "WalkTo", "Examine", 2, 1, 1, 0, "brokenwall", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading upwards", "Climb-Up", "Examine", 1, 1, 1, 0, "ladder", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("hole", "This ship isn't much use with that there", "WalkTo", "Examine", 2, 1, 1, 0, "brokenwall", i++));
		objects.add(new GameObjectDef("ship", "The Lumbridge Lady", "board", "Examine", 0, 5, 3, 0, "Shipfront", i++));
		objects.add(new GameObjectDef("ship", "The Lumbridge Lady", "board", "Examine", 0, 5, 3, 0, "Shipback", i++));
		objects.add(new GameObjectDef("Altar of Guthix", "An altar dedicated to Guthix", "Worship", "Examine", 1, 2, 2, 0, "dolmen", i++));
		objects.add(new GameObjectDef("The Cauldron of Thunder", "A very large pot", "WalkTo", "Examine", 1, 1, 1, 0, "cauldron", i++));
		objects.add(new GameObjectDef("Tree", "A leafy tree", "Search", "Examine", 1, 1, 1, 0, "tree", i++));
		objects.add(new GameObjectDef("ship", "A ship to Entrana", "board", "Examine", 0, 5, 3, 0, "Shipfront", i++));
		objects.add(new GameObjectDef("ship", "A ship to Entrana", "board", "Examine", 0, 2, 3, 0, "Shipmiddle", i++));
		objects.add(new GameObjectDef("ship", "A ship to Entrana", "board", "Examine", 0, 5, 3, 0, "Shipback", i++));
		objects.add(new GameObjectDef("ship", "A ship to Port Sarim", "board", "Examine", 0, 5, 3, 0, "Shipfront", i++));
		objects.add(new GameObjectDef("ship", "A ship to Port Sarim", "board", "Examine", 0, 2, 3, 0, "Shipmiddle", i++));
		objects.add(new GameObjectDef("ship", "A ship to Port Sarim", "board", "Examine", 0, 5, 3, 0, "Shipback", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading downwards", "Climb-Down", "Examine", 1, 1, 1, 0, "ladderdown", i++));
		objects.add(new GameObjectDef("Dramen Tree", "This tree doesn't look too healthy", "Chop", "Examine", 1, 1, 1, 0, "dramentree", i++));
		objects.add(new GameObjectDef("hopper", "You put grain in here", "operate", "Examine", 1, 2, 2, 0, "milltop", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "WalkTo", "Examine", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading upwards", "Climb-Up", "Examine", 1, 1, 1, 0, "ladder", i++));
		objects.add(new GameObjectDef("sign", "2-handed swords sold here", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("sign", "ye olde herbalist", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("gate", "You can pass through this on the members server", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("gate", "You can pass through this on the members server", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("gate", "You can pass through this on the members server", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("Door mat", "If I ever get my boots muddy I know where to come", "search", "Examine", 0, 1, 1, 0, "Doormat", i++));
		objects.add(new GameObjectDef("gate", "The gate is closed", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("Cauldron", "A very large pot", "WalkTo", "Examine", 1, 1, 1, 0, "cauldron", i++));
		objects.add(new GameObjectDef("cupboard", "The cupboard is shut", "open", "Examine", 1, 1, 2, 0, "cupboard", i++));
		objects.add(new GameObjectDef("cupboard", "Perhaps I should search it", "Search", "close", 1, 1, 2, 0, "cupboardopen", i++));
		objects.add(new GameObjectDef("gate", "The bank vault gate", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("fish", "I can see fish swimming in the water", "Fish", "Examine", 0, 1, 1, 0, "fishing", i++));
		objects.add(new GameObjectDef("sign", "Harry's fishing shack", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("cupboard", "The cupboard is shut", "open", "Examine", 1, 1, 2, 0, "cupboard", i++));
		objects.add(new GameObjectDef("cupboard", "Perhaps I should search it", "Search", "close", 1, 1, 2, 0, "cupboardopen", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("sign", "The shrimp and parrot", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("signpost", "Palm Street", "WalkTo", "Examine", 1, 1, 1, 0, "signpost", i++));
		objects.add(new GameObjectDef("Rockslide", "A pile of rocks blocks your path", "Mine", "Prospect", 1, 1, 1, 0, "rock3", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading upwards", "Climb-Up", "Examine", 1, 1, 1, 0, "ladder", i++));
		objects.add(new GameObjectDef("fish", "I can see fish swimming in the lava!", "Fish", "Examine", 0, 1, 1, 0, "fishing", i++));
		objects.add(new GameObjectDef("barrel", "Its got ale in it", "WalkTo", "Examine", 1, 1, 1, 0, "barrel", i++));
		objects.add(new GameObjectDef("table", "It's a sturdy table", "WalkTo", "Examine", 1, 2, 1, 96, "bigtable", i++));
		objects.add(new GameObjectDef("Fireplace", "It would be very cold without this", "WalkTo", "Examine", 1, 2, 1, 0, "fireplacea1", i++));
		objects.add(new GameObjectDef("Egg", "Thats one big egg!", "WalkTo", "Examine", 1, 1, 1, 0, "bigegg", i++));
		objects.add(new GameObjectDef("Eggs", "They'd make an impressive omlette", "WalkTo", "Examine", 1, 1, 1, 0, "eggs", i++));
		objects.add(new GameObjectDef("Stalagmites", "Hmm pointy", "WalkTo", "Examine", 1, 1, 1, 0, "stalagmites", i++));
		objects.add(new GameObjectDef("Stool", "A simple three legged stool", "WalkTo", "Examine", 1, 1, 1, 0, "stool", i++));
		objects.add(new GameObjectDef("Bench", "It doesn't look to comfortable", "WalkTo", "Examine", 1, 1, 1, 0, "wallbench", i++));
		objects.add(new GameObjectDef("table", "A round table ideal for knights", "WalkTo", "Examine", 1, 2, 2, 0, "bigroundtable", i++));
		objects.add(new GameObjectDef("table", "A handy little table", "WalkTo", "Examine", 1, 1, 1, 96, "roundtable", i++));
		objects.add(new GameObjectDef("fountain of heros", "Use a dragonstone gem here to increase it's abilties", "WalkTo", "Examine", 1, 2, 2, 0, "fountain", i++));
		objects.add(new GameObjectDef("bush", "A leafy bush", "WalkTo", "Examine", 1, 1, 1, 0, "bush1", i++));
		objects.add(new GameObjectDef("hedge", "A carefully trimmed hedge", "WalkTo", "Examine", 1, 1, 1, 0, "bush2", i++));
		objects.add(new GameObjectDef("flower", "A nice colourful flower", "WalkTo", "Examine", 1, 1, 1, 0, "blueflower", i++));
		objects.add(new GameObjectDef("plant", "Hmm leafy", "WalkTo", "Examine", 1, 1, 1, 0, "smallfern", i++));
		objects.add(new GameObjectDef("Giant crystal", "How unusual a crystal with a wizard trapped in it", "WalkTo", "Examine", 1, 3, 3, 0, "giantcrystal", i++));
		objects.add(new GameObjectDef("sign", "The dead man's chest", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("sign", "The rising sun", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("crate", "A large wooden storage box", "WalkTo", "Search", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("crate", "A large wooden storage box", "WalkTo", "Search", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("ship", "A merchant ship", "stow away", "Examine", 0, 5, 3, 0, "Shipfront", i++));
		objects.add(new GameObjectDef("ship", "A merchant ship", "stow away", "Examine", 0, 5, 3, 0, "Shipback", i++));
		objects.add(new GameObjectDef("beehive", "It's guarded by angry looking bees", "WalkTo", "Examine", 1, 1, 1, 0, "beehive", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading upwards", "Climb-Up", "Examine", 1, 1, 1, 0, "ladder", i++));
		objects.add(new GameObjectDef("Altar of Zamorak", "An altar dedicated to Zamorak", "Worship", "Search", 1, 2, 1, 0, "chaosaltar", i++));
		objects.add(new GameObjectDef("sign", "Hickton's Archery store", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("signpost", "To Camelot", "WalkTo", "Examine", 1, 1, 1, 0, "signpost", i++));
		objects.add(new GameObjectDef("Archway", "A decorative marble arch", "WalkTo", "Examine", 1, 2, 1, 0, "marblearch", i++));
		objects.add(new GameObjectDef("Obelisk of water", "It doesn't look very wet", "WalkTo", "Examine", 1, 1, 1, 0, "obelisk", i++));
		objects.add(new GameObjectDef("Obelisk of fire", "It doesn't look very hot", "WalkTo", "Examine", 1, 1, 1, 0, "obelisk", i++));
		objects.add(new GameObjectDef("sand pit", "I can use a bucket to get sand from here", "WalkTo", "Search", 1, 2, 2, 0, "sandpit", i++));
		objects.add(new GameObjectDef("Obelisk of air", "A tall stone pointy thing", "WalkTo", "Examine", 1, 1, 1, 0, "obelisk", i++));
		objects.add(new GameObjectDef("Obelisk of earth", "A tall stone pointy thing", "WalkTo", "Examine", 1, 1, 1, 0, "obelisk", i++));
		objects.add(new GameObjectDef("gate", "You can pass through this on the members server", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("Oak Tree", "A grand old oak tree", "Chop", "Examine", 1, 2, 2, 0, "oaktree", i++));
		objects.add(new GameObjectDef("Willow Tree", "A weeping willow", "Chop", "Examine", 1, 2, 2, 0, "willowtree", i++));
		objects.add(new GameObjectDef("Maple Tree", "It's got nice shaped leaves", "Chop", "Examine", 1, 2, 2, 0, "mapletree", i++));
		objects.add(new GameObjectDef("Yew Tree", "A tough looking yew tree", "Chop", "Examine", 1, 2, 2, 0, "yewtree", i++));
		objects.add(new GameObjectDef(Config.S_IMPROVED_ITEM_OBJECT_NAMES ? "Magic Tree" : "Tree", "A magical tree", "Chop", "Examine", 1, 1, 1, 0, "magictree", i++));
		objects.add(new GameObjectDef("gate", "A gate guarded by a fierce barbarian", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("sign", "The forester's arms", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("flax", "A flax plant", Config.S_BATCH_PROGRESSION ? "pick" : "WalkTo", Config.S_BATCH_PROGRESSION ? "Examine" : "pick", 0, 1, 1, 0, "flax", i++));
		objects.add(new GameObjectDef("Large treestump", "Someone has chopped this tree down!", "WalkTo", "Examine", 1, 2, 2, 0, "treestump", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "goldrock1", i++));
		objects.add(new GameObjectDef("Lever", "It's a lever", "Pull", "Inspect", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("Lever", "It's a lever", "Pull", "Inspect", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("Lever", "It's a lever", "Pull", "Inspect", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("gate", "You can pass through this on the members server", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("ship", "A ship bound for Ardougne", "board", "Examine", 0, 5, 3, 0, "Shipfront", i++));
		objects.add(new GameObjectDef("ship", "A ship bound for Ardougne", "board", "Examine", 0, 5, 3, 0, "Shipback", i++));
		objects.add(new GameObjectDef("Bakers Stall", "A market stall", "WalkTo", "steal from", 1, 2, 2, 112, "market", i++));
		objects.add(new GameObjectDef("Silk Stall", "A market stall", "WalkTo", "steal from", 1, 2, 2, 112, "market", i++));
		objects.add(new GameObjectDef("Fur Stall", "A market stall", "WalkTo", "steal from", 1, 2, 2, 112, "market", i++));
		objects.add(new GameObjectDef("Silver Stall", "A market stall", "WalkTo", "steal from", 1, 2, 2, 112, "market", i++));
		objects.add(new GameObjectDef("Spices Stall", "A market stall", "WalkTo", "steal from", 1, 2, 2, 112, "market", i++));
		objects.add(new GameObjectDef("gems Stall", "A market stall", "WalkTo", "steal from", 1, 2, 2, 112, "market", i++));
		objects.add(new GameObjectDef("crate", "A large heavy sealed crate", "WalkTo", "Search", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("crate", "A large heavy sealed crate", "WalkTo", "Search", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("sign", "RPDT depot", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("stairs", "These lead upstairs", "Go up", "Search for traps", 1, 2, 3, 0, "stonestairs", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Search for traps", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Search for traps", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Search for traps", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Search for traps", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Search for traps", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Chest", "someone is stealing something from it", "WalkTo", "Examine", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Search for traps", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("empty stall", "A market stall", "WalkTo", "Examine", 1, 2, 2, 112, "market", i++));
		objects.add(new GameObjectDef("stairs", "These lead upstairs", "Go up", "Examine", 1, 2, 3, 0, "stonestairs", i++));
		objects.add(new GameObjectDef("hopper", "You put grain in here", "operate", "Examine", 1, 2, 2, 0, "milltop", i++));
		objects.add(new GameObjectDef("signpost", "Ardougne city zoo", "WalkTo", "Examine", 1, 1, 1, 0, "signpost", i++));
		objects.add(new GameObjectDef("sign", "The flying horse", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("gate", "You can pass through this on the members server", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("gate", "You can pass through this on the members server", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("Lever", "The lever is up", "pull", "Examine", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("Lever", "The lever is up", "pull", "Examine", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("pipe", "a dirty sewer pipe", "WalkTo", "Examine", 1, 1, 1, 0, "wallpipe", i++));
		objects.add(new GameObjectDef("fish", "I can see fish swimming in the water", "Bait", "Examine", 0, 1, 1, 0, "fishing", i++));
		objects.add(new GameObjectDef("fish", "I can see fish swimming in the water", "Bait", "Examine", 0, 1, 1, 0, "fishing", i++));
		objects.add(new GameObjectDef("fish", "I can see fish swimming in the water", "Bait", "Examine", 0, 1, 1, 0, "fishing", i++));
		objects.add(new GameObjectDef("fish", "I can see fish swimming in the water", "Bait", "Examine", 0, 1, 1, 0, "fishing", i++));
		objects.add(new GameObjectDef("Vine", "A creepy creeper", "WalkTo", "Examine", 0, 1, 1, 0, "vine", i++));
		objects.add(new GameObjectDef("gate", "The main entrance to McGrubor's wood", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("gate", "The gate is open", "WalkTo", "Examine", 2, 1, 2, 0, "woodengateopen", i++));
		objects.add(new GameObjectDef("gate", "The gate is closed", "open", "Examine", 2, 1, 2, 0, "woodengateclosed", i++));
		objects.add(new GameObjectDef("stairs", "These lead downstairs", "Go down", "Examine", 1, 2, 3, 0, "stonestairsdown", i++));
		objects.add(new GameObjectDef("broken cart", "A farm cart", "WalkTo", "Examine", 1, 2, 3, 0, "brokencart", i++));
		objects.add(new GameObjectDef("Lever", "It's a lever", "Pull", "Searchfortraps", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("clock pole blue", "A pole - a pole to put cog's on", "inspect", "Examine", 0, 1, 1, 0, "clockpoleblue", i++));
		objects.add(new GameObjectDef("clock pole red", "A pole - a pole to put cog's on", "inspect", "Examine", 0, 1, 1, 0, "clockpolered", i++));
		objects.add(new GameObjectDef("clock pole purple", "A pole - a pole to put cog's on", "inspect", "Examine", 0, 1, 1, 0, "clockpolepurple", i++));
		objects.add(new GameObjectDef("clock pole black", "A pole - a pole to put cog's on", "inspect", "Examine", 0, 1, 1, 0, "clockpoleblack", i++));
		objects.add(new GameObjectDef("wallclockface", "It's a large clock face", "WalkTo", "Examine", 1, 2, 2, 0, "wallclockface", i++));
		objects.add(new GameObjectDef("Lever Bracket", "Theres something missing here", "WalkTo", "Examine", 0, 1, 1, 0, "leverbracket", i++));
		objects.add(new GameObjectDef("Lever", "It's a lever", "Pull", "Examine", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("stairs", "These lead upstairs", "Go up", "Examine", 1, 2, 3, 0, "woodenstairs", i++));
		objects.add(new GameObjectDef("stairs", "These lead downstairs", "Go down", "Examine", 1, 2, 3, 0, "woodenstairsdown", i++));
		objects.add(new GameObjectDef("gate", "The gate is closed", "open", "Examine", 2, 1, 2, 0, "metalgateclosed2", i++));
		objects.add(new GameObjectDef("gate", "The gate is open", "close", "Examine", 3, 1, 2, 0, "metalgateopen2", i++));
		objects.add(new GameObjectDef("Lever", "The lever is up", "pull", "Examine", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("Lever", "The lever is up", "push", "Examine", 0, 1, 1, 0, "leverdown", i++));
		objects.add(new GameObjectDef("Foodtrough", "It's for feeding the rat's", "WalkTo", "Examine", 1, 2, 1, 0, "Foodtrough", i++));
		objects.add(new GameObjectDef("fish", "I can see fish swimming in the water", "Fish", "Examine", 0, 1, 1, 0, "fishing", i++));
		objects.add(new GameObjectDef("spearwall", "It's a defensive battlement", "WalkTo", "Examine", 1, 2, 1, 0, "spearwall", i++));
		objects.add(new GameObjectDef("hornedskull", "A horned dragon skull", "WalkTo", "Examine", 1, 2, 2, 0, "hornedskull", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "picklock", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "picklock", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("guardscupboard", "The cupboard is shut", "open", "Examine", 1, 1, 2, 0, "cupboard", i++));
		objects.add(new GameObjectDef("guardscupboard", "Perhaps I should search it", "Search", "close", 1, 1, 2, 0, "cupboardopen", i++));
		objects.add(new GameObjectDef("Coal truck", "I can use this to transport coal", "get coal from", "Examine", 1, 1, 1, 0, "minecart", i++));
		objects.add(new GameObjectDef("ship", "A ship to Port Birmhaven", "board", "Examine", 0, 5, 3, 0, "Shipfront", i++));
		objects.add(new GameObjectDef("ship", "A ship to Port Birmhaven", "board", "Examine", 0, 2, 3, 0, "Shipmiddle", i++));
		objects.add(new GameObjectDef("ship", "A ship to Port Birmhaven", "board", "Examine", 0, 5, 3, 0, "Shipback", i++));
		objects.add(new GameObjectDef("Tree", "It's a tree house", "WalkTo", "Examine", 1, 1, 1, 0, "toplesstree", i++));
		objects.add(new GameObjectDef("Ballista", "It's a war machine", "fire", "Examine", 1, 4, 1, 0, "catabow", i++));
		objects.add(new GameObjectDef("largespear", "", "WalkTo", "Examine", 1, 2, 1, 0, "catabowarrow", i++));
		objects.add(new GameObjectDef("spirit tree", "A grand old spirit tree", "talk to", "Examine", 1, 2, 2, 0, "ent", i++));
		objects.add(new GameObjectDef("young spirit Tree", "Ancestor of the spirit tree", "talk to", "Examine", 1, 1, 1, 0, "tree2", i++));
	}

	private static void loadGameObjectDefinitionsB() {
		int i = objects.size();
		objects.add(new GameObjectDef("gate", "The gate is closed", "talk through", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("wall", "A damaged wall", "climb", "Examine", 1, 3, 1, 0, "khazardwall", i++));
		objects.add(new GameObjectDef("tree", "An exotic looking tree", "WalkTo", "Examine", 1, 1, 1, 0, "jungle tree 2", i++));
		objects.add(new GameObjectDef("tree", "An exotic looking tree", "WalkTo", "Examine", 1, 1, 1, 0, "jungle tree 1", i++));
		objects.add(new GameObjectDef("Fern", "An exotic leafy plant", "WalkTo", "Examine", 0, 1, 1, 0, "jungle fern textured", i++));
		objects.add(new GameObjectDef("Fern", "An exotic leafy plant", "WalkTo", "Examine", 0, 1, 1, 0, "jungle fern textured 2", i++));
		objects.add(new GameObjectDef("Fern", "An exotic leafy plant", "WalkTo", "Examine", 0, 1, 1, 0, "jungle fern textured 3", i++));
		objects.add(new GameObjectDef("Fern", "An exotic leafy plant", "WalkTo", "Examine", 0, 1, 1, 0, "jungle fern textured 4", i++));
		objects.add(new GameObjectDef("fly trap", "A small carnivourous plant", "approach", "Search", 0, 1, 1, 0, "jungle fly trap", i++));
		objects.add(new GameObjectDef("Fern", "An exotic leafy plant", "WalkTo", "Examine", 0, 1, 1, 0, "jungle fern", i++));
		objects.add(new GameObjectDef("Fern", "An exotic spikey plant", "WalkTo", "Examine", 0, 1, 1, 0, "jungle spikey fern", i++));
		objects.add(new GameObjectDef("plant", "What an unusual plant", "WalkTo", "Examine", 0, 1, 1, 0, "jungle strange plant", i++));
		objects.add(new GameObjectDef("plant", "An odd looking plant", "WalkTo", "Examine", 1, 1, 1, 0, "jungle strange plant 2", i++));
		objects.add(new GameObjectDef("plant", "some nice jungle foliage", "WalkTo", "Examine", 1, 1, 1, 0, "jungle medium size plant", i++));
		objects.add(new GameObjectDef("stone head", "It looks like it's been here some time", "WalkTo", "Examine", 1, 2, 2, 0, "jungle statue", i++));
		objects.add(new GameObjectDef("dead Tree", "A rotting tree", "WalkTo", "Examine", 1, 1, 1, 0, "deadtree2", i++));
		objects.add(new GameObjectDef("sacks", "Yep they're sacks", "WalkTo", "prod", 1, 1, 1, 0, "sacks", i++));
		objects.add(new GameObjectDef("khazard open Chest", "Perhaps I should search it", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("khazard shut Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("doorframe", "It's a stone doorframe", "WalkTo", "Examine", 3, 1, 2, 0, "doubledoorframe", i++));
		objects.add(new GameObjectDef("Sewer valve", "It changes the water flow of the sewer's", "turn left", "turn right", 1, 1, 1, 0, "sewervalve", i++));
		objects.add(new GameObjectDef("Sewer valve 2", "It changes the water flow of the sewer's", "turn left", "turn right", 1, 1, 1, 0, "sewervalve", i++));
		objects.add(new GameObjectDef("Sewer valve 3", "It changes the water flow of the sewer's", "turn left", "turn right", 1, 1, 1, 0, "sewervalve", i++));
		objects.add(new GameObjectDef("Sewer valve 4", "It changes the water flow of the sewer's", "turn left", "turn right", 1, 1, 1, 0, "sewervalve", i++));
		objects.add(new GameObjectDef("Sewer valve 5", "It changes the water flow of the sewer's", "turn left", "turn right", 1, 1, 1, 0, "sewervalve", i++));
		objects.add(new GameObjectDef("Cave entrance", "I wonder what is inside...", "enter", "Examine", 1, 2, 2, 0, "caveentrance", i++));
		objects.add(new GameObjectDef("Log bridge", "A tree gnome construction", "WalkTo", "Examine", 0, 1, 1, 0, "logbridgelow", i++));
		objects.add(new GameObjectDef("Log bridge", "A tree gnome construction", "WalkTo", "Examine", 0, 1, 1, 0, "logbridgehigh", i++));
		objects.add(new GameObjectDef("tree platform", "A tree gnome construction", "WalkTo", "Examine", 0, 1, 1, 0, "treeplatformhigh", i++));
		objects.add(new GameObjectDef("tree platform", "A tree gnome construction", "WalkTo", "Examine", 0, 1, 1, 0, "treeplatformlow", i++));
		objects.add(new GameObjectDef("gate", "The gate is open", "close", "Examine", 2, 1, 2, 0, "metalgateopen2", i++));
		objects.add(new GameObjectDef("tree platform", "A tree gnome construction", "WalkTo", "Examine", 0, 1, 1, 0, "largetreeplatformlow", i++));
		objects.add(new GameObjectDef("tree platform", "A tree gnome construction", "WalkTo", "Examine", 0, 1, 1, 0, "largetreeplatformhigh", i++));
		objects.add(new GameObjectDef("Log bridge", "A tree gnome construction", "WalkTo", "Examine", 0, 1, 1, 0, "logbridgecurvedhigh", i++));
		objects.add(new GameObjectDef("Log bridge", "A tree gnome construction", "WalkTo", "Examine", 0, 1, 1, 0, "logbridgecurvedlow", i++));
		objects.add(new GameObjectDef("tree platform", "A tree gnome construction", "WalkTo", "Examine", 0, 1, 1, 0, "treeplatformlow2", i++));
		objects.add(new GameObjectDef("tree platform", "A tree gnome construction", "WalkTo", "Examine", 0, 1, 1, 0, "treeplatformhigh2", i++));
		objects.add(new GameObjectDef("Tribal brew", "A very large pot", "WalkTo", "drink", 1, 1, 1, 0, "cauldron", i++));
		objects.add(new GameObjectDef("Pineapple tree", "A tree with nice ripe pineapples growing on it", "WalkTo", "Pick pineapple", 1, 1, 1, 0, "palm2", i++));
		objects.add(new GameObjectDef("Pineapple tree", "There are no pineapples left on the tree", "WalkTo", "Pick pineapple", 1, 1, 1, 0, "palm2", i++));
		objects.add(new GameObjectDef("log raft", "A mighty fine raft", "board", "Examine", 0, 1, 1, 96, "lograft", i++));
		objects.add(new GameObjectDef("log raft", "A mighty fine raft", "board", "Examine", 0, 1, 1, 96, "lograft", i++));
		objects.add(new GameObjectDef("Tomb of hazeel", "A clay shrine to lord hazeel", "WalkTo", "Examine", 1, 1, 2, 96, "hazeeltomb", i++));
		objects.add(new GameObjectDef("range", "A pot of soup slowly cooking", "WalkTo", "Examine", 1, 1, 2, 0, "range", i++));
		objects.add(new GameObjectDef("Bookcase", "A large collection of books", "Search", "Examine", 1, 1, 2, 0, "bookcase", i++));
		objects.add(new GameObjectDef("Carnillean Chest", "Perhaps I should search it", "WalkTo", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Carnillean Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("crate", "A crate used for storing food", "Search", "Examine", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("Butlers cupboard", "The cupboard is shut", "open", "Examine", 1, 1, 2, 0, "cupboard", i++));
		objects.add(new GameObjectDef("Butlers cupboard", "The cupboard is open", "Search", "close", 1, 1, 2, 0, "cupboardopen", i++));
		objects.add(new GameObjectDef("gate", "The gate is open", "WalkTo", "Examine", 2, 1, 2, 0, "woodengateopen", i++));
		objects.add(new GameObjectDef("gate", "The gate is closed", "open", "Examine", 2, 1, 2, 0, "woodengateclosed", i++));
		objects.add(new GameObjectDef("Cattle furnace", "A red hot furnace", "WalkTo", "Examine", 1, 2, 2, 0, "furnace", i++));
		objects.add(new GameObjectDef("Ardounge wall", "A huge wall seperating east and west ardounge", "WalkTo", "Examine", 1, 1, 3, 0, "ardoungewall", i++));
		objects.add(new GameObjectDef("Ardounge wall corner", "A huge wall seperating east and west ardounge", "WalkTo", "Examine", 1, 1, 1, 0, "ardoungewallcorner", i++));
		objects.add(new GameObjectDef("Dug up soil", "A freshly dug pile of mud", "WalkTo", "Examine", 0, 1, 1, 0, "mudpatch", i++));
		objects.add(new GameObjectDef("Pile of mud", "Mud caved in from above", "climb", "Examine", 1, 2, 1, 0, "mudpile", i++));
		objects.add(new GameObjectDef("large Sewer pipe", "a dirty sewer pipe", "enter", "Examine", 1, 1, 1, 0, "largesewerpipe", i++));
		objects.add(new GameObjectDef("Ardounge wall gateway", "A huge set of heavy wooden doors", "open", "Examine", 2, 1, 2, 0, "doubledoorsclosed", i++));
		objects.add(new GameObjectDef("cupboard", "The cupboard is shut", "open", "Examine", 1, 1, 2, 0, "cupboard", i++));
		objects.add(new GameObjectDef("cupboard", "The cupboard is open", "Search", "close", 1, 1, 2, 0, "cupboardopen", i++));
		objects.add(new GameObjectDef("Fishing crane", "For hauling in large catches of fish", "operate", "Examine", 1, 1, 2, 0, "fishingcrane", i++));
		objects.add(new GameObjectDef("Rowboat", "A reasonably sea worthy two man boat", "WalkTo", "Examine", 1, 2, 2, 0, "rowboat", i++));
		objects.add(new GameObjectDef("Damaged Rowboat", "A not so sea worthy two man boat", "WalkTo", "Examine", 1, 2, 2, 0, "rowboatsinking", i++));
		objects.add(new GameObjectDef("barrel", "I wonder what's inside", "WalkTo", "Search", 1, 1, 1, 0, "barrel", i++));
		objects.add(new GameObjectDef("gate", "The gate is closed", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading upwards", "Climb-Up", "Examine", 1, 1, 1, 0, "ladder", i++));
		objects.add(new GameObjectDef("Fishing crane", "For hauling in large catches of fish", "operate", "Examine", 1, 1, 1, 0, "fishingcranerot1", i++));
		objects.add(new GameObjectDef("Fishing crane", "For hauling in large catches of fish", "operate", "Examine", 1, 1, 1, 0, "fishingcranerot2", i++));
		objects.add(new GameObjectDef("Waterfall", "it's a waterfall", "WalkTo", "Examine", 2, 1, 2, 0, "waterfall", i++));
		objects.add(new GameObjectDef("leaflessTree", "A pointy tree", "jump off", "jump to next", 1, 1, 1, 0, "deadtree2base", i++));
		objects.add(new GameObjectDef("leaflessTree", "A pointy tree", "jump off", "jump to next", 1, 1, 1, 0, "deadtree2base", i++));
		objects.add(new GameObjectDef("log raft", "A mighty fine raft", "board", "Examine", 0, 1, 1, 96, "lograft", i++));
		objects.add(new GameObjectDef("doors", "The doors are shut", "Open", "Examine", 2, 1, 2, 0, "doubledoorsclosed", i++));
		objects.add(new GameObjectDef("Well", "An oddly placed well", "operate", "Examine", 1, 2, 2, 0, "well", i++));
		objects.add(new GameObjectDef("Tomb of glarial", "A stone tomb surrounded by flowers", "Search", "Examine", 1, 2, 4, 96, "elventomb", i++));
		objects.add(new GameObjectDef("Waterfall", "it's a fast flowing waterfall", "jump off", "Examine", 2, 1, 2, 0, "waterfalllev1", i++));
		objects.add(new GameObjectDef("Waterfall", "it's a fast flowing waterfall", "jump off", "Examine", 0, 1, 2, 0, "waterfalllev2", i++));
		objects.add(new GameObjectDef("Bookcase", "A large collection of books", "Search", "Examine", 1, 1, 2, 0, "bookcase", i++));
		objects.add(new GameObjectDef("doors", "The doors are shut", "Open", "Examine", 2, 1, 2, 0, "doubledoorsclosed", i++));
		objects.add(new GameObjectDef("doors", "The doors are shut", "Open", "Examine", 2, 1, 2, 0, "doubledoorsclosed", i++));
		objects.add(new GameObjectDef("Stone stand", "On top is an indent the size of a rune stone", "WalkTo", "Examine", 1, 1, 1, 0, "stonestand", i++));
		objects.add(new GameObjectDef("Stone stand", "On top is an indent the size of a rune stone", "WalkTo", "Examine", 1, 1, 1, 0, "stonestand", i++));
		objects.add(new GameObjectDef("Stone stand", "On top is an indent the size of a rune stone", "WalkTo", "Examine", 1, 1, 1, 0, "stonestand", i++));
		objects.add(new GameObjectDef("Stone stand", "On top is an indent the size of a rune stone", "WalkTo", "Examine", 1, 1, 1, 0, "stonestand", i++));
		objects.add(new GameObjectDef("Stone stand", "On top is an indent the size of a rune stone", "WalkTo", "Examine", 1, 1, 1, 0, "stonestand", i++));
		objects.add(new GameObjectDef("Stone stand", "On top is an indent the size of a rune stone", "WalkTo", "Examine", 1, 1, 1, 0, "stonestand", i++));
		objects.add(new GameObjectDef("Glarial's Gravestone", "There is an indent the size of a pebble in the stone's center", "read", "Examine", 1, 1, 1, 0, "gravestone1", i++));
		objects.add(new GameObjectDef("gate", "The gate is closed", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("crate", "It's a crate", "Search", "Examine", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("leaflessTree", "A pointy tree", "jump off", "Examine", 1, 1, 1, 0, "deadtree2base", i++));
		objects.add(new GameObjectDef("Statue of glarial", "A statue of queen glarial - something's missing", "WalkTo", "Examine", 1, 1, 1, 0, "glarialsstatue", i++));
		objects.add(new GameObjectDef("Chalice of eternity", "A magically elevated chalice full of treasure", "WalkTo", "Examine", 1, 1, 1, 0, "baxtorianchalice", i++));
		objects.add(new GameObjectDef("Chalice of eternity", "A magically elevated chalice full of treasure", "empty", "Examine", 1, 1, 1, 0, "baxtorianchalicelow", i++));
		objects.add(new GameObjectDef("doors", "The doors are shut", "Open", "Examine", 2, 1, 2, 0, "doubledoorsclosed", i++));
		objects.add(new GameObjectDef("Lever", "The lever is up", "pull", "Examine", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("Lever", "The lever is up", "pull", "Examine", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("log raft remains", "oops!", "WalkTo", "Examine", 0, 1, 1, 96, "brokenlograft", i++));
		objects.add(new GameObjectDef("Mithril Tree", "A magical tree veined with mithril", "Chop", "Examine", 1, 1, 1, 0, "tree2", i++));
		objects.add(new GameObjectDef(" Range", "A hot well stoked range", "WalkTo", "Examine", 1, 1, 2, 0, "range", i++));
		objects.add(new GameObjectDef("crate", "It's an old crate", "WalkTo", "Search", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("fish", "I can see fish swimming in the water", "Fish", "Examine", 0, 1, 1, 0, "fishing", i++));
		objects.add(new GameObjectDef("Watch tower", "They're always watching", "approach", "Examine", 1, 2, 2, 0, "watchtower", i++));
		objects.add(new GameObjectDef("signpost", "Tourist infomation", "WalkTo", "Examine", 1, 1, 1, 0, "signpost", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("doors", "The doors are open", "WalkTo", "Examine", 2, 1, 2, 0, "doubledoorsopen", i++));
		objects.add(new GameObjectDef("Rope ladder", "A hand made ladder", "WalkTo", "Examine", 1, 1, 1, 0, "ropeladder", i++));
		objects.add(new GameObjectDef("cupboard", "The cupboard is shut", "open", "Examine", 1, 1, 2, 0, "cupboard", i++));
		objects.add(new GameObjectDef("cupboard", "Perhaps I should search it", "Search", "close", 1, 1, 2, 0, "cupboardopen", i++));
		objects.add(new GameObjectDef("Rope ladder", "A hand made ladder", "WalkTo", "Examine", 1, 1, 1, 0, "ropeladder", i++));
		objects.add(new GameObjectDef("Cooking pot", "the mourners are busy enjoying this stew", "WalkTo", "Examine", 1, 1, 1, 0, "cauldron", i++));
		objects.add(new GameObjectDef("Gallow", "Best not hang about!", "WalkTo", "Examine", 1, 2, 2, 0, "gallows", i++));
		objects.add(new GameObjectDef("gate", "The gate is closed", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("crate", "A crate used for storing confiscated goods", "WalkTo", "Search", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("cupboard", "The cupboard is shut", "open", "Examine", 1, 1, 2, 0, "cupboard", i++));
		objects.add(new GameObjectDef("cupboard", "Perhaps I should search it", "Search", "close", 1, 1, 2, 0, "cupboardopen", i++));
		objects.add(new GameObjectDef("gate", "You can pass through this on the members server", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("cupboard", "The cupboard is shut", "open", "Examine", 1, 1, 2, 0, "cupboard", i++));
		objects.add(new GameObjectDef("cupboard", "Perhaps I should search it", "Search", "close", 1, 1, 2, 0, "cupboardopen", i++));
		objects.add(new GameObjectDef("sign", "Tailors fancy dress", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("grand tree", "the grand tree", "WalkTo", "Examine", 0, 1, 1, 0, "grand tree-lev 0", i++));
		objects.add(new GameObjectDef("gate", "The gate is closed", "open", "Examine", 2, 1, 2, 0, "woodengateclosed", i++));
		objects.add(new GameObjectDef("gate", "The gate is open", "WalkTo", "close", 2, 1, 2, 0, "woodengateopen", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Log bridge", "A tree gnome construction", "WalkTo", "Examine", 0, 1, 1, 0, "logbridge lev0", i++));
		objects.add(new GameObjectDef("Watch tower", "A tree gnome construction", "WalkTo", "Examine", 1, 1, 1, 0, "gnomewatchtower lev0", i++));
		objects.add(new GameObjectDef("Log bridge", "A tree gnome construction", "WalkTo", "Examine", 0, 0, 0, 0, "logbridgejunction lev0", i++));
		objects.add(new GameObjectDef("climbing rocks", "I wonder if I can climb up these", "climb", "Examine", 0, 1, 1, 0, "climbing_rocks", i++));
		objects.add(new GameObjectDef("Ledge", "It looks rather thin", "balance on", "Examine", 0, 1, 0, 0, "corner_ledge", i++));
		objects.add(new GameObjectDef("Ledge", "It looks rather thin", "balance on", "Examine", 0, 1, 1, 0, "straight_ledge", i++));
		objects.add(new GameObjectDef("log", "It looks slippery", "WalkTo", "Examine", 0, 1, 1, 0, "log_balance1", i++));
		objects.add(new GameObjectDef("log", "It looks slippery", "WalkTo", "Examine", 0, 1, 1, 0, "log_balance2", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("PalmTree", "A shady palm tree", "Chop", "Examine", 1, 1, 1, 0, "palm", i++));
		objects.add(new GameObjectDef("Scorched Earth", "An area of burnt soil", "WalkTo", "Search", 0, 1, 1, 0, "mudpatch", i++));
		objects.add(new GameObjectDef("Rocks", "A moss covered rock", "Mine", "Search", 1, 1, 1, 0, "mossyrock", i++));
		objects.add(new GameObjectDef("sign", "The dancing donkey inn", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("fish", "I can see fish swimming in the water", "Fish", "Examine", 0, 1, 1, 0, "fishing", i++));
		objects.add(new GameObjectDef("Rocky Walkway", "A precarious rocky walkway", "Balance", "Examine", 1, 1, 1, 0, "rocktile", i++));
		objects.add(new GameObjectDef("Rocky Walkway", "A precarious rocky walkway", "Balance", "Examine", 1, 1, 1, 0, "rocktile", i++));
		objects.add(new GameObjectDef("Rocky Walkway", "A precarious rocky walkway", "Balance", "Examine", 1, 1, 1, 0, "rocktile", i++));
		objects.add(new GameObjectDef("Rocky Walkway", "A precarious rocky walkway", "Balance", "Examine", 1, 1, 1, 0, "rocktile", i++));
		objects.add(new GameObjectDef("fight Dummy", "I can practice my fighting here", "hit", "Examine", 1, 1, 1, 0, "sworddummy", i++));
		objects.add(new GameObjectDef("gate", "The gate is closed", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("Jungle Vine", "A deep jungle Vine", "WalkTo", "Search", 0, 1, 1, 0, "vine", i++));
		objects.add(new GameObjectDef("statue", "hand carved", "WalkTo", "Examine", 1, 1, 1, 0, "tribalstature", i++));
		objects.add(new GameObjectDef("sign", "Ye Olde Dragon Inn", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("grand tree", "the grand tree", "WalkTo", "Examine", 0, 1, 1, 0, "grand treeinside-lev 0", i++));
		objects.add(new GameObjectDef("grand tree", "the grand tree", "WalkTo", "Examine", 0, 1, 1, 0, "grand treeinside-lev 1", i++));
		objects.add(new GameObjectDef("grand tree", "the grand tree", "WalkTo", "Examine", 0, 1, 1, 0, "grand treeinside-lev 2", i++));
		objects.add(new GameObjectDef("grand tree", "the grand tree", "WalkTo", "Examine", 0, 1, 1, 0, "grand tree-lev 1", i++));
		objects.add(new GameObjectDef("grand tree", "the grand tree", "WalkTo", "Examine", 0, 1, 1, 0, "grand tree-lev 2", i++));
		objects.add(new GameObjectDef("Hillside Entrance", "Large doors that seem to lead into the hillside", "Open", "Search", 2, 1, 2, 0, "hillsidedoor", i++));
		objects.add(new GameObjectDef("tree", "A large exotic looking tree", "WalkTo", "Search", 1, 1, 1, 0, "jungle medium size plant", i++));
		objects.add(new GameObjectDef("Log bridge", "A tree gnome construction", "WalkTo", "Examine", 0, 0, 0, 0, "logbridgejunction lev1", i++));
		objects.add(new GameObjectDef("Tree platform", "A tree gnome construction", "WalkTo", "Examine", 0, 0, 0, 0, "fourwayplatform-lev 0", i++));
		objects.add(new GameObjectDef("Tree platform", "A tree gnome construction", "WalkTo", "Examine", 0, 0, 0, 0, "fourwayplatform-lev 1", i++));
		objects.add(new GameObjectDef("Metalic Dungeon Gate", "It seems to be closed", "Open", "Search", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("Log bridge", "A tree gnome construction", "WalkTo", "Examine", 0, 0, 0, 0, "logbridge lev1", i++));
		objects.add(new GameObjectDef("Log bridge", "A tree gnome construction", "WalkTo", "Examine", 0, 0, 0, 0, "logbridge lev2", i++));
		objects.add(new GameObjectDef("Watch tower", "A tree gnome construction", "WalkTo", "Examine", 1, 0, 0, 0, "gnomewatchtower lev1", i++));
		objects.add(new GameObjectDef("Watch tower", "A tree gnome construction", "WalkTo", "Examine", 1, 0, 0, 0, "gnomewatchtower lev2", i++));
		objects.add(new GameObjectDef("Shallow water", "A small opening in the ground with some spots of water", "WalkTo", "Investigate", 1, 2, 2, 0, "rockpoolwater", i++));
		objects.add(new GameObjectDef("Doors", "Perhaps you should give them a push", "Open", "Search", 2, 1, 2, 0, "hillsidedoor", i++));
		objects.add(new GameObjectDef("grand tree", "the grand tree", "WalkTo", "Examine", 0, 1, 1, 0, "grand tree-lev 3", i++));
		objects.add(new GameObjectDef("Tree Ladder", "it's a ladder leading upwards", "Climb-Up", "Examine", 0, 1, 1, 0, "ladder", i++));
		objects.add(new GameObjectDef("Tree Ladder", "it's a ladder leading downwards", "Climb-down", "Examine", 0, 1, 1, 0, "ladderdown", i++));
		objects.add(new GameObjectDef("blurberrys cocktail bar", "the gnome social hot spot", "WalkTo", "Examine", 0, 1, 1, 0, "blurberrybar", i++));
		objects.add(new GameObjectDef("Gem Rocks", "A rocky outcrop with a vein of semi precious stones", "Mine", "Prospect", 1, 1, 1, 0, "gemrock", i++));
		objects.add(new GameObjectDef("Giannes place", "Eat green eat gnome cruisine", "WalkTo", "Examine", 0, 1, 1, 0, "blurberrybar", i++));
		objects.add(new GameObjectDef("ropeswing", "A good place to train agility", "WalkTo", "Examine", 0, 1, 1, 0, "obstical_ropeswing", i++));
		objects.add(new GameObjectDef("net", "A good place to train agility", "WalkTo", "Examine", 1, 2, 1, 0, "obstical_net", i++));
		objects.add(new GameObjectDef("Tanning rack", "A sturdy rack for stretching and curing hides", "Tan", "Examine", 1, 1, 1, 0, "obstical_frame", i++));
		objects.add(new GameObjectDef("Tree", "It has a branch ideal for tying ropes to", "WalkTo", "Examine", 1, 2, 2, 0, "tree_for_rope", i++));
		objects.add(new GameObjectDef("Tree", "I wonder who put that rope there", "WalkTo", "Examine", 1, 2, 2, 0, "tree_with_rope", i++));
		objects.add(new GameObjectDef("Tree", "they look fun to swing on", "WalkTo", "Examine", 1, 2, 2, 0, "tree_with_vines", i++));
		objects.add(new GameObjectDef("cart", "A farm cart", "WalkTo", "Search", 1, 2, 3, 0, "cart", i++));
		objects.add(new GameObjectDef("fence", "it doesn't look too strong", "WalkTo", "Examine", 1, 1, 1, 0, "gnomefence", i++));
		objects.add(new GameObjectDef("beam", "A plank of wood", "WalkTo", "Examine", 0, 1, 1, 0, "beam", i++));
		objects.add(new GameObjectDef("Sign", "read me", "WalkTo", "Examine", 1, 1, 1, 0, "gnomesign", i++));
		objects.add(new GameObjectDef("Sign", "Blurberry's cocktail bar", "WalkTo", "Examine", 1, 1, 1, 0, "gnomesign", i++));
		objects.add(new GameObjectDef("Sign", "Giannes tree gnome cuisine", "WalkTo", "Examine", 1, 1, 1, 0, "gnomesign", i++));
		objects.add(new GameObjectDef("Sign", "Heckel funch's grocery store", "WalkTo", "Examine", 1, 1, 1, 0, "gnomesign", i++));
		objects.add(new GameObjectDef("Sign", "Hudo glenfad's grocery store", "WalkTo", "Examine", 1, 1, 1, 0, "gnomesign", i++));
		objects.add(new GameObjectDef("Sign", "Rometti's fashion outlet", "WalkTo", "Examine", 1, 1, 1, 0, "gnomesign", i++));
		objects.add(new GameObjectDef("Sign", "Tree gnome bank and rometti's fashion outlet", "WalkTo", "Examine", 1, 1, 1, 0, "gnomesign", i++));
		objects.add(new GameObjectDef("Sign", "Tree gnome local swamp", "WalkTo", "Examine", 1, 1, 1, 0, "gnomesign", i++));
		objects.add(new GameObjectDef("Sign", "Agility training course", "WalkTo", "Examine", 1, 1, 1, 0, "gnomesign", i++));
		objects.add(new GameObjectDef("Sign", "To the grand tree", "WalkTo", "Examine", 1, 1, 1, 0, "gnomesign", i++));
		objects.add(new GameObjectDef("Root", "To the grand tree", "search", "Examine", 1, 1, 1, 0, "treeroot1", i++));
		objects.add(new GameObjectDef("Root", "To the grand tree", "search", "Examine", 1, 1, 1, 0, "treeroot2", i++));
		objects.add(new GameObjectDef("Metal Gate", "The gate is closed", "Open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("Metal Gate", "The gate is open", "WalkTo", "close", 2, 1, 2, 0, "metalgateopen", i++));
		objects.add(new GameObjectDef("A farm cart", "It is blocking the entrance to the village", "Examine", "Search", 1, 2, 3, 0, "cart", i++));
		objects.add(new GameObjectDef("Ledge", "It looks rather thin", "balance on", "Examine", 0, 1, 1, 0, "straight_ledge", i++));
		objects.add(new GameObjectDef("Ledge", "It looks rather thin", "balance on", "Examine", 0, 1, 1, 0, "straight_ledge", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading upwards", "Climb-Up", "Examine", 1, 1, 1, 0, "ladder", i++));
		objects.add(new GameObjectDef("cage", "i don't like the look of that", "open", "Examine", 0, 1, 1, 0, "gnomecage", i++));
		objects.add(new GameObjectDef("glider", "i wonder if it flys", "fly", "Examine", 1, 1, 1, 0, "gnomeglider", i++));
		objects.add(new GameObjectDef("cupboard", "The cupboard is shut", "open", "Examine", 1, 1, 2, 0, "cupboard", i++));
		objects.add(new GameObjectDef("cupboard", "Perhaps I should search it", "Search", "close", 1, 1, 2, 0, "cupboardopen", i++));
		objects.add(new GameObjectDef("stairs", "These lead upstairs", "Go up", "Examine", 1, 2, 3, 0, "woodenstairs", i++));
		objects.add(new GameObjectDef("glider", "i wonder if it flys", "WalkTo", "Examine", 1, 1, 1, 0, "gnomeglidercrashed", i++));
		objects.add(new GameObjectDef("gate", "The gate is open", "WalkTo", "close", 1, 1, 2, 0, "woodengateopen", i++));
		objects.add(new GameObjectDef("gate", "The gate is closed", "open", "Examine", 1, 1, 2, 0, "woodengateclosed", i++));
		objects.add(new GameObjectDef("Altar of Zamorak", "An altar dedicated to Zamorak", "Worship", "Examine", 1, 2, 1, 0, "chaosaltar", i++));
		objects.add(new GameObjectDef("Gnome stronghold gate", "The gate is closed", "Open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("ropeswing", "A good place to train agility", "Swing", "Examine", 0, 1, 1, 0, "obstical_ropeswing", i++));
		objects.add(new GameObjectDef("ropeswing", "A good place to train agility", "Swing", "Examine", 0, 1, 1, 0, "obstical_ropeswing", i++));
		objects.add(new GameObjectDef("stairs", "These lead upstairs", "Go up", "Examine", 1, 2, 3, 0, "woodenstairs", i++));
		objects.add(new GameObjectDef("stairs", "These lead downstairs", "Go down", "Examine", 1, 2, 3, 0, "woodenstairsdown", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Pile of rubble", "What a mess", "climb", "Examine", 1, 2, 1, 0, "mudpile", i++));
		objects.add(new GameObjectDef("Stone stand", "On top our four indents from left to right", "WalkTo", "push down", 1, 1, 1, 0, "stonestand", i++));
		objects.add(new GameObjectDef("Watch tower", "A tree gnome construction", "climb up", "Examine", 1, 1, 1, 0, "gnomewatchtower lev1", i++));
		objects.add(new GameObjectDef("Pile of rubble", "What a mess", "climb", "Examine", 1, 2, 1, 0, "mudpiledown", i++));
		objects.add(new GameObjectDef("Root", "To the grand tree", "search", "Examine", 1, 1, 1, 0, "treeroot2", i++));
		objects.add(new GameObjectDef("Root", "To the grand tree", "push", "Examine", 1, 1, 1, 0, "treeroot2", i++));
		objects.add(new GameObjectDef("Root", "To the grand tree", "push", "Examine", 1, 1, 1, 0, "treeroot2", i++));
		objects.add(new GameObjectDef("Sign", "Home to the Head tree guardian", "WalkTo", "Examine", 1, 1, 1, 0, "gnomesign", i++));
		objects.add(new GameObjectDef("Hammock", "They've got to sleep somewhere", "lie in", "Examine", 1, 1, 2, 0, "gnomehamek", i++));
		objects.add(new GameObjectDef("Goal", "You're supposed to throw the ball here", "WalkTo", "Examine", 0, 1, 1, 0, "gnomegoal", i++));
		objects.add(new GameObjectDef("stone tile", "It looks as if it might move", "twist", "Examine", 1, 1, 1, 0, "stonedisc", i++));
		objects.add(new GameObjectDef("Chest", "You get a sense of dread from the chest", "WalkTo", "Examine", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "You get a sense of dread from the chest", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Watch tower", "A tree gnome construction", "WalkTo", "climb down", 0, 1, 1, 0, "gnomewatchtower lev2", i++));
		objects.add(new GameObjectDef("net", "A good place to train agility", "climb", "Examine", 1, 2, 1, 0, "obstical_net", i++));
		objects.add(new GameObjectDef("Watch tower", "A tree gnome construction", "climb up", "Examine", 1, 1, 1, 0, "gnomewatchtower lev1", i++));
		objects.add(new GameObjectDef("Watch tower", "A tree gnome construction", "climb down", "Examine", 1, 1, 1, 0, "gnomewatchtower lev2", i++));
		objects.add(new GameObjectDef("ropeswing", "A good place to train agility", "grab hold of", "Examine", 0, 1, 1, 0, "obstical_ropeswing", i++));
		objects.add(new GameObjectDef("Bumpy Dirt", "Some disturbed earth", "Look", "Search", 0, 1, 1, 0, "mudpatch", i++));
		objects.add(new GameObjectDef("pipe", "a dirty sewer pipe", "WalkTo", "Examine", 1, 1, 1, 0, "obstical_pipe", i++));
		objects.add(new GameObjectDef("net", "A good place to train agility", "climb", "Examine", 1, 2, 1, 0, "obstical_net", i++));
		objects.add(new GameObjectDef("pipe", "a dirty sewer pipe", "enter", "Examine", 1, 1, 1, 0, "obstical_pipe", i++));
		objects.add(new GameObjectDef("log", "It looks slippery", "balance on", "Examine", 0, 1, 1, 0, "log_balance1", i++));
		objects.add(new GameObjectDef("pipe", "a dirty sewer pipe", "enter", "Examine", 1, 1, 1, 0, "obstical_pipe", i++));
		objects.add(new GameObjectDef("pipe", "a dirty sewer pipe", "enter", "Examine", 0, 1, 1, 0, "obstical_pipe", i++));
		objects.add(new GameObjectDef("Handholds", "I wonder if I can climb up these", "climb", "Examine", 0, 1, 1, 0, "climbing_rocks", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading upwards", "Climb-Up", "Examine", 1, 1, 1, 0, "ladder", i++));
		objects.add(new GameObjectDef("gate", "The gate is closed", "open", "Examine", 2, 1, 2, 0, "woodengateclosed", i++));
		objects.add(new GameObjectDef("stronghold spirit Tree", "Ancestor of the spirit tree", "talk to", "Examine", 1, 1, 1, 0, "tree2", i++));
		objects.add(new GameObjectDef("Tree", "It has a branch ideal for tying ropes to", "WalkTo", "Examine", 1, 2, 2, 0, "tree_for_rope", i++));
		objects.add(new GameObjectDef("Tree", "I wonder who put that rope there", "swing on", "Examine", 1, 2, 2, 0, "tree_with_rope", i++));
		objects.add(new GameObjectDef("Tree", "I wonder who put that rope there", "swing on", "Examine", 1, 2, 2, 0, "tree_with_rope", i++));
		objects.add(new GameObjectDef("Spiked pit", "I don't want to go down there", "WalkTo", "Examine", 1, 2, 2, 0, "spikedpit-low", i++));
		objects.add(new GameObjectDef("Spiked pit", "I don't want to go down there", "WalkTo", "Examine", 1, 2, 2, 0, "spikedpit", i++));
		objects.add(new GameObjectDef("Cave", "I wonder what is inside...", "enter", "Examine", 1, 2, 2, 0, "caveentrance", i++));
		objects.add(new GameObjectDef("stone pebble", "Looks like a stone", "WalkTo", "Examine", 1, 1, 1, 0, "stonedisc", i++));
		objects.add(new GameObjectDef("Pile of rubble", "Rocks that have caved in", "WalkTo", "Examine", 1, 2, 1, 0, "mudpile", i++));
		objects.add(new GameObjectDef("Pile of rubble", "Rocks that have caved in", "WalkTo", "Search", 1, 2, 1, 0, "mudpile", i++));
		objects.add(new GameObjectDef("pipe", "I might be able to fit through this", "enter", "Examine", 1, 1, 1, 0, "obstical_pipe", i++));
		objects.add(new GameObjectDef("pipe", "2", "enter", "Examine", 1, 1, 1, 0, "obstical_pipe", i++));
		objects.add(new GameObjectDef("Stone", "Looks like a stone", "WalkTo", "Examine", 1, 1, 1, 0, "stonedisc", i++));
		objects.add(new GameObjectDef("Stone", "Looks like a stone", "Look Closer", "Investigate", 1, 1, 1, 0, "stonedisc", i++));
		objects.add(new GameObjectDef("ropeswing", "A good place to train agility", "Swing", "Examine", 0, 1, 1, 0, "obstical_ropeswing", i++));
		objects.add(new GameObjectDef("log", "It looks slippery", "balance on", "Examine", 0, 1, 1, 0, "log_balance1", i++));
		objects.add(new GameObjectDef("net", "A good place to train agility", "Climb Up", "Examine", 1, 2, 1, 0, "obstical_net", i++));
		objects.add(new GameObjectDef("Ledge", "It looks rather thin", "balance on", "Examine", 0, 1, 1, 0, "straight_ledge", i++));
		objects.add(new GameObjectDef("Handholds", "I wonder if I can climb up these", "climb", "Examine", 0, 1, 1, 0, "climbing_rocks", i++));
		objects.add(new GameObjectDef("log", "It looks slippery", "balance on", "Examine", 0, 1, 1, 0, "log_balance1", i++));
		objects.add(new GameObjectDef("log", "It looks slippery", "balance on", "Examine", 0, 1, 1, 0, "log_balance1", i++));
		objects.add(new GameObjectDef("Rotten Gallows", "A human corpse hangs from the noose", "Look", "Search", 1, 2, 2, 0, "gallows", i++));
		objects.add(new GameObjectDef("Pile of rubble", "Rocks that have caved in", "WalkTo", "Search", 1, 2, 1, 0, "mudpile", i++));
		objects.add(new GameObjectDef("ropeswing", "I wonder what's over here", "Swing", "Examine", 0, 1, 1, 0, "obstical_ropeswing", i++));
		objects.add(new GameObjectDef("ropeswing", "I wonder what's over here", "Swing", "Examine", 0, 1, 1, 0, "obstical_ropeswing", i++));
		objects.add(new GameObjectDef("ocks", "A moss covered rock", "balance", "Examine", 1, 1, 1, 0, "mossyrock", i++));
		objects.add(new GameObjectDef("Tree", "This tree doesn't look too healthy", "WalkTo", "balance", 1, 1, 1, 0, "deadtree1", i++));
		objects.add(new GameObjectDef("Well stacked rocks", "Rocks that have been stacked at regular intervals", "Investigate", "Search", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Tomb Dolmen", "An ancient construct for displaying the bones of the deceased", "Look", "Search", 1, 2, 2, 0, "dolmen", i++));
		objects.add(new GameObjectDef("Handholds", "I wonder if I can climb up these", "Climb", "Examine", 0, 1, 1, 0, "climbing_rocks", i++));
		objects.add(new GameObjectDef("Bridge Blockade", "A crudely constructed fence to stop you going further", "Investigate", "Jump", 1, 1, 1, 0, "gnomefence", i++));
		objects.add(new GameObjectDef("Log Bridge", "A slippery log that is a make-do bridge", "Balance On", "Examine", 0, 1, 1, 0, "log_balance2", i++));
		objects.add(new GameObjectDef("Handholds", "I wonder if I can climb up these", "climb", "Examine", 0, 1, 1, 0, "climbing_rocks", i++));
		objects.add(new GameObjectDef("Tree", "they look fun to swing on", "Swing on", "Examine", 1, 2, 2, 0, "tree_with_vines", i++));
		objects.add(new GameObjectDef("Tree", "they look fun to swing on", "Swing on", "Examine", 1, 2, 2, 0, "tree_with_vines", i++));
		objects.add(new GameObjectDef("Wet rocks", "A rocky outcrop", "Look", "Search", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Smashed table", "This table has seen better days", "Examine", "Craft", 1, 1, 1, 0, "smashedtable", i++));
		objects.add(new GameObjectDef("Crude Raft", "A crudely constructed raft", "Disembark", "Examine", 0, 1, 1, 96, "lograft", i++));
		objects.add(new GameObjectDef("Daconia rock", "Piles of daconia rock", "Mine", "Prospect", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("statue", "A statue to mark Taie Bwo Wannai sacred grounds", "WalkTo", "Examine", 1, 1, 1, 0, "tribalstature", i++));
		objects.add(new GameObjectDef("Stepping stones", "A rocky outcrop", "Balance", "Jump onto", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("gate", "The gate is closed", "open", "Examine", 2, 1, 2, 0, "woodengateclosed", i++));
		objects.add(new GameObjectDef("gate", "Enter to balance into an agility area", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("gate", "Enter to balance into an agility area", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("pipe", "It looks a tight squeeze", "enter", "Examine", 1, 1, 1, 0, "obstical_pipe", i++));
		objects.add(new GameObjectDef("ropeswing", "A good place to train agility", "Swing", "Examine", 0, 1, 1, 0, "obstical_ropeswing", i++));
		objects.add(new GameObjectDef("Stone", "Looks like a stone", "balance on", "Examine", 1, 1, 1, 0, "stonedisc", i++));
		objects.add(new GameObjectDef("Ledge", "It doesn't look stable", "balance on", "Examine", 0, 1, 1, 0, "straight_ledge", i++));
		objects.add(new GameObjectDef("Vine", "A creepy creeper", "climb up", "Examine", 0, 1, 1, 0, "vinecorner", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "WalkTo", "Climb", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Wooden Gate", "The gate is open", "Close", "Close", 2, 1, 2, 0, "woodengateopen", i++));
		objects.add(new GameObjectDef("Wooden Gate", "The gate is closed", "Open", "Examine", 2, 1, 2, 0, "woodengateclosed", i++));
		objects.add(new GameObjectDef("Stone bridge", "An ancient stone construction", "WalkTo", "Examine", 0, 1, 1, 0, "bridge section 1", i++));
		objects.add(new GameObjectDef("Stone bridge", "An ancient stone construction", "WalkTo", "Examine", 0, 1, 1, 0, "bridge section 2", i++));
		objects.add(new GameObjectDef("Stone bridge", "An ancient stone construction", "WalkTo", "Examine", 0, 1, 1, 0, "bridge section 3", i++));
		objects.add(new GameObjectDef("Stone bridge", "An ancient stone construction", "WalkTo", "Examine", 0, 1, 1, 0, "cave bridge support", i++));
		objects.add(new GameObjectDef("Stone platform", "An ancient stone construction", "WalkTo", "Examine", 0, 1, 1, 0, "cave platform small", i++));
		objects.add(new GameObjectDef("fence", "it doesn't look too strong", "WalkTo", "Examine", 1, 1, 1, 0, "gnomefence2", i++));
		objects.add(new GameObjectDef("Rocks", "A rocky outcrop", "Climb", "Climb", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "cross", "Examine", 1, 3, 1, 0, "bridge section collapsed", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "cross", "Examine", 1, 3, 1, 0, "bridge section collapsed2", i++));
		objects.add(new GameObjectDef("Gate of Iban", "It doesn't look very inviting", "open", "Examine", 1, 3, 1, 0, "rams skull door", i++));
		objects.add(new GameObjectDef("Wooden Door", "It doesn't look very inviting", "cross", "Examine", 1, 3, 1, 0, "rams skull dooropen", i++));
		objects.add(new GameObjectDef("Tomb Dolmen", "An ancient construct for displaying the bones of the deceased", "Look", "Search", 1, 2, 2, 0, "dolmen", i++));
		objects.add(new GameObjectDef("Cave entrance", "It doesn't look very inviting", "enter", "Examine", 1, 3, 1, 0, "caveentrance2", i++));
		objects.add(new GameObjectDef("Old bridge", "That's been there a while", "WalkTo", "Examine", 1, 3, 1, 0, "cave old bridge", i++));
		objects.add(new GameObjectDef("Old bridge", "That's been there a while", "cross", "Examine", 1, 3, 1, 0, "cave old bridgedown", i++));
		objects.add(new GameObjectDef("Crumbled rock", "climb up to above ground", "climb", "Examine", 1, 2, 1, 0, "mudpile", i++));
		objects.add(new GameObjectDef("stalagmite", "Formed over thousands of years", "WalkTo", "Examine", 1, 1, 1, 0, "cave large stagamite", i++));
		objects.add(new GameObjectDef("stalagmite", "Formed over thousands of years", "WalkTo", "Examine", 1, 1, 1, 0, "cave small stagamite", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Ledge", "I might be able to climb that", "climb up", "Examine", 1, 1, 1, 0, "cave ledge", i++));
		objects.add(new GameObjectDef("Lever", "Seems to be some sort of winch", "pull", "Examine", 1, 1, 1, 0, "cave lever", i++));
		objects.add(new GameObjectDef("stalactite", "Formed over thousands of years", "WalkTo", "Examine", 0, 1, 1, 0, "cave large stagatite", i++));
		objects.add(new GameObjectDef("stalactite", "Formed over thousands of years", "WalkTo", "Examine", 0, 1, 1, 0, "cave small stagatite", i++));
		objects.add(new GameObjectDef("stalactite", "Formed over thousands of years", "climb", "Examine", 0, 1, 1, 0, "cave extra large stagatite", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Ledge", "I might be able to climb that", "climb up", "Examine", 1, 1, 1, 0, "cave ledge", i++));
		objects.add(new GameObjectDef("Ledge", "I might be able to climb that", "climb up", "Examine", 1, 1, 1, 0, "cave ledge", i++));
		objects.add(new GameObjectDef("Ledge", "I might be able to climb that", "climb up", "Examine", 1, 1, 1, 0, "cave ledge", i++));
		objects.add(new GameObjectDef("Ledge", "I might be able to climb that", "climb up", "Examine", 1, 1, 1, 0, "cave ledge", i++));
		objects.add(new GameObjectDef("Swamp", "That smells horrid", "step over", "Examine", 1, 1, 1, 0, "cave swampbubbles", i++));
		objects.add(new GameObjectDef("Swamp", "That smells horrid", "WalkTo", "Examine", 1, 1, 1, 0, "cave swampbubbles", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Pile of mud", "Mud caved in from above", "climb", "Examine", 1, 2, 1, 0, "mudpile", i++));
		objects.add(new GameObjectDef("Travel Cart", "A sturdy cart for travelling in", "Board", "Look", 1, 2, 3, 0, "cart", i++));
		objects.add(new GameObjectDef("Travel Cart", "A sturdy cart for travelling in", "Board", "Look", 1, 2, 3, 0, "cart", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "mine", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("stalactite", "Formed over thousands of years", "WalkTo", "Examine", 0, 1, 1, 0, "cave large stagatite", i++));
		objects.add(new GameObjectDef("Rocks", "You should be able to move these", "clear", "Examine", 1, 2, 2, 0, "cave rocktrap1", i++));
		objects.add(new GameObjectDef("Rocks", "You should be able to move these", "WalkTo", "Examine", 1, 2, 2, 0, "cave rocktrap1a", i++));
		objects.add(new GameObjectDef("Rocks", "You should be able to move these", "WalkTo", "Examine", 1, 1, 3, 0, "cave swamprocks", i++));
		objects.add(new GameObjectDef("sign", "The Paramaya Hostel", "WalkTo", "Examine", 0, 1, 1, 0, "shopsign", i++));
		objects.add(new GameObjectDef("Ladder", "A ladder that leads to the dormitory - a ticket is needed", "Climb-Up", "Examine", 1, 1, 1, 0, "ladder", i++));
		objects.add(new GameObjectDef("Grill", "They looks suspicious", "walk over", "Examine", 1, 1, 1, 0, "cave grilltrap", i++));
		objects.add(new GameObjectDef("Spiked pit", "They looks suspicious", "walk over", "Examine", 1, 1, 1, 0, "cave grilltrapa", i++));
		objects.add(new GameObjectDef("signpost", "To the Furnace", "WalkTo", "Examine", 1, 1, 1, 0, "signpost", i++));
		objects.add(new GameObjectDef("Ship", "A sea faring ship called 'Lady Of The Waves'", "board", "Examine", 0, 5, 3, 0, "Shipfront", i++));
		objects.add(new GameObjectDef("Ship", "A sea faring ship called 'Lady Of The Waves'", "board", "Examine", 0, 5, 3, 0, "Shipback", i++));
		objects.add(new GameObjectDef("Grill", "They looks suspicious", "walk over", "Examine", 1, 1, 1, 0, "cave grilltrap", i++));
		objects.add(new GameObjectDef("sacks", "Yep they're sacks", "WalkTo", "Search", 1, 1, 1, 0, "sacks", i++));
		objects.add(new GameObjectDef("Zamorakian Temple", "Scary!", "WalkTo", "Examine", 0, 1, 1, 0, "cave temple", i++));
		objects.add(new GameObjectDef("Grill", "They looks suspicious", "walk over", "Examine", 1, 1, 1, 0, "cave grilltrap", i++));
		objects.add(new GameObjectDef("Grill", "They looks suspicious", "walk over", "Examine", 1, 1, 1, 0, "cave grilltrap", i++));
		objects.add(new GameObjectDef("Grill", "They looks suspicious", "walk over", "Examine", 1, 1, 1, 0, "cave grilltrap", i++));
		objects.add(new GameObjectDef("Grill", "They looks suspicious", "walk over", "Examine", 1, 1, 1, 0, "cave grilltrap", i++));
		objects.add(new GameObjectDef("Grill", "They looks suspicious", "walk over", "Examine", 1, 1, 1, 0, "cave grilltrap", i++));
		objects.add(new GameObjectDef("Grill", "They looks suspicious", "walk over", "Examine", 1, 1, 1, 0, "cave grilltrap", i++));
		objects.add(new GameObjectDef("Grill", "They looks suspicious", "walk over", "Examine", 1, 1, 1, 0, "cave grilltrap", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "walk here", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "walk here", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Tomb Doors", "Ornately carved wooden doors depicting skeletal warriors", "Open", "Search", 2, 1, 2, 0, "doubledoorsclosed", i++));
		objects.add(new GameObjectDef("Swamp", "That smells horrid", "step over", "Examine", 1, 1, 1, 0, "cave swampbubbles", i++));
		objects.add(new GameObjectDef("Rocks", "You should be able to move these", "clear", "Examine", 1, 2, 1, 0, "cave rocktrap1", i++));
		objects.add(new GameObjectDef("Rocks", "You should be able to move these", "clear", "Examine", 1, 2, 1, 0, "cave rocktrap1", i++));
		objects.add(new GameObjectDef("stalactite", "Formed over thousands of years", "WalkTo", "Examine", 0, 1, 1, 0, "cave extra large stagatite", i++));
		objects.add(new GameObjectDef("stalactite", "Formed over thousands of years", "WalkTo", "Examine", 0, 1, 1, 0, "cave extra large stagatite", i++));
		objects.add(new GameObjectDef("Spiked pit", "They looks suspicious", "walk over", "Examine", 1, 1, 1, 0, "cave grilltrapa up", i++));
		objects.add(new GameObjectDef("Lever", "Seems to be some sort of winch", "pull", "Examine", 1, 1, 1, 0, "cave lever", i++));
		objects.add(new GameObjectDef("Cage", "Seems to be mechanical ", "WalkTo", "Examine", 1, 1, 1, 0, "cave grillcage", i++));
		objects.add(new GameObjectDef("Cage", "Seems to be mechanical ", "WalkTo", "Examine", 1, 1, 1, 0, "cave grillcageup", i++));
		objects.add(new GameObjectDef("Rocks", "More rocks!", "step over", "search for traps", 1, 1, 1, 0, "cave speartrap", i++));
		objects.add(new GameObjectDef("Spear trap", "Ouch!", "WalkTo", "Examine", 1, 1, 1, 0, "cave speartrapa", i++));
		objects.add(new GameObjectDef("Rocks", "More rocks!", "step over", "search", 1, 1, 1, 0, "cave speartrap", i++));
		objects.add(new GameObjectDef("Rocks", "More rocks!", "step over", "search", 1, 1, 1, 0, "cave speartrap", i++));
		objects.add(new GameObjectDef("Rocks", "More rocks!", "step over", "search", 1, 1, 1, 0, "cave speartrap", i++));
		objects.add(new GameObjectDef("Rocks", "More rocks!", "step over", "search", 1, 1, 1, 0, "cave speartrap", i++));
		objects.add(new GameObjectDef("Rocks", "More rocks!", "step over", "search", 1, 1, 1, 0, "cave speartrap", i++));
		objects.add(new GameObjectDef("Rocks", "More rocks!", "step over", "search", 1, 1, 1, 0, "cave speartrap", i++));
		objects.add(new GameObjectDef("Ledge", "I might be able to climb that", "drop down", "Examine", 1, 1, 1, 0, "cave ledge", i++));
		objects.add(new GameObjectDef("Furnace", "Charred bones are slowly burning inside", "WalkTo", "Examine", 1, 1, 1, 0, "cave furnace", i++));
		objects.add(new GameObjectDef("Well", "The remains of a warrior slump over the strange construction", "drop down", "Examine", 1, 1, 1, 0, "cave well", i++));
		objects.add(new GameObjectDef("Passage", "A strange metal grill covers the passage", "walk down", "Examine", 1, 2, 1, 0, "cave tubetrap", i++));
		objects.add(new GameObjectDef("Passage", "The passage way has swung down to a vertical position", "climb up", "Examine", 1, 2, 1, 0, "cave tubetrapa", i++));
		objects.add(new GameObjectDef("Passage", "The passage way has swung down to a vertical position", "climb up rope", "Examine", 1, 2, 1, 0, "cave tubetrapa rope", i++));
		objects.add(new GameObjectDef("stalagmite", "Formed over thousands of years", "search", "Examine", 1, 1, 1, 0, "cave large stagamite", i++));
		objects.add(new GameObjectDef("Rocks", "You should be able to move these", "clear", "search", 1, 2, 2, 0, "cave rocktrap1", i++));
		objects.add(new GameObjectDef("Rocks", "You should be able to move these", "clear", "search", 1, 2, 2, 0, "cave rocktrap1", i++));
		objects.add(new GameObjectDef("Rocks", "You should be able to move these", "clear", "search", 1, 2, 2, 0, "cave rocktrap1", i++));
		objects.add(new GameObjectDef("Rocks", "You should be able to move these", "clear", "search", 1, 2, 2, 0, "cave rocktrap1", i++));
		objects.add(new GameObjectDef("Rocks", "You should be able to move these", "clear", "search", 1, 2, 2, 0, "cave rocktrap1", i++));
		objects.add(new GameObjectDef("Rocks", "You should be able to move these", "clear", "search", 1, 2, 2, 0, "cave rocktrap1", i++));
		objects.add(new GameObjectDef("Passage", "Looks suspicous!", "Walk here", "search", 1, 1, 1, 0, "cave snaptrap", i++));
		objects.add(new GameObjectDef("snap trap", "aaaarghh", "WalkTo", "Examine", 1, 1, 1, 0, "cave snaptrapa", i++));
		objects.add(new GameObjectDef("Wooden planks", "You can walk across these", "WalkTo", "Examine", 1, 1, 1, 0, "cave planks", i++));
		objects.add(new GameObjectDef("Passage", "Looks suspicous!", "Walk here", "search", 1, 1, 1, 0, "cave snaptrap", i++));
		objects.add(new GameObjectDef("Passage", "Looks suspicous!", "Walk here", "search", 1, 1, 1, 0, "cave snaptrap", i++));
		objects.add(new GameObjectDef("Flames of zamorak", "Careful", "search", "Examine", 1, 2, 2, 0, "cave bloodwell", i++));
		objects.add(new GameObjectDef("Platform", "An ancient construction", "WalkTo", "Examine", 1, 1, 1, 0, "cave platform verysmall", i++));
		objects.add(new GameObjectDef("Rock", "Scripture has been carved into the rock", "read", "Examine", 1, 1, 1, 0, "cave carvings", i++));
		objects.add(new GameObjectDef("Rock", "Scripture has been carved into the rock", "read", "Examine", 1, 1, 1, 0, "cave carvings", i++));
		objects.add(new GameObjectDef("Rock", "Scripture has been carved into the rock", "read", "Examine", 1, 1, 1, 0, "cave carvings", i++));
		objects.add(new GameObjectDef("Rock", "Scripture has been carved into the rock", "read", "Examine", 1, 1, 1, 0, "cave carvings", i++));
		objects.add(new GameObjectDef("wall grill", "It seems to filter the rotten air through the caverns", "climb up", "Examine", 1, 1, 1, 0, "cave wallgrill", i++));
		objects.add(new GameObjectDef("Ledge", "I might be able to make to the other side", "jump off", "climb up", 1, 1, 1, 0, "cave ledge", i++));
		objects.add(new GameObjectDef("wall grill", "It seems to filter the rotten air through the caverns", "climb up", "Examine", 1, 1, 1, 0, "cave wallgrill", i++));
		objects.add(new GameObjectDef("Dug up soil", "A freshly dug pile of mud", "search", "Examine", 0, 1, 1, 0, "mudpatch", i++));
		objects.add(new GameObjectDef("Dug up soil", "A freshly dug pile of mud", "search", "Examine", 0, 1, 1, 0, "mudpatch", i++));
		objects.add(new GameObjectDef("Pile of mud", "Mud caved in from above", "climb", "Examine", 1, 2, 1, 0, "mudpile", i++));
		objects.add(new GameObjectDef("stalagmite", "Formed over thousands of years", "WalkTo", "Examine", 0, 1, 1, 0, "cave small stagamite", i++));
		objects.add(new GameObjectDef("Pile of mud", "Mud and rocks piled up", "climb", "Examine", 1, 2, 1, 0, "mudpile", i++));
		objects.add(new GameObjectDef("Pile of mud", "Mud and rocks piled up", "climb", "Examine", 1, 2, 1, 0, "mudpile", i++));
		objects.add(new GameObjectDef("Pile of mud", "Mud and rocks piled up", "climb", "Examine", 1, 2, 1, 0, "mudpile", i++));
		objects.add(new GameObjectDef("Pile of mud", "Mud and rocks piled up", "climb", "Examine", 1, 2, 1, 0, "mudpile", i++));
		objects.add(new GameObjectDef("Pile of mud", "Mud and rocks piled up", "climb", "Examine", 1, 2, 1, 0, "mudpile", i++));
		objects.add(new GameObjectDef("Spiked pit", "I don't want to go down there", "WalkTo", "Examine", 0, 1, 1, 0, "spikedpit", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Ledge", "I might be able to climb that", "climb up", "Examine", 1, 1, 1, 0, "cave ledge", i++));
		objects.add(new GameObjectDef("Ledge", "I might be able to climb that", "climb up", "Examine", 1, 1, 1, 0, "cave ledge", i++));
		objects.add(new GameObjectDef("Ledge", "I might be able to climb that", "climb up", "Examine", 1, 1, 1, 0, "cave ledge", i++));
		objects.add(new GameObjectDef("Ledge", "I might be able to climb that", "climb up", "Examine", 1, 1, 1, 0, "cave ledge", i++));
		objects.add(new GameObjectDef("Ledge", "I might be able to climb that", "climb up", "Examine", 1, 1, 1, 0, "cave ledge", i++));
		objects.add(new GameObjectDef("Ledge", "I might be able to climb that", "climb up", "Examine", 1, 1, 1, 0, "cave ledge", i++));
		objects.add(new GameObjectDef("Boulder", "Could be dangerous!", "WalkTo", "Examine", 1, 2, 2, 0, "cave bolder", i++));
		objects.add(new GameObjectDef("crate", "Someone or something has been here before us", "WalkTo", "Search", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("Door", "Spooky!", "open", "Examine", 1, 1, 1, 0, "cave templedoor", i++));
		objects.add(new GameObjectDef("Platform", "An ancient construction", "WalkTo", "Examine", 1, 1, 1, 0, "cave platform small2", i++));
		objects.add(new GameObjectDef("Cage remains", "Poor unicorn!", "WalkTo", "Search", 1, 1, 1, 0, "cave smashedcage", i++));
		objects.add(new GameObjectDef("Ledge", "I might be able to climb that", "climb up", "Examine", 1, 1, 1, 0, "cave ledge", i++));
		objects.add(new GameObjectDef("Passage", "Looks suspicous!", "Walk here", "Examine", 1, 1, 1, 0, "cave snaptrap", i++));
		objects.add(new GameObjectDef("Passage", "Looks suspicous!", "Walk here", "Examine", 1, 1, 1, 0, "cave snaptrap", i++));
		objects.add(new GameObjectDef("Gate of Zamorak", "It doesn't look very inviting", "open", "Examine", 1, 3, 1, 0, "rams skull door", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Bridge support", "An ancient construction", "WalkTo", "Examine", 1, 1, 1, 0, "cave bridge supportbase", i++));
		objects.add(new GameObjectDef("Tomb of Iban", "A clay shrine to lord iban", "Open", "Examine", 1, 1, 2, 96, "hazeeltomb", i++));
		objects.add(new GameObjectDef("Claws of Iban", "claws of iban", "WalkTo", "Examine", 1, 1, 1, 96, "clawsofiban", i++));
		objects.add(new GameObjectDef("barrel", "Its stinks of alcohol", "empty", "Examine", 1, 1, 1, 0, "barrel", i++));
		objects.add(new GameObjectDef("Rock", "Scripture has been carved into the rock", "read", "Examine", 1, 1, 1, 0, "cave carvings", i++));
		objects.add(new GameObjectDef("Rocks", "More rocks", "step over", "search for traps", 1, 1, 1, 0, "cave speartrap", i++));
		objects.add(new GameObjectDef("Rocks", "More rocks", "step over", "search for traps", 1, 1, 1, 0, "cave speartrap", i++));
		objects.add(new GameObjectDef("Swamp", "That smells horrid", "WalkTo", "Examine", 1, 1, 1, 0, "cave swampbubbles", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "Search", "Examine", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Stone bridge", "An ancient stone construction", "WalkTo", "Examine", 0, 1, 1, 0, "bridge section corner", i++));
		objects.add(new GameObjectDef("cage", "That's no way to live", "search", "Examine", 0, 1, 1, 0, "gnomecage", i++));
		objects.add(new GameObjectDef("cage", "That's no way to live", "search", "Examine", 0, 1, 1, 0, "gnomecage", i++));
		objects.add(new GameObjectDef("Stone steps", "They lead into the darkness", "walk down", "Examine", 0, 1, 1, 0, "cave bridge stairs", i++));
		objects.add(new GameObjectDef("Pile of mud", "Mud and rocks piled up", "climb", "Examine", 1, 2, 1, 0, "mudpile", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "cross", "Examine", 1, 1, 3, 0, "bridge section collapsed", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "jump over", "Examine", 1, 1, 3, 0, "bridge section collapsed2", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "cross", "Examine", 1, 1, 3, 0, "bridge section collapsed", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "jump over", "Examine", 1, 1, 3, 0, "bridge section collapsed2", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "cross", "Examine", 1, 1, 3, 0, "bridge section collapsed", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "jump over", "Examine", 1, 1, 3, 0, "bridge section collapsed2", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "cross", "Examine", 1, 1, 3, 0, "bridge section collapsed", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "jump over", "Examine", 1, 1, 3, 0, "bridge section collapsed2", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "cross", "Examine", 1, 1, 3, 0, "bridge section collapsed", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "jump over", "Examine", 1, 1, 3, 0, "bridge section collapsed2", i++));//900
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "cross", "Examine", 1, 1, 3, 0, "bridge section collapsed", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "jump over", "Examine", 1, 1, 3, 0, "bridge section collapsed2", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "cross", "Examine", 1, 1, 3, 0, "bridge section collapsed", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "jump over", "Examine", 1, 1, 3, 0, "bridge section collapsed2", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "cross", "Examine", 1, 1, 3, 0, "bridge section collapsed", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "jump over", "Examine", 1, 1, 3, 0, "bridge section collapsed2", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "cross", "Examine", 1, 1, 3, 0, "bridge section collapsed", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "jump over", "Examine", 1, 1, 3, 0, "bridge section collapsed2", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "cross", "Examine", 1, 1, 3, 0, "bridge section collapsed", i++));
		objects.add(new GameObjectDef("Stone bridge", "The bridge has partly collapsed", "jump over", "Examine", 1, 1, 3, 0, "bridge section collapsed2", i++));//910
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Pit of the Damned", "The son of zamoracks alter...", "WalkTo", "Examine", 1, 1, 1, 0, "cave temple alter", i++));
		objects.add(new GameObjectDef("Open Door", "Spooky!", "open", "Examine", 1, 1, 1, 0, "cave templedooropen", i++));
		objects.add(new GameObjectDef("signpost", "Observatory reception", "WalkTo", "Examine", 1, 1, 1, 0, "signpost", i++));
		objects.add(new GameObjectDef("Stone Gate", "A mystical looking object", "Go through", "Look", 1, 2, 2, 0, "henge", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps there is something inside", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Zodiac", "A map of the twelve signs of the zodiac", "WalkTo", "Examine", 0, 3, 3, 0, "zodiac", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading upwards", "Climb-Up", "Examine", 1, 1, 1, 0, "ladder", i++));//920
		objects.add(new GameObjectDef("Stone steps", "They lead into the darkness", "walk down", "Examine", 0, 1, 1, 0, "cave bridge stairs", i++));
		objects.add(new GameObjectDef("Rock", "Scripture has been carved into the rock", "read", "Examine", 1, 1, 1, 0, "cave carvings", i++));
		objects.add(new GameObjectDef("Rock", "Scripture has been carved into the rock", "read", "Examine", 1, 1, 1, 0, "cave carvings", i++));
		objects.add(new GameObjectDef("Rock", "Scripture has been carved into the rock", "read", "Examine", 1, 1, 1, 0, "cave carvings", i++));
		objects.add(new GameObjectDef("Telescope", "A device for viewing the heavens", "Use", "Examine", 1, 1, 1, 0, "telescope", i++));
		objects.add(new GameObjectDef("Gate", "The entrance to the dungeon jail", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("sacks", "These sacks feels lumpy!", "Search", "Examine", 1, 1, 1, 0, "sacks", i++));
		objects.add(new GameObjectDef("Ladder", "the ladder goes down into a dark area", "Climb-Down", "Examine", 1, 1, 1, 0, "ladderdown", i++));
		objects.add(new GameObjectDef("Chest", "All these chests look the same!", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Chest", "All these chests look the same!", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));//930
		objects.add(new GameObjectDef("Bookcase", "A very roughly constructed bookcase.", "WalkTo", "Search", 1, 1, 2, 0, "bookcase", i++));
		objects.add(new GameObjectDef("Iron Gate", "A well wrought iron gate - it's locked.", "Open", "Search", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("Ladder", "the ladder down to the cavern", "Climb-Down", "Examine", 1, 1, 1, 0, "ladderdown", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps there is something inside", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "All these chests look the same!", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps there is something inside", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "All these chests look the same!", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Rockslide", "A pile of rocks blocks your path", "WalkTo", "Examine", 1, 1, 1, 0, "rock3", i++));
		objects.add(new GameObjectDef("Altar of Zamorak", "An altar dedicated to Zamorak", "Worship", "Examine", 1, 2, 1, 0, "chaosaltar", i++));
		objects.add(new GameObjectDef("column", "Formed over thousands of years", "WalkTo", "Examine", 1, 1, 1, 0, "cave pillar", i++));//940
		objects.add(new GameObjectDef("Grave of Scorpius", "Here lies Scorpius: dread follower of zamorak", "Read", "Examine", 1, 1, 3, 0, "gravestone1", i++));
		objects.add(new GameObjectDef("Bank Chest", "Allows you to access your bank.", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("dwarf multicannon", "fires metal balls", "fire", "pick up", 0, 1, 1, 0, "dwarf multicannon", i++));
		objects.add(new GameObjectDef("Disturbed sand", "Footprints in the sand show signs of a struggle", "Look", "Search", 0, 1, 1, 0, "sandyfootsteps", i++));
		objects.add(new GameObjectDef("Disturbed sand", "Footprints in the sand show signs of a struggle", "Look", "Search", 0, 1, 1, 0, "sandyfootsteps", i++));
		objects.add(new GameObjectDef("dwarf multicannon base", "bang", "pick up", "Examine", 0, 1, 1, 0, "dwarf multicannon part1", i++));
		objects.add(new GameObjectDef("dwarf multicannon stand", "bang", "pick up", "Examine", 0, 1, 1, 0, "dwarf multicannon part2", i++));
		objects.add(new GameObjectDef("dwarf multicannon barrels", "bang", "pick up", "Examine", 0, 1, 1, 0, "dwarf multicannon part3", i++));
		objects.add(new GameObjectDef("Cave", "I wonder what's inside!", "enter", "Examine", 1, 1, 1, 0, "small caveentrance2", i++));
		objects.add(new GameObjectDef("Cave", "I wonder what's inside!", "enter", "Examine", 1, 1, 1, 0, "small caveentrance2", i++));//950
		objects.add(new GameObjectDef("fence", "These bridges seem hastily put up", "WalkTo", "Examine", 0, 1, 1, 0, "gnomefence", i++));
		objects.add(new GameObjectDef("signpost", "a signpost", "read", "Examine", 1, 1, 1, 0, "signpost2", i++));
		objects.add(new GameObjectDef("Rocks", "I wonder if I can climb up these", "climb", "Examine", 0, 1, 1, 0, "brownclimbingrocks", i++));
		objects.add(new GameObjectDef("Rocks", "I wonder if I can climb up these", "climb", "Examine", 0, 1, 1, 0, "brownclimbingrocks", i++));
		objects.add(new GameObjectDef("Cave entrance", "A noxious smell emanates from the cave...", "enter", "Examine", 1, 3, 1, 0, "caveentrance2", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "I wouldn't like to think where the owner is now", "Search", "Close", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Wooden Doors", "Large oak doors constantly watched by guards", "Open", "Watch", 2, 1, 2, 0, "hillsidedoor", i++));
		objects.add(new GameObjectDef("Pedestal", "something fits on here", "WalkTo", "Examine", 1, 1, 1, 96, "stonestand", i++));
		objects.add(new GameObjectDef("bush", "A leafy bush", "Search", "Examine", 1, 1, 1, 0, "bush1", i++));//960
		objects.add(new GameObjectDef("bush", "A leafy bush", "Search", "Examine", 1, 1, 1, 0, "bush1", i++));
		objects.add(new GameObjectDef("Standard", "A standard with a human skull on it", "WalkTo", "Examine", 1, 1, 1, 0, "ogre standard", i++));
		objects.add(new GameObjectDef("Mining Cave", "A gaping hole that leads to another section of the mine", "enter", "Examine", 1, 3, 1, 0, "caveentrance2", i++));
		objects.add(new GameObjectDef("Mining Cave", "A gaping hole that leads to another section of the mine", "enter", "Examine", 1, 3, 1, 0, "caveentrance2", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "WalkTo", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Lift", "To brings mined rocks to the surface", "Operate", "Examine", 1, 1, 2, 0, "liftwinch", i++));
		objects.add(new GameObjectDef("Mining Barrel", "For loading up mined stone from below ground", "WalkTo", "Search", 1, 1, 1, 0, "barrel", i++));
		objects.add(new GameObjectDef("Hole", "I wonder where this leads...", "enter", "Examine", 1, 1, 1, 0, "hole", i++));
		objects.add(new GameObjectDef("Hole", "I wonder where this leads...", "enter", "Examine", 1, 1, 1, 0, "hole", i++));
		objects.add(new GameObjectDef("Cave", "I wonder what's inside!", "enter", "Examine", 1, 1, 1, 0, "small caveentrance2", i++));//970
		objects.add(new GameObjectDef("Cave", "I wonder what's inside!", "enter", "Examine", 1, 1, 1, 0, "small caveentrance2", i++));
		objects.add(new GameObjectDef("Cave", "I wonder what's inside!", "enter", "Examine", 1, 1, 1, 0, "small caveentrance2", i++));
		objects.add(new GameObjectDef("Counter", "An ogre is selling items here", "Steal from", "Examine", 1, 1, 1, 0, "rockcounter", i++));
		objects.add(new GameObjectDef("Track", "Train track", "Look", "Examine", 1, 2, 2, 0, "trackcurve", i++));
		objects.add(new GameObjectDef("Cave", "I wonder what's inside!", "enter", "Examine", 1, 1, 1, 0, "small caveentrance2", i++));
		objects.add(new GameObjectDef("Mine Cart", "A heavily constructed and often used mining cart.", "Look", "Search", 1, 1, 1, 0, "minecart", i++));
		objects.add(new GameObjectDef("Lift Platform", "A wooden lift that is operated from the surface.", "Use", "Search", 1, 1, 1, 0, "liftbed", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Close", "Examine", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Watch tower", "Constructed by the dwarven black guard", "WalkTo", "Examine", 0, 2, 2, 0, "watchtower", i++));//980
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading upwards", "Climb-Up", "Examine", 1, 1, 1, 0, "ladder", i++));
		objects.add(new GameObjectDef("Cave entrance", "I wonder what is inside...", "enter", "Examine", 1, 2, 2, 0, "caveentrance", i++));
		objects.add(new GameObjectDef("Pile of mud", "Mud caved in from above", "climb", "Examine", 1, 2, 1, 0, "mudpile", i++));
		objects.add(new GameObjectDef("Cave", "I wonder what's inside!", "enter", "Examine", 1, 1, 1, 0, "small caveentrance2", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading downwards", "Climb-Down", "Examine", 1, 1, 1, 0, "ladderdown", i++));
		objects.add(new GameObjectDef("crate", "A crate", "search", "Examine", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("crate", "A crate", "search", "Examine", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("Gate", "This gate barrs your way into gu'tanoth", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("Gate", "This gate barrs your way into gu'tanoth", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("bush", "A leafy bush", "Search", "Examine", 1, 1, 1, 0, "bush1", i++));//990
		objects.add(new GameObjectDef("bush", "A leafy bush", "Search", "Examine", 1, 1, 1, 0, "bush1", i++));
		objects.add(new GameObjectDef("bush", "A leafy bush", "Search", "Examine", 1, 1, 1, 0, "bush1", i++));
		objects.add(new GameObjectDef("bush", "A leafy bush", "Search", "Examine", 1, 1, 1, 0, "bush1", i++));
		objects.add(new GameObjectDef("multicannon", "fires metal balls", "inspect", "Examine", 1, 1, 1, 0, "dwarf multicannon", i++));
		objects.add(new GameObjectDef("Rocks", "Some rocks are close to the egde", "jump over", "look at", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "Some rocks are close to the edge", "jump over", "look at", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading downwards", "Climb-down", "Examine", 0, 1, 1, 0, "ladderdown", i++));
		objects.add(new GameObjectDef("Cave entrance", "I wonder what is inside...", "enter", "Examine", 1, 1, 1, 0, "small caveentrance2", i++));
		objects.add(new GameObjectDef("Counter", "An ogre is selling cakes here", "Steal from", "Examine", 1, 1, 1, 0, "rock cake counter", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));//1000
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Bookcase", "A large collection of books", "Look", "Search", 1, 1, 2, 0, "bookcase", i++));
		objects.add(new GameObjectDef("Captains Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Experimental Anvil", "An experimental anvil - for developing new techniques in forging", "Use", "Examine", 1, 1, 1, 0, "anvil", i++));
		objects.add(new GameObjectDef("Rocks", "A small pile of stones", "Search", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "Search", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("Column", "Created by ancient mages", "WalkTo", "Examine", 1, 1, 1, 0, "magearena colomn", i++));
		objects.add(new GameObjectDef("Wall", "Created by ancient mages", "WalkTo", "Examine", 1, 1, 1, 0, "magearena wall", i++));//1010
		objects.add(new GameObjectDef("Wall", "Created by ancient mages", "WalkTo", "Examine", 1, 1, 1, 0, "magearena corner", i++));
		objects.add(new GameObjectDef("Wall", "Created by ancient mages", "WalkTo", "Examine", 1, 1, 1, 0, "magearena tallwall", i++));
		objects.add(new GameObjectDef("Wall", "Created by ancient mages", "WalkTo", "Examine", 1, 1, 1, 0, "magearena cornerfill", i++));
		objects.add(new GameObjectDef("Lever", "The lever is up", "pull", "Examine", 0, 1, 1, 0, "leverup", i++));
		objects.add(new GameObjectDef("Lever", "The lever is down", "pull", "Examine", 0, 1, 1, 0, "leverdown", i++));
		objects.add(new GameObjectDef("Wall", "Created by ancient mages", "WalkTo", "Examine", 1, 1, 1, 0, "magearena tallcorner", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading downwards", "Climb-Down", "Examine", 1, 1, 1, 0, "ladderdown", i++));
		objects.add(new GameObjectDef("Wall", "Created by ancient mages", "WalkTo", "Examine", 1, 1, 1, 0, "magearena plain wall", i++));
		objects.add(new GameObjectDef("Gate", "The gate is closed", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("Gate", "The gate is closed", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));//1020
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading upwards", "Climb-Up", "Examine", 1, 1, 1, 0, "ladder", i++));
		objects.add(new GameObjectDef("shock", "cosmic energy", "WalkTo", "Examine", 1, 1, 1, 0, "spellshock", i++));
		objects.add(new GameObjectDef("Desk", "A very strong looking table with some locked drawers.", "WalkTo", "Search", 1, 2, 1, 120, "counter", i++));
		objects.add(new GameObjectDef("Cave", "I wonder what's inside!", "enter", "Examine", 1, 1, 1, 0, "small caveentrance2", i++));
		objects.add(new GameObjectDef("Mining Cart", "A sturdy well built mining cart with barrels full of rock on the back.", "WalkTo", "Search", 1, 2, 3, 0, "cart", i++));
		objects.add(new GameObjectDef("Rock of Dalgroth", "A mysterious boulder of the ogres", "mine", "prospect", 1, 2, 2, 0, "cave bolder", i++));
		objects.add(new GameObjectDef("entrance", "Created by ancient mages", "walk through", "Examine", 1, 1, 1, 0, "magearena door", i++));
		objects.add(new GameObjectDef("Dried Cactus", "It looks very spikey", "WalkTo", "Examine", 1, 1, 1, 0, "cactuswatered", i++));
		objects.add(new GameObjectDef("climbing rocks", "I wonder if I can climb up these", "climb", "Examine", 0, 1, 1, 0, "climbing_rocks", i++));
		objects.add(new GameObjectDef("Rocks", "Strange rocks - who knows why they're wanted?", "Mine", "Prospect", 1, 1, 1, 0, "tinrock1", i++));//1030
		objects.add(new GameObjectDef("lightning", "blimey!", "WalkTo", "Examine", 1, 1, 1, 0, "lightning1", i++));
		objects.add(new GameObjectDef("Crude Desk", "A very roughly constructed desk", "WalkTo", "Search", 1, 2, 1, 120, "counter", i++));
		objects.add(new GameObjectDef("Heavy Metal Gate", "This is an immense and very heavy looking gate made out of thick wrought metal", "Look", "Push", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("Counter", "An ogre is selling cakes here", "Steal from", "Examine", 1, 1, 1, 0, "rock cake counter", i++));
		objects.add(new GameObjectDef("Crude bed", "A flea infested sleeping experience", "rest", "Examine", 1, 1, 2, 0, "poorbed", i++));
		objects.add(new GameObjectDef("flames", "looks hot!", "WalkTo", "Examine", 1, 1, 1, 0, "firespell1", i++));
		objects.add(new GameObjectDef("Carved Rock", "An ornately carved rock with a pointed recepticle", "WalkTo", "Search", 1, 1, 1, 120, "cave small stagamite", i++));
		objects.add(new GameObjectDef("USE", "FREE SLOT PLEASE USE", "WalkTo", "Examine", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("crate", "A crate used for storing materials", "search", "Examine", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("crate", "A crate used for storing materials", "search", "Examine", 1, 1, 1, 0, "crate", i++));//1040
		objects.add(new GameObjectDef("barrel", "Its shut", "search", "Examine", 1, 1, 1, 0, "barrel", i++));
		objects.add(new GameObjectDef("Brick", "A stone brick", "WalkTo", "Examine", 1, 1, 1, 0, "1-1light", i++));
		objects.add(new GameObjectDef("Brick", "A stone brick", "WalkTo", "Examine", 1, 1, 1, 0, "1-1dark", i++));
		objects.add(new GameObjectDef("Brick", "A stone brick", "WalkTo", "Examine", 1, 3, 1, 0, "1-3light", i++));
		objects.add(new GameObjectDef("Brick", "A stone brick", "WalkTo", "Examine", 1, 3, 1, 0, "1-3dark", i++));
		objects.add(new GameObjectDef("Brick", "A stone brick", "WalkTo", "Examine", 1, 2, 2, 0, "2-2light", i++));
		objects.add(new GameObjectDef("Brick", "A stone brick", "WalkTo", "Examine", 1, 1, 1, 0, "2-2dark", i++));
		objects.add(new GameObjectDef("Barrier", "this section is roped off", "WalkTo", "Examine", 1, 1, 1, 0, "barrier1", i++));
		objects.add(new GameObjectDef("buried skeleton", "I hope I don't meet any of these", "search", "Examine", 1, 1, 1, 0, "halfburiedskeleton", i++));
		objects.add(new GameObjectDef("Brick", "A stone brick", "WalkTo", "Examine", 1, 1, 1, 0, "2-1light", i++));//1050
		objects.add(new GameObjectDef("Brick", "A stone brick", "WalkTo", "Examine", 1, 1, 1, 0, "2-1light", i++));
		objects.add(new GameObjectDef("Specimen tray", "A pile of sifted earth", "WalkTo", "Search", 1, 2, 2, 0, "compost", i++));
		objects.add(new GameObjectDef("winch", "This winches earth from the dig hole", "Operate", "Examine", 1, 1, 2, 0, "liftwinch", i++));
		objects.add(new GameObjectDef("crate", "A crate", "search", "Examine", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("crate", "A crate", "search", "Examine", 1, 1, 1, 0, "crate", i++));
		objects.add(new GameObjectDef("Urn", "A large ornamental urn", "WalkTo", "Examine", 1, 1, 1, 0, "largeurn", i++));
		objects.add(new GameObjectDef("buried skeleton", "I'm glad this isn't around now", "search", "Examine", 1, 1, 1, 0, "halfburiedskeleton2", i++));
		objects.add(new GameObjectDef("panning point", "a shallow where I can pan for gold", "look", "Examine", 0, 1, 1, 0, "fishing", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "WalkTo", "Examine", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("signpost", "a signpost", "read", "Examine", 1, 1, 1, 0, "signpost2", i++));//1060
		objects.add(new GameObjectDef("signpost", "a signpost", "read", "Examine", 1, 1, 1, 0, "signpost2", i++));
		objects.add(new GameObjectDef("signpost", "a signpost", "read", "Examine", 1, 1, 1, 0, "signpost2", i++));
		objects.add(new GameObjectDef("signpost", "a signpost", "read", "Examine", 1, 1, 1, 0, "signpost2", i++));
		objects.add(new GameObjectDef("signpost", "Digsite educational centre", "WalkTo", "Examine", 1, 1, 1, 0, "signpost", i++));
		objects.add(new GameObjectDef("soil", "soil", "search", "Examine", 0, 1, 1, 0, "dugupsoil1", i++));
		objects.add(new GameObjectDef("soil", "soil", "search", "Examine", 0, 1, 1, 0, "dugupsoil2", i++));
		objects.add(new GameObjectDef("soil", "soil", "search", "Examine", 0, 1, 1, 0, "dugupsoil3", i++));
		objects.add(new GameObjectDef("Gate", "The gate has closed", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("ship", "The ship is sinking", "WalkTo", "Examine", 2, 1, 2, 0, "sinkingshipfront", i++));
		objects.add(new GameObjectDef("barrel", "The ship is sinking", "climb on", "Examine", 2, 1, 2, 0, "sinkingbarrel", i++));//1070
		objects.add(new GameObjectDef("Leak", "The ship is sinking", "fill", "Examine", 0, 1, 1, 0, "shipleak", i++));
		objects.add(new GameObjectDef("bush", "A leafy bush", "search", "Examine", 1, 1, 1, 0, "bush1", i++));
		objects.add(new GameObjectDef("bush", "A leafy bush", "search", "Examine", 1, 1, 1, 0, "bush1", i++));
		objects.add(new GameObjectDef("cupboard", "The cupboard is shut", "open", "Examine", 1, 1, 2, 0, "cupboard", i++));
		objects.add(new GameObjectDef("sacks", "Yep they're sacks", "search", "Examine", 1, 1, 1, 0, "sacks", i++));
		objects.add(new GameObjectDef("sacks", "Yep they're sacks", "search", "Examine", 1, 1, 1, 0, "sacks", i++));
		objects.add(new GameObjectDef("Leak", "The ship is sinking", "fill", "Examine", 0, 1, 1, 0, "shipleak2", i++));
		objects.add(new GameObjectDef("cupboard", "The cupboard is shut", "search", "Examine", 1, 1, 2, 0, "cupboardopen", i++));
		objects.add(new GameObjectDef("Wrought Mithril Gates", "Magnificent wrought mithril gates giving access to the Legends Guild", "open", "Search", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("Legends Hall Doors", "Solid Oak doors leading to the Hall of Legends", "Open", "Search", 2, 1, 2, 0, "doubledoorsclosed", i++));//1080
		objects.add(new GameObjectDef("Camp bed", "Not comfortable but useful nonetheless", "WalkTo", "Examine", 1, 1, 2, 0, "poorbed", i++));
		objects.add(new GameObjectDef("barrel", "It has a lid on it - I need something to lever it off", "WalkTo", "Examine", 1, 1, 1, 0, "barrelredcross", i++));
		objects.add(new GameObjectDef("barrel", "I wonder what is inside...", "search", "Examine", 1, 1, 1, 0, "barrelredcross", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside...", "Open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Dense Jungle Tree", "Thick vegetation", "Chop", "Examine", 1, 1, 1, 0, "jungle medium size plant", i++));
		objects.add(new GameObjectDef("Jungle tree stump", "A chopped down jungle tree", "Walk", "Examine", 1, 1, 1, 0, "treestump", i++));
		objects.add(new GameObjectDef("signpost", "To the digsite", "WalkTo", "Examine", 1, 1, 1, 0, "signpost", i++));
		objects.add(new GameObjectDef("gate", "You can pass through this on the members server", "open", "Examine", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("Bookcase", "A large collection of books", "search", "Examine", 1, 1, 2, 0, "bookcase", i++));//1090
		objects.add(new GameObjectDef("Dense Jungle Tree", "An exotic looking tree", "Chop", "Examine", 1, 1, 1, 0, "jungle tree 2", i++));
		objects.add(new GameObjectDef("Dense Jungle Tree", "An exotic looking tree", "Chop", "Examine", 1, 1, 1, 0, "jungle tree 1", i++));
		objects.add(new GameObjectDef("Spray", "There's a strong wind", "WalkTo", "Examine", 1, 1, 1, 0, "shipspray1", i++));
		objects.add(new GameObjectDef("Spray", "There's a strong wind", "WalkTo", "Examine", 1, 1, 1, 0, "shipspray2", i++));
		objects.add(new GameObjectDef("winch", "This winches earth from the dig hole", "Operate", "Examine", 1, 1, 2, 0, "liftwinch", i++));
		objects.add(new GameObjectDef("Brick", "It seems these were put here deliberately", "search", "Examine", 1, 1, 1, 0, "1-1light", i++));
		objects.add(new GameObjectDef("Rope", "it's a rope leading upwards", "Climb-Up", "Examine", 1, 1, 1, 0, "ropeforclimbingbot", i++));
		objects.add(new GameObjectDef("Rope", "it's a rope leading upwards", "Climb-Up", "Examine", 1, 1, 1, 0, "ropeforclimbingbot", i++));
		objects.add(new GameObjectDef("Dense Jungle Palm", "A hardy palm tree with dense wood", "Chop", "Examine", 1, 1, 1, 0, "palm2", i++));
		objects.add(new GameObjectDef("Dense Jungle Palm", "A hardy palm tree with dense wood", "Chop", "Examine", 1, 1, 1, 0, "palm", i++));//1100
		objects.add(new GameObjectDef("Trawler net", "A huge net to catch little fish", "inspect", "Examine", 1, 1, 1, 0, "trawlernet-l", i++));
		objects.add(new GameObjectDef("Trawler net", "A huge net to catch little fish", "inspect", "Examine", 1, 1, 1, 0, "trawlernet-r", i++));
		objects.add(new GameObjectDef("Brick", "The bricks are covered in the strange compound", "WalkTo", "Examine", 1, 1, 1, 0, "1-1light", i++));
		objects.add(new GameObjectDef("Chest", "I wonder what is inside ?", "open", "Examine", 1, 1, 1, 0, "ChestClosed", i++));
		objects.add(new GameObjectDef("Chest", "Perhaps I should search it", "Search", "Close", 1, 1, 1, 0, "ChestOpen", i++));
		objects.add(new GameObjectDef("Trawler catch", "Smells like fish!", "Search", "Examine", 1, 1, 1, 0, "trawlernet", i++));
		objects.add(new GameObjectDef("Yommi Tree", "An adolescent rare and mystical looking tree in", "WalkTo", "Examine", 1, 2, 2, 0, "totemtree1", i++));
		objects.add(new GameObjectDef("Grown Yommi Tree", "A fully grown rare and mystical looking tree", "WalkTo", "Examine", 1, 2, 2, 0, "totemtree2", i++));
		objects.add(new GameObjectDef("Chopped Yommi Tree", "A mystical looking tree that has recently been felled", "WalkTo", "Examine", 1, 2, 2, 0, "totemtree3", i++));
		objects.add(new GameObjectDef("Trimmed Yommi Tree", "The trunk of the yommi tree.", "WalkTo", "Examine", 1, 2, 2, 0, "totemtree4", i++));//1110
		objects.add(new GameObjectDef("Totem Pole", "A nicely crafted wooden totem pole.", "Lift", "Examine", 1, 2, 2, 0, "totemtree5", i++));
		objects.add(new GameObjectDef("Baby Yommi Tree", "A baby Yommi tree - with a mystical aura", "WalkTo", "Examine", 1, 2, 2, 0, "smallfern", i++));
		objects.add(new GameObjectDef("Fertile earth", "A very fertile patch of earth", "WalkTo", "Examine", 0, 2, 2, 0, "dugupsoil1", i++));
		objects.add(new GameObjectDef("Rock Hewn Stairs", "steps cut out of the living rock", "Climb", "Examine", 1, 2, 3, 0, "rocksteps", i++));
		objects.add(new GameObjectDef("Hanging rope", "A rope hangs from the ceiling", "WalkTo", "Examine", 1, 1, 1, 0, "ropeladder", i++));
		objects.add(new GameObjectDef("Rocks", "A large boulder blocking the stream", "Move", "Examine", 1, 2, 2, 0, "cave bolder", i++));
		objects.add(new GameObjectDef("Boulder", "A large boulder blocking the way", "WalkTo", "Smash to pieces", 1, 2, 2, 0, "cave bolder", i++));
		objects.add(new GameObjectDef("dwarf multicannon", "fires metal balls", "fire", "pick up", 1, 1, 1, 0, "dwarf multicannon", i++));
		objects.add(new GameObjectDef("dwarf multicannon base", "bang", "pick up", "Examine", 1, 1, 1, 0, "dwarf multicannon part1", i++));
		objects.add(new GameObjectDef("dwarf multicannon stand", "bang", "pick up", "Examine", 1, 1, 1, 0, "dwarf multicannon part2", i++));//1120
		objects.add(new GameObjectDef("dwarf multicannon barrels", "bang", "pick up", "Examine", 1, 1, 1, 0, "dwarf multicannon part3", i++));
		objects.add(new GameObjectDef("rock", "A rocky outcrop", "climb over", "Examine", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Rock Hewn Stairs", "steps cut out of the living rock", "Climb", "Examine", 1, 2, 3, 0, "rocksteps", i++));
		objects.add(new GameObjectDef("Rock Hewn Stairs", "steps cut out of the living rock", "Climb", "Examine", 1, 2, 3, 0, "rocksteps", i++));
		objects.add(new GameObjectDef("Rock Hewn Stairs", "steps cut out of the living rock", "Climb", "Examine", 1, 2, 3, 0, "rocksteps", i++));
		objects.add(new GameObjectDef("Compost Heap", "The family gardeners' compost heap", "WalkTo", "Investigate", 1, 2, 2, 0, "compost", i++));
		objects.add(new GameObjectDef("beehive", "An old looking beehive", "WalkTo", "Investigate", 1, 1, 1, 0, "beehive", i++));
		objects.add(new GameObjectDef("Drain", "This drainpipe runs from the kitchen to the sewers", "WalkTo", "Investigate", 0, 1, 1, 0, "pipe&drain", i++));
		objects.add(new GameObjectDef("web", "An old thick spider's web", "WalkTo", "Investigate", 0, 1, 1, 0, "floorweb", i++));
		objects.add(new GameObjectDef("fountain", "There seems to be a lot of insects here", "WalkTo", "Investigate", 1, 2, 2, 0, "fountain", i++));//1130
		objects.add(new GameObjectDef("Sinclair Crest", "The Sinclair family crest", "WalkTo", "Investigate", 0, 1, 1, 0, "wallshield", i++));
		objects.add(new GameObjectDef("barrel", "Annas stuff - There seems to be something shiny at the bottom", "WalkTo", "Search", 1, 1, 1, 0, "barrel", i++));
		objects.add(new GameObjectDef("barrel", "Bobs things - There seems to be something shiny at the bottom", "WalkTo", "Search", 1, 1, 1, 0, "barrel", i++));
		objects.add(new GameObjectDef("barrel", "Carols belongings - there seems to be something shiny at the bottom", "WalkTo", "Search", 1, 1, 1, 0, "barrel", i++));
		objects.add(new GameObjectDef("barrel", "Davids equipment - there seems to be something shiny at the bottom", "WalkTo", "Search", 1, 1, 1, 0, "barrel", i++));
		objects.add(new GameObjectDef("barrel", "Elizabeths clothes - theres something shiny at the bottom", "WalkTo", "Search", 1, 1, 1, 0, "barrel", i++));
		objects.add(new GameObjectDef("barrel", "Franks barrel seems to have something shiny at the bottom", "WalkTo", "Search", 1, 1, 1, 0, "barrel", i++));
		objects.add(new GameObjectDef("Flour Barrel", "Its full of flour", "WalkTo", "Take From", 1, 1, 1, 0, "barrel", i++));
		objects.add(new GameObjectDef("sacks", "Full of various gardening tools", "WalkTo", "investigate", 1, 1, 1, 0, "sacks", i++));
		objects.add(new GameObjectDef("gate", "A sturdy and secure wooden gate", "WalkTo", "Investigate", 2, 1, 2, 0, "woodengateclosed", i++));//1140
		objects.add(new GameObjectDef("Dead Yommi Tree", "A dead Yommi Tree - it looks like a tough axe will be needed to fell this", "WalkTo", "Inspect", 1, 2, 2, 0, "deadtree2", i++));
		objects.add(new GameObjectDef("clawspell", "forces of guthix", "WalkTo", "Examine", 1, 1, 1, 0, "clawspell1", i++));
		objects.add(new GameObjectDef("Rocks", "The remains of a large rock", "WalkTo", "Examine", 1, 2, 2, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("crate", "A crate of some kind", "WalkTo", "Search", 1, 1, 1, 70, "crate", i++));
		objects.add(new GameObjectDef("Cavernous Opening", "A dark and mysterious cavern", "enter", "search", 1, 3, 1, 0, "caveentrance2", i++));
		objects.add(new GameObjectDef("Ancient Lava Furnace", "A badly damaged furnace fueled by red hot Lava - it looks ancient", "Look", "Search", 1, 2, 2, 0, "furnace", i++));
		objects.add(new GameObjectDef("Spellcharge", "forces of guthix", "WalkTo", "Examine", 1, 1, 1, 0, "spellcharge1", i++));
		objects.add(new GameObjectDef("Rocks", "A small rocky outcrop", "WalkTo", "Search", 1, 1, 1, 0, "cave rock1", i++));
		objects.add(new GameObjectDef("cupboard", "The cupboard is shut", "open", "Examine", 1, 1, 2, 0, "cupboard", i++));
		objects.add(new GameObjectDef("sacks", "Yep they're sacks", "search", "Examine", 1, 1, 1, 0, "sacks", i++));//1150
		objects.add(new GameObjectDef("Rock", "A rocky outcrop", "WalkTo", "Search", 1, 1, 1, 0, "rocks1", i++));
		objects.add(new GameObjectDef("Saradomin stone", "A faith stone", "chant to", "Examine", 1, 1, 1, 0, "saradominstone", i++));
		objects.add(new GameObjectDef("Guthix stone", "A faith stone", "chant to", "Examine", 1, 1, 1, 0, "guthixstone", i++));
		objects.add(new GameObjectDef("Zamorak stone", "A faith stone", "chant to", "Examine", 1, 1, 1, 0, "zamorakstone", i++));
		objects.add(new GameObjectDef("Magical pool", "A cosmic portal", "step into", "Examine", 1, 2, 2, 0, "rockpool", i++));
		objects.add(new GameObjectDef("Wooden Beam", "Some sort of support - perhaps used with ropes to lower people over the hole", "WalkTo", "Search", 0, 1, 1, 0, "Scaffoldsupport", i++));
		objects.add(new GameObjectDef("Rope down into darkness", "A scarey downwards trip into possible doom.", "WalkTo", "Use", 0, 1, 1, 0, "ScaffoldsupportRope", i++));
		objects.add(new GameObjectDef("Cave entrance", "A dark cave entrance leading to the surface.", "Enter", "Examine", 1, 3, 1, 0, "caveentrance2", i++));
		objects.add(new GameObjectDef("Cave entrance", "A small tunnel that leads to a large room beyond.", "enter", "Examine", 1, 2, 2, 0, "Shamancave", i++));
		objects.add(new GameObjectDef("Ancient Wooden Doors", "The doors are locked shut", "Open", "Pick Lock", 2, 1, 2, 0, "doubledoorsclosed", i++));//1160
		objects.add(new GameObjectDef("Table", "An old rickety table", "WalkTo", "search", 1, 1, 1, 96, "table", i++));
		objects.add(new GameObjectDef("Crude bed", "Barely a bed at all", "Rest", "Search", 1, 1, 2, 0, "poorbed", i++));
		objects.add(new GameObjectDef("Tall Reeds", "A tall plant with a tube for a stem.", "WalkTo", "Search", 0, 1, 1, 0, "bullrushes", i++));
		objects.add(new GameObjectDef("Goblin foot prints", "They seem to be heading south east", "WalkTo", "Examine", 0, 1, 1, 0, "sandyfootsteps", i++));
		objects.add(new GameObjectDef("Dark Metal Gate", "A dark metalic gate which seems to be fused with the rock", "Open", "Search", 2, 1, 2, 0, "metalgateclosed", i++));
		objects.add(new GameObjectDef("Magical pool", "A cosmic portal", "step into", "Examine", 1, 2, 2, 0, "rockpool", i++));
		objects.add(new GameObjectDef("Rope Up", "A welcome rope back up and out of this dark place.", "Climb", "Examine", 0, 1, 1, 0, "obstical_ropeswing", i++));
		objects.add(new GameObjectDef("Half buried remains", "Some poor unfortunate soul", "WalkTo", "Search", 1, 1, 1, 0, "skeletonwithbag", i++));
		objects.add(new GameObjectDef("Totem Pole", "A carved and decorated totem pole", "Look", "Examine", 1, 1, 1, 0, "totemtreeevil", i++));
		objects.add(new GameObjectDef("Totem Pole", "A carved and decorated totem pole", "Look", "Examine", 1, 1, 1, 0, "totemtreegood", i++));//1170
		objects.add(new GameObjectDef("Comfy bed", "Its a bed - wow", "rest", "Examine", 1, 2, 2, 0, "bed", i++));
		objects.add(new GameObjectDef("Rotten Yommi Tree", "A decomposing fully grown Yommi Tree", "WalkTo", "Inspect", 1, 2, 2, 0, "totemtreerotten2", i++));
		objects.add(new GameObjectDef("Rotten Yommi Tree", "A decomposing felled Yommi Tree", "WalkTo", "Inspect", 1, 2, 2, 0, "totemtreerotten3", i++));
		objects.add(new GameObjectDef("Rotten Yommi Tree", "A decomposing Yommi Tree Trunk", "WalkTo", "Inspect", 1, 2, 2, 0, "totemtreerotten4", i++));
		objects.add(new GameObjectDef("Rotten Totem Pole", "A decomposing Totem Pole", "WalkTo", "Inspect", 1, 2, 2, 0, "totemtreerotten5", i++));
		objects.add(new GameObjectDef("Leafy Palm Tree", "A shady palm tree", "Chop", "Examine", 1, 1, 1, 0, "palm", i++));
		objects.add(new GameObjectDef("Grand Viziers Desk", "A very elegant desk - you could knock it to get the Grand Viziers attention.", "WalkTo", "Knock on table", 1, 2, 1, 120, "counter", i++));
		objects.add(new GameObjectDef("Strange Barrel", "It might have something inside of it.", "Smash", "Examine", 1, 1, 1, 0, "barrel", i++));
		objects.add(new GameObjectDef("ship", "A sturdy sailing ship", "WalkTo", "Examine", 0, 5, 3, 0, "Shipfront", i++));
		objects.add(new GameObjectDef("ship", "A sturdy sailing ship", "WalkTo", "Examine", 0, 2, 3, 0, "Shipmiddle", i++));//1180
		objects.add(new GameObjectDef("ship", "A sturdy sailing ship", "WalkTo", "Examine", 0, 5, 3, 0, "Shipback", i++));
		objects.add(new GameObjectDef("digsite bed", "Not comfortable but useful nonetheless", "sleep", "Examine", 1, 1, 2, 0, "poorbed", i++));
		objects.add(new GameObjectDef("Tea stall", "A stall selling oriental infusions", "WalkTo", "Steal from", 1, 2, 2, 112, "market", i++));
		objects.add(new GameObjectDef("Boulder", "A large boulder blocking the way", "WalkTo", "Smash to pieces", 1, 2, 2, 0, "cave bolder", i++));
		objects.add(new GameObjectDef("Boulder", "A large boulder blocking the way", "WalkTo", "Smash to pieces", 1, 2, 2, 0, "cave bolder", i++));
		objects.add(new GameObjectDef("Damaged Earth", "Disturbed earth - it will heal itself in time", "WalkTo", "Examine", 0, 1, 1, 0, "dugupsoil1", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading upwards", "Climb-Up", "Examine", 1, 1, 1, 0, "ladder", i++));
		objects.add(new GameObjectDef("Ladder", "it's a ladder leading downwards", "Climb-Down", "Examine", 1, 1, 1, 0, "ladderdown", i++)); //1188
		objects.add(new GameObjectDef("Vine", "A creepy creeper", "Grab", "Examine", 0, 1, 1, 0, "vinejunction", i++));

		//Runecraft Objects
		objects.add(new GameObjectDef("Mysterious Ruins", "A mysterious power eminates from this shrine", "Enter", "Examine", 1, 3, 3, 0, "mysterious ruins", i++));//Air 1190
		objects.add(new GameObjectDef("Air Altar", "A mysterious power eminates from this shrine", "Bind", "Examine", 1, 2, 2, 0, "dolmen", i++));//Air
		objects.add(new GameObjectDef("Mysterious Ruins", "A mysterious power eminates from this shrine", "Enter", "Examine", 1, 3, 3, 0, "mysterious ruins", i++));//Mind
		objects.add(new GameObjectDef("Mind Altar", "A mysterious power eminates from this shrine", "Bind", "Examine", 1, 2, 2, 0, "dolmen", i++));//Mind
		objects.add(new GameObjectDef("Mysterious Ruins", "A mysterious power eminates from this shrine", "Enter", "Examine", 1, 3, 3, 0, "mysterious ruins", i++));//Water
		objects.add(new GameObjectDef("Water Altar", "A mysterious power eminates from this shrine", "Bind", "Examine", 1, 2, 2, 0, "dolmen", i++));//Water
		objects.add(new GameObjectDef("Mysterious Ruins", "A mysterious power eminates from this shrine", "Enter", "Examine", 1, 3, 3, 0, "mysterious ruins", i++));//Earth
		objects.add(new GameObjectDef("Earth Altar", "A mysterious power eminates from this shrine", "Bind", "Examine", 1, 2, 2, 0, "dolmen", i++));//Earth
		objects.add(new GameObjectDef("Mysterious Ruins", "A mysterious power eminates from this shrine", "Enter", "Examine", 1, 3, 3, 0, "mysterious ruins", i++));//Fire
		objects.add(new GameObjectDef("Fire Altar", "A mysterious power eminates from this shrine", "Bind", "Examine", 1, 2, 2, 0, "dolmen", i++));//Fire
		objects.add(new GameObjectDef("Mysterious Ruins", "A mysterious power eminates from this shrine", "Enter", "Examine", 1, 3, 3, 0, "mysterious ruins", i++));//Body
		objects.add(new GameObjectDef("Body Altar", "A mysterious power eminates from this shrine", "Bind", "Examine", 1, 2, 2, 0, "dolmen", i++));//Body
		objects.add(new GameObjectDef("Mysterious Ruins", "A mysterious power eminates from this shrine", "Enter", "Examine", 1, 3, 3, 0, "mysterious ruins", i++));//Cosmic
		objects.add(new GameObjectDef("Cosmic Altar", "A mysterious power eminates from this shrine", "Bind", "Examine", 1, 2, 2, 0, "dolmen", i++));//Cosmic
		objects.add(new GameObjectDef("Mysterious Ruins", "A mysterious power eminates from this shrine", "Enter", "Examine", 1, 3, 3, 0, "mysterious ruins", i++));//Chaos
		objects.add(new GameObjectDef("Chaos Altar", "A mysterious power eminates from this shrine", "Bind", "Examine", 1, 2, 2, 0, "dolmen", i++));//Chaos
		objects.add(new GameObjectDef("Mysterious Ruins", "A mysterious power eminates from this shrine", "Enter", "Examine", 1, 3, 3, 0, "mysterious ruins", i++));//Nature 1206
		objects.add(new GameObjectDef("Nature Altar", "A mysterious power eminates from this shrine", "Bind", "Examine", 1, 2, 2, 0, "dolmen", i++));//Nature
		objects.add(new GameObjectDef("Mysterious Ruins", "A mysterious power eminates from this shrine", "Enter", "Examine", 1, 3, 3, 0, "mysterious ruins", i++));//Law
		objects.add(new GameObjectDef("Law Altar", "A mysterious power eminates from this shrine", "Bind", "Examine", 1, 2, 2, 0, "dolmen", i++));//Law
		objects.add(new GameObjectDef("Mysterious Ruins", "A mysterious power eminates from this shrine", "Enter", "Examine", 1, 3, 3, 0, "mysterious ruins", i++));//Death
		objects.add(new GameObjectDef("Death Altar", "A mysterious power eminates from this shrine", "Bind", "Examine", 1, 2, 2, 0, "dolmen", i++));//Death
		objects.add(new GameObjectDef("Mysterious Ruins", "A mysterious power eminates from this shrine", "Enter", "Examine", 1, 3, 3, 0, "mysterious ruins", i++));//Blood
		objects.add(new GameObjectDef("Blood Altar", "A mysterious power eminates from this shrine", "Bind", "Examine", 1, 2, 2, 0, "dolmen", i++));//Blood
		objects.add(new GameObjectDef("Portal", "This will lead you out", "Exit", "Examine", 1, 2, 2, 0, "portal", i++));//air altar 1214
		objects.add(new GameObjectDef("Portal", "This will lead you out", "Exit", "Examine", 1, 2, 2, 0, "portal", i++));//mind altar
		objects.add(new GameObjectDef("Portal", "This will lead you out", "Exit", "Examine", 1, 2, 2, 0, "portal", i++));//water altar
		objects.add(new GameObjectDef("Portal", "This will lead you out", "Exit", "Examine", 1, 2, 2, 0, "portal", i++));//earth altar
		objects.add(new GameObjectDef("Portal", "This will lead you out", "Exit", "Examine", 1, 2, 2, 0, "portal", i++));//fire altar
		objects.add(new GameObjectDef("Portal", "This will lead you out", "Exit", "Examine", 1, 2, 2, 0, "portal", i++));//body altar
		objects.add(new GameObjectDef("Portal", "This will lead you out", "Exit", "Examine", 1, 2, 2, 0, "portal", i++));//cosmic altar
		objects.add(new GameObjectDef("Portal", "This will lead you out", "Exit", "Examine", 1, 2, 2, 0, "portal", i++));//chaos altar
		objects.add(new GameObjectDef("Portal", "This will lead you out", "Exit", "Examine", 1, 2, 2, 0, "portal", i++));//nature altar
		objects.add(new GameObjectDef("Portal", "This will lead you out", "Exit", "Examine", 1, 2, 2, 0, "portal", i++));//law altar
		objects.add(new GameObjectDef("Portal", "This will lead you out", "Exit", "Examine", 1, 2, 2, 0, "portal", i++));//death altar
		objects.add(new GameObjectDef("Portal", "This will lead you out", "Exit", "Examine", 1, 2, 2, 0, "portal", i++));//blood altar
		objects.add(new GameObjectDef("Portal", "This will lead you out", "Exit", "Examine", 1, 2, 2, 0, "portal", i++));//Runeessence portal
		objects.add(new GameObjectDef("Raw Essence", "A pile of raw essence", "Mine", "Examine", 1, 8, 8, 0, "essencemine", i++));//rune stone mine1227
		objects.add(new GameObjectDef("Portal", "This portal helps you navigate the maze.", "Take", "Examine", 1, 1, 1, 0, "portal", i++));//Runeessence portal 1228
		objects.add(new GameObjectDef("Portal", "This portal helps you navigate the maze.", "Take", "Examine", 1, 1, 1, 0, "portal", i++));//Runeessence portal
		objects.add(new GameObjectDef("Portal", "This portal helps you navigate the maze.", "Take", "Examine", 1, 1, 1, 0, "portal", i++));//Runeessence portal
		objects.add(new GameObjectDef("Portal", "This portal helps you navigate the maze.", "Take", "Examine", 1, 1, 1, 0, "portal", i++));//Runeessence portal
		objects.add(new GameObjectDef("Portal", "This portal helps you navigate the maze.", "Take", "Examine", 1, 1, 1, 0, "portal", i++));//Runeessence portal
		objects.add(new GameObjectDef("Portal", "This portal helps you navigate the maze.", "Take", "Examine", 1, 1, 1, 0, "portal", i++));//Runeessence portal
		objects.add(new GameObjectDef("Portal", "This portal helps you navigate the maze.", "Take", "Examine", 1, 1, 1, 0, "portal", i++));//Runeessence portal
		objects.add(new GameObjectDef("Portal", "This portal helps you navigate the maze.", "Take", "Examine", 1, 1, 1, 0, "portal", i++));//Runeessence portal


		objects.add(new GameObjectDef("pipe", "a dirty sewer pipe", "enter", "Examine", 1, 1, 1, 0, "obstical_pipe", i = 1236)); //yanille agility shortcut1336
		objects.add(new GameObjectDef("pipe", "a dirty sewer pipe", "enter", "Examine", 1, 1, 1, 0, "obstical_pipe", ++i)); //yanille agility shortcut

		objects.add(new GameObjectDef("Christmas Tree", "A very festive tree", "Collect", "Examine", 1, 1, 1, 0, "xmastree", ++i)); //1238
		objects.add(new GameObjectDef("Decorated Tree", "A tree that gathers people around", "WalkTo", "Examine", 1, 1, 1, 0, "ornamenttree", ++i));
		objects.add(new GameObjectDef("Pine Tree", "A tree waiting to be decorated", "WalkTo", "Examine", 1, 1, 1, 0, "pinetree", ++i));
		objects.add(new GameObjectDef("Tunnel entrance", "I wonder where this leads...", "enter", "Examine", 1, 3, 1, 0, "small caveentrance2", ++i));
		objects.add(new GameObjectDef("Rowboat", "This looks usable", "Travel", "Examine", 1, 2, 2, 0, "rowboat", ++i));

		//Harvesting Objects

		objects.add(new GameObjectDef("Lemon Tree", "A tree filled with many ripe lemons", "Harvest", "Examine", 1, 1, 1, 0, "lemontree", i = 1243)); //1243
		objects.add(new GameObjectDef("Lime Tree", "A tree filled with many ripe limes", "Harvest", "Examine", 1, 1, 1, 0, "limetree", ++i));
		objects.add(new GameObjectDef("Apple Tree", "A tree filled with many ripe apples", "Harvest", "Examine", 1, 1, 1, 0, "appletree", ++i));
		objects.add(new GameObjectDef("Orange Tree", "A tree filled with many ripe oranges", "Harvest", "Examine", 1, 1, 1, 0, "orangetree", ++i));
		objects.add(new GameObjectDef("Grapefruit Tree", "A tree filled with many ripe grapefruits", "Harvest", "Examine", 1, 1, 1, 0, "grapefruittree", ++i));
		objects.add(new GameObjectDef("Banana Palm", "A palm containing many ripe bananas", "Harvest", "Examine", 1, 1, 1, 0, "bananapalm", ++i));
		objects.add(new GameObjectDef("Coconut Palm", "A palm containing many ripe coconuts", "Harvest", "Examine", 1, 1, 1, 0, "coconutpalm", ++i));
		objects.add(new GameObjectDef("Papaya Palm", "A palm containing many ripe papayas", "Harvest", "Examine", 1, 1, 1, 0, "papayapalm", ++i));
		objects.add(new GameObjectDef("Pineapple Plant", "A plant with many nice ripe pineapples", "Harvest", "Examine", 1, 1, 1, 0, "pineappleplant", ++i));
		objects.add(new GameObjectDef("Exhausted Tree", "Someone has taken the last of the produce!", "WalkTo", "Examine", 1, 1, 1, 0, "exhaustedtree", ++i));
		objects.add(new GameObjectDef("Exhausted Palm", "Someone has taken the last of the produce!", "WalkTo", "Examine", 1, 1, 1, 0, "exhaustedpalm", ++i));
		objects.add(new GameObjectDef("Exhausted Palm", "Someone has taken the last of the produce!", "WalkTo", "Examine", 1, 1, 1, 0, "exhaustedpalm2", ++i));
		objects.add(new GameObjectDef("Exhausted Plant", "A plant that got its produce taken away", "WalkTo", "Examine", 1, 1, 1, 0, "depletedplant", ++i));
		objects.add(new GameObjectDef("Redberry Bush", "A bush containing some redberries", "Harvest", "Examine", 1, 1, 1, 0, "redberrybush", ++i)); //1256
		objects.add(new GameObjectDef("Cadavaberry Bush", "A bush containing some cadavaberries", "Harvest", "Examine", 1, 1, 1, 0, "cadavaberrybush", ++i));
		objects.add(new GameObjectDef("Dwellberry Bush", "A bush filled with mysterious dwellberries", "Harvest", "Examine", 1, 1, 1, 0, "dwellberrybush", ++i));
		objects.add(new GameObjectDef("Jangerberry Bush", "A bush having the mysterious jangerberries", "Harvest", "Examine", 1, 1, 1, 0, "jangerberrybush", ++i));
		objects.add(new GameObjectDef("Whiteberry Bush", "A bush containing some whiteberries", "Harvest", "Examine", 1, 1, 1, 0, "whiteberrybush", ++i));
		objects.add(new GameObjectDef("Depleted Bush", "A bush that once contained berries", "WalkTo", "Examine", 1, 1, 1, 0, "depletedbush", ++i));
		objects.add(new GameObjectDef("Cabbage", "Oooh some cabbage", "Harvest", "Examine", 0, 1, 1, 0, "greencabbage", ++i)); //1262
		objects.add(new GameObjectDef("Red Cabbage", "Oooh some red cabbage", "Harvest", "Examine", 0, 1, 1, 0, "redcabbage", ++i));
		objects.add(new GameObjectDef("White Pumpkin", "A pumpkin ready for harvest", "Harvest", "Examine", 0, 1, 1, 0, "pumpkinwhite", ++i));
		objects.add(new GameObjectDef("Potato Plant", "Some nice looking potatoes growing underneath", "Harvest", "Examine", 0, 1, 1, 0, "potatoplant", ++i));
		objects.add(new GameObjectDef("Onion Plant", "Some nice onions growing underneath", "Harvest", "Examine", 0, 1, 1, 0, "onionplant", ++i));
		objects.add(new GameObjectDef("Garlic Plant", "Some garlic growing underneath", "Harvest", "Examine", 0, 1, 1, 0, "garlicplant", ++i));
		objects.add(new GameObjectDef("Tomato Plant", "This plant has some good looking tomatoes", "Harvest", "Examine", 0, 1, 1, 0, "tomatoplant", ++i));
		objects.add(new GameObjectDef("Corn Plant", "This plant contains ripe corn", "Harvest", "Examine", 0, 1, 1, 0, "cornplant", ++i));
		objects.add(new GameObjectDef("Damaged Ground", "Disturbed ground left after a harvest", "WalkTo", "Examine", 0, 1, 1, 0, "dugupsoil1", ++i));
		objects.add(new GameObjectDef("Depleted tomato plant", "A plant that got its produce taken away", "WalkTo", "Examine", 0, 1, 1, 0, "depletedtomato", ++i));
		objects.add(new GameObjectDef("Depleted corn plant", "A plant that got its produce taken away", "WalkTo", "Examine", 0, 1, 1, 0, "depletedcorn", ++i));
		objects.add(new GameObjectDef("Snape Grass", "Some interesting snape grass growing here", "Clip", "Examine", 1, 1, 1, 0, "snapegrass", ++i));
		objects.add(new GameObjectDef("Herb", "I wonder what herb is around", "Clip", "Examine", 1, 1, 1, 0, "herb", ++i));
		objects.add(new GameObjectDef("Pumpkin", "A pumpkin of autumn", "Harvest", "Examine", 0, 1, 1, 0, "pumpkin", ++i));
		objects.add(new GameObjectDef("Soil Mound", "A pile of very good soil", "WalkTo", "Examine", 1, 1, 1, 0, "soilmound", ++i));
		objects.add(new GameObjectDef("Barrel of water", "A barrel filled with filtered water", "WalkTo", "Examine", 1, 1, 1, 0, "barrelwater", ++i));
		objects.add(new GameObjectDef("Compost Bin", "A bin of compost", "Open", "Examine", 1, 1, 1, 0, "compostbin", ++i));
		objects.add(new GameObjectDef("Compost Bin", "A bin of compost", "Close", "Examine", 1, 1, 1, 0, "compostbin2", ++i));
		objects.add(new GameObjectDef("Sea Weed", "Some tall sea weed growing here", "Clip", "Examine", 1, 1, 1, 0, "seaweed", ++i));//1280
		objects.add(new GameObjectDef("Limpwurt Root", "Some nice limpwurt root around here", "Clip", "Examine", 1, 1, 1, 0, "limpwurtroot", ++i));
		objects.add(new GameObjectDef("Sugar Cane", "The plant of interesting sugar cane!", "Harvest", "Examine", 0, 1, 1, 0, "sugarcane", ++i));
		objects.add(new GameObjectDef("Mysterious Grape Vine", "This vine may have more than just grapes", "Harvest", "Examine", 0, 1, 1, 0, "grapevine", ++i));


		objects.add(new GameObjectDef("Lava Forge", "The latest in dwarven technology", "WalkTo", "Examine", 1, 2, 2, 0, "furnace", ++i));//1284
		objects.add(new GameObjectDef("anvil", "heavy metal", "WalkTo", "Examine", 1, 1, 1, 0, "anvil", ++i));//1285
		objects.add(new GameObjectDef("Rocks", "It looks dangerous...", "climb", "Examine", 0, 1, 1, 0, "brownclimbingrocks", ++i));//1286
		//Taverly stepping stone
		objects.add(new GameObjectDef("Stepping Stone", "It looks like I could jump on this", "jump to", "Examine", 1, 1, 1, 0, "stonedisc", ++i)); //1287
		//Catherby stepping stone
		objects.add(new GameObjectDef("Stepping Stone", "It looks like I could jump on this", "jump to", "Examine", 1, 1, 1, 0, "stonedisc", ++i)); //1288
		//Stone that sits between them
		objects.add(new GameObjectDef("Stepping Stone", "It looks like I could jump on this", "WalkTo", "Examine", 1, 1, 1, 0, "stonedisc", ++i)); //1289
		//Falador->Members area handholds
		objects.add(new GameObjectDef("Handholds", "I wonder if I can climb up these", "climb", "Examine", 0, 1, 1, 0, "climbing_rocks", ++i)); //1290
		//KBD->drag stepping stone
		objects.add(new GameObjectDef("Stepping Stone", "It looks like I could jump on this", "jump to", "Examine", 1, 1, 1, 0, "stonedisc", ++i)); //1291
		//drag->KBD stepping stone
		objects.add(new GameObjectDef("Stepping Stone", "It looks like I could jump on this", "jump to", "Examine", 1, 1, 1, 0, "stonedisc", ++i)); //1292


		objects.add(new GameObjectDef("Dragonfruit Tree", "A tree filled with many ripe dragonfruits", "Harvest", "Examine", 1, 1, 1, 0, "dragonfruit", ++i)); //1293
		objects.add(new GameObjectDef("Exhausted Tree", "Someone has taken the last of the produce!", "WalkTo", "Examine", 1, 1, 1, 0, "depleteddragonfruit", ++i)); //1294

		objects.add(new GameObjectDef("Stepping Stone", "It looks like I could jump on this", "jump to", "Examine", 1, 1, 1, 0, "stonedisc", ++i)); //1295
		objects.add(new GameObjectDef("Soul Altar", "A mysterious power emanates from this shrine", "Bind", "Examine", 1, 2, 2, 0, "dolmen", ++i)); //1296
		objects.add(new GameObjectDef("Air Glyph", "A hovering air rune glyph", "WalkTo", "Examine", 0, 1, 1, 0, "myworld_air_glyph", ++i)); //1297
		objects.add(new GameObjectDef("Obelisk of mind", "A tall stone pointy thing", "WalkTo", "Examine", 1, 1, 1, 0, "obelisk", ++i)); //1298
		objects.add(new GameObjectDef("Obelisk of body", "A tall stone pointy thing", "WalkTo", "Examine", 1, 1, 1, 0, "obelisk", ++i)); //1299
		objects.add(new GameObjectDef("Obelisk of cosmic", "A tall stone pointy thing", "WalkTo", "Examine", 1, 1, 1, 0, "obelisk", ++i)); //1300
		objects.add(new GameObjectDef("Obelisk of chaos", "A tall stone pointy thing", "WalkTo", "Examine", 1, 1, 1, 0, "obelisk", ++i)); //1301
		objects.add(new GameObjectDef("Obelisk of nature", "A tall stone pointy thing", "WalkTo", "Examine", 1, 1, 1, 0, "obelisk", ++i)); //1302
		objects.add(new GameObjectDef("Obelisk of law", "A tall stone pointy thing", "WalkTo", "Examine", 1, 1, 1, 0, "obelisk", ++i)); //1303
		objects.add(new GameObjectDef("Obelisk of death", "A tall stone pointy thing", "WalkTo", "Examine", 1, 1, 1, 0, "obelisk", ++i)); //1304
		objects.add(new GameObjectDef("Obelisk of blood", "A tall stone pointy thing", "WalkTo", "Examine", 1, 1, 1, 0, "obelisk", ++i)); //1305
		objects.add(new GameObjectDef("Obelisk of soul", "A tall stone pointy thing", "WalkTo", "Examine", 1, 1, 1, 0, "obelisk", ++i)); //1306
		objects.add(new GameObjectDef("Tin Tree", "A magical tree flecked with tin", "Chop", "Examine", 1, 1, 1, 0, "tree2", ++i)); //1307
		objects.add(new GameObjectDef("Copper Tree", "A magical tree flecked with copper", "Chop", "Examine", 1, 1, 1, 0, "tree2", ++i)); //1308
		objects.add(new GameObjectDef("Iron Tree", "A magical tree flecked with iron", "Chop", "Examine", 1, 1, 1, 0, "tree2", ++i)); //1309
		objects.add(new GameObjectDef("Coal Tree", "A magical tree darkened by coal", "Chop", "Examine", 1, 1, 1, 0, "tree2", ++i)); //1310
		objects.add(new GameObjectDef("Silver Tree", "A magical tree shining with silver", "Chop", "Examine", 1, 1, 1, 0, "tree2", ++i)); //1311
		objects.add(new GameObjectDef("Gold Tree", "A magical tree shining with gold", "Chop", "Examine", 1, 1, 1, 0, "tree2", ++i)); //1312
		objects.add(new GameObjectDef("Adamantite Tree", "A magical tree veined with adamantite", "Chop", "Examine", 1, 1, 1, 0, "tree2", ++i)); //1313
		objects.add(new GameObjectDef("Runite Tree", "A magical tree veined with runite", "Chop", "Examine", 1, 1, 1, 0, "tree2", ++i)); //1314
		objects.add(new GameObjectDef("Resource Plant", "A magical plant ready to harvest", "Harvest", "Examine", 1, 1, 1, 0, "herb", ++i)); //1315
		objects.add(new GameObjectDef("Resource Tree", "A magical tree ready to harvest", "Chop", "Examine", 1, 1, 1, 0, "tree2", ++i)); //1316
		objects.add(new GameObjectDef("Knowledge Tree", "A magical tree humming with knowledge", "Chop", "Examine", 1, 1, 1, 0, "tree2", ++i)); //1317
		objects.add(new GameObjectDef("Money Tree", "A magical tree glittering with coins", "Chop", "Examine", 1, 1, 1, 0, "tree2", ++i)); //1318
		objects.add(new GameObjectDef("Knowledge Plant", "A magical plant humming with knowledge", "Harvest", "Examine", 1, 1, 1, 0, "herb", ++i)); //1319
		objects.add(new GameObjectDef("Money Plant", "A magical plant glittering with coins", "Harvest", "Examine", 1, 1, 1, 0, "herb", ++i)); //1320
		objects.add(new GameObjectDef("Life Altar", "A mysterious power emanates from this shrine", "Bind", "Examine", 1, 2, 2, 0, "dolmen", ++i)); //1321
		objects.add(new GameObjectDef("Obelisk of life", "A tall stone pointy thing", "WalkTo", "Examine", 1, 1, 1, 0, "obelisk", ++i)); //1322
	}

	public static void load(boolean loadMembers) {
		// Each function should contain only 250 definitions,
		// otherwise they get too big to compile.
		loadNpcDefinitions1();
		loadNpcDefinitions2();
		loadNPCDefinitions3();
		loadNpcDefinitions4();
		loadItemDefinitions();
		applyMyWorldToolOverrides();
		applyMyWorldEconomyOverrides();
		loadTextureDefinitions();
		loadAnimationDefinitions();
		loadSpellDefinitions();
		loadPrayerDefinitions();
		loadTileDefinitions();
		loadDoorDefinitions();
		loadElevationDefinitions();
		loadGameObjectDefinitionsA();
		loadGameObjectDefinitionsB();
		loadProjectiles();
		loadGUIParts();
		loadCrowns();
		if (!Config.S_WANT_CUSTOM_SPRITES) {
			for (ItemDef item : items) {
				if (item.getSpriteID() + 1 > invPictureCount) {
					invPictureCount = item.getSpriteID() + 1;
				}
				if (item.membersItem && !loadMembers) {
					item.name = "Members object";
					item.description = "You need to be a member to use this object";
					item.basePrice = 0;
					item.command = null;
					item.wieldable = false;
					item.wearableID = 0;
					item.untradeable = true;
				}
			}
		}

		for (GameObjectDef object : objects) {
			object.modelID = storeModel(object
				.getObjectModel());
		}

	}

	private static void configureEquippableToolClient(int itemId, String name) {
		ItemDef item = items.get(itemId);
		if (item == null || isUnobtaniumPlaceholder(item)) {
			return;
		}
		setCustomItemDefinition(itemId, new ItemDef(name, item.getDescription(), commandString(item),
			item.basePrice, item.getSpriteID(), item.getSpriteLocation(), item.stackable, true, 16,
			item.getPictureMask(), item.getBlueMask(), item.membersItem, item.untradeable, item.noteable, itemId));
	}

	private static void applyMyWorldEconomyOverrides() {
		int[][] prices = new int[][]{
			{12, 420}, {87, 180}, {88, 900}, {144, 40}, {156, 180},
			{203, 1800}, {204, 5500}, {377, 40}, {405, 15000},
			{428, 900}, {594, 40000}, {1258, 420}, {1259, 900},
			{1260, 1800}, {1261, 5500}, {1262, 15000}, {1480, 40000},
			{1987, 40}, {2001, 40}, {2012, 80}, {2023, 3200},
			{2034, 9000}, {2047, 80}, {2048, 3200}, {2049, 9000},
			{2215, 80}, {2216, 180}, {2217, 420}, {2218, 900},
			{2219, 1800}, {2220, 3200}, {2221, 5500}, {2222, 9000},
			{2223, 15000}, {2682, 80}, {2683, 180}, {2684, 420},
			{2685, 900}, {2686, 1800}, {2687, 3200}, {2688, 5500},
			{2689, 9000}, {2690, 15000}
		};
		for (int[] price : prices) {
			ItemDef item = items.get(price[0]);
			if (item != null && !isUnobtaniumPlaceholder(item)) {
				item.basePrice = price[1];
			}
		}
	}

	private static void applyMyWorldToolOverrides() {
		configureEquippableToolClient(12, "Iron Hatchet");
		configureEquippableToolClient(87, "Bronze Hatchet");
		configureEquippableToolClient(88, "Steel Hatchet");
		configureEquippableToolClient(203, "Mithril Hatchet");
		configureEquippableToolClient(204, "Adamantite Hatchet");
		configureEquippableToolClient(405, "Rune Hatchet");
		configureEquippableToolClient(428, "Black Hatchet");
		configureEquippableToolClient(594, "Dragon Hatchet");
		configureEquippableToolClient(2001, "Tin Hatchet");
		configureEquippableToolClient(2012, "Copper Hatchet");
		configureEquippableToolClient(2023, "Titan Steel Hatchet");
		configureEquippableToolClient(2034, "Orichalcum Hatchet");

		configureEquippableToolClient(156, "Bronze Pickaxe");
		configureEquippableToolClient(1258, "Iron Pickaxe");
		configureEquippableToolClient(1259, "Steel Pickaxe");
		configureEquippableToolClient(1260, "Mithril Pickaxe");
		configureEquippableToolClient(1261, "Adamantite Pickaxe");
		configureEquippableToolClient(1262, "Rune Pickaxe");
		configureEquippableToolClient(1987, "Tin Pickaxe");
		configureEquippableToolClient(2047, "Copper Pickaxe");
		configureEquippableToolClient(2048, "Titan Steel Pickaxe");
		configureEquippableToolClient(2049, "Orichalcum Pickaxe");
	}

	public static int storeModel(String name) {
		if (name.equalsIgnoreCase("na")) {
			return 0;
		}
		int index = models.indexOf(name);
		if (index < 0) {
			models.add(name);
			return models.size() - 1;
		}
		return index;
	}
}
