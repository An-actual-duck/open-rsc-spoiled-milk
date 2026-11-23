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
