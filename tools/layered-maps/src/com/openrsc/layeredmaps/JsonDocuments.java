package com.openrsc.layeredmaps;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Dependency-free strict JSON reader and deterministic writer. */
final class JsonDocuments {
	private static final long MAX_JSON_BYTES = 32L * 1024L * 1024L;

	private JsonDocuments() {
	}

	static Map<String, Object> readObject(Path path) throws IOException, PreflightException {
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
			throw new PreflightException("Required JSON file is missing or unsafe: " + path);
		}
		long size = Files.size(path);
		if (size < 2L || size > MAX_JSON_BYTES) {
			throw new PreflightException("JSON file has an invalid size: " + path);
		}
		String text;
		try {
			text = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();
		} catch (CharacterCodingException failure) {
			throw new PreflightException("JSON file is not valid UTF-8: " + path, failure);
		}
		Object parsed = new Parser(text, path.toString()).parse();
		if (!(parsed instanceof Map)) {
			throw new PreflightException("JSON document root must be an object: " + path);
		}
		return object(parsed);
	}

	static String pretty(Object value) {
		StringBuilder out = new StringBuilder(64 * 1024);
		write(value, out, 0, true);
		return out.append('\n').toString();
	}

	static String canonical(Object value) {
		StringBuilder out = new StringBuilder();
		write(value, out, 0, false);
		return out.toString();
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> object(Object value) {
		return (Map<String, Object>) value;
	}

	@SuppressWarnings("unchecked")
	static List<Object> array(Object value) {
		return (List<Object>) value;
	}

	private static void write(Object value, StringBuilder out, int depth, boolean pretty) {
		if (value == null) {
			out.append("null");
		} else if (value instanceof String) {
			string((String) value, out);
		} else if (value instanceof Boolean || value instanceof Byte || value instanceof Short
			|| value instanceof Integer || value instanceof Long) {
			out.append(value);
		} else if (value instanceof Map) {
			writeObject(object(value), out, depth, pretty);
		} else if (value instanceof List) {
			writeArray(array(value), out, depth, pretty);
		} else {
			throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
		}
	}

	private static void writeObject(
		Map<String, Object> value, StringBuilder out, int depth, boolean pretty) {
		Map<String, Object> sorted = new TreeMap<String, Object>(value);
		out.append('{');
		if (sorted.isEmpty()) {
			out.append('}');
			return;
		}
		int index = 0;
		for (Map.Entry<String, Object> entry : sorted.entrySet()) {
			if (index++ > 0) {
				out.append(',');
			}
			line(out, depth + 1, pretty);
			string(entry.getKey(), out);
			out.append(pretty ? ": " : ":");
			write(entry.getValue(), out, depth + 1, pretty);
		}
		line(out, depth, pretty);
		out.append('}');
	}

	private static void writeArray(List<Object> value, StringBuilder out, int depth, boolean pretty) {
		out.append('[');
		if (value.isEmpty()) {
			out.append(']');
			return;
		}
		for (int index = 0; index < value.size(); index++) {
			if (index > 0) {
				out.append(',');
			}
			line(out, depth + 1, pretty);
			write(value.get(index), out, depth + 1, pretty);
		}
		line(out, depth, pretty);
		out.append(']');
	}

	private static void line(StringBuilder out, int depth, boolean pretty) {
		if (!pretty) {
			return;
		}
		out.append('\n');
		for (int index = 0; index < depth; index++) {
			out.append("  ");
		}
	}

	private static void string(String value, StringBuilder out) {
		out.append('"');
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
		out.append('"');
	}

	private static final class Parser {
		private final String text;
		private final String label;
		private int at;
		private int values;

		Parser(String text, String label) {
			this.text = text;
			this.label = label;
		}

		Object parse() throws PreflightException {
			Object value = value(0);
			whitespace();
			if (at != text.length()) {
				fail("Trailing data");
			}
			return value;
		}

		private Object value(int depth) throws PreflightException {
			if (depth > 64 || ++values > 2_000_000) {
				fail("JSON complexity limit exceeded");
			}
			whitespace();
			if (at >= text.length()) {
				fail("Unexpected end");
			}
			char character = text.charAt(at);
			if (character == '{') {
				return object(depth + 1);
			}
			if (character == '[') {
				return array(depth + 1);
			}
			if (character == '"') {
				return string();
			}
			if (character == '-' || character >= '0' && character <= '9') {
				return number();
			}
			if (literal("true")) {
				return Boolean.TRUE;
			}
			if (literal("false")) {
				return Boolean.FALSE;
			}
			if (literal("null")) {
				return null;
			}
			fail("Unexpected token");
			return null;
		}

		private Map<String, Object> object(int depth) throws PreflightException {
			at++;
			Map<String, Object> result = new LinkedHashMap<String, Object>();
			whitespace();
			if (take('}')) {
				return result;
			}
			while (true) {
				whitespace();
				if (at >= text.length() || text.charAt(at) != '"') {
					fail("Object key must be a string");
				}
				String key = string();
				whitespace();
				if (!take(':')) {
					fail("Missing ':'");
				}
				if (result.containsKey(key)) {
					fail("Duplicate object key");
				}
				result.put(key, value(depth));
				whitespace();
				if (take('}')) {
					return result;
				}
				if (!take(',')) {
					fail("Missing ','");
				}
			}
		}

		private List<Object> array(int depth) throws PreflightException {
			at++;
			List<Object> result = new ArrayList<Object>();
			whitespace();
			if (take(']')) {
				return result;
			}
			while (true) {
				result.add(value(depth));
				whitespace();
				if (take(']')) {
					return result;
				}
				if (!take(',')) {
					fail("Missing ','");
				}
			}
		}

		private String string() throws PreflightException {
			at++;
			StringBuilder result = new StringBuilder();
			while (at < text.length()) {
				char character = text.charAt(at++);
				if (character == '"') {
					return result.toString();
				}
				if (character < ' ') {
					fail("Control character in string");
				}
				if (character != '\\') {
					result.append(character);
					continue;
				}
				if (at >= text.length()) {
					fail("Incomplete escape");
				}
				char escaped = text.charAt(at++);
				switch (escaped) {
					case '"':
					case '\\':
					case '/':
						result.append(escaped);
						break;
					case 'b':
						result.append('\b');
						break;
					case 'f':
						result.append('\f');
						break;
					case 'n':
						result.append('\n');
						break;
					case 'r':
						result.append('\r');
						break;
					case 't':
						result.append('\t');
						break;
					case 'u':
						unicode(result);
						break;
					default:
						fail("Invalid escape");
				}
			}
			fail("Unterminated string");
			return null;
		}

		private void unicode(StringBuilder result) throws PreflightException {
			if (at + 4 > text.length()) {
				fail("Incomplete Unicode escape");
			}
			try {
				result.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
			} catch (NumberFormatException failure) {
				fail("Invalid Unicode escape");
			}
			at += 4;
		}

		private Long number() throws PreflightException {
			int start = at;
			if (text.charAt(at) == '-') {
				at++;
			}
			if (at >= text.length()) {
				fail("Incomplete number");
			}
			if (text.charAt(at) == '0') {
				at++;
			} else {
				if (text.charAt(at) < '1' || text.charAt(at) > '9') {
					fail("Invalid number");
				}
				while (at < text.length() && Character.isDigit(text.charAt(at))) {
					at++;
				}
			}
			if (at < text.length()
				&& (text.charAt(at) == '.' || text.charAt(at) == 'e' || text.charAt(at) == 'E')) {
				fail("Structured-source numbers must be integers");
			}
			try {
				return Long.valueOf(text.substring(start, at));
			} catch (NumberFormatException failure) {
				fail("Integer out of range");
				return null;
			}
		}

		private boolean literal(String value) {
			if (text.regionMatches(at, value, 0, value.length())) {
				at += value.length();
				return true;
			}
			return false;
		}

		private void whitespace() {
			while (at < text.length()) {
				char character = text.charAt(at);
				if (character == ' ' || character == '\n' || character == '\r' || character == '\t') {
					at++;
				} else {
					return;
				}
			}
		}

		private boolean take(char expected) {
			if (at < text.length() && text.charAt(at) == expected) {
				at++;
				return true;
			}
			return false;
		}

		private void fail(String message) throws PreflightException {
			throw new PreflightException(
				message + " at byte/character " + at + " in " + label);
		}
	}
}
