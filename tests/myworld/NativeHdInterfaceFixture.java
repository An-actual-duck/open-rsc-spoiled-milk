package orsc;

import java.util.Properties;
import java.util.Collections;

/** Run with and without -Dspoiledmilk.nativeHdInterface=true in separate JVMs. */
public final class NativeHdInterfaceFixture {
	public static void main(String[] args) {
		boolean preview = Boolean.getBoolean("spoiledmilk.nativeHdInterface");
		RenderSurfaceSettings.Mode expected = preview ? RenderSurfaceSettings.Mode.FULL_HD : RenderSurfaceSettings.Mode.WIDE;
		check(RenderSurfaceSettings.getMode() == expected, "Explicit 1080p selection");
		check(RenderSurfaceSettings.getWidth() == (preview ? 1920 : 960), "Framebuffer width");
		check(RenderSurfaceSettings.getHeight() == (preview ? 1080 : 540), "Framebuffer height");
		check(RenderSurfaceSettings.usesNativeHdInterface(1080) == preview, "HD layout admission");
		check(!RenderSurfaceSettings.usesNativeHdInterface(540), "540p layout admission");
		RenderSurfaceSettings.setMode(RenderSurfaceSettings.Mode.WIDE);
		check(RenderSurfaceSettings.cycleMode() == (preview ? expected : RenderSurfaceSettings.Mode.SVGA),
			"Selectable resolutions");

		verifyIndependentSizing();
		verifyAbilityBookSettings();
		verifySettingRows(preview);
		for (GameInterfaceSettings.Size size : GameInterfaceSettings.Size.values()) {
			for (GameInterfaceSettings.TextSize text : GameInterfaceSettings.TextSize.values()) {
				verifyChatBounds(chat(1920, 1080, size, text), 1920);
				verifyChatBounds(chat(960, 540, size, text), 960);
				verifyChatBounds(chat(800, 600, size, text), 800);
				verifyInventoryBounds(1920, 1080, size, text, false);
				verifyInventoryBounds(960, 540, size, text, false);
				verifyInventoryBounds(960, 540, size, text, true);
				verifyToolbarBounds(1920, 1080, size, text, false);
				verifyToolbarBounds(960, 540, size, text, false);
				verifyToolbarBounds(960, 540, size, text, true);
				verifyAccomplishmentsBounds(1920, 1080, size, text, false);
				verifyAccomplishmentsBounds(960, 540, size, text, false);
				verifyAccomplishmentsBounds(960, 540, size, text, true);
				verifyAbilityBounds(1920, 1080, size, text, false);
				verifyAbilityBounds(960, 540, size, text, false);
				verifyAbilityBounds(960, 540, size, text, true);
			}
		}
		System.out.println("PASS native HD interface preview=" + preview);
	}

	private static void verifySettingRows(boolean preview) {
		RendererSettingsPanel.State state = RendererSettingsPanel.State.capture(false,
			ScaledWindow.ScalingAlgorithm.INTEGER_SCALING, 1, Collections.singletonList(1f),
			false, false, false, false);
		int sizeRow = -1;
		int textRow = -1;
		for (RendererSettingsPanel.Row row : RendererSettingsPanel.rows(state)) {
			if (row.label.contains("UI size -")) sizeRow = row.action;
			if (row.label.contains("Text size -")) textRow = row.action;
		}
		check(preview == (sizeRow >= 0 && textRow >= 0), "Sizing controls follow preview admission");
		if (preview) {
			check(sizeRow != textRow, "Each preference has its own action");
		}
	}

	private static ChatPanelLayout chat(int width, int height,
									  GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text) {
		int[] heights = {12, 14, 14, 15, 15, 19, 24, 29};
		return new ChatPanelLayout(width, height, size, text,
			heights[text.bodyFont], heights[text.labelFont], 130);
	}

