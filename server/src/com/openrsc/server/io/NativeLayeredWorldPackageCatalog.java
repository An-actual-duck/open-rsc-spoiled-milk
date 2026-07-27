package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldMapSectorId;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Immutable composition of non-overlapping native layered world packages.
 *
 * <p>The catalog resolves terrain ownership before runtime state changes. A
 * package boundary is a hard scope boundary: crossing it requires an explicit
 * transition even when two package sectors happen to be adjacent.</p>
 */
public final class NativeLayeredWorldPackageCatalog {
	public static final int MAX_PACKAGES = 128;

	private final List<NativeLayeredWorldPackage> packages;
	private final Map<String, NativeLayeredWorldPackage> packagesById;
	private final Map<WorldMapSectorId, NativeLayeredWorldPackage> terrainOwners;
	private final NativeLayeredWorldPackage primaryPackage;

	private NativeLayeredWorldPackageCatalog(
		final List<NativeLayeredWorldPackage> packages,
		final Map<String, NativeLayeredWorldPackage> packagesById,
		final Map<WorldMapSectorId, NativeLayeredWorldPackage> terrainOwners) {
		this.packages = Collections.unmodifiableList(
			new ArrayList<NativeLayeredWorldPackage>(packages));
		this.packagesById = Collections.unmodifiableMap(
			new LinkedHashMap<String, NativeLayeredWorldPackage>(packagesById));
		this.terrainOwners = Collections.unmodifiableMap(
			new LinkedHashMap<WorldMapSectorId, NativeLayeredWorldPackage>(
				terrainOwners));
		this.primaryPackage = this.packages.get(0);
	}

	/**
	 * Loads one or more package roots from the existing configuration value.
	 * Multiple roots use the platform path separator; a single existing value
	 * remains byte-for-byte compatible with the original private gate.
	 */
	public static NativeLayeredWorldPackageCatalog loadConfigured(
		final String configuredPaths) throws IOException {
		if (configuredPaths == null || configuredPaths.trim().isEmpty()) {
			throw new IOException(
				"At least one native layered package path is required");
		}
		String[] values = configuredPaths.split(
			Pattern.quote(File.pathSeparator), -1);
		List<NativeLayeredWorldPackage> loaded =
			new ArrayList<NativeLayeredWorldPackage>();
		for (int index = 0; index < values.length; index++) {
			String value = values[index].trim();
			if (value.isEmpty()) {
				throw new IOException(
					"Native layered package path " + index + " is empty");
			}
			Path path = Paths.get(value);
			loaded.add(NativeLayeredWorldPackage.load(path));
		}
		return of(loaded);
	}

	public static NativeLayeredWorldPackageCatalog of(
		final Collection<NativeLayeredWorldPackage> requestedPackages)
		throws IOException {
		Objects.requireNonNull(requestedPackages, "requestedPackages");
		if (requestedPackages.isEmpty()
			|| requestedPackages.size() > MAX_PACKAGES) {
			throw new IOException(
				"Native layered package count must be 1.." + MAX_PACKAGES);
		}
		List<NativeLayeredWorldPackage> packages =
			new ArrayList<NativeLayeredWorldPackage>();
		Map<String, NativeLayeredWorldPackage> byId =
			new LinkedHashMap<String, NativeLayeredWorldPackage>();
		Map<WorldMapSectorId, NativeLayeredWorldPackage> terrainOwners =
			new LinkedHashMap<WorldMapSectorId, NativeLayeredWorldPackage>();
		Integer presentationChunkSize = null;
		for (NativeLayeredWorldPackage worldPackage : requestedPackages) {
			NativeLayeredWorldPackage checked = Objects.requireNonNull(
				worldPackage, "worldPackage");
			if (byId.put(checked.getPackageId(), checked) != null) {
				throw new IOException(
					"Duplicate native layered package ID: "
						+ checked.getPackageId());
			}
			if (presentationChunkSize == null) {
				presentationChunkSize =
					Integer.valueOf(checked.getPresentationChunkSize());
			} else if (presentationChunkSize.intValue()
					!= checked.getPresentationChunkSize()) {
				throw new IOException(
					"Native layered packages must use one presentation chunk size");
			}
			for (WorldMapSectorId sector
				: checked.getTerrainSectors().keySet()) {
				NativeLayeredWorldPackage previous =
					terrainOwners.put(sector, checked);
				if (previous != null) {
					throw new IOException(
						"Native layered terrain ownership overlaps at "
							+ sector + ": "
							+ previous.getPackageId() + " and "
							+ checked.getPackageId());
				}
			}
			packages.add(checked);
		}
		return new NativeLayeredWorldPackageCatalog(
			packages, byId, terrainOwners);
	}

	public Optional<NativeLayeredWorldPackage> findPackage(
		final WorldLocation location) {
		Objects.requireNonNull(location, "location");
		NativeLayeredWorldPackage owner =
			terrainOwners.get(WorldMapSectorId.from(location));
		return owner == null || !owner.findTile(location).isPresent()
			? Optional.<NativeLayeredWorldPackage>empty()
			: Optional.of(owner);
	}

