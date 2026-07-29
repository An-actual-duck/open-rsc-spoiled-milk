#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REGULAR = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/RegularPlayer.java"
COMMAND_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/CommandHandler.java"
BANK = ROOT / "server/src/com/openrsc/server/model/container/Bank.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
DOC = ROOT / "docs/community/player-commands-and-shortcuts.md"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def forbid(text: str, needle: str, description: str) -> None:
    if needle in text:
        fail(f"unexpected {description}: {needle}")


def main() -> None:
    regular = REGULAR.read_text(encoding="utf-8")
    command_handler = COMMAND_HANDLER.read_text(encoding="utf-8")
    bank = BANK.read_text(encoding="utf-8")
    player = PLAYER.read_text(encoding="utf-8")
    doc = DOC.read_text(encoding="utf-8")

    require(regular, 'command.equalsIgnoreCase("b") || command.equalsIgnoreCase("bank")', "bank command aliases")
    require(regular, "openBankFromCommand(player);", "bank command dispatch")
    require(regular, "player.getLocation().isInBank(", "bank area guard")
    require(regular, "player.getWorldLocation()", "layer-aware bank area guard")
    require(regular, "player.getBank().quickFeature(null, player, false);", "bank quick-open action")
    require(regular, "Quick banking is a QoL feature which you are opted out of.", "bank QoL opt-out message")
    forbid(regular, 'command.equalsIgnoreCase("b") && config().RIGHT_CLICK_BANK', "right-click-bank-gated bank command")

    require(regular, 'command.equalsIgnoreCase("wilderness") && !player.isMod()', "regular wilderness command")
    require(regular, "private void queryWildernessState(Player player)", "regular wilderness report")
    require(regular, '::b or ::bank - open bank while standing in a bank area', "bank command help")
    require(regular, "private boolean quickBankingIsAvailable()", "quick banking QoL availability helper")
    require(regular, "return config().RIGHT_CLICK_BANK || config().PLAYER_COMMANDS;", "quick banking QoL sources")
    require(bank, "player.getConfig().BATCH_PROGRESSION && player.hasActiveBatch()", "quick bank active-batch guard")
    forbid(bank, "player.getConfig().BATCH_PROGRESSION && player.getBatchProgressBar()", "quick bank progress-display preference guard")
    require(player, "public boolean hasActiveBatch()", "player active-batch helper")
    require(player, "return batch != null && !batch.isComplete();", "player active-batch semantics")

    require(command_handler, '"bank",', "bank command staff-log exclusion")
    require(command_handler, '"wilderness"', "wilderness command staff-log exclusion")

    require(doc, "`::b` or `::bank`", "community command doc bank aliases")

    print("PASS: player convenience commands are reachable and documented")


if __name__ == "__main__":
    main()
