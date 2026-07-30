#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PACKET_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
INSTANCE_STORE = ROOT / "Client_Base/src/orsc/ClientSceneInstanceStore.java"
RSMODEL = ROOT / "Client_Base/src/orsc/graphics/three/RSModel.java"
SCENE_OBJECT_DEBUG = ROOT / "Client_Base/src/orsc/SceneObjectDebugSettings.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> None:
    packet_handler = PACKET_HANDLER.read_text(encoding="utf-8")
    client = CLIENT.read_text(encoding="utf-8")
    instance_store = INSTANCE_STORE.read_text(encoding="utf-8")
    rsmodel = RSMODEL.read_text(encoding="utf-8")
    scene_object_debug = SCENE_OBJECT_DEBUG.read_text(encoding="utf-8")

    require("public boolean hasLoadedTerrainForGameObject(int xTile, int zTile, int objectID, int dir)" in client,
            "Client should expose a terrain-availability check for scenery")
    require("private boolean hasLoadedTerrainForWallObject(int x, int y, int dir)" in client,
            "Client should check wall endpoints before drawing boundary models")
    require("private boolean hasLoadedTerrainForWorldPoint(int xWorld, int zWorld)" in client
            and "this.world.isPresentationTerrainFaceTile(xTile, zTile);" in client,
            "Terrain checks should match the elevation interpolation window")
    require("private boolean[] gameObjectMaterialized" in instance_store
            and "private boolean[] wallObjectMaterialized" in instance_store,
            "Deferred scenery should track explicit scene materialization state")
    require("public void materializeGameObjectInstance(int index)" in client
            and "if (!hasLoadedTerrainForGameObject(xTile, zTile, objectID, dir)) {\n\t\t\tdebugSceneGameObjectEvent(\"defer-terrain\", index, \"\");\n\t\t\treturn;\n\t\t}" in client,
            "Scenery materialization should be gated on loaded terrain")
    require("int farXTile = xTile + Math.max(0, xSize);" in client
            and "int farZTile = zTile + Math.max(0, zSize);" in client
            and "hasLoadedTerrainForWorldPoint(xTile * this.tileSize, zTile * this.tileSize)\n\t\t\t&& hasLoadedTerrainForWorldPoint(farXTile * this.tileSize, farZTile * this.tileSize)" in client,
            "Scenery terrain checks should require the full footprint, not just the center point")
    require("public void materializeWallObjectInstance(int index)" in client
            and "if (!hasLoadedTerrainForWallObject(x, z, dir)) {\n\t\t\tdebugSceneWallObjectEvent(\"defer-terrain\", index, \"\");\n\t\t\treturn;\n\t\t}" in client,
            "Boundary materialization should be gated before wall geometry is built")
    require("private void materializeLoadedTerrainScenery()" in client
            and "this.materializeLoadedTerrainScenery();" in client,
            "Region loads should retry deferred scenery once terrain is active")
    require("private void rematerializeLoadedTerrainSceneryAfterWorldReload()" in client
            and "this.rematerializeLoadedTerrainSceneryAfterWorldReload();" in client,
            "Roof-only world reloads should reattach scenery to the rebuilt terrain scene")
    require("this.setWallObjectInstanceModel(i, null);" in client
            and "this.getWorld().registerObjectDir(\n\t\t\t\t\tthis.getWallObjectInstanceX(i)," in client,
            "Roof-only world reloads should rebuild wall object models and object directions")
    require("for (int i = 0; i < this.getGameObjectInstanceCount(); i++) {\n\t\t\tthis.dematerializeGameObjectInstance(i);" in client
            and "this.dematerializeWallObjectInstance(i);\n\t\t\tthis.setWallObjectInstanceModel(i, null);" in client
            and "this.clearResidentObjectChunkCache();\n\t\tthis.materializeLoadedTerrainScenery();" in client,
            "Roof-only world reloads should remove old scene models before rebuilding scenery")
    require("this.dematerializeGameObjectInstance(i);\n\t\t\t\t\t}\n\t\t\t\t\tfor (int i = 0; this.getWallObjectInstanceCount() > i; ++i) {\n\t\t\t\t\t\tthis.dematerializeWallObjectInstance(i);\n\t\t\t\t\t}\n\t\t\t\t\tif (hardAreaLoad || heightOffsetChanged) {\n\t\t\t\t\t\tthis.clearResidentObjectChunkCache();\n\t\t\t\t\t}\n\t\t\t\t\tlong dematerializeNanos" in client,
            "Region shifts should detach existing scenery before loading the next terrain window")
    require("if (hardAreaLoad || heightOffsetChanged) {\n\t\t\t\t\t\tthis.clearResidentObjectChunkCache();\n\t\t\t\t\t}\n\t\t\t\t\tthis.materializeLoadedTerrainScenery();" in client,
            "Hard or plane-changing region shifts should clear resident object chunks before rebuilding scenery")
    require("debugSceneGameObjectHover(var9, var8, var7);" in client
            and "debugResidentObjectChunkBuild(\"resident-chunk-build\", input, objectChunk);" in client,
            "Scene object diagnostics should trace hover and resident chunk rebuilds")
    require("spoiledmilk.sceneObjectDebug" in scene_object_debug
            and "SPOILED_MILK_SCENE_OBJECT_DEBUG" in scene_object_debug
            and "spoiledmilk.sceneObjectDebugFilter" in scene_object_debug,
            "Scene object diagnostics should be hidden behind runtime flags")
    require("private static final String WATCHTOWER_MODEL_NAME = \"watchtower\";" in client
            and "model.hideFacesUsingVerticesOutsideLocalBounds(" in client,
            "The legacy watchtower model should discard outlier geometry during model load")
    require("public int hideFacesUsingVerticesOutsideLocalBounds(int maxAbsX, int maxAbsY, int maxAbsZ)" in rsmodel
            and "this.faceTextureFront[face] = this.m_Vb;" in rsmodel
            and "this.vertX[vertex] = 0;" in rsmodel,
            "Model outlier cleanup should hide bad faces and collapse orphan outlier vertices")

    store_record = "mc.setGameObjectInstanceModel(instanceIndex, m);"
    materialize_record = "mc.materializeGameObjectInstance(instanceIndex);"
    capacity_guard = "if (!mc.hasGameObjectInstanceCapacity()) {\n\t\t\t\t\t\tcontinue;\n\t\t\t\t\t}"
    capacity_guard_index = packet_handler.index(capacity_guard)
    store_record_index = packet_handler.index(store_record, capacity_guard_index)
    materialize_record_index = packet_handler.index(materialize_record, store_record_index)
    require("static final int WALL_OBJECT_KEY_BASE = 20000;" in instance_store
            and "static final int GAME_OBJECT_INITIAL_CAPACITY = WALL_OBJECT_KEY_BASE;" in instance_store,
            "Scenery capacity should grow past the old 5000 limit without colliding with wall pick keys")
    require("static final int WALL_OBJECT_INITIAL_CAPACITY = 5000;" in instance_store,
            "Boundary capacity should grow past the old 500 limit")
    require("new boolean[gameObjectCapacity]" in instance_store
            and "new RSModel[gameObjectCapacity]" in instance_store,
            "Scenery instance arrays should use the expanded named capacity")
    require("new boolean[wallObjectCapacity]" in instance_store
            and "new RSModel[wallObjectCapacity]" in instance_store,
            "Boundary instance arrays should use the expanded named capacity")
    require("public boolean hasGameObjectInstanceCapacity()" in client
            and "public boolean hasWallObjectInstanceCapacity()" in client,
            "Packet handlers should be able to guard instance appends")
    require(capacity_guard in packet_handler,
            "Scenery append packets should be ignored instead of overflowing local arrays")
    require("if (!mc.hasWallObjectInstanceCapacity()) {\n\t\t\t\t\t\tcontinue;\n\t\t\t\t\t}" in packet_handler,
            "Boundary append packets should be ignored instead of overflowing local arrays")
    require("var8.getRenderer3DModelKind() == Renderer3DModelKind.WALL_OBJECT" in client
            and "var8.key - ClientSceneInstanceStore.WALL_OBJECT_KEY_BASE" in client
            and "model.key = index + WALL_OBJECT_KEY_BASE;" in instance_store,
            "Boundary pick-key encoding should move with the expanded scenery capacity")
    require(capacity_guard_index < store_record_index,
            "Scenery capacity should be checked before storing a new instance")
    require(store_record_index < materialize_record_index,
            "Scenery should be stored before terrain-ready materialization is retried")
    require("mc.setGameObjectInstanceMaterialized(instanceIndex, false);" in packet_handler,
            "New scenery packets should start as deferred materialization candidates")
    require("mc.setWallObjectInstanceModel(instanceIndex, null);" in packet_handler
            and "mc.materializeWallObjectInstance(instanceIndex);" in packet_handler,
            "Boundary packets should defer model construction until terrain endpoints are loaded")
    require("mc.dematerializeGameObjectInstance(" in packet_handler
            and "mc.dematerializeWallObjectInstance(" in packet_handler,
            "Packet removals should only remove scene/collision for materialized instances")
    require("this.world.addGameObject_UpdateCollisionMap(xTile, zTile, objectID, false);" in client,
            "Scenery collision should be applied only by terrain-ready materialization")
    require("this.world.applyWallToCollisionFlags(id, x, z, dir);" in client,
            "Boundary collision should be applied only by terrain-ready materialization")

    print("PASS: client defers scenery drawing until terrain is loaded")


if __name__ == "__main__":
    main()
