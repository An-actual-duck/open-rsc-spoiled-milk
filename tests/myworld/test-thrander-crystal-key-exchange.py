#!/usr/bin/env python3
"""Regression checks for Thrander's crystal-key-half exchange."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
THRANDER = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "npcs"
    / "varrock"
    / "Thrander.java"
)


def require(source: str, snippet: str, description: str) -> None:
    if snippet not in source:
        raise AssertionError(f"Missing {description}: {snippet}")


def exchange(source_count: int) -> tuple[int, int, bool]:
    """Model the guarded 3:1 inventory mutation."""
    if source_count < 3:
        return source_count, 0, False
    return source_count - 3, 1, True


def main() -> None:
    source = THRANDER.read_text(encoding="utf-8")

    for dialogue in (
        '"Can you convert crystal key halves?"',
        '"Crystal? Well I do have a knack for converting things, but crystal is brittle, "',
        "\"I'll need 3 of a kind to cleanly swap them\"",
        "\"Here's 3 loops\"",
        "\"Here's three teeth\"",
        '"No thanks"',
        '"Perfect, one moment"',
        "\"Here's your tooth half\"",
        "\"Here's your loop half\"",
    ):
        require(source, dialogue, "assigned dialogue")

    for existing_shop_contract in (
        '"Do you want to trade?"',
        '"No thank you"',
        "player.setAccessingShop(shop);",
        "ActionSender.showShop(player, shop);",
    ):
        require(source, existing_shop_contract, "existing Thrander shop flow")

    require(source, "private static final int REQUIRED_KEY_HALVES = 3;", "3:1 exchange constant")
    require(
        source,
        "ItemId.LOOP_KEY_HALF.id(),\n\t\t\t\t\tItemId.TOOTH_KEY_HALF.id(),",
        "loop-to-tooth mapping",
    )
    require(
        source,
        "ItemId.TOOTH_KEY_HALF.id(),\n\t\t\t\t\tItemId.LOOP_KEY_HALF.id(),",
        "tooth-to-loop mapping",
    )
    require(source, "countId(sourceId) < REQUIRED_KEY_HALVES", "pre-mutation quantity check")
    require(source, "synchronized (player)", "serialized exchange")
    require(
        source,
        "remove(new Item(sourceId, REQUIRED_KEY_HALVES)) == -1",
        "all-or-nothing three-half removal",
    )
    require(source, "add(new Item(productId))", "opposite-half grant")
    require(
        source,
        "add(new Item(sourceId, REQUIRED_KEY_HALVES))",
        "source rollback on failed grant",
    )
    require(source, "delay(2);", "short conversion delay")

    for source_count, expected in (
        (0, (0, 0, False)),
        (1, (1, 0, False)),
        (2, (2, 0, False)),
        (3, (0, 1, True)),
        (4, (1, 1, True)),
        (6, (3, 1, True)),
    ):
        actual = exchange(source_count)
        if actual != expected:
            raise AssertionError(
                f"3:1 exchange mismatch for {source_count} halves: "
                f"expected {expected}, got {actual}"
            )

    print("PASS: Thrander crystal-key-half exchanges are guarded and symmetric")


if __name__ == "__main__":
    main()
