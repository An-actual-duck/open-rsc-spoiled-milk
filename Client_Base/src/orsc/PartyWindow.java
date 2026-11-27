package orsc;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

import com.openrsc.interfaces.misc.party.Party;
import com.openrsc.interfaces.misc.party.PartyInterface;
import orsc.graphics.gui.SocialLists;
import orsc.graphics.two.MudClientGraphics;
import orsc.util.StringUtil;

/** Scaled Party windows for the desktop and browser interface. */
final class PartyWindow {
	enum View { CREATE, INVITATION, MANAGE }
	private enum Confirmation { LEAVE, KICK, TRANSFER }

	private static final String[] PERMISSIONS = { "Who can kick members?", "Who can invite players?" };
	private static final String[] PERMISSION_RANKS = { "Anyone", "Owner", "General+" };

	private final View view;
	private final String inviter;
	private final String partyName;
	private final long expiresAt;
	private boolean permissionsOpen;
	private String selectedMember;
	private Confirmation confirmation;
	private int pendingKey;

	PartyWindow(View view) {
		this(view, "", "");
	}

	PartyWindow(String inviter, String partyName) {
		this(View.INVITATION, inviter, partyName);
	}

	private PartyWindow(View view, String inviter, String partyName) {
		this.view = view;
		this.inviter = inviter;
		this.partyName = partyName == null ? "" : partyName;
		expiresAt = view == View.INVITATION ? System.nanoTime() + TimeUnit.SECONDS.toNanos(60) : 0;
	}

	synchronized void keyPressed(int code) {
		if (code == KeyEvent.VK_ESCAPE || code == KeyEvent.VK_ENTER && view == View.CREATE) {
			pendingKey = code;
		}
	}

	synchronized boolean draw(mudclient client, int bottom) {
		Party party = client.party;
		PartyInterface actions = party.getPartyInterface();
		int key = pendingKey;
		pendingKey = 0;
		if (view == View.MANAGE && !party.inParty() || view != View.MANAGE && party.inParty()) return true;
		if (view == View.INVITATION && System.nanoTime() - expiresAt >= 0) return true;
		if (key == KeyEvent.VK_ESCAPE) {
			if (confirmation != null) confirmation = null;
			else {
				if (view == View.INVITATION) actions.sendPartyDecline();
				return true;
			}
		}

		Layout layout = new Layout(client, bottom);
		switch (view) {
			case CREATE:
				layout.frame("Create Party", layout.bodyLine);
				layout.line("Create a party for up to five players.", layout.contentY, layout.bodyFont);
				if (layout.button(1, 2, layout.footerY, "Create Party", true) || key == KeyEvent.VK_ENTER) {
					actions.sendCreateParty("", "");
					return true;
				}
				return layout.button(0, 2, layout.footerY, "Cancel", true);
			case INVITATION:
				layout.frame("Party Invitation", 2 * layout.bodyLine);
				layout.line("Invitation from " + inviter, layout.contentY, layout.bodyFont);
				layout.line(partyName.isEmpty() ? "Join their party?" : "Join " + partyName + "?",
					layout.contentY + layout.bodyLine, layout.bodyFont);
				if (layout.button(2, 3, layout.footerY, "Accept", true)) {
					actions.sendPartyAccept();
					return true;
				}
				if (layout.button(0, 3, layout.footerY, "Ignore 2.5 min", true)) {
					client.addDelayedIgnore(inviter);
					actions.sendPartyDecline();
					return true;
				}
				if (layout.button(1, 3, layout.footerY, "Decline", true)) {
					actions.sendPartyDecline();
					return true;
				}
				return false;
			case MANAGE:
				return drawManagement(client, party, actions, layout);
			default:
				throw new AssertionError(view);
		}
	}

