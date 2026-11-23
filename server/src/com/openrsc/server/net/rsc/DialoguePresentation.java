package com.openrsc.server.net.rsc;

import com.openrsc.server.event.rsc.PluginTask;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.PacketBuilder;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Optional dialogue presentation scoped to the script running a conversation. */
public final class DialoguePresentation implements AutoCloseable {
	static final String CONFIG_TAG = "dialogue-presentation/1";
	private static final int OPCODE = 166;
	private static final int VERSION = 1;
	private static final int OPEN = 1;
	private static final int PLAYER_LINE = 2;
	private static final int NPC_LINE = 3;
	private static final int CLOSE = 4;
	private static final boolean ENABLED = Boolean.getBoolean("spoiledmilk.dialoguePreview");
	private static final AtomicInteger NEXT_ID = new AtomicInteger();

	private final PluginTask task;
	private final Player player;
	private final Npc npc;
	private final int id;

	private DialoguePresentation(PluginTask task, Player player, Npc npc) {
		this.task = task;
		this.player = player;
		this.npc = npc;
		id = NEXT_ID.updateAndGet(value -> value == Integer.MAX_VALUE ? 1 : value + 1);
	}

	public static boolean enabled() {
		return ENABLED;
	}

	public static DialoguePresentation open(Player player, Npc npc) {
		if (!ENABLED || !player.isDialoguePresentationReady()) return null;
		PluginTask task = PluginTask.getContextPluginTask();
		if (task == null || task.getDialoguePresentation() != null) return null;
		DialoguePresentation presentation = new DialoguePresentation(task, player, npc);
		task.setDialoguePresentation(presentation);
		player.write(presentation.packet(OPEN).writeShort(npc.getID()).toPacket());
		return presentation;
	}

	private static DialoguePresentation current(Player player) {
		if (!ENABLED || !player.isDialoguePresentationReady()) return null;
		PluginTask task = PluginTask.getContextPluginTask();
		DialoguePresentation presentation = task == null ? null : task.getDialoguePresentation();
		return presentation != null && presentation.player == player ? presentation : null;
	}

	public static int currentId(Player player) {
		DialoguePresentation presentation = current(player);
		return presentation == null ? 0 : presentation.id;
	}

	public static void line(Player player, Npc npc, boolean playerSpeaking, String message) {
		DialoguePresentation presentation = current(player);
		if (presentation == null || presentation.npc != npc) return;
		byte[] text = message.getBytes(StandardCharsets.UTF_8);
		if (text.length > 2048) return;
		player.write(presentation.packet(playerSpeaking ? PLAYER_LINE : NPC_LINE).write(text).toPacket());
	}

	@Override
	public void close() {
		task.setDialoguePresentation(null);
		player.write(packet(CLOSE).toPacket());
	}

	private PacketBuilder packet(int type) {
		return new PacketBuilder(OPCODE).writeByte(VERSION).writeByte(type).writeInt(id);
	}
}
