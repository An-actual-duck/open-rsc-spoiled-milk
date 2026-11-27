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
	static final int HEADER_HEIGHT = 23;
	final int left;
	final int top;
	final int buttonSize;
	final int iconSize;
	final int headerHeight;
	final int width;
	final int right;
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
		int maxSize = (surfaceWidth - size.pixels(6)) / BUTTON_COUNT;
		int baseButton = bottomLauncher ? 42 : 53;
		int baseIcon = bottomLauncher ? ICON_SIZE : 40;
		buttonSize = Math.min(maxSize, size.pixels(baseButton));
		iconSize = Math.max(1, Math.min(buttonSize - size.pixels(4), size.pixels(baseIcon)));
		headerHeight = size.pixels(HEADER_HEIGHT);
		width = buttonSize * BUTTON_COUNT;
		right = surfaceWidth - size.pixels(3);
		left = right - width;
		int bottom = gameHeight - 10;
		if (bottomLauncher && left < chatRight) {
			bottom = Math.min(bottom, chatEntryTop - size.pixels(3));
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
		return left + index * buttonSize;
	}

	int bottom() {
		return top + buttonSize;
	}

	int panelTop() {
		return bottom() + headerHeight;
	}

	boolean contains(int x, int y) {
		return x >= left && x < right && y >= top && y < bottom();
	}

	int buttonAt(int x, int y) {
		return contains(x, y) ? (x - left) / buttonSize : -1;
	}
}