	private boolean drawManagement(mudclient client, Party party, PartyInterface actions, Layout layout) {
		int count = Math.min(SocialLists.partyListCount, 5);
		int selected = -1;
		for (int index = 0; index < count; index++) {
			if (selectedMember != null && selectedMember.equals(party.username[index]) && party.partyRank[index] != 1) {
				selected = index;
				break;
			}
		}
		if (selected < 0) selectedMember = null;
		boolean owner = party.isPartyLeader();
		boolean canKick = selected >= 0 && party.isAllowed(0);
		boolean canRank = selected >= 0 && owner;
		if (confirmation != null) {
			return drawConfirmation(actions, layout, canKick, canRank);
		}
		if (!owner) permissionsOpen = false;
		int tabHeight = owner ? layout.buttonHeight + layout.pad : 0;
		int permissionsHeight = PERMISSIONS.length * (layout.labelLine + layout.buttonHeight + layout.pad);
		int actionsHeight = owner || party.isAllowed(0) ? layout.buttonHeight + layout.pad / 2 : 0;
		int tableHeader = layout.labelLine + layout.pad / 2;
		int availableRows = layout.contentLimit() - tabHeight - tableHeader - actionsHeight;
		int rowHeight = Math.max(layout.labelLine + 4, Math.min(layout.size.pixels(27),
			availableRows / Math.max(1, count)));
		int tableHeight = tableHeader + count * rowHeight;
		int memberHeight = tableHeight + actionsHeight;
		layout.frame("Manage Party", tabHeight + (owner ? Math.max(permissionsHeight, memberHeight) : memberHeight));
		if (owner) {
			if (layout.button(0, 2, layout.contentY, "Members", true, !permissionsOpen)) permissionsOpen = false;
			if (layout.button(1, 2, layout.contentY, "Permissions", true, permissionsOpen)) permissionsOpen = true;
		}
		if (permissionsOpen) {
			drawPermissions(party, actions, layout, layout.contentY + tabHeight);
		} else {
			drawMembers(client, party, layout, layout.contentY + tabHeight, count, rowHeight);
			int y = layout.footerY - actionsHeight;
			if (actionsHeight > 0) {
				boolean general = selected >= 0 && party.partyRank[selected] == 2;
				if (layout.button(0, 3, y, general ? "Remove Rank" : "Make General", canRank)) {
					actions.sendPartyRank(selectedMember, general ? 0 : 2);
				}
				if (layout.button(1, 3, y, "Transfer Leader", canRank)) confirmation = Confirmation.TRANSFER;
				if (layout.button(2, 3, y, "Kick Member", canKick)) confirmation = Confirmation.KICK;
			}
		}
		if (layout.button(0, 3, layout.footerY, "Invite Player", party.isAllowed(1))) client.openPartyInvitePrompt();
		if (layout.button(1, 3, layout.footerY, "Leave Party", true)) confirmation = Confirmation.LEAVE;
		return layout.button(2, 3, layout.footerY, "Close", true);
	}

	private void drawPermissions(Party party, PartyInterface actions, Layout layout, int y) {
		for (int setting = 0; setting < PERMISSIONS.length; setting++) {
			layout.text(PERMISSIONS[setting], layout.left, y + layout.labelLine - 3, layout.width, InterfacePalette.MUTED_TEXT);
			y += layout.labelLine;
			for (int rank = 0; rank < PERMISSION_RANKS.length; rank++) {
				int current = party.getPartySetting(setting);
				if (layout.button(rank, PERMISSION_RANKS.length, y, PERMISSION_RANKS[rank], true, current == rank)
					&& current != rank) {
					actions.sendPartySettings(setting, rank);
				}
			}
			y += layout.buttonHeight + layout.pad;
		}
	}

