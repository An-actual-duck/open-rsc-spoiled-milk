package com.openrsc.server.plugins.custom.myworld.npcs;

import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerGuildAccess;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDialoguePlan;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerContactService;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerData;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerRank;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerState;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerShopService;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import java.util.concurrent.ThreadLocalRandom;

import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;
import static com.openrsc.server.plugins.Functions.say;

/** Player-facing contact shell; all rank/task state remains in typed Slayer services. */
public final class MonsterSlayerContacts implements TalkNpcTrigger, OpNpcTrigger {
	/** Injectable boundary keeps production dialogue and deterministic route tests aligned. */
	public interface DialogueRenderer {
		boolean render(Player player, Npc npc, MonsterSlayerDialoguePlan.Step step);
		default void npc(Player player, Npc npc, String... text) { npcsay(player, npc, text); }
		default void player(Player player, Npc npc, String text) { say(player, npc, text); }
		default int choose(Player player, String... choices) { return multi(player, choices); }
	}
	private static final int FIRST_CONTACT = 846;
	private static final int FIRST_ASSOCIATE = 852;
	private static final int FIRST_AMBIENT = 858;
	private static final String[] CONTACTS = {"falador", "port_sarim", "brimhaven", "champions", "heroes", "legends"};
	private static final MonsterSlayerRank[] REQUIRED = {MonsterSlayerRank.FLEDGLING, MonsterSlayerRank.INITIATE, MonsterSlayerRank.VETERAN, MonsterSlayerRank.ELITE, MonsterSlayerRank.CHAMPION, MonsterSlayerRank.HERO};
	private static final String[] HOBART_FOLLOW_UP_REMARKS = {
		"Right then, back to it. Try not to make a spectacle of yourself.",
		"Good. I was starting to think you'd gone soft.",
		"Come back for more work anytime.",
		"There's always another mess needing a capable pair of hands.",
		"That's the spirit. Don't keep the monsters waiting."
	};
	private static final String[] MARA_ASSIGNMENT_REMARKS = {
		"Steady hands make lighter work.",
		"Take your time and do the job properly.",
		"Keep your footing. Strength is no use flat on your back.",
		"A hard day's work is still just a day. You'll manage.",
		"Pack what you need, and mind yourself out there."
	};
	private static final String[] MARA_FIRST_TASK_WELCOME = {
		"Right, you must be the newest among the Adepts.",
		"Getting here means you can swing a sword.",
		"Better than a goblin can stab a spear.",
		"Glad to have you."
	};
	private static final String[] HOBART_MEMBERSHIP_PROOF = {
		"Right, show me your stamp then to prove your membership.", "You mean you aren't even a fledgling?!",
		"Well this won't do. Hm...", "Right, I got it!", "I can think of no fouler beast to slay for your first task.",
		"My thirst! Quickly! A drink!"
	};
	private static final String[] HOBART_DRINK_RETURN = {
		"Splendid! Now hold out your hand.", "The most official of stamps. Welcome aboard."
	};
	private final DialogueRenderer dialogue;
	public MonsterSlayerContacts() { this(new DialogueRenderer() { public boolean render(Player player, Npc npc, MonsterSlayerDialoguePlan.Step step) { if (step.getSpeaker() == MonsterSlayerDialoguePlan.Speaker.NPC) npcsay(player, npc, step.getText()); else say(player, npc, step.getText()); return true; }}); }
	public MonsterSlayerContacts(DialogueRenderer dialogue) { if (dialogue == null) throw new IllegalArgumentException("dialogue renderer is required"); this.dialogue = dialogue; }

