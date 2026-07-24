package orsc;

import orsc.graphics.gui.Panel;
import orsc.graphics.two.MudClientGraphics;
import orsc.remastered.RemasteredSpriteSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static orsc.ScaledWindow.ScalingAlgorithm;

/**
 * Presents and dispatches the desktop Graphics settings tab.
 *
 * <p>The panel owns stable row identities and pointer interpretation, while
 * the client adapter retains application, persistence, packet, and gameplay
 * authority.</p>
 */
final class RendererSettingsPanel {

	static final int SECTION_ROW = -1000;
	static final int TERRAIN_RELIEF_SLIDER = 66;
	static final int OBJECT_RELIEF_SLIDER = 67;
	static final int DIMNESS_SLIDER = 68;
	static final int CONTRAST_SLIDER = 69;
	static final int GAMMA_SLIDER = 70;
	static final int SATURATION_SLIDER = 71;
	static final int REMASTERED_SPRITES = 72;
	static final int MIDDLE_MOUSE = 73;

	private static final int SCALE_MINUS_X_OFFSET = 96;
	private static final int SCALE_LABEL_X_OFFSET = 73;
	private static final int SCALE_PLUS_X_OFFSET = 45;
	private static final int LEGACY_SCALING_ROW_INDEX = 3;

	private static final int ACTION_SCALING_ROW = 49;
	private static final int ACTION_SCALING_TYPE = 46;
	private static final int ACTION_RENDER_SURFACE = 56;
	private static final int ACTION_RENDERER_PROFILE = 59;
	private static final int ACTION_FOG = 60;
	private static final int ACTION_LIGHTING = 61;
	private static final int ACTION_GEOMETRY = 62;
	private static final int ACTION_WINDOW_MODE = 63;
	private static final int ACTION_TERRAIN_VARIATION = 64;
	private static final int ACTION_HIDE_ROOFS = 26;
	private static final int ACTION_HIDE_UNDERGROUND_FLICKER = 42;

	enum Action {
		SCALE_DOWN,
		SCALE_UP,
		SCALING_TYPE,
		RENDER_SURFACE,
		RENDERER_PROFILE,
		FOG,
		LIGHTING,
		GEOMETRY,
		WINDOW_MODE,
		TERRAIN_VARIATION,
		REMASTERED_SPRITES,
		MIDDLE_MOUSE,
		HIDE_ROOFS,
		HIDE_UNDERGROUND_FLICKER
	}

	interface Actions {
		void perform(Action action);

		void rendererTuningChanged(String control, int level, float value);
	}

	interface View {
		void clearRows();

		void setRow(int index, String label, int action);

		void drawString(String text, int x, int y, int color, int font);

		int stringWidth(int font, String text);

		int scrollAmount();

		int selectedAction();

		void drawPanel();
	}

	static final class PanelView implements View {
		private final Panel panel;
		private final int control;
		private final MudClientGraphics surface;

		PanelView(Panel panel, int control, MudClientGraphics surface) {
			this.panel = panel;
			this.control = control;
			this.surface = surface;
		}

		@Override
		public void clearRows() {
			panel.clearList(control);
		}

		@Override
		public void setRow(int index, String label, int action) {
			panel.setListEntry(control, index, label, action, null, null);
		}

		@Override
		public void drawString(String text, int x, int y, int color, int font) {
			surface.drawString(text, x, y, color, font);
		}

		@Override
		public int stringWidth(int font, String text) {
			return surface.stringWidth(font, text);
		}

		@Override
		public int scrollAmount() {
			return panel.controlScrollAmount[control];
		}

		@Override
		public int selectedAction() {
			int selectedRow = panel.getControlSelectedListIndex(control);
			return selectedRow >= 0 ? panel.getControlSelectedListInt(control, selectedRow) : selectedRow;
		}

		@Override
		public void drawPanel() {
			panel.drawPanel();
		}
	}

	static final class Input {
		final int gameWidth;
		final int mouseX;
		final int mouseY;
		final int mouseButtonClick;
		final int mouseButtonDown;
		final boolean customUi;

