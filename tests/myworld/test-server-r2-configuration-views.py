#!/usr/bin/env python3

import os
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
CORE = SERVER / "core.jar"
SERVER_SOURCE = SERVER / "src/com/openrsc/server/Server.java"
ADMIN_SOURCE = SERVER / "plugins/com/openrsc/server/plugins/authentic/commands/Admins.java"
FILTER_SOURCE = SERVER / "src/com/openrsc/server/net/RSCPacketFilter.java"

PROBE_SOURCE = r"""
package com.openrsc.server;

import com.openrsc.server.config.ConfigurationValidationException;
import com.openrsc.server.config.DiagnosticsConfiguration;
import com.openrsc.server.config.ProcessNetworkConfiguration;
import com.openrsc.server.config.ServerConfigurationLoadResult;
import com.openrsc.server.config.ServerConfigurationLoader;
import java.util.Collections;

public final class ConfigurationViewProbe {
    private static void out(String key, Object value) {
        System.out.println("PROBE:" + key + "=" + value);
    }

    private static boolean immutable(ProcessNetworkConfiguration process) {
        try {
            process.getIgnoredNetworkExceptions().add("mutable");
            return false;
        } catch (UnsupportedOperationException expected) {
            return true;
        }
    }

    private static boolean exceptionImmutable(ConfigurationValidationException exception) {
        try {
            exception.getErrors().add("mutable");
            return false;
        } catch (UnsupportedOperationException expected) {
            return true;
        }
    }

    private static void invalidProcess() {
        ProcessNetworkConfiguration process = new ProcessNetworkConfiguration(
            "", 70000, 0, true, "", "", -1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            Collections.<String>emptyList(), Collections.<String>emptyList());
        out("processErrors", process.getValidationErrors().size());
        try {
            process.requireValid();
            out("processRejected", false);
        } catch (ConfigurationValidationException exception) {
            out("processRejected", true);
            out("exceptionImmutable", exceptionImmutable(exception));
        }
    }

    private static void invalidDiagnostics() {
        DiagnosticsConfiguration diagnostics = new DiagnosticsConfiguration(
            false, false, false, false, false, false, false, false, false,
            false, false, 4, 0, 0);
        out("diagnosticErrors", diagnostics.getValidationErrors().size());
        try {
            diagnostics.requireValid();
            out("diagnosticsRejected", false);
        } catch (ConfigurationValidationException exception) {
            out("diagnosticsRejected", true);
            out("exceptionImmutable", exceptionImmutable(exception));
        }
    }

    public static void main(String[] args) throws Exception {
		if ("loader".equals(args[0])) {
			ServerConfigurationLoadResult loaded = ServerConfigurationLoader.load(args[1]);
			ServerBootstrapComposition composition = ServerBootstrapComposition.prepare(loaded);
			out("loaderConfig", loaded.getLegacyConfiguration().configFile);
			out("persistenceParity", loaded.getPersistence().getDatabaseName().equals(loaded.getLegacyConfiguration().DB_NAME));
			out("worldParity", loaded.getWorldRuntime().getGameTick() == loaded.getLegacyConfiguration().GAME_TICK);
			out("compatibilityParity", loaded.getCompatibility().getClientVersion() == loaded.getLegacyConfiguration().CLIENT_VERSION);
			out("toolsParity", loaded.getTools().isWorldBuilderMode() == loaded.getLegacyConfiguration().WORLD_BUILDER_MODE);
			out("contentParity", loaded.getContent().isMyWorld() == loaded.getLegacyConfiguration().WANT_MYWORLD);
			out("noResources", !composition.ownsRuntimeResources());
			composition.close();
			composition.close();
			out("closed", composition.isClosed());
			return;
		}
        if ("invalid-process".equals(args[0])) {
            invalidProcess();
            return;
        }
        if ("invalid-diagnostics".equals(args[0])) {
            invalidDiagnostics();
            return;
        }

        ServerConfiguration legacy = new ServerConfiguration();
        legacy.initConfig(args[0]);
        ProcessNetworkConfiguration process = legacy.processNetworkConfiguration();
        DiagnosticsConfiguration diagnostics = legacy.diagnosticsConfiguration();
        process.requireValid();
        diagnostics.requireValid();

        boolean processParity =
            process.getServerBindAddress().equals(legacy.SERVER_BIND_ADDRESS)
            && process.getServerPort() == legacy.SERVER_PORT
            && process.getWebsocketPort() == legacy.WS_SERVER_PORT
            && process.isWebsocketsEnabled() == legacy.WANT_FEATURE_WEBSOCKETS
            && process.getSslServerCertPath().equals(legacy.SSL_SERVER_CERT_PATH)
            && process.getSslServerKeyPath().equals(legacy.SSL_SERVER_KEY_PATH)
            && process.getMaxConnectionsPerIp() == legacy.MAX_CONNECTIONS_PER_IP
            && process.getMaxConnectionsPerSecond() == legacy.MAX_CONNECTIONS_PER_SECOND
            && process.getMaxPacketsPerSecond() == legacy.MAX_PACKETS_PER_SECOND
            && process.getMaxLoginsPerSecond() == legacy.MAX_LOGINS_PER_SECOND
            && process.getMaxLoginsPerServerPerTick() == legacy.MAX_LOGINS_PER_SERVER_PER_TICK
            && process.getMaxPasswordGuessesPerFiveMinutes() == legacy.MAX_PASSWORD_GUESSES_PER_FIVE_MINUTES
            && process.getNetworkFloodIpBanMinutes() == legacy.NETWORK_FLOOD_IP_BAN_MINUTES
            && process.getMaxPlayers() == legacy.MAX_PLAYERS
            && process.getMaxPlayersPerIp() == legacy.MAX_PLAYERS_PER_IP
            && process.getSessionIdSenderTimer() == legacy.SESSION_ID_SENDER_TIMER
            && process.getIgnoredNetworkExceptions().equals(legacy.IGNORED_NETWORK_EXCEPTIONS)
            && process.getConnectionResetExceptions().equals(legacy.NETWORK_CONNECTION_RESET_EXCEPTIONS);
        boolean diagnosticParity =
            diagnostics.isDebug() == legacy.DEBUG
            && diagnostics.isPcapLogging() == legacy.WANT_PCAP_LOGGING
            && diagnostics.isBreakPidPriority() == legacy.WANT_THREADING__BREAK_PID_PRIORITY
            && diagnostics.isForceGcOnProfiling() == legacy.WANT_FORCE_GC_ON_PROFILING
            && diagnostics.isSyncVisibilityShadow() == legacy.WANT_SYNC_VISIBILITY_SHADOW
            && diagnostics.isSyncVisibilitySnapshotInput() == legacy.WANT_SYNC_VISIBILITY_SNAPSHOT_INPUT
            && diagnostics.isSyncVisibilityTickCache() == legacy.WANT_SYNC_VISIBILITY_TICK_CACHE
            && diagnostics.isSyncSceneBaseline() == legacy.WANT_SYNC_SCENE_BASELINE
            && diagnostics.isSyncMovementSnapshot() == legacy.WANT_SYNC_MOVEMENT_SNAPSHOT
            && diagnostics.isMovementStutterDiagnostics() == legacy.WANT_MOVEMENT_STUTTER_DIAGNOSTICS
            && diagnostics.isLayeredMapParityObserver() == legacy.WANT_LAYERED_MAP_PARITY_OBSERVER
            && diagnostics.getMovementStutterSummarySeconds() == legacy.MOVEMENT_STUTTER_DIAGNOSTIC_SUMMARY_SECONDS
            && diagnostics.getMovementStutterPollOutlierMs() == legacy.MOVEMENT_STUTTER_POLL_OUTLIER_MS
            && diagnostics.getMovementStutterTickOutlierMs() == legacy.MOVEMENT_STUTTER_TICK_OUTLIER_MS;

        out("configFile", legacy.configFile);
        out("bind", process.getServerBindAddress());
        out("tcp", process.getServerPort());
        out("ws", process.getWebsocketPort());
        out("websockets", process.isWebsocketsEnabled());
        out("maxConnectionsPerIp", process.getMaxConnectionsPerIp());
        out("maxConnectionsPerSecond", process.getMaxConnectionsPerSecond());
        out("maxPlayers", process.getMaxPlayers());
        out("maxPlayersPerIp", process.getMaxPlayersPerIp());
        out("pcap", diagnostics.isPcapLogging());
        out("sceneBaseline", diagnostics.isSyncSceneBaseline());
        out("movementSnapshot", diagnostics.isSyncMovementSnapshot());
        out("summarySeconds", diagnostics.getMovementStutterSummarySeconds());
        out("pollOutlierMs", diagnostics.getMovementStutterPollOutlierMs());
        out("tickOutlierMs", diagnostics.getMovementStutterTickOutlierMs());
        out("processParity", processParity);
        out("diagnosticParity", diagnosticParity);
        out("immutable", immutable(process));
        out("processOwnershipNotes", process.getOwnershipNotes().size());
        out("diagnosticOwnershipNotes", diagnostics.getOwnershipNotes().size());
        out("deprecatedKeys", process.getDeprecatedKeys().size() + diagnostics.getDeprecatedKeys().size());
    }
}
"""


