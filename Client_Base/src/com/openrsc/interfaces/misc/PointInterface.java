package com.openrsc.interfaces.misc;

import orsc.Config;
import orsc.graphics.gui.Panel;
import orsc.graphics.two.GraphicsController;
import orsc.enumerations.InputXAction;
import orsc.graphics.gui.InputXPrompt;
import orsc.mudclient;

import java.util.Arrays;

public final class PointInterface {
	// Classic RSC style colours
	private static final int WINDOW_BG = 0x3A2A10;      // dark brown
	private static final int WINDOW_BG_SHADE = 0x2A1C08; // darker shade
	private static final int WINDOW_BORDER = 0x000000;   // black
	private static final int TITLE_BG = 0x4A3620;       // mid brown
	private static final int TITLE_TEXT = 0xFFFF00;     // yellow
	private static final int CONTENT_TEXT = 0xFFFFFF;   // white
	private static final int GRID_LINE = 0x000000;      // black lines
	private static final int BUTTON_BG = 0x333333;      // neutral
	private static final int BUTTON_HOVER_BG = 0x6580B7;// hover blue
	private static final int BUTTON_ACTIVE_BG = 0x871E1E;// active / checked red tone

	// Layout constants
	private static final int STAT_ROW_HEIGHT = 40;
	private static final int HEADER_HEIGHT = 29;
	private static final int TITLE_BAR_HEIGHT = 44; // enlarged to fit title + header labels
	private static final int PADDING_LEFT = 10;
	private static final int COL_LEVEL_X_OFFSET = 62; // separators relative positions
	private static final int COL_POINTS_X_OFFSET = 93;
	private static final int COL_TOTAL_EXP_X_OFFSET = 225;
	private static final int COL_LEVEL_MOD_X_OFFSET = 295;
	// Button columns
	private static final int COL_POINTS_MINUS_X = 170;
	private static final int COL_POINTS_PLUS_X = 200;
	private static final int COL_LEVEL_MINUS_X = 300;
	private static final int COL_LEVEL_PLUS_X = 325;

	private static final int ATTACK = 0, DEFENSE = 1, STRENGTH = 2, HITPOINTS = 3, HITS = 3, RANGED = 4, PRAYER = 5, MAGIC = 6;

	private static final int REDUCE_DEFENSE = 0, INCREASE_DEFENSE = 1, INCREASE_ATTACK = 2, INCREASE_STRENGTH = 3, INCREASE_RANGED = 4, INCREASE_PRAYER = 5, INCREASE_MAGIC = 6, REDUCE_ATTACK = 7, REDUCE_STRENGTH = 8, REDUCE_RANGED = 9, REDUCE_PRAYER = 10, REDUCE_MAGIC = 11;

	public Panel experienceConfig;
	public int experienceConfigScroll;
	public boolean selectSkillMenu = false;
	int width = 400, height = 325;
	private boolean visible = false;
	private mudclient mc;
	private int x, y;
	private int panelColour;
	private int textColour;
	private int bordColour;
	private int lineColour;
	private int pColour;

	// Arrays to drive rendering
	private static final int[] SKILL_IDS = {ATTACK, DEFENSE, STRENGTH, RANGED, PRAYER, MAGIC};
	private static final String[] SKILL_LABELS = {"Attack", "Defense", "Strength", "Ranged", "Prayer", "Magic"};
	private static final int[] OPTION_INC = {INCREASE_ATTACK, INCREASE_DEFENSE, INCREASE_STRENGTH, INCREASE_RANGED, INCREASE_PRAYER, INCREASE_MAGIC};
	private static final int[] OPTION_DEC = {REDUCE_ATTACK, REDUCE_DEFENSE, REDUCE_STRENGTH, REDUCE_RANGED, REDUCE_PRAYER, REDUCE_MAGIC};

	// Adjustable vertical offsets
	private static final int BUTTON_TEXT_EXTRA_OFFSET = 5; // baseline offset for symbols
	private static final int BUTTON_PLUS_ADDITIONAL_OFFSET = 4; // extra offset to lower '+' for visual centering
	private static final int BUTTON_FONT_HEIGHT_APPROX = 12; // approximate font height for button font
	private static final int FOOTER_FONT_HEIGHT_APPROX = 12; // estimated font height for font 2
	private static final int FOOTER_TEXT_BASELINE_ADJUST = -14; // raise footer text to center vertically

