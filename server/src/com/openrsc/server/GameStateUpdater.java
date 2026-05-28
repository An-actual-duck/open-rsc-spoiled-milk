package com.openrsc.server;

import com.openrsc.server.constants.AppearanceId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.database.impl.mysql.queries.logging.PMLog;
import com.openrsc.server.external.GameObjectLoc;
import com.openrsc.server.external.ItemLoc;
import com.openrsc.server.model.PlayerAppearance;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.PrivateMessage;
import com.openrsc.server.model.RSCString;
import com.openrsc.server.model.entity.Entity;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.UnregisterForcefulness;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PlayerSettings;
import com.openrsc.server.model.entity.update.*;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.outgoing.*;
import com.openrsc.server.plugins.triggers.TimedEventTrigger;
import com.openrsc.server.util.EntityList;
import com.openrsc.server.util.rsc.AppearanceRetroConverter;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

import static com.openrsc.server.net.rsc.ActionSender.isRetroClient;
import static com.openrsc.server.net.rsc.ActionSender.tryFinalizeAndSendPacket;

public final class GameStateUpdater {
	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private final Server server;
	public final Server getServer() {
		return server;
	}

	public GameStateUpdater(final Server server) {
		this.server = server;
	}

	public void load() {
	}

	public void unload() {
	}

	public void sendUpdatePackets(final Player player) {
		// TODO: Should be private
		try {
			if (player.isUsing233CompatibleClient()) {
				if (player.isChangingAppearance()) {
					recordUpdateAppearanceKeepalive(() -> sendAppearanceKeepalive(player));
				} else {
					sendNormalUpdatePackets(player);
				}
			} else {
				sendNormalUpdatePackets(player);
			}
		} catch (final Exception e) {
			LOGGER.error("Exception during GameStateUpdater sendUpdatePackets", e);
			player.unregister(UnregisterForcefulness.FORCED, "Exception while updating player " + player.getUsername());
		}
	}

	private void sendNormalUpdatePackets(final Player player) {
		final Collection<GameObject> visibleGameObjects = player.getViewArea().getGameObjectsInView();
		final Collection<GroundItem> visibleGroundItems = player.getViewArea().getItemsInView();
		recordUpdatePlayers(() -> updatePlayers(player));
		recordUpdatePlayerAppearances(() -> updatePlayerAppearances(player));
		recordUpdateNpcs(() -> updateNpcs(player));
		recordUpdateNpcAppearances(() -> updateNpcAppearances(player));
		recordUpdateGameObjects(() -> updateGameObjects(player, visibleGameObjects));
		recordUpdateWallObjects(() -> updateWallObjects(player, visibleGameObjects));
		recordUpdateGroundItems(() -> updateGroundItems(player, visibleGroundItems));
		recordUpdateClearLocations(() -> sendClearLocations(player));
		recordUpdateTimeouts(() -> updateTimeouts(player));
	}

	private void recordUpdatePlayers(final Runnable update) {
		getServer().incrementLastUpdatePlayersDuration(getServer().bench(update));
	}

	private void recordUpdatePlayerAppearances(final Runnable update) {
		getServer().incrementLastUpdatePlayerAppearancesDuration(getServer().bench(update));
	}

	private void recordUpdateNpcs(final Runnable update) {
		getServer().incrementLastUpdateNpcsDuration(getServer().bench(update));
	}

	private void recordUpdateNpcAppearances(final Runnable update) {
		getServer().incrementLastUpdateNpcAppearancesDuration(getServer().bench(update));
	}

	private void recordUpdateGameObjects(final Runnable update) {
		getServer().incrementLastUpdateGameObjectsDuration(getServer().bench(update));
	}

	private void recordUpdateWallObjects(final Runnable update) {
		getServer().incrementLastUpdateWallObjectsDuration(getServer().bench(update));
	}

	private void recordUpdateGroundItems(final Runnable update) {
		getServer().incrementLastUpdateGroundItemsDuration(getServer().bench(update));
	}

	private void recordUpdateClearLocations(final Runnable update) {
		getServer().incrementLastUpdateClearLocationsDuration(getServer().bench(update));
	}

	private void recordUpdateTimeouts(final Runnable update) {
		getServer().incrementLastUpdateTimeoutsDuration(getServer().bench(update));
	}

	private void recordUpdateAppearanceKeepalive(final Runnable update) {
		getServer().incrementLastUpdateAppearanceKeepaliveDuration(getServer().bench(update));
	}

	/**
	 * Checks if the player has moved within the last X minutes
	 */
	protected void updateTimeouts(final Player player) {
		final long curTime = System.currentTimeMillis();
		final int timeoutLimit = getServer().getConfig().IDLE_TIMER; // 5 minute idle log out
		final int autoSave = getServer().getConfig().AUTO_SAVE; // 30 second autosave by default
		final int timedEvents = getServer().getConfig().TIMED_EVENT_INTERVAL;
		if (player.isRemoved() || player.getAttribute("dummyplayer", false)) {
			return;
		}
		if (curTime - player.getLastSaveTime() >= (autoSave) && player.loggedIn()) {
			player.timeIncrementActivity();
			player.save();
			player.setLastSaveTime(curTime);
		}

		if (player.getConfig().WANT_CUSTOM_QUESTS && curTime - player.getLastTimedEvent() >= timedEvents && player.loggedIn()) {
			player.getWorld().getServer().getPluginHandler().handlePlugin(TimedEventTrigger.class, player, new Object[]{player});
			player.setLastTimedEvent(curTime);
		}

		if (curTime - player.getLastClientActivity() >= 30000) {
			player.unregister(UnregisterForcefulness.WAIT_UNTIL_COMBAT_ENDS, "Client activity time-out");
		}

		if (player.warnedToMove()) {
			if (curTime - player.getLastMoved() >= (timeoutLimit + 60000) &&
				player.loggedIn() &&
				!player.hasElevatedPriveledges() &&
				!(player.inCombat() && player.getDuel().isDuelActive())) {
				player.unregister(UnregisterForcefulness.FORCED, "Movement time-out");
			} else if (player.hasMoved()) {
				player.setWarnedToMove(false);
			}
		} else if (timeoutLimit > 0 && !player.isMod() &&
				(curTime - player.getLastMoved()) >= timeoutLimit) {
			if (player.isSleeping()) {
				player.setSleeping(false);
				ActionSender.sendWakeUp(player, false, false);
			}
			player.message("@cya@You have been standing here for " + (timeoutLimit / 60000)
				+ " mins! Please move to a new area");
			player.setWarnedToMove(true);
		}
	}

	public boolean sendMovementUpdatePacket(final Player player, final List<Player> movedPlayers, final List<Npc> movedNpcs) {
		if (!player.isUsingCustomClient()) {
			return false;
		}

		MovementUpdateStruct struct = new MovementUpdateStruct();
		struct.localX = player.getX();
		struct.localY = player.getY();
		struct.localSprite = player.getSprite();

		for (final Player movedPlayer : movedPlayers) {
			if (struct.players.size() >= 255) {
				break;
			}
			if (movedPlayer.equals(player) || !player.getLocalPlayers().contains(movedPlayer)) {
				continue;
			}
			if (!movedPlayer.withinAuthenticRangeAdditionally(player) || !player.withinRange(movedPlayer)) {
				continue;
			}
			struct.players.add(new MovementUpdateStruct.MobMovement(
				movedPlayer.getIndex(), movedPlayer.getX(), movedPlayer.getY(), movedPlayer.getSprite()));
		}

		for (final Npc movedNpc : movedNpcs) {
			if (struct.npcs.size() >= 255) {
				break;
			}
			if (!player.getLocalNpcs().contains(movedNpc)) {
				continue;
			}
			if (!movedNpc.withinAuthenticRangeAdditionally(player) || !player.withinRange(movedNpc)) {
				continue;
			}
			struct.npcs.add(new MovementUpdateStruct.MobMovement(
				movedNpc.getIndex(), movedNpc.getX(), movedNpc.getY(), movedNpc.getSprite()));
		}

		if (!movedPlayers.contains(player) && struct.players.isEmpty() && struct.npcs.isEmpty()) {
			return false;
		}

		tryFinalizeAndSendPacket(OpcodeOut.SEND_MOVEMENT_UPDATE, struct, player);
		return true;
	}

	private int safeNPCIndex(final Player player, final int npcIndex) {
		if (!player.isUsingCustomClient()) {
			return npcIndex % player.getClientLimitations().maxServerId;
		}
		return npcIndex;
	}

	private static Map.Entry<Integer, Integer> bit(final int value, final int bits) {
		return new MobsUpdateStruct.BitUpdate(value, bits);
	}

	private static int mobCoordOffset(final int coord, final int referenceCoord) {
		int offset = coord - referenceCoord;
		if (offset < 0) {
			offset += 64;
		}
		return offset;
	}

