package com.openrsc.layeredmaps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Deterministic lexical triage of unresolved Java coordinate owners. */
final class CoordinateOwnerClassifier {
	static final String REPORT_TYPE = "layered-coordinate-owner-classification";
	static final String SCHEMA_ID = "coordinate-owner-classification-v1";

	private static final Pattern STANDALONE_944 =
		Pattern.compile("(?<![A-Za-z0-9_])944(?![A-Za-z0-9_])");
	private static final Pattern FLOOR_ARITHMETIC = Pattern.compile(
		"Math\\.floorDiv\\s*\\([^\\)]{0,160},\\s*944\\s*\\)"
			+ "|[/\\%*+\\-]\\s*944\\b"
			+ "|\\b944\\s*[*\\%/+\\-]");

	CoordinateOwnerClassification classify(Path root, PreflightReport preflight)
		throws IOException, PreflightException {
		List<Classification> classifications = new ArrayList<Classification>();
		for (PreflightReport.SourceFile source : preflight.candidateSources) {
			if (!source.role.endsWith("coordinate-source")) {
				continue;
			}
			Path path = sourcePath(root, source.path);
			if (Files.size(path) != source.size || !source.sha256.equals(Hashes.sha256(path))) {
				throw new PreflightException(
					"Candidate source changed before owner classification: " + source.path);
			}
			String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			classifications.add(classifySource(source, text));
		}

		List<Object> owners = new ArrayList<Object>();
		Map<String, Integer> dispositions = new TreeMap<String, Integer>();
		Map<String, Integer> families = new TreeMap<String, Integer>();
		Map<String, Integer> risks = new TreeMap<String, Integer>();
		Map<String, Integer> roles = new TreeMap<String, Integer>();
		for (Classification classification : classifications) {
			owners.add(classification.document());
			increment(dispositions, classification.disposition);
			increment(families, classification.primaryFamily);
			increment(risks, classification.migrationRisk);
			increment(roles, classification.source.role);
		}

		String classificationFingerprint = Hashes.sha256(JsonDocuments.canonical(owners));
		Map<String, Object> summary = map();
		summary.put("classifiedSourceOwnerCount", Long.valueOf(classifications.size()));
		summary.put("sourceOwnerCountByDisposition", counts(dispositions));
		summary.put("sourceOwnerCountByFamily", counts(families));
		summary.put("sourceOwnerCountByRisk", counts(risks));
		summary.put("sourceOwnerCountByRole", counts(roles));

		List<Object> notes = new ArrayList<Object>();
		notes.add("Classification is lexical migration triage; Java coordinates remain unparsed and unresolved.");
		notes.add("Signal collisions remain inventoried so later scanner changes cannot silently remove evidence.");
		notes.add("No source file, runtime behavior, persistence value, terrain archive, or placement was modified.");

		Map<String, Object> document = map();
		document.put("schemaVersion", 1L);
		document.put("reportType", REPORT_TYPE);
		document.put("reportSchema", SCHEMA_ID);
		document.put("coordinateModel", PreflightReport.COORDINATE_MODEL);
		document.put("legacyCodec", LegacyPackedCoordinateCodec.ID);
		document.put("layoutAdapter", preflight.layoutAdapter);
		document.put("sourceFingerprintSha256", preflight.sourceFingerprint);
		document.put("classificationFingerprintSha256", classificationFingerprint);
		document.put("summary", summary);
		document.put("owners", owners);
		document.put("notes", notes);
		return new CoordinateOwnerClassification(
			document,
			preflight.sourceFingerprint,
			classificationFingerprint,
			classifications.size(),
			classifications,
			markdown(preflight, classificationFingerprint, dispositions, families, risks, classifications));
	}

