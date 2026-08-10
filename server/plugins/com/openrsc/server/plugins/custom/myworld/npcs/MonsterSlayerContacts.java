package com.openrsc.server.plugins.custom.myworld.npcs;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerContactService;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerData;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerRank;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerState;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerTaskService;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.npcsay;

/** Player-facing contact shell; all rank/task state remains in typed Slayer services. */
public final class MonsterSlayerContacts implements TalkNpcTrigger, OpNpcTrigger {
	private static final int FIRST_CONTACT = 846;
	private static final int FIRST_ASSOCIATE = 852;
	private static final int FIRST_AMBIENT = 858;
	private static final String[] CONTACTS = {"falador", "port_sarim", "brimhaven", "champions", "heroes", "legends"};
	private static final MonsterSlayerRank[] REQUIRED = {MonsterSlayerRank.FLEDGLING, MonsterSlayerRank.INITIATE, MonsterSlayerRank.VETERAN, MonsterSlayerRank.ELITE, MonsterSlayerRank.CHAMPION, MonsterSlayerRank.HERO};

	@Override public boolean blockTalkNpc(Player player, Npc npc) { return managed(npc); }
	@Override public boolean blockOpNpc(Player player, Npc npc, String command) { return managed(npc) && ("Task".equalsIgnoreCase(command) || "Trade".equalsIgnoreCase(command) || "Shop".equalsIgnoreCase(command)); }
	@Override public void onTalkNpc(Player player, Npc npc) {
		if (isAmbient(npc)) { npcsay(player, npc, "I'm here to hunt monsters, not hand out contracts."); return; }
		if (isAssociate(npc)) { associate(player, npc); return; }
		contact(player, npc, false);
	}
	@Override public void onOpNpc(Player player, Npc npc, String command) {
		if (isAssociate(npc)) { associate(player, npc); return; }
		if (isContact(npc)) contact(player, npc, true);
	}
	private void contact(Player player, Npc npc, boolean shortcut) {
		int index = npc.getID() - FIRST_CONTACT;
		MonsterSlayerData data = player.getWorld().getMonsterSlayerData();
		MonsterSlayerState.Snapshot state;
		try { state = MonsterSlayerState.read(player.getCache(), data); } catch (RuntimeException ex) { player.message("Your Monster Slayer record needs staff attention."); return; }
		MonsterSlayerContactService service = new MonsterSlayerContactService(data, player.getWorld().getMonsterSlayerTaskService());
		if (index == 0 && state.getRank() == MonsterSlayerRank.UNSTAMPED) { introduction(player, npc, service, state, shortcut); return; }
		if (state.getRank() != REQUIRED[index]) { npcsay(player, npc, refusal(index)); return; }
		MonsterSlayerContactService.Result result = service.requestTask(player, CONTACTS[index]);
		if (!result.isAccepted()) { npcsay(player, npc, result.getReason().equals("active-task") ? "Finish your current task first." : "Not yet. Your record is not ready for another task."); return; }
		npcsay(player, npc, "Your next task has been recorded. Be prepared before you leave.");
	}
	private void introduction(Player player, Npc npc, MonsterSlayerContactService service, MonsterSlayerState.Snapshot state, boolean shortcut) {
		if (shortcut) { npcsay(player, npc, "No stamp, no task. Fetch the beer first."); return; }
		if (state.getIntroStage() == 0) { npcsay(player, npc, "'ello there.", "I sure do! Show me your stamp first.", "Blimey! You're not even a member! Slay my thirst. I require beer!"); service.beginBeerIntroduction(player); return; }
		if (!player.getCarriedItems().getInventory().contains(new Item(ItemId.BEER.id()))) { npcsay(player, npc, "You haven't got the beer yet."); return; }
		if (player.getCarriedItems().remove(new Item(ItemId.BEER.id())) == -1) { npcsay(player, npc, "You haven't got the beer yet."); return; }
		if (!service.completeBeerIntroduction(player).isAccepted()) { player.getCarriedItems().getInventory().add(new Item(ItemId.BEER.id()), false); player.message("Your rank record could not be updated."); return; }
		npcsay(player, npc, "Excellent, I dub thee an official fledgling Monster Slayer. Hold out your hand for your official stamp", "Nope, just the stamp.", "It's an honor. Return to me any time you wish to continue hunting monsters!");
	}
	private void associate(Player player, Npc npc) { int index = npc.getID() - FIRST_ASSOCIATE; try { MonsterSlayerRank rank = MonsterSlayerState.read(player.getCache(), player.getWorld().getMonsterSlayerData()).getRank(); if (rank.getCode() < index + 2) { npcsay(player, npc, "Sorry, can't show you my wares till you're a " + MonsterSlayerRank.fromCode(index + 2).name().toLowerCase() + "."); return; } npcsay(player, npc, "Your standing is in order. The Monster Slayer shop interface is coming soon."); } catch (RuntimeException ex) { player.message("Your Monster Slayer record needs staff attention."); } }
	private static boolean managed(Npc npc) { return isContact(npc) || isAssociate(npc) || isAmbient(npc); }
	private static boolean isContact(Npc npc) { return npc.getID() >= FIRST_CONTACT && npc.getID() < FIRST_ASSOCIATE; }
	private static boolean isAssociate(Npc npc) { return npc.getID() >= FIRST_ASSOCIATE && npc.getID() < FIRST_AMBIENT; }
	private static boolean isAmbient(Npc npc) { return npc.getID() >= FIRST_AMBIENT && npc.getID() < FIRST_AMBIENT + 3; }
	private static String refusal(int index) { String[] lines = {"No stamp, no task. Fetch the beer first.", "I need to see an Initiate sticker before I can put your name on my list.", "A Veteran button gets you a proper job from me.", "An Elite badge is the price of a Champion's contract!", "Champion's medal first. These contracts are not lessons.", "Hero's crest required. Return when you have earned it."}; return lines[index]; }
}
