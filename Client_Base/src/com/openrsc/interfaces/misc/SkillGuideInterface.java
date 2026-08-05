package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ElementalSpellDisplayMetadata;
import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.entityhandling.defs.NPCDef;
import com.openrsc.client.entityhandling.defs.SpellDef;
import orsc.Config;
import orsc.graphics.gui.Panel;
import orsc.graphics.two.GraphicsController;
import orsc.mudclient;

import java.util.ArrayList;


public final class SkillGuideInterface {
	public int curTab = 0;
	public int skillGuideScroll;
	public int hiscoreScroll;
	public Panel skillGuide;
	int width = 430;
	int height = 320;
	int autoHeight = 0;
	// Different y values used for larger skill guides with more tabs
	boolean largeSkillGuide = false;
	private boolean hiscoresMode = false;
	private ArrayList<SkillMenuEntry> skillMenuEntries;
	private boolean visible = false;
	private mudclient mc;
	private int panelColour, textColour, bordColour;
	private int x, y;

	public SkillGuideInterface(mudclient mc) {
		this.mc = mc;

		skillGuide = new Panel(mc.getSurface(), 15);

		x = (mc.getGameWidth() - width) / 2;
		y = (mc.getGameHeight() - height) / 2;

		skillMenuEntries = new ArrayList<SkillMenuEntry>();

		skillGuideScroll = skillGuide.addScrollingList2(x + 4, y + 79, width - 5, height - 77, 100, 7, true);
		hiscoreScroll = skillGuide.addScrollingList2(x + 4, y + 97, width - 5, height - 99, HiscoreData.MAX_ENTRIES + 8, 7, true);
	}

	public void reposition() {
		x = (mc.getGameWidth() - width) / 2;
		y = (mc.getGameHeight() - height) / 2;

		skillGuide.reposition(skillGuideScroll, x + 4, y + 81, width - 5, height - 82);
		skillGuide.reposition(hiscoreScroll, x + 4, y + 97, width - 5, height - 99);
	}

	public void onRender(GraphicsController graphics) {
		reposition();
		int x = (mc.getGameWidth() - width) / 2;
		int y = (mc.getGameHeight() - height) / 2;

		panelColour = 0x989898;
		textColour = 0xffffff;
		bordColour = 0x000000;

		skillGuide.handleMouse(mc.getMouseX(), mc.getMouseY(), mc.getMouseButtonDown(), mc.getLastMouseDown());

		boolean hiscoresView = isHiscoresView();

		// Draws the background
		mc.getSurface().drawBoxAlpha(x, y, width, autoHeight - y, panelColour, 160);
		mc.getSurface().drawBoxBorder(x, width, y, autoHeight - y, bordColour);

		// Draws the title
		if (hiscoresView) {
			largeSkillGuide = false;
			String title = isOverallHiscores() ? "Overall Hiscores" : mc.getSkillGuideChosen() + " Hiscores";
			drawStringCentered(title, x, y + 28, 5, textColour);
		} else if (mc.skillGuideChosenTabs.size() <= 4) {
			largeSkillGuide = false;
			drawStringCentered(mc.getSkillGuideChosen(), x, y + 28, 5, textColour);
		} else {
			largeSkillGuide = true;
			drawStringCentered(mc.getSkillGuideChosen(), x, y + 20, 5, textColour);
		}

		this.drawButton(x + 394, y + 6, 30, 30, "X", 5, false, new ButtonHandler() {
			@Override
			void handle() {
				skillGuide.resetScrollIndex(skillGuideScroll);
				skillGuide.resetScrollIndex(hiscoreScroll);
				curTab = 0;
				hiscoresMode = false;
				mc.getHiscoreData().clear();
				setVisible(false);
			}
		});

		// Toggle between the guide and the hiscores for the chosen skill
		if (Config.S_WANT_HISCORES && !isOverallHiscores() && mc.getHiscoreGuideSkillId() >= 0) {
			this.drawButton(x + 6, y + 6, 84, 22, hiscoresView ? "Guide" : "Hiscores", 1, false, new ButtonHandler() {
				@Override
				void handle() {
					setHiscoresMode(!hiscoresMode);
				}
			});
		}

		if (!hiscoresView) {
			int tabDrawX = 0;
			int tabDrawY = 0;
			if (largeSkillGuide) {
				tabDrawX = 220 - (45 * 4);
				tabDrawY = 27;
			} else {
				tabDrawX = 220 - (45 * mc.skillGuideChosenTabs.size());
				tabDrawY = 45;
			}
			int tabDrawXDiff = 75;
			int tabDrawYDiff = 20;

			// Draws the tab pickers
			for (int i = 0; i < mc.skillGuideChosenTabs.size(); i++) {
				// Starts new row of tabs
				if (i == 4) {
					tabDrawY += 25;
					tabDrawX = 220 - (45 * (mc.skillGuideChosenTabs.size() - i));
				}
				this.drawTab(x + tabDrawX, y + tabDrawY, tabDrawXDiff, tabDrawYDiff, mc.skillGuideChosenTabs.get(i), 1);
				tabDrawX += tabDrawXDiff + 10;
			}
		}

		mc.getSurface().drawLineHoriz(x + 1, y + 81, width - 2, 0);
		mc.getSurface().drawBoxAlpha(x + 1, y + 82, width - 2, 16, 0x6580B7, 192);

		if (hiscoresView) {
			drawHiscoreHeaders(x, y);
			drawHiscoreList(x, y);
		} else {
			drawGuideHeaders(x, y);
			drawSkillItems();
		}
	}

	public boolean isHiscoresView() {
		return hiscoresMode || isOverallHiscores();
	}

	private boolean isOverallHiscores() {
		return "Overall".equalsIgnoreCase(mc.getSkillGuideChosen());
	}

	public void setHiscoresMode(boolean on) {
		hiscoresMode = on;
		skillGuide.resetScrollIndex(skillGuideScroll);
		skillGuide.resetScrollIndex(hiscoreScroll);
		skillGuide.clearList(hiscoreScroll);
		mc.getHiscoreData().clear();
	}

	public int activeScrollControl() {
		return isHiscoresView() ? hiscoreScroll : skillGuideScroll;
	}

	private void drawHiscoreHeaders(int x, int y) {
		boolean overall = isOverallHiscores();
		mc.getSurface().drawString("Rank", x + 5, y + 94, 0xffffff, 2);
		mc.getSurface().drawString("Name", x + 60, y + 94, 0xffffff, 2);
		mc.getSurface().drawString(overall ? "Total level" : "Level", x + 205, y + 94, 0xffffff, 2);
		mc.getSurface().drawString(overall ? "Total exp" : "Experience", x + 300, y + 94, 0xffffff, 2);
	}

	private void drawHiscoreList(int x, int y) {
		HiscoreData data = mc.getHiscoreData();
		int skillId = mc.getHiscoreGuideSkillId();
		if (skillId >= 0 && data.shouldRequest()) {
			data.markRequested(skillId);
			mc.sendHiscoreRequest(skillId);
		}

		// Keep the guide list empty so its scrollbar never draws in this mode
		skillGuide.clearList(skillGuideScroll);

		// Own rank strip, pinned under the column headers
		mc.getSurface().drawBoxAlpha(x + 1, y + 98, width - 2, 16, 0x6580B7, 128);
		if (data.isLoaded()) {
			String ownText = "Your rank: #" + formatNumber(data.getOwnRank())
				+ "      " + (isOverallHiscores() ? "Total level: " : "Level: ") + formatNumber(data.getOwnLevel())
				+ "      Exp: " + formatNumber(data.getOwnExp());
			drawString(ownText, x + 5, y + 110, 2, 0xFFFF00);
		} else {
			drawString("Fetching hiscores from the server...", x + 5, y + 110, 2, 0xFFFF00);
		}

		int count = data.isLoaded() ? Math.min(data.getCount(), HiscoreData.MAX_ENTRIES) : 0;

		// Rows are 34px (vs the guide's 37px) so the extra own-rank strip
		// still leaves the panel bottom within the 320px interface height.
		// count + 1 scrollbar entries make the clamp stop exactly at the
		// last full window of 6 rows (region height 221 / font-7 height 29 = 7).
		skillGuide.clearList(hiscoreScroll);
		for (int i = 0; i <= count; i++) {
			skillGuide.setListEntry(hiscoreScroll, i, "", 0, (String) null, (String) null);
		}

		int listStartPoint = skillGuide.getScrollPosition(hiscoreScroll);
		int listEndPoint = listStartPoint + 5;
		int allY = y + 98 + 16;

		if (!data.isLoaded()) {
			mc.getSurface().drawBoxAlpha(x, allY, width, 34, 0x45454545, 90);
			drawString("Loading...", x + 10, allY + 23, 2, textColour);
			allY += 34;
		} else if (count == 0) {
			mc.getSurface().drawBoxAlpha(x, allY, width, 34, 0x45454545, 90);
			drawString("No players are ranked yet.", x + 10, allY + 23, 2, textColour);
			allY += 34;
		} else {
			for (int i = 0; i < count; i++) {
				if (i < listStartPoint || i > listEndPoint)
					continue;

				mc.getSurface().drawBoxAlpha(x, allY, width, 34, 0x45454545, 90);
				int rowColour = (i == data.getOwnListIndex()) ? 0x00FF00 : textColour;
				drawString((i + 1) + ".", x + 10, allY + 23, 2, rowColour);
				drawString(data.getName(i), x + 60, allY + 23, 2, rowColour);
				drawString(formatNumber(data.getLevel(i)), x + 205, allY + 23, 2, rowColour);
				drawString(formatNumber(data.getExp(i)), x + 300, allY + 23, 2, rowColour);
				if (i != count - 1 && i != listEndPoint) {
					mc.getSurface().drawBoxBorder(x, width, allY, 35, 0);
				}

				allY += 34;
			}
		}
		autoHeight = allY;

		skillGuide.drawPanel();
	}

	private String formatNumber(long value) {
		return String.format("%,d", value);
	}

	private void drawGuideHeaders(int x, int y) {
		if (isMagicSpellsTab()) {
			mc.getSurface().drawString("Lvl", x + 5, y + 94, 0xffffff, 2);
			mc.getSurface().drawString("Name", x + 35, y + 94, 0xffffff, 2);
			mc.getSurface().drawString("Details", x + 140, y + 94, 0xffffff, 2);
			return;
		}
		if (isExpositoryTab()) {
			int headerX = isSummoningInfoTab() ? x + 85 : x + 5;
			mc.getSurface().drawString("Important information to know", headerX, y + 94, 0xffffff, 2);
			return;
		}
		mc.getSurface().drawString("Level", x + 5, y + 94, 0xffffff, 2);
		mc.getSurface().drawString("Advancement", x + 5 + 80, y + 94, 0xffffff, 2);
	}

	public void drawSkillItems() {
		int x = (mc.getGameWidth() - width) / 2;
		int y = (mc.getGameHeight() - height) / 2;

		// Gets all items in the list for what skill was chosen
		populateSkillItems();

		// Keep the hiscore list empty so its scrollbar never draws in this mode
		skillGuide.clearList(hiscoreScroll);

		// Sets up scroll
		skillGuide.clearList(skillGuideScroll);
		for (int i = -1; i <= skillMenuEntries.size(); i++) {
			skillGuide.setListEntry(skillGuideScroll, i + 1, "", 0, (String) null, (String) null);
		}

		int listStartPoint = skillGuide.getScrollPosition(skillGuideScroll);
		int listEndPoint = listStartPoint + 5;

		int levelX = x + 10;
		int spriteX = levelX + 15;
		int detailX = spriteX + 50;
		int allY = 0;
		allY = y + 82 + 16;

		for (int i = -1; i < skillMenuEntries.size(); i++) {
			if (i >= 100) {
				break;
			}

			if (i < listStartPoint || i > listEndPoint)
				continue;

			SkillMenuEntry curItem = skillMenuEntries.get(i);
			if (curItem instanceof MagicSpellMenuItem) {
				drawMagicSpellGuideItem((MagicSpellMenuItem) curItem, x, allY, i, listEndPoint);
				allY += 37;
				continue;
			}

			int gapHeight = (curItem instanceof SkillMenuItem) ? 37 : 37;

			mc.getSurface().drawBoxAlpha(detailX - 75, allY, width, gapHeight, 0x45454545, 90);
			if (!isExpositoryTab()) {
				drawString(curItem.getLevelReq(), levelX, allY + 25, 2, textColour);
			}

			int textX = isExpositoryTab() && !isSummoningInfoTab() ? x + 10 : detailX + 10;
			drawString(curItem.getSkillDetail(), textX, allY + 25, 2, textColour);

			//mc.getSurface().drawLineHoriz(detailX - 75, allY, width, 0);
			if (i != skillMenuEntries.size() - 1 && i != listEndPoint) {
				mc.getSurface().drawBoxBorder(detailX - 75, width, allY, gapHeight + 1, 0);
			}

			if (curItem instanceof SkillMenuItem && !isExpositoryTab()) {
				int itemId = ((SkillMenuItem) curItem).getItemID();
				ItemDef def = EntityHandler.getItemDef(itemId);
				if (def != null) {
					int drawWidth = 48;
					int drawHeight = 32;
					int drawX = spriteX + 5;
					int drawY = allY + 2;
					if (itemId == 2328) {
						drawWidth = 28;
						drawHeight = 22;
						drawX += 10;
						drawY += 5;
					} else if (itemId == 2329) {
						drawWidth = 38;
						drawHeight = 27;
						drawX += 5;
						drawY += 3;
					}
					mc.getSurface().drawSpriteClipping(mc.spriteSelect(def),
						drawX, drawY, drawWidth, drawHeight, def.getPictureMask(), 0,
						def.getBlueMask(), false, 0, 1);
				}
			} else if (curItem instanceof SkillMenuNPC) {
				int npcId = ((SkillMenuNPC) curItem).getNpcID();
				NPCDef def = EntityHandler.getNpcDef(npcId);
				if (def != null) {
					int height = 32;
					int width = def.getCamera2() > 0 ? (def.getCamera1() * height) / def.getCamera2() : height;
					width = Math.max(12, Math.min(48, width));
					if (npcId == 114 || npcId == 241) {
						height /= 2;
						width = Math.max(6, width / 2);
					}
					int drawX = spriteX + 5 + (48 - width) / 2;
					int drawY = allY + 2 + (32 - height) / 2;
					int animDir = isSummonGuideSideFacingNpc(npcId) ? 3 : 0;
					mc.drawNPCDef(def, drawX, drawY, width, height, animDir);
				}
			}

			allY += gapHeight;
		}
		autoHeight = allY;

		skillGuide.drawPanel();
	}

	private void drawMagicSpellGuideItem(MagicSpellMenuItem spellItem, int x, int allY, int rowIndex, int listEndPoint) {
		int gapHeight = 37;
		int levelX = x + 10;
		int nameX = x + 35;
		int detailX = x + 140;
		mc.getSurface().drawBoxAlpha(x, allY, width, gapHeight, 0x45454545, 90);
		drawString(spellItem.getLevelReq(), levelX, allY + 25, 2, textColour);
		drawString(spellItem.getSpellName(), nameX, allY + 25, 2, textColour);
		drawString(spellItem.getTooltip(), detailX, allY + 25, 2, textColour);
		if (rowIndex != skillMenuEntries.size() - 1 && rowIndex != listEndPoint) {
			mc.getSurface().drawBoxBorder(x, width, allY, gapHeight + 1, 0);
		}
	}

	private boolean isMagicSpellsTab() {
		return mc.getSkillGuideChosen().equals("Magic") && curTab == 0;
	}

	private boolean isInfoTab() {
		if (mc.skillGuideChosenTabs == null || curTab < 0 || curTab >= mc.skillGuideChosenTabs.size()) {
			return false;
		}
		String tab = mc.skillGuideChosenTabs.get(curTab);
		return tab.equals("Info") || tab.equals("Other");
	}

