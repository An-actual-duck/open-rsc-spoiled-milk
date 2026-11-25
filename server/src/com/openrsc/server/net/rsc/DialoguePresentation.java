package com.openrsc.server.net.rsc;

import com.openrsc.server.event.rsc.PluginTask;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.PacketBuilder;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Optional dialogue presentation scoped to the script running a conversation. */
public final class DialoguePresentation {
	static final String CONFIG_TAG = "dialogue-presentation/1";
	private static final int OPCODE = 166;
	private static final int VERSION = 1;
	private static final int OPEN = 1;
	private static final int PLAYER_LINE = 2;
	private static final int NPC_LINE = 3;
	private static final int CLOSE = 4;
	private static final boolean ENABLED = Boolean.getBoolean("spoiledmilk.dialoguePreview");
	private static final AtomicInteger NEXT_ID = new AtomicInteger();

	private final Player player;
	private Npc npc;
	private final int id;

	private DialoguePresentation(Player player) {
		this.player = player;
		id = NEXT_ID.updateAndGet(value -> value == Integer.MAX_VALUE ? 1 : value + 1);
	}

	public static boolean enabled() {
		return ENABLED;
	}

	private static PluginTask eligibleTask(Player player) {
		if (!ENABLED || !player.isDialoguePresentationReady()) return null;
		PluginTask task = PluginTask.getContextPluginTask();
		return task != null && task.getOwner() == player ? task : null;
	}

	public static int currentId(Player player) {
		PluginTask task = eligibleTask(player);
		DialoguePresentation presentation = task == null ? null : task.getDialoguePresentation();
		return presentation == null ? 0 : presentation.id;
	}

	public static void line(Player player, Npc npc, boolean playerSpeaking, String message) {
		if (npc == null || message.isEmpty()) return;
		PluginTask task = eligibleTask(player);
		if (task == null) return;
		byte[] text = message.getBytes(StandardCharsets.UTF_8);
		if (text.length > 2048) return;

		DialoguePresentation presentation = task.getDialoguePresentation();
		if (presentation == null) {
			presentation = new DialoguePresentation(player);
			task.setDialoguePresentation(presentation);
		}
		presentation.sendLine(npc, playerSpeaking, text);
	}

	private void sendLine(Npc participant, boolean playerSpeaking, byte[] text) {
		if (npc != participant) {
			npc = participant;
			player.write(packet(OPEN).writeShort(npc.getID()).toPacket());
		}
		player.write(packet(playerSpeaking ? PLAYER_LINE : NPC_LINE).write(text).toPacket());
	}

	public void close() {
		player.write(packet(CLOSE).toPacket());
	}

	private PacketBuilder packet(int type) {
		return new PacketBuilder(OPCODE).writeByte(VERSION).writeByte(type).writeInt(id);
	}
}