		Input(int gameWidth, int mouseX, int mouseY, int mouseButtonClick,
			  int mouseButtonDown, boolean customUi) {
			this.gameWidth = gameWidth;
			this.mouseX = mouseX;
			this.mouseY = mouseY;
			this.mouseButtonClick = mouseButtonClick;
			this.mouseButtonDown = mouseButtonDown;
			this.customUi = customUi;
		}
	}

	static final class State {
		final boolean openGLPrimary;
		final String rendererProfileLabel;
		final boolean remasteredSprites;
		final String middleMouseLabel;
		final ScalingAlgorithm scalingType;
		final float renderingScalar;
		final List<Float> scalingOptions;
		final String aspectLabel;
		final boolean borderless;
		final String lightingLabel;
		final String geometryLabel;
		final String terrainVariationLabel;
		final String fogLabel;
		final int terrainReliefLevel;
		final int objectReliefLevel;
		final int dimnessLevel;
		final int contrastLevel;
		final int gammaLevel;
		final int saturationLevel;
		final boolean roofToggleAvailable;
		final boolean roofsHidden;
		final boolean undergroundFlickerToggleAvailable;
		final boolean undergroundFlickerHidden;

		State(boolean openGLPrimary, String rendererProfileLabel, boolean remasteredSprites,
			  String middleMouseLabel,
			  ScalingAlgorithm scalingType, float renderingScalar, List<Float> scalingOptions,
			  String aspectLabel, boolean borderless, String lightingLabel, String geometryLabel,
			  String terrainVariationLabel, String fogLabel, int terrainReliefLevel,
			  int objectReliefLevel, int dimnessLevel, int contrastLevel, int gammaLevel,
			  int saturationLevel, boolean roofToggleAvailable, boolean roofsHidden,
			  boolean undergroundFlickerToggleAvailable, boolean undergroundFlickerHidden) {
			this.openGLPrimary = openGLPrimary;
			this.rendererProfileLabel = rendererProfileLabel;
			this.remasteredSprites = remasteredSprites;
			this.middleMouseLabel = middleMouseLabel;
			this.scalingType = scalingType;
			this.renderingScalar = renderingScalar;
			this.scalingOptions = scalingOptions;
			this.aspectLabel = aspectLabel;
			this.borderless = borderless;
			this.lightingLabel = lightingLabel;
			this.geometryLabel = geometryLabel;
			this.terrainVariationLabel = terrainVariationLabel;
			this.fogLabel = fogLabel;
			this.terrainReliefLevel = terrainReliefLevel;
			this.objectReliefLevel = objectReliefLevel;
			this.dimnessLevel = dimnessLevel;
			this.contrastLevel = contrastLevel;
			this.gammaLevel = gammaLevel;
			this.saturationLevel = saturationLevel;
			this.roofToggleAvailable = roofToggleAvailable;
			this.roofsHidden = roofsHidden;
			this.undergroundFlickerToggleAvailable = undergroundFlickerToggleAvailable;
			this.undergroundFlickerHidden = undergroundFlickerHidden;
		}

		static State capture(boolean openGLPrimary, ScalingAlgorithm scalingType,
						 float renderingScalar, List<Float> scalingOptions,
						 boolean roofToggleAvailable, boolean roofsHidden,
						 boolean undergroundFlickerToggleAvailable,
						 boolean undergroundFlickerHidden) {
			return new State(
				openGLPrimary,
				RendererProfileSettings.getMode().label,
				RemasteredSpriteSettings.isEnabled(),
				DesktopMiddleMouseSettings.getMode().label,
				scalingType,
				renderingScalar,
				scalingOptions,
				RenderSurfaceSettings.getAspectLabel(),
				OpenGLWindowSettings.getMode() == OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN,
				RendererLightingSettings.getMode().label,
				RendererGeometrySettings.getMode().label,
				RendererTerrainVariationSettings.getMode().label,
				RendererFogSettings.getMode().label,
				RendererReliefSettings.getTerrainLevel(),
				RendererReliefSettings.getObjectLevel(),
				RendererColorDiagnosticSettings.getDimnessLevel(),
				RendererColorDiagnosticSettings.getContrastLevel(),
				RendererColorDiagnosticSettings.getGammaLevel(),
				RendererColorDiagnosticSettings.getSaturationLevel(),
				roofToggleAvailable,
				roofsHidden,
				undergroundFlickerToggleAvailable,
				undergroundFlickerHidden);
		}
	}

