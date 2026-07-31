package com.openrsc.server.external;

import com.openrsc.server.Server;
import com.openrsc.server.constants.AppearanceId;
import com.openrsc.server.constants.Constants;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.event.rsc.impl.projectile.RangeUtils;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.TelePoint;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldObjectTransition;
import com.openrsc.server.util.PersistenceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static com.openrsc.server.plugins.Functions.ZERO_RESERVED;
import static com.openrsc.server.plugins.Functions.patchObject;

/**
 * This class handles the loading of entities from the conf files, and provides
 * methods for relaying these entities to the user.
 */
@SuppressWarnings("unchecked")
public final class EntityHandler {

	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Set<String> MYWORLD_NPC_OVERRIDE_FIELDS = new HashSet<>(Arrays.asList(
		"id", "name", "description", "attack", "strength", "hits", "defense", "ranged",
		"projectileRange",
		"meleeOffense", "rangedOffense", "magicOffense",
		"meleeDefense", "rangedDefense", "magicDefense",
		"meleeDefenseMultiplier", "rangedDefenseMultiplier", "magicDefenseMultiplier",
		"meleeDefenseDivisor", "rangedDefenseDivisor", "magicDefenseDivisor",
		"combatlvl", "hairColour", "topColour", "bottomColour", "skinColour"
	));
	private static final Set<String> MYWORLD_ITEM_OVERRIDE_FIELDS = new HashSet<>(Arrays.asList(
		"id", "name", "description", "meleeOffense", "rangedOffense", "magicOffense",
		"weaponSpeed", "meleeDefense", "rangedDefense", "magicDefense", "requiredLevel",
		"requiredSkillID", "isWearable", "appearanceID", "wearableID", "wearSlot",
		"weaponAimBonus", "weaponPowerBonus", "armourBonus", "magicBonus", "prayerBonus", "basePrice"
	));