	public PointInterface(mudclient mc) {
		this.mc = mc;
		x = (mc.getGameWidth() - width) / 2;
		y = (mc.getGameHeight() - height) / 2;
		experienceConfig = new Panel(mc.getSurface(), 5);
		experienceConfigScroll = experienceConfig.addScrollingList(x + 95, y + 34, 160, height - 40, 20, 2, false);
	}

	public void reposition() {
		x = (mc.getGameWidth() - width) / 2;
		y = (mc.getGameHeight() - height) / 2;
		experienceConfig.reposition(experienceConfigScroll, x + 95, y + 34, 160, height - 40);
	}

	public void onRender(GraphicsController graphics) {
		reposition();
		drawExperienceConfig();
		if (selectSkillMenu) {
			drawSelectSkillMenu();
		}
	}

	private void drawExperienceConfig() {
		reposition();
		panelColour = WINDOW_BG;
		textColour = CONTENT_TEXT;
		bordColour = WINDOW_BORDER;
		lineColour = GRID_LINE;
		pColour = WINDOW_BG_SHADE;

		experienceConfig.handleMouse(mc.getMouseX(), mc.getMouseY(), mc.getMouseButtonDown(), mc.getLastMouseDown());

		// Window background & border
		mc.getSurface().drawBoxAlpha(x, y, width, height, pColour, 160);
		mc.getSurface().drawBoxBorder(x, width, y, height, bordColour);

		// Title bar (now taller to include two lines)
		mc.getSurface().drawBoxAlpha(x, y, width, TITLE_BAR_HEIGHT, TITLE_BG, 220);
		// Title line
		drawStringCentered("Skill Points Manager", x, y + 14, 2, TITLE_TEXT);

		// Header labels (second line within title bar) now centered per column
		int statColStart = x;
		int statColEnd = x + COL_LEVEL_X_OFFSET;
		int lvColStart = x + COL_LEVEL_X_OFFSET;
		int lvColEnd = x + COL_POINTS_X_OFFSET;
		int ptsColStart = x + COL_POINTS_X_OFFSET;
		int ptsColEnd = x + COL_TOTAL_EXP_X_OFFSET;
		int expColStart = x + COL_TOTAL_EXP_X_OFFSET;
		int expColEnd = x + COL_LEVEL_MOD_X_OFFSET;
		int modColStart = x + COL_LEVEL_MOD_X_OFFSET;
		int modColEnd = x + width;
		int headerLabelY = y + 30;
		drawHeaderCentered("Stat", statColStart, statColEnd, headerLabelY);
		drawHeaderCentered("Lv", lvColStart, lvColEnd, headerLabelY);
		drawHeaderCentered("Points to advance", ptsColStart, ptsColEnd, headerLabelY);
		drawHeaderCentered("Total Exp", expColStart, expColEnd, headerLabelY);
		drawHeaderCentered("+/- Levels", modColStart, modColEnd, headerLabelY);

		// Bottom line of title/header area
		mc.getSurface().drawLineHoriz(x, y + TITLE_BAR_HEIGHT, width, lineColour);

		// Removed vertical separator lines in header area for cleaner look
		// Clear scrolling list
		experienceConfig.clearList(experienceConfigScroll);

		// Adjusted starting Y for stat rows due to larger header area
		int rowStartOffset = TITLE_BAR_HEIGHT + 18; // was ~48 before enlargement

		// Draw stat rows
		for (int i = 0; i < SKILL_IDS.length; i++) {
			int skillId = SKILL_IDS[i];
			// Add final copies for inner classes
			final int idx = i;
			final int skillIdFinal = skillId;
			String label = SKILL_LABELS[i];
			int baseY = y + rowStartOffset + (i * STAT_ROW_HEIGHT);
			int pointsMinusY = baseY - 15; // align buttons just above text line
			int pointsPlusY = pointsMinusY;
			int levelsMinusY = pointsMinusY;
			int levelsPlusY = pointsMinusY;

			// Background strip for row (subtle shading)
			mc.getSurface().drawBoxAlpha(x + 1, baseY - 20, width - 2, STAT_ROW_HEIGHT - 4, (i % 2 == 0 ? WINDOW_BG : WINDOW_BG_SHADE), 90);

			// Data
			int expToNext = getExpToNextLevel(skillIdFinal);
			int level = mc.getPlayerStatBase(skillIdFinal);
			long totalExp = mc.getPlayerExperience(skillIdFinal);

			// Labels & values
			this.drawString(label + ":", x + PADDING_LEFT, baseY, 3, textColour);
			this.drawString("" + level, x + COL_LEVEL_X_OFFSET + 7, baseY, 3, textColour);
			this.drawString("" + expToNext, x + COL_POINTS_X_OFFSET + 10, baseY, 3, textColour);
			this.drawString("" + totalExp, x + COL_TOTAL_EXP_X_OFFSET + 6, baseY, 3, textColour);

			// Points +/- buttons
			drawButton(COL_POINTS_MINUS_X + x, pointsMinusY, 20, 20, "@red@-", 6, false, new ButtonHandler() {
				@Override void handle() {
					mc.setPointsSkillId(skillIdFinal);
					mc.setPointsOptionId(OPTION_DEC[idx]);
					mc.showItemModX(InputXPrompt.reducePointsX, InputXAction.REDUCEPOINTS_X, true);
				}
			});
			drawButton(COL_POINTS_PLUS_X + x, pointsPlusY, 20, 20, "@gre@+", 6, false, new ButtonHandler() {
				@Override void handle() {
					mc.setPointsSkillId(skillIdFinal);
					mc.setPointsOptionId(OPTION_INC[idx]);
					mc.showItemModX(InputXPrompt.incPointsX, InputXAction.INCPOINTS_X, true);
				}
			});

			// Level +/- buttons
			drawButton(COL_LEVEL_MINUS_X + x, levelsMinusY, 20, 20, "@red@-", 6, false, new ButtonHandler() {
				@Override void handle() {
					mc.setPointsSkillId(skillIdFinal);
					mc.setPointsOptionId(OPTION_DEC[idx]);
					mc.showItemModX(InputXPrompt.reduceLevelsX, InputXAction.REDUCELEVELS_X, true);
				}
			});
			drawButton(COL_LEVEL_PLUS_X + x, levelsPlusY, 20, 20, "@gre@+", 6, false, new ButtonHandler() {
				@Override void handle() {
					mc.setPointsSkillId(skillIdFinal);
					mc.setPointsOptionId(OPTION_INC[idx]);
					mc.showItemModX(InputXPrompt.incLevelsX, InputXAction.INCLEVELS_X, true);
				}
			});
		}

		int lastRowBaseY = y + rowStartOffset + (SKILL_IDS.length - 1) * STAT_ROW_HEIGHT;
		int bottomLineY = lastRowBaseY + (Config.S_WANT_OPENPK_PRESETS ? 11 : 14);
		mc.getSurface().drawLineHoriz(x, bottomLineY, width, lineColour);
		if (Config.S_WANT_OPENPK_PRESETS) {
			int secondLineY = bottomLineY + 34;
			mc.getSurface().drawLineHoriz(x, secondLineY, width, lineColour);
		}

		// Close button adjusted (still at top right)
		drawCloseButton(x + width - 32, y + 4, 30, 20, "X", 2, new ButtonHandler() {
			@Override void handle() { setVisible(false); }
		});

		if (Config.S_WANT_OPENPK_PRESETS) {
			int presetY = bottomLineY + 6;
			int presetWidth = 45;
			int presetHeight = 20; // slightly smaller to free space
			drawCloseButton(x + 265, presetY, 82, presetHeight, "Save Preset", 3, new ButtonHandler() {
				@Override void handle() { mc.showItemModX(InputXPrompt.savePreset, InputXAction.SAVEPRESET_X, true); }
			});
			String[] presetNums = {"1","2","3","4","5"};
			for (int i = 0; i < presetNums.length; i++) {
				final int presetIndex = 13 + (i + 1);
				int px = x + 5 + (i * 50);
				drawCloseButton(px, presetY, presetWidth, presetHeight, presetNums[i], 3, new ButtonHandler() {
					@Override void handle() {
						try {
							mc.packetHandler.getClientStream().newPacket(199);
							mc.packetHandler.getClientStream().bufferBits.putByte(13);
							mc.packetHandler.getClientStream().bufferBits.putByte(presetIndex);
							mc.packetHandler.getClientStream().finishPacket();
						} catch (NumberFormatException ex) {
							System.out.println("load preset number format exception: " + ex);
						}
					}
				});
			}
		}

		// Footer info (smaller font & dynamic position, centered segments)
		String footerA = "HP: " + mc.getPlayerStatBase(HITS);
		String footerB = "Combat Level: " + mc.getLocalPlayer().level;
		String footerC = "Points: " + mc.getPoints();
		int footerBarHeight = 30;
		int footerTop = y + height - footerBarHeight;
		int segmentWidth = (width / 3) - 20;
		// Center baseline: top + half bar + (fontHeight/2) plus manual adjust
		int footerBaseline = footerTop + (footerBarHeight / 2) + (FOOTER_FONT_HEIGHT_APPROX / 2) + FOOTER_TEXT_BASELINE_ADJUST;
		drawCenteredInSegment(footerA, x, segmentWidth, footerBaseline);
		drawCenteredInSegment(footerB, x + segmentWidth, segmentWidth, footerBaseline);
		drawCenteredInSegment(footerC, x + 2 * segmentWidth, width - 2 * segmentWidth, footerBaseline);

		if (selectSkillMenu) {
			mc.getSurface().drawBoxAlpha(x, y, width, height, 0, 192);
		}
	}

