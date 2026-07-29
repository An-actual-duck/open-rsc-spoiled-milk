package com.openrsc.layeredmaps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only lexical inventory of coordinate-shaped Java occurrences. */
final class JavaCoordinateOccurrenceInventory {
	static final String REPORT_TYPE = "layered-java-coordinate-occurrence-inventory";
	static final String SCHEMA_ID = "java-coordinate-occurrence-inventory-v1";

	private static final List<CallShape> CALL_SHAPES = Arrays.asList(
		new CallShape("point-construction", "Point.location", Pattern.compile("\\bPoint\\s*\\.\\s*location\\s*\\(")),
		new CallShape("point-construction", "new Point", Pattern.compile("\\bnew\\s+Point\\s*\\(")),
		new CallShape("area-construction", "new Area", Pattern.compile("\\bnew\\s+Area\\s*\\(")),
		new CallShape("area-check", ".inBounds", Pattern.compile("\\.\\s*inBounds\\s*\\(")),
		new CallShape("teleport", "teleport", Pattern.compile("\\bteleport\\s*\\(")));

	JavaCoordinateOccurrences inventory(
		Path root,
		PreflightReport preflight,
		CoordinateOwnerClassification ownerClassification)
		throws IOException, PreflightException {
		List<Object> sources = new ArrayList<Object>();
		List<Object> allOccurrences = new ArrayList<Object>();
		Map<String, Integer> countsByKind = new TreeMap<String, Integer>();
		Map<String, Integer> countsByArgumentShape = new TreeMap<String, Integer>();
		int sourceCount = 0;

		for (CoordinateOwnerClassifier.Classification classification
			: ownerClassification.classifications) {
			if (!"migration-owner".equals(classification.disposition)
				|| !"content-topology".equals(classification.primaryFamily)) {
				continue;
			}
			PreflightReport.SourceFile source = classification.source;
			Path path = sourcePath(root, source.path);
			if (Files.size(path) != source.size || !source.sha256.equals(Hashes.sha256(path))) {
				throw new PreflightException(
					"Content coordinate source changed before occurrence inventory: " + source.path);
			}
			String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			List<Occurrence> occurrences = scan(text);
			List<Object> occurrenceDocuments = new ArrayList<Object>();
			for (Occurrence occurrence : occurrences) {
				Map<String, Object> document = occurrence.document(source.path, source.sha256);
				occurrenceDocuments.add(document);
				allOccurrences.add(document);
				increment(countsByKind, occurrence.kind);
				increment(countsByArgumentShape, occurrence.argumentShape);
			}
			Map<String, Object> sourceDocument = map();
			sourceDocument.put("path", source.path);
			sourceDocument.put("sourceSha256", source.sha256);
			sourceDocument.put("occurrenceCount", Long.valueOf(occurrences.size()));
			sourceDocument.put("occurrences", occurrenceDocuments);
			sources.add(sourceDocument);
			sourceCount++;
		}

		String occurrenceFingerprint = Hashes.sha256(JsonDocuments.canonical(allOccurrences));
		Map<String, Object> summary = map();
		summary.put("contentTopologySourceCount", Long.valueOf(sourceCount));
		summary.put("sourceWithOccurrenceCount", Long.valueOf(countNonEmptySources(sources)));
		summary.put("sourceWithoutOccurrenceCount", Long.valueOf(sourceCount - countNonEmptySources(sources)));
		summary.put("occurrenceCount", Long.valueOf(allOccurrences.size()));
		summary.put("occurrenceCountByKind", counts(countsByKind));
		summary.put("occurrenceCountByArgumentShape", counts(countsByArgumentShape));

		List<Object> notes = new ArrayList<Object>();
		notes.add("Occurrences are lexical call shapes, not resolved Java symbols or inferred transition edges.");
		notes.add("Arguments are preserved as normalized source expressions; expression-bearing calls remain unresolved.");
		notes.add("Comments, string literals, and character literals are masked before scanning.");
		notes.add("No Java source or world data was modified.");

		Map<String, Object> document = map();
		document.put("schemaVersion", 1L);
		document.put("reportType", REPORT_TYPE);
		document.put("reportSchema", SCHEMA_ID);
		document.put("coordinateModel", PreflightReport.COORDINATE_MODEL);
		document.put("layoutAdapter", preflight.layoutAdapter);
		document.put("sourceFingerprintSha256", preflight.sourceFingerprint);
		document.put("ownerClassificationFingerprintSha256",
			ownerClassification.classificationFingerprint);
		document.put("occurrenceFingerprintSha256", occurrenceFingerprint);
		document.put("summary", summary);
		document.put("sources", sources);
		document.put("notes", notes);
		return new JavaCoordinateOccurrences(
			document,
			occurrenceFingerprint,
			sourceCount,
			allOccurrences.size(),
			markdown(preflight, ownerClassification, occurrenceFingerprint, summary, sources));
	}

