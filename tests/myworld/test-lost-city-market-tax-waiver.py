#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FAIRY_QUEEN = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/npcs/lostcity/FairyQueen.java"
MARKET_ACCESS = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/npcs/lostcity/LostCityMarketAccess.java"
DOOR_ACTION = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/defaults/DoorAction.java"


def fail(message: str) -> None:
    raise AssertionError(message)


def require(text: str, needle: str, message: str) -> None:
    if needle not in text:
        fail(message)


def main() -> None:
    fairy = FAIRY_QUEEN.read_text()
    access = MARKET_ACCESS.read_text()
    door = DOOR_ACTION.read_text()

    require(
        access,
        'DIAMOND_TAX_WAIVED_CACHE_KEY = "lost_city_market_diamond_tax_waived"',
        "market tax waiver should use a named persistent cache key",
    )
    require(
        fairy,
        "LostCityMarketAccess.hasDiamondTaxWaiver(player)",
        "Fairy Queen should hide the waiver dialogue after the unlock is purchased",
    )
    require(
        fairy,
        '"The diamond trading tax is annoying"',
        "Fairy Queen should expose the new market tax complaint option",
    )
    require(
        fairy,
        "MARKET_TAX_WAIVER_DRAGONSTONES = 5",
        "Fairy Queen waiver should cost five dragonstones",
    )
    require(
        fairy,
        "countId(ItemId.DRAGONSTONE.id(), Optional.of(false))",
        "Fairy Queen should require five cut dragonstones in inventory",
    )
    require(
        fairy,
        "if (dragonstones < MARKET_TAX_WAIVER_DRAGONSTONES)",
        "Fairy Queen should compare inventory dragonstones against the waiver cost",
    )
    require(
        fairy,
        "new Item(ItemId.DRAGONSTONE.id(), MARKET_TAX_WAIVER_DRAGONSTONES)",
        "Fairy Queen should remove the five dragonstones when granting the waiver",
    )
    require(
        fairy,
        "LostCityMarketAccess.waiveDiamondTax(player)",
        "Fairy Queen should persist the waiver after payment",
    )
    if 'say(player, n, "The diamond trading tax is annoying"' in fairy:
        fail("Fairy Queen should not manually repeat the selected tax complaint option")
    if 'say(player, n, "That\'s a deal"' in fairy:
        fail("Fairy Queen should not manually repeat the selected deal option")
    if 'say(player, n, "That\'s even more absurd!"' in fairy:
        fail("Fairy Queen should not manually repeat the selected refusal option")
    require(
        door,
        "if (LostCityMarketAccess.hasDiamondTaxWaiver(player))",
        "Lost City market doors should check the permanent waiver before charging diamonds",
    )
    require(
        door,
        "handleLostCityMarketTax(",
        "Lost City market door toll dialogue should remain shared by both entrances",
    )
    require(
        door,
        "new Item(ItemId.DIAMOND.id())",
        "Lost City market should still charge a diamond for players without the waiver",
    )


if __name__ == "__main__":
    main()