	static final class Row {
		final String label;
		final int action;

		Row(String label, int action) {
			this.label = label;
			this.action = action;
		}
	}

	void draw(View view, State state, Input input, int baseX, short boxWidth, int x, int y) {
		int originalY = y - 15;
		view.clearRows();
		view.drawString("Graphics options", 3 + baseX, y, 0, 1);

		List<Row> rows = rows(state);
		for (int index = 0; index < rows.size(); index++) {
			Row row = rows.get(index);
			view.setRow(index, row.label, row.action);
		}

		if (!state.openGLPrimary && view.scrollAmount() <= LEGACY_SCALING_ROW_INDEX) {
			drawSoftwareScalingControls(view, state, input, y);
		}

		y = input.customUi ? originalY + 214 : 275;
		view.drawString("Always logout when you finish", x, y, 0, 1);
		y += 15;
		int logoutColor = 0xFFFFFF;
		if (x < input.mouseX && x + boxWidth > input.mouseX
			&& y - 12 < input.mouseY && input.mouseY < y + 4) {
			logoutColor = 0xFFFF00;
		}
		view.drawString("Click here to logout", baseX + 3, y, logoutColor, 1);
		view.drawPanel();
	}

	boolean handleSelectedAction(View view, State state, Input input, int textX,
								 int yFromTopDistance, Actions actions) {
		int selectedAction = view.selectedAction();
		if (selectedAction == SECTION_ROW) {
			return true;
		}
		if (isRendererTuningSlider(selectedAction)) {
			handleRendererTuningSliderInput(view, input, selectedAction, textX, actions);
			return true;
		}

		if (!state.openGLPrimary && view.scrollAmount() <= LEGACY_SCALING_ROW_INDEX) {
			int yPos = yFromTopDistance
				+ ((LEGACY_SCALING_ROW_INDEX - view.scrollAmount() + 1) * 15);
			boolean scaleMinusHover = input.gameWidth - input.mouseX >= SCALE_MINUS_X_OFFSET - 18
				&& input.gameWidth - input.mouseX <= SCALE_MINUS_X_OFFSET
				&& input.mouseY >= yPos + 3 && input.mouseY <= yPos + 14;
			if (scaleMinusHover && input.mouseButtonClick == 1) {
				actions.perform(Action.SCALE_DOWN);
			}

			boolean scalePlusHover = input.gameWidth - input.mouseX >= SCALE_PLUS_X_OFFSET - 20
				&& input.gameWidth - input.mouseX <= SCALE_PLUS_X_OFFSET
				&& input.mouseY >= yPos + 3 && input.mouseY <= yPos + 14;
			if (scalePlusHover && input.mouseButtonClick == 1) {
				actions.perform(Action.SCALE_UP);
			}
		}

		Action action = actionFor(selectedAction, state);
		if (action == null) {
			return selectedAction == ACTION_SCALING_ROW && !state.openGLPrimary;
		}
		if (input.mouseButtonClick == 1) {
			actions.perform(action);
		}
		return true;
	}

