package com.openrsc.interfaces.misc;

import java.awt.Rectangle;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import orsc.graphics.gui.Panel;
import orsc.mudclient;

import static com.openrsc.interfaces.misc.ItemWorkspaceStyle.*;

/** Shared search, matching mode and tag controls. Saving is supplied by the bank. */
final class ItemFilterSidebar {
	private static final int TAG_ROWS = BankItemTag.values().length + BankItemTag.Group.values().length;
	private final mudclient client;
	private final AtomicInteger wheel = new AtomicInteger();
	private volatile Rectangle scrollArea;
	private int firstRow;

	ItemFilterSidebar(mudclient client) {
		this.client = client;
	}

	void reset() {
		firstRow = 0;
		wheel.set(0);
		scrollArea = null;
	}

	boolean scroll(int x, int y, int delta) {
		Rectangle area = scrollArea;
		if (area == null || !area.contains(x, y)) return false;
		wheel.addAndGet(Integer.signum(delta));
		return true;
	}

	void draw(Rectangle frame, ItemWorkspaceStyle style, Panel input, int search,
			  BankFilterSettings filters, Consumer<BankFilterSettings> apply,
			  boolean showSave, boolean saved, Runnable save) {
		style.panel(frame, "Filters");
		int x = frame.x + style.gap;
		int width = frame.width - 2 * style.gap;
		int y = frame.y + style.header + style.gap;
		style.draw("Search", x, y + style.line - 2, MUTED);
		y += style.line;
		client.getSurface().drawBox(x, y, width, style.button, 0x16212D);
		client.getSurface().drawBoxBorder(x, width, y, style.button, BORDER);
		input.reposition(search, x + 5, y + style.button / 2, width - 10, style.button - 4);
		input.setTextFont(search, style.font);
		y += style.button + style.gap;
		int split = width / 2;
		if (style.button(x, y, split - 2, style.button, "AND", filters.andMode, true)) {
			apply.accept(new BankFilterSettings(filters.query, filters.tags, true));
		}
		if (style.button(x + split, y, width - split, style.button, "OR", !filters.andMode, true)) {
			apply.accept(new BankFilterSettings(filters.query, filters.tags, false));
		}
		y += style.button + style.gap;
		int clearWidth = showSave ? Math.min(width / 3, style.measure("Clear") + style.gap) : width;
		if (showSave && style.button(x, y, width - clearWidth - 3, style.button,
				saved ? "Save filters [X]" : "Save filters", saved, save != null)) save.run();
		if (style.button(x + width - clearWidth, y, clearWidth, style.button, "Clear", false, true)) {
			apply.accept(BankFilterSettings.ALL);
		}
		y += style.button + style.gap;
		drawTags(style, new Rectangle(x, y, width, frame.y + frame.height - y - style.button - 2 * style.gap),
			filters, apply);
	}

	private void drawTags(ItemWorkspaceStyle style, Rectangle area, BankFilterSettings filters,
						  Consumer<BankFilterSettings> apply) {
		int rowHeight = Math.max(size(18), style.line + 2);
		int visible = Math.max(1, area.height / rowHeight);
		int max = Math.max(0, TAG_ROWS - visible);
		scrollArea = new Rectangle(area.x, area.y, area.width, visible * rowHeight);
		firstRow = Math.max(0, Math.min(max, firstRow + wheel.getAndSet(0)));
		int index = 0;
		for (BankItemTag.Group group : BankItemTag.Group.values()) {
			int y = area.y + (index - firstRow) * rowHeight;
			if (index >= firstRow && index < firstRow + visible) {
				style.draw(group == BankItemTag.Group.SKILLS ? "Skills" : "Item types", area.x + 2, y + style.line, GOLD);
			}
			index++;
			for (BankItemTag tag : BankItemTag.values()) {
				if (tag.group != group) continue;
				if (index >= firstRow && index < firstRow + visible) {
					y = area.y + (index - firstRow) * rowHeight;
					int check = rowHeight - 4;
					boolean selected = filters.tags.contains(tag);
					if (style.button(area.x, y + 2, check, check, selected ? "X" : "", selected, true)
						|| style.click(area.x + check, y, area.width - check, rowHeight)) {
						EnumSet<BankItemTag> tags = filters.tags.clone();
						if (selected) tags.remove(tag);
						else tags.add(tag);
						apply.accept(new BankFilterSettings(filters.query, tags, filters.andMode));
					}
					style.draw(style.fit(tag.label, area.width - check - 6), area.x + check + 5, y + style.line, TEXT);
				}
				index++;
			}
		}
		if (max > 0) {
			int footer = area.y + visible * rowHeight + 2;
			if (style.button(area.x, footer, style.button, style.button, "<", false, firstRow > 0)) {
				firstRow = Math.max(0, firstRow - visible);
			}
			if (style.button(area.x + area.width - style.button, footer, style.button, style.button, ">", false, firstRow < max)) {
				firstRow = Math.min(max, firstRow + visible);
			}
		}
	}
}
