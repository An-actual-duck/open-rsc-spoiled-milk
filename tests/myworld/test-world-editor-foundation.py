#!/usr/bin/env python3
import re
import struct
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVER_ARCHIVE = ROOT / "server/conf/server/data/Custom_Landscape.orsc"
CLIENT_ARCHIVE = ROOT / "Client_Base/Cache/video/Custom_Landscape.orsc"

class WorldEditorFoundationTest(unittest.TestCase):
    def test_client_server_archives_are_byte_identical(self):
        with zipfile.ZipFile(SERVER_ARCHIVE) as server, zipfile.ZipFile(CLIENT_ARCHIVE) as client:
            self.assertEqual(server.namelist(), client.namelist())
            for name in server.namelist():
                self.assertEqual(server.read(name), client.read(name), name)

    def test_all_sector_tiles_round_trip_byte_exact(self):
        with zipfile.ZipFile(SERVER_ARCHIVE) as archive:
            for name in archive.namelist():
                raw = archive.read(name)
                self.assertEqual(48 * 48 * 10, len(raw), name)
                rebuilt = bytearray()
                for offset in range(0, len(raw), 10):
                    fields = struct.unpack(">6Bi", raw[offset:offset + 10])
                    rebuilt.extend(struct.pack(">6Bi", *fields))
                self.assertEqual(raw, bytes(rebuilt), name)

    def test_coordinate_materialization_matches_archive_entry(self):
        samples = [(128, 640, 0), (512, 1700, 1), (216, 2840, 3)]
        with zipfile.ZipFile(SERVER_ARCHIVE) as archive:
            names = set(archive.namelist())
            for x, y, plane in samples:
                self.assertEqual(plane, y // 944)
                base_y = y - plane * 944
                sx, sy = (x + 2304) // 48, (base_y + 1776) // 48
                name = f"h{plane}x{sx}y{sy}"
                if name in names:
                    raw = archive.read(name)
                    index = (x % 48) * 48 + (base_y % 48)
                    self.assertEqual(10, len(raw[index * 10:index * 10 + 10]))

    def test_gate_and_authoritative_protocol_guards_are_present(self):
        config = (ROOT / "server/src/com/openrsc/server/ServerConfiguration.java").read_text()
        self.assertIn('tryReadBool("allow_in_game_world_editor").orElse(false)', config)
        for path in (ROOT / "server/myworld.conf", ROOT / "server/myworld-host.conf"):
            self.assertRegex(path.read_text(), r"allow_in_game_world_editor:\s*false")
        incoming = (ROOT / "server/src/com/openrsc/server/net/rsc/handlers/WorldEditorHandler.java").read_text()
        self.assertLess(incoming.index("ALLOW_IN_GAME_WORLD_EDITOR"), incoming.index("paintTerrain(request"))
        self.assertLess(incoming.index("validate(player"), incoming.index("paintTerrain(request"))
        self.assertNotRegex(incoming.lower(), r"\b(save|publish)\b")
        self.assertIn("Unsupported editor operation", incoming)

    def test_desktop_shell_and_session_guards_are_present(self):
        ui = (ROOT / "Client_Base/src/com/openrsc/interfaces/misc/WorldEditorInterface.java").read_text()
        self.assertIn("Config.isAndroid()", ui)
        self.assertIn('"Navigate","Inspect","Terrain","Scenery","NPC"', ui)
        self.assertIn("World Editor", ui)
        sessions = (ROOT / "server/src/com/openrsc/server/content/worldedit/WorldEditorSessionManager.java").read_text()
        self.assertIn("!player.isAdmin()", sessions)
        self.assertIn("sequence != active.nextSequence", sessions)
        world = (ROOT / "server/src/com/openrsc/server/model/world/World.java").read_text()
        self.assertIn("getWorldEditorSessions().closeFor(player)", world)

    def test_navigate_and_inspect_have_distinct_behavior(self):
        ui = (ROOT / "Client_Base/src/com/openrsc/interfaces/misc/WorldEditorInterface.java").read_text()
        self.assertIn("mode==Mode.INSPECT", ui)
        self.assertIn("mode==Mode.NAVIGATE&&clickTeleportPreferred", ui)
        self.assertIn("Last clicked tile", ui)
        self.assertIn("Brush: inactive at", ui)
        self.assertIn("Teleport to coordinates", ui)
        self.assertIn('"Surface"', ui)
        self.assertIn('"Structure"', ui)
        client = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text()
        self.assertIn("setWorldEditorNavigateClickTeleport", client)
        self.assertIn("worldEditorTeleport", client)
        self.assertIn("isClickTeleportActiveForCurrentContext", client)
        terrain_menu = re.search(
            r"if \(worldEditorInterface != null && worldEditorInterface\.isInspecting\(\)\) \{(?P<body>.*?)\n\s*\}",
            client,
            re.S,
        )
        self.assertIsNotNone(terrain_menu)
        self.assertIn("WORLD_EDITOR_COPY_TERRAIN", client)
        self.assertIn("WORLD_EDITOR_COPY_OBJECT", client)
        self.assertIn("WORLD_EDITOR_COPY_NPC", client)
        for label in (
            "Coordinates:", "Floor Color:", "Floor Texture:", "Walls: North",
            "First floor", "Second floor", "Underground", "Copy inspected",
        ):
            self.assertIn(label, ui)

    def test_scenery_and_npc_tools_delegate_to_established_commands(self):
        ui = (ROOT / "Client_Base/src/com/openrsc/interfaces/misc/WorldEditorInterface.java").read_text()
        client = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text()
        actions = (ROOT / "Client_Base/src/orsc/enumerations/MenuItemAction.java").read_text()
        handler = (ROOT / "server/src/com/openrsc/server/net/rsc/handlers/WorldEditorHandler.java").read_text()
        sessions = (ROOT / "server/src/com/openrsc/server/content/worldedit/WorldEditorSessionManager.java").read_text()
        commands = (ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java").read_text()

        self.assertIn("enum SceneryTool { PLACE, ROTATE, REMOVE }", ui)
        self.assertIn("enum NpcTool { PLACE, REMOVE }", ui)
        self.assertIn("Math.min(radius,64)", ui)
        self.assertIn("Boundaries remain inspection-only", ui)
        self.assertIn("isLayeredPlacementDraftLevel()", ui)
        self.assertIn("placeNativeNpc(", sessions)
        self.assertIn("removeNativeNpc(", sessions)
        self.assertIn("nativeNpcDraftSize()", sessions)
        self.assertIn("placeNativeNpc(player,id,radius,x,y)", commands)
        self.assertIn("removeNativeNpc(player,npc)", commands)
        self.assertIn('sendCommandString("saveworldedits")', ui)
        self.assertNotIn("WORLD_EDITOR_UNDO", actions)
        self.assertNotIn("WORLD_EDITOR_REDO", actions)

        expected_commands = (
            'sendCommandString("aobject "+',
            'sendCommandString("rotateobject "+',
            'sendCommandString("robject "+',
            'sendCommandString("cnpc "+',
            'sendCommandString("rpc "+',
        )
        for command in expected_commands:
            self.assertIn(command, client)
        for action in (
            "WORLD_EDITOR_PLACE_SCENERY(100)",
            "WORLD_EDITOR_ROTATE_SCENERY(100)",
            "WORLD_EDITOR_REMOVE_SCENERY(100)",
            "WORLD_EDITOR_PLACE_NPC(100)",
            "WORLD_EDITOR_REMOVE_NPC(100)",
        ):
            self.assertIn(action, actions)
        self.assertIn('" radius="+radius', handler)

    def test_terrain_t1_is_bounded_server_authoritative_and_durably_saveable(self):
        ui = (ROOT / "Client_Base/src/com/openrsc/interfaces/misc/WorldEditorInterface.java").read_text()
        client = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text()
        world = (ROOT / "Client_Base/src/orsc/graphics/three/World.java").read_text()
        parser = (ROOT / "server/src/com/openrsc/server/net/rsc/parsers/impl/PayloadCustomParser.java").read_text()
        sessions = (ROOT / "server/src/com/openrsc/server/content/worldedit/WorldEditorSessionManager.java").read_text()
        collision = (ROOT / "server/src/com/openrsc/server/model/world/region/TileValue.java").read_text()

        self.assertIn("editor.type == 5 ? 29", parser)
        self.assertIn("fieldMask<=0||(fieldMask&~127)!=0", sessions)
        self.assertIn("TERRAIN_DRAFT_LIMIT = 4096", sessions)
        self.assertIn("terrainDraft.remove(key)", sessions)
        commands = (ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java").read_text()
        save_files = (ROOT / "server/src/com/openrsc/server/io/WorldEditorTerrainSaveFiles.java").read_text()
        self.assertIn("paintElevation?1:0", ui)
        self.assertIn("paintFloorColor?2:0", ui)
        self.assertIn("paintFloorTexture?4:0", ui)
        self.assertIn("paintRoof?8:0", ui)
        self.assertIn("paintEastWall?16:0", ui)
        self.assertIn("paintNorthWall?32:0", ui)
        self.assertIn("paintDiagonalWall?64:0", ui)
        self.assertIn("terrainStructureTab", ui)
        self.assertIn("encodedDiagonalWall", ui)
        self.assertIn("int strokeSize=terrainBrushSize", ui)
        self.assertNotIn("mask&112", ui)
        self.assertIn("terrainBrushSize==1", ui)
        self.assertIn("centeredThreeByThree", ui)
        self.assertIn('button(x+220,y+194,155,"Save edits")', ui)
        self.assertIn('button(x+220,y+248,155,"Save edits")', ui)
        self.assertIn("requestWorldEditSave()", ui)
        self.assertIn('sendCommandString("saveworldedits")', ui)
        self.assertIn("Save commits server/client archives; undo remains disabled.", ui)
        self.assertIn("saveTerrainDraft(Player player)", sessions)
        self.assertIn("ownsActiveSession(player)", sessions)
        self.assertLess(sessions.index("terrainArchive=new WorldEditorTerrainArchive", sessions.index("saveTerrainDraft")),
                        sessions.index("terrainDraft.clear()", sessions.index("saveTerrainDraft")))
        self.assertIn("editor.saveTerrainDraft(player)", commands)
        self.assertIn("ATOMIC_MOVE", save_files)
        self.assertIn("verifyMaterialization", save_files)
        self.assertIn("restore(backup,serverArchive)", save_files)
        self.assertIn("Server and client landscape archives are not byte-identical before save.", save_files)
        self.assertIn("WORLD_EDITOR_PAINT_TERRAIN(100)", (ROOT / "Client_Base/src/orsc/enumerations/MenuItemAction.java").read_text())
        self.assertIn("applyWorldEditorTerrainPatch", client)
        self.assertIn("worldEditorTerrainRevision", world)
        self.assertIn("-editor-", world)
        self.assertIn("editorPaintedOverlay", world)
        self.assertIn("!source.editorPaintedOverlay", world)
        self.assertIn("terrainBlocked||blockingSceneryCount>0", collision)
        self.assertIn("addTerrainCollision", collision)
        self.assertIn("addDynamicCollision", collision)
        self.assertIn("terrainWallProjectileCount", collision)
        self.assertIn("dynamicProjectileCount", collision)
        actions = (ROOT / "Client_Base/src/orsc/enumerations/MenuItemAction.java").read_text()
        self.assertNotIn("WORLD_EDITOR_UNDO", actions)
        self.assertNotIn("WORLD_EDITOR_REDO", actions)

    def test_world_editor_dispatcher_accepts_only_supported_envelope_lengths(self):
        parser = (ROOT / "server/src/com/openrsc/server/net/rsc/parsers/impl/PayloadCustomParser.java").read_text()
        match = re.search(r"WORLD_EDITOR_PACKET_LENGTHS\s*=\s*\{([^}]+)\}", parser)
        self.assertIsNotNone(match)
        accepted = {int(value) for value in re.findall(r"\d+", match.group(1))}
        self.assertEqual({13, 15, 19, 22, 29}, accepted)
        self.assertTrue(accepted.isdisjoint({12, 14, 16, 18, 20, 21, 23, 28, 31}))
        self.assertIn("return isWorldEditorPacketLength(packet.getLength());", parser)

    def test_terrain_stroke_uses_one_bounded_authoritative_round_trip(self):
        parser = (ROOT / "server/src/com/openrsc/server/net/rsc/parsers/impl/PayloadCustomParser.java").read_text()
        handler = (ROOT / "server/src/com/openrsc/server/net/rsc/handlers/WorldEditorHandler.java").read_text()
        sessions = (ROOT / "server/src/com/openrsc/server/content/worldedit/WorldEditorSessionManager.java").read_text()
        generator = (ROOT / "server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java").read_text()
        client_handler = (ROOT / "Client_Base/src/orsc/PacketHandler.java").read_text()
        ui = (ROOT / "Client_Base/src/com/openrsc/interfaces/misc/WorldEditorInterface.java").read_text()

        self.assertIn("length>=30&&length<=282&&(length-26)%4==0", parser)
        self.assertIn("packet.getLength()!=26+count*4", parser)
        self.assertIn("paintTerrainStroke(request", handler)
        self.assertLess(sessions.index("for(int[] coordinate:coordinates)"), sessions.index("terrainDraft.put(key,after.get(i))"))
        self.assertIn("projectedDraftSize>TERRAIN_DRAFT_LIMIT", sessions)
        self.assertIn("editor.type == 8", generator)
        self.assertIn("count<1||count>64", client_handler)
        self.assertIn("acceptTerrainStroke", client_handler)
        self.assertIn("putByte(6)", ui)
        self.assertIn('ack "+ackMs+"ms, rebuild "', ui)
        self.assertIn("updateTerrainDrag", ui)
        self.assertIn("terrainDragSeen.add(key)", ui)
        self.assertIn("TERRAIN_BATCH_LIMIT=64", ui)
        client = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text()
        build_mode = re.search(r"public void setWorldEditorBuildMode\(boolean enabled\)\{(?P<body>.*?)\n\t\}", client, re.S)
        self.assertIsNotNone(build_mode)
        self.assertIn("worldEditorSavedLighting", build_mode.group("body"))
        self.assertIn("worldEditorSavedGeometry", build_mode.group("body"))
        self.assertIn("RendererGeometrySettings.Mode.FACETED", build_mode.group("body"))
        self.assertIn("RendererReliefSettings.MIN_LEVEL", build_mode.group("body"))
        self.assertNotIn("saveRenderer", build_mode.group("body"))
        self.assertIn("if(!worldEditorBuildMode)this.updateSceneryAnimations();", client)

if __name__ == "__main__":
    unittest.main()
