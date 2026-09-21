package com.openrsc.server.combat;

import com.openrsc.server.content.minigame.monsterslayer.*;
import com.openrsc.server.external.NPCDef;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import java.lang.reflect.*;
import java.nio.file.Paths;
import java.util.*;

/** Real definitions, persisted rank and production dialogue routes, without authored door coordinates. */
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
			Player player = h.player("towerguest", 450, 450);
			List<Npc> associates = new ArrayList<>();
			int[] torsos = {27, 55, 29, 30, 58, 33};
			for (MonsterSlayerTowerAccess entry : MonsterSlayerTowerAccess.values()) {
				int index = entry.ordinal();
				NPCDef def = h.server().getEntityHandler().getNpcDef(entry.getNpcId());
				check(def != null && def.getName().equals("Monster Slayer Associate"), "append-order definition ID " + entry);
				check(!def.isAttackable() && def.getCommand1().isEmpty() && def.getCommand2().isEmpty(), "noncombat talk-only associate");
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
			for (MonsterSlayerRank rank : MonsterSlayerRank.values()) {
				setRank(player, data, rank);
				Map<String, Object> before = new HashMap<>(player.getCache().getCacheMap());
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
			Player fresh = h.player("newtower", 450, 450);
			check(!MonsterSlayerTowerAccess.FLEDGLING.allows(fresh), "empty cache cannot enter");
			player.getCache().store("monster_slayer_rank", 999);
			recording.clear(); talk.invoke(plugin, player, associates.get(0));
			check(!MonsterSlayerTowerAccess.FLEDGLING.allows(player) && recording.options.isEmpty()
				&& recording.lines.get(0).contains("can't verify"), "invalid state fails closed, not enrollment");
			setRank(player, data, MonsterSlayerRank.LEGEND);
			h.installMonsterSlayerData(null);
			check(!MonsterSlayerTowerAccess.FLEDGLING.allows(player), "missing service fails closed");
			h.installMonsterSlayerData(data);
			h.server().getConfig().WANT_MYWORLD = false;
			check(!(Boolean)block.invoke(plugin, player, associates.get(0)) && !MonsterSlayerTowerAccess.FLEDGLING.allows(player), "profile isolation");
			h.server().getConfig().WANT_MYWORLD = true;
			check(MonsterSlayerDialoguePlan.promotion(1).stream().anyMatch(step -> step.getText().contains("this button"))
				&& MonsterSlayerTowerAccess.VETERAN.getRefusal().contains("Veteran button")
				&& !MonsterSlayerTowerAccess.VETERAN.getRefusal().contains("crest"), "Veteran flavor matches promotion");
			System.out.println("PASS: six tower definitions, tier armor, all rank/floor combinations, approved dialogue branches, cancellation, fail-closed state, no progression or placement changes");
		}
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
