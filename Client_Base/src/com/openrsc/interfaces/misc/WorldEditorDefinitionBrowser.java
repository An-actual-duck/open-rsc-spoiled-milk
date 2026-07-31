package com.openrsc.interfaces.misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Presentation-only search and paging state for the shared World Builder
 * definition browser. The catalog remains authoritative for editor labels;
 * selecting a result still writes the original stable definition ID.
 */
final class WorldEditorDefinitionBrowser {
	static final int COLUMNS = 2;
	static final int VISIBLE_ROWS = 4;
	static final int VISIBLE_RESULTS = COLUMNS * VISIBLE_ROWS;
	static final int MAX_QUERY_LENGTH = 48;

	enum Family {
		SCENERY,
		BOUNDARY,
		NPC,
		ITEM
	}

	private Family family = Family.SCENERY;
	private boolean open;
	private String query = "";
	private int firstRow;
	private List<WorldEditorDefinitionCatalog.Entry> results = Collections.emptyList();

	void open(Family selectedFamily, int selectedId) {
		family = selectedFamily == null ? Family.SCENERY : selectedFamily;
		open = true;
		query = "";
		rebuild();
		centerOn(selectedId);
	}

	void close() {
		open = false;
		query = "";
		firstRow = 0;
		results = Collections.emptyList();
	}

	boolean isOpen() {
		return open;
	}

	Family family() {
		return family;
	}

	String query() {
		return query;
	}

	void clearQuery() {
		setQuery("");
	}

	void setQuery(String value) {
		String next = value == null ? "" : value;
		if (next.length() > MAX_QUERY_LENGTH) {
			next = next.substring(0, MAX_QUERY_LENGTH);
		}
		if (query.equals(next)) {
			return;
		}
		query = next;
		rebuild();
	}

	void append(char value) {
		if (query.length() >= MAX_QUERY_LENGTH || !isSearchCharacter(value)) {
			return;
		}
		setQuery(query + value);
	}

	void backspace() {
		if (!query.isEmpty()) {
			setQuery(query.substring(0, query.length() - 1));
		}
	}

	int resultCount() {
		return results.size();
	}

	int firstIndex() {
		return firstRow * COLUMNS;
	}

	int visibleCount() {
		return Math.max(0, Math.min(VISIBLE_RESULTS, resultCount() - firstIndex()));
	}

	WorldEditorDefinitionCatalog.Entry resultAtVisibleSlot(int slot) {
		int index = firstIndex() + slot;
		return slot < 0 || slot >= VISIBLE_RESULTS || index < 0 || index >= resultCount()
			? null : results.get(index);
	}

	void scrollRows(int rows) {
		firstRow = clamp(firstRow + rows, 0, maxFirstRow());
	}

	void page(int direction) {
		scrollRows(direction < 0 ? -VISIBLE_ROWS : VISIBLE_ROWS);
	}

	String rangeLabel() {
		if (results.isEmpty()) {
			return "0 of 0";
		}
		return (firstIndex() + 1) + "-" + (firstIndex() + visibleCount()) + " of " + resultCount();
	}

	private void centerOn(int selectedId) {
		for (int index = 0; index < results.size(); index++) {
			if (results.get(index).id() == selectedId) {
				firstRow = clamp(index / COLUMNS - VISIBLE_ROWS / 2, 0, maxFirstRow());
				return;
			}
		}
		firstRow = 0;
	}

	private void rebuild() {
		List<WorldEditorDefinitionCatalog.Entry> source;
		switch (family) {
			case BOUNDARY:
				source = WorldEditorDefinitionCatalog.boundaryEntries();
				break;
			case NPC:
				source = WorldEditorDefinitionCatalog.npcEntries();
				break;
			case ITEM:
				source = WorldEditorDefinitionCatalog.itemEntries();
				break;
			case SCENERY:
			default:
				source = WorldEditorDefinitionCatalog.sceneryEntries();
				break;
		}
		String normalized = normalized(query);
		if (normalized.isEmpty()) {
			results = source;
			firstRow = clamp(firstRow, 0, maxFirstRow());
			return;
		}
		String[] tokens = normalized.split(" ");
		List<WorldEditorDefinitionCatalog.Entry> filtered =
			new ArrayList<WorldEditorDefinitionCatalog.Entry>();
		for (WorldEditorDefinitionCatalog.Entry entry : source) {
			if (matches(entry, tokens)) {
				filtered.add(entry);
			}
		}
		results = Collections.unmodifiableList(filtered);
		firstRow = 0;
	}

	private static boolean matches(WorldEditorDefinitionCatalog.Entry entry, String[] tokens) {
		String searchable = normalized(entry.displayName() + " " + entry.canonicalName() + " "
			+ entry.tags() + " " + entry.searchTerms());
		for (String token : tokens) {
			if (token.isEmpty()) {
				continue;
			}
			Integer exactId = numericToken(token);
			if (exactId != null) {
				if (entry.id() != exactId.intValue()) {
					return false;
				}
			} else if (!searchable.contains(token)) {
				return false;
			}
		}
		return true;
	}

	private int maxFirstRow() {
		int rows = (resultCount() + COLUMNS - 1) / COLUMNS;
		return Math.max(0, rows - VISIBLE_ROWS);
	}

	private static Integer numericToken(String token) {
		String candidate = token.startsWith("#") ? token.substring(1) : token;
		if (candidate.isEmpty()) {
			return null;
		}
		for (int index = 0; index < candidate.length(); index++) {
			if (!Character.isDigit(candidate.charAt(index))) {
				return null;
			}
		}
		try {
			return Integer.valueOf(candidate);
		} catch (NumberFormatException ignored) {
			return Integer.valueOf(-1);
		}
	}

	private static String normalized(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH).replaceAll("\\s+", " ");
	}

	private static boolean isSearchCharacter(char value) {
		return Character.isLetterOrDigit(value) || Character.isWhitespace(value)
			|| value == '#' || value == '-' || value == '_' || value == '\''
			|| value == '(' || value == ')' || value == '/';
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(value, maximum));
	}
}
