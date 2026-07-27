package com.openrsc.worldbuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Revalidates the immutable-by-contract source tree before project operations. */
final class WorldBuilderSourceSnapshot {
	private WorldBuilderSourceSnapshot() {
	}

	static void verify(Path workspace) throws IOException, WorldBuilderDiscoveryException {
		Path inventoryPath = workspace.resolve(WorldBuilderRuntimePreparer.SOURCE_INVENTORY);
		if (!Files.isRegularFile(inventoryPath, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(inventoryPath)) {
			throw new WorldBuilderDiscoveryException("World Builder source inventory is missing or unsafe.");
		}
		if (Files.size(inventoryPath) > 1_048_576L) {
			throw new WorldBuilderDiscoveryException("World Builder source inventory is unexpectedly large.");
		}
		List<String> lines = Files.readAllLines(inventoryPath, StandardCharsets.UTF_8);
		if (lines.isEmpty() || !"world-builder-source-v1".equals(lines.get(0))) {
			throw new WorldBuilderDiscoveryException("World Builder source inventory is invalid.");
		}
		Path source = workspace.resolve(WorldBuilderRuntimePreparer.SOURCE_DIRECTORY).normalize();
		if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
			throw new WorldBuilderDiscoveryException("World Builder source snapshot is missing or unsafe.");
		}
		Map<Path,String> expected = new LinkedHashMap<Path,String>();
		for (int index = 1; index < lines.size(); index++) {
			String line = lines.get(index);
			int tab = line.indexOf('\t');
			if (tab < 1 || tab + 1 >= line.length()) {
				throw new WorldBuilderDiscoveryException("World Builder source inventory contains an invalid record.");
			}
			String hash = line.substring(0, tab);
			String relative = line.substring(tab + 1).replace('\\', '/');
			Path candidate = source.resolve(relative).normalize();
			if (relative.startsWith("/") || !candidate.startsWith(source) || expected.put(candidate, hash) != null
				|| !("-".equals(hash) || hash.matches("[0-9a-f]{64}"))) {
				throw new WorldBuilderDiscoveryException("World Builder source inventory contains an unsafe record.");
			}
		}
		Set<Path> present = new HashSet<Path>();
		try (java.util.stream.Stream<Path> paths = Files.walk(source)) {
			java.util.Iterator<Path> iterator = paths.iterator();
			while (iterator.hasNext()) {
				Path path = iterator.next();
				if (path.equals(source)) continue;
				if (Files.isSymbolicLink(path)) {
					throw new WorldBuilderDiscoveryException("World Builder source snapshot contains a symbolic link.");
				}
				if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) present.add(path.normalize());
				else if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
					throw new WorldBuilderDiscoveryException("World Builder source snapshot contains an unsupported entry.");
				}
			}
		}
		for (Map.Entry<Path,String> entry : expected.entrySet()) {
			Path path = entry.getKey(); String hash = entry.getValue();
			if ("-".equals(hash)) {
				if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
					throw new WorldBuilderDiscoveryException("An absent source file was added: " + source.relativize(path));
				}
			} else if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
				|| Files.isSymbolicLink(path) || !hash.equals(WorldBuilderHashes.sha256(path))) {
				throw new WorldBuilderDiscoveryException("World Builder source snapshot changed: " + source.relativize(path));
			}
		}
		Set<Path> expectedPresent = new HashSet<Path>();
		for (Map.Entry<Path,String> entry : expected.entrySet()) {
			if (!"-".equals(entry.getValue())) expectedPresent.add(entry.getKey());
		}
		if (!present.equals(expectedPresent)) {
			throw new WorldBuilderDiscoveryException("World Builder source snapshot contains untracked files.");
		}
	}
}
