package com.openrsc.server;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.openrsc.server.constants.Constants;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.config.DiagnosticsConfiguration;
import com.openrsc.server.config.ProcessNetworkConfiguration;
import com.openrsc.server.config.ServerConfigurationLoadResult;
import com.openrsc.server.config.ServerConfigurationLoader;
import com.openrsc.server.content.achievement.AchievementSystem;
import com.openrsc.server.content.worldedit.WorldEditorSessionManager;
import com.openrsc.server.content.worldedit.WorldEditStorageContext;
import com.openrsc.server.content.worldedit.WorldBuilderAccountProvisioner;
import com.openrsc.server.content.worldedit.WorldBuilderMode;
import com.openrsc.server.content.worldedit.WorldBuilderRuntimeControl;
import com.openrsc.server.database.GameDatabase;
import com.openrsc.server.database.JDBCDatabase;
import com.openrsc.server.database.impl.mysql.MySqlGameDatabase;
import com.openrsc.server.database.impl.mysql.MySqlGameLogger;
import com.openrsc.server.database.impl.sqlite.SqliteGameDatabase;
import com.openrsc.server.database.patches.JDBCPatchApplier;
import com.openrsc.server.database.patches.PatchApplier;
import com.openrsc.server.diagnostics.MovementStutterDiagnostics;
import com.openrsc.server.diagnostics.ActiveCombatBenchmark;
import com.openrsc.server.diagnostics.AuthenticatedNetworkBenchmarkProbe;
import com.openrsc.server.diagnostics.ProjectileCombatBenchmark;
import com.openrsc.server.event.custom.DailyShutdownEvent;
import com.openrsc.server.event.custom.HourlyResetEvent;
import com.openrsc.server.event.rsc.FinitePeriodicEvent;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.PluginTickEvent;
import com.openrsc.server.event.rsc.handler.GameEventHandler;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.MagicCombatEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ThrowingEvent;
import com.openrsc.server.event.rsc.impl.combat.scripts.CombatScriptLoader;
import com.openrsc.server.external.EntityHandler;
import com.openrsc.server.model.PlayerAppearance;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.WorldDayNightClock;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.net.DiscordService;
import com.openrsc.server.net.RSCConnectionHandler;
import com.openrsc.server.net.RSCMultiPortDecoder;
import com.openrsc.server.net.RSCPacketFilter;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.net.rsc.Crypto;
import com.openrsc.server.plugins.handler.PluginHandler;
import com.openrsc.server.plugins.triggers.StartupTrigger;
import com.openrsc.server.runtime.CombatDamageObserver;
import com.openrsc.server.runtime.ResolvedDamageTransaction;
import com.openrsc.server.runtime.GameClock;
import com.openrsc.server.runtime.GameRandom;
import com.openrsc.server.runtime.ProductionGameRandom;
import com.openrsc.server.runtime.DeterministicGameRandom;
import com.openrsc.server.runtime.SystemGameClock;
import com.openrsc.server.service.IPlayerService;
import com.openrsc.server.service.PcapLoggerService;
import com.openrsc.server.service.PlayerService;
import com.openrsc.server.util.*;
import com.openrsc.server.util.languages.I18NService;
import com.openrsc.server.util.rsc.CaptchaGenerator;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.MessageType;
import com.openrsc.server.util.rsc.StringUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.apache.logging.log4j.util.Unbox.box;

public class Server implements Runnable {

	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER;

	public static final ConcurrentHashMap<String, Server> serversList = new ConcurrentHashMap<>();

	private final GameStateUpdater gameUpdater;
	private final WorldDayNightClock worldDayNightClock;
	private final GameEventHandler gameEventHandler;
	private final DiscordService discordService;
	private final LoginExecutor loginExecutor;
	private final ServerConfiguration config;
	private final ProcessNetworkConfiguration processNetworkConfiguration;
	private final DiagnosticsConfiguration diagnosticsConfiguration;
	private ScheduledExecutorService scheduledExecutor;
	private final PluginHandler pluginHandler;
	private final CombatScriptLoader combatScriptLoader;
	private final EntityHandler entityHandler;
	private final MySqlGameLogger gameLogger;
	private final PcapLoggerService pcapLogger;
	private final GameDatabase database;
	private final AchievementSystem achievementSystem;
	private final Constants constants;
	private final RSCPacketFilter packetFilter;
	private final IPlayerService playerService;
	private final I18NService i18nService;
	private final MovementStutterDiagnostics movementStutterDiagnostics;
	private final GameClock gameClock;
	private final GameRandom combatRandom;
	private final CombatDamageObserver combatDamageObserver;
	private final ResolvedDamageTransaction resolvedDamageTransaction;
	private final WorldEditStorageContext worldEditStorage;
	private final WorldEditorSessionManager worldEditorSessions;

	private final World world;
	private final String name;

	private GameTickEvent shutdownEvent;
	private ChannelFuture serverChannel;
	private ChannelFuture serverChannelWs;
	private EventLoopGroup workerGroup;
	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroupWs;
	private EventLoopGroup bossGroupWs;

	private volatile AtomicBoolean running = new AtomicBoolean(false);
	private boolean restarting = false;
	private boolean shuttingDown = false;

	private long serverStartedTime = 0;

	private long lastIncomingPacketsDuration = 0;
	private long lastEventsDuration = 0;
	private long lastOutgoingPacketsDuration = 0;
	private long lastWorldUpdateDuration = 0;
	private long lastProcessPlayersDuration = 0;
	private long lastProcessNpcsDuration = 0;
	private long lastProcessNpcUnregisterDuration = 0;
	private long lastProcessNpcBehaviorDuration = 0;
	private long lastProcessNpcMovementDuration = 0;
	private long lastNpcIdleThrottleSkipped = 0;
	private long lastNpcBehaviorRoamDuration = 0;
	private long lastNpcBehaviorAggroDuration = 0;
	private long lastNpcBehaviorCombatDuration = 0;
	private long lastNpcBehaviorTackleDuration = 0;
	private long lastNpcBehaviorRetreatDuration = 0;
	private long lastNpcRoamEligibilityDuration = 0;
	private long lastNpcRoamAggroScanDuration = 0;
	private long lastNpcRoamTackleScanDuration = 0;
	private long lastNpcRoamRandomWalkDuration = 0;
	private long lastProcessMessageQueuesDuration = 0;
	private long lastUpdateClientsDuration = 0;
	private long lastUpdatePlayersDuration = 0;
	private long lastUpdatePlayerAppearancesDuration = 0;
	private long lastUpdateNpcsDuration = 0;
	private long lastUpdateNpcAppearancesDuration = 0;
	private long lastUpdateGameObjectsDuration = 0;
	private long lastUpdateWallObjectsDuration = 0;
	private long lastUpdateGroundItemsDuration = 0;
	private long lastUpdateTimeoutsDuration = 0;
	private long lastUpdateAppearanceKeepaliveDuration = 0;
	private long lastVisibilitySnapshotDuration = 0;
	private long lastVisibilitySnapshotSamples = 0;
	private long lastVisiblePlayersTotal = 0;
	private long lastVisibleNpcsTotal = 0;
	private long lastVisibleSceneryTotal = 0;
	private long lastVisibleWallObjectsTotal = 0;
	private long lastVisibleGroundItemsTotal = 0;
	private int lastVisiblePlayersMax = 0;
	private int lastVisibleNpcsMax = 0;
	private int lastVisibleSceneryMax = 0;
	private int lastVisibleWallObjectsMax = 0;
	private int lastVisibleGroundItemsMax = 0;
	private long lastVisibilityRegionCacheRequests = 0;
	private long lastVisibilityRegionCacheHits = 0;
	private long lastVisibilityRegionCacheMisses = 0;
	private long lastVisibilityObjectCacheRequests = 0;
	private long lastVisibilityObjectCacheHits = 0;
	private long lastVisibilityObjectCacheMisses = 0;
	private long lastVisibilityObjectCacheClears = 0;
	private long lastVisibilityObjectCacheEntriesCleared = 0;
	private long lastVisibilityObjectSnapshotCacheRequests = 0;
	private long lastVisibilityObjectSnapshotCacheHits = 0;
	private long lastVisibilityObjectSnapshotCacheMisses = 0;
	private long lastVisibilityTickSnapshotCacheRequests = 0;
	private long lastVisibilityTickSnapshotCacheHits = 0;
	private long lastVisibilityTickSnapshotCacheMisses = 0;
	private long lastVisibilityShadowDuration = 0;
	private long lastVisibilityShadowSamples = 0;
	private long lastVisibilityShadowMismatchSamples = 0;
	private long lastVisibilityShadowPlayerMismatches = 0;
	private long lastVisibilityShadowNpcMismatches = 0;
	private long lastVisibilityShadowGameObjectMismatches = 0;
	private long lastVisibilityShadowGroundItemMismatches = 0;
	private int lastVisibilityShadowMobRegionsMax = 0;
	private int lastVisibilityShadowObjectRegionsMax = 0;
	private long lastSceneBaselinePackets = 0;
	private long lastSceneBaselinePages = 0;
	private long lastSceneBaselineRecords = 0;
	private long lastSceneBaselinePayloadBytes = 0;
	private long lastMovementSnapshotPackets = 0;
	private long lastMovementSnapshotRecords = 0;
	private long lastMovementSnapshotPayloadBytes = 0;
	private long lastNativeTerrainContextPackets = 0;
	private long lastNativeTerrainSectorSlots = 0;
	private long lastNativeTerrainAvailableSectors = 0;
	private long lastNativeTerrainPayloadSectors = 0;
	private long lastNativeTerrainReferencedSectors = 0;
	private long lastNativeTerrainRawBytes = 0;
	private long lastNativeTerrainWireBytes = 0;
	private long lastNativeTerrainWireCacheRequests = 0;
	private long lastNativeTerrainWireCacheHits = 0;
	private long lastNativeTerrainWireCacheMisses = 0;
	private long lastNativeTerrainWireBuildNanos = 0;
	private int lastNativeTerrainWireCacheEntriesMax = 0;
	private long lastSuppressedLegacySceneryPackets = 0;
	private long lastSuppressedLegacySceneryRecords = 0;
	private long lastSuppressedLegacyWallPackets = 0;
	private long lastSuppressedLegacyWallRecords = 0;
	private long lastDoCleanupDuration = 0;
	private long lastExecuteWalkToActionsDuration = 0;

	private long lastTickDuration = 0;
	private long timeLate = 0;
	private long lastTickTimestamp = 0;
	private final Map<Integer, Long> incomingTimePerPacketOpcode = new HashMap<>();
	private final Map<Integer, Integer> incomingCountPerPacketOpcode = new HashMap<>();
	private final Map<Integer, Long> outgoingTimePerPacketOpcode = new HashMap<>();
	private final Map<Integer, Integer> outgoingCountPerPacketOpcode = new HashMap<>();
	private final Map<Integer, Long> outgoingPayloadBytesPerPacketOpcode = new HashMap<>();
	private long lastOutgoingPayloadBytes = 0;
	private int privateMessagesSent = 0;
	private final AtomicLong playerSaveRequestCount = new AtomicLong();
	private final AtomicLong playerSaveLogoutRequestCount = new AtomicLong();
	private final AtomicLong playerSaveQueueDuration = new AtomicLong();
	private final AtomicLong playerSaveQueueMaxDuration = new AtomicLong();
	private final AtomicLong playerSaveProcessDuration = new AtomicLong();
	private final AtomicLong playerSaveProcessMaxDuration = new AtomicLong();

	private volatile long maxItemId;

	private final int benchmarkTargetTicks;
	private final int benchmarkWarmupTicks;
	private final int benchmarkSyntheticPlayers;
	private final int benchmarkSyntheticClientVersion;
	private final boolean benchmarkNpcProfiling;
	private final boolean benchmarkDeepNpcProfiling;
	private final int benchmarkActiveCombatPairs;
	private final int benchmarkProjectileCombatGroups;
	private final String benchmarkProjectileCombatFamily;
	private final long benchmarkCombatSeed;
	private volatile ActiveCombatBenchmark activeCombatBenchmark;
	private volatile ProjectileCombatBenchmark projectileCombatBenchmark;
	private long benchmarkSamples = 0;
	private long benchmarkTickTotal = 0;
	private long benchmarkTickMax = 0;
	private long benchmarkEventsTotal = 0;
	private long benchmarkWorldUpdateTotal = 0;
	private long benchmarkProcessPlayersTotal = 0;
	private long benchmarkProcessNpcsTotal = 0;
	private long benchmarkProcessNpcUnregisterTotal = 0;
	private long benchmarkProcessNpcBehaviorTotal = 0;
	private long benchmarkProcessNpcMovementTotal = 0;
	private long benchmarkNpcIdleThrottleSkipped = 0;
	private long benchmarkNpcBehaviorRoamTotal = 0;
	private long benchmarkNpcBehaviorAggroTotal = 0;
	private long benchmarkNpcBehaviorCombatTotal = 0;
	private long benchmarkNpcBehaviorTackleTotal = 0;
	private long benchmarkNpcBehaviorRetreatTotal = 0;
	private long benchmarkNpcRoamEligibilityTotal = 0;
	private long benchmarkNpcRoamAggroScanTotal = 0;
	private long benchmarkNpcRoamTackleScanTotal = 0;
	private long benchmarkNpcRoamRandomWalkTotal = 0;
	private long benchmarkMessageQueuesTotal = 0;
	private long benchmarkUpdateClientsTotal = 0;
	private long benchmarkUpdatePlayersTotal = 0;
	private long benchmarkUpdatePlayerAppearancesTotal = 0;
	private long benchmarkUpdateNpcsTotal = 0;
	private long benchmarkUpdateNpcAppearancesTotal = 0;
	private long benchmarkUpdateGameObjectsTotal = 0;
	private long benchmarkUpdateWallObjectsTotal = 0;
	private long benchmarkUpdateGroundItemsTotal = 0;
	private long benchmarkUpdateTimeoutsTotal = 0;
	private long benchmarkUpdateAppearanceKeepaliveTotal = 0;
	private long benchmarkVisibilitySnapshotTotal = 0;
	private long benchmarkVisibilitySnapshotSamples = 0;
	private long benchmarkVisiblePlayersTotal = 0;
	private long benchmarkVisibleNpcsTotal = 0;
	private long benchmarkVisibleSceneryTotal = 0;
	private long benchmarkVisibleWallObjectsTotal = 0;
	private long benchmarkVisibleGroundItemsTotal = 0;
	private int benchmarkVisiblePlayersMax = 0;
	private int benchmarkVisibleNpcsMax = 0;
	private int benchmarkVisibleSceneryMax = 0;
	private int benchmarkVisibleWallObjectsMax = 0;
	private int benchmarkVisibleGroundItemsMax = 0;
	private long benchmarkVisibilityRegionCacheRequests = 0;
	private long benchmarkVisibilityRegionCacheHits = 0;
	private long benchmarkVisibilityRegionCacheMisses = 0;
	private long benchmarkVisibilityObjectCacheRequests = 0;
	private long benchmarkVisibilityObjectCacheHits = 0;
	private long benchmarkVisibilityObjectCacheMisses = 0;
	private long benchmarkVisibilityObjectCacheClears = 0;
	private long benchmarkVisibilityObjectCacheEntriesCleared = 0;
	private long benchmarkVisibilityObjectSnapshotCacheRequests = 0;
	private long benchmarkVisibilityObjectSnapshotCacheHits = 0;
	private long benchmarkVisibilityObjectSnapshotCacheMisses = 0;
	private long benchmarkVisibilityTickSnapshotCacheRequests = 0;
	private long benchmarkVisibilityTickSnapshotCacheHits = 0;
	private long benchmarkVisibilityTickSnapshotCacheMisses = 0;
	private long benchmarkVisibilityShadowTotal = 0;
	private long benchmarkVisibilityShadowSamples = 0;
	private long benchmarkVisibilityShadowMismatchSamples = 0;
	private long benchmarkVisibilityShadowPlayerMismatches = 0;
	private long benchmarkVisibilityShadowNpcMismatches = 0;
	private long benchmarkVisibilityShadowGameObjectMismatches = 0;
	private long benchmarkVisibilityShadowGroundItemMismatches = 0;
	private int benchmarkVisibilityShadowMobRegionsMax = 0;
	private int benchmarkVisibilityShadowObjectRegionsMax = 0;
	private long benchmarkSceneBaselinePackets = 0;
	private long benchmarkSceneBaselinePages = 0;
	private long benchmarkSceneBaselineRecords = 0;
	private long benchmarkSceneBaselinePayloadBytes = 0;
	private long benchmarkMovementSnapshotPackets = 0;
	private long benchmarkMovementSnapshotRecords = 0;
	private long benchmarkMovementSnapshotPayloadBytes = 0;
	private long benchmarkNativeTerrainContextPackets = 0;
	private long benchmarkNativeTerrainSectorSlots = 0;
	private long benchmarkNativeTerrainAvailableSectors = 0;
	private long benchmarkNativeTerrainPayloadSectors = 0;
	private long benchmarkNativeTerrainReferencedSectors = 0;
	private long benchmarkNativeTerrainRawBytes = 0;
	private long benchmarkNativeTerrainWireBytes = 0;
	private long benchmarkNativeTerrainWireCacheRequests = 0;
	private long benchmarkNativeTerrainWireCacheHits = 0;
	private long benchmarkNativeTerrainWireCacheMisses = 0;
	private long benchmarkNativeTerrainWireBuildNanos = 0;
	private int benchmarkNativeTerrainWireCacheEntriesMax = 0;
	private long benchmarkSuppressedLegacySceneryPackets = 0;
	private long benchmarkSuppressedLegacySceneryRecords = 0;
	private long benchmarkSuppressedLegacyWallPackets = 0;
	private long benchmarkSuppressedLegacyWallRecords = 0;
	private long benchmarkCleanupTotal = 0;
	private long benchmarkWalkToActionsTotal = 0;
	private long benchmarkIncomingPacketsTotal = 0;
	private long benchmarkOutgoingPacketsTotal = 0;
	private long benchmarkOutgoingPayloadBytesTotal = 0;
	private int benchmarkMaxPlayers = 0;
	private int benchmarkMaxNpcs = 0;
	private int benchmarkMaxEvents = 0;
	private long benchmarkPvmMeleeEventTotal = 0;
	private long benchmarkPvmMeleeEventCalls = 0;
	private long benchmarkRangeEventTotal = 0;
	private long benchmarkRangeEventCalls = 0;
	private long benchmarkMagicCombatEventTotal = 0;
	private long benchmarkMagicCombatEventCalls = 0;
	private long benchmarkThrowingEventTotal = 0;
	private long benchmarkThrowingEventCalls = 0;
	private long benchmarkProjectileEventTotal = 0;
	private long benchmarkProjectileEventCalls = 0;
	private long benchmarkPluginTickEventTotal = 0;
	private long benchmarkPluginTickEventCalls = 0;
	private long benchmarkOtherEventTotal = 0;
	private long benchmarkOtherEventCalls = 0;

