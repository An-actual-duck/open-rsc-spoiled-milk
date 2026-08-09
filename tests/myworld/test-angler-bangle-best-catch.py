#!/usr/bin/env python3
"""Verify the Angler's Bangle boosts only highest-tier fishing outcomes."""
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]
WEIGHTS = ROOT / "server/src/com/openrsc/server/content/FishingBestCatchWeights.java"

FIXTURE = """
package com.openrsc.server.content;

import java.util.Arrays;

public final class FishingBestCatchWeightsFixture {
    private static void expect(int[] actual, int[] expected, String label) {
        if (!Arrays.equals(actual, expected)) {
            throw new AssertionError(label + ": expected " + Arrays.toString(expected)
                + ", got " + Arrays.toString(actual));
        }
    }

    public static void main(String[] args) {
        expect(FishingBestCatchWeights.applyBonus(new int[] {100, 100}, new int[] {1, 2}, 10),
            new int[] {100, 110}, "tier-one best-catch boost");
        expect(FishingBestCatchWeights.applyBonus(new int[] {100, 100}, new int[] {1, 2}, 100),
            new int[] {100, 200}, "dragonstone doubles best-catch weight");
        expect(FishingBestCatchWeights.applyBonus(new int[] {10, 20, 20}, new int[] {1, 2, 2}, 100),
            new int[] {10, 40, 40}, "tied best tier is boosted evenly");
        expect(FishingBestCatchWeights.applyBonus(new int[] {10, 20}, new int[] {1, 2}, 0),
            new int[] {10, 20}, "no bangle leaves weights unchanged");
    }
}
"""


def main() -> None:
    with tempfile.TemporaryDirectory() as directory:
        temp = Path(directory)
        fixture = temp / "FishingBestCatchWeightsFixture.java"
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(["javac", "-d", str(temp), str(WEIGHTS), str(fixture)], check=True, cwd=ROOT)
        subprocess.run(["java", "-cp", str(temp), "com.openrsc.server.content.FishingBestCatchWeightsFixture"],
                       check=True, cwd=ROOT)
    print("PASS: Angler's Bangle boosts only best-tier fish selection weights")


if __name__ == "__main__":
    main()