	protected void updateNpcs(final Player playerToUpdate) {
		MobsUpdateStruct struct = new MobsUpdateStruct();
		ClearMobsStruct clearStruct = new ClearMobsStruct();
		boolean isRetroClient = playerToUpdate.isUsing38CompatibleClient() || playerToUpdate.isUsing39CompatibleClient();
		if (isRetroClient) {
			// TODO: check impl
			List<Object> mobsUpdate = new ArrayList<>();
			List<Integer> clearIdx = new ArrayList<>();

			for (final Iterator<Npc> it$ = playerToUpdate.getLocalNpcs().iterator(); it$.hasNext(); ) {
				Npc localNpc = it$.next();

				if (!localNpc.withinAuthenticRangeAdditionally(playerToUpdate) || !playerToUpdate.withinRange(localNpc) || localNpc.isRemoved() || localNpc.isRespawning() || localNpc.isTeleporting() || localNpc.inCombat()) {
					if (!localNpc.inCombat() || localNpc.getOpponent() != playerToUpdate) {
						// TODO: check if more conditions need to be added from outer if
						clearIdx.add(localNpc.getIndex());
					}
					it$.remove();
				} else {
					final byte[] offsets = DataConversions.getMobPositionOffsets(localNpc.getLocation(), playerToUpdate.getLocation());

					int X = offsets[0];
					int Y = offsets[1];
					int packed = (localNpc.getIndex() << 6) | ((X & 0x1F) << 1) | ((Y & 0x1F) >> 4);
					mobsUpdate.add((short) packed);
					int packed2 = ((Y & 0xF) << 4) | (localNpc.getSprite() & 0xF);
					mobsUpdate.add((byte) packed2);
					mobsUpdate.add((byte) localNpc.getID());
				}
			}
			clearStruct.indices = clearIdx;
			for (final Npc newNPC : playerToUpdate.getViewArea().getNpcsInView()) {
				if (playerToUpdate.getLocalNpcs().contains(newNPC) || newNPC.isRemoved() || newNPC.isRespawning()
					|| newNPC.getID() == NpcId.NED_BOAT.id() && !playerToUpdate.getCache().hasKey("ned_hired")
					|| !newNPC.withinAuthenticRangeAdditionally(playerToUpdate) || !playerToUpdate.withinRange(newNPC) || (newNPC.isTeleporting() && !newNPC.inCombat())) {
					continue;
				} else if (playerToUpdate.getLocalNpcs().size() >= 255) {
					break;
				}
				final byte[] offsets = DataConversions.getMobPositionOffsets(newNPC.getLocation(), playerToUpdate.getLocation());

				int X = offsets[0];
				int Y = offsets[1];
				int packed = (newNPC.getIndex() << 6) | ((X & 0x1F) << 1) | ((Y & 0x1F) >> 4);
				mobsUpdate.add((short) packed);
				int packed2 = ((Y & 0xF) << 4) | (newNPC.getSprite() & 0xF);
				mobsUpdate.add((byte) packed2);
				mobsUpdate.add((byte) newNPC.getID());

				if (!playerToUpdate.getConfig().BREAK_NPC_LOCATION_CACHE) {
					playerToUpdate.getLocalNpcs().add(newNPC);
				}
			}

			struct.mobsUpdate = mobsUpdate;
		} else {
			final int localNpcCount = playerToUpdate.getLocalNpcs().size();
			final int visibleNpcCount = playerToUpdate.getViewArea().getNpcsInView().size();
			List<Map.Entry<Integer, Integer>> mobsUpdate = new ArrayList<>(1 + (localNpcCount * 3) + (Math.min(255, visibleNpcCount) * 5));
			final boolean traceNpcPackets = playerToUpdate.getAttribute("debug_npc_trace", false);
			final int traceRadius = playerToUpdate.getAttribute("debug_npc_trace_radius", 12);
			final ArrayList<String> packetTraceSamples = traceNpcPackets ? new ArrayList<>(6) : null;
			int packetTraceMoveCount = 0;
			final int MOVEMENT_UPDATE = 0;
			final int UPDATE_NOT_REQUIRED = 0;
			final int UPDATE_REQUIRED = 1;
			final int NOT_MOVING = 1;
			final int REMOVE_NPC = 3;
			final boolean useCustomMovementStream = playerToUpdate.isUsingCustomClient()
				&& getServer().getConfig().WANT_CUSTOM_WALK_SPEED;

			mobsUpdate.add(bit(playerToUpdate.getLocalNpcs().size(), 8));
			for (final Iterator<Npc> it$ = playerToUpdate.getLocalNpcs().iterator(); it$.hasNext(); ) {
				Npc localNpc = it$.next();
				final UpdateFlags updateFlags = localNpc.getUpdateFlags();
				final boolean hasPendingDeathVisual = playerToUpdate.isUsingCustomClient()
					&& (localNpc.isRemoved() || localNpc.isRespawning())
					&& playerToUpdate.withinRange(localNpc)
					&& localNpc.withinAuthenticRangeAdditionally(playerToUpdate)
					&& (updateFlags.hasCombatEffect() || updateFlags.hasHitSplats() || updateFlags.hasTakenDamage());

				if (localNpc.isInvisibleTo(playerToUpdate)) {
					it$.remove();
					mobsUpdate.add(bit(UPDATE_REQUIRED, 1));
					mobsUpdate.add(bit(NOT_MOVING, 1));
					mobsUpdate.add(bit(REMOVE_NPC, 2));
				}

				if (!localNpc.withinAuthenticRangeAdditionally(playerToUpdate) || !playerToUpdate.withinRange(localNpc) || // remove because they are out of range
					(localNpc.isRemoved() && !hasPendingDeathVisual) || // remove because they are removed
					localNpc.isTeleporting() || // if they've teleported, then they may have moved more than one square, and thus require a full coordinate refresh
					(localNpc.inCombat() && !hasPendingDeathVisual) || // remove because when FIRST entering combat, they may have advanced towards the player, then their sprite is incompatible with a movement update (no direction, and > 7) TODO: should be inCombatChanged(), since it's only necessary on the first round of combat.
					(localNpc.isRespawning() && !hasPendingDeathVisual) // removed because they have not yet respawned; may not be necessary, but there's no scenario where this is true & they shouldn't be removed.
					) {
					it$.remove(); // removes NPC from player's localNpcs list
					mobsUpdate.add(bit(UPDATE_REQUIRED, 1));
					mobsUpdate.add(bit(NOT_MOVING, 1));
					mobsUpdate.add(bit(REMOVE_NPC, 2));
				} else {
					if (localNpc.hasMoved() && !useCustomMovementStream) {
						mobsUpdate.add(bit(UPDATE_REQUIRED, 1));
						mobsUpdate.add(bit(MOVEMENT_UPDATE, 1)); // Tell player that the NPC has moved 1 tile in the direction that their sprite is facing
						mobsUpdate.add(bit(localNpc.getSprite(), 3)); // sprite is limited to 3 bits for 8 directions, since NPC can't be fighting while moving
						if (traceNpcPackets && playerToUpdate.withinRange(localNpc, traceRadius)) {
							packetTraceMoveCount++;
							if (packetTraceSamples.size() < 6) {
								packetTraceSamples.add(localNpc.getID() + ":" + localNpc.getIndex() + "@"
									+ localNpc.getX() + "," + localNpc.getY() + " sprite=" + localNpc.getSprite());
							}
						}
					} else if (localNpc.spriteChanged() && !useCustomMovementStream) {
						mobsUpdate.add(bit(UPDATE_REQUIRED, 1));
						mobsUpdate.add(bit(NOT_MOVING, 1));
						mobsUpdate.add(bit(localNpc.getSprite(), 4)); // 4 bits to accommodate sprites 8 & 9, used for fighting
					} else {
						mobsUpdate.add(bit(UPDATE_NOT_REQUIRED, 1));
					}
				}
			}

			for (final Npc newNPC : playerToUpdate.getViewArea().getNpcsInView()) {
				if (newNPC.isInvisibleTo(playerToUpdate)) {
					continue;
				}

				if (newNPC.getID() == NpcId.NED_BOAT.id() && !playerToUpdate.getCache().hasKey("ned_hired")) {
					// TODO: probably this is incorrect & should be removed.
					// There are authentically 4 versions of the Lady Lumbridge interior, to accommodate Ned being present or not & ship being crashed or not.
					continue;
				}

				if (playerToUpdate.getLocalNpcs().contains(newNPC) || // The NPC is cached & updated successfully. Don't refresh & don't duplicate them in the localNpcs cache.
					newNPC.isRemoved() || // The NPC is removed & shouldn't be added.
					newNPC.isRespawning() || // The NPC has not yet spawned & shouldn't be added.
					!newNPC.withinAuthenticRangeAdditionally(playerToUpdate) || !playerToUpdate.withinRange(newNPC) // only have 5 bits in the rsc235 protocol, so the npc can only be shown up to 16 tiles away
					// || (newNPC.isTeleporting() && !newNPC.inCombat()) // ??? Might be a bug. If they teleported this tick, and ended up within range, we want to refresh them for sure, right?
				) {
					continue;
				} else if (playerToUpdate.getLocalNpcs().size() >= 255) {
					break;
				}

				final byte[] offsets = DataConversions.getMobPositionOffsets(newNPC.getLocation(), playerToUpdate.getLocation());
				boolean forClient115 = playerToUpdate.isUsing115CompatibleClient();
				boolean forClient140 = playerToUpdate.isUsing140CompatibleClient();
				mobsUpdate.add(bit(safeNPCIndex(playerToUpdate, newNPC.getIndex()), forClient115 || forClient140 ? 11 : 12));
				boolean forAuthentic = !playerToUpdate.isUsingCustomClient();
				mobsUpdate.add(bit((int) offsets[0], forAuthentic ? 5 : 6));
				mobsUpdate.add(bit((int) offsets[1], forAuthentic ? 5 : 6));
				mobsUpdate.add(bit(newNPC.getSprite(), 4));
				int numBits = forClient115 ? 8 : (forClient140 ? 9 : 10);
				mobsUpdate.add(bit(newNPC.getID(), numBits));

				if (!playerToUpdate.getConfig().BREAK_NPC_LOCATION_CACHE) {
					playerToUpdate.getLocalNpcs().add(newNPC);
				}
			}

			struct.mobs = mobsUpdate;
			if (traceNpcPackets) {
				LOGGER.info("NPC_TRACE packetTick player={} localNpcs={} movedPackets={} samples={}",
					playerToUpdate.getUsername(), playerToUpdate.getLocalNpcs().size(), packetTraceMoveCount, packetTraceSamples);
			}
		}
		if (clearStruct.indices != null && clearStruct.indices.size() > 0) {
			tryFinalizeAndSendPacket(OpcodeOut.SEND_REMOVE_WORLD_NPC, clearStruct, playerToUpdate);
		}
		tryFinalizeAndSendPacket(OpcodeOut.SEND_NPC_COORDS, struct, playerToUpdate);
	}