	static List<Row> rows(State state) {
		List<Row> rows = new ArrayList<Row>();
		addSection(rows, "Graphics");
		if (state.openGLPrimary) {
			addRow(rows, "@whi@Preset - " + state.rendererProfileLabel, ACTION_RENDERER_PROFILE);
		}
		addRow(rows, "@whi@Sprites: "
			+ (state.remasteredSprites ? "@cya@Enhanced" : "@gre@Classic"), REMASTERED_SPRITES);
		addRow(rows, "@whi@Middle mouse - " + state.middleMouseLabel, MIDDLE_MOUSE);

		if (!state.openGLPrimary) {
			addRow(rows, "@whi@Scaling - ", ACTION_SCALING_ROW);
			addRow(rows, "@whi@Scaling type - @gre@" + scalingTypeDescription(state.scalingType),
				ACTION_SCALING_TYPE);
		} else {
			addRow(rows, "@whi@Aspect Ratio - " + state.aspectLabel, ACTION_RENDER_SURFACE);
			addRow(rows, "@whi@Borderless - " + (state.borderless ? "@gre@On" : "@red@Off"),
				ACTION_WINDOW_MODE);
			addRow(rows, "@whi@Lighting - " + state.lightingLabel, ACTION_LIGHTING);
			addRow(rows, "@whi@Geometry - " + state.geometryLabel, ACTION_GEOMETRY);
			addRow(rows, "@whi@Terrain Variation - " + state.terrainVariationLabel,
				ACTION_TERRAIN_VARIATION);
			addRow(rows, "@whi@Fog - " + state.fogLabel, ACTION_FOG);
			addTuningRows(rows, "Terrain shading", state.terrainReliefLevel,
				RendererReliefSettings.MAX_LEVEL, TERRAIN_RELIEF_SLIDER);
			addTuningRows(rows, "Object shading", state.objectReliefLevel,
				RendererReliefSettings.MAX_LEVEL, OBJECT_RELIEF_SLIDER);
			addTuningRows(rows, "Brightness / dimness", state.dimnessLevel,
				RendererColorDiagnosticSettings.MAX_LEVEL, DIMNESS_SLIDER);
			addTuningRows(rows, "Contrast", state.contrastLevel,
				RendererColorDiagnosticSettings.MAX_LEVEL, CONTRAST_SLIDER);
			addTuningRows(rows, "Gamma", state.gammaLevel,
				RendererColorDiagnosticSettings.MAX_LEVEL, GAMMA_SLIDER);
			addTuningRows(rows, "Saturation", state.saturationLevel,
				RendererColorDiagnosticSettings.MAX_LEVEL, SATURATION_SLIDER);
		}

		addSection(rows, "Visibility");
		if (state.roofToggleAvailable) {
			addRow(rows, "@whi@Hide Roofs - " + (state.roofsHidden ? "@gre@On" : "@red@Off"),
				ACTION_HIDE_ROOFS);
		}
		if (state.undergroundFlickerToggleAvailable) {
			addRow(rows, "@whi@Hide Underground Flicker - "
				+ (state.undergroundFlickerHidden ? "@gre@On" : "@red@Off"),
				ACTION_HIDE_UNDERGROUND_FLICKER);
		}
		return Collections.unmodifiableList(rows);
	}

	private static Action actionFor(int selectedAction, State state) {
		if (selectedAction == ACTION_SCALING_TYPE && !state.openGLPrimary) {
			return Action.SCALING_TYPE;
		}
		if (state.openGLPrimary) {
			switch (selectedAction) {
				case ACTION_RENDER_SURFACE:
					return Action.RENDER_SURFACE;
				case ACTION_RENDERER_PROFILE:
					return Action.RENDERER_PROFILE;
				case ACTION_FOG:
					return Action.FOG;
				case ACTION_LIGHTING:
					return Action.LIGHTING;
				case ACTION_GEOMETRY:
					return Action.GEOMETRY;
				case ACTION_WINDOW_MODE:
					return Action.WINDOW_MODE;
				case ACTION_TERRAIN_VARIATION:
					return Action.TERRAIN_VARIATION;
				default:
					break;
			}
		}
		if (selectedAction == REMASTERED_SPRITES) {
			return Action.REMASTERED_SPRITES;
		}
		if (selectedAction == MIDDLE_MOUSE) {
			return Action.MIDDLE_MOUSE;
		}
		if (selectedAction == ACTION_HIDE_ROOFS && state.roofToggleAvailable) {
			return Action.HIDE_ROOFS;
		}
		if (selectedAction == ACTION_HIDE_UNDERGROUND_FLICKER
			&& state.undergroundFlickerToggleAvailable) {
			return Action.HIDE_UNDERGROUND_FLICKER;
		}
		return null;
	}

