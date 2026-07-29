#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
SCENE = ROOT / "Client_Base/src/orsc/graphics/three/Scene.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def custom_panel_bounds(
    tab: str,
    surface_width: int = 960,
    tab_bar_y: int = 486,
    equipment: bool = False,
    exp_info: bool = True,
    openpk: bool = False,
    clan: bool = False,
) -> tuple[int, int, int, int] | None:
    right = surface_width
    left = right - 199
    bottom = tab_bar_y
    if tab == "inventory":
        left = right - 248
        top = tab_bar_y - (273 if equipment else 228)
    elif tab == "skills":
        top = tab_bar_y - 287
        height = 186 if openpk else 275 if exp_info else 262
        bottom = min(tab_bar_y, top + height + 12)
    elif tab == "magic":
        top = tab_bar_y - 182
    elif tab == "friends":
        top = tab_bar_y - 182 - (19 if clan else 0)
    elif tab == "options":
        top = tab_bar_y - 265
    else:
        return None
    return left, max(0, top), right, bottom


def contains(bounds: tuple[int, int, int, int] | None, x: int, y: int) -> bool:
    return bounds is not None and bounds[0] <= x < bounds[2] and bounds[1] <= y < bounds[3]


def main() -> None:
    client = CLIENT.read_text(encoding="utf-8")
    scene = SCENE.read_text(encoding="utf-8")

    require(
        client,
        "private boolean isMouseOverCustomOpenTabPanel(int x, int y)",
        "one custom-tab occlusion owner",
    )
    require(
        client,
        "if (!C_CUSTOM_UI) {\n"
        "\t\t\t\treturn false;\n"
        "\t\t\t}",
        "custom tab occlusion disabled for the authentic top-right UI",
    )
    require(
        client,
        "return this.isMouseOverCustomOpenTabPanel(x, y);",
        "scroll and drag input sharing the tab occlusion owner",
    )
    require(
        client,
        "this.isMouseOverCustomTabBar(this.mouseX, this.mouseY)\n"
        "\t\t\t\t|| this.isMouseOverCustomOpenTabPanel(this.mouseX, this.mouseY)",
        "world menu using the same exact custom UI occlusion",
    )
    require(
        client,
        "panelLeft = panelRight - CUSTOM_UI_INVENTORY_PANEL_WIDTH;",
        "inventory-specific 248-pixel panel width",
    )
    require(
        client,
        "panelTop = tabBarY - CUSTOM_UI_OPTIONS_PANEL_HEIGHT;",
        "options-specific panel top",
    )
    if "getUITabsY() - 340" in client:
        fail("legacy blanket 340-pixel tab exclusion remains")
    require(
        client,
        "TERRAIN_NAVIGATION_REJECT mouse=",
        "click-correlated terrain projection rejection diagnostic",
    )
    require(
        client,
        "projection-\"+scene.getTerrainProjectionDiagnostic()",
        "terrain rejection carrying the projection result",
    )
    require(
        client,
        "emitTerrainNavigationRejectForClick();",
        "hover rejection emitted when its following click arrives",
    )
    require(
        client,
        "if (~var2 != 0 && !nativeTerrainPicking)",
        "legacy terrain faces limited to legacy terrain authority",
    )
    require(
        client,
        "this.world.isNativeTerrainAuthorityOnlyActive();",
        "native layered terrain selecting the full-width projection path",
    )
    require(
        client,
        "isMouseOverMessageUi(this.mouseX,this.mouseY)",
        "terrain fallback using visible message UI bounds",
    )
    if "mouseY>=getGameHeight()-70" in client:
        fail("legacy blanket 70-pixel message exclusion remains")
    require(
        scene,
        "public String getTerrainProjectionDiagnostic()",
        "allocation-free projection state exposed on rejected clicks",
    )
    require(
        scene,
        'return "march-left-field";',
        "presentation-field exit reason",
    )

    inventory = custom_panel_bounds("inventory")
    if not contains(inventory, 800, 300):
        fail("visible inventory body must occlude world input")
    if contains(inventory, 800, 200):
        fail("visible world above inventory must remain interactive")
    if contains(inventory, 700, 300):
        fail("world left of inventory must remain interactive")

    equipment = custom_panel_bounds("inventory", equipment=True)
    if not contains(equipment, 800, 220):
        fail("visible equipment body must occlude world input")

    magic = custom_panel_bounds("magic")
    if contains(magic, 800, 250) or not contains(magic, 800, 350):
        fail("magic occlusion must match its lower-right 182-pixel panel")

    options = custom_panel_bounds("options")
    if contains(options, 800, 200) or not contains(options, 800, 250):
        fail("options occlusion must match its lower-right 265-pixel panel")

    compact_skills = custom_panel_bounds("skills", exp_info=False)
    if contains(compact_skills, 800, 480):
        fail("compact skills panel must not retain the old blank lower exclusion")

    print("PASS: widescreen world input excludes only visible custom UI panels")


if __name__ == "__main__":
    main()
