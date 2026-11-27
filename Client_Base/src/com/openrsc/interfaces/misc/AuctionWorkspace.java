package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.entityhandling.instances.Item;
import java.awt.Rectangle;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import orsc.Config;
import orsc.InterfacePalette;
import orsc.graphics.gui.Panel;
import orsc.mudclient;

import static com.openrsc.interfaces.misc.ItemWorkspaceStyle.*;

/** Scaled auction views backed by the existing market list and transactions. */
final class AuctionWorkspace {
	private enum Sort {
		PRICE_LOW("Total: low to high", Comparator.comparingInt(AuctionItem::getPrice)),
		PRICE_HIGH("Total: high to low", Comparator.comparingInt(AuctionItem::getPrice).reversed()),
		NAME("Name", Comparator.comparing(item -> definition(item).getName(), String.CASE_INSENSITIVE_ORDER)),
		EACH_LOW("Each: low to high", Comparator.comparingInt(AuctionWorkspace::unitPrice)),
		EACH_HIGH("Each: high to low", Comparator.comparingInt(AuctionWorkspace::unitPrice).reversed());

		final String label;
		final Comparator<AuctionItem> comparator;

		Sort(String label, Comparator<AuctionItem> comparator) {
			this.label = label;
			this.comparator = comparator.thenComparingInt(AuctionItem::getAuctionID);
		}
	}

	private final AuctionHouse market;
	private final mudclient client;
	private final ItemFilterSidebar sidebar;
	private final NumberFormat number = NumberFormat.getIntegerInstance(Locale.US);
	private final List<AuctionItem> browseResults = new ArrayList<>();
	private final List<AuctionItem> ownResults = new ArrayList<>();
	private final AtomicInteger listWheel = new AtomicInteger();
	private volatile Rectangle scrollArea;
	private volatile boolean browsing = true;
	private BankFilterSettings filters = BankFilterSettings.ALL;
	private BankFilterSettings shownFilters;
	private Sort sort = Sort.PRICE_LOW;
	private Sort shownSort;
	private int marketRevision = -1;
	private int firstRow;
	private int selectedId = -1;
	private boolean choosingItem;
	private ItemDef draft;
	private boolean held;
	private volatile Review review;
	private String status = "";
	private ItemWorkspaceStyle style;

	AuctionWorkspace(AuctionHouse market, mudclient client) {
		this.market = market;
		this.client = client;
		sidebar = new ItemFilterSidebar(client);
	}

	void reset() {
		browsing = true;
		filters = BankFilterSettings.ALL;
		sort = Sort.PRICE_LOW;
		choosingItem = false;
		draft = null;
		review = null;
		status = "";
		sidebar.reset();
		resetScroll();
		clearSelection();
		held = client.getMouseButtonDown() == 1;
	}

	void scroll(int delta) {
		if (review != null) return;
		int x = client.getMouseX();
		int y = client.getMouseY();
		if (browsing && sidebar.scroll(x, y, delta)) return;
		Rectangle area = scrollArea;
		if (area != null && area.contains(x, y)) listWheel.addAndGet(Integer.signum(delta));
	}

	void draw() {
		style = new ItemWorkspaceStyle(client, () -> review == null);
		Layout layout = new Layout(client, style);
		consumePress();
		if (review == null && client.getMouseClick() == 1 && !layout.frame.contains(client.getMouseX(), client.getMouseY())) {
			market.auctionClose();
			return;
		}
		style.panel(layout.frame, "Auction House");
		if (style.button(layout.right() - style.header, layout.frame.y + 2, style.header - 3, style.header - 4,
				"X", false, true)) {
			market.auctionClose();
			return;
		}
		int tabWidth = Math.max(size(124), style.measure("My Auctions") + 2 * style.gap);
		int tabX = layout.frame.x + style.gap;
		if (style.button(tabX, layout.tabY, tabWidth, style.button, "Browse", browsing, true) && !browsing) {
			switchTab(true);
		}
		if (style.button(tabX + tabWidth + style.gap, layout.tabY, tabWidth, style.button, "My Auctions", !browsing, true) && browsing) {
			switchTab(false);
		}
		int statusX = tabX + 2 * tabWidth + 2 * style.gap;
		style.draw(style.fit(status, layout.right() - style.gap - statusX), statusX,
			layout.tabY + (style.button + style.line) / 2 - 2, GOLD);

		market.auctionMenu.hide(market.auctionScrollHandle);
		market.myAuctions.hide(market.myAuctionScrollHandle);
		if (browsing) {
			hideSellerFields();
			market.auctionMenu.show(market.auctionSearchHandle);
			sidebar.draw(layout.filters, style, market.auctionMenu, market.auctionSearchHandle,
				currentFilters(), this::applyFilters, false, false, null);
			updateResults();
			drawListings(layout.catalog, browseResults, false);
			drawBuyer(layout.actions, findSelected(browseResults));
			market.auctionMenu.drawPanel();
		} else {
			market.auctionMenu.hide(market.auctionSearchHandle);
			hideBuyField();
			updateResults();
			if (choosingItem) drawInventory(layout.mine);
			else drawListings(layout.mine, ownResults, true);
			drawSeller(layout.actions, findSelected(ownResults));
			market.myAuctions.drawPanel();
		}
		Review currentReview = review;
		if (currentReview != null) drawReview(layout.frame, currentReview);
		client.setMouseClick(0);
	}

