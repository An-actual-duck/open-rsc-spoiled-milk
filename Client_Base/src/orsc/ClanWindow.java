package orsc;

import com.openrsc.interfaces.misc.clan.Clan;
import com.openrsc.interfaces.misc.clan.ClanInterface;
import com.openrsc.interfaces.misc.clan.ClanInterface.ClanResult;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import orsc.graphics.gui.SocialLists;
import orsc.graphics.two.MudClientGraphics;
import orsc.util.StringUtil;

/** Scaled clan setup, search, invitations, and member management. */
final class ClanWindow {
	enum View { CREATE, SEARCH, MEMBERS, PERMISSIONS, INVITATION }
	enum Confirmation {
		LEAVE("Leave Clan", "Leave Clan"),
		KICK("Kick Member", "Kick Member"),
		TRANSFER("Transfer Leadership", "Transfer");

		final String title, button;

		Confirmation(String title, String button) {
			this.title = title;
			this.button = button;
		}
	}

	private static final int CLAN_LIMIT = 15;
	private static final String[] PERMISSIONS = {"Who can kick members?", "Who can invite players?", "Who can join?"};
	private static final String[] RANKS = {"Anyone", "Owner", "General+"};
	private static final String[] JOIN = {"Anyone can join", "Invite only", "Closed"};

	private final ModalTextField name = new ModalTextField(16, ClanWindow::nameCharacter);
	private final ModalTextField tag = new ModalTextField(5, ClanWindow::nameCharacter);
	private final ModalTextField search = new ModalTextField(16, ClanWindow::nameCharacter);
	private final String inviter, invitedClan;
	private final long expiresAt;
	private View view;
	private Confirmation confirmation;
	private String member;
	private int selectedClan = -1;
	private int firstRow, wheel, key;
	private Rectangle scrollArea;
	private ModalTextField focus;
	private List<ClanResult> results = new ArrayList<>();
	private int searchRevision = -1;
	private String query;

	ClanWindow(View view) {
		this(view, "", "");
	}

	ClanWindow(String inviter, String clan) {
		this(View.INVITATION, inviter, clan);
	}

	private ClanWindow(View view, String inviter, String clan) {
		this.inviter = inviter;
		this.invitedClan = clan;
		expiresAt = view == View.INVITATION ? System.nanoTime() + TimeUnit.SECONDS.toNanos(60) : 0;
		show(view);
	}

	static boolean nameCharacter(int key) {
		return Character.isLetterOrDigit(key) || key == ' ';
	}

	void confirm(Confirmation action, String target) {
		confirmation = action;
		member = target;
	}

	private void show(View next) {
		view = next;
		focus = next == View.CREATE ? name : next == View.SEARCH ? search : null;
		member = null;
		selectedClan = -1;
		firstRow = wheel = 0;
		scrollArea = null;
	}

	synchronized void keyPressed(char typed, int code, boolean shortcut) {
		if (code == KeyEvent.VK_ESCAPE || code == KeyEvent.VK_ENTER) {
			key = code;
		} else if (code == KeyEvent.VK_TAB && view == View.CREATE) {
			focus = focus == name ? tag : name;
		} else if (focus != null && confirmation == null) {
			focus.keyPressed(typed, code, shortcut);
		}
	}

	synchronized void scroll(int x, int y, int direction) {
		if (scrollArea != null && scrollArea.contains(x, y) && confirmation == null) {
			wheel += Integer.signum(direction);
		}
	}

	synchronized boolean draw(mudclient client, int bottom) {
		Clan clan = client.clan;
		ClanInterface actions = clan.getClanInterface();
		int pressed = key;
		key = 0;
		if (view == View.INVITATION && (clan.inClan() || System.nanoTime() - expiresAt >= 0)) return true;
		if (pressed == KeyEvent.VK_ESCAPE) {
			if (confirmation != null) confirmation = null;
			else if (view == View.SEARCH && selectedClan != -1) {
				selectedClan = -1;
				focus = search;
			} else {
				if (view == View.INVITATION) actions.sendClanDecline();
				return true;
			}
		}
		if (clan.inClan() && (view == View.SEARCH || view == View.CREATE)) show(View.MEMBERS);
		if (!clan.inClan() && (view == View.MEMBERS || view == View.PERMISSIONS)) return true;
		if (view == View.PERMISSIONS && !clan.isClanLeader()) show(View.MEMBERS);
		Layout layout = new Layout(client, bottom);

		if (confirmation != null) return drawConfirmation(clan, actions, layout);
		switch (view) {
			case CREATE: return drawCreate(actions, layout, pressed);
			case SEARCH: return drawSearch(actions, layout);
			case MEMBERS: return drawMembers(clan, actions, layout);
			case PERMISSIONS: return drawPermissions(clan, actions, layout);
			case INVITATION: return drawInvitation(actions, layout);
			default: throw new AssertionError(view);
		}
	}

