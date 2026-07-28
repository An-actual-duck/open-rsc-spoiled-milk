package com.openrsc.server;

import com.openrsc.server.constants.AppearanceId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.database.DatabaseLookupResult;
import com.openrsc.server.database.GameDatabase;
import com.openrsc.server.database.impl.mysql.queries.logging.PMLog;
import com.openrsc.server.external.GameObjectLoc;
import com.openrsc.server.external.ItemLoc;
import com.openrsc.server.io.NativeLayeredTerrainChunk;
import com.openrsc.server.io.NativeLayeredTerrainSector;
import com.openrsc.server.io.NativeLayeredWorldPackage;
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
import com.openrsc.server.model.world.coordinate.LayeredSpatialWindowKey;
import com.openrsc.server.model.world.coordinate.LayeredCompatibilityPointAdapter;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldMapSectorId;
import com.openrsc.server.model.world.region.VisibilitySnapshot;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.NativeLayeredTerrainClientResidency;
import com.openrsc.server.net.rsc.NativeLayeredTerrainReadiness;
import com.openrsc.server.net.rsc.NativeLayeredTerrainStageReadiness;
import com.openrsc.server.net.rsc.NativeLayeredTerrainWireCache;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.incoming.LayeredTerrainReadyStruct;
import com.openrsc.server.net.rsc.struct.incoming
	.LayeredTerrainStageReadyStruct;
import com.openrsc.server.net.rsc.struct.outgoing.*;
import com.openrsc.server.plugins.triggers.TimedEventTrigger;
import com.openrsc.server.util.EntityList;
import com.openrsc.server.util.rsc.AppearanceRetroConverter;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.zip.Deflater;

import static com.openrsc.server.net.rsc.ActionSender.isRetroClient;
import static com.openrsc.server.net.rsc.ActionSender.tryFinalizeAndSendPacket;
import static com.openrsc.server.net.rsc.ActionSender.tryFinalizeAndSendPacketChecked;

public final class GameStateUpdater {
	private enum VisibilitySnapshotMode {
		LEGACY,
		SNAPSHOT
	}