	private int getExpToNextLevel(int skillId) {
		int baseLevel = mc.getPlayerStatBase(skillId);
		if (baseLevel <= 0) return 0;
		int[] expArray = mc.getExperienceArray();
		int targetIndex = Math.max(0, baseLevel - 1);
		int nextLevelExp = expArray[targetIndex];
		return nextLevelExp - mc.getPlayerExperience(skillId);
	}

	private void drawHeaderLabel(String text, int x, int y) {
		this.drawString(text, x, y + 1, 2, TITLE_TEXT);
	}

	private void drawHeaderCentered(String text, int startX, int endX, int baselineY) {
		int w = mc.getSurface().stringWidth(2, text);
		int cx = startX + ((endX - startX) - w) / 2;
		this.drawString(text, cx, baselineY + 1, 2, TITLE_TEXT);
	}

	// Added helper used for footer alignment
	private void drawCenteredInSegment(String text, int segStart, int segWidth, int baselineY) {
		int w = mc.getSurface().stringWidth(2, text);
		int cx = segStart + (segWidth - w) / 2;
		this.drawString(text, cx, baselineY, 2, textColour);
	}

	private void drawSelectSkillMenu() {
		reposition();
		mc.getSurface().drawBoxAlpha(x + 90, y + 5, 166, height - 10, panelColour, 160);
		mc.getSurface().drawBoxBorder(x + 90, 166, y + 5, height - 10, bordColour);
		this.drawStringCentered("Select a skill to track", x - 12, y + 22, 3, TITLE_TEXT);
		mc.getSurface().drawLineHoriz(x + 90, y + 30, 166, lineColour);
		drawCloseButton(x + 237, y + 6, 18, 18, "X", 2, new ButtonHandler() {
			@Override void handle() {
				experienceConfig.resetScrollIndex(experienceConfigScroll);
				selectSkillMenu = false;
			}
		});
		String[] skillNames = mc.getSkillNamesLong();
		experienceConfig.clearList(experienceConfigScroll);
		for (int i = 0; i < mudclient.skillCount; i++) {
			experienceConfig.setListEntry(experienceConfigScroll, i, "@whi@" + skillNames[i], 0, (String) null, (String) null);
		}
		int index = experienceConfig.getControlSelectedListIndex(experienceConfigScroll);
		if (index >= 0 && mc.mouseButtonClick == 1) {
			mc.selectedSkill = index;
			experienceConfig.resetScrollIndex(experienceConfigScroll);
			selectSkillMenu = false;
			mc.setMouseClick(0);
		}
		experienceConfig.drawPanel();
		Config.C_EXPERIENCE_COUNTER_MODE = 2;
	}

