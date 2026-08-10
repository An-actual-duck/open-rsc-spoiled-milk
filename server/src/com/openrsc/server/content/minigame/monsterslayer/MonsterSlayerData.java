package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.external.NPCDef;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Category;
import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Contact;
import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Family;
import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Reward;
import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Shop;
import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task;

/** Loads and validates the independently authored Monster Slayer definitions. */
public final class MonsterSlayerData {
	public static final int SCHEMA_VERSION = 1;
	public static final int MAX_TASK_KILLS = 10_000;
	public static final long MAX_TASK_POINTS = 10_000L;
	public static final int MAX_TASK_WEIGHT = 1_000;
	public static final int MAX_REWARD_AMOUNT = 10_000;

	private static final Logger LOGGER = LogManager.getLogger();
	private static final Pattern STABLE_KEY =
		Pattern.compile("[a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)*");

	private final Map<String, Family> families;
	private final Map<String, Contact> contacts;
	private final Map<String, Task> tasks;
	private final Map<String, Shop> shops;

	private MonsterSlayerData(Map<String, Family> families, Map<String, Contact> contacts,
						   Map<String, Task> tasks, Map<String, Shop> shops) {
		this.families = immutableMap(families);
		this.contacts = immutableMap(contacts);
		this.tasks = immutableMap(tasks);
		this.shops = immutableMap(shops);
	}

	public static MonsterSlayerData loadForWorld(final World world) {
		Path path = Paths.get(world.getServer().getConfig().CONFIG_DIR,
			"defs", "extras", "MonsterSlayer.json");
		MonsterSlayerData data = load(path, new ReferenceCatalog() {
			@Override
			public boolean npcExists(int npcId) {
				return world.getServer().getEntityHandler().getNpcDef(npcId) != null;
			}

			@Override
			public boolean npcAttackable(int npcId) {
				NPCDef definition = world.getServer().getEntityHandler().getNpcDef(npcId);
				return definition != null && definition.isAttackable();
			}

			@Override
			public boolean npcSpawned(int npcId) {
				for (Npc npc : world.getNpcs()) {
					if (npc.getID() == npcId) {
						return true;
					}
				}
				return false;
			}

			@Override
			public boolean itemExists(int itemId) {
				ItemDefinition definition = world.getServer().getEntityHandler().getItemDef(itemId);
				return definition != null;
			}
		});
		LOGGER.info("Loaded {} Monster Slayer families, {} contacts, {} tasks, and {} shops from {}",
			data.families.size(), data.contacts.size(), data.tasks.size(), data.shops.size(), path);
		return data;
	}

	public static MonsterSlayerData load(Path path, ReferenceCatalog catalog) {
		if (path == null || catalog == null) {
			throw new IllegalArgumentException("Monster Slayer path and reference catalog are required");
		}
		try {
			String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			return parse(new JSONObject(json), catalog);
		} catch (RuntimeException ex) {
			throw new IllegalArgumentException("Invalid Monster Slayer definitions at " + path + ": "
				+ ex.getMessage(), ex);
		} catch (Exception ex) {
			throw new IllegalArgumentException("Unable to load Monster Slayer definitions at " + path, ex);
		}
	}

	static MonsterSlayerData parse(JSONObject root, ReferenceCatalog catalog) {
		requireFields(root, "root", "schemaVersion", "families", "contacts", "shops");
		if (root.getInt("schemaVersion") != SCHEMA_VERSION) {
			throw new IllegalArgumentException("Unsupported schemaVersion: " + root.getInt("schemaVersion"));
		}

		LinkedHashMap<String, Family> families = parseFamilies(root.getJSONArray("families"), catalog);
		LinkedHashMap<String, Task> tasks = new LinkedHashMap<String, Task>();
		LinkedHashMap<String, Contact> contacts = parseContacts(
			root.getJSONArray("contacts"), catalog, families, tasks);
		LinkedHashMap<String, Shop> shops = parseShops(root.getJSONArray("shops"), catalog);
		return new MonsterSlayerData(families, contacts, tasks, shops);
	}

