#!/usr/bin/env python3
"""Exercise the fixed-point offering XP table and Devotion multiplier inputs."""
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]
ITEM_ID = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
OFFERING_XP = ROOT / "server/src/com/openrsc/server/content/OfferingExperience.java"

FIXTURE = """
package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;

public final class OfferingExperienceFixture {
    private static void expect(int actual, int expected, String label) {
        if (actual != expected) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        expect(OfferingExperience.getDisplayedExperience(ItemId.BONES.id()), 10, "bones XP");
        expect(OfferingExperience.getDisplayedExperience(ItemId.ASHES.id()), 10, "ashes XP");
        expect(OfferingExperience.getDisplayedExperience(ItemId.BIG_BONES.id()), 12, "big bones XP");
        expect(OfferingExperience.getDisplayedExperience(ItemId.BAT_BONES.id()), 13, "bat bones XP");
        expect(OfferingExperience.getDisplayedExperience(ItemId.DEMON_ASH.id()), 14, "demon ash XP");
        expect(OfferingExperience.getDisplayedExperience(ItemId.DRAGON_BONES.id()), 16, "dragon bones XP");
        expect(OfferingExperience.scaleDisplayedExperience(10, ItemId.DRAGON_BONES.id()), 16,
            "dragon Devotion bonus multiplier");
        expect(OfferingExperience.scaleDisplayedExperience(1000, ItemId.DRAGON_BONES.id()), 1600,
            "high Devotion bonus multiplier");
        expect(OfferingExperience.getInternalExperience(ItemId.DRAGON_BONES.id()), 64,
            "dragon bones quarter-XP conversion");
        expect(OfferingExperience.getMultiplierPercent(ItemId.COINS.id()), 0, "non-offering excluded");
    }
}
"""


def main() -> None:
    with tempfile.TemporaryDirectory() as directory:
        temp = Path(directory)
        fixture = temp / "OfferingExperienceFixture.java"
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            ["javac", "-d", str(temp), str(ITEM_ID), str(OFFERING_XP), str(fixture)],
            check=True,
            cwd=ROOT,
        )
        subprocess.run(
            ["java", "-cp", str(temp), "com.openrsc.server.content.OfferingExperienceFixture"],
            check=True,
            cwd=ROOT,
        )
    print("PASS: offering base XP and Devotion multipliers are exact")


if __name__ == "__main__":
    main()
