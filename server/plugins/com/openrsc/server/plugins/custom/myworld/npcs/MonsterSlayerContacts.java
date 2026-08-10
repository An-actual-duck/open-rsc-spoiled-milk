package com.openrsc.server.plugins.custom.myworld.npcs;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerContactService;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerData;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerRank;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerState;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.multi;
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
		if (isAmbient(npc)) { npcsay(player, npc, ambient(npc.getID() - FIRST_AMBIENT)); return; }
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
		if (!hostGuildAllows(player, index)) { npcsay(player, npc, "You need to meet this guild's normal entry requirements first."); return; }
		if (!state.getRank().isAtLeast(REQUIRED[index])) { npcsay(player, npc, refusal(index)); return; }
		if (state.getRank().isAtLeast(data.getContact(CONTACTS[index]).getAwardedRank())
			&& state.getMandatoryCursors().get(CONTACTS[index]).intValue() == data.getContact(CONTACTS[index]).getMandatoryTasks().size()) {
			if (!state.isPromotionAcknowledged(CONTACTS[index], data)) {
				npcsay(player, npc, promotion(index));
				if (!service.acknowledgePromotion(player, CONTACTS[index]).isAccepted()) { player.message("Your Monster Slayer promotion could not be recorded."); return; }
			}
		}
		if (!shortcut) {
			npcsay(player, npc, greeting(index));
			if (multi(player, "Yes please.", "Not now.") != 0) return;
			npcsay(player, npc, proof(index));
		}
		com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task preview = service.previewTask(player, CONTACTS[index]);
		if (preview != null) {
			String warning = warning(preview);
			if (warning != null) npcsay(player, npc, warning);
		}
		MonsterSlayerContactService.Result result = service.requestTask(player, CONTACTS[index]);
		if (!result.isAccepted()) { if (result.getReason().equals("active-task")) { com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task active = data.getTask(state.getActiveTaskKey()); npcsay(player, npc, "Your current task is " + state.getActiveKills() + " of " + active.getRequiredKills() + " " + data.getFamily(active.getFamilyKey()).getDisplayName() + "."); } else npcsay(player, npc, "Not yet. Your record is not ready for another task."); return; }
		String taskKey = MonsterSlayerState.read(player.getCache(), data).getActiveTaskKey();
		com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task task = data.getTask(taskKey);
		npcsay(player, npc, "Your next task is to slay " + task.getRequiredKills() + " " + data.getFamily(task.getFamilyKey()).getDisplayName() + ".");
	}
	private void introduction(Player player, Npc npc, MonsterSlayerContactService service, MonsterSlayerState.Snapshot state, boolean shortcut) {
		if (shortcut) { npcsay(player, npc, "No stamp, no task. Fetch the beer first."); return; }
		if (state.getIntroStage() == 0) { npcsay(player, npc, "'ello there."); if (multi(player, "I hear you give Monster Slayer tasks?", "Hi. And, uh... bye!") != 0) return; npcsay(player, npc, "I sure do! Show me your stamp first.", "Blimey! You're not even a member! Slay my thirst. I require beer!"); service.beginBeerIntroduction(player); return; }
		if (!player.getCarriedItems().getInventory().contains(new Item(ItemId.BEER.id()))) { npcsay(player, npc, "You haven't got the beer yet."); return; }
		if (multi(player, "Offer the beer.", "Not yet.") != 0) return;
		if (player.getCarriedItems().remove(new Item(ItemId.BEER.id())) == -1) { npcsay(player, npc, "You haven't got the beer yet."); return; }
		if (!service.completeBeerIntroduction(player).isAccepted()) { player.getCarriedItems().getInventory().add(new Item(ItemId.BEER.id()), false); player.message("Your rank record could not be updated."); return; }
		npcsay(player, npc, "Excellent, I dub thee an official fledgling Monster Slayer. Hold out your hand for your official stamp", "Nope, just the stamp.", "It's an honor. Return to me any time you wish to continue hunting monsters!");
	}
	private void associate(Player player, Npc npc) { int index = npc.getID() - FIRST_ASSOCIATE; try { MonsterSlayerRank rank = MonsterSlayerState.read(player.getCache(), player.getWorld().getMonsterSlayerData()).getRank(); if (rank.getCode() < index + 2) { npcsay(player, npc, "Sorry, can't show you my wares till you're a " + MonsterSlayerRank.fromCode(index + 2).name().toLowerCase() + "."); return; } npcsay(player, npc, "Your standing is in order. Keep your kit ready; supplies will be issued through the guild soon."); } catch (RuntimeException ex) { player.message("Your Monster Slayer record needs staff attention."); } }
	private static boolean managed(Npc npc) { return isContact(npc) || isAssociate(npc) || isAmbient(npc); }
	private static boolean isContact(Npc npc) { return npc.getID() >= FIRST_CONTACT && npc.getID() < FIRST_ASSOCIATE; }
	private static boolean isAssociate(Npc npc) { return npc.getID() >= FIRST_ASSOCIATE && npc.getID() < FIRST_AMBIENT; }
	private static boolean isAmbient(Npc npc) { return npc.getID() >= FIRST_AMBIENT && npc.getID() < FIRST_AMBIENT + 3; }
	private static String refusal(int index) { String[] lines = {"No stamp, no task. Fetch the beer first.", "I need to see an Initiate sticker before I can put your name on my list.", "A Veteran button gets you a proper job from me.", "An Elite badge is the price of a Champion's contract!", "Champion's medal first. These contracts are not lessons.", "Hero's crest required. Return when you have earned it."}; return lines[index]; }
	private static String greeting(int index) { String[] lines = {"Oh, it's you again. Another task then?", "Back for work, are you?", "You've got the look of someone after a dangerous job.", "Ah! An Elite hunter. Here for a real challenge?", "You came back. Do you want another contract?", "Another contract?"}; return lines[index]; }
	private static String proof(int index) { String[] lines = {"Stamp?", "Let's see that sticker.", "Button.", "Badge, if you please!", "Your medal.", "Crest."}; return lines[index]; }
	private static String promotion(int index) { String[] lines = {"Excellent work! You are an Initiate now. My associate nearby can trade Fledgling Slayer Points.", "You did what you said you would. You're a Veteran now; the trader beside me serves Veterans.", "You're Elite now; take the badge. The big leagues are dangerous, so spend your points on staying alive.", "You are a Champion now. The quartermaster nearby takes Elite Slayer Points.", "You are a Hero. Carry this crest with care; the supplier nearby accepts Champion Slayer Points.", "You've completed your journey for now. You've done well. And what use would you make of the rank?"}; return lines[index]; }
	private static String warning(com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task task) { if (task.getHazards().isEmpty()) return null; StringBuilder text = new StringBuilder("Take care: "); for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard hazard : task.getHazards()) { if (text.length() > 11) text.append("; "); if (hazard == com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.DESERT_HEAT) text.append("bring desert heat protection"); else if (hazard == com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.WILDERNESS) text.append("this work is in the Wilderness"); else if (hazard == com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.PRAYER_DRAIN) text.append("expect Prayer drain"); else if (hazard == com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.POISON) text.append("bring an antidote for poison"); else text.append("prepare for dragon fire"); } return text.append('.').toString(); }
	private static String ambient(int index) { String[] lines = {"Fresh stamp, fresh start. I could take on a goblin with one hand!", "I keep my supplies packed and my journal dry. Sea air ruins both.", "I have done the work. I do not hand out contracts."}; return lines[index]; }
	private static boolean hostGuildAllows(Player player, int index) {
		if (index == 3) return player.getConfig().INFLUENCE_INSTEAD_QP ? player.getSkills().getLevel(Skill.INFLUENCE.id()) >= 20 : player.getQuestPoints() >= 32;
		if (index == 4) return player.getQuestStage(Quests.HEROS_QUEST) == -1;
		return index != 5 || player.getQuestStage(Quests.LEGENDS_QUEST) >= 11 || player.getQuestStage(Quests.LEGENDS_QUEST) == -1;
	}
}
