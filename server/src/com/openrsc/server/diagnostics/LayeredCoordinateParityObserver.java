package com.openrsc.server.diagnostics;

import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.coordinate.LayeredCoordinateParitySnapshot;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldRegionInterestDelta;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Opt-in, non-authoritative JSONL observer for private layered-coordinate parity tests. */
public final class LayeredCoordinateParityObserver {
	public static final String EVENT_SCHEMA = "layered-map-parity-event-v3";
	public static final String LOG_ROOT_PROPERTY = "openrsc.layeredParityLogRoot";
	private static final int MAX_TRACE_REGIONS_PER_WINDOW = 4096;

	private static final Logger LOGGER = LogManager.getLogger(LayeredCoordinateParityObserver.class);
	private static final Map<TraceKey, TraceState> TRACES =
		new ConcurrentHashMap<TraceKey, TraceState>();

	private LayeredCoordinateParityObserver() {
	}

	public static Status start(
		int playerId,
		long usernameHash,
		Point current,
		int viewGridDistance) {
		TraceKey key = new TraceKey(playerId, usernameHash);
		TraceState created = new TraceState(key, logPath(key), viewGridDistance);
		TraceState state = TRACES.putIfAbsent(key, created);
		boolean newlyStarted = state == null;
		if (newlyStarted) {
			state = created;
		} else if (state.viewGridDistance != viewGridDistance) {
			throw new IllegalArgumentException(
				"Active trace view distance does not match the current server configuration");
		}
		return write(state, newlyStarted ? "start" : "snapshot", null, current, null, null);
	}

	public static Status snapshot(int playerId, long usernameHash, Point current) {
		TraceState state = TRACES.get(new TraceKey(playerId, usernameHash));
		return state == null ? Status.disabled(logPath(new TraceKey(playerId, usernameHash)))
			: write(state, "snapshot", null, current, null, null);
	}

	public static Status mark(int playerId, long usernameHash, Point current, String label) {
		TraceState state = TRACES.get(new TraceKey(playerId, usernameHash));
		return state == null ? Status.disabled(logPath(new TraceKey(playerId, usernameHash)))
			: write(state, "marker", null, current, null, sanitizeLabel(label));
	}

	public static Status stop(int playerId, long usernameHash, Point current) {
		TraceKey key = new TraceKey(playerId, usernameHash);
		TraceState state = TRACES.get(key);
		if (state == null) {
			return Status.disabled(logPath(key));
		}
		Status status = write(state, "stop", null, current, null, null);
		TRACES.remove(key, state);
		return status.asDisabled();
	}

	public static Status status(int playerId, long usernameHash) {
		TraceKey key = new TraceKey(playerId, usernameHash);
		TraceState state = TRACES.get(key);
		if (state == null) {
			return Status.disabled(logPath(key));
		}
		synchronized (state) {
			return state.status(true);
		}
	}

	public static void onLocationChanged(
		int playerId,
		long usernameHash,
		Point previous,
		Point current,
		boolean teleported) {
		if (previous == null || current == null
			|| (previous.getX() == current.getX() && previous.getY() == current.getY())) {
			return;
		}
		TraceState state = TRACES.get(new TraceKey(playerId, usernameHash));
		if (state != null) {
			write(state, teleported ? "teleport" : "move", previous, current,
				Boolean.valueOf(teleported), null);
		}
	}

	public static void onSession(int playerId, long usernameHash, Point current, boolean loggedIn) {
		TraceState state = TRACES.get(new TraceKey(playerId, usernameHash));
		if (state != null && current != null) {
			write(state, loggedIn ? "login" : "logout", null, current, null, null);
		}
	}

	private static Status write(
		TraceState state,
		String eventType,
		Point previous,
		Point current,
		Boolean teleported,
		String label) {
		Objects.requireNonNull(current, "current");
		synchronized (state) {
			try {
				LayeredCoordinateParitySnapshot to =
					LayeredCoordinateParitySnapshot.capture(current, state.viewGridDistance);
				LayeredCoordinateParitySnapshot from = previous == null
					? null : LayeredCoordinateParitySnapshot.capture(
						previous, state.viewGridDistance);
				long nextSequence = state.sequence + 1L;
				String line = eventJson(
					state.key, nextSequence, System.currentTimeMillis(), eventType,
					teleported, label, from, to);
				Files.createDirectories(state.path.getParent());
				try (BufferedWriter writer = Files.newBufferedWriter(
					state.path,
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.WRITE,
					StandardOpenOption.APPEND)) {
					writer.write(line);
					writer.newLine();
				}
				state.sequence = nextSequence;
				state.lastSnapshot = to;
				state.lastError = null;
			} catch (RuntimeException | IOException failure) {
				state.lastError = failure.getClass().getSimpleName() + ": " + safeMessage(failure);
				LOGGER.error("Layered coordinate parity observer could not write {}", state.path, failure);
			}
			return state.status(true);
		}
	}

