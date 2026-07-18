package com.openrsc.layeredmaps;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Lossless, non-relocating normalization of recognized legacy world sources. */
final class WorldNormalizer {
	static final String MANIFEST_TYPE = "layered-world-inventory";
	static final String SCHEMA_ID = "layered-world-inventory-v1";

	private static final Pattern TERRAIN_ENTRY =
		Pattern.compile("h([0-3])x([0-9]+)y([0-9]+)");
	private static final Map<String, RecordShape> RECORD_SHAPES = recordShapes();

	NormalizationResult normalize(Path requestedRoot) throws PreflightException {
		try {
			Path root = requestedRoot.toAbsolutePath().normalize().toRealPath();
			PreflightReport preflight = new RepositoryPreflight().inspect(root);
			List<Object> findings = new ArrayList<Object>();
			NormalizationStats stats = new NormalizationStats();

			Map<String, Object> terrain = normalizeTerrain(root, preflight, stats);
			List<Object> placements = normalizePlacements(root, preflight, findings, stats);
			Map<String, Object> transitions = normalizeTransitions(root, preflight, findings, stats);
			List<Object> unresolvedOwners = unresolvedSourceOwners(preflight);

			Map<String, Object> fingerprintBody = map();
			fingerprintBody.put("terrain", terrain);
			fingerprintBody.put("placementSources", placements);
			fingerprintBody.put("transitionGraph", transitions);
			fingerprintBody.put("unresolvedSourceOwners", unresolvedOwners);
			fingerprintBody.put("findings", findings);
			String inventoryFingerprint = Hashes.sha256(JsonDocuments.canonical(fingerprintBody));

			Map<String, Object> summary = summary(stats, placements.size(), unresolvedOwners.size(), findings.size());
			Map<String, Object> document = map();
			document.put("schemaVersion", 1L);
			document.put("manifestType", MANIFEST_TYPE);
			document.put("manifestSchema", SCHEMA_ID);
			document.put("coordinateModel", PreflightReport.COORDINATE_MODEL);
			document.put("legacyCodec", LegacyPackedCoordinateCodec.ID);
			document.put("layoutAdapter", preflight.layoutAdapter);
			document.put("worldSpace", WorldSpaceId.GLOBAL.getValue());
			document.put("sourceFingerprintSha256", preflight.sourceFingerprint);
			document.put("inventoryFingerprintSha256", inventoryFingerprint);
			document.put("summary", summary);
			document.put("terrain", terrain);
			document.put("placementSources", placements);
			document.put("transitionGraph", transitions);
			document.put("unresolvedSourceOwners", unresolvedOwners);
			document.put("findings", findings);
			Map<String, Object> summaryDocument = summaryDocument(
				preflight, inventoryFingerprint, summary, terrain, placements,
				transitions, unresolvedOwners, findings);

			verifyUnchanged(root, preflight);
			return new NormalizationResult(
				document,
				summaryDocument,
				preflight.sourceFingerprint,
				inventoryFingerprint,
				stats.terrainSectors,
				stats.placementRecords,
				stats.transitions,
				stats.unresolvedCoordinates,
				markdown(preflight, inventoryFingerprint, stats, placements, unresolvedOwners, findings));
		} catch (PreflightException failure) {
			throw failure;
		} catch (IOException failure) {
			throw new PreflightException(
				"Could not normalize recognized world sources: " + failure.getMessage(), failure);
		} catch (RuntimeException failure) {
			throw new PreflightException(
				"Structured-source normalization failed: " + failure.getMessage(), failure);
		}
	}

