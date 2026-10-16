package com.openrsc.server.io;

import java.util.Arrays;

/** Immutable startup decision. No terrain, population or protocol implementation. */
public final class MapSourceActivationPlan {
	public static final int GATE_COUNT = 9;
	public static final int NATIVE_PACKAGE = 3;
	private final boolean[] gates;
	private final String path;
	private final String manifestSha256;
	private final String profile;
	private final boolean replacement;
	private final String report;

	private MapSourceActivationPlan(boolean[] gates, String path,
		String manifestSha256, String profile, boolean replacement, String report) {
		this.gates = gates.clone();
		this.path = path;
		this.manifestSha256 = manifestSha256;
		this.profile = profile;
		this.replacement = replacement;
		this.report = report;
	}

	/**
	 * Explicit launch source selection wins; otherwise a verified installed primary
	 * wins over configuration-file defaults. A disabled package never falls back to
	 * installed discovery. Invalid selected evidence is an error before this call.
	 */
	public static MapSourceActivationPlan resolve(boolean[] configuredGates,
		boolean[] launchGates, boolean launchSourceSelection, String path,
		String hash, String profile, String provenance, String installedStatus,
		String installedPath, String installedHash) {
		if (configuredGates.length != GATE_COUNT || launchGates.length != GATE_COUNT) {
			throw new IllegalArgumentException("Map activation gate count is invalid");
		}
		if (!("absent".equals(installedStatus) || "packed-inert".equals(installedStatus)
			|| "layered-verified".equals(installedStatus))) {
			throw new IllegalArgumentException("Unrecognized installed map evidence");
		}
		boolean[] gates = configuredGates.clone();
		boolean installed = "layered-verified".equals(installedStatus);
		String authority = launchSourceSelection ? "explicit-launch" : "configuration";
		if (installed && !launchSourceSelection) {
			for (int i = 0; i < gates.length; i++) {
				if (launchGates[i] && !gates[i]) {
					throw new IllegalArgumentException(
						"Installed map conflicts with explicitly disabled authority gate " + i);
				}
			}
			Arrays.fill(gates, true);
			path = installedPath;
			hash = installedHash;
			profile = "spoiled-milk-editor-installed";
			authority = "installed-primary";
		}
		if (gates[NATIVE_PACKAGE] && path.trim().isEmpty()) {
			throw new IllegalArgumentException("Selected native map requires a package path");
		}
		boolean replacement = false;
		if (gates[NATIVE_PACKAGE]) {
			NativeLayeredWorldRuntimeProfile policy = NativeLayeredWorldRuntimeProfile.fromConfiguration(profile);
			profile = policy.getId();
			replacement = policy.replacesLegacyBasePopulation();
		}
		String representation = gates[NATIVE_PACKAGE] ? "native-layered" : "classic";
		return new MapSourceActivationPlan(gates, path, hash, profile, replacement,
			"map-source=" + authority + " representation=" + representation
			+ " ownership=" + (gates[NATIVE_PACKAGE] ? replacement ? "replacement" : "additive" : "classic")
			+ " primary=" + installedStatus + " profile=" + profile
			+ " package=" + (gates[NATIVE_PACKAGE] ? path : "inactive")
			+ " manifest=" + (gates[NATIVE_PACKAGE] ? hash : "inactive")
			+ " gates=" + Arrays.toString(gates) + " inputs=" + provenance
			+ (installed && launchSourceSelection ? " (installed primary not selected)" : ""));
	}

	public boolean gate(int index) { return gates[index]; }
	public String getPackagePath() { return path; }
	public String getManifestSha256() { return manifestSha256; }
	public String getRuntimeProfile() { return profile; }
	public boolean replacesLegacyBasePopulation() { return replacement; }
	public String report() { return report; }
}
