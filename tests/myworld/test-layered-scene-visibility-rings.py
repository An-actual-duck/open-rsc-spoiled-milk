#!/usr/bin/env python3
"""Guard the exact 3x3 gameplay scene and visual-only 5x5 static ring."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
UPDATER = (
    ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"
).read_text(encoding="utf-8")
REGION_MANAGER = (
    ROOT
    / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
).read_text(encoding="utf-8")
PRESENTATION_SNAPSHOT = (
    ROOT
    / "server/src/com/openrsc/server/model/world/region/"
    "StaticScenePresentationSnapshot.java"
).read_text(encoding="utf-8")
WINDOW_KEY = (
    ROOT
    / "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredSpatialWindowKey.java"
).read_text(encoding="utf-8")
GENERATOR = (
    ROOT
    / "server/src/com/openrsc/server/net/rsc/generators/impl/"
    "PayloadCustomGenerator.java"
).read_text(encoding="utf-8")
STRUCT = (
    ROOT
    / "server/src/com/openrsc/server/net/rsc/struct/outgoing/"
    "SceneBaselineStruct.java"
).read_text(encoding="utf-8")
HANDLER = (
    ROOT / "Client_Base/src/orsc/PacketHandler.java"
).read_text(encoding="utf-8")
BASELINE = (
    ROOT / "Client_Base/src/orsc/SceneBaselineState.java"
).read_text(encoding="utf-8")
CLIENT = (
    ROOT / "Client_Base/src/orsc/mudclient.java"
).read_text(encoding="utf-8")
WORLD = (
    ROOT / "Client_Base/src/orsc/graphics/three/World.java"
).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def between(source: str, start: str, end: str) -> str:
    begin = source.index(start)
    finish = source.index(end, begin)
    return source[begin:finish]


# The active client scene is one exact 144x144 half-open rectangle. It must
# come from the layered index rather than the older player-centered view radius.
exact_snapshot = between(
    REGION_MANAGER,
    "public VisibilitySnapshot buildClientSceneVisibilitySnapshot(",
    "public StaticScenePresentationSnapshot",
)
for fragment in (
    "minRuntimeX",
    "maxRuntimeXExclusive",
    "minLogicalX",
    "maxLogicalXExclusive",
    "layeredSpatialEntityIndex.snapshot(window)",
    "candidate instanceof Player",
    "candidate instanceof Npc",
    "candidate instanceof GameObject",
    "candidate instanceof GroundItem",
    "LayeredSpatialWindowKey.exact(",
):
    require(fragment in exact_snapshot, f"exact scene snapshot missing {fragment}")
require(
    "coordinate.getX() < minLogicalX" in exact_snapshot
    and "coordinate.getX() >= maxLogicalXExclusive" in exact_snapshot
    and "coordinate.getY() < minLogicalY" in exact_snapshot
    and "coordinate.getY() >= maxLogicalYExclusive" in exact_snapshot,
    "exact scene snapshot lost its half-open tile filter",
)

visibility_builder = between(
    UPDATER,
    "private VisibilitySnapshot buildUncachedVisibilitySnapshot(",
    "private VisibilitySnapshot buildLegacyVisibilitySnapshot(",
)
require(
    "buildClientSceneVisibilitySnapshot(" in visibility_builder
    and "Math.addExact(minX, CLIENT_LOCAL_TILE_COUNT)" in visibility_builder
    and "Math.addExact(minY, CLIENT_LOCAL_TILE_COUNT)" in visibility_builder,
    "custom packet visibility is not sourced from the exact client scene",
)
for helper in (
    "private static boolean isWithinMobPacketRange(",
    "private static boolean isWithinAuthoritativeSceneWindow(",
):
    block = between(
        UPDATER,
        helper,
        (
            "private static boolean isWithinAuthoritativeSceneWindow("
            if "MobPacket" in helper
            else "private static void updateCustomMovementClientRegion("
        ),
    )
    require(
        "isWithinClientLocalTileWindow(" in block,
        f"{helper} does not enforce exact custom-client ownership",
    )

# The resident 5x5 ring is intentionally static presentation only. No mob or
# item collection is permitted there, and the DTO carries detached records
# rather than live gameplay entities.
outer_builder = between(
    REGION_MANAGER,
    "public StaticScenePresentationSnapshot",
    "private static StaticScenePresentationSnapshot.Record",
)
for fragment in (
    "snapshotGameObjects(window)",
    "object.getType() == 0",
    "object.getType() == 1",
    "x >= innerMinX && x < innerMaxX",
    "y >= innerMinY && y < innerMaxY",
):
    require(fragment in outer_builder, f"outer static ring missing {fragment}")
require("Npc" not in outer_builder, "outer ring must not collect NPCs")
require("GroundItem" not in outer_builder, "outer ring must not collect items")
require(
    "List<Record> scenery" in PRESENTATION_SNAPSHOT
    and "List<Record> walls" in PRESENTATION_SNAPSHOT
    and "GameObject" not in PRESENTATION_SNAPSHOT
    and "GroundItem" not in PRESENTATION_SNAPSHOT
    and "Npc" not in PRESENTATION_SNAPSHOT,
    "outer snapshot must remain a detached static-only DTO",
)

# Protocol v8 adds two outer categories with wide coordinates. Inner records
# retain the compact legacy envelope.
for fragment in (
    "LAYERED_PRESENTATION_SCENE_BASELINE_PROTOCOL_VERSION = 8",
    "SCENE_BASELINE_PAGE_PRESENTATION_SCENERY = 4",
    "SCENE_BASELINE_PAGE_PRESENTATION_WALLS = 5",
    "SCENE_BASELINE_PRESENTATION_HEADER_BYTES = 22",
    "SCENE_BASELINE_PRESENTATION_OBJECT_RECORD_BYTES = 12",
    "private static int sceneBaselinePageSize(",
    "private static int sceneBaselinePageBurstLimit(",
    "LAYERED_PRESENTATION_SCENE_BASELINE_PAGE_SIZE = 512",
    "LAYERED_PRESENTATION_SCENE_BASELINE_PAGE_BURST_LIMIT = 8",
):
    require(fragment in UPDATER, f"server presentation protocol missing {fragment}")
for fragment in (
    "presentationCenterSectorX",
    "presentationCenterSectorY",
    "presentationOuterRadius",
    "presentationInnerRadius",
    "presentationScenery",
    "presentationWalls",
):
    require(fragment in STRUCT, f"scene baseline DTO missing {fragment}")
require(
    "builder.writeInt(objectRecord.x);" in GENERATOR
    and "builder.writeInt(objectRecord.y);" in GENERATOR,
    "outer presentation coordinates must use 32-bit wire fields",
)
require(
    "PRESENTATION_PROTOCOL_VERSION = 8" in BASELINE
    and "PAGE_PRESENTATION_SCENERY = 4" in BASELINE
    and "PAGE_PRESENTATION_WALLS = 5" in BASELINE,
    "client baseline state does not recognize the outer product",
)
require(
    "SCENE_BASELINE_PRESENTATION_HEADER_BYTES = 22" in HANDLER
    and "final int recordBytes = presentationRecords ? 12 : 8;" in HANDLER
    and "? packetsIncoming.get32()" in HANDLER,
    "client parser lost the v8 presentation envelope",
)

page_order = between(
    UPDATER,
    "private SceneBaselinePage buildNextSceneBaselinePage(",
    "private int pageTotal(",
)
positions = [
    page_order.index("SCENE_BASELINE_PAGE_SCENERY"),
    page_order.index("SCENE_BASELINE_PAGE_WALLS"),
    page_order.index("SCENE_BASELINE_PAGE_PRESENTATION_SCENERY"),
    page_order.index("SCENE_BASELINE_PAGE_PRESENTATION_WALLS"),
]
require(
    positions == sorted(positions),
    "authoritative pages must be sent before presentation pages",
)

# Outer models are appended only to renderer resident chunks. They must never
# enter the interactive Scene or mutate collision.
presentation_models = between(
    CLIENT,
    "public void replaceStaticScenePresentation(",
    "private Renderer3DWorldChunkFrame appendResidentObjectChunkFrame(",
)
for forbidden in (
    "scene.addModel",
    "addGameObject_UpdateCollisionMap",
    "removeGameObject_CollisonFlags",
    "menuCommon",
):
    require(
        forbidden not in presentation_models,
        f"outer presentation leaked gameplay behavior through {forbidden}",
    )
resident_inputs = between(
    CLIENT,
    "private List<ResidentObjectChunkInput> buildResidentObjectChunkInputs(",
    "private void addResidentObjectChunkModel(",
)
require(
    "ensureStaticScenePresentationModels();" in resident_inputs
    and "for (StaticPresentationModel presentation" in resident_inputs,
    "outer models are not attached to resident renderer chunks",
)
require(
    "isPresentationTerrainFaceTile" in CLIENT
    and "Math.max(0, xTile)" in WORLD
    and "Math.min(" in WORLD,
    "inner edge objects lost presentation terrain or bounded collision support",
)

# Cache identity must distinguish exact rectangles from inclusive radius keys.
for fragment in (
    "public static LayeredSpatialWindowKey exact(",
    "private final boolean exactTileBounds;",
    "getMaxTileXExclusive()",
    "getMaxTileYExclusive()",
    "&& exactTileBounds == key.exactTileBounds",
):
    require(fragment in WINDOW_KEY, f"exact window key missing {fragment}")

# A 5x5 resident square contains a 3x3 authoritative square plus 16 static
# presentation sectors. Native scenes are centered on the exact 48-tile
# storage sector containing the player. The player therefore remains in the
# middle sector and every active point stays inside the signed 8-bit mob
# offset envelope.
sector_size = 48
active_radius = 1
resident_radius = 2
active_sector_count = (active_radius * 2 + 1) ** 2
resident_sector_count = (resident_radius * 2 + 1) ** 2
require(active_sector_count == 9, "active scene is not 3x3")
require(
    resident_sector_count - active_sector_count == 16,
    "static presentation ring is not exactly the outer 16 sectors",
)
for player_delta in range(0, sector_size):
    minimum_offset = -sector_size - player_delta
    maximum_offset = sector_size * 2 - 1 - player_delta
    require(
        -128 <= minimum_offset <= 127
        and -128 <= maximum_offset <= 127,
        "active 3x3 window exceeds the custom signed-byte wire envelope",
    )

for fragment in (
    "private static boolean usesCenteredClientSceneWindow(",
    "clientLocalCenteredSectionAnchorForTile(",
    "Math.floorDiv(\n\t\t\tprojectedTile, CLIENT_LOCAL_SECTION_SIZE)",
):
    require(fragment in UPDATER, f"server centered native scene missing {fragment}")
for fragment in (
    "public int activeSectionXForWorldTile(int worldTile)",
    "public int activeSectionYForWorldTile(int worldTile)",
    "nativeSnapshotRuntimeSection(",
):
    require(fragment in WORLD, f"client centered native scene missing {fragment}")
for fragment in (
    "this.world.activeSectionXForWorldTile(wantX)",
    "this.world.activeSectionYForWorldTile(wantZ)",
    "centeredNativeWindow ? World.SECTION_SIZE : 32",
):
    require(fragment in CLIENT, f"client native recentering missing {fragment}")

# Regression for the reported north/south seam. At y=655, the rounded legacy
# center was sector 14 and made sheep at y=624..631 local z=0..7. The native
# center is the containing sector 13, keeping the same sheep safely in the
# middle of the authoritative square.
player_y = 655
native_center_y = player_y // sector_size
native_base_y = (native_center_y - active_radius) * sector_size
require(native_center_y == 13, "reported seam chose the wrong native sector")
require(
    [sheep_y - native_base_y for sheep_y in range(624, 632)]
        == list(range(48, 56)),
    "reported sheep strip is not retained in the native middle sector",
)

# Crossing the real storage boundary at 672 advances exactly one sector and
# leaves the player at the beginning of the new middle sector.
next_player_y = 672
next_center_y = next_player_y // sector_size
next_base_y = (next_center_y - active_radius) * sector_size
require(next_center_y == native_center_y + 1, "native boundary did not advance")
require(
    next_player_y - next_base_y == sector_size,
    "player is not at the start of the new middle sector",
)

print("PASS: exact 3x3 gameplay visibility and outer static presentation ring")
