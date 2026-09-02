#!/usr/bin/env python3
import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
VIEWPORT = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/BankInventoryViewport.java"
BANK = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/CustomBankInterface.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"


def main() -> None:
    bank_source = BANK.read_text(encoding="utf-8")
    client_source = CLIENT.read_text(encoding="utf-8")
    assert "mc.getSurface().setClip(inventoryViewportX" in bank_source
    assert "mc.getSurface().clearClip();" in bank_source
    assert "drawInventoryScrollbar(inventoryViewportX, inventoryViewportY);" in bank_source
    assert "drawDraggingInventoryItem();" in bank_source
    assert "if (!bank.scrollInventory(x) && this.bankPage == 0)" in client_source

    harness = textwrap.dedent(
        """
        package com.openrsc.interfaces.misc;

        public final class BankInventoryViewportHarness {
            private static void require(boolean condition, String message) {
                if (!condition) throw new AssertionError(message);
            }

            public static void main(String[] args) {
                require(BankInventoryViewport.rowCount(30) == 3, "base rows");
                require(BankInventoryViewport.maxScrollRow(30) == 0, "base no overflow");
                require(BankInventoryViewport.rowCount(40) == 4, "extended rows");
                require(BankInventoryViewport.maxScrollRow(40) == 1, "extended overflow");
                require(BankInventoryViewport.scrollBy(0, 1, 40) == 1, "wheel down");
                require(BankInventoryViewport.scrollBy(1, 99, 40) == 1, "lower clamp");
                require(BankInventoryViewport.scrollBy(1, -1, 40) == 0, "wheel up");
                require(BankInventoryViewport.scrollBy(0, -99, 40) == 0, "upper clamp");

                int left = 100;
                int top = 200;
                require(BankInventoryViewport.slotAt(left, top, left, top, 0, 40) == 0,
                    "first visible slot");
                require(BankInventoryViewport.slotAt(left + 489, top + 101,
                    left, top, 0, 40) == 29, "last base-view slot");
                require(BankInventoryViewport.slotAt(left, top, left, top, 1, 40) == 10,
                    "scrolled first slot");
                require(BankInventoryViewport.slotAt(left + 489, top + 101,
                    left, top, 1, 40) == 39, "scrolled final slot");
                require(BankInventoryViewport.slotAt(left, top + 102,
                    left, top, 1, 40) == -1, "bottom padding clipped from hit testing");
                require(BankInventoryViewport.slotAt(left + 490, top,
                    left, top, 1, 40) == -1, "scrollbar excluded from item hit testing");
                require(BankInventoryViewport.slotAt(left, top, left, top, 1, 30) == 0,
                    "stale scroll clamps when capacity returns to base");
                System.out.println("bank-inventory-viewport-ok");
            }
        }
        """
    )

    with tempfile.TemporaryDirectory(prefix="bank-inventory-scroll-") as temp:
        temp_root = Path(temp)
        package = temp_root / "com/openrsc/interfaces/misc"
        package.mkdir(parents=True)
        (package / VIEWPORT.name).write_text(VIEWPORT.read_text(encoding="utf-8"), encoding="utf-8")
        (package / "BankInventoryViewportHarness.java").write_text(harness, encoding="utf-8")
        subprocess.run(
            ["javac", "-source", "8", "-target", "8", "-d", str(temp_root),
             str(package / VIEWPORT.name), str(package / "BankInventoryViewportHarness.java")],
            check=True,
        )
        completed = subprocess.run(
            ["java", "-cp", str(temp_root),
             "com.openrsc.interfaces.misc.BankInventoryViewportHarness"],
            check=True,
            text=True,
            capture_output=True,
        )
        assert completed.stdout.strip() == "bank-inventory-viewport-ok"

    print("PASS: bank inventory overflow is clipped, independently scrollable, and hit-tested")


if __name__ == "__main__":
    main()