	private boolean isExpositoryTab() {
		if (mc.skillGuideChosenTabs == null || curTab < 0 || curTab >= mc.skillGuideChosenTabs.size()) {
			return false;
		}
		String tab = mc.skillGuideChosenTabs.get(curTab);
		return isInfoTab() || (mc.getSkillGuideChosen().equals("Worship") && tab.equals("Devotion"));
	}

	private boolean isSummoningInfoTab() {
		return mc.getSkillGuideChosen().equals("Summoning") && isInfoTab();
	}

	public void changeTab(int tabNum) {
		curTab = tabNum;
		skillGuide.resetScrollIndex(skillGuideScroll);
		drawSkillItems();
	}

	public void drawString(String str, int x, int y, int font, int color) {
		mc.getSurface().drawString(str, x, y, color, font);
	}

	public void drawStringCentered(String str, int x, int y, int font, int color) {
		int stringWid = mc.getSurface().stringWidth(font, str);
		mc.getSurface().drawShadowText(str, x + (width / 2) - (stringWid / 2) - 2, y, color, font, false);
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

	// Used for drawing tabs
	// Keeps track of current tab and tab hovered over
	private void drawTab(int x, int y, int width, int height, String text, int font) {
		int bgBtnColour = 0x333333; // grey
		boolean current = mc.skillGuideChosenTabs.get(curTab).equals(text);
		if (current) {
			bgBtnColour = 0x659CDE; // red
		} else if (mc.getMouseX() >= x && mc.getMouseY() >= y && mc.getMouseX() <= x + width && mc.getMouseY() <= y + height) {
			bgBtnColour = 0x6580B7; // blue
			if (mc.getMouseClick() == 1) {
				for (int i = 0; i < mc.skillGuideChosenTabs.size(); i++) {
					if (mc.skillGuideChosenTabs.get(i) == text) {
						changeTab(i);
					}
				}
				mc.setMouseClick(0);
			}
		}
		mc.getSurface().drawBoxAlpha(x, y, width, height, bgBtnColour, 192);
		mc.getSurface().drawBoxBorder(x, width, y, height, 0x242424);
		mc.getSurface().drawString(text, x + (width / 2) - (mc.getSurface().stringWidth(font, text) / 2), y + height / 2 + 5, textColour, font);
	}

	public void populateSkillItems() {
		skillMenuEntries.clear();
		if (mc.getSkillGuideChosen().equals("Melee") || mc.getSkillGuideChosen().equals("Attack")) {
			if (curTab == 0) {
				addMeleeTierGuide("Tin dagger", 1, 1995);
				addMeleeTierGuide("Copper dagger", 8, 2006);
				addMeleeTierGuide("Bronze dagger", 15, 62);
				addMeleeTierGuide("Iron dagger", 22, 28);
				addMeleeTierGuide("Steel dagger", 30, 63);
				addMeleeTierGuide("Mithril dagger", 38, 64);
				addMeleeTierGuide("Titan steel dagger", 46, 2017);
				addMeleeTierGuide("Adamantite dagger", 54, 65);
				addMeleeTierGuide("Orichalcum dagger", 62, 2028);
				addMeleeTierGuide("Rune dagger", 70, 396);
				addMeleeTierGuide("Dragon dagger", 80, 1447);
			} else if (curTab == 1) {
				addMeleeTierGuide("Tin short sword", 1, 1997);
				addMeleeTierGuide("Copper short sword", 8, 2008);
				addMeleeTierGuide("Bronze short sword", 15, 66);
				addMeleeTierGuide("Iron short sword", 22, 1);
				addMeleeTierGuide("Steel short sword", 30, 67);
				addMeleeTierGuide("Mithril short sword", 38, 68);
				addMeleeTierGuide("Titan steel short sword", 46, 2019);
				addMeleeTierGuide("Adamantite short sword", 54, 69);
				addMeleeTierGuide("Orichalcum short sword", 62, 2030);
				addMeleeTierGuide("Rune short sword", 70, 397);
				addMeleeTierGuide("Tin long sword", 1, 1998);
				addMeleeTierGuide("Copper long sword", 8, 2009);
				addMeleeTierGuide("Bronze long sword", 15, 70);
				addMeleeTierGuide("Iron long sword", 22, 71);
				addMeleeTierGuide("Steel long sword", 30, 72);
				addMeleeTierGuide("Mithril long sword", 38, 73);
				addMeleeTierGuide("Titan steel long sword", 46, 2020);
				addMeleeTierGuide("Adamantite long sword", 54, 74);
				addMeleeTierGuide("Orichalcum long sword", 62, 2031);
				addMeleeTierGuide("Rune long sword", 70, 75);
				addMeleeTierGuide("Dragon sword", 80, 593);
			} else if (curTab == 2) {
				addMeleeTierGuide("Tin scimitar", 1, 1999);
				addMeleeTierGuide("Copper scimitar", 8, 2010);
				addMeleeTierGuide("Bronze scimitar", 15, 82);
				addMeleeTierGuide("Iron scimitar", 22, 83);
				addMeleeTierGuide("Steel scimitar", 30, 84);
				addMeleeTierGuide("Mithril scimitar", 38, 85);
				addMeleeTierGuide("Titan steel scimitar", 46, 2021);
				addMeleeTierGuide("Adamantite scimitar", 54, 86);
				addMeleeTierGuide("Orichalcum scimitar", 62, 2032);
				addMeleeTierGuide("Rune scimitar", 70, 398);
			} else if (curTab == 3) {
				addMeleeTierGuide("Tin 2-handed sword", 1, 2000);
				addMeleeTierGuide("Copper 2-handed sword", 8, 2011);
				addMeleeTierGuide("Bronze 2-handed sword", 15, 76);
				addMeleeTierGuide("Iron 2-handed sword", 22, 77);
				addMeleeTierGuide("Steel 2-handed sword", 30, 78);
				addMeleeTierGuide("Mithril 2-handed sword", 38, 79);
				addMeleeTierGuide("Titan steel 2-handed sword", 46, 2022);
				addMeleeTierGuide("Adamantite 2-handed sword", 54, 80);
				addMeleeTierGuide("Orichalcum 2-handed sword", 62, 2033);
				addMeleeTierGuide("Rune 2-handed sword", 70, 81);
				addMeleeTierGuide("Dragon 2-handed sword", 80, 1346);
			} else if (curTab == 4) {
				addMeleeTierGuide("Tin battle axe", 1, 2002);
				addMeleeTierGuide("Copper battle axe", 8, 2013);
				addMeleeTierGuide("Bronze battle axe", 15, 205);
				addMeleeTierGuide("Iron battle axe", 22, 89);
				addMeleeTierGuide("Steel battle axe", 30, 90);
				addMeleeTierGuide("Mithril battle axe", 38, 91);
				addMeleeTierGuide("Titan steel battle axe", 46, 2024);
				addMeleeTierGuide("Adamantite battle axe", 54, 92);
				addMeleeTierGuide("Orichalcum battle axe", 62, 2035);
				addMeleeTierGuide("Rune battle axe", 70, 93);
				addMeleeTierGuide("Dragon battle axe", 80, 2752);
			} else if (curTab == 5) {
				addMeleeTierGuide("Tin mace", 1, 2003);
				addMeleeTierGuide("Copper mace", 8, 2014);
				addMeleeTierGuide("Bronze mace", 15, 94);
				addMeleeTierGuide("Iron mace", 22, 0);
				addMeleeTierGuide("Steel mace", 30, 95);
				addMeleeTierGuide("Mithril mace", 38, 96);
				addMeleeTierGuide("Titan steel mace", 46, 2025);
				addMeleeTierGuide("Adamantite mace", 54, 97);
				addMeleeTierGuide("Orichalcum mace", 62, 2036);
				addMeleeTierGuide("Rune mace", 70, 98);
			} else if (curTab == 6) {
				addMeleeTierGuide("Tin spear", 1, 2207);
				addMeleeTierGuide("Copper spear", 8, 2208);
				addMeleeTierGuide("Bronze spear", 15, 827);
				addMeleeTierGuide("Iron spear", 22, 1088);
				addMeleeTierGuide("Steel spear", 30, 1089);
				addMeleeTierGuide("Mithril spear", 38, 1090);
				addMeleeTierGuide("Titan steel spear", 46, 2209);
				addMeleeTierGuide("Adamantite spear", 54, 1091);
				addMeleeTierGuide("Orichalcum spear", 62, 2210);
				addMeleeTierGuide("Rune spear", 70, 1092);
			} else if (curTab == 7) {
				addMeleeTierGuide("Tin scythe", 1, 3181);
				addMeleeTierGuide("Copper scythe", 8, 3182);
				addMeleeTierGuide("Bronze scythe", 15, 3183);
				addMeleeTierGuide("Iron scythe", 22, 3184);
				addMeleeTierGuide("Steel scythe", 30, 3185);
				addMeleeTierGuide("Mithril scythe", 38, 3186);
				addMeleeTierGuide("Titan steel scythe", 46, 3187);
				addMeleeTierGuide("Adamantite scythe", 54, 3188);
				addMeleeTierGuide("Orichalcum scythe", 62, 3189);
				addMeleeTierGuide("Rune scythe", 70, 3190);
			} else if (curTab == 8) {
				skillMenuEntries.add(new SkillMenuEntry("", "Scythes cannot be used with a shield"));
				skillMenuEntries.add(new SkillMenuEntry("", "2-handers cannot be equipped with a shield"));
				skillMenuEntries.add(new SkillMenuEntry("", "Scythes hit an area around the player"));
				skillMenuEntries.add(new SkillMenuEntry("", "Maces give prayer point bonuses"));
				if (Config.S_WANT_CUSTOM_SPRITES) {
					addSkillCapeGuide(1374, "Melee");
				}
			}
		}
		if (mc.getSkillGuideChosen().equals("Defense")) {
			skillMenuEntries.add(new SkillMenuItem(404, "", "MyWorld folds old Defense progression into Melee"));
			skillMenuEntries.add(new SkillMenuItem(404, "", "Check the Melee guide for live weapon and armor progression"));
			skillMenuEntries.add(new SkillMenuItem(404, "", "Metal armor lowers ranged power from major slots"));
			skillMenuEntries.add(new SkillMenuItem(404, "", "Leather armor lowers magic power from five armor slots"));
			skillMenuEntries.add(new SkillMenuItem(404, "", "Cloth armor lowers melee power from major slots"));
		}
		if (mc.getSkillGuideChosen().equals("Strength")) {
			skillMenuEntries.add(new SkillMenuItem(90, "", "MyWorld folds old Strength progression into Melee"));
			skillMenuEntries.add(new SkillMenuItem(90, "", "Check the Melee guide for live weapon and armor progression"));
			skillMenuEntries.add(new SkillMenuItem(90, "", "Melee weapon power now drives the active melee damage model"));
		}
		if (mc.getSkillGuideChosen().equals("Hits")) {
			boolean harvesting = Config.S_WANT_HARVESTING && Config.S_WANT_CUSTOM_SPRITES;
			boolean customSprites = Config.S_WANT_CUSTOM_SPRITES;
			if (curTab == 0) { // Fish
				skillMenuEntries.add(new SkillMenuItem(350, "", "Shrimp - Heals 3"));
				skillMenuEntries.add(new SkillMenuItem(352, "", "Anchovies - Heals 1"));
				skillMenuEntries.add(new SkillMenuItem(355, "", "Sardine - Heals 4"));
				skillMenuEntries.add(new SkillMenuItem(362, "", "Herring - Heals 5"));
				skillMenuEntries.add(new SkillMenuItem(718, "", "Giant Carp - Heals 6"));
				skillMenuEntries.add(new SkillMenuItem(553, "", "Mackerel - Heals 6"));
				skillMenuEntries.add(new SkillMenuItem(359, "", "Trout - Heals 7"));
				skillMenuEntries.add(new SkillMenuItem(551, "", "Cod - Heals 7"));
				skillMenuEntries.add(new SkillMenuItem(364, "", "Pike - Heals 8"));
				skillMenuEntries.add(new SkillMenuItem(357, "", "Salmon - Heals 9"));
				skillMenuEntries.add(new SkillMenuItem(367, "", "Tuna - Heals 10"));
				skillMenuEntries.add(new SkillMenuItem(590, "", "Lava Eel - Heals 11"));
				skillMenuEntries.add(new SkillMenuItem(373, "", "Lobster - Heals 12"));
				skillMenuEntries.add(new SkillMenuItem(555, "", "Bass - Heals 13"));
				skillMenuEntries.add(new SkillMenuItem(370, "", "Swordfish - Heals 14"));
				skillMenuEntries.add(new SkillMenuItem(546, "", "Shark - Heals 20"));
				skillMenuEntries.add(new SkillMenuItem(1193, "", "Sea Turtle - Heals 20"));
				skillMenuEntries.add(new SkillMenuItem(1191, "", "Manta Ray - Heals 20"));
			} else if (curTab == 1) { // Pizzas
				skillMenuEntries.add(new SkillMenuItem(325, "", "Plain Pizza - Heals 11"));
				skillMenuEntries.add(new SkillMenuItem(326, "", "Meat Pizza - Heals 14 (7 per slice)"));
				skillMenuEntries.add(new SkillMenuItem(327, "", "Anchovie Pizza - Heals 16 (8 per slice)"));
				skillMenuEntries.add(new SkillMenuItem(750, "", "Pineapple Pizza - Heals 20 (10 per slice)"));
			} else if (curTab == 2) { // Pies
				skillMenuEntries.add(new SkillMenuItem(258, "", "Redberry Pie - Heals 6 (3 per slice)"));
				skillMenuEntries.add(new SkillMenuItem(259, "", "Meat Pie - Heals 8 (4 per slice)"));
				skillMenuEntries.add(new SkillMenuItem(257, "", "Apple Pie - Heals 10 (5 per slice)"));
				skillMenuEntries.add(new SkillMenuItem(1571, "", "Lily's Pumpkin Pie - Heals 12 (6 per slice)"));
				skillMenuEntries.add(new SkillMenuItem(1490, "", "Pumpkin Pie - Heals 24 (12 per slice)"));
				skillMenuEntries.add(new SkillMenuItem(1493, "", "White Pumpkin Pie - Heals 16 (8 per slice)"));
			} else if (curTab == 3) { // Produce
				skillMenuEntries.add(new SkillMenuItem(18, "", "Cabbage - Heals 1"));
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(1352, "", "Red Cabbage - Heals 1"));
				skillMenuEntries.add(new SkillMenuItem(765, "", "Dwellberries - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(320, "", "Tomato - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(249, "", "Banana - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(855, "", "Lemon - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(856, "", "Lemon Slices - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(860, "", "Diced Lemon - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(863, "", "Lime - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(865, "", "Lime Slices - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(864, "", "Lime Chunks - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(857, "", "Orange - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(858, "", "Orange Slices - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(859, "", "Diced Orange - Heals 2"));
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(1349, "", "Grapefruit - Heals 2"));
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(1359, "", "Grapefruit Slices - Heals 2"));
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(1360, "", "Diced Grapefruit - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(748, "", "Fresh Pineapple - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(749, "", "Pineapple Ring - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(862, "", "Pineapple Chunks - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(936, "", "Jangerberries - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(1245, "", "Edible Seaweed - Heals 4"));
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(1348, "", "Red Apple - Heals 4"));
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(1353, "", "Corn - Heals 6"));
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(1569, "", "Lily's Pumpkin - Heals 7"));
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(1350, "", "Papaya - Heals 8"));
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(1354, "", "White Pumpkin - Heals 10"));
			} else if (curTab == 4) { // Gnome Food
				skillMenuEntries.add(new SkillMenuItem(897, "", "King Worm - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(896, "", "Toad Legs - Heals 3"));
				skillMenuEntries.add(new SkillMenuItem(911, "", "Choc Crunchies - Heals 7"));
				skillMenuEntries.add(new SkillMenuItem(914, "", "Spice Crunchies - Heals 7"));
				skillMenuEntries.add(new SkillMenuItem(912, "", "Worm Crunchies - Heals 8"));
				skillMenuEntries.add(new SkillMenuItem(913, "", "Toad Crunchies - Heals 8"));
				skillMenuEntries.add(new SkillMenuItem(901, "", "Cheese and Tomato Batta - Heals 11"));
				skillMenuEntries.add(new SkillMenuItem(905, "", "Fruit Batta - Heals 11"));
				skillMenuEntries.add(new SkillMenuItem(902, "", "Toad Batta - Heals 11"));
				skillMenuEntries.add(new SkillMenuItem(906, "", "Veg Batta - Heals 11"));
				skillMenuEntries.add(new SkillMenuItem(904, "", "Worm Batta - Heals 11"));
				skillMenuEntries.add(new SkillMenuItem(908, "", "Vegball - Heals 12"));
				skillMenuEntries.add(new SkillMenuItem(909, "", "Worm Hole - Heals 12"));
				skillMenuEntries.add(new SkillMenuItem(907, "", "Chocolate Bomb - Heals 15"));
				skillMenuEntries.add(new SkillMenuItem(910, "", "Tangled Toads Legs - Heals 15"));
			} else if (curTab == 5) { // Drinks
				skillMenuEntries.add(new SkillMenuItem(193, "", "Beer - Heals 1"));
				skillMenuEntries.add(new SkillMenuItem(269, "", "Dwarven Stout - Heals 1"));
				skillMenuEntries.add(new SkillMenuItem(830, "", "Greenman's Ale - Heals 1"));
				skillMenuEntries.add(new SkillMenuItem(268, "", "Wizard's Mind Bomb - Heals 1"));
				skillMenuEntries.add(new SkillMenuItem(267, "", "Asgarnian Ale - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(739, "", "Cup of Tea - Heals 2-3"));
				skillMenuEntries.add(new SkillMenuItem(598, "", "Grog - Heals 3"));
				skillMenuEntries.add(new SkillMenuItem(870, "", "Gin - Heals 4"));
				skillMenuEntries.add(new SkillMenuItem(869, "", "Vodka - Heals 4"));
				skillMenuEntries.add(new SkillMenuItem(868, "", "Whisky - Heals 4"));
				skillMenuEntries.add(new SkillMenuItem(770, "", "Chocolaty Milk - Heals 4"));
				skillMenuEntries.add(new SkillMenuItem(876, "", "Brandy - Heals 5"));
				skillMenuEntries.add(new SkillMenuItem(877, "", "Blurberry Special - Heals 5"));
				skillMenuEntries.add(new SkillMenuItem(875, "", "Chocolate Saturday - Heals 5"));
				skillMenuEntries.add(new SkillMenuItem(872, "", "Drunk Dragon - Heals 5"));
				skillMenuEntries.add(new SkillMenuItem(874, "", "SGG - Heals 5"));
				skillMenuEntries.add(new SkillMenuItem(878, "", "Wizard Blizzard - Heals 5"));
				skillMenuEntries.add(new SkillMenuItem(866, "", "Fruit Blast - Heals 8"));
				skillMenuEntries.add(new SkillMenuItem(879, "", "Pineapple Punch - Heals 9"));
				skillMenuEntries.add(new SkillMenuItem(142, "", "Wine - Heals 11"));
				skillMenuEntries.add(new SkillMenuItem(737, "", "Poison Chalice - ???"));
			} else if (curTab == 6) { // Other
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(1459, "", "Sweetened Slices - Heals 1 or 2"));
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(1460, "", "Sweetened Chunks - Heals 1 or 2"));
				skillMenuEntries.add(new SkillMenuItem(319, "", "Cheese - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(179, "", "Spinach Roll - Heals 2"));
				if (customSprites) skillMenuEntries.add(new SkillMenuItem(1417, "", "Pizza Bagel - Heals 2"));
				skillMenuEntries.add(new SkillMenuItem(132, "", "Cooked Meat - Heals 3"));
				skillMenuEntries.add(new SkillMenuItem(1103, "", "Cooked Ugthanki Meat - Heals 3"));
				skillMenuEntries.add(new SkillMenuItem(337, "", "Chocolate Bar - Heals 3"));
				skillMenuEntries.add(new SkillMenuItem(138, "", "Bread - Heals 4"));
				skillMenuEntries.add(new SkillMenuItem(1269, "", "Oomlie Meat Parcel - Heals 8"));
				skillMenuEntries.add(new SkillMenuItem(346, "", "Stew - Heals 9"));
				skillMenuEntries.add(new SkillMenuItem(677, "", "Easter Egg - Heals 14"));
				skillMenuEntries.add(new SkillMenuItem(330, "", "Cake - Heals 12 (4 per slice)"));
				skillMenuEntries.add(new SkillMenuItem(422, "", "Pumpkin - Heals 14"));
				skillMenuEntries.add(new SkillMenuItem(332, "", "Chocolate Cake - Heals 15 (5 per slice)"));
				skillMenuEntries.add(new SkillMenuItem(709, "", "Curry - Heals 19"));
				skillMenuEntries.add(new SkillMenuItem(1102, "", "Tasty Ugthanki Kebab - Heals 19"));
				skillMenuEntries.add(new SkillMenuItem(210, "", "Kebab - Variable heal or effect"));
				skillMenuEntries.add(new SkillMenuItem(923, "", "Ugthanki Kebab - Heals 19"));
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(1463, "", "Seaweed Soup - Heals 26"));
				if (Config.S_WANT_CUSTOM_SPRITES) {
					addSkillCapeGuide(1522, "Hits");
				}
			}
		}
		if (mc.getSkillGuideChosen().equals("Ranged")) {
			populateRangedGuide();
		}
		if (mc.getSkillGuideChosen().equals("Worship")) {
			populatePrayerGuide();
		}
		if (mc.getSkillGuideChosen().equals("Magic")) {
			if (curTab == 0) {
				addMagicSpellGuideEntries();
			} else if (curTab == 1) {
				addEnchantedStaffTierGuide();
				skillMenuEntries.add(new SkillMenuItem(1000, "50", "Staff of Iban"));
				skillMenuEntries.add(new SkillMenuItem(1218, "80", "Staff of Saradomin"));
				skillMenuEntries.add(new SkillMenuItem(1216, "80", "Staff of Zamorak"));
				skillMenuEntries.add(new SkillMenuItem(1217, "80", "Staff of Guthix"));
				skillMenuEntries.add(new SkillMenuItem(3249, "80", "Staff of Elements"));
				skillMenuEntries.add(new SkillMenuItem(3250, "80", "Staff of Power"));
				skillMenuEntries.add(new SkillMenuItem(3251, "80", "Staff of Enlightenment"));
				addSkillCapeGuide(1382, "Magic");
			} else if (curTab == 2) {
				skillMenuEntries.add(new SkillMenuItem(185, "", "Magic Guild can be entered at level 66"));
				skillMenuEntries.add(new SkillMenuItem(35, "", "Combat spells can be toggled for auto-cast"));
				skillMenuEntries.add(new SkillMenuItem(36, "", "Heal spells have their own cooldown"));
				skillMenuEntries.add(new SkillMenuItem(42, "", "Teleport spells charge before moving you"));
				skillMenuEntries.add(new SkillMenuItem(33, "", "Unsteady lowers average damage for 5 attacks"));
				skillMenuEntries.add(new SkillMenuItem(32, "", "Dampen lowers max damage for 5 attacks"));
				skillMenuEntries.add(new SkillMenuItem(34, "", "Slow lowers attack speed for 5 attacks"));
				skillMenuEntries.add(new SkillMenuItem(31, "", "Scorch lowers defense for 5 attacks"));
				skillMenuEntries.add(new SkillMenuItem(41, "", "Withering combines all elemental debuffs"));
				skillMenuEntries.add(new SkillMenuItem(701, "", "Spells using mind runes will do damage equal to chaos runes"));
				skillMenuEntries.add(new SkillMenuItem(31, "", "Thunder spells can Startle and negate the next attack"));
				skillMenuEntries.add(new SkillMenuItem(34, "", "Acid spells can Corrode and apply poison"));
				skillMenuEntries.add(new SkillMenuItem(33, "", "Ice spells can Frostbite and reflect damage"));
				skillMenuEntries.add(new SkillMenuItem(32, "", "Wood spells can Splinter to hit another NPC"));
			}
		}
		if (mc.getSkillGuideChosen().equals("Cooking")) {
			boolean harvesting = Config.S_WANT_HARVESTING && Config.S_WANT_CUSTOM_SPRITES;
			if (curTab == 0) {
				skillMenuEntries.add(new SkillMenuItem(132, "1", "Cooked Meat"));
				skillMenuEntries.add(new SkillMenuItem(350, "1", "Shrimp"));
				skillMenuEntries.add(new SkillMenuItem(352, "1", "Anchovies"));
				skillMenuEntries.add(new SkillMenuItem(355, "4", "Sardine"));
				skillMenuEntries.add(new SkillMenuItem(362, "8", "Herring"));
				skillMenuEntries.add(new SkillMenuItem(718, "12", "Giant Carp"));
				skillMenuEntries.add(new SkillMenuItem(553, "12", "Mackerel"));
				skillMenuEntries.add(new SkillMenuItem(359, "16", "Trout"));
				skillMenuEntries.add(new SkillMenuItem(551, "20", "Cod"));
				skillMenuEntries.add(new SkillMenuItem(364, "20", "Pike"));
				skillMenuEntries.add(new SkillMenuItem(357, "24", "Salmon"));
				skillMenuEntries.add(new SkillMenuItem(367, "28", "Tuna"));
				skillMenuEntries.add(new SkillMenuItem(373, "36", "Lobster"));
				skillMenuEntries.add(new SkillMenuItem(555, "40", "Bass"));
				skillMenuEntries.add(new SkillMenuItem(370, "44", "Swordfish"));
				skillMenuEntries.add(new SkillMenuItem(590, "52", "Lava Eel"));
				skillMenuEntries.add(new SkillMenuItem(546, "64", "Shark"));
				skillMenuEntries.add(new SkillMenuItem(1193, "69", "Sea Turtle"));
				skillMenuEntries.add(new SkillMenuItem(1191, "70", "Manta Ray"));
			} else if (curTab == 1) {
				skillMenuEntries.add(new SkillMenuItem(325, "32", "Plain Pizza"));
				skillMenuEntries.add(new SkillMenuItem(326, "45", "Meat Pizza"));
				skillMenuEntries.add(new SkillMenuItem(327, "55", "Anchovie Pizza"));
				skillMenuEntries.add(new SkillMenuItem(750, "65", "Pineapple Pizza"));
			} else if (curTab == 2) {
				skillMenuEntries.add(new SkillMenuItem(258, "10", "Redberry Pie"));
				skillMenuEntries.add(new SkillMenuItem(259, "20", "Meat Pie"));
				skillMenuEntries.add(new SkillMenuItem(257, "28", "Apple Pie"));
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(1571, "36", "Lily's Pumpkin Pie"));
				if (Config.S_WANT_CUSTOM_SPRITES) skillMenuEntries.add(new SkillMenuItem(1490, "68", "Pumpkin Pie"));
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(1493, "68", "White Pumpkin Pie"));
			} else if (curTab == 3) {
				skillMenuEntries.add(new SkillMenuItem(346, "24", "Stew"));
				skillMenuEntries.add(new SkillMenuItem(709, "60", "Curry"));
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(1463, "67", "Seaweed Soup"));
			} else if (curTab == 4) {
				skillMenuEntries.add(new SkillMenuItem(138, "1", "Bread"));
				skillMenuEntries.add(new SkillMenuItem(1105, "56", "Pitta Bread"));
			} else if (curTab == 5) {
				skillMenuEntries.add(new SkillMenuItem(330, "36", "Cake"));
				skillMenuEntries.add(new SkillMenuItem(332, "50", "Chocolate Cake"));
			} else if (curTab == 6) {
				skillMenuEntries.add(new SkillMenuItem(833, "1", "Cocktails"));
				skillMenuEntries.add(new SkillMenuItem(911, "15", "Choc Crunchies"));
				skillMenuEntries.add(new SkillMenuItem(912, "15", "Worm Crunchies"));
				skillMenuEntries.add(new SkillMenuItem(913, "15", "Toad Crunchies"));
				skillMenuEntries.add(new SkillMenuItem(914, "15", "Spice Crunchies"));
				skillMenuEntries.add(new SkillMenuItem(901, "25", "Cheese and Tomato Batta"));
				skillMenuEntries.add(new SkillMenuItem(902, "25", "Toad Batta"));
				skillMenuEntries.add(new SkillMenuItem(904, "25", "Worm Batta"));
				skillMenuEntries.add(new SkillMenuItem(905, "25", "Fruit Batta"));
				skillMenuEntries.add(new SkillMenuItem(906, "25", "Veg Batta"));
				skillMenuEntries.add(new SkillMenuItem(907, "30", "Chocolate Bomb"));
				skillMenuEntries.add(new SkillMenuItem(908, "30", "Vegball"));
				skillMenuEntries.add(new SkillMenuItem(909, "30", "Worm Hole"));
				skillMenuEntries.add(new SkillMenuItem(910, "30", "Tangled Toads Legs"));
			} else if (curTab == 7) {
				skillMenuEntries.add(new SkillMenuItem(192, "", "Cooking Guild can be entered at level 32 with a chef's hat"));
				skillMenuEntries.add(new SkillMenuItem(142, "35", "Wine"));
				skillMenuEntries.add(new SkillMenuItem(1269, "48", "Oomlie Meat Parcel"));
				skillMenuEntries.add(new SkillMenuItem(1102, "58", "Tasty Ugthanki Kebab"));
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(501, "70", "Wine of Zamorak"));
				if (harvesting) skillMenuEntries.add(new SkillMenuItem(1467, "70", "Wine of Saradomin"));
				if (Config.S_WANT_CUSTOM_SPRITES) {
					addSkillCapeGuide(1373, "Cooking");
				}
			}
		}
		if (mc.getSkillGuideChosen().equals("Woodcutting")) {
			if (curTab == 0) {
				skillMenuEntries.add(new SkillMenuItem(14, "1", "Trees"));
				skillMenuEntries.add(new SkillMenuItem(2111, "8", "Pine trees"));
				skillMenuEntries.add(new SkillMenuItem(632, "15", "Oak trees"));
				skillMenuEntries.add(new SkillMenuItem(633, "22", "Willow trees"));
				skillMenuEntries.add(new SkillMenuItem(2112, "30", "Palm trees"));
				skillMenuEntries.add(new SkillMenuItem(634, "38", "Maple trees"));
				skillMenuEntries.add(new SkillMenuItem(635, "46", "Yew trees"));
				skillMenuEntries.add(new SkillMenuItem(2113, "54", "Ebony trees"));
				skillMenuEntries.add(new SkillMenuItem(636, "62", "Magic trees"));
				skillMenuEntries.add(new SkillMenuItem(2114, "70", "Blood trees"));
			} else if (curTab == 1) {
				skillMenuEntries.add(new SkillMenuItem(2001, "1", "Tin hatchet"));
				skillMenuEntries.add(new SkillMenuItem(2012, "8", "Copper hatchet"));
				skillMenuEntries.add(new SkillMenuItem(87, "15", "Bronze hatchet"));
				skillMenuEntries.add(new SkillMenuItem(12, "22", "Iron hatchet"));
				skillMenuEntries.add(new SkillMenuItem(88, "30", "Steel hatchet"));
				skillMenuEntries.add(new SkillMenuItem(203, "38", "Mithril hatchet"));
				skillMenuEntries.add(new SkillMenuItem(2023, "46", "Titan steel hatchet"));
				skillMenuEntries.add(new SkillMenuItem(204, "54", "Adamantite hatchet"));
				skillMenuEntries.add(new SkillMenuItem(2034, "62", "Orichalcum hatchet"));
				skillMenuEntries.add(new SkillMenuItem(405, "70", "Rune hatchet"));
			} else if (curTab == 2) {
				if (Config.S_WANT_WOODCUTTING_GUILD) {
					skillMenuEntries.add(new SkillMenuItem(405, "", "Woodcutting Guild can be entered at level 55 after paying 1000 coins"));
				}
				skillMenuEntries.add(new SkillMenuItem(405, "", "Hatchet must be equipped to chop trees"));
				skillMenuEntries.add(new SkillMenuItem(2707, "", "Resource seeds can appear as side rewards"));
				skillMenuEntries.add(new SkillMenuItem(2707, "", "Seed focus: No seeds, A few, More, or Even more"));
				skillMenuEntries.add(new SkillMenuItem(2707, "", "Seeds can roll up to hatchet tier plus 2"));
				skillMenuEntries.add(new SkillMenuItem(2707, "", "Above-tier seeds roll at reduced weight"));
				skillMenuEntries.add(new SkillMenuItem(2708, "", "Knowledge and Money seeds scale to your level"));
				if (Config.S_WANT_CUSTOM_SPRITES) {
					addSkillCapeGuide(1525, "Woodcutting");
				}
			}
		}
		if (mc.getSkillGuideChosen().equals("Fletching")) {
			skillMenuEntries.add(new SkillMenuItem(189, "", "MyWorld folds active bow, arrow, dart, and staff shaping into Crafting"));
			skillMenuEntries.add(new SkillMenuItem(189, "", "Check the Crafting guide for the live production path"));
		}
		if (mc.getSkillGuideChosen().equals("Fishing")) {
			if (curTab == 0) {
				skillMenuEntries.add(new SkillMenuItem(349, "1", "Shrimp - Fishing Rod (T1)"));
				skillMenuEntries.add(new SkillMenuItem(354, "1", "Sardine - Fishing Rod (T1)"));
				skillMenuEntries.add(new SkillMenuItem(361, "8", "Herring - Pine Fishing Rod (T2)"));
				skillMenuEntries.add(new SkillMenuItem(552, "15", "Mackerel - Oak Fishing Rod (T3)"));
				skillMenuEntries.add(new SkillMenuItem(351, "15", "Anchovies - Oak Fishing Rod (T3)"));
				skillMenuEntries.add(new SkillMenuItem(358, "22", "Trout - Willow Fishing Rod (T4)"));
				skillMenuEntries.add(new SkillMenuItem(550, "22", "Cod - Willow Fishing Rod (T4)"));
				skillMenuEntries.add(new SkillMenuItem(363, "30", "Pike - Palm Fishing Rod (T5)"));
				skillMenuEntries.add(new SkillMenuItem(356, "30", "Salmon - Palm Fishing Rod (T5)"));
				skillMenuEntries.add(new SkillMenuItem(366, "38", "Tuna - Maple Fishing Rod (T6)"));
				skillMenuEntries.add(new SkillMenuItem(372, "46", "Lobster - Yew Fishing Rod (T7)"));
				skillMenuEntries.add(new SkillMenuItem(369, "54", "Swordfish - Ebony Fishing Rod (T8)"));
				skillMenuEntries.add(new SkillMenuItem(554, "54", "Bass - Ebony Fishing Rod (T8)"));
				skillMenuEntries.add(new SkillMenuItem(591, "62", "Lava Eel - Magic Fishing Rod (T9)"));
				skillMenuEntries.add(new SkillMenuItem(545, "70", "Shark - Blood Fishing Rod (T10)"));
			} else if (curTab == 1) {
				skillMenuEntries.add(new SkillMenuItem(377, "1", "Fishing Rod - T1, +0 effective Fishing"));
				skillMenuEntries.add(new SkillMenuItem(2682, "8", "Pine Fishing Rod - T2, +5 effective Fishing"));
				skillMenuEntries.add(new SkillMenuItem(2683, "15", "Oak Fishing Rod - T3, +10 effective Fishing"));
				skillMenuEntries.add(new SkillMenuItem(2684, "22", "Willow Fishing Rod - T4, +15 effective Fishing"));
				skillMenuEntries.add(new SkillMenuItem(2685, "30", "Palm Fishing Rod - T5, +20 effective Fishing"));
				skillMenuEntries.add(new SkillMenuItem(2686, "38", "Maple Fishing Rod - T6, +25 effective Fishing"));
				skillMenuEntries.add(new SkillMenuItem(2687, "46", "Yew Fishing Rod - T7, +30 effective Fishing"));
				skillMenuEntries.add(new SkillMenuItem(2688, "54", "Ebony Fishing Rod - T8, +35 effective Fishing"));
				skillMenuEntries.add(new SkillMenuItem(2689, "62", "Magic Fishing Rod - T9, +40 effective Fishing"));
				skillMenuEntries.add(new SkillMenuItem(2690, "70", "Blood Fishing Rod - T10, +45 effective Fishing"));
			} else if (curTab == 2) {
				skillMenuEntries.add(new SkillMenuItem(379, "", "Fishing Guild can be entered at level 68"));
				skillMenuEntries.add(new SkillMenuItem(377, "", "Fishing shops sell rods through T6"));
				skillMenuEntries.add(new SkillMenuItem(2686, "", "Fishing Guild sells rods through T10"));
				skillMenuEntries.add(new SkillMenuItem(377, "", "Rods catch their tier and 3 tiers below"));
				skillMenuEntries.add(new SkillMenuItem(793, "", "Side rewards: oyster, seaweed, casket"));
				skillMenuEntries.add(new SkillMenuItem(1837, "", "Side rewards: leather gloves and boots by rod tier"));
				skillMenuEntries.add(new SkillMenuItem(793, "", "Loot focus: Just the fish, A little, Plenty, Lots"));
				skillMenuEntries.add(new SkillMenuItem(1837, "", "Side loot can roll up to rod tier plus 2"));
				skillMenuEntries.add(new SkillMenuItem(1837, "", "Above-tier side loot rolls at reduced weight"));
				skillMenuEntries.add(new SkillMenuItem(589, "", "Fishing Contest and Dragon Slayer keep quest exceptions"));
				if (Config.S_WANT_CUSTOM_SPRITES) {
					addSkillCapeGuide(1380, "Fishing");
				}
			}
		}
		if (mc.getSkillGuideChosen().equals("Crafting")) {
			populateCraftingGuide();
		}
		if (mc.getSkillGuideChosen().equals("Smithing")) {
			populateSmithingGuide();
		}
		if (mc.getSkillGuideChosen().equals("Mining")) {
			populateMiningGuide();
		}
		if (mc.getSkillGuideChosen().equals("Herblaw")) {
			if (curTab == 0) {
				skillMenuEntries.add(new SkillMenuItem(444, "3", EntityHandler.getItemDef(444).name));
				skillMenuEntries.add(new SkillMenuItem(445, "8", EntityHandler.getItemDef(445).name));
				skillMenuEntries.add(new SkillMenuItem(446, "15", EntityHandler.getItemDef(446).name));
				skillMenuEntries.add(new SkillMenuItem(447, "22", EntityHandler.getItemDef(447).name));
				skillMenuEntries.add(new SkillMenuItem(448, "29", EntityHandler.getItemDef(448).name));
				skillMenuEntries.add(new SkillMenuItem(449, "36", EntityHandler.getItemDef(449).name));
				skillMenuEntries.add(new SkillMenuItem(450, "43", EntityHandler.getItemDef(450).name));
				skillMenuEntries.add(new SkillMenuItem(451, "50", EntityHandler.getItemDef(451).name));
				skillMenuEntries.add(new SkillMenuItem(452, "57", EntityHandler.getItemDef(452).name));
				skillMenuEntries.add(new SkillMenuItem(453, "64", EntityHandler.getItemDef(453).name));
				skillMenuEntries.add(new SkillMenuItem(934, "70", EntityHandler.getItemDef(934).name));
			} else if (curTab == 1) {
				addHerblawPotionFamilyGuide(474, 477, 480, 483, 486, 3198, "Potion of Brawn",
					new String[] {"fern leaf", "mushroom", "fungus", "limpwurt root", "red flower", "blue flower"});
				addHerblawPotionFamilyGuide(489, 492, 495, 498, 566, 3201, "Potion of Deftness",
					new String[] {"10 low quality fish oil", "10 fair quality fish oil", "10 good quality fish oil",
						"10 fine quality fish oil", "10 high quality fish oil", "10 superior quality fish oil"});
				addHerblawPotionFamilyGuide(569, 963, 1411, 1414, 1468, 3204, "Potion of Insight",
					new String[] {"eye of newt", "spider eye", "zombie eye", "bat eye", "baby dragon's eye", "demon eye"});
				skillMenuEntries.add(new SkillMenuItem(1474, "8", "Antidote - Marrentill & red spiders' eggs"));
				skillMenuEntries.add(new SkillMenuItem(1176, "10", "Explosive compound - Nitro & nitrate & charcoal & a. root"));
				skillMenuEntries.add(new SkillMenuItem(1053, "18", "Ogre potion - Guam leaf, jangerberries, ground bat bones"));
				skillMenuEntries.add(new SkillMenuItem(1471, "22", "Stat restore - Harralander & ground unicorn horn"));
				skillMenuEntries.add(new SkillMenuItem(572, "23", "Weapon poison - Harralander & ground blue dragon scale"));
				skillMenuEntries.add(new SkillMenuItem(588, "25", "Blamish oil - Harralander & blamish snail slime"));
				skillMenuEntries.add(new SkillMenuItem(1253, "45", "Gujuo potion - Snake weed & ardrigal"));
				skillMenuEntries.add(new SkillMenuItem(1477, "45", "Skiller's Brew - Irit leaf & snape grass"));
				skillMenuEntries.add(new SkillMenuItem(3192, "45", "Warrior's Brew - Irit leaf & white berries"));
				skillMenuEntries.add(new SkillMenuItem(221, "72", "Strong Skiller's Brew - Dwarf weed & 5 snape grass"));
				skillMenuEntries.add(new SkillMenuItem(3195, "72", "Strong Warrior's Brew - Dwarf weed & 5 white berries"));
			} else if (curTab == 2) {
				skillMenuEntries.add(new SkillMenuItem(474, "", "Brawn boosts Melee, Hits, Mining, Smithing, Woodcutting"));
				skillMenuEntries.add(new SkillMenuItem(489, "", "Deftness boosts Ranged, Agility, Fishing, Crafting, Pickpocketing"));
				skillMenuEntries.add(new SkillMenuItem(569, "", "Insight boosts Magic, Enchanting, Summoning, Cooking, Worship"));
				skillMenuEntries.add(new SkillMenuItem(474, "", "Tiered potions go v1 to v6: 5%, 8%, 11%, 14%, 17%, 20%"));
				skillMenuEntries.add(new SkillMenuItem(474, "", "Tiered potions last 5, 8, 11, 14, 17, or 20 minutes"));
				skillMenuEntries.add(new SkillMenuItem(1477, "", "Skiller's Brew gives non-combat XP: 20% for 30 minutes"));
				skillMenuEntries.add(new SkillMenuItem(221, "", "Strong Skiller's Brew gives non-combat XP: 40% for 60 minutes"));
				skillMenuEntries.add(new SkillMenuItem(3192, "", "Warrior's Brew gives combat XP: 20% for 30 minutes"));
				skillMenuEntries.add(new SkillMenuItem(3195, "", "Strong Warrior's Brew gives combat XP: 40% for 60 minutes"));
				skillMenuEntries.add(new SkillMenuItem(1474, "", "Antidote cures poison and grants poison immunity"));
				skillMenuEntries.add(new SkillMenuItem(1471, "", "Stat restore restores reduced stats and blocks stat reduction"));
				if (Config.S_WANT_CUSTOM_SPRITES) {
					addSkillCapeGuide(1521, "Herblaw");
				}
			}
		}
		if (mc.getSkillGuideChosen().equals("Agility")) {
			if (curTab == 0) {
				skillMenuEntries.add(new SkillMenuItem(2328, "1", "Gnome Stronghold Course - Stronghold Chest per lap"));
				skillMenuEntries.add(new SkillMenuItem(981, "1", "Gnomeball minigame"));
				skillMenuEntries.add(new SkillMenuItem(2329, "35", "Barbarian Outpost Course - Outpost Chest per lap"));
				skillMenuEntries.add(new SkillMenuItem(2330, "52", "Wilderness Course - Wilderness Chest per lap"));
			} else if (curTab == 1) {
				skillMenuEntries.add(new SkillMenuItem(410, "5", "Falador West enter handholds"));
				skillMenuEntries.add(new SkillMenuItem(410, "10", "Brimhaven treeswing"));
				skillMenuEntries.add(new SkillMenuItem(410, "15", "Edgeville dungeon ropeswing"));
				skillMenuEntries.add(new SkillMenuItem(410, "15", "Yanille North climbing rocks"));
				skillMenuEntries.add(new SkillMenuItem(410, "18", "Yanille watchtower handholds"));
				skillMenuEntries.add(new SkillMenuItem(410, "20", "North-west of McGruber's Woods log balance"));
				skillMenuEntries.add(new SkillMenuItem(410, "25", "Lum river stepping stone"));
				skillMenuEntries.add(new SkillMenuItem(410, "25", "Glough's watch tower"));
				skillMenuEntries.add(new SkillMenuItem(410, "30", "Southern Gu'Tanoth bridge rock"));
				skillMenuEntries.add(new SkillMenuItem(410, "30", "West of Yanille tree swing"));
				skillMenuEntries.add(new SkillMenuItem(410, "32", "Ardougne river rock crossing"));
				skillMenuEntries.add(new SkillMenuItem(410, "32", "Cairn Isle rock climb"));
				skillMenuEntries.add(new SkillMenuItem(410, "32", "Southeastern Karamja stepping stones"));
				skillMenuEntries.add(new SkillMenuItem(410, "32", "Ah Za Roon temple pile of rubble"));
				skillMenuEntries.add(new SkillMenuItem(410, "32", "Tomb of Bervirius entrance"));
				skillMenuEntries.add(new SkillMenuItem(410, "32", "East Karamjan River log balance"));
				skillMenuEntries.add(new SkillMenuItem(410, "35", "Barbarian outpost handholds"));
				skillMenuEntries.add(new SkillMenuItem(410, "40", "Yanille Agility Dungeon ledge"));
				skillMenuEntries.add(new SkillMenuItem(410, "40", "Falador West exit handholds"));
				skillMenuEntries.add(new SkillMenuItem(410, "45", "White Wolf Mountain vine climb"));
				skillMenuEntries.add(new SkillMenuItem(410, "49", "Yanille Agility Dungeon pipe"));
				skillMenuEntries.add(new SkillMenuItem(410, "50", "Kharazi Jungle cave entrance"));
				skillMenuEntries.add(new SkillMenuItem(410, "50", "Taverly stepping stones to Catherby"));
				skillMenuEntries.add(new SkillMenuItem(410, "55", "Entrana wall rubble"));
				skillMenuEntries.add(new SkillMenuItem(410, "57", "Yanille Agility Dungeon rope swing"));
				skillMenuEntries.add(new SkillMenuItem(410, "67", "Yanille Agility Dungeon pile of rubble"));
				skillMenuEntries.add(new SkillMenuItem(410, "67", "Lava Maze stepping stones"));
				skillMenuEntries.add(new SkillMenuItem(410, "70", "Taverly Dungeon pipe crawl"));
				skillMenuEntries.add(new SkillMenuItem(410, "85", "Karamja river stepping stone"));
			} else if (curTab == 2) {
				skillMenuEntries.add(new SkillMenuItem(2328, "", "Chests are earned for completing obstacle courses"));
				skillMenuEntries.add(new SkillMenuItem(2329, "", "Chests reward the player with various resources"));
				if (Config.S_WANT_CUSTOM_SPRITES) {
					addSkillCapeGuide(1518, "Agility");
				}
			}
		}
		if (mc.getSkillGuideChosen().equals("Thieving")) {
			if (curTab == 0) {
				skillMenuEntries.add(new SkillMenuNPC(11, "1", "Man"));
				skillMenuEntries.add(new SkillMenuNPC(63, "10", "Farmer"));
				skillMenuEntries.add(new SkillMenuNPC(86, "25", "Warrior"));
				skillMenuEntries.add(new SkillMenuNPC(722, "25", "Workman"));
				skillMenuEntries.add(new SkillMenuNPC(342, "32", "Rogue"));
				skillMenuEntries.add(new SkillMenuNPC(65, "40", "Guard"));
				skillMenuEntries.add(new SkillMenuNPC(322, "55", "Knight"));
				skillMenuEntries.add(new SkillMenuNPC(574, "65", "Watchman"));
				skillMenuEntries.add(new SkillMenuNPC(323, "70", "Paladin"));
				skillMenuEntries.add(new SkillMenuNPC(582, "75", "Gnome"));
				skillMenuEntries.add(new SkillMenuNPC(324, "80", "Hero"));
			} else if (curTab == 1) {
				skillMenuEntries.add(new SkillMenuItem(739, "5", "Tea Stall"));
				skillMenuEntries.add(new SkillMenuItem(330, "5", "Baker's Stall"));
				skillMenuEntries.add(new SkillMenuItem(1061, "15", "Rock Cake Stall"));
				skillMenuEntries.add(new SkillMenuItem(200, "20", "Silk Stall"));
				skillMenuEntries.add(new SkillMenuItem(146, "35", "Fur Stall"));
				skillMenuEntries.add(new SkillMenuItem(383, "50", "Silver Stall"));
				skillMenuEntries.add(new SkillMenuItem(707, "65", "Spice Stall"));
				skillMenuEntries.add(new SkillMenuItem(157, "75", "Gem Stall"));
			} else if (curTab == 2) {
				skillMenuEntries.add(new SkillMenuItem(10, "13", "Ardougne Market house, Pirate's Hideout, Axe Hut"));
				skillMenuEntries.add(new SkillMenuItem(40, "28", "Ardougne Market house, Pirate's Hideout"));
				skillMenuEntries.add(new SkillMenuItem(10, "43", "Ardougne Market house, Axe Hut"));
				skillMenuEntries.add(new SkillMenuItem(671, "47", "Hemenster"));
				skillMenuEntries.add(new SkillMenuItem(619, "59", "Ardougne Chaos Druid Tower"));
				skillMenuEntries.add(new SkillMenuItem(545, "72", "Ardougne Castle"));
			} else if (curTab == 3) {
				skillMenuEntries.add(new SkillMenuItem(10, "7", "Ardougne Market house"));
				skillMenuEntries.add(new SkillMenuItem(40, "16", "Ardougne Market house"));
				skillMenuEntries.add(new SkillMenuItem(705, "16", "Ardougne Market house, Handelmort's mansion"));
				skillMenuEntries.add(new SkillMenuItem(155, "31", "Ardougne sewer mine"));
				skillMenuEntries.add(new SkillMenuItem(90, "32", "Axe Hut"));
				skillMenuEntries.add(new SkillMenuItem(262, "39", "Pirate's Hideout"));
				skillMenuEntries.add(new SkillMenuItem(444, "46", "Ardougne Chaos Druid Tower"));
				skillMenuEntries.add(new SkillMenuItem(545, "61", "Ardougne Castle upstairs"));
				skillMenuEntries.add(new SkillMenuItem(714, "82", "Yanille Agility Dungeon"));
			} else if (curTab == 4) {
				if (Config.S_WANT_CUSTOM_SPRITES) {
					addSkillCapeGuide(1375, "Thieving");
				}
			}
		}
		if (mc.getSkillGuideChosen().equalsIgnoreCase("Enchanting")
			|| mc.getSkillGuideChosen().equalsIgnoreCase("Magecraft")
			|| mc.getSkillGuideChosen().equalsIgnoreCase("Runecraft")) {
			if (curTab == 0) {
				addStaffTierGuide();
			} else if (curTab == 1) {
				addRobeTierGuide();
			} else if (curTab == 2) {
				addJewelryTierGuide();
			} else if (curTab == 3) {
				addRuneTierGuide();
			} else if (curTab == 4) {
				skillMenuEntries.add(new SkillMenuItem(33, "", "Rune output improves every 10 levels past req"));
				skillMenuEntries.add(new SkillMenuItem(1299, "", "Stone is mined from regular rocks"));
				skillMenuEntries.add(new SkillMenuItem(1299, "", "Stone is used on overworld altars to craft runes"));
				skillMenuEntries.add(new SkillMenuItem(1299, "", "Talismans and tiaras have been retired"));
				skillMenuEntries.add(new SkillMenuItem(619, "", "Every altar offers a unique effect"));
				skillMenuEntries.add(new SkillMenuItem(100, "", "Unenchanted staves work at every altar"));
				skillMenuEntries.add(new SkillMenuItem(2051, "", "Unenchanted robes and hats work at every altar"));
				skillMenuEntries.add(new SkillMenuItem(302, "", "Unenchanted jewelry works at every altar"));
				skillMenuEntries.add(new SkillMenuItem(2051, "", "Each cloth piece preserves 10% matching rune"));
				skillMenuEntries.add(new SkillMenuItem(101, "", "Matching enchanted staff preserves 50%"));
				skillMenuEntries.add(new SkillMenuItem(101, "", "Matching cloth and staff can preserve 100%"));
				skillMenuEntries.add(new SkillMenuItem(284, "", "Jewelry requires both gem and altar levels"));
				skillMenuEntries.add(new SkillMenuItem(544, "", "Examine jewelry for details on their effects"));
				addSkillCapeGuide(1527, "Enchanting");
			}
		}
		if (mc.getSkillGuideChosen().equalsIgnoreCase("Harvest")) {
			if (curTab == 0) {
				skillMenuEntries.add(new SkillMenuItem(29, "1", "Grain - T1 shears"));
				skillMenuEntries.add(new SkillMenuItem(348, "1", "Potato - T1 shears"));
				skillMenuEntries.add(new SkillMenuItem(241, "1", "Onion - T1 shears"));
				skillMenuEntries.add(new SkillMenuItem(18, "1", "Cabbage - T1 shears"));
				skillMenuEntries.add(new SkillMenuItem(218, "8", "Garlic - T2 shears"));
				skillMenuEntries.add(new SkillMenuItem(675, "15", "Flax - T3 shears"));
				skillMenuEntries.add(new SkillMenuItem(320, "15", "Tomato - T3 shears"));
				skillMenuEntries.add(new SkillMenuItem(1353, "22", "Corn - T4 shears"));
				skillMenuEntries.add(new SkillMenuItem(1569, "1", "Lily's Pumpkin - T1 shears"));
				skillMenuEntries.add(new SkillMenuItem(422, "1", "Event Pumpkin - T1 shears, bonus XP"));
				skillMenuEntries.add(new SkillMenuItem(1352, "30", "Red Cabbage - T5 shears"));
				skillMenuEntries.add(new SkillMenuItem(1354, "46", "White Pumpkin - T7 shears"));
				skillMenuEntries.add(new SkillMenuItem(1456, "46", "Sugar Cane - T7 shears"));
			} else if (curTab == 1) {
				skillMenuEntries.add(new SkillMenuItem(855, "15", "Lemon Tree - T3 shears"));
				skillMenuEntries.add(new SkillMenuItem(863, "22", "Lime Tree - T4 shears"));
				skillMenuEntries.add(new SkillMenuItem(1348, "30", "Apple Tree - T5 shears"));
				skillMenuEntries.add(new SkillMenuItem(249, "30", "Banana Palm - T5 shears"));
				skillMenuEntries.add(new SkillMenuItem(143, "38", "Grape Vine - T6 shears"));
				skillMenuEntries.add(new SkillMenuItem(857, "38", "Orange Tree - T6 shears"));
				skillMenuEntries.add(new SkillMenuItem(861, "54", "Pineapple Plant - T8 shears"));
				skillMenuEntries.add(new SkillMenuItem(1349, "54", "Grapefruit Tree - T8 shears"));
				skillMenuEntries.add(new SkillMenuItem(1350, "54", "Papaya Palm - T8 shears"));
				skillMenuEntries.add(new SkillMenuItem(1351, "62", "Coconut Palm - T9 shears"));
				skillMenuEntries.add(new SkillMenuItem(1457, "70", "Dragonfruit Tree - T10 shears"));
				skillMenuEntries.add(new SkillMenuItem(1465, "70", "Bless/Curse Grapes"));
			} else if (curTab == 2) {
				skillMenuEntries.add(new SkillMenuItem(236, "8", "Redberry Bush - T2 shears"));
				skillMenuEntries.add(new SkillMenuItem(55, "22", "Cadavaberry Bush - T4 shears"));
				skillMenuEntries.add(new SkillMenuItem(765, "38", "Dwellberry Bush - T6 shears"));
				skillMenuEntries.add(new SkillMenuItem(936, "46", "Jangerberry Bush - T7 shears"));
				skillMenuEntries.add(new SkillMenuItem(471, "62", "Whiteberry Bush - T9 shears"));
			} else if (curTab == 3) {
				skillMenuEntries.add(new SkillMenuItem(165, "1", "Guam herb chance - T1 shears"));
				skillMenuEntries.add(new SkillMenuItem(435, "8", "Marrentill chance - T2 shears"));
				skillMenuEntries.add(new SkillMenuItem(436, "15", "Tarromin chance - T3 shears"));
				skillMenuEntries.add(new SkillMenuItem(622, "22", "Sea weed - T4 shears"));
				skillMenuEntries.add(new SkillMenuItem(437, "22", "Harralander chance - T4 shears"));
				skillMenuEntries.add(new SkillMenuItem(438, "30", "Ranarr Weed chance - T5 shears"));
				skillMenuEntries.add(new SkillMenuItem(220, "38", "Limpwurt Root - T6 shears"));
				skillMenuEntries.add(new SkillMenuItem(439, "38", "Irit Leaf chance - T6 shears"));
				skillMenuEntries.add(new SkillMenuItem(440, "46", "Avantoe chance - T7 shears"));
				skillMenuEntries.add(new SkillMenuItem(441, "54", "Kwuarm chance - T8 shears"));
				skillMenuEntries.add(new SkillMenuItem(469, "54", "Snape Grass - T8 shears"));
				skillMenuEntries.add(new SkillMenuItem(442, "54", "Cadantine chance - T8 shears"));
				skillMenuEntries.add(new SkillMenuItem(443, "70", "Dwarf Weed chance - T10 shears"));
				skillMenuEntries.add(new SkillMenuItem(933, "70", "Torstol rare chance - T10 shears"));
			} else if (curTab == 4) {
				skillMenuEntries.add(new SkillMenuItem(3256, "1", "Fern leaf - T1 shears"));
				skillMenuEntries.add(new SkillMenuItem(3257, "8", "Mushroom - T2 shears"));
				skillMenuEntries.add(new SkillMenuItem(3258, "30", "Fungus - T5 shears"));
				skillMenuEntries.add(new SkillMenuItem(220, "38", "Limpwurt Root - T6 shears"));
				skillMenuEntries.add(new SkillMenuItem(3259, "54", "Red flower - T8 shears"));
				skillMenuEntries.add(new SkillMenuItem(3260, "70", "Blue flower - T10 shears"));
			} else if (curTab == 5) {
				skillMenuEntries.add(new SkillMenuItem(144, "1", "Tin shears - T1, equipped tool"));
				skillMenuEntries.add(new SkillMenuItem(2215, "8", "Copper shears - T2, equipped tool"));
				skillMenuEntries.add(new SkillMenuItem(2216, "15", "Bronze shears - T3, equipped tool"));
				skillMenuEntries.add(new SkillMenuItem(2217, "22", "Iron shears - T4, equipped tool"));
				skillMenuEntries.add(new SkillMenuItem(2218, "30", "Steel shears - T5, equipped tool"));
				skillMenuEntries.add(new SkillMenuItem(2219, "38", "Mithril shears - T6, equipped tool"));
				skillMenuEntries.add(new SkillMenuItem(2220, "46", "Titan steel shears - T7, equipped tool"));
				skillMenuEntries.add(new SkillMenuItem(2221, "54", "Adamantite shears - T8, equipped tool"));
				skillMenuEntries.add(new SkillMenuItem(2222, "62", "Orichalcum shears - T9, equipped tool"));
				skillMenuEntries.add(new SkillMenuItem(2223, "70", "Rune shears - T10, equipped tool"));
			} else if (curTab == 6) {
				if (Config.S_WANT_CUSTOM_SPRITES) {
					addSkillCapeGuide(1526, "Harvesting");
				}
				skillMenuEntries.add(new SkillMenuItem(144, "", "Harvestables unlock by equipped shears tier"));
				skillMenuEntries.add(new SkillMenuItem(144, "", "Harvesting level gates which shears you can use"));
				skillMenuEntries.add(new SkillMenuItem(439, "", "Herb rolls use shears tier and 3 tiers below"));
				skillMenuEntries.add(new SkillMenuItem(2709, "", "Resource seeds can appear as side rewards"));
				skillMenuEntries.add(new SkillMenuItem(2709, "", "Seed focus: No seeds, A few, More, or Even more"));
				skillMenuEntries.add(new SkillMenuItem(2709, "", "Seeds can roll up to shears tier plus 2"));
				skillMenuEntries.add(new SkillMenuItem(2709, "", "Above-tier seeds roll at reduced weight"));
				skillMenuEntries.add(new SkillMenuItem(2708, "", "Knowledge and Money seeds scale to your level"));
			}
		}
		if (mc.getSkillGuideChosen().equalsIgnoreCase("Summoning")) {
			populateSummoningGuide();
		}
		if (mc.getSkillGuideChosen().equalsIgnoreCase("Blessing")) {
			populateBlessingGuide();
		}
	}

	private void populateBlessingGuide() {
		skillMenuEntries.add(new SkillMenuItem(3293, "", "Blessing follows the standard level 1-99 experience curve"));
		skillMenuEntries.add(new SkillMenuItem(3293, "", "Altar conversion awards Blessing experience"));
		skillMenuEntries.add(new SkillMenuItem(3293, "", "Carving sigils awards Crafting experience"));
		skillMenuEntries.add(new SkillMenuItem(3293, "", "Production does not award Worship experience"));
	}

	private void populateSummoningGuide() {
		if (curTab == 0) {
			addSummonGuide(23, "1", "Broodling Spider - Combat; 1 life, 1 body");
			addSummonGuide(114, "7", "Mischief Imp - Support; 1 life, 1 body, ashes");
			addSummonGuide(838, "12", "Loot Goblin - Support; bones, life, body, mind");
			addSummonGuide(8, "14", "Ironhide Bear - Combat; 1 life, 2 body, bones");
			addSummonGuide(0, "20", "Sacred Unicorn - Support; 1 life, body, cosmic, bones");
			addSummonGuide(43, "26", "Duskwind Bat - Combat; 1 life, air, body, nature, bat bones");
			addSummonGuide(241, "33", "Pack Rat - Utility; 1 life, 2 law, body, nature");
			addSummonGuide(295, "39", "Bound Battleaxe - Combat; 1 life, 2 cosmic, iron battle axe");
			addSummonGuide(296, "45", "Mourning Unicorn - Support; 1 life, body, cosmic, bones");
			addSummonGuide(53, "51", "Restless Shade - Combat; 2 life, 3 cosmic, soul, ashes");
			addSummonGuide(748, "55", "Guard Dog - Support; 2 life, 2 body, soul, bones");
			addSummonGuide(13, "58", "Delivery Camel - Utility; 1 life, 2 body, 2 law, 2 nature");
			addSummonGuide(298, "64", "Astral Wraith - Combat; 2 life, 4 cosmic, soul, bones");
			addSummonGuide(184, "70", "Abyssal Demon - Combat; 3 life, blood, soul, demon ash");
		} else if (curTab == 1) {
			addSummonGuide(23, "1", "Broodling Spider - Hits 2 +1/10; dmg 1 +1/24");
			addSummonGuide(114, "7", "Mischief Imp - Does not engage in combat");
			addSummonGuide(838, "12", "Loot Goblin - Does not engage in combat");
			addSummonGuide(8, "14", "Ironhide Bear - Hits 14 +1/8; dmg 2 +1/30");
			addSummonGuide(0, "20", "Sacred Unicorn - Does not engage in combat");
			addSummonGuide(43, "26", "Duskwind Bat - Hits 7 +1/9; dmg 3 +1/18");
			addSummonGuide(241, "33", "Pack Rat - Does not engage in combat");
			addSummonGuide(295, "39", "Bound Battleaxe - Hits 8 +1/10; dmg 6 +1/16");
			addSummonGuide(296, "45", "Mourning Unicorn - Does not engage in combat");
			addSummonGuide(53, "51", "Restless Shade - Hits 9 +1/9; dmg 4 +1/18");
			addSummonGuide(748, "55", "Guard Dog - Does not engage in combat");
			addSummonGuide(13, "58", "Delivery Camel - Does not engage in combat");
			addSummonGuide(298, "64", "Astral Wraith - Hits 10 +1/8; dmg 7 +1/14");
			addSummonGuide(184, "70", "Abyssal Demon - Hits 18 +1/7; dmg 9 +1/12");
		} else if (curTab == 2) {
			addSummonGuide(23, "1", "None - introductory combat summon");
			addSummonGuide(114, "7", "Mischievous - prevents enemy aggro until you attack");
			addSummonGuide(838, "12", "Scavenger - collects dropped stackable items");
			addSummonGuide(8, "14", "Tank - absorbs 60% of incoming damage");
			addSummonGuide(0, "20", "Divine - grants +10 prayer points");
			addSummonGuide(43, "26", "Vampirism - heals owner for damage dealt");
			addSummonGuide(241, "33", "Hoarder - 4 uses; certs matching selected items");
			addSummonGuide(295, "39", "Relentless - 15% chance for bonus damage");
			addSummonGuide(296, "45", "Reverent - double XP and +50% Devotion from auto-offerings");
			addSummonGuide(53, "51", "Fear - 20% chance to stop enemy attacks");
			addSummonGuide(748, "55", "Watchful - one enemy; suppresses area attacks");
			addSummonGuide(13, "58", "Beast of Burden - 2 uses; banks one stack");
			addSummonGuide(298, "64", "Spell Echo - 15% chance for magic bonus damage");
			addSummonGuide(184, "70", "Hellfire - 10% chance for Hell's Fire damage");
		} else if (curTab == 3) {
			addSummonGuide(23, "", "Combat summons stay until death or dismissal");
			addSummonGuide(8, "", "Combat summons absorb only their listed share");
			addSummonGuide(0, "", "Support summons last 1 minute by default");
			addSummonGuide(0, "", "Support upkeep rises every 3 minutes active");
			addSummonGuide(0, "", "Upkeep recovers 1 step per minute inactive");
			addSummonGuide(241, "", "Utility uses are spent only on successful tasks");
			addSummonGuide(13, "", "Pack Rat has 4 uses; Delivery Camel has 2");
			addSummonGuide(114, "", "Only one manual summon can be active");
			addSummonGuide(53, "", "Summoning has a 5 second interruptible charge");
		}
	}

	private void addSummonGuide(int npcId, String level, String detail) {
		skillMenuEntries.add(new SkillMenuNPC(npcId, level, detail));
	}

	private boolean isSummonGuideSideFacingNpc(int npcId) {
		return npcId == 8 || npcId == 0 || npcId == 296 || npcId == 241 || npcId == 295 || npcId == 748 || npcId == 13;
	}

	private void populateRangedGuide() {
		if (curTab == 0) {
			addRangedBowGuide("Logs", 1, 189, 188);
			addRangedBowGuide("Pine", 8, 2118, 2117);
			addRangedBowGuide("Oak", 15, 649, 648);
			addRangedBowGuide("Willow", 22, 651, 650);
			addRangedBowGuide("Palm", 30, 2122, 2121);
			addRangedBowGuide("Maple", 38, 653, 652);
			addRangedBowGuide("Yew", 46, 655, 654);
			addRangedBowGuide("Ebony", 54, 2126, 2125);
			addRangedBowGuide("Magic", 62, 657, 656);
			addRangedBowGuide("Blood", 70, 2130, 2129);
		} else if (curTab == 1) {
			addRangedCrossbowGuide("Logs", 1, 60, "Tin bolts");
			addRangedCrossbowGuide("Pine", 8, 59, "Copper bolts");
			addRangedCrossbowGuide("Oak", 15, 2169, "Bronze bolts");
			addRangedCrossbowGuide("Willow", 22, 2170, "Iron bolts");
			addRangedCrossbowGuide("Palm", 30, 2171, "Steel bolts");
			addRangedCrossbowGuide("Maple", 38, 2172, "Mithril bolts");
			addRangedCrossbowGuide("Yew", 46, 2173, "Titan steel bolts");
			addRangedCrossbowGuide("Ebony", 54, 2174, "Adamantite bolts");
			addRangedCrossbowGuide("Magic", 62, 2175, "Orichalcum bolts");
			addRangedCrossbowGuide("Blood", 70, 2176, "Rune bolts");
		} else if (curTab == 2) {
			addThrownGuide("Tin", 1, 2043, 1996, 3208);
			addThrownGuide("Copper", 8, 2044, 2007, 3209);
			addThrownGuide("Bronze", 15, 1013, 1076, 3210);
			addThrownGuide("Iron", 22, 1015, 1075, 3211);
			addThrownGuide("Steel", 30, 1024, 1077, 3212);
			addThrownGuide("Mithril", 38, 1068, 1078, 3213);
			addThrownGuide("Titan steel", 46, 2045, 2018, 3214);
			addThrownGuide("Adamantite", 54, 1069, 1079, 3215);
			addThrownGuide("Orichalcum", 62, 2046, 2029, 3216);
			addThrownGuide("Rune", 70, 1070, 1080, 3217);
		} else if (curTab == 3) {
			if (Config.S_WANT_CUSTOM_SPRITES) {
				addSkillCapeGuide(1524, "Ranged");
			}
		}
	}

	private void addRangedBowGuide(String name, int level, int shortbowId, int longbowId) {
		skillMenuEntries.add(new SkillMenuItem(shortbowId, String.valueOf(level), name + " shortbow"));
		skillMenuEntries.add(new SkillMenuItem(longbowId, String.valueOf(level), name + " longbow"));
	}

	private void addRangedCrossbowGuide(String name, int level, int crossbowId, String maxBoltTier) {
		skillMenuEntries.add(new SkillMenuItem(crossbowId, String.valueOf(level), name + " crossbow - up to " + maxBoltTier));
	}

	private void addMeleeTierGuide(String name, int level, int itemId) {
		skillMenuEntries.add(new SkillMenuItem(itemId, String.valueOf(level), name));
	}

	private void addSkillCapeGuide(int itemId, String skillName) {
		skillMenuEntries.add(new SkillMenuItem(itemId, "", skillName + " cape unlocked at level 99"));
	}

	private void addThrownGuide(String name, int level, int dartId, int knifeId, int shurikenId) {
		skillMenuEntries.add(new SkillMenuItem(dartId, String.valueOf(level), name + " throwing darts"));
		skillMenuEntries.add(new SkillMenuItem(knifeId, String.valueOf(level), name + " throwing knives"));
		skillMenuEntries.add(new SkillMenuItem(shurikenId, String.valueOf(level), name + " shuriken"));
	}

	private void addRuneTierGuide() {
		addAltarGuide(33, 1, "Air Rune", "306,593");
		addAltarGuide(32, 1, "Water Rune", "147,684");
		addAltarGuide(34, 1, "Earth Rune", "62,464");
		addAltarGuide(31, 1, "Fire Rune", "50,633");
		addAltarGuide(37, 1, "Life Rune", "283,694");
		addAltarGuide(35, 8, "Mind Rune", "297,438");
		addAltarGuide(36, 15, "Body Rune", "259,503");
		addAltarGuide(41, 22, "Chaos Rune", "232,375");
		addAltarGuide(46, 30, "Cosmic Rune", "104,3556");
		addAltarGuide(40, 38, "Nature Rune", "392,804");
		addAltarGuide(42, 46, "Law Rune", "409,534");
		addAltarGuide(38, 54, "Death Rune", "395,3541");
		addAltarGuide(825, 62, "Soul Rune", "611,3599");
		addAltarGuide(619, 70, "Blood Rune", "247,102");
	}

	private void addAltarGuide(int itemId, int level, String runeName, String coordinate) {
		skillMenuEntries.add(new SkillMenuItem(itemId, String.valueOf(level),
			runeName + " - altar at " + coordinate));
	}

	private void addRobeTierGuide() {
		skillMenuEntries.add(new SkillMenuItem(2050, "1", "Beginner Wool robes and hats"));
		skillMenuEntries.add(new SkillMenuItem(2051, "8", "Novice Wool robes and hats"));
		skillMenuEntries.add(new SkillMenuItem(2052, "15", "Apprentice Wool robes and hats"));
		skillMenuEntries.add(new SkillMenuItem(2050, "22", "Adept Wool robes and hats"));
		skillMenuEntries.add(new SkillMenuItem(2051, "30", "Expert Wool robes and hats"));
		skillMenuEntries.add(new SkillMenuItem(2052, "38", "Master Wool robes and hats"));
		skillMenuEntries.add(new SkillMenuItem(2050, "46", "Mystic Wool robes and hats"));
		skillMenuEntries.add(new SkillMenuItem(2051, "54", "Arcane Wool robes and hats"));
		skillMenuEntries.add(new SkillMenuItem(2052, "62", "Elder Wool robes and hats"));
		skillMenuEntries.add(new SkillMenuItem(2050, "70", "Mythic Wool robes and hats"));
	}

	private void addMagicSpellGuideEntries() {
		for (int spellIndex = 0; spellIndex <= 45; spellIndex++) {
			if (spellIndex == 25) {
				continue;
			}
			SpellDef spell = EntityHandler.getSpellDef(spellIndex);
			if (spell == null || spell.getName().equals("Retired spell")) {
				continue;
			}
			skillMenuEntries.add(new MagicSpellMenuItem(spellIndex, spell));
		}
	}

	private void addEnchantedStaffTierGuide() {
		skillMenuEntries.add(new SkillMenuItem(101, "1", "Enchanted Staff"));
		skillMenuEntries.add(new SkillMenuItem(2132, "8", "Enchanted Pine Staff"));
		skillMenuEntries.add(new SkillMenuItem(1765, "15", "Enchanted Oak Staff"));
		skillMenuEntries.add(new SkillMenuItem(1770, "22", "Enchanted Willow Staff"));
		skillMenuEntries.add(new SkillMenuItem(2137, "30", "Enchanted Palm Staff"));
		skillMenuEntries.add(new SkillMenuItem(1775, "38", "Enchanted Maple Staff"));
		skillMenuEntries.add(new SkillMenuItem(1780, "46", "Enchanted Yew Staff"));
		skillMenuEntries.add(new SkillMenuItem(2142, "54", "Enchanted Ebony Staff"));
		skillMenuEntries.add(new SkillMenuItem(1785, "62", "Enchanted Magic Staff"));
		skillMenuEntries.add(new SkillMenuItem(2147, "70", "Enchanted Blood Staff"));
	}

	private void addStaffTierGuide() {
		skillMenuEntries.add(new SkillMenuItem(100, "1", "Staff"));
		skillMenuEntries.add(new SkillMenuItem(2131, "8", "Pine Staff"));
		skillMenuEntries.add(new SkillMenuItem(1764, "15", "Oak Staff"));
		skillMenuEntries.add(new SkillMenuItem(1769, "22", "Willow Staff"));
		skillMenuEntries.add(new SkillMenuItem(2136, "30", "Palm Staff"));
		skillMenuEntries.add(new SkillMenuItem(1774, "38", "Maple Staff"));
		skillMenuEntries.add(new SkillMenuItem(1779, "46", "Yew Staff"));
		skillMenuEntries.add(new SkillMenuItem(2141, "54", "Ebony Staff"));
		skillMenuEntries.add(new SkillMenuItem(1784, "62", "Magic Staff"));
		skillMenuEntries.add(new SkillMenuItem(2146, "70", "Blood Staff"));
	}

	private void addJewelryTierGuide() {
		skillMenuEntries.add(new SkillMenuItem(284, "8", "Sapphire rings, necklaces, and Bangles"));
		skillMenuEntries.add(new SkillMenuItem(285, "18", "Emerald rings, necklaces, and Bangles"));
		skillMenuEntries.add(new SkillMenuItem(286, "32", "Ruby rings, necklaces, and Bangles"));
		skillMenuEntries.add(new SkillMenuItem(287, "48", "Diamond rings, necklaces, and Bangles"));
		skillMenuEntries.add(new SkillMenuItem(543, "58", "Dragonstone rings, necklaces, and Bangles"));
	}

	private void populatePrayerGuide() {
		if (curTab == 0) {
			addPrayerLine("Magic Power", "5 tiers of magic damage bonuses");
			addPrayerLine("Melee Protection", "5 tiers of melee damage reduction");
			addPrayerLine("Enchanting Favor", "5 tiers of enchanting XP bonuses");
		} else if (curTab == 1) {
			addPrayerLine("Melee Power", "5 tiers of melee damage bonuses");
			addPrayerLine("Ranged Protection", "5 tiers of ranged damage reduction");
			addPrayerLine("Smithing Favor", "5 tiers of smithing XP bonuses");
		} else if (curTab == 2) {
			addPrayerLine("Ranged Power", "5 tiers of ranged damage bonuses");
			addPrayerLine("Magic Protection", "5 tiers of magic damage reduction");
			addPrayerLine("Crafting Favor", "5 tiers of crafting XP bonuses");
		} else if (curTab == 3) {
			skillMenuEntries.add(new SkillMenuItem(1214, "80", "Saradomin Cape - requires Saradomin worship"));
			skillMenuEntries.add(new SkillMenuItem(1218, "80", "Staff of Saradomin - requires Saradomin worship"));
			skillMenuEntries.add(new SkillMenuItem(1213, "80", "Zamorak Cape - requires Zamorak worship"));
			skillMenuEntries.add(new SkillMenuItem(1216, "80", "Staff of Zamorak - requires Zamorak worship"));
			skillMenuEntries.add(new SkillMenuItem(1215, "80", "Guthix Cape - requires Guthix worship"));
			skillMenuEntries.add(new SkillMenuItem(1217, "80", "Staff of Guthix - requires Guthix worship"));
			skillMenuEntries.add(new SkillMenuItem(1218, "", "God gear gives prayer points and empowers god spells"));
			skillMenuEntries.add(new SkillMenuItem(2228, "1", "Staff blessed by Zamorak - requires Worship and Magic"));
			skillMenuEntries.add(new SkillMenuItem(2229, "8", "Pine staff blessed by Zamorak - requires Worship and Magic"));
			skillMenuEntries.add(new SkillMenuItem(2230, "15", "Oak staff blessed by Zamorak - requires Worship and Magic"));
			skillMenuEntries.add(new SkillMenuItem(2231, "22", "Willow staff blessed by Zamorak - requires Worship and Magic"));
			skillMenuEntries.add(new SkillMenuItem(2232, "30", "Palm staff blessed by Zamorak - requires Worship and Magic"));
			skillMenuEntries.add(new SkillMenuItem(2233, "38", "Maple staff blessed by Zamorak - requires Worship and Magic"));
			skillMenuEntries.add(new SkillMenuItem(2234, "46", "Yew staff blessed by Zamorak - requires Worship and Magic"));
			skillMenuEntries.add(new SkillMenuItem(2235, "54", "Ebony staff blessed by Zamorak - requires Worship and Magic"));
			skillMenuEntries.add(new SkillMenuItem(2236, "62", "Magic staff blessed by Zamorak - requires Worship and Magic"));
			skillMenuEntries.add(new SkillMenuItem(2237, "70", "Blood staff blessed by Zamorak - requires Worship and Magic"));
			skillMenuEntries.add(new SkillMenuItem(3152, "", "Saradomin blessed staves use the same tiers"));
			skillMenuEntries.add(new SkillMenuItem(3162, "", "Guthix blessed staves use the same tiers"));
			skillMenuEntries.add(new SkillMenuItem(3137, "100", "Wool hats can be blessed"));
			skillMenuEntries.add(new SkillMenuItem(3140, "200", "Wool gloves and boots can be blessed"));
			skillMenuEntries.add(new SkillMenuItem(3139, "300", "Wool robe bottoms can be blessed"));
			skillMenuEntries.add(new SkillMenuItem(3138, "400", "Wool robe tops can be blessed"));
		} else if (curTab == 4) {
			skillMenuEntries.add(new SkillMenuEntry("", "Bury bones or scatter ashes to make offerings"));
			skillMenuEntries.add(new SkillMenuEntry("", "Every 10 offerings gives +1 devotion"));
			skillMenuEntries.add(new SkillMenuEntry("", "Matching blessed symbols give 2x devotion from offerings"));
			skillMenuEntries.add(new SkillMenuEntry("", "Mourning Unicorn and its full hide set each add 50%"));
			skillMenuEntries.add(new SkillMenuEntry("", "+1 Worship XP per offering for each devotion"));
			skillMenuEntries.add(new SkillMenuEntry("", "Devotion is tracked separately for each god"));
			skillMenuEntries.add(new SkillMenuEntry("", "Devotion ranges from -1000 to 1000"));
			skillMenuEntries.add(new SkillMenuEntry("", "Bless items only at your current god's altar"));
			skillMenuEntries.add(new SkillMenuEntry("", "Symbols require 50 devotion and are free to bless"));
			skillMenuEntries.add(new SkillMenuEntry("", "Blessed items grow stronger with devotion"));
			skillMenuEntries.add(new SkillMenuEntry("", "Blessing Worship XP rises by 1% per devotion"));
			skillMenuEntries.add(new SkillMenuEntry("", "Equipment requires 100 devotion per resource"));
			skillMenuEntries.add(new SkillMenuEntry("", "Blessing equipment costs 0.5 devotion per resource"));
			skillMenuEntries.add(new SkillMenuEntry("", "1 resource: daggers, short swords, maces, helms, wool hats"));
			skillMenuEntries.add(new SkillMenuEntry("", "2 resources: longs, scims, spears, gloves, boots"));
			skillMenuEntries.add(new SkillMenuEntry("", "3 resources: 2-handers, battleaxes, shields, legs, scythes"));
			skillMenuEntries.add(new SkillMenuEntry("", "4 resources: bodies and robe tops"));
			skillMenuEntries.add(new SkillMenuEntry("", "Staves use their wood tier as their resource count"));
			skillMenuEntries.add(new SkillMenuEntry("", "Basic staves require 100; blood staves require 1000"));
			skillMenuEntries.add(new SkillMenuEntry("", "You may complete 10 blessings per hour"));
			skillMenuEntries.add(new SkillMenuEntry("", "Destroy opposing god's blessed items at your altar"));
			skillMenuEntries.add(new SkillMenuEntry("", "Destruction transfers 1 devotion per resource or staff tier"));
			skillMenuEntries.add(new SkillMenuEntry("", "Symbols transfer 0.2 devotion when destroyed"));
			skillMenuEntries.add(new SkillMenuEntry("", "Destruction grants 5x the item's production Worship XP"));
			skillMenuEntries.add(new SkillMenuEntry("", "God artifacts cannot be destroyed"));
		} else if (curTab == 5) {
			skillMenuEntries.add(new SkillMenuItem(388, "", "Worship at a god's altar to switch prayers"));
			skillMenuEntries.add(new SkillMenuItem(388, "", "Prayer does not drain over time"));
			skillMenuEntries.add(new SkillMenuItem(388, "", "Prayer uses point reservation to activate"));
			if (Config.S_WANT_CUSTOM_SPRITES) {
				addSkillCapeGuide(1523, "Worship");
			}
		}
	}

	private void addPrayerLine(String line, String effect) {
		skillMenuEntries.add(new SkillMenuItem(44, "", line + " - " + effect));
	}

	private void populateMiningGuide() {
		if (curTab == 0) {
			skillMenuEntries.add(new SkillMenuItem(149, "1", "Clay"));
			skillMenuEntries.add(new SkillMenuItem(1299, "1", "Stone"));
			skillMenuEntries.add(new SkillMenuItem(202, "1", "Tin Ore"));
			skillMenuEntries.add(new SkillMenuItem(150, "8", "Copper Ore"));
			skillMenuEntries.add(new SkillMenuItem(266, "10", "Blurite Ore"));
			skillMenuEntries.add(new SkillMenuItem(151, "15", "Iron Ore"));
			skillMenuEntries.add(new SkillMenuItem(383, "20", "Silver Nugget"));
			skillMenuEntries.add(new SkillMenuItem(155, "22", "Coal"));
			skillMenuEntries.add(new SkillMenuItem(153, "38", "Mithril Ore"));
			skillMenuEntries.add(new SkillMenuItem(152, "40", "Gold Nugget"));
			skillMenuEntries.add(new SkillMenuItem(889, "40", "Gem Rocks"));
			skillMenuEntries.add(new SkillMenuItem(154, "54", "Adamantite Ore"));
			skillMenuEntries.add(new SkillMenuItem(409, "70", "Runite Ore"));
		} else if (curTab == 1) {
			skillMenuEntries.add(new SkillMenuItem(1987, "1", "Tin Pickaxe"));
			skillMenuEntries.add(new SkillMenuItem(2047, "8", "Copper Pickaxe"));
			skillMenuEntries.add(new SkillMenuItem(156, "15", "Bronze Pickaxe"));
			skillMenuEntries.add(new SkillMenuItem(1258, "22", "Iron Pickaxe"));
			skillMenuEntries.add(new SkillMenuItem(1259, "30", "Steel Pickaxe"));
			skillMenuEntries.add(new SkillMenuItem(1260, "38", "Mithril Pickaxe"));
			skillMenuEntries.add(new SkillMenuItem(2048, "46", "Titan steel Pickaxe"));
			skillMenuEntries.add(new SkillMenuItem(1261, "54", "Adamantite Pickaxe"));
			skillMenuEntries.add(new SkillMenuItem(2049, "62", "Orichalcum Pickaxe"));
			skillMenuEntries.add(new SkillMenuItem(1262, "70", "Rune Pickaxe"));
		} else if (curTab == 2) {
			skillMenuEntries.add(new SkillMenuItem(1259, "", "Mining Guild can be entered at level 60"));
			skillMenuEntries.add(new SkillMenuItem(1260, "", "Pickaxe must be equipped to mine rocks"));
			skillMenuEntries.add(new SkillMenuItem(3178, "", "Geode focus: Just the ore, A few, Plenty, Lots"));
			skillMenuEntries.add(new SkillMenuItem(3178, "", "Higher geode focus increases rare geode rolls"));
			skillMenuEntries.add(new SkillMenuItem(1299, "", "Stone comes only from regular rock nodes"));
			if (Config.S_WANT_CUSTOM_SPRITES) {
				addSkillCapeGuide(1377, "Mining");
			}
		}
	}

	private void populateCraftingGuide() {
		if (curTab == 0) {
			addLeatherGuide(1839, "1", "Cow hide armor");
			addLeatherGuide(1844, "1", "Goblin hide armor");
			addLeatherGuide(1849, "8", "Unicorn hide armor");
			addLeatherGuide(1854, "8", "Bear hide armor");
			addLeatherGuide(1859, "8", "Black unicorn hide armor");
			addLeatherGuide(1864, "8", "Scorpion carapace armor");
			addLeatherGuide(1869, "15", "Wolf hide armor");
			addLeatherGuide(1874, "15", "Spider carapace armor");
			addLeatherGuide(1879, "15", "Giant hide armor");
			addLeatherGuide(1884, "22", "Ogre hide armor");
			addLeatherGuide(1889, "22", "Baby dragon hide armor");
			addLeatherGuide(1894, "30", "Magic spider carapace armor");
			addLeatherGuide(1899, "30", "Moss giant hide armor");
			addLeatherGuide(1904, "30", "Ice giant hide armor");
			addLeatherGuide(1909, "38", "Demon hide armor");
			addLeatherGuide(1914, "46", "Hellhound hide armor");
			addLeatherGuide(1919, "46", "Fire giant hide armor");
			addLeatherGuide(1924, "46", "Blue dragon hide armor");
			addLeatherGuide(1929, "46", "Green dragon hide armor");
			addLeatherGuide(1934, "54", "Red dragon hide armor");
			addLeatherGuide(1939, "54", "Black demon hide armor");
			addLeatherGuide(1944, "62", "Black dragon hide armor");
			addLeatherGuide(1949, "70", "Balrog hide armor");
			addLeatherGuide(1954, "80", "Elder green dragon hide armor");
		} else if (curTab == 1) {
			skillMenuEntries.add(new SkillMenuItem(2050, "1", "Wool hat - 1 ball of wool"));
			skillMenuEntries.add(new SkillMenuItem(2051, "1", "Wool robe top - 4 balls of wool"));
			skillMenuEntries.add(new SkillMenuItem(2052, "1", "Wool robe bottom - 3 balls of wool"));
			skillMenuEntries.add(new SkillMenuItem(2053, "1", "Wool cape - 2 balls of wool"));
			skillMenuEntries.add(new SkillMenuItem(207, "1", "Ball of Wool"));
			skillMenuEntries.add(new SkillMenuItem(676, "10", "Bow String"));
		} else if (curTab == 2) {
			skillMenuEntries.add(new SkillMenuItem(135, "1", "Pot"));
			skillMenuEntries.add(new SkillMenuItem(251, "4", "Pie Dish"));
			skillMenuEntries.add(new SkillMenuItem(341, "7", "Bowl"));
			skillMenuEntries.add(new SkillMenuItem(623, "1", "Molten Glass"));
			skillMenuEntries.add(new SkillMenuItem(620, "1", "Beer Glass"));
			skillMenuEntries.add(new SkillMenuItem(1018, "10", "Lens"));
			skillMenuEntries.add(new SkillMenuItem(465, "33", "Vial"));
		} else if (curTab == 3) {
			skillMenuEntries.add(new SkillMenuItem(164, "8", "Sapphire"));
			skillMenuEntries.add(new SkillMenuItem(894, "10", "Opal"));
			skillMenuEntries.add(new SkillMenuItem(893, "13", "Jade"));
			skillMenuEntries.add(new SkillMenuItem(892, "16", "Red Topaz"));
			skillMenuEntries.add(new SkillMenuItem(163, "18", "Emerald"));
			skillMenuEntries.add(new SkillMenuItem(162, "32", "Ruby"));
			skillMenuEntries.add(new SkillMenuItem(161, "48", "Diamond"));
			skillMenuEntries.add(new SkillMenuItem(523, "58", "Dragonstone"));
			skillMenuEntries.add(new SkillMenuItem(283, "1", "Gold Ring"));
			skillMenuEntries.add(new SkillMenuItem(288, "3", "Gold Necklace"));
			skillMenuEntries.add(new SkillMenuItem(3292, "5", "Gold Bangle"));
			skillMenuEntries.add(new SkillMenuItem(284, "8", "Sapphire Ring"));
			skillMenuEntries.add(new SkillMenuItem(289, "10", "Sapphire Necklace"));
			skillMenuEntries.add(new SkillMenuItem(3282, "13", "Sapphire Bangle"));
			skillMenuEntries.add(new SkillMenuItem(44, "16", "Symbol of Saradomin"));
			skillMenuEntries.add(new SkillMenuItem(1027, "16", "Symbol of Zamorak"));
			skillMenuEntries.add(new SkillMenuItem(3173, "16", "Symbol of Guthix"));
			skillMenuEntries.add(new SkillMenuItem(285, "18", "Emerald Ring"));
			skillMenuEntries.add(new SkillMenuItem(290, "22", "Emerald Necklace"));
			skillMenuEntries.add(new SkillMenuItem(3283, "26", "Emerald Bangle"));
			skillMenuEntries.add(new SkillMenuItem(286, "32", "Ruby Ring"));
			skillMenuEntries.add(new SkillMenuItem(291, "38", "Ruby Necklace"));
			skillMenuEntries.add(new SkillMenuItem(3284, "44", "Ruby Bangle"));
			skillMenuEntries.add(new SkillMenuItem(287, "48", "Diamond Ring"));
			skillMenuEntries.add(new SkillMenuItem(292, "54", "Diamond Necklace"));
			skillMenuEntries.add(new SkillMenuItem(543, "58", "Dragonstone Ring"));
			skillMenuEntries.add(new SkillMenuItem(3285, "60", "Diamond Bangle"));
			skillMenuEntries.add(new SkillMenuItem(544, "64", "Dragonstone Necklace"));
			skillMenuEntries.add(new SkillMenuItem(3286, "70", "Dragonstone Bangle"));
		} else if (curTab == 4) {
			skillMenuEntries.add(new SkillMenuItem(637, "1", "Headless arrows - arrow shafts and feathers"));
			addAmmoMouldGuide("Tin", 1, 190, 2004, 2039, 2043, 1996, 3208);
			addAmmoMouldGuide("Copper", 8, 2178, 2015, 2040, 2044, 2007, 3209);
			addAmmoMouldGuide("Bronze", 15, 2180, 669, 11, 1013, 1076, 3210);
			addAmmoMouldGuide("Iron", 22, 2182, 670, 638, 1015, 1075, 3211);
			addAmmoMouldGuide("Steel", 30, 2184, 671, 640, 1024, 1077, 3212);
			addAmmoMouldGuide("Mithril", 38, 2186, 672, 642, 1068, 1078, 3213);
			addAmmoMouldGuide("Titan steel", 46, 2188, 2026, 2041, 2045, 2018, 3214);
			addAmmoMouldGuide("Adamantite", 54, 2190, 673, 644, 1069, 1079, 3215);
			addAmmoMouldGuide("Orichalcum", 62, 2192, 2037, 2042, 2046, 2029, 3216);
			addAmmoMouldGuide("Rune", 70, 2194, 674, 646, 1070, 1080, 3217);
		} else if (curTab == 5) {
			addWoodcraftGuide("Basic", 10, 1, 277, 5, 276, 8, 60, 9, 100, 1, 377, 1);
			addWoodcraftGuide("Pine", 15, 8, 2116, 12, 2115, 15, 59, 16, 2131, 8, 2682, 8);
			addWoodcraftGuide("Oak", 20, 15, 659, 19, 658, 22, 2169, 23, 1764, 15, 2683, 15);
			addWoodcraftGuide("Willow", 25, 22, 661, 26, 660, 29, 2170, 30, 1769, 22, 2684, 22);
			addWoodcraftGuide("Palm", 30, 30, 2120, 33, 2119, 36, 2171, 37, 2136, 30, 2685, 30);
			addWoodcraftGuide("Maple", 35, 38, 663, 40, 662, 43, 2172, 44, 1774, 38, 2686, 38);
			addWoodcraftGuide("Yew", 40, 46, 665, 47, 664, 50, 2173, 51, 1779, 46, 2687, 46);
			addWoodcraftGuide("Ebony", 45, 54, 2124, 54, 2123, 57, 2174, 58, 2141, 54, 2688, 54);
			addWoodcraftGuide("Magic", 50, 62, 667, 61, 666, 64, 2175, 65, 1784, 62, 2689, 62);
			addWoodcraftGuide("Blood", 55, 70, 2128, 67, 2127, 70, 2176, 72, 2146, 70, 2690, 70);
		} else if (curTab == 6) {
			skillMenuEntries.add(new SkillMenuItem(1839, "", "Leather armor has set effects if all five pieces are worn"));
			skillMenuEntries.add(new SkillMenuItem(1839, "", "Leather armor mimics the defenses of its creature"));
			skillMenuEntries.add(new SkillMenuItem(148, "", "Hides and carapaces must be tanned at tanning racks"));
			skillMenuEntries.add(new SkillMenuItem(1839, "", "Examine armor pieces to read their trait"));
			skillMenuEntries.add(new SkillMenuItem(1869, "", "Set traits can grant stats, procs, or spirits"));
			skillMenuEntries.add(new SkillMenuItem(1839, "", "Leather armor slots lower Magic Power"));
			skillMenuEntries.add(new SkillMenuItem(1076, "", "Arrowheads moved to Crafting and use molds"));
			skillMenuEntries.add(new SkillMenuItem(11, "", "Bolts moved to Crafting and use molds"));
			skillMenuEntries.add(new SkillMenuItem(1013, "", "Dart tips moved to Crafting and use molds"));
			skillMenuEntries.add(new SkillMenuItem(1996, "", "Throwing knives moved to Crafting and use molds"));
			skillMenuEntries.add(new SkillMenuItem(3208, "", "Shuriken use Crafting and molds"));
			skillMenuEntries.add(new SkillMenuItem(191, "", "Crafting Guild can be entered at level 40 with a brown apron"));
			if (Config.S_WANT_CUSTOM_SPRITES) {
				addSkillCapeGuide(1384, "Crafting");
			}
		}
	}

	private void addLeatherGuide(int itemId, String level, String detail) {
		skillMenuEntries.add(new SkillMenuItem(itemId, level, detail));
	}

	private void addHerblawPotionFamilyGuide(int tier1Id, int tier2Id, int tier3Id, int tier4Id, int tier5Id, int tier6Id,
											 String potionName, String secondary) {
		addHerblawPotionFamilyGuide(tier1Id, tier2Id, tier3Id, tier4Id, tier5Id, tier6Id, potionName,
			new String[] {secondary, secondary, secondary, secondary, secondary, secondary});
	}

	private void addHerblawPotionFamilyGuide(int tier1Id, int tier2Id, int tier3Id, int tier4Id, int tier5Id, int tier6Id,
											 String potionName, String[] secondaries) {
		skillMenuEntries.add(new SkillMenuItem(tier1Id, "3", potionName + " v1 - Guam leaf & " + secondaries[0]));
		skillMenuEntries.add(new SkillMenuItem(tier2Id, "12", potionName + " v2 - Tarromin & " + secondaries[1]));
		skillMenuEntries.add(new SkillMenuItem(tier3Id, "30", potionName + " v3 - Ranarr weed & " + secondaries[2]));
		skillMenuEntries.add(new SkillMenuItem(tier4Id, "50", potionName + " v4 - Avantoe & " + secondaries[3]));
		skillMenuEntries.add(new SkillMenuItem(tier5Id, "66", potionName + " v5 - Cadantine & " + secondaries[4]));
		skillMenuEntries.add(new SkillMenuItem(tier6Id, "78", potionName + " v6 - Torstol & " + secondaries[5]));
	}

	private void addAmmoMouldGuide(String name, int baseLevel, int boltsId, int arrowheadsId, int arrowId, int dartsId, int knivesId, int shurikenId) {
		skillMenuEntries.add(new SkillMenuItem(boltsId, String.valueOf(baseLevel), name + " bolts - 1 bar makes 5"));
		skillMenuEntries.add(new SkillMenuItem(arrowheadsId, String.valueOf(baseLevel), name + " arrowheads - 1 bar makes 10"));
		skillMenuEntries.add(new SkillMenuItem(arrowId, String.valueOf(baseLevel), name + " arrows - headless arrows and arrowheads"));
		skillMenuEntries.add(new SkillMenuItem(dartsId, String.valueOf(baseLevel + 2), name + " throwing darts - 1 bar makes 7"));
		skillMenuEntries.add(new SkillMenuItem(knivesId, String.valueOf(baseLevel + 4), name + " throwing knives - 1 bar makes 2"));
		skillMenuEntries.add(new SkillMenuItem(shurikenId, String.valueOf(baseLevel + 4), name + " shuriken - 1 bar makes 9"));
	}

	private void addWoodcraftGuide(String name, int shaftAmount, int shaftLevel, int shortbowId, int shortbowLevel,
								   int longbowId, int longbowLevel, int crossbowId, int crossbowLevel,
								   int staffId, int staffLevel, int fishingRodId, int fishingRodLevel) {
		skillMenuEntries.add(new SkillMenuItem(280, String.valueOf(shaftLevel), name + " arrow shafts - 1 log makes " + shaftAmount));
		skillMenuEntries.add(new SkillMenuItem(staffId, String.valueOf(staffLevel), name + " staff"));
		skillMenuEntries.add(new SkillMenuItem(fishingRodId, String.valueOf(fishingRodLevel), EntityHandler.getItemDef(fishingRodId).name));
		skillMenuEntries.add(new SkillMenuItem(shortbowId, String.valueOf(shortbowLevel), "Unstrung " + name.toLowerCase() + " shortbow"));
		skillMenuEntries.add(new SkillMenuItem(longbowId, String.valueOf(longbowLevel), "Unstrung " + name.toLowerCase() + " longbow"));
		skillMenuEntries.add(new SkillMenuItem(crossbowId, String.valueOf(crossbowLevel), name + " crossbow"));
	}

	private void populateSmithingGuide() {
		if (curTab == 0) {
			skillMenuEntries.add(new SkillMenuItem(1955, "1", "Tin bar - 1 tin ore"));
			skillMenuEntries.add(new SkillMenuItem(1956, "8", "Copper bar - 1 copper ore"));
			skillMenuEntries.add(new SkillMenuItem(169, "15", "Bronze bar - 1 tin ore and 1 copper ore"));
			skillMenuEntries.add(new SkillMenuItem(384, "20", "Silver bar - 1 silver nugget"));
			skillMenuEntries.add(new SkillMenuItem(170, "22", "Iron bar - failed smelts create pig iron"));
			skillMenuEntries.add(new SkillMenuItem(2753, "22", "Pig Iron bar - recovered from failed iron"));
			skillMenuEntries.add(new SkillMenuItem(171, "30", "Steel bar - 1 iron or pig iron and 1 coal"));
			skillMenuEntries.add(new SkillMenuItem(173, "38", "Mithril bar - 1 mithril ore and 2 coal"));
			skillMenuEntries.add(new SkillMenuItem(172, "40", "Gold bar - 1 gold nugget"));
			skillMenuEntries.add(new SkillMenuItem(1957, "46", "Titan steel bar - mithril, silver, and 3 coal"));
			skillMenuEntries.add(new SkillMenuItem(174, "54", "Adamantite bar - 1 adamantite ore and 4 coal"));
			skillMenuEntries.add(new SkillMenuItem(1958, "62", "2 Orichalcum bars - 1 mithril, 1 adamantite, and 5 coal"));
			skillMenuEntries.add(new SkillMenuItem(408, "70", "Runite bar - 1 runite ore and 6 coal"));
			skillMenuEntries.add(new SkillMenuItem(1365, "80", "Dragon bar - dragon metal scrap and 6 dragon sulfur"));
			skillMenuEntries.add(new SkillMenuItem(3261, "90", "Purified Rune Bar - rune bar and 14 dragon sulfur"));
			if (Config.S_WANT_CUSTOM_SPRITES) {
				addSkillCapeGuide(1383, "Smithing");
			}
		} else if (curTab == 1) {
			addSmithingTier("Tin", 1, 1995, 1997, 1998, 1999, 2000, 2001, 1987, 2002, 2003, 3181, 2207, -1, 1959, 1960, 1961, 2224, 1962, 1963, 1964);
			addSmithingTier("Copper", 8, 2006, 2008, 2009, 2010, 2011, 2012, 2047, 2013, 2014, 3182, 2208, 2178, 1965, 1966, 1967, 2225, 1968, 1969, 1970);
		} else if (curTab == 2) {
			addSmithingTier("Bronze", 15, 62, 66, 70, 82, 76, 87, 156, 205, 3183, 94, 827, 2180, 108, 1983, 1984, 124, 128, 206, 117);
			addSmithingTier("Iron", 22, 28, 1, 71, 83, 77, 12, 1258, 89, 3184, 0, 1088, 2182, 6, 1985, 1986, 3, 2, 9, 8);
		} else if (curTab == 3) {
			addSmithingTier("Steel", 30, 63, 67, 72, 84, 78, 88, 1259, 90, 3185, 95, 1089, 2184, 109, 698, 1988, 125, 129, 121, 118);
			addSmithingTier("Mithril", 38, 64, 68, 73, 85, 79, 203, 1260, 91, 3186, 96, 1090, 2186, 110, 1989, 1990, 126, 130, 122, 119);
		} else if (curTab == 4) {
			addSmithingTier("Titan steel", 46, 2017, 2019, 2020, 2021, 2022, 2023, 2048, 2024, 2025, 3187, 2209, 2188, 1971, 1972, 1973, 2226, 1974, 1975, 1976);
			addSmithingTier("Adamantite", 54, 65, 69, 74, 86, 80, 204, 1261, 92, 3188, 97, 1091, 2190, 111, 1991, 1992, 127, 131, 123, 120);
		} else if (curTab == 5) {
			addSmithingTier("Orichalcum", 62, 2028, 2030, 2031, 2032, 2033, 2034, 2049, 2035, 2036, 3189, 2210, 2192, 1977, 1978, 1979, 2227, 1980, 1981, 1982);
			addSmithingTier("Rune", 70, 396, 397, 75, 398, 81, 405, 1262, 93, 3190, 98, 1092, 2194, 112, 1993, 1994, 403, 404, 402, 401);
		} else if (curTab == 6) {
			addSmithingTier("Dragon", 80, 1447, -1, 593, -1, 1346, 594, -1, 2752, -1, -1, -1, -1, 1425, -1, -1, 1278, 1426, 1429, 1427);
			addSmithingTier("Exalted Rune", 90, 3262, 3263, 3264, 3265, 3266, 3267, 3268, 3269, 3271, 3270, 3273, -1, 3274, 3275, 3276, 3277, 3278, 3279, 3280);
			skillMenuEntries.add(new SkillMenuItem(979, "4", "Bronze wire - 1 bronze bar"));
			skillMenuEntries.add(new SkillMenuItem(419, "34", "Nails - 1 steel bar makes 2"));
			skillMenuEntries.add(new SkillMenuItem(1278, "60", "Dragon square shield - smith the 2 halves together"));
		} else if (curTab == 7) {
			skillMenuEntries.add(new SkillMenuEntry("", "Dragon bars require the repaired lava forge"));
			skillMenuEntries.add(new SkillMenuEntry("", "Purified Rune Bars require the repaired lava forge"));
			skillMenuEntries.add(new SkillMenuEntry("", "Repair the forge after Dwarf Youth Rescue"));
			skillMenuEntries.add(new SkillMenuEntry("", "Repair cost: 100 black dragon scales and 1,000,000 coins"));
			skillMenuEntries.add(new SkillMenuEntry("", "Dragon and Exalted Rune gear are smithed at normal anvils"));
		}
	}

	private void addSmithingTier(String name, int baseLevel, int daggerId, int shortSwordId, int longSwordId,
								int scimitarId, int twoHandedId, int axeId, int pickaxeId, int battleAxeId,
								int maceId, int scytheId, int spearId, int boltId, int helmId, int gauntletId, int greavesId,
								int shieldId, int paladinShieldId, int legsId, int bodyId) {
		addSmithingGuideItem(daggerId, String.valueOf(baseLevel), name + " weapons - 1 to 3 bars");
		addSmithingGuideItem(axeId, String.valueOf(baseLevel), name + " tools - 1 bar");
		addSmithingGuideItem(scytheId, String.valueOf(baseLevel), name + " scythe - 3 bars");
		addSmithingGuideItem(helmId, String.valueOf(baseLevel), name + " helm - 1 bar");
		addSmithingGuideItem(gauntletId, String.valueOf(baseLevel + 2), name + " gauntlets - 2 bars");
		addSmithingGuideItem(greavesId, String.valueOf(baseLevel + 2), name + " greaves - 2 bars");
		addSmithingGuideItem(shieldId, String.valueOf(baseLevel + 3), name + " square shield - 3 bars");
		addSmithingGuideItem(paladinShieldId, String.valueOf(baseLevel + 4), name + " paladin shield - 3 bars");
		addSmithingGuideItem(legsId, String.valueOf(baseLevel + 4), name + " platelegs - 3 bars");
		addSmithingGuideItem(bodyId, String.valueOf(baseLevel + 6), name + " platebody - 4 bars");
	}

	private void addSmithingGuideItem(int itemId, String level, String description) {
		if (itemId < 0) {
			return;
		}
		skillMenuEntries.add(new SkillMenuItem(itemId, level, description));
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}
}

class SkillMenuEntry {

	private String levelReq, skillDetail;

	protected SkillMenuEntry(String levelReq, String skillDetail) {
		this.levelReq = levelReq;
		this.skillDetail = skillDetail;
	}

	public String getLevelReq() {
		return levelReq;
	}

	public String getSkillDetail() {
		return skillDetail;
	}
}

class SkillMenuItem extends SkillMenuEntry {

	private int itemID;

	public SkillMenuItem(int itemID, String level, String detail) {
		super(level, detail);
		this.itemID = itemID;
	}

	public int getItemID() {
		return itemID;
	}
}

class MagicSpellMenuItem extends SkillMenuEntry {
	private String spellName;
	private String tooltip;

	public MagicSpellMenuItem(int spellIndex, SpellDef spell) {
		super(String.valueOf(spell.getReqLevel()), "");
		ElementalSpellDisplayMetadata metadata = ElementalSpellDisplayMetadata.resolve(spellIndex, spell);
		String displayName = metadata == null ? spell.getName() : metadata.getSpellName();
		this.spellName = formatSpellName(displayName);
		this.tooltip = formatTooltip(spell.getDescription(), metadata);
	}

	private String formatSpellName(String name) {
		if (name == null) {
			return "";
		}
		if (name.equals("Saradomin Soul Slash")) {
			return "Saradomin S.S.";
		}
		if (name.equals("Zamorak's Apocolypse")) {
			return "Zamorak's Apoc";
		}
		return name.replace("teleport", "Tele").replace("Teleport", "Tele");
	}

	private String formatTooltip(String description, ElementalSpellDisplayMetadata metadata) {
		if (metadata != null) {
			return metadata.getGuideTooltip();
		}
		if (description == null) {
			return "";
		}
		if (description.startsWith("Advanced god spell")) {
			return description.substring("Advanced god spell; ".length());
		}
		if (description.equals("Take an item you can see but can't reach")) {
			return "Grabs distant ground items";
		}
		if (description.equals("Converts an item into gold")) {
			return "Turns items into coins";
		}
		if (description.equals("Dark magic that splashes nearby foes")) {
			return "Area dark magic damage";
		}
		return description;
	}

	public String getSpellName() {
		return spellName;
	}

	public String getTooltip() {
		return tooltip;
	}
}

class SkillMenuNPC extends SkillMenuEntry {
	private int npcID;

	public SkillMenuNPC(int npcID, String level, String detail) {
		super(level, detail);
		this.npcID = npcID;
	}

	public int getNpcID() {
		return npcID;
	}
}