	protected void updatePlayers(final Player playerToUpdate) {
		MobsUpdateStruct struct = new MobsUpdateStruct();
		ClearMobsStruct clearStruct = new ClearMobsStruct();

		Point midRegion = playerToUpdate.getAttribute("midpointRegion");
		if (midRegion != null) {
			if (!playerToUpdate.getLocation().inBounds(midRegion.getX() - 32, midRegion.getY() - 32, midRegion.getX() + 32, midRegion.getY() + 32)) {
				playerToUpdate.setNextRegionLoad();
				playerToUpdate.changeZone();
			}
		} else {
			playerToUpdate.setNextRegionLoad();
		}

		boolean isRetroClient = playerToUpdate.isUsing38CompatibleClient() || playerToUpdate.isUsing39CompatibleClient();
		boolean usesKnownPlayers = playerToUpdate.getClientVersion() >= 61 && playerToUpdate.getClientVersion() <= 204;
		final Collection<Player> visiblePlayers = playerToUpdate.getViewArea().getPlayersInView();

		if (isRetroClient) {
			// TODO: check impl
			List<Object> mobsUpdate = new ArrayList<>();
			List<Integer> clearIdx = new ArrayList<>();

			mobsUpdate.add((short) playerToUpdate.getIndex());
			mobsUpdate.add((short) playerToUpdate.getX());
			mobsUpdate.add((short) playerToUpdate.getY());
			mobsUpdate.add((byte) playerToUpdate.getSprite());

			if (playerToUpdate.loggedIn()) {
				for (final Iterator<Player> it$ = playerToUpdate.getLocalPlayers().iterator(); it$.hasNext(); ) {
					final Player otherPlayer = it$.next();

					if (!otherPlayer.withinAuthenticRangeAdditionally(playerToUpdate) || !playerToUpdate.withinRange(otherPlayer) || !otherPlayer.loggedIn() || otherPlayer.isRemoved()
						|| otherPlayer.isTeleporting() || otherPlayer.isInvisibleTo(playerToUpdate)
						|| otherPlayer.inCombat() || otherPlayer.hasMoved() || otherPlayer.isUnregistering()) {
						if ((!otherPlayer.hasMoved() || !otherPlayer.withinAuthenticRangeAdditionally(playerToUpdate) || !playerToUpdate.withinRange(otherPlayer)) && !otherPlayer.inCombat()) {
							// TODO: check if more conditions need to be added from outer if
							clearIdx.add(otherPlayer.getIndex());
						}
						it$.remove();
						playerToUpdate.getKnownPlayerAppearanceIDs().remove(otherPlayer.getUsernameHash());
					} else {
						final byte[] offsets = DataConversions.getMobPositionOffsets(otherPlayer.getLocation(),
							playerToUpdate.getLocation());

						int X = offsets[0];
						int Y = offsets[1];
						if (otherPlayer.equals(playerToUpdate)) {
							int packed = ((X & 0x1F) << 5) | (Y & 0x1F);
							mobsUpdate.add((short) packed);
							int packed2 = (otherPlayer.getIndex() << 4) | (otherPlayer.getSprite() & 0xF);
							mobsUpdate.add((short) packed2);
						} else {
							int packed = (otherPlayer.getIndex() << 6) | ((X & 0x1F) << 1) | ((Y & 0x1F) >> 4);
							mobsUpdate.add((short) packed);
							int packed2 = ((Y & 0xF) << 4) | (otherPlayer.getSprite() & 0xF);
							mobsUpdate.add((byte) packed2);
						}
					}
				}
				clearStruct.indices = clearIdx;

				for (final Player otherPlayer : visiblePlayers) {
					if (playerToUpdate.getLocalPlayers().contains(otherPlayer) || otherPlayer.equals(playerToUpdate)
						|| !otherPlayer.withinAuthenticRangeAdditionally(playerToUpdate) || !otherPlayer.withinRange(playerToUpdate) || !otherPlayer.loggedIn() || otherPlayer.isUnregistering()
						|| otherPlayer.isRemoved() || otherPlayer.isInvisibleTo(playerToUpdate)
						|| (otherPlayer.isTeleporting() && !otherPlayer.inCombat())) {
						continue;
					}

					int X = mobCoordOffset(otherPlayer.getX(), playerToUpdate.getX());
					int Y = mobCoordOffset(otherPlayer.getY(), playerToUpdate.getY());
					if (otherPlayer.equals(playerToUpdate)) {
						int packed = ((X & 0x1F) << 5) | (Y & 0x1F);
						mobsUpdate.add((short) packed);
						int packed2 = (otherPlayer.getIndex() << 4) | (otherPlayer.getSprite() & 0xF);
						mobsUpdate.add((short) packed2);
					} else {
						int packed = (otherPlayer.getIndex() << 6) | ((X & 0x1F) << 1) | ((Y & 0x1F) >> 4);
						mobsUpdate.add((short) packed);
						int packed2 = ((Y & 0xF) << 4) | (otherPlayer.getSprite() & 0xF);
						mobsUpdate.add((byte) packed2);
					}

					playerToUpdate.getLocalPlayers().add(otherPlayer);
					if (playerToUpdate.getLocalPlayers().size() >= 255) {
						break;
					}
				}
			}

			struct.mobsUpdate = mobsUpdate;
		} else {
			final int localPlayerCount = playerToUpdate.getLocalPlayers().size();
			final int visiblePlayerCount = visiblePlayers.size();
			List<Map.Entry<Integer, Integer>> mobsUpdate = new ArrayList<>(4 + (localPlayerCount * 3) + (Math.min(255, visiblePlayerCount) * 5));
			final boolean forAuthentic = !playerToUpdate.isUsingCustomClient();

			if (playerToUpdate.isUsing140CompatibleClient() || playerToUpdate.isUsing115CompatibleClient() || playerToUpdate.isUsing69CompatibleClient()) {
				mobsUpdate.add(bit(playerToUpdate.getX(), 10));
				mobsUpdate.add(bit(playerToUpdate.getY(), 12));
			} else {
				mobsUpdate.add(bit(playerToUpdate.getX(), 11));
				mobsUpdate.add(bit(playerToUpdate.getY(), 13));
			}
			mobsUpdate.add(bit(playerToUpdate.getSprite(), 4));
			mobsUpdate.add(bit(playerToUpdate.getLocalPlayers().size(), 8));
			if (playerToUpdate.loggedIn()) {
				for (final Iterator<Player> it$ = playerToUpdate.getLocalPlayers().iterator(); it$.hasNext(); ) {
					final Player otherPlayer = it$.next();

					if (!otherPlayer.withinAuthenticRangeAdditionally(playerToUpdate) || !playerToUpdate.withinRange(otherPlayer) || !otherPlayer.loggedIn() || otherPlayer.isRemoved()
						|| otherPlayer.isTeleporting() || otherPlayer.isInvisibleTo(playerToUpdate)
						|| otherPlayer.inCombat() || otherPlayer.hasMoved())
					{
						mobsUpdate.add(bit(1, 1)); //Needs Update
						mobsUpdate.add(bit(1, 1)); //Update Type
						mobsUpdate.add(bit(3, 2)); //Animation type (Remove)
						it$.remove();
						playerToUpdate.getKnownPlayerAppearanceIDs().remove(otherPlayer.getUsernameHash());
					} else {
						if (!otherPlayer.hasMoved() && !otherPlayer.spriteChanged()) {
							mobsUpdate.add(bit(0, 1)); //Needs Update
						} else {
							// The player is actually going to be updated
							if (otherPlayer.hasMoved()) {
								mobsUpdate.add(bit(1, 1)); //Needs Update
								mobsUpdate.add(bit(0, 1)); //Update Type
								mobsUpdate.add(bit(otherPlayer.getSprite(), 3));
							} else if (otherPlayer.spriteChanged()) {
								mobsUpdate.add(bit(1, 1)); //Needs Update
								mobsUpdate.add(bit(1, 1)); //Update Type
								mobsUpdate.add(bit(otherPlayer.getSprite(), 4));
							}
						}
					}
				}

				for (final Player otherPlayer : visiblePlayers) {
					if (playerToUpdate.getLocalPlayers().contains(otherPlayer) || otherPlayer.equals(playerToUpdate)
						|| !otherPlayer.withinAuthenticRangeAdditionally(playerToUpdate) || !otherPlayer.withinRange(playerToUpdate) || !otherPlayer.loggedIn()
						|| otherPlayer.isRemoved() || otherPlayer.isInvisibleTo(playerToUpdate)
						|| (otherPlayer.isTeleporting() && !otherPlayer.inCombat())) {
						continue;
					}

					mobsUpdate.add(bit(otherPlayer.getIndex(), 11));
					mobsUpdate.add(bit(mobCoordOffset(otherPlayer.getX(), playerToUpdate.getX()), forAuthentic ? 5 : 6));
					mobsUpdate.add(bit(mobCoordOffset(otherPlayer.getY(), playerToUpdate.getY()), forAuthentic ? 5 : 6));
					mobsUpdate.add(bit(otherPlayer.getSprite(), 4));
					if (usesKnownPlayers) {
						mobsUpdate.add(bit(playerToUpdate.isKnownPlayer(otherPlayer.getIndex()) ? 1 : 0, 1));
					}

					playerToUpdate.getLocalPlayers().add(otherPlayer);
					if (playerToUpdate.getLocalPlayers().size() >= 255) {
						break;
					}
				}
			}

			struct.mobs = mobsUpdate;
		}
		if (clearStruct.indices != null && clearStruct.indices.size() > 0) {
			tryFinalizeAndSendPacket(OpcodeOut.SEND_REMOVE_WORLD_PLAYER, clearStruct, playerToUpdate);
		}
		if (playerToUpdate.getAttribute("debug_walk_trace", false)) {
			int budget = playerToUpdate.getAttribute("debug_walk_trace_budget", 0);
			if (budget > 0) {
				Point nextMovement = playerToUpdate.getWalkingQueue().getNextMovement();
				int pathSize = playerToUpdate.getWalkingQueue().path == null ? -1 : playerToUpdate.getWalkingQueue().path.size();
				LOGGER.info("WALK_TRACE send player={} budget={} local={},{} hasMoved={} sprite={} nextMovement={},{} pathSize={} pathFinished={} localPlayers={} localNpcs={}",
					playerToUpdate.getUsername(),
					budget,
					playerToUpdate.getX(), playerToUpdate.getY(),
					playerToUpdate.hasMoved(),
					playerToUpdate.getSprite(),
					nextMovement.getX(), nextMovement.getY(),
					pathSize,
					playerToUpdate.getWalkingQueue().finished(),
					playerToUpdate.getLocalPlayers().size(),
					playerToUpdate.getLocalNpcs().size());
				playerToUpdate.setAttribute("debug_walk_trace_budget", budget - 1);
			}
		}
		tryFinalizeAndSendPacket(OpcodeOut.SEND_PLAYER_COORDS, struct, playerToUpdate);
	}

