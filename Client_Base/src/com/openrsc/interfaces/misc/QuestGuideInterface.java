package com.openrsc.interfaces.misc;

import orsc.graphics.gui.Panel;
import orsc.graphics.two.GraphicsController;
import orsc.mudclient;

import java.util.ArrayList;


public final class QuestGuideInterface {
	private static final int VISIBLE_QUEST_ROWS = 17;
	public int questGuideScroll;
	public Panel questGuide;
	int width = 430;
	int height = 320;
	int autoHeight = 0;
	int index = 0;
	int trackY = 0;
	private boolean visible = false;
	private ArrayList<QuestItem> questItems;
	private mudclient mc;
	private int panelColour, textColour, bordColour, lineColour;
	private int x, y;

	public QuestGuideInterface(mudclient mc) {
		this.mc = mc;

		questGuide = new Panel(mc.getSurface(), 15);

		x = (mc.getGameWidth() - width) / 2;
		y = (mc.getGameHeight() - height) / 2;

		questItems = new ArrayList<QuestItem>();

		questGuideScroll = questGuide.addScrollingList(x + 4, y + 36, width - 5, height - 37, 1000, 2, false);
	}

	public void reposition() {
		x = (mc.getGameWidth() - width) / 2;
		y = (mc.getGameHeight() - height) / 2;

		questGuide.reposition(questGuideScroll, x + 4, y + 36, width - 5, height - 37);
	}

	public void onRender(GraphicsController graphics) {
		reposition();

		int x = (mc.getGameWidth() - width) / 2;
		int y = (mc.getGameHeight() - height) / 2;

		panelColour = 0x989898;
		textColour = 0xffffff;
		bordColour = 0x000000;
		lineColour = 0x000000;

		questGuide.handleMouse(mc.getMouseX(), mc.getMouseY(), mc.getMouseButtonDown(), mc.getLastMouseDown());

		if (autoHeight - y > 320) {
			mc.getSurface().drawBoxAlpha(x, y, width, height, panelColour, 90);
			mc.getSurface().drawBoxBorder(x, width, y, height, bordColour);
		} else {
			mc.getSurface().drawBoxAlpha(x, y, width, autoHeight - y, panelColour, 90);
			mc.getSurface().drawBoxBorder(x, width, y, autoHeight - y, bordColour);
		}
		drawStringCentered(mc.getQuestGuideChosen(), x, y + 28, 5, textColour);

		this.drawButton(x + 394, y + 6, 30, 30, "X", 5, false, new ButtonHandler() {
			@Override
			void handle() {
				questGuide.resetScrollIndex(questGuideScroll);
				setVisible(false);
			}
		});

		mc.getSurface().drawLineHoriz(x, y + 35, width, lineColour);

		questItems.clear();

		if (mc.getQuestGuideProgress() >= 0) {
			String questStart = (mc.getQuestGuideProgress() == 0
				? "I can start the quest by speaking to " : "Start contact: ")
				+ mc.getQuestGuideStartWho() + " " + mc.getQuestGuideStartWhere() + ".";
			customAdd(questStart, 2, textColour);

			customAdd("", 2, textColour);
			customAdd("Requirements: ", 2, textColour);

			for (String requirement : safeQuestGuideLines(mc.getQuestGuideRequirement())) {
				customAdd("  - " + requirement, 2, textColour);
			}
			if (mc.getQuestGuideProgress() > 0) {
				customAdd("", 2, textColour);
				customAdd("Detailed walkthrough unavailable.", 2, textColour);
			}
		} else {
			customAdd("Congratulations you have completed " + mc.getQuestGuideChosen() + ".", 2, textColour);
		}


		customAdd("", 2, textColour);
		customAdd("Rewards: ", 2, textColour);

		for (String reward : safeQuestGuideLines(mc.getQuestGuideReward())) {
			customAdd("  - " + reward, 2, textColour);
		}

		questGuide.clearList(questGuideScroll);

		int trailingRows = Math.max(0, (height - 37) / mc.getSurface().fontHeight(2) - VISIBLE_QUEST_ROWS);
		for (int i = 0; i < questItems.size() + trailingRows; i++) {
			questGuide.setListEntry(questGuideScroll, i, "", 0, (String) null, (String) null);
		}

		int listStartPoint = questGuide.getScrollPosition(questGuideScroll);
		int maximumStart = Math.max(0, questItems.size() - VISIBLE_QUEST_ROWS);
		if (listStartPoint > maximumStart) {
			questGuide.resetScrollIndex(questGuideScroll);
			questGuide.scrollMethodList(questGuideScroll, maximumStart);
			listStartPoint = maximumStart;
		}
		int listEndPoint = listStartPoint + VISIBLE_QUEST_ROWS - 1;

		trackY = y + 55;

		for (int i = 0; i < questItems.size(); i++) {
			if (i < listStartPoint || i > listEndPoint)
				continue;

			QuestItem curItem = questItems.get(i);

			drawString(curItem.getText(), x + 8, trackY, curItem.getFont(), curItem.getColor());

			trackY += 15;
		}

		drawString("Progress: " + asStringStage(mc.getQuestGuideProgress()), x + 8, trackY, 2, this.textColour);
		trackY += 15;

		autoHeight = trackY;

		questGuide.drawPanel();
	}

