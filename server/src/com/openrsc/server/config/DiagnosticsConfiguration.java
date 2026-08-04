package com.openrsc.server.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable view of diagnostic/proof settings resolved during configuration loading. */
public final class DiagnosticsConfiguration {

	private static final List<String> DEPRECATED_KEYS = Collections.emptyList();
	private static final Map<String, String> OWNERSHIP_NOTES = ownershipNotes();

	private final boolean debug;
	private final boolean pcapLogging;
	private final boolean breakPidPriority;
	private final boolean forceGcOnProfiling;
	private final boolean syncVisibilityShadow;
	private final boolean syncVisibilitySnapshotInput;
	private final boolean syncVisibilityTickCache;
	private final boolean syncSceneBaseline;
	private final boolean syncMovementSnapshot;
	private final boolean movementStutterDiagnostics;
	private final boolean layeredMapParityObserver;
	private final int movementStutterSummarySeconds;
	private final int movementStutterPollOutlierMs;
	private final int movementStutterTickOutlierMs;
	private final List<String> validationErrors;

	public DiagnosticsConfiguration(
		final boolean debug,
		final boolean pcapLogging,
		final boolean breakPidPriority,
		final boolean forceGcOnProfiling,
		final boolean syncVisibilityShadow,
		final boolean syncVisibilitySnapshotInput,
		final boolean syncVisibilityTickCache,
		final boolean syncSceneBaseline,
		final boolean syncMovementSnapshot,
		final boolean movementStutterDiagnostics,
		final boolean layeredMapParityObserver,
		final int movementStutterSummarySeconds,
		final int movementStutterPollOutlierMs,
		final int movementStutterTickOutlierMs) {
		this.debug = debug;
		this.pcapLogging = pcapLogging;
		this.breakPidPriority = breakPidPriority;
		this.forceGcOnProfiling = forceGcOnProfiling;
		this.syncVisibilityShadow = syncVisibilityShadow;
		this.syncVisibilitySnapshotInput = syncVisibilitySnapshotInput;
		this.syncVisibilityTickCache = syncVisibilityTickCache;
		this.syncSceneBaseline = syncSceneBaseline;
		this.syncMovementSnapshot = syncMovementSnapshot;
		this.movementStutterDiagnostics = movementStutterDiagnostics;
		this.layeredMapParityObserver = layeredMapParityObserver;
		this.movementStutterSummarySeconds = movementStutterSummarySeconds;
		this.movementStutterPollOutlierMs = movementStutterPollOutlierMs;
		this.movementStutterTickOutlierMs = movementStutterTickOutlierMs;
		this.validationErrors = Collections.unmodifiableList(validate());
	}

	private List<String> validate() {
		final List<String> errors = new ArrayList<String>();
		if (movementStutterSummarySeconds < 5) {
			errors.add("movement_stutter_diagnostic_summary_seconds must be at least 5");
		}
		if (movementStutterPollOutlierMs < 1) {
			errors.add("movement_stutter_poll_outlier_ms must be at least 1");
		}
		if (movementStutterTickOutlierMs < 1) {
			errors.add("movement_stutter_tick_outlier_ms must be at least 1");
		}
		return errors;
	}

	private static Map<String, String> ownershipNotes() {
		final Map<String, String> notes = new LinkedHashMap<String, String>();
		notes.put("want_threading__break_pid_priority", "legacy double-underscore key also changes scheduler behavior; retained verbatim");
		notes.put("want_force_gc_on_profiling", "diagnostic process-tuning policy rather than a general runtime default");
		notes.put("want_sync_scene_baseline", "diagnostic/proof origin now overlaps maintained custom-client protocol behavior");
		notes.put("want_sync_movement_snapshot", "diagnostic/proof origin now overlaps maintained custom-client protocol behavior");
		return Collections.unmodifiableMap(notes);
	}

	public void requireValid() {
		if (!validationErrors.isEmpty()) {
			throw new ConfigurationValidationException("diagnostics", validationErrors);
		}
	}

	public boolean isDebug() { return debug; }
	public boolean isPcapLogging() { return pcapLogging; }
	public boolean isBreakPidPriority() { return breakPidPriority; }
	public boolean isForceGcOnProfiling() { return forceGcOnProfiling; }
	public boolean isSyncVisibilityShadow() { return syncVisibilityShadow; }
	public boolean isSyncVisibilitySnapshotInput() { return syncVisibilitySnapshotInput; }
	public boolean isSyncVisibilityTickCache() { return syncVisibilityTickCache; }
	public boolean isSyncSceneBaseline() { return syncSceneBaseline; }
	public boolean isSyncMovementSnapshot() { return syncMovementSnapshot; }
	public boolean isMovementStutterDiagnostics() { return movementStutterDiagnostics; }
	public boolean isLayeredMapParityObserver() { return layeredMapParityObserver; }
	public int getMovementStutterSummarySeconds() { return movementStutterSummarySeconds; }
	public int getMovementStutterPollOutlierMs() { return movementStutterPollOutlierMs; }
	public int getMovementStutterTickOutlierMs() { return movementStutterTickOutlierMs; }
	public List<String> getValidationErrors() { return validationErrors; }
	public List<String> getDeprecatedKeys() { return DEPRECATED_KEYS; }
	public Map<String, String> getOwnershipNotes() { return OWNERSHIP_NOTES; }
}
