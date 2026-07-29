#!/usr/bin/env python3
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ROWBOAT_LOCS = ROOT / "server/conf/server/defs/locs/SceneryLocsOther.json"
ROWBOAT_PLUGIN = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/misc/RandomObjects.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
PACKET_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"
SCENE_BASELINE_STATE = ROOT / "Client_Base/src/orsc/SceneBaselineState.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(condition: bool, description: str) -> None:
    if not condition:
        fail(description)


def extract(text: str, start: str, end: str) -> str:
    start_index = text.find(start)
    require(start_index >= 0, f"missing start marker: {start}")
    end_index = text.find(end, start_index)
    require(end_index >= 0, f"missing end marker: {end}")
    return text[start_index:end_index]


class SceneClientModel:
    """Minimal model of baseline delivery across the rowboat/death sequence."""

    def __init__(self) -> None:
        self.loaded_origin = "lumbridge"
        self.stored_origin = "lumbridge"
        self.applied_key = "lumbridge"
        self.objects = {1242}

    def receive_complete_baseline(self, origin: str, objects: set[int]) -> None:
        self.stored_origin = origin
        if self.loaded_origin == origin:
            self.applied_key = origin
            self.objects = set(objects)

    def defer_death_region_load(self, respawn_origin: str) -> None:
        self.receive_complete_baseline(respawn_origin, {1242})

    def finish_region_load(self, origin: str) -> None:
        self.loaded_origin = origin
        self.objects.clear()
        if self.stored_origin == origin:
            # Region loads must force a replay even when this baseline key was
            # applied on an earlier visit to the same place.
            self.applied_key = origin
            self.objects = {1242}


class LegacySceneClientModel:
    """Model legacy scenery delivery while a death screen defers a hard load."""

    def __init__(self) -> None:
        self.area_load_pending = False
        self.objects = {"old-tree": False}
        self.walls = {"old-wall": False}

    def begin_area_load(self) -> None:
        self.area_load_pending = True
        self.objects = {name: False for name in self.objects}
        self.walls = {name: False for name in self.walls}

    def receive_legacy_destination_scene(self) -> None:
        self.objects["lumbridge-rowboat"] = self.area_load_pending
        self.walls["lumbridge-bank-wall"] = self.area_load_pending

    def finish_hard_area_load(self) -> None:
        self.objects = {
            name: False for name, pending in self.objects.items() if pending
        }
        self.walls = {
            name: False for name, pending in self.walls.items() if pending
        }
        self.area_load_pending = False


def verify_repeated_use_and_death_model() -> None:
    client = SceneClientModel()
    for _ in range(2):
        require(1242 in client.objects, "rowboat missing before repeated travel")
        client.loaded_origin = "edgeville"
        client.objects.clear()
        client.defer_death_region_load("lumbridge")
        require(not client.objects, "respawn baseline applied against the stale Edgeville origin")
        client.finish_region_load("lumbridge")
        require(1242 in client.objects, "rowboat baseline was not replayed after respawn region load")


def verify_legacy_deferred_load_model() -> None:
    client = LegacySceneClientModel()
    client.begin_area_load()
    client.receive_legacy_destination_scene()
    client.finish_hard_area_load()
    require(
        set(client.objects) == {"lumbridge-rowboat"},
        "deferred hard load discarded destination legacy scenery",
    )
    require(
        set(client.walls) == {"lumbridge-bank-wall"},
        "deferred hard load discarded destination legacy walls",
    )


def main() -> None:
    locations = json.loads(ROWBOAT_LOCS.read_text(encoding="utf-8"))["sceneries"]
    rowboats = [
        loc for loc in locations
        if loc.get("id") == 1242
        and loc.get("pos") == {"X": 119, "Y": 642}
    ]
    require(len(rowboats) == 1, "Lumbridge rowboat must remain one persistent scenery spawn")

    plugin = ROWBOAT_PLUGIN.read_text(encoding="utf-8")
    rowboat_case = extract(plugin, "case 1242:", "break;")
    require("player.teleport(206,449);" in rowboat_case, "rowboat travel destination changed")
    for mutation in ("delloc", "changeloc", "unregisterGameObject", "delayedSpawnObject"):
        require(mutation not in rowboat_case, f"rowboat travel must not mutate persistent scenery via {mutation}")

    player = PLAYER.read_text(encoding="utf-8")
    death = extract(player, "public void killedBy(final Mob mob)", "private int getEquippedWeaponID()")
    require(
        "teleportToConfiguredRespawn(false);" in death,
        "death must continue through the normal respawn teleport lifecycle",
    )
    require(
        "public void teleportToConfiguredRespawn(final boolean bubble)" in player,
        "respawn must retain the configured legacy destination across layered authority",
    )

    packet_handler = PACKET_HANDLER.read_text(encoding="utf-8")
    scene_baseline_state = SCENE_BASELINE_STATE.read_text(encoding="utf-8")
    require(
        "public void reapplyCompleteSceneBaselineAfterRegionLoad()" in packet_handler
        and "applyCompleteSceneBaselineToLegacyLists(true);" in packet_handler,
        "completed scene baselines must be force-replayed after a region load",
    )
    require(
        "|| !sceneBaselineState.isBaselineOriginLoaded(mc)" in packet_handler
        and re.search(
            r"boolean isBaselineOriginLoaded\(mudclient mc\).*?World\.isLocalTile\(",
            scene_baseline_state,
            re.DOTALL,
        ),
        "scene baselines must not apply against a stale region origin during the death screen",
    )

    client = CLIENT.read_text(encoding="utf-8")
    region_load = extract(client, "public final boolean loadNextRegion", "private void loadSounds()")
    require(
        "if (this.deathScreenTimeout != 0)" in region_load
        and "this.packetHandler.reapplyCompleteSceneBaselineAfterRegionLoad();" in region_load,
        "deferred death region loads must replay their stored scenery baseline when loading completes",
    )
    require(
        region_load.index("this.world.playerAlive = true;")
        < region_load.index("this.packetHandler.reapplyCompleteSceneBaselineAfterRegionLoad();")
        < region_load.index("return true;"),
        "baseline replay must happen after the new region is live and before region load returns",
    )
    require(
        "this.retainPendingAreaLoadStaticScene();" in region_load,
        "hard area loads must retain legacy destination scenery received during a deferred load",
    )
    require(
        "public void beginAreaLoad()" in client
        and "public boolean isAreaLoadPending()" in client,
        "the client must identify packets received for a pending area load",
    )

    require(
        "mc.beginAreaLoad();" in packet_handler,
        "world-info packets must begin legacy static-scene transition tracking",
    )
    for snippet in (
        "mc.setGameObjectInstancePendingAreaLoad(instanceIndex, mc.isAreaLoadPending());",
        "mc.setWallObjectInstancePendingAreaLoad(instanceIndex, mc.isAreaLoadPending());",
        "mc.setGameObjectInstancePendingAreaLoad(count, mc.isGameObjectInstancePendingAreaLoad(i));",
        "mc.setWallObjectInstancePendingAreaLoad(localIndex, mc.isWallObjectInstancePendingAreaLoad(var9));",
    ):
        require(snippet in packet_handler, f"legacy scene transition metadata missing: {snippet}")

    verify_repeated_use_and_death_model()
    verify_legacy_deferred_load_model()
    print("PASS: Lumbridge static scene survives baseline and legacy deferred death/respawn loads")


if __name__ == "__main__":
    main()