	private boolean drawCreate(ClanInterface actions, Layout l, int pressed) {
		l.frame("Create Clan", l.section + 2 * l.fieldBlock());
		if (guestTabs(actions, l)) return false;
		int y = l.top + l.section;
		l.field("Clan name (2-16 characters)", name, y);
		l.field("Clan tag (2-5 characters)", tag, y + l.fieldBlock());
		String clanName = name.value().trim();
		String clanTag = tag.value().trim();
		boolean valid = clanName.length() >= 2 && clanTag.length() >= 2;
		if (l.button(1, 2, l.footer, "Create Clan", valid, false)
			|| valid && pressed == KeyEvent.VK_ENTER) {
			actions.sendCreateClan(clanName, clanTag);
		}
		return l.button(0, 2, l.footer, "Close", true, false);
	}

	private boolean guestTabs(ClanInterface actions, Layout l) {
		if (l.button(0, 2, l.top, "Create Clan", true, view == View.CREATE) && view != View.CREATE) {
			show(View.CREATE);
			return true;
		}
		if (l.button(1, 2, l.top, "Find a Clan", true, view == View.SEARCH) && view != View.SEARCH) {
			show(View.SEARCH);
			actions.sendClanSearch();
			return true;
		}
		return false;
	}

	private boolean drawInvitation(ClanInterface actions, Layout l) {
		l.frame("Clan Invitation", 2 * l.bodyLine);
		l.text("Invitation from " + inviter, l.left, l.top + l.bodyLine - 3, l.width, 0xFFFFFF, l.bodyFont);
		l.text("Join " + invitedClan + "?", l.left, l.top + 2 * l.bodyLine - 3, l.width, 0xFFDC82, l.bodyFont);
		if (l.button(0, 2, l.footer, "Decline", true, false)) {
			actions.sendClanDecline();
			return true;
		}
		if (l.button(1, 2, l.footer, "Accept", true, false)) {
			actions.sendClanAccept();
			return true;
		}
		return false;
	}

	private boolean drawSearch(ClanInterface actions, Layout l) {
		String filter = search.value().trim().toLowerCase(Locale.ROOT);
		if (actions.searchRevision() != searchRevision || !filter.equals(query)) {
			if (!filter.equals(query)) firstRow = 0;
			query = filter;
			searchRevision = actions.searchRevision();
			results = actions.searchResults();
			results.removeIf(clan -> !clan.getClanName().toLowerCase(Locale.ROOT).contains(filter)
				&& !clan.getClanTag().toLowerCase(Locale.ROOT).contains(filter));
		}
		ClanResult selected = null;
		for (ClanResult result : results) {
			if (result.getClanID() == selectedClan) {
				selected = result;
				break;
			}
		}
		if (selected != null) return drawClanDetails(l, selected);

		int fixed = l.section + l.fieldBlock() + l.line + l.gap;
		int rows = l.visibleRows(fixed);
		l.frame("Find a Clan", fixed + rows * l.row);
		if (guestTabs(actions, l)) return false;
		l.field("Search by clan name or tag", search, l.top + l.section);
		int y = l.top + l.section + l.fieldBlock();
		l.columns(y + l.line - 3, "Clan", "Members", "Points");
		y += l.line + l.gap;
		scrollArea = new Rectangle(l.left, y, l.width, rows * l.row);
		page(results.size(), rows);
		if (results.isEmpty()) l.text("No clans found.", l.left, y + l.line, l.width, 0xC4D0E0, l.font);
		for (int index = firstRow; index < Math.min(firstRow + rows, results.size()); index++) {
			ClanResult clan = results.get(index);
			if (l.row(y, false, true)) {
				selectedClan = clan.getClanID();
				focus = null;
			}
			l.columns(y + (l.row + l.line) / 2 - 3, label(clan),
				clan.getClanMembersTotal() + "/" + CLAN_LIMIT, Integer.toString(clan.getClanPoints()));
			y += l.row;
		}
		if (l.button(2, 4, l.footer, "Refresh", true, false)) actions.sendClanSearch();
		drawPager(l, results.size(), rows, 4);
		return l.button(3, 4, l.footer, "Close", true, false);
	}

