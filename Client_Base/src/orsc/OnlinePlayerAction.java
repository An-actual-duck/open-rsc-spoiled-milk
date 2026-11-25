package orsc;

/** Actions shared by the legacy online list and the scaled Social tab. */
public enum OnlinePlayerAction {
	ADD_FRIEND("Add friend", Role.PLAYER, null),
	ADD_IGNORE("Add ignore", Role.PLAYER, null),
	INVITE_TO_PARTY("Invite to Party", Role.PLAYER, null),
	GOTO("Goto", Role.MODERATOR, "goto %s"),
	SUMMON("Summon Player", Role.MODERATOR, "summon %s"),
	RETURN("Return Player", Role.MODERATOR, "return %s"),
	INVENTORY("Inspect inventory", Role.MODERATOR, "inventory %s"),
	BANK("Inspect bank", Role.MODERATOR, "bank %s"),
	MUTE("10 minute mute", Role.MODERATOR, "mute %s 10"),
	PERMANENT_MUTE("Permanent mute", Role.MODERATOR, "mute %s -1"),
	UNMUTE("Unmute", Role.MODERATOR, "mute %s 0"),
	KICK("Kick", Role.MODERATOR, "kick %s"),
	JAIL("Jail", Role.SUPER_MODERATOR, "jail %s"),
	RELEASE("Release", Role.SUPER_MODERATOR, "release %s"),
	BAN("10 minute ban", Role.ADMINISTRATOR, "ban %s 10"),
	PERMANENT_BAN("Permanent ban", Role.ADMINISTRATOR, "ban %s -1"),
	DEMOTE("Demote to player", Role.ADMINISTRATOR, "rank %s 10"),
	PROMOTE_MOD("Promote to mod", Role.ADMINISTRATOR, "rank %s 3"),
	PROMOTE_SUPER_MOD("Promote to super mod", Role.ADMINISTRATOR, "rank %s 2"),
	PROMOTE_ADMIN("Promote to admin", Role.ADMINISTRATOR, "rank %s 1");

	private enum Role { PLAYER, MODERATOR, SUPER_MODERATOR, ADMINISTRATOR }

	public final String label;
	private final Role role;
	private final String command;

	OnlinePlayerAction(String label, Role role, String command) {
		this.label = label;
		this.role = role;
		this.command = command;
	}

	public boolean isStaffAction() {
		return role != Role.PLAYER;
	}

	public boolean isAvailable(ORSCharacter viewer) {
		if (viewer == null) return false;
		if (!isStaffAction()) return true;
		if (!viewer.isMod()) return false;
		if (role == Role.SUPER_MODERATOR) return viewer.isSuperMod();
		if (role == Role.ADMINISTRATOR) return viewer.isOwner() || viewer.isAdmin();
		return true;
	}

	public void execute(mudclient client, String username) {
		if (!isAvailable(client.getLocalPlayer())) return;
		String target = username.replace(' ', '_');
		switch (this) {
			case ADD_FRIEND: client.addFriend(target); break;
			case ADD_IGNORE: client.addIgnore(target); break;
			case INVITE_TO_PARTY: client.addPartyInv(target); break;
			default: client.sendCommandString(String.format(command, target)); break;
		}
	}
}
