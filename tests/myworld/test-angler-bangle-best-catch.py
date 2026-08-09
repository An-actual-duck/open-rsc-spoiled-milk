#!/usr/bin/env python3
"""Verify the Angler's Bangle directly selects the best fishing outcome."""
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]
SELECTOR = ROOT / "server/src/com/openrsc/server/content/FishingBestCatchSelector.java"

FIXTURE = """
package com.openrsc.server.content;

public final class FishingBestCatchSelectorFixture {
    private static void expect(boolean actual, boolean expected, String label) {
        if (actual != expected) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void expect(int actual, int expected, String label) {
        if (actual != expected) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        expect(FishingBestCatchSelector.shouldSelectBestCatch(10, 10), true,
            "tier one includes its upper boundary");
        expect(FishingBestCatchSelector.shouldSelectBestCatch(10, 11), false,
            "tier one leaves a failed roll to normal fishing");
        expect(FishingBestCatchSelector.shouldSelectBestCatch(60, 60), true,
            "diamond tier direct chance uses sixty percent");
        expect(FishingBestCatchSelector.shouldSelectBestCatch(60, 61), false,
            "diamond tier direct chance is bounded");
        expect(FishingBestCatchSelector.shouldSelectBestCatch(100, 100), true,
            "dragonstone guarantees the best catch");
        expect(FishingBestCatchSelector.countHighestTierEntries(new int[] {1, 3, 3, 2}), 2,
            "all tied highest-tier fish are eligible for direct selection");
        expect(FishingBestCatchSelector.selectHighestTierIndex(new int[] {1, 3, 3, 2}, 0), 1,
            "first tied highest-tier fish can be selected");
        expect(FishingBestCatchSelector.selectHighestTierIndex(new int[] {1, 3, 3, 2}, 1), 2,
            "second tied highest-tier fish can be selected");
    }
}
"""


def main() -> None:
    with tempfile.TemporaryDirectory() as directory:
        temp = Path(directory)
        fixture = temp / "FishingBestCatchSelectorFixture.java"
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(["javac", "-d", str(temp), str(SELECTOR), str(fixture)], check=True, cwd=ROOT)
        subprocess.run(["java", "-cp", str(temp), "com.openrsc.server.content.FishingBestCatchSelectorFixture"],
                       check=True, cwd=ROOT)
    print("PASS: Angler's Bangle directly selects the best-tier fish")


if __name__ == "__main__":
    main()
