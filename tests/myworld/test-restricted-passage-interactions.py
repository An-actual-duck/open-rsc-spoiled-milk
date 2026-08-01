#!/usr/bin/env python3
import json
import os
import re
import subprocess
import tempfile
import textwrap
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
CORE_JAR = SERVER / "core.jar"
FUNCTIONS = SERVER / "src/com/openrsc/server/plugins/Functions.java"
DOOR_ACTION = (
    SERVER
    / "plugins/com/openrsc/server/plugins/authentic/defaults/DoorAction.java"
)
RANGERS_GUILD_DOOR = (
    SERVER
    / "plugins/com/openrsc/server/plugins/custom/misc/RangersGuildDoor.java"
)
SCENERY_LOCS = SERVER / "conf/server/defs/locs/SceneryLocs.json"
GAME_OBJECT_DEFS = SERVER / "conf/server/defs/GameObjectDef.xml"


def method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1 : index]
    raise AssertionError(f"Unterminated method: {signature}")


def compact(source: str) -> str:
    return re.sub(r"\s+", "", source)


class RestrictedPassageInteractionTest(unittest.TestCase):
    def test_northwest_falador_uses_the_shared_members_gate_path(self):
        sceneries = json.loads(
            SCENERY_LOCS.read_text(encoding="utf-8")
        )["sceneries"]
        self.assertIn(
            {
                "id": 137,
                "pos": {"X": 341, "Y": 487},
                "direction": 4,
            },
            sceneries,
        )

        definitions = ET.parse(GAME_OBJECT_DEFS).getroot().findall(
            "GameObjectDef"
        )
        falador_gate = definitions[137]
        self.assertEqual("gate", falador_gate.findtext("name").lower())
        self.assertEqual("open", falador_gate.findtext("command1").lower())
        self.assertEqual("2", falador_gate.findtext("type"))
        self.assertEqual("1", falador_gate.findtext("width"))
        self.assertEqual("2", falador_gate.findtext("height"))

        source = DOOR_ACTION.read_text(encoding="utf-8")
        gate_handler = method_body(
            source, "private void handleGates(GameObject obj, Player player)"
        )
        falador_case = gate_handler[
            gate_handler.index("case GATE_MEMBERS_TAVERLY_AND_RETRO_ASGARNIA:") :
            gate_handler.index("case GATE_MEMBERS_WILDERNESS_ICE_GIANT:")
        ]
        self.assertIn("members = true;", falador_case)
        self.assertIn("break;", falador_case)
        self.assertNotIn("obj.getX()", falador_case)
        self.assertNotIn("obj.getY()", falador_case)
        self.assertIn('player.message("you go through the gate");', gate_handler)
        self.assertIn("doGate(player, obj);", gate_handler)

    def test_transient_passages_preserve_layered_placement_identity(self):
        functions = FUNCTIONS.read_text(encoding="utf-8")
        do_gate = method_body(
            functions,
            (
                "public static void doGate(final Player player, "
                "final GameObject object, int replaceID, Point destination)"
            ),
        )
        do_door = method_body(
            functions,
            (
                "public static void doDoor(final GameObject object, "
                "final Player player, int replaceID)"
            ),
        )

        self.assertIn("GameObject openGate = new GameObject(", do_gate)
        self.assertIn("changeloc(object, openGate);", do_gate)
        self.assertNotIn("delloc(object);", do_gate)
        self.assertIn(
            "addloc(new GameObject(object.getWorld(), object.getLoc()));",
            do_gate,
        )
        self.assertIn("changeloc(object, newObject);", do_door)
        self.assertIn(
            "addloc(object.getWorld(), object.getLoc(), 3000);", do_door
        )

        door_action = DOOR_ACTION.read_text(encoding="utf-8")
        self.assertIn("replaceGameObject(object,", door_action)
        rangers = RANGERS_GUILD_DOOR.read_text(encoding="utf-8")
        self.assertIn("doDoor(obj, player, OPEN_DOUBLE_DOORS);", rangers)

    def test_cardinal_gate_traversal_accepts_both_sides(self):
        source = FUNCTIONS.read_text(encoding="utf-8")
        do_gate = compact(
            method_body(
                source,
                (
                    "public static void doGate(final Player player, "
                    "final GameObject object, int replaceID, Point destination)"
                ),
            )
        )
        cases = (
            (
                "dir==0",
                "player.getX()>=object.getX()",
                "Point.location(object.getX()-1,object.getY())",
                "Point.location(object.getX(),object.getY())",
            ),
            (
                "dir==2",
                "player.getY()<=object.getY()",
                "Point.location(object.getX(),object.getY()+1)",
                "Point.location(object.getX(),object.getY())",
            ),
            (
                "dir==4",
                "player.getX()>object.getX()",
                "Point.location(object.getX(),object.getY())",
                "Point.location(object.getX()+1,object.getY())",
            ),
            (
                "dir==6",
                "player.getY()>=object.getY()",
                "Point.location(object.getX(),object.getY()-1)",
                "Point.location(object.getX(),object.getY())",
            ),
        )
        for direction, side_test, first_target, second_target in cases:
            start = do_gate.index(direction)
            end = do_gate.find("elseif(dir==", start + len(direction))
            block = do_gate[start : end if end >= 0 else len(do_gate)]
            self.assertIn(side_test, block)
            self.assertIn(first_target, block)
            self.assertIn(second_target, block)

    def test_atomic_layered_gate_collision_transition(self):
        harness = textwrap.dedent(
            """
            import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner;
            import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.ConstructorState;
            import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.Definition;
            import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.Operation;
            import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.WorldBounds;
            import com.openrsc.server.model.world.NativeLayeredGameObjectRegistry;
            import com.openrsc.server.model.world.coordinate.WorldCoordinate;
            import com.openrsc.server.model.world.coordinate.WorldLocation;
            import com.openrsc.server.model.world.coordinate.WorldSpaceId;
            import com.openrsc.server.model.world.region.TileValue;
            import com.openrsc.server.util.rsc.CollisionFlag;

            public final class RestrictedPassageFixture {
                private static void require(boolean value, String message) {
                    if (!value) throw new AssertionError(message);
                }

                private static TileValue tile() {
                    TileValue value = new TileValue();
                    value.initializeTerrainCollision();
                    return value;
                }

                public static void main(String[] arguments) {
                    NativeLayeredGameObjectRegistry<Object> gates =
                        new NativeLayeredGameObjectRegistry<Object>();
                    long generation = gates.getGeneration();
                    WorldLocation location = new WorldLocation(
                        WorldSpaceId.GLOBAL,
                        new WorldCoordinate(341, 487, 0));
                    WorldBounds bounds = WorldBounds.of(1000, 4000);
                    GameTickEventRestorationCollisionFootprintPlanner.Result closed =
                        GameTickEventRestorationCollisionFootprintPlanner.plan(
                            Operation.REGISTER,
                            ConstructorState.of(137, 341, 487, 4, 0),
                            Definition.scenery(
                                2, 1, 2, "gate", new String[0]),
                            false, bounds);
                    GameTickEventRestorationCollisionFootprintPlanner.Result open =
                        GameTickEventRestorationCollisionFootprintPlanner.plan(
                            Operation.REGISTER,
                            ConstructorState.of(181, 341, 487, 4, 0),
                            Definition.scenery(
                                3, 1, 2, "gate", new String[0]),
                            false, bounds);
                    Object closedGate = new Object();
                    require(gates.register(
                            generation, "falador-northwest-gate", location,
                            0, 4, closedGate, closed,
                            java.util.Collections.<WorldLocation>emptyList())
                            == closedGate,
                        "closed gate registration");
                    TileValue westClosed = tile();
                    TileValue eastClosed = tile();
                    gates.applyCollision(location, westClosed);
                    gates.applyCollision(
                        new WorldLocation(
                            WorldSpaceId.GLOBAL,
                            new WorldCoordinate(342, 487, 0)),
                        eastClosed);
                    require((westClosed.traversalMask
                            & CollisionFlag.WALL_WEST) != 0,
                        "closed gate blocks its west side");
                    require((eastClosed.traversalMask
                            & CollisionFlag.WALL_EAST) != 0,
                        "closed gate blocks its east side");

                    Object openGate = new Object();
                    require(gates.replace(
                            generation, "falador-northwest-gate", closedGate,
                            location, 0, 4, openGate, open,
                            java.util.Collections.<WorldLocation>emptyList())
                            == openGate,
                        "atomic open transition");
                    require(gates.find("falador-northwest-gate") == openGate,
                        "open gate retains the placement identity");
                    require(gates.size() == 1,
                        "open transition does not duplicate the gate");
                    TileValue westOpen = tile();
                    TileValue eastOpen = tile();
                    gates.applyCollision(location, westOpen);
                    gates.applyCollision(
                        new WorldLocation(
                            WorldSpaceId.GLOBAL,
                            new WorldCoordinate(342, 487, 0)),
                        eastOpen);
                    require((westOpen.traversalMask
                            & CollisionFlag.WALL_WEST) == 0,
                        "open gate clears west-side collision");
                    require((eastOpen.traversalMask
                            & CollisionFlag.WALL_EAST) == 0,
                        "open gate clears east-side collision");

                    Object restoredGate = new Object();
                    require(gates.replace(
                            generation, "falador-northwest-gate", openGate,
                            location, 0, 4, restoredGate, closed,
                            java.util.Collections.<WorldLocation>emptyList())
                            == restoredGate,
                        "atomic closed restoration");
                    require(gates.find("falador-northwest-gate")
                            == restoredGate && gates.size() == 1,
                        "restoration remains duplicate-free");
                    TileValue surfaceRestored = tile();
                    gates.applyCollision(location, surfaceRestored);
                    require((surfaceRestored.traversalMask
                            & CollisionFlag.WALL_WEST) != 0,
                        "restoration reinstates collision");
                    TileValue otherLevel = tile();
                    gates.applyCollision(
                        new WorldLocation(
                            WorldSpaceId.GLOBAL,
                            new WorldCoordinate(341, 487, -1)),
                        otherLevel);
                    require((otherLevel.traversalMask
                            & CollisionFlag.WALL_WEST) == 0,
                        "gate collision remains level-qualified");
                }
            }
            """
        )
        with tempfile.TemporaryDirectory(
            prefix="restricted-passage-fixture-"
        ) as temp:
            temp_path = Path(temp)
            source = temp_path / "RestrictedPassageFixture.java"
            source.write_text(harness, encoding="utf-8")
            subprocess.run(
                [
                    "javac",
                    "-source",
                    "8",
                    "-target",
                    "8",
                    "-cp",
                    str(CORE_JAR),
                    "-d",
                    str(temp_path),
                    str(source),
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run(
                [
                    "java",
                    "-cp",
                    os.pathsep.join((str(temp_path), str(CORE_JAR))),
                    "RestrictedPassageFixture",
                ],
                cwd=ROOT,
                check=True,
            )


if __name__ == "__main__":
    unittest.main()
