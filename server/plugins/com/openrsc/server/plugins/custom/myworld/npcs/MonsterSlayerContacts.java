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
	/** Injectable boundary keeps promotion acknowledgement contingent on rendering. */
	public interface DialogueRenderer { boolean render(Player player, Npc npc, MonsterSlayerDialoguePlan.Step step); }
	private static final int FIRST_CONTACT = 846;
	private static final int FIRST_ASSOCIATE = 852;
	private static final int FIRST_AMBIENT = 858;
	private static final String[] CONTACTS = {"falador", "port_sarim", "brimhaven", "champions", "heroes", "legends"};
	private static final MonsterSlayerRank[] REQUIRED = {MonsterSlayerRank.FLEDGLING, MonsterSlayerRank.INITIATE, MonsterSlayerRank.VETERAN, MonsterSlayerRank.ELITE, MonsterSlayerRank.CHAMPION, MonsterSlayerRank.HERO};
	private static final String[] HOBART_FOLLOW_UP_REMARKS = {
		"Right then, back to it. Try not to make a spectacle of yourself.",
		"Good. I was starting to think you'd gone soft.",
		"Keep your blade sharp and your excuses shorter.",
		"There's always another mess needing a capable pair of hands.",
		"That's the spirit. Don't keep the monsters waiting."
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
		if (!hostGuildAllows(player, index)) { npcsay(player, npc, "You need to meet this guild's normal entry requirements first."); return; }
		if (!state.getRank().isAtLeast(REQUIRED[index])) { npcsay(player, npc, refusal(index)); return; }
		if (state.getRank().isAtLeast(data.getContact(CONTACTS[index]).getAwardedRank())
			&& state.getMandatoryCursors().get(CONTACTS[index]).intValue() == data.getContact(CONTACTS[index]).getMandatoryTasks().size()) {
			if (!state.isPromotionAcknowledged(CONTACTS[index], data)) {
				if (!renderPromotion(player, npc, index)) return;
				if (!service.acknowledgePromotion(player, CONTACTS[index]).isAccepted()) { player.message("Your Monster Slayer promotion could not be recorded."); return; }
			}
		}
		if (!shortcut) {
			npcsay(player, npc, greeting(index));
			if (speakChoice(player, npc, "Yes please.", "Not now.") != 0) return;
			npcsay(player, npc, proof(index));
		}
		com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task preview = service.previewTask(player, CONTACTS[index]);
		if (preview != null) {
			String warning = warning(preview);
			if (warning != null) npcsay(player, npc, warning);
		}
		MonsterSlayerContactService.Result result = service.requestTask(player, CONTACTS[index]);
		if (!result.isAccepted()) { if (result.getReason().equals("active-task")) { com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task active = data.getTask(state.getActiveTaskKey()); npcsay(player, npc, "Your current task is " + state.getActiveKills() + " of " + active.getRequiredKills() + " " + active.getDisplayName(data.getFamily(active.getFamilyKey()).getDisplayName()) + "."); } else npcsay(player, npc, "Not yet. Your record is not ready for another task."); return; }
		if (shouldUseHobartFollowUpRemark(index, state.getTasksCompleted())) npcsay(player, npc, hobartFollowUpRemark());
		String taskKey = MonsterSlayerState.read(player.getCache(), data).getActiveTaskKey();
		com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task task = data.getTask(taskKey);
		npcsay(player, npc, "Your next task is to slay " + task.getRequiredKills() + " " + task.getDisplayName(data.getFamily(task.getFamilyKey()).getDisplayName()) + ".");
	}
	private void introduction(Player player, Npc npc, MonsterSlayerContactService service, MonsterSlayerState.Snapshot state, boolean shortcut) {
		if (shortcut) { npcsay(player, npc, "No stamp, no task. Fetch a Rising Sun ale first."); return; }
		if (state.getIntroStage() == 0) {
			npcsay(player, npc, "'Ello there! Looking for Monster Slayer work?");
			if (speakChoice(player, npc, "I'm looking to join The Monster Slayer's Guild.", "Not today, thanks.") != 0) return;
			npcsay(player, npc, "Splendid! First, I need proof you're serious.", "Bring me a drink from that barmaid.", "Then I'll stamp you into the guild.");
			service.beginIntroduction(player); return;
		}
		int[] offeredAles = MonsterSlayerContactService.eligibleRisingSunAleIds(player);
		if (offeredAles.length == 0) { npcsay(player, npc, "Still dry? A fledgling Slayer needs bravery, preparedness, and a drink from that barmaid over there."); return; }
		npcsay(player, npc, "Ah, you've returned with refreshments. A proper administrative matter at last!");
		String[] choices = new String[offeredAles.length + 1];
		for (int choice = 0; choice < offeredAles.length; choice++) choices[choice] = MonsterSlayerContactService.risingSunAleOfferLabel(offeredAles[choice]);
		choices[choices.length - 1] = "Not yet";
		int selected = speakChoice(player, npc, choices);
		int selectedAleId = MonsterSlayerContactService.selectedRisingSunAleId(offeredAles, selected);
		if (selectedAleId == -1) return;
		MonsterSlayerContactService.Result result = service.completeIntroductionWithRisingSunAle(player, selectedAleId);
		if (!result.isAccepted()) { player.message(aleFailureMessage(result.getReason())); return; }
		npcsay(player, npc, "Excellent choice. That goes down much more neatly than a form.", "Hold still while I stamp you in. If it smudges, tell people it's a badge.");
		say(player, npc, "That doesn't sound very official.");
		npcsay(player, npc, "Official enough! You're a Fledgling Monster Slayer now. Return to me whenever you're ready for honest work and questionable paperwork.");
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
			if (rank.getCode() < index + 2) { npcsay(player, npc, "Sorry, can't show you my wares till you're a " + MonsterSlayerRank.fromCode(index + 2).name().toLowerCase() + "."); return; }
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
		String tier = challenge.name().substring(0, 1) + challenge.name().substring(1).toLowerCase();
		return "I can expand your backpack from " + before + " to " + after + " slots for "
			+ price + " " + tier + " Slayer Points.";
	}
	/** Echoes a menu selection through normal player speech before its branch acts. */
	private static int speakChoice(Player player, Npc npc, String... choices) {
		int selected = multi(player, choices);
		String response = selectedChoice(selected, choices);
		if (response != null) say(player, npc, response);
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
	private static String refusal(int index) { String[] lines = {"No stamp, no task. Fetch a Rising Sun ale first.", "I need an Initiate sticker before I can put you on my Port Sarim list.", "No Veteran button, no Blue Moon work. Earn one, then come back.", "An Elite badge is the price of a Champion's contract!", "Champion's medal first. These contracts are not lessons.", "Hero's crest required. Return when you have earned it."}; return lines[index]; }
	private static String hobartFollowUpRemark() { return hobartFollowUpRemark(ThreadLocalRandom.current().nextInt(HOBART_FOLLOW_UP_REMARKS.length)); }
	/** Test seam for bounded, task-independent Hobart flavour dialogue. */
	public static String hobartFollowUpRemark(int index) {
		if (index < 0 || index >= HOBART_FOLLOW_UP_REMARKS.length) throw new IllegalArgumentException("Hobart dialogue index is out of range");
		return HOBART_FOLLOW_UP_REMARKS[index];
	}
	public static boolean shouldUseHobartFollowUpRemark(int contactIndex, long tasksCompleted) {
		return contactIndex == 0 && tasksCompleted > 0L;
	}
	private static String greeting(int index) { String[] lines = {"Oh, it's you again. Another task then?", "Back for work, are you?", "Back for another hard job? The Blue Moon has seen worse.", "Ah! An Elite hunter. Here for a real challenge?", "You came back. Ready for another contract?", "Another contract?"}; return lines[index]; }
	private static String proof(int index) { String[] lines = {"Stamp?", "Let's see that sticker.", "Button.", "Badge, if you please!", "Your medal.", "Crest."}; return lines[index]; }
	private boolean renderPromotion(Player player, Npc npc, int index) { try { for (MonsterSlayerDialoguePlan.Step step : MonsterSlayerDialoguePlan.promotion(index)) if (!dialogue.render(player, npc, step)) return false; return true; } catch (RuntimeException failure) { return false; } }
	/** Read-only dialogue seam: Talk-to must not enter the shop state machine. */
	public static String associateGreeting(int index) { String[] lines = {"An Initiate has earned a look at the Fledgling supplies.", "A Veteran's button carries weight here. Your Initiate supplies are available.", "An Elite hunter knows what to pack. Your Blue Moon supplies are available.", "A Champion is welcome at this quartermaster's counter.", "A Hero has earned access to Champion supplies.", "Legend is not a title we sell. Your Hero supplies are available."}; return lines[index]; }
	public static String associateSupplyLine(int index) { String[] lines = {"Use Trade when you are ready. Spend carefully; your Fledgling points are hard won.", "Use Trade when you are ready. Salt water ruins gear, not good preparation.", "Use Trade when you are ready. Pack for the job, not the story afterward.", "Use Trade when you are ready. A Champion brings the right kit.", "Use Trade when you are ready. A Hero knows that preparation saves lives.", "Use Trade when you are ready. That is all."}; return lines[index]; }
	public static boolean isAssociateShopOperation(String command) { return "Trade".equalsIgnoreCase(command) || "Shop".equalsIgnoreCase(command); }
	private static String warning(com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Task task) { if (task.getHazards().isEmpty()) return null; StringBuilder text = new StringBuilder("Take care: "); for (com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard hazard : task.getHazards()) { if (text.length() > 11) text.append("; "); if (hazard == com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.DESERT_HEAT) text.append("bring desert heat protection"); else if (hazard == com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.WILDERNESS) text.append("this work is in the Wilderness"); else if (hazard == com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.PRAYER_DRAIN) text.append("expect Prayer drain"); else if (hazard == com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerHazard.POISON) text.append("bring an antidote for poison"); else text.append("prepare for dragon fire"); } return text.append('.').toString(); }
	private static String ambient(int index) { String[] lines = {"Fresh stamp, fresh start. I could take on a goblin with one hand!", "I keep my supplies packed and my journal dry. Sea air ruins both.", "The Blue Moon is quiet. The work outside it is not."}; return lines[index]; }
	private static boolean hostGuildAllows(Player player, int index) { return MonsterSlayerGuildAccess.allows(player, index); }
}
