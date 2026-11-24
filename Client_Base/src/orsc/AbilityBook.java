package orsc;

/** The four ability launchers used by the desktop interface preview. */
enum AbilityBook {
	MAGE(Config.MAGIC_AND_PRAYER_TAB, "Mage", "M"),
	CLERIC(7, "Cleric", "C"),
	PRAYER(8, "Prayer", "P"),
	SUMMONING(9, "Summoning", "S");

	final int toolbarTab;
	final String title;
	final String letter;

	AbilityBook(int toolbarTab, String title, String letter) {
		this.toolbarTab = toolbarTab;
		this.title = title;
		this.letter = letter;
	}

	private static final AbilityBook[] BOOKS = values();

	static AbilityBook forTab(int tab) {
		for (AbilityBook book : BOOKS) {
			if (book.toolbarTab == tab) return book;
		}
		return null;
	}
}
