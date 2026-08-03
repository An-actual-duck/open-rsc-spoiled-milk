package com.openrsc.server.content.worldedit;

import com.openrsc.server.Server;
import com.openrsc.server.io.AdaptiveWorldBuilderPackageGuard;
import com.openrsc.server.io.NativeLayeredBoundaryPlacement;
import com.openrsc.server.io.NativeLayeredGroundItemPlacement;
import com.openrsc.server.io.NativeLayeredNpcPlacement;
import com.openrsc.server.io.NativeLayeredPlacementSet;
import com.openrsc.server.io.NativeLayeredSceneryPlacement;
import com.openrsc.server.io.NativeLayeredWorldPackage;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable proof shared by the isolated server and its one Builder client. */
public final class AdaptiveWorldBuilderRuntimeSession {
	public static final String BINDING_FILE = "runtime-binding.properties";
	public static final String COMPOSITION_FILE =
		"effective-static-composition.json";

	private final String token;
	private final Path bindingFile;
	private final Path compositionFile;
	private final Map<String, String> fields;

	private AdaptiveWorldBuilderRuntimeSession(
		String token, Path bindingFile, Path compositionFile,
		Map<String, String> fields) {
		this.token = token;
		this.bindingFile = bindingFile;
		this.compositionFile = compositionFile;
		this.fields = Collections.unmodifiableMap(
			new LinkedHashMap<String, String>(fields));
	}

	public static AdaptiveWorldBuilderRuntimeSession publish(
		Server server, Path controlDirectory) throws IOException {
		if (!AdaptiveWorldBuilderRuntimeIdentity.isAdaptive(server.getConfig())) {
			throw new IOException(
				"Adaptive session evidence requires explicit adaptive mode");
		}
		WorldEditStorageContext storage = server.getWorldEditStorage();
		Path composition = storage.validateGeneratedPath(
			controlDirectory.resolve(COMPOSITION_FILE),
			"adaptive effective-composition evidence");
		Path binding = storage.validateGeneratedPath(
			controlDirectory.resolve(BINDING_FILE),
			"adaptive runtime binding");
		NativeLayeredWorldPackage worldPackage = server.getWorld()
			.getRegionManager().getNativeLayeredWorldPackage();
		AdaptiveWorldBuilderPackageGuard.Inventory inventory =
			AdaptiveWorldBuilderPackageGuard.requireClosedPackage(
				storage.layeredWorkingPackage());
		if (!worldPackage.getManifestSha256().equals(
				server.getConfig().LAYERED_NATIVE_TERRAIN_MANIFEST_SHA256)
			|| !inventory.getFingerprint().equals(
				server.getConfig().LAYERED_NATIVE_TERRAIN_INVENTORY_SHA256)) {
			throw new IOException(
				"Adaptive runtime package changed before readiness publication");
		}
		AdaptiveWorldBuilderDefinitionInventory.Result definitions =
			AdaptiveWorldBuilderDefinitionInventory.validate(
				server.getEntityHandler(), worldPackage);
		writeComposition(composition, worldPackage, inventory.getFingerprint());
		String compositionSha256 = sha256(composition);
		Map<String, String> fields =
			AdaptiveWorldBuilderRuntimeIdentity.bindingFields(
				server.getConfig(), worldPackage, inventory,
				compositionSha256,
				definitions.tileIdsCsv(), definitions.boundaryIdsCsv(),
				definitions.sceneryIdsCsv(), definitions.npcIdsCsv(),
				definitions.itemIdsCsv());
		String canonical =
			AdaptiveWorldBuilderRuntimeIdentity.canonicalSession(fields);
		writeAtomic(binding, canonical.getBytes(StandardCharsets.US_ASCII));
		String token = AdaptiveWorldBuilderRuntimeIdentity.fingerprint(fields);
		return new AdaptiveWorldBuilderRuntimeSession(
			token, binding, composition, fields);
	}

	public String getToken() { return token; }
	public Path getBindingFile() { return bindingFile; }
	public Path getCompositionFile() { return compositionFile; }
	public Map<String, String> getFields() { return fields; }