	boolean hasReview() {
		return review != null;
	}

	void setStatus(String message) {
		status = message;
	}

	private void review(String title, String action, String detail, Runnable submit) {
		market.auctionMenu.setFocus(-1);
		market.myAuctions.setFocus(-1);
		review = new Review(title, action, detail, submit);
	}

	private void drawReview(Rectangle frame, Review current) {
		client.getSurface().drawBoxAlpha(frame.x, frame.y, frame.width, frame.height, 0, 145);
		ItemWorkspaceStyle modal = new ItemWorkspaceStyle(client, () -> review == current);
		int width = Math.min(frame.width - 2 * modal.gap,
			Math.max(size(430), modal.measure(current.detail) + 2 * modal.gap));
		int height = modal.header + 4 * modal.gap + modal.line + modal.button;
		Rectangle box = new Rectangle(frame.x + (frame.width - width) / 2,
			frame.y + (frame.height - height) / 2, width, height);
		modal.panel(box, current.title);
		modal.draw(modal.fit(current.detail, box.width - 2 * modal.gap), box.x + modal.gap,
			box.y + modal.header + modal.gap + modal.line, TEXT);
		int y = box.y + box.height - modal.gap - modal.button;
		int buttonWidth = Math.max(size(95), Math.max(modal.measure("Back"), modal.measure(current.action)) + 2 * modal.gap);
		int right = box.x + box.width - modal.gap;
		if (modal.button(right - 2 * buttonWidth - modal.gap, y, buttonWidth, modal.button, "Back", false, true)) {
			review = null;
		} else if (modal.button(right - buttonWidth, y, buttonWidth, modal.button, current.action, true, true)) {
			review = null;
			current.submit.run();
		}
	}

	private static final class Review {
		final String title, action, detail;
		final Runnable submit;

		Review(String title, String action, String detail, Runnable submit) {
			this.title = title;
			this.action = action;
			this.detail = detail;
			this.submit = submit;
		}
	}

	private void consumePress() {
		boolean down = client.getMouseButtonDown() == 1;
		if (down && held && client.getMouseClick() == 1) {
			client.setMouseClick(0);
		} else if (down && !held && client.getMouseClick() == 0) {
			client.lastMouseButtonDown = 0;
			client.setMouseClick(1);
		}
		held = down;
	}

	private void switchTab(boolean browse) {
		browsing = browse;
		clearSelection();
		resetScroll();
	}

	private void clearSelection() {
		selectedId = -1;
		market.auctionMenu.setFocus(-1);
		market.myAuctions.setFocus(-1);
	}

	private void resetScroll() {
		firstRow = 0;
		listWheel.set(0);
		scrollArea = null;
	}

	private BankFilterSettings currentFilters() {
		return new BankFilterSettings(market.auctionMenu.getControlText(market.auctionSearchHandle), filters.tags, filters.andMode);
	}

	private void applyFilters(BankFilterSettings updated) {
		filters = updated;
		if (updated == BankFilterSettings.ALL) market.auctionMenu.setText(market.auctionSearchHandle, "");
		clearSelection();
		resetScroll();
	}