	private boolean drawClanDetails(Layout l, ClanResult result) {
		scrollArea = null;
		focus = null;
		l.frame(label(result), 4 * l.line);
		int y = l.top + l.line - 3;
		int join = result.getJoinSetting();
		l.text("Members  " + result.getClanMembersTotal() + "/" + CLAN_LIMIT, l.left, y, l.width, 0xFFFFFF, l.font);
		l.text("Points  " + result.getClanPoints(), l.left, y + l.line, l.width, 0xFFFFFF, l.font);
		l.text("Rank  #" + result.getClanGlobalRank(), l.left, y + 2 * l.line, l.width, 0xFFFFFF, l.font);
		l.text(JOIN[join], l.left, y + 3 * l.line, l.width, 0xFFDC82, l.font);
		if (l.button(0, 2, l.footer, "Back to Search", true, false)) {
			selectedClan = -1;
			focus = search;
		}
		if (l.button(1, 2, l.footer, "Join Clan", join == 0 && result.getClanMembersTotal() < CLAN_LIMIT, false)) {
			l.client.sendCommandString("joinclan " + result.getClanName().replace(' ', '_'));
		}
		return false;
	}

	private static String label(ClanResult clan) {
		return clan.getClanName() + " [" + clan.getClanTag() + "]";
	}

	private boolean drawMembers(Clan clan, ClanInterface actions, Layout l) {
		int count = Math.min(CLAN_LIMIT, SocialLists.clanListCount);
		int index = memberIndex(clan);
		boolean owner = clan.isClanLeader();
		boolean canManage = owner || clan.isAllowed(0);
		int fixed = (owner ? l.section : 0) + 2 * l.line + l.gap + (canManage ? l.section : 0);
		int rows = l.visibleRows(fixed);
		l.frame("Clan Members", fixed + rows * l.row);
		if (owner && membershipTabs(l)) return false;
		int y = l.top + (owner ? l.section : 0);
		l.text(clan.getClanName() + " [" + clan.getClanTag() + "]  " + count + "/" + CLAN_LIMIT,
			l.left, y + l.line - 3, l.width, 0xFFDC82, l.font);
		l.columns(y + 2 * l.line - 3, "Member", "Rank", "Status");
		y += 2 * l.line + l.gap;
		scrollArea = new Rectangle(l.left, y, l.width, rows * l.row);
		page(count, rows);
		String local = StringUtil.displayNameToKey(l.client.getUsername());
		for (int row = firstRow; row < Math.min(firstRow + rows, count); row++) {
			String target = clan.username[row];
			boolean selectable = canManage && target != null && clan.clanRank[row] != 1
				&& !local.equals(StringUtil.displayNameToKey(target));
			if (l.row(y, row == index, selectable)) member = target;
			String rank = clan.clanRank[row] == 0 ? "Member" : clan.getClanRankNames(clan.clanRank[row]);
			l.columns(y + (l.row + l.line) / 2 - 3, target, rank, clan.onlineClanMember[row] != 0 ? "Online" : "Offline");
			y += l.row;
		}
		if (canManage) {
			int actionY = l.footer - l.section;
			boolean general = index >= 0 && clan.clanRank[index] == 2;
			if (l.button(0, 3, actionY, general ? "Remove Rank" : "Make General", owner && index >= 0, false)) {
				actions.sendClanRank(member, general ? 0 : 2);
			}
			if (l.button(1, 3, actionY, "Transfer Leader", owner && index >= 0, false)) confirmation = Confirmation.TRANSFER;
			if (l.button(2, 3, actionY, "Kick Member", index >= 0 && clan.isAllowed(0), false)) confirmation = Confirmation.KICK;
		}
		drawPager(l, count, rows, 5);
		return drawManagementFooter(clan, l, 2, 5);
	}

