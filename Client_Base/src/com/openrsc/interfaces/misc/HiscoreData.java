package com.openrsc.interfaces.misc;

/**
 * Client side holder for hiscore data fetched on demand from the server.
 * Data only exists while the hiscores view is open and is cleared (unloaded)
 * whenever the hiscores view or the skill guide is closed.
 */
public class HiscoreData {
	public static final int OVERALL_ID = 255;
	public static final int MAX_ENTRIES = 100;
	private static final long REQUEST_RETRY_MS = 2000;

	private boolean requested = false;
	private boolean loaded = false;
	private long lastRequestTime = 0;
	private int skillId = -1;
	private int ownRank = 0;
	private int ownLevel = 0;
	private long ownExp = 0;
	private int ownListIndex = -1;
	private int count = 0;
	private String[] names;
	private int[] levels;
	private long[] exps;

	public void clear() {
		requested = false;
		loaded = false;
		lastRequestTime = 0;
		skillId = -1;
		ownRank = 0;
		ownLevel = 0;
		ownExp = 0;
		ownListIndex = -1;
		count = 0;
		names = null;
		levels = null;
		exps = null;
	}

	public boolean shouldRequest() {
		if (loaded) {
			return false;
		}
		return !requested || System.currentTimeMillis() - lastRequestTime > REQUEST_RETRY_MS;
	}

	public void markRequested(int skillId) {
		this.requested = true;
		this.loaded = false;
		this.skillId = skillId;
		this.lastRequestTime = System.currentTimeMillis();
	}

	public void setOwnStats(int rank, int level, long exp, int listIndex) {
		this.ownRank = rank;
		this.ownLevel = level;
		this.ownExp = exp;
		this.ownListIndex = listIndex;
	}

	public void setRows(String[] names, int[] levels, long[] exps, int count) {
		this.names = names;
		this.levels = levels;
		this.exps = exps;
		this.count = count;
		this.loaded = true;
	}

	public boolean isRequested() {
		return requested;
	}

	public boolean isLoaded() {
		return loaded;
	}

	public int getSkillId() {
		return skillId;
	}

	public int getOwnRank() {
		return ownRank;
	}

	public int getOwnLevel() {
		return ownLevel;
	}

	public long getOwnExp() {
		return ownExp;
	}

	public int getOwnListIndex() {
		return ownListIndex;
	}

	public int getCount() {
		return count;
	}

	public String getName(int index) {
		return names[index];
	}

	public int getLevel(int index) {
		return levels[index];
	}

	public long getExp(int index) {
		return exps[index];
	}
}