	private static LinkedHashMap<String, Family> parseFamilies(JSONArray array, ReferenceCatalog catalog) {
		LinkedHashMap<String, Family> result = new LinkedHashMap<String, Family>();
		Map<Integer, String> npcOwners = new HashMap<Integer, String>();
		for (int index = 0; index < array.length(); index++) {
			JSONObject object = array.getJSONObject(index);
			requireFields(object, "family", "key", "displayName", "npcIds");
			String key = stableKey(object.getString("key"), "family");
			String displayName = nonempty(object.getString("displayName"), "family displayName");
			JSONArray npcArray = object.getJSONArray("npcIds");
			if (npcArray.length() == 0) {
				throw new IllegalArgumentException("Family " + key + " has no NPC IDs");
			}
			List<Integer> npcIds = new ArrayList<Integer>();
			boolean spawned = false;
			for (int npcIndex = 0; npcIndex < npcArray.length(); npcIndex++) {
				int npcId = npcArray.getInt(npcIndex);
				if (!catalog.npcExists(npcId)) {
					throw new IllegalArgumentException("Family " + key + " has unknown NPC " + npcId);
				}
				if (!catalog.npcAttackable(npcId)) {
					throw new IllegalArgumentException("Family " + key + " has nonattackable NPC " + npcId);
				}
				String previousOwner = npcOwners.put(npcId, key);
				if (previousOwner != null) {
					throw new IllegalArgumentException("NPC " + npcId + " belongs to both "
						+ previousOwner + " and " + key);
				}
				npcIds.add(npcId);
				spawned |= catalog.npcSpawned(npcId);
			}
			if (!spawned) {
				throw new IllegalArgumentException("Family " + key + " has no active spawn");
			}
			putUnique(result, key, new Family(key, displayName, npcIds), "family");
		}
		if (result.isEmpty()) {
			throw new IllegalArgumentException("Monster Slayer requires families");
		}
		return result;
	}

	private static LinkedHashMap<String, Contact> parseContacts(JSONArray array, ReferenceCatalog catalog,
			Map<String, Family> families, Map<String, Task> allTasks) {
		if (array.length() != MonsterSlayerChallenge.values().length) {
			throw new IllegalArgumentException("Monster Slayer requires exactly six contacts");
		}
		LinkedHashMap<String, Contact> result = new LinkedHashMap<String, Contact>();
		Set<Integer> contactNpcIds = new HashSet<Integer>();
		Set<MonsterSlayerChallenge> challenges = new HashSet<MonsterSlayerChallenge>();
		for (int index = 0; index < array.length(); index++) {
			JSONObject object = array.getJSONObject(index);
			requireFields(object, "contact", "key", "npcId", "challenge", "requiredRank",
				"awardedRank", "mandatoryTasks", "repeatableTasks");
			String key = stableKey(object.getString("key"), "contact");
			int npcId = object.getInt("npcId");
			if (!catalog.npcExists(npcId) || !catalog.npcSpawned(npcId)) {
				throw new IllegalArgumentException("Contact " + key + " has unknown/unspawned NPC " + npcId);
			}
			if (!contactNpcIds.add(npcId)) {
				throw new IllegalArgumentException("Duplicate contact NPC " + npcId);
			}
			MonsterSlayerChallenge challenge = MonsterSlayerChallenge.fromKey(object.getString("challenge"));
			MonsterSlayerRank requiredRank = MonsterSlayerRank.fromKey(object.getString("requiredRank"));
			MonsterSlayerRank awardedRank = MonsterSlayerRank.fromKey(object.getString("awardedRank"));
			if (!challenges.add(challenge)) {
				throw new IllegalArgumentException("Duplicate contact challenge " + challenge);
			}
			MonsterSlayerRank expectedRequired = MonsterSlayerRank.fromCode(challenge.getCode() + 1);
			MonsterSlayerRank expectedAwarded = MonsterSlayerRank.fromCode(challenge.getCode() + 2);
			if (requiredRank != expectedRequired || awardedRank != expectedAwarded) {
				throw new IllegalArgumentException("Contact " + key + " breaks the rank/challenge ladder");
			}
			List<Task> mandatory = parseTasks(object.getJSONArray("mandatoryTasks"), false,
				key, families, allTasks);
			List<Task> repeatable = parseTasks(object.getJSONArray("repeatableTasks"), true,
				key, families, allTasks);
			if (mandatory.isEmpty() || repeatable.isEmpty()) {
				throw new IllegalArgumentException("Contact " + key + " requires mandatory and repeatable tasks");
			}
			putUnique(result, key, new Contact(key, npcId, challenge, requiredRank, awardedRank,
				mandatory, repeatable), "contact");
		}
		return result;
	}

