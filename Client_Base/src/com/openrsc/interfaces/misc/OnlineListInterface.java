package com.openrsc.interfaces.misc;

import com.openrsc.interfaces.InputListener;
import com.openrsc.interfaces.MenuAction;
import com.openrsc.interfaces.NComponent;
import com.openrsc.interfaces.NRightClickMenu;
import orsc.graphics.gui.Panel;
import orsc.mudclient;

public class OnlineListInterface extends NComponent {

	private static final int PANEL_WIDTH = 184;
	private static final int PANEL_HEIGHT = 246;
	private static final int PANEL_MARGIN = 6;
	private static final int TITLE_HEIGHT = 20;

	public int scroll;
	public Panel panel;
	private NRightClickMenu rightClickMenu;
	private NComponent userListContainer;
	private NComponent titleText;
	private boolean ignoredList;

	public OnlineListInterface(mudclient client) {
		super(client);

		panel = new Panel(client.getSurface(), 1);
		scroll = panel.addScrollingList2(getX(), getY() + TITLE_HEIGHT, getWidth(), getHeight() - TITLE_HEIGHT, 500, 1, true);

		setBackground(10000536, 10000536, 128);
		setSize(PANEL_WIDTH, PANEL_HEIGHT);
		anchorToBottomRight();
		setInputListener(new InputListener() {
			@Override
			public boolean onMouseDown(int clickX, int clickY, int mButtonDown, int mButtonClick) {
				return true;
			}

			@Override
			public boolean onMouseMove(int x, int y) {
				return true;
			}

		});
		NComponent title = new NComponent(client);
		title.setBackground(3093151, 0x7e8d09, 192);
		title.setLocation(0, 0);
		title.setSize(PANEL_WIDTH, TITLE_HEIGHT);

		titleText = new NComponent(client);
		titleText.setText("Online Players");
		titleText.setFontColor(0xFFFFFF, 0xFFFFFF);
		titleText.setTextSize(1);
		titleText.setLocation(2, 1);

		NComponent close = new NComponent(client);
		close.setText("Close");
		close.setLocation(PANEL_WIDTH - 40, 1);
		close.setTextSize(1);
		close.setSize(39, TITLE_HEIGHT);
		close.setInputListener(new InputListener() {
			@Override
			public boolean onMouseDown(int clickX, int clickY, int mButtonDown, int mButtonClick) {
				if (mButtonClick == 1) {
					setVisible(false);
					getClient().setMouseClick(0);
				}
				return true;
			}
		});
		close.setFontColor(0xFFFFFF, 0xFF0000);
		title.addComponent(close);
		addComponent(title);
		addComponent(titleText);

		userListContainer = new NComponent(client);
		userListContainer.setFontColor(0xFFFFFF, 0xFFFFFF);
		userListContainer.setLocation(1, TITLE_HEIGHT + 1);
		userListContainer.setSize(getWidth() - 3, getHeight() - TITLE_HEIGHT);
		addComponent(userListContainer);

		rightClickMenu = new NRightClickMenu(this);
		addComponent(rightClickMenu);
		setVisible(false);
	}

	public void addOnlineUser(final String user, final int crownID, String location, boolean isLast) {
		addUser(user, crownID, location);
	}

	public void addIgnoredUser(final String user) {
		addUser(user, 0, "");
	}

	private void addUser(final String user, final int crownID, String location) {
		String text = user;
		if (!location.equals("")) {
			text += " (" + location + ")";
		}
		final NComponent userComponent = new NComponent(getClient());
		userComponent.setText(text);
		userComponent.setFontColor(0xFFFFFF, 0xFF0000);
		userComponent.setTextSize(1);
		userComponent.setLocation(5, 0);
		userComponent.setSize(userListContainer.getWidth() - 10, graphics().fontHeight(1));
		userComponent.setCrownDisplay(true);
		userComponent.setCrown(crownID);

		userListContainer.addComponent(userComponent);
	}