	private static void verifyIndependentSizing() {
		Properties settings = new Properties();
		settings.setProperty(RenderSurfaceSettings.SURFACE_MODE_PROPERTY_KEY, "960x540");
		GameInterfaceSettings.loadFromClientSettings(settings);
		check(GameInterfaceSettings.size() == GameInterfaceSettings.Size.STANDARD, "Legacy UI size fallback");
		check(GameInterfaceSettings.textSize() == GameInterfaceSettings.TextSize.STANDARD, "Legacy font fallback");
		RenderSurfaceSettings.Mode renderMode = RenderSurfaceSettings.getMode();
		GameInterfaceSettings.cycleSize();
		check(GameInterfaceSettings.textSize() == GameInterfaceSettings.TextSize.STANDARD, "UI size preserves text size");
		GameInterfaceSettings.cycleTextSize();
		check(GameInterfaceSettings.size() == GameInterfaceSettings.Size.LARGE, "Text size preserves UI size");
		GameInterfaceSettings.saveToClientSettings(settings);
		GameInterfaceSettings.loadFromClientSettings(new Properties());
		GameInterfaceSettings.loadFromClientSettings(settings);
		check(GameInterfaceSettings.size() == GameInterfaceSettings.Size.LARGE, "UI size reload");
		check(GameInterfaceSettings.textSize() == GameInterfaceSettings.TextSize.EXTRA_LARGE, "Text size reload");
		check("960x540".equals(settings.getProperty(RenderSurfaceSettings.SURFACE_MODE_PROPERTY_KEY))
			&& RenderSurfaceSettings.getMode() == renderMode, "Sizing changes preserve framebuffer settings");

		ChatPanelLayout standard = chat(1920, 1080, GameInterfaceSettings.Size.STANDARD, GameInterfaceSettings.TextSize.STANDARD);
		ChatPanelLayout larger = chat(1920, 1080, GameInterfaceSettings.Size.LARGE, GameInterfaceSettings.TextSize.STANDARD);
		ChatPanelLayout biggerText = chat(1920, 1080, GameInterfaceSettings.Size.STANDARD, GameInterfaceSettings.TextSize.EXTRA_LARGE);
		ChatPanelLayout compactWithBigText = chat(960, 540, GameInterfaceSettings.Size.COMPACT, GameInterfaceSettings.TextSize.EXTRA_LARGE);
		check(larger.width > standard.width && larger.scrollbarWidth > standard.scrollbarWidth
			&& larger.bodyFont == standard.bodyFont, "UI sizing adjusts geometry with the same font");
		check(biggerText.bodyFont > standard.bodyFont && biggerText.lineHeight > standard.lineHeight
			&& biggerText.scrollbarWidth == standard.scrollbarWidth, "Text sizing adjusts type and line metrics");
		check(compactWithBigText.entryHeight >= compactWithBigText.lineHeight
			&& compactWithBigText.recentBaseline(4) - compactWithBigText.lineHeight >= compactWithBigText.historyTop(),
			"Compact geometry fits large text");
	}