	public String asStringStage(int progress) {
		return progress == 0 ? "Not started" : progress < 0 ? "Completed" : "In progress";
	}

	public void customAdd(String text, int font, int color) {
		if (text == null) {
			text = "";
		}
		int textWidth = mc.getSurface().stringWidth(font, text);
		int maxTextWidth = width - 16;
		if (textWidth <= maxTextWidth || text.length() < 2) {
			questItems.add(new QuestItem(text, font, color));
			return;
		}

		int split = text.length();
		while (split > 0 && mc.getSurface().stringWidth(font, text.substring(0, split)) > maxTextWidth) {
			int previousSpace = text.lastIndexOf(" ", split - 1);
			if (previousSpace <= 0) {
				break;
			}
			split = previousSpace;
		}
		if (split <= 0 || split >= text.length()) {
			questItems.add(new QuestItem(text, font, color));
			return;
		}
		questItems.add(new QuestItem(text.substring(0, split), font, color));
		customAdd(text.substring(split).trim(), font, color);
	}

	private String[] safeQuestGuideLines(String[] lines) {
		if (lines == null || lines.length == 0) {
			return new String[] {"None"};
		}
		return lines;
	}

	public void drawString(String str, int x, int y, int font, int color) {
		mc.getSurface().drawShadowText(str, x, y, color, font, false);
	}

	public void drawStringCentered(String str, int x, int y, int font, int color) {
		int stringWid = mc.getSurface().stringWidth(font, str);
		drawString(str, x + (width / 2) - (stringWid / 2), y, font, color);
	}

	private void drawButton(int x, int y, int width, int height, String text, int font, boolean checked, ButtonHandler handler) {
		int bgBtnColour = 0x333333; // grey
		if (checked) {
			bgBtnColour = 16711680; // red
		}
		if (mc.getMouseX() >= x && mc.getMouseY() >= y && mc.getMouseX() <= x + width && mc.getMouseY() <= y + height) {
			if (!checked)
				bgBtnColour = 16711680; // blue
			if (mc.getMouseClick() == 1) {
				handler.handle();
				mc.setMouseClick(0);
			}
		}
		mc.getSurface().drawBoxAlpha(x, y, width, height, bgBtnColour, 192);
		mc.getSurface().drawBoxBorder(x, width, y, height, 0x242424);
		mc.getSurface().drawString(text, x + (width / 2) - (mc.getSurface().stringWidth(font, text) / 2) - 1, y + height / 2 + 5, textColour, font);
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}
}

class QuestItem {

	private String text;
	private int font, color;

	public QuestItem(String text, int font, int color) {
		this.text = text;
		this.font = font;
		this.color = color;
	}

	public String getText() {
		return text;
	}

	public int getFont() {
		return font;
	}

	public int getColor() {
		return color;
	}
}
