package com.openrsc.server.net.rsc.handlers;


import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.model.Path;
import com.openrsc.server.model.Path.PathType;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.states.CombatState;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.WalkStruct;
import com.openrsc.server.plugins.triggers.EscapeNpcTrigger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class WalkRequest implements PayloadProcessor<WalkStruct, OpcodeIn> {
	private static final Logger LOGGER = LogManager.getLogger();

	@Override
	public void process(final WalkStruct payload, final Player player) throws Exception {

		OpcodeIn packetOpcode = payload.getOpcode();

		if (player.isBusy() && player.getMenuHandler() == null && !player.inCombat()) {
			if (player.getConfig().BATCH_PROGRESSION) {
				player.interruptPlugins();
			}
			return;
		}

		boolean retreatedFromCombat = false;
		player.resetMagicCombat();

		if (player.isHostile()) {
			if (player.getConfig().WANT_MYWORLD) {
				if (packetOpcode != OpcodeIn.WALK_TO_POINT) {
					return;
				}
				player.resetCombatEvent();
				player.clearHostility();
				player.setRanAwayTimer();
			} else if (packetOpcode == OpcodeIn.WALK_TO_POINT) {
				Mob opponent = player.getHostileTarget();
				if (opponent == null) {
					player.setSuspiciousPlayer(true, "walk request null opponent");
					return;
				}
				if (player.getDuel().isDuelActive() && player.getDuel().getDuelSetting(0)) {
					player.message("You cannot retreat from this duel!");
					return;
				}
				if (player.getDuel().isDuelActive()) {
					if (player.getAttribute("projectile", null) != null) {
						ProjectileEvent projectileEvent = player.getAttribute("projectile");
						projectileEvent.setCanceled(true);
					}
				}

				opponent.setLastOpponent(opponent.getOpponent());
				player.setLastOpponent(player.getHostileTarget());
				player.setCombatTimer();
				if (player.getHostileTarget().isPlayer()) {
					Player victimPlayer = ((Player) player.getHostileTarget());
					victimPlayer.message("Your opponent is retreating!");
					ActionSender.sendSound(victimPlayer, "retreat");
					victimPlayer.setRanAwayTimer();
				}
				player.setLastCombatState(CombatState.RUNNING);
				if (opponent.isPlayer()) {
					opponent.setLastCombatState(CombatState.RUNNING);
					opponent.resetCombatEvent();
					opponent.setRanAwayTimer();
				} else {
					Npc npcOpponent = (Npc) opponent;
					if (!player.getAutoRetaliate()) {
						npcOpponent.setLastCombatState(CombatState.WAITING);
					} else {
						npcOpponent.setLastCombatState(CombatState.WAITING);
						npcOpponent.resetCombatEvent();
					}
				}
				player.resetCombatEvent();
				player.clearHostility(); // Clear hostility when retreating
				player.setRanAwayTimer();
				ActionSender.sendSound(player, "retreat");
				retreatedFromCombat = true;

				if (player.getConfig().WANT_PARTIES) {
					if(player.getParty() != null){
						player.getParty().sendParty();
					}
				}
				if (opponent.isPlayer() && opponent.getConfig().WANT_PARTIES) {
					if(((Player) opponent).getParty() != null){
						((Player) opponent).getParty().sendParty();
					}
				}

				if (opponent.isNpc()) {
					player.getWorld().getServer().getPluginHandler().handlePlugin(EscapeNpcTrigger.class, player, new Object[]{player, ((Npc) opponent)});
				}
				if (!player.getAutoRetaliate()) {
					player.setWalkToAction(null);
					player.resetAll();
				}
			} else {
				return;
			}
		}

		// Only reset all if we didn't already handle retreat (which does its own cleanup)
		if (!retreatedFromCombat) {
			player.resetAll();
		}
		player.resetPath();

		int firstStepX = payload.firstStep.getX();
		int firstStepY = payload.firstStep.getY();
		PathType pathType = packetOpcode == OpcodeIn.WALK_TO_POINT ? PathType.WALK_TO_POINT : PathType.WALK_TO_ENTITY;
		Path path = new Path(player, pathType);
		{
			path.addStep(firstStepX, firstStepY);
			for (Point step : payload.steps) {
				path.addStep(firstStepX + step.getX(), firstStepY + step.getY());
			}
			path.finish();
		}
		if (player.getAttribute("debug_walk_trace", false)) {
			final ArrayList<String> samples = new ArrayList<>();
			final int sampleCount = Math.min(payload.steps.size(), 5);
			for (int i = 0; i < sampleCount; i++) {
				Point step = payload.steps.get(i);
				samples.add((firstStepX + step.getX()) + "," + (firstStepY + step.getY()));
			}
			player.setAttribute("debug_walk_trace_budget", 6);
			LOGGER.info("WALK_TRACE request player={} opcode={} start={},{} firstStep={},{} payloadSteps={} pathSize={} samples={}",
				player.getUsername(),
				packetOpcode,
				player.getX(), player.getY(),
				firstStepX, firstStepY,
				payload.steps.size(),
				path.size(),
				samples);
		}
		player.getWalkingQueue().setPath(path);
	}
}
