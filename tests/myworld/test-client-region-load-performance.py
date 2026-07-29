#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORLD = ROOT / "Client_Base/src/orsc/graphics/three/World.java"
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
SECTOR = ROOT / "Client_Base/src/com/openrsc/client/model/Sector.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> None:
    world = WORLD.read_text(encoding="utf-8")
    mudclient = MUDCLIENT.read_text(encoding="utf-8")
    sector = SECTOR.read_text(encoding="utf-8")

    require(
        "return Math.floorDiv(SECTION_SIZE / 2 + worldTile, SECTION_SIZE);"
        in world,
        "Signed layered coordinates must use floor division for section lookup",
    )
    signed_section_cases = {
        -529: -11,
        -528: -11,
        -505: -11,
        -504: -10,
        -491: -10,
        -481: -10,
        -480: -10,
        -457: -10,
        -456: -9,
        0: 0,
        23: 0,
        24: 1,
        2342: 49,
    }
    for world_tile, expected_section in signed_section_cases.items():
        actual_section = (24 + world_tile) // 48
        require(
            actual_section == expected_section,
            f"signed section boundary {world_tile}: "
            f"{actual_section} != {expected_section}",
        )
    mining_guild_section = (24 - 491) // 48
    mining_guild_origin = (mining_guild_section - 1) * 48
    require(
        -491 - mining_guild_origin == 37,
        "Mining Guild target must be inside the active 96-tile window",
    )

    require("private void resetModels()" in world,
            "World region reset method should still exist")
    require("System.gc()" not in world,
            "Client region loads must not force a stop-the-world GC pause")
    require("sectorTemplateCache" in world and "world-sector-preload" in world,
            "Client region loads should keep a background sector template cache")
    require("public void preloadSections(int worldX, int worldZ, int plane)" in world,
            "World should expose predictive sector preloading")
    require("sectors[sector] = loadSectorTemplate(height, sectionX, sectionY).copy();" in world,
            "Active 2x2 sectors should be cloned from cached templates")
    require("public Sector copy()" in sector,
            "Sector templates should support cloning before active-world mutation")
    require("private Sector(boolean loaded)" in sector and "new Sector(true)" in sector,
            "Sector templates should distinguish decoded terrain from missing blank fallback sectors")
    require("public boolean isLoaded()" in sector,
            "Sector loaded state should be queryable by world rendering")
    require("public boolean isTerrainLoadedAtLocalTile(int tileX, int tileZ)" in world,
            "World should expose loaded-terrain checks for sprite culling")
    require("return sector != null && sector.isLoaded();" in world,
            "World loaded-terrain checks should use decoded sector state")
    require("return new Sector();" in world,
            "Missing landscape archive entries should remain unloaded blank sectors")
    require("private boolean canDrawWorldSpriteAtLocalPixel(int pixelX, int pixelZ)" in mudclient,
            "Client should centralize terrain-loaded sprite gating")
    require("if (!canDrawWorldSpriteAtLocalPixel(var4, var5))" in mudclient,
            "Player and NPC sprites should not draw over unloaded terrain")
    require("if (!canDrawWorldSpriteAtLocalTile(this.groundItemX[centerX], this.groundItemZ[centerX]))" in mudclient,
            "Ground item sprites should not draw over unloaded terrain")
    require("this.world.preloadSections(wantX, wantZ, this.requestedPlane);" in mudclient,
            "Normal movement updates should warm sectors before boundary crossings")

    print("PASS: client region loading avoids forced GC stalls and warms sector cache")


if __name__ == "__main__":
    main()