	static Classification classifySource(PreflightReport.SourceFile source, String text) {
		if (source == null || text == null) {
			throw new NullPointerException("source and text are required");
		}
		List<String> reasons = new ArrayList<String>();
		reasons.add("role:" + source.role);

		boolean onlyPackedFloorSignal = source.signals.size() == 1
			&& source.signals.contains("packed-floor-stride");
		boolean namedFloorEvidence = text.contains("FLOOR_OFFSET")
			|| text.contains("distanceBetweenFloors");
		boolean arithmeticFloorEvidence = FLOOR_ARITHMETIC.matcher(text).find();
		boolean semanticFloorEvidence = namedFloorEvidence || arithmeticFloorEvidence;
		boolean standalone944 = STANDALONE_944.matcher(text).find();

		String disposition;
		String family;
		String risk;
		String confidence;
		if (onlyPackedFloorSignal && !semanticFloorEvidence && !standalone944) {
			disposition = "signal-collision";
			family = "incidental-signal-review";
			risk = "review";
			confidence = "high";
			reasons.add("signal-collision:packed-floor-substring-only");
		} else if (onlyPackedFloorSignal && !semanticFloorEvidence) {
			disposition = "ambiguous-literal";
			family = "ambiguous-literal-review";
			risk = "review";
			confidence = "low";
			reasons.add("ambiguous-literal:944");
		} else {
			disposition = "migration-owner";
			family = migrationFamily(source);
			risk = migrationRisk(family);
			confidence = "manual-review".equals(family) ? "medium" : "high";
			reasons.add("path-rule:" + pathRule(source, family));
			for (String signal : source.signals) {
				if (!"packed-floor-stride".equals(signal)) {
					reasons.add("semantic-signal:" + signal);
				}
			}
			if (namedFloorEvidence) {
				reasons.add("semantic-signal:named-floor-stride");
			} else if (arithmeticFloorEvidence) {
				reasons.add("semantic-signal:floor-stride-arithmetic");
			} else if (source.signals.contains("packed-floor-stride") && standalone944) {
				reasons.add("semantic-signal:standalone-944-with-other-coordinate-evidence");
			}
		}
		return new Classification(
			source, disposition, family, risk, confidence,
			Collections.unmodifiableList(reasons));
	}

	private static String migrationFamily(PreflightReport.SourceFile source) {
		String path = source.path;
		if ("client-coordinate-source".equals(source.role)) {
			return "client-world-presentation";
		}
		if ("builder-coordinate-source".equals(source.role)) {
			return "builder-authoring";
		}
		if (path.startsWith("server/src/com/openrsc/server/net/rsc/")) {
			return "protocol-session-boundary";
		}
		if (path.startsWith("server/src/com/openrsc/server/io/")
			|| path.startsWith("server/src/com/openrsc/server/model/world/region/")
			|| path.equals("server/src/com/openrsc/server/constants/Constants.java")) {
			return "terrain-region-storage";
		}
		if (path.startsWith("server/src/com/openrsc/server/database/")
			|| path.startsWith("server/src/com/openrsc/server/service/")
			|| path.startsWith("server/src/com/openrsc/server/external/")
			|| path.equals("server/src/com/openrsc/server/Server.java")
			|| path.equals("server/src/com/openrsc/server/model/world/World.java")) {
			return "persistence-world-bootstrap";
		}
		if (path.startsWith("server/plugins/")
			|| path.startsWith("server/src/com/openrsc/server/content/")
			|| path.startsWith("server/src/com/openrsc/server/plugins/")) {
			return "content-topology";
		}
		if (path.startsWith("server/src/com/openrsc/server/model/")
			|| path.startsWith("server/src/com/openrsc/server/event/")
			|| path.equals("server/src/com/openrsc/server/util/rsc/Formulae.java")
			|| path.equals("server/src/com/openrsc/server/GameStateUpdater.java")) {
			return "simulation-spatial-runtime";
		}
		return "manual-review";
	}

	private static String migrationRisk(String family) {
		if ("protocol-session-boundary".equals(family)
			|| "terrain-region-storage".equals(family)
			|| "persistence-world-bootstrap".equals(family)
			|| "simulation-spatial-runtime".equals(family)) {
			return "critical";
		}
		if ("client-world-presentation".equals(family)
			|| "builder-authoring".equals(family)) {
			return "high";
		}
		if ("content-topology".equals(family)) {
			return "medium";
		}
		return "review";
	}

	private static String pathRule(PreflightReport.SourceFile source, String family) {
		if ("client-world-presentation".equals(family)) {
			return "client-source-root";
		}
		if ("builder-authoring".equals(family)) {
			return "builder-source-root";
		}
		if ("protocol-session-boundary".equals(family)) {
			return "server-network-protocol";
		}
		if ("terrain-region-storage".equals(family)) {
			return "server-terrain-region";
		}
		if ("persistence-world-bootstrap".equals(family)) {
			return "server-persistence-bootstrap";
		}
		if ("content-topology".equals(family)) {
			return "server-content-script";
		}
		if ("simulation-spatial-runtime".equals(family)) {
			return "server-simulation-runtime";
		}
		return "unclassified-server-path";
	}

