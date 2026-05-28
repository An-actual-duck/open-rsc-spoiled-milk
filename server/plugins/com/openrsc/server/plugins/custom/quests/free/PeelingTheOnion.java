package com.openrsc.server.plugins.custom.quests.free;

import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

public final class PeelingTheOnion {
	public static final int STATE_COMPLETE = com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.STATE_COMPLETE;
	public static final int STATE_NOT_BEGUN = com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.STATE_NOT_BEGUN;
	public static final int STATE_STARTED_QUEST_WITH_KRESH = com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.STATE_STARTED_QUEST_WITH_KRESH;
	public static final int STATE_STARTED_QUEST_WITH_SEDRIDOR = com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.STATE_STARTED_QUEST_WITH_SEDRIDOR;
	public static final int STATE_STARTED_QUEST_WITH_SEDRIDOR_CONFRONTED_KRESH = com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.STATE_STARTED_QUEST_WITH_SEDRIDOR_CONFRONTED_KRESH;
	public static final int STATE_PLAYER_CONSIDERS_OGRE = com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.STATE_PLAYER_CONSIDERS_OGRE;
	public static final int STATE_SEDRIDOR_SUGGESTED_YOU_VISIT_MAKE_OVER_MAGE = com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.STATE_SEDRIDOR_SUGGESTED_YOU_VISIT_MAKE_OVER_MAGE;
	public static final int STATE_MAKE_OVER_MAGE_GAVE_WAIVER = com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.STATE_MAKE_OVER_MAGE_GAVE_WAIVER;
	public static final int STATE_SIGNED_WAIVER = com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.STATE_SIGNED_WAIVER;
	public static final int STATE_A_NEW_OGRE = com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.STATE_A_NEW_OGRE;
	public static final int STATE_AGGIE_TOLD_PLAYER_TO_COLLECT_ITEMS = com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.STATE_AGGIE_TOLD_PLAYER_TO_COLLECT_ITEMS;
	public static final int STATE_AGGIE_HAS_GIVEN_CLAY = com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.STATE_AGGIE_HAS_GIVEN_CLAY;
	public static final int STATE_KRESH_NEEDS_RECIPES = com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.STATE_KRESH_NEEDS_RECIPES;

	private PeelingTheOnion() {
	}

	public static void kreshDialogue(Player player, Npc npc) {
		com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.kreshDialogue(player, npc);
	}

	public static void sedridorDialogue(Player player, Npc npc) {
		com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.sedridorDialogue(player, npc);
	}

	public static void makeOverMageDialogue(Player player, Npc npc) {
		com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.makeOverMageDialogue(player, npc);
	}

	public static void aggieDialogue(Player player, Npc npc) {
		com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.aggieDialogue(player, npc);
	}

	public static void makeAnotherClay(Player player, Npc npc, boolean postquest) {
		com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.makeAnotherClay(player, npc, postquest);
	}

	public static boolean aggieHasDialogue(Player player) {
		return com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.aggieHasDialogue(player);
	}

	public static void bookcaseSearch(Player player) {
		com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.bookcaseSearch(player);
	}

	public static void freeMakeover(Player player, Npc npc) {
		com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion.freeMakeover(player, npc);
	}
}