	private static void verifyInventoryBounds(int width, int height, GameInterfaceSettings.Size size,
											  GameInterfaceSettings.TextSize text, boolean bottomLauncher) {
		ChatPanelLayout chat = chat(width, height, size, text);
		SideToolbarLayout toolbar = new SideToolbarLayout(width, height - 12, bottomLauncher, size,
			chat.left + chat.width, chat.entryTop());
		int launcher = bottomLauncher ? toolbar.top : toolbar.panelTop();
		int chatRight = chat.left + chat.width;
		InventoryEquipmentLayout panel = new InventoryEquipmentLayout(width, height - 12, launcher, bottomLauncher,
			chatRight, chat.entryTop(), size, text, chat.lineHeight, chat.tabsHeight - size.pixels(8), 250, 40, true);

		for (int slot = 0; slot < 40; slot++) {
			int x = panel.cellX(slot);
			int y = panel.cellY(slot);
			check(panel.contains(x, y) && panel.inventorySlotAt(x, y) == slot, "Inventory slot top-left " + slot);
			check(panel.inventorySlotAt(x + panel.cellWidth - 1, y + panel.cellHeight - 1) == slot,
				"Inventory slot bottom-right " + slot);
		}
		check(panel.inventorySlotAt(panel.cellX(4) + panel.cellWidth, panel.cellY(0)) == -1, "Right edge excludes padding");
		check(panel.inventorySlotAt(panel.cellX(39), panel.cellY(39) + panel.cellHeight) == -1,
			"Grid ends before the tab strip");
		int[] equippableX = {98, 98, 98, 153, 43, 43, 98, 98, 43, 153, 153, 43};
		int[] equippableY = {5, 85, 125, 85, 85, 165, 165, 45, 45, 45, 165, 125};
		for (int slot = 0; slot < equippableX.length; slot++) {
			int x = panel.equipmentLeft + panel.equipmentPixels(equippableX[slot]);
			int y = panel.equipmentTop + panel.equipmentPixels(equippableY[slot]);
			check(panel.equipmentSlotAt(equippableX, equippableY, equippableX.length, x, y) == slot, "Equipment slot " + slot);
			int outsideX = x + panel.equipmentPixels(EquipmentSlotHoverTarget.SLOT_WIDTH);
			check(panel.equipmentSlotAt(equippableX, equippableY, equippableX.length, outsideX, y) != slot,
				"Equipment slot excludes right edge " + slot);
		}
		check(panel.tabAt(panel.left, panel.tabsTop) == InventoryEquipmentLayout.EQUIPMENT, "Equipment tab starts at left");
		check(panel.tabAt(panel.left + panel.width / 2, panel.tabsTop) == InventoryEquipmentLayout.INVENTORY,
			"Inventory tab starts at the tab boundary");
		check(panel.tabAt(panel.left, panel.bottom()) == -1 && !panel.contains(panel.left + panel.width, panel.top),
			"Sidebar excludes bottom and right edges");
		check(panel.inventorySlotAt(panel.left, panel.tabsTop) == -1, "Tab clicks cannot hit inventory slots");
		check(panel.left >= 0 && panel.top >= 0 && panel.left + panel.width <= width && panel.bottom() <= height,
			"Sidebar fits the screen");
		if (panel.left < chatRight) {
			check(panel.bottom() <= chat.entryTop(), "Sidebar does not cover chat entry or tabs");
		}
		if (bottomLauncher) {
			check(panel.bottom() < launcher, "Sidebar stays above the lower launcher");
		}
	}

	private static void verifyToolbarBounds(int width, int height, GameInterfaceSettings.Size size,
											GameInterfaceSettings.TextSize text, boolean bottomLauncher) {
		ChatPanelLayout chat = chat(width, height, size, text);
		SideToolbarLayout toolbar = new SideToolbarLayout(width, height - 12, bottomLauncher, size,
			chat.left + chat.width, chat.entryTop());
		check(toolbar.left >= 0 && toolbar.right < width && toolbar.top >= 0 && toolbar.bottom() < height,
			"Toolbar fits the viewport");
		check(toolbar.buttonSize >= SideToolbarLayout.ICON_SIZE + 4, "Original icons fit with padding");
		for (int index = 0; index < SideToolbarLayout.BUTTON_COUNT; index++) {
			int x = toolbar.leftOf(index);
			check(toolbar.buttonAt(x, toolbar.top) == index, "Button's visible leading edge " + index);
			check(toolbar.buttonAt(x + toolbar.buttonSize - 1, toolbar.bottom() - 1) == index,
				"Button's visible trailing edge " + index);
			check(toolbar.buttonAt(x + toolbar.buttonSize, toolbar.top) == -1, "Gap after button " + index);
		}
		check(toolbar.buttonAt(toolbar.left, toolbar.bottom()) == -1, "Toolbar stops at its bottom edge");
		check(toolbar.panelTop() - toolbar.bottom() == SideToolbarLayout.BANNER_HEIGHT + 1,
			"Original header strip fits between toolbar and sidebar");
		if (bottomLauncher && toolbar.left < chat.left + chat.width) {
			check(toolbar.bottom() < chat.entryTop(), "Lower toolbar fits above chat");
		}
	}

