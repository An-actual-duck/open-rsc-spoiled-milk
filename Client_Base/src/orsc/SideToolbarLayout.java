package orsc;

/** Screen-space dimensions shared by the toolbar's drawing and pointer handling. */
final class SideToolbarLayout {
	private static final int[] TABS = {
		Config.OPTIONS_TAB, Config.FRIENDS_TAB, AbilityBook.MAGE.toolbarTab, AbilityBook.CLERIC.toolbarTab,
		AbilityBook.PRAYER.toolbarTab, AbilityBook.SUMMONING.toolbarTab, Config.SKILLS_AND_QUESTS_TAB,
		Config.MINIMAP_AND_COMPASS_TAB, Config.INVENTORY_TAB
	};
	static final int BUTTON_COUNT = TABS.length;
	static final int ICON_SIZE = 32;
	static final int BANNER_HEIGHT = 10;
	final int left;
	final int top;
	final int buttonSize;
	final int gap;
	final int width;
	final int right;
	private final int pitch;
	private final int surfaceWidth;
	private final int gameHeight;
	private final boolean bottomLauncher;
	private final GameInterfaceSettings.Size size;
	private final int chatRight;
	private final int chatEntryTop;

	SideToolbarLayout(int surfaceWidth, int gameHeight, boolean bottomLauncher, GameInterfaceSettings.Size size,
					  int chatRight, int chatEntryTop) {
		this.surfaceWidth = surfaceWidth;
		this.gameHeight = gameHeight;
		this.bottomLauncher = bottomLauncher;
		this.size = size;
		this.chatRight = chatRight;
		this.chatEntryTop = chatEntryTop;
		gap = size.pixels(3);
		int maxSize = (surfaceWidth - size.pixels(6) - gap * (BUTTON_COUNT - 1)) / BUTTON_COUNT;
		buttonSize = Math.min(maxSize, Math.max(ICON_SIZE + 4, size.pixels(42)));
		pitch = buttonSize + gap;
		width = pitch * BUTTON_COUNT - gap;
		right = surfaceWidth - size.pixels(3);
		left = right - width;
		int bottom = gameHeight - 10;
		if (bottomLauncher && left < chatRight) {
			bottom = Math.min(bottom, chatEntryTop - gap);
		}
		top = bottomLauncher ? bottom - buttonSize : 3;
	}

	boolean matches(int surfaceWidth, int gameHeight, boolean bottomLauncher, GameInterfaceSettings.Size size,
					int chatRight, int chatEntryTop) {
		return this.surfaceWidth == surfaceWidth && this.gameHeight == gameHeight && this.bottomLauncher == bottomLauncher
			&& this.size == size && this.chatRight == chatRight && this.chatEntryTop == chatEntryTop;
	}

	static int tabAtIndex(int index) {
		return TABS[index];
	}

	static int indexOf(int tab) {
		for (int index = 0; index < TABS.length; index++) {
			if (TABS[index] == tab) return index;
		}
		return -1;
	}

	int leftOf(int index) {
		return left + index * pitch;
	}

	int bottom() {
		return top + buttonSize;
	}

	int panelTop() {
		return bottom() + BANNER_HEIGHT + 1;
	}

	boolean contains(int x, int y) {
		return x >= left && x < right && y >= top && y < bottom();
	}

	int buttonAt(int x, int y) {
		if (!contains(x, y)) return -1;
		int distance = x - left;
		return distance % pitch < buttonSize ? distance / pitch : -1;
	}
}