	public Optional<NativeLayeredWorldPackage> findPackage(
		final String packageId) {
		return Optional.ofNullable(packagesById.get(
			Objects.requireNonNull(packageId, "packageId")));
	}

	/**
	 * Resolves and validates a complete destination before the caller mutates
	 * Player, cache, interest, or protocol state.
	 */
	public Transition prepareTransition(
		final WorldLocation source,
		final WorldLocation destination,
		final boolean explicit) {
		WorldLocation checkedDestination = Objects.requireNonNull(
			destination, "destination");
		NativeLayeredWorldPackage sourcePackage = source == null
			? null : findPackage(source).orElse(null);
		NativeLayeredWorldPackage destinationPackage =
			findPackage(checkedDestination).orElse(null);
		if (destinationPackage != null) {
			int chunkSize = destinationPackage.getPresentationChunkSize();
			int chunkX = Math.floorDiv(
				checkedDestination.getCoordinate().getX(), chunkSize);
			int chunkY = Math.floorDiv(
				checkedDestination.getCoordinate().getY(), chunkSize);
			if (!destinationPackage.findPresentationChunk(
					checkedDestination.getWorldSpace(),
					checkedDestination.getCoordinate().getLevel(),
					chunkX,
					chunkY).isPresent()) {
				throw new IllegalArgumentException(
					"Native layered transition destination has no presentable chunk");
			}
		}
		return prepareResolvedTransition(
			source, checkedDestination, explicit,
			sourcePackage, destinationPackage);
	}

	public Transition prepareResolvedTransition(
		final WorldLocation source,
		final WorldLocation destination,
		final boolean explicit,
		final NativeLayeredWorldPackage sourcePackage,
		final NativeLayeredWorldPackage destinationPackage) {
		WorldLocation checkedDestination=Objects.requireNonNull(
			destination,"destination");
		if((sourcePackage!=null&&!packages.contains(sourcePackage))
			||(destinationPackage!=null&&!packages.contains(destinationPackage))){
			throw new IllegalArgumentException(
				"Resolved transition references a package outside this catalog");
		}
		TransitionKind kind;
		if (source == null) {
			kind = destinationPackage == null
				? TransitionKind.INITIAL_LEGACY
				: TransitionKind.INITIAL_PACKAGE;
		} else if (sourcePackage == destinationPackage) {
			kind = sourcePackage == null
				? TransitionKind.WITHIN_LEGACY
				: TransitionKind.WITHIN_PACKAGE;
		} else {
			if (!explicit) {
				throw new IllegalArgumentException(
					"Cross-scope native layered movement requires an explicit transition");
			}
			if (sourcePackage == null) {
				kind = TransitionKind.ENTER_PACKAGE;
			} else if (destinationPackage == null) {
				kind = TransitionKind.EXIT_PACKAGE;
			} else {
				kind = TransitionKind.CROSS_PACKAGE;
			}
		}
		return new Transition(
			source,
			checkedDestination,
			sourcePackage == null ? "" : sourcePackage.getPackageId(),
			destinationPackage == null ? "" : destinationPackage.getPackageId(),
			kind,
			explicit);
	}

	public NativeLayeredWorldPackage getPrimaryPackage() {
		return primaryPackage;
	}

	public List<NativeLayeredWorldPackage> getPackages() {
		return packages;
	}

	public int size() {
		return packages.size();
	}

	public enum TransitionKind {
		INITIAL_LEGACY,
		INITIAL_PACKAGE,
		WITHIN_LEGACY,
		WITHIN_PACKAGE,
		ENTER_PACKAGE,
		EXIT_PACKAGE,
		CROSS_PACKAGE
	}

	public static final class Transition {
		private final WorldLocation source;
		private final WorldLocation destination;
		private final String sourcePackageId;
		private final String destinationPackageId;
		private final TransitionKind kind;
		private final boolean explicit;

		private Transition(
			final WorldLocation source,
			final WorldLocation destination,
			final String sourcePackageId,
			final String destinationPackageId,
			final TransitionKind kind,
			final boolean explicit) {
			this.source = source;
			this.destination = destination;
			this.sourcePackageId = sourcePackageId;
			this.destinationPackageId = destinationPackageId;
			this.kind = kind;
			this.explicit = explicit;
		}

		public WorldLocation getSource() {
			return source;
		}

		public WorldLocation getDestination() {
			return destination;
		}

		public String getSourcePackageId() {
			return sourcePackageId;
		}

		public String getDestinationPackageId() {
			return destinationPackageId;
		}

		public TransitionKind getKind() {
			return kind;
		}

		public boolean isExplicit() {
			return explicit;
		}

		@Override
		public String toString() {
			return kind + " "
				+ (sourcePackageId.isEmpty() ? "legacy" : sourcePackageId)
				+ " -> "
				+ (destinationPackageId.isEmpty()
					? "legacy" : destinationPackageId)
				+ " at " + destination;
		}
	}
}