	private static List<Task> parseTasks(JSONArray array, boolean repeatable, String contactKey,
			Map<String, Family> families, Map<String, Task> allTasks) {
		List<Task> result = new ArrayList<Task>();
		for (int index = 0; index < array.length(); index++) {
			JSONObject object = array.getJSONObject(index);
			if (repeatable) {
				requireFields(object, "repeatable task", "key", "familyKey", "requiredKills",
					"pointReward", "weight");
			} else {
				requireFields(object, "mandatory task", "key", "familyKey", "requiredKills", "pointReward");
			}
			String key = stableKey(object.getString("key"), "task");
			if (!key.startsWith(contactKey + ".")) {
				throw new IllegalArgumentException("Task " + key + " is not namespaced to " + contactKey);
			}
			String familyKey = stableKey(object.getString("familyKey"), "family reference");
			if (!families.containsKey(familyKey)) {
				throw new IllegalArgumentException("Task " + key + " references unknown family " + familyKey);
			}
			int requiredKills = positiveBounded(object.getInt("requiredKills"), MAX_TASK_KILLS,
				"requiredKills for " + key);
			long pointReward = positiveBounded(object.getLong("pointReward"), MAX_TASK_POINTS,
				"pointReward for " + key);
			int weight = repeatable
				? positiveBounded(object.getInt("weight"), MAX_TASK_WEIGHT, "weight for " + key)
				: 0;
			Task task = new Task(key, familyKey, requiredKills, pointReward, weight, repeatable);
			putUnique(allTasks, key, task, "task");
			result.add(task);
		}
		return result;
	}