	static List<Occurrence> scan(String text) throws PreflightException {
		if (text == null) {
			throw new NullPointerException("text");
		}
		String masked = maskNonCode(text);
		List<Occurrence> result = new ArrayList<Occurrence>();
		for (CallShape shape : CALL_SHAPES) {
			Matcher matcher = shape.pattern.matcher(masked);
			while (matcher.find()) {
				int open = masked.indexOf('(', matcher.start());
				int close = matchingParenthesis(masked, open);
				if (close < 0) {
					throw new PreflightException(
						"Unbalanced coordinate-shaped Java occurrence at line "
							+ lineNumber(masked, matcher.start()));
				}
				List<String> arguments = splitArguments(text, masked, open + 1, close);
				result.add(new Occurrence(
					shape.kind,
					shape.form,
					lineNumber(masked, matcher.start()),
					columnNumber(masked, matcher.start()),
					arguments));
			}
		}
		Collections.sort(result, new Comparator<Occurrence>() {
			@Override
			public int compare(Occurrence left, Occurrence right) {
				int line = Integer.compare(left.line, right.line);
				if (line != 0) {
					return line;
				}
				int column = Integer.compare(left.column, right.column);
				if (column != 0) {
					return column;
				}
				return left.form.compareTo(right.form);
			}
		});
		return result;
	}

	private static String maskNonCode(String text) throws PreflightException {
		StringBuilder masked = new StringBuilder(text);
		int state = 0;
		for (int index = 0; index < text.length(); index++) {
			char current = text.charAt(index);
			char next = index + 1 < text.length() ? text.charAt(index + 1) : '\0';
			if (state == 0) {
				if (current == '/' && next == '/') {
					masked.setCharAt(index, ' ');
					masked.setCharAt(++index, ' ');
					state = 1;
				} else if (current == '/' && next == '*') {
					masked.setCharAt(index, ' ');
					masked.setCharAt(++index, ' ');
					state = 2;
				} else if (current == '"') {
					masked.setCharAt(index, ' ');
					state = 3;
				} else if (current == '\'') {
					masked.setCharAt(index, ' ');
					state = 4;
				}
			} else if (state == 1) {
				if (current == '\n' || current == '\r') {
					state = 0;
				} else {
					masked.setCharAt(index, ' ');
				}
			} else if (state == 2) {
				if (current == '*' && next == '/') {
					masked.setCharAt(index, ' ');
					masked.setCharAt(++index, ' ');
					state = 0;
				} else if (current != '\n' && current != '\r') {
					masked.setCharAt(index, ' ');
				}
			} else {
				if (current == '\\') {
					masked.setCharAt(index, ' ');
					if (index + 1 < text.length()) {
						char escaped = text.charAt(++index);
						if (escaped != '\n' && escaped != '\r') {
							masked.setCharAt(index, ' ');
						}
					}
				} else if ((state == 3 && current == '"') || (state == 4 && current == '\'')) {
					masked.setCharAt(index, ' ');
					state = 0;
				} else if (current != '\n' && current != '\r') {
					masked.setCharAt(index, ' ');
				}
			}
		}
		if (state == 2 || state == 3 || state == 4) {
			throw new PreflightException("Unterminated Java comment or literal in coordinate source.");
		}
		return masked.toString();
	}

	private static int matchingParenthesis(String masked, int open) {
		int depth = 0;
		for (int index = open; index < masked.length(); index++) {
			char current = masked.charAt(index);
			if (current == '(') {
				depth++;
			} else if (current == ')' && --depth == 0) {
				return index;
			}
		}
		return -1;
	}

	private static List<String> splitArguments(
		String original, String masked, int start, int end) {
		List<String> result = new ArrayList<String>();
		int argumentStart = start;
		int parentheses = 0;
		int brackets = 0;
		int braces = 0;
		for (int index = start; index < end; index++) {
			char current = masked.charAt(index);
			if (current == '(') {
				parentheses++;
			} else if (current == ')') {
				parentheses--;
			} else if (current == '[') {
				brackets++;
			} else if (current == ']') {
				brackets--;
			} else if (current == '{') {
				braces++;
			} else if (current == '}') {
				braces--;
			} else if (current == ',' && parentheses == 0 && brackets == 0 && braces == 0) {
				result.add(normalizeExpression(original.substring(argumentStart, index)));
				argumentStart = index + 1;
			}
		}
		String finalArgument = normalizeExpression(original.substring(argumentStart, end));
		if (!finalArgument.isEmpty() || !result.isEmpty()) {
			result.add(finalArgument);
		}
		return result;
	}

	private static String normalizeExpression(String expression) {
		return expression.trim().replaceAll("\\s+", " ");
	}

	private static int lineNumber(String text, int offset) {
		int line = 1;
		for (int index = 0; index < offset; index++) {
			if (text.charAt(index) == '\n') {
				line++;
			}
		}
		return line;
	}

	private static int columnNumber(String text, int offset) {
		int previousNewline = text.lastIndexOf('\n', Math.max(0, offset - 1));
		return offset - previousNewline;
	}

	private static int countNonEmptySources(List<Object> sources) {
		int count = 0;
		for (Object value : sources) {
			Map<String, Object> source = JsonDocuments.object(value);
			if (((Long) source.get("occurrenceCount")).longValue() > 0L) {
				count++;
			}
		}
		return count;
	}

