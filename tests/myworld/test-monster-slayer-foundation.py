#!/usr/bin/env python3
"""Validate the bounded Monster Slayer data/state/migration foundation."""

import json
import shutil
import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "server/conf/server/defs/extras/MonsterSlayer.json"
ODYSSEY = ROOT / "server/conf/server/defs/extras/CombatOdyssey.json"
SERVER_JAR = ROOT / "server/core.jar"
PACKAGE = "com.openrsc.server.content.minigame.monsterslayer"
JAVA_ROOT = ROOT / "server/src/com/openrsc/server/content/minigame/monsterslayer"

EXPECTED_CHALLENGES = ["FLEDGLING", "INITIATE", "VETERAN", "ELITE", "CHAMPION", "HERO"]
EXPECTED_RANKS = ["FLEDGLING", "INITIATE", "VETERAN", "ELITE", "CHAMPION", "HERO", "LEGEND"]
EXPECTED_POINTS = [38, 67, 62, 50, 121, 128]
FORBIDDEN_NPCS = {
    473, 583, 694, 50, 359, 710, 375, 376, 252, 192, 109, 182, 78,
    567, 518, 704, 630,
}
FORBIDDEN_DRAGON_ARMOR = {1368, 1427, 1428, 1429, 1430, 1537}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def load_npc_definitions() -> dict[int, dict]:
    definitions: dict[int, dict] = {}
    for name in ("NpcDefs.json", "NpcDefsCustom.json", "MonsterSlayerNpcDefs.json", "MyWorldNpcDefs.json"):
        entries = json.loads((ROOT / "server/conf/server/defs" / name).read_text())["npcs"]
        definitions.update({entry["id"]: entry for entry in entries})
    for patch in json.loads((ROOT / "server/conf/server/defs/NpcDefsMyWorld.json").read_text())["npcs"]:
        require(patch["id"] in definitions, f"MyWorld override has unknown NPC {patch['id']}")
        definitions[patch["id"]] = {**definitions[patch["id"]], **patch}
    return definitions


def npc_identity(entry: dict) -> tuple:
    return (
        entry["id"], entry["start"]["X"], entry["start"]["Y"],
        entry["min"]["X"], entry["min"]["Y"],
        entry["max"]["X"], entry["max"]["Y"],
    )


def load_active_spawns() -> list[dict]:
    names = (
        "NpcLocs.json", "NpcLocsDiscontinued.json", "NpcLocsModRoom.json",
        "NpcLocsRunecraft.json", "NpcLocsAuction.json", "NpcLocsIronman.json",
        "NpcLocsHarvesting.json", "NpcLocsCustomQuest.json", "NpcLocsOther.json",
        "MyWorldNpcLocs.json",
    )
    spawns: list[dict] = []
    loc_root = ROOT / "server/conf/server/defs/locs"
    for name in names:
        document = json.loads((loc_root / name).read_text())
        entries = next(iter(document.values()))
        spawns.extend(entries)
    removals = json.loads((loc_root / "MyWorldNpcRemovals.json").read_text())["npc_removals"]
    removed = {npc_identity(entry) for entry in removals}
    return [entry for entry in spawns if npc_identity(entry) not in removed
            and not (190 <= entry["start"]["X"] <= 245 and 710 <= entry["start"]["Y"] <= 760)]


def recursively_collect_keys(value) -> set[str]:
    if isinstance(value, dict):
        result = set(value)
        for nested in value.values():
            result.update(recursively_collect_keys(nested))
        return result
    if isinstance(value, list):
        result: set[str] = set()
        for nested in value:
            result.update(recursively_collect_keys(nested))
        return result
    return set()


