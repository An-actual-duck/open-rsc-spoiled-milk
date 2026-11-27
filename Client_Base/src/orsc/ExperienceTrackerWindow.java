package orsc;

import com.openrsc.interfaces.misc.ExperienceSkillSelector;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicInteger;
import orsc.graphics.two.MudClientGraphics;

/** Controls the existing XP tracker preferences in the scaled interface. */
final class ExperienceTrackerWindow {
	private static final String[] MODES = {"Recent skill", "Total XP", "Choose skill"};
	private static final String[] VISIBILITY = {"Never", "On XP gain", "Always"};
	private static final String[] COLORS = {"White", "Yellow", "Red", "Blue"};
	private static final String[] MORE_COLORS = {"Green", "Pink", "Magenta"};
	private static final String[] SPEEDS = {"Slow", "Medium", "Fast"};
	private static final int SKILL_COLUMNS = 2;
	private final int[] skillIds = ExperienceSkillSelector.buildSelectableSkillIds(mudclient.skillCount);
	private final AtomicInteger scroll = new AtomicInteger();
	private boolean picking;
	private int firstSkillRow;

	void scroll(int amount) {
		scroll.addAndGet(amount);
	}

	boolean dismiss() {
		if (!picking) return true;
		picking = false;
		return false;
	}

	boolean draw(mudclient client, int bottom) {
		MudClientGraphics surface = client.getSurface();
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		GameInterfaceSettings.TextSize text = GameInterfaceSettings.textSize();
		int line = surface.fontHeight(text.labelFont);
		int bodyLine = surface.fontHeight(text.bodyFont);
		int pad = Math.min(size.pixels(12), bottom < 530 ? 15 : 24);
		int gap = Math.max(4, pad / 2);
		int preferredRowHeight = bottom < 530 ? Math.min(size.pixels(30), 36) : size.pixels(30);
		int rowHeight = Math.max(preferredRowHeight, line + gap);
		int header = Math.max(size.pixels(40), bodyLine + pad);
		int width = Math.min(client.getGameWidth() - 2 * pad, Math.max(size.pixels(610), 25 * line));
		int inner = width - 2 * pad;
		if (picking) return drawSkillPicker(client, bottom, width, header, pad, gap, rowHeight, text, line, bodyLine);

		int groups = 7;
		int height = header + 2 * pad + groups * (rowHeight + gap);
		int x = (client.getGameWidth() - width) / 2;
		int y = Math.max(pad, (bottom - height) / 2);
		SocialTextPrompt.drawFrame(surface, new Rectangle(x, y, width, height),
			header, pad, "XP Tracker", text.bodyFont, bodyLine);
		int labelWidth = surface.stringWidth(text.labelFont, "Drop speed") + pad;
		int left = x + pad;
		int controls = left + labelWidth;
		int controlsWidth = inner - labelWidth;
		int top = y + header + pad;
		int stride = rowHeight + gap;

		label(surface, left, top, rowHeight, "Track", text.labelFont, line);
		int mode = choices(client, MODES, controls, top, controlsWidth, rowHeight, gap,
			Config.C_EXPERIENCE_COUNTER_MODE, text.labelFont, line);
		if (mode >= 0) {
			if (mode == 2) {
				picking = true;
				firstSkillRow = 0;
				scroll.set(0);
			} else {
				Config.C_EXPERIENCE_COUNTER_MODE = mode;
				client.selectedSkill = -1;
			}
		}

		top += stride;
		label(surface, left, top, rowHeight, "Show", text.labelFont, line);
		int visibility = choices(client, VISIBILITY, controls, top, controlsWidth, rowHeight,
			gap, Config.C_EXPERIENCE_COUNTER, text.labelFont, line);
		if (visibility >= 0) Config.C_EXPERIENCE_COUNTER = visibility;

		top += stride;
		label(surface, left, top, rowHeight, "Text color", text.labelFont, line);
		int color = choices(client, COLORS, controls, top, controlsWidth, rowHeight, gap,
			Config.C_EXPERIENCE_COUNTER_COLOR, text.labelFont, line);
		if (color >= 0) Config.C_EXPERIENCE_COUNTER_COLOR = color;
		top += stride;
		int moreColor = choices(client, MORE_COLORS, controls, top, controlsWidth, rowHeight, gap,
			Config.C_EXPERIENCE_COUNTER_COLOR - COLORS.length, text.labelFont, line);
		if (moreColor >= 0) Config.C_EXPERIENCE_COUNTER_COLOR = moreColor + COLORS.length;

		top += stride;
		label(surface, left, top, rowHeight, "Drop speed", text.labelFont, line);
		int speed = choices(client, SPEEDS, controls, top, controlsWidth, rowHeight, gap,
			Config.C_EXPERIENCE_DROP_SPEED, text.labelFont, line);
		if (speed >= 0) Config.C_EXPERIENCE_DROP_SPEED = speed;

		top += stride;
		label(surface, left, top, rowHeight, "Controls", text.labelFont, line);
		String[] options = {Config.C_EXPERIENCE_CONFIG_SUBMENU ? "Hover details on" : "Hover details off", "Reset session XP"};
		int option = choices(client, options, controls, top, controlsWidth, rowHeight,
			gap, -1, text.labelFont, line);
		if (option == 0) Config.C_EXPERIENCE_CONFIG_SUBMENU = !Config.C_EXPERIENCE_CONFIG_SUBMENU;
		if (option == 1) client.resetExperienceCounter();

		top += stride;
		int closeWidth = size.pixels(92);
		if (Config.C_EXPERIENCE_COUNTER_MODE == 2 && client.selectedSkill >= 0
			&& client.selectedSkill < client.getSkillNamesLong().length) {
			String tracked = "Tracking " + client.getSkillNamesLong()[client.selectedSkill];
			label(surface, left, top, rowHeight, tracked, text.labelFont, line);
		}
		return button(client, new Rectangle(left + inner - closeWidth, top, closeWidth, rowHeight),
			"Close", false, true, text.labelFont, line);
	}

