#!/usr/bin/env python3
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOT = ROOT / "tools/world-builder/src"


class WorldBuilderSupervisionTest(unittest.TestCase):
    def test_readiness_lock_shutdown_and_bounded_run_metadata(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-supervision-") as temp:
            base = Path(temp)
            classes = base / "classes"
            classes.mkdir()
            harness = base / "com/openrsc/worldbuilder/WorldBuilderSupervisorHarness.java"
            harness.parent.mkdir(parents=True)
            harness.write_text(
                textwrap.dedent(
                    """
                    package com.openrsc.worldbuilder;

                    import java.net.ServerSocket;
                    import java.net.Socket;
                    import java.nio.charset.StandardCharsets;
                    import java.nio.channels.FileChannel;
                    import java.nio.channels.FileLock;
                    import java.nio.file.Files;
                    import java.nio.file.Path;
                    import java.nio.file.Paths;
                    import java.nio.file.StandardOpenOption;
                    import java.util.Arrays;
                    import java.util.List;

                    public final class WorldBuilderSupervisorHarness {
                        private static void require(boolean value, String message) {
                            if (!value) throw new AssertionError(message);
                        }

                        private static String javaExecutable() {
                            return Paths.get(System.getProperty("java.home"), "bin", "java").toString();
                        }

                        private static List<String> command(String classes, String nested, Path workspace, int port) {
                            return Arrays.asList(javaExecutable(), "-cp", classes,
                                "com.openrsc.worldbuilder.WorldBuilderSupervisorHarness$" + nested,
                                workspace.toString(), Integer.toString(port));
                        }

                        public static void main(String[] args) throws Exception {
                            Path workspace = Paths.get(args[0]);
                            int port = Integer.parseInt(args[1]);
                            String classes = args[2];
                            Files.createDirectories(workspace.resolve("working/server/run/world-builder"));
                            Files.createDirectories(workspace.resolve("working/server/inc/sqlite"));
                            Files.createDirectories(workspace.resolve("working/Client_Base"));
                            Files.createDirectories(workspace.resolve("logs"));
                            Files.createDirectories(workspace.resolve("run"));
                            Files.write(workspace.resolve("runtime.json"),
                                ("{\\\"sourceFingerprintSha256\\\":\\\""
                                    + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                                    + "\\\",\\\"port\\\":" + port + "}\\n").getBytes(StandardCharsets.UTF_8));

                            WorldBuilderProcessSupervisor supervisor = new WorldBuilderProcessSupervisor();
                            List<String> realServer = WorldBuilderProcessSupervisor.defaultServerCommand(
                                workspace);
                            int classpathFlag = realServer.indexOf("-cp");
                            require(classpathFlag >= 0 && classpathFlag + 1 < realServer.size(),
                                "Builder server classpath argument");
                            String serverClasspath = realServer.get(classpathFlag + 1);
                            String expectedClasspath = String.join(System.getProperty("path.separator"),
                                "lib/*", "core.jar", "plugins.jar");
                            require(expectedClasspath.equals(serverClasspath),
                                "dependency jars must precede the fat server jar for Java 9+");
                            List<String> realClient = WorldBuilderProcessSupervisor.defaultClientCommand(
                                workspace, port);
                            require(realClient.contains("-Dsun.java2d.opengl=false"),
                                "Builder must not start a second Java2D OpenGL pipeline");
                            require(realClient.contains("-Dspoiledmilk.openglWindowMode=borderless-fullscreen"),
                                "Builder must start with borderless presentation");
                            require(realClient.contains("-Dspoiledmilk.openglVsync=true"),
                                "Builder must start with vsync enabled");
                            require(!realClient.contains("-Dsun.java2d.opengl=true"),
                                "unsafe Java2D OpenGL launch flag");
                            require(WorldBuilderProcessSupervisor.readPreparedPort(workspace) == port,
                                "prepared runtime port");
                            List<String> server = command(classes, "FakeServer", workspace, port);
                            List<String> client = command(classes, "FakeClient", workspace, port);
                            Path generatedIpBans = workspace.resolve("working/server/ipbans.txt");
                            Path escapedIpBans = workspace.getParent().resolve("ipbans.txt");
                            require(!Files.exists(generatedIpBans), "clean workspace starts without IP bans");
                            require(!Files.exists(escapedIpBans), "no external IP bans before startup");
                            int first = supervisor.superviseWithCommands(workspace, port, server, client, 5000L);
                            require(first == 0, "first run");
                            require(Files.isRegularFile(generatedIpBans),
                                "server-generated IP bans remain in workspace");
                            require(Files.size(generatedIpBans) == 0L,
                                "server-generated IP bans start empty");
                            require(!Files.exists(escapedIpBans),
                                "server-generated IP bans never escape workspace");
                            require(!Files.exists(workspace.resolve("run/server.pid")), "server pid cleanup");
                            require(!Files.exists(workspace.resolve("run/client.pid")), "client pid cleanup");
                            require(!Files.exists(workspace.resolve("working/server/run/world-builder/ready")), "ready cleanup");
                            String receipt = new String(Files.readAllBytes(workspace.resolve("run/last-run.json")),
                                StandardCharsets.UTF_8);
                            require(receipt.contains("\\\"serverExit\\\": 0"), "server exit receipt");
                            require(receipt.contains("\\\"clientExit\\\": 0"), "client exit receipt");

                            int second = supervisor.superviseWithCommands(workspace, port, server, client, 5000L);
                            require(second == 0, "lock must be released for second run");
                            require(Files.exists(workspace.resolve("logs/server.log.previous")), "one rotated server log");
                            require(Files.exists(workspace.resolve("logs/client.log.previous")), "one rotated client log");

                            Path lockPath = workspace.getParent().resolve("." + workspace.getFileName()
                                + ".world-builder.lock");
                            try (FileChannel channel = FileChannel.open(lockPath,
                                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                                 FileLock held = channel.lock()) {
                                boolean refused = false;
                                try {
                                    supervisor.superviseWithCommands(workspace, port, server, client, 5000L);
                                } catch (WorldBuilderDiscoveryException expected) {
                                    refused = expected.getMessage().contains("already running");
                                }
                                require(refused, "concurrent workspace lock");
                            }
                            System.out.println("supervision-ok");
                        }

                        public static final class FakeServer {
                            public static void main(String[] args) throws Exception {
                                Path workspace = Paths.get(args[0]);
                                int port = Integer.parseInt(args[1]);
                                Files.write(Paths.get("ipbans.txt"), new byte[0]);
                                Path control = workspace.resolve("working/server/run/world-builder");
                                Path credential = workspace.resolve("working/server/inc/sqlite/world-builder.credential");
                                Files.createDirectories(control);
                                Files.createDirectories(credential.getParent());
                                Files.write(credential, "Abcdefghijk23456789Z".getBytes(StandardCharsets.US_ASCII));
                                try (ServerSocket listener = new ServerSocket(port, 1,
                                        java.net.InetAddress.getByName("127.0.0.1"))) {
                                    listener.setSoTimeout(100);
                                    Files.write(control.resolve("ready"), "ready\\n".getBytes(StandardCharsets.US_ASCII));
                                    while (!Files.exists(control.resolve("shutdown.request"))) {
                                        try (Socket ignored = listener.accept()) {
                                        } catch (java.net.SocketTimeoutException expected) {
                                        }
                                    }
                                    Files.deleteIfExists(control.resolve("shutdown.request"));
                                    Files.deleteIfExists(control.resolve("ready"));
                                }
                            }
                        }

                        public static final class FakeClient {
                            public static void main(String[] args) throws Exception {
                                Thread.sleep(350L);
                            }
                        }
                    }
                    """
                ),
                encoding="utf-8",
            )
            sources = sorted(str(path) for path in SOURCE_ROOT.rglob("*.java"))
            subprocess.run(
                [
                    "javac",
                    "-source",
                    "8",
                    "-target",
                    "8",
                    "-d",
                    str(classes),
                    *sources,
                    str(harness),
                ],
                cwd=ROOT,
                check=True,
                capture_output=True,
                text=True,
            )
            workspace = base / "workspace"
            result = subprocess.run(
                [
                    "java",
                    "-cp",
                    str(classes),
                    "com.openrsc.worldbuilder.WorldBuilderSupervisorHarness",
                    str(workspace),
                    "43673",
                    str(classes),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
                timeout=20,
            )
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual("supervision-ok\n", result.stdout)


if __name__ == "__main__":
    unittest.main()