	private static LinkedHashMap<String, Shop> parseShops(JSONArray array, ReferenceCatalog catalog) {
		if (array.length() != MonsterSlayerChallenge.values().length) {
			throw new IllegalArgumentException("Monster Slayer requires exactly six shops");
		}
		LinkedHashMap<String, Shop> result = new LinkedHashMap<String, Shop>();
		Set<MonsterSlayerChallenge> challengeOwners = new HashSet<MonsterSlayerChallenge>();
		Set<String> categoryKeys = new HashSet<String>();
		Set<String> rewardKeys = new HashSet<String>();
		Set<String> capacityKeys = new HashSet<String>();
		for (int shopIndex = 0; shopIndex < array.length(); shopIndex++) {
			JSONObject object = array.getJSONObject(shopIndex);
			requireFields(object, "shop", "key", "challenge", "categories", "capacityUpgrade");
			String key = stableKey(object.getString("key"), "shop");
			MonsterSlayerChallenge challenge = MonsterSlayerChallenge.fromKey(object.getString("challenge"));
			if (!challengeOwners.add(challenge)) {
				throw new IllegalArgumentException("Duplicate shop challenge " + challenge);
			}
			JSONArray categoryArray = object.getJSONArray("categories");
			if (categoryArray.length() == 0) {
				throw new IllegalArgumentException("Shop " + key + " has no categories");
			}
			List<Category> categories = new ArrayList<Category>();
			for (int categoryIndex = 0; categoryIndex < categoryArray.length(); categoryIndex++) {
				JSONObject category = categoryArray.getJSONObject(categoryIndex);
				requireFields(category, "category", "key", "label", "iconItemId", "rewards");
				String categoryKey = stableKey(category.getString("key"), "category");
				if (!categoryKeys.add(categoryKey)) {
					throw new IllegalArgumentException("Duplicate category " + categoryKey);
				}
				String label = nonempty(category.getString("label"), "category label");
				int iconItemId = category.getInt("iconItemId");
				if (!catalog.itemExists(iconItemId)) {
					throw new IllegalArgumentException("Category " + categoryKey + " has unknown icon " + iconItemId);
				}
				JSONArray rewardArray = category.getJSONArray("rewards");
				if (rewardArray.length() == 0) {
					throw new IllegalArgumentException("Category " + categoryKey + " has no rewards");
				}
				List<Reward> rewards = new ArrayList<Reward>();
				for (int rewardIndex = 0; rewardIndex < rewardArray.length(); rewardIndex++) {
					JSONObject reward = rewardArray.getJSONObject(rewardIndex);
				requireFields(reward, "reward", "key", "itemId", "amount", "cost", "stock", "restockAmount");
					String rewardKey = stableKey(reward.getString("key"), "reward");
					if (!rewardKeys.add(rewardKey)) {
						throw new IllegalArgumentException("Duplicate reward " + rewardKey);
					}
					int itemId = reward.getInt("itemId");
					if (!catalog.itemExists(itemId) || isForbiddenDragonArmor(itemId)) {
						throw new IllegalArgumentException("Reward " + rewardKey + " has forbidden/unknown item " + itemId);
					}
					int amount = positiveBounded(reward.getInt("amount"), MAX_REWARD_AMOUNT,
						"amount for " + rewardKey);
					MonsterSlayerCost cost = parseCost(reward.getJSONObject("cost"));
					cost.validateForShop(challenge, true);
					int stock = positiveBounded(reward.getInt("stock"), 10_000, "stock for " + rewardKey);
					int restock = positiveBounded(reward.getInt("restockAmount"), stock, "restock for " + rewardKey);
					rewards.add(new Reward(rewardKey, itemId, amount, cost, stock, restock));
				}
				categories.add(new Category(categoryKey, label, iconItemId, rewards));
			}
			JSONObject upgrade = object.getJSONObject("capacityUpgrade");
			requireFields(upgrade, "capacity upgrade", "key", "cost");
			MonsterSlayerCost upgradeCost = parseCost(upgrade.getJSONObject("cost"));
			if (upgradeCost.get(challenge) <= 0L || !hasOnly(upgradeCost, challenge)
				|| upgradeCost.get(challenge) <= mandatoryTotalFor(challenge)) {
				throw new IllegalArgumentException("Invalid capacity upgrade cost for " + key);
			}
			String capacityKey = stableKey(upgrade.getString("key"), "capacity upgrade");
			if (!capacityKeys.add(capacityKey) || !capacityKey.equals(key + ".capacity")) {
				throw new IllegalArgumentException("Invalid/duplicate capacity upgrade key " + capacityKey);
			}
			putUnique(result, key, new Shop(key, challenge, categories,
				new MonsterSlayerDefinitions.CapacityUpgrade(capacityKey, upgradeCost)), "shop");
		}
		return result;
	}

