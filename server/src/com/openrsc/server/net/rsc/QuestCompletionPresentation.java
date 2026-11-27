package com.openrsc.server.net.rsc;

import com.openrsc.server.event.rsc.PluginTask;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.PacketBuilder;
import com.openrsc.server.plugins.QuestInterface;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Describes rewards issued by the completing script, after its existing award logic. */
public final class QuestCompletionPresentation {
	public static final String CLIENT_ATTRIBUTE = "quest_completion_presentation";
	static final String CONFIG_TAG = "quest-completion/1";
	private static final boolean ENABLED = Boolean.getBoolean("spoiledmilk.questRewardPreview");
	private static final int OPCODE = 167;
	private static final int MAX_ROWS = 120;
	private static final int MAX_TEXT_BYTES = 240;
	private static final ThreadLocal<QuestCompletionPresentation> CURRENT = new ThreadLocal<>();

	public enum Destination { INVENTORY, BANK, GROUND }

	private final Player player;
	private final int questId;
	private final int pointsBefore;
	private final Map<Integer, Long> experience = new LinkedHashMap<>();
	private final List<ItemReward> items = new ArrayList<>();

	private QuestCompletionPresentation(Player player, int questId) {
		this.player = player;
		this.questId = questId;
		pointsBefore = player.getQuestPoints();
	}

	public static boolean enabled() { return ENABLED; }

	public static void award(Player player, int questId, Runnable giveRewards) {
		if (!ENABLED || !player.getAttribute(CLIENT_ATTRIBUTE, false) || player.getQuestStage(questId) < 0) {
			giveRewards.run();
			return;
		}
		finishScript();
		CURRENT.set(new QuestCompletionPresentation(player, questId));
		boolean scripted = PluginTask.getContextPluginTask() != null;
		try {
			giveRewards.run();
			if (!scripted) finishScript();
		} catch (RuntimeException | Error failure) {
			discardScript();
			throw failure;
		}
	}

	public static void finishScript() {
		if (!ENABLED) return;
		QuestCompletionPresentation current = CURRENT.get();
		if (current == null) return;
		CURRENT.remove();
		if (current.player.getQuestStage(current.questId) < 0) current.send();
	}

	public static void discardScript() {
		if (ENABLED) CURRENT.remove();
	}

	public static void experience(Player player, int skill, long amount) {
		if (!ENABLED || amount <= 0) return;
		QuestCompletionPresentation current = CURRENT.get();
		if (current != null && current.player == player) current.experience.merge(skill, amount, Long::sum);
	}

	public static void item(Player player, int id, int amount, boolean noted, Destination destination) {
		if (!ENABLED || amount <= 0) return;
		QuestCompletionPresentation current = CURRENT.get();
		if (current != null && current.player == player) current.addItem(id, amount, noted, destination);
	}

	public static void groundItem(GroundItem item) {
		if (!ENABLED || item.getAmount() <= 0) return;
		QuestCompletionPresentation current = CURRENT.get();
		if (current != null && current.player.getWorld() == item.getWorld()
			&& current.player.getUsernameHash() == item.getOwnerUsernameHash()) {
			current.addItem(item.getID(), item.getAmount(), item.getNoted(), Destination.GROUND);
		}
	}

	private void addItem(int id, int amount, boolean noted, Destination destination) {
		for (ItemReward item : items) {
			if (item.id == id && item.noted == noted && item.destination == destination) {
				item.amount += amount;
				return;
			}
		}
		if (items.size() < MAX_ROWS) items.add(new ItemReward(id, amount, noted, destination));
	}

	private void send() {
		QuestInterface quest = player.getWorld().getQuest(questId);
		List<String> rows = new ArrayList<>();
		int totalPoints = player.getQuestPoints();
		int points = totalPoints - pointsBefore;
		if (points > 0) rows.add(points + (points == 1 ? " quest point" : " quest points") + " (" + totalPoints + " total)");
		experience.forEach((skill, amount) -> {
			String name = player.getWorld().getServer().getConstants().getSkills().getSkillDisplayName(skill);
			rows.add(formatExperience(amount) + " " + name + " XP");
		});
		for (ItemReward item : items) {
			String name = player.getWorld().getServer().getEntityHandler().getItemDef(item.id).getName();
			String delivery = item.destination == Destination.BANK ? " (banked)" : item.destination == Destination.GROUND ? " (on the ground)" : "";
			rows.add(item.amount + " x " + name + (item.noted ? " (noted)" : "") + delivery);
		}
		rows.addAll(quest.getCompletionUnlocks());
		PacketBuilder packet = new PacketBuilder(OPCODE).writeByte(1);
		writeText(packet, quest.getQuestName());
		int count = Math.min(MAX_ROWS, rows.size());
		packet.writeByte(count);
		for (int i = 0; i < count; i++) writeText(packet, rows.get(i));
		player.write(packet.toPacket());
	}

	private static String formatExperience(long quarters) {
		String[] fractions = {"", ".25", ".5", ".75"};
		return String.format(java.util.Locale.ROOT, "%,d", quarters / 4) + fractions[(int) (quarters % 4)];
	}

	private static void writeText(PacketBuilder packet, String text) {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		if (bytes.length > MAX_TEXT_BYTES) {
			int end = text.length();
			do { end = text.offsetByCodePoints(end, -1); }
			while (text.substring(0, end).getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES - 3);
			bytes = (text.substring(0, end) + "...").getBytes(StandardCharsets.UTF_8);
		}
		packet.writeByte(bytes.length).write(bytes);
	}

	private static final class ItemReward {
		final int id;
		final boolean noted;
		final Destination destination;
		long amount;

		ItemReward(int id, int amount, boolean noted, Destination destination) {
			this.id = id;
			this.amount = amount;
			this.noted = noted;
			this.destination = destination;
		}
	}
}
