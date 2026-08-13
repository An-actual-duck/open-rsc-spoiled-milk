package orsc;

public final class SideMenuPinPolicyFixture {
	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	public static void main(String[] args) {
		int pinned = 0;
		int transientTab = 0;

		pinned = SideMenuPinPolicy.pinnedTabAfterPrimaryClick(pinned, 1);
		transientTab = SideMenuPinPolicy.transientTabAfterIconInteraction(pinned, 1, true);
		require(pinned == 1, "primary click pins requested tab");
		require(transientTab == 0, "pinned tab is detached from transient tab state");
		require(SideMenuPinPolicy.visibleTab(transientTab, pinned) == 1,
			"detached pinned tab remains visible");

		transientTab = SideMenuPinPolicy.transientTabAfterIconInteraction(pinned, 3, false);
		require(transientTab == 3, "hover temporarily displays another tab");
		require(SideMenuPinPolicy.visibleTab(transientTab, pinned) == 3,
			"transient tab takes visual priority");
		transientTab = SideMenuPinPolicy.transientTabAfterPointerLeaves();
		require(transientTab == 0, "leaving transient panel restores gameplay state");
		require(SideMenuPinPolicy.visibleTab(transientTab, pinned) == 1,
			"leaving transient panel reveals pinned home tab");

		pinned = SideMenuPinPolicy.pinnedTabAfterPrimaryClick(pinned, 1);
		require(pinned == 0, "clicking pinned icon unpins it");
		require(SideMenuPinPolicy.visibleTab(0, pinned) == 0,
			"unpin returns to unobstructed gameplay state");

		System.out.println("PASS: detached side-menu pin transitions");
	}
}
