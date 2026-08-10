#!/usr/bin/env python3
"""Regression coverage for custom-client login framing before authentication."""

import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "server/core.jar"


HARNESS = r"""
import com.openrsc.server.net.ConnectionAttachment;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.RSCProtocolDecoder;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

public final class CustomLoginDecoderHarness {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static byte[] frame(int length, int opcode) {
        byte[] result = new byte[length + 2];
        result[0] = (byte) (length >>> 8);
        result[1] = (byte) length;
        result[2] = (byte) opcode;
        for (int index = 3; index < result.length; index++) {
            result[index] = (byte) (index & 255);
        }
        return result;
    }

    private static EmbeddedChannel channel(ConnectionAttachment attachment) {
        EmbeddedChannel result = new EmbeddedChannel(new RSCProtocolDecoder());
        result.attr(RSCProtocolDecoder.attachment).set(attachment);
        return result;
    }

    private static void requirePacket(
        EmbeddedChannel channel, int opcode, int length, String label) {
        Packet packet = channel.readInbound();
        require(packet != null, label + " did not deliver a packet");
        require(packet.getID() == opcode, label + " opcode");
        require(packet.getLength() == length, label + " payload length");
        require(channel.readInbound() == null, label + " delivered more than once");
    }

    private static void fragmented278() {
        ConnectionAttachment attachment = new ConnectionAttachment();
        EmbeddedChannel channel = channel(attachment);
        byte[] login = frame(278, 0);
        channel.writeInbound(Unpooled.wrappedBuffer(login, 0, 3));
        require(channel.readInbound() == null, "278 header was decoded as legacy");
        require(attachment.authenticClient.get() == null, "278 header classified early");
        channel.writeInbound(Unpooled.wrappedBuffer(login, 3, 129));
        require(channel.readInbound() == null, "fragmented 278 delivered early");
        require(attachment.authenticClient.get() == null, "fragmented 278 classified early");
        channel.writeInbound(Unpooled.wrappedBuffer(login, 132, login.length - 132));
        require(Short.valueOf((short) -1).equals(attachment.authenticClient.get()),
            "278 login did not commit custom framing");
        requirePacket(channel, 0, 277, "fragmented 278 login");
        channel.finishAndReleaseAll();
    }

    private static void coalescedAndBoundaryLengths() {
        for (int length : new int[] {255, 256, 278}) {
            ConnectionAttachment attachment = new ConnectionAttachment();
            EmbeddedChannel channel = channel(attachment);
            byte[] login = frame(length, 0);
            byte[] config = frame(1, 19);
            byte[] joined = new byte[login.length + config.length];
            System.arraycopy(login, 0, joined, 0, login.length);
            System.arraycopy(config, 0, joined, login.length, config.length);
            channel.writeInbound(Unpooled.wrappedBuffer(joined));
            require(Short.valueOf((short) -1).equals(attachment.authenticClient.get()),
                "boundary " + length + " did not classify custom");
            Packet first = channel.readInbound();
            Packet second = channel.readInbound();
            require(first != null && first.getID() == 0 && first.getLength() == length - 1,
                "boundary " + length + " login");
            require(second != null && second.getID() == 19 && second.getLength() == 0,
                "boundary " + length + " coalesced config");
            require(channel.readInbound() == null,
                "boundary " + length + " duplicate delivery");
            channel.finishAndReleaseAll();
        }
    }

    private static void malformedAndLegacyFrames() {
        ConnectionAttachment malformed = new ConnectionAttachment();
        EmbeddedChannel malformedChannel = channel(malformed);
        malformedChannel.writeInbound(Unpooled.wrappedBuffer(new byte[] {0, 0, 0}));
        require(malformedChannel.readInbound() == null, "zero-length frame delivered");
        require(malformed.authenticClient.get() == null, "zero-length frame classified");
        malformedChannel.finishAndReleaseAll();

        ConnectionAttachment oversized = new ConnectionAttachment();
        EmbeddedChannel oversizedChannel = channel(oversized);
        oversizedChannel.writeInbound(Unpooled.wrappedBuffer(new byte[] {(byte) 0xff, (byte) 0xff, 0}));
        require(oversizedChannel.readInbound() == null, "oversized partial frame delivered");
        require(oversized.authenticClient.get() == null, "oversized partial frame classified");
        oversizedChannel.finishAndReleaseAll();

        ConnectionAttachment legacy = new ConnectionAttachment();
        EmbeddedChannel legacyChannel = channel(legacy);
        legacyChannel.writeInbound(Unpooled.wrappedBuffer(new byte[] {1, 19, 2}));
        requirePacket(legacyChannel, 19, 1, "legacy configuration request");
        require(legacy.authenticClient.get() == null, "legacy request became custom");
        legacyChannel.finishAndReleaseAll();
    }

    public static void main(String[] args) {
        fragmented278();
        coalescedAndBoundaryLengths();
        malformedAndLegacyFrames();
        System.out.println("custom-login-decoder-ok");
    }
}
"""


