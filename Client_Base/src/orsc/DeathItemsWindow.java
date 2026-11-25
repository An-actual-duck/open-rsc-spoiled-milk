package orsc;

import java.awt.Rectangle;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import orsc.graphics.two.MudClientGraphics;

/** A read-only view of the server's current kept and lost items. */
final class DeathItemsWindow {
	static final class Entry {
		final int id;
		final boolean noted;
		final int basePrice;
		long amount;
		String amountLabel;

		Entry(int id, boolean noted, int basePrice, int amount) {
			this.id = id;
			this.noted = noted;
			this.basePrice = basePrice;
			this.amount = amount;
		}
	}

	private static final long RESPONSE_TIMEOUT_NS = 6_000_000_000L;

	final int requestId;
	private final long openedAt = System.nanoTime();
	private List<Entry> kept;
	private List<Entry> lost;
	private boolean inDuel;
	private String lossValue;
	private int keptFirst, lostFirst;

	DeathItemsWindow(int requestId) {
		this.requestId = requestId;
	}

	void receive(List<Entry> entries, int keptCount, boolean inDuel) {
		this.inDuel = inDuel;
		List<Entry> dropped = entries.subList(keptCount, entries.size());
		BigInteger total = BigInteger.ZERO;
		for (Entry item : dropped) {
			total = total.add(BigInteger.valueOf(item.basePrice).multiply(BigInteger.valueOf(item.amount)));
		}
		NumberFormat quantities = NumberFormat.getIntegerInstance(Locale.US);
		lossValue = quantities.format(total) + " gp";
		kept = group(entries.subList(0, keptCount), quantities);
		lost = group(dropped, quantities);
	}

	private static List<Entry> group(List<Entry> items, NumberFormat quantities) {
		LinkedHashMap<Integer, Entry> grouped = new LinkedHashMap<>();
		for (Entry item : items) {
			int key = 2 * item.id + (item.noted ? 1 : 0);
			Entry prior = grouped.putIfAbsent(key, item);
			if (prior != null) prior.amount += item.amount;
		}
		for (Entry item : grouped.values()) item.amountLabel = quantities.format(item.amount);
		return new ArrayList<>(grouped.values());
	}

	boolean draw(mudclient client, int bottom, int wheel) {
		MudClientGraphics surface = client.getSurface();
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		GameInterfaceSettings.TextSize text = GameInterfaceSettings.textSize();
		int padding = size.pixels(12);
		int labelLine = surface.fontHeight(text.labelFont);
		int bodyLine = surface.fontHeight(text.bodyFont);
		int header = Math.max(size.pixels(42), bodyLine + padding);
		int footer = Math.max(size.pixels(34), labelLine + padding);
		int targetRowHeight = size.pixels(34);
		if (bottom < 530) targetRowHeight = Math.min(targetRowHeight, 40);
		int rowHeight = Math.max(targetRowHeight, labelLine + 8);
		int labelHeight = labelLine + padding;
		int biggestList = kept == null ? 0 : Math.max(kept.size(), lost.size());
		boolean showItems = kept != null && !inDuel;
		int available = bottom - header - footer - labelHeight - 3 * padding;
		int possibleRows = Math.max(1, Math.min(15, available / rowHeight));
		boolean hasPages = showItems && biggestList > possibleRows;
		int rows = Math.max(1, Math.min(biggestList, possibleRows - (hasPages ? 1 : 0)));
		int width = Math.min(client.getGameWidth() - 2 * padding, size.pixels(showItems ? 840 : 490));
		int contentHeight = showItems ? labelHeight + rowHeight * (rows + (hasPages ? 1 : 0)) : bodyLine;
		int height = header + footer + contentHeight + 3 * padding;
		int x = (client.getGameWidth() - width) / 2;
		int y = Math.max(0, (bottom - height) / 2);
		int left = x + padding;
		int contentY = y + header + padding;
		int footerY = contentY + contentHeight + padding;
		int closeWidth = Math.min(size.pixels(118), width / 3);
		Rectangle close = new Rectangle(x + width - padding - closeWidth, footerY, closeWidth, footer);
		SocialTextPrompt.drawFrame(surface, new Rectangle(x, y, width, height), header, padding,
			"Items on Death", text.bodyFont, bodyLine);

		if (showItems) {
			int column = (width - 3 * padding) / 2;
			int lostLeft = left + column + padding;
			int listTop = contentY + labelHeight;
			drawLabel(surface, "Kept", left, contentY + labelLine - 2, column, text.labelFont, 0xA9E0B2);
			drawLabel(surface, "Lost", lostLeft, contentY + labelLine - 2, column, text.labelFont, 0xFFC3A8);
			keptFirst = drawColumn(client, kept, keptFirst, rows,
				new Rectangle(left, listTop, column, rowHeight * rows), rowHeight, padding, wheel, text.labelFont, labelLine);
			lostFirst = drawColumn(client, lost, lostFirst, rows,
				new Rectangle(lostLeft, listTop, column, rowHeight * rows), rowHeight, padding, wheel, text.labelFont, labelLine);
			drawLabel(surface, "Estimated loss value  " + lossValue, left, footerY + (footer + labelLine) / 2 - 3,
				close.x - left - padding, text.labelFont, 0xFFDC82);
		} else {
			String message = inDuel ? "Duel stakes use separate rules."
				: System.nanoTime() - openedAt >= RESPONSE_TIMEOUT_NS ? "The preview could not be loaded." : "Checking your items...";
			drawLabel(surface, message, left, contentY + bodyLine - 3, width - 2 * padding, text.bodyFont, 0xC4D0E0);
		}
		return button(client, close, "Close", true, text.labelFont, labelLine);
	}

