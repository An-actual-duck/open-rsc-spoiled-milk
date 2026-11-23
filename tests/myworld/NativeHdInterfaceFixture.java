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
