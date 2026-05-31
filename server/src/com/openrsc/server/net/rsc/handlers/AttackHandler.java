package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.handler.GameEventHandler;
import com.openrsc.server.event.rsc.impl.projectile.MagicCombatEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeUtils;
import com.openrsc.server.event.rsc.impl.projectile.ThrowingEvent;
import com.openrsc.server.model.action.ActionType;
import com.openrsc.server.model.action.WalkToMobAction;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcInteraction;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.TargetMobStruct;
import com.openrsc.server.plugins.triggers.AttackNpcTrigger;
import com.openrsc.server.plugins.triggers.AttackPlayerTrigger;

import static com.openrsc.server.plugins.Functions.inArray;

public class AttackHandler implements PayloadProcessor<TargetMobStruct, OpcodeIn> {
	public void process(TargetMobStruct payload, Player player) throws Exception {
		OpcodeIn pID = payload.getOpcode();
		Mob affectedMob = null;
		if (pID == OpcodeIn.PLAYER_ATTACK) {
			affectedMob = player.getWorld().getPlayer(payload.serverIndex);
		} else if (pID == OpcodeIn.NPC_ATTACK) {
			affectedMob = player.getWorld().getNpc(payload.serverIndex);
		}
		if (affectedMob == null || affectedMob.equals(player)) {
			player.resetPath();
			return;
		}

		if (pID == OpcodeIn.PLAYER_ATTACK && !player.getConfig().WANT_PVP) {
			player.message(player.getConfig().WANT_MYWORLD
				? "This is a PvM-only world"
				: "You can't attack other players on this world");
			player.resetPath();
			return;
		}

		boolean retargetingNpcWhileInCombat = canRetargetNpcWhileInCombat(player, affectedMob);

		boolean retargetingNpcWithRangedWhileInCombat = player.inCombat()
			&& affectedMob.isNpc()
			&& (player.getRangeEquip() >= 0 || player.getThrowingEquip() >= 0)
			&& player.getOpponent() != null
			&& player.getOpponent().isNpc()
			&& !player.getOpponent().equals(affectedMob)
			&& !player.getDuel().isDueling();
		boolean autoCastingNpcWhileInCombat = player.inCombat()
			&& player.getAutoCastSpell() != null
			&& affectedMob.isNpc()
			&& !player.getDuel().isDueling();

		if (player.inCombat() && !retargetingNpcWhileInCombat && !retargetingNpcWithRangedWhileInCombat && !autoCastingNpcWhileInCombat) {
			player.message("You are already busy fighting!");
			player.resetPath();
			return;
		}

		if (player.getDuel().isDueling()) {
			return;
		}

		if (player.isBusy() && !retargetingNpcWhileInCombat && !retargetingNpcWithRangedWhileInCombat && !autoCastingNpcWhileInCombat) {
			player.resetPath();
			return;
		}

		if (retargetingNpcWhileInCombat || retargetingNpcWithRangedWhileInCombat || autoCastingNpcWhileInCombat) {
			player.resetCombatEvent();
		}

		player.resetAll();

		if (affectedMob.isPlayer()) {
			assert affectedMob instanceof Player;
			Player pl = (Player) affectedMob;
			//Immune players cannot be attacked until their immunity wears off.
			if (!pl.canBeReattacked()) {
				if (pl.getLocation().inWilderness() || player.getConfig().USES_PK_MODE) {
					player.resetPath();
				}
				return;
			}
		} else {
			assert affectedMob instanceof Npc;
			Npc n = (Npc) affectedMob;
			if (Summoning.isOwnedUtilitySummon(player, n)) {
				player.resetPath();
				Summoning.openUtilitySummon(player, n);
				return;
			}
			if (Summoning.isSummon(n)) {
				player.message("You can't attack a summon.");
				player.resetPath();
				return;
			}
			long curTick = player.getWorld().getServer().getCurrentTick();
			long runTick = n.getRanAwayTimer();
			if (n.isRespawning()) return;
			if (n.getX() == 0 && n.getY() == 0)
				return;
			if (n.getID() == NpcId.OGRE_TRAINING_CAMP.id()) {
				boolean melee = player.getRangeEquip() < 0 && player.getThrowingEquip() < 0;
				boolean inPen = player.getX() >= 663 && player.getX() <= 668
					&& player.getY() >= 531 && player.getY() <= 535;
				if (melee || inPen) {
					player.message("these ogres are for range combat training only");
					return;
				}
			} else if (inArray(n.getID(), NpcId.BATTLE_MAGE_GUTHIX.id(), NpcId.BATTLE_MAGE_ZAMORAK.id(), NpcId.BATTLE_MAGE_SARADOMIN.id())
				&& (!player.getCache().hasKey("mage_arena") || player.getCache().getInt("mage_arena") < 2)) {
				player.message("you are not yet ready to fight the battle mages");
				return;
			} else if (!n.isHostileToward(player) && (curTick <= runTick || (curTick <= runTick + 1 && !n.finishedPath()))) {
				//Moving retreating enemies are immune from attack requests for an extra tick.
				player.resetPath();
				return;
			}
		}

		if (player.getAutoCastSpell() != null && MagicCombatEvent.start(player, affectedMob)) {
			return;
		}

		if (player.getRangeEquip() < 0 && player.getThrowingEquip() < 0) {

			if (affectedMob.isPlayer() && !player.finishedPath() && !affectedMob.finishedPath()) {
				int pidlessCatchingDistanceOffset = 0;
				if (player.getConfig().PIDLESS_CATCHING && !player.willBeProcessedBefore((Player)affectedMob)) {
					// other player has already moved this tick, meaning the gap is 1 more than is rendered on either person's client
					pidlessCatchingDistanceOffset += 1;
				}

				// authentically, if you're more than a couple tiles away while already moving, the attack packet just resets your path.
				// https://www.youtube.com/watch?v=ia02boQlVts&t=1131s
				 if (player.getLocation().getDistancePythagoras(affectedMob.getLocation()) > player.getConfig().MAX_PVP_MELEE_ATTACK_DISTANCE + pidlessCatchingDistanceOffset) {
					 player.resetPath();
					 return;
				 }
			}

			int radius = affectedMob.isPlayer() ? player.getConfig().PVP_CATCHING_DISTANCE : player.getConfig().PVM_CATCHING_DISTANCE;
			int attackRadius = radius + RangeUtils.PLAYER_COMBAT_RANGE_BONUS;
			int approachRadius = RangeUtils.getApproachRadius(attackRadius);
			int walkRadius = player.withinRange(affectedMob, attackRadius) ? attackRadius : approachRadius;
			int followRadius = player.getConfig().WANT_MYWORLD ? walkRadius : 0;
			player.setFollowing(affectedMob, followRadius, false, true);

			player.setWalkToAction(new WalkToMobAction(player, affectedMob, walkRadius, true, ActionType.ATTACK) {
				public void executeInternal() {
					getPlayer().resetFollowing();

					if (!getPlayer().getConfig().WANT_MYWORLD && mob.inCombat() && getPlayer().getRangeEquip() < 0 && getPlayer().getThrowingEquip() < 0) {
						if (mob.isNpc()) {
							Npc npc = (Npc) mob;
							if (npc.tryTakeMeleeFocus(getPlayer())) {
								return;
							}
							getPlayer().startCombat(npc);
							return;
						}
						getPlayer().message("I can't get close enough");
						return;
					}
					if (getPlayer().isBusy() || mob.isBusy() || !getPlayer().checkAttack(mob, false)) {
						return;
					}
					if (mob.isNpc()) {
						NpcInteraction interaction = NpcInteraction.NPC_ATTACK;
						NpcInteraction.setInteractions(((Npc)mob), getPlayer(), interaction);
						getPlayer().getWorld().getServer().getPluginHandler().handlePlugin(AttackNpcTrigger.class, getPlayer(), new Object[]{getPlayer(), (Npc) mob}, this);
					} else {
						getPlayer().getWorld().getServer().getPluginHandler().handlePlugin(AttackPlayerTrigger.class, getPlayer(), new Object[]{getPlayer(), mob}, this);
					}
				}
			});
		} else { // Attack with ranged instead of melee
			if (!player.checkAttack(affectedMob, true)) {
				return;
			}
			final Mob target = affectedMob;
			player.resetPath();
			int radius = player.getProjectileRadius();
			int approachRadius = player.getProjectileApproachRadius();
			int walkRadius = player.withinRange(affectedMob, radius) ? radius : approachRadius;
			player.setFollowing(affectedMob, walkRadius, false);
			player.setWalkToAction(new WalkToMobAction(player, affectedMob, walkRadius, false, ActionType.ATTACK) {
				public void executeInternal() {
					boolean retargetingNpcWithRanged = getPlayer().inCombat()
						&& getMob().isNpc()
						&& getPlayer().getOpponent() != null
						&& getPlayer().getOpponent().isNpc()
						&& !getPlayer().getOpponent().equals(getMob())
						&& !getPlayer().getDuel().isDueling();
					if (getPlayer().isBusy() || (getPlayer().inCombat() && !retargetingNpcWithRanged)) return;
					if (retargetingNpcWithRanged) {
						getPlayer().resetCombatEvent();
					}
					getPlayer().resetFollowing();
					if (getMob().isPlayer()) {
						Player affectedPlayer = (Player) getMob();
						getPlayer().setSkulledOn(affectedPlayer);
						affectedPlayer.getTrade().resetAll();
						if (affectedPlayer.getMenuHandler() != null) {
							affectedPlayer.resetMenuHandler();
						}
						if (affectedPlayer.accessingBank()) {
							affectedPlayer.resetBank();
						}
						if (affectedPlayer.accessingShop()) {
							affectedPlayer.resetShop();
						}
					}

					// Authentic player always faced NW
					getPlayer().face(getPlayer().getX() + 1, getPlayer().getY() - 1);

						int throwingEquip = getPlayer().getThrowingEquip();
						int rangeEquip = getPlayer().getRangeEquip();

						if (throwingEquip < 0 && rangeEquip > 0) {
						// TODO: replace with gameEventHandler.addOrUpdate()
						final GameEventHandler gameEventHandler = getPlayer().getWorld()
							.getServer()
							.getGameEventHandler();

						RangeEvent rangeEvent = null;

						for (final GameTickEvent gameTickEvent : gameEventHandler.getPlayerEvents(getPlayer())) {
							if (gameTickEvent instanceof RangeEvent) {
								rangeEvent = (RangeEvent) gameTickEvent;
								break;
							}
						}

						if (rangeEvent != null) {
							if (!rangeEvent.getTarget().equals(getMob())) {
								rangeEvent.reTarget(getMob());
							}

							rangeEvent.restart();
							getPlayer().setRangeEvent(rangeEvent);
							return;
						}

						rangeEvent = new RangeEvent(getPlayer().getWorld(), getPlayer(), 1, target);
						getPlayer().setRangeEvent(rangeEvent);
						gameEventHandler.add(rangeEvent);
					} else {
						// TODO: replace with gameEventHandler.addOrUpdate()
						final GameEventHandler gameEventHandler = getPlayer().getWorld()
							.getServer()
							.getGameEventHandler();

						ThrowingEvent throwingEvent = null;

						for (final GameTickEvent gameTickEvent : gameEventHandler.getPlayerEvents(getPlayer())) {
							if (gameTickEvent instanceof ThrowingEvent) {
								throwingEvent = (ThrowingEvent) gameTickEvent;
								break;
							}
						}

						if (throwingEvent != null) {
							if (!throwingEvent.getTarget().equals(getMob())) {
								throwingEvent.reTarget(getMob());
							}

							throwingEvent.restart();
							getPlayer().setThrowingEvent(throwingEvent);
							return;
						}

						throwingEvent = new ThrowingEvent(getPlayer().getWorld(), getPlayer(), 1, target);
						getPlayer().setThrowingEvent(throwingEvent);
						gameEventHandler.add(throwingEvent);
					}
				}
			});
		}
	}

	private boolean canRetargetNpcWhileInCombat(final Player player, final Mob affectedMob) {
		if (!player.inCombat() || affectedMob == null || !affectedMob.isNpc() || player.getDuel().isDueling()) {
			return false;
		}
		Mob currentOpponent = player.getOpponent();
		if (currentOpponent == null || !currentOpponent.isNpc() || currentOpponent.equals(affectedMob)) {
			return false;
		}
		return true;
	}
}