def validate_json() -> None:
    data = json.loads(DATA.read_text())
    require(set(data) == {"schemaVersion", "families", "contacts", "shops"}, "schema root drift")
    require(data["schemaVersion"] == 1, "schema version drift")

    families = data["families"]
    contacts = data["contacts"]
    shops = data["shops"]
    family_keys = [family["key"] for family in families]
    require(len(family_keys) == len(set(family_keys)) == 50, "family key inventory drift")
    all_npc_ids = [npc_id for family in families for npc_id in family["npcIds"]]
    require(len(all_npc_ids) == len(set(all_npc_ids)), "NPC IDs overlap between families")
    require(not FORBIDDEN_NPCS.intersection(all_npc_ids), "unsafe Odyssey NPC entered launch families")

    require(len(contacts) == 6, "contact count drift")
    require([contact["challenge"] for contact in contacts] == EXPECTED_CHALLENGES, "challenge ladder drift")
    require([contact["requiredRank"] for contact in contacts] == EXPECTED_RANKS[:-1], "required rank ladder drift")
    require([contact["awardedRank"] for contact in contacts] == EXPECTED_RANKS[1:], "awarded rank ladder drift")
    require(len({contact["key"] for contact in contacts}) == 6, "duplicate contact key")

    require(len(shops) == 6, "shop count drift")
    require([shop["key"] for shop in shops] == [contact["key"] for contact in contacts],
            "shop/contact order drift")
    require([shop["challenge"] for shop in shops] == EXPECTED_CHALLENGES,
            "shop challenge ladder drift")
    reward_keys = []
    for shop in shops:
        upgrade = shop["capacityUpgrade"]
        require(upgrade["key"] == f'{shop["key"]}.capacity', "capacity upgrade key drift")
        require(upgrade["cost"].get(shop["challenge"], 0) > 0,
                "capacity upgrade must cost its challenge currency")
        require(shop["categories"], "shop must retain at least one category")
        rewards = [reward for category in shop["categories"] for reward in category["rewards"]]
        require(rewards, "shop must retain at least one reward")
        require(all(reward["itemId"] >= 0 and reward["amount"] > 0 and reward["cost"]
                    for reward in rewards), "shop reward bounds drift")
        reward_keys.extend(reward["key"] for reward in rewards)
    require(len(reward_keys) == len(set(reward_keys)), "duplicate shop reward key")

    mandatory = [task for contact in contacts for task in contact["mandatoryTasks"]]
    repeatable = [task for contact in contacts for task in contact["repeatableTasks"]]
    require(len(mandatory) == 35, "mandatory task count drift")
    require(sum(task["requiredKills"] for task in mandatory) == 1_113, "mandatory kill total drift")
    require(len(repeatable) == 12, "repeatable task count drift")
    require([sum(task["pointReward"] for task in contact["mandatoryTasks"])
             for contact in contacts] == EXPECTED_POINTS, "typed challenge earnings drift")
    task_keys = [task["key"] for task in mandatory + repeatable]
    require(len(task_keys) == len(set(task_keys)), "duplicate task key")
    require(all(task["requiredKills"] > 0 and task["pointReward"] > 0 for task in mandatory + repeatable),
            "task bounds must remain positive")
    require(all(task["weight"] > 0 for task in repeatable), "repeatable weights must remain positive")
    require(all(task["familyKey"] in set(family_keys) for task in mandatory + repeatable),
            "task references unknown family")

    keys = recursively_collect_keys({"families": families, "contacts": contacts})
    require(keys.isdisjoint({"materials", "certificates", "taskId", "tierId", "itemReward", "rewards"}),
            "forbidden material/reward/positional field entered schema")
    serialized = DATA.read_text()
    require(not any(f'"itemId":{item_id}' in serialized.replace(" ", "")
                    for item_id in FORBIDDEN_DRAGON_ARMOR), "finished dragon armor entered launch data")

    definitions = load_npc_definitions()
    spawns = load_active_spawns()
    spawn_ids = {spawn["id"] for spawn in spawns}
    for family in families:
        for npc_id in family["npcIds"]:
            require(npc_id in definitions, f"unknown NPC definition {npc_id}")
            require(bool(definitions[npc_id]["attackable"]), f"nonattackable launch NPC {npc_id}")
        require(any(npc_id in spawn_ids for npc_id in family["npcIds"]),
                f"family {family['key']} has no active spawn evidence")
    for contact in contacts:
        require(contact["npcId"] in definitions, f"unknown contact NPC {contact['npcId']}")
        require(contact["npcId"] in spawn_ids, f"unspawned contact NPC {contact['npcId']}")