	private static void drawSoftwareScalingControls(View view, State state, Input input, int y) {
		int yPos = y + ((LEGACY_SCALING_ROW_INDEX - view.scrollAmount() + 1) * 15);
		boolean scaleMinusHover = input.gameWidth - input.mouseX >= SCALE_MINUS_X_OFFSET - 18
			&& input.gameWidth - input.mouseX <= SCALE_MINUS_X_OFFSET
			&& input.mouseY >= yPos - 7 && input.mouseY <= yPos + 4;
		String minusButtonLabel = state.renderingScalar <= 1 ? " " : "-";
		int minusButtonColor = state.renderingScalar <= 1
			? 16777215 : (scaleMinusHover ? 65280 : 16616744);
		view.drawString("[ " + minusButtonLabel + " ]",
			input.gameWidth - SCALE_MINUS_X_OFFSET, yPos, minusButtonColor, 1);

		String scalarLabel = state.scalingType == ScalingAlgorithm.INTEGER_SCALING
			? (int) state.renderingScalar + "x" : state.renderingScalar + "x";
		int scalarLabelOffset = state.scalingType == ScalingAlgorithm.INTEGER_SCALING
			? SCALE_LABEL_X_OFFSET : SCALE_LABEL_X_OFFSET + 5;
		int scalarLabelColor = state.renderingScalar > 1 ? 65280 : 16777215;
		view.drawString(scalarLabel, input.gameWidth - scalarLabelOffset,
			yPos + 1, scalarLabelColor, 1);

		boolean scalePlusHover = input.gameWidth - input.mouseX >= SCALE_PLUS_X_OFFSET - 20
			&& input.gameWidth - input.mouseX <= SCALE_PLUS_X_OFFSET
			&& input.mouseY >= yPos - 7 && input.mouseY <= yPos + 4;
		boolean maxScalar = state.scalingOptions.indexOf(state.renderingScalar)
			== state.scalingOptions.size() - 1;
		String plusButtonLabel = maxScalar ? "  " : "+";
		int plusButtonColor = maxScalar ? 16777215 : (scalePlusHover ? 65280 : 16616744);
		view.drawString("[ " + plusButtonLabel + " ]",
			input.gameWidth - SCALE_PLUS_X_OFFSET, yPos, plusButtonColor, 1);
	}

	private static void handleRendererTuningSliderInput(View view, Input input, int settingIndex,
											  int textX, Actions actions) {
		int trackStartX = textX + view.stringWidth(1, "- [");
		int trackEndX = trackStartX;
		int currentLevel = getRendererTuningLevel(settingIndex);
		int maxLevel = getRendererTuningMaxLevel(settingIndex);
		for (int level = RendererReliefSettings.MIN_LEVEL; level <= maxLevel; level++) {
			String segment = level == currentLevel ? "o" : "-";
			int segmentEndX = trackEndX + Math.max(1, view.stringWidth(1, segment));
			if ((input.mouseButtonClick == 1 || input.mouseButtonDown == 1)
				&& input.mouseX >= trackEndX && input.mouseX < segmentEndX) {
				setRendererTuningLevel(settingIndex, level, actions);
				return;
			}
			trackEndX = segmentEndX;
		}
		int minusEndX = textX + view.stringWidth(1, "-");
		int plusStartX = trackEndX + view.stringWidth(1, "] ");
		int plusEndX = plusStartX + view.stringWidth(1, "+");

		if (input.mouseButtonClick == 1 && input.mouseX >= textX && input.mouseX <= minusEndX) {
			setRendererTuningLevel(settingIndex, currentLevel - 1, actions);
		} else if (input.mouseButtonClick == 1
			&& input.mouseX >= plusStartX && input.mouseX <= plusEndX) {
			setRendererTuningLevel(settingIndex, currentLevel + 1, actions);
		}
	}

	private static boolean isRendererTuningSlider(int settingIndex) {
		return settingIndex >= TERRAIN_RELIEF_SLIDER && settingIndex <= SATURATION_SLIDER;
	}

	private static int getRendererTuningLevel(int settingIndex) {
		if (settingIndex == TERRAIN_RELIEF_SLIDER) {
			return RendererReliefSettings.getTerrainLevel();
		}
		if (settingIndex == OBJECT_RELIEF_SLIDER) {
			return RendererReliefSettings.getObjectLevel();
		}
		if (settingIndex == DIMNESS_SLIDER) {
			return RendererColorDiagnosticSettings.getDimnessLevel();
		}
		if (settingIndex == CONTRAST_SLIDER) {
			return RendererColorDiagnosticSettings.getContrastLevel();
		}
		if (settingIndex == GAMMA_SLIDER) {
			return RendererColorDiagnosticSettings.getGammaLevel();
		}
		return RendererColorDiagnosticSettings.getSaturationLevel();
	}