class CustomLoginDecoderTest(unittest.TestCase):
    def test_fragmented_coalesced_malformed_boundary_and_single_delivery(self):
        self.assertTrue(CORE.is_file(), "run ./scripts/build-server.sh first")
        with tempfile.TemporaryDirectory(prefix="custom-login-decoder-") as temp:
            root = Path(temp)
            source = root / "CustomLoginDecoderHarness.java"
            source.write_text(textwrap.dedent(HARNESS), encoding="utf-8")
            subprocess.run(
                ["javac", "-source", "8", "-target", "8", "-cp", str(CORE),
                 "-d", str(root), str(source)],
                cwd=ROOT, check=True, capture_output=True, text=True,
            )
            result = subprocess.run(
                ["java", "-cp", str(CORE) + ":" + str(root),
                 "CustomLoginDecoderHarness"],
                cwd=ROOT, capture_output=True, text=True,
            )
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual("custom-login-decoder-ok\n", result.stdout)

    def test_authenticated_builder_binding_gates_native_terrain_readiness(self):
        """Run the production Builder binding gate before its terrain sender can run."""
        session = ROOT / (
            "server/src/com/openrsc/server/content/worldedit/"
            "WorldBuilderPlayerSession.java"
        )
        command = (ROOT / "server/src/com/openrsc/server/net/rsc/handlers/"
                   "CommandHandler.java").read_text(encoding="utf-8")
        updater = (ROOT / "server/src/com/openrsc/server/GameStateUpdater.java").read_text(
            encoding="utf-8"
        )
        self.assertIn('"builderbind".equalsIgnoreCase(cmd)', command)
        self.assertIn("WorldBuilderPlayerSession.bind(", command)
        self.assertIn("WorldBuilderPlayerSession.mayReceiveWorldState(player)", updater)
        stubs = {
            "com/openrsc/server/ServerConfiguration.java": """
                package com.openrsc.server;
                public class ServerConfiguration { public boolean WORLD_BUILDER_MODE; public boolean adaptive; public String MESSAGE_PREFIX = ""; }
            """,
            "com/openrsc/server/Server.java": """
                package com.openrsc.server;
                import com.openrsc.server.content.worldedit.AdaptiveWorldBuilderRuntimeSession;
                import com.openrsc.server.model.entity.player.Player;
                public class Server {
                    private final AdaptiveWorldBuilderRuntimeSession session;
                    public Server(String token) { session = new AdaptiveWorldBuilderRuntimeSession(token); }
                    public AdaptiveWorldBuilderRuntimeSession getAdaptiveWorldBuilderRuntimeSession() { return session; }
                    public Sessions getWorldEditorSessions() { return new Sessions(); }
                    public static class Sessions { public void closeFor(Player player) { } }
                }
            """,
            "com/openrsc/server/content/worldedit/AdaptiveWorldBuilderRuntimeSession.java": """
                package com.openrsc.server.content.worldedit;
                public class AdaptiveWorldBuilderRuntimeSession { private final String token; public AdaptiveWorldBuilderRuntimeSession(String token) { this.token = token; } public String getToken() { return token; } }
            """,
            "com/openrsc/server/content/worldedit/AdaptiveWorldBuilderRuntimeIdentity.java": """
                package com.openrsc.server.content.worldedit;
                import com.openrsc.server.ServerConfiguration;
                import com.openrsc.server.model.world.coordinate.WorldLocation;
                public final class AdaptiveWorldBuilderRuntimeIdentity {
                    public static boolean isAdaptive(ServerConfiguration c) { return c.adaptive; }
                    public static WorldLocation initialLocation(ServerConfiguration c) { return new WorldLocation(); }
                }
            """,
            "com/openrsc/server/content/worldedit/WorldBuilderMode.java": """
                package com.openrsc.server.content.worldedit;
                public final class WorldBuilderMode { public static boolean isBuilderAccount(String value) { return "builder".equals(value); } }
            """,
            "com/openrsc/server/content/worldedit/WorldEditorAccessService.java": """
                package com.openrsc.server.content.worldedit;
                import com.openrsc.server.model.entity.player.Player;
                public final class WorldEditorAccessService { public static boolean open(Player player) { return true; } }
            """,
            "com/openrsc/server/model/entity/player/Group.java": """
                package com.openrsc.server.model.entity.player;
                public final class Group { public static final int ADMIN = 3; }
            """,
            "com/openrsc/server/model/world/coordinate/WorldLocation.java": """
                package com.openrsc.server.model.world.coordinate;
                public class WorldLocation { }
            """,
            "com/openrsc/server/model/World.java": """
                package com.openrsc.server.model;
                import com.openrsc.server.Server;
                public class World { private final Server server; public World(Server server) { this.server = server; } public Server getServer() { return server; } }
            """,
            "com/openrsc/server/model/entity/player/Player.java": """
                package com.openrsc.server.model.entity.player;
                import com.openrsc.server.ServerConfiguration;
                import com.openrsc.server.model.World;
                import com.openrsc.server.model.world.coordinate.WorldLocation;
                import java.util.HashMap;
                import java.util.Map;
                public class Player {
                    private final ServerConfiguration config; private final String username; private final int group; private final World world; private final Map<String, Object> attributes = new HashMap<String, Object>();
                    public Player(ServerConfiguration config, String username, int group, World world) { this.config=config; this.username=username; this.group=group; this.world=world; }
                    public ServerConfiguration getConfig() { return config; } public String getUsername() { return username; } public int getGroupID() { return group; } public void setCacheInvulnerable(boolean value) { } public void teleportLayered(WorldLocation location, boolean value) { } public void message(String value) { }
                    public Object getAttribute(String key, Object fallback) { Object value=attributes.get(key); return value == null ? fallback : value; } public void setAttribute(String key, Object value) { attributes.put(key, value); } public void removeAttribute(String key) { attributes.remove(key); } public World getWorld() { return world; }
                }
            """,
            "org/apache/logging/log4j/Logger.java": """
                package org.apache.logging.log4j; public class Logger { public void error(String value) { } }
            """,
            "org/apache/logging/log4j/LogManager.java": """
                package org.apache.logging.log4j; public final class LogManager { public static Logger getLogger(Class<?> type) { return new Logger(); } }
            """,
            "BuilderBindingHarness.java": """
                import com.openrsc.server.Server; import com.openrsc.server.ServerConfiguration; import com.openrsc.server.content.worldedit.WorldBuilderPlayerSession; import com.openrsc.server.model.World; import com.openrsc.server.model.entity.player.Group; import com.openrsc.server.model.entity.player.Player;
                public final class BuilderBindingHarness { public static void main(String[] args) { ServerConfiguration config = new ServerConfiguration(); config.WORLD_BUILDER_MODE = true; config.adaptive = true; Player player = new Player(config, "builder", Group.ADMIN, new World(new Server("binding-token"))); WorldBuilderPlayerSession.activate(player); if (WorldBuilderPlayerSession.mayReceiveWorldState(player)) throw new AssertionError("native terrain escaped before builderbind"); WorldBuilderPlayerSession.bind(player, "binding-token"); if (!WorldBuilderPlayerSession.mayReceiveWorldState(player)) throw new AssertionError("native terrain remained blocked after authenticated builderbind"); System.out.println("builder-binding-ok"); } }
            """,
        }
        with tempfile.TemporaryDirectory(prefix="builder-binding-") as temp:
            root = Path(temp)
            sources = []
            for relative, contents in stubs.items():
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(textwrap.dedent(contents), encoding="utf-8")
                sources.append(path)
            subprocess.run(
                ["javac", "-source", "8", "-target", "8", "-d", str(root),
                 *map(str, sources), str(session)],
                cwd=ROOT, check=True, capture_output=True, text=True,
            )
            result = subprocess.run(
                ["java", "-cp", str(root), "BuilderBindingHarness"], cwd=ROOT,
                capture_output=True, text=True,
            )
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual("builder-binding-ok\n", result.stdout)


if __name__ == "__main__":
    unittest.main()
