package com.openrsc.server.combat;

import com.openrsc.server.content.minigame.monsterslayer.*;
import com.openrsc.server.external.NPCDef;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.coordinate.*;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.model.entity.player.Player;
import java.lang.reflect.*;
import java.nio.file.Paths;
import java.util.*;

/** Real definitions, persisted rank and production dialogue routes, with exact authored passage scopes and independent rank enforcement. */
public final class SlayerTowerAssociatesFixture {
	private static final class Recording implements InvocationHandler {
		final List<String> lines = new ArrayList<>();
		final List<String> options = new ArrayList<>();
		int choice = -1;
		public Object invoke(Object proxy, Method method, Object[] args) {
			if (method.getName().equals("choose")) {
				options.addAll(Arrays.asList((String[])args[1]));
				return choice;
			}
			if (method.getName().equals("npc")) lines.addAll(Arrays.asList((String[])args[2]));
			if (method.getName().equals("player")) lines.add("Player: " + args[2]);
			return null;
		}
		void clear() { lines.clear(); options.clear(); choice = -1; }
	}

	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness h = new CurrentCombatHarness()) {
			MonsterSlayerData.ReferenceCatalog catalog = new MonsterSlayerData.ReferenceCatalog() {
				public boolean npcExists(int id) { return true; }
				public boolean npcAttackable(int id) { return true; }
				public boolean npcSpawned(int id) { return true; }
				public boolean itemExists(int id) { return h.server().getEntityHandler().getItemDef(id) != null; }
			};
			MonsterSlayerData data = MonsterSlayerData.load(Paths.get("conf/server/defs/extras/MonsterSlayer.json"), catalog);
			h.installMonsterSlayerData(data);
			Class<?> pluginType = Class.forName("com.openrsc.server.plugins.custom.myworld.npcs.MonsterSlayerTowerAssociates");
			Class<?> dialogueType = Class.forName(pluginType.getName() + "$Dialogue");
			Recording recording = new Recording();
			Object dialogue = Proxy.newProxyInstance(dialogueType.getClassLoader(), new Class<?>[]{dialogueType}, recording);
			Object plugin = pluginType.getConstructor(dialogueType).newInstance(dialogue);
			Method talk = pluginType.getMethod("onTalkNpc", Player.class, Npc.class);
			Method block = pluginType.getMethod("blockTalkNpc", Player.class, Npc.class);
			Method passageBlock = pluginType.getMethod("blockOpLoc", Player.class, GameObject.class, String.class);
			Player player = h.player("towerguest", 450, 450);
			List<Npc> associates = new ArrayList<>();
			int[] torsos = {27, 55, 29, 30, 58, 33};
			for (MonsterSlayerTowerAccess entry : MonsterSlayerTowerAccess.values()) {
				int index = entry.ordinal();
				NPCDef def = h.server().getEntityHandler().getNpcDef(entry.getNpcId());
				check(def != null && def.getName().equals("Monster Slayer Associate"), "append-order definition ID " + entry);
				check(!def.isAttackable() && !def.isAggressive() && def.getCommand1().isEmpty() && def.getCommand2().isEmpty(), "noncombat talk-only associate");
				check(def.getSprite(1) == torsos[index] && def.getSprite(2) == 36 + index
					&& def.getSprite(3) == 97 + index && def.getSprite(4) == 47 + index, "tier armor and weapons");
				Npc npc = h.npc(entry.getNpcId(), 451, 450);
				associates.add(npc);
				check((Boolean)block.invoke(plugin, player, npc), "production talk route registered");
			}
			check(h.server().getEntityHandler().getNpcDef(870).getName().equals("Abyssal demon"), "last monster ID preserved");
			check(MonsterSlayerTowerAccess.forNpc(870) == null && MonsterSlayerTowerAccess.forNpc(877) == null, "only six tower IDs");
			for (int old : new int[]{846, 852, 857, 858, 870}) {
				Npc npc = h.npc(old, 451, 450);
				check(!(Boolean)block.invoke(plugin, player, npc), "existing NPC routes untouched");
			}
			passageScopes();
			GameObject lowerStairs = object(h, 43, 6, 472, 0, 0);
			GameObject entrance = object(h, 1337, 22, 474, 6, 0);
			GameObject roomDoor = object(h, 115, 6, 469, 0, 1);
			Player outside = h.player("toweroutside", 22, 475);
			Player inside = h.player("towerinside", 23, 473);
			for (MonsterSlayerRank rank : MonsterSlayerRank.values()) {
				setRank(player, data, rank);
				setRank(outside, data, rank);
				Map<String, Object> before = new HashMap<>(player.getCache().getCacheMap());
				check((Boolean)passageBlock.invoke(plugin, player, lowerStairs, "climb-up") == !MonsterSlayerTowerAccess.ADEPT.allows(rank), "stair plugin independently enforces rank " + rank);
				check((Boolean)passageBlock.invoke(plugin, outside, entrance, "open") == !MonsterSlayerTowerAccess.FLEDGLING.allows(rank), "entrance plugin independently enforces rank " + rank);
				check(!(Boolean)passageBlock.invoke(plugin, inside, entrance, "open"), "unjoined player may leave");
				check(!(Boolean)passageBlock.invoke(plugin, outside, entrance, "examine"), "harmless entrance commands not captured");
				check(!(Boolean)passageBlock.invoke(plugin, player, roomDoor, "open"), "safe stair room door stays free");
				for (MonsterSlayerTowerAccess entry : MonsterSlayerTowerAccess.values()) {
					boolean expected = rank.getCode() >= entry.getRequiredRank().getCode();
					check(entry.allows(player) == expected, "earned rank gate " + rank + " / " + entry);
					recording.clear();
					talk.invoke(plugin, player, associates.get(entry.ordinal()));
					if (expected) check(recording.lines.equals(Arrays.asList(entry.getWelcome())), "eligible dialogue " + entry);
					else check(recording.lines.contains(entry.getRefusal()), "denied dialogue " + entry);
					check(recording.options.isEmpty() == (expected || entry != MonsterSlayerTowerAccess.FLEDGLING), "entrance-only choices");
					check(before.equals(player.getCache().getCacheMap()), "talk and rank read never mutate state");
					check(player.getX() == 450 && player.getY() == 450
						&& player.getCarriedItems().getInventory().size() == 0, "no teleport, token or inventory mutation");
				}
			}
			setRank(player, data, MonsterSlayerRank.UNSTAMPED);
			for (int choice : new int[]{0, 1, -1, 2}) {
				recording.clear(); recording.choice = choice;
				talk.invoke(plugin, player, associates.get(0));
				check(recording.lines.get(0).equals("Welcome to the Slayer Tower, buddy."), "separate greeting line");
				check(recording.options.equals(Arrays.asList("How do I join?", "Okay, I'll come back later.")), "approved choices");
				if (choice == 0) check(recording.lines.get(3).equals("Head to Falador and speak to Hobart at the Rising Sun, friend. He'll get you started."), "actual recruiter directions");
				else if (choice == 1) check(recording.lines.get(3).equals("See you soon, buddy."), "return later");
				else check(recording.lines.size() == 2, "cancel/out-of-range safe");
				check(MonsterSlayerTowerAccess.readRank(player) == MonsterSlayerRank.UNSTAMPED, "guard cannot enroll");
			}
			h.installPlugin(OpLocTrigger.class, plugin);
			h.installDefaultPlugin(Class.forName("com.openrsc.server.plugins.authentic.defaults.Default").newInstance());
			setRank(player, data, MonsterSlayerRank.UNSTAMPED);
			check(h.server().getPluginHandler().handlePlugin(OpLocTrigger.class, player,
				new Object[]{player, lowerStairs, "climb-up"}), "production dispatcher preempts default for denied rank");
			check(h.findEvent("MonsterSlayerTowerAssociates.onOpLoc") != null
				&& h.findEvent("Default.onOpLoc") == null, "only guard denial action scheduled");
			h.findEvent("MonsterSlayerTowerAssociates.onOpLoc").stop();
			setRank(player, data, MonsterSlayerRank.HERO);
			check(!h.server().getPluginHandler().handlePlugin(OpLocTrigger.class, player,
				new Object[]{player, lowerStairs, "climb-up"}), "eligible rank falls through production dispatcher");
			check(h.findEvent("Default.onOpLoc") != null, "existing default transport action scheduled");
			h.findEvent("Default.onOpLoc").stop();
			Class<?> ladderType = Class.forName("com.openrsc.server.plugins.authentic.defaults.Ladders");
			Method coordinates = ladderType.getDeclaredMethod("coordModifier", Player.class, boolean.class, GameObject.class, boolean.class);
			coordinates.setAccessible(true);
			int[] landing = (int[]) coordinates.invoke(ladderType.newInstance(), player, true, lowerStairs, false);
			check(landing[0] == 6 && landing[1] == 472 + lowerStairs.getGameObjectDef().getHeight(), "existing upward stair landing uses authored footprint");
			System.out.println("Tower upward landing offset: " + landing[0] + "," + landing[1] + ", next signed level");
			Player fresh = h.player("newtower", 450, 450);
			check(!MonsterSlayerTowerAccess.FLEDGLING.allows(fresh), "empty cache cannot enter");
			player.getCache().store("monster_slayer_rank", 999);
			recording.clear(); talk.invoke(plugin, player, associates.get(0));
			check((Boolean)passageBlock.invoke(plugin, player, lowerStairs, "climb-up"), "corrupt rank cannot bypass stairs");
			check(!MonsterSlayerTowerAccess.FLEDGLING.allows(player) && recording.options.isEmpty()
				&& recording.lines.get(0).contains("can't verify"), "invalid state fails closed, not enrollment");
			setRank(player, data, MonsterSlayerRank.LEGEND);
			h.installMonsterSlayerData(null);
			check(!MonsterSlayerTowerAccess.FLEDGLING.allows(player), "missing service fails closed");
			check((Boolean)passageBlock.invoke(plugin, player, lowerStairs, "climb-up"), "missing service cannot bypass stairs");
			h.installMonsterSlayerData(data);
			h.server().getConfig().WANT_MYWORLD = false;
			check(!(Boolean)block.invoke(plugin, player, associates.get(0)) && !MonsterSlayerTowerAccess.FLEDGLING.allows(player), "profile isolation");
			check(!(Boolean)passageBlock.invoke(plugin, player, lowerStairs, "climb-up"), "non-MyWorld passages untouched");
			h.server().getConfig().WANT_MYWORLD = true;
			check(MonsterSlayerDialoguePlan.promotion(1).stream().anyMatch(step -> step.getText().contains("this button"))
				&& MonsterSlayerTowerAccess.VETERAN.getRefusal().contains("Veteran button")
				&& !MonsterSlayerTowerAccess.VETERAN.getRefusal().contains("crest"), "Veteran flavor matches promotion");
			System.out.println("PASS: six tower definitions, tier armor, all rank/floor combinations, approved dialogue branches, cancellation, fail-closed state, exact entrance and upward stairs, free exits and room doors, no progression changes");
		}
	}

	private static GameObject object(CurrentCombatHarness harness, int id, int x, int y, int direction, int type) {
		GameObject result = new GameObject(harness.server().getWorld(), Point.location(x, y), id, direction, type);
		result.setInitialLocation(Point.location(x, y));
		return result;
	}

	private static WorldLocation at(int x, int y, int level) {
		return WorldLocation.global(new WorldCoordinate(x, y, level));
	}

	private static void passageScopes() {
		for (int level = 0; level < 5; level++) {
			int x = level % 2 == 0 ? 6 : 37;
			WorldLocation here = at(x, 472, level);
			MonsterSlayerTowerAccess access = MonsterSlayerTowerAccess.values()[level + 1];
			for (String command : new String[]{"climb-up", "climb up", "go up", "CLIMB-UP"})
				check(MonsterSlayerTowerPassages.stairs(here, 43, 0, 0, command) == access, "exact upward route " + level);
			for (String command : new String[]{"climb-down", "go down", "pull", "open", "", null})
				check(MonsterSlayerTowerPassages.stairs(here, 43, 0, 0, command) == null, "not an upward action");
			check(MonsterSlayerTowerPassages.stairs(here, 44, 0, 0, "climb-up") == null, "downstairs definition stays free");
			check(MonsterSlayerTowerPassages.stairs(here, 43, 2, 0, "climb-up") == null, "wrong direction");
			check(MonsterSlayerTowerPassages.stairs(here, 43, 0, 1, "climb-up") == null, "wrong kind");
			check(MonsterSlayerTowerPassages.stairs(at(x+1,472,level),43,0,0,"climb-up") == null, "adjacent stairs not owned");
			check(MonsterSlayerTowerPassages.stairs(at(x,473,level),43,0,0,"climb-up") == null, "different row not owned");
			WorldLocation instance = new WorldLocation(new WorldSpaceId("tower-instance"), here.getCoordinate());
			check(MonsterSlayerTowerPassages.stairs(instance,43,0,0,"climb-up") == null, "other worldspace not owned");
		}
		check(MonsterSlayerTowerPassages.stairs(at(6,472,5),43,0,0,"climb-up") == null, "no extra floor gate");
		for (int id : new int[]{1337,1338}) for (int x : new int[]{22,23}) {
			check(MonsterSlayerTowerPassages.entrance(at(22,474,0),id,6,0,at(x,474,0)) == MonsterSlayerTowerAccess.FLEDGLING, "both outside lanes guarded, including temporary visual");
			check(MonsterSlayerTowerPassages.entrance(at(22,474,0),id,6,0,at(x,473,0)) == null, "both exits free");
		}
		check(MonsterSlayerTowerPassages.entrance(at(22,474,1),1337,6,0,at(22,475,1)) == null, "entrance different level free");
		check(MonsterSlayerTowerPassages.entrance(at(23,474,0),1337,6,0,at(22,475,0)) == null, "different origin free");
		check(MonsterSlayerTowerPassages.entrance(at(22,474,0),1337,2,0,at(22,475,0)) == null, "other door direction free");
		check(MonsterSlayerTowerPassages.entrance(at(22,474,0),115,6,0,at(22,475,0)) == null, "other door definition free");
		check(MonsterSlayerTowerPassages.entrance(at(22,474,0),1337,6,0,at(22,475,1)) == null, "player level must match");
	}

	private static void setRank(Player player, MonsterSlayerData data, MonsterSlayerRank rank) {
		Map<String, Integer> cursors = new LinkedHashMap<>();
		for (MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder())
			cursors.put(contact.getKey(), rank.isAtLeast(contact.getAwardedRank()) ? contact.getMandatoryTasks().size() : 0);
		MonsterSlayerState.write(player.getCache(), data, MonsterSlayerState.create(rank == MonsterSlayerRank.UNSTAMPED ? 0 : 2,
			rank, MonsterSlayerBalances.zero(), cursors, null, 0, 0L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data));
	}
	private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
