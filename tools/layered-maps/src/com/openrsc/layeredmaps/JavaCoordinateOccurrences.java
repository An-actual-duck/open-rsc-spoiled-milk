package com.openrsc.layeredmaps;

import java.util.Map;

/** Stable AI- and operator-readable coordinate occurrence inventory. */
final class JavaCoordinateOccurrences {
	final Map<String, Object> document;
	final String occurrenceFingerprint;
	final int sourceCount;
	final int occurrenceCount;
	final String markdown;

	JavaCoordinateOccurrences(
		Map<String, Object> document,
		String occurrenceFingerprint,
		int sourceCount,
		int occurrenceCount,
		String markdown) {
		this.document = document;
		this.occurrenceFingerprint = occurrenceFingerprint;
		this.sourceCount = sourceCount;
		this.occurrenceCount = occurrenceCount;
		this.markdown = markdown;
	}

	String toJson() {
		return JsonDocuments.pretty(document);
	}

	String toMarkdown() {
		return markdown;
	}
}