	public void updateNpcAppearances(final Player player) {
		final ArrayDeque<Damage> npcsNeedingHitsUpdate = new ArrayDeque<>();
		final ArrayDeque<ChatMessage> npcMessagesNeedingDisplayed = new ArrayDeque<>();
		final ArrayDeque<Projectile> npcProjectilesNeedingDisplayed = new ArrayDeque<>();
		final ArrayDeque<Skull> npcSkullsNeedingDisplayed = new ArrayDeque<>();
		final ArrayDeque<Wield> npcWieldsNeedingDisplayed = new ArrayDeque<>();
		final ArrayDeque<BubbleNpc> npcBubblesNeedingDisplayed = new ArrayDeque<>();
		final ArrayDeque<CombatEffect> npcCombatEffectsNeedingDisplayed = new ArrayDeque<>();
		final ArrayDeque<HitSplat> npcHitSplatsNeedingDisplayed = new ArrayDeque<>();
		final ArrayDeque<Npc> npcSummonFlagsNeedingDisplayed = new ArrayDeque<>();
		final ArrayDeque<Npc> npcSummonHealthNeedingDisplayed = new ArrayDeque<>();
		final ArrayDeque<Npc> npcSummonSpritesNeedingDisplayed = new ArrayDeque<>();
		final ArrayDeque<Npc> npcSpiritSummonFlagsNeedingDisplayed = new ArrayDeque<>();

		for (final Npc npc : player.getLocalNpcs()) {
			final UpdateFlags updateFlags = npc.getUpdateFlags();
			if (player.isUsingCustomClient() && Summoning.isSummon(npc)) {
				npcSummonFlagsNeedingDisplayed.add(npc);
				npcSummonHealthNeedingDisplayed.add(npc);
				npcSummonSpritesNeedingDisplayed.add(npc);
				npcSpiritSummonFlagsNeedingDisplayed.add(npc);
			}
			if (updateFlags.hasChatMessage()) {
				ChatMessage chatMessage = updateFlags.getChatMessage();
				npcMessagesNeedingDisplayed.add(chatMessage);
			}
			if (updateFlags.hasSkulled()) {
				Skull skull = updateFlags.getSkull().get();
				npcSkullsNeedingDisplayed.add(skull);
			}
			if (updateFlags.changedWield()) {
				Wield wield = updateFlags.getWield().get();
				npcWieldsNeedingDisplayed.add(wield);
			}
			if (updateFlags.changedWield2()) {
				Wield wield2 = updateFlags.getWield2().get();
				npcWieldsNeedingDisplayed.add(wield2);
			}
			if (updateFlags.hasTakenDamage()) {
				Damage damage = updateFlags.getDamage().get();
				npcsNeedingHitsUpdate.add(damage);
			}
			if (updateFlags.hasFiredProjectile()) {
				Projectile projectileFired = updateFlags.getProjectile().get();
				if (projectileFired.getCaster().getIndex() != -1 && projectileFired.getVictim().getIndex() != -1) {
					npcProjectilesNeedingDisplayed.add(projectileFired);
				}
			}
			if (updateFlags.hasBubbleNpc()) {
				BubbleNpc bubble = updateFlags.getActionBubbleNpc().get();
				npcBubblesNeedingDisplayed.add(bubble);
			}
			if (player.isUsingCustomClient() && updateFlags.hasCombatEffect()) {
				npcCombatEffectsNeedingDisplayed.addAll(updateFlags.getCombatEffects());
			}
			if (player.isUsingCustomClient() && updateFlags.hasHitSplats()) {
				npcHitSplatsNeedingDisplayed.addAll(updateFlags.getHitSplats());
			}
		}
		int updateSize = npcMessagesNeedingDisplayed.size() + npcsNeedingHitsUpdate.size();
		if (player.isUsingCustomClient()) {
			updateSize += npcProjectilesNeedingDisplayed.size() + npcSkullsNeedingDisplayed.size() + npcWieldsNeedingDisplayed.size()
				+ npcBubblesNeedingDisplayed.size() + npcCombatEffectsNeedingDisplayed.size() + npcHitSplatsNeedingDisplayed.size()
				+ npcSummonFlagsNeedingDisplayed.size() + npcSummonHealthNeedingDisplayed.size()
				+ npcSummonSpritesNeedingDisplayed.size() + npcSpiritSummonFlagsNeedingDisplayed.size();
		}
		if (updateSize > 0) {
			AppearanceUpdateStruct struct = new AppearanceUpdateStruct();
			List<Object> updates = new ArrayList<>(1 + (updateSize * 5));

			updates.add((short) updateSize);

			ChatMessage chatMessage;
			while ((chatMessage = npcMessagesNeedingDisplayed.poll()) != null) {
				updates.add((short) safeNPCIndex(player, chatMessage.getSender().getIndex()));
				updates.add((byte) 1);
				updates.add((short) (chatMessage.getRecipient() == null ? -1 : chatMessage.getRecipient().getIndex()));
				if (isRetroClient(player)) {
					updates.add((byte) chatMessage.getMessageString().length());
					updates.add(chatMessage.getMessageString());
				} else if (player.isUsingCustomClient()) {
					updates.add(chatMessage.getMessageString());
				} else {
					updates.add(new RSCString(chatMessage.getMessageString()));
				}
			}
			Damage npcNeedingHitsUpdate;
			while ((npcNeedingHitsUpdate = npcsNeedingHitsUpdate.poll()) != null) {
				updates.add((short) safeNPCIndex(player, npcNeedingHitsUpdate.getIndex()));
				updates.add((byte) 2);
				updates.add((byte) npcNeedingHitsUpdate.getDamage());
				updates.add((byte) npcNeedingHitsUpdate.getCurHits());
				updates.add(((byte) npcNeedingHitsUpdate.getMaxHits()));
			}
			if (player.isUsingCustomClient()) {
				Projectile projectile;
				while ((projectile = npcProjectilesNeedingDisplayed.poll()) != null) {
					Entity victim = projectile.getVictim();
					if (victim.isNpc()) {
						updates.add((short) projectile.getCaster().getIndex());
						updates.add((byte) 3);
						updates.add((short) projectile.getType());
						updates.add((short) victim.getIndex());
					} else if (victim.isPlayer()) {
						updates.add((short) projectile.getCaster().getIndex());
						updates.add((byte) 4);
						updates.add((short) projectile.getType());
						updates.add((short) victim.getIndex());
					}
				}
				Skull npcNeedingSkullUpdate;
				while ((npcNeedingSkullUpdate = npcSkullsNeedingDisplayed.poll()) != null) {
					updates.add((short) npcNeedingSkullUpdate.getIndex());
					updates.add((byte) 5);
					updates.add((byte) npcNeedingSkullUpdate.getSkull());
				}
				Wield npcNeedingWieldUpdate;
				while ((npcNeedingWieldUpdate = npcWieldsNeedingDisplayed.poll()) != null) {
					updates.add((short) npcNeedingWieldUpdate.getIndex());
					updates.add((byte) 6);
					updates.add((byte) npcNeedingWieldUpdate.getWield());
					updates.add((byte) npcNeedingWieldUpdate.getWield2());
				}
				BubbleNpc npcNeedingBubbleUpdate;
				while ((npcNeedingBubbleUpdate = npcBubblesNeedingDisplayed.poll()) != null) {
					updates.add((short) npcNeedingBubbleUpdate.getOwner().getIndex());
					updates.add((byte) 7);
					updates.add((short) npcNeedingBubbleUpdate.getID());
				}
				CombatEffect npcCombatEffect;
				while ((npcCombatEffect = npcCombatEffectsNeedingDisplayed.poll()) != null) {
					updates.add((short) npcCombatEffect.getTarget().getIndex());
					updates.add((byte) 10);
					updates.add((byte) npcCombatEffect.getEffectType());
				}
				HitSplat npcHitSplat;
				while ((npcHitSplat = npcHitSplatsNeedingDisplayed.poll()) != null) {
					updates.add((short) safeNPCIndex(player, npcHitSplat.getIndex()));
					updates.add((byte) 11);
					updates.add((byte) npcHitSplat.getType());
					updates.add((byte) npcHitSplat.getAmount());
					updates.add((byte) npcHitSplat.getCurHits());
					updates.add((byte) npcHitSplat.getMaxHits());
				}
				Npc summonedNpc;
				while ((summonedNpc = npcSummonFlagsNeedingDisplayed.poll()) != null) {
					updates.add((short) safeNPCIndex(player, summonedNpc.getIndex()));
					updates.add((byte) 12);
					updates.add((byte) 1);
				}
				Npc summonedNpcHealth;
				while ((summonedNpcHealth = npcSummonHealthNeedingDisplayed.poll()) != null) {
					updates.add((short) safeNPCIndex(player, summonedNpcHealth.getIndex()));
					updates.add((byte) 13);
					updates.add((byte) summonedNpcHealth.getSkills().getLevel(com.openrsc.server.constants.Skill.HITS.id()));
					updates.add((byte) summonedNpcHealth.getSkills().getMaxStat(com.openrsc.server.constants.Skill.HITS.id()));
				}
				Npc summonedNpcSprite;
				while ((summonedNpcSprite = npcSummonSpritesNeedingDisplayed.poll()) != null) {
					updates.add((short) safeNPCIndex(player, summonedNpcSprite.getIndex()));
					updates.add((byte) 14);
					updates.add((byte) summonedNpcSprite.getSprite());
				}
				Npc spiritSummonNpc;
				while ((spiritSummonNpc = npcSpiritSummonFlagsNeedingDisplayed.poll()) != null) {
					updates.add((short) safeNPCIndex(player, spiritSummonNpc.getIndex()));
					updates.add((byte) 15);
					updates.add((byte) (Summoning.isArmorSummon(spiritSummonNpc) ? 1 : 0));
				}
			}

			struct.info = updates;
			tryFinalizeAndSendPacket(OpcodeOut.SEND_UPDATE_NPC, struct, player);
		}
	}

