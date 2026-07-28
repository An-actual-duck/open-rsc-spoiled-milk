#!/usr/bin/env python3
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_MODE = ROOT / "server/src/com/openrsc/server/content/worldedit/WorldBuilderMode.java"
STORAGE_CONTEXT = ROOT / "server/src/com/openrsc/server/content/worldedit/WorldEditStorageContext.java"
DATABASE_TYPE = ROOT / "server/src/com/openrsc/server/database/DatabaseType.java"
CLIENT_PROFILE = ROOT / "Client_Base/src/orsc/WorldBuilderClientProfile.java"


class WorldBuilderRuntimeTest(unittest.TestCase):
    def compile_and_run(self, sources, harness_name, harness_source, *args, run_cwd=None):
        with tempfile.TemporaryDirectory(prefix="world-builder-runtime-") as temp:
            temp_path = Path(temp)
            harness = temp_path / (harness_name.replace(".", "/") + ".java")
            harness.parent.mkdir(parents=True, exist_ok=True)
            harness.write_text(textwrap.dedent(harness_source), encoding="utf-8")
            classes = temp_path / "classes"
            classes.mkdir()
            subprocess.run(
                [
                    "javac",
                    "-source",
                    "8",
                    "-target",
                    "8",
                    "-d",
                    str(classes),
                    *map(str, sources),
                    str(harness),
                ],
                cwd=ROOT,
                check=True,
                capture_output=True,
                text=True,
            )
            result = subprocess.run(
                ["java", "-cp", str(classes), harness_name, *map(str, args)],
                cwd=run_cwd or ROOT,
                capture_output=True,
                text=True,
            )
            if result.returncode != 0:
                raise AssertionError(result.stdout + result.stderr)
            return result.stdout

    def test_server_mode_is_opt_in_and_fail_closed(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-server-stub-") as temp:
            stub = Path(temp) / "com/openrsc/server/ServerConfiguration.java"
            stub.parent.mkdir(parents=True)
            stub.write_text(
                textwrap.dedent(
                    """
                    package com.openrsc.server;
                    import com.openrsc.server.database.DatabaseType;
                    public class ServerConfiguration {
                        public boolean WORLD_BUILDER_MODE;
                        public boolean WORLD_BUILDER_LAYERED_REVIEW_MODE;
                        public boolean WANT_LAYERED_PLAYER_LOCATION_AUTHORITY;
                        public boolean WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
                        public boolean WANT_LAYERED_PROTOCOL_CLIENT_AUTHORITY;
                        public boolean WANT_LAYERED_NATIVE_TERRAIN_PACKAGE;
                        public String LAYERED_NATIVE_WORLD_RUNTIME_PROFILE;
                        public String SERVER_BIND_ADDRESS;
                        public DatabaseType DB_TYPE;
                        public String DB_NAME;
                        public String DB_TABLE_PREFIX;
                        public int MAX_PLAYERS;
                        public boolean WANT_PACKET_REGISTER;
                        public boolean ALLOW_IN_GAME_WORLD_EDITOR;
                        public boolean WANT_CUSTOM_LANDSCAPE;
                        public boolean WANT_MYWORLD;
                    }
                    """
                ),
                encoding="utf-8",
            )
            output = self.compile_and_run(
                [stub, DATABASE_TYPE, SERVER_MODE],
                "WorldBuilderModeHarness",
                """
                import com.openrsc.server.ServerConfiguration;
                import com.openrsc.server.content.worldedit.WorldBuilderMode;
                import com.openrsc.server.database.DatabaseType;

                public final class WorldBuilderModeHarness {
                    private static ServerConfiguration config(boolean enabled, String host) {
                        ServerConfiguration c = new ServerConfiguration();
                        c.WORLD_BUILDER_MODE = enabled;
                        c.SERVER_BIND_ADDRESS = host;
                        c.DB_TYPE = DatabaseType.SQLITE;
                        c.DB_NAME = "world_builder";
                        c.DB_TABLE_PREFIX = "";
                        c.MAX_PLAYERS = 1;
                        c.WANT_PACKET_REGISTER = false;
                        c.ALLOW_IN_GAME_WORLD_EDITOR = true;
                        c.WANT_CUSTOM_LANDSCAPE = true;
                        c.WANT_MYWORLD = true;
                        return c;
                    }

                    private static void require(boolean value, String message) {
                        if (!value) throw new AssertionError(message);
                    }

                    public static void main(String[] args) {
                        ServerConfiguration ordinary = config(false, "0.0.0.0");
                        ordinary.DB_TYPE = DatabaseType.MYSQL;
                        ordinary.DB_NAME = "live";
                        WorldBuilderMode.validate(ordinary);
                        ordinary.LAYERED_NATIVE_WORLD_RUNTIME_PROFILE =
                            "spoiled-milk-builder-draft";
                        boolean publicDraftRefused = false;
                        try {
                            WorldBuilderMode.validate(ordinary);
                        } catch (IllegalArgumentException expected) {
                            publicDraftRefused = expected.getMessage().contains(
                                "restricted to isolated World Builder mode");
                        }
                        require(publicDraftRefused, "public Builder draft profile");

                        WorldBuilderMode.validate(config(true, "127.0.0.1"));
                        ServerConfiguration layered = config(true, "127.0.0.1");
                        layered.WORLD_BUILDER_LAYERED_REVIEW_MODE = true;
                        boolean incompleteLayeredRefused = false;
                        try {
                            WorldBuilderMode.validate(layered);
                        } catch (IllegalArgumentException expected) {
                            incompleteLayeredRefused = expected.getMessage().contains(
                                "complete Spoiled Milk native package authority");
                        }
                        require(incompleteLayeredRefused, "incomplete layered Builder authority");
                        layered.WANT_LAYERED_PLAYER_LOCATION_AUTHORITY = true;
                        layered.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
                        layered.WANT_LAYERED_PROTOCOL_CLIENT_AUTHORITY = true;
                        layered.WANT_LAYERED_NATIVE_TERRAIN_PACKAGE = true;
                        layered.LAYERED_NATIVE_WORLD_RUNTIME_PROFILE =
                            "spoiled-milk-builder-draft";
                        WorldBuilderMode.validate(layered);
                        require(WorldBuilderMode.isBuilderAccount("builder"), "identity case");
                        require(!WorldBuilderMode.isBuilderAccount("DevDuck"), "identity scope");
                        require(WorldBuilderMode.isLoopbackAddress("::1"), "IPv6 loopback");

                        ServerConfiguration unsafe = config(true, "0.0.0.0");
                        unsafe.DB_NAME = "myworld_dev";
                        unsafe.MAX_PLAYERS = 100;
                        unsafe.WANT_PACKET_REGISTER = true;
                        boolean refused = false;
                        try {
                            WorldBuilderMode.validate(unsafe);
                        } catch (IllegalArgumentException expected) {
                            refused = expected.getMessage().contains("loopback")
                                && expected.getMessage().contains("world_builder")
                                && expected.getMessage().contains("max_players")
                                && expected.getMessage().contains("want_packet_register");
                        }
                        require(refused, "unsafe Builder configuration must be refused");
                        System.out.println("server-mode-ok");
                    }
                }
                """,
            )
            self.assertEqual("server-mode-ok\n", output)

    def test_client_profile_is_explicit_bounded_and_loopback_only(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-client-fixture-") as fixture:
            fixture_path = Path(fixture)
            credential = fixture_path / "builder.credential"
            credential.write_text("Abcdefghijk23456789Z", encoding="ascii")
            invalid = fixture_path / "invalid.credential"
            invalid.write_text("not-valid", encoding="ascii")

            stub = fixture_path / "orsc/Config.java"
            stub.parent.mkdir(parents=True)
            stub.write_text(
                "package orsc; public final class Config { "
                "public static String SERVER_IP = \"unchanged\"; public static int SERVER_PORT = 12; }\n",
                encoding="utf-8",
            )
            output = self.compile_and_run(
                [stub, CLIENT_PROFILE],
                "orsc.WorldBuilderClientProfileHarness",
                """
                package orsc;

                public final class WorldBuilderClientProfileHarness {
                    private static void require(boolean value, String message) {
                        if (!value) throw new AssertionError(message);
                    }

                    private static void expectRefusal(String host, String port, String credential) {
                        System.setProperty(WorldBuilderClientProfile.ENABLED_PROPERTY, "true");
                        System.setProperty(WorldBuilderClientProfile.HOST_PROPERTY, host);
                        System.setProperty(WorldBuilderClientProfile.PORT_PROPERTY, port);
                        System.setProperty(WorldBuilderClientProfile.CREDENTIAL_FILE_PROPERTY, credential);
                        try {
                            WorldBuilderClientProfile.initializeFromSystemProperties();
                            throw new AssertionError("unsafe client profile was accepted");
                        } catch (IllegalArgumentException expected) {
                        }
                    }

                    public static void main(String[] args) {
                        System.clearProperty(WorldBuilderClientProfile.ENABLED_PROPERTY);
                        WorldBuilderClientProfile.initializeFromSystemProperties().applyConnection();
                        require(!WorldBuilderClientProfile.isEnabled(), "profile default");
                        require("unchanged".equals(Config.SERVER_IP) && Config.SERVER_PORT == 12,
                            "disabled profile must not change normal connection");

                        System.setProperty(WorldBuilderClientProfile.ENABLED_PROPERTY, "true");
                        System.setProperty(WorldBuilderClientProfile.HOST_PROPERTY, "127.0.0.1");
                        System.setProperty(WorldBuilderClientProfile.PORT_PROPERTY, "43615");
                        System.setProperty(WorldBuilderClientProfile.CREDENTIAL_FILE_PROPERTY, args[0]);
                        System.setProperty(WorldBuilderClientProfile.PROJECT_NAME_PROPERTY, "Lumbridge Rebuild");
                        System.setProperty(WorldBuilderClientProfile.SOURCE_REVISION_PROPERTY,
                            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
                        WorldBuilderClientProfile profile =
                            WorldBuilderClientProfile.initializeFromSystemProperties();
                        profile.applyConnection();
                        require(profile.isEnabled(), "profile enabled");
                        require("Builder".equals(profile.username()), "fixed identity");
                        require("Abcdefghijk23456789Z".equals(profile.credential()), "credential");
                        require("Lumbridge Rebuild".equals(profile.projectName()), "project name");
                        require("0123456789ab".equals(profile.sourceRevisionShort()), "source revision");
                        require("127.0.0.1".equals(Config.SERVER_IP) && Config.SERVER_PORT == 43615,
                            "explicit connection");

                        System.setProperty(WorldBuilderClientProfile.LAYERED_REVIEW_PROPERTY, "true");
                        System.setProperty(WorldBuilderClientProfile.LAYERED_PACKAGE_ID_PROPERTY,
                            "rsc-remastered.spoiled-milk-layered-world");
                        System.setProperty(WorldBuilderClientProfile.LAYERED_PACKAGE_VERSION_PROPERTY, "0.4.0");
                        System.setProperty(WorldBuilderClientProfile.LAYERED_MANIFEST_SHA256_PROPERTY,
                            "ed93e345a93c88cd22bcf694b5ac121b7117faa213a77af27bc90bd7faead789");
                        System.setProperty(WorldBuilderClientProfile.LAYERED_WORLD_SPACE_PROPERTY, "global");
                        System.setProperty(WorldBuilderClientProfile.LAYERED_LEVELS_PROPERTY, "-1,0,1,2,10");
                        profile = WorldBuilderClientProfile.initializeFromSystemProperties();
                        require(profile.isLayeredReview(), "layered review enabled");
                        require(!profile.isLayeredTerrainDraft(), "review is not writable by default");
                        require(profile.declaresLayer(-1) && profile.declaresLayer(2)
                            && profile.declaresLayer(10)
                            && !profile.declaresLayer(-2), "declared signed levels");
                        require("-1,0,1,2,10".equals(profile.layeredLevelsLabel()), "level label");
                        require("ed93e345a93c".equals(profile.layeredManifestShort()), "manifest identity");
                        System.setProperty(
                            WorldBuilderClientProfile.LAYERED_TERRAIN_DRAFT_PROPERTY, "true");
                        profile = WorldBuilderClientProfile.initializeFromSystemProperties();
                        require(profile.isLayeredTerrainDraft(), "terrain draft enabled");

                        expectRefusal("0.0.0.0", "43615", args[0]);
                        expectRefusal("127.0.0.1", "0", args[0]);
                        expectRefusal("127.0.0.1", "43615", args[1]);
                        System.out.println("client-profile-ok");
                    }
                }
                """,
                credential,
                invalid,
            )
            self.assertEqual("client-profile-ok\n", output)

    def test_storage_context_is_explicit_contained_and_symlink_safe(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-storage-") as temp:
            fixture = Path(temp)
            workspace = fixture / "project"
            server = workspace / "working/server"
            client = workspace / "working/Client_Base"
            source = workspace / "source"
            server_terrain = server / "conf/server/data/Custom_Landscape.orsc"
            client_terrain = client / "Cache/video/Custom_Landscape.orsc"
            server_terrain.parent.mkdir(parents=True)
            client_terrain.parent.mkdir(parents=True)
            source.mkdir(parents=True)
            server_terrain.write_bytes(b"terrain")
            client_terrain.write_bytes(b"terrain")

            stub = fixture / "stub/com/openrsc/server/ServerConfiguration.java"
            stub.parent.mkdir(parents=True)
            stub.write_text(
                textwrap.dedent(
                    """
                    package com.openrsc.server;
                    public class ServerConfiguration {
                        public boolean WORLD_BUILDER_MODE;
                        public boolean WANT_CUSTOM_LANDSCAPE = true;
                        public boolean MEMBER_WORLD = true;
                        public String CONFIG_DIR = "conf/server";
                    }
                    """
                ),
                encoding="utf-8",
            )
            output = self.compile_and_run(
                [stub, STORAGE_CONTEXT],
                "WorldEditStorageContextHarness",
                """
                import com.openrsc.server.ServerConfiguration;
                import com.openrsc.server.content.worldedit.WorldEditStorageContext;
                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.nio.file.Paths;

                public final class WorldEditStorageContextHarness {
                    private static void require(boolean value, String message) {
                        if (!value) throw new AssertionError(message);
                    }

                    public static void main(String[] args) throws Exception {
                        Path workspace = Paths.get(args[0]).toRealPath();
                        ServerConfiguration config = new ServerConfiguration();
                        config.WORLD_BUILDER_MODE = false;
                        System.clearProperty(WorldEditStorageContext.WORKSPACE_PROPERTY);
                        WorldEditStorageContext ordinary = WorldEditStorageContext.create(config);
                        require(!ordinary.isBuilderMode(), "ordinary default");
                        require(ordinary.configDirectory().equals(
                            Paths.get("").toAbsolutePath().normalize().resolve("conf/server")),
                            "ordinary relative layout changed");

                        config.WORLD_BUILDER_MODE = true;
                        boolean missingRefused = false;
                        try { WorldEditStorageContext.create(config); }
                        catch (java.io.IOException expected) { missingRefused = expected.getMessage().contains("property"); }
                        require(missingRefused, "missing workspace property");

                        System.setProperty(WorldEditStorageContext.WORKSPACE_PROPERTY, workspace.toString());
                        WorldEditStorageContext builder = WorldEditStorageContext.create(config);
                        require(builder.isBuilderMode(), "builder mode");
                        require(builder.sourceRoot().equals(workspace.resolve("source")), "source owner");
                        require(builder.workingRoot().equals(workspace.resolve("working")), "working owner");
                        require(builder.terrainArchive(config).startsWith(builder.workingRoot()), "server terrain owner");
                        require(builder.clientTerrainArchive().startsWith(builder.workingRoot()), "client terrain owner");
                        require(builder.terrainBackupDirectory(builder.terrainArchive(config))
                            .equals(workspace.resolve("backups/terrain")), "backup owner");
                        boolean escapeRefused = false;
                        try { builder.validateWorkingAuthoredFile(workspace.getParent().resolve("escape.json")); }
                        catch (java.io.IOException expected) { escapeRefused = true; }
                        require(escapeRefused, "working path traversal");

                        Path clientTerrain = workspace.resolve(
                            "working/Client_Base/Cache/video/Custom_Landscape.orsc");
                        Path outside = workspace.getParent().resolve("outside.orsc");
                        Files.write(outside, new byte[] {1});
                        Files.delete(clientTerrain);
                        Files.createSymbolicLink(clientTerrain, outside);
                        boolean symlinkRefused = false;
                        try { WorldEditStorageContext.create(config); }
                        catch (java.io.IOException expected) { symlinkRefused = true; }
                        require(symlinkRefused, "symlinked authored file");
                        System.out.println("storage-context-ok");
                    }
                }
                """,
                workspace,
                run_cwd=server,
            )
            self.assertEqual("storage-context-ok\n", output)

    def test_runtime_wiring_preserves_authoritative_paths(self):
        config = (ROOT / "server/src/com/openrsc/server/ServerConfiguration.java").read_text()
        server = (ROOT / "server/src/com/openrsc/server/Server.java").read_text()
        login = (ROOT / "server/src/com/openrsc/server/login/LoginRequest.java").read_text()
        shared_login = (ROOT / "server/plugins/com/openrsc/server/plugins/shared/PlayerLogin.java").read_text()
        command = (
            ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
        ).read_text()
        client = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text()
        client_world = (
            ROOT / "Client_Base/src/orsc/graphics/three/World.java"
        ).read_text()
        editor = (
            ROOT / "Client_Base/src/com/openrsc/interfaces/misc/WorldEditorInterface.java"
        ).read_text()
        editor_handler = (
            ROOT / "server/src/com/openrsc/server/net/rsc/handlers/WorldEditorHandler.java"
        ).read_text()
        parser = (
            ROOT / "server/src/com/openrsc/server/net/rsc/parsers/impl/PayloadCustomParser.java"
        ).read_text()

        self.assertIn('tryReadBool("world_builder_mode").orElse(false)', config)
        self.assertIn('tryReadBool("world_builder_layered_review_mode").orElse(false)', config)
        self.assertLess(server.index("WorldBuilderMode.validate(getConfig())"), server.index("packetFilter ="))
        self.assertIn("WorldEditStorageContext.create(getConfig())", server)
        self.assertIn("WorldBuilderAccountProvisioner.provision(this)", server)
        self.assertIn("WorldBuilderRuntimeControl.start(this)", server)
        self.assertIn("!WorldBuilderMode.isBuilderAccount(username)", login)
        self.assertIn("WorldBuilderPlayerSession.activate(player)", shared_login)
        self.assertIn("WorldEditorAccessService.open(player)", command)
        self.assertIn("player.setCacheInvulnerable(true)", (
            ROOT / "server/src/com/openrsc/server/content/worldedit/WorldBuilderPlayerSession.java"
        ).read_text())
        self.assertIn("isAndroid() || !WorldBuilderClientProfile.isEnabled()", client)
        self.assertIn("profile.applyConnection()", client)
        self.assertIn("this.autoLoginTimeout = 3", client)
        self.assertIn("getLogicalPlayerY", client)
        self.assertIn('\"buildergoto \" + worldX + \" \" + worldY + \" \" + level', client)
        self.assertIn("Layered package review is read-only", editor)
        self.assertIn("declaresLayer(level)", editor)
        self.assertIn("WORLD_BUILDER_LAYERED_REVIEW_MODE", editor_handler)
        self.assertIn("inspectNativeTerrain(p,location)", editor_handler)
        self.assertIn("editor.plane=packet.readByte();", parser)
        self.assertIn("layeredBuilderGoTo(player, command, args)", command)
        self.assertIn("isLayeredBuilderMutationCommand(command)", command)
        supervisor = (
            ROOT / "tools/world-builder/src/com/openrsc/worldbuilder/WorldBuilderProcessSupervisor.java"
        ).read_text()
        layered_package = (
            ROOT / "tools/world-builder/src/com/openrsc/worldbuilder/WorldBuilderLayeredPackage.java"
        ).read_text()
        exporter = (
            ROOT / "tools/world-builder/src/com/openrsc/worldbuilder/WorldBuilderExporter.java"
        ).read_text()
        self.assertIn("-Dopenrsc.worldBuilderWorkspaceRoot=", supervisor)
        self.assertIn("-Dopenrsc.worldBuilderSourceRevision=", supervisor)
        self.assertIn("-Dopenrsc.worldBuilderLayeredReview=true", supervisor)
        self.assertIn("-Dopenrsc.worldBuilderLayeredTerrainDraft=", supervisor)
        self.assertIn("tile.groundOverlay = (byte) 8;", client_world)
        self.assertIn("isBuilderCreatedLevel(snapshot.getLevel())", client_world)
        self.assertIn("tile.editorPaintedOverlay = true;", client_world)
        self.assertIn("WorldBuilderLayeredReview.readIfPresent(workspace)", supervisor)
        self.assertIn("rsc-remastered.spoiled-milk-layered-world", layered_package)
        self.assertIn("Layered package contains missing or untracked files", layered_package)
        self.assertIn("Layered World Builder review projects are read-only", exporter)
        self.assertNotIn('workspace.resolve("server/run/world-builder")', supervisor)


if __name__ == "__main__":
    unittest.main()
