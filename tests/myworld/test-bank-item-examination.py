#!/usr/bin/env python3
"""Guard the bank's local Examine action and placeholder exclusion."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = (ROOT / "Client_Base/src/com/openrsc/interfaces/misc/CustomBankInterface.java").read_text(encoding="utf-8")

assert "private void drawBankExamineAction(BankItem bankItem, int row, int menuWidth)" in SOURCE
assert 'drawString("Examine"' in SOURCE
assert "bankItem.getItem().getItemDef().getDescription()" in SOURCE
assert "MessageType.GAME" in SOURCE
assert "if (!selectedBankItem.isPlaceholder()) {\n\t\t\t\t\t\t\tdrawBankExamineAction(selectedBankItem, 1, menuWidth);" in SOURCE
assert "drawBankExamineAction(selectedBankItem, pinRow + 1, menuWidth);" in SOURCE
assert "if (selectedBankItem != null && !selectedBankItem.isPlaceholder()) {\n\t\t\t\toffset++;" in SOURCE
print("PASS: occupied bank items expose local Examine while placeholders remain silent")
