#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLIENT = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text()
WORLD = (ROOT / "Client_Base/src/orsc/graphics/three/World.java").read_text()


def require(fragment, message):
    if fragment not in CLIENT:
        raise AssertionError(message)


require("private void addWorldEditorTileActions(int localX,int localZ)",
        "shared editor tile-action builder is missing")
require("private boolean addProjectedEditorTileFallback()",
        "editor void projection fallback is missing")
require("worldEditorInterface!=null&&worldEditorInterface.isEditorOpen()",
        "void projection is not gated by an open editor")
require("scene.projectScreenToGroundTile(mouseX,mouseY,tileSize,getClickTeleportGroundPlaneY())",
        "void projection does not reuse the established ground projection")
require("tile[0]>=World.LOCAL_FACE_TILE_COUNT||tile[1]>=World.LOCAL_FACE_TILE_COUNT",
        "projected void coordinates are not bounded to loaded face tiles")
require("mouseY>=getGameHeight()-70||mouseInTabArea_CUSTOM()",
        "void projection can leak under client UI")
require("else addProjectedEditorTileFallback();",
        "missing scene faces do not invoke the editor fallback")

if CLIENT.count("addWorldEditorTileActions(") != 3:
    raise AssertionError("visible and projected terrain do not share one editor action builder")

for action in (
    "WORLD_EDITOR_INSPECT_TERRAIN",
    "WORLD_EDITOR_COPY_TERRAIN",
    "WORLD_EDITOR_PAINT_TERRAIN",
    "WORLD_EDITOR_PLACE_SCENERY",
    "WORLD_EDITOR_PLACE_NPC",
):
    require(action, f"projected terrain lost {action}")

if "return tileDecorationID(tileX, tileZ) == 26;" not in WORLD:
    raise AssertionError("normal transparent-terrain pick geometry was changed")
if "tileDecorationID(tileX, tileZ) == 10" in WORLD:
    raise AssertionError("overlay 10 became globally pickable outside the editor")
require_upper = "if (shouldLoadUpperPlaneModels(plane))"
if WORLD.count(require_upper) != 2:
    raise AssertionError("active loading and preloading do not share the upper-plane gate")
if "nativeLayeredTerrainSnapshot.getLevel() == 0" not in WORLD:
    raise AssertionError("generated signed levels can still compose unrelated upper-plane models")

print("PASS: editor void picking and generated-level render isolation are guarded")
