#!/usr/bin/env python3
"""Regression checks for bounded desktop pinned-tab input capture."""
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
POLICY = ROOT / "Client_Base/src/orsc/SideMenuPinPolicy.java"
FIXTURE = ROOT / "tests/myworld/fixtures/SideMenuPinPolicyFixture.java"


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        raise SystemExit(f"FAIL: {label}: missing {snippet!r}")


def main() -> None:
    text = CLIENT.read_text(encoding="utf-8")
    input_helper = text.split("\tprivate boolean mouseInTabArea_CUSTOM()", 1)[1].split(
        "\n\tprivate boolean hasRunes", 1
    )[0]

    # Every pinnable desktop side panel must be represented by the bounded
    # custom-panel switch; the minimap intentionally keeps its own bounds.
    for tab in (
        "Config.INVENTORY_TAB",
        "Config.SKILLS_AND_QUESTS_TAB",
        "Config.MAGIC_AND_PRAYER_TAB",
        "Config.FRIENDS_TAB",
        "Config.OPTIONS_TAB",
    ):
        require(text, f"case {tab}:", f"bounded input case for {tab}")

    for snippet, label in (
        ("private boolean isMouseOverCustomOpenTabPanel(int x, int y)", "custom panel bounds helper"),
        ("private boolean isMouseOverCustomTabBar(int x, int y)", "tab-bar bounds helper"),
        ("|| this.isMouseOverOpenUiTabPanel(this.mouseX, this.mouseY)", "world input uses bounded side-tab hit test"),
        ("boolean acceptTabInput = mustDrawMenu && mouseInTabArea;", "tab input is gated by bounded hit test"),
        ("int visibleSideMenuTab = this.getVisibleSideMenuTab();", "detached panel render state"),
        ("if (!mouseInTabArea && !interfaceOpen && mustDrawMenu)", "world input remains active outside panel"),
        ("this.drawUiTab1(-15252, acceptTabInput);", "inventory receives only bounded input"),
        ("this.drawUiTabPlayerInfo(acceptTabInput, var1 ^ 0);", "skills receives only bounded input"),
        ("this.drawUiTabMagic(acceptTabInput, (byte) -74);", "magic receives only bounded input"),
        ("this.drawUiTab5(acceptTabInput, false);", "social receives only bounded input"),
        ("this.drawUiTabOptions(15, acceptTabInput);", "options receives only bounded input"),
        ("private int getCustomEquipmentPanelTop()", "equipment draw/hit geometry helper"),
        ("yOffset = C_CUSTOM_UI\n\t\t\t\t\t? getCustomEquipmentPanelTop()", "equipment draw uses shared geometry"),
        ("? getCustomEquipmentPanelTop()\n\t\t\t\t\t: getCustomInventoryPanelTop();", "equipment hit test uses shared geometry"),
        ("private boolean shouldDrawMinimapPanel()", "minimap remains separately rendered"),
        ("int[] minimapBounds = this.getMinimapContentBounds(floatingMinimap);", "minimap retains content-bound input"),
        ("this.drawUiTabMinimap(mustDrawMenu, (byte) 125);", "minimap input path remains unchanged"),
        ("this.showUiTab = SideMenuPinPolicy.transientTabAfterIconInteraction(", "pinning detaches transient tab"),
        ("return SideMenuPinPolicy.visibleTab(this.showUiTab, this.pinnedSideMenuTab);", "pinned panel visibility fallback"),
        ("int visibleSideMenuTab = this.getVisibleSideMenuTab();\n\t\treturn visibleSideMenuTab != 0", "minimap remains pinned behind another panel"),
    ):
        require(text, snippet, label)

    for retired in (
        "getUITabsY() - 340",
        "CUSTOM_UI_EQUIPMENT_PANEL_HEIGHT",
    ):
        if retired in text:
            raise SystemExit(f"FAIL: stale broad pinned-tab bound remains: {retired}")

    # The hit decision is button-agnostic. Therefore left and right world-menu
    # clicks, along with middle-button camera input, are all passed through
    # outside UI bounds instead of depending on a special pinned-tab mode.
    for button_state in ("mouseButtonClick", "currentMouseButtonDown", "lastMouseButtonDown"):
        if button_state in input_helper:
            raise SystemExit(f"FAIL: tab hit testing must not depend on {button_state}")

    with tempfile.TemporaryDirectory(prefix="side-menu-pin-fixture-") as output:
        subprocess.run(
            ["javac", "-d", output, str(POLICY), str(FIXTURE)],
            cwd=ROOT,
            check=True,
        )
        subprocess.run(
            ["java", "-cp", output, "orsc.SideMenuPinPolicyFixture"],
            cwd=ROOT,
            check=True,
        )

    print("PASS: pinned desktop tabs capture input only within rendered bounds")


if __name__ == "__main__":
    main()
