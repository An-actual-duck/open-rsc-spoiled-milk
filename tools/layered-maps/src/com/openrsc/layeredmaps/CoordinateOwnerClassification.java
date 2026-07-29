package com.openrsc.layeredmaps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Stable AI- and operator-readable Java coordinate-owner classification. */
final class CoordinateOwnerClassification {
	final Map<String, Object> document;
	final String sourceFingerprint;
	final String classificationFingerprint;
	final int sourceOwnerCount;
	final List<CoordinateOwnerClassifier.Classification> classifications;
	final String markdown;

	CoordinateOwnerClassification(
		Map<String, Object> document,
		String sourceFingerprint,
		String classificationFingerprint,
		int sourceOwnerCount,
		List<CoordinateOwnerClassifier.Classification> classifications,
		String markdown) {
		this.document = document;
		this.sourceFingerprint = sourceFingerprint;
		this.classificationFingerprint = classificationFingerprint;
		this.sourceOwnerCount = sourceOwnerCount;
		this.classifications = Collections.unmodifiableList(
			new ArrayList<CoordinateOwnerClassifier.Classification>(classifications));
		this.markdown = markdown;
	}

	String toJson() {
		return JsonDocuments.pretty(document);
	}

	String toMarkdown() {
		return markdown;
	}
}