	private void drawString(String str, int x, int y, int font, int color) {
		if (color == 0xFFFFFF) {
			mc.getSurface().drawShadowText(str, x, y, color, font, false);
		} else {
			mc.getSurface().drawString(str, x, y, color, font);
		}
	}

	private void drawStringCentered(String str, int x, int y, int font, int color) {
		int stringWid = mc.getSurface().stringWidth(font, str);
		drawString(str, x + (width / 2) - (stringWid / 2), y, font, color);
	}

	private void drawCloseButton(int x, int y, int width, int height, String text, int font, ButtonHandler handler) {
		int bgBtnColour = BUTTON_BG;
		if (mc.getMouseX() >= x && mc.getMouseY() >= y && mc.getMouseX() <= x + width && mc.getMouseY() <= y + height) {
			bgBtnColour = BUTTON_HOVER_BG;
			if (mc.getMouseClick() == 1) {
				handler.handle();
				mc.setMouseClick(0);
			}
		}
		mc.getSurface().drawBoxAlpha(x, y, width, height, bgBtnColour, 192);
		mc.getSurface().drawBoxBorder(x, width, y, height, 0x242424);
		int textY = y + (height - BUTTON_FONT_HEIGHT_APPROX) / 2 + BUTTON_FONT_HEIGHT_APPROX - 2; // refined centering
		mc.getSurface().drawString(text, x + (width / 2) - (mc.getSurface().stringWidth(font, text) / 2) - 1, textY, textColour, font);
	}