	private static final int CUSTOM_MOB_COORD_OFFSET_BITS = 8;
	private static final int CLIENT_LOCAL_SECTION_SIZE = 48;
	private static final int CLIENT_LOCAL_ACTIVE_SECTION_GRID = 3;
	private static final int CLIENT_LOCAL_ACTIVE_SECTION_ORIGIN_OFFSET = CLIENT_LOCAL_ACTIVE_SECTION_GRID / 2;
	private static final int CLIENT_LOCAL_TILE_COUNT = CLIENT_LOCAL_SECTION_SIZE * CLIENT_LOCAL_ACTIVE_SECTION_GRID;
	private static final int CLIENT_LOCAL_PLANE_WIDTH = 2304;
	private static final int CLIENT_LOCAL_PLANE_HEIGHT = 1776;
	private static final int CLIENT_LOCAL_REGION_RELOAD_RADIUS = 32;
	private static final int CUSTOM_CLIENT_REGION_REFRESH_RADIUS = 80;
	private static final int CUSTOM_MOVEMENT_UPDATE_LIMIT = 0xFFFF;
	private static final int AUTHENTIC_LOCAL_MOB_LIMIT = 255;
	private static final int CUSTOM_LOCAL_MOB_LIMIT = 0xFFFF;
	private static final int AUTHENTIC_LOCAL_MOB_COUNT_BITS = 8;
	private static final int CUSTOM_LOCAL_MOB_COUNT_BITS = 16;
	private static final int MOVEMENT_SNAPSHOT_PROTOCOL_VERSION = 1;
	private static final int LAYERED_MOVEMENT_SNAPSHOT_PROTOCOL_VERSION = 2;
	private static final int MOVEMENT_SNAPSHOT_FIXED_PAYLOAD_BYTES = 18;
	private static final int LAYERED_CONTEXT_SEQUENCE_BYTES = 4;
	private static final int MOVEMENT_SNAPSHOT_MOB_RECORD_BYTES = 7;
	private static final int SCENE_BASELINE_PROTOCOL_VERSION = 5;
	private static final int LAYERED_SCENE_BASELINE_PROTOCOL_VERSION = 6;
	private static final int ATOMIC_SCENE_FENCE_PROTOCOL_VERSION = 7;
	private static final int LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION = 1;
	private static final int SYNTHETIC_DEEP_SCENE_CONTEXT_PROTOCOL_VERSION = 2;
	private static final int NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION = 5;
	private static final int RESIDENT_NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION = 6;
	private static final int READY_RESIDENT_NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION = 7;
	private static final int ATOMIC_NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION = 8;
	private static final int LAYERED_TERRAIN_READY_PROTOCOL_VERSION = 1;
	private static final int LAYERED_TERRAIN_STAGE_PROTOCOL_VERSION = 1;
	private static final int LAYERED_TERRAIN_SYMMETRIC_RESIDENCY_PROTOCOL_VERSION = 2;
	private static final int LAYERED_TERRAIN_SYMMETRIC_STRUCTURE_PROTOCOL_VERSION = 3;
	private static final int NATIVE_LAYERED_PREDICTIVE_LEAD_TILES = 48;
	private static final int NATIVE_LAYERED_CHUNK_RADIUS = 1;
	private static final int NATIVE_LAYERED_SYMMETRIC_RESIDENCY_RADIUS = 2;
	private static final int NATIVE_LAYERED_WIRE_CHUNK_SIZE =
		NativeLayeredTerrainSector.SIZE;
	private static final int NATIVE_LAYERED_VISUAL_TILE_WIRE_BYTES = 3;
	private static final int NATIVE_LAYERED_STRUCTURAL_TILE_WIRE_BYTES = 7;
	private static final String NATIVE_LAYERED_VISUAL_ENCODING =
		"visual-layered-sector-v1";
	private static final String NATIVE_LAYERED_STRUCTURAL_ENCODING =
		"structural-layered-sector-v1";
	private static final int SCENE_BASELINE_PAGE_SIZE = 64;
	private static final int SCENE_BASELINE_PAGE_BURST_LIMIT = 4;
	private static final int SCENE_BASELINE_FIXED_PAYLOAD_BYTES = 48;
	private static final int SCENE_BASELINE_OBJECT_RECORD_BYTES = 8;
	private static final int SCENE_BASELINE_PAGE_NONE = 0;
	private static final int SCENE_BASELINE_PAGE_SCENERY = 1;
	private static final int SCENE_BASELINE_PAGE_WALLS = 2;
	private static final int SCENE_BASELINE_PAGE_ATOMIC_FENCE = 3;
	private static final String NPC_DEATH_VISUAL_SENT_TICK_PREFIX = "npc_death_visual_sent_tick_";
	private static final String SCENE_BASELINE_SUMMARY_ATTRIBUTE = "scene_baseline_summary";
	private static final String STATIC_SCENE_SCAN_KEY_ATTRIBUTE = "static_scene_scan_key";
	private static final String WORLD_TIME_LAST_SYNC_MILLIS_ATTRIBUTE = "world_time_last_sync_millis";
	private static final String CUSTOM_MOVEMENT_CLIENT_MID_X_ATTRIBUTE = "custom_movement_client_mid_x";
	private static final String CUSTOM_MOVEMENT_CLIENT_MID_Y_ATTRIBUTE = "custom_movement_client_mid_y";
	private static final String LAYERED_SCENE_CONTEXT_SCOPE_ATTRIBUTE =
		"layered_scene_context_scope";
	private static final String LAYERED_SCENE_CONTEXT_SEQUENCE_ATTRIBUTE =
		"layered_scene_context_sequence";
	private static final String LAYERED_SCENE_CONTEXT_PROTOCOL_ATTRIBUTE =
		"layered_scene_context_protocol";
	private static final String ATOMIC_SCENE_FENCE_SEQUENCE_ATTRIBUTE =
		"atomic_scene_fence_sequence";
	private static final String NATIVE_TERRAIN_CLIENT_RESIDENCY_ATTRIBUTE =
		"native_terrain_client_residency";
	private static final String NATIVE_TERRAIN_PENDING_READINESS_ATTRIBUTE =
		"native_terrain_pending_readiness";
	private static final String NATIVE_TERRAIN_ACCEPTED_READINESS_ATTRIBUTE =
		"native_terrain_accepted_readiness";
	private static final String NATIVE_TERRAIN_SELF_APPEARANCE_PENDING_ATTRIBUTE =
		"native_terrain_self_appearance_pending";
	private static final String NATIVE_TERRAIN_STAGE_SEQUENCE_ATTRIBUTE =
		"native_terrain_stage_sequence";
	private static final String NATIVE_TERRAIN_PENDING_STAGE_ATTRIBUTE =
		"native_terrain_pending_stage";
	private static final String NATIVE_TERRAIN_ACCEPTED_STAGE_ATTRIBUTE =
		"native_terrain_accepted_stage";
	private static final String NATIVE_TERRAIN_STAGE_TRANSACTION_ATTRIBUTE =
		"native_terrain_stage_transaction";
	private static final String NATIVE_TERRAIN_SYMMETRIC_VISUAL_CONTEXT_ATTRIBUTE =
		"native_terrain_symmetric_visual_context";
	private static final String NATIVE_TERRAIN_SYMMETRIC_STRUCTURE_CONTEXT_ATTRIBUTE =
		"native_terrain_symmetric_structure_context";
	private static final long WORLD_TIME_SYNC_INTERVAL_MILLIS = 15000L;
	private static final long WORLD_TIME_FAST_SYNC_INTERVAL_MILLIS = 250L;
	private static final int RECENT_VISIBILITY_SHADOW_LOG_LIMIT = 5;
	private static final String VISIBILITY_SHADOW_LOG_PREFIX = "VISIBILITY_SHADOW_RECENT";

	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private final Server server;
	private final NativeLayeredTerrainWireCache nativeTerrainWireCache =
		new NativeLayeredTerrainWireCache();
	private final Map<Long, CachedVisibilitySnapshot> visibilityTickSnapshotCache = new HashMap<>();
	private long visibilityTickSnapshotCacheTick = Long.MIN_VALUE;
	private int movementSnapshotSequence = 0;
	private final String[] recentVisibilityShadowLines = new String[RECENT_VISIBILITY_SHADOW_LOG_LIMIT];
	private int recentVisibilityShadowNext = 0;
	private int recentVisibilityShadowCount = 0;
	private int lastLoggedVisibilityShadowSignature = 0;

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
		sendUpdatePackets(player, false);
	}

	private void sendUpdatePackets(final Player player, final boolean allowTickSnapshotCache) {
		// TODO: Should be private
		try {
			if (player.isUsing233CompatibleClient()) {
				if (player.isChangingAppearance()) {
					recordUpdateAppearanceKeepalive(() -> sendAppearanceKeepalive(player));
				} else {
					sendNormalUpdatePackets(player, allowTickSnapshotCache);
				}
			} else {
				sendNormalUpdatePackets(player, allowTickSnapshotCache);
			}
		} catch (final Exception e) {
			LOGGER.error("Exception during GameStateUpdater sendUpdatePackets", e);
			player.unregister(UnregisterForcefulness.FORCED, "Exception while updating player " + player.getUsername());
		}
	}

	private void sendNormalUpdatePackets(final Player player, final boolean allowTickSnapshotCache) {
		if (!ensureLayeredSceneContext(player)) {
			return;
		}
		final VisibilitySnapshot packetVisibility = buildPacketVisibilitySnapshot(player, allowTickSnapshotCache);
		final Collection<Player> visiblePlayers = packetVisibility.getPlayers();
		final Collection<Npc> visibleNpcs = packetVisibility.getNpcs();
		final Collection<GameObject> visibleSceneryObjects = packetVisibility.getSceneryObjects();
		final Collection<GameObject> visibleWallObjects = packetVisibility.getWallObjects();
		final Collection<GroundItem> visibleGroundItems = packetVisibility.getGroundItems();
		recordVisibilityShadowSnapshot(player, packetVisibility, allowTickSnapshotCache);

		recordUpdatePlayers(() -> updatePlayers(player, visiblePlayers));
		recordUpdatePlayerAppearances(() -> updatePlayerAppearances(player));
		recordUpdateNpcs(() -> updateNpcs(player, visibleNpcs));
		recordUpdateNpcAppearances(() -> updateNpcAppearances(player));
		final boolean[] sceneryChanged = new boolean[1];
		final boolean[] wallsChanged = new boolean[1];
		final boolean[] groundItemsChanged = new boolean[1];
		final boolean skipStaticSceneScan = canSkipStaticSceneScan(player, packetVisibility);
		final boolean sendLegacyStaticScenePackets = shouldSendLegacyStaticScenePackets(player, skipStaticSceneScan);
		if (skipStaticSceneScan) {
			recordUpdateGameObjects(() -> {});
			recordUpdateWallObjects(() -> {});
		} else {
			recordUpdateGameObjects(() -> sceneryChanged[0] = updateGameObjects(
				player, visibleSceneryObjects, sendLegacyStaticScenePackets));
			recordUpdateWallObjects(() -> wallsChanged[0] = updateWallObjects(
				player, visibleWallObjects, sendLegacyStaticScenePackets));
			storeStaticSceneScanKey(player, packetVisibility);
		}
		recordUpdateGroundItems(() -> groundItemsChanged[0] = updateGroundItems(player, visibleGroundItems));
		sendAtomicSceneActivationFenceIfNeeded(player);
		sendSceneBaselineIfEnabled(player, sceneryChanged[0], wallsChanged[0], groundItemsChanged[0]);
		recordUpdateTimeouts(() -> updateTimeouts(player));
		sendWorldTimeIfNeeded(player);
	}

	private boolean ensureLayeredSceneContext(final Player player) {
		if (!getServer().getConfig().WANT_LAYERED_PROTOCOL_CLIENT_AUTHORITY) {
			return true;
		}
		if (!player.isUsingCustomClient()
			|| player.getClientVersion() != getServer().getConfig().CLIENT_VERSION) {
			player.unregister(
				UnregisterForcefulness.FORCED,
				"Layered protocol/client authority requires the matched custom client");
			return false;
		}

		updateCustomMovementClientRegion(player);
		final WorldLocation location = player.getWorldLocation();
		final com.openrsc.server.model.world.region.RegionManager regionManager =
			getServer().getWorld().getRegionManager();
		final Point expectedLegacy =
			regionManager.toRuntimeCompatibilityPoint(location);
		final NativeLayeredSceneTerrain nativeTerrain =
			nativeLayeredSceneTerrain(player, location);
		final String projectionId = nativeTerrain == null
			? regionManager.runtimeProjectionId(location)
			: NativeLayeredWorldPackage.RUNTIME_PROJECTION_ID;
		if (expectedLegacy.getX() != player.getX()
			|| expectedLegacy.getY() != player.getY()) {
			throw new IllegalStateException(
				"Layered scene context legacy receipt disagrees with Player location");
		}

		final LayeredProtocolSceneScope nextScope =
			LayeredProtocolSceneScope.from(
				location,
				projectionId,
				nativeTerrain == null ? "" : nativeTerrain.scopeIdentity());
		final LayeredProtocolSceneScope previousScope =
			player.getAttribute(LAYERED_SCENE_CONTEXT_SCOPE_ATTRIBUTE, null);
		if (nextScope.equals(previousScope)) {
			final boolean ready = nativeTerrain == null
				|| !nativeTerrain.requiresBlockingReadiness()
				|| hasAcceptedNativeTerrainReadiness(player);
			if (ready && nativeTerrain != null) {
				if (nativeTerrainSymmetricResidencyEnabled()) {
					maybeSendNativeTerrainSymmetricResidency(
						player, location, nativeTerrain);
				} else {
					maybeSendNativeTerrainStage(
						player, location, nativeTerrain);
				}
			}
			return ready;
		}
		if (previousScope == null) {
			clearNativeTerrainStage(player);
		}
		if (nativeTerrain != null
			&& !canActivateNativeTerrainStage(
				player, location, nativeTerrain)) {
			return false;
		}

		final Integer previousSequence = player.getAttribute(
			LAYERED_SCENE_CONTEXT_SEQUENCE_ATTRIBUTE, Integer.valueOf(0));
		final int sequence = Math.addExact(previousSequence.intValue(), 1);
		final LayeredSceneContextStruct context = new LayeredSceneContextStruct();
		context.protocolVersion =
			nativeTerrain != null
				? nativeTerrain.protocolVersion()
				: LayeredCompatibilityPointAdapter.SYNTHETIC_DEEP_FIXTURE_ID
					.equals(projectionId)
				? SYNTHETIC_DEEP_SCENE_CONTEXT_PROTOCOL_VERSION
				: LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION;
		context.sequence = sequence;
		context.serverTick = (int)(getServer().getCurrentTick() & 0x7FFFFFFF);
		context.worldSpace = location.getWorldSpace().getValue();
		context.projectionId = projectionId;
		context.logicalX = location.getCoordinate().getX();
		context.logicalY = location.getCoordinate().getY();
		context.logicalLevel = location.getCoordinate().getLevel();
		context.legacyX = expectedLegacy.getX();
		context.legacyY = expectedLegacy.getY();
		if (nativeTerrain != null) {
			nativeTerrain.populate(context);
		}
		if (!tryFinalizeAndSendPacketChecked(
				OpcodeOut.SEND_LAYERED_SCENE_CONTEXT, context, player)) {
			return false;
		}
		if (nativeTerrain != null) {
			nativeTerrain.commitResidency();
		}
		clearNativeTerrainStage(player);
		player.setAttribute(LAYERED_SCENE_CONTEXT_SCOPE_ATTRIBUTE, nextScope);
		player.setAttribute(LAYERED_SCENE_CONTEXT_SEQUENCE_ATTRIBUTE, sequence);
		player.setAttribute(
			LAYERED_SCENE_CONTEXT_PROTOCOL_ATTRIBUTE,
			Integer.valueOf(context.protocolVersion));
		if (nativeTerrain != null && nativeTerrain.requiresReadiness()) {
			player.setAttribute(
				NATIVE_TERRAIN_PENDING_READINESS_ATTRIBUTE,
				NativeLayeredTerrainReadiness.from(context));
			player.setAttribute(
				NATIVE_TERRAIN_SELF_APPEARANCE_PENDING_ATTRIBUTE,
				Boolean.TRUE);
			player.removeAttribute(
				NATIVE_TERRAIN_ACCEPTED_READINESS_ATTRIBUTE);
			/*
			 * Protocol-v8 activation may send Player/static-scene packets in
			 * this same update. Put the cheap visual-only radius-two stage on
			 * the wire first so the client can publish the full terrain field
			 * before it uncovers that atomic scene. The structural halo stays
			 * asynchronous and follows its normal acknowledgement.
			 */
			if (nativeTerrain.usesAtomicActivation()
				&& nativeTerrainSymmetricResidencyEnabled()) {
				maybeSendNativeTerrainSymmetricResidency(
					player, location, nativeTerrain);
			}
			return !nativeTerrain.requiresBlockingReadiness();
		}
		player.removeAttribute(NATIVE_TERRAIN_PENDING_READINESS_ATTRIBUTE);
		player.removeAttribute(NATIVE_TERRAIN_ACCEPTED_READINESS_ATTRIBUTE);
		return true;
	}

	public void acceptLayeredTerrainReady(
		final Player player,
		final LayeredTerrainReadyStruct receipt) {
		if (!getServer().getConfig().WANT_LAYERED_NATIVE_TERRAIN_READINESS
			|| !player.isUsingCustomClient()
			|| receipt == null
			|| receipt.protocolVersion
				!= LAYERED_TERRAIN_READY_PROTOCOL_VERSION) {
			return;
		}
		final NativeLayeredTerrainReadiness pending =
			player.getAttribute(
				NATIVE_TERRAIN_PENDING_READINESS_ATTRIBUTE, null);
		if (pending == null || !pending.matches(receipt)) {
			return;
		}
		player.setAttribute(
			NATIVE_TERRAIN_ACCEPTED_READINESS_ATTRIBUTE, pending);
	}

	private boolean hasAcceptedNativeTerrainReadiness(final Player player) {
		final NativeLayeredTerrainReadiness pending =
			player.getAttribute(
				NATIVE_TERRAIN_PENDING_READINESS_ATTRIBUTE, null);
		final NativeLayeredTerrainReadiness accepted =
			player.getAttribute(
				NATIVE_TERRAIN_ACCEPTED_READINESS_ATTRIBUTE, null);
		return pending != null && pending.equals(accepted);
	}

	/**
	 * Keeps the authoritative queued path at the context anchor until the
	 * first Player-position/appearance scene receipt has been emitted. The
	 * path remains queued and resumes on the following movement tick.
	 */
	public boolean isNativeTerrainActivationMovementHeld(
		final Player player) {
		return getServer().getConfig().WANT_LAYERED_NATIVE_TERRAIN_READINESS
			&& player.getAttribute(
				NATIVE_TERRAIN_SELF_APPEARANCE_PENDING_ATTRIBUTE,
				Boolean.FALSE).booleanValue();
	}

	public void acceptLayeredTerrainStageReady(
		final Player player,
		final LayeredTerrainStageReadyStruct receipt) {
		if ((!nativeTerrainPredictionEnabled()
				&& !nativeTerrainSymmetricResidencyEnabled())
			|| !player.isUsingCustomClient()
			|| receipt == null
			|| receipt.protocolVersion
					!= LAYERED_TERRAIN_STAGE_PROTOCOL_VERSION
				&& receipt.protocolVersion
					!= LAYERED_TERRAIN_SYMMETRIC_RESIDENCY_PROTOCOL_VERSION
				&& receipt.protocolVersion
					!= LAYERED_TERRAIN_SYMMETRIC_STRUCTURE_PROTOCOL_VERSION) {
			return;
		}
		final NativeLayeredTerrainStageReadiness pending =
			player.getAttribute(
				NATIVE_TERRAIN_PENDING_STAGE_ATTRIBUTE, null);
		if (pending == null || !pending.matches(receipt)) {
			return;
		}
		final NativeLayeredSceneTerrain stagedTerrain =
			player.getAttribute(
				NATIVE_TERRAIN_STAGE_TRANSACTION_ATTRIBUTE, null);
		if (stagedTerrain == null) {
			return;
		}
		stagedTerrain.commitResidency();
		player.removeAttribute(
			NATIVE_TERRAIN_STAGE_TRANSACTION_ATTRIBUTE);
		player.setAttribute(
			NATIVE_TERRAIN_ACCEPTED_STAGE_ATTRIBUTE, pending);
		if (receipt.protocolVersion
				== LAYERED_TERRAIN_SYMMETRIC_RESIDENCY_PROTOCOL_VERSION
			|| receipt.protocolVersion
				== LAYERED_TERRAIN_SYMMETRIC_STRUCTURE_PROTOCOL_VERSION) {
			player.setAttribute(
				receipt.protocolVersion
						== LAYERED_TERRAIN_SYMMETRIC_RESIDENCY_PROTOCOL_VERSION
					? NATIVE_TERRAIN_SYMMETRIC_VISUAL_CONTEXT_ATTRIBUTE
					: NATIVE_TERRAIN_SYMMETRIC_STRUCTURE_CONTEXT_ATTRIBUTE,
				Integer.valueOf(receipt.contextSequence));
			player.removeAttribute(NATIVE_TERRAIN_PENDING_STAGE_ATTRIBUTE);
			player.removeAttribute(NATIVE_TERRAIN_ACCEPTED_STAGE_ATTRIBUTE);
		}
	}

	private boolean canActivateNativeTerrainStage(
		final Player player,
		final WorldLocation location,
		final NativeLayeredSceneTerrain nativeTerrain) {
		if (!nativeTerrainPredictionEnabled()
			|| nativeTerrainSymmetricResidencyEnabled()) {
			return true;
		}
		final NativeLayeredTerrainStageReadiness pending =
			player.getAttribute(
				NATIVE_TERRAIN_PENDING_STAGE_ATTRIBUTE, null);
		if (pending == null
			|| !pending.matchesTarget(
				location.getWorldSpace().getValue(),
				location.getCoordinate().getLevel(),
				nativeTerrain.currentChunkX,
				nativeTerrain.currentChunkY,
				nativeTerrain.terrainPackage.getManifestSha256())) {
			return true;
		}
		final NativeLayeredTerrainStageReadiness accepted =
			player.getAttribute(
				NATIVE_TERRAIN_ACCEPTED_STAGE_ATTRIBUTE, null);
		return pending.equals(accepted);
	}

	private void maybeSendNativeTerrainStage(
		final Player player,
		final WorldLocation location,
		final NativeLayeredSceneTerrain activeTerrain) {
		if (!nativeTerrainPredictionEnabled()
			|| player.isTeleporting()
			|| player.getAttribute(
				NATIVE_TERRAIN_PENDING_STAGE_ATTRIBUTE, null) != null) {
			return;
		}
		final int[] targetCenter =
			predictNativeTerrainCenter(player, activeTerrain);
		if (targetCenter == null) {
			return;
		}
		final NativeLayeredSceneTerrain stagedTerrain =
			new NativeLayeredSceneTerrain(
				getServer(),
				nativeTerrainWireCache,
				activeTerrain.residency,
				activeTerrain.terrainPackage,
				location,
				targetCenter[0],
				targetCenter[1]);
		final Integer previousSequence = player.getAttribute(
			NATIVE_TERRAIN_STAGE_SEQUENCE_ATTRIBUTE,
			Integer.valueOf(0));
		final int sequence = Math.addExact(
			previousSequence.intValue(), 1);
		final LayeredTerrainStageStruct stage =
			new LayeredTerrainStageStruct();
		stage.protocolVersion = LAYERED_TERRAIN_STAGE_PROTOCOL_VERSION;
		stage.sequence = sequence;
		stage.contextSequence =
			requireLayeredSceneContextSequence(player);
		stage.serverTick =
			(int)(getServer().getCurrentTick() & 0x7FFFFFFF);
		stage.worldSpace = location.getWorldSpace().getValue();
		stage.logicalLevel = location.getCoordinate().getLevel();
		stagedTerrain.populate(stage);
		if (!tryFinalizeAndSendPacketChecked(
				OpcodeOut.SEND_LAYERED_TERRAIN_STAGE,
				stage,
				player)) {
			return;
		}
		player.setAttribute(
			NATIVE_TERRAIN_STAGE_SEQUENCE_ATTRIBUTE, sequence);
		player.setAttribute(
			NATIVE_TERRAIN_PENDING_STAGE_ATTRIBUTE,
			NativeLayeredTerrainStageReadiness.from(stage));
		player.setAttribute(
			NATIVE_TERRAIN_STAGE_TRANSACTION_ATTRIBUTE,
			stagedTerrain);
		player.removeAttribute(NATIVE_TERRAIN_ACCEPTED_STAGE_ATTRIBUTE);
	}

	private void maybeSendNativeTerrainSymmetricResidency(
		final Player player,
		final WorldLocation location,
		final NativeLayeredSceneTerrain activeTerrain) {
		if (!nativeTerrainSymmetricResidencyEnabled()
			|| player.getAttribute(
				NATIVE_TERRAIN_PENDING_STAGE_ATTRIBUTE, null) != null) {
			return;
		}
		final int contextSequence =
			requireLayeredSceneContextSequence(player);
		final Integer structuralContext = player.getAttribute(
			NATIVE_TERRAIN_SYMMETRIC_STRUCTURE_CONTEXT_ATTRIBUTE,
			Integer.valueOf(0));
		if (structuralContext.intValue() == contextSequence) {
			return;
		}
		final Integer visualContext = player.getAttribute(
			NATIVE_TERRAIN_SYMMETRIC_VISUAL_CONTEXT_ATTRIBUTE,
			Integer.valueOf(0));
		final boolean structural =
			visualContext.intValue() == contextSequence;
		final NativeLayeredSceneTerrain haloTerrain =
			new NativeLayeredSceneTerrain(
				getServer(),
				nativeTerrainWireCache,
				activeTerrain.residency,
				activeTerrain.terrainPackage,
				location,
				activeTerrain.currentChunkX,
				activeTerrain.currentChunkY);
		final Integer previousSequence = player.getAttribute(
			NATIVE_TERRAIN_STAGE_SEQUENCE_ATTRIBUTE,
			Integer.valueOf(0));
		final int sequence = Math.addExact(
			previousSequence.intValue(), 1);
		final LayeredTerrainStageStruct halo =
			new LayeredTerrainStageStruct();
		halo.protocolVersion = structural
			? LAYERED_TERRAIN_SYMMETRIC_STRUCTURE_PROTOCOL_VERSION
			: LAYERED_TERRAIN_SYMMETRIC_RESIDENCY_PROTOCOL_VERSION;
		halo.sequence = sequence;
		halo.contextSequence = contextSequence;
		halo.serverTick =
			(int)(getServer().getCurrentTick() & 0x7FFFFFFF);
		halo.worldSpace = location.getWorldSpace().getValue();
		halo.logicalLevel = location.getCoordinate().getLevel();
		if (structural) {
			haloTerrain.populateSymmetricStructure(halo);
		} else {
			haloTerrain.populate(
				halo,
				NATIVE_LAYERED_SYMMETRIC_RESIDENCY_RADIUS);
		}
		if (!tryFinalizeAndSendPacketChecked(
				OpcodeOut.SEND_LAYERED_TERRAIN_STAGE,
				halo,
				player)) {
			return;
		}
		player.setAttribute(
			NATIVE_TERRAIN_STAGE_SEQUENCE_ATTRIBUTE, sequence);
		player.setAttribute(
			NATIVE_TERRAIN_PENDING_STAGE_ATTRIBUTE,
			NativeLayeredTerrainStageReadiness.from(halo));
		player.setAttribute(
			NATIVE_TERRAIN_STAGE_TRANSACTION_ATTRIBUTE,
			haloTerrain);
		player.removeAttribute(NATIVE_TERRAIN_ACCEPTED_STAGE_ATTRIBUTE);
	}

	private int[] predictNativeTerrainCenter(
		final Player player,
		final NativeLayeredSceneTerrain activeTerrain) {
		if (player.getWalkingQueue().path == null
			|| player.getWalkingQueue().path.isEmpty()) {
			return null;
		}
		final int currentMidpointX =
			activeTerrain.currentChunkX * NATIVE_LAYERED_WIRE_CHUNK_SIZE;
		final int currentMidpointY =
			activeTerrain.currentChunkY * NATIVE_LAYERED_WIRE_CHUNK_SIZE;
		for (final Point waypoint
			: player.getWalkingQueue().path.getWaypoints()) {
			final int distance = Math.max(
				Math.abs(waypoint.getX() - player.getX()),
				Math.abs(waypoint.getY() - player.getY()));
			if (distance > NATIVE_LAYERED_PREDICTIVE_LEAD_TILES) {
				return null;
			}
			if (waypoint.getX()
					> currentMidpointX - CLIENT_LOCAL_REGION_RELOAD_RADIUS
				&& waypoint.getX()
					< currentMidpointX + CLIENT_LOCAL_REGION_RELOAD_RADIUS
				&& waypoint.getY()
					> currentMidpointY - CLIENT_LOCAL_REGION_RELOAD_RADIUS
				&& waypoint.getY()
					< currentMidpointY + CLIENT_LOCAL_REGION_RELOAD_RADIUS) {
				continue;
			}
			final int targetCenterX = Math.floorDiv(
				clientLocalMidpointForTile(
					waypoint.getX(), CLIENT_LOCAL_PLANE_WIDTH),
				NATIVE_LAYERED_WIRE_CHUNK_SIZE);
			final int targetCenterY = Math.floorDiv(
				clientLocalMidpointForTile(
					waypoint.getY(), CLIENT_LOCAL_PLANE_HEIGHT),
				NATIVE_LAYERED_WIRE_CHUNK_SIZE);
			final int deltaX =
				targetCenterX - activeTerrain.currentChunkX;
			final int deltaY =
				targetCenterY - activeTerrain.currentChunkY;
			if ((deltaX == 0 && deltaY == 0)
				|| Math.abs(deltaX) > 1
				|| Math.abs(deltaY) > 1) {
				return null;
			}
			return new int[] {targetCenterX, targetCenterY};
		}
		return null;
	}

	private boolean nativeTerrainPredictionEnabled() {
		return getServer().getConfig().WANT_LAYERED_NATIVE_TERRAIN_PREDICTION
			&& getServer().getConfig().WANT_LAYERED_NATIVE_TERRAIN_READINESS
			&& getServer().getConfig().WANT_LAYERED_NATIVE_TERRAIN_RESIDENCY;
	}

	private boolean nativeTerrainSymmetricResidencyEnabled() {
		return getServer().getConfig()
				.WANT_LAYERED_NATIVE_TERRAIN_SYMMETRIC_RESIDENCY
			&& nativeTerrainPredictionEnabled();
	}

	private static void clearNativeTerrainStage(final Player player) {
		player.removeAttribute(NATIVE_TERRAIN_PENDING_STAGE_ATTRIBUTE);
		player.removeAttribute(NATIVE_TERRAIN_ACCEPTED_STAGE_ATTRIBUTE);
		player.removeAttribute(NATIVE_TERRAIN_STAGE_TRANSACTION_ATTRIBUTE);
		player.removeAttribute(
			NATIVE_TERRAIN_SYMMETRIC_VISUAL_CONTEXT_ATTRIBUTE);
		player.removeAttribute(
			NATIVE_TERRAIN_SYMMETRIC_STRUCTURE_CONTEXT_ATTRIBUTE);
	}

	private NativeLayeredSceneTerrain nativeLayeredSceneTerrain(
		final Player player,
		final WorldLocation location) {
		if (!getServer().getConfig().WANT_LAYERED_NATIVE_TERRAIN_PACKAGE
			|| !getServer().getWorld().getRegionManager()
				.hasNativeLayeredTerrain(location)) {
			return null;
		}
		final NativeLayeredWorldPackage terrainPackage =
			getServer().getWorld().getRegionManager()
				.findNativeLayeredWorldPackage(location)
				.orElse(null);
		if (terrainPackage == null) {
			throw new IllegalStateException(
				"Native layered terrain gate has no loaded package");
		}
		final WorldMapSectorId sectorId = WorldMapSectorId.from(location);
		findNativeLayeredSceneSector(terrainPackage,sectorId)
			.orElseThrow(() -> new IllegalStateException(
				"Native layered scene has no terrain page at " + sectorId));
		final int centerSectorX = Math.floorDiv(
			currentClientLocalMidpoint(
				player,
				CUSTOM_MOVEMENT_CLIENT_MID_X_ATTRIBUTE,
				CLIENT_LOCAL_PLANE_WIDTH),
			NATIVE_LAYERED_WIRE_CHUNK_SIZE);
		final int centerSectorY = Math.floorDiv(
			currentClientLocalMidpoint(
				player,
				CUSTOM_MOVEMENT_CLIENT_MID_Y_ATTRIBUTE,
				CLIENT_LOCAL_PLANE_HEIGHT),
			NATIVE_LAYERED_WIRE_CHUNK_SIZE);
		if (Math.abs(sectorId.getSectorX() - centerSectorX)
				> NATIVE_LAYERED_CHUNK_RADIUS
			|| Math.abs(sectorId.getSectorY() - centerSectorY)
				> NATIVE_LAYERED_CHUNK_RADIUS) {
			throw new IllegalStateException(
				"Native client-sector window does not cover the player");
		}
		NativeLayeredTerrainClientResidency residency = null;
		if (getServer().getConfig()
				.WANT_LAYERED_NATIVE_TERRAIN_RESIDENCY) {
			residency = player.getAttribute(
				NATIVE_TERRAIN_CLIENT_RESIDENCY_ATTRIBUTE, null);
			if (residency == null) {
				residency = new NativeLayeredTerrainClientResidency();
				player.setAttribute(
					NATIVE_TERRAIN_CLIENT_RESIDENCY_ATTRIBUTE,
					residency);
			}
		}
		return new NativeLayeredSceneTerrain(
			getServer(),
			nativeTerrainWireCache,
			residency,
			terrainPackage,
			location,
			centerSectorX,
			centerSectorY);
	}

	private Optional<NativeLayeredTerrainSector> findNativeLayeredSceneSector(
		final NativeLayeredWorldPackage terrainPackage,
		final WorldMapSectorId sectorId){
		if(getServer().getConfig().WORLD_BUILDER_MODE){
			return getServer().getWorldEditorSessions()
				.findNativeTerrainSector(terrainPackage,sectorId);
		}
		return terrainPackage.findSector(sectorId);
	}

	private int requireLayeredSceneContextSequence(final Player player) {
		final Integer sequence = player.getAttribute(
			LAYERED_SCENE_CONTEXT_SEQUENCE_ATTRIBUTE, null);
		if (sequence == null || sequence.intValue() <= 0) {
			throw new IllegalStateException(
				"Layered protocol packet has no established scene context");
		}
		return sequence.intValue();
	}

	private VisibilitySnapshot buildPacketVisibilitySnapshot(final Player player, final boolean allowTickSnapshotCache) {
		return buildVisibilitySnapshot(
			player,
			useVisibilitySnapshotInput(player) ? VisibilitySnapshotMode.SNAPSHOT : VisibilitySnapshotMode.LEGACY,
			allowTickSnapshotCache,
			true);
	}

	private VisibilitySnapshot buildVisibilitySnapshot(
		final Player player,
		final VisibilitySnapshotMode mode,
		final boolean allowTickSnapshotCache,
		final boolean recordPacketMetrics) {
		if (allowTickSnapshotCache && getServer().getConfig().WANT_SYNC_VISIBILITY_TICK_CACHE) {
			return buildTickCachedVisibilitySnapshot(player, mode, recordPacketMetrics);
		}

		final long visibilitySnapshotStart = System.nanoTime();
		final VisibilitySnapshot snapshot = buildUncachedVisibilitySnapshot(player, mode);
		if (recordPacketMetrics) {
			recordVisibilitySnapshotMetrics(snapshot, System.nanoTime() - visibilitySnapshotStart);
		}
		return snapshot;
	}

	private VisibilitySnapshot buildTickCachedVisibilitySnapshot(
		final Player player,
		final VisibilitySnapshotMode mode,
		final boolean recordPacketMetrics) {
		final long currentTick = getServer().getCurrentTick();
		if (visibilityTickSnapshotCacheTick != currentTick) {
			visibilityTickSnapshotCache.clear();
			visibilityTickSnapshotCacheTick = currentTick;
		}

		final long cacheKey = visibilityTickSnapshotCacheKey(player, mode);
		final CachedVisibilitySnapshot cached = visibilityTickSnapshotCache.get(cacheKey);
		if (cached != null && cached.matches(player, mode, currentTick)) {
			getServer().recordVisibilityTickSnapshotCacheAccess(true);
			if (recordPacketMetrics) {
				recordVisibilitySnapshotMetrics(cached.snapshot, 0L);
			}
			return cached.snapshot;
		}

		getServer().recordVisibilityTickSnapshotCacheAccess(false);
		final long visibilitySnapshotStart = System.nanoTime();
		final VisibilitySnapshot snapshot = buildUncachedVisibilitySnapshot(player, mode);
		if (recordPacketMetrics) {
			recordVisibilitySnapshotMetrics(snapshot, System.nanoTime() - visibilitySnapshotStart);
		}
		visibilityTickSnapshotCache.put(cacheKey, new CachedVisibilitySnapshot(player, mode, currentTick, snapshot));
		return snapshot;
	}

	private long visibilityTickSnapshotCacheKey(final Player player, final VisibilitySnapshotMode mode) {
		return (((long)player.getIndex()) << 2) | mode.ordinal();
	}

	private VisibilitySnapshot buildUncachedVisibilitySnapshot(final Player player, final VisibilitySnapshotMode mode) {
		return mode == VisibilitySnapshotMode.SNAPSHOT
			? getServer().getWorld().getRegionManager().buildVisibilitySnapshot(player)
			: buildLegacyVisibilitySnapshot(player);
	}

	private VisibilitySnapshot buildLegacyVisibilitySnapshot(final Player player) {
		return new VisibilitySnapshot(
			player.getViewArea().getPlayersInView(),
			player.getViewArea().getNpcsInView(),
			player.getViewArea().getGameObjectsInView(),
			player.getViewArea().getItemsInView(),
			0,
			0);
	}

	private boolean useVisibilitySnapshotInput(final Player player) {
		return player.isUsingCustomClient() && getServer().getConfig().WANT_SYNC_VISIBILITY_SNAPSHOT_INPUT;
	}

	private boolean canSkipStaticSceneScan(final Player player, final VisibilitySnapshot packetVisibility) {
		if (!player.isUsingCustomClient() || !getServer().getConfig().WANT_SYNC_SCENE_BASELINE) {
			return false;
		}
		final SceneBaselineSummary summary = player.getAttribute(SCENE_BASELINE_SUMMARY_ATTRIBUTE);
		if (summary == null || !summary.hasSentCompleteStaticBaseline()) {
			return false;
		}
		final Object scanKey = staticSceneScanKey(player, packetVisibility);
		if (scanKey == null) {
			return false;
		}
		final Object previousScanKey =
			player.getAttribute(STATIC_SCENE_SCAN_KEY_ATTRIBUTE);
		return scanKey.equals(previousScanKey);
	}

	private void storeStaticSceneScanKey(final Player player, final VisibilitySnapshot packetVisibility) {
		final Object scanKey = staticSceneScanKey(player, packetVisibility);
		if (scanKey != null) {
			player.setAttribute(STATIC_SCENE_SCAN_KEY_ATTRIBUTE, scanKey);
		}
	}

	private Object staticSceneScanKey(
		final Player player,
		final VisibilitySnapshot packetVisibility) {
		if (packetVisibility.getObjectSnapshotVersion() <= 0L) {
			return null;
		}
		LayeredSpatialWindowKey layeredKey =
			packetVisibility.getLayeredObjectSnapshotKey();
		if (layeredKey != null) {
			return new LayeredStaticSceneScanKey(
				layeredKey,
				packetVisibility.getObjectSnapshotVersion(),
				player.getWorldLocation(),
				getServer().getConfig().OBJECT_VIEW_DISTANCE);
		}
		long hash = packetVisibility.getObjectSnapshotKey();
		hash = hash * 31 + packetVisibility.getObjectSnapshotVersion();
		hash = hash * 31 + player.getX();
		hash = hash * 31 + player.getY();
		hash = hash * 31 + getServer().getConfig().OBJECT_VIEW_DISTANCE;
		return Long.valueOf(hash == 0L ? 1L : hash);
	}

	private void sendSceneBaselineIfEnabled(
		final Player player,
		final boolean sceneryChanged,
		final boolean wallsChanged,
		final boolean groundItemsChanged) {
		if (!player.isUsingCustomClient() || !getServer().getConfig().WANT_SYNC_SCENE_BASELINE) {
			return;
		}

		final SceneBaselineSummary previous = player.getAttribute(SCENE_BASELINE_SUMMARY_ATTRIBUTE);
		final SceneBaselineSummary current = buildSceneBaselineSummary(
			player, previous, sceneryChanged, wallsChanged, groundItemsChanged);
		int sentPages = 0;
		while (sentPages < SCENE_BASELINE_PAGE_BURST_LIMIT) {
			final SceneBaselinePage page = buildNextSceneBaselinePage(player, current);
			if (page.isEmpty()) {
				break;
			}

			sendSceneBaselinePacket(player, current, page);
			sentPages++;
		}

		if (sentPages == 0 && previous != null && current.sameStaticPayload(previous)) {
			return;
		}

		if (sentPages == 0) {
			sendSceneBaselinePacket(player, current, SceneBaselinePage.empty());
		}

		player.setAttribute(SCENE_BASELINE_SUMMARY_ATTRIBUTE, current);
	}

	private void sendAtomicSceneActivationFenceIfNeeded(
		final Player player) {
		if (!player.isUsingCustomClient()
			|| !getServer().getConfig().WANT_SYNC_SCENE_BASELINE) {
			return;
		}
		final Integer contextProtocol = player.getAttribute(
			LAYERED_SCENE_CONTEXT_PROTOCOL_ATTRIBUTE,
			Integer.valueOf(0));
		if (contextProtocol.intValue()
				!= ATOMIC_NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION) {
			return;
		}
		final int contextSequence =
			requireLayeredSceneContextSequence(player);
		final Integer sentSequence = player.getAttribute(
			ATOMIC_SCENE_FENCE_SEQUENCE_ATTRIBUTE,
			Integer.valueOf(0));
		if (sentSequence.intValue() >= contextSequence) {
			return;
		}

		final SceneBaselineStruct fence = new SceneBaselineStruct();
		fence.protocolVersion = ATOMIC_SCENE_FENCE_PROTOCOL_VERSION;
		fence.locationContextSequence = contextSequence;
		fence.serverTick =
			(int)(getServer().getCurrentTick() & 0x7FFFFFFF);
		fence.localX = player.getX();
		fence.localY = player.getY();
		fence.scenery = player.getLocalGameObjects().size();
		fence.walls = player.getLocalWallObjects().size();
		fence.groundItems = player.getLocalGroundItems().size();
		fence.objectViewDistance =
			getServer().getConfig().OBJECT_VIEW_DISTANCE;
		fence.sceneryHash = summarizeWireSceneGameObjects(
			player, player.getLocalGameObjects(), true);
		fence.wallsHash = summarizeWireSceneGameObjects(
			player, player.getLocalWallObjects(), false);
		fence.pageCategory = SCENE_BASELINE_PAGE_ATOMIC_FENCE;
		if (tryFinalizeAndSendPacketChecked(
				OpcodeOut.SEND_SCENE_BASELINE, fence, player)) {
			player.setAttribute(
				ATOMIC_SCENE_FENCE_SEQUENCE_ATTRIBUTE,
				Integer.valueOf(contextSequence));
		}
	}

	private void sendSceneBaselinePacket(
		final Player player,
		final SceneBaselineSummary current,
		final SceneBaselinePage page) {
		final SceneBaselineStruct baseline = current.toStruct();
		page.applyTo(baseline);
		tryFinalizeAndSendPacket(OpcodeOut.SEND_SCENE_BASELINE, baseline, player);
		getServer().addSceneBaselineMetrics(
			page.recordCount(), page.payloadBytes(current.protocolVersion));
	}

	private SceneBaselineSummary buildSceneBaselineSummary(
		final Player player,
		final SceneBaselineSummary previous,
		final boolean sceneryChanged,
		final boolean wallsChanged,
		final boolean groundItemsChanged) {
		final SceneBaselineSummary summary = new SceneBaselineSummary();
		final boolean layeredProtocol =
			getServer().getConfig().WANT_LAYERED_PROTOCOL_CLIENT_AUTHORITY;
		summary.protocolVersion = layeredProtocol
			? LAYERED_SCENE_BASELINE_PROTOCOL_VERSION
			: SCENE_BASELINE_PROTOCOL_VERSION;
		summary.locationContextSequence = layeredProtocol
			? requireLayeredSceneContextSequence(player)
			: 0;
		summary.serverTick = (int)(getServer().getCurrentTick() & 0x7FFFFFFF);
		summary.localX = player.getX();
		summary.localY = player.getY();
		summary.scenery = player.getLocalGameObjects().size();
		summary.walls = player.getLocalWallObjects().size();
		summary.groundItems = player.getLocalGroundItems().size();
		summary.objectViewDistance = getServer().getConfig().OBJECT_VIEW_DISTANCE;
		summary.sceneryHash = previous != null && !sceneryChanged && previous.scenery == summary.scenery
			? previous.sceneryHash
			: summarizeSceneGameObjects(player.getLocalGameObjects());
		summary.wallsHash = previous != null && !wallsChanged && previous.walls == summary.walls
			? previous.wallsHash
			: summarizeSceneGameObjects(player.getLocalWallObjects());
		summary.groundItemsHash = previous != null && !groundItemsChanged && previous.groundItems == summary.groundItems
			? previous.groundItemsHash
			: summarizeSceneGroundItems(player.getLocalGroundItems());
		final boolean sameContext = previous != null
			&& previous.protocolVersion == summary.protocolVersion
			&& previous.locationContextSequence
				== summary.locationContextSequence;
		summary.sceneryPageCursor = sameContext
			&& previous.scenery == summary.scenery
			&& previous.sceneryHash == summary.sceneryHash
			? previous.sceneryPageCursor
			: 0;
		summary.wallsPageCursor = sameContext
			&& previous.walls == summary.walls
			&& previous.wallsHash == summary.wallsHash
			? previous.wallsPageCursor
			: 0;
		return summary;
	}

	private SceneBaselinePage buildNextSceneBaselinePage(final Player player, final SceneBaselineSummary summary) {
		final int sceneryPageTotal = pageTotal(player.getLocalGameObjects().size());
		if (summary.sceneryPageCursor < sceneryPageTotal) {
			final int pageIndex = summary.sceneryPageCursor++;
			return buildSceneBaselineObjectPage(
				SCENE_BASELINE_PAGE_SCENERY,
				pageIndex,
				sceneryPageTotal,
				player.getLocalGameObjects());
		}

		final int wallPageTotal = pageTotal(player.getLocalWallObjects().size());
		if (summary.wallsPageCursor < wallPageTotal) {
			final int pageIndex = summary.wallsPageCursor++;
			return buildSceneBaselineObjectPage(
				SCENE_BASELINE_PAGE_WALLS,
				pageIndex,
				wallPageTotal,
				player.getLocalWallObjects());
		}

		return SceneBaselinePage.empty();
	}

	private int pageTotal(final int recordCount) {
		return (recordCount + SCENE_BASELINE_PAGE_SIZE - 1) / SCENE_BASELINE_PAGE_SIZE;
	}

	private SceneBaselinePage buildSceneBaselineObjectPage(
		final int category,
		final int pageIndex,
		final int pageTotal,
		final Collection<GameObject> gameObjects) {
		final SceneBaselinePage page = new SceneBaselinePage(category, pageIndex, pageTotal);
		final int start = pageIndex * SCENE_BASELINE_PAGE_SIZE;
		final int end = start + SCENE_BASELINE_PAGE_SIZE;
		int index = 0;
		for (final GameObject gameObject : gameObjects) {
			if (index >= end) {
				break;
			}
			if (index >= start) {
				page.objectRecords.add(new SceneBaselineStruct.ObjectRecord(
					gameObject.getID(),
					gameObject.getX(),
					gameObject.getY(),
					gameObject.getDirection(),
					gameObject.getType()));
			}
			index++;
		}
		return page;
	}

	private int summarizeSceneGameObjects(final Collection<GameObject> gameObjects) {
		int summary = 0;
		for (final GameObject gameObject : gameObjects) {
			summary = addSceneIdentity(summary, sceneIdentity(
				gameObject.getID(),
				(gameObject.getType() << 8) | (gameObject.getDirection() & 0xFF),
				gameObject.getX(),
				gameObject.getY(),
				gameObject.getLoc().getId()));
		}
		return summary;
	}

	private int summarizeWireSceneGameObjects(
		final Player player,
		final Collection<GameObject> gameObjects,
		final boolean scenery) {
		int summary = 0;
		for (final GameObject gameObject : gameObjects) {
			final int id = scenery
				? retroRockConverter(player, gameObject.getLoc())
				: gameObject.getID();
			summary = addSceneIdentity(summary, sceneIdentity(
				id,
				(gameObject.getType() << 8)
					| (gameObject.getDirection() & 0xFF),
				gameObject.getX(),
				gameObject.getY(),
				0));
		}
		return summary;
	}

	private int summarizeSceneGroundItems(final Collection<GroundItem> groundItems) {
		int summary = 0;
		for (final GroundItem groundItem : groundItems) {
			final long ownerHash = groundItem.getOwnerUsernameHash();
			summary = addSceneIdentity(summary, sceneIdentity(
				groundItem.getID(),
				groundItem.getAmount(),
				groundItem.getX(),
				groundItem.getY(),
				(groundItem.getNoted() ? 1 : 0) ^ (int)(ownerHash ^ (ownerHash >>> 32))));
		}
		return summary;
	}

	private int sceneIdentity(final int a, final int b, final int c, final int d, final int e) {
		int hash = 0x811C9DC5;
		hash = mixSceneIdentity(hash, a);
		hash = mixSceneIdentity(hash, b);
		hash = mixSceneIdentity(hash, c);
		hash = mixSceneIdentity(hash, d);
		hash = mixSceneIdentity(hash, e);
		return hash;
	}

	private int mixSceneIdentity(final int hash, final int value) {
		return (hash ^ value) * 0x01000193;
	}

	private int addSceneIdentity(final int summary, final int identity) {
		return summary + identity + Integer.rotateLeft(identity, 16);
	}

	private static final class SceneBaselineSummary {
		private int protocolVersion;
		private int locationContextSequence;
		private int serverTick;
		private int localX;
		private int localY;
		private int scenery;
		private int walls;
		private int groundItems;
		private int objectViewDistance;
		private int sceneryHash;
		private int wallsHash;
		private int groundItemsHash;
		private int sceneryPageCursor;
		private int wallsPageCursor;

		private boolean sameStaticPayload(final SceneBaselineSummary other) {
			return protocolVersion == other.protocolVersion
				&& locationContextSequence == other.locationContextSequence
				&& scenery == other.scenery
				&& walls == other.walls
				&& groundItems == other.groundItems
				&& objectViewDistance == other.objectViewDistance
				&& sceneryHash == other.sceneryHash
				&& wallsHash == other.wallsHash
				&& groundItemsHash == other.groundItemsHash;
		}

		private boolean hasSentCompleteStaticBaseline() {
			return sceneryPageCursor >= pageTotalFor(scenery)
				&& wallsPageCursor >= pageTotalFor(walls);
		}

		private static int pageTotalFor(final int recordCount) {
			return (recordCount + SCENE_BASELINE_PAGE_SIZE - 1) / SCENE_BASELINE_PAGE_SIZE;
		}

		private SceneBaselineStruct toStruct() {
			final SceneBaselineStruct struct = new SceneBaselineStruct();
			struct.protocolVersion = protocolVersion;
			struct.locationContextSequence = locationContextSequence;
			struct.serverTick = serverTick;
			struct.localX = localX;
			struct.localY = localY;
			struct.scenery = scenery;
			struct.walls = walls;
			struct.groundItems = groundItems;
			struct.objectViewDistance = objectViewDistance;
			struct.sceneryHash = sceneryHash;
			struct.wallsHash = wallsHash;
			struct.groundItemsHash = groundItemsHash;
			return struct;
		}
	}

	private static final class SceneBaselinePage {
		private final int category;
		private final int pageIndex;
		private final int pageTotal;
		private final List<SceneBaselineStruct.ObjectRecord> objectRecords = new ArrayList<>();

		private SceneBaselinePage(final int category, final int pageIndex, final int pageTotal) {
			this.category = category;
			this.pageIndex = pageIndex;
			this.pageTotal = pageTotal;
		}

		private static SceneBaselinePage empty() {
			return new SceneBaselinePage(SCENE_BASELINE_PAGE_NONE, 0, 0);
		}

		private boolean isEmpty() {
			return category == SCENE_BASELINE_PAGE_NONE;
		}

		private int recordCount() {
			return objectRecords.size();
		}

		private int payloadBytes(final int protocolVersion) {
			return SCENE_BASELINE_FIXED_PAYLOAD_BYTES
				+ (protocolVersion >= LAYERED_SCENE_BASELINE_PROTOCOL_VERSION
					? LAYERED_CONTEXT_SEQUENCE_BYTES
					: 0)
				+ recordCount() * SCENE_BASELINE_OBJECT_RECORD_BYTES;
		}

		private void applyTo(final SceneBaselineStruct struct) {
			struct.pageCategory = category;
			struct.pageIndex = pageIndex;
			struct.pageTotal = pageTotal;
			struct.objectRecords.addAll(objectRecords);
		}
	}

	private void recordVisibilitySnapshotMetrics(final VisibilitySnapshot packetVisibility, final long duration) {
		getServer().addVisibilitySnapshotMetrics(
			packetVisibility.getPlayers().size(),
			packetVisibility.getNpcs().size(),
			packetVisibility.getSceneryCount(),
			packetVisibility.getWallObjectCount(),
			packetVisibility.getGroundItems().size(),
			duration);
	}

	private void recordVisibilityShadowSnapshot(
		final Player player,
		final VisibilitySnapshot packetVisibility,
		final boolean allowTickSnapshotCache) {
		if (!getServer().getConfig().WANT_SYNC_VISIBILITY_SHADOW) {
			return;
		}

		final long start = System.nanoTime();
		final VisibilitySnapshot comparisonSnapshot = buildVisibilitySnapshot(
			player,
			useVisibilitySnapshotInput(player) ? VisibilitySnapshotMode.LEGACY : VisibilitySnapshotMode.SNAPSHOT,
			allowTickSnapshotCache,
			false);
		final boolean playersMatch = sameIdentityCollection(packetVisibility.getPlayers(), comparisonSnapshot.getPlayers());
		final boolean npcsMatch = sameIdentityCollection(packetVisibility.getNpcs(), comparisonSnapshot.getNpcs());
		final boolean gameObjectsMatch = sameIdentityCollection(packetVisibility.getGameObjects(), comparisonSnapshot.getGameObjects());
		final boolean groundItemsMatch = sameIdentityCollection(packetVisibility.getGroundItems(), comparisonSnapshot.getGroundItems());
		recordVisibilityShadowDiagnostics(
			player,
			packetVisibility,
			comparisonSnapshot,
			useVisibilitySnapshotInput(player),
			allowTickSnapshotCache,
			playersMatch,
			npcsMatch,
			gameObjectsMatch,
			groundItemsMatch);
		getServer().addVisibilityShadowMetrics(
			System.nanoTime() - start,
			playersMatch,
			npcsMatch,
			gameObjectsMatch,
			groundItemsMatch,
			Math.max(packetVisibility.getMobRegionCount(), comparisonSnapshot.getMobRegionCount()),
			Math.max(packetVisibility.getObjectRegionCount(), comparisonSnapshot.getObjectRegionCount()));
	}

	private void recordVisibilityShadowDiagnostics(
		final Player player,
		final VisibilitySnapshot packetVisibility,
		final VisibilitySnapshot comparisonSnapshot,
		final boolean snapshotInput,
		final boolean allowTickSnapshotCache,
		final boolean playersMatch,
		final boolean npcsMatch,
		final boolean gameObjectsMatch,
		final boolean groundItemsMatch) {
		final String line = buildVisibilityShadowLine(
			player,
			packetVisibility,
			comparisonSnapshot,
			snapshotInput,
			allowTickSnapshotCache,
			playersMatch,
			npcsMatch,
			gameObjectsMatch,
			groundItemsMatch);
		rememberVisibilityShadowLine(line);
		if (playersMatch && npcsMatch && gameObjectsMatch && groundItemsMatch) {
			return;
		}

		final int issueSignature = visibilityShadowIssueSignature(
			packetVisibility,
			comparisonSnapshot,
			snapshotInput,
			allowTickSnapshotCache,
			playersMatch,
			npcsMatch,
			gameObjectsMatch,
			groundItemsMatch);
		if (issueSignature != lastLoggedVisibilityShadowSignature) {
			lastLoggedVisibilityShadowSignature = issueSignature;
			logRecentVisibilityShadowLines();
		}
	}

	private String buildVisibilityShadowLine(
		final Player player,
		final VisibilitySnapshot packetVisibility,
		final VisibilitySnapshot comparisonSnapshot,
		final boolean snapshotInput,
		final boolean allowTickSnapshotCache,
		final boolean playersMatch,
		final boolean npcsMatch,
		final boolean gameObjectsMatch,
		final boolean groundItemsMatch) {
		return "tick " + getServer().getCurrentTick()
			+ " player " + player.getUsername() + "#" + player.getIndex()
			+ " pos " + player.getX() + "," + player.getY()
			+ " packetMode " + (snapshotInput ? "snapshot" : "legacy")
			+ " compareMode " + (snapshotInput ? "legacy" : "snapshot")
			+ " tickCache " + allowTickSnapshotCache
			+ " packet p/n/o/g " + visibilityCounts(packetVisibility)
			+ " compare p/n/o/g " + visibilityCounts(comparisonSnapshot)
			+ " match p/n/o/g " + boolFlag(playersMatch) + "/" + boolFlag(npcsMatch)
				+ "/" + boolFlag(gameObjectsMatch) + "/" + boolFlag(groundItemsMatch)
			+ " regions mob/object "
				+ packetVisibility.getMobRegionCount() + "/" + comparisonSnapshot.getMobRegionCount()
				+ "/" + packetVisibility.getObjectRegionCount() + "/" + comparisonSnapshot.getObjectRegionCount();
	}

	private String visibilityCounts(final VisibilitySnapshot snapshot) {
		return snapshot.getPlayers().size()
			+ "/" + snapshot.getNpcs().size()
			+ "/" + snapshot.getGameObjects().size()
			+ "/" + snapshot.getGroundItems().size();
	}

	private String boolFlag(final boolean value) {
		return value ? "ok" : "bad";
	}

	private int visibilityShadowIssueSignature(
		final VisibilitySnapshot packetVisibility,
		final VisibilitySnapshot comparisonSnapshot,
		final boolean snapshotInput,
		final boolean allowTickSnapshotCache,
		final boolean playersMatch,
		final boolean npcsMatch,
		final boolean gameObjectsMatch,
		final boolean groundItemsMatch) {
		int hash = snapshotInput ? 1 : 0;
		hash = hash * 31 + (allowTickSnapshotCache ? 1 : 0);
		hash = hash * 31 + (playersMatch ? 1 : 0);
		hash = hash * 31 + (npcsMatch ? 1 : 0);
		hash = hash * 31 + (gameObjectsMatch ? 1 : 0);
		hash = hash * 31 + (groundItemsMatch ? 1 : 0);
		hash = hash * 31 + packetVisibility.getPlayers().size();
		hash = hash * 31 + comparisonSnapshot.getPlayers().size();
		hash = hash * 31 + packetVisibility.getNpcs().size();
		hash = hash * 31 + comparisonSnapshot.getNpcs().size();
		hash = hash * 31 + packetVisibility.getGameObjects().size();
		hash = hash * 31 + comparisonSnapshot.getGameObjects().size();
		hash = hash * 31 + packetVisibility.getGroundItems().size();
		hash = hash * 31 + comparisonSnapshot.getGroundItems().size();
		return hash;
	}

	private void rememberVisibilityShadowLine(final String line) {
		recentVisibilityShadowLines[recentVisibilityShadowNext] = line;
		recentVisibilityShadowNext = (recentVisibilityShadowNext + 1) % RECENT_VISIBILITY_SHADOW_LOG_LIMIT;
		if (recentVisibilityShadowCount < RECENT_VISIBILITY_SHADOW_LOG_LIMIT) {
			recentVisibilityShadowCount++;
		}
	}

	private void logRecentVisibilityShadowLines() {
		LOGGER.warn("{} latest mismatch; last {} visibility shadow snapshots:",
			VISIBILITY_SHADOW_LOG_PREFIX, recentVisibilityShadowCount);
		for (int i = 0; i < recentVisibilityShadowCount; i++) {
			int index = recentVisibilityShadowNext - recentVisibilityShadowCount + i;
			if (index < 0) {
				index += RECENT_VISIBILITY_SHADOW_LOG_LIMIT;
			}
			LOGGER.warn("{} {}", VISIBILITY_SHADOW_LOG_PREFIX, recentVisibilityShadowLines[index]);
		}
	}

	private boolean sameIdentityCollection(final Collection<?> first, final Collection<?> second) {
		if (first.size() != second.size()) {
			return false;
		}
		final Set<Object> secondIdentities = Collections.newSetFromMap(new IdentityHashMap<>());
		secondIdentities.addAll(second);
		for (final Object item : first) {
			if (!secondIdentities.contains(item)) {
				return false;
			}
		}
		return true;
	}

	private static final class CachedVisibilitySnapshot {
		private final int x;
		private final int y;
		private final WorldLocation worldLocation;
		private final long tick;
		private final VisibilitySnapshotMode mode;
		private final VisibilitySnapshot snapshot;

		private CachedVisibilitySnapshot(
			final Player player,
			final VisibilitySnapshotMode mode,
			final long tick,
			final VisibilitySnapshot snapshot) {
			this.x = player.getX();
			this.y = player.getY();
			this.worldLocation = player.getWorldLocation();
			this.tick = tick;
			this.mode = mode;
			this.snapshot = snapshot;
		}

		private boolean matches(final Player player, final VisibilitySnapshotMode mode, final long tick) {
			return this.tick == tick
				&& this.mode == mode
				&& this.x == player.getX()
				&& this.y == player.getY()
				&& this.worldLocation.equals(player.getWorldLocation());
		}
	}

	private static final class LayeredStaticSceneScanKey {
		private final LayeredSpatialWindowKey windowKey;
		private final long version;
		private final WorldLocation playerLocation;
		private final int objectViewDistance;

		private LayeredStaticSceneScanKey(
			final LayeredSpatialWindowKey windowKey,
			final long version,
			final WorldLocation playerLocation,
			final int objectViewDistance) {
			this.windowKey = Objects.requireNonNull(windowKey, "windowKey");
			this.version = version;
			this.playerLocation = Objects.requireNonNull(
				playerLocation, "playerLocation");
			this.objectViewDistance = objectViewDistance;
		}

		@Override
		public boolean equals(final Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof LayeredStaticSceneScanKey)) {
				return false;
			}
			LayeredStaticSceneScanKey key =
				(LayeredStaticSceneScanKey) other;
			return version == key.version
				&& objectViewDistance == key.objectViewDistance
				&& windowKey.equals(key.windowKey)
				&& playerLocation.equals(key.playerLocation);
		}

		@Override
		public int hashCode() {
			int result = windowKey.hashCode();
			result = 31 * result
				+ (int) (version ^ (version >>> 32));
			result = 31 * result + playerLocation.hashCode();
			result = 31 * result + objectViewDistance;
			return result;
		}
	}

	private static final class NativeLayeredSceneTerrain {
		private final Server server;
		private final NativeLayeredTerrainWireCache wireCache;
		private final NativeLayeredTerrainClientResidency residency;
		private final NativeLayeredWorldPackage terrainPackage;
		private final WorldLocation location;
		private final int currentChunkX;
		private final int currentChunkY;
		private NativeLayeredTerrainClientResidency.Transaction
			residencyTransaction;

		private NativeLayeredSceneTerrain(
			final Server server,
			final NativeLayeredTerrainWireCache wireCache,
			final NativeLayeredTerrainClientResidency residency,
			final NativeLayeredWorldPackage terrainPackage,
			final WorldLocation location,
			final int currentChunkX,
			final int currentChunkY) {
			this.server = Objects.requireNonNull(server, "server");
			this.wireCache = Objects.requireNonNull(wireCache, "wireCache");
			this.residency = residency;
			this.terrainPackage = Objects.requireNonNull(
				terrainPackage, "terrainPackage");
			this.location = Objects.requireNonNull(location, "location");
			this.currentChunkX = currentChunkX;
			this.currentChunkY = currentChunkY;
		}

		private String scopeIdentity() {
			String identity=terrainPackage.getPackageId()
				+ "@" + terrainPackage.getPackageVersion()
				+ ":" + terrainPackage.getManifestSha256()
				+ ":" + location.getWorldSpace().getValue()
				+ ":" + location.getCoordinate().getLevel()
				+ ":center-" + currentChunkX + "," + currentChunkY
				+ ":chunk-" + NATIVE_LAYERED_WIRE_CHUNK_SIZE;
			return server.getConfig().WORLD_BUILDER_MODE
				?identity+":draft-"
					+server.getWorldEditorSessions()
						.nativeTerrainSceneRevision()
				:identity
					+(residency == null
						? ":snapshot-v5"
						: usesAtomicActivation()
							? ":atomic-resident-v8"
							: requiresReadiness()
								? ":ready-resident-v7"
							: ":resident-v6");
		}

		private int protocolVersion() {
			return residency == null
				? NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION
				: usesAtomicActivation()
					? ATOMIC_NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION
					: requiresReadiness()
						? READY_RESIDENT_NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION
					: RESIDENT_NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION;
		}

		private boolean usesAtomicActivation() {
			return residency != null
				&& server.getConfig()
					.WANT_LAYERED_NATIVE_TERRAIN_ATOMIC_ACTIVATION
				&& server.getConfig().WANT_LAYERED_NATIVE_TERRAIN_PREDICTION
				&& server.getConfig().WANT_LAYERED_NATIVE_TERRAIN_READINESS
				&& server.getConfig().WANT_SYNC_SCENE_BASELINE;
		}

		private boolean requiresReadiness() {
			return residency != null
				&& server.getConfig()
					.WANT_LAYERED_NATIVE_TERRAIN_READINESS;
		}

		private boolean requiresBlockingReadiness() {
			/*
			 * Protocol-v8 context packets are installed synchronously by the
			 * client and are followed by an atomic Player/static-scene
			 * presentation barrier. Their readiness receipt remains useful
			 * for residency accounting, but delaying the already ordered
			 * Player/static packets until the next 640 ms server tick only
			 * adds visible latency. Protocol v7 retains the original blocking
			 * acknowledgement rollback.
			 */
			return requiresReadiness() && !usesAtomicActivation();
		}

		private void commitResidency() {
			if (residencyTransaction != null) {
				residencyTransaction.commit();
				residencyTransaction = null;
			}
		}

		private void populate(final LayeredSceneContextStruct context) {
			context.nativePackageId = terrainPackage.getPackageId();
			context.nativePackageVersion = terrainPackage.getPackageVersion();
			context.nativeManifestSha256 =
				terrainPackage.getManifestSha256();
			context.nativePresentationChunkSize =
				NATIVE_LAYERED_WIRE_CHUNK_SIZE;
			context.nativeCurrentChunkX = currentChunkX;
			context.nativeCurrentChunkY = currentChunkY;
			context.nativeChunkRadius = NATIVE_LAYERED_CHUNK_RADIUS;
			populateChunks(
				context.worldSpace,
				context.logicalLevel,
				context.nativePackageId,
				context.nativePackageVersion,
				context.nativeManifestSha256,
				context.nativePresentationChunkSize,
				context.nativeChunks);
		}

		private void populate(final LayeredTerrainStageStruct stage) {
			populate(stage, NATIVE_LAYERED_CHUNK_RADIUS);
		}

		private void populate(
			final LayeredTerrainStageStruct stage,
			final int chunkRadius) {
			populate(stage, chunkRadius, false);
		}

		private void populateSymmetricStructure(
			final LayeredTerrainStageStruct stage) {
			populate(
				stage,
				NATIVE_LAYERED_SYMMETRIC_RESIDENCY_RADIUS,
				true);
		}

		private void populate(
			final LayeredTerrainStageStruct stage,
			final int chunkRadius,
			final boolean structuralOnly) {
			if (chunkRadius < NATIVE_LAYERED_CHUNK_RADIUS
				|| chunkRadius
					> NATIVE_LAYERED_SYMMETRIC_RESIDENCY_RADIUS) {
				throw new IllegalArgumentException(
					"Unsupported native terrain stage radius");
			}
			stage.nativePackageId = terrainPackage.getPackageId();
			stage.nativePackageVersion = terrainPackage.getPackageVersion();
			stage.nativeManifestSha256 =
				terrainPackage.getManifestSha256();
			stage.nativePresentationChunkSize =
				NATIVE_LAYERED_WIRE_CHUNK_SIZE;
			stage.nativeCurrentChunkX = currentChunkX;
			stage.nativeCurrentChunkY = currentChunkY;
			stage.nativeChunkRadius = chunkRadius;
			populateChunks(
				stage.worldSpace,
				stage.logicalLevel,
				stage.nativePackageId,
				stage.nativePackageVersion,
				stage.nativeManifestSha256,
				stage.nativePresentationChunkSize,
				stage.nativeChunks,
				chunkRadius,
				structuralOnly);
		}

		private void populateChunks(
			final String worldSpace,
			final int logicalLevel,
			final String packageId,
			final String packageVersion,
			final String manifestSha256,
			final int presentationChunkSize,
			final List<LayeredSceneTerrainChunkStruct> chunks) {
			populateChunks(
				worldSpace,
				logicalLevel,
				packageId,
				packageVersion,
				manifestSha256,
				presentationChunkSize,
				chunks,
				NATIVE_LAYERED_CHUNK_RADIUS,
				false);
		}

		private void populateChunks(
			final String worldSpace,
			final int logicalLevel,
			final String packageId,
			final String packageVersion,
			final String manifestSha256,
			final int presentationChunkSize,
			final List<LayeredSceneTerrainChunkStruct> chunks,
			final int chunkRadius,
			final boolean structuralOnly) {
			if (residencyTransaction != null) {
				throw new IllegalStateException(
					"Native terrain residency receipt was populated twice");
			}
			if (residency != null) {
				residencyTransaction = residency.begin();
			}
			int availableSectors = 0;
			int payloadSectors = 0;
			int referencedSectors = 0;
			long rawBytes = 0L;
			long wireBytes = 0L;
			long cacheRequests = 0L;
			long cacheHits = 0L;
			long cacheMisses = 0L;
			long wireBuildNanos = 0L;
			for (int deltaX = -chunkRadius;
				deltaX <= chunkRadius;
				deltaX++) {
				for (int deltaY = -chunkRadius;
					deltaY <= chunkRadius;
					deltaY++) {
					final int chunkX = Math.addExact(currentChunkX, deltaX);
					final int chunkY = Math.addExact(currentChunkY, deltaY);
					final LayeredSceneTerrainChunkStruct output =
						new LayeredSceneTerrainChunkStruct();
					output.chunkX = chunkX;
					output.chunkY = chunkY;
					final boolean symmetricOuter =
						chunkRadius
								== NATIVE_LAYERED_SYMMETRIC_RESIDENCY_RADIUS
							&& Math.max(
								Math.abs(deltaX),
								Math.abs(deltaY))
								== NATIVE_LAYERED_SYMMETRIC_RESIDENCY_RADIUS;
					final WorldMapSectorId sectorId=new WorldMapSectorId(
							location.getWorldSpace(),
							location.getCoordinate().getLevel(),
							chunkX,
							chunkY);
					final Optional<NativeLayeredTerrainSector> source =
						server.getConfig().WORLD_BUILDER_MODE
							?server.getWorldEditorSessions()
								.findNativeTerrainSector(
									terrainPackage,sectorId)
							:terrainPackage.findSector(sectorId);
					output.available =
						source.isPresent()
							&& (!structuralOnly || symmetricOuter);
					if (output.available) {
						availableSectors++;
						final NativeLayeredTerrainSector chunk = source.get();
						final boolean visualOnly =
							symmetricOuter && !structuralOnly;
						output.sourceSectorX =
							chunk.getIdentity().getSectorX();
						output.sourceSectorY =
							chunk.getIdentity().getSectorY();
						output.sourceEncoding = structuralOnly
							? NATIVE_LAYERED_STRUCTURAL_ENCODING
							: visualOnly
								? NATIVE_LAYERED_VISUAL_ENCODING
								: chunk.getSourceEncoding();
						if(server.getConfig().WORLD_BUILDER_MODE){
							output.sourcePayloadSha256 =
								server.getWorldEditorSessions()
									.nativeTerrainSectorSha256(chunk);
						}else{
							output.sourcePayloadSha256=chunk.getSourceSha256();
						}
						output.payloadPresent =
							residencyTransaction == null
								|| residencyTransaction.requiresPayload(
									residentContentIdentity(
										packageId,
										packageVersion,
										manifestSha256,
										worldSpace,
										logicalLevel,
										presentationChunkSize,
										output));
						if (output.payloadPresent) {
							payloadSectors++;
							final int expectedRawBytes =
								NativeLayeredTerrainSector.TILE_COUNT
									* (structuralOnly
										? NATIVE_LAYERED_STRUCTURAL_TILE_WIRE_BYTES
										: visualOnly
										? NATIVE_LAYERED_VISUAL_TILE_WIRE_BYTES
										: NativeLayeredTerrainChunk
											.TILE_WIRE_BYTES);
							if(server.getConfig().WORLD_BUILDER_MODE){
								long buildStart = System.nanoTime();
								byte[] rawImage =
									server.getWorldEditorSessions()
										.copyNativeTerrainSectorWireBytes(chunk);
								output.tileBytes =
									compressNativeTerrain(
										structuralOnly
											? structuralTerrainWireBytes(rawImage)
											: visualOnly
											? visualTerrainWireBytes(rawImage)
											: rawImage);
								wireBuildNanos +=
									System.nanoTime() - buildStart;
							} else {
								NativeLayeredTerrainWireCache.Lookup lookup =
									wireCache.getOrCompress(
										terrainPackage.getPackageId()
											+ "@" + terrainPackage.getPackageVersion()
											+ ":" + terrainPackage.getManifestSha256()
											+ ":" + chunk.getIdentity()
											+ ":" + output.sourceEncoding,
										chunk.getSourceSha256()
											+ ":" + output.sourceEncoding,
										expectedRawBytes,
										() -> structuralOnly
											? structuralTerrainWireBytes(
												chunk.copyWireBytes())
											: visualOnly
											? visualTerrainWireBytes(
												chunk.copyWireBytes())
											: chunk.copyWireBytes());
								output.tileBytes = lookup.getCompressedBytes();
								cacheRequests++;
								if (lookup.isCacheHit()) {
									cacheHits++;
								} else {
									cacheMisses++;
								}
								wireBuildNanos += lookup.getBuildNanos();
							}
							rawBytes += expectedRawBytes;
							wireBytes += output.tileBytes.length;
						} else {
							referencedSectors++;
						}
					}
					chunks.add(output);
				}
			}
			server.addNativeTerrainTransferMetrics(
				1,
				chunks.size(),
				availableSectors,
				payloadSectors,
				referencedSectors,
				rawBytes,
				wireBytes,
				cacheRequests,
				cacheHits,
				cacheMisses,
				wireBuildNanos,
				wireCache.size());
		}

		private static String residentContentIdentity(
			final String packageId,
			final String packageVersion,
			final String manifestSha256,
			final String worldSpace,
			final int logicalLevel,
			final int presentationChunkSize,
			final LayeredSceneTerrainChunkStruct chunk) {
			return packageId
				+ "@" + packageVersion
				+ ":" + manifestSha256
				+ ":" + worldSpace
				+ ":" + logicalLevel
				+ ":" + presentationChunkSize
				+ ":" + chunk.chunkX + "," + chunk.chunkY
				+ ":" + chunk.sourceSectorX + "," + chunk.sourceSectorY
				+ ":" + chunk.sourceEncoding
				+ ":" + chunk.sourcePayloadSha256;
		}
	}

	private static byte[] visualTerrainWireBytes(
		final byte[] fullTerrainBytes) {
		final int fullTileBytes =
			NativeLayeredTerrainChunk.TILE_WIRE_BYTES;
		final int expectedFullBytes =
			NativeLayeredTerrainSector.TILE_COUNT * fullTileBytes;
		if (fullTerrainBytes == null
			|| fullTerrainBytes.length != expectedFullBytes) {
			throw new IllegalArgumentException(
				"Full native terrain image is invalid");
		}
		final byte[] visual = new byte[
			NativeLayeredTerrainSector.TILE_COUNT
				* NATIVE_LAYERED_VISUAL_TILE_WIRE_BYTES];
		int source = 0;
		int target = 0;
		while (source < fullTerrainBytes.length) {
			visual[target++] = fullTerrainBytes[source];
			visual[target++] = fullTerrainBytes[source + 1];
			visual[target++] = fullTerrainBytes[source + 2];
			source += fullTileBytes;
		}
		return visual;
	}

	private static byte[] structuralTerrainWireBytes(
		final byte[] fullTerrainBytes) {
		final int fullTileBytes =
			NativeLayeredTerrainChunk.TILE_WIRE_BYTES;
		final int expectedFullBytes =
			NativeLayeredTerrainSector.TILE_COUNT * fullTileBytes;
		if (fullTerrainBytes == null
			|| fullTerrainBytes.length != expectedFullBytes) {
			throw new IllegalArgumentException(
				"Full native terrain image is invalid");
		}
		final byte[] structural = new byte[
			NativeLayeredTerrainSector.TILE_COUNT
				* NATIVE_LAYERED_STRUCTURAL_TILE_WIRE_BYTES];
		int source = 0;
		int target = 0;
		while (source < fullTerrainBytes.length) {
			System.arraycopy(
				fullTerrainBytes,
				source + NATIVE_LAYERED_VISUAL_TILE_WIRE_BYTES,
				structural,
				target,
				NATIVE_LAYERED_STRUCTURAL_TILE_WIRE_BYTES);
			source += fullTileBytes;
			target += NATIVE_LAYERED_STRUCTURAL_TILE_WIRE_BYTES;
		}
		return structural;
	}

	private static byte[] compressNativeTerrain(final byte[] source) {
		final Deflater compressor = new Deflater(Deflater.BEST_SPEED);
		try {
			compressor.setInput(source);
			compressor.finish();
			final byte[] buffer = new byte[source.length + 128];
			final int length = compressor.deflate(buffer);
			if (!compressor.finished() || length <= 0
				|| length > 0xFFFF) {
				throw new IllegalStateException(
					"Native terrain sector compression exceeded one packet field");
			}
			return Arrays.copyOf(buffer, length);
		} finally {
			compressor.end();
		}
	}

	private static final class LayeredProtocolSceneScope {
		private final String worldSpace;
		private final int level;
		private final String projectionId;
		private final String nativeTerrainScopeIdentity;

		private LayeredProtocolSceneScope(
			final String worldSpace,
			final int level,
			final String projectionId,
			final String nativeTerrainScopeIdentity) {
			this.worldSpace = worldSpace;
			this.level = level;
			this.projectionId = projectionId;
			this.nativeTerrainScopeIdentity = nativeTerrainScopeIdentity;
		}

		private static LayeredProtocolSceneScope from(
			final WorldLocation location,
			final String projectionId,
			final String nativeTerrainScopeIdentity) {
			return new LayeredProtocolSceneScope(
				location.getWorldSpace().getValue(),
				location.getCoordinate().getLevel(),
				projectionId,
				nativeTerrainScopeIdentity);
		}

		@Override
		public boolean equals(final Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof LayeredProtocolSceneScope)) {
				return false;
			}
			final LayeredProtocolSceneScope scope =
				(LayeredProtocolSceneScope) other;
			return level == scope.level
				&& worldSpace.equals(scope.worldSpace)
				&& projectionId.equals(scope.projectionId)
				&& nativeTerrainScopeIdentity.equals(
					scope.nativeTerrainScopeIdentity);
		}

		@Override
		public int hashCode() {
			int result = 31 * worldSpace.hashCode() + level;
			result = 31 * result + projectionId.hashCode();
			return 31 * result + nativeTerrainScopeIdentity.hashCode();
		}
	}

	private void sendWorldTimeIfNeeded(final Player player) {
		if (!player.isUsingCustomClient()) {
			return;
		}
		final long now = System.currentTimeMillis();
		final long lastSyncMillis = player.getAttribute(WORLD_TIME_LAST_SYNC_MILLIS_ATTRIBUTE, 0L);
		final long syncIntervalMillis = getServer().getWorldDayNightClock().shouldSyncFrequently()
			? WORLD_TIME_FAST_SYNC_INTERVAL_MILLIS
			: WORLD_TIME_SYNC_INTERVAL_MILLIS;
		if (lastSyncMillis > 0L && now - lastSyncMillis < syncIntervalMillis) {
			return;
		}
		ActionSender.sendWorldTime(player);
		player.setAttribute(WORLD_TIME_LAST_SYNC_MILLIS_ATTRIBUTE, now);
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
		if (!ensureLayeredSceneContext(player)) {
			return false;
		}
		updateCustomMovementClientRegion(player);

		MovementUpdateStruct struct = new MovementUpdateStruct();
		struct.localX = player.getX();
		struct.localY = player.getY();
		struct.localSprite = player.getSprite();

		for (final Player movedPlayer : movedPlayers) {
			if (struct.players.size() >= CUSTOM_MOVEMENT_UPDATE_LIMIT) {
				break;
			}
			if (movedPlayer.equals(player) || !player.getLocalPlayers().contains(movedPlayer)) {
				continue;
			}
			if (!movedPlayer.withinAuthenticRangeAdditionally(player) || !player.withinRange(movedPlayer)) {
				continue;
			}
			if (!isWithinClientLocalTileWindow(player, movedPlayer.getX(), movedPlayer.getY())) {
				continue;
			}
			struct.players.add(new MovementUpdateStruct.MobMovement(
				movedPlayer.getIndex(), movedPlayer.getX(), movedPlayer.getY(), movedPlayer.getSprite()));
		}

		for (final Npc movedNpc : movedNpcs) {
			if (struct.npcs.size() >= CUSTOM_MOVEMENT_UPDATE_LIMIT) {
				break;
			}
			if (!player.getLocalNpcs().contains(movedNpc)) {
				continue;
			}
			if (!movedNpc.withinAuthenticRangeAdditionally(player) || !player.withinRange(movedNpc)) {
				continue;
			}
			if (!isWithinClientLocalTileWindow(player, movedNpc.getX(), movedNpc.getY())) {
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

	public boolean sendMovementSnapshotPacket(final Player player, final List<Player> movedPlayers, final List<Npc> movedNpcs) {
		if (!player.isUsingCustomClient() || !getServer().getConfig().WANT_SYNC_MOVEMENT_SNAPSHOT) {
			return false;
		}
		if (!ensureLayeredSceneContext(player)) {
			return false;
		}
		updateCustomMovementClientRegion(player);

		MovementSnapshotStruct struct = new MovementSnapshotStruct();
		final boolean layeredProtocol =
			getServer().getConfig().WANT_LAYERED_PROTOCOL_CLIENT_AUTHORITY;
		struct.protocolVersion = layeredProtocol
			? LAYERED_MOVEMENT_SNAPSHOT_PROTOCOL_VERSION
			: MOVEMENT_SNAPSHOT_PROTOCOL_VERSION;
		struct.locationContextSequence = layeredProtocol
			? requireLayeredSceneContextSequence(player)
			: 0;
		struct.serverTick = (int)(getServer().getCurrentTick() & 0x7FFFFFFF);
		struct.sequence = ++movementSnapshotSequence;
		struct.localX = player.getX();
		struct.localY = player.getY();
		struct.localSprite = player.getSprite();

		for (final Player movedPlayer : movedPlayers) {
			if (struct.players.size() >= CUSTOM_MOVEMENT_UPDATE_LIMIT) {
				break;
			}
			if (movedPlayer.equals(player) || !player.getLocalPlayers().contains(movedPlayer)) {
				continue;
			}
			if (!movedPlayer.withinAuthenticRangeAdditionally(player) || !player.withinRange(movedPlayer)) {
				continue;
			}
			if (!isWithinClientLocalTileWindow(player, movedPlayer.getX(), movedPlayer.getY())) {
				continue;
			}
			struct.players.add(new MovementSnapshotStruct.MobMovement(
				movedPlayer.getIndex(), movedPlayer.getX(), movedPlayer.getY(), movedPlayer.getSprite()));
		}

		for (final Npc movedNpc : movedNpcs) {
			if (struct.npcs.size() >= CUSTOM_MOVEMENT_UPDATE_LIMIT) {
				break;
			}
			if (!player.getLocalNpcs().contains(movedNpc)) {
				continue;
			}
			if (!movedNpc.withinAuthenticRangeAdditionally(player) || !player.withinRange(movedNpc)) {
				continue;
			}
			if (!isWithinClientLocalTileWindow(player, movedNpc.getX(), movedNpc.getY())) {
				continue;
			}
			struct.npcs.add(new MovementSnapshotStruct.MobMovement(
				movedNpc.getIndex(), movedNpc.getX(), movedNpc.getY(), movedNpc.getSprite()));
		}

		if (!movedPlayers.contains(player) && struct.players.isEmpty() && struct.npcs.isEmpty()) {
			return false;
		}

		tryFinalizeAndSendPacket(OpcodeOut.SEND_MOVEMENT_SNAPSHOT, struct, player);
		getServer().addMovementSnapshotMetrics(
			1 + struct.players.size() + struct.npcs.size(),
			MOVEMENT_SNAPSHOT_FIXED_PAYLOAD_BYTES
				+ (layeredProtocol ? LAYERED_CONTEXT_SEQUENCE_BYTES : 0)
				+ ((struct.players.size() + struct.npcs.size()) * MOVEMENT_SNAPSHOT_MOB_RECORD_BYTES));
		return true;
	}

	private static boolean isWithinClientLocalTileWindow(final Player viewer, final int worldX, final int worldY) {
		if (!viewer.isUsingCustomClient()) {
			return true;
		}
		final int baseX = currentClientLocalBaseX(viewer);
		final int baseY = currentClientLocalBaseY(viewer);
		return worldX >= baseX
			&& worldY >= baseY
			&& worldX < baseX + CLIENT_LOCAL_TILE_COUNT
			&& worldY < baseY + CLIENT_LOCAL_TILE_COUNT;
	}

	private static void updateCustomMovementClientRegion(final Player viewer) {
		if (!viewer.isUsingCustomClient()) {
			return;
		}
		Integer midpointX = viewer.getAttribute(CUSTOM_MOVEMENT_CLIENT_MID_X_ATTRIBUTE, null);
		Integer midpointY = viewer.getAttribute(CUSTOM_MOVEMENT_CLIENT_MID_Y_ATTRIBUTE, null);
		if (midpointX == null || midpointY == null) {
			final Point midpointRegion = viewer.getAttribute("midpointRegion", null);
			if (midpointRegion != null) {
				midpointX = midpointRegion.getX();
				midpointY = midpointRegion.getY();
			} else {
				midpointX = clientLocalMidpointForTile(viewer.getX(), CLIENT_LOCAL_PLANE_WIDTH);
				midpointY = clientLocalMidpointForTile(viewer.getY(), CLIENT_LOCAL_PLANE_HEIGHT);
			}
		}
		// A teleport forces the client to recenter even when its destination
		// remains inside the ordinary walking reload radius. Recenter the
		// server mirror in the same tick so terrain receipts and movement
		// coordinates describe the client's newly active section window.
		if (viewer.isTeleporting()) {
			midpointX = clientLocalMidpointForTile(
				viewer.getX(), CLIENT_LOCAL_PLANE_WIDTH);
			midpointY = clientLocalMidpointForTile(
				viewer.getY(), CLIENT_LOCAL_PLANE_HEIGHT);
		} else if (viewer.getX() <= midpointX - CLIENT_LOCAL_REGION_RELOAD_RADIUS
			|| viewer.getX() >= midpointX + CLIENT_LOCAL_REGION_RELOAD_RADIUS
			|| viewer.getY() <= midpointY - CLIENT_LOCAL_REGION_RELOAD_RADIUS
			|| viewer.getY() >= midpointY + CLIENT_LOCAL_REGION_RELOAD_RADIUS) {
			midpointX = clientLocalMidpointForTile(viewer.getX(), CLIENT_LOCAL_PLANE_WIDTH);
			midpointY = clientLocalMidpointForTile(viewer.getY(), CLIENT_LOCAL_PLANE_HEIGHT);
		}
		viewer.setAttribute(CUSTOM_MOVEMENT_CLIENT_MID_X_ATTRIBUTE, midpointX);
		viewer.setAttribute(CUSTOM_MOVEMENT_CLIENT_MID_Y_ATTRIBUTE, midpointY);
	}

	private static int currentClientLocalBaseX(final Player viewer) {
		return currentClientLocalMidpoint(viewer, CUSTOM_MOVEMENT_CLIENT_MID_X_ATTRIBUTE, CLIENT_LOCAL_PLANE_WIDTH)
			- (CLIENT_LOCAL_ACTIVE_SECTION_ORIGIN_OFFSET * CLIENT_LOCAL_SECTION_SIZE);
	}

	private static int currentClientLocalBaseY(final Player viewer) {
		return currentClientLocalMidpoint(viewer, CUSTOM_MOVEMENT_CLIENT_MID_Y_ATTRIBUTE, CLIENT_LOCAL_PLANE_HEIGHT)
			- (CLIENT_LOCAL_ACTIVE_SECTION_ORIGIN_OFFSET * CLIENT_LOCAL_SECTION_SIZE);
	}

	private static int currentClientLocalMidpoint(
		final Player viewer,
		final String attribute,
		final int planeOffset
	) {
		final Integer midpoint = viewer.getAttribute(attribute, null);
		if (midpoint != null) {
			return midpoint;
		}
		updateCustomMovementClientRegion(viewer);
		final Integer updatedMidpoint = viewer.getAttribute(attribute, null);
		return updatedMidpoint == null
			? clientLocalMidpointForTile(planeOffset == CLIENT_LOCAL_PLANE_WIDTH ? viewer.getX() : viewer.getY(), planeOffset)
			: updatedMidpoint;
	}

	private static int clientLocalMidpointForTile(final int worldTile, final int planeOffset) {
		final int section = (worldTile + planeOffset + (CLIENT_LOCAL_SECTION_SIZE / 2))
			/ CLIENT_LOCAL_SECTION_SIZE;
		return (section * CLIENT_LOCAL_SECTION_SIZE) - planeOffset;
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
			offset += 1 << CUSTOM_MOB_COORD_OFFSET_BITS;
		}
		return offset;
	}

	private static int npcDistanceToPlayer(final Player player, final Npc npc) {
		return Math.abs(npc.getX() - player.getX()) + Math.abs(npc.getY() - player.getY());
	}

	private static int npcPriorityRank(final Player player, final Npc npc) {
		if (npc.equals(player.getOpponent()) || player.equals(npc.getOpponent())) {
			return 0;
		}
		if (npc.inCombat()) {
			return 1;
		}
		return 2;
	}

	private static boolean canSendNpcToPlayer(final Player player, final Npc npc) {
		if (npc.isInvisibleTo(player)) {
			return false;
		}
		if (npc.getID() == NpcId.NED_BOAT.id() && !player.getCache().hasKey("ned_hired")) {
			return false;
		}
		return !npc.isRemoved()
			&& !npc.isRespawning()
			&& npc.withinAuthenticRangeAdditionally(player)
			&& player.withinRange(npc)
			&& isWithinClientLocalTileWindow(
				player, npc.getX(), npc.getY());
	}

	private static List<Npc> prioritizeVisibleNpcs(final Player player, final Collection<Npc> visibleNpcs) {
		final int localNpcLimit = localMobLimit(player);
		final HashSet<Npc> existingLocalNpcs = new HashSet<>(player.getLocalNpcs());
		final ArrayList<Npc> prioritizedNpcs = new ArrayList<>(visibleNpcs.size());
		for (final Npc npc : visibleNpcs) {
			if (canSendNpcToPlayer(player, npc)) {
				prioritizedNpcs.add(npc);
			}
		}
		prioritizedNpcs.sort((left, right) -> {
			int comparison = Integer.compare(npcPriorityRank(player, left), npcPriorityRank(player, right));
			if (comparison != 0) {
				return comparison;
			}
			comparison = Integer.compare(npcDistanceToPlayer(player, left), npcDistanceToPlayer(player, right));
			if (comparison != 0) {
				return comparison;
			}
			comparison = Boolean.compare(!existingLocalNpcs.contains(left), !existingLocalNpcs.contains(right));
			if (comparison != 0) {
				return comparison;
			}
			return Integer.compare(left.getIndex(), right.getIndex());
		});
		if (prioritizedNpcs.size() > localNpcLimit) {
			return prioritizedNpcs.subList(0, localNpcLimit);
		}
		return prioritizedNpcs;
	}

	private static int localMobLimit(final Player player) {
		return player.isUsingCustomClient() ? CUSTOM_LOCAL_MOB_LIMIT : AUTHENTIC_LOCAL_MOB_LIMIT;
	}

	private static int localMobCountBits(final Player player) {
		return player.isUsingCustomClient() ? CUSTOM_LOCAL_MOB_COUNT_BITS : AUTHENTIC_LOCAL_MOB_COUNT_BITS;
	}

	protected void updateNpcs(final Player playerToUpdate, final Collection<Npc> visibleNpcs) {
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
			for (final Npc newNPC : visibleNpcs) {
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

				if (!playerToUpdate.getConfig().DISABLE_NPC_LOCATION_CACHE) {
					playerToUpdate.getLocalNpcs().add(newNPC);
				}
			}

			struct.mobsUpdate = mobsUpdate;
		} else {
			final int localNpcCount = playerToUpdate.getLocalNpcs().size();
			final int visibleNpcCount = visibleNpcs.size();
			final int localNpcLimit = localMobLimit(playerToUpdate);
			List<Map.Entry<Integer, Integer>> mobsUpdate =
				new ArrayList<>(1 + (localNpcCount * 3) + (Math.min(localNpcLimit, visibleNpcCount) * 5));
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
			final List<Npc> prioritizedVisibleNpcs = useCustomMovementStream
				? prioritizeVisibleNpcs(playerToUpdate, visibleNpcs)
				: null;
			final HashSet<Npc> prioritizedVisibleNpcSet = prioritizedVisibleNpcs == null
				? null
				: new HashSet<>(prioritizedVisibleNpcs);

			mobsUpdate.add(bit(playerToUpdate.getLocalNpcs().size(), localMobCountBits(playerToUpdate)));
			for (final Iterator<Npc> it$ = playerToUpdate.getLocalNpcs().iterator(); it$.hasNext(); ) {
				Npc localNpc = it$.next();
				final UpdateFlags updateFlags = localNpc.getUpdateFlags();
				final long deathVisualTick = localNpc.getAttribute(Npc.DEATH_VISUAL_TICK_ATTRIBUTE, -1L);
				final String deathVisualViewerKey = NPC_DEATH_VISUAL_SENT_TICK_PREFIX + playerToUpdate.getIndex();
				final long deathVisualSentTick = localNpc.getAttribute(deathVisualViewerKey, Long.MIN_VALUE);
				final boolean hasPendingDeathVisual = playerToUpdate.isUsingCustomClient()
					&& (localNpc.isRemoved() || localNpc.isRespawning())
					&& playerToUpdate.withinRange(localNpc)
					&& localNpc.withinAuthenticRangeAdditionally(playerToUpdate)
					&& deathVisualTick >= 0
					&& deathVisualSentTick != deathVisualTick
					&& (updateFlags.hasCombatEffect() || updateFlags.hasHitSplats() || updateFlags.hasTakenDamage());
				final boolean spriteNeedsFullRefresh = useCustomMovementStream
					&& localNpc.spriteChanged()
					&& localNpc.getSprite() >= 12;
				final boolean evictForNpcPriority = prioritizedVisibleNpcSet != null
					&& !hasPendingDeathVisual
					&& !prioritizedVisibleNpcSet.contains(localNpc);
				if (hasPendingDeathVisual) {
					localNpc.setAttribute(deathVisualViewerKey, deathVisualTick);
				}

				if (localNpc.isInvisibleTo(playerToUpdate)) {
					it$.remove();
					mobsUpdate.add(bit(UPDATE_REQUIRED, 1));
					mobsUpdate.add(bit(NOT_MOVING, 1));
					mobsUpdate.add(bit(REMOVE_NPC, 2));
					continue;
				}

				if (!localNpc.withinAuthenticRangeAdditionally(playerToUpdate) || !playerToUpdate.withinRange(localNpc) || // remove because they are out of range
					!isWithinClientLocalTileWindow(playerToUpdate, localNpc.getX(), localNpc.getY()) ||
					(localNpc.isRemoved() && !hasPendingDeathVisual) || // remove because they are removed
					localNpc.isTeleporting() || // if they've teleported, then they may have moved more than one square, and thus require a full coordinate refresh
					(localNpc.inCombat() && !hasPendingDeathVisual && !useCustomMovementStream) || // remove because when FIRST entering combat, they may have advanced towards the player, then their sprite is incompatible with a movement update (no direction, and > 7) TODO: should be inCombatChanged(), since it's only necessary on the first round of combat.
					spriteNeedsFullRefresh || // remove/re-add because the legacy no-move update reserves high two-bit value 3 as remove, colliding with sprites 12-15.
					evictForNpcPriority || // remove lower-priority locals so nearby/combat NPCs fit in the 8-bit local NPC cache.
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
					} else if (localNpc.spriteChanged()) {
						mobsUpdate.add(bit(UPDATE_REQUIRED, 1));
						mobsUpdate.add(bit(NOT_MOVING, 1));
						mobsUpdate.add(bit(localNpc.getSprite(), 4)); // 4 bits to accommodate sprites 8 & 9, used for fighting
					} else {
						mobsUpdate.add(bit(UPDATE_NOT_REQUIRED, 1));
					}
				}
			}

			for (final Npc newNPC : prioritizedVisibleNpcs == null ? visibleNpcs : prioritizedVisibleNpcs) {
				if (playerToUpdate.getLocalNpcs().contains(newNPC) || // The NPC is cached & updated successfully. Don't refresh & don't duplicate them in the localNpcs cache.
					!canSendNpcToPlayer(playerToUpdate, newNPC) // only have 5 bits in the rsc235 protocol, so the npc can only be shown up to 16 tiles away
					// || (newNPC.isTeleporting() && !newNPC.inCombat()) // ??? Might be a bug. If they teleported this tick, and ended up within range, we want to refresh them for sure, right?
					) {
					continue;
				} else if (playerToUpdate.getLocalNpcs().size() >= localNpcLimit) {
					break;
				}

				boolean forClient115 = playerToUpdate.isUsing115CompatibleClient();
				boolean forClient140 = playerToUpdate.isUsing140CompatibleClient();
				boolean forAuthentic = !playerToUpdate.isUsingCustomClient();
				int offsetBits = forAuthentic ? 5 : CUSTOM_MOB_COORD_OFFSET_BITS;
				mobsUpdate.add(bit(safeNPCIndex(playerToUpdate, newNPC.getIndex()), forClient115 || forClient140 ? 11 : 12));
				mobsUpdate.add(bit(mobCoordOffset(newNPC.getX(), playerToUpdate.getX()), offsetBits));
				mobsUpdate.add(bit(mobCoordOffset(newNPC.getY(), playerToUpdate.getY()), offsetBits));
				mobsUpdate.add(bit(newNPC.getSprite(), 4));
				int numBits = forClient115 ? 8 : (forClient140 ? 9 : 10);
				mobsUpdate.add(bit(newNPC.getID(), numBits));

				if (!playerToUpdate.getConfig().DISABLE_NPC_LOCATION_CACHE) {
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

	protected void updatePlayers(final Player playerToUpdate, final Collection<Player> visiblePlayers) {
		MobsUpdateStruct struct = new MobsUpdateStruct();
		ClearMobsStruct clearStruct = new ClearMobsStruct();

		Point midRegion = playerToUpdate.getAttribute("midpointRegion");
		if (midRegion != null) {
			int regionRefreshRadius = playerToUpdate.isUsingCustomClient() ? CUSTOM_CLIENT_REGION_REFRESH_RADIUS : 32;
			if (!playerToUpdate.getLocation().inBounds(midRegion.getX() - regionRefreshRadius,
				midRegion.getY() - regionRefreshRadius, midRegion.getX() + regionRefreshRadius,
				midRegion.getY() + regionRefreshRadius)) {
				playerToUpdate.setNextRegionLoad();
				playerToUpdate.changeZone();
			}
		} else {
			playerToUpdate.setNextRegionLoad();
		}

		boolean isRetroClient = playerToUpdate.isUsing38CompatibleClient() || playerToUpdate.isUsing39CompatibleClient();
		boolean usesKnownPlayers = playerToUpdate.getClientVersion() >= 61 && playerToUpdate.getClientVersion() <= 204;
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
			final int localPlayerLimit = localMobLimit(playerToUpdate);
			List<Map.Entry<Integer, Integer>> mobsUpdate =
				new ArrayList<>(4 + (localPlayerCount * 3) + (Math.min(localPlayerLimit, visiblePlayerCount) * 5));
			final boolean forAuthentic = !playerToUpdate.isUsingCustomClient();
			final int offsetBits = forAuthentic ? 5 : CUSTOM_MOB_COORD_OFFSET_BITS;

			if (playerToUpdate.isUsing140CompatibleClient() || playerToUpdate.isUsing115CompatibleClient() || playerToUpdate.isUsing69CompatibleClient()) {
				mobsUpdate.add(bit(playerToUpdate.getX(), 10));
				mobsUpdate.add(bit(playerToUpdate.getY(), 12));
			} else {
				mobsUpdate.add(bit(playerToUpdate.getX(), 11));
				mobsUpdate.add(bit(playerToUpdate.getY(), 13));
			}
			mobsUpdate.add(bit(playerToUpdate.getSprite(), 4));
			mobsUpdate.add(bit(playerToUpdate.getLocalPlayers().size(), localMobCountBits(playerToUpdate)));
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
					mobsUpdate.add(bit(mobCoordOffset(otherPlayer.getX(), playerToUpdate.getX()), offsetBits));
					mobsUpdate.add(bit(mobCoordOffset(otherPlayer.getY(), playerToUpdate.getY()), offsetBits));
					mobsUpdate.add(bit(otherPlayer.getSprite(), 4));
					if (usesKnownPlayers) {
						mobsUpdate.add(bit(playerToUpdate.isKnownPlayer(otherPlayer.getIndex()) ? 1 : 0, 1));
					}

					playerToUpdate.getLocalPlayers().add(otherPlayer);
					if (playerToUpdate.getLocalPlayers().size() >= localPlayerLimit) {
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
				if (chatMessage.getRecipient() == null || chatMessage.getRecipient() == player) {
					npcMessagesNeedingDisplayed.add(chatMessage);
				}
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
				for (Projectile projectileFired : updateFlags.getProjectiles()) {
					if (projectileFired.getCaster().getIndex() != -1 && projectileFired.getVictim().getIndex() != -1) {
						npcProjectilesNeedingDisplayed.add(projectileFired);
					}
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
					Entity caster = projectile.getCaster();
					Entity victim = projectile.getVictim();
					if (!victim.isNpc()) {
						continue;
					}
					updates.add((short) safeNPCIndex(player, victim.getIndex()));
					if (caster.isNpc()) {
						updates.add((byte) 3);
						updates.add((short) projectile.getType());
						updates.add((short) safeNPCIndex(player, caster.getIndex()));
					} else if (caster.isPlayer()) {
						updates.add((byte) 4);
						updates.add((short) projectile.getType());
						updates.add((short) caster.getIndex());
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
					updates.add((byte) Summoning.getSummonCurrentHits(summonedNpcHealth));
					updates.add((byte) Summoning.getSummonMaxHits(summonedNpcHealth));
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
			for (Projectile projectileFired : player.getUpdateFlags().getProjectiles()) {
				if (projectileFired.getCaster().getIndex() != -1 && projectileFired.getVictim().getIndex() != -1) {
					projectilesNeedingDisplayed.add(projectileFired);
				}
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
		final boolean selfAppearancePending = player.getAttribute(
			NATIVE_TERRAIN_SELF_APPEARANCE_PENDING_ATTRIBUTE, false);
		if (player.getUpdateFlags().hasAppearanceChanged()
			|| selfAppearancePending) {
			playersNeedingAppearanceUpdate.add(player);
		}
		for (final Player otherPlayer : player.getLocalPlayers()) {
			final UpdateFlags updateFlags = otherPlayer.getUpdateFlags();

			if (updateFlags.hasBubble()) {
				final Bubble bubble = updateFlags.getActionBubble().get();
				bubblesNeedingDisplayed.add(bubble);
			}
			if (updateFlags.hasFiredProjectile()) {
				projectilesNeedingDisplayed.addAll(updateFlags.getProjectiles());
			}

			if (updateFlags.hasChatMessage()) {
				ChatMessage chatMessage = updateFlags.getChatMessage();
				boolean directedToViewer = chatMessage.getRecipient() == player;
				boolean publicChatVisible = chatMessage.getRecipient() == null
					&& ((((player.getSocial().isFriendsWith(otherPlayer.getUsernameHash()) && !blockAll)
					|| (!player.getSocial().isFriendsWith(otherPlayer.getUsernameHash()) && blockNone))
					&& !player.getSocial().isIgnoring(otherPlayer.getUsernameHash()))
					|| player.isMod() || otherPlayer.isMod());
				if ((directedToViewer || publicChatVisible)
					&& (!chatMessage.getMuted() || player.hasElevatedPriveledges())) {
					chatMessagesNeedingDisplayed.add(chatMessage);
				}
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
		if (selfAppearancePending) {
			player.removeAttribute(
				NATIVE_TERRAIN_SELF_APPEARANCE_PENDING_ATTRIBUTE);
		}
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
					Entity caster = projectile.getCaster();
					Entity victim = projectile.getVictim();
					if (!victim.isPlayer()) {
						continue;
					}
					updatesMain.add((short) victim.getIndex());
					if (caster.isNpc()) {
						updatesMain.add((byte) 3);
						updatesMain.add((short) projectile.getType());
						updatesMain.add((short) caster.getIndex());
					} else if (caster.isPlayer()) {
						updatesMain.add((byte) 4);
						updatesMain.add((short) projectile.getType());
						updatesMain.add((short) caster.getIndex());
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
							updatesMain.add((short) playerNeedingAppearanceUpdate.getAppearanceID());
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
							updatesMain.add((short) playerNeedingAppearanceUpdate.getAppearanceID());
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

	protected boolean updateGameObjects(
		final Player playerToUpdate,
		final Collection<GameObject> visibleSceneryObjects,
		final boolean sendLegacyStaticScenePackets) {
		boolean changed = false;

		GameObjectsUpdateStruct struct = new GameObjectsUpdateStruct();
		List<GameObjectLoc> objectLocs = new ArrayList<>(playerToUpdate.getLocalGameObjects().size() + visibleSceneryObjects.size());

		for (final Iterator<GameObject> it$ = playerToUpdate.getLocalGameObjects().iterator(); it$.hasNext(); ) {
			final GameObject o = it$.next();
			final int offsetX = o.getX() - playerToUpdate.getX();
			final int offsetY = o.getY() - playerToUpdate.getY();
			if (!playerToUpdate.withinObjectGridRange(o)
				|| !isSceneDeltaSafeOffset(offsetX, offsetY)
				|| o.isRemoved()
				|| o.isInvisibleTo(playerToUpdate)) {
				if (isSignedByteOffset(offsetX, offsetY)) {
					objectLocs.add(new GameObjectLoc(60000, offsetX, offsetY, o.getDirection(), 0));
				}
				it$.remove();
				changed = true;
			}
		}

		// Add scenery
		for (final GameObject newObject : visibleSceneryObjects) {
			boolean skipAdd = newObject.isRemoved() ||
				newObject.isInvisibleTo(playerToUpdate) ||
				newObject.getType() != 0 ||
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
			if (!isSceneDeltaSafeOffset(offsetX, offsetY)) {
				continue;
			}

			final int newObjectId = retroRockConverter(playerToUpdate, newObject.getLoc());

			objectLocs.add(new GameObjectLoc(newObjectId, offsetX, offsetY, newObject.getDirection(), 0));
			playerToUpdate.getLocalGameObjects().add(newObject);
			changed = true;
		}
		struct.objects = objectLocs;
		if (!objectLocs.isEmpty()) {
			if (sendLegacyStaticScenePackets) {
				tryFinalizeAndSendPacket(OpcodeOut.SEND_SCENERY_HANDLER, struct, playerToUpdate);
			} else {
				getServer().addSuppressedLegacyStaticSceneMetrics(false, objectLocs.size());
			}
		}
		return changed;
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

	protected boolean updateGroundItems(final Player playerToUpdate, final Collection<GroundItem> visibleGroundItems) {
		boolean changed = false;

		GroundItemsUpdateStruct struct = new GroundItemsUpdateStruct();
		List<ItemLoc> itemLocs = new ArrayList<>(playerToUpdate.getLocalGroundItems().size() + visibleGroundItems.size());

		for (final Iterator<GroundItem> it$ = playerToUpdate.getLocalGroundItems().iterator(); it$.hasNext(); ) {
			final GroundItem groundItem = it$.next();
			final int offsetX = (groundItem.getX() - playerToUpdate.getX());
			final int offsetY = (groundItem.getY() - playerToUpdate.getY());

			if (!playerToUpdate.withinObjectGridRange(groundItem)
				|| !isSceneDeltaSafeOffset(offsetX, offsetY)
				|| groundItem.isRemoved()
				|| groundItem.isInvisibleTo(playerToUpdate)) {
				if (isSignedByteOffset(offsetX, offsetY)) {
					itemLocs.add(new ItemLoc(groundItem.getID() + 32768, offsetX, offsetY, groundItem.getAmount(), 0,
						groundItem.getNoted() && getServer().getConfig().WANT_BANK_NOTES ? 1 : 0));
				}
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
			if (!isSceneDeltaSafeOffset(offsetX, offsetY)) {
				continue;
			}
			itemLocs.add(new ItemLoc(groundItem.getID(), offsetX, offsetY, groundItem.getAmount(), 0,
				groundItem.getNoted() && getServer().getConfig().WANT_BANK_NOTES ? 1 : 0));
			playerToUpdate.getLocalGroundItems().add(groundItem);
			changed = true;
		}
		struct.objects = itemLocs;
		if (!itemLocs.isEmpty()) {
			tryFinalizeAndSendPacket(OpcodeOut.SEND_GROUND_ITEM_HANDLER, struct, playerToUpdate);
		}
		return changed;
	}

	protected boolean updateWallObjects(
		final Player playerToUpdate,
		final Collection<GameObject> visibleWallObjects,
		final boolean sendLegacyStaticScenePackets) {
		boolean changed = false;

		GameObjectsUpdateStruct struct = new GameObjectsUpdateStruct();
		List<GameObjectLoc> objectLocs = new ArrayList<>(playerToUpdate.getLocalWallObjects().size() + visibleWallObjects.size());

		// remove all boundaries that need to be removed
		for (final Iterator<GameObject> it$ = playerToUpdate.getLocalWallObjects().iterator(); it$.hasNext(); ) {
			final GameObject o = it$.next();
			final int offsetX = o.getX() - playerToUpdate.getX();
			final int offsetY = o.getY() - playerToUpdate.getY();
			if (!playerToUpdate.withinObjectGridRange(o)
				|| !isSceneDeltaSafeOffset(offsetX, offsetY)
				|| o.isRemoved()
				|| o.isInvisibleTo(playerToUpdate)) {
				if (isSignedByteOffset(offsetX, offsetY)) {
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
					}
				}
				it$.remove();
				changed = true;
			}
		}

		// add all new boundaries to be added
		for (final GameObject newObject : visibleWallObjects) {
			if (!playerToUpdate.withinObjectGridRange(newObject) || newObject.isRemoved()
				|| newObject.isInvisibleTo(playerToUpdate) || newObject.getType() != 1
				|| playerToUpdate.getLocalWallObjects().contains(newObject)) {
				continue;
			}

			final int offsetX = newObject.getX() - playerToUpdate.getX();
			final int offsetY = newObject.getY() - playerToUpdate.getY();
			if (!isSceneDeltaSafeOffset(offsetX, offsetY)) {
				continue;
			}
			objectLocs.add(new GameObjectLoc(newObject.getID(), offsetX, offsetY, newObject.getDirection(), 1));
			playerToUpdate.getLocalWallObjects().add(newObject);
			changed = true;
		}
		struct.objects = objectLocs;
		if (!objectLocs.isEmpty()) {
			if (sendLegacyStaticScenePackets) {
				tryFinalizeAndSendPacket(OpcodeOut.SEND_BOUNDARY_HANDLER, struct, playerToUpdate);
			} else {
				getServer().addSuppressedLegacyStaticSceneMetrics(true, objectLocs.size());
			}
		}
		return changed;
	}

	private boolean shouldSendLegacyStaticScenePackets(final Player player, final boolean staticSceneScanSkipped) {
		if (!player.isUsingCustomClient() || !getServer().getConfig().WANT_SYNC_SCENE_BASELINE) {
			return true;
		}
		return !staticSceneScanSkipped;
	}

	protected void sendAppearanceKeepalive(final Player player) {
		NoPayloadStruct struct = new NoPayloadStruct();
		tryFinalizeAndSendPacket(OpcodeOut.SEND_APPEARANCE_KEEPALIVE, struct, player);
	}

	private boolean isSignedByteOffset(final int offsetX, final int offsetY) {
		return offsetX >= Byte.MIN_VALUE && offsetX <= Byte.MAX_VALUE
			&& offsetY >= Byte.MIN_VALUE && offsetY <= Byte.MAX_VALUE;
	}

	/**
	 * Keeps one encodable tile of headroom at every legacy scene edge. Ordinary
	 * one-tile movement can therefore retire an object with a final signed-byte
	 * delta instead of silently losing removal authority at +128 or -129.
	 * Larger jumps are reconciled by the exact-context scene fence.
	 */
	private boolean isSceneDeltaSafeOffset(
		final int offsetX,
		final int offsetY) {
		return offsetX > Byte.MIN_VALUE && offsetX < Byte.MAX_VALUE
			&& offsetY > Byte.MIN_VALUE && offsetY < Byte.MAX_VALUE;
	}

	public final long updateWorld() {
		return getServer().bench(() -> getServer().getWorld().run());
	}

	public final long updateClient(final Player player) {
		return getServer().bench(() -> {
			sendUpdatePackets(player, true);
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
			if (!getServer().isFoundationBenchmarkNpcProfilingEnabled()) {
				npcs.forEachLive(n -> {
					try {
						if (n.isUnregistering()) {
							getServer().getWorld().unregisterNpc(n);
							return;
						}
						if (shouldThrottleIdleNpc(n)) {
							getServer().incrementLastNpcIdleThrottleSkipped();
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
					if (shouldThrottleIdleNpc(n)) {
						getServer().incrementLastNpcIdleThrottleSkipped();
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

	private boolean shouldThrottleIdleNpc(final Npc npc) {
		if (!getServer().getConfig().WANT_NPC_IDLE_TICK_THROTTLE) {
			return false;
		}
		if (isActiveNpc(npc)) {
			return false;
		}

		final int interval = Math.max(1, getServer().getConfig().NPC_IDLE_TICK_THROTTLE_INTERVAL);
		return Math.floorMod(getServer().getCurrentTick() + npc.getIndex(), interval) != 0;
	}

	private boolean isActiveNpc(final Npc npc) {
		return npc.isRemoved()
			|| npc.isRespawning()
			|| npc.isBusy()
			|| npc.isUnregistering()
			|| npc.inCombat()
			|| npc.isHostile()
			|| npc.isFollowing()
			|| !npc.finishedPath()
			|| npc.getInteractingPlayer() != null
			|| npc.getPlayerWantsNpc();
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
						processOfflinePrivateMessageLookup(player, pm);
					}
				}
			}

			if (player.requiresOfferUpdate()) {
				ActionSender.sendTradeItems(player);
				player.setRequiresOfferUpdate(false);
			}
		});
	}

	private void processOfflinePrivateMessageLookup(final Player player, final PrivateMessage privateMessage) {
		final GameDatabase database = player.getWorld().getServer().getDatabase();
		final DatabaseLookupResult<Integer> recipientLookup = DatabaseLookupResult.resolve(
			() -> database.playerIdFromUsername(DataConversions.hashToUsername(privateMessage.getFriend())),
			playerId -> playerId != null && playerId >= 0
		);

		if (recipientLookup.isFound()) {
			if (player.getClientVersion() <= 204) {
				player.playerServerMessage(
					MessageType.PRIVATE_SEND,
					"@cya@" + DataConversions.hashToUsername(privateMessage.getFriend())
						+ " is offline or has privacy mode enabled"
				);
			} else {
				player.message("Unable to send message - player unavailable.");
			}
			return;
		}

		if (recipientLookup.isNotFound()) {
			player.message("Unable to send message - unknown player.");
			return;
		}

		LOGGER.error(
			"Database lookup failed during offline private-message recipient check "
				+ "(databaseType={}, exceptionType={}, origin={})",
			database.getClass().getSimpleName(),
			recipientLookup.getFailureType(),
			recipientLookup.getFailureOrigin()
		);
		player.message("Unable to send message right now. Please try again later.");
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