	private static Map<String, Object> normalizeTerrain(
		Path root, PreflightReport preflight, NormalizationStats stats)
		throws IOException, PreflightException {
		Path archivePath = sourcePath(root, preflight.terrain.serverPath);
		List<TerrainSector> sectors = new ArrayList<TerrainSector>();
		try (ZipFile archive = new ZipFile(archivePath.toFile())) {
			java.util.Enumeration<? extends ZipEntry> entries = archive.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				Matcher matcher = TERRAIN_ENTRY.matcher(entry.getName());
				if (!matcher.matches()) {
					throw new PreflightException(
						"Terrain entry changed after preflight validation: " + entry.getName());
				}
				int legacyPlane = parseNonNegativeInt(matcher.group(1), "terrain plane");
				int legacySectorX = parseNonNegativeInt(matcher.group(2), "terrain sector X");
				int legacySectorY = parseNonNegativeInt(matcher.group(3), "terrain sector Y");
				WorldMapSectorId mapSector = LegacyTerrainSectorCodec.fromLegacySector(
					legacyPlane, legacySectorX, legacySectorY);
				String reconstructed = LegacyTerrainSectorCodec.encode(mapSector);
				if (!entry.getName().equals(reconstructed)) {
					throw new PreflightException(
						"Terrain entry did not round-trip through the legacy codec: " + entry.getName());
				}
				byte[] payload = readBytes(archive.getInputStream(entry), 48 * 48 * 10 + 1);
				if (payload.length != 48 * 48 * 10) {
					throw new PreflightException(
						"Terrain payload changed after preflight validation: " + entry.getName());
				}
				sectors.add(new TerrainSector(
					entry.getName(), legacyPlane, legacySectorX, legacySectorY,
					mapSector, Hashes.sha256(payload)));
			}
		}
		Collections.sort(sectors);
		Map<Integer, Integer> levels = new TreeMap<Integer, Integer>();
		List<Object> sectorDocuments = new ArrayList<Object>();
		for (TerrainSector sector : sectors) {
			Integer count = levels.get(sector.mapSector.getLevel());
			levels.put(sector.mapSector.getLevel(), count == null ? 1 : count + 1);
			sectorDocuments.add(sector.document());
		}
		if (sectors.size() != preflight.terrain.sectorCount) {
			throw new PreflightException("Terrain sector count changed during normalization.");
		}
		stats.terrainSectors = sectors.size();
		Map<String, Object> levelCounts = map();
		for (Map.Entry<Integer, Integer> entry : levels.entrySet()) {
			levelCounts.put(Integer.toString(entry.getKey()), Long.valueOf(entry.getValue()));
		}
		Map<String, Object> result = map();
		result.put("serverPath", preflight.terrain.serverPath);
		result.put("clientPath", preflight.terrain.clientPath);
		result.put("sourceSha256", preflight.terrain.sha256);
		result.put("sectorCount", Long.valueOf(sectors.size()));
		result.put("sectorCountByLevel", levelCounts);
		result.put("storageSectorSize", 48L);
		result.put("payloadBytesPerSector", Long.valueOf(48 * 48 * 10));
		result.put("roundTripVerified", Boolean.TRUE);
		result.put("sectors", sectorDocuments);
		return result;
	}

	private static List<Object> normalizePlacements(
		Path root,
		PreflightReport preflight,
		List<Object> findings,
		NormalizationStats stats) throws IOException, PreflightException {
		List<Object> result = new ArrayList<Object>();
		for (PreflightReport.SourceFile source : preflight.candidateSources) {
			if (!"placement".equals(source.role)) {
				continue;
			}
			Map<String, Object> rootObject = JsonDocuments.readObject(sourcePath(root, source.path));
			if (rootObject.size() != 1) {
				throw new PreflightException(
					"Placement document must contain exactly one known root array: " + source.path);
			}
			String rootName = rootObject.keySet().iterator().next();
			RecordShape shape = RECORD_SHAPES.get(rootName);
			Object rootValue = rootObject.get(rootName);
			if (shape == null || !(rootValue instanceof List)) {
				throw new PreflightException(
					"Unsupported placement root in " + source.path + ": " + rootName);
			}
			List<Object> records = JsonDocuments.array(rootValue);
			List<Object> normalizedRecords = new ArrayList<Object>();
			SourceStats sourceStats = new SourceStats();
			for (int index = 0; index < records.size(); index++) {
				normalizedRecords.add(normalizeRecord(
					source.path, index, records.get(index), shape, findings, sourceStats));
			}
			stats.add(sourceStats);
			Map<String, Object> sourceDocument = map();
			sourceDocument.put("path", source.path);
			sourceDocument.put("sourceSha256", source.sha256);
			sourceDocument.put("rootName", rootName);
			sourceDocument.put("recordType", shape.recordType);
			sourceDocument.put("recordCount", Long.valueOf(records.size()));
			sourceDocument.put("fullyNormalizedRecordCount", Long.valueOf(sourceStats.fullyNormalized));
			sourceDocument.put("partiallyNormalizedRecordCount", Long.valueOf(sourceStats.partiallyNormalized));
			sourceDocument.put("unresolvedRecordCount", Long.valueOf(sourceStats.unresolvedRecords));
			sourceDocument.put("coordinateCount", Long.valueOf(sourceStats.coordinates));
			sourceDocument.put("normalizedCoordinateCount", Long.valueOf(sourceStats.normalizedCoordinates));
			sourceDocument.put("unresolvedCoordinateCount", Long.valueOf(sourceStats.unresolvedCoordinates));
			sourceDocument.put("roundTripVerified", Boolean.TRUE);
			sourceDocument.put("records", normalizedRecords);
			result.add(sourceDocument);
		}
		return result;
	}

	private static Map<String, Object> normalizeRecord(
		String sourcePath,
		int recordIndex,
		Object value,
		RecordShape shape,
		List<Object> findings,
		SourceStats stats) throws PreflightException {
		stats.records++;
		if (!(value instanceof Map)) {
			throw new PreflightException(
				"Placement record must be an object: " + sourcePath + " record " + recordIndex);
		}

		Map<String, Object> original = JsonDocuments.object(value);
		Map<String, Object> attributes = new TreeMap<String, Object>();
		for (Map.Entry<String, Object> entry : original.entrySet()) {
			if (!shape.coordinateFields.contains(entry.getKey())) {
				attributes.put(entry.getKey(), entry.getValue());
			}
		}
		if (countCoordinateObjects(attributes) > 0) {
			throw new PreflightException(
				"Placement record contains an unclassified coordinate field: "
					+ sourcePath + " record " + recordIndex);
		}

		Map<String, Object> locations = map();
		Map<String, Object> unresolved = map();
		for (String field : shape.coordinateFields) {
			stats.coordinates++;
			Object coordinateValue = original.get(field);
			if (!validCoordinateObject(coordinateValue)) {
				throw new PreflightException(
					"Placement coordinate must contain exactly 32-bit integer X and Y: "
						+ sourcePath + " record " + recordIndex + " field " + field);
			}
			Map<String, Object> legacy = JsonDocuments.object(coordinateValue);
			int x = integer(legacy.get("X"));
			int y = integer(legacy.get("Y"));
			try {
				WorldCoordinate coordinate = LegacyPackedCoordinateCodec.decode(x, y);
				locations.put(field, worldLocation(coordinate));
				stats.normalizedCoordinates++;
			} catch (IllegalArgumentException failure) {
				stats.unresolvedCoordinates++;
				unresolved.put(field, unresolvedLocation(legacy, failure.getMessage()));
				Map<String, Object> finding = finding(
					"warning", "legacy-coordinate-out-of-range", sourcePath, recordIndex, field,
					"Coordinate is outside legacy-packed-y-v1 and was preserved raw.");
				finding.put("legacyX", Long.valueOf(x));
				finding.put("legacyY", Long.valueOf(y));
				findings.add(finding);
			}
		}

		Map<String, Object> normalized = map();
		normalized.put("index", Long.valueOf(recordIndex));
		normalized.put("status", unresolved.isEmpty() ? "fully-normalized" : "partially-normalized");
		normalized.put("sourceRecordSha256", Hashes.sha256(JsonDocuments.canonical(original)));
		normalized.put("attributes", attributes);
		normalized.put("locations", locations);
		if (!unresolved.isEmpty()) {
			normalized.put("unresolvedLocations", unresolved);
			stats.partiallyNormalized++;
		} else {
			stats.fullyNormalized++;
		}

		Map<String, Object> reconstructed = reconstructRecord(attributes, locations, unresolved);
		if (!original.equals(reconstructed)) {
			throw new PreflightException(
				"Placement record did not round-trip: " + sourcePath + " record " + recordIndex);
		}
		normalized.put("roundTripVerified", Boolean.TRUE);
		return normalized;
	}

	private static Map<String, Object> reconstructRecord(
		Map<String, Object> attributes,
		Map<String, Object> locations,
		Map<String, Object> unresolved) throws PreflightException {
		Map<String, Object> result = new TreeMap<String, Object>();
		result.putAll(attributes);
		for (Map.Entry<String, Object> entry : locations.entrySet()) {
			Map<String, Object> location = JsonDocuments.object(entry.getValue());
			Map<String, Object> coordinateDocument = JsonDocuments.object(location.get("coordinate"));
			WorldCoordinate coordinate = new WorldCoordinate(
				integer(coordinateDocument.get("x")),
				integer(coordinateDocument.get("y")),
				integer(coordinateDocument.get("level")));
			LegacyPackedCoordinateCodec.PackedCoordinate packed =
				LegacyPackedCoordinateCodec.encode(coordinate);
			result.put(entry.getKey(), legacyCoordinate(packed.getX(), packed.getY()));
		}
		for (Map.Entry<String, Object> entry : unresolved.entrySet()) {
			Map<String, Object> unresolvedDocument = JsonDocuments.object(entry.getValue());
			result.put(entry.getKey(), unresolvedDocument.get("legacyCoordinate"));
		}
		return result;
	}

	private static Map<String, Object> normalizeTransitions(
		Path root,
		PreflightReport preflight,
		List<Object> findings,
		NormalizationStats stats) throws IOException, PreflightException {
		PreflightReport.SourceFile source = null;
		for (PreflightReport.SourceFile candidate : preflight.candidateSources) {
			if ("transition".equals(candidate.role)) {
				source = candidate;
				break;
			}
		}
		Map<String, Object> result = map();
		List<Object> edges = new ArrayList<Object>();
		if (source == null) {
			result.put("sourcePath", null);
			result.put("sourceSha256", null);
			result.put("edgeCount", 0L);
			result.put("normalizedEdgeCount", 0L);
			result.put("unresolvedEdgeCount", 0L);
			result.put("roundTripVerified", Boolean.TRUE);
			result.put("edges", edges);
			return result;
		}

		Document document = parseXml(sourcePath(root, source.path));
		Element rootElement = document.getDocumentElement();
		if (!"map".equals(rootElement.getTagName())) {
			throw new PreflightException("Transition XML root must be <map>: " + source.path);
		}
		List<Element> entries = childElements(rootElement);
		Set<String> sources = new HashSet<String>();
		int normalizedEdges = 0;
		int unresolvedEdges = 0;
		for (int index = 0; index < entries.size(); index++) {
			Element entry = entries.get(index);
			if (!"entry".equals(entry.getTagName())) {
				throw new PreflightException("Unexpected transition XML element: " + entry.getTagName());
			}
			List<Element> parts = childElements(entry);
			if (parts.size() != 2 || !"Point".equals(parts.get(0).getTagName())
				|| !"TelePoint".equals(parts.get(1).getTagName())) {
				throw new PreflightException(
					"Transition entry must contain Point then TelePoint: " + source.path + " entry " + index);
			}
			LegacyPoint from = point(parts.get(0), false, source.path, index);
			LegacyPoint to = point(parts.get(1), true, source.path, index);
			Map<String, Object> edge = map();
			edge.put("index", Long.valueOf(index));
			edge.put("command", to.command);
			edge.put("sourceLegacy", legacyCoordinate(from.x, from.y));
			edge.put("destinationLegacy", legacyCoordinate(to.x, to.y));
			try {
				WorldCoordinate sourceCoordinate = LegacyPackedCoordinateCodec.decode(from.x, from.y);
				WorldCoordinate destinationCoordinate = LegacyPackedCoordinateCodec.decode(to.x, to.y);
				edge.put("status", "normalized");
				edge.put("source", worldLocation(sourceCoordinate));
				edge.put("destination", worldLocation(destinationCoordinate));
				edge.put("levelDelta", Long.valueOf(
					destinationCoordinate.getLevel() - sourceCoordinate.getLevel()));
				edge.put("deltaX", Long.valueOf(
					destinationCoordinate.getX() - sourceCoordinate.getX()));
				edge.put("deltaY", Long.valueOf(
					destinationCoordinate.getY() - sourceCoordinate.getY()));
				edge.put("sameGeographicAnchor", Boolean.valueOf(
					sourceCoordinate.getX() == destinationCoordinate.getX()
						&& sourceCoordinate.getY() == destinationCoordinate.getY()));
				verifyLegacyPointRoundTrip(from, sourceCoordinate, source.path, index);
				verifyLegacyPointRoundTrip(to, destinationCoordinate, source.path, index);
				edge.put("roundTripVerified", Boolean.TRUE);
				normalizedEdges++;
				stats.normalizedCoordinates += 2;
			} catch (IllegalArgumentException failure) {
				edge.put("status", "unresolved");
				edge.put("reason", failure.getMessage());
				edge.put("roundTripVerified", Boolean.TRUE);
				unresolvedEdges++;
				stats.unresolvedCoordinates += 2;
				findings.add(finding("warning", "legacy-transition-coordinate-out-of-range",
					source.path, index, null,
					"Transition endpoint could not be normalized and both raw endpoints were preserved."));
			}
			stats.coordinates += 2;
			String sourceKey = from.x + "," + from.y;
			if (!sources.add(sourceKey)) {
				findings.add(finding("warning", "duplicate-transition-source", source.path,
					index, "Point", "Multiple directed edges share the same legacy source point."));
			}
			edges.add(edge);
		}
		stats.transitions = edges.size();
		result.put("sourcePath", source.path);
		result.put("sourceSha256", source.sha256);
		result.put("edgeCount", Long.valueOf(edges.size()));
		result.put("normalizedEdgeCount", Long.valueOf(normalizedEdges));
		result.put("unresolvedEdgeCount", Long.valueOf(unresolvedEdges));
		result.put("roundTripVerified", Boolean.TRUE);
		result.put("edges", edges);
		return result;
	}

	private static Document parseXml(Path path) throws IOException, PreflightException {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			try (InputStream input = Files.newInputStream(path)) {
				return builder.parse(input);
			}
		} catch (ParserConfigurationException failure) {
			throw new PreflightException("Secure transition XML parser is unavailable.", failure);
		} catch (SAXException failure) {
			throw new PreflightException(
				"Transition XML is malformed or unsafe: " + path + ": " + failure.getMessage(), failure);
		}
	}

	private static LegacyPoint point(
		Element element, boolean commandRequired, String sourcePath, int index)
		throws PreflightException {
		List<Element> children = childElements(element);
		int expected = commandRequired ? 3 : 2;
		if (children.size() != expected) {
			throw new PreflightException(
				"Unexpected transition point fields: " + sourcePath + " entry " + index);
		}
		int offset = 0;
		String command = null;
		if (commandRequired) {
			if (!"command".equals(children.get(0).getTagName())) {
				throw new PreflightException("TelePoint command is missing: " + sourcePath + " entry " + index);
			}
			command = text(children.get(0));
			offset = 1;
		}
		if (!"x".equals(children.get(offset).getTagName())
			|| !"y".equals(children.get(offset + 1).getTagName())) {
			throw new PreflightException(
				"Transition point must contain x then y: " + sourcePath + " entry " + index);
		}
		return new LegacyPoint(
			parseInt(text(children.get(offset)), sourcePath, index),
			parseInt(text(children.get(offset + 1)), sourcePath, index),
			command);
	}

	private static String text(Element element) throws PreflightException {
		if (!childElements(element).isEmpty()) {
			throw new PreflightException("Nested transition XML values are unsupported: " + element.getTagName());
		}
		return element.getTextContent().trim();
	}

	private static List<Element> childElements(Element parent) {
		List<Element> result = new ArrayList<Element>();
		NodeList children = parent.getChildNodes();
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				result.add((Element) child);
			}
		}
		return result;
	}

	private static List<Object> unresolvedSourceOwners(PreflightReport preflight) {
		List<Object> result = new ArrayList<Object>();
		for (PreflightReport.SourceFile source : preflight.candidateSources) {
			if (!source.role.endsWith("coordinate-source")) {
				continue;
			}
			Map<String, Object> owner = map();
			owner.put("status", "unresolved");
			owner.put("role", source.role);
			owner.put("path", source.path);
			owner.put("size", Long.valueOf(source.size));
			owner.put("sha256", source.sha256);
			owner.put("signals", new ArrayList<Object>(source.signals));
			result.add(owner);
		}
		return result;
	}

	private static Map<String, Object> summary(
		NormalizationStats stats,
		int placementSourceCount,
		int unresolvedOwnerCount,
		int findingCount) {
		Map<String, Object> result = map();
		result.put("terrainSectorCount", Long.valueOf(stats.terrainSectors));
		result.put("placementSourceCount", Long.valueOf(placementSourceCount));
		result.put("placementRecordCount", Long.valueOf(stats.placementRecords));
		result.put("fullyNormalizedPlacementRecordCount", Long.valueOf(stats.fullyNormalizedRecords));
		result.put("partiallyNormalizedPlacementRecordCount", Long.valueOf(stats.partiallyNormalizedRecords));
		result.put("unresolvedPlacementRecordCount", Long.valueOf(stats.unresolvedRecords));
		result.put("transitionEdgeCount", Long.valueOf(stats.transitions));
		result.put("coordinateCount", Long.valueOf(stats.coordinates));
		result.put("normalizedCoordinateCount", Long.valueOf(stats.normalizedCoordinates));
		result.put("unresolvedCoordinateCount", Long.valueOf(stats.unresolvedCoordinates));
		result.put("unresolvedSourceOwnerCount", Long.valueOf(unresolvedOwnerCount));
		result.put("findingCount", Long.valueOf(findingCount));
		result.put("roundTripVerified", Boolean.TRUE);
		return result;
	}

	private static Map<String, Object> summaryDocument(
		PreflightReport preflight,
		String inventoryFingerprint,
		Map<String, Object> summary,
		Map<String, Object> terrain,
		List<Object> placements,
		Map<String, Object> transitions,
		List<Object> unresolvedOwners,
		List<Object> findings) {
		Map<String, Object> terrainSummary = map();
		for (Map.Entry<String, Object> entry : terrain.entrySet()) {
			if (!"sectors".equals(entry.getKey())) {
				terrainSummary.put(entry.getKey(), entry.getValue());
			}
		}
		List<Object> placementSummaries = new ArrayList<Object>();
		for (Object value : placements) {
			Map<String, Object> source = JsonDocuments.object(value);
			Map<String, Object> sourceSummary = map();
			for (Map.Entry<String, Object> entry : source.entrySet()) {
				if (!"records".equals(entry.getKey())) {
					sourceSummary.put(entry.getKey(), entry.getValue());
				}
			}
			placementSummaries.add(sourceSummary);
		}
		Map<String, Object> result = map();
		result.put("schemaVersion", 1L);
		result.put("reportType", "layered-world-normalization-summary");
		result.put("reportSchema", "normalization-summary-v1");
		result.put("manifestSchema", SCHEMA_ID);
		result.put("coordinateModel", PreflightReport.COORDINATE_MODEL);
		result.put("legacyCodec", LegacyPackedCoordinateCodec.ID);
		result.put("layoutAdapter", preflight.layoutAdapter);
		result.put("worldSpace", WorldSpaceId.GLOBAL.getValue());
		result.put("sourceFingerprintSha256", preflight.sourceFingerprint);
		result.put("inventoryFingerprintSha256", inventoryFingerprint);
		result.put("summary", summary);
		result.put("terrain", terrainSummary);
		result.put("placementSources", placementSummaries);
		result.put("transitionGraph", transitions);
		result.put("unresolvedSourceOwners", unresolvedOwners);
		result.put("findings", findings);
		return result;
	}

	private static String markdown(
		PreflightReport preflight,
		String inventoryFingerprint,
		NormalizationStats stats,
		List<Object> placements,
		List<Object> unresolvedOwners,
		List<Object> findings) {
		StringBuilder out = new StringBuilder(16 * 1024);
		out.append("# Layered World Normalization\n\n");
		out.append("- Manifest schema: `").append(SCHEMA_ID).append("`\n");
		out.append("- Coordinate model: `").append(PreflightReport.COORDINATE_MODEL).append("`\n");
		out.append("- Legacy codec: `").append(LegacyPackedCoordinateCodec.ID).append("`\n");
		out.append("- Layout adapter: `").append(preflight.layoutAdapter).append("`\n");
		out.append("- Source fingerprint: `").append(preflight.sourceFingerprint).append("`\n");
		out.append("- Inventory fingerprint: `").append(inventoryFingerprint).append("`\n\n");
		out.append("## Summary\n\n");
		out.append("- Terrain sectors: ").append(stats.terrainSectors).append("\n");
		out.append("- Placement sources/records: ").append(placements.size()).append('/')
			.append(stats.placementRecords).append("\n");
		out.append("- Fully/partially/unresolved placement records: ")
			.append(stats.fullyNormalizedRecords).append('/')
			.append(stats.partiallyNormalizedRecords).append('/')
			.append(stats.unresolvedRecords).append("\n");
		out.append("- Directed transition edges: ").append(stats.transitions).append("\n");
		out.append("- Normalized/unresolved coordinates: ")
			.append(stats.normalizedCoordinates).append('/')
			.append(stats.unresolvedCoordinates).append("\n");
		out.append("- Unresolved Java source owners: ").append(unresolvedOwners.size()).append("\n");
		out.append("- Semantic legacy round trip: verified\n\n");
		out.append("Normalization preserves topology. It has not aligned, relocated, rewritten, or exported content.\n\n");
		out.append("## Findings\n\n");
		if (findings.isEmpty()) {
			out.append("No structured-source findings.\n");
		} else {
			for (Object value : findings) {
				Map<String, Object> finding = JsonDocuments.object(value);
				out.append("- **").append(finding.get("severity").toString().toUpperCase())
					.append("** `").append(finding.get("code")).append("` in `")
					.append(finding.get("sourcePath")).append('`');
				if (finding.get("recordIndex") != null) {
					out.append(" record ").append(finding.get("recordIndex"));
				}
				if (finding.get("field") != null) {
					out.append(" field `").append(finding.get("field")).append('`');
				}
				out.append(": ").append(finding.get("message")).append("\n");
			}
		}
		out.append("\nThe JSON inventory contains every terrain sector, placement record, directed edge, and unresolved source owner.\n");
		return out.toString();
	}

	private static void verifyUnchanged(Path root, PreflightReport preflight)
		throws IOException, PreflightException {
		if (!preflight.terrain.sha256.equals(Hashes.sha256(sourcePath(root, preflight.terrain.serverPath)))
			|| !preflight.terrain.sha256.equals(Hashes.sha256(sourcePath(root, preflight.terrain.clientPath)))) {
			throw new PreflightException("Terrain archives changed during normalization; run it again.");
		}
		for (PreflightReport.SourceFile source : preflight.candidateSources) {
			Path path = sourcePath(root, source.path);
			if (Files.size(path) != source.size || !source.sha256.equals(Hashes.sha256(path))) {
				throw new PreflightException(
					"Candidate source changed during normalization: " + source.path);
			}
		}
	}

	private static Path sourcePath(Path root, String relative) throws IOException, PreflightException {
		Path path = root.resolve(relative).normalize();
		if (!path.startsWith(root) || !Files.isRegularFile(path)) {
			throw new PreflightException("Required normalization source is missing: " + relative);
		}
		Path real = path.toRealPath();
		if (!real.startsWith(root)) {
			throw new PreflightException("Normalization source escapes the repository root: " + relative);
		}
		return real;
	}

	private static Map<String, Object> worldLocation(WorldCoordinate coordinate) {
		Map<String, Object> location = map();
		location.put("worldSpace", WorldSpaceId.GLOBAL.getValue());
		Map<String, Object> coordinateDocument = map();
		coordinateDocument.put("x", Long.valueOf(coordinate.getX()));
		coordinateDocument.put("y", Long.valueOf(coordinate.getY()));
		coordinateDocument.put("level", Long.valueOf(coordinate.getLevel()));
		location.put("coordinate", coordinateDocument);
		return location;
	}

	private static Map<String, Object> legacyCoordinate(int x, int y) {
		Map<String, Object> result = map();
		result.put("X", Long.valueOf(x));
		result.put("Y", Long.valueOf(y));
		return result;
	}

	private static Map<String, Object> unresolvedLocation(Object coordinate, String reason) {
		Map<String, Object> result = map();
		result.put("legacyCoordinate", coordinate);
		result.put("reason", reason);
		return result;
	}

	private static Map<String, Object> finding(
		String severity,
		String code,
		String sourcePath,
		Integer recordIndex,
		String field,
		String message) {
		Map<String, Object> result = map();
		result.put("severity", severity);
		result.put("code", code);
		result.put("sourcePath", sourcePath);
		result.put("recordIndex", recordIndex == null ? null : Long.valueOf(recordIndex));
		result.put("field", field);
		result.put("message", message);
		return result;
	}

	private static boolean validCoordinateObject(Object value) {
		if (!(value instanceof Map)) {
			return false;
		}
		Map<String, Object> coordinate = JsonDocuments.object(value);
		return coordinate.size() == 2 && coordinate.containsKey("X") && coordinate.containsKey("Y")
			&& isInteger(coordinate.get("X")) && isInteger(coordinate.get("Y"));
	}

	private static int countCoordinateObjects(Object value) {
		if (value instanceof Map) {
			Map<String, Object> object = JsonDocuments.object(value);
			int count = object.containsKey("X") && object.containsKey("Y") ? 1 : 0;
			for (Object child : object.values()) {
				count += countCoordinateObjects(child);
			}
			return count;
		}
		if (value instanceof List) {
			int count = 0;
			for (Object child : JsonDocuments.array(value)) {
				count += countCoordinateObjects(child);
			}
			return count;
		}
		return 0;
	}

	private static boolean isInteger(Object value) {
		return value instanceof Long && (Long) value >= Integer.MIN_VALUE && (Long) value <= Integer.MAX_VALUE;
	}

	private static int integer(Object value) throws PreflightException {
		if (!isInteger(value)) {
			throw new PreflightException("Expected a 32-bit integer in a structured source.");
		}
		return ((Long) value).intValue();
	}

	private static int parseInt(String value, String sourcePath, int index) throws PreflightException {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException failure) {
			throw new PreflightException(
				"Transition coordinate must be a 32-bit integer: " + sourcePath + " entry " + index);
		}
	}

	private static int parseNonNegativeInt(String value, String label) throws PreflightException {
		try {
			int parsed = Integer.parseInt(value);
			if (parsed < 0) {
				throw new NumberFormatException();
			}
			return parsed;
		} catch (NumberFormatException failure) {
			throw new PreflightException("Invalid " + label + ": " + value);
		}
	}

	private static byte[] readBytes(InputStream input, int limit) throws IOException {
		try (InputStream stream = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[8192];
			while (out.size() < limit) {
				int count = stream.read(buffer, 0, Math.min(buffer.length, limit - out.size()));
				if (count == -1) {
					break;
				}
				out.write(buffer, 0, count);
			}
			return out.toByteArray();
		}
	}

	private static void verifyLegacyPointRoundTrip(
		LegacyPoint legacy, WorldCoordinate coordinate, String sourcePath, int index) {
		LegacyPackedCoordinateCodec.PackedCoordinate packed =
			LegacyPackedCoordinateCodec.encode(coordinate);
		if (packed.getX() != legacy.x || packed.getY() != legacy.y) {
			throw new IllegalArgumentException(
				"Transition did not round-trip: " + sourcePath + " entry " + index);
		}
	}

	private static Map<String, RecordShape> recordShapes() {
		Map<String, RecordShape> result = new HashMap<String, RecordShape>();
		result.put("boundaries", new RecordShape("boundary", "pos"));
		result.put("grounditems", new RecordShape("ground-item", "pos"));
		result.put("npclocs", new RecordShape("npc", "start", "min", "max"));
		result.put("npc_removals", new RecordShape("npc-removal", "start", "min", "max"));
		result.put("sceneries", new RecordShape("scenery", "pos"));
		result.put("scenery_removals", new RecordShape("scenery-removal", "pos"));
		return Collections.unmodifiableMap(result);
	}

	private static Map<String, Object> map() {
		return new LinkedHashMap<String, Object>();
	}

	private static final class RecordShape {
		final String recordType;
		final List<String> coordinateFields;

		RecordShape(String recordType, String... coordinateFields) {
			this.recordType = recordType;
			this.coordinateFields = Collections.unmodifiableList(Arrays.asList(coordinateFields));
		}
	}

	private static final class TerrainSector implements Comparable<TerrainSector> {
		final String legacyEntry;
		final int legacyPlane;
		final int legacySectorX;
		final int legacySectorY;
		final WorldMapSectorId mapSector;
		final String payloadSha256;

		TerrainSector(
			String legacyEntry,
			int legacyPlane,
			int legacySectorX,
			int legacySectorY,
			WorldMapSectorId mapSector,
			String payloadSha256) {
			this.legacyEntry = legacyEntry;
			this.legacyPlane = legacyPlane;
			this.legacySectorX = legacySectorX;
			this.legacySectorY = legacySectorY;
			this.mapSector = mapSector;
			this.payloadSha256 = payloadSha256;
		}

		Map<String, Object> document() {
			Map<String, Object> result = map();
			result.put("legacyEntry", legacyEntry);
			result.put("legacyPlane", Long.valueOf(legacyPlane));
			result.put("legacySectorX", Long.valueOf(legacySectorX));
			result.put("legacySectorY", Long.valueOf(legacySectorY));
			result.put("worldSpace", mapSector.getWorldSpace().getValue());
			result.put("level", Long.valueOf(mapSector.getLevel()));
			result.put("sectorX", Long.valueOf(mapSector.getSectorX()));
			result.put("sectorY", Long.valueOf(mapSector.getSectorY()));
			result.put("payloadSha256", payloadSha256);
			return result;
		}

		@Override
		public int compareTo(TerrainSector other) {
			return legacyEntry.compareTo(other.legacyEntry);
		}
	}

	private static final class LegacyPoint {
		final int x;
		final int y;
		final String command;

		LegacyPoint(int x, int y, String command) {
			this.x = x;
			this.y = y;
			this.command = command;
		}
	}

	private static final class SourceStats {
		int records;
		int fullyNormalized;
		int partiallyNormalized;
		int unresolvedRecords;
		int coordinates;
		int normalizedCoordinates;
		int unresolvedCoordinates;
	}

	private static final class NormalizationStats {
		int terrainSectors;
		int placementRecords;
		int fullyNormalizedRecords;
		int partiallyNormalizedRecords;
		int unresolvedRecords;
		int transitions;
		int coordinates;
		int normalizedCoordinates;
		int unresolvedCoordinates;

		void add(SourceStats source) {
			placementRecords += source.records;
			fullyNormalizedRecords += source.fullyNormalized;
			partiallyNormalizedRecords += source.partiallyNormalized;
			unresolvedRecords += source.unresolvedRecords;
			coordinates += source.coordinates;
			normalizedCoordinates += source.normalizedCoordinates;
			unresolvedCoordinates += source.unresolvedCoordinates;
		}
	}
}
