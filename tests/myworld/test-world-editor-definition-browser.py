#!/usr/bin/env python3
"""Regression coverage for the shared World Builder definition browser shell."""

from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
BROWSER = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/WorldEditorDefinitionBrowser.java"
EDITOR = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/WorldEditorInterface.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"


class WorldEditorDefinitionBrowserTest(unittest.TestCase):
    def test_editor_wires_search_grid_selection_and_wheel_routing(self) -> None:
        browser = BROWSER.read_text(encoding="utf-8")
        editor = EDITOR.read_text(encoding="utf-8")
        client = CLIENT.read_text(encoding="utf-8")

        self.assertIn("VISIBLE_RESULTS = COLUMNS * VISIBLE_ROWS", browser)
        self.assertIn("entry.tags() + \" \" + entry.searchTerms()", browser)
        self.assertIn("Integer exactId = numericToken(token)", browser)
        self.assertEqual(2, editor.count('"Browse scenery..."'))
        self.assertEqual(2, editor.count('"Browse NPCs..."'))
        self.assertEqual(2, editor.count('"Browse items..."'))
        self.assertIn("renderDefinitionBrowser", editor)
        self.assertIn("handleDefinitionBrowserMouse", editor)
        self.assertIn("selectDefinitionBrowserEntry", editor)
        self.assertIn("case NPC:setNpcId(entry.id())", editor)
        self.assertIn("case ITEM:setGroundItemId(entry.id())", editor)
        self.assertIn("definitionBrowser.resultAtVisibleSlot", editor)
        self.assertIn("scrollDefinitionBrowser", editor)
        self.assertIn("worldEditorInterface.scrollDefinitionBrowser(x)", client)
        self.assertLess(
            client.index("worldEditorInterface.scrollDefinitionBrowser(x)"),
            client.index("showUiTab == Config.SKILLS_AND_QUESTS_TAB", client.index("public void runScroll")),
        )

    def test_compiled_browser_filters_and_pages_catalog_families(self) -> None:
        self.assertTrue(CLIENT_JAR.is_file(), "build the client before running browser coverage")
        fixture = r"""
package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.EntityHandler;

public final class WorldEditorDefinitionBrowserFixture {
    public static void main(String[] args) {
        EntityHandler.load(true);
        WorldEditorDefinitionBrowser browser = new WorldEditorDefinitionBrowser();

        browser.open(WorldEditorDefinitionBrowser.Family.SCENERY, 104);
        require(browser.isOpen(), "browser did not open");
        require(browser.resultCount() == 1332, "unfiltered scenery count changed");
        require(visibleContains(browser, 104), "opening did not center the selected scenery");

        browser.setQuery("tin rock");
        require(browser.resultCount() >= 2, "multi-term semantic search missed tin rocks");
        require(visibleContains(browser, 104), "semantic search missed scenery 104");
        require(visibleContains(browser, 105), "semantic search missed scenery 105");

        browser.setQuery("104");
        require(browser.resultCount() == 1, "numeric search was not exact");
        require(browser.resultAtVisibleSlot(0).id() == 104, "numeric search returned the wrong ID");
        browser.setQuery("#223");
        require(browser.resultCount() == 1, "hash-prefixed search was not exact");
        require(browser.resultAtVisibleSlot(0).id() == 223, "hash-prefixed search returned the wrong ID");

        browser.setQuery("mining guild down");
        require(visibleContains(browser, 223), "action/location metadata was not searchable");
        browser.setQuery("definitely-not-a-real-definition");
        require(browser.resultCount() == 0, "missing query returned results");
        require("0 of 0".equals(browser.rangeLabel()), "empty range label changed");

        browser.clearQuery();
        browser.scrollRows(-10000);
        require(browser.firstIndex() == 0, "scroll underflow was not clamped");
        browser.page(1);
        require(browser.firstIndex() == WorldEditorDefinitionBrowser.VISIBLE_RESULTS,
            "page advance did not move one visible page");
        browser.scrollRows(10000);
        require(browser.firstIndex() + browser.visibleCount() == browser.resultCount(),
            "scroll overflow was not clamped to the final result");

        browser.open(WorldEditorDefinitionBrowser.Family.BOUNDARY, 8);
        browser.setQuery("gray bricks");
        require(visibleContains(browser, 8), "shared boundary metadata was not searchable");

		browser.open(WorldEditorDefinitionBrowser.Family.NPC, 0);
		require(browser.resultCount() == WorldEditorDefinitionCatalog.npcEntries().size(),
			"unfiltered NPC count changed");
		require(browser.resultCount() > 100, "NPC browser omitted runtime definitions");
		require(visibleContains(browser, 0), "opening did not center the selected NPC");
		browser.setQuery("hans castle servant combat");
		require(browser.resultCount() >= 1, "NPC name/description/behavior search missed Hans");
		require("Hans".equals(browser.resultAtVisibleSlot(0).displayName()),
			"NPC search returned the wrong definition");
		browser.setQuery("pickpocket citizens");
		require(browser.resultCount() >= 1, "NPC action metadata was not searchable");

		browser.open(WorldEditorDefinitionBrowser.Family.ITEM, 10);
		require(browser.resultCount() == WorldEditorDefinitionCatalog.itemEntries().size(),
			"unfiltered item count changed");
		require(browser.resultCount() > 2000, "item browser omitted runtime definitions");
		require(visibleContains(browser, 10), "opening did not center the selected item");
		browser.setQuery("lovely money stackable");
		require(visibleContains(browser, 10), "item description/trait search missed coins");
		browser.setQuery("bury bones");
		require(visibleContains(browser, 20), "item action search missed bones");
		browser.setQuery("#18");
		require(browser.resultCount() == 1, "exact item ID search returned extra definitions");
		require(browser.resultAtVisibleSlot(0).id() == 18, "exact item ID search returned the wrong item");
        browser.close();
        require(!browser.isOpen(), "browser did not close");
    }

    private static boolean visibleContains(WorldEditorDefinitionBrowser browser, int id) {
        for (int slot = 0; slot < WorldEditorDefinitionBrowser.VISIBLE_RESULTS; slot++) {
            WorldEditorDefinitionCatalog.Entry entry = browser.resultAtVisibleSlot(slot);
            if (entry != null && entry.id() == id) return true;
        }
        return false;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
"""
        with tempfile.TemporaryDirectory(prefix="world-editor-definition-browser-") as temporary:
            directory = Path(temporary)
            package = directory / "com/openrsc/interfaces/misc"
            package.mkdir(parents=True)
            source = package / "WorldEditorDefinitionBrowserFixture.java"
            source.write_text(fixture, encoding="utf-8")
            compiled = subprocess.run(
                ["javac", "-cp", str(CLIENT_JAR), "-d", str(directory), str(source)],
                cwd=ROOT,
                capture_output=True,
                text=True,
            )
            self.assertEqual(0, compiled.returncode, compiled.stdout + compiled.stderr)
            executed = subprocess.run(
                [
                    "java",
                    "-cp",
                    f"{directory}:{CLIENT_JAR}",
                    "com.openrsc.interfaces.misc.WorldEditorDefinitionBrowserFixture",
                ],
                cwd=ROOT,
                capture_output=True,
                text=True,
            )
            self.assertEqual(0, executed.returncode, executed.stdout + executed.stderr)


if __name__ == "__main__":
    unittest.main()