	/**
	 * Handles the appearance updating for @param player
	 *
	 * @param player
	 */
	public void updatePlayerAppearances(final Player player) {
		final int localPlayerCount = player.getLocalPlayers().size();
		final int expectedUpdateCount = localPlayerCount + 1;
		final ArrayDeque<Bubble> bubblesNeedingDisplayed = new ArrayDeque<>(expectedUpdateCount);
		final ArrayDeque<ChatMessage> chatMessagesNeedingDisplayed = new ArrayDeque<>(expectedUpdateCount);
		final ArrayDeque<Projectile> projectilesNeedingDisplayed = new ArrayDeque<>(expectedUpdateCount);
		final ArrayDeque<Damage> playersNeedingDamageUpdate = new ArrayDeque<>(expectedUpdateCount);
		final ArrayDeque<HpUpdate> playersNeedingHpUpdate = new ArrayDeque<>(expectedUpdateCount);
		final ArrayDeque<Player> playersNeedingAppearanceUpdate = new ArrayDeque<>(expectedUpdateCount);
		final ArrayDeque<CombatEffect> combatEffectsNeedingDisplayed = new ArrayDeque<>(expectedUpdateCount);
		final ArrayDeque<HitSplat> hitSplatsNeedingDisplayed = new ArrayDeque<>(expectedUpdateCount);
		final boolean viewerUsesCustomClient = player.isUsingCustomClient();
		final byte chatPrivacySetting = player.getSettings().getPrivacySetting(
			PlayerSettings.PRIVACY_BLOCK_CHAT_MESSAGES, viewerUsesCustomClient);
		final boolean blockAll = chatPrivacySetting == PlayerSettings.BlockingMode.All.id();
		final boolean blockNone = chatPrivacySetting == PlayerSettings.BlockingMode.None.id();

		if (player.getUpdateFlags().hasBubble()) {
			Bubble bubble = player.getUpdateFlags().getActionBubble().get();
			bubblesNeedingDisplayed.add(bubble);
		}
		if (player.getUpdateFlags().hasFiredProjectile()) {
			Projectile projectileFired = player.getUpdateFlags().getProjectile().get();
			if (projectileFired.getCaster().getIndex() != -1 && projectileFired.getVictim().getIndex() != -1) {
				projectilesNeedingDisplayed.add(projectileFired);
			}
		}

		if (player.getUpdateFlags().hasChatMessage()) {
			ChatMessage chatMessage = player.getUpdateFlags().getChatMessage();
			if (!chatMessage.getMuted() || player.hasElevatedPriveledges()) {
				// late 2001 to 2006 clients locally echo player's own chat messages instead of having the server confirm what the player sent
				if (
					!(
						// is a client that echos their own local chat messages
						(player.getClientVersion() >= 92 && player.getClientVersion() <= 204) &&
							// is public chat & not quest/private message
							(chatMessage.getRecipient() == null || chatMessage.getRecipient().isPlayer()) &&
							// chat sender is chat receiver
							((Player)chatMessage.getSender()).getUsernameHash() == player.getUsernameHash()
					) || player.getUpdateFlags().isPluginChatMessage().get() //Plugin induced messages should always show up.
				)
				{
					chatMessagesNeedingDisplayed.add(chatMessage);
				}
			}
		}
		if (player.getUpdateFlags().hasTakenDamage()) {
			Damage damage = player.getUpdateFlags().getDamage().get();
			playersNeedingDamageUpdate.add(damage);
		}
		if (player.getUpdateFlags().hasTakenHpUpdate()) {
			HpUpdate hpUpdate = player.getUpdateFlags().getHpUpdate().get();
			playersNeedingHpUpdate.add(hpUpdate);
		}
		if (viewerUsesCustomClient && player.getUpdateFlags().hasCombatEffect()) {
			combatEffectsNeedingDisplayed.addAll(player.getUpdateFlags().getCombatEffects());
		}
		if (viewerUsesCustomClient && player.getUpdateFlags().hasHitSplats()) {
			hitSplatsNeedingDisplayed.addAll(player.getUpdateFlags().getHitSplats());
		}
		if (player.getUpdateFlags().hasAppearanceChanged()) {
			playersNeedingAppearanceUpdate.add(player);
		}
		for (final Player otherPlayer : player.getLocalPlayers()) {
			final UpdateFlags updateFlags = otherPlayer.getUpdateFlags();

			if (updateFlags.hasBubble()) {
				final Bubble bubble = updateFlags.getActionBubble().get();
				bubblesNeedingDisplayed.add(bubble);
			}
			if (updateFlags.hasFiredProjectile()) {
				Projectile projectileFired = updateFlags.getProjectile().get();
				projectilesNeedingDisplayed.add(projectileFired);
			}

			if (updateFlags.hasChatMessage()
				&& (((player.getSocial().isFriendsWith(otherPlayer.getUsernameHash()) && !blockAll)
				|| (!player.getSocial().isFriendsWith(otherPlayer.getUsernameHash()) && blockNone))
				&& !player.getSocial().isIgnoring(otherPlayer.getUsernameHash())
				|| player.isMod()|| otherPlayer.isMod() || updateFlags.getChatMessage().getRecipient() != null)) {
				ChatMessage chatMessage = updateFlags.getChatMessage();
				if (!chatMessage.getMuted() || player.hasElevatedPriveledges())
					chatMessagesNeedingDisplayed.add(chatMessage);
			}
			if (updateFlags.hasTakenDamage()) {
				Damage damage = updateFlags.getDamage().get();
				playersNeedingDamageUpdate.add(damage);
			}
			if (updateFlags.hasTakenHpUpdate()) {
				HpUpdate hpUpdate = updateFlags.getHpUpdate().get();
				playersNeedingHpUpdate.add(hpUpdate);
			}
			if (viewerUsesCustomClient && updateFlags.hasCombatEffect()) {
				combatEffectsNeedingDisplayed.addAll(updateFlags.getCombatEffects());
			}
			if (viewerUsesCustomClient && updateFlags.hasHitSplats()) {
				hitSplatsNeedingDisplayed.addAll(updateFlags.getHitSplats());
			}
			if (player.requiresAppearanceUpdateFor(otherPlayer)) {
				playersNeedingAppearanceUpdate.add(otherPlayer);
			}
		}
		issuePlayerAppearanceUpdatePacket(player, bubblesNeedingDisplayed, chatMessagesNeedingDisplayed,
			projectilesNeedingDisplayed, playersNeedingDamageUpdate, playersNeedingHpUpdate, playersNeedingAppearanceUpdate,
			combatEffectsNeedingDisplayed, hitSplatsNeedingDisplayed);
	}