FIXTURE = r"""
package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.model.Cache;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Contact;

public final class MonsterSlayerFoundationFixture {
	private static final class Catalog implements MonsterSlayerData.ReferenceCatalog {
		private final Set<Integer> nonattackable = new HashSet<Integer>();
		private final Set<Integer> unspawned = new HashSet<Integer>();
		public boolean npcExists(int id) { return id >= 0; }
		public boolean npcAttackable(int id) { return !nonattackable.contains(id); }
		public boolean npcSpawned(int id) { return !unspawned.contains(id); }
		public boolean itemExists(int id) { return id >= 0; }
	}

	private interface Action { void run(); }

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static void reject(Action action, String message) {
		try {
			action.run();
			throw new AssertionError("Expected rejection: " + message);
		} catch (RuntimeException expected) {
			// expected
		}
	}

	private static JSONObject copy(JSONObject object) { return new JSONObject(object.toString()); }

	public static void main(String[] args) throws Exception {
		JSONObject root = new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8));
		Catalog catalog = new Catalog();
		MonsterSlayerData data = MonsterSlayerData.parse(copy(root), catalog);
		check(data.getFamilies().size() == 50 && data.getContacts().size() == 6
			&& data.getTasks().size() == 47 && data.getShops().size() == 6, "real data inventory drift");
		check(data.getTask("falador.goblins") != null && data.getFamily("rat") != null,
			"stable key resolution failed");
		assertOrderIndependence(root, catalog);
		assertDataRejections(root);
		assertVectors();
		assertState(data);
		assertMigration(data, args[1]);
	}

	private static void assertOrderIndependence(JSONObject root, Catalog catalog) {
		JSONObject reversed = copy(root);
		JSONArray original = reversed.getJSONArray("contacts");
		JSONArray contacts = new JSONArray();
		for (int i = original.length() - 1; i >= 0; i--) contacts.put(original.getJSONObject(i));
		reversed.put("contacts", contacts);
		MonsterSlayerData reordered = MonsterSlayerData.parse(reversed, catalog);
		check("falador".equals(reordered.getContactsInChallengeOrder().get(0).getKey()),
			"contact ladder depends on JSON position");
		check(reordered.getContact("legends").getChallenge() == MonsterSlayerChallenge.HERO,
			"stable contact lookup changed after reorder");
	}

	private static void assertDataRejections(final JSONObject root) {
		reject(new Action() { public void run() {
			JSONObject bad = copy(root); bad.getJSONArray("families").put(copy(bad.getJSONArray("families").getJSONObject(0)));
			MonsterSlayerData.parse(bad, new Catalog());
		}}, "duplicate family");
		reject(new Action() { public void run() {
			JSONObject bad = copy(root); bad.getJSONArray("contacts").getJSONObject(0)
				.getJSONArray("mandatoryTasks").getJSONObject(0).put("familyKey", "missing");
			MonsterSlayerData.parse(bad, new Catalog());
		}}, "missing family");
		reject(new Action() { public void run() {
			JSONObject bad = copy(root); bad.getJSONArray("contacts").getJSONObject(0).put("awardedRank", "HERO");
			MonsterSlayerData.parse(bad, new Catalog());
		}}, "broken rank ladder");
		reject(new Action() { public void run() {
			JSONObject bad = copy(root); bad.getJSONArray("contacts").getJSONObject(0)
				.getJSONArray("mandatoryTasks").getJSONObject(0).put("requiredKills", 0);
			MonsterSlayerData.parse(bad, new Catalog());
		}}, "invalid count");
		reject(new Action() { public void run() {
			JSONObject bad = copy(root); bad.getJSONArray("contacts").getJSONObject(0)
				.getJSONArray("mandatoryTasks").getJSONObject(0).put("challenge", "HERO");
			MonsterSlayerData.parse(bad, new Catalog());
		}}, "task challenge override");
		reject(new Action() { public void run() {
			Catalog badCatalog = new Catalog(); badCatalog.nonattackable.add(19);
			MonsterSlayerData.parse(copy(root), badCatalog);
		}}, "nonattackable family NPC");
		reject(new Action() { public void run() {
			Catalog badCatalog = new Catalog();
			badCatalog.unspawned.add(19); badCatalog.unspawned.add(29);
			badCatalog.unspawned.add(47); badCatalog.unspawned.add(177);
			MonsterSlayerData.parse(copy(root), badCatalog);
		}}, "zero-spawn family");

		MonsterSlayerData shopData = MonsterSlayerData.parse(copy(root), new Catalog());
		check(shopData.getShops().size() == 6,
			"multi-cost shop schema did not load");
		final MonsterSlayerDefinitions.Reward loadedReward = shopData.getShop("legends")
			.getCategories().get(0).getRewards().get(0);
		check(loadedReward.outputAmountFor(3) == 3L
			&& loadedReward.costFor(3).get(MonsterSlayerChallenge.HERO) == 27L,
			"reward quantity quote did not multiply output and every cost component");
		reject(new Action() { public void run() {
			loadedReward.costFor(Long.MAX_VALUE);
		}}, "reward cost multiplication overflow");
		reject(new Action() { public void run() {
			JSONObject bad = copy(root); bad.getJSONArray("shops").getJSONObject(0)
				.getJSONArray("categories").getJSONObject(0).getJSONArray("rewards").getJSONObject(0)
				.getJSONObject("cost").put("LEGEND", 1);
			MonsterSlayerData.parse(bad, new Catalog());
		}}, "unknown cost component");
		reject(new Action() { public void run() {
			JSONObject bad = copy(root); bad.getJSONArray("shops").getJSONObject(0)
				.getJSONArray("categories").getJSONObject(0).getJSONArray("rewards").getJSONObject(0)
				.getJSONObject("cost").put("HERO", 1);
			MonsterSlayerData.parse(bad, new Catalog());
		}}, "cost above shop tier");
	}

	private static void assertVectors() {
		EnumMap<MonsterSlayerChallenge, Long> price = new EnumMap<MonsterSlayerChallenge, Long>(MonsterSlayerChallenge.class);
		price.put(MonsterSlayerChallenge.FLEDGLING, 5L);
		price.put(MonsterSlayerChallenge.INITIATE, 3L);
		price.put(MonsterSlayerChallenge.HERO, 1L);
		MonsterSlayerCost cost = MonsterSlayerCost.of(price);
		check(cost.get(MonsterSlayerChallenge.FLEDGLING) == 5L
			&& cost.get(MonsterSlayerChallenge.INITIATE) == 3L
			&& cost.get(MonsterSlayerChallenge.HERO) == 1L, "typed cost fixture drift");
		MonsterSlayerCost doubled = cost.multiply(2);
		check(doubled.get(MonsterSlayerChallenge.FLEDGLING) == 10L
			&& doubled.get(MonsterSlayerChallenge.HERO) == 2L, "component multiplication failed");
		reject(new Action() { public void run() {
			MonsterSlayerCost.single(MonsterSlayerChallenge.HERO, Long.MAX_VALUE).multiply(2);
		}}, "cost multiplication overflow");

		EnumMap<MonsterSlayerChallenge, Long> funds = new EnumMap<MonsterSlayerChallenge, Long>(MonsterSlayerChallenge.class);
		funds.put(MonsterSlayerChallenge.FLEDGLING, 20L);
		funds.put(MonsterSlayerChallenge.INITIATE, 12L);
		funds.put(MonsterSlayerChallenge.HERO, 4L);
		MonsterSlayerBalances initial = MonsterSlayerBalances.of(funds);
		MonsterSlayerBalances.SpendResult spent = initial.trySpend(cost, 2);
		check(spent.isSuccessful() && spent.getBalances().get(MonsterSlayerChallenge.FLEDGLING) == 10L
			&& spent.getBalances().get(MonsterSlayerChallenge.INITIATE) == 6L
			&& spent.getBalances().get(MonsterSlayerChallenge.HERO) == 2L, "atomic vector deduction failed");
		MonsterSlayerBalances restored = spent.getReceipt().refund(spent.getBalances());
		check(restored.equals(initial), "exact vector refund failed");
		reject(new Action() { public void run() { spent.getReceipt().refund(spent.getBalances()); }}, "double refund");
		MonsterSlayerBalances.SpendResult insufficient = initial.trySpend(cost, 5);
		check(!insufficient.isSuccessful() && insufficient.getBalances().equals(initial),
			"insufficient vector spend partially deducted");
		reject(new Action() { public void run() {
			MonsterSlayerBalances.zero().credit(MonsterSlayerChallenge.HERO, MonsterSlayerBalances.MAX_BALANCE + 1L);
		}}, "balance cap");
		for (int code = 0; code < 6; code++) {
			MonsterSlayerChallenge tier = MonsterSlayerChallenge.fromCode(code);
			MonsterSlayerCost.single(tier, 1L).validateForShop(tier, true);
			if (code < 5) {
				final MonsterSlayerChallenge lowerTier = tier;
				final MonsterSlayerChallenge higher = MonsterSlayerChallenge.fromCode(code + 1);
				reject(new Action() { public void run() {
					MonsterSlayerCost.single(higher, 1L).validateForShop(lowerTier, false);
				}}, "currency above shop tier");
			}
		}
	}

	private static Map<String, Integer> cursorsForRank(MonsterSlayerData data, MonsterSlayerRank rank) {
		Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
		int completed = Math.max(0, rank.getCode() - 1);
		List<Contact> contacts = data.getContactsInChallengeOrder();
		for (int i = 0; i < contacts.size(); i++) {
			cursors.put(contacts.get(i).getKey(), i < completed ? contacts.get(i).getMandatoryTasks().size() : 0);
		}
		return cursors;
	}

	private static MonsterSlayerState.Snapshot rankSnapshot(MonsterSlayerData data, MonsterSlayerRank rank) {
		return MonsterSlayerState.create(rank == MonsterSlayerRank.UNSTAMPED ? 0 : 2, rank,
			MonsterSlayerBalances.zero(), cursorsForRank(data, rank), null, 0, 0L, 0, 0,
			MonsterSlayerState.LegacyStatus.NONE, 0, data);
	}

	private static void assertState(final MonsterSlayerData data) {
		MonsterSlayerState.Snapshot defaults = MonsterSlayerState.defaults(data);
		MonsterSlayerState.validate(defaults, data);
		for (MonsterSlayerRank rank : MonsterSlayerRank.values()) {
			MonsterSlayerState.Snapshot snapshot = rankSnapshot(data, rank);
			check(snapshot.getRank().getCode() == rank.getCode(), "rank code round trip failed");
		}
		MonsterSlayerState.Snapshot complete = rankSnapshot(data, MonsterSlayerRank.LEGEND);
		check(complete.isComplete(data), "Legend completion derivation failed");

		Map<String, Integer> fledgling = cursorsForRank(data, MonsterSlayerRank.FLEDGLING);
		MonsterSlayerState.Snapshot mandatory = MonsterSlayerState.create(2, MonsterSlayerRank.FLEDGLING,
			MonsterSlayerBalances.zero(), fledgling, "falador.goblins", 40, 0L, 0, 0,
			MonsterSlayerState.LegacyStatus.NONE, 0, data);
		check("falador.goblins".equals(mandatory.getActiveTaskKey()), "mandatory active task validation failed");
		Map<String, Integer> initiate = cursorsForRank(data, MonsterSlayerRank.INITIATE);
		MonsterSlayerState.Snapshot repeatable = MonsterSlayerState.create(2, MonsterSlayerRank.INITIATE,
			MonsterSlayerBalances.zero(), initiate, "falador.goblins.repeatable", 1, 1L, 0, 0,
			MonsterSlayerState.LegacyStatus.NONE, 0, data);
		check(repeatable.getActiveKills() == 1, "repeatable active task validation failed");
		reject(new Action() { public void run() {
			Map<String, Integer> bad = cursorsForRank(data, MonsterSlayerRank.FLEDGLING); bad.put("falador", 99);
			MonsterSlayerState.create(2, MonsterSlayerRank.FLEDGLING, MonsterSlayerBalances.zero(), bad,
				null, 0, 0L, 0, 0, MonsterSlayerState.LegacyStatus.NONE, 0, data);
		}}, "cursor/rank contradiction");

		EnumMap<MonsterSlayerChallenge, Long> amounts = new EnumMap<MonsterSlayerChallenge, Long>(MonsterSlayerChallenge.class);
		long value = 10L;
		for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) amounts.put(challenge, value++);
		MonsterSlayerState.Snapshot funded = MonsterSlayerState.create(2, MonsterSlayerRank.FLEDGLING,
			MonsterSlayerBalances.of(amounts), fledgling, null, 0, 0L, 0, 0,
			MonsterSlayerState.LegacyStatus.NONE, 0, data);
		Cache cache = new Cache();
		cache.store("monster_slayer_points", 999L);
		MonsterSlayerState.write(cache, data, funded);
		MonsterSlayerState.Snapshot loaded = MonsterSlayerState.read(cache, data);
		check(loaded.getBalances().equals(funded.getBalances()), "six-balance cache round trip failed");
		check(cache.getLong("monster_slayer_points") == 999L, "retired scalar balance was read or overwritten");
		check(MonsterSlayerState.read(new Cache(), data).getRank() == MonsterSlayerRank.UNSTAMPED,
			"missing cache keys did not default safely");
		final Cache corrupt = new Cache(); corrupt.put("monster_slayer_rank", "bad");
		reject(new Action() { public void run() { MonsterSlayerState.read(corrupt, data); }}, "wrong cache type");

		EnumMap<MonsterSlayerChallenge, Long> costValues = new EnumMap<MonsterSlayerChallenge, Long>(MonsterSlayerChallenge.class);
		costValues.put(MonsterSlayerChallenge.FLEDGLING, 5L);
		costValues.put(MonsterSlayerChallenge.INITIATE, 3L);
		MonsterSlayerState.SpendProposal spend = MonsterSlayerState.proposeSpend(funded, data,
			MonsterSlayerCost.of(costValues), 1);
		check(spend.isSuccessful(), "state multi-cost spend failed");
		MonsterSlayerState.Snapshot refunded = spend.getReceipt().refund(spend.getSnapshot(), data);
		check(refunded.getBalances().equals(funded.getBalances()), "state exact refund failed");
	}

	private static CombatOdysseyMigration.Result migrate(Object state, Object progress, Object prestige,
			CombatOdysseyMigration.LegacyData legacyData, MonsterSlayerData data,
			MonsterSlayerState.Snapshot current) {
		return CombatOdysseyMigration.propose(CombatOdysseyMigration.LegacySnapshot.of(state, progress, prestige),
			legacyData, data, current);
	}

	private static void assertMigration(final MonsterSlayerData data, String legacyPath) throws Exception {
		CombatOdysseyMigration.LegacyData legacyData = CombatOdysseyMigration.LegacyData.load(Paths.get(legacyPath));
		MonsterSlayerState.Snapshot empty = MonsterSlayerState.defaults(data);
		CombatOdysseyMigration.Result none = migrate(null, null, null, legacyData, data, empty);
		check(none.isSuccessful() && none.getClassification() == CombatOdysseyMigration.Classification.NONE
			&& none.getProposal().getMigrationVersion() == 1, "no-state migration failed");
		for (String stage : new String[]{"1", "2"}) {
			CombatOdysseyMigration.Result intro = migrate(stage, null, 0, legacyData, data, empty);
			check(intro.isSuccessful() && intro.getClassification() == CombatOdysseyMigration.Classification.PARTIAL
				&& intro.getProposal().getRank() == MonsterSlayerRank.FLEDGLING,
				"intro-stage migration failed for " + stage);
		}

		JSONObject legacyJson = new JSONObject(new String(Files.readAllBytes(Paths.get(legacyPath)), StandardCharsets.UTF_8));
		JSONArray tiers = legacyJson.getJSONArray("tiers");
		long priorKills = 0L;
		for (int tier = 0; tier < tiers.length(); tier++) {
			CombatOdysseyMigration.Result boundary = migrate(tier + ":-1:0", 0L, 0,
				legacyData, data, empty);
			check(boundary.isSuccessful() && boundary.getCreditedKills() == priorKills
				&& boundary.getProposal().getRank() == MonsterSlayerRank.FLEDGLING,
				"tier boundary migration failed at " + tier);
			JSONArray tasks = tiers.getJSONObject(tier).getJSONArray("tasks");
			for (int task = 0; task < tasks.length(); task++) priorKills += tasks.getJSONObject(task).getInt("kills");
		}
		CombatOdysseyMigration.Result partial = migrate("0:0:100", 0L, 0, legacyData, data, empty);
		check(partial.isSuccessful() && partial.getCreditedKills() == 100L
			&& partial.getProposal().getBalances().get(MonsterSlayerChallenge.HERO) == 0L,
			"partial current-task vector failed");
		CombatOdysseyMigration.Result completedBit = migrate("0:0:500", 1L, 0, legacyData, data, empty);
		check(completedBit.isSuccessful() && completedBit.getCreditedKills() == 500L,
			"completed current task was double counted");

		CombatOdysseyMigration.Result unclaimed = migrate("13:0:1", 0L, 0, legacyData, data, empty);
		check(unclaimed.isSuccessful()
			&& unclaimed.getClassification() == CombatOdysseyMigration.Classification.COMPLETED_UNCLAIMED
			&& unclaimed.getProposal().getRank() == MonsterSlayerRank.LEGEND
			&& unclaimed.getProposal().getBalances().get(MonsterSlayerChallenge.HERO) == 0L,
			"completed-unclaimed migration failed");
		CombatOdysseyMigration.Result unclaimedBit = migrate("13:0:0", 1L, 0, legacyData, data, empty);
		check(unclaimedBit.isSuccessful()
			&& unclaimedBit.getClassification() == CombatOdysseyMigration.Classification.COMPLETED_UNCLAIMED,
			"completed-unclaimed KBD bit migration failed");
		CombatOdysseyMigration.Result claimed = migrate(null, null, 1, legacyData, data, empty);
		check(claimed.isSuccessful()
			&& claimed.getClassification() == CombatOdysseyMigration.Classification.COMPLETED_CLAIMED
			&& claimed.getProposal().getBalances().get(MonsterSlayerChallenge.FLEDGLING) == 0L,
			"claimed completion migration failed");
		CombatOdysseyMigration.Result activeRepeat = migrate("0:0:500", 0L, 2, legacyData, data, empty);
		check(activeRepeat.isSuccessful()
			&& activeRepeat.getProposal().getBalances().get(MonsterSlayerChallenge.HERO) == 0L,
			"active prestige repeat unexpectedly granted shop currency");
		CombatOdysseyMigration.Result capped = migrate(null, null, 1000, legacyData, data, empty);
		check(capped.isSuccessful(), "high prestige migration failed");
		for (int code = 0; code < 6; code++) {
			check(capped.getProposal().getBalances().get(MonsterSlayerChallenge.fromCode(code)) == 0L,
				"migration unexpectedly granted shop currency");
		}

		check(!migrate("broken", null, 0, legacyData, data, empty).isSuccessful(), "malformed string accepted");
		check(!migrate("1", null, "0", legacyData, data, empty).isSuccessful(), "wrong prestige type accepted");
		check(!migrate("1:0:0", 2L, 0, legacyData, data, empty).isSuccessful(), "impossible mask accepted");
		check(!migrate("0:0:0", Integer.valueOf(0), 0, legacyData, data, empty).isSuccessful(),
			"wrong progress type accepted");

		CombatOdysseyMigration.Result repeated = migrate(null, null, 1, legacyData, data, claimed.getProposal());
		check(repeated.isSuccessful()
			&& repeated.getClassification() == CombatOdysseyMigration.Classification.ALREADY_MIGRATED
			&& repeated.getProposal().getBalances().equals(claimed.getProposal().getBalances()),
			"migration is not idempotent");

		Cache cache = new Cache();
		cache.store("combat_odyssey", "13:0:1");
		cache.store("co_tier_progress", 0L);
		cache.set("co_prestige", 0);
		MonsterSlayerState.write(cache, data, unclaimed.getProposal());
		check("13:0:1".equals(cache.getString("combat_odyssey"))
			&& cache.getLong("co_tier_progress") == 0L && cache.getInt("co_prestige") == 0,
			"legacy keys were changed by proposal/state write");
	}
}
"""