	private static String eventJson(
		TraceKey key,
		long sequence,
		long timestamp,
		String eventType,
		Boolean teleported,
		String label,
		LayeredCoordinateParitySnapshot from,
		LayeredCoordinateParitySnapshot to) {
		StringBuilder out = new StringBuilder(1024);
		out.append('{');
		field(out, "schema", EVENT_SCHEMA).append(',');
		field(out, "eventType", eventType).append(',');
		out.append("\"sequence\":").append(sequence).append(',');
		out.append("\"timestampEpochMillis\":").append(timestamp).append(',');
		out.append("\"player\":{\"databaseId\":").append(key.playerId)
			.append(",\"usernameHash\":\"").append(Long.toUnsignedString(key.usernameHash))
			.append("\"},");
		out.append("\"label\":");
		if (label == null) {
			out.append("null");
		} else {
			quoted(out, label);
		}
		out.append(",\"teleported\":");
		out.append(teleported == null ? "null" : teleported.toString());
		out.append(",\"from\":").append(
			from == null ? "null" : from.toJsonWithVisibilityWindow());
		out.append(",\"to\":").append(to.toJsonWithVisibilityWindow());
		out.append(",\"delta\":");
		if (from == null) {
			out.append("null");
		} else {
			WorldCoordinate before = from.getLocation().getCoordinate();
			WorldCoordinate after = to.getLocation().getCoordinate();
			out.append("{\"x\":").append(after.getX() - before.getX())
				.append(",\"y\":").append(after.getY() - before.getY())
				.append(",\"level\":").append(after.getLevel() - before.getLevel())
				.append('}');
		}
		out.append(",\"interestDelta\":");
		if (from == null) {
			out.append("null");
		} else {
			appendInterestDelta(out, WorldRegionInterestDelta.between(
				from.getVisibilityWindow(), to.getVisibilityWindow(),
				MAX_TRACE_REGIONS_PER_WINDOW));
		}
		out.append(",\"roundTripExact\":")
			.append(to.isRoundTripExact() && (from == null || from.isRoundTripExact()));
		return out.append('}').toString();
	}

	private static void appendInterestDelta(
		final StringBuilder out,
		final WorldRegionInterestDelta delta) {
		out.append('{');
		out.append("\"previousRegionCount\":")
			.append(delta.getExited().size() + delta.getRetained().size()).append(',');
		out.append("\"currentRegionCount\":")
			.append(delta.getEntered().size() + delta.getRetained().size()).append(',');
		out.append("\"enteredCount\":").append(delta.getEntered().size()).append(',');
		out.append("\"retainedCount\":").append(delta.getRetained().size()).append(',');
		out.append("\"exitedCount\":").append(delta.getExited().size()).append(',');
		out.append("\"worldSpaceChanged\":").append(delta.changesWorldSpace()).append(',');
		out.append("\"levelChanged\":").append(delta.changesLevel()).append(',');
		out.append("\"noOp\":").append(delta.isNoOp()).append(',');
		out.append("\"enteredKeys\":");
		appendRegionKeys(out, delta.getEntered());
		out.append(",\"exitedKeys\":");
		appendRegionKeys(out, delta.getExited());
		out.append('}');
	}