	private static boolean hasOnly(MonsterSlayerCost cost, MonsterSlayerChallenge required) {
		for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) {
			if (challenge != required && cost.get(challenge) != 0L) return false;
		}
		return true;
	}

	private static long mandatoryTotalFor(MonsterSlayerChallenge challenge) {
		long[] totals = {25L, 40L, 60L, 90L, 150L, 260L};
		return totals[challenge.getCode()];
	}

	private static MonsterSlayerCost parseCost(JSONObject object) {
		EnumMap<MonsterSlayerChallenge, Long> amounts =
			new EnumMap<MonsterSlayerChallenge, Long>(MonsterSlayerChallenge.class);
		Iterator<String> keys = object.keys();
		while (keys.hasNext()) {
			String key = keys.next();
			MonsterSlayerChallenge challenge = MonsterSlayerChallenge.fromKey(key);
			if (amounts.put(challenge, object.getLong(key)) != null) {
				throw new IllegalArgumentException("Duplicate cost component " + key);
			}
		}
		return MonsterSlayerCost.of(amounts);
	}

	private static boolean isForbiddenDragonArmor(int itemId) {
		return itemId == ItemId.DRAGON_SCALE_MAIL.id()
			|| itemId == ItemId.DRAGON_PLATE_MAIL_BODY.id()
			|| itemId == ItemId.DRAGON_PLATE_MAIL_TOP.id()
			|| itemId == ItemId.DRAGON_PLATE_MAIL_LEGS.id()
			|| itemId == ItemId.DRAGON_SCALE_MAIL_LEGS.id()
			|| itemId == ItemId.DRAGON_SCALE_MAIL_TOP.id();
	}

	private static void requireFields(JSONObject object, String owner, String... expected) {
		Set<String> remaining = new HashSet<String>();
		Iterator<String> keys = object.keys();
		while (keys.hasNext()) {
			remaining.add(keys.next());
		}
		for (String field : expected) {
			if (!remaining.remove(field)) {
				throw new IllegalArgumentException(owner + " is missing " + field);
			}
		}
		if (!remaining.isEmpty()) {
			throw new IllegalArgumentException(owner + " has unsupported fields " + remaining);
		}
	}

	private static String stableKey(String value, String owner) {
		if (value == null || !STABLE_KEY.matcher(value).matches()) {
			throw new IllegalArgumentException("Invalid " + owner + " key: " + value);
		}
		return value;
	}

	private static String nonempty(String value, String owner) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException(owner + " must not be empty");
		}
		return value;
	}

	private static int positiveBounded(int value, int maximum, String owner) {
		if (value <= 0 || value > maximum) {
			throw new IllegalArgumentException(owner + " outside 1.." + maximum + ": " + value);
		}
		return value;
	}

	private static long positiveBounded(long value, long maximum, String owner) {
		if (value <= 0L || value > maximum) {
			throw new IllegalArgumentException(owner + " outside 1.." + maximum + ": " + value);
		}
		return value;
	}

	private static <T> void putUnique(Map<String, T> values, String key, T value, String owner) {
		if (values.put(key, value) != null) {
			throw new IllegalArgumentException("Duplicate " + owner + " key " + key);
		}
	}

	private static <T> Map<String, T> immutableMap(Map<String, T> values) {
		return Collections.unmodifiableMap(new LinkedHashMap<String, T>(values));
	}

	public Family getFamily(String key) {
		return families.get(key);
	}

	public Contact getContact(String key) {
		return contacts.get(key);
	}

	public Task getTask(String key) {
		return tasks.get(key);
	}

	public Shop getShop(String key) {
		return shops.get(key);
	}

	public List<Family> getFamilies() {
		return Collections.unmodifiableList(new ArrayList<Family>(families.values()));
	}

	public List<Contact> getContacts() {
		return Collections.unmodifiableList(new ArrayList<Contact>(contacts.values()));
	}

	public List<Contact> getContactsInChallengeOrder() {
		List<Contact> ordered = new ArrayList<Contact>();
		for (int code = 0; code < MonsterSlayerChallenge.values().length; code++) {
			MonsterSlayerChallenge challenge = MonsterSlayerChallenge.fromCode(code);
			for (Contact contact : contacts.values()) {
				if (contact.getChallenge() == challenge) {
					ordered.add(contact);
					break;
				}
			}
		}
		return Collections.unmodifiableList(ordered);
	}

	public List<Task> getTasks() {
		return Collections.unmodifiableList(new ArrayList<Task>(tasks.values()));
	}

	public List<Shop> getShops() {
		return Collections.unmodifiableList(new ArrayList<Shop>(shops.values()));
	}

	public interface ReferenceCatalog {
		boolean npcExists(int npcId);

		boolean npcAttackable(int npcId);

		boolean npcSpawned(int npcId);

		boolean itemExists(int itemId);
	}
}