	private void issuePlayerAppearanceUpdatePacket(final Player player, final Queue<Bubble> bubblesNeedingDisplayed,
												   final Queue<ChatMessage> chatMessagesNeedingDisplayed, final Queue<Projectile> projectilesNeedingDisplayed,
												   final Queue<Damage> playersNeedingDamageUpdate,final Queue<HpUpdate> playersNeedingHpUpdate,

												   final Queue<Player> playersNeedingAppearanceUpdate,
												   final Queue<CombatEffect> combatEffectsNeedingDisplayed,
												   final Queue<HitSplat> hitSplatsNeedingDisplayed) {

		if (player.loggedIn()) {
			final int playersNeedingAppearanceUpdateSize = playersNeedingAppearanceUpdate.size();
			final int combatEffectsNeedingDisplayedSize = player.isUsingCustomClient() ? combatEffectsNeedingDisplayed.size() : 0;
			final int hitSplatsNeedingDisplayedSize = player.isUsingCustomClient() ? hitSplatsNeedingDisplayed.size() : 0;
			final int updateSize = bubblesNeedingDisplayed.size() + chatMessagesNeedingDisplayed.size()
				+ playersNeedingDamageUpdate.size() + projectilesNeedingDisplayed.size()
				+ playersNeedingAppearanceUpdateSize + combatEffectsNeedingDisplayedSize + hitSplatsNeedingDisplayedSize;

			// TODO: needs to be later revised for mc38
			if (updateSize > 0) {
				AppearanceUpdateStruct mainStruct = new AppearanceUpdateStruct();
				AppearanceUpdateStruct altStruct = new AppearanceUpdateStruct(); // for early mudclient, appearance update was sent appart;
				boolean isRetroClient = player.isUsing38CompatibleClient() || player.isUsing39CompatibleClient();
				boolean isCustomClient = player.isUsingCustomClient();
				boolean appearanceUpdateWithUsernameHash = player.getClientVersion() >= 61 && player.getClientVersion() <= 204;

				List<Object> updatesMain = new ArrayList<>(Math.max(4, (updateSize * 6) + playersNeedingHpUpdate.size() * 4));
				List<Object> updatesAlt = new ArrayList<>(Math.max(2, playersNeedingAppearanceUpdateSize * 20));
				if (isRetroClient) {
					if (updateSize - playersNeedingAppearanceUpdateSize > 0) {
						updatesMain.add((short) (updateSize - playersNeedingAppearanceUpdateSize));
					}
					if (playersNeedingAppearanceUpdateSize > 0) {
						updatesAlt.add((short) playersNeedingAppearanceUpdateSize);
					}
				} else if (!player.isUsingCustomClient()) {
					updatesMain.add((short) updateSize);
				} else {
					updatesMain.add((short) (updateSize + playersNeedingHpUpdate.size()));
				}

				// Note: The order that these updates are written to packet 234 is not authentic.
				// Probably the correct way to handle it is *not* having different arrays for every type of update.
				// It looks more like "playersNeedingXXXUpdate" would just be one array where mixed update types are put as-acquired.
				// There is no consistent order of update types in the real server's data.
				// It is also not consistent in order of PID. I suspect that they are ordered "as acquired and processed".
				// TODO: entire server structure regarding how UpdateFlags are used is probably wrong, but it doesn't matter much.
				// It'll be frame-accurate anyway. -- 2020-08-26 Logg

				// Update Type 0, Bubble
				Bubble b;
				while ((b = bubblesNeedingDisplayed.poll()) != null) {
					updatesMain.add((short) b.getOwner().getIndex());
					updatesMain.add((byte) 0);
					updatesMain.add((short) b.getID());
				}

				// Update Type 1: Chat Message
				// AND
				// Update Type 6: Quest Chat Message, 1 on retro client prefixed by "@que@"
				ChatMessage cm;
				while ((cm = chatMessagesNeedingDisplayed.poll()) != null) {
					Player sender = (Player) cm.getSender();
					boolean tutorialPlayer = sender.getLocation().onTutorialIsland() && !sender.hasElevatedPriveledges();
					boolean muted = cm.getMuted();

					// Determine Update Type
					int updateType;
					if (cm.getRecipient() == null) {
						if (tutorialPlayer || muted) {
							updateType = 7; // Not authentic! There is no update type 7.
						} else {
							updateType = 1; // Public Chat
						}
					} else {
						if (cm.getRecipient() instanceof Player) {
							if (tutorialPlayer || muted) {
								updateType = 7; // Not authentic! There is no update type 7.
							} else {
								updateType = 6; // Quest Chat
							}
						} else {
							updateType = 6; // Quest Chat
						}
					}

					if (isCustomClient) {
						// Non Authentic OpenRSC client
						updatesMain.add((short) cm.getSender().getIndex());
						updatesMain.add((byte) updateType);

						if (updateType == 1 || updateType == 7) {
							if (cm.getSender() != null && cm.getSender() instanceof Player)
								updatesMain.add((int) sender.getIcon());
						}

						if (updateType == 7) {
							updatesMain.add((byte) (sender.isMuted() ? 1 : 0));
							updatesMain.add((byte) (sender.getLocation().onTutorialIsland() ? 1 : 0));
						}

						if (updateType != 7 || player.isAdmin()) {
							updatesMain.add(cm.getMessageString());
						} else {
							updatesMain.add("");
						}
					} else {
						String message = cm.getMessageString();
						if (updateType == 7) {
							if (player.hasElevatedPriveledges()) {
								// Just prepend "Muted" to message, could be faked but doesn't matter.
								message = "(Muted) " + message;
								if (cm.getRecipient() == null) {
									updateType = 1;
								} else {
									updateType = 6;
								}
							}
						}
						if (updateType != 7) {
							updatesMain.add((short) cm.getSender().getIndex());
							updatesMain.add((byte) (!isRetroClient ? updateType : 1));
							if (updateType != 6 && (isCustomClient || player.isUsing233CompatibleClient())) {
								updatesMain.add((byte) sender.getIconAuthentic());
							}
							if (isRetroClient) {
								String messageUse = message;
								if (updateType == 6) messageUse = "@que@" + message;
								updatesMain.add((byte) messageUse.length());
								updatesMain.add(messageUse);
							} else {
								updatesMain.add(new RSCString(message));
							}
						} else {
							LOGGER.error("extraneous chat update packet will crash the authentic client...!");
						}
					}
				}

				// Update Type 2: Damage Update
				Damage playerNeedingHitsUpdate;
				while ((playerNeedingHitsUpdate = playersNeedingDamageUpdate.poll()) != null) {
					updatesMain.add((short) playerNeedingHitsUpdate.getIndex());
					updatesMain.add((byte) 2);
					updatesMain.add((byte) playerNeedingHitsUpdate.getDamage());
					updatesMain.add((byte) playerNeedingHitsUpdate.getCurHits());
					updatesMain.add((byte) playerNeedingHitsUpdate.getMaxHits());
				}

				// Update Types 3 & 4: Projectile Update (draws the projectile)
				Projectile projectile;
				while ((projectile = projectilesNeedingDisplayed.poll()) != null) {
					Entity victim = projectile.getVictim();
					if (victim.isNpc()) {
						updatesMain.add((short) projectile.getCaster().getIndex());
						updatesMain.add((byte) 3);
						updatesMain.add((short) projectile.getType());
						updatesMain.add((short) victim.getIndex());
					} else if (victim.isPlayer()) {
						updatesMain.add((short) projectile.getCaster().getIndex());
						updatesMain.add((byte) 4);
						updatesMain.add((short) projectile.getType());
						updatesMain.add((short) victim.getIndex());
					}
				}

				// Update Type 5: Player appearance and identity
				Player playerNeedingAppearanceUpdate;
				while ((playerNeedingAppearanceUpdate = playersNeedingAppearanceUpdate.poll()) != null) {
					PlayerAppearance appearance = playerNeedingAppearanceUpdate.getSettings().getAppearance();

					if (isRetroClient) {
						updatesAlt.add((short) playerNeedingAppearanceUpdate.getIndex()); // server index
						updatesAlt.add((short) playerNeedingAppearanceUpdate.getIndex()); // server id
						updatesAlt.add((long) DataConversions.usernameToHash(playerNeedingAppearanceUpdate.getUsername()));
					} else {
						updatesMain.add((short) playerNeedingAppearanceUpdate.getIndex());
						updatesMain.add((byte) 5);
						if (player.isUsing233CompatibleClient()) {
							updatesMain.add((short) player.getAppearanceID());
							updatesMain.add(playerNeedingAppearanceUpdate.getUsername());

							// TODO: just send username twice if this packet can be chunked up better later
							// TODO: updatesMain.add(playerNeedingAppearanceUpdate.getUsername()); // Pretty sure this is unnecessary & always redundant authentically.
							if (playerNeedingAppearanceUpdate.equals(player) || playersNeedingAppearanceUpdateSize < 65) {
								updatesMain.add(playerNeedingAppearanceUpdate.getUsername());
							} else {
								// this current behaviour is slightly buggy esp on rsc+, but will save bytes towards the 5000 allowed.
								updatesMain.add(playerNeedingAppearanceUpdate.getUsername().substring(0, 1));
							}
						} else if (appearanceUpdateWithUsernameHash) {
							updatesMain.add((short) player.getAppearanceID());
							updatesMain.add(playerNeedingAppearanceUpdate.getUsernameHash());
						} else if (player.isUsingCustomClient()) {
							updatesMain.add(playerNeedingAppearanceUpdate.getUsername());
						}
					}

					if (playerNeedingAppearanceUpdate.getPossessing() != null) {
						// while possessing another creature
						// do not wish to see any sprites of our own character under any circumstance
						if (isRetroClient) {
							updatesAlt.add((byte) 0); // Equipment count
						} else {
							updatesMain.add((byte) 0); // Equipment count
						}
					} else if (!isCustomClient &&
						(playerNeedingAppearanceUpdate.stateIsInvisible() ||
							playerNeedingAppearanceUpdate.stateIsInvulnerable())) {
						// Handle Invisibility & Invulnerability in the authentic client

						int[] wornItems = playerNeedingAppearanceUpdate.getWornItemsForAppearanceUpdate();

						int bootColour = wornItems[AppearanceId.SLOT_BOOTS]; // if player is already wearing boots, we can let them choose their colour. :-)
						if (wornItems[AppearanceId.SLOT_BOOTS] == 0) {
							if (isRetroClient) {
								bootColour = AppearanceId.LEATHER_BOOTS.id();
							} else {
								bootColour = AppearanceId.SHADOW_WARRIOR_BOOTS.id(); // default
							}
						}

						int shieldSprite = 0; // default to invisible
						if (playerNeedingAppearanceUpdate.stateIsInvulnerable()) {
							if (isRetroClient) {
								if (wornItems[AppearanceId.SLOT_SHIELD] == AppearanceId.ADAMANTITE_SQUARE_SHIELD.id()) {
									shieldSprite = AppearanceId.WOODEN_SHIELD.id();
								} else {
									shieldSprite = AppearanceId.ADAMANTITE_SQUARE_SHIELD.id();
								}
							} else {
								if (wornItems[AppearanceId.SLOT_SHIELD] == AppearanceId.DRAGON_SQUARE_SHIELD.id()) {
									shieldSprite = AppearanceId.RUNE_SQUARE_SHIELD.id();
								} else {
									shieldSprite = AppearanceId.DRAGON_SQUARE_SHIELD.id();
								}
							}
						}

						int gloveColour = wornItems[AppearanceId.SLOT_GLOVES]; // let player keep their gloves, even if they have none
						if (wornItems[AppearanceId.SLOT_GLOVES] == 0 && wornItems[AppearanceId.SLOT_WEAPON] != 0) {
							// give player gloves if they are wielding a weapon
							gloveColour = AppearanceId.LEATHER_GLOVES.id();
						}

						// if player is just invulnerable & not invisible, give them a dark-robed appearance
						int headSprite = 0; // default to invisible
						int hatSprite = 0;
						int bodySprite = 0;
						int legSprite = 0;
						int pantsSprite = 0;
						int shirtSprite = 0;
						int amuletSprite = wornItems[AppearanceId.SLOT_AMULET];
						if (!playerNeedingAppearanceUpdate.stateIsInvisible()) {
							headSprite = wornItems[AppearanceId.SLOT_HEAD];
							if (wornItems[AppearanceId.SLOT_HAT] == 0) {
								hatSprite = AppearanceId.LARGE_BLACK_HELMET.id();
								headSprite = AppearanceId.NOTHING.id();
							} else {
								hatSprite = wornItems[AppearanceId.SLOT_HAT];
							}

							// dark robes
							if (isRetroClient) {
								bodySprite = AppearanceId.DARKWIZARDS_ROBE.id();
								legSprite = AppearanceId.BLACK_SKIRT.id();
							} else {
								bodySprite = AppearanceId.SHADOW_WARRIOR_ROBE.id();
								legSprite = AppearanceId.SHADOW_WARRIOR_SKIRT.id();
							}
							pantsSprite = AppearanceId.COLOURED_PANTS.id();
							shirtSprite = AppearanceId.FEMALE_BODY.id();
							if (isRetroClient) {
								gloveColour = AppearanceId.LEATHER_GLOVES.id();
								amuletSprite = AppearanceId.SILVER_NECKLACE.id();
							} else {
								gloveColour = AppearanceId.ICE_GLOVES.id();
								amuletSprite = AppearanceId.PENDANT_OF_LUCIEN.id();
							}
						}

						// as char to indicate to the generator to use appearancebyte
						if (isRetroClient) {
							updatesAlt.add((byte) 11); // Equipment count
							updatesAlt.add((char) (AppearanceRetroConverter.convert(headSprite) & 0xFF));
							updatesAlt.add((char) (AppearanceRetroConverter.convert(shirtSprite) & 0xFF));
							updatesAlt.add((char) (AppearanceRetroConverter.convert(pantsSprite) & 0xFF));
							updatesAlt.add((char) (AppearanceRetroConverter.convert(shieldSprite) & 0xFF));
							updatesAlt.add((char) (AppearanceRetroConverter.convert(wornItems[AppearanceId.SLOT_WEAPON]) & 0xFF));
							updatesAlt.add((char) (AppearanceRetroConverter.convert(hatSprite) & 0xFF));
							updatesAlt.add((char) (AppearanceRetroConverter.convert(bodySprite) & 0xFF));
							updatesAlt.add((char) (AppearanceRetroConverter.convert(legSprite) & 0xFF));
							updatesAlt.add((char) (AppearanceRetroConverter.convert(gloveColour) & 0xFF));
							updatesAlt.add((char) (AppearanceRetroConverter.convert(bootColour) & 0xFF));
							updatesAlt.add((char) (AppearanceRetroConverter.convert(amuletSprite) & 0xFF));
						} else {
							updatesMain.add((byte) 11); // Equipment count
							updatesMain.add((char) headSprite);
							updatesMain.add((char) shirtSprite);
							updatesMain.add((char) pantsSprite);
							updatesMain.add((char) shieldSprite);
							updatesMain.add((char) wornItems[AppearanceId.SLOT_WEAPON]);
							updatesMain.add((char) hatSprite);
							updatesMain.add((char) bodySprite);
							updatesMain.add((char) legSprite);
							updatesMain.add((char) gloveColour);
							updatesMain.add((char) bootColour);
							updatesMain.add((char) amuletSprite);
						}
						// No Cape
					} else {
						// normal appearance update (not invisible)
						int[] wornItems = playerNeedingAppearanceUpdate.getWornItemsForAppearanceUpdate();
						if (isRetroClient) {
							updatesAlt.add((byte) wornItems.length);
						} else {
							updatesMain.add((byte) wornItems.length);
						}
						for (int i : wornItems) {
							if (isRetroClient) {
								updatesAlt.add((char) (AppearanceRetroConverter.convert(i) & 0xFF));
							} else if (isCustomClient) {
								updatesMain.add((short) i);
							} else {
								updatesMain.add((char) (i & 0xFF));
							}
						}
					}

					if (isRetroClient) {
						updatesAlt.add((char) appearance.getHairColour());
						updatesAlt.add((char) appearance.getTopColour());
						updatesAlt.add((char) appearance.getTrouserColour());
						updatesAlt.add((char) appearance.getSkinColour(playerNeedingAppearanceUpdate.getClientLimitations().maxSkinColor));
						updatesAlt.add((byte) playerNeedingAppearanceUpdate.getPkMode()); //is player attackable?
						updatesAlt.add((byte) playerNeedingAppearanceUpdate.getCombatLevel());
						updatesAlt.add((byte) playerNeedingAppearanceUpdate.getSkullType());
					} else {
						updatesMain.add((char) appearance.getHairColour());
						updatesMain.add((char) appearance.getTopColour());
						updatesMain.add((char) appearance.getTrouserColour());
						updatesMain.add((char) appearance.getSkinColour(playerNeedingAppearanceUpdate.getClientLimitations().maxSkinColor));
						updatesMain.add((byte) playerNeedingAppearanceUpdate.getCombatLevel());
						updatesMain.add((byte) playerNeedingAppearanceUpdate.getSkullType());
					}

					if (isCustomClient) {
						if (playerNeedingAppearanceUpdate.getClan() != null) {
							updatesMain.add((byte) 1);
							updatesMain.add(playerNeedingAppearanceUpdate.getClan().getClanTag());
						} else {
							updatesMain.add((byte) 0);
						}

						updatesMain.add((byte) (playerNeedingAppearanceUpdate.stateIsInvisible() ? 1 : 0));
						updatesMain.add((byte) (playerNeedingAppearanceUpdate.stateIsInvulnerable() ? 1 : 0));
						updatesMain.add((byte) playerNeedingAppearanceUpdate.getGroupID());
						updatesMain.add((int) playerNeedingAppearanceUpdate.getIcon());
					}
				}

				if (isCustomClient) {
					// Non authentic type 10. Plays a client-side visual effect over the target.
					CombatEffect combatEffect;
					while ((combatEffect = combatEffectsNeedingDisplayed.poll()) != null) {
						updatesMain.add((short) combatEffect.getTarget().getIndex());
						updatesMain.add((byte) 10);
						updatesMain.add((byte) combatEffect.getEffectType());
					}

					// Non authentic type 11. Source-specific damage/healing indicators for custom clients.
					HitSplat hitSplat;
					while ((hitSplat = hitSplatsNeedingDisplayed.poll()) != null) {
						updatesMain.add((short) hitSplat.getIndex());
						updatesMain.add((byte) 11);
						updatesMain.add((byte) hitSplat.getType());
						updatesMain.add((byte) hitSplat.getAmount());
						updatesMain.add((byte) hitSplat.getCurHits());
						updatesMain.add((byte) hitSplat.getMaxHits());
					}

					// Non authentic type 9. In authentic network protocol, this information is just in type 2.
					HpUpdate playerNeedingHpUpdate;
					while ((playerNeedingHpUpdate = playersNeedingHpUpdate.poll()) != null) {
						updatesMain.add((short) playerNeedingHpUpdate.getIndex());
						updatesMain.add((byte) 9);
						updatesMain.add((byte) playerNeedingHpUpdate.getCurHits());
						updatesMain.add((byte) playerNeedingHpUpdate.getMaxHits());
					}
				}

				mainStruct.info = updatesMain;
				altStruct.info = updatesAlt;
				if (updatesMain.size() > 0 ) {
					tryFinalizeAndSendPacket(OpcodeOut.SEND_UPDATE_PLAYERS, mainStruct, player);
				}
				if (updatesAlt.size() > 0) {
					tryFinalizeAndSendPacket(OpcodeOut.SEND_UPDATE_PLAYERS_RETRO, altStruct, player);
				}
			}
		}
	}