	private static void verifyAccomplishmentsBounds(int width, int height, GameInterfaceSettings.Size size,
												   GameInterfaceSettings.TextSize text, boolean bottomLauncher) {
		ChatPanelLayout chat = chat(width, height, size, text);
		int chatRight = chat.left + chat.width;
		SideToolbarLayout toolbar = new SideToolbarLayout(width, height - 12, bottomLauncher, size,
			chatRight, chat.entryTop());
		int anchor = bottomLauncher ? toolbar.top : toolbar.panelTop();
		String[] questNames = new String[60];
		questNames[1] = "An unusually long quest title with enough words to wrap at any supported panel width";
		for (int i = 3; i < questNames.length; i++) questNames[i] = "Quest " + i;
		int[] skills = {14, 19, 6, 8, 10, 4, 13};
		AccomplishmentsLayout panel = new AccomplishmentsLayout(width, anchor, bottomLauncher, chatRight, chat.entryTop(),
			size, text, chat.lineHeight, chat.tabsHeight - size.pixels(8), 150, 96, skills, questNames,
			value -> value.length() * 10);
		check(panel.tabAt(panel.left, panel.top) == AccomplishmentsLayout.STATS, "Stats tab first");
		check(panel.tabAt(panel.left + panel.width / 2, panel.top) == AccomplishmentsLayout.QUESTS, "Quests tab second");
		int firstSkillY = panel.listTop(AccomplishmentsLayout.STATS);
		check(panel.skillAt(panel.columnLeft(0), firstSkillY, 0) == skills[0], "First skill retains its ID");
		check(panel.skillAt(panel.columnLeft(1), firstSkillY, 0) == skills[panel.skillRows], "Second column uses the right ID");
		check(panel.skillAt(panel.columnLeft(0), firstSkillY, panel.rowHeight) == skills[1], "Scrolling selects the next skill");
		check(panel.skillAt(panel.scrollbarLeft(), firstSkillY, 0) == -1, "Scrollbar cannot activate a skill");
		int firstQuestY = panel.listTop(AccomplishmentsLayout.QUESTS);
		AccomplishmentsLayout.QuestRow firstQuest = panel.quests.get(0);
		check(firstQuest.lines.size() > 1, "Long quest name wraps");
		check(panel.questAt(panel.bodyLeft(), firstQuestY + firstQuest.height - 1, 0) == 1, "Wrapped row keeps its first ID");
		check(panel.questAt(panel.bodyLeft(), firstQuestY + firstQuest.height, 0) == 3, "Next row skips missing quest IDs");
		int maxScroll = panel.clampScroll(AccomplishmentsLayout.QUESTS, 100_000);
		check(maxScroll > 0 && panel.questAt(panel.bodyLeft(), panel.listBottom() - 1, maxScroll) == 59,
			"Last visible quest at maximum scroll");
		if (panel.left < chatRight) {
			check(panel.bottom <= chat.entryTop(), "Accomplishments stays clear of chat input");
		}
		if (bottomLauncher) {
			check(panel.bottom < anchor, "Accomplishments stays above the lower toolbar");
		}
	}

	private static void verifyAbilityBookSettings() {
		Properties props = new Properties();
		props.setProperty(SpellbookLayoutSettings.PROPERTY_KEY, "text");
		SpellbookLayoutSettings.loadFromClientSettings(props);
		for (AbilityBook book : AbilityBook.values()) {
			check(SpellbookLayoutSettings.mode(book) == SpellbookLayoutSettings.Mode.ICONS, "Icons default in " + book);
			check(AbilityBook.forTab(book.toolbarTab) == book, "Direct routing for " + book);
			check(SideToolbarLayout.tabAtIndex(SideToolbarLayout.indexOf(book.toolbarTab)) == book.toolbarTab,
				"Distinct toolbar button for " + book);
		}
		SpellbookLayoutSettings.cycleMode(AbilityBook.MAGE);
		SpellbookLayoutSettings.cycleMode(AbilityBook.PRAYER);
		SpellbookLayoutSettings.saveToClientSettings(props);
		SpellbookLayoutSettings.loadFromClientSettings(new Properties());
		SpellbookLayoutSettings.loadFromClientSettings(props);
		check(SpellbookLayoutSettings.mode(AbilityBook.MAGE) == SpellbookLayoutSettings.Mode.TEXT, "Mage preference reload");
		check(SpellbookLayoutSettings.mode(AbilityBook.PRAYER) == SpellbookLayoutSettings.Mode.TEXT, "Prayer preference reload");
		check(SpellbookLayoutSettings.mode(AbilityBook.CLERIC) == SpellbookLayoutSettings.Mode.ICONS, "Cleric remains independent");
		check(SpellbookLayoutSettings.mode(AbilityBook.SUMMONING) == SpellbookLayoutSettings.Mode.ICONS, "Summoning remains independent");
		check(SpellbookLayoutSettings.usesTextLayout(), "Classic combined setting is preserved");
	}