	private static int getRendererTuningMaxLevel(int settingIndex) {
		return settingIndex == TERRAIN_RELIEF_SLIDER || settingIndex == OBJECT_RELIEF_SLIDER
			? RendererReliefSettings.MAX_LEVEL : RendererColorDiagnosticSettings.MAX_LEVEL;
	}

	private static void setRendererTuningLevel(int settingIndex, int level, Actions actions) {
		if (settingIndex == TERRAIN_RELIEF_SLIDER) {
			int oldLevel = RendererReliefSettings.getTerrainLevel();
			int acceptedLevel = RendererReliefSettings.setTerrainLevel(level);
			if (oldLevel != acceptedLevel) {
				actions.rendererTuningChanged("terrain-relief", acceptedLevel,
					RendererReliefSettings.getTerrainStrength());
			}
		} else if (settingIndex == OBJECT_RELIEF_SLIDER) {
			int oldLevel = RendererReliefSettings.getObjectLevel();
			int acceptedLevel = RendererReliefSettings.setObjectLevel(level);
			if (oldLevel != acceptedLevel) {
				actions.rendererTuningChanged("object-relief", acceptedLevel,
					RendererReliefSettings.getObjectStrength());
			}
		} else if (settingIndex == DIMNESS_SLIDER) {
			int oldLevel = RendererColorDiagnosticSettings.getDimnessLevel();
			int acceptedLevel = RendererColorDiagnosticSettings.setDimnessLevel(level);
			if (oldLevel != acceptedLevel) {
				actions.rendererTuningChanged("dimness", acceptedLevel,
					RendererColorDiagnosticSettings.getDimnessMultiplier());
			}
		} else if (settingIndex == CONTRAST_SLIDER) {
			int oldLevel = RendererColorDiagnosticSettings.getContrastLevel();
			int acceptedLevel = RendererColorDiagnosticSettings.setContrastLevel(level);
			if (oldLevel != acceptedLevel) {
				actions.rendererTuningChanged("contrast", acceptedLevel,
					RendererColorDiagnosticSettings.getContrastMultiplier());
			}
		} else if (settingIndex == GAMMA_SLIDER) {
			int oldLevel = RendererColorDiagnosticSettings.getGammaLevel();
			int acceptedLevel = RendererColorDiagnosticSettings.setGammaLevel(level);
			if (oldLevel != acceptedLevel) {
				actions.rendererTuningChanged("gamma", acceptedLevel,
					RendererColorDiagnosticSettings.getGammaValue());
			}
		} else if (settingIndex == SATURATION_SLIDER) {
			int oldLevel = RendererColorDiagnosticSettings.getSaturationLevel();
			int acceptedLevel = RendererColorDiagnosticSettings.setSaturationLevel(level);
			if (oldLevel != acceptedLevel) {
				actions.rendererTuningChanged("saturation", acceptedLevel,
					RendererColorDiagnosticSettings.getSaturationMultiplier());
			}
		}
	}

	private static String rendererTuningSliderBar(int level, int maxLevel) {
		StringBuilder bar = new StringBuilder("@whi@- [");
		for (int i = RendererReliefSettings.MIN_LEVEL; i <= maxLevel; i++) {
			bar.append(i == level ? "@gre@o@whi@" : "-");
		}
		return bar.append("] + @yel@[").append(level).append("]").toString();
	}

	private static String scalingTypeDescription(ScalingAlgorithm scalingType) {
		switch (scalingType) {
			case BILINEAR_INTERPOLATION:
				return "@yel@Bilinear";
			case BICUBIC_INTERPOLATION:
				return "@ora@Bicubic";
			case INTEGER_SCALING:
			default:
				return "@gre@Integer";
		}
	}

	private static void addTuningRows(List<Row> rows, String label, int level,
								  int maxLevel, int action) {
		addRow(rows, "@whi@" + label, SECTION_ROW);
		addRow(rows, rendererTuningSliderBar(level, maxLevel), action);
	}

	private static void addSection(List<Row> rows, String label) {
		addRow(rows, "@yel@" + label, SECTION_ROW);
	}

	private static void addRow(List<Row> rows, String label, int action) {
		rows.add(new Row(label, action));
	}
}