	@Override
	public void update() {
		anchorToBottomRight();
		panel.handleMouse(getClient().getMouseX(), getClient().getMouseY(), getClient().getMouseButtonDown(),
			getClient().getLastMouseDown());
		panel.reposition(scroll, getX(), getY() + TITLE_HEIGHT, getWidth(), getHeight() - TITLE_HEIGHT);
		panel.clearList(scroll);
		int currentY = 0;
		int startComponentIndex = panel.getScrollPosition(scroll);
		int visibleRows = Math.max(1, (getHeight() - TITLE_HEIGHT - 6) / graphics().fontHeight(1));
		int listEndPoint = startComponentIndex + visibleRows;

		for (int componentIndex = 0; componentIndex < userListContainer.subComponents().size(); componentIndex++) {
			final NComponent userComp = userListContainer.subComponents().get(componentIndex);
			userComp.setVisible(false);
			panel.setListEntry(scroll, componentIndex, "", 0, null, null);

			if (componentIndex < startComponentIndex || componentIndex > listEndPoint)
				continue;

			int textHeight = graphics().fontHeight(1);

			userComp.setInputListener(new InputListener() {
				@Override
				public boolean onMouseDown(int clickX, int clickY, int mButtonDown, int mButtonClick) {
					if (mButtonClick == 2) {
						rightClickMenu.hide();
						final String username = userComp.getText()
							.replace(", ", "")
							.replaceAll("\\(.*\\)", "")
							.replaceAll(" ", "_");
						if (ignoredList) {
							rightClickMenu.createOption("Remove from list", new MenuAction() {
								@Override
								public void action() {
									getClient().removeIgnore(username);
									getClient().showIgnoredList();
								}
							});
							rightClickMenu.show(userComp.x, userComp.y);
							return true;
						}
						NRightClickMenu staffMenu = new NRightClickMenu(OnlineListInterface.this);

						// Moderator menu options
						if (getClient().getLocalPlayer().isMod()) {
							staffMenu.createOption("Goto", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("goto " + username);
								}
							});
							staffMenu.createOption("Summon Player", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("summon " + username);
								}
							});
							staffMenu.createOption("Return Player", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("return " + username);
								}
							});
							staffMenu.createOption("Inspect inventory", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("inventory " + username);
								}
							});
							staffMenu.createOption("Inspect bank", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("bank " + username);
								}
							});
							staffMenu.createOption("10 minute mute", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("mute " + username + " 10");
								}
							});
							staffMenu.createOption("Permanent mute", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("mute " + username + " -1");
								}
							});
							staffMenu.createOption("Unmute", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("mute " + username + " 0");
								}
							});
							staffMenu.createOption("Kick", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("kick " + username + "");
								}
							});
						}

						// Super Moderator menu options
						if (getClient().getLocalPlayer().isSuperMod()) {
							staffMenu.createOption("Jail", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("jail " + username + "");
								}
							});
							staffMenu.createOption("Release", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("release " + username + "");
								}
							});
						}

						// Administrator menu options
						if (getClient().getLocalPlayer().isOwner() || getClient().getLocalPlayer().isAdmin()) {
							staffMenu.createOption("10 minute ban", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("ban " + username + " 10");
								}
							});
							staffMenu.createOption("Permanent ban", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("ban " + username + " -1");
								}
							});
							staffMenu.createOption("Demote to player", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("rank " + username + " 10");
								}
							});
							staffMenu.createOption("Promote to mod", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("rank " + username + " 3");
								}
							});
							staffMenu.createOption("Promote to super mod", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("rank " + username + " 2");
								}
							});
							staffMenu.createOption("Promote to admin", new MenuAction() {
								@Override
								public void action() {
									getClient().sendCommandString("rank " + username + " 1");
								}
							});
						}

						// Regular player menu options
						rightClickMenu.createOption("Add friend", new MenuAction() {
							@Override
							public void action() {
								getClient().addFriend(username);
							}
						});
						rightClickMenu.createOption("Add ignore", new MenuAction() {
							@Override
							public void action() {
								getClient().addIgnore(username);
							}
						});
						rightClickMenu.createOption("Invite to Party", new MenuAction() {
							@Override
							public void action() {
								getClient().addPartyInv(username);
							}
						});

						if (getClient().getLocalPlayer().isMod())
							rightClickMenu.createSubMenuOption("Staff >", null, staffMenu);

						rightClickMenu.show(userComp.x, userComp.y);
						return true;
					}
					return false;
				}
			});
			userComp.setLocation(5, currentY + 3);
			userComp.setSize(userListContainer.getWidth() - 10, textHeight);
			userComp.setVisible(true);

			currentY += textHeight;
		}
		titleText.setText((ignoredList ? "Ignored Players: " : "Online Players: ")
			+ userListContainer.subComponents().size());

		panel.drawPanel();
	}

	public void reset() {
		userListContainer.subComponents().clear();
		ignoredList = false;
	}

	public void showIgnoredUsers(final String[] ignoredUsers, final int count) {
		reset();
		ignoredList = true;
		for (int i = 0; i < count; i++) {
			if (ignoredUsers[i] != null && !ignoredUsers[i].isEmpty()) {
				addIgnoredUser(ignoredUsers[i]);
			}
		}
		setVisible(true);
	}

	private void anchorToBottomRight() {
		setLocation(
			Math.max(PANEL_MARGIN, getClient().getGameWidth() - getWidth() - PANEL_MARGIN),
			Math.max(PANEL_MARGIN, getClient().getGameHeight() - getHeight() - PANEL_MARGIN));
	}
}
