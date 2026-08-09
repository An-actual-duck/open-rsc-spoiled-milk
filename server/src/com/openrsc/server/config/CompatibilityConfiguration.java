package com.openrsc.server.config;

/** Immutable client/protocol compatibility selection; legacy fields retain authority at runtime. */
public final class CompatibilityConfiguration {
	private final int clientVersion, basedMapData, basedConfigData;
	private final boolean enforceCustomClientVersion, memberWorld;
	public CompatibilityConfiguration(final int clientVersion, final boolean enforceCustomClientVersion,
			final boolean memberWorld, final int basedMapData, final int basedConfigData) {
		this.clientVersion = clientVersion; this.enforceCustomClientVersion = enforceCustomClientVersion;
		this.memberWorld = memberWorld; this.basedMapData = basedMapData; this.basedConfigData = basedConfigData;
	}
	public int getClientVersion() { return clientVersion; } public boolean isEnforceCustomClientVersion() { return enforceCustomClientVersion; }
	public boolean isMemberWorld() { return memberWorld; } public int getBasedMapData() { return basedMapData; }
	public int getBasedConfigData() { return basedConfigData; }
}
