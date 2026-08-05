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
import com.openrsc.server.model.combat.AttackIntent;
import com.openrsc.server.model.combat.AttackTransactionResult;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.PlayerAttackTransaction;
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

		if (player.getAutoCastSpell() != null && MagicCombatEvent.start(
			player, affectedMob, AttackIntent.Source.MANUAL)) {
			return;
		}

		if (player.getRangeEquip() < 0 && player.getThrowingEquip() < 0) {
			final AttackIntent intent = player.getAttackTransaction().issue(
				affectedMob, CombatStyle.MELEE, AttackIntent.Channel.MELEE,
				AttackIntent.Source.MANUAL, null);

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
			if (player.getConfig().WANT_MYWORLD) {
				player.walkAdjacentToEntity(affectedMob);
			} else {
				player.setFollowing(affectedMob, 0, false, true);
			}

			final WalkToMobAction approach = new WalkToMobAction(player, affectedMob, walkRadius, true, ActionType.ATTACK) {
				public void executeInternal() {
					if (!getPlayer().getAttackTransaction().prepare(intent)
						.isReadyToCommit()) {
						return;
					}
					getPlayer().resetFollowing();

					if (!getPlayer().getConfig().WANT_MYWORLD && mob.inCombat() && getPlayer().getRangeEquip() < 0 && getPlayer().getThrowingEquip() < 0) {
						if (mob.isNpc()) {
							Npc npc = (Npc) mob;
							AttackTransactionResult result = getPlayer().getAttackTransaction().commit(
								intent, new PlayerAttackTransaction.CommitAction() {
									@Override
									public boolean commit() {
										if (!npc.tryTakeMeleeFocus(getPlayer())) {
											getPlayer().startCombat(npc);
										}
										return getPlayer().getPvmMeleeEvent() != null
											&& getPlayer().getPvmMeleeEvent().isRunning()
											&& getPlayer().getPvmMeleeEvent().getTarget() == npc;
									}
								});
							if (result.isCommitted()) {
								return;
							}
							return;
						}
						getPlayer().message("I can't get close enough");
						getPlayer().getAttackTransaction().cancel(intent,
							AttackTransactionResult.Reason.ELIGIBILITY_REJECTED);
						return;
					}
					if (getPlayer().isBusy() || mob.isBusy()
						|| !getPlayer().checkAttack(mob, false)) {
						getPlayer().getAttackTransaction().cancel(intent,
							AttackTransactionResult.Reason.ELIGIBILITY_REJECTED);
						return;
					}
					if (mob.isNpc()) {
						NpcInteraction interaction = NpcInteraction.NPC_ATTACK;
						NpcInteraction.setInteractions(((Npc)mob), getPlayer(), interaction);
						boolean blocked = getPlayer().getWorld().getServer().getPluginHandler().handlePlugin(AttackNpcTrigger.class, getPlayer(), new Object[]{getPlayer(), (Npc) mob}, this);
						if (blocked) {
							getPlayer().cancelPendingMeleeAttack(mob);
						}
					} else {
						boolean blocked = getPlayer().getWorld().getServer().getPluginHandler().handlePlugin(AttackPlayerTrigger.class, getPlayer(), new Object[]{getPlayer(), mob}, this);
						if (blocked) {
							getPlayer().cancelPendingMeleeAttack(mob);
						}
					}
				}
			};
			player.setWalkToAction(approach);
			player.getAttackTransaction().bindApproach(intent, approach);
		} else { // Attack with ranged instead of melee
			if (!player.checkAttack(affectedMob, true)) {
				return;
			}
			final boolean throwing = player.getThrowingEquip() >= 0;
			final AttackIntent intent = player.getAttackTransaction().issue(
				affectedMob,
				throwing ? CombatStyle.THROWING : CombatStyle.RANGED,
				throwing ? AttackIntent.Channel.THROWING : AttackIntent.Channel.RANGED,
				AttackIntent.Source.MANUAL, null);
			final Mob target = affectedMob;
			player.resetPath();
			int radius = player.getProjectileRadius();
			int approachRadius = player.getProjectileApproachRadius();
			int walkRadius = player.withinRange(affectedMob, radius) ? radius : approachRadius;
			player.setFollowing(affectedMob, walkRadius, false);
			final WalkToMobAction approach = new WalkToMobAction(player, affectedMob, walkRadius, false, ActionType.ATTACK) {
				public void executeInternal() {
					boolean retargetingNpcWithRanged = getPlayer().inCombat()
						&& getMob().isNpc()
						&& getPlayer().getOpponent() != null
						&& getPlayer().getOpponent().isNpc()
						&& !getPlayer().getOpponent().equals(getMob())
						&& !getPlayer().getDuel().isDueling();
					if (getPlayer().isBusy() || (getPlayer().inCombat() && !retargetingNpcWithRanged)) {
						getPlayer().getAttackTransaction().cancel(intent,
							AttackTransactionResult.Reason.ELIGIBILITY_REJECTED);
						return;
					}
					if (!getPlayer().getAttackTransaction().prepare(intent)
						.isReadyToCommit()) {
						return;
					}
					AttackTransactionResult committed = getPlayer().getAttackTransaction().commit(
						intent, new PlayerAttackTransaction.CommitAction() {
							@Override
							public boolean commit() {
								if (retargetingNpcWithRanged) {
									getPlayer().resetCombatEvent();
								}
								getPlayer().resetAll();
								getPlayer().resetFollowing();
								if (getMob().isPlayer()) {
									Player affectedPlayer = (Player) getMob();
									getPlayer().setSkulledOn(affectedPlayer);
									affectedPlayer.getTrade().resetAll();
									if (affectedPlayer.getMenuHandler() != null) {
										affectedPlayer.resetMenuHandler();
									}
									if (affectedPlayer.accessingBank()) affectedPlayer.resetBank();
									if (affectedPlayer.accessingShop()) affectedPlayer.resetShop();
								}
								// Authentic player always faced NW.
								getPlayer().face(getPlayer().getX() + 1, getPlayer().getY() - 1);
								return installRangedOrThrowingEvent(getPlayer(), getMob(), target);
							}
						});
					if (!committed.isCommitted()) {
						return;
					}
					if (getMob().isNpc()) {
						Summoning.recordCombatSummonEngagement(
							getPlayer(), (Npc) getMob());
					}
				}
			};
			player.setWalkToAction(approach);
			player.getAttackTransaction().bindApproach(intent, approach);
		}
	}

	private boolean installRangedOrThrowingEvent(final Player player,
			final Mob target, final Mob originalTarget) {
		final int throwingEquip = player.getThrowingEquip();
		final int rangeEquip = player.getRangeEquip();
		final GameEventHandler gameEventHandler = player.getWorld()
			.getServer().getGameEventHandler();
		if (throwingEquip < 0 && rangeEquip > 0) {
			RangeEvent rangeEvent = null;
			for (final GameTickEvent event : gameEventHandler.getPlayerEvents(player)) {
				if (event instanceof RangeEvent) {
					rangeEvent = (RangeEvent) event;
					break;
				}
			}
			if (rangeEvent != null) {
				if (!rangeEvent.getTarget().equals(target)) rangeEvent.reTarget(target);
				rangeEvent.restart();
				player.setRangeEvent(rangeEvent);
				return true;
			}
			rangeEvent = new RangeEvent(player.getWorld(), player, 1, originalTarget);
			player.setRangeEvent(rangeEvent);
			gameEventHandler.add(rangeEvent);
			return true;
		}
		if (throwingEquip >= 0) {
			ThrowingEvent throwingEvent = null;
			for (final GameTickEvent event : gameEventHandler.getPlayerEvents(player)) {
				if (event instanceof ThrowingEvent) {
					throwingEvent = (ThrowingEvent) event;
					break;
				}
			}
			if (throwingEvent != null) {
				if (!throwingEvent.getTarget().equals(target)) throwingEvent.reTarget(target);
				throwingEvent.restart();
				player.setThrowingEvent(throwingEvent);
				return true;
			}
			throwingEvent = new ThrowingEvent(player.getWorld(), player, 1, originalTarget);
			player.setThrowingEvent(throwingEvent);
			gameEventHandler.add(throwingEvent);
			return true;
		}
		return false;
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