	private boolean membershipTabs(Layout l) {
		if (l.button(0, 2, l.top, "Members", true, view == View.MEMBERS) && view != View.MEMBERS) {
			show(View.MEMBERS);
			return true;
		}
		if (l.button(1, 2, l.top, "Permissions", true, view == View.PERMISSIONS) && view != View.PERMISSIONS) {
			show(View.PERMISSIONS);
			return true;
		}
		return false;
	}

	private boolean drawPermissions(Clan clan, ClanInterface actions, Layout l) {
		scrollArea = null;
		l.frame("Clan Permissions", l.section + PERMISSIONS.length * (l.line + l.section));
		if (membershipTabs(l)) return false;
		int y = l.top + l.section;
		for (int setting = 0; setting < PERMISSIONS.length; setting++) {
			l.text(PERMISSIONS[setting], l.left, y + l.line - 3, l.width, 0xC4D0E0, l.font);
			y += l.line;
			String[] choices = setting == 2 ? JOIN : RANKS;
			int current = clan.getClanSetting(setting);
			for (int choice = 0; choice < choices.length; choice++) {
				if (l.button(choice, choices.length, y, choices[choice], true, current == choice) && current != choice) {
					actions.sendClanSettings(setting, choice);
				}
			}
			y += l.section;
		}
		return drawManagementFooter(clan, l, 0, 3);
	}

	private boolean drawManagementFooter(Clan clan, Layout l, int from, int count) {
		if (l.button(from, count, l.footer, "Invite Player", clan.isAllowed(1), false)) l.client.openClanInvitePrompt();
		if (l.button(from + 1, count, l.footer, "Leave Clan", true, false)) confirmation = Confirmation.LEAVE;
		return l.button(from + 2, count, l.footer, "Close", true, false);
	}

	private int memberIndex(Clan clan) {
		for (int index = 0; member != null && index < Math.min(CLAN_LIMIT, SocialLists.clanListCount); index++) {
			if (clan.clanRank[index] != 1 && clan.username[index] != null
				&& StringUtil.displayNameToKey(member).equals(StringUtil.displayNameToKey(clan.username[index]))) return index;
		}
		return -1;
	}

	private boolean drawConfirmation(Clan clan, ClanInterface actions, Layout l) {
		Confirmation pending = confirmation;
		boolean allowed = pending == Confirmation.LEAVE || memberIndex(clan) >= 0
			&& (pending == Confirmation.KICK ? clan.isAllowed(0) : clan.isClanLeader());
		String question = pending == Confirmation.LEAVE ? "Leave your current clan?"
			: pending == Confirmation.KICK ? "Remove " + member + " from the clan?" : "Make " + member + " the clan leader?";
		l.frame(pending.title, l.bodyLine);
		l.text(question, l.left, l.top + l.bodyLine - 3, l.width, 0xFFFFFF, l.bodyFont);
		if (l.button(0, 2, l.footer, "Cancel", true, false)) confirmation = null;
		if (l.button(1, 2, l.footer, pending.button, allowed, false)) {
			confirmation = null;
			if (pending == Confirmation.LEAVE) {
				actions.sendClanLeave();
				return true;
			}
			if (pending == Confirmation.KICK) actions.sendClanKick(member);
			else actions.sendClanRank(member, 1);
			member = null;
		}
		return false;
	}

	private void page(int count, int rows) {
		firstRow = Math.max(0, Math.min(firstRow + wheel, Math.max(0, count - rows)));
		wheel = 0;
	}

	private void drawPager(Layout l, int count, int rows, int buttons) {
		int max = Math.max(0, count - rows);
		if (max == 0) return;
		if (l.button(0, buttons, l.footer, "Previous", firstRow > 0, false)) firstRow = Math.max(0, firstRow - rows);
		if (l.button(1, buttons, l.footer, "Next", firstRow < max, false)) firstRow = Math.min(max, firstRow + rows);
	}