	private void updateResults() {
		BankFilterSettings current = currentFilters();
		boolean listChanged = marketRevision != market.revision();
		if (listChanged) {
			ownResults.clear();
			for (AuctionItem auction : market.listings()) {
				if (valid(auction) && own(auction)) ownResults.add(auction);
			}
			ownResults.sort(Sort.PRICE_LOW.comparator);
			marketRevision = market.revision();
		}
		if (!listChanged && current.equals(shownFilters) && shownSort == sort) return;
		if (!current.equals(shownFilters) || shownSort != sort) resetScroll();
		browseResults.clear();
		for (AuctionItem auction : market.listings()) {
			if (valid(auction) && current.matches(definition(auction))) browseResults.add(auction);
		}
		browseResults.sort(sort.comparator);
		shownFilters = current;
		shownSort = sort;
	}

	private static boolean valid(AuctionItem auction) {
		ItemDef definition = definition(auction);
		return definition != null && definition.getName() != null && auction.getAmount() > 0;
	}

	private AuctionItem findSelected(List<AuctionItem> items) {
		for (AuctionItem item : items) {
			if (item.getAuctionID() == selectedId) return item;
		}
		return null;
	}

	private boolean own(AuctionItem auction) {
		return auction.getSeller().equalsIgnoreCase(client.getUsername())
			|| (client.getLocalPlayer() != null && auction.getSeller().equalsIgnoreCase(client.getLocalPlayer().displayName));
	}

	private void drawListings(Rectangle panel, List<AuctionItem> items, boolean ownListings) {
		style.panel(panel, ownListings ? "My Listings" : "Listings");
		int refreshWidth = Math.max(style.measure("Refresh") + 2 * style.gap, size(78));
		if (style.button(panel.x + panel.width - refreshWidth - style.gap, panel.y + 2, refreshWidth,
				Math.min(style.button, style.header - 4), "Refresh", false, true)) market.sendRefreshList();
		int x = panel.x + style.gap;
		int y = panel.y + style.header + style.gap;
		int width = panel.width - 2 * style.gap;
		int bottom = panel.y + panel.height - style.gap;
		int footer = bottom - style.button;
		int count = items.size();
		int rowHeight = Math.max(size(52), 2 * style.line + style.gap);
		int availableRows = Math.max(1, (footer - style.gap - y) / rowHeight);
		Rectangle list = new Rectangle(x, y, width, availableRows * rowHeight);
		scrollList(list, count, availableRows);
		if (items.isEmpty()) {
			String empty = ownListings ? "You have no active listings."
				: market.listings().isEmpty() ? "There are no active listings." : "No listings match your filters.";
			style.draw(style.fit(empty, width), x, y + style.line, MUTED);
		}
		for (int index = firstRow; index < Math.min(firstRow + availableRows, count); index++) {
			drawListingRow(items.get(index), x, y + (index - firstRow) * rowHeight, width, rowHeight, ownListings);
		}
		if (!ownListings) {
			int sortWidth = Math.min(width - 2 * style.button - 2 * style.gap,
				Math.max(size(150), style.measure("Each: high to low") + 2 * style.gap));
			if (style.button(x, footer, sortWidth, style.button, sort.label, false, true)) {
				sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
				clearSelection();
				resetScroll();
			}
			x += sortWidth + style.gap;
		}
		drawPager(x, list.x + list.width, footer, count, availableRows);
	}

