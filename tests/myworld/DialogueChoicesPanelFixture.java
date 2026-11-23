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
		check(compact.activate(749, 210) == DialogueChoicesPanel.SCROLLED, "Scrollbar owns its click");
		compact.scrollRows(100);
		check(compact.activate(400, 220) == 19, "Last response is reachable at largest settings");
		compact.scrollRows(-100);
		check(compact.activate(400, 82) == 0, "Scrolling back restores the first response");
		System.out.println("PASS dialogue wrapping, scrolling, and original response IDs");
	}

	private static void check(boolean condition, String description) {
		if (!condition) throw new AssertionError(description);
	}
}
