package com.openrsc.server.model.world.region;

import com.openrsc.server.io.MapSourceActivationPlan;
import com.openrsc.server.io.NativeLayeredTerrainTile;
import com.openrsc.server.io.NativeLayeredWorldPackage;
import com.openrsc.server.io.NativeLayeredWorldPackageCatalog;
import com.openrsc.server.model.world.coordinate.LayeredCompatibilityPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/** One resolved source policy; scene preparation and rendering are not authority. */
public final class RuntimeMapAuthority {
	/** Explicit retained authoring bridge, separate from immutable authored content. */
	public interface AuthoringAccess {
		boolean hasDraft(WorldLocation location);
		NativeLayeredTerrainTile resolve(WorldLocation location, NativeLayeredTerrainTile base);
	}
	private final MapSourceActivationPlan plan;
	private final NativeLayeredWorldPackageCatalog catalog;
	private final LegacyTerrainProjectionAdapter legacy;
	private final BooleanSupplier syntheticEnabled;
	private final AuthoringAccess authoring;
	private final NativeTerrainTileAdapter nativeTiles;
	private final BiFunction<WorldLocation, TileValue, TileValue> registeredCollision;

	public RuntimeMapAuthority(MapSourceActivationPlan plan, NativeLayeredWorldPackageCatalog catalog,
		LegacyTerrainProjectionAdapter legacy, BooleanSupplier syntheticEnabled,
		AuthoringAccess authoring, NativeTerrainTileAdapter nativeTiles,
		BiFunction<WorldLocation, TileValue, TileValue> registeredCollision) {
		this.plan = Objects.requireNonNull(plan, "plan");
		if (plan.gate(MapSourceActivationPlan.NATIVE_PACKAGE) != (catalog != null)) {
			throw new IllegalArgumentException("Resolved source plan and validated catalog disagree");
		}
		this.catalog = catalog;
		this.legacy = Objects.requireNonNull(legacy, "legacy");
		this.syntheticEnabled = Objects.requireNonNull(syntheticEnabled, "syntheticEnabled");
		this.authoring = Objects.requireNonNull(authoring, "authoring");
		this.nativeTiles = Objects.requireNonNull(nativeTiles, "nativeTiles");
		this.registeredCollision = Objects.requireNonNull(registeredCollision, "registeredCollision");
	}
	public Optional<NativeLayeredWorldPackage> findOwner(WorldLocation location) {
		if (catalog == null) return Optional.empty();
		WorldLocation checked = Objects.requireNonNull(location, "location");
		Optional<NativeLayeredWorldPackage> owner = catalog.findPackage(checked);
		if (owner.isPresent()) return owner;
		return hasDraft(checked) ? Optional.of(catalog.getPrimaryPackage()) : Optional.empty();
	}
	public Optional<NativeLayeredWorldPackage> findOwner(String packageId) {
		// Preserve disabled-native behavior: no package-id validation until active.
		return catalog == null ? Optional.empty() : catalog.findPackage(Objects.requireNonNull(packageId, "packageId"));
	}
	public boolean hasDraft(WorldLocation location) { return authoring.hasDraft(location); }
	public boolean hasNativeTerrain(WorldLocation location) { return findOwner(Objects.requireNonNull(location, "location")).isPresent(); }
	public TileValue getTile(WorldLocation location) {
		Objects.requireNonNull(location, "location");
		Optional<NativeLayeredWorldPackage> owner = findOwner(location);
		if (owner.isPresent()) {
			NativeLayeredTerrainTile source = source(owner.get(), location);
			if (source == null) throw new IllegalStateException(
				"Native layered terrain disappeared after startup validation: " + location);
			TileValue tile = nativeTiles.materialize(source, neighbor(owner.get(), location, 1, 0),
				neighbor(owner.get(), location, 0, 1));
			return registeredCollision.apply(location, tile);
		}
		if (LayeredCompatibilityPointAdapter.isSyntheticDeepLevel(location)) {
			LayeredCompatibilityPointAdapter.toCompatibilityPoint(location, syntheticEnabled.getAsBoolean());
			TileValue tile = new TileValue(); tile.overlay = 0; tile.initializeTerrainCollision(); return tile;
		}
		// Exact legacy projection retains blocked blank regions and refusal for
		// nonrepresentable absence. It is never permission to enter unowned native terrain.
		return legacy.getTile(location);
	}
	public TileValue getMutableTile(WorldLocation location) {
		if (hasNativeTerrain(location)) throw new UnsupportedOperationException("Native layered package terrain is immutable");
		if (LayeredCompatibilityPointAdapter.isSyntheticDeepLevel(location)) {
			LayeredCompatibilityPointAdapter.toCompatibilityPoint(location, syntheticEnabled.getAsBoolean());
			throw new UnsupportedOperationException("Synthetic deep fixture terrain is immutable");
		}
		return legacy.getMutableTile(location);
	}
	private NativeLayeredTerrainTile source(NativeLayeredWorldPackage owner, WorldLocation location) {
		return authoring.resolve(location, owner.findTile(location).orElse(null));
	}
	private NativeLayeredTerrainTile neighbor(NativeLayeredWorldPackage owner,
		WorldLocation location, int dx, int dy) {
		WorldCoordinate coordinate = location.getCoordinate();
		WorldLocation neighbor = new WorldLocation(location.getWorldSpace(), new WorldCoordinate(
			Math.addExact(coordinate.getX(), dx), Math.addExact(coordinate.getY(), dy), coordinate.getLevel()));
		return source(owner, neighbor); // Never resolve a different package owner.
	}
	public List<NativeLayeredWorldPackage> authoredPackages() {
		return catalog == null ? Collections.emptyList() : catalog.getPackages();
	}
	public NativeLayeredWorldPackageCatalog catalog() { return catalog; }
	public NativeLayeredWorldPackage primaryPackage() { return catalog == null ? null : catalog.getPrimaryPackage(); }
	public boolean replacesLegacyPopulation() { return catalog != null && plan.replacesLegacyBasePopulation(); }
	public boolean skipsLegacyTerrain() { return replacesLegacyPopulation(); }
}