	private static void writeComposition(
		Path destination, NativeLayeredWorldPackage worldPackage,
		String inventorySha256) throws IOException {
		requireReplaceable(destination);
		Path staged = destination.resolveSibling(destination.getFileName() + ".tmp");
		if (Files.exists(staged, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException(
				"Adaptive composition staging file already exists");
		}
		List<NativeLayeredPlacementSet> sets =
			new ArrayList<NativeLayeredPlacementSet>(
				worldPackage.getPlacementSets().values());
		Collections.sort(sets, new Comparator<NativeLayeredPlacementSet>() {
			@Override
			public int compare(
				NativeLayeredPlacementSet left, NativeLayeredPlacementSet right) {
				int value = left.getWorldSpace().getValue().compareTo(
					right.getWorldSpace().getValue());
				if (value == 0) value = Integer.compare(left.getLevel(), right.getLevel());
				if (value == 0) value = left.getId().compareTo(right.getId());
				return value;
			}
		});
		try (BufferedWriter writer = Files.newBufferedWriter(
			staged, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
			StandardOpenOption.WRITE)) {
			writer.write("{\"schemaVersion\":1,\"evidence\":");
			json(writer, AdaptiveWorldBuilderRuntimeIdentity.EFFECTIVE_COMPOSITION_ID);
			writer.write(",\"compositionMode\":\"package-replacement\",\"packageId\":");
			json(writer, worldPackage.getPackageId());
			writer.write(",\"packageVersion\":");
			json(writer, worldPackage.getPackageVersion());
			writer.write(",\"manifestSha256\":");
			json(writer, worldPackage.getManifestSha256());
			writer.write(",\"inventorySha256\":");
			json(writer, inventorySha256);
			writer.write(",\"counts\":{\"boundaries\":");
			writer.write(Integer.toString(worldPackage.getBoundaryPlacementCount()));
			writer.write(",\"groundItems\":");
			writer.write(Integer.toString(worldPackage.getGroundItemPlacementCount()));
			writer.write(",\"npcs\":");
			writer.write(Integer.toString(worldPackage.getNpcPlacementCount()));
			writer.write(",\"scenery\":");
			writer.write(Integer.toString(worldPackage.getSceneryPlacementCount()));
			writer.write("},\"placementSets\":[");
			for (int setIndex = 0; setIndex < sets.size(); setIndex++) {
				if (setIndex > 0) writer.write(',');
				writeSet(writer, sets.get(setIndex));
			}
			writer.write("]}\n");
		}
		moveAtomic(staged, destination);
	}

	private static void writeSet(
		BufferedWriter writer, NativeLayeredPlacementSet set) throws IOException {
		writer.write("{\"id\":"); json(writer, set.getId());
		writer.write(",\"worldSpace\":"); json(writer, set.getWorldSpace().getValue());
		writer.write(",\"level\":"); writer.write(Integer.toString(set.getLevel()));
		writer.write(",\"encoding\":"); json(writer, set.getSourceEncoding());
		writer.write(",\"sourcePath\":"); json(writer, set.getSourcePath());
		writer.write(",\"sourceSha256\":"); json(writer, set.getSourceSha256());
		writer.write(",\"boundaries\":[");
		List<NativeLayeredBoundaryPlacement> boundaries =
			new ArrayList<NativeLayeredBoundaryPlacement>(set.getBoundaries());
		Collections.sort(boundaries, placementIdComparator());
		for (int index = 0; index < boundaries.size(); index++) {
			if (index > 0) writer.write(',');
			NativeLayeredBoundaryPlacement value = boundaries.get(index);
			writer.write("{\"placementId\":"); json(writer, value.getPlacementId());
			writer.write(",\"boundaryId\":"); writer.write(Integer.toString(value.getBoundaryId()));
			writer.write(",\"position\":"); location(writer, value.getLocation());
			writer.write(",\"direction\":"); writer.write(Integer.toString(value.getDirection()));
			writer.write('}');
		}
		writer.write("],\"groundItems\":[");
		List<NativeLayeredGroundItemPlacement> items =
			new ArrayList<NativeLayeredGroundItemPlacement>(set.getGroundItems());
		Collections.sort(items, placementIdComparator());
		for (int index = 0; index < items.size(); index++) {
			if (index > 0) writer.write(',');
			NativeLayeredGroundItemPlacement value = items.get(index);
			writer.write("{\"placementId\":"); json(writer, value.getPlacementId());
			writer.write(",\"itemId\":"); writer.write(Integer.toString(value.getItemId()));
			writer.write(",\"position\":"); location(writer, value.getLocation());
			writer.write(",\"amount\":"); writer.write(Integer.toString(value.getAmount()));
			writer.write(",\"respawnSeconds\":"); writer.write(Integer.toString(value.getRespawnSeconds()));
			writer.write('}');
		}
		writer.write("],\"npcs\":[");
		List<NativeLayeredNpcPlacement> npcs =
			new ArrayList<NativeLayeredNpcPlacement>(set.getNpcs());
		Collections.sort(npcs, placementIdComparator());
		for (int index = 0; index < npcs.size(); index++) {
			if (index > 0) writer.write(',');
			NativeLayeredNpcPlacement value = npcs.get(index);
			writer.write("{\"placementId\":"); json(writer, value.getPlacementId());
			writer.write(",\"npcId\":"); writer.write(Integer.toString(value.getNpcId()));
			writer.write(",\"start\":"); location(writer, value.getStart());
			writer.write(",\"roamBounds\":{\"minimum\":");
			coordinate(writer, value.getMinX(), value.getMinY());
			writer.write(",\"maximum\":");
			coordinate(writer, value.getMaxX(), value.getMaxY());
			writer.write("}}");
		}
		writer.write("],\"scenery\":[");
		List<NativeLayeredSceneryPlacement> scenery =
			new ArrayList<NativeLayeredSceneryPlacement>(set.getScenery());
		Collections.sort(scenery, placementIdComparator());
		for (int index = 0; index < scenery.size(); index++) {
			if (index > 0) writer.write(',');
			NativeLayeredSceneryPlacement value = scenery.get(index);
			writer.write("{\"placementId\":"); json(writer, value.getPlacementId());
			writer.write(",\"sceneryId\":"); writer.write(Integer.toString(value.getSceneryId()));
			writer.write(",\"position\":"); location(writer, value.getLocation());
			writer.write(",\"direction\":"); writer.write(Integer.toString(value.getDirection()));
			writer.write('}');
		}
		writer.write("]}");
	}

	private static <T> Comparator<T> placementIdComparator() {
		return new Comparator<T>() {
			@Override
			public int compare(T left, T right) {
				return placementId(left).compareTo(placementId(right));
			}
		};
	}

	private static String placementId(Object value) {
		if (value instanceof NativeLayeredBoundaryPlacement)
			return ((NativeLayeredBoundaryPlacement) value).getPlacementId();
		if (value instanceof NativeLayeredGroundItemPlacement)
			return ((NativeLayeredGroundItemPlacement) value).getPlacementId();
		if (value instanceof NativeLayeredNpcPlacement)
			return ((NativeLayeredNpcPlacement) value).getPlacementId();
		return ((NativeLayeredSceneryPlacement) value).getPlacementId();
	}

	private static void location(BufferedWriter writer, WorldLocation value)
		throws IOException {
		coordinate(
			writer, value.getCoordinate().getX(), value.getCoordinate().getY());
	}

	private static void coordinate(BufferedWriter writer, int x, int y)
		throws IOException {
		writer.write("{\"x\":"); writer.write(Integer.toString(x));
		writer.write(",\"y\":"); writer.write(Integer.toString(y));
		writer.write('}');
	}

	private static void json(BufferedWriter writer, String value)
		throws IOException {
		writer.write('"');
		for (int index = 0; index < value.length(); index++) {
			char current = value.charAt(index);
			if (current == '"' || current == '\\') writer.write('\\');
			if (current < 32) {
				writer.write(String.format("\\u%04x", (int) current));
			} else {
				writer.write(current);
			}
		}
		writer.write('"');
	}

	private static void writeAtomic(Path destination, byte[] bytes)
		throws IOException {
		requireReplaceable(destination);
		Path staged = destination.resolveSibling(destination.getFileName() + ".tmp");
		if (Files.exists(staged, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Adaptive runtime binding staging file already exists");
		}
		Files.write(staged, bytes, StandardOpenOption.CREATE_NEW);
		moveAtomic(staged, destination);
	}

	private static void requireReplaceable(Path destination) throws IOException {
		if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) return;
		if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(destination)) {
			throw new IOException("Adaptive runtime evidence destination is unsafe");
		}
		try {
			Object links = Files.getAttribute(
				destination, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
			if (links instanceof Number && ((Number) links).longValue() != 1L) {
				throw new IOException(
					"Adaptive runtime evidence destination is hard linked");
			}
		} catch (UnsupportedOperationException ignored) {
		} catch (IllegalArgumentException ignored) {
		}
	}

	private static void moveAtomic(Path staged, Path destination)
		throws IOException {
		try {
			Files.move(
				staged, destination, StandardCopyOption.ATOMIC_MOVE,
				StandardCopyOption.REPLACE_EXISTING);
		} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
			Files.move(staged, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static String sha256(Path path) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[64 * 1024];
			try (java.io.InputStream input = Files.newInputStream(path)) {
				int count;
				while ((count = input.read(buffer)) != -1) {
					digest.update(buffer, 0, count);
				}
			}
			StringBuilder result = new StringBuilder(64);
			for (byte part : digest.digest()) {
				result.append(String.format("%02x", part & 0xff));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}
}