	private static void verifyAbilityBounds(int width, int height, GameInterfaceSettings.Size size,
											GameInterfaceSettings.TextSize text, boolean bottomLauncher) {
		ChatPanelLayout chat = chat(width, height, size, text);
		int chatRight = chat.left + chat.width;
		SideToolbarLayout toolbar = new SideToolbarLayout(width, height - 12, bottomLauncher, size, chatRight, chat.entryTop());
		int anchor = bottomLauncher ? toolbar.top : toolbar.panelTop();
		AbilityPanelLayout panel = new AbilityPanelLayout(width, anchor, bottomLauncher, chatRight, chat.entryTop(),
			size, text, chat.lineHeight, chat.tabsHeight - size.pixels(8), size.pixels(112));
		check(panel.onToggle(panel.toggleLeft, panel.toggleTop()), "First visible toggle pixel is clickable");
		check(!panel.onToggle(panel.toggleLeft, panel.toggleTop() + panel.toggleHeight()), "Toggle stops at its border");
		for (boolean textMode : new boolean[] {false, true}) {
			int last = panel.visibleRows(textMode) * panel.columns(textMode) - 1;
			check(panel.entryAt(panel.entryX(last, textMode), panel.entryY(last, textMode), 0, 100, textMode) == last,
				"Last visible entry maps to its action");
			check(panel.entryAt(panel.entryX(0, textMode), panel.entryY(0, textMode), 1, 100, textMode)
				== panel.columns(textMode), "Scroll advances the right number of entries");
			check(panel.entryAt(panel.scrollbarLeft(), panel.firstRowY, 0, 100, textMode) == -1,
				"Scrollbar can't activate a spell");
			check(panel.entryAt(panel.entryX(0, textMode), panel.lastRowY, 0, 100, textMode) == -1,
				"Requirements area can't activate a spell");
		}
		int iconX = panel.entryX(0, false);
		check(panel.entryAt(iconX + panel.iconSize, panel.firstRowY, 0, 100, false) == -1, "Grid gaps aren't actions");
		if (panel.left < chatRight) {
			check(panel.bottom <= chat.entryTop(), "Ability book fits above chat");
		}
		if (bottomLauncher) check(panel.bottom < anchor, "Ability book fits above the lower toolbar");
	}

	private static void verifyChatBounds(ChatPanelLayout chat, int width) {
		for (int tab = 0; tab < ChatPanelLayout.TAB_COUNT; tab++) {
			check(chat.tabAt(chat.tabBoundary(tab), chat.tabsTop()) == tab, "Tab starts " + tab);
			check(chat.tabAt(chat.tabBoundary(tab + 1) - 1, chat.bottom - 1) == tab, "Tab ends " + tab);
		}
		int right = chat.tabBoundary(ChatPanelLayout.TAB_COUNT);
		check(right <= width - chat.left, "Panel fits the frame");
		check(chat.tabAt(right, chat.tabsTop()) == -1, "Tabs exclude right margin");
		check(chat.tabAt(chat.left, chat.tabsTop() - 1) == -1, "Entry and tabs don't overlap");
		check(chat.tabAt(chat.left, chat.bottom) == -1, "Tabs exclude bottom margin");
		check(chat.contains(chat.left, chat.entryTop(), false), "Entry owns pointer");
		check(!chat.contains(chat.left, chat.historyTop(), false), "Empty history leaves world input");
		check(chat.contains(chat.left, chat.historyTop(), true), "Visible history owns pointer");
		check(!chat.contains(right, chat.historyTop(), true), "Outside the panel leaves world input");
		check(!chat.containsHistory(chat.left, chat.entryTop()), "History excludes entry");
	}

	private static void check(boolean condition, String description) {
		if (!condition) {
			throw new AssertionError(description);
		}
	}
}