	protected void updateGameObjects(final Player playerToUpdate, final Collection<GameObject> visibleGameObjects) {
		boolean changed = false;

		GameObjectsUpdateStruct struct = new GameObjectsUpdateStruct();
		List<GameObjectLoc> objectLocs = new ArrayList<>(playerToUpdate.getLocalGameObjects().size() + visibleGameObjects.size());

		// TODO: Unloading scenery is not handled correctly.
		//       According to RSC+ replays, the server never tells the client to unload objects until
		//       a region is unloaded. It then instructs the client to only unload the region.
		//       Right now the server is very aggressive in unloading scenery, which is detrimental for clients with a larger view

		for (final Iterator<GameObject> it$ = playerToUpdate.getLocalGameObjects().iterator(); it$.hasNext(); ) {
			final GameObject o = it$.next();
			if (!playerToUpdate.withinObjectGridRange(o) || o.isRemoved() || o.isInvisibleTo(playerToUpdate)) {
				final int offsetX = o.getX() - playerToUpdate.getX();
				final int offsetY = o.getY() - playerToUpdate.getY();
				//If the object is close enough we can use regular way to remove:
				if (offsetX > -128 && offsetY > -128 && offsetX < 128 && offsetY < 128) {
					objectLocs.add(new GameObjectLoc(60000, offsetX, offsetY, o.getDirection(), 0));
					it$.remove();
					changed = true;
				} else {
					//If it's not close enough we need to use the region clean packet
					playerToUpdate.getLocationsToClear().add(o.getLocation());
					it$.remove();
					changed = true;
				}
			}
		}

		// Add scenery
		for (final GameObject newObject : visibleGameObjects) {
			boolean skipAdd = newObject.isRemoved() ||
				newObject.isInvisibleTo(playerToUpdate) ||
				newObject.getType() != 0 || // not a wallObject
				playerToUpdate.getLocalGameObjects().contains(newObject);
			if (!playerToUpdate.isUsingCustomClient()) {
				// Honestly don't think this does anything because the scenery isn't iterated over in the view anyway
				// TODO: funny behaviour where if a rock is mined > 16 tiles from you, it can be removed but not replaced until you get closer.
				skipAdd |= !playerToUpdate.within4GridRange(newObject);
			} else {
				skipAdd |= !playerToUpdate.withinObjectGridRange(newObject);
			}
			if (skipAdd) {
				continue;
			}

			final int offsetX = newObject.getX() - playerToUpdate.getX();
			final int offsetY = newObject.getY() - playerToUpdate.getY();

			final int newObjectId = retroRockConverter(playerToUpdate, newObject.getLoc());

			objectLocs.add(new GameObjectLoc(newObjectId, offsetX, offsetY, newObject.getDirection(), 0));
			playerToUpdate.getLocalGameObjects().add(newObject);
			changed = true;
		}
		struct.objects = objectLocs;
		if (changed) {
			tryFinalizeAndSendPacket(OpcodeOut.SEND_SCENERY_HANDLER, struct, playerToUpdate);
		}
	}

	// Rocks should not have their appearances changed prior to client 157 which introduced fatigue & mining improvements
	private int retroRockConverter(Player playerToUpdate, GameObjectLoc curSceneryLoc) {
		int permId = curSceneryLoc.perm_id;
		int curId = curSceneryLoc.id;
		if (curId == SceneryId.ROCK_GENERIC.id()) {
			if (permId != SceneryId.ROCK_GENERIC.id()) {
				if (playerToUpdate.getClientVersion() < 157) {
					return permId;
				}
			}
		}
		return curId;
	}

	protected void updateGroundItems(final Player playerToUpdate, final Collection<GroundItem> visibleGroundItems) {
		boolean changed = false;

		GroundItemsUpdateStruct struct = new GroundItemsUpdateStruct();
		List<ItemLoc> itemLocs = new ArrayList<>(playerToUpdate.getLocalGroundItems().size() + visibleGroundItems.size());

		for (final Iterator<GroundItem> it$ = playerToUpdate.getLocalGroundItems().iterator(); it$.hasNext(); ) {
			final GroundItem groundItem = it$.next();
			final int offsetX = (groundItem.getX() - playerToUpdate.getX());
			final int offsetY = (groundItem.getY() - playerToUpdate.getY());

			if (!playerToUpdate.withinObjectGridRange(groundItem)) {
				if (offsetX > -128 && offsetY > -128 && offsetX < 128 && offsetY < 128) {
					// respawnTime = -1 to indicate to clear not on range
					itemLocs.add(new ItemLoc(groundItem.getID(), offsetX, offsetY, groundItem.getAmount(), -1,
						groundItem.getNoted() && getServer().getConfig().WANT_BANK_NOTES ? 1 : 0));
				} else {
					playerToUpdate.getLocationsToClear().add(groundItem.getLocation());
				}
				it$.remove();
				changed = true;
			} else if (groundItem.isRemoved() || groundItem.isInvisibleTo(playerToUpdate)) {
				itemLocs.add(new ItemLoc(groundItem.getID() + 32768, offsetX, offsetY, groundItem.getAmount(), 0,
					groundItem.getNoted() && getServer().getConfig().WANT_BANK_NOTES ? 1 : 0));
				//System.out.println("Removing " + groundItem + " with isRemoved() remove: " + offsetX + ", " + offsetY);
				it$.remove();
				changed = true;
			}
		}

		for (final GroundItem groundItem : visibleGroundItems) {
			if (!playerToUpdate.withinObjectGridRange(groundItem) || groundItem.isRemoved()
				|| groundItem.isInvisibleTo(playerToUpdate)
				|| playerToUpdate.getLocalGroundItems().contains(groundItem)) {
				continue;
			}
			final int offsetX = groundItem.getX() - playerToUpdate.getX();
			final int offsetY = groundItem.getY() - playerToUpdate.getY();
			itemLocs.add(new ItemLoc(groundItem.getID(), offsetX, offsetY, groundItem.getAmount(), 0,
				groundItem.getNoted() && getServer().getConfig().WANT_BANK_NOTES ? 1 : 0));
			playerToUpdate.getLocalGroundItems().add(groundItem);
			changed = true;
		}
		struct.objects = itemLocs;
		if (changed) {
			tryFinalizeAndSendPacket(OpcodeOut.SEND_GROUND_ITEM_HANDLER, struct, playerToUpdate);
		}
	}