	private final ListeningExecutorService sqlLoggingThreadPool;
	private final ListeningExecutorService sqlThreadPool;
	private final ListeningExecutorService onlineMonitorThreadPool;

	private volatile boolean onlineReachable = true;

	public static final String rscConnectionHandlerId = "handler";

	static {
		Thread.currentThread().setName("InitThread");
		LogUtil.configure();
		LOGGER = LogManager.getLogger();
	}

	private SslContext sslcontext = null;

	private static String getDefaultConfigFileName() {
		return "myworld.conf";
	}

	public static Server startServer(final String confName) throws IOException {
		final long startTime = System.currentTimeMillis();
		final int activeCombatPairs = getIntegerSystemProperty(
			"openrsc.benchmarkActiveCombatPairs", 0);
		final int projectileCombatGroups = getIntegerSystemProperty(
			"openrsc.benchmarkProjectileCombatGroups", 0);
		final Server server = activeCombatPairs > 0 || projectileCombatGroups > 0
			? new Server(confName, SystemGameClock.INSTANCE,
				new DeterministicGameRandom(getLongSystemProperty(
					"openrsc.benchmarkCombatSeed", 0x5A17C0B4L)))
			: new Server(confName);
		if (!server.isRunning()) {
			server.start();
		}
		final long endTime = System.currentTimeMillis();
		final long bootTime = endTime - startTime;
		LOGGER.info(server.getName() + " started in " + bootTime + "ms");

		return server;
	}

	public static boolean closeProcess(final int seconds, final String message) {
		for (final Server server : serversList.values()) {
			if (server.shutdownEvent != null) {
				return false;
			}
		}

		for (final Server server : serversList.values()) {
			if (message != null) {
				String[] messages = message.split(": % %");
				for (final Player playerToUpdate : server.getWorld().getPlayers()) {
					if (playerToUpdate.getClientLimitations().supportsMessageBox) {
						ActionSender.sendBox(playerToUpdate, message, false);
					} else {
						for (String msg : messages) {
							playerToUpdate.playerServerMessage(MessageType.BROADCAST, msg);
						}
					}
				}
			}
			LOGGER.info("Server shutdown requested by closeProcess");
			server.shutdown(seconds);
		}

		return true;
	}

	public static void main(final String[] args) {
		LOGGER.info("Launching Game Server...");
		try {
			List<String> configurationFiles = new ArrayList<>();
			Optional.ofNullable(System.getProperty("conf")).ifPresent(files -> {
				configurationFiles.addAll(
						Arrays.stream(files.split(",")).map(file -> file + ".conf").collect(Collectors.toList())
				);
			});

			configurationFiles.addAll(Arrays.asList(args));

			if (configurationFiles.size() == 0) {
				LOGGER.info(
					"Server Configuration file not provided. Loading from {}.",
					getDefaultConfigFileName()
			);

				try {
					startServer(getDefaultConfigFileName());
				} catch (final Throwable t) {
					LOGGER.error("Exception starting server with default config", t);
					SystemUtil.exit(1);
				}
			} else {
				for (String configuration : configurationFiles) {
					try {
						startServer(configuration);
					} catch (final Throwable t) {
						LOGGER.error("Exception starting server with a configuration file", t);
						SystemUtil.exit(1);
					}
				}
			}

			while (serversList.size() > 0) {
				try {
					Thread.sleep(1000);
				} catch (final InterruptedException e) {
					e.printStackTrace();
				}

				for (final Server server : serversList.values()) {
					server.checkShutdown();
				}
			}
		} catch(Exception ex) {
			LOGGER.error("Exception starting server: ", ex);
			SystemUtil.exit(1);
		}

		LOGGER.info("Exiting server process...");
		SystemUtil.exit(0);
	}

	public Server(final String configFile) throws IOException {
		this(configFile, SystemGameClock.INSTANCE, ProductionGameRandom.INSTANCE,
			CombatDamageObserver.NONE);
	}

	/**
	 * Creates a server context with explicit gameplay runtime services. The
	 * production constructor supplies adapters over the existing system clock
	 * and random generator; isolated fixtures may supply deterministic sources.
	 */
	public Server(final String configFile, final GameClock gameClock,
			final GameRandom combatRandom) throws IOException {
		this(configFile, gameClock, combatRandom, CombatDamageObserver.NONE);
	}

	/**
	 * Creates a server context with an optional read-only combat damage observer.
	 * Production uses the inert observer; this constructor exists for isolated
	 * characterization fixtures and future explicitly enabled diagnostics.
	 */
	public Server(final String configFile, final GameClock gameClock,
			final GameRandom combatRandom,
			final CombatDamageObserver combatDamageObserver) throws IOException {
		this.gameClock = Objects.requireNonNull(gameClock, "gameClock");
		this.combatRandom = Objects.requireNonNull(combatRandom, "combatRandom");
		this.combatDamageObserver = Objects.requireNonNull(
			combatDamageObserver, "combatDamageObserver");
		this.resolvedDamageTransaction = new ResolvedDamageTransaction();
		final ServerConfigurationLoadResult loadedConfiguration =
			ServerConfigurationLoader.load(configFile);
		config = loadedConfiguration.getLegacyConfiguration();
		processNetworkConfiguration = loadedConfiguration.getProcessNetwork();
		diagnosticsConfiguration = loadedConfiguration.getDiagnostics();
		WorldBuilderMode.validate(getConfig());
		worldEditStorage = WorldEditStorageContext.create(getConfig());
		LOGGER.info("Server configuration loaded: " + getConfig().configFile);

		name = getConfig().SERVER_NAME;
		worldDayNightClock = new WorldDayNightClock();
		worldEditorSessions = new WorldEditorSessionManager(worldEditStorage);
		movementStutterDiagnostics = new MovementStutterDiagnostics(
			diagnosticsConfiguration.isMovementStutterDiagnostics(),
			10_000_000L,
			diagnosticsConfiguration.getMovementStutterSummarySeconds() * 1_000_000_000L,
			diagnosticsConfiguration.getMovementStutterPollOutlierMs() * 1_000_000L,
			diagnosticsConfiguration.getMovementStutterPollOutlierMs() * 1_000_000L,
			diagnosticsConfiguration.getMovementStutterTickOutlierMs() * 1_000_000L);
		if (movementStutterDiagnostics.isEnabled()) {
			LOGGER.info(
				"Movement stutter diagnostics enabled: summary={}s pollOutlier={}ms tickOutlier={}ms",
				box(diagnosticsConfiguration.getMovementStutterSummarySeconds()),
				box(diagnosticsConfiguration.getMovementStutterPollOutlierMs()),
				box(diagnosticsConfiguration.getMovementStutterTickOutlierMs()));
		}

		packetFilter = new RSCPacketFilter(this);

		pluginHandler = new PluginHandler(this);
		combatScriptLoader = new CombatScriptLoader(this);
		constants = new Constants(this);
		switch (getConfig().DB_TYPE){
			case MYSQL:
				database = new MySqlGameDatabase(this);
				break;
			case SQLITE:
				database = new SqliteGameDatabase(this);
				break;
			default:
				database = null;
				LOGGER.error("No database type");
				SystemUtil.exit(1);
				break;
		}

		final boolean wantDiscordBot = getConfig().WANT_DISCORD_BOT;
		final boolean wantDiscordAuctionUpdates = getConfig().WANT_DISCORD_AUCTION_UPDATES;
		final boolean wantDiscordMonitoringUpdates = getConfig().WANT_DISCORD_MONITORING_UPDATES;
		final boolean wantDiscordReportAbuseUpdates = getConfig().WANT_DISCORD_REPORT_ABUSE_UPDATES;
		final boolean wantDiscordStaffCommands = getConfig().WANT_DISCORD_STAFF_COMMANDS;
		final boolean wantDiscordNaughtyWordsUpdates = getConfig().WANT_DISCORD_NAUGHTY_WORDS_UPDATES;
		final boolean wantDiscordDowntimeReports = getConfig().WANT_DISCORD_DOWNTIME_REPORTS;
		final boolean wantDiscordGeneralLogs = getConfig().WANT_DISCORD_GENERAL_LOGGING;
		discordService = wantDiscordBot || wantDiscordAuctionUpdates || wantDiscordMonitoringUpdates || wantDiscordReportAbuseUpdates || wantDiscordStaffCommands || wantDiscordNaughtyWordsUpdates || wantDiscordDowntimeReports || wantDiscordGeneralLogs ? new DiscordService(this) : null;
		loginExecutor = new LoginExecutor(this);
		world = new World(this);
		gameEventHandler = new GameEventHandler(this);
		gameUpdater = new GameStateUpdater(this);
		gameLogger = new MySqlGameLogger(this, (MySqlGameDatabase)database);
		pcapLogger = new PcapLoggerService(this);
		entityHandler = new EntityHandler(this);
		achievementSystem = new AchievementSystem(this);
		playerService = new PlayerService(world, config, database);
		i18nService = new I18NService(this);
		ThreadPoolExecutor sqlLoggingExecutor = new ThreadPoolExecutor(
			1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()
		);
		sqlLoggingExecutor.allowCoreThreadTimeOut(false);
		sqlLoggingExecutor.setThreadFactory(new NamedThreadFactory(getName() + " : SqlLoggingThread", getConfig()));
		sqlLoggingThreadPool = MoreExecutors.listeningDecorator(sqlLoggingExecutor);
		ThreadPoolExecutor sqlExecutor = new ThreadPoolExecutor(
			1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()
		);
		sqlExecutor.allowCoreThreadTimeOut(false);
		sqlExecutor.setThreadFactory(new NamedThreadFactory(getName() + " : SqlThread", getConfig()));
		sqlThreadPool = MoreExecutors.listeningDecorator(sqlExecutor);
		ThreadPoolExecutor onlineMonitorExecutor = new ThreadPoolExecutor(
			1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()
		);
		onlineMonitorExecutor.allowCoreThreadTimeOut(false);
		onlineMonitorExecutor.setThreadFactory(new NamedThreadFactory(getName() + " : OnlineMonitorThread", getConfig()));
		onlineMonitorThreadPool = MoreExecutors.listeningDecorator(onlineMonitorExecutor);
		MessageFilter.loadGoodAndBadWordsFromDisk();
		StringUtil.loadJagGoodAndBadWordsFromDisk(); // static/hardcoded jag good and badwords for retro protocols

		maxItemId = 0;
		benchmarkTargetTicks = getIntegerSystemProperty("openrsc.benchmarkTicks", 0);
		benchmarkWarmupTicks = getIntegerSystemProperty("openrsc.benchmarkWarmupTicks", 5);
		benchmarkSyntheticPlayers = getIntegerSystemProperty("openrsc.benchmarkSyntheticPlayers", 0);
		benchmarkSyntheticClientVersion = getIntegerSystemProperty("openrsc.benchmarkSyntheticClientVersion", 235);
		benchmarkNpcProfiling = getBooleanSystemProperty("openrsc.benchmarkNpcProfiling", true);
		benchmarkDeepNpcProfiling = getBooleanSystemProperty("openrsc.benchmarkDeepNpcProfiling", true);
		benchmarkActiveCombatPairs = getIntegerSystemProperty(
			"openrsc.benchmarkActiveCombatPairs", 0);
		benchmarkProjectileCombatGroups = getIntegerSystemProperty(
			"openrsc.benchmarkProjectileCombatGroups", 0);
		benchmarkProjectileCombatFamily = System.getProperty(
			"openrsc.benchmarkProjectileCombatFamily", "ranged").trim();
		benchmarkCombatSeed = getLongSystemProperty(
			"openrsc.benchmarkCombatSeed", 0x5A17C0B4L);
	}

	private static int getIntegerSystemProperty(final String key, final int defaultValue) {
		final String configuredValue = System.getProperty(key);
		if (configuredValue == null || configuredValue.trim().isEmpty()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(configuredValue.trim());
		} catch (final NumberFormatException e) {
			LOGGER.warn("Ignoring invalid integer system property {}={}", key, configuredValue);
			return defaultValue;
		}
	}

	private static boolean getBooleanSystemProperty(final String key, final boolean defaultValue) {
		final String configuredValue = System.getProperty(key);
		if (configuredValue == null || configuredValue.trim().isEmpty()) {
			return defaultValue;
		}
		return Boolean.parseBoolean(configuredValue.trim());
	}

	private static long getLongSystemProperty(final String key, final long defaultValue) {
		final String configuredValue = System.getProperty(key);
		if (configuredValue == null || configuredValue.trim().isEmpty()) {
			return defaultValue;
		}
		try {
			return Long.decode(configuredValue.trim()).longValue();
		} catch (final NumberFormatException e) {
			LOGGER.warn("Ignoring invalid long system property {}={}", key, configuredValue);
			return defaultValue;
		}
	}

	private void initializeBenchmarkSyntheticPlayers() {
		if (benchmarkSyntheticPlayers <= 0) {
			return;
		}

		final int syntheticClientVersion = benchmarkSyntheticClientVersion;
		final ClientLimitations clientLimitations = ClientLimitations.forVersion(syntheticClientVersion);
		final int baseX = getConfig().RESPAWN_LOCATION_X;
		final int baseY = getConfig().RESPAWN_LOCATION_Y;
		final int columns = Math.max(1, (int) Math.ceil(Math.sqrt(benchmarkSyntheticPlayers)));

		for (int i = 0; i < benchmarkSyntheticPlayers; i++) {
			final int offsetX = i % columns;
			final int offsetY = i / columns;
			final long hash = DataConversions.usernameToHash("benchmark" + i);
			final Player player = new Player(getWorld(), hash);
			player.setAttribute("dummyplayer", true);
			player.setAttribute("benchmark_synthetic_player", true);
			player.setClientVersion(syntheticClientVersion);
			player.setClientLimitations(clientLimitations);
			player.setMale((i % 2) == 0);
			player.getSettings().setAppearance(new PlayerAppearance(
				i % 10,
				(i * 2) % 15,
				(i * 3) % 15,
				i % 5,
				1,
				2
			));
			// Synthetic benchmark players have no login loader to establish their
			// initial location authority. Use the same initialization boundary as
			// authenticated players before publishing them to the world.
			player.setInitialLocation(Point.location(baseX + offsetX, baseY + offsetY));
			getWorld().getPlayers().add(player);
			player.updateRegion();
			player.setBusy(false);
			player.setLoggedIn(true);
		}

		LOGGER.info("Registered {} benchmark synthetic players around {},{}",
			box(benchmarkSyntheticPlayers), box(baseX), box(baseY));
	}

	private void initializeActiveCombatBenchmark() {
		if (benchmarkActiveCombatPairs <= 0) {
			return;
		}
		if (!isFoundationBenchmarkEnabled()) {
			throw new IllegalStateException(
				"Active combat workload requires foundation benchmark mode");
		}
		if (!(getCombatRandom() instanceof DeterministicGameRandom)
				|| ((DeterministicGameRandom)getCombatRandom()).getSeed()
					!= benchmarkCombatSeed) {
			throw new IllegalStateException(
				"Active combat workload requires its configured deterministic random source");
		}
		activeCombatBenchmark = new ActiveCombatBenchmark(
			this, benchmarkActiveCombatPairs, benchmarkSyntheticClientVersion);
		activeCombatBenchmark.initialize();
		LOGGER.info("Registered {} deterministic active-combat pairs with seed {}",
			box(benchmarkActiveCombatPairs), box(benchmarkCombatSeed));
	}

	private void initializeProjectileCombatBenchmark() {
		if (benchmarkProjectileCombatGroups <= 0) return;
		if (benchmarkActiveCombatPairs > 0) {
			throw new IllegalStateException(
				"Projectile and melee combat benchmarks are mutually exclusive");
		}
		if (!isFoundationBenchmarkEnabled()) {
			throw new IllegalStateException(
				"Projectile combat workload requires foundation benchmark mode");
		}
		if (!(getCombatRandom() instanceof DeterministicGameRandom)
				|| ((DeterministicGameRandom)getCombatRandom()).getSeed()
					!= benchmarkCombatSeed) {
			throw new IllegalStateException(
				"Projectile combat workload requires its configured deterministic random source");
		}
		projectileCombatBenchmark = new ProjectileCombatBenchmark(
			this, benchmarkProjectileCombatGroups, benchmarkSyntheticClientVersion,
			benchmarkProjectileCombatFamily);
		projectileCombatBenchmark.initialize();
		LOGGER.info("Registered {} deterministic projectile-combat groups with seed {}",
			box(benchmarkProjectileCombatGroups), box(benchmarkCombatSeed));
	}

