package com.openrsc.layeredmaps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Deterministic human- and machine-readable preflight model. */
final class PreflightReport {
	static final int SCHEMA_VERSION = 1;
	static final String REPORT_TYPE = "layered-maps-preflight";
	static final String COORDINATE_MODEL = "signed-layered-v1";

	final String layoutAdapter;
	final String sourceFingerprint;
	final Configuration configuration;
	final Terrain terrain;
	final List<SourceFile> candidateSources;
	final List<Finding> findings;

	PreflightReport(
		String layoutAdapter,
		String sourceFingerprint,
		Configuration configuration,
		Terrain terrain,
		List<SourceFile> candidateSources,
		List<Finding> findings) {
		this.layoutAdapter = layoutAdapter;
		this.sourceFingerprint = sourceFingerprint;
		this.configuration = configuration;
		this.terrain = terrain;
		this.candidateSources = Collections.unmodifiableList(new ArrayList<SourceFile>(candidateSources));
		this.findings = Collections.unmodifiableList(new ArrayList<Finding>(findings));
	}

	String toJson() {
		StringBuilder out = new StringBuilder(32 * 1024);
		out.append("{\n");
		field(out, 1, "schemaVersion", Integer.toString(SCHEMA_VERSION), false);
		field(out, 1, "reportType", quote(REPORT_TYPE), false);
		field(out, 1, "coordinateModel", quote(COORDINATE_MODEL), false);
		field(out, 1, "legacyCodec", quote(LegacyPackedCoordinateCodec.ID), false);
		field(out, 1, "layoutAdapter", quote(layoutAdapter), false);
		field(out, 1, "sourceFingerprintSha256", quote(sourceFingerprint), false);

		indent(out, 1).append("\"configuration\": {\n");
		field(out, 2, "path", quote(configuration.path), false);
		field(out, 2, "sha256", quote(configuration.sha256), false);
		field(out, 2, "clientVersion", Integer.toString(configuration.clientVersion), false);
		field(out, 2, "basedMapData", Integer.toString(configuration.basedMapData), false);
		field(out, 2, "memberWorld", Boolean.toString(configuration.memberWorld), false);
		field(out, 2, "customLandscape", Boolean.toString(configuration.customLandscape), false);
		field(out, 2, "wantMyWorld", Boolean.toString(configuration.wantMyWorld), true);
		indent(out, 1).append("},\n");

		indent(out, 1).append("\"terrain\": {\n");
		field(out, 2, "serverPath", quote(terrain.serverPath), false);
		field(out, 2, "clientPath", quote(terrain.clientPath), false);
		field(out, 2, "sha256", quote(terrain.sha256), false);
		field(out, 2, "sectorCount", Integer.toString(terrain.sectorCount), false);
		indent(out, 2).append("\"planes\": {\n");
		int planeIndex = 0;
		for (Map.Entry<Integer, PlaneStats> entry : terrain.planes.entrySet()) {
			PlaneStats stats = entry.getValue();
			indent(out, 3).append(quote(Integer.toString(entry.getKey()))).append(": {");
			out.append("\"sectorCount\": ").append(stats.sectorCount).append(", ");
			out.append("\"minSectorX\": ").append(stats.minSectorX).append(", ");
			out.append("\"maxSectorX\": ").append(stats.maxSectorX).append(", ");
			out.append("\"minSectorY\": ").append(stats.minSectorY).append(", ");
			out.append("\"maxSectorY\": ").append(stats.maxSectorY).append('}');
			out.append(++planeIndex == terrain.planes.size() ? '\n' : ',').append(planeIndex == terrain.planes.size() ? "" : "\n");
		}
		indent(out, 2).append("}\n");
		indent(out, 1).append("},\n");

		field(out, 1, "candidateSourceCount", Integer.toString(candidateSources.size()), false);
		indent(out, 1).append("\"candidateSources\": [\n");
		for (int index = 0; index < candidateSources.size(); index++) {
			SourceFile source = candidateSources.get(index);
			indent(out, 2).append("{\n");
			field(out, 3, "role", quote(source.role), false);
			field(out, 3, "path", quote(source.path), false);
			field(out, 3, "size", Long.toString(source.size), false);
			field(out, 3, "sha256", quote(source.sha256), false);
			indent(out, 3).append("\"signals\": [");
			for (int signal = 0; signal < source.signals.size(); signal++) {
				if (signal > 0) {
					out.append(", ");
				}
				out.append(quote(source.signals.get(signal)));
			}
			out.append("]\n");
			indent(out, 2).append('}').append(index + 1 == candidateSources.size() ? '\n' : ',').append(index + 1 == candidateSources.size() ? "" : "\n");
		}
		indent(out, 1).append("],\n");

		indent(out, 1).append("\"findings\": [\n");
		for (int index = 0; index < findings.size(); index++) {
			Finding finding = findings.get(index);
			indent(out, 2).append("{");
			out.append("\"severity\": ").append(quote(finding.severity)).append(", ");
			out.append("\"code\": ").append(quote(finding.code)).append(", ");
			out.append("\"message\": ").append(quote(finding.message)).append('}');
			out.append(index + 1 == findings.size() ? '\n' : ',').append(index + 1 == findings.size() ? "" : "\n");
		}
		indent(out, 1).append("]\n");
		out.append("}\n");
		return out.toString();
	}