	private void drawListingRow(AuctionItem auction, int x, int y, int w, int h, boolean ownListing) {
		boolean selected = selectedId == auction.getAuctionID();
		boolean over = style.over(x, y, w, h - 2);
		client.getSurface().drawBox(x, y, w, h - 2,
			selected ? InterfacePalette.CONTROL_SELECTED : over ? InterfacePalette.CONTROL_HOVER : InterfacePalette.ROW);
		client.getSurface().drawBoxBorder(x, w, y, h - 2, BORDER);
		int iconWidth = Math.min(size(54), (h - 4) * 3 / 2);
		int iconHeight = iconWidth * 2 / 3;
		drawSprite(definition(auction), x + 2, y + (h - iconHeight) / 2, iconWidth, iconHeight);
		int labelX = x + iconWidth + style.gap;
		int priceWidth = Math.max(w / 3, Math.max(style.measure(gp(auction.getPrice())), style.measure(gp(unitPrice(auction)) + " each")) + style.gap);
		int titleBaseline = y + (h - 2 * style.line) / 2 + style.line - 2;
		style.draw(style.fit(definition(auction).getName(), x + w - priceWidth - labelX), labelX, titleBaseline, TEXT);
		style.draw(style.fit("Qty " + number.format(auction.getAmount()), x + w - priceWidth - labelX),
			labelX, titleBaseline + style.line, MUTED);
		drawRight(gp(auction.getPrice()), x + w - style.gap, titleBaseline, GOLD);
		drawRight(gp(unitPrice(auction)) + " each", x + w - style.gap, titleBaseline + style.line, MUTED);
		if (style.click(x, y, w, h - 2)) {
			selectedId = auction.getAuctionID();
			if (!ownListing) {
				market.auctionMenu.setText(market.textField_buyAmount, "1");
				market.auctionMenu.setFocus(-1);
			}
		}
	}

	private void drawBuyer(Rectangle panel, AuctionItem auction) {
		style.panel(panel, "Purchase");
		int x = panel.x + style.gap;
		int w = panel.width - 2 * style.gap;
		int y = panel.y + style.header + style.gap;
		y = line("Carried  " + gp(client.getInventoryCount(10)), x, y, w, GOLD);
		if (auction == null) {
			hideBuyField();
			line("Select a listing to view it.", x, y, w, MUTED);
			return;
		}
		ItemDef definition = definition(auction);
		y = line(definition.getName(), x, y, w, TEXT);
		y = line("Seller  " + auction.getSeller(), x, y, w, MUTED);
		y = line(number.format(auction.getAmount()) + " available at " + gp(unitPrice(auction)) + " each", x, y, w, MUTED);
		if (own(auction)) {
			hideBuyField();
			line("Manage this in My Auctions.", x, y, w, MUTED);
			return;
		}
		y = field(market.auctionMenu, market.textField_buyAmount, "Quantity", x, y, w);
		int quantity = value(market.auctionMenu, market.textField_buyAmount);
		boolean valid = quantity > 0 && quantity <= auction.getAmount();
		long total = (long) quantity * unitPrice(auction);
		boolean canBuy = valid && total > 0 && total <= client.getInventoryCount(10);
		String cost = valid ? "Total  " + gp(total) : "Enter an available quantity.";
		if (valid && !canBuy) cost = total <= 0 ? "This listing has an invalid price." : "Need " + gp(total) + " carried.";
		y = line(cost, x, y, w, canBuy ? GOLD : MUTED);
		if (style.button(x, y, w, style.button, "Review purchase", false, canBuy)) {
			review("Confirm purchase", "Buy", itemQuantity(definition, quantity) + " for " + gp(total), () -> {
				AuctionItem latest = market.findListing(auction.getAuctionID());
				if (latest == null || latest.getAmount() < quantity || unitPrice(latest) != unitPrice(auction)) {
					setStatus("This listing changed. Select it again to review the new price and quantity.");
				} else if (total > client.getInventoryCount(10)) {
					setStatus("You need " + gp(total) + " in your inventory.");
				} else market.sendAuctionBuy(latest, quantity);
			});
		}
		y += style.button + 4;
		boolean moderator = client.getLocalPlayer() != null && client.getLocalPlayer().isMod();
		if (style.button(x, y, w, style.button, moderator ? "Remove listing (staff)" : "Add seller as friend", false, true)) {
			if (moderator) {
				reviewRemoval(auction, true);
			} else client.addFriend(auction.getSeller());
		}
		y += style.button + 4;
		String description = definition.getDescription();
		if (description != null && y + style.line <= panel.y + panel.height - style.gap) {
			line(description, x, y, w, MUTED);
		}
	}

