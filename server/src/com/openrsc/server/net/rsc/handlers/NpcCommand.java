package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.content.Summoning;
import com.openrsc.server.external.NPCDef;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.action.WalkToMobAction;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcInteraction;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.TargetMobStruct;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;

public final class NpcCommand implements PayloadProcessor<TargetMobStruct, OpcodeIn> {

	public void process(TargetMobStruct payload, Player player) throws Exception {
		OpcodeIn pID = payload.getOpcode();
		int serverIndex = payload.serverIndex;
		if (player == null) return;
		if (!player.getConfig().WANT_MYWORLD && player.inCombat()) {
			player.message("You can't do that whilst you are fighting");
			return;
		}
		if (player.isBusy()) {
			return;
		}

		final boolean click = pID == OpcodeIn.NPC_COMMAND;
		player.click = click ? 0 : 1;
		final Npc affectedNpc = player.getWorld().getNpc(serverIndex);
		if (affectedNpc == null) return;
		if (click && Summoning.isOwnedSummon(player, affectedNpc)) {
			if (Summoning.isArmorSummon(affectedNpc)) {
				player.message("Your spirit companion is bound to your armor.");
				return;
			}
			Summoning.dismissManualSummon(player);
			player.message("You dismiss your summon.");
			return;
		}
		NPCDef def = affectedNpc.getDef();
		String command = (click ? def.getCommand1() : def.getCommand2()).toLowerCase();
		boolean isPickpocket = command.equalsIgnoreCase("pickpocket");
		boolean isGnomeballOp = command.equalsIgnoreCase("tackle") || command.equalsIgnoreCase("pass to");
		int followRadius = isPickpocket
			&& player.withinRange(affectedNpc, 1)
			&& PathValidation.checkAdjacentDistance(player.getWorld(), player.getX(), player.getY(), affectedNpc.getX(), affectedNpc.getY(), true)
			? 0 : 1;
		// Don't believe that the player would follow during pickpocketing if they were on the same tile. If they do, they follow under the NPC for one tick, which has not been seen on footage review.
		if (!isPickpocket || !player.getLocation().equals(affectedNpc.getLocation())) player.setFollowing(affectedNpc, followRadius, true, true);
		int radius = 1;

		player.setWalkToAction(new WalkToMobAction(player, affectedNpc, radius) {
			public void executeInternal() {
				NpcInteraction interaction = isGnomeballOp ? NpcInteraction.NPC_GNOMEBALL_OP : NpcInteraction.NPC_OP;
				if (getPlayer().isBusy() || getPlayer().isRanging()) {
					return;
				}
				getPlayer().resetAll(true, false);
				NpcInteraction.setInteractions(affectedNpc, getPlayer(), interaction);

				getPlayer().getWorld().getServer().getPluginHandler().handlePlugin(OpNpcTrigger.class, getPlayer(), new Object[]{getPlayer(), affectedNpc, command}, this);
			}
		});
	}

}
