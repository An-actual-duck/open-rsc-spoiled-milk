package orsc;

import java.io.IOException;
import java.util.Properties;
import java.util.Collections;

/** Run with and without -Dspoiledmilk.nativeHdInterface=true in separate JVMs. */
public final class NativeHdInterfaceFixture {
	public static void main(String[] args) throws IOException {
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
		verifyGraphicsTuningDrag();
		verifySessionNoticeWrapping();
		verifyMinimapLandmarks();
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
				verifySocialBounds(1920, 1080, size, text, false);
				verifySocialBounds(960, 540, size, text, false);
				verifySocialBounds(960, 540, size, text, true);
				verifySettingsBounds(1920, 1080, size, text, false);
				verifySettingsBounds(960, 540, size, text, false);
				verifySettingsBounds(960, 540, size, text, true);
				verifyContextMenu(1920, 1068, size, text);
				verifyContextMenu(960, 528, size, text);
				verifyMinimapFrame(1920, 1080, size, text, false);
				verifyMinimapFrame(960, 540, size, text, false);
				verifyMinimapFrame(960, 540, size, text, true);
				verifyWorldMapFrame(1920, 1080, size, text);
				verifyWorldMapFrame(960, 540, size, text);
				verifyBankPinLayout(1920, 1080, size, text);
				verifyBankPinLayout(960, 540, size, text);
			}
		}
		System.out.println("PASS native HD interface preview=" + preview);
	}

	private static void verifyBankPinLayout(int width, int height,
			GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text) {
		int[] fontHeights = {12, 14, 14, 15, 15, 19, 24, 29};
		int bottom = chat(width, height, size, text).entryTop();
		BankPinWindow.Layout layout = new BankPinWindow.Layout(width, bottom, size, text,
			fontHeights[text.bodyFont], fontHeights[text.labelFont]);
		check(layout.frame.x >= 0 && layout.frame.x + layout.frame.width <= width
			&& layout.frame.y >= 0 && layout.frame.y + layout.frame.height <= bottom,
			"Bank PIN frame fits above chat");
		check(layout.keys.length == 10 && layout.slots.length == 4, "Bank PIN keypad and display count");
		for (int digit = 0; digit < layout.keys.length; digit++) {
			java.awt.Rectangle key = layout.keys[digit];
			check(layout.frame.contains(key) && !layout.cancel.intersects(key), "Bank PIN key fits inside frame");
			check(layout.digitAt(key.x + key.width / 2, key.y + key.height / 2) == digit,
				"Bank PIN key selects its displayed digit");
			check(layout.digitAt(key.x + key.width, key.y) < 0, "Bank PIN keypad gutters are inactive");
		}
		check(layout.frame.contains(layout.cancel) && layout.digitAt(layout.cancel.x, layout.cancel.y) < 0,
			"Bank PIN Cancel stays separate from the keypad");
	}

	private static void verifySessionNoticeWrapping() {
		java.util.List<String> lines = SessionNoticeWindow.wrap("@yel@alpha beta%%@whi@omega", 5, String::length);
		check(lines.size() == 4 && lines.get(0).equals("@yel@alpha") && lines.get(1).equals("@yel@beta")
			&& lines.get(2).isEmpty() && lines.get(3).endsWith("@whi@omega"),
			"Server notice wrapping preserves colors and explicit paragraph breaks");
		lines = SessionNoticeWindow.wrap("~120~a bcdefg\r\nz", 4, String::length);
		check(lines.equals(java.util.Arrays.asList("a", "bcde", "fg", "z")),
			"Long notice words wrap without dropping characters or carrying legacy pixel offsets");
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
		int originalButtonSize = Math.max(SideToolbarLayout.ICON_SIZE + 4, size.pixels(42));
		check(toolbar.buttonSize == (bottomLauncher ? originalButtonSize : (originalButtonSize * 5 + 2) / 4),
			"Upper toolbar increases by one quarter at each UI scale");
		check(toolbar.iconSize == (bottomLauncher ? 32 : 40) && toolbar.buttonSize >= toolbar.iconSize + 4,
			"Classic artwork fits with padding");
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

	private static void verifySocialBounds(int width, int height, GameInterfaceSettings.Size size,
											GameInterfaceSettings.TextSize text, boolean bottomLauncher) {
		ChatPanelLayout chat = chat(width, height, size, text);
		int chatRight = chat.left + chat.width;
		SideToolbarLayout toolbar = new SideToolbarLayout(width, height - 12, bottomLauncher, size, chatRight, chat.entryTop());
		int anchor = bottomLauncher ? toolbar.top : toolbar.panelTop();
		for (boolean clans : new boolean[]{true, false}) {
			SocialPanelLayout panel = new SocialPanelLayout(width, anchor, bottomLauncher, chatRight, chat.entryTop(),
				size, text, clans, chat.lineHeight, chat.tabsHeight - size.pixels(8),
				label -> label.length() * (text == GameInterfaceSettings.TextSize.EXTRA_LARGE ? 12 : 8));
			check(panel.left >= 0 && panel.right <= width, "Social panel fits the screen");
			if (panel.left < chatRight) check(panel.bottom <= chat.entryTop(), "Social panel fits above chat");
			if (bottomLauncher) check(panel.bottom < anchor, "Social panel fits above the lower toolbar");
			for (int tab = SocialPanelLayout.FRIENDS; tab <= SocialPanelLayout.ONLINE; tab++) {
				int left = panel.tabLeft(tab);
				int tabWidth = panel.tabWidth(tab);
				if (tab == SocialPanelLayout.CLAN && !clans) {
					check(tabWidth == 0, "Disabled clans leave no dead tab space");
				} else {
					check(panel.tabAt(left, panel.top) == tab && panel.tabAt(left + tabWidth - 1, panel.top) == tab,
						"Social tab owns both visible edges");
				}
			}
			check(panel.tabAt(panel.left, panel.top + panel.tabHeight) == -1, "Social tabs end at the content");
			int listTop = panel.listTop(SocialPanelLayout.CLAN, true);
			int rows = panel.visibleRows(SocialPanelLayout.CLAN, listTop);
			check(rows > 0, "Clan details leave room for members");
			check(panel.rowAt(SocialPanelLayout.CLAN, panel.bodyLeft(), listTop + (rows - 1) * panel.rowHeight, listTop, 2, 100) == rows + 1,
				"Social hit testing honors scroll and last full row");
			check(panel.rowAt(SocialPanelLayout.CLAN, panel.scrollbarLeft(), listTop, listTop, 2, 100) == -1, "Scrollbar excludes row actions");
			check(panel.rowAt(SocialPanelLayout.CLAN, panel.bodyLeft(), panel.actionTop(), listTop, 2, 100) == -1,
				"Footer excludes row actions");
			int onlineTop = panel.listTop(SocialPanelLayout.ONLINE, false);
			int onlineRows = panel.visibleRows(SocialPanelLayout.ONLINE, onlineTop);
			check(onlineRows > 0 && panel.listBottom(SocialPanelLayout.ONLINE) > panel.actionTop(),
				"Online players can use the space below the other tabs' lists");
			int lastOnlineY = onlineTop + (onlineRows - 1) * panel.rowHeight;
			check(panel.rowAt(SocialPanelLayout.ONLINE, panel.bodyLeft(), lastOnlineY, onlineTop, onlineRows, onlineRows * 2)
				== onlineRows * 2 - 1, "The online list can select its last scrolled entry");
		}
	}

	private static void verifySettingsBounds(int width, int height, GameInterfaceSettings.Size size,
											  GameInterfaceSettings.TextSize text, boolean bottomLauncher) {
		ChatPanelLayout chat = chat(width, height, size, text);
		int chatRight = chat.left + chat.width;
		SideToolbarLayout toolbar = new SideToolbarLayout(width, height - 12, bottomLauncher, size, chatRight, chat.entryTop());
		int anchor = bottomLauncher ? toolbar.top : toolbar.panelTop();
		SettingsPanelLayout panel = new SettingsPanelLayout(width, anchor, bottomLauncher, chatRight, chat.entryTop(),
			size, text, chat.lineHeight, chat.tabsHeight - size.pixels(8));
		for (int tab = 0; tab < SettingsPanelLayout.TAB_COUNT; tab++) {
			check(panel.tabAt(panel.tabLeft(tab), panel.top) == tab, "Settings tab left edge");
			check(panel.tabAt(panel.tabLeft(tab + 1) - 1, panel.top) == tab, "Settings tab right edge");
		}
		check(panel.sliderLeft() < panel.sliderRight(), "Slider has a usable track");
		check(panel.listHeight() >= panel.sliderHeight, "Slider fits in the scroll viewport");
		check(panel.overSlider(panel.sliderLeft(), panel.sliderY(panel.listTop()), panel.listTop()), "Track is clickable");
		check(!panel.overSlider(panel.sliderLeft(), panel.listTop(), panel.listTop()), "Slider label has no pointer action");
		check(!panel.overList(panel.listLeft(), panel.logoutTop()), "Log out stays outside scroll viewport");
		if (panel.left < chatRight) check(panel.bottom <= chat.entryTop(), "Settings fit above chat");
		if (bottomLauncher) check(panel.bottom < anchor, "Settings fit above the lower toolbar");
	}

	private static void verifyGraphicsTuningDrag() {
		RendererSettingsPanel.State state = RendererSettingsPanel.State.capture(true,
			ScaledWindow.ScalingAlgorithm.INTEGER_SCALING, 1, Collections.singletonList(1f),
			false, false, false, false);
		java.util.List<RendererSettingsPanel.Row> rows = RendererSettingsPanel.previewRows(state);
		check(rows.get(1).label.contains("Resolution") && rows.get(2).label.contains("UI size")
			&& rows.get(3).label.contains("Text size"), "Display sizing options are grouped together");
		int sliders = 0;
		for (RendererSettingsPanel.Row row : rows) {
			if (RendererSettingsPanel.isRendererTuningSlider(row.action)) {
				sliders++;
				check(!row.label.contains("[") && !row.label.startsWith("@"), "Preview slider has a plain label");
			}
		}
		check(sliders == 6, "All image tuning sliders are available");

		int before = RendererColorDiagnosticSettings.getGammaLevel();
		RendererColorDiagnosticSettings.setGammaLevel(10);
		RendererSettingsPanel.TuningDrag drag = new RendererSettingsPanel.TuningDrag();
		int[] committed = {0, 0};
		RendererSettingsPanel.Actions actions = new RendererSettingsPanel.Actions() {
			@Override public void perform(RendererSettingsPanel.Action action) { throw new AssertionError(action); }
			@Override public void rendererTuningChanged(String control, int level, float value) {
				check("gamma".equals(control), "Drag retains the starting setting");
				committed[0]++;
				committed[1] = level;
			}
		};
		drag.begin(RendererSettingsPanel.GAMMA_SLIDER, 100, 290, 135);
		check(RendererColorDiagnosticSettings.getGammaLevel() == 5, "Track click rounds to the nearest existing level");
		drag.move(400);
		check(RendererColorDiagnosticSettings.getGammaLevel() == 20, "Dragging past the track clamps the maximum");
		drag.move(-4);
		check(RendererColorDiagnosticSettings.getGammaLevel() == 1 && committed[0] == 0,
			"Dragging updates live before saving");
		drag.finish(actions);
		check(committed[0] == 1 && committed[1] == 1 && !drag.isActive(), "Release saves only the final level");
		drag.begin(RendererSettingsPanel.GAMMA_SLIDER, 100, 290, 100);
		drag.move(200);
		drag.move(100);
		drag.finish(actions);
		check(committed[0] == 1, "Returning to the initial value skips persistence");
		RendererColorDiagnosticSettings.setGammaLevel(before);
	}

	private static void verifyContextMenu(int width, int height, GameInterfaceSettings.Size size,
										  GameInterfaceSettings.TextSize text) {
		String[] actions = new String[20];
		for (int index = 0; index < actions.length; index++) actions[index] = "Action " + index;
		int[] fontHeight = {12, 14, 14, 15, 15, 19, 24, 29};
		ContextMenuLayout menu = new ContextMenuLayout(width, height, width - 1, height - 1, size, text,
			fontHeight[text.bodyFont], fontHeight[text.labelFont], "Choose option", actions,
			label -> label.length() * 10, label -> label.length() * 8);
		check(menu.left >= 0 && menu.left + menu.width <= width, "Context menu fits the right edge");
		check(menu.top >= 0 && menu.top + menu.height <= height, "Context menu fits the bottom edge");
		check(menu.keepsOpenAt(width - 1, height - 1), "Opening at an edge keeps the menu open");
		check(!menu.keepsOpenAt(0, 0), "Moving away dismisses the menu");
		int x = menu.left + 1;
		check(menu.itemAt(x, menu.rowsTop() - 1, 0) == -1, "The menu heading is not an action");
		check(menu.itemAt(x, menu.rowsTop(), 0) == 0, "The first drawn row maps to the first action");
		check(menu.itemAt(x, menu.rowTop(1), 0) == 1, "A row starts where the previous row ends");
		check(menu.itemAt(x, menu.rowsTop() + menu.rowsHeight(), 0) == -1, "The frame below the items is not an action");
		int finalY = menu.rowsTop() + menu.rowsHeight() - 1;
		if (menu.maxScroll() > 0) {
			check(menu.overScrollbar(menu.scrollbarLeft(), finalY), "Long lists have a scrollbar");
			check(menu.itemAt(menu.scrollbarLeft(), finalY, 0) == -1, "The scrollbar cannot select an action");
			check(menu.scrollFor(finalY) == menu.maxScroll(), "The scrollbar can reach the end");
		}
		int last = menu.itemAt(x, finalY, menu.clampScroll(actions.length));
		check(last == actions.length - 1 && menu.label(last).equals(actions[last]), "Scrolling preserves the original action indices");
		check(menu.clampScroll(-10) == 0, "Scroll stays inside the action list");

		String longLabel = "Use @lre@" + String.join("", Collections.nCopies(30, "Very long target "));
		java.util.function.ToIntFunction<String> measure = label -> label.replaceAll("@...@", "").length() * 9;
		ContextMenuLayout narrow = new ContextMenuLayout(512, 346, 0, 0, size, text,
			fontHeight[text.bodyFont], fontHeight[text.labelFont], "Choose option",
			new String[]{longLabel}, measure, measure);
		check(narrow.label(0).startsWith("Use @lre@") && narrow.label(0).endsWith("..."),
			"A long actor label keeps the action and its colour");
		check(measure.applyAsInt(narrow.label(0)) <= narrow.width - 2 * narrow.padding, "A long label fits the frame");
		check(narrow.keepsOpenAt(0, 0), "Opening at the top-left remains possible");
	}

	private static void verifyMinimapLandmarks() throws IOException {
		MinimapAtlas atlas = MinimapAtlas.open(0);
		boolean lumbridgeBank = false;
		boolean varrockQuest = false;
		boolean alKharidTanner = false;
		boolean shifted = false;
		check(atlas != null, "Surface minimap atlas is packaged");
		for (int index = 0; index < atlas.landmarks.length; index++) {
			MinimapAtlas.Landmark point = atlas.landmarks[index];
			lumbridgeBank |= point.x == 111 && point.y == 650 && point.icon == MinimapMarkerIcon.BANK;
			varrockQuest |= point.x == 136 && point.y == 511 && point.icon == MinimapMarkerIcon.QUEST;
			alKharidTanner |= point.x == 84 && point.y == 676 && point.icon == MinimapMarkerIcon.LEATHER;
			shifted |= point.offsetX != 0 || point.offsetY != 0;
			check(!point.label.isEmpty(), "Landmark has a hover label");
			for (int prior = 0; prior < index; prior++) {
				MinimapAtlas.Landmark other = atlas.landmarks[prior];
				int dx = point.x + point.offsetX - other.x - other.offsetX;
				int dy = point.y + point.offsetY - other.y - other.offsetY;
				check(Math.max(Math.abs(dx), Math.abs(dy)) >= 4, "Landmark icon tiles are spaced apart");
			}
		}
		check(lumbridgeBank && varrockQuest && alKharidTanner, "Atlas contains landmarks in all three preview towns");
		check(shifted, "Atlas contains offsets to keep neighbouring icons visible");
		int sectorSize = MinimapAtlas.SECTOR_SIZE;
		check(atlas.sector(111 / sectorSize, 650 / sectorSize) != null, "Landmarks preserve the terrain sector data");
		assertLandmark(atlas, 325, 490, MinimapMarkerIcon.QUEST);
		assertLandmark(atlas, 439, 491, MinimapMarkerIcon.BANK);
		assertLandmark(MinimapAtlas.open(1), 717, 465, MinimapMarkerIcon.CLOTHING);
		assertLandmark(MinimapAtlas.open(-2), 320, 592, MinimapMarkerIcon.LAVA_FORGE);
		assertLandmark(MinimapAtlas.open(10), 174, 691, MinimapMarkerIcon.BANK);
	}

	private static void assertLandmark(MinimapAtlas atlas, int x, int y, MinimapMarkerIcon icon) {
		check(atlas != null, "Landmark's level atlas is packaged");
		check(atlas.sector(Math.floorDiv(x, MinimapAtlas.SECTOR_SIZE), Math.floorDiv(y, MinimapAtlas.SECTOR_SIZE)) != null,
			"Landmark has terrain on the same floor");
		for (MinimapAtlas.Landmark point : atlas.landmarks) {
			if (point.x == x && point.y == y && point.icon == icon) return;
		}
		throw new AssertionError("Could not find " + icon + " at " + x + ", " + y);
	}

	private static void verifyMinimapFrame(int width, int height, GameInterfaceSettings.Size size,
										   GameInterfaceSettings.TextSize text, boolean floating) {
		ChatPanelLayout chat = chat(width, height, size, text);
		int chatRight = chat.left + chat.width;
		SideToolbarLayout toolbar = new SideToolbarLayout(width, height - 12, floating, size, chatRight, chat.entryTop());
		int anchor = floating ? toolbar.top : toolbar.panelTop();
		int[] fontHeight = {12, 14, 14, 15, 15, 19, 24, 29};
		MinimapFrameLayout frame = new MinimapFrameLayout(width, height - 12, anchor, floating, chatRight,
			chat.entryTop(), size, text, fontHeight[text.labelFont]);
		check(frame.left >= 0 && frame.left + frame.width < width, "Map frame fits the right edge");
		check(frame.width >= size.pixels(400), "Map frame is comparable in width to the other sidebar panels");
		check(frame.viewport.pixelsPerTile == size.pixels(6), "Map zoom follows the UI size");
		check(frame.top == (floating ? frame.padding : toolbar.panelTop()), "Map frame follows its existing anchor");
		check(frame.viewport.top > frame.top + frame.headerHeight, "Map viewport sits below the header");
		check(frame.contains(frame.expand.x, frame.expand.y), "Fullscreen button fits in the minimap");
		check(frame.expand.y + frame.expand.height <= frame.viewport.top, "Expand button stays out of click-to-walk");
		check(frame.viewport.bottom > frame.viewport.top && frame.viewport.bottom == frame.bottom - frame.padding,
			"Map fills the frame down to its bottom padding");
		check(frame.viewport.contains(frame.viewport.left, frame.viewport.top), "Visible map corner accepts navigation");
		check(frame.viewport.contains(frame.viewport.left, frame.viewport.bottom - 1), "The bottom of the visible map accepts navigation");
		check(!frame.viewport.contains(frame.viewport.left, frame.viewport.bottom), "Bottom padding stays out of map navigation");
		if (floating) check(frame.bottom < toolbar.top, "Floating frame leaves the lower toolbar reachable");
		if (frame.left < chatRight) check(frame.bottom <= chat.entryTop(), "Frame leaves the chat controls reachable");
		check(frame.contains(frame.left + frame.width - 1, frame.bottom - 1), "The visible frame owns input");
		check(!frame.contains(frame.left + frame.width, frame.bottom - 1), "Input remains available beside the frame");
		check(!frame.contains(frame.left, frame.bottom), "Input remains available below the frame");
	}

	private static void verifyWorldMapFrame(int width, int height, GameInterfaceSettings.Size size,
											GameInterfaceSettings.TextSize text) {
		int[] fontHeight = {12, 14, 14, 15, 15, 19, 24, 29};
		WorldMapWindow.Layout frame = new WorldMapWindow.Layout(width, height, size, text, fontHeight[text.labelFont]);
		check(frame.close.x + frame.close.width < width && frame.close.y + frame.close.height <= frame.headerHeight,
			"Fullscreen map can close from the top-right");
		check(frame.viewport.right < frame.key.x && frame.viewport.bottom <= height, "Map and key fit side by side");
		check(frame.viewport.contains(frame.viewport.left, frame.viewport.top), "Map canvas admits drag");
		check(!frame.viewport.contains(frame.key.x, frame.key.y), "Key stays out of drag bounds");
		check(!frame.viewport.contains(frame.close.x, frame.close.y), "Close stays out of drag bounds");
		check(frame.visibleKeyRows > 0 && frame.scrollbar.y + frame.scrollbar.height <= frame.key.y + frame.key.height,
			"Scrollable map key stays inside the panel");
		int entries = MinimapMarkerIcon.values().length + 1;
		check(frame.clampKeyOffset(-1) == 0 && frame.clampKeyOffset(entries) + frame.visibleKeyRows >= entries,
			"Key can show its first and last markers");
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
