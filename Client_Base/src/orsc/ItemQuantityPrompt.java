package orsc;

import com.openrsc.client.entityhandling.defs.ItemDef;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import orsc.graphics.two.MudClientGraphics;

/** A focused amount field tied to the item and action that opened it. */
final class ItemQuantityPrompt {
	static final int OPEN = -1;
	static final int CANCEL = 0;

	enum Action {
		BUY("Buy", " in stock"),
		SELL("Sell", " available to sell"),
		DROP("Drop", " in inventory");

		final String label;
		final String availability;

		Action(String label, String availability) {
			this.label = label;
			this.availability = availability;
		}

		boolean isShop() {
			return this != DROP;
		}
	}

	final Action action;
	final int itemId;
	final int originalSlot;
	final boolean noted;
	private final ModalTextField editor = new ModalTextField(10, key -> key >= '0' && key <= '9');
	private long enteredAmount = OPEN;
	private boolean cancelled;
	private boolean showValidation;

	ItemQuantityPrompt(Action action, int itemId, int originalSlot, boolean noted) {
		this.action = action;
		this.itemId = itemId;
		this.originalSlot = originalSlot;
		this.noted = noted;
	}

	synchronized void keyPressed(char key, int code, boolean shortcut) {
		if (code == KeyEvent.VK_ESCAPE || key == 27) {
			cancelled = true;
		} else if (code == KeyEvent.VK_ENTER || key == '\r' || key == '\n') {
			enteredAmount = amount();
		} else {
			editor.keyPressed(key, code, shortcut);
		}
	}

	private long amount() {
		String value = editor.value();
		return value.isEmpty() ? 0 : Long.parseLong(value);
	}

	synchronized int draw(mudclient client, int bottom, ItemDef item, String itemName, int maximum) {
		if (cancelled) return CANCEL;
		if (enteredAmount != OPEN) {
			long requested = enteredAmount;
			enteredAmount = OPEN;
			if (requested > 0 && requested <= maximum) return (int) requested;
			showValidation = true;
		}

		MudClientGraphics surface = client.getSurface();
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		GameInterfaceSettings.TextSize style = GameInterfaceSettings.textSize();
		int pad = size.pixels(12);
		int bodyLine = surface.fontHeight(style.bodyFont);
		int labelLine = surface.fontHeight(style.labelFont);
		int width = Math.min(client.getGameWidth() - 2 * pad, size.pixels(440));
		int header = Math.max(size.pixels(42), bodyLine + pad);
		int itemRow = Math.max(size.pixels(38), bodyLine);
		int fieldHeight = bodyLine + 2 * pad;
		int buttonHeight = Math.max(size.pixels(34), labelLine + pad);
		int height = header + itemRow + fieldHeight + 2 * labelLine + buttonHeight + 6 * pad;
		int x = (client.getGameWidth() - width) / 2;
		int y = Math.max(pad, (bottom - height) / 2);
		int left = x + pad;
		int inner = width - 2 * pad;
		int itemY = y + header + pad;
		int labelY = itemY + itemRow + pad + labelLine;
		Rectangle field = new Rectangle(left, labelY + pad / 2, inner, fieldHeight);
		int feedbackY = field.y + field.height + pad / 2 + labelLine;
		int buttonWidth = Math.min((inner - pad) / 2, Math.max(size.pixels(88),
			surface.stringWidth(style.labelFont, "Cancel") + 2 * pad));
		int actionsY = feedbackY + pad;
		Rectangle submit = new Rectangle(x + width - pad - buttonWidth, actionsY, buttonWidth, buttonHeight);
		Rectangle cancel = new Rectangle(submit.x - pad - buttonWidth, actionsY, buttonWidth, buttonHeight);
		int mouseX = client.getMouseX();
		int mouseY = client.getMouseY();
		boolean clicked = client.getMouseClick() == 1;
		long requested = amount();
		boolean enabled = requested > 0 && requested <= maximum;
		if (clicked && cancel.contains(mouseX, mouseY)) return CANCEL;
		if (clicked && enabled && submit.contains(mouseX, mouseY)) return (int) requested;

		SocialTextPrompt.drawFrame(surface, new Rectangle(x, y, width, height), header, pad,
			action.label + " X", style.bodyFont, bodyLine);
		int iconWidth = size.pixels(45);
		client.drawInventoryPreviewIcon(item, noted, left, itemY, iconWidth, itemRow);
		String name = ContextMenuLayout.fitLabel(itemName, inner - iconWidth - pad,
			part -> surface.literalStringWidth(style.bodyFont, part));
		surface.drawLiteralString(name, left + iconWidth + pad, itemY + (itemRow + bodyLine) / 2 - 3,
			0xFFFFFF, style.bodyFont);
		surface.drawString("Quantity", left, labelY, 0xC4D0E0, style.labelFont);
		editor.draw(client, field, 1, style.bodyFont, pad);
		String feedback;
		if (maximum == 0) {
			feedback = "This item is no longer available";
		} else if (requested > maximum) {
			feedback = "Enter " + maximum + " or fewer";
		} else if (showValidation && requested == 0) {
			feedback = "Enter at least 1";
		} else {
			feedback = maximum + action.availability;
		}
		feedback = ContextMenuLayout.fitLabel(feedback, inner, part -> surface.stringWidth(style.labelFont, part));
		surface.drawString(feedback, left, feedbackY, maximum == 0 || requested > maximum
			|| showValidation && requested == 0 ? 0xFFBC8D : 0xC4D0E0, style.labelFont);
		SocialTextPrompt.drawButton(surface, cancel, "Cancel", true, cancel.contains(mouseX, mouseY), style.labelFont, labelLine);
		SocialTextPrompt.drawButton(surface, submit, action.label, enabled, submit.contains(mouseX, mouseY),
			style.labelFont, labelLine);
		return OPEN;
	}
}