	private void auditPlayerOwnedItemIds() {
		if (!(getDatabase() instanceof JDBCDatabase)) {
			LOGGER.warn("PLAYER_ITEM_ID_AUDIT skipped: configured database does not expose JDBC queries");
			return;
		}

		final int maxDefinedItemId = getEntityHandler().getItemCount() - 1;
		final String prefix = getConfig().DB_TABLE_PREFIX;
		final String query =
			"SELECT * FROM (" +
				"SELECT 'bank' AS container, p.`username`, b.`slot`, b.`itemID` AS status_id, s.`catalogID`, s.`amount`, s.`noted` " +
				"FROM `" + prefix + "bank` b " +
				"JOIN `" + prefix + "itemstatuses` s ON b.`itemID` = s.`itemID` " +
				"JOIN `" + prefix + "players` p ON b.`playerID` = p.`id` " +
				"UNION ALL " +
				"SELECT 'inventory' AS container, p.`username`, i.`slot`, i.`itemID` AS status_id, s.`catalogID`, s.`amount`, s.`noted` " +
				"FROM `" + prefix + "invitems` i " +
				"JOIN `" + prefix + "itemstatuses` s ON i.`itemID` = s.`itemID` " +
				"JOIN `" + prefix + "players` p ON i.`playerID` = p.`id` " +
				"UNION ALL " +
				"SELECT 'equipped' AS container, p.`username`, -1 AS slot, e.`itemID` AS status_id, s.`catalogID`, s.`amount`, s.`noted` " +
				"FROM `" + prefix + "equipped` e " +
				"JOIN `" + prefix + "itemstatuses` s ON e.`itemID` = s.`itemID` " +
				"JOIN `" + prefix + "players` p ON e.`playerID` = p.`id`" +
			") owned_items " +
			"WHERE owned_items.`catalogID` < 0 " +
				"OR owned_items.`catalogID` > ? " +
				"OR owned_items.`catalogID` IN (?, ?) " +
			"ORDER BY owned_items.`username`, owned_items.`container`, owned_items.`slot`";

		int issues = 0;
		final int sampleLimit = 100;
		try (PreparedStatement statement = ((JDBCDatabase)getDatabase()).getConnection().prepareStatement(query)) {
			statement.setInt(1, maxDefinedItemId);
			statement.setInt(2, com.openrsc.server.constants.ItemId.UNOBTANIUM.id());
			statement.setInt(3, com.openrsc.server.constants.ItemId.UNOBTANIUM_STACKABLE.id());

			try (ResultSet results = statement.executeQuery()) {
				while (results.next()) {
					issues++;
					if (issues <= sampleLimit) {
						LOGGER.warn(
							"PLAYER_ITEM_ID_AUDIT issue={} player={} container={} slot={} statusId={} catalogId={} amount={} noted={} maxDefinedItemId={}",
							issues,
							results.getString("username"),
							results.getString("container"),
							results.getInt("slot"),
							results.getLong("status_id"),
							results.getInt("catalogID"),
							results.getInt("amount"),
							results.getInt("noted"),
							maxDefinedItemId
						);
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error("PLAYER_ITEM_ID_AUDIT failed", e);
			return;
		}

		if (issues == 0) {
			LOGGER.info("PLAYER_ITEM_ID_AUDIT passed: no placeholder or undefined item IDs in player-owned containers; maxDefinedItemId={}", maxDefinedItemId);
		} else {
			LOGGER.warn(
				"PLAYER_ITEM_ID_AUDIT found {} problematic player-owned item rows; logged first {}",
				issues,
				Math.min(issues, sampleLimit)
			);
		}
	}

	public void checkShutdown() {
		if (isShuttingDown()) {
			stop();
			if (isRestarting()) {
				start();
				restarting = false;
			}
			shuttingDown = false;
		}
	}

	public void start() {
		synchronized (running) {
			try {
				if (isRunning()) {
					return;
				}

				scheduledExecutor = Executors.newSingleThreadScheduledExecutor(
						new ServerAwareThreadFactory(
								getName() + " : GameThread",
								config
						)
				);
				scheduledExecutor.scheduleAtFixedRate(this, 0, 10, TimeUnit.MILLISECONDS);

				// Do not allow two servers to be started with the same name
				// We will bypass that if we are restarting because we never removed this server from the list.
				if (!isRestarting() && serversList.get(this.getName()) != null) {
					throw new IllegalArgumentException("Can not initialize. Server " + this.getName() + " already exists.");
				}

				LOGGER.info("Connecting to Database...");
				try {
					getDatabase().open();
				} catch (final Exception ex) {
					LOGGER.error("Exception opening database", ex);
					SystemUtil.exit(1);
				}
				LOGGER.info("Database Connection Completed");

				LOGGER.info("Checking For Database Structure Changes...");
				PatchApplier patchApplier = new JDBCPatchApplier(
						(JDBCDatabase) getDatabase(),
						getConfig().DB_TABLE_PREFIX
				);
				if (!patchApplier.applyPatches()) {
					LOGGER.error("Unable to apply database patches");
					SystemUtil.exit(1);
				}

				PidShuffler.init();

				if (getConfig().LOAD_PRERENDERED_SLEEPWORDS) {
					LOGGER.info("Loading Prerendered Sleepword Images...");
					CaptchaGenerator.loadPrerenderedCaptchas();
					LOGGER.info("Loaded " + CaptchaGenerator.prerenderedSleepwordsSize + " Prerendered Sleepword Images");
				}

				if (getConfig().LOAD_SPECIAL_PRERENDERED_SLEEPWORDS) {
					LOGGER.info("Loading Special Prerendered Sleepword Images...");
					CaptchaGenerator.loadSpecialPrerenderedCaptchas();
					LOGGER.info("Loaded " + CaptchaGenerator.prerenderedSleepwordsSpecialSize + " Special Prerendered Sleepword Images");
				}

				LOGGER.info("Loading Game Definitions...");
				getEntityHandler().load();
				LOGGER.info("Definitions Completed");

				WorldBuilderAccountProvisioner.provision(this);

				LOGGER.info("Loading Game State Updater...");
				getGameUpdater().load();
				LOGGER.info("Game State Updater Completed");

				LOGGER.info("Loading Game Event Handler...");
				getGameEventHandler().load();
				LOGGER.info("Game Event Handler Completed");

				LOGGER.info("Loading Combat Scripts...");
				getCombatScriptLoader().load();
				LOGGER.info("Combat Scripts Completed");

				LOGGER.info("Loading World...");
				getWorld().load();
				LOGGER.info("World Completed");

				LOGGER.info("Loading Plugins...");
				getPluginHandler().load();
				LOGGER.info("Plugins Completed");

				initializeBenchmarkSyntheticPlayers();
				initializeActiveCombatBenchmark();
				initializeProjectileCombatBenchmark();

				/*LOGGER.info("Loading Achievements...");
				getAchievementSystem().load();
				LOGGER.info("Achievements Completed");*/

				LOGGER.info("Loading LoginExecutor...");
				getLoginExecutor().start();
				LOGGER.info("LoginExecutor Completed");

				if (getDiscordService() != null) {
					LOGGER.info("Loading DiscordService...");
					getDiscordService().start();
					LOGGER.info("DiscordService Completed");
				}

				LOGGER.info("Loading GameLogger...");
				getGameLogger().start();
				LOGGER.info("GameLogger Completed");

				LOGGER.info("Loading PcapLogger...");
				getPcapLogger().start();
				LOGGER.info("PcapLogger Completed");

				LOGGER.info("Loading Packet Filter...");
				getPacketFilter().load();
				LOGGER.info("Packet Filter Completed");

                Crypto.init();

				maxItemId = getDatabase().getMaxItemID();
				LOGGER.info("Set max item ID to : " + maxItemId);
				auditPlayerOwnedItemIds();

				if (AuthenticatedNetworkBenchmarkProbe.isEnabled()) {
					if (!isFoundationBenchmarkEnabled()) {
						throw new IllegalStateException(
							"Authenticated network workload requires foundation benchmark mode");
					}
					AuthenticatedNetworkBenchmarkProbe.reset();
				}

				bossGroup = new NioEventLoopGroup(
						0,
						new NamedThreadFactory(getName() + " : IOBossThread", getConfig())
				);
				workerGroup = new NioEventLoopGroup(
						0,
						new NamedThreadFactory(getName() + " : IOWorkerThread", getConfig())
				);
				bossGroupWs = new NioEventLoopGroup(
					0,
					new NamedThreadFactory(getName() + " : IOBossWSThread", getConfig())
				);
				workerGroupWs = new NioEventLoopGroup(
					0,
					new NamedThreadFactory(getName() + " : IOWorkerWSThread", getConfig())
				);
				final ServerBootstrap bootstrap = new ServerBootstrap();
				final Server serverOwner = this;

				if (processNetworkConfiguration.isWebsocketsEnabled()) {
					if (!processNetworkConfiguration.getSslServerCertPath().trim().isEmpty() && !processNetworkConfiguration.getSslServerKeyPath().trim().isEmpty()) {
						LOGGER.info("Loading Websockets SSL cert...");
						try {
							setSSLContext(loadWebsocketSSLFiles(processNetworkConfiguration.getSslServerCertPath(), processNetworkConfiguration.getSslServerKeyPath(), null));
						} catch (CertificateExpiredException certExpiredEx) {
							LOGGER.error("Websocket certificate is expired and can no longer be used...! Make sure to replace it.");
						} catch (CertificateNotYetValidException certNotYetValidEx) {
							LOGGER.error("Websocket certificate is not yet valid...! Unable to use.");
						} catch (SSLException | CertificateException sslex) {
							LOGGER.error(sslex);
							LOGGER.error("Websocket certificate could not be parsed as a valid X.509 certificate file.");
						} catch (Exception ex) {
							LOGGER.error(ex);
							LOGGER.error("Generic error occurred while loading the websocket SSL certificate.");
						}
					} else {
						LOGGER.warn("No SSL certificate configured for WebSocket connections...!");
					}
				}

				if (!processNetworkConfiguration.isWebsocketsEnabled()) {
					bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(
						new ChannelInitializer<SocketChannel>() {
							@Override
							protected void initChannel(final SocketChannel channel) {
								final ChannelPipeline pipeline = channel.pipeline();
								if (AuthenticatedNetworkBenchmarkProbe.isEnabled()) {
									pipeline.addLast("authenticated-network-benchmark",
										new AuthenticatedNetworkBenchmarkProbe());
								}
								pipeline.addLast("decoder", new RSCMultiPortDecoder(RSCMultiPortDecoder.DecoderMode.TCP, serverOwner));
								pipeline.addLast(rscConnectionHandlerId, new RSCConnectionHandler(serverOwner));
							}
						}
					);

					bootstrap.childOption(ChannelOption.TCP_NODELAY, true);
					bootstrap.childOption(ChannelOption.SO_KEEPALIVE, false);
					bootstrap.childOption(ChannelOption.SO_RCVBUF, 10000);
					bootstrap.childOption(ChannelOption.SO_SNDBUF, 10000);
					getPluginHandler().handlePlugin(StartupTrigger.class);
					if (isFoundationBenchmarkEnabled()
						&& !AuthenticatedNetworkBenchmarkProbe.isEnabled()) {
						LOGGER.info("Foundation benchmark mode: network listeners are disabled");
					} else {
						try {
							serverChannel = bootstrap.bind(new InetSocketAddress(processNetworkConfiguration.getServerBindAddress(), processNetworkConfiguration.getServerPort())).sync();
							LOGGER.info("Game world is now online on {}:{}!", processNetworkConfiguration.getServerBindAddress(), box(processNetworkConfiguration.getServerPort()));
							LOGGER.info("RSA exponent: " + Crypto.getPublicExponent());
							LOGGER.info("RSA modulus: " + Crypto.getPublicModulus());
						} catch (final InterruptedException e) {
							LOGGER.error(e);
						}
					}
				} else {
					final ServerBootstrap bootstrapWs = new ServerBootstrap();

					bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(
						new ChannelInitializer<SocketChannel>() {
							@Override
							protected void initChannel(final SocketChannel channel) {
								final ChannelPipeline pipeline = channel.pipeline();
								if (AuthenticatedNetworkBenchmarkProbe.isEnabled()) {
									pipeline.addLast("authenticated-network-benchmark",
										new AuthenticatedNetworkBenchmarkProbe());
								}
								pipeline.addLast("decoder_tcp", new RSCMultiPortDecoder(RSCMultiPortDecoder.DecoderMode.TCP, serverOwner));
								pipeline.addLast(rscConnectionHandlerId, new RSCConnectionHandler(serverOwner));
							}
						}
					);

					bootstrap.childOption(ChannelOption.TCP_NODELAY, true);
					bootstrap.childOption(ChannelOption.SO_KEEPALIVE, false);
					bootstrap.childOption(ChannelOption.SO_RCVBUF, 10000);
					bootstrap.childOption(ChannelOption.SO_SNDBUF, 10000);

					bootstrapWs.group(bossGroupWs, workerGroupWs).channel(NioServerSocketChannel.class).childHandler(
						new ChannelInitializer<SocketChannel>() {
							@Override
							protected void initChannel(final SocketChannel channel) {
								final ChannelPipeline pipeline = channel.pipeline();
								pipeline.addLast("decoder_ws", new RSCMultiPortDecoder(RSCMultiPortDecoder.DecoderMode.WS, serverOwner));
								pipeline.addLast(rscConnectionHandlerId, new RSCConnectionHandler(serverOwner));
							}
						}
					);

					bootstrapWs.childOption(ChannelOption.TCP_NODELAY, true);
					bootstrapWs.childOption(ChannelOption.SO_KEEPALIVE, false);
					bootstrapWs.childOption(ChannelOption.SO_RCVBUF, 10000);
					bootstrapWs.childOption(ChannelOption.SO_SNDBUF, 10000);

					getPluginHandler().handlePlugin(StartupTrigger.class);
					if (isFoundationBenchmarkEnabled()
						&& !AuthenticatedNetworkBenchmarkProbe.isEnabled()) {
						LOGGER.info("Foundation benchmark mode: network listeners are disabled");
					} else {
						try {
							serverChannel = bootstrap.bind(new InetSocketAddress(processNetworkConfiguration.getServerBindAddress(), processNetworkConfiguration.getServerPort())).sync();
							LOGGER.info("Game world is now online on TCP {}:{}!", processNetworkConfiguration.getServerBindAddress(), box(processNetworkConfiguration.getServerPort()));
							if (!AuthenticatedNetworkBenchmarkProbe.isEnabled()) {
								serverChannelWs = bootstrapWs.bind(new InetSocketAddress(processNetworkConfiguration.getServerBindAddress(), processNetworkConfiguration.getWebsocketPort())).sync();
								LOGGER.info("Game world is now online on WS {}:{}! (webclient only)", processNetworkConfiguration.getServerBindAddress(), box(processNetworkConfiguration.getWebsocketPort()));
							}
							LOGGER.info("RSA exponent: " + Crypto.getPublicExponent());
							LOGGER.info("RSA modulus: " + Crypto.getPublicModulus());
						} catch (final InterruptedException e) {
							LOGGER.error(e);
						}
					}
				}

				// Only add this server to the active servers list if it's not already there
				if (!isRestarting()) {
					serversList.put(this.getName(), this);
				}

				lastTickTimestamp = serverStartedTime = System.nanoTime();
				running.set(true);
				WorldBuilderRuntimeControl.start(this);
			} catch (final Throwable t) {
				LOGGER.error("Exception in server start", t);
				rollbackPartialStart();
				throw new IllegalStateException("Server startup failed", t);
			}
		}
	}

	/**
	 * Best-effort reverse-order cleanup for a failed startup. This deliberately
	 * avoids the normal stop path: a failed start has not yet set {@code running}
	 * and may own only a prefix of the normal resources.
	 */
	private void rollbackPartialStart() {
		shutdownExecutor(scheduledExecutor);
		scheduledExecutor = null;
		closeChannel(serverChannel);
		closeChannel(serverChannelWs);
		serverChannel = null;
		serverChannelWs = null;
		shutdownEventLoop(workerGroupWs);
		shutdownEventLoop(bossGroupWs);
		shutdownEventLoop(workerGroup);
		shutdownEventLoop(bossGroup);
		workerGroupWs = null;
		bossGroupWs = null;
		workerGroup = null;
		bossGroup = null;
		if (database != null) {
			try {
				database.close();
			} catch (final Throwable cleanupFailure) {
				LOGGER.warn("Failed to close database during startup rollback", cleanupFailure);
			}
		}
		serversList.remove(getName());
		running.set(false);
	}

	private static void shutdownExecutor(final ScheduledExecutorService executor) {
		if (executor != null) executor.shutdownNow();
	}

	private static void closeChannel(final ChannelFuture channel) {
		if (channel != null && channel.channel() != null) channel.channel().close();
	}

	private static void shutdownEventLoop(final EventLoopGroup eventLoop) {
		if (eventLoop != null) eventLoop.shutdownGracefully();
	}

	public void stop() {
		synchronized (running) {
			try {
				if (!isRunning()) {
					return;
				}
				LOGGER.info("Server stop requested");
				getWorld().unloadPlayers();

				scheduledExecutor.shutdown();
				try {
					final boolean terminationResult = scheduledExecutor.awaitTermination(1, TimeUnit.MINUTES);
					if (!terminationResult) {
						LOGGER.error("Server thread termination failed");
						List<Runnable> skippedTasks = scheduledExecutor.shutdownNow();
						LOGGER.error("{} task(s) never commenced execution, forcing shutdown", skippedTasks.size());
					}
				} catch (final InterruptedException e) {
					LOGGER.error("Exception during task shutdown", e);
				}
				getLoginExecutor().stop();
				if (getDiscordService() != null) {
					getDiscordService().stop();
				}
				getGameLogger().stop();
				getGameUpdater().unload();
				getGameEventHandler().unload();
				getEntityHandler().unload();
				getPluginHandler().unload();
				getCombatScriptLoader().unload();
				getPacketFilter().unload();
				getPcapLogger().stop();
				//getAchievementSystem().unload();
				getWorld().unload();
				getDatabase().close();
				bossGroup.shutdownGracefully().sync();
				workerGroup.shutdownGracefully().sync();
				serverChannel.channel().closeFuture().sync();
				bossGroupWs.shutdownGracefully().sync();
				workerGroupWs.shutdownGracefully().sync();
				if (serverChannelWs != null) serverChannelWs.channel().closeFuture().sync();

				shutdownEvent = null;
				serverChannel = null;
				if (serverChannelWs != null) serverChannelWs = null;
				bossGroup = null;
				workerGroup = null;
				bossGroupWs = null;
				workerGroupWs = null;
				scheduledExecutor = null;

				maxItemId = 0;
				serverStartedTime = 0;
				lastIncomingPacketsDuration = 0;
				lastEventsDuration = 0;
				lastOutgoingPacketsDuration = 0;
				lastTickDuration = 0;
				timeLate = 0;
				lastTickTimestamp = 0;
				incomingTimePerPacketOpcode.clear();
				incomingCountPerPacketOpcode.clear();
				outgoingTimePerPacketOpcode.clear();
				outgoingCountPerPacketOpcode.clear();
				outgoingPayloadBytesPerPacketOpcode.clear();

				// Don't remove this server from the active servers list if we are just restarting.
				if (!isRestarting()) {
					serversList.remove(this.getName());
				}

				running.set(false);

				LOGGER.info("Server unloaded");
			} catch (final Throwable t) {
				LOGGER.error("Exception during Server stop()", t);
				SystemUtil.exit(1);
			}
		}
	}

	public long bench(final Runnable r) {
		final long start = System.nanoTime();
		r.run();
		final long end = System.nanoTime();
		return end - start;
	}

	public boolean isFoundationBenchmarkEnabled() {
		return benchmarkTargetTicks > 0;
	}

	public boolean isFoundationBenchmarkNpcProfilingEnabled() {
		return isFoundationBenchmarkEnabled() && benchmarkNpcProfiling;
	}

	public boolean isFoundationBenchmarkDeepNpcProfilingEnabled() {
		return isFoundationBenchmarkNpcProfilingEnabled() && benchmarkDeepNpcProfiling;
	}

	/** Records scheduler cost only for explicitly enabled benchmark samples. */
	public void recordFoundationBenchmarkEvent(final GameTickEvent event) {
		if (!isFoundationBenchmarkEnabled() || event == null
				|| getCurrentTick() <= benchmarkWarmupTicks) {
			return;
		}
		final long duration = event.getLastEventDuration();
		if (event instanceof PvmMeleeEvent) {
			benchmarkPvmMeleeEventTotal += duration;
			benchmarkPvmMeleeEventCalls++;
		} else if (event instanceof RangeEvent) {
			benchmarkRangeEventTotal += duration;
			benchmarkRangeEventCalls++;
		} else if (event instanceof MagicCombatEvent) {
			benchmarkMagicCombatEventTotal += duration;
			benchmarkMagicCombatEventCalls++;
		} else if (event instanceof ThrowingEvent) {
			benchmarkThrowingEventTotal += duration;
			benchmarkThrowingEventCalls++;
		} else if (event instanceof ProjectileEvent) {
			benchmarkProjectileEventTotal += duration;
			benchmarkProjectileEventCalls++;
		} else if (event instanceof PluginTickEvent) {
			benchmarkPluginTickEventTotal += duration;
			benchmarkPluginTickEventCalls++;
		} else {
			benchmarkOtherEventTotal += duration;
			benchmarkOtherEventCalls++;
		}
	}

	@Override
	public void run() {
		LogUtil.populateThreadContext(getConfig());
		synchronized (running) {
			try {
				this.timeLate = System.nanoTime() - lastTickTimestamp;
				if (getTimeLate() >= getConfig().GAME_TICK * 1000000L) {
					this.timeLate -= getConfig().GAME_TICK * 1000000L;

					// Doing the set in two stages here such that the whole tick has access to the same values for profiling information.
					this.lastTickDuration = bench(() -> {
						try {
							resetBenchmarkDurations();
							incrementLastEventsDuration(getGameEventHandler().processNonPlayerEvents());
							incrementLastWorldUpdateDuration(getGameUpdater().updateWorld());
							incrementLastProcessNpcsDuration(getGameUpdater().processNpcs());
							if (config.SHUFFLE_PID_ORDER) {
								for (int curPid : PidShuffler.pidProcessingOrder) {
									Player player = getWorld().getPlayer(curPid);
									if (player != null) {
										player.processTick();
									}
								}
								if (getCurrentTick() % config.SHUFFLE_PID_ORDER_INTERVAL == 0) {
									PidShuffler.shuffle();
								}
							} else {
								for (final Player player : getWorld().getPlayers()) {
									player.processTick();
								}
							}

							incrementLastExecuteWalkToActionsDuration(getGameUpdater().executePidlessCatching());
							incrementLastProcessMessageQueuesDuration(getWorld().processGlobalMessageQueue());

							checkAndRespondToServerNotHavingAccessToInternet();

							for (final Player player : getWorld().getPlayers()) {
								player.processLogout();
							}
							for (final Player player : getWorld().getPlayers()) {
								player.sendUpdates();
							}

							incrementLastDoCleanupDuration(getGameUpdater().doCleanup());
							getGameEventHandler().cleanupEvents();

							// TODO: remove this vacuum service. It is for debugging.
							getWorld().getNpcs().forEachLive(npc -> {
								boolean deadReciprocalCombat = npc.getCombatEvent() != null && !npc.getCombatEvent().isRunning();
								boolean deadPvmCombat = npc.getPvmMeleeEvent() != null && !npc.getPvmMeleeEvent().isRunning();
								if (deadReciprocalCombat || deadPvmCombat) {
									if (npc.getOpponent() != null) {
										if (config.WANT_DISCORD_GENERAL_LOGGING) {
											getDiscordService().playerLog((Player)npc.getOpponent(), "An NPC with ID " + npc.getID() + ":" + npc.getIndex() + " @ " + npc.getX() + "," + npc.getY() + " was stuck, and should be unstuck now. You have some debugging still to do.");
										}

										if (npc.getSkills().getLevel(Skill.HITS.id()) <= 0) {
											npc.killedBy(npc.getOpponent()); // possibly not actually who killed them e.g. ranged/mage kill
										}
										npc.resetCombatEvent();
									}
								}
							});
							// TODO: end remove section

						} catch (final Throwable t) {
							LOGGER.error("Exception in server tick", t);
						}
					});

					monitorTickPerformance();
					if (movementStutterDiagnostics.isEnabled()) {
						final long diagnosticNow = System.nanoTime();
						movementStutterDiagnostics.recordWorldTick(
							diagnosticNow,
							getCurrentTick(),
							lastTickDuration,
							new MovementStutterDiagnostics.TickStages(
								lastWorldUpdateDuration,
								lastEventsDuration,
								lastProcessPlayersDuration,
								lastProcessNpcsDuration,
								lastUpdateClientsDuration,
								lastOutgoingPacketsDuration,
								lastDoCleanupDuration));
						emitMovementStutterDiagnostics(diagnosticNow);
					}
					recordBenchmarkTick();

					dailyShutdownEvent();
					// not ideal location but is safe guarded to only keep 1
					resetEvent();

					// Set us to be in the next tick.
					advanceTicks(1);

					// allow more players to login now that a tick has been processed
					getWorld().getServer().getLoginExecutor().resetRequestsThisTick();

					// Clear out the outgoing and incoming packet processing time frames
					incomingTimePerPacketOpcode.clear();
					incomingCountPerPacketOpcode.clear();
					outgoingTimePerPacketOpcode.clear();
					outgoingCountPerPacketOpcode.clear();
					outgoingPayloadBytesPerPacketOpcode.clear();

					//LOGGER.info("Tick " + getCurrentTick() + " processed.");
				} else {
					if (getConfig().WANT_CUSTOM_WALK_SPEED) {
						final boolean movementDiagnosticsEnabled = movementStutterDiagnostics.isEnabled();
						final long movementPollStarted = movementDiagnosticsEnabled ? System.nanoTime() : 0L;
						World world = getWorld();
						final ArrayList<Player> movedPlayers = new ArrayList<>();
						for (final Player p : getWorld().getPlayers()) {
							final int oldX = p.getX();
							final int oldY = p.getY();
							p.updatePosition();
							getGameUpdater().executeWalkToActions(p);
							if (p.getX() != oldX || p.getY() != oldY) {
								movedPlayers.add(p);
							}
						}

						final boolean traceNpcMovement = shouldTraceNpcMovement(world);
						final long npcMovementTraceNow = traceNpcMovement ? System.currentTimeMillis() : 0L;
						final ArrayList<Npc> movedNpcs = new ArrayList<>();
						world.getNpcs().forEachLive(npc -> {
							final int oldX = npc.getX();
							final int oldY = npc.getY();
							final long lastMoveLoop = traceNpcMovement ? npc.getAttribute("debug_npc_last_move_loop", 0L) : 0L;
							final int pathSizeBefore = traceNpcMovement
								? (npc.getWalkingQueue().path == null ? -1 : npc.getWalkingQueue().path.size())
								: -1;
							npc.updateMovementOnly();
							if (npc.getX() != oldX || npc.getY() != oldY) {
								movedNpcs.add(npc);
								if (traceNpcMovement) {
									npc.setAttribute("debug_npc_last_move_delta_ms", lastMoveLoop <= 0 ? -1L : npcMovementTraceNow - lastMoveLoop);
									npc.setAttribute("debug_npc_last_move_from", oldX + "," + oldY);
									npc.setAttribute("debug_npc_last_move_path_before", pathSizeBefore);
									npc.setAttribute("debug_npc_last_move_path_after",
										npc.getWalkingQueue().path == null ? -1 : npc.getWalkingQueue().path.size());
								}
							}
							if (traceNpcMovement) {
								npc.setAttribute("debug_npc_last_move_loop", npcMovementTraceNow);
							}
						});

						if (!movedPlayers.isEmpty() || !movedNpcs.isEmpty()) {
							int queuedPackets = 0;
							int maxPlayerQueue = 0;
							int backpressuredPlayers = 0;
							for (final Player p : world.getPlayers()) {
								boolean sentMovementPacket = getGameUpdater().sendMovementUpdatePacket(p, movedPlayers, movedNpcs);
								sentMovementPacket |= getGameUpdater().sendMovementSnapshotPacket(p, movedPlayers, movedNpcs);
								if (sentMovementPacket) {
									if (movementDiagnosticsEnabled) {
										final int playerQueue = p.getOutgoingPacketQueueSize();
										queuedPackets += playerQueue;
										maxPlayerQueue = Math.max(maxPlayerQueue, playerQueue);
										if (!p.isOutgoingChannelWritable()) {
											backpressuredPlayers++;
										}
									}
									p.processOutgoingPackets();
								}
							}
							if (movementDiagnosticsEnabled) {
								final long movementPollFinished = System.nanoTime();
								movementStutterDiagnostics.recordMovementPoll(
									movementPollStarted,
									movementPollFinished,
									movedPlayers.size(),
									movedNpcs.size(),
									queuedPackets,
									maxPlayerQueue,
									backpressuredPlayers);
								emitMovementStutterDiagnostics(movementPollFinished);
							}
						} else if (movementDiagnosticsEnabled) {
							final long movementPollFinished = System.nanoTime();
							movementStutterDiagnostics.recordMovementPoll(
								movementPollStarted,
								movementPollFinished,
								0,
								0,
								0,
								0,
								0);
							emitMovementStutterDiagnostics(movementPollFinished);
						}

						final long now = System.currentTimeMillis();
						for (final Player p : world.getPlayers()) {
							if (!p.getAttribute("debug_npc_trace", false)) {
								continue;
							}
							int budget = p.getAttribute("debug_npc_trace_budget", 0);
							if (budget <= 0) {
								continue;
							}
							final long lastLog = p.getAttribute("debug_npc_trace_last_custom_log", 0L);
							if (now - lastLog < 250L) {
								continue;
							}
							final int radius = p.getAttribute("debug_npc_trace_radius", 12);
							final ArrayList<String> samples = new ArrayList<>();
							for (final Npc npc : movedNpcs) {
								if (p.withinRange(npc, radius)) {
									samples.add(describeNpcMovementTrace(npc));
									if (samples.size() >= 6) {
										break;
									}
								}
							}
							LOGGER.info("NPC_TRACE customLoop player={} budget={} movedNearby={} samples={}",
								p.getUsername(), budget, samples.size(), samples);
							p.setAttribute("debug_npc_trace_budget", budget - 1);
							p.setAttribute("debug_npc_trace_last_custom_log", now);
						}
					}
				}
			} catch (final Throwable t) {
				LOGGER.error("Exception in Server run()", t);
			}
		}
	}

	private void emitMovementStutterDiagnostics(final long nowNanos) {
		for (final String line : movementStutterDiagnostics.drainIfDue(nowNanos)) {
			LOGGER.info("MOVEMENT_STUTTER_DIAGNOSTIC {}", line);
		}
	}

	private boolean shouldTraceNpcMovement(final World world) {
		for (final Player p : world.getPlayers()) {
			if (p.getAttribute("debug_npc_trace", false)
				&& p.getAttribute("debug_npc_trace_budget", 0) > 0) {
				return true;
			}
		}
		return false;
	}

	private String describeNpcMovementTrace(final Npc npc) {
		final Point location = npc.getLocation();
		final TileValue tile = npc.getWorld().getTile(location);
		final int mask = tile == null ? -1 : tile.traversalMask & 0xff;
		final boolean fullBlock = tile == null || (tile.traversalMask & CollisionFlag.FULL_BLOCK) != 0;
		final boolean spawnBounds = location.inBounds(npc.getLoc().minX(), npc.getLoc().minY(), npc.getLoc().maxX(), npc.getLoc().maxY());
		final boolean roamBounds = npc.inRoamBounds(location);
		final boolean scenery = npcOnBlockingScenery(npc);
		final int pathSize = npc.getWalkingQueue().path == null ? -1 : npc.getWalkingQueue().path.size();
		final String from = npc.getAttribute("debug_npc_last_move_from", "?,?");
		final long delta = npc.getAttribute("debug_npc_last_move_delta_ms", -1L);
		final int pathBefore = npc.getAttribute("debug_npc_last_move_path_before", -1);
		final int pathAfter = npc.getAttribute("debug_npc_last_move_path_after", -1);
		return npc.getID() + ":" + npc.getIndex()
			+ " " + npc.getDef().getName()
			+ " " + from + "->" + npc.getX() + "," + npc.getY()
			+ " dt=" + delta
			+ " sprite=" + npc.getSprite()
			+ " moved=" + npc.hasMoved()
			+ " combat=" + npc.inCombat()
			+ " chasing=" + npc.isChasing()
			+ " spawn=" + spawnBounds
			+ " roam=" + roamBounds
			+ " fullBlock=" + fullBlock
			+ " scenery=" + scenery
			+ " mask=" + mask
			+ " path=" + pathSize + "/" + pathBefore + ">" + pathAfter;
	}

	private boolean npcOnBlockingScenery(final Npc npc) {
		for (final GameObject object : npc.getWorld().getRegionManager().getLocalObjects(npc)) {
			if (!object.isScenery() || object.getGameObjectDef().getType() == 0) {
				continue;
			}
			int width;
			int height;
			if (object.getDirection() == 0 || object.getDirection() == 4) {
				width = object.getGameObjectDef().getWidth();
				height = object.getGameObjectDef().getHeight();
			} else {
				width = object.getGameObjectDef().getHeight();
				height = object.getGameObjectDef().getWidth();
			}
			if (npc.getX() >= object.getX() && npc.getX() < object.getX() + width
				&& npc.getY() >= object.getY() && npc.getY() < object.getY() + height) {
				return true;
			}
		}
		return false;
	}

	private void checkAndRespondToServerNotHavingAccessToInternet() {
		if (getConfig().MONITOR_IP.equals("localhost") || !getConfig().MONITOR_ONLINE) {
			return;
		}

		try {
			submitOnlineMonitor(() -> {
				try {
					LOGGER.info("Checking monitor IP " + getConfig().MONITOR_IP);
					InetAddress address = InetAddress.getByName(getConfig().MONITOR_IP);
					this.onlineReachable = address.isReachable(getConfig().MONITOR_IP_TIMEOUT);
				} catch (IOException ex) {
					LOGGER.catching(ex);
					onlineReachable = false;
				}
			});

			boolean unloadedPlayers = false;
			final boolean OFFLINE_THIS_TICK = !onlineReachable;
			int playersOnline = 0;
			long timeOffline = System.currentTimeMillis();
			if (!onlineReachable) {
				// calculate number of affected users
				for (Player p : getWorld().getPlayers()) {
					playersOnline++;
				}
			}
			while (!onlineReachable) {
				LOGGER.info(getConfig().SERVER_NAME + " has been offline from " + getConfig().MONITOR_IP +  " for " + (System.currentTimeMillis() - timeOffline) + " millis!");
				// after 10 seconds offline, give up and unregister all players
				if (System.currentTimeMillis() - timeOffline > 10000 && !unloadedPlayers) {
					LOGGER.info(getConfig().SERVER_NAME + " server offline for over 10 seconds, unloading all players...");
					getWorld().unloadPlayers();
					LOGGER.info("unloaded all players on " + getConfig().SERVER_NAME + " as a result of being offline for over 10 seconds.");
					unloadedPlayers = true;
				}
				onlineReachable =  InetAddress.getByName(getConfig().MONITOR_IP).isReachable(getConfig().MONITOR_IP_TIMEOUT);
			}

			// now back online
			if (OFFLINE_THIS_TICK) {
				LOGGER.info(getDowntimeReportForLogFile(timeOffline, System.currentTimeMillis(), unloadedPlayers, playersOnline));
				// tell discord we were offline, for how long, and that we are now back online.
				if (getDiscordService() != null) {
					getDiscordService().reportDowntimeToDiscord(timeOffline, System.currentTimeMillis(), unloadedPlayers, playersOnline);
				}

				if (getConfig().MONITOR_AUTOMATIC_SHUTDOWN) {
					LOGGER.info("Online connection restored, shutting down now...");
					try {
						String restartFileName = getConfig().configFile + "_shutdown.txt";
						File restartFile = new File(restartFileName);
						if (!restartFile.exists()) {
							restartFile.createNewFile();
						}
						LOGGER.info("Created shutdown file: " + restartFileName);
						System.exit(0); // Shutdown the server
					} catch (IOException e) {
						LOGGER.fatal("Failed to create shutdown file after automatic shutdown: " + e.getMessage());
					}
				}
			}
		} catch(IOException ex) {
			ex.printStackTrace();
		}
	}

	private String getDowntimeReportForLogFile(long startmillis, long endmillis, boolean unloaded, int onlineCount) {
		long downtime = endmillis - startmillis;
		StringBuilder mainContent = new StringBuilder();
		mainContent.append(getName());
		mainContent.append(" is now back online...!\n\n");

		mainContent.append("The server detected it was offline at ");
		mainContent.append(new java.text.SimpleDateFormat("MMMM d, yyyy hh:mm a").format(new java.util.Date(startmillis)));
		mainContent.append(" (");
		mainContent.append(startmillis);
		mainContent.append(") and recovered at ");
		mainContent.append(new java.text.SimpleDateFormat("MMMM d, yyyy hh:mm a").format(new java.util.Date(endmillis)));
		mainContent.append(" (");
		mainContent.append(endmillis);
		mainContent.append("). A total downtime of ");

		if (downtime > 60000) {
			mainContent.append(downtime / 60000);
			mainContent.append(" minutes.");
		} else {
			mainContent.append(downtime / 1000);
			mainContent.append(" seconds.");
		}

		if (unloaded) {
			mainContent.append("\n\nBecause the downtime was so long, all players were unloaded from the server, after 10 seconds.");
		}

		mainContent.append("\n\n");
		mainContent.append(onlineCount);
		mainContent.append(" accounts were logged in at the time of the outage.");

		return mainContent.toString();
	}
	private void dailyShutdownEvent() {
		try {
			if (getConfig().WANT_AUTO_SERVER_SHUTDOWN) {
				LOGGER.info("Daily shutdown event requested and enabled");
				List<GameTickEvent> events = getWorld().getServer().getGameEventHandler().getEvents();
				for (GameTickEvent event : events) {
					if (!(event instanceof DailyShutdownEvent)) continue;

					// There is already a daily shutdown running!;
					// do nothing!
					return;
				}
				getWorld().getServer().getGameEventHandler().add(new DailyShutdownEvent(getWorld(), 1, getConfig().RESTART_HOUR));
				/*int hour = LocalDateTime.now().getHour();
				int minute = LocalDateTime.now().getMinute();

				if (hour == getConfig().RESTART_HOUR && minute == 0)
					getWorld().getServer().shutdown(300);*/
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void resetEvent() {
		if (getConfig().WANT_RESET_EVENT) {
			List<GameTickEvent> events = getWorld().getServer().getGameEventHandler().getEvents();
			for (GameTickEvent event : events) {
				if (!(event instanceof HourlyResetEvent)) continue;

				// There is already an hourly reset running!;
				// do nothing!
				return;
			}
			getWorld().getServer().getGameEventHandler().add(new HourlyResetEvent(getWorld(), 48, 0));
		}
	}

	private void monitorTickPerformance() {
		// Store the current tick because we can modify it by calling skipTicks()
		final long currentTick = getCurrentTick();
		// Check if processing game tick took longer than the tick
		final boolean isLastTickLate = (getLastTickDuration() / 1000000) > getConfig().GAME_TICK;
		final long ticksLate = (getTimeLate() / 1000000) / getConfig().GAME_TICK;
		final boolean isServerLate = ticksLate >= 1;

		if (isLastTickLate) {
			// Current tick processing took too long.
			final String message = "Tick " + currentTick + " is late: " +
				(getLastTickDuration() / 1000000) + "ms " +
				(getLastIncomingPacketsDuration() / 1000000) + "ms " +
				(getLastEventsDuration() / 1000000) + "ms " +
				(getLastOutgoingPacketsDuration() / 1000000) + "ms";

			sendMonitoringWarning(message, true);
		}
		if (isServerLate) {
			// Server fell behind, skip ticks
			advanceTicks(ticksLate);
			final String ticksSkipped = ticksLate > 1 ? "ticks (" + (currentTick+1) + " - " + (currentTick+ticksLate) + ")" : "tick (" + (currentTick+ticksLate) + ")";
			final String message = "Tick " + currentTick + " " + getTimeLate() / 1000000 + "ms behind. Skipping " + ticksLate + " " + ticksSkipped;
			sendMonitoringWarning(message, false);
		}
	}

	private void recordBenchmarkTick() {
		if (benchmarkTargetTicks <= 0) {
			return;
		}

		final long currentTick = getCurrentTick();
		if (currentTick <= benchmarkWarmupTicks) {
			return;
		}

		benchmarkSamples++;
		benchmarkTickTotal += getLastTickDuration();
		benchmarkTickMax = Math.max(benchmarkTickMax, getLastTickDuration());
		benchmarkEventsTotal += getLastEventsDuration();
		benchmarkWorldUpdateTotal += getLastWorldUpdateDuration();
		benchmarkProcessPlayersTotal += getLastProcessPlayersDuration();
		benchmarkProcessNpcsTotal += getLastProcessNpcsDuration();
		benchmarkProcessNpcUnregisterTotal += getLastProcessNpcUnregisterDuration();
		benchmarkProcessNpcBehaviorTotal += getLastProcessNpcBehaviorDuration();
		benchmarkProcessNpcMovementTotal += getLastProcessNpcMovementDuration();
		benchmarkNpcIdleThrottleSkipped += getLastNpcIdleThrottleSkipped();
		benchmarkNpcBehaviorRoamTotal += getLastNpcBehaviorRoamDuration();
		benchmarkNpcBehaviorAggroTotal += getLastNpcBehaviorAggroDuration();
		benchmarkNpcBehaviorCombatTotal += getLastNpcBehaviorCombatDuration();
		benchmarkNpcBehaviorTackleTotal += getLastNpcBehaviorTackleDuration();
		benchmarkNpcBehaviorRetreatTotal += getLastNpcBehaviorRetreatDuration();
		benchmarkNpcRoamEligibilityTotal += getLastNpcRoamEligibilityDuration();
		benchmarkNpcRoamAggroScanTotal += getLastNpcRoamAggroScanDuration();
		benchmarkNpcRoamTackleScanTotal += getLastNpcRoamTackleScanDuration();
		benchmarkNpcRoamRandomWalkTotal += getLastNpcRoamRandomWalkDuration();
		benchmarkMessageQueuesTotal += getLastProcessMessageQueuesDuration();
		benchmarkUpdateClientsTotal += getLastUpdateClientsDuration();
		benchmarkUpdatePlayersTotal += getLastUpdatePlayersDuration();
		benchmarkUpdatePlayerAppearancesTotal += getLastUpdatePlayerAppearancesDuration();
		benchmarkUpdateNpcsTotal += getLastUpdateNpcsDuration();
		benchmarkUpdateNpcAppearancesTotal += getLastUpdateNpcAppearancesDuration();
		benchmarkUpdateGameObjectsTotal += getLastUpdateGameObjectsDuration();
		benchmarkUpdateWallObjectsTotal += getLastUpdateWallObjectsDuration();
		benchmarkUpdateGroundItemsTotal += getLastUpdateGroundItemsDuration();
		benchmarkUpdateTimeoutsTotal += getLastUpdateTimeoutsDuration();
		benchmarkUpdateAppearanceKeepaliveTotal += getLastUpdateAppearanceKeepaliveDuration();
		benchmarkVisibilitySnapshotTotal += getLastVisibilitySnapshotDuration();
		benchmarkVisibilitySnapshotSamples += getLastVisibilitySnapshotSamples();
		benchmarkVisiblePlayersTotal += getLastVisiblePlayersTotal();
		benchmarkVisibleNpcsTotal += getLastVisibleNpcsTotal();
		benchmarkVisibleSceneryTotal += getLastVisibleSceneryTotal();
		benchmarkVisibleWallObjectsTotal += getLastVisibleWallObjectsTotal();
		benchmarkVisibleGroundItemsTotal += getLastVisibleGroundItemsTotal();
		benchmarkVisiblePlayersMax = Math.max(benchmarkVisiblePlayersMax, getLastVisiblePlayersMax());
		benchmarkVisibleNpcsMax = Math.max(benchmarkVisibleNpcsMax, getLastVisibleNpcsMax());
		benchmarkVisibleSceneryMax = Math.max(benchmarkVisibleSceneryMax, getLastVisibleSceneryMax());
		benchmarkVisibleWallObjectsMax = Math.max(benchmarkVisibleWallObjectsMax, getLastVisibleWallObjectsMax());
		benchmarkVisibleGroundItemsMax = Math.max(benchmarkVisibleGroundItemsMax, getLastVisibleGroundItemsMax());
		benchmarkVisibilityRegionCacheRequests += getLastVisibilityRegionCacheRequests();
		benchmarkVisibilityRegionCacheHits += getLastVisibilityRegionCacheHits();
		benchmarkVisibilityRegionCacheMisses += getLastVisibilityRegionCacheMisses();
		benchmarkVisibilityObjectCacheRequests += getLastVisibilityObjectCacheRequests();
		benchmarkVisibilityObjectCacheHits += getLastVisibilityObjectCacheHits();
		benchmarkVisibilityObjectCacheMisses += getLastVisibilityObjectCacheMisses();
		benchmarkVisibilityObjectCacheClears += getLastVisibilityObjectCacheClears();
		benchmarkVisibilityObjectCacheEntriesCleared += getLastVisibilityObjectCacheEntriesCleared();
		benchmarkVisibilityObjectSnapshotCacheRequests += getLastVisibilityObjectSnapshotCacheRequests();
		benchmarkVisibilityObjectSnapshotCacheHits += getLastVisibilityObjectSnapshotCacheHits();
		benchmarkVisibilityObjectSnapshotCacheMisses += getLastVisibilityObjectSnapshotCacheMisses();
		benchmarkVisibilityTickSnapshotCacheRequests += getLastVisibilityTickSnapshotCacheRequests();
		benchmarkVisibilityTickSnapshotCacheHits += getLastVisibilityTickSnapshotCacheHits();
		benchmarkVisibilityTickSnapshotCacheMisses += getLastVisibilityTickSnapshotCacheMisses();
		benchmarkVisibilityShadowTotal += getLastVisibilityShadowDuration();
		benchmarkVisibilityShadowSamples += getLastVisibilityShadowSamples();
		benchmarkVisibilityShadowMismatchSamples += getLastVisibilityShadowMismatchSamples();
		benchmarkVisibilityShadowPlayerMismatches += getLastVisibilityShadowPlayerMismatches();
		benchmarkVisibilityShadowNpcMismatches += getLastVisibilityShadowNpcMismatches();
		benchmarkVisibilityShadowGameObjectMismatches += getLastVisibilityShadowGameObjectMismatches();
		benchmarkVisibilityShadowGroundItemMismatches += getLastVisibilityShadowGroundItemMismatches();
		benchmarkVisibilityShadowMobRegionsMax = Math.max(benchmarkVisibilityShadowMobRegionsMax, getLastVisibilityShadowMobRegionsMax());
		benchmarkVisibilityShadowObjectRegionsMax = Math.max(benchmarkVisibilityShadowObjectRegionsMax, getLastVisibilityShadowObjectRegionsMax());
		benchmarkSceneBaselinePackets += lastSceneBaselinePackets;
		benchmarkSceneBaselinePages += lastSceneBaselinePages;
		benchmarkSceneBaselineRecords += lastSceneBaselineRecords;
		benchmarkSceneBaselinePayloadBytes += lastSceneBaselinePayloadBytes;
		benchmarkSuppressedLegacySceneryPackets += lastSuppressedLegacySceneryPackets;
		benchmarkSuppressedLegacySceneryRecords += lastSuppressedLegacySceneryRecords;
		benchmarkSuppressedLegacyWallPackets += lastSuppressedLegacyWallPackets;
		benchmarkSuppressedLegacyWallRecords += lastSuppressedLegacyWallRecords;
		benchmarkCleanupTotal += getLastDoCleanupDuration();
		benchmarkWalkToActionsTotal += getLastExecuteWalkToActionsDuration();
		benchmarkIncomingPacketsTotal += getLastIncomingPacketsDuration();
		benchmarkOutgoingPacketsTotal += getLastOutgoingPacketsDuration();
		benchmarkOutgoingPayloadBytesTotal += getLastOutgoingPayloadBytes();
		benchmarkMaxPlayers = Math.max(benchmarkMaxPlayers, getWorld().getPlayers().size());
		benchmarkMaxNpcs = Math.max(benchmarkMaxNpcs, getWorld().getNpcs().size());
		benchmarkMaxEvents = Math.max(benchmarkMaxEvents, getGameEventHandler().getEvents().size());

		if (benchmarkSamples >= benchmarkTargetTicks) {
			LOGGER.info(buildBenchmarkSummary());
			SystemUtil.exit(0);
		}
	}

	private synchronized String buildBenchmarkSummary() {
		final long samples = Math.max(1, benchmarkSamples);
		final long visibilitySamples = Math.max(1, benchmarkVisibilitySnapshotSamples);
		return "FOUNDATION_BENCHMARK "
			+ "samples=" + benchmarkSamples
			+ " warmupTicks=" + benchmarkWarmupTicks
			+ " syntheticPlayers=" + benchmarkSyntheticPlayers
			+ " syntheticClientVersion=" + benchmarkSyntheticClientVersion
			+ " npcProfiling=" + benchmarkNpcProfiling
			+ " deepNpcProfiling=" + benchmarkDeepNpcProfiling
			+ " activeCombatConfiguredPairs=" + benchmarkActiveCombatPairs
			+ " projectileCombatConfiguredGroups=" + benchmarkProjectileCombatGroups
			+ " projectileCombatConfiguredFamily=" + benchmarkProjectileCombatFamily
			+ " avgTickMs=" + nanosToMillis(benchmarkTickTotal / samples)
			+ " maxTickMs=" + nanosToMillis(benchmarkTickMax)
			+ " avgEventsMs=" + nanosToMillis(benchmarkEventsTotal / samples)
			+ " avgWorldUpdateMs=" + nanosToMillis(benchmarkWorldUpdateTotal / samples)
			+ " avgProcessPlayersMs=" + nanosToMillis(benchmarkProcessPlayersTotal / samples)
			+ " avgProcessNpcsMs=" + nanosToMillis(benchmarkProcessNpcsTotal / samples)
			+ " npcIdleThrottleSkipped=" + benchmarkNpcIdleThrottleSkipped
			+ " avgMessageQueuesMs=" + nanosToMillis(benchmarkMessageQueuesTotal / samples)
			+ " avgUpdateClientsMs=" + nanosToMillis(benchmarkUpdateClientsTotal / samples)
			+ " avgCleanupMs=" + nanosToMillis(benchmarkCleanupTotal / samples)
			+ " avgWalkToActionsMs=" + nanosToMillis(benchmarkWalkToActionsTotal / samples)
			+ " avgIncomingPacketsMs=" + nanosToMillis(benchmarkIncomingPacketsTotal / samples)
			+ " avgOutgoingPacketsMs=" + nanosToMillis(benchmarkOutgoingPacketsTotal / samples)
			+ " avgOutgoingPayloadBytes=" + (benchmarkOutgoingPayloadBytesTotal / samples)
			+ " avgVisibilitySnapshotMs=" + nanosToMillis(benchmarkVisibilitySnapshotTotal / samples)
			+ " avgVisiblePlayers=" + (benchmarkVisiblePlayersTotal / visibilitySamples)
			+ " avgVisibleNpcs=" + (benchmarkVisibleNpcsTotal / visibilitySamples)
			+ " avgVisibleScenery=" + (benchmarkVisibleSceneryTotal / visibilitySamples)
			+ " avgVisibleWallObjects=" + (benchmarkVisibleWallObjectsTotal / visibilitySamples)
			+ " avgVisibleGroundItems=" + (benchmarkVisibleGroundItemsTotal / visibilitySamples)
			+ " maxVisiblePlayers=" + benchmarkVisiblePlayersMax
			+ " maxVisibleNpcs=" + benchmarkVisibleNpcsMax
			+ " maxVisibleScenery=" + benchmarkVisibleSceneryMax
			+ " maxVisibleWallObjects=" + benchmarkVisibleWallObjectsMax
			+ " maxVisibleGroundItems=" + benchmarkVisibleGroundItemsMax
			+ " visibilityRegionCacheRequests=" + benchmarkVisibilityRegionCacheRequests
			+ " visibilityRegionCacheHits=" + benchmarkVisibilityRegionCacheHits
			+ " visibilityRegionCacheMisses=" + benchmarkVisibilityRegionCacheMisses
			+ " visibilityObjectCacheRequests=" + benchmarkVisibilityObjectCacheRequests
			+ " visibilityObjectCacheHits=" + benchmarkVisibilityObjectCacheHits
			+ " visibilityObjectCacheMisses=" + benchmarkVisibilityObjectCacheMisses
			+ " visibilityObjectCacheClears=" + benchmarkVisibilityObjectCacheClears
			+ " visibilityObjectCacheEntriesCleared=" + benchmarkVisibilityObjectCacheEntriesCleared
			+ " visibilityObjectSnapshotCacheRequests=" + benchmarkVisibilityObjectSnapshotCacheRequests
			+ " visibilityObjectSnapshotCacheHits=" + benchmarkVisibilityObjectSnapshotCacheHits
			+ " visibilityObjectSnapshotCacheMisses=" + benchmarkVisibilityObjectSnapshotCacheMisses
			+ " visibilityTickSnapshotCacheRequests=" + benchmarkVisibilityTickSnapshotCacheRequests
			+ " visibilityTickSnapshotCacheHits=" + benchmarkVisibilityTickSnapshotCacheHits
			+ " visibilityTickSnapshotCacheMisses=" + benchmarkVisibilityTickSnapshotCacheMisses
			+ " avgVisibilityShadowMs=" + nanosToMillis(benchmarkVisibilityShadowTotal / samples)
			+ " visibilityShadowSamples=" + benchmarkVisibilityShadowSamples
			+ " visibilityShadowMismatches=" + benchmarkVisibilityShadowMismatchSamples
			+ " visibilityShadowPlayerMismatches=" + benchmarkVisibilityShadowPlayerMismatches
			+ " visibilityShadowNpcMismatches=" + benchmarkVisibilityShadowNpcMismatches
			+ " visibilityShadowGameObjectMismatches=" + benchmarkVisibilityShadowGameObjectMismatches
			+ " visibilityShadowGroundItemMismatches=" + benchmarkVisibilityShadowGroundItemMismatches
			+ " maxVisibilityShadowMobRegions=" + benchmarkVisibilityShadowMobRegionsMax
			+ " maxVisibilityShadowObjectRegions=" + benchmarkVisibilityShadowObjectRegionsMax
			+ " sceneBaselinePackets=" + benchmarkSceneBaselinePackets
			+ " sceneBaselinePages=" + benchmarkSceneBaselinePages
			+ " sceneBaselineRecords=" + benchmarkSceneBaselineRecords
			+ " sceneBaselinePayloadBytes=" + benchmarkSceneBaselinePayloadBytes
			+ " movementSnapshotPackets=" + benchmarkMovementSnapshotPackets
			+ " movementSnapshotRecords=" + benchmarkMovementSnapshotRecords
			+ " movementSnapshotPayloadBytes=" + benchmarkMovementSnapshotPayloadBytes
			+ " nativeTerrainContextPackets=" + benchmarkNativeTerrainContextPackets
			+ " nativeTerrainSectorSlots=" + benchmarkNativeTerrainSectorSlots
			+ " nativeTerrainAvailableSectors=" + benchmarkNativeTerrainAvailableSectors
			+ " nativeTerrainPayloadSectors=" + benchmarkNativeTerrainPayloadSectors
			+ " nativeTerrainReferencedSectors=" + benchmarkNativeTerrainReferencedSectors
			+ " nativeTerrainRawBytes=" + benchmarkNativeTerrainRawBytes
			+ " nativeTerrainWireBytes=" + benchmarkNativeTerrainWireBytes
			+ " nativeTerrainWireCacheRequests=" + benchmarkNativeTerrainWireCacheRequests
			+ " nativeTerrainWireCacheHits=" + benchmarkNativeTerrainWireCacheHits
			+ " nativeTerrainWireCacheMisses=" + benchmarkNativeTerrainWireCacheMisses
			+ " nativeTerrainWireBuildMsPrecise="
				+ nanosToMillisPrecise(benchmarkNativeTerrainWireBuildNanos)
			+ " nativeTerrainWireCacheEntriesMax="
				+ benchmarkNativeTerrainWireCacheEntriesMax
			+ " suppressedLegacySceneryPackets=" + benchmarkSuppressedLegacySceneryPackets
			+ " suppressedLegacySceneryRecords=" + benchmarkSuppressedLegacySceneryRecords
			+ " suppressedLegacyWallPackets=" + benchmarkSuppressedLegacyWallPackets
			+ " suppressedLegacyWallRecords=" + benchmarkSuppressedLegacyWallRecords
			+ " saveRequests=" + getPlayerSaveRequestCount()
			+ " saveLogoutRequests=" + getPlayerSaveLogoutRequestCount()
			+ " avgTickMsPrecise=" + nanosToMillisPrecise(benchmarkTickTotal / samples)
			+ " maxTickMsPrecise=" + nanosToMillisPrecise(benchmarkTickMax)
			+ " avgEventsMsPrecise=" + nanosToMillisPrecise(benchmarkEventsTotal / samples)
			+ " avgWorldUpdateMsPrecise=" + nanosToMillisPrecise(benchmarkWorldUpdateTotal / samples)
			+ " avgProcessPlayersMsPrecise=" + nanosToMillisPrecise(benchmarkProcessPlayersTotal / samples)
			+ " avgProcessNpcsMsPrecise=" + nanosToMillisPrecise(benchmarkProcessNpcsTotal / samples)
			+ " avgProcessNpcUnregisterMsPrecise=" + nanosToMillisPrecise(benchmarkProcessNpcUnregisterTotal / samples)
			+ " avgProcessNpcBehaviorMsPrecise=" + nanosToMillisPrecise(benchmarkProcessNpcBehaviorTotal / samples)
			+ " avgProcessNpcMovementMsPrecise=" + nanosToMillisPrecise(benchmarkProcessNpcMovementTotal / samples)
			+ " avgNpcIdleThrottleSkipped=" + (benchmarkNpcIdleThrottleSkipped / samples)
			+ " avgNpcBehaviorRoamMsPrecise=" + nanosToMillisPrecise(benchmarkNpcBehaviorRoamTotal / samples)
			+ " avgNpcBehaviorAggroMsPrecise=" + nanosToMillisPrecise(benchmarkNpcBehaviorAggroTotal / samples)
			+ " avgNpcBehaviorCombatMsPrecise=" + nanosToMillisPrecise(benchmarkNpcBehaviorCombatTotal / samples)
			+ " avgNpcBehaviorTackleMsPrecise=" + nanosToMillisPrecise(benchmarkNpcBehaviorTackleTotal / samples)
			+ " avgNpcBehaviorRetreatMsPrecise=" + nanosToMillisPrecise(benchmarkNpcBehaviorRetreatTotal / samples)
			+ " avgNpcRoamEligibilityMsPrecise=" + nanosToMillisPrecise(benchmarkNpcRoamEligibilityTotal / samples)
			+ " avgNpcRoamAggroScanMsPrecise=" + nanosToMillisPrecise(benchmarkNpcRoamAggroScanTotal / samples)
			+ " avgNpcRoamTackleScanMsPrecise=" + nanosToMillisPrecise(benchmarkNpcRoamTackleScanTotal / samples)
			+ " avgNpcRoamRandomWalkMsPrecise=" + nanosToMillisPrecise(benchmarkNpcRoamRandomWalkTotal / samples)
			+ " avgMessageQueuesMsPrecise=" + nanosToMillisPrecise(benchmarkMessageQueuesTotal / samples)
			+ " avgUpdateClientsMsPrecise=" + nanosToMillisPrecise(benchmarkUpdateClientsTotal / samples)
			+ " avgUpdatePlayersMsPrecise=" + nanosToMillisPrecise(benchmarkUpdatePlayersTotal / samples)
			+ " avgUpdatePlayerAppearancesMsPrecise=" + nanosToMillisPrecise(benchmarkUpdatePlayerAppearancesTotal / samples)
			+ " avgUpdateNpcsMsPrecise=" + nanosToMillisPrecise(benchmarkUpdateNpcsTotal / samples)
			+ " avgUpdateNpcAppearancesMsPrecise=" + nanosToMillisPrecise(benchmarkUpdateNpcAppearancesTotal / samples)
			+ " avgUpdateGameObjectsMsPrecise=" + nanosToMillisPrecise(benchmarkUpdateGameObjectsTotal / samples)
			+ " avgUpdateWallObjectsMsPrecise=" + nanosToMillisPrecise(benchmarkUpdateWallObjectsTotal / samples)
			+ " avgUpdateGroundItemsMsPrecise=" + nanosToMillisPrecise(benchmarkUpdateGroundItemsTotal / samples)
			+ " avgUpdateTimeoutsMsPrecise=" + nanosToMillisPrecise(benchmarkUpdateTimeoutsTotal / samples)
			+ " avgUpdateAppearanceKeepaliveMsPrecise=" + nanosToMillisPrecise(benchmarkUpdateAppearanceKeepaliveTotal / samples)
			+ " avgCleanupMsPrecise=" + nanosToMillisPrecise(benchmarkCleanupTotal / samples)
			+ " avgWalkToActionsMsPrecise=" + nanosToMillisPrecise(benchmarkWalkToActionsTotal / samples)
			+ " avgIncomingPacketsMsPrecise=" + nanosToMillisPrecise(benchmarkIncomingPacketsTotal / samples)
			+ " avgOutgoingPacketsMsPrecise=" + nanosToMillisPrecise(benchmarkOutgoingPacketsTotal / samples)
			+ " avgSaveQueueMsPrecise=" + nanosToMillisPrecise(getAveragePlayerSaveQueueDuration())
			+ " maxSaveQueueMsPrecise=" + nanosToMillisPrecise(playerSaveQueueMaxDuration.get())
			+ " avgSaveProcessMsPrecise=" + nanosToMillisPrecise(getAveragePlayerSaveProcessDuration())
			+ " maxSaveProcessMsPrecise=" + nanosToMillisPrecise(playerSaveProcessMaxDuration.get())
			+ " maxPlayers=" + benchmarkMaxPlayers
			+ " maxNpcs=" + benchmarkMaxNpcs
			+ " maxEvents=" + benchmarkMaxEvents
			+ " pvmMeleeEventCalls=" + benchmarkPvmMeleeEventCalls
			+ " pvmMeleeEventMsPrecise=" + nanosToMillisPrecise(benchmarkPvmMeleeEventTotal)
			+ " rangeEventCalls=" + benchmarkRangeEventCalls
			+ " rangeEventMsPrecise=" + nanosToMillisPrecise(benchmarkRangeEventTotal)
			+ " magicCombatEventCalls=" + benchmarkMagicCombatEventCalls
			+ " magicCombatEventMsPrecise=" + nanosToMillisPrecise(benchmarkMagicCombatEventTotal)
			+ " throwingEventCalls=" + benchmarkThrowingEventCalls
			+ " throwingEventMsPrecise=" + nanosToMillisPrecise(benchmarkThrowingEventTotal)
			+ " projectileEventCalls=" + benchmarkProjectileEventCalls
			+ " projectileEventMsPrecise=" + nanosToMillisPrecise(benchmarkProjectileEventTotal)
			+ " pluginTickEventCalls=" + benchmarkPluginTickEventCalls
			+ " pluginTickEventMsPrecise=" + nanosToMillisPrecise(benchmarkPluginTickEventTotal)
			+ " otherEventCalls=" + benchmarkOtherEventCalls
			+ " otherEventMsPrecise=" + nanosToMillisPrecise(benchmarkOtherEventTotal)
			+ (activeCombatBenchmark == null
				? " activeCombatInvariant=disabled"
				: activeCombatBenchmark.buildSummary())
			+ (projectileCombatBenchmark == null
				? " projectileCombatInvariant=disabled"
				: projectileCombatBenchmark.buildSummary())
			+ (AuthenticatedNetworkBenchmarkProbe.isEnabled()
				? AuthenticatedNetworkBenchmarkProbe.summary() : "");
	}

	public void recordPlayerSaveTiming(final boolean logout, final long queueDuration, final long processDuration) {
		playerSaveRequestCount.incrementAndGet();
		if (logout) {
			playerSaveLogoutRequestCount.incrementAndGet();
		}
		final long safeQueueDuration = Math.max(0, queueDuration);
		final long safeProcessDuration = Math.max(0, processDuration);
		playerSaveQueueDuration.addAndGet(safeQueueDuration);
		playerSaveProcessDuration.addAndGet(safeProcessDuration);
		updateMax(playerSaveQueueMaxDuration, safeQueueDuration);
		updateMax(playerSaveProcessMaxDuration, safeProcessDuration);
	}

	private static void updateMax(final AtomicLong max, final long value) {
		long current;
		do {
			current = max.get();
			if (value <= current) {
				return;
			}
		} while (!max.compareAndSet(current, value));
	}

	private long getAveragePlayerSaveQueueDuration() {
		final long count = Math.max(1, playerSaveRequestCount.get());
		return playerSaveQueueDuration.get() / count;
	}

	private long getAveragePlayerSaveProcessDuration() {
		final long count = Math.max(1, playerSaveRequestCount.get());
		return playerSaveProcessDuration.get() / count;
	}

	public long getPlayerSaveRequestCount() {
		return playerSaveRequestCount.get();
	}

	public long getPlayerSaveLogoutRequestCount() {
		return playerSaveLogoutRequestCount.get();
	}

	private long nanosToMillis(final long nanos) {
		return nanos / 1000000L;
	}

	private String nanosToMillisPrecise(final long nanos) {
		return String.format(Locale.ROOT, "%.3f", nanos / 1000000.0D);
	}

	private void sendMonitoringWarning(final String message, final boolean showEventData) {
		if (getConfig().DEBUG) { // only displays in-client to logged in staff players if server config debug is true
			for (Player p : getWorld().getPlayers()) {
				if (!p.isDev())
					continue;

				p.playerServerMessage(MessageType.BROADCAST, getWorld().getServer().getConfig().MESSAGE_PREFIX + message);
			}
		}

		LOGGER.warn(message);
		if (getWorld().getServer().getDiscordService() != null) {
			getWorld().getServer().getDiscordService().monitoringSendServerBehind("**=== " + getWorld().getServer().getName() + "===**\n" + message, showEventData);
		}
	}

	public boolean shutdown(final int seconds) {
		if (shutdownEvent != null) {
			return false;
		}
		LOGGER.info("Server shutdown requested");
		shutdownEvent = new FinitePeriodicEvent(getWorld(), null, seconds * 1000 / getConfig().GAME_TICK, 1, "Server shut down") {
			int ticksElapsed = 0;

			@Override
			public void action() {
				int secs = (int) (getTimeLeftMillis() / 1000);
				if (ticksElapsed % 10 == 0) {
					for (final Player playerToUpdate : getWorld().getPlayers()) {
						if (playerToUpdate.getClientLimitations().supportsSystemUpdateTimer) {
							ActionSender.sendSystemUpdateTimer(playerToUpdate,  secs);
						} else if (ticksElapsed % 50 == 0) {
							ActionSender.sendSystemMessage(playerToUpdate, "System update in " + StringUtil.formatTime(secs));
						}
					}
				}
				if (ticksElapsed >= getNumIterations()) {
					shuttingDown = true;
				}
				ticksElapsed++;
			}
		};
		getGameEventHandler().add(shutdownEvent);

		return true;
	}

	public boolean restart(final int seconds) {
		if (shutdownEvent != null) {
			return false;
		}
		LOGGER.info("Server restart requested");
		shutdownEvent = new FinitePeriodicEvent(getWorld(), null, seconds * 1000 / getConfig().GAME_TICK, 1, "Server shut down") {
			int ticksElapsed = 0;

			@Override
			public void action() {
				int secs = (int) (getTimeLeftMillis() / 1000);
				if (ticksElapsed % 10 == 0) {
					for (final Player playerToUpdate : getWorld().getPlayers()) {
						if (playerToUpdate.getClientLimitations().supportsSystemUpdateTimer) {
							ActionSender.sendSystemUpdateTimer(playerToUpdate,  secs);
						} else if (ticksElapsed % 50 == 0) {
							ActionSender.sendSystemMessage(playerToUpdate, "System update in: " + StringUtil.formatTime(secs));
						}
					}
				}
				if (ticksElapsed >= getNumIterations()) {
					shuttingDown = true;
					restarting = true;
				}
				ticksElapsed++;
			}
		};
		getGameEventHandler().add(shutdownEvent);

		return true;
	}

	public long getTimeUntilShutdown() {
		if (shutdownEvent == null) {
			return -1;
		}
		return Math.max(((FinitePeriodicEvent)shutdownEvent).getTimeLeftMillis(), 0);
	}

	public final long getLastEventsDuration() {
		return lastEventsDuration;
	}

	public final long getLastTickDuration() {
		return lastTickDuration;
	}

	public final GameEventHandler getGameEventHandler() {
		return gameEventHandler;
	}

	public final GameClock getGameClock() {
		return gameClock;
	}

	public final GameRandom getCombatRandom() {
		return combatRandom;
	}

	public final CombatDamageObserver getCombatDamageObserver() {
		return combatDamageObserver;
	}

	public final ResolvedDamageTransaction getResolvedDamageTransaction() {
		return resolvedDamageTransaction;
	}

	public final GameStateUpdater getGameUpdater() {
		return gameUpdater;
	}

	public WorldDayNightClock getWorldDayNightClock() {
		return worldDayNightClock;
	}

	public final DiscordService getDiscordService() {
		return discordService;
	}

	public ListenableFuture<?> submitSqlLogging(Runnable runnable) {
		return sqlLoggingThreadPool.submit(runnable);
	}

	public <V> ListenableFuture<V> submitSqlLogging(Callable<V> callable) {
		return sqlLoggingThreadPool.submit(callable);
	}

	public ListenableFuture<?> submitOnlineMonitor(Runnable runnable) {
		return onlineMonitorThreadPool.submit(runnable);
	}

	public <V> ListenableFuture<V> submitOnlineMonitor(Callable<V> callable) {
		return onlineMonitorThreadPool.submit(callable);
	}

	public ListenableFuture<?> submitSql(Runnable runnable) {
		return sqlThreadPool.submit(runnable);
	}

	public <V> ListenableFuture<V> submitSql(Callable<V> callable) {
		return sqlThreadPool.submit(callable);
	}

	public final LoginExecutor getLoginExecutor() {
		return loginExecutor;
	}

	public final RSCPacketFilter getPacketFilter() {
		return packetFilter;
	}

  	public final int clearAllIpBans() {
    return packetFilter.clearAllIpBans();
  }

	public final int recalculateLoggedInCounts() {
		return packetFilter.recalculateLoggedInCounts();
	}

	public final int getPlayersCount(String hostAddress) {
		return packetFilter.getPlayersCount(hostAddress);
	}

	public final long getLastIncomingPacketsDuration() {
		return lastIncomingPacketsDuration;
	}

	public final long getLastOutgoingPacketsDuration() {
		return lastOutgoingPacketsDuration;
	}

	public long getLastWorldUpdateDuration() {
		return lastWorldUpdateDuration;
	}

	public long getLastProcessPlayersDuration() {
		return lastProcessPlayersDuration;
	}

	public long getLastProcessNpcsDuration() {
		return lastProcessNpcsDuration;
	}

	public long getLastProcessNpcUnregisterDuration() {
		return lastProcessNpcUnregisterDuration;
	}

	public long getLastProcessNpcBehaviorDuration() {
		return lastProcessNpcBehaviorDuration;
	}

	public long getLastProcessNpcMovementDuration() {
		return lastProcessNpcMovementDuration;
	}

	public long getLastNpcIdleThrottleSkipped() {
		return lastNpcIdleThrottleSkipped;
	}

	public long getLastNpcBehaviorRoamDuration() {
		return lastNpcBehaviorRoamDuration;
	}

	public long getLastNpcBehaviorAggroDuration() {
		return lastNpcBehaviorAggroDuration;
	}

	public long getLastNpcBehaviorCombatDuration() {
		return lastNpcBehaviorCombatDuration;
	}

	public long getLastNpcBehaviorTackleDuration() {
		return lastNpcBehaviorTackleDuration;
	}

	public long getLastNpcBehaviorRetreatDuration() {
		return lastNpcBehaviorRetreatDuration;
	}

	public long getLastNpcRoamEligibilityDuration() {
		return lastNpcRoamEligibilityDuration;
	}

	public long getLastNpcRoamAggroScanDuration() {
		return lastNpcRoamAggroScanDuration;
	}

	public long getLastNpcRoamTackleScanDuration() {
		return lastNpcRoamTackleScanDuration;
	}

	public long getLastNpcRoamRandomWalkDuration() {
		return lastNpcRoamRandomWalkDuration;
	}

	public long getLastProcessMessageQueuesDuration() {
		return lastProcessMessageQueuesDuration;
	}

	public long getLastUpdateClientsDuration() {
		return lastUpdateClientsDuration;
	}

	public long getLastUpdatePlayersDuration() {
		return lastUpdatePlayersDuration;
	}

	public long getLastUpdatePlayerAppearancesDuration() {
		return lastUpdatePlayerAppearancesDuration;
	}

	public long getLastUpdateNpcsDuration() {
		return lastUpdateNpcsDuration;
	}

	public long getLastUpdateNpcAppearancesDuration() {
		return lastUpdateNpcAppearancesDuration;
	}

	public long getLastUpdateGameObjectsDuration() {
		return lastUpdateGameObjectsDuration;
	}

	public long getLastUpdateWallObjectsDuration() {
		return lastUpdateWallObjectsDuration;
	}

	public long getLastUpdateGroundItemsDuration() {
		return lastUpdateGroundItemsDuration;
	}

	public long getLastUpdateTimeoutsDuration() {
		return lastUpdateTimeoutsDuration;
	}

	public long getLastUpdateAppearanceKeepaliveDuration() {
		return lastUpdateAppearanceKeepaliveDuration;
	}

	public long getLastVisibilitySnapshotDuration() {
		return lastVisibilitySnapshotDuration;
	}

	public long getLastVisibilitySnapshotSamples() {
		return lastVisibilitySnapshotSamples;
	}

	public long getLastVisiblePlayersTotal() {
		return lastVisiblePlayersTotal;
	}

	public long getLastVisibleNpcsTotal() {
		return lastVisibleNpcsTotal;
	}

	public long getLastVisibleSceneryTotal() {
		return lastVisibleSceneryTotal;
	}

	public long getLastVisibleWallObjectsTotal() {
		return lastVisibleWallObjectsTotal;
	}

	public long getLastVisibleGroundItemsTotal() {
		return lastVisibleGroundItemsTotal;
	}

	public int getLastVisiblePlayersMax() {
		return lastVisiblePlayersMax;
	}

	public int getLastVisibleNpcsMax() {
		return lastVisibleNpcsMax;
	}

	public int getLastVisibleSceneryMax() {
		return lastVisibleSceneryMax;
	}

	public int getLastVisibleWallObjectsMax() {
		return lastVisibleWallObjectsMax;
	}

	public int getLastVisibleGroundItemsMax() {
		return lastVisibleGroundItemsMax;
	}

	public long getLastVisibilityRegionCacheRequests() {
		return lastVisibilityRegionCacheRequests;
	}

	public long getLastVisibilityRegionCacheHits() {
		return lastVisibilityRegionCacheHits;
	}

	public long getLastVisibilityRegionCacheMisses() {
		return lastVisibilityRegionCacheMisses;
	}

	public long getLastVisibilityObjectCacheRequests() {
		return lastVisibilityObjectCacheRequests;
	}

	public long getLastVisibilityObjectCacheHits() {
		return lastVisibilityObjectCacheHits;
	}

	public long getLastVisibilityObjectCacheMisses() {
		return lastVisibilityObjectCacheMisses;
	}

	public long getLastVisibilityObjectCacheClears() {
		return lastVisibilityObjectCacheClears;
	}

	public long getLastVisibilityObjectCacheEntriesCleared() {
		return lastVisibilityObjectCacheEntriesCleared;
	}

	public long getLastVisibilityObjectSnapshotCacheRequests() {
		return lastVisibilityObjectSnapshotCacheRequests;
	}

	public long getLastVisibilityObjectSnapshotCacheHits() {
		return lastVisibilityObjectSnapshotCacheHits;
	}

	public long getLastVisibilityObjectSnapshotCacheMisses() {
		return lastVisibilityObjectSnapshotCacheMisses;
	}

	public long getLastVisibilityTickSnapshotCacheRequests() {
		return lastVisibilityTickSnapshotCacheRequests;
	}

	public long getLastVisibilityTickSnapshotCacheHits() {
		return lastVisibilityTickSnapshotCacheHits;
	}

	public long getLastVisibilityTickSnapshotCacheMisses() {
		return lastVisibilityTickSnapshotCacheMisses;
	}

	public long getLastVisibilityShadowDuration() {
		return lastVisibilityShadowDuration;
	}

	public long getLastVisibilityShadowSamples() {
		return lastVisibilityShadowSamples;
	}

	public long getLastVisibilityShadowMismatchSamples() {
		return lastVisibilityShadowMismatchSamples;
	}

	public long getLastVisibilityShadowPlayerMismatches() {
		return lastVisibilityShadowPlayerMismatches;
	}

	public long getLastVisibilityShadowNpcMismatches() {
		return lastVisibilityShadowNpcMismatches;
	}

	public long getLastVisibilityShadowGameObjectMismatches() {
		return lastVisibilityShadowGameObjectMismatches;
	}

	public long getLastVisibilityShadowGroundItemMismatches() {
		return lastVisibilityShadowGroundItemMismatches;
	}

	public int getLastVisibilityShadowMobRegionsMax() {
		return lastVisibilityShadowMobRegionsMax;
	}

	public int getLastVisibilityShadowObjectRegionsMax() {
		return lastVisibilityShadowObjectRegionsMax;
	}

	public long getLastNativeTerrainContextPackets() {
		return lastNativeTerrainContextPackets;
	}

	public long getLastNativeTerrainSectorSlots() {
		return lastNativeTerrainSectorSlots;
	}

	public long getLastNativeTerrainAvailableSectors() {
		return lastNativeTerrainAvailableSectors;
	}

	public long getLastNativeTerrainPayloadSectors() {
		return lastNativeTerrainPayloadSectors;
	}

	public long getLastNativeTerrainReferencedSectors() {
		return lastNativeTerrainReferencedSectors;
	}

	public long getLastNativeTerrainRawBytes() {
		return lastNativeTerrainRawBytes;
	}

	public long getLastNativeTerrainWireBytes() {
		return lastNativeTerrainWireBytes;
	}

	public long getLastNativeTerrainWireCacheRequests() {
		return lastNativeTerrainWireCacheRequests;
	}

	public long getLastNativeTerrainWireCacheHits() {
		return lastNativeTerrainWireCacheHits;
	}

	public long getLastNativeTerrainWireCacheMisses() {
		return lastNativeTerrainWireCacheMisses;
	}

	public long getLastNativeTerrainWireBuildNanos() {
		return lastNativeTerrainWireBuildNanos;
	}

	public int getLastNativeTerrainWireCacheEntriesMax() {
		return lastNativeTerrainWireCacheEntriesMax;
	}

	public long getLastDoCleanupDuration() {
		return lastDoCleanupDuration;
	}

	public long getLastExecuteWalkToActionsDuration() {
		return lastExecuteWalkToActionsDuration;
	}

	public final long getTimeLate() {
		return timeLate;
	}

	public final long getServerStartedTime() {
		return serverStartedTime;
	}

	public final long getCurrentTick() {
		return (lastTickTimestamp - getServerStartedTime()) / (getConfig().GAME_TICK * 1000000);
	}

	private void advanceTicks(final long ticks) {
		lastTickTimestamp += ticks * getConfig().GAME_TICK * 1000000;
	}

	public final ServerConfiguration getConfig() {
		return config;
	}

	public final ProcessNetworkConfiguration getProcessNetworkConfiguration() {
		return processNetworkConfiguration;
	}

	public final DiagnosticsConfiguration getDiagnosticsConfiguration() {
		return diagnosticsConfiguration;
	}

	public final WorldEditorSessionManager getWorldEditorSessions() { return worldEditorSessions; }

	public final WorldEditStorageContext getWorldEditStorage() { return worldEditStorage; }

	public final boolean isRunning() {
		return running.get();
	}

	public final Constants getConstants() {
		return constants;
	}

	public synchronized World getWorld() {
		return world;
	}

	public String getName() {
		return name;
	}

	public PluginHandler getPluginHandler() {
		return pluginHandler;
	}

	public CombatScriptLoader getCombatScriptLoader() {
		return combatScriptLoader;
	}

	public MySqlGameLogger getGameLogger() {
		return gameLogger;
	}

	public PcapLoggerService getPcapLogger() {
		return pcapLogger;
	}

	public EntityHandler getEntityHandler() {
		return entityHandler;
	}

	public GameDatabase getDatabase() {
		return database;
	}

	public IPlayerService getPlayerService() { return playerService; }

	public AchievementSystem getAchievementSystem() {
		return achievementSystem;
	}

	public I18NService getI18nService() {
		return i18nService;
	}

	public boolean isRestarting() {
		return restarting;
	}

	public boolean isShuttingDown() {
		return shuttingDown;
	}

	public Map<Integer, Long> getIncomingTimePerPacketOpcode() {
		return incomingTimePerPacketOpcode;
	}

	public Map<Integer, Integer> getIncomingCountPerPacketOpcode() {
		return incomingCountPerPacketOpcode;
	}

	public Map<Integer, Long> getOutgoingTimePerPacketOpcode() {
		return outgoingTimePerPacketOpcode;
	}

	public Map<Integer, Integer> getOutgoingCountPerPacketOpcode() {
		return outgoingCountPerPacketOpcode;
	}

	public Map<Integer, Long> getOutgoingPayloadBytesPerPacketOpcode() {
		return outgoingPayloadBytesPerPacketOpcode;
	}

	public long getLastOutgoingPayloadBytes() {
		return lastOutgoingPayloadBytes;
	}

	public void addIncomingPacketDuration(final int packetOpcode, final long additionalTime) {
		if (!incomingTimePerPacketOpcode.containsKey(packetOpcode)) {
			incomingTimePerPacketOpcode.put(packetOpcode, 0L);
		}
		incomingTimePerPacketOpcode.put(packetOpcode, incomingTimePerPacketOpcode.get(packetOpcode) + additionalTime);
	}

	public void incrementIncomingPacketCount(final int packetOpcode) {
		if (!incomingCountPerPacketOpcode.containsKey(packetOpcode)) {
			incomingCountPerPacketOpcode.put(packetOpcode, 0);
		}
		incomingCountPerPacketOpcode.put(packetOpcode, incomingCountPerPacketOpcode.get(packetOpcode) + 1);
	}

	public void addOutgoingPacketDuration(final int packetOpcode, final long additionalTime) {
		if (!outgoingTimePerPacketOpcode.containsKey(packetOpcode)) {
			outgoingTimePerPacketOpcode.put(packetOpcode, 0L);
		}
		outgoingTimePerPacketOpcode.put(packetOpcode, outgoingTimePerPacketOpcode.get(packetOpcode) + additionalTime);
	}

	public void incrementOutgoingPacketCount(final int packetOpcode) {
		if (!outgoingCountPerPacketOpcode.containsKey(packetOpcode)) {
			outgoingCountPerPacketOpcode.put(packetOpcode, 0);
		}
		outgoingCountPerPacketOpcode.put(packetOpcode, outgoingCountPerPacketOpcode.get(packetOpcode) + 1);
	}

	public void addOutgoingPacketBytes(final int packetOpcode, final long additionalBytes) {
		if (!outgoingPayloadBytesPerPacketOpcode.containsKey(packetOpcode)) {
			outgoingPayloadBytesPerPacketOpcode.put(packetOpcode, 0L);
		}
		outgoingPayloadBytesPerPacketOpcode.put(packetOpcode,
			outgoingPayloadBytesPerPacketOpcode.get(packetOpcode) + additionalBytes);
		lastOutgoingPayloadBytes += additionalBytes;
	}

	public synchronized long getMaxItemID() {
		return maxItemId;
	}

	public synchronized long incrementMaxItemID() {
		return ++maxItemId;
	}

	public synchronized int incrementPrivateMessagesSent() {
		return ++privateMessagesSent;
	}

	public synchronized void incrementLastIncomingPacketsDuration(final long duration) {
		this.lastIncomingPacketsDuration += duration;
	}

	public synchronized void incrementLastEventsDuration(final long duration) {
		this.lastEventsDuration += duration;
	}

	public synchronized void incrementLastOutgoingPacketsDuration(final long duration) {
		this.lastOutgoingPacketsDuration += duration;
	}

	public synchronized void incrementLastWorldUpdateDuration(final long duration) {
		this.lastWorldUpdateDuration += duration;
	}

	public synchronized void incrementLastProcessPlayersDuration(final long duration) {
		this.lastProcessPlayersDuration += duration;
	}

	public synchronized void incrementLastProcessNpcsDuration(final long duration) {
		this.lastProcessNpcsDuration += duration;
	}

	public synchronized void incrementLastProcessNpcUnregisterDuration(final long duration) {
		this.lastProcessNpcUnregisterDuration += duration;
	}

	public synchronized void incrementLastProcessNpcBehaviorDuration(final long duration) {
		this.lastProcessNpcBehaviorDuration += duration;
	}

	public synchronized void incrementLastProcessNpcMovementDuration(final long duration) {
		this.lastProcessNpcMovementDuration += duration;
	}

	public synchronized void incrementLastNpcIdleThrottleSkipped() {
		this.lastNpcIdleThrottleSkipped++;
	}

	public synchronized void incrementLastNpcBehaviorRoamDuration(final long duration) {
		this.lastNpcBehaviorRoamDuration += duration;
	}

	public synchronized void incrementLastNpcBehaviorAggroDuration(final long duration) {
		this.lastNpcBehaviorAggroDuration += duration;
	}

	public synchronized void incrementLastNpcBehaviorCombatDuration(final long duration) {
		this.lastNpcBehaviorCombatDuration += duration;
	}

	public synchronized void incrementLastNpcBehaviorTackleDuration(final long duration) {
		this.lastNpcBehaviorTackleDuration += duration;
	}

	public synchronized void incrementLastNpcBehaviorRetreatDuration(final long duration) {
		this.lastNpcBehaviorRetreatDuration += duration;
	}

	public synchronized void incrementLastNpcRoamEligibilityDuration(final long duration) {
		this.lastNpcRoamEligibilityDuration += duration;
	}

	public synchronized void incrementLastNpcRoamAggroScanDuration(final long duration) {
		this.lastNpcRoamAggroScanDuration += duration;
	}

	public synchronized void incrementLastNpcRoamTackleScanDuration(final long duration) {
		this.lastNpcRoamTackleScanDuration += duration;
	}

	public synchronized void incrementLastNpcRoamRandomWalkDuration(final long duration) {
		this.lastNpcRoamRandomWalkDuration += duration;
	}

	public synchronized void incrementLastProcessMessageQueuesDuration(final long duration) {
		this.lastProcessMessageQueuesDuration += duration;
	}

	public synchronized void incrementLastUpdateClientsDuration(final long duration) {
		this.lastUpdateClientsDuration += duration;
	}

	public synchronized void incrementLastUpdatePlayersDuration(final long duration) {
		this.lastUpdatePlayersDuration += duration;
	}

	public synchronized void incrementLastUpdatePlayerAppearancesDuration(final long duration) {
		this.lastUpdatePlayerAppearancesDuration += duration;
	}

	public synchronized void incrementLastUpdateNpcsDuration(final long duration) {
		this.lastUpdateNpcsDuration += duration;
	}

	public synchronized void incrementLastUpdateNpcAppearancesDuration(final long duration) {
		this.lastUpdateNpcAppearancesDuration += duration;
	}

	public synchronized void incrementLastUpdateGameObjectsDuration(final long duration) {
		this.lastUpdateGameObjectsDuration += duration;
	}

	public synchronized void incrementLastUpdateWallObjectsDuration(final long duration) {
		this.lastUpdateWallObjectsDuration += duration;
	}

	public synchronized void incrementLastUpdateGroundItemsDuration(final long duration) {
		this.lastUpdateGroundItemsDuration += duration;
	}

	public synchronized void incrementLastUpdateTimeoutsDuration(final long duration) {
		this.lastUpdateTimeoutsDuration += duration;
	}

	public synchronized void incrementLastUpdateAppearanceKeepaliveDuration(final long duration) {
		this.lastUpdateAppearanceKeepaliveDuration += duration;
	}

	public synchronized void addVisibilitySnapshotMetrics(
		final int visiblePlayers,
		final int visibleNpcs,
		final int visibleScenery,
		final int visibleWallObjects,
		final int visibleGroundItems,
		final long duration) {
		this.lastVisibilitySnapshotDuration += duration;
		this.lastVisibilitySnapshotSamples++;
		this.lastVisiblePlayersTotal += visiblePlayers;
		this.lastVisibleNpcsTotal += visibleNpcs;
		this.lastVisibleSceneryTotal += visibleScenery;
		this.lastVisibleWallObjectsTotal += visibleWallObjects;
		this.lastVisibleGroundItemsTotal += visibleGroundItems;
		this.lastVisiblePlayersMax = Math.max(this.lastVisiblePlayersMax, visiblePlayers);
		this.lastVisibleNpcsMax = Math.max(this.lastVisibleNpcsMax, visibleNpcs);
		this.lastVisibleSceneryMax = Math.max(this.lastVisibleSceneryMax, visibleScenery);
		this.lastVisibleWallObjectsMax = Math.max(this.lastVisibleWallObjectsMax, visibleWallObjects);
		this.lastVisibleGroundItemsMax = Math.max(this.lastVisibleGroundItemsMax, visibleGroundItems);
	}

	public synchronized void recordVisibilityRegionCacheAccess(final boolean hit) {
		this.lastVisibilityRegionCacheRequests++;
		if (hit) {
			this.lastVisibilityRegionCacheHits++;
		} else {
			this.lastVisibilityRegionCacheMisses++;
		}
	}

	public synchronized void recordVisibilityObjectCacheAccess(final boolean hit) {
		this.lastVisibilityObjectCacheRequests++;
		if (hit) {
			this.lastVisibilityObjectCacheHits++;
		} else {
			this.lastVisibilityObjectCacheMisses++;
		}
	}

	public synchronized void recordVisibilityObjectSnapshotCacheAccess(final boolean hit) {
		this.lastVisibilityObjectSnapshotCacheRequests++;
		if (hit) {
			this.lastVisibilityObjectSnapshotCacheHits++;
		} else {
			this.lastVisibilityObjectSnapshotCacheMisses++;
		}
	}

	public synchronized void recordVisibilityTickSnapshotCacheAccess(final boolean hit) {
		this.lastVisibilityTickSnapshotCacheRequests++;
		if (hit) {
			this.lastVisibilityTickSnapshotCacheHits++;
		} else {
			this.lastVisibilityTickSnapshotCacheMisses++;
		}
	}

	public synchronized void recordVisibilityObjectCacheClear(final int entriesCleared) {
		if (entriesCleared > 0) {
			this.lastVisibilityObjectCacheClears++;
			this.lastVisibilityObjectCacheEntriesCleared += entriesCleared;
		}
	}

	public synchronized void addVisibilityShadowMetrics(
		final long duration,
		final boolean playersMatch,
		final boolean npcsMatch,
		final boolean gameObjectsMatch,
		final boolean groundItemsMatch,
		final int mobRegionCount,
		final int objectRegionCount) {
		this.lastVisibilityShadowDuration += duration;
		this.lastVisibilityShadowSamples++;
		if (!playersMatch || !npcsMatch || !gameObjectsMatch || !groundItemsMatch) {
			this.lastVisibilityShadowMismatchSamples++;
		}
		if (!playersMatch) {
			this.lastVisibilityShadowPlayerMismatches++;
		}
		if (!npcsMatch) {
			this.lastVisibilityShadowNpcMismatches++;
		}
		if (!gameObjectsMatch) {
			this.lastVisibilityShadowGameObjectMismatches++;
		}
		if (!groundItemsMatch) {
			this.lastVisibilityShadowGroundItemMismatches++;
		}
		this.lastVisibilityShadowMobRegionsMax = Math.max(this.lastVisibilityShadowMobRegionsMax, mobRegionCount);
		this.lastVisibilityShadowObjectRegionsMax = Math.max(this.lastVisibilityShadowObjectRegionsMax, objectRegionCount);
	}

	public synchronized void addSceneBaselineMetrics(final int pageRecords, final int payloadBytes) {
		this.lastSceneBaselinePackets++;
		this.lastSceneBaselinePayloadBytes += payloadBytes;
		if (pageRecords > 0) {
			this.lastSceneBaselinePages++;
			this.lastSceneBaselineRecords += pageRecords;
		}
	}

	public synchronized void addMovementSnapshotMetrics(final int records, final int payloadBytes) {
		this.lastMovementSnapshotPackets++;
		this.lastMovementSnapshotRecords += records;
		this.lastMovementSnapshotPayloadBytes += payloadBytes;
		if (benchmarkTargetTicks > 0 && getCurrentTick() > benchmarkWarmupTicks) {
			this.benchmarkMovementSnapshotPackets++;
			this.benchmarkMovementSnapshotRecords += records;
			this.benchmarkMovementSnapshotPayloadBytes += payloadBytes;
		}
	}

	public synchronized void addNativeTerrainTransferMetrics(
		final int contextPackets,
		final int sectorSlots,
		final int availableSectors,
		final int payloadSectors,
		final int referencedSectors,
		final long rawBytes,
		final long wireBytes,
		final long cacheRequests,
		final long cacheHits,
		final long cacheMisses,
		final long wireBuildNanos,
		final int cacheEntries) {
		this.lastNativeTerrainContextPackets += contextPackets;
		this.lastNativeTerrainSectorSlots += sectorSlots;
		this.lastNativeTerrainAvailableSectors += availableSectors;
		this.lastNativeTerrainPayloadSectors += payloadSectors;
		this.lastNativeTerrainReferencedSectors += referencedSectors;
		this.lastNativeTerrainRawBytes += rawBytes;
		this.lastNativeTerrainWireBytes += wireBytes;
		this.lastNativeTerrainWireCacheRequests += cacheRequests;
		this.lastNativeTerrainWireCacheHits += cacheHits;
		this.lastNativeTerrainWireCacheMisses += cacheMisses;
		this.lastNativeTerrainWireBuildNanos += wireBuildNanos;
		this.lastNativeTerrainWireCacheEntriesMax = Math.max(
			this.lastNativeTerrainWireCacheEntriesMax, cacheEntries);
		if (benchmarkTargetTicks > 0
			&& getCurrentTick() > benchmarkWarmupTicks) {
			this.benchmarkNativeTerrainContextPackets += contextPackets;
			this.benchmarkNativeTerrainSectorSlots += sectorSlots;
			this.benchmarkNativeTerrainAvailableSectors += availableSectors;
			this.benchmarkNativeTerrainPayloadSectors += payloadSectors;
			this.benchmarkNativeTerrainReferencedSectors += referencedSectors;
			this.benchmarkNativeTerrainRawBytes += rawBytes;
			this.benchmarkNativeTerrainWireBytes += wireBytes;
			this.benchmarkNativeTerrainWireCacheRequests += cacheRequests;
			this.benchmarkNativeTerrainWireCacheHits += cacheHits;
			this.benchmarkNativeTerrainWireCacheMisses += cacheMisses;
			this.benchmarkNativeTerrainWireBuildNanos += wireBuildNanos;
			this.benchmarkNativeTerrainWireCacheEntriesMax = Math.max(
				this.benchmarkNativeTerrainWireCacheEntriesMax,
				cacheEntries);
		}
	}

	public synchronized void addSuppressedLegacyStaticSceneMetrics(final boolean wallPacket, final int records) {
		if (wallPacket) {
			this.lastSuppressedLegacyWallPackets++;
			this.lastSuppressedLegacyWallRecords += records;
		} else {
			this.lastSuppressedLegacySceneryPackets++;
			this.lastSuppressedLegacySceneryRecords += records;
		}
	}

	public synchronized void incrementLastDoCleanupDuration(final long duration) {
		this.lastDoCleanupDuration += duration;
	}

	public synchronized void incrementLastExecuteWalkToActionsDuration(final long duration) {
		this.lastExecuteWalkToActionsDuration += duration;
	}

	public synchronized void resetBenchmarkDurations() {
		this.lastIncomingPacketsDuration = 0;
		this.lastEventsDuration = 0;
		this.lastOutgoingPacketsDuration = 0;
		this.lastWorldUpdateDuration = 0;
		this.lastProcessPlayersDuration = 0;
		this.lastProcessNpcsDuration = 0;
		this.lastProcessNpcUnregisterDuration = 0;
		this.lastProcessNpcBehaviorDuration = 0;
		this.lastProcessNpcMovementDuration = 0;
		this.lastNpcIdleThrottleSkipped = 0;
		this.lastNpcBehaviorRoamDuration = 0;
		this.lastNpcBehaviorAggroDuration = 0;
		this.lastNpcBehaviorCombatDuration = 0;
		this.lastNpcBehaviorTackleDuration = 0;
		this.lastNpcBehaviorRetreatDuration = 0;
		this.lastNpcRoamEligibilityDuration = 0;
		this.lastNpcRoamAggroScanDuration = 0;
		this.lastNpcRoamTackleScanDuration = 0;
		this.lastNpcRoamRandomWalkDuration = 0;
		this.lastProcessMessageQueuesDuration = 0;
		this.lastUpdateClientsDuration = 0;
		this.lastUpdatePlayersDuration = 0;
		this.lastUpdatePlayerAppearancesDuration = 0;
		this.lastUpdateNpcsDuration = 0;
		this.lastUpdateNpcAppearancesDuration = 0;
		this.lastUpdateGameObjectsDuration = 0;
		this.lastUpdateWallObjectsDuration = 0;
		this.lastUpdateGroundItemsDuration = 0;
		this.lastUpdateTimeoutsDuration = 0;
		this.lastUpdateAppearanceKeepaliveDuration = 0;
		this.lastVisibilitySnapshotDuration = 0;
		this.lastVisibilitySnapshotSamples = 0;
		this.lastVisiblePlayersTotal = 0;
		this.lastVisibleNpcsTotal = 0;
		this.lastVisibleSceneryTotal = 0;
		this.lastVisibleWallObjectsTotal = 0;
		this.lastVisibleGroundItemsTotal = 0;
		this.lastVisiblePlayersMax = 0;
		this.lastVisibleNpcsMax = 0;
		this.lastVisibleSceneryMax = 0;
		this.lastVisibleWallObjectsMax = 0;
		this.lastVisibleGroundItemsMax = 0;
		this.lastVisibilityRegionCacheRequests = 0;
		this.lastVisibilityRegionCacheHits = 0;
		this.lastVisibilityRegionCacheMisses = 0;
		this.lastVisibilityObjectCacheRequests = 0;
		this.lastVisibilityObjectCacheHits = 0;
		this.lastVisibilityObjectCacheMisses = 0;
		this.lastVisibilityObjectCacheClears = 0;
		this.lastVisibilityObjectCacheEntriesCleared = 0;
		this.lastVisibilityObjectSnapshotCacheRequests = 0;
		this.lastVisibilityObjectSnapshotCacheHits = 0;
		this.lastVisibilityObjectSnapshotCacheMisses = 0;
		this.lastVisibilityTickSnapshotCacheRequests = 0;
		this.lastVisibilityTickSnapshotCacheHits = 0;
		this.lastVisibilityTickSnapshotCacheMisses = 0;
		this.lastVisibilityShadowDuration = 0;
		this.lastVisibilityShadowSamples = 0;
		this.lastVisibilityShadowMismatchSamples = 0;
		this.lastVisibilityShadowPlayerMismatches = 0;
		this.lastVisibilityShadowNpcMismatches = 0;
		this.lastVisibilityShadowGameObjectMismatches = 0;
		this.lastVisibilityShadowGroundItemMismatches = 0;
		this.lastVisibilityShadowMobRegionsMax = 0;
		this.lastVisibilityShadowObjectRegionsMax = 0;
		this.lastSceneBaselinePackets = 0;
		this.lastSceneBaselinePages = 0;
		this.lastSceneBaselineRecords = 0;
		this.lastSceneBaselinePayloadBytes = 0;
		this.lastMovementSnapshotPackets = 0;
		this.lastMovementSnapshotRecords = 0;
		this.lastMovementSnapshotPayloadBytes = 0;
		this.lastNativeTerrainContextPackets = 0;
		this.lastNativeTerrainSectorSlots = 0;
		this.lastNativeTerrainAvailableSectors = 0;
		this.lastNativeTerrainPayloadSectors = 0;
		this.lastNativeTerrainReferencedSectors = 0;
		this.lastNativeTerrainRawBytes = 0;
		this.lastNativeTerrainWireBytes = 0;
		this.lastNativeTerrainWireCacheRequests = 0;
		this.lastNativeTerrainWireCacheHits = 0;
		this.lastNativeTerrainWireCacheMisses = 0;
		this.lastNativeTerrainWireBuildNanos = 0;
		this.lastNativeTerrainWireCacheEntriesMax = 0;
		this.lastSuppressedLegacySceneryPackets = 0;
		this.lastSuppressedLegacySceneryRecords = 0;
		this.lastSuppressedLegacyWallPackets = 0;
		this.lastSuppressedLegacyWallRecords = 0;
		this.lastDoCleanupDuration = 0;
		this.lastExecuteWalkToActionsDuration = 0;
		this.lastOutgoingPayloadBytes = 0;
	}

	public void refreshWebsocketSSLContext(Player player) throws Exception {
		setSSLContext(loadWebsocketSSLFiles(getConfig().SSL_SERVER_CERT_PATH, getConfig().SSL_SERVER_KEY_PATH, player));
    }

	private static SslContext loadWebsocketSSLFiles(String sslServerCertPath, String sslServerKeyPath, Player player) throws Exception {
		SslContext sslContext = SslContextBuilder.forServer(new File(sslServerCertPath), new File(sslServerKeyPath)).build();

		X509Certificate websocketCert = (X509Certificate) CertificateFactory.getInstance("X.509")
			.generateCertificate(Files.newInputStream(Paths.get(sslServerCertPath)));

		LOGGER.info("Websocket Certificate Not Valid Before - {} ", websocketCert.getNotBefore());
		LOGGER.info("Websocket Certificate Not Valid After  - {} ", websocketCert.getNotAfter());
		LOGGER.info("Certificate Issuer - {} ", websocketCert.getIssuerX500Principal());
		websocketCert.checkValidity();

		if (player != null) {
			player.message("Successfully reloaded websocket certificate files!");
			player.message("Valid until " + websocketCert.getNotAfter());
			LOGGER.info("Websockets Certificate reloaded by " + player.getUsername());
		}
		return sslContext;
	}

	private void setSSLContext(SslContext sslContext) {
		this.sslcontext = sslContext;
	}

	public SslContext getSSLContext() {
		return this.sslcontext;
	}
}
