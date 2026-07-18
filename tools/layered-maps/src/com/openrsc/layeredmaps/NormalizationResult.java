package com.openrsc.layeredmaps;

import java.util.Map;

/** Deterministic Slice 2 inventory plus compact operator report. */
final class NormalizationResult {
	final Map<String, Object> document;
	final Map<String, Object> summaryDocument;
	final String sourceFingerprint;
	final String inventoryFingerprint;
	final int terrainSectorCount;
	final int placementRecordCount;
	final int transitionCount;
	final int unresolvedCoordinateCount;
	final String markdown;

	NormalizationResult(
		Map<String, Object> document,
		Map<String, Object> summaryDocument,
		String sourceFingerprint,
		String inventoryFingerprint,
		int terrainSectorCount,
		int placementRecordCount,
		int transitionCount,
		int unresolvedCoordinateCount,
		String markdown) {
		this.document = document;
		this.summaryDocument = summaryDocument;
		this.sourceFingerprint = sourceFingerprint;
		this.inventoryFingerprint = inventoryFingerprint;
		this.terrainSectorCount = terrainSectorCount;
		this.placementRecordCount = placementRecordCount;
		this.transitionCount = transitionCount;
		this.unresolvedCoordinateCount = unresolvedCoordinateCount;
		this.markdown = markdown;
	}

	String toJson() {
		return JsonDocuments.pretty(document);
	}

	String toMarkdown() {
		return markdown;
	}

	String toSummaryJson() {
		return JsonDocuments.pretty(summaryDocument);
	}
}
