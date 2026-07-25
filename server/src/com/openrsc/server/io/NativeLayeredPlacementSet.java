package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One immutable, hash-addressed native layered entity-placement payload. */
public final class NativeLayeredPlacementSet {
	private final String id;
	private final WorldSpaceId worldSpace;
	private final int level;
	private final String sourceEncoding;
	private final String sourcePath;
	private final String sourceSha256;
	private final List<NativeLayeredNpcPlacement> npcs;
	private final List<NativeLayeredGroundItemPlacement> groundItems;

	NativeLayeredPlacementSet(
		final String id,
		final WorldSpaceId worldSpace,
		final int level,
		final String sourceEncoding,
		final String sourcePath,
		final String sourceSha256,
		final List<NativeLayeredNpcPlacement> npcs,
		final List<NativeLayeredGroundItemPlacement> groundItems) {
		this.id = Objects.requireNonNull(id, "id");
		this.worldSpace = Objects.requireNonNull(worldSpace, "worldSpace");
		this.level = level;
		this.sourceEncoding = Objects.requireNonNull(
			sourceEncoding, "sourceEncoding");
		this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
		this.sourceSha256 = Objects.requireNonNull(
			sourceSha256, "sourceSha256");
		this.npcs = Collections.unmodifiableList(
			new ArrayList<NativeLayeredNpcPlacement>(npcs));
		this.groundItems = Collections.unmodifiableList(
			new ArrayList<NativeLayeredGroundItemPlacement>(groundItems));
	}

	public String getId() {
		return id;
	}

	public WorldSpaceId getWorldSpace() {
		return worldSpace;
	}

	public int getLevel() {
		return level;
	}

	public String getSourceEncoding() {
		return sourceEncoding;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public String getSourceSha256() {
		return sourceSha256;
	}

	public List<NativeLayeredNpcPlacement> getNpcs() {
		return npcs;
	}

	public List<NativeLayeredGroundItemPlacement> getGroundItems() {
		return groundItems;
	}
}