	private static Path sourcePath(Path root, String relative)
		throws IOException, PreflightException {
		Path path = root.resolve(relative).normalize();
		if (!path.startsWith(root) || !Files.isRegularFile(path)) {
			throw new PreflightException("Coordinate owner source is missing: " + relative);
		}
		Path real = path.toRealPath();
		if (!real.startsWith(root)) {
			throw new PreflightException("Coordinate owner source escapes repository root: " + relative);
		}
		return real;
	}

	private static void increment(Map<String, Integer> counts, String key) {
		Integer previous = counts.get(key);
		counts.put(key, previous == null ? 1 : previous + 1);
	}

	private static Map<String, Object> counts(Map<String, Integer> counts) {
		Map<String, Object> result = map();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			result.put(entry.getKey(), Long.valueOf(entry.getValue()));
		}
		return result;
	}

	private static String markdown(
		PreflightReport preflight,
		String classificationFingerprint,
		Map<String, Integer> dispositions,
		Map<String, Integer> families,
		Map<String, Integer> risks,
		List<Classification> classifications) {
		StringBuilder out = new StringBuilder(32 * 1024);
		out.append("# Layered Coordinate Owner Classification\n\n");
		out.append("- Report schema: `").append(SCHEMA_ID).append("`\n");
		out.append("- Coordinate model: `").append(PreflightReport.COORDINATE_MODEL).append("`\n");
		out.append("- Source fingerprint: `").append(preflight.sourceFingerprint).append("`\n");
		out.append("- Classification fingerprint: `").append(classificationFingerprint).append("`\n");
		out.append("- Classified unresolved owners: ").append(classifications.size()).append("\n\n");
		out.append("This is lexical migration triage. Coordinates remain unparsed and unresolved, and no target source was rewritten.\n\n");
		appendCounts(out, "Disposition", dispositions);
		appendCounts(out, "Migration family", families);
		appendCounts(out, "Migration risk", risks);
		out.append("## Owners\n\n");
		out.append("| Family | Disposition | Risk | Confidence | Role | Path | Reasons |\n");
		out.append("| --- | --- | --- | --- | --- | --- | --- |\n");
		for (Classification classification : classifications) {
			out.append("| ").append(classification.primaryFamily)
				.append(" | ").append(classification.disposition)
				.append(" | ").append(classification.migrationRisk)
				.append(" | ").append(classification.confidence)
				.append(" | ").append(classification.source.role)
				.append(" | `").append(classification.source.path).append("` | ")
				.append(join(classification.reasons)).append(" |\n");
		}
		return out.toString();
	}

	private static void appendCounts(
		StringBuilder out, String label, Map<String, Integer> counts) {
		out.append("## ").append(label).append(" counts\n\n");
		out.append("| ").append(label).append(" | Owners |\n");
		out.append("| --- | ---: |\n");
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			out.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue()).append(" |\n");
		}
		out.append('\n');
	}

	private static String join(List<String> values) {
		StringBuilder out = new StringBuilder();
		for (int index = 0; index < values.size(); index++) {
			if (index > 0) {
				out.append(", ");
			}
			out.append(values.get(index));
		}
		return out.toString();
	}

	private static Map<String, Object> map() {
		return new LinkedHashMap<String, Object>();
	}

	static final class Classification {
		final PreflightReport.SourceFile source;
		final String disposition;
		final String primaryFamily;
		final String migrationRisk;
		final String confidence;
		final List<String> reasons;

		Classification(
			PreflightReport.SourceFile source,
			String disposition,
			String primaryFamily,
			String migrationRisk,
			String confidence,
			List<String> reasons) {
			this.source = source;
			this.disposition = disposition;
			this.primaryFamily = primaryFamily;
			this.migrationRisk = migrationRisk;
			this.confidence = confidence;
			this.reasons = reasons;
		}

		Map<String, Object> document() {
			Map<String, Object> owner = map();
			owner.put("classificationStatus", "classified-unparsed");
			owner.put("disposition", disposition);
			owner.put("primaryFamily", primaryFamily);
			owner.put("migrationRisk", migrationRisk);
			owner.put("confidence", confidence);
			owner.put("role", source.role);
			owner.put("path", source.path);
			owner.put("size", Long.valueOf(source.size));
			owner.put("sha256", source.sha256);
			owner.put("signals", new ArrayList<Object>(source.signals));
			owner.put("reasons", new ArrayList<Object>(reasons));
			return owner;
		}
	}
}