	private void drawButton(int bx, int by, int w, int h, String text, int font, boolean checked, ButtonHandler handler) {
		if (mc.inputX_Action != InputXAction.ACT_0 || mc.isInputXConsumeNextClick()) {
			mc.getSurface().drawBoxAlpha(bx, by, w, h, BUTTON_BG, 192);
			mc.getSurface().drawBoxBorder(bx, w, by, h, 0x242424);
			int baseTextY = by + (h - BUTTON_FONT_HEIGHT_APPROX) / 2 + BUTTON_FONT_HEIGHT_APPROX - 2;
			int textY = baseTextY;
			if (text.indexOf('+') >= 0) textY += BUTTON_PLUS_ADDITIONAL_OFFSET;
			mc.getSurface().drawString(text,
					bx + (w / 2) - (mc.getSurface().stringWidth(font, text) / 2) - 1,
					textY, textColour, font);
			return;
		}

		int bgBtnColour = BUTTON_BG;
		if (checked) bgBtnColour = BUTTON_ACTIVE_BG;

		boolean inside = mc.getMouseX() >= bx && mc.getMouseY() >= by &&
				mc.getMouseX() <= bx + w && mc.getMouseY() <= by + h && !selectSkillMenu;

		if (inside) {
			if (!checked) bgBtnColour = BUTTON_HOVER_BG;
			if (mc.getMouseClick() == 1) {
				handler.handle();
				mc.setMouseClick(0); // consume click
			}
		}

		mc.getSurface().drawBoxAlpha(bx, by, w, h, bgBtnColour, 192);
		mc.getSurface().drawBoxBorder(bx, w, by, h, 0x242424);

		int baseTextY = by + (h - BUTTON_FONT_HEIGHT_APPROX) / 2 + BUTTON_FONT_HEIGHT_APPROX - 2;
		int textY = baseTextY;
		if (text.indexOf('+') >= 0) {
			textY += BUTTON_PLUS_ADDITIONAL_OFFSET; // lower '+' slightly
		}
		mc.getSurface().drawString(text,
				bx + (w / 2) - (mc.getSurface().stringWidth(font, text) / 2) - 1,
				textY, textColour, font);
	}


	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}
}
