package orsc;

import java.awt.Rectangle;
import orsc.graphics.two.MudClientGraphics;

/** Scaled keypad for the server's four-digit bank PIN prompts. */
final class BankPinWindow {
	static final String CANCEL = "cancel";
	private static final String[] DIGITS = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
	private final StringBuilder pin = new StringBuilder(4);
	private volatile boolean cancelled;
	private Layout layout;

	void cancel() {
		cancelled = true;
	}

	String draw(mudclient client, int bottom) {
		if (cancelled) return CANCEL;
		MudClientGraphics surface = client.getSurface();
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		GameInterfaceSettings.TextSize text = GameInterfaceSettings.textSize();
		int bodyLine = surface.fontHeight(text.bodyFont);
		int labelLine = surface.fontHeight(text.labelFont);
		int width = client.getGameWidth();
		if (layout == null || !layout.matches(width, bottom, size, text)) {
			layout = new Layout(width, bottom, size, text, bodyLine, labelLine);
		}
		int mouseX = client.getMouseX();
		int mouseY = client.getMouseY();
		boolean cancelHovered = layout.cancel.contains(mouseX, mouseY);
		if (client.getMouseClick() == 1) {
			if (cancelHovered) return CANCEL;
			int digit = layout.digitAt(mouseX, mouseY);
			if (digit >= 0) {
				pin.append(DIGITS[digit]);
				if (pin.length() == 4) return pin.toString();
			}
		}

		SocialTextPrompt.drawFrame(surface, layout.frame, layout.header, layout.padding, "Bank PIN",
			text.bodyFont, bodyLine);
		surface.drawColoredStringCentered(layout.center, "Click the four digits of your PIN.", 0xC4D0E0,
			0, text.labelFont, layout.instructionBaseline);
		for (int i = 0; i < layout.slots.length; i++) {
			Rectangle slot = layout.slots[i];
			surface.drawBox(slot.x, slot.y, slot.width, slot.height, 0x101923);
			surface.drawBoxBorder(slot.x, slot.width, slot.y, slot.height, 0x667993);
			if (i < pin.length()) {
				surface.drawColoredStringCentered(slot.x + slot.width / 2, "*", 0xFFDC82, 0, text.bodyFont,
					slot.y + (slot.height + bodyLine) / 2 - 2);
			}
		}
		for (int digit = 0; digit < DIGITS.length; digit++) {
			Rectangle key = layout.keys[digit];
			SocialTextPrompt.drawButton(surface, key, DIGITS[digit], true, key.contains(mouseX, mouseY),
				text.bodyFont, bodyLine);
		}
		SocialTextPrompt.drawButton(surface, layout.cancel, "Cancel", true, cancelHovered, text.labelFont, labelLine);
		return null;
	}

	static final class Layout {
		private static final int COLUMNS = 5;
		final Rectangle frame;
		final Rectangle[] slots = new Rectangle[4];
		final Rectangle[] keys = new Rectangle[DIGITS.length];
		final Rectangle cancel;
		final int padding;
		final int header;
		final int center;
		final int instructionBaseline;
		private final int width;
		private final int bottom;
		private final GameInterfaceSettings.Size size;
		private final GameInterfaceSettings.TextSize text;

		Layout(int width, int bottom, GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text,
			   int bodyLine, int labelLine) {
			this.width = width;
			this.bottom = bottom;
			this.size = size;
			this.text = text;
			center = width / 2;
			padding = size.pixels(14);
			int gap = size.pixels(8);
			header = Math.max(size.pixels(42), bodyLine + padding);
			int keySize = Math.max(size.pixels(62), bodyLine + gap);
			int slotHeight = Math.max(size.pixels(34), bodyLine + gap);
			int buttonHeight = Math.max(size.pixels(34), labelLine + gap);
			int innerWidth = COLUMNS * keySize + (COLUMNS - 1) * gap;
			int fixedHeight = header + 2 * padding + labelLine + slotHeight + buttonHeight + 4 * gap;
			int keyHeight = Math.max(bodyLine + gap, Math.min(keySize, (bottom - fixedHeight) / 2));
			int height = fixedHeight + 2 * keyHeight;
			int x = (width - innerWidth) / 2 - padding;
			int y = Math.max(0, (bottom - height) / 2);
			frame = new Rectangle(x, y, innerWidth + 2 * padding, height);
			instructionBaseline = y + header + padding + labelLine - 2;
			int slotY = instructionBaseline + gap + 2;
			int slotLeft = center - (slots.length * slotHeight + (slots.length - 1) * gap) / 2;
			for (int i = 0; i < slots.length; i++) {
				slots[i] = new Rectangle(slotLeft + i * (slotHeight + gap), slotY, slotHeight, slotHeight);
			}
			int keysY = slotY + slotHeight + gap;
			for (int i = 0; i < keys.length; i++) {
				keys[i] = new Rectangle(x + padding + i % COLUMNS * (keySize + gap),
					keysY + i / COLUMNS * (keyHeight + gap), keySize, keyHeight);
			}
			int buttonWidth = size.pixels(108);
			cancel = new Rectangle(center - buttonWidth / 2, keysY + 2 * (keyHeight + gap), buttonWidth, buttonHeight);
		}

		boolean matches(int width, int bottom, GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text) {
			return this.width == width && this.bottom == bottom && this.size == size && this.text == text;
		}

		int digitAt(int x, int y) {
			for (int digit = 0; digit < keys.length; digit++) {
				if (keys[digit].contains(x, y)) return digit;
			}
			return -1;
		}
	}
}
