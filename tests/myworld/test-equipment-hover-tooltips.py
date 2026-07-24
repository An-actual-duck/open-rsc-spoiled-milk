#!/usr/bin/env python3
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
EQUIP_POLICY = ROOT / "Client_Base/src/orsc/InventoryEquipMenuPolicy.java"
HIT_TARGET = ROOT / "Client_Base/src/orsc/EquipmentSlotHoverTarget.java"
INPUT_BRIDGE = ROOT / "PC_Client/src/orsc/OpenGLInputBridge.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


FIXTURE = r"""
package orsc;

public final class EquipmentSlotHoverTargetFixture {
    private static final int[] SLOT_X = {98, 98, 98, 153, 43, 43, 98, 98, 43, 153, 153, 43};
    private static final int[] SLOT_Y = {5, 85, 125, 85, 85, 165, 165, 45, 45, 45, 165, 125};

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static int slotAt(int x, int y) {
        return EquipmentSlotHoverTarget.findSlot(SLOT_X, SLOT_Y, SLOT_X.length, 1000, 200, x, y);
    }

    public static void main(String[] args) {
        for (int slot = 0; slot < SLOT_X.length; slot++) {
            int left = 1000 + SLOT_X[slot];
            int top = 200 + SLOT_Y[slot];
            require(slotAt(left, top) == slot, "inclusive top-left for slot " + slot);
            require(slotAt(left + 47, top + 31) == slot, "inclusive inner bottom-right for slot " + slot);
            require(slotAt(left + 24, top + 16) == slot, "center for slot " + slot);
        }

        int firstLeft = 1000 + SLOT_X[0];
        int firstTop = 200 + SLOT_Y[0];
        require(slotAt(firstLeft - 1, firstTop) == -1, "left edge excludes outside coordinate");
        require(slotAt(firstLeft + 48, firstTop) == -1, "right edge is exclusive");
        require(slotAt(firstLeft, firstTop - 1) == -1, "top edge excludes outside coordinate");
        require(slotAt(firstLeft, firstTop + 32) == -1, "bottom edge is exclusive");
        require(slotAt(1010, 210) == -1, "kept-on-death button area does not overlap equipment slots");
        require(slotAt(1100, 430) == -1, "equipment stat area is silent");
        require(EquipmentSlotHoverTarget.findSlot(null, SLOT_Y, SLOT_X.length, 0, 0, 0, 0) == -1,
            "null coordinates are silent");
        require(EquipmentSlotHoverTarget.findSlot(SLOT_X, SLOT_Y, 0, 0, 0, 0, 0) == -1,
            "empty slot mapping is silent");

        for (int first = 0; first < SLOT_X.length; first++) {
            for (int second = first + 1; second < SLOT_X.length; second++) {
                boolean overlaps = SLOT_X[first] < SLOT_X[second] + EquipmentSlotHoverTarget.SLOT_WIDTH
                    && SLOT_X[second] < SLOT_X[first] + EquipmentSlotHoverTarget.SLOT_WIDTH
                    && SLOT_Y[first] < SLOT_Y[second] + EquipmentSlotHoverTarget.SLOT_HEIGHT
                    && SLOT_Y[second] < SLOT_Y[first] + EquipmentSlotHoverTarget.SLOT_HEIGHT;
                require(!overlaps, "equipment slot rectangles overlap: " + first + "/" + second);
            }
        }

        System.out.println("PASS: deterministic equipment hover slot boundaries");
    }
}
"""


def main() -> None:
    client = CLIENT.read_text(encoding="utf-8")
    equip_policy = EQUIP_POLICY.read_text(encoding="utf-8")
    input_bridge = INPUT_BRIDGE.read_text(encoding="utf-8")

    require(client, "int hoveredEquipmentSlot = EquipmentSlotHoverTarget.findSlot(",
            "logical equipment slot hover lookup")
    require(client, "if (var2 && !isAndroid() && this.mouseButtonClick == 0",
            "desktop passive-hover gate")
    require(client, "if (hoveredEquipmentSlot >= 0 && equippedItems[hoveredEquipmentSlot] != null)",
            "empty equipment slot guard")
    require(client, "MenuItemAction.ITEM_UNEQUIP_FROM_EQUIPMENT,\n\t\t\t\t\t\t\t\"Unequip\"",
            "Unequip action/name tooltip")

    # Existing click and context-menu behavior must remain separate and unchanged.
    require(client, "if ((this.mouseButtonClick == 1 || this.mouseButtonClick == 2) && this.mouseY > yOffset)",
            "equipment click gate")
    require(client, "newPacket(Opcodes.Out.ITEM_UNEQUIP_FROM_EQUIPMENT.getOpcode());",
            "direct click unequip packet")
    require(client, "this.packetHandler.getClientStream().bufferBits.putByte(j);",
            "equipment slot packet mapping")
    require(client, 'MenuItemAction.ITEM_UNEQUIP_FROM_EQUIPMENT, "Remove"',
            "existing equipment context-menu label")
    require(client, "MenuItemAction.ITEM_EQUIP_FROM_INVENTORY, equipCommand",
            "inventory Equip action remains inventory-only")
    require(client, "InventoryEquipMenuPolicy.canOfferEquip(def, item.getNoted())",
            "inventory equipability uses the item flag")
    require(client, "InventoryEquipMenuPolicy.actionLabel(def)",
            "inventory Wear/Wield tooltip")
    require(equip_policy, '!= 0 ? "Wield" : "Wear"',
            "visual type controls only Wear/Wield wording")

    require(input_bridge, "int x = delegate.mapMouseX(cursorX[0]);", "OpenGL logical X mapping")
    require(input_bridge, "int y = delegate.mapMouseY(cursorY[0]);", "OpenGL logical Y mapping")

    with tempfile.TemporaryDirectory(prefix="equipment-hover-target-") as raw_tmp:
        tmp = Path(raw_tmp)
        fixture = tmp / "orsc/EquipmentSlotHoverTargetFixture.java"
        fixture.parent.mkdir(parents=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        classes = tmp / "classes"
        classes.mkdir()
        compile_result = subprocess.run(
            ["javac", "-d", str(classes), str(HIT_TARGET), str(fixture)],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        if compile_result.returncode != 0:
            fail("fixture compile failed:\n" + compile_result.stdout + compile_result.stderr)
        fixture_result = subprocess.run(
            ["java", "-cp", str(classes), "orsc.EquipmentSlotHoverTargetFixture"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        if fixture_result.returncode != 0:
            fail("fixture failed:\n" + fixture_result.stdout + fixture_result.stderr)
        print(fixture_result.stdout.strip())

    print("PASS: equipped-item tooltip and input behavior guards validated")


if __name__ == "__main__":
    main()
