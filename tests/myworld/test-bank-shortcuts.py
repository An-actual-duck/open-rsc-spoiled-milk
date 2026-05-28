#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BANK = ROOT / "Client_Base" / "src" / "com" / "openrsc" / "interfaces" / "misc" / "CustomBankInterface.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def main() -> None:
    text = BANK.read_text(encoding="utf-8")

    withdraw_branch = "if (mc.getMouseClick() == 1 && mc.controlPressed && !equipmentMode)"
    deposit_branch = "else if (mc.getMouseClick() == 1 && mc.controlPressed"
    organize_branch = "else if (organizeMode > 0 && !rightClickMenu"

    require(text, withdraw_branch, "Ctrl-click bank withdraw branch")
    require(text, "sendWithdraw(Integer.MAX_VALUE);", "full bank stack withdrawal action")
    require(text, deposit_branch, "Ctrl-click inventory deposit branch")
    require(text, "sendDeposit(Integer.MAX_VALUE);", "full inventory stack deposit action")
    if "NPE while searching bank" in text:
        fail("bank search should skip malformed items without direct console output")

    withdraw_start = text.index(withdraw_branch)
    withdraw_end = text.index("sendWithdraw(Integer.MAX_VALUE);", withdraw_start)
    first_organize = text.index(organize_branch, withdraw_start)
    if not withdraw_start < withdraw_end < first_organize:
        fail("Ctrl-click withdrawal must take priority over bank organize dragging")

    deposit_start = text.index(deposit_branch)
    deposit_end = text.index("sendDeposit(Integer.MAX_VALUE);", deposit_start)
    inventory_organize = text.index(organize_branch, deposit_start)
    if not deposit_start < deposit_end < inventory_organize:
        fail("Ctrl-click deposit must take priority over inventory organize dragging")

    print("PASS: custom bank Ctrl-click shortcuts transfer full item quantities")


if __name__ == "__main__":
    main()