	private void drawSeller(Rectangle panel, AuctionItem auction) {
		if (!choosingItem || draft == null) hideSellerFields();
		style.panel(panel, choosingItem ? "Create listing" : "Manage listings");
		int x = panel.x + style.gap;
		int w = panel.width - 2 * style.gap;
		int y = panel.y + style.header + style.gap;
		if (style.button(x, y, w, style.button, choosingItem ? "Back to listings" : "Select item to list", false, true)) {
			choosingItem = !choosingItem;
			draft = null;
			hideSellerFields();
			clearSelection();
			resetScroll();
			return;
		}
		y += style.button + Math.min(4, style.gap);
		if (!choosingItem) {
			if (auction != null) {
				y = line(definition(auction).getName(), x, y, w, TEXT) + style.gap;
				if (style.button(x, y, w, style.button, "Cancel auction", false, true)) {
					reviewRemoval(auction, false);
				}
			}
			y += style.button + style.gap;
			y = line("Collect from a banker.", x, y, w, MUTED);
			line("Sale proceeds and returned items", x, y, w, MUTED);
			return;
		}
		if (draft == null) {
			line("Select an item from your inventory.", x, y, w, MUTED);
			return;
		}
		int allowed = available(draft.id);
		String count = number.format(allowed) + " available";
		if (panel.height < size(390)) {
			y = line(draft.getName() + " (" + count + ")", x, y, w, TEXT);
		} else {
			y = line(draft.getName(), x, y, w, TEXT);
			y = line(count, x, y, w, MUTED);
		}
		Panel input = market.myAuctions;
		y = field(input, market.textField_amount, "Quantity", x, y, w);
		y = field(input, market.textField_priceEach, "Price each", x, y, w);
		y = field(input, market.textField_price, "Total price", x, y, w) + Math.min(4, style.gap);
		int quantity = value(input, market.textField_amount);
		int total = value(input, market.textField_price);
		boolean valid = quantity > 0 && quantity <= allowed && total >= quantity
			&& value(input, market.textField_priceEach) > 0;
		if (style.button(x, y, w, style.button, "Create auction", false, valid)) {
			ItemDef item = draft;
			review("Confirm listing", "List item", itemQuantity(item, quantity) + " for " + gp(total), () -> {
				if (available(item.id) < quantity) {
					setStatus("Your available quantity changed. Review the listing again.");
					return;
				}
				market.sendCreateAuction(item.id, quantity, total);
				choosingItem = false;
				draft = null;
				hideSellerFields();
				clearSelection();
				resetScroll();
			});
		}
	}

	private void reviewRemoval(AuctionItem auction, boolean moderator) {
		String action = moderator ? "Remove listing" : "Cancel auction";
		review(action, moderator ? "Remove" : "Cancel listing", itemQuantity(definition(auction), auction.getAmount()), () -> {
			AuctionItem latest = market.findListing(auction.getAuctionID());
			if (latest == null || latest.getAmount() != auction.getAmount()) {
				setStatus("This listing changed. Select it again to review the remaining items.");
				return;
			}
			if (moderator) market.sendModCancelAuction(auction.getAuctionID());
			else market.sendCancelAuction(auction.getAuctionID());
			clearSelection();
		});
	}

	private String itemQuantity(ItemDef item, int quantity) {
		return item.getName() + " x " + number.format(quantity);
	}

	private void hideBuyField() {
		market.auctionMenu.hide(market.textField_buyAmount);
		if (market.auctionMenu.focusOn(market.textField_buyAmount)) market.auctionMenu.setFocus(-1);
	}

	private void hideSellerFields() {
		market.myAuctions.hide(market.textField_amount);
		market.myAuctions.hide(market.textField_price);
		market.myAuctions.hide(market.textField_priceEach);
		market.myAuctions.setFocus(-1);
	}

	private void drawInventory(Rectangle panel) {
		style.panel(panel, "My Inventory");
		int x = panel.x + style.gap;
		int y = panel.y + style.header + style.gap;
		int w = panel.width - 2 * style.gap;
		int columns = Math.max(1, Math.min(8, w / size(61)));
		int width = w / columns;
		int height = Math.max(size(51), style.line + style.gap);
		int footer = panel.y + panel.height - style.gap - style.button;
		int rows = Math.max(1, (footer - style.gap - y - style.line) / height);
		int inventoryCount = client.getInventoryCapacity();
		int totalRows = (inventoryCount + columns - 1) / columns;
		Rectangle area = new Rectangle(x, y, width * columns, rows * height);
		scrollList(area, totalRows, rows);
		for (int index = firstRow * columns; index < Math.min(inventoryCount, (firstRow + rows) * columns); index++) {
			int left = x + (index % columns) * width;
			int top = y + (index / columns - firstRow) * height;
			Item item = index < client.getInventoryItemCount() ? client.getInventoryItem(index) : null;
			drawInventoryTile(item, left, top, width - 2, height - 2, area);
		}
		if (totalRows > rows) drawPager(x, x + w, footer, totalRows, rows);
	}

