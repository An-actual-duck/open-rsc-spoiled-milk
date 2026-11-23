package orsc;

import java.util.Properties;

/** Local preferences for component geometry and Classic bitmap typography. */
public final class GameInterfaceSettings {
	static final String UI_SIZE_KEY = "game_ui_size";
	static final String TEXT_SIZE_KEY = "game_ui_text_size";

	private static Size size = Size.STANDARD;
	private static TextSize textSize = TextSize.STANDARD;

	private GameInterfaceSettings() {
	}

	public static Size size() {
		return size;
	}

	public static TextSize textSize() {
		return textSize;
	}

	static void cycleSize() {
		Size[] choices = Size.values();
		size = choices[(size.ordinal() + 1) % choices.length];
	}

	static void cycleTextSize() {
		TextSize[] choices = TextSize.values();
		textSize = choices[(textSize.ordinal() + 1) % choices.length];
	}

	static void loadFromClientSettings(Properties properties) {
		size = Size.STANDARD;
		for (Size choice : Size.values()) {
			if (Integer.toString(choice.percent).equals(properties.getProperty(UI_SIZE_KEY))) {
				size = choice;
			}
		}
		textSize = TextSize.STANDARD;
		for (TextSize choice : TextSize.values()) {
			if (choice.id.equals(properties.getProperty(TEXT_SIZE_KEY))) {
				textSize = choice;
			}
		}
	}

	static void saveToClientSettings(Properties properties) {
		properties.setProperty(UI_SIZE_KEY, Integer.toString(size.percent));
		properties.setProperty(TEXT_SIZE_KEY, textSize.id);
	}

	public enum Size {
		COMPACT(75), STANDARD(100), LARGE(125), EXTRA_LARGE(150);

		final int percent;

		Size(int percent) {
			this.percent = percent;
		}

		public int pixels(int baseline) {
			return (baseline * percent + 50) / 100;
		}
	}

	public enum TextSize {
		SMALL("small", "Small", 4, 3),
		MEDIUM("medium", "Medium", 5, 4),
		STANDARD("standard", "Large", 6, 5),
		EXTRA_LARGE("extra-large", "Extra large", 7, 6);

		final String id;
		final String label;
		public final int bodyFont;
		public final int labelFont;

		TextSize(String id, String label, int bodyFont, int labelFont) {
			this.id = id;
			this.label = label;
			this.bodyFont = bodyFont;
			this.labelFont = labelFont;
		}
	}
}