	@Override public boolean blockTalkNpc(Player player, Npc npc) { return managed(npc); }
	@Override public boolean blockOpNpc(Player player, Npc npc, String command) {
		return isContact(npc) ? "Task".equalsIgnoreCase(command)
			: isAssociate(npc) && isAssociateShopOperation(command);
	}
	@Override public void onTalkNpc(Player player, Npc npc) {
		if (isAmbient(npc)) { npcsay(player, npc, ambient(npc.getID() - FIRST_AMBIENT)); return; }
		if (isAssociate(npc)) { associate(player, npc, false); return; }
		contact(player, npc, false);
	}
	@Override public void onOpNpc(Player player, Npc npc, String command) {
		if (isAssociate(npc) && isAssociateShopOperation(command)) { associate(player, npc, true); return; }
		if (isContact(npc)) contact(player, npc, true);
	}
	private void contact(Player player, Npc npc, boolean shortcut) {
		int index = npc.getID() - FIRST_CONTACT;
		MonsterSlayerData data = player.getWorld().getMonsterSlayerData();
		MonsterSlayerState.Snapshot state;
		try { state = MonsterSlayerState.read(player.getCache(), data); } catch (RuntimeException ex) { player.message("Your Monster Slayer record needs staff attention."); return; }
		MonsterSlayerContactService service = new MonsterSlayerContactService(data, player.getWorld().getMonsterSlayerTaskService());
		if (index == 0 && state.getRank() == MonsterSlayerRank.UNSTAMPED) { introduction(player, npc, service, state, shortcut); return; }
		if (!hostGuildAllows(player, index)) { dialogue.npc(player, npc, "You need to meet this guild's normal entry requirements first."); return; }
		if (state.getRank().isAtLeast(data.getContact(CONTACTS[index]).getAwardedRank())
			&& state.getMandatoryCursors().get(CONTACTS[index]).intValue() == data.getContact(CONTACTS[index]).getMandatoryTasks().size()
			&& !state.isPromotionAcknowledged(CONTACTS[index], data)) {
			if (!renderPromotion(player, npc, index)) return;
			if (!service.acknowledgePromotion(player, CONTACTS[index]).isAccepted()) player.message("Your Monster Slayer promotion could not be recorded.");
			return;
		}
		if (shortcut && !state.getRank().isAtLeast(REQUIRED[index])) { dialogue.npc(player, npc, contactRefusal(index)); return; }
		if (!shortcut) {
			dialogue.npc(player, npc, contactGreeting(index));
			if (speakChoice(player, npc, contactChoices(index)) != 0) return;
			dialogue.npc(player, npc, contactProof(index));
			if (!state.getRank().isAtLeast(REQUIRED[index])) { dialogue.player(player, npc, missingProofResponse(index)); dialogue.npc(player, npc, contactRefusal(index)); return; }
			dialogue.player(player, npc, "Right here!");
			if (shouldUseMaraFirstTaskWelcome(index, state)) dialogue.npc(player, npc, maraFirstTaskWelcome());
		}
		com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task preview = service.previewTask(player, CONTACTS[index]);
		if (preview != null) {
			String warning = warning(preview);
			if (warning != null) dialogue.npc(player, npc, warning);
		}
		MonsterSlayerContactService.Result result = service.requestTask(player, CONTACTS[index]);
		if (!result.isAccepted()) { if (result.getReason().equals("active-task")) { com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task active = data.getTask(state.getActiveTaskKey()); dialogue.npc(player, npc, "Your current task is " + state.getActiveKills() + " of " + active.getRequiredKills() + " " + active.getDisplayName(data.getFamily(active.getFamilyKey()).getDisplayName()) + "."); } else dialogue.npc(player, npc, "Not yet. Your record is not ready for another task."); return; }
		if (shouldUseHobartFollowUpRemark(index, state.getTasksCompleted())) dialogue.npc(player, npc, hobartFollowUpRemark());
		if (shouldUseMaraAssignmentRemark(index, state)) dialogue.npc(player, npc, maraAssignmentRemark());
		String taskKey = MonsterSlayerState.read(player.getCache(), data).getActiveTaskKey();
		com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task task = data.getTask(taskKey);
		dialogue.npc(player, npc, "Your next task is to slay " + task.getRequiredKills() + " " + task.getDisplayName(data.getFamily(task.getFamilyKey()).getDisplayName()) + ".");
	}
	private void introduction(Player player, Npc npc, MonsterSlayerContactService service, MonsterSlayerState.Snapshot state, boolean shortcut) {
		if (shortcut) { npcsay(player, npc, "No stamp, no task. Fetch a Rising Sun ale first."); return; }
		if (state.getIntroStage() == 0) {
			npcsay(player, npc, "'Ello there! Looking for Monster Slayer work?");
			if (speakChoice(player, npc, "You bet I want to slay some monsters.", "Not today, thanks.") != 0) return;
			npcsay(player, npc, HOBART_MEMBERSHIP_PROOF[0]);
			say(player, npc, "Stamp?");
			npcsay(player, npc, HOBART_MEMBERSHIP_PROOF[1], HOBART_MEMBERSHIP_PROOF[2], HOBART_MEMBERSHIP_PROOF[3], HOBART_MEMBERSHIP_PROOF[4], HOBART_MEMBERSHIP_PROOF[5]);
			say(player, npc, "You can count on me!");
			service.beginIntroduction(player); return;
		}
		int[] offeredAles = MonsterSlayerContactService.eligibleRisingSunAleIds(player);
		if (offeredAles.length == 0) { npcsay(player, npc, "Still dry?", "Bring me a drink from the barmaid."); return; }
		npcsay(player, npc, "Ah, refreshments!", "A proper guild matter at last.");
		String[] choices = new String[offeredAles.length + 1];
		for (int choice = 0; choice < offeredAles.length; choice++) choices[choice] = MonsterSlayerContactService.risingSunAleOfferLabel(offeredAles[choice]);
		choices[choices.length - 1] = "Not yet";
		int selected = speakChoice(player, npc, choices);
		int selectedAleId = MonsterSlayerContactService.selectedRisingSunAleId(offeredAles, selected);
		if (selectedAleId == -1) return;
		MonsterSlayerContactService.Result result = service.completeIntroductionWithRisingSunAle(player, selectedAleId);
		if (!result.isAccepted()) { player.message(aleFailureMessage(result.getReason())); return; }
		npcsay(player, npc, HOBART_DRINK_RETURN[0]);
		say(player, npc, "Oh, you weren't kidding. This is like... a stamp.");
		npcsay(player, npc, HOBART_DRINK_RETURN[1]);
		npcsay(player, npc, "You're a Fledgling Monster Slayer now.", "Return when you're ready for work.");
	}
	/** Keeps transaction outcomes truthful without exposing persistence details to players. */
	public static String aleFailureMessage(String reason) {
		if ("missing-rising-sun-ale".equals(reason)) return "You haven't got a Rising Sun ale yet. Visit the barmaid and come back.";
		if ("state-write-failed".equals(reason)) return "Your Rising Sun ale was returned, but your Monster Slayer rank could not be recorded. Please try again.";
		if ("refund-failed".equals(reason)) return "Your rank record failed and your Rising Sun ale could not be returned. Please contact staff.";
		return "Your Monster Slayer record needs staff attention.";
	}
	private void associate(Player player, Npc npc, boolean trade) {
		int index = npc.getID() - FIRST_ASSOCIATE;
		try {
			if (!hostGuildAllows(player, index)) { npcsay(player, npc, "You need to meet this guild's normal entry requirements first."); return; }
			MonsterSlayerRank rank = MonsterSlayerState.read(player.getCache(), player.getWorld().getMonsterSlayerData()).getRank();
			if (rank.getCode() < index + 2) { MonsterSlayerRank required = MonsterSlayerRank.fromCode(index + 2); npcsay(player, npc, "Sorry, can't show you my wares till you're " + rankArticle(required) + " " + required.getDisplayName().toLowerCase() + "."); return; }
			if (trade) { MonsterSlayerChallengeShops.open(player, npc, CONTACTS[index]); return; }
			npcsay(player, npc, associateGreeting(index));
			int choice = speakChoice(player, npc, "Tell me about the supplies.", "I'd like to improve my backpack.", "Not now.");
			if (choice == 0) { npcsay(player, npc, associateSupplyLine(index)); return; }
			if (choice == 1) purchaseBackpackUpgrade(player, npc, index);
		} catch (RuntimeException ex) { player.message("Your Monster Slayer record needs staff attention."); }
	}