	protected void updateWallObjects(final Player playerToUpdate, final Collection<GameObject> visibleGameObjects) {
		boolean changed = false;

		GameObjectsUpdateStruct struct = new GameObjectsUpdateStruct();
		List<GameObjectLoc> objectLocs = new ArrayList<>(playerToUpdate.getLocalWallObjects().size() + visibleGameObjects.size());

		// remove all boundaries that need to be removed
		for (final Iterator<GameObject> it$ = playerToUpdate.getLocalWallObjects().iterator(); it$.hasNext(); ) {
			final GameObject o = it$.next();
			if (!playerToUpdate.withinObjectGridRange(o) || (o.isRemoved() || o.isInvisibleTo(playerToUpdate))) {
				final int offsetX = o.getX() - playerToUpdate.getX();
				final int offsetY = o.getY() - playerToUpdate.getY();
				if (offsetX > -128 && offsetY > -128 && offsetX < 128 && offsetY < 128) {
					if (!playerToUpdate.isUsingCustomClient()) {
						// The authentic server does not really send removals for boundaries.
						// The client is able to handle having boundaries overwritten by new boundaries, but
						// it doesn't correctly handle having boundaries outright removed.
						//
						// The RSC server may have sent proper removals at one time, the structure is there in the client,
						// but in 2018, the server does something which confuses me, and it should be considered a bug in the server.
						//
						// Sometimes when adding a boundary, it will send a removal for some unrelated coordinate first.
						// The coordinate it specifies for boundary removal *does not* have a boundary at that location.
						// If it did have a boundary, it would cause erroneous extraneous removals of nearby boundaries.
						// I haven't spent a lot of time looking at it to discern any further pattern, if there is one. Sorry.
						//
						// TODO: determine the pattern that the server uses to send its buggy "random" boundary removal instructions
						// Until this is implemented, the server will not be 100% authentic to 2018 RSC.
						// (Also, removals & additions are intertwined, not in a removal block & addition block, as structured here)
						//
						// I went through the effort of writing code in the RSCMinus scraper to check if the boundary removal command
						// *ever* successfully removed a boundary.
						// ...
						// **It never does.**
						// ...
						// Because X & Y coordinates never match with the coordinate of a boundary that has been added,
						// all instances where 0xFF removal are invoked are effectively NO-OPs.
						// Therefore, no buggy behaviour from omitting the ability to remove boundaries should arise.

                        /* RSC235 Compatible removal code, shouldn't be used
                        packet.writeByte(0xFF);
                        packet.writeByte(offsetX);
                        packet.writeByte(offsetY);
                        */

						/* Addendum - code is identical for pre-233 mudclients
						 * removal code likely was not used either
						 * */

					} else {
						objectLocs.add(new GameObjectLoc(60000, offsetX, offsetY, o.getDirection(), 1));
						changed = true;
					}
					it$.remove();
				} else {
					playerToUpdate.getLocationsToClear().add(o.getLocation());
					it$.remove();
					changed = true;
				}
			}
		}

		// add all new boundaries to be added
		for (final GameObject newObject : visibleGameObjects) {
			if (!playerToUpdate.withinObjectGridRange(newObject) || newObject.isRemoved()
				|| newObject.isInvisibleTo(playerToUpdate) || newObject.getType() != 1
				|| playerToUpdate.getLocalWallObjects().contains(newObject)) {
				continue;
			}

			final int offsetX = newObject.getX() - playerToUpdate.getX();
			final int offsetY = newObject.getY() - playerToUpdate.getY();
			objectLocs.add(new GameObjectLoc(newObject.getID(), offsetX, offsetY, newObject.getDirection(), 1));
			playerToUpdate.getLocalWallObjects().add(newObject);
			changed = true;
		}
		struct.objects = objectLocs;
		if (changed) {
			tryFinalizeAndSendPacket(OpcodeOut.SEND_BOUNDARY_HANDLER, struct, playerToUpdate);
		}
	}

	protected void sendAppearanceKeepalive(final Player player) {
		NoPayloadStruct struct = new NoPayloadStruct();
		tryFinalizeAndSendPacket(OpcodeOut.SEND_APPEARANCE_KEEPALIVE, struct, player);
	}

	protected void sendClearLocations(final Player player) {
		if (player.getLocationsToClear().size() > 0) {
			ClearLocationsStruct struct = new ClearLocationsStruct();
			List<Point> pointList = new ArrayList<>(player.getLocationsToClear().size());
			for (final Point point : player.getLocationsToClear()) {
				final int offsetX = point.getX() - player.getX();
				final int offsetY = point.getY() - player.getY();
				pointList.add(new Point(offsetX, offsetY));
			}
			player.getLocationsToClear().clear();
			struct.points = pointList;
			tryFinalizeAndSendPacket(OpcodeOut.SEND_REMOVE_WORLD_ENTITY, struct, player);
		}
	}

	public final long updateWorld() {
		return getServer().bench(() -> getServer().getWorld().run());
	}

	public final long updateClient(final Player player) {
		return getServer().bench(() -> {
			sendUpdatePackets(player);
		});
	}

	public final long doCleanup() { // it can do the teleport at this time.
		return getServer().bench(() -> {
			World world = getServer().getWorld();
			world.getPlayers().forEachLive(Player::resetAfterUpdate);
			world.getNpcs().forEachLive(Npc::resetAfterUpdate);
		});
	}

	public final long executeWalkToActions(final Player player) {
		return getServer().bench(() -> {
			if (player.getWalkToAction() != null) {
				if (player.getWalkToAction().shouldExecute()) {
					player.getWalkToAction().execute();
				}
			}
		});
	}

	public final long processNpcs() {
		return getServer().bench(() -> {
			final boolean shouldUpdatePosition = !getServer().getConfig().WANT_CUSTOM_WALK_SPEED;
			final EntityList<Npc> npcs = getServer().getWorld().getNpcs();
			final boolean hasPlayers = getServer().getWorld().getPlayers().size() > 0;
			if (!getServer().isFoundationBenchmarkEnabled()) {
				npcs.forEachLive(n -> {
					try {
						if (n.isUnregistering()) {
							getServer().getWorld().unregisterNpc(n);
							return;
						}

						// NPC behavior stays on the game tick. Custom walking only changes movement cadence.
						if (shouldUpdatePosition) {
							n.updatePosition(hasPlayers);
						} else {
							n.updateBehavior(hasPlayers);
						}
					} catch (final Exception e) {
						LOGGER.error("Error while updating " + n + " at position " + n.getLocation() + " loc: " + n.getLoc());
						LOGGER.catching(e);
					}
				});
				return;
			}

			final long[] unregisterDuration = new long[1];
			final long[] behaviorDuration = new long[1];
			final long[] movementDuration = new long[1];
			npcs.forEachLive(n -> {
				try {
					if (n.isUnregistering()) {
						unregisterDuration[0] += getServer().bench(() -> getServer().getWorld().unregisterNpc(n));
						return;
					}

					// NPC behavior stays on the game tick. Custom walking only changes movement cadence.
					behaviorDuration[0] += getServer().bench(() -> n.updateBehavior(hasPlayers));
					if (!shouldUpdatePosition) {
						return;
					}
					movementDuration[0] += getServer().bench(n::updateMovementOnly);
				} catch (final Exception e) {
					LOGGER.error("Error while updating " + n + " at position " + n.getLocation() + " loc: " + n.getLoc());
					LOGGER.catching(e);
				}
			});
			getServer().incrementLastProcessNpcUnregisterDuration(unregisterDuration[0]);
			getServer().incrementLastProcessNpcBehaviorDuration(behaviorDuration[0]);
			getServer().incrementLastProcessNpcMovementDuration(movementDuration[0]);
		});
	}

	/**
	 * Updates the messages queues for each player
	 */
	public final long processMessageQueue(final Player player) {
		return getServer().bench(() -> {
			final PrivateMessage pm = player.getNextPrivateMessage();
			if (pm != null) {
				Player affectedPlayer = getServer().getWorld().getPlayer(pm.getFriend());
				if (affectedPlayer != null) {
					boolean blockAll = affectedPlayer.getSettings().getPrivacySetting(PlayerSettings.PRIVACY_BLOCK_PRIVATE_MESSAGES, affectedPlayer.isUsingCustomClient())
						== PlayerSettings.BlockingMode.All.id();
					boolean blockNone = affectedPlayer.getSettings().getPrivacySetting(PlayerSettings.PRIVACY_BLOCK_PRIVATE_MESSAGES, affectedPlayer.isUsingCustomClient())
						== PlayerSettings.BlockingMode.None.id();
					if (!player.getSocial().isFriendsWith(affectedPlayer.getUsernameHash()) && !player.isPlayerMod()) {
						player.message("Unable to send message - player not on your friendlist.");
					} else if (((affectedPlayer.getSocial().isFriendsWith(player.getUsernameHash()) && !blockAll) || blockNone)
						&& !affectedPlayer.getSocial().isIgnoring(player.getUsernameHash()) || player.isMod()) {
						ActionSender.sendPrivateMessageSent(player, affectedPlayer.getUsernameHash(), pm.getMessage(), false);
						ActionSender.sendPrivateMessageReceived(affectedPlayer, player, pm.getMessage(), false);
					} else if (player.getClientVersion() <= 204) {
						player.playerServerMessage(MessageType.PRIVATE_SEND,"@cya@" + DataConversions.hashToUsername(pm.getFriend()) + " is offline or has privacy mode enabled");
					}

					player.getWorld().getServer().getGameLogger().addQuery(new PMLog(player.getWorld(), player.getUsername(), pm.getMessage(),
						DataConversions.hashToUsername(pm.getFriend())));
				} else {
					// player not online
					if (pm.getFriend() >= 0L) {
						try {
							int friendId = player.getWorld().getServer().getDatabase().playerIdFromUsername(DataConversions.hashToUsername(pm.getFriend()));

							if (player.getWorld().getServer().getDatabase().playerExists(friendId)) {
								// player not online
								if (player.getClientVersion() <= 204) {
									player.playerServerMessage(MessageType.PRIVATE_SEND,"@cya@" + DataConversions.hashToUsername(pm.getFriend()) + " is offline or has privacy mode enabled");
								} else {
									player.message("Unable to send message - player unavailable.");
								}
							}
						} catch (Exception e) { }
					}
				}
			}

			if (player.requiresOfferUpdate()) {
				ActionSender.sendTradeItems(player);
				player.setRequiresOfferUpdate(false);
			}
		});
	}

	/**
	 * Update the position of players, and check if who (and what) they are
	 * aware of needs updated
	 */
	public final long movePlayer(final Player player) {
		return getServer().bench(() -> {

			if (player.isUnregistering() && player.isLoggedIn()) {
				return;
			}

			// Only do the walking tick here if the Players' walking tick matches the game tick
			if(!getServer().getConfig().WANT_CUSTOM_WALK_SPEED) {
				player.updatePosition();
			}

			// TODO: maybe not this here, but maybe it's fine
			if (player.getUpdateFlags().hasAppearanceChanged()) {
				player.incAppearanceID();
			}
		});
	}

	public long executePidlessCatching() {
		return getServer().bench(() -> {
			if (getServer().getConfig().PIDLESS_CATCHING) {
				// Executed after all players have moved, we check a second time this tick
				// if the higher pid player is now close enough to catch the lower pid player.
				for (final Player player : getServer().getWorld().getPlayers()) {
					if (player.getWalkToAction() != null) {
						if (player.getWalkToAction().isPvPAttack()) {
							if (player.getWalkToAction().shouldExecute()) {
								player.getWalkToAction().execute();
							}
						}
					}
				}
			}
		});
	}
}
