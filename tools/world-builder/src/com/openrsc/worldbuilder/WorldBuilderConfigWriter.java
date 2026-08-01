package com.openrsc.worldbuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict, comment-preserving writer for generated Builder server overrides. */
final class WorldBuilderConfigWriter {
	private static final Pattern CONFIG_LINE = Pattern.compile(
		"^(\\s*)([A-Za-z0-9_]+)\\s*:\\s*([^#]*?)(\\s*(?:#.*)?)$");

	private WorldBuilderConfigWriter() {
	}

	static void write(Path source, Path destination, LinkedHashMap<String, String> overrides)
		throws IOException, WorldBuilderDiscoveryException {
		List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
		List<String> rendered = render(lines, overrides);
		Files.write(destination, rendered, StandardCharsets.UTF_8);
	}

	static List<String> render(List<String> source, LinkedHashMap<String, String> overrides)
		throws WorldBuilderDiscoveryException {
		return render(source, overrides, "# Generated World Builder isolation settings");
	}

	static List<String> render(List<String> source,
		LinkedHashMap<String, String> overrides, String generatedComment)
		throws WorldBuilderDiscoveryException {
		List<String> output = new ArrayList<String>(source.size() + overrides.size() + 2);
		Set<String> replaced = new HashSet<String>();
		for (String line : source) {
			Matcher matcher = CONFIG_LINE.matcher(line);
			if (!matcher.matches() || !overrides.containsKey(matcher.group(2))) {
				output.add(line);
				continue;
			}
			String key = matcher.group(2);
			if (!replaced.add(key)) {
				throw new WorldBuilderDiscoveryException(
					"Selected configuration contains duplicate key required by Builder mode: " + key);
			}
			output.add(matcher.group(1) + key + ": " + overrides.get(key) + matcher.group(4));
		}
		output.add("");
		output.add(generatedComment);
		for (Map.Entry<String, String> override : overrides.entrySet()) {
			if (!replaced.contains(override.getKey())) {
				output.add(override.getKey() + ": " + override.getValue());
			}
		}
		return output;
	}
}
