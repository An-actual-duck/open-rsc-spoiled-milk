package com.openrsc.server.database.struct;

public class HiscoreEntry {
	public String username;
	// Max stat for the skill, or the summed total level for the overall board
	public int level;
	// Raw x4 fixed point experience, already masked to unsigned 32-bit range
	public long experience;
}