	private void drawInventoryTile(Item item, int x, int y, int w, int h, Rectangle area) {
		ItemDef definition = item == null ? null : item.getItemDef();
		boolean over = style.over(x, y, w, h);
		boolean selected = definition != null && draft != null && draft.id == definition.id;
		client.getSurface().drawBox(x, y, w, h,
			selected ? InterfacePalette.CONTROL_SELECTED : over ? InterfacePalette.CONTROL_HOVER : InterfacePalette.ROW);
		client.getSurface().drawBoxBorder(x, w, y, h, BORDER);
		if (definition == null) return;
		int icon = Math.min(w - 4, (h - 4) * 3 / 2);
		int iconHeight = icon * 2 / 3;
		int left = x + (w - icon) / 2;
		int top = y + (h - iconHeight) / 2;
		if (item.getNoted()) {
			drawSprite(Config.S_WANT_CERT_AS_NOTES ? EntityHandler.noteDef : EntityHandler.certificateDef, left, top, icon, iconHeight);
			if (Config.S_WANT_CERT_AS_NOTES) {
				drawSprite(definition, left + icon / 5, top + iconHeight / 5, icon * 3 / 5, iconHeight * 3 / 5);
			}
		} else drawSprite(definition, left, top, icon, iconHeight);
		if (item.getAmount() > 1) style.draw(mudclient.formatStackAmount(item.getAmount()), x + 3, y + style.line, TEXT);
		boolean tradable = definition.id != 10 && !definition.untradeable && !item.getEquipped();
		if (over) {
			line(definition.getName() + (tradable ? "" : " (unavailable for auction)"), area.x,
				area.y + area.height, area.width, tradable ? GOLD : MUTED);
		}
		if (tradable && style.click(x, y, w, h)) {
			draft = definition;
			int quantity = available(definition.id);
			int each = Math.max(1, Math.min(Integer.MAX_VALUE / quantity, definition.getBasePrice()));
			market.updateTextFields(quantity, each * quantity, each);
			market.myAuctions.setFocus(-1);
		}
	}

	private int available(int id) {
		long count = 0;
		for (int slot = 0; slot < client.getInventoryItemCount(); slot++) {
			Item item = client.getInventoryItem(slot);
			if (item.getItemDef() != null && item.getItemDef().id == id && !item.getEquipped()) {
				count += Math.max(1, item.getAmount());
			}
		}
		return (int) Math.min(Integer.MAX_VALUE, count);
	}

	private int field(Panel input, int handle, String label, int x, int y, int w) {
		int boxWidth = Math.max(w / 2, style.measure("2,147,483,647") + 8);
		boxWidth = Math.min(boxWidth, w * 2 / 3);
		int left = x + w - boxWidth;
		style.draw(style.fit(label, left - x - 4), x, y + (style.button + style.line) / 2 - 2, MUTED);
		client.getSurface().drawBox(left, y, boxWidth, style.button, InterfacePalette.INSET);
		client.getSurface().drawBoxBorder(left, boxWidth, y, style.button, BORDER);
		input.reposition(handle, left + 5, y + style.button / 2, boxWidth - 10, style.button - 4);
		input.setTextFont(handle, style.font);
		input.show(handle);
		if (style.click(left, y, boxWidth, style.button)) input.setFocus(handle);
		return y + style.button + 4;
	}

	boolean keyDown(int key) {
		if (review != null) {
			if (key == 27) review = null;
			return true;
		}
		if (key == 27) {
			market.auctionClose();
			return true;
		}
		if (browsing) {
			if (market.auctionMenu.focusOn(market.auctionSearchHandle)) {
				if (key != 9) market.auctionMenu.keyPress(key);
				selectedId = -1;
				return true;
			}
			if (market.auctionMenu.focusOn(market.textField_buyAmount)) {
				if (numericKey(key)) market.auctionMenu.keyPress(key);
				return true;
			}
			return false;
		}
		Panel input = market.myAuctions;
		boolean quantity = input.focusOn(market.textField_amount);
		boolean total = input.focusOn(market.textField_price);
		if (draft == null || (!quantity && !total && !input.focusOn(market.textField_priceEach))) return false;
		if (!numericKey(key)) return true;
		input.keyPress(key);
		int amount = value(input, market.textField_amount);
		int priceEach = value(input, market.textField_priceEach);
		if (amount > 0) {
			if (total) {
				input.setText(market.textField_priceEach, Integer.toString(value(input, market.textField_price) / amount));
			} else if (priceEach > 0) {
				long extended = (long) amount * priceEach;
				input.setText(market.textField_price, extended <= Integer.MAX_VALUE ? Long.toString(extended) : "");
			}
		}
		return true;
	}