	private static void appendRegionKeys(
		final StringBuilder out,
		final Iterable<WorldRegionKey> keys) {
		out.append('[');
		boolean first = true;
		for (WorldRegionKey key : keys) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append("{\"worldSpace\":\"")
				.append(jsonEscape(key.getWorldSpace().getValue()))
				.append("\",\"level\":").append(key.getLevel())
				.append(",\"x\":").append(key.getRegionX())
				.append(",\"y\":").append(key.getRegionY()).append('}');
		}
		out.append(']');
	}

	private static StringBuilder field(StringBuilder out, String name, String value) {
		quoted(out, name).append(':');
		return quoted(out, value);
	}

	private static StringBuilder quoted(StringBuilder out, String value) {
		return out.append('"').append(jsonEscape(value)).append('"');
	}

	private static String jsonEscape(String value) {
		StringBuilder out = new StringBuilder(value.length() + 8);
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
				case '"':
					out.append("\\\"");
					break;
				case '\\':
					out.append("\\\\");
					break;
				case '\b':
					out.append("\\b");
					break;
				case '\f':
					out.append("\\f");
					break;
				case '\n':
					out.append("\\n");
					break;
				case '\r':
					out.append("\\r");
					break;
				case '\t':
					out.append("\\t");
					break;
				default:
					if (character < 0x20) {
						out.append(String.format("\\u%04x", (int) character));
					} else {
						out.append(character);
					}
			}
		}
		return out.toString();
	}

	private static String sanitizeLabel(String label) {
		if (label == null) {
			throw new NullPointerException("label");
		}
		String trimmed = label.trim();
		if (trimmed.isEmpty() || trimmed.length() > 64 || !trimmed.matches("[A-Za-z0-9._-]+")) {
			throw new IllegalArgumentException(
				"Marker must be 1-64 letters, digits, dots, underscores, or hyphens.");
		}
		return trimmed;
	}

	private static String safeMessage(Throwable failure) {
		String message = failure.getMessage();
		return message == null ? "no detail" : message.replace('\n', ' ').replace('\r', ' ');
	}

	private static Path logPath(TraceKey key) {
		String configured = System.getProperty(
			LOG_ROOT_PROPERTY, Paths.get("logs", "layered-map-parity").toString());
		Path root = Paths.get(configured).toAbsolutePath().normalize();
		return root.resolve("player-" + key.playerId + '-'
			+ Long.toUnsignedString(key.usernameHash) + ".jsonl");
	}

	static void resetForTests() {
		TRACES.clear();
	}

	private static final class TraceKey {
		final int playerId;
		final long usernameHash;

		TraceKey(int playerId, long usernameHash) {
			if (playerId < 0) {
				throw new IllegalArgumentException("playerId must be non-negative");
			}
			this.playerId = playerId;
			this.usernameHash = usernameHash;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof TraceKey)) {
				return false;
			}
			TraceKey key = (TraceKey) other;
			return playerId == key.playerId && usernameHash == key.usernameHash;
		}

		@Override
		public int hashCode() {
			return 31 * playerId + (int) (usernameHash ^ (usernameHash >>> 32));
		}
	}

	private static final class TraceState {
		final TraceKey key;
		final Path path;
		final int viewGridDistance;
		long sequence;
		LayeredCoordinateParitySnapshot lastSnapshot;
		String lastError;

		TraceState(TraceKey key, Path path, int viewGridDistance) {
			if (viewGridDistance < 0) {
				throw new IllegalArgumentException("View grid distance must not be negative");
			}
			Math.multiplyExact(viewGridDistance, 8);
			this.key = key;
			this.path = path;
			this.viewGridDistance = viewGridDistance;
		}

		Status status(boolean enabled) {
			return new Status(enabled, path, sequence, lastSnapshot, lastError);
		}
	}

	public static final class Status {
		private final boolean enabled;
		private final Path path;
		private final long recordCount;
		private final LayeredCoordinateParitySnapshot lastSnapshot;
		private final String error;

		private Status(
			boolean enabled,
			Path path,
			long recordCount,
			LayeredCoordinateParitySnapshot lastSnapshot,
			String error) {
			this.enabled = enabled;
			this.path = path;
			this.recordCount = recordCount;
			this.lastSnapshot = lastSnapshot;
			this.error = error;
		}

		static Status disabled(Path path) {
			return new Status(false, path, 0L, null, null);
		}

		Status asDisabled() {
			return new Status(false, path, recordCount, lastSnapshot, error);
		}

		public boolean isEnabled() {
			return enabled;
		}

		public Path getPath() {
			return path;
		}

		public long getRecordCount() {
			return recordCount;
		}

		public LayeredCoordinateParitySnapshot getLastSnapshot() {
			return lastSnapshot;
		}

		public String getError() {
			return error;
		}
	}
}
