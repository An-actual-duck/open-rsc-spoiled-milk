package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.entityhandling.instances.Item;
import orsc.Config;
import orsc.mudclient;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** The scaled bank view and its independent, live filter tabs. */
final class BankWorkspace {
	static final int TEXT = ItemWorkspaceStyle.TEXT;
	static final int GOLD = ItemWorkspaceStyle.GOLD;
	static final int MUTED = ItemWorkspaceStyle.MUTED;
	static final int BORDER = ItemWorkspaceStyle.BORDER;
	private static final int SAVE_FILTER_TAB = 32;

	private final CustomBankInterface bank;
	private final mudclient client;
	private final ArrayList<Tab> tabs = new ArrayList<>();
	private final AtomicInteger stockWheel = new AtomicInteger();
	private final ItemFilterSidebar filterSidebar;
	private ItemWorkspaceStyle style;
	private final AtomicInteger carriedWheel = new AtomicInteger();
	private volatile BankWorkspaceLayout layout;
	private boolean supported;
	private boolean held;
	private int active;
	private int carriedRow;

	BankWorkspace(CustomBankInterface bank, mudclient client) {
		this.bank = bank;
		this.client = client;
		filterSidebar = new ItemFilterSidebar(client);
		tabs.add(new Tab(null));
	}

	void receive(String[] records, boolean opening) {
		if (!opening && supported) return;
		supported = records.length > 0;
		tabs.clear();
		for (int i = 0; i < Math.min(records.length, BankFilterSettings.LIMIT); i++) {
			tabs.add(new Tab(BankFilterSettings.decode(records[i])));
		}
		if (tabs.isEmpty()) tabs.add(new Tab(null));
		active = 0;
		filterSidebar.reset();
		carriedRow = 0;
		held = client.getMouseButtonDown() == 1;
		selected();
	}

	BankWorkspaceLayout begin(boolean presets, boolean notes) {
		style = new ItemWorkspaceStyle(client, bank::workspaceCanClick);
		layout = new BankWorkspaceLayout(client, presets, notes);
		boolean down = client.getMouseButtonDown() == 1;
		if (down && bank.workspaceCanClick()) {
			if (held && client.getMouseClick() == 1) {
				client.setMouseClick(0);
			} else if (!held && client.getMouseClick() == 0) {
				// Consume the pending press before the game input loop can deliver it again.
				client.lastMouseButtonDown = 0;
				client.setMouseClick(1);
			}
		}
		held = down;
		return layout;
	}

	void scroll(int delta) {
		BankWorkspaceLayout view = layout;
		if (view == null) return;
		int step = Integer.signum(delta);
		int x = client.getMouseX();
		int y = client.getMouseY();
		if (view.stock.contains(x, y)) stockWheel.addAndGet(step);
		else if (view.carried.contains(x, y)) carriedWheel.addAndGet(step);
		else filterSidebar.scroll(x, y, step);
	}

	ArrayList<BankInterface.BankItem> stock() {
		BankFilterSettings filter = bank.currentBankFilters();
		rememberActive(filter);
		return items(tabs.get(active), filter);
	}

	private ArrayList<BankInterface.BankItem> items(Tab tab, BankFilterSettings filter) {
		if (!filter.equals(tab.shown) || tab.bankRevision != bank.bankContentsRevision()) {
			if (!filter.equals(tab.shown)) tab.row = 0;
			tab.shown = filter;
			tab.bankRevision = bank.bankContentsRevision();
			tab.items = bank.filterBankItems(filter);
		}
		return tab.items;
	}

	int stockStart(int count) {
		Tab tab = tabs.get(active);
		int max = Math.max(0, rows(count, layout.stockColumns) - layout.stockRows);
		tab.row = clamp(tab.row + stockWheel.getAndSet(0), 0, max);
		return tab.row * layout.stockColumns;
	}

	int carriedStart(int count, int columns, int rows) {
		carriedRow = clamp(carriedRow + carriedWheel.getAndSet(0), 0,
			Math.max(0, rows(count, columns) - rows));
		return carriedRow * columns;
	}

	void resetCarriedScroll() {
		carriedRow = 0;
		carriedWheel.set(0);
	}

	void carriedPages(int count, int columns, int visibleRows) {
		int max = Math.max(0, rows(count, columns) - visibleRows);
		if (max == 0) return;
		carriedRow = pager(layout.carried.x, layout.carried.x + layout.carried.width,
			layout.carried.y + layout.carried.height - layout.actionButton, carriedRow, visibleRows, max, layout.actionButton);
	}

	void drawStockFooter(int count, int capacity) {
		Tab tab = tabs.get(active);
		int max = Math.max(0, rows(count, layout.stockColumns) - layout.stockRows);
		int y = layout.stock.y + layout.stock.height + layout.gap;
		if (max > 0) {
			tab.row = pager(layout.stock.x, layout.stock.x + layout.stock.width, y, tab.row, layout.stockRows, max, layout.button);
		}
		String slots = bank.getWorkspaceUsedSlots() + " / " + capacity + " bank slots";
		center(slots, layout.bank.x, layout.bank.y + layout.bank.height - layout.gap, layout.bank.width, layout.font, MUTED);
	}

