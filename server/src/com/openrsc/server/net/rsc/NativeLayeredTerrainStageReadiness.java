package com.openrsc.server.net.rsc;

import com.openrsc.server.net.rsc.struct.incoming
	.LayeredTerrainStageReadyStruct;
import com.openrsc.server.net.rsc.struct.outgoing.LayeredTerrainStageStruct;

import java.util.Objects;

/**
 * Immutable identity of one cache-only native terrain generation awaiting
 * client proof.
 */
public final class NativeLayeredTerrainStageReadiness {
	private final int protocolVersion;
	private final int stageSequence;
	private final int contextSequence;
	private final String worldSpace;
	private final int logicalLevel;
	private final int centerSectorX;
	private final int centerSectorY;
	private final String manifestSha256;

	private NativeLayeredTerrainStageReadiness(
		final int protocolVersion,
		final int stageSequence,
		final int contextSequence,
		final String worldSpace,
		final int logicalLevel,
		final int centerSectorX,
		final int centerSectorY,
		final String manifestSha256) {
		this.protocolVersion = protocolVersion;
		this.stageSequence = stageSequence;
		this.contextSequence = contextSequence;
		this.worldSpace = Objects.requireNonNull(worldSpace, "worldSpace");
		this.logicalLevel = logicalLevel;
		this.centerSectorX = centerSectorX;
		this.centerSectorY = centerSectorY;
		this.manifestSha256 = Objects.requireNonNull(
			manifestSha256, "manifestSha256");
	}

	public static NativeLayeredTerrainStageReadiness from(
		final LayeredTerrainStageStruct stage) {
		Objects.requireNonNull(stage, "stage");
		return new NativeLayeredTerrainStageReadiness(
			stage.protocolVersion,
			stage.sequence,
			stage.contextSequence,
			stage.worldSpace,
			stage.logicalLevel,
			stage.nativeCurrentChunkX,
			stage.nativeCurrentChunkY,
			stage.nativeManifestSha256);
	}

	public boolean matches(
		final LayeredTerrainStageReadyStruct receipt) {
		return receipt != null
			&& receipt.protocolVersion == protocolVersion
			&& receipt.stageSequence == stageSequence
			&& receipt.contextSequence == contextSequence
			&& receipt.logicalLevel == logicalLevel
			&& receipt.centerSectorX == centerSectorX
			&& receipt.centerSectorY == centerSectorY
			&& worldSpace.equals(receipt.worldSpace)
			&& manifestSha256.equals(receipt.manifestSha256);
	}

	public boolean matchesTarget(
		final String targetWorldSpace,
		final int targetLogicalLevel,
		final int targetCenterSectorX,
		final int targetCenterSectorY,
		final String targetManifestSha256) {
		return worldSpace.equals(targetWorldSpace)
			&& logicalLevel == targetLogicalLevel
			&& centerSectorX == targetCenterSectorX
			&& centerSectorY == targetCenterSectorY
			&& manifestSha256.equals(targetManifestSha256);
	}

	public boolean hasProtocolVersion(final int expectedProtocolVersion) {
		return protocolVersion == expectedProtocolVersion;
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof NativeLayeredTerrainStageReadiness)) {
			return false;
		}
		final NativeLayeredTerrainStageReadiness receipt =
			(NativeLayeredTerrainStageReadiness) other;
		return stageSequence == receipt.stageSequence
			&& protocolVersion == receipt.protocolVersion
			&& contextSequence == receipt.contextSequence
			&& logicalLevel == receipt.logicalLevel
			&& centerSectorX == receipt.centerSectorX
			&& centerSectorY == receipt.centerSectorY
			&& worldSpace.equals(receipt.worldSpace)
			&& manifestSha256.equals(receipt.manifestSha256);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			protocolVersion,
			stageSequence,
			contextSequence,
			worldSpace,
			logicalLevel,
			centerSectorX,
			centerSectorY,
			manifestSha256);
	}
}