	private final class Layout {
		final mudclient client;
		final MudClientGraphics surface;
		final int bottom, x, left, width, frameWidth, pad, gap, font, bodyFont, bodyLine, line, header, button, row, section;
		int top, footer;

		Layout(mudclient client, int bottom) {
			this.client = client;
			this.bottom = bottom;
			surface = client.getSurface();
			GameInterfaceSettings.Size size = GameInterfaceSettings.size();
			GameInterfaceSettings.TextSize text = GameInterfaceSettings.textSize();
			pad = size.pixels(12);
			gap = size.pixels(6);
			font = text.labelFont;
			bodyFont = text.bodyFont;
			line = surface.fontHeight(font);
			bodyLine = surface.fontHeight(bodyFont);
			header = Math.max(size.pixels(40), bodyLine + pad);
			button = Math.max(size.pixels(29), line + gap);
			row = Math.max(size.pixels(29), line + gap);
			section = button + gap;
			frameWidth = Math.min(client.getGameWidth() - 2 * pad, size.pixels(690));
			x = (client.getGameWidth() - frameWidth) / 2;
			left = x + pad;
			width = frameWidth - 2 * pad;
		}

		int fieldBlock() {
			return line + bodyLine + 3 * gap;
		}

		int visibleRows(int fixed) {
			return Math.max(1, Math.min(8, (bottom - header - button - 5 * pad - fixed) / row));
		}

		void frame(String title, int content) {
			int height = header + content + button + 3 * pad;
			int y = Math.max(pad, (bottom - height) / 2);
			top = y + header + pad;
			footer = top + content + pad;
			String label = ContextMenuLayout.fitLabel(title, width, part -> surface.literalStringWidth(bodyFont, part));
			SocialTextPrompt.drawFrame(surface, new Rectangle(x, y, frameWidth, height), header, pad, label, bodyFont, bodyLine);
		}

		void field(String label, ModalTextField field, int y) {
			text(label, left, y + line - 3, width, 0xC4D0E0, font);
			Rectangle box = new Rectangle(left, y + line, width, bodyLine + 2 * gap);
			if (hover(box) && client.getMouseClick() == 1) focus = field;
			field.draw(client, box, bodyFont, gap, focus == field);
		}

		boolean row(int y, boolean selected, boolean selectable) {
			Rectangle box = new Rectangle(left, y, width, row - 1);
			boolean hovered = selectable && hover(box);
			surface.drawBox(box.x, box.y, box.width, box.height, selected || hovered ? 0x405978 : 0x263449);
			if (selected) surface.drawBoxBorder(box.x, box.width, box.y, box.height, 0xD2B873);
			return hovered && click();
		}

		void columns(int y, String first, String second, String third) {
			int one = width / 2;
			int two = width / 4;
			text(first, left + gap, y, one - 2 * gap, 0xFFFFFF, font);
			text(second, left + one + gap, y, two - 2 * gap, 0xC4D0E0, font);
			text(third, left + one + two + gap, y, width - one - two - 2 * gap, 0xC4D0E0, font);
		}

		void text(String value, int x, int y, int limit, int color, int font) {
			if (value == null) return;
			String shown = ContextMenuLayout.fitLabel(value, limit, part -> surface.literalStringWidth(font, part));
			surface.drawLiteralString(shown, x, y, color, font);
		}

		boolean button(int index, int count, int y, String label, boolean enabled, boolean selected) {
			int available = width + gap;
			int start = available * index / count;
			int end = available * (index + 1) / count;
			Rectangle box = new Rectangle(left + start, y, end - start - gap, button);
			boolean hovered = hover(box);
			String shown = ContextMenuLayout.fitLabel(label, box.width - gap, part -> surface.stringWidth(font, part));
			SocialTextPrompt.drawButton(surface, box, shown, enabled, hovered || selected, font, line);
			if (selected) surface.drawBoxBorder(box.x, box.width, box.y, box.height, 0xD2B873);
			return enabled && hovered && click();
		}

		private boolean hover(Rectangle box) {
			return box.contains(client.getMouseX(), client.getMouseY());
		}

		private boolean click() {
			if (client.getMouseClick() != 1) return false;
			client.setMouseClick(0);
			return true;
		}
	}
}