	private void drawMembers(mudclient client, Party party, Layout layout, int y, int count, int rowHeight) {
		int nameWidth = layout.width * 43 / 100;
		int rankWidth = layout.width * 22 / 100;
		int hitsWidth = layout.width * 17 / 100;
		int nameX = layout.left + layout.pad / 2;
		int rankX = nameX + nameWidth;
		int hitsX = rankX + rankWidth;
		int levelX = hitsX + hitsWidth;
		int baseline = y + layout.labelLine - 3;
		layout.text("Player", nameX, baseline, nameWidth, InterfacePalette.MUTED_TEXT);
		layout.text("Rank", rankX, baseline, rankWidth, InterfacePalette.MUTED_TEXT);
		layout.text("HP", hitsX, baseline, hitsWidth, InterfacePalette.MUTED_TEXT);
		layout.text("Combat", levelX, baseline, layout.width - (levelX - layout.left), InterfacePalette.MUTED_TEXT);
		y += layout.labelLine + layout.pad / 2;

		String local = StringUtil.displayNameToKey(client.getLocalPlayer().accountName);
		for (int index = 0; index < count; index++) {
			String member = party.username[index];
			boolean selectable = member != null && party.partyRank[index] != 1
				&& !local.equals(StringUtil.displayNameToKey(member)) && (party.isPartyLeader() || party.isAllowed(0));
			Rectangle row = new Rectangle(layout.left, y, layout.width, rowHeight);
			boolean hover = selectable && layout.hover(row);
			if (hover && client.getMouseClick() == 1) selectedMember = member;
			boolean selected = member != null && member.equals(selectedMember);
			layout.surface.drawBox(row.x, row.y, row.width, row.height, selected || hover ? InterfacePalette.CONTROL_HOVER : InterfacePalette.ROW);
			if (selected) {
				layout.surface.drawBoxBorder(row.x, row.width, row.y, row.height, 0xD2B873);
			} else {
				layout.surface.drawLineHoriz(row.x, row.y + row.height - 1, row.width, InterfacePalette.PANEL);
			}
			baseline = y + (rowHeight + layout.labelLine) / 2 - 3;
			boolean online = party.onlinePartyMember[index] != 0;
			layout.text(member == null ? "Unknown" : member, nameX, baseline, nameWidth,
				online ? 0x91E2A0 : InterfacePalette.MUTED_TEXT);
			layout.text(party.getPartyRankNames(party.partyRank[index]), rankX, baseline, rankWidth, 0xFFFFFF);
			layout.text(party.curHp[index] + "/" + party.maxHp[index], hitsX, baseline, hitsWidth, 0xFFFFFF);
			layout.text(Integer.toString(party.cbLvl[index]), levelX, baseline, layout.width - (levelX - layout.left),
				0xFFFFFF);
			y += rowHeight;
		}
	}

	private boolean drawConfirmation(PartyInterface actions, Layout layout, boolean canKick, boolean canRank) {
		String title;
		String question;
		String confirm;
		boolean allowed = true;
		switch (confirmation) {
			case KICK:
				title = "Kick Member";
				question = "Remove " + selectedMember + " from the party?";
				confirm = "Kick Member";
				allowed = canKick;
				break;
			case TRANSFER:
				title = "Transfer Leadership";
				question = "Make " + selectedMember + " the party leader?";
				confirm = "Transfer";
				allowed = canRank;
				break;
			case LEAVE:
				title = "Leave Party";
				question = "Leave your current party?";
				confirm = "Leave Party";
				break;
			default:
				throw new AssertionError(confirmation);
		}
		if (!allowed) {
			confirmation = null;
			return false;
		}
		layout.frame(title, layout.bodyLine);
		layout.line(question, layout.contentY, layout.bodyFont);
		if (layout.button(0, 2, layout.footerY, "Cancel", true)) {
			confirmation = null;
		}
		if (layout.button(1, 2, layout.footerY, confirm, true)) {
			switch (confirmation) {
				case LEAVE: actions.sendPartyLeave(); return true;
				case KICK: actions.sendPartyKick(selectedMember); break;
				case TRANSFER: actions.sendPartyRank(selectedMember, 1); break;
			}
			confirmation = null;
			selectedMember = null;
		}
		return false;
	}