	private static boolean numericKey(int key) {
		return key >= '0' && key <= '9' || key == 8;
	}

	private static int value(Panel input, int handle) {
		try {
			return Integer.parseInt(input.getControlText(handle));
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static int unitPrice(AuctionItem auction) {
		return auction.getPrice() / Math.max(1, auction.getAmount());
	}

	private static ItemDef definition(AuctionItem auction) {
		return auction.getItem().getItemDef();
	}

	private String gp(long amount) {
		return number.format(amount) + " gp";
	}

	private int line(String text, int x, int y, int width, int color) {
		style.draw(style.fit(text, width), x, y + style.line, color);
		return y + style.line + 3;
	}

	private void drawRight(String text, int right, int y, int color) {
		style.draw(text, right - style.measure(text), y, color);
	}

	private void drawSprite(ItemDef definition, int x, int y, int w, int h) {
		if (definition == null) return;
		client.getSurface().drawSpriteClipping(client.spriteSelect(definition), x, y, w, h,
			definition.getPictureMask(), 0, definition.getBlueMask(), false, 0, 1);
	}

	private void scrollList(Rectangle area, int count, int visible) {
		scrollArea = area;
		firstRow = Math.max(0, Math.min(Math.max(0, count - visible), firstRow + listWheel.getAndSet(0)));
	}

	private void drawPager(int left, int right, int y, int count, int visible) {
		int max = Math.max(0, count - visible);
		if (style.button(right - 2 * style.button - 3, y, style.button, style.button, "<", false, firstRow > 0)) {
			firstRow = Math.max(0, firstRow - visible);
		}
		if (style.button(right - style.button, y, style.button, style.button, ">", false, firstRow < max)) {
			firstRow = Math.min(max, firstRow + visible);
		}
		String range = count == 0 ? "" : (firstRow + 1) + "-" + Math.min(firstRow + visible, count) + " of " + count;
		style.draw(style.fit(range, right - 2 * style.button - 6 - left), left, y + (style.button + style.line) / 2 - 2, MUTED);
	}

	static final class Layout {
		final Rectangle frame, filters, catalog, mine, actions;
		final int tabY;

		Layout(mudclient client, ItemWorkspaceStyle style) {
			int windowWidth = Math.min(size(1180), client.getGameWidth() - 2 * style.gap);
			int bottom = client.getCraftingPanelBottom();
			int height = Math.min(size(570), bottom - 2 * style.gap);
			int x = (client.getGameWidth() - windowWidth) / 2;
			int y = (bottom - height) / 2;
			frame = new Rectangle(x, y, windowWidth, height);
			tabY = y + style.header + style.gap;
			int top = tabY + style.button + style.gap;
			int panelHeight = y + height - top - style.gap;
			int content = windowWidth - 2 * style.gap;
			int side = Math.min(content / 3, Math.max(size(250), style.measure("Refresh listings") + 4 * style.gap));
			int filterWidth = Math.min(content / 4, Math.max(size(190), style.measure("Crafting: Jewelry") + 5 * style.gap));
			int innerLeft = x + style.gap;
			int mineWidth = content - side - style.gap;
			filters = new Rectangle(innerLeft, top, filterWidth, panelHeight);
			catalog = new Rectangle(innerLeft + filterWidth + style.gap, top, mineWidth - filterWidth - style.gap, panelHeight);
			mine = new Rectangle(innerLeft, top, mineWidth, panelHeight);
			actions = new Rectangle(innerLeft + mineWidth + style.gap, top, side, panelHeight);
		}

		int right() {
			return frame.x + frame.width;
		}
	}
}
