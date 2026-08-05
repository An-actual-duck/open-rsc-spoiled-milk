#!/usr/bin/env python3
"""Verify the bounded C12 Devotion-source and Black Unicorn set rebalance."""

from pathlib import Path
import subprocess
import tempfile
import textwrap


ROOT = Path(__file__).resolve().parents[2]
CONTENT = ROOT / "server/src/com/openrsc/server/content"
DEVOTION = CONTENT / "Devotion.java"
GAIN = CONTENT / "DevotionOfferingGain.java"
BALANCE = CONTENT / "DevotionHalfOfferingBalance.java"
UNICORN_EFFECT = CONTENT / "BlackUnicornOfferingEffect.java"
UNICORN_HEALING = CONTENT / "BlackUnicornOfferingHealing.java"
SUMMONING = CONTENT / "Summoning.java"
ITEM_ID = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
EQUIPMENT = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
BONES = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/misc/Bones.java"
GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
CLIENT_ITEMS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
CLIENT_OVERRIDES = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/MyWorldItemOverrides.java"


FIXTURE = r"""
package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;

public final class ClericC12DevotionFixture {
    private static void expect(int actual, int expected, String label) {
        if (actual != expected) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        expect(DevotionOfferingGain.getHalfOfferingUnits(false, false, false), 2, "base 1x");
        expect(DevotionOfferingGain.getHalfOfferingUnits(true, false, false), 4, "symbol 2x");
        expect(DevotionOfferingGain.getHalfOfferingUnits(false, true, false), 3, "summon 1.5x");
        expect(DevotionOfferingGain.getHalfOfferingUnits(false, false, true), 3, "set 1.5x");
        expect(DevotionOfferingGain.getHalfOfferingUnits(true, true, false), 5, "symbol plus summon 2.5x");
        expect(DevotionOfferingGain.getHalfOfferingUnits(true, false, true), 5, "symbol plus set 2.5x");
        expect(DevotionOfferingGain.getHalfOfferingUnits(false, true, true), 4, "summon plus set 2x");
        expect(DevotionOfferingGain.getHalfOfferingUnits(true, true, true), 6, "all sources 3x");

        expect(BlackUnicornOfferingEffect.getHealing(ItemId.BONES.id()), 1, "bones healing");
        expect(BlackUnicornOfferingEffect.getHealing(ItemId.BIG_BONES.id()), 2, "big bones healing");
        expect(BlackUnicornOfferingEffect.getHealing(ItemId.BAT_BONES.id()), 2, "bat bones healing");
        expect(BlackUnicornOfferingEffect.getHealing(ItemId.DEMON_ASH.id()), 3, "demon ash healing");
        expect(BlackUnicornOfferingEffect.getHealing(ItemId.DRAGON_BONES.id()), 4, "dragon bones healing");
        expect(BlackUnicornOfferingEffect.getHealing(ItemId.ASHES.id()), 0, "ordinary ash excluded");
        expect(BlackUnicornOfferingEffect.getRequestedHealing(ItemId.BIG_BONES.id(), 3), 6,
            "stacked big bones multiply before healing");
        expect(BlackUnicornOfferingEffect.getRequestedHealing(ItemId.DRAGON_BONES.id(), Integer.MAX_VALUE),
            Integer.MAX_VALUE, "stacked healing arithmetic is bounded");
        expect(BlackUnicornOfferingEffect.calculateHealing(
            ItemId.BONES.id(), 3, true, 90, 99), 3, "manual-style healing");
        expect(BlackUnicornOfferingEffect.calculateHealing(
            ItemId.BIG_BONES.id(), 3, true, 90, 99), 6, "automatic stacked healing");
        expect(BlackUnicornOfferingEffect.calculateHealing(
            ItemId.DRAGON_BONES.id(), 3, true, 90, 99), 9, "healing ceiling cap");
        expect(BlackUnicornOfferingEffect.calculateHealing(
            ItemId.BAT_BONES.id(), 5, true, 99, 109), 10, "boosted maximum Hits ceiling");
        expect(BlackUnicornOfferingEffect.calculateHealing(
            ItemId.BONES.id(), 1, true, 99, 99), 0, "full-health no-op");
        expect(BlackUnicornOfferingEffect.calculateHealing(
            ItemId.BONES.id(), 1, false, 90, 99), 0, "incomplete set no-op");
        expect(BlackUnicornOfferingEffect.calculateHealing(
            ItemId.ASHES.id(), 20, true, 1, 99), 0, "ordinary ashes remain excluded");
    }
}
"""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def validate_runtime_wiring() -> None:
    devotion = DEVOTION.read_text(encoding="utf-8")
    equipment = EQUIPMENT.read_text(encoding="utf-8")
    bones = BONES.read_text(encoding="utf-8")
    summoning = SUMMONING.read_text(encoding="utf-8")
    healing = UNICORN_HEALING.read_text(encoding="utf-8")
    guide = GUIDE.read_text(encoding="utf-8")
    client_items = CLIENT_ITEMS.read_text(encoding="utf-8")
    client_overrides = CLIENT_OVERRIDES.read_text(encoding="utf-8")

    require(
        "DevotionOfferingGain.getHalfOfferingUnits(" in devotion
        and "hasBlessedSymbolEquipped(player, godLine)" in devotion
        and "hasFullBlackUnicornSetEquipped(player)" in devotion,
        "ordinary and Black Unicorn offerings must share the exact additive gain model",
    )
    require(
        "_symbol_bonus_toggle" not in devotion
        and "_black_unicorn_bonus_toggle" not in devotion,
        "exact half-offering gains must not retain every-other-offering toggles",
    )
    require(
        "total += getBlackUnicornHidePrayerBonus();" not in equipment
        and "getBlackUnicornHidePrayerBonus()" not in equipment,
        "the full Black Unicorn set must no longer add Prayer points",
    )
    require(
        "BlackUnicornOfferingHealing.apply(player, item.getCatalogId(), 1);" in bones,
        "manual offerings must call the shared full-set heal",
    )
    require(
        bones.index("player.getCarriedItems().remove(toRemove) != -1")
        < bones.index("BlackUnicornOfferingHealing.apply(player, item.getCatalogId(), 1);"),
        "the set heal must follow successful inventory removal",
    )
    require(
        bones.count("BlackUnicornOfferingHealing.apply(") == 1
        and "BlackUnicornOfferingHealing" not in bones.split(
            "public void onUseInv", 1
        )[1],
        "Bonecrusher must remain excluded from the shared set heal",
    )
    require(
        "BlackUnicornOfferingHealing.apply(owner, itemId, amount);" in summoning
        and summoning.index("BlackUnicornOfferingHealing.apply(owner, itemId, amount);")
        < summoning.index("Your black unicorn sanctifies the"),
        "Mourning Unicorn must apply stacked set healing during sanctification",
    )
    require(
        "BlackUnicornOfferingEffect.calculateHealing(" in healing
        and "hasFullBlackUnicornHideSet()" in healing
        and "player.getHealingMaximumHits()" in healing
        and "HitSplat.TYPE_HEAL" in healing,
        "manual and automatic offerings must share one capped healing mutation",
    )
    require(
        "Matching blessed symbols give 2x devotion from offerings" in guide
        and "Mourning Unicorn and its full hide set each add 50%" in guide
        and "double XP, +50% Devotion, and full-set healing from auto-offerings" in guide
        and "Mourning Unicorn auto-offerings also activate the set heal" in guide,
        "skill guides must describe all three Devotion bonuses",
    )
    require(
        "Full set: +50% Devotion; bones/bat/big/demon/dragon offerings heal 1/2/2/3/4 Hits, including Mourning Unicorn offerings." in client_overrides
        and "normal/big/demon/dragon offerings" not in client_items + client_overrides
        and "Full black unicorn-hide set: +10 Prayer." not in client_items,
        "Black Unicorn armor descriptions must advertise the replacement set bonus",
    )


def run_compiled_fixture() -> None:
    with tempfile.TemporaryDirectory(prefix="cleric-c12-devotion-") as temporary:
        temp = Path(temporary)
        fixture = temp / "com/openrsc/server/content/ClericC12DevotionFixture.java"
        fixture.parent.mkdir(parents=True)
        fixture.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        classes = temp / "classes"
        classes.mkdir()
        subprocess.run(
            [
                "javac",
                "-d",
                str(classes),
                str(ITEM_ID),
                str(BALANCE),
                str(GAIN),
                str(UNICORN_EFFECT),
                str(fixture),
            ],
            cwd=ROOT,
            check=True,
        )
        subprocess.run(
            ["java", "-cp", str(classes), "com.openrsc.server.content.ClericC12DevotionFixture"],
            cwd=ROOT,
            check=True,
        )


def main() -> None:
    validate_runtime_wiring()
    run_compiled_fixture()
    print("PASS: C12 Devotion multipliers and Black Unicorn set replacement validated")


if __name__ == "__main__":
    main()