	/** One permanent, ordered entitlement per associate. This does not need a
	 * free inventory slot because the server expands admission capacity itself. */
	private void purchaseBackpackUpgrade(Player player, Npc npc, int index) {
		if (!player.supportsExpandedInventory()) {
			npcsay(player, npc, "Your client must be updated before I can expand this backpack safely.");
			return;
		}
		MonsterSlayerData data = player.getWorld().getMonsterSlayerData();
		MonsterSlayerShopService service = player.getWorld().getMonsterSlayerShopService();
		if (data == null || service == null) { player.message("Your Monster Slayer record needs staff attention."); return; }
		com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Shop shop = data.getShop(CONTACTS[index]);
		MonsterSlayerState.Snapshot state = MonsterSlayerState.read(player.getCache(), data);
		MonsterSlayerShopService.CapacityProposal proposal = service.proposeCapacityPurchase(state, shop.getKey());
		if (!proposal.isSuccessful()) { npcsay(player, npc, "That backpack upgrade is locked until you have the earlier Slayer promotions, upgrades, and points."); return; }
		long price = shop.getCapacityUpgrade().getCost().get(shop.getChallenge());
		int before = state.getDerivedInventoryCapacity();
		int after = proposal.getSnapshot().getDerivedInventoryCapacity();
		npcsay(player, npc, backpackUpgradeQuote(before, after, price, shop.getChallenge()));
		if (speakChoice(player, npc, "Buy the backpack upgrade.", "Back.") != 0) return;
		MonsterSlayerShopService.Result result = service.purchaseCapacity(player, shop.getKey());
		if (result.isSuccessful()) {
			ActionSender.sendInventory(player); // sends capacity before refreshed inventory contents
			npcsay(player, npc, "Done. Your backpack now holds " + after + " slots.");
		}
		else if ("locked-or-points".equals(result.getReason())) npcsay(player, npc, "You do not have the required points or prior backpack upgrades.");
		else npcsay(player, npc, "That backpack upgrade could not be completed. Nothing was spent.");
	}
	/** Exact confirmation text is kept deterministic so the quote always matches the typed server cost. */
	public static String backpackUpgradeQuote(int before, int after, long price,
			com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerChallenge challenge) {
		if (before < 0 || after <= before || price <= 0L || challenge == null) {
			throw new IllegalArgumentException("Invalid backpack-upgrade quote");
		}
		String tier = challenge.getDisplayName();
		return "I can expand your backpack from " + before + " to " + after + " slots for "
			+ price + " " + tier + " Slayer Points.";
	}
	/** Echoes a menu selection through normal player speech before its branch acts. */
	private int speakChoice(Player player, Npc npc, String... choices) {
		int selected = dialogue.choose(player, choices);
		String response = selectedChoice(selected, choices);
		if (response != null) dialogue.player(player, npc, response);
		return selected;
	}
	/** Read-only seam: menu choices must be rendered as player speech before a branch advances. */
	public static String selectedChoice(int selected, String... choices) {
		return selected >= 0 && selected < choices.length ? choices[selected] : null;
	}
	private static boolean managed(Npc npc) { return isContact(npc) || isAssociate(npc) || isAmbient(npc); }
	private static boolean isContact(Npc npc) { return npc.getID() >= FIRST_CONTACT && npc.getID() < FIRST_ASSOCIATE; }
	private static boolean isAssociate(Npc npc) { return npc.getID() >= FIRST_ASSOCIATE && npc.getID() < FIRST_AMBIENT; }
	private static boolean isAmbient(Npc npc) { return npc.getID() >= FIRST_AMBIENT && npc.getID() < FIRST_AMBIENT + 3; }
	private static String refusal(int index) { String[] lines = {"No stamp, no task. Fetch a Rising Sun ale first.", "I need an Adept sticker before I can put you on my Port Sarim list.", "No Veteran button, no Blue Moon work. Earn one, then come back.", "An Elite badge is the price of a Champion's contract!", "Champion's medal first. These contracts are not lessons.", "Hero's crest required. Return when you have earned it."}; return lines[index]; }
	private static String hobartFollowUpRemark() { return hobartFollowUpRemark(ThreadLocalRandom.current().nextInt(HOBART_FOLLOW_UP_REMARKS.length)); }
	/** Test seam for bounded, task-independent Hobart flavour dialogue. */
	public static String hobartFollowUpRemark(int index) {
		if (index < 0 || index >= HOBART_FOLLOW_UP_REMARKS.length) throw new IllegalArgumentException("Hobart dialogue index is out of range");
		return HOBART_FOLLOW_UP_REMARKS[index];
	}
	public static boolean shouldUseHobartFollowUpRemark(int contactIndex, long tasksCompleted) {
		return contactIndex == 0 && tasksCompleted > 0L;
	}
	/** Authoritative cursor zero plus no assignment identifies Mara's one-time first-task welcome. */
	public static boolean shouldUseMaraFirstTaskWelcome(int contactIndex, MonsterSlayerState.Snapshot state) {
		return contactIndex == 1 && state != null && state.getRank() == MonsterSlayerRank.INITIATE
			&& state.getActiveTaskKey() == null && state.getMandatoryCursors().get(CONTACTS[1]).intValue() == 0;
	}
	/** Later mandatory and repeatable assignments receive Mara-specific bounded flavour. */
	public static boolean shouldUseMaraAssignmentRemark(int contactIndex, MonsterSlayerState.Snapshot state) {
		return contactIndex == 1 && state != null && state.getActiveTaskKey() == null
			&& state.getMandatoryCursors().get(CONTACTS[1]).intValue() > 0;
	}
	private static String maraAssignmentRemark() { return maraAssignmentRemark(ThreadLocalRandom.current().nextInt(MARA_ASSIGNMENT_REMARKS.length)); }
	/** Test seam for Mara's bounded, assignment-only flavour. */
	public static String maraAssignmentRemark(int index) {
		if (index < 0 || index >= MARA_ASSIGNMENT_REMARKS.length) throw new IllegalArgumentException("Mara dialogue index is out of range");
		return MARA_ASSIGNMENT_REMARKS[index];
	}
	/** Defensive copy of Mara's first Port Sarim assignment welcome. */
	public static String[] maraFirstTaskWelcome() { return MARA_FIRST_TASK_WELCOME.clone(); }
	/** Exact short lines for the pre-membership flow; callers receive a defensive copy. */
	public static String[] hobartMembershipProofLines() { return HOBART_MEMBERSHIP_PROOF.clone(); }
	/** Exact short lines spoken after a selected eligible drink is accepted. */
	public static String[] hobartDrinkReturnLines() { return HOBART_DRINK_RETURN.clone(); }
	/** Contact-specific greeting seam; every later contact follows the same proof-first pattern. */
	public static String contactGreeting(int index) { String[] lines = {"Oh, it's you again. Another task then?", "Are you here to slay monsters?", "Back for another hard job? The Blue Moon has seen worse.", "Ah! An Elite hunter. Here for a real challenge?", "You came back. Ready for another contract?", "Another contract?"}; return lines[index]; }
	/** Natural yes/no responses; menu text is repeated as player speech before continuing. */
	public static String[] contactChoices(int index) { String[][] choices = {{"Yes please.", "Not now."}, {"Yes, I am.", "No, not today."}, {"Yes please.", "Not now."}, {"Yes please.", "Not now."}, {"Yes please.", "Not now."}, {"Yes please.", "Not now."}}; return choices[index].clone(); }
	/** The rank proof required before normal assignment is attempted. */
	public static String contactProof(int index) { String[] lines = {"Stamp?", "Let's see that Adept sticker.", "Button.", "Badge, if you please!", "Your medal.", "Crest."}; return lines[index]; }
	/** Player acknowledgement after a proof request but before a proof-gated refusal. */
	public static String missingProofResponse(int index) { String[] lines = {"Oh, I don't have one.", "Oh, I don't have one.", "Oh, I don't have one.", "Oh, I don't have one.", "Oh, I don't have one.", "Oh, I don't have one."}; return lines[index]; }
	/** Ineligible contacts direct the player to the immediately preceding task giver. */
	public static String contactRefusal(int index) { String[] lines = {"No stamp, no task. Fetch a Rising Sun ale first.", "You need an Adept sticker first. Hobart in Falador can help.", "I need a Veteran button. Earn it from Mara in Port Sarim.", "I need an Elite badge. Earn it from Bran at the Blue Moon Inn.", "Champion's medal first. Doran at the Champions Guild can help.", "Hero's crest required. Sella at the Heroes Guild can help."}; return lines[index]; }
	private boolean renderPromotion(Player player, Npc npc, int index) { try { for (MonsterSlayerDialoguePlan.Step step : MonsterSlayerDialoguePlan.promotion(index)) if (!dialogue.render(player, npc, step)) return false; return true; } catch (RuntimeException failure) { return false; } }
	/** Read-only dialogue seam: Talk-to must not enter the shop state machine. */
	public static String associateGreeting(int index) { String[] lines = {"An Adept has earned a look at the Fledgling supplies.", "A Veteran's button carries weight here. Your Adept supplies are available.", "An Elite hunter knows what to pack. Your Blue Moon supplies are available.", "A Champion is welcome at this quartermaster's counter.", "A Hero has earned access to Champion supplies.", "Legend is not a title we sell. Your Hero supplies are available."}; return lines[index]; }
	public static String associateSupplyLine(int index) { String[] lines = {"Use Trade when you are ready. Spend carefully; your Fledgling points are hard won.", "Use Trade when you are ready. Salt water ruins gear, not good preparation.", "Use Trade when you are ready. Pack for the job, not the story afterward.", "Use Trade when you are ready. A Champion brings the right kit.", "Use Trade when you are ready. A Hero knows that preparation saves lives.", "Use Trade when you are ready. That is all."}; return lines[index]; }
	public static boolean isAssociateShopOperation(String command) { return "Trade".equalsIgnoreCase(command) || "Shop".equalsIgnoreCase(command); }
	private static String warning(com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task task) { if (task.getHazards().isEmpty()) return null; StringBuilder text = new StringBuilder("Take care: "); for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard hazard : task.getHazards()) { if (text.length() > 11) text.append("; "); if (hazard == com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.DESERT_HEAT) text.append("bring desert heat protection"); else if (hazard == com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.WILDERNESS) text.append("this work is in the Wilderness"); else if (hazard == com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.PRAYER_DRAIN) text.append("expect Prayer drain"); else if (hazard == com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.POISON) text.append("bring an antidote for poison"); else text.append("prepare for dragon fire"); } return text.append('.').toString(); }
	private static String ambient(int index) { String[] lines = {"Fresh stamp, fresh start. I could take on a goblin with one hand!", "I keep my supplies packed and my journal dry. Sea air ruins both.", "The Blue Moon is quiet. The work outside it is not."}; return lines[index]; }
	private static boolean hostGuildAllows(Player player, int index) { return MonsterSlayerGuildAccess.allows(player, index); }
	private static String rankArticle(MonsterSlayerRank rank) { return rank == MonsterSlayerRank.INITIATE ? "an" : "a"; }
}