def run_compiled_fixture() -> None:
    java_sources = list((ROOT / "server/src").rglob("*.java"))
    if not SERVER_JAR.exists() or any(source.stat().st_mtime > SERVER_JAR.stat().st_mtime
                                      for source in java_sources):
        subprocess.run([str(ROOT / "scripts/build-server.sh")], cwd=ROOT, check=True)
    javac = shutil.which("javac")
    java = shutil.which("java")
    require(javac is not None and java is not None, "Java compiler/runtime are required")
    with tempfile.TemporaryDirectory(prefix="monster-slayer-foundation-") as directory:
        temp = Path(directory)
        source = temp / "com/openrsc/server/content/minigame/monsterslayer/MonsterSlayerFoundationFixture.java"
        source.parent.mkdir(parents=True)
        source.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        subprocess.run(
            [javac, "-source", "8", "-target", "8", "-cp", str(SERVER_JAR), "-d", str(temp), str(source)],
            cwd=ROOT, check=True,
        )
        result = subprocess.run(
            [java, "-cp", f"{temp}:{SERVER_JAR}", f"{PACKAGE}.MonsterSlayerFoundationFixture",
             str(DATA), str(ODYSSEY)],
            cwd=ROOT / "server", capture_output=True, text=True,
        )
        require(result.returncode == 0,
                f"compiled Monster Slayer fixture failed:\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}")


