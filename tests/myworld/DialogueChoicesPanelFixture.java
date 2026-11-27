package orsc;

import java.util.Arrays;

/** Exercises wrapped response bounds and preserves server IDs through clipping and scrolling. */
public final class DialogueChoicesPanelFixture {
	public static void main(String[] args) {
		DialogueChoicesPanel panel = new DialogueChoicesPanel(1920, 870, GameInterfaceSettings.Size.STANDARD,
			GameInterfaceSettings.TextSize.STANDARD, 24, 19, true,
			new String[] {"First response", "Second response", "Third response"}, 3, value -> value.length() * 10);
		check(panel.optionAt(960, 755) == 0, "First row retains index zero");
		check(panel.optionAt(960, 792) == 1, "Second row retains index one");
		check(panel.activate(960, 830) == 2, "Third row retains index two");
		check(panel.activate(100, 830) == DialogueChoicesPanel.CANCEL, "Outside click cancels");

		String[] responses = new String[20];
		Arrays.fill(responses, "A longer response with enough text to wrap onto another line at this interface size.");
		DialogueChoicesPanel compact = new DialogueChoicesPanel(800, 245, GameInterfaceSettings.Size.EXTRA_LARGE,
			GameInterfaceSettings.TextSize.EXTRA_LARGE, 29, 24, true, responses, 20, value -> value.length() * 14);
		check(compact.optionAt(400, 82) == 0 && compact.optionAt(400, 114) == 0,
			"All wrapped lines are in the same response hit target");
		check(compact.activate(749, 210) == DialogueChoicesPanel.NO_SELECTION, "Scrollbar owns its click");
		compact.scrollRows(100);
		check(compact.activate(400, 220) == 19, "Last response is reachable at largest settings");
		compact.scrollRows(-100);
		check(compact.activate(400, 82) == 0, "Scrolling back restores the first response");
		DialogueChoicesPanel conversation = new DialogueChoicesPanel(1920, 870, GameInterfaceSettings.Size.STANDARD,
			GameInterfaceSettings.TextSize.STANDARD, 24, 19, true,
			new String[] {"First response", "Second response", "Third response"}, 3,
			value -> value.length() * 10, "Banker", "Good day, how may I help you?");
		check(conversation.activate(655, 700) == DialogueChoicesPanel.NO_SELECTION, "Clicking a portrait keeps choices open");
		check(conversation.activate(960, 792) == 1, "Speech keeps the second choice at the existing position");
		check(conversation.activate(1530, 792) == DialogueChoicesPanel.CANCEL, "Outside the portrait panel still cancels");

		String lengthySpeech = String.join(" ", java.util.Collections.nCopies(120, "Long line"));
		DialogueChoicesPanel overflowing = new DialogueChoicesPanel(960, 380, GameInterfaceSettings.Size.EXTRA_LARGE,
			GameInterfaceSettings.TextSize.EXTRA_LARGE, 29, 24, true, responses, responses.length,
			value -> value.length() * 12, "Banker", lengthySpeech);
		check(overflowing.optionAt(480, 361) == 0, "A long line leaves space for responses at 540p");
		overflowing.scrollRows(200);
		check(overflowing.activate(480, 361) == 19, "The final response remains reachable below speech");
		System.out.println("PASS dialogue wrapping, scrolling, and original response IDs");
	}

	private static void check(boolean condition, String description) {
		if (!condition) throw new AssertionError(description);
	}
}
