#!/usr/bin/env python3
"""Regression checks for custom bank hover tooltip text styling."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BANK_UI = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/CustomBankInterface.java"


def main() -> None:
	source = BANK_UI.read_text()

	required_snippets = [
		"private static final int HOVER_TOOLTIP_FONT = 0;",
		'drawString((bankItem.isPinned() ? "Pinned: " : "")',
		"x + 7, y + 15, HOVER_TOOLTIP_FONT,",
		"drawString(mc.equippedItems[selectedEquipmentSlot].getName(), x + 7, y + 15, HOVER_TOOLTIP_FONT, 0xFFFFFF);",
		"mc.getInventoryItemID(hoveredInventorySlot)",
		"mc.getInventory()[hoveredInventorySlot].getNoted()).getName()",
	]

	missing = [snippet for snippet in required_snippets if snippet not in source]
	if missing:
		raise AssertionError("Custom bank hover tooltip font is not consistently shared")


if __name__ == "__main__":
	main()
