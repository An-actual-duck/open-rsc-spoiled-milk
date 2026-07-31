#!/usr/bin/env python3
"""Compiled regression for stable resident-object chunk origins."""

import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GRID = ROOT / "Client_Base/src/orsc/ResidentObjectChunkGrid.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def compile_and_run_grid_regression() -> None:
    harness = """
package orsc;

public final class ResidentObjectChunkGridRegression {
    public static void main(String[] args) {
        int cellSize = 24;

        int firstCell = ResidentObjectChunkGrid.worldCellForLocalTile(
            0, 464, cellSize);
        int nextCell = ResidentObjectChunkGrid.worldCellForLocalTile(
            16, 464, cellSize);
        if (firstCell != 19 || nextCell != 20) {
            throw new AssertionError("unaligned region did not retain world cells");
        }
        int firstOrigin = ResidentObjectChunkGrid.localOriginTileForWorldCell(
            firstCell, 464, cellSize);
        int nextOrigin = ResidentObjectChunkGrid.localOriginTileForWorldCell(
            nextCell, 464, cellSize);
        if (firstOrigin != -8 || nextOrigin != 16
                || nextOrigin - firstOrigin != cellSize) {
            throw new AssertionError("adjacent world cells collapsed locally");
        }

        int alignedCell = ResidentObjectChunkGrid.worldCellForLocalTile(
            24, 480, cellSize);
        if (alignedCell != 21
                || ResidentObjectChunkGrid.localOriginTileForWorldCell(
                    alignedCell, 480, cellSize) != 24) {
            throw new AssertionError("aligned region mapping changed");
        }

        int negativeCell = ResidentObjectChunkGrid.worldCellForLocalTile(
            0, -5, cellSize);
        if (negativeCell != -1
                || ResidentObjectChunkGrid.localOriginTileForWorldCell(
                    negativeCell, -5, cellSize) != -19) {
            throw new AssertionError("negative world coordinates lost floor semantics");
        }

        boolean rejected = false;
        try {
            ResidentObjectChunkGrid.worldCellForLocalTile(0, 0, 0);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        if (!rejected) {
            throw new AssertionError("non-positive cell size was accepted");
        }
    }
}
"""
    with tempfile.TemporaryDirectory(prefix="resident-object-grid-") as directory:
        temp = Path(directory)
        harness_path = temp / "ResidentObjectChunkGridRegression.java"
        harness_path.write_text(harness, encoding="utf-8")
        subprocess.run(
            ["javac", "-Xlint:all", "-d", str(temp), str(GRID), str(harness_path)],
            check=True,
        )
        subprocess.run(
            ["java", "-cp", str(temp), "orsc.ResidentObjectChunkGridRegression"],
            check=True,
        )


def audit_client_boundary() -> None:
    source = CLIENT.read_text(encoding="utf-8")
    require(
        "ResidentObjectChunkGrid.worldCellForLocalTile(" in source,
        "resident chunks must derive identity from the stable world grid",
    )
    require(
        "ResidentObjectChunkGrid.localOriginTileForWorldCell(" in source,
        "resident chunks must derive presentation origins from the world cell",
    )
    require(
        "int cellX = Math.floorDiv(tileX, cellTileSize);" not in source
        and "int cellZ = Math.floorDiv(tileZ, cellTileSize);" not in source,
        "local tile bucketing can collapse distinct world cells",
    )


def main() -> None:
    compile_and_run_grid_regression()
    audit_client_boundary()
    print("PASS: resident object cells keep unique world-aligned renderer origins")


if __name__ == "__main__":
    main()