class ServerR2ConfigurationViewsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not CORE.exists():
            raise AssertionError("Missing server/core.jar; run ./scripts/build-server.sh first")
        cls.temp = tempfile.TemporaryDirectory(prefix="server-r2-config-")
        cls.root = Path(cls.temp.name)
        source = cls.root / "ConfigurationViewProbe.java"
        source.write_text(textwrap.dedent(PROBE_SOURCE), encoding="utf-8")
        compile_result = subprocess.run(
            ["javac", "-source", "8", "-target", "8", "-cp", str(CORE),
             "-d", str(cls.root), str(source)],
            cwd=str(ROOT), text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )
        if compile_result.returncode:
            raise AssertionError(compile_result.stdout + compile_result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def run_probe(self, argument, files=()):
        case = Path(tempfile.mkdtemp(prefix="case-", dir=str(self.root)))
        shutil.copy2(SERVER / "connections.conf", case / "connections.conf")
        for name in files:
            shutil.copy2(SERVER / name, case / name)
        env = {key: value for key, value in os.environ.items() if not key.startswith("OPENRSC_")}
        result = subprocess.run(
            ["java", "-cp", os.pathsep.join((str(self.root), str(CORE))),
             "com.openrsc.server.ConfigurationViewProbe", argument],
            cwd=str(case), env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )
        values = {}
        for line in result.stdout.splitlines():
            if line.startswith("PROBE:"):
                key, value = line[len("PROBE:"):].split("=", 1)
                values[key] = value
        return result, values

    def assert_common_parity(self, values):
        self.assertEqual("true", values["processParity"])
        self.assertEqual("true", values["diagnosticParity"])
        self.assertEqual("true", values["immutable"])
        self.assertEqual("4", values["processOwnershipNotes"])
        self.assertEqual("4", values["diagnosticOwnershipNotes"])
        self.assertEqual("0", values["deprecatedKeys"])

    def test_myworld_profile_has_exact_legacy_parity(self):
        result, values = self.run_probe("myworld.conf", ("myworld.conf",))
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(
            ("myworld.conf", "127.0.0.1", "43615", "43515", "true", "20", "20", "100", "10"),
            tuple(values[key] for key in ("configFile", "bind", "tcp", "ws", "websockets",
                                          "maxConnectionsPerIp", "maxConnectionsPerSecond",
                                          "maxPlayers", "maxPlayersPerIp")),
        )
        self.assertEqual(("false", "false", "true", "60", "25", "160"),
                         tuple(values[key] for key in ("pcap", "sceneBaseline", "movementSnapshot",
                                                       "summarySeconds", "pollOutlierMs", "tickOutlierMs")))
        self.assert_common_parity(values)

    def test_host_profile_has_exact_legacy_parity(self):
        result, values = self.run_probe("myworld-host.conf", ("myworld-host.conf",))
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(
            ("myworld-host.conf", "0.0.0.0", "43605", "43505", "false", "6", "8", "25", "3"),
            tuple(values[key] for key in ("configFile", "bind", "tcp", "ws", "websockets",
                                          "maxConnectionsPerIp", "maxConnectionsPerSecond",
                                          "maxPlayers", "maxPlayersPerIp")),
        )
        self.assertEqual(("false", "true", "true", "60", "25", "160"),
                         tuple(values[key] for key in ("pcap", "sceneBaseline", "movementSnapshot",
                                                       "summarySeconds", "pollOutlierMs", "tickOutlierMs")))
        self.assert_common_parity(values)

    def test_missing_profile_uses_existing_defaults_without_listener(self):
        result, values = self.run_probe("missing.conf")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("Default values", values["configFile"])
        self.assertEqual(("0.0.0.0", "43594", "43494", "true"),
                         tuple(values[key] for key in ("bind", "tcp", "ws", "websockets")))
        self.assert_common_parity(values)

    def test_malformed_value_preserves_parser_failure_without_listener(self):
        case = Path(tempfile.mkdtemp(prefix="malformed-", dir=str(self.root)))
        shutil.copy2(SERVER / "connections.conf", case / "connections.conf")
        (case / "malformed.conf").write_text("server_port: not-a-number\n", encoding="utf-8")
        result = subprocess.run(
            ["java", "-cp", os.pathsep.join((str(self.root), str(CORE))),
             "com.openrsc.server.ConfigurationViewProbe", "malformed.conf"],
            cwd=str(case), text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)

    def test_loader_composes_configuration_without_runtime_resources_and_closes_idempotently(self):
        case = Path(tempfile.mkdtemp(prefix="loader-", dir=str(self.root)))
        shutil.copy2(SERVER / "connections.conf", case / "connections.conf")
        shutil.copy2(SERVER / "myworld.conf", case / "myworld.conf")
        result = subprocess.run(
            ["java", "-cp", os.pathsep.join((str(self.root), str(CORE))),
             "com.openrsc.server.ConfigurationViewProbe", "loader", "myworld.conf"],
            cwd=str(case), text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )
        values = dict(line[len("PROBE:"):].split("=", 1)
                      for line in result.stdout.splitlines() if line.startswith("PROBE:"))
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("myworld.conf", values["loaderConfig"])
        self.assertEqual(("true", "true", "true", "true", "true"),
                         tuple(values[key] for key in ("persistenceParity", "worldParity", "compatibilityParity", "toolsParity", "contentParity")))
        self.assertEqual("true", values["noResources"])
        self.assertEqual("true", values["closed"])

    def test_incompatible_typed_values_are_rejected_and_errors_are_immutable(self):
        result, values = self.run_probe("invalid-process")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("true", values["processRejected"])
        self.assertEqual("true", values["exceptionImmutable"])
        self.assertGreaterEqual(int(values["processErrors"]), 4)

        result, values = self.run_probe("invalid-diagnostics")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("true", values["diagnosticsRejected"])
        self.assertEqual("true", values["exceptionImmutable"])
        self.assertEqual("3", values["diagnosticErrors"])

    def test_validation_precedes_resources_and_listener_uses_frozen_endpoint_view(self):
        source = SERVER_SOURCE.read_text(encoding="utf-8")
        parsed = source.index("ServerConfigurationLoader.load(configFile)")
        projected = source.index("processNetworkConfiguration = loadedConfiguration.getProcessNetwork()")
        validated = source.index("WorldBuilderMode.validate(getConfig())")
        storage = source.index("WorldEditStorageContext.create(getConfig())")
        bind = source.index("bootstrap.bind(new InetSocketAddress")
        self.assertLess(parsed, projected)
        self.assertLess(projected, validated)
        self.assertLess(validated, storage)
        self.assertLess(storage, bind)
        self.assertIn("processNetworkConfiguration.getServerBindAddress()", source[bind:bind + 400])
        self.assertIn("processNetworkConfiguration.getServerPort()", source[bind:bind + 400])

    def test_runtime_administered_limits_remain_on_mutable_compatibility_facade(self):
        admins = ADMIN_SOURCE.read_text(encoding="utf-8")
        packet_filter = FILTER_SOURCE.read_text(encoding="utf-8")
        self.assertIn("MAX_CONNECTIONS_PER_SECOND =", admins)
        self.assertIn("MAX_CONNECTIONS_PER_IP =", admins)
        self.assertIn("getConfig().MAX_CONNECTIONS_PER_SECOND", packet_filter)
        self.assertIn("getConfig().MAX_CONNECTIONS_PER_IP", packet_filter)


if __name__ == "__main__":
    unittest.main()
