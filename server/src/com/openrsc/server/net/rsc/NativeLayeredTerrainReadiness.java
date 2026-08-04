package com.openrsc.server.net.rsc;

import com.openrsc.server.net.rsc.struct.incoming.LayeredTerrainReadyStruct;
import com.openrsc.server.net.rsc.struct.outgoing.LayeredSceneContextStruct;

import java.util.Objects;

/**
 * Immutable identity of one native terrain generation awaiting client proof.
 */
public final class NativeLayeredTerrainReadiness {
	private final int contextSequence;
	private final String worldSpace;
	private final int logicalLevel;
	private final int centerSectorX;
	private final int centerSectorY;
	private final String manifestSha256;

	private NativeLayeredTerrainReadiness(
		final int contextSequence,
		final String worldSpace,
		final int logicalLevel,
		final int centerSectorX,
		final int centerSectorY,
		final String manifestSha256) {
		this.contextSequence = contextSequence;
		this.worldSpace = Objects.requireNonNull(worldSpace, "worldSpace");
		this.logicalLevel = logicalLevel;
		this.centerSectorX = centerSectorX;
		this.centerSectorY = centerSectorY;
		this.manifestSha256 = Objects.requireNonNull(
			manifestSha256, "manifestSha256");
	}

	public static NativeLayeredTerrainReadiness from(
		final LayeredSceneContextStruct context) {
		Objects.requireNonNull(context, "context");
		return new NativeLayeredTerrainReadiness(
			context.sequence,
			context.worldSpace,
			context.logicalLevel,
			context.nativeCurrentChunkX,
			context.nativeCurrentChunkY,
			context.nativeManifestSha256);
	}

	public boolean matches(final LayeredTerrainReadyStruct receipt) {
		return receipt != null
			&& receipt.contextSequence == contextSequence
			&& receipt.logicalLevel == logicalLevel
			&& receipt.centerSectorX == centerSectorX
			&& receipt.centerSectorY == centerSectorY
			&& worldSpace.equals(receipt.worldSpace)
			&& manifestSha256.equals(receipt.manifestSha256);
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof NativeLayeredTerrainReadiness)) {
			return false;
		}
		final NativeLayeredTerrainReadiness receipt =
			(NativeLayeredTerrainReadiness) other;
		return contextSequence == receipt.contextSequence
			&& logicalLevel == receipt.logicalLevel
			&& centerSectorX == receipt.centerSectorX
			&& centerSectorY == receipt.centerSectorY
			&& worldSpace.equals(receipt.worldSpace)
			&& manifestSha256.equals(receipt.manifestSha256);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			contextSequence,
			worldSpace,
			logicalLevel,
			centerSectorX,
			centerSectorY,
			manifestSha256);
	}

	@Override
	public String toString() {
		return "context=" + contextSequence
			+ ",world=" + worldSpace
			+ ",level=" + logicalLevel
			+ ",center=" + centerSectorX + "," + centerSectorY
			+ ",manifest=" + manifestSha256;
	}
}
