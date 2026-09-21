package com.openrsc.server.plugins.custom.myworld.npcs;

import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerRank;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerTowerAccess;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;
import static com.openrsc.server.plugins.Functions.say;

/** Tower dialogue only. Actual passage enforcement awaits the owner's mapped doors. */
public final class MonsterSlayerTowerAssociates implements TalkNpcTrigger {
	public interface Dialogue {
		default void npc(Player player, Npc npc, String... lines) { npcsay(player, npc, lines); }
		default void player(Player player, Npc npc, String line) { say(player, npc, line); }
		default int choose(Player player, String... options) { return multi(player, options); }
	}

	private final Dialogue dialogue;
	public MonsterSlayerTowerAssociates() { this(new Dialogue() { }); }
	public MonsterSlayerTowerAssociates(Dialogue dialogue) {
		if (dialogue == null) throw new IllegalArgumentException("Dialogue is required");
		this.dialogue = dialogue;
	}

	@Override public boolean blockTalkNpc(Player player, Npc npc) {
		return player.getConfig().WANT_MYWORLD && npc != null
			&& MonsterSlayerTowerAccess.forNpc(npc.getID()) != null;
	}

	@Override public void onTalkNpc(Player player, Npc npc) {
		if (!blockTalkNpc(player, npc)) return;
		MonsterSlayerTowerAccess entry = MonsterSlayerTowerAccess.forNpc(npc.getID());
		MonsterSlayerRank rank = MonsterSlayerTowerAccess.readRank(player);
		if (rank == null) {
			dialogue.npc(player, npc, "I can't verify your Slayer rank right now. Please try again later.");
			return;
		}
		if (entry.allows(rank)) { dialogue.npc(player, npc, entry.getWelcome()); return; }
		if (entry != MonsterSlayerTowerAccess.FLEDGLING) {
			dialogue.npc(player, npc, entry.getRefusal());
			return;
		}
		dialogue.npc(player, npc, "Welcome to the Slayer Tower, buddy.", entry.getRefusal());
		String[] choices = {"How do I join?", "Okay, I'll come back later."};
		int choice = dialogue.choose(player, choices);
		if (choice < 0 || choice >= choices.length) return;
		dialogue.player(player, npc, choices[choice]);
		dialogue.npc(player, npc, choice == 0
			? "Head to Falador and speak to Hobart at the Rising Sun, friend. He'll get you started."
			: "See you soon, buddy.");
	}
}
