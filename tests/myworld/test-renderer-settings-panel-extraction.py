#!/usr/bin/env python3
"""Exercise the extracted desktop Graphics row and input contract."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"


FIXTURE = r"""
package orsc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RendererSettingsPanelFixture {
	private static final RendererSettingsPanel PANEL = new RendererSettingsPanel();

	private static final class FakeView implements RendererSettingsPanel.View {
		final List<String> rows = new ArrayList<String>();
		final List<Integer> rowActions = new ArrayList<Integer>();
		final List<String> drawn = new ArrayList<String>();
		int scroll;
		int selected = -1;

		public void clearRows() {
			rows.clear();
			rowActions.clear();
		}

		public void setRow(int index, String label, int action) {
			if (index != rows.size()) {
				throw new AssertionError("non-contiguous row index " + index);
			}
			rows.add(label);
			rowActions.add(action);
		}

		public void drawString(String text, int x, int y, int color, int font) {
			drawn.add(text);
		}

		public int stringWidth(int font, String text) {
			return text.length();
		}

		public int scrollAmount() {
			return scroll;
		}

		public int selectedAction() {
			return selected;
		}

		public void drawPanel() {
		}
	}

	private static final class FakeActions implements RendererSettingsPanel.Actions {
		final List<RendererSettingsPanel.Action> performed =
			new ArrayList<RendererSettingsPanel.Action>();
		String tuningControl;
		int tuningLevel;

		public void perform(RendererSettingsPanel.Action action) {
			performed.add(action);
		}

		public void rendererTuningChanged(String control, int level, float value) {
			tuningControl = control;
			tuningLevel = level;
		}
	}

	private RendererSettingsPanelFixture() {
	}

	public static void main(String[] args) {
		assertOpenGLRows();
		assertSoftwareRowsAndScroll();
		assertStableActionDispatch();
		assertSliderClickAndDrag();
	}

	private static RendererSettingsPanel.State state(boolean openGL, boolean roofs, boolean flicker) {
		return new RendererSettingsPanel.State(
			openGL,
			"@yel@Test",
			true,
			"@gre@With tilt",
			ScaledWindow.ScalingAlgorithm.INTEGER_SCALING,
			2.0f,
			Arrays.asList(1.0f, 2.0f, 3.0f),
			"@gre@4:3",
			true,
			"@yel@Directional",
			"@gre@Smooth",
			"@gre@On",
			"@gre@On",
			10,
			10,
			10,
			10,
			10,
			10,
			roofs,
			false,
			flicker,
			true);
	}

	private static RendererSettingsPanel.Input input(int mouseX, int mouseY,
												 int click, int down) {
		return new RendererSettingsPanel.Input(1000, mouseX, mouseY, click, down, false);
	}

	private static void assertOpenGLRows() {
		List<RendererSettingsPanel.Row> rows = RendererSettingsPanel.rows(state(true, true, true));
		int[] expectedActions = {
			-1000, 59, 72, 73, 56, 63, 61, 62, 64, 60,
			-1000, 66, -1000, 67, -1000, 68, -1000, 69,
			-1000, 70, -1000, 71, -1000, 26, 42
		};
		String[] expectedFixedLabels = {
			"@yel@Graphics",
			"@whi@Preset - @yel@Test",
			"@whi@Sprites: @cya@Enhanced",
			"@whi@Middle mouse - @gre@With tilt",
			"@whi@Aspect Ratio - @gre@4:3",
			"@whi@Borderless - @gre@On",
			"@whi@Lighting - @yel@Directional",
			"@whi@Geometry - @gre@Smooth",
			"@whi@Terrain Variation - @gre@On",
			"@whi@Fog - @gre@On",
			"@whi@Terrain shading",
			null,
			"@whi@Object shading",
			null,
			"@whi@Brightness / dimness",
			null,
			"@whi@Contrast",
			null,
			"@whi@Gamma",
			null,
			"@whi@Saturation",
			null,
			"@yel@Visibility",
			"@whi@Hide Roofs - @red@Off",
			"@whi@Hide Underground Flicker - @gre@On"
		};
		assertEquals(expectedActions.length, rows.size(), "OpenGL row count");
		for (int index = 0; index < expectedActions.length; index++) {
			assertEquals(expectedActions[index], rows.get(index).action, "OpenGL action " + index);
			if (expectedFixedLabels[index] != null) {
				assertEquals(expectedFixedLabels[index], rows.get(index).label,
					"OpenGL label " + index);
			} else if (!rows.get(index).label.startsWith("@whi@- [")
				|| !rows.get(index).label.endsWith("] + @yel@[10]")) {
				throw new AssertionError("slider label drift at " + index + ": " + rows.get(index).label);
			}
		}

		List<RendererSettingsPanel.Row> disabled = RendererSettingsPanel.rows(state(true, false, false));
		assertEquals(23, disabled.size(), "visibility-disabled OpenGL row count");
		assertEquals("@yel@Visibility", disabled.get(22).label, "visibility section remains");
	}

	private static void assertSoftwareRowsAndScroll() {
		List<RendererSettingsPanel.Row> rows = RendererSettingsPanel.rows(state(false, true, true));
		int[] expectedActions = {-1000, 72, 73, 49, 46, -1000, 26, 42};
		String[] expectedLabels = {
			"@yel@Graphics",
			"@whi@Sprites: @cya@Enhanced",
			"@whi@Middle mouse - @gre@With tilt",
			"@whi@Scaling - ",
			"@whi@Scaling type - @gre@@gre@Integer",
			"@yel@Visibility",
			"@whi@Hide Roofs - @red@Off",
			"@whi@Hide Underground Flicker - @gre@On"
		};
		assertEquals(expectedActions.length, rows.size(), "software row count");
		for (int index = 0; index < expectedActions.length; index++) {
			assertEquals(expectedActions[index], rows.get(index).action, "software action " + index);
			assertEquals(expectedLabels[index], rows.get(index).label, "software label " + index);
		}

		FakeView visible = new FakeView();
		PANEL.draw(visible, state(false, true, true), input(0, 0, 0, 0), 800, (short) 184, 803, 66);
		assertEquals(3, countScalingDraws(visible.drawn), "visible scaling controls");
		FakeView scrolled = new FakeView();
		scrolled.scroll = 4;
		PANEL.draw(scrolled, state(false, true, true), input(0, 0, 0, 0), 800, (short) 184, 803, 66);
		assertEquals(0, countScalingDraws(scrolled.drawn), "scrolled scaling controls");
	}

	private static int countScalingDraws(List<String> drawn) {
		int count = 0;
		for (String text : drawn) {
			if (text.startsWith("[ ") || "2x".equals(text)) {
				count++;
			}
		}
		return count;
	}

	private static void assertStableActionDispatch() {
		int[] ids = {56, 59, 60, 61, 62, 63, 64, 72, 73, 26, 42};
		RendererSettingsPanel.Action[] expected = {
			RendererSettingsPanel.Action.RENDER_SURFACE,
			RendererSettingsPanel.Action.RENDERER_PROFILE,
			RendererSettingsPanel.Action.FOG,
			RendererSettingsPanel.Action.LIGHTING,
			RendererSettingsPanel.Action.GEOMETRY,
			RendererSettingsPanel.Action.WINDOW_MODE,
			RendererSettingsPanel.Action.TERRAIN_VARIATION,
			RendererSettingsPanel.Action.REMASTERED_SPRITES,
			RendererSettingsPanel.Action.MIDDLE_MOUSE,
			RendererSettingsPanel.Action.HIDE_ROOFS,
			RendererSettingsPanel.Action.HIDE_UNDERGROUND_FLICKER
		};
		for (int index = 0; index < ids.length; index++) {
			FakeView view = new FakeView();
			view.selected = ids[index];
			FakeActions actions = new FakeActions();
			boolean handled = PANEL.handleSelectedAction(
				view, state(true, true, true), input(0, 0, 1, 0), 100, 66, actions);
			assertTrue(handled, "OpenGL action handled " + ids[index]);
			assertEquals(1, actions.performed.size(), "OpenGL dispatch count " + ids[index]);
			assertEquals(expected[index], actions.performed.get(0), "OpenGL action " + ids[index]);
		}

		FakeView section = new FakeView();
		section.selected = -1000;
		FakeActions sectionActions = new FakeActions();
		assertTrue(PANEL.handleSelectedAction(
			section, state(true, true, true), input(0, 0, 1, 0), 100, 66, sectionActions),
			"section handled");
		assertEquals(0, sectionActions.performed.size(), "section has no action");

		FakeView unavailable = new FakeView();
		unavailable.selected = 26;
		FakeActions unavailableActions = new FakeActions();
		assertTrue(!PANEL.handleSelectedAction(
			unavailable, state(true, false, false), input(0, 0, 1, 0), 100, 66, unavailableActions),
			"disabled visibility action rejected");

		FakeView fallbackProfile = new FakeView();
		fallbackProfile.selected = 59;
		FakeActions fallbackProfileActions = new FakeActions();
		assertTrue(!PANEL.handleSelectedAction(
			fallbackProfile, state(false, true, true), input(0, 0, 1, 0), 100, 66,
			fallbackProfileActions), "OpenGL action rejected in fallback");

		FakeView scalingType = new FakeView();
		scalingType.selected = 46;
		FakeActions scalingTypeActions = new FakeActions();
		assertTrue(PANEL.handleSelectedAction(
			scalingType, state(false, true, true), input(0, 0, 1, 0), 100, 66,
			scalingTypeActions), "scaling type handled");
		assertEquals(RendererSettingsPanel.Action.SCALING_TYPE,
			scalingTypeActions.performed.get(0), "scaling type action");

		FakeView plus = new FakeView();
		plus.selected = 49;
		FakeActions plusActions = new FakeActions();
		assertTrue(PANEL.handleSelectedAction(
			plus, state(false, true, true), input(965, 129, 1, 0), 100, 66, plusActions),
			"software plus handled");
		assertEquals(RendererSettingsPanel.Action.SCALE_UP, plusActions.performed.get(0),
			"software plus action");

		FakeView hiddenPlus = new FakeView();
		hiddenPlus.selected = 49;
		hiddenPlus.scroll = 4;
		FakeActions hiddenPlusActions = new FakeActions();
		assertTrue(PANEL.handleSelectedAction(
			hiddenPlus, state(false, true, true), input(965, 129, 1, 0), 100, 66,
			hiddenPlusActions), "scrolled software row remains non-actionable");
		assertEquals(0, hiddenPlusActions.performed.size(), "scrolled software click ignored");
	}

	private static void assertSliderClickAndDrag() {
		RendererReliefSettings.setTerrainLevel(10);
		FakeView clicked = new FakeView();
		clicked.selected = 66;
		clicked.scroll = 8;
		FakeActions clickActions = new FakeActions();
		assertTrue(PANEL.handleSelectedAction(
			clicked, state(true, true, true), input(114, 0, 1, 0), 100, 66, clickActions),
			"scrolled terrain slider click handled");
		assertEquals(12, RendererReliefSettings.getTerrainLevel(), "clicked terrain level");
		assertEquals("terrain-relief", clickActions.tuningControl, "clicked tuning control");
		assertEquals(12, clickActions.tuningLevel, "clicked tuning callback level");

		RendererReliefSettings.setObjectLevel(10);
		FakeView dragged = new FakeView();
		dragged.selected = 67;
		dragged.scroll = 10;
		FakeActions dragActions = new FakeActions();
		assertTrue(PANEL.handleSelectedAction(
			dragged, state(true, true, true), input(107, 0, 0, 1), 100, 66, dragActions),
			"scrolled object slider drag handled");
		assertEquals(5, RendererReliefSettings.getObjectLevel(), "dragged object level");
		assertEquals("object-relief", dragActions.tuningControl, "dragged tuning control");
	}

	private static void assertTrue(boolean condition, String label) {
		if (!condition) {
			throw new AssertionError(label);
		}
	}

	private static void assertEquals(Object expected, Object actual, String label) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
		}
	}
}
"""


def main() -> None:
    if not CLIENT_JAR.is_file():
        raise SystemExit(f"FAIL: build the client before running this fixture: {CLIENT_JAR}")
    with tempfile.TemporaryDirectory(prefix="renderer-settings-panel-") as directory:
        temp = Path(directory)
        source = temp / "orsc/RendererSettingsPanelFixture.java"
        source.parent.mkdir(parents=True)
        source.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        subprocess.run(
            ["javac", "-source", "1.8", "-target", "1.8", "-cp", str(CLIENT_JAR),
             "-d", str(temp), str(source)],
            cwd=ROOT,
            check=True,
        )
        subprocess.run(
            ["java", "-cp", f"{temp}:{CLIENT_JAR}", "orsc.RendererSettingsPanelFixture"],
            cwd=ROOT,
            check=True,
        )
    print("PASS: renderer settings panel rows and input dispatch match the desktop contract")


if __name__ == "__main__":
    main()