	String toMarkdown() {
		StringBuilder out = new StringBuilder(16 * 1024);
		out.append("# Layered Maps Preflight\n\n");
		out.append("- Report schema: `").append(SCHEMA_VERSION).append("`\n");
		out.append("- Coordinate model: `").append(COORDINATE_MODEL).append("`\n");
		out.append("- Legacy codec: `").append(LegacyPackedCoordinateCodec.ID).append("`\n");
		out.append("- Layout adapter: `").append(layoutAdapter).append("`\n");
		out.append("- Source fingerprint: `").append(sourceFingerprint).append("`\n\n");

		out.append("## Configuration\n\n");
		out.append("- Path: `").append(configuration.path).append("`\n");
		out.append("- SHA-256: `").append(configuration.sha256).append("`\n");
		out.append("- Client version: `").append(configuration.clientVersion).append("`\n");
		out.append("- Based map data: `").append(configuration.basedMapData).append("`\n");
		out.append("- Member/custom/MyWorld: `")
			.append(configuration.memberWorld).append('/')
			.append(configuration.customLandscape).append('/')
			.append(configuration.wantMyWorld).append("`\n\n");

		out.append("## Terrain\n\n");
		out.append("- Server: `").append(terrain.serverPath).append("`\n");
		out.append("- Client: `").append(terrain.clientPath).append("`\n");
		out.append("- Shared SHA-256: `").append(terrain.sha256).append("`\n");
		out.append("- Sectors: `").append(terrain.sectorCount).append("`\n\n");
		out.append("| Legacy plane | Sectors | Sector X | Sector Y |\n");
		out.append("| --- | ---: | --- | --- |\n");
		for (Map.Entry<Integer, PlaneStats> entry : terrain.planes.entrySet()) {
			PlaneStats stats = entry.getValue();
			out.append("| ").append(entry.getKey()).append(" | ").append(stats.sectorCount)
				.append(" | ").append(stats.minSectorX).append("..").append(stats.maxSectorX)
				.append(" | ").append(stats.minSectorY).append("..").append(stats.maxSectorY)
				.append(" |\n");
		}

		out.append("\n## Candidate coordinate owners\n\n");
		out.append("Candidates require later parsing; preflight has not converted them.\n\n");
		out.append("| Role | Path | Bytes | Signals | SHA-256 |\n");
		out.append("| --- | --- | ---: | --- | --- |\n");
		for (SourceFile source : candidateSources) {
			out.append("| ").append(escapeMarkdown(source.role)).append(" | `")
				.append(source.path).append("` | ").append(source.size).append(" | ")
				.append(escapeMarkdown(join(source.signals))).append(" | `")
				.append(source.sha256).append("` |\n");
		}

		out.append("\n## Findings\n\n");
		for (Finding finding : findings) {
			out.append("- **").append(finding.severity.toUpperCase()).append("** `")
				.append(finding.code).append("`: ").append(finding.message).append("\n");
		}
		return out.toString();
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

	private static String escapeMarkdown(String value) {
		return value.replace("|", "\\|");
	}

	private static void field(StringBuilder out, int depth, String name, String value, boolean last) {
		indent(out, depth).append(quote(name)).append(": ").append(value);
		out.append(last ? '\n' : ',').append(last ? "" : "\n");
	}

	private static StringBuilder indent(StringBuilder out, int depth) {
		for (int index = 0; index < depth; index++) {
			out.append("  ");
		}
		return out;
	}

	private static String quote(String value) {
		StringBuilder out = new StringBuilder(value.length() + 2).append('"');
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
				case '"':
					out.append("\\\"");
					break;
				case '\\':
					out.append("\\\\");
					break;
				case '\b':
					out.append("\\b");
					break;
				case '\f':
					out.append("\\f");
					break;
				case '\n':
					out.append("\\n");
					break;
				case '\r':
					out.append("\\r");
					break;
				case '\t':
					out.append("\\t");
					break;
				default:
					if (character < 0x20) {
						out.append(String.format("\\u%04x", (int) character));
					} else {
						out.append(character);
					}
			}
		}
		return out.append('"').toString();
	}

	static final class Configuration {
		final String path;
		final String sha256;
		final int clientVersion;
		final int basedMapData;
		final boolean memberWorld;
		final boolean customLandscape;
		final boolean wantMyWorld;

		Configuration(String path, String sha256, int clientVersion, int basedMapData,
			boolean memberWorld, boolean customLandscape, boolean wantMyWorld) {
			this.path = path;
			this.sha256 = sha256;
			this.clientVersion = clientVersion;
			this.basedMapData = basedMapData;
			this.memberWorld = memberWorld;
			this.customLandscape = customLandscape;
			this.wantMyWorld = wantMyWorld;
		}
	}

	static final class Terrain {
		final String serverPath;
		final String clientPath;
		final String sha256;
		final int sectorCount;
		final Map<Integer, PlaneStats> planes;

		Terrain(String serverPath, String clientPath, String sha256, int sectorCount,
			Map<Integer, PlaneStats> planes) {
			this.serverPath = serverPath;
			this.clientPath = clientPath;
			this.sha256 = sha256;
			this.sectorCount = sectorCount;
			this.planes = Collections.unmodifiableMap(new TreeMap<Integer, PlaneStats>(planes));
		}
	}

	static final class PlaneStats {
		int sectorCount;
		int minSectorX = Integer.MAX_VALUE;
		int maxSectorX = Integer.MIN_VALUE;
		int minSectorY = Integer.MAX_VALUE;
		int maxSectorY = Integer.MIN_VALUE;

		void include(int sectorX, int sectorY) {
			sectorCount++;
			minSectorX = Math.min(minSectorX, sectorX);
			maxSectorX = Math.max(maxSectorX, sectorX);
			minSectorY = Math.min(minSectorY, sectorY);
			maxSectorY = Math.max(maxSectorY, sectorY);
		}
	}

	static final class SourceFile {
		final String role;
		final String path;
		final long size;
		final String sha256;
		final List<String> signals;

		SourceFile(String role, String path, long size, String sha256, List<String> signals) {
			this.role = role;
			this.path = path;
			this.size = size;
			this.sha256 = sha256;
			this.signals = Collections.unmodifiableList(new ArrayList<String>(signals));
		}
	}

	static final class Finding {
		final String severity;
		final String code;
		final String message;

		Finding(String severity, String code, String message) {
			this.severity = severity;
			this.code = code;
			this.message = message;
		}
	}
}