	private static void increment(Map<String, Integer> counts, String key) {
		Integer previous = counts.get(key);
		counts.put(key, previous == null ? 1 : previous + 1);
	}

	private static Map<String, Object> counts(Map<String, Integer> values) {
		Map<String, Object> result = map();
		for (Map.Entry<String, Integer> entry : values.entrySet()) {
			result.put(entry.getKey(), Long.valueOf(entry.getValue()));
		}
		return result;
	}

	private static Path sourcePath(Path root, String relative)
		throws IOException, PreflightException {
		Path path = root.resolve(relative).normalize();
		if (!path.startsWith(root) || !Files.isRegularFile(path)) {
			throw new PreflightException("Content coordinate source is missing: " + relative);
		}
		Path real = path.toRealPath();
		if (!real.startsWith(root)) {
			throw new PreflightException("Content coordinate source escapes repository root: " + relative);
		}
		return real;
	}

	private static String markdown(
		PreflightReport preflight,
		CoordinateOwnerClassification classification,
		String occurrenceFingerprint,
		Map<String, Object> summary,
		List<Object> sources) {
		StringBuilder out = new StringBuilder(64 * 1024);
		out.append("# Java Coordinate Occurrence Inventory\n\n");
		out.append("- Report schema: `").append(SCHEMA_ID).append("`\n");
		out.append("- Coordinate model: `").append(PreflightReport.COORDINATE_MODEL).append("`\n");
		out.append("- Source fingerprint: `").append(preflight.sourceFingerprint).append("`\n");
		out.append("- Owner-classification fingerprint: `")
			.append(classification.classificationFingerprint).append("`\n");
		out.append("- Occurrence fingerprint: `").append(occurrenceFingerprint).append("`\n");
		out.append("- Content sources/occurrences: ")
			.append(summary.get("contentTopologySourceCount")).append('/')
			.append(summary.get("occurrenceCount")).append("\n\n");
		out.append("This is lexical evidence only. Expressions remain unresolved and no Java source was rewritten.\n\n");
		out.append("## Counts by kind\n\n");
		appendCounts(out, JsonDocuments.object(summary.get("occurrenceCountByKind")));
		out.append("## Sources\n\n");
		out.append("| Path | Occurrences |\n| --- | ---: |\n");
		for (Object value : sources) {
			Map<String, Object> source = JsonDocuments.object(value);
			out.append("| `").append(source.get("path")).append("` | ")
				.append(source.get("occurrenceCount")).append(" |\n");
		}
		return out.toString();
	}

	private static void appendCounts(StringBuilder out, Map<String, Object> counts) {
		out.append("| Kind | Occurrences |\n| --- | ---: |\n");
		for (Map.Entry<String, Object> entry : counts.entrySet()) {
			out.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue()).append(" |\n");
		}
		out.append('\n');
	}

	private static Map<String, Object> map() {
		return new LinkedHashMap<String, Object>();
	}

	private static final class CallShape {
		final String kind;
		final String form;
		final Pattern pattern;

		CallShape(String kind, String form, Pattern pattern) {
			this.kind = kind;
			this.form = form;
			this.pattern = pattern;
		}
	}

	static final class Occurrence {
		final String kind;
		final String form;
		final int line;
		final int column;
		final List<String> arguments;
		final String argumentShape;

		Occurrence(String kind, String form, int line, int column, List<String> arguments) {
			this.kind = kind;
			this.form = form;
			this.line = line;
			this.column = column;
			this.arguments = Collections.unmodifiableList(new ArrayList<String>(arguments));
			this.argumentShape = argumentShape(arguments);
		}

		Map<String, Object> document(String path, String sourceSha256) {
			Map<String, Object> result = map();
			result.put("path", path);
			result.put("sourceSha256", sourceSha256);
			result.put("line", Long.valueOf(line));
			result.put("column", Long.valueOf(column));
			result.put("kind", kind);
			result.put("form", form);
			result.put("argumentCount", Long.valueOf(arguments.size()));
			result.put("argumentShape", argumentShape);
			List<Object> argumentDocuments = new ArrayList<Object>();
			for (int index = 0; index < arguments.size(); index++) {
				Map<String, Object> argument = map();
				argument.put("index", Long.valueOf(index));
				argument.put("expression", arguments.get(index));
				argument.put("expressionKind", expressionKind(arguments.get(index)));
				argumentDocuments.add(argument);
			}
			result.put("arguments", argumentDocuments);
			return result;
		}

		private static String argumentShape(List<String> arguments) {
			if (arguments.isEmpty()) {
				return "no-arguments";
			}
			for (String argument : arguments) {
				if (!"integer-literal".equals(expressionKind(argument))) {
					return "expression-bearing";
				}
			}
			return "all-integer-literals";
		}

		private static String expressionKind(String expression) {
			if (expression.matches("[+-]?(?:0[xX][0-9a-fA-F]+|[0-9]+)")) {
				return "integer-literal";
			}
			if (expression.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
				return "identifier";
			}
			if (expression.matches("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+")) {
				return "member-access";
			}
			if (expression.contains("(")) {
				return "call-expression";
			}
			return "other-expression";
		}
	}
}