	void drawCatalog() {
		BankWorkspaceLayout view = layout;
		panel(view.bank, "Bank");
		String wealth = bank.workspaceWealth();
		int width = Math.min(view.bank.width / 2, measure(view.font, wealth));
		draw(fit(wealth, width), view.bank.x + view.bank.width - width - view.gap,
			view.bank.y + (view.header + view.line) / 2 - 2, view.font, GOLD);
		int x = view.bank.x + view.gap;
		int y = view.bank.y + view.header + view.gap;
		int step = Math.min(BankWorkspaceLayout.size(55), (view.bank.width - 2 * view.gap) / (BankFilterSettings.LIMIT + 1));
		int w = step - 3;
		for (int i = 0; i < tabs.size(); i++) {
			if (button(x, y, w, view.tabHeight, "", i == active, true) && i != active) {
				rememberActive(bank.currentBankFilters());
				active = i;
				selected();
			}
			Tab tab = tabs.get(i);
			BankFilterSettings filter = i == active ? bank.currentBankFilters() : savedOrAll(tab);
			ArrayList<BankInterface.BankItem> results = items(tab, filter);
			int iconWidth = Math.min(w - 4, BankWorkspaceLayout.size(43));
			int iconHeight = Math.min(view.tabHeight - 4, iconWidth * 2 / 3);
			if (!results.isEmpty()) {
				BankInterface.BankItem first = results.get(0);
				drawItem(first.getItem(), x + (w - iconWidth) / 2, y + (view.tabHeight - iconHeight) / 2,
					iconWidth, iconHeight, first.isPlaceholder() ? 0x60FFFFFF : 0xFFFFFFFF);
			}
			draw(Integer.toString(i + 1), x + 3, y + view.line, view.font, i == active ? GOLD : TEXT);
			x += step;
		}
		if (tabs.size() < BankFilterSettings.LIMIT && button(x, y, w, view.tabHeight, "+", false, supported)) {
			rememberActive(bank.currentBankFilters());
			active = tabs.size();
			tabs.add(new Tab(null));
			send(active, null);
			selected();
		}
	}

	void drawFilters() {
		Tab tab = tabs.get(active);
		filterSidebar.draw(layout.filters, style, bank.bank, bank.bankSearch, bank.currentBankFilters(),
			bank::updateWorkspaceFilters, true, tab.saved != null, supported ? () -> {
				tab.saved = tab.saved == null ? bank.currentBankFilters() : null;
				send(active, tab.saved);
			} : null);
	}

	private int pager(int x, int right, int y, int row, int step, int max, int w) {
		if (button(x, y, w, w, "<", false, row > 0)) row = Math.max(0, row - step);
		if (button(right - w, y, w, w, ">", false, row < max)) row = Math.min(max, row + step);
		return row;
	}

	void panel(Rectangle area, String title) {
		style.panel(area, title);
	}

	boolean button(int x, int y, int w, int h, String text, boolean selected, boolean enabled) {
		return style.button(x, y, w, h, text, selected, enabled);
	}

	void drawItem(Item item, int x, int y, int width, int height, int tint) {
		ItemDef def = item.getItemDef();
		if (def == null) return;
		if (item.getNoted()) {
			ItemDef paper = Config.S_WANT_CERT_AS_NOTES
				? EntityHandler.noteDef
				: EntityHandler.certificateDef;
			sprite(paper, x, y, width, height, tint);
			if (Config.S_WANT_CERT_AS_NOTES) {
				sprite(def, x + width / 7, y + height / 4, width * 3 / 5, height * 3 / 5, tint);
			}
		} else {
			sprite(def, x, y, width, height, tint);
		}
	}

	private void sprite(ItemDef def, int x, int y, int width, int height, int tint) {
		client.getSurface().drawSpriteClipping(client.spriteSelect(def), x, y, width, height,
			def.getPictureMask(), 0, def.getBlueMask(), false, 0, 1, tint);
	}

	void draw(String text, int x, int y, int font, int colour) {
		client.getSurface().drawString(text, x, y, colour, font);
	}

	String fit(String text, int width) {
		if (measure(layout.font, text) <= width) return text;
		int end = text.length();
		while (end > 0 && measure(layout.font, text.substring(0, end) + "...") > width) end--;
		return end > 0 ? text.substring(0, end) + "..." : "";
	}

	private void center(String text, int x, int y, int width, int font, int colour) {
		draw(text, x + (width - measure(font, text)) / 2, y, font, colour);
	}

	private int measure(int font, String text) {
		return client.getSurface().stringWidth(font, text);
	}

	void rememberActive(BankFilterSettings current) {
		Tab tab = tabs.get(active);
		if (tab.saved != null && !tab.saved.equals(current)) {
			tab.saved = current;
			send(active, current);
		}
	}

	private void selected() {
		bank.applyBankFilters(savedOrAll(tabs.get(active)));
		stockWheel.set(0);
	}

	private static BankFilterSettings savedOrAll(Tab tab) {
		return tab.saved == null ? BankFilterSettings.ALL : tab.saved;
	}

	private void send(int slot, BankFilterSettings settings) {
		client.packetHandler.getClientStream().newPacket(199);
		client.packetHandler.getClientStream().bufferBits.putByte(SAVE_FILTER_TAB);
		client.packetHandler.getClientStream().bufferBits.putByte(slot);
		client.packetHandler.getClientStream().bufferBits.putString(settings == null ? "0" : settings.encode());
		client.packetHandler.getClientStream().finishPacket();
	}

	static int rows(int count, int columns) {
		return (count + columns - 1) / columns;
	}

	private static int clamp(int value, int low, int high) {
		return Math.max(low, Math.min(high, value));
	}

	private static final class Tab {
		BankFilterSettings saved;
		BankFilterSettings shown;
		ArrayList<BankInterface.BankItem> items;
		long bankRevision = -1;
		int row;

		Tab(BankFilterSettings saved) {
			this.saved = saved;
		}
	}
}