def validate_boundaries() -> None:
    new_sources = "\n".join(path.read_text() for path in JAVA_ROOT.glob("*.java"))
    require("monster_slayer_points" not in new_sources, "new foundation references retired scalar balance")
    require("LEGEND(" not in (JAVA_ROOT / "MonsterSlayerChallenge.java").read_text(),
            "challenge enum introduced Legend points")
    migration = (JAVA_ROOT / "CombatOdysseyMigration.java").read_text()
    require("Player" not in migration and "Cache" not in migration, "pure migration depends on player cache")
    world = (ROOT / "server/src/com/openrsc/server/model/world/World.java").read_text()
    require("if (getServer().getConfig().WANT_MYWORLD) {\n\t\t\t\tsetMonsterSlayerData(MonsterSlayerData.loadForWorld(this));"
            in world, "startup validation is not gated to MyWorld")
    for name in ("CombatOdysseyData.java", "Tier.java", "Task.java"):
        require("compatibility" in (ROOT / "server/src/com/openrsc/server/content/minigame/combatodyssey" / name).read_text(),
                f"{name} is not labeled as compatibility source")


def main() -> None:
    validate_json()
    validate_boundaries()
    run_compiled_fixture()
    print("PASS: Monster Slayer data, typed state, vector costs, and pure Odyssey migration foundation")


if __name__ == "__main__":
    main()