	private static int drawColumn(mudclient client, List<Entry> entries, int first, int rows,
								  Rectangle list, int rowHeight, int padding, int wheel, int font, int line) {
		MudClientGraphics surface = client.getSurface();
		int maxFirst = Math.max(0, entries.size() - rows);
		if (list.contains(client.getMouseX(), client.getMouseY())) first += wheel;
		first = Math.max(0, Math.min(maxFirst, first));
		if (maxFirst > 0) {
			int arrow = Math.max(line + 6, padding * 2);
			int controlY = list.y + list.height + 3;
			int controlHeight = rowHeight - 3;
			Rectangle down = new Rectangle(list.x + list.width - arrow, controlY, arrow, controlHeight);
			Rectangle up = new Rectangle(down.x - arrow - padding / 2, controlY, arrow, controlHeight);
			if (button(client, up, "^", first > 0, font, line)) first = Math.max(0, first - rows);
			if (button(client, down, "v", first < maxFirst, font, line)) first = Math.min(maxFirst, first + rows);
			drawLabel(surface, (first + 1) + "-" + (first + rows) + " / " + entries.size(), list.x,
				controlY + (controlHeight + line) / 2 - 3, up.x - list.x - 4, font, 0xC4D0E0);
		}

		if (entries.isEmpty()) {
			drawLabel(surface, "None", list.x, list.y + (rowHeight + line) / 2 - 3, list.width, font, 0xC4D0E0);
		}
		int iconHeight = rowHeight - 4;
		int iconWidth = iconHeight * 3 / 2;
		for (int index = 0; index < rows && first + index < entries.size(); index++) {
			Entry item = entries.get(first + index);
			int top = list.y + index * rowHeight;
			int baseline = top + (rowHeight + line) / 2 - 3;
			surface.drawBox(list.x, top, list.width, rowHeight - 1, 0x233144);
			ItemDef def = EntityHandler.findItem(item.id, false);
			String name = def == null ? "Item " + item.id : def.getName();
			if (def != null) client.drawInventoryPreviewIcon(def, item.noted, list.x + 3, top + 2, iconWidth, iconHeight);
			if (item.noted) name += Config.S_WANT_CERT_AS_NOTES ? " (noted)" : " (certificate)";
			String count = item.amountLabel;
			int countWidth = surface.literalStringWidth(font, count);
			int countX = list.x + list.width - countWidth - padding / 2;
			int nameX = list.x + iconWidth + padding / 2;
			drawLabel(surface, name, nameX, baseline, countX - nameX - padding / 2, font, 0xFFFFFF);
			surface.drawLiteralString(count, countX, baseline, 0xFFDC82, font);
		}
		return first;
	}

	private static void drawLabel(MudClientGraphics surface, String value, int x, int y, int width, int font, int color) {
		String fitted = ContextMenuLayout.fitLabel(value, width, text -> surface.literalStringWidth(font, text));
		surface.drawLiteralString(fitted, x, y, color, font);
	}

	private static boolean button(mudclient client, Rectangle bounds, String label, boolean enabled, int font, int line) {
		boolean hovered = bounds.contains(client.getMouseX(), client.getMouseY());
		SocialTextPrompt.drawButton(client.getSurface(), bounds, label, enabled, hovered, font, line);
		return hovered && enabled && client.getMouseClick() == 1;
	}
}