	private static final class Layout {
		private final mudclient client;
		private final MudClientGraphics surface;
		private final GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		private final int bodyFont = GameInterfaceSettings.textSize().bodyFont;
		private final int labelFont = GameInterfaceSettings.textSize().labelFont;
		private final int bodyLine;
		private final int labelLine;
		private final int pad;
		private final int header;
		private final int buttonHeight;
		private final int bottom;
		private final int left;
		private final int width;
		private int contentY;
		private int footerY;

		Layout(mudclient client, int bottom) {
			this.client = client;
			this.bottom = bottom;
			surface = client.getSurface();
			bodyLine = surface.fontHeight(bodyFont);
			labelLine = surface.fontHeight(labelFont);
			boolean shortViewport = bottom < 530;
			pad = shortViewport ? Math.min(size.pixels(12), 12) : size.pixels(12);
			int targetHeader = size.pixels(46);
			int targetButton = size.pixels(34);
			if (shortViewport) {
				targetHeader = Math.min(targetHeader, 50);
				targetButton = Math.min(targetButton, 38);
			}
			header = Math.max(targetHeader, bodyLine + pad);
			buttonHeight = Math.max(targetButton, labelLine + pad / 2);
			int minButton = Math.max(surface.stringWidth(labelFont, "Ignore 2.5 min"),
				surface.stringWidth(labelFont, "Transfer Leader")) + pad;
			int panelWidth = Math.min(client.getGameWidth() - 2 * pad, Math.max(size.pixels(540), 3 * minButton + 4 * pad));
			width = panelWidth - 2 * pad;
			left = (client.getGameWidth() - width) / 2;
		}

		int contentLimit() {
			return bottom - header - buttonHeight - 4 * pad;
		}

		void frame(String title, int contentHeight) {
			int height = header + 3 * pad + contentHeight + buttonHeight;
			int y = Math.max(0, (bottom - height) / 2);
			contentY = y + header + pad;
			footerY = contentY + contentHeight + pad;
			SocialTextPrompt.drawFrame(surface, new Rectangle(left - pad, y, width + 2 * pad, height),
				header, pad, title, bodyFont, bodyLine);
		}

		void line(String value, int y, int font) {
			String fitted = ContextMenuLayout.fitLabel(value, width, text -> surface.literalStringWidth(font, text));
			surface.drawLiteralString(fitted, left + (width - surface.literalStringWidth(font, fitted)) / 2,
				y + bodyLine - 3, 0xFFFFFF, font);
		}

		void text(String value, int x, int y, int available, int color) {
			String fitted = ContextMenuLayout.fitLabel(value, available - pad / 2,
				text -> surface.literalStringWidth(labelFont, text));
			surface.drawLiteralString(fitted, x, y, color, labelFont);
		}

		boolean hover(Rectangle bounds) {
			return bounds.contains(client.getMouseX(), client.getMouseY());
		}

		boolean button(int column, int columns, int y, String label, boolean enabled) {
			return button(column, columns, y, label, enabled, false);
		}

		boolean button(int column, int columns, int y, String label, boolean enabled, boolean selected) {
			int span = width + pad;
			int x = left + column * span / columns;
			Rectangle bounds = new Rectangle(x, y, left + (column + 1) * span / columns - pad - x, buttonHeight);
			boolean hover = hover(bounds);
			String fitted = ContextMenuLayout.fitLabel(label, bounds.width - pad / 2,
				part -> surface.stringWidth(labelFont, part));
			SocialTextPrompt.drawButton(surface, bounds, fitted, enabled, hover || selected, labelFont, labelLine);
			if (selected) surface.drawBoxBorder(bounds.x, bounds.width, bounds.y, bounds.height, 0xD2B873);
			return enabled && hover && client.getMouseClick() == 1;
		}
	}
}
