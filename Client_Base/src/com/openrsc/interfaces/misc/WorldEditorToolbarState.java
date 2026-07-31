package com.openrsc.interfaces.misc;

/** Presentation-only state shared by the compact and expanded editor layouts. */
public final class WorldEditorToolbarState {
	public enum Flyout {
		NONE,
		NAVIGATE,
		INSPECT,
		TERRAIN,
		SCENERY,
		NPC,
		ITEMS
	}

	private Flyout flyout = Flyout.NAVIGATE;
	private boolean collapsed;
	private boolean pinned;
	private boolean expandedFallback;

	public Flyout getFlyout() {
		return flyout;
	}

	public boolean isFlyoutOpen() {
		return flyout != Flyout.NONE;
	}

	public void open(Flyout selected) {
		flyout = selected == null ? Flyout.NONE : selected;
	}

	public void selectMode(Flyout selected) {
		if (selected == null || selected == Flyout.NONE) {
			return;
		}
		flyout = flyout == selected ? Flyout.NONE : selected;
	}

	public void closeUnpinnedAfterWorldAction() {
		if (!pinned) {
			flyout = Flyout.NONE;
		}
	}

	public boolean closeFlyout() {
		if (flyout == Flyout.NONE) {
			return false;
		}
		flyout = Flyout.NONE;
		return true;
	}

	public boolean isCollapsed() {
		return collapsed;
	}

	public void toggleCollapsed() {
		collapsed = !collapsed;
		if (collapsed) {
			flyout = Flyout.NONE;
		}
	}

	public boolean isPinned() {
		return pinned;
	}

	public void togglePinned() {
		pinned = !pinned;
	}

	public boolean isExpandedFallback() {
		return expandedFallback;
	}

	public void setExpandedFallback(boolean expandedFallback) {
		this.expandedFallback = expandedFallback;
		if (expandedFallback) {
			collapsed = false;
		}
	}

	public void reset() {
		flyout = Flyout.NAVIGATE;
		collapsed = false;
		pinned = false;
		expandedFallback = false;
	}
}