	private final Server server;
	private final PersistenceManager persistenceManager;
	private static final int MYWORLD_STAFF_BASE_APPEARANCE_START = 789;
	private static final int MYWORLD_RUNE_STAFF_APPEARANCE_START = MYWORLD_STAFF_BASE_APPEARANCE_START + 10;
	private static final int MYWORLD_RUNE_CLOTH_COLOR_COUNT = 14;
	private static final int MYWORLD_WOOL_ACCESSORY_APPEARANCE_COUNT = 2;
	private static final int MYWORLD_PICKAXE_APPEARANCE_START = MYWORLD_RUNE_STAFF_APPEARANCE_START
		+ (15 * 10)
		+ (MYWORLD_RUNE_CLOTH_COLOR_COUNT * 2)
		+ MYWORLD_WOOL_ACCESSORY_APPEARANCE_COUNT;
	private static final int MYWORLD_SHEARS_APPEARANCE_ID = AppearanceId.SHEARS.id();
	private static final int DEMON_PITCHFORK_APPEARANCE_ID = AppearanceId.DEMON_PITCHFORK.id();
	private static final int SARADOMIN_MACE_APPEARANCE_ID = AppearanceId.SARADOMIN_MACE.id();
	private static final int ZAMORAK_MACE_APPEARANCE_ID = AppearanceId.ZAMORAK_MACE.id();
	private static final int GUTHIX_MACE_APPEARANCE_ID = AppearanceId.GUTHIX_MACE.id();
	private static final int[] MYWORLD_STAFF_BASE_IDS = {
		100, 2131, 1764, 1769, 2136, 1774, 1779, 2141, 1784, 2146
	};
	private static final int[] MYWORLD_SHEARS_IDS = {
		ItemId.SHEARS.id(),
		ItemId.COPPER_SHEARS.id(),
		ItemId.BRONZE_SHEARS.id(),
		ItemId.IRON_SHEARS.id(),
		ItemId.STEEL_SHEARS.id(),
		ItemId.MITHRIL_SHEARS.id(),
		ItemId.TITAN_STEEL_SHEARS.id(),
		ItemId.ADAMANTITE_SHEARS.id(),
		ItemId.ORICHALCUM_SHEARS.id(),
		ItemId.RUNE_SHEARS.id(),
		MyWorldItemId.EXALTED_RUNE_SHEARS
	};
	private static final int[] MYWORLD_PICKAXE_IDS = {
		ItemId.TIN_PICKAXE.id(),
		ItemId.COPPER_PICKAXE.id(),
		ItemId.BRONZE_PICKAXE.id(),
		ItemId.IRON_PICKAXE.id(),
		ItemId.STEEL_PICKAXE.id(),
		ItemId.MITHRIL_PICKAXE.id(),
		ItemId.TITAN_STEEL_PICKAXE.id(),
		ItemId.ADAMANTITE_PICKAXE.id(),
		ItemId.ORICHALCUM_PICKAXE.id(),
		ItemId.RUNE_PICKAXE.id()
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

	public ArrayList<ItemDefinition> items;
	public ArrayList<ItemDefinition> itemsPatch;
	public ArrayList<NPCDef> npcs;
	public ArrayList<NPCDef> npcsPatch;
	public HashSet<String> npcNames;
	public HashSet<String> npcNamesLowerCase;
	public SpellDef[] spells;
	private HashMap<Integer, ItemArrowHeadDef> arrowHeads;
	private HashMap<Integer, ItemBowStringDef> bowString;
	private HashMap<Integer, CerterDef> certers;
	private HashMap<Integer, ItemDartTipDef> dartTips;
	private DoorDef[] doors;
	private HashMap<Integer, FiremakingDef> firemaking;
	private GameObjectDef[] gameObjects;
	private HashMap<Integer, ItemGemDef> gems;
	private ItemHerbSecond[] herbSeconds;
	private HashMap<Integer, int[]> itemAffectedTypes;
	private HashMap<Integer, ItemCookingDef> itemCooking;
	private HashMap<Integer, ItemPerfectCookingDef> itemPerfectCooking;
	private HashMap<Integer, ItemCraftingDef> itemCrafting;
	private HashMap<Integer, Integer> itemEdibleHeals;
	private HashMap<Integer, ItemHerbDef> itemHerb;
	private HashMap<Integer, ItemSmeltingDef> itemSmelting;
	private ItemSmithingDef[] itemSmithing;
	private HashMap<Integer, ItemUnIdentHerbDef> itemUnIdentHerb;
	private HashMap<Integer, ItemLogCutDef> logCut;
	private HashMap<Integer, ObjectFishingDef[]> objectFishing;
	private HashMap<Integer, ObjectMiningDef> objectMining;
	private HashMap<Point, TelePoint> objectTelePoints;
	private HashMap<Integer, ObjectWoodcuttingDef> objectWoodcutting;
	private HashMap<Integer, ObjectRunecraftDef> objectRunecraft;
	private HashMap<Integer, ObjectHarvestingDef> objectHarvesting;
	private PrayerDef[] prayers;
	private TileDef[] tiles;

	private int[] quickTradeNpcs = new int[] {
		NpcId.BOB.id(), NpcId.APOTHECARY.id(), NpcId.HORVIK_THE_ARMOURER.id(), NpcId.SHOPKEEPER_VARROCK.id(),
		NpcId.SHOPKEEPER_LUMBRIDGE.id(), NpcId.SHOPKEEPER_VARROCK_SWORD.id(), NpcId.LOWES_ARCHERY_SHOPKEEPER.id(),
		NpcId.THESSALIA.id(), NpcId.ZAFF.id(), NpcId.PEKSA.id(),
		NpcId.SHOP_ASSISTANT_VARROCK.id(), NpcId.SHOP_ASSISTANT_LUMBRIDGE.id(), NpcId.ZEKE.id(),
		NpcId.LOUIE_LEGS.id(), NpcId.SHOPKEEPER_ALKHARID.id(), NpcId.SHOP_ASSISTANT_ALKHARID.id(),
		NpcId.CASSIE.id(), NpcId.RANAEL.id(), NpcId.SHOPKEEPER_FALADOR.id(),
		NpcId.SHOP_ASSISTANT_FALADOR.id(), NpcId.VALAINE.id(), NpcId.DROGO.id(),
		NpcId.FLYNN.id(), NpcId.WYDIN.id(), NpcId.HEAD_CHEF.id(), NpcId.SHOP_ASSISTANT_VARROCK_SWORD.id(),
		NpcId.BRIAN.id(), NpcId.WAYNE.id(), NpcId.DWARVEN_SHOPKEEPER.id(),
		NpcId.SHOPKEEPER_RIMMINGTON.id(), NpcId.SHOP_ASSISTANT_RIMMINGTON.id(), NpcId.BETTY.id(),
		NpcId.HERQUIN.id(), NpcId.ROMMIK.id(), NpcId.GRUM.id(),
		NpcId.ZAMBO.id(), NpcId.GERRANT.id(), NpcId.SHOPKEEPER_KARAMJA.id(),
		NpcId.SHOP_ASSISTANT_KARAMJA.id(), NpcId.DOMMIK.id(),
		NpcId.SHOPKEEPER_EDGEVILLE.id(), NpcId.SHOP_ASSISTANT_EDGEVILLE.id(),
		NpcId.IRKSOL.id(), NpcId.JAKUT.id(), NpcId.FAIRY_SHOPKEEPER.id(),
		NpcId.FAIRY_SHOP_ASSISTANT.id(), NpcId.GAIUS.id(), NpcId.JATIX.id(),
		NpcId.NOTERAZZO.id(), NpcId.FAT_TONY.id(), NpcId.HARRY.id(),
		NpcId.ALFONSE_THE_WAITER.id(), NpcId.HELEMOS.id(), NpcId.DAVON.id(),
		NpcId.ARHEIN.id(), NpcId.CANDLEMAKER.id(), NpcId.HICKTON.id(),
		NpcId.FRINCOS.id(), NpcId.GEM_TRADER.id(), NpcId.BAKER.id(),
		NpcId.FUR_TRADER.id(), NpcId.SILVER_MERCHANT.id(), NpcId.SPICE_MERCHANT.id(),
		NpcId.GEM_MERCHANT.id(), NpcId.ZENESHA.id(), NpcId.AEMAD.id(),
		NpcId.KORTAN.id(), NpcId.SHOPKEEPER_FISHING_GUILD.id(), NpcId.SHOPKEEPER_PORTKHAZARD.id(),
		NpcId.BOLKOY.id(), NpcId.TAILOR.id(), NpcId.MAGIC_STORE_OWNER.id(),
		NpcId.JIMINUA.id(), NpcId.SHOP_KEEPER_TRAINING_CAMP.id(), NpcId.FRENITA.id(),
		NpcId.ROMETTI.id(), NpcId.HECKEL_FUNCH.id(),
		NpcId.HUDO_GLENFAD.id(), NpcId.GULLUCK.id(), NpcId.FERNAHEI.id(), NpcId.OBLI.id(),
		NpcId.CHADWELL.id(), NpcId.OGRE_MERCHANT.id(), NpcId.OGRE_TRADER_GENSTORE.id(),
		NpcId.OGRE_TRADER_ROCKCAKE.id(), NpcId.OGRE_TRADER_FOOD.id(), NpcId.SHANTAY_PASS_GUARD_MOVING.id(),
		NpcId.SHANTAY_PASS_GUARD_STANDING.id(), NpcId.ASSISTANT.id(), NpcId.NURMOF.id(),
		NpcId.SIEGFRIED_ERKLE.id(), NpcId.TEA_SELLER.id(), NpcId.FIONELLA.id(),
		NpcId.THRANDER.id(),
		NpcId.LUNDAIL.id(), NpcId.DWARVEN_SMITHY.id(), NpcId.GARDENER.id(),
		NpcId.RANGERS_GUILD_DRAGON_VENDOR.id()
	};

	public EntityHandler(Server server) {
		this.server = server;
		this.persistenceManager = new PersistenceManager(getServer());
	}

	public void unload() {
		npcs = null;
		npcsPatch = null;
		npcNames = null;
		npcNamesLowerCase = null;
		items = null;
		itemsPatch = null;

		doors = null;
		gameObjects = null;
		prayers = null;
		spells = null;
		tiles = null;

		herbSeconds = null;
		dartTips = null;
		gems = null;
		logCut = null;
		bowString = null;
		arrowHeads = null;
		firemaking = null;
		itemAffectedTypes = null;
		itemUnIdentHerb = null;
		itemHerb = null;
		itemEdibleHeals = null;
		itemCooking = null;
		itemPerfectCooking = null;
		itemSmelting = null;
		itemSmithing = null;
		itemCrafting = null;
		objectMining = null;
		objectWoodcutting = null;
		objectRunecraft = null;
		objectFishing = null;
		objectHarvesting = null;
		objectTelePoints = null;
		certers = null;
	}

	public void load() {
		npcs = new ArrayList<>();
		npcsPatch = new ArrayList<>();
		npcNames = new HashSet<>();
		npcNamesLowerCase = new HashSet<>();
		LOGGER.info("Loading npc definitions...");
		loadNpcs(getServer().getConfig().CONFIG_DIR + "/defs/NpcDefs.json");
		loadNpcs(getServer().getConfig().CONFIG_DIR + "/defs/NpcDefsCustom.json");
		//loadNpcs(getServer().getConfig().CONFIG_DIR + "/defs/NpcDefsExpansion.json");
		patchNpcs();
		if (getServer().getConfig().WANT_MYWORLD) {
			applyOptionalNpcOverrides(getServer().getConfig().CONFIG_DIR + "/defs/NpcDefsMyWorld.json");
		}
		customNpcConditions();
		loadNpcNames();
		LOGGER.info("Loaded " + npcs.size() + " total npc definitions");

		items = new ArrayList<>();
		itemsPatch = new ArrayList<>();
		LOGGER.info("Loading item definitions...");
		loadItems(getServer().getConfig().CONFIG_DIR + "/defs/ItemDefs.json");
		loadItems(getServer().getConfig().CONFIG_DIR + "/defs/ItemDefsCustom.json");
		//loadItems(getServer().getConfig().CONFIG_DIR + "/defs/ItemDefsExpansion.json");
		patchItems();
		customItemConditions();
		if (getServer().getConfig().WANT_MYWORLD) {
			applyOptionalItemOverrides(getServer().getConfig().CONFIG_DIR + "/defs/ItemDefsMyWorld.json");
		}
		LOGGER.info("Loaded " + items.size() + " item definitions");

		doors = (DoorDef[]) getPersistenceManager().load("defs/DoorDef.xml");
		gameObjects = (GameObjectDef[]) getPersistenceManager().load("defs/GameObjectDef.xml");
		prayers = (PrayerDef[]) getPersistenceManager().load("defs/PrayerDef.xml");
		if (!getServer().getConfig().LACKS_PRAYERS) {
			// On May 24 2001 original magic/prayer rework, new spells featured
			spells = (SpellDef[]) getPersistenceManager().load("defs/SpellDef.xml");
		} else {
			spells = (SpellDef[]) getPersistenceManager().load("defs/SpellDefRetro.xml");
		}
		tiles = (TileDef[]) getPersistenceManager().load("defs/TileDef.xml");

		herbSeconds = (ItemHerbSecond[]) getPersistenceManager().load(getPath("defs/extras/ItemHerbSecond.xml"));
		dartTips = (HashMap<Integer, ItemDartTipDef>) getPersistenceManager().load(getPath("defs/extras/ItemDartTipDef.xml"));
		gems = (HashMap<Integer, ItemGemDef>) getPersistenceManager().load(getPath("defs/extras/ItemGemDef.xml"));
		logCut = (HashMap<Integer, ItemLogCutDef>) getPersistenceManager().load(getPath("defs/extras/ItemLogCutDef.xml"));
		bowString = (HashMap<Integer, ItemBowStringDef>) getPersistenceManager().load(getPath("defs/extras/ItemBowStringDef.xml"));
		arrowHeads = (HashMap<Integer, ItemArrowHeadDef>) getPersistenceManager().load(getPath("defs/extras/ItemArrowHeadDef.xml"));
		firemaking = (HashMap<Integer, FiremakingDef>) getPersistenceManager().load(getPath("defs/extras/FiremakingDef.xml"));
		itemAffectedTypes = (HashMap<Integer, int[]>) getPersistenceManager().load(getPath("defs/extras/ItemAffectedTypes.xml"));
		itemUnIdentHerb = (HashMap<Integer, ItemUnIdentHerbDef>) getPersistenceManager().load(getPath("defs/extras/ItemUnIdentHerbDef.xml"));
		itemHerb = (HashMap<Integer, ItemHerbDef>) getPersistenceManager().load(getPath("defs/extras/ItemHerbDef.xml"));
		itemEdibleHeals = (HashMap<Integer, Integer>) getPersistenceManager().load(getPath("defs/extras/ItemEdibleHeals.xml"));
		itemCooking = (HashMap<Integer, ItemCookingDef>) getPersistenceManager().load(getPath("defs/extras/ItemCookingDef.xml"));
		itemPerfectCooking = (HashMap<Integer, ItemPerfectCookingDef>) getPersistenceManager().load(getPath("defs/extras/ItemPerfectCookingDef.xml"));
		itemSmelting = (HashMap<Integer, ItemSmeltingDef>) getPersistenceManager().load(getPath("defs/extras/ItemSmeltingDef.xml"));
		itemSmithing = (ItemSmithingDef[]) getPersistenceManager().load(getPath("defs/extras/ItemSmithingDef.xml"));
		itemCrafting = (HashMap<Integer, ItemCraftingDef>) getPersistenceManager().load(getPath("defs/extras/ItemCraftingDef.xml"));
		objectMining = (HashMap<Integer, ObjectMiningDef>) getPersistenceManager().load(getPath("defs/extras/ObjectMining.xml"));
		objectWoodcutting = (HashMap<Integer, ObjectWoodcuttingDef>) getPersistenceManager().load(getPath("defs/extras/ObjectWoodcutting.xml"));
		objectRunecraft = (HashMap<Integer, ObjectRunecraftDef>) getPersistenceManager().load(getPath("defs/extras/ObjectRunecraft.xml"));
		objectFishing = (HashMap<Integer, ObjectFishingDef[]>) getPersistenceManager().load(getPath("defs/extras/ObjectFishing.xml"));
		objectHarvesting = (HashMap<Integer, ObjectHarvestingDef>) getPersistenceManager().load(getPath("defs/extras/ObjectHarvesting.xml"));
		objectTelePoints = (HashMap<Point, TelePoint>) getPersistenceManager().load(getPath("defs/extras/ObjectTelePoints.xml"));
		certers = (HashMap<Integer, CerterDef>) getPersistenceManager().load(getPath("defs/extras/NpcCerters.xml"));

		for (int fishSpot : objectFishing.keySet()) {
			for (ObjectFishingDef fishDef : objectFishing.get(fishSpot)) {
				fishDef.calculateFishRates();
			}
		}

		for (int tree : objectWoodcutting.keySet()) {
			objectWoodcutting.get(tree).calculateWoodRates();
		}
	}

	private String getPath(String filePath) {
		String path = filePath;
		if (getServer().getConfig().OLD_SKILL_DEFS) {
			int idx = filePath.lastIndexOf('/');
			String retroPath = path.substring(0, idx + 1) + "retro/" + path.substring(idx + 1);
			File theFile = new File(getServer().getConfig().CONFIG_DIR, retroPath);
			if (!theFile.exists()) {
				// fallback for old servers using .gz definitions
				theFile = new File(getServer().getConfig().CONFIG_DIR, retroPath + ".gz");
				if (theFile.exists()) {
					path = retroPath;
				}
			} else {
				path = retroPath;
			}
		}
		return path;
	}

	private void loadNpcs(String filename) {
		try {
			JSONObject object = new JSONObject(new String(Files.readAllBytes(Paths.get(filename))));
			JSONArray npcDefs = object.getJSONArray(JSONObject.getNames(object)[0]);
			for (int i = 0; i < npcDefs.length(); i++) {
				NPCDef def = new NPCDef();
				JSONObject npc = npcDefs.getJSONObject(i);
				def.name = npc.getString("name");
				def.description = npc.getString("description");
				def.command1 = npc.getString("command");
				def.command2 = npc.getString("command2");
					def.attack = npc.getInt("attack");
					def.strength = npc.getInt("strength");
					def.hits = npc.getInt("hits");
					def.defense = npc.getInt("defense");
					def.ranged = npc.getBoolean("ranged") ? 1 : 0;
					def.projectileRange = readProjectileRange(npc, "Npc definition " + i);
					def.meleeOffense = npc.has("meleeOffense") ? npc.getInt("meleeOffense") : 0;
					def.rangedOffense = npc.has("rangedOffense") ? npc.getInt("rangedOffense") : 0;
					def.magicOffense = npc.has("magicOffense") ? npc.getInt("magicOffense") : 0;
					def.meleeDefense = npc.has("meleeDefense") ? npc.getInt("meleeDefense") : 0;
					def.rangedDefense = npc.has("rangedDefense") ? npc.getInt("rangedDefense") : 0;
					def.magicDefense = npc.has("magicDefense") ? npc.getInt("magicDefense") : 0;
					def.meleeDefenseMultiplier = npc.has("meleeDefenseMultiplier") ? npc.getDouble("meleeDefenseMultiplier") : -1.0D;
					def.rangedDefenseMultiplier = npc.has("rangedDefenseMultiplier") ? npc.getDouble("rangedDefenseMultiplier") : -1.0D;
					def.magicDefenseMultiplier = npc.has("magicDefenseMultiplier") ? npc.getDouble("magicDefenseMultiplier") : -1.0D;
					def.meleeDefenseDivisor = npc.has("meleeDefenseDivisor") ? npc.getDouble("meleeDefenseDivisor") : -1.0D;
					def.rangedDefenseDivisor = npc.has("rangedDefenseDivisor") ? npc.getDouble("rangedDefenseDivisor") : -1.0D;
					def.magicDefenseDivisor = npc.has("magicDefenseDivisor") ? npc.getDouble("magicDefenseDivisor") : -1.0D;
					def.combatLevel = npc.getInt("combatlvl");
				def.members = npc.getInt("isMembers") == 1;
				def.attackable = npc.getInt("attackable") == 1;
				def.aggressive = npc.getInt("aggressive") == 1;
				def.respawnTime = npc.getInt("respawnTime");
				int[] sprites = new int[12];
				for (int j = 0; j < 12; j++) {
					sprites[j] = npc.getInt("sprites" + (j+1));
				}
				def.sprites = sprites;
				def.hairColour = npc.getInt("hairColour");
				def.topColour = npc.getInt("topColour");
				def.bottomColour = npc.getInt("bottomColour");
				def.skinColour = npc.getInt("skinColour");
				def.camera1 = npc.getInt("camera1");
				def.camera2 = npc.getInt("camera2");
				def.walkModel = npc.getInt("walkModel");
				def.combatModel = npc.getInt("combatModel");
				def.combatSprite = npc.getInt("combatSprite");
				def.roundMode = npc.getInt("roundMode");
				npcs.add(def);
			}
			LOGGER.info("Loaded " + npcDefs.length() + " npcs from " + filename);
		}
		catch (Exception e) {
			LOGGER.error(e);
		}
	}

	private void loadPatchNpcs(String filename) {
		try {
			JSONObject object = new JSONObject(new String(Files.readAllBytes(Paths.get(filename))));
			JSONArray npcPatchDefs = object.getJSONArray(JSONObject.getNames(object)[0]);
			for (int i = 0; i < npcPatchDefs.length(); i++) {
				JSONObject npc = npcPatchDefs.getJSONObject(i);
				NPCDef.NPCDefinitionBuilder toAddBuild =
					new NPCDef.NPCDefinitionBuilder(npc.getInt("id"), npc.getString("name"))
						.description(npc.getString("description"))
						.command(npc.getString("command"))
						.attack((int)ifZeroReserve(npc.getInt("attack")))
						.strength((int)ifZeroReserve(npc.getInt("strength")))
						.hits((int)ifZeroReserve(npc.getInt("hits")))
						.defense((int)ifZeroReserve(npc.getInt("defense")))
						.ranged(npc.getBoolean("ranged") ? 1 : 0)
						.projectileRange(readProjectileRange(npc, "Npc patch definition " + npc.getInt("id")))
						.meleeOffense(npc.has("meleeOffense") ? (int)ifZeroReserve(npc.getInt("meleeOffense")) : 0)
						.rangedOffense(npc.has("rangedOffense") ? (int)ifZeroReserve(npc.getInt("rangedOffense")) : 0)
						.magicOffense(npc.has("magicOffense") ? (int)ifZeroReserve(npc.getInt("magicOffense")) : 0)
						.meleeDefense(npc.has("meleeDefense") ? (int)ifZeroReserve(npc.getInt("meleeDefense")) : 0)
						.rangedDefense(npc.has("rangedDefense") ? (int)ifZeroReserve(npc.getInt("rangedDefense")) : 0)
						.magicDefense(npc.has("magicDefense") ? (int)ifZeroReserve(npc.getInt("magicDefense")) : 0)
						.meleeDefenseMultiplier(npc.has("meleeDefenseMultiplier") ? npc.getDouble("meleeDefenseMultiplier") : -1.0D)
						.rangedDefenseMultiplier(npc.has("rangedDefenseMultiplier") ? npc.getDouble("rangedDefenseMultiplier") : -1.0D)
						.magicDefenseMultiplier(npc.has("magicDefenseMultiplier") ? npc.getDouble("magicDefenseMultiplier") : -1.0D)
						.meleeDefenseDivisor(npc.has("meleeDefenseDivisor") ? npc.getDouble("meleeDefenseDivisor") : -1.0D)
						.rangedDefenseDivisor(npc.has("rangedDefenseDivisor") ? npc.getDouble("rangedDefenseDivisor") : -1.0D)
						.magicDefenseDivisor(npc.has("magicDefenseDivisor") ? npc.getDouble("magicDefenseDivisor") : -1.0D)
						.combatLevel(npc.getInt("combatlvl"))
						.members(npc.getInt("isMembers") == 1)
						.attackable(npc.getInt("attackable") == 1)
						.aggressive(npc.getInt("aggressive") == 1);
				NPCDef toAdd = toAddBuild.build();
				npcsPatch.add(toAdd);
			}
		}
		catch (Exception e) {
			LOGGER.error(e);
		}
	}

	private void patchNpcs() {
		File file;
		if (getServer().getConfig().BASED_CONFIG_DATA < 85) {
			String filePath = getServer().getConfig().CONFIG_DIR + "/defs/NpcDefsPatch" + getServer().getConfig().BASED_CONFIG_DATA + ".json";
			file = new File(filePath);
			if (file.exists()) {
				LOGGER.info("Patching npc definitions...");
				loadPatchNpcs(filePath);
				try {
					for (int i = 0; i < npcsPatch.size(); i++) {
						npcs.set(i, patchObject(npcs.get(i), npcsPatch.get(i)));
					}
				} catch (Exception e) {
					LOGGER.error(e);
				}
			}
		}
	}

	private void applyOptionalNpcOverrides(String filename) {
		File file = new File(filename);
		if (!file.exists()) {
			return;
		}

		LOGGER.info("Applying npc overrides from " + file.getName() + "...");
		try {
			JSONObject object = new JSONObject(new String(Files.readAllBytes(Paths.get(filename))));
			JSONArray npcOverrides = object.getJSONArray("npcs");
			ArrayList<NPCDef> stagedNpcs = new ArrayList<>(npcs);
			Set<Integer> overriddenIds = new HashSet<>();
			for (int i = 0; i < npcOverrides.length(); i++) {
				JSONObject npc = npcOverrides.getJSONObject(i);
				validateOverrideFields(npc, MYWORLD_NPC_OVERRIDE_FIELDS, "npc", i);
				int npcId = npc.getInt("id");
				if (!overriddenIds.add(npcId)) {
					throw new IllegalArgumentException("Duplicate npc override id " + npcId);
				}
				if (npcId < 0 || npcId >= npcs.size() || npcs.get(npcId) == null) {
					throw new IllegalArgumentException("Npc override references unknown id " + npcId);
				}
				NPCDef staged = new NPCDef(npcs.get(npcId));
				if (npc.has("name")) staged.name = npc.getString("name");
				if (npc.has("description")) staged.description = npc.getString("description");
				if (npc.has("attack")) staged.attack = (int) ifZeroReserve(npc.getInt("attack"));
				if (npc.has("strength")) staged.strength = (int) ifZeroReserve(npc.getInt("strength"));
				if (npc.has("hits")) staged.hits = (int) ifZeroReserve(npc.getInt("hits"));
				if (npc.has("defense")) staged.defense = (int) ifZeroReserve(npc.getInt("defense"));
				if (npc.has("ranged")) staged.ranged = npc.getBoolean("ranged") ? 1 : 0;
				if (npc.has("projectileRange")) {
					staged.projectileRange = readProjectileRange(npc, "Npc override " + npcId);
				}
				if (npc.has("meleeOffense")) staged.meleeOffense = (int) ifZeroReserve(npc.getInt("meleeOffense"));
				if (npc.has("rangedOffense")) staged.rangedOffense = (int) ifZeroReserve(npc.getInt("rangedOffense"));
				if (npc.has("magicOffense")) staged.magicOffense = (int) ifZeroReserve(npc.getInt("magicOffense"));
				if (npc.has("meleeDefense")) staged.meleeDefense = (int) ifZeroReserve(npc.getInt("meleeDefense"));
				if (npc.has("rangedDefense")) staged.rangedDefense = (int) ifZeroReserve(npc.getInt("rangedDefense"));
				if (npc.has("magicDefense")) staged.magicDefense = (int) ifZeroReserve(npc.getInt("magicDefense"));
				if (npc.has("meleeDefenseMultiplier")) staged.meleeDefenseMultiplier = npc.getDouble("meleeDefenseMultiplier");
				if (npc.has("rangedDefenseMultiplier")) staged.rangedDefenseMultiplier = npc.getDouble("rangedDefenseMultiplier");
				if (npc.has("magicDefenseMultiplier")) staged.magicDefenseMultiplier = npc.getDouble("magicDefenseMultiplier");
				if (npc.has("meleeDefenseDivisor")) staged.meleeDefenseDivisor = npc.getDouble("meleeDefenseDivisor");
				if (npc.has("rangedDefenseDivisor")) staged.rangedDefenseDivisor = npc.getDouble("rangedDefenseDivisor");
				if (npc.has("magicDefenseDivisor")) staged.magicDefenseDivisor = npc.getDouble("magicDefenseDivisor");
				if (npc.has("combatlvl")) staged.combatLevel = (int) ifZeroReserve(npc.getInt("combatlvl"));
				if (npc.has("hairColour")) staged.hairColour = (int) ifZeroReserve(npc.getInt("hairColour"));
				if (npc.has("topColour")) staged.topColour = (int) ifZeroReserve(npc.getInt("topColour"));
				if (npc.has("bottomColour")) staged.bottomColour = (int) ifZeroReserve(npc.getInt("bottomColour"));
				if (npc.has("skinColour")) staged.skinColour = (int) ifZeroReserve(npc.getInt("skinColour"));
				stagedNpcs.set(npcId, staged);
			}
			npcs = stagedNpcs;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to apply npc overrides from " + file.getName(), e);
		}
	}

	private void loadNpcNames() {
		for (NPCDef npc : getServer().getConfig().BASED_CONFIG_DATA < 85 ? getServer().getEntityHandler().npcsPatch : getServer().getEntityHandler().npcs) {
			npcNames.add(npc.getName());
		}
		for (NPCDef npc : getServer().getConfig().BASED_CONFIG_DATA < 85 ? getServer().getEntityHandler().npcsPatch : getServer().getEntityHandler().npcs) {
			npcNamesLowerCase.add(npc.getName().toLowerCase());
		}
	}

	private void customNpcConditions() {
		if (getServer().getConfig().RIGHT_CLICK_TRADE) {
			for (int npcId : quickTradeNpcs) {
				npcs.get(npcId).setCommand1("Trade");
			}

			npcs.get(NpcId.GNOME_WAITER.id()).setCommand2("Trade");
			npcs.get(NpcId.BLURBERRY_BARMAN.id()).setCommand2("Trade");
			if (getServer().getConfig().WANT_RUNECRAFT && !getServer().getConfig().WANT_MYWORLD) {
				npcs.get(NpcId.AUBURY.id()).setCommand2("Trade");
			}
			else {
				npcs.get(NpcId.AUBURY.id()).setCommand1("Trade");
				npcs.get(NpcId.AUBURY.id()).setCommand2("Shop");
			}
		}
		if (getServer().getConfig().WANT_RUNECRAFT && !getServer().getConfig().WANT_MYWORLD) {
			npcs.get(NpcId.AUBURY.id()).setCommand1("Teleport/Trade"); //moves position depending on rune mysteries completion
		}
		// these although couldn't be pickpocket by client the command was allowed server side
		npcs.get(NpcId.GUARD_KHAZARD.id()).setCommand1("pickpocket");
		npcs.get(NpcId.GUARD_KHAZARD_MACE.id()).setCommand1("pickpocket");
	}

	private void loadItems(String filename) {
		try {
			JSONObject object = new JSONObject(new String(Files.readAllBytes(Paths.get(filename))));
			JSONArray itemDefs = object.getJSONArray(JSONObject.getNames(object)[0]);
			for (int i = 0; i < itemDefs.length(); i++) {
				JSONObject item = itemDefs.getJSONObject(i);
				ItemDefinition toAdd = new ItemDefinition(
					item.getInt("id"),
					item.getString("name"),
					item.getString("description"),
					item.getString("command").split(","),
					item.getInt("isFemaleOnly") == 1,
					item.getInt("isMembersOnly") == 1,
					item.getInt("isStackable") == 1,
					item.getInt("isUntradable") == 1,
					item.getInt("isWearable") == 1,
					item.getInt("appearanceID"),
					item.getInt("wearableID"),
					item.getInt("wearSlot"),
					item.getInt("requiredLevel"),
					item.getInt("requiredSkillID"),
					item.getLong("armourBonus"),
					item.getInt("weaponAimBonus"),
					item.getInt("weaponPowerBonus"),
					item.getInt("magicBonus"),
					item.getInt("prayerBonus"),
					item.getInt("basePrice"),
					item.getInt("isNoteable") == 1
				);

					if (toAdd.getCommand().length == 1 && "".equals(toAdd.getCommand()[0])) {
						toAdd.nullCommand();
					}
					if (item.has("meleeOffense")) toAdd.setMeleeOffense((int) ifZeroReserve(item.getInt("meleeOffense")));
					if (item.has("rangedOffense")) toAdd.setRangedOffense((int) ifZeroReserve(item.getInt("rangedOffense")));
					if (item.has("magicOffense")) toAdd.setMagicOffense((int) ifZeroReserve(item.getInt("magicOffense")));
					if (item.has("weaponSpeed")) toAdd.setWeaponSpeed((int) ifZeroReserve(item.getInt("weaponSpeed")));
					if (item.has("meleeDefense")) toAdd.setMeleeDefense((int) ifZeroReserve(item.getInt("meleeDefense")));
					if (item.has("rangedDefense")) toAdd.setRangedDefense((int) ifZeroReserve(item.getInt("rangedDefense")));
					if (item.has("magicDefense")) toAdd.setMagicDefense((int) ifZeroReserve(item.getInt("magicDefense")));
					addItemDefinition(toAdd);
				}
		}
		catch (Exception e) {
			LOGGER.error(e);
		}
	}

	private void addItemDefinition(ItemDefinition item) {
		while (items.size() <= item.getId()) {
			items.add(null);
		}
		items.set(item.getId(), item);
	}

	private void loadPatchItems(String filename) {
		try {
			JSONObject object = new JSONObject(new String(Files.readAllBytes(Paths.get(filename))));
			JSONArray itemPatchDefs = object.getJSONArray(JSONObject.getNames(object)[0]);
			for (int i = 0; i < itemPatchDefs.length(); i++) {
				JSONObject item = itemPatchDefs.getJSONObject(i);
				ItemDefinition.ItemDefinitionBuilder toAddBuild =
					new ItemDefinition.ItemDefinitionBuilder(item.getInt("id"), item.getString("name"))
						.description(item.getString("description"))
						.command(item.getString("command").split(","))
						.isStackable(item.getInt("isStackable") == 1)
						.defaultPrice(item.getInt("basePrice"));
					if (item.has("armourBonus")) toAddBuild.armourBonus(ifZeroReserve(item.getLong("armourBonus")));
					if (item.has("weaponAimBonus")) toAddBuild.weaponAimBonus((int)ifZeroReserve(item.getInt("weaponAimBonus")));
					if (item.has("weaponPowerBonus")) toAddBuild.weaponPowerBonus((int)ifZeroReserve(item.getInt("weaponPowerBonus")));
					if (item.has("magicBonus")) toAddBuild.magicBonus((int)ifZeroReserve(item.getInt("magicBonus")));
					if (item.has("meleeOffense")) toAddBuild.meleeOffense((int)ifZeroReserve(item.getInt("meleeOffense")));
					if (item.has("rangedOffense")) toAddBuild.rangedOffense((int)ifZeroReserve(item.getInt("rangedOffense")));
					if (item.has("magicOffense")) toAddBuild.magicOffense((int)ifZeroReserve(item.getInt("magicOffense")));
					if (item.has("weaponSpeed")) toAddBuild.weaponSpeed((int)ifZeroReserve(item.getInt("weaponSpeed")));
					if (item.has("meleeDefense")) toAddBuild.meleeDefense((int)ifZeroReserve(item.getInt("meleeDefense")));
					if (item.has("rangedDefense")) toAddBuild.rangedDefense((int)ifZeroReserve(item.getInt("rangedDefense")));
					if (item.has("magicDefense")) toAddBuild.magicDefense((int)ifZeroReserve(item.getInt("magicDefense")));
					if (item.has("prayerBonus")) toAddBuild.prayerBonus((int)ifZeroReserve(item.getInt("prayerBonus")));
				ItemDefinition toAdd = toAddBuild.build();
				if (toAdd.getCommand().length == 1 && "".equals(toAdd.getCommand()[0])) {
					toAdd.nullCommand();
				}
				itemsPatch.add(toAdd);
			}
		}
		catch (Exception e) {
			LOGGER.error(e);
		}
	}

	private long ifZeroReserve(long value) {
		return value == 0 ? ZERO_RESERVED : value;
	}

	private int readProjectileRange(JSONObject npc, String source) {
		if (!npc.has("projectileRange")) {
			return 0;
		}
		int projectileRange = npc.getInt("projectileRange");
		if (projectileRange < 1 || projectileRange > 15) {
			throw new IllegalArgumentException(source + " projectileRange must be between 1 and 15");
		}
		return projectileRange;
	}

	private void patchItems() {
		File file;
		if (getServer().getConfig().BASED_CONFIG_DATA < 85) {
			String filePath = getServer().getConfig().CONFIG_DIR + "/defs/ItemDefsPatch" + getServer().getConfig().BASED_CONFIG_DATA + ".json";
			file = new File(filePath);
			if (file.exists()) {
				LOGGER.info("Patching item definitions...");
				loadPatchItems(filePath);
				try {
					for (int i = 0; i < itemsPatch.size(); i++) {
						items.set(i, patchObject(items.get(i), itemsPatch.get(i)));
					}
				} catch (Exception e) {
					LOGGER.error(e);
				}
			}
		}
	}

	private void applyOptionalItemOverrides(String filename) {
		File file = new File(filename);
		if (!file.exists()) {
			return;
		}

		LOGGER.info("Applying item overrides from " + file.getName() + "...");
		try {
			JSONObject object = new JSONObject(new String(Files.readAllBytes(Paths.get(filename))));
			JSONArray itemOverrides = object.getJSONArray("items");
			ArrayList<ItemDefinition> stagedItems = new ArrayList<>(items);
			Set<Integer> overriddenIds = new HashSet<>();
			for (int i = 0; i < itemOverrides.length(); i++) {
				JSONObject item = itemOverrides.getJSONObject(i);
				validateOverrideFields(item, MYWORLD_ITEM_OVERRIDE_FIELDS, "item", i);
				int itemId = item.getInt("id");
				if (!overriddenIds.add(itemId)) {
					throw new IllegalArgumentException("Duplicate item override id " + itemId);
				}
				if (itemId < 0 || itemId >= items.size() || items.get(itemId) == null) {
					throw new IllegalArgumentException("Item override references unknown id " + itemId);
				}
				ItemDefinition staged = new ItemDefinition(items.get(itemId));
				if (item.has("name")) staged.setName(item.getString("name"));
				if (item.has("description")) staged.setDescription(item.getString("description"));
				if (item.has("meleeOffense")) staged.setMeleeOffense(item.getInt("meleeOffense"));
				if (item.has("rangedOffense")) staged.setRangedOffense(item.getInt("rangedOffense"));
				if (item.has("magicOffense")) staged.setMagicOffense(item.getInt("magicOffense"));
				if (item.has("weaponSpeed")) staged.setWeaponSpeed(item.getInt("weaponSpeed"));
				if (item.has("meleeDefense")) staged.setMeleeDefense(item.getInt("meleeDefense"));
				if (item.has("rangedDefense")) staged.setRangedDefense(item.getInt("rangedDefense"));
				if (item.has("magicDefense")) staged.setMagicDefense(item.getInt("magicDefense"));
				if (item.has("requiredLevel")) staged.setRequiredLevel(item.getInt("requiredLevel"));
				if (item.has("requiredSkillID")) staged.setRequiredSkillIndex(item.getInt("requiredSkillID"));
				if (item.has("isWearable")) staged.setWieldable(item.getInt("isWearable") != 0);
				if (item.has("appearanceID")) staged.setAppearanceId(item.getInt("appearanceID"));
				if (item.has("wearableID")) staged.setWearableId(item.getInt("wearableID"));
				if (item.has("wearSlot")) staged.setWieldPosition(item.getInt("wearSlot"));
				if (item.has("weaponAimBonus")) staged.setWeaponAimBonus(item.getInt("weaponAimBonus"));
				if (item.has("weaponPowerBonus")) staged.setWeaponPowerBonus(item.getInt("weaponPowerBonus"));
				if (item.has("armourBonus")) staged.setArmourBonus(item.getLong("armourBonus"));
				if (item.has("magicBonus")) staged.setMagicBonus(item.getInt("magicBonus"));
				if (item.has("prayerBonus")) staged.setPrayerBonus(item.getInt("prayerBonus"));
				if (item.has("basePrice")) staged.setDefaultPrice(item.getInt("basePrice"));
				stagedItems.set(itemId, staged);
			}
			items = stagedItems;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to apply item overrides from " + file.getName(), e);
		}
	}

	private void validateOverrideFields(JSONObject override, Set<String> allowedFields, String type, int index) {
		for (String field : override.keySet()) {
			if (!allowedFields.contains(field)) {
				throw new IllegalArgumentException(
					"Unexpected " + type + " override field '" + field + "' at index " + index
				);
			}
		}
	}

	private void customItemConditions() {
		if (getServer().getConfig().WANT_EQUIPMENT_TAB) {
			// Make arrows wieldable.
			int[] wieldableArrows = new int[] {
				ItemId.BRONZE_ARROWS.id(), ItemId.POISON_BRONZE_ARROWS.id(),
				ItemId.IRON_ARROWS.id(), ItemId.POISON_IRON_ARROWS.id(),
				ItemId.STEEL_ARROWS.id(), ItemId.POISON_STEEL_ARROWS.id(),
				ItemId.MITHRIL_ARROWS.id(), ItemId.POISON_MITHRIL_ARROWS.id(),
				ItemId.ADAMANTITE_ARROWS.id(), ItemId.POISON_ADAMANTITE_ARROWS.id(),
				ItemId.RUNE_ARROWS.id(), ItemId.POISON_RUNE_ARROWS.id(),
				ItemId.ICE_ARROWS.id(), ItemId.DRAGON_ARROWS.id(), ItemId.POISON_DRAGON_ARROWS.id(),
				ItemId.TIN_ARROWS.id(), ItemId.COPPER_ARROWS.id(),
				ItemId.TITAN_STEEL_ARROWS.id(), ItemId.ORICHALCUM_ARROWS.id()
			};
			for (int itemId : wieldableArrows) {
				items.get(itemId).setWieldable(true);
				items.get(itemId).setWearableId(RangeUtils.WEARABLE_ARROWS_ID);
				items.get(itemId).setWieldPosition(Equipment.EquipmentSlot.SLOT_AMMO.getIndex());
				items.get(itemId).setRequiredLevel(1);
				items.get(itemId).setRequiredSkillIndex(4);
			}

			// Make bolts wieldable.
			int[] wieldableBolts = new int[] {
				ItemId.CROSSBOW_BOLTS.id(), ItemId.POISON_CROSSBOW_BOLTS.id(),
				ItemId.COPPER_BOLTS.id(), ItemId.POISON_COPPER_BOLTS.id(),
				ItemId.BRONZE_BOLTS.id(), ItemId.POISON_BRONZE_BOLTS.id(),
				ItemId.IRON_BOLTS.id(), ItemId.POISON_IRON_BOLTS.id(),
				ItemId.STEEL_BOLTS.id(), ItemId.POISON_STEEL_BOLTS.id(),
				ItemId.MITHRIL_BOLTS.id(), ItemId.POISON_MITHRIL_BOLTS.id(),
				ItemId.TITAN_STEEL_BOLTS.id(), ItemId.POISON_TITAN_STEEL_BOLTS.id(),
				ItemId.ADAMANTITE_BOLTS.id(), ItemId.POISON_ADAMANTITE_BOLTS.id(),
				ItemId.ORICHALCUM_BOLTS.id(), ItemId.POISON_ORICHALCUM_BOLTS.id(),
				ItemId.RUNE_BOLTS.id(), ItemId.POISON_RUNE_BOLTS.id(),
				ItemId.POISON_DRAGON_BOLTS.id(), ItemId.DRAGON_BOLTS.id()
			};
			for (int itemId : wieldableBolts) {
				items.get(itemId).setWieldable(true);
				items.get(itemId).setWearableId(RangeUtils.WEARABLE_BOLTS_ID);
				items.get(itemId).setWieldPosition(Equipment.EquipmentSlot.SLOT_AMMO.getIndex());
				items.get(itemId).setRequiredLevel(1);
				items.get(itemId).setRequiredSkillIndex(4);
			}

			int[] mainhandBows = new int[] {
				ItemId.LONGBOW.id(), ItemId.SHORTBOW.id(),
				ItemId.PINE_LONGBOW.id(), ItemId.PINE_SHORTBOW.id(),
				ItemId.OAK_LONGBOW.id(), ItemId.OAK_SHORTBOW.id(),
				ItemId.WILLOW_LONGBOW.id(), ItemId.WILLOW_SHORTBOW.id(),
				ItemId.PALM_LONGBOW.id(), ItemId.PALM_SHORTBOW.id(),
				ItemId.MAPLE_LONGBOW.id(), ItemId.MAPLE_SHORTBOW.id(),
				ItemId.YEW_LONGBOW.id(), ItemId.YEW_SHORTBOW.id(),
				ItemId.EBONY_LONGBOW.id(), ItemId.EBONY_SHORTBOW.id(),
				ItemId.MAGIC_LONGBOW.id(), ItemId.MAGIC_SHORTBOW.id(),
				ItemId.BLOOD_LONGBOW.id(), ItemId.BLOOD_SHORTBOW.id(),
				ItemId.DRAGON_LONGBOW.id()
			};
			for (int itemId : mainhandBows) {
				items.get(itemId).setWieldPosition(Equipment.EquipmentSlot.SLOT_MAINHAND.getIndex());
			}

			// Make non-enchanted rings wearable
			items.get(ItemId.GOLD_RING.id()).setWieldable(true);
			items.get(ItemId.GOLD_RING.id()).setWearableId(1200);
			items.get(ItemId.GOLD_RING.id()).setWieldPosition(13);
			items.get(ItemId.GOLD_RING.id()).setRequiredSkillIndex(0);
			items.get(ItemId.SAPPHIRE_RING.id()).setWieldable(true);
			items.get(ItemId.SAPPHIRE_RING.id()).setWearableId(1200);
			items.get(ItemId.SAPPHIRE_RING.id()).setWieldPosition(13);
			items.get(ItemId.SAPPHIRE_RING.id()).setRequiredSkillIndex(0);
			items.get(ItemId.EMERALD_RING.id()).setWieldable(true);
			items.get(ItemId.EMERALD_RING.id()).setWearableId(1200);
			items.get(ItemId.EMERALD_RING.id()).setWieldPosition(13);
			items.get(ItemId.EMERALD_RING.id()).setRequiredSkillIndex(0);
			items.get(ItemId.RUBY_RING.id()).setWieldable(true);
			items.get(ItemId.RUBY_RING.id()).setWearableId(1200);
			items.get(ItemId.RUBY_RING.id()).setWieldPosition(13);
			items.get(ItemId.RUBY_RING.id()).setRequiredSkillIndex(0);
			items.get(ItemId.DIAMOND_RING.id()).setWieldable(true);
			items.get(ItemId.DIAMOND_RING.id()).setWearableId(1200);
			items.get(ItemId.DIAMOND_RING.id()).setWieldPosition(13);
			items.get(ItemId.DIAMOND_RING.id()).setRequiredSkillIndex(0);
			items.get(ItemId.DRAGONSTONE_RING.id()).setWieldable(true);
			items.get(ItemId.DRAGONSTONE_RING.id()).setWearableId(1200);
			items.get(ItemId.DRAGONSTONE_RING.id()).setWieldPosition(13);
			items.get(ItemId.DRAGONSTONE_RING.id()).setRequiredSkillIndex(0);
		}

		for (Integer itemId : RangeUtils.THROWING_DARTS) {
			items.get(itemId).setStackable(true);
			items.get(itemId).setNoteable(false);
		}
		for (Integer itemId : RangeUtils.THROWING_KNIVES) {
			items.get(itemId).setStackable(true);
			items.get(itemId).setNoteable(false);
		}

		// This enables overrides for existing authentic items so replacement custom equipment animations may be used instead
		if (getServer().getConfig().WANT_CUSTOM_SPRITES) {
			// Set custom appearance IDs in ascending order.
			items.get(ItemId.BRONZE_AXE.id()).setAppearanceId(230);
			items.get(ItemId.IRON_AXE.id()).setAppearanceId(231);
			items.get(ItemId.STEEL_AXE.id()).setAppearanceId(232);
			items.get(ItemId.MITHRIL_AXE.id()).setAppearanceId(233);
			items.get(ItemId.ADAMANTITE_AXE.id()).setAppearanceId(234);
			items.get(ItemId.RUNE_AXE.id()).setAppearanceId(235);
			items.get(ItemId.BLACK_AXE.id()).setAppearanceId(236);

			items.get(ItemId.BRONZE_KITE_SHIELD.id()).setAppearanceId(237);
			items.get(ItemId.IRON_KITE_SHIELD.id()).setAppearanceId(238);
			items.get(ItemId.STEEL_KITE_SHIELD.id()).setAppearanceId(239);
			items.get(ItemId.MITHRIL_KITE_SHIELD.id()).setAppearanceId(240);
			items.get(ItemId.ADAMANTITE_KITE_SHIELD.id()).setAppearanceId(241);
			items.get(ItemId.RUNE_KITE_SHIELD.id()).setAppearanceId(242);
			items.get(ItemId.BLACK_KITE_SHIELD.id()).setAppearanceId(243);

			items.get(ItemId.DRAGON_SQUARE_SHIELD.id()).setAppearanceId(244);

			items.get(ItemId.DRAGON_MEDIUM_HELMET.id()).setAppearanceId(245);

			items.get(ItemId.BRONZE_PLATED_SKIRT.id()).setAppearanceId(246);
			items.get(ItemId.IRON_PLATED_SKIRT.id()).setAppearanceId(247);
			items.get(ItemId.STEEL_PLATED_SKIRT.id()).setAppearanceId(248);
			items.get(ItemId.MITHRIL_PLATED_SKIRT.id()).setAppearanceId(249);
			items.get(ItemId.ADAMANTITE_PLATED_SKIRT.id()).setAppearanceId(250);
			items.get(ItemId.RUNE_SKIRT.id()).setAppearanceId(251);
			items.get(ItemId.BLACK_PLATED_SKIRT.id()).setAppearanceId(252);

			items.get(ItemId.LONGBOW.id()).setAppearanceId(253);
			items.get(ItemId.SHORTBOW.id()).setAppearanceId(253);
			items.get(ItemId.OAK_LONGBOW.id()).setAppearanceId(254);
			items.get(ItemId.OAK_SHORTBOW.id()).setAppearanceId(254);
			items.get(ItemId.WILLOW_LONGBOW.id()).setAppearanceId(255);
			items.get(ItemId.WILLOW_SHORTBOW.id()).setAppearanceId(255);
			items.get(ItemId.MAPLE_LONGBOW.id()).setAppearanceId(256);
			items.get(ItemId.MAPLE_SHORTBOW.id()).setAppearanceId(256);
			items.get(ItemId.YEW_LONGBOW.id()).setAppearanceId(257);
			items.get(ItemId.YEW_SHORTBOW.id()).setAppearanceId(257);
			items.get(ItemId.MAGIC_LONGBOW.id()).setAppearanceId(258);
			items.get(ItemId.MAGIC_SHORTBOW.id()).setAppearanceId(258);

			items.get(ItemId.BRONZE_SHORT_SWORD.id()).setAppearanceId(259);
			items.get(ItemId.IRON_SHORT_SWORD.id()).setAppearanceId(260);
			items.get(ItemId.STEEL_SHORT_SWORD.id()).setAppearanceId(261);
			items.get(ItemId.MITHRIL_SHORT_SWORD.id()).setAppearanceId(262);
			items.get(ItemId.ADAMANTITE_SHORT_SWORD.id()).setAppearanceId(263);
			items.get(ItemId.RUNE_SHORT_SWORD.id()).setAppearanceId(264);
			items.get(ItemId.BLACK_SHORT_SWORD.id()).setAppearanceId(265);

			items.get(ItemId.BRONZE_DAGGER.id()).setAppearanceId(266);
			items.get(ItemId.IRON_DAGGER.id()).setAppearanceId(267);
			items.get(ItemId.STEEL_DAGGER.id()).setAppearanceId(268);
			items.get(ItemId.MITHRIL_DAGGER.id()).setAppearanceId(269);
			items.get(ItemId.ADAMANTITE_DAGGER.id()).setAppearanceId(270);
			items.get(ItemId.RUNE_DAGGER.id()).setAppearanceId(271);
			items.get(ItemId.BLACK_DAGGER.id()).setAppearanceId(272);
			items.get(ItemId.DRAGON_DAGGER.id()).setAppearanceId(469);

			items.get(ItemId.POISONED_BRONZE_DAGGER.id()).setAppearanceId(273);
			items.get(ItemId.POISONED_IRON_DAGGER.id()).setAppearanceId(274);
			items.get(ItemId.POISONED_STEEL_DAGGER.id()).setAppearanceId(275);
			items.get(ItemId.POISONED_MITHRIL_DAGGER.id()).setAppearanceId(276);
			items.get(ItemId.POISONED_ADAMANTITE_DAGGER.id()).setAppearanceId(277);
			items.get(ItemId.POISONED_RUNE_DAGGER.id()).setAppearanceId(278);
			items.get(ItemId.POISONED_BLACK_DAGGER.id()).setAppearanceId(279);
			items.get(ItemId.POISONED_DRAGON_DAGGER.id()).setAppearanceId(470);

			items.get(ItemId.BRONZE_2_HANDED_SWORD.id()).setAppearanceId(280);
			items.get(ItemId.IRON_2_HANDED_SWORD.id()).setAppearanceId(281);
			items.get(ItemId.STEEL_2_HANDED_SWORD.id()).setAppearanceId(282);
			items.get(ItemId.MITHRIL_2_HANDED_SWORD.id()).setAppearanceId(283);
			items.get(ItemId.ADAMANTITE_2_HANDED_SWORD.id()).setAppearanceId(284);
			items.get(ItemId.RUNE_2_HANDED_SWORD.id()).setAppearanceId(285);
			items.get(ItemId.BLACK_2_HANDED_SWORD.id()).setAppearanceId(286);

			items.get(ItemId.BRONZE_SPEAR.id()).setAppearanceId(388);
			items.get(ItemId.IRON_SPEAR.id()).setAppearanceId(389);
			items.get(ItemId.STEEL_SPEAR.id()).setAppearanceId(390);
			items.get(ItemId.MITHRIL_SPEAR.id()).setAppearanceId(391);
			items.get(ItemId.ADAMANTITE_SPEAR.id()).setAppearanceId(392);
			items.get(ItemId.RUNE_SPEAR.id()).setAppearanceId(220);

			items.get(ItemId.POISONED_BRONZE_SPEAR.id()).setAppearanceId(388);
			items.get(ItemId.POISONED_IRON_SPEAR.id()).setAppearanceId(389);
			items.get(ItemId.POISONED_STEEL_SPEAR.id()).setAppearanceId(390);
			items.get(ItemId.POISONED_MITHRIL_SPEAR.id()).setAppearanceId(391);
			items.get(ItemId.POISONED_ADAMANTITE_SPEAR.id()).setAppearanceId(392);
			items.get(ItemId.POISONED_RUNE_SPEAR.id()).setAppearanceId(393);

			items.get(ItemId.SAPPHIRE_NECKLACE.id()).setAppearanceId(405);
			items.get(ItemId.SAPPHIRE_AMULET.id()).setAppearanceId(406);
			items.get(ItemId.EMERALD_NECKLACE.id()).setAppearanceId(407);
			items.get(ItemId.EMERALD_AMULET.id()).setAppearanceId(408);
			items.get(ItemId.RUBY_NECKLACE.id()).setAppearanceId(409);
			items.get(ItemId.RUBY_AMULET.id()).setAppearanceId(410);
			items.get(ItemId.DIAMOND_NECKLACE.id()).setAppearanceId(411);
			items.get(ItemId.DIAMOND_AMULET.id()).setAppearanceId(412);
			items.get(ItemId.DRAGONSTONE_NECKLACE.id()).setAppearanceId(413);
			items.get(ItemId.DRAGONSTONE_AMULET.id()).setAppearanceId(414);
			items.get(ItemId.CHARGED_DRAGONSTONE_AMULET.id()).setAppearanceId(414);

			items.get(ItemId.ANNAS_SILVER_NECKLACE.id()).setAppearanceId(415);
			items.get(ItemId.BEADS_OF_THE_DEAD.id()).setAppearanceId(416);
			items.get(ItemId.PENDANT_OF_LUCIEN.id()).setAppearanceId(417);
			items.get(ItemId.PENDANT_OF_ARMADYL.id()).setAppearanceId(418);
			items.get(ItemId.GLARIALS_AMULET.id()).setAppearanceId(419);

			items.get(ItemId.HOLY_SYMBOL_OF_SARADOMIN.id()).setAppearanceId(420);
			items.get(ItemId.UNHOLY_SYMBOL_OF_ZAMORAK.id()).setAppearanceId(421);

			items.get(ItemId.BATTLESTAFF_OF_AIR.id()).setAppearanceId(422);
			items.get(ItemId.BATTLESTAFF_OF_WATER.id()).setAppearanceId(423);
			items.get(ItemId.BATTLESTAFF_OF_EARTH.id()).setAppearanceId(424);
			items.get(ItemId.BATTLESTAFF_OF_FIRE.id()).setAppearanceId(425);

			items.get(ItemId.ENCHANTED_BATTLESTAFF_OF_AIR.id()).setAppearanceId(422);
			items.get(ItemId.ENCHANTED_BATTLESTAFF_OF_WATER.id()).setAppearanceId(423);
			items.get(ItemId.ENCHANTED_BATTLESTAFF_OF_EARTH.id()).setAppearanceId(424);
			items.get(ItemId.ENCHANTED_BATTLESTAFF_OF_FIRE.id()).setAppearanceId(425);

			items.get(ItemId.BRONZE_CHAIN_MAIL_LEGS.id()).setAppearanceId(442);
			items.get(ItemId.IRON_CHAIN_MAIL_LEGS.id()).setAppearanceId(443);
			items.get(ItemId.STEEL_CHAIN_MAIL_LEGS.id()).setAppearanceId(444);
			items.get(ItemId.MITHRIL_CHAIN_MAIL_LEGS.id()).setAppearanceId(445);
			items.get(ItemId.ADAMANTITE_CHAIN_MAIL_LEGS.id()).setAppearanceId(446);
			items.get(ItemId.RUNE_CHAIN_MAIL_LEGS.id()).setAppearanceId(447);
			items.get(ItemId.BLACK_CHAIN_MAIL_LEGS.id()).setAppearanceId(448);

			items.get(ItemId.ZAMORAK_CAPE.id()).setAppearanceId(465);
			items.get(ItemId.SARADOMIN_CAPE.id()).setAppearanceId(464);
			items.get(ItemId.GUTHIX_CAPE.id()).setAppearanceId(463);

			items.get(ItemId.DRAGON_CROSSBOW.id()).setAppearanceId(471);
			items.get(ItemId.DRAGON_LONGBOW.id()).setAppearanceId(472);

			items.get(ItemId.BRONZE_SCIMITAR.id()).setAppearanceId(477);
			items.get(ItemId.IRON_SCIMITAR.id()).setAppearanceId(478);
			items.get(ItemId.STEEL_SCIMITAR.id()).setAppearanceId(479);
			items.get(ItemId.BLACK_SCIMITAR.id()).setAppearanceId(480);
			items.get(ItemId.MITHRIL_SCIMITAR.id()).setAppearanceId(481);
			items.get(ItemId.ADAMANTITE_SCIMITAR.id()).setAppearanceId(482);
			items.get(ItemId.RUNE_SCIMITAR.id()).setAppearanceId(483);

			items.get(ItemId.YOYO.id()).setAppearanceId(485);
		}

		if (getServer().getConfig().WANT_MYWORLD) {
			if (getServer().getConfig().WANT_CUSTOM_SPRITES) {
				items.get(ItemId.FISHING_ROD.id()).setAppearanceId(784);
				items.get(ItemId.PINE_FISHING_ROD.id()).setAppearanceId(784);
				items.get(ItemId.OAK_FISHING_ROD.id()).setAppearanceId(784);
				items.get(ItemId.WILLOW_FISHING_ROD.id()).setAppearanceId(784);
				items.get(ItemId.PALM_FISHING_ROD.id()).setAppearanceId(784);
				items.get(ItemId.MAPLE_FISHING_ROD.id()).setAppearanceId(784);
				items.get(ItemId.YEW_FISHING_ROD.id()).setAppearanceId(784);
				items.get(ItemId.EBONY_FISHING_ROD.id()).setAppearanceId(784);
				items.get(ItemId.MAGIC_FISHING_ROD.id()).setAppearanceId(784);
				items.get(ItemId.BLOOD_FISHING_ROD.id()).setAppearanceId(784);

				items.get(ItemId.PINE_LONGBOW.id()).setAppearanceId(785);
				items.get(ItemId.PINE_SHORTBOW.id()).setAppearanceId(785);
				items.get(ItemId.PALM_LONGBOW.id()).setAppearanceId(786);
				items.get(ItemId.PALM_SHORTBOW.id()).setAppearanceId(786);
				items.get(ItemId.EBONY_LONGBOW.id()).setAppearanceId(787);
				items.get(ItemId.EBONY_SHORTBOW.id()).setAppearanceId(787);
				items.get(ItemId.BLOOD_LONGBOW.id()).setAppearanceId(788);
				items.get(ItemId.BLOOD_SHORTBOW.id()).setAppearanceId(788);

				applyMyWorldStaffAppearanceOverrides();
				applyMyWorldPickaxeAppearanceOverrides();
				applyMyWorldShearsAppearanceOverrides();
				setItemAppearance(ItemId.DEMON_PITCHFORK.id(), DEMON_PITCHFORK_APPEARANCE_ID);
				setItemAppearance(ItemId.SARADOMIN_MACE.id(), SARADOMIN_MACE_APPEARANCE_ID);
				setItemAppearance(ItemId.ZAMORAK_MACE.id(), ZAMORAK_MACE_APPEARANCE_ID);
				setItemAppearance(ItemId.GUTHIX_MACE.id(), GUTHIX_MACE_APPEARANCE_ID);
			} else {
				items.get(ItemId.FISHING_ROD.id()).setAppearanceId(123);
				items.get(ItemId.PINE_FISHING_ROD.id()).setAppearanceId(123);
				items.get(ItemId.OAK_FISHING_ROD.id()).setAppearanceId(123);
				items.get(ItemId.WILLOW_FISHING_ROD.id()).setAppearanceId(123);
				items.get(ItemId.PALM_FISHING_ROD.id()).setAppearanceId(123);
				items.get(ItemId.MAPLE_FISHING_ROD.id()).setAppearanceId(123);
				items.get(ItemId.YEW_FISHING_ROD.id()).setAppearanceId(123);
				items.get(ItemId.EBONY_FISHING_ROD.id()).setAppearanceId(123);
				items.get(ItemId.MAGIC_FISHING_ROD.id()).setAppearanceId(123);
				items.get(ItemId.BLOOD_FISHING_ROD.id()).setAppearanceId(123);
			}
		}
	}

	private void applyMyWorldPickaxeAppearanceOverrides() {
		for (int i = 0; i < MYWORLD_PICKAXE_IDS.length; i++) {
			setItemAppearance(MYWORLD_PICKAXE_IDS[i], MYWORLD_PICKAXE_APPEARANCE_START + i);
		}
	}

	private void applyMyWorldShearsAppearanceOverrides() {
		for (int shearsId : MYWORLD_SHEARS_IDS) {
			setItemAppearance(shearsId, MYWORLD_SHEARS_APPEARANCE_ID);
		}
	}

	private void applyMyWorldStaffAppearanceOverrides() {
		for (int woodIndex = 0; woodIndex < MYWORLD_STAFF_BASE_IDS.length; woodIndex++) {
			setItemAppearance(MYWORLD_STAFF_BASE_IDS[woodIndex], MYWORLD_STAFF_BASE_APPEARANCE_START + woodIndex);
		}
		int appearanceId = MYWORLD_RUNE_STAFF_APPEARANCE_START;
		for (int[] runeStaffIds : MYWORLD_RUNE_STAFF_IDS) {
			for (int staffId : runeStaffIds) {
				setItemAppearance(staffId, appearanceId++);
			}
		}
	}

	private void setItemAppearance(int itemId, int appearanceId) {
		if (itemId >= 0 && itemId < items.size() && items.get(itemId) != null) {
			items.get(itemId).setAppearanceId(appearanceId);
		}
	}

	/**
	 * @param id the npcs ID
	 * @return the CerterDef for the given npc
	 */
	public CerterDef getCerterDef(int id) {
		return certers.get(id);
	}

	/**
	 * @return the ItemCraftingDef for the requested item
	 */
	public ItemCraftingDef getCraftingDef(int id) {
		return itemCrafting.getOrDefault(id, null);
	}

	/**
	 * @param id the entities ID
	 * @return the DoorDef with the given ID
	 */
	public DoorDef getDoorDef(int id) {
		if (id < 0 || id >= doors.length) {
			return null;
		}
		return doors[id];
	}

	/**
	 * @return the FiremakingDef for the given log
	 */
	public FiremakingDef getFiremakingDef(int id) {
		return firemaking.get(id);
	}

	/**
	 * @param id the entities ID
	 * @return the GameObjectDef with the given ID
	 */
	public GameObjectDef getGameObjectDef(int id) {
		if (id < 0 || id >= gameObjects.length) {
			return null;
		}
		return gameObjects[id];
	}

	/**
	 * @param type items type
	 * @return the types of items affected
	 */
	public int[] getAffectedTypes(int type) {
		int[] affectedTypes = itemAffectedTypes.get(type);
		if (affectedTypes != null) {
			return affectedTypes;
		}
		return new int[0];
	}


	/**
	 * @return the ItemArrowHeadDef for the given arrow
	 */
	public ItemArrowHeadDef getItemArrowHeadDef(int id) {
		return arrowHeads.get(id);
	}

	/**
	 * @return the ItemBowStringDef for the given bow
	 */
	public ItemBowStringDef getItemBowStringDef(int id) {
		return bowString.get(id);
	}

	/**
	 * @param id the entities ID
	 * @return the ItemCookingDef with the given ID
	 */
	public ItemCookingDef getItemCookingDef(int id) {
		return itemCooking.get(id);
	}

	/**
	 * @param id the entities ID
	 * @return the ItemPerfectCookingDef with the given ID
	 */
	public ItemPerfectCookingDef getItemPerfectCookingDef(int id) {
		return itemPerfectCooking.get(id);
	}

	/**
	 * @return the ItemDartTipDef for the given tip
	 */
	public ItemDartTipDef getItemDartTipDef(int id) {
		return dartTips.get(id);
	}

	public int getItemCount() {
		return items.size();
	}

	/**
	 * @param id the entities ID
	 * @return the ItemDef with the given ID
	 */
	public ItemDefinition getItemDef(int id) {
		if (id < 0 || id >= items.size()) {
			return null;
		}
		return items.get(id);
	}

	/**
	 * @param id items id
	 * @return the amount eating the item should heal
	 */
	public int getItemEdibleHeals(int id) {
		Integer heals = itemEdibleHeals.get(id);
		if (heals != null) {
			return heals;
		}
		return 0;
	}

	/**
	 * @return the ItemGemDef for the given gem
	 */
	public ItemGemDef getItemGemDef(int id) {
		return gems.get(id);
	}

	/**
	 * @param id the entities ID
	 * @return the ItemHerbDef with the given ID
	 */
	public ItemHerbDef getItemHerbDef(int id) {
		return itemHerb.get(id);
	}

	/**
	 * @return the ItemHerbSecond for the given second ingredient
	 */
	public ItemHerbSecond getItemHerbSecond(int secondID, int unfinishedID) {
		for (ItemHerbSecond def : herbSeconds) {
			if (def.getSecondID() == secondID && def.getUnfinishedID() == unfinishedID) {
				return def;
			}
		}
		return null;
	}

	/**
	 * @return the ItemLogCutDef for the given log
	 */
	public ItemLogCutDef getItemLogCutDef(int id) {
		return logCut.get(id);
	}

	/**
	 * @param id the entities ID
	 * @return the ItemSmeltingDef with the given ID
	 */
	public ItemSmeltingDef getItemSmeltingDef(int id) {
		return itemSmelting.get(id);
	}

	/**
	 * @param id the entities ID
	 * @return the ItemUnIdentHerbDef with the given ID
	 */
	public ItemUnIdentHerbDef getItemUnIdentHerbDef(int id) {
		return itemUnIdentHerb.get(id);
	}

	/**
	 * @param id the entities ID
	 * @return the NPCDef with the given ID
	 */
	public NPCDef getNpcDef(int id) {
		if (id < 0 || id >= npcs.size()) {
			return null;
		}
		return npcs.get(id);
	}

	/**
	 * @param name the entities name
	 * @return the NPC name with the given NPC name, if found, otherwise ""
	 */
	public String getNpcName(String name) {
		if (npcNames.contains(name)) {
			return name;
		}
		return "";
	}

	/**
	 * @param name the entities lower-case name
	 * @return the lower-case NPC name with the given lower-case NPC name, if found, otherwise ""
	 */
	public String getNpcNameLowerCase(String name) {
		if (npcNamesLowerCase.contains(name)) {
			return name;
		}
		return "";
	}

	/**
	 * @param id the entities ID
	 * @return the ObjectFishingDef with the given ID
	 */
	public ObjectFishingDef getObjectFishingDef(int id, int click) {
		ObjectFishingDef[] defs = objectFishing.get(id);
		if (defs == null) {
			return null;
		}
		return defs[click];
	}

	/**
	 * @param id the entities ID
	 * @return the ObjectHarvestingDef with the given ID
	 */
	public ObjectHarvestingDef getObjectHarvestingDef(int id) {
		return objectHarvesting.get(id);
	}

	/**
	 * @param id the entities ID
	 * @return the ObjectMiningDef with the given ID
	 */
	public ObjectMiningDef getObjectMiningDef(int id) {
		return objectMining.get(id);
	}

	/**
	 * @param location the point we are currently at
	 * @return the point we should be teleported to
	 */
	public TelePoint getObjectTelePoint(Point location, String command) {
		TelePoint point = objectTelePoints.get(location);
		if (point == null) {
			return null;
		}
		if (command == null || point.getCommand().equalsIgnoreCase(command)) {
			return point;
		}
		return null;
	}

	/**
	 * Looks up a legacy-configured object telepoint from an authoritative source
	 * location. Unrepresentable native worlds or levels cannot have entries in
	 * the packed telepoint table and therefore return no match.
	 */
	public TelePoint getObjectTelePoint(
		final WorldLocation location,
		final String command) {
		try {
			return getObjectTelePoint(
				LegacyPackedPointAdapter.toLegacyPoint(location), command);
		} catch (IllegalArgumentException unrepresentableLocation) {
			return null;
		}
	}

	/**
	 * Projects an existing object telepoint into the directed layered contract.
	 * Existing command matching and packed storage remain authoritative.
	 */
	public WorldObjectTransition getObjectWorldTransition(Point location, String command) {
		TelePoint destination = getObjectTelePoint(location, command);
		if (destination == null) {
			return null;
		}
		return WorldObjectTransition.fromLegacyPoints(
			location, destination, destination.getCommand());
	}

	public ObjectRunecraftDef getObjectRunecraftDef(int id) {
		return objectRunecraft.get(id);
	}
	/**
	 * @param id the entities ID
	 * @return the ObjectWoodcuttingDef with the given ID
	 */
	public ObjectWoodcuttingDef getObjectWoodcuttingDef(int id) {
		return objectWoodcutting.get(id);
	}

	/**
	 * @param id the entities ID
	 * @return the PrayerDef with the given ID
	 */
	public PrayerDef getPrayerDef(int id) {
		if (id < 0 || id >= prayers.length) {
			return null;
		}
		return prayers[id];
	}

	/**
	 * @return the ItemSmithingDef for the requested item
	 */
	public ItemSmithingDef getSmithingDef(int id) {
		if (id < 0 || id >= itemSmithing.length) {
			return null;
		}
		return itemSmithing[id];
	}

	/**
	 * @return the ItemSmithingDef for the requested item
	 */
	public ItemSmithingDef getSmithingDefbyID(int itemID) {
		for (ItemSmithingDef i : itemSmithing) {
			if (i.itemID == itemID)
				return i;
		}
		return null;
	}

	/**
	 * @param id the entities ID
	 * @return the SpellDef with the given ID
	 */
	@Deprecated
	public SpellDef getSpellDef(int id) {
		if (id < 0 || id >= spells.length) {
			return null;
		}
		return spells[id];
	}

	/**
	 * Return spell definition from Spell enum
	 * @param spellEnum
	 * @return
	 */
	public SpellDef getSpellDef(Spells spellEnum) {
		Integer spellId = Constants.spellMap.get(spellEnum);
		if (spellId != null) {
			SpellDef spell = getSpellDef(spellId);
			if (spell != null) {
				return spell;
			}
		}

		SpellDef result = null;
		String defName;
		for (SpellDef spell : spells) {
			defName = spell.getName().replaceAll("[-()]", "").replaceAll(" ", "_");
			if (spellEnum.toString().equalsIgnoreCase(defName)) {
				result = spell;
				break;
			}
		}
		return result;
	}

	/**
	 * @param id the entities ID
	 * @return the TileDef with the given ID
	 */
	public TileDef getTileDef(int id) {
		if (id < 0 || id >= tiles.length) {
			return null;
		}
		return tiles[id];
	}

	public SkillDef getSkillDef(String skillName) {
		return null;
	}

	public Server getServer() {
		return server;
	}

	public PersistenceManager getPersistenceManager() {
		return persistenceManager;
	}
}
