#!/usr/bin/env python3
"""Regression checks for desktop side-menu pinning and fixed HUD layout."""
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        raise SystemExit(f"FAIL: {label}: missing {snippet!r}")


def main() -> None:
    text = CLIENT.read_text(encoding="utf-8")

    for tab in (
        "Config.INVENTORY_TAB",
        "Config.MINIMAP_AND_COMPASS_TAB",
        "Config.SKILLS_AND_QUESTS_TAB",
        "Config.MAGIC_AND_PRAYER_TAB",
        "Config.FRIENDS_TAB",
        "Config.OPTIONS_TAB",
    ):
        require(text, f"return {tab};", f"side-menu icon mapping for {tab}")

    for snippet in (
        "private int pinnedSideMenuTab = 0;",
        "private boolean handleSideMenuTabInteraction(final int tab)",
        "if (this.pinnedSideMenuTab == tab)",
        "this.pinnedSideMenuTab = tab;",
		"if (tab == Config.MINIMAP_AND_COMPASS_TAB) {\n\t\t\treturn false;",
		"} else {\n\t\t\tthis.showUiTab = tab;",
		"private boolean isCurrentSideMenuHomeTab()",
		"private void closeOrRestorePinnedSideMenu()",
		"this.showUiTab = this.pinnedSideMenuTab != 0 ? this.pinnedSideMenuTab : 0;",
        "this.getSideMenuTabAt(this.mouseX, this.mouseY, 3, 35)",
        "this.getSideMenuTabAt(this.mouseX, this.mouseY, minY, maxY)",
		"!this.isCurrentSideMenuHomeTab()",
		"this.closeOrRestorePinnedSideMenu();",
    ):
        require(text, snippet, "side-menu pin/hover behavior")

    for retired in (
        "minimapPosition",
        "MINIMAP_POSITION_",
        "getMinimapPositionLabel",
        "cycleMinimapPosition",
        "Minimap position",
    ):
        if retired in text:
            raise SystemExit(f"FAIL: retired map relocation control remains: {retired}")
    require(text, 'props.remove("minimap_position");', "legacy map-position migration")
    require(text, "return new int[] {rightX, topY};", "fixed floating map anchor")

    for snippet in (
        "private static final int COMBAT_XP_ALLOCATION_X = 7;",
        "private static final int COMBAT_XP_ALLOCATION_Y = 15;",
        "private static final int COMBAT_XP_ALLOCATION_HEIGHT = 100;",
        "private static final int POTION_HUD_Y = COMBAT_XP_ALLOCATION_Y",
        "+ COMBAT_XP_ALLOCATION_HEIGHT + 8;",
        "int sx = COMBAT_XP_ALLOCATION_X;",
        "int sy = COMBAT_XP_ALLOCATION_Y;",
    ):
        require(text, snippet, "combat/status HUD anchor")

    print("PASS: side-menu pinning, map-position migration, and status HUD layout guarded")


if __name__ == "__main__":
    main()
