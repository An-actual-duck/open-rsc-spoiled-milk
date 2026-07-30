package orsc;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class RendererPerformanceDiagnostics {
	private static final Map<String, ActivePhase> ACTIVE_PHASES =
		new LinkedHashMap<String, ActivePhase>();
	private static long nextPhaseId = 1L;
	private static long nextMarkerId = 1L;

	private RendererPerformanceDiagnostics() {
	}

	static boolean isCommand(String input) {
		if (input == null) {
			return false;
		}
		String normalized = input.trim().toLowerCase(Locale.ROOT);
		return normalized.equals("::perf")
			|| normalized.startsWith("::perf ")
			|| normalized.equals("::pf")
			|| normalized.startsWith("::pf ");
	}

	static synchronized String handleCommand(String input) {
		if (!RendererDiagnosticSession.isEnabled()) {
			return "Performance markers require a renderer-diagnostics launch.";
		}
		String[] parts = input == null
			? new String[0]
			: input.trim().split("\\s+");
		if (parts.length < 2) {
			return "Use ::pf s name, ::pf e name, or ::pf m name.";
		}
		String action = normalizedAction(parts[1]);
		if (action == null) {
			return "Unknown performance marker action; use start, stop, or mark.";
		}
		String label = parts.length >= 3 ? normalizedLabel(parts[2]) : "";
		if ("start".equals(action)) {
			if (label.isEmpty()) {
				label = "phase-" + nextPhaseId;
			}
			if (ACTIVE_PHASES.containsKey(label)) {
				return "Performance phase '" + label + "' is already active.";
			}
			long phaseId = nextPhaseId++;
			RenderTelemetry.recordPerformancePhaseBoundary("start");
			long now = System.nanoTime();
			ACTIVE_PHASES.put(label, new ActivePhase(phaseId, now));
			writeEvent("start", label, phaseId, 0L);
			return "Performance phase started: " + label + " (#" + phaseId + ").";
		}
		if ("stop".equals(action)) {
			if (label.isEmpty() && ACTIVE_PHASES.size() == 1) {
				label = ACTIVE_PHASES.keySet().iterator().next();
			}
			ActivePhase active = ACTIVE_PHASES.remove(label);
			if (active == null) {
				return "No active performance phase named '" + label + "'.";
			}
			RenderTelemetry.recordPerformancePhaseBoundary("stop");
			long durationNanos = Math.max(0L, System.nanoTime() - active.startedNanos);
			writeEvent("stop", label, active.phaseId, durationNanos);
			return "Performance phase stopped: " + label + " ("
				+ formatSeconds(durationNanos) + "s).";
		}

		if (label.isEmpty()) {
			label = "marker-" + nextMarkerId;
		}
		long markerId = nextMarkerId++;
		RenderTelemetry.recordPerformancePhaseBoundary("mark");
		writeEvent("mark", label, markerId, 0L);
		return "Performance marker recorded: " + label + " (#" + markerId + ").";
	}

	private static void writeEvent(
		String action,
		String label,
		long phaseId,
		long durationNanos) {
		long now = System.nanoTime();
		RendererDiagnosticSession.Record event =
			RendererDiagnosticSession.newEventRecord("renderer.performance-phase");
		if (event == null) {
			return;
		}
		event.string("action", action);
		event.string("label", label);
		event.number("phaseId", phaseId);
		event.number("durationNanos", durationNanos);
		RenderTelemetry.appendMovementCorrelation(event, now);
		RendererDiagnosticSession.writeEventRecord(event);
	}

	private static String normalizedAction(String value) {
		String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
		if ("s".equals(normalized) || "start".equals(normalized) || "begin".equals(normalized)) {
			return "start";
		}
		if ("e".equals(normalized) || "stop".equals(normalized) || "end".equals(normalized)) {
			return "stop";
		}
		if ("m".equals(normalized) || "mark".equals(normalized)) {
			return "mark";
		}
		return null;
	}

	private static String normalizedLabel(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder label = new StringBuilder();
		String normalized = value.toLowerCase(Locale.ROOT);
		for (int i = 0; i < normalized.length() && label.length() < 32; i++) {
			char ch = normalized.charAt(i);
			if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' || ch == '.') {
				label.append(ch);
			} else if (label.length() > 0 && label.charAt(label.length() - 1) != '-') {
				label.append('-');
			}
		}
		return label.toString();
	}

	private static String formatSeconds(long nanos) {
		return String.format(Locale.ROOT, "%.1f", nanos / 1_000_000_000.0D);
	}

	private static final class ActivePhase {
		private final long phaseId;
		private final long startedNanos;

		private ActivePhase(long phaseId, long startedNanos) {
			this.phaseId = phaseId;
			this.startedNanos = startedNanos;
		}
	}
}
