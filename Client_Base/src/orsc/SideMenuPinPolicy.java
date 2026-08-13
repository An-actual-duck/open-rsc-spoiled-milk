package orsc;

/** Pure state transitions for detached desktop side-menu panels. */
final class SideMenuPinPolicy {
	private SideMenuPinPolicy() {
	}

	static int visibleTab(int transientTab, int pinnedTab) {
		return transientTab != 0 ? transientTab : pinnedTab;
	}

	static int pinnedTabAfterPrimaryClick(int currentPinnedTab, int requestedTab) {
		return currentPinnedTab == requestedTab ? 0 : requestedTab;
	}

	static int transientTabAfterIconInteraction(int pinnedTab, int requestedTab,
		boolean primaryClick) {
		if (primaryClick || pinnedTab == requestedTab) {
			return 0;
		}
		return requestedTab;
	}

	static int transientTabAfterPointerLeaves() {
		return 0;
	}
}
