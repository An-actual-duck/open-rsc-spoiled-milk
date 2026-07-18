package com.openrsc.layeredmaps;

import java.util.Map;

/** Stable AI- and operator-readable Java coordinate-owner classification. */
final class CoordinateOwnerClassification {
	final Map<String, Object> document;
	final String sourceFingerprint;
	final String classificationFingerprint;
	final int sourceOwnerCount;
	final String markdown;

	CoordinateOwnerClassification(
		Map<String, Object> document,
		String sourceFingerprint,
		String classificationFingerprint,
		int sourceOwnerCount,
		String markdown) {
		this.document = document;
		this.sourceFingerprint = sourceFingerprint;
		this.classificationFingerprint = classificationFingerprint;
		this.sourceOwnerCount = sourceOwnerCount;
		this.markdown = markdown;
	}

	String toJson() {
		return JsonDocuments.pretty(document);
	}

	String toMarkdown() {
		return markdown;
	}
}
