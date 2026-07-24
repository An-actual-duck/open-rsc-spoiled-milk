#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
APPLET = ROOT / "PC_Client/src/orsc/ORSCApplet.java"
INPUT_BRIDGE = ROOT / "PC_Client/src/orsc/OpenGLInputBridge.java"
KEY_BINDINGS = ROOT / "PC_Client/src/orsc/OpenGLKeyBindings.java"
KEY_BINDING = ROOT / "PC_Client/src/orsc/KeyBinding.java"
LWJGL_BINDINGS = ROOT / "PC_Client/src/orsc/LwjglBindings.java"
BANK = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/CustomBankInterface.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def main() -> None:
    applet = APPLET.read_text(encoding="utf-8")
    input_bridge = INPUT_BRIDGE.read_text(encoding="utf-8")
    key_bindings = KEY_BINDINGS.read_text(encoding="utf-8")
    key_bindings += "\n" + KEY_BINDING.read_text(encoding="utf-8")
    lwjgl_bindings = LWJGL_BINDINGS.read_text(encoding="utf-8")
    bank = BANK.read_text(encoding="utf-8")
    client = CLIENT.read_text(encoding="utf-8")

    require(
        applet,
        "mudclient.controlPressed = (mod & Event.CTRL_MASK) != 0;",
        "legacy AWT Ctrl modifier consumer",
    )
    require(
        applet,
        "mudclient.shiftPressed = (mod & Event.SHIFT_MASK) != 0;",
        "legacy AWT Shift modifier consumer",
    )
    require(
        applet,
        "boolean mayBeScrollable = mudclient.isMouseOverOpenUiTabPanel(mouseX, mouseY);",
        "mouse wheel zoom only yields to an open tab under the cursor",
    )
    require(applet, "middleMouseOrbit.update(", "desktop middle-orbit drag gate")
    require(applet, "mudclient.adjustCameraPitch(-deltaY);", "desktop middle-drag pitch")

    require(
        lwjgl_bindings,
        'glfwGetKey = method(glfwClass, "glfwGetKey", long.class, int.class);',
        "OpenGL physical key polling binding",
    )
    require(lwjgl_bindings, 'GLFW_KEY_LEFT_CONTROL = constant(glfwClass, "GLFW_KEY_LEFT_CONTROL");', "left Ctrl constant")
    require(lwjgl_bindings, 'GLFW_KEY_RIGHT_CONTROL = constant(glfwClass, "GLFW_KEY_RIGHT_CONTROL");', "right Ctrl constant")
    require(input_bridge, "private boolean isControlDown()", "OpenGL Ctrl helper")
    require(input_bridge, "|| isGlfwKeyDown(gl.GLFW_KEY_LEFT_CONTROL)", "left Ctrl physical-state fallback")
    require(input_bridge, "|| isGlfwKeyDown(gl.GLFW_KEY_RIGHT_CONTROL)", "right Ctrl physical-state fallback")
    require(input_bridge, "if (isControlDown()) {\n\t\t\tmodifiers |= InputEvent.CTRL_MASK;", "AWT Ctrl mask emission")
    require(input_bridge, "if (isShiftDown()) {\n\t\t\tmodifiers |= InputEvent.SHIFT_MASK;", "AWT Shift mask emission")
    require(input_bridge, "if (isAltDown()) {\n\t\t\tmodifiers |= InputEvent.ALT_MASK;", "AWT Alt mask emission")
    require(
        input_bridge,
        "boolean repeated = action == gl.GLFW_REPEAT;",
        "OpenGL key repeat detection",
    )
    require(
        input_bridge,
        "if (repeated && !binding.postsRepeatPressEvents())",
        "OpenGL key repeat filter",
    )
    require(
        input_bridge,
        "boolean pressed = action == gl.GLFW_PRESS || repeated;",
        "OpenGL repeat emits legacy pressed events",
    )
    require(
        input_bridge,
        "if (!repeated && pressed == keyDown[keyIndex])",
        "OpenGL held-key duplicate guard keeps repeats eligible",
    )
    require(
        key_bindings,
        "return normalChar == '\\b';",
        "OpenGL backspace repeat whitelist",
    )

    require(
        bank,
        "if (mc.getMouseClick() == 1 && mc.controlPressed && !equipmentMode",
        "Ctrl-click bank withdraw shortcut",
    )
    require(bank, "sendWithdraw(Integer.MAX_VALUE);", "full bank stack withdrawal")
    require(
        bank,
        "else if (mc.getMouseClick() == 1 && mc.controlPressed",
        "Ctrl-click inventory deposit shortcut",
    )
    require(bank, "sendDeposit(Integer.MAX_VALUE);", "full inventory stack deposit")

    require(client, "item = selectCtrlClickNpcAltAction(item);", "Ctrl-click NPC alternate action hook")
    require(client, "item = selectCtrlClickObjectTravelAction(item);", "Ctrl-click object alternate action hook")
    require(client, 'normalizedLabel.equals("bank")', "NPC bank shortcut label")
    require(client, 'normalizedLabel.equals("shop") || normalizedLabel.equals("trade")', "NPC shop/trade shortcut label")
    require(client, 'equalsIgnoreCase("Travel")', "object travel shortcut label")
    require(client, "public boolean isMouseOverOpenUiTabPanel(int x, int y)", "open tab hover helper")
    require(client, "if (this.showUiTab == 0 || this.getSurface() == null)", "open tab hover ignores closed tabs")

    print("PASS: OpenGL input bridge preserves legacy Ctrl-click shortcut modifiers")


if __name__ == "__main__":
    main()