	private boolean drawSkillPicker(mudclient client, int bottom, int width, int header, int pad,
									int gap, int rowHeight, GameInterfaceSettings.TextSize text, int line, int bodyLine) {
		MudClientGraphics surface = client.getSurface();
		int rowCount = (skillIds.length + SKILL_COLUMNS - 1) / SKILL_COLUMNS;
		int stride = rowHeight + gap;
		int chrome = header + rowHeight + 3 * pad + gap;
		int rows = Math.min(rowCount, Math.max(1, (bottom - 2 * pad - chrome) / stride));
		int height = chrome + rows * stride;
		int x = (client.getGameWidth() - width) / 2;
		int y = Math.max(pad, (bottom - height) / 2);
		SocialTextPrompt.drawFrame(surface, new Rectangle(x, y, width, height),
			header, pad, "Choose a skill", text.bodyFont, bodyLine);
		int maxFirst = Math.max(0, rowCount - rows);
		firstSkillRow = Math.max(0, Math.min(maxFirst, firstSkillRow + scroll.getAndSet(0)));
		int columnWidth = (width - 2 * pad - gap) / SKILL_COLUMNS;
		String[] names = client.getSkillNamesLong();
		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < SKILL_COLUMNS; column++) {
				int index = (row + firstSkillRow) * SKILL_COLUMNS + column;
				int skillId = ExperienceSkillSelector.resolveSkillId(skillIds, index);
				if (skillId < 0 || skillId >= names.length) continue;
				Rectangle bounds = new Rectangle(x + pad + column * (columnWidth + gap),
					y + header + pad + row * stride, columnWidth, rowHeight);
				if (button(client, bounds, names[skillId], client.selectedSkill == skillId,
					true, text.labelFont, line)) {
					client.selectedSkill = skillId;
					Config.C_EXPERIENCE_COUNTER_MODE = 2;
					picking = false;
				}
			}
		}
		int buttonsY = y + height - pad - rowHeight;
		int left = x + pad;
		int pageWidth = Math.max(line * 4, 58);
		if (button(client, new Rectangle(left, buttonsY, pageWidth, rowHeight), "Up", false,
			firstSkillRow > 0, text.labelFont, line)) firstSkillRow = Math.max(0, firstSkillRow - rows);
		if (button(client, new Rectangle(left + pageWidth + gap, buttonsY, pageWidth, rowHeight), "Down", false,
			firstSkillRow < maxFirst, text.labelFont, line)) firstSkillRow = Math.min(maxFirst, firstSkillRow + rows);
		if (button(client, new Rectangle(x + width - pad - pageWidth, buttonsY, pageWidth, rowHeight),
			"Back", false, true, text.labelFont, line)) picking = false;
		return false;
	}

	private static int choices(mudclient client, String[] labels, int x, int y, int width,
							   int height, int gap, int selected, int font, int line) {
		int available = width - gap * (labels.length - 1);
		int chosen = -1;
		for (int index = 0; index < labels.length; index++) {
			int start = available * index / labels.length;
			Rectangle bounds = new Rectangle(x + start + index * gap, y,
				available * (index + 1) / labels.length - start, height);
			if (button(client, bounds, labels[index], index == selected, true, font, line)) chosen = index;
		}
		return chosen;
	}

	private static boolean button(mudclient client, Rectangle bounds, String label, boolean selected,
								  boolean enabled, int font, int line) {
		MudClientGraphics surface = client.getSurface();
		boolean hover = bounds.contains(client.getMouseX(), client.getMouseY()) && enabled;
		String fitted = ContextMenuLayout.fitLabel(label, bounds.width - 6, part -> surface.stringWidth(font, part));
		if (enabled) GameplayHud.drawChoice(surface, bounds, fitted, selected, hover, font, line);
		else SocialTextPrompt.drawButton(surface, bounds, fitted, false, false, font, line);
		return hover && client.getMouseClick() == 1;
	}

	private static void label(MudClientGraphics surface, int x, int y, int height, String label, int font, int line) {
		surface.drawString(label, x, y + (height + line) / 2 - 3, InterfacePalette.MUTED_TEXT, font);
	}
}
